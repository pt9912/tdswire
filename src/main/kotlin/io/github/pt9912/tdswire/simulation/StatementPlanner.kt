package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.StatementKind
import io.github.pt9912.tdswire.port.BulkTarget
import io.github.pt9912.tdswire.port.BulkTargetResult

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

    /** Der Einfüge-Callback einer Massenlade-Nachricht ist fehlgeschlagen (Schlüsselverstoß, Messprotokoll M-12). */
    const val KEY_VIOLATION = "TDS_SIM_KEY_VIOLATION"

    /** `CREATE TABLE` einer vorhandenen Tabelle (2714, Messprotokoll M-19). */
    const val OBJECT_EXISTS = "TDS_SIM_OBJECT_EXISTS"

    /** `ALTER TABLE … ADD` einer vorhandenen Spalte (2705, Messprotokoll M-19). */
    const val COLUMN_EXISTS = "TDS_SIM_COLUMN_EXISTS"

    /** `ALTER TABLE` einer Tabelle, die der Katalog nicht führt (4902, Messprotokoll M-19). */
    const val OBJECT_NOT_FOUND = "TDS_SIM_OBJECT_NOT_FOUND"

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
    /** [StatementKind.CREATE_TABLE] oder [StatementKind.ALTER_TABLE]: die Art, die der Abschluss meldet. */
    val kind: StatementKind,
)

/** Das Ergebnis der Planung. */
internal sealed interface PlanResult {
    data class Planned(val plan: Plan) : PlanResult

    /**
     * Eine Katalogabfrage; [catalog] ist der Stand, den der Anbieter bei der Planung lieferte, [literal] das Argument,
     * das der Text selbst trägt (statt eines Parameters), sonst `null`.
     */
    data class Catalog(val form: CatalogForm, val catalog: SimCatalog, val literal: String?) : PlanResult

    /** `INSERT BULK` (Form B): das Ziel oder die Ablehnung, wie [BulkLoadingBackend.bulkTarget] sie meldet. */
    data class Bulk(val result: BulkTargetResult) : PlanResult

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

        is Recognition.Catalog -> PlanResult.Catalog(recognition.form, catalogs(), recognition.literal)

        is Recognition.Bulk -> PlanResult.Bulk(bulk(recognition, catalogs()))

