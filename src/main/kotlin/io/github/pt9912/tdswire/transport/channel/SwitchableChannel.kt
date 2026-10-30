package io.github.pt9912.tdswire.transport.channel

import java.nio.ByteBuffer

/**
 * Ein Kanal, dessen Ziel einmal im Leben der Verbindung wechselt: vor dem TLS-Handschlag der rohe Kanal,
 * danach der verschlüsselte (`ARC-003`). Leser und Schreiber der Nachrichten halten diesen Kanal und
 * bleiben beim Wechsel unverändert. Der Wechsel gilt für den nächsten Vorgang; er wird zwischen zwei
 * Vorgängen ausgeführt, nicht während eines Lesens oder Schreibens (Festlegung des Aufrufers, nicht
 * erzwungen).
 */
class SwitchableChannel(initial: SuspendingChannel) : SuspendingChannel {
    @Volatile
    private var current: SuspendingChannel = initial

    /** Leitet alle folgenden Vorgänge an [next] weiter. */
    fun switchTo(next: SuspendingChannel) {
        current = next
    }

    override suspend fun read(buffer: ByteBuffer): ChannelResult<Int> = current.read(buffer)

    override suspend fun writeFully(buffer: ByteBuffer): ChannelResult<Unit> = current.writeFully(buffer)

    override fun close() = current.close()
}
