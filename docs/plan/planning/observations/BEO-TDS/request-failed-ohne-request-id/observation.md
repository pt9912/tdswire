# `REQUEST_FAILED` trägt keine Anfrage-Kennung, obwohl `tds.query` sie trägt

**Sub-Area:** `*` (Bibliothek, öffentliche Fläche, Telemetrie)

Die Kennung der Anfrage (`requestId`) steht im Ende der Spans `tds.query` und `backend.execute`, nicht in `REQUEST_FAILED` und in keinem anderen Ereignis. Wer einen Fehler (`error_code`) einer Anfrage zuordnen will, muss über `connection_id`, `session_id` und die Reihenfolge schließen; mit `LOG_LEVEL` unter `DEBUG` fehlt das Ende des Spans ganz. Ein Feld `requestId` an `REQUEST_FAILED` ist kein neuer Typname, aber ein weiterer Zugriff der öffentlichen Ereignisklasse; ein Ereignis „Anfrage beendet“ wäre eine neue Art.

Optionen: (a) so lassen; (b) `requestId` in `REQUEST_FAILED`; (c) zusätzlich ein Ereignis „Anfrage beendet“ (Art, Dauer, Ergebnis).
