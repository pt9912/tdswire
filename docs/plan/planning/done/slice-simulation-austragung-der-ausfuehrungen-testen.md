# Slice slice-simulation-austragung-der-ausfuehrungen-testen: Austragung der Ausführungen aus der Registrierung des Simulations-Backends je Form testen

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** ohne Welle (keine Closure-Bedingung außerhalb der DoD dieses Slice; Folge-Slice der Closure von [slice-simulation-anweisungsformen-erweitern](../done/slice-simulation-anweisungsformen-erweitern.md): dort trägt ein Test die Austragung der `WAITFOR`-Ausführung, für die übrigen Ausführungen fehlt er).

**Bezug:** [TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe) (Ressourcenfreigabe), [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) (Abbruch), [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md)

**Berührte Spec-Stellen:** [SPEC-029](../../../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung) (Abbruch, Austragung der Ausführung) · [SPEC-035](../../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) (Verhalten des Backends), nur wenn der Test ein Leck findet und die Beschreibung ändert.

**Verantwortlich:** pt9912

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-22.

---

## 1. Ziel und Abgrenzung

**Ziel:** Für jede Ausführung des Simulations-Backends, die sich unter (Sitzung, Anfrage) in `running` einträgt (`SelectExecution`, `ChangeExecution`, `MergeExecution`), beobachtet ein Test, dass der Eintrag nach Ende, Fehler, `cancel` und `close()` verschwindet, und wird bei Entfernung der Austragung rot.

**Ist-Stand** (gelesen am 2026-11-22; was nicht als gemessen steht, ist aus Datei oder Code gelesen):

- Die Austragung steht in `SelectExecution`, `ChangeExecution` (`onEnd` in `onCompletion` und in `close()`) und `MergeExecution` (`whenEnded`), verdrahtet in `SimulationBackend.kt` (`running.remove(key, it)`).
- Für `WaitExecution` trägt sie `SimulationBackendWaitTest` („die Registrierung endet mit Ablauf Cancel Abbruch und close“) über die interne Naht `SimulationBackend.registrations`; Mutationen M22 bis M25 und R5 färben ihn rot ([Mutationsbeleg](../../../messungen/mutationen-anweisungsformen-2026-11-21.md), [Re-Review](../../../reviews/2026-11-21-slice-simulation-anweisungsformen-erweitern-re-review.md)).
- Für die drei anderen Ausführungen ist die Lücke im Beleg benannt: die Mutation R2 des Reviews (Austragung entfernt) ist dort nicht gefahren, und `registrations` kommt in den Tests nur in `SimulationBackendWaitTest` vor (Suche am 2026-11-22). Ob die Austragung dort wirkt, ist damit nicht belegt; Failure-Szenario: ein Server mit vielen Anfragen behielte je Anfrage einen Eintrag im `ConcurrentHashMap` des Backends.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- `WaitExecution`: getragen durch `SimulationBackendWaitTest`.
- Das Verhalten des Protokollkerns bei Abbruch ([SPEC-029](../../../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung)): getestet in `AttentionCancelTest` und `CancelTimeoutTest`, hier nicht berührt.
- Eine Änderung der öffentlichen Fläche: die Naht `registrations` ist `internal`; `make flaeche` bleibt grün.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit.

