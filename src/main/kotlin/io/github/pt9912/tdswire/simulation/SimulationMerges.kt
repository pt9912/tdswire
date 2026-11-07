package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryParameter
import io.github.pt9912.tdswire.domain.ResultColumn
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.port.QueryExecution
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import java.util.concurrent.atomic.AtomicBoolean

/** Der Callback, der eine Zeile einfügt: `onInsert`, ersatzweise `onInsertBatch` mit einer Zeile in der Liste. */
internal typealias InsertCall = suspend (Map<String, Any?>) -> Long

/** Der Callback, der eine Zeile ändert: `onUpdate`. */
internal typealias UpdateCall = suspend (Map<String, Any?>, RowFilter?) -> Long

/**
 * Der Schlüsselvergleich eines `MERGE`: [indexes] sind die Stellen der Schlüsselspalten in den Zeilen der Tabelle,
 * [values] die Werte des Vergleichs dazu in derselben Reihenfolge (ein `null` trifft nie), [filter] der Vorfilter.
 */
internal class MergeKey(val indexes: List<Int>, val values: List<Any?>, val filter: RowFilter)

/** Die Callbacks eines `MERGE`: Einfügen immer, Ändern nur mit dem Zweig `WHEN MATCHED`. */
internal class MergeCalls(val insert: InsertCall, val update: UpdateCall?)

/** Das Ergebnis der Vorbereitung eines `MERGE` (Form M, `SPEC-036`): bereit zum Ausführen oder abgewiesen. */
internal sealed interface PreparedMerge {
    /**
     * Die überführten Werte und die Callbacks; [update] ist `null` ohne den Zweig `WHEN MATCHED`. [key] gibt den
     * Schlüsselvergleich, [calls] die Callbacks der Tabelle.
     */
    class Ready(
        val table: SimTable,
        val key: MergeKey,
        val update: Map<String, Any?>?,
        val insert: Map<String, Any?>,
        val calls: MergeCalls,
    ) : PreparedMerge

    /** Die Anfrage endet mit dem Fehler [code] (ein Wert von [SimCodes]), bevor etwas gelesen wird. */
    data class Rejected(val code: String, val message: String) : PreparedMerge
}

/**
 * `MERGE … OUTPUT $action` (Form M, `SPEC-036`): die Bibliothek zerlegt die Anweisung in ein Lesen über die
 * Zeilenquelle mit dem Schlüssel als Vorfilter und höchstens einen Callback. Gelesen werden die Zeilen, die die
 * Zeilenquelle liefert; ob der Schlüssel gleich ist, prüft die Bibliothek selbst (der Vorfilter bleibt ein Hinweis).
 * Gleich: mit dem Zweig `WHEN MATCHED` ruft sie `onUpdate` mit den Werten der Zuordnung als `set` und dem Schlüssel als
 * `where`, die Antwort ist die Zeile `UPDATE`; ohne den Zweig ruft sie nichts, die Antwort hat keine Zeile. Nicht
 * gleich: sie ruft `onInsert` (hat die Tabelle nur `onInsertBatch`, mit einer Liste aus einer Zeile), die Antwort ist
 * die Zeile `INSERT`. Ein Wert `NULL` im Vergleich trifft nie. Eine Quellzeile ergibt höchstens eine Ausgabezeile;
 * meldet ein Callback 0, bleibt die Antwort ohne Zeile. Prüfen und Ändern sind nicht atomar: zwei gleichzeitige `MERGE`
 * mit demselben neuen Schlüssel können beide `INSERT` melden, wenn der Integrator den Schlüssel nicht selbst prüft.
 */
internal object SimulationMerges {
    private const val NO_CALLBACK = "Änderung für diese Tabelle nicht verfügbar"

    /** Der Vergleich nennt genau die Schlüsselspalten der Tabelle, und `SET` ändert keine von ihnen. */
    fun comparesKey(statement: Statement.Merge, table: SimTable): Boolean {
        val keys = table.primaryKey.map { SimNames.fold(it) }.toSet()
        val compared = statement.on.map { SimNames.fold(it.target) }
        val changed = statement.update.orEmpty().map { SimNames.fold(it.target) }
        return compared.toSet() == keys && changed.none { it in keys }
    }

    fun prepare(plan: Plan, parameters: List<QueryParameter>): PreparedMerge {
        val statement = plan.statement as Statement.Merge
        val table = plan.table
        val insertCall = insertCall(table)
        val updateCall = table.onUpdate
        val key = values(statement, table, parameters, statement.on)
        val insert = values(statement, table, parameters, statement.insert)
        val update = statement.update?.let { values(statement, table, parameters, it) }
        val invalid = listOfNotNull(key, insert, update).filterIsInstance<Values.Invalid>().firstOrNull()
        val missing = insertCall == null || (statement.update != null && updateCall == null)
        return when {
            missing -> PreparedMerge.Rejected(SimCodes.UNSUPPORTED_SQL, NO_CALLBACK)

            invalid != null -> PreparedMerge.Rejected(SimCodes.INVALID_VALUE, invalid.reason)

            else -> {
                val columns = statement.on.map { requireNotNull(table.column(it.target)) }
                val keyValues = (key as Values.Done).map.values.toList()
                val terms = columns.zip(keyValues) { column, value -> FilterTerm.In(column.name, listOf(value)) }
                PreparedMerge.Ready(
                    table = table,
                    key = MergeKey(columns.map { table.columns.indexOf(it) }, keyValues, RowFilter(terms)),
                    update = (update as Values.Done?)?.map,
                    insert = (insert as Values.Done).map,
                    calls = MergeCalls(requireNotNull(insertCall), updateCall),
                )
            }
        }
    }

