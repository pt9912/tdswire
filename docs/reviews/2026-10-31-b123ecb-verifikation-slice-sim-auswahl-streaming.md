# Verifikation slice-sim-auswahl-streaming (Modul 11)

**Gegenstand:** [slice-sim-auswahl-streaming](../plan/planning/in-progress/slice-sim-auswahl-streaming.md) (Welle welle-simulations-backend) nach Umsetzung f65f486 und 72a3b04 sowie Fix-Runde 31ae343, Stand `b123ecb` (Merge der Fix-Runde). Bezug: [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-08](../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität), [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md), [ADR-0013](../plan/adr/0013-backend-grenzen.md), [SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) bis SPEC-039.
**Rolle:** Verifier (gegen DoD, Spec und ADR; kein Diff-Review). Eingang: Review [2026-10-31-7c8917d-slice-sim-auswahl-streaming.md](2026-10-31-7c8917d-slice-sim-auswahl-streaming.md) (F-1 bis F-7) und die Fix-Runde.
**Methode:** alle Läufe Docker-only; Mutationen im Quelltext des Worktrees, je danach zurückgesetzt (`git status` sauber); ein Docker-Build zur Zeit. Ausgaben der Läufe lagen im Scratchpad, nicht im Repo.

## Urteil

Die Umsetzung ist **bestätigt**: alle drei Liefer-Punkte der DoD sind an Code und bestandenen Tests belegt, die sieben Findings des Reviews sind behoben (F-1, F-3, F-4 durch Mutation nachgewiesen, F-2, F-5, F-6, F-7 durch Spec oder KDoc). Keine Befunde der Klassen HIGH oder MEDIUM, zwei LOW. Der Slice ist **noch nicht schließbar**, weil die Closure-Pflichten (Zeilen 4 bis 10 unten) erst bei der Closure entstehen; das sind keine Mängel der Umsetzung.

## Eigene Läufe

| Lauf | Ergebnis |
|---|---|
| `docker build --no-cache-filter test --target test --progress=plain .` auf `b123ecb` | Exit 0, `BUILD SUCCESSFUL in 1m 26s`, 738 Tests, keine rote Zeile; die Stage lief frisch (Gradle-Ausgabe im Lauf). `Generator laeuft waehrend der Wartezeit des Lesers nicht voraus` PASSED an beiden Backends, `der Formatraum der Zeit ist der der ISO-Parser` PASSED, `SimulationMemoryTest` PASSED. |
| `make gates` | Exit 0 (die Stage `test` aus dem Cache des Laufs davor; Frischbeleg ist der Lauf in der Zeile darüber) |
| `make docs-check` | nach Anlage dieses Berichts, siehe Ende |

## Mutationen

