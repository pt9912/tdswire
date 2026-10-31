package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.protocol.response.TokenSink
import io.github.pt9912.tdswire.protocol.session.ReplyStream
import io.github.pt9912.tdswire.protocol.token.ColMetadataToken
import io.github.pt9912.tdswire.protocol.token.Token
import io.github.pt9912.tdswire.protocol.token.TokenEncoder
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult

/**
 * Die Senke einer Anfrage (`TDS-FA-08.a` Schritt 3, `SPEC-031` „Backpressure begrenzt Puffer“): kodiert
 * jedes Token des `ResponseSequencer` und gibt dessen Bytes an den [reply], bevor sie das nächste Token
 * annimmt; sie meldet das erste COLMETADATA an [onResultStart]. Zusage: [accept] kehrt erst zurück, wenn
 * der [reply] die Bytes angenommen hat; nimmt der Kanal nichts ab, suspendiert der Sequenzierer und
 * liest kein weiteres Ereignis. Die Senke hält nur die Bytes des einen Tokens. Ein Token, das der Encoder
 * nicht schreibt, und ein Schreiben, das scheitert, sind ein Ausfall der Senke (`false`).
 */
internal class StreamedResponse(private val reply: ReplyStream, private val onResultStart: suspend () -> Unit) :
    TokenSink {
    private val encoder = TokenEncoder()
    private var started = false

    override suspend fun accept(token: Token): Boolean {
        val written = encoder.write(token) is ProtocolResult.Ok && reply.write(encoder.drain())
        if (written && token is ColMetadataToken && !started) {
            started = true
            onResultStart()
        }
        return written
    }
}
