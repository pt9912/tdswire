package io.github.pt9912.tdswire.transport.tls

import io.github.pt9912.tdswire.testsupport.RecordingChannel
import io.github.pt9912.tdswire.testsupport.TestKeys
import io.github.pt9912.tdswire.testsupport.TlsSetup
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.channel
import io.github.pt9912.tdswire.testsupport.handMessage
import io.github.pt9912.tdswire.testsupport.handPacket
import io.github.pt9912.tdswire.testsupport.peerFor
import io.github.pt9912.tdswire.testsupport.runHandshake
import io.github.pt9912.tdswire.testsupport.tls11ClientHello
import io.github.pt9912.tdswire.transport.channel.ChannelFailure
import io.github.pt9912.tdswire.transport.channel.ChannelResult
import io.github.pt9912.tdswire.transport.channel.MemoryChannel
import io.github.pt9912.tdswire.transport.channel.SuspendingChannel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.nio.ByteBuffer
import java.security.cert.CertificateExpiredException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Negativfälle der TLS-Schleife: jeder endet in einem Ergebniswert mit Kategorie, der Kanal ist danach
 * geschlossen, und es verlässt kein Anwendungs-Byte im Klartext den Kanal. Die Fehlerfälle mit
 * Zertifikat laufen gegen eine JDK-`SSLEngine` als Client; die übrigen schicken handgebaute Bytes.
 */
class TlsNegativeTest {
    private val bothPaths = listOf(TlsPath.FROM_FIRST_BYTE, TlsPath.ENCAPSULATED_IN_TDS)

    private fun failureOf(result: TlsResult<TlsChannel>): TlsError {
        assertIs<TlsResult.Failed>(result)
        return result.error
    }

    /** Der Text des Fehlers trägt keinen Namen aus dem Zertifikat und keine Datei- oder Kennwortangabe. */
    private fun assertNoLeak(error: TlsError) {
        listOf("CN=", "localhost", "andere.invalid", ".p12", "tdswire-test", "-----").forEach {
            assertTrue(it !in error.detail, "Detail enthält $it: ${error.detail}")
        }
    }

    /** Die Ausnahme und ihre Ursachen, von außen nach innen. */
    private fun causes(failure: Throwable?): List<Throwable> = generateSequence(failure) { it.cause }.toList()

    private suspend fun acceptAgainst(
        path: TlsPath,
        feed: suspend (SuspendingChannel) -> Unit,
    ): Pair<TlsError, RecordingChannel> = coroutineScope {
        val (serverEnd, peerEnd) = MemoryChannel.pair(1 shl 16)
        val recording = RecordingChannel(serverEnd)
        val accepted = async { TlsChannel.accept(recording, TestKeys.serverContext(), path) }
        feed(peerEnd)
        val error = failureOf(accepted.await())
        assertClosed(recording)
        error to recording
    }

    /** Der innere Kanal ist nach dem Fehler geschlossen: ein Schreiben scheitert, es geht nichts mehr hinaus. */
    private suspend fun assertClosed(channel: RecordingChannel) {
        val before = channel.written.size
        val attempt = channel.writeFully(ByteBuffer.wrap("Klartext".toByteArray()))
        assertIs<ChannelResult.Failed>(attempt)
        assertEquals(before, channel.written.size)
    }

    private suspend fun SuspendingChannel.send(bytes: ByteArray) {
        assertEquals(ChannelResult.Ok(Unit), writeFully(ByteBuffer.wrap(bytes)))
    }

