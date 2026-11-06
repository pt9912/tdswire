package io.github.pt9912.tdswire.integrator

import io.github.pt9912.tdswire.config.AuthSettings
import io.github.pt9912.tdswire.config.EncryptionSetting
import io.github.pt9912.tdswire.config.ListenSettings
import io.github.pt9912.tdswire.config.ServerConfig
import io.github.pt9912.tdswire.config.auth.UserList
import io.github.pt9912.tdswire.config.auth.UserListError
import io.github.pt9912.tdswire.config.auth.UserListErrorKind
import io.github.pt9912.tdswire.config.auth.UserListResult
import io.github.pt9912.tdswire.domain.ParameterDirection
import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryParameter
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.port.QueryExecution
import io.github.pt9912.tdswire.runtime.BackendSource
import io.github.pt9912.tdswire.runtime.StartOutcome
import io.github.pt9912.tdswire.runtime.TdsServer
import io.github.pt9912.tdswire.simulation.FilterTerm
import io.github.pt9912.tdswire.simulation.RowFilter
import io.github.pt9912.tdswire.simulation.RowSource
import io.github.pt9912.tdswire.simulation.SimCatalog
import io.github.pt9912.tdswire.simulation.SimColumn
import io.github.pt9912.tdswire.simulation.SimQuery
import io.github.pt9912.tdswire.simulation.SimTable
import io.github.pt9912.tdswire.simulation.SimulationBackend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Namen der öffentlichen Fläche, die weder der Testserver noch ein anderer Test dieses Source-Sets benutzt
 * (`SPEC-059`): die Parameter und Antworten eines Backends bei `sp_executesql`, die Bedingung, die eine Tabelle des
 * Integrators an `rows` und `onUpdate` bekommt, der zweite Konstruktor von `SimulationBackend` mit Katalog-Anbieter und
 * Strukturänderungs-Callback samt `SimColumn.maxLength` und `SimTable.onInsertBatch`, und der Fehler einer ungültigen
 * Benutzerliste. Das Source-Set sieht
 * nur die öffentliche Fläche; belegt ist, dass diese Namen von außen übersetzbar sind und die Werte ankommen, nicht
 * die Vollständigkeit ihrer Felder.
 */
class FlaecheEmbeddingTest {
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

    /**
     * Ein Backend des Integrators liest die Parameter einer Anfrage mit Name, Typ, Wert und Richtung
     * (`QueryParameter`, `ParameterDirection`) und antwortet mit Änderungszähler, Rückgabestatus und
     * Ausgabeparameter (`UpdateCount`, `ReturnStatus`, `OutputParameter`); der Rückgabestatus und der Ausgabeparameter
     * stehen in der Antwort auf den Rohbytes.
     *
     * @spec SPEC-059
     * @spec SPEC-030
     */
    @Test
    fun einBackendLiestParameterUndMeldetZaehlerStatusUndAusgabe() {
        val backend = ParameterBackend()
        val server = start(BackendSource.Custom(backend))
        val answer = WireClient(server.port).use { client ->
            client.login()
            client.executeSql("select @a, @o", "@a int, @o int output", inputInt("@a", INPUT_VALUE), outputInt("@o"))
        }
        assertEquals(
            listOf(
                QueryParameter("@a", SqlType.Int32, INPUT_VALUE, ParameterDirection.INPUT),
                QueryParameter("@o", SqlType.Int32, null, ParameterDirection.OUTPUT),
            ),
            backend.seen.single(),
        )
        assertTrue(answer.contains(byteArrayOf(RETURN_STATUS_TOKEN, STATUS_VALUE, 0, 0, 0)), "Rückgabestatus fehlt")
        assertTrue(answer.contains("@o".toByteArray(Charsets.UTF_16LE)), "Ausgabeparameter fehlt")
        assertTrue(answer.contains(byteArrayOf(OUTPUT_VALUE, 0, 0, 0)), "Wert des Ausgabeparameters fehlt")
    }

    /**
     * Die Tabelle des Integrators bekommt die Bedingung einer `UPDATE`-Anweisung als `RowFilter` aus `FilterTerm.In`
     * und `FilterTerm.IsNull` im Callback `onUpdate`, und die Zeilenquelle einer Auswahl bekommt eine `SimQuery` mit
     * der Bedingung als Vorfilter und den Spalten (hier die der Auswahl und der Bedingung: `Id`, `Name`).
     *
     * @spec SPEC-059
     * @spec SPEC-035
     */
    @Test
    fun eineTabelleDesIntegratorsLiestDieBedingungAlsFilter() {
        val seen = FilterRecorder()
        val server = start(BackendSource.Simulation(listOf(seen.table())))
        WireClient(server.port).use { client ->
            client.login()
            client.batch("SELECT Name FROM person WHERE Id = 2")
            client.batch("UPDATE person SET Name = 'Zed' WHERE Id IN (1, 2) AND Name IS NOT NULL")
        }
        val terms = listOf(FilterTerm.In("Id", listOf(1, 2)), FilterTerm.IsNull("Name", negated = true))
        assertEquals(RowFilter(listOf(FilterTerm.In("Id", listOf(2)))), seen.queries.single().filter)
        assertEquals(listOf("Id", "Name"), seen.queries.single().columns.map { it.name })
        assertEquals(RowFilter(terms), seen.updates.single())
    }

