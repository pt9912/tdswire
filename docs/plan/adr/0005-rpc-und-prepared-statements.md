# ADR-0005: RPC-Unterstützung und Prepared Statements

**Status:** Proposed

**Datum:** 2026-10-29

**Autor:** pt9912

**Bezug:** [TDS-FA-04](../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-FA-10](../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte)

**Schärft:** [SPEC-030](../../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten), [SPEC-003](../../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog), [ARC-005](../../../spec/architecture.md#1-komponenten-übersicht), [ARC-007](../../../spec/architecture.md#1-komponenten-übersicht), [TDS-FA-04.a](../../../spec/spezifikation.md#tds-fa-04a--rpc-und-parameter), [TDS-FA-10.a](../../../spec/spezifikation.md#tds-fa-10a--mehrteilige-antworten-und-rpc-ausgabewerte)

---

## Kontext

Parametrisierte .NET-Kommandos nutzen RPC. Der tatsächlich erforderliche Satz an Prozeduren und Tokenantworten ist client- und API-abhängig.

Gemessen mit `Microsoft.Data.SqlClient` 7.1.1 (.NET 10, TDS 7.4 unverschlüsselt, `Pooling=false`, eine Verbindung je Szenario) gegen einen eigens gebauten Testserver in C#. Es lief **kein Microsoft-SQL-Server**; die Antworten stammen vom Testserver und belegen nicht, was ein echter Server sendet. Die Zahlen stammen aus einem Lauf des Spike-Agenten und wurden nicht wiederholt. Die Messtabelle steht im Planungsdokument zu dieser Entscheidung (Zwischenstand der Messung); der Spike-Code ist nicht im Repo.

Beobachtet (Anfrage-Seite des Clients):

- Der Client wählt den Weg nach Befehlsform: Text ohne Parameter ist SQL Batch (`0x01`); Text mit Parametern ist immer `sp_executesql` (ProcID 10), auch mit OUTPUT; `CommandType.StoredProcedure` ist immer ein RPC mit dem Prozedurnamen als String (eine ProcID wurde dafür nie gesendet); `Prepare()` mit Parametern erzeugt `sp_prepexec` (ProcID 13) beim ersten und `sp_execute` (ProcID 12) bei den Folgeaufrufen.
- `Prepare()` ohne Parameter (Text) und auf einem `StoredProcedure`-Befehl erzeugt keinen Prepare-Aufruf auf der Leitung; die Ausführungen sind Batch bzw. RPC mit Prozedurname.
- `sp_prepare` und `sp_unprepare` erschienen in keinem Szenario. Der Client sendete `sp_unprepare` weder bei `Dispose` noch bei `Close` ohne Pool noch bei der nächsten Ausführung; mit Pooling, `sp_reset_connection` oder nach Verbindungsabbau ist das nicht geprüft.
- Ein Cursor-RPC (`sp_cursor*`) und ein Batch-Flag (mehrere RPCs in einer Nachricht) kamen nicht vor.
- Der RPC-Parser muss mindestens ProcID und Prozedurnamen unterscheiden, die zwei namenlosen Textparameter von `sp_executesql` lesen, das Statusbyte `0x01` (OUTPUT) auswerten und die TYPE_INFO-Formen samt Collation, Precision/Scale, PLP (MAX-Typen) und NULL-Sentinels dekodieren. Die Typliste steht in [SPEC-003](../../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog).
- Die Collation, die der Server im Login per ENVCHANGE (Typ 7) sendet, ist Teil jedes RPC-Pfads mit Text: Ohne sie scheitert der Client bei jedem RPC mit Zeichenparameter clientseitig, bevor ein Byte gesendet wird. Der Client gibt sie unverändert in den Parametern zurück.
- Antworten: Ein einzelnes DONE (Batch) bzw. DONEPROC (RPC) genügt, damit der Client ohne Fehler abschließt. „Ohne Fehler“ ist nicht „korrekt“: Ohne RETURNVALUE bleiben OUTPUT-Parameter leer, ohne RETURNSTATUS bleibt der ReturnValue leer, und bei `Prepare()` reicht der Client das Handle −1 unverändert in `sp_execute` weiter. Für OUTPUT, ReturnValue und das Prepare-Handle sind RETURNSTATUS und RETURNVALUE nötig.

**Nicht gemessen:** Antworten eines echten SQL Servers (Reihenfolge der Tokens, ordinale Zählung der RETURNVALUE, DONE-Statusbits, Fehlerverhalten bei unbekanntem RPC), `sp_prepare`, `sp_unprepare` unter Pooling, `sp_cursor*`, mehrere RPCs in einer Nachricht, Table-Valued-Parameter, `sql_variant`, MARS, asynchrone APIs, `CommandBehavior.SequentialAccess`, `Cancel`/Timeout, OUTPUT für `varchar(max)`/`xml`/Datumstypen, sehr viele Parameter, TDS 8.0 und TLS (der Spike lief nur unverschlüsselt in TDS 7.4), Typen außerhalb der Liste in SPEC-003 (`text`, `ntext`, `image`, `sql_variant`, UDT, `json`), Fehlerantworten auf RPC, Last und Dauer.

## Entscheidung

Wir wählen **Option B**: den clientprofilbasierten RPC-Mindestkatalog. Der Server unterstützt genau diese Formen:

- `sp_executesql` (ProcID 10): SQL-Text und Parameterdeklaration getrennt vom Wertetransport, INPUT, NULL und OUTPUT/InputOutput.
- `sp_prepexec` (ProcID 13) und `sp_execute` (ProcID 12): Prepared-Statements mit Handle; `sp_prepexec` liefert das Handle als OUTPUT-Parameter (RETURNVALUE), `sp_execute` trägt es im ersten Parameter. Der Server hält das Handle je Sitzung.
- RPC per Prozedurname (`CommandType.StoredProcedure`): der Name kommt als String, INPUT, OUTPUT, InputOutput; der ReturnValue-Parameter wird vom Client nicht gesendet, RETURNSTATUS trägt ihn.

Alle anderen RPCs, darunter `sp_prepare`, `sp_unprepare` und `sp_cursor*`, sind nicht Teil des Katalogs und ergeben einen definierten Fehler (kein Verbindungsabbruch). Der Katalog wächst nur, wenn der Referenzclient eine weitere Form nachweislich sendet (Re-Evaluierungs-Trigger).

- **Antwortpflicht:** Für diese Formen sendet der Server RETURNSTATUS, RETURNVALUE (OUTPUT-Parameter, Prepare-Handle) und DONEPROC bzw. DONEINPROC; die Reihenfolge RETURNSTATUS, RETURNVALUE, DONEPROC akzeptierte der Client vom Testserver. Dass ein echter SQL Server dieselbe Reihenfolge sendet, ist nicht gemessen; maßgeblich bleibt MS-TDS.
- **Login-Antwort:** Die Collation-ENVCHANGE im Login ist Pflicht (Abhängigkeit zu [SPEC-028](../../../spec/spezifikation.md#spec-028--prelogin-tls-und-login7-zustands--und-antwortvertrag)).
- **Auswahlgrund:** Die Messung zeigt, dass Option A `CommandType.StoredProcedure` und `Prepare()` mit Parametern nicht deckt; beide sind übliche .NET-Pfade. Option B deckt alle gemessenen Szenarien mit dem kleinsten Katalog, den die Messung hergibt; Option C ist durch die Messung weder gestützt noch widerlegt, ihr Mehraufwand ist ohne beobachteten Bedarf nicht begründet (Entscheidung des Nutzers).
- **Nachweis:** Ein Spike mit einem Testserver in C# und dem Referenzclient hat die Befehlsformen, Parametertypen, NULL, Unicode, MAX, OUTPUT, mehrere Resultsets und `Prepare()` beobachtet (siehe Kontext). Der Nachweis gilt für den Referenzclient mit dem Testserver, nicht für einen echten SQL Server.

## Verglichene Alternativen

| Option                                       | Pro                        | Contra                                                 |
| -------------------------------------------- | -------------------------- | ------------------------------------------------------ |
| A — nur sp_executesql                        | Kleiner Anfangsumfang; deckt Text mit Parametern, NULL, Unicode, MAX, OUTPUT in Textbefehlen | Deckt nach der Messung `CommandType.StoredProcedure` und `Prepare()` mit Parametern nicht |
| **B — clientprofilbasierter RPC-Mindestkatalog** | Bedarfsgerecht und testbar; deckt alle gemessenen Szenarien (`sp_executesql`, `sp_prepexec`, `sp_execute`, Prozedurname) | Erfordert Testfälle je Form; ein später genutzter RPC ist bis zum Nachzug nicht unterstützt |
| C — umfassende RPC-Kompatibilität            | Breite API-Abdeckung; durch die Messung weder gestützt noch widerlegt | Sehr großer Implementierungs- und Testaufwand ohne beobachteten Bedarf |
| 0 — keine Festlegung vor den Mitschnitten | Keine Vorfestlegung auf einen RPC-Umfang | Blockiert `SPEC-030` und `SPEC-003` |

## Konsequenzen

- **Positiv:** Der RPC-Umfang ist an das Clientprofil gebunden und für jede Form testbar; Prepared-Statements und gespeicherte Prozeduren sind nutzbar.
- **Negativ / Risiko:** Ein vom Client später genutzter RPC-Typ ist nicht unterstützt, bis er nachgezogen wird. Der Server muss Prepare-Handles je Sitzung halten; da der Client kein `sp_unprepare` sendet, wachsen sie bis zum Sitzungsende, wenn der Produktivcode sie nicht begrenzt.
- **Folgepflicht:** [SPEC-030](../../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten) und [TDS-FA-04.a](../../../spec/spezifikation.md#tds-fa-04a--rpc-und-parameter) auf den Katalog festlegen, [SPEC-003](../../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog) um die RPC-Parametertypen ergänzen, [TDS-FA-10.a](../../../spec/spezifikation.md#tds-fa-10a--mehrteilige-antworten-und-rpc-ausgabewerte) um RETURNVALUE für das Prepare-Handle; das Clientprofil nennt die unterstützten RPCs ([contracts/client-profile.md](../../../contracts/client-profile.md)). [ARC-005](../../../spec/architecture.md#1-komponenten-übersicht) und [ARC-007](../../../spec/architecture.md#1-komponenten-übersicht) bleiben unverändert und technologieneutral. Die Protokolltests mit dem Referenzclient je Form liefert der funktionale Slice; Mitschnitte nur redigiert ablegen.
- **Offen:** Antworten eines echten SQL Servers sind nicht gemessen; die Antwortreihenfolge und die DONE-Statusbits sind gegen MS-TDS zu prüfen. Verhalten von `sp_unprepare` unter Pooling und `sp_reset_connection`, Batch-Flag mehrerer RPCs, Table-Valued-Parameter, MARS, `Cancel`/Timeout, TDS 8.0 und die Fehlerantwort auf eine unbekannte RPC-Form sind nicht gemessen.

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
| 2026-10-30 | Accepted – Option B (Mindestkatalog); Spike mit Testserver und Referenzclient, ohne echten SQL Server; Messaussagen vor Abschluss des Slice begrenzt | —       |
| 2026-10-30 | Zurück auf Proposed – Review fordert Korrekturen vor erneutem Accepted | [Review](../../reviews/2026-10-30-7fe5583-052712d-adr-0005.md) |
