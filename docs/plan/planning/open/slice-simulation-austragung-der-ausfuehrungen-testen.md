# Slice slice-simulation-austragung-der-ausfuehrungen-testen: Austragung der Ausführungen aus der Registrierung des Simulations-Backends je Form testen

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** ohne Welle (keine Closure-Bedingung außerhalb der DoD dieses Slice; Folge-Slice der Closure von [slice-simulation-anweisungsformen-erweitern](../in-progress/slice-simulation-anweisungsformen-erweitern.md): dort trägt ein Test die Austragung der `WAITFOR`-Ausführung, für die übrigen Ausführungen fehlt er).

**Bezug:** [TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe) (Ressourcenfreigabe), [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) (Abbruch), [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md)

**Berührte Spec-Stellen:** [SPEC-029](../../../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung) (Abbruch, Austragung der Ausführung) · [SPEC-035](../../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) (Verhalten des Backends), nur wenn der Test ein Leck findet und die Beschreibung ändert.

**Verantwortlich:** —

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

- [ ] **Test je Ausführung:** `SelectExecution` (S1, S2, Form C), `ChangeExecution` (`INSERT`, `UPDATE`, `DELETE`, auch über RPC) und `MergeExecution`: die Zahl der Registrierungen sinkt nach Ende, Fehler der Zeilenquelle oder des Callbacks, `cancel`, Abbruch des Sammlers und `close()` ohne Sammeln auf den Stand vor der Anfrage; der Test wartet keine Wanduhr ab.
- [ ] **Mutation je Zusage:** die Austragung je Ausführung entfernt und je Verdrahtung in `SimulationBackend.kt` leer gesetzt macht je einen Test rot (Mutationsbeleg als Datei im Repo); findet ein Test ein Leck, ist die Behebung Teil dieses Slice.
- [ ] `make gates` Exit 0, dazu je Art der Änderung nach `harness/README.md` §Minimal agent workflow: engste Testklasse und Mutation je Zusage über enge Testklassen (`src/`), `make spec-abdeckung` einmal (Tests oder `@spec`-Tags), `make flaeche`, `make docs-check` (Verifier fährt `make clean-build` und `make e2e` je einmal auf dem End-Stand; Implementer und Reviewer nicht).
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor (`.harness/skills/reviewer.md`), kein Self-Review.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben: `BEO-TDS/austragung-aus-der-registrierung-ohne-test-bei-select-change-merge` (Ausgang nachführen); keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — Pfad-Kandidaten für §8; geprüft ist das Vorhandensein der Dateien am 2026-11-22, nicht ihre Eignung.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/test/kotlin/io/github/pt9912/tdswire/simulation/SimulationBackendSelectTest.kt`, `SimulationBackendWaitTest.kt` (Muster), `SimulationBackendMergeTest.kt`, `SimulationBackendChangeTest.kt` | update | Tests je Ausführung über `SimulationBackend.registrations` |
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/SelectExecution.kt`, `SimulationChanges.kt`, `SimulationMerges.kt`, `SimulationBackend.kt` | update, nur falls ein Test ein Leck findet | Behebung |
| `docs/messungen/` (Mutationsbeleg) | create | Mutation je Zusage als Datei im Repo |

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

- Die Austragung wirkt bei einer Ausführung nicht (ein Leck je Anfrage). — **Ausgang:** offen bis zur Closure; der Test entscheidet, ein Leck ist Teil dieses Slice.
- Die Naht `registrations` zählt alle Ausführungen unter einem Schlüssel, ein Test mit zwei gleichzeitigen Anfragen unterscheidet sie nicht. — **Ausgang:** offen bis zur Closure; der Test benutzt eine Anfrage je Sitzung.

## 7. Closure-Notiz

Wird bei der Closure gefüllt.

**Herkunft:** Review F-2 und Re-Review von [slice-simulation-anweisungsformen-erweitern](../in-progress/slice-simulation-anweisungsformen-erweitern.md) („die Lücke der Austragung bei `SelectExecution`, `ChangeExecution` und `MergeExecution` ist im Beleg benannt und kein Befund“); Register `BEO-TDS/austragung-aus-der-registrierung-ohne-test-bei-select-change-merge`.

## 8. Sub-Area-Prüfungen und Modus-Begründung

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register (`../observations/BEO-TDS/`) gegen die Verzeichnisliste gelesen; Treffer: `austragung-aus-der-registrierung-ohne-test-bei-select-change-merge` (offen), `zeile-oder-zweig-ohne-unterscheidenden-test` (über der Schwelle). Zähler-Stände stehen in den Dateien des Registers, nicht hier.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
