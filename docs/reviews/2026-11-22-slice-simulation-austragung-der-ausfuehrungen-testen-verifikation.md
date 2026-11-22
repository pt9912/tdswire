# Verifikationsbericht: slice-simulation-austragung-der-ausfuehrungen-testen — 2026-11-22

**Rolle:** Verifier (Modul 11), frischer Kontext; DoD- und Entscheidungs-Konformität, Plan-vs-Code-Diff. Kein Review, kein Fix, keine DoD-Haken, kein Schließen, nichts nach außen.

**Stand:** Branch `slice-simulation-austragung-der-ausfuehrungen-testen`, End-Stand `fb68e9a1` (Nachbesserung F-1/F-2 des Reviews; danach nur dieser Bericht); Arbeitsbaum vor und nach allen Läufen sauber. Alle Läufe unten gelten für `fb68e9a1`.

**Gegenstand:** Slice [slice-simulation-austragung-der-ausfuehrungen-testen](../plan/planning/in-progress/slice-simulation-austragung-der-ausfuehrungen-testen.md); [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-07](../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität); [ADR-0027](../plan/adr/0027-abbruch-und-zuruecksetzen.md) und [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) (Bezug des Slice), [ADR-0054](../plan/adr/0054-antwort-auf-attention-in-zwei-nachrichten.md) und [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md) (nicht Bezug des Slice, auf Wunsch des Auftrags mitgeprüft); [SPEC-029](../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung); Mutationsbeleg [mutationen-austragung-der-ausfuehrungen-2026-11-22.md](../messungen/mutationen-austragung-der-ausfuehrungen-2026-11-22.md); Review [2026-11-22-slice-simulation-austragung-der-ausfuehrungen-testen.md](2026-11-22-slice-simulation-austragung-der-ausfuehrungen-testen.md) (0 HIGH, 1 MEDIUM, 1 LOW, 4 INFO; F-1 und F-2 nachgebessert).

## Urteil

**DoD erfüllt, soweit sie vor der Closure erfüllbar ist; keine Abweichung, kein Befund mit Aktion.** `make clean-build`, `make gates` (`FORCE=1`), `make docs-check` und `make e2e` sind Exit 0. Der Diff ändert keinen Produktionscode; der Test `SimulationBackendRegistrationTest` (7 Testfälle) trägt die Zusagen und ist in fünf von fünf nachgefahrenen Mutationen rot (Zahl der roten Testfälle und roter Testfall wie im Beleg) und dreimal in Folge ohne Mutation grün; die Mutation E2 des Reviews bleibt wie dokumentiert grün. Offen sind nur die Closure-Punkte (Notiz, Register, Risiko-Ausgänge, Paarungen), die der Planner trägt, und die Nachführung von drei benannten Lücken in §6 (siehe „Offene Fragen“).

## Selbst gefahrene Sensoren (End-Stand `fb68e9a1`, Docker, keine künstliche Last)

Keine fremde Messsperre vorhanden; für `make e2e` (Stufe 9 misst) legte ich `/tmp/claude-1000/-Development-tdswire/messlock` selbst an (Datei `owner`, Freigabe per `trap` nach erfolgreichem `mkdir`), danach freigegeben (Pfad nicht mehr vorhanden). `available` vor den Läufen 15,6 GiB, im e2e-Lauf tiefster Stand 10,7 GiB, nie unter 4000 MB. Läufe im Vordergrund, nacheinander.

| Sensor | Ergebnis | Dauer |
|---|---|---|
| `make clean-build` | **Exit 0** (Jar, Tests, detekt, `--no-cache`); alle Gradle-Stages `BUILD SUCCESSFUL`; 1565 Zeilen `PASSED`, keine Zeile mit `FAILED` ohne `PASSED`; `SimulationBackendRegistrationTest`: alle 7 Testfälle `PASSED` | 672 s (11 min 12 s) |
| `make gates FORCE=1` | **Exit 0**; 16 Gates, `gate-zuordnung` Selbsttest 33 Fälle, `stempel-eingaenge` ok; `test` kam größtenteils aus dem Cache des eben gelaufenen `clean-build` | 41 s |
| `make docs-check` | **Exit 0**, `1757 Datei(en) geprüft, 0 Befund(e)` (vor diesem Bericht) | 3 s |
| `make e2e` | **Exit 0**, „gruen (9 Stufen, 509 s)“; Stufen 1 bis 9: 107, 103, 60, 51, 18, 18, 37, 19, 96 s, alle grün | 509 s (Wanduhr 8 min 29 s) |

