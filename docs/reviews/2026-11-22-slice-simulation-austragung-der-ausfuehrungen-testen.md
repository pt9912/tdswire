# Review-Report: slice-simulation-austragung-der-ausfuehrungen-testen — 2026-11-22

**Review-Art:** Code (Testcode, Mutationsbeleg, Register, Plan-Tabelle) gegen Plan (Slice §1 bis §6), `AGENTS.md` §3.7 und §3.8 und `harness/README.md` §Minimal agent workflow; nicht gegen die DoD (Aufgabe des Verifiers).

**Gegenstand:** Diff `git diff main...HEAD` des Zweigs `slice-simulation-austragung-der-ausfuehrungen-testen` (HEAD `c19b889c`; Commits `2ab3f12e` Testcode und Abdeckungsmatrix, `c19b889c` Mutationsbeleg, Zeiger, Register, Plan-Tabelle). Sechs Dateien: `SimulationBackendRegistrationTest.kt` (neu, 7 Testfälle), `docs/plan/spec-abdeckung.md`, `docs/messungen/mutationen-austragung-der-ausfuehrungen-2026-11-22.md` (neu), `docs/messungen/mutationen-anweisungsformen-2026-11-21.md`, `state.md` des Registereintrags, der Slice-Plan. `git diff main...HEAD -- src/main` ist leer: kein Produktionscode geändert.

**Skill:** `.harness/skills/reviewer.md` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-22

**Eingangs-Kontext:** Slice §1 bis §6; `TDS-FA-11`, `TDS-FA-07`, `TDS-QA-01` (Bezug); [ADR-0027](../plan/adr/0027-abbruch-und-zuruecksetzen.md), [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) (Bezug des Slice); `SPEC-029`; die Vorlage `die Registrierung endet mit Ablauf Cancel Abbruch und close` in `SimulationBackendWaitTest`; die Naht `SimulationBackend.registrations`; Review und Re-Review von `slice-simulation-anweisungsformen-erweitern` (letzte Findings am Modul); `AGENTS.md` (Hard Rules).

**Ausgeführte Sensoren** (Docker, Wegwerf-Kontext im Scratchpad: Kopie des Arbeitsbaums ohne `.git`, `docs`, `.harness`, im kopierten `Dockerfile` die letzte Zeile der Stage `test` auf `:test --tests '<Klasse>'` gesetzt; nichts davon im Repo; `free -m` available 10 GB zu Beginn; höchstens drei Läufe gleichzeitig; Einzelläufe, ein Host, Last nicht erhoben):

1. Ausgang ohne Mutation, `SimulationBackendRegistrationTest`: 7 Testfälle, `BUILD SUCCESSFUL`, 134 s.
2. Fünf Mutationen des Mutationsbelegs nachgefahren (N2, N4, N9, N11, N14), je ein Lauf mit der Klasse: alle rot, gleiche Zahl der roten Testfälle und gleiche erste rote Meldung wie im Beleg (siehe Tabelle).
3. V1 (alter Bestand bleibt grün): die Mutationen N3, N6 und N9 (beide Austragungen je Klasse entfernt) gegen `io.github.pt9912.tdswire.simulation.*` ohne die neue Testklasse: je `BUILD SUCCESSFUL`, 110 bis 114 s. Die Zahl 296 des Belegs ist nicht nachgezählt (die Gradle-Ausgabe nennt sie bei Erfolg nicht).
4. Zwei eigene Mutationen (E1, E2, siehe Tabelle).
5. `make docs-check` Exit 0 (1756 Dateien, 0 Befunde, 3 s).

Nicht gefahren (Auftrag und Tabelle `harness/README.md`): `make gates`, `make spec-abdeckung`, `make clean-build`, `make e2e`, `make kompatibilitaet`, `make vergleichslauf-d`. Gesamtlaufzeit des Reviews rund 30 Minuten; größte Posten: die elf Docker-Läufe (je 105 bis 135 s, drei gleichzeitig, in vier Schüben).

## Mutationen (nachgefahren)

