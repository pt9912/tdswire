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
import io.github.pt9912.tdswire.simulation.SimCatalog
import io.github.pt9912.tdswire.simulation.SimColumn
import io.github.pt9912.tdswire.simulation.SimIndex
import io.github.pt9912.tdswire.simulation.SimTable
import io.github.pt9912.tdswire.simulation.SimUniqueKey
import io.github.pt9912.tdswire.simulation.SimulationBackend
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * `DROP INDEX [IF EXISTS] <Name> ON <Tabelle>` (Form D6, `SPEC-036`) auf den Rohbytes: der Strukturänderungs-Callback
 * bekommt die Tabelle ohne den Index; `IF EXISTS` ohne Treffer gelingt mit unveränderter Tabelle, `IF EXISTS` an einer
 * unbekannten Tabelle gelingt ohne Callback, ohne `IF EXISTS` trägt die Antwort das Fehlerbit mit der Nummer 3701 und
 * der Callback bleibt ungerufen, der Index einer Eindeutigkeit ist 3723 (gemessen am echten Server, Szenario
 * `struktur-drop-index`).
 */
class DropIndexEmbeddingTest {
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
     * Ein Integrator bekommt nach `DROP INDEX` die Tabelle ohne den Index; die übrigen Indizes und die Eindeutigkeit
     * bleiben. `IF EXISTS` ohne Treffer ruft den Callback mit der unveränderten Tabelle, an einer unbekannten Tabelle
     * läuft der Callback nicht und die Antwort trägt kein Fehlerbit; ein Name ohne Treffer ohne `IF EXISTS` setzt das
     * Fehlerbit im Abschluss, mit der Nummer 3701, der Index einer Eindeutigkeit mit 3723, und der Callback läuft
     * nicht.
     *
     * @spec SPEC-059
     * @spec SPEC-036
     */
    @Test
    fun einIntegratorBekommtNachDropIndexDieTabelleOhneDenIndex() {
        val described = SimTable(
            name = "beschrieben",
            columns = listOf(SimColumn("Id", SqlType.Int64, nullable = false), SimColumn("Menge", SqlType.Int32)),
            primaryKey = listOf("Id"),
            rows = { RowSource { null } },
            uniqueKeys = listOf(SimUniqueKey("uq_beschrieben", listOf("Menge"))),
            indexes = listOf(SimIndex("ix_menge", listOf("Menge")), SimIndex("ix_zweiter", listOf("Id", "Menge"))),
        )
        val store = CopyOnWriteArrayList(listOf(described))
        val shapes = CopyOnWriteArrayList<SimTable>()
        val backend = SimulationBackend({ SimCatalog(store.toList()) }) { shape ->
            shapes += shape
            store.removeAll { it.name == shape.name }
            store += shape
        }
        val server = start(BackendSource.Custom(backend))
        WireClient(server.port).use { client ->
            client.login()
            val dropped = client.batch("DROP INDEX ix_menge ON beschrieben")
            assertEquals(0, dropped.doneStatus and DONE_ERROR, "Erfolg: ohne Fehlerbit")
            assertEquals(DROP_INDEX_COMMAND, dropped.command, "Erfolg: Befehl 0x00c9")
            assertEquals(0L, dropped.rowCount, "Erfolg: ohne Zeilenzahl")
            val none = client.batch("DROP INDEX IF EXISTS ix_menge ON beschrieben")
            assertEquals(0, none.doneStatus and DONE_ERROR, "IF EXISTS ohne Treffer: ohne Fehlerbit")
            assertEquals(DROP_INDEX_COMMAND, none.command, "IF EXISTS ohne Treffer: Befehl 0x00c9")
            val missing = client.batch("DROP INDEX ix_menge ON beschrieben")
            assertEquals(DONE_ERROR, missing.doneStatus and DONE_ERROR)
            assertEquals(NOT_AN_INDEX, errorNumber(missing), "Name ohne Treffer: 3701")
            val unknown = client.batch("DROP INDEX x ON gibtesnicht")
            assertEquals(DONE_ERROR, unknown.doneStatus and DONE_ERROR)
            assertEquals(NOT_AN_INDEX, errorNumber(unknown), "unbekannte Tabelle ohne IF EXISTS: 3701")
            val constraint = client.batch("DROP INDEX IF EXISTS uq_beschrieben ON beschrieben")
            assertEquals(DONE_ERROR, constraint.doneStatus and DONE_ERROR)
            assertEquals(INDEX_OF_CONSTRAINT, errorNumber(constraint), "Index der Eindeutigkeit: 3723")
            val absent = client.batch("DROP INDEX IF EXISTS x ON gibtesnicht")
            assertEquals(0, absent.doneStatus and DONE_ERROR, "IF EXISTS an unbekannter Tabelle: Erfolg")
            assertEquals(DROP_INDEX_COMMAND, absent.command, "IF EXISTS an unbekannter Tabelle: Befehl 0x00c9")
        }
        assertEquals(2, shapes.size)
        assertEquals(listOf(SimIndex("ix_zweiter", listOf("Id", "Menge"))), shapes.first().indexes)
        assertEquals(described.uniqueKeys, shapes.first().uniqueKeys)
        assertEquals(shapes.first().indexes, shapes.last().indexes)
        assertEquals(shapes.first().uniqueKeys, shapes.last().uniqueKeys)
    }

    /** Die Nummer des ersten ERROR-Tokens der Antwort (Token `0xAA`, Länge 2 Bytes, danach die Nummer als Int32). */
    private fun errorNumber(answer: Answer): Int {
        assertEquals(ERROR_TOKEN, answer.payload.first().toInt() and BYTE_MASK, "die Antwort beginnt mit ERROR")
        return (0 until INT_BYTES).fold(0) { sum, index ->
            sum or ((answer.payload[ERROR_NUMBER_OFFSET + index].toInt() and BYTE_MASK) shl (BYTE_BITS * index))
        }
    }

    private companion object {
        const val DROP_INDEX_COMMAND = 0x00c9
        const val NOT_AN_INDEX = 3701
        const val INDEX_OF_CONSTRAINT = 3723
        const val ERROR_NUMBER_OFFSET = 3
        const val BYTE_BITS = 8
    }
}
