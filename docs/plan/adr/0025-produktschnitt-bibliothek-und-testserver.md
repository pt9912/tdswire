# ADR-0025: Produktschnitt: Bibliothek als Produkt, Testserver nur zum Testen

**Status:** Proposed

**Datum:** 2026-11-01

**Autor:** pt9912

**Bezug:** [TDS-RB-01](../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [ADR-0012](0012-simulations-backend-beschreibung-und-formen.md), [ADR-0018](0018-laufzeit-artefakt.md), [ADR-0019](0019-kompatibilitaetstests-zuschnitt.md), [ADR-0020](0020-logformat-und-observability-schnittstelle.md), [ADR-0021](0021-oeffentlicher-integrations-einstieg.md), [ADR-0022](0022-e2e-laeufe-nicht-in-den-gates.md)

**Schärft:** [SPEC-052](../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge) (Artefakt), [SPEC-035](../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) (Ort der Tabellenbeschreibung als Datei), [SPEC-040](../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge) (Läufe gegen den Testserver). Betroffen ohne eigene Schärfung, Nachzug siehe Konsequenzen: `SPEC-005`, `SPEC-010`, `SPEC-023`.

**Supersedes (teilweise), jeweils nur wörtlich benannt; die Dateien der Vorgänger bleiben unverändert und `Accepted` ([AGENTS.md](../../../AGENTS.md) §3.5):**

- [ADR-0021](0021-oeffentlicher-integrations-einstieg.md) Entscheidung 1, **nur** (a) die Aussagen zu `ServerConfig.simulationFile` (Bytes der Tabellenbeschreibung in der Konfiguration, Obergrenze von 1 MiB, „Die Tabellen aus `ServerConfig.simulationFile` kommen bei `Simulation` und `None` dazu …“, der Konflikt `Custom` zusammen mit gesetzter `simulationFile`, „und die Tabellenbeschreibung als Bytes in `ServerConfig`“) und (b) die Zuweisung des Lesens von Umgebung und Dateien an die Startschicht `runtime`; **Entscheidung 2 (b1)** ganz; **Entscheidung 4 (d1)** ganz. Es bleiben: a1 (Fassade, `BackendSource`, `StartOutcome`), die Prüfung der Konfiguration in `config`, Einmal-Konfiguration, Entscheidung 3 (`explicitApi`, Fläche, Zusage), Entscheidung 5 (Source-Set `integrator`) und Entscheidung 6.
- [ADR-0018](0018-laufzeit-artefakt.md) die Entscheidungen 1 bis 4 (Basis-Image, Nutzer, Betreiberoptionen über `JAVA_TOOL_OPTIONS`, `laufzeittest` als Lauf des Produkt-Images). Die Zuordnung „E2E-Lauf, nicht in `make gates`“ trägt weiter [ADR-0022](0022-e2e-laeufe-nicht-in-den-gates.md). Der Punkt 5 von ADR-0018 („nicht entschieden“) ist keine Entscheidung und wird weder ersetzt noch übernommen (siehe Offen).
- [ADR-0022](0022-e2e-laeufe-nicht-in-den-gates.md) Entscheidung 5 und die Option M1, **nur** soweit sie die Startklasse betreffen („Main-Class falsch“, „`Main-Class` gegen die Konstante“). Entscheidung 1 (Begriff E2E-Lauf) wird nicht ersetzt, nur gelesen (Entscheidung 3 unten).
- [ADR-0012](0012-simulations-backend-beschreibung-und-formen.md) **nur** den Ort: „Ein Adapter in `runtime` … liest die Datei mit der Bibliothek“ (Ort der Bibliothek, N3 A) und in der Verdrahtung „Kotlin-API und eine Umgebungsvariable mit dem Dateipfad … `runtime` liest die Datei“. Format, Gelesene Teilmenge, Typabbildung, Fehlerfälle und die Größengrenze der Datei gelten unverändert; sie gelten für die Datei des Testservers.

---

## Kontext

**Nutzerentscheidungen vom 2026-11-01:**

1. Das Produkt ist die JVM-Bibliothek mit der Server-Schnittstelle `TdsServer.start(config, source)` ([ADR-0021](0021-oeffentlicher-integrations-einstieg.md)). Es gibt **kein** ausführbares Server-Tool als Produkt: kein Produkt-`main`, kein Container-Image, keine Startklasse im Produkt-Jar. Ein Testserver existiert nur zum Testen (E2E, Kompatibilität, Referenzclient).
2. Die YAML-Beschreibung der Simulations-Tabellen geht in den Testserver. Die Bibliothek nimmt nur Werte (`BackendSource.Simulation(tables)`), kein `simulationFile` und kein Jackson.
3. Das Lastenheft ist in Version 0.6.0 angepasst (Zweck, `TDS-FA-09`, `TDS-QA-06`, `TDS-RB-01`, Glossar „Server“ und „Testserver“; gelesen am 2026-11-01). Spezifikation und angenommene ADRs folgen; diese ADR ist der Teil für die ADRs.

**Ist-Zustand** (am 2026-11-01 im Quelltext und in der Spezifikation gelesen, nichts davon gemessen oder ausgeführt):

- `runtime/Main.kt` ist `fun main()` mit `Wiring(log = ServerLog.STDOUT)`, `runBlocking { Bootstrap.start(System.getenv(), …) }`, Shutdown-Hook und Exit-Code `EXIT_START_FAILED`. `Bootstrap.start` liest die Umgebung über `ConfigLoader.load` und ruft `launch`; `ServerLog.STDOUT` ist die Standardausgabe-Voreinstellung. `ConfigLoader` und `ConfigFiles` liegen heute in `config`; ein Umzug nach `runtime` (`internal`) nach [ADR-0021](0021-oeffentlicher-integrations-einstieg.md) ist auf einem anderen Branch in Arbeit und nicht gemergt.
- `runtime/YamlTreeReader.kt` ist die einzige Stelle mit Jackson im Hauptmodul; `runtime/SimulationSetup.kt` verdrahtet Datei und Tabellen. Der Leser der Beschreibung (`simulation/SimDescriptionReader`, `SimTree`, `SimTreeAccess`; `SimColumnTypes` hängt am Leser) ist reiner Kotlin-Code ohne Bibliothek. `build.gradle.kts` führt Jackson als `implementation` (`libs.jackson.dataformat.yaml`).
- `build.gradle.kts` setzt im `jar`-Task `Main-Class` auf `…runtime.MainKt` und einen `Class-Path` aus `lib/`; die Task `laufzeitBibliotheken` kopiert die Laufzeit-Bibliotheken nach `build/libs/lib`. `tools/manifest-pruefung.sh` prüft Jar-Name, `Main-Class` und die `Class-Path`-Einträge gegen `lib/` (läuft in der Stage `build-jar`). Das Dockerfile hat die Stages `build-jar`, `runtime` (Distroless Java 21 `nonroot`), `laufzeit-client`, `integrationsserver` und `build` (Distroless `static`).
- Im Test-Source-Set liegen bereits `IntegrationServer.kt` (eigenes `main`, Tabellen des Integrationslaufs, `runServer(Wiring(log = ServerLog.STDOUT, …))`), `LaufzeitClient.kt` und `ServerProcess.kt`; Letzteres startet für den Smoke eine Kind-JVM mit `MainKt` aus dem Test-Klassenpfad. Die Gradle-Tasks `laufzeitClient` und `integrationsServer` laufen über `sourceSets.test`.
- Server-als-Produkt-Aussagen stehen in `SPEC-052` (Jar mit Startklasse, `lib/`, Laufzeit-Image, Konfiguration über Umgebung und Datei im Container, `make laufzeittest`, Manifest-Prüfung im Bau), `SPEC-005` und `SPEC-010` (Umgebungsvariablen `LISTEN_PORT`, `LISTEN_HOST`, `ENCRYPTION_POLICY`, `TLS_KEYSTORE_FILE`, `TLS_KEYSTORE_PASSWORD_FILE`), `SPEC-023` (JVM-Flag „des Serverprozesses“), `SPEC-035` (Datei, `SIMULATION_DESCRIPTION_FILE`) und `SPEC-040` (`make simulations-integration` gegen den Serverprozess).

**Annahme, die kippt:** Dass kein Betreiber das Jar als fertigen Prozess erwartet. Das Lastenheft 0.6.0 sagt es ausdrücklich; kippt es, kehrt ein Produkt-`main` mit eigener Entscheidung zurück (Trigger).

## Entscheidung

Wir wählen **Option B: Bibliothek als Produkt, Testserver als Testcode im Test-Source-Set**.

1. **Zielbild des Schnitts.**
   - **Bleibt in `src/main`:** `domain`, `port`, `protocol`, `transport`, `simulation` (Katalog, Backend, Planer, Scanner, Modell, Werte; ohne den Leser der Beschreibung, siehe unten), `config` (Typen, Defaults, Prüfung für beide Wege nach [ADR-0021](0021-oeffentlicher-integrations-einstieg.md)) und `runtime` mit `TdsServer`, `BackendSource`, `StartOutcome`, `launch` (Verdrahten, TLS-Material laden, Binden), `Wiring`, `Server`, `ServerLog` als Schnittstelle mit `NONE`. Öffentlich bleibt die Fläche aus [ADR-0021](0021-oeffentlicher-integrations-einstieg.md) Entscheidung 3; diese ADR fügt ihr nichts hinzu.
   - **Wandert in den Testserver (Test-Source-Set):** `main()`, der Shutdown-Hook und die Exit-Codes, `ServerLog.STDOUT`, der Lader für Umgebung und Dateien (`ConfigLoader`, `ConfigFiles`, die Namen der Umgebungsvariablen, auch die Dateipfade `TLS_KEYSTORE_FILE`, `TLS_KEYSTORE_PASSWORD_FILE`, `AUTH_USERS_FILE`), der YAML-Leser mit Jackson (`YamlTreeReader`), der Leser der Beschreibung (`SimDescriptionReader` samt `SimTree`-Typen und dem Teil von `SimColumnTypes`, den nur er braucht) und der Teil von `SimulationSetup`, der die Datei liest, samt der Obergrenze von 1 MiB. Der Prozess-Teil von `Bootstrap.start` (Lader, Hook, Warten) wird Testserver-Code; `launch` bleibt in der Bibliothek und ist der Teil, den auch `TdsServer.start` ruft.
   - **Fällt aus der Konfiguration der Bibliothek:** `ServerConfig.simulationFile` und `MAX_SIMULATION_FILE_BYTES`. Die Bibliothek kennt keine Datei und kein Dateiformat der Tabellenbeschreibung; der Konflikt `Custom` zusammen mit `simulationFile` ist nicht mehr darstellbar. `BackendSource.None` bleibt (wie `Simulation` mit leerer Liste).
   - **Der Testserver baut `SimTable`-Werte** aus der Datei und übergibt sie an `BackendSource.Simulation` oder `Custom`; er liest also Werte in das, was auch ein Embedder bekommt.
2. **Ort des Testservers (Setzung): `src/test`**, wie `IntegrationServer` heute. Er hat Zugriff auf `internal`, weil er im selben Modul liegt; das ist hier gewollt (Log-Voreinstellung, `launch`) und kein Nachweis der öffentlichen Fläche. Diesen Nachweis führt weiter das nicht assoziierte Source-Set `integrator` ([ADR-0021](0021-oeffentlicher-integrations-einstieg.md) Entscheidung 5). Ein eigenes Source-Set für den Testserver ist nur nötig, wenn er einen Klassenpfad ohne die Testbibliotheken braucht; das ist nicht gelesen worden und wird nicht vorausgesetzt. Jackson wird damit eine Test-Abhängigkeit (`testImplementation`) und verlässt `runtimeClasspath` und `lib/`.
3. **Verhalten des Jars.** `tdswire.jar` ist ein Bibliotheks-Jar **ohne `Main-Class`**; Lizenz in `META-INF` bleibt ([TDS-RB-05](../../../spec/lastenheft.md), Prüfung in `build-jar` bleibt), der `Class-Path` auf `lib/` bleibt als Komfort für Embedder nach [ADR-0021](0021-oeffentlicher-integrations-einstieg.md) (Setzung: kleinste Änderung, `lib/` enthält ohne Jackson nur noch, was die Bibliothek braucht). Die Manifest-Prüfung (M1 von [ADR-0022](0022-e2e-laeufe-nicht-in-den-gates.md)) bleibt im Bau und im Gate, mit geänderter Aussage: kein `Main-Class`-Eintrag (Rückfall wird rot), `Class-Path`-Einträge nur `lib/<Name>` mit vorhandener Datei, Jar-Name `tdswire.jar`. Ihr Selbsttest (`make skript-selbsttest`) zieht mit. Die Option M1 bleibt also, ihr Punkt „Startklasse“ entfällt.
4. **Container und E2E-Stufen.** Es gibt kein Produkt-Image: die Stage `runtime` (Distroless, Nutzer 65532, `JAVA_TOOL_OPTIONS` als Betreiberschnittstelle, Image-Inhalt `/app`) und die Stage `build` (Distroless `static`) entfallen als Produkt; `make build` liefert Compile, Jar, Lizenz- und Manifest-Prüfung. Der Begriff E2E-Lauf aus [ADR-0022](0022-e2e-laeufe-nicht-in-den-gates.md) Entscheidung 1 gilt unverändert; der „Serverprozess“ ist der Testserver-Prozess, der die Bibliothek trägt. Die Stufen ändern ihren Gegenstand, nicht ihre Zuordnung: `make smoke` (Kind-JVM startet das `main` des Testservers statt `MainKt`), `make laufzeittest` (Testserver als Container in einem internen Netzwerk, Rohbytes-Client aus einem zweiten Container, `docker stop` mit Exit-Code 143; das Container-Modell bleibt, [ADR-0019](0019-kompatibilitaetstests-zuschnitt.md)) und `make simulations-integration`. **Setzung:** das Testserver-Container-Image entsteht aus einer Stage auf Basis von `deps` wie `integrationsserver` heute (Test-Klassenpfad, ohne Distroless-Pin); ob der Name `laufzeittest` bleibt und welche seiner Prüfungen (Image-Inhalt, Nutzer, Lizenz in `/app`, Namen der Umgebung) gegenstandslos werden, legt der Umbau fest. Ein Image-Digest-Pin bleibt nur für Basis-Images, die noch gebaut werden ([SPEC-042](../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge)).
5. **Datei der Tabellenbeschreibung.** Format, Teilmenge, Typabbildung und Fehlerfälle von [ADR-0012](0012-simulations-backend-beschreibung-und-formen.md) bleiben; Ort und Umgebungsvariable `SIMULATION_DESCRIPTION_FILE` gehören dem Testserver. Die Unit-Tests des Lesers ziehen mit in `src/test` (sie liegen dort schon als Tests; ihr Gegenstand wandert in denselben Source-Set).
6. **Konfiguration aus Umgebung und Dateien** ist Sache des Testservers, nicht der Bibliothek. Defaults und Prüfung bleiben in `config` für beide Wege; der Testserver ruft sie wie ein Embedder.

## Verglichene Alternativen

| Option | Pro | Contra |
|---|---|---|
| A — nichts tun: Jar mit Startklasse, Image und Datei bleiben im Produkt | keine Änderung | widerspricht der Nutzerentscheidung und dem Lastenheft 0.6.0; Embedder erben Startklasse und Jackson |
| **B — Testserver in `src/test`, Jar als Bibliothek (gewählt)** | kleinste Änderung: das Muster (`IntegrationServer`, `ServerProcess`, `LaufzeitClient`) liegt im Test-Source-Set und läuft; kein zweites Modul; Jackson verlässt die Bibliothek; `integrator` kann ohne Jackson laufen (damit belegbar statt gelesen, [ADR-0021](0021-oeffentlicher-integrations-einstieg.md) Entscheidung 2) | der Testserver ist Testcode: er hat `internal`-Zugriff und gilt nicht als Nachweis der Fläche; das Container-Bild enthält Test-Klassenpfad statt eines schlanken Images |
| C — eigenes Source-Set oder zweites Gradle-Modul für den Testserver | saubere Trennung des Klassenpfads; Jackson nur dort | neue Build-Einheit, Anbindung an Dockerfile, detekt, Lock und `DEPS_TASKS`; Nutzen nicht belegt (siehe Entscheidung 2); [ADR-0021](0021-oeffentlicher-integrations-einstieg.md) b2 hat zwei Module bereits als zu teuer verworfen |
| D — Leser der Beschreibung (ohne Jackson) bleibt in der Bibliothek, nur `YamlTreeReader` wandert | weniger Verschiebung; Format bleibt Bibliotheksaufgabe | totes Format in der Bibliothek ohne Eingang; widerspricht der Nutzerentscheidung „die YAML-Beschreibung geht in den Testserver“; Test und Gegenstand getrennt |
| E — Produkt-`main` als dünner Starter behalten, nur ohne Image | ein Start per `java -jar` bliebe | ein ausführbarer Server als Produkt; widerspricht der Nutzerentscheidung |

## Konsequenzen

- Positiv: Das Produkt ist so klein wie das Lastenheft es sagt; Jackson und Startklasse sind nicht mehr Sache des Embedders; Prozess, Dateien, Umgebung und Container-Bau sind Testcode und ändern sich ohne Zusage; die Aussage „Bibliothek ohne Jackson läuft“ wird durch das Source-Set `integrator` prüfbar.
- Negativ: Der Prozess-Pfad (Umgebung, Dateien, Hook) wird nicht mehr als Produkt gehärtet oder dokumentiert; wer ihn als Betriebsweg nutzte, hat keinen mehr (Nutzerentscheidung). Das Container-Bild der E2E-Stufen sagt über ein Betriebs-Image nichts mehr (kein Produkt-Image). `internal` ist im Testserver frei zugänglich; Abweichungen zwischen Testserver-Pfad und Embedder-Pfad sind möglich und werden nur durch `integrator` gefangen.
- **Wirkung auf den Bestand (gelesen, nicht gemessen):** die Tests `ConfigLoaderTest`, `ConfigUsersFileTest`, `ConfigTestSupport`, `YamlTreeReaderTest`, `SimDescriptionReaderTest`, `ServerSmokeTest`, `LaufzeitClient`, `LizenzTest` und `BootstrapTest` hängen an Teilen, die wandern oder sich ändern; den genauen Umfang bestimmt der Umbau mit dem Übersetzungslauf.
- **Folgepflicht (nach `Accepted`, nicht Teil dieser ADR):**
  1. **Spezifikation nachziehen** (Träger: der Spec-Nachzug nach Annahme): `SPEC-052` (Artefakt: Bibliotheks-Jar ohne Startklasse; Laufzeit-Image, Nutzer, Port, Konfiguration im Container, `JAVA_TOOL_OPTIONS` und der Laufzeittest als Produktaussagen entfallen oder werden Testserver-Aussagen; Manifest-Prüfung ohne Startklasse), `SPEC-005` und `SPEC-010` (Umgebungsvariablen und Dateipfade sind Eingänge des Testservers, nicht der Bibliothek; Wertebereiche, Paarung und Voreinstellungen bleiben Aussagen über `ServerConfig` und ihre Prüfung), `SPEC-023` (das JVM-Flag gilt für jeden Prozess, der die Bibliothek trägt, also Embedder und Testserver), `SPEC-035` (Datei und Umgebungsvariable gehören dem Testserver; Kotlin-Schnittstelle bleibt; `simulationFile` und die Größengrenze nicht mehr in der Bibliothek) und `SPEC-040` (Läufe gegen den Testserver; Heap-Angabe des Integrationslaufs bleibt Angabe des Testservers). Die Spezifikation nennt dabei keine ADR-Bezüge in Sicht-Dokumenten ([AGENTS.md](../../../AGENTS.md) §3.4). `harness/README.md` (Sensors-Tabelle: `build`, `smoke`, `laufzeittest`, `skript-selbsttest`, `kopplung-zahlen`) folgt dem Umbau.
  2. **Umbau, Vorschlag zum Zuschnitt (Planung entscheidet):** (a) Testserver herauslösen: Bewegungen als reine `git mv`-Commits vor jeder Inhaltsänderung ([AGENTS.md](../../../AGENTS.md) §3.3), `ServerConfig.simulationFile` entfernen, `Bootstrap.start` teilen, `ServerProcess` und `IntegrationServer` auf das gemeinsame `main` des Testservers stellen, Jackson auf `testImplementation` und neues Lock (`make deps-lock`); (b) Jar ohne `Main-Class`, Manifest-Prüfung samt Selbsttest anpassen, Stages `runtime` und `build` entfernen, `laufzeittest` und `laufzeit-client` auf den Testserver-Container stellen, `kopplung-zahlen` und README prüfen. (b) setzt (a) voraus.
  3. **Angenommene ADRs bleiben unverändert**; ihr Status in der Indexzeile wird nach Annahme dieser ADR nachgeführt (ADR-0018 ersetzt in den genannten Entscheidungen; die übrigen teilweise).
  4. **Laufende Arbeiten:** Arbeiten, die `ConfigLoader`/`ConfigFiles`, `Main`, die Startklasse, das Laufzeit-Image oder die Datei der Tabellenbeschreibung berühren, werden vor ihrer Weiterführung an diese ADR ausgerichtet, sobald sie angenommen ist; ein bereits geplanter Umzug von Lader und Dateizugriff nach `runtime` ist dann nur Zwischenstand, sein Ziel ist der Testserver.
  5. **Berührte Entscheidungen, nicht geprüft:** [ADR-0011](0011-pruefwertformat.md) (Konfigurationsweg der Benutzerliste über Umgebung und Datei: als Betriebsweg des Produkts hinfällig, als Prüfwertformat unberührt) und [ADR-0002](0002-tls-und-verschluesselung.md) (Schlüsselspeicher als Datei) sind beim Spec-Nachzug auf Produktaussagen zu lesen; ob eine Folge-ADR nötig ist, ist nicht festgestellt.
- **Akzeptiertes Negativ (einmalig):** Die Bibliothek wird nicht mehr als Prozess auf einem Distroless-Image gemessen, und ein Alterssensor für den Digest entfällt mit dem Image; kein Ersatz, weil kein Produkt-Image mehr besteht.

## Fitness Function (falls maschinell prüfbar)

Zeilen sind Absicht, nicht Bestand.

| Tooling | Regel | Make-Target |
|---|---|---|
| `tools/manifest-pruefung.sh` (angepasst) | das Manifest von `tdswire.jar` nennt keine `Main-Class`; `Class-Path` nur `lib/<Name>` mit vorhandener Datei | `make build` (und damit `make gates`), Selbsttest `make skript-selbsttest` |
| Source-Set `integrator` ohne Jackson im Klassenpfad | der Einbettungstest startet und bedient den Server ohne Jackson; ein Rückfall von Jackson in `implementation` bricht ihn nicht, daher nur Absicht | `make test` |
| Zugriff in `src/main` | kein Import aus `com.fasterxml` im Hauptmodul | heute keine Importregel dafür; `make detekt` kann sie tragen, Entscheidung des Umbaus |

## Offen

- **Log der Bibliothek:** `ServerLog.STDOUT` wandert in den Testserver, die Bibliothek kennt nur `NONE`; wie ein Embedder Log erhält, entscheidet [ADR-0020](0020-logformat-und-observability-schnittstelle.md) (Proposed). Dort steht die Möglichkeit, den Log-Schreiber an `jackson-core` zu hängen; sie verträgt sich nicht mit „kein Jackson in der Bibliothek“ und ist beim Nachzug von [ADR-0020](0020-logformat-und-observability-schnittstelle.md) zu entscheiden. Der Testserver braucht bis dahin einen Log-Zugang über `Wiring` (`internal`).
- **Schnitt von `SimColumnTypes`:** ob die Typabbildung der Beschreibung (Name der Spaltenart auf `SqlType`) vollständig in den Leser gehört oder Teile in der Bibliothek gebraucht werden, ist nicht gelesen; der Übersetzungslauf des Umbaus entscheidet, wie die Datei zu schneiden ist.
- **Lizenzhinweise der Bibliotheken** (offener Punkt von ADR-0018): bleibt für das Bibliotheks-Jar mit `lib/` offen und gehört dem Release-Prozess; diese ADR entscheidet ihn nicht.

## Re-Evaluierungs-Trigger

Ein Betreiber oder Integrator, der einen ausführbaren Server mit Betriebs-Image verlangt (dann Produkt-`main` und Image neu); der Wunsch, die Bibliothek in einem Artefakt-Repository zu veröffentlichen ([ADR-0021](0021-oeffentlicher-integrations-einstieg.md), Option b2: dann gilt der Klassenpfad ohne Jackson als Voraussetzung und ist zu belegen); ein Testserver-Szenario, das einen Klassenpfad ohne Testbibliotheken oder ein schlankes Image braucht (dann Option C); ein Befund, dass Testserver-Pfad und Embedder-Pfad auseinanderlaufen.

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-11-01 | Nutzerentscheidung: Bibliothek ist das Produkt, Testserver nur zum Testen, YAML-Beschreibung in den Testserver; Lastenheft 0.6.0 | Sitzung |
| 2026-11-01 | Proposed – Annahme durch den Nutzer steht aus | — |

Nach `Accepted` wird diese Datei **nicht mehr inhaltlich überschrieben**.
Spätere Korrekturen oder Schärfungen entstehen als neue ADR mit
`Supersedes ADR-NNNN` (Baseline-Regelwerk `modul-04-adrs.md`
§Hard Rule für Accepted-ADRs).