| Nr | Mutation | Ergebnis (gesehen) | Deckt sich mit dem Beleg |
|---|---|---|---|
| (Ausgang) | keine | grün, 7 von 7 | ja |
| N2 | `SelectExecution.kt`: `onEnd(this)` in `close()` entfernt | rot, 3 von 7 (S1, S1 mit Parameter, S2), „nach close() ohne Sammeln“, erwartet 0, war 1 | ja |
| N4 | `SimulationChanges.kt`: `.onCompletion { onEnd(…) }` von `ChangeExecution` entfernt | rot, 2 von 7 (Änderung „INSERT: nach dem Ende“, Strukturänderung „CREATE TABLE: nach dem Ende“) | ja |
| N9 | `SimulationMerges.kt`: beide Austragungen von `MergeExecution` entfernt | rot, 1 von 7 („MERGE: nach dem Ende“) | ja |
| N11 | `SimulationBackend.kt`: Austragung der Folge S2 leer verdrahtet | rot, 1 von 7 („S2: nach dem Ende“) | ja |
| N14 | `SimulationBackend.kt`, `structure`: Austragung leer verdrahtet | rot, 1 von 7 („CREATE TABLE: nach dem Ende“) | ja |
| V1 (N3, N6, N9) | beide Austragungen je Klasse entfernt, Paket `simulation.*` ohne die neue Klasse | je grün | ja |
| E1 (eigen) | `SimulationBackend.kt`: `running.remove(key)` statt `running.remove(key, it)` bei `MergeExecution` | grün, 7 von 7 | der Beleg nennt sie „nicht gefahren, gelesen“; jetzt gemessen |
| E2 (eigen) | `SimulationBackend.cancel`: Rumpf zu `Unit` (cancel ohne Wirkung) | grün, 7 von 7 | nicht im Beleg |

