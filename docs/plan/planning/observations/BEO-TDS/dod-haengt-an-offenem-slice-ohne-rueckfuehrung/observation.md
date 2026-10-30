# Ein DoD-Punkt hängt an einem Slice in open/, und §4 nennt es nicht

**Sub-Area:** `*` (Planung, Slice-Schnitt)

Ein Slice führt einen Liefer-Punkt, der Eingänge eines anderen Slice braucht, der noch in `open/` liegt (Szenarien, Gerüste, Messwerkzeuge). Die Rückführungen und der Start-Trigger in §4 nennen diese Abhängigkeit nicht; die Schleife (A wartet auf B, B ist an A „ausgeschlossen“) fällt erst in der Verifikation als HIGH auf. Der Schnitt gehört in die Planung: Liefer-Punkt und Eingang in denselben Slice oder die Abhängigkeit als Start-Trigger.
