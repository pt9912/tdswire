package io.github.pt9912.tdswire.protocol.response

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.domain.StatementKind
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.protocol.token.DoneCommand
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
internal fun interface TokenSink {
    suspend fun accept(token: Token): Boolean
}

/** Eine abgebildete Meldung und ob die Verbindung nach ihrer Antwort schließt (Folge ungleich „offen“). */
internal class MappedError(val message: ServerMessage, val closesConnection: Boolean)

/**
 * Die Fehlerabbildung als Funktionen, damit der Sequenzierer keinen Katalog kennt: [backend] bildet
 * ein [QueryEvent.Failure] ab, [typeMapping] einen Fehler der Metadaten- oder Wertabbildung oder ein
 * nicht abbildbares Ereignis (`TDS_UNSUPPORTED_TYPE`, `SPEC-014`), [internal] die Meldung für
 * `TDS_INTERNAL_ERROR` (`SPEC-048`). Der Sequenzierer ruft [internal] mit der Ausnahme des Backends
 * oder mit `null` bei einem Reihenfolgeverstoß; so kann der Aufrufer den Fall loggen. Die Meldung
 * enthält weder den Text der Ausnahme noch das `detail` eines [ProtocolError]; der Sequenzierer
 * schreibt beides nie selbst in den Strom, die Zusage hängt an der Abbildung.
 */
internal class ErrorMapping(
    val backend: (QueryEvent.Failure) -> MappedError,
    val typeMapping: (ProtocolError) -> MappedError,
    val internal: (Throwable?) -> MappedError,
)

/**
 * Der Ausgang eines Laufs des [ResponseSequencer]; was die Verbindung danach tut, steht je Wert. Der
 * Ausgang trägt die Verbindungsfolge vollständig: `ErrorMapper.fateOf` liefert sie, ohne die Meldung
 * im Tokenstrom zu suchen.
 */
internal enum class SequenceOutcome {
    /** Das abschließende DONE ist geschrieben; die Verbindung bleibt, wie sie war. */
    COMPLETED,

    /**
     * ERROR und DONE mit Fehlerstatus sind geschrieben (Fehler des Backends, nicht abbildbare Metadaten
     * oder Werte, nicht abbildbares Ereignis), und die Abbildung lässt die Verbindung offen
     * ([MappedError.closesConnection] ist `false`). Ob ein Fall hierher führt, bestimmt die Abbildung:
     * mit dem `ErrorMapper` ist es ein Failure mit Code der Class 16; jeder Typfehler ist Class 20 und
     * ergibt [FAILED_CLOSE_CONNECTION].
     */
    FAILED,

