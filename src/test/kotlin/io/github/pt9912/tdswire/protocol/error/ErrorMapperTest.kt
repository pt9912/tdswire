package io.github.pt9912.tdswire.protocol.error

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.protocol.response.ResponseForm
import io.github.pt9912.tdswire.protocol.response.SequenceOutcome
import io.github.pt9912.tdswire.protocol.response.TokenSink
import io.github.pt9912.tdswire.protocol.session.LoginResponses
import io.github.pt9912.tdswire.protocol.token.DoneCommand
import io.github.pt9912.tdswire.protocol.token.DoneKind
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.DoneToken
import io.github.pt9912.tdswire.protocol.token.ErrorToken
import io.github.pt9912.tdswire.protocol.token.InfoToken
import io.github.pt9912.tdswire.protocol.token.ReturnStatusToken
import io.github.pt9912.tdswire.protocol.token.TokenEncoder
import io.github.pt9912.tdswire.protocol.wire.ProtocolError
import io.github.pt9912.tdswire.protocol.wire.ProtocolFailure
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import io.github.pt9912.tdswire.testsupport.TokenDecoder
import io.github.pt9912.tdswire.testsupport.hex
import io.github.pt9912.tdswire.testsupport.toHex
import io.github.pt9912.tdswire.transport.framing.PacketType
import kotlinx.coroutines.runBlocking
import java.util.BitSet
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Abstand des niederwertigen Bytes der Befehlskennung vom Ende des DONE-Tokens (Zeilenzahl 8 Byte, Kennung 2 Byte). */
private const val DONE_COMMAND_LOW_FROM_END = 10

/**
 * Die Handvektoren sind von Hand nach dem Tokenlayout des Codecs zusammengesetzt: AA, Länge,
 * Nummer (4 Byte), State, Class, Textlänge in Zeichen, Text UTF-16LE, leerer Servername, leere
 * Prozedur, Zeilennummer 0; dann DONE mit Statusbit ERROR. Nummern, States, Texte und Classes sind
 * Festlegung (nicht gemessen, Nummern 208 und 245 sind Vorschlag der Spezifikation); gemessen ist
 * nur, dass die Login-Ablehnung aus genau einem ERROR vor DONE besteht. Das DONE trägt die Befehlskennung `0x00fd`
 * (Fehler ohne offenes Ergebnis, am echten SQL Server gemessen).
 */
class ErrorMapperTest {
    private val done = "FD 02 00 FD 00 00 00 00 00 00 00 00 00"

    /** RETURNSTATUS 8179 und DONEPROC `ERROR` der letzten Nummer, Befehl `0x00e0` (Messung M-23, Handvektor). */
    private val unknownLast = "79 F3 1F 00 00  FE 02 00 E0 00 00 00 00 00 00 00 00 00"

    /** RETURNSTATUS 8179 und DONEPROC `MORE|ERROR` einer Nummer vor weiteren (Messung M-22, Handvektor). */
    private val unknownMore = "79 F3 1F 00 00  FE 03 00 E0 00 00 00 00 00 00 00 00 00"
    private val mapper = ErrorMapper()

    private fun assertBytes(code: ErrorCode, errorToken: String, severity: Int, fate: ConnectionFate) {
        val response = mapper.code(code)
        val message = assertNotNull(response.message)
        assertEquals(PacketType.TABULAR_RESULT, message.type)
        assertEquals(hex("$errorToken  $done").toHex(), message.payload.toHex())
        assertEquals(severity, code.rule.severity)
        assertEquals(fate, response.fate)
    }

    /**
     * Die Antwort auf einen RPC endet nie in einem DONE (Messprotokoll W-1): ERROR und DONEPROC mit `ERROR` und der
     * Befehlskennung `0x00e0`. Eine Nummer des echten Servers (208) bekommt ihren RETURNSTATUS vor dem DONEPROC, eine
     * eigene Nummer (50010) keinen (Setzung des Nutzers vom 2026-11-06). Wächter: ein DONE am Ende, ein RETURNSTATUS
     * mit einer eigenen Nummer, ein fehlender RETURNSTATUS bei Nummer 208.
     *
     * @spec SPEC-013
     * @spec SPEC-030
     */
    @Test
    fun `Die Antwort auf einen RPC endet in DONEPROC mit ERROR, RETURNSTATUS nur bei SQL-Server-Nummern`() {
        val doneProc = "FE 02 00 E0 00 00 00 00 00 00 00 00 00"
        val own = assertNotNull(mapper.code(ErrorCode.TDS_PREPARE_LIMIT, form = ResponseForm.RPC).message)
        assertEquals(PacketType.TABULAR_RESULT, own.type)
        val ownBytes = own.payload.toHex()
        assertTrue(ownBytes.endsWith(doneProc), ownBytes)
        assertFalse(ownBytes.contains("79 "), "kein RETURNSTATUS bei eigener Nummer")
        val real = assertNotNull(mapper.code(ErrorCode.TDS_SIM_UNKNOWN_OBJECT, form = ResponseForm.RPC).message)
        assertTrue(real.payload.toHex().endsWith("79 D0 00 00 00 $doneProc"), real.payload.toHex())
        val tokens = TokenDecoder(real.payload).decodeAll()
        assertEquals(listOf(ReturnStatusToken(208)), tokens.filterIsInstance<ReturnStatusToken>())
    }

