# Das Orakel eines Zufallstests spiegelt die Implementierung statt eines getrennten Modells

**Sub-Area:** `*` (Tests, Verifikation)

Ein Zufallstest mit festem Startwert prüft die Ausgabe nur auf Wohlgeformtheit (hier: kodierbar, ROW nach COLMETADATA, `DONE_MORE` genau dann, wenn ein Token folgt) und bindet den Ausgang nicht an die Tokenfolge; er hat kein eigenes Modell der erlaubten Eingaben, aus dem der erwartete Ausgang folgt. Er findet dann nur, was die Implementierung ohnehin selbst weiß. Der Umkehrschluss gilt ebenso: ein Mutant, der nur am Zufallstest stirbt, zeigt, dass ein deterministischer Test fehlt. Verwandt, aber eigener Mechanismus: `BEO-TDS/strukturtest-ohne-variation-der-eingabe` (dort variiert die Eingabe nicht, hier variiert sie, aber das Urteil über die Ausgabe ist zu schwach).
