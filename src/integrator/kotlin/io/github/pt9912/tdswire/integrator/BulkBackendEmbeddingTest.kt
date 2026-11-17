package io.github.pt9912.tdswire.integrator

import io.github.pt9912.tdswire.config.AuthSettings
import io.github.pt9912.tdswire.config.EncryptionSetting
import io.github.pt9912.tdswire.config.ListenSettings
import io.github.pt9912.tdswire.config.RequestLimits
import io.github.pt9912.tdswire.config.RequestSettings
import io.github.pt9912.tdswire.config.ServerConfig
import io.github.pt9912.tdswire.config.auth.UserList
import io.github.pt9912.tdswire.config.auth.UserListResult
import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.domain.ResultColumn
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.port.BulkLoadingBackend
import io.github.pt9912.tdswire.port.BulkProbeColumn
import io.github.pt9912.tdswire.port.BulkProbeResult
import io.github.pt9912.tdswire.port.BulkRows
import io.github.pt9912.tdswire.port.BulkTarget
import io.github.pt9912.tdswire.port.BulkTargetResult
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.port.QueryExecution
import io.github.pt9912.tdswire.runtime.BackendSource
import io.github.pt9912.tdswire.runtime.StartOutcome
import io.github.pt9912.tdswire.runtime.TdsServer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val TEXT_CHARS = 4000
private const val DATE_CHARS = 10
private const val BACKEND_ERROR_NUMBER = 50004
private const val UNSUPPORTED_SQL_NUMBER = 50001
private const val UNKNOWN_OBJECT_NUMBER = 208
private const val LIMIT_BYTES = 65535
private const val MANY_ROWS = 3000
private const val INTERNAL_ERROR_NUMBER = 50007
private val EXPECTED_OBSERVATION = listOf(
    "geschlossen ohne Antwort",
    "geschlossen ohne Antwort",
    "Fehler [$INTERNAL_ERROR_NUMBER]",
)
private const val FIRST_ROW = "d10801"
private const val SECOND_ROW = "d10802"
private const val DONE_HEX_CHARS = 26
private const val LOAD_COMMAND = 0x00F0
private const val FAIL_TABLE = "kaputt"
private const val UNKNOWN_CODE_TABLE = "fremd"
private const val MISSING_TABLE = "fehlt"
private const val THROWING_TABLE = "wirft"
private const val LOAD_THROWING_TABLE = "ladewirft"

/** Ein Aufruf von `load`: das Ziel, die Namen der Spalten und die Zeilen. */
private class Loaded(val target: BulkTarget, val columns: List<String>, val rows: List<List<Any?>>)

/**
 * Ein eigenes Backend, das [BulkLoadingBackend] zusätzlich zu [QueryBackend] trägt und nur die öffentliche Fläche
 * sieht. Es erkennt die Probe an `SELECT @@TRANCOUNT` und `OBJECT_ID('<Name>')` und `INSERT BULK` am Anfang des
 * Textes; `kunden` ist beschrieben, [FAIL_TABLE] lädt mit einem Fehler, [UNKNOWN_CODE_TABLE] lehnt mit einem Namen ab,
 * den die Bibliothek nicht kennt, [MISSING_TABLE] lehnt mit `TDS_SIM_UNKNOWN_OBJECT` ab.
 */