- [x] **Test je Ausführung:** `SelectExecution` (S1, S2, Form C), `ChangeExecution` (`INSERT`, `UPDATE`, `DELETE`, auch über RPC) und `MergeExecution`: die Zahl der Registrierungen sinkt nach Ende, Fehler der Zeilenquelle oder des Callbacks, `cancel`, Abbruch des Sammlers und `close()` ohne Sammeln auf den Stand vor der Anfrage; der Test wartet keine Wanduhr ab.
- [x] **Mutation je Zusage:** die Austragung je Ausführung entfernt und je Verdrahtung in `SimulationBackend.kt` leer gesetzt macht je einen Test rot (Mutationsbeleg als Datei im Repo); findet ein Test ein Leck, ist die Behebung Teil dieses Slice.
- [x] `make gates` Exit 0, dazu je Art der Änderung nach `harness/README.md` §Minimal agent workflow: engste Testklasse und Mutation je Zusage über enge Testklassen (`src/`), `make spec-abdeckung` einmal (Tests oder `@spec`-Tags), `make flaeche`, `make docs-check` (Verifier fährt `make clean-build` und `make e2e` je einmal auf dem End-Stand; Implementer und Reviewer nicht).
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor (`.harness/skills/reviewer.md`), kein Self-Review.
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben: `BEO-TDS/austragung-aus-der-registrierung-ohne-test-bei-select-change-merge` (Ausgang nachführen); keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — Pfad-Kandidaten für §8; geprüft ist das Vorhandensein der Dateien am 2026-11-22, nicht ihre Eignung.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/test/kotlin/io/github/pt9912/tdswire/simulation/SimulationBackendRegistrationTest.kt` | create | ein Test je Form über `SimulationBackend.registrations` (S1, S1 mit Parameter, S2, Änderungen, Strukturänderung, `MERGE`, Form C) statt Änderungen in vier Testklassen: die Zusage steht an einer Stelle, die Vorlage bleibt `SimulationBackendWaitTest` |
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/SelectExecution.kt`, `SimulationChanges.kt`, `SimulationMerges.kt`, `SimulationBackend.kt` | update, nur falls ein Test ein Leck findet | Behebung |
| `docs/messungen/mutationen-austragung-der-ausfuehrungen-2026-11-22.md` (Mutationsbeleg) | create | Mutation je Zusage als Datei im Repo |
| `docs/messungen/mutationen-anweisungsformen-2026-11-21.md` | update | der Satz „Lücke benannt, nicht geschlossen“ zeigt auf den neuen Beleg |
| `docs/plan/planning/observations/BEO-TDS/austragung-aus-der-registrierung-ohne-test-bei-select-change-merge/state.md` | update | Träger und Beleg nachgeführt |
| `docs/plan/spec-abdeckung.md` | update | `make spec-abdeckung` erzeugt die Matrix neu: die Tests des Slice tragen `@spec`-Tags, die Zählung je `SPEC-*` ändert sich |
| `docs/plan/planning/done/slice-simulation-austragung-der-ausfuehrungen-testen.md` | update | dieser Plan: Liste abgeglichen, DoD-Häkchen |
| `docs/reviews/2026-11-22-slice-simulation-austragung-der-ausfuehrungen-testen.md` | create | Review-Report des Reviewers (frischer Kontext), nicht Teil der Implementer-Lieferung |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf und gleicht sie am Ende gegen `git diff main...HEAD --stat` ab.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`open` → `next` → `in-progress`): Nutzerzeichen und ein Verantwortlicher (`Verantwortlich:` ist bis dahin `—`).

**Rückführungen — vorab benennen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2 oder ein Leck verlangt einen Umbau der Registrierung.

## 5. Closure-Trigger

Alle Punkte der DoD in §2 erledigt, `make gates` und `make docs-check` Exit 0, und die Closure-Notiz mit Steering-Loop-Lerneintrag ist geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang.

