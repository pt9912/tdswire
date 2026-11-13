# Review-Report: slice-einstellungen-in-typen-buendeln — 2026-11-13

**Review-Art:** Code (gegen Plan, ADR und Hard Rules; nicht gegen die DoD).

**Gegenstand:** Diff `013d2954..7582e1ef` im Zweig `worktree-agent-a2bd77169a720681a` (ein Commit `7582e1ef`, 37 Dateien). `main` steht auf `013d2954`, dem Basisstand.

**Skill:** `.harness/skills/reviewer.md` @ `7582e1ef` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-13

**Eingangs-Kontext:** Slice `slice-einstellungen-in-typen-buendeln` (Plan §1 und §3), [ADR-0021](../plan/adr/0021-oeffentlicher-integrations-einstieg.md), [ADR-0040](../plan/adr/0040-wirksame-konfiguration-im-ereignis-aus-typisiertem-wertobjekt.md), `TDS-QA-05`, `TDS-FA-09`, `SPEC-059`, `SPEC-062`, `AGENTS.md` §3.7 und §3.8, die Reports vom 2026-11-12.

## Selbst gefahren

| Lauf | Ergebnis |
|---|---|
| Diff vollständig gelesen (Haupt-, Test-, Testserver-, Integrator-Code, Spezifikation, `docs/user/`, Plan, `spec-abdeckung.md`) | siehe Befunde |
| Grep über `spec/`, `docs/user/`, `docs/plan/`, `src/`, `testserver/`, `tools/`, `README.md`, `harness/` nach `RequestSettings`, `requests.<altes Feld>`, Konstruktor, `copy`, `componentN`, „sechs“, „positional“, „nach Position“ | keine zweite Fundstelle der alten Lesart außerhalb der Zeitdokumente (`done/`, `observations/`, `reviews/`); `src/main` trägt keinen alten Feldnamen mehr |
| Grep `docs/user/` nach „bisher“, „noch nicht“, „nicht gebaut“, „Zielzustand“, „vorläufig“, „seit Slice“ | keine Treffer zu den geänderten Stellen |
| Mutationen aus dem Quelltext abgeleitet (nicht gefahren, kein Gradle-Lauf): Tausch `query`/`cancel` in `ConnectionWiring`, `cancel`/`grace` in `Bootstrap`, `handleLimit`/`maxMessageBytes` in `ConnectionWiring`, Felder in `effectiveSettings()`, Bereiche in `ConfigValidation`, Felder im Aufbau von `ConfigLoader`, Voreinstellung einer Gruppe, Gruppe ohne `@JvmOverloads` | je Mutation mindestens ein Test rot, Zuordnung unten; eine Wirkung ist aus dem Quelltext gelesen, nicht gemessen |
| `make docs-check` | grün vor dem Commit dieses Reports (1394 Dateien, 0 Befunde) |

Gesamtlaufzeit rund 15 Minuten; größter Posten: Lesen der Spezifikationszeilen und der Greps. Kein Gradle-Lauf, keine Last.

## Zu den Schwerpunkten

