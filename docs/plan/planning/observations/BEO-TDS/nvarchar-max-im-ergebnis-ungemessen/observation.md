# Das COLMETADATA einer `nvarchar(max)`-Spalte im Ergebnis ist am echten Server nicht gemessen; die Bibliothek meldet im Ergebnis 4000

**Sub-Area:** `*` (Simulations-Backend, Protokollantworten)

Eine Spalte der Tabellenbeschreibung mit `SimColumn.maxLength` -1 (`nvarchar(max)`) erscheint im COLMETADATA einer Auswahl als `nvarchar(4000)` (Länge 8000); ein Text über 4000 Zeichen endet im Fehler der Antwort ([ADR-0056](../../../../adr/0056-laenge-einer-textspalte-erreicht-das-colmetadata-einer-auswahl.md) Entscheidung 3, Setzung, SPEC-035 kennzeichnet sie so). Wie der echte Server das COLMETADATA einer solchen Spalte in einer Auswahl schreibt (Typ, Längenfeld, Form des Wertes), ist nicht gemessen; im Katalog ist -1 gemessen (M-18). Kein Fixture und keine Aufnahme führt eine `nvarchar(max)`-Spalte in einer Auswahl, deshalb steht keine Zeile auf der Ausnahmeliste.
