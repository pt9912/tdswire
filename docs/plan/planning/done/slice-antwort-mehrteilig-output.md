# Slice slice-antwort-mehrteilig-output: Mehrteilige Antworten, Rückgabestatus und OUTPUT-Parameter

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-anfrageverarbeitung

**Bezug:** [TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md), [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)

**Berührte Spec-Stellen:** SPEC-002 · SPEC-030 · SPEC-031 · ARC-005 · ARC-007 · ARC-015 · [TDS-FA-10.a](../../../../spec/spezifikation.md#tds-fa-10a--mehrteilige-antworten-und-rpc-ausgabewerte)

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

**Ziel:** Der Server beantwortet eine Anfrage mit mehreren Ergebnissen, Update-Zählern, Rückgabestatus und OUTPUT-Parametern als eine geordnete Antwort: die Ereignisse des Backends erscheinen in der Reihenfolge des Stroms, eine Ergebnisgrenze (auch ein leeres Ergebnis zwischen zwei Ergebnissen) ist für den Client eindeutig, ein RPC liefert RETURNSTATUS, je OUTPUT-Parameter ein RETURNVALUE und DONEPROC, und eine nicht unterstützte Antwortform ergibt einen definierten Fehler statt eines unvollständigen Erfolgsstroms ([TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte)).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Der Kern des Sequenzierers (Ereignis → Token für `ResultStart`, `Row`, `ResultEnd`, Vorschau für `DONE_MORE`, Backpressure) und die Kodierung von COLMETADATA und ROW — die Welle `welle-backend-streaming` ([ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)); dieser Slice ergänzt, was der Dispatch für RPC und Mehrfach-Ergebnisse braucht, und baut den Kern nicht neu.
- Fehlerkodierung (ERROR, DONE mit Fehlerstatus), die Unterscheidung statement- und batch-abbrechender Fehler im Strom und das Schicksal eines zurückgehaltenen Abschlusses bei Cancel oder Disconnect — die Welle `welle-fehler-abbruch`; offen in [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) und [ADR-0007](../../adr/0007-fehler-und-attention.md) mit dieser Zuständigkeit.
- Parameterbindung und Dispatch des RPC — `slice-rpc-executesql-parameter`; das Prepare-Handle als RETURNVALUE — `slice-rpc-prepared-handles` (es nutzt die RPC-Antwortform dieses Slice).
- Ordinale Zählung der RETURNVALUE, Tokenfolge und DONE-Statusbits eines echten SQL Servers — nicht gemessen, Träger `slice-qa-reproduzierbarkeit` (`BEO-TDS/messung-am-echten-sql-server-fehlt`); dieser Slice setzt die Festlegung aus MS-TDS und dem Spike-Testserver.
- Transaktionen, MARS, Table-Valued-Parameter und weitere Antwortformen außerhalb des Clientprofils — dokumentiert als nicht unterstützt (siehe DoD), nicht gebaut ([TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte) Out-of-Scope).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [x] **Mehrere Ergebnisse in Reihenfolge** ([TDS-FA-10.a](../../../../spec/spezifikation.md#tds-fa-10a--mehrteilige-antworten-und-rpc-ausgabewerte), SPEC-002, SPEC-031): Ein Test-Backend mit zwei Ergebnissen und einem Update-Zähler führt zu einer Antwort in der Reihenfolge des Stroms; `DONE_MORE` ist an jedem Abschluss außer dem letzten gesetzt, `DONE_COUNT` nur bei gemeldetem Zähler; ein leeres Ergebnis zwischen zwei Ergebnissen erzeugt Metadaten und Abschluss, sodass die Grenze eindeutig ist (Byte-Vektor je Fall; die Messzeile `DONE MORE|COUNT` mit anschließendem Abschluss aus [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) ist ein Spike-Befund am Testserver, kein Wert eines echten Servers).
- [x] **RPC-Antwortform und OUTPUT** ([TDS-FA-10.a](../../../../spec/spezifikation.md#tds-fa-10a--mehrteilige-antworten-und-rpc-ausgabewerte), SPEC-030): ein RPC mit OUTPUT-Parameter und mit Rückgabestatus erzeugt RETURNSTATUS, je OUTPUT- und InputOutput-Parameter ein RETURNVALUE (Name, Typ, Wert, ordinale Zählung als Festlegung) und zum Schluss DONEPROC bzw. DONEINPROC passend zum Kontext; ein NULL-Wert im OUTPUT bleibt NULL; ohne OUTPUT-Parameter entsteht kein RETURNVALUE.
- [x] **Nicht unterstützte Antwortform** ([TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte) Negative, SPEC-031): Liefert das Test-Backend eine Form, die das Profil nicht kennt (Zeile ohne vorheriges `ResultStart`, doppeltes `ResultEnd`, `OutputParameter` bei einem Batch, Typ außerhalb der Abbildung), meldet der Server einen definierten Fehler und beginnt vor dem ersten gesendeten Byte keine Erfolgsantwort; nach dem ersten gesendeten Byte folgt ERROR und Abschluss mit Fehlerstatus, solange der Strom synchron ist, sonst schließt der Server (Setzung aus [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) und [ADR-0007](../../adr/0007-fehler-und-attention.md), nicht gemessen); ein Test prüft, dass kein vollständiger Erfolgsabschluss auf eine Teilantwort folgt.

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
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/` (Unterpackage `dispatch`) <!-- d-check:ignore (geplant: neues Package) --> | update | Anbindung der Antwort an den Sequenzierer für mehrere Ergebnisse und für RPC (RETURNSTATUS, RETURNVALUE, DONEPROC bzw. DONEINPROC) (ARC-005, ARC-007, ARC-015); Prüfung der Ereignisreihenfolge, soweit die Nachbarwelle sie nicht liefert |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/` (Sequenzierer aus `welle-backend-streaming`) | update, nur falls nötig | RPC-spezifische Ereignisse ergänzen, falls der Kern sie nicht trägt; die Änderung stimmt die Welle `welle-backend-streaming` ab |
| `spec/spezifikation.md` `SPEC-021` | update | Antwortformen, die der Server liefert, und die ausdrücklich nicht unterstützten ([TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte) verlangt die Dokumentation nicht benötigter Varianten) |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/` (Unterpackage `dispatch`) <!-- d-check:ignore (geplant: neues Package) --> | neu | Byte-Vektoren der Antworten, Ergebnisgrenzen, OUTPUT und RETURNSTATUS, nicht unterstützte Form; nach [TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte); `@spec`-Tag je Test |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

**Ist-Zustand gegen den Plan (Implementer, vor dem Code):** Der Sequenzierer-Kern liegt auf `main` (`slice-backend-sequenzierer` in `done/`) und trägt schon die Vorschau für `DONE_MORE`, `DONE_COUNT` nur bei gemeldeter Zahl, die Ereignisreihenfolge-Prüfung (Verstoß ist ein Fall von `SPEC-048`) und die Ablehnung von `ReturnStatus` und `OutputParameter`; die Unterpackages `response` und `dispatch` existieren (nicht `dispatch` neu). Offen waren daher nur die RPC-Antwortform und die Dispatch-Tests für Mehrfach-Ergebnisse und Negativformen. Der dritte Liefer-Punkt (Reihenfolge) ist im Kern vorhanden; dieser Slice belegt ihn an der Leitung des Dispatch. Die Liste der Dateien:

| Datei | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/response/ResponseForm.kt` | neu | Antwortform BATCH oder RPC, von der Anfrageschleife an den Sequenzierer gegeben |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/response/ResponseRun.kt` | update | DONEINPROC und DONEPROC, RETURNSTATUS, RETURNVALUE in der Form RPC |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/response/ResponseSequencer.kt` | update | Parameter `form`, Festlegungen im KDoc |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/response/RpcOutputs.kt` | neu | Zählung von RETURNSTATUS und Ordinal der RETURNVALUE, Abbildung des Ausgabewerts über `ValueMapping`; im ersten Lauf von `ResponseRun` abgespalten und in der Fix-Runde in `status` und `value` geteilt (Verifikation V-2: fehlte in dieser Tabelle) |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/RequestLoop.kt` | update | RPC ruft den Sequenzierer mit der Form RPC |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/response/ResponseRpcTest.kt` | neu | Sequenzierer in der Form RPC |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/dispatch/AnswerFormsDispatchTest.kt` | neu | Byte-Vektoren der Antworten an der Anfrageschleife |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/dispatch/RpcDispatchTest.kt` | update | Erwartung der RPC-Antwort: DONEINPROC und DONEPROC statt DONE |

- Reihenfolge: Mehrfach-Ergebnisse, dann RPC-Antwortform, dann der Negativfall.
- Die Byte-Vektoren stammen aus MS-TDS und aus den Spike-Antworten in [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md); Abschnittsnummern externer Standards nur mit belegbarer Quelle (`AGENTS.md` §3.8).

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): `slice-sql-batch-dispatch` und `slice-rpc-executesql-parameter` liegen in `done/`, der Sequenzierer-Kern der Welle `welle-backend-streaming` liegt auf `main` (Kennung des Slice wird nachgetragen), und ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2; Schnittvorschlag: die Prüfung der Ereignisreihenfolge (dritter Punkt) wird ein eigener Slice.
- `in-progress` → `open` (blockiert — Carveout?): der Sequenzierer-Kern der Nachbarwelle trägt die DONE-Familie nicht in der hier gebrauchten Form und ihr Planer kann sie nicht ergänzen.

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

- Wem gehört die Prüfung der Ereignisreihenfolge im Sequenzierer ([ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) §Offen: "im Spike nicht gebaut", ohne Slice-Zuständigkeit)? Dieser Plan legt sie hierher, weil [TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte) Negative sie verlangt; widerspricht ein Nachbarplan, wandert sie dorthin und der dritte Liefer-Punkt entfällt. — **Ausgang:** entfallen — kein Nachbarplan widersprach; die Prüfung liegt im Kern des Sequenzierers (`slice-backend-sequenzierer`, `done/`), dieser Slice belegt sie an der Leitung des Dispatch (`AnswerFormsDispatchTest`); die Frage an den Nutzer wurde nicht nötig, weil der Ist-Zustand in §3 sie beantwortete.
- Die ordinale Zählung der RETURNVALUE, die Tokenreihenfolge und die DONE-Statusbits eines echten SQL Servers sind nicht gemessen (`BEO-TDS/messung-am-echten-sql-server-fehlt`); [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) nennt sie ausdrücklich als gegen MS-TDS zu prüfen. Die Vektoren sind von Hand abgeleitet. — **Ausgang:** weiter offen — wandert in das Register `BEO-TDS/messung-am-echten-sql-server-fehlt` (geplant, [slice-qa-vergleichslauf-d](../open/slice-qa-vergleichslauf-d.md)); die Vektoren sind Arithmetik (Handvektor), nicht am Server gemessen.
- Ob ein `Failure` als "nächstes Ereignis" der `DONE_MORE`-Vorschau zählt, ist in [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) offen (Zuständigkeit Backend-/Mapping-Slice, nach Messung); ein Test mit Fehler nach einem Ergebnis hängt davon ab. — **Ausgang:** weiter offen — Zuständigkeit Backend-/Mapping-Slice nach Messung ([ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) §Offen); der Fehlerfall nach einem Ergebnis ist in `ResponseRpcTest` als Setzung gepinnt (DONE statt DONEPROC, Gegenbeleg R02), die Entscheidung bleibt bei `welle-fehler-abbruch`; ein Träger-Slice dafür fehlt (§7, Spec-Lücken).
- Der Rückgabestatus eines RPC per Prozedurname kommt aus dem Backend (`ReturnStatus`); ein Backend, das ihn nicht liefert, ergibt keinen RETURNSTATUS, und ob der Client daran scheitert, ist nicht gemessen. — **Ausgang:** weiter offen — wandert in das Register `BEO-TDS/messung-am-echten-sql-server-fehlt`; ein Backend ohne `ReturnStatus` ergibt keinen RETURNSTATUS (Test), ob der Client daran scheitert, ist nicht gemessen.
- Größe: drei Liefer-Punkte, der dritte hängt an der Nachbarwelle. — **Ausgang:** entfallen — die Rückführung nach §4 war nicht nötig; alle drei Liefer-Punkte sind im Code bestätigt, der dritte (Reihenfolge) lag schon im Kern und wurde an der Leitung des Dispatch belegt.

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

- **Was hat funktioniert:** Der Schnitt trug, die Rückführung nach §4 war nicht nötig; alle drei Liefer-Punkte sind im Code bestätigt. Die Ist-Zustand-Prüfung vor dem Code (§3) erkannte, dass der Sequenzierer-Kern die Vorschau für `DONE_MORE`, `DONE_COUNT` und die Reihenfolge-Prüfung schon trug; es blieb die RPC-Antwortform und der Beleg an der Leitung des Dispatch, kein Neubau. Die Tests fanden mit Mutationen, was sie versprechen: Die Verifikation fuhr acht Mutationen (M1 bis M8) gegen die Fix-Runde, alle wurden erkannt, die beiden F-4-Fälle einzeln.
- **Was ging anders als geplant:** (a) Die Datei `RpcOutputs.kt` entstand beim Zerlegen von `ResponseRun` und fehlte in der Datei-Tabelle von §3 (Verifikation V-2); sie steht jetzt dort. (b) Der Review (gegen `5bb99b2`) fand 0 HIGH, 1 MEDIUM (F-1: [SPEC-031](../../../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität) ohne die Ausnahme der Form RPC), 4 LOW (F-2 bis F-5) und 4 INFO (F-6 bis F-9); die Fix-Runde `89aad43` behob F-1 nur an zwei von drei Stellen, die dritte (Typklassen des Backends) fand die Verifikation als V-1 (MEDIUM), und diese Closure berichtigte sie in `spec/spezifikation.md`. (c) **Die Fix-Runde prüften nur Verifier und Implementer**, kein zweiter Review: Der Review-Report gilt für den Stand `5bb99b2`, nicht für `89aad43`. (d) Der Verifier überschrieb im geteilten Scratchpad eine fremde Datei `mut.sh` (eigener Hinweis im Report); das berührt das Repo nicht.
- **Reports:** [Review](../../../reviews/2026-10-31-5bb99b2-slice-antwort-mehrteilig-output.md) gegen `5bb99b2` (0 HIGH, 1 MEDIUM, 4 LOW, 4 INFO) und [Verifikation](../../../reviews/2026-10-31-d28604a-verifikation-slice-antwort-mehrteilig-output.md) am Stand `d28604a` nach der Fix-Runde (0 HIGH, 1 MEDIUM V-1, 3 LOW V-2 bis V-4, 4 INFO V-5 bis V-8; acht Mutationen erkannt). Frischer Testlauf dieser Closure (`docker build --no-cache-filter test --target test .`, Stand mit der Berichtigung von V-1): Exit 0, `BUILD SUCCESSFUL`, Task `test` im Lauf ausgeführt, 738 Tests `PASSED`, keiner rot (ein Test übersprungen).
- **Behandlung der Verifikations-Befunde:** V-1 berichtigt in `spec/spezifikation.md` (nur die Antwort auf einen Batch lehnt `OutputParameter` ab); die ADR-Seite steht in Spec-Lücke 1. V-2 in die Datei-Tabelle von §3 aufgenommen. V-3 als Spec-Lücke 2 mit Träger geführt, kein Code. V-4 erledigt durch diese Closure. V-5: die Abdeckungsmatrix wurde in dieser Closure mit `make spec-abdeckung` neu erzeugt. V-6: Spec-Lücke 4. **V-7 (INFO):** der KDoc des Tests „Ein nicht abbildbarer Ausgabewert …“ nennt drei von fünf Fällen, im `RequestLoop` stehen zwei Zeilenumbrüche mitten im Satz; **V-8 (INFO):** der Zweig `null -> violation()` für `value` in `ResponseRun.rpcOutput` ist unerreichbar. Beide bleiben unverändert (die Closure ändert keinen Produktionscode), kein Träger, kein Verhaltensunterschied.

**Zuordnung gemessen | Handvektor | Selbstkonsistenz | Festlegung | nicht gemessen je Aussage.** *Gemessen* heißt beobachtet in einem Lauf dieses Repos gegen das Test-Backend (eine Fixture des Backend-Ports) und gegen den Sitzungsrahmen; **gegen den Referenzclient und gegen einen echten SQL Server wurde in diesem Slice nichts gemessen.** *Handvektor* heißt ein von Hand gezählter Vektor ohne externe Quelle — **Arithmetik, nicht am Server gemessen.** *Selbstkonsistenz* heißt Code gegen Code. *Festlegung* heißt Setzung ohne Messung.

| Stärke | Aussage | Beleg im Repo |
|---|---|---|
| gemessen (gegen das Test-Backend) | Zwei Ergebnisse und ein Update-Zähler erscheinen in der Reihenfolge des Stroms; `MORE` an jedem Abschluss außer dem letzten, `COUNT` nur bei gemeldeter Zahl; ein leeres Ergebnis zwischen zwei Ergebnissen erzeugt Metadaten und Abschluss ohne `COUNT` | `AnswerFormsDispatchTest` („Zwei Ergebnisse und ein Update-Zaehler …“, „Ein leeres Ergebnis zwischen zwei Ergebnissen …“); Mutationen M4 und M6 der Verifikation rot |
| gemessen | Ein RPC mit Rückgabestatus und OUTPUT-Parameter erzeugt RETURNSTATUS, je OUTPUT- und InputOutput-Parameter ein RETURNVALUE und zum Schluss DONEPROC (DONEINPROC je Abschluss davor); ohne Ereignis kein RETURNSTATUS und kein RETURNVALUE; NULL im OUTPUT bleibt NULL | `AnswerFormsDispatchTest`, `ResponseRpcTest`, `RpcDispatchTest`; M6, M7 rot |
| gemessen | Der Batch lehnt `ReturnStatus` und `OutputParameter` ab; im RPC ist ein zweiter `ReturnStatus` oder ein Ausgabeereignis im offenen Ergebnis ein Reihenfolgeverstoß ([SPEC-048](../../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder)) | `ResponseRpcTest`, `AnswerFormsDispatchTest`; M3, M5, M8 rot (je an einem einzigen Test, ohne Redundanz) |
| gemessen | Ein nicht abbildbarer Ausgabewert (Precision über 38, Klasse passt nicht zum Typ, Name über 255 Zeichen, Text über 4000 Einheiten, Dezimalwert außerhalb von Precision und Scale) ergibt einen Fehler der Typabbildung; eine Zeile ohne `ResultStart` und ein doppeltes `ResultEnd` ergeben einen definierten Fehler, und auf eine Teilantwort folgt kein vollständiger Erfolgsabschluss | `ResponseRpcTest`, `AnswerFormsDispatchTest`; M1, M2 je einzeln rot |
| Handvektor | Die Byte-Vektoren der DONE-Familie (13 Byte), RETURNSTATUS (5 Byte) und RETURNVALUE (Ordinal, Name UTF-16LE, Status, UserType, Flags, `INTN`) in `AnswerFormsDispatchTest` und `RpcDispatchTest`; der Verifier zählte sie gegen das Tabellenlayout von MS-TDS nach, ohne belegbare Abschnittsnummer (`AGENTS.md` §3.8) | `AnswerFormsDispatchTest`, `RpcDispatchTest` |
| Selbstkonsistenz | Der Fehlerfall einer RPC-Antwort nutzt dasselbe `failedDone()` wie der Batch; Statusbits und Code kommen aus dem Sequenzierer, die Tests prüfen gegen dessen Konstanten und gegen Handvektoren desselben Laufs | `ResponseRpcTest` |
| Festlegung | (1) `MORE` am letzten `DONEINPROC` vor dem `DONEPROC`, abweichend vom Spike-Testserver, der den letzten Abschluss ohne `MORE` schrieb ([ADR-0005](../../adr/0005-rpc-und-prepared-statements.md): „zwei Gruppen mit DONEINPROC `0x11` und `0x10`“); gepinnt von M4. (2) Ordinal der RETURNVALUE ab 0 in der Reihenfolge der Ereignisse, nicht als Position in der Aufrufliste. (3) ERROR und Abschluss mit Fehlerstatus sind in der RPC-Antwort ein `DONE` statt `DONEPROC`, **gegen die einzige Messzeile R02** von [ADR-0007](../../adr/0007-fehler-und-attention.md) (`DONEPROC[ERR]` am echten Server); die Entscheidung bleibt bei `welle-fehler-abbruch`. (4) Statusbyte 1 und Tokenfolge RETURNSTATUS, RETURNVALUE, DONEPROC als Folge der Ereignisse des Stroms. (5) **Offen:** ob ein `ResultStart` nach einem Ausgabewert vom Client angenommen wird; die Folge ist nicht ausgeschlossen und kein Test pinnt sie | KDoc von `ResponseSequencer` und `RpcOutputs`; Spec [TDS-FA-10.a](../../../../spec/spezifikation.md#tds-fa-10a--mehrteilige-antworten-und-rpc-ausgabewerte); `ResponseRpcTest` „Ein Fehler nach einem Ergebnis …“ pinnt (3) |
| nicht gemessen | Ordinale Zählung der RETURNVALUE, Tokenfolge, DONE-Statusbits und DONE-Familie im Fehlerfall an einem echten SQL Server und am Referenzclient; ob der Client einen RPC ohne RETURNSTATUS annimmt; TDS 8.0 und Pooling | Register `BEO-TDS/messung-am-echten-sql-server-fehlt` (*geplant*, [slice-qa-vergleichslauf-d](../open/slice-qa-vergleichslauf-d.md)) und `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` (*geplant*, [slice-qa-kompatibilitaet](../open/slice-qa-kompatibilitaet.md)) |

**Offene Punkte der Closure mit Träger** (gegen den Inhalt der Dateien geprüft):

| Offener Punkt | Träger | Stand |
|---|---|---|
| Spec-Lücke 1: [ADR-0013](../../adr/0013-backend-grenzen.md) Entscheidung 6 sagt (angenommen, unveränderlich nach `AGENTS.md` §3.5), `OutputParameter` bleibe „bis zur Abbildung abgelehnt“; die Spezifikation sagt jetzt, nur der Batch lehne ab, im RPC werde er abgebildet. Die ADR wurde nicht angefasst | **Folge-ADR nötig**, Entscheidung beim Architect und beim Nutzer; die Spezifikation rangiert über der ADR und gilt bis dahin | der Re-Evaluierungs-Trigger der ADR deckt den Fall nicht; kein Träger-Slice, eine Folge-ADR ist kein Slice |
| Spec-Lücke 2 (Verifikation V-3): Liefert das Backend `OutputParameter` vor `ReturnStatus`, schreibt der Sequenzierer RETURNVALUE vor RETURNSTATUS; weder die Spezifikation („Tokens in der Reihenfolge des Stroms“) noch ein Test sagt, ob das gewollt ist; [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) nennt RETURNSTATUS, RETURNVALUE, DONEPROC | [slice-qa-vergleichslauf-d](../open/slice-qa-vergleichslauf-d.md) (`open/`): Zeile „Reihenfolge der Antwort-Tokens … RPC mit Ausgabewert“ | der Träger misst die Reihenfolge am echten Server, soweit der Client sie beobachtet; die Vertauschung durch das Backend nennt sein Text nicht ausdrücklich — Hinweis an den Planner dieses Slice |
| Spec-Lücke 3: ein `ResultStart` nach einem Ausgabewert ergibt COLMETADATA hinter RETURNVALUE; Annahme durch den Client nicht gemessen | derselbe Träger [slice-qa-vergleichslauf-d](../open/slice-qa-vergleichslauf-d.md) | wie Lücke 2 |
| Spec-Lücke 4: DONE statt DONEPROC im Fehlerfall der RPC-Antwort, gegen Messzeile R02 | **kein Träger im Repo** (die Slices von `welle-fehler-abbruch` nennen DONEPROC nicht); Entscheidung bei Architect und Nutzer: eine Zeile im Plan von `slice-attention-cancel-timeout` oder in der Fehlerabbildung, oder die Messung in D | bleibt eine Setzung, gepinnt vom Test |
| Frühere Spec-Lücke (aus der Closure von `slice-rpc-executesql-parameter`): [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) nennt DONEPROC für die RPC-Antwort, gebaut war DONE; SPEC-030 führte die Antwortform als nicht festgelegt | dieser Slice | **geschlossen**: Spezifikation und Code kennen die RPC-Antwortform |
| Weitere Messpunkte (Tokenfolge, Statusbits, Ordinal) | Register `BEO-TDS/messung-am-echten-sql-server-fehlt` | Evidence dieses Slice ergänzt |
| V-7, V-8 (INFO) | kein Träger | siehe Behandlung der Verifikations-Befunde |

- **Gegenstand:** entfällt (der Slice lieferte).
- **Steering-Loop-Eintrag:** Kein `liegt in`: mit diesem Slice ist keine Regel, kein Sensor und keine Gate-Zeile an einem Zielort verkörpert worden; gezählt wird, nicht verkörpert. Vier Feststellungen. (1) **Eine Aussage wurde an einer Stelle fortgeschrieben und an einer zweiten, ja dritten stehen gelassen:** Review F-1 und Verifikation V-1 (die Fix-Runde berichtigte zwei Fundstellen der Spezifikation, die dritte und die ADR-Seite blieben); neue Beobachtung `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`, 1×; zwei Berichte desselben Vorgangs zählen einmal. (2) **Die Datei-Tabelle des Plans nannte eine beim Bau entstandene Datei nicht, und der Review behauptete im Negativbefund das Gegenteil:** neue Beobachtung `BEO-TDS/plan-tabelle-nennt-neue-datei-nicht`, 1×. (3) **Die committete Abdeckungsmatrix stand wieder hinter dem Bestand** (F-5, ein Tag auf eine unbekannte Festlegung, den der Quelltext nicht trug): `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor` (jetzt 4×, über der Schwelle, nicht verkörpert, Entscheidung beim Nutzer; die Handlung „bei jeder Closure mit Tests neu erzeugen“ wurde hier befolgt, nicht verkörpert). (4) **Benannte Spec-Lücken:** die vier Lücken der Tabelle oben; **Folge-ADR nötig** für Lücke 1 (die angenommene [ADR-0013](../../adr/0013-backend-grenzen.md) widerspricht der Spezifikation; der Re-Evaluierungs-Trigger der ADR deckt sie nicht), Entscheidung beim Architect und beim Nutzer, nicht in dieser Closure (Bezug: [TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte)). Nur Evidence ergänzt, keine Schärfung: `BEO-TDS/aussagen-ueber-messung-hinaus` (verkörpert; F-2, F-3, F-6) und `BEO-TDS/messung-am-echten-sql-server-fehlt` (geplant).
- **Beobachtungs-Register (`../observations/`):** `evidence/slice-antwort-mehrteilig-output.md` ergänzt in `BEO-TDS/aussagen-ueber-messung-hinaus`, `BEO-TDS/messung-am-echten-sql-server-fehlt` und `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor`; neu angelegt `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` und `BEO-TDS/plan-tabelle-nennt-neue-datei-nicht`. Keine weitere Beobachtung angefallen.
- **Risiken aus §6:** alle fünf tragen genau einen Ausgang (siehe §6): zwei entfallen (Zuständigkeit der Reihenfolge-Prüfung, Größe), drei bleiben weiter offen (ordinale Zählung und DONE-Bits, `Failure` als nächstes Ereignis, Rückgabestatus ohne Backend-Ereignis). Kein Risiko ist eingetreten, das einen Carveout verlangt.
- **Drei Paarungen:** Anker: kein Eintrag trägt `liegt in`, die Paarung entfällt. Folge-Slice: `slice-qa-vergleichslauf-d` und `slice-qa-kompatibilitaet` existieren in `open/` und führen die Punkte in ihrem Inhalt (Lücke 2 und 3 mit dem Hinweis oben); Lücke 1 und 4 nennen keinen Slice. Register: eine Evidence-Datei dieses Slice in drei bestehenden Klassen und zwei neue Beobachtungen, je mit nicht leerem `evidence/`; die Welle-Closure prüft die Paarungen ein zweites Mal.
- **Für die Welle-Closure von `welle-anfrageverarbeitung`:** die Welle ist mit diesem Slice nicht vollständig. `slice-rpc-prepared-handles` liegt in `open/` und gehört zu den Closure-Kriterien; `slice-codec-datentypen-erweiterung` bleibt Vorrat (kein Closure-Kriterium). Der Start von `slice-rpc-prepared-handles` ist nach seinem §4 an drei Bedingungen gebunden: `slice-antwort-mehrteilig-output` in `done/` (mit dieser Closure erfüllt), [ADR-0004](../../adr/0004-session-zustandsautomat.md) auf `Accepted` (im ADR-Index erfüllt) und der **Grenzwert der Handles je Sitzung vom Nutzer entschieden**; für den Grenzwert findet sich in Spezifikation, ADRs und Plänen keine Zahl, er ist offen (§6 des Slice, Frage an den Nutzer).

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
- `BEO-TDS/messung-am-echten-sql-server-fehlt` — 3×, geplant (Träger `slice-qa-reproduzierbarkeit`, Vergleichslauf D). Treffer: ordinale Zählung der RETURNVALUE, Tokenfolge, DONE-Statusbits (§6). Die Schwelle ist erreicht und der Träger benannt; ein weiterer Beleg aus der Closure dieses Slice hebt die Beobachtung nicht in einen eigenen Slice, er wird als Beleg ergänzt.
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — 1×, offen. Treffer: `ADR-*`- und `TDS-*`-Kennungen stehen in diesem Plan als Link; `make docs-check` belegt es.
- Ohne Treffer: `BEO-TDS/adapter-festlegungen-ohne-slice`, `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut`, `BEO-TDS/betreiberanforderung-benutzerverwaltung-fehlt`, `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`, `BEO-TDS/vergleichslauf-d-ungeklaert`.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