Nicht gefahren, mit Grund: `make kompatibilitaet` und `make vergleichslauf-d` entfallen, weil der Slice keinen Produktionscode ändert: `git diff main...HEAD -- src/main` ist leer (0 Zeilen), und `git log main..HEAD -- src/main` ist leer; der Diff berührt nur eine Testklasse (`SimulationBackendRegistrationTest.kt`), vier Doku-/Messdateien, die Matrix und Register/Plan. Der Referenzclient und die Instanzen des Vergleichslaufs sehen dieselben Bytes wie auf `main`; `make kompatibilitaet` läuft ohnehin als Stufen 4 bis 7 in `make e2e` (grün). Nicht gefahren auch `make spec-abdeckung` (Aufgabe des Implementers; Plausibilität unten) und `make flaeche` / `make kopplung-zahlen` einzeln (beide Teil von `make gates`, grün).

Gesamt rund 35 Minuten Wanduhr (09:11 bis rund 09:46), davon `clean-build` 11 min, `e2e` 8,5 min, Mutations- und Wiederholungsläufe rund 11 min, Lesen und Bericht der Rest. Größte Posten: `clean-build` 672 s, `e2e` 509 s (Stufe 1 107 s, Stufe 2 103 s, Stufe 9 96 s), Mutationsbatches (je drei Docker-Läufe gleichzeitig, 107 bis 112 s je Lauf).

## Zu den fünf besonderen Fragen

### (1) Jede Zusage hat einen roten Test: Stichprobe nachgefahren

Wegwerf-Verzeichnis im Scratchpad (Kopie des Baums ohne `.git`, `docs`, `.harness`; im kopierten `Dockerfile` die Zeile `test integratorTest` der Stage `test` durch `cleanTest :test --tests '*SimulationBackendRegistrationTest'` ersetzt; je Mutation genau ein Treffer der Zeichenkette per Skript geprüft); der Arbeitsbaum blieb unverändert. Einzelläufe, ein Host; je drei Läufe gleichzeitig.

| Nr (Beleg) | Mutation | Ergebnis (gesehen) | Deckt sich mit dem Beleg |
|---|---|---|---|
| N5 | `SimulationChanges.kt`, `close()` von `ChangeExecution`: `onEnd(this)` entfernt | rot, 2 von 7 (Änderung, Strukturänderung) | ja |
| N7 | `SimulationMerges.kt`: `.onCompletion { whenEnded(…) }` entfernt (der Beleg führt N7 so) | rot, 1 von 7 (`MERGE: nach dem Ende`, erwartet 0, war 1) | ja |
| N12 | `SimulationBackend.kt`: Austragung des `MERGE` leer verdrahtet | rot, 1 von 7 (`… des MERGE endet …`) | ja |
| N15 | `SimulationBackend.kt`: Form C trägt sich ein und nie aus | rot, 1 von 7 (`Form C traegt sich nicht ein`, `SELECT 1: ausgeführt`, erwartet 0, war 1) | ja |
| N16 | `SelectExecution.kt`: Austragung nur bei normalem Ende des Stroms | rot, 3 von 7 (S1, S1 mit Parameter, S2: `nach dem Abbruch des Sammlers`, erwartet 0, war 1) | ja |
| E2 (Review, nicht im Beleg) | `SimulationBackend.cancel`: Rumpf zu `Unit` | **grün**, 7 von 7 | ja, wie im Review F-3 |

Ausgang ohne Mutation: dreimal in Folge `BUILD SUCCESSFUL`, 7 von 7 `PASSED` (siehe (3)). Dazu sind die Verdrahtungen laut Plan vollständig: `SimulationBackend.kt` trägt die Austragung an fünf Stellen (S2, S1, `MERGE`, Änderung, Strukturänderung; die sechste, `WaitExecution`, ist ausdrücklich nicht Gegenstand); der Beleg führt dazu N10 bis N14, also je eine Mutation. Die Zusage „nach `cancel` sinkt die Zahl auf den Stand vor der Anfrage“ ist belegt als **Zustand nach Cancel und Ende**, nicht als Wirkung von `cancel` (E2 bleibt grün, weil der Strom nach der Freigabe des Tors regulär endet); das ist im Review F-3 benannt und im Beleg unter „Lesarten“ ehrlich als „keine natürliche Mutation“ geführt.

