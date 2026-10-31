package io.github.pt9912.tdswire.protocol.session

/**
 * Eine Antwort-Nachricht, die in Stücken auf den Draht geht (`TDS-FA-08.a` Schritt 3, `SPEC-031`).
 * Zusage: [write] kehrt erst zurück, wenn der Kanal die Pakete angenommen hat, die das Stück
 * vollendet; solange der Kanal nichts annimmt, suspendiert der Aufrufer (Rückstau). Die Antwort ist
 * erst mit [finish] eine vollständige Nachricht; ohne [finish] endet sie ohne Paket mit EOM-Bit.
 * Nach dem ersten Fehler liefern [write] und [finish] `false`; das gilt auch, wenn ein Aufruf mit einer
 * Ausnahme endete, denn die Antwort kann dann mitten in einem Token stehen. Eine Instanz gehört einem Aufrufer.
 */
interface ReplyStream {
    /** Hängt [bytes] an die Antwort; `false`, wenn das Schreiben scheitert oder die Antwort beendet ist. */
    suspend fun write(bytes: ByteArray): Boolean

    /** Schreibt den Rest samt EOM-Bit; `false`, wenn das Schreiben scheitert oder die Antwort beendet ist. */
    suspend fun finish(): Boolean
}
