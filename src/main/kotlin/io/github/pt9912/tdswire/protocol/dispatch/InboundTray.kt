package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.protocol.session.HandshakeChannel
import io.github.pt9912.tdswire.protocol.session.Inbound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/**
 * Der Eingang der Anfrageschleife: liest Nachrichten aus dem [channel] und erlaubt es, die nächste zu sehen, ohne
 * sie zu verbrauchen, damit ein ATTENTION während einer Anfrage erkannt wird (`SPEC-029`).
 *
 * Zusage: ein begonnener Lesevorgang wird nie abgebrochen, bevor die Schleife endet. Ein Abbruch des Lesens schließt
 * den Kanal (Vertrag von `SuspendingChannel`) und verwirft, was schon gelesen ist. Das Warten auf das Ergebnis
 * ist dagegen abbrechbar ([peek]); der Lesevorgang läuft in [scope] weiter und liefert seine Nachricht dem nächsten
 * [peek] oder [take]. Es liest höchstens ein Vorgang zugleich. Nicht thread-sicher: ein Aufrufer zugleich, der
 * Wechsel zwischen Aufrufern braucht eine Synchronisierung durch `join`.
 */
internal class InboundTray(private val channel: HandshakeChannel, private val scope: CoroutineScope) {
    private var pending: Deferred<Inbound>? = null
    private var held: Inbound? = null

    /** Die nächste Nachricht, ohne sie zu verbrauchen; startet einen Lesevorgang, falls keiner läuft. */
    suspend fun peek(): Inbound {
        held?.let { return it }
        val read = pending ?: scope.async { channel.receive() }.also { pending = it }
        val inbound = read.await()
        pending = null
        held = inbound
        return inbound
    }

    /** Verbraucht die zuletzt gesehene Nachricht. */
    fun drop() {
        held = null
    }

    /** Die nächste Nachricht: die gesehene, das Ergebnis des laufenden Lesevorgangs oder eine neu gelesene. */
    suspend fun take(): Inbound {
        val seen = held
        held = null
        if (seen != null) return seen
        val read = pending
        pending = null
        return read?.await() ?: channel.receive()
    }

    /** Bricht einen noch laufenden Lesevorgang ab; nur am Ende der Schleife, wenn der Kanal schließt. */
    fun release() {
        pending?.cancel()
        pending = null
    }
}
