# Slice slice-testserver-herausloesen: Testserver im eigenen Source-Set herauslösen, `simulationFile` und Jackson aus der Bibliothek nehmen

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-produktschnitt

**Bezug:** [TDS-RB-01](../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) (Tabellenbeschreibung), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität) (Referenzclient-Läufe); [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) (`Accepted`, Entscheidungen 1, 2, 4 bis 7 und Folgepflicht 2 (a)), [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) (Entscheidungen 1, 3, 5), [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md) (`Accepted`, Entscheidung 2: Aufzeichner über Port und `SimulationBackend`), [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) (`Accepted`, Frage 1: Format als Adapter im Testserver; Träger der Testserver-Seite ist `slice-betrieb-log-adapter-testserver`), [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md) (Begriff E2E-Lauf), [ADR-0019](../../adr/0019-kompatibilitaetstests-zuschnitt.md) (Container-Modell).

**Berührte Spec-Stellen:** SPEC-005 · SPEC-010 · SPEC-023 · SPEC-035 · SPEC-040 · SPEC-052 · SPEC-059 (die Aussagen stehen nach `slice-spec-nachzug-produktschnitt`; dieser Slice setzt sie im Code um und entfernt die Zielzustand-Vermerke, die sie erfüllt (Nachweis-Pflicht in der DoD), und ändert sie sonst nur bei einem Befund).

**Verantwortlich:** pt9912.

**Autor:** pt9912. **Datum:** 2026-11-01.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Der Serverprozess der E2E-Läufe ist ein Testserver im eigenen, nicht mit `main` assoziierten Source-Set `testserver`, der die Bibliothek ausschließlich über `TdsServer.start`, `BackendSource`, `StartOutcome` und die öffentlichen Typen bindet; `ServerConfig.simulationFile`, Lader, YAML-Leser, Beschreibungs-Leser und Jackson liegen nicht mehr in `src/main`, und die Stufen 1, 2, 3 und 5 bis 7 von `make e2e` laufen gegen den Testserver ([ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) Entscheidungen 1 bis 7).

**Befund, der den Slice begründet** (am 2026-11-01 im Quelltext und in den Skripten gelesen, nichts ausgeführt; `main` seit dem Stand `8fc765d`): `runtime/Main.kt` ist `fun main()` mit `Bootstrap.start`; `ConfigLoader`, `ConfigFiles` und `YamlTreeReader` liegen als `internal` in `runtime`; `SimDescriptionReader`, `SimTree`, `SimTreeAccess` und `SimColumnTypes` liegen in `simulation`; `ServerConfig.simulationFile` und `MAX_SIMULATION_FILE_BYTES` stehen in `config`; Jackson ist `implementation` in `build.gradle.kts` und `integratorRuntimeOnly`. Die Prozess-Einstiege `IntegrationServer`, `KompatibilitaetsServer`, `ServerProcess`, `ServerSmokeTest` und `LaufzeitClient` liegen in `src/test` und binden `Wiring`, `RequestSource` und `ServerLog.STDOUT` aus `internal`.

**Vorbedingung „Folge-ADR angenommen“: erfüllt** (Offen-Punkt 1 der [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md)): [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md) ist am 2026-11-01 `Accepted`, Variante (i). Der `RecordingBackend` des Kompatibilitätslaufs ist ein `QueryBackend`, das ein inneres `QueryBackend` umhüllt, und `main` des Testservers baut `RecordingBackend(SimulationBackend(SimCatalog(KompatibilitaetsTables.all())))` aus öffentlichen Namen (gelesen in der ADR, nicht übersetzt; der Übersetzungslauf dieses Slice belegt es, ein weiterer nötiger Name ist der Trigger der [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md) und führt in eine Folge-ADR, nicht in `internal`).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Das Jar ohne `Main-Class`, die Manifest-Prüfung ohne Startklasse samt Selbsttest, das Entfernen der Stages `runtime` und `build` und das Aufräumen von `laufzeittest` — `slice-jar-ohne-main-und-image`. Dieser Slice stellt nur den Server-Container des Laufs `make laufzeittest` auf den Testserver um (Setzung unten), damit Stufe 2 grün bleibt; die Stage `runtime` bleibt als ungenutzte Stage stehen.
- Das Log der Bibliothek im Testserver — [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) ist `Accepted` und legt fest: die Bibliothek definiert Modell und Senke samt Parameter an `TdsServer.start`, der JSON-Adapter (`jackson-core`), `LOG_LEVEL` und die Warteschlange liegen im Testserver. Gebaut wird das in zwei Slices der Welle [welle-betrieb-diagnose](../welle-betrieb-diagnose.md): [slice-betrieb-log-schnittstelle](../done/slice-betrieb-log-schnittstelle.md) (Bibliothek, wartet auf diesen Slice) und [slice-betrieb-log-adapter-testserver](../next/slice-betrieb-log-adapter-testserver.md) (Testserver, wartet auf beide). Der Testserver ruft hier `TdsServer.start(config, source)` ohne Senke und hat bis dahin kein Log aus der Bibliothek ([ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) Entscheidung 7). Dieser Slice nimmt die Prüfungen heraus, die davon leer wahr würden, kennzeichnet sie mit dem Träger des Ersatzes und ersetzt sie nicht. `ServerLog` samt `STDOUT` bleibt in `src/main`, bis `slice-betrieb-log-schnittstelle` es ablöst (Tests in `src/test` nutzen es).
- Die Spezifikation — sie steht nach `slice-spec-nachzug-produktschnitt`; dieser Slice nimmt nur die Zielzustand-Vermerke heraus, die er erfüllt (DoD, Nachweis-Pflichten); ein Widerspruch, den der Umbau findet, ist ein Befund für den Nutzer, kein stilles Ändern.
- `explicitApi` und die Verkleinerung der Fläche ([slice-explicit-api-umbau](../done/slice-explicit-api-umbau.md)) — Folge-Slice derselben Welle, Start-Trigger ist `done/` dieses Slice (damit er nicht zweimal umbaut); dieser Slice ändert keine Sichtbarkeit. Fehlt dem Testserver ein Typ in der öffentlichen Fläche, ist das ein Befund über die Fläche eines Embedders und kein Anlass, `internal` zu öffnen ([ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) Entscheidung 1).
- Ein Sensor gegen Java-Quellen im Source-Set (`BEO-TDS/java-quelle-umgeht-kotlin-regel`) — akzeptiertes Negativ der ADR.
- Änderungen am Format der Tabellenbeschreibung ([ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md)) — der Leser wandert, er ändert sich nicht.
- Das Zusammenlegen mit dem Source-Set `integrator` — Jackson im Klassenpfad des Einbettungstests machte die Aussage „läuft ohne Jackson“ unbelegbar ([ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) Entscheidung 2).