### (2) Die vier benannten Lücken in §6

[Slice §6](../plan/planning/in-progress/slice-simulation-austragung-der-ausfuehrungen-testen.md) führt **eine** der vier: „zwei gleichzeitige Anfragen“ (Ausgang „offen bis zur Closure“). **Nicht in §6 geführt**, aber im Beleg beziehungsweise im Review benannt:

| Lücke | Wo benannt | In §6 | Vorschlag Ausgang |
|---|---|---|---|
| zwei gleichzeitige Anfragen unter einem Schlüssel (`remove(key)` statt `remove(key, it)` bleibt grün, E1 des Reviews) | Beleg „Lesarten“, Review F-4, §6 | ja, offen | weiter offen (nicht Gegenstand: Austragung je Anfrage; Folge-Beobachtung, falls der Nutzer die Ersetzung bei gleichem Schlüssel belegt haben will) |
| `cancel`-Mutation E2 (Wirkung von `cancel` auf die Austragung nicht von diesem Test getragen) | Review F-3; im Beleg nur als „Schritte ohne eigene Mutation“, E2 selbst steht nicht im Beleg | **nein** | zu führen; Vorschlag: weiter offen, `cancel` selbst tragen `SimulationBackendWaitTest` und die Tests der Quelle; E2 gehört in den Beleg (siehe Offene Fragen) |
| Form C nur zwei Texte (`SELECT 1`, `SET LOCK_TIMEOUT 30000`; Katalogabfragen, Form D, `INSERT BULK` nicht) | Beleg, Review F-5 | **nein** | zu führen; Vorschlag: weiter offen, akzeptiert (Gegenstand sind die Ausführungen mit Registrierung; die übrigen Formen tragen sich nicht ein, gelesen) |
| Fehler der Quelle nur in `next()`, nicht beim Öffnen (`rows` wirft) | Beleg, Review F-5 | **nein** | zu führen; Vorschlag: weiter offen, akzeptiert (nicht gemessen) |

Außerdem im Beleg als Grenze, nicht im Review: der Erzeuger der Mutationen liegt nicht im Repo (Review F-6, INFO). Die zweite Zeile des §6 („Austragung wirkt nicht“) ist **entfallen** aus Sicht der Verifikation: der Test fand kein Leck (7 von 7 grün auf dem unveränderten Stand), Produktionscode unverändert.

### (3) Keine Wanduhr, kein Flackern

Gelesen: im Testcode weder `delay` noch `sleep` noch Zeitvergleich (Suche am End-Stand: kein Treffer); die Haltepunkte sind `CompletableDeferred` (`gate`, `entered`), die Reihenfolge erzwingt `entered.await()` vor dem Cancel beziehungsweise Abbruch; die 20 s in `blocking` fangen nur einen Hänger. Gemessen: die Klasse **dreimal in Folge** mit der engsten Testklasse (je ein eigener Docker-Lauf mit `cleanTest`, damit kein Cache-Treffer): F1 73 s, F2 64 s, F3 60 s, jeweils 7 von 7 `PASSED`, 0 `FAILED`. Dazu der Lauf in `make clean-build` (`--no-cache`, 7 von 7), im Beleg und im Review (sieben Läufe grün) und die vier Läufe der Mutationen (N5 bis N16 rot an den erwarteten Stellen): keine Abweichung, kein Flackern gesehen. Eine Rate ist aus acht grünen Läufen nicht belegt, nur kein Fall.

### (4) `@spec`-Tags und Abdeckung 53 auf 60