- Die Austragung wirkt bei einer Ausführung nicht (ein Leck je Anfrage). — **Ausgang:** entfallen; der Test fand kein Leck (7 von 7 grün auf dem unveränderten Stand, dreimal in Folge vom Verifier), der Produktionscode ist unverändert (`git diff main...HEAD -- src/main` leer).
- Die Naht `registrations` zählt alle Ausführungen unter einem Schlüssel, ein Test mit zwei gleichzeitigen Anfragen unterscheidet sie nicht (Review F-4: `running.remove(key)` statt `running.remove(key, it)` an der Verdrahtung des `MERGE` bleibt grün, Mutation E1, gemessen). — **Ausgang:** weiter offen, Register `BEO-TDS/austragung-der-ausfuehrungen-test-grenzen-offen`; Gegenstand des Slice ist die Austragung je Anfrage, ob das Protokoll zwei Anfragen unter einem Schlüssel zulässt, ist nicht untersucht.
- Die Wirkung von `cancel` auf die Austragung ist nicht getrennt belegt (Review F-3, Verifikation: Ende, Fehler und Cancel laufen durch dieselbe `onCompletion`; `cancel` ohne Wirkung, Mutation E2, lässt alle sieben Testfälle grün). — **Ausgang:** weiter offen, derselbe Registereintrag; der Schritt „nach dem Cancel“ belegt den Zustand nach Cancel und Ende, nicht die Wirkung von `cancel`; `cancel` selbst tragen `SimulationBackendWaitTest` und die Tests der Quelle. E2 steht im Mutationsbeleg.
- Form C ist an zwei Texten belegt (`SELECT 1`, `SET LOCK_TIMEOUT 30000`), nicht an Katalogabfragen, Form D und `INSERT BULK` (Review F-5). — **Ausgang:** weiter offen, derselbe Registereintrag; die übrigen Formen ohne Zeilenquelle laufen nach dem gelesenen Code ohne Registrierung (nicht gemessen).
- Der Fehler der Zeilenquelle ist nur in `next()` belegt, nicht beim Öffnen (`rows` wirft; Review F-5). — **Ausgang:** weiter offen, derselbe Registereintrag; nicht gemessen.
- `SPEC-029` nennt die Austragung der Registrierung nicht ausdrücklich; der `@spec`-Tag der sieben Testfälle ist eine Zuordnung durch den Slice (Verifikation Frage 4). — **Ausgang:** weiter offen, Entscheidung des Nutzers; die Spezifikation ist nicht geändert; Register `BEO-TDS/spec-029-nennt-die-austragung-der-registrierung-nicht`.

## 7. Closure-Notiz

Geschlossen 2026-11-22 (Planner, Slice-Closure ohne Welle). Geprüfter Stand: Zweig `slice-simulation-austragung-der-ausfuehrungen-testen`, Verifikation auf `fb68e9a1` (danach nur Berichte und Doku); Commits: Test `2ab3f12e`, Mutationsbeleg, Register und Plan-Tabelle `c19b889c`, Review-Report `e685ce2f`, Nachbesserung `fb68e9a1`, Verifikationsbericht `da23f1fc`; danach nur Doku (die Commits dieser Closure). Prüfung: [Review](../../../reviews/2026-11-22-slice-simulation-austragung-der-ausfuehrungen-testen.md) (kein HIGH; F-1 MEDIUM und F-2 LOW behoben, F-3 bis F-6 INFO ohne Aktion am Code), [Verifikation](../../../reviews/2026-11-22-slice-simulation-austragung-der-ausfuehrungen-testen-verifikation.md) (keine Abweichung; drei Lücken fehlten in §6, in dieser Closure nachgeführt; Empfehlung Closure ja).

**Nachbesserung ohne zweiten Review (Vermerk der Regel `BEO-TDS/nachbesserung-ohne-zweiten-review`).** `fb68e9a1` ändert die Plan-Tabelle in §3 und in `SimulationBackendRegistrationTest.kt` ausschließlich das KDoc des S1-Testfalls (Satz im Indikativ, eine Zeile umbrochen); kein Quelltext, kein Testcode, keine Konfiguration. Der Verifier fuhr auf diesem Stand `make clean-build` (detekt eingeschlossen), `make gates` und `make e2e`. Die Commits dieser Closure ändern nur Doku (Mutationsbeleg, Register, Plan, diese Notiz). Test- und Quellcode des Slice haben Review und Verifikation gesehen.

