# Review: slice-testserver-herausloesen (HEAD b7995d2, Merge-Basis a6ec9c8)

* Rolle: Reviewer (Modul 10), frischer Kontext; Inhalt vom Aufrufer abgelegt (verdichtet, Findings vollständig).
* Prüfgrundlage: Plan, [ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md), [ADR-0026](../plan/adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md), [ADR-0020](../plan/adr/0020-logformat-und-observability-schnittstelle.md), [ADR-0021](../plan/adr/0021-oeffentlicher-integrations-einstieg.md), [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md), [ADR-0023](../plan/adr/0023-gate-klassen-und-stempel.md), AGENTS 3.1 bis 3.8. Nicht gegen die DoD.
* Methode: Mutationen in Wegwerf-Kopie (`git archive HEAD`), `docker build --target <stage>`, ein Prozess zugleich. Nicht gefahren: `make gates`, `make e2e`, `make skript-selbsttest`, `make kopplung-zahlen`, `make detekt`, die acht Mutationen von `integrationszahlen-selbsttest`, Exit-Code 143, E2E-Stufen 2 bis 7.

## Gesamturteil

Der Schnitt ist sauber und durch Compiler und Sensor erzwungen: alle 11 fremden Mutationen am Schnitt rot, eine Lücke bei der Jackson-Sperre. Ein HIGH (abgebrochener Kommentar), vier MEDIUM, sechs LOW. Der Testserver nutzt ausschließlich die öffentliche Fläche der Bibliothek (Import-Liste: `config.*`, `domain.*`, `port.*`, `runtime.BackendSource/StartOutcome/TdsServer`, `simulation.*` nur `RowSource`, `SimCatalog`, `SimColumn`, `SimQuery`, `SimTable`, `SimulationBackend`; keine Reflection).

## Findings