    /**
     * Ein abgelaufenes Zertifikat scheitert an der prüfenden Gegenstelle (Alert); die Schleife meldet
     * `HANDSHAKE_FAILED` mit Kategorie `TDS_PROTOCOL_ERROR` und ohne Zertifikatsinhalt im Text, in
     * beiden Pfaden. Die Gegenstelle bricht mit einer `SSLException` ab, deren Ursachenkette eine
     * `CertificateExpiredException` enthält (der Ablauf ist die beobachtete Ursache, nicht nur der Abbruch).
     *
     * @spec SPEC-028
     */
    @Test
    fun `expired certificate ends in a handshake failure`() = blocking {
        bothPaths.forEach { path ->
            val outcome = runHandshake(TlsSetup(path, serverKeys = TestKeys.EXPIRED, clientTrust = TestKeys.EXPIRED))
            assertIs<javax.net.ssl.SSLException>(outcome.peerFailure)
            assertTrue(causes(outcome.peerFailure).any { it is CertificateExpiredException }, "Ursache Ablauf")
            val error = failureOf(outcome.server)
            assertEquals(TlsFailure.HANDSHAKE_FAILED, error.failure)
            assertEquals(TlsErrorCategory.TDS_PROTOCOL_ERROR, error.category)
            assertNoLeak(error)
            assertTrue(Regex("[A-Za-z]+").matches(error.detail), "Detail ist ein Ausnahmetyp: ${error.detail}")
            assertClosed(outcome.serverWire)
        }
    }

    /**
     * Ein Zertifikat für einen anderen Namen scheitert an der Namensprüfung der Gegenstelle (die Meldung
     * der Gegenstelle nennt den fehlenden Namen im Zertifikat, Wortlaut des JDK 21 der Testumgebung);
     * Ergebnis wie beim abgelaufenen Zertifikat, in beiden Pfaden.
     *
     * @spec SPEC-028
     */
    @Test
    fun `certificate for another name ends in a handshake failure`() = blocking {
        bothPaths.forEach { path ->
            val setup = TlsSetup(path, serverKeys = TestKeys.OTHER_NAME, clientTrust = TestKeys.OTHER_NAME)
            val outcome = runHandshake(setup)
            assertIs<javax.net.ssl.SSLException>(outcome.peerFailure)
            val reasons = causes(outcome.peerFailure).mapNotNull { it.message }
            assertTrue(reasons.any { "No subject alternative DNS name" in it }, "Ursache Hostname: $reasons")
            val error = failureOf(outcome.server)
            assertEquals(TlsFailure.HANDSHAKE_FAILED, error.failure)
            assertNoLeak(error)
            assertClosed(outcome.serverWire)
        }
    }

    /**
     * Ein ClientHello nur nach TLS 1.1 (von Hand gebaut) wird abgelehnt, im TDS-8.0-Pfad roh und im
     * TDS-7.4-Pfad in einem `0x12`-Paket; Mindestversion ist TLS 1.2. Der Server schreibt nichts.
     * Grenze: Das JDK sperrt TLS 1.1 ohnehin (`jdk.tls.disabledAlgorithms`); der Test wird teils vom
     * JDK-Default getragen und belegt `settings.protocols` nicht. Das tut `peer without a common version …`.
     *
     * @spec SPEC-023 SPEC-028
     */
    @Test
    fun `TLS 1_1 hello is rejected in both paths`() = blocking {
        val raw = acceptAgainst(TlsPath.FROM_FIRST_BYTE) { it.send(tls11ClientHello()) }
        assertEquals(TlsFailure.HANDSHAKE_FAILED, raw.first.failure)
        assertEquals(0, raw.second.written.size)
        val packed = acceptAgainst(TlsPath.ENCAPSULATED_IN_TDS) { it.send(handMessage(0x12, tls11ClientHello(), 4000)) }
        assertEquals(TlsFailure.HANDSHAKE_FAILED, packed.first.failure)
        assertEquals(0, packed.second.written.size)
    }

