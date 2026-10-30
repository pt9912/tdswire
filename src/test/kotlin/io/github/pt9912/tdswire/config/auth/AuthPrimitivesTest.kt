package io.github.pt9912.tdswire.config.auth

import java.text.Normalizer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AuthPrimitivesTest {
    private fun hexBytes(text: String): ByteArray = text.split(' ').map { it.toInt(16).toByte() }.toByteArray()

    private fun derive(password: String, iterations: Int = 1000, salt: ByteArray = testSalt, bytes: Int = 32) =
        JdkAuthPrimitives.derive(password.toCharArray(), salt, iterations, bytes)

    /**
     * Orakel: RFC 7914 §11, Testvektor für PBKDF2-HMAC-SHA-256 (`passwd`, `salt`, c = 1, 64 Byte); hier die
     * ersten 32 Byte. Der Erwartungswert stammt aus dem RFC, nicht aus dem JDK.
     *
     * @spec SPEC-051
     */
    @Test
    fun `JDK-Ableitung stimmt mit dem RFC-7914-Vektor ueberein`() {
        val expected = hexBytes(
            "55 ac 04 6e 56 e3 08 9f ec 16 91 c2 25 44 b6 05 f9 41 85 21 6d de 04 65 e6 8b 9d 57 c2 0d ac bc",
        )
        assertContentEquals(expected, derive("passwd", 1, "salt".toByteArray()))
        assertContentEquals(expected, oraclePbkdf2("passwd".toByteArray(), "salt".toByteArray(), 1, 32))
    }

    /**
     * Orakel: RFC 7914 §11 (`Password`, `NaCl`, c = 80000), die ersten 32 Byte.
     *
     * @spec SPEC-051
     */
    @Test
    fun `JDK-Ableitung stimmt mit dem zweiten RFC-7914-Vektor ueberein`() {
        val expected = hexBytes(
            "4d dc d8 f6 0b 98 be 21 83 0c ee 5e f2 27 01 f9 64 1a 44 18 d0 4c 04 14 ae ff 08 87 6b 34 ab 56",
        )
        assertContentEquals(expected, derive("Password", 80_000, "NaCl".toByteArray()))
    }

    /**
     * Orakel: zweite Implementierung (`oraclePbkdf2` aus `Mac`) rechnet dieselben Eingaben, darunter Umlaut,
     * Surrogatpaar und leeres Passwort; UTF-8 ist die Kodierung der Ableitung (ADR-0011, M1-9).
     *
     * @spec SPEC-051
     */
    @Test
    fun `JDK-Ableitung stimmt mit dem unabhaengigen Orakel ueberein`() {
        listOf("", "a", "Pässwörd", "😀x", "x".repeat(128)).forEach {
            assertContentEquals(
                oraclePbkdf2(it.toByteArray(Charsets.UTF_8), testSalt, 1000, 32),
                derive(it),
                "Passwort der Länge ${it.length}",
            )
        }
    }

    /**
     * Orakel: ADR-0011 dokumentiert als Eigenschaft (M1-10): ein alleinstehendes Hochsurrogat ergibt denselben
     * Hash wie `?`, NFC und NFD ergeben verschiedene Hashes, ein leeres Passwort wird abgeleitet.
     *
     * @spec SPEC-051
     */
    @Test
    fun `Randfaelle der Ableitung sind die dokumentierten Eigenschaften`() {
        assertContentEquals(derive("?"), derive("\uD800"))
        assertContentEquals(derive("a?b"), derive("a\uDC00b"))
        val nfc = Normalizer.normalize("ä", Normalizer.Form.NFC)
        val nfd = Normalizer.normalize("ä", Normalizer.Form.NFD)
        assertNotEquals(nfc, nfd)
        assertFalse(derive(nfc).contentEquals(derive(nfd)))
        assertEquals(32, derive("").size)
    }

    /**
     * Orakel: Der Vergleich untersucht jede Position bis zur längeren Länge, unabhängig davon, wo der erste
     * Unterschied liegt (Strukturbeleg, keine Zeitaussage). Ergebnis gleich genau dann, wenn Länge und Inhalt
     * gleich sind.
     *
     * @spec SPEC-051
     */
    @Test
    fun `Vergleich bricht nicht beim ersten Unterschied ab`() {
        val base = ByteArray(32) { it.toByte() }
        val differsFirst = base.copyOf().also { it[0] = 99 }
        val differsLast = base.copyOf().also { it[31] = 99 }
        val equal = JdkAuthPrimitives.compare(base, base.copyOf())
        val first = JdkAuthPrimitives.compare(base, differsFirst)
        val last = JdkAuthPrimitives.compare(base, differsLast)
        assertTrue(equal.equal)
        assertFalse(first.equal)
        assertFalse(last.equal)
        assertEquals(listOf(32, 32, 32), listOf(equal.steps, first.steps, last.steps))
    }

    /**
     * Orakel: Ein Präfix ist nicht gleich der längeren Folge (auch nicht, wenn der Überhang nur Nullen trägt);
     * die Schritte sind die längere Länge; zwei leere Folgen sind gleich.
     *
     * @spec SPEC-051
     */
    @Test
    fun `Vergleich unterscheidet Laengen`() {
        val short = byteArrayOf(1, 2)
        val padded = byteArrayOf(1, 2, 0, 0)
        val prefix = JdkAuthPrimitives.compare(short, padded)
        assertFalse(prefix.equal)
        assertEquals(4, prefix.steps)
        assertFalse(JdkAuthPrimitives.compare(padded, short).equal)
        assertFalse(JdkAuthPrimitives.compare(ByteArray(0), byteArrayOf(0)).equal)
        assertTrue(JdkAuthPrimitives.compare(ByteArray(0), ByteArray(0)).equal)
    }
}
