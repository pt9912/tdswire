# Testserver als Gradle-Modul: Messprotokoll vom 2026-11-06

Messprotokoll zu [TDS-RB-01](../../spec/lastenheft.md#tds-rb-01--implementierungssprache) und [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) (`AGENTS.md` §5 Regel 7), Grundlage der Messpunkte M1, M2 und M4 von [ADR-0034](../plan/adr/0034-testserver-als-gradle-modul.md). Es hält fest, was gemessen wurde, nicht, ob eine Anforderung belegt ist. Stand dieses Protokolls: Messpunkte M1, M2 und M4; die Punkte M3, M5, M6 und M7 stehen unten als offen.

**Vorbehalt, der für jede Zeile gilt.** Jede Zeile ist ein Einzellauf auf einem Rechner unter fremder Last. Die Zeiten vergleichen nichts und sind keine Leistungszusage. Die Läufe liegen im Arbeitsbaum des Zweigs `slice-testserver-gradle-modul` nach dem reinen Umzug `src/testserver` → `testserver` und dem Modul-Stand dieses Commits; die Rohprotokolle liegen im Scratchpad der Sitzung und nicht im Repo.

## Bedingungen

| Angabe | Wert |
|---|---|
| Datum | 2026-11-06 |
| Host | Linux 6.8, 20 Kerne, 31 GiB; Load Average (1 Minute) zwischen 3,5 und 16 durch fremde Läufe (gelesen mit `uptime` und `/proc/loadavg` während der Läufe) |
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

Ein Lauf in der Kind-JVM und im Container nutzt dieselbe Datei: `make smoke` (M4.1) startet den Testserver mit diesem Klassenpfad. Nicht gemessen: ob der Klassenpfad der Stages `integrationsserver` und `kompatibilitaetsserver` (Gradle-Task `integrationsServer`, `kompatibilitaetsServer`) dasselbe Jar nennt; diese Stages liefen in diesem Lauf nicht.

## M4 — Zyklus zwischen Wurzel-`test` und Modul?

| Nr. | Lauf | Ergebnis |
|---|---|---|
| M4.1 | `make smoke` (Gradle-Aufruf `:test --tests …ServerSmokeTest`) | grün: die drei Tests von `ServerSmokeTest` bestanden, darunter „Serverprozess beantwortet den Login-Pfad mit Rohbytes“ (Kind-JVM mit `tdswire.testserverclasspath` aus der Datei des Moduls) und „Ein Prozess ohne JVM-Option startet mit TLS“; 47 s Gradle-Zeit, 50,7 s Wanduhr |
| M4.2 | `make test` | grün: `BUILD SUCCESSFUL in 1m 50s`; Reihenfolge der Tasks im Protokoll: `:integratorTest`, `:testserver:testserverKlassenpfad`, `:testserver:test`, `:test`; 933 Zeilen „PASSED“, kein Test rot; 1 m 55 s Wanduhr |
| M4.3 | Mutation: `tasks.named("testserverKlassenpfad") { dependsOn(":test") }` an `testserver/build.gradle.kts` angehängt (die Rückkante Modul → Wurzel-`test`), `make smoke` | **rot**: „Circular dependency between the following tasks: :test \--- :testserver:testserverKlassenpfad \--- :test“ |

Aussage, die M4 trägt: Die Kante Wurzel-`test` → Klassenpfad-Datei des Moduls → Jar der Bibliothek hat keinen Zyklus. Die Wurzel löst `:testserver` nicht als Konfiguration auf, sondern liest die Datei der Modul-Task. Nicht gemessen: ob die Zahl der Tests der Zahl vor dem Umzug gleicht (der Vergleichslauf vorher kam aus dem Cache, ohne Einzelzeilen).

## Nebenbefunde zu M3, M5 und M7 (Messpunkte gehören zu Liefer-Punkt 2 und 3, hier nicht abgeschlossen)

| Nr. | Beobachtung | Grenze |
|---|---|---|
| N1 | `make deps-lock` schreibt `testserver/gradle.lockfile` (101 Zeilen) mit Jackson (`jackson-core`, `-databind`, `-annotations`, `-dataformat-yaml`, `-bom`); die Wurzel-`gradle.lockfile` führt danach keine Zeile mit `jackson` und keine mit `snakeyaml` mehr (vorher 5 Jackson-Zeilen und `snakeyaml`). Die Menge der Koordinaten `Gruppe:Artefakt:Version` aus Wurzel- und Modul-Sperre gleicht der der alten Wurzel-Sperre (`diff` der sortierten Koordinaten leer). `gradle/verification-metadata.xml` und `settings-gradle.lockfile` blieben unverändert. Die Wurzel-Sperre verliert die Namen `testserver*Classpath` in den Spalten | Einzellauf, 4 m 8 s; die Erklärung jeder Zeile des Diffs und die Rot-Gegenprobe (Lock fehlt) stehen aus |
| N2 | `make detekt` (Stage mit `suppression-check.sh src testserver`, ohne Task `detektTestserver`) grün: `:detekt`, `:testserver:detekt`, `:detektMain`, `:detektIntegrator`, `:testserver:detektMain`, `:testserver:detektTest`, `:detektTest`; `config/detekt/detekt.yml` unverändert | `make detekt-selbsttest` lief nicht |
| N3 | `make build` grün (Stage `build-jar` mit `gradle :assemble`, Manifest-Prüfung) | nur Exit-Code gelesen |
| N4 | Die Stage `testserver` mit `--offline` läuft erst, seit `abhaengigkeitenLaden` die Sperre des Moduls mitliest und `testserver/gradle.lockfile` im Eingang der Stage `deps` liegt; davor brach sie (Ursache nicht aus dem Protokoll gelesen; erwartet: Jackson fehlt im Cache) | Rot-Gegenprobe nicht gesichert |

## Offen

M3 (Erklärung des Lock-Diffs, `make deps-lock` ohne Versionsänderung), M5 (`make detekt-selbsttest`), M6 (`make stempel-eingaenge-pruefung` rot vor dem Nachzug der Listen, grün danach) und M7 (Laufzeiten vorher und nachher) sind nicht gemessen; sie gehören zu den Liefer-Punkten 2 und 3 des Slice.
