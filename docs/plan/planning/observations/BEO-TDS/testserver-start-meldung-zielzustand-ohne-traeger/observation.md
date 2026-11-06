# Der Zielzustand „Testserver schreibt beim Start keine Meldung einer Ausnahme“ hat keinen Träger

**Sub-Area:** `*` (Testserver, Logging)

Die Spezifikation (Abschnitt 4, Logging) führt als Zielzustand, nicht gebaut: dass auch der Testserver beim Start keine Meldung einer Ausnahme schreibt; gebaut schreibt er die Meldung des Ladefehlers der Tabellenbeschreibung mit Pfad, Zeile und Spalte, einem festen Grund und nie dem Inhalt. Grundsatz des Nutzers vom 2026-11-06: die Spezifikation ist das Zielbild, der Code folgt ihr. `slice-betrieb-log-adapter-testserver` führt die Inspektion am laufenden Prozess, nicht diese Streichung; der Zielzustand hängt an keinem Slice.
