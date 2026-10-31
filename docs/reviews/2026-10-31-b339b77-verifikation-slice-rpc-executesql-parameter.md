# Verifikation: slice-rpc-executesql-parameter (Umsetzung und Fix-Runde) — 2026-10-31

**Rolle:** Verifier (Modul 11), frischer Kontext. Frage: Bauen wir es richtig? Geprüft wird die DoD von [slice-rpc-executesql-parameter](../plan/planning/in-progress/slice-rpc-executesql-parameter.md) gegen Code, [SPEC-013 und SPEC-011](../../spec/spezifikation.md#4-fehler-codes-und-logging-felder), [SPEC-030](../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten), den Absatz „RPC im Backend-Vertrag“ und [ADR-0005](../plan/adr/0005-rpc-und-prepared-statements.md), [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md), [ADR-0007](../plan/adr/0007-fehler-und-attention.md). Nicht geprüft wird, ob das Richtige gebaut wird (Validator) oder der Diff gegen den Plan (Reviewer).

**Stand:** `main` bei b339b77. Gegenstand: Umsetzung fffd7c1 und febbe92 (Merge 092b7a1), Fix-Runde 543a3db und 5d349a5 (Merge 5952d16). Die Quellen unter `protocol/` und `domain/` sind seit der Fix-Runde unverändert (Abgleich des Scratchpad-Stands mit `main`).

**Eingang:** der Review-Report [F-1 bis F-6](2026-10-31-092b7a1-slice-rpc-executesql-parameter.md) und die Nutzerentscheidungen vom 2026-10-31 (a: RPC per Prozedurname als EXEC-Text, Backend-Vertrag unverändert; b: Option A für Strukturfehler). Ein Sensor-Beleg des Implementers liegt nicht vor; die Sensoren sind selbst gefahren.

## Ergebnis in einem Satz

Alle drei Liefer-Punkte sind im Code und in den Tests erfüllt, die Fix-Runde setzt die Entscheidung (b) vollständig um, und alle elf eigenen Mutationen plus die Mutation der Hauptentscheidung wurden von den Tests erkannt; **drei Dinge brauchen den Planner oder den Nutzer**: die neue Festlegung `@@x` (zu bestätigen), die Frage, ob eine gültig kodierte, aber nicht unterstützte Typangabe (PLP, zum Beispiel `nvarchar(max)`) die Verbindung schließen soll (V-1), und der unveränderte DoD-Wortlaut (V-2). Der Slice darf mit gesetzten Häkchen nach `done/` erst, wenn die Closure-Notiz steht (V-5).

## Sensor-Belege

| Sensor | Ausgang | Beleg |
|---|---|---|
| `make test` (Stage test, Scratchpad-Kopie von `main`) | grün, 640 Tests; die Stage kam aus dem Docker-Layer-Cache (Inhalt identisch zu einem früheren Lauf), kein frischer Testlauf | Log im Scratchpad `/tmp/claude-1000/-Development-tdswire/8562f454-84ba-456b-9970-945e69c035f4/scratchpad/ver/base.log`; die Mutationsläufe unten sind frische Läufe mit 640 Tests und belegen die Test-Stage als lauffähig |
| `make detekt` (Scratchpad-Kopie) | Exit 0, Layer aus dem Cache | `/tmp/claude-1000/-Development-tdswire/8562f454-84ba-456b-9970-945e69c035f4/scratchpad/ver/detekt.log` |
| `make docs-check` | siehe Abschnitt „Abschluss“ | — |
| Handvektoren mit Python | alle stichprobenhaft nachgerechneten Vektoren stimmen | Abschnitt „Handvektoren“ |
| Mutationen | 12 von 12 erkannt | Abschnitt „Mutationen“ |

Nicht gefahren: voller `make gates` (Rechner ausgelastet, nur ein Docker-Build gleichzeitig); die Gates `detekt` und `test` sind einzeln belegt, `docs-check` am Ende.

## DoD-Konformität

Alle Häkchen im Plan stehen noch leer, die Closure-Notiz (§7) lautet „Wird bei der Closure gefüllt“. Der Befund gilt also dem Stand von Code, Spezifikation und Tests, nicht den Häkchen.

| DoD-Punkt | Befund |
|---|---|
| 1 `sp_executesql` ohne Verkettung ([TDS-FA-04.a](../../spec/spezifikation.md#tds-fa-04a--rpc-und-parameter), [SPEC-030](../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten), [SPEC-002](../../spec/spezifikation.md#spec-002--interner-query-vertrag)) | **Erfüllt.** `RpcBinder` übergibt `call.statement` unverändert als `sql`, die Deklarationsliste fließt nicht ein, die Werte stehen als `QueryParameter` mit Namen, Typ und Richtung in der Liste. Beleg: `RpcBinderTest` „sp_executesql gibt den Text unveraendert…“ (Unicode, Zeilenumbruch, leer, Injektionswert, Prüfung auf Nichtenthaltensein von Wert und Deklaration) und `RpcDispatchTest` „sp_executesql erreicht das Backend…“ (Sitzungs-ID, Anfrage-ID, Parameter, keine Frist, ein Aufruf je RPC). Mutationen M5 (Wert im Aufruftext) und M6 (Deklaration angehängt) erkannt. Der Dispatch setzt keinen Text aus Wert und SQL zusammen: der einzige gebaute Text ist `EXEC <name> @a = @a …` aus Namen. |
| 2 NULL und Typgrenzen ([TDS-FA-04](../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc) Boundary, [SPEC-003](../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog)) | **Erfüllt im Binder, im Dispatch nur stichprobenartig.** NULL mit Typ für alle neun Katalogtypen, Int32 und Int64 an Minimum, Maximum, 0, −1, Bit, Unicode-Text mit Nicht-BMP-Zeichen und mit einzelnem Surrogat, Dezimal 38/0 und 38/38 an der Grenze und mit Vorzeichen, Datum 0001-01-01 und 9999-12-31, Zeit und Datum mit Zeit an der Tagesgrenze, Richtung (Bit 0 mit Wert INPUT_OUTPUT, mit NULL OUTPUT, ohne Bit INPUT). Gap (V-3, LOW): die Grenzwerte laufen im Test nur durch den Binder, nicht durch die `RequestLoop` bis ins Test-Backend; im Dispatch-Test gehen nur Int, NULL, Text und die Richtung durch. Die Verbindung Binder zu `QueryRequest` ist eine Zeile (`execute(bound.sql, bound.parameters)`), M7 (Richtung vertauscht) wird auch im Dispatch erkannt. |
| 3 RPC per Prozedurname und definierte Fehler ([TDS-FA-04](../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc) Negative, [SPEC-030](../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten)) | **Erfüllt nach der geänderten Fehlerfolge, DoD-Wortlaut weicht ab (V-2).** RPC per Name: Test-Backend erhält `EXEC [dbo].[p] @x = @x, @o = @o OUTPUT` mit Parameterliste, Werte nie im Text (Nutzerentscheidung a, Backend-Vertrag unverändert, `QueryRequest` ohne neues Feld). Gültig kodierte, nicht unterstützte Formen (`sp_prepare`, `sp_unprepare`, `sp_cursor*` per ProcID und per Name, `sp_prepexec`, `sp_execute`, ungültiger Name, ungültiger Parametername, Typ außerhalb des Katalogs, Wert außerhalb des Bereichs): ERROR 50002 Class 16 und DONE mit Fehlerstatus, Backend nicht gerufen, Verbindung offen, Folge-RPC wird beantwortet; mehrere Ablehnungen hintereinander beenden die Sitzung nicht; „unbekannter Name nach der Antwort des Backends“ ist getestet (`TDS_UNSUPPORTED_SQL`, offen, Folge-RPC beantwortet). Eine Nachricht, die der Parser abweist, schließt mit Protokollfehler; das entspricht Entscheidung (b), nicht dem Wortlaut „Verbindung bleibt offen“. |
| Nachweis: `@spec`-Tag je Test | Erfüllt für alle Tests in `RpcBinderTest` und `RpcDispatchTest` (Stichprobe aller Testfunktionen: je ein KDoc-Block mit mindestens einem `@spec`). |
| Nachweis: *Test \| Festlegung* je Aussage | Im Code vorbereitet (KDoc der Objekte nennt „Festlegung, nicht gemessen“ je Aussage), die Zuordnungstabelle gehört in die Closure-Notiz und fehlt noch. |
| Gates, Review, Closure, Register, Risiken, Paarungen | Review liegt vor, Fix-Runde nachgeprüft (unten); `make gates` einzeln belegt (detekt, test); Closure-Notiz, Register-Fortschreibung, Risiko-Ausgänge und Paarungen stehen aus. |

### Abgleich mit [ADR-0005](../plan/adr/0005-rpc-und-prepared-statements.md), [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md), [ADR-0007](../plan/adr/0007-fehler-und-attention.md)

| ADR | Aussage | Befund |
|---|---|---|
| [ADR-0005](../plan/adr/0005-rpc-und-prepared-statements.md) | Katalog: `sp_executesql` unterstützt, `sp_prepexec`/`sp_execute` gehören zur Handle-Verwaltung, `sp_prepare`, `sp_unprepare`, `sp_cursor*`, TVP, MARS nicht im Katalog | Code deckt: `sp_prepexec` und `sp_execute` NOT_BUILT, übrige UNKNOWN_PROCEDURE, auch per Name. Die ADR nennt DONEPROC für die RPC-Antwort (Zeile 32); gebaut ist DONE, die Spezifikation führt das als „noch nicht festgelegt“ (Review F-6, Zuständigkeit `slice-antwort-mehrteilig-output`). |
| [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md) | Backend-Vertrag `QueryRequest` mit Text und Parametern, Strom mit Rückstau | Unverändert; der RPC läuft durch dieselbe `execute`-Funktion wie der Batch (Senke, Rückstau, EOM). Kein neues Feld in `QueryRequest`, `QueryParameter.toString` verbirgt Werte (M11 erkannt). |
| [ADR-0007](../plan/adr/0007-fehler-und-attention.md) | Class 16 offen für Anfragefehler (`TDS_UNSUPPORTED_RPC` Voreinstellung), `TDS_PROTOCOL_ERROR` Class 20 schließt | Code deckt beide Wege (Test prüft 50002/16 offen, 50006/20 schließt). Die ADR sagt nichts über „ungültig kodierte RPC-Nachricht“; die Zuordnung steht in der Spezifikation (SPEC-011-Zeile), dort als Setzung der Nutzerentscheidung. Kein Widerspruch. |

## Review-Befunde F-1 bis F-6

| ID | Klasse (Review) | Stand | Beleg im Repo |
|---|---|---|---|
| F-1 | MEDIUM, Strukturfehler je Pakettyp verschieden | **behoben** (Option A) | `RpcBinder` trägt den `ProtocolError` in `Rejected`; `RequestLoop.answerRpc` ruft bei `error != null` `errors.protocol(error)`, sonst `errors.code(TDS_UNSUPPORTED_RPC, cause = reason.name)`. SPEC-011-Zeile und SPEC-013-Zeile grenzen ab. Test „Eine Kodierung, die der Parser abweist, ergibt einen Protokollfehler und schliesst“ (50006, Class 20, Verbindung zu, Folgenachricht ungelesen, Backend nicht gerufen). Mutation M1 erkannt. Teilrest: der Log trägt beim Protokollfehler keinen Grund (`cause = null`, per Test festgelegt); der Review wollte Grund für beide Ursachen. Unterscheidbar bleibt der Fall über den Code (50006 gegen 50002 mit Marke). |
| F-2 | LOW, Zählregel | **behoben** | Spezifikationsabsatz nennt die Zählregel (regulär mit allen Zeichen, in Klammern ohne Klammern, `]]` zählt zwei, Parametername mit `@`); KDoc von `RpcParameters` und `RpcProcedureName` stimmt überein, Grenzen 127/128/129 getestet. |
| F-3 | INFO, VALUE nur im KDoc | **behoben** | SPEC-013-Zeile und Absatz nennen den Wert außerhalb des Bereichs (Dezimalbetrag ab 10 hoch Precision, Vorzeichenbyte, Datum, Zeit) als Festlegung ohne Messung. Mutation M2a zeigt, dass VALUE bei offener Verbindung per Test festgehalten ist. |
| F-4 | INFO, Layout nur als „nach MS-TDS“ | **ehrlich offen** | KDoc von `RpcWireValues` unverändert („Layout nach MS-TDS“, keine Abschnittsnummer); Vektoren sind selbstkonsistent, nicht am Referenzclient gemessen. Die Spezifikation führt alles als „nicht gemessen“. Träger bleibt der Vergleichslauf (`BEO-TDS/messung-am-echten-sql-server-fehlt`). |
| F-5 | INFO, Grammatik weiter als T-SQL, `@@x` ohne Test | **behoben, mit einer Ausnahme** | `@@x` wird abgelehnt (neue Festlegung, siehe unten) und getestet (`@@x`, `@@rowcount`); die Quotierungspflicht für ein späteres Backend steht im Absatz („endet nur an einem einfachen `]`, danach `.` oder Ende“). Offen: der Review nannte einen Test mit U+2028 im Klammerteil; ein solcher Test steht nicht (`isISOControl` schließt U+2028 nicht aus, der Teil in Klammern nimmt es an). Risiko eines Bruchs des Textes besteht nicht (der Teil endet nur am einfachen `]`), die Ablehnung steht aber nicht fest. |
| F-6 | INFO, Antwortform nur DONE | **ehrlich offen** | Spezifikation: „RETURNSTATUS, RETURNVALUE und DONEPROC sind noch nicht festgelegt“; Träger `slice-antwort-mehrteilig-output`. |

## Fix-Runde nachgeprüft

| Zusage | Befund |
|---|---|
| `Rejected` trägt den `ProtocolError` | Ja (`RpcBinding.Rejected(reason, error: ProtocolError? = null)`); `ENCODING` setzt ihn, jeder andere Grund lässt ihn `null`. Test prüft beides (`assertNotNull`/`assertNull` auf `error`). |
| `ErrorMapper.code` mit `cause`, Log nennt Enum-Namen, nie Werte | Ja. `cause` ist ein `String?` und wird als feste Marke geloggt; an der einzigen Aufrufstelle ist es `reason.name`, ein Name aus einem geschlossenen Enum (`PARAMETER_NAME` und so weiter). Test mit Geheimnis „Kennwort-Q7“ im Wert und im SQL-Text: Log gleich `[ErrorLogEntry(TDS_UNSUPPORTED_RPC, "PARAMETER_NAME")]`, `toString` ohne Geheimnis. Mutation M3 (cause weg) erkannt. Grenze: Der Parameter ist ein frei belegbarer String; dass nie ein Wert hineinkommt, trägt der Aufrufer, nicht der Typ (KDoc nennt es). Heute ein Aufrufer. |
| Parametername `@@x` abgelehnt | Umgesetzt und getestet (siehe unten). M4 erkannt. |
| Unbekannter Typcode im Parser (`FE 00`) | `TDS_UNSUPPORTED_TYPE`, ERROR 50003 Class 20, Verbindung schließt, Backend nicht gerufen; getestet. |

### Neue Festlegung des Implementers: `@@x` abgelehnt (zu bestätigen)

- **Was:** Ein Parametername, dessen zweites Zeichen `@` ist (`@@x`, `@@rowcount`), ergibt `PARAMETER_NAME` und damit 50002 bei offener Verbindung. Sie steht in der Spezifikation als „Festlegung“, im KDoc und im Test; sie ist nicht Teil der Nutzerentscheidung vom 2026-10-31.
- **Bewertung:** Die Begründung trägt. Der Aufruftext enthält den Parameternamen links und rechts von `=`; rechts ist `@@x` in T-SQL eine Systemfunktion (`@@ROWCOUNT`, `@@IDENTITY`), und der Aufruf würde einen anderen Wert übergeben als den gesendeten. Ein Client kann `@@x` nicht als eigenen Parameter deklarieren (reservierter Präfix), die Ablehnung nimmt einem gültigen Client nichts. Sie liegt innerhalb der delegierten Eingrenzung der Namen („Grammatik, Obergrenzen“ im Plan §6), schärft die Grammatik und lockert kein Gate. Zwei Anmerkungen: Die Aussage über das Verhalten von `@@x` am Ziel ist nicht gemessen (§3.8; im KDoc als „kann“ formuliert, das ist die richtige Stärke). Die Prüfung gilt auch für `sp_executesql`, wo der Name nicht in den Text kommt; dort ist sie harmlos, aber breiter als nötig.
- **Entscheidung nötig:** Nutzer bestätigt die Festlegung, oder sie wird auf den EXEC-Pfad beschränkt. Der Verifier meldet sie als **Festlegung, zu bestätigen**.

### `FE 00` und PLP mit 50003 statt 50006: passt es zur Entscheidung (a/b)?

- **Ableitung:** Entscheidung (b) lautet: was der Parser abweist, wird wie ein SQL_BATCH beantwortet, `errors.protocol(error)`. Dieser Aufruf bildet die Kategorie des Parsers ab (`TDS_PROTOCOL_ERROR` auf 50006, `TDS_UNSUPPORTED_TYPE` auf 50003, beide Class 20, beide schließen). Ein unbekannter Typcode (`INVALID_TYPE`) trägt die Kategorie `TDS_UNSUPPORTED_TYPE`. Das Ergebnis 50003 folgt daher aus (b) in Verbindung mit dem Parser, die Spezifikation sagt es offen („`SPEC-011` beziehungsweise die Kategorie des Parsers“). Mit (a) hat es nichts zu tun (a betrifft nur die Form des RPC per Name).
- **Für `FE 00` selbst passt es:** ein unbekannter Typcode macht die Länge des Wertes unbestimmt; die Nachricht ist nicht lesbar, also Strukturfehler, also Ende der Verbindung. Dass der Client den Typ-Fehler (50003) statt „Protokollfehler“ sieht, ist die Information, die der Batch-Pfad auch gibt.
- **Spannung (V-1, MEDIUM, zu bestätigen):** Derselbe Weg nimmt auch gültig kodierte Typangaben, die der Parser nur nicht im Katalog führt: `nvarchar(max)` und andere PLP-Typen (`INVALID_TYPE`, getestet als `ENCODING` mit `E7 FF FF …`). Entscheidung (b) sagt: „alle gültig kodierten, nicht unterstützten Fälle → `TDS_UNSUPPORTED_RPC` offen“. Ein PLP-Parameter ist gültig kodiert und nicht unterstützt, schließt aber mit 50003. Der Plan (§1) sagt für PLP und Typen außerhalb des Katalogs „ergibt den definierten Fehler“, ohne die Folge zu nennen; die SPEC-011-Zeile nennt PLP ausdrücklich als Protokollfehler (Kategorie dort `TDS_PROTOCOL_ERROR`, im Code `TDS_UNSUPPORTED_TYPE`; die Zeile ist insoweit ungenau). Praktische Folge: Ein Client, der einen langen Text als `nvarchar(max)` sendet, verliert die Verbindung, wo ein 4000er-Text sie behält. Das ist eine Auslegungsfrage von (b), keine Verletzung des Wortlauts, die der Nutzer beantworten sollte: PLP entweder wie `FE 00` schließen lassen (Ist-Zustand, dann SPEC-011-Zeile und Absatz nachziehen) oder als nicht unterstützte Form offen halten (dann trennt der Parser PLP und Katalog-Außenseiter nach Ursache).

## Mutationen

Je Mutation allein im Scratchpad-Stand, ein Docker-Build gleichzeitig, danach die Datei zurückgebaut; Basis 640 Tests. M1 bis M11 sind eigene Nummern dieses Verifiers (die des Implementers liegen nicht vor).

| Nr. | Mutation | Erkannt durch |
|---|---|---|
| M1 | `ENCODING` beantwortet wieder mit `TDS_UNSUPPORTED_RPC` (offen) | `RpcDispatchTest` „Eine Kodierung, die der Parser abweist…“, „Eine unbekannte Typkennung schliesst…“, „Der Log der Ablehnung…“ (3 rot) |
| M2a | `VALUE` schließt versehentlich (Protokollfehler) | `RpcDispatchTest` „Eine Ablehnung beantwortet… laesst die Verbindung offen“ |
| M2b | `TYPE` schließt versehentlich | dasselbe |
| M3 | `cause` nicht geloggt | `RpcDispatchTest` „Der Log der Ablehnung nennt den Code und keine Werte“ |
| M4 | `@@`-Prüfung entfernt | `RpcBinderTest` „Parameternamen folgen der Grammatik“ |
| M5 | Parameterwert im Aufruftext statt Name rechts | `RpcBinderTest` „RPC per Name baut den Aufruftext…“, `RpcDispatchTest` „RPC per Name erreicht das Backend…“ |
| M6 | Deklarationsliste an `sql` angehängt | 5 rot, darunter `RpcBinderTest` „sp_executesql gibt den Text unveraendert…“ |
| M7 | Richtung OUTPUT und INPUT_OUTPUT vertauscht | `RpcBinderTest` „Die Richtung folgt Bit 0…“ und zwei `RpcDispatchTest` |
| M8 | `sp_cursor`-Präfix nicht mehr abgelehnt | `RpcBinderTest` „Katalognamen per Name…“, `RpcDispatchTest` „Eine Ablehnung beantwortet…“ |
| M9 | Duplikatprüfung der Parameternamen aus | `RpcBinderTest` „Parameternamen…“, `RpcDispatchTest` „Der Log der Ablehnung…“ |
| M10 | RPC setzt kein `RUNNING` | 10 rot, darunter `HandshakeMatrixTest` und sechs `RpcDispatchTest` |
| M11 | Wert in `QueryParameter.toString` | `BackendModelTest` „Die Textdarstellung…“, `RpcBinderTest` „Die Textdarstellung nennt keine Werte“ |

Ergebnis: 12 von 12 erkannt, keine überlebende Mutation. Grenze: M2a und M2b werden nur von einem Sammeltest erkannt (eine Schleife über zehn Fälle; bei Rot nennt der Test den Fall über die Meldung, `name`).

## Handvektoren (Python)

Nachgerechnet mit Python (Little-Endian): 10 hoch 38 minus 1 gleich `FF FF FF FF 3F 22 8A 09 7A C4 86 5A A8 4C 3B 4B`; 10 hoch 38 gleich `00 00 00 00 40 22 8A 09 7A C4 86 5A A8 4C 3B 4B`; 99999 gleich `9F 86 01 00`, 100000 gleich `A0 86 01 00`, 1234 gleich `D2 04 00 00`; Tag von 9999-12-31 gleich `DA B9 37` (3652058), der Tag danach `DB B9 37`; 2025-01-15 gleich `C1 47 0B`; letzter Tick bei Scale 7 `FF BF 69 2A C9`, 24 Uhr `00 C0 69 2A C9`; Scale 0: 86399 gleich `7F 51 01`, 86400 `80 51 01`; Scale 3 12:34:56.789 gleich `95 2C B3 02`; Scale 7 01:02:03 plus ein Tick gleich `81 B7 14 AB 08`. Alle stimmen mit den Vektoren in `RpcBinderTest`. Das belegt die Arithmetik und die Selbstkonsistenz, nicht das Drahtformat am Referenzclient (siehe F-4). Herkunftsstufen und §3.8: nichts gemessen; die Stellen tragen „Festlegung“ oder „nicht gemessen“.

## Verifier-Befunde

| ID | Klasse | Befund | Zuständig |
|---|---|---|---|
| V-1 | MEDIUM | PLP und Katalog-Außenseiter schließen die Verbindung mit 50003, obwohl gültig kodiert; Spannung zu Entscheidung (b), SPEC-011-Zeile nennt PLP als `TDS_PROTOCOL_ERROR`. Auslegung vom Nutzer zu bestätigen. | Nutzer, dann Planner und Implementer |
| V-2 | LOW | Der DoD-Wortlaut (Punkt 3: „ungültige Parameterkodierung … die Verbindung bleibt offen“) widerspricht der gebauten Folge für Parserfehler. Der Plan §3 und §6 vermerken es und überlassen die Anpassung dem Planner; der Wortlaut ist unverändert. | Planner |
| V-3 | LOW | Typgrenzwerte laufen nur durch den Binder, nicht durch die `RequestLoop` ins Test-Backend. | Implementer, optional |
| V-4 | INFO | §6 des Plans führt weiter den „Ist-Zustand“ „`RequestLoop` lässt RPC unbeantwortet“ (V-4 der Verifikation zu `slice-fehler-zustandsuebergang`); der Ist-Zustand ist überholt, der Ausgang bei der Closure ist „eingetreten und behoben“. Gleiches für das Risiko zu den Typen außerhalb des Katalogs: der Ausgang lautet nach Fix-Runde „definierter Fehler, schließt mit 50003“, nicht offen. | Planner (Closure) |
| V-5 | INFO | Closure-Notiz, Häkchen, Register-Fortschreibung, Zuordnung *Test \| Festlegung*, Risiko-Ausgänge fehlen; der Slice liegt zu Recht in `in-progress/`. Für die Closure gelten: Messgrenzen (kein echter SQL Server, kein Referenzclient) bleiben bei `slice-qa-kompatibilitaet` und `slice-qa-reproduzierbarkeit`. | Planner |
| V-6 | INFO | Zur `@@x`-Festlegung siehe oben; zu bestätigen. U+2028 im Klammerteil ohne Test (Rest von F-5). | Nutzer, Implementer |

## Abschluss

`make docs-check` nach Anlage dieses Reports: grün (Befunde 0), nachdem drei unverlinkte ADR-Kennungen in der Tabelle verlinkt wurden.
