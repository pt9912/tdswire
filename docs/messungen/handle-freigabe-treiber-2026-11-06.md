# Prepared-Handles von mssql-jdbc gegen den gebauten Server mit Freigabe und RETURNSTATUS: Messprotokoll vom 2026-11-06

Messprotokoll zu [TDS-FA-04](../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc) und [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität) (`AGENTS.md` §5 Regel 7), Beleg zu [ADR-0036](../plan/adr/0036-handle-freigabe-und-grenze-je-sitzung.md). Es hält fest, was `make messung-treiber-handles` gemessen hat, nicht, ob eine Anforderung belegt ist. Es ist das Gegenstück zu den Reihen `h` und `t` des [Messprotokolls vom selben Tag](handle-grenze-optionen-2026-11-06.md): dort lief der Treiber gegen den Server **ohne** RETURNSTATUS und Freigabe (Reihen `h`) und mit einem Eingriff im Durchlauf (Reihen `t`); hier läuft er gegen den **gebauten** Server, der beides selbst tut, ohne Eingriff.

**Vorbehalt, der für jede Zeile gilt.** Gemessen ist das Verhalten von mssql-jdbc 13.4.0 gegen den Testserver dieses Repos, nicht gegen einen echten SQL Server und nicht d-migrate selbst (ob d-migrate seine Anweisungsobjekte schließt oder wiederverwendet, ist weiter **nicht gemessen**; die Reihen `eine` und `neu` sind die zwei Grenzfälle). Eine Verbindung je Reihe, TDS 7.4 im Klartext, ein Host, ein Lauf. Anders als beim Protokoll der Reihen `h` und `t` ist das Messwerkzeug im Repo (`tools/treiber-handles/Messung.java`, Aufruf `make messung-treiber-handles`) und die Messung wiederholbar; sie endet mit Exit 1, wenn eine Erwartung nicht zutrifft.

## Bedingungen

| Angabe | Wert |
|---|---|
| Datum | 2026-11-06 |
| Host | Linux 6.8, 20 Kerne, 31 GiB; Last durch andere Läufe: Lastmittel (1, 5, 15 Minuten) 13,28, 13,20, 15,11 zu Beginn (14:54:50) und 11,18, 12,96, 14,76 am Ende (14:57:37), gelesen mit `uptime` |
| Befehl | `make messung-treiber-handles`; Laufzeit 167 s einschließlich Bau des Images (Cache warm), Einzelmessung |
| Server | Testserver aus der Stage `testserver`, lokal gebaut aus dem Arbeitsbaum, dessen Quelltext von Bibliothek und Testserver dem Commit `bec8ccef` gleicht; Voreinstellungen (Grenze 1000), Beispieltabelle `bestellungen`, Benutzer `alice` |
| Client | `mssql-jdbc-13.4.0.jre11.jar` und OpenJDK 21.0.12 (Temurin, JRE) aus dem Image `ghcr.io/pt9912/d-migrate@sha256:af9d3eb323a6cfd13eb63f012823788e510f764509918933d2ff98c8e68e4212` (dasselbe Image wie im Protokoll der Reihen `h` und `t`); d-migrate selbst lief nicht |
| Übersetzung | `Messung.java` mit `javac --release 21` im JDK des Gradle-Images aus dem `Dockerfile` (`gradle:8.14-jdk21`, Digest im Skript), weil das Image von d-migrate keinen Compiler enthält |
| Anweisung | `SELECT * FROM bestellungen WHERE KundenId = ?` mit `setInt`, Klartext (`encrypt=false`) |
| Durchlauf | ein TCP-Durchlauf zwischen Treiber und Server, der die Nachrichten des Clients zählt (RPC nach ProcID aus dem Kopf der Nachricht, SQL-Batch mit und ohne `sp_unprepare`) und keine Bytes ändert |

Die Abkürzungen: **eine** = ein `PreparedStatement`, n-mal ausgeführt; **neu** = je Ausführung ein neues `PreparedStatement` mit demselben Text, danach geschlossen; **Erstes-Mal** = `enablePrepareOnFirstPreparedStatementCall=true`.

## (m) Gegen den gebauten Server