    /** @spec SPEC-011 */
    @Test
    fun `TDS_PROTOCOL_ERROR ist ERROR 50006 Class 20 und schliesst`() = assertBytes(
        ErrorCode.TDS_PROTOCOL_ERROR,
        "AA 2C 00  56 C3 00 00  01  14  0F 00  50 00 72 00 6F 00 74 00 6F 00 63 00 6F 00 6C 00 20 00 65 00" +
            " 72 00 72 00 6F 00 72 00 2E 00  00  00  00 00 00 00",
        20,
        ConnectionFate.CLOSE_AFTER_REPLY,
    )

    /** @spec SPEC-012 */
    @Test
    fun `TDS_LOGIN_FAILED ist ERROR 18456 Class 14 und schliesst`() = assertBytes(
        ErrorCode.TDS_LOGIN_FAILED,
        "AA 28 00  18 48 00 00  01  0E  0D 00  4C 00 6F 00 67 00 69 00 6E 00 20 00 66 00 61 00 69 00 6C 00" +
            " 65 00 64 00 2E 00  00  00  00 00 00 00",
        14,
        ConnectionFate.CLOSE_AFTER_REPLY,
    )

    /** @spec SPEC-013 */
    @Test
    fun `TDS_UNSUPPORTED_RPC ist ERROR 50002 Class 16 und bleibt offen`() = assertBytes(
        ErrorCode.TDS_UNSUPPORTED_RPC,
        "AA 2E 00  52 C3 00 00  01  10  10 00  55 00 6E 00 73 00 75 00 70 00 70 00 6F 00 72 00 74 00 65 00" +
            " 64 00 20 00 52 00 50 00 43 00 2E 00  00  00  00 00 00 00",
        16,
        ConnectionFate.OPEN,
    )

    /** @spec SPEC-058 */
    @Test
    fun `TDS_PREPARE_LIMIT ist ERROR 50010 Class 16 und bleibt offen`() = assertBytes(
        ErrorCode.TDS_PREPARE_LIMIT,
        "AA 4A 00  5A C3 00 00  01  10  1E 00  50 00 72 00 65 00 70 00 61 00 72 00 65 00 64 00 20 00 68 00" +
            " 61 00 6E 00 64 00 6C 00 65 00 20 00 6C 00 69 00 6D 00 69 00 74 00 20 00 72 00 65 00 61 00" +
            " 63 00 68 00 65 00 64 00 2E 00  00  00  00 00 00 00",
        16,
        ConnectionFate.OPEN,
    )

    /**
     * `TDS_SIM_KEY_VIOLATION` ist in der Form des Massenladens ERROR 2627 (Class 14, State 1), danach INFO 3621
     * „The statement has been terminated.“ (State 0, Class 0) und DONE mit `ERROR`, Befehl `0x00f0`, ohne Zähler; die
     * Verbindung bleibt offen. Nummer, Class, State, Reihenfolge der Tokens und der Abschluss sind am Referenzclient
     * gegen den echten SQL Server gemessen (Zeile M-12), der Text des ERROR ist gekürzt (Setzung); die Bytes des INFO
     * und des DONE sind Handvektor. Wächter: ein Abschluss mit Befehl `0x00fd`, ein fehlendes INFO, Class 16.
     *
     * @spec SPEC-060
     * @spec TDS-QA-01
     */
    @Test
    fun `TDS_SIM_KEY_VIOLATION ist in der Form des Massenladens ERROR 2627 INFO 3621 und DONE 0xf0`() {
        val response = mapper.code(ErrorCode.TDS_SIM_KEY_VIOLATION, form = ResponseForm.BULK)
        val message = assertNotNull(response.message)
        assertEquals(ConnectionFate.OPEN, response.fate)
        val tokens = TokenDecoder(message.payload).decodeAll()
        assertEquals(3, tokens.size)
        val error = assertIs<ErrorToken>(tokens[0]).message
        assertEquals(listOf(2627, 1, 14), listOf(error.number, error.state, error.severity))
        val info = assertIs<InfoToken>(tokens[1]).message
        assertEquals(listOf(3621, 0, 0), listOf(info.number, info.state, info.severity))
        assertEquals("The statement has been terminated.", info.text)
        assertEquals(DoneToken(DoneKind.DONE, DoneStatus.ERROR, DoneCommand.INSERT_BULK, 0), tokens[2])
        val tail = hex("00  00  00 00 00 00  FD 02 00 F0 00 00 00 00 00 00 00 00 00").toHex()
        assertTrue(message.payload.toHex().endsWith(tail))
    }

    /** Die Bytes der Antwort von [ErrorMapper.releaseAnswer] auf lauter nicht gehaltene [handles]. */
    private fun unknownHandlesAnswer(errors: ErrorMapper, handles: List<Int>): ByteArray {
        val encoder = TokenEncoder()
        val sink = TokenSink { token -> encoder.write(token) is ProtocolResult.Ok }
        val unknown = BitSet().apply { set(0, handles.size) }
        assertTrue(runBlocking { errors.releaseAnswer(handles, unknown, "unknown-prepared-handle", sink) })
        return encoder.toByteArray()
    }

