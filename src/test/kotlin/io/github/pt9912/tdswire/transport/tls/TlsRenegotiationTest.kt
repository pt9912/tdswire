package io.github.pt9912.tdswire.transport.tls

import io.github.pt9912.tdswire.testsupport.TlsSetup
import io.github.pt9912.tdswire.testsupport.channel
import io.github.pt9912.tdswire.testsupport.runHandshake
import io.github.pt9912.tdswire.transport.channel.ChannelResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Neuverhandlung in TLS 1.2 durch die Gegenstelle (`SPEC-023` §Festlegungen der TLS-Schleife, `TDS-QA-03`): Die
 * Gegenstelle ist die JDK-`SSLEngine` im selben Prozess, kein Referenzclient; gemessen ist, was die Schleife mit der
 * Voreinstellung des JDK dieses Images tut, nicht eine Zusage der Spezifikation.
 */
class TlsRenegotiationTest {
    private val clientMarker = "klartext-marke-vom-client".toByteArray()
    private val serverMarker = "klartext-marke-vom-server".toByteArray()

    @Volatile
    private var progress = "Start"

    private fun ByteArray.contains(part: ByteArray): Boolean =
        (0..size - part.size).any { start -> part.indices.all { this[start + it] == part[it] } }

    /**
     * Läuft [body] auf einem eigenen Thread mit Frist. Eine Schleife, die nicht suspendiert, hielte `withTimeout`
     * auf, deshalb prüft ein Wächter von außen; ein Hänger ist ein Fehler mit dem letzten Schritt und dem Stapel.
     */
    private fun watched(body: suspend () -> Unit) {
        var failure: Throwable? = null
        val worker = Thread {
            try {
                runBlocking { withTimeout(WAIT_MILLIS) { body() } }
            } catch (all: Throwable) {
                failure = all
            }
        }
        worker.isDaemon = true
        worker.start()
        worker.join(WATCHDOG_MILLIS)
        if (worker.isAlive) fail("haengt bei '$progress': " + worker.stackTrace.take(STACK_LINES).joinToString("\n  "))
        failure?.let { throw AssertionError("Fehler bei '$progress': $it", it) }
    }

    /**
     * Beginnt die Gegenstelle nach dem Handschlag eine Neuverhandlung, beantwortet die Schleife sie (JDK-
     * Voreinstellung: sichere Neuverhandlung zugelassen): Nutzdaten laufen danach in beide Richtungen unversehrt,
     * und die Marke des Servers erscheint nicht im Klartext im Ausgang des Servers. Das gilt für [REPEATS]
     * Runden mit je einer Marke je Richtung; mehr ist nicht belegt (der Eingang des Servers ist die Ausgabe der
     * Test-Engine und zählt nicht als Beleg für das Produkt). Nicht gemessen: Rechenlast durch viele Neuverhandlungen
     * (eine Begrenzung gibt es nicht), Verhalten bei TLS 1.3 (dort gibt es KeyUpdate, keine Neuverhandlung).
     *
     * @spec SPEC-023
     */
    @Test
    fun `Eine Neuverhandlung in TLS 1_2 wird beantwortet und zeigt die Marke nicht im Klartext`() = watched {
        val outcome = runHandshake(TlsSetup(TlsPath.ENCAPSULATED_IN_TDS, listOf("TLSv1.2")))
        val server = outcome.channel()
        val before = outcome.serverWire.written.size
        coroutineScope {
            repeat(REPEATS) { round ->
                progress = "Runde $round: Gegenstelle verlangt Neuverhandlung und schreibt"
                val peerSide = async {
                    outcome.peer.requestKeyUpdate()
                    outcome.peer.writeFully(ByteBuffer.wrap(clientMarker))
                }
                progress = "Runde $round: Server liest"
                val atServer = ByteBuffer.allocate(clientMarker.size)
                while (atServer.hasRemaining()) {
                    val read = server.read(atServer)
                    assertTrue(read is ChannelResult.Ok && read.value > 0, "Lesen des Servers: $read")
                }
                assertContentEquals(clientMarker, atServer.array())
                assertEquals(ChannelResult.Ok(Unit), peerSide.await())
                progress = "Runde $round: Server schreibt"
                assertEquals(ChannelResult.Ok(Unit), server.writeFully(ByteBuffer.wrap(serverMarker)))
                progress = "Runde $round: Gegenstelle liest"
                val atPeer = ByteBuffer.allocate(serverMarker.size)
                while (atPeer.hasRemaining()) {
                    val read = outcome.peer.read(atPeer)
                    assertTrue(read is ChannelResult.Ok && read.value > 0, "Lesen der Gegenstelle: $read")
                }
                assertContentEquals(serverMarker, atPeer.array())
            }
        }
        val sent = outcome.serverWire.written.let { it.copyOfRange(before, it.size) }
        assertTrue(!sent.contains(serverMarker), "Klartext im Ausgang des Servers")
        assertTrue(sent.isNotEmpty(), "die Neuverhandlung hat nichts auf den Draht gebracht")
    }

    /**
     * Gegenprobe des Prüfers: Ein Draht, der die Marke im Klartext trägt, wird erkannt, ein Draht ohne sie nicht.
     * Ohne sie bewiese das Grün der Neuverhandlung nur, dass der Prüfer nichts findet.
     *
     * @spec SPEC-023
     */
    @Test
    fun `Der Pruefer findet eine Marke im Draht`() {
        assertTrue(("vorne".toByteArray() + clientMarker + "hinten".toByteArray()).contains(clientMarker))
        assertTrue(!"kurz".toByteArray().contains(clientMarker))
        assertTrue(!ByteArray(100).contains(serverMarker))
    }

    private companion object {
        const val REPEATS = 3
        const val WAIT_MILLIS = 8_000L
        const val WATCHDOG_MILLIS = 12_000L
        const val STACK_LINES = 25
    }
}