* **H-1 (HIGH), Kommentar bricht mitten im Satz ab (§3.7):** `src/main/kotlin/io/github/pt9912/tdswire/runtime/Bootstrap.kt`, KDoc von `Wiring` (ca. Zeile 41): „Benutzerliste als [UserListAuthAdapter], “ endet nach dem Komma. Die Teilersetzung hat „, Backend nur aus der Datei der Konfiguration“ entfernt und den Rest stehen lassen. Kein Gate fängt das.
* **M-1 (MEDIUM), Zeitangaben belegen den neuen Gegenstand nicht (§3.8):** `harness/README.md`, `make laufzeittest` („warm 24 bis 31 s, kalt 88 bis 171 s (2026-10-31)“) und `make simulations-integration` („warm 19 bis 25 s“) stammen vom Produkt-Image (Stage `runtime`, Distroless); jetzt `FROM deps AS testserver`, Gradle-Basis. Nicht neu gemessen.
* **M-2 (MEDIUM), herausgenommene Prüfungen unvollständig gekennzeichnet (§3.6/§3.8, [ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md) „Verlust beim Log“):** `tools/kompatibilitaetstest.sh`: Der Block `Luecken` ist ein nacktes `:` („Entfallen bis slice-betrieb-log-adapter-testserver“); Grund der 18456-Ablehnung, `TDS_PROTOCOL_ERROR`, `TDS_UNSUPPORTED_TYPE` werden nirgends mehr gelesen. Die Prüfung „Prepare: keine unbekannte Prozedur (sp_unprepare, sp_prepare) erreicht den Server“ wurde ersatzlos und ohne Kennzeichnung gestrichen (sie war eine Negativprüfung, jetzt leer wahr). Die Planung sagte: jede herausgenommene Prüfung trägt im Skript die Kennung des Ersatz-Slice; gilt hier nur für den 1000er-Block. Die Übergabe an den Log-Adapter-Slice ist nur über Skript-Kommentare, Slice-Texte und Plan-Aufzählung verankert (Closure-Notiz noch nicht geschrieben). Eine verdeckte Lockerung liegt nicht vor (E2E ist kein Gate, [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md); [ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md) benennt den Verlust), offen ist die Ehrlichkeit der Liste.
* **M-3 (MEDIUM), stille Verhaltensänderung ohne Test (§3.8):** `SimulationSetup.kt`: `if (tables.isEmpty()) return SimulationResult.Absent` zusammen mit `DescriptionFile.kt` im Testserver. Alt: Datei mit null Tabellen ergab ein Backend mit leerem Katalog; neu: kein Backend, der Server schließt bei einer Anfrage ohne Bytes die Verbindung. Kein Test für `tables: {}`; in keinem Dokument vermerkt.
* **M-4 (MEDIUM), Fangklausel verbreitert:** `SimulationSetup.kt`: `catch (ignored: IllegalArgumentException)` um `SimulationBackend(SimCatalog(all))` fängt jede `IllegalArgumentException` und meldet „ein Name kommt doppelt vor“; vorher umschloss die Klausel bewusst nur den Katalog (`DuplicateNameException`). Eine andere ungültige Tabellenform wird mit falscher Diagnose als öffentlicher Text (`Aborted.reason`) gemeldet.
* **L-1 (LOW), Fehlerbericht zweistufig:** `src/testserver/.../ConfigLoader.kt` (`rangeIssues`/`Invalid`): Treten Formfehler und Bereichsfehler gleichzeitig auf, meldet der Testserver nur die Formfehler; kein Test für den gemischten Fall; die Spec verlangt „alle Fehler in einem Durchlauf“ nicht.
* **L-2 (LOW), Lesehilfe-Lücke der Spec:** Festlegungstabelle `LOGIN_TIMEOUT`, `MAX_PRELOGIN_MESSAGE_BYTES`, `SHUTDOWN_GRACE`, `QUERY_TIMEOUT` „konfigurierbar“. Der Testserver liest `LOGIN_TIMEOUT` und `MAX_PRELOGIN_MESSAGE_BYTES` aus der Umgebung (`EnvKeys`), das steht nirgends bei der Zeile; `SHUTDOWN_GRACE` und `QUERY_TIMEOUT` sind über `TdsServer.start` nicht einstellbar (`ListenerConfig`/`ListenerTimes` sind `internal`).
* **L-3 (LOW), doppelte Regel ohne Gleichheitstest:** `NameFold.kt` dupliziert `SimNames.fold` (`internal`); Test belegt einen Fall (`PERSON` gegen `person`); ebenso Obergrenze 38 (`SimColumnTypes.MAX_PRECISION` gegen `SimModel.MAX_DECIMAL_PRECISION`).
* **L-4 (LOW), Kommentare (§3.7):** `tools/jackson-sperre.sh`: „Eine neue Sperre ist keine Lockerung (AGENTS.md 3.6). Darum steht die Pruefung vor detekt …“ (Nicht-Folgesatz) und „Warum kein detekt-Eintrag: …“ (verworfene Alternative).
* **L-5 (LOW), Aussage vor dem Lauf:** `spec/spezifikation.md`, Änderungsverlauf-Zeile `2026-11-01 | Testserver gebaut: …` behauptet „die Läufe des E2E-Durchgangs gehen gegen den Testserver“; im Review nur für Stufe 1 und die Schnitt-Mutationen gemessen (der Implementer fuhr `make e2e` mit 7 Stufen grün, das ist als Beleg zu nennen).
* **L-6 (LOW), Main-Class zeigt auf nicht mehr vorhandene Klasse:** `build.gradle.kts` `Main-Class … runtime.MainKt`, `tools/manifest-pruefung.sh` vergleicht nur die Zeichenkette; im Plan ausdrücklich nach `slice-jar-ohne-main-und-image` abgegrenzt.
* **I-1:** Jackson-Rückfall nach `implementation` wird vom Gradle-Lock erkannt (M12: „Resolved 'org.yaml:snakeyaml:2.5' which is not part of the dependency lock state“), solange niemand `make deps-lock` neu schreibt; dann zeigt der Diff der `gradle.lockfile` Jackson in `compileClasspath`. „Nur Absicht“ ist zu schwach formuliert.
* **I-2:** Lücken der Jackson-Sperre: Alias-Import wird gefunden; nicht gefunden: `org.yaml.snakeyaml`, `com.google.gson`, eine Dependency-Zeile in `*.gradle.kts`, `Class.forName("com.faster"+"xml…")`. Der Skript-Kopf nennt nur den letzten Fall. Nach [ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md) nicht verlangt.
* **I-3:** Jackson-Sperre als Skript statt Importregel ist mit [ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md) vereinbar (Begründung belegt: Importregeln gelten je Package der Schichten, `runtime` hat keine); der Planwortlaut „Importregel“ weicht ab (dokumentiert in §3).
* **I-4:** `src/testserver/test/kotlin` im selben Source-Set; keine Fehlmessung.
* **I-5:** Move-Commits `5c8edd4`, `1a4423d`, `9a3406e` sind R100; der Zwischenstand ist rot. Reihenfolge Move vor Inhalt ist nach AGENTS 3.3 Pflicht; Umstellung brächte die roten Zwischenstände nicht weg. Vertretbar, wenn alle Commits als eine Einheit landen.
* **I-6 (Merge):** Gegen `main`: Konflikte in `docs/plan/planning/in-progress/roadmap.md` und `harness/README.md` (Dokumente). Gegen den Abbruchpfad-Branch: echte Konflikte in `config/ConfigValidation.kt`, `config/ServerConfig.kt`, `src/test/.../ConfigValidationTest.kt`, `src/testserver/.../ConfigLoader.kt`, `spec/spezifikation.md`, zwei Slice-Dateien; konfliktfrei `Bootstrap.kt`, `ConfigLoaderTest.kt`, `Dockerfile`. Der Abbruch-Zweig muss nach dem Umbau-Merge von `main` nachziehen.
* **I-7:** Entfernt (beabsichtigt): `ServerConfig.simulationFile`, `ConfigKeys.SIMULATION_DESCRIPTION_FILE`, `MAX_SIMULATION_FILE_BYTES`, `ConfigValidation.simulationFile`.
* **I-8:** Neue Stage `testserver` baut auf `deps` (gleiche Digests, kein Mount, `docker cp`); `test` und `spec-abdeckung` führen `testserverTest`. Gate-Zeit nicht gemessen.

