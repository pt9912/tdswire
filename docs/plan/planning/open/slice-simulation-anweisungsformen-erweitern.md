# Slice slice-simulation-anweisungsformen-erweitern: Simulations-Backend: Anweisungsformen erweitern (A-4, A-5, A-15, A-17)

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** ohne Welle (keine Closure-Bedingung außerhalb der DoD dieses Slice; Träger der Lücken A-4, A-5, A-15 und A-17 der Ausnahmeliste des Referenzabgleichs, angelegt nach Nutzerentscheidung vom 2026-11-17: „ja slices anlegen“ auf die Frage der Closure von [welle-referenzabgleich-release](../done/welle-referenzabgleich-release.md)).

**Bezug:** [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) (Scope: erkannte Anweisungsformen), [TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe) (Abbruch eines wartenden Befehls), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) (Entscheidung 14, Offen 12), [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) (Abbruch)

**Berührte Spec-Stellen:** [SPEC-036](../../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) (Tabelle der Formen und Liste „Nicht erkannt“) · [SPEC-045](../../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig) (Ausnahmeliste) · [SPEC-029](../../../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung) (Abbruch, Zusammenspiel mit `WAITFOR`).

**Verantwortlich:** —

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-17.

---

## 1. Ziel und Abgrenzung

**Ziel:** Für jede der vier Formen, die der Referenzabgleich abspielt und der Server heute mit `TDS_UNSUPPORTED_SQL` abweist (`SELECT 2`, `OR`, `WAITFOR`, Division durch null in einer Anweisung mit Folgeanweisung), steht nach Entscheidung des Nutzers fest, ob das Simulations-Backend sie erkennt (Regel entfällt) oder die Regel *so gewollt* wird.

**Ist-Stand** (gelesen am 2026-11-17; was nicht als gemessen steht, ist aus Datei oder Code gelesen):

- **A-4** (`SELECT 2`, Live-Schritt `anweisung-ohne-tabelle`): die Referenz gelingt, der Server `Number 50001`, „Unsupported SQL.“, Verbindung offen. Form C kennt nur `SELECT 1` ([SPEC-036](../../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig)).
- **A-5** (`WHERE Id = 1 OR Id = 2`, Live-Schritt `bedingung-mit-or`): die Referenz gelingt, der Server weist ab. [SPEC-036](../../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) führt `OR` unter „Nicht erkannt“; [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) beschreibt „Gleichheits- und Mengenbedingungen … verknüpft durch UND“ und schließt im Out-of-Scope Verbund, Aggregate, Ausdrücke, Unterabfragen und Sortierung aus, `OR` nennt es dort nicht. Ob die positive Aufzählung abschließend ist, ist die unbestätigte Lesart (`BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt`, [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) Offen 12).
- **A-15** (`WAITFOR DELAY`): vier Szenarien des Mitschnitt-Laufs (`timeout-wartend`, `cancel-wartend`, `cancel-kurz-vor-timeout`, `cancel-kurz-nach-timeout`) tragen je Regeln `…/01/fehler` und `…/01/tokenfolge@2022`, `@2025`: die Referenz wartet bis zum ATTENTION, der Server antwortet sofort mit ERROR 50001. Der Abbruch eines **wartenden** Befehls ist am Server dieses Repos damit nicht abgespielt (nur der Abbruch im Leerlauf, [Messprotokoll des Referenzabgleichs](../../../messungen/referenzabgleich.md) A-15).
- **A-17** (zwei Anweisungen, Division durch null; Szenarien `batch-statement-abbrechend`, `batch-batch-abbrechend`): die Referenz sendet bei statement-abbrechendem Fehler `DONE` mit `MORE|ERROR`, bei batch-abbrechendem `DONE` mit `ERROR`. Der Sequenzierer schreibt diese Formen laut Register bereits aus `Failure` mit `FailureScope` (`BEO-TDS/done-more-statement-abbrechender-fehler-weicht-ab`, Stand `gestrichen`, Tests `ResponseMeasuredTest`); abgespielt am Server sind sie nicht, weil der Text zwei Anweisungen und einen Ausdruck (`1 / (Id - 4)` in der Ansicht, der Text des Szenarios steht in `MitschnittLaufTests.cs`) enthält. Die Division durch null ist ein Ausdruck, den das Lastenheft ausschließt.
- **Lage der Regeln** (gezählt in den beiden `.tsv`-Dateien am 2026-11-17): Live-Liste 2 Regeln (A-4, A-5); Mitschnitt-Liste 12 Regeln für A-15 (je `fehler`, `tokenfolge@2022`, `tokenfolge@2025` der vier Szenarien; die Zeilen `@2022` nennen auf Seiten der Referenz außerdem SESSIONSTATE, den A-18 trägt) und 6 Regeln für A-17 (`batch-statement-abbrechend/01/{colmetadata,done,fehler,tokenfolge,zeilen}` und `batch-batch-abbrechend/01/fehler`).

