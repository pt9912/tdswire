# Ein Testtext „aus der Aufzeichnung“ ist erst durch eine Summe auf beiden Seiten an den Beleg gebunden

**Sub-Area:** `*` (Tests, Messung am echten Server)

Dateien wie `probe-*.sql` und `typen-fest.hex` heißen in KDoc und Test „aufgezeichnet“; ohne Prüfung stimmt das nur, solange niemand die Bytes ändert (Zeilenenden, Edit). Im Review `d4331dc4` (F-5) war die Gleichheit mit der Ablage einmal von Hand geprüft. Gehalten wird sie seit `82f62eae` durch eine SHA-256-Tabelle in `BulkCopyFormsTest` (Kotlin, gegen die Dateien) und in `MessungBulkBelegTests` (.NET, gegen den dekodierten Text der Ablage beider Instanzen). Grenze: wer Tabelle und Datei zugleich ändert, wird nicht gefangen.
