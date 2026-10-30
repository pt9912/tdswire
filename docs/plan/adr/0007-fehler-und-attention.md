# ADR-0007: Fehlerbehandlung und ATTENTION-Semantik

**Status:** Proposed

**Datum:** 2026-10-29

**Autor:** pt9912

**Bezug:** [TDS-FA-06](../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände), [TDS-FA-07](../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), [TDS-QA-07](../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität)

**Schärft:** [SPEC-044](../../../spec/spezifikation.md#spec-044--geordnetes-beenden), [SPEC-047](../../../spec/spezifikation.md#3-defaults-und-konstanten), [SPEC-048](../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder), [SPEC-049](../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder), [SPEC-029](../../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung), [SPEC-031](../../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität), [SPEC-011](../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder), [SPEC-016](../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder), [ARC-015](../../../spec/architecture.md#1-komponenten-übersicht), [TDS-FA-06.a](../../../spec/spezifikation.md#tds-fa-06a--fehlerabbildung), [TDS-FA-07.a](../../../spec/spezifikation.md#tds-fa-07a--attention-und-cleanup)

---

## Kontext

Ein Cancel oder Streaming-Fehler darf den TDS-Antwortstrom nicht desynchronisieren. ERROR, DONE und ATTENTION-Bestätigung müssen zum Sessionzustand passen.

Dasselbe gilt für zwei Fälle, die keinen Client-Abbruch voraussetzen: das geordnete Beenden des Servers (`SPEC-044`, Frist `SPEC-047`, Fehler `TDS_SHUTDOWN` nach `SPEC-049`) und interne Fehler (`SPEC-048`). Auch dort endet eine laufende Antwort regulär oder die Verbindung wird getrennt, nie mit einem halben Tokenstrom, und ein Fehler nach Beginn des Beendens führt nie zur Weiterverwendung der Verbindung.

## Messung (Spike, Stand 2026-10-30)

Gemessen in einem Spike per Dockerfile ohne Mount: Testprogramm mit `Microsoft.Data.SqlClient` (Paketversion 7.1.1 in der `.csproj`; die geladene Assembly meldet 7.0.0.0) auf `mcr.microsoft.com/dotnet/sdk:10.0.403` (Digest `sha256:de4fe90a…`, derselbe wie in [ADR-0008](0008-dotnet-kompatibilitaetstests.md)); Docker 29.9.2 auf einem Host mit 20 CPUs und 31 GiB Speicher. `Encrypt=false`, `Pooling=false` (außer R18/R19), synchrone API, eine Verbindung je Szenario, der Client verlangt TDS 7.4 (die ausgehandelte Version wurde nicht mitgeschnitten, das Login läuft verschlüsselt). Der Spike-Code liegt nur im Scratchpad des Spike-Agenten und ist nicht im Repo; die Roh-Logs sind nicht abgelegt, nur diese Zusammenfassung. **Zwei Messobjekte, die nicht vermischt werden dürfen:**

- **R — echter SQL Server.** `mcr.microsoft.com/mssql/server`, Digest `sha256:cf8f0ab2ebe61feec2eed2629061a07d247f791c2a8ddda74a4ac9f06a291718` (SQL Server 2022 RTM-CU27-GDR, `ServerVersion` 16.00.4305), eigener Container in eigenem Docker-Netzwerk, `ACCEPT_EULA=Y` (Zustimmung des Repo-Inhabers laut [ADR-0008](0008-dotnet-kompatibilitaetstests.md)), `sa`. Der Client sprach über einen mitschneidenden TCP-Proxy im Client-Container; der Proxy zerlegt die TDS-Pakete und die Tokens nach dem Login (Pakettypen, Status-Bits, Tokenfolge, DONE-Status). Der Proxy-Parser kennt nur die Tokens der Szenarien; was er nicht kennt, steht nicht in den Tabellen. R beantwortet, **was ein echter SQL Server sendet**, nicht, was ein Server dieses Repos senden muss.
- **T — Testserver.** Ein in C# geschriebener Server im Client-Prozess (Skript je SQL-Text), der PRELOGIN (`ENCRYPT_NOT_SUP`), LOGIN7 (LOGINACK, DONE) und je Szenario eine feste Tokenfolge beantwortet und ATTENTION-Pakete liest. T beantwortet, **wie der Referenzclient auf eine bestimmte Antwort reagiert**; das sagt nichts darüber, was ein echter Server sendet, und der Testserver ist keine Vorwegnahme der Implementierung.

Zahlen und Verhalten stammen aus je einem Lauf je Zeile; R wurde zweimal gefahren (der zweite Lauf mit erweitertem Token-Parser), das Verhalten stimmte überein, Zeiten und die Zahl der vor dem Cancel schon gesendeten Zeilen (R12) nicht. Alle Reuse-Aussagen heißen: eine unmittelbar folgende Abfrage (`SELECT 42`, in R08/R09 `SELECT @@TRANCOUNT`) auf **derselben** `SqlConnection` lieferte ihre eigene, richtige Antwort.

### R — echter SQL Server: Fehler und Abschluss

DONE-Status in Kurzform: `ERR` = Bit 0x0002, `MORE` = 0x0001, `COUNT` = 0x0010, `ATTN` = 0x0020, `SRVERR` = 0x0100.

| Nr | Fall | Tokenfolge (Server → Client) | Client / Verbindung danach |
|---|---|---|---|
| R01 | `SELECT 1/0` | COLMETADATA, ERROR 8134 (Class 16), DONE[ERR] | `SqlException` 8134/16/1; Verbindung offen, Folgeabfrage richtig |
| R02 | `SELEC 1` | ERROR 2812 (Class 16, State 62), DONEPROC[ERR] (der Batch wurde als Prozedurname gelesen, es ist kein Syntaxfehler 102) | `SqlException`; offen, Folgeabfrage richtig |
| R03 | Zeilen, dann Fehler: `SELECT CONVERT(int,v) FROM (VALUES('1'),('2'),('x'),('4')) t(v)` | COLMETADATA, ROW×2, ERROR 245 (Class 16), DONE[ERR] in **einer** Nachricht | `Read` liefert 2 Zeilen, dann `SqlException` 245; offen, Folgeabfrage richtig |
| R04 | `SELECT 1; SELECT 1/0; SELECT 3` | COLMETADATA ROW DONE[MORE\|COUNT]; COLMETADATA ERROR 8134 DONE[MORE\|ERR]; COLMETADATA ROW DONE[COUNT] | Fehler wirft beim Weiterlesen des zweiten Resultsets, das dritte Statement lief und lieferte; offen, Folgeabfrage richtig |
| R05 | `SELECT 1; SELECT * FROM dbo.nope; SELECT 3` | COLMETADATA ROW DONE[MORE\|COUNT]; ERROR 208 (Class 16); DONE[ERR] (ohne MORE) | `SqlException` 208 bei `NextResult`; das dritte Statement lief **nicht**; offen, Folgeabfrage richtig |
| R06 | `SET XACT_ABORT ON; SELECT 1; SELECT 1/0; SELECT 3` | DONE[MORE]; COLMETADATA ROW DONE[MORE\|COUNT]; COLMETADATA ERROR 8134; SESSIONSTATE; DONE[ERR] (ohne MORE) | das dritte Statement lief **nicht**; offen, Folgeabfrage richtig |
| R07 | `SELECT 1; THROW 50001, N'boom', 1; SELECT 3` | COLMETADATA ROW DONE[MORE\|COUNT]; ERROR 50001 (Class 16); DONE[ERR] (ohne MORE) | das dritte Statement lief **nicht**; offen, Folgeabfrage richtig |
| R08 | `BEGIN TRAN; SELECT 1/0` (ohne `XACT_ABORT`) | ENVCHANGE (Transaktionsbeginn), DONE[MORE]; COLMETADATA ERROR 8134 SESSIONSTATE DONE[ERR] | `@@TRANCOUNT` der Folgeabfrage = **1**: die Transaktion blieb offen |
| R09 | `SET XACT_ABORT ON; BEGIN TRAN; SELECT 1/0` | … ERROR 8134, ENVCHANGE (Rollback), SESSIONSTATE, DONE[ERR] | `@@TRANCOUNT` der Folgeabfrage = **0** |
| R10 | `RAISERROR(…, n, 1) WITH LOG`, n = 10 | INFO 50000 (Class 0), DONE (ohne ERR) | keine Ausnahme; `InfoMessage`; offen |
| R10 | n = 11, 16, 18, 19 | ERROR 50000 (Class n), DONE[ERR] | `SqlException` mit Class n; offen, Folgeabfrage richtig |
| R10 | n = 20, 25 | ERROR 50000 (Class n), ERROR 2745 (Class 16), SESSIONSTATE, ERROR 596 (Class 21), DONE[ERR\|SRVERR]; danach schließt der **Server** die TCP-Verbindung | `SqlException` mit vier `Errors`; `conn.State` = Closed; die Folgeabfrage scheitert, weil die Verbindung nicht offen ist |
| R17 | Deadlock zweier Verbindungen (je eine Transaktion mit `UPDATE`, `INSERT` in ein Log, `WAITFOR 1 s`, zweites `UPDATE`) | Opfer: ENVCHANGE (Rollback), … ERROR 1205 (Class 13, State 51), ENVCHANGE, DONE[ERR]; Gewinner: Commit | Opfer: `SqlException` 1205; **jede** Verbindung sandte genau **einen** SQL_BATCH; beide Verbindungen offen, `@@TRANCOUNT` 0; im Log steht eine Zeile (die des Gewinners). Weder Client noch Server führten das Opfer erneut aus |

### R — echter SQL Server: ATTENTION, Timeout, Pooling

| Nr | Fall | Auf der Leitung | Client / Verbindung danach |
|---|---|---|---|
| R11 | `Command.Cancel()` nach 500 ms bei `WAITFOR DELAY '00:00:10'` | Client sendet Pakettyp 0x06 (ATTENTION) 3 ms nach dem Aufruf; Server antwortet mit **zwei** Nachrichten: (1) SESSIONSTATE, DONE[ERR]; (2) DONE[ATTN] | `SqlException` Number 0, Class 11 (`A severe error occurred on the current command`); offen, Folgeabfrage richtig |
| R12 | `Cancel()` nach 100 gelesenen Zeilen eines großen Streams (Dreifach-Kreuzprodukt von `sys.all_objects`) | Der Server sendet die bereits eingereihten Pakete zu Ende (Lauf 1: 2594 Zeilen, Lauf 2: 10605 Zeilen, danach SESSIONSTATE und DONE[ERR]), dann eine eigene Nachricht DONE[ATTN] | Die Anwendung las 2495 bzw. 10506 weitere Zeilen nach dem `Cancel()`, dann `SqlException` Class 11; offen, Folgeabfrage richtig |
| R13 | `Cancel()` nach `ExecuteReader`, **nachdem** der Server die kleine Antwort (`SELECT 1`) vollständig gesendet hatte, vor dem Lesen | Client sendet trotzdem ATTENTION; Server antwortet mit einer Nachricht DONE[ATTN] | die gesendete Zeile ist lesbar, danach `SqlException` Class 11 (`Operation cancelled by user`); offen, Folgeabfrage richtig |
| R14 | `Cancel()` nach vollständigem Lesen der Antwort | **kein** ATTENTION-Paket auf der Leitung | keine Ausnahme; offen, Folgeabfrage richtig |
| R15 | `CommandTimeout` 1 s bei `WAITFOR DELAY '00:00:05'` | Client sendet ATTENTION nach 1003 ms; Antwort wie R11 (zwei Nachrichten) | `SqlException` Number −2, Class 11; offen, Folgeabfrage richtig |
| R16 | `CommandTimeout` 1 s, `SELECT 1; WAITFOR DELAY '00:00:05'; SELECT 2` | ATTENTION nach 1000 ms; Server sendet die bis dahin fertigen Tokens (COLMETADATA ROW SESSIONSTATE DONE[ERR]) und DONE[ATTN] | `SqlException` −2/11; offen, Folgeabfrage richtig |
| R18 | `Pooling=true`: Fehler `SELECT 1/0`, `Close()`, erneut `Open()`, Abfrage | Das erste Paket nach dem erneuten `Open()` trägt im Header das Status-Bit 0x08 (RESETCONNECTION); die Antwort beginnt mit ENVCHANGE | richtig; dieselbe physische Verbindung (eine TCP-Verbindung im Mitschnitt) |
| R19 | `Pooling=true`, Zeilenfehler, Verbindung wiederverwendet | wie R18: RESETCONNECTION im ersten Paket | richtig |

### T — Testserver: Reaktion des Referenzclients

| Nr | Server sendet | Client / Verbindung danach |
|---|---|---|
| S01 | ERROR (Class 16), DONE[ERR] | `SqlException`; offen, Folgeabfrage richtig |
| S02 | dasselbe mit Class 11 | wie S01 |
| S20 | dasselbe mit Class 16 bis 19 | `SqlException` mit der Class; offen, Folgeabfrage richtig (Class 10 als ERROR-Token: keine Ausnahme) |
| S03, S04, S20 | dasselbe mit Class 20 bis 25, der Server lässt die Verbindung **offen** | `SqlException`; der **Client** schließt die Verbindung selbst (`conn.State` = Closed, der Server sieht das Ende der Verbindung nach rund 40 ms) |
| S05 | ERROR, DONE[MORE\|ERR], COLMETADATA ROW DONE | `ExecuteReader` wirft den Fehler; offen, Folgeabfrage richtig (das folgende Resultset war nicht erreichbar) |
| S06 | COLMETADATA ROW ROW ERROR DONE[ERR] | `Read` liefert 2 Zeilen, dann `SqlException`; offen, Folgeabfrage richtig |
| S07 | wie S06, aber DONE **ohne** ERR-Bit | gleiche Ausnahme wie S06 (der Client wertet das ERROR-Token); offen, Folgeabfrage richtig |
| S08 | ERROR, dann Server schließt (kein DONE) | `SqlException` 50001 **und** ein Transportfehler (`Errors` = 2); Closed |
| S09 | COLMETADATA ROW ROW, dann Server schließt (weder ERROR noch DONE) | Transportfehler Number 0, Class 20 nach 2 Zeilen; Closed |
| S10 | ERROR, **kein** DONE, Server hält die Verbindung offen; `CommandTimeout` 3 s | nach 8176 ms `SqlException` 50001 plus Timeout (Errors = 2); Closed. Die Zeit entspricht 3 s Timeout plus rund 5 s; die 5 s sind wie in S18 als Wartezeit auf die Bestätigung der ATTENTION gedeutet (abgeleitet aus den Zeiten, nicht am Paketstrom belegt) |
| S21 | ERROR, DONE[ERR], dann Server schließt | `ExecuteReader` wirft den Fehler 50001 (Verbindung zeigt noch Open); die **nächste** Abfrage scheitert mit Transportfehler Number 0, Class 20, Closed; kein selbsttätiges Wiederverbinden |
| S22 | reguläre Antwort (COLMETADATA ROW DONE), dann Server schließt | Antwort richtig gelesen; Verbindung zeigt Open; die nächste Abfrage scheitert wie S21 |
| S11 | Zeilenstrom; auf ATTENTION: DONE[ATTN] | `SqlException` Number 0, Class 11 („Operation cancelled by user“); die Anwendung las noch 9829 Zeilen nach dem `Cancel()` (bereits gesendet); offen, Folgeabfrage richtig |
| S12 | Zeilenstrom; auf ATTENTION: DONE **ohne** ATTN-Bit | Der Client wertet es nicht als Bestätigung: `SqlException` −2 (Timeout) nach 20075 ms bei `CommandTimeout` 10 s (davon 10012 ms in `Dispose` des Readers); Closed |
| S13 | Zeilenstrom; auf ATTENTION: DONE[ATTN] **zweimal** in derselben Nachricht | wie S11; offen, Folgeabfrage richtig (die zweite Bestätigung störte hier nicht; die Ursache wurde nicht untersucht) |
| S14 | Zeilenstrom; ATTENTION wird gelesen und ignoriert, der Server sendet alle 400000 Zeilen und DONE | Der Client las alle 400000 Zeilen; Timeout −2 nach 20142 ms (`CommandTimeout` 10 s); Closed |
| S15 | Antwort vollständig gesendet; Cancel danach: ATTENTION kommt an, Server sendet DONE[ATTN] | `SqlException` Class 11; offen, Folgeabfrage richtig |
| S16 | wie S15, der Server ignoriert die ATTENTION | Timeout −2 nach 20083 ms (`CommandTimeout` 10 s); Closed |
| S17 | Server antwortet auf die Anfrage nicht; `CommandTimeout` 2 s; auf ATTENTION: DONE[ATTN] | `SqlException` −2 nach 2086 ms; offen, Folgeabfrage richtig |
| S18 | wie S17, der Server ignoriert die ATTENTION | `SqlException` −2 nach 7042 ms (2 s plus rund 5 s Wartezeit auf die Bestätigung); Closed |
| S19 | `Cancel()` nach vollständigem Lesen | der Testserver sah kein ATTENTION-Paket; Verbindung offen |

Nebenbefund zum Testserver (kein Gegenstand dieser ADR, aber für den Codec): Ein COLMETADATA für `INTN` mit Flags `0x0000` ließ den Client mit „Internal connection fatal error“ (Token 0) scheitern, mit Flags `0x0021` (nullable) nicht. Gemessen nur an T.

### Wo diese Messung von [ADR-0004](0004-session-zustandsautomat.md) abhängt

[ADR-0004](0004-session-zustandsautomat.md) (Session-Zustandsautomat) wählt den expliziten Automaten mit `Mutex` je Sitzung (Status Proposed). Die fünf Punkte unten sind damit **nicht** beantwortet: ADR-0004 führt sie als offen. Die Tabellen oben beschreiben Tokenfolgen und Clientreaktionen und gelten unabhängig von dessen Wahl. Die **Aussagen über den Server dieses Repos**, die man daraus ableitet, hängen an [ADR-0004](0004-session-zustandsautomat.md) an diesen Stellen:

1. **ATTENTION in jedem Anfragezustand.** R13/S15/S16 zeigen: Der Client sendet ATTENTION auch nach vollständig gesendeter Antwort, und ohne Bestätigung wartet er bis zum Timeout und schließt. Ob der Server eine ATTENTION im Zustand „Antwort fertig, noch nicht quittiert“ oder im Leerlauf beantworten muss, legt der Zustandsautomat fest; Eigentümer dieser Frage ist [ADR-0004](0004-session-zustandsautomat.md), sie steht dort unter „Nicht entschieden“. Gemessen ist nur, dass der Client eine Bestätigung erwartet und der echte Server sie in R13 sendet.
2. **Sitzungszustand nach einem Fehler.** R08/R09: Eine offene Transaktion überlebt einen Fehler (ohne `XACT_ABORT`). „Verbindung weiterverwenden“ umfasst also den Sitzungszustand; was davon der Server dieses Repos kennt (Transaktionen sind im Katalog nicht enthalten, siehe `SPEC-030`), legt [ADR-0004](0004-session-zustandsautomat.md) fest.
3. **RESETCONNECTION.** R18/R19: Mit Pooling trägt das erste Paket nach der Wiederverwendung das Status-Bit 0x08. Wie der Server es behandelt (Sitzungszustand zurücksetzen, Antwort mit ENVCHANGE), gehört zum Zustandsautomaten.
4. **Beenden nach DONE.** S21/S22: Das Ende der Verbindung nach einer vollständigen Antwort bemerkt der Client erst bei der nächsten Abfrage. Wann der Server nach einem Fehler oder bei `TDS_SHUTDOWN` schließt (`SPEC-044`, `SPEC-049`), ist eine Zustandsfrage.
5. **Fehler und Anfrage-Pipeline.** Der Client schickt in allen Szenarien höchstens eine Anfrage zurzeit; was passiert, wenn weitere Pakete hinter einem Fehler im Puffer liegen, ist nicht gemessen und hängt von der Lese-Disziplin des Zustandsautomaten ab.

### Nicht gemessen

- **TDS 8.0 und TLS.** Alle Läufe mit `Encrypt=false`; der Server R bietet TDS 8.0 nicht an ([ADR-0008](0008-dotnet-kompatibilitaetstests.md)), T hat keine TLS-Schicht. Dass Fehler- und ATTENTION-Framing unter TDS 8.0 gleich sind, ist **abgeleitet, nicht gemessen**.
- Ein Server dieses Repos: Es gibt keinen. Alle Aussagen über „was der Server tun muss“ sind aus R und T abgeleitet.
- Client-Disconnect mitten in einer Anfrage (der Client schließt den Socket), ATTENTION während PRELOGIN/Login, ATTENTION genau im Moment des letzten DONE (Race; R13 ist die geordnete Variante „Server war schon fertig“), mehrere ATTENTION hintereinander, MARS, asynchrone APIs, `ExecuteNonQuery` ohne Reader, RPC-Pfade (`sp_executesql`) mit Fehlern, Fehler in einer Prepared-Statement-Handle-Sequenz, `SqlBulkCopy`, andere Clients (JDBC, ODBC, Go, Python), andere Versionen von `Microsoft.Data.SqlClient`.
- Der Server R mit Klasse ≥ 20 außer mit `RAISERROR … WITH LOG` und `sa`; andere Fehlerquellen der Klasse 20+ (zum Beispiel Speicher- oder Plattenfehler) sind nicht erzeugt.
- Der Client-Verbindungsaufbau nach einem Abbruch (Kosten eines erneuten Login), Pool-Verhalten bei einer vom Server geschlossenen Verbindung (S21/S22 liefen nur mit `Pooling=false`), die selbsttätige Wiederherstellung (Connection Resiliency / Session Recovery): der Testserver bot sie nicht an; was R dazu sendet, wurde nicht ausgewertet.
- Lastverhalten, Dauer, Häufigkeit (ein Lauf je Zeile, ein Host).
- Option C: Es gibt keine Messung eines Wiederholungsmechanismus im Server. R17 zeigt nur, dass der echte Server und der Client im gemessenen Deadlock-Fall nicht selbst wiederholten.

## Entscheidung

Wir wählen die **enge Form von Option B mit Option A als Grundregel** (Entscheidung des Nutzers, 2026-10-30; die Wahl ist eine Setzung, die Messungen stützen sie in den unten genannten Punkten). A ist die Grundregel: im Zweifel schließt der Server. B gilt nur für die zwei Fälle unten.

**Die Verbindung bleibt offen** nur

1. nach einem Fehler der Class ≤ 19, den der Server vollständig mit ERROR und DONE[ERR] beendet hat (Ausnahme: der abgelehnte Login, siehe unten), und
2. nach Cancel oder Timeout, wenn DONE[ATTN] gesendet wurde.

**Der Server schließt die Verbindung nach ERROR und DONE[ERR]** bei

- Class ≥ 20,
- `TDS_INTERNAL_ERROR` ([SPEC-048](../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder)),
- `TDS_SHUTDOWN` ([SPEC-049](../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder)).

**Der Server schließt die Verbindung außerdem**, wenn der Strom nicht mehr sicher abgeschlossen werden kann ([SPEC-031](../../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität)), und zwar ohne ERROR und DONE (Entscheidung des Nutzers, 2026-10-30, wie `SPEC-031` und die Architektursicht): ERROR und DONE werden nur gesendet, solange der Strom synchron ist.

**Ausnahme für das Ende des Handshakes** (Entscheidung des Nutzers, 2026-10-30): Nach einem abgelehnten Login (feste Antwort ERROR 18456, Class 14, DONE mit Fehlerstatus, [SPEC-012](../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder)) schließt der Server die Verbindung; ein weiteres LOGIN7 wird nicht angenommen. Das ist eine Ausnahme zur Class-Regel (Class 14 liegt unter 20) und eine **Setzung, nicht gemessen**: am echten SQL Server wurde nur die Fehlerantwort einmal beobachtet, nicht das Verhalten der Verbindung danach.

**Class der eigenen Fehlercodes** (Entscheidung des Nutzers, 2026-10-30, Setzung; die Class-Werte sind nicht an Referenzclients gemessen): Anfragefehler (zum Beispiel `TDS_UNSUPPORTED_SQL`, `TDS_BACKEND_ERROR`) tragen Class 16, die Verbindung bleibt offen; `TDS_PROTOCOL_ERROR` und `TDS_INTERNAL_ERROR` tragen Class 20, der Server schließt. Die Zuordnung **jedes** Codes legt der Slice für das Fehler-Mapping fest; diese ADR legt sie nicht fest.

Beim geordneten Beenden (`SPEC-044`) endet die Verbindung auch nach DONE[ATTN] (aus `SPEC-044` Nr. 2 und 4 abgeleitet, nicht gemessen).

**Gestützt durch die Messung:** Der echte SQL Server behielt die Verbindung bei allen gemessenen Fehlern der Class ≤ 19 (R01 bis R10, R17) und beendete sie bei Class 20 und 25 (R10, ausgelöst über `RAISERROR … WITH LOG` als `sa`; Class 21 bis 24 und andere Auslöser sind nicht gemessen); nach Cancel und Timeout blieb sie offen (R11 bis R16). Der Referenzclient verträgt „ERROR, DONE, Schließen“ (T, S21), hängt aber bei fehlendem DONE oder fehlender ATTN-Bestätigung bis zum Timeout und schließt dann selbst (T, S10, S12, S14, S16, S18); „im Zweifel schließen“ ist für diesen Client billiger als „im Zweifel weitermachen“ (abgeleitet, nicht gemessen mit Pooling). Ein Server, der bei jedem Fehler schließt (A), weicht vom gemessenen Verhalten des echten Servers ab (R01 bis R09, R17).

**Grenzen der Wahl:** Sie ist nicht gemessen an einem Server dieses Repos (er existiert nicht), nicht mit TDS 8.0, nicht mit anderen Clients und nicht mit Pooling bei einer vom Server geschlossenen Verbindung. Sie hängt in den mit **[0004]** markierten Punkten (ATTENTION in jedem Anfragezustand, Sitzungszustand nach Fehler, `RESETCONNECTION`) von [ADR-0004](0004-session-zustandsautomat.md) ab. Das Schwellwert-Verhalten gilt für die Class, die der Server in seinem Fehler-Mapping ausgibt ([TDS-FA-06.a](../../../spec/spezifikation.md#tds-fa-06a--fehlerabbildung)); für die eigenen Fehlercodes gelten die oben gesetzten Class-Werte, solange das Mapping einen Code nicht einzeln zuordnet, gilt für ihn die Grundregel A (im Zweifel schließen).

**Setzungen des Nutzers über die Messung hinaus:** (1) Die Schwelle ab Class 20 gilt auch für die nicht gemessenen Class 21 bis 24 und andere Auslöser. (2) `SPEC-048` verlangt für `TDS_INTERNAL_ERROR` ERROR und DONE mit Fehlerstatus und danach das Verbindungsende (vorher stand zum Verbindungsende nichts), `SPEC-049` verlangt für `TDS_SHUTDOWN` zusätzlich zum ERROR ein DONE mit Fehlerstatus (vorher ERROR, danach Verbindungsende). Beides ist Setzung: ein interner Fehler und ein Herunterfahren wurden am echten Server nicht gemessen, und R10 (Class 20 und 25 über `RAISERROR … WITH LOG`) stützt das Schließen bei diesen beiden Codes nicht. (3) Das Schließen nach abgelehntem Login, (4) die Class-Werte 16 und 20 der eigenen Codes und (5) das Schließen ohne ERROR und DONE bei nicht abschließbarem Strom sind ebenfalls Setzung (siehe oben).

**Nicht entschieden und in dieser ADR als offen geführt:**

- ob Cancel und Timeout als Fehler gelten, die unter die Regel „Fehler“ fallen ([SPEC-016](../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder) nennt „Fehler/Disconnect“ für `TDS_TIMEOUT`);
- die Abbildung statement-abbrechender (R04) und batch-abbrechender Fehler (R05 bis R07) im Strom: Zuständigkeit Backend-/Mapping-Slice nach Messung (die Fehlersemantik im Strom selbst folgt `SPEC-031`: ERROR und DONE bei synchronem Strom, sonst Schließen ohne Abschluss, siehe oben; dieselbe Abgrenzung führt [ADR-0006](0006-backend-vertrag-und-streaming.md));
- die Zuordnung jedes eigenen Fehlercodes zu einer Class im Mapping (die Werte 16 und 20 für die genannten Gruppen sind oben gesetzt): Zuständigkeit Fehler-Mapping-Slice;
- die Reichweite von ATTENTION im Handshake (PRELOGIN, TLS, LOGIN7) und die Antwort auf unzulässige Übergänge (dieselben Punkte führt [ADR-0004](0004-session-zustandsautomat.md));
- der Inhalt der Cancel-Antwort: zwei Nachrichten wie am echten Server (R11, R12) oder nur DONE[ATTN] (T, S11 genügte dem Referenzclient);
- die Behandlung von `RESETCONNECTION` und der Sitzungszustand nach einem Fehler (offene Transaktion, R08/R09).

## Verglichene Alternativen

| Option                                    | Pro                                       | Contra                                            |
| ----------------------------------------- | ----------------------------------------- | ------------------------------------------------- |
| A — Verbindung bei jedem Fehler schließen | Einfache Isolation                        | Schlechte Client-Wiederverwendung                 |
| **B — zustandsabhängige Recovery (enge Form, mit A als Grundregel gewählt)** | Wiederverwendung nach definierten Fehlern | Komplexe Token- und Cancel-Sequenz                |
| C — generisches Retry im Server           | Kann temporäre Fehler abfedern            | Gefahr doppelter Ausführung und falscher Semantik |
| 0 — keine Festlegung vor der Fehler-/Cancel-Matrix | Keine Vorfestlegung der Recovery-Regeln | Blockiert `SPEC-029`, `SPEC-031` und `SPEC-016` (die Matrix liegt jetzt als Messung vor, siehe oben) |

### Pro und Contra mit Messbelegen

Kennzeichnung: **(R)** am echten SQL Server gemessen, **(T)** am Testserver mit dem Referenzclient gemessen, **(abgeleitet)** Folgerung aus den Messungen, nicht gemessen. Stellen, die an [ADR-0004](0004-session-zustandsautomat.md) hängen, sind mit **[0004]** markiert.

**Option A — Verbindung bei jedem Fehler schließen**

- Pro: Der Client kommt mit „Fehler, dann Ende der Verbindung“ zurecht, wenn der Fehler vollständig gesendet wurde: ERROR, DONE[ERR], dann Schließen ergibt die richtige `SqlException` und scheitert erst die nächste Abfrage mit Class 20 (T, S21); eine reguläre Antwort mit anschließendem Schließen wird richtig gelesen (T, S22). Ohne DONE kommt zum Fehler ein Transportfehler hinzu (T, S08, `Errors` = 2), also gehört das DONE auch bei A dazu.
- Pro: Keine Folgeabfrage kann auf einem Strom mit Restdaten laufen, weil keine Folgeabfrage kommt (abgeleitet).
- Pro: Die Randfälle der Wiederverwendung aus den Tabellen entfallen: offene Transaktion nach Fehler (R08) und Zeilen-dann-Fehler-Sequenzen müssen nicht fortgeführt werden **[0004]**.
- Contra: Weicht vom echten Server ab. Der echte Server behielt die Verbindung bei allen gemessenen Fehlern der Class ≤ 19 (R01 bis R10, R17) und schloss nur bei Class 20 und 25 (R10). Eine Anwendung, die Fehler wie 8134, 208 oder 1205 gewohnt abfängt und weiterarbeitet, verliert bei A jedes Mal die Verbindung (abgeleitet). Die Kosten eines erneuten Verbindungsaufbaus sind nicht gemessen.
- Contra: Nach einem Fehler zeigt `conn.State` noch Open, die **nächste** Abfrage scheitert mit Class 20 (T, S21/S22). Wie sich das mit Pooling auswirkt (eine tote Verbindung geht in den Pool zurück), ist nicht gemessen (S21/S22 liefen mit `Pooling=false`); das ist das Hauptrisiko von A für Clients mit Pool (abgeleitet, nicht gemessen).
- Contra: A regelt Fehler, nicht Abbruch. Das Cancel/Timeout (R11 bis R16) ist kein Serverfehler; schließt der Server dabei ebenfalls, ist die Ausnahme beim Client Class 11 gefolgt von einem Verbindungsende. Nicht gemessen: DONE[ATTN] mit anschließendem Schließen (abgeleitet aus S21/S22: erst die nächste Abfrage scheitert).
- Offen bei A: ob „Fehler“ auch Cancel und Timeout umfasst.

**Option B — zustandsabhängige Recovery**

- Pro: Entspricht dem Verhalten des echten Servers, das gemessen ist: Class ≤ 19 mit vollständigem ERROR und DONE[ERR] behält die Verbindung (R01 bis R10, R17), bei Class 20 und 25 beendet der Server sie (R10). Der Client trennt ab Class 20 selbst, auch wenn der Server offen lässt (T, S03/S04/S20, Schwelle zwischen 19 und 20).
- Pro: Cancel und Timeout lassen die Verbindung am echten Server offen (R11 bis R16); der Client verlangt dafür eine Bestätigung DONE[ATTN] **[0004]** (T, S11, S15, S17; ohne sie schließt der Client nach Timeout plus rund 5 s: T, S12, S14, S16, S18).
- Pro: Die Fehlerfälle sind gemessen aufzählbar: Fehler ohne Zeilen (R01), mit Zeilen davor in derselben Nachricht (R03), Statement-abbrechend (R04) und Batch-abbrechend (R05 bis R07) mit jeweils eigener DONE-Folge, Info ohne Ausnahme (R10, Class 10), Cancel (R11 bis R14).
- Contra: Die Zahl der Tokenfolgen ist größer als bei A. Gemessen sind am echten Server zehn unterscheidbare Tokenfolgen (R01, R03, R04, R05, R10 in drei Formen, R11, R12, R13), und der Server muss sie so beenden, dass der Client nicht hängt: Fehlendes DONE hängt bis zum Timeout (T, S10), DONE ohne ATTN-Bit als Bestätigung hängt bis zum Timeout (T, S12), eine ignorierte ATTENTION lässt den Client alle Zeilen lesen und dann in den Timeout laufen (T, S14).
- Contra: Sitzungszustand überlebt den Fehler: eine offene Transaktion bleibt (R08), mit `XACT_ABORT` wird sie zurückgerollt (R09) **[0004]**. „Verbindung darf weiterverwendet werden“ braucht deshalb eine Aussage über diesen Zustand; welche der Server dieses Repos überhaupt kennt, ist offen.
- Contra: Der echte Server sendet beim Cancel zwei Nachrichten (erst die abgebrochene Antwort mit DONE[ERR], dann DONE[ATTN]) und schickt nach dem `Cancel()` noch bereits eingereihte Zeilen (R11, R12). Ob der Server dieses Repos dasselbe tun muss oder nur das, was der Client verlangt (T: DONE[ATTN] genügte in S11), ist nicht entschieden **[0004]**; gemessen ist nur, dass der Client mit der einfacheren Form in T zurechtkam. T sagt nichts darüber, ob andere Clients mit der einfacheren Form zurechtkommen (nicht gemessen).
- Contra: `RESETCONNECTION` bei Pooling (R18/R19) ist Teil der Wiederverwendung **[0004]**.

**Option C — generisches Retry im Server**

- Pro: Kein Messbeleg dafür oder dagegen am Server; das Argument (temporäre Fehler abfedern) bleibt ein Argument, kein Messwert.
- Contra: Im gemessenen Deadlock (R17) wiederholten weder der echte Server noch der Client das Opfer, und der Client sah den Fehler 1205 genau einmal; ein Retry im Server wäre also eine Abweichung vom echten Server (abgeleitet, ein Fall). Eine Wiederholung veränderte das, was der Client sieht (kein Fehler statt 1205).
- Contra: Gefahr doppelter Ausführung bleibt eine Folgerung: Das Simulations-Backend schreibt über Callbacks (`SPEC-035`, `onInsert`, `onUpdate`, `onDelete`); eine Wiederholung nach einem Teilerfolg würde sie doppelt aufrufen (abgeleitet; nicht gemessen, weil es keine Implementierung gibt). Ob ein Fehler „temporär“ ist, ist im Backend-Vertrag nicht gemessen oder festgelegt.
- Contra: Löst weder die Frage nach Verbindungswiederverwendung noch die nach ATTENTION; A oder B bliebe nötig (abgeleitet).

**Option 0 — keine Festlegung:** Die Matrix liegt vor; die offenen Fragen aus dem Abschnitt „Wo diese Messung von [ADR-0004](0004-session-zustandsautomat.md) abhängt“ bleiben auch bei 0 offen. Blockiert die Folgepflichten unten.

### Empfehlung des Spike-Agenten (Grundlage der Entscheidung)

Der Nutzer hat diese Form gewählt (siehe Entscheidung). Die Empfehlung des Spike-Agenten, begründet nur mit den Messungen oben, lautete:

**Eine enge Form von B mit A als Grundregel:** Die Verbindung bleibt nur für Fehler, die der Server vollständig mit ERROR und DONE[ERR] beendet hat und deren Class ≤ 19 ist, sowie nach Cancel/Timeout mit gesendetem DONE[ATTN] (das ist die am echten Server gemessene Regel, R). In allen anderen Fällen — Class ≥ 20, `TDS_INTERNAL_ERROR` (`SPEC-048`), `TDS_SHUTDOWN` (`SPEC-049`), ein Strom, der nicht mehr sicher abgeschlossen werden kann (`SPEC-031`) — schließt der Server nach dem ERROR und DONE[ERR]. Gründe aus der Messung: (1) der echte Server verhält sich so (R10, R01 bis R07), (2) der Client verträgt „ERROR, DONE, Schließen“ (T, S21), hängt aber bei fehlendem DONE oder fehlender ATTN-Bestätigung bis zum Timeout und schließt dann selbst (T, S10, S12, S14, S16, S18), also ist „im Zweifel schließen“ für den Client billiger als „im Zweifel weitermachen“ (abgeleitet), (3) ein Server, der bei jedem Fehler schließt (A), bräche mit dem Verhalten, das Anwendungen am echten Server gewohnt sind (R01 bis R09, R17), und dessen Auswirkung mit Pooling ist nicht gemessen. Der Wortlaut „nach dem ERROR und DONE[ERR]“ gilt in der Empfehlung für alle vier Fälle; die Entscheidung schließt den nicht abschließbaren Strom ohne ERROR und DONE. Die Empfehlung ist **nicht** gemessen am Server dieses Repos (er existiert nicht), nicht gemessen mit TDS 8.0 und nicht mit anderen Clients; sie hängt in den mit **[0004]** markierten Punkten (ATTENTION in jedem Anfragezustand, Sitzungszustand nach Fehler, RESETCONNECTION) von [ADR-0004](0004-session-zustandsautomat.md) ab; diese ADR wird erst `Accepted`, wenn [ADR-0004](0004-session-zustandsautomat.md) entschieden ist.

## Konsequenzen

- **Positiv:** Nach jedem Fehlerfall ist definiert, ob die Verbindung weiterverwendet werden darf; die Regel folgt dem gemessenen Verhalten des echten Servers und schließt im Zweifel.
- **Negativ / Risiko:** Zustandsabhängige Recovery erhöht die Zahl der zu testenden Token- und Cancel-Sequenzen. Wo der Server schließt, verliert ein Client mit Pool die Verbindung; die Wirkung mit Pooling bei einer vom Server geschlossenen Verbindung ist nicht gemessen.
- **Folgepflicht:** Die Recovery-Regel in `TDS-FA-06.a`, `SPEC-029`, `SPEC-048` und `SPEC-049` festlegen (geschehen); `SPEC-011` und `SPEC-016` nachziehen, sobald die Behandlung von Cancel und Timeout als Fehler entschieden ist (`SPEC-031` trägt die Entscheidung zum nicht abschließbaren Strom bereits); Tests: Folgeabfrage nach Fehler und ATTENTION, Schließen nach Class ≥ 20, `TDS_INTERNAL_ERROR` und `TDS_SHUTDOWN`.
- **Entschieden (Setzung des Nutzers, 2026-10-30):** Nach abgelehntem Login schließt der Server (Ausnahme zur Class-Regel, nicht gemessen). Eigene Anfragefehler tragen Class 16 (Verbindung bleibt offen), `TDS_PROTOCOL_ERROR` und `TDS_INTERNAL_ERROR` Class 20 (Server schließt); die Zuordnung jedes Codes legt der Fehler-Mapping-Slice fest. Ein nicht abschließbarer Strom wird ohne ERROR und DONE geschlossen. Dazu: Class ≥ 20 schließt, Class ≤ 19 mit vollständigem ERROR und DONE[ERR] lässt offen. Gemessen am echten Server sind nur Class 20 und 25 (R10, `RAISERROR … WITH LOG`, `sa`) und die Fehler der Class ≤ 19 aus R01 bis R10 und R17; der Referenzclient schließt bei Class 20 bis 25 selbst (T). Class 21 bis 24 und andere Auslöser sind Setzung (Grundregel A). Nach `TDS_SHUTDOWN` und nach einem internen Fehler (`SPEC-048`) endet die Verbindung stets: Setzung, nicht gemessen.
- **Offen:** Cancel und Timeout als Fehler; Abbildung statement- und batch-abbrechender Fehler im Strom (Zuständigkeit Backend-/Mapping-Slice, nach Messung); ATTENTION im Handshake; Inhalt der Cancel-Antwort (eine oder zwei Nachrichten); `RESETCONNECTION`; Zuordnung jedes eigenen Fehlercodes zu seiner Class (Zuständigkeit Fehler-Mapping-Slice).
- **Offen durch [ADR-0004](0004-session-zustandsautomat.md):** ATTENTION in jedem Anfragezustand, Sitzungszustand nach Fehler (Transaktionen), `RESETCONNECTION`, Zeitpunkt des Schließens nach `TDS_SHUTDOWN`; Einzelheiten im Abschnitt „Wo diese Messung von [ADR-0004](0004-session-zustandsautomat.md) abhängt“. Eine Entscheidung dieser ADR vor [ADR-0004](0004-session-zustandsautomat.md) bindet diese Punkte nicht mit.
- **Nicht gemessen** (Auszug, Vollständiges im Abschnitt „Nicht gemessen“): TDS 8.0 und TLS, Client-Disconnect mitten in der Anfrage, ATTENTION-Race im Moment des letzten DONE, Pooling mit vom Server geschlossener Verbindung, andere Clients.

## Fitness Function (falls maschinell prüfbar)

| Tooling                                     | Regel                                                                                                                                 | Make-Target                         |
| ------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------- |
| JVM-/Protokolltests und .NET-Referenzclient | Nach Fehler der Class ≤ 19 und nach ATTENTION mit DONE[ATTN] liefert die unmittelbar folgende Abfrage auf derselben Verbindung nur ihre eigene Antwort; nach Class ≥ 20, `TDS_INTERNAL_ERROR` und `TDS_SHUTDOWN` ist die Verbindung nach ERROR und DONE geschlossen, bei nicht mehr abschließbarem Strom ohne ERROR und DONE geschlossen; nach abgelehntem Login ist die Verbindung geschlossen und ein weiteres LOGIN7 wird nicht angenommen. Noch kein bestandenes Gate. | — (Gate noch einzurichten) |

## Re-Evaluierungs-Trigger

Neue Recovery-Lücke oder reproduzierbare Desynchronisation; ein Client mit Pool, der bei geschlossener Verbindung scheitert; eine Messung an einem Server dieses Repos oder mit TDS 8.0, die der Regel widerspricht.

## Geschichte

| Datum      | Ereignis                      | Verweis |
| ---------- | ----------------------------- | ------- |
| 2026-10-29 | Proposed – Entscheidung offen | —       |
| 2026-10-29 | Bezug auf `SPEC-044`, `SPEC-047`, `SPEC-048`, `SPEC-049` (geordnetes Beenden, interne Fehler) ergänzt; Entscheidung weiter offen | [SPEC-044](../../../spec/spezifikation.md#spec-044--geordnetes-beenden) |
| 2026-10-30 | Messung ergänzt (Spike mit .NET-Referenzclient gegen einen echten SQL Server 2022 und gegen einen Testserver): Optionstabelle mit Messbelegen und Empfehlung des Spike-Agenten; Status weiter Proposed, keine Option gewählt | [TDS-FA-06](../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände), [TDS-FA-07](../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), [TDS-QA-07](../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität) |
| 2026-10-30 | Enge Form von Option B mit A als Grundregel gewählt (Entscheidung des Nutzers, 2026-10-30); Status weiter Proposed bis Review und Verifikation; Cancel und Timeout als Fehler, Fehlersemantik im Strom, ATTENTION im Handshake und Inhalt der Cancel-Antwort offen | [TDS-FA-06.a](../../../spec/spezifikation.md#tds-fa-06a--fehlerabbildung) |
| 2026-10-30 | Nach Review nachgeschärft (Status weiter Proposed): Schwelle auf die gemessenen Class 20 und 25 begrenzt, Verschärfungen in `SPEC-048` und `SPEC-049` als Setzung gekennzeichnet, Handshake-Ende, Class der eigenen Codes und der nicht abschließbare Strom als offen geführt, Eigentümer von „ATTENTION in jedem Anfragezustand“ benannt, Ausnahme für das geordnete Beenden | [TDS-FA-06.a](../../../spec/spezifikation.md#tds-fa-06a--fehlerabbildung) |
| 2026-10-30 | Nach Entscheidungen des Nutzers nachgeführt (Status weiter Proposed): Verbindungsende nach abgelehntem Login als Ausnahme und Setzung, Class 16 für Anfragefehler und Class 20 für `TDS_PROTOCOL_ERROR` und `TDS_INTERNAL_ERROR` als Setzung (Zuordnung jedes Codes beim Fehler-Mapping-Slice), nicht abschließbarer Strom ohne ERROR und DONE geschlossen; Abbildung statement- und batch-abbrechender Fehler als offen mit Zuständigkeit | [TDS-FA-06.a](../../../spec/spezifikation.md#tds-fa-06a--fehlerabbildung) |
