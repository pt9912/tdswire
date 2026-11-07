package io.github.pt9912.tdswire.testserver

import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.runtime.BackendSource
import io.github.pt9912.tdswire.simulation.RowSource
import io.github.pt9912.tdswire.simulation.SimColumn
import io.github.pt9912.tdswire.simulation.SimTable
import java.math.BigDecimal

/**
 * Zeilen der Tabelle `gross`; der .NET-Test (`referenztest-dotnet/Integration`) zählt dieselbe Zahl. `make
 * kopplung-zahlen` hält beide, den Heap, die Spezifikation und die README gleich.
 */
internal const val BIG_ROWS = 500_000

/** Zeichen Text je Zeile von `gross`; wie [BIG_ROWS] im .NET-Test festgehalten. */
internal const val BIG_TEXT_CHARS = 128

/** Zeilen von `kaputt`, bevor die Quelle fehlschlägt; wie [BIG_ROWS] im .NET-Test festgehalten. */
internal const val BROKEN_AFTER = 3

/** Alter von `Ada` und `Cy`; gleich, damit die Auswahl nach `Age` zwei Zeilen trifft. */
private const val ADA_AGE = 36

/** Zeichen des Textes von `bulk_ziel`. */
private const val BULK_TEXT_CHARS = 10

/** Stellen der Dezimalspalten `Score` und `Preis`. */
private const val MONEY_PRECISION = 10

/** Zeile der Standardausgabe beim Schließen einer Quelle von `laufend`; `tools/simulationstest.sh` zählt sie. */
internal const val RUNNING_CLOSED_LINE = "integrationsserver: Quelle laufend geschlossen"

private val ID = SimColumn("Id", SqlType.Int32, nullable = false)

/**
 * Die Tabellen des Integrationslaufs mit Zeilenquellen (die Kotlin-Schnittstelle der Verdrahtung): `person` mit
 * drei Zeilen, `gross` mit [BIG_ROWS] Zeilen zu [BIG_TEXT_CHARS] Zeichen, jede Zeile frisch erzeugt, und `kaputt`,
 * dessen Quelle nach [BROKEN_AFTER] Zeilen mit einer Ausnahme endet, und `laufend` mit einer Quelle ohne Ende
 * (`LaufendeQuelle.kt`), und `bulk_ziel`, das Ziel des Massenladens von `SqlBulkCopy` (siehe [bulkTarget]). Die Tabelle
 * `leer` steht nur in der Datei der Beschreibung (`testserver/resources/integration/sim.yaml`) und hat eine leere
 * Quelle.
 */
internal object IntegrationTables {
    fun all(): List<SimTable> = listOf(person(), big(), broken(), running(), bulkTarget())

    /** `laufend`: Quelle ohne Ende; jedes Schließen einer Quelle schreibt [RUNNING_CLOSED_LINE]. */
    private fun running() = runningTable { println(RUNNING_CLOSED_LINE) }

    private fun person() = SimTable(
        name = "person",
        columns = listOf(
            ID,
            SimColumn("Name", SqlType.UnicodeText),
            SimColumn("Age", SqlType.Int32),
            SimColumn("Score", SqlType.Decimal(MONEY_PRECISION, 2)),
        ),
        primaryKey = listOf("Id"),
        rows = {
            val rows = listOf(
                listOf(1, "Ada", ADA_AGE, BigDecimal("10.50")),
                listOf(2, "Bob", null, BigDecimal("7.25")),
                listOf(3, "Cy", ADA_AGE, null),
            )
            var next = 0
            RowSource { rows.getOrNull(next++) }
        },
    )

    /**
     * `bulk_ziel` (`Id` BIGINT nicht nullbar als Schlüssel, `Text` NVARCHAR(10), `Zahl` INT, `Preis` DECIMAL(10,2)):
     * der
     * Stapel-Callback nimmt die Zeilen einer Nachricht 0x07 ganz oder gar nicht in eine Liste im Arbeitsspeicher auf
     * und
     * wirft bei einem doppelten Schlüssel (die Bibliothek meldet das dem Client als 2627); die Quelle liefert die
     * Liste.
     */
    private fun bulkTarget(): SimTable {
        val stored = mutableListOf<Map<String, Any?>>()
        val columns = listOf(
            SimColumn("Id", SqlType.Int64, nullable = false),
            SimColumn("Text", SqlType.UnicodeText, maxLength = BULK_TEXT_CHARS),
            SimColumn("Zahl", SqlType.Int32),
            SimColumn("Preis", SqlType.Decimal(MONEY_PRECISION, 2)),
        )
        return SimTable(
            name = "bulk_ziel",
            columns = columns,
            primaryKey = listOf("Id"),
            rows = { query ->
                val snapshot = synchronized(stored) { stored.toList() }
                var next = 0
                RowSource { snapshot.getOrNull(next++)?.let { row -> query.columns.map { row[it.name] } } }
            },
            onInsertBatch = { batch ->
                synchronized(stored) {
                    val keys = stored.mapTo(HashSet()) { it["Id"] }
                    check(batch.all { keys.add(it["Id"]) }) { "Schluessel doppelt" }
                    stored += batch
                }
                batch.size.toLong()
            },
        )
    }

    private fun big() = SimTable(
        name = "gross",
        columns = listOf(ID, SimColumn("Text", SqlType.UnicodeText)),
        primaryKey = listOf("Id"),
        rows = {
            var next = 0
            RowSource { if (next < BIG_ROWS) listOf(++next, String(CharArray(BIG_TEXT_CHARS) { 'x' })) else null }
        },
    )

    private fun broken() = SimTable(
        name = "kaputt",
        columns = listOf(ID, SimColumn("Text", SqlType.UnicodeText)),
        primaryKey = listOf("Id"),
        rows = {
            var next = 0
            RowSource { if (next < BROKEN_AFTER) listOf(++next, "zeile") else error("Geheimnis der Quelle") }
        },
    )
}

/**
 * Der Testserver des Integrationslaufs (`make simulations-integration`, Dockerfile-Stage `integrationsserver`):
 * [runTestserver] mit den Tabellen der Datei der Beschreibung und von [IntegrationTables] in einem Katalog. Die erste
 * Zeile nennt den Heap der JVM und den Text von `gross`, damit der Lauf prüfen kann, dass der Text größer ist als
 * der Heap.
 */
fun main() {
    val heap = Runtime.getRuntime().maxMemory()
    val chars = BIG_ROWS.toLong() * BIG_TEXT_CHARS
    println("integrationsserver: Heap max $heap Bytes, Text von gross $chars Zeichen in $BIG_ROWS Zeilen")
    runTestserver { BackendSource.Simulation(it + IntegrationTables.all()) }
}
