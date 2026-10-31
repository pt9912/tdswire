# Review-Report: [slice-sim-auswahl-streaming](../plan/planning/done/slice-sim-auswahl-streaming.md) — 2026-10-31

**Review-Art:** Code (Diff des Slice gegen Plan, [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md), [ADR-0013](../plan/adr/0013-backend-grenzen.md), Spezifikation und Hard Rules). Gegen die DoD wird nicht geprüft; das ist die Frage des Verifiers.

**Gegenstand:** Merge-Commit 7c8917d („Merge slice-sim-auswahl-streaming Umsetzung“), Diff `5d95bd2..7c8917d` über `src`, `spec` und `docs/plan/planning/in-progress`: Code und Tests f65f486, Spezifikation und Plan 72a3b04, Roadmap-Zeile 1714f1b.

**Skill:** `.harness/skills/reviewer.md` im Stand des Worktrees
**Modell:** claude-sonnet-5-5 · **Datum:** 2026-10-31

**Eingangs-Kontext:**

- [slice-sim-auswahl-streaming](../plan/planning/done/slice-sim-auswahl-streaming.md) (Plan, Risiken, Rückführungen)
- [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md) (Accepted; im Diff unverändert), insbesondere Entscheidung 6 (Überführung) und 9 (Textvergleich); [ADR-0013](../plan/adr/0013-backend-grenzen.md) (Accepted; im Diff unverändert)
- [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-08](../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität)
- [SPEC-002](../../spec/spezifikation.md#spec-002--interner-query-vertrag), [SPEC-029](../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung), [SPEC-031](../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität), [SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends), [SPEC-036](../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig), SPEC-037 bis SPEC-039 (Fehlercodes-Tabelle)
- `AGENTS.md` §3.1 bis §3.8
- vorheriger Report am Modul: [2026-10-31-7aa0763-slice-sim-erkennung.md](2026-10-31-7aa0763-slice-sim-erkennung.md)

**Eigene Läufe** (im Worktree, nie zwei eigene Docker-Builds gleichzeitig; Mutationen im Quelltext des Worktrees, danach zurückgesetzt, `git status` sauber):

- `make test` auf dem unveränderten Stand: grün, Stage lag im Cache (`CACHED`), daher kein Ausgabebeleg (siehe F-5).
- Mutation M1: `events` um `.buffer(64)` ergänzt (Vorauslesen). Ergebnis: rot in `SimulationBackendSourceTest > die Quelle wird nur auf Abnahme gelesen`, `… > ein abbrechender Sammler beendet das Lesen und schliesst die Quelle` und `SimulationMemoryTest`. Grün blieben **alle acht Tests** der Suite `QueryBackendContract` an beiden Implementierungen, darunter `Generator laeuft nicht ueber die Abnahme hinaus` (F-1).
- Mutation M2 (zwei Änderungen in einem Lauf): Zahl `1` in einer Bool-Spalte als ungültig, und die Meldung der Quelle an die feste Fehlermeldung angehängt (Leck des Ausnahmetexts). Ergebnis: rot in `SimValuesTest > Zahl und Text wechseln nie die Familie` und `SimulationBackendSelectTest > Gleichheit Liste und NULL-Pruefung waehlen die Zeilen` (Bool 1) sowie in `SimulationBackendContractTest > Fehler nach Zeilen ist ein Failure-Ereignis im Strom` und drei Tests von `SimulationBackendSourceTest` (Leck); die Zuordnung je Änderung ist wegen des gemeinsamen Laufs nicht einzeln belegt. `SimulationMemoryTest` blieb in diesem frischen Lauf grün.
- Mutation M3: jede abgenommene Zeile in einer Liste behalten (`keep.add`). Der Build lief nach 23 Minuten noch ohne Ergebnis (F-5); ich konnte ihn nicht beenden (die Berechtigung dafür wurde verweigert). **Nicht abgeschlossen, kein Beleg für rot oder grün.**
- Nicht gewertet: `AuthTimingTest` und `UserListAuthAdapterTest` waren in M1 rot; sie hängen an Zeitmessung unter Last (zwei Docker-Builds anderer Läufe parallel) und nicht am Diff; ich werte sie nicht.
- `make docs-check` (436 Dateien, 0 Befunde) und `make gates` (Exit 0) nach Anlage dieses Reports.

---

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| F-1 | MEDIUM | Die Suite sagt, die Zeilenquelle laufe „nicht über die Abnahme hinaus“; der Test dazu bleibt grün, wenn der Strom mit `buffer(64)` vorausliest (M1). Ein Backend, das die Suite erbt und vorausliest, besteht den Punkt; für das Simulations-Backend tragen ihn erst die Tests der Quelle und der Heap-Test. Der Implementer meldet dieselbe Lücke; die Grenze der Suite („`runBlocking` ohne eigenen Dispatcher“) nennt das Vorauslesen nicht. | [AGENTS.md §3.8](../../AGENTS.md), Skill „Aussage breiter als ihr Beleg“ | `src/test/kotlin/io/github/pt9912/tdswire/port/QueryBackendContract.kt` · „die Zeilenquelle läuft nicht über die Abnahme hinaus“ (Kopf-KDoc) und `Generator laeuft nicht ueber die Abnahme hinaus` | ja: `.buffer(64)` in `SelectExecution.events`, `make test` | Suite-Test ohne Wirkung auf Vorauslesen |
| F-2 | LOW | Zwei Einzelfestlegungen stehen nur im Test- oder Code-KDoc und nicht in SPEC-035: die Namen im `ResultStart` sind die der Beschreibung, nicht die Schreibweise im Text (`SELECT AGE` liefert `Age`), und eine Zelle anderer Klasse als der Wert ist ungleich (`1` gegen `1L`). Beide gelten als Aussage, ohne das Wort „Setzung“ und ohne Spec-Zeile. | [AGENTS.md §3.8](../../AGENTS.md) | `src/test/kotlin/io/github/pt9912/tdswire/simulation/SimulationBackendSelectTest.kt` · „die Namen und die Nullbarkeit sind die der Beschreibung“; `src/main/kotlin/io/github/pt9912/tdswire/simulation/SimValues.kt` · „Eine Zelle anderer Klasse als der Wert ist ungleich“ | nein: kein Gate liest die Zuordnung Festlegung zu Spec | Setzung nur im Test-KDoc |
| F-3 | LOW | „ISO-Format“ ist in SPEC-035 und im Slice das Maß der Zeitüberführung; der Parser ist `LocalTime.parse`, `LocalDate.parse`, `LocalDateTime.parse` ohne eigene Formatprüfung. Was er darüber hinaus annimmt (Sekunden optional: `'12:30'`; Bruchteile bis Nanosekunden; nach JDK-Dokumentation, nicht ausgeführt, auch ein Datum mit Vorzeichen und mehr als vier Stellen im Jahr) ist weder in der Spezifikation benannt noch in `SimValuesTest` fixiert (dort nur `'12:30:15'` und `'2026-10-30T12:30:00'`). | [AGENTS.md §3.8](../../AGENTS.md), [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md) Entscheidung 6 („ISO-Formate“) | `src/main/kotlin/io/github/pt9912/tdswire/simulation/SimValues.kt` · `Converted.Value(LocalTime.parse(value))` | ja: Probe-Test mit `'12:30'` | Formatraum des Parsers ungeprüft |
| F-4 | LOW | Der Heap-Test hat keine Zeitgrenze. Bei der Mutation, die jede Zeile behält (M3), lief `make test` nach 23 Minuten ohne Ausgang (kein rot, kein Abschluss); ein Rückfall wäre als hängender Lauf sichtbar, nicht als roter Test. Dauer und Ursache (Speicherdruck der Test-JVM) sind nicht abgeschlossen gemessen. | Maintainability | `src/test/kotlin/io/github/pt9912/tdswire/simulation/SimulationMemoryTest.kt` · `fun \`ein Ergebnis ueber dem Heap wird an einer wartenden Senke Zeile fuer Zeile gelesen\`() = runBlocking {` | ja: M3 wiederholen und das Ende abwarten | Speichertest ohne Zeitgrenze |
| F-5 | INFO | Der Heap-Beleg trägt „das Ergebnis wird nicht vollständig gehalten“ (1 200 000 × 128 Zeichen; Untergrenze 153,6 MB gegen 128 MiB = 134,2 MB, also 14 % darüber; Zähler mit Unter- und Obergrenze, in M1 rot). Er trägt nicht „Speicher unabhängig von der Zeilenzahl“: Es gibt einen Messpunkt, und ein Backend, das unter 87 % der Zeilen behielte, bestünde. Der Wortlaut in Spec, Slice und KDoc bleibt innerhalb dieses Maßes („nicht vollständig gehalten“, „für diesen Testlauf mit dieser Senke“). Die Stage `test` lag beim unveränderten Stand im Cache; frische Läufe dieses Reviews (M2 und die Läufe von M1) ließen den Test grün bzw. bei M1 erwartungsgemäß rot (`BEO-TDS/test-stage-cache-verdeckt-wiederholung`). | [AGENTS.md §3.8](../../AGENTS.md) | `src/test/kotlin/io/github/pt9912/tdswire/simulation/SimulationMemoryTest.kt` · „das Backend hält das Ergebnis nicht“ | ja | Grenze des Speicherbelegs |
| F-6 | INFO | Die Haken `sql` und `seen` der Suite lockern keine Port-Pflicht: alle acht Tests laufen an beiden Backends mit derselben Prüfung; `seen` ändert nur die erwartete Gestalt eines Fehlers. Bei der Simulation ist `seen` konstant, die zwei Fehlertests prüfen dort „es kommt ein `Failure` mit festem Code und fester Meldung“, nicht, dass Code und Meldung eines Skripts den Strom erreichen. Ein Backend mit durchgereichtem Code besteht beide Tests über die Voreinstellung. Der Plan benennt die Änderung (§6) und nimmt sie als Ausgang „offen bis zur Closure“ auf. | Maintainability | `src/test/kotlin/io/github/pt9912/tdswire/port/QueryBackendContract.kt` · `protected open fun seen(scripted: QueryEvent.Failure)` | ja | Suite-Haken |
| F-7 | INFO | `SelectExecution` steht in `running`, bis der Strom endet oder `close()` gerufen wird. Eine Ausführung, die weder gesammelt noch geschlossen wird, bleibt im Map; derselbe Schlüssel überschreibt eine laufende Ausführung. Beides liegt im Vertrag des Aufrufers (`QueryExecution.close`), das KDoc nennt „genau einen Sammler“, aber nicht die Pflicht zu `close()`. | Maintainability | `src/main/kotlin/io/github/pt9912/tdswire/simulation/SimulationBackend.kt` · `running[key] = execution` | nein | Registrierung ohne Abmeldepflicht im KDoc |

## Antworten auf die Fragen des Implementers

1. **Haken `sql` und `seen` in der Suite.** Zulässig. Es wird nichts aufgeweicht: Voreinstellungen erhalten das Verhalten des Test-Backends, die Tests laufen unverändert an beiden Klassen. Die Grenze der Wirkung steht in F-6. Eine Fabrik je Backend hätte dieselbe Prüfschärfe bei mehr Quelltext; sie ist keine Voraussetzung.
2. **Setzungen in SPEC-035.** Gemessen an [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md) Entscheidung 6 („gleiche Typfamilie, ISO-Formate, keine Rundung, Parameter nur verlustfrei erweitert“) gehen Zahl „ohne Rest“ (`1.0`, `3.500`: keine Rundung), nicht übergebener Parameter und unbeachtete Richtung nicht über die ADR hinaus. **Bool aus `0` und `1`** verlässt streng die Typfamilie (Zahl in Bool) und ist von M2 der ADR nur an der Zahl `2` gemessen; die Spec kennzeichnet es als „Setzung, Voreinstellung, nicht gemessen“, was 3.8 genügt, und der Test fixiert beide Werte (M2 rot). Die Kennzeichnung der übrigen Einzelfestlegungen im Absatz von SPEC-035 ist erfüllt; offen sind nur F-2 und F-3.
3. **Heap-Test.** Siehe F-4 und F-5.
4. **Mutation Vorauslesen.** Lücke der Suite, kein Fehler dieses Backends: F-1.
5. **Zusage von `cancel` und `close` im KDoc.** Nicht zu breit: Das KDoc sagt „vor dem Lesen der nächsten Zeile“ und nennt das hängende `next()` ausdrücklich als Grenze. Der Code liefert eine Zeile, deren `next()` bei `stop()` schon läuft, noch aus; „vor der nächsten Zeile“ ist damit wörtlich wahr, „stoppt sofort“ wäre es nicht, und das behauptet niemand.
6. **Fehlermeldung und Fangregeln.** Die Meldung ist ein Literal (`Zeilenquelle fehlgeschlagen`); das Leck wurde mutiert und gerötet (M2). `guarded` wirft `CancellationException` und `Error` weiter; `Error und Abbruch der Quelle werden nicht gefangen` testet das. Kein `@Suppress` im Diff (`make detekt` siehe Ende).
7. **Nicht mutiert beim Implementer.** Leck der Ausnahmemeldung und Bool `1`: beide mutiert, beide rot (M2).

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `src/main/kotlin/…/simulation/SimulationBackend.kt` (Lesen auf Abnahme, genau einmal schließen, Fehler vor/nach `ResultStart`, Cancel) | geprüft; Befund nur F-7 |
| `src/main/kotlin/…/simulation/SimValues.kt`, `SimConditions.kt` (Überführung, Gleichheit, Dreiwertigkeit, kein Exponent im Scanner, daher keine Zahl mit Riesenexponent) | geprüft; Befund nur F-2, F-3 |
| `src/main/kotlin/…/simulation/StatementPlanner.kt` (zwei Codes in `SimCodes`) | geprüft, ohne Befund |
| `src/test/kotlin/…/simulation/` (Select-, Source-, Values-, Contract-, Memory-Test, Support) | geprüft; Befund F-1, F-4, F-5 |
| `src/test/kotlin/…/runtime/SimulationCodesTest.kt` | geprüft, ohne Befund |
| `spec/spezifikation.md` SPEC-035 (Erweiterung der Festlegungen, Historie) | geprüft; die untere Schicht präzisiert, erweitert nicht (Schritte 4 bis 7 von `TDS-FA-11.a` tragen alle Aussagen); Befund F-2, F-3 |
| `docs/plan/planning/in-progress/` (Slice, Roadmap) | geprüft, ohne Befund; Zustandsfeld der Roadmap nennt Zustand und Anker |
| `docs/plan/adr/` ([ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md), [ADR-0013](../plan/adr/0013-backend-grenzen.md)) | im Diff unverändert, kein Verstoß gegen §3.5 |
| `config/detekt/detekt.yml`, `build.gradle.kts`, `Dockerfile` | nicht im Diff, ohne Befund |
| Host statt Docker, `@Suppress`, Importgrenze `simulation` | im Diff nicht gefunden |

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 1 |
| LOW | 3 |
| INFO | 3 |

**Finding-Klassen dieses Laufs:** Suite-Test ohne Wirkung auf Vorauslesen · Setzung nur im Test-KDoc · Formatraum des Parsers ungeprüft · Speichertest ohne Zeitgrenze · Grenze des Speicherbelegs · Suite-Haken · Registrierung ohne Abmeldepflicht im KDoc

## Verdikt

**Merge-blockierend:** nein. F-1 ist eine Lücke der gemeinsamen Suite und kein Fehler des Backends (M1 rötet drei andere Tests); sie braucht in der Closure-Notiz den Satz, was die Suite nicht fängt, oder eine Annahme. Die Mutation M3 ist offen und gehört in den Lauf des Verifiers oder der Closure.

**Übergabe:** Findings gehen an den Implementer; die Finding-Klassen gehen in die Slice-Closure §7 und von dort in den Zähler. Dieser Report ersetzt keine Verifikation.