Nicht nachgefahren: N1, N3, N5 bis N8, N10, N12, N13, N15 bis N18 (die Zeilen des Belegs sind plausibel, N16 bis N18 sind ein reiner Wiederholungsfall von N1 am Weg „Abbruch des Sammlers“, aber hier ungesehen).

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| F-1 | MEDIUM | Der Diff berührt `docs/plan/spec-abdeckung.md` (Zeile `SPEC-029`, 53 auf 60, Klasse eingefügt), die Tabelle in Slice §3 nennt die Datei nicht; die DoD verlangt `make spec-abdeckung` einmal, die Datei ist also erwartbar berührt. Der Abgleich der Tabelle gegen `git diff main...HEAD --stat`, den §3 am Ende vorsieht, ist damit nicht gelaufen oder nicht nachgeführt. | Reviewer-Skill, Prüfpunkt „Plan-Tabelle nennt die berührten Dateien nicht“ (seit slice-e2e-backend-ausserhalb-modul) | `docs/plan/planning/done/slice-simulation-austragung-der-ausfuehrungen-testen.md` · Tabelle §3, Zeile `docs/plan/spec-abdeckung.md` fehlt | ja — Abgleich der Tabelle gegen `git diff --name-only main...HEAD` (der Slice-Plan selbst und die Zeile des Belegs sind der Sonderfall der Tabelle) | Plan-Tabelle nennt berührte Datei nicht |
| F-2 | LOW | Das KDoc des S1-Testfalls nennt den abwesenden Zustand im Konjunktiv: „eine Auswahl, die sich nicht austrägt (dann bliebe je Anfrage ein Eintrag im Backend)“. Dieselbe Form steht in der Vorlage `SimulationBackendWaitTest` (dort im Re-Review nicht beanstandet); die übrigen „Wächter“-Zeilen der Klasse sind im Indikativ. | `AGENTS.md` §3.7; Reviewer-Skill, LOW „Test-KDoc nennt den abwesenden oder unwirklichen Zustand“ | `SimulationBackendRegistrationTest.kt` · „Wächter: eine Auswahl, die sich nicht austrägt (dann bliebe je Anfrage ein Eintrag im Backend)“ | nein — kein Gate | Test-KDoc im Konjunktiv über den abwesenden Zustand |
| F-3 | INFO | Die Schritte „nach dem Fehler“ und „nach dem Cancel“ sind in keiner Mutation der erste rote Schritt (der Beleg sagt das). Gemessen dazu: E2 (`cancel` ohne Wirkung) lässt alle sieben Testfälle grün, weil der Strom nach dem Freigeben des Tors regulär endet. Die Zusage „der Eintrag verschwindet nach `cancel`“ ist damit als Zustand nach Cancel und Ende belegt, nicht als Wirkung von `cancel` auf die Austragung; `cancel` selbst tragen `SimulationBackendWaitTest` und die Tests der Quelle. Fehler der Quelle, des Callbacks und das Ende laufen im Code durch dieselbe `onCompletion`, es gibt keine natürliche Mutation, die sie trennt; akzeptabel, kein Befund. | Maintainability; `AGENTS.md` §3.8 | `mutationen-austragung-der-ausfuehrungen-2026-11-22.md` · „Die Schritte „nach dem Fehler“ und „nach dem Cancel“ waren in keiner Mutation der erste rote Schritt“ | ja — E2 wiederholbar mit `:test --tests '*SimulationBackendRegistrationTest'` | Schritt eines Testfalls ohne eigene Mutation |
| F-4 | INFO | Zwei gleichzeitige Anfragen unter einem Schlüssel sind nicht getestet; der Beleg und §6 (offen bis zur Closure) nennen es. E1 (`remove(key)` statt `remove(key, it)`) bleibt grün, wie der Beleg „gelesen, nicht gemessen“ sagt; jetzt gemessen. Akzeptabel für diesen Slice (Gegenstand ist die Austragung je Anfrage, nicht die Ersetzung bei gleichem Schlüssel); §6 braucht in der Closure einen Ausgang. | Slice §6; `AGENTS.md` §3.8 | `mutationen-austragung-der-ausfuehrungen-2026-11-22.md` · „Die Mutation `running.remove(key)` statt `running.remove(key, it)` … ist nicht gefahren“ | ja — E1 | Austragung der eigenen Ausführung ohne Test |
| F-5 | INFO | Form C ist an zwei Texten (`SELECT 1`, `SET LOCK_TIMEOUT 30000`) belegt, der Fehler der Quelle nur in `next()`, nicht beim Öffnen (`rows` wirft). Beide Grenzen stehen im Beleg; die übrigen Formen ohne Zeilenquelle (`Catalog`, `Unchanged`, `Bulk`) laufen in `SimulationBackend.execute` über `events(...)` ohne Registrierung. Akzeptabel, kein Befund. | Maintainability | `SimulationBackendRegistrationTest.kt` · „for (sql in listOf("SELECT 1", "SET LOCK_TIMEOUT 30000"))“ | nein | Auswahl der Eingaben schmaler als die Form |
| F-6 | INFO | Der Erzeuger der Mutationen liegt nicht im Repo; der Beleg sagt das, nennt je Mutation Ort und Änderung (eine Zeichenkette, ein Treffer) und führt als Zahlen nur rote Testfälle je Klasse und Laufzeiten. Die fünf nachgefahrenen Zeilen und V1 stimmen mit dem Beleg überein; Laufzeiten dieses Reviews 105 bis 135 s (Beleg 106 bis 184 s). Der Satz „296 Testfälle“ und die elf nicht nachgefahrenen Zeilen (oben) sind hier nicht belegt, aber ebenfalls als Einzelmessung gekennzeichnet. Keine Aktion. | `AGENTS.md` §3.8; Reviewer-Skill, MEDIUM „Messzahl aus einem Erzeuger, der nicht im Repo liegt“ (nicht ausgelöst: Zahlen sind Zählungen, Herkunft und Grenze stehen im Beleg) | `mutationen-austragung-der-ausfuehrungen-2026-11-22.md` · „Das Setz-Skript der Mutationen liegt **nicht im Repo**“ | nein | Messprotokoll mit Wegwerf-Erzeuger |

## Prüfung gegen die Schwerpunkte

**(1) Trägt jeder Testfall die Zusage?** Ja. `endsRegistration` prüft je Form nacheinander: vor der Anfrage 0, nach `execute` 1, nach dem Sammeln 0; je Fehlerstelle 0; je gehaltener Anfrage 1 und nach Cancel mit Freigabe des Tors 0, nach Abbruch des Sammlers 0; ausgeführt und nicht gesammelt 1, nach `close()` 0. Die Formen sind S1, S1 mit Parameter, S2, `INSERT`, `UPDATE`, `DELETE` je als Text und über RPC, Strukturänderung, `MERGE` (Fehler der Zeilenquelle und des Callbacks) und Form C (nicht eingetragen). Das deckt die Aufzählung von DoD-Punkt 1 (Prüfung gegen die DoD ist Sache des Verifiers). Wanduhr: kein `delay`, kein `sleep`; Tore sind `CompletableDeferred`, der Lauf steht auf `runBlocking` mit einem Dispatcher, `launch` startet erst beim `await` des Tests, die Reihenfolge ist durch `entered.await()` festgelegt; die 20 s von `blocking` sind nur der Fang eines Hängers (bei einer nicht erreichten Haltestelle rot nach 20 s statt grün). Flakiness: kein Befund; sieben Läufe der Klasse in diesem Review (Ausgang, fünf Mutationen, E1, E2) lieferten die erwarteten Ergebnisse ohne Abweichung. Naht: `registrations` ist `internal`, das KDoc nennt die Kopplung; unverändert.

