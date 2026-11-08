# Antwort auf `EXEC sp_unprepare` mit sehr vielen unbekannten Nummern: Messprotokoll vom 2026-11-07 und 2026-11-08

Messprotokoll zu [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität) und [TDS-FA-04](../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc) (`AGENTS.md` §5 Regel 7). Es hält fest, was Läufe des Testsatzes gemessen haben, nicht, ob eine Anforderung belegt ist. Der Messwert am echten SQL Server (jede Nummer wird gemeldet) steht in [Zeile M-22 des Referenzabgleichs](referenzabgleich.md); hier geht es nur um den Speicher des Servers dieses Repos.

**Vorbehalt, der für jede Zeile gilt.** Gemessen ist der Server dieses Repos, nicht der echte SQL Server. Die Reihen a und b sind Einzelmessungen unter hoher Last des Hosts. Erzeuger: Reihe a der Test `UnprepareDispatchTest` des damaligen Standes mit einem Abtaster, der nicht im Repo liegt (Größenordnung); Reihe b der Test `UnprepareLastProcessTest` im Repo (die Zeilen b1 bis b3 stammen aus einer Messfassung des Tests mit Schleife über Verbindungszahlen und Leerraum-Batch, die nicht im Repo liegt; im Repo besteht der Fall b2 als Test). Wiederholbar im Repo ist allein die Aussage von b2; die Zeilen b4 bis b6 (Messfassung) und b7, b8 (Proben des Reviews) sind es nicht.

## Bedingungen

| Angabe | Wert |
|---|---|
| Host | Linux 6.8, 20 Kerne, 31 GiB; Load Average (1 Minute) 30 bis 65 durch parallele Läufe anderer Rollen |
| Lauf | `make test` (Stage `test`); Serverprozess: Kind-JVM mit `-Xmx256m` und `-XX:+ExitOnOutOfMemoryError` (Option des Image des Testservers) |
| Eingabe | ein SQL_BATCH aus rund 419 000 Anweisungen `EXEC sp_unprepare 1;` (ohne gehaltenes Handle, also ebenso viele unbekannte Nummern), Nachricht knapp unter der Voreinstellung `MAX_MESSAGE_BYTES` (16 MiB) samt Paketköpfen |

## Reihe a: Antwort als Puffer, Dispatch-Test, eine Verbindung, 419 429 Nummern (Stand 2026-11-07)

| Nr. | Stand | Heap der Test-JVM | Ergebnis |
|---|---|---|---|
| a1 | Obergrenze entfernt, Antwort als Listen von Meldungen und Tokens | 128 MiB | `OutOfMemoryError` |
| a2 | Antwort in einem Puffer genau ihrer Größe | 128 MiB | besteht |
| a3 | wie a2 | 96 MiB | besteht; Antwort rund 45 MiB, belegter Heap höchstens 92 MiB (Abtaster) |
| a4 | wie a2 | 64 MiB | `OutOfMemoryError` |

Dieser Stand liegt nicht mehr im Repo (die Antwort wird gestreamt, Reihe b). Die Reihe a zeigt, dass ein Antwortpuffer bei 16 MiB Anfrage rund 45 MiB belegt (rund das 2,8-Fache der Anfrage: 419 429 Nummern zu 112 Byte gegen 16 777 182 Byte).

## Reihe b: Serverprozess, mehrere gleichzeitige Verbindungen, je ein Batch von rund 16 MiB

| Nr. | Stand | Verbindungen | Ergebnis |
|---|---|---|---|
| b0 | Antwort als Puffer, im Dispatch gebaut, mit der Paketkopie von `MessageWriter.write` | 1 | vollständige Antwort (47 381 930 Byte), Prozess lebt |
| b0 | wie oben | 2 | beide ohne Antwort geschlossen, der Prozess endet (`OutOfMemoryError`, Prozess beendet sich) |
| b1 | Antwort gestreamt (Token für Token in den Strom der Antwort) | 2 | beide erhalten 47 381 930 Byte, der Prozess lebt (zwei Läufe) |
| b2 | wie b1 | 2, danach eine neue Verbindung | im Repo als Test: vollständige Antwort je Verbindung, Prozess lebt, die neue Verbindung wird beantwortet |
| b3 | wie b1 | 3 | alle drei erhalten die vollständige Antwort, der Prozess lebt (ein Lauf) |
| b4 | wie b1 | 4 | alle vier ohne Antwort geschlossen; Prozess endete in einem Lauf, lebte in einem anderen |
| b5 | Batch aus `SELECT 1` und Leerraum, gleiche Größe, kleine Antwort | 4 | alle vier ohne Antwort geschlossen, Prozess lebt |
| b6 | wie b5 | 8 | alle ohne Antwort geschlossen oder abgebrochen, Prozess endete |
| b7 | wie b1, lauter `1` oder 322 000 verschiedene siebenstellige Nummern; Probe des Reviews vom 2026-11-08, kein Test im Repo, Last 20 bis 35 | 4, mit `-XX:+ExitOnOutOfMemoryError` | alle vier ohne Antwort geschlossen nach rund 0,5 s, der Prozess endet (zwei von zwei Läufen) |
| b8 | wie b7, lauter `1`, ohne die Option | 4 | drei erhalten die vollständige Antwort, eine endet mit `OutOfMemoryError` (`HANDLER_FAILED`, ohne Fehlerantwort), der Prozess lebt (ein Lauf) |

