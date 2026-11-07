# Review-Bericht: slice-qa-test-stage-run-kopplung (HEAD 3f4568e2)

* Rolle: Reviewer (Modul 10), frischer Kontext. Gegenstand: Diff `81800657..3f4568e2` (Zweig `worktree-agent-a679b3097d69efc71`), Maßstab: der Slice (Plan, DoD, Rückführungen, Abgrenzung), [TDS-QA-05](../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), `SPEC-045`, [ADR-0024](../plan/adr/0024-lokale-stempel-zwei-klassen.md) und [ADR-0030](../plan/adr/0030-dritter-stempel-fuer-die-selbsttests-der-stempel.md), `AGENTS.md` §3.1 bis §3.8.
* Selbst gefahren (Host unter hoher Last, Last 28): `make gates FORCE=1` (Exit 0), `make stempel-eingaenge-pruefung`, `make stempel-eingaenge` (95 Fälle), `make stempel-gruppe` (67), `make stempel-abbild` (100), `make stempel-selbsttest` (118), `make gate-zuordnung` (16 Gates, Selbsttest 33), `make skript-selbsttest`, `make docs-check` (1187 Dateien, 0 Befunde), `make test`, `make spec-abdeckung` (Exit 0, `docs/plan/spec-abdeckung.md` unverändert, 9 Festlegungen ohne Nachweis wie zuvor), `make clean-build` (Exit 0). Frischbeleg der .NET-Tests: `docker build --no-cache-filter test --target test` ergibt `Passed: 126, Total: 126`.
* Der Arbeitsbaum des Zweigs ist unverändert; alle Mutationen liefen in Kopien von `referenztest-dotnet/` im Scratchpad.

## Gesamturteil

0 HIGH, 2 MEDIUM, 2 LOW, 3 INFO. Die Umstellung ist für die Gate-Stage `test` gleichwertig zum Vorzustand (gleiche Tests, gleicher Exit bei rotem Test); der Bericht bleibt bei rotem Test erhalten und zählt ihn rot. Die Kopplung gilt aber nur für eine Form der Änderung (zusätzliches Argument am Skriptaufruf); der Slice-Wortlaut „nicht mehr unbemerkt“ trägt weiter, als gemessen (F-1), und der tragende Zweig des Skripts hat keinen Test (F-2).

## Gemessene Mutationen und Gegenproben

| Mutation (Kopie unter dem Scratchpad) | Wirkung gemessen |
|---|---|
| M1: `RUN --network=none sh test-lauf.sh test --filter "…"` (nur Stage `test`) | rot: `test-lauf.sh: genau ein Argument`, `exit code: 2`; bestätigt die Angabe des Implementers |
| M2a: `--filter FullyQualifiedName~<Treffer eines Tests>` in `DOTNET_TEST_ARGS` der Basis | beide Stages führen genau 1 Test aus (`Passed: 1, Total: 1`), der Bericht hat 1 Ergebnis; Gate und Matrix folgen einander wie vom Slice gewollt |
| M2b: Filter ohne Treffer in `DOTNET_TEST_ARGS` | `No test matches the given testcase filter`, Stage `test` Exit 0, `bericht.trx` wird geschrieben; `make spec-abdeckung` prüft nur `[ -s bericht.trx ]`. Der Befund des Implementers „Bericht 0 Testergebnisse“ ist für die Datei bestätigt, das Gate bleibt grün (F-3) |
| M3: RUN-Zeile von `test` ohne `test-lauf.sh`: `dotnet test $DOTNET_TEST_ARGS --filter "FullyQualifiedName~ZZZKeinTreffer"` | Stage `test` Exit 0 mit `No test matches`; Stage `test-bericht` führt weiter alle 126 Tests aus und der Bericht hat 126 Ergebnisse. Kein Sensor wird rot (F-1) |
| M4: zusätzlicher roter Test (`Assert.Equal(1, 2)`) in `Smoke/` | Stage `test`: `Failed: 1, Total: 127`, `exit code: 1`, Build bricht. Stage `test-bericht`: Build läuft durch, Meldung „der Testlauf ist rot“, TRX enthält 126 `Passed`, 2 `Failed`, 1 `Error`; ein roter Test steht also im Bericht als nicht bestanden |
| M5: Argument-Zählung (`if [ "$#" -ne 1 ]`) aus `test-lauf.sh` gestrichen, dazu M1 | Stage `test` grün mit 126 Tests, `--filter` wird ignoriert, nichts wird rot (F-2) |