    private fun insertCall(table: SimTable): InsertCall? {
        val single = table.onInsert
        val batch = table.onInsertBatch
        return when {
            single != null -> single
            batch != null -> { row -> batch(listOf(row)) }
            else -> null
        }
    }

    private sealed interface Values {
        class Done(val map: Map<String, Any?>) : Values

        class Invalid(val reason: String) : Values
    }

    /** Die Werte der Zuordnungen unter den Namen der Beschreibung, in der Reihenfolge der Zuordnungen. */
    private fun values(
        statement: Statement.Merge,
        table: SimTable,
        parameters: List<QueryParameter>,
        links: List<MergeLink>,
    ): Values {
        val result = LinkedHashMap<String, Any?>(links.size)
        for (link in links) {
            val column = requireNotNull(table.column(link.target)) { "Spalte ist nach der Objektprüfung bekannt" }
            val index = statement.sourceColumns.indexOfFirst { SimNames.fold(it) == SimNames.fold(link.source) }
            when (val converted = SimValues.convert(statement.values[index], column, parameters)) {
                is Converted.Value -> result[column.name] = converted.value
                is Converted.Invalid -> return Values.Invalid(converted.reason)
            }
        }
        return Values.Done(result)
    }
}

/** Die Spalte der Antwort: `$action`, Text, nicht nullbar (Messprotokoll M-13). */
private val ACTION_COLUMN = ResultColumn("\$action", SqlType.UnicodeText, nullable = false)

private const val ACTION_INSERT = "INSERT"
private const val ACTION_UPDATE = "UPDATE"

/**
 * Ein `MERGE`: der Strom beginnt mit dem Ergebnis `$action`, liest dann die Zeilenquelle, ruft höchstens einen Callback
 * und liefert die Ausgabezeile. Ein Fehler der Quelle oder des Callbacks steht als [QueryEvent.Failure] hinter dem
 * Beginn des Ergebnisses (Messprotokoll M-15: `COLMETADATA` vor dem `ERROR`); ein Callback, den [stop] vor dem Sammeln
 * verhindert hat, bleibt ungerufen.
 */
internal class MergeExecution(
    private val prepared: PreparedMerge.Ready,
    private val whenEnded: (MergeExecution) -> Unit,
) : QueryExecution,
    Stoppable {
    private val stopped = AtomicBoolean(false)

    override val events: Flow<QueryEvent> =
        flow { if (!stopped.get()) run() }.onCompletion { whenEnded(this@MergeExecution) }

    override suspend fun close() {
        stop()
        whenEnded(this)
    }

    override fun stop() {
        stopped.set(true)
    }

    private suspend fun FlowCollector<QueryEvent>.run() {
        emit(QueryEvent.ResultStart(listOf(ACTION_COLUMN)))
        val found = guarded { exists() }.getOrElse {
            emit(QueryEvent.Failure(SimCodes.BACKEND_ERROR, "Zeilenquelle fehlgeschlagen"))
            return
        }
        val action = if (found) ACTION_UPDATE.takeIf { prepared.update != null } else ACTION_INSERT
        val count = if (action == null || stopped.get()) 0L else guarded { change(action) }.getOrElse { -1L }
        when {
            count < 0 -> emit(QueryEvent.Failure(SimCodes.BACKEND_ERROR, "Änderung fehlgeschlagen"))

            action != null && count > 0 -> {
                emit(QueryEvent.Row(listOf(action)))
                emit(QueryEvent.ResultEnd(1))
            }

            else -> emit(QueryEvent.ResultEnd(0))
        }
    }

    private suspend fun change(action: String): Long = when (action) {
        ACTION_UPDATE -> requireNotNull(prepared.calls.update)(requireNotNull(prepared.update), prepared.key.filter)
        else -> prepared.calls.insert(prepared.insert)
    }

    /** Wahr, wenn die Zeilenquelle eine Zeile mit gleichem Schlüssel liefert; ein Schlüsselwert `null` trifft nie. */
    private suspend fun exists(): Boolean {
        if (prepared.key.values.any { it == null } || stopped.get()) return false
        val source = prepared.table.rows(SimQuery(prepared.table.columns, prepared.key.filter))
        try {
            var found = false
            while (!found && !stopped.get()) {
                val row = source.next() ?: break
                check(row.size == prepared.table.columns.size) { "Zeile mit falscher Wertezahl" }
                found = matches(row)
            }
            return found
        } finally {
            guarded { source.close() }
        }
    }

    private fun matches(row: List<Any?>): Boolean {
        val key = prepared.key
        return key.indexes.zip(key.values).all { (index, wanted) ->
            val cell = row[index]
            cell != null && wanted != null && SimValues.same(cell, wanted)
        }
    }
}
