package io.github.pt9912.tdswire.protocol.error

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.protocol.session.LoginResponses
import io.github.pt9912.tdswire.protocol.token.DoneKind
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.DoneToken
import io.github.pt9912.tdswire.protocol.token.ErrorToken
import io.github.pt9912.tdswire.protocol.wire.ProtocolError
import io.github.pt9912.tdswire.protocol.wire.ProtocolFailure
import io.github.pt9912.tdswire.testsupport.TokenDecoder
import io.github.pt9912.tdswire.testsupport.hex
import io.github.pt9912.tdswire.testsupport.toHex
import io.github.pt9912.tdswire.transport.framing.PacketType
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Die Handvektoren sind von Hand nach dem Tokenlayout (MS-TDS 2.2.7.9) zusammengesetzt: AA, Länge,
 * Nummer (4 Byte), State, Class, Textlänge in Zeichen, Text UTF-16LE, leerer Servername, leere
 * Prozedur, Zeilennummer 0; dann DONE mit Statusbit ERROR. Nummern, States, Texte und Classes sind
 * Festlegung (nicht gemessen, Nummern 208 und 245 sind Vorschlag der Spezifikation); gemessen ist
 * nur, dass die Login-Ablehnung aus genau einem ERROR vor DONE besteht.
 */
class ErrorMapperTest {
    private val done = "FD 02 00 00 00 00 00 00 00 00 00 00 00"
    private val mapper = ErrorMapper()

    private fun assertBytes(code: ErrorCode, errorToken: String, severity: Int, fate: ConnectionFate) {
        val response = mapper.code(code)
        val message = assertNotNull(response.message)
        assertEquals(PacketType.TABULAR_RESULT, message.type)
        assertEquals(hex("$errorToken  $done").toHex(), message.payload.toHex())
        assertEquals(severity, code.rule.severity)
        assertEquals(fate, response.fate)
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
    fun `TDS_TIMEOUT ist ERROR 50005 Class 20 und schliesst`() = assertBytes(
        ErrorCode.TDS_TIMEOUT,
        "AA 1E 00  55 C3 00 00  01  14  08 00  54 00 69 00 6D 00 65 00 6F 00 75 00 74 00 2E 00" +
            "  00  00  00 00 00 00",
        20,
        ConnectionFate.CLOSE_AFTER_REPLY,
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

    /** @spec SPEC-038 */
    @Test
    fun `TDS_SIM_UNKNOWN_OBJECT ist ERROR 208 Class 20 und schliesst`() = assertBytes(
        ErrorCode.TDS_SIM_UNKNOWN_OBJECT,
        "AA 2C 00  D0 00 00 00  01  14  0F 00  55 00 6E 00 6B 00 6E 00 6F 00 77 00 6E 00 20 00 6F 00 62 00" +
            " 6A 00 65 00 63 00 74 00 2E 00  00  00  00 00 00 00",
        20,
        ConnectionFate.CLOSE_AFTER_REPLY,
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
     * Vollständigkeit: die Liste der Codes steht hier ausgeschrieben; ein neuer [ErrorCode] ohne
     * Eintrag in dieser Liste (und damit ohne Handvektor-Test oben) lässt den Test scheitern.
     * Der Abgleich gegen die Tabelle in `spec/spezifikation.md` läuft nicht: die Testumgebung
     * enthält die Spezifikation nicht.
     *
     * @spec SPEC-011 SPEC-012 SPEC-013 SPEC-014 SPEC-015 SPEC-016 SPEC-037 SPEC-038 SPEC-039 SPEC-048 SPEC-049
     */
    @Test
    fun `Jeder Code der Spezifikation hat genau eine Zuordnung mit eigener Nummer`() {
        val expected = setOf(
            "TDS_PROTOCOL_ERROR", "TDS_LOGIN_FAILED", "TDS_UNSUPPORTED_RPC", "TDS_UNSUPPORTED_TYPE",
            "TDS_BACKEND_ERROR", "TDS_TIMEOUT", "TDS_UNSUPPORTED_SQL", "TDS_SIM_UNKNOWN_OBJECT",
            "TDS_SIM_INVALID_VALUE", "TDS_INTERNAL_ERROR", "TDS_SHUTDOWN",
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
     * nach Voreinstellung bei den Anfragefehlern; Codes ohne Gruppe schließen (Grundregel).
     *
     * @spec SPEC-013 SPEC-015 SPEC-037 SPEC-039 SPEC-048
     */
    @Test
    fun `Gruppen der Entscheidung und Grundregel fuer Codes ohne Gruppe`() {
        val open = setOf(
            ErrorCode.TDS_UNSUPPORTED_SQL,
            ErrorCode.TDS_BACKEND_ERROR,
            ErrorCode.TDS_UNSUPPORTED_RPC,
            ErrorCode.TDS_SIM_INVALID_VALUE,
        )
        ErrorCode.entries.forEach {
            val expectedFate = if (it in open) ConnectionFate.OPEN else ConnectionFate.CLOSE_AFTER_REPLY
            assertEquals(expectedFate, it.rule.fate, it.name)
        }
        open.forEach { assertEquals(16, it.rule.severity, it.name) }
        assertEquals(20, ErrorCode.TDS_PROTOCOL_ERROR.rule.severity)
        assertEquals(20, ErrorCode.TDS_INTERNAL_ERROR.rule.severity)
    }

    /**
     * Beide Stellen, die ERROR 18456 serialisieren, liefern dieselben Bytes (Risiko des Slice).
     *
     * @spec SPEC-012
     */
    @Test
    fun `Die Login-Ablehnung des Mappings gleicht den Bytes des Handshakes`() {
        val message = assertNotNull(mapper.code(ErrorCode.TDS_LOGIN_FAILED).message)
        assertContentEquals(LoginResponses.rejection.payload, message.payload)
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
        assertEquals(DoneToken(DoneKind.DONE, DoneStatus.ERROR, 0, 0), tokens[1])
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
}
