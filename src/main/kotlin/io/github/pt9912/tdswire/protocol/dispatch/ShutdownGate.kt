package io.github.pt9912.tdswire.protocol.dispatch

/**
 * Was die Anfrageschleife vom geordneten Beenden des Servers weiß und ihm meldet (`SPEC-044`). Der Protokollkern
 * kennt den Transport nicht; die Verdrahtung setzt diese Schnittstelle auf die Verbindung des Listeners um.
 *
 * - [begun]: wahr ab dem Beginn des Beendens; eine Anfrage, die danach eintrifft, beantwortet die Schleife mit
 *   `TDS_SHUTDOWN`, ohne das Backend zu rufen.
 * - [requestRunning]: die Schleife meldet, dass eine angenommene Anfrage läuft (`true`) oder beendet ist (`false`).
 *   Ohne laufende Anfrage endet die Verbindung im Beenden sofort, mit laufender nach deren Ende.
 * - [onGraceExpired]: die Schleife meldet, wie sie die laufende Anfrage nach Ablauf von `SHUTDOWN_GRACE` abbricht
 *   (Nr. 4: wie bei ATTENTION).
 */
internal interface ShutdownGate {
    val begun: Boolean

    fun requestRunning(running: Boolean)

    fun onGraceExpired(abort: suspend () -> Unit)

    companion object {
        /** Ein Server ohne geordnetes Beenden: es beginnt nie, und die Meldungen haben keine Wirkung. */
        val NONE: ShutdownGate = object : ShutdownGate {
            override val begun: Boolean get() = false

            override fun requestRunning(running: Boolean) = Unit

            override fun onGraceExpired(abort: suspend () -> Unit) = Unit
        }
    }
}
