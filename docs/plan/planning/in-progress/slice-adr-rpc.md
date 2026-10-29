# Slice slice-adr-rpc: ADR-0005 entscheiden: RPC-Unterstützung und Prepared Statements

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-entscheidungen-grundlage

**Bezug:** [TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md)

**Berührte Spec-Stellen:** SPEC-030 · SPEC-003 · ARC-005 · ARC-007

**Verantwortlich:** pt9912.

**Autor:** pt9912. **Datum:** 2026-10-29.

---

## 1. Ziel und Abgrenzung


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) ist mit einem Nachweis entschieden und `Accepted`; die offene Frage lautet: Unterstützte RPC-Formen und Prepared-Statement-Umfang.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Produktionscode der gewählten Option — er entsteht in einem Slice der funktionalen Welle, weil dieser Slice entscheidet und nicht baut.
- Änderungen am Lastenheft — eine ADR schärft höchstens die Spezifikation; ein Widerspruch zum Lastenheft ist ein eigener Vorgang.

## 2. Definition of Done


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [ ] Spike durchgeführt; seine Ergebnisse (Messwerte, Matrix, Befund) stehen in der ADR. Der Spike-Code ist temporär und nach der Entscheidung gelöscht.
- [ ] [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) auf `Accepted`, Option gewählt, Geschichte und ADR-Index nachgezogen.
- [ ] Spec-Stellen aus dem `Schärft:`-Feld der ADR an die Entscheidung angepasst.
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

## 3. Plan (vor Code)


Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `docs/plan/adr/0005-rpc-und-prepared-statements.md` | update | Entscheidung, Status, Geschichte |
| `docs/plan/adr/README.md` | update | Indexzeile |
| Spike (temporär, nicht im Repo behalten) | neu, danach löschen | Ergebnisse gehen in die ADR |
| `spec/spezifikation.md` | update | Stellen aus `Schärft:` |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

