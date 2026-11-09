package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.SqlType

/** Schema, das gilt, wenn eine Anweisung oder die Datei keines nennt (`TDS-FA-11.a` Schritt 3). */
internal const val DEFAULT_SCHEMA = "dbo"

/**
 * Eine beschriebene Tabelle (`SPEC-035`). Die Validierung gilt für die Kotlin-Schnittstelle und den Leser
 * der Datei gleichermaßen: Spaltennamen sind ohne Beachtung der Schreibweise eindeutig, es gibt mindestens
 * eine Spalte, jeder Schlüsseleintrag nennt eine vorhandene, nicht nullbare Spalte (Setzung, nicht am Server
 * gemessen). Der Schlüssel darf leer sein: der echte Server nimmt `CREATE TABLE` ohne Schlüssel an (Messprotokoll
 * M-19). Der Schlüssel wirkt weder auf Resultset noch auf Callbacks.
 *
 * [onInsertBatch] nimmt die Zeilen einer Massenlade-Nachricht (Pakettyp 0x07, `SPEC-036` Form B) als Liste: die
 * Bibliothek ruft ihn einmal je Nachricht mit allen Zeilen. Zusage an den Integrator: der Aufruf gilt ganz oder gar
 * nicht; bei Erfolg liefert er die Zahl der übernommenen Zeilen, bei einem Fehler wirft er, ohne eine Zeile der
 * Nachricht übernommen zu haben (die Bibliothek nimmt nichts zurück, `ADR-0044`). Hat die Tabelle ihn nicht, ist
 * `INSERT BULK` für sie abgewiesen (`TDS_UNSUPPORTED_SQL`), [onInsert] wird dafür nicht gerufen. Die Grenze der Zusage:
 * gemessen am echten Server ist ein Fall (Schlüsselverstoß, drei Zeilen, ein Paket), darüber hinaus ist sie eine
 * Setzung; die Fehlernummer 2627 ist für den Schlüsselverstoß gemessen und für jede andere Ursache ein Sammelcode.
 *
 * [uniqueKeys], [foreignKeys], [indexes] und [checks] beschreiben die Elemente der Tabelle für die Katalogabfragen
 * (`SPEC-035`); sie wirken weder auf Resultset noch auf Callbacks. Namen von Bedingungen sind in der Tabelle ohne
 * Beachtung der Schreibweise eindeutig, ebenso Namen von Indizes (eine Eindeutigkeit zählt als Index); jede genannte
 * Spalte ist in der Tabelle vorhanden (Setzung, nicht am Server gemessen). Ob die Zieltabelle eines Fremdschlüssels
 * beschrieben ist, prüft der Katalog nicht, nur die Anweisung `CREATE TABLE` und `ALTER TABLE` der Bibliothek.
 *
 * `columns`, `primaryKey` und die Listen der Elemente werden nicht kopiert: ein Konstruktor mit dreizehn Parametern
 * erlaubt die Regel `LongParameterList` nur an einer `data class`, und deren Konstruktor kann die Listen nicht
 * ersetzen. Der Aufrufer ändert sie nach der Übergabe nicht; [column] liest einen Index, der bei der Übergabe gebaut
 * wurde.
 */
public data class SimTable(
    val schema: String = DEFAULT_SCHEMA,
    val name: String,
    val columns: List<SimColumn>,
    val primaryKey: List<String>,
    val rows: suspend (SimQuery) -> RowSource,
    val onInsert: (suspend (values: Map<String, Any?>) -> Long)? = null,
    val onUpdate: (suspend (set: Map<String, Any?>, where: RowFilter?) -> Long)? = null,
    val onDelete: (suspend (where: RowFilter?) -> Long)? = null,
    val onInsertBatch: (suspend (rows: List<Map<String, Any?>>) -> Long)? = null,
    val uniqueKeys: List<SimUniqueKey> = emptyList(),
    val foreignKeys: List<SimForeignKey> = emptyList(),
    val indexes: List<SimIndex> = emptyList(),
    val checks: List<SimCheck> = emptyList(),
) {
    private val byName: Map<String, SimColumn>

    init {
        require(schema.isNotEmpty()) { "schema darf nicht leer sein" }
        require(name.isNotEmpty()) { "name darf nicht leer sein" }
        require(columns.isNotEmpty()) { "Tabelle $name braucht mindestens eine Spalte" }
        val duplicate = SimNames.firstDuplicate(columns.map { it.name })
        require(duplicate == null) { "Spalte doppelt in Tabelle $name (ohne Beachtung der Schreibweise)" }
        byName = columns.associateBy { SimNames.fold(it.name) }
        require(SimNames.firstDuplicate(primaryKey) == null) { "Schlüssel von Tabelle $name nennt eine Spalte doppelt" }
        for (entry in primaryKey) {
            val column = byName[SimNames.fold(entry)]
            requireNotNull(column) { "Schlüsselspalte $entry ist in Tabelle $name nicht vorhanden" }
            require(!column.nullable) { "Schlüsselspalte $entry von Tabelle $name darf nicht nullbar sein" }
        }
        SimElementChecks.validate(this)
    }

    /** Die Spalte namens [columnName] ohne Beachtung der Schreibweise oder `null`. */
    internal fun column(columnName: String): SimColumn? = byName[SimNames.fold(columnName)]
}

