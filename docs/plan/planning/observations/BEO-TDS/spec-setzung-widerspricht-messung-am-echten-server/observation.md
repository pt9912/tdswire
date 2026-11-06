# Vier Setzungen der Spezifikation widersprechen der Messung am echten SQL Server

**Sub-Area:** `*` (Spezifikation, Response Sequencer)

Die Messung des Mitschnitt-Laufs (`docs/messungen/referenzabgleich.md`, Ergebnis 2, 3 und 5; je ein Fall je Instanz, 2022 und 2025) widerspricht vier Setzungen: (1) Ordinal der RETURNVALUE (Spec: nach der Reihenfolge der Ereignisse; gemessen: Position in der Aufrufliste, 2 für den dritten Parameter von `sp_executesql`), (2) `curCmd` der DONE-Familie (Spec: 0; gemessen: `0x00c1`, `0x00fd`, `0x00e0`), (3) `MORE` bei statement-abbrechendem Fehler (Spec: Variante ohne `MORE`; gemessen: `MORE|ERROR`; geführt in `BEO-TDS/done-more-statement-abbrechender-fehler-weicht-ab`), (4) die RETURNSTATUS-Regel (Spec: ohne `ReturnStatus` des Backends keiner; gemessen: RETURNSTATUS 0 bei jedem gemessenen RPC). Maßstab des Nutzers: die Spezifikation ist Zielbild, der echte SQL Server die Referenz.
