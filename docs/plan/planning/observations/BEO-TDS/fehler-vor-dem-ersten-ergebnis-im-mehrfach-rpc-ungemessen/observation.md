# Ein Fehler ohne Vorereignis im Mehrfach-RPC endet am Server dieser Bibliothek anders als der gemessene Fehler

**Sub-Area:** `*` (Sequenzierer, Antwort auf mehrere RPCs)

Gemessen ist (W-13) ein Schlüsselverstoß im mittleren von drei Aufrufen: DONEINPROC `MORE|ERROR`, RETURNSTATUS 2627, DONEPROC `MORE|0x80` ohne Fehlerbit. Der Server dieser Bibliothek schreibt für einen Fehler ohne Vorereignis (kein Ergebnis, kein Zähler, Umfang `BATCH`) in einem Aufruf, der nicht der letzte ist, DONEPROC mit Fehlerbit und `MORE|0x80` (`0x0083`); das ist in [ADR-0051](../../../../adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) Entscheidung 3 als Setzung benannt, am echten Server nicht gemessen. Der Test `Ein Fehler beendet nur seinen Aufruf und die weiteren laufen` pinnt diese Setzung, die gemessene Form hat einen eigenen Test (Review F-6).