        is Recognition.Recognized -> resolve(recognition.statement, catalogs())
    }

    private fun reject(code: String, message: String): PlanResult = PlanResult.Rejected(code, message)

    /**
     * `INSERT BULK`: die Tabelle und jede genannte Spalte sind beschrieben, und die Tabelle hat einen Callback für
     * Zeilen (`onInsertBatch` oder `onInsert`); sonst ist die Anweisung abgewiesen, bevor eine Nachricht 0x07 folgt.
     */
    private fun bulk(recognition: Recognition.Bulk, catalog: SimCatalog): BulkTargetResult {
        val table = catalog.find(recognition.table.schema, recognition.table.name)
        return when {
            table == null -> BulkTargetResult.Rejected(SimCodes.UNKNOWN_OBJECT, "Tabelle nicht beschrieben")

            recognition.columns.any { table.column(it) == null } ->
                BulkTargetResult.Rejected(SimCodes.UNKNOWN_OBJECT, "Spalte nicht beschrieben")

            table.onInsertBatch == null && table.onInsert == null ->
                BulkTargetResult.Rejected(SimCodes.UNSUPPORTED_SQL, "Einfügen für diese Tabelle nicht verfügbar")

            else -> BulkTargetResult.Ready(BulkTarget(table.schema, table.name))
        }
    }

    private fun resolve(statement: Statement, catalog: SimCatalog): PlanResult = when (statement) {
        is Statement.CreateTable -> create(statement, catalog)
        is Statement.AddColumns -> addColumns(statement, catalog)
        else -> resolveTable(statement, catalog)
    }

    private fun resolveTable(statement: Statement, catalog: SimCatalog): PlanResult {
        val table = catalog.find(statement.table.schema, statement.table.name)
            ?: return reject(SimCodes.UNKNOWN_OBJECT, "Tabelle nicht beschrieben")
        return when {
            namedColumns(statement).any { table.column(it) == null } ->
                reject(SimCodes.UNKNOWN_OBJECT, "Spalte nicht beschrieben")

            statement is Statement.Merge && !SimulationMerges.comparesKey(statement, table) ->
                reject(SimCodes.UNSUPPORTED_SQL, "MERGE vergleicht nicht genau den Schlüssel oder ändert ihn")

            else -> PlanResult.Planned(Plan(statement, table, resultColumns(statement, table)))
        }
    }

    /**
     * `CREATE TABLE`: die Tabelle darf nicht beschrieben sein (sonst [SimCodes.OBJECT_EXISTS], gemessen M-19), der
     * Schlüssel nennt vorhandene Spalten, und eine Schlüsselspalte ist nicht nullbar (Setzung nach dem Server, am
     * Server dieses Repos nicht gemessen). Ohne Schlüssel gelingt die Anweisung, die Tabelle hat dann einen leeren
     * Schlüssel (gemessen M-19).
     */
    private fun create(statement: Statement.CreateTable, catalog: SimCatalog): PlanResult {
        val schema = statement.table.schema ?: DEFAULT_SCHEMA
        val key = statement.key
        val known = statement.cols.associateBy { SimNames.fold(it.name) }
        return when {
            catalog.find(schema, statement.table.name) != null ->
                reject(SimCodes.OBJECT_EXISTS, "Tabelle ist schon beschrieben")

            key != null && key.any { SimNames.fold(it) !in known } ->
                reject(SimCodes.UNKNOWN_OBJECT, "Schlüsselspalte nicht beschrieben")

            else -> {
                val keyed = key.orEmpty().map { SimNames.fold(it) }.toSet()
                val columns = statement.cols.map {
                    if (SimNames.fold(it.name) in keyed) it.copy(nullable = false) else it
                }
                val names = key.orEmpty().map { known.getValue(SimNames.fold(it)).name }
                PlanResult.Structure(StructurePlan(schema, statement.table.name, columns, names, StatementKind.CREATE_TABLE))
            }
        }
    }

    /**
     * `ALTER TABLE … ADD`: die Tabelle ist beschrieben (sonst [SimCodes.OBJECT_NOT_FOUND]), die neuen Spalten nicht
     * (sonst [SimCodes.COLUMN_EXISTS]); Schlüssel und Name bleiben. Beide Fehler sind gemessen (M-19).
     */
    private fun addColumns(statement: Statement.AddColumns, catalog: SimCatalog): PlanResult {
        val table = catalog.find(statement.table.schema, statement.table.name)
            ?: return reject(SimCodes.OBJECT_NOT_FOUND, "Tabelle nicht beschrieben")
        return if (statement.columns.any { table.column(it.name) != null }) {
            reject(SimCodes.COLUMN_EXISTS, "Spalte ist schon beschrieben")
        } else {
            PlanResult.Structure(
                StructurePlan(
                    table.schema,
                    table.name,
                    table.columns + statement.columns,
                    table.primaryKey,
                    StatementKind.ALTER_TABLE,
                ),
            )
        }
    }

    /** Alle Spaltennamen des Textes: Auswahlliste, Spalten von `INSERT` und `SET` zuerst, dann die Bedingung. */
    private fun namedColumns(statement: Statement): List<String> = when (statement) {
        is Statement.Select -> statement.columns.orEmpty() + whereColumns(statement.where)

        is Statement.Insert -> statement.columns

        is Statement.Update -> statement.set.map { it.column } + whereColumns(statement.where)

        is Statement.Delete -> whereColumns(statement.where)

        is Statement.Merge ->
            statement.on.map { it.target } + statement.update.orEmpty().map { it.target } +
                statement.insert.map { it.target }

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
