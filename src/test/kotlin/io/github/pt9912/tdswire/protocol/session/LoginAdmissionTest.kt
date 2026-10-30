package io.github.pt9912.tdswire.protocol.session

import io.github.pt9912.tdswire.config.auth.NegotiatedEncryption
import io.github.pt9912.tdswire.protocol.login7.Login7Parser
import io.github.pt9912.tdswire.protocol.login7.SecretChars
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import io.github.pt9912.tdswire.testsupport.Login7Builder
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LoginAdmissionTest {
    private fun admit(configure: Login7Builder.() -> Unit): Admission =
        LoginAdmission.admit(SessionVectors.login7Payload(configure), NegotiatedEncryption.REQUIRED)

    private fun refusal(configure: Login7Builder.() -> Unit): LoginRejection =
        assertIs<Admission.Refused>(admit(configure)).rejection

    /**
     * `fIntSecurity` (OptionFlags2 Bit 7) mit SSPI-Block: abgelehnt, ohne dass ein Adapteraufruf vorbereitet wird.
     *
     * @spec SPEC-012
     */
    @Test
    fun `fIntSecurity wird abgelehnt`() {
        val reason = refusal {
            optionFlags2 = 0x03 or 0x80
            sspi = ByteArray(8) { it.toByte() }
        }
        assertEquals(LoginRejection.UNSUPPORTED_INTEGRATED_SECURITY, reason)
    }

    /**
     * FeatureExt-Option `0x02` (Entra-ID) bei gesetztem `fExtension`: abgelehnt.
     *
     * @spec SPEC-012
     */
    @Test
    fun `FeatureExt 0x02 wird abgelehnt`() {
        val reason = refusal {
            optionFlags3 = 0x10
            features = listOf(0x02 to byteArrayOf(1, 0, 0, 0, 0))
        }
        assertEquals(LoginRejection.UNSUPPORTED_FEDERATED_AUTHENTICATION, reason)
    }

    /**
     * Eine andere FeatureExt-Option allein ist kein nicht unterstützter Modus.
     *
     * @spec SPEC-012
     */
    @Test
    fun `Eine andere FeatureExt-Option wird nicht abgelehnt`() {
        assertIs<Admission.Admitted>(
            admit {
                optionFlags3 = 0x10
                features = listOf(0x05 to byteArrayOf(1))
            },
        )
    }

    /**
     * `fChangePassword` (OptionFlags3 Bit 0) mit neuem Passwort: abgelehnt.
     *
     * @spec SPEC-012
     */
    @Test
    fun `fChangePassword wird abgelehnt`() {
        val reason = refusal {
            optionFlags3 = 0x01
            changePassword = "neu".toCharArray()
        }
        assertEquals(LoginRejection.UNSUPPORTED_PASSWORD_CHANGE, reason)
    }

    /**
     * Genau 128 UTF-16-Einheiten bei Kennung und Passwort sind zulässig, 129 werden abgelehnt
     * (`spec/spezifikation.md` `SPEC-051`); die Grenze gilt je Feld.
     *
     * @spec SPEC-012
     */
    @Test
    fun `Eingaben ueber 128 Einheiten werden abgelehnt, 128 nicht`() {
        val max = "a".repeat(128)
        val over = "a".repeat(129)
        assertIs<Admission.Admitted>(
            admit {
                userName = max
                password = max.toCharArray()
            },
        )
        assertEquals(LoginRejection.INPUT_TOO_LONG, refusal { userName = over })
        assertEquals(LoginRejection.INPUT_TOO_LONG, refusal { password = over.toCharArray() })
    }

    /**
     * Der Datenbankname darf höchstens 128 Einheiten haben, damit die Antwort ihn im ENVCHANGE tragen kann.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Ein Datenbankname ueber 128 Einheiten wird abgelehnt`() {
        assertIs<Admission.Admitted>(admit { database = "d".repeat(128) })
        assertEquals(LoginRejection.INPUT_TOO_LONG, refusal { database = "d".repeat(129) })
    }

    /**
     * Ungültige Offsets und Längen ergeben ein kontrolliertes Urteil `Malformed`, keine Ausnahme.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Ungueltige Offsets und Laengen sind Malformed`() {
        val valid = SessionVectors.login7Payload()
        val badOffset = valid.copyOf().also {
            it[PASSWORD_OFFSET] = 0xF0.toByte()
            it[PASSWORD_OFFSET + 1] = 0xFF.toByte()
        }
        val badLength = valid.copyOf().also {
            it[PASSWORD_OFFSET + 2] = 0xFF.toByte()
            it[PASSWORD_OFFSET + 3] = 0x7F
        }
        listOf(badOffset, badLength, ByteArray(0), ByteArray(Login7Parser.FIXED_BYTES)).forEach { payload ->
            assertIs<Admission.Malformed>(LoginAdmission.admit(payload, NegotiatedEncryption.REQUIRED))
        }
    }

    /**
     * Jedes Präfix eines gültigen LOGIN7 und zufällige Bytes ergeben ein Urteil, nie eine Ausnahme
     * (Festlegung: Stichprobe mit festem Startwert, kein Fuzz-Gate).
     *
     * @spec SPEC-028
     */
    @Test
    fun `Praefixe und Zufallsbytes fuehren zu keinem Absturz`() {
        val valid = SessionVectors.login7Payload()
        for (length in valid.indices) {
            assertIs<Admission.Malformed>(
                LoginAdmission.admit(valid.copyOf(length), NegotiatedEncryption.REQUIRED),
                "Praefix $length",
            )
        }
        val random = Random(SEED)
        repeat(RUNS) {
            val garbage = ByteArray(random.nextInt(0, MAX_GARBAGE)) { random.nextInt().toByte() }
            LoginAdmission.admit(garbage, NegotiatedEncryption.REQUIRED)
        }
    }

    /**
     * Das Passwort und das neue Passwort des geparsten LOGIN7 sind nach dem Urteil überschrieben, bei
     * Ablehnung wie bei Zulassung; das Passwort des Adapters trägt den Inhalt.
     *
     * @spec SPEC-012
     */
    @Test
    fun `Die Passwoerter des LOGIN7 sind nach dem Urteil ueberschrieben`() {
        fun parsed(configure: Login7Builder.() -> Unit) =
            (Login7Parser.parse(SessionVectors.login7Payload(configure)) as ProtocolResult.Ok).value

        val admitted = parsed { password = "geheim".toCharArray() }
        val result = LoginAdmission.decide(admitted, NegotiatedEncryption.REQUIRED)
        assertEquals("geheim", String(assertIs<Admission.Admitted>(result).request.password.reveal()))
        assertWiped(admitted.password)
        assertWiped(admitted.newPassword)

        val refused = parsed {
            optionFlags3 = 0x01
            password = "alt".toCharArray()
            changePassword = "neu".toCharArray()
        }
        assertIs<Admission.Refused>(LoginAdmission.decide(refused, NegotiatedEncryption.REQUIRED))
        assertWiped(refused.password)
        assertWiped(refused.newPassword)
    }

    private fun assertWiped(secret: SecretChars) {
        assertTrue(secret.reveal().all { it == '\u0000' }, "Feld nicht ueberschrieben")
    }

    /**
     * Die Abbildung `SecretChars` nach `PasswordChars` überschreibt das Quellfeld und das Zwischenfeld.
     *
     * @spec SPEC-012
     */
    @Test
    fun `Die Abbildung ueberschreibt Quellfeld und Zwischenfeld`() {
        val secret = SecretChars("pw".toCharArray())
        val mapped = PasswordMapping.fromSecret(secret)
        assertEquals("pw", String(mapped.reveal()))
        assertWiped(secret)

        val intermediate = "zw".toCharArray()
        val adopted = PasswordMapping.adopt(intermediate)
        assertEquals("zw", String(adopted.reveal()))
        assertTrue(intermediate.all { it == '\u0000' }, "Zwischenfeld nicht ueberschrieben")
    }

    private companion object {
        const val PASSWORD_OFFSET = 44
        const val SEED = 20_261_030
        const val RUNS = 500
        const val MAX_GARBAGE = 300
    }
}
