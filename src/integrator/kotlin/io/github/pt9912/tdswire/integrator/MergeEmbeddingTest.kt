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
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
            ListenSettings(host = LOOPBACK, port = 0),
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
     * ohne Zweig `WHEN MATCHED` ruft bei vorhandenem Schlüssel nichts und antwortet ohne Zeile. Die Spalte `$action`
     * ist `nvarchar(10)` mit Flags 0 und der Abschluss trägt die Kennung `0x0117` mit dem Zähler (Messprotokoll M-26).
     * Wächter: ein `MERGE`, das als nicht erkannt endet; Parameter, die nicht ankommen; eine Antwort ohne `$action`;
     * die Länge 4000 der Spalte; die Kennung `0x00c1` des Abschlusses.
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
            assertTrue(insert.containsUtf16("\$action") && insert.containsUtf16("INSERT"))
            assertFalse(insert.containsUtf16("UPDATE"))
            assertTrue(insert.containsBytes(ACTION_COLUMN), "nvarchar(10), Flags 0")
            assertTrue(insert.containsBytes("FF 11 00 17 01 01 00 00 00 00 00 00 00"), "MORE|COUNT, 0x0117, Zähler 1")
            val update = client.executeSql(
                merge(withUpdate = true),
                declarations,
                inputBigint("", 1),
                inputText("", null),
                inputDate("", null),
            )
            assertTrue(update.containsUtf16("\$action") && update.containsUtf16("UPDATE"))
            assertFalse(update.containsUtf16("INSERT"))
            val skipped = client.executeSql(
                merge(withUpdate = false),
                declarations,
                inputBigint("", 1),
                inputText("", "x"),
                inputDate("", days(joined)),
            )
            assertTrue(skipped.containsUtf16("\$action"))
            assertFalse(skipped.containsUtf16("UPDATE") || skipped.containsUtf16("INSERT"))
            assertTrue(skipped.containsBytes("FF 11 00 17 01 00 00 00 00 00 00 00 00"), "0x0117, Zähler 0")
        }
        val expected = mapOf<String, Any?>("Id" to FIVE, "Name" to "Ada Ünï", "Eintritt" to joined)
        assertEquals(listOf(expected), inserted.toList())
        assertEquals(1, updated.size)
        assertEquals(mapOf<String, Any?>("Name" to null, "Eintritt" to null), updated.single().first)
    }

    private fun mergeInto(table: String, columns: List<String>): String {
        val names = columns.joinToString(", ") { "[$it]" }
        val values = columns.indices.joinToString(" ,  ") { "@P$it" }
        val sources = columns.joinToString(", ") { "src.[$it]" }
        return "MERGE INTO [dbo].[$table] AS tgt USING (VALUES ( $values )) AS src ($names) ON tgt.[Id] = src.[Id] " +
            "WHEN NOT MATCHED THEN INSERT ($names) VALUES ($sources) OUTPUT \$action;"
    }

    private fun emptyTable(name: String, columns: List<SimColumn>, inserted: MutableList<Map<String, Any?>>) = SimTable(
        name = name,
        columns = columns,
        primaryKey = listOf("Id"),
        rows = { RowSource { null } },
        onInsert = { values ->
            inserted += values
            1L
        },
    )

    /**
     * Die Parameter von d-migrate für `DECIMAL` über den Draht, mit den Bytes der Aufnahme am echten SQL Server
     * (Messprotokoll der Referenzabgleiche M-31): `DECIMALN(38,2)` mit 3 und 2 Byte Wert und NULL (Länge 0). Die erste
     * Zeile geht als `sp_executesql`, die Folgezeilen wie bei d-migrate als `sp_prepexec` (Handle 1) und `sp_execute`.
     * Wächter: ein Parser, der `DECIMALN` nur mit 17 Byte liest (Antwort `Protocol error.`).
     *
     * @spec SPEC-030
     * @spec SPEC-036
     */
    @Test
    fun dezimalParameterVonDMigrateKommenInAllenDreiFormenAnDenCallback() {
        val orders = CopyOnWriteArrayList<Map<String, Any?>>()
        val columns = listOf(
            SimColumn("Id", SqlType.Int64, nullable = false),
            SimColumn("KundenId", SqlType.Int64, nullable = false),
            SimColumn("Betrag", SqlType.Decimal(10, 2)),
        )
        val server = start(BackendSource.Simulation(listOf(emptyTable("bestellungen", columns, orders))))
        val sql = mergeInto("bestellungen", listOf("Id", "KundenId", "Betrag"))
        val declaration = "@P0 bigint,@P1 bigint,@P2 decimal(38,2)"
        WireClient(server.port).use { client ->
            client.login()
            val first = client.executeSql(
                sql,
                declaration,
                inputBigint("", 1),
                inputBigint("", SEVEN),
                inputRaw("6A 11 26 02 03 01 E2 04"),
            )
            assertTrue(first.containsUtf16("INSERT"), "sp_executesql")
            val second = client.rpc(
                prepExecPayload(
                    sql,
                    declaration,
                    inputBigint("", 2),
                    inputBigint("", SEVEN),
                    inputRaw("6A 11 26 02 00"),
                ),
            )
            assertTrue(second.containsUtf16("INSERT"), "sp_prepexec")
            val third = client.rpc(
                executeHandlePayload(1, inputBigint("", 3), inputBigint("", EIGHT), inputRaw("6A 11 26 02 02 00 05")),
            )
            assertTrue(third.containsUtf16("INSERT"), "sp_execute")
        }
        assertEquals(listOf<Any?>(BigDecimal("12.50"), null, BigDecimal("-0.05")), orders.map { it["Betrag"] })
        assertEquals(listOf(1L, 2L, THREE), orders.map { it["Id"] })
    }

    /**
     * Die Parameter von d-migrate für `TIME`, `DATETIME2`, `BIT` und `DATE` über den Draht, mit den Bytes der Aufnahme
     * am echten SQL Server (M-31): die Zeit einer `time`-Spalte kommt als `datetime` (1970-01-01 13:14:15), dazu
     * `datetime2` (Scale 7), `bit` und `date`. Wächter: ein Parser, der `DATETIMN` nicht liest (Antwort
     * `Unsupported RPC.`) oder eine Überführung, die den `DateTime`-Parameter in der `Time`-Spalte ablehnt.
     *
     * @spec SPEC-030
     * @spec SPEC-036
     */
    @Test
    fun zeitParameterVonDMigrateKommenAnDenCallback() {
        val times = CopyOnWriteArrayList<Map<String, Any?>>()
        val columns = listOf(
            SimColumn("Id", SqlType.Int64, nullable = false),
            SimColumn("Uhr", SqlType.Time),
            SimColumn("Zeitpunkt", SqlType.DateTime),
            SimColumn("Flag", SqlType.Bool),
            SimColumn("Datum", SqlType.Date),
        )
        val server = start(BackendSource.Simulation(listOf(emptyTable("zeiten", columns, times))))
        val sql = mergeInto("zeiten", listOf("Id", "Uhr", "Zeitpunkt", "Flag", "Datum"))
        WireClient(server.port).use { client ->
            client.login()
            val answer = client.executeSql(
                sql,
                "@P0 bigint,@P1 datetime,@P2 datetime2,@P3 bit,@P4 date",
                inputBigint("", 1),
                inputRaw("6F 08 08 DF 63 00 00 B4 25 DA 00"),
                inputRaw("2A 07 08 80 ED 97 F4 6E 21 49 0B"),
                inputRaw("68 01 01 01"),
                inputRaw("28 03 21 49 0B"),
            )
            assertTrue(answer.containsUtf16("INSERT"), "datetime, datetime2, bit, date")
        }
        val row = times.single()
        assertEquals(CLOCK, row["Uhr"])
        assertEquals(DAY.atTime(CLOCK), row["Zeitpunkt"])
        assertEquals(true, row["Flag"])
        assertEquals(DAY, row["Datum"])
    }

    /**
     * Ein Fehler von `onInsert` steht hinter dem Ergebnis `$action` (COLMETADATA, dann ERROR 2627, INFO 3621,
     * DONEINPROC
     * `MORE|ERROR` mit der Kennung `0x0117`, RETURNSTATUS 2627, DONEPROC; Messprotokoll M-15), ohne den Text der
     * Ausnahme; die Verbindung bleibt offen und beantwortet die nächste Anfrage ohne Fehler. Wächter: ein Fehler vor
     * dem Beginn des Ergebnisses, eine geschlossene Verbindung nach dem Fehler, `TDS_BACKEND_ERROR` (50004) statt 2627.
     *
     * @spec SPEC-036
     * @spec SPEC-037
     */
    @Test
    fun einFehlerDesCallbacksStehtHinterDemErgebnisUndLaesstDieVerbindungOffen() {
        val broken = SimTable(
            name = "kunden",
            columns = columns,
            primaryKey = listOf("Id"),
            rows = { RowSource { null } },
            onInsert = { error("Geheimnis des Callbacks") },
        )
        val server = start(BackendSource.Simulation(listOf(broken)))
        WireClient(server.port).use { client ->
            client.login()
            val failed = client.executeSql(
                merge(withUpdate = false),
                declarations,
                inputBigint("", 1),
                inputText("", "x"),
                inputDate("", null),
            )
            assertTrue(failed.containsUtf16("\$action"), "COLMETADATA vor dem Fehler")
            assertTrue(failed.containsBytes("AA"), "ERROR")
            assertTrue(failed.containsBytes("43 0A 00 00 01 0E"), "Nummer 2627, State 1, Class 14")
            assertFalse(failed.containsUtf16("Geheimnis"), "kein Text der Ausnahme")
            assertTrue(failed.containsBytes(INFO_TERMINATED), "INFO 3621")
            assertTrue(failed.endsWithBytes("FF 03 00 17 01 00 00 00 00 00 00 00 00  79 43 0A 00 00  $DONEPROC_OK"))
            assertEquals(0, client.batch("SELECT Id FROM kunden WHERE 1 = 0").doneStatus and DONE_ERROR)
        }
    }

    private companion object {
        /** Die Spalte `$action`: `nvarchar(10)` (Länge 20 Byte), Kollation, Flags 0 (Messprotokoll M-26). */
        const val ACTION_COLUMN = "00 00 E7 14 00 09 04 D0 00 34 07 24 00 61 00 63 00 74 00 69 00 6F 00 6E 00"
        const val FIVE = 5L
        val CLOCK: LocalTime = LocalTime.of(13, 14, 15)
        val DAY: LocalDate = LocalDate.of(2026, 1, 2)
        const val SEVEN = 7L
        const val EIGHT = 8L
        const val THREE = 3L
    }
}
