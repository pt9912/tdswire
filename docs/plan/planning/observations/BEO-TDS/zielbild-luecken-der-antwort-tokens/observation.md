# Zielbild-Lücken der Antwort-Tokens: Flag-Feld des Ausgabewerts, `curCmd` des Update-Zählers, `curCmd` nach Fristablauf

**Sub-Area:** `*` (Sequenzierer, Tokenstrom)

Drei Abweichungen des Servers vom echten SQL Server sind gemessen oder benannt und haben keinen Träger, der die Closure von [slice-antwort-tokens-an-messung-angleichen](../../../done/slice-antwort-tokens-an-messung-angleichen.md) überlebt (Maßstab: Spezifikation ist Zielbild, der echte Server die Referenz; [TDS-QA-01](../../../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)):

1. **Flag-Feld des RETURNVALUE (A-20).** Der Server schreibt es für jeden Ausgabewert des Backends mit `0x0001`; am echten Server steht `0x0000` (22 von 22 Treffern je Instanz, Typ `INTN` 4; NULL und andere Typen nicht gemessen). Das Handle von `sp_prepexec` schreibt der Server mit `0x0000`.
2. **`curCmd` des Update-Zählers.** Der Sequenzierer schreibt 0 für den Abschluss eines `UpdateCount` und für `EXEC sp_unprepare`; am echten Server sind `0x00c3` (INSERT), `0x00c5` (UPDATE) und `0x00c4` (DELETE) gemessen (W-6), für `EXEC sp_unprepare` (DONEINPROC ohne Ergebnis) steht der Wert des echten Servers nicht im Messprotokoll. Der Port trägt keine Anweisungsart.
3. **`curCmd` nach Fristablauf bei offenem Ergebnis.** Der Server schreibt `0x00fd` (DONE) bzw. `0x00e0` (DONEPROC); am echten Server steht `0x00c1`.
