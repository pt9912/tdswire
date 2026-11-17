package io.github.pt9912.tdswire.protocol.response

import io.github.pt9912.tdswire.protocol.token.DoneCommand
import io.github.pt9912.tdswire.protocol.token.DoneKind
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.DoneToken
import io.github.pt9912.tdswire.protocol.token.ReturnStatusToken
import io.github.pt9912.tdswire.protocol.token.Token

/**
 * Das Ende einer RPC-Antwort (Messprotokoll W-3 und W-5): RETURNSTATUS, die RETURNVALUE der [outputs] in der
 * Reihenfolge der Aufrufliste und das DONEPROC. [sink] schreibt ein Token in die Senke des Laufs und meldet, ob sie es
 * annahm. Der Wert des RETURNSTATUS hängt am letzten Fehler ([record]): hat er die Ausführung beendet, ist es seine
 * SQL-Server-Nummer (eigene Nummern ab [FIRST_OWN_ERROR_NUMBER]: kein RETURNSTATUS, auch nicht der des Backends);
 * sonst der `ReturnStatus` des Backends, ohne ihn 0. Folgt ein weiterer RPC derselben Nachricht ([more]), trägt das
 * DONEPROC die Bits `MORE` und `RPC_IN_BATCH` (gemessen mit `UpdateBatchSize` 2, Messreihe am Draht `rpc-batch-*`,
 * 2022 und 2025: `MORE|0x80` am DONEPROC jedes Aufrufs außer dem letzten, keine Bits am letzten; ein DONEPROC mit
 * `ERROR` vor dem ersten Ergebnis trägt sie ebenso, Setzung ohne Messung).
 */
internal class RpcTrailer(
    private val outputs: RpcOutputs,
    private var more: Boolean = false,
    private val sink: suspend (Token) -> Boolean,
) {
    /** Nummer des letzten ERROR und ob die Ausführung danach weiterlief; `null` ohne Fehler. */
    private var lastError: Pair<Int, Boolean>? = null

    /** Wahr, sobald ein ERROR geschrieben wurde. */
    val failed: Boolean get() = lastError != null

    /** Die Antwort endet hier, auch wenn weitere RPCs folgen sollten (die Verbindung schließt): kein `MORE` mehr. */
    fun endsMessage() {
        more = false
    }

    /** Merkt den ERROR mit der Nummer [number]; [continued] ist wahr, wenn die Ausführung danach weiterläuft. */
    fun record(number: Int, continued: Boolean) {
        lastError = number to continued
    }

    /**
     * DONEINPROC `MORE|ERROR` für die Anweisung, in der der Fehler auftrat, danach das Ende der Antwort. Bei offenem
     * Ergebnis trägt es die Kennung [resultCommand] ([DoneCommand.forResult]), sonst keine.
     */
    suspend fun afterResult(resultOpen: Boolean, resultCommand: Int = DoneCommand.SELECT): Boolean {
        val command = if (resultOpen) resultCommand else DoneCommand.NONE
        val statementDone = DoneToken(DoneKind.DONE_IN_PROC, DoneStatus.MORE or DoneStatus.ERROR, command, 0)
        return sink(statementDone) && finish(errorBit = false)
    }

    /** RETURNSTATUS, RETURNVALUE und DONEPROC, mit `ERROR` am DONEPROC, wenn [errorBit] gesetzt ist. */
    suspend fun finish(errorBit: Boolean): Boolean {
        val batch = if (more) DoneStatus.MORE or DoneStatus.RPC_IN_BATCH else DoneStatus.FINAL
        val doneProc = DoneToken(
            DoneKind.DONE_PROC,
            (if (errorBit) DoneStatus.ERROR else DoneStatus.FINAL) or batch,
            DoneCommand.PROC,
            0,
        )
        val tokens = listOfNotNull<Token>(statusValue()?.let { ReturnStatusToken(it) }) + outputs.returnValues() +
            doneProc
        return tokens.all { sink(it) }
    }

    private fun statusValue(): Int? {
        val (number, continued) = lastError ?: return outputs.status ?: 0
        return when {
            continued -> outputs.status ?: 0
            number < FIRST_OWN_ERROR_NUMBER -> number
            else -> null
        }
    }
}
