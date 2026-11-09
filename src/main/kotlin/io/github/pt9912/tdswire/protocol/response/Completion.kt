package io.github.pt9912.tdswire.protocol.response

import io.github.pt9912.tdswire.domain.StatementKind
import io.github.pt9912.tdswire.protocol.token.DoneCommand
import io.github.pt9912.tdswire.protocol.token.DoneKind
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.DoneToken
import io.github.pt9912.tdswire.protocol.token.EnvChange
import io.github.pt9912.tdswire.protocol.token.Token

/**
 * Der Abschluss einer Anweisung: [before] steht im Strom vor dem Abschluss, [done] ist der Abschluss, den der Lauf
 * ein Ereignis lang zurückhält (`MORE` setzt der Lauf, sobald ein Ereignis folgt).
 */
internal class Completion(val before: List<Token>, val done: DoneToken) {
    companion object {
        /**
         * Der Abschluss eines Ergebnisses der Art [kind] ([resultEnd]; die Kennung folgt [DoneCommand.forResult]) oder
         * eines Änderungszählers der Art [kind] mit der Zahl [affectedRows] in der Antwortform [form] (`SPEC-031`,
         * Messprotokoll W-6, M-20 und M-26): das Bit `COUNT` und die Kennung folgen der Art; die Arten ohne
         * Zeilenzahl gelten nur im SQL_BATCH, in den Formen RPC und BULK gilt die Art wie `UNKNOWN`. Im SQL_BATCH führt
         * [transaction] den Zustand der Verbindung nach und bestimmt, ob ENVCHANGE Typ 8 oder Typ 9 zum Abschluss
         * gehört.
         */
        fun of(
            form: ResponseForm,
            resultEnd: Boolean,
            kind: StatementKind,
            affectedRows: Long?,
            transaction: SessionTransaction,
        ): Completion {
            val doneKind = if (form == ResponseForm.RPC) DoneKind.DONE_IN_PROC else DoneKind.DONE
            if (resultEnd) {
                val command = DoneCommand.forResult(kind)
                return Completion(emptyList(), done(doneKind, command, affectedRows, counted = true))
            }
            val effective = effectiveKind(form, kind)
            val transition = if (form == ResponseForm.BATCH) transaction.apply(effective) else null
            val command = when {
                form == ResponseForm.BULK -> DoneCommand.INSERT_BULK
                transition == SessionTransaction.Transition.COMMITTED -> DoneCommand.COMMIT_TRAN
                else -> DoneCommand.forKind(effective)
            }
            val counted = DoneCommand.countsRows(effective)
            return Completion(before(transition), done(doneKind, command, affectedRows, counted))
        }

        /** Nur der SQL_BATCH kennt die Arten ohne Zeilenzahl (Messprotokoll M-20); die übrigen Formen: `UNKNOWN`. */
        private fun effectiveKind(form: ResponseForm, kind: StatementKind): StatementKind = when {
            form == ResponseForm.BATCH || DoneCommand.countsRows(kind) -> kind
            else -> StatementKind.UNKNOWN
        }

        private fun done(kind: DoneKind, command: Int, affectedRows: Long?, counted: Boolean): DoneToken {
            val rows = affectedRows.takeIf { counted }
            return DoneToken(kind, if (rows != null) DoneStatus.COUNT else DoneStatus.FINAL, command, rows ?: 0)
        }

        /** Die Tokens vor dem Abschluss: bei Beginn ENVCHANGE Typ 8, beim `COMMIT` DONE `MORE` und ENVCHANGE Typ 9. */
        private fun before(transition: SessionTransaction.Transition?): List<Token> = when (transition) {
            SessionTransaction.Transition.BEGUN -> listOf(EnvChange.BeginTransaction(SessionTransaction.DESCRIPTOR))

            SessionTransaction.Transition.COMMITTED -> listOf(
                DoneToken(DoneKind.DONE, DoneStatus.MORE, DoneCommand.forKind(StatementKind.COMMIT_IF_OPEN), 0),
                EnvChange.CommitTransaction(SessionTransaction.DESCRIPTOR),
            )

            else -> emptyList()
        }
    }
}