| # | Mutation | Erwartung | Ergebnis |
|---|---|---|---|
| M1 | `SelectExecution.events` um `.buffer(64)` | Suite-Test am Simulations-Backend rot | **gefangen**: rot `SimulationBackendContractTest > Generator laeuft waehrend der Wartezeit des Lesers nicht voraus`, dazu `SimulationBackendSourceTest > die Quelle wird nur auf Abnahme gelesen`, `… > ein abbrechender Sammler beendet das Lesen und schliesst die Quelle`, `SimulationMemoryTest`; 4 von 738 rot. Der ältere Suite-Test `Generator laeuft nicht ueber die Abnahme hinaus` blieb in diesem Lauf grün. |
| M1b | Test-Backend: `ScriptedBackend.generating` mit `.buffer(64)` (vorauslesend) | Suite-Test am Test-Backend rot | **gefangen**: rot `ScriptedBackendContractTest > Generator laeuft waehrend der Wartezeit des Lesers nicht voraus` (und, als Folge, `SqlBatchDispatchTest > Der Anfragezustand laeuft von READY ueber RUNNING nach STREAMING`); `Generator laeuft nicht ueber die Abnahme hinaus` blieb grün. F-1 ist damit behoben: der neue Test fängt, was der alte nicht fing, an beiden Implementierungen. |
| M2 | Zeit-Parser verengt (`LocalTime.parse` nur bei Länge 8) | `SimValuesTest` rot | **gefangen**: `der Formatraum der Zeit ist der der ISO-Parser` rot (F-3 behoben). |
| M3 | Vorfilter immer `null` | Hinweis-Test rot | **gefangen**: `SimulationBackendSelectTest > die Quelle bekommt den Vorfilter als Hinweis`. |
| M4 | Bedingung trifft immer (`matches` = wahr) | Auswahl-Tests rot | **gefangen**: 10 Tests von `SimulationBackendSelectTest` rot (Dezimal, NULL, Widerspruch, Parameter, Textvergleich, Zeilenzahl u. a.). |
| M5 | `Opened.close()` ohne Einmaligkeits-Sperre | Einmaligkeit rot | **gefangen**: 5 Tests rot, darunter `nach dem Ende ist die Quelle genau einmal geschlossen`. |
| M6 | Schließen im `finally` entfernt | Cancel/Close/Abbruch rot | **gefangen**: 4 Tests rot (`close beendet das Lesen und schliesst die Quelle einmal`, `cancel trifft nur die benannte Anfrage`, `ein abbrechender Sammler …`, `Error und Abbruch der Quelle …`). |
| M7 | jede abgenommene Zeile in einer Liste behalten, Lauf mit `timeout 540` | Heap-Test rot | **gefangen als roter Lauf, nicht als roter Test**: nach 76 s `Task :test FAILED`, `SimulationMemoryTest` steht auf SKIPPED (Test-Worker abgebrochen, bekannte Klasse `BEO-TDS/oom-bricht-testlauf-statt-test`), keine rote Testzeile. Der Lauf hing nicht (anders als der 23-Minuten-Lauf des Reviews). |
| M8 | `LIMIT_NANOS = 1` im Heap-Test | Zeitgrenze löst aus | **gefangen**: `SimulationMemoryTest` rot mit `AssertionError: Zeitgrenze bei Zeile 0`. Die Grenze greift als roter Test; 90 s liegen weit über der Dauer des grünen Laufs (Sekunden). |

Nicht gewertet: Zeitmess-Tests anderer Slices; sie waren in keinem Lauf rot.

## DoD Punkt für Punkt

| # | DoD-Punkt | Urteil | Beleg |
|---|---|---|---|
| 1 | **Auswahl**: typisierte Parameter, streng/verlustfrei nach SPEC-035 und [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md); `TDS_SIM_INVALID_VALUE` vor dem Lesen; `compareTo`, Textvergleich ohne Schreibweise; `ResultStart` auch ohne Treffer; Bedingung je Zeile; `ResultEnd` mit Zeilenzahl | **bestätigt** | `SimValues.kt` (Überführung, `same`), `SimConditions.kt` (Vorbereitung vor dem Öffnen: `RowConditions.prepare` läuft in `start` vor `SelectExecution`), `SimulationBackend.kt`. Tests `SimValuesTest` (13), `SimulationBackendSelectTest` (13); M2, M3, M4 rot. |
| 2 | **Schrittweises Lesen und Ende**: `next()` nur auf Abnahme, `close()` genau einmal, Fehler vor/nach `ResultStart` | **bestätigt** | `pump` ruft `step` je Schleife nach dem `emit`; `Opened.close` mit `compareAndSet`; Fehler vor `ResultStart`: `sourceFailure()` ohne Metadaten, danach letztes Ereignis. `SimulationBackendSourceTest` (12); M1, M5, M6 rot. |
| 3 | **Port-Pflichten und Speichergrenze**: Suite `QueryBackendContract` am Simulations-Backend; Heap-Test mit Zähler | **bestätigt (mit Grenze)** | `SimulationBackendContractTest` erbt die Suite (9 Tests an beiden Backends grün); `SimulationMemoryTest` PASSED, M1 und M7 rot. Grenze des Belegs: ein Messpunkt (153,6 MB Text gegen 134,2 MB Heap), das KDoc sagt das (`nicht vollständig gehalten`). |
| 4 | Jeder Test trägt `@spec SPEC-<NNN>`; Tests laufen in `make test`, grün | **bestätigt** | Zählung der Dateien: `@spec`-Zeilen ≥ `@Test`-Zeilen in allen sechs berührten Testdateien (13/23, 13/16, 12/12, 1/1, 9/9, 2/5); frischer Lauf grün. |
| 5 | Zuordnung *Test \| Festlegung* je Aussage in der Closure-Notiz | **offen (Closure)** | §7 „Wird bei der Closure gefüllt.“ Die Festlegungen sind in SPEC-035 und KDoc als „Setzung, nicht am Server gemessen“ gekennzeichnet. |
| 6 | `make gates` grün | **bestätigt** | Exit 0. |
| 7 | Review durchgeführt, Report liegt vor | **bestätigt** | [2026-10-31-7c8917d-slice-sim-auswahl-streaming.md](2026-10-31-7c8917d-slice-sim-auswahl-streaming.md); Haken in §2 steht noch auf `[ ]`. |
| 8 | Doku-Update bei öffentlichem Vertrag | **bestätigt** | SPEC-035 um Einzelfestlegungen und Formatraum ergänzt (72a3b04, 31ae343). |
| 9 | Closure-Notiz mit Lerneintrag; Register fortgeschrieben | **offen (Closure)** | §7 leer. |
| 10 | Jedes Risiko aus §6 trägt einen Ausgang; drei Paarungen | **offen (Closure)** | alle fünf Risiken „offen bis zur Closure“. |

