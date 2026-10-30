package io.github.pt9912.tdswire.testsupport

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.security.Key
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.Certificate
import java.security.cert.CertificateFactory

/**
 * PKCS#12-Speicher als Bytes für die Startprobe: die Dateien der Dockerfile-Stage `testkeys` und
 * Abwandlungen daraus, die im Test entstehen (anderes Schlüsselkennwort, fremder Schlüssel zur Kette,
 * Schlüssel anderer Art). Die Abwandlungen tauschen nur Schlüssel oder Kennwort; die Ketten bleiben
 * die der Stage.
 */
object KeyVariants {
    const val PASSWORD = "tdswire-test"
    private const val ALIAS = "server"
    private const val EC_BITS = 256
    private const val SMALL_RSA_BITS = 1024

    private fun directory(): String = checkNotNull(System.getProperty("tdswire.testkeys")) { "Systemeigenschaft fehlt" }

    fun password(): CharArray = PASSWORD.toCharArray()

    /** Die Datei [name] der Stage `testkeys` unverändert. */
    fun file(name: String): ByteArray = File(directory(), name).readBytes()

    private fun load(name: String): KeyStore = KeyStore.getInstance("PKCS12").also { store ->
        FileInputStream(File(directory(), name)).use { store.load(it, password()) }
    }

    private fun keyOf(name: String): Key = load(name).getKey(ALIAS, password())

    private fun chainOf(name: String): Array<Certificate> = load(name).getCertificateChain(ALIAS)

    private fun bytesOf(key: Key, keyPassword: CharArray, chain: Array<Certificate>): ByteArray {
        val store = KeyStore.getInstance("PKCS12").also { it.load(null, null) }
        store.setKeyEntry(ALIAS, key, keyPassword, chain)
        return ByteArrayOutputStream().also { store.store(it, password()) }.toByteArray()
    }

    /** Die Kette aus `gueltig.p12` mit dem Schlüssel aus `falscher-name.p12`. */
    fun mismatchedPair(): ByteArray = bytesOf(keyOf(TestKeys.OTHER_NAME), password(), chainOf(TestKeys.VALID))

    /**
     * Ein gültiges Blatt (Schlüssel aus `blatt.p12`), signiert von einem abgelaufenen Zwischenzertifikat,
     * das die gültige Wurzel signiert hat: die Kette `blatt.pem`, `zwischen-abgelaufen.pem`, `wurzel.pem`.
     */
    fun expiredIntermediate(): ByteArray {
        val factory = CertificateFactory.getInstance("X.509")
        val chain = listOf("blatt.pem", "zwischen-abgelaufen.pem", "wurzel.pem").map { name ->
            FileInputStream(File(directory(), name)).use { factory.generateCertificate(it) }
        }
        return bytesOf(keyOf("blatt.p12"), password(), chain.toTypedArray())
    }

    /** Schlüssel und Kette aus `gueltig.p12`, der Schlüssel unter einem anderen Kennwort als der Speicher. */
    fun keyWithOtherPassword(): ByteArray =
        bytesOf(keyOf(TestKeys.VALID), "anderes-kennwort".toCharArray(), chainOf(TestKeys.VALID))

    /** Die Kette aus `gueltig.p12` mit einem frischen EC-Schlüssel. */
    fun ecKey(): ByteArray = bytesOf(freshKey("EC", EC_BITS), password(), chainOf(TestKeys.VALID))

    /** Die Kette aus `gueltig.p12` mit einem frischen RSA-Schlüssel von 1024 Bit. */
    fun smallRsaKey(): ByteArray = bytesOf(freshKey("RSA", SMALL_RSA_BITS), password(), chainOf(TestKeys.VALID))

    /** Ein Speicher ohne Schlüsseleintrag. */
    fun withoutKey(): ByteArray {
        val store = KeyStore.getInstance("PKCS12").also { it.load(null, null) }
        store.setCertificateEntry(ALIAS, chainOf(TestKeys.VALID).first())
        return ByteArrayOutputStream().also { store.store(it, password()) }.toByteArray()
    }

    private fun freshKey(algorithm: String, bits: Int): Key =
        KeyPairGenerator.getInstance(algorithm).also { it.initialize(bits) }.generateKeyPair().private
}
