# Eine Aussage des Plans über das Verhalten am Prozess wird durch einen Merge von `main` falsch, ohne dass ein Test den Plan prüft

**Sub-Area:** `*` (Slice-Pläne, Tests)

Der Slice sagte (DoD 1, `SPEC-052`): `LOG_LEVEL` filtert, und `DEBUG` zeigt mehr. Nach dem Merge der Änderung von `main`, dass der Prozess `main` des Testservers immer ein Backend über dem `TableStore` übergibt, gilt der zweite Teil am Prozess nicht mehr (kein Ereignis der Ebene `DEBUG`). Der Implementer hat die Erwartung des Prozesstests angepasst; die Nutzer-Doku, §6 und die Closure-Notiz trugen den Wandel nicht. Die Aussage war beim Schreiben wahr; sie hing an einem Verhalten außerhalb des Slice.