Die Aussage reicht so weit: mit gestreamter Antwort besteht der Fall mit zwei gleichzeitigen Nachrichten von 16 MiB bei 256 MiB Heap (b1, b2); die Antwort ist nicht mehr der Engpass, die Grenze liegt bei den gleichzeitigen Anfragen dieser Größe (b4 bis b8: ein Batch aus Leerraum scheitert ebenso; mit der Option `-XX:+ExitOnOutOfMemoryError` des Image des Testservers endet bei vier Nachrichten in den Läufen von b7 der Prozess, nicht nur die Verbindungen; b4 bis b6 stammen aus der Messfassung außerhalb des Repos, b7 und b8 aus Proben ohne Test im Repo). Nicht gemessen: andere Heapgrößen, Verbindungszahlen zwischen den Reihen, `MAX_MESSAGE_BYTES` über 16 MiB, Last durch Anfragen anderer Art. Wer mehr gleichzeitige Verbindungen oder ein größeres `MAX_MESSAGE_BYTES` zulässt, bemisst den Heap für die Anfragen; die Antwort wächst mit der Zahl der Nummern, wird aber nicht gehalten.

## Reihe b, Antwort mit RETURNSTATUS und DONEPROC je Nummer (Stand 2026-11-08)

Die Antwort je Nummer wächst von 113 Byte (ERROR) auf 131 Byte (ERROR 113, RETURNSTATUS 5, DONEPROC 13), die Antwort auf 419 309 Nummern von 47 381 930 auf 54 929 479 Byte. Die Reihe wiederholt b2 bis b4 mit diesem Stand. Erzeuger: der Test `UnprepareLastProcessTest` im Repo für b9; b10 und b11 sind derselbe Test mit der Konstante `CONNECTIONS` gleich 3 und 4, diese Fassung liegt nicht im Repo (Größenordnung). Lauf: Gradle in Docker (Image der Stage `deps`, Init-Skript der Stage `test`), nicht `make test`; Serverprozess wie oben (Kind-JVM mit `-Xmx256m` und `-XX:+ExitOnOutOfMemoryError`); Load Average (1 Minute) 13, Einzelmessungen, ein Lauf je Zeile.

| Nr. | Stand | Verbindungen | Ergebnis |
|---|---|---|---|
| b9 | Antwort gestreamt, 131 Byte je Nummer | 2, danach eine neue Verbindung | im Repo als Test: vollständige Antwort je Verbindung (54 929 479 Byte), Prozess lebt, die neue Verbindung wird beantwortet |
| b10 | wie b9 | 3 | alle drei erhalten die vollständige Antwort, der Prozess lebt, die neue Verbindung wird beantwortet |
| b11 | wie b9 | 4 | die Verbindung 0 ohne Antwort geschlossen, der Prozess endet mit `OutOfMemoryError` (`Terminating due to java.lang.OutOfMemoryError: Java heap space`) |

Die Aussage der Reihe: mit der längeren Antwort besteht der Fall mit zwei gleichzeitigen Nachrichten von 16 MiB bei 256 MiB Heap (b9) und, in einem Lauf, der mit drei (b10); bei vier endete der Prozess in einem Lauf (b11). Das ist dasselbe Bild wie in b2 bis b4; ob die längere Antwort die Grenze verschiebt, zeigt ein Lauf je Zeile nicht. Nicht gemessen: Wiederholungen der Zeilen b10 und b11, andere Heapgrößen und die Zeit der Antwort.
