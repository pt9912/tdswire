# Verifikationsbericht: slice-release-dateien-bauen, Stand 08fba8d3 — 2026-11-16

**Rolle:** Verifier (Frage: bauen wir es richtig, gegen DoD, Plan und Entscheidungen). Kein Review, keine Reparatur; die DoD-Haken im Plan setzt der Planner bei der Closure und sind hier offen geblieben.

**Gegenstand:** Zweig `slice-release-dateien-bauen`, HEAD `08fba8d3`, Basis `main` `c670a6cf`. Plan: [slice-release-dateien-bauen](../plan/planning/done/slice-release-dateien-bauen.md) (§1 bis §8). Maßstäbe: [ADR-0050](../plan/adr/0050-github-release-im-tag-lauf.md) Entscheidungen 2 und 3 (und 6 für den Vorschau-Modus), [TDS-RB-05](../../spec/lastenheft.md#tds-rb-05--lizenz), [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), `AGENTS.md` 3.1, 3.3, 3.7, 3.8. Eingang: Beleg [lizenzen-lib-2026-11-16](../messungen/lizenzen-lib-2026-11-16.md), Reviews [Erst-Review b50561eb](2026-11-16-b50561eb-slice-release-dateien-bauen-review.md) und [Re-Review bc7a8343](2026-11-16-bc7a8343-slice-release-dateien-bauen-re-review.md).

**Änderungsart:** Skripte, Build-Datei (`Dockerfile`), Make-Fragment, Doku; `src/` und `testserver/` unberührt. Pflicht nach [`harness/README.md`](../../harness/README.md) §Minimal agent workflow: `make gates`, Lauf des gelieferten Targets, Doku-Teile (`docs-check`, `flaeche`, `kopplung-zahlen`). Auftragsgemäß zusätzlich `make clean-build` und `make e2e` je einmal auf dem End-Stand (Verifier-Zeile der Tabelle).

## Urteil in Kürze

Die drei Liefer-Punkte sind erfüllt, am Lauf belegt und an Mutationen rot gesehen, auch die beiden, die der Implementer nicht einzeln rot sah. Kein HIGH, kein MEDIUM; drei LOW, fünf INFO. Offen bleiben nur die Closure-Pflichten (Notiz, Register, Risiko-Ausgänge, Paarungen), wie vor der Closure vorgesehen. Zwei Fragen gehören dem Nutzer: Host-`sh` vor `docker build` unter `AGENTS.md` 3.1, und die Form `lizenzen/lib/…` im ZIP (offene Fragen 2a bis 2c, nicht entschieden).

## 1. DoD, Zeile für Zeile (§2 des Plans)

| # | DoD-Zeile | Urteil | Beleg |
|---|---|---|---|
| 1 | Stage und Target liefern die Dateien | **erfüllt** | Stages `release-dateien` (`FROM build-jar`) und `release-dateien-export` (`FROM scratch`, `--output type=local`) im Dockerfile; `make release-dateien` in einer Wegwerf-Kopie mit `version = "0.1.1"`: Exit 0, drei Dateien (Abschnitt 3). Version aus `build.gradle.kts`, ZIP `tdswire-0.1.1.zip`. `-SNAPSHOT` ohne Modus: Exit 2 vor `docker build` (Meldung „endet auf -SNAPSHOT“, kein Export-Verzeichnis). Vorschau-Modus (`VORSCHAU=1`) mit `-SNAPSHOT`: Exit 0, ZIP `tdswire-0.1.1-SNAPSHOT-vorschau.zip`. `VORSCHAU=x`: bricht. Das Target steht in keiner Gate-Liste und nicht in `e2e.mk` (der Selbsttest liest das und ist grün; eigener Lauf `make gate-zuordnung` Exit 0). `make stempel-eingaenge-pruefung` Exit 0. `make build` / Stage `build-jar` im Diff unverändert (`git diff c670a6cf..HEAD -- Dockerfile` berührt nur `skript-selbsttest` und hängt Stages hinter `publish` an). |
| 2 | Prüfungen am entpackten Archiv | **erfüllt** | `tools/release-pruefung.sh` entpackt das ZIP in ein leeres Verzeichnis und ruft dort `manifest-pruefung.sh --ohne-pom` (Modus mit Selbsttest in `manifest-selbsttest.sh`, im frischen Lauf grün). `META-INF/LICENSE` und `LICENSE` gleich der Repo-Datei (am entpackten Archiv von mir mit `cmp` bestätigt). Sollmenge der sieben Dateien: am echten ZIP entfernte NOTICE von kotlinx-coroutines: rot (Abschnitt 5b). Neue Bibliothek ohne Verzeichnis: Fall `neue-bibliothek` im Selbsttest. |
| 3 | Selbsttest mit Mutationen in `make skript-selbsttest` | **erfüllt** | `docker build --no-cache --target skript-selbsttest` Exit 0, 15 s, `release-selbsttest: ok (ein Gutfall …, fünfzehn rote Fälle am Eingang, acht am fertigen Archiv, vier Fälle des Modus --vorschau, …)`. Die sieben Mutationen der DoD (Jackson, Main-Class, andere LICENSE, Class-Path ohne Datei, Lizenzdatei entfernt, NOTICE entfernt, SHA256SUMS passt nicht) stehen als Fälle `jackson`, `main`, `andere-lizenz`, `cp-ohne-datei`, `lizenz-fehlt`, `notice-fehlt`, `summe-falsch`; drei davon (NOTICE, Testserver, SHA) habe ich durch Mutation des Prüfers einzeln rot gesehen (Abschnitt 5). Der Test liest den Archivinhalt (Eingang wird gepackt, entpackt, geprüft). |
| 4 | `make gates` grün; Doku-Teile | **erfüllt** | `make gates` Exit 0 (8 s); `docs-check`, `stempel-eingaenge-pruefung`, `gate-zuordnung`, `flaeche`, `kopplung-zahlen` je einzeln Exit 0 (Abschnitt 4). Gruppen `werkzeug`, `tests`, `stempel` per Stempel übersprungen (INFO F-5, Frischbelege separat gefahren). `make release-dateien` auf dem End-Stand: Abschnitt 3. |
| 5 | Review durchgeführt, Report unter `docs/reviews/` | **erfüllt** | Zwei Reports (Erst-Review b50561eb: 4 MEDIUM, 2 LOW, 1 INFO; Re-Review bc7a8343: 0 HIGH/MEDIUM, 4 LOW, 3 INFO). Nach dem Re-Review wurde Code geändert (siehe F-2, LOW). |
| 6 | Doku-Update | **erfüllt** | `harness/README.md`: Zeile `make release-dateien` (Werkzeug-Tabelle, Zeile 113) und Zeile `make skript-selbsttest` ergänzt (`release-*.sh`, [ADR-0050](../plan/adr/0050-github-release-im-tag-lauf.md)); `releasing.md` Schritt 6, 7 und „Nicht Teil dieses Ablaufs“, „Zurückgestellt“ nachgezogen; Kopfkommentare der Stage, des Fragments und der Skripte vorhanden. `docs/user/` nicht im Diff. |
| 7 | Closure-Notiz mit Steering-Loop-Lerneintrag | **offen (vor Closure erwartbar)** | §7 trägt „Wird bei der Closure gefüllt.“ |
| 8 | Beobachtungs-Register fortgeschrieben, kein Zähler | **offen (vor Closure erwartbar)** | Im Diff keine Datei unter `docs/plan/planning/observations/`. |
| 9 | Jedes Risiko aus §6 mit Ausgang | **offen (vor Closure erwartbar)** | Sieben Risiken, alle „offen bis zur Closure“. Material aus diesem Lauf: Zeitstempel/Byte-Gleichheit weiter nicht zugesagt (F-6, INFO); Jar aus `build-jar` gegen Jar aus `publish` weiter ungemessen (Träger `slice-release-im-tag-lauf-messen`); Eingangslisten-Risiko: `stempel-eingaenge-pruefung` ist grün; `&&`-Kette: gesehen (F-4). |
| 10 | Drei Paarungen | **offen (an die Closure gebunden)** | Nicht Gegenstand vor der Closure. |

Liefer-Punkte (3 von 3): erfüllt.

## 2. Entscheidungs-Konformität

| Maßstab | Ergebnis |
|---|---|
| [ADR-0050](../plan/adr/0050-github-release-im-tag-lauf.md) Entscheidung 2 (Archivform A1: ZIP + Jar + `SHA256SUMS`; neue Stage, Export wie `lock-export`; `make build` unverändert) | Konform. Ausgabe genau drei Dateien (`ls`, und die Prüfung erzwingt „genau drei“). `sha256sum -c SHA256SUMS` auf dem Host am Export: beide `OK`. `make build` und Stage `build-jar` unverändert. Die Notiz-/Gleichheitsaussage zum Paket-Jar wird nicht gemacht (Kopf: „nicht zugesagt“). |
| [ADR-0050](../plan/adr/0050-github-release-im-tag-lauf.md) Entscheidung 3 (`lib/` nur mit Beleg, Lauf bricht statt stillem Teilinhalt) | Konform. Sollmenge der sieben Dateien entspricht Tabelle „Ablage“ im Beleg (sieben Hashes im entpackten Archiv gleich den Hashes im Beleg, Abschnitt 6). Fehlt eine, auch nur eine NOTICE, bricht der Lauf ohne Ausgabe-Verzeichnis (Fälle `notice-fehlt`, `zip-notice-fehlt`, Abschnitt 5b). Rechtsfrage wird nicht beantwortet (Kopf der Skripte, `releasing.md` Schritt 7). |
| [ADR-0050](../plan/adr/0050-github-release-im-tag-lauf.md) Entscheidung 6 (Vorschau) | Der Modus ist Nutzerentscheidung 2026-11-16, in keiner ADR (Plan §6, Re-Review N-7); Umsetzung wie entschieden: ZIP immer `-vorschau`, ohne Modus bricht `-SNAPSHOT`, `tdswire.jar` ohne Marke. „Ein Release nutzt ihn nie“ sichert nur die Abweisung eines `*-vorschau.zip` in einer Ausgabe ohne `--vorschau` (Selbsttest, grün). |
| `AGENTS.md` 3.1 (Docker-only) | Konform bis auf die ausdrücklich offene Frage. Kein Host-JDK, kein Gradle: Bau, ZIP (`jar` im Image) und Prüfung im Container; Export mit `--output type=local`, nichts gemountet (Make-Fragment enthält kein `-v`/`--mount`, der Selbsttest hält das mit zwei Gegenproben; im Worktree entstand kein `build/` und kein `.gradle/`). **Frage an den Nutzer (F-1, LOW):** das Make-Target ruft vor `docker build` `sh tools/release-dateien.sh --nur-version` auf dem Host (POSIX `sed`, `grep`, `wc`, `printf`, `dirname`; Präzedenz `tools/publish.sh`). Ob das unter „kein lokales JDK-, Gradle- oder Kotlin-Install“ fällt, ist nicht entschieden; die Standardantwort „Host-`sh` ist zulässig“ ist als Setzung im Make-Kopf genannt. |
| `AGENTS.md` 3.3 (git mv + Inhalt = zwei Commits) | Kein `git mv` im Diff (`git diff --stat`: nur Änderungen und neue Dateien). Ohne Befund. |
| `AGENTS.md` 3.7 (Kommentare im Indikativ) | Stichprobe der neuen Kopfkommentare (`release-dateien.sh`, `release-pruefung.sh`, Fragment, Dockerfile-Stage): Zusage, Grenze, Kopplung; die Rollenwörter („Vorschlag des Implementers“) sind laut Re-Review N-2 entfernt und stehen nicht mehr im Quelltext (`git grep -n 'Implementer' tools/release-*`: keine Treffer). Ohne Befund. |
| `AGENTS.md` 3.8 (Aussage trägt so weit wie ihr Beleg) | „lokal gelaufen, nicht in der CI gelaufen“: trägt (Abschnitt 7). „Byte-Reproduzierbarkeit nicht zugesagt“: als Grenze formuliert, nicht als Zusage. Die Form `lizenzen/lib/…` ist an allen Fundstellen als Vorschlag gekennzeichnet, offene Fragen 2a bis 2c unbeantwortet. Ohne Befund. |

## 3. Pflichtlauf `make release-dateien` (Wegwerf-Kopie und Worktree)

Die Kopie entstand mit `git archive HEAD | tar -x` im Scratchpad, dort `version = "0.1.1"` gesetzt; der Worktree blieb unverändert. Der Export in der Kopie, danach `sha256sum -c` auf dem Host:

| Lauf | Ergebnis |
|---|---|
| `make release-dateien` (Kopie, `0.1.1`) | Exit 0, 3,4 s Wanduhr; `release-pruefung: ok (tdswire-0.1.1.zip entpackt: Manifest ohne Main-Class, kein Jackson, LICENSE gleich, 7 Lizenz- und Hinweisdateien, SHA256SUMS gruen)` |
| Host: `sha256sum -c SHA256SUMS` im Export | `tdswire-0.1.1.zip: OK`, `tdswire.jar: OK` |
| zweiter Aufruf bei belegtem `release-dateien/` | Exit 2, „release-dateien besteht und ist nicht leer“, vor `docker build` |
| Export in leeres/neues `RELEASE_AUSGABE` | Exit 0 |
| Worktree, `make release-dateien` mit `0.1.1-SNAPSHOT` | Exit 2 vor `docker build`, kein `release-dateien/` entstanden |
| Worktree, `make release-dateien VORSCHAU=1` | Exit 0, 1,5 s; ZIP `tdswire-0.1.1-SNAPSHOT-vorschau.zip` 4 732 453 Byte; `sha256sum -c` auf dem Host zweimal `OK` |
| `VORSCHAU=1` zweiter Aufruf bei belegtem Verzeichnis | Exit 2 |
| `VORSCHAU=x` | Exit 2 („VORSCHAU ist 1 oder leer“) |

Messwerte (Version `0.1.1`):

- `tdswire-0.1.1.zip` 4 732 455 Byte, SHA-256 `312cb65df5fafd76acef55f283870e0f1f6fa8357d180a152ff1ee14d44d86f2`
- `tdswire.jar` 1 784 788 Byte, SHA-256 `d78eb6e269165f2dc957a3942da5d09bd544b755deae4b86c90df3dd15457119` (das `-SNAPSHOT`-Jar: `b6c8073c…d4ee`, gleich dem Jar des Verifikationsberichts zu `slice-release-lizenzen-lib-belegen`)
- `SHA256SUMS` 162 Byte (Vorschau 180 Byte): genau zwei Zeilen, `sha256sum`-Format

Hinweis: beide Läufe der Kopie lieferten dieselben ZIP-Bytes (`312cb65d…`); das war ein Lauf aus dem Layer-Cache und belegt keine Reproduzierbarkeit (ist nicht zugesagt).

Aufgeräumt: der Export im Worktree (`release-dateien/`), das Export-Verzeichnis im Scratchpad und die Wegwerf-Kopie sind entfernt; `git status --short` im Worktree leer.

## 4. Sensoren auf dem End-Stand

| Lauf | Exit | Zeit | Anmerkung |
|---|---|---|---|
| `make docs-check` | 0 | 2 s | |
| `make stempel-eingaenge-pruefung` | 0 | 0,2 s | |
| `make gate-zuordnung` | 0 | 0,5 s | |
| `make flaeche` | 0 | 0,9 s | |
| `make kopplung-zahlen` | 0 | 0,8 s | |
| `make skript-selbsttest`-Stage frisch (`docker build --no-cache --target skript-selbsttest`) | 0 | 15 s | `release-selbsttest: ok`, alle acht Selbsttests der Kette grün |
| `make gates` | 0 | 8 s | Gruppen `werkzeug`, `tests`, `stempel` per Stempel übersprungen (F-5); `immer`-Gates gelaufen |
| `make clean-build` | 0 | 10 min 19 s | `--no-cache`: Jar, Tests, detekt grün; größte Posten: Tests (`BUILD SUCCESSFUL in 3m 7s`) und Jar/Kompilierung (3 m 40 s) |
| `make e2e` | 0 | 2 min 53 s | 9 Stufen, „e2e: gruen (9 Stufen, 173 s)“ |

Gesamtlaufzeit der Verifikation (erster Befehl bis Ende der Sensoren): rund 16 Minuten Maschinenzeit nach dem Lesen; größte Posten `make clean-build` 10 min 19 s, `make e2e` 2 min 53 s, drei Mutationsläufe der Selbsttest-Stage je rund 15 s. Speicher (verfügbar, `free -m`): zu Beginn 12 807 MB, nach `skript-selbsttest` 13 129 MB, tiefster Wert während `clean-build` 10 249 MB, am Ende 13 347 MB; nie unter 10 GB, keine künstliche Last.

## 5. Gegenproben (Mutationen, jeweils in der Wegwerf-Kopie, Quelle danach zurückgesetzt)

| # | Mutation | Ergebnis |
|---|---|---|
| a1 | Testserver-Prüfung in `tools/manifest-pruefung.sh` abgeschaltet (Muster geändert), volle `&&`-Kette | rot, aber **nur** `manifest-selbsttest: FEHLER - testserver-im-jar`; `release-selbsttest` läuft in dieser Kette nicht mehr (Maskierung bestätigt) |
| a2 | dieselbe Mutation, nur `sh tools/release-selbsttest.sh` | rot einzeln: `release-selbsttest: FEHLER - testserver-im-jar: Exit 0 statt 1`, dazu „die Meldung nennt [Klassen des Testservers] nicht“ und „die Ausgabe entstand trotz Befund“. Der Fall `testserver-im-jar` ist also ein wirksamer eigener Sensor, nur in der Kette hinter `manifest-selbsttest` unsichtbar (Plan §6 und Dockerfile-Kommentar nennen die Grenze). |
| b1 | NOTICE-Zeile aus der Sollmenge von `tools/release-pruefung.sh` entfernt | rot: `notice-fehlt` und `zip-notice-fehlt` (zwei Fälle: Eingang und fertiges Archiv) |
| b2 | echtes ZIP aus `make release-dateien` entpackt, NOTICE von kotlinx-coroutines entfernt, mit `jar` neu gepackt, Summen neu gerechnet, `release-pruefung.sh` im Image | Exit 1: `lizenzen/lib/org.jetbrains.kotlinx-kotlinx-coroutines-core-jvm-1.10.1/NOTICE.txt fehlt im ZIP oder ist leer` (LICENSE.txt blieb; die Prüfung „je Jar eine Lizenzdatei“ allein hätte das nicht gemeldet) |
| c1 | `sha256sum -c` in `release-pruefung.sh` durch `true` ersetzt | rot: Fall `summe-falsch` (Exit 0 statt 1, Meldung fehlt) |
| c2 | `-SNAPSHOT`-Zweig in `release-dateien.sh` entwertet | rot: Fälle `snapshot`, `ohne-vorschau-snapshot` und `make ohne-vorschau-snapshot` (die Formprüfung fängt `-SNAPSHOT` weiter ab, aber die erwartete Meldung fehlt; INFO F-7) |

Keine Mutation blieb grün. Die Quelle der Kopie war nach jedem Lauf gleich `tools/` des Worktrees (`diff -r`), der Worktree unverändert.

## 6. ZIP-Inhalt gegen den Beleg

`unzip` des Exports (Version `0.1.1`) im Scratchpad, `find` und `sha256sum`: 12 Dateien.

- `tdswire.jar` (gleich dem einzelnen Jar, `cmp`), `LICENSE` (gleich der Repo-Datei, `cmp`), `META-INF/LICENSE` im Jar gleich `LICENSE`.
- `lib/`: `annotations-23.0.0.jar` `7b0f1972…cd05`, `kotlin-stdlib-2.4.10.jar` `4ec0293b…a6df`, `kotlinx-coroutines-core-jvm-1.10.1.jar` `069c5988…c00d`; gleich den Hashes des Verifikationsberichts zu `slice-release-lizenzen-lib-belegen` und dem Beleg.
- `lizenzen/lib/`: sieben Dateien, alle sieben Hashes gleich der Tabelle „Ablage“ des Belegs (`8c1e966c…`, `cfc7749b…`, `0b09a83d…`, `8d8291ca…`, `d1bc53b4…`, `b1febe63…`, `421d45ac…`).
- Manifest des Jars: `Class-Path: lib/kotlinx-coroutines-core-jvm-1.10.1.jar lib/kotlin-stdlib-2.4.10.jar lib/annotations-23.0.0.jar`; das ZIP führt kein eigenes `META-INF/MANIFEST.MF` (`--no-manifest`, Gutfall-Prüfung).

## 7. „lokal gelaufen, nicht in der CI gelaufen“ (`releasing.md`)

Die Aussage trägt: `make release-dateien` ist hier lokal mit einer Release-Version (Kopie), im Vorschau-Modus und mit dem erwarteten Abbruch gelaufen. In der CI ist es nicht gelaufen: `.github/workflows` ist nicht im Diff, kein Workflow ruft das Target (der Selbsttest hält das für `e2e.mk` und die Gate-Listen; `git diff c670a6cf..HEAD --stat` nennt keine Datei unter `.github/`). Der Satz trägt nicht weiter, als der Lauf reicht: er sagt nichts über Tag-Läufe.

## 8. Plan-vs-Code (`git diff c670a6cf..HEAD --stat`: 17 Dateien, +723 −20)

| Plan §3 | Diff |
|---|---|
| `Dockerfile`: neue Stage, Export-Stage, `skript-selbsttest` erweitert | ja (+ Kopfkommentar der Stage) |
| `tools/mk/` neues Fragment `make release-dateien` | `tools/mk/release-dateien.mk` neu |
| `tools/manifest-pruefung.sh`, `tools/manifest-selbsttest.sh` | beide geändert (`--ohne-pom`, Selbsttest-Fall) |
| Prüfskript der Stage | `tools/release-pruefung.sh` neu; zusätzlich `tools/release-dateien.sh` (Treiber der Stage, nicht in §3 genannt, im Plan implizit „Archiv“) |
| `tools/mk/skript-selbsttest.mk` Hilfezeile, Selbsttest-Skript(e) | `tools/release-selbsttest.sh` neu; `skript-selbsttest.mk` nicht im Diff, nicht nötig (die Stage ruft den Test auf) |
| `tools/stempel/*.eingaenge` | `werkzeug.eingaenge`: `lizenzen`; `make stempel-eingaenge-pruefung` grün |
| `harness/README.md`, `releasing.md` | ja |
| nicht in §3 | `.dockerignore`, `.gitignore` (Ausgabeverzeichnis `release-dateien`), zwei Review-Reports, Planpflege in `slice-release-job-im-workflow-bauen` und `slice-release-im-tag-lauf-messen` (Re-Review F-5, N-3: Folge-Slices nachgezogen), Slice §2/§6 |

Nichts im Diff außerhalb des Zuschnitts von §1 (kein `.github/`, kein `build.gradle.kts`, kein `src/`, kein `Makefile`). Ohne Befund.

## 9. Findings

| ID | Kategorie | Befund | Pfad |
|---|---|---|---|
| F-1 | LOW | Frage an den Nutzer (nichts entschieden): Die Versionsprüfung vor `docker build` läuft per Host-`sh` mit POSIX-Werkzeugen. Ob `AGENTS.md` 3.1 das einschließt, ist offen; Standardantwort „zulässig, wie `tools/publish.sh`“. Wird sie verneint, entfällt die Vorabprüfung und `-SNAPSHOT` bricht erst nach dem Bau von `build-jar` (Kosten: ein Bau). | `tools/mk/release-dateien.mk`, `tools/release-dateien.sh` |
| F-2 | LOW | Commit `08fba8d3` ändert Skripte nach dem Re-Review (`VORSCHAU` nur 1 oder leer, Abweisung eines Vorschau-ZIP in einer Release-Ausgabe, `release-pruefung.sh`, `release-selbsttest.sh`); ein dritter Review in frischem Kontext liegt nicht vor (Plan §3 Ablauf 3: „bei Code ein Re-Review“, die Nachbesserung eines Re-Reviews ist darin nicht geregelt). Mein Befund: `VORSCHAU=x` bricht, der Selbsttest ist grün, die Mutation c1/c2 trifft den neuen Code; die neuen Zweige habe ich nicht einzeln mutiert. Entscheidung, ob ein weiterer Review nötig ist, beim Planner. | `tools/release-pruefung.sh`, `tools/mk/release-dateien.mk` |
| F-3 | LOW | Die Zelle „Vertrag“ der Zeile `make skript-selbsttest` führt jetzt `e2e.sh: Abbruch` statt „`e2e.sh` bricht an roter Stufe“; die Zeile steht sonst vollständig (Re-Review F-4 behoben). Die Kürzung ist kleiner als im Erst-Review, aber eine Angabe weniger gegenüber `main`. | `harness/README.md` |
| F-4 | INFO | Maskierung in der `&&`-Kette ist real (a1) und im Dockerfile und Plan §6 als Grenze benannt; `testserver-im-jar` wird von `manifest-selbsttest` und, einzeln, von `release-selbsttest` abgefangen (a2). Kein Befund gegen die DoD. | `Dockerfile` (Stage `skript-selbsttest`) |
| F-5 | INFO | `make gates` lief mit Stempel-Beleg für `werkzeug`, `tests`, `stempel`. Ein Frischlauf von `make gates` mit `FORCE=1` hätte Stempel (git notes) geschrieben und ist nicht gefahren; die Aussage der Gruppen trägt stattdessen durch meine eigenen Läufe: Stage `skript-selbsttest` frisch (`--no-cache`), `make clean-build` (Jar, Tests, detekt, `--no-cache`), `make e2e`. Nicht frisch: `make referenztest-dotnet` (Klasse `tests`, .NET), `make detekt-selbsttest` als eigener Lauf; beide berührt der Diff nicht (kein `src/`, kein .NET). | `harness/README.md` §Sensors |
| F-6 | INFO | Die Byte-Gleichheit des ZIP aus zwei Läufen ist nicht zugesagt; in meinem Cache-Lauf kam dieselbe SHA-256, das ist kein Beleg. Plan §6 trägt „weiter offen“. | `tools/release-dateien.sh` Kopf |
| F-7 | INFO | Mutation c2 zeigt, dass im Skript zwei Wächter gegen `-SNAPSHOT` stehen (der `-SNAPSHOT`-Zweig und die Formprüfung `<zahl>.<zahl>.<zahl>`); entfällt einer, bleibt der Lauf rot, nur die Meldung ändert sich. Der Selbsttest prüft die Meldung und fängt es. | `tools/release-dateien.sh` |
| F-8 | INFO | Offene Nutzerentscheidungen, nicht still entschieden: Form `lizenzen/lib/…` im ZIP (Fragen 2a bis 2c des Belegs; Sollmenge als Vorschlag), `tdswire.jar` ohne Vorschau-Marke, Auslegung des Vorschau-Modus als ADR oder `Schärft:`-Eintrag (Plan §6). Der Bau trifft sie nicht. | Beleg, Plan §6 |

## 10. Negativbefund-Pflicht

| Bereich | Ergebnis |
|---|---|
| DoD 1 bis 3 am Lauf (Release-Version, Vorschau, SNAPSHOT, belegtes Verzeichnis) | geprüft, ohne Befund |
| Export ohne Mount, kein `build/` auf dem Host | geprüft, ohne Befund |
| Prüfung am entpackten Archiv, nicht am Baum | geprüft (b2 am echten ZIP), ohne Befund |
| Sollmenge sieben Dateien, NOTICE einzeln | geprüft (b1, b2), ohne Befund |
| Selbsttest-Mutationen, nicht in der Kette sichtbare Fälle | geprüft (a2), ohne Befund |
| `make build` / `build-jar` unverändert | geprüft (Diff), ohne Befund |
| Gate-Listen, `e2e.mk`, Stempel-Eingangslisten | geprüft (`gate-zuordnung`, `stempel-eingaenge-pruefung`), ohne Befund |
| Kein Token, kein `gh`, kein Netz zu einem Dienst im Ablauf | gelesen (`release-dateien.sh`, `release-pruefung.sh`, Fragment, Stage), ohne Befund; der Bau selbst braucht Netz nur wie `build-jar` |
| Zahlen und Aussagen der Doku gegen den Lauf (3,4 s, drei Dateien, sieben Dateien) | geprüft, ohne Befund |
| Haken im Plan | unverändert (`[ ]`), von mir nicht gesetzt |

## 11. Verdikt

Die drei Liefer-Punkte der DoD sind erfüllt, die Nachweise Gates, Review und Doku liegen; Closure-Notiz, Register, Risiko-Ausgänge und Paarungen stehen vor der Closure aus. Kein HIGH, kein MEDIUM. Der Planner entscheidet über F-2 (weiterer Review der Nachbesserung) und nimmt F-3 auf; der Nutzer beantwortet F-1 und die Form im ZIP (F-8).
