# Slice slice-simulation-mehrere-anweisungen-und-ausgabewert: Simulations-Backend: mehrere Anweisungen je Text und Ausgabewert (A-2, Rest von A-16)

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** ohne Welle (keine Closure-Bedingung außerhalb der DoD dieses Slice; Träger der Lücken A-2 und des Rests von A-16 der Ausnahmeliste des Referenzabgleichs, angelegt nach Nutzerentscheidung vom 2026-11-17: „ja slices anlegen“ auf die Frage der Closure von [welle-referenzabgleich-release](../done/welle-referenzabgleich-release.md)).

**Bezug:** [TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte) (Scope: mehrere Ergebnisse, OUTPUT-Parameter), [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) (Anweisungsformen des Simulations-Backends)

**Berührte Spec-Stellen:** [SPEC-036](../../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) (Liste „Nicht erkannt“: mehrere Anweisungen in einem Text) · [SPEC-030](../../../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten) (Ausgabewert des Backends) · [SPEC-045](../../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig) (Ausnahmeliste); Abschnitt 5 Nr. 16 und Abschnitt 7 der [Kompatibilitätsmatrix](../../kompatibilitaetsmatrix.md).

**Verantwortlich:** —

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-17.

---

## 1. Ziel und Abgrenzung

**Ziel:** Das Simulations-Backend beantwortet einen SQL-Text aus zwei erkannten Anweisungen mit einer mehrteiligen Antwort und gibt für einen `InputOutput`-Parameter einen Ausgabewert zurück, so wie der echte SQL Server es am Referenzclient tut — oder die Regeln A-2 und A-16 gelten nach Entscheidung des Nutzers als *so gewollt*.

**Ist-Stand** (gelesen am 2026-11-17; was nicht als gemessen steht, ist aus Datei oder Code gelesen):

- **Lücke A-2:** Schritt `mehrteilig` des Live-Laufs (`SELECT Id FROM person WHERE Id = 1; SELECT Id FROM person WHERE Id = 2`): die Referenz (2022 und 2025) liefert zwei Ergebnismengen mit je einer Zeile, der Server `Number 50001`, „Unsupported SQL.“ ([Messprotokoll des Referenzabgleichs](../../../messungen/referenzabgleich.md), A-2; `referenztest-dotnet/Vergleich/live-ausnahmen.tsv`). Im Mitschnitt-Lauf stehen fünf Regeln `mehrteilig/01/{colmetadata,done,fehler,tokenfolge,zeilen}` (`mitschnitt-ausnahmen.tsv`).
- **Ursache laut Spezifikation:** [SPEC-036](../../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) führt „mehrere Anweisungen in einem Text“ unter „Nicht erkannt“ (Ausnahmen: Form C und der Text der Form D7); die Setzung, dass die Liste abschließend ist, ist vom Nutzer nicht bestätigt (`BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt`). Der Codec und der Sequenzierer bauen mehrteilige Antworten bereits ([slice-antwort-mehrteilig-output](../done/slice-antwort-mehrteilig-output.md)); „es fehlt die Backend-Seite“ ([Messprotokoll des Referenzabgleichs](../../../messungen/referenzabgleich.md) A-2; [Kompatibilitätsmatrix](../../kompatibilitaetsmatrix.md) Abschnitt 5 Nr. 16).
- **Rest von A-16 (Ausgabewert):** Mitschnitt `rpc-ausgabewert` (`SELECT Id FROM person WHERE Id = @id`, `@id` als `InputOutput` mit Wert 7): die Referenz sendet `RETURNSTATUS 0` und `RETURNVALUE` (Ordinal 2, Wert 7), der Server nur `RETURNSTATUS 0`; zwei Regeln `rpc-ausgabewert/01/{rueckgabe,tokenfolge}`. Gemessen ist nur dieser Fall (ein `InputOutput`-Parameter, dessen Wert die Anweisung nicht ändert); die Wirkung eines reinen `OUTPUT`-Parameters ohne Eingabewert ist nicht gemessen. `QueryEvent.OutputParameter` existiert im Port, das Simulations-Backend sendet es nicht ([SPEC-030](../../../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten)).
- **Abgrenzung zu A-17:** der Schritt `batch-statement-abbrechend` (zwei Anweisungen, die zweite mit Division durch null) braucht dieselbe Zerlegung und zusätzlich einen Ausdruck; er liegt in [slice-simulation-anweisungsformen-erweitern](../open/slice-simulation-anweisungsformen-erweitern.md).

