package io.github.pt9912.tdswire.runtime

import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import kotlin.system.exitProcess

/** Exit-Code eines abgebrochenen Starts (`TDS-FA-09.a`: fehlerhafte Konfiguration führt zu Startabbruch). */
internal const val EXIT_START_FAILED = 1

/**
 * Der Serverprozess (`ARC-008`). Er liest die Umgebung, startet nach [Bootstrap] und bleibt laufen, bis die
 * JVM beendet wird (SIGTERM oder SIGINT); der Shutdown-Hook ruft [Server.stop]. Ein abgebrochener Start
 * beendet den Prozess mit [EXIT_START_FAILED]; bis dahin ist kein Thread des Servers gestartet.
 * Der Start setzt `-Djdk.tls.server.newSessionTicketCount=0` nicht selbst, er verlangt es (siehe [Bootstrap]).
 */
fun main() {
    val wiring = Wiring(log = ServerLog.STDOUT)
    when (val boot = runBlocking { Bootstrap.start(System.getenv(), System::getProperty, wiring) }) {
        BootResult.Aborted -> exitProcess(EXIT_START_FAILED)

        is BootResult.Running -> {
            Runtime.getRuntime().addShutdownHook(Thread { runBlocking { boot.server.stop() } })
            CountDownLatch(1).await()
        }
    }
}