Alle Haken in §2 stehen auf `[ ]` bis auf die drei Liefer-Punkte; der Slice liegt in `in-progress/`. Das ist der erwartete Zustand vor der Closure.

## Fix-Runde: F-1 bis F-7

| Finding | Behoben? | Beleg |
|---|---|---|
| F-1 Suite-Test ohne Wirkung auf Vorauslesen | **ja** | neuer Test `Generator laeuft waehrend der Wartezeit des Lesers nicht voraus` (Leser wartet 20 ms je Zeile, `rowsPulled` muss gleich `received` sein); M1 rot am Simulations-Backend, M1b rot am Test-Backend. |
| F-2 Setzungen nur im Test-KDoc | **ja** | SPEC-035 nennt jetzt Namen aus der Beschreibung (`SELECT AGE` liefert `Age`) und „Zelle anderer JVM-Klasse ist ungleich (`1` gegen `1L`)“ als Setzung. |
| F-3 Formatraum der Zeit | **ja** | SPEC-035 nennt Sekunden optional, Nanosekunden, Jahr mit Vorzeichen, `T` Pflicht, keine Zeitzone; Test fixiert es (M2 rot). |
| F-4 Heap-Test ohne Zeitgrenze | **ja, mit Grenze** | Frist 90 s in `drain`, M8 rot mit `Zeitgrenze bei Zeile 0`. Der Weg über den Abbruch des Test-Workers (M7) bleibt ein roter Lauf ohne roten Test (L-1). |
| F-5 Grenze des Speicherbelegs | **ja** | KDoc des Heap-Tests: „trägt `nicht vollständig gehalten`, nicht `Speicher unabhängig von der Zeilenzahl`“, Kopf-KDoc „nicht vollständig“. |
| F-6 Suite-Haken `seen` | **ja (Wortlaut)** | Grenze im Kopf-KDoc der Suite: bei festem Fehler wird nicht geprüft, dass Code und Meldung des Skripts durchgereicht werden. Die Prüfschärfe ist unverändert (Entscheidung, kein Mangel). |
| F-7 Registrierung ohne Abmeldepflicht | **ja** | KDoc von `SimulationBackend` nennt Sammeln oder `close()` und das Ersetzen bei gleichem Schlüssel. |

## Konformität mit ADR-0012 und ADR-0013