    /**
     * Eine Gegenstelle ohne gemeinsame Protokollversion mit der Konfiguration des Servers wird abgewiesen:
     * Server nur TLS 1.3 gegen Gegenstelle nur TLS 1.2 und umgekehrt (beide Pfade). Der Server liefert
     * `HANDSHAKE_FAILED`, schreibt nichts mehr, und der Kanal ist zu. Das belegt, dass die Protokolle der
     * Konfiguration an die Engine gehen (die Gegenstelle bietet sonst beide Versionen des JDK an).
     *
     * @spec SPEC-023 SPEC-028
     */
    @Test
    fun `peer without a common version is rejected`() = blocking {
        val tls13 = listOf("TLSv1.3")
        val tls12 = listOf("TLSv1.2")
        bothPaths.forEach { path ->
            listOf(tls13 to tls12, tls12 to tls13).forEach { (server, peer) ->
                val setup = TlsSetup(path, protocols = peer, serverProtocols = server)
                val outcome = runHandshake(setup)
                assertEquals(TlsFailure.HANDSHAKE_FAILED, failureOf(outcome.server).failure, "$path $server $peer")
                assertNotNull(outcome.peerFailure)
                assertClosed(outcome.serverWire)
            }
        }
    }

    /**
     * Eine Gegenstelle ohne gemeinsame Suite mit der Konfiguration des Servers wird abgewiesen: nur CBC
     * gegen einen Server mit den Suiten von `SPEC-023` in TLS 1.2, eine
     * TLS-1.3-Suite gegen die andere. Die Gegenprobe ist derselbe Aufbau mit einem Server, dessen
     * Konfiguration die Suite der Gegenstelle nennt: dort gelingt der Handshake, die Abweisung kommt also
     * aus der Konfiguration, nicht daraus, dass das JDK die Suite sperrt. Dass `TlsParameters` auch solche
     * Suiten annimmt, ist die dokumentierte Grenze der Klasse. Eine Suite mit RSA-Schlüsselaustausch ist
     * nicht getestet: das JDK der Testumgebung bietet sie als Client nicht an, der Handshake kommt nicht zustande.
     *
     * @spec SPEC-023 SPEC-028
     */
    @Test
    fun `peer without a common suite is rejected`() = blocking {
        val tls12 = listOf("TLSv1.2")
        val cbc = listOf("TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA")
        val others = listOf(
            Triple(tls12, cbc, TlsParameters.TLS12_SUITES),
            Triple(listOf("TLSv1.3"), listOf("TLS_AES_128_GCM_SHA256"), listOf("TLS_AES_256_GCM_SHA384")),
        )
        bothPaths.forEach { path ->
            others.forEach { (protocols, peerSuites, serverSuites) ->
                val rejected = runHandshake(TlsSetup(path, protocols, peerSuites, serverSuites = serverSuites))
                assertEquals(TlsFailure.HANDSHAKE_FAILED, failureOf(rejected.server).failure, "$path $peerSuites")
                assertNotNull(rejected.peerFailure)
                assertClosed(rejected.serverWire)

                val control = runHandshake(TlsSetup(path, protocols, peerSuites, serverSuites = peerSuites))
                assertEquals(peerSuites.single(), control.channel().cipherSuite, "Gegenprobe $path")
            }
        }
    }

    /**
     * Versionen unterhalb von TLS 1.2 sind für die Schleife nicht konfigurierbar: `TlsParameters`
     * weist TLS 1.1 und TLS 1.0 ab.
     *
     * @spec SPEC-023
     */
    @Test
    fun `parameters below TLS 1_2 are refused`() {
        assertFailsWith<IllegalArgumentException> { TlsParameters(listOf("TLSv1.1"), TlsParameters.TLS12_SUITES) }
        assertFailsWith<IllegalArgumentException> {
            TlsParameters(listOf("TLSv1.2", "TLSv1"), TlsParameters.TLS12_SUITES)
        }
        assertFailsWith<IllegalArgumentException> { TlsParameters(emptyList(), TlsParameters.TLS12_SUITES) }
    }

