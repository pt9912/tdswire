package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.simulation.RowSource
import io.github.pt9912.tdswire.simulation.SimColumn
import io.github.pt9912.tdswire.simulation.SimTable
import java.math.BigDecimal

/** Zeilen der Tabelle `gross`; der .NET-Test (`referenztest-dotnet/Integration`) zählt dieselbe Zahl. */
internal const val BIG_ROWS = 500_000

/** Zeichen Text je Zeile von `gross`; wie [BIG_ROWS] im .NET-Test festgehalten. */
internal const val BIG_TEXT_CHARS = 128

/** Zeilen von `kaputt`, bevor die Quelle fehlschlägt; wie [BIG_ROWS] im .NET-Test festgehalten. */
internal const val BROKEN_AFTER = 3

private val ID = SimColumn("Id", SqlType.Int32, nullable = false)

/**
 * Die Tabellen des Integrationslaufs mit Zeilenquellen (die Kotlin-Schnittstelle der Verdrahtung): `person` mit
 * drei Zeilen, `gross` mit [BIG_ROWS] Zeilen zu [BIG_TEXT_CHARS] Zeichen, jede Zeile frisch erzeugt, und `kaputt`,
 * dessen Quelle nach [BROKEN_AFTER] Zeilen mit einer Ausnahme endet. Die Tabelle `leer` steht nur in der Datei der
 * Beschreibung (`src/test/resources/integration/sim.yaml`) und hat eine leere Quelle.
 */
internal object IntegrationTables {
    fun all(): List<SimTable> = listOf(person(), big(), broken())

    private fun person() = SimTable(
        name = "person",
        columns = listOf(
            ID,
            SimColumn("Name", SqlType.UnicodeText),
            SimColumn("Age", SqlType.Int32),
            SimColumn("Score", SqlType.Decimal(10, 2)),
        ),
        primaryKey = listOf("Id"),
        rows = {
            val rows = listOf(
                listOf(1, "Ada", 36, BigDecimal("10.50")),
                listOf(2, "Bob", null, BigDecimal("7.25")),
                listOf(3, "Cy", 36, null),
            )
            var next = 0
            RowSource { rows.getOrNull(next++) }
        },
    )

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
 * Der Serverprozess des Integrationslaufs (`make simulations-integration`, Dockerfile-Stage `integrationsserver`):
 * `Main` mit den Tabellen von [IntegrationTables]. Die erste Zeile nennt den Heap der JVM und den Text von `gross`,
 * damit der Lauf prüfen kann, dass der Text größer ist als der Heap.
 */
fun main() {
    val heap = Runtime.getRuntime().maxMemory()
    val chars = BIG_ROWS.toLong() * BIG_TEXT_CHARS
    println("integrationsserver: Heap max $heap Bytes, Text von gross $chars Zeichen in $BIG_ROWS Zeilen")
    runServer(Wiring(log = ServerLog.STDOUT, requests = RequestSource(simulationTables = IntegrationTables.all())))
}
