# Live-Lauf und Mitschnitt-Lauf führen je eine Ausnahmeliste-Datei mit geteilten Nummern, und nichts hält sie gleich

**Sub-Area:** `*` (Referenzabgleich, Ausnahmeliste)

Die Prosa-Liste A-n in `docs/messungen/referenzabgleich.md` ist eine; maschinenlesbar stehen `live-ausnahmen.tsv` (Schlüssel: Schritt) und `mitschnitt-ausnahmen.tsv` (Schlüssel: Szenario/NN/Feld). A-2, A-3 und A-6 gelten in beiden. Wird der Grund oder die Art einer geteilten Nummer in einer Datei geändert, bleibt die andere stehen; kein Sensor liest beide.
