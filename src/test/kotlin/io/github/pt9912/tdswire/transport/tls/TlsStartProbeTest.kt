package io.github.pt9912.tdswire.transport.tls

import io.github.pt9912.tdswire.testsupport.KeyVariants
import io.github.pt9912.tdswire.testsupport.TestKeys
import io.github.pt9912.tdswire.testsupport.blocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Startprobe der TLS-Konfiguration (`SPEC-043`) gegen die Zertifikate der Dockerfile-Stage
 * `testkeys` und Abwandlungen daraus. Die Probe läuft gegen eine JDK-`SSLEngine` im selben Prozess;
 * die Tests sagen nichts darüber, ob ein Client dem Zertifikat vertraut.
 */
class TlsStartProbeTest {
    private val tls12 = TlsParameters(listOf("TLSv1.2"), TlsParameters.TLS12_SUITES.take(1))
    private val tls13 = TlsParameters(listOf("TLSv1.3"), TlsParameters.TLS13_SUITES.take(1))

    private fun materialOf(store: ByteArray): TlsMaterial {
        val loaded = TlsMaterial.load(store, KeyVariants.password())
        assertIs<MaterialResult.Loaded>(loaded)
        return loaded.material
    }

    private fun rejectionOf(store: ByteArray): TlsDiagnosis {
        val loaded = TlsMaterial.load(store, KeyVariants.password())
        assertIs<MaterialResult.Rejected>(loaded)
        return loaded.diagnosis
    }

    private suspend fun probeOf(store: ByteArray, parameters: TlsParameters = TlsParameters.SPEC_023): ProbeResult =
        TlsStartProbe.run(materialOf(store), parameters)

    private suspend fun failureOf(store: ByteArray): TlsDiagnosis {
        val result = probeOf(store)
        assertIs<ProbeResult.Failed>(result)
        return result.diagnosis
    }

    /**
     * Die Probe nutzt genau die übergebenen Versionen und Suiten, nicht die Voreinstellung: mit nur
     * TLS 1.2 und einer Suite meldet sie diese; mit nur TLS 1.3 die andere Version. Das Zertifikat ist
     * selbstsigniert und trägt einen anderen Namen im zweiten Fall; beides besteht (die Probe prüft
     * weder Vertrauen noch Namen).
     *
     * @spec SPEC-043
     */
    @Test
    fun `probe uses the configured versions and suites and checks neither trust nor name`() = blocking {
        val twelve = probeOf(KeyVariants.file(TestKeys.VALID), tls12)
        assertEquals(ProbeResult.Passed("TLSv1.2", TlsParameters.TLS12_SUITES.first()), twelve)
        val thirteen = probeOf(KeyVariants.file(TestKeys.OTHER_NAME), tls13)
        assertEquals(ProbeResult.Passed("TLSv1.3", TlsParameters.TLS13_SUITES.first()), thirteen)
    }

    /**
     * Eine abgelaufene Kette scheitert als `CERTIFICATE`, auch wenn Schlüssel und Paar stimmen.
     *
     * @spec SPEC-043
     */
    @Test
    fun `expired chain is reported as certificate`() = blocking {
        val diagnosis = failureOf(KeyVariants.file(TestKeys.EXPIRED))
        assertEquals(TlsPart.CERTIFICATE, diagnosis.part)
    }

    /**
     * Die Probe prüft jedes Zertifikat der Kette, nicht nur das erste: ein gültiges Blatt, dessen
     * Zwischenzertifikat abgelaufen ist, scheitert als `CERTIFICATE`. Die Gegenprobe mit gültigem Blatt
     * und gültiger Kette (`gueltig.p12`) besteht.
     *
     * @spec SPEC-043
     */
    @Test
    fun `every certificate of the chain must be valid`() = blocking {
        assertIs<ProbeResult.Passed>(probeOf(KeyVariants.file(TestKeys.VALID)))
        val diagnosis = failureOf(KeyVariants.expiredIntermediate())
        assertEquals(TlsPart.CERTIFICATE, diagnosis.part)
    }

    /**
     * Ein Speicher, der sich nicht laden lässt, und einer ohne Schlüsseleintrag: unlesbar ist
     * `CERTIFICATE` (Festlegung: beschädigter Speicher und falsches Speicher-Kennwort sind nicht
     * unterscheidbar), kein Schlüssel ist `PRIVATE_KEY`.
     *
     * @spec SPEC-043
     */
    @Test
    fun `unreadable store is certificate and a store without key is private key`() {
        assertEquals(TlsPart.CERTIFICATE, rejectionOf(ByteArray(64) { it.toByte() }).part)
        assertEquals(TlsPart.CERTIFICATE, rejectionOf(ByteArray(0)).part)
        assertEquals(TlsPart.PRIVATE_KEY, rejectionOf(KeyVariants.withoutKey()).part)
    }

    /**
     * Ein Schlüssel unter einem anderen Kennwort (nicht entschlüsselbar), ein EC-Schlüssel und ein
     * RSA-Schlüssel unter 2048 Bit sind `PRIVATE_KEY`; die beiden letzten nennen ihre Form.
     *
     * @spec SPEC-043
     */
    @Test
    fun `encrypted or disallowed key is reported as private key`() {
        assertEquals(TlsPart.PRIVATE_KEY, rejectionOf(KeyVariants.keyWithOtherPassword()).part)
        assertEquals("Form nicht zugelassen: EC", rejectionOf(KeyVariants.ecKey()).form)
        assertEquals("Form nicht zugelassen: RSA 1024 Bit", rejectionOf(KeyVariants.smallRsaKey()).form)
        assertEquals(TlsPart.PRIVATE_KEY, rejectionOf(KeyVariants.ecKey()).part)
    }

    /**
     * Gehört der Schlüssel nicht zur Kette, lädt das Material, und die Probe scheitert als `PAIR`.
     *
     * @spec SPEC-043
     */
    @Test
    fun `mismatched key and chain is reported as pair`() = blocking {
        assertEquals(TlsPart.PAIR, failureOf(KeyVariants.mismatchedPair()).part)
    }

    /**
     * Keine Diagnose und kein Text des Materials enthält Dateinamen, Speicher-Kennwort, Namen aus dem
     * Zertifikat oder PEM-Rahmen; der Pfad des Verzeichnisses steht in keiner Diagnose.
     *
     * @spec SPEC-043
     */
    @Test
    fun `diagnoses name neither paths nor content`() = blocking {
        val stores = listOf(
            KeyVariants.file(TestKeys.EXPIRED),
            KeyVariants.mismatchedPair(),
            ByteArray(64) { it.toByte() },
            KeyVariants.ecKey(),
            KeyVariants.keyWithOtherPassword(),
            KeyVariants.withoutKey(),
        )
        val texts = stores.map { store ->
            val loaded = TlsMaterial.load(store, KeyVariants.password())
            if (loaded is MaterialResult.Rejected) loaded.toString() else failureOf(store).toString() + loaded
        }
        val directory = checkNotNull(System.getProperty("tdswire.testkeys"))
        val forbidden = listOf(".p12", KeyVariants.PASSWORD, "CN=", "localhost", "andere.invalid", "BEGIN", directory)
        texts.forEach { text ->
            forbidden.forEach { assertTrue(it !in text, "Text enthält $it: $text") }
        }
    }
}