1. **Typschnitt.** `RequestSettings(timeouts, limits, observation)` mit `RequestTimeouts` (drei Fristen), `RequestLimits` (zwei Grenzen) und `ObservationSettings` (ein Feld) entspricht dem Plan §3 und dem Beispiel „Limits, Zeiten, Beobachtung“ in §1. Die drei Namen stehen in `SPEC-059` (Package `config`); die Fläche wächst sonst nur um die von `@JvmOverloads` erzeugten Konstruktoren und die implizite `copy`/`componentN` der Datenklassen. Die inkompatible Änderung ist in Plan §1 benannt und nach [ADR-0021](../plan/adr/0021-oeffentlicher-integrations-einstieg.md) (Zusage vor 1.0 eng) vor der ersten Veröffentlichung zulässig; die Closure-Notiz trägt sie laut Plan §5 noch nach. Ein Finding dazu: F-4.
2. **Zweite Fundstellen.** Alle sechs Feldnamen sind in Spezifikation (`SPEC-007`, `-009`, `-033`, `-047`, `-058`/Handle-Abschnitt, `-061`, `-062`, Telemetrie-Abschnitt, Prüfzeile `SPEC-059`), `docs/user/einbettung-und-betrieb.md`, `docs/user/referenz-konfiguration.md`, KDoc in `PreparedHandles.kt` und `RequestLoop.kt` und im Aufbau von `ConfigLoader`/`EnvironmentConfig` nachgezogen. Voreinstellungen (30, 5, 10, 1000, 16 MiB, 60) stehen in Spezifikation §3, Referenztabelle, Prüfzeile `SPEC-059` und `ConfigValidationTest` gleich. `docs/user/testserver-manuell.md` und `referenz-ereignisse.md` nennen weder die Felder noch den Konstruktor. Findings: F-1, F-5.
3. **Zusage, Test, Mutation.**

   | Mutation | rot durch |
   |---|---|
   | Tausch `queryTimeout`/`cancelTimeout` in `ConnectionWiring` | `AbbruchWiringTest` (QUERY 1, CANCEL 60, Obergrenze 30 s für die Frist) |
   | Tausch `shutdownGrace`/`cancelTimeout` in `Bootstrap` | `ShutdownWiringTest` (`SHUTDOWN_GRACE` 0 und 1 bei Voreinstellung 5 für Abbruch); aus dem Quelltext gelesen |
   | Tausch `handleLimit`/`maxMessageBytes` | `HandleLimitEmbeddingTest` (Grenze 2), `MessageLimitWiringTest` |
   | Tausch zweier Felder in `effectiveSettings()` | `RequestSettingsTest` (sechs verschiedene Werte) |
   | Bereiche in `ConfigValidation` einer anderen Gruppe zugeordnet | `ConfigValidationTest` (Grenzfälle je Schlüssel) |
   | Tausch zweier Felder im Aufbau von `ConfigLoader` | `ConfigLoaderTest` (11/3/2, 250/70000/9 verschieden) |
   | verlorene Voreinstellung | `RequestSettingsTest`, `EinstellungenEmbeddingTest` |
   | `@JvmOverloads` fehlt an einer Gruppe | `EinstellungenEmbeddingTest` (`getConstructor(...)` wirft) |

   Die Java-Lesart ist nur über Reflection auf öffentliche Konstruktoren belegt. `docs/user/einbettung-und-betrieb.md` nennt als Beleg `EinstellungenEmbeddingTest`, die Spezifikation führt keine Java-Aussage; die Doku ist damit nicht breiter als der Beleg (INFO I-1).
4. **Vertauschbarkeit innerhalb einer Gruppe** (`query`/`cancel`/`grace`, `handles`/`bytes`) ist in `docs/user/` ausdrücklich genannt („übersetzt in jeder Reihenfolge; Felder benennen“, Fehlerzeile der Tabelle). Als Restrisiko richtig benannt; der Plan §6 trägt den Ausgang erst bei der Closure.
5. **Schichtregel.** `RequestSettingsTest` ist neu (kein Move) und liegt in `runtime`, weil `effectiveSettings()` dort `internal` ist; `runtime` hat in `config/detekt/detekt.yml` keine Importregel. Plausibel, kein Befund.
6. **`spec-abdeckung.md`.** Die Änderungen (zusätzliche Test-Spalten für `RequestSettingsTest` bei `SPEC-007`, `-009`, `-033`, `-047`, `-058`, `-062`; `EinstellungenEmbeddingTest` bei `SPEC-059`, `-062`) decken sich mit den `@spec`-Tags der beiden neuen Testklassen. `main` steht auf dem Basisstand, ein Konflikt besteht jetzt nicht (I-2).