**Offene Frage an den Nutzer — „Code angleichen“ oder „Zielbild anpassen“.** Die Spezifikation ist das Zielbild, eine Abweichung des Servers ist Schuld; ob sie hier durch Bau behoben wird oder ob Spezifikation und Ausnahmeliste dem Ist-Stand folgen, entscheidet der Nutzer, nicht der Planner. Der Slice startet nicht, bevor die Frage beantwortet ist.

- **Option A — Code angleichen**, je Form einzeln zu entscheiden: `OR` in den Bedingungen von S1 bis W3 (Erweiterung der Terme), `SELECT <Ganzzahl>` als Konstanten-Auswahl, `WAITFOR DELAY` mit einem Backend, das wartet und den Cancel beachtet, und die Anweisung mit Division durch null mit zwei Anweisungen (setzt [slice-simulation-mehrere-anweisungen-und-ausgabewert](../open/slice-simulation-mehrere-anweisungen-und-ausgabewert.md) voraus). Jede Form erweitert [SPEC-036](../../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) und widerspricht dem Wortlaut von [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) (Entscheidung 14): Folge-ADR vor dem Bau (Setzung des Planners).
- **Option B — Zielbild anpassen**, je Form: die Regel wird *so gewollt* mit der Begründung des Nutzers (Lesart „die Liste ist abschließend“ bestätigt, oder die Form liegt ausserhalb des Lastenhefts: die Division ist ein Ausdruck). Für A-17 und A-15 hieße das auch, die Szenarien des Mitschnitt-Laufs so zu ändern, dass ihr Fehler aus einer erkannten Form entsteht (neue Aufnahme an der Referenzinstanz, beide Instanzen) — das wäre ein Messauftrag und ein eigener Slice, nicht Teil dieses.
- **Empfehlung des Planners (Setzung, keine Entscheidung), je Form:** `OR` (A-5): Option A, weil das Lastenheft es nicht ausschließt und die Referenz es annimmt. `SELECT 2` (A-4): erst die Grenze „Ausdruck“ vom Nutzer ziehen lassen; die Konstante ist ein Grenzfall. `WAITFOR` (A-15): Option A erwägen, weil sonst der Abbruch eines wartenden Befehls am Server nie abgespielt wird; der Preis ist ein Backend mit Wartesemantik, nahe an dem, was das Lastenheft „keine SQL-Engine“ nennt. Division (A-17): Option B für die Division selbst (Lastenheft schließt Ausdrücke aus); die Statusbits sind durch Tests mit `Failure(STATEMENT)` belegt, ihr Abspielen am Server braucht ein Szenario mit erkannter Form.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Zwei Anweisungen in einem Text als solche (`mehrteilig`, A-2) und der Ausgabewert — [slice-simulation-mehrere-anweisungen-und-ausgabewert](../open/slice-simulation-mehrere-anweisungen-und-ausgabewert.md); die Zerlegung ist hier Voraussetzung für A-17, nicht Gegenstand.
- Weitere Formen, die kein Szenario des Referenzabgleichs abspielt (`JOIN`, `ORDER BY`, `TOP`, Aggregate): sie sind im Lastenheft wörtlich ausgeschlossen und haben keine Ausnahmeregel; ohne Szenario keine Messung ([AGENTS.md](../../../../AGENTS.md) §3.8).
- SESSIONSTATE in denselben Austauschen (A-18, nur Instanz 2022): bewusst „kein Bau“ mit Re-Evaluierungs-Trigger (Nutzerentscheid 2026-11-06, `BEO-TDS/mitschnitt-luecken-ohne-traeger`); die Regeln `…/02/tokenfolge@2022` der Cancel-Szenarien bleiben deshalb stehen.
- Die Antwortform bei ATTENTION (zwei Nachrichten, Text des Clients, A-7, A-13) — [slice-cancel-antwort-zwei-nachrichten-angleichen](../in-progress/slice-cancel-antwort-zwei-nachrichten-angleichen.md); eine Regel kann mehrere Gründe tragen, die Zeilen `cancel-*/…/tokenfolge@…` entfallen erst, wenn beide Gründe weg sind.
- Die Lastenheft-Zeile zu [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) als erledigt führen ([ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) Offen 12 (c)) — Entscheidung des Nutzers, kein Bau.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit.

