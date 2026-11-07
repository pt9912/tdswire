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
 * `MERGE … OUTPUT $action` über die öffentliche Fläche (`SPEC-036` Form M, `SPEC-035`, `SPEC-059`): ein Integrator
 * übergibt eine Tabelle mit `onInsert` und `onUpdate` und bekommt die Aufrufe, die ein Client mit dem
 * `sp_executesql` von
 * d-migrate 1.12.0 auslöst (Wortlaut und Deklaration gemessen am 2026-11-07, Parameter `bigint`, `nvarchar(4000)`,
 * `date`). Die Antwort ist am Draht gelesen: das Ergebnis `$action` und die Zeile `INSERT` oder `UPDATE` als UTF-16 in
 * den Nutzdaten. Das Source-Set sieht nur die öffentliche Fläche; der Test belegt auch, dass kein Name der Form M
 * öffentlich ist (er braucht keinen).
 */
class MergeEmbeddingTest {
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

    private val declarations = "@P0 bigint,@P1 nvarchar(4000),@P2 date"

    private val matched =
        "WHEN MATCHED THEN UPDATE SET tgt.[Name] = src.[Name], tgt.[Eintritt] = src.[Eintritt] "

    private fun merge(withUpdate: Boolean): String {
        val branch = if (withUpdate) matched else ""
        return "MERGE INTO [dbo].[kunden] AS tgt USING (VALUES ( @P0 ,  @P1 ,  @P2 )) AS src " +
            "([Id], [Name], [Eintritt]) ON tgt.[Id] = src.[Id] " + branch +
            "WHEN NOT MATCHED THEN INSERT ([Id], [Name], [Eintritt]) VALUES (src.[Id], src.[Name], src.[Eintritt]) " +
            "OUTPUT \$action;"
    }

    private fun days(date: LocalDate): Int = (date.toEpochDay() - LocalDate.of(1, 1, 1).toEpochDay()).toInt()

    private fun has(payload: ByteArray, text: String): Boolean {
        val needle = utf16(text)
        return (0..payload.size - needle.size).any { at -> needle.indices.all { payload[at + it] == needle[it] } }
    }

    private fun table(
        existing: Set<Long>,
        inserted: MutableList<Map<String, Any?>>,
        updated: MutableList<Pair<Map<String, Any?>, Any?>>,
    ) = SimTable(
        name = "kunden",
        columns = columns,
        primaryKey = listOf("Id"),
        rows = {
            val rows = existing.map { id -> listOf<Any?>(id, "vorhanden", null) }.iterator()
            RowSource { if (rows.hasNext()) rows.next() else null }
        },
        onInsert = { values ->
            inserted += values
            1L
        },
        onUpdate = { set, where ->
            updated += set to where
            1L
        },
    )

    /**
     * Ein `MERGE` von d-migrate (`--on-conflict update`) über `sp_executesql`: der fehlende Schlüssel 5 ruft
     * `onInsert` mit den
     * überführten Werten aus `bigint`, `nvarchar` und `date` und antwortet mit dem Ergebnis `$action` und der Zeile
     * `INSERT`; der vorhandene Schlüssel 1 ruft `onUpdate` mit `set` ohne Schlüssel und antwortet `UPDATE`; derselbe
     * Text
     * ohne Zweig `WHEN MATCHED` ruft bei vorhandenem Schlüssel nichts und antwortet ohne Zeile. Wächter: ein
     * `MERGE`, das als nicht erkannt endet; Parameter, die nicht ankommen; eine Antwort ohne `$action`.
     *
     * @spec SPEC-036
     * @spec SPEC-035
     * @spec SPEC-059
     */
    @Test
    fun einMergeFuegtEinAendertOderLaesstUnveraendertUndAntwortetMitAction() {
        val inserted = CopyOnWriteArrayList<Map<String, Any?>>()
        val updated = CopyOnWriteArrayList<Pair<Map<String, Any?>, Any?>>()
        val server = start(BackendSource.Simulation(listOf(table(setOf(1L), inserted, updated))))
        val joined = LocalDate.of(2026, 1, 2)
        WireClient(server.port).use { client ->
            client.login()
            val insert = client.executeSql(
                merge(withUpdate = true),
                declarations,
                inputBigint("", 5),
                inputText("", "Ada Ünï"),
                inputDate("", days(joined)),
            )
            assertTrue(has(insert, "\$action") && has(insert, "INSERT") && !has(insert, "UPDATE"))
            val update = client.executeSql(
                merge(withUpdate = true),
                declarations,
                inputBigint("", 1),
                inputText("", null),
                inputDate("", null),
            )
            assertTrue(has(update, "\$action") && has(update, "UPDATE") && !has(update, "INSERT"))
            val skipped = client.executeSql(
                merge(withUpdate = false),
                declarations,
                inputBigint("", 1),
                inputText("", "x"),
                inputDate("", days(joined)),
            )
            assertTrue(has(skipped, "\$action") && !has(skipped, "UPDATE") && !has(skipped, "INSERT"))
        }
        val expected = mapOf<String, Any?>("Id" to FIVE, "Name" to "Ada Ünï", "Eintritt" to joined)
        assertEquals(listOf(expected), inserted.toList())
        assertEquals(1, updated.size)
        assertEquals(mapOf<String, Any?>("Name" to null, "Eintritt" to null), updated.single().first)
    }

    private companion object {
        const val FIVE = 5L
    }
}
