package io.github.pt9912.tdswire.protocol.response

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.port.QueryExecution
import io.github.pt9912.tdswire.protocol.token.ServerMessage
import io.github.pt9912.tdswire.protocol.token.Token
import io.github.pt9912.tdswire.protocol.wire.ProtocolError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

/**
 * Wohin der Sequenzierer seine Tokens schreibt. [accept] liefert `true`, wenn das Token vollständig
 * angenommen ist, und `false`, wenn es nicht (mehr) vollständig geschrieben werden konnte; die
 * Senke wirft nur die Abbruchausnahme der Coroutine (Disconnect), keine andere Ausnahme.
 */
fun interface TokenSink {
    suspend fun accept(token: Token): Boolean
}

/**
 * Die Fehlerabbildung als Funktionen, damit der Sequenzierer keinen Katalog kennt: [backend] bildet
 * ein [QueryEvent.Failure] ab, [typeMapping] einen Fehler der Metadaten- oder Wertabbildung oder ein
 * nicht abbildbares Ereignis (`TDS_UNSUPPORTED_TYPE`, `SPEC-014`), [internal] ist die feste Meldung
 * für `TDS_INTERNAL_ERROR` (`SPEC-048`; ohne den Text einer Ausnahme).
 */
class ErrorMapping(
    val backend: (QueryEvent.Failure) -> ServerMessage,
    val typeMapping: (ProtocolError) -> ServerMessage,
    val internal: ServerMessage,
)

/** Der Ausgang eines Laufs des [ResponseSequencer]; was die Verbindung danach tut, steht je Wert. */
enum class SequenceOutcome {
    /** Das abschließende DONE ist geschrieben; die Verbindung bleibt, wie sie war. */
    COMPLETED,

    /**
     * ERROR und DONE mit Fehlerstatus sind geschrieben (Fehler des Backends, nicht abbildbare Metadaten
     * oder Werte, nicht abbildbares Ereignis). Ob die Verbindung bleibt, folgt aus der Severity der
     * Meldung, nicht aus dem Sequenzierer.
     */
    FAILED,

    /** ERROR und DONE mit Fehlerstatus sind geschrieben; die Verbindung wird danach geschlossen (`SPEC-048`). */
    FAILED_CLOSE_CONNECTION,

    /** Der Strom ließ sich nicht mehr abschließen; die Verbindung wird ohne ERROR und DONE geschlossen (`SPEC-031`). */
    CLOSE_WITHOUT_COMPLETION,
}

/**
 * Response Sequencer (`SPEC-031`, `TDS-FA-05.a` Schritt 2 bis 4): übersetzt den Ereignisstrom eines
 * Backends in die Tokenfolge COLMETADATA, ROW und DONE. Er hält ein `ResultEnd` oder `UpdateCount`
 * genau ein Ereignis lang zurück, setzt daraus `DONE_MORE` und kennt weder Sitzung noch
 * Anfragezustand.
 *
 * Festlegungen (nicht gemessen; Setzungen des Nutzers vom 2026-10-30, wo genannt):
 * - Ein [QueryEvent.Failure] zählt in der Vorschau als nächstes Ereignis: ein zurückgehaltener
 *   Abschluss bekommt `DONE_MORE`, danach folgen ERROR und ein DONE mit Fehlerstatus (Nutzer).
 *   Dasselbe gilt für jedes andere nächste Ereignis, auch für eine Ausnahme des Stroms.
 * - Ein zurückgehaltener Abschluss wird bei Abbruch der Coroutine (Cancel, Disconnect) verworfen,
 *   nicht geschrieben (Nutzer). `close()` der Ausführung läuft trotzdem.
 * - Ein Verstoß des Backends gegen die Reihenfolge ([QueryEvent.Row] oder [QueryEvent.ResultEnd] ohne
 *   offenes Ergebnis, [QueryEvent.ResultStart] oder [QueryEvent.UpdateCount] in einem offenen
 *   Ergebnis, Strom endet in einem offenen Ergebnis) ist ein Fall von `SPEC-048`: ERROR mit
 *   [ErrorMapping.internal], DONE mit Fehlerstatus, [SequenceOutcome.FAILED_CLOSE_CONNECTION]
 *   (Nutzer). Dasselbe gilt für eine Ausnahme aus `execute()` oder aus dem Strom (Festlegung des
 *   Implementers).
 * - [QueryEvent.ReturnStatus] und [QueryEvent.OutputParameter] werden mit einem Fehler der
 *   Typabbildung abgelehnt ([ErrorMapping.typeMapping], [SequenceOutcome.FAILED]); sie werden nicht auf
 *   RETURNSTATUS oder RETURNVALUE abgebildet (Nutzer).
 * - „Synchron“ (`SPEC-031` definiert den Begriff nicht): der Strom ist synchron, solange die Senke
 *   jedes Token vollständig angenommen hat. Nimmt sie ein Token nicht vollständig an, wird nichts
 *   mehr geschrieben, auch kein ERROR und kein DONE: [SequenceOutcome.CLOSE_WITHOUT_COMPLETION]
 *   (Festlegung des Implementers).
 * - [QueryEvent.Failure] beendet den Lauf; weitere Ereignisse werden nicht gelesen (Festlegung des
 *   Implementers). Ein Strom ohne Ereignis endet in einem DONE ohne Bits (Festlegung des
 *   Implementers).
 * - `curCmd` aller DONE ist 0: der Port trägt keine Befehlskennung (benannte Spec-Lücke); die
 *   Zeilenzahl steht mit dem Bit `COUNT`, wenn das Ereignis eine Zahl trägt, sonst steht 0 ohne das
 *   Bit (Festlegung des Implementers).
 * - „Ausnahme“ heißt jedes `Throwable` außer der Abbruchausnahme der Coroutine; die Behandlung von
 *   `OutOfMemoryError` bleibt offen (ADR-0006).
 * - Eine andere Ausnahme der Senke als die Abbruchausnahme und eine Ausnahme aus `close()` laufen nach
 *   `close()` unverändert nach außen; dafür gibt es keine Zusage.
 */
class ResponseSequencer(private val errors: ErrorMapping) {
    /**
     * Führt [request] auf [backend] aus und schreibt die Tokens in [sink]. `close()` der Ausführung
     * läuft in jedem Ausgang, auch beim Abbruch der Coroutine.
     */
    suspend fun run(backend: QueryBackend, request: QueryRequest, sink: TokenSink): SequenceOutcome {
        val run = ResponseRun(errors, sink)
        val execution = start(backend, request)
        if (execution == null) {
            run.fault()
            return run.result
        }
        try {
            execution.events.catch { run.fault() }.firstOrNull { run.accept(it) }
            run.end()
        } finally {
            withContext(NonCancellable) { execution.close() }
        }
        return run.result
    }

    private suspend fun start(backend: QueryBackend, request: QueryRequest): QueryExecution? {
        val started = runCatching { backend.execute(request) }
        val cause = started.exceptionOrNull()
        if (cause is CancellationException) throw cause
        return started.getOrNull()
    }
}
