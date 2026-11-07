# Antwort auf `EXEC sp_unprepare` mit sehr vielen unbekannten Nummern: Messprotokoll vom 2026-11-07

Messprotokoll zu [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität) und [TDS-FA-04](../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc) (`AGENTS.md` §5 Regel 7). Es hält fest, was ein Lauf des Testsatzes und ein Wegwerf-Abtaster gemessen haben, nicht, ob eine Anforderung belegt ist. Der Messwert am echten SQL Server (jede Nummer wird gemeldet) steht in [Zeile M-22 des Referenzabgleichs](referenzabgleich.md); hier geht es nur um den Speicher des Servers dieses Repos.

**Vorbehalt, der für jede Zeile gilt.** Gemessen ist der Server dieses Repos im Testlauf (Dispatch-Test mit einem Kanal im Speicher, ohne Socket, ohne TLS), **nicht** der Serverprozess und nicht der echte SQL Server. Der Erzeuger der Zahlen ist der Test `UnprepareDispatchTest > Ein Batch bis zur Grenze der Nachricht mit unbekannten Nummern endet im Heap` (im Repo) **mit einem Abtaster**, der nicht im Repo liegt: ein Thread, der alle 1 ms `totalMemory() - freeMemory()` liest und das Maximum hält. Die Zahlen des Abtasters und die Zeit sind Größenordnungen; wiederholbar im Repo ist allein die Aussage des Tests (besteht oder endet im Heap-Überlauf bei 128 MiB). Der belegte Heap enthält Objekte, die der Müllsammler noch nicht eingesammelt hat.

## Bedingungen

| Angabe | Wert |
|---|---|
| Datum | 2026-11-07 |
| Host | Linux 6.8, 20 Kerne, 31 GiB; Load Average (1, 5, 15 Minuten) 65,72, 62,41, 43,77 (gelesen mit `uptime` um 20:10, nach den Läufen) |
| Lauf | `make test` (Stage `test`), die Test-JVM mit `maxHeapSize` aus `gradle/test-jvm.init.gradle.kts`, für die Reihen 64 MiB und 96 MiB von Hand geändert und zurückgesetzt |
| Eingabe | ein SQL_BATCH von 419 429 Anweisungen `EXEC sp_unprepare 1;` (ohne gehaltenes Handle, also 419 429 unbekannte Nummern), Nutzlast mit ALL_HEADERS 16 777 182 Byte, knapp unter der Voreinstellung `MAX_MESSAGE_BYTES` (16 MiB) |

## Ergebnis

| Nr. | Stand | Heap der Test-JVM | Ergebnis |
|---|---|---|---|
| s1 | Obergrenze entfernt, Antwort wie vor der Änderung als Listen von Meldungen und Tokens gebaut | 128 MiB | `OutOfMemoryError`, Test rot |
| s2 | Antwort in einem Puffer genau ihrer Größe, Log ein Eintrag je Antwort | 128 MiB | besteht; 419 429 ERROR, DONE mit Fehlerstatus, Folgeanfrage beantwortet |
| s3 | wie s2 | 96 MiB | besteht; Antwort 45 MiB (rund 112 Byte je Nummer), Laufzeit der Antwort 3,6 s unter Last, belegter Heap höchstens 92 MiB (Abtaster) |
| s4 | wie s2 | 64 MiB | `OutOfMemoryError`, Test rot |

Die Aussage reicht so weit: bei der Voreinstellung von 16 MiB für die Nachricht und in der kürzesten Schreibweise der Anweisung belegt die Antwort etwa das 2,7-Fache der Anfrage, und der Lauf kommt im Testlauf mit 96 MiB Heap aus, mit 64 MiB nicht. Nicht gemessen: der Serverprozess unter Last mit mehreren Verbindungen, ein größerer Wert von `MAX_MESSAGE_BYTES`, längere Nummern in der Anweisung. Wer `MAX_MESSAGE_BYTES` erhöht, bemisst den Heap für Anfrage und Antwort entsprechend; die Antwort wächst linear mit der Zahl der Nummern, ein Strom statt eines Puffers ist nicht gebaut.
