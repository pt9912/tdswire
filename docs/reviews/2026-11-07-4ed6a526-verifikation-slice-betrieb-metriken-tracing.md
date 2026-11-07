# Verifikationsbericht: slice-betrieb-metriken-tracing (HEAD 4ed6a526)

* Rolle: Verifier (Modul 11), frischer Kontext. Gegenstand: Slice `slice-betrieb-metriken-tracing` (`in-progress/`, Zweig `worktree-agent-a905952da1a804fdb`, HEAD `4ed6a526`, Basis des Slice-Anteils `f0886534`, enthält `main` `f5250140`) gegen die DoD §2, die Plan-Tabelle §3, [TDS-FA-09](../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit), [ADR-0020](../plan/adr/0020-logformat-und-observability-schnittstelle.md), [ADR-0040](../plan/adr/0040-wirksame-konfiguration-im-ereignis-aus-typisiertem-wertobjekt.md), [ADR-0021](../plan/adr/0021-oeffentlicher-integrations-einstieg.md), [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 4, Abschnitt 4 und 5 der Spezifikation, `SPEC-017` bis `SPEC-019`, `SPEC-052`, `SPEC-059`, `SPEC-062` und `AGENTS.md` §3.1 bis §3.8.
* Maßstab des Nutzers: „Spec ist Zielbild“; Abweichungen werden sichtbar mit Träger geführt. Verifiziert wird die Zusage (DoD, Plan), nicht der Diff; Belege sind eigene Läufe, nicht die Behauptung des Implementers. Nichts am Produktivcode geändert: alle Mutationen und Schleifen liefen in Kopien des Arbeitsbaums im Scratchpad (Docker-Kontext, Dockerfile-Duplikat, das nur die Gradle-Zeile der Stage `test` ersetzt); der Arbeitsbaum war vor diesem Bericht sauber (`git status` leer).
* Zwei Abschnitte der Historie hatten keinen zweiten Review und sind hier wie ein Review bewertet (Abschnitte „Nachbesserung“ und „Merge-Auflösung“): `33f67126..85a7c00b` und die Auflösung der Merges `0d74eda0` und `4ed6a526` (27 Konfliktdateien, 14 im Code). Das ersetzt keinen Re-Review in eigenem Kontext.

## Eigene Läufe (Belege)

Host: Lastmittel 8 bis 26 durch andere Läufe während der Messungen (kein ruhiger Host; Einzelmessungen). Alle Läufe am Arbeitsbaum bei HEAD `4ed6a526`.

| Lauf | Ergebnis |
|---|---|
| `make gates FORCE=1` | Exit 0 in 34 s: Layer-Cache (die Stage `test` lief dabei nicht neu), also **kein** Beleg für die Tests (Cache-Falle G1), nur für die übrigen Gates und die Stempel (`gate-zuordnung`: 16 Gates, Selbsttest 33 Fälle) |
| `make clean-build` (`--no-cache`, Stages `build-jar`, `test`, `detekt`) | Exit 0 in 1288 s; Stage `test` frisch: 1183 Testfälle `PASSED`, 0 `FAILED` (717 s unter Last 8 bis 26); `detekt` grün |
| `make flaeche` | Exit 0 (3 s, Stage aus dem Cache; Liste in `SPEC-059` unverändert, kein neuer Typname) |
| `make docs-check` | Exit 0, 1183 Dateien, 0 Befunde (vor Anlage dieses Berichts). Er fängt weder V-1 noch V-2 |
| `make matrix-zitate` | Exit 0 (frische Stage `test`) |
| `make e2e` | Exit 0, 8 Stufen, 211 s. Stufe 1 meldet „1 s“: aus dem Cache (Muster `BEO-TDS/test-stage-cache-verdeckt-wiederholung`) |
| Stufe 1 frisch: `docker build --target smoke --no-cache-filter smoke --progress=plain .` | Exit 0; `ServerSmokeTest` (6) und `ZustandsereignisseProzessTest` (5) `PASSED` |
| `make spec-abdeckung`, Gradle-Stufe ohne Cache | gleiche Rezeptur wie das Target (`test-bericht-export` mit `--no-cache-filter test-bericht`, dann `spec-abdeckung-export` mit `--no-cache-filter spec-abdeckung`), Ausgabe in das Scratchpad statt in den Arbeitsbaum; Schritt `[spec-abdeckung 8/12] RUN gradle … test integratorTest` lief mit Ausgabe. Die erzeugte Matrix ist **byte-gleich** zu `docs/plan/spec-abdeckung.md` (`diff` leer), 6 Festlegungen ohne Nachweis (`SPEC-004`, `021`, `024`, `025`, `032`, `050`, keine aus diesem Slice). `SPEC-017`: 15, `SPEC-018`: 24, `SPEC-019`: 10, `SPEC-062`: 2 bestandene Tests |
| Schleifen `cleanTest`, je 3 Läufe, Last 10 bis 26: `MetricsTracingWiringTest`, `ObserverTest`, `SpanOutcomesDispatchTest`, `ZustandsereignisseTest`, `TestserverLogProcessTest`, `ListenerAbortTest`, `AuthTimingTest` | 3 von 3 grün (150 `PASSED`) |
| Schleifen `cleanIntegratorTest`, 3 Läufe: `TelemetryEmbeddingTest`, `TelemetryFelderEmbeddingTest` | 3 von 3 grün |
| Schleifen Modul `testserver`, 3 Läufe: `LogStartTest`, `TelemetryJsonTest`, `LogAdapterTest` | 3 von 3 grün |
| Lange Schleifen, je 10 Läufe (`MetricsTracingWiringTest` + `ObserverTest` + `SpanOutcomesDispatchTest` + `TestserverLogProcessTest`; `TelemetryEmbeddingTest` + `TelemetryFelderEmbeddingTest`) | 10 von 10 grün (330 `PASSED`) bzw. 10 von 10 grün; zusammen mit den 3er-Schleifen 13 Läufe je Klasse ohne Rot |

## Gesamturteil

0 HIGH, 4 MEDIUM, 3 LOW, 4 INFO. Die Zusage der DoD ist in der Sache **erfüllt**: ein Span endet in allen gefahrenen Pfaden genau einmal (Ausnahme `abandoned` benannt, getestet), kein Pfad trägt SQL-Text, Anmeldekennung oder Parameter in einem Attribut oder Schlüssel, die Schichtgrenze (`observability` importiert nur `domain`, `java.*`) und das Suppression-Verbot sind gehalten, `make clean-build` ist grün, die Matrix ist frisch gleich, und neun von elf Mutationen machen mindestens einen Test rot (die zwei grünen sind V-3 und V-8). Die Behebungen F-1 bis F-7 und F-11 des Reviews sind nachvollzogen (Abschnitt „Nachbesserung“). Die Auflösung des Merges ist in allen acht Punkten der Übergabeliste des Verifikationsberichts der Zustandsereignisse getragen; zwei Reste stammen aus dem Merge selbst (V-1 ein stehengebliebenes Duplikat des Slice der Zustandsereignisse in `open/`, V-2 ein Satz der Spezifikation, der ihr eigenes Gegenteil sagt) und einer ist der ungetestete Bulk-Load-Zweig (V-3). Haken: der Implementer hat keinen gesetzt, richtig; die Closure-Notiz steht aus („Wird bei der Closure gefüllt“).

## DoD-Punkte

| DoD-Zeile | Urteil | Beleg |
|---|---|---|
| Liefer-Punkt 1 **Spans** `tds.connection`, `tds.query`, `backend.execute` mit Pflicht-Attributen, Rohbytes-Lauf (Login, Anfrage, Fehler, Cancel) | erfüllt | `MetricsTracingWiringTest > Login, Anfrage, Fehler und Cancel erzeugen die Spans mit ihren Pflicht-Attributen` (M2, M14 rot), `SpanOutcomesDispatchTest` (Ausgänge an den Stellen der Schleife, ohne Netz), `TelemetryEmbeddingTest` über die öffentliche Fläche. Die Menge der `outcome`-Werte steht in der Spezifikation Abschnitt 5; die **Closure** muss sie tragen (Bedingung 1) |
| Liefer-Punkt 2 **Metriken und Schnappschuss**: Zähler, Verteilung (p50, p95, p99), Aggregation in `observability`, Ereignis `metrics`, Abstand, Prüfung in `config`, Testserver liest `METRICS_INTERVAL_SECONDS`, Kardinalität | erfüllt mit benannter Abweichung | `ObserverTest`, `LatencyHistogramTest`, `ConfigValidationTest > Bereichsgrenzen gelten auch ohne Lader` (M3 rot), `ConfigLoaderTest`, `TestserverLogProcessTest > Metriken und Span-Enden stehen als Zeilen`, Kardinalität `MetricsTracingWiringTest > Viele verschiedene SQL-Texte aendern die Zahl der Schluessel nicht` (200 Texte). **Abweichung vom Wortlaut:** der Abstand ist `RequestSettings.metricsIntervalSeconds`, kein Feld von `ServerConfig`; im Slice als „Abweichung vom Plan-Wortlaut“ benannt, in der Spezifikation geführt; die DoD-Zeile selbst nennt weiter „ein Feld der `ServerConfig`“ (V-4) |
| Liefer-Punkt 3 **Zugang über die Senke und Ausfallsicherheit**: Senke wirft, blockiert (akzeptiertes Negativ), Span genau einmal bei Cancel, Disconnect, Stromende, JSON-Adapter | erfüllt, Negativ in Spec und Nutzer-Doku benannt | `ThrowsOnMeasurements`/`ErrorOnMetrics` in `MetricsTracingWiringTest`, `MetricsTracingWiringTest > Eine Ausnahme des Handlers endet tds_connection mit error`, `stop beendet die offenen Spans und den Schnappschuss` (M4 rot), `TelemetryJsonTest`. Die blockierende Senke steht in Spezifikation Abschnitt 5 und Nutzer-Doku („Grenzen“); in die **Closure** gehört sie als akzeptiertes Negativ (Bedingung 1). Für den Bulk-Load-Pfad ist „genau einmal“ nicht belegt (V-3) |
| Nachweis: `@spec`-Tags, Tests laufen in `make test` und sind grün | erfüllt | Matrix frisch, byte-gleich, 0 Waisen aus diesem Slice; `clean-build` 1183 `PASSED` |
| Nachweis: Zuordnung *Test \| Festlegung* je Aussage in der Closure | **offen** | Closure-Notiz leer (Bedingung 1) |
| `make gates` grün | erfüllt | `make gates FORCE=1` Exit 0, dazu `make clean-build` (der Cache-Lauf allein wäre kein Beleg) |
| Review durchgeführt, Report liegt vor | **teilweise** | Report für `33f67126` liegt vor; die Nachbesserung `85a7c00b` und die Merge-Auflösung hatten keinen Review (hier wie ein Review bewertet, kein Re-Review in eigenem Kontext) |
| Doku-Update bei öffentlichem Vertrag | erfüllt mit Rest | Spezifikation, Nutzer-Doku, Matrix; Rest V-2; Übertragung in die neue Nutzer-Doku: Abschnitt „Übergabe an den Zweig der Nutzer-Doku“ |
| Closure-Notiz, Register, Risiken aus §6 mit Ausgang, drei Paarungen | **offen** | Closure leer; im Register steht kein Beleg dieses Slice außer `state.md` von `dod-nennt-pflichtfeld-das-die-spezifikation-als-zielzustand-fuehrt`; alle sieben Risiken „offen bis zur Closure“ (Vorschlag unten) |

## Plan-vs-Code-Diff (§3)

Verglichen: Diff `f0886534..4ed6a526` ohne die Anteile, die `main` mitbringt (Schreibpfad, Zustandsereignisse), also `git diff f5250140..4ed6a526 --name-only` für den Code.

| Plan-Zeile / Datei im Diff | Im Plan | Befund |
|---|---|---|
| `domain/telemetry/TelemetryEvent.kt`, `MeasurementEvents.kt`, `Instruments.kt` | ja | gebaut wie geplant; zwei `EventKind`, sieben Zugriffe, keine neuen öffentlichen Typnamen (`make flaeche` grün) |
| `observability/` (`Observer`, `Metrics`, `LatencyHistogram`) | ja | importiert nur `domain` und `java.*` |
| `protocol/dispatch/InstrumentedBackend.kt`, `RequestLoop.kt`, `RequestOutcomes.kt` | ja (`RequestOutcomes` seit der Nachbesserung) | `RequestLoop.answer` mit `try/finally` um die Verzweigung; Bulk-Zweig siehe V-3 und V-4 |
| `runtime/` (`ConnectionWiring`, `Bootstrap`, `TdsServer`) | ja | Zeitgeber in `Bootstrap.snapshots()`, `stop` beendet ihn |
| `config/` (`ServerConfig`, `ConfigValidation`) | ja, als „Feld der `ServerConfig`“ | gebaut als `RequestSettings.metricsIntervalSeconds`: **Abweichung vom Plan-Wortlaut**, im Slice benannt, Träger: Nutzer entscheidet über einen eigenen Typ (Slice) |
| `testserver/kotlin/` (`ConfigLoader`, `EnvKeys`, `TelemetryJson`) | ja | |
| `testserver/test/` (`ConfigLoaderTest`, `TelemetryJsonTest`) | ja (`testserver/test/`) | |
| `src/test/` (`ObserverTest`, `LatencyHistogramTest`, `SpanOutcomesDispatchTest`, `MetricsTracingWiringTest`, `TelemetryEventTest`, `TelemetryRedactionTest`, `ConfigValidationTest`, `EnvironmentConfig`, `TestserverLogProcessTest`) | ja | |
| `src/test/.../runtime/ZustandsereignisseTest.kt` | **nein** | angepasst durch den Merge (zwölfter Schlüssel, `SPAN_ENDED` in den Ereignisfolgen) |
| `domain/telemetry/EffectiveSettings.kt`, `runtime/TelemetryEvents.kt` | **nein** | angepasst durch den Merge (zwölftes Feld, Zuordnung); [ADR-0040](../plan/adr/0040-wirksame-konfiguration-im-ereignis-aus-typisiertem-wertobjekt.md) steht weder im Bezug noch in §3 |
| `src/integrator/` (`TelemetryEmbeddingTest`, `TelemetryFelderEmbeddingTest`) | ja | |
| `docs/plan/spec-abdeckung.md`, `spec/spezifikation.md`, `docs/user/einbettung-und-betrieb.md` | ja | |
| Kopf „Berührte Spec-Stellen“ | nennt `SPEC-017`, `018`, `019`, `062`, `052`, `059`, `044`, `048`, `009`, `011`, `030`, `ARC-009` | `SPEC-031` und `SPEC-033` sind in den neuen Tests von `SpanOutcomesDispatchTest` getaggt und fehlen im Kopf (V-4) |
| Abschnitt „Zuschnitt der Fläche“ / DoD | „ein Konstruktorparameter an `ServerConfig`“ | falsch (Abweichung steht darunter); gebaut: ein sechster Parameter von `RequestSettings` (öffentliche Mitglieder: V-5) |
| Bulk-Load als `sql_batch` beobachtet | **nicht im Slice** | nur in Spezifikation (Abschnitt 5), Nutzer-Doku und KDoc (V-4) |

## Nachbesserung `33f67126..85a7c00b` (Nachvollzug F-1 bis F-7, F-11, wie ein Review)

| Finding | Urteil | Beleg |
|---|---|---|
| F-1 (`abandoned`, `closed`, Abbildung des Laufendes ohne Test) | behoben, **Mutation rot** | `SpanOutcomesDispatchTest > Das Ende des Laufs ergibt das outcome von tds_query`, `… Ein Backend, das den Abbruch ignoriert, endet tds_query mit abandoned und backend_execute spaeter`, `… Eine Anfrage ohne vollstaendige Antwort endet mit closed`. M6 (`abandoned` als `ok`): zwei Tests rot; M10 (Voreinstellung `closed` als `ok`): ein Test rot; M14 (ATTENTION als `error`): zwei Tests rot |
| F-2 (Zweitfundstellen) | behoben, ein Rest neu gefunden (V-2) | Typen der Felder, Aufzählung der Zeichenketten-Felder, `SPEC-062` in der Tabelle der Defaults, Nutzer-Doku |
| F-3 (Plan-Tabelle, Kopf) | teilweise (V-4) | Tabelle um `RequestOutcomes`, `SpanOutcomesDispatchTest`, Integrator, Tests, Matrix ergänzt; Abweichung vom Plan-Wortlaut steht; nach dem Merge nicht auf Stand |
| F-4 (KDoc „wirft nicht und blockiert nicht“) | behoben | `Span.end` und `Observer` sagen: Ende auf dem Pfad des Aufrufers, `Exception` der Senke gefangen, `CancellationException` und `Error` laufen durch, eine blockierende Senke hält an; gleiche Aussage wie Spezifikation Abschnitt 5 |
| F-5 (Zeitgeber endet still bei `Error`) | behoben (benannt und getestet) | Spezifikation Abschnitt 5 und Nutzer-Doku („Abstand“) nennen es; `MetricsTracingWiringTest > Ein Error der Senke beendet den Zeitgeber, nicht den Server` (nach 2,3 s genau ein `METRICS`, Server antwortet weiter). Die Abwesenheit wird mit einer Wartezeit belegt (2,3 s bei Abstand 1 s): unter extremer Last könnte der Test zu früh grün sein, nie falsch rot |
| F-6 (Backend-Aufruf nach `abandoned` offen) | behoben | Ausnahme von „genau einmal“ in Spezifikation und Nutzer-Doku; Test mit offenem Backend-Span und spätem `ok` |
| F-7 (Atomizität des Endes) | **benannt, nicht gehalten** | `ObserverTest > Das Ende eines Spans ist unter Nebenlaeufigkeit atomar` (acht Threads, Barriere, 5000 Runden) tötet M1 (Setzen ohne Prüfung), **nicht** M11 (Prüfen-dann-Setzen, Exit 0); KDoc sagt „Stresstest, kein Beweis“ (V-8) |
| F-11 (Konsistenz des Schnappschusses) | behoben | `Metrics`-KDoc, Spezifikation und Nutzer-Doku nennen die Einzelablesung |
| F-8 bis F-10 (INFO) | übernommen in „Merge-Auflösung“ | |

## Merge-Auflösung `0d74eda0` und `4ed6a526` (wie ein Review)

| Punkt (Auftrag) | Urteil | Beleg |
|---|---|---|
| `RequestSettings`: `maxMessageBytes` fünfter, `metricsIntervalSeconds` sechster Parameter; alle Aufrufe benannt | erfüllt | Grep nach `RequestSettings(` in Haupt-, Test-, Testserver- und Integrator-Code (`.kt`, `.java`, `.cs`, `.kts`): jeder Aufruf mit Argumenten benannt (`ConfigLoader`, `EnvironmentConfig`, `ConfigValidationTest`, `TelemetryFelderEmbeddingTest`, `HandleLimitEmbeddingTest`); kein Java- oder C#-Aufruf im Repo. Der Testfall `Voreinstellungen sind gueltig …` benennt alle sechs Parameter |
| `EffectiveSettings` (12 Schlüssel, `asMap()`, Test gleich `ConfigKeys`) | erfüllt | zwölftes Feld `metricsIntervalSeconds`, Eintrag `METRICS_INTERVAL_SECONDS` in `asMap()`, Zuordnung in `ServerConfig.effectiveSettings()`; `ZustandsereignisseTest` (Liste der Schlüssel gleich den Namen der Konfiguration, Anzahl 12); M13 (Schlüssel entfernt): drei Tests rot. Die Schlüsselliste steht weiter zweimal (Literale in `EffectiveSettings` und `ConfigKeys`), von einem Test gehalten (V-6 des Verifikationsberichts der Zustandsereignisse, unverändert) |
| `TdsServer.stop`: `snapshots?.cancelAndJoin()` im `withLock`-Block nach `SERVER_STOPPING`, vor `listener.shutdown()`; Deadlock | erfüllt, eine Kante unbenannt (V-7) | Code wie verlangt; Zeitgeber und Span-Ende rufen `stop` nicht; die Tests `Zwei nebenlaeufige stop warten aufeinander …` und `Ein abgebrochener stop meldet das Ende erst mit dem naechsten Aufruf` sind grün (3 Läufe); M4 (Aufruf entfernt) rot |
| `RequestLoop.answer`: `try/finally` des Spans um Gate, RPC, Bulk-Load, Batch; Span endet in allen Pfaden genau einmal | erfüllt für Gate, RPC, Batch, Unprepare, `closed`; **Bulk-Load ungetestet** (V-3) | `finally { span.end(queryOutcome ?: CLOSED) }`; `Malformed` setzt `PROTOCOL_ERROR`; ATTENTION über `RunEnd.Cut`. M12 (`Malformed` als `ok`): **grün**, kein Test |
| `RequestKind` zwei Werte, Bulk-Load als `sql_batch` | Festlegung benannt, schwach begründet, ohne Träger im Slice (V-4, V-5) | Spezifikation Abschnitt 5, Nutzer-Doku („ein Bulk-Load-Paket zählt als `sql_batch`“), KDoc; `TelemetryEvent.requestKind` ist ein öffentliches `String?`, ein dritter Wert wäre kein neuer Typname |
| Ereignisfolgen der Tests ohne Lockerung angepasst | erfüllt | Diff der Erwartungen gegen `f5250140`: `ZustandsereignisseTest` (zwei `SPAN_ENDED` in der Folge, drei Arten bei `stop`), `TelemetryEmbeddingTest` (drei `SPAN_ENDED`, Ebenen `DEBUG`), `TestserverLogProcessTest` (zwei `span_ended`); keine Prüfung entfernt, die Zusage „Beginn, Ende der Verbindung, Ende“ bleibt eine Zusage über die Zustandsarten |
| `TelemetryRedactionTest`: `Map` nur für `metrics` und `settings`, Reflexionstest nicht gelockert | erfüllt | `MAPS = setOf("metrics", "settings")`; `STRINGS_BY_KIND` führt `SpanEnded` mit fünf Feldern; M5 (SQL-Text als Kennung des Spans) lässt den Test rot werden |
| `TelemetryJson`: `when` und `covered` | erfüllt | `METRICS`, `SPAN_ENDED` im `when`; `metrics.<Schlüssel>` flach, `settings` weiter je Schlüssel; `TelemetryJsonTest` grün (3 Läufe) |
| `ConnectionWiring`: `sessionLimits`, `MessageLimitWiringTest` | erfüllt | `MessageLimitWiringTest` in `clean-build` grün |
| `harness/README.md`: Zelle `make smoke` bei 200 Zeichen | erfüllt | unverändert |
| Planung (Roadmap, Slices, Welle) | **Rest** | V-1 |

## Mutationen (selbst gefahren, je Kopie, Docker-Lauf der genannten Klassen)

| Mutation | Wirkung gemessen |
|---|---|
| M1 `Tracked.end`: Sperre `compareAndSet` durch `ended.set(true)` (Span endet mehrfach) | rot: `ObserverTest > Ein Span endet genau einmal`, `… Das Ende eines Spans ist unter Nebenlaeufigkeit atomar` |
| M2 `ObservedExecution.outcome`: Fehler im Strom als `ok` | rot: `SpanOutcomesDispatchTest > backend_execute endet mit close der Ausfuehrung`, `MetricsTracingWiringTest > Login, Anfrage, Fehler und Cancel …` |
| M3 `ConfigValidation`: Bereich `0..Int.MAX_VALUE` statt `0..86400` (Intervall ohne Obergrenze) | rot: `ConfigValidationTest > Bereichsgrenzen gelten auch ohne Lader` |
| M4 `TdsServer.stop`: Zeitgeber wird nicht beendet | rot: `MetricsTracingWiringTest > stop beendet die offenen Spans und den Schnappschuss` |
| M5 `RequestLoop.answer`: SQL-Text der Nachricht als Kennung des Spans (SQL-Text in einem Attribut, [TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit)) | rot, fünf Tests: `MetricsTracingWiringTest` (RPC-Art, Kardinalität, Pflicht-Attribute), `SpanOutcomesDispatchTest > Handle-Freigabe und RPC-Abweisung nennen ihr Ende`, `TelemetryRedactionTest > SQL-Text, Parameterwert und Ausnahmetext erreichen kein Ereignis` |
| M6 `RunEnd.Abandoned` als `ok` | rot: zwei Tests von `SpanOutcomesDispatchTest` |
| M10 Voreinstellung `closed` als `ok` | rot: `SpanOutcomesDispatchTest > Eine Anfrage ohne vollstaendige Antwort endet mit closed` |
| M11 `Tracked.end`: Prüfen-dann-Setzen statt `compareAndSet` | **grün** (V-8) |
| M12 `RequestLoop.answer`: `Malformed` des Bulk-Load als `ok` statt `protocol_error` | **grün** (V-3) |
| M13 `EffectiveSettings.asMap()`: Schlüssel `METRICS_INTERVAL_SECONDS` entfernt | rot: drei Tests von `ZustandsereignisseTest` |
| M14 `RunEnd.spanOutcome`: ATTENTION als `error` statt `cancelled` | rot: `SpanOutcomesDispatchTest > Das Ende des Laufs ergibt das outcome von tds_query`, `MetricsTracingWiringTest > Login, Anfrage, Fehler und Cancel …` |

Nicht gefahren: Kardinalität über einen Schlüssel der Metriken aus Eingabe (der Test liest `toString` der Abbildung samt Schlüsseln; M5 zeigt die Wirkung für Text im Attribut); eine blockierende Senke (die Grenze ist benannt, nicht getestet, laut DoD richtig so).

## Entscheidungs-Konformität

| Entscheidung | Urteil |
|---|---|
| [ADR-0020](../plan/adr/0020-logformat-und-observability-schnittstelle.md) Fragen 2 und 6 (Schnittstelle in `domain`, Aggregation in `observability`, Schnappschuss und Span-Ende als Ereignisse an die Senke, kein zweiter Parameter, kein Telemetrie-Port, keine Fremdbibliothek) | konform: `Instruments` in `domain`, `Observer` nur `domain`-Importe, Zugang ist die Senke `TelemetrySink`; `Bootstrap` ist die einzige Stelle außerhalb von `observability`, die `Observer` kennt |
| [ADR-0040](../plan/adr/0040-wirksame-konfiguration-im-ereignis-aus-typisiertem-wertobjekt.md) (Accepted, Option D) | konform: `EffectiveSettings` nur Zahlen, Aufzählungen, `Boolean`; zwölf Schlüssel; Test gleich `ConfigKeys`; Reflexionstest nicht gelockert |
| [ADR-0021](../plan/adr/0021-oeffentlicher-integrations-einstieg.md) / `SPEC-059` (Fläche) | konform nach benannter Grenze: `make flaeche` grün, 61 Typnamen; neue öffentliche Mitglieder (zwei `EventKind`, sieben Zugriffe, sechster `RequestSettings`-Parameter); Java: die Konstruktoren `RequestSettings(int, …)` mit weniger als sechs Parametern entfallen (kein `@JvmOverloads`; Kotlin nutzt benannte Argumente und Voreinstellungen) |
| [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 4 (Lesart öffentliche Namen) | die Lesart „Konstruktoren, Parameter und Felder sind Namen“ trifft hier zu; der Slice-Absatz „Zuschnitt der Fläche“ nennt Zugriffe und Parameter, aber ohne Architect-Frage und ohne Träger (V-5) |
| `AGENTS.md` §3.1 (Docker-only), §3.2 (Suppression-Verbot), §3.7 (Kommentar), §3.8 (Aussage trägt so weit wie der Beleg) | §3.1 und §3.2 konform (`make detekt` grün, kein `@Suppress`); §3.8: V-6 (`unveränderliche Kopie`), V-4 (Begründung zu `sql_batch` breiter als der Beleg) |

## Sicherheit und Ressourcen

* Keine Geheimnisse in Attributen und Metriken: `SpanEnded` trägt `span`, `outcome`, `durationMs`, `requestId` (gekürzt auf 256 Einheiten), `phase`, `requestKind` und den `EventContext` mit `connectionId`, `sessionId`, Profil; die Anmeldekennung steht nur in `LOGIN_ACCEPTED`. Die Schlüssel der Metriken sind feste Namen (`MetricKeys`); der Redaktionstest und der Kardinalitätstest halten es, M5 rot.
* Blockierende Senke: hält Span-Ende und damit den Anfragepfad an; akzeptiertes Negativ, in Spezifikation Abschnitt 5 und Nutzer-Doku benannt, nicht getestet ([TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität)); sie hält außerdem `stop` an, wenn sie im Zeitgeber blockiert (V-7).
* Overhead der Telemetrie ungemessen; Träger `slice-qa-performance` und `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` (Spezifikation: „Kosten … nicht gemessen“). Der Slice sagt dazu nichts darüber hinaus.
* Der Zeitgeber läuft auf `Dispatchers.Default` (daemon-Threads), endet mit `stop`; ohne `stop` bleibt die Aufgabe bis zum Prozessende (Grenze, mit der Pflicht zum Aufruf von `stop` gedeckt).

## Findings

### V-1 MEDIUM — Die Auflösung des Merges lässt ein Duplikat des Slice der Zustandsereignisse in `open/` stehen

* quelle: `AGENTS.md` §3.3 (Lifecycle: der Zustand ist das Verzeichnis); Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine
* pfad: `docs/plan/planning/open/slice-betrieb-zustandsereignisse.md` (171 Zeilen) neben `docs/plan/planning/done/slice-betrieb-zustandsereignisse.md`
* befund: `git diff --name-status f5250140 HEAD -- docs/plan/planning` zeigt `A docs/plan/planning/open/slice-betrieb-zustandsereignisse.md`: `main` hat den Slice per `git mv` nach `done/` geschlossen, dieser Zweig hat die Datei in `open/` mit den Verweiskorrekturen auf seinen Slice geändert (Commits `325ab47a`, `ef1d41fa`), die Auflösung des Konflikts (Änderung gegen Löschung) hat sie behalten. Der Slice liegt damit in zwei Zuständen; `make docs-check` meldet nichts (0 Befunde).
* verifizierbar: ja (`git ls-files docs/plan/planning/open | grep zustandsereignisse`)
* klasse: Konflikt „geändert gegen gelöscht“ zugunsten der Änderung aufgelöst

### V-2 MEDIUM — Spezifikation Abschnitt 4 sagt in einem Satz das Gegenteil von sich selbst

* quelle: [TDS-FA-09](../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), `AGENTS.md` §3.8; Fortschreibung lässt die zweite Fundstelle stehen (dritte Wiederholung dieser Klasse in dieser Welle)
* pfad: `spec/spezifikation.md` · „**Zielzustand, nicht gebaut:** Anfrage beendet (mit Art, Dauer und Ergebnis), Abbruch durch ATTENTION, Schnappschuss der Metriken und Ende eines Spans (Abschnitt 5).“ (Zeile 1338 f., Ende des Absatzes zu den Ereignisarten)
* befund: derselbe Absatz führt wenige Zeilen davor `METRICS` und `SPAN_ENDED` als Arten mit Feldern und Ebenen auf, Abschnitt 1 (Zeile 154) und Abschnitt 5 nennen beide als gebaut. Der Satz stammt aus dem Text der Zustandsereignisse auf `main` (dort stimmte er) und wurde im Merge nicht fortgeschrieben.
* verifizierbar: ja (Grep nach „Schnappschuss der Metriken und Ende eines Spans“; `make docs-check` fängt es nicht)
* klasse: Fortschreibung lässt die zweite Fundstelle stehen

### V-3 MEDIUM — Der Bulk-Load-Zweig von `RequestLoop.answer` ist für Spans nicht getestet

* quelle: DoD Liefer-Punkt 3 („ein Span wird auch bei Cancel, Disconnect und Stromende genau einmal beendet“); `AGENTS.md` §3.8; Reviewer-Regel „Eigencode, dessen Entfernung keinen Test rot macht“
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/RequestLoop.kt` · „queryOutcome = SpanOutcome.PROTOCOL_ERROR“ im Zweig `message.type == PacketType.BULK_LOAD`; `execute("", emptyList(), layout = null, via = loaded.via)`
* befund: Kein Test in `SpanOutcomesDispatchTest`, `MetricsTracingWiringTest`, `ObserverTest`, `TelemetryEmbeddingTest` oder `TestserverLogProcessTest` sendet ein Paket 0x07 (Grep nach `bulk` in diesen Dateien: kein Treffer). M12 (`Malformed` als `ok`) lässt alle Tests grün. Ungetestet sind: `outcome` bei zerlegtem Bulk-Load (`protocol_error`), `request_kind` = `sql_batch` für Typ 0x07, der Span `backend.execute` über `bulk.statement(text)` und `loaded.via` (`via.observed(session)`), das Ende des Spans im Bulk-Load bei Abbruch. Der Merge hat diesen Zweig zusammengeführt; er trägt die Aussage „Span endet in allen Pfaden genau einmal“ nicht belegt.
* verifizierbar: ja (M12, Grep)
* klasse: Eigencode ohne rot werdenden Test

### V-4 MEDIUM — Plan und Slice sind nicht auf dem Stand des Merges; die Festlegung `sql_batch` für Bulk-Load steht nicht im Slice

* quelle: Reviewer-Regel „Plan-Tabelle nennt die berührten Dateien nicht“; `AGENTS.md` §3.8; Maßstab „Abweichungen sichtbar mit Träger“
* pfad: `docs/plan/planning/done/slice-betrieb-metriken-tracing.md` · „Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.“; Kopf „Bezug“ und „Berührte Spec-Stellen“
* befund: (a) §3 nennt `EffectiveSettings.kt`, `runtime/TelemetryEvents.kt`, `ZustandsereignisseTest.kt` und den Bulk-Zweig in `RequestLoop` nicht; „Bezug“ nennt [ADR-0040](../plan/adr/0040-wirksame-konfiguration-im-ereignis-aus-typisiertem-wertobjekt.md) nicht, obwohl der zwölfte Schlüssel aus ihr folgt. (b) „Berührte Spec-Stellen“ nennt `SPEC-031` und `SPEC-033` nicht (getaggt in `SpanOutcomesDispatchTest`). (c) Die Entscheidung, Bulk-Load als `sql_batch` zu führen, steht in Spezifikation, Nutzer-Doku und KDoc, nicht in „Abweichung vom Plan-Wortlaut“ und nicht in §6. Die Begründung der Spezifikation („ein dritter wäre eine Erweiterung der öffentlichen Marken“) trägt nicht weit: `requestKind` ist ein öffentliches `String?`, ein dritter Wert wäre kein neuer Typname und bräuchte keine Änderung der Fläche nach `SPEC-059`; für den Betreiber ist ein Massenladen als `sql_batch` unehrlich gemessen (Dauer und Zeilenzahl eines Ladens mischen sich in die Verteilung der Batches). (d) Die DoD-Zeile 2 nennt weiter „Feld der `ServerConfig`“.
* verifizierbar: ja (`git diff f5250140..HEAD --name-only` gegen §3)
* klasse: Plan nennt die berührten Stellen nicht (zweite Wiederholung nach den Zustandsereignissen); Festlegung ohne Träger

### V-5 LOW — Öffentliche Mitglieder und offene Nutzerentscheidungen haben keinen Träger außerhalb des Slice

* quelle: Maßstab „Abweichungen sichtbar mit Träger“; [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 4
* pfad: `docs/plan/planning/done/slice-betrieb-metriken-tracing.md` · „Der Nutzer entscheidet, ob ein eigener Typ folgt.“ und §6 letztes Risiko („entscheidet er mit dem Nutzer“)
* befund: Drei Entscheidungen sind offen und tragen nur den Satz im Slice: (1) sechster Parameter von `RequestSettings` statt eines eigenen Typs (Java: Konstruktoren mit weniger als sechs Parametern entfallen), (2) `REQUEST_FAILED` ohne `requestId` und kein Ereignis „Anfrage beendet“ (Spezifikation: „Zielzustand, nicht gebaut“), (3) Bulk-Load als `sql_batch` (V-4). Die Beobachtung `BEO-TDS/oeffentliche-flaeche-der-telemetrie-ohne-entscheidung` steht auf „gestrichen“; die Closure muss sagen, ob sie mit den neuen Zugriffen wieder zählt. Ohne eine Architect-Frage oder einen Folge-Slice als Träger gilt §6 nicht als aufgelöst.
* verifizierbar: nein (Lesart)
* klasse: Entscheidung ohne Träger

### V-6 LOW — „unveränderliche Kopie“ ist durch den Typ nicht belegt

* quelle: `AGENTS.md` §3.8
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/domain/telemetry/MeasurementEvents.kt` · „`metrics` ist eine\n * unveränderliche Kopie der Messwerte“
* befund: Die Zuweisung ist `metrics.toMap()`; für mehr als einen Eintrag liefert Kotlin ein `LinkedHashMap`, das ein Java-Aufrufer oder ein Cast verändern kann. `ConfigEffective` schützt `settings` mit `Collections.unmodifiableMap`; für `metrics` fehlt es, und kein Test hält die Aussage. Folge gering (eine Senke, ein Empfänger), die Zusage steht aber im KDoc.
* verifizierbar: ja (Test mit Cast auf `MutableMap`)
* klasse: Zusage breiter als ihr Beleg

### V-7 LOW — `stop` kehrt nicht zurück, solange die Senke im Zeitgeber blockiert; nicht benannt

* quelle: `AGENTS.md` §3.8; [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität)
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/runtime/TdsServer.kt` · „snapshots?.cancelAndJoin()“ (innerhalb von `stopLock.withLock`)
* befund: `cancelAndJoin` wartet, bis die Aufgabe endet; steckt sie in einem blockierenden `accept` der Senke (nicht abbrechbar), hält `stop` die Sperre und erreicht `listener.shutdown()` nicht; weitere `stop`-Aufrufe warten. Die Spezifikation nennt für die blockierende Senke den Anfragepfad und das Span-Ende, nicht das Beenden; die KDoc von `stop` sagt „eine Senke blockiert ohnehin nicht“. Nicht gefahren (aus dem Code gelesen).
* verifizierbar: ja (Test mit blockierender Senke bei `METRICS` und einer Zeitgrenze)
* klasse: Folge einer Festlegung am Nebenpfad nicht benannt

### V-8 INFO — Der Stresstest der Atomizität tötet die Prüfen-dann-Setzen-Lücke nicht

* quelle: Review F-7
* pfad: `src/test/kotlin/io/github/pt9912/tdswire/observability/ObserverTest.kt` · „Ein Stresstest, kein Beweis“
* befund: M11 bleibt grün (Exit 0) trotz acht Threads, Barriere und 5000 Runden unter Last 10 bis 26. Die KDoc nennt das Verfahren ehrlich als nicht deterministisch; die Behebung von F-7 ist damit „benannt“, nicht „gehalten“. Eine deterministische Prüfung (Hook zwischen Lesen und Setzen) ist nicht gebaut.
* verifizierbar: ja (M11)
* klasse: Atomizität nur probabilistisch getestet

### V-9 INFO — Cache-Hinweise der Läufe

* befund: `make gates FORCE=1` (34 s) und `make e2e` Stufe 1 (1 s) kamen aus dem Layer-Cache; die Tests belegen `make clean-build`, die Stufe 1 der frische Lauf der Stage `smoke` oben. Wer nur `make gates` liest, sieht diese Belege nicht.
* klasse: Cache verdeckt Wiederholung (`BEO-TDS/test-stage-cache-verdeckt-wiederholung`)

### V-10 INFO — Zweitfundstellen der Nutzer-Doku im Zweig `worktree-agent-a9764e63162fbe637` (Übergabe)

* befund: siehe Abschnitt „Übergabe an den Zweig der Nutzer-Doku“.

### V-11 INFO — Haken, Closure, Register, Review

* befund: Keine Haken gesetzt (richtig, die Closure-Notiz steht aus). Das Register ist nicht fortgeschrieben (Bedingung 4). Der Review `33f67126` deckt die Nachbesserung und den Merge nicht; dieser Bericht ersetzt keinen Re-Review in eigenem Kontext (Bedingung 5).

## Übergabe an den Zweig der Nutzer-Doku (`worktree-agent-a9764e63162fbe637`)

Dieser Zweig ändert `docs/user/einbettung-und-betrieb.md` an fünf Stellen (Stand `4ed6a526`); der andere Zweig teilt die Datei auf und enthält zu Metriken und Spans heute **nichts** (Grep nach `Metrik`, `Span`, `METRICS`, `request_id` in seinen vier Dateien: keine Treffer außer `SERVER_STOPPED`). Nach seinem Merge sind zu übertragen:

1. **`referenz-konfiguration.md`, Tabelle „Konfigurationsfelder“:** neue Zeile `requests.metricsIntervalSeconds` | `METRICS_INTERVAL_SECONDS` | 60 s | 0 bis 86400, 0 = aus | `SPEC-062` (hinter `requests.maxMessageBytes`).
2. **`referenz-ereignisse.md`, Tabelle „Ereignisarten“:** `METRICS` (INFO, `metrics`) und `SPAN_ENDED` (DEBUG, `span`, `outcome`, `durationMs`, `connectionId`, `sessionId`, `requestId`, `requestKind`, `phase`); im Absatz „Die übrigen Felder gehören zur Art und sind `null` …“ die Namen `span`, `outcome`, `durationMs`, `requestId`, `requestKind`, `phase`, `metrics` ergänzen; den Satz „Zum Vertrag der Senke gehört …“ um „Das Ende eines Spans läuft auf dem Anfragepfad: eine blockierende Senke hält es an“ ergänzen.
3. **`referenz-ereignisse.md`, Abschnitt „Log des Testservers“, Aufzählung zu `LOG_LEVEL`:** der Satz „Am Prozess `main` des Testservers entsteht kein Ereignis der Ebene `DEBUG` …“ ist nach diesem Zweig falsch: `LOG_LEVEL=DEBUG` zeigt dort das Ende der Spans (`span_ended`), `INFO` zeigt es nicht; dazu die Zeilen `metrics` mit Feldern `metrics.<Schlüssel>` (Beispielzeilen sind keine „echten“ mehr, wenn sie `span_ended` oder `metrics` auslassen: neu aufnehmen oder mit Stand kennzeichnen).
4. **`testserver-manuell.md`, Satz „Mit `LOG_LEVEL=DEBUG` erscheint im Handlauf kein weiteres Ereignis (gemessen am 2026-11-06 …)“** (Abschnitt „Das Log lesen“, Zeile 263 in `worktree-agent-a9764e63162fbe637`): falsch nach diesem Zweig; dazu die Tabelle der Einstellungen (Zeile „Log-Ebene“ bei 299): `METRICS_INTERVAL_SECONDS` als Eingang des Testservers (Ziffern, 0 = aus, Voreinstellung 60).
5. **`einbettung-und-betrieb.md`:** der Abschnitt „Metriken und Spans“ (Spans mit `outcome`, Metriken mit Schlüsseln, Abstand, Grenzen: blockierende Senke, Overhead ungemessen, `abandoned` verlängert `backend.execute`, `Error` der Senke beendet den Zeitgeber, kein `request_id` in `REQUEST_FAILED`) in den Abschnitt „Ereignisse lesen“ und in die Liste „Grenzen“; der Satz „Nicht gebaut: Metriken, Spans, `request_id`, …“ darf nicht (wieder) auftauchen.
6. Sensor: `make docs-check` fängt keine dieser Stellen; Grep nach `DEBUG`, `Nicht gebaut` und `Metrik` in `docs/user/`; `AnleitungTest` des anderen Zweigs prüft Ereignisse mit `containsAll` und bricht durch `SPAN_ENDED` nicht.

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `src/main/.../observability/` (Observer, Metrics, LatencyHistogram) | geprüft, ohne Befund über V-8 hinaus; Importe nur `domain` und `java.*` |
| `src/main/.../domain/telemetry/` (`Instruments`, `MeasurementEvents`, `TelemetryEvent`, `EffectiveSettings`) | geprüft, ohne Befund über V-6 hinaus; kein Feld für SQL-Text, Anmeldekennung, Parameter |
| `src/main/.../config/` (`ServerConfig`, `ConfigValidation`) | geprüft, ohne Befund; Bereich 0 bis 86400, Voreinstellung 60, M3 rot |
| `src/main/.../runtime/` (`Bootstrap`, `ConnectionWiring`, `TdsServer`, `TelemetryEvents`) | geprüft, ohne Befund über V-7 hinaus; `Span` des Handlers endet in `finally`, `CancellationException` führt zu `cancelled` |
| `testserver/kotlin/` (`ConfigLoader`, `EnvKeys`, `TelemetryJson`) | geprüft, ohne Befund |
| Positionale Aufrufe von `RequestSettings` | geprüft, keiner (Haupt-, Test-, Testserver-, Integrator-Code; kein Java, kein C#) |
| Suppression-Verbot (`@Suppress` in `src`, `testserver`) | geprüft, ohne Befund (`make detekt` in `clean-build` grün) |
| `harness/README.md`, `AGENTS.md`, `harness/conventions.md` | im Diff unverändert; keine Behauptung eines nicht vorhandenen Targets neu eingeführt |
| Roadmap | verweist auf `done/slice-betrieb-metriken-tracing.md` und `done/slice-betrieb-zustandsereignisse.md`, beide Pfade existieren |

## Risiken aus §6 (Vorschlag der Ausgänge, Entscheid beim Planner)

* „Abbrüche“ und „Protokollfehler“: entfallen als Lücke; [ADR-0027](../plan/adr/0027-abbruch-und-zuruecksetzen.md) steht im Code, `aborts_total` zählt `cancelled` und `timeout`, `protocol_errors_total` zählt `protocol_error`; Festlegung in Spezifikation Abschnitt 5, Tests `ObserverTest`, `MetricsTracingWiringTest`.
* Blockierende Senke, Kosten des Ereignisbaus: weiter offen → `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`, geplant bei `slice-qa-performance`; Zusatz V-7 (`stop`).
* Adapter trifft keine fachliche Entscheidung: entfallen, soweit `import` es zeigt (SPEC-041); `RequestLoop` entscheidet nichts anhand der Beobachtung.
* Overhead unter Last: weiter offen, gleicher Träger.
* Grenzen des Histogramms sind eine Setzung: eingetreten und getragen (Spezifikation nennt „Setzung, nicht gemessen“, Perzentil = Klassengrenze).
* Kardinalität mit variierten Texten: entfallen, Test variiert (200 Texte); die Mutation des Schlüssels aus Eingabe ist nicht gefahren (oben).
* `request_id` in `REQUEST_FAILED`, Ereignis „Anfrage beendet“: weiter offen, mit Träger (V-5, Nutzerentscheid).

## Bedingungen vor der Closure

1. **Closure-Notiz (Planner):** Zuordnung *Test | Festlegung* je Aussage; die Menge der `outcome`-Werte (Spezifikation Abschnitt 5); das akzeptierte Negativ der blockierenden Senke; die Abweichung `RequestSettings` statt `ServerConfig`; Steering-Loop-Lerneintrag; Ausgänge aller sieben Risiken; Finding-Klassen des Reviews und dieses Berichts (Fortschreibung lässt die zweite Fundstelle stehen · Plan nennt die berührten Stellen nicht · Eigencode ohne rot werdenden Test · Konflikt geändert gegen gelöscht zugunsten der Änderung aufgelöst · Atomizität nur probabilistisch getestet).
2. **V-1:** `git rm docs/plan/planning/open/slice-betrieb-zustandsereignisse.md` (Docs-only) und die Verweiskorrekturen aus `325ab47a` und `ef1d41fa` in der Fassung unter `done/` nachtragen, soweit sie dort fehlen.
3. **V-2:** den Satz in Abschnitt 4 um „Schnappschuss der Metriken und Ende eines Spans“ kürzen (Docs-only).
4. **V-3:** ein Test mit Paket 0x07 (zerlegter und gültiger Bulk-Load): `protocol_error` und Ende genau einmal, `sql_batch`, ein `backend.execute` mit der Kennung der Anfrage; M12 muss rot werden. Alternativ die Aussage „in allen Pfaden“ in Spezifikation und Nutzer-Doku für Bulk-Load einschränken und mit Träger führen.
5. **V-4 und V-5:** Plan-Tabelle, Kopf und „Bezug“ auf den Stand des Merges bringen ([ADR-0040](../plan/adr/0040-wirksame-konfiguration-im-ereignis-aus-typisiertem-wertobjekt.md), `EffectiveSettings.kt`, `TelemetryEvents.kt`, `ZustandsereignisseTest`, Bulk-Zweig, `SPEC-031`, `SPEC-033`); die Festlegung `sql_batch` für Bulk-Load in „Abweichung vom Plan-Wortlaut“ aufnehmen und als Architect-Frage mit Träger führen (alternativ den Wert `bulk_load` einführen: Änderung der Spezifikation, der Marke und eines Tests, keine Änderung der Typnamen).
6. **Register (Planner):** Beobachtungen fortschreiben (Fortschreibung lässt die zweite Fundstelle stehen; Plan nennt die berührten Stellen nicht; Nachbesserung ohne zweiten Review; Konflikt geändert gegen gelöscht; Atomizität nur probabilistisch), `BEO-TDS/oeffentliche-flaeche-der-telemetrie-ohne-entscheidung` neu bewerten.
7. **Review:** die Nachbesserung `33f67126..85a7c00b` und die Merge-Auflösung brauchen einen Re-Review in eigenem Kontext oder die ausdrückliche Entscheidung des Planners, diesen Bericht dafür gelten zu lassen.
8. **Haken** setzt der Planner nach Deckung durch diesen Bericht (aktuell keiner gesetzt, richtig).
9. **Nutzer-Doku:** die sechs Übertragungsstellen oben sind Pflicht für den Zweig, der als zweiter merged (Reihenfolge: dieser Zweig vor dem der Nutzer-Doku).
10. Nicht Bedingung, empfohlen: V-6 (`Collections.unmodifiableMap` oder die Aussage kürzen), V-7 (ein Satz in der KDoc von `stop`, besser ein Test mit Zeitgrenze), V-8 (deterministischer Test oder Benennung in der Closure als Grenze).

## Summary

Slice `slice-betrieb-metriken-tracing` auf `4ed6a526`: 0 HIGH, 4 MEDIUM (V-1 bis V-4), 3 LOW (V-5 bis V-7), 4 INFO (V-8 bis V-11). Die drei Liefer-Punkte sind in der Sache erfüllt und mit eigenen Läufen belegt (`make clean-build` Exit 0 mit 1183 Testfällen, `make flaeche`, `make docs-check`, `make matrix-zitate`, `make e2e` mit frischer Stufe 1, Matrix byte-gleich, Schleifen grün, neun von elf Mutationen rot). Die Behebungen des Reviews sind nachvollzogen; die Auflösung der 14 Codekonflikte des Merges ist in allen acht Übergabepunkten getragen. Vor der Closure fehlen: das Entfernen des durch den Merge zurückgebrachten Duplikats in `open/` (V-1), ein Satz der Spezifikation (V-2), ein Test des Bulk-Load-Zweigs (V-3), der Plan auf Stand des Merges samt Träger für `sql_batch` (V-4, V-5), die Closure-Notiz mit Register und die Übertragung in die neue Nutzer-Doku.
