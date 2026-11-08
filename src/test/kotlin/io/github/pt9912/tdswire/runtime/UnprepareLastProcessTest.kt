package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.config.ConfigDefaults
import io.github.pt9912.tdswire.config.ConfigKeys
import io.github.pt9912.tdswire.config.TempFilesTest
import io.github.pt9912.tdswire.protocol.dispatch.BatchVectors
import io.github.pt9912.tdswire.protocol.session.SessionVectors
import io.github.pt9912.tdswire.testsupport.handMessage
import io.github.pt9912.tdswire.testsupport.handPacket
import io.github.pt9912.tdswire.transport.listener.LOOPBACK
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val TABULAR_TYPE = 4
private const val PACKET_PAYLOAD = 32760
private const val HEADER_RESERVE = 600 * 8
private const val HEADER_BYTES = 8
private const val BYTES_PER_STATEMENT = 40
private const val CONNECTIONS = 2
private const val ERROR_BYTES = 113
private const val STATUS_BYTES = 5
private const val DONE_BYTES = 13
private const val ANSWER_BYTES = ERROR_BYTES + STATUS_BYTES + DONE_BYTES

/** RETURNSTATUS 8179 und DONEPROC `ERROR` mit Befehl `0x00e0`: das Ende der Antwort für eine nicht gehaltene Nummer. */
private const val UNKNOWN_LAST = "79F31F0000FE0200E0000000000000000000"

/**
 * Der Serverprozess (Kind-JVM mit `-Xmx256m`, `-XX:+ExitOnOutOfMemoryError` wie im Image des Testservers) unter
 * mehreren gleichzeitigen Verbindungen, die je einen Batch bis zur Grenze der Nachricht aus lauter unbekannten
 * Nummern senden (`SPEC-060`, `SPEC-007`). Der Client baut seine Bytes von Hand und wirft die Antwort beim Lesen weg.
 */
internal class UnprepareLastProcessTest : TempFilesTest() {
    private val statements =
        (ConfigDefaults.MAX_MESSAGE_BYTES - BatchVectors.payload("").size - HEADER_RESERVE) / BYTES_PER_STATEMENT

    /** Der Batch als Draht: Pakete zu [PACKET_PAYLOAD] Nutzdatenbytes, in einem Zug in einen Puffer gebaut. */
    private fun wire(): ByteArray {
        val payload = BatchVectors.payload("EXEC sp_unprepare 1;".repeat(statements - 1) + "EXEC sp_unprepare 1")
        val packets = (payload.size + PACKET_PAYLOAD - 1) / PACKET_PAYLOAD
        val wire = ByteArray(payload.size + packets * HEADER_BYTES)
        var at = 0
        for (index in 0 until packets) {
            val from = index * PACKET_PAYLOAD
            val to = minOf(payload.size, from + PACKET_PAYLOAD)
            val last = if (to == payload.size) 1 else 0
            val packet = handPacket(BATCH_TYPE, last, payload.copyOfRange(from, to), index + 1)
            packet.copyInto(wire, at)
            at += packet.size
        }
        return wire
    }

    private fun login(client: RawClient) {
        client.send(preloginWire(SessionVectors.ENCRYPT_OFF))
        assertNotNull(client.readMessage())
        val payload = SessionVectors.login7Payload {
            userName = "alice"
            password = "secret".toCharArray()
        }
        client.send(handMessage(LOGIN7_TYPE, payload, 4000))
        assertEquals(TABULAR_TYPE, assertNotNull(client.readMessage()).type)
    }

    private fun environment() = mapOf(
        ConfigKeys.LISTEN_HOST to LOOPBACK,
        ConfigKeys.LISTEN_PORT to "0",
    ) + testModeEnvironment(file("users.txt", "alice\t{plain}secret\n"))

    /** Eine Verbindung: Anmeldung, der große Batch, die Antwort verworfen; der Ausgang als Text. */
    private fun oneConnection(port: Int, wire: ByteArray, ready: CountDownLatch): DrainedMessage? =
        RawClient(port).use { client ->
            login(client)
            ready.countDown()
            ready.await()
            client.send(wire)
            client.drainMessage()
        }

    /**
     * Zwei Verbindungen senden gleichzeitig je einen Batch bis zur Grenze der Nachricht (`MAX_MESSAGE_BYTES`, 16 MiB)
     * aus lauter unbekannten Nummern an den Serverprozess mit 256 MiB Heap: jede erhält die vollständige Antwort (ein
     * ERROR von 113 Byte, ein RETURNSTATUS von 5 Byte und ein DONEPROC von 13 Byte je Nummer, 131 Byte), der Prozess
     * läuft weiter und beantwortet danach eine neue Verbindung. Wächter: eine Antwort, die als Puffer oder Liste im
     * Speicher steht (der Prozess endet mit `OutOfMemoryError`, gemessen), eine Obergrenze der Meldungen (zu kurze
     * Antwort). Die Aussage gilt für zwei, nicht für beliebig viele Verbindungen: vier gleichzeitige Nachrichten
     * dieser Größe überlasten den Heap auch ohne die Antwort (Messprotokoll
     * `docs/messungen/unprepare-antwort-speicher-2026-11-07.md`).
     *
     * @spec SPEC-060
     * @spec SPEC-007
     */
    @Test
    fun `Zwei gleichzeitige Batches bis zur Grenze der Nachricht erhalten die vollstaendige Antwort`() {
        val wire = wire()
        ServerProcess(environment(), jvmOptions = listOf("-XX:+ExitOnOutOfMemoryError")).use { server ->
            val port = server.port()
            val ready = CountDownLatch(CONNECTIONS)
            val results = arrayOfNulls<DrainedMessage>(CONNECTIONS)
            val threads = (0 until CONNECTIONS).map { index ->
                thread { results[index] = oneConnection(port, wire, ready) }
            }
            threads.forEach { it.join() }
            results.forEachIndexed { index, reply ->
                val message = assertNotNull(reply, "Verbindung $index ohne Antwort; ${server.output.takeLast(3)}")
                assertEquals(TABULAR_TYPE, message.type)
                assertEquals(statements.toLong() * ANSWER_BYTES, message.payloadBytes, "Verbindung $index")
                assertEquals(UNKNOWN_LAST, message.tail.takeLast(STATUS_BYTES + DONE_BYTES).toHexCompact())
            }
            assertTrue(server.alive(), "der Prozess endete: ${server.output.takeLast(3)}")
            RawClient(port).use { client ->
                login(client)
                client.send(handMessage(BATCH_TYPE, BatchVectors.payload("EXEC sp_unprepare 1"), PACKET_PAYLOAD))
                val small = assertNotNull(client.drainMessage())
                assertEquals(ANSWER_BYTES.toLong(), small.payloadBytes)
            }
        }
    }

    private fun List<Byte>.toHexCompact() = joinToString("") { String.format(java.util.Locale.ROOT, "%02X", it) }
}