## Schwerpunkte (Kurzantworten)

* **ACHTUNG-Zeilen:** `TEST_UNENCRYPTED` und `AUTH_PLAIN_TESTMODE` schreibt der Testserver nicht mehr (Einstieg schreibt kein Log, Bibliothek loggt über `ServerLog.NONE`). `SPEC-010` verlangt „im Start-Log sichtbar“: offener Widerspruch zu [ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md) Entscheidung 7, schließt mit `slice-betrieb-log-adapter-testserver`; als Befund an den Nutzer.
* **Bereitschaftszeile/Hook/Exit-Code:** `tdswire: gestartet, Port <port>` (Quelle `Main.kt`, `outcome.server.port`), Hook `tdswire: gestoppt`, Exit-Code 1 bei Abbruch; 143 stammt aus SIGTERM der JVM und wird nur im Container-Lauf gemessen.
* **Abweichung „Clients bleiben in `src/test`“:** mit [ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md) vereinbar (die ADR verlangt, dass der Testserver die Bibliothek nur öffentlich bindet, nicht, dass seine Clients dort liegen); mit der Einordnung des Nutzers (Integrationstests) vertretbar.

## Terminologie „E2E“ gegen Integrationstest

* [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md) Entscheidung 1 definiert den „E2E-Lauf“ als Lauf über die Außengrenze des Serverprozesses (Referenzclient oder Rohbytes-Client); nach dieser Definition sind Stufen 1 und 2 E2E. Die strenge Definition des Nutzers (unabhängiger Beleg) trägt der Repo-Begriff nicht.
* **F-T1 (MEDIUM, §3.8):** Stufe 1 steht in `tools/e2e.sh` und `docs/plan/e2e-abdeckung.md` mit „Rohbytes-Client: PRELOGIN und LOGIN7 mit LOGINACK“ als E2E-Stufe; die LOGINACK-Dekodierung nutzt `TokenDecoder`/`LoginAck` der Bibliothek (selbstkonsistent), nicht unabhängig. Dasselbe für Stufe 2 (`LaufzeitClient`). Der Text sagt das nicht.
* **F-T2 (LOW):** `smoke` ist zugleich Stufe 1 von `make e2e` und läuft in `make test` (Gate); „E2E kein Gate“ gilt für Stufe 1 nicht uneingeschränkt.
* **F-T3 (LOW):** Spec nennt `SPEC-025` „Integrationstest (E2E-Durchgang)“, `SPEC-052`/`SPEC-040`/`SPEC-032` „E2E-Durchgang“; `SPEC-040` nennt „Integrationstest mit dem .NET-Referenzclient“, obwohl das nach strenger Definition echtes E2E ist.

## Bestandsaufnahme der 7 Stufen von `tools/e2e.sh`

| Stufe | Target | Client | Dekodierung | Einordnung |
|---|---|---|---|---|
| 1 | `make smoke` | Kotlin-Rohbytes-Client, Kind-JVM mit Testserver-`main` | Eingabe Handvektor; Antwort `TokenDecoder`/`LoginAck` (selbstkonsistent), PRELOGIN von Hand | Integrationstest mit Prozessgrenze |
| 2 | `make laufzeittest` | `LaufzeitClient` (Kotlin) im 2. Container | wie Stufe 1 | wie Stufe 1; zusätzlich Exit-Code 143 |
| 3 | `make simulations-integration` | .NET `Microsoft.Data.SqlClient` | Referenzclient | echtes E2E (TDS 7.4 unverschlüsselt, ohne Pool, ohne Cancel) |
| 4 | `kompatibilitaet KATEGORIE=Szenario` | .NET-Referenzclient | Referenzclient | echtes E2E (TDS 7.4 mit TLS) |
| 5 | `…Anmeldung` | .NET-Referenzclient | Referenzclient | echtes E2E (TDS 8.0, Prepare/Pooling) |
| 6 | `…Luecken` | .NET-Referenzclient | Referenzclient | echtes E2E; Grund der Ablehnung nur im Log, das fehlt jetzt (M-2) |
| 7 | `…Synchronitaet` | .NET-Referenzclient | Referenzclient | echtes E2E (ohne Cancel) |