**Zwischenstand der Messung** für [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) (Spike per Dockerfile ohne Mount; ein Testserver in C# auf .NET 10, TDS 7.4 unverschlüsselt (`Encrypt=false`, PRELOGIN-Antwort `ENCRYPT_NOT_SUP`), und der Referenzclient `Microsoft.Data.SqlClient` 7.1.1 im selben Prozess, `Pooling=false`, eine Verbindung je Szenario. Es lief kein Microsoft-SQL-Server; die Antworten stammen vom Testserver und belegen nicht, was ein echter Server sendet. Der Server dekodiert jede Anfrage und beantwortet sie in zwei Varianten: `min` mit genau einem DONE (Batch) bzw. DONEPROC (RPC), `std` mit COLMETADATA und ROW, RETURNSTATUS, RETURNVALUE für OUTPUT-Parameter und DONEPROC. Zahlen aus einem Lauf des Spike-Agenten, bislang nicht wiederholt.)

Die Anfragen mit Parametern sind in beiden Varianten byte-gleich; die Spalte „Beobachtete Anfrage“ gilt daher für beide. `OptionFlags=0x0002` ist `fNoMetaData`; Pakettyp `0x01` ist SQL Batch, `0x03` RPC, `0x0E` Transaction Manager. Die Collation in `sp_executesql`- und `sp_prepexec`-Parametern ist die, die der Server im Login per ENVCHANGE (Typ 7) sendet; der Client gibt sie unverändert zurück (`0904D00034` im Testserver).

| Szenario | Paket | Beobachtete Anfrage | Antwort, mit der der Client ohne Fehler abschließt |
|---|---|---|---|
| `ExecuteNonQuery`/`ExecuteScalar`/`ExecuteReader` ohne Parameter, Text | `0x01` | SQL Batch, UTF-16-Text, davor ALL_HEADERS mit Transaktionsdeskriptor | `min`: ein DONE (`0xFD`, Status 0) genügt; `ExecuteNonQuery` liefert dann −1, der Reader hat 0 Felder. `std`: COLMETADATA, ROW, DONE mit COUNT; Zeilenzahl, Skalar und Feldzahl kommen an |
| Zwei Resultsets in einem Batch (`NextResult`) | `0x01` | ein Batch `SELECT 1; SELECT 2` | `min`: 1 Resultset. `std`: zwei COLMETADATA/ROW-Gruppen, die erste mit DONE `MORE\|COUNT` (`0x11`), die letzte mit `0x10`; der Client zählt 2 |
| Text mit Parametern (int, bigint, nvarchar, varchar, decimal(10,2), datetime2, bit, uniqueidentifier, varbinary) | `0x03` | `sp_executesql` (ProcID 10), `OptionFlags=0x0002`; Parameter 0 `NVARCHAR` (`0xE7`) mit dem SQL-Text, Parameter 1 `NVARCHAR` mit der Deklarationsliste (`@i int,@b bigint,@n nvarchar(50),…`), beide ohne Namen und ohne Statusbits; danach je Parameter Name und Wert (Typen siehe unten) | `min`: DONEPROC (`0xFE`). `std`: COLMETADATA/ROW, DONEINPROC, RETURNSTATUS, DONEPROC |
| `AddWithValue` ohne `SqlDbType` | `0x03` | `sp_executesql`; string wird `nvarchar(<Länge>)` mit Länge in Zeichen (`nvarchar(4)` für „text“), `int`→`INTN` 4, `long`→`INTN` 8, `decimal 1.50`→`NUMERICN` mit Precision 3 Scale 2, `DateTime`→`DATETIMN` 8, `bool`→`BITN`, `Guid`→`GUID` 16, `byte[]`→`BIGVARBINARY` | wie oben |
| `nvarchar(max)`/`varbinary(max)` mit 5000 Zeichen bzw. 9000 Byte; `varchar(max)` mit 1 Zeichen | `0x03` | `sp_executesql`; TYPE_INFO mit Länge `0xFFFF`, Wert als PLP (8-Byte-Gesamtlänge, Chunks, Terminator), im Test ein Chunk | wie oben |
| NULL-Parameter (int, nvarchar, varbinary, decimal, uniqueidentifier, datetime2) | `0x03` | `sp_executesql`; TYPE_INFO bleibt vollständig, der Wert ist der NULL-Sentinel des Typs: Länge 0 bei `INTN`, `NUMERICN`, `GUID`, `DATETIME2N`; `0xFFFF` bei `NVARCHAR`, `BIGVARBINARY` | wie oben |
| Unicode-Parameter (BMP, Surrogat-Paar, leerer String) | `0x03` | `sp_executesql`; `NVARCHAR` UTF-16LE, `maxlen` = 2 × Zeichenzahl der Deklaration; leerer String Länge 0 (nicht NULL) | wie oben |
| `ExecuteNonQuery` mit Parametern | `0x03` | `sp_executesql` | `min`: DONEPROC; `std`: RETURNSTATUS, DONEPROC; Zeilenzahl −1 |
| Text mit OUTPUT und InputOutput | `0x03` | `sp_executesql`; die Deklarationsliste enthält `@o int output,@io nvarchar(20) output`; im Parameter selbst Statusbyte `0x01`; der OUTPUT-Parameter trägt den NULL-Sentinel, der InputOutput-Parameter seinen Wert | `min`: Client schließt ab, die Werte bleiben unverändert (`o` leer, `io`=„x“). `std`: RETURNVALUE je OUTPUT-Parameter (`0xAC`) liefert `o`=42; ein RETURNVALUE mit NULL setzt `io` auf leer |
| `CommandType.StoredProcedure`, benannter Prozedurname, INPUT/OUTPUT/InputOutput/ReturnValue | `0x03` | RPC mit Prozedurname (`NameLenProcID` = Zeichenzahl, Name UTF-16, `OptionFlags=0x0000`), z. B. `dbo.p_out` oder `[dbo].[p_def]` unverändert wie in `CommandText`; Parameter mit Name und Typ, OUTPUT mit Statusbyte `0x01`; der ReturnValue-Parameter wird **nicht** gesendet; nicht gesetzte Parameter werden nicht gesendet (keine Default-Bits beobachtet) | `min`: DONEPROC, `rv`, `o` und `s` bleiben leer. `std`: RETURNSTATUS befüllt `rv` (0); RETURNVALUE befüllt `o` (42) |
| `StoredProcedure` ohne Parameter, `ExecuteReader` | `0x03` | RPC mit Name `p_rows`, keine Parameter | `std`: COLMETADATA/ROW, DONEINPROC, RETURNSTATUS, DONEPROC; 1 Zeile |
| `Prepare()` mit Parametern, danach dreimal `ExecuteScalar`, `Dispose`, `Close` | `0x03` | Aufruf 1: `sp_prepexec` (ProcID 13), Parameter 0 `INTN` 4 mit Statusbyte `0x01` OUTPUT und Wert −1 (Handle), Parameter 1 Deklarationsliste, Parameter 2 SQL-Text, danach die Nutzerparameter. Aufruf 2 und 3: `sp_execute` (ProcID 12), Parameter 0 `INTN` 4 mit dem Handle, danach nur die Nutzerparameter mit neuen Werten. Nach Aufruf 3 ging bei `Dispose` und `Close` nichts über die Leitung; kein `sp_unprepare` | `std`: RETURNSTATUS, RETURNVALUE mit dem Handle (Ordinal 0, Name leer, `INTN` 4), DONEPROC; die Folgeaufrufe tragen dieses Handle. `min` (ohne RETURNVALUE): der Client schließt ohne Fehler ab und sendet `sp_execute` mit Handle −1 weiter |
| `Prepare()` mit Parametern, `ExecuteNonQuery` zweimal, dann `CommandText` ändern | `0x03` | `sp_prepexec`, `sp_execute`, danach bei geändertem Text erneut `sp_prepexec`, jetzt mit dem alten Handle (102) im OUTPUT-Parameter statt −1; kein `sp_unprepare` | wie oben, RETURNVALUE liefert das neue Handle |
| Zwei vorbereitete Befehle, abwechselnd ausgeführt | `0x03` | je Befehl ein `sp_prepexec` mit Handle −1, danach `sp_execute` mit dem jeweils eigenen Handle (105, 106) | wie oben |
| `Prepare()` ohne Parameter (Text) | `0x01` | kein Prepare auf der Leitung: beide Ausführungen sind SQL Batch | DONE |
| `Prepare()` auf `StoredProcedure`-Befehl | `0x03` | kein Prepare auf der Leitung: beide Ausführungen sind der RPC mit Prozedurname | DONEPROC |
| `ExecuteReader(SchemaOnly)` | `0x01` | Batch ` SET FMTONLY OFF; SET FMTONLY ON;SELECT 1 SET FMTONLY OFF;` | DONE, Reader ohne Zeilen |
| `ExecuteReader(KeyInfo\|SingleRow)` mit Parameter | `0x03` | `sp_executesql` mit SQL-Text ` SET FMTONLY OFF; SET NO_BROWSETABLE ON; … ` (Rest des Textes nicht ausgegeben), d. h. der Server müsste die `SET`-Anweisungen im Text verstehen | `std`: wie oben |
| Zwei Resultsets über `sp_executesql` | `0x03` | `sp_executesql` | `std`: zwei COLMETADATA/ROW-Gruppen mit DONEINPROC `0x11` und `0x10`, RETURNSTATUS, DONEPROC; der Client zählt 2 |
| `BeginTransaction`/`Commit` (Nebenbefund) | `0x0E` | Transaction-Manager-Request (Begin `0x0005`, Commit `0x0007`), danach der Batch mit gesetztem Transaktionsdeskriptor in ALL_HEADERS | DONE genügte |

Typen, wie der Client sie in RPC-Parametern kodiert (Anfrage-Seite, beobachtet): `tinyint`/`smallint`/`int`/`bigint` als `INTN` (`0x26`) mit Länge 1/2/4/8; `float`/`real` als `FLTN` (`0x6D`) 8/4; `money`/`smallmoney` als `MONEYN` (`0x6E`) 8/4; `bit` als `BITN` (`0x68`) 1; `decimal` als `NUMERICN` (`0x6C`) mit `maxlen` 17, Precision und Scale wie am Parameter gesetzt, Wert als Vorzeichenbyte plus 16 Byte; `datetime`/`smalldatetime` als `DATETIMN` (`0x6F`) 8/4; `date` als `DATEN` (`0x28`); `time`/`datetime2`/`datetimeoffset` als `TIMEN`/`DATETIME2N`/`DATETIMEOFFSETN` (`0x29`/`0x2A`/`0x2B`) mit Scale 7; `uniqueidentifier` als `GUID` (`0x24`) 16; `char`/`varchar` als `BIGCHAR`/`BIGVARCHAR` (`0xAF`/`0xA7`) mit Collation, Wert 1 Byte je Zeichen; `nchar`/`nvarchar` als `NCHAR`/`NVARCHAR` (`0xEF`/`0xE7`) mit Collation; `binary`/`varbinary` als `BIGBINARY`/`BIGVARBINARY` (`0xAD`/`0xA5`); `xml` als `XML` (`0xF1`, ohne Schema) mit PLP, Gesamtlänge „unbekannt“, Wert mit UTF-16-BOM `FFFE`. Beobachtet: `nvarchar(50)` führt zu `maxlen` 100 Byte, 5000 Zeichen und `Size=-1` zu `MAX` (`0xFFFF`) mit PLP.

Befunde:

- Der Client öffnet die Verbindung mit einer Login-Antwort aus ENVCHANGE (Typ 7, SQL Collation, 5 Byte), LOGINACK (TDS-Version 7.4 als Bytefolge `74 00 00 04`, Interface 1) und DONE. Die Bytefolge `04 00 00 74` verwirft der Client mit „invalid or unsupported protocol version“. Ohne die Collation-ENVCHANGE gelingt `Open()`, Batches und Prozeduraufrufe mit reinen Zahlenparametern laufen, aber jeder RPC mit Zeichenparameter (auch `sp_executesql`) scheitert clientseitig mit einer `NullReferenceException` in `TDSExecuteRPCAddParameter` bzw. `BuildParamList`, **bevor** ein Byte gesendet wird. Die Collation des Servers ist damit Teil jedes RPC-Pfads mit Text. Datenbank-, Sprach- und Packet-Size-ENVCHANGE, Feature-Extension-Acks und weitere PRELOGIN-Optionen waren nicht nötig (PRELOGIN-Antwort nur mit VERSION und ENCRYPTION).
- Für `ExecuteNonQuery`, `ExecuteScalar`, `ExecuteReader` und `NextResult` genügt jeweils das schlichteste Ende (ein DONE bzw. DONEPROC); fehlende RETURNSTATUS und RETURNVALUE erzeugen keinen Fehler, aber leere Ausgabewerte und, bei `Prepare()`, ein Handle −1, das der Client unverändert in `sp_execute` weiterreicht. „Ohne Fehler abgeschlossen“ ist deshalb nicht „korrekt“: für OUTPUT-Parameter, ReturnValue und das Prepare-Handle sind RETURNVALUE und RETURNSTATUS nötig.
- Der Client wählt den Weg nach Befehlsform, nicht nach Parameter-Typ: Text ohne Parameter ist SQL Batch; Text mit Parametern ist immer `sp_executesql`, auch mit OUTPUT; `StoredProcedure` ist immer ein RPC mit dem Prozedurnamen als String (eine ProcID wurde dafür nie gesendet); `Prepare()` mit Parametern erzeugt `sp_prepexec` und `sp_execute`. `sp_prepare` und `sp_unprepare` erschienen in keinem Szenario.
- Der RPC-Parser des Servers müsste nach den Messwerten mindestens: ProcID und Namen unterscheiden, die zwei Textparameter von `sp_executesql` ohne Namen lesen, Statusbyte `0x01` für OUTPUT auswerten und die oben genannten TYPE_INFO-Formen samt Collation, Precision/Scale, PLP und NULL-Sentinels dekodieren. Ein Batch-Flag (mehrere RPCs in einer Nachricht) kam im Spike nicht vor.
- Die Antwortreihenfolge RETURNSTATUS, RETURNVALUE, DONEPROC ist die des Testservers; der Client akzeptierte sie. Ob ein echter SQL Server dieselbe Reihenfolge sendet, ist nicht gemessen.

Abdeckung der Optionen anhand der Messung (nur Aussagen über die gemessenen Szenarien):

- Option A (nur `sp_executesql`) deckt: Text mit Parametern (auch NULL, Unicode, MAX), `ExecuteNonQuery`/`ExecuteReader` mit Parametern, OUTPUT und InputOutput in Textbefehlen, mehrere Resultsets, `SchemaOnly` und `KeyInfo` (Letzteres nur, wenn der Server die `SET`-Anweisungen verarbeitet).
- Option A deckt **nicht**: `CommandType.StoredProcedure` (RPC mit Prozedurname; im Spike der Pfad für ReturnValue und OUTPUT bei benannten Prozeduren) und `Prepare()` mit Parametern (`sp_prepexec` ProcID 13, `sp_execute` ProcID 12). Der Mindestkatalog für das Clientprofil (Option B) wäre nach den Messwerten `sp_executesql`, `sp_prepexec`, `sp_execute` und der RPC per Prozedurname; `sp_prepare` und `sp_unprepare` brauchte er in den gemessenen Szenarien nicht. Das ist die Menge, die [SPEC-030](../../../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten) festlegen müsste; die Typliste oben ist Eingabe für [SPEC-003](../../../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog).
- Option C ist durch die Messung weder gestützt noch widerlegt: im Spike erschien kein Cursor-RPC (`sp_cursor*`) und kein `sp_prepare`.

Nicht gemessen: Antworten eines echten SQL Servers (Reihenfolge der Tokens, ordinale Zählung der RETURNVALUE, DONE-Statusbits, Fehlerverhalten bei unbekanntem RPC), `sp_unprepare` (der Client sendete ihn weder bei `Dispose`, noch bei `Close` ohne Pool, noch bei der nächsten Ausführung auf derselben Verbindung; mit Pooling, `sp_reset_connection` oder nach Verbindungsabbau nicht geprüft), `sp_prepare`, `sp_cursor*`, Batch-Flag mehrerer RPCs in einer Nachricht, Table-Valued-Parameter, `sql_variant`, `SqlBulkCopy` (ausgeschlossen), MARS, asynchrone APIs (`ExecuteReaderAsync`), `CommandBehavior.SequentialAccess`, `Cancel`/Timeout, Parameter mit `Direction.Output` für `varchar(max)`/`xml`/Datumstypen, sehr viele Parameter, Pooling mit `RESETCONNECTION`, TDS 8.0 und TLS (der Spike lief nur unverschlüsselt in TDS 7.4), Typen außerhalb der Liste oben (`text`, `ntext`, `image`, `sql_variant`, UDT, `json`), Fehlerantworten (ERROR-Token) auf RPC, Last und Dauer. Die Ergebnisse stammen aus einem einzelnen Lauf.

Wiederholung: die Spike-Dateien (`Dockerfile`, `Program.cs`, `spike.csproj`) liegen nur im Scratchpad des Spike-Agenten und gehören nicht ins Repo; `docker build --no-cache --progress=plain .` im Verzeichnis führt Testserver und Szenarien aus und gibt das Protokoll im Build-Log aus.

## 4. Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): `slice-adr-clientprofil` liegt in `done/`.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert — Carveout?): Der Referenzclient sendet RPC-Formen, die das Profil nicht vorhersah.

## 5. Closure-Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle Punkte der DoD in §2 erledigt, `make gates` grün und die Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben.

## 6. Risiken und offene Punkte


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Der Referenzclient sendet RPC-Formen, die das Profil nicht vorhersah. — **Ausgang:** offen bis zur Closure.

## 7. Closure-Notiz


Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register (vorhandene `BEO-<KUERZEL>/<slug>` **zitieren** statt neu
formulieren — sonst zählt das Register zwei Namen getrennt) ·
`grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln (das
Feld `liegt in` steht **nur**, wenn mit diesem Slice wirklich etwas verkörpert
wurde; Feld und Zielort auf **einer** Zeile, Sektionsangabe innerhalb der
Backticks). Ging der Gegenstand an einen anderen Slice oder entfiel er, trägt
diese Sektion die Zeile `Gegenstand:` mit Kennung oder Grund und jedes Risiko
aus §6 seinen Ausgang; die Liefer-Punkte der DoD bleiben leer
(`modul-05-planning-harness.md` §Ein Slice, dessen Gegenstand ein anderer
übernimmt).

Wird bei der Closure gefüllt.

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung — dort die **zwei vorgelagerten
Schritte** (sie stehen in jedem Slice-Plan, unabhängig von Modus und
Slice-Typ) und die **vier Pflichtkriterien** (Konventionen-Dichte ·
Phase-Reife · Evidenz-/Diskrepanz-Risiko · Reconciliation-Aufwand), vier und
nicht mehr.

**Der Abschnitt selbst entfällt nie.** Die zwei vorgelagerten Prüfungen laufen
in **jedem** Slice-Plan — sie hängen weder am Modus noch am Slice-Typ. Bedingt
ist allein der Modus-Begründungsblock am Ende; deshalb nennt der Titel beide
Hälften.

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield); der Slice berührt Spec und ADR, eine feinere Sub-Area ist nicht deklariert.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/` trägt nur die `README.md`): keine Treffer.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
