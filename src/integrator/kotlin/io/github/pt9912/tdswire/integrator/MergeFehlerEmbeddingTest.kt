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
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Die Fehler eines `MERGE … OUTPUT $action` am Draht (`SPEC-036` Form M, Messprotokoll M-15): ein Text über der Länge
 * seiner Spalte und eine unbekannte Tabelle. Der Fehler von `onInsert` steht in `MergeEmbeddingTest`. Wortlaut und
 * Deklaration wie dort (d-migrate 1.12.0, `bigint`, `nvarchar(4000)`, `date`).
 */
class MergeFehlerEmbeddingTest {
    private val running = CopyOnWriteArrayList<TdsServer>()

    @AfterTest
    fun stopServers() {
        runBlocking { running.forEach { it.stop() } }
    }

    private fun start(source: BackendSource): TdsServer {
        val parsed = assertIs<UserListResult.Valid>(UserList.parse("alice\t{plain}secret\n", true))
        val config = ServerConfig(
            ListenSettings(host = LOOPBACK, port = 0),
            encryption = EncryptionSetting.TEST_UNENCRYPTED,
            auth = AuthSettings(parsed.list, plainTestMode = true),
        )
        val outcome = runBlocking { TdsServer.start(config, source) }
        return assertIs<StartOutcome.Running>(outcome).server.also { running += it }
    }

    private val declarations = "@P0 bigint,@P1 nvarchar(4000),@P2 date"

    private fun merge(table: String) =
        "MERGE INTO [dbo].[$table] AS tgt USING (VALUES ( @P0 ,  @P1 ,  @P2 )) AS src ([Id], [Name], [Eintritt]) " +
            "ON tgt.[Id] = src.[Id] WHEN NOT MATCHED THEN INSERT ([Id], [Name], [Eintritt]) " +
            "VALUES (src.[Id], src.[Name], src.[Eintritt]) OUTPUT \$action;"

    private fun kunden(nameLength: Int, inserted: MutableList<Map<String, Any?>>) = SimTable(
        name = "kunden",
        columns = listOf(
            SimColumn("Id", SqlType.Int64, nullable = false),
            SimColumn("Name", SqlType.UnicodeText, maxLength = nameLength),
            SimColumn("Eintritt", SqlType.Date),
        ),
        primaryKey = listOf("Id"),
        rows = { RowSource { null } },
        onInsert = { values ->
            inserted += values
            1L
        },
    )

    /**
     * Ein Text über der Länge seiner Spalte (`nvarchar(5)`) endet mit ERROR 2628 (State 1, Class 16) hinter dem
     * Ergebnis, INFO 3621 und dem Abschluss `0x0117` ohne `COUNT`, RETURNSTATUS 2628; kein Callback läuft, die
     * Verbindung bleibt offen (Messprotokoll M-15). Ein Text in der Länge der Spalte geht an den Callback. Wächter: die
     * Antwort `TDS_SIM_INVALID_VALUE` (245), ein Callback trotz Fehler, eine Grenze von 4000.
     *
     * @spec SPEC-036
     * @spec SPEC-035
     */
    @Test
    fun einTextUeberDerLaengeDerSpalteEndetMit2628UndRuftNichts() {
        val inserted = CopyOnWriteArrayList<Map<String, Any?>>()
        val server = start(BackendSource.Simulation(listOf(kunden(nameLength = FIVE, inserted))))
        WireClient(server.port).use { client ->
            client.login()
            val failed = client.executeSql(
                merge("kunden"),
                declarations,
                inputBigint("", 1),
                inputText("", "123456"),
                inputDate("", null),
            )
            assertTrue(failed.containsBytes("44 0A 00 00 01 10"), "Nummer 2628, State 1, Class 16")
            assertTrue(failed.containsBytes(INFO_TERMINATED), "INFO 3621")
            assertTrue(failed.endsWithBytes("FF 03 00 17 01 00 00 00 00 00 00 00 00  79 44 0A 00 00  $DONEPROC_OK"))
            assertTrue(inserted.isEmpty(), "kein Callback")
            val fits = client.executeSql(
                merge("kunden"),
                declarations,
                inputBigint("", 1),
                inputText("", "12345"),
                inputDate("", null),
            )
            assertTrue(fits.containsUtf16("INSERT"))
            assertEquals(1, inserted.size)
        }
    }

    /**
     * Eine unbekannte Tabelle endet vor dem Ergebnis mit ERROR 208 (State 1, Class 16), RETURNSTATUS 208 und DONEPROC
     * `ERROR` (Messprotokoll M-15: `ERROR RETURNSTATUS DONEPROC`, ohne COLMETADATA, INFO und DONEINPROC); die
     * Verbindung bleibt offen, eine Folgeanfrage auf ihr erhält ihre eigene Antwort. Wächter: ein COLMETADATA oder
     * INFO vor dem Fehler, eine andere Nummer, Class 20, eine schließende Verbindung.
     *
     * @spec SPEC-036
     * @spec SPEC-038
     * @spec TDS-QA-01
     */
    @Test
    fun eineUnbekannteTabelleEndetMit208OhneErgebnisUndLaesstDieVerbindungOffen() {
        val inserted = CopyOnWriteArrayList<Map<String, Any?>>()
        val server = start(BackendSource.Simulation(listOf(kunden(nameLength = FIVE, inserted))))
        WireClient(server.port).use { client ->
            client.login()
            val failed = client.executeSql(
                merge("gibtesnicht"),
                declarations,
                inputBigint("", 1),
                inputText("", "x"),
                inputDate("", null),
            )
            assertTrue(failed.containsBytes("D0 00 00 00 01 10"), "Nummer 208, State 1, Class 16")
            assertFalse(failed.containsUtf16("\$action"), "kein Ergebnis")
            assertFalse(failed.containsBytes(INFO_TERMINATED), "kein INFO")
            assertTrue(failed.endsWithBytes("79 D0 00 00 00  FE 02 00 E0 00 00 00 00 00 00 00 00 00"))
            val next = client.executeSql(
                merge("kunden"),
                declarations,
                inputBigint("", 1),
                inputText("", "12345"),
                inputDate("", null),
            )
            assertTrue(next.containsUtf16("INSERT"))
            assertEquals(1, inserted.size)
        }
    }

    private companion object {
        const val FIVE = 5
    }
}
