# Die Obergrenze des Testserver-Bestands ist in Zellen gesetzt, nicht am Heap gemessen

**Sub-Area:** `*` (Testserver, [TDS-FA-12](../../../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-02](../../../../../../spec/lastenheft.md#tds-qa-02--stabilität))

Der Bestand des Testservers hält Zeilen im Arbeitsspeicher. Eine Grenze in Zellen (Zeilen mal Spalten) verhindert, dass die Zahl der Nachrichten den Heap erschöpft und `-XX:+ExitOnOutOfMemoryError` den Prozess für alle Sitzungen beendet; der Wert der Grenze ist eine Setzung, kein Messwert am Heap, und ein kleinerer Heap als die Zellen brauchen endet weiter mit `OutOfMemoryError`.
