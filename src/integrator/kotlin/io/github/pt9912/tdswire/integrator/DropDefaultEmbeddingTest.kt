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
import io.github.pt9912.tdswire.simulation.SimTable
import io.github.pt9912.tdswire.simulation.SimulationBackend
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Der Text von d-migrate mit `DECLARE @df …` (Form D7, `SPEC-036`) auf den Rohbytes: der Strukturänderungs-Callback
 * bekommt die Tabelle ohne den Standardwert der Spalte; die Antwort ist ein Abschluss der Art `ALTER_TABLE` (Befehl
 * `0x00d8`, ohne Zähler, ohne Fehlerbit); an einer unbekannten Tabelle gelingt der Text ohne Callback.
 */
class DropDefaultEmbeddingTest {
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

    private fun declareText(table: String, column: String): String = listOf(
        "DECLARE @df sysname, @sql nvarchar(max);",
        "SELECT @df = dc.name FROM sys.default_constraints dc",
        "    JOIN sys.columns c ON c.object_id = dc.parent_object_id",
        "        AND c.column_id = dc.parent_column_id",
        "    WHERE dc.parent_object_id = OBJECT_ID('$table')",
        "        AND c.name = '$column';",
        "IF @df IS NOT NULL",
        "BEGIN",
        "    SET @sql = 'ALTER TABLE [$table] DROP CONSTRAINT ' + QUOTENAME(@df);",
        "    EXEC sp_executesql @sql;",
        "END;",
    ).joinToString("\n")

    /**
     * Ein Integrator bekommt nach dem Text die Tabelle ohne den Standardwert der Spalte, die übrigen Standardwerte
     * bleiben; der folgende `ADD CONSTRAINT … DEFAULT … FOR` setzt den neuen Standardwert. Der Text ohne Treffer
     * gelingt mit unveränderter Tabelle, der an einer unbekannten Tabelle ohne Callback; keiner setzt das Fehlerbit.
     *
     * @spec SPEC-059
     * @spec SPEC-036
     */
    @Test
    fun einIntegratorBekommtNachDemTextDieTabelleOhneDenStandardwert() {
        val described = SimTable(
            name = "beschrieben",
            columns = listOf(
                SimColumn("Id", SqlType.Int64, nullable = false),
                SimColumn("Menge", SqlType.Int32, defaultDefinition = "((1))"),
                SimColumn("Status", SqlType.UnicodeText, maxLength = 20, defaultDefinition = "(N'neu')"),
            ),
            primaryKey = listOf("Id"),
            rows = { RowSource { null } },
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
            val dropped = client.batch(declareText("beschrieben", "Menge"))
            assertEquals(0, dropped.doneStatus and DONE_ERROR, "Treffer: ohne Fehlerbit")
            assertEquals(ALTER_TABLE_COMMAND, dropped.command, "Treffer: Befehl 0x00d8")
            assertEquals(0L, dropped.rowCount, "Treffer: ohne Zeilenzahl")
            val none = client.batch(declareText("beschrieben", "Menge"))
            assertEquals(0, none.doneStatus and DONE_ERROR, "ohne Standardwert: ohne Fehlerbit")
            assertEquals(ALTER_TABLE_COMMAND, none.command)
            val absent = client.batch(declareText("gibtesnicht", "Menge"))
            assertEquals(0, absent.doneStatus and DONE_ERROR, "unbekannte Tabelle: Erfolg")
            assertEquals(ALTER_TABLE_COMMAND, absent.command)
            val added = client.batch("ALTER TABLE beschrieben ADD CONSTRAINT df_menge DEFAULT 2 FOR Menge")
            assertEquals(0, added.doneStatus and DONE_ERROR, "ADD CONSTRAINT nach dem Text: Erfolg")
            val twice = client.batch("ALTER TABLE beschrieben ADD CONSTRAINT df_menge2 DEFAULT 3 FOR Menge")
            assertEquals(DONE_ERROR, twice.doneStatus and DONE_ERROR, "zweiter Standardwert: Fehlerbit")
            assertEquals(listOf(COLUMN_HAS_DEFAULT, NOT_CREATED), twice.errorNumbers(), "gemessen: 1781, dann 1750")
        }
        assertEquals(STRUCTURE_CALLS, shapes.size)
        assertEquals(listOf(null, "(N'neu')"), shapes.first().columns.drop(1).map { it.defaultDefinition })
        assertEquals(shapes.first().columns, shapes[1].columns)
        assertEquals(listOf("((2))", "(N'neu')"), shapes.last().columns.drop(1).map { it.defaultDefinition })
    }

    private companion object {
        const val ALTER_TABLE_COMMAND = 0x00d8
        const val STRUCTURE_CALLS = 3
        const val COLUMN_HAS_DEFAULT = 1781
        const val NOT_CREATED = 1750
    }
}
