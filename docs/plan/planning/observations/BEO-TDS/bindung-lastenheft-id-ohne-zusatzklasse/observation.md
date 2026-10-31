# Die Bindung einer Sensors-Zeile nennt eine Lastenheft-Kennung, ohne dass die Zusatzklasse deklariert ist

**Sub-Area:** `*` (harness/README.md, harness/conventions.md)

Die Zeilen `make laufzeittest` ([TDS-RB-01](../../../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache)) und `make workflow-lint` ([TDS-QA-06](../../../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)) binden an eine Lastenheft-Kennung; der Abschnitt Zusatzklassen in `harness/conventions.md` führt nur den Platzhalter. Ob die Klasse „Lastenheft-Bindung“ zu deklarieren ist, ist eine Entscheidung des Nutzers über eine Konventionsdatei; der Planner ändert sie nicht.
