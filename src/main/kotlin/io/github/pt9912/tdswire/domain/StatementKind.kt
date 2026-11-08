package io.github.pt9912.tdswire.domain

/**
 * Die Art einer Anweisung ohne Ergebnis, die ein Backend im [QueryEvent.UpdateCount] meldet (`SPEC-031`, `SPEC-059`).
 * Die Bibliothek leitet daraus die Befehlskennung (`curCmd`) des Abschlusses ab; die Kennungen sind am echten SQL
 * Server gemessen (Messprotokoll W-6, Instanz 2022 und 2025, je ein Fall). Das Mitglied [UNKNOWN] ist der Wert eines
 * Backends, das keine Art meldet: Kennung 0 mit Zeilenzahl.
 */
public enum class StatementKind {
    /** Keine Aussage über die Art: der Abschluss trägt die Kennung 0. */
    UNKNOWN,

    /** `INSERT`: Kennung `0x00c3`. */
    INSERT,

    /** `DELETE`: Kennung `0x00c4`. */
    DELETE,

    /** `UPDATE`: Kennung `0x00c5`. */
    UPDATE,
}
