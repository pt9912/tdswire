package io.github.pt9912.tdswire.simulation

/**
 * Namen der Fehlercodes, die das Simulations-Backend meldet (`SPEC-037`, `SPEC-038`, `SPEC-039`). `simulation` darf
 * `protocol` nicht importieren; die Namen sind die von `ErrorCode`, und ein Test in `runtime` hält sie
 * gegeneinander.
 */
internal object SimCodes {
    /** Anweisung außerhalb von `SPEC-036`. */
    const val UNSUPPORTED_SQL = "TDS_UNSUPPORTED_SQL"

    /** Tabelle oder Spalte ist nicht beschrieben. */
    const val UNKNOWN_OBJECT = "TDS_SIM_UNKNOWN_OBJECT"

    /** Ein Wert der Anweisung ist nicht in den Spaltentyp überführbar (`SPEC-039`). */
    const val INVALID_VALUE = "TDS_SIM_INVALID_VALUE"

    /** Die Zeilenquelle ist fehlgeschlagen (`TDS-FA-11.a` Schritt 7). */
    const val BACKEND_ERROR = "TDS_BACKEND_ERROR"
}

/** Eine erkannte Anweisung mit den beschriebenen Objekten, die sie nennt. */
internal data class Plan(
    val statement: Statement,
    val table: SimTable,
    /** Die Spalten der Auswahlliste in der Reihenfolge des Textes, bei `*` alle der Tabelle; sonst leer. */
    val resultColumns: List<SimColumn>,
)

/** Die Tabelle nach einer Strukturänderung (`CREATE TABLE`, `ALTER TABLE … ADD`), wie der Callback sie bekommt. */
internal data class StructurePlan(
    val schema: String,
    val name: String,
    val columns: List<SimColumn>,
    val primaryKey: List<String>,
)

/** Das Ergebnis der Planung. */
internal sealed interface PlanResult {
    data class Planned(val plan: Plan) : PlanResult

    /** Eine Katalogabfrage; [catalog] ist der Stand, den der Anbieter bei der Planung lieferte. */
    data class Catalog(val form: CatalogForm, val catalog: SimCatalog) : PlanResult

    /** Eine Strukturänderung; die Tabelle ist im Zustand nach der Anweisung beschrieben. */
    data class Structure(val plan: StructurePlan) : PlanResult

    /** Ein Text der Form C: keine Tabelle, die Antwort steht fest. */
    data class Fixed(val form: FixedForm) : PlanResult

    /** Die Anfrage endet mit dem Fehler [code] (ein Wert von [SimCodes]), bevor etwas gelesen wird. */
    data class Rejected(val code: String, val message: String) : PlanResult
}

/**
 * Die Schritte 1 bis 3 von `TDS-FA-11.a`: Text normalisieren, einer Form zuordnen, Tabelle und Spalten
 * gegen die Beschreibung prüfen. Der Planer ruft weder eine Zeilenquelle noch einen Callback; er liest
 * nur den Text und die Beschreibung. Ohne Schema gilt [DEFAULT_SCHEMA]. [catalogs] liefert bei jeder Planung den
 * aktuellen Stand der Tabellen; der Planer hält keinen.
 *
 * Die Meldungen nennen nie Teile des Anweisungstextes.
 */
internal class StatementPlanner(private val catalogs: () -> SimCatalog) {
    constructor(catalog: SimCatalog) : this({ catalog })

    fun plan(sql: String): PlanResult = when (val recognition = StatementParser.recognize(sql)) {
        is Recognition.NotRecognized ->
            reject(SimCodes.UNSUPPORTED_SQL, "Anweisung nicht erkannt: ${recognition.reason}")

        is Recognition.Fixed -> PlanResult.Fixed(recognition.form)

        is Recognition.Catalog -> PlanResult.Catalog(recognition.form, catalogs())

        is Recognition.Recognized -> resolve(recognition.statement, catalogs())
    }

    private fun reject(code: String, message: String): PlanResult = PlanResult.Rejected(code, message)

