package io.github.pt9912.tdswire.protocol.response

import io.github.pt9912.tdswire.domain.StatementKind

/**
 * Der Zustand der Transaktion, den die Verbindung einer Sitzung führt (ADR-0042 Entscheidung 5): ob
 * `implicit_transactions` an ist und ob der Client eine Transaktion offen hat. Die Bibliothek hat keine Transaktion
 * (ADR-0033 Entscheidung 6); der Zustand sagt nur, welche ENVCHANGE-Token der Client bekommen hat. Er hängt allein an
 * den Arten, die das Backend meldet ([apply]), und endet mit der Verbindung ([reset] bei RESETCONNECTION, Setzung).
 * Gemessen sind nur die Übergänge von [apply] (Messprotokoll M-20); jede andere Art lässt den Zustand unverändert.
 * Eine Instanz gehört einer Verbindung, deren Anfragen nacheinander laufen.
 */
internal class SessionTransaction {
    private var implicit = false
    private var open = false

    /** Was der Abschluss der Art außer dem DONE in den Strom schreibt. */
    enum class Transition {
        /** Nur das DONE. */
        NONE,

        /** ENVCHANGE Typ 8 vor dem DONE: `CREATE TABLE` bei `implicit_transactions on` ohne offene Transaktion. */
        BEGUN,

        /** DONE `MORE`, ENVCHANGE Typ 9, DONE des `COMMIT`: der bedingte `COMMIT` bei offener Transaktion. */
        COMMITTED,
    }

    /** Führt den Zustand um den Abschluss der Art [kind] nach und liefert, was der Strom dazu bekommt. */
    fun apply(kind: StatementKind): Transition = when (kind) {
        StatementKind.IMPLICIT_TRANSACTIONS_ON -> {
            implicit = true
            Transition.NONE
        }

        StatementKind.IMPLICIT_TRANSACTIONS_OFF -> {
            implicit = false
            Transition.NONE
        }

        StatementKind.CREATE_TABLE -> if (implicit && !open) {
            open = true
            Transition.BEGUN
        } else {
            Transition.NONE
        }

        StatementKind.COMMIT_IF_OPEN -> if (open) {
            open = false
            Transition.COMMITTED
        } else {
            Transition.NONE
        }

        else -> Transition.NONE
    }

    /** Setzt den Zustand zurück (RESETCONNECTION). */
    fun reset() {
        implicit = false
        open = false
    }

    internal companion object {
        /**
         * Der Deskriptor in ENVCHANGE Typ 8 und 9: die Bytes `01 00 00 00 35 00 00 00` der Instanz 2022 (Messprotokoll
         * M-20; die Instanz 2025 sendet `... 33 ...`). Der Server wählt den Wert je Transaktion, der Client sendet ihn
         * im Kopf der folgenden Anfragen zurück; die Bibliothek liest ihn dort nicht. Der Wert ist Setzung.
         */
        const val DESCRIPTOR = 0x0000_0035_0000_0001L
    }
}
