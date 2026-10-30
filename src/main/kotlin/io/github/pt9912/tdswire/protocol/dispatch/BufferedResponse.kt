package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.protocol.response.TokenSink
import io.github.pt9912.tdswire.protocol.token.ColMetadataToken
import io.github.pt9912.tdswire.protocol.token.Token
import io.github.pt9912.tdswire.protocol.token.TokenEncoder
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.framing.TdsMessage

/**
 * Die Senke einer Anfrage: kodiert die Tokens des `ResponseSequencer` in einen Tokenstrom im Speicher
 * und meldet das erste COLMETADATA an [onResultStart]. Nichts geht auf den Draht, bevor [message] die
 * ganze Antwort liefert; ein Token, das der Encoder nicht schreibt, ist ein Ausfall der Senke (`false`).
 *
 * Festlegung ohne Messung: Die Antwort wird vollständig gepuffert, ihr Speicher wächst mit der
 * Ergebnisgröße, und Backpressure gibt es nicht; die Obergrenze ist offen (`ADR-0006`).
 */
internal class BufferedResponse(private val onResultStart: suspend () -> Unit) : TokenSink {
    private val encoder = TokenEncoder()
    private var started = false

    override suspend fun accept(token: Token): Boolean {
        if (encoder.write(token) !is ProtocolResult.Ok) return false
        if (token is ColMetadataToken && !started) {
            started = true
            onResultStart()
        }
        return true
    }

    /** Die bisher geschriebenen Tokens als eine Antwort-Nachricht. */
    fun message(): TdsMessage = TdsMessage(PacketType.TABULAR_RESULT, encoder.toByteArray())
}