**Setzungen des Planners** (gelesen und nicht gemessen; der Implementer bestätigt sie im ersten Lauf oder meldet den Befund):

- Das Paket des Testservers ist `io.github.pt9912.tdswire.testserver`; die Verzeichnisse `src/testserver/kotlin` und `src/testserver/resources`.
- `IntegrationServer.kt`, `KompatibilitaetsServer.kt` und `LaufzeitClient.kt` wandern ebenfalls nach `src/testserver` (Prozess-Einstiege mit `main`); `ServerProcess` und `ServerSmokeTest` liegen als Tests des Testservers in `src/testserver` und laufen über `testserverTest`.
- Der Server-Container von `make laufzeittest` entsteht aus derselben Stage wie `integrationsserver` (Klassenpfad des Source-Sets, [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) Entscheidung 4) und ersetzt die Stage `runtime` als Server dieses Laufs; die Prüfungen des Skripts, die ein Produkt-Image betreffen (Image-Inhalt, Nutzer 65532, Lizenz in `/app`, `JAVA_TOOL_OPTIONS`), werden in diesem Slice herausgenommen oder gekennzeichnet, wenn der Gegenstand fehlt; das Aufräumen der übrigen Reste und der Name des Targets sind Sache von `slice-jar-ohne-main-und-image`. Grund: Mit `MainKt` außerhalb von `src/main` kann die Stage `runtime` nicht mehr starten; ohne diese Setzung bliebe Stufe 2 bis zum Folge-Slice rot, ohne dass `make gates` es zeigt (E2E-Läufe sind kein Gate, [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)).
- Die Zeile `tdswire: gestoppt` beim Beenden schreibt der Testserver in seinem Shutdown-Hook selbst, zeichengleich, damit die Prüfung von `laufzeittest.sh` („Ausgabe nennt `gestoppt`“) die Bibliothek nicht liest; ob das reicht, belegt der Lauf.
- Der Aufzeichner des Kompatibilitätslaufs (`RecordingBackend`) umhüllt `SimulationBackend(SimCatalog(…))` und geht als `BackendSource.Custom(backend)` an den Einstieg ([ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md) Entscheidung 2); `Custom` zusammen mit gesetzter `simulationFile` gibt es nach diesem Slice nicht mehr.
- Der Testserver hält den Aufruf von `TdsServer.start` in einer Stelle, an der `slice-betrieb-log-adapter-testserver` den Parameter der Senke nachträgt (Setzung der Form; kein Platzhalter-Parameter, kein totes Feld).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Wortlaut und Zuschnitt folgen [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) Folgepflicht 2 (a); drei Liefer-Punkte, jeder einzeln im Gate belegbar. Reihenfolge der Commits: Bewegungen als reine `git mv`-Commits vor jeder Inhaltsänderung (`AGENTS.md` §3.3), ein Move-Commit und sein Paket-Commit gehören in denselben Push (`BEO-TDS/move-commit-laesst-detekt-rot`).

Liefer-Punkte (drei):

- [x] **Source-Set, Schnitt und Jackson** ([TDS-RB-01](../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)): `src/testserver` (nur Kotlin) ist übersetzbar und gegen `sourceSets.main.output` gebunden, ohne Assoziation zu `main`; ein Sensor bricht die Übersetzung, wenn das Source-Set assoziiert ist (Muster `integratorOhneAssoziation`; Form: erweitert oder dupliziert). `testserverTest` läuft in der Stage `test`; `DEPS_TASKS`, die detekt-Aufgabe des Source-Sets und die Stages des Dockerfiles führen es; Lock und Prüfsummen über `make deps-lock` neu. Jackson ist `testserverImplementation` und steht weder in `implementation`, noch in `integratorRuntimeOnly`, noch in `build/libs/lib`; `make detekt` sperrt einen Import aus `com.fasterxml` in `src/main` (Importregel mit Begründung am Eintrag, `AGENTS.md` §3.6: eine neue Sperre ist keine Lockerung). `ServerConfig.simulationFile`, `MAX_SIMULATION_FILE_BYTES` und die Prüfung der Dateigröße in `ConfigValidation` sind entfernt; `Bootstrap.start` ist geteilt (Prozess-Teil im Testserver, `launch` `internal` in der Bibliothek); `SimColumnTypes` ist so geschnitten, dass die Bibliothek nur behält, was sie braucht (Offen-Punkt der ADR, der Übersetzungslauf entscheidet). Die Bewegungen sind reine Move-Commits (R100 in `git log --follow`, Beleg in der Closure-Notiz). **Belege:** (1) die Mutation „Testserver ruft ein `internal`-Symbol“ macht die Übersetzung des Source-Sets rot; (2) `integratorTest` läuft grün ohne Jackson im Klassenpfad des Source-Sets `integrator` (schließt `BEO-TDS/jackson-abhaengigkeit-fuer-embedder-nicht-gemessen`, wenn er besteht); (3) `make test` und `make detekt` sind grün.
- [x] **Testserver-Prozess und Test-Einstiege** ([TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)): `main` des Testservers liest Umgebung und Dateien (Lader), baut `ServerConfig` und `SimTable`-Werte, ruft `TdsServer.start`, schreibt nach `StartOutcome.Started` die Zeile `tdswire: gestartet, Port <port>` zeichengleich zur heutigen Bereitschaftszeile (der Port ist der des laufenden Servers) und beendet sich bei `Aborted` mit dem Grund und dem bisherigen Exit-Code; der Hook schreibt `tdswire: gestoppt`. `IntegrationServer`, `KompatibilitaetsServer` (mit `RecordingBackend` nach der Folge-ADR), `ServerProcess`, `ServerSmokeTest` und `LaufzeitClient` laufen über `TdsServer.start`; die Kind-JVM des Smoke startet das `main` des Testservers aus dem Klassenpfad des Source-Sets. `BootstrapTest` ist geteilt (Prozess-Teil im Testserver, `launch`-Teil in `src/test`); `ConfigLoaderTest`, `ConfigUsersFileTest`, `ConfigTestSupport`, `YamlTreeReaderTest`, `SimDescriptionReaderTest` liegen im Source-Set `testserver`. **Belege:** `ServerSmokeTest` und ein Test der Bereitschaftszeile bestehen in `make smoke` und `make test` (`ServerProcess.port()` liest die Zeile); jeder Test trägt im KDoc den Block-Tag `@spec SPEC-<NNN>` seiner Festlegung. **E2E-Stufe:** Stufe 1 (`make smoke`) ist grün und ihre Deklaration in `tools/e2e.sh` nennt den Testserver statt `main` des Produkts ([slice-e2e-geruest](../done/slice-e2e-geruest.md), Kennungen unverändert: [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-RB-01](../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)); `make e2e-abdeckung` zeigt sie danach in `docs/plan/e2e-abdeckung.md`, und der Lauf geht über die Prozessgrenze (Kind-JVM, Rohbytes-Client), nicht über `internal`-Zugriff.
- [x] **Container, Skripte und ehrliche Logprüfungen** ([TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit)): Die Stufen `laufzeit-client`, `integrationsserver` und `kompatibilitaetsserver` des `Dockerfile` und die Gradle-Tasks `laufzeitClient`, `integrationsServer`, `kompatibilitaetsServer` nehmen den Klassenpfad des Source-Sets `testserver`; der Server-Container von `tools/laufzeittest.sh` kommt aus der Stage des Testservers (Setzung oben); `tools/integrationszahlen-pruefung.sh`, die Stage `kopplung-zahlen` und der Selbsttest folgen dem neuen Pfad von `IntegrationServer.kt` (acht Mutationen weiter grün). **Log-Verlust:** keine Skript-Prüfung bleibt leer wahr. Aus den Skripten genommen oder als Bibliotheks-Log-Prüfung gekennzeichnet (mit Verweis auf die Prüfung in `src/test`, die sie ersetzt, soweit festgestellt) sind: (a) „Log des Servers nennt keinen Handlerfehler“ (`simulationstest.sh`, `kompatibilitaetstest.sh`), (b) „Log enthält weder `secret` noch `Geheimnis`“, soweit es Zeilen der Bibliothek betraf (bleibt für das Backend-Log des Testservers), (c) „Log nennt als Grund … `UNSUPPORTED_PASSWORD_CHANGE`“ und `UNSUPPORTED_FEDERATED_AUTHENTICATION` (`Login: abgelehnt (…)`), und die Prüfungen, die das Planen beim Lesen der Skripte zusätzlich fand (grep, nicht ausgeführt): `tdswire: Simulations-Backend: 4 Tabellen` (`simulationstest.sh`), `tdswire: Fehler: TDS_PROTOCOL_ERROR`, `TDS_UNSUPPORTED_TYPE`, `TDS_PREPARE_LIMIT…` und `TDS_UNSUPPORTED_RPC (UNKNOWN_PROCEDURE)` (`kompatibilitaetstest.sh`; die Zeilen `Fehler:` schreibt `ConnectionWiring` der Bibliothek). Bleibt: die Bereitschaftsschleife (`docker logs … | grep -q 'tdswire: gestartet, Port 1433'`) unverändert und die Zeilen des `RecordingBackend` (`kompatibilitaet: anfrage …`). Der Ersatz kommt nicht aus diesem Slice: [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) ist `Accepted`, aber Senke und Parameter von `start` entstehen erst mit [slice-betrieb-log-schnittstelle](../done/slice-betrieb-log-schnittstelle.md), der Adapter des Testservers mit [slice-betrieb-log-adapter-testserver](../next/slice-betrieb-log-adapter-testserver.md). Jede herausgenommene oder gekennzeichnete Prüfung trägt im Skript die Kennung des Slice, der sie ersetzt (Zusage im Kommentar, `AGENTS.md` §3.7), und die Liste der Prüfungen steht in der Closure-Notiz als Übergabe an ihn. **E2E-Stufen:** `make e2e` ist grün in den sieben Stufen; Stufe 2 (Container, Rohbytes-Login aus dem zweiten Container, `docker stop` mit Exit-Code 143), Stufe 3 (Referenzclient gegen Simulations-Backend) und die Stufen 5 bis 7 (Referenzclient, Kompatibilität) gehen gegen den Testserver-Container und sind in `tools/e2e.sh` so deklariert ([slice-e2e-geruest](../done/slice-e2e-geruest.md)); `make e2e-abdeckung` zeigt sie in `docs/plan/e2e-abdeckung.md`. Die Zahlen der Messung (Warm- und Kaltlauf der Stufen) stehen in der Closure-Notiz, nicht aus dem Gedächtnis.

