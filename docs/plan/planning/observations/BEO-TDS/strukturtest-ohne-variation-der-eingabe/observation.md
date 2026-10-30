# Ein Strukturtest belegt die behauptete Eigenschaft nicht, weil seine Eingabe nicht variiert

**Sub-Area:** `*` (Tests, Verifikation)

Ein Test behauptet eine Auswahl- oder Gleichheitseigenschaft (hier: der Scheinvergleich nimmt einen bestimmten Eintrag), seine Eingabe ist aber einheitlich; jede Mutation der Auswahl bleibt grün. Erst der Verifier findet es per Mutation. Verwandt, aber eigener Mechanismus: `BEO-TDS/schranken-test-mit-ungeprueftem-zaehler` (dort fehlt die Untergrenze, hier die Variation der Eingabe).
