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

**Bezug:** [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) (Scope: erkannte Anweisungsformen), [TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe) (Abbruch eines wartenden Befehls), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) (Entscheidung 14, Offen 12), [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) (Abbruch), [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) (Nr. 11), [ADR-0058](../../adr/0058-or-in-der-auswahl-ganzzahlkonstante-und-waitfor-delay-sind-erkannt.md) (Entscheidung des Nutzers je Form, `Accepted` 2026-11-21; trägt die Formen und ersetzt die Lesart „abschließend“)

**Berührte Spec-Stellen:** [SPEC-036](../../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) (Tabelle der Formen, Abschnitt „Term“, Liste „Nicht erkannt“, Satz zur Lesart) · [TDS-FA-11.a](../../../../spec/spezifikation.md#tds-fa-11a--simulations-backend) (Schritte 1, 2 und 5) · [SPEC-045](../../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig) (Ausnahmeliste) · [SPEC-029](../../../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung) (Abbruch, Zusammenspiel mit `WAITFOR`).

**Verantwortlich:** pt9912

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-17.

---

## 1. Ziel und Abgrenzung

**Ziel:** Das Simulations-Backend erkennt nach [ADR-0058](../../adr/0058-or-in-der-auswahl-ganzzahlkonstante-und-waitfor-delay-sind-erkannt.md) `OR` in der Auswahl (reine Folge, nur Form S1), `SELECT <Ganzzahl>` und `WAITFOR DELAY`; die Regeln A-4, A-5 und A-15 entfallen im Rahmen der Grenzen der ADR. Die Division durch null bleibt ein Ausdruck und abgewiesen; die Regeln von A-17 bleiben *so gewollt*, je Szenario mit eigenem Grund.

**Ist-Stand** (gelesen am 2026-11-17; was nicht als gemessen steht, ist aus Datei oder Code gelesen):

- **A-4** (`SELECT 2`, Live-Schritt `anweisung-ohne-tabelle`): die Referenz gelingt, der Server `Number 50001`, „Unsupported SQL.“, Verbindung offen. Form C kennt nur `SELECT 1` ([SPEC-036](../../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig)).
- **A-5** (`WHERE Id = 1 OR Id = 2`, Live-Schritt `bedingung-mit-or`): die Referenz gelingt, der Server weist ab. [SPEC-036](../../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) führt `OR` unter „Nicht erkannt“; [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) beschreibt „Gleichheits- und Mengenbedingungen … verknüpft durch UND“ und schließt im Out-of-Scope Verbund, Aggregate, Ausdrücke, Unterabfragen und Sortierung aus, `OR` nennt es dort nicht. Die Lesart „die positive Aufzählung ist abschließend“ ist verworfen ([ADR-0058](../../adr/0058-or-in-der-auswahl-ganzzahlkonstante-und-waitfor-delay-sind-erkannt.md) Entscheidung 1; Register `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt`).
- **A-15** (`WAITFOR DELAY`): vier Szenarien des Mitschnitt-Laufs (`timeout-wartend`, `cancel-wartend`, `cancel-kurz-vor-timeout`, `cancel-kurz-nach-timeout`) tragen je Regeln `…/01/fehler` und `…/01/tokenfolge@2022`, `@2025`: die Referenz wartet bis zum ATTENTION, der Server antwortet sofort mit ERROR 50001. Der Abbruch eines **wartenden** Befehls ist am Server dieses Repos damit nicht abgespielt (nur der Abbruch im Leerlauf, [Messprotokoll des Referenzabgleichs](../../../messungen/referenzabgleich.md) A-15).
- **A-17** (zwei Anweisungen, Division durch null; Szenarien `batch-statement-abbrechend`, `batch-batch-abbrechend`): die Referenz sendet bei statement-abbrechendem Fehler `DONE` mit `MORE|ERROR`, bei batch-abbrechendem `DONE` mit `ERROR`. Der Sequenzierer schreibt diese Formen laut Register bereits aus `Failure` mit `FailureScope` (`BEO-TDS/done-more-statement-abbrechender-fehler-weicht-ab`, Stand `gestrichen`, Tests `ResponseMeasuredTest`); abgespielt am Server sind sie nicht, weil der Text zwei Anweisungen und einen Ausdruck (`1 / (Id - 4)` in der Ansicht, der Text des Szenarios steht in `MitschnittLaufTests.cs`) enthält. Die Division durch null ist ein Ausdruck, den das Lastenheft ausschließt. Das zweite Szenario (`batch-batch-abbrechend`, Text `SELECT Id FROM gibtesnicht; SELECT 2` laut Messprotokoll) enthält keinen Ausdruck: seine Regel steht wegen der Folge aus einer Auswahl und einer Konstantenauswahl (Form S2 sind genau zwei Auswahlen der Form S1).
- **Lage der Regeln** (gezählt in den beiden `.tsv`-Dateien am 2026-11-17): Live-Liste 2 Regeln (A-4, A-5); Mitschnitt-Liste 12 Regeln für A-15 (je `fehler`, `tokenfolge@2022`, `tokenfolge@2025` der vier Szenarien; die Zeilen `@2022` nennen auf Seiten der Referenz außerdem SESSIONSTATE, den A-18 trägt) und 6 Regeln für A-17 (`batch-statement-abbrechend/01/{colmetadata,done,fehler,tokenfolge,zeilen}` und `batch-batch-abbrechend/01/fehler`).

**Entscheidung des Nutzers (2026-11-21), festgehalten in [ADR-0058](../../adr/0058-or-in-der-auswahl-ganzzahlkonstante-und-waitfor-delay-sind-erkannt.md) (`Accepted`):** Die Frage „Code angleichen oder Zielbild anpassen“ ist je Form beantwortet; die Lesart „abschließend“ ist verworfen, die Folge-ADR liegt vor, der Slice ist startbar.

| Form | Entscheidung | Grenze der Erkennung (Setzungen der ADR, außer wo „gemessen“) |
|---|---|---|
| A-5 `OR` | Code angleichen | nur Form S1, reine `OR`-Folge der Terme von SPEC-036 (`=`, `IN`, `IS [NOT] NULL`); gemessen ist nur `Id = 1 OR Id = 2` (der Erfolg); `SimQuery.filter` ist bei `OR` `null`, das Backend filtert; keine Mischung mit `AND`, keine Klammer, kein Konstantenterm, kein `OR` in `UPDATE`/`DELETE` (`RowFilter` ist eine Konjunktion der öffentlichen Fläche) |
| A-4 `SELECT <Ganzzahl>` | Code angleichen | genau ein Ganzzahlliteral im Bereich `Int32`, ohne `FROM`, `WHERE`, Alias, weitere Spalte; Antwort wie `SELECT 1`; gemessen ist der Wert 2; Rechnen, Funktionen, Variablen, Klammern bleiben abgewiesen; nicht Teil von S2 |
| A-15 `WAITFOR DELAY` | Code angleichen | `WAITFOR DELAY '<hh>:<mm>:<ss>'` als einziger Text; gemessen ist nur `'00:00:10'` und der Abbruch durch ATTENTION (Paar `DONE[ERROR]` mit Befehl `0x00fd` und `DONE[ATTN]`, [ADR-0054](../../adr/0054-antwort-auf-attention-in-zwei-nachrichten.md)); `QUERY_TIMEOUT` kürzer als die Wartezeit endet mit `TDS_TIMEOUT` (Setzung); das reguläre Ende ist ungemessen (Setzung) |
| A-17 Division durch null | so gewollt | die Division ist ein Ausdruck; `batch-batch-abbrechend` bleibt abgewiesen als Folge aus S1 und Konstantenauswahl, S2 wächst nicht |

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Zwei Anweisungen in einem Text als solche (`mehrteilig`, A-2) und der Ausgabewert — [slice-simulation-mehrere-anweisungen-und-ausgabewert](../done/slice-simulation-mehrere-anweisungen-und-ausgabewert.md); Form S2 ist dort geliefert und wächst hier nicht (keine Konstantenauswahl in S2).
- Weitere Formen, die kein Szenario des Referenzabgleichs abspielt: `JOIN`, `ORDER BY` und Aggregate sind im Lastenheft wörtlich ausgeschlossen; `NOT`, `<`, `>`, `LIKE`, `BETWEEN`, `<>`, `!=`, `NOT IN`, `DISTINCT`, `TOP`, Aliase und `OUTPUT` bleiben `TDS_UNSUPPORTED_SQL`, weil sie nicht gebaut und nicht gemessen sind, nicht weil das Lastenheft sie ausschlösse ([ADR-0058](../../adr/0058-or-in-der-auswahl-ganzzahlkonstante-und-waitfor-delay-sind-erkannt.md) Entscheidung 1; [AGENTS.md](../../../../AGENTS.md) §3.8).
- `OR` in `UPDATE` und `DELETE` und eine Änderung der öffentlichen Fläche von `RowFilter`: eigene ADR ([ADR-0058](../../adr/0058-or-in-der-auswahl-ganzzahlkonstante-und-waitfor-delay-sind-erkannt.md) Entscheidung 3).
- Ein Bau für A-17: kein Szenario mit Division wird abgespielt; `DONE[MORE|ERROR]` am Server bleibt unabgespielt und ist durch `ResponseMeasuredTest` belegt ([ADR-0058](../../adr/0058-or-in-der-auswahl-ganzzahlkonstante-und-waitfor-delay-sind-erkannt.md) Entscheidung 6).
- SESSIONSTATE in denselben Austauschen (A-18, nur Instanz 2022): bewusst „kein Bau“ mit Re-Evaluierungs-Trigger (Nutzerentscheid 2026-11-06, `BEO-TDS/mitschnitt-luecken-ohne-traeger`); die Regeln `…/02/tokenfolge@2022` der Cancel-Szenarien bleiben deshalb stehen.
- Die Antwortform bei ATTENTION (zwei Nachrichten, Text des Clients, A-7, A-13) — [slice-cancel-antwort-zwei-nachrichten-angleichen](../done/slice-cancel-antwort-zwei-nachrichten-angleichen.md); eine Regel kann mehrere Gründe tragen, die Zeilen `cancel-*/…/tokenfolge@…` entfallen erst, wenn beide Gründe weg sind.
- Die Lastenheft-Zeile zu [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) als erledigt führen ([ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) Offen 12 (c)) — Entscheidung des Nutzers, kein Bau; [ADR-0058](../../adr/0058-or-in-der-auswahl-ganzzahlkonstante-und-waitfor-delay-sind-erkannt.md) Entscheidung 7 lässt das Lastenheft unverändert.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit.

- [ ] **`OR` und `SELECT <Ganzzahl>` (A-5, A-4):** erkannt nach [ADR-0058](../../adr/0058-or-in-der-auswahl-ganzzahlkonstante-und-waitfor-delay-sind-erkannt.md) Entscheidungen 2 bis 4; Test je Form (Happy, Grenze, Negativ). Negativtests mit bleibendem `TDS_UNSUPPORTED_SQL`: `OR` in `UPDATE` und `DELETE`, Mischung aus `AND` und `OR`, Klammer, Konstantenterm in einer `OR`-Folge, `NOT`, `<`, `>`, `LIKE`, `BETWEEN`, `<>`, `!=`, `NOT IN`; für die Konstante `2147483648`, `1 + 1`, `1 / 0`, `1, 2`, `2 AS x`, `'a'`, `NULL`, `1.5`, `@p`, `@@VERSION`. Die zwei Live-Regeln entfallen, und die Live-Schritte `anweisung-ohne-tabelle` und `bedingung-mit-or` lesen danach Spaltentyp und Werte (nicht nur „keine Ausnahme“), sonst hieße „gleich“ nur „gelingt“.
- [ ] **`WAITFOR DELAY` mit Abbruch (A-15):** gemäß [ADR-0058](../../adr/0058-or-in-der-auswahl-ganzzahlkonstante-und-waitfor-delay-sind-erkannt.md) Entscheidung 5. Gemessen ist nur `'00:00:10'`; der Bereich von `hh:mm:ss` ist Setzung. ATTENTION im Warten endet im Paar `DONE[ERROR]` und `DONE[ATTN]` ([ADR-0054](../../adr/0054-antwort-auf-attention-in-zwei-nachrichten.md)), `QUERY_TIMEOUT` kürzer als die Wartezeit endet mit `TDS_TIMEOUT` (Setzung), das reguläre Ende ist ungemessen (Setzung); die Tests warten die Zeit nicht ab. Die Regeln `fehler` und `tokenfolge@2025` der vier Szenarien entfallen; die Regeln `tokenfolge@2022` bleiben mit dem Grund A-18 (SESSIONSTATE) stehen, ebenso die Regeln `…/02/tokenfolge@2022`.
- [ ] **Regeln von A-17 bleiben mit je eigenem Grund (kein Bau):** `batch-statement-abbrechend` (fünf Regeln): die Anweisung enthält einen Ausdruck (Division durch null); `batch-batch-abbrechend` (`…/01/fehler`): kein Ausdruck, sondern eine Folge aus einer Auswahl und einer Konstantenauswahl, die Form S2 nicht kennt (Folge aus S1 und Konstantenauswahl). Beide Gründe stehen je Szenario in `mitschnitt-ausnahmen.tsv` und im Messprotokoll so, dass sie wahr sind; der Selbsttest der Liste prüft die Abweichung wortgleich.
- [ ] `make gates` Exit 0, dazu je Art der Änderung nach `harness/README.md` §Minimal agent workflow: engste Testklasse und Mutation je Zusage über enge Testklassen (`src/`), `make docs-check`, `make flaeche`, `make kopplung-zahlen` (Doku/Spec), `make spec-abdeckung` einmal (Tests oder `@spec`-Tags) (`src/` oder `testserver/`: Verifier fährt `make clean-build` und `make e2e` je einmal auf dem End-Stand; Implementer und Reviewer nicht). Die Läufe `make vergleichslauf-d` sind Messung und Nachweis, keine Gates ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)).
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor (`.harness/skills/reviewer.md`), kein Self-Review.
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt: [SPEC-036](../../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) (Tabelle und „Nicht erkannt“), [SPEC-045](../../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig), [Kompatibilitätsmatrix](../../kompatibilitaetsmatrix.md), [Messprotokoll des Referenzabgleichs](../../../messungen/referenzabgleich.md) (A-4, A-5, A-15 entfallen oder verkleinern sich, A-17 *so gewollt* mit zwei Gründen), `live-ausnahmen.tsv`, `mitschnitt-ausnahmen.tsv`, [TDS-FA-11.a](../../../../spec/spezifikation.md#tds-fa-11a--simulations-backend) (Schritte 1, 2 und 5), [SPEC-029](../../../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung) (der wartende Befehl wird am Server abgespielt), `docs/user/einbettung-und-betrieb.md` (Aufzählung „Was das Backend erkennt“); die ADR liegt vor, dieser Slice zieht die Spezifikation nach ([ADR-0058](../../adr/0058-or-in-der-auswahl-ganzzahlkonstante-und-waitfor-delay-sind-erkannt.md) Konsequenzen).
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben: `BEO-TDS/anweisungsformen-des-simulations-backends-enger-als-der-server`, `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt`, `BEO-TDS/mitschnitt-luecken-ohne-traeger` (A-15); keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — Pfad-Kandidaten für §8; geprüft ist das Vorhandensein der Dateien am 2026-11-21, nicht ihre Eignung.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/StatementParser.kt`, `StatementPlanner.kt`, `SimConditions.kt`, `FixedForms.kt`, `SqlScanner.kt`, `SimulationBackend.kt` | update | Terme mit `OR`, Konstanten-Auswahl neben Form C, `WAITFOR`; der Zeichenscanner bleibt die Grundlage ([ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) Entscheidung 14) |
| `src/test/kotlin/io/github/pt9912/tdswire/simulation/StatementFormsTest.kt`, `StatementPlannerTest.kt`, `SimulationBackendFixedTest.kt`, `SimulationBackendSelectTest.kt` | update | Tests je Form (Happy, Boundary, Negative) und die bleibenden Abweisungen |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/dispatch/AttentionCancelTest.kt`, `CancelTimeoutTest.kt` | update | Abbruch eines wartenden Befehls (Cancel, Frist `QUERY_TIMEOUT`) gegen das neue Verhalten des Backends |
| `referenztest-dotnet/Vergleich/live-ausnahmen.tsv`, `referenztest-dotnet/Vergleich/mitschnitt-ausnahmen.tsv`, `referenztest-dotnet/Vergleich/MitschnittLaufTests.cs` | update | Regeln A-4, A-5 und die acht Regeln `fehler`/`tokenfolge@2025` von A-15 entfallen; `tokenfolge@2022` und die Regeln von A-17 bleiben mit neu gefasstem Grund; die Live-Schritte `anweisung-ohne-tabelle` und `bedingung-mit-or` lesen Spaltentyp und Werte; keine neue Aufnahme nötig |
| `spec/spezifikation.md` | update | SPEC-036: Zeile C um `SELECT <Ganzzahl>`, neue Zeile Z, Abschnitt „Term“ um die `OR`-Folge in S1, „Nicht erkannt“ ohne `OR` in S1, der Satz zur Lesart („abschließend … eine Lesart des Lastenhefts, vom Nutzer nicht bestätigt“) ersetzt durch die Aussage von [ADR-0058](../../adr/0058-or-in-der-auswahl-ganzzahlkonstante-und-waitfor-delay-sind-erkannt.md) Entscheidung 1; [TDS-FA-11.a](../../../../spec/spezifikation.md#tds-fa-11a--simulations-backend) Schritte 1, 2 und 5; SPEC-029 (wartender Befehl am Server); SPEC-045 (Ausnahmeliste) |
| `docs/messungen/referenzabgleich.md`, `docs/plan/kompatibilitaetsmatrix.md`, `docs/user/einbettung-und-betrieb.md` | update | Tabelle der Ausnahmen (A-4, A-5, A-15 entfallen oder verkleinern sich; A-17 *so gewollt* mit zwei Gründen), Matrix, Aufzählung „Was das Backend erkennt" |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf (Testdateien mit Kennung der Akzeptanzkriterien).

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`open` → `next` → `in-progress`): Nutzerzeichen, ein Verantwortlicher (erfüllt: pt9912) und die Entscheidung des Nutzers zur Lesart und zu den vier Formen (erfüllt: [ADR-0058](../../adr/0058-or-in-der-auswahl-ganzzahlkonstante-und-waitfor-delay-sind-erkannt.md) `Accepted`, 2026-11-21; Offen 12 (c) von [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md), die Lastenheft-Zeile, bleibt beim Nutzer und blockiert nicht). Abspielen der Mitschnitte braucht nur den Server dieses Repos; neue Aufnahmen und der Live-Lauf brauchen die gepinnten Referenzinstanzen (EULA bestätigt 2026-11-01 und 2026-11-17), laufen **nicht parallel** zu Last-Messslices, und die Messsperre `/tmp/claude-1000/-Development-tdswire/messlock` gilt für Messläufe.

**Rückführungen — vorab benennen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2 (`OR` und Konstantenauswahl einerseits, `WAITFOR` andererseits können als getrennte Slices zurück in die Zerlegung gehen).
- `in-progress` → `open` (blockiert): ein Live-Lauf gegen die Referenzinstanzen ist nicht möglich (Pull, Platz, geänderter EULA-Text); die Messung der Antwortform von `OR` und der Konstante hängt an ihm.

## 5. Closure-Trigger

Alle Punkte der DoD in §2 erledigt, `make gates` und `make docs-check` Exit 0, die Regeln der Ausnahmeliste, die dieser Slice trägt, sind entfallen oder mit der Entscheidung des Nutzers in ihrer Art geändert (nicht still stehen gelassen), und die Closure-Notiz mit Steering-Loop-Lerneintrag ist geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang.

- `WAITFOR` verlangt ein Backend mit Zeit- und Abbruchsemantik; die Grenze zur „SQL-Engine“ des Lastenhefts ist unscharf. — **Ausgang:** weiter offen bis zur Closure; die Entscheidung des Nutzers liegt vor ([ADR-0058](../../adr/0058-or-in-der-auswahl-ganzzahlkonstante-und-waitfor-delay-sind-erkannt.md)), die Wartezeit ist durch `QUERY_TIMEOUT` begrenzt, der Bau bleibt auf die Form Z beschränkt.
- Die Aufnahmen decken je Szenario einen Lauf je Instanz; `OR` und die Konstante sind im Live-Lauf je ein Fall. — **Ausgang:** offen bis zur Closure; Aussagen tragen für diese Fälle ([AGENTS.md](../../../../AGENTS.md) §3.8); weitere Messungen sind eigene Messaufträge.
- Eine Regel mit zwei Gründen (A-15 und A-18 in `tokenfolge@2022`) entfällt erst, wenn beide Gründe weg sind; A-18 bleibt „kein Bau“. — **Ausgang:** offen bis zur Closure; die Regel wird auf den Grund A-18 verkleinert, nicht gelöscht; der Selbsttest der Liste prüft die Abweichung wortgleich.
- Eine neue Anweisungsform kann Clients (d-migrate, mssql-jdbc) ein Verhalten geben, das bisher als Abweisung vorkam (`data profile` ist ausdrücklich Out-of-Scope von [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate)). — **Ausgang:** offen bis zur Closure; Negativtests für die bleibenden Abweisungen sind Teil der DoD.
- Größe: drei Liefer-Punkte (Formen `OR` und Konstante, `WAITFOR`, Regeln von A-17 ohne Bau), dazu der Spec-Nachzug als Doku-Update; Schichten: Backend, Protokollkern-Tests, Referenztest. — **Ausgang:** offen bis zur Closure; Rückführung nach §4.

## 7. Closure-Notiz

Wird bei der Closure gefüllt.

**Herkunft:** Lücken A-4, A-5, A-15 und A-17 der Ausnahmeliste, [Messprotokoll des Referenzabgleichs](../../../messungen/referenzabgleich.md); Register `BEO-TDS/anweisungsformen-des-simulations-backends-enger-als-der-server` (Träger-Vorschlag „erkannte Anweisungsformen erweitern“), `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt`, `BEO-TDS/mitschnitt-luecken-ohne-traeger` (A-15), `BEO-TDS/done-more-statement-abbrechender-fehler-weicht-ab` (A-17); [Results-Notiz von welle-referenzabgleich-release](../done/welle-referenzabgleich-release-results.md). Nutzerentscheidung 2026-11-17: „ja slices anlegen“ (Frage der Closure von `welle-referenzabgleich-release`, Punkt: die 14 Lücken-Nummern ohne Träger-Slice).

## 8. Sub-Area-Prüfungen und Modus-Begründung

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register (`../observations/BEO-TDS/`) gegen die Verzeichnisliste gelesen; Treffer: `anweisungsformen-des-simulations-backends-enger-als-der-server` (offen), `lesart-einer-nutzerentscheidung-unbestaetigt` (offen, über der Schwelle), `mitschnitt-luecken-ohne-traeger`, `done-more-statement-abbrechender-fehler-weicht-ab` (gestrichen). Zähler-Stände stehen in den Dateien des Registers, nicht hier.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
