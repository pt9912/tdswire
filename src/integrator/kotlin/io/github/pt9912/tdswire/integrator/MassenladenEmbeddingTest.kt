package io.github.pt9912.tdswire.integrator

import io.github.pt9912.tdswire.config.AuthSettings
import io.github.pt9912.tdswire.config.EncryptionSetting
import io.github.pt9912.tdswire.config.ListenSettings
import io.github.pt9912.tdswire.config.ServerConfig
import io.github.pt9912.tdswire.config.auth.UserList
import io.github.pt9912.tdswire.config.auth.UserListResult
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.runtime.BackendSource
import io.github.pt9912.tdswire.runtime.StartOutcome
import io.github.pt9912.tdswire.runtime.TdsServer
import io.github.pt9912.tdswire.simulation.RowSource
import io.github.pt9912.tdswire.simulation.SimColumn
import io.github.pt9912.tdswire.simulation.SimTable
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Das Massenladen über die öffentliche Fläche (`SPEC-036` Form B, `SPEC-035`, `SPEC-059`): ein Integrator übergibt eine
 * Tabelle mit `onInsertBatch` und bekommt die Zeilen, die ein Client als Nachricht 0x07 sendet, von den Rohbytes. Die
 * Nutzlast ist der Mitschnitt von mssql-jdbc 13.4.0 (d-migrate 1.12.0, `data import`; `BulkLoadReaderTest`); die
 * Antwort ist von Hand gelesen (DONE mit Zähler). Das Source-Set sieht nur die öffentliche Fläche: der Test belegt
 * auch,
 * dass die Verdrahtung des Servers das Massenladen für das Simulations-Backend einschaltet, ohne dass der Integrator
 * etwas dafür tut.
 */
class MassenladenEmbeddingTest {
    private val running = CopyOnWriteArrayList<TdsServer>()

    @AfterTest
    fun stopServers() {
        runBlocking { running.forEach { it.stop() } }
    }

    private fun start(source: BackendSource): TdsServer {
        val parsed = assertIs<UserListResult.Valid>(UserList.parse("alice\t{plain}secret\n", true))
        val config = ServerConfig(
            ListenSettings(LOOPBACK, 0),
            encryption = EncryptionSetting.TEST_UNENCRYPTED,
            auth = AuthSettings(parsed.list, plainTestMode = true),
        )
        val outcome = runBlocking { TdsServer.start(config, source) }
        return assertIs<StartOutcome.Running>(outcome).server.also { running += it }
    }

    private val columns = listOf(
        SimColumn("Id", SqlType.Int64, nullable = false),
        SimColumn("Name", SqlType.UnicodeText),
        SimColumn("Eintritt", SqlType.Date),
    )

    private fun bytes(compact: String): ByteArray = hex(compact.chunked(2).joinToString(" "))

    /**
     * `INSERT BULK` und die Nachricht 0x07 des Treibers laden drei Zeilen in einem Aufruf von `onInsertBatch`: die
     * Werte
     * sind überführt (Datum aus Text, Umlaute, NULL), die Antwort ist DONE mit der Zahl 3; der Schlussteil ohne Zeile
     * ruft nichts und zählt 0. Wächter: eine Verdrahtung ohne die Fähigkeit des Massenladens (die Nachricht wäre ein
     * Fehler), ein Aufruf je Zeile, Werte als Text.
     *
     * @spec SPEC-036
     * @spec SPEC-035
     * @spec SPEC-059
     */
    @Test
    fun eineNachricht0x07LaedtDieZeilenInEinemAufrufVonOnInsertBatch() {
        val batches = CopyOnWriteArrayList<List<Map<String, Any?>>>()
        val table = SimTable(
            name = "kunden",
            columns = columns,
            primaryKey = listOf("Id"),
            rows = { RowSource { null } },
            onInsertBatch = { rows ->
                batches += rows
                rows.size.toLong()
            },
        )
        val server = start(BackendSource.Simulation(listOf(table)))
        val insert = "INSERT BULK [dbo].[kunden] ([Id] BIGINT , [Name] NVARCHAR(4000) , [Eintritt] VARCHAR(10) )  " +
            "with (CHECK_CONSTRAINTS, FIRE_TRIGGERS, ROWS_PER_BATCH = 3)"
        WireClient(server.port).use { client ->
            client.login()
            assertEquals(0, client.batch(insert).doneStatus and DONE_ERROR)
            val loaded = client.bulkLoad(bytes(THREE_ROWS))
            assertEquals(0, loaded.doneStatus and DONE_ERROR)
            assertEquals(THREE, loaded.rowCount)
            assertEquals(0, client.batch(insert).doneStatus and DONE_ERROR)
            val tail = client.bulkLoad(bytes(NO_ROW))
            assertEquals(0, tail.doneStatus and DONE_ERROR)
            assertEquals(0L, tail.rowCount)
        }
        assertEquals(1, batches.size)
        assertEquals(
            listOf(
                mapOf("Id" to 1L, "Name" to "Ada Ünï", "Eintritt" to LocalDate.parse("2026-01-02")),
                mapOf("Id" to 2L, "Name" to null, "Eintritt" to null),
                mapOf("Id" to THREE, "Name" to "Cäcilie", "Eintritt" to LocalDate.parse("2025-12-31")),
            ),
            batches.single(),
        )
    }

    /**
     * Ohne vorangehendes `INSERT BULK` ist die Nachricht 0x07 ein Fehler (DONE mit Fehlerstatus), die Verbindung bleibt
     * offen und beantwortet den nächsten Batch; ein Callback wird nicht gerufen. Wächter: ein Ziel ohne Anweisung, eine
     * geschlossene Verbindung.
     *
     * @spec SPEC-036
     */
    @Test
    fun eineNachricht0x07OhneInsertBulkIstEinFehlerBeiOffenerVerbindung() {
        val calls = CopyOnWriteArrayList<Int>()
        val table = SimTable(
            name = "kunden",
            columns = columns,
            primaryKey = listOf("Id"),
            rows = { RowSource { null } },
            onInsertBatch = { rows ->
                calls += rows.size
                0L
            },
        )
        val server = start(BackendSource.Simulation(listOf(table)))
        WireClient(server.port).use { client ->
            client.login()
            assertTrue(client.bulkLoad(bytes(THREE_ROWS)).doneStatus and DONE_ERROR != 0)
            assertEquals(0, client.batch("SELECT Id FROM kunden WHERE 1 = 0").doneStatus and DONE_ERROR)
        }
        assertEquals(emptyList(), calls)
    }

    private companion object {
        const val THREE = 3L

        /** Nutzlast der ersten Nachricht 0x07 von `data import`, siehe `BulkLoadReaderTest`. */
        const val THREE_ROWS =
            "81030000000000000026080249006400000000000100e7401f0904d00034044e0061006d006500000000000100" +
                "a70a000904d0003408450069006e00740072006900740074" +
                "00d10801000000000000000e004100640061002000dc006e00ef000a00323032362d30312d3032" +
                "d1080200000000000000ffffffffd10803000000000000000e004300e400630069006c00690065000a" +
                "00323032352d31322d3331fd000000000000000000000000"

        /** Nutzlast der letzten Nachricht 0x07: dieselben Spalten, keine Zeile. */
        const val NO_ROW =
            "81030000000000000026080249006400000000000100e7401f0904d00034044e0061006d006500000000000100" +
                "a70a000904d0003408450069006e00740072006900740074" +
                "00fd000000000000000000000000"
    }
}