- [ ] **Formen ohne Ausdruck, je Entscheidung:** `OR` und die Konstanten-Auswahl (A-4, A-5) sind erkannt (Test je Form: Happy, Grenze, Negativ mit bleibendem `TDS_UNSUPPORTED_SQL` für `NOT`, `<`, `LIKE`), die zwei Live-Regeln entfallen — oder sie sind *so gewollt*.
- [ ] **`WAITFOR` mit Abbruch (A-15):** ein wartender Befehl endet auf ATTENTION, Frist und `Cancel()` wie die Referenz (Messzeilen der vier Szenarien), die Regeln `fehler` und `tokenfolge@2025` der vier Szenarien entfallen — oder sie sind *so gewollt*; die Regeln `tokenfolge@2022` bleiben mit dem Grund A-18 (SESSIONSTATE) stehen, ebenso die Regeln `…/02/tokenfolge@2022`.
- [ ] **Statement-abbrechender Fehler am Server (A-17):** ein Szenario mit zwei Anweisungen, deren erste fehlschlägt, spielt `DONE[MORE|ERROR]` am Server ab; entweder entfallen die Regeln der beiden `batch-*-abbrechend`-Szenarien, oder sie werden *so gewollt* mit der Begründung „Ausdruck“.
- [ ] `make gates` Exit 0, dazu je Art der Änderung nach `harness/README.md` §Minimal agent workflow: engste Testklasse und Mutation je Zusage über enge Testklassen (`src/`), `make docs-check`, `make flaeche`, `make kopplung-zahlen` (Doku/Spec), `make spec-abdeckung` einmal (Tests oder `@spec`-Tags) (`src/` oder `testserver/`: Verifier fährt `make clean-build` und `make e2e` je einmal auf dem End-Stand; Implementer und Reviewer nicht). Die Läufe `make vergleichslauf-d` sind Messung und Nachweis, keine Gates ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)).
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor (`.harness/skills/reviewer.md`), kein Self-Review.
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt: [SPEC-036](../../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) (Tabelle und „Nicht erkannt“), [SPEC-045](../../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig), [Kompatibilitätsmatrix](../../kompatibilitaetsmatrix.md), [Messprotokoll des Referenzabgleichs](../../../messungen/referenzabgleich.md) (A-4, A-5, A-15, A-17), `live-ausnahmen.tsv`, `mitschnitt-ausnahmen.tsv`; eine Folge-ADR schreibt der Architect, nicht dieser Slice.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben: `BEO-TDS/anweisungsformen-des-simulations-backends-enger-als-der-server`, `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt`, `BEO-TDS/mitschnitt-luecken-ohne-traeger` (A-15); keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — Pfad-Kandidaten für §8; geprüft ist das Vorhandensein der Dateien am 2026-11-17, nicht ihre Eignung.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/StatementParser.kt`, `StatementPlanner.kt`, `SimConditions.kt`, `FixedForms.kt`, `SqlScanner.kt`, `SimulationBackend.kt` | update | Terme mit `OR`, Konstanten-Auswahl neben Form C, `WAITFOR`; der Zeichenscanner bleibt die Grundlage ([ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) Entscheidung 14) |
| `src/test/kotlin/io/github/pt9912/tdswire/simulation/StatementFormsTest.kt`, `StatementPlannerTest.kt`, `SimulationBackendFixedTest.kt`, `SimulationBackendSelectTest.kt` | update | Tests je Form (Happy, Boundary, Negative) und die bleibenden Abweisungen |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/dispatch/AttentionCancelTest.kt`, `CancelTimeoutTest.kt` | update, falls `WAITFOR` gebaut | Abbruch eines wartenden Befehls (Cancel, Frist `QUERY_TIMEOUT`) gegen das neue Verhalten des Backends |
| `referenztest-dotnet/Vergleich/live-ausnahmen.tsv`, `referenztest-dotnet/Vergleich/mitschnitt-ausnahmen.tsv`, `referenztest-dotnet/Vergleich/MitschnittLaufTests.cs` | update | Regeln entfallen oder werden *so gewollt*; ein geändertes Szenario braucht eine neue Aufnahme (`LAUF=aufnahme`, Referenzinstanz) |
| `spec/spezifikation.md`, `docs/messungen/referenzabgleich.md`, `docs/plan/kompatibilitaetsmatrix.md` | update | SPEC-036 (Tabelle, „Nicht erkannt“), Tabelle der Ausnahmen, Matrix |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf (Testdateien mit Kennung der Akzeptanzkriterien).

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`open` → `next` → `in-progress`): Nutzerzeichen, ein Verantwortlicher (`Verantwortlich:` ist bis dahin `—`) und die Entscheidung des Nutzers zur Lesart der Anweisungsformen (`BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt`, [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) Offen 12 (a) und (c)) und zu jeder der vier Formen (Frage in §1); für A-17 außerdem [slice-simulation-mehrere-anweisungen-und-ausgabewert](../open/slice-simulation-mehrere-anweisungen-und-ausgabewert.md) in `done/` (Zerlegung zweier Anweisungen). Der Slice ist **nicht ohne den Nutzer startbar**. Abspielen der Mitschnitte braucht nur den Server dieses Repos; neue Aufnahmen und der Live-Lauf brauchen die gepinnten Referenzinstanzen (EULA bestätigt 2026-11-01 und 2026-11-17), laufen **nicht parallel** zu Last-Messslices, und die Messsperre `/tmp/claude-1000/-Development-tdswire/messlock` gilt für Messläufe.

