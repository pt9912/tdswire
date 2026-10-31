# Slice slice-rpc-executesql-parameter: RPC-Dispatch: sp_executesql und Prozedurname mit typisierten Parametern

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-anfrageverarbeitung

**Bezug:** [TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit), [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md)

**Berührte Spec-Stellen:** SPEC-002 · SPEC-003 · SPEC-030 · ARC-005 · [TDS-FA-04.a](../../../../spec/spezifikation.md#tds-fa-04a--rpc-und-parameter)

**Verantwortlich:** pt9912.

**Autor:** pt9912. **Datum:** 2026-10-30.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Eine Sitzung im Zustand `READY` nimmt einen RPC an, den der Parser aus `slice-codec-rpc-parser` zerlegt hat, bildet für `sp_executesql` und für den RPC per Prozedurname einen `QueryRequest` mit typisierten Parametern (INPUT, NULL, OUTPUT, InputOutput) ohne Verkettung von SQL-Text und Werten, und beantwortet eine unbekannte RPC-Form oder eine ungültige Parameterkodierung mit einem definierten Fehler, ohne die Verbindung zu beenden ([TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc)).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- `sp_prepexec`, `sp_execute` und die Handle-Verwaltung — `slice-rpc-prepared-handles`; sie brauchen die RPC-Antwortform (RETURNVALUE) aus `slice-antwort-mehrteilig-output`.
- Antwort-Tokens für OUTPUT-Parameter und Rückgabestatus (RETURNSTATUS, RETURNVALUE, DONEPROC) — `slice-antwort-mehrteilig-output`; hier wird die Richtung (OUTPUT, InputOutput) in den `QueryRequest` übernommen, die Antwort darauf bildet jener Slice.
- RPC-Parser, TYPE_INFO-Dekodierung, NULL-Sentinels — `slice-codec-rpc-parser` (Voraussetzung, nicht Teil dieser Welle); PLP, NBCROW und Typen außerhalb des Mindestkatalogs — `slice-codec-datentypen-erweiterung`; ein Parameter dieser Art ergibt hier den definierten Fehler.
- Typkatalog (`SqlType`), Werteabbildung und Prüfung der Typabbildbarkeit gegen das Backend — die Welle `welle-backend-streaming` ([TDS-FA-05](../../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen)); dieser Slice bildet vom Parser-Ergebnis auf den dortigen Typ ab und führt keinen zweiten Katalog.
- Fehlerkodierung, Class- und Code-Zuordnung von `TDS_UNSUPPORTED_RPC` und `TDS_PROTOCOL_ERROR` — die Welle `welle-fehler-abbruch` ([ADR-0007](../../adr/0007-fehler-und-attention.md)); dieser Slice entscheidet, *dass* ein Fehler entsteht und ob die Verbindung bleibt, nicht wie er kodiert wird.
- `sp_prepare`, `sp_unprepare`, `sp_cursor*`, Table-Valued-Parameter, MARS — nicht im Katalog von [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md); sie ergeben den definierten Fehler; eine Aufnahme braucht den Nachweis des Bedarfs im Clientprofil (Re-Evaluierungs-Trigger der ADR).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [x] **`sp_executesql` ohne Verkettung** ([TDS-FA-04.a](../../../../spec/spezifikation.md#tds-fa-04a--rpc-und-parameter), SPEC-030, SPEC-002): Das Test-Backend erhält den SQL-Text unverändert als `sql`, die Deklarationsliste ist aufgelöst, und die Werte stehen als typisierte `QueryParameter` mit Namen, Typ und Richtung in der Parameterliste; ein Parameterwert, der SQL-Syntax enthält, kommt nur als Wert an (Test: der Text ist byte-gleich zu dem im RPC, der Wert steht in der Liste). Ein Test belegt, dass der Dispatch keinen Text aus Wert und SQL zusammensetzt.
- [x] **NULL und Typgrenzen** ([TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc) Boundary, SPEC-003): ein NULL-Parameter und je ein Wert an der dokumentierten Grenze der Typen des Mindestkatalogs behalten ihre Bedeutung auf dem Weg zum Backend (NULL bleibt `null` mit Typ, Integer-Grenzen, Unicode-Text mit Nicht-BMP-Zeichen, Dezimalzahl mit Precision und Scale); die Richtung OUTPUT und InputOutput wird in die Parameterliste übernommen. *Beleg-Grenze: die Grenzwerte laufen im Test bis zum `QueryRequest` des Binders; durch die `RequestLoop` ins Test-Backend gehen nur Integer, NULL, Text und die Richtung (Verifikation V-3, nicht behoben).*
- [x] **RPC per Prozedurname und definierte Fehler** ([TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc) Negative, SPEC-030): ein RPC per Prozedurname erreicht das Test-Backend als `EXEC`-Text aus Prozedur- und Parameternamen, die Werte stehen in der Parameterliste (Nutzerentscheidung 2026-10-31, §6); eine gültig kodierte, nicht unterstützte Form (`sp_prepare`, `sp_unprepare`, `sp_cursor*`, unbekannte ProcID, `sp_prepexec` und `sp_execute` bis `slice-rpc-prepared-handles`, ungültiger Name, Typ außerhalb des Katalogs, nicht unterstützter PLP-Typ, Wert außerhalb des Bereichs) ergibt je einen definierten Fehler (`TDS_UNSUPPORTED_RPC`), die Verbindung bleibt offen, ein Folge-RPC wird beantwortet; ein unbekannter Name nach der Antwort des Backends ebenso. Ein Struktur- oder Kodierfehler, den der Parser abweist (Format, Länge, unbekannte Typkennung, ALL_HEADERS), ergibt den Fehler des Parsers und schließt die Verbindung (Setzung aus [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) und [ADR-0007](../../adr/0007-fehler-und-attention.md), nicht gemessen). *Wortlaut angepasst nach Nutzerentscheidung 2026-10-31 an die gebaute Folge; vorher: „… und eine ungültige Parameterkodierung ergeben je einen definierten Fehler, die Verbindung bleibt offen …“ und „… in der Form, die die offene Frage zum Backend-Vertrag (§6) festlegt“.*

Nachweis-Pflichten für alle Punkte:

- [x] Jeder Test trägt im KDoc über der Testfunktion den Block-Tag `@spec SPEC-<NNN>` (Konvention nach `slice-qa-spec-abdeckung`); die Tests laufen in `make test` und sind grün.
- [x] Jede Aussage steht an einem Test oder ist als Festlegung gekennzeichnet (KDoc des Elements). Die Zuordnung *Test | Festlegung* je Aussage steht in der Closure-Notiz. Die Tests laufen gegen ein Test-Backend, das den Backend-Port implementiert, nicht gegen einen echten SQL Server und nicht gegen den Referenzclient; Aussagen über deren Verhalten trägt dieser Slice nicht.
- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo mit Wellen von der Welle-Closure geprüft.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/` (Unterpackage `dispatch`) <!-- d-check:ignore (geplant: neues Package) --> | update | RPC-Dispatch in `READY` neben dem Batch-Dispatch aus `slice-sql-batch-dispatch`: Parser-Ergebnis → `QueryRequest`, Bindung der Parameter, Abbildung der Parser-Fehler (ARC-005) |
| `spec/spezifikation.md` `SPEC-022` | update, nur falls die Frage zum Prozedurnamen (§6) so entschieden wird | Form, in der ein RPC per Prozedurname das Backend erreicht; die Änderung des Vertrags stimmt die Welle `welle-backend-streaming` ab |
| `spec/spezifikation.md` `SPEC-021` | update | Festlegung der Fehlerantwort auf nicht unterstützte RPC-Formen, ohne Zusage über das Verhalten des Referenzclients |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/` (Unterpackage `dispatch`) <!-- d-check:ignore (geplant: neues Package) --> | neu | Bindungstests (Text und Werte getrennt), NULL und Grenzwerte, OUTPUT-Richtung, unbekannte Form, ungültige Kodierung, Folge-RPC auf offener Verbindung; nach [TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc); `@spec`-Tag je Test |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

- Fehlerfolge (Nutzerentscheidungen vom 2026-10-31, nicht gemessen): Eine RPC-Nachricht, die der Parser wegen Struktur oder Kodierung abweist (Format, Länge, unbekannte Typkennung, ALL_HEADERS), wird wie ein SQL_BATCH mit Parserfehler beantwortet — `TDS_PROTOCOL_ERROR` oder die Kategorie des Parsers, die Verbindung schließt. Eine gültig kodierte, aber nicht unterstützte Form (unbekannte ProcID oder Name, nicht gebaute Katalogform, Namen, Status, Typ, nicht unterstützter PLP-Typ, Wert) ergibt `TDS_UNSUPPORTED_RPC`, die Verbindung bleibt offen. Der Wortlaut von DoD-Punkt 3 trägt diese Trennung.

- Reihenfolge: `sp_executesql` mit Bindung, dann NULL und Grenzen, dann Prozedurname und Fehlerfälle.
- Die Typabbildung hängt am Datentypen-Katalog der Welle `welle-backend-streaming`; fehlt ein Typ dort, ergibt der Parameter den definierten Fehler, es entsteht hier kein Ersatzkatalog.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): `slice-sql-batch-dispatch` liegt in `done/` (Dispatch-Rahmen und Anfragezustand stehen), `slice-codec-rpc-parser` liegt in `done/` (Parser für den Katalog von [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md)), der Typkatalog (`SqlType`) der Welle `welle-backend-streaming` liegt auf `main`, die Frage nach der Form des RPC per Prozedurname (§6) ist beantwortet, und ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2; Schnittvorschlag: der RPC per Prozedurname wird ein eigener Slice.
- `in-progress` → `open` (blockiert — Carveout?): der Typkatalog der Nachbarwelle deckt die Typen des Mindestkatalogs nicht, oder die Form des Prozedurnamen-RPC ist nicht entschieden.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün (einschließlich `make test`), der Review-Report liegt vor, und die Closure-Notiz trägt die Zuordnung *Test | Festlegung* je Aussage sowie den Steering-Loop-Lerneintrag (geschärfte Regel, neuer Sensor oder benannte Spec-Lücke; eine Closure ohne Lerneintrag ist keine). Gemessen ist in diesem Slice nur, was ein Test gegen das Test-Backend belegt; was der Referenzclient oder ein echter SQL Server sendet oder annimmt, ist nicht gemessen und bleibt bei `slice-qa-kompatibilitaet` und `slice-qa-reproduzierbarkeit`.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Wie erreicht ein RPC per Prozedurname das Backend? `QueryRequest` kennt nur `sql` und Parameter (SPEC-002). Entscheidung des Nutzers (2026-10-31): Option A — der Server baut aus Prozedurname und Parameternamen einen `EXEC`-Text, der Backend-Vertrag bleibt unverändert; die Werte stehen nie im Text; Grammatik und Obergrenzen des Namens stehen in der Spezifikation. — **Ausgang: entfallen.** Begründung: entschieden, gebaut (`RpcProcedureName`, `RpcBinder`) und durch Tests und Mutationen belegt (Verifikation M5, M6); der Text bleibt eine Festlegung ohne Messung gegen einen echten Server (eigene Zeile unten).
- Die Messung des RPC-Verhaltens stammt vom Spike-Testserver (Referenzclient, TDS 7.4 unverschlüsselt, `Pooling=false`, ein Lauf); ein echter SQL Server, TDS 8.0 und Pooling sind nicht gemessen; die Freigabe der RPCs im Clientprofil steht aus. — **Ausgang: weiter offen**, im Register `BEO-TDS/messung-am-echten-sql-server-fehlt` und `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` (Evidence dieses Slice ergänzt; Träger `slice-qa-vergleichslauf-d` und `slice-qa-kompatibilitaet`).
- Die Fehlerantwort auf eine nicht unterstützte RPC-Form ist nicht gemessen; die Class 16 des Fehlers `TDS_UNSUPPORTED_RPC` ist eine Voreinstellung des Nutzers, die Verbindung bleibt offen ([ADR-0007](../../adr/0007-fehler-und-attention.md)). — **Ausgang: weiter offen**, im Register `BEO-TDS/messung-am-echten-sql-server-fehlt` (Träger `slice-qa-vergleichslauf-d`).
- Fehlerfolge nach Ursache getrennt (Nutzerentscheidungen vom 2026-10-31): Struktur- und Kodierfehler des Parsers schließen mit dem Fehler des Parsers, gültig kodierte nicht unterstützte Formen bleiben `TDS_UNSUPPORTED_RPC` bei offener Verbindung; der Wortlaut von DoD-Punkt 3 ist angepasst (§2). — **Ausgang: entfallen.** Begründung: entschieden, in SPEC-011 und SPEC-013 festgelegt, gebaut und getestet (`RpcDispatchTest`, `RpcBinderTest`, `RpcFailureTest`); nicht gemessen bleibt das Verhalten gegen einen Referenzclient (Zeile davor).
- Ist-Zustand der Antwort auf eine unbekannte RPC-Form: `RequestLoop` ließ RPC unbeantwortet und schloss (Verifikation V-4 zu `slice-fehler-zustandsuebergang`). — **Ausgang: entfallen.** Begründung: behoben durch diesen Slice; `RequestLoop` beantwortet einen RPC (`answerRpc`), die Ablehnung kommt als ERROR und DONE mit Fehlerstatus bei offener Verbindung, `DispatchFailureTest` und `RpcDispatchTest` halten es fest. Die Closure von `slice-fehler-zustandsuebergang` trägt die Zeile weiter als Verweis; die Zeile war mit der Umsetzung überholt (Verifikation V-4, `BEO-TDS/risikozeile-nennt-behobenen-zustand`).
- Typen außerhalb des Mindestkatalogs und PLP: Der Parser liest PLP (`nvarchar(max)` und andere MAX-Typen) und Typen außerhalb des Katalogs nicht. Neue Lage nach den Nutzerentscheidungen vom 2026-10-31: ein gültig kodierter PLP-Typ wird offen abgelehnt (`TDS_UNSUPPORTED_RPC`, 50002, Verbindung bleibt offen); eine Typkennung außerhalb der Liste des Parsers (zum Beispiel `FE 00`) macht die Länge des Wertes unbestimmt und schließt mit `TDS_UNSUPPORTED_TYPE` (50003); der Rest der Nachricht hinter der PLP-Typangabe wird nicht gelesen (Festlegung). — **Ausgang: weiter offen**, Träger `slice-codec-datentypen-erweiterung` (`open/`, DoD-Punkt PLP „auch als RPC-Parameter“; ob der Dispatch die dann gelesenen Parameter bindet, ist dort und in `welle-anfrageverarbeitung` §5 offen).
- Die Parameter-Werte können Geheimnisse sein (Kennung, Passwort der Anwendung); keine automatisch erzeugte Textdarstellung von `QueryRequest` darf sie ausgeben ([TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit)). — **Ausgang: entfallen.** Begründung: `QueryParameter.toString` verbirgt Werte (Mutation M11 der Verifikation rot), der Log der Ablehnung nennt nur Code und einen festen Namen aus einem geschlossenen Enum (Test „Der Log der Ablehnung nennt den Code und keine Werte“, Mutation M3 rot). Grenze: dass nie ein Wert in den `cause`-String gelangt, trägt der einzige Aufrufer, nicht der Typ. Nicht gemessen bleiben Kopien der Werte im Speicher (`BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen`).
- Antwortform eines RPC: Die Antwort besteht nur aus DONE; RETURNSTATUS und RETURNVALUE für OUTPUT-Parameter fehlen, ein OutputParameter-Ereignis des Backends schließt die Verbindung (SPEC-031); SPEC-030 nennt beide für die Formen als Pflicht (Review F-6). — **Ausgang: weiter offen**, Träger `slice-antwort-mehrteilig-output` (`open/`; Ziel des Slice: RETURNSTATUS, RETURNVALUE, DONEPROC).
- Layout der Parameterwerte (Dezimal, Datum, Zeit): steht als „nach MS-TDS“ im KDoc von `RpcWireValues` ohne Abschnittsnummer; die Handvektoren sind unabhängig mit Python nachgerechnet, aber nicht am Referenzclient gemessen (Review F-4, Verifikation). — **Ausgang: weiter offen**, im Register `BEO-TDS/messung-am-echten-sql-server-fehlt` (Evidence dieses Slice ergänzt; Träger `slice-qa-vergleichslauf-d`).
- Abweichung der Entscheidung von der Bauweise: [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) nennt DONEPROC für die RPC-Antwort, gebaut ist DONE (SPEC-030 führt DONEPROC als noch nicht festgelegt). Eine `Accepted`-ADR wird nicht überschrieben (`AGENTS.md` §3.5). — **Ausgang: weiter offen**, Träger `slice-antwort-mehrteilig-output` (liefert DONEPROC; ob die Abweichung bis dahin eine Folge-ADR braucht, entscheidet der Architect dort, nicht diese Closure).

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

- **Was hat funktioniert:** Der Schnitt trug, die Rückführung nach §4 war nicht nötig; alle drei Liefer-Punkte sind im Code bestätigt. Die Tests fanden mit Mutationen, was sie versprechen: Der Reviewer fuhr zwölf Mutationen (R1 bis R12) und die Textdarstellung (R13), die Verifikation weitere zwölf (M1 bis M11 und M2a/b), die Nachverifikation fünf (M1 bis M5 zu `@@x` und PLP); alle wurden erkannt, keine überlebte. Die Injektionsfreiheit hielt dem Gedankenfuzz am Namen stand. Die Nachverifikation prüfte die Umsetzung der Nutzerentscheidungen über Spezifikation, Code und Tests und fand keinen HIGH- und keinen MEDIUM-Befund.
- **Was ging anders als geplant:** (a) Der Plan ließ die Form des RPC per Prozedurname offen; der Nutzer entschied am 2026-10-31 für den `EXEC`-Text (Option A), der Backend-Vertrag blieb unverändert. (b) Der Review fand einen MEDIUM-Befund (F-1: Strukturfehler je Pakettyp verschieden beantwortet), einen LOW (F-2) und vier INFO (F-3 bis F-6); der Nutzer entschied die Fehlerfolge nach Ursache, die Fix-Runde (543a3db, 5d349a5) setzte sie um. (c) Die Verifikation fand V-1 (MEDIUM: PLP schließt, obwohl gültig kodiert), V-2 (LOW: DoD-Wortlaut), V-3 (LOW: Grenzwerte nur im Binder), V-4 und V-5 (INFO: Plan §6 überholt, Closure steht aus) und V-6 (`@@x` zu bestätigen). Der Nutzer entschied am 2026-10-31: `@@x` nur auf dem EXEC-Pfad abgelehnt, bei `sp_executesql` zulässig; PLP offen abgelehnt (76ca52f Spezifikation, fc5d0be Code). (d) Die Nachverifikation fand drei LOW-Lücken: N-1 und N-2 (Tests für PLP in der Mitte, mehrpaketigen RPC mit PLP und `@@` bei OUTPUT und in der Mitte) behoben durch 0f97567; N-3 (`make detekt` und `make gates` nicht gefahren) durch die grünen Gates dieser Closure belegt. (e) Die Tests 0f97567 stammen vom Implementer, die Mutationsbelege ebenfalls; **kein dritter Kontext hat sie gefahren.** V-2 und V-4 sind in dieser Closure bearbeitet (DoD-Wortlaut §2, Risiken §6); V-3 bleibt offen (Tabelle unten).
- **Reports:** [Review](../../../reviews/2026-10-31-092b7a1-slice-rpc-executesql-parameter.md) gegen den Merge `092b7a1` (0 HIGH, 1 MEDIUM, 1 LOW, 4 INFO), [Verifikation](../../../reviews/2026-10-31-b339b77-verifikation-slice-rpc-executesql-parameter.md) am Stand `b339b77` (Umsetzung und Fix-Runde) und [Nachverifikation](../../../reviews/2026-10-31-fa4d6a9-nachverifikation-slice-rpc-executesql-parameter.md) am Stand `fa4d6a9` (Nutzerentscheidungen; 0 HIGH, 0 MEDIUM, 3 LOW). Ein frischer Testlauf dieser Closure, `docker build --no-cache-filter test --target test .` auf `06aebbf`: Exit 0, `BUILD SUCCESSFUL`, 654 bestandene Testfälle, keiner rot (Task `:test` ohne Layer-Cache). `make gates` lief in dieser Closure vor dem Move und danach. Ein Validator-Lauf entfällt: der Slice liefert keinen Betriebsweg, `Main` verdrahtet kein Backend.

**Zuordnung gemessen | Handvektor | Selbstkonsistenz | Festlegung | nicht gemessen je Aussage.** *Gemessen* heißt beobachtet in einem Lauf dieses Repos gegen das Test-Backend (eine Fixture des Backend-Ports) und gegen den Sitzungsrahmen; **gegen den Referenzclient und gegen einen echten SQL Server wurde in diesem Slice nichts gemessen.** *Handvektor* heißt ein von Hand gezählter Vektor ohne externe Quelle. *Selbstkonsistenz* heißt Code gegen Code. *Festlegung* heißt Setzung ohne Messung.

| Stärke | Aussage | Beleg im Repo |
|---|---|---|
| gemessen (gegen das Test-Backend) | `sp_executesql`: das Backend erhält den Text unverändert als `sql`, die Deklarationsliste fließt nicht ein, die Werte stehen als `QueryParameter` mit Namen, Typ und Richtung in der Liste; ein Wert mit SQL-Syntax kommt nur als Wert an; der Dispatch setzt keinen Text aus Wert und SQL zusammen | `RpcBinderTest`, `RpcDispatchTest`; Mutationen M5, M6 der Verifikation und R10 des Reviews rot |
| gemessen | RPC per Prozedurname: das Backend erhält `EXEC <Name> @a = @a …` aus Namen, die Werte stehen in der Parameterliste und nie im Text; Katalognamen (`sp_cursor*`) auch per Name abgelehnt | `RpcBinderTest`, `RpcDispatchTest`; R9, M8 rot |
| gemessen (Binder) | NULL mit Typ für alle neun Katalogtypen, Integer-Grenzen, Unicode-Text mit Nicht-BMP-Zeichen, Dezimal 38/0 und 38/38 mit Vorzeichen, Datum und Zeit an der Grenze; Richtung nach Statusbit 0 (mit Wert INPUT_OUTPUT, mit NULL OUTPUT). Durch die `RequestLoop` ins Test-Backend laufen davon nur Integer, NULL, Text und die Richtung | `RpcBinderTest`; `RpcDispatchTest` für den Teil; R3, R7, M7 rot |
| gemessen | Eine gültig kodierte, nicht unterstützte Form ergibt ERROR 50002 (Class 16) und DONE mit Fehlerstatus, das Backend wird nicht gerufen, die Verbindung bleibt offen, ein Folge-RPC wird beantwortet; mehrere Ablehnungen hintereinander beenden die Sitzung nicht; ein unbekannter Name nach der Antwort des Backends ergibt `TDS_UNSUPPORTED_SQL` bei offener Verbindung | `RpcDispatchTest`; R12, M2a, M2b rot |
| gemessen | Ein PLP-Typ wird offen abgelehnt (50002), auch in der Mitte der Parameterliste und in einer mehrpaketigen Nachricht mit zweitem RPC; der Rest hinter der PLP-Typangabe wird nicht gelesen | `RpcDispatchTest` (drei Tests), `RpcBinderTest`, `RpcFailureTest`; Mutationen M1, M2 der Nachverifikation rot. Die Tests 0f97567 trägt der Implementer allein, siehe oben |
| gemessen | Eine Nachricht, die der Parser wegen Struktur oder Kodierung abweist, ergibt `TDS_PROTOCOL_ERROR` (50006, Class 20) und schließt; eine unbekannte Typkennung (`FE 00`) ergibt `TDS_UNSUPPORTED_TYPE` (50003, Class 20) und schließt; das Backend wird nicht gerufen | `RpcDispatchTest`, `RpcFailureTest`; M1 der Verifikation rot |
| gemessen | `@@x` als Parametername: beim RPC per Prozedurname abgelehnt, bei `sp_executesql` gebunden, der Name unverändert | `RpcBinderTest`; M3 bis M5 der Nachverifikation rot (eine Testfunktion trägt alle drei) |
| gemessen | Der Log der Ablehnung nennt den Code und einen festen Namen, keinen Wert; `QueryParameter.toString` verbirgt Werte | `RpcDispatchTest`, `BackendModelTest`, `RpcBinderTest`; M3, M11 rot |
| gemessen | Ein RPC setzt `RUNNING`; Transaction Manager und ATTENTION in `READY` schließen weiter | `HandshakeMatrixTest`, `RpcDispatchTest`; R8, M10 rot |
| Handvektor | Die Byte-Vektoren für Dezimal (10^38 − 1, 10^38), Datum (9999-12-31 als Tag 3652058), Zeit und Datum mit Zeit bei Scale 0, 3 und 7; vom Reviewer und vom Verifier unabhängig mit Python nachgerechnet | `RpcBinderTest`, `RpcWire`; belegt die Arithmetik, nicht das Drahtformat am Referenzclient |
| Selbstkonsistenz | Code, Class und Verbindungsfolge je Ablehnung kommen aus `ErrorMapper` und der Kategorie des Parsers; der Dispatch wird gegen diese Quellen geprüft, nicht gegen eine zweite. Die Handvektoren schrieb derselbe Lauf wie den Decoder | `RpcDispatchTest`, `RpcBinderTest` |
| Festlegung | Nutzerentscheidungen 2026-10-31: RPC per Prozedurname als `EXEC`-Text; Fehlerfolge nach Ursache (Struktur-/Kodierfehler schließen, gültig kodierte nicht unterstützte Formen offen); `@@x` nur auf dem EXEC-Pfad abgelehnt; gültig kodierte nicht unterstützte PLP-Typen offen abgelehnt (50002). Festlegungen des Implementers: Grammatik und Obergrenzen der Namen samt Zählregel (128), die Ablehnung eines Wertes außerhalb seines Bereichs (`VALUE`), InputOutput mit NULL erscheint als OUTPUT, `FE 00` ergibt 50003 und schließt (folgt aus dem Parser), der Rest hinter der PLP-Typangabe wird nicht gelesen, die Antwort besteht nur aus DONE | KDoc von `RpcBinder`, `RpcParameters`, `RpcProcedureName`, `RpcWireValues`, `RpcTypeInfoReader`; SPEC-011, SPEC-013 und der Absatz „RPC im Backend-Vertrag“ in der Spezifikation |
| nicht gemessen | Die Fehlerantwort, das Drahtformat der Werte, der `EXEC`-Text und die Antwort allein aus DONE am Referenzclient und an einem echten SQL Server; TDS 8.0 und Pooling; die Freigabe der RPCs im Clientprofil; Grenzwerte durch die `RequestLoop` (Verifikation V-3); die Ablehnung von U+2028 im Klammerteil (Review F-5, Rest); RETURNSTATUS, RETURNVALUE, DONEPROC | Register und Träger in der Tabelle unten |

**Offene Punkte der Closure mit Träger** (gegen den Inhalt der Dateien geprüft):

| Offener Punkt | Träger | Stand |
|---|---|---|
| RETURNSTATUS, RETURNVALUE und DONEPROC für OUTPUT-Parameter und Rückgabestatus; die Antwort besteht bis dahin nur aus DONE | [slice-antwort-mehrteilig-output](../open/slice-antwort-mehrteilig-output.md) (`open/`) | Ziel des Slice; SPEC-030 führt die Antwortform als noch nicht festgelegt |
| [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) nennt DONEPROC für die RPC-Antwort, gebaut ist DONE | [slice-antwort-mehrteilig-output](../open/slice-antwort-mehrteilig-output.md) (`open/`) | der Slice liefert DONEPROC; ob bis dahin eine Folge-ADR nötig ist, entscheidet der Architect (siehe Steering-Loop-Eintrag) |
| PLP und Typen außerhalb des Mindestkatalogs als RPC-Parameter lesen; ob der Dispatch sie dann bindet | [slice-codec-datentypen-erweiterung](../open/slice-codec-datentypen-erweiterung.md) (`open/`, Vorrat) | DoD-Punkt PLP „auch als RPC-Parameter“; PLP wird bis dahin offen abgelehnt (50002) |
| Fehlerantwort, Wertlayout, `EXEC`-Text und Antwortform am Referenzclient und an einem echten Server | Register `BEO-TDS/messung-am-echten-sql-server-fehlt` (*geplant*, [slice-qa-vergleichslauf-d](../open/slice-qa-vergleichslauf-d.md), `open/`) | Evidence dieses Slice ergänzt |
| TDS 8.0, Pooling und Freigabe der RPCs im Clientprofil | Register `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` (*geplant*, [slice-qa-kompatibilitaet](../open/slice-qa-kompatibilitaet.md), `open/`) | Evidence dieses Slice ergänzt |
| Grenzwerte (Dezimal, Datum, Zeit) laufen nicht durch die `RequestLoop` ins Test-Backend (V-3); U+2028 im Klammerteil ohne Test (F-5, Rest) | kein Träger; beide LOW, die Verbindung Binder zu `QueryRequest` ist eine Zeile | nicht behoben, kein Test geändert |
| Der `cause`-String des Fehler-Mappers ist ein frei belegbarer Text; dass kein Wert hineingerät, trägt der einzige Aufrufer, nicht der Typ | kein Träger; Hinweis für den Architect, bei einem zweiten Aufrufer den Typ zu verengen | Verifikation, KDoc nennt die Grenze |

- **Gegenstand:** entfällt (der Slice lieferte).
- **Steering-Loop-Eintrag:** Kein `liegt in`: mit diesem Slice ist keine Regel, kein Sensor und keine Gate-Zeile an einem Zielort verkörpert worden; gezählt wird, nicht verkörpert. Fünf Feststellungen. (1) **Eine Nutzerentscheidung, die das Verhalten gegen den DoD-Wortlaut ändert, lässt den Wortlaut bis zur Closure stehen:** die Verifikation führte V-2 in zwei Berichten als offen, weil nur der Planner den Wortlaut ändert; `BEO-TDS/dod-satz-verlangt-ablage-die-der-slice-ersetzt` (jetzt 3×, über der Schwelle, nicht verkörpert, Entscheidung beim Nutzer). (2) **Eine Risikozeile, aus der Closure eines Vorgänger-Slice übernommen, beschrieb nach der Umsetzung einen behobenen Zustand** (V-4): `BEO-TDS/risikozeile-nennt-behobenen-zustand` (jetzt 4×, über der Schwelle, nicht verkörpert, Entscheidung beim Nutzer). (3) **Die committete Abdeckungsmatrix stand wieder hinter dem Bestand** (SPEC-030 und SPEC-013): `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor` (jetzt 3×, über der Schwelle, nicht verkörpert, Entscheidung beim Nutzer; die Handlung „bei jeder Closure mit Tests neu erzeugen“ wurde hier befolgt, nicht verkörpert). (4) **Eine Aussage über den Zusammenbau zweier Schichten ruhte auf dem Code** (N-1, N-2): neue Beobachtung `BEO-TDS/aussage-ueber-schichtgrenze-ohne-test`, 1×; die Tests 0f97567 schließen sie, sie stammen vom Implementer allein. (5) **Benannte Spec-Lücken:** [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) nennt DONEPROC für die RPC-Antwort, gebaut ist DONE, und SPEC-030 führt die Antwortform als noch nicht festgelegt (Träger `slice-antwort-mehrteilig-output`); [ADR-0007](../../adr/0007-fehler-und-attention.md) nennt nicht, wie eine ungültig kodierte RPC-Nachricht beantwortet wird — die Fehlerfolge nach Ursache steht nur in SPEC-011 und SPEC-013 als Setzung des Nutzers vom 2026-10-31. **Folge-ADR nötig**, wenn der Nutzer die Fehlerfolge als dauerhafte Entscheidung führen will; der Re-Evaluierungs-Trigger der ADR deckt sie nicht; Entscheidung beim Architect und beim Nutzer, nicht in dieser Closure (Bezug: [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung)). Einzelfälle ohne Klasse: keine. Nur Evidence ergänzt: `BEO-TDS/aussagen-ueber-messung-hinaus` (verkörpert, keine Schärfung), `BEO-TDS/messung-am-echten-sql-server-fehlt` und `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` (geplant), `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` (jetzt 9×, über der Schwelle, nicht verkörpert, Entscheidung beim Nutzer), `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` (jetzt 4×, über der Schwelle, nicht verkörpert, Entscheidung beim Nutzer).
- **Beobachtungs-Register (`../observations/`):** `evidence/slice-rpc-executesql-parameter.md` ergänzt in `BEO-TDS/aussagen-ueber-messung-hinaus` (F-3, F-4, F-5), `BEO-TDS/messung-am-echten-sql-server-fehlt`, `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`, `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` (F-2, V-1, V-6), `BEO-TDS/risikozeile-nennt-behobenen-zustand` (V-4), `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten`, `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor` und `BEO-TDS/dod-satz-verlangt-ablage-die-der-slice-ersetzt` (V-2). Eine neue Beobachtung mit dieser Evidence-Datei: `BEO-TDS/aussage-ueber-schichtgrenze-ohne-test`. Kein Auftreten: die übrigen. Die Zähler folgen aus den Dateien.
- **Risiken aus §6:** alle zehn tragen genau einen Ausgang (siehe §6): vier entfallen (Form des Prozedurnamen-RPC, Fehlerfolge nach Ursache, Ist-Zustand der Antwort auf eine unbekannte Form, Geheimnisse in der Textdarstellung), sechs bleiben weiter offen (Messung am Spike-Testserver, Fehlerantwort und Wertlayout im Register; Typen außerhalb des Katalogs und PLP, Antwortform und DONEPROC der Entscheidung mit Träger-Slice). Kein Risiko ist eingetreten, das einen Carveout verlangt.
- **Drei Paarungen:** Anker: kein Eintrag trägt `liegt in`, die Paarung entfällt. Folge-Slice: `slice-antwort-mehrteilig-output`, `slice-codec-datentypen-erweiterung`, `slice-qa-vergleichslauf-d` und `slice-qa-kompatibilitaet` existieren in `open/` und führen die Punkte in ihrem Inhalt. Register: eine Evidence-Datei dieses Slice in acht bestehenden Klassen und eine neue Beobachtung; die Welle-Closure prüft die Paarungen ein zweites Mal.
- **Für die Welle-Closure von `welle-anfrageverarbeitung`:** die Welle ist mit diesem Slice nicht vollständig; `slice-antwort-mehrteilig-output` und `slice-rpc-prepared-handles` liegen in `open/`, `slice-codec-datentypen-erweiterung` bleibt Vorrat (kein Closure-Kriterium). Startbar ist `slice-antwort-mehrteilig-output`; `slice-rpc-prepared-handles` wartet auf dessen `done/`.

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung — dort die **zwei vorgelagerten
Schritte** (sie stehen in jedem Slice-Plan, unabhängig von Modus und
Slice-Typ) und die **vier Pflichtkriterien** (Konventionen-Dichte ·
Phase-Reife · Evidenz-/Diskrepanz-Risiko · Reconciliation-Aufwand), vier und
nicht mehr.

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`). Das Package `protocol` (Dispatch, Antwortabbildung) ist ein Pfad-Cluster (Achse 3), trägt aber keine eigene Strukturregel (Achse 1) und keine eigene Inventur-Linie (Achse 2): eine von drei Achsen, unter der Schwelle. Keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, acht Beobachtungen, Stand des gemergten `main`); alle tragen die Sub-Area `*`. Treffer mit Zähler-Stand (Zahl der Dateien unter `evidence/`):

- `BEO-TDS/aussagen-ueber-messung-hinaus` — 7×, verkörpert (`AGENTS.md` §3.8, Anker `seit welle-protokollcodec`). Treffer: Die Regel gilt für diesen Slice; die Zuordnung *Test | Festlegung* (§2) und die Messgrenze im KDoc setzen sie um.
- `BEO-TDS/messung-am-echten-sql-server-fehlt` — 3×, geplant (Träger `slice-qa-reproduzierbarkeit`, Vergleichslauf D). Treffer: Messgrenze der RPC-Formen (Testserver, kein echter SQL Server). Die Schwelle ist erreicht und der Träger benannt; ein weiterer Beleg aus der Closure dieses Slice hebt die Beobachtung nicht in einen eigenen Slice, er wird als Beleg ergänzt.
- `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` — 3×, geplant (Träger `slice-qa-kompatibilitaet`). Treffer: Pooling und TDS 8.0 für `sp_executesql` ungemessen (§6).
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — 1×, offen. Treffer: `ADR-*`- und `TDS-*`-Kennungen stehen in diesem Plan als Link; `make docs-check` belegt es.
- Ohne Treffer: `BEO-TDS/adapter-festlegungen-ohne-slice`, `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut`, `BEO-TDS/betreiberanforderung-benutzerverwaltung-fehlt`, `BEO-TDS/vergleichslauf-d-ungeklaert`.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
