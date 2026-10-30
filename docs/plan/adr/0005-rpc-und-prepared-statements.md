# ADR-0005: RPC-Unterstützung und Prepared Statements

**Status:** Proposed

**Datum:** 2026-10-29

**Autor:** pt9912

**Bezug:** [TDS-FA-04](../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-FA-10](../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte)

**Schärft:** [SPEC-030](../../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten), [SPEC-003](../../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog), [ARC-005](../../../spec/architecture.md#1-komponenten-übersicht), [ARC-007](../../../spec/architecture.md#1-komponenten-übersicht), [TDS-FA-04.a](../../../spec/spezifikation.md#tds-fa-04a--rpc-und-parameter), [TDS-FA-10.a](../../../spec/spezifikation.md#tds-fa-10a--mehrteilige-antworten-und-rpc-ausgabewerte)

---

## Kontext

Parametrisierte .NET-Kommandos nutzen RPC. Der tatsächlich erforderliche Satz an Prozeduren und Tokenantworten ist client- und API-abhängig.

Gemessen mit `Microsoft.Data.SqlClient` 7.1.1 (.NET 10, TDS 7.4 unverschlüsselt, `Pooling=false`, eine Verbindung je Szenario) gegen einen eigens gebauten Testserver in C#. Es lief **kein Microsoft-SQL-Server**; die Antworten stammen vom Testserver und belegen nicht, was ein echter Server sendet. Die Zahlen stammen aus einem Lauf des Spike-Agenten; ein Review hat die Kernaussagen mit demselben Spike-Code und demselben Testserver wiederholt, eine Messung gegen einen echten SQL Server liegt nicht vor. Die Messmatrix steht im Abschnitt „Messmatrix“ unten; der Spike-Code ist nicht im Repo, die Messung ist damit nach der Entscheidung nicht mehr wiederholbar.

**Messgrenzen** (gelten für jede Aussage dieser ADR und sind in [SPEC-030](../../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten) und [contracts/client-profile.md](../../../contracts/client-profile.md) gleichlautend geführt): Testserver statt Microsoft-SQL-Server · TDS 7.4 unverschlüsselt (`Encrypt=false`), kein TDS 8.0, kein TLS · `Pooling=false` · eine Verbindung je Szenario · ein Lauf, Spike-Code nicht im Repo.

Beobachtet (Anfrage-Seite des Clients):

- Der Client wählt den Weg nach Befehlsform: Text ohne Parameter ist SQL Batch (`0x01`); Text mit Parametern ist immer `sp_executesql` (ProcID 10), auch mit OUTPUT; `CommandType.StoredProcedure` ist immer ein RPC mit dem Prozedurnamen als String (eine ProcID wurde dafür nie gesendet); `Prepare()` mit Parametern erzeugt `sp_prepexec` (ProcID 13) beim ersten und `sp_execute` (ProcID 12) bei den Folgeaufrufen.
- `Prepare()` ohne Parameter (Text) und auf einem `StoredProcedure`-Befehl erzeugt keinen Prepare-Aufruf auf der Leitung; die Ausführungen sind Batch bzw. RPC mit Prozedurname.
- `sp_prepare` und `sp_unprepare` erschienen in keinem gemessenen Szenario. Der Client sendete `sp_unprepare` weder bei `Dispose` noch bei `Close` ohne Pool noch bei der nächsten Ausführung auf derselben Verbindung; mit Pooling, `sp_reset_connection` oder nach Verbindungsabbau ist das nicht geprüft.
- Drei gemessene Szenarien liegen außerhalb des Katalogs oder brauchen eine Zusatzannahme: `ExecuteReader(SchemaOnly)` sendet einen Batch mit `SET FMTONLY OFF; SET FMTONLY ON;SELECT 1 SET FMTONLY OFF;`, der Server müsste die `SET`-Anweisungen im Batchtext verarbeiten; `ExecuteReader(KeyInfo|SingleRow)` sendet `sp_executesql` mit dem Text ` SET FMTONLY OFF; SET NO_BROWSETABLE ON; …`, der Server müsste die `SET`-Anweisungen im Text verarbeiten; `BeginTransaction`/`Commit` sind Pakettyp `0x0E` (Transaction Manager), kein RPC und nicht Teil des Katalogs. Alle drei sind **nicht entschieden**, siehe „Offen“.
- Ein Cursor-RPC (`sp_cursor*`) und ein Batch-Flag (mehrere RPCs in einer Nachricht) kamen nicht vor.
- Der RPC-Parser muss mindestens ProcID und Prozedurnamen unterscheiden, die zwei namenlosen Textparameter von `sp_executesql` lesen, das Statusbyte `0x01` (OUTPUT) auswerten und die TYPE_INFO-Formen samt Collation, Precision/Scale, PLP (MAX-Typen) und NULL-Sentinels dekodieren. Die Typliste steht in [SPEC-003](../../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog).
- Die Collation, die der Server im Login per ENVCHANGE (Typ 7) sendet, ist Teil jedes RPC-Pfads mit Text: Ohne sie scheitert der Client bei jedem RPC mit Zeichenparameter clientseitig, bevor ein Byte gesendet wird. Der Client gibt sie unverändert in den Parametern zurück.
- Antworten: Ein einzelnes DONE (Batch) bzw. DONEPROC (RPC) genügt, damit der Client ohne Fehler abschließt. „Ohne Fehler“ ist nicht „korrekt“: Ohne RETURNVALUE bleiben OUTPUT-Parameter leer, ohne RETURNSTATUS bleibt der ReturnValue leer, und bei `Prepare()` reicht der Client das Handle −1 unverändert in `sp_execute` weiter. Für OUTPUT, ReturnValue und das Prepare-Handle sind RETURNSTATUS und RETURNVALUE nötig.

**Nicht gemessen:** Antworten eines echten SQL Servers (Reihenfolge der Tokens, ordinale Zählung der RETURNVALUE, DONE-Statusbits, Fehlerverhalten bei unbekanntem RPC), `sp_prepare`, `sp_unprepare` unter Pooling, `sp_cursor*`, mehrere RPCs in einer Nachricht, Table-Valued-Parameter, `sql_variant`, MARS, asynchrone APIs, `CommandBehavior.SequentialAccess`, `Cancel`/Timeout, OUTPUT für `varchar(max)`/`xml`/Datumstypen, sehr viele Parameter, TDS 8.0 und TLS (der Spike lief nur unverschlüsselt in TDS 7.4), Pooling mit `RESETCONNECTION`, die Verarbeitung der `SET`-Anweisungen im KeyInfo-Text durch einen Server, Transaktionen auf der Serverseite (nur die Anfrage `0x0E` wurde beobachtet), Typen außerhalb der Liste in SPEC-003 (`text`, `ntext`, `image`, `sql_variant`, UDT, `json`), Fehlerantworten auf RPC, Last und Dauer.

## Messmatrix

Beobachtet mit dem Referenzclient gegen den Testserver unter den Messgrenzen im Kontext. Der Testserver beantwortete jede Anfrage in zwei Varianten: `min` mit genau einem DONE (Batch) bzw. DONEPROC (RPC), `std` mit COLMETADATA und ROW, RETURNSTATUS, RETURNVALUE für OUTPUT und DONEPROC. Anfragen mit Parametern sind in beiden Varianten byte-gleich. Pakettyp `0x01` ist SQL Batch, `0x03` RPC, `0x0E` Transaction Manager; `OptionFlags=0x0002` ist `fNoMetaData`.

| Szenario | Paket | Beobachtete Anfrage | Antwort, mit der der Client ohne Fehler abschließt |
| --- | --- | --- | --- |
| `ExecuteNonQuery`/`ExecuteScalar`/`ExecuteReader` ohne Parameter | `0x01` | SQL Batch, UTF-16-Text, davor ALL_HEADERS | `min`: ein DONE genügt (`ExecuteNonQuery` −1, Reader 0 Felder); `std`: COLMETADATA, ROW, DONE mit COUNT |
| Zwei Resultsets in einem Batch | `0x01` | `SELECT 1; SELECT 2` | `min`: 1 Resultset; `std`: zwei Gruppen, DONE `MORE\|COUNT` (`0x11`), dann `0x10`; der Client zählt 2 |
| Text mit Parametern (int, bigint, nvarchar, varchar, decimal, datetime2, bit, uniqueidentifier, varbinary), auch `AddWithValue`, MAX, NULL, Unicode, `ExecuteNonQuery` | `0x03` | `sp_executesql` (ProcID 10), `OptionFlags=0x0002`; Parameter 0 SQL-Text und Parameter 1 Deklarationsliste (`NVARCHAR`, ohne Namen), danach benannte Parameter; NULL als Sentinel des Typs; MAX mit Länge `0xFFFF` und PLP | `min`: DONEPROC; `std`: COLMETADATA/ROW, DONEINPROC, RETURNSTATUS, DONEPROC |
| Text mit OUTPUT und InputOutput | `0x03` | `sp_executesql`; Deklaration `@o int output,@io nvarchar(20) output`, Statusbyte `0x01` | `min`: Werte bleiben leer bzw. unverändert; `std`: je OUTPUT ein RETURNVALUE (`0xAC`), `o`=42 |
| `CommandType.StoredProcedure` mit INPUT/OUTPUT/InputOutput/ReturnValue | `0x03` | RPC mit Prozedurname als String (`OptionFlags=0x0000`), Name wie in `CommandText`; der ReturnValue-Parameter wird nicht gesendet; nicht gesetzte Parameter fehlen | `min`: `rv`, `o`, `s` leer; `std`: RETURNSTATUS befüllt `rv`, RETURNVALUE befüllt `o` |
| `StoredProcedure` ohne Parameter, `ExecuteReader` | `0x03` | RPC mit Name, keine Parameter | `std`: COLMETADATA/ROW, DONEINPROC, RETURNSTATUS, DONEPROC |
| `Prepare()` mit Parametern, dreimal `ExecuteScalar`, `Dispose`, `Close` | `0x03` | Aufruf 1 `sp_prepexec` (ProcID 13) mit Parameter 0 `INTN` 4 OUTPUT, Wert −1; Aufruf 2 und 3 `sp_execute` (ProcID 12) mit dem Handle; bei `Dispose` und `Close` nichts auf der Leitung, kein `sp_unprepare` | `std`: RETURNSTATUS, RETURNVALUE mit Handle (Ordinal 0, `INTN` 4), DONEPROC; `min`: Client sendet `sp_execute` mit Handle −1 weiter |
| `Prepare()`, Text danach geändert; zwei vorbereitete Befehle abwechselnd | `0x03` | erneut `sp_prepexec` mit dem alten Handle im OUTPUT-Parameter; je Befehl ein eigenes Handle, kein `sp_unprepare` | wie oben |
| `Prepare()` ohne Parameter bzw. auf `StoredProcedure` | `0x01` / `0x03` | kein Prepare auf der Leitung (Batch bzw. RPC mit Name) | DONE bzw. DONEPROC |
| `ExecuteReader(SchemaOnly)` | `0x01` | Batch ` SET FMTONLY OFF; SET FMTONLY ON;SELECT 1 SET FMTONLY OFF;` | DONE, Reader ohne Zeilen; die `SET`-Behandlung ist **offen** |
| `ExecuteReader(KeyInfo\|SingleRow)` mit Parameter | `0x03` | `sp_executesql` mit Text ` SET FMTONLY OFF; SET NO_BROWSETABLE ON; …`; der Server müsste die `SET`-Anweisungen verarbeiten | `std`: wie oben; **Behandlung offen** |
| Zwei Resultsets über `sp_executesql` | `0x03` | `sp_executesql` | `std`: zwei Gruppen mit DONEINPROC `0x11` und `0x10`, RETURNSTATUS, DONEPROC |
| `BeginTransaction`/`Commit` (Nebenbefund) | `0x0E` | Transaction-Manager-Request (Begin `0x0005`, Commit `0x0007`), danach Batch mit Transaktionsdeskriptor in ALL_HEADERS | DONE genügte; kein RPC, **nicht im Katalog, offen** |

Weitere Befunde der Messung: Ohne Collation-ENVCHANGE (Typ 7) im Login scheitert jeder RPC mit Zeichenparameter clientseitig, bevor ein Byte gesendet wird; die LOGINACK-Bytefolge der TDS-Version ist `74 00 00 04`. Der Client wählt den Weg nach Befehlsform, nicht nach Parameter-Typ. Die Antwortreihenfolge RETURNSTATUS, RETURNVALUE, DONEPROC ist die des Testservers.

## Entscheidung

Wir wählen **Option B**: den clientprofilbasierten RPC-Mindestkatalog. Der Katalog des Servers umfasst genau diese Formen (Entscheidung zum Umfang; die Freigabe im Clientprofil steht aus, siehe unten):

- `sp_executesql` (ProcID 10): SQL-Text und Parameterdeklaration getrennt vom Wertetransport, INPUT, NULL und OUTPUT/InputOutput.
- `sp_prepexec` (ProcID 13) und `sp_execute` (ProcID 12): Prepared-Statements mit Handle; `sp_prepexec` liefert das Handle als OUTPUT-Parameter (RETURNVALUE), `sp_execute` trägt es im ersten Parameter. Der Server hält das Handle je Sitzung.
- RPC per Prozedurname (`CommandType.StoredProcedure`): der Name kommt als String, INPUT, OUTPUT, InputOutput; der ReturnValue-Parameter wird vom Client nicht gesendet, RETURNSTATUS trägt ihn.

**Abgrenzung zur Freigabe:** `Accepted` entscheidet den Umfang des Katalogs, ersetzt aber nicht die Freigabe der unterstützten RPCs im Clientprofil ([contracts/client-profile.md](../../../contracts/client-profile.md), [SPEC-004](../../../spec/spezifikation.md#spec-004--testprofil)); diese bleibt an die Messgrenzen gebunden (TDS 8.0, Pooling, echter SQL Server, `SET`-Text bei KeyInfo und SchemaOnly, Transaktionen).

Alle anderen RPCs, darunter `sp_prepare`, `sp_unprepare` und `sp_cursor*`, sind nicht Teil des Katalogs und ergeben einen definierten Fehler (kein Verbindungsabbruch). Der Katalog wächst nur, wenn der Referenzclient eine weitere Form nachweislich sendet (Re-Evaluierungs-Trigger).

- **Antwortpflicht:** Für diese Formen sendet der Server RETURNSTATUS, RETURNVALUE (OUTPUT-Parameter, Prepare-Handle) und DONEPROC bzw. DONEINPROC; die Reihenfolge RETURNSTATUS, RETURNVALUE, DONEPROC akzeptierte der Client vom Testserver. Dass ein echter SQL Server dieselbe Reihenfolge sendet, ist nicht gemessen; maßgeblich bleibt MS-TDS.
- **Login-Antwort:** Die Collation-ENVCHANGE im Login ist Pflicht (Abhängigkeit zu [SPEC-028](../../../spec/spezifikation.md#spec-028--prelogin-tls-und-login7-zustands--und-antwortvertrag)).
- **Auswahlgrund:** Die Messung zeigt, dass Option A `CommandType.StoredProcedure` und `Prepare()` mit Parametern nicht deckt; beide sind übliche .NET-Pfade. Option B deckt die gemessenen RPC-Formen `sp_executesql`, `sp_prepexec`, `sp_execute` und Prozedurname mit dem kleinsten Katalog, den die Messung hergibt; offen bleiben `ExecuteReader(SchemaOnly)` (Batch mit `SET FMTONLY`) und `ExecuteReader(KeyInfo|SingleRow)` (der `SET`-Text ist nicht entschieden, Option A hätte denselben Vorbehalt) und Transaktionen (Pakettyp `0x0E`, kein RPC, außerhalb des Katalogs); Option C ist durch die Messung weder gestützt noch widerlegt, ihr Mehraufwand ist ohne beobachteten Bedarf nicht begründet (Entscheidung des Nutzers).
- **Nachweis:** Ein Spike mit einem Testserver in C# und dem Referenzclient hat die Befehlsformen, Parametertypen, NULL, Unicode, MAX, OUTPUT, mehrere Resultsets und `Prepare()` beobachtet (siehe Kontext). Der Nachweis gilt für den Referenzclient mit dem Testserver unter den Messgrenzen oben, nicht für einen echten SQL Server. Festlegungen des Katalogs ohne Messung (Fehler bei unbekannter Form ohne Verbindungsabbruch, Begrenzung der Handles je Sitzung, Fehler bei unbekanntem Handle) sind Festlegungen aus dem Negativkriterium von [TDS-FA-04](../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), nicht gemessen.

## Verglichene Alternativen

| Option                                       | Pro                        | Contra                                                 |
| -------------------------------------------- | -------------------------- | ------------------------------------------------------ |
| A — nur sp_executesql                        | Kleiner Anfangsumfang; deckt Text mit Parametern, NULL, Unicode, MAX, OUTPUT in Textbefehlen | Deckt nach der Messung `CommandType.StoredProcedure` und `Prepare()` mit Parametern nicht |
| **B — clientprofilbasierter RPC-Mindestkatalog** | Bedarfsgerecht und testbar; deckt die gemessenen RPC-Formen (`sp_executesql`, `sp_prepexec`, `sp_execute`, Prozedurname); `SET`-Text bei SchemaOnly und KeyInfo sowie Transaktionen offen | Erfordert Testfälle je Form; ein später genutzter RPC ist bis zum Nachzug nicht unterstützt |
| C — umfassende RPC-Kompatibilität            | Breite API-Abdeckung; durch die Messung weder gestützt noch widerlegt | Sehr großer Implementierungs- und Testaufwand ohne beobachteten Bedarf |
| 0 — keine Festlegung vor den Mitschnitten | Keine Vorfestlegung auf einen RPC-Umfang | Blockiert `SPEC-030` und `SPEC-003` |

## Konsequenzen

- **Positiv:** Der RPC-Umfang ist an das Clientprofil gebunden und für jede Form testbar; Prepared-Statements und gespeicherte Prozeduren sind nutzbar.
- **Negativ / Risiko:** Ein vom Client später genutzter RPC-Typ ist nicht unterstützt, bis er nachgezogen wird. Der Server muss Prepare-Handles je Sitzung halten; da der Client in den gemessenen Szenarien (ohne Pool) kein `sp_unprepare` sendet, wachsen sie dort bis zum Sitzungsende, wenn der Produktivcode sie nicht begrenzt; unter Pooling ist das Verhalten nicht gemessen.
- **Folgepflicht:** [SPEC-030](../../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten) und [TDS-FA-04.a](../../../spec/spezifikation.md#tds-fa-04a--rpc-und-parameter) auf den Katalog festlegen, [SPEC-003](../../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog) um die RPC-Parametertypen ergänzen, [TDS-FA-10.a](../../../spec/spezifikation.md#tds-fa-10a--mehrteilige-antworten-und-rpc-ausgabewerte) um RETURNVALUE für das Prepare-Handle; das Clientprofil nennt die unterstützten RPCs ([contracts/client-profile.md](../../../contracts/client-profile.md)). [ARC-005](../../../spec/architecture.md#1-komponenten-übersicht) und [ARC-007](../../../spec/architecture.md#1-komponenten-übersicht) bleiben unverändert und technologieneutral. Die Protokolltests mit dem Referenzclient je Form liefert der funktionale Slice; Mitschnitte nur redigiert ablegen.
- **Offen:** Antworten eines echten SQL Servers sind nicht gemessen; die Antwortreihenfolge und die DONE-Statusbits sind gegen MS-TDS zu prüfen. Verhalten von `sp_unprepare` unter Pooling und `sp_reset_connection` (ein gepoolter Client kann `sp_unprepare` senden, das der Katalog mit einem Fehler beantwortet), Batch-Flag mehrerer RPCs, Table-Valued-Parameter, MARS, `Cancel`/Timeout, TDS 8.0 und die Fehlerantwort auf eine unbekannte RPC-Form sind nicht gemessen. Offen und zu entscheiden: die Behandlung der `SET`-Anweisungen im Text von `ExecuteReader(SchemaOnly)` (Batch mit `SET FMTONLY`) und im KeyInfo-Text (`SET FMTONLY`, `SET NO_BROWSETABLE`, etwa bei DataAdapter und CommandBuilder) und die Transaktionsbehandlung (Pakettyp `0x0E`, Freigabepunkt in [SPEC-004](../../../spec/spezifikation.md#spec-004--testprofil)).

## Fitness Function (falls maschinell prüfbar)

| Tooling                                     | Regel                                                                                                                                 | Make-Target                         |
| ------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------- |
| JVM-/Protokolltests und .NET-Referenzclient | Eine parametrisierte Abfrage mit NULL und OUTPUT-Parameter liefert die erwarteten Werte; eine unbekannte RPC-Form ergibt einen definierten Fehler. Noch kein bestandenes Gate. | — (Gate noch einzurichten) |

## Re-Evaluierungs-Trigger

Neuer .NET-Aufruftyp erzeugt bisher nicht unterstützte RPC-Nachricht (etwa `sp_prepare`, `sp_unprepare` oder ein Cursor-RPC), oder ein echter SQL Server beantwortet die Katalog-Formen anders als der Testserver.

## Geschichte

| Datum      | Ereignis                      | Verweis |
| ---------- | ----------------------------- | ------- |
| 2026-10-29 | Proposed – Entscheidung offen | —       |
| 2026-10-30 | Accepted – Option B (Mindestkatalog); Spike mit Testserver und Referenzclient, ohne echten SQL Server; Messaussagen auf die Messgrenzen im Abschnitt Kontext begrenzt | [Kontext](#kontext) |
| 2026-10-30 | Zurück auf Proposed – Review fordert Korrekturen vor erneutem Accepted | [Review](../../reviews/2026-10-30-7fe5583-052712d-adr-0005.md) |
| 2026-10-30 | Korrektur nach Review (F-1 bis F-8): Messmatrix und Messgrenzen in die ADR, Abdeckungsaussage auf die gemessenen RPC-Formen begrenzt, KeyInfo und Transaktionen als offen geführt. Ausnahme von §3.5: die Regel bindet nur `Accepted`; die ADR stand zuvor auf `Accepted` und wurde per Nutzerentscheidung auf `Proposed` zurückgestuft, die Korrektur erfolgt daher in der `Proposed`-Fassung | [Review](../../reviews/2026-10-30-7fe5583-052712d-adr-0005.md) |