**Offene Frage an den Nutzer — „Code angleichen“ oder „Zielbild anpassen“.** Die Spezifikation ist das Zielbild, eine Abweichung des Servers ist Schuld; ob sie hier durch Bau behoben wird oder ob Spezifikation und Ausnahmeliste dem Ist-Stand folgen, entscheidet der Nutzer, nicht der Planner. Der Slice startet nicht, bevor die Frage beantwortet ist.

- **Option A — Code angleichen:** das Simulations-Backend zerlegt einen Text in Anweisungen, die es einzeln erkennt, und sendet die Ereignisse für den Sequenzierer; für `InputOutput`-Parameter sendet es als Ausgabewert den Eingabewert (der gemessene Fall). [SPEC-036](../../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) und [SPEC-030](../../../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten) werden nachgezogen, die beiden Zeilen der Matrix (Nr. 16, Abschnitt 7) auf „gemessen“ gestellt, die Regeln A-2 und A-16 entfallen. Offen für den Nutzer: welche Kombinationen gelten (nur `SELECT; SELECT`, oder auch Schreibformen; Obergrenze der Anweisungszahl). Eine Änderung der Anweisungsformen widerspricht dem Wortlaut von [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) (Entscheidung 14); sie braucht eine Folge-ADR (Setzung des Planners, vom Architect und vom Nutzer zu bestätigen), `Accepted`-ADRs ändert dieser Slice nicht.
- **Option B — Zielbild anpassen:** [SPEC-036](../../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) bleibt „eine Anweisung je Text“; A-2 und der Rest von A-16 werden in der Ausnahmeliste *so gewollt* mit Begründung; die Matrix führt Nr. 16 als „nicht lieferbar, so gewollt“. [TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte) sagt „sofern die zugehörigen Operationen Bestandteil des Profils sind“; der Nutzer müsste bestätigen, dass mehrteilige Antworten und OUTPUT-Werte für das Simulations-Backend nicht zum Profil gehören. Dann entfällt der Bau; der Slice bestünde nur aus Spec- und Listenänderung und wäre im Kern ein Doku-Slice.
- **Empfehlung des Planners (Setzung, keine Entscheidung):** Option A, begrenzt auf Formen, die das Backend ohnehin erkennt, weil [TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte) die Antwortformen nennt, der Codec sie baut und die Matrix die Lücke als „nicht lieferbar mit dem Simulations-Backend“ führt; Option B nur, wenn der Nutzer das Profil bewusst enger zieht.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Neue Anweisungsformen (`OR`, Konstanten-`SELECT`, `WAITFOR`, Ausdrücke) und die Regel A-17 — [slice-simulation-anweisungsformen-erweitern](../open/slice-simulation-anweisungsformen-erweitern.md); sie hängen an der Lesart-Entscheidung des Nutzers und sind ein anderes Lieferziel. Dieser Slice ist deren Voraussetzung für die Zerlegung zweier Anweisungen.
- Mehrere RPCs in einer Nachricht ([ADR-0051](../../adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md)) und deren Messlücken — [slice-mehrfach-rpc-messluecken](../done/slice-mehrfach-rpc-messluecken.md); anderer Gegenstand (mehrere Aufrufe einer Nachricht, nicht mehrere Anweisungen eines Textes).
- Mehrteilige Antworten für `sp_executesql` mit Parametern — im Referenzabgleich nicht abgespielt (Schritt und Szenario `mehrteilig` sind SQL_BATCH ohne Parameter, gelesen in der Ausnahmeliste); ohne Messung keine Zusage ([AGENTS.md](../../../../AGENTS.md) §3.8).
- Eigene Backends des Integrators — sie senden `QueryEvent.OutputParameter` und mehrere Ergebnisse schon heute über den Port; es geht um das mitgelieferte Simulations-Backend.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit.

