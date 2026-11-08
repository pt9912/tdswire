package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.protocol.response.TokenSink
import io.github.pt9912.tdswire.protocol.token.ColMetadataToken
import io.github.pt9912.tdswire.protocol.token.DoneToken
import io.github.pt9912.tdswire.protocol.token.Token

/**
 * Eine Senke, die für die Antwort festhält, ob der Client ein Ergebnis begonnen und noch keinen Abschluss dazu gesehen
 * hat: [open] wird mit dem angenommenen COLMETADATA wahr und mit dem nächsten angenommenen DONE, DONEINPROC oder
 * DONEPROC falsch. Ein Abschluss, den der Sequenzierer noch zurückhält, ist nicht geschrieben und zählt nicht. Der
 * Fristablauf bei [open] schließt die Antwort mit der Befehlskennung `0x00c1` (`SPEC-031`).
 */
internal class OpenResultTracker(private val inner: TokenSink) : TokenSink {
    /** Wahr, solange ein begonnenes Ergebnis keinen geschriebenen Abschluss hat. */
    var open: Boolean = false
        private set

    override suspend fun accept(token: Token): Boolean {
        val written = inner.accept(token)
        if (written) {
            when (token) {
                is ColMetadataToken -> open = true
                is DoneToken -> open = false
                else -> Unit
            }
        }
        return written
    }
}
