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
import io.github.pt9912.tdswire.simulation.SimCheck
import io.github.pt9912.tdswire.simulation.SimColumn
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
 * `ALTER TABLE … DROP CONSTRAINT [IF EXISTS]` (Form D5, `SPEC-036`) auf den Rohbytes: der Strukturänderungs-Callback
 * bekommt die Tabelle ohne die Bedingung; `IF EXISTS` ohne Treffer gelingt mit unveränderter Tabelle, ohne `IF EXISTS`
 * trägt die Antwort das Fehlerbit und der Callback bleibt ungerufen (gemessen am echten Server, Szenario
 * `struktur-drop-constraint`).
 */
class DropConstraintEmbeddingTest {
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
     * Ein Integrator bekommt nach `DROP CONSTRAINT` die Tabelle ohne die Bedingung; die Bedingung einer anderen Art
     * bleibt. `IF EXISTS` ohne Treffer ruft den Callback mit der unveränderten Tabelle, ein Name ohne Treffer ohne
     * `IF EXISTS` setzt das Fehlerbit im Abschluss, und der Callback läuft nicht.
     *
     * @spec SPEC-059
     * @spec SPEC-036
     */
    @Test
    fun einIntegratorBekommtNachDropConstraintDieTabelleOhneDieBedingung() {
        val described = SimTable(
            name = "beschrieben",
            columns = listOf(SimColumn("Id", SqlType.Int64, nullable = false), SimColumn("Menge", SqlType.Int32)),
            primaryKey = listOf("Id"),
            rows = { RowSource { null } },
            uniqueKeys = listOf(SimUniqueKey("uq_beschrieben", listOf("Menge"))),
            checks = listOf(SimCheck("ck_beschrieben", "([Menge]>(0))")),
        )
        val other = SimTable(
            name = "andere",
            columns = listOf(SimColumn("Id", SqlType.Int64, nullable = false), SimColumn("Menge", SqlType.Int32)),
            primaryKey = listOf("Id"),
            rows = { RowSource { null } },
            checks = listOf(SimCheck("ck_andere", "([Menge]>(1))")),
        )
        val store = CopyOnWriteArrayList(listOf(described, other))
        val shapes = CopyOnWriteArrayList<SimTable>()
        val backend = SimulationBackend({ SimCatalog(store.toList()) }) { shape ->
            shapes += shape
            store.removeAll { it.name == shape.name }
            store += shape
        }
        val server = start(BackendSource.Custom(backend))
        WireClient(server.port).use { client ->
            client.login()
            val dropped = client.batch("ALTER TABLE beschrieben DROP CONSTRAINT ck_beschrieben")
            assertEquals(0, dropped.doneStatus and DONE_ERROR)
            assertEquals(ALTER_TABLE_COMMAND, dropped.command, "Erfolg: Befehl 0x00d8")
            assertEquals(0L, dropped.rowCount, "Erfolg: ohne COUNT")
            val none = client.batch("ALTER TABLE beschrieben DROP CONSTRAINT IF EXISTS ck_beschrieben")
            assertEquals(0, none.doneStatus and DONE_ERROR)
            assertEquals(ALTER_TABLE_COMMAND, none.command, "IF EXISTS ohne Treffer: Befehl 0x00d8")
            val missing = client.batch("ALTER TABLE beschrieben DROP CONSTRAINT ck_beschrieben")
            assertEquals(DONE_ERROR, missing.doneStatus and DONE_ERROR)
            assertEquals(NOT_A_CONSTRAINT, errorNumber(missing), "Name ohne Treffer: 3728")
            assertEquals(listOf(NOT_A_CONSTRAINT, NOT_DROPPED), missing.errorNumbers(), "danach das zweite ERROR 3727")
            val other = client.batch("ALTER TABLE beschrieben DROP CONSTRAINT ck_andere")
            assertEquals(DONE_ERROR, other.doneStatus and DONE_ERROR)
            assertEquals(OTHER_TABLE, errorNumber(other), "Name einer anderen Tabelle: 3733")
            assertEquals(listOf(OTHER_TABLE, NOT_DROPPED), other.errorNumbers(), "danach das zweite ERROR 3727")
            val unknown = client.batch("ALTER TABLE gibtesnicht DROP CONSTRAINT IF EXISTS x")
            assertEquals(DONE_ERROR, unknown.doneStatus and DONE_ERROR)
            assertEquals(OBJECT_NOT_FOUND, errorNumber(unknown), "unbekannte Tabelle: 4902")
            assertEquals(listOf(OBJECT_NOT_FOUND), unknown.errorNumbers(), "gemessen: ohne zweites ERROR")
        }
        assertEquals(2, shapes.size)
        assertEquals(emptyList(), shapes.first().checks)
        assertEquals(described.uniqueKeys, shapes.first().uniqueKeys)
        assertEquals(shapes.first().checks, shapes.last().checks)
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
        const val ALTER_TABLE_COMMAND = 0x00d8
        const val NOT_A_CONSTRAINT = 3728
        const val OTHER_TABLE = 3733
        const val NOT_DROPPED = 3727
        const val OBJECT_NOT_FOUND = 4902
        const val ERROR_NUMBER_OFFSET = 3
        const val BYTE_BITS = 8
    }
}