- [ ] **Mehrere Anweisungen je Text:** nach Entscheidung des Nutzers beantwortet das Simulations-Backend die vereinbarten Kombinationen mit einer mehrteiligen Antwort; Test je Kombination (Happy, Grenze der Anweisungszahl, Fehler in der zweiten Anweisung), der Live-Schritt `mehrteilig` und die fünf Mitschnitt-Regeln `mehrteilig/01/*` entfallen (oder, bei Option B, ihre Art ist *so gewollt* mit Begründung).
- [ ] **Ausgabewert:** ein `InputOutput`-Parameter bekommt den gemessenen Ausgabewert (RETURNVALUE mit Ordinal, Status und Wert wie die Referenz); die zwei Regeln `rpc-ausgabewert/01/*` entfallen; die Aussage zum reinen `OUTPUT`-Parameter steht als gemessen oder als „nicht gemessen“ (kein stilles Annehmen).
- [ ] **Nachweis:** der Mitschnitt-Lauf (`LAUF=mitschnitt`, nur der Server dieses Repos, kein EULA-Schritt) und `make test` mit der engsten Testklasse sind grün gegen die verkleinerten Listen; der Live-Lauf (`LAUF=live-2022`, `live-2025`) ist als Messung gefahren, falls die Referenzinstanz zur Verfügung steht, sonst steht „Live-Lauf nicht gefahren“ in §7.
- [ ] `make gates` Exit 0, dazu je Art der Änderung nach `harness/README.md` §Minimal agent workflow: engste Testklasse und Mutation je Zusage über enge Testklassen (`src/`), `make docs-check`, `make flaeche`, `make kopplung-zahlen` (Doku/Spec), `make spec-abdeckung` einmal (Tests oder `@spec`-Tags) (`src/` oder `testserver/`: Verifier fährt `make clean-build` und `make e2e` je einmal auf dem End-Stand; Implementer und Reviewer nicht). Die Läufe `make vergleichslauf-d` sind Messung und Nachweis, keine Gates ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)).
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor (`.harness/skills/reviewer.md`), kein Self-Review.
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt: [SPEC-036](../../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig), [SPEC-030](../../../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten), [SPEC-045](../../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig) (Ausnahmeliste), [Kompatibilitätsmatrix](../../kompatibilitaetsmatrix.md) (Abschnitt 5 Nr. 16, Abschnitt 7), [Messprotokoll des Referenzabgleichs](../../../messungen/referenzabgleich.md) (A-2, A-16), `referenztest-dotnet/Vergleich/live-ausnahmen.tsv`, `mitschnitt-ausnahmen.tsv`.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben: `BEO-TDS/fa-10-mehrteilige-antwort-ohne-backend-traeger` (Ausgang nachführen), `BEO-TDS/mitschnitt-luecken-ohne-traeger` (A-16-Anteil); keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — Pfad-Kandidaten für §8; geprüft ist das Vorhandensein der Dateien am 2026-11-17, nicht ihre Eignung.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/StatementParser.kt`, `StatementPlanner.kt`, `SqlScanner.kt`, `Statement.kt`, `SimulationBackend.kt` | update | Zerlegung eines Textes in Anweisungen und Ereignisfolge je Anweisung; die Form C und D7 kennen mehrere Anweisungen bereits als Ganzes (Spec) und sind Muster |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/response/ResponseSequencer.kt`, `RpcOutputs.kt`, `RpcTrailer.kt` | lesen, ggf. update | mehrteilige Antwort und RETURNVALUE bauen Codec und Sequenzierer schon; Prüfung, ob der Ausgabewert des Simulations-Backends ohne Änderung durchläuft |
| `src/test/kotlin/io/github/pt9912/tdswire/simulation/StatementFormsTest.kt`, `SimulationBackendSelectTest.kt`, `src/test/kotlin/io/github/pt9912/tdswire/protocol/response/ResponseRpcTest.kt` | update | Tests der neuen Kombinationen und des Ausgabewerts (Handvektor aus der Aufnahme `rpc-ausgabewert`) |
| `testserver/kotlin/io/github/pt9912/tdswire/testserver/KompatibilitaetsServer.kt`, `referenztest-dotnet/Kompatibilitaet/SzenarienTests.cs` | update, falls Option A | Zeilen 7a und „Zwei Anweisungen in einem Text“ der Matrix messen heute die Abweisung (Abschnitt 7); sie wandern auf „gemessen, angenommen“ |
| `referenztest-dotnet/Vergleich/live-ausnahmen.tsv`, `referenztest-dotnet/Vergleich/mitschnitt-ausnahmen.tsv` | update | Regeln entfallen (Option A) oder Art ändert sich (Option B); der Lauf ist rot bei jeder Abweichung von der Liste (Selbsttest in `make referenztest-dotnet`) |
| `spec/spezifikation.md`, `docs/plan/kompatibilitaetsmatrix.md`, `docs/messungen/referenzabgleich.md` | update | SPEC-036 (Nicht erkannt), SPEC-030 (Ausgabewert), Matrix Nr. 16 und Abschnitt 7, Tabelle der Ausnahmen |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf (Testdateien mit Kennung der Akzeptanzkriterien).

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`open` → `next` → `in-progress`): Nutzerzeichen, ein Verantwortlicher (`Verantwortlich:` ist bis dahin `—`) und die Antwort auf die Frage in §1 (Option A oder B, Kombinationen). Das Zeichen für Option A schließt die Folge-ADR zu [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) (Entscheidung 14) ein, bevor gebaut wird. Der Live-Lauf als Nachweis braucht die gepinnten Referenzinstanzen (EULA bestätigt 2026-11-01 und 2026-11-17), läuft **nicht parallel** zu Last-Messslices, und die Messsperre `/tmp/claude-1000/-Development-tdswire/messlock` gilt für Messläufe.

