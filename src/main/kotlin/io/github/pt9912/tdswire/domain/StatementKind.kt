package io.github.pt9912.tdswire.domain

/**
 * Die Art einer Anweisung, die ein Backend im [QueryEvent.UpdateCount] meldet, bei [MERGE] im [QueryEvent.ResultStart]
 * (`SPEC-031`, `SPEC-059`).
 * Die Bibliothek leitet daraus die Befehlskennung (`curCmd`) des Abschlusses ab; die Kennungen sind am echten SQL
 * Server gemessen (Messprotokoll W-6 und M-20, Instanz 2022 und 2025, je ein Fall). Das Mitglied [UNKNOWN] ist der
 * Wert eines Backends, das keine Art meldet: Kennung 0 mit Zeilenzahl. Bei [CREATE_TABLE], [ALTER_TABLE],
 * [CREATE_INDEX] und [DROP_INDEX] schließt der Server ohne Zeilenzahl ab; die Bibliothek ignoriert dort die Zahl des
 * Ereignisses.
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

    /** `CREATE TABLE`: Kennung `0x00c6`, ohne Zeilenzahl (Messprotokoll M-20). */
    CREATE_TABLE,

    /**
     * `ALTER TABLE … ADD` und `ALTER TABLE … DROP CONSTRAINT`: Kennung `0x00d8`, ohne Zeilenzahl (Messprotokoll M-20;
     * `DROP CONSTRAINT`: Szenario `struktur-drop-constraint`).
     */
    ALTER_TABLE,

    /** `set implicit_transactions on`: Kennung `0x00b9`, ohne Zeilenzahl (Messprotokoll M-20). */
    IMPLICIT_TRANSACTIONS_ON,

    /**
     * Erster Abschluss des Textes `set implicit_transactions off IF @@TRANCOUNT > 0 COMMIT TRAN`: Kennung `0x00ba`,
     * ohne Zeilenzahl (Messprotokoll M-20). Der Text `set implicit_transactions off` allein ist nicht gemessen; dafür
     * gibt es keine Art, und ein Backend meldet [UNKNOWN].
     */
    IMPLICIT_TRANSACTIONS_OFF,

    /**
     * `IF @@TRANCOUNT > 0 COMMIT TRAN`: Kennung `0x00c0`, ohne Zeilenzahl (Messprotokoll M-20); der Abschluss der
     * Bedingung, nicht des `COMMIT`.
     */
    COMMIT_IF_OPEN,

    /**
     * `MERGE … OUTPUT $action`: Kennung `0x0117` am Abschluss des Ergebnisses und eines Fehlers in ihm, mit Zähler
     * (Messprotokoll M-13, M-15 und M-26). Das Backend meldet sie im [QueryEvent.ResultStart]; die Antwort ist ein
     * Ergebnis, kein Änderungszähler.
     */
    MERGE,

    /**
     * `CREATE INDEX` und `CREATE UNIQUE INDEX`: Kennung `0x00c8`, ohne Zeilenzahl (am echten SQL Server gemessen für
     * einen gewöhnlichen, einen eindeutigen und einen Index mit zwei Spalten; `INCLUDE`, Filter und absteigende
     * Spalte sind nicht gemessen). Messprotokoll `drop-index-d-migrate-2026-11-11`.
     */
    CREATE_INDEX,

    /**
     * `DROP INDEX` und `DROP INDEX IF EXISTS`: Kennung `0x00c9`, ohne Zeilenzahl, bei Treffer, bei `IF EXISTS` ohne
     * Treffer und an einer unbekannten Tabelle mit `IF EXISTS` (Messprotokoll `drop-index-d-migrate-2026-11-11`).
     */
    DROP_INDEX,
}
