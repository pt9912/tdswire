# Testserver als Gradle-Modul: Messprotokoll vom 2026-11-06

Messprotokoll zu [TDS-RB-01](../../spec/lastenheft.md#tds-rb-01--implementierungssprache) und [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) (`AGENTS.md` §5 Regel 7), Grundlage der Messpunkte M1, M2 und M4 von [ADR-0034](../plan/adr/0034-testserver-als-gradle-modul.md). Es hält fest, was gemessen wurde, nicht, ob eine Anforderung belegt ist. Stand dieses Protokolls: Messpunkte M1 bis M7, dazu die Läufe E1 und E2 und G1 und G2.

**Vorbehalt, der für jede Zeile gilt.** Jede Zeile ist ein Einzellauf auf einem Rechner unter fremder Last. Die Zeiten vergleichen nichts und sind keine Leistungszusage. Die Läufe liegen im Arbeitsbaum des Zweigs `slice-testserver-gradle-modul` nach dem reinen Umzug `src/testserver` → `testserver` und dem Modul-Stand dieses Commits; die Rohprotokolle liegen im Scratchpad der Sitzung und nicht im Repo.

## Bedingungen

| Angabe | Wert |
|---|---|
| Datum | 2026-11-06 |
| Host | Linux 6.8, 20 Kerne, 31 GiB; Load Average (1 Minute) zwischen 3,5 und 16, gelesen mit `uptime` und `/proc/loadavg` während der Läufe; die Ursache der Last ist nicht geklärt und wird hier nicht behauptet |
| Werkzeug | Docker-Stages des Repos über `make` bzw. `docker build --target …`; Basis `gradle:8.14-jdk21` (Digest im `Dockerfile`) |
| Aufbau | Wurzelprojekt = Bibliothek (`src/main`, Jar `tdswire.jar`); Modul `:testserver` mit `implementation(project(":"))`; Verzeichnisse des Moduls `testserver/kotlin`, `testserver/test/kotlin`, `testserver/resources` |

## M1 — bricht ein Zugriff auf `internal` die Übersetzung des Moduls?

| Nr. | Lauf | Ergebnis |
|---|---|---|
| M1.1 | Modul ohne Sensor `testserverOhneAssoziation` (Source-Set und Sensor sind aus der Wurzel-Build-Datei entfernt), `docker build --target compile` | grün: `:testserver:compileKotlin` läuft durch |
| M1.2 | Mutation: Datei `testserver/kotlin/io/github/pt9912/tdswire/testserver/MutationM1.kt` mit `import io.github.pt9912.tdswire.simulation.SqlLexemes` (`internal object` der Bibliothek) und `internal fun mutationM1() = SqlLexemes`, gleicher Lauf | **rot**: `:testserver:compileKotlin FAILED`, `e: …MutationM1.kt:3:44 Cannot access 'object SqlLexemes : Any': it is internal in file.` und `…:5:29` dieselbe Meldung; die Datei ist danach wieder entfernt |

Aussage, die M1 trägt: Ein Zugriff auf einen `internal`-Namen der Bibliothek aus Kotlin-Quellen des Moduls bricht dessen Übersetzung (eine Mutation, ein Name). Nicht gemessen: Java-Quellen, Reflection, ein zweiter `internal`-Name; die Grenze „im Bytecode öffentlich“ steht unverändert.

## M2 — trägt der Laufzeit-Klassenpfad das Jar, nicht das Klassenverzeichnis?

Klassenpfad-Datei der Stage `testserver` (`testserver/build/testserver-klassenpfad.txt`), Einträge zeilenweise, Cache-Pfade der Fremd-Jars gekürzt:

```text
/src/testserver/build/classes/java/main
/src/testserver/build/classes/kotlin/main
/src/testserver/build/resources/main
/src/build/libs/tdswire.jar
…/kotlinx-coroutines-core-jvm-1.10.1.jar
…/kotlin-stdlib-2.4.10.jar
…/jackson-databind-2.21.7.jar
…/jackson-core-2.21.7.jar
…/jackson-dataformat-yaml-2.21.7.jar
…/annotations-23.0.0.jar
…/snakeyaml-2.5.jar
…/jackson-annotations-2.21.jar
```

| Nr. | Lauf | Ergebnis |
|---|---|---|
| M2.1 | `implementation(project(":"))` unverändert, Stage `testserver` | Die Standardwahl von Gradle für die Projekt-Abhängigkeit liefert zur Laufzeit das Jar `/src/build/libs/tdswire.jar` und kein `build/classes/kotlin/main` der Bibliothek; eine zusätzliche Form (Jar-Variante) war nicht nötig. Die drei übrigen Wurzel-Einträge liegen im Modul selbst |
| M2.2 | Jar in der Stage geöffnet: `META-INF/MANIFEST.MF` mit `Class-Path: lib/…`; **ohne** `COPY LICENSE` in der Stage fehlt `META-INF/LICENSE` im Jar (`unzip -l`) | Befund: die Task `jar` der Bibliothek nimmt die Lizenz nur auf, wenn die Datei im Stage-Verzeichnis liegt. Die Stages `smoke`, `testserver`, `integrationsserver` und `kompatibilitaetsserver` kopieren seither `LICENSE` |
| M2.3 | Stage `testserver` mit `COPY LICENSE` | `unzip -l` zeigt `META-INF/MANIFEST.MF` und `META-INF/LICENSE`; `unzip -p … META-INF/LICENSE \| cmp - LICENSE` ohne Unterschied |
| M2.4 | Mutation: `implementation(project(":"))` durch `implementation(<main.output der Wurzel>)` ersetzt (Klassenverzeichnis statt Jar), Stage `testserver` | **rot**: `:testserver:testserverKlassenpfad` bricht mit „Klassenpfad des Testservers nennt tdswire.jar nicht“; die Task prüft seit diesem Commit, dass `tdswire.jar` im Klassenpfad steht und kein Verzeichnis außerhalb des Moduls |

Ein Lauf in der Kind-JVM und im Container nutzt dieselbe Datei: `make smoke` (M4.1) startet den Testserver mit diesem Klassenpfad. | Nr. | Lauf | Ergebnis |
|---|---|---|
| M2.5 | Images der Stages `integrationsserver` und `kompatibilitaetsserver` gebaut und gestartet (`docker run -d`, 25 s Wartezeit); in `/proc/<pid>/cmdline` der Server-JVM den Klassenpfad gelesen | beide Klassenpfade beginnen mit `/src/testserver/build/classes/java/main:/src/testserver/build/classes/kotlin/main:/src/testserver/build/resources/main:/src/build/libs/tdswire.jar:` und führen danach Fremd-Jars aus dem Gradle-Cache (Jackson 2.21.7, `snakeyaml-2.5.jar`); `build/classes` der Bibliothek steht nicht darin. In beiden Images enthält `/src/build/libs/tdswire.jar` `META-INF/MANIFEST.MF` und `META-INF/LICENSE` (`unzip -l`, zwei Treffer je Image). Die Server-JVM des Integrationsservers trägt `-Xmx48m`, sein Log meldet „Heap max 50331648 Bytes“ |
| M2.6 | Stage `laufzeit-client` | nicht über das Modul: die Task `laufzeitClient` nutzt `sourceSets.test.runtimeClasspath` der Wurzel; die Stage baut `testClasses` und startet den Client im Lauf von `make laufzeittest` (Stufe 2 von `make e2e`, grün, siehe E1) |

Nicht gemessen in M2.5: der Klassenpfad des Prozesses von `make testserver-up` (Stage `testserver` liest die Klassenpfad-Datei, M2.1) im laufenden Container.

## M4 — Zyklus zwischen Wurzel-`test` und Modul?

| Nr. | Lauf | Ergebnis |
|---|---|---|
| M4.1 | `make smoke` (Gradle-Aufruf `:test --tests …ServerSmokeTest`) | grün: die drei Tests von `ServerSmokeTest` bestanden, darunter „Serverprozess beantwortet den Login-Pfad mit Rohbytes“ (Kind-JVM mit `tdswire.testserverclasspath` aus der Datei des Moduls) und „Ein Prozess ohne JVM-Option startet mit TLS“; 47 s Gradle-Zeit, 50,7 s Wanduhr |
| M4.2 | `make test` | grün: `BUILD SUCCESSFUL in 1m 50s`; Reihenfolge der Tasks im Protokoll: `:integratorTest`, `:testserver:testserverKlassenpfad`, `:testserver:test`, `:test`; 933 Zeilen „PASSED“, kein Test rot; 1 m 55 s Wanduhr |
| M4.3 | Mutation: `tasks.named("testserverKlassenpfad") { dependsOn(":test") }` an `testserver/build.gradle.kts` angehängt (die Rückkante Modul → Wurzel-`test`), `make smoke` | **rot**: „Circular dependency between the following tasks: :test \--- :testserver:testserverKlassenpfad \--- :test“ |

Aussage, die M4 trägt: Die Kante Wurzel-`test` → Klassenpfad-Datei des Moduls → Jar der Bibliothek hat keinen Zyklus. Die Wurzel löst `:testserver` nicht als Konfiguration auf, sondern liest die Datei der Modul-Task. Nicht gemessen: ob die Zahl der Tests der Zahl vor dem Umzug gleicht (der Vergleichslauf vorher kam aus dem Cache, ohne Einzelzeilen).

## Nebenbefunde aus Lauf 1 (M3, M5 sind unten abgeschlossen)

| Nr. | Beobachtung | Grenze |
|---|---|---|
| N1 | `make deps-lock` schreibt `testserver/gradle.lockfile` (101 Zeilen) mit Jackson (`jackson-core`, `-databind`, `-annotations`, `-dataformat-yaml`, `-bom`); die Wurzel-`gradle.lockfile` führt danach keine Zeile mit `jackson` und keine mit `snakeyaml` mehr (vorher 5 Jackson-Zeilen und `snakeyaml`). Die Menge der Koordinaten `Gruppe:Artefakt:Version` aus Wurzel- und Modul-Sperre gleicht der der alten Wurzel-Sperre (`diff` der sortierten Koordinaten leer). `gradle/verification-metadata.xml` und `settings-gradle.lockfile` blieben unverändert. Die Wurzel-Sperre verliert die Namen `testserver*Classpath` in den Spalten | Einzellauf, 4 m 8 s; Erklärung des Diffs und Rot-Gegenprobe: M3 |
| N2 | `make detekt` (Stage mit `suppression-check.sh src testserver`, ohne Task `detektTestserver`) grün: `:detekt`, `:testserver:detekt`, `:detektMain`, `:detektIntegrator`, `:testserver:detektMain`, `:testserver:detektTest`, `:detektTest`; `config/detekt/detekt.yml` unverändert | `make detekt-selbsttest`: M5 |
| N3 | `make build` grün (Stage `build-jar` mit `gradle :assemble`, Manifest-Prüfung) | nur Exit-Code gelesen |
| N4 | Die Stage `testserver` mit `--offline` läuft erst, seit `abhaengigkeitenLaden` die Sperre des Moduls mitliest und `testserver/gradle.lockfile` im Eingang der Stage `deps` liegt; davor brach sie (Ursache nicht aus dem Protokoll gelesen; erwartet: Jackson fehlt im Cache) | Rot-Gegenprobe: M3.4 |

## M3 — Lock und Prüfsummen mit zwei Projekten

| Nr. | Lauf | Ergebnis |
|---|---|---|
| M3.1 | `make deps-lock` (Netz) auf dem Stand dieses Protokolls | Exit 0; `git status` nennt danach für `gradle.lockfile`, `testserver/gradle.lockfile`, `settings-gradle.lockfile` und `gradle/verification-metadata.xml` keine Änderung gegenüber dem committeten Stand: kein Diff, keine Versionsänderung |
| M3.2 | `git diff 76a2e07c HEAD -- gradle.lockfile` (Wurzel-Sperre vor dem Umzug gegen jetzt), jede Zeile gelesen | Entfallen sind nur Zeilen, die ausschließlich die Konfigurationen `testserverCompileClasspath` und `testserverRuntimeClasspath` führten: `jackson-annotations 2.21`, `jackson-core`, `jackson-databind`, `jackson-dataformat-yaml`, `jackson-bom` (je 2.21.7) und `snakeyaml 2.5`. Alle übrigen geänderten Zeilen (unter anderem `kotlin-stdlib`, `kotlin-test`, `junit-*`, `opentest4j`, `kotlinx-coroutines-*`, `annotations`, `apiguardian`, die `kotlin-scripting-*`-Zeilen und die Zeile `empty=`) verlieren nur die Namen `testserver…Classpath` in der Spalte der Konfigurationen oder stehen in anderer Reihenfolge der Spalte; Koordinate und Version bleiben |
| M3.3 | Zählung: Koordinaten in `gradle.lockfile` (nach Lock) 91, in `testserver/gradle.lockfile` 97, in der Wurzel-Sperre vor dem Umzug 97 (`grep -v '^#'`, ohne `empty`, `cut -d= -f1`, `sort -u`); `grep -c jackson`: Wurzel 0, Modul 5 | die Jackson-Aussage steht jetzt in der Sperre des Moduls. Beide Sperren führen die Kotlin- und JUnit-Koordinaten, deshalb ist die Summe der Zahlen nicht die Zahl der Vereinigung; die Gleichheit der Vereinigung mit der alten Sperre hielt N1 aus Lauf 1 fest |
| M3.4 | Rot-Gegenprobe: in `build.gradle.kts` die Liste der Projekte von `abhaengigkeitenLaden` auf `listOf(project)` verkürzt (Modul fehlt), `docker build --target testserver` (Stage mit `--offline`) | **rot**: `:testserver:compileKotlin FAILED`, „Could not resolve all files for configuration ':testserver:compileClasspath'. > Could not resolve com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.21.7.“; die Datei ist danach wieder im Ausgangszustand |

Grenze: M3.4 mutiert die Task, nicht die Sperre des Moduls; dass eine fehlende Datei `testserver/gradle.lockfile` die Stage `deps` bricht, ist nicht einzeln gemessen (das `COPY` der Stage nennt sie).

## M5 — trifft eine Regel von detekt das Modul?

| Nr. | Lauf | Ergebnis |
|---|---|---|
| M5.1 | `make detekt-selbsttest` (Stage `detekt-selbsttest`), `config/detekt/detekt.yml` unverändert | Exit 0; Meldungen: 9 Importregeln und 69 verbotene Importe gemeldet, 45 Aufruf-Fälle gemeldet, 10 Formen von `@Suppress` gemeldet, 4 Formen eines Jackson-Verweises gemeldet, 6 Formen eines `friend`- oder `associate`-Eintrags gemeldet (neuer Modus `modulgrenze`, siehe S1) |
| M5.2 | `make detekt` (Stage `detekt` mit den Sperren und allen detekt-Tasks), Lauf 1 (N2) und in `make gates FORCE=1` dieses Laufs | grün; Ergebnis des Gate-Laufs: siehe unten G1 |

## S1 — Sperre der Modulgrenze (`tools/modulgrenze-sperre.sh`)

Das Skript meldet jede Zeile mit `friend` oder `associate` (Groß-/Kleinschreibung gleich) in `testserver/build.gradle.kts`; es läuft in der Stage `detekt` (Klasse `tests`), sein Selbsttest ist der Modus `modulgrenze` von `tools/detekt-selbsttest.sh` (Klasse `werkzeug`). Das Skript steht in `tools/stempel/tests.eingaenge`.

| Nr. | Lauf | Ergebnis |
|---|---|---|
| S1.1 | Selbsttest, sechs erzeugte Dateien mit `associateWith`, `-Xfriend-paths`, `friendPaths`, `associatedCompilations`, einem Kommentar mit `associateWith` und `FRIEND`; eine Datei ohne diese Namen; eine fehlende Datei | alle sechs gemeldet, die Datei ohne die Namen durchgelassen, die fehlende Datei endet mit Exit 2 |
| S1.2 | Mutation des Skripts: Muster `associate` entfernt (`-e 'friend'` allein), `make detekt-selbsttest` | **rot**: „tools/modulgrenze-sperre.sh meldet build/modulgrenze-test/verboten/AssociateWith.kts nicht“; Skript danach wiederhergestellt |
| S1.3 | Mutation der Build-Datei: Zeile `// x associateWith(y)` an `testserver/build.gradle.kts` angehängt, `make detekt` | **rot**: Stage `detekt` bricht mit „modulgrenze-sperre: FEHLER - die Modulgrenze des Testservers bleibt zu“; Zeile danach entfernt |

Grenzen: der Text einer Datei; ein Eintrag in einer anderen Datei oder ein anderer Weg, `internal` zu öffnen, ist nicht erkannt (Kopf des Skripts).

## M6 — Eingangslisten der Stempel

| Nr. | Lauf | Ergebnis |
|---|---|---|
| M6.1 | `make stempel-eingaenge-pruefung` vor dem Nachzug der Listen (Stand nach Lauf 1) | **rot**: 17 Befunde, alle „kein Eintrag deckt ‚testserver…‘“ in `tests.eingaenge` (Stages `test`, `compile`, `detekt`, `build-jar`, `smoke`) und `werkzeug.eingaenge` (Stage `detekt-selbsttest`) |
| M6.2 | nach `testserver` in `tests.eingaenge` und `testserver/build.gradle.kts`, `testserver/gradle.lockfile`, `testserver/kotlin/…/IntegrationServer.kt` in `werkzeug.eingaenge` | noch 1 Befund: das `COPY` der Stage `kopplung-zahlen` nannte noch `src/testserver/kotlin/…/IntegrationServer.kt` |
| M6.3 | nach Nachzug von Dockerfile (`COPY testserver/build.gradle.kts`, `COPY testserver/kotlin/…/IntegrationServer.kt`) und der Prüfung der Zahlen | **grün**: `stempel-eingaenge: ok - alle COPY-Quellen …` |
| M6.4 | `make stempel-eingaenge` / `make stempel-abbild` / `make stempel-gruppe` | grün: 95 / 99 / 64 Fälle wie erwartet (die Fallzahlen der Ausgaben; `stempel-eingaenge-selbsttest.sh` führt nun auch `tests:testserver`, `werkzeug:testserver/build.gradle.kts` und `werkzeug:testserver/gradle.lockfile` unter den entfernten Einträgen) |
| M6.5 | Mutation: Eintrag `tools/modulgrenze-sperre.sh` aus `tests.eingaenge` entfernt, `make stempel-eingaenge-pruefung` | **rot**: „tests.eingaenge: kein Eintrag deckt ‚tools/modulgrenze-sperre.sh‘ (Stage detekt)“; Liste danach wiederhergestellt |
| M6.6 | `make kopplung-zahlen` (mit Selbsttest, acht Mutationen) | grün (Exit 0); Mutation: Pfad der Prüfung wieder auf `build.gradle.kts` der Wurzel gesetzt: **rot** „Datei fehlt: ./build.gradle.kts“ (die Heap-Zeile steht jetzt in `testserver/build.gradle.kts`); danach wiederhergestellt |

Grenze: die Pfade in Skripten ohne `COPY` (zum Beispiel `tools/testserver.sh`) sieht die Prüfung nicht; sie liegen unter `tools`, das `werkzeug.eingaenge` führt, und in `tests.eingaenge` stehen nur die benannten Skripte.

## E1 — E2E und Läufe auf dem neuen Aufbau (Einzelläufe; Load Average (1 Minute) zu Beginn von `make e2e` 5,07 und von `make testserver-selbsttest` 7,05, gelesen mit `uptime`)

| Nr. | Lauf | Ergebnis |
|---|---|---|
| E1.1 | `make e2e` | Stufen 1 bis 6 grün: 1 `make smoke` 52 s, 2 `make laufzeittest` 86 s, 3 `make simulations-integration` 51 s, 4 `make kompatibilitaet KATEGORIE=Szenario` 48 s, 5 `KATEGORIE=Anmeldung` 17 s, 6 `KATEGORIE=Luecken` 16 s. **Stufe 7 (`KATEGORIE=Synchronitaet`) rot**: von 4 Tests 3 bestanden; `Anfragefehler_im_Batch_und_in_der_RPC_lassen_die_naechste_Anfrage_nur_ihre_Antwort_erhalten` erwartet für `SELECT 1` eine `SqlException` (50001), der Server beantwortet `SELECT 1` (Form C, SPEC-036) |
| E1.2 | derselbe Test am Merge-Base `f1d1c429` (Zustand vor dem Slice, `git archive` in das Scratchpad, `make kompatibilitaet KATEGORIE=Synchronitaet`) | **gleich rot**: 4 Tests, 3 bestanden, 1 fehlgeschlagen; das Ergebnis hängt nicht am Umbau dieses Slice |
| E1.3 | `make kompatibilitaet KATEGORIE=Synchronitaet` im Hauptarbeitsbaum auf `main` (`cd0e2368`) | grün, 10 Tests; die Testdatei auf `main` ist eine andere (31 Commits nach dem Merge-Base) |
| E1.4 | `make testserver-selbsttest` | grün, 71 s laut Ausgabe; alle Mutationen rot gesehen (Ausgabe „Mutation … färbt rot“, neun Zeilen) |
| E1.5 | Mutation: in `tools/testserver.sh` der Pfad der mitgelieferten Beschreibung wieder `/src/src/testserver/resources/manuell/sim.yaml`, `make testserver-up` | **rot**: „tdswire: Konfiguration ungueltig: SIMULATION_DESCRIPTION_FILE: UNREADABLE“; Pfad danach wiederhergestellt |

Aussage, die E1 trägt: die Stufen 1 bis 6 laufen auf dem Aufbau mit Modul grün. Stufe 7 ist am Merge-Base ebenso rot; ob sie nach einem Zusammenführen mit `main` grün ist, beantwortet E2.

## G1 — `make gates FORCE=1`

| Nr. | Lauf | Ergebnis |
|---|---|---|
| G1.1 | `make gates FORCE=1` auf dem Arbeitsbaum vor dem Commit der Skripte und Listen | 5 m 21 s; alle Gates bis auf `docs-check` grün (933 Zeilen „PASSED“ im Protokoll der Stage `test`); `docs-check` rot: „Zelle der Spalte ‚Vertrag‘ hat 296 Zeichen, erlaubt sind 200“ in der Zeile von `make detekt` in `harness/README.md`; Stempel nicht geschrieben („Eingangspfade nicht sauber“, die Änderungen waren nicht committet) |
| G1.2 | Zelle gekürzt, committet (`ee93d602`), `make gates FORCE=1` erneut | Exit 0, 28,8 s; die Docker-Stages kamen aus dem Layer-Cache des Laufs G1.1 (70 Zeilen „CACHED“ im Protokoll), die vier Selbsttests der Stempel, `gate-zuordnung` (15 Gates, Selbsttest 33 Fälle), `stempel-eingaenge` und `docs-check` (812 Dateien, 0 Befunde) liefen |

Grenze: G1.2 belegt die Stages über den Layer-Cache; die ausgeführte Fassung der Stages ist die aus G1.1 mit gleichem Inhalt der Eingänge.

## E2 und G2 — Läufe nach dem Zusammenführen mit `main`

Stand: Zweig `slice-testserver-gradle-modul` auf `a572c5de` (Merge von `main` `cd0e2368` in den Zweig; bringt unter anderem `LaufendeQuelle.kt` und die Cancel-Tests), Arbeitsbaum sauber. Einzelläufe nacheinander; Load Average (1 Minute) zu Beginn des jeweiligen Laufs aus `uptime`.

| Nr. | Lauf | Ergebnis |
|---|---|---|
| G2.1 | `make gates FORCE=1`, Load 5,23 | Exit 0, 309 s; im Protokoll 933 Zeilen „PASSED“ (Stage `test`), keine Zeile mit „FAILED“ für einen Test; `stempel-selbsttest` 118, `stempel-eingaenge` 95, `stempel-abbild` 99, `stempel-gruppe` 64 Fälle wie erwartet; `d-check` 843 Dateien, 0 Befunde; `gate-zuordnung` 15 Gates, Selbsttest 33 Fälle |
| E2.1 | `make e2e`, Load 7,20 | Exit 0, 293 s, alle sieben Stufen grün: 1 `make smoke` 48 s, 2 `make laufzeittest` 85 s, 3 `make simulations-integration` 57 s, 4 `KATEGORIE=Szenario` 38 s, 5 `KATEGORIE=Anmeldung` 15 s, 6 `KATEGORIE=Luecken` 14 s, 7 `KATEGORIE=Synchronitaet` 36 s |
| E2.2 | Stufe 7 im Einzelnen | „Alle Tests der Kategorie Synchronitaet bestanden (Total tests: 10)“; darunter `Anfragefehler_im_Batch_und_in_der_RPC_lassen_die_naechste_Anfrage_nur_ihre_Antwort_erhalten` (in E1.1 rot) und `CancellationToken_bricht_die_laufende_Anfrage_ab_…` |
| E2.3 | `make testserver-selbsttest`, Load 5,93 | Exit 0, 61 s Wanduhr, 60 s laut Ausgabe; sieben Zeilen „Grundlage“ und neun Zeilen „Mutation … färbt rot“ |
| E2.4 | `make docs-check` | Exit 0, 843 Dateien, 0 Befunde (auf dem Stand vor den Nachzügen von Spezifikation und Doku) |

Aussage, die E2 trägt: Auf dem Stand nach dem Zusammenführen sind `make gates FORCE=1`, alle sieben Stufen von `make e2e` und `make testserver-selbsttest` grün; Stufe 7 war in E1.1 am Merge-Base rot, weil `SELECT 1` Form C ist, und ist nach dem Zusammenführen grün. Ob der Wechsel an der Testdatei von `main` oder am Modul hängt, trennt dieser Lauf nicht (E1.2 und E1.3 sind die Vorläufe).

## M7 — Laufzeiten vorher und nachher

**Zuschnitt.** „Vorher“ ist `main` `cd0e2368` (der zweite Elternteil des Merges `a572c5de`), „nachher“ ist `a572c5de`: beide tragen dieselben Quellen der Bibliothek und der Tests von `main`, der Unterschied ist das Modul. Der Merge-Base `f1d1c429` ist 31 Commits älter und hätte den Unterschied von `main` mitgemessen; er ist hier nicht gemessen. Beide Stände liegen als `git archive` in getrennten Wegwerf-Verzeichnissen im Scratchpad (danach entfernt) und teilen den Docker-Cache des Hosts.

**Ablauf.** Je Stand und Runde: ein Kommentar in `src/main/kotlin/io/github/pt9912/tdswire/runtime/Telemetry.kt` (Zeile 25) wird um eine Markierung der Runde ergänzt, damit alle Stages ab `compile` neu laufen, danach nacheinander `make build`, `make test`, `make detekt`, `make smoke`, `make laufzeittest` (Spalte „Quelle“). Reihenfolge: Runde 1 vorher, nachher; Runde 2 nachher, vorher. Vor jedem Target ist der Load Average (1 Minute) gelesen. Anschließend je Stand dieselben fünf Targets ohne Änderung („warm“).

| Target | vorher R1 | nachher R1 | nachher R2 | vorher R2 |
|---|---|---|---|---|
| `make build` | 34 s | 86 s | 40 s | 37 s |
| `make test` | 88 s | 98 s | 90 s | 92 s |
| `make detekt` | 142 s | 156 s | 149 s | 156 s |
| `make smoke` | 45 s | 51 s | 51 s | 50 s |
| `make laufzeittest` | 77 s | 87 s | 86 s | 95 s |
| Load (1 min) vor dem ersten Target des Blocks | 6,29 | 8,08 | 10,90 | 11,09 |

Load (1 Minute) vor `make test`, `detekt`, `smoke`, `laufzeittest` je Block in dieser Folge: vorher R1 9,36, 6,94, 9,03, 8,93; nachher R1 7,38, 8,37, 7,68, 9,05; nachher R2 10,57, 7,05, 10,50, 10,78; vorher R2 10,31, 8,65, 10,30, 8,82. Alle Läufe Exit 0; in den vier Läufen von `make test` stehen je 933 Zeilen „PASSED“ (die Zahl der Tests gleicht damit vorher und nachher; in M4.2 war sie nicht verglichen).

Warm (ohne Änderung, Stages aus dem Cache): `make build`, `make test`, `make detekt`, `make smoke` je 1 bis 2 s; `make laufzeittest` 12 s und 24 s vorher, 15 s und 15 s nachher. Diese Zeilen messen den Cache, nicht die Arbeit.

**Lesung, so weit sie trägt.** Die Spannweiten von vorher und nachher überlappen bei `make test`, `detekt`, `smoke` und `laufzeittest`; der Abstand zwischen den Spannweiten (zum Beispiel `make smoke`: 45 und 50 s gegen 51 und 51 s) ist kleiner als die Streuung zwischen zwei Läufen desselben Standes bei `make laufzeittest` (77 und 95 s). Bei `make build` liegt ein Lauf nachher (86 s) deutlich über den übrigen drei (34 bis 40 s); die Ursache ist nicht untersucht. Eine Schwelle gilt nicht.

**Grenzen.** Zwei Läufe je Zelle, ein Rechner, Last von 6 bis 11 und nicht konstant (zum Ende des Gesamtlaufs 25); die Ursache der Last ist nicht geklärt. Die Zeiten sagen nichts über Kaltläufe (kein `--no-cache`), nichts über den Cache anderer Hosts und nichts über `make gates` als Ganzes. Frühere Läufe desselben Tages (zwei je Target, eine Runde mit Quelländerung) liegen nur als Rohprotokolle ohne Zeitangabe im Scratchpad, ihr Stand ist nicht festgehalten und der erste Lauf von `make test` kam aus dem Cache; sie fließen hier nicht ein.

## Offen

Nichts Gemessenes offen. Nicht gemessen und oben benannt: Kaltläufe, Java-Quellen und Reflection an der Modulgrenze (M1), eine fehlende `testserver/gradle.lockfile` (M3), der Klassenpfad des Prozesses von `make testserver-up` im laufenden Container (M2.5).