    /**
     * Die Strukturabweisung ist ERROR 2714 (State 6), 2705 (State 4), 4902 (State 1), 3728 (State 1) und 3733 (State 2),
     * je Class 16, danach DONE mit
     * `ERROR`, Befehl `0x00fd`, ohne Zähler; die Verbindung bleibt offen. Nummer, Class, State, die offene Verbindung
     * und der Abschluss sind am Referenzclient gegen den echten SQL Server gemessen (Zeile M-19, 2022 und 2025; 3728 und
     * 3733: Szenario `struktur-drop-constraint`, wo ein zweites ERROR 3727 folgt, das die Bibliothek nicht sendet), der
     * Text ist ein Auszug des gemessenen Textes ohne die Namen (Setzung). Ebenso `TDS_SIM_STRING_TRUNCATED`: ERROR
     * 2628, State 1, Class 16, offene Verbindung (Zeile M-15; der Abschluss im Ergebnis steht in `ResponseMergeTest`).
     * Wächter: Class 20 mit schließender Verbindung, State 1 für alle drei, eine
     * andere Nummer, ein anderer Text.
     *
     * @spec SPEC-036
     * @spec TDS-QA-01
     */
    @Test
    fun `Strukturabweisung ist ERROR 2714, 2705, 4902, 3728 und 3733 mit gemessenem State und offener Verbindung`() {
        val expected = mapOf(
            ErrorCode.TDS_SIM_OBJECT_EXISTS to listOf(2714, 6, 16),
            ErrorCode.TDS_SIM_COLUMN_EXISTS to listOf(2705, 4, 16),
            ErrorCode.TDS_SIM_OBJECT_NOT_FOUND to listOf(4902, 1, 16),
            ErrorCode.TDS_SIM_STRING_TRUNCATED to listOf(2628, 1, 16),
            ErrorCode.TDS_SIM_NOT_A_CONSTRAINT to listOf(3728, 1, 16),
            ErrorCode.TDS_SIM_CONSTRAINT_OTHER_TABLE to listOf(3733, 2, 16),
        )
        val texts = mapOf(
            ErrorCode.TDS_SIM_OBJECT_EXISTS to "There is already an object named in the database.",
            ErrorCode.TDS_SIM_COLUMN_EXISTS to "Column names in each table must be unique.",
            ErrorCode.TDS_SIM_OBJECT_NOT_FOUND to
                "Cannot find the object because it does not exist or you do not have permissions.",
            ErrorCode.TDS_SIM_STRING_TRUNCATED to "String or binary data would be truncated.",
            ErrorCode.TDS_SIM_NOT_A_CONSTRAINT to "Name is not a constraint.",
            ErrorCode.TDS_SIM_CONSTRAINT_OTHER_TABLE to "Constraint does not belong to table.",
        )
        for ((code, numbers) in expected) {
            val response = mapper.code(code)
            assertEquals(ConnectionFate.OPEN, response.fate, code.name)
            val tokens = TokenDecoder(assertNotNull(response.message).payload).decodeAll()
            assertEquals(2, tokens.size, code.name)
            val error = assertIs<ErrorToken>(tokens[0]).message
            assertEquals(numbers, listOf(error.number, error.state, error.severity), code.name)
            assertEquals(texts.getValue(code), error.text, code.name)
            assertEquals(DoneToken(DoneKind.DONE, DoneStatus.ERROR, DoneCommand.BATCH, 0), tokens[1], code.name)
            assertTrue(code in ErrorMapper.BACKEND_CODES, code.name)
        }
    }

    /**
     * `TDS_SIM_UNKNOWN_PREPARED_HANDLE` ist ERROR 8179, State 8, Class 16 mit dem Text „Could not find prepared
     * statement with handle <n>.“ und bleibt offen. Nummer, Class, State und Wortlaut für Handle 1 sind am
     * Referenzclient gegen den echten SQL Server gemessen (Zeile M-4); die Bytes sind Handvektor. Zwei Nummern
     * ergeben zwei ERROR, je gefolgt von RETURNSTATUS 8179 und DONEPROC (`MORE|ERROR` in der Mitte, `ERROR` am Ende,
     * Befehl `0x00e0`, Handvektor der Messung M-22 und M-23), ein Log-Eintrag mit der festen Marke je Antwort.
     * Wächter: State 1 statt 8, eine andere Nummer, ein Text ohne Handle, ein ERROR nur für die erste Nummer, ein
     * fehlender Log-Eintrag, ein Log-Eintrag je Nummer.
     *
     * @spec SPEC-060
     */
    @Test
    fun `TDS_SIM_UNKNOWN_PREPARED_HANDLE ist ERROR 8179 State 8 Class 16 mit der Nummer im Text`() {
        val entries = mutableListOf<ErrorLogEntry>()
        val logging = ErrorMapper { entries += it }
        val code = ErrorCode.TDS_SIM_UNKNOWN_PREPARED_HANDLE
        val one = unknownHandlesAnswer(logging, listOf(1))
        assertEquals(
            hex(
                "AA 6E 00 F3 1F 00 00 08 10 30 00 43 00 6F 00 75 00 6C 00 64 00 20 00 6E" +
                    " 00 6F 00 74 00 20 00 66 00 69 00 6E 00 64 00 20 00 70 00 72 00 65 00 70" +
                    " 00 61 00 72 00 65 00 64 00 20 00 73 00 74 00 61 00 74 00 65 00 6D 00 65" +
                    " 00 6E 00 74 00 20 00 77 00 69 00 74 00 68 00 20 00 68 00 61 00 6E 00 64" +
                    " 00 6C 00 65 00 20 00 31 00 2E 00 00 00 00 00 00 00  $unknownLast",
            ).toHex(),
            one.toHex(),
        )
        val two = unknownHandlesAnswer(logging, listOf(1, 12))
        assertEquals(
            hex(
                "AA 6E 00 F3 1F 00 00 08 10 30 00 43 00 6F 00 75 00 6C 00 64 00 20 00 6E" +
                    " 00 6F 00 74 00 20 00 66 00 69 00 6E 00 64 00 20 00 70 00 72 00 65 00 70" +
                    " 00 61 00 72 00 65 00 64 00 20 00 73 00 74 00 61 00 74 00 65 00 6D 00 65" +
                    " 00 6E 00 74 00 20 00 77 00 69 00 74 00 68 00 20 00 68 00 61 00 6E 00 64" +
                    " 00 6C 00 65 00 20 00 31 00 2E 00 00 00 00 00 00 00  $unknownMore  " +
                    "AA 70 00 F3 1F 00 00 08 10 31 00 43 00 6F 00 75 00 6C 00 64 00 20 00 6E" +
                    " 00 6F 00 74 00 20 00 66 00 69 00 6E 00 64 00 20 00 70 00 72 00 65 00 70" +
                    " 00 61 00 72 00 65 00 64 00 20 00 73 00 74 00 61 00 74 00 65 00 6D 00 65" +
                    " 00 6E 00 74 00 20 00 77 00 69 00 74 00 68 00 20 00 68 00 61 00 6E 00 64" +
                    " 00 6C 00 65 00 20 00 31 00 32 00 2E 00 00 00 00 00 00 00  $unknownLast",
            ).toHex(),
            two.toHex(),
        )
        assertEquals(16, code.rule.severity)
        assertEquals(8, code.rule.state)
        assertEquals(List(2) { ErrorLogEntry(code, "unknown-prepared-handle") }, entries)
    }

