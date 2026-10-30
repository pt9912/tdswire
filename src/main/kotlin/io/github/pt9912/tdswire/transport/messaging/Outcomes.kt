package io.github.pt9912.tdswire.transport.messaging

import io.github.pt9912.tdswire.transport.channel.ChannelError
import io.github.pt9912.tdswire.transport.framing.FramingError
import io.github.pt9912.tdswire.transport.framing.TdsMessage

/** Warum Lesen oder Schreiben einer Nachricht endet: Eingabe ungültig oder Kanal gestört. */
sealed interface TransportError {
    /** Der Paket-Codec weist Eingabe oder Auftrag ab (Kategorie `TDS_PROTOCOL_ERROR`). */
    data class Framing(val error: FramingError) : TransportError

    /** Der Kanal meldet einen Fehler (Kategorie `TDS_INTERNAL_ERROR`, Festlegung). */
    data class Channel(val error: ChannelError) : TransportError
}

/** Ausgang von [MessageReader.next]: eine Nachricht, das saubere Ende oder ein Fehler. */
sealed interface ReadOutcome {
    data class Message(val message: TdsMessage) : ReadOutcome

    /** Die Gegenstelle hat zwischen zwei Nachrichten geschlossen. */
    data object EndOfStream : ReadOutcome

    data class Failed(val error: TransportError) : ReadOutcome
}

/** Ausgang von [MessageWriter.write]. */
sealed interface WriteOutcome {
    data object Written : WriteOutcome

    data class Failed(val error: TransportError) : WriteOutcome
}