**Gegenstand.** `SimulationBackendRegistrationTest` (7 Testfälle, je `@spec SPEC-029`) beobachtet über die interne Naht `SimulationBackend.registrations` die Zahl der Registrierungen von `SelectExecution` (S1, S1 mit Parameter, S2, Form C), `ChangeExecution` (Änderung als Text und über RPC, Strukturänderung) und `MergeExecution` nach Ende, Fehler der Zeilenquelle oder des Callbacks, `cancel`, Abbruch des Sammlers und `close()` ohne Sammeln; die Haltepunkte sind `CompletableDeferred`, keine Wanduhr. Der Test fand **kein Leck**: auf dem unveränderten Stand 7 von 7 grün, der Produktionscode ist unverändert (`git diff main...HEAD -- src/main` leer). Der [Mutationsbeleg](../../../messungen/mutationen-austragung-der-ausfuehrungen-2026-11-22.md) führt 18 Mutationen (Austragung je Klasse und je Weg entfernt, je Verdrahtung leer gesetzt, Form C trägt sich nicht aus), die den Test rot färben, die Gegenprobe (ohne den Test bleibt das Paket `simulation` grün) und zwei Mutationen, die grün bleiben (E1, E2). **Nicht belegt, benannt:** die Wirkung von `cancel` auf die Austragung (Ende, Fehler und Cancel laufen durch dieselbe `onCompletion`; E2 grün), zwei gleichzeitige Anfragen unter einem Schlüssel (E1 grün, nur an der Verdrahtung des `MERGE` gefahren), Form C an nur zwei Texten, ein Fehler der Zeilenquelle beim Öffnen.

**Beleg je DoD-Punkt.**

- Punkt 1, Test je Ausführung: erfüllt, mit der Lesart: „nach `cancel`“ belegt den **Zustand** nach Cancel und Ende, nicht die Wirkung von `cancel` (Review F-3, Verifikation Frage 1). Sieben Testfälle `PASSED` im `make clean-build` des Verifiers (672 s, 1565 Zeilen `PASSED`); dreimal in Folge grün mit der engsten Testklasse (60 bis 73 s je Lauf).
- Punkt 2, Mutation je Zusage: erfüllt. Beleg als Datei im Repo; von einer zweiten Rolle nachgefahren und gleich dem Beleg: Review N2, N4, N9, N11, N14 und V1 (N3, N6, N9 gegen das Paket ohne den Test), Verifikation N5, N7, N12, N15, N16; von keiner zweiten Rolle gefahren: N1, N8, N10, N13, N17, N18 (Einzelmessungen, ein Host, Last nicht erhoben; das Setz-Skript liegt nicht im Repo, der Beleg sagt es). Kein Leck, daher keine Behebung.
- Punkt 3, Sensoren: Verifier auf `fb68e9a1`: `make clean-build` Exit 0 (672 s), `make gates FORCE=1` Exit 0 (41 s), `make docs-check` Exit 0, `make e2e` Exit 0 (9 Stufen, 509 s; Messsperre selbst angelegt und freigegeben). `make kompatibilitaet` und `make vergleichslauf-d` entfielen, weil `src/main` unverändert ist (`make kompatibilitaet` läuft als Stufen 4 bis 7 in `make e2e`). `make spec-abdeckung` fuhr der Implementer einmal (Zeile `SPEC-029` 53 auf 60, 53 + 7 = 60, vom Verifier gegengelesen, nicht wiederholt); in dieser Closure ist keine `SPEC-*`-Kennung neu oder entfernt, der Planner fuhr es nicht. Läufe des Planners: siehe die Zeile am Ende dieser Notiz.
- Punkt 4, Review: erfüllt (Reviewer-Kontext, Verifikation als weitere Sicht, kein Self-Review).
- Punkte 5 bis 7: durch diese Notiz, die Zeilen unten und §6 (sechs Risiken, je ein Ausgang).
- Punkt 8, Paarungen: im eigenen Commit nach dem `git mv` abgehakt (unten).

**Zuordnung je Aussage.** *Gemessen* (Einzelläufe, ein Host, Docker): die roten und grünen Läufe der Mutationen und die dreimaligen Läufe der Klasse (Verifikation), die Läufe der Sensoren oben. *Gelesen, nicht gemessen:* dass die Formen ohne Zeilenquelle (Katalogabfragen, Form D, `INSERT BULK`) sich nicht eintragen; ob das Protokoll zwei Anfragen unter einem Schlüssel zulässt. *Setzung des Slice:* der `@spec`-Tag `SPEC-029` der sieben Testfälle (die Spezifikation nennt die Austragung nicht ausdrücklich; Verifikation Frage 4).

