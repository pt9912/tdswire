package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryParameter
import io.github.pt9912.tdswire.port.QueryExecution
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import java.util.concurrent.atomic.AtomicBoolean

/** Das Ergebnis der Vorbereitung einer Änderung (`TDS-FA-11.a` Schritt 6): ein Callback-Aufruf oder eine Ablehnung. */
internal sealed interface PreparedChange {
    /** [call] ruft den Callback der Tabelle mit den überführten Werten und liefert dessen Zahl. */
    class Ready(val call: suspend () -> Long) : PreparedChange

    /** Die Anfrage endet mit dem Fehler [code] (ein Wert von [SimCodes]), bevor ein Callback gerufen wird. */
    data class Rejected(val code: String, val message: String) : PreparedChange
}

/**
 * Bereitet Einfügen, Ändern und Löschen (W1 bis W3) vor: überführt jeden Wert in den Typ seiner Spalte
 * (`SPEC-035`), bevor ein Callback gerufen wird, und wählt den Callback der Tabelle. Die Werte stehen unter den
 * Namen der Beschreibung (nicht der Schreibweise im Text) in der Reihenfolge des Textes; eine Spalte, die der Text
 * nicht nennt, fehlt in der Abbildung. `where` ist der Filter der Bedingung wie der Vorfilter einer Auswahl und
 * `null` ohne `WHERE`.
 *
 * Reihenfolge der Prüfung (Setzung, Voreinstellung nach der Reihenfolge von Schritt 6, nicht am Server gemessen):
 * zuerst die Werte, dann der Callback; ein ungültiger Wert ist `TDS_SIM_INVALID_VALUE` auch an einer Tabelle ohne
 * Callback.
 */
internal object SimulationChanges {
    private const val NO_CALLBACK = "Änderung für diese Tabelle nicht verfügbar"

    fun prepare(plan: Plan, parameters: List<QueryParameter>): PreparedChange {
        val table = plan.table
        return when (val statement = plan.statement) {
            is Statement.Insert -> assigned(table, statement.columns.zip(statement.values), parameters) { values ->
                table.onInsert?.let { callback -> PreparedChange.Ready { callback(values) } }
            }

            is Statement.Update -> assigned(table, statement.set.map { it.column to it.value }, parameters) { set ->
                filtered(statement.where, table, parameters) { where ->
                    table.onUpdate?.let { callback -> PreparedChange.Ready { callback(set, where) } }
                }
            }

            is Statement.Delete -> filtered(statement.where, table, parameters) { where ->
                table.onDelete?.let { callback -> PreparedChange.Ready { callback(where) } }
            }

            is Statement.Select -> error("eine Auswahl ist keine Änderung")

            is Statement.Merge -> error("MERGE bereitet SimulationMerges vor")

            is Statement.CreateTable, is Statement.AddColumns -> error("eine Strukturänderung hat keinen Callback")
        }
    }

    private fun noCallback() = PreparedChange.Rejected(SimCodes.UNSUPPORTED_SQL, NO_CALLBACK)

    private inline fun filtered(
        where: List<Term>,
        table: SimTable,
        parameters: List<QueryParameter>,
        callback: (RowFilter?) -> PreparedChange?,
    ): PreparedChange = when (val prepared = RowConditions.prepare(where, table, parameters)) {
        is PreparedCondition.Invalid -> PreparedChange.Rejected(SimCodes.INVALID_VALUE, prepared.reason)
        is PreparedCondition.Ready -> callback(prepared.hint) ?: noCallback()
    }

    private inline fun assigned(
        table: SimTable,
        pairs: List<Pair<String, SimValue>>,
        parameters: List<QueryParameter>,
        callback: (Map<String, Any?>) -> PreparedChange?,
    ): PreparedChange {
        val values = LinkedHashMap<String, Any?>(pairs.size)
        for ((name, value) in pairs) {
            val column = requireNotNull(table.column(name)) { "Spalte ist nach der Objektprüfung bekannt" }
            values[column.name] = when (val converted = SimValues.convert(value, column, parameters)) {
                is Converted.Value -> converted.value
                is Converted.Invalid -> return PreparedChange.Rejected(SimCodes.INVALID_VALUE, converted.reason)
            }
        }
        return callback(values) ?: noCallback()
    }
}

/** Die Strukturänderung ist beim erneuten Planen unter der Sperre abgewiesen: [code] ist ein Wert von [SimCodes]. */
internal class ChangeRejected(val code: String, val reason: String) : RuntimeException(reason)

/** Eine registrierte Ausführung, die [SimulationBackend.cancel] beendet. */
internal interface Stoppable {
    /** Beendet die Ausführung vor dem nächsten Schritt. */
    fun stop()
}

/**
 * Eine Änderung: ruft den Callback beim Sammeln genau einmal und sendet dessen Zahl als [QueryEvent.UpdateCount].
 * Ein Fehler des Callbacks und eine negative Zahl sind ein [QueryEvent.Failure] mit `TDS_BACKEND_ERROR` und fester
 * Meldung, nie dem Text der Ausnahme (Festlegung, nicht am Server gemessen). Eine [ChangeRejected] beim erneuten
 * Planen unter der Sperre einer Strukturänderung ist ein [QueryEvent.Failure] mit dem Code und der Meldung der
 * Planung (`TDS_UNSUPPORTED_SQL`, `TDS_SIM_UNKNOWN_OBJECT`), kein `TDS_BACKEND_ERROR`. [stop] vor dem Sammeln lässt
 * den Callback ungerufen; ein laufender Callback endet nur mit dem Abbruch des Sammlers und hält bis dahin die
 * Sperre der Strukturänderung des Backends, nicht reentrant (`SimulationBackend.structure`).
 */
internal class ChangeExecution(
    private val change: PreparedChange.Ready,
    private val onEnd: (ChangeExecution) -> Unit,
) : QueryExecution,
    Stoppable {
    private val stopped = AtomicBoolean(false)

    override val events: Flow<QueryEvent> =
        flow { if (!stopped.get()) emit(run()) }.onCompletion { onEnd(this@ChangeExecution) }

    override suspend fun close() {
        stop()
        onEnd(this)
    }

    override fun stop() {
        stopped.set(true)
    }

    private suspend fun run(): QueryEvent = guarded { change.call() }.fold(
        onSuccess = { count -> if (count >= 0) QueryEvent.UpdateCount(count) else failure() },
        onFailure = { error ->
            (error as? ChangeRejected)?.let { QueryEvent.Failure(it.code, it.reason) } ?: failure()
        },
    )

    private fun failure() = QueryEvent.Failure(SimCodes.BACKEND_ERROR, "Änderung fehlgeschlagen")
}
