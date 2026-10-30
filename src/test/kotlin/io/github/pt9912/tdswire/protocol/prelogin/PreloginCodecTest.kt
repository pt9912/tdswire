package io.github.pt9912.tdswire.protocol.prelogin

import io.github.pt9912.tdswire.protocol.wire.ProtocolErrorCategory
import io.github.pt9912.tdswire.protocol.wire.ProtocolFailure
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import io.github.pt9912.tdswire.testsupport.hex
import io.github.pt9912.tdswire.testsupport.toHex
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PreloginCodecTest {
    /**
     * Aufbau der Anfrage nach MS-TDS 2.2.6.5 und 4.1.1 von Hand: fünf Optionen (VERSION, ENCRYPTION,
     * INSTOPT, THREADID, MARS) zu je 5 Byte Tabelle, Terminator `FF`, Tabelle = 26 Byte = `0x1A`,
     * dann 6 + 1 + 1 + 4 + 1 Byte Daten; die Nachricht ist 39 Byte lang.
     */
    private val request = hex(
        "00 00 1A 00 06  01 00 20 00 01  02 00 21 00 01  03 00 22 00 04  04 00 26 00 01  FF " +
            "0B 00 00 00 00 00  00  00  00 00 05 39  00",
    )

    private fun parsed(bytes: ByteArray): PreloginMessage =
        assertIs<ProtocolResult.Ok<PreloginMessage>>(PreloginCodec.parse(bytes)).value

    private fun failureOf(bytes: ByteArray): ProtocolFailure =
        assertIs<ProtocolResult.Failed>(PreloginCodec.parse(bytes)).error.failure

    /**
     * Orakel: die Optionen der Handvektor-Anfrage mit ihren Daten (Offsets `0x1A`, `0x20`, `0x21`,
     * `0x22`, `0x26`).
     *
     * @spec SPEC-028
     */
    @Test
    fun `Anfrage nach MS-TDS wird anhand der Tabelle gelesen`() {
        val message = parsed(request)
        assertEquals(
            listOf(
                PreloginToken.VERSION,
                PreloginToken.ENCRYPTION,
                PreloginToken.INSTOPT,
                PreloginToken.THREADID,
                PreloginToken.MARS,
            ),
            message.options.map { it.token },
        )
        assertEquals("0B 00 00 00 00 00", message.option(PreloginToken.VERSION)?.data?.toHex())
        assertEquals(EncryptionMode.OFF, message.encryption)
        assertEquals("00 00 05 39", message.option(PreloginToken.THREADID)?.data?.toHex())
        assertNull(message.fedAuthRequired)
    }

    /**
     * Orakel: die Antwort mit VERSION, ENCRYPTION (REQUIRED = 3), INSTOPT (leer) und MARS (aus) hat
     * die Tabelle von 4 * 5 + 1 = 21 = `0x15` Byte; die Daten folgen ab `0x15`, `0x1B`, `0x1C`, `0x1D`.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Antwort wird mit Offset-Tabelle und Terminator geschrieben`() {
        val message = PreloginMessage(
            listOf(
                PreloginOption.version(11, 0, 0, 0),
                PreloginOption.encryption(EncryptionMode.REQUIRED),
                PreloginOption.instance(""),
                PreloginOption.mars(false),
            ),
        )
        val encoded = assertIs<ProtocolResult.Ok<ByteArray>>(PreloginCodec.encode(message)).value
        assertEquals(
            "00 00 15 00 06 01 00 1B 00 01 02 00 1C 00 01 04 00 1D 00 01 FF 0B 00 00 00 00 00 03 00 00",
            encoded.toHex(),
        )
    }

    /**
     * Orakel: der Wert VERSION ist Haupt- und Nebenversion, Build und Unter-Build jeweils Big-Endian.
     * Der Build ist ein Handvektor; die Byteordnung des Unter-Builds ist eine Festlegung ohne Messung.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Versionsoption traegt Build und Unter-Build Big-Endian`() {
        assertEquals("07 01 00 01 A0 66", PreloginOption.version(7, 1, 1, 0xA066).data.toHex())
    }

    /**
     * Orakel: die Werte der Option ENCRYPTION sind OFF 0, ON 1, NOT_SUP 2, REQ 3 (MS-TDS 2.2.6.5),
     * die FEDAUTHREQUIRED-Option ist ein Byte.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Verschluesselungswerte und FEDAUTHREQUIRED haben die Werte aus MS-TDS`() {
        assertEquals(0, EncryptionMode.OFF)
        assertEquals(1, EncryptionMode.ON)
        assertEquals(2, EncryptionMode.NOT_SUPPORTED)
        assertEquals(3, EncryptionMode.REQUIRED)
        val message = parsed(hex("06 00 06 00 01 FF 01"))
        assertEquals(true, message.fedAuthRequired)
        assertEquals(false, parsed(hex("06 00 06 00 01 FF 00")).fedAuthRequired)
    }

    /**
     * Roundtrip der Struktur: Lesen und Schreiben der Handvektor-Anfrage ergibt dieselben Bytes
     * (Selbstkonsistenz; das Orakel sind die Tests oben).
     *
     * @spec SPEC-028
     */
    @Test
    fun `Lesen und Schreiben ergeben dieselben Bytes`() {
        val again = assertIs<ProtocolResult.Ok<ByteArray>>(PreloginCodec.encode(parsed(request))).value
        assertEquals(request.toHex(), again.toHex())
    }

    /**
     * Festlegung: eine Option mit unbekannter Kennung bleibt mit ihren Rohdaten erhalten.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Unbekannte Option bleibt erhalten`() {
        val message = parsed(hex("42 00 06 00 02 FF AA BB"))
        assertEquals(0x42, message.options.single().token)
        assertEquals("AA BB", message.options.single().data.toHex())
    }

    /**
     * Negativfall: ohne Terminator `FF` endet die Tabelle am Nachrichtenende; eine leere Nachricht
     * hat gar keine Tabelle.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Tabelle ohne Terminator ist ein Fehler`() {
        assertEquals(ProtocolFailure.MISSING_TERMINATOR, failureOf(hex("00 00 1A 00 06")))
        assertEquals(ProtocolFailure.MISSING_TERMINATOR, failureOf(ByteArray(0)))
        assertEquals(ProtocolFailure.TRUNCATED, failureOf(hex("00 00 1A")))
    }

    /**
     * Negativfall und Grenze: Offset plus Länge darf das Nachrichtenende erreichen (ENCRYPTION auf
     * dem letzten Byte), nicht überschreiten; ein Offset in der Tabelle selbst ist ein Fehler.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Offsets liegen hinter der Tabelle und in der Nachricht`() {
        assertEquals(EncryptionMode.ON, parsed(hex("01 00 06 00 01 FF 01")).encryption)
        assertEquals(ProtocolFailure.INVALID_OFFSET, failureOf(hex("01 00 07 00 01 FF 01")))
        assertEquals(ProtocolFailure.INVALID_OFFSET, failureOf(hex("01 00 05 00 01 FF 01")))
        assertEquals(ProtocolFailure.INVALID_OFFSET, failureOf(hex("01 FF FF 00 01 FF 01")))
        assertEquals(ProtocolFailure.INVALID_OFFSET, failureOf(hex("42 00 06 00 02 FF AA")))
    }

    /**
     * Negativfall: Endet die Nachricht unmittelbar nach einem Byte der Tabelle, das kein Terminator
     * ist (Kennung allein, Kennung mit halbem Offset), fehlen Bytes der Tabelle: `TRUNCATED`, nicht
     * das Ende der Tabelle.
     *
     * @spec SPEC-011
     */
    @Test
    fun `Nachricht endet nach einem Nicht-Terminator-Byte der Tabelle`() {
        listOf("00", "00 00", "00 00 06", "00 00 06 00").forEach { input ->
            val error = assertIs<ProtocolResult.Failed>(PreloginCodec.parse(hex(input))).error
            assertEquals(ProtocolFailure.TRUNCATED, error.failure, input)
            assertEquals(ProtocolErrorCategory.TDS_PROTOCOL_ERROR, error.category, input)
        }
        assertEquals(ProtocolFailure.MISSING_TERMINATOR, failureOf(ByteArray(0)))
    }

    /**
     * Negativfall: bekannte Optionen mit falscher Länge (VERSION 5, ENCRYPTION 2) sind Fehler der
     * Kategorie `TDS_PROTOCOL_ERROR`.
     *
     * @spec SPEC-011
     */
    @Test
    fun `Bekannte Option mit falscher Laenge ist ein Protokollfehler`() {
        val error = assertIs<ProtocolResult.Failed>(PreloginCodec.parse(hex("01 00 06 00 02 FF 01 00"))).error
        assertEquals(ProtocolFailure.INVALID_OPTION_LENGTH, error.failure)
        assertEquals(ProtocolErrorCategory.TDS_PROTOCOL_ERROR, error.category)
        assertEquals(ProtocolFailure.INVALID_OPTION_LENGTH, failureOf(hex("00 00 06 00 05 FF 00 00 00 00 00")))
    }

    /**
     * Grenze beim Schreiben: Offset plus Daten bis `0xFFFF` passen in das 16-Bit-Feld, ein Byte
     * mehr nicht; eine bekannte Option mit falscher Länge wird nicht geschrieben.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Schreiben prueft Groesse und feste Laengen`() {
        val fits = PreloginMessage(listOf(PreloginOption(0x42, ByteArray(65529))))
        assertIs<ProtocolResult.Ok<ByteArray>>(PreloginCodec.encode(fits))
        val tooBig = PreloginMessage(listOf(PreloginOption(0x42, ByteArray(65530))))
        val tooBigFailure = assertIs<ProtocolResult.Failed>(PreloginCodec.encode(tooBig)).error.failure
        assertEquals(ProtocolFailure.TOO_LONG, tooBigFailure)
        val wrong = PreloginMessage(listOf(PreloginOption(PreloginToken.ENCRYPTION, ByteArray(2))))
        assertEquals(
            ProtocolFailure.INVALID_OPTION_LENGTH,
            assertIs<ProtocolResult.Failed>(PreloginCodec.encode(wrong)).error.failure,
        )
    }

    /**
     * Fuzz mit festem Startwert (`SPEC-027`): zufällige Bytes und Handvektor mit verändertem Byte
     * enden in einem Ergebniswert, ein Fehler trägt `TDS_PROTOCOL_ERROR`; was sich lesen lässt,
     * lässt sich schreiben und wieder lesen.
     *
     * @spec SPEC-027
     */
    @Test
    fun `Zufaellige und veraenderte Eingaben enden in einem Ergebniswert`() {
        val random = Random(SEED)
        repeat(RUNS) {
            val noise = random.nextBytes(random.nextInt(0, 60))
            val mutated = request.copyOf()
            repeat(random.nextInt(1, 4)) { mutated[random.nextInt(mutated.size)] = random.nextInt().toByte() }
            listOf(noise, mutated).forEach { input ->
                val result = PreloginCodec.parse(input)
                if (result is ProtocolResult.Failed) {
                    assertEquals(ProtocolErrorCategory.TDS_PROTOCOL_ERROR, result.error.category)
                }
                if (result is ProtocolResult.Ok) {
                    val encoded = assertIs<ProtocolResult.Ok<ByteArray>>(PreloginCodec.encode(result.value)).value
                    assertTrue(parsed(encoded).options == result.value.options)
                }
            }
        }
    }

    private companion object {
        const val SEED = 20261030
        const val RUNS = 3000
    }
}
