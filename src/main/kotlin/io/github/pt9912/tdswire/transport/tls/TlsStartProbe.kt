package io.github.pt9912.tdswire.transport.tls

import io.github.pt9912.tdswire.transport.channel.MemoryChannel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Socket
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedTrustManager
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Ausgang der Startprobe: bestanden mit der ausgehandelten Version und Suite, oder die Diagnose. */
sealed interface ProbeResult {
    data class Passed(val protocol: String, val cipherSuite: String) : ProbeResult

    data class Failed(val diagnosis: TlsDiagnosis) : ProbeResult
}

/**
 * Startprobe der TLS-Konfiguration (`SPEC-043`): ein vollständiger Handschlag im Speicher zwischen
 * der Schleife ([TlsChannel.accept], Pfad [TlsPath.FROM_FIRST_BYTE]) und einer Gegenstelle, die nur
 * prüft, dass jedes Zertifikat der Kette jetzt gültig ist. Die Probe nutzt genau die übergebenen
 * [TlsParameters]. Sie öffnet keinen Port, keinen Thread und keine Datei; ein
 * `IllegalArgumentException` kommt wie bei [TlsChannel.accept] nur, wenn die JVM eine genannte
 * Version oder Suite nicht kennt.
 *
 * Grenze (`SPEC-043`): die Probe prüft weder, ob ein Client dem Zertifikat vertraut, noch die
 * TDS-Kapselung des Handschlags (Pfad [TlsPath.ENCAPSULATED_IN_TDS]), noch den Namen im Zertifikat.
 * Festlegung (nicht gemessen): scheitert der Handschlag und ist keine Kette abgelaufen, ist der Teil
 * [TlsPart.PAIR]; eine Probe, die nicht binnen [timeout] endet, ebenso.
 */
object TlsStartProbe {
    val DEFAULT_TIMEOUT: Duration = 10.seconds
    private const val MEMORY_CAPACITY = 1 shl 16

    suspend fun run(
        material: TlsMaterial,
        parameters: TlsParameters,
        timeout: Duration = DEFAULT_TIMEOUT,
    ): ProbeResult {
        val trust = ValidityOnlyTrust()
        val outcome = withTimeoutOrNull(timeout) { handshake(material.context, parameters, trust) }
        return when {
            trust.rejectedChain -> failed(TlsPart.CERTIFICATE, "Kette jetzt nicht gueltig")

            outcome == null -> failed(TlsPart.PAIR, "Zeitueberschreitung")

            outcome.server is TlsResult.Failed -> {
                val server = outcome.server.error.failure
                failed(TlsPart.PAIR, "Handschlag $server / Gegenstelle ${outcome.clientFailure}")
            }

            outcome.server is TlsResult.Ok -> passed(outcome.server.value)

            else -> failed(TlsPart.PAIR, "Handschlag")
        }
    }

    private fun failed(part: TlsPart, form: String) = ProbeResult.Failed(TlsDiagnosis(part, form))

    private fun passed(channel: TlsChannel): ProbeResult =
        ProbeResult.Passed(channel.protocol, channel.cipherSuite).also { channel.close() }

    private class Outcome(val server: TlsResult<TlsChannel>, val clientFailure: String?)

    private suspend fun handshake(context: SSLContext, parameters: TlsParameters, trust: ValidityOnlyTrust): Outcome =
        coroutineScope {
            val (serverEnd, clientEnd) = MemoryChannel.pair(MEMORY_CAPACITY)
            val server = async { TlsChannel.accept(serverEnd, context, TlsPath.FROM_FIRST_BYTE, parameters) }
            val client = async {
                ProbeClient(clientEngine(trust, parameters), clientEnd).handshake().also { clientEnd.close() }
            }
            Outcome(server.await(), client.await())
        }

    private fun clientEngine(trust: ValidityOnlyTrust, parameters: TlsParameters): SSLEngine {
        val context = SSLContext.getInstance("TLS").also { it.init(null, arrayOf(trust), null) }
        val engine = context.createSSLEngine()
        engine.useClientMode = true
        val settings = engine.sslParameters
        settings.protocols = parameters.protocols.toTypedArray()
        settings.cipherSuites = parameters.cipherSuites.toTypedArray()
        settings.applicationProtocols = arrayOf(TlsChannel.ALPN_TDS_8_0)
        engine.sslParameters = settings
        return engine
    }
}

/**
 * Vertrauensprüfung der Probe: jedes Zertifikat der Kette muss jetzt gültig sein, mehr nicht. Ein
 * Fehler setzt [rejectedChain]; die Probe ordnet ihn danach dem Teil `CERTIFICATE` zu.
 */
private class ValidityOnlyTrust : X509ExtendedTrustManager() {
    @Volatile
    var rejectedChain = false
        private set

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        try {
            chain.forEach { it.checkValidity() }
        } catch (invalid: CertificateException) {
            rejectedChain = true
            throw invalid
        }
    }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) =
        checkServerTrusted(chain, authType)

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) =
        checkServerTrusted(chain, authType)

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
        throw CertificateException("Die Probe prueft keine Client-Zertifikate")

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) =
        checkClientTrusted(chain, authType)

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) =
        checkClientTrusted(chain, authType)

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
