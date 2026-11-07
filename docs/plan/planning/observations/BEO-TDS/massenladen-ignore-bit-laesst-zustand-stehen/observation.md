# Eine verworfene Nachricht des Massenladens (IGNORE-Bit) setzt den Zustand „wartend“ nicht zurück

**Sub-Area:** `*` (Protokoll, Massenladen)

`BulkDispatch.answer` verwirft eine Nachricht 0x07 mit gesetztem IGNORE-Bit und beantwortet sie (Zähler 0, Setzung), setzt aber `awaiting` nicht zurück. Ein folgendes ATTENTION in `READY` wird deshalb mit `ERROR|ATTENTION` (gemessene Antwort auf „Client bricht ab, ehe er sendet“) statt mit der gewöhnlichen Bestätigung beantwortet, obwohl die Nachricht beantwortet wurde. Randfall: die Antwort auf IGNORE ist ohnehin Setzung, kein Test deckt die Kombination, der Fehler ist von Hand im Code gelesen, nicht gefahren.
