package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.protocol.response.TokenSink
import io.github.pt9912.tdswire.protocol.token.Token

/**
 * Eine Senke, die vor dem ersten Token des Sequenzierers [lead] in die [inner] schreibt (ENVCHANGE Typ 18 als erstes
 * Token der Antwort auf eine Anfrage mit `RESETCONNECTION`, Messprotokoll W-7). Nimmt die [inner] das Vorspann-Token
 * nicht an, nimmt sie auch das Token nicht an, das ihm folgen sollte.
 */
internal class LeadingToken(private val inner: TokenSink, private var lead: Token?) : TokenSink {
    override suspend fun accept(token: Token): Boolean {
        val first = lead
        lead = null
        return (first == null || inner.accept(first)) && inner.accept(token)
    }
}