Sieben Testfälle, jeder trägt genau ein `@spec SPEC-029` (gezählt: 7 Treffer von `@spec` in der Datei). `git diff main...HEAD -- docs/plan/spec-abdeckung.md` ändert genau die Zeile `SPEC-029`: 53 auf 60 und die Klasse `SimulationBackendRegistrationTest` (einmal, alphabetisch bei den Backend-Klassen); Kopfzeile „49 von 59“ unverändert (keine Festlegung neu bestanden). 53 + 7 = 60: plausibel. Beleg am End-Stand: alle sieben Testfälle bestanden (`clean-build`), also zählen sie. `make spec-abdeckung` selbst habe ich nicht neu gefahren (Implementer-Sensor; siehe oben). Anmerkung (INFO, keine Aktion): [SPEC-029](../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung) nennt die Austragung der Registrierung nicht ausdrücklich, sie behandelt das Signalisieren des Cancel an die Backendoperation; der Slice nennt SPEC-029 im Kopf als „Abbruch, Austragung der Ausführung“. Der Tag ist damit eine Zuordnung durch den Slice, keine Aussage der Spezifikation.

### (5) Register-Stand und Plan-Tabelle §3 gegen den Diff

`git diff main...HEAD --stat`: sieben Dateien, 408 Einfügungen, 5 Löschungen.

| Datei im Diff | In Plan-Tabelle §3 |
|---|---|
| `src/test/kotlin/…/simulation/SimulationBackendRegistrationTest.kt` (neu) | ja (create) |
| `docs/messungen/mutationen-austragung-der-ausfuehrungen-2026-11-22.md` (neu) | ja (create) |
| `docs/messungen/mutationen-anweisungsformen-2026-11-21.md` | ja (update) |
| `docs/plan/planning/observations/BEO-TDS/austragung-…/state.md` | ja (update) |
| `docs/plan/spec-abdeckung.md` | ja (update; Review F-1 nachgeführt) |
| `docs/plan/planning/in-progress/slice-simulation-austragung-der-ausfuehrungen-testen.md` | ja (update) |
| `docs/reviews/2026-11-22-slice-simulation-austragung-der-ausfuehrungen-testen.md` (neu) | ja (create) |

Plan und Diff stimmen überein; die Zeile `SelectExecution.kt`, `SimulationChanges.kt`, `SimulationMerges.kt`, `SimulationBackend.kt` („update, nur falls ein Test ein Leck findet“) ist nicht ausgelöst, wie vorgesehen. Die ursprüngliche Zeile „vier Testklassen“ ist durch eine einzige neue Klasse ersetzt, mit Begründung in der Tabelle (die Zusage steht an einer Stelle). Dieser Bericht kommt hinzu. Register: `state.md` nennt Zustand (`offen — 1×`, unter der Schwelle), Träger (`in-progress/`) und Beleg als auflösbare Verweise, Formulierung „Urteil beim Verifier“ ist Zustand, keine Chronik; ein neuer Beleg unter `evidence/` ist nicht entstanden, die Beobachtung bleibt bei 1× (ihr Ausgang wird bei der Closure nachgeführt, wie die DoD sagt). `docs-check` löst alle Verweise auf.

## Entscheidungs-Konformität

| Entscheidung / Regel | Befund |
|---|---|
| [ADR-0054](../plan/adr/0054-antwort-auf-attention-in-zwei-nachrichten.md) (Antwort auf ATTENTION in zwei Nachrichten) | **Nicht berührt, nicht Bezug des Slice.** Kein Produktionscode, keine Datei des Protokollkerns im Diff; der Test beobachtet nur die Registrierung des Backends, nicht die Antwort auf ATTENTION. Kein Verstoß möglich. |
| [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md) (Backend-Vertrag und Streaming) | **Nicht berührt, nicht Bezug des Slice.** Der Backend-Vertrag (`QueryExecution`, `events`, `close()`) ist unverändert (kein `src/main` im Diff); der Test nutzt ihn nur: `execute`, `events.toList()`, `close()`, `cancel`. |
| [ADR-0027](../plan/adr/0027-abbruch-und-zuruecksetzen.md), [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) (Bezug des Slice) | **Erfüllt.** Der Test fährt Callback-Fehler und Abbruch an der Naht ohne den Vertrag zu ändern; kein ADR im Diff, kein `Accepted`-ADR berührt. |
| [SPEC-029](../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung) | **Nicht berührt** (Spec im Diff unverändert). Zuordnung des Tags siehe (4). |
| Hard Rules | §3.1 kein Host-Gradle (alle Läufe über `make` und `docker build`); §3.2 kein `@Suppress`, `noqa`, `nolint` im Diff-Code (Treffer nur im Text der Reviews); §3.5 keine ADR geändert; §3.6 `config/`, `Dockerfile`, `Makefile`, `tools/` unberührt, keine Schwelle gesenkt; §3.7 KDoc des Tests im Indikativ (F-2 behoben); §3.8 der Beleg führt Grenzen im selben Absatz wie die Zusagen. |

