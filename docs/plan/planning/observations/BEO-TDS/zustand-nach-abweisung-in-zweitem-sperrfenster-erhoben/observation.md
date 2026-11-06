# Die Ursache einer Abweisung wird in einem zweiten Sperrfenster erhoben, nicht in dem der Entscheidung

**Sub-Area:** `*` (Spezifikation, Listener)

`ConnectionRegistry.refusal()` nahm die Sperre ein zweites Mal, nachdem `admit` abgewiesen hatte; gab in der Lücke eine Verbindung einen Platz frei, meldete das Ereignis den falschen Grund. Behoben: der Grund wird in `admit` unter derselben Sperre gesetzt. Ein Test für die Rennlage fehlt.
