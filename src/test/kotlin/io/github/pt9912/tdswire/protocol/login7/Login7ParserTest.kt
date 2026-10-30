package io.github.pt9912.tdswire.protocol.login7

import io.github.pt9912.tdswire.protocol.wire.ProtocolErrorCategory
import io.github.pt9912.tdswire.protocol.wire.ProtocolFailure
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import io.github.pt9912.tdswire.testsupport.Login7Builder
import io.github.pt9912.tdswire.testsupport.hex
import io.github.pt9912.tdswire.testsupport.toHex
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class Login7ParserTest {
    /**
     * Handvektor nach MS-TDS 2.2.6.4: 110 Byte. Kopf 36 Byte (Länge `6E`, TDSVersion `74000004`
     * als `04 00 00 74`, Paketgröße 8000 = `1F40`), 14 Offset-/Längen-Paare bis Byte 94, dann die
     * Felder ab `0x5E`: HostName "H", UserName "sa", Password "a" (verschleiert `B3 A5`), AppName
     * "A", ServerName "S", CltIntName "C", Database "d".
     */
    private val vector = hex(
        "6E 00 00 00  04 00 00 74  40 1F 00 00  00 00 00 07  34 12 00 00  00 00 00 00 " +
            "E0 03 00 00  00 00 00 00  09 04 00 00 " +
            "5E 00 01 00  60 00 02 00  64 00 01 00  66 00 01 00  68 00 01 00  00 00 00 00 " +
            "6A 00 01 00  6C 00 00 00  6C 00 01 00  00 00 00 00 00 00 " +
            "6E 00 00 00  6E 00 00 00  6E 00 00 00  00 00 00 00 " +
            "48 00  73 00 61 00  B3 A5  41 00  53 00  43 00  64 00",
    )

    private fun parse(bytes: ByteArray): ProtocolResult<Login7Request> = Login7Parser.parse(bytes)

    private fun ok(bytes: ByteArray): Login7Request = assertIs<ProtocolResult.Ok<Login7Request>>(parse(bytes)).value

    private fun assertFailureIsProtocolError(result: ProtocolResult<*>) {
        if (result is ProtocolResult.Failed) {
            assertEquals(ProtocolErrorCategory.TDS_PROTOCOL_ERROR, result.error.category)
        }
    }

    private fun failureOf(bytes: ByteArray): ProtocolFailure =
        assertIs<ProtocolResult.Failed>(parse(bytes)).error.failure

    private fun patched(bytes: ByteArray, at: Int, replacement: String): ByteArray {
        val copy = bytes.copyOf()
        hex(replacement).copyInto(copy, at)
        return copy
    }

    /**
     * Orakel: Felder des Handvektors; die Felder stehen dort, wo die Offset-Tabelle sie nennt.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Handvektor nach MS-TDS wird gelesen`() {
        val request = ok(vector)
        assertEquals(110, vector.size)
        assertEquals(0x74000004, request.header.tdsVersion)
        assertEquals(8000, request.header.packetSize)
        assertEquals(0xE0, request.header.optionFlags1)
        assertEquals(0x0409, request.header.clientLcid)
        val fields = request.fields
        assertEquals(
            listOf("H", "sa", "A", "S", "C", "", "d", ""),
            listOf(
                fields.hostName,
                fields.userName,
                fields.appName,
                fields.serverName,
                fields.cltIntName,
                fields.language,
                fields.database,
                fields.attachDbFile,
            ),
        )
        assertEquals("a", String(request.password.reveal()))
        assertEquals(0, request.newPassword.length)
        assertFalse(request.integratedSecurity)
        assertFalse(request.changePassword)
        assertFalse(request.hasFeatureExtension)
        assertEquals(0L, request.sspiLength)
        assertEquals(emptyList(), request.featureIds)
    }

    /**
     * Orakel: Entschleierung je Byte (erst XOR `A5`, dann Nibble-Tausch) für "ab" (`B3 A5 83 A5`),
     * "€" (`6F A7`) und ein Surrogatpaar (`76 28 A5 48`); Werte von Hand gerechnet.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Passwort wird entschleiert`() {
        assertEquals("ab", String(ok(withWire("B3 A5 83 A5")).password.reveal()))
        assertEquals("\u20AC", String(ok(withWire("6F A7")).password.reveal()))
        assertEquals("\uD83D\uDE00", String(ok(withWire("76 28 A5 48")).password.reveal()))
    }

    private fun withWire(wireHex: String): ByteArray = Login7Builder().apply { passwordWire = hex(wireHex) }.build()

    /**
     * Festlegung und Grenze: die UTF-16-Einheiten des Passworts bleiben unverändert, auch ein
     * unpaariges Surrogat und die Länge 128 (`ADR-0009`: 0 bis 128 Einheiten, auch leer).
     *
     * @spec SPEC-028
     */
    @Test
    fun `Passwort behaelt UTF-16-Einheiten und Laengen`() {
        val lone = Login7Builder().apply { password = charArrayOf('\uD800', 'x') }
        assertContentEquals(charArrayOf('\uD800', 'x'), ok(lone.build()).password.reveal())
        val long = Login7Builder().apply { password = CharArray(128) { 'p' } }
        assertEquals(128, ok(long.build()).password.length)
        val empty = Login7Builder().apply { password = CharArray(0) }
        assertEquals(0, ok(empty.build()).password.length)
    }

    /**
     * Zusage `TDS-QA-03`: weder Passwort noch neues Passwort noch Kennung erscheinen in einer
     * Textdarstellung des Parse-Ergebnisses.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Keine Textdarstellung enthaelt Passwort oder Kennung`() {
        val builder = Login7Builder().apply {
            userName = "alice-kennung"
            password = "Geheim-123".toCharArray()
            changePassword = "Neu-456".toCharArray()
            optionFlags3 = Login7Request.FLAG_CHANGE_PASSWORD
        }
        val request = ok(builder.build())
        val texts = listOf(
            request.toString(),
            request.header.toString(),
            request.fields.toString(),
            request.password.toString(),
            request.newPassword.toString(),
            "$request ${request.fields} ${request.password} ${request.newPassword}",
        )
        texts.forEach { text ->
            listOf("Geheim", "Geheim-123", "Neu-456", "alice-kennung", "kennung").forEach { secret ->
                assertFalse(text.contains(secret), "'$secret' in '$text'")
            }
        }
        assertEquals("Geheim-123", String(request.password.reveal()))
        assertEquals("Neu-456", String(request.newPassword.reveal()))
    }

    /**
     * Die Kopie aus `reveal` ist unabhängig; `wipe` überschreibt das gehaltene Feld.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Geheimnis gibt Kopien und laesst sich ueberschreiben`() {
        val secret = ok(vector).password
        secret.reveal()[0] = 'z'
        assertEquals("a", String(secret.reveal()))
        secret.wipe()
        assertContentEquals(charArrayOf('\u0000'), secret.reveal())
    }

    /**
     * Orakel: `fIntSecurity` ist OptionFlags2 Bit 7 (`0x80`); der SSPI-Block wird gemessen, mit
     * `cbSSPI = 0xFFFF` aus `cbSSPILong`.
     *
     * @spec SPEC-028
     */
    @Test
    fun `fIntSecurity und SSPI-Block werden erkannt`() {
        val short = Login7Builder().apply {
            optionFlags2 = 0x83
            sspi = ByteArray(5) { 1 }
        }
        val shortRequest = ok(short.build())
        assertTrue(shortRequest.integratedSecurity)
        assertEquals(5L, shortRequest.sspiLength)
        val long = Login7Builder().apply {
            optionFlags2 = 0x83
            sspi = ByteArray(770)
            sspiShortMarker = true
        }
        assertEquals(770L, ok(long.build()).sspiLength)
        assertFalse(ok(Login7Builder().build()).integratedSecurity)
    }

    /**
     * Orakel: `fChangePassword` ist OptionFlags3 Bit 0, das neue Passwort steht im eigenen Feld,
     * das alte im Passwortfeld (`ADR-0009`); `fReadOnlyIntent` ist TypeFlags Bit 5.
     *
     * @spec SPEC-028
     */
    @Test
    fun `fChangePassword und neues Passwort`() {
        val builder = Login7Builder().apply {
            optionFlags3 = 0x01
            typeFlags = 0x20
            password = "alt".toCharArray()
            changePassword = "neu".toCharArray()
        }
        val request = ok(builder.build())
        assertTrue(request.changePassword)
        assertTrue(request.readOnlyIntent)
        assertEquals("alt", String(request.password.reveal()))
        assertEquals("neu", String(request.newPassword.reveal()))
    }

    /**
     * Orakel: FeatureExt hängt an `fExtension` (OptionFlags3 Bit 4); die Option `0x02` (föderierte
     * Authentifizierung) kommt mit leerer Kennung und leerem Passwort (`ADR-0009`). Die Reihenfolge
     * der Kennungen ist die der Nachricht.
     *
     * @spec SPEC-028
     */
    @Test
    fun `FeatureExt-Option 0x02 wird erkannt`() {
        val builder = Login7Builder().apply {
            optionFlags3 = 0x10
            userName = ""
            password = CharArray(0)
            features = listOf(0x01 to ByteArray(1), 0x02 to ByteArray(7) { 9 })
        }
        val request = ok(builder.build())
        assertTrue(request.hasFeatureExtension)
        assertTrue(request.federatedAuthentication)
        assertEquals(listOf(1, 2), request.featureIds)
        val plain = Login7Builder().apply {
            optionFlags3 = 0x10
            features = listOf(0x01 to ByteArray(0))
        }
        val without = ok(plain.build())
        assertFalse(without.federatedAuthentication)
        assertEquals(listOf(1), without.featureIds)
    }

    /**
     * Festlegung: ohne `fExtension` wird die FeatureExt-Tabelle nicht gelesen, auch wenn die
     * Nachricht Bytes dafür trägt.
     *
     * @spec SPEC-028
     */
    @Test
    fun `FeatureExt ohne Flag wird nicht gelesen`() {
        val builder = Login7Builder().apply { features = listOf(0x02 to ByteArray(0)) }
        assertEquals(emptyList(), ok(builder.build()).featureIds)
    }

    /**
     * Negativfall: jede Länge unter 94 Byte (auch 0) ist ein Ergebniswert `TRUNCATED`, nie eine Ausnahme.
     *
     * @spec SPEC-027
     */
    @Test
    fun `Zu kurze Nachricht ist ein Ergebniswert`() {
        for (size in 0 until Login7Parser.FIXED_BYTES) {
            assertEquals(ProtocolFailure.TRUNCATED, failureOf(vector.copyOf(size)))
        }
    }

    /**
     * Negativfall: das Längenfeld muss die Nachrichtenlänge nennen (110): 109 und 111 sind Fehler.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Laengenfeld muss zur Nachricht passen`() {
        assertEquals(ProtocolFailure.INVALID_LENGTH, failureOf(patched(vector, 0, "6D 00 00 00")))
        assertEquals(ProtocolFailure.INVALID_LENGTH, failureOf(patched(vector, 0, "6F 00 00 00")))
        assertEquals(ProtocolFailure.INVALID_LENGTH, failureOf(patched(vector, 0, "6E 00 00 80")))
        assertEquals(ProtocolFailure.INVALID_LENGTH, failureOf(vector.copyOf(vector.size - 1)))
    }

    /**
     * Grenze: ein Feld darf genau am Nachrichtenende enden (Database "d" bei Offset 108, Länge 1),
     * ein Zeichen mehr ragt hinaus; ein Offset im festen Kopf (93) oder über der Nachricht ist ein Fehler.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Felder liegen hinter dem Kopf und in der Nachricht`() {
        assertEquals("d", ok(vector).fields.database)
        assertEquals(ProtocolFailure.INVALID_OFFSET, failureOf(patched(vector, 70, "02 00")))
        assertEquals(ProtocolFailure.INVALID_OFFSET, failureOf(patched(vector, 36, "5D 00")))
        assertEquals(ProtocolFailure.INVALID_OFFSET, failureOf(patched(vector, 36, "FF FF")))
        assertEquals(ProtocolFailure.INVALID_OFFSET, failureOf(patched(vector, 46, "FF FF")))
    }

    /**
     * Festlegung: ein Feld der Länge 0 wird nicht an seinem Offset geprüft.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Leeres Feld mit beliebigem Offset ist zulaessig`() {
        val request = ok(patched(vector, 36, "FF FF 00 00"))
        assertEquals("", request.fields.hostName)
    }

    /**
     * Negativfall: Fehler der FeatureExt-Tabelle: Zeiger nicht 4 Byte lang, Tabelle außerhalb,
     * Feature länger als der Rest, Terminator fehlt.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Fehlerhafte FeatureExt-Tabelle ist ein Ergebniswert`() {
        val good = Login7Builder().apply {
            optionFlags3 = 0x10
            features = listOf(0x02 to ByteArray(3))
        }.build()
        ok(good)
        val extPair = 36 + 4 * 5
        assertEquals(ProtocolFailure.INVALID_LENGTH, failureOf(patched(good, extPair + 2, "03 00")))
        assertEquals(ProtocolFailure.INVALID_OFFSET, failureOf(patched(good, extPair, "FF FF")))
        val pointerAt = (good[extPair].toInt() and 0xFF) or ((good[extPair + 1].toInt() and 0xFF) shl 8)
        assertEquals(ProtocolFailure.INVALID_OFFSET, failureOf(patched(good, pointerAt, "01 00 00 00")))
        assertEquals(ProtocolFailure.INVALID_OFFSET, failureOf(patched(good, pointerAt, "FF 00 00 00")))
        val tableAt = pointerAt + 4
        assertEquals(ProtocolFailure.INVALID_LENGTH, failureOf(patched(good, tableAt + 1, "FF 00 00 00")))
        assertEquals(ProtocolFailure.TRUNCATED, failureOf(patched(good, good.size - 1, "00")))
    }

    /**
     * Negativfall: ein SSPI-Block, der aus der Nachricht ragt, ist ein Fehler; `cbSSPILong` zählt
     * als 32-Bit-Zahl ohne Vorzeichen.
     *
     * @spec SPEC-028
     */
    @Test
    fun `SSPI-Block ausserhalb der Nachricht ist ein Fehler`() {
        assertEquals(ProtocolFailure.INVALID_OFFSET, failureOf(patched(vector, 80, "01 00")))
        val marker = patched(patched(vector, 80, "FF FF"), 90, "FF FF FF FF")
        assertEquals(ProtocolFailure.INVALID_OFFSET, failureOf(marker))
    }

    /**
     * Fuzz mit festem Startwert (`SPEC-027`): zufällige Bytes, Handvektor mit verändertem Byte und
     * mit verändertem Längenfeld enden in einem Ergebniswert, nie in einer Ausnahme; ein Fehler trägt
     * `TDS_PROTOCOL_ERROR`.
     *
     * @spec SPEC-027
     */
    @Test
    fun `Zufaellige und veraenderte Eingaben enden in einem Ergebniswert`() {
        val random = Random(SEED)
        val featured = Login7Builder().apply {
            optionFlags3 = 0x11
            features = listOf(0x02 to ByteArray(3))
            sspi = ByteArray(4)
        }.build()
        repeat(RUNS) {
            val noise = random.nextBytes(random.nextInt(0, 300))
            listOf(vector, featured).forEach { base ->
                val mutated = base.copyOf()
                repeat(random.nextInt(1, 4)) { mutated[random.nextInt(mutated.size)] = random.nextInt().toByte() }
                assertFailureIsProtocolError(parse(mutated))
                assertFailureIsProtocolError(parse(base.copyOf(random.nextInt(0, base.size))))
            }
            assertFailureIsProtocolError(parse(noise))
        }
        assertEquals("a", String(ok(vector).password.reveal()))
        assertEquals("B3 A5", vector.copyOfRange(100, 102).toHex())
    }

    private companion object {
        const val SEED = 20261030
        const val RUNS = 4000
    }
}