/** Höchste Precision einer Dezimalzahl (`SPEC-003`). */
private const val MAX_DECIMAL_PRECISION = 38

/** Höchste Länge eines Textes in UTF-16-Einheiten (`SPEC-003`); zugleich der Vorgabewert von [SimColumn.maxLength]. */
internal const val MAX_TEXT_LENGTH = 4000

/** Der Wert von [SimColumn.maxLength] für `NVARCHAR(MAX)`: der Katalog meldet `max_length` -1. */
internal const val UNBOUNDED_TEXT = -1

/**
 * Eine Spalte; der Typ stammt aus dem Mindestkatalog von [SqlType], [nullable] sagt, ob NULL vorkommen darf.
 * Eine Dezimalzahl hat höchstens 38 Stellen (`SPEC-003`); die Grenze gilt für jede Spalte.
 *
 * [maxLength] ist die Länge eines Textes in Zeichen, wie der Katalog sie meldet (`nvarchar(<maxLength>)`); sie
 * gilt nur für [SqlType.UnicodeText], liegt zwischen 1 und 4000 oder ist -1 (`nvarchar(max)`, der Katalog meldet
 * `max_length` -1) und wirkt weder auf Zeilen noch auf die Metadaten eines Ergebnisses (dort gilt `SPEC-003`).
 *
 * [defaultDefinition] ist der Text, den der Katalog als `default_definition` der Spalte meldet, mit den Klammern des
 * Servers (`((1))` für `DEFAULT 1`, `(N'neu')` für `DEFAULT N'neu'`, Messprotokoll M-21); `null` heißt: ohne
 * Standardwert.
 * Die Bibliothek setzt den Wert nicht ein: ein `INSERT` ohne die Spalte bleibt ohne sie (`SPEC-035`).
 */
public data class SimColumn(
    val name: String,
    val type: SqlType,
    val nullable: Boolean = true,
    val maxLength: Int = MAX_TEXT_LENGTH,
    val defaultDefinition: String? = null,
) {
    init {
        require(name.isNotEmpty()) { "Spaltenname darf nicht leer sein" }
        require(type !is SqlType.Decimal || type.precision <= MAX_DECIMAL_PRECISION) {
            "Spalte $name: Precision über $MAX_DECIMAL_PRECISION"
        }
        require(maxLength == UNBOUNDED_TEXT || maxLength in 1..MAX_TEXT_LENGTH) {
            "Spalte $name: maxLength außerhalb 1..$MAX_TEXT_LENGTH und nicht $UNBOUNDED_TEXT"
        }
        require(defaultDefinition == null || defaultDefinition.isNotEmpty()) {
            "Spalte $name: defaultDefinition darf nicht leer sein"
        }
    }
}

/** Was das Backend der Zeilenquelle als Hinweis mitgibt: die Spalten der Zeilen und ein Vorfilter. */
public data class SimQuery(val columns: List<SimColumn>, val filter: RowFilter?)

/** Die Terme einer Bedingung; alle gelten gemeinsam (UND). */
public data class RowFilter(val terms: List<FilterTerm>)

/** Ein Term einer Bedingung (`SPEC-035`): `=` und `IN` als [In], `IS [NOT] NULL` als [IsNull]. */
public sealed interface FilterTerm {
    /** Die Spalte [column] steht in [values]; `=` ist die Liste mit einem Wert. */
    public data class In(val column: String, val values: List<Any?>) : FilterTerm

    /** Die Spalte [column] ist NULL, mit [negated] ist sie es nicht. */
    public data class IsNull(val column: String, val negated: Boolean) : FilterTerm
}

/** Die Zeilen einer Tabelle, eine nach der anderen (`SPEC-035`). */
public fun interface RowSource {
    /** Die Werte der nächsten Zeile in der Reihenfolge von [SimQuery.columns] oder `null` am Ende. */
    public suspend fun next(): List<Any?>?

    /** Gibt die Ressourcen frei; wird genau einmal gerufen. */
    public fun close() {}
}

/**
 * Der Satz beschriebener Tabellen. Tabellen sind über Schema und Name ohne Beachtung der Schreibweise
 * eindeutig (Setzung, nicht am Server gemessen).
 */
public class SimCatalog(tables: List<SimTable>) {
    /** Die Tabellen in der übergebenen Reihenfolge; eine Kopie der übergebenen Liste. */
    public val tables: List<SimTable> = tables.toList()

    private val byKey: Map<String, SimTable>

    init {
        val map = LinkedHashMap<String, SimTable>(tables.size)
        for (table in tables) {
            val duplicate = map.put(key(table.schema, table.name), table)
            require(duplicate == null) { "Tabelle doppelt (ohne Beachtung der Schreibweise): ${table.name}" }
        }
        byKey = map
    }

    /** Die Tabelle [name] im Schema [schema] (ohne Angabe [DEFAULT_SCHEMA]) oder `null`. */
    internal fun find(schema: String?, name: String): SimTable? = byKey[key(schema ?: DEFAULT_SCHEMA, name)]

    private fun key(schema: String, name: String): String = SimNames.fold(schema) + "\u0000" + SimNames.fold(name)
}
