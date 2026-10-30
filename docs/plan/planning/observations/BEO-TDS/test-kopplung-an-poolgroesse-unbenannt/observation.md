# Ein Test hängt an der Größe eines Pools, und die Kopplung steht in keinem Kommentar

**Sub-Area:** `*` (Tests, Verifikation)

Ein Sturmtest startet blockierende Clients auf einem Pool fester Größe (hier 10 der 105 Fälle auf 12 Threads) und lässt sie auf Antworten warten, die der Handler erst nach einer Eintrittssperre schickt. Wächst die Kopienzahl über die Poolgröße, blockiert der Lauf bis zur Zeitüberschreitung statt einen Fehler der Software zu zeigen. Bei den heutigen Werten ist es kein Fehler; die Kopplung zwischen Kopienzahl und Poolgröße steht nirgends, und wer einen Wert ändert, erfährt sie als roten Lauf ohne Hinweis. Verwandt, aber eigener Mechanismus: `BEO-TDS/einmal-rot-ohne-gesicherte-ausgabe` (dort fehlt die Ausgabe eines Laufs, hier die Aussage über die Abhängigkeit).