- [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md) Entscheidung 6 (streng und verlustfrei, gleiche Typfamilie, ISO-Formate, keine Rundung, Parameter nur verlustfrei erweitert, Überlauf ungültig) und 9 (Textvergleich ohne Schreibweise): im Code (`SimValues`) und in Tests belegt; die Ergänzungen (Bool aus `0`/`1`, `1.0` in Ganzzahlspalte, Formatraum) stehen als Setzung in SPEC-035 und gehen nicht über die ADR hinaus. ADR unverändert im Diff (§3.5).
- [ADR-0013](../plan/adr/0013-backend-grenzen.md): Zeilendarstellung als JVM-Klassen je Spaltenart (`hasClassOf` prüft den Parameter gegen die Klasse), Quelle suspendierend, Frist nicht durchgesetzt (KDoc nennt `next()` hängend als Grenze). ADR unverändert.
- Hard Rules: kein `@Suppress` in `src/`, keine Schwelle gesenkt (`config/detekt/detekt.yml` nicht im Diff, §3.6), Aussagen in KDoc und Spec tragen ihren Beleg oder die Kennzeichnung „Setzung“ (§3.8). Kein Verstoß gefunden.

## Plan gegen Code

Alle Dateien der Tabelle in §3 des Slice liegen vor (`SimulationBackend.kt`, `SimValues.kt`, `SimConditions.kt`, `StatementPlanner.kt` mit zwei Codes, SPEC-035, `QueryBackendContract.kt` mit den zwei Haken, fünf neue Testdateien, `SimulationCodesTest.kt`). Der Diff `f65f486`, `72a3b04` und `31ae343` berührt nichts darüber hinaus außer dem Slice selbst (Plan-Tabelle). Der Vorfilter `SimQuery.filter` wird übergeben und nie als Ersatz verwendet (M3, M4). Ein Fehler, den der Plan nicht antizipierte, wurde nicht gefunden.

## Findings

| ID | Kategorie | Befund | Beleg |
|---|---|---|---|
| L-1 | LOW | Die Mutation, die jede Zeile behält (M7), endet als abgebrochener Test-Worker: der Heap-Test steht auf SKIPPED, der Lauf ist rot, aber kein Test meldet den Rückfall. Die 90 s Zeitgrenze (F-4) fängt den langsamen Weg, nicht den Abbruch durch Speichermangel. Beides macht `make test` rot; wer die Ursache sucht, liest eine Worker-Meldung statt einer Testzeile (bekannte Klasse `BEO-TDS/oom-bricht-testlauf-statt-test`). Der 23-Minuten-Hang des Reviews trat unter `timeout 540` nicht auf (76 s); die Ursache dort ist nicht geklärt. | M7, M8 |
| L-2 | LOW | Die Matrix `docs/plan/spec-abdeckung.md` ist nicht Teil des Gates (Werkzeug `make spec-abdeckung`); der Stand im Repo zählt für SPEC-035 55 Tests und kennt den neuen Formatraum-Test womöglich nicht. Kein Mangel der Umsetzung, ein Hinweis für die Closure (`BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor`). | `docs/plan/spec-abdeckung.md` Zeile SPEC-035 |
| I-1 | INFO | Closure-Pflichten (DoD 5, 9, 10) stehen aus; vor `done/` die Zuordnung *Test \| Festlegung* mit den Messbedingungen des Heap-Laufs (1 200 000 × 128 Zeichen, 128 MiB, 14 % über der Grenze, M1 und M7 rot), die Ausgänge der fünf Risiken und den Lerneintrag schreiben. | §7 des Slice |
| I-2 | INFO | Der Absatz „Fehler der Zeilenquelle“ in SPEC-035 ist durch die Fix-Runde um Fragmente zweier fremder Themen (Namen der Beschreibung, Klassenvergleich) gewachsen, und ein Satz bricht mitten in der Zeile um; die Aussagen sind korrekt, die Gliederung leidet. Kein Verstoß. | `spec/spezifikation.md` SPEC-035 |

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 0 |
| LOW | 2 |
| INFO | 2 |

**Übergabe an den Planer:** Umsetzung und Fix-Runde bestätigt; keine Rückführung nötig. Vor `done/`: Closure-Notiz (I-1), danach der reine `git mv` nach `AGENTS.md` §3.3. Dieser Bericht schließt nichts.