    /**
     * Handshake-Bytes in einem Paket falschen Typs (SQL_BATCH `0x01`, ein Wert außerhalb des Katalogs,
     * eine Länge unter dem Header) im TDS-7.4-Pfad sind ein `PROTOCOL_VIOLATION`; der Server schreibt nichts.
     *
     * @spec SPEC-028
     */
    @Test
    fun `handshake bytes in a packet of the wrong type are a protocol violation`() = blocking {
        val hello = tls11ClientHello()
        val wrongType = handPacket(0x01, 1, hello)
        val unknownType = handPacket(0x55, 1, hello)
        val tooShort = byteArrayOf(0x12, 1, 0, 4, 0, 0, 1, 0)
        listOf(wrongType, unknownType, tooShort).forEach { bytes ->
            val result = acceptAgainst(TlsPath.ENCAPSULATED_IN_TDS) { it.send(bytes) }
            assertEquals(TlsFailure.PROTOCOL_VIOLATION, result.first.failure)
            assertEquals(TlsErrorCategory.TDS_PROTOCOL_ERROR, result.first.category)
            assertEquals(0, result.second.written.size)
        }
    }

    /**
     * Ein letztes Handshake-Paket, dessen Nutzdaten über das Ende von `FINISHED` hinaus in den Puffer der
     * Engine nicht mehr passen (hier 30000 Nullbytes hinter Client-Finished), ist ein `PROTOCOL_VIOLATION`:
     * der Rest gehört dem Paket, nicht dem rohen Strom. Nicht geprüft ist ein kleiner Rest, der noch in den
     * Puffer passt: er scheitert erst in der Datenphase (fail-closed, nicht am Umschaltpunkt gemeldet).
     *
     * @spec SPEC-028
     */
    @Test
    fun `rest of a last handshake packet too large for the engine buffer is a protocol violation`() = blocking {
        coroutineScope {
            val setup = TlsSetup(TlsPath.ENCAPSULATED_IN_TDS, listOf("TLSv1.3"))
            val (serverEnd, peerEnd) = MemoryChannel.pair(1 shl 17)
            val recording = RecordingChannel(serverEnd)
            val peer = peerFor(setup, peerEnd, payloadPerPacket = 30000, trailingJunk = 30000)
            val accepted = async {
                val parameters = TlsParameters(setup.protocols, setup.suites)
                TlsChannel.accept(recording, TestKeys.serverContext(), setup.path, parameters)
            }
            val peerSide = async { peer.handshake().also { peer.close() } }
            val error = failureOf(accepted.await())
            peerSide.await()
            assertEquals(TlsFailure.PROTOCOL_VIOLATION, error.failure)
            assertClosed(recording)
        }
    }

    /**
     * Ein abgeschnittener Handshake (halbes ClientHello, ein halbes Paket oder kein Byte, dann
     * Ende des Stroms) ist `CONNECTION_ABORTED` mit Kategorie `TDS_INTERNAL_ERROR`, in beiden Pfaden.
     *
     * @spec SPEC-028
     */
    @Test
    fun `truncated handshake ends as an aborted connection`() = blocking {
        val hello = tls11ClientHello()
        val cases = listOf(
            TlsPath.FROM_FIRST_BYTE to hello.copyOf(20),
            TlsPath.FROM_FIRST_BYTE to ByteArray(0),
            TlsPath.ENCAPSULATED_IN_TDS to handMessage(0x12, hello, 4000).copyOf(30),
            TlsPath.ENCAPSULATED_IN_TDS to handMessage(0x12, hello, 4000).copyOf(5),
            TlsPath.ENCAPSULATED_IN_TDS to ByteArray(0),
        )
        cases.forEach { (path, bytes) ->
            val result = acceptAgainst(path) {
                if (bytes.isNotEmpty()) it.send(bytes)
                it.close()
            }
            assertEquals(TlsFailure.CONNECTION_ABORTED, result.first.failure, "$path ${bytes.size}")
            assertEquals(TlsErrorCategory.TDS_INTERNAL_ERROR, result.first.category)
        }
    }

