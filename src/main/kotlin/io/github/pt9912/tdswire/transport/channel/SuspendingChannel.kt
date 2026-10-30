package io.github.pt9912.tdswire.transport.channel

import java.nio.ByteBuffer

/**
 * Ein verbundener Byte-Kanal mit suspendierenden Operationen (`ARC-003`, `ARC-013`).
 *
 * Festlegung als Anforderung an den Aufrufer: höchstens ein Lesevorgang und höchstens ein
 * Schreibvorgang zugleich; Lesen und Schreiben dürfen nebeneinander laufen. Die Schnittstelle
 * erzwingt das nicht, und die Implementierungen verhalten sich bei Verletzung verschieden
 * (siehe dort).
 *
 * Ein Abbruch der aufrufenden Coroutine beendet die Operation und schließt den Kanal.
 */
interface SuspendingChannel {
    /**
     * Liest mindestens ein Byte in [buffer], sobald eines vorliegt, und liefert die Zahl der
     * gelesenen Bytes; -1 meldet das Ende der Gegenstelle. [buffer] muss Platz haben.
     */
    suspend fun read(buffer: ByteBuffer): ChannelResult<Int>

    /** Schreibt alle verbleibenden Bytes aus [buffer] und kehrt erst danach zurück. */
    suspend fun writeFully(buffer: ByteBuffer): ChannelResult<Unit>

    /** Schließt den Kanal; ein weiterer Aufruf ist wirkungslos. */
    fun close()
}