    /**
     * `sp_execute` mit unbekanntem Handle ist dieselbe Nummer 8179 mit State 4 (Class 16, Text mit der Nummer des
     * Handles) in der Form RPC: ERROR, RETURNSTATUS 8179 und DONEPROC mit `ERROR` (Befehl `0x00e0`), ohne DONE; die
     * Verbindung bleibt offen, das Log trägt die übergebene Marke. Nummer, Class, State und Text (Handle 999999) sind
     * am Referenzclient gemessen (M-6); RETURNSTATUS und die leeren Felder des ERROR sind Setzung (Bytes sind
     * Handvektor, der Rest der Antwort wie `unknownLast`). Die Regel selbst behält State 8 der Batch-Freigabe.
     * Wächter: State 8 oder 1 statt 4, die Nummer 50009, ein Text ohne Handle, die Form BATCH (DONE), ein fehlender
     * Log-Eintrag.
     *
     * @spec SPEC-057
     * @spec SPEC-060
     */
    @Test
    fun `sp_execute mit unbekanntem Handle ist ERROR 8179 State 4 in der Form RPC`() {
        val entries = mutableListOf<ErrorLogEntry>()
        val response = ErrorMapper { entries += it }.executeUnknownHandle(1, "unknown-handle")
        assertEquals(ConnectionFate.OPEN, response.fate)
        assertEquals(ErrorCode.TDS_SIM_UNKNOWN_PREPARED_HANDLE, response.code)
        assertEquals(
            hex(
                "AA 6E 00 F3 1F 00 00 04 10 30 00 43 00 6F 00 75 00 6C 00 64 00 20 00 6E" +
                    " 00 6F 00 74 00 20 00 66 00 69 00 6E 00 64 00 20 00 70 00 72 00 65 00 70" +
                    " 00 61 00 72 00 65 00 64 00 20 00 73 00 74 00 61 00 74 00 65 00 6D 00 65" +
                    " 00 6E 00 74 00 20 00 77 00 69 00 74 00 68 00 20 00 68 00 61 00 6E 00 64" +
                    " 00 6C 00 65 00 20 00 31 00 2E 00 00 00 00 00 00 00  $unknownLast",
            ).toHex(),
            assertNotNull(response.message).payload.toHex(),
        )
        assertEquals(8, ErrorCode.TDS_SIM_UNKNOWN_PREPARED_HANDLE.rule.state)
        assertEquals(listOf(ErrorLogEntry(ErrorCode.TDS_SIM_UNKNOWN_PREPARED_HANDLE, "unknown-handle")), entries)
    }

    /**
     * Die Regel mit Nummer im Text verlangt die Nummer, und eine Regel ohne verbietet sie: `message` für die falsche
     * Seite wirft, statt einen Text ohne Nummer oder mit einer fremden zu senden; die Antwort auf null Nummern gibt es
     * nicht. Wächter: ein `message()` ohne Prüfung.
     *
     * @spec SPEC-060
     */
    @Test
    fun `Eine Regel mit Handle im Text verlangt die Nummer und die anderen verbieten sie`() {
        assertFailsWith<IllegalArgumentException> { ErrorCode.TDS_SIM_UNKNOWN_PREPARED_HANDLE.rule.message() }
        assertFailsWith<IllegalArgumentException> { ErrorCode.TDS_PREPARE_LIMIT.rule.message(1) }
        assertFailsWith<IllegalArgumentException> { mapper.code(ErrorCode.TDS_SIM_UNKNOWN_PREPARED_HANDLE) }
    }

    /** @spec SPEC-014 */
    @Test
    fun `TDS_UNSUPPORTED_TYPE ist ERROR 50003 Class 20 und schliesst`() = assertBytes(
        ErrorCode.TDS_UNSUPPORTED_TYPE,
        "AA 30 00  53 C3 00 00  01  14  11 00  55 00 6E 00 73 00 75 00 70 00 70 00 6F 00 72 00 74 00 65 00" +
            " 64 00 20 00 74 00 79 00 70 00 65 00 2E 00  00  00  00 00 00 00",
        20,
        ConnectionFate.CLOSE_AFTER_REPLY,
    )