private class BulkBackend :
    QueryBackend,
    BulkLoadingBackend {
    val executed = CopyOnWriteArrayList<String>()
    val loaded = CopyOnWriteArrayList<Loaded>()

    override suspend fun execute(request: QueryRequest): QueryExecution {
        executed += request.sql
        val events: List<QueryEvent> = listOf(QueryEvent.UpdateCount(0))
        return object : QueryExecution {
            override val events: Flow<QueryEvent> = events.asFlow()

            override suspend fun close() = Unit
        }
    }

    override suspend fun cancel(sessionId: String, requestId: String) = Unit

    override fun bulkTarget(sql: String): BulkTargetResult? {
        val name = INSERT_BULK.find(sql)?.groupValues?.get(1) ?: return null
        check(name != THROWING_TABLE) { "bulkTarget wirft" }
        return when (name) {
            MISSING_TABLE -> BulkTargetResult.Rejected("TDS_SIM_UNKNOWN_OBJECT", "Tabelle nicht beschrieben")
            UNKNOWN_CODE_TABLE -> BulkTargetResult.Rejected("TDS_GIBT_ES_NICHT", "Name unbekannt")
            else -> BulkTargetResult.Ready(BulkTarget("dbo", name))
        }
    }

    override fun bulkProbe(sql: String): BulkProbeResult? {
        val name = if (sql.startsWith("SELECT @@TRANCOUNT")) OBJECT_ID.find(sql)?.groupValues?.get(1) else null
        check(name != THROWING_TABLE) { "bulkProbe wirft" }
        return when (name) {
            null -> null

            "kunden" -> BulkProbeResult.Ready(
                listOf(
                    BulkProbeColumn(ResultColumn("Id", SqlType.Int64, nullable = false), TEXT_CHARS),
                    BulkProbeColumn(ResultColumn("Name", SqlType.UnicodeText, nullable = true), TEXT_CHARS),
                    BulkProbeColumn(
                        ResultColumn("Eintritt", SqlType.UnicodeText, nullable = true, maxLength = DATE_CHARS),
                        DATE_CHARS,
                    ),
                ),
            )

            else -> BulkProbeResult.Rejected("TDS_SIM_UNKNOWN_OBJECT", "Tabelle nicht beschrieben")
        }
    }

    override suspend fun load(target: BulkTarget, rows: BulkRows): QueryEvent {
        loaded += Loaded(target, rows.columns.map { it.name }, rows.rows)
        check(target.table != LOAD_THROWING_TABLE) { "load wirft" }
        return if (target.table == FAIL_TABLE) {
            QueryEvent.Failure("TDS_BACKEND_ERROR", "Text des Backends, nicht für den Client")
        } else {
            QueryEvent.UpdateCount(rows.rows.size.toLong())
        }
    }

    private companion object {
        val INSERT_BULK = Regex("""^\s*INSERT BULK \[dbo]\.\[(\w+)]""")
        val OBJECT_ID = Regex("""OBJECT_ID\('\[dbo]\.\[(\w+)]'\)""")
    }
}

/** Dasselbe Backend ohne die Fähigkeit: ein `QueryBackend` und sonst nichts. */
private class PlainBackend : QueryBackend {
    val executed = CopyOnWriteArrayList<String>()

    override suspend fun execute(request: QueryRequest): QueryExecution {
        executed += request.sql
        val events: List<QueryEvent> = listOf(QueryEvent.UpdateCount(0))
        return object : QueryExecution {
            override val events: Flow<QueryEvent> = events.asFlow()

            override suspend fun close() = Unit
        }
    }

    override suspend fun cancel(sessionId: String, requestId: String) = Unit
}

/** Sendet eine Nachricht vom Typ [type]; `null`, wenn der Server schließt, ohne zu antworten. */
private fun WireClient.orClosed(type: Int, payload: ByteArray): Answer? {
    send(type, payload)
    val first = try {
        input.read()
    } catch (expectedReset: IOException) {
        -1
    }
    if (first < 0) return null
    input.unread(first)
    return Answer(readMessage().second)
}

private fun bytes(compact: String): ByteArray = hex(compact.chunked(2).joinToString(" "))

/**
 * Eine gültige Nachricht 0x07 mit [MANY_ROWS] Zeilen (die erste Zeile des Mitschnitts wiederholt, gut 100 000
 * Byte): sie ist nur wegen ihrer Größe abzuweisen, nicht wegen ihres Inhalts.
 */
private fun manyRows(): String {
    val all = MassenladenEmbeddingTest.THREE_ROWS
    val first = all.indexOf(FIRST_ROW)
    val row = all.substring(first, all.indexOf(SECOND_ROW))
    return all.substring(0, first) + row.repeat(MANY_ROWS) + all.takeLast(DONE_HEX_CHARS)
}

private fun probe(table: String) = "SELECT @@TRANCOUNT;\nDECLARE @Object_ID INT = OBJECT_ID('[dbo].[$table]');\n"

private fun insert(table: String) =
    "INSERT BULK [dbo].[$table] ([Id] BIGINT , [Name] NVARCHAR(4000) , [Eintritt] VARCHAR(10) )  " +
        "with (CHECK_CONSTRAINTS, FIRE_TRIGGERS, ROWS_PER_BATCH = 3)"

