# Prepared-Handles von mssql-jdbc gegen den Testserver: Messprotokoll vom 2026-11-06

Messprotokoll zu [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate) und [TDS-FA-04](../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc) (`AGENTS.md` §5 Regel 7), Grundlage der Optionen in [ADR-0036](../plan/adr/0036-handle-freigabe-und-grenze-je-sitzung.md). Es hält fest, was ein Wegwerf-Messwerkzeug gemessen hat, nicht, ob eine Anforderung belegt ist. Es ergänzt die Zeilen a6 und a7 des [Spikes vom 2026-11-05](callback-loesung-spike-2026-11-05.md) (Exit 5 nach 1001 `MERGE` je Sitzung, kein `sp_unprepare` in 2002 Anweisungen), die d-migrate gegen einen Prototyp maßen; hier läuft allein der Treiber gegen den gebauten Testserver.

**Vorbehalt, der für jede Zeile gilt.** Gemessen ist das Verhalten von mssql-jdbc 13.4.0 gegen den **Testserver des Standes `cd0e2368`**, nicht gegen einen echten SQL Server (kein Lauf gegen ihn; die EULA-Entscheidung liegt beim Nutzer) und nicht d-migrate selbst: Ob d-migrate seine Anweisungsobjekte wiederverwendet oder je Zeile neu anlegt und schließt, ist **nicht gemessen** (das Muster beider Fälle steht unten als Reihen mit `eine` und `neu`). Das Messwerkzeug liegt im Scratchpad der Sitzung und nicht im Repo; die Zeilen sind nachrechenbar, nicht wiederholbar (Klasse „Client gegen Prototyp“ der Beobachtung `BEO-TDS/spike-prototyp-nicht-im-repo-messung-nicht-wiederholbar`, hier mit gebautem Server und eingreifendem Durchlauf).

## Bedingungen

| Angabe | Wert |
|---|---|
| Datum | 2026-11-06 |
| Host | Linux 6.8, 20 Kerne, 31 GiB; Load Average (1, 5, 15 Minuten) 7,21, 8,02, 9,99 (gelesen mit `uptime` um 08:15) |
| Server | Testserver aus Stage `testserver` des Standes `cd0e2368`, lokal gebaut (`make testserver-up` mit eigenem Namen und Port 14330), Voreinstellungen, Beispieltabelle `bestellungen`; Handle-Grenze 1000 wie gebaut |
| Client | `mssql-jdbc-13.4.0.jre11.jar` und OpenJDK 21.0.12 aus dem Image `ghcr.io/pt9912/d-migrate@sha256:af9d3eb323a6cfd13eb63f012823788e510f764509918933d2ff98c8e68e4212` (nicht dasselbe Image wie im Spike; die Treiberversion ist dieselbe); d-migrate selbst lief nicht |
| Anweisung | `SELECT * FROM bestellungen WHERE KundenId = ?` mit `setInt`, eine Verbindung je Reihe, Klartext (`encrypt=false`) |
| Messwerkzeug | ein Java-Programm im Scratchpad (übersetzt mit dem JDK des Gradle-Images aus dem Dockerfile), das zwischen Treiber und Testserver einen TCP-Durchlauf legt und die Nachrichten des Clients zählt: RPC nach ProcID (aus dem Kopf der Nachricht gelesen), SQL-Batch nach Text. Reihen `S` und `T` unterscheiden sich nur im Eingriff unten |
| Eingriff in Reihen `T` | der Durchlauf fügt in die Antwort auf `sp_prepexec` vor dem RETURNVALUE `@handle` ein RETURNSTATUS (Token `0x79`, Wert 0) ein und korrigiert die Paketlänge; der Testserver selbst ist unverändert |
| Eingriff in Reihen `U` | der Durchlauf ersetzt dort das RETURNVALUE `@handle` durch ein RETURNSTATUS (Wert 0); der Server hat das Handle trotzdem vergeben und gezählt |

Die Abkürzungen: **eine** = ein `PreparedStatement`, n-mal ausgeführt; **neu** = je Ausführung ein neues `PreparedStatement` mit demselben Text, danach geschlossen; **Voreinstellung** = keine Verbindungseigenschaft für Prepare; **Erstes-Mal** = `enablePrepareOnFirstPreparedStatementCall=true`.

## (h) Ohne Eingriff: die Antwort des Testservers

| Nr. | Reihe | Ergebnis |
|---|---|---|
| h1 | `neu`, Voreinstellung, 1100 Ausführungen | 1100 gelingen; 1100 Nachrichten `sp_executesql`, kein `sp_prepexec`: ohne Erstes-Mal entsteht kein Handle |
| h2 | `neu`, Erstes-Mal, 1100 | 1000 gelingen, die 1001. endet mit „Prepared handle limit reached.“; 1001 Nachrichten `sp_prepexec`, **keine andere Nachricht**, also weder `sp_unprepare` als RPC noch als Batch, obwohl 1000 Anweisungen geschlossen wurden |
| h3 | `eine`, Voreinstellung, 1100 | 1001 gelingen, die 1002. endet mit „Prepared handle limit reached.“; 1 `sp_executesql`, 1001 `sp_prepexec`, **kein** `sp_execute`: der Treiber bereitet dieselbe Anweisung bei jeder Ausführung neu vor |
| h4 | `neu`, Erstes-Mal mit `statementPoolingCacheSize=10` und `disableStatementPooling=false`, 1100 | wie h2 |
| h5 | `neu`, Erstes-Mal mit `serverPreparedStatementDiscardThreshold=0`, 1100 | wie h2 |
| h6 | Nachricht des Clients bei h3, `sp_prepexec` (4 Ausführungen, Bytes gelesen) | Parameter 0 ist `INTN` 4 mit Status OUTPUT und dem Wert **0** in jeder der drei `sp_prepexec` (nicht −1, nicht das Handle der Vorgängerin); die Antwort des Servers enthält RETURNVALUE `@handle` (Ordinal 0, `INTN` 4, Werte 1, 2, 3) **ohne** RETURNSTATUS davor |

