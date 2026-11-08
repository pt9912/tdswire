package io.github.pt9912.tdswire.simulation

/**
 * Die Texte der Transaktionssteuerung, die d-migrate 1.12.0 beim Schreiben einer Struktur sendet (`SPEC-036`, Form C):
 * `IF @@TRANCOUNT > 0 COMMIT TRAN`, `IF @@TRANCOUNT > 0 ROLLBACK TRAN`, jeweils auch mit einem vorangestellten
 * `set implicit_transactions on|off` im selben Text ohne `;` (gemessen: `set implicit_transactions off IF
 * @@TRANCOUNT > 0 COMMIT TRAN` und `IF @@TRANCOUNT > 0 ROLLBACK TRAN`; die übrigen Kombinationen sind Setzung).
 * Erkannt wird der Wortlaut nach [CatalogForms.normalize], ohne Beachtung der ASCII-Großschreibung; die Antwort ist
 * der Änderungszähler 0. Die Bibliothek hat keine Transaktion: `ROLLBACK TRAN` nimmt nichts zurück (`ADR-0033` Nr. 6).
 */
internal object TransactionForms {
    private val FORM = Regex(
        "(?:SET IMPLICIT_TRANSACTIONS (ON|OFF) )?IF @@TRANCOUNT > 0 (COMMIT|ROLLBACK) TRAN",
        RegexOption.IGNORE_CASE,
    )

    /** Die Form des Textes oder `null`, wenn er keine Transaktionssteuerung ist. */
    fun recognize(sql: String): FixedForm.Transaction? {
        val found = FORM.matchEntire(CatalogForms.normalize(sql)) ?: return null
        val implicit = found.groupValues[1].takeIf { it.isNotEmpty() }?.let { it.equals("ON", ignoreCase = true) }
        return FixedForm.Transaction(implicit, found.groupValues[2].equals("COMMIT", ignoreCase = true))
    }
}