Nachweis-Pflichten für alle Punkte:

- [x] `make gates` grün.
- [x] **Zielzustand-Vermerke der Spezifikation entfernt** (Nachweis, kein Liefer-Punkt; Zusage aus `slice-spec-nachzug-produktschnitt` §3): Die Zielzustand-Vermerke in `spec/spezifikation.md`, die dieser Slice erfüllt, sind an den Stellen entfernt (Stand per `grep -n Zielzustand spec/spezifikation.md` im Nachweis geprüft, mit Treffern vor und nach dem Umbau); die Stellen, die der jeweils andere Slice trägt, bleiben stehen. Ein stehengebliebener Vermerk ist ein Befund (`BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`), ein entfernter Satz ohne tragenden Code ebenso. Die Zeilennummern gelten für den Stand nach `slice-spec-nachzug-produktschnitt` und verschieben sich mit jeder Änderung der Datei; maßgeblich ist der Text der Stelle.

  | Zeile | Stelle |
  |---|---|
  | 607 | Absatz Tabellenbeschreibung (`SPEC-035`): Leser, Datei, Format beim Testserver |
  | 685 | Spiegelstrich Datei und Umgebungsvariable (`SPEC-035`): `simulationFile`, Bytes der Datei in `ServerConfig`, Zusammenführen im Einstieg |
  | 823 | `SPEC-005`: `LISTEN_*` aus der Umgebung |
  | 830 | `SPEC-010`: `ENCRYPTION_POLICY` aus der Umgebung |
  | 1085 | Schlüsselmaterial: `TLS_KEYSTORE_*` als Dateien |
  | 1150 | `SPEC-051` Konfigurationsweg: `AUTH_USERS_FILE` |
  | 1151 | `SPEC-051` Testmodus-Schalter `AUTH_PLAIN_TESTMODE` (Verweis auf 1150) |
  | 1155 | `SPEC-051` Lesen der Datei (Verweis auf 1150) |
  | 1180 | `SPEC-051` Validierung der Liste (Verweis auf 1150) |
  | 1207 | `SPEC-059` Zeile `BackendSource`: Feld für die Bytes der Datei, `Custom`-Abbruch |
  | 1209 | `SPEC-059` Zeile Prüfung: Größe der Beschreibung, Umgebung im Prozess der Bibliothek |
  | 1225 | `SPEC-040`: Integrationsserver bindet an `TdsServer.start` vorbei |

  Die Stellen 1213 und 1228 (`SPEC-059` Abhängigkeiten, `SPEC-052`) trägt `slice-jar-ohne-main-und-image`.

  Lesehilfe-Lücke, mitzuprüfen (Befund der Verifikation, kein Liefer-Punkt): `LOGIN_TIMEOUT` (`SPEC-008`), `MAX_PRELOGIN_MESSAGE_BYTES` (`SPEC-046`), `SHUTDOWN_GRACE` und `QUERY_TIMEOUT` stehen als „konfigurierbar“ in der Festlegungstabelle, ohne als Eingang des Testservers ausgezeichnet zu sein wie `LISTEN_*`; nach dem Umbau ist zu lesen, ob die Auszeichnung fehlt, und ein Befund an den Nutzer zu melden.

- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update: `harness/README.md` — die Zeilen `make smoke`, `make simulations-integration` und `make laufzeittest` sagen Testserver statt Serverprozess und Produkt-Image-Inhalt (die Zeilen `build`, `skript-selbsttest`, `kopplung-zahlen` und `e2e` folgen mit `slice-jar-ohne-main-und-image`, soweit ihre Aussage erst dort wahr wird); KDoc des Testservers (Einstieg, Bereitschaftszeile, Exit-Codes).
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo mit Wellen von der Welle-Closure geprüft.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `build.gradle.kts` | update | Source-Set `testserver` (nicht assoziiert) mit Sensor, `testserverTest`, Jackson nur dort, Tasks `laufzeitClient`, `integrationsServer`, `kompatibilitaetsServer` auf dessen Klassenpfad, `detekt`-Quellen |
| `gradle.lockfile`, `gradle/verification-metadata.xml` | update (erzeugt) | `make deps-lock` (braucht Netz) |
| `tools/mk/build.mk` | update | `DEPS_TASKS` um die Tasks des Source-Sets erweitert |
| `config/detekt/detekt.yml` | update | Importregel gegen `com.fasterxml` in `src/main`; Regeln für `src/testserver` (Prozess-Code mit `System.getenv`, Exit-Codes), jeweils mit Begründung am Eintrag |
| `src/main/.../runtime/Main.kt`, `YamlTreeReader.kt`, `ConfigLoader.kt`, `ConfigFiles.kt` | `git mv` (rein), dann update | nach `src/testserver`; Paket `testserver`, `main` ruft `TdsServer.start` |
| `src/main/.../runtime/Bootstrap.kt`, `SimulationSetup.kt` | update | `Bootstrap.start` geteilt; der Dateiteil von `SimulationSetup` wandert in den Testserver |
| `src/main/.../simulation/SimDescriptionReader.kt`, `SimTree.kt`, `SimTreeAccess.kt` | `git mv` (rein), dann update | nach `src/testserver`; `SimColumnTypes.kt` wird geschnitten (nur der Leser-Teil wandert) |
| `src/main/.../config/ServerConfig.kt`, `ConfigValidation.kt`, `runtime/TdsServer.kt` | update | `simulationFile` und Größengrenze entfernt; KDoc von `BackendSource` ohne Datei |
| `src/test/.../runtime/ConfigLoaderTest.kt`, `ConfigUsersFileTest.kt`, `ConfigTestSupport.kt`, `YamlTreeReaderTest.kt`, `simulation/SimDescriptionReaderTest.kt`, `LaufzeitClient.kt`, `IntegrationServer.kt`, `KompatibilitaetsServer.kt`, `ServerProcess.kt`, `ServerSmokeTest.kt` | `git mv` (rein), dann update | in `src/testserver`; Einstiege auf `TdsServer.start`, nur öffentliche Fläche |
| `src/test/.../runtime/BootstrapTest.kt`, `LizenzTest.kt`, `config/ConfigValidationTest.kt` | update | `BootstrapTest` geteilt; `LizenzTest` nur soweit er das Jar liest; Fall `simulationFile` entfällt; weitere Tests, die den Übersetzungslauf nennt |
| `src/integrator/...`, `build.gradle.kts` (`integratorRuntimeOnly`) | update | Jackson aus dem Klassenpfad des Einbettungstests |
| `Dockerfile` | update | Stages `laufzeit-client`, `integrationsserver`, `kompatibilitaetsserver`, `test`, `detekt`, `spec-abdeckung`, `kopplung-zahlen` (Pfad), Stage des Testserver-Containers für `laufzeittest` |
| `tools/laufzeittest.sh`, `tools/simulationstest.sh`, `tools/kompatibilitaetstest.sh` | update | Server-Container aus der Testserver-Stage; Logprüfungen der Bibliothek heraus oder gekennzeichnet |
| `tools/integrationszahlen-pruefung.sh`, `tools/integrationszahlen-selbsttest.sh` | update | neuer Pfad von `IntegrationServer.kt` |
| `tools/e2e.sh` | update | Stufentexte nennen den Testserver; Kennungen unverändert |
| `docs/plan/e2e-abdeckung.md`, `docs/plan/spec-abdeckung.md` | update (erzeugt) | `make e2e-abdeckung`, `make spec-abdeckung` |
| `harness/README.md` | update | Zeilen `smoke`, `simulations-integration`, `laufzeittest` |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf; den Umfang bestimmt der Übersetzungslauf ({ADR25}: „Wirkung auf den Bestand“).

Start-Trigger, Bedingung 3: Der Start der Welle ist am 2026-11-01 vom Nutzer ausdrücklich verlangt; `slice-e2e-geruest` liegt noch in `in-progress/` (Abweichung vom Wortlaut), `tools/e2e.sh` hat sieben Stufen (Entscheidung F-6, in `main`).

**Ergebnis des Übersetzungslaufs** (Abweichungen von den Setzungen oben; Zustand, Belege in der Übergabe an den Reviewer):

- `ServerProcess`, `ServerSmokeTest`, `LaufzeitClient` und `RawClient` bleiben in `src/test`: sie bauen ihre Bytes mit `SessionVectors`, `TokenDecoder` und `LoginAck`, alle `internal` in `main` oder Test-Hilfen; die Kind-JVM startet das `main` des Testservers über die Systemeigenschaft `tdswire.testserverclasspath`. `laufzeitClient` läuft deshalb weiter über `sourceSets.test`.
- Die Tests des Testservers liegen unter `src/testserver/test/kotlin` (derselbe Source-Set `testserver`), damit detekt sie wie `src/test` behandelt; `IntegrationServer.kt`, `KompatibilitaetsServer.kt` und `sim.yaml` liegen in `src/testserver/kotlin` und `src/testserver/resources`.
- Die Sperre gegen `com.fasterxml` in `src/main` ist `tools/jackson-sperre.sh` (Stage `detekt`, Selbsttest in `tools/detekt-selbsttest.sh jackson`), keine detekt-Importregel: deren Selbsttest vergleicht Regeln mit Packages, und `runtime` hat keine Regel.
- Der Lader meldet Form und Dateifehler, die Wertebereiche meldet der Start der Bibliothek (`ConfigValidation.check`); `SimColumnTypes` ist ganz in den Testserver gewandert, die Bibliothek behält nur die Obergrenze 38 in `SimModel`; `EnvKeys` führt die Namen der Umgebungsvariablen, `ConfigKeys` bleibt für die Schlüssel der Diagnose.
- Eine Datei der Beschreibung ohne Tabellen (`tables: {}`) ergibt wie bisher ein Backend mit leerem Katalog (der Testserver übergibt `Custom(SimulationBackend(SimCatalog(emptyList())))`, denn `Simulation` mit leerer Liste hieße: kein Backend); belegt von `TestserverSimulationProcessTest`.
- Der Lader meldet Formfehler und Dateifehler; die Wertebereiche meldet erst der Start der Bibliothek. Treten beide zugleich auf, meldet der Testserver nur die Formfehler (Setzung, die Spezifikation verlangt „alle Fehler in einem Durchlauf“ nicht); belegt von `TestserverStartTest`, Fall gemischt.
- `NameFold` im Testserver und `SimNames` in der Bibliothek sowie die Obergrenze 38 in `SimColumnTypes` und `SimModel` sind Kopien derselben Regel; `SimNames` ist `internal`, ein Gleichheitstest über die öffentliche Fläche prüft nur die Wirkung (gleiche Schreibweise in Datei und Katalog, `TestserverStartTest`); Drift ist sonst ungeprüft (Register: `BEO-TDS/zahl-an-mehreren-orten-ohne-sensor`).
- Jackson-Rückfall nach `implementation`: das Lock erkennt ihn (`Resolved … is not part of the dependency lock state`, gemessen im Review), solange niemand `make deps-lock` neu schreibt; danach zeigt die `gradle.lockfile` Jackson in `compileClasspath`, die Änderung ist im Diff sichtbar.
- Die Wiring-Tests in `src/test` bauen ihre `ServerConfig` über `configOf` (nur gültige Umgebungen); die Ladefehler prüft der Testserver.


## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`), alle Bedingungen beobachtbar:

1. `slice-spec-nachzug-produktschnitt` liegt in `done/` (die Spezifikation sagt, was der Slice umsetzt).
2. **Vorbedingung „Folge-ADR angenommen“ (erfüllt):** [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md) steht im ADR-Index auf `Accepted` (2026-11-01, Variante (i)).
3. Der Start-Trigger von [welle-produktschnitt](../welle-produktschnitt.md) ist eingetreten ([ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) `Accepted` und Nennung des Starts durch den Nutzer), und `slice-e2e-geruest` liegt in `done/`.
4. Ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): der Slice trägt drei große Liefer-Punkte. Gelingt der Übersetzungslauf nicht in einem Zug (Move-Commits, Pakete, Bibliothek ohne Datei, Test-Einstiege), zerfällt er in **(2a)** Source-Set, `git mv`, Konfiguration und Jackson (Liefer-Punkt 1), **(2b)** Testserver-`main`, Bereitschaftszeile und Umstellung der Test-Einstiege samt Stufe 1 (Liefer-Punkt 2), **(2c)** Container, Skripte und Logprüfungen (Liefer-Punkt 3); jeder Teil schließt mit grünem `make gates`, 2b setzt 2a voraus, 2c setzt 2b voraus. Zwischen 2a und 2c ist `make e2e` rot (Stufen 2, 3, 5 bis 7 starten über die alten Stages); die Teile liegen dann in derselben Welle und der Zustand steht in der Roadmap, nicht als stilles Rot.
- `in-progress` → `open` (blockiert — Carveout?): Der Testserver braucht einen Typ, der nicht öffentlich ist (Befund über die Fläche; Folge-ADR oder `slice-explicit-api-umbau`, nicht `internal` öffnen), oder [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md) wird durch eine Folge-ADR abgelöst, oder `slice-betrieb-log-schnittstelle` liegt wider Erwarten schon in `done/` (dann ersetzt dieser Slice die Logprüfungen mit dem Adapter, statt sie herauszunehmen; die Kante in dessen §4 schließt das aus), oder ein Gate müsste gelockert werden (`AGENTS.md` §3.6).

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün, `make e2e` ist in allen sieben Stufen grün (Messung in der Closure-Notiz, nicht aus dem Gedächtnis), der Review-Report liegt vor, und die Closure-Notiz trägt den Steering-Loop-Lerneintrag. Gemessen sind die Übersetzung gegen die öffentliche Fläche (Mutation), die Abwesenheit von Jackson im Einbettungstest und die Läufe über die Prozessgrenze mit Rohbytes-Client und Referenzclient; nicht gemessen sind Java-Quellen im Source-Set und das Log der Bibliothek ([ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md), akzeptierte Negative).

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- `SimColumnTypes` lässt sich nicht sauber schneiden (Teile werden von der Bibliothek gebraucht); der Übersetzungslauf entscheidet. — **Ausgang:** entfallen (geschnitten: die Bibliothek behält nur die Grenze `MAX_DECIMAL_PRECISION` in `SimModel.kt`, die Typabbildung der Datei liegt im Testserver; die Kopie der Namensfaltung hält `NameFoldTest` gleich).
- Dem Testserver fehlt ein Typ in der öffentlichen Fläche (etwa `TlsKeyMaterial`, `UserList.parse`, `SimTable`, `SimColumn`, `SqlType`, oder ein dritter Name aus `simulation` neben `SimulationBackend` und `SimCatalog`); das ist ein Befund über die Fläche eines Embedders, kein Anlass für `internal`; für `simulation` ist es der Trigger der [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md) (Folge-ADR). — **Ausgang:** entfallen (der Testserver übersetzt gegen die öffentliche Fläche, kein Symbol wurde von `internal` geöffnet; Mutation m8 der Verifikation, ein `internal`-Zugriff im Testserver, ist rot am Compiler).
- Verlust beim Log: zusätzlich zu den Prüfungen (a) bis (c) der ADR lesen die Skripte weitere Zeilen der Bibliothek (`Simulations-Backend: 4 Tabellen`, `gestoppt`, `Fehler: <Code>`); die Liste ist aus den Skripten gelesen, nicht gemessen und kann unvollständig sein. — **Ausgang:** eingetreten; die Übergabeliste steht in §7, die Skripte tragen die Kennung von `slice-betrieb-log-adapter-testserver`, und die Verifikation hat die gelöschten Prüfungen gegen `git diff main...HEAD` abgeglichen; die Kennzeichnung hält kein Sensor (F-4, in den Adapter-Slice §6 übergeben).
- Die Bereitschaftszeile des Testservers ist nicht zeichengleich zur heutigen, und die Schleifen der Skripte und `ServerProcess.port()` warten ins Leere; ob die Zeichen gleich sind, belegt der Lauf. — **Ausgang:** entfallen (`tdswire: gestartet, Port <port>` zeichengleich; `TestserverStartTest > Die Bereitschaftszeile nennt den Port des laufenden Servers`, Mutation m1 rot, `make e2e` in sieben Stufen grün).
- Reine Move-Commits lassen das Repo kurz rot (Paketdeklaration gegen Verzeichnis, `BEO-TDS/move-commit-laesst-detekt-rot`); der Zwischenstand darf nicht die Spitze eines Push werden. — **Ausgang:** eingetreten (`5c8edd4`, `1a4423d`, `9a3406e` sind R100 und lassen das Repo bis `6f24fbe` rot; Evidenz unter `BEO-TDS/move-commit-laesst-detekt-rot`); alle Commits des Slice gehören in denselben Push.
- Der Nachweis „nur öffentliche Fläche“ gilt für Kotlin-Quellen im Source-Set; Java-Quellen und Reflection umgehen ihn (`BEO-TDS/java-quelle-umgeht-kotlin-regel`). — **Ausgang:** weiter offen (`BEO-TDS/java-quelle-umgeht-kotlin-regel`, Evidenz ergänzt; im Testserver liegen keine Java-Quellen und keine Reflection, gelesen, nicht durch einen Sensor gehalten).
- Die Doppelpflege des zweiten Source-Sets (Build, Lock, detekt, Dockerfile) erweist sich als Last; Zusammenlegen mit `integrator` wäre eine Umsetzungsentscheidung der ADR, solange Jackson den Einbettungstest nicht erreicht. — **Ausgang:** weiter offen (das zweite Source-Set ist gebaut und trägt Build, Lock, detekt und Dockerfile; die Last ist nicht gemessen; Zusammenlegen mit `integrator` ist nicht eingetreten, Jackson steht nur in den Konfigurationen des Testservers).
- Der Zuschnitt zerfällt in 2a bis 2c (§4); dazwischen ist `make e2e` rot. — **Ausgang:** entfallen (kein Teilpush; der Endstand ist in sieben Stufen grün, die Zwischenstände der Commit-Folge sind nicht gefahren).
- Der Eingangs-Hash der Gates erfasst `src/` ohnehin; eine Eingangsliste je Stage von Hand kann `src/testserver` vergessen (`BEO-TDS/eingangsliste-je-stage-von-hand`). — **Ausgang:** weiter offen (`BEO-TDS/eingangsliste-je-stage-von-hand`; `src/testserver` wird von den drei Docker-Stages der Läufe erfasst, auf Ebene des Eingangs-Hash nicht eigens geprüft).
- Die Welle startet ohne den früheren Zuschnitt „nach den produktiven Wellen“ (Nutzerentscheidung 2026-11-01): [welle-fehler-abbruch](welle-fehler-abbruch.md) und die Abbruch-Slices nach [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) ändern `Bootstrap`, den Stop-Pfad und `ServerConfig` zur selben Zeit wie die Teilung von `Bootstrap.start` und die Entfernung von `simulationFile` hier; ein Slice liest den Stand des anderen, ein Rebase bricht still. — **Ausgang:** eingetreten (gegen den Abbruchpfad-Branch Konflikte in `ConfigValidation.kt`, `ServerConfig.kt`, `ConfigValidationTest.kt`, `ConfigLoader.kt` und `spec/spezifikation.md`, Review I-6, Verifikation Folgepflicht 4); der Abbruch-Zweig zieht nach dem Umbau-Merge `main` nach, die Reihenfolge der Merges entscheidet der Nutzer.
- `ServerLog` und `ServerLog.STDOUT` bleiben in `src/main` stehen, obwohl kein Prozess sie mehr setzt, bis `slice-betrieb-log-schnittstelle` sie ablöst ([ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) Frage 1 Punkt 4); in dieser Zeit schreibt die Bibliothek im Testserver kein Log. — **Ausgang:** eingetreten, Träger `slice-betrieb-log-schnittstelle`; die Bibliothek loggt im Testserver über `ServerLog.NONE`, die `ACHTUNG`-Zeilen und die Konfiguration „im Log sichtbar“ (`SPEC-010`) fehlen bis dahin (Folgepflicht im Adapter-Slice).
- Das Jar nennt bis `slice-jar-ohne-main-und-image` weiter `Main-Class: …MainKt`, obwohl die Klasse nicht mehr in `src/main` liegt; die Manifest-Prüfung vergleicht nur die Zeichenkette. — **Ausgang:** eingetreten, Träger `slice-jar-ohne-main-und-image`; das Manifest nennt `…runtime.MainKt`, die Klasse liegt nicht im Jar.

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register (vorhandene `BEO-<KUERZEL>/<slug>` **zitieren** statt neu
formulieren — sonst zählt das Register zwei Namen getrennt) ·
`grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln (das
Feld `liegt in` steht **nur**, wenn mit diesem Slice wirklich etwas verkörpert
wurde; Feld und Zielort auf **einer** Zeile, Sektionsangabe innerhalb der
Backticks). Ging der Gegenstand an einen anderen Slice oder entfiel er, trägt
diese Sektion die Zeile `Gegenstand:` mit Kennung oder Grund und jedes Risiko
aus §6 seinen Ausgang; die Liefer-Punkte der DoD bleiben leer
(`modul-05-planning-harness.md` §Ein Slice, dessen Gegenstand ein anderer
übernimmt).

**Übergabe an `slice-betrieb-log-adapter-testserver`** (Prüfungen, die im Umbau aus den Skripten genommen und dort mit der Kennung des Slice gekennzeichnet sind; Ersatz trägt der Adapter-Slice):

- `tools/simulationstest.sh`: `tdswire: Simulations-Backend: 4 Tabellen`; `Handlerfehler` keinmal.
- `tools/kompatibilitaetstest.sh`, alle Kategorien: `Handlerfehler` keinmal.
- `tools/kompatibilitaetstest.sh`, `Anmeldung`: `tdswire: Fehler: TDS_PREPARE_LIMIT (handle-limit)` genau einmal; `TDS_UNSUPPORTED_RPC (UNKNOWN_PROCEDURE)` keinmal (Negativprüfung; ohne Log nicht beobachtbar, weil eine unbekannte Prozedur das Backend nicht erreicht).
- `tools/kompatibilitaetstest.sh`, `Luecken`: `Login: abgelehnt (UNSUPPORTED_PASSWORD_CHANGE)`, `Login: abgelehnt (UNSUPPORTED_FEDERATED_AUTHENTICATION)`, `tdswire: Fehler: TDS_PROTOCOL_ERROR`, `tdswire: Fehler: TDS_UNSUPPORTED_TYPE`.
- `tools/laufzeittest.sh`: keine Logprüfung der Bibliothek herausgenommen; entfallen sind dort die Prüfungen des Produkt-Images (Nutzer, Lizenz in `/app`, Dateiliste, `JAVA_TOOL_OPTIONS`), sie haben keinen Ersatz im Log.
- Bleibt, weil der Testserver oder der Aufzeichner die Zeile schreibt: `tdswire: gestartet, Port 1433`, `tdswire: gestoppt`, `kompatibilitaet: anfrage …`, die Prüfung auf `secret` und `Geheimnis` in der Ausgabe des Testservers.

**Gegenstand:** geliefert (`src/testserver` als eigenes Source-Set, `simulationFile` und Jackson aus der Bibliothek, Testserver-Prozess und Test-Einstiege über `TdsServer.start`, Container und Skripte am Testserver). Commits: Moves `5c8edd4`, `1a4423d`, `9a3406e` (R100), Umbau `6f24fbe`, Spezifikation `1feb21b`, Skripte `572502b`, Doku `980d1c0` und `b7995d2`, Fix-Runde `71d221d`, README `b84577d`.

**Gemessen** (Einzelmessungen am 2026-11-01 auf einem Host mit Lastmittel durch andere Agenten, Docker-Cache warm, kein Beleg für andere Hosts):

- Verifikation `b84577d`: `make gates` Exit 0, erster Lauf 13,0 s, zweiter 8,97 s, alle Stages aus dem Docker-Cache (main-Referenz 8 bis 10 s, main nicht selbst gemessen); `make e2e` Exit 0 in sieben Stufen, 202 s warm (49, 17, 28, 53, 20, 18, 17 s); Stufe 2 mit `Exit-Code 143` und Ausgabe `gestoppt`, Stufe 1 `ServerSmokeTest` 3 von 3. Der Wert 312 s in README und Änderungsverlauf der Spezifikation ist die Messung des Implementers und widerspricht nicht (andere Last).
- `make matrix-zitate` nach dem Nachziehen der Matrix: 851 bestandene Testfälle, 229 Zitate, 0 ohne PASSED (vorher rot, F-1).
- Schnitt: `gradle.lockfile` führt Jackson nur in `testserverCompileClasspath` und `testserverRuntimeClasspath`; `build/libs/lib` besteht aus `annotations`, `kotlin-stdlib`, `kotlinx-coroutines-core-jvm`; im Jar liegen weder Jackson noch `MainKt` noch eine Testserver-Klasse. `grep -c Zielzustand spec/spezifikation.md`: 15 auf main, 3 danach (Zeile 1221, `SPEC-052`, trägt `slice-jar-ohne-main-und-image`, und zwei Änderungsverlauf-Zeilen).
- Mutationen: Review 11 fremde am Schnitt rot, eine Lücke bei der Jackson-Sperre (I-2: `org.yaml.snakeyaml`, `com.google.gson`, Zeile in `*.gradle.kts`); Verifikation 10 eigene (m1 bis m10), rot bis auf m7 (alle Kennzeichnungszeilen aus den Skripten gelöscht, `skript-selbsttest` bleibt grün); m6 und m10 (Jackson in `implementation`, in `integratorRuntimeOnly`) fallen nur am Lock.
- README-Zeiten von `laufzeittest`, `simulations-integration` und `e2e` am Testserver-Image gemessen (14 s warm, 86 s nach Quelländerung laut Zeile, Kaltlauf als „nicht gemessen“ vermerkt).

**Handvektor:** keiner; keine Aussage dieses Slice hängt an einem Standard-Vektor.

**Selbstkonsistenz:** Die Bereitschaftszeile ist in `Main.kt` und im Test `TestserverStartTest` aus derselben Quelle abgeleitet; die Skript-Schleifen und `ServerProcess.port()` lesen sie, und `make e2e` fährt sie durch. `NameFoldTest` hält die Kopie der Namensfaltung im Testserver gegen `SimNames.fold`.

**Festlegung (Setzungen):** (1) Die Jackson-Sperre ist `tools/jackson-sperre.sh` vor detekt (Skript statt Importregel in `config/detekt/detekt.yml`; keine Lockerung nach `AGENTS.md` §3.6, `detekt.yml` unverändert). (2) Die Test-Clients (`IntegrationServer`, `KompatibilitaetsServer`, `LaufzeitClient`, `ServerProcess`, `ServerSmokeTest`) bleiben in `src/test` und laufen über den Klassenpfad des Testservers; `laufzeitClient` hängt an `sourceSets.test`. (3) Die Bereitschaftszeile folgt `StartOutcome.Running`, nicht dem Plan-Wort `Started`. (4) Herausgenommene Skriptprüfungen tragen `Entfallen, Ersatz in slice-betrieb-log-adapter-testserver` im Skript.

**Abweichungen der Lieferung vom DoD-Wortlaut:** (a) Clients in `src/test` statt im Source-Set `testserver` (mit [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) vereinbar, Review). (b) Jackson-Sperre als Skript, nicht als Importregel mit Begründung am Eintrag (Review I-3: vereinbar). (c) `laufzeitClient` auf `sourceSets.test`. (d) Der Beleg „`integratorTest` läuft grün ohne Jackson im Klassenpfad“ ist durch Lock und Klassenpfad belegt, nicht durch einen Test: keiner fällt, wenn Jackson im Klassenpfad von `integrator` läge (F-6); `BEO-TDS/jackson-abhaengigkeit-fuer-embedder-nicht-gemessen` ist damit am Lock beantwortet, an keinem Test.

**Nicht gemessen:** Kaltlauf (`make clean-build`, kalte Zeiten der Stufen); `make gates` auf `main` selbst; Zwischenstände der Commit-Folge; Java-Quellen und Reflection im Testserver (nur gelesen); Last der Doppelpflege des zweiten Source-Sets; der Eingangs-Hash der Stages für `src/testserver`; Terminologie „E2E-Durchgang“ der Spezifikation (F-T3, nicht angefasst); `SPEC-059` „ohne Jackson im Klassenpfad“ ohne Laufzeitprüfung.

**Ergänzungen zur Übergabe:** Die Liste der herausgenommenen Prüfungen steht oben in diesem Abschnitt (Block „Übergabe an `slice-betrieb-log-adapter-testserver`“). Ergänzt in dessen §6: die Probe `grep slice-betrieb-log-adapter-testserver tools/*.sh` als Abnahmeschritt (F-4), die leere Prüfung „weder Kennwort noch Text der Quellenausnahme“ in `simulationstest.sh` (F-3), die entfallenen Image-Prüfungen von `laufzeittest.sh` (F-5), die Grenze `alice|secret` in `laufzeittest.sh` und der Widerspruch `SPEC-010` „im Log sichtbar“. An [slice-explicit-api-umbau](../done/slice-explicit-api-umbau.md) §6: `SHUTDOWN_GRACE` und `QUERY_TIMEOUT` als „konfigurierbar“ ohne Auszeichnung als Eingang.

**Review und Verifikation:** Review `b7995d2` (H-1, M-1 bis M-4, L-1 bis L-6, behoben in `71d221d` und `b84577d`; L-2 teilweise: die Lesehilfe-Lücke steht beim Nutzer), Verifikation `b84577d`: closure-fähig mit Bedingungen (F-1 behoben durch Nachziehen der Matrix; F-2 durch diese Notiz; F-3 bis F-5 und die Folgepflichten in die Folge-Slices übergeben; F-6, F-7 informativ). Berichte: `docs/reviews/2026-11-01-b7995d2-slice-testserver-herausloesen.md`, `docs/reviews/2026-11-01-b84577d-verifikation-slice-testserver-herausloesen.md`.

**Ausgang der Risiken in §6:** in §6 je Zeile eingetragen: `SimColumnTypes`, Typ in der Fläche, Bereitschaftszeile, Zerfall: entfallen; Log-Verlust, Move-Commits, Merge gegen den Abbruchpfad, `ServerLog`, `Main-Class`: eingetreten; Java-Quelle, Doppelpflege, Eingangsliste: weiter offen (Register).

**Steering-Loop-Eintrag:** Kein Eintrag verkörpert, daher kein `liegt in`. Gelernt: (1) Eine Kennzeichnung im Skript („Entfallen, Ersatz in …“) ist eine Zusage ohne Sensor; die Verifikation löschte alle Zeilen, und kein Gate fiel (m7). Vorschlag, nicht gesetzt: ein Selbsttest, der die Kennung aus den Skripten gegen die Übergabeliste des Folge-Slice hält. (2) Das Verschieben von Tests lässt Zitate der Kompatibilitätsmatrix auf alte Namen stehen, weil `make matrix-zitate` kein Gate ist (F-1); dieselbe Klasse wie die vier Treffer von `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`, Evidenz ergänzt, über der Schwelle, Entscheidung beim Nutzer. (3) Benannte Spec-Lücken, in der Spezifikation noch offen: `SHUTDOWN_GRACE` und `QUERY_TIMEOUT` sind „konfigurierbar“, aber nicht einstellbar und nicht ausgezeichnet; `SPEC-010` „im Log sichtbar“ widerspricht dem Zustand bis zum Adapter-Slice.

**Register:** Evidenz `slice-testserver-herausloesen.md` ergänzt zu [BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen](../observations/BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen/observation.md) (F-1, Lesehilfe-Lücke, `SPEC-010`; 5×, über der Schwelle, nicht verkörpert), [BEO-TDS/aussagen-ueber-messung-hinaus](../observations/BEO-TDS/aussagen-ueber-messung-hinaus/observation.md) (M-1, L-5; verkörpert), [BEO-TDS/move-commit-laesst-detekt-rot](../observations/BEO-TDS/move-commit-laesst-detekt-rot/observation.md) (2×), [BEO-TDS/java-quelle-umgeht-kotlin-regel](../observations/BEO-TDS/java-quelle-umgeht-kotlin-regel/observation.md) (2×), [BEO-TDS/zusage-ohne-sensor-in-der-ci-kette](../observations/BEO-TDS/zusage-ohne-sensor-in-der-ci-kette/observation.md) (F-4, m7). Neu: keine, alle Treffer passten auf vorhandene Beobachtungen. Zähler nicht gesetzt.

**Folge-Slice:** [slice-betrieb-log-adapter-testserver](../next/slice-betrieb-log-adapter-testserver.md), [slice-betrieb-log-schnittstelle](../done/slice-betrieb-log-schnittstelle.md), [slice-jar-ohne-main-und-image](../done/slice-jar-ohne-main-und-image.md), [slice-explicit-api-umbau](../done/slice-explicit-api-umbau.md) (Start-Trigger mit diesem Slice in `done/` erfüllt).

**Drei Paarungen:** Das Repo führt Wellen; die Paarungen prüft die Welle-Closure. Anker ↔ Folge-Slice: kein Anker gesetzt. Register ↔ Closure-Notiz: getragen (Pfade oben). Folge-Slice: genannt.

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung — dort die **zwei vorgelagerten
Schritte** (sie stehen in jedem Slice-Plan, unabhängig von Modus und
Slice-Typ) und die **vier Pflichtkriterien** (Konventionen-Dichte ·
Phase-Reife · Evidenz-/Diskrepanz-Risiko · Reconciliation-Aufwand), vier und
nicht mehr.

**Der Abschnitt selbst entfällt nie.** Die zwei vorgelagerten Prüfungen laufen
in **jedem** Slice-Plan — sie hängen weder am Modus noch am Slice-Typ. Bedingt
ist allein der Modus-Begründungsblock am Ende; deshalb nennt der Titel beide
Hälften.

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration kennt nur `*` (Greenfield, Kürzel `TDS`). Der Slice berührt `src/main`, `src/test`, `src/integrator`, das neue `src/testserver`, `build.gradle.kts`, `Dockerfile`, `tools/` und `config/detekt/`: ein Pfad-Cluster (Achse 3) ohne eigene Strukturregel (Achse 1) oder Inventur-Linie (Achse 2) — eine von drei Achsen, unter der Schwelle. Keine feinere Sub-Area.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`). Treffer: `BEO-TDS/jackson-abhaengigkeit-fuer-embedder-nicht-gemessen` (1×, offen; Liefer-Punkt 1 misst sie), `BEO-TDS/nachweis-von-der-falschen-seite-der-grenze` (2×, offen; der Testserver liegt im nicht assoziierten Source-Set, die Mutation belegt es), `BEO-TDS/java-quelle-umgeht-kotlin-regel` (1×, offen; Grenze des Nachweises), `BEO-TDS/move-commit-laesst-detekt-rot` (1×, offen; reine Move-Commits), `BEO-TDS/eingangsliste-je-stage-von-hand` (1×, offen; neue Stages), `BEO-TDS/katalog-und-lock-ohne-gegenpruefung` (1×, offen; `make deps-lock` für das Source-Set), `BEO-TDS/zahl-an-mehreren-orten-ohne-sensor` (1×, offen; Pfad in `kopplung-zahlen`), `BEO-TDS/test-naht-im-produktivcode` (1×, offen; der Testserver nimmt keine Naht in `src/main`), `BEO-TDS/aussagen-ueber-messung-hinaus` (verkörpert, `AGENTS.md` §3.8; Aussagen zu Log und Bereitschaftszeile stehen an einem Lauf oder sind Setzung). Über der Schwelle und nicht berührt: keine. Nachgesehen am 2026-11-01 (nach [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md), [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md), Lockerung des Start-Triggers der Welle): `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` (geplant bei `slice-qa-performance`; berührt hier nicht, die Warteschlange liegt im Adapter-Slice), `BEO-TDS/dod-haengt-an-offenem-slice-ohne-rueckfuehrung` (3×, offen; die Kanten zu den Slices in `welle-betrieb-diagnose` stehen als Übergabe in §1 und §6, die Rückführung in §4), `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` (10×, offen; die Auslegung „Prüfungen herausnehmen und mit dem Träger kennzeichnen“ ist Lesart des Planners). Sonst keine Treffer.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