| Nr. | Reihe | Ergebnis |
|---|---|---|
| m1 | `eine`, Voreinstellung, 200 Ausführungen | 200 gelingen; 1 `sp_executesql`, 1 `sp_prepexec`, **198 `sp_execute`**: ein Handle für alle Ausführungen (wie t1, jetzt ohne Eingriff im Durchlauf) |
| m2 | `neu`, Erstes-Mal, 1100 Ausführungen | **alle 1100 gelingen**, keine Abweisung an der Grenze von 1000; 1100 `sp_prepexec`, **100 Batches mit `sp_unprepare`** (elf Handles je Batch); der Server gibt die Handles frei, die Zahl der gleichzeitig gehaltenen bleibt unter der Grenze (Gegenstück zu t4: dort 1000 gelungen, 1001. abgewiesen, 90 Batches) |
| m3 | Text des ersten Batches aus m2 (Zeichen, nicht gekürzt) | `EXEC sp_unprepare 1;EXEC sp_unprepare 2;…;EXEC sp_unprepare 11;` (222 Zeichen): **mit einem `;` am Ende** (das Ende des Textes stand im Protokoll der Reihe t5 nicht); der Server erkennt die Form mit dem `;` am Ende (`SPEC-030`) |

## Rot gesehen (Mutationen am Server, einmalig, 2026-11-06)

| Nr. | Änderung am geprüften Code | Ergebnis des Werkzeugs |
|---|---|---|
| r1 | `HandleReturningBackend`: das RETURNSTATUS vor dem Handle entfällt | m1 verfehlt: 1 `sp_executesql` und 199 `sp_prepexec`, kein `sp_execute`; m2 verfehlt: 1000 gelingen, die 1001. endet mit „Prepared handle limit reached.“, kein Batch mit `sp_unprepare` (gleich den Reihen h3 und h2); Exit 1 |
| r2 | `UnprepareBatch.parse` erkennt nie eine Freigabe (der Batch geht ans Backend) | m1 erfüllt; m2 verfehlt: 90 Batches mit `sp_unprepare`, 1000 gelingen, die 1001. endet mit „Prepared handle limit reached.“ (gleich der Reihe t4); Exit 1 |
| r3 | Mitschnitt-Lauf (`make vergleichslauf-d LAUF=mitschnitt-2022`) mit der Änderung aus r1 | `prepare/01/tokenfolge` und `prepare/01/rueckgabe` weichen ab und stehen nicht in der Ausnahmeliste; der Lauf endet rot. Ohne die Änderung ist derselbe Lauf auf beiden Instanzen grün (`make vergleichslauf-d LAUF=mitschnitt`, zwei Instanzen, 83 s), die beiden Ausnahmen für `prepare` sind aus der Ausnahmeliste entfernt |

## Gelesen

Gegen den gebauten Server benutzt mssql-jdbc 13.4.0 ein Handle je Anweisungsobjekt (m1) und gibt geschlossene Anweisungen als SQL-Batch frei, den der Server annimmt (m2, m3); die Grenze von 1000 gleichzeitig gehaltenen Handles wird dabei in der Reihe `neu` nicht erreicht. Der Eingriff im Durchlauf der Reihen `t` war damit gleichwertig zu dem, was der Server jetzt selbst sendet, soweit der Treiber es unterscheidet: dieselben Zahlen für `eine`; für `neu` fällt die Abweisung der 1001. Ausführung weg.

## Nicht gemessen

- d-migrate: Wiederverwendung und Schließen seiner Anweisungsobjekte beim `MERGE` (Träger: der Lauf im Slice des `MERGE`-Pfads); die Reihen `eine` und `neu` sind die zwei Grenzfälle.
- Die Antwort eines echten SQL Servers auf RETURNSTATUS bei `sp_prepexec` auf dem Draht (der Mitschnitt der Referenzinstanz zeigt RETURNSTATUS 0 vor dem Handle, für den Client `Microsoft.Data.SqlClient`) und die Reaktion von mssql-jdbc auf einen echten Server.
- Eine Freigabe, die 8179 liefert, am Treiber (der Treiber nennt nach seiner Reihe kein Handle doppelt; ein Lauf, der es täte, ist nicht Teil der Reihen).
- Speicher je Handle bei `MAX_CONNECTIONS`; Pooling mit `RESETCONNECTION`; TDS 8.0; andere Treiberversionen und Clients.