/**
 * Das Massenladen für ein eigenes Backend über die öffentliche Fläche (`ADR-0052`, `SPEC-035`, `SPEC-036` Form B,
 * `SPEC-059`): ein Integrator trägt [BulkLoadingBackend] an seinem [QueryBackend] und bekommt die Probe der
 * Zieltabelle, `INSERT BULK` und die Zeilen einer Nachricht 0x07 über die Rohbytes. Die Nutzlast ist der Mitschnitt von
 * mssql-jdbc 13.4.0 (`MassenladenEmbeddingTest`); der Text der Probe ist gekürzt (nur `SELECT @@TRANCOUNT` und
 * `OBJECT_ID('<Name>')` des Wortlauts von Form B, den das Backend selbst erkennt); die Annahme der echten Probe durch
 * den Referenzclient belegt der Lauf `make eigenes-backend`, nicht dieser Test.
 */
class BulkBackendEmbeddingTest {
    private val running = CopyOnWriteArrayList<TdsServer>()

    @AfterTest
    fun stopServers() {
        runBlocking { running.forEach { it.stop() } }
    }

    private fun start(backend: QueryBackend, requests: RequestSettings = RequestSettings()): TdsServer {
        val parsed = assertIs<UserListResult.Valid>(UserList.parse("alice\t{plain}secret\n", true))
        val config = ServerConfig(
            ListenSettings(host = LOOPBACK, port = 0),
            encryption = EncryptionSetting.TEST_UNENCRYPTED,
            auth = AuthSettings(parsed.list, plainTestMode = true),
            requests = requests,
        )
        val outcome = runBlocking { TdsServer.start(config, BackendSource.Custom(backend)) }
        return assertIs<StartOutcome.Running>(outcome).server.also { running += it }
    }

    /**
     * Die Probe der Zieltabelle, `INSERT BULK` und zwei Nachrichten 0x07 auf einer Verbindung: das Backend bekommt das
     * Ziel und die Zeilen mit Namen und Werten (NULL und Umlaute; das Datum bleibt ein Text, weil die Spalte
     * `VARCHAR` ist), die Antwort trägt die Zahl des Backends; die Probe trägt die Namen der Spalten; weder die Probe
     * noch `INSERT BULK` erreichen `execute`. Wächter: ein Kern, der nur `QueryBackend` kennt (der Text ginge an
     * `execute`), ein Aufruf je Zeile, eine Antwort ohne Zahl, ein Ziel ohne Schema.
     *
     * @spec SPEC-035
     * @spec SPEC-036
     * @spec SPEC-059
     */
    @Test
    fun einEigenesBackendMitDerFaehigkeitNimmtProbeInsertBulkUndZweiNachrichtenAn() {
        val backend = BulkBackend()
        val server = start(backend)
        WireClient(server.port).use { client ->
            client.login()
            val probed = client.batch(probe("kunden"))
            assertEquals(emptyList(), probed.errorNumbers())
            assertTrue(probed.contains("Eintritt"))
            assertEquals(0, client.batch(insert("kunden")).doneStatus and DONE_ERROR)
            val first = client.bulkLoad(bytes(MassenladenEmbeddingTest.THREE_ROWS))
            assertEquals(0, first.doneStatus and DONE_ERROR)
            assertEquals(MassenladenEmbeddingTest.THREE, first.rowCount)
            assertEquals(LOAD_COMMAND, first.command)
            assertEquals(0, client.batch(insert("kunden")).doneStatus and DONE_ERROR)
            val second = client.bulkLoad(bytes(MassenladenEmbeddingTest.THREE_ROWS))
            assertEquals(MassenladenEmbeddingTest.THREE, second.rowCount)
        }
        assertEquals(emptyList(), backend.executed.toList())
        assertEquals(2, backend.loaded.size)
        val call = backend.loaded.first()
        assertEquals(BulkTarget("dbo", "kunden"), call.target)
        assertEquals(listOf("Id", "Name", "Eintritt"), call.columns)
        assertEquals(
            listOf(
                listOf(1L, "Ada Ünï", "2026-01-02"),
                listOf(2L, null, null),
                listOf(MassenladenEmbeddingTest.THREE, "Cäcilie", "2025-12-31"),
            ),
            call.rows,
        )
    }