    /**
     * ERROR und DONE mit Fehlerstatus sind geschrieben; die Verbindung wird danach geschlossen:
     * Reihenfolgeverstoß und Ausnahme (`SPEC-048`) sowie jede Abbildung mit
     * [MappedError.closesConnection], mit dem `ErrorMapper` auch jeder Typfehler (Metadaten, Wert,
     * `ReturnStatus`, `OutputParameter`).
     */
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
 *   Dasselbe gilt für jedes andere nächste Ereignis, auch für eine Ausnahme des Stroms. Belegt am echten
 *   SQL Server ist nur das Bit vor dem ERROR eines batch-abbrechenden Fehlers: `DONE[MORE|COUNT]` hinter dem
 *   Ergebnis, dann ERROR, dann `DONE[ERR]` (`ADR-0007` Messung R05, `SELECT 1; SELECT * FROM dbo.nope; SELECT 3`;
 *   ein Fall; die Szenarien des Mitschnitts zeigen dieses Bit nicht).
 * - Die Klasse des Fehlers ([QueryEvent.Failure.scope]) bestimmt den Fortgang. `BATCH`: ERROR und ein DONE mit
 *   `ERROR` (ohne `MORE`), der Lauf endet; ebenso jeder Fehler, den die Abbildung als verbindungsschließend
 *   ausweist. `STATEMENT` (gemessen: `SELECT 1/0; SELECT 2`): ERROR und ein zurückgehaltenes DONE mit `ERROR`, das
 *   `MORE` bekommt, sobald ein Ereignis folgt; der Lauf liest weiter. Die Fehler des Servers selbst sind `BATCH`
 *   (Setzung des Nutzers, kein Messbefund).
 * - Ein zurückgehaltener Abschluss wird bei Abbruch der Coroutine (Cancel, Disconnect) verworfen,
 *   nicht geschrieben (Nutzer). `close()` der Ausführung läuft trotzdem.
 * - Ein Verstoß des Backends gegen die Reihenfolge ([QueryEvent.Row] oder [QueryEvent.ResultEnd] ohne
 *   offenes Ergebnis, [QueryEvent.ResultStart] oder [QueryEvent.UpdateCount] in einem offenen
 *   Ergebnis, Strom endet in einem offenen Ergebnis) ist ein Fall von `SPEC-048`: ERROR mit
 *   [ErrorMapping.internal] (mit `null`), DONE mit Fehlerstatus, [SequenceOutcome.FAILED_CLOSE_CONNECTION]
 *   (Nutzer). Dasselbe gilt für eine Ausnahme aus `execute()` oder aus dem Strom (Festlegung des
 *   Implementers).
 * - [ResponseForm.BATCH]: [QueryEvent.ReturnStatus] und [QueryEvent.OutputParameter] werden mit einem
 *   Fehler der Typabbildung abgelehnt ([ErrorMapping.typeMapping]) (Nutzer). Der Ausgang folgt der
 *   Abbildung: mit dem `ErrorMapper` ist die Ablehnung `TDS_UNSUPPORTED_TYPE` (Class 20) und der Ausgang
 *   [SequenceOutcome.FAILED_CLOSE_CONNECTION], die Verbindung schließt nach der Antwort; ebenso jeder
 *   andere Typfehler. Die Einzelzuordnung des Codes ist offen.
 * - [ResponseForm.RPC] (gemessen für `sp_executesql` und `sp_prepexec`, ein Fall je Instanz und Form; sonst
 *   Setzung nach derselben Regel): der Abschluss eines Ergebnisses oder Update-Zählers ist ein DONEINPROC mit `MORE`
 *   (auch wenn nur das DONEPROC folgt); die Antwort endet mit RETURNSTATUS, den RETURNVALUE in der Reihenfolge der
 *   Aufrufliste und einem DONEPROC ohne Bits. Der Sequenzierer hält RETURNSTATUS und RETURNVALUE bis zum Ende der
 *   Antwort ([RpcOutputs]); ein Ergebnis nach einem Ausgabewert steht damit vor dem RETURNSTATUS. Ohne
 *   [QueryEvent.ReturnStatus] ist der Wert 0. Ein zweiter `ReturnStatus` und ein Ereignis beider Arten in einem
 *   offenen Ergebnis sind ein Reihenfolgeverstoß; ein Typ oder Wert, den die Abbildung ablehnt, ist ein Fehler der
 *   Typabbildung.
 * - Fehler in der Form RPC (Messprotokoll W-1 bis W-3): vor dem ersten Ergebnis ERROR, RETURNSTATUS, DONEPROC mit
 *   `ERROR`; danach ERROR, DONEINPROC `MORE|ERROR`, RETURNSTATUS, RETURNVALUE, DONEPROC ohne Fehlerbit. Der
 *   RETURNSTATUS ist ohne Wert des Backends die Nummer des letzten ERROR, wenn der Fehler die Ausführung beendet hat
 *   und die Nummer unter 50000 liegt (SQL-Server-Nummer), und 0, wenn die Ausführung weiterlief; für eigene Nummern
 *   entsteht keiner.
 * - „Synchron“ (`SPEC-031` definiert den Begriff nicht): der Strom ist synchron, solange die Senke
 *   jedes Token vollständig angenommen hat. Nimmt sie ein Token nicht vollständig an, wird nichts
 *   mehr geschrieben, auch kein ERROR und kein DONE: [SequenceOutcome.CLOSE_WITHOUT_COMPLETION]
 *   (Festlegung des Implementers).
 * - Ein batch-abbrechendes [QueryEvent.Failure] beendet den Lauf; weitere Ereignisse werden nicht gelesen
 *   (Festlegung des Implementers). Ein Strom ohne Ereignis endet im Batch in einem DONE ohne Bits.
 * - `curCmd` folgt der Form des Ereignisses und der Art des Änderungszählers ([DoneCommand], Messprotokoll W-6):
 *   `ResultEnd` und ein Fehler bei offenem Ergebnis `0x00c1`, ein Fehler ohne offenes Ergebnis und ein Strom ohne
 *   Ereignis `0x00fd`, DONEPROC `0x00e0`; `UpdateCount` trägt die Kennung seiner [StatementKind] (INSERT `0x00c3`,
 *   DELETE `0x00c4`, UPDATE `0x00c5`) und bei `UNKNOWN` die Kennung 0. Die Zeilenzahl steht mit dem Bit `COUNT`, wenn
 *   das Ereignis eine Zahl trägt, sonst steht 0 ohne das Bit (Festlegung des Implementers).
 * - „Ausnahme“ heißt jedes `Throwable` außer einer [CancellationException]; die Ausnahme geht an
 *   [ErrorMapping.internal]. Eine [CancellationException] läuft aus `execute()` wie aus dem Strom
 *   nach außen, auch wenn die Coroutine nicht abgebrochen ist (etwa ein Zeitlimit im Backend).
 * - Ein `OutOfMemoryError` aus dem Backend, dem Encoder oder der Senke wird wie eine Ausnahme des
 *   Backends behandelt; gefangen wird nur dieser Fehler. Gelingt das Schreiben von ERROR und DONE
 *   nicht, ist der Strom nicht abschließbar. Fiel der Fehler im Schreiben der Senke und steht der Strom
 *   womöglich mitten in einem Token, nimmt die Senke nichts mehr an (`false`): kein ERROR, kein DONE,
 *   [SequenceOutcome.CLOSE_WITHOUT_COMPLETION].
 * - Eine andere Ausnahme der Senke als die Abbruchausnahme und `OutOfMemoryError`, ebenso eine Ausnahme
 *   aus `close()` oder `cancel()`, laufen nach `close()` unverändert nach außen; dafür gibt es keine
 *   Zusage.
 */
internal class ResponseSequencer(private val errors: ErrorMapping) {
    /**
     * Führt [request] auf [backend] aus und schreibt die Tokens der Antwortform [form] in [sink]; [layout] sagt, wo
     * die Parameter der Anfrage in der Aufrufliste des RPC stehen (Ordinal der RETURNVALUE); [transaction] ist der Zustand
     * der Verbindung, den die Arten der Transaktion nachführen (nur in der Form BATCH). `close()`
     * der Ausführung läuft in jedem Ausgang, auch beim Abbruch der Coroutine. Endet das Lesen durch den Abbruch der
     * Coroutine, durch eine Senke, die ein Token nicht annimmt, oder durch eine Ausnahme der Senke,
     * ruft der Lauf zuvor genau einmal `cancel()` des Backends; kein weiteres Ereignis wird danach
     * gelesen.
     */
    suspend fun run(
        backend: QueryBackend,
        request: QueryRequest,
        sink: TokenSink,
        form: ResponseForm = ResponseForm.BATCH,
        layout: CallLayout = CallLayout.PROCEDURE,
        transaction: SessionTransaction = SessionTransaction(),
    ): SequenceOutcome {
        val run = ResponseRun(errors, sink, form, RpcOutputs(layout, request.parameters), transaction)
        val started = runCatching { backend.execute(request) }
        val cause = started.exceptionOrNull()
        if (cause != null) {
            if (cause is CancellationException) throw cause
            run.fault(cause)
            return run.result
        }
        val execution = started.getOrThrow()
        var aborted = true
        try {
            execution.events.catch { run.fault(rethrowCancellation(it)) }.firstOrNull { acceptGuarded(run, it) }
            endGuarded(run)
            aborted = run.result == SequenceOutcome.CLOSE_WITHOUT_COMPLETION
        } finally {
            withContext(NonCancellable) {
                try {
                    if (aborted) backend.cancel(request.sessionId, request.requestId)
                } finally {
                    execution.close()
                }
            }
        }
        return run.result
    }

    /**
     * Ein `OutOfMemoryError` aus Encoder oder Senke endet den Lauf wie eine Ausnahme des Backends
     * (`ResponseRun.fault`). Andere `Error` und die Abbruchausnahme laufen nach außen.
     */
    private suspend fun acceptGuarded(run: ResponseRun, event: QueryEvent): Boolean = try {
        run.accept(event)
    } catch (exhausted: OutOfMemoryError) {
        run.fault(exhausted)
        true
    }

    private suspend fun endGuarded(run: ResponseRun) {
        try {
            run.end()
        } catch (exhausted: OutOfMemoryError) {
            run.fault(exhausted)
        }
    }

    private fun rethrowCancellation(cause: Throwable): Throwable =
        if (cause is CancellationException) throw cause else cause
}
