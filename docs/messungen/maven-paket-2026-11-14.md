# Maven-Paket der Bibliothek: Messprotokoll vom 2026-11-14

Messprotokoll zu [TDS-RB-01](../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-RB-05](../../spec/lastenheft.md#tds-rb-05--lizenz) und [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) (`AGENTS.md` §5 Regel 7), Grundlage der Aussagen zur veröffentlichten Form in `SPEC-052` und `SPEC-059` und der Messungen, die [ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md) für den Folge-Slice nennt (Entscheidungen 2, 6, Zielbild-Prüfung zu `TDS-RB-01`). Es hält fest, was an der vorbereiteten Veröffentlichung **gemessen** ist, und ausdrücklich, was nicht. **Nichts wurde nach GitHub geschrieben, kein Tag im Repo gesetzt, kein echtes Token gelesen oder verwendet.** Jeder Lauf nennt sein Ziel.

**Vorbehalt, der für jede Zeile gilt.** Einzelläufe auf einem Rechner unter fremder Last, die Zeiten sind keine Leistungszusage. Die Zahlen stammen aus Erzeugern, die im Repo liegen: `tools/publish-probe.sh` (Argumente `konsument` und `token`), die Dockerfile-Stage `publish`, `tools/publish.sh` und die Mutationen in `tools/publish-selbsttest.sh` und `tools/manifest-selbsttest.sh`; die Läufe sind damit wiederholbar. Die Ausgaben stehen hier gekürzt; die Ausgabe eines Laufs ist nicht abgelegt.

## Bedingungen

| Angabe | Wert |
|---|---|
| Datum | 2026-11-14 |
| Host | Linux 6.8, 20 Kerne, 31 GiB; Load Average (1 Minute) zwischen 5 und 9, gelesen mit `uptime` zwischen den Läufen; die Ursache der Last ist nicht geklärt |
| Werkzeug | Docker 29.9.3; Stages des Repos; Basis `gradle:8.14-jdk21` (Digest im `Dockerfile`), Gradle 8.14.6 |
| Stand | Commit `edc7844c` des Zweigs; danach änderte sich nur die Ausgabezeile des Scopes in `tools/publish-probe.sh`; `version` in `build.gradle.kts` ist `0.1.0-SNAPSHOT` |
| Ziele | `publishToMavenLocal` im Verzeichnis `~/.m2` eines Wegwerf-Containers (M2, M3); ein Dummy-Server (JDK-HTTP-Server, Quelle im Skript) in einem Docker-Netz mit der Option `--internal`, also ohne Zugang nach außen (M4). **Kein Lauf ging gegen GitHub.** |

## M1 — Scope von `kotlinx-coroutines-core`: `api` oder `implementation`?

Aufbau: Das Skript baut das Image der Stage `publish`, setzt in einer Kopie den Scope in `build.gradle.kts`, führt `publishToMavenLocal` aus und lässt einen Probe-Konsumenten (Gradle-Projekt mit Kotlin 2.4.10 und der Bibliothek als einziger Abhängigkeit, ohne eigene Angabe zu coroutines; der Quelltext `Probe.kt` steht im Skript) den Server über `TdsServer.start` mit einem eigenen `QueryBackend` starten. Das Backend nennt `Flow` und `emptyFlow` aus `kotlinx.coroutines`. Der Konsument liest das lokale Repository einmal mit den Gradle-Metadaten und einmal nur über die POM.

| Nr. | Scope | POM-Eintrag | Konsument mit Gradle-Metadaten | Konsument nur über die POM |
|---|---|---|---|---|
| M1.1 | `api` | `kotlinx-coroutines-core-jvm`, `<scope>compile</scope>` | übersetzt, läuft (`PROBE-OK`, Exit 0) | übersetzt, läuft (`PROBE-OK`, Exit 0) |
| M1.2 | `implementation` | `kotlinx-coroutines-core-jvm`, `<scope>runtime</scope>` | **rot**: `Unresolved reference 'kotlinx'` und `'Flow'`, Exit 1 | **rot**: dieselben Fehler, Exit 1 |

Entscheid dieses Slice: **`api`**, weil `Flow` und `suspend` in der öffentlichen Fläche stehen und ein Konsument ohne diese Angabe nicht übersetzt (M1.2 ist der rote Gegenfall zu M1.1). Gemessen ist das für einen Gradle-Konsumenten mit Kotlin; ein Maven- oder Java-Konsument ist nicht gemessen ([ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md), akzeptiertes Negativ c).

## M2 — der `Class-Path` mit Einträgen `lib/<Name>` im veröffentlichten Jar (Option 2a)

Das veröffentlichte Jar ist dasselbe `tdswire.jar`; sein Manifest führt `Class-Path: lib/kotlinx-coroutines-core-jvm-1.10.1.jar lib/kotlin-stdlib-2.4.10.jar lib/annotations-23.0.0.jar`. Im lokalen Repository liegen neben dem Jar nur `maven-metadata-local.xml`, `.module` und `.pom`, kein `lib/`.

| Nr. | Lauf | Ergebnis |
|---|---|---|
| M2.1 | Konsument wie M1.1 (Scope `api`), ohne `lib/` neben dem Jar | läuft, Exit 0: die fehlenden Einträge stören den Start nicht |
| M2.2 | Gegenfall: Bibliothek ohne transitive Abhängigkeiten, coroutines nur zur Übersetzung, ohne `lib/` | **rot** zur Laufzeit: `NoClassDefFoundError: kotlinx/coroutines/Dispatchers`, Exit 1: der `Class-Path` ersetzt die Abhängigkeiten der POM nicht |
| M2.3 | wie M2.2, aber `lib/` mit den drei Jars neben dem Jar im lokalen Repository | läuft, Exit 0: der `Class-Path` greift, wenn `lib/` liegt (der Handweg bleibt wirksam) |

M2.2 und M2.3 zeigen, dass der Lauf M2.1 den `Class-Path` unterscheiden kann: Er wäre rot, wenn die Abhängigkeiten nicht aus der POM kämen. Aussage, die M2 trägt: für einen Gradle-Konsumenten der eigenen Bauart stören die nicht auflösbaren Einträge nicht. Nicht gemessen: Java- und Maven-Konsumenten, ein Konsument, der das Jar in ein Fat-Jar verpackt, und das Verhalten gegen die Registry. Der Trigger zu den Optionen 2b und 2c in [ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md) ist nicht eingetreten.

## M3 — Probe-Konsument nur über `publishToMavenLocal` und die POM

M1.1 ist die Messung: der Konsument bindet `io.github.pt9912:tdswire:0.1.0-SNAPSHOT` aus dem lokalen Repository, einmal mit der Auswahl `gradleMetadata()` und einmal ohne sie (nur POM), übersetzt gegen die öffentliche Fläche und startet und beendet den Server (`StartOutcome.Running`, `stop`). Das belegt den **lokalen** Weg; der Konsum aus der Registry ist die Lücke zu `TDS-RB-01`, die [ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md) benennt, und bleibt ungemessen.

## M4 — Platzhalter-Token: Image, Log und Gradle-Lauf

Aufbau: `publish-probe.sh token` erzeugt einen Platzhalter `PLATZHALTER-<24 Hexzeichen>`, ein Wegwerf-Repo aus dem Arbeitsbaum mit der Version `0.0.1` und dem Tag `v0.0.1` (der Tag liegt nur in diesem Wegwerf-Repo), startet den Dummy-Server im internen Docker-Netz (er prüft den Benutzer und den Platzhalter per Basic-Auth) und ruft `tools/publish.sh --url http://registry:8080/maven` mit `PUBLISH_DOCKER_NETZ` auf. `gradle publish` läuft ohne `--info` und ohne `--debug`.

| Nr. | Lauf | Ergebnis |
|---|---|---|
| M4.1 | `GITHUB_TOKEN` leer | Exit 1, Meldung `GITHUB_TOKEN ist leer oder nicht gesetzt`, **0 Anfragen** am Dummy-Server, kein `docker build` gestartet |
| M4.2 | Platzhalter gesetzt | Exit 0, `BUILD SUCCESSFUL`; der Dummy-Server nahm 21 Anfragen mit gültiger Anmeldung entgegen (`auth=ok`): `GET` der `maven-metadata.xml` (404), `PUT` von `tdswire-0.0.1.jar`, `.module`, `.pom` und `maven-metadata.xml`, je mit `.md5`, `.sha1`, `.sha256`, `.sha512`, alle mit 201 |
| M4.3 | Suche nach dem Platzhalter in Log von `publish.sh` (enthält die Ausgabe von `docker build` und `gradle publish`), `docker history --no-trunc` und `docker image inspect` des Images `tdswire-publish:lokal` und im Log von M4.1 | **0 Treffer** in jeder der vier Dateien, je für den Klartext, für Base64 des Tokens und für Base64 von `Benutzer:Token` |
| M4.4 | Positivprobe derselben Suche (`grep -F`) am `docker inspect` des Dummy-Containers, in dessen Umgebung der Platzhalter steht | 1 Treffer: die Suche findet ihn, wo er steht |

Aussage, die M4 trägt: In diesem Aufbau steht der Platzhalter in keiner der vier durchsuchten Quellen, und ein leeres Token bricht vor dem Netz. M4.2 zeigt zugleich, dass der Platzhalter beim Server ankommt (`auth=ok`), nur die Umgebung des Containers trägt ihn. **Grenzen:** Gesucht sind drei Formen; andere abgeleitete Formen (URL-Kodierung, gekürzte Teile) findet die Suche nicht (`BEO-TDS/redaktion-sucht-nur-den-bekannten-wert`). Ein Lauf mit `--info` oder `--debug` ist nicht gemessen und im Werkzeug nicht vorgesehen. M4.4 zeigt, dass der Platzhalter in der Umgebung eines laufenden Containers im `docker inspect` steht (am Dummy-Container; von [ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md) akzeptiert); `/proc` ist nicht gelesen. Der Weg zu GitHub ist mit einem Dummy-Server nicht belegt: Anmeldeverfahren, TLS, die Antworten und die Regeln der Registry (zum Beispiel das Ablehnen einer vorhandenen Version) sind ungemessen. Ein anderes Ziel als GitHub Packages nimmt `tools/publish.sh` nur mit einem Token, das mit `PLATZHALTER-` beginnt (M6: Fälle `fremdes-ziel`, `fremdes-ziel-https`, `platzhalter-leer`); die Probe erzeugt einen solchen Platzhalter. Gesehen ist das am Selbsttest mit Docker-Stub, nicht an einem Lauf gegen ein fremdes Ziel. Gradle läuft im Veröffentlichungslauf **nicht** mit `--offline`: im Offline-Modus bricht der Upload mit `Cannot upload … in offline mode` (ein erster Lauf mit `--offline`, Exit 1).

## M5 — Sperre und Prüfsummen

| Nr. | Lauf | Ergebnis |
|---|---|---|
| M5.1 | `maven-publish` im Wurzelprojekt, `make build` (Stage `build-jar` mit `generatePomFileForMavenPublication`) bei aktiver Sperre und Verifikation | grün ohne Änderung von `gradle.lockfile`, `settings-gradle.lockfile`, `testserver/gradle.lockfile` und `gradle/verification-metadata.xml` |
| M5.2 | `make deps-lock` (Netz) mit `generatePomFileForMavenPublication` und `publishToMavenLocal` in `DEPS_TASKS`, Scope `api` | grün; `git status` danach ohne Änderung an den vier Dateien: kein neuer Eintrag in Sperre und Prüfsummen |
| M5.3 | `make publish-vorschau` (Stage `publish`, `publishToMavenLocal` mit `--offline`) | grün; Jar, `.module`, `.pom` und `maven-metadata-local.xml` im lokalen Verzeichnis des Containers |

Die Aussage zu M5 gilt für diese Gradle-Distribution (`maven-publish` ist ein Plugin der Distribution) und die Tasks der Stages; ein weiterer Task, den ein Lauf aufruft, ist nicht gemessen. Die Zeit von `make deps-lock` ist nicht mitgeschrieben (mehrere Minuten, Netz).

## M6 — rote Gegenfälle der Prüfer (Mutationen)

Jede Mutation wurde an `tools/publish.sh` oder `tools/manifest-pruefung.sh` eingebaut und `make skript-selbsttest` gefahren; jede machte ihn rot, danach wurde sie zurückgenommen.

| Mutation | Rot gesehen in |
|---|---|
| Prüfung auf `-SNAPSHOT` entfernt (die Prüfung der Form fängt die Version weiter ab, die Meldung ändert sich) | `snapshot` (Meldung `endet auf -SNAPSHOT` fehlt) |
| Prüfung der Form `<zahl>.<zahl>.<zahl>` entfernt | `form-zusatz` (und `form-zwei`) |
| Tag-liegt-auf-`HEAD`-Prüfung entfernt | `tag-alt` |
| `git status` ohne unversionierte Dateien | `baum-unversioniert` |
| Prüfung des sauberen Baums entfernt | `baum-geaendert` |
| Prüfung von `GITHUB_TOKEN` entfernt | `token-fehlt` |
| Prüfung von `GITHUB_ACTOR` entfernt | `actor-fehlt` |
| Token als Wert im `docker run` (`-e GITHUB_TOKEN="$GITHUB_TOKEN"`) | `gut` (Form des Aufrufs und Wert in Ausgabe) |
| POM-Prüfung auf Jackson entfernt | `pom-jackson` |
| POM-Prüfung auf Testserver entfernt | `pom-testserver` |
| Lizenzname in der POM beliebig | `pom-mit-nur-im-kommentar` |
| URL der Lizenz beliebig | `pom-lizenz-url-ohne-schema` |
| Meldung bei fehlender POM entfernt | `pom-fehlt` |
| Bindung des Ziels an das Platzhalter-Token entfernt (Review F-1) | `fremdes-ziel` |
| Muster `PLATZHALTER-?*` beliebig (Review F-1) | `fremdes-ziel` |
| Suffix `^{commit}` der Tag-Auflösung entfernt, Fall mit annotiertem Tag (Review F-2) | `annotiert` |
| Standardziel in `publish.sh` weicht von `build.gradle.kts` ab (Review F-3) | `Standardziel` |
| Lizenz-URL in `build.gradle.kts` weicht von der guten POM des Selbsttests ab (Review F-3) | Kopplungsprüfung in `manifest-selbsttest` |
| Zeile `version = ` in `build.gradle.kts` in anderer Form (Review F-3) | Kopplungsprüfung in `publish-selbsttest` |

Zwei Mutationen waren beim ersten Lauf **grün** und wurden durch einen schärferen Fall rot: die Entfernung der Prüfung auf `-SNAPSHOT` (die Prüfung der Form fängt dieselbe Version ab; der Fall prüft jetzt die Meldung) und eine beliebige URL der Lizenz (der Fall mit der URL ohne Schema fehlte). Nicht per Mutation belegt: die Reihenfolge „alle Prüfungen vor Docker“ als Eigenschaft des Skripts (der Selbsttest prüft pro roten Fall, dass der Docker-Stub nicht gerufen wurde); die Abwesenheit von `publish` in Gate-Listen und in `tools/mk/e2e.mk` prüft der Selbsttest mit zwei Gegenproben an einer Kopie (eine Zeile `GATE_CHECKS += publish-vorschau`, eine Zeile in `e2e.mk`), nicht an den echten Dateien.

## Vorgehen

Beim Aufbau der Selbsttests lief `sh tools/manifest-selbsttest.sh` einmal auf dem Host mit dem dort vorhandenen `jar`, entgegen der Regel Docker-only (`AGENTS.md` §3.1): ein Fehler im Vorgehen, kein Fehler des Artefakts; das Skript läuft vorgesehen nur in der Stage `skript-selbsttest`, alle Belege dieses Protokolls stammen aus Docker.

## Nicht gemessen

- Veröffentlichen und Konsum gegen GitHub Packages (M3 und M4 gelten für das lokale Repository und einen Dummy-Server).
- Ein Konsument aus Java oder Maven, ein Fat-Jar und eine Version, die mit `Class-Path` und Registry-Auflösung anders umgeht.
- Das Verhalten der Registry bei einer vorhandenen Version und die Rechte des Tokens (`write:packages` als kleinster Scope ist Setzung der ADR).
- Die `.module`-Datei (Gradle-Metadaten) neben der POM: die Prüfung im Bau liest nur die POM; die Datei enthält dieselben Abhängigkeiten (M1.1 liest sie), eine Prüfung auf Jackson gibt es für sie nicht.
- Byte-Gleichheit zweier Bauten des Jars (keine Zusage).