    /**
     * Ein Fehler des Backends in `load` (`TDS_BACKEND_ERROR`) erreicht den Client als Nummer 50004 mit Fehlerstatus im
     * DONE, die Verbindung bleibt offen und lädt die nächste Nachricht in eine andere Tabelle. Wächter: ein Fehler, der
     * als Erfolg zählt (DONE ohne Fehlerstatus), eine andere Nummer, eine geschlossene Verbindung.
     *
     * @spec SPEC-035
     * @spec SPEC-059
     */
    @Test
    fun einFehlerDesBackendsInLoadIstTdsBackendErrorBeiOffenerVerbindung() {
        val backend = BulkBackend()
        val server = start(backend)
        WireClient(server.port).use { client ->
            client.login()
            assertEquals(0, client.batch(insert(FAIL_TABLE)).doneStatus and DONE_ERROR)
            val failed = client.bulkLoad(bytes(MassenladenEmbeddingTest.THREE_ROWS))
            assertTrue(failed.doneStatus and DONE_ERROR != 0)
            assertEquals(listOf(BACKEND_ERROR_NUMBER), failed.errorNumbers())
            assertFalse(failed.contains("Text des Backends"))
            assertEquals(0, client.batch(insert("kunden")).doneStatus and DONE_ERROR)
            assertEquals(
                MassenladenEmbeddingTest.THREE,
                client.bulkLoad(bytes(MassenladenEmbeddingTest.THREE_ROWS)).rowCount,
            )
        }
        assertEquals(listOf(FAIL_TABLE, "kunden"), backend.loaded.map { it.target.table })
    }

    /**
     * Das IGNORE-Bit einer Nachricht 0x07 verwirft sie ohne Aufruf von `load`: die Antwort zählt 0 ohne Fehler, die
     * Verbindung bleibt offen. Wächter: ein Aufruf von `load` trotz des Bits, ein Fehler als Antwort.
     *
     * @spec SPEC-036
     * @spec SPEC-059
     */
    @Test
    fun dasIgnoreBitVerwirftDieNachrichtOhneAufrufVonLoad() {
        val backend = BulkBackend()
        val server = start(backend)
        WireClient(server.port).use { client ->
            client.login()
            assertEquals(0, client.batch(insert("kunden")).doneStatus and DONE_ERROR)
            val ignored = client.bulkLoad(bytes(MassenladenEmbeddingTest.THREE_ROWS), ignore = true)
            assertEquals(0, ignored.doneStatus and DONE_ERROR)
            assertEquals(0L, ignored.rowCount)
            assertEquals(0, client.batch("SELECT 1").doneStatus and DONE_ERROR)
        }
        assertEquals(emptyList(), backend.loaded.map { it.target })
    }

    /**
     * Die Ablehnung in `bulkTarget` und in `bulkProbe` endet mit dem Fehler des genannten Namens
     * (`TDS_SIM_UNKNOWN_OBJECT` ist die Nummer 208) bei offener Verbindung und setzt kein Ziel: die folgende Nachricht
     * 0x07 ist ein Fehler, `load` wird nicht gerufen. Ein Name, den die Bibliothek nicht kennt, ist
     * `TDS_INTERNAL_ERROR` (50007) und schließt die Verbindung. Wächter: eine Ablehnung, die ein Ziel setzt, ein
     * anderer Name im Fehler, eine offene Verbindung nach dem unbekannten Namen.
     *
     * @spec SPEC-036
     * @spec SPEC-059
     */
    @Test
    fun eineAblehnungSetztKeinZielUndEinUnbekannterNameSchliesstDieVerbindung() {
        val backend = BulkBackend()
        val server = start(backend)
        WireClient(server.port).use { client ->
            client.login()
            val probed = client.batch(probe(MISSING_TABLE))
            assertEquals(listOf(UNKNOWN_OBJECT_NUMBER), probed.errorNumbers())
            val rejected = client.batch(insert(MISSING_TABLE))
            assertEquals(listOf(UNKNOWN_OBJECT_NUMBER), rejected.errorNumbers())
            val orphan = client.bulkLoad(bytes(MassenladenEmbeddingTest.THREE_ROWS))
            assertEquals(listOf(UNSUPPORTED_SQL_NUMBER), orphan.errorNumbers())
            assertEquals(0, client.batch("SELECT 1").doneStatus and DONE_ERROR)
        }
        WireClient(server.port).use { client ->
            client.login()
            val unknown = client.batch(insert(UNKNOWN_CODE_TABLE))
            assertEquals(listOf(INTERNAL_ERROR_NUMBER), unknown.errorNumbers())
            assertTrue(client.closedByServer())
        }
        assertEquals(emptyList(), backend.loaded.map { it.target })
    }

