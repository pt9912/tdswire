# Eine Aussage für die Nutzer-Doku überlebt die Neustrukturierung der Doku nur als Übergabeliste

**Sub-Area:** `*` (Nutzer-Doku)

Ein Zweig änderte `docs/user/einbettung-und-betrieb.md` an fünf Stellen, während ein anderer die Datei in Aufgaben und Referenzseiten teilte; ein Merge auf Textebene ist dort nicht möglich. Die Verifikation führte eine Übergabeliste (sechs Stellen); der Übertrag geschah im Merge-Commit und wurde weder gereviewt noch verifiziert. Prüfpunkt: nach einem Merge mit Umbau der Doku alle Dateien unter `docs/user/` gegen den Slice lesen, nicht nur die Stellen der Liste (Aufgabe 10 fehlte im Einstieg `README.md`).
