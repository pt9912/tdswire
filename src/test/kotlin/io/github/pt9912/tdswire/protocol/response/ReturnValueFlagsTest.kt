package io.github.pt9912.tdswire.protocol.response

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.port.ScriptedBackend
import io.github.pt9912.tdswire.protocol.token.ColumnDef
import io.github.pt9912.tdswire.protocol.token.ReturnValueToken
import io.github.pt9912.tdswire.protocol.token.TdsType
import io.github.pt9912.tdswire.protocol.token.TdsValue
import io.github.pt9912.tdswire.protocol.token.TokenEncoder
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Das Flag-Feld des RETURNVALUE eines Ausgabewerts des Backends ist `0x0000` (`SPEC-030`, `TDS-FA-10`). Gemessen am
 * echten SQL Server (Messprotokoll `docs/messungen/referenzabgleich.md`, A-20 und M-24, je Instanz 2022 und 2025, der
 * Referenzclient `Microsoft.Data.SqlClient`): `int`, `smallint`, `bigint`, `bit`, `decimal(10,2)`, `float`,
 * `nvarchar(50)`, `date`, `time` und `datetime2`, jeweils mit Wert und mit NULL, dazu ein nicht zugewiesener
 * Ausgabewert; NULL ändert das Feld nicht. Die Typen, die das Backend über [SqlType] liefern kann, laufen hier durch
 * den Sequenzierer: neun der zehn gemessenen (`smallint` über [SqlType.Int16]); `tinyint` ([SqlType.UInt8]) läuft
 * mit, ist aber nicht gemessen (M-24 nennt es nicht). Für `float` gibt es weder einen [SqlType] noch einen Typ der
 * Tokens: der Fall ist nicht gebaut und hat keinen Test.
 */
class ReturnValueFlagsTest {
    private val request = QueryRequest("s", "r", "SELECT 1", emptyList(), null)

    /** Die Bytes des RETURNVALUE zum Ausgabewert [value] vom Typ [type], wie der Sequenzierer es schreibt. */
    private fun returnValue(type: SqlType, value: Any?): ByteArray {
        val sink = RecordingSink()
        val outcome = runBlocking {
            ResponseSequencer(FIXED_MAPPING).run(
                ScriptedBackend.ofList(QueryEvent.OutputParameter("@a", type, value)),
                request,
                sink,
                ResponseForm.RPC,
                CallLayout.EXECUTE_SQL,
            )
        }
        assertEquals(SequenceOutcome.COMPLETED, outcome, "$type $value")
        return bytesOf(sink.tokens.filterIsInstance<ReturnValueToken>().single())
    }

    private fun bytesOf(token: ReturnValueToken): ByteArray {
        val encoder = TokenEncoder()
        assertIs<ProtocolResult.Ok<*>>(encoder.write(token))
        return encoder.toByteArray()
    }

    /**
     * Das Flag-Feld steht hinter `AC`, Ordinal (2), Name `@a` als B_VARCHAR (1 + 4), Status (1) und UserType (4), also
     * an den Bytes 13 und 14. Die Stellen davor werden mitgeprüft (Status 1 = OUTPUT, UserType 0), damit ein
     * verschobener Offset den Test nicht grün lässt.
     */
    private fun assertFlagsZero(type: SqlType, value: Any?, label: String) {
        val bytes = returnValue(type, value)
        assertEquals(0xAC, bytes[0].toInt() and 0xFF, label)
        assertEquals(1, bytes[8].toInt(), "$label: Status OUTPUT")
        assertEquals(listOf<Byte>(0, 0, 0, 0), bytes.slice(9..12), "$label: UserType")
        assertEquals(listOf<Byte>(0, 0), bytes.slice(13..14), "$label: Flag-Feld")
    }

    /**
     * Je Typ, den das Backend liefern kann, mit Wert und mit NULL: Flag-Feld `0x0000` (M-24, zehn Typen am echten
     * Server, hier die neun gemessenen über [SqlType] erreichbaren und `tinyint` ohne Messung). Wächter: das Flag-Feld
     * aus der Nullbarkeit der Spalte (`ColumnDef.nullable`), das bei jedem Ausgabewert des Backends `0x0001` ergäbe.
     *
     * @spec SPEC-030
     * @spec TDS-FA-10
     */
    @Test
    fun `Das Flag-Feld des RETURNVALUE ist 0x0000 fuer jeden Typ, mit Wert und mit NULL`() {
        val values = listOf<Triple<String, SqlType, Any>>(
            Triple("int", SqlType.Int32, 4),
            Triple("smallint", SqlType.Int16, 4),
            Triple("tinyint (nicht gemessen)", SqlType.UInt8, 4),
            Triple("bigint", SqlType.Int64, 4L),
            Triple("bit", SqlType.Bool, true),
            Triple("decimal", SqlType.Decimal(10, 2), BigDecimal("12.34")),
            Triple("nvarchar", SqlType.UnicodeText, "text"),
            Triple("date", SqlType.Date, LocalDate.of(2026, 11, 8)),
            Triple("time", SqlType.Time, LocalTime.of(12, 30, 15)),
            Triple("datetime2", SqlType.DateTime, LocalDateTime.of(2026, 11, 8, 12, 30, 15)),
        )
        for ((name, type, value) in values) {
            assertFlagsZero(type, value, "$name mit Wert")
            assertFlagsZero(type, null, "$name mit NULL")
        }
    }

    /**
     * Der Schreiber des RETURNVALUE schreibt auch für einen nullbaren `INTN` 2 und 8 das Feld `0x0000`, unabhängig vom
     * Backend-Weg (M-24: `INTN` 2 und 8 Byte). Wächter: ein Schreiber, der das Feld aus `ColumnDef.nullable` bildet.
     *
     * @spec SPEC-030
     * @spec TDS-FA-10
     */
    @Test
    fun `Das Flag-Feld eines nullbaren INTN 2 und 8 ist 0x0000`() {
        for (size in listOf(2, 8)) {
            val column = ColumnDef("", TdsType.IntNType(size), nullable = true)
            val bytes = bytesOf(ReturnValueToken(2, "@a", 1, column, TdsValue.IntegerValue(4)))
            assertEquals(listOf<Byte>(0, 0), bytes.slice(13..14), "INTN $size")
            assertEquals(0x26, bytes[15].toInt(), "INTN $size: TYPE_INFO")
            assertEquals(size, bytes[16].toInt(), "INTN $size: Länge")
        }
    }
}
