package io.github.pt9912.tdswire.protocol.session

import io.github.pt9912.tdswire.protocol.token.DoneCommand
import io.github.pt9912.tdswire.protocol.token.DoneKind
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.DoneToken
import io.github.pt9912.tdswire.protocol.token.TokenEncoder
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.framing.TdsMessage

/**
 * Die Bestätigung eines ATTENTION (`SPEC-029`, `ADR-0027` Entscheidung 1): genau ein DONE mit dem Statusbit
 * `ATTENTION`, Befehlskennung `0x00fd` und Zeilenzahl 0, ohne ERROR-Token und ohne zweite Nachricht. Die Bytes sind
 * als Handvektor belegt; am Testserver des Spike genügte diese Form dem Referenzclient (S11, S15, S17), an einem
 * Server dieses Repos ist sie nicht gemessen.
 */
internal object AttentionAck {
    private val done = DoneToken(DoneKind.DONE, DoneStatus.ATTENTION, DoneCommand.BATCH, rowCount = 0)

    /** Das Token als Bytes, um an eine laufende Antwort angehängt zu werden. */
    val bytes: ByteArray by lazy {
        val encoder = TokenEncoder()
        check(encoder.write(done) is ProtocolResult.Ok) { "Bestätigung nicht schreibbar" }
        encoder.toByteArray()
    }

    /** Die Bestätigung als eigene Antwort-Nachricht (ATTENTION ohne laufende Anfrage). */
    val message: TdsMessage by lazy { TdsMessage(PacketType.TABULAR_RESULT, bytes) }
}