## DoD Punkt für Punkt

Keine Haken gesetzt; `[x]` ist Sache des Planners bei der Closure.

| DoD-Punkt (§2) | Befund |
|---|---|
| **Test je Ausführung** (`SelectExecution` S1, S2, Form C; `ChangeExecution` `INSERT`, `UPDATE`, `DELETE` auch über RPC; `MergeExecution`; Zahl sinkt nach Ende, Fehler, `cancel`, Abbruch des Sammlers, `close()`; keine Wanduhr) | **Erfüllt**, mit der Einschränkung der Lesart von `cancel` (Zustand, nicht Wirkung; F-3 des Reviews, E2 gemessen). Sieben Testfälle grün in `clean-build`; Wanduhr: keine; Form C: Registrierung bleibt 0 (zwei Texte). |
| **Mutation je Zusage** (Austragung je Ausführung entfernt, je Verdrahtung leer gesetzt, Beleg im Repo; Behebung eines Lecks) | **Erfüllt.** Beleg liegt vor (18 Mutationen N1 bis N18, Gegenprobe ohne den neuen Test: grün); sechs nachgefahren, fünf rot wie behauptet (N5, N7, N12, N15, N16), E2 grün wie dokumentiert; Leck: keines gefunden, Produktionscode unverändert. |
| `make gates` Exit 0 und Sensoren je Art | **Erfüllt.** `make gates` Exit 0, `make docs-check` Exit 0, `make clean-build` und `make e2e` Exit 0 (je einmal, Verifier); `make flaeche` und `make kopplung-zahlen` in `make gates` grün; `make spec-abdeckung` laut Implementer einmal (Zeile `SPEC-029` im Diff, plausibel). |
| Review durchgeführt, Report unter `docs/reviews/` | **Erfüllt:** liegt vor (0 HIGH, 1 MEDIUM, 1 LOW, 4 INFO; MEDIUM und LOW behoben, vier INFO ohne Aktion). |
| Closure-Notiz mit Steering-Loop-Lerneintrag | **Offen:** §7 „Wird bei der Closure gefüllt.“ |
| Beobachtungs-Register fortgeschrieben | **Teilweise:** `state.md` führt Träger und Beleg; der Ausgang wird bei der Closure nachgeführt. |
| Jedes Risiko aus §6 trägt einen Ausgang | **Offen bis zur Closure:** beide Zeilen stehen auf „offen bis zur Closure“; Vorschläge in (2): Zeile 1 (Leck) entfallen, Zeile 2 (zwei gleichzeitige Anfragen) weiter offen; drei weitere Lücken sind zu führen. |
| Drei Paarungen (Anker · Folge-Slice · Register) | **Offen bis zur Closure** (Sache des Planners). |

## Abweichungen

Keine Abweichung von Plan, DoD oder Entscheidungen. Der Slice bleibt innerhalb von drei Liefer-Punkten (Test, Beleg, Doku-Nachführung).

## Offene Fragen (knapp)

1. §6 um drei Lücken ergänzen (E2 / `cancel`-Wirkung, Form C nur zwei Texte, Fehler der Quelle nur in `next()`) und je einen Ausgang vergeben, sonst bleibt der Beleg breiter als die Risikoliste (Sache des Planners, kein Code).
2. Soll die Mutation E2 (`cancel` ohne Wirkung, Test bleibt grün) in den Mutationsbeleg aufgenommen werden? Bisher steht sie nur im Review.
3. Soll [SPEC-029](../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung) die Austragung der Registrierung nach Ende, Fehler, Cancel und `close()` benennen, oder ist der Tag eine Zuordnung durch den Slice?
