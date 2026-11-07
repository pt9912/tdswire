# Review-Bericht: slice-betrieb-metriken-tracing (HEAD 33f67126)

* Rolle: Reviewer (Modul 10), frischer Kontext. Gegenstand: Diff `f0886534..33f67126` (Commits `10afe022`, `dfa5146a`, `33f67126` und die vier Moves `fe3b92b1` bis `ef1d41fa` samt `91b5f3e2`), Zweig `worktree-agent-a905952da1a804fdb`. Maßstab: Slice (Plan, DoD, Rückführungen), [TDS-FA-09](../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit), [ADR-0020](../plan/adr/0020-logformat-und-observability-schnittstelle.md) (`Accepted`), [ADR-0021](../plan/adr/0021-oeffentlicher-integrations-einstieg.md), [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 4 (Lesart öffentliche Namen), `SPEC-059` und `make flaeche`, `AGENTS.md` §3.1 bis §3.8. Keine Prüfung gegen die DoD (Aufgabe des Verifiers).
* Skill: `.harness/skills/reviewer.md`, Stand des Worktrees. Modell: Sonnet 5.5. Datum: 2026-11-07.
* Selbst gefahren (alle am Stand `33f67126`, Arbeitsbaum zwischen den Läufen sauber):
  * `make gates FORCE=1`: Exit 0 (Layer-Cache; `gate-zuordnung` 16 Gates, Selbsttest 33 Fälle).
  * `make clean-build` (`--no-cache`, Stages `build-jar`, `test`, `detekt`): Exit 0, Stage `test` 6 min 28 s bei Systemlast 21 bis 23 (andere Läufe auf dem Host), alle Tests grün, `detekt` grün. Der Wanduhr-Test `MetricsTracingWiringTest` (Abstand 1 s) lief damit unter hoher Last grün; dazu kommen drei weitere grüne Läufe seiner Klasse in den Mutationsläufen ohne Wirkung (M6, M10, M11), also vier grüne Läufe, kein Flackern gesehen.
  * `make flaeche`: Exit 0. `make docs-check`: 1109 Dateien, 0 Befunde (vor Anlage dieses Berichts). `make matrix-zitate`: Exit 0. `make e2e`: grün, 8 Stufen, 242 s (Last 21 bis 23). `make test` ist als Stage `test` in `make clean-build` und `make gates` gelaufen.
  * Mutationen (Tabelle unten), je ein Docker-Lauf der Klassen `ObserverTest`, `LatencyHistogramTest`, `SpanOutcomesDispatchTest`, `MetricsTracingWiringTest`, `ConfigValidationTest`, `TelemetryRedactionTest` (Lauf über ein Dockerfile-Duplikat im Scratchpad, das nur die Gradle-Zeile ersetzt); nach jeder Mutation `git checkout` der Datei.
  * Merge-Konflikte: `git merge-tree --write-tree` gegen `worktree-agent-a0206a0d2b6e5d78e` (Zustandsereignisse), `worktree-agent-a4845d02c1beaf126` (Schreibpfad) und `worktree-agent-a9764e63162fbe637` (Nutzer-Doku).

## Gesamturteil

0 HIGH, 4 MEDIUM, 3 LOW, 4 INFO. Die Beobachtung ist in der Sache tragfähig: ein Span endet in allen gefahrenen Pfaden genau einmal, kein Pfad hält SQL-Text, Kennung der Anmeldung oder Parameter in einem Attribut, die Schichtgrenze (`observability` importiert nur `domain`), das Suppression-Verbot und die Ausgabe-Sperre sind gehalten, und acht von elf Mutationen machen mindestens einen Test rot. Die drei, die nicht rot werden (`abandoned`, `closed`, Atomizität des Endes), sind F-1 und F-7. Die übrigen MEDIUM sind zweite Fundstellen der Spezifikation (F-2), die Plan-Tabelle (F-3) und KDoc-Zusagen breiter als der Beleg (F-4). Haken: keiner der Liefer-Punkte steht als `- [x]` im Diff.

## Mutationen (selbst gefahren)

| Mutation | Wirkung gemessen |
|---|---|
| M1 `Tracked.end`: Sperre `compareAndSet` ersetzt durch `ended.set(true)` (Span endet mehrfach) | rot: `ObserverTest > Ein Span endet genau einmal` |
| M2 `InstrumentedBackend`: Fehler im Strom zählt als `ok` | rot: `SpanOutcomesDispatchTest > backend_execute endet mit close der Ausfuehrung`, `MetricsTracingWiringTest > Login, Anfrage, Fehler und Cancel erzeugen die Spans mit ihren Pflicht-Attributen` |
| M3 `ConfigValidation`: Bereich `0..Int.MAX_VALUE` statt `0..86400` (Intervall ohne Obergrenze) | rot: `ConfigValidationTest > Bereichsgrenzen gelten auch ohne Lader` |
| M4 `TdsServer.stop`: Zeitgeber wird nicht beendet | rot: `MetricsTracingWiringTest > stop beendet die offenen Spans und den Schnappschuss` |
| M5 `RequestLoop.answer`: der SQL-Text der Nachricht (Nutzdaten als UTF-16LE) wird als Kennung des Spans übergeben (SQL-Text in einem Attribut) | rot, fünf Tests: `SpanOutcomesDispatchTest > Handle-Freigabe und RPC-Abweisung nennen ihr Ende`, `MetricsTracingWiringTest` (drei: RPC-Art, Kardinalität/Text, Pflicht-Attribute), `TelemetryRedactionTest > SQL-Text, Parameterwert und Ausnahmetext erreichen kein Ereignis` |
| M6 `RunEnd.spanOutcome`: `Abandoned` ergibt `OK` statt `ABANDONED` | **grün (Exit 0), kein Test rot** (F-1) |
| M7 `LatencyHistogram.percentile`: Rang abgerundet statt aufgerundet | rot: `LatencyHistogramTest > Der Rang folgt Nearest-Rank`, `ObserverTest > Die Latenz-Perzentile sind die Obergrenzen der Klassen` |
| M8 `HandshakeConnectionHandler.handle`: bei Ausnahme (`outcome == ERROR`) endet der Span nicht | rot: `MetricsTracingWiringTest > Eine Ausnahme des Handlers endet tds_connection mit error` |
| M9 `Tracked.end`: `openCount` wird nicht gesenkt (`spans_open` wächst) | rot, zehn Tests (u. a. `ObserverTest > Ein Span endet genau einmal`, `Zaehler und Momentwerte folgen den Spans`, fünf Wiring-Tests) |
| M10 `RequestLoop.answer`: Voreinstellung `closed` ersetzt durch `ok` | **grün (Exit 0), kein Test rot** (F-1) |
| M11 `Tracked.end`: `compareAndSet` ersetzt durch `if (ended.get()) return; ended.set(true)` (Prüfen-dann-Setzen, nicht atomar) | **grün (Exit 0), kein Test rot** (F-7) |

Nicht gefahren: Kardinalität über einen Schlüssel der Metriken aus SQL-Text (die Prüfung `visibleText()` im Kardinalitätstest liest `toString` der Map samt Schlüsseln, M5 zeigt, dass sie bei Text im Attribut rot wird); eine blockierende Senke (die Grenze ist benannt, nicht getestet, laut DoD richtig so).

## Findings

### F-1 MEDIUM — Zwei Ausgänge von `tds.query` (`abandoned`, `closed`) und die Abbildung des Laufendes haben keinen Test

* kategorie: MEDIUM
* quelle: Reviewer-Skill „Eigencode, dessen Entfernung keinen Test rot macht“; `AGENTS.md` §3.8 (Spezifikation Abschnitt 5 führt beide Werte als „gebaut“)
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/RequestOutcomes.kt` · „RunEnd.Abandoned -> SpanOutcome.ABANDONED“; `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/RequestLoop.kt` · „span.end(queryOutcome ?: SpanOutcome.CLOSED)“
* befund: Mutation M6 (`abandoned` als `ok`) und M10 (Voreinstellung `closed` als `ok`) lassen alle sechs Testklassen grün; im ganzen Test-Baum kommt weder `abandoned` noch `closed` als erwarteter Wert vor, und `RunEnd.spanOutcome()` hat keinen Unit-Test. Ein Backend, das den Abbruch ignoriert (`CANCEL_TIMEOUT`), meldete damit `ok` und zählte nicht als Abbruch-Ende, ohne dass ein Lauf es bemerkt.
* verifizierbar: ja (Mutationen M6, M10, `make test`)
* klasse: Eigencode ohne rot werdenden Test

### F-2 MEDIUM — Zweite Fundstellen der Spezifikation und der Nutzer-Doku nicht nachgezogen

* kategorie: MEDIUM
* quelle: Reviewer-Skill „Fortschreibung lässt die zweite Fundstelle stehen“; `AGENTS.md` §3.8
* pfad: `spec/spezifikation.md` · „sie nach dem Feld (`port`, `tables` als Zahl, die übrigen als Text), nicht je Art“
* befund: Die Aussage ist mit `durationMs` (Zahl, `Long?`) und `metrics` (Abbildung `Map<String, Long>`) falsch geworden; sie steht unverändert in Abschnitt 4 (Zeilen um 1206). Ebenso unverändert: die Aufzählung der Zeichenketten-Felder im Absatz „Redaktion durch Bauart“ („Zeichenketten stehen in `principal`, in der Adresse des Bindefehlers, …“) nennt `span`, `outcome`, `requestId`, `phase`, `requestKind` nicht, und die Tabelle der Konfiguration in `docs/user/einbettung-und-betrieb.md` („Alle Bereiche und Voreinstellungen sind Festlegungen“), die Tabelle „Defaults und Konstanten“ (Abschnitt 3) und die Aufzählung der Voreinstellungen von `RequestSettings` in der Zeile „Prüfung“ der Fläche führen `METRICS_INTERVAL_SECONDS` / `metricsIntervalSeconds` (60) nicht; er steht nur im Absatz „Abstand“ und in der Prüfungs-Bereichsliste.
* verifizierbar: ja (Grep nach „als Zahl, die übrigen als Text“; `make docs-check` fängt es nicht)
* klasse: Fortschreibung lässt die zweite Fundstelle stehen

### F-3 MEDIUM — Plan-Tabelle und Kopf „Berührte Spec-Stellen“ nennen nicht alle berührten Dateien und Stellen

* kategorie: MEDIUM
* quelle: Reviewer-Skill „Plan-Tabelle nennt die berührten Dateien nicht“
* pfad: `docs/plan/planning/done/slice-betrieb-metriken-tracing.md` · „Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.“
* befund: Der Diff berührt Dateien ohne Zeile in §3: `src/main/.../protocol/dispatch/RequestOutcomes.kt` (neu, bei der Zerlegung entstanden), `src/integrator/.../TelemetryEmbeddingTest.kt` und `TelemetryFelderEmbeddingTest.kt`, `src/test/.../ConfigValidationTest.kt`, `EnvironmentConfig.kt`, `TelemetryEventTest.kt`, `TelemetryRedactionTest.kt`, `SpanOutcomesDispatchTest.kt` und `docs/plan/spec-abdeckung.md`. Der Kopf „Berührte Spec-Stellen“ nennt `SPEC-029`, das der Diff nicht ändert, aber nicht `SPEC-052` (Zeile der Spezifikation geändert), `SPEC-059`, `SPEC-044`, `SPEC-048`, `SPEC-009`, `SPEC-011`, `SPEC-030` (als `@spec`-Tags und Abdeckung berührt). Der Absatz „Zuschnitt der Fläche (vorab)“ und die DoD nennen „ein Konstruktorparameter an `ServerConfig`“; gebaut ist `RequestSettings.metricsIntervalSeconds` (Abweichung ist in der Spezifikation benannt, im Slice noch nicht). Die Closure-Notiz steht aus („Wird bei der Closure gefüllt“); sie muss die Abweichung tragen.
* verifizierbar: ja (Diff gegen §3: `git diff f0886534..HEAD --name-only`)
* klasse: Plan-Tabelle ohne die berührten Dateien

### F-4 MEDIUM — KDoc sagt „wirft nicht und blockiert nicht“, wo Spezifikation und Telemetry das Gegenteil festlegen

* kategorie: MEDIUM
* quelle: `AGENTS.md` §3.8; [ADR-0020](../plan/adr/0020-logformat-und-observability-schnittstelle.md) Frage 7 (blockierende Senke ist ein akzeptiertes Negativ)
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/domain/telemetry/Instruments.kt` · „Der\n * Aufruf wirft nicht und blockiert nicht.“; `src/main/kotlin/io/github/pt9912/tdswire/observability/Observer.kt` · „es wirft nicht“
* befund: `Span.end` ruft die Senke auf dem Anfragepfad; eine blockierende Senke blockiert damit `end` (Spezifikation Abschnitt 5: „hält auch das Ende von `tds.query` und `backend.execute` an“), und `Telemetry.emit` lässt `CancellationException` und jeden `Error` zum Aufrufer laufen (`Telemetry`, KDoc). Die Zusage der Schnittstelle ist breiter als beides.
* verifizierbar: nein (Lesart; M-frei, kein Test)
* klasse: Zusage breiter als ihr Beleg

### F-5 LOW — Der Zeitgeber der Schnappschüsse endet still bei einem `Error` oder einer `CancellationException` der Senke

* kategorie: LOW
* quelle: Maintainability; Spezifikation Abschnitt 5 („Dass auch jeder `Error` der Senke nicht gefangen wird, ist eine Festlegung“), `SPEC-059` („Die Bibliothek schreibt nirgends hin“)
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/runtime/Bootstrap.kt` · „source.snapshot()“
* befund: Die Schleife in `CoroutineScope(dispatcher).launch` fängt nichts; läuft ein `Error` aus der Senke heraus, endet die Aufgabe ohne Ereignis, und der Fehler geht an den Handler für nicht gefangene Ausnahmen des Threads (üblicherweise Standardfehlerkanal). Danach erscheint bis `stop` kein `metrics` mehr. Die Spezifikation benennt diese Folge für den Zeitgeber nicht (nur den Anfragepfad); aus dem Code gelesen, nicht gefahren.
* verifizierbar: nein (kein Test; Lesart)
* klasse: Folge einer Festlegung am Nebenpfad nicht benannt

### F-6 LOW — Backend-Aufruf nach `abandoned` bleibt über das Ende von Anfrage und Verbindung hinaus offen; nicht benannt

* kategorie: LOW
* quelle: Maintainability; Slice-DoD „kein offener Span nach dem Lauf“ (Maßstab nur als Zusage für den Plan-Text, nicht als Verifikation)
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/InstrumentedBackend.kt` · „span.end(outcome())“
* befund: Nach `RunEnd.Abandoned` endet der Lauf des Sequencers erst, wenn das ignorierende Backend zurückkehrt (`AbortableRun`, KDoc „detached“); der Span `backend.execute` und `backend_active` bleiben bis dahin offen, während `tds.query` (`abandoned`) und `tds.connection` schon geendet sind. Das ist stimmig mit der Wirklichkeit, aber weder in der Spezifikation (Abschnitt 5: „Ein Span endet genau einmal, auch bei … Beenden“) noch in der Nutzer-Doku („Jeder Span endet genau einmal, auch bei Cancel, Disconnect, Ablauf der Frist und Beenden“) als Ausnahme genannt und von keinem Test gehalten (F-1).
* verifizierbar: nein
* klasse: Zusage ohne ihre gemessene Bedingung

### F-7 LOW — Kein Test hält die Nebenläufigkeit des Beobachters

* kategorie: LOW
* quelle: Maintainability; [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität) (parallele Verbindungen als Messmethode)
* pfad: `src/test/kotlin/io/github/pt9912/tdswire/observability/ObserverTest.kt` · „Ein Span endet genau einmal“
* befund: Zähler sind `AtomicLong`, das Ende eines Spans ist `compareAndSet`; alle Tests des Beobachters laufen sequentiell, die Tests am Socket mit höchstens zwei Verbindungen nacheinander. Mutation M11 (Prüfen-dann-Setzen statt `compareAndSet`) lässt `ObserverTest` und `MetricsTracingWiringTest` grün: die Atomizität des Endes ist von keinem Test gehalten.
* verifizierbar: ja (M11)
* klasse: Atomizität nur sequentiell getestet

### F-8 INFO — Konflikte mit den parallelen Zweigen (konkret) und semantische Überschneidungen

* kategorie: INFO (Übergabe an Planner/Architect; keine Aktion am Diff)
* quelle: Maintainability; Slice §3 („Konfliktfläche“)
* pfad: `git merge-tree --write-tree HEAD worktree-agent-a0206a0d2b6e5d78e` und `… worktree-agent-a4845d02c1beaf126`
* befund: **Zustandsereignisse** (`worktree-agent-a0206a0d2b6e5d78e`): Inhaltskonflikte in 24 Dateien: `TelemetryEvent.kt` (beide hängen Konstanten an `EventKind` und Zugriffe an `TelemetryEvent`), `ServerConfig.kt` und `ConfigValidation.kt` (beide fügen `RequestSettings` einen fünften Ganzzahl-Parameter an: `maxMessageBytes` dort, `metricsIntervalSeconds` hier), `TdsServer.kt` (`stop`: dort `stopped.compareAndSet` vor `listener.shutdown()` mit `ServerStopping`, hier `snapshots?.cancelAndJoin()` am Anfang), Testserver `ConfigLoader.kt`, `EnvKeys.kt`, `TelemetryJson.kt`, `ConfigLoaderTest.kt`, `src/integrator/.../TelemetryEmbeddingTest.kt` und `TelemetryFelderEmbeddingTest.kt`, `TestserverLogProcessTest.kt`, `TelemetryRedactionTest.kt`, `EnvironmentConfig.kt`, `ConfigValidationTest.kt`, `spec/spezifikation.md`, `docs/user/einbettung-und-betrieb.md`, Roadmap, `welle-betrieb-diagnose.md`, beide Slice-Dateien und fünf Slices in `done/`. `Bootstrap.kt` und `ConnectionWiring.kt` mischen ohne Konflikt, aber mit Folgen: (1) `ConfigLoader` ruft `RequestSettings(queryTimeout, cancelTimeout, grace, handles, <fünftes>)` mit Ganzzahlen; wer beide Zeilen behält, vertauscht `maxMessageBytes` und `metricsIntervalSeconds` still, wenn die Reihenfolge im Konstruktor von der im Aufruf abweicht. (2) `ServerConfig.effectiveSettings()` des anderen Zweigs führt elf Schlüssel und nicht `METRICS_INTERVAL_SECONDS`; das Ereignis `CONFIG_EFFECTIVE` meldete nach dem Merge das Intervall nicht. (3) Beide Zweige zählen Verbindungsenden: `CONNECTION_CLOSED` (`reason` des Listeners, auch für abgewiesene Verbindungen, die den Handler nie erreichen) und `SPAN_ENDED` `tds.connection` (`outcome`); die Vokabulare unterscheiden sich (etwa `HANDLER_FAILED` gegen `error`, `SHUTDOWN` gegen `cancelled`; die Zuordnung aller Werte ist nicht gefahren); eine Doppelmeldung ist es nicht, die Reihenfolge ist Span-Ende vor `CONNECTION_CLOSED` (der Span endet im Handler). (4) Exakte Ereignislisten in `TelemetryEmbeddingTest`, `TestserverLogProcessTest` und `ConfigValidationTest` ändern sich in beiden Zweigen. **Schreibpfad** (`worktree-agent-a4845d02c1beaf126`): Konflikte in `RequestLoop.kt` (`answer` wird hier zu einer Funktion mit `try/finally`, dort bleibt es der Ausdruck `when`; `execute(…, via = …)`) und `ConnectionWiring.kt` (`SimulationBulk.of(backend)` an der Stelle von `RequestLoop(...)`), Roadmap und `spec-abdeckung.md`. Semantisch: eine Nachricht vom Typ Bulk-Load wird hier als `sql_batch` beobachtet (`RequestKind` kennt nur `SQL_BATCH` und `RPC`; `if (message.type == PacketType.RPC) RPC else SQL_BATCH`), und das `execute` über `via = bulk.statement(text) ?: backend` läuft durch `via.observed(session)`, sodass auch dort `backend.execute` entsteht. **Nutzer-Doku** (`worktree-agent-a9764e63162fbe637`): ein Konflikt, `docs/user/einbettung-und-betrieb.md`.
* verifizierbar: ja (`git merge-tree`)
* klasse: Konfliktfläche benannt

### F-9 INFO — Öffentliche Fläche: neue Mitglieder, keine neuen Typnamen

* kategorie: INFO
* quelle: [ADR-0021](../plan/adr/0021-oeffentlicher-integrations-einstieg.md), `SPEC-059`, [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 4
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/domain/telemetry/TelemetryEvent.kt` · „public open val metrics: Map<String, Long>?“; `src/main/kotlin/io/github/pt9912/tdswire/config/ServerConfig.kt` · „val metricsIntervalSeconds: Int“
* befund: `make flaeche` bleibt bei 61 Typnamen und vergleicht nach seiner benannten Grenze keine Mitglieder; neu sind zwei Konstanten (`METRICS`, `SPAN_ENDED`), sieben Zugriffe an `TelemetryEvent` und ein fünfter Parameter an `RequestSettings`. Das ist mit der Lesart „die Zusage gilt den Typnamen, jede neue Art bricht ein `when`“ (Spezifikation Abschnitt 4) vereinbar; die Lesart von [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 4, die Konstruktoren, Parameter und Felder als „Namen“ aufzählt, erfüllt der Slice-Absatz „Zuschnitt der Fläche“ (mit dem unter F-3 genannten Fehler bei `ServerConfig`). Java-Lesart: der Konstruktor `RequestSettings(int, int, int, int)` entfällt (kein `@JvmOverloads`; `QueryEvent.Failure` trägt es), ebenso ändern sich `copy` und `component5`; vor `1.0` nach `SPEC-059` zulässig. `REQUEST_FAILED` bleibt ohne `requestId` (Nutzerentscheidung offen, §6 und Spezifikation benennen es). Die Beobachtung `BEO-TDS/oeffentliche-flaeche-der-telemetrie-ohne-entscheidung` steht auf „gestrichen“; die Closure nennt, ob sie mit den neuen Zugriffen wieder zählt.
* verifizierbar: ja (`make flaeche`, Grep)
* klasse: Fläche wächst um Mitglieder

### F-10 INFO — Änderungen an bestehenden Tests und an der Aussage „`LOG_LEVEL=DEBUG` ändert nichts“

* kategorie: INFO
* quelle: Maintainability
* pfad: `docs/user/einbettung-und-betrieb.md` · „`LOG_LEVEL=DEBUG` zeigt dort das Ende der Spans“; `src/test/kotlin/io/github/pt9912/tdswire/runtime/TestserverLogProcessTest.kt` · „span_ended“
* befund: Die Aussage ist richtig nachgezogen (`LOG_LEVEL=DEBUG` am Prozess `main` zeigt jetzt `span_ended`; gemessen in `TestserverLogProcessTest`, beide Ebenen). Die angepassten bestehenden Tests (`ConfigValidationTest` mit `RequestSettings(30, 5, 10, 1000, 60)`, `TelemetryEmbeddingTest` mit Fertigstellung des Spans vor `stop`, `TelemetryRedactionTest` mit Abbildung nur für den Namen `metrics`) lockern keine Prüfung außer der Abbildung; die Schlüssel der Abbildung hält der Kardinalitätstest über `visibleText()`, nicht die Bauart. `requestIds()` wird je Anfrage gerufen, auch ohne Backend-Aufruf: von außen nicht sichtbar (die Kennung geht nur in Span und `QueryRequest`; zwei Backend-Aufrufe je Anfrage gibt es nicht), in Tests mit Zähler verschieben sich die Nummern.
* verifizierbar: ja
* klasse: Änderung bestehender Tests begründet

### F-11 INFO — Zeit-Schärfe des Schnappschusses und Konsistenz der Schlüssel

* kategorie: INFO
* quelle: Maintainability
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/observability/Metrics.kt` · „fun read(): Map<String, Long>“
* befund: Der Schnappschuss liest die Zähler einzeln; `requests_total` und die Verteilung können um einen Eintrag auseinanderliegen, wenn ein Span gleichzeitig endet (`requestEnded` zählt zuerst, dann die Verteilung). Die Spezifikation sagt nichts über Konsistenz zwischen den Schlüsseln; ein Auswertender, der `requests_total` mit der Verteilung verrechnet, sollte das wissen. `spans_open` ist wahr zum Zeitpunkt des Lesens.
* verifizierbar: nein
* klasse: Konsistenz des Schnappschusses nicht benannt

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `src/main/.../observability/` (Observer, Metrics, LatencyHistogram): Importe, Atomizität, Perzentil-Rang (Nearest-Rank über Klassengrenzen 1 bis 60000 ms, darüber `Long.MAX_VALUE`) | geprüft, ohne Befund über F-7 und F-11 hinaus; Importe nur `domain` und `java.*`, Ende genau einmal per CAS (M1, M9 rot) |
| `src/main/.../domain/telemetry/` (`Instruments.kt`, `MeasurementEvents.kt`, `TelemetryEvent.kt`) | geprüft, ohne Befund über F-4 und F-9 hinaus; kein SQL-Text-Feld, `requestId` auf 256 Einheiten gekürzt |
| `src/main/.../protocol/dispatch/` (`RequestLoop.kt`, `InstrumentedBackend.kt`, `RequestOutcomes.kt`) | Befunde F-1 und F-6; Span `tds.query` endet in `finally` von `answer` genau einmal (kein Pfad ohne Ende; M10 zeigt nur die Voreinstellung ungetestet); Backend-Fehler als `error` (M2 rot) |
| `src/main/.../runtime/` (`ConnectionWiring.kt`, `Bootstrap.kt`, `TdsServer.kt`) | Befund F-5; Handler-Ausnahme, Cancel und Stop beenden `tds.connection` (M8 rot); Zeitgeber endet mit `stop` (M4 rot); Intervall 0 und fehlende Senke erzeugen nichts |
| `src/main/.../config/` | geprüft, ohne Befund; Bereich 0 bis 86400 (M3 rot), Voreinstellung 60 |
| `testserver/kotlin/` (`ConfigLoader`, `EnvKeys`, `TelemetryJson`) | geprüft, ohne Befund; `metrics.<Schlüssel>` flach, Escape der Bibliothek, Ziffernform des Eingangs |
| [TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit) (Sicherheit der Attribute) | geprüft, ohne Befund: kein Attribut und keine Metrik trägt SQL-Text, Parameter, Kennung der Anmeldung oder Geheimnis; M5 (SQL-Text im Attribut) macht fünf Tests rot; `TelemetryRedactionTest` lockert nur den Typ `Map` für den Namen `metrics`, `SpanEnded` mit fünf Namen |
| [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität) und blockierende Senke | geprüft, ohne Befund: die Grenze „eine blockierende Senke hält das Ende von `tds.query` und `backend.execute` an“ steht in Spezifikation Abschnitt 5 und in der Nutzer-Doku („Grenzen“), im Slice als DoD-Punkt für die Closure; ein Client löst die Blockade nicht aus, sie ist Sache des Einbettenden, und der Absturz-Maßstab dieser Anforderung wird nicht berührt. Overhead ist nicht gemessen und als Folgeträger `slice-qa-performance` benannt (Slice §6, `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`) |
| Suppression-Verbot (`AGENTS.md` §3.2), Ausgabe-Sperre, Host statt Docker (§3.1), Gate-Lockerung (§3.6), `detekt.yml` | geprüft, ohne Befund: im Diff weder `@Suppress` noch Ausgabe noch Änderung an `config/`, `tools/`, `Dockerfile`, `build.gradle.kts` |
| `spec/spezifikation.md` Abschnitt 5, `SPEC-017` bis `SPEC-019`; Lastenheft-Anker | Befund F-2; die Wertemengen sind als Festlegung gekennzeichnet; `TDS-FA-09` nennt Metriken und Spans nicht im Akzeptanzkriterium, die Anker liegen in `SPEC-017` bis `SPEC-019` (Bestand, nicht dieser Diff) |
| `docs/user/einbettung-und-betrieb.md` | Befund F-2 (Tabelle); Abschnitt „Metriken und Spans“ sonst konsistent mit Abschnitt 5 |
| Haken (DoD) und Closure | geprüft, ohne Befund: kein `- [x]` im Diff, Closure-Notiz „Wird bei der Closure gefüllt“, Rollen-Text fehlt |
| Plan-Tabelle und Kopf | Befund F-3 |
| Tests (`ObserverTest`, `LatencyHistogramTest`, `SpanOutcomesDispatchTest`, `MetricsTracingWiringTest`, `TelemetryEmbeddingTest`, `TelemetryFelderEmbeddingTest`, `TelemetryJsonTest`, `ConfigLoaderTest`, `ConfigValidationTest`, `TestserverLogProcessTest`) | Befunde F-1, F-7; grün in vier Läufen, auch unter Last 21 bis 23 |

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 4 |
| LOW | 3 |
| INFO | 4 |

**Finding-Klassen dieses Laufs:** Eigencode ohne rot werdenden Test · Fortschreibung lässt die zweite Fundstelle stehen · Plan-Tabelle ohne die berührten Dateien · Zusage breiter als ihr Beleg · Folge einer Festlegung am Nebenpfad nicht benannt · Zusage ohne ihre gemessene Bedingung · Atomizität nur sequentiell getestet

## Verdikt

**Merge-blockierend:** ja, wegen der vier MEDIUM (F-1 bis F-4); jedes ist mit kleinem Aufwand am Diff zu beheben (Tests für die zwei Ausgänge, zweite Fundstellen, Plan-Tabelle, KDoc), keines erzwingt einen Umbau. Kein HIGH. Der Konflikt-Pfad über den Architect ist nicht ausgelöst (kein Rollen-Widerspruch, kein dritter gleicher Konflikttyp); F-8 geht als Hinweis an den Planner für die Reihenfolge der Merges.

**Übergabe:** Findings gehen an den Implementer; die Finding-Klassen gehen in die Slice-Closure §7. Dieser Bericht ersetzt keine Verifikation gegen die DoD.
