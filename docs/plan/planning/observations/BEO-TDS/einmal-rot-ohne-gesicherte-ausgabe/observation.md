# Ein Test war einmal rot, die Ausgabe wurde vor dem Neustart nicht gesichert

**Sub-Area:** `*` (Tests, Verifikation)

Ein Lauf endet einmal rot (hier: ein Test der Fix-Runde von `slice-bootstrap-konfiguration`), der nächste Lauf ist grün, und die Ausgabe des roten Laufs wurde nicht gesichert. Die Ursache ist unbekannt; vier Läufe des Verifiers belegen keine Flake-Freiheit, und ein Fix ließe sich nicht messen, weil nichts vorliegt, woran er zu messen wäre. Verwandt, aber eigener Mechanismus: `BEO-TDS/flake-fix-ohne-reproduzierten-fehler` (dort ist die Ursache analysiert und die Wirkung des Fix ungemessen, hier fehlt die Ausgabe) und `BEO-TDS/test-stage-cache-verdeckt-wiederholung` (dort verdeckt der Cache die Wiederholung).
