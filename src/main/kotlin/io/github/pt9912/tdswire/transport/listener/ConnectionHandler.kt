package io.github.pt9912.tdswire.transport.listener

import io.github.pt9912.tdswire.transport.channel.SuspendingChannel

/**
 * Was der Listener einem [ConnectionHandler] von seiner Verbindung gibt (`ARC-003`): den rohen
 * Kanal und zwei Meldungen. Verschlüsselung, Pakete und Protokollzustände gehören dem Handler; der
 * Transport kennt nur diese zwei Zustände.
 *
 * Festlegung (Form der Schnittstelle, nicht gemessen): die Meldungen sind die einzigen, die der
 * Listener braucht, um `LOGIN_TIMEOUT` (`SPEC-008`) und das geordnete Beenden (`SPEC-044`) zu halten.
 * Die Protokollschicht darf die Form ändern.
 */
interface Connection {
    /** Laufende Nummer dieser Verbindung im Listener; sie taucht in den [ListenerEvent]s auf. */
    val id: Long

    /** Der verbundene Kanal der Gegenstelle ohne TLS; der Listener schließt ihn mit der Verbindung. */
    val channel: SuspendingChannel

    /** Meldet „Login abgeschlossen“: `LOGIN_TIMEOUT` gilt für diese Verbindung nicht mehr. */
    fun loginCompleted()

    /**
     * Meldet, ob eine Anfrage läuft ([running] = `true`) oder nicht. Auch eine unvollständig empfangene
     * Nachricht ist hier „läuft“: der Transport sieht Nachrichtengrenzen nicht, `SPEC-044` Nr. 2
     * behandelt beide gleich. Ist das geordnete Beenden im Gang, endet die Verbindung mit der Meldung `false`.
     */
    fun requestRunning(running: Boolean)
}

/**
 * Nimmt eine angenommene Verbindung entgegen und läuft in deren Coroutine-Scope. Kehrt [handle] zurück
 * oder wirft, wird die Verbindung geschlossen; der Listener und die anderen Verbindungen bleiben
 * unberührt. Wird die Verbindung beendet (Frist, Beenden), ist [handle] abgebrochen.
 */
fun interface ConnectionHandler {
    suspend fun handle(connection: Connection)
}