    /**
     * Ein Backend ohne die Fähigkeit (Regression, `ADR-0052` Entscheidung 4): `INSERT BULK` ist ein Text für
     * `execute`, die Probe ebenso, und eine Nachricht 0x07 ist ein Fehler `TDS_UNSUPPORTED_SQL` (50001) bei offener
     * Verbindung. Wächter: eine Auswahl der Fähigkeit nach dem Namen der Methode statt nach der Schnittstelle, ein
     * Paket 0x07, das das Backend erreicht.
     *
     * @spec SPEC-036
     * @spec SPEC-059
     */
    @Test
    fun einBackendOhneDieFaehigkeitBekommtInsertBulkAlsTextUndLehntDieNachrichtAb() {
        val backend = PlainBackend()
        val server = start(backend)
        val text = insert("kunden")
        WireClient(server.port).use { client ->
            client.login()
            assertEquals(0, client.batch(text).doneStatus and DONE_ERROR)
            val refused = client.bulkLoad(bytes(MassenladenEmbeddingTest.THREE_ROWS))
            assertEquals(listOf(UNSUPPORTED_SQL_NUMBER), refused.errorNumbers())
            assertEquals(0, client.batch("SELECT 1").doneStatus and DONE_ERROR)
        }
        assertEquals(listOf(text, "SELECT 1"), backend.executed.toList())
    }

    /**
     * Eine gültige Nachricht 0x07 über `RequestLimits.maxMessageBytes` (der Kleinstwert 65535 statt der 16 MiB der
     * Voreinstellung, `SPEC-007`) schließt die Verbindung, `load` wird nicht gerufen. Belegt ist die Grenze am
     * kleinsten einstellbaren Wert; die Nachricht über 16 MiB der Voreinstellung ist nicht gemessen (der Heap des
     * Testprozesses wäre die Bedingung). Wächter: eine Nachricht, die ohne Grenze angenommen wird; der Test wartet
     * dann auf das Schließen und scheitert am Lesetimeout.
     *
     * @spec SPEC-007
     * @spec SPEC-059
     */
    @Test
    fun eineNachrichtUeberDerGrenzeSchliesstDieVerbindungOhneAufrufVonLoad() {
        val backend = BulkBackend()
        val server = start(backend, RequestSettings(limits = RequestLimits(maxMessageBytes = LIMIT_BYTES)))
        WireClient(server.port).use { client ->
            client.login()
            assertEquals(0, client.batch(insert("kunden")).doneStatus and DONE_ERROR)
            assertTrue(client.bulkLoadUntilClosed(bytes(manyRows())))
        }
        assertEquals(emptyList(), backend.loaded.map { it.target })
    }

    /**
     * Wirft eine der drei Funktionen des Backends (Verstoß des Autors gegen die Zusage), hält dieser Test den
     * gemessenen Ist-Zustand je Funktion fest ([EXPECTED_OBSERVATION]); danach nimmt der Server wieder eine
     * Verbindung an.
     *
     * @spec SPEC-059
     */
    @Test
    fun eineAusnahmeInDenFunktionenDesBackendsIstImIstZustandFestgehalten() {
        val backend = BulkBackend()
        val server = start(backend)
        val seen = listOf(
            observe(server) { it.orClosed(TYPE_SQL_BATCH, allHeaders() + utf16(probe(THROWING_TABLE))) },
            observe(server) { it.orClosed(TYPE_SQL_BATCH, allHeaders() + utf16(insert(THROWING_TABLE))) },
            observe(server) {
                assertEquals(0, it.batch(insert(LOAD_THROWING_TABLE)).doneStatus and DONE_ERROR)
                it.orClosed(TYPE_BULK_LOAD, bytes(MassenladenEmbeddingTest.THREE_ROWS))
            },
        )
        assertEquals(EXPECTED_OBSERVATION, seen)
        WireClient(server.port).use { client ->
            client.login()
            assertEquals(0, client.batch(insert("kunden")).doneStatus and DONE_ERROR)
        }
    }

    private fun observe(server: TdsServer, step: (WireClient) -> Answer?): String =
        WireClient(server.port).use { client ->
            client.login()
            step(client)?.let { "Fehler ${it.errorNumbers()}" } ?: "geschlossen ohne Antwort"
        }
}
