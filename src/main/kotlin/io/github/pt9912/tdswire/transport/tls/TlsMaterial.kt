package io.github.pt9912.tdswire.transport.tls

import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.Key
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPrivateKey
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

/** Der Teil der TLS-Konfiguration, den die Startprobe als fehlerhaft meldet (`SPEC-043`). */
enum class TlsPart {
    /** Kette unlesbar oder jetzt nicht gültig. */
    CERTIFICATE,

    /** Schlüssel unlesbar, verschlüsselt oder in einer nicht zugelassenen Form. */
    PRIVATE_KEY,

    /** Der Schlüssel gehört nicht zum Zertifikat, oder der Handschlag scheitert aus anderem Grund. */
    PAIR,
}

/**
 * Diagnose der Startprobe: der fehlerhafte [part] und die gefundene [form] als feste Beschreibung
 * oder Ausnahmetyp. [form] enthält nie einen Dateipfad, nie Zertifikats- oder Schlüsselinhalt und nie
 * den Text einer Ausnahme (`SPEC-043`).
 */
data class TlsDiagnosis(val part: TlsPart, val form: String)

/** Ausgang des Ladens von [TlsMaterial]. */
sealed interface MaterialResult {
    class Loaded(val material: TlsMaterial) : MaterialResult {
        override fun toString(): String = "Loaded(verborgen)"
    }

    data class Rejected(val diagnosis: TlsDiagnosis) : MaterialResult
}

/**
 * Schlüssel und Kette des Servers als fertiger `SSLContext`. Das Material kommt aus den Bytes eines
 * PKCS#12-Speichers (Setzung, nicht gemessen: `contracts/security-profile.md` §Schlüsselmaterial); den
 * Pfad kennt die Klasse nicht. Speicher- und Schlüsselkennwort sind derselbe Wert.
 *
 * Zugelassen ist der erste Schlüsseleintrag mit einem RSA-Schlüssel von mindestens [MIN_RSA_BITS] Bit
 * (Festlegung: ECDSA ist nicht gemessen, die Grenze 2048 ist gesetzt, nicht gemessen). Die Klasse hält
 * Schlüssel und Kette nur im `SSLContext`; ihre Darstellung als Text nennt nichts davon.
 */
class TlsMaterial private constructor(internal val context: SSLContext, internal val chain: List<X509Certificate>) {
    override fun toString(): String = "TlsMaterial(verborgen)"

    companion object {
        const val MIN_RSA_BITS = 2048
        private const val ALIAS = "server"

        /**
         * Lädt Schlüssel und Kette aus [store] mit dem Kennwort [password]. Ein nicht ladbarer Speicher
         * (beschädigt oder falsches Speicher-Kennwort, nicht unterscheidbar) und eine unbrauchbare Kette
         * sind [TlsPart.CERTIFICATE]; ein fehlender, nicht entschlüsselbarer (etwa anderes Kennwort des
         * Schlüssels) oder nicht zugelassener Schlüssel ist [TlsPart.PRIVATE_KEY].
         */
        fun load(store: ByteArray, password: CharArray): MaterialResult =
            when (val step = open(store, password).then { entryOf(it, password) }) {
                is Step.Stop -> MaterialResult.Rejected(step.diagnosis)
                is Step.Next -> MaterialResult.Loaded(build(step.value, password))
            }

        private fun open(store: ByteArray, password: CharArray): Step<KeyStore> = try {
            Step.Next(KeyStore.getInstance("PKCS12").also { it.load(ByteArrayInputStream(store), password) })
        } catch (broken: IOException) {
            stop(TlsPart.CERTIFICATE, "Speicher nicht lesbar: ${broken.javaClass.simpleName}")
        } catch (broken: GeneralSecurityException) {
            stop(TlsPart.CERTIFICATE, "Speicher nicht lesbar: ${broken.javaClass.simpleName}")
        }

        private fun entryOf(keyStore: KeyStore, password: CharArray): Step<Entry> = aliasOf(keyStore).then { alias ->
            chainOf(keyStore, alias).then { chain ->
                keyOf(keyStore, alias, password).then { checked(Entry(it, chain)) }
            }
        }

        private fun aliasOf(keyStore: KeyStore): Step<String> {
            val alias = keyStore.aliases().asSequence().firstOrNull { keyStore.isKeyEntry(it) }
            return if (alias == null) stop(TlsPart.PRIVATE_KEY, "kein Schluesseleintrag") else Step.Next(alias)
        }

        private fun chainOf(keyStore: KeyStore, alias: String): Step<List<X509Certificate>> {
            val chain = keyStore.getCertificateChain(alias).orEmpty().filterIsInstance<X509Certificate>()
            val complete = chain.isNotEmpty() && chain.size == keyStore.getCertificateChain(alias).size
            return if (complete) Step.Next(chain) else stop(TlsPart.CERTIFICATE, "Kette unbrauchbar")
        }

        private fun keyOf(keyStore: KeyStore, alias: String, password: CharArray): Step<Key> = try {
            keyStore.getKey(alias, password)?.let { Step.Next(it) } ?: stop(TlsPart.PRIVATE_KEY, "Schluessel fehlt")
        } catch (broken: GeneralSecurityException) {
            stop(TlsPart.PRIVATE_KEY, "Schluessel nicht entschluesselbar: ${broken.javaClass.simpleName}")
        }

        private fun checked(entry: Entry): Step<Entry> = if (allowed(entry.key)) {
            Step.Next(entry)
        } else {
            stop(TlsPart.PRIVATE_KEY, "Form nicht zugelassen: ${formOf(entry.key)}")
        }

        private fun allowed(key: Key): Boolean = key is RSAPrivateKey && key.modulus.bitLength() >= MIN_RSA_BITS

        /** Der Algorithmusname des Schlüssels und, bei RSA, die Länge; beides ist kein Schlüsselinhalt. */
        private fun formOf(key: Key): String =
            if (key is RSAPrivateKey) "RSA ${key.modulus.bitLength()} Bit" else key.algorithm

        private fun build(entry: Entry, password: CharArray): TlsMaterial {
            val single = KeyStore.getInstance("PKCS12").also { it.load(null, null) }
            single.setKeyEntry(ALIAS, entry.key, password, entry.chain.toTypedArray())
            val factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            factory.init(single, password)
            val context = SSLContext.getInstance("TLS").also { it.init(factory.keyManagers, null, null) }
            return TlsMaterial(context, entry.chain)
        }

        private fun stop(part: TlsPart, form: String) = Step.Stop(TlsDiagnosis(part, form))
    }
}

private class Entry(val key: Key, val chain: List<X509Certificate>)

/** Ein Schritt des Ladens: weiter mit einem Wert oder halt mit der Diagnose. */
private sealed interface Step<out T> {
    class Next<out T>(val value: T) : Step<T>

    class Stop(val diagnosis: TlsDiagnosis) : Step<Nothing>
}

private inline fun <T, R> Step<T>.then(next: (T) -> Step<R>): Step<R> = when (this) {
    is Step.Next -> next(value)
    is Step.Stop -> this
}