    /** @spec SPEC-015 */
    @Test
    fun `TDS_BACKEND_ERROR ist ERROR 50004 Class 16 und bleibt offen`() = assertBytes(
        ErrorCode.TDS_BACKEND_ERROR,
        "AA 2A 00  54 C3 00 00  01  10  0E 00  42 00 61 00 63 00 6B 00 65 00 6E 00 64 00 20 00 65 00 72 00" +
            " 72 00 6F 00 72 00 2E 00  00  00  00 00 00 00",
        16,
        ConnectionFate.OPEN,
    )

    /** @spec SPEC-016 */
    @Test
    fun `TDS_TIMEOUT ist ERROR 50005 Class 16 und bleibt offen`() = assertBytes(
        ErrorCode.TDS_TIMEOUT,
        "AA 1E 00  55 C3 00 00  01  10  08 00  54 00 69 00 6D 00 65 00 6F 00 75 00 74 00 2E 00" +
            "  00  00  00 00 00 00",
        16,
        ConnectionFate.OPEN,
    )

    /** @spec SPEC-037 */
    @Test
    fun `TDS_UNSUPPORTED_SQL ist ERROR 50001 Class 16 und bleibt offen`() = assertBytes(
        ErrorCode.TDS_UNSUPPORTED_SQL,
        "AA 2E 00  51 C3 00 00  01  10  10 00  55 00 6E 00 73 00 75 00 70 00 70 00 6F 00 72 00 74 00 65 00" +
            " 64 00 20 00 53 00 51 00 4C 00 2E 00  00  00  00 00 00 00",
        16,
        ConnectionFate.OPEN,
    )

    /**
     * Nummer 208, State 1, Class 16 und die offene Verbindung sind am Referenzclient gegen den echten SQL Server
     * gemessen (Zeile M-15 und Schritt `fehler-vor-zeilen` des Messprotokolls); der Text ist Festlegung, die Bytes
     * sind Handvektor. Wächter: Class 20 mit schließender Verbindung, eine andere Nummer.
     *
     * @spec SPEC-038
     * @spec TDS-QA-01
     */
    @Test
    fun `TDS_SIM_UNKNOWN_OBJECT ist ERROR 208 Class 16 und bleibt offen`() = assertBytes(
        ErrorCode.TDS_SIM_UNKNOWN_OBJECT,
        "AA 2C 00  D0 00 00 00  01  10  0F 00  55 00 6E 00 6B 00 6E 00 6F 00 77 00 6E 00 20 00 6F 00 62 00" +
            " 6A 00 65 00 63 00 74 00 2E 00  00  00  00 00 00 00",
        16,
        ConnectionFate.OPEN,
    )

    /** @spec SPEC-039 */
    @Test
    fun `TDS_SIM_INVALID_VALUE ist ERROR 245 Class 16 und bleibt offen`() = assertBytes(
        ErrorCode.TDS_SIM_INVALID_VALUE,
        "AA 2A 00  F5 00 00 00  01  10  0E 00  49 00 6E 00 76 00 61 00 6C 00 69 00 64 00 20 00 76 00 61 00" +
            " 6C 00 75 00 65 00 2E 00  00  00  00 00 00 00",
        16,
        ConnectionFate.OPEN,
    )

    /** @spec SPEC-048 */
    @Test
    fun `TDS_INTERNAL_ERROR ist ERROR 50007 Class 20 und schliesst`() = assertBytes(
        ErrorCode.TDS_INTERNAL_ERROR,
        "AA 2C 00  57 C3 00 00  01  14  0F 00  49 00 6E 00 74 00 65 00 72 00 6E 00 61 00 6C 00 20 00 65 00" +
            " 72 00 72 00 6F 00 72 00 2E 00  00  00  00 00 00 00",
        20,
        ConnectionFate.CLOSE_AFTER_REPLY,
    )

    /** @spec SPEC-049 */
    @Test
    fun `TDS_SHUTDOWN ist ERROR 50008 Class 20 und schliesst`() = assertBytes(
        ErrorCode.TDS_SHUTDOWN,
        "AA 3E 00  58 C3 00 00  01  14  18 00  53 00 65 00 72 00 76 00 65 00 72 00 20 00 69 00 73 00 20 00" +
            " 73 00 68 00 75 00 74 00 74 00 69 00 6E 00 67 00 20 00 64 00 6F 00 77 00 6E 00 2E 00" +
            "  00  00  00 00 00 00",
        20,
        ConnectionFate.CLOSE_AFTER_REPLY,
    )