**Rückführungen — vorab benennen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert): die Folge-ADR zu den Anweisungsformen liegt nicht vor, oder die gepinnten Referenzinstanzen sind nicht beziehbar (Pull, Platz, geänderter EULA-Text).

## 5. Closure-Trigger

Alle Punkte der DoD in §2 erledigt, `make gates` und `make docs-check` Exit 0, die Regeln der Ausnahmeliste, die dieser Slice trägt, sind entfallen oder mit der Entscheidung des Nutzers in ihrer Art geändert (nicht still stehen gelassen), und die Closure-Notiz mit Steering-Loop-Lerneintrag ist geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang.

- Die Zerlegung an `;` im Simulations-Backend kennt Literale und Kommentare nicht, die ein `;` enthalten; ein falsches Zerlegen wäre ein stiller Fehler statt `TDS_UNSUPPORTED_SQL`. — **Ausgang:** offen bis zur Closure; der Zeichenscanner von [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) (Entscheidung 14) ist die Grundlage, Tests mit `;` in Literal und Kommentar sind Pflicht.
- Die Reihenfolge der Statusbits (`DONE` mit `MORE`, `COUNT`) bei mehrteiligen Antworten kommt aus nur einem aufgenommenen Fall je Instanz (`mehrteilig/01`). — **Ausgang:** offen bis zur Closure; die Aussage trägt für diesen Fall; Messung weiterer Kombinationen ist ein Messauftrag, kein Teil dieses Slice ([AGENTS.md](../../../../AGENTS.md) §3.8).
- Der Echo-Wert des `InputOutput`-Parameters ist die einzige gemessene Form; ein Backend könnte den Parameter ändern wollen. — **Ausgang:** offen bis zur Closure; ein Ausgabewert aus dem Backend selbst (Callback) bleibt dem Integrator und dem Port überlassen; dieser Slice sendet nur den gemessenen Fall.
- Option A ändert eine Zusage von [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) und [SPEC-036](../../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig). — **Ausgang:** offen bis zur Closure; Folge-ADR und Spec-Änderung gehen dem Bau voraus (Start-Bedingung).
- Größe: drei Liefer-Punkte. — **Ausgang:** offen bis zur Closure; Rückführung nach §4.

## 7. Closure-Notiz

Wird bei der Closure gefüllt.

**Herkunft:** Lücken A-2 und A-16 (Rest) der Ausnahmeliste, [Messprotokoll des Referenzabgleichs](../../../messungen/referenzabgleich.md); Register `BEO-TDS/fa-10-mehrteilige-antwort-ohne-backend-traeger` (Träger-Vorschlag des Planners dort, „Backend liefert mehrere Anweisungen je Text und Ausgabewerte“) und `BEO-TDS/mitschnitt-luecken-ohne-traeger` (Rest von A-16); [Results-Notiz von welle-referenzabgleich-release](../done/welle-referenzabgleich-release-results.md) (Abschnitt „Ausnahmeliste: Art je Regel und die offene Arbeit“); [slice-antwort-mehrteilig-output](../done/slice-antwort-mehrteilig-output.md). Nutzerentscheidung 2026-11-17: „ja slices anlegen“ (Frage der Closure von `welle-referenzabgleich-release`, Punkt: die 14 Lücken-Nummern ohne Träger-Slice).

## 8. Sub-Area-Prüfungen und Modus-Begründung

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register (`../observations/BEO-TDS/`) gegen die Verzeichnisliste gelesen; Treffer: `fa-10-mehrteilige-antwort-ohne-backend-traeger` (offen), `lesart-einer-nutzerentscheidung-unbestaetigt` (offen, über der Schwelle; die Antwort auf Option A/B ist ein Teil der Lesart), `mitschnitt-luecken-ohne-traeger`. Zähler-Stände stehen in den Dateien des Registers, nicht hier.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