## Findings dieses Laufs

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| F-1 | MEDIUM | Die Plan-Tabelle §3 nennt `runtime/TelemetryEvents.kt`, aber nicht `runtime/Bootstrap.kt`, `runtime/ConnectionWiring.kt` (Verdrahtung der Fristen und Grenzen, im Diff), `protocol/dispatch/PreparedHandles.kt` und `protocol/dispatch/RequestLoop.kt` (KDoc) und nicht `src/test/.../runtime/AbbruchWiringTest.kt`, `TdsServerLifecycleTest.kt`; der Kopf „Berührte Spec-Stellen“ nennt `SPEC-061` und `SPEC-058` nicht, beide Zeilen der Spezifikation sind im Diff. Failure-Szenario: Der Negativbefund „keine Datei darüber hinaus“ der Closure ruht auf einer Tabelle, die die Verdrahtung der Fristen (genau die Stelle der Vertauschungsgefahr) nicht nennt. | Reviewer-Skill „Plan-Tabelle nennt die berührten Dateien nicht“ (Maintainability) | `docs/plan/planning/in-progress/slice-einstellungen-in-typen-buendeln.md` · „`src/main/kotlin/io/github/pt9912/tdswire/domain/telemetry/EffectiveSettings.kt`, `runtime/TelemetryEvents.kt`“ und „**Berührte Spec-Stellen:** SPEC-059 · SPEC-062“ | ja: Abgleich Tabelle mit `git diff --stat` | Plan-Tabelle nennt die berührten Dateien nicht |
| F-2 | LOW | Das KDoc von `EinstellungenEmbeddingTest.eineAusJavaGebauteGruppeWirdBeimStartGeprueft` nennt als Wächter „eine Gruppe, die der Start nicht liest“. Gültig ist die Frist von 20 s; läse der Start `timeouts` nicht, bliebe der Test grün, nur eine ungelesene Gruppe `limits` macht ihn rot. Die Wächter-Aussage ist breiter als der Test; die Prüfung von `timeouts` trägt `ConfigValidationTest`. | `AGENTS.md` §3.8 | `src/integrator/kotlin/io/github/pt9912/tdswire/integrator/EinstellungenEmbeddingTest.kt` · „Wächter: eine Gruppe, die der Start nicht liest, ein Befund für die gültige Gruppe.“ | ja: Mutation in `ConfigValidation` (Fristen nicht geprüft) | Wächter-Beschreibung breiter als der Test |
| F-3 | LOW | Das Änderungsprotokoll der Spezifikation führt für diesen Slice keine Zeile, obwohl frühere Änderungen der öffentlichen Fläche je eine tragen (`2026-11-07`, `2026-11-08`, `2026-11-09`); die Zusage in `SPEC-059` bindet Änderungen der Fläche an eine Änderung der Spezifikation. Zugleich sind in zwei älteren Zeilen (`2026-11-06` Handles, `2026-11-07` Metriken) die Namen des damaligen Standes durch die heutigen ersetzt (`RequestLimits.maxPreparedHandles`, `ObservationSettings.metricsIntervalSeconds`); an dem Tag gab es diese Typen nicht. | `SPEC-059` (Zusage), `AGENTS.md` §3.7 | `spec/spezifikation.md` · „(`RequestLimits.maxPreparedHandles`, `MAX_PREPARED_HANDLES`, `SPEC-061`, `SPEC-058`)“ | nein | Änderungsprotokoll ohne Zeile / Protokollzeile fortgeschrieben |
| F-4 | LOW | Der Satz der Aussage über die inkompatible Änderung (Konstruktor `RequestSettings(int, int, int, int)` entfällt, `copy`/`componentN` ändern sich) steht im Plan §1, aber weder in der Spezifikation noch in `docs/user/`; ein Einbettender, der von einem früheren Stand kommt, findet sie dort nicht. Vor der ersten Veröffentlichung kein Verstoß ([ADR-0021](../plan/adr/0021-oeffentlicher-integrations-einstieg.md)); der Plan §5 verlangt die Benennung in der Closure-Notiz. | [ADR-0021](../plan/adr/0021-oeffentlicher-integrations-einstieg.md), Plan §1 | `docs/plan/planning/in-progress/slice-einstellungen-in-typen-buendeln.md` · „**Inkompatible Änderung der öffentlichen Fläche.**“ | nein | Benennung der Flächenänderung nur im Plan |
| F-5 | INFO | Die Zeile „Nachweis“ in `SPEC-059` führt `EinstellungenEmbeddingTest` nicht in der Aufzählung der Einbettungstests; `docs/user/einbettung-und-betrieb.md` nennt den Test. Die Aufzählung ist mit „Dazu gehören“ eingeleitet, kein Widerspruch. | Maintainability | `spec/spezifikation.md` · „Test: `FlaecheEmbeddingTest`“ | nein | Aufzählung der Einbettungstests unvollständig |
| I-1 | INFO | Java-Lesart nur per Reflection auf `getConstructor(...)` belegt; `new RequestTimeouts(10)` im Doku-Satz ist als Java-Quelltext nicht übersetzt. Die Signatur der erzeugten Konstruktoren ist damit belegt, ein Java-Compiler nicht. Der Plan §3 nennt diese Grenze (kein Java-Quelltext im Source-Set `integrator`); die Doku ist nicht breiter als der Beleg. Ohne Aktion. | `AGENTS.md` §3.8 | `docs/user/einbettung-und-betrieb.md` · „(`new RequestTimeouts(10)`, `new RequestSettings(timeouts, limits)`)“ | nein | Beleg per Reflection statt Compiler |
| I-2 | INFO | `docs/plan/spec-abdeckung.md` ändert Zeilen, die parallele Zweige ebenfalls berühren (`SPEC-007`, `-009`, `-033`, `-047`, `-058`, `-062`, `-059`). Bei einem Konflikt gilt laut `harness/README.md` die Fassung von `main`, neu erzeugt wird nur nach Test- oder Tag-Änderung (hier der Fall). Ohne Aktion jetzt. | `harness/README.md` §Minimal agent workflow | `docs/plan/spec-abdeckung.md` · „RequestSettingsTest, ServerSmokeTest“ | nein | Matrix-Konfliktrisiko |
| I-3 | INFO | `@JvmOverloads` an `ObservationSettings` (ein Feld) und `RequestSettings` fügt nichts über den von Kotlin erzeugten Konstruktor ohne Argumente hinaus, soweit alle Felder Voreinstellungen haben, bzw. bei `RequestSettings` die Überladungen mit zwei Gruppen; die Plan-Begründung („an allen vier Typen“) bleibt gleichförmig. Ohne Aktion. | Maintainability | `src/main/kotlin/io/github/pt9912/tdswire/config/ServerConfig.kt` · „public data class ObservationSettings @JvmOverloads constructor(“ | nein | Gleichförmige Annotation |

Hard Rules: §3.1 (Docker), §3.2 (kein `@Suppress` im Diff), §3.5 (keine ADR geändert), §3.6 (keine Schwelle gesenkt) nicht berührt. §3.7: die neuen KDoc-Sätze sind Zusage, Kopplung oder Grenze; ein Satz „Kotlin ruft mit Namen auf“ ist eine Zusage an den Aufrufer. §3.8: Bereiche und Voreinstellungen sind als Festlegung gekennzeichnet geblieben, die Java-Aussage ist belegt in ihrer Grenze (I-1).

## Negativbefund (geprüft, ohne Befund)

- `src/main/kotlin/…/config/` (`ServerConfig.kt`, `ConfigValidation.kt`): Bereiche und Voreinstellungen unverändert, Prüfung folgt den Gruppen — geprüft, ohne Befund
- `src/main/kotlin/…/runtime/` (`Bootstrap.kt`, `ConnectionWiring.kt`, `TelemetryEvents.kt`): Zuordnung Feld zu Verbraucher Zeile für Zeile gelesen, zwölf Schlüssel von `EffectiveSettings` unverändert ([ADR-0040](../plan/adr/0040-wirksame-konfiguration-im-ereignis-aus-typisiertem-wertobjekt.md)) — geprüft, ohne Befund (Plan-Tabelle: F-1)
- `src/main/kotlin/…/protocol/dispatch/`: nur KDoc-Namen — geprüft, ohne Befund
- `src/test/`: Tests, Mutationen und Wächter-Beschreibungen gegen den Quelltext gelesen — geprüft, ohne Befund außer F-2
- `src/integrator/`: Aufrufe benannt, nur öffentliche Namen — geprüft, ohne Befund außer F-2
- `testserver/`: `ConfigLoader.kt` und Tests, Umgebungsnamen unverändert — geprüft, ohne Befund
- `spec/`: keine `ADR-`/`ARC-`-Kennung im Zugefügten, keine neue Zusage ohne Lastenheft-Anker — geprüft, ohne Befund außer F-3, F-5
- `docs/user/`: `einbettung-und-betrieb.md` und `referenz-konfiguration.md` ganz gelesen, `testserver-manuell.md` und `referenz-ereignisse.md` auf die Felder und den Konstruktor durchsucht, ohne Abwesendes oder Geplantes — geprüft, ohne Befund
- `docs/plan/`, `tools/`, `harness/`, `README.md`: keine zweite Fundstelle der alten Lesart — geprüft, ohne Befund
- `config/detekt/detekt.yml`, `build.gradle.kts`, `Dockerfile`: nicht im Diff — geprüft, ohne Befund
