# Mutationsbeleg: Austragung der Ausführungen aus der Registrierung des Simulations-Backends (2026-11-22)

Messprotokoll der Mutationen zu `SimulationBackendRegistrationTest` im Slice [slice-simulation-austragung-der-ausfuehrungen-testen](../plan/planning/in-progress/slice-simulation-austragung-der-ausfuehrungen-testen.md) ([TDS-FA-07](../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [SPEC-029](../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung)). Es sagt, welche Änderung am geprüften Code welchen Test rot machte; es sagt nicht, ob eine Anforderung belegt ist ([AGENTS.md](../../AGENTS.md) §5 Regel 7). Es schließt die Lücke, die der [Mutationsbeleg zu den Anweisungsformen](mutationen-anweisungsformen-2026-11-21.md) (Lesarten und Lücken, M22 bis M25) für `SelectExecution`, `ChangeExecution` und `MergeExecution` benennt.

**Erzeuger.** Das Setz-Skript der Mutationen liegt **nicht im Repo**: je Mutation kopierte ein Wegwerf-Skript den Arbeitsbaum (ohne `.git`, `docs`, `.harness`) in ein Wegwerf-Verzeichnis, ersetzte dort eine Zeichenkette der Quelle (alt, neu, genau ein Treffer) und ersetzte im `Dockerfile` des Verzeichnisses die letzte Zeile der Stage `test` (`gradle … test integratorTest`) durch `gradle … :test --tests '<Klasse>'`; danach lief `docker build --target test`. Der Arbeitsbaum selbst blieb unverändert. Die Spalte „Mutation“ nennt Ort und Änderung; ein Verifier wiederholt sie von Hand. Einzelläufe, ein Host, Last nicht erhoben; je drei Läufe gleichzeitig; ein Lauf dauerte 106 bis 184 s (Docker-Build mit Gradle, `deps` aus dem Cache). „rot“ heißt mindestens ein Testfall des Laufs rot (Zahl der roten von allen Testfällen der Klasse); die Testfallnamen und der erste rote Schritt stammen aus der Ausgabe des Laufs.

**Ausgangslauf ohne Mutation.** `SimulationBackendRegistrationTest`: 7 Testfälle, `BUILD SUCCESSFUL`, 118 s (Docker-Build).

**Grenze.** Gemessen ist die Wirkung auf die Klasse `SimulationBackendRegistrationTest` (und im Abschnitt „Bisheriger Bestand“ auf das Paket `simulation`), nicht auf `make gates` und nicht auf die Läufe am Referenzclient (Aufgabe des Verifiers). Die Mutationen wählte der Implementer; ein Review kann weitere nennen. Der Test beobachtet die Zahl der Registrierungen des Backends (`SimulationBackend.registrations`, interne Test-Naht) je Form nach Ende, Fehler, `cancel`, Abbruch des Sammlers und `close()` ohne Sammeln; die Attrappe hält die Zeilenquelle oder den Callback an einem Tor des Tests, kein Testfall wartet auf die Wanduhr.

| Nr | Mutation (Ort) | Wirkung (gemessen) | Roter Testfall (erster roter Schritt) |
|---|---|---|---|
| N1 | `SelectExecution.kt`: `.onCompletion { onEnd(this@SelectExecution) }` am Strom entfernt (`close()` trägt sich weiter aus) | rot, 3 von 7 | `… der Auswahl S1 endet …`; `… der Auswahl S1 mit Parameter endet …`; `… der Auswahlen S2 endet …` (alle: „nach dem Ende“, erwartet 0, war 1) |
| N2 | `SelectExecution.kt`, `close()`: `onEnd(this)` entfernt (der Strom trägt sich weiter aus) | rot, 3 von 7 | dieselben drei Testfälle (alle: „nach close() ohne Sammeln“) |
| N3 | `SelectExecution.kt`: beide Austragungen entfernt | rot, 3 von 7 | dieselben drei Testfälle (alle: „nach dem Ende“) |
| N4 | `SimulationChanges.kt`, `ChangeExecution`: `.onCompletion { onEnd(this@ChangeExecution) }` entfernt | rot, 2 von 7 | `… der Aenderung endet …` („INSERT: nach dem Ende“); `… der Strukturaenderung endet …` („CREATE TABLE: nach dem Ende“) |
| N5 | `SimulationChanges.kt`, `close()` von `ChangeExecution`: `onEnd(this)` entfernt | rot, 2 von 7 | dieselben zwei Testfälle (beide: „nach close() ohne Sammeln“) |
| N6 | `SimulationChanges.kt`: beide Austragungen von `ChangeExecution` entfernt | rot, 2 von 7 | dieselben zwei Testfälle (beide: „nach dem Ende“) |
| N7 | `SimulationMerges.kt`, `MergeExecution`: `.onCompletion { whenEnded(this@MergeExecution) }` entfernt | rot, 1 von 7 | `… des MERGE endet …` („MERGE: nach dem Ende“) |
| N8 | `SimulationMerges.kt`, `close()` von `MergeExecution`: `whenEnded(this)` entfernt | rot, 1 von 7 | `… des MERGE endet …` („nach close() ohne Sammeln“) |
| N9 | `SimulationMerges.kt`: beide Austragungen von `MergeExecution` entfernt | rot, 1 von 7 | `… des MERGE endet …` („nach dem Ende“) |
| N10 | `SimulationBackend.kt`, `start`: die Austragung der Auswahl S1 leer verdrahtet (`SelectExecution(parts, echo) { }` statt `{ running.remove(key, it) }`) | rot, 2 von 7 | `… der Auswahl S1 endet …`; `… der Auswahl S1 mit Parameter endet …` (beide: „nach dem Ende“) |
| N11 | `SimulationBackend.kt`, `execute`: die Austragung der Folge S2 leer verdrahtet (`SelectExecution(series.parts, emptyList()) { }`) | rot, 1 von 7 | `… der Auswahlen S2 endet …` („S2: nach dem Ende“) |
| N12 | `SimulationBackend.kt`, `start`: die Austragung des `MERGE` leer verdrahtet (`MergeExecution(prepared, mergeLock) { }`) | rot, 1 von 7 | `… des MERGE endet …` („nach dem Ende“) |
| N13 | `SimulationBackend.kt`, `start`: die Austragung von `INSERT`, `UPDATE`, `DELETE` leer verdrahtet (`ChangeExecution(change) { }`) | rot, 1 von 7 | `… der Aenderung endet …` („INSERT: nach dem Ende“) |
| N14 | `SimulationBackend.kt`, `structure`: die Austragung der Strukturänderung leer verdrahtet (`ChangeExecution(call) { }`) | rot, 1 von 7 | `… der Strukturaenderung endet …` („CREATE TABLE: nach dem Ende“) |
| N15 | `SimulationBackend.kt`, `execute`: Form C trägt sich ein und nie aus (`is PlanResult.Fixed -> register(…, SelectExecution(emptyList(), emptyList()) { })`) | rot, 1 von 7 | `Form C traegt sich nicht ein` („SELECT 1: ausgeführt“, erwartet 0, war 1) |
| N16 | `SelectExecution.kt`: Austragung nur bei normalem Ende des Stroms (`.onCompletion { if (it == null) onEnd(…) }`; `close()` bleibt) | rot, 3 von 7 | `… der Auswahl S1 endet …`; `… S1 mit Parameter …`; `… der Auswahlen S2 …` (alle: „nach dem Abbruch des Sammlers“) |
| N17 | `SimulationChanges.kt`: dieselbe Änderung an `ChangeExecution` | rot, 2 von 7 | `… der Aenderung endet …` („INSERT: nach dem Abbruch des Sammlers“); `… der Strukturaenderung endet …` |
| N18 | `SimulationMerges.kt`: dieselbe Änderung an `MergeExecution` | rot, 1 von 7 | `… des MERGE endet …` („nach dem Abbruch des Sammlers“) |

**Bisheriger Bestand (Gegenprobe, die Lücke war belegbar).** Die Mutationen N3, N6 und N9 (beide Austragungen je Klasse entfernt) gegen das Paket `io.github.pt9912.tdswire.simulation.*` **ohne** die Klasse `SimulationBackendRegistrationTest` (Datei im Wegwerf-Verzeichnis weggelassen): je `BUILD SUCCESSFUL`, 296 Testfälle grün, 136 s. Ohne den neuen Test hielt also kein Test des Pakets die Austragung von `SelectExecution`, `ChangeExecution` und `MergeExecution`; die Wirkung auf andere Pakete (etwa `WaitForWireTest`) ist hier nicht gefahren.

**Ergebnis der Prüfung.** Der Test fand kein Leck: auf dem unveränderten Stand sind alle sieben Testfälle grün, die Zahl der Registrierungen sinkt nach Ende, Fehler der Zeilenquelle oder des Callbacks, `cancel`, Abbruch des Sammlers und `close()` ohne Sammeln auf 0. Produktionscode ist unverändert.

**Lesarten und Lücken.**

- Ein Testfall prüft die Wege nacheinander und bricht am ersten roten Schritt ab; weitere Schritte desselben Testfalls bleiben bei einer Mutation ungesehen. Je Weg einzeln rot gesehen sind „nach dem Ende“ (N1, N3), „nach close() ohne Sammeln“ (N2, N5, N8) und „nach dem Abbruch des Sammlers“ (N16 bis N18). Die Schritte „nach dem Fehler“ und „nach dem Cancel“ waren in keiner Mutation der erste rote Schritt: bei diesen Formen endet der Strom nach einem Fehler der Quelle oder des Callbacks und nach `cancel` regulär (der Fehler ist ein Ereignis, `cancel` setzt ein Kennzeichen), der Weg ist derselbe `onCompletion` wie nach dem Ende, und eine Mutation nur an diesen Wegen wurde nicht gefunden, also nicht gefahren.
- N4 bis N6, N13 und N14: `ChangeExecution` trägt zwei Formen (Änderung, Strukturänderung); die Mutation an der Klasse färbt beide Testfälle, die an einer Verdrahtung nur den ihren.
- Form C (N15): der Test bindet nur, dass `SELECT 1` und `SET LOCK_TIMEOUT 30000` sich nicht eintragen; die übrigen Texte ohne Zeilenquelle (Katalogabfragen, Form D mit nicht angenommener Strukturänderung, `INSERT BULK`) sind nicht einzeln gefahren.
- Eine Anfrage je Sitzung: die Naht zählt alle Ausführungen unter einem Schlüssel; der Test hält nie zwei Anfragen zugleich und unterscheidet sie nicht. Die Mutation `running.remove(key)` statt `running.remove(key, it)` (Austragung nur der eigenen Ausführung) an der Verdrahtung des `MERGE` (E1) ließ alle sieben Testfälle grün (7 von 7, Einzellauf des Reviews 2026-11-22); die übrigen vier Verdrahtungen sind mit dieser Änderung nicht gefahren.

**Mutationen, die grün blieben (Review und Verifikation, nicht in der Tabelle oben).**

| Nr | Mutation (Ort) | Wirkung (gemessen) | Gefahren von |
|---|---|---|---|
| E1 | `SimulationBackend.kt`, `start`: Austragung des `MERGE` als `running.remove(key)` statt `running.remove(key, it)` | grün, 7 von 7 | Review |
| E2 | `SimulationBackend.kt`, `cancel`: Rumpf zu `Unit` (`cancel` ohne Wirkung) | grün, 7 von 7 | Review und Verifikation (je ein Lauf, Verifikation auf `fb68e9a1`) |

E2 bleibt grün, weil die Austragung für Ende, Fehler der Quelle oder des Callbacks und `cancel` durch dieselbe `onCompletion` läuft und der Strom nach der Freigabe des Tors im Test regulär endet: der Schritt „nach dem Cancel“ belegt den Zustand nach Cancel und Ende, nicht die Wirkung von `cancel` auf die Austragung. `cancel` selbst tragen `SimulationBackendWaitTest` und die Tests der Quelle; eine Mutation, die Ende, Fehler und Cancel trennt, wurde im Code nicht gefunden. Beide Lücken führt das Register unter `BEO-TDS/austragung-der-ausfuehrungen-test-grenzen-offen`.
- Nicht gemessen: ein Fehler der Zeilenquelle, der beim Öffnen (`rows` wirft) statt in `next()` auftritt; die Attrappe wirft in `next()`.