**Was hat funktioniert.** Das Muster war im Review des Vorgänger-Slice per Mutation (R2 an der `WAITFOR`-Ausführung) gefunden, die Lücke bei den drei anderen Ausführungen als benannte Lücke mit Register-Eintrag und Träger-Slice geführt; dieser Slice schloss sie in einem Test statt vier Änderungen in vier Testklassen. Der Reviewer fuhr die Mutationen nach und fand die zwei grünen (E1, E2); der Verifier fuhr fünf weitere nach und bestätigte alle Zahlen. Der Beleg führte seine Grenzen im selben Absatz wie die Zusagen (`AGENTS.md` §3.8).

**Was ging anders als geplant.** (a) Die Plan-Tabelle §3 nannte die Abdeckungsmatrix nicht (Review F-1, wiederkehrend, `BEO-TDS/plan-tabelle-nennt-neue-datei-nicht`). (b) Das KDoc eines Testfalls stand im Konjunktiv über den abwesenden Zustand (F-2, `AGENTS.md` §3.7). (c) Drei Lücken, die Beleg und Review benannten, standen nicht in §6 (Verifikation Frage 2); der Beleg war breiter als die Risikoliste. (d) Die Zusage „nach dem Cancel“ trägt als Zustand, nicht als Wirkung; das stand im Beleg, die Lesart musste in DoD-Beleg und §6 nachgeführt werden.

**Steering-Loop-Eintrag.** *Benannte Spec-Lücke, offen, Träger Nutzer (versioniert benannt, daher ohne `LH-*`-Kennung):* `SPEC-029` nennt die Austragung der Registrierung nach Ende, Fehler, Cancel und `close()` nicht, der Tag der Tests ist eine Zuordnung durch den Slice; ob die Austragung eine Zusage der Spezifikation ist, entscheidet der Nutzer, die Spezifikation ist nicht geändert: Register `BEO-TDS/spec-029-nennt-die-austragung-der-registrierung-nicht` (neu, 1×). *Geschärfte Regel und neuer Sensor: nicht verkörpert.* Kein Eintrag dieses Slice trägt `liegt in`. *Lerneffekte, Setzung des Planners, nicht verkörpert:* (1) Eine Plan-Tabelle wird beim Abschluss des Baus gegen `git diff main...HEAD --stat` abgeglichen, auch für Dateien, die ein Sensor der DoD erzeugt (hier `make spec-abdeckung` → die Abdeckungsmatrix), für den Review-Report und den Slice-Plan selbst; der Befund kehrte wieder, die verkörperte Regel im Reviewer-Skill fing ihn (Retirement-Check: wiedergekehrt, die Regel bleibt). (2) Ein Test, der Wege nacheinander prüft, trägt den ersten roten Schritt; ein Schritt, den keine Mutation trennt („nach dem Cancel“ bei gemeinsamer `onCompletion`), ist ein Zustand und steht so in Beleg, DoD-Beleg und §6. (3) Jede Grenze unter „Lesarten und Lücken“ eines Mutationsbelegs steht in §6 mit Ausgang; der Verifier fand drei, die fehlten. Ein Sensor dafür bräuchte einen Abgleich Beleg gegen Risikoliste und rät, welche Zeile eine Grenze ist: akzeptiertes Negativ. *Wiederauftreten, als Evidenz geführt:* `BEO-TDS/plan-tabelle-nennt-neue-datei-nicht` (F-1). *Kein Auftreten, Prüfpunkt eingehalten:* `BEO-TDS/mutationsbeleg-erzeuger-nicht-im-repo` (Beleg im Repo, Erzeuger nicht, benannt; Stand 1×), `BEO-TDS/nachbesserung-ohne-zweiten-review` (Vermerk oben), `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test` (der Slice führt keinen Eigencode ein; E1, die Zeile `remove(key, it)` ohne unterscheidenden Test, ist eine benannte Grenze des neuen Tests und im eigenen Eintrag geführt, nicht als Befund gezählt). Gelesen wurden die von diesem Slice berührten Einträge; das Register insgesamt wurde bei dieser Closure nicht auf weitere Einträge mit drei Dateien durchgesehen.

