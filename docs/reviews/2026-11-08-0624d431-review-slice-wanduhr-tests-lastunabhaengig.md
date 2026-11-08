# Review-Report: slice-wanduhr-tests-lastunabhaengig — 2026-11-08

**Review-Art:** Code (gegen Plan, Entscheidungen und Hard Rules; nicht gegen die DoD, das ist die Frage des Verifiers).

**Gegenstand:** Diff `9e97143c..0624d431` im Zweig `worktree-agent-ace4075b0828e3268` (Commits `f5b19e26` Slice angelegt, `92d77267` reiner `slice-mv`, `dfcc1ec1`, `9168a366`, `cced9afb` Testcode, `0624d431` Slice-Links und Roadmap; 5 Dateien, +225/−35). Merge-Probe `git merge-tree --write-tree HEAD main`: sauber, Exit 0. Nur Tests; kein Produktcode, keine Spezifikation, keine Nutzer-Doku.

**Skill:** `.harness/skills/reviewer.md` @ `0624d431` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-08

**Eingangs-Kontext:**

- Slice `slice-wanduhr-tests-lastunabhaengig` (Plan §1, §3, §6, DoD)
- [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität); `SPEC-008` (`LOGIN_TIMEOUT`), `SPEC-033`, [SPEC-044](../../spec/spezifikation.md#spec-044--geordnetes-beenden), `SPEC-047` (Fristen des Listeners)
- Register `BEO-TDS/wanduhr-tests-flackern-unter-last`
- `AGENTS.md` §3.1 bis §3.8

## Selbst gefahren

Messungen unter natürlicher Last (Lastmittel 7 bis 12), keine künstliche Last. Rig: Image der Stage `deps`, Quellen einer Kopie gemountet; Kopien je Mutation im Scratchpad `wanduhr-rev2`.

| Lauf | Ergebnis |
|---|---|
| `make gates FORCE=1` | Exit 0 in 37 s; die Stage `test` kam aus dem Docker-Cache (derselbe Eingang wie der Lauf des Implementers), also kein frischer Gradle-Lauf |
| `make flaeche`, `make docs-check` | Teil von `make gates` grün; `make docs-check` nach Anlage dieses Reports siehe Commit-Nachricht |
| Baseline `TableStoreTest` (Rig, 3 Läufe) | grün; `gleichzeitiger Zugriff verliert nichts` 1,06 s (XML) |
| Baseline `ListenerAbortTest` (Rig, 3 Läufe) | 5 von 5 grün in jedem Lauf; 45 s (mit Übersetzung), 8 s, 8 s |
| `make clean-build`, `make e2e`, `make matrix-zitate`, `make spec-abdeckung` | nicht gefahren (Sensor-Zuschnitt des Auftrags) |

**Mutationen (je 3 Läufe, Klasse allein)**

| Mutation | Ergebnis | Rot-Zeit |
|---|---|---|
| M1 `@Synchronized` von `catalog()` und `replace` entfernt | `gleichzeitiger Zugriff…` rot 3/3 | 8 s |
| M1b nur `catalog()` ohne `@Synchronized` (nur der Leser kann es erkennen) | rot 3/3 | 7 s |
| M1c nur `replace` ohne `@Synchronized` | rot 3/3 (Zeile 161) | 13 bis 17 s |
| M2 `structureLock.withLock` → `run` in `SimulationBackend` | rot 3/3 (Zeile 164, Endzustand: Spalte verloren) | 15 bis 17 s |
| A1 `expireGrace()` entfernt (`ListenerAbortTest`) | 4 von 5 Tests rot, 3/3 | 66 bis 68 s (erster Lauf 146 s mit Übersetzung) |
| A2 `delay(times.cancelTimeout)` entfernt | 3 von 5 rot (u. a. beide Zusage-Tests), 3/3 | 47 s |
| H2 Treiber ignoriert `LOGIN_TIMEOUT` (feste 15 s in `ConnectionWiring`), `HandshakeWiringTest` | **grün 3/3 (kein Test rot)** | — |
| H1 Warten auf das erste Byte ignoriert `LOGIN_TIMEOUT` (feste 15 s) | **grün 3/3** | — |
| H3 wie H2, aber `ListenerSupport.kt` und `ListenerAbortTest.kt` auf dem Stand vor dem Slice | `Die Frist des Treibers traegt den konfigurierten Wert` rot 3/3 (Zeile 213) | 14 s |

Nicht gefahren, nur gedacht: „Leser liest veraltete Kopie“ und „Schreiber verliert einen von zwei Einträgen“ als Quelltext-Mutation. Der Leser prüft den Inhalt von `catalog()` nicht, nur dass der Aufruf nicht wirft; das gilt vor und nach dem Slice gleich.

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| F-1 | MEDIUM | Die Schutzgrenze von `eventually` ist von höchstens rund 5 s (1000 Abfragen à 5 ms) auf 60 s Uhr gestiegen. Zwei Tests nutzen die alte Grenze als Beleg, dass der konfigurierte Wert `LOGIN_TIMEOUT=1` gilt und nicht der Standard 15 s: mit festen 15 s im Treiber (H2) und beim Warten auf das erste Byte (H1) bleiben beide grün, vor dem Slice war H2 rot. Failure-Szenario: die Konfiguration von `LOGIN_TIMEOUT` geht verloren, und kein Test merkt es. | `SPEC-008`, [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), `AGENTS.md` §3.8 | `src/test/kotlin/io/github/pt9912/tdswire/transport/listener/ListenerSupport.kt` · `private const val HANG_GUARD_NANOS = 60_000_000_000L`; betroffen `src/test/kotlin/io/github/pt9912/tdswire/runtime/HandshakeWiringTest.kt` · `Die Frist des Treibers traegt den konfigurierten Wert` und `Die Frist des Wartens auf das erste Byte traegt den konfigurierten Wert` | ja: Mutation H1/H2 (`15.seconds` statt `setup.login.timeoutSeconds.seconds` in `ConnectionWiring.kt`) färbt `HandshakeWiringTest` nicht rot | Gemeinsamer Helfer verlängert die implizite Zeitaussage anderer Tests |
| F-2 | MEDIUM | Zwei KDocs sagen weiter „Wartezeit von `eventually` (fünf Sekunden)“ und „Die Frist läuft in Echtzeit“; die Aussage stimmt nach dem Slice nicht mehr (60 s). Die Stelle steht nicht in der Plan-Tabelle §3, und Slice §6 nennt als Folge der 60 s nur die längere Zeit bis zum Rot, nicht die geänderte Aussagekraft von rund 88 Aufrufen in `src/test`. | `AGENTS.md` §3.8, Skill „Fortschreibung lässt die zweite Fundstelle stehen“ | `src/test/kotlin/io/github/pt9912/tdswire/runtime/HandshakeWiringTest.kt` · Wartezeit von eventually (fünf Sekunden) zurück (zwei Stellen); Slice §1/§6 | ja: Grep `fünf Sekunden` in `src/test` | Fortschreibung lässt zweite Fundstelle stehen |
| F-3 | MEDIUM | Der KDoc von `gleichzeitiger Zugriff verliert nichts` und DoD-Punkt 1 sagen „jeder Leser ruft `catalog()` genau einmal je Schritt der Schreiber“. Gebaut ist ein gemeinsamer Semaphore: je Anweisung gibt der Schreiber `READERS` Marken frei, die beiden Leser teilen sie nach Zufall (ein Leser kann beide nehmen, der andere keine). Die Aussage „je Leser“ ist breiter als der Bau; gemessen ist nur, dass die vier Mutationen M1, M1b, M1c, M2 je 3 von 3 rot färben. | `AGENTS.md` §3.8 | `testserver/test/kotlin/io/github/pt9912/tdswire/testserver/TableStoreTest.kt` · „jeder Leser ruft catalog() genau einmal je Schritt der Schreiber“; Slice §2 Punkt 1 | ja: Lesen von `readCatalog`/`writeStep` | Aussage breiter als der Bau |
| F-4 | LOW | Das Ziel des Slice sagt, beide Tests messen „ohne Abhängigkeit von der Last des Hosts“. Gebaut sind Warten auf Ereignis und eine Uhr-Schutzgrenze von 60 s; unter ausreichend hoher Last reißt auch sie. Die Einschränkung („begründet, nicht gemessen“) steht in §6, nicht im Satz des Ziels. | `AGENTS.md` §3.8 | Slice · `messen ihre Zusage ohne Abhängigkeit von der Last des Hosts` | nein | Zusage ohne Bedingung im selben Satz |
| F-5 | INFO | Plan-Tabelle §3 nennt `docs/plan/spec-abdeckung.md` („erzeugt“); der Diff berührt sie nicht (`make spec-abdeckung` lag nicht im Sensor-Zuschnitt). Die Zeile der DoD „`make spec-abdeckung` einmal gelaufen“ ist offen; Planner/Verifier entscheiden. | Maintainability | Slice §3 · `docs/plan/spec-abdeckung.md` | nein | Plan-Tabelle nennt unberührte Datei |
| F-6 | INFO | Zeit bis zum Rot (Slice §6, dritter Punkt): bei A1 (4 Tests rot) 66 bis 68 s, bei A2 47 s je Klassenlauf; ein hängender Test kostet bis zu 60 s statt rund 3 bis 5 s. In einer Klasse mit n hängenden Tests wächst es mit n. | [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) | `ListenerAbortTest.kt` · `private val hangGuard = 60_000L` | ja: A1/A2 | — |
| F-7 | INFO | Der Flake von `ListenerAbortTest` ist im Slice als „begründet, nicht gemessen“ geführt (§6); das Register ist unverändert. Das stimmt mit dem Beleg überein; keine Aussage „behoben“ im Diff gefunden (Grep `behoben` in Slice und Diff: kein Treffer). | `AGENTS.md` §3.8 | Slice §6 · `begründet, nicht gemessen` | nein | — |

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `ManualDelayer.awaitPending` (Lost-Wakeup, Signalisierung vor dem Warten) | geprüft, ohne Befund: `waiting += entry` steht vor `changes.update`, `StateFlow.first` wertet den aktuellen Wert zuerst aus und sieht jede spätere Änderung; `pending` filtert nach `isActive`, deshalb genügt `releaseAll` ohne eigene Signalisierung für „nicht mehr wartend“ (das Ziel ist nur „wartet allein auf X“, das ein neuer Eintrag signalisiert). Gelesen und mit 3 Läufen belegt (5 Tests × 3 grün), kein Hänger; ein Lost-Wakeup-Test ist nicht gebaut |
| `TableStoreTest` Zusage „gleichzeitiger Zugriff verliert nichts“ | geprüft: Interleaving ist durch Marken nicht erzwungen, sondern zufallsabhängig; gemessen rot 3/3 für M1, M1b (nur Leser), M1c, M2; Schreiber-Seite (Startlatch, 4×150) unverändert |
| `ListenerAbortTest` Zusage Abbruch nach der Frist und `CANCEL_TIMEOUT` | geprüft: A1 und A2 färben die Klasse rot 3/3, Erwartungen unverändert |
| Schutzgrenzen anderer Listener-Klassen (`shortTimeout` in `ListenerShutdownTest` usw.) | geprüft, ohne Befund: im Diff unberührt, wie in Slice §1 zugesagt |
| Hard Rules `AGENTS.md` §3.1, §3.2, §3.6 | geprüft, ohne Befund: kein `@Suppress`, keine Änderung an Gate-Dateien, Läufe über `make`/Docker |
| Kommentare (§3.7) im Diff | geprüft, ohne Befund: Klassen Zusage/Kopplung/Grenze, keine verworfene Alternative |
| Haken, Roadmap, `docs/user/`, `spec/` | geprüft, ohne Befund: kein `- [x]` gesetzt; Roadmap-Zeile nennt Zustand und Anker; `docs/user/` und `spec/` unberührt (Slice sagt es so); Register unverändert (Planner) |
| Plan-Tabelle §3 gegen Diff | drei Testdateien übereinstimmend; Roadmap-Zeile im Plan nicht genannt (Zustandsfeld, kein Befund); F-5 |
| Nachbesserung ohne zweiten Review | entfällt (erster Review) |

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 3 |
| LOW | 1 |
| INFO | 3 |

**Finding-Klassen dieses Laufs:** Gemeinsamer Helfer verlängert die implizite Zeitaussage anderer Tests · Fortschreibung lässt zweite Fundstelle stehen · Aussage breiter als der Bau · Zusage ohne Bedingung im selben Satz · Plan-Tabelle nennt unberührte Datei.

## Verdikt

**Merge-blockierend:** ja, wegen F-1 (messbarer Verlust an Beleg in `HandshakeWiringTest`, Mutation überlebt) und F-2/F-3 (Aussagen hinter dem Bau). Die beiden Zielklassen selbst tragen ihre Zusagen: alle Mutationen von `TableStoreTest` und `ListenerAbortTest` färben rot. Gates grün.

**Übergabe:** Findings an den Implementer; DoD-Zeile `spec-abdeckung` (F-5) an Planner/Verifier. Dieser Report ersetzt keine Verifikation.