    /**
     * Klartext statt TLS im TDS-8.0-Pfad (ein PRELOGIN-Paket) endet in `HANDSHAKE_FAILED`; der Server
     * schreibt kein Byte, auch keine Fehlermeldung im Klartext.
     *
     * @spec SPEC-028
     */
    @Test
    fun `plaintext instead of TLS in the eight zero path is rejected without a reply`() = blocking {
        val prelogin = handMessage(0x12, ByteArray(24) { it.toByte() }, 4000)
        val result = acceptAgainst(TlsPath.FROM_FIRST_BYTE) { it.send(prelogin) }
        assertEquals(TlsFailure.HANDSHAKE_FAILED, result.first.failure)
        assertEquals(0, result.second.written.size)
    }

    /**
     * Im TDS-8.0-Pfad wird eine Gegenstelle abgewiesen, die kein ALPN anbietet oder nur ein fremdes
     * (`h2`): der Ausgang ist `ALPN_MISMATCH` oder `HANDSHAKE_FAILED`, nie ein Kanal. Festlegung dieser
     * Schleife, nicht am Referenzclient gemessen.
     *
     * @spec SPEC-028
     */
    @Test
    fun `eight zero path rejects a peer without the ALPN tds 8_0`() = blocking {
        listOf(emptyList(), listOf("h2")).forEach { alpn ->
            val outcome = runHandshake(TlsSetup(TlsPath.FROM_FIRST_BYTE, peerAlpn = alpn))
            val error = failureOf(outcome.server)
            assertTrue(error.failure in setOf(TlsFailure.ALPN_MISMATCH, TlsFailure.HANDSHAKE_FAILED), "$alpn: $error")
            assertClosed(outcome.serverWire)
        }
        val without = failureOf(runHandshake(TlsSetup(TlsPath.FROM_FIRST_BYTE, peerAlpn = emptyList())).server)
        assertEquals(TlsFailure.ALPN_MISMATCH, without.failure)
    }

    /**
     * Schließt die Gegenstelle nach dem Handshake ohne `close_notify`, liefert `read` ein `Failed` und nicht
     * das Ende, auch nachdem zuvor gesendete Daten vollständig angekommen sind; das Ergebnis bleibt bei
     * weiteren Aufrufen gleich.
     *
     * @spec SPEC-028
     */
    @Test
    fun `abrupt close after the handshake is a failure not an end`() = blocking {
        bothPaths.forEach { path ->
            val clean = runHandshake(TlsSetup(path))
            val server = clean.channel()
            clean.peer.close()
            val first = server.read(ByteBuffer.allocate(10))
            assertIs<ChannelResult.Failed>(first)
            assertEquals(ChannelFailure.IO_FAILURE, first.error.failure)
            assertEquals(first, server.read(ByteBuffer.allocate(10)))

            val delivered = runHandshake(TlsSetup(path))
            val serverAfterData = delivered.channel()
            delivered.peer.writeFully(ByteBuffer.wrap(ByteArray(2000)))
            delivered.peer.close()
            val received = ByteBuffer.allocate(2000)
            while (received.hasRemaining()) assertIs<ChannelResult.Ok<Int>>(serverAfterData.read(received))
            assertIs<ChannelResult.Failed>(serverAfterData.read(ByteBuffer.allocate(10)))
        }
    }

    /**
     * Schreibt der Server nach dem Schließen der Gegenstelle, ist das ein `Failed`, keine Ausnahme.
     *
     * @spec SPEC-028
     */
    @Test
    fun `writing to a closed peer fails with a result`() = blocking {
        bothPaths.forEach { path ->
            val outcome = runHandshake(TlsSetup(path))
            val server = outcome.channel()
            outcome.peer.close()
            val results = (1..3).map { server.writeFully(ByteBuffer.wrap(ByteArray(100))) }
            assertTrue(results.any { it is ChannelResult.Failed }, "$results")
        }
    }
}