Gelesen: Das Muster von h2 und h3 ist das des Spikes (a6): ein `sp_prepexec` je Ausführung, die Grenze nach 1001, kein `sp_unprepare`. Dass dieselbe Ursache d-migrate trifft, ist nicht gemessen.

## (t) Mit RETURNSTATUS vor dem Handle (Eingriff `T`)

| Nr. | Reihe | Ergebnis |
|---|---|---|
| t1 | `eine`, Voreinstellung, 200 | 200 gelingen; 1 `sp_executesql`, 1 `sp_prepexec`, **198 `sp_execute`**: ein Handle für alle Ausführungen |
| t2 | `neu`, Voreinstellung, 200 | 200 gelingen; 200 `sp_executesql` |
| t3 | `neu`, Erstes-Mal, 200 | 200 gelingen; 200 `sp_prepexec`, **18 Batches mit `sp_unprepare`** |
| t4 | `neu`, Erstes-Mal, 1100 | 1000 gelingen, die 1001. endet mit „Prepared handle limit reached.“; 1001 `sp_prepexec`, **90 Batches mit `sp_unprepare`**; die Handles werden vom Server nicht freigegeben, daher bleibt die Grenze erreichbar |
| t5 | Text der Batches aus t3 (25 Ausführungen, Bytes gelesen) | SQL-Batch (Pakettyp `0x01`), kein RPC: `EXEC sp_unprepare 1;EXEC sp_unprepare 2;…` mit den Handles 1 bis 11, der zweite Batch mit 12 bis 22, getrennt durch `;`; das Ende des Textes steht nicht im Mitschnitt (auf 300 Zeichen gekürzt) |
| t6 | Antwort des Testservers auf den Batch aus t5 | ERROR 50001 „Unsupported SQL.“ und DONE mit Fehlerbit; der Treiber setzt fort, die folgende Ausführung gelingt (t3, t4: alle gelingen bis zur Grenze) |

Gelesen: Mit RETURNSTATUS übernimmt der Treiber das Handle (t1) und sendet `sp_unprepare` (t3); das RETURNSTATUS ist der einzige Unterschied der Antwort zwischen h und t (nachgeprüft am Durchlauf, nicht am Quelltext des Treibers). **Warum** der Treiber es braucht, ist nicht aus seinem Code gelesen. Ob ein echter SQL Server RETURNSTATUS auf `sp_prepexec` sendet, ist nicht gemessen.

## (u) Antwort ohne Handle (Eingriff `U`)

| Nr. | Reihe | Ergebnis |
|---|---|---|
| u1 | `eine`, Voreinstellung, 200 | 200 gelingen; 1 `sp_executesql`, 199 `sp_prepexec` |
| u2 | `neu`, Erstes-Mal, 200 | 200 gelingen; 200 `sp_prepexec`; weder `sp_execute` noch `sp_unprepare` |

Gelesen: Der Treiber verträgt eine `sp_prepexec`-Antwort ohne Handle; er bereitet bei der nächsten Ausführung erneut vor. Das ist ein Zustand der Verbindung des Treibers, kein Hinweis darauf, wie ein anderer Client oder d-migrate reagiert.

## Nicht gemessen

- Verhalten und Handle-Grenze eines echten SQL Servers; ob er RETURNSTATUS auf `sp_prepexec` sendet; Antwort auf `sp_unprepare` mit unbekanntem Handle.
- d-migrate: Wiederverwendung und Schließen seiner Anweisungsobjekte beim `MERGE`; die Reihen `eine` und `neu` sind die beiden Grenzfälle, keine Messung seiner Läufe.
- Speicher je Handle bei `MAX_CONNECTIONS` (Text, Deklaration): nicht gemessen; der Text im Messlauf umfasst rund 50 Zeichen, der des `MERGE` von d-migrate ist länger (Wortlaut liegt nicht im Repo).
- Wiederverwendung eines Handles für denselben Text, Verdrängung des ältesten Handles und Ausführung ohne Handle bei Überlauf **auf dem Server**: kein Prototyp, nur die Treiberseite von u1 und u2.
- Reaktion des Treibers auf einen Fehler bei `sp_execute` mit unbekanntem Handle (der Testserver kann ihn in dieser Messreihe nicht auslösen).
- Andere Treiberversionen, `Pooling` mit `RESETCONNECTION`, TDS 8.0.
