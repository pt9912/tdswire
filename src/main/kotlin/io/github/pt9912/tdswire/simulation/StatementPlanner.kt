package io.github.pt9912.tdswire.simulation

/**
 * Namen der Fehlercodes, die das Simulations-Backend meldet (`SPEC-037`, `SPEC-038`). `simulation` darf
 * `protocol` nicht importieren; die Namen sind die von `ErrorCode`, und ein Test in `runtime` hält sie
 * gegeneinander.
 */
object SimCodes {
    /** Anweisung außerhalb von `SPEC-036`. */
    const val UNSUPPORTED_SQL = "TDS_UNSUPPORTED_SQL"

    /** Tabelle oder Spalte ist nicht beschrieben. */
    const val UNKNOWN_OBJECT = "TDS_SIM_UNKNOWN_OBJECT"
}

/** Eine erkannte Anweisung mit den beschriebenen Objekten, die sie nennt. */
data class Plan(
    val statement: Statement,
    val table: SimTable,
    /** Die Spalten der Auswahlliste in der Reihenfolge des Textes, bei `*` alle der Tabelle; sonst leer. */
    val resultColumns: List<SimColumn>,
)

/** Das Ergebnis der Planung. */
sealed interface PlanResult {
    data class Planned(val plan: Plan) : PlanResult

    /** Die Anfrage endet mit dem Fehler [code] (ein Wert von [SimCodes]), bevor etwas gelesen wird. */
    data class Rejected(val code: String, val message: String) : PlanResult
}

/**
 * Die Schritte 1 bis 3 von `TDS-FA-11.a`: Text normalisieren, einer Form zuordnen, Tabelle und Spalten
 * gegen die Beschreibung prüfen. Der Planer ruft weder eine Zeilenquelle noch einen Callback; er liest
 * nur den Text und die Beschreibung. Ohne Schema gilt [DEFAULT_SCHEMA].
 *
 * Die Meldungen nennen nie Teile des Anweisungstextes.
 */
class StatementPlanner(private val catalog: SimCatalog) {
    fun plan(sql: String): PlanResult = when (val recognition = StatementParser.recognize(sql)) {
        is Recognition.NotRecognized ->
            reject(SimCodes.UNSUPPORTED_SQL, "Anweisung nicht erkannt: ${recognition.reason}")

        is Recognition.Recognized -> resolve(recognition.statement)
    }

    private fun reject(code: String, message: String): PlanResult = PlanResult.Rejected(code, message)

    private fun resolve(statement: Statement): PlanResult {
        val table = catalog.find(statement.table.schema, statement.table.name)
            ?: return reject(SimCodes.UNKNOWN_OBJECT, "Tabelle nicht beschrieben")
        return if (namedColumns(statement).any { table.column(it) == null }) {
            reject(SimCodes.UNKNOWN_OBJECT, "Spalte nicht beschrieben")
        } else {
            PlanResult.Planned(Plan(statement, table, resultColumns(statement, table)))
        }
    }

    /** Alle Spaltennamen des Textes: Auswahlliste, Spalten von `INSERT` und `SET` zuerst, dann die Bedingung. */
    private fun namedColumns(statement: Statement): List<String> = when (statement) {
        is Statement.Select -> statement.columns.orEmpty() + statement.where.map { it.column }
        is Statement.Insert -> statement.columns
        is Statement.Update -> statement.set.map { it.column } + statement.where.map { it.column }
        is Statement.Delete -> statement.where.map { it.column }
    }

    private fun resultColumns(statement: Statement, table: SimTable): List<SimColumn> = when {
        statement !is Statement.Select -> emptyList()
        statement.columns == null -> table.columns
        else -> statement.columns.mapNotNull { table.column(it) }
    }
}
