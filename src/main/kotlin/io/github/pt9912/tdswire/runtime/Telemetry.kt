package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.domain.telemetry.TelemetryEvent
import io.github.pt9912.tdswire.domain.telemetry.TelemetrySink
import java.time.Clock
import java.time.Instant
import java.util.concurrent.CancellationException

/**
 * Der Weg jedes Ereignisses zur Senke (`SPEC-059`): stempelt den Zeitpunkt aus [clock] und ruft [sink]. Ohne Senke
 * ([TelemetrySink.NONE]) baut [emit] kein Ereignis und ruft nichts; liefert die Bauvorschrift `null`, entsteht
 * keines. Eine `Exception` der Senke wird gefangen und verworfen; bei einer `InterruptedException` bleibt das Flag der
 * Unterbrechung gesetzt. Eine `CancellationException` und jeder `Error` laufen zum Aufrufer (Vertrag in
 * [TelemetrySink]).
 */
internal class Telemetry(private val sink: TelemetrySink, private val clock: Clock = Clock.systemUTC()) {
    fun emit(build: (Instant) -> TelemetryEvent?) {
        if (sink === TelemetrySink.NONE) return
        val event = build(clock.instant()) ?: return
        try {
            sink.accept(event)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (interrupted: InterruptedException) {
            // Die Unterbrechung des Threads bleibt sichtbar; das Ereignis geht verloren.
            Thread.currentThread().interrupt()
        } catch (ignored: Exception) {
            // Die Senke hat ihren Vertrag gebrochen; das Ereignis geht verloren, der Anfragepfad läuft weiter.
        }
    }
}