**Beobachtungs-Register (`../observations/`).** Ausgang nachgeführt: `BEO-TDS/austragung-aus-der-registrierung-ohne-test-bei-select-change-merge` (gestrichen: die Lücke ist geschlossen, kein Leck). Neu: `BEO-TDS/austragung-der-ausfuehrungen-test-grenzen-offen` (1×; Wirkung von `cancel`, gleichzeitige Anfragen, Form C an zwei Texten, Fehler beim Öffnen) und `BEO-TDS/spec-029-nennt-die-austragung-der-registrierung-nicht` (1×). Fortgeschrieben, je eine Evidence-Datei: `BEO-TDS/plan-tabelle-nennt-neue-datei-nicht`. Nur im Eintrag: `BEO-TDS/mutationsbeleg-erzeuger-nicht-im-repo` (Prüfpunkt-Absatz).

**Folge-Slices.** Keine angelegt: die vier Grenzen sind hingenommen und tragen im Register einen Eintrag ohne Träger-Slice (ein Träger entsteht, wenn eine Grenze einen Befund auslöst); die Spec-Lücke liegt beim Nutzer. Roadmap: nicht angefasst (Zeile 13 setzt der Orchestrator beim Merge).

**Risiken aus §6.** Ausgänge je Risiko in §6: Leck entfallen; gleichzeitige Anfragen, Wirkung von `cancel`, Form C an zwei Texten, Fehler beim Öffnen weiter offen (`BEO-TDS/austragung-der-ausfuehrungen-test-grenzen-offen`); `SPEC-029` weiter offen (Nutzerentscheidung, `BEO-TDS/spec-029-nennt-die-austragung-der-registrierung-nicht`).

**Drei Paarungen.** (a) Anker: kein Eintrag trägt `liegt in`, nichts zu prüfen. (b) Folge-Slice: kein Folge-Slice genannt, nichts zu prüfen. (c) Register: jede genannte Kennung `BEO-TDS/<slug>` existiert als Verzeichnis und trägt ein nicht leeres `evidence/`. Geprüft nach dem `git mv` (unten).

**Läufe des Planners** (auf `da3d51d8`, vor dem `git mv`; Docker, Einzelläufe, Wanduhr gemessen): `make docs-check` Exit 0 (1765 Dateien, 0 Befunde), `make flaeche` Exit 0, `make kopplung-zahlen` Exit 0 (zusammen 6 s), `make gates` Exit 0 (11 s; die Klassen `werkzeug`, `tests` und `stempel` mit Beleg übersprungen, `src/` und `testserver/` sind gegenüber `fb68e9a1` unverändert, der Beleg der Klasse `tests` stammt vom `make gates FORCE=1` des Verifiers; `immer` gelaufen, `gate-zuordnung` 33 Fälle, `stempel-eingaenge` ok). Der Lauf nach dem `git mv` steht im Commit der letzten DoD-Zeile.

**Herkunft:** Review F-2 und Re-Review von [slice-simulation-anweisungsformen-erweitern](../done/slice-simulation-anweisungsformen-erweitern.md) („die Lücke der Austragung bei `SelectExecution`, `ChangeExecution` und `MergeExecution` ist im Beleg benannt und kein Befund“); Register `BEO-TDS/austragung-aus-der-registrierung-ohne-test-bei-select-change-merge`.

## 8. Sub-Area-Prüfungen und Modus-Begründung

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register (`../observations/BEO-TDS/`) gegen die Verzeichnisliste gelesen; Treffer: `austragung-aus-der-registrierung-ohne-test-bei-select-change-merge` (offen), `zeile-oder-zweig-ohne-unterscheidenden-test` (über der Schwelle). Zähler-Stände stehen in den Dateien des Registers, nicht hier.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