    private fun resolve(statement: Statement, catalog: SimCatalog): PlanResult = when (statement) {
        is Statement.CreateTable -> create(statement, catalog)
        is Statement.AddColumns -> addColumns(statement, catalog)
        else -> resolveTable(statement, catalog)
    }

    private fun resolveTable(statement: Statement, catalog: SimCatalog): PlanResult {
        val table = catalog.find(statement.table.schema, statement.table.name)
            ?: return reject(SimCodes.UNKNOWN_OBJECT, "Tabelle nicht beschrieben")
        return if (namedColumns(statement).any { table.column(it) == null }) {
            reject(SimCodes.UNKNOWN_OBJECT, "Spalte nicht beschrieben")
        } else {
            PlanResult.Planned(Plan(statement, table, resultColumns(statement, table)))
        }
    }

    /**
     * `CREATE TABLE`: die Tabelle darf nicht beschrieben sein, der Schlüssel nennt vorhandene Spalten, und eine
     * Schlüsselspalte ist nicht nullbar (Setzung nach dem Server, am Server dieses Repos nicht gemessen). Ohne
     * Schlüssel ist die Tabelle nicht beschreibbar (`SPEC-035`) und der Text abgewiesen.
     */
    private fun create(statement: Statement.CreateTable, catalog: SimCatalog): PlanResult {
        val schema = statement.table.schema ?: DEFAULT_SCHEMA
        val key = statement.key
        val known = statement.cols.associateBy { SimNames.fold(it.name) }
        return when {
            catalog.find(schema, statement.table.name) != null ->
                reject(SimCodes.UNSUPPORTED_SQL, "Tabelle ist schon beschrieben")

            key == null -> reject(SimCodes.UNSUPPORTED_SQL, "Tabelle ohne Schlüssel nicht beschreibbar")

            key.any { SimNames.fold(it) !in known } ->
                reject(SimCodes.UNKNOWN_OBJECT, "Schlüsselspalte nicht beschrieben")

            else -> {
                val keyed = key.map { SimNames.fold(it) }.toSet()
                val columns = statement.cols.map {
                    if (SimNames.fold(it.name) in keyed) it.copy(nullable = false) else it
                }
                val names = key.map { known.getValue(SimNames.fold(it)).name }
                PlanResult.Structure(StructurePlan(schema, statement.table.name, columns, names))
            }
        }
    }

    /** `ALTER TABLE … ADD`: die Tabelle ist beschrieben, die neuen Spalten nicht; Schlüssel und Name bleiben. */
    private fun addColumns(statement: Statement.AddColumns, catalog: SimCatalog): PlanResult {
        val table = catalog.find(statement.table.schema, statement.table.name)
            ?: return reject(SimCodes.UNKNOWN_OBJECT, "Tabelle nicht beschrieben")
        return if (statement.columns.any { table.column(it.name) != null }) {
            reject(SimCodes.UNSUPPORTED_SQL, "Spalte ist schon beschrieben")
        } else {
            PlanResult.Structure(
                StructurePlan(table.schema, table.name, table.columns + statement.columns, table.primaryKey),
            )
        }
    }

    /** Alle Spaltennamen des Textes: Auswahlliste, Spalten von `INSERT` und `SET` zuerst, dann die Bedingung. */
    private fun namedColumns(statement: Statement): List<String> = when (statement) {
        is Statement.Select -> statement.columns.orEmpty() + whereColumns(statement.where)
        is Statement.Insert -> statement.columns
        is Statement.Update -> statement.set.map { it.column } + whereColumns(statement.where)
        is Statement.Delete -> whereColumns(statement.where)
        is Statement.CreateTable, is Statement.AddColumns -> emptyList()
    }

    /** Die Spalten der Bedingung; ein Konstantenterm nennt keine. */
    private fun whereColumns(where: List<Term>): List<String> = where.filterIsInstance<Term.Named>().map { it.column }

    private fun resultColumns(statement: Statement, table: SimTable): List<SimColumn> = when {
        statement !is Statement.Select -> emptyList()
        statement.columns == null -> table.columns
        else -> statement.columns.mapNotNull { table.column(it) }
    }
}
