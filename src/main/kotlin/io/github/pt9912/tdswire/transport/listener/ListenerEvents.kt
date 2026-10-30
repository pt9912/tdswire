package io.github.pt9912.tdswire.transport.listener

/** Wie eine Verbindung endet; die erste Ursache zählt. */
enum class ConnectionEnd {
    /** Der Handler ist zurückgekehrt (auch nach dem Trennen durch die Gegenstelle). */
    HANDLER_RETURNED,

    /**
     * Der Handler hat eine Ausnahme geworfen, auch eine `CancellationException` bei unabgebrochenem Scope
     * der Verbindung; [ListenerEvent.Closed.failure] nennt ihren Typ.
     */
    HANDLER_FAILED,

    /** `LOGIN_TIMEOUT` ist abgelaufen, bevor der Handler „Login abgeschlossen“ meldete (`SPEC-008`). */
    LOGIN_TIMEOUT,

    /** Das geordnete Beenden hat die Verbindung beendet: ohne laufende Anfrage oder nach deren Ende (`SPEC-044`). */
    SHUTDOWN,

    /** `SHUTDOWN_GRACE` ist abgelaufen, die Verbindung wurde getrennt (`SPEC-044`). */
    SHUTDOWN_GRACE_EXPIRED,

    /** Die Verbindung wurde im Moment des Beginns des Beendens angenommen und sofort geschlossen (`SPEC-044`). */
    REFUSED_AT_SHUTDOWN,
}

/** Ereignisse des Listeners; sie tragen weder Adressen der Gegenstelle noch Nutzdaten. */
sealed interface ListenerEvent {
    data class Accepted(val connection: Long) : ListenerEvent

    /** [failure] ist der Typname der Ausnahme bei [ConnectionEnd.HANDLER_FAILED], sonst `null`; nie ihr Text. */
    data class Closed(val connection: Long, val end: ConnectionEnd, val failure: String?) : ListenerEvent

    /** Annehmen scheiterte, der Listener läuft weiter; [failure] ist der Typname der Ausnahme. */
    data class AcceptFailed(val failure: String) : ListenerEvent
}

/**
 * Empfänger der [ListenerEvent]s. Der Aufruf kommt aus beliebigen Threads, blockiert nicht und wirft
 * nicht; die Anbindung an Telemetrie liefert `observability`.
 */
fun interface ListenerEvents {
    fun onEvent(event: ListenerEvent)

    companion object {
        val NONE = ListenerEvents { }
    }
}