    /**
     * Die Liste der Codes steht hier ausgeschrieben; ein neuer [ErrorCode] ohne Eintrag in dieser
     * Liste (und damit ohne Handvektor-Test oben) lässt den Test scheitern. Die Liste ist eine Kopie
     * der Tabelle in `spec/spezifikation.md`, kein Abgleich mit ihr: die Testumgebung enthält die
     * Spezifikation nicht. Der Test trägt deshalb kein `@spec`; die Codes belegen die Handvektor-Tests.
     */
    @Test
    fun `Die Enum gleicht der im Test ausgeschriebenen Liste der Codes mit eigener Nummer`() {
        val expected = setOf(
            "TDS_PROTOCOL_ERROR", "TDS_LOGIN_FAILED", "TDS_UNSUPPORTED_RPC", "TDS_UNSUPPORTED_TYPE",
            "TDS_BACKEND_ERROR", "TDS_TIMEOUT", "TDS_UNSUPPORTED_SQL", "TDS_SIM_UNKNOWN_OBJECT",
            "TDS_SIM_INVALID_VALUE", "TDS_INTERNAL_ERROR", "TDS_SHUTDOWN", "TDS_PREPARE_LIMIT",
            "TDS_SIM_UNKNOWN_PREPARED_HANDLE", "TDS_SIM_KEY_VIOLATION",
            "TDS_SIM_OBJECT_EXISTS", "TDS_SIM_COLUMN_EXISTS", "TDS_SIM_OBJECT_NOT_FOUND", "TDS_SIM_STRING_TRUNCATED",
            "TDS_SIM_NOT_A_CONSTRAINT", "TDS_SIM_CONSTRAINT_OTHER_TABLE",
        )
        assertEquals(expected, ErrorCode.entries.map { it.name }.toSet())
        assertEquals(expected.size, ErrorCode.entries.map { it.rule.number }.toSet().size)
    }

    /**
     * Die Regel der `ADR-0007`: offen genau bei Severity bis 19, sonst schließt der Server. Einzige
     * Ausnahme ist der abgelehnte Login (Class 14, schließt).
     *
     * @spec SPEC-011 SPEC-012
     */
    @Test
    fun `Verbindungsfolge und Severity folgen der Schwelle 19`() {
        ErrorCode.entries.filter { it != ErrorCode.TDS_LOGIN_FAILED }.forEach {
            assertEquals(it.rule.severity <= 19, it.rule.fate == ConnectionFate.OPEN, it.name)
        }
        assertEquals(ConnectionFate.CLOSE_AFTER_REPLY, ErrorCode.TDS_LOGIN_FAILED.rule.fate)
    }

    /**
     * Die Gruppen der `ADR-0007`: Anfragefehler Class 16 offen, `TDS_PROTOCOL_ERROR` und
     * `TDS_INTERNAL_ERROR` Class 20 schließen, `TDS_UNSUPPORTED_RPC` und `TDS_SIM_INVALID_VALUE`
     * nach Voreinstellung bei den Anfragefehlern, `TDS_TIMEOUT` nach Setzung des Nutzers vom 2026-11-01,
     * `TDS_SIM_KEY_VIOLATION` bleibt offen mit der gemessenen Class 14; Codes ohne
     * Gruppe schließen (Grundregel).
     *
     * @spec SPEC-013 SPEC-015 SPEC-037 SPEC-039 SPEC-048 SPEC-057 SPEC-058
     */
    @Test
    fun `Gruppen der Entscheidung und Grundregel fuer Codes ohne Gruppe`() {
        val open = setOf(
            ErrorCode.TDS_UNSUPPORTED_SQL,
            ErrorCode.TDS_BACKEND_ERROR,
            ErrorCode.TDS_UNSUPPORTED_RPC,
            ErrorCode.TDS_SIM_INVALID_VALUE,
            ErrorCode.TDS_SIM_UNKNOWN_OBJECT,
            ErrorCode.TDS_PREPARE_LIMIT,
            ErrorCode.TDS_SIM_UNKNOWN_PREPARED_HANDLE,
            ErrorCode.TDS_SIM_KEY_VIOLATION,
            ErrorCode.TDS_SIM_OBJECT_EXISTS,
            ErrorCode.TDS_SIM_COLUMN_EXISTS,
            ErrorCode.TDS_SIM_OBJECT_NOT_FOUND,
            ErrorCode.TDS_SIM_STRING_TRUNCATED,
            ErrorCode.TDS_SIM_NOT_A_CONSTRAINT,
            ErrorCode.TDS_SIM_CONSTRAINT_OTHER_TABLE,
            ErrorCode.TDS_TIMEOUT,
        )
        ErrorCode.entries.forEach {
            val expectedFate = if (it in open) ConnectionFate.OPEN else ConnectionFate.CLOSE_AFTER_REPLY
            assertEquals(expectedFate, it.rule.fate, it.name)
        }
        (open - ErrorCode.TDS_SIM_KEY_VIOLATION).forEach { assertEquals(16, it.rule.severity, it.name) }
        assertEquals(14, ErrorCode.TDS_SIM_KEY_VIOLATION.rule.severity)
        assertEquals(20, ErrorCode.TDS_PROTOCOL_ERROR.rule.severity)
        assertEquals(20, ErrorCode.TDS_INTERNAL_ERROR.rule.severity)
    }

    /**
     * Beide Stellen, die ERROR 18456 serialisieren, liefern dieselben Bytes bis auf die Befehlskennung des DONE:
     * der Mapper schreibt `0x00fd` (Fehler ohne offenes Ergebnis, gemessen), die Ablehnung des Logins 0 (für die
     * Ablehnung gibt es keine Messung).
     *
     * @spec SPEC-012
     */
    @Test
    fun `Die Login-Ablehnung des Mappings gleicht den Bytes des Handshakes`() {
        val message = assertNotNull(mapper.code(ErrorCode.TDS_LOGIN_FAILED).message)
        val mapped = message.payload.copyOf()
        mapped[mapped.size - DONE_COMMAND_LOW_FROM_END] = 0
        assertContentEquals(LoginResponses.rejection.payload, mapped)
    }

