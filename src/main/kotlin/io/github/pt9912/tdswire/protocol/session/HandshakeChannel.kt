package io.github.pt9912.tdswire.protocol.session

import io.github.pt9912.tdswire.transport.framing.TdsMessage

/** Was [HandshakeChannel.receive] liefert. */
sealed interface Inbound {
    class Message(val message: TdsMessage) : Inbound {
        override fun toString(): String = "Message($message)"
    }

    /** Die Gegenstelle hat zwischen zwei Nachrichten geschlossen. */
    data object EndOfStream : Inbound

    /** Lesen ist gescheitert (Paket-Codec oder Kanal). */
    data object Failed : Inbound
}

/**
 * Die schmale Schnittstelle zwischen dem Handshake ([HandshakeDriver]) und dem Transport
 * (`ARC-003`, `ARC-004`): vollständige Nachricht hinein, Antwort-Nachricht, TLS und Verbindungsende
 * heraus. Der Treiber ruft nur diese Schnittstelle und kennt weder Leser noch Schreiber noch Kanal.
 * Eine Implementierung liest und schreibt je höchstens einen Vorgang zugleich.
 */
interface HandshakeChannel {
    /** Die nächste vollständige Nachricht, das Ende oder den Fehler. */
    suspend fun receive(): Inbound

    /** Schreibt [message]; `false`, wenn das Schreiben scheitert. */
    suspend fun send(message: TdsMessage): Boolean

    /** Führt den TLS-Handschlag aus; `false`, wenn er scheitert. */
    suspend fun startTls(): Boolean

    /**
     * Wechselt die Rahmen-Grenze von der Vor-Login-Grenze auf die Standardgrenze. Bereits gelesene
     * Bytes gehen nicht verloren; die neue Grenze gilt für die Nachrichten, deren Paketköpfe danach
     * geprüft werden.
     */
    fun useSessionLimits()

    /** Schließt die Verbindung; ein weiterer Aufruf ist wirkungslos. */
    fun close()
}
