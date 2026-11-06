package io.github.pt9912.tdswire.transport.listener

import io.github.pt9912.tdswire.transport.channel.SuspendingChannel
import java.net.InetAddress

/**
 * Was der Listener einem [ConnectionHandler] von seiner Verbindung gibt (`ARC-003`): den rohen
 * Kanal und zwei Meldungen. Verschlüsselung, Pakete und Protokollzustände gehören dem Handler; der
 * Transport kennt nur diese zwei Zustände.
 *
 * Festlegung (Form der Schnittstelle, nicht gemessen): die Meldungen sind die einzigen, die der
 * Listener braucht, um `LOGIN_TIMEOUT` (`SPEC-008`) und das geordnete Beenden (`SPEC-044`) zu halten.
 * Die Protokollschicht darf die Form ändern.
 */
internal interface Connection {
    /** Laufende Nummer dieser Verbindung im Listener; sie taucht in den [ListenerEvent]s auf. */
    val id: Long

    /** Der verbundene Kanal der Gegenstelle ohne TLS; der Listener schließt ihn mit der Verbindung. */
    val channel: SuspendingChannel

    /** Die Adresse der Gegenstelle des Kanals; `null`, wenn der Kanal keine kennt (`ADR-0016`). */
    val remoteAddress: InetAddress? get() = null

    /** Meldet „Login abgeschlossen“: `LOGIN_TIMEOUT` gilt für diese Verbindung nicht mehr. */
    fun loginCompleted()

    /**
     * Meldet, ob eine Anfrage läuft ([running] = `true`) oder nicht. Eine unvollständig empfangene Nachricht
     * meldet [messageInProgress]; erst wenn beides ruht, endet die Verbindung im geordneten Beenden (`SPEC-044`
     * Nr. 2), und zwar mit der Meldung, die den letzten der beiden Zustände löst.
     */
    fun requestRunning(running: Boolean)

    /**
     * Meldet, ob Bytes einer unvollständig empfangenen Nachricht vorliegen. Eine solche Verbindung gilt im geordneten
     * Beenden nicht als ruhend und bleibt bis zum Ablauf der Frist offen (`SPEC-044` Nr. 2 und Schlussabsatz).
     */
    fun messageInProgress(inProgress: Boolean) = Unit

    /**
     * Wahr ab dem Beginn des geordneten Beendens (`SPEC-044`). Eine Anfrage, die danach eintrifft, beantwortet der
     * Handler mit `TDS_SHUTDOWN`, statt sie auszuführen.
     */
    val shutdownBegun: Boolean get() = false

    /**
     * Meldet, was nach Ablauf von `SHUTDOWN_GRACE` mit einer laufenden Anfrage geschieht (`SPEC-044` Nr. 4): der
     * Listener ruft [abort] einmal in diesem Scope der Verbindung, und der Handler bricht die Anfrage wie bei
     * ATTENTION ab. Ohne Meldung trennt der Listener die Verbindung beim Ablauf der Frist sofort; mit Meldung nach
     * `CANCEL_TIMEOUT`, falls die Verbindung dann noch besteht.
     */
    fun onShutdownGraceExpired(abort: suspend () -> Unit) = Unit
}

/**
 * Nimmt eine angenommene Verbindung entgegen und läuft in deren Coroutine-Scope. Kehrt [handle] zurück
 * oder wirft, wird die Verbindung geschlossen; der Listener und die anderen Verbindungen bleiben
 * unberührt. Wird die Verbindung beendet (Frist, Beenden), ist [handle] abgebrochen.
 */
internal fun interface ConnectionHandler {
    suspend fun handle(connection: Connection)
}