    /**
     * Selbstkonsistenz (Decoder des Tests, kein Orakel): ERROR gefolgt von DONE mit Fehlerstatus.
     *
     * @spec SPEC-015
     */
    @Test
    fun `Die Antwort ist ein ERROR gefolgt von DONE mit Fehlerstatus`() {
        val message = assertNotNull(mapper.code(ErrorCode.TDS_BACKEND_ERROR).message)
        val tokens = TokenDecoder(message.payload).decodeAll()
        assertEquals(2, tokens.size)
        val error = (tokens[0] as ErrorToken).message
        assertEquals(50004, error.number)
        assertEquals(16, error.severity)
        assertEquals(DoneToken(DoneKind.DONE, DoneStatus.ERROR, DoneCommand.BATCH, 0), tokens[1])
    }

    /**
     * Eine Ausnahme mit einem Konfigurationswert im Text erreicht den Client als TDS_INTERNAL_ERROR
     * mit festem Text; weder die Antwortbytes (ASCII oder UTF-16LE) noch der Log-Eintrag tragen den
     * Wert, der Eintrag nur den Typnamen.
     *
     * @spec SPEC-048
     */
    @Test
    fun `Der Text einer Ausnahme steht weder in der Antwort noch im Log`() {
        val secret = "jdbc:sql://host/db?password=Gehe1mnis"
        val entries = mutableListOf<ErrorLogEntry>()
        val response = ErrorMapper { entries += it }.unexpected(IllegalStateException(secret))
        val message = assertNotNull(response.message)

        assertEquals(ErrorCode.TDS_INTERNAL_ERROR, response.code)
        assertEquals(ConnectionFate.CLOSE_AFTER_REPLY, response.fate)
        assertEquals(mapper.code(ErrorCode.TDS_INTERNAL_ERROR).message, message)
        assertFalse(String(message.payload, Charsets.ISO_8859_1).contains("Gehe1mnis"))
        assertFalse(String(message.payload, Charsets.UTF_16LE).contains("Gehe1mnis"))
        assertEquals(listOf(ErrorLogEntry(ErrorCode.TDS_INTERNAL_ERROR, "java.lang.IllegalStateException")), entries)
        assertFalse(entries.single().toString().contains("Gehe1mnis"))
    }

    /**
     * Ein Failure mit eigenem Code wird nach diesem Code abgebildet, sein Text erreicht den Client
     * nicht; ein fremder Code ist keine erwartete Ablehnung und wird TDS_INTERNAL_ERROR.
     *
     * @spec SPEC-015 SPEC-048
     */
    @Test
    fun `Failure mit bekanntem Code wird abgebildet und mit fremdem Code zum internen Fehler`() {
        val entries = mutableListOf<ErrorLogEntry>()
        val mapper = ErrorMapper { entries += it }

        val known = mapper.failure(QueryEvent.Failure("TDS_BACKEND_ERROR", "Gehe1mnis"))
        assertEquals(ErrorCode.TDS_BACKEND_ERROR, known.code)
        assertFalse(String(assertNotNull(known.message).payload, Charsets.UTF_16LE).contains("Gehe1mnis"))

        val foreign = mapper.failure(QueryEvent.Failure("ORA-00001", "Gehe1mnis"))
        assertEquals(ErrorCode.TDS_INTERNAL_ERROR, foreign.code)
        assertEquals(ConnectionFate.CLOSE_AFTER_REPLY, foreign.fate)
        assertEquals(ErrorLogEntry(ErrorCode.TDS_INTERNAL_ERROR, "unknown-failure-code"), entries.last())
        assertFalse(entries.toString().contains("ORA-00001"))
    }

    /**
     * Die Kategorie des Codecs bestimmt den Code (`SPEC-011`, `SPEC-014`).
     *
     * @spec SPEC-011 SPEC-014
     */
    @Test
    fun `Codec-Fehler werden nach ihrer Kategorie abgebildet`() {
        assertEquals(
            ErrorCode.TDS_PROTOCOL_ERROR,
            mapper.protocol(ProtocolError(ProtocolFailure.TRUNCATED, "d")).code,
        )
        assertEquals(
            ErrorCode.TDS_UNSUPPORTED_TYPE,
            mapper.protocol(ProtocolError(ProtocolFailure.TOO_LONG, "d")).code,
        )
    }

    /**
     * Ist der Strom nicht synchron, gibt es keine Antwort und die Verbindung schließt (`SPEC-031`);
     * der Fehler wird trotzdem geloggt.
     *
     * @spec SPEC-031
     */
    @Test
    fun `Bei nicht synchronem Strom schliesst die Verbindung ohne Antwort`() {
        val entries = mutableListOf<ErrorLogEntry>()
        val response = ErrorMapper { entries += it }.code(ErrorCode.TDS_BACKEND_ERROR, streamSynchronous = false)
        assertNull(response.message)
        assertEquals(ConnectionFate.CLOSE_WITHOUT_REPLY, response.fate)
        assertTrue(entries.single().code == ErrorCode.TDS_BACKEND_ERROR)
    }

    /**
     * Der Negativpfad gilt für jeden Einstieg: `failure`, `protocol` und `unexpected` liefern bei nicht
     * synchronem Strom keine Antwort; die Folge ist `CLOSE_WITHOUT_REPLY` und der Log-Eintrag bleibt.
     *
     * @spec SPEC-031
     */
    @Test
    fun `Jeder Einstieg schliesst bei nicht synchronem Strom ohne Antwort`() {
        val entries = mutableListOf<ErrorLogEntry>()
        val mapper = ErrorMapper { entries += it }
        val responses = listOf(
            mapper.failure(QueryEvent.Failure("TDS_BACKEND_ERROR", "m"), streamSynchronous = false),
            mapper.protocol(ProtocolError(ProtocolFailure.TRUNCATED, "d"), streamSynchronous = false),
            mapper.unexpected(IllegalStateException("x"), streamSynchronous = false),
        )
        responses.forEach {
            assertNull(it.message)
            assertEquals(ConnectionFate.CLOSE_WITHOUT_REPLY, it.fate)
        }
        assertEquals(3, entries.size)
    }