Lücken für echtes E2E: Cancel/Timeout (kein Lauf, CO-004); RESETCONNECTION/Pooling (nur „zweite Nutzung derselben Sitzung laut Log“, das RESETCONNECTION-Bit ist „nicht belegt“); TDS 8.0 nur Encrypt=Strict mit Referenzclient; Rohbytes-Pfade 1/2 nur eine Eingabevariante und keine unabhängige Dekodierung der Antwort. Empfehlung: Stufen 1/2 als „Rohbytes-Integration über die Prozessgrenze“ kennzeichnen, LOGINACK-Antwort optional per Handvektor aus Quelle belegen (Folge-Slice).

## API-Abdeckung (Lücken zuerst)

| Element | Stand | Bemerkung |
|---|---|---|
| `BackendSource.None` | nur Test | im Testserver-Code ungenutzt |
| `AuthAdapter`-Familie (`AuthFeatures`, `AuthRequest`, `AuthResult`, `RejectReason`, `NegotiatedEncryption`, `ConnectionContext`) | nur Unit/ungeprüft | `TdsServer.start` hat keinen Parameter für einen eigenen Adapter; nur `UserListAuthAdapter` im Testserver |
| Einstellungen `SHUTDOWN_GRACE`, `QUERY_TIMEOUT`, Cancel-Fristen | ungeprüft | über die öffentliche Fläche nicht einstellbar (L-2) |
| Diagnose/Log-Senke | ungeprüft | nicht vorhanden (`ServerLog.NONE`) |
| `SimQuery`, `RowFilter`/`FilterTerm` | nur Unit | vom Backend intern |
| `Principal`, `PasswordChars` | nur Unit | nicht im Prozessweg belegt |
| `QueryEvent`, `QueryExecution`, `QueryParameter`/`ParameterDirection` | teilweise | `RecordingBackend`; Richtungen OUTPUT/INPUT_OUTPUT nur als Abweisung (Stufen 4 und 5) |
| `ConfigValidation.check` | Unit | Bereichsfehler nur Unit |

Abgedeckt (Testserver-Code und Prozess-Beleg): `TdsServer.start`, `StartOutcome.Running` (Port, `stop()`), `StartOutcome.Aborted` (`reason`, `issues`), `BackendSource.Simulation`/`Custom`, `SimulationBackend`, `SimCatalog`, `SimTable`, `SimColumn`, `SqlType`, `RowSource`, `ServerConfig` samt `ListenSettings`/`LoginSettings`/`AuthSettings`/`EncryptionSetting`/`TlsKeyMaterial`/`UserList.parse`/`ConfigIssue`/`IssueKind`.

## Mutationen (gefahren)

M1 Bereitschaftszeile „gestartet, Port“ zu „gestartet Port“: rot (`ServerSmokeTest`). M2 Hook ohne `println(… gestoppt)`: rot. M3 `EXIT_START_FAILED` 1 zu 2: rot (2 Tests). M4 Port-Ausgabe fest `1433`: rot (`TestserverStartTest`, 2 Tests). M5 `EnvKeys.LISTEN_PORT` umbenannt: rot. M6 `readNBytes(limit + 1)` zu `limit`: rot. M7 `DIGITS {1,9}` zu `{1,12}`: rot. M8 BOM-Zweig `false -> null`: rot. M9 `TdsServer.start(loaded.config, source)` mit `BackendSource.None`: rot. M10 `associateWith(main)` für `testserver`: rot (Sensor „Source-Set testserver ist assoziiert mit [main]“). M11 `Wiring()` im Testserver: rot (Compiler „it is internal“). M12 `implementation(libs.jackson.dataformat.yaml)`: rot in `deps` (Lock). M13 Jackson-Sperre Alias-Import: rot. M13b `snakeyaml`/`gson`-Import, Gradle-Dependency-Zeile, zusammengesetzter `Class.forName`: **nicht erkannt**. Nicht mutiert: `boot.server.stop()` im Hook.