## Findings

### F-1 MEDIUM — Aussage „nicht mehr unbemerkt“ breiter als die Kopplung

* kategorie: MEDIUM
* quelle: `AGENTS.md` §3.8; [TDS-QA-05](../../spec/lastenheft.md#tds-qa-05--wartbarkeit)
* pfad: `docs/plan/planning/in-progress/slice-qa-test-stage-run-kopplung.md` · „nicht mehr unbemerkt auseinanderläuft“ (§1 Ziel); `docs/plan/planning/in-progress/roadmap.md` · „an die Basis `test-quellen` gekoppelt“
* befund: Die Herkunft nennt Mutation 1 des Verifiers (ein `--filter` nur in der RUN-Zeile von `test`). Diese Form (`RUN … dotnet test $DOTNET_TEST_ARGS --filter X`, ohne `test-lauf.sh`) bleibt unbemerkt: Gate grün mit 0 Tests, Matrix zählt 126 weiter (M3); abgewiesen wird nur das zusätzliche Argument am Skriptaufruf (M1). Dockerfile, Kopf von `tools/spec-abdeckung.sh` und `docs/plan/spec-abdeckung.md` nennen das Restrisiko ehrlich („nicht gekoppelt“), Ziel und Roadmap-Zeile nicht.
* verifizierbar: ja — Kopie des Dockerfiles mit M3 bauen
* klasse: Kopplung durch Konvention statt Sensor, Zielsatz breiter als Beleg

### F-2 MEDIUM — Abweisung im Skript hat keinen Test

* kategorie: MEDIUM
* quelle: Maintainability (Reviewer-Skill: Eigencode, dessen Entfernung keinen Test rot macht)
* pfad: `referenztest-dotnet/test-lauf.sh` · `if [ "$#" -ne 1 ]; then`
* befund: Die Exit-2-Zweige (falsche Argumentzahl, unbekannter Modus) sind der im Kommentar zugesagte Mechanismus („ein Flag … ist hier nicht möglich“); kein Gate und kein Selbsttest ruft sie auf. Streicht man die Zählung, bleiben alle Gates grün (M5); der Beleg liegt nur in dieser Mutationsmessung. Der Slice erlaubt Hand-Mutationen (Plan-Wortlaut), ein Träger im Repo ist nicht benannt.
* verifizierbar: ja — M5
* klasse: Wächterzweig ohne Gegenprobe

### F-3 INFO — `dotnet test` ohne Treffer lässt die Gate-Stage grün

* kategorie: INFO
* quelle: [TDS-QA-05](../../spec/lastenheft.md#tds-qa-05--wartbarkeit)
* pfad: `referenztest-dotnet/test-lauf.sh` · „exec dotnet test $DOTNET_TEST_ARGS“
* befund: Ein Filter ohne Treffer in `DOTNET_TEST_ARGS` (M2b) lässt `test` mit Exit 0 enden; das Verhalten ist unverändert zum Vorzustand und liegt außerhalb dieses Slice (kein Gleichlauf-Sensor, Abgrenzung §1). Ein Träger dafür ist im Slice nicht benannt; Zuständigkeit Planner (Register-Eintrag oder Folge-Slice).
* verifizierbar: ja — M2b
* klasse: Gate bei null Tests grün

### F-4 LOW — „Berührte Spec-Stellen: SPEC-045“ ohne Änderung an `spec/`

* kategorie: LOW
* quelle: Maintainability (Plan-Kopf gegen Diff)
* pfad: `docs/plan/planning/in-progress/slice-qa-test-stage-run-kopplung.md` · „**Berührte Spec-Stellen:** SPEC-045“
* befund: Der Diff berührt `spec/` nicht und `SPEC-045` enthält keine Aussage über die Stage `test-bericht`; berührt sind nur `docs/plan/spec-abdeckung.md` und der Erzeuger. Die Plan-Tabelle §3 deckt alle Dateien des Implementers (`Dockerfile`, `test-lauf.sh`, `tools/spec-abdeckung.sh`, `docs/plan/spec-abdeckung.md`; Stempel-Listen „prüfen“, ohne Änderung); nicht in der Tabelle stehen die Lifecycle-Dateien (Slice-Move, Roadmap, `state.md` der Beobachtung), die der Planner schrieb.
* verifizierbar: ja — `git diff --stat 81800657..HEAD -- spec`
* klasse: Plan-Kopf nennt Spec-Stelle ohne Berührung

### F-5 LOW — Roadmap-Zeile behauptet „gekoppelt“ vor Review und Verifikation

* kategorie: LOW
* quelle: `AGENTS.md` §3.7 (Zustandsfelder nennen Zustand und Beleg)
* pfad: `docs/plan/planning/in-progress/roadmap.md` · „`RUN`-Zeile der Stage `test` über `test-lauf.sh` an die Basis `test-quellen` gekoppelt“
* befund: Die Zeile steht in der Rubrik „In Arbeit“ und nennt den Zielzustand als erreicht; der Beleg (Review, Verifikation) fehlt noch. Haken der DoD sind im Diff nicht gesetzt (geprüft: kein `- [x]` in der Slice-Datei).
* verifizierbar: nein
* klasse: Zustandsfeld nennt Ziel statt Stand

### F-6 INFO — Stempel: kein Eintrag nötig, Hash ändert sich

* kategorie: INFO
* quelle: [ADR-0024](../plan/adr/0024-lokale-stempel-zwei-klassen.md), [ADR-0030](../plan/adr/0030-dritter-stempel-fuer-die-selbsttests-der-stempel.md)
* pfad: `tools/stempel/tests.eingaenge` · „referenztest-dotnet“
* befund: Das Verzeichnis steht ganz in der Liste der Klasse `tests`, also ändert `test-lauf.sh` deren Hash; `make stempel-eingaenge-pruefung` und `make stempel-abbild` (100 Fälle) sind grün. `referenztest-dotnet/Dockerfile` steht zusätzlich in `stempel.eingaenge`; eine Änderung am Dockerfile löst auch die Klasse `stempel` aus, wie bisher.
* verifizierbar: ja — `make stempel-abbild`
* klasse: —

### F-7 INFO — Cache der Stages

* kategorie: INFO
* quelle: Slice §6 (Risiko Stempel/Cache)
* pfad: `referenztest-dotnet/Dockerfile` · „COPY test-lauf.sh ./“
* befund: Das `COPY` steht in der gemeinsamen Basis `test-quellen`; beide Stages hängen darunter, eine Änderung des Skripts invalidiert sie also nach der Bauart gemeinsam. Als eigener Versuch (Skript ändern, beide Stages ohne Cache vergleichen) nicht gefahren. `make spec-abdeckung` baut `test-bericht` ohnehin mit `--no-cache-filter`.
* verifizierbar: ja
* klasse: —

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `referenztest-dotnet/Dockerfile`, `test-lauf.sh`: Gleichwertigkeit von `test` (Exit, `exec`, `sh` im Image, Quoting von `$DOTNET_TEST_ARGS`, Umgebung, 126 Tests) | geprüft, ohne Befund (außer F-1, F-2) |
| `test-bericht` bei rotem Test | geprüft, ohne Befund (M4) |
| `tools/spec-abdeckung.sh`, `docs/plan/spec-abdeckung.md`: Kommentar und gedruckter Satz, Logik unverändert | geprüft, ohne Befund |
| `tools/stempel/`, `tools/mk/`, `harness/README.md` (Zellen ≤ 200 Zeichen; Diff berührt `harness/` nicht) | geprüft, ohne Befund |
| `spec/` (`SPEC-045`, `SPEC-059` o. a.), `docs/user/`, `docs/maintainer/`: Treffer auf „nicht gekoppelt“, „RUN-Zeile“, `DOTNET_TEST_ARGS`, `test-bericht` | geprüft, ohne Befund; Fundstellen nur in Erzeuger, Matrix, Dockerfile, Slice, Register |
| Register `schwesterstage-ohne-gleichlauf-sensor` (`state.md` zeigt auf `in-progress/`), `abdeckungsmatrix-veraltet-ohne-sensor`; Verweise auf `open/slice-qa-test-stage-run-kopplung` | geprüft, ohne Befund; kein verbliebener Verweis auf `open/` |
| Hard Rules §3.1 (Docker-only), §3.2, §3.5, §3.6 (Gate nicht schwächer als zuvor) | geprüft, ohne Befund |
| DoD-Haken im Diff | geprüft: keine gesetzt (siehe F-5) |

## Summary

Der Umbau ist als Gate-Änderung sauber; die Fassung „Kopplung“ ist eine Konvention mit einer Wache am Skriptaufruf, kein Sensor (der Slice schließt ihn aus). Zu entscheiden bleiben F-1 (Zielsatz) und F-2 (Träger für die Wache).

`make clean-build`: Exit 0 (Gradle-Stages; berührt .NET nicht).