    /**
     * Positivliste: nur die Codes aus [ErrorMapper.BACKEND_CODES] kommen als Backend-Fehler durch. Jeder
     * andere eigene Code (Login, Beenden, Protokoll, RPC, intern) wird `TDS_INTERNAL_ERROR` mit
     * Verbindungsende; der Log nennt die Konstante, nicht den Code des Backends.
     *
     * @spec SPEC-048
     */
    @Test
    fun `Failure mit einem Code, den nur der Server erzeugt, wird zum internen Fehler`() {
        val entries = mutableListOf<ErrorLogEntry>()
        val mapper = ErrorMapper { entries += it }
        val serverOnly = ErrorCode.entries - ErrorMapper.BACKEND_CODES.toSet()
        assertEquals(
            setOf(
                ErrorCode.TDS_LOGIN_FAILED,
                ErrorCode.TDS_SHUTDOWN,
                ErrorCode.TDS_PROTOCOL_ERROR,
                ErrorCode.TDS_UNSUPPORTED_RPC,
                ErrorCode.TDS_INTERNAL_ERROR,
                ErrorCode.TDS_PREPARE_LIMIT,
                ErrorCode.TDS_SIM_UNKNOWN_PREPARED_HANDLE,
            ),
            serverOnly.toSet(),
        )
        serverOnly.forEach {
            val response = mapper.failure(QueryEvent.Failure(it.name, "m"))
            assertEquals(ErrorCode.TDS_INTERNAL_ERROR, response.code, it.name)
            assertEquals(ConnectionFate.CLOSE_AFTER_REPLY, response.fate, it.name)
            assertEquals(ErrorLogEntry(ErrorCode.TDS_INTERNAL_ERROR, "unknown-failure-code"), entries.last())
        }
        ErrorMapper.BACKEND_CODES.forEach {
            assertEquals(it, mapper.failure(QueryEvent.Failure(it.name, "m")).code, it.name)
        }
    }

    /**
     * Die Meldung des Adapters für den Sequenzierer ist die Meldung der Regel, und ihre Abbildung
     * schließt genau dann, wenn die Folge der Regel nicht `OPEN` ist.
     *
     * @spec SPEC-015
     */
    @Test
    fun `Der Adapter liefert Meldung und Schliessen der Regel`() {
        val mapping = mapper.sequencerMapping()
        ErrorMapper.BACKEND_CODES.forEach {
            val mapped = mapping.backend(QueryEvent.Failure(it.name, "m"))
            assertEquals(it.rule.message(), mapped.message, it.name)
            assertEquals(it.rule.fate != ConnectionFate.OPEN, mapped.closesConnection, it.name)
        }
        val internal = mapping.internal(null)
        assertEquals(ErrorCode.TDS_INTERNAL_ERROR.rule.message(), internal.message)
        assertTrue(internal.closesConnection)
        assertEquals(internal.message, mapping.internal(IllegalStateException("geheim")).message)
    }

    /**
     * `fateOf` ist total über die Ausgänge des Sequenzierers.
     *
     * @spec SPEC-031
     */
    @Test
    fun `fateOf bildet jeden Ausgang des Sequenzierers ab`() {
        assertEquals(
            mapOf(
                SequenceOutcome.COMPLETED to ConnectionFate.OPEN,
                SequenceOutcome.FAILED to ConnectionFate.OPEN,
                SequenceOutcome.FAILED_CLOSE_CONNECTION to ConnectionFate.CLOSE_AFTER_REPLY,
                SequenceOutcome.CLOSE_WITHOUT_COMPLETION to ConnectionFate.CLOSE_WITHOUT_REPLY,
            ),
            SequenceOutcome.entries.associateWith { mapper.fateOf(it) },
        )
    }

    /**
     * Der Adapter schreibt dieselben Log-Einträge wie die Einstiege `failure` und `protocol`.
     *
     * @spec SPEC-048
     */
    @Test
    fun `Der Adapter fuer den Sequenzierer loggt wie die Einstiege`() {
        val entries = mutableListOf<ErrorLogEntry>()
        val mapping = ErrorMapper { entries += it }.sequencerMapping()
        mapping.backend(QueryEvent.Failure("ORA-00001", "Gehe1mnis"))
        mapping.backend(QueryEvent.Failure("TDS_BACKEND_ERROR", "m"))
        mapping.typeMapping(ProtocolError(ProtocolFailure.TOO_LONG, "d"))
        mapping.internal(IllegalStateException("Gehe1mnis"))
        mapping.internal(null)
        assertEquals(
            listOf(
                ErrorLogEntry(ErrorCode.TDS_INTERNAL_ERROR, "unknown-failure-code"),
                ErrorLogEntry(ErrorCode.TDS_BACKEND_ERROR, null),
                ErrorLogEntry(ErrorCode.TDS_UNSUPPORTED_TYPE, null),
                ErrorLogEntry(ErrorCode.TDS_INTERNAL_ERROR, IllegalStateException::class.java.name),
                ErrorLogEntry(ErrorCode.TDS_INTERNAL_ERROR, "sequence-violation"),
            ),
            entries,
        )
    }
}
