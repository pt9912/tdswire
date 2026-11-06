# ADR-0034: Testserver als eigenes Gradle-Modul mit Projekt-Abhängigkeit auf die Bibliothek

**Status:** Proposed (Entwurf des Planners; Wortlaut der Einzelheiten und `Accepted` stehen beim Nutzer aus)

**Datum:** 2026-11-06

**Autor:** pt9912 (Entwurf in der Planner-Rolle)

**Bezug:** [TDS-RB-01](../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-QA-05](../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md), [ADR-0021](0021-oeffentlicher-integrations-einstieg.md), [ADR-0022](0022-e2e-laeufe-nicht-in-den-gates.md), [ADR-0024](0024-lokale-stempel-zwei-klassen.md), [ADR-0030](0030-dritter-stempel-fuer-die-selbsttests-der-stempel.md)

**Schärft:** [SPEC-052](../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge) (Testserver, Container-Abbild aus dem Klassenpfad des Testservers), [SPEC-059](../../../spec/spezifikation.md#einstieg-des-integrators-vertrag-zu-spec-059) (Nachweis ohne Zugriff auf `internal`-Namen). Betroffen ohne eigene Schärfung: [SPEC-042](../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge) (Lock und Prüfsummen), [SPEC-040](../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge) (Läufe gegen den Testserver; Heap-Angabe des Integrationslaufs wandert in die Build-Datei des Moduls).

**Supersedes (teilweise), jeweils nur wörtlich benannt; die Datei des Vorgängers bleibt unverändert und `Accepted` ([AGENTS.md](../../../AGENTS.md) §3.5):**

- [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md) Entscheidung 2, **nur** die Überschrift „**Ort des Testservers (Setzung): eigenes Source-Set `testserver` unter `src/testserver`, nicht mit `main` assoziiert, getrennt von `integrator`.**“ und ihre Unterpunkte „**Grenze.**“ (Assoziation, Erweiterung oder Duplikat des Sensors `integratorOhneAssoziation`), „**Tests des Testservers**“ (Task `testserverTest` im selben Source-Set) und „**Jackson**“ (`testserverImplementation`, Lock über die Tasks des Source-Sets in `DEPS_TASKS`, „verlässt `runtimeClasspath` und `lib/`“): Es gilt ein eigenes Gradle-Modul (Entscheidung 1 unten). Nicht ersetzt: „**Grenze des Nachweises (benannt)**“, „**Wahl gegen ‚`integrator` wird der Testserver‘**“ und der Satz, dass nur Kotlin-Quellen im Testserver liegen.
- [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md) Entscheidung 1, **nur** die Ortsangabe „(Source-Set `testserver`, Entscheidung 2)“ in „Wandert in den Testserver“, und Entscheidung 4, **nur** die Ortsangaben „Klassenpfad des Source-Sets `testserver`“ und „dessen `runtimeClasspath`“ in der Setzung des Testserver-Abbilds und der Gradle-Tasks: Es gilt der Klassenpfad des Moduls.
- [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md) Verglichene Alternativen, **nur** die Ablehnung der Zeile „C — zweites Gradle-Modul für den Testserver“ (Contra „neue Build-Einheit; Nutzen gegenüber dem Source-Set nicht belegt; [ADR-0021](0021-oeffentlicher-integrations-einstieg.md) b2 hat zwei Module bereits als zu teuer verworfen“) und im Re-Evaluierungs-Trigger die Klammer „(dann Option C)“; ebenso die dritte Zeile der Fitness Function („Source-Set `testserver` ohne Assoziation zu `main` …“).

Nicht ersetzt, unverändert in Kraft: Kontext 4 und die Wahl von Option B in der Sache — der Testserver bindet die Bibliothek nur über ihre öffentliche Fläche —, die Entscheidungen 1 (Zielbild des Schnitts), 3, 5, 6 und 7 von [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md) sowie die Fläche aus [ADR-0021](0021-oeffentlicher-integrations-einstieg.md) Entscheidung 3. [ADR-0021](0021-oeffentlicher-integrations-einstieg.md) b2 betraf die Teilung **der Bibliothek** in Kern und Prozess; diese ADR teilt die Bibliothek nicht, sie bleibt ein Modul (Wurzelprojekt, ein Jar), und der Testserver ist ihr Verbraucher.

---

## Kontext

**Nutzerentscheidung vom 2026-11-06 (wörtlich):** „aber wir wollen die lib testen, also muss sie in einem gradle-file auch eingebunden werden. bitte anpassen“.

**Lesart des Planners (nicht bestätigt, Einzelheiten offen):** Der Testserver wird ein eigenes Gradle-Modul mit eigener `build.gradle.kts`, das die Bibliothek als Abhängigkeit einbindet, so wie ein Embedder sie bindet. Ob „einbinden“ das Jar der Bibliothek oder ihre Klassenausgabe meint, sagt der Wortlaut nicht (Offen 2).

**Ist-Zustand** (gelesen am 2026-11-06 in `build.gradle.kts`, `settings.gradle.kts`, `Dockerfile`, `gradle.lockfile`, `config/detekt/detekt.yml` und `tools/stempel/*.eingaenge` auf `main` bei `f12f360`; nichts ausgeführt oder gemessen):

- `settings.gradle.kts` nennt nur `rootProject.name = "tdswire"`; es gibt ein Gradle-Projekt.
- Das Source-Set `testserver` übersetzt gegen `sourceSets.main.get().output`, also gegen das **Klassenverzeichnis** von `main`, nicht gegen das Artefakt `tdswire.jar`. Manifest, Lizenz in `META-INF`, `Class-Path` und `lib/` des Jars gehen damit in keinen Lauf des Testservers ein; sie prüft allein `tools/manifest-pruefung.sh` an der Datei (Stage `build-jar`). „So wie ein Embedder sie bindet“ gilt heute für die Sichtbarkeit der Namen (Sensor `testserverOhneAssoziation`), nicht für das Artefakt.
- Das Source-Set hat keine eigene Build-Datei und keinen eigenen Lock: `gradle.lockfile` führt die Konfigurationen `testserverCompileClasspath` und `testserverRuntimeClasspath` (mit Jackson) neben denen von `main`, `test` und `integrator`.
- Die Tasks `testserverTest`, `testserverKlassenpfad`, `integrationsServer` (Heap 48 MiB) und `kompatibilitaetsServer` stehen in der Wurzel-Build-Datei; die Task `test` der Wurzel liest den Klassenpfad des Source-Sets und reicht ihn als Systemeigenschaft `tdswire.testserverclasspath` an die Kind-JVM von `ServerProcess` (`src/test`).
- Quellen: `src/testserver/kotlin`, `src/testserver/test/kotlin`, `src/testserver/resources`. Das Dockerfile kopiert `src` ganz; die Stage `deps` kopiert genau die Dateien der Auflösung (Build-Dateien, Lockfiles, `gradle/libs.versions.toml`, `gradle/verification-metadata.xml`); `--offline` läuft in den Stages `laufzeit-client`, `integrationsserver`, `kompatibilitaetsserver`.
- `config/detekt/detekt.yml`: die Regel `ForbiddenMethodCall/ausgabe` (Ausgabe-Sperre im Hauptmodul) hat `includes: '**/src/main/kotlin/io/github/pt9912/tdswire/**'`; der Testserver schreibt die Bereitschaftszeile selbst auf die Standardausgabe ([ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md) Entscheidung 7).
- Die Stempel-Eingangslisten nennen `src` (Klasse `tests`) und den Pfad `src/testserver/kotlin/io/github/pt9912/tdswire/testserver/IntegrationServer.kt` (Klasse `werkzeug`, Stage `kopplung-zahlen`); `tools/stempel-eingaenge-pruefung.sh` hält die `COPY`-Quellen der Stages gegen die Listen ([ADR-0024](0024-lokale-stempel-zwei-klassen.md), [ADR-0030](0030-dritter-stempel-fuer-die-selbsttests-der-stempel.md)).

**Annahme, die kippt:** Dass ein Gradle-Projekt, das `project(":")` einbindet, `internal`-Namen der Bibliothek nicht übersetzen kann (Messpunkt M1). Kippt sie, ist die Modulgrenze kein Ersatz für den Sensor, und der Sensor bleibt (Entscheidung 3).

## Entscheidung

Alle Punkte außer 1 sind **Setzungen des Planners**, nicht gemessen und vom Nutzer nicht entschieden; Messpunkte stehen unten.

1. **Modul (Nutzerentscheidung, Lesart oben).** `settings.gradle.kts` nimmt `include(":testserver")` auf; der Testserver hat eine eigene `build.gradle.kts` im Modulverzeichnis. Das Source-Set `testserver` der Wurzel entfällt. Das Wurzelprojekt bleibt die Bibliothek (ein Modul, ein Jar `tdswire.jar`, `src/main`); `integrator` und `test` bleiben ihre Source-Sets.
2. **Bindung.** Das Modul hängt per `implementation(project(":"))` an der Bibliothek und nicht an `sourceSets.main.output`. Gradle liefert dem Verbraucher damit die Ausgabe des Projekts als Abhängigkeit mit Metadaten; welche Form der Compile- und Laufzeit-Klassenpfad bekommt (Jar oder Klassenverzeichnis), ist nicht gemessen (M2). Soll der Laufzeit-Klassenpfad des Testservers das Jar `tdswire.jar` tragen, prüft der Slice, ob die Standardwahl von Gradle das leistet, und wählt sonst eine Form, die es leistet (Offen 2).
3. **Die Modulgrenze ersetzt den Sensor `testserverOhneAssoziation`, wenn M1 sie belegt.** Kotlin gibt `internal` nur Quelltext desselben Moduls oder assoziierter Übersetzungen frei; das Modul hat weder Assoziation noch `friend`-Pfad zur Bibliothek. Erst wenn die Mutation M1 (ein Zugriff auf einen `internal`-Namen im Modul bricht die Übersetzung) rot ist, entfällt die Task `testserverOhneAssoziation`; vorher bleibt sie. Neu zu sperren ist der Weg, die Grenze zu öffnen: ein Eintrag zu `friend`-Pfaden oder `associateWith` in `testserver/build.gradle.kts` (Textprüfung als Skript; Form legt der Slice fest, die Absicht steht in der Fitness Function). Der Sensor `integratorOhneAssoziation` bleibt unverändert. Grenze wie bisher: `internal` ist im Bytecode öffentlich, der Nachweis gilt für Kotlin-Quellen; im Modul liegen nur Kotlin-Quellen (`BEO-TDS/java-quelle-umgeht-kotlin-regel`).
4. **Ort und Layout.** Das Modul liegt unter `testserver/` (Wurzel des Repos), die Quellverzeichnisse behalten ihre Form (`testserver/kotlin`, `testserver/test/kotlin`, `testserver/resources`, per `sourceSets` in der Modul-Build-Datei); der Umzug ist ein reiner `git mv src/testserver testserver` ([AGENTS.md](../../../AGENTS.md) §3.3). Package `io.github.pt9912.tdswire.testserver` und Klassennamen (`MainKt`, `IntegrationServerKt`, `KompatibilitaetsServerKt`) bleiben. **Grund gegen das Standardlayout `testserver/src/main/kotlin/…`:** der Pfad träfe das `includes`-Muster der Ausgabe-Sperre (Ist-Zustand), und der Testserver schreibt die Bereitschaftszeile; das Muster zu ändern wäre eine Änderung einer Gate-Regel ([AGENTS.md](../../../AGENTS.md) §3.6) ohne Not. Der Pfad `**/test/**` für die Tests des Testservers (detekt-Standardausschlüsse für Tests) bleibt wahr.
5. **Lock und Prüfsummen.** Das Modul führt `dependencyLocking { lockAllConfigurations() }` und eine eigene `testserver/gradle.lockfile`; Jackson steht nur dort, die Wurzel-`gradle.lockfile` führt kein Jackson mehr (zu belegen, M3). `gradle/verification-metadata.xml` bleibt eine Datei im Wurzelverzeichnis (ein Build, eine Prüfsummen-Datei; Verhalten bei zwei Projekten nicht gemessen, M3). `make deps-lock`: die Stage `lock` kopiert die Modul-Dateien, `lock-export` gibt die drei Lock-Dateien (`gradle.lockfile`, `settings-gradle.lockfile`, `testserver/gradle.lockfile`) und `gradle/verification-metadata.xml` heraus; `DEPS_TASKS` in `tools/mk/build.mk` verliert die Namen `testserverTest` und `detektTestserver` (die Aufgaben des Moduls heißen `test`, `detekt`, `detektMain`, `detektTest` und laufen bei unqualifiziertem Aufruf in jedem Projekt, das sie hat; nicht gemessen). Die Task `abhaengigkeitenLaden` liest heute nur die Wurzel-Sperre; sie muss die Sperre des Moduls mitlesen, sonst lädt die Stage `deps` dessen Konfigurationen nicht, und die `--offline`-Stages scheitern an Jackson (gelesen, nicht ausgeführt; Beobachtung `BEO-TDS/katalog-und-lock-ohne-gegenpruefung`).
6. **detekt.** Das detekt-Plugin gilt im Modul mit derselben `config/detekt/detekt.yml` (eine Datei, keine Regel gelockert, keine Baseline); die Stage `detekt` führt die Tasks des Moduls mit. Die Ausgabe-Sperre trifft das Modul nach Entscheidung 4 nicht, die package-gebundenen Kernregeln (`includes` über `domain`, `port`, `config`, …) treffen es nicht, weil das Package `testserver` keiner dieser Schichten gehört (gelesen, M5 prüft). `tools/jackson-sperre.sh src/main` prüft weiter die Wurzel-Quellen und wird nicht gelockert.
7. **Dockerfile.** Die Stage `deps` kopiert zusätzlich `testserver/build.gradle.kts` und `testserver/gradle.lockfile`; die Stages, die Gradle über beide Projekte laufen lassen (`compile`, `test`, `smoke`, `detekt`, `spec-abdeckung`, `lock`, `laufzeit-client`, `testserver`, `integrationsserver`, `kompatibilitaetsserver`), kopieren `testserver` zu `src`. Die Stage `build-jar` baut allein die Bibliothek (`gradle :assemble` statt `gradle assemble`, damit `build/libs` das Jar der Bibliothek und kein Modul-Artefakt enthält); die Prüfung „das Jar führt keine Klasse des Testservers“ in `tools/manifest-pruefung.sh` bleibt. Der Pfad der Beschreibungsdatei im Abbild (`/src/src/testserver/resources/…` in Dockerfile und `tools/testserver.sh`) ändert sich mit dem Umzug.
8. **Die Wurzel-`test` bekommt den Klassenpfad des Moduls über eine auflösbare Konfiguration** (`project(":testserver")`, Laufzeit) und reicht ihn weiter als `tdswire.testserverclasspath`. `ServerProcess`, `ServerSmokeTest` und `LaufzeitClient` bleiben in `src/test` (sie bauen ihre Bytes mit `internal`-Hilfen der Bibliothek; der Zuschnitt des Herauslösens ist in der Planung dazu begründet). Daraus folgt eine Kante in beide Richtungen zwischen den Projekten (Modul → Bibliothek zur Übersetzung, Wurzel-Test → Modul zur Laufzeit); ein Zyklus in Tasks oder Konfigurationen wird nicht erwartet, ist aber nicht gemessen (M4).
9. **`integrator` bleibt Source-Set der Wurzel** (Kante geprüft, gelesen): (a) `integratorRuntimeClasspath` führt in `gradle.lockfile` heute kein Jackson, und die Wurzel-Sperre führt nach dem Umzug auch sonst keines; die Aussage „der Einbettungstest läuft ohne Jackson“ stützt damit zusätzlich der Lock (M3) und nicht nur die Konfiguration des Source-Sets. (b) Der Einbettungstest bindet weiter das Klassenverzeichnis `main.output`, der Testserver die Projekt-Abhängigkeit: zwei Bindungsformen der Bibliothek im Repo; ob `integrator` ebenfalls ein Modul wird, ist nicht entschieden (Offen 1). (c) `integratorTest` und der Name im Gate bleiben.
10. **Stempel-Eingänge.** `tools/stempel/tests.eingaenge` nimmt `testserver` auf (Verzeichnis, rekursiv: Quellen, Build-Datei, Sperre); `tools/stempel/werkzeug.eingaenge` ersetzt den Pfad `src/testserver/kotlin/…/IntegrationServer.kt` durch `testserver/kotlin/…/IntegrationServer.kt` und nimmt `testserver/build.gradle.kts` und `testserver/gradle.lockfile` auf (die Stage `kopplung-zahlen` liest die Heap-Zahl, die mit der Task `integrationsServer` in die Modul-Build-Datei wandert; die Stages `detekt-selbsttest` und `lock` erben von `deps`). `tools/stempel/stempel.eingaenge` bleibt (Dockerfile, `tools/mk` und die Prüfskripte stehen schon darin). Die Listen wachsen, sie schrumpfen nicht: keine Senkung nach [AGENTS.md](../../../AGENTS.md) §3.6; die Änderung ändert den Hash der Klassen, die Stempel gelten danach neu (Folge von [ADR-0024](0024-lokale-stempel-zwei-klassen.md), erwartet, nicht gemessen). Der Gegenprüfer `make stempel-eingaenge-pruefung` bleibt der Sensor dafür, dass kein `COPY` der Stages eine Quelle außerhalb der Listen hat (M6). Nachzuziehen sind außerdem die festen Pfade in `tools/integrationszahlen-pruefung.sh`, `tools/integrationszahlen-selbsttest.sh`, `tools/stempel-eingaenge-selbsttest.sh` (Beispielpfad), `tools/spec-abdeckung.sh` (Voreinstellungen `TEST_QUELLE` und `BERICHTE`), `tools/simulationstest.sh` (Kommentar) und `tools/testserver-selbsttest.sh`.

## Verglichene Alternativen

| Option | Pro | Contra |
|---|---|---|
| A — nichts tun: Source-Set `testserver` bleibt ([ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md) Entscheidung 2) | keine Kosten; der Sensor gilt, die Sichtbarkeit der Namen ist belegt | der Testserver bindet das Klassenverzeichnis und nie das Artefakt; keine eigene Build-Datei und kein eigener Lock; widerspricht der Nutzerentscheidung vom 2026-11-06 |
| **B — Gradle-Modul `:testserver` mit `project(":")` (Vorschlag)** | eigene Build-Datei und eigener Lock; Jackson nur dort, auch im Lock; Verbraucher-Sicht der Bibliothek über die Projekt-Abhängigkeit; Modulgrenze trägt „nur öffentliche Fläche“ (nach M1) | neue Build-Einheit: Dockerfile-Stages, Lock-Export, Stempel-Listen, feste Pfade in Skripten ziehen mit; Rückkante Wurzel-`test` → Modul (Entscheidung 8); ob Jar oder Klassenverzeichnis ankommt, ist offen |
| B2 — Modul mit Datei-Abhängigkeit auf das Jar (`files(tasks.jar)`) | das Jar ist sicher der Gegenstand | Gradle-Metadaten fehlen: die Laufzeit-Abhängigkeiten der Bibliothek (`kotlinx-coroutines`) muss das Modul von Hand deklarieren, der Lock sieht die Kante nicht; Abweichung vom Muster eines Embedders mit Koordinate; nicht gemessen |
| C — Wurzelprojekt ohne Code, Bibliothek, Testserver und Einbettungstest als drei Module | symmetrisch; auch `integrator` bindet das Artefakt | verschiebt `src/main` und `src/test`: Pfade in `detekt.yml`, Dockerfile, Stempel-Listen, Spezifikation und Skripten; nicht gefordert; der Aufwand steht in keinem Verhältnis zum Auftrag |
| D — Source-Set bleibt, bindet aber das Jar statt `main.output` | kleinster Eingriff; das Artefakt wird Gegenstand | keine eigene Build-Datei, kein eigener Lock; wie A gegen den Wortlaut des Nutzers; der Sensor bleibt Pflicht |
| E — Bibliothek ins lokale Maven-Repository veröffentlichen, Testserver als Koordinate | die Form eines fremden Embedders, auch die Metadaten | ein Veröffentlichungsschritt vor jedem Testserver-Bau; Veröffentlichung ist ein Trigger von [ADR-0021](0021-oeffentlicher-integrations-einstieg.md) und [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md); Versions-Drift zwischen Quelle und Repository |

## Konsequenzen

- Positiv (Absicht, nach den Messpunkten zu belegen): der Testserver hängt an der Bibliothek wie ein Verbraucher; die Aussage „Bibliothek ohne Jackson“ steht auch im Lock; die Modulgrenze ersetzt einen Sensor, der eine Konfiguration prüft, durch die Übersetzung selbst.
- Negativ: ein Eingriff in Build, Dockerfile (zehn Stages), Lock-Export, drei Stempel-Listen und mindestens sechs Skripte; die Änderung der Listen und des Dockerfiles ändert die Hashes aller drei Stempel, der erste `make gates` danach läuft voll; zwei Projekte tragen eine Task `test`.
- **Wirkung auf die Laufzeiten (nicht gemessen):** `make build`, `make test`, `make detekt`, `make smoke` und die E2E-Stufen laufen über zwei Projekte; ob sich die warmen Zeiten (Werte in `harness/README.md`, Einzelmessungen) merklich ändern, misst M7.
- **Nachzug (nach `Accepted`, nicht Teil dieser ADR):** `harness/README.md` (Zeilen `detekt`, `test`, `build`, `smoke`, `kopplung-zahlen`, `testserver-up`), `SPEC-052` und `SPEC-040` (Pfad und Heap-Angabe), die Pfade in `docs/user/testserver-manuell.md`, ADR-0033 <!-- d-check:ignore (liegt auf einem anderen Zweig, die Nummer ist beim Merge zu prüfen) --> (Zeile der Fitness Function „Source-Set `testserver` ohne Assoziation“, solange diese ADR `Proposed` ist), und der Index dieser ADR-Datei.
- **Laufende Arbeiten:** Arbeiten, die Dateien unter `src/testserver` ändern (Katalog, Massenladen, MERGE, Log-Adapter des Testservers), werden entweder vor dem Umzug beendet oder lesen danach den neuen Pfad; die Reihenfolge ordnet die Planung.

## Messpunkte

Alle sind Aufgaben des Slice und vor `Accepted` der Einzelheiten nicht ausgeführt.

| # | Frage | Wie | Erwartung, die kippen kann |
|---|---|---|---|
| M1 | Bricht ein Zugriff auf `internal` aus dem Modul die Übersetzung? | im Wegwerf-Stand eine `internal`-Funktion der Bibliothek im Modul aufrufen, `docker build --target compile` | Übersetzungsfehler; ohne ihn bleibt der Sensor `testserverOhneAssoziation` (Entscheidung 3) |
| M2 | Welche Form (Jar oder Klassenverzeichnis) bekommen Compile- und Laufzeit-Klassenpfad? | `testserverKlassenpfad` und `gradle :testserver:dependencies` am Modul lesen | steht `tdswire.jar` im Laufzeit-Pfad; sonst Offen 2 |
| M3 | Lock und Prüfsummen mit zwei Projekten | `make deps-lock` ohne Versionsänderung; `git diff --stat` der drei Lock-Dateien und der Prüfsummen-Datei | Jackson nur in `testserver/gradle.lockfile`; Prüfsummen unverändert bis auf Umzug; ein Fund ist die Antwort |
| M4 | Zyklus zwischen Wurzel-`test` und Modul | `make test` und `make smoke` | beide laufen; sonst Fallback als eigene Entscheidung (die Smoke-Hilfen verlassen `src/test`) |
| M5 | Trifft eine Regel von detekt das Modul (Ausgabe-Sperre, Kernregeln) | `make detekt` am Modul, dazu `make detekt-selbsttest` | grün ohne Änderung von `detekt.yml` |
| M6 | Erkennt `make stempel-eingaenge-pruefung` den fehlenden Eintrag | Dockerfile mit `COPY testserver` vor dem Nachzug der Listen lesen: rot; danach grün; `make stempel-eingaenge` und `make stempel-abbild` grün | rot-vor-grün-nach ist der Beleg, dass der Sensor das Modul sieht |
| M7 | Laufzeit der Gates vorher und nachher | `make build`, `make test`, `make detekt`, `make smoke`, `make laufzeittest` warm, mit Last und Host im Protokoll | Zahl in `docs/messungen/`; keine Schwelle gesetzt |

## Fitness Function (falls maschinell prüfbar)

Zeilen sind Absicht, nicht Bestand.

| Tooling | Regel | Make-Target |
|---|---|---|
| Übersetzung des Moduls `:testserver` (ohne Assoziation, ohne `friend`-Pfad) | ein Zugriff auf `internal` aus dem Testserver bricht die Übersetzung; Java-Quellen und Reflection bleiben unbelegt | `make test` (und damit `make gates`) |
| Textprüfung von `testserver/build.gradle.kts` (Form offen, Absicht) | kein `friend`-Pfad, keine Assoziation zur Bibliothek | `make test` oder `make detekt`; die Klasse legt der Slice fest |
| `testserver/gradle.lockfile` und Wurzel-`gradle.lockfile` | Jackson steht nur im Lock des Moduls | `make deps-lock` (Werkzeug, kein Gate); als Gate nicht entschieden |
| `tools/stempel-eingaenge-pruefung.sh` | jedes `COPY` der Stages steht in den Listen, auch `testserver` | `make stempel-eingaenge-pruefung` (Klasse `immer`) |
| `tools/manifest-pruefung.sh` | das Jar der Bibliothek führt keine Klasse des Testservers | `make build` |

## Offen

1. **`integrator` als Modul (Entscheidung des Nutzers):** gleiche Bindungsform für beide Verbraucher (`project(":")`) gegen den kleineren Eingriff (Einbettungstest bleibt Source-Set mit `main.output`). Empfehlung des Planners: Source-Set behalten, weil der Auftrag den Testserver nennt; nicht entschieden.
2. **Jar oder Klassenverzeichnis:** „in einem gradle-file einbinden“ ist erfüllt durch `project(":")`; ob das Jar (mit Manifest und Lizenz) Gegenstand des Laufs sein muss, sagt der Wortlaut nicht. M2 liefert die Tatsache; die Entscheidung bleibt beim Nutzer.
3. **Ort `testserver/` gegen `src/testserver` als Projektverzeichnis:** Setzung ist `testserver/` mit den bisherigen Unterverzeichnissen (Entscheidung 4); ein Modul unter `src/testserver` mit eigenem Standardlayout träfe dieselbe Ausgabe-Sperre. Nicht entschieden.
4. **Nummer dieser ADR:** `0033` ist auf einem anderen Zweig vergeben (`Proposed`); die Nummer ist beim Merge gegen den Index von `main` zu prüfen.
5. **Zeilen von Lock und Prüfsummen, die nur das Modul braucht:** ob `gradle/verification-metadata.xml` Einträge verliert, die kein Projekt mehr braucht, zeigt M3; Entfernen ist keine Lockerung, wenn der Lock die Artefakte nicht mehr führt.

## Re-Evaluierungs-Trigger

M1 ist rot (die Modulgrenze sperrt `internal` nicht); M4 zeigt einen Zyklus; die Zeit der Gates wächst um ein Maß, das der Nutzer nicht trägt (M7); der Wunsch, die Bibliothek zu veröffentlichen (dann Option E und [ADR-0021](0021-oeffentlicher-integrations-einstieg.md) b2 neu); ein Befund, dass Embedder-Pfad und Testserver-Pfad auseinanderlaufen.

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-11-06 | Nutzerentscheidung (wörtlich im Kontext): Testserver in ein Gradle-File mit Einbindung der Bibliothek | Sitzung |
| 2026-11-06 | Proposed (Entwurf des Planners) | — |

Nach `Accepted` wird diese Datei **nicht mehr inhaltlich überschrieben**.
Spätere Korrekturen oder Schärfungen entstehen als neue ADR mit
`Supersedes ADR-NNNN` (Baseline-Regelwerk `modul-04-adrs.md`
§Hard Rule für Accepted-ADRs).