    /**
     * Ein Integrator hält Tabellen über den zweiten Konstruktor von `SimulationBackend`: der Anbieter liefert bei jeder
     * Anfrage den aktuellen Stand, der Strukturänderungs-Callback legt die Tabelle an (`CREATE TABLE` als SQL-Text auf
     * den Rohbytes), die nächste Anfrage sieht sie, und ein zweites `CREATE TABLE` derselben Tabelle ruft den Callback
     * nicht. Der Callback bekommt die Spalten mit `SimColumn.maxLength`; die
     * Tabelle trägt `SimTable.onInsertBatch` (die Bibliothek ruft ihn in diesem Stand nicht).
     *
     * @spec SPEC-059
     * @spec SPEC-035
     * @spec SPEC-036
     */
    @Test
    fun einIntegratorLegtTabellenUeberAnbieterUndStrukturCallbackAn() {
        val store = CopyOnWriteArrayList<SimTable>()
        val calls = CopyOnWriteArrayList<List<String>>()
        val backend = SimulationBackend({ SimCatalog(store.toList()) }) { schema, name, columns, key ->
            calls += listOf(schema, name) + columns.map { "${it.name}:${it.type}:${it.maxLength}" } + key
            store += SimTable(schema, name, columns, key, rows = { RowSource { null } }, onInsertBatch = { 0L })
        }
        val server = start(BackendSource.Custom(backend))
        WireClient(server.port).use { client ->
            client.login()
            val ddl = "CREATE TABLE geraete (Seriennummer BIGINT NOT NULL, Name NVARCHAR(40), " +
                "PRIMARY KEY (Seriennummer))"
            assertEquals(0, client.batch(ddl).doneStatus and DONE_ERROR)
            assertTrue(client.batch(ddl).doneStatus and DONE_ERROR != 0, "die Tabelle gibt es schon")
            val seen = client.batch("SELECT Seriennummer FROM geraete WHERE 1 = 0")
            assertEquals(0, seen.doneStatus and DONE_ERROR)
            assertTrue(seen.contains("Seriennummer"), "die Spalte steht im Ergebnis")
            assertTrue(client.batch("SELECT SCHEMA_NAME() AS schema_name").contains("dbo"), "Katalogabfrage")
        }
        val columns = listOf("Seriennummer:Int64:4000", "Name:UnicodeText:40")
        assertEquals(listOf(listOf("dbo", "geraete") + columns + "Seriennummer"), calls)
    }

    /**
     * Eine ungültige Benutzerliste ergibt `UserListResult.Invalid` mit einem `UserListError`: Art
     * (`UserListErrorKind`) und Zeilennummer, ohne Benutzernamen oder Prüfwert.
     *
     * @spec SPEC-059
     * @spec SPEC-051
     */
    @Test
    fun eineUngueltigeBenutzerlisteNenntArtUndZeile() {
        val result = UserList.parse("alice\t{plain}secret\nalice\t{plain}other\n", true)
        assertEquals(
            UserListResult.Invalid(UserListError(UserListErrorKind.DUPLICATE_USER_NAME, 2)),
            result,
        )
    }

    private fun ByteArray.contains(needle: ByteArray): Boolean =
        (0..size - needle.size).any { start -> needle.indices.all { this[start + it] == needle[it] } }
}

private const val INPUT_VALUE = 42
private const val OUTPUT_VALUE: Byte = 99
private const val STATUS_VALUE: Byte = 7
private const val RETURN_STATUS_TOKEN: Byte = 0x79
private const val UPDATED_ROWS = 3L

/** Ein Backend, das die Parameter jeder Anfrage merkt und mit Zähler, Status und Ausgabeparameter antwortet. */
private class ParameterBackend : QueryBackend {
    val seen = CopyOnWriteArrayList<List<QueryParameter>>()

    override suspend fun execute(request: QueryRequest): QueryExecution {
        seen += request.parameters
        val events: List<QueryEvent> = listOf(
            QueryEvent.UpdateCount(UPDATED_ROWS),
            QueryEvent.ReturnStatus(STATUS_VALUE.toInt()),
            QueryEvent.OutputParameter("@o", SqlType.Int32, OUTPUT_VALUE.toInt()),
        )
        return object : QueryExecution {
            override val events: Flow<QueryEvent> = events.asFlow()

            override suspend fun close() = Unit
        }
    }

    override suspend fun cancel(sessionId: String, requestId: String) = Unit
}

/** Eine Tabelle `person`, die merkt, was ihre Zeilenquelle und ihr Callback `onUpdate` bekommen. */
private class FilterRecorder {
    val queries = CopyOnWriteArrayList<SimQuery>()
    val updates = CopyOnWriteArrayList<RowFilter?>()

    fun table(): SimTable = SimTable(
        name = "person",
        columns = listOf(SimColumn("Id", SqlType.Int32, nullable = false), SimColumn("Name", SqlType.UnicodeText)),
        primaryKey = listOf("Id"),
        rows = { query ->
            queries += query
            object : RowSource {
                override suspend fun next(): List<Any?>? = null

                override fun close() = Unit
            }
        },
        onUpdate = { _, where ->
            updates += where
            1L
        },
    )
}