**Rückführungen — vorab benennen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2 (die vier Formen können als getrennte Slices zurück in die Zerlegung gehen, wenn der Nutzer sie unterschiedlich entscheidet).
- `in-progress` → `open` (blockiert): die Folge-ADR liegt nicht vor, [slice-simulation-mehrere-anweisungen-und-ausgabewert](../open/slice-simulation-mehrere-anweisungen-und-ausgabewert.md) bleibt offen (A-17), oder eine neue Aufnahme der Referenzinstanz ist nicht möglich (Pull, Platz, geänderter EULA-Text).

## 5. Closure-Trigger

Alle Punkte der DoD in §2 erledigt, `make gates` und `make docs-check` Exit 0, die Regeln der Ausnahmeliste, die dieser Slice trägt, sind entfallen oder mit der Entscheidung des Nutzers in ihrer Art geändert (nicht still stehen gelassen), und die Closure-Notiz mit Steering-Loop-Lerneintrag ist geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang.

- `WAITFOR` verlangt ein Backend mit Zeit- und Abbruchsemantik; die Grenze zur „SQL-Engine“ des Lastenhefts ist unscharf. — **Ausgang:** offen bis zur Closure; Entscheidung des Nutzers in §1 (Option A oder B); bei B entfällt der Bau.
- Die Aufnahmen decken je Szenario einen Lauf je Instanz; `OR` und die Konstante sind im Live-Lauf je ein Fall. — **Ausgang:** offen bis zur Closure; Aussagen tragen für diese Fälle ([AGENTS.md](../../../../AGENTS.md) §3.8); weitere Messungen sind eigene Messaufträge.
- Eine Regel mit zwei Gründen (A-15 und A-18 in `tokenfolge@2022`) entfällt erst, wenn beide Gründe weg sind; A-18 bleibt „kein Bau“. — **Ausgang:** offen bis zur Closure; die Regel wird auf den Grund A-18 verkleinert, nicht gelöscht; der Selbsttest der Liste prüft die Abweichung wortgleich.
- Eine neue Anweisungsform kann Clients (d-migrate, mssql-jdbc) ein Verhalten geben, das bisher als Abweisung vorkam (`data profile` ist ausdrücklich Out-of-Scope von [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate)). — **Ausgang:** offen bis zur Closure; Negativtests für die bleibenden Abweisungen sind Teil der DoD.
- Größe: drei Liefer-Punkte. — **Ausgang:** offen bis zur Closure; Rückführung nach §4.

## 7. Closure-Notiz

Wird bei der Closure gefüllt.

**Herkunft:** Lücken A-4, A-5, A-15 und A-17 der Ausnahmeliste, [Messprotokoll des Referenzabgleichs](../../../messungen/referenzabgleich.md); Register `BEO-TDS/anweisungsformen-des-simulations-backends-enger-als-der-server` (Träger-Vorschlag „erkannte Anweisungsformen erweitern“), `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt`, `BEO-TDS/mitschnitt-luecken-ohne-traeger` (A-15), `BEO-TDS/done-more-statement-abbrechender-fehler-weicht-ab` (A-17); [Results-Notiz von welle-referenzabgleich-release](../done/welle-referenzabgleich-release-results.md). Nutzerentscheidung 2026-11-17: „ja slices anlegen“ (Frage der Closure von `welle-referenzabgleich-release`, Punkt: die 14 Lücken-Nummern ohne Träger-Slice).

## 8. Sub-Area-Prüfungen und Modus-Begründung

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register (`../observations/BEO-TDS/`) gegen die Verzeichnisliste gelesen; Treffer: `anweisungsformen-des-simulations-backends-enger-als-der-server` (offen), `lesart-einer-nutzerentscheidung-unbestaetigt` (offen, über der Schwelle), `mitschnitt-luecken-ohne-traeger`, `done-more-statement-abbrechender-fehler-weicht-ab` (gestrichen). Zähler-Stände stehen in den Dateien des Registers, nicht hier.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