**(2) Mutationen:** siehe Tabelle; fünf der 18 Mutationen und V1 nachgefahren, alle Aussagen des Belegs dazu bestätigt.

**(3) Benannte Lücken:** F-3 bis F-5, akzeptabel.

**(4) `@spec`-Tags und Abdeckung:** jeder der sieben Testfälle trägt `@spec SPEC-029` (Tag der Austragung/Abbruch-Zusage laut Slice-Kopf); `spec-abdeckung.md` ändert genau die Zeile `SPEC-029` von 53 auf 60 (sieben Testfälle, plausibel) und nimmt die Klasse alphabetisch auf; die Kopfzeile „49 von 59“ bleibt, weil keine Festlegung neu bestanden ist. Den Lauf der Matrix habe ich nicht wiederholt (nicht Auftrag). Die Datei ist in der Plan-Tabelle nicht genannt (F-1).

**(5) KDoc, Beleg, Register:** Test-KDoc im Indikativ bis auf F-2. Der Beleg belegt seine Aussagen mit der gemessenen Wirkung (N-Zeilen nachgefahren); die Grenzen stehen im selben Absatz wie die Zusagen (§3.8). `state.md` nennt Zustand (`offen — 1×`), Träger (`in-progress/`) und Beleg als auflösbare Verweise, keine Chronik; die Wendung „Urteil beim Verifier“ ist ein Zustand, kein Ablauf. Die Zeile in `mutationen-anweisungsformen-2026-11-21.md` ersetzt „Lücke benannt, nicht geschlossen“ durch einen Verweis im Indikativ. Die Haken der DoD sind im Diff nicht gesetzt.

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `src/main/` (Diff leer; `git diff main...HEAD -- src/main`) | geprüft, ohne Befund |
| `src/test/kotlin/io/github/pt9912/tdswire/simulation/SimulationBackendRegistrationTest.kt` (Zusage, Tore, Reihenfolge, Wanduhr, Suppression: kein `@Suppress`) | geprüft, ohne Befund außer F-2 |
| `docs/messungen/` (zwei Dateien) | geprüft, ohne Befund (F-6 INFO) |
| `docs/plan/spec-abdeckung.md` | geprüft, ohne Befund (Aufnahme in die Plan-Tabelle: F-1) |
| `docs/plan/planning/observations/BEO-TDS/austragung-aus-der-registrierung-ohne-test-bei-select-change-merge/` | geprüft, ohne Befund (Verweise lösen auf, `make docs-check` Exit 0) |
| Fortschreibung zweiter Fundstellen („Austragung ohne Test“ in `docs/`, `spec/`, `harness/`, `tools/`) | geprüft, ohne Befund: weitere Treffer stehen in geschlossenen Dokumenten (`done/`, Reviews) und bleiben als Zeitdokumente |
| `docs/user/` | geprüft, ohne Befund (Testcode und Belege, keine Verhaltensänderung) |
| `spec/` (keine Änderung) und Schichtregeln (`config/detekt/detekt.yml` nicht berührt) | geprüft, ohne Befund |

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 1 |
| LOW | 1 |
| INFO | 4 |

**Finding-Klassen dieses Laufs:** Plan-Tabelle nennt berührte Datei nicht · Test-KDoc im Konjunktiv über den abwesenden Zustand · Schritt eines Testfalls ohne eigene Mutation · Austragung der eigenen Ausführung ohne Test · Auswahl der Eingaben schmaler als die Form · Messprotokoll mit Wegwerf-Erzeuger

**Empfehlung:** weiter an den Verifier, nach der Berichtigung von F-1 (reine Doku, kein Code; ein Re-Review ist dafür nicht nötig). F-2 bis F-6 verlangen keine Nachbesserung am Code; ändert der Implementer den Testcode (F-2 ist reine Kommentarkorrektur), fällt das unter die Doku-Ausnahme des Skills.
