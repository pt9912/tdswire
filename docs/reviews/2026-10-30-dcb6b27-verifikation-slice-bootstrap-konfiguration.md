# Verifikation slice-bootstrap-konfiguration (DoD- und Entscheidungs-Konformität)

**Rolle:** Verifier (Modul 11) · **Stand:** HEAD `dcb6b27`, main (enthält Umsetzung `d5b0270` und Fix-Runde `61141fd`) · **Datum:** 2026-10-30
**Gegenstand:** [slice-bootstrap-konfiguration](../plan/planning/in-progress/slice-bootstrap-konfiguration.md), Welle welle-handshake-login
**Bezug:** [TDS-FA-09](../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-FA-01](../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung), [TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit), [ADR-0002](../plan/adr/0002-tls-und-verschluesselung.md), [ADR-0003](../plan/adr/0003-transportframework.md), [ADR-0009](../plan/adr/0009-authentifizierungsmodus.md), [ADR-0011](../plan/adr/0011-pruefwertformat.md)
**Vorgänger:** Review [2026-10-30-d5b0270-slice-bootstrap-konfiguration](2026-10-30-d5b0270-slice-bootstrap-konfiguration.md) (F-1 bis F-9)
**Adressat:** Planner. Der Verifier repariert nichts und entscheidet den Schnitt nicht. Nichts committet; geändert ist nur diese Datei.

## 1. Urteil

**Die drei Liefer-Punkte sind im Code bestätigt, die Fix-Runde hält der Nachprüfung stand. Der Slice ist noch nicht schließbar:** die Closure-Pflichten (Häkchen, Closure-Notiz §7, Ausgang je Risiko aus §6, Register, Steering-Loop-Lerneintrag) fehlen, und zwei Punkte sind ehrlich offen und brauchen eine Nutzerentscheidung oder einen Träger (Abschnitt 5).

- Alle neun Mutationen (M07, M08, M09, M11, M14, M19, M21, X1, F-8) wurden von mir selbst einzeln gefahren und sind rot; X1 ist jetzt von genau einem eigenen Test rot (Abschnitt 4).
- Von den Befunden des Reviews sind F-1, F-2, F-3, F-4, F-8 behoben, F-9 zur Hälfte (Abschnitt 3). F-5, F-6, F-7 sind nicht angefasst (LOW/INFO, bewusst), aber auch nicht mit Begründung angenommen; das gehört in die Closure.
- Verhalten ohne Backend (nach [slice-sql-batch-dispatch](../plan/planning/in-progress/slice-sql-batch-dispatch.md)) ist unverändert (Abschnitt 6).
- Kein Flake beobachtet (Abschnitt 7); die Ursache des einen roten Laufs der Fix-Runde bleibt ungeklärt.

## 2. DoD Punkt für Punkt

| # | DoD-Punkt | Urteil | Beleg |
|---|---|---|---|
| 1 | **Konfiguration** laden, Defaults, validieren, Abschwächung nur ausdrücklich und im Log, Diagnose ohne Geheimnis, Tests Happy/Boundary/Negative | **bestätigt** (Einschränkung: Dateirechte/Übergabe, siehe 5) | Defaults im Lader: Port 1433, `LOGIN_TIMEOUT` 15, `MAX_PRELOGIN_MESSAGE_BYTES` 65536, `ENCRYPTION_POLICY` `REQUIRED`, Host `0.0.0.0`; Bereiche 0..65535, 1..3600, 512..65536 stimmen mit der Tabelle in Abschnitt 3 der Spezifikation ([SPEC-005](../../spec/spezifikation.md#3-defaults-und-konstanten), [SPEC-008](../../spec/spezifikation.md#3-defaults-und-konstanten), [SPEC-010](../../spec/spezifikation.md#3-defaults-und-konstanten), [SPEC-046](../../spec/spezifikation.md#3-defaults-und-konstanten)) überein. Start-Log nennt beide Abschwächungen mit ACHTUNG (`Bootstrap.running`). Textdarstellungen von ServerConfig, AuthSettings, TlsKeyMaterial lauten "verborgen". BOM wird abgewiesen, Zeilenenden nicht normalisiert ([SPEC-051](../../spec/spezifikation.md#6-externe-verträge), Abschnitt 6 der Spezifikation; Festlegung gekennzeichnet). Schlüssel `AUTH_USERS_FILE`, `AUTH_PLAIN_TESTMODE` (nur genau `true`, `TRUE` ist Fehler) stimmen mit [ADR-0011](../plan/adr/0011-pruefwertformat.md) überein. Tests: `ConfigLoaderTest` und `ConfigUsersFileTest` laufen im Lauf unten grün. |
| 2 | **Composition Root, `main`, Start, Stop**; Flag wird geprüft, nicht gesetzt; Test belegt Prüfung; Make-Target in `harness/README.md` | **bestätigt** | Plan ist an den Ist-Zustand gebunden (Abschnitt 3, F-1). `Bootstrap.start` prüft das Flag zuerst, vor Laden und TLS; Mutation M07 rot mit `BootstrapTest` und `ServerSmokeTest`. Stop: Mutation M21 rot. Zeile `make smoke` steht in `harness/README.md` §Sensors; `make help` führt das Target; `make docs-check` grün. |
| 3 | **Rohbytes-Smoke**: PRELOGIN, LOGIN7, Ablehnungen byte-gleich, SSPI/FeatureExt `0x02`/Passwortwechsel, mehrpaketig byteweise, Stop; Teil von `make gates` | **bestätigt** (Einschränkung: Rohbytes-Client, kein Referenzclient) | `ServerSmokeTest` (3 Tests) deckt die Aufzählung im Test `Serverprozess beantwortet den Login-Pfad mit Rohbytes` (SSPI, FeatureExt `0x02`, Passwortwechsel, 20-Byte-Pakete byteweise, SIGTERM), den Start ohne Flag und den Start mit Fehlern/belegtem Port (Exit-Code 1). Der Test läuft in der Stage `test` und damit in `make gates` (`GATE_CHECKS += test`); `make smoke` ist zusätzlich der engste Sensor. Aussage "Login funktioniert gegen den Referenzclient" steht nirgends. |
| N1 | Jeder Test trägt `@spec SPEC-<NNN>` im KDoc; Tests grün | **bestätigt** | Skript über alle acht Testdateien des Slice (`runtime/*Test.kt`, `config/*Test.kt`, `SwitchableChannelTest`): kein `@Test` ohne `@spec SPEC-` im vorangehenden KDoc. Stage `test` frisch: 500 PASSED, 0 FAILED. |
| N2 | Jede Aussage an Test oder als Festlegung gekennzeichnet; Zuordnung in der Welle-Closure | **teilweise bestätigt** | Im Code und in der Spezifikation sind Wertebereiche, BOM, `shutdownGrace` 0 als Festlegung gekennzeichnet. Die Welle-Closure-Zuordnung steht aus (Welle nicht geschlossen). |
| N3 | `make gates` grün | **teilweise von mir gefahren** | Einzeln und frisch (ohne Cache für die Stage): `make detekt` Exit 0, Stage `test` Exit 0 (500 PASSED). `make docs-check` siehe Abschnitt 8. Ein vollständiger `make gates` ist nicht gelaufen (Last, kein zweiter Docker-Build gleichzeitig); die übrigen Gates (`build`, `detekt-selbsttest`, `referenztest-dotnet`, `baseline-verify`) sind nicht von mir bestätigt. |
| N4 | Review-Report liegt vor | **bestätigt** | [d5b0270-Review](2026-10-30-d5b0270-slice-bootstrap-konfiguration.md); diese Datei ist die Verifikation, kein Ersatz. |
| N5 | Doku-Update (öffentlicher Vertrag) | **bestätigt** | `spec/spezifikation.md` (Tabelle Abschnitt 3, Sicherheitsprofil, Authentifizierungsadapter) und `harness/README.md` im Merge `d5b0270`; Spezifikation nennt keine ADR-Pfade. |
| N6 | Closure-Notiz mit Steering-Loop-Lerneintrag | **nicht erfüllt** | §7: "Wird bei der Closure gefüllt." |
| N7 | Beobachtungs-Register fortgeschrieben | **nicht erfüllt** | Kein Eintrag zu diesem Slice im Diff von `d5b0270` oder `61141fd`. |
| N8 | Jedes Risiko aus §6 trägt einen Ausgang | **nicht erfüllt** | Alle Risiken stehen auf "offen bis zur Closure" bzw. "weiter offen". Vorschlag je Risiko in Abschnitt 5. |
| N9 | Drei Paarungen (Anker, Folge-Slice, Register) | **nicht prüfbar vor Closure**; Folge-Slice-Paarung des Flags siehe 5 | |

Die Häkchen der drei Liefer-Punkte in §2 des Plans sind alle noch leer; das ist vor der Closure erwartbar, aber kein Beleg.

## 3. Befunde des Reviews: behoben oder ehrlich offen?

| ID | Urteil | Prüfung |
|---|---|---|
| [F-1](2026-10-30-d5b0270-slice-bootstrap-konfiguration.md) Plan sagt "setzt", Code prüft nur | **behoben** | DoD-Punkt 2 sagt jetzt "prüft ... und setzt es nicht selbst", nennt "Wert der Systemeigenschaft, nicht die Wirkung auf dem Draht" und, dass die Kind-JVM des Tests das Flag setzt; die Zeile der Dateiliste in §3 ist angepasst; ein eigenes Risiko in §6 führt "Kein Startartefakt setzt das Flag" mit Ausgang "weiter offen". Passt zu `Main.kt`, `Bootstrap.kt` und der Spezifikation (Abschnitt "JVM-Flag des Serverprozesses"). **Ehrlich offen bleibt der Betrieb:** siehe 5 (Träger ohne Zusage). |
| [F-2](2026-10-30-d5b0270-slice-bootstrap-konfiguration.md) KDoc von `ServerLog` breiter als der Beleg | **behoben** | KDoc sagt jetzt: nur gebundener Port und bei Bindefehler Adresse und Port ([SPEC-043](../../spec/spezifikation.md#spec-043--startprüfung-tls-konfiguration-und-port)); nie Pfad, Benutzername, Prüfwert, Passwort. Gegengeprüft an den übrigen Zeilen: Handler- und Annahmefehler tragen den Typnamen, TLS-Fehler Teil und Form. Der Test zum Bindefehler (`Port belegt: 127.0.0.1:<port>`) deckt die genannte Ausnahme. |
| [F-3](2026-10-30-d5b0270-slice-bootstrap-konfiguration.md) Mutation X1 überlebt | **behoben** | Neuer Test `Die Frist des Treibers trägt den konfigurierten Wert` (`HandshakeWiringTest`, `@spec SPEC-008`): `LOGIN_TIMEOUT=1`, `ManualDelayer` des Listeners wird nie freigegeben (nur `pending == [1 s]` geprüft), der Treiber beendet binnen `eventually` (5 s) als `HANDLER_RETURNED`, nicht als `LOGIN_TIMEOUT` des Listeners. X1 selbst gefahren: Abschnitt 4. |
| [F-4](2026-10-30-d5b0270-slice-bootstrap-konfiguration.md) `shutdownGrace` 0 ohne Kennzeichnung | **behoben** | KDoc von `Bootstrap`: "Festlegung, nicht gemessen und nicht die Voreinstellung `SHUTDOWN_GRACE` von 10 s ([SPEC-047](../../spec/spezifikation.md#3-defaults-und-konstanten)); die Frist ... trägt slice-abbruch-ressourcen-shutdown". Die Zeile mit `Duration.ZERO` trägt selbst keinen Kommentar; die Zusage steht im Kopf-KDoc des Objekts (ausreichend). |
| [F-5](2026-10-30-d5b0270-slice-bootstrap-konfiguration.md) Smoke läuft doppelt | **offen, bewusst** | Unverändert (Dockerfile-Kommentar und `smoke.mk` benennen es). In der Closure als angenommen vermerken. |
| [F-6](2026-10-30-d5b0270-slice-bootstrap-konfiguration.md) Herkunftsstufen | **kein Handlungsbedarf (INFO)** | Bestätigt: Der Smoke ist Rohbytes-Client auf dem Klassenpfad der Test-JVM; TLS-Pfad nur in `TlsWiringTest`. |
| [F-7](2026-10-30-d5b0270-slice-bootstrap-konfiguration.md) Keystore-Kennwort als String/ByteArray im Heap | **offen, bewusst (INFO)** | Unverändert (`ConfigLoader.passwordOf`); die Spezifikation sagt dazu nichts. Gehört als Grenze in die Closure, nicht als stille Zusage. |
| [F-8](2026-10-30-d5b0270-slice-bootstrap-konfiguration.md) `ConfigIssue.detail` freier String | **behoben** | `detail` hat den Typ `UserListError` (Aufzählung plus Zeilennummer); `message` setzt daraus Art und Zeile zusammen. Der Test vergleicht jetzt Typwerte und die fertige Meldung. F-8-Mutation (freier Text mit Passwort) lässt sich nicht mehr übersetzen: Abschnitt 4. |
| [F-9](2026-10-30-d5b0270-slice-bootstrap-konfiguration.md) `ServerProcess.close` ohne `waitFor` | **zur Hälfte behoben** | `close` wartet nach `destroyForcibly` bis `WAIT_SECONDS` mit `waitFor` (Code gelesen, kompiliert, Tests grün). Der zweite Teil des Befunds (offener `RawClient` im Fehlerpfad von `Serverprozess beantwortet den Login-Pfad mit Rohbytes`) ist nicht angefasst; der Commit meldet F-9 als behoben. INFO, ohne Wirkung beobachtet. |

## 4. Selbst gefahrene Mutationen

Jede einzeln, auf einer Kopie von HEAD im Scratchpad (`/tmp/claude-1000/-Development-tdswire/8562f454-84ba-456b-9970-945e69c035f4/scratchpad/v8`), je ein Docker-Build der Stage `smoke` mit erweitertem Test-Filter (alle Tests der Pakete `runtime` und `config` plus `SwitchableChannelTest`). Nichts gemountet, das Repo selbst nicht verändert. Ausgangslauf ohne Mutation: Exit 0.

| Mutation | Änderung | Ergebnis | Rote Tests |
|---|---|---|---|
| X1 | `ConnectionWiring`: Frist des Treibers `3600.seconds` statt konfiguriertem Wert | **rot** | genau einer: `HandshakeWiringTest > Die Frist des Treibers trägt den konfigurierten Wert` |
| M07 | `Bootstrap`: Flag-Prüfung entfernt | **rot** | `BootstrapTest > Ohne das JVM-Flag startet der Server nicht`; `ServerSmokeTest > Ein Prozess ohne das JVM-Flag startet nicht` |
| M08 | Treiber baut `AuthPool()` statt des gemeinsamen Pools | **rot** | `HandshakeWiringTest > Zwei Verbindungen teilen sich einen AuthPool` |
| M09 | `ListenerTimes` mit `3600.seconds` statt `LOGIN_TIMEOUT` | **rot** | `LOGIN_TIMEOUT beginnt mit der Annahme und schliesst ohne Antwort`; zusätzlich der neue Treiber-Test (seine Vorbedingung `pending == [1 s]` bricht) |
| M11 | `SwitchableChannel.switchTo` ohne Wirkung | **rot** | `SwitchableChannelTest > Nach dem Wechsel gehen alle Vorgaenge an das neue Ziel`; `TlsWiringTest > REQUIRED fuehrt den TLS-Handschlag aus ...` |
| M14 | `AuthExecutor`: jede `CancellationException` wird weitergeworfen, nicht nur bei abgebrochenem Aufruf | **rot** | `HandshakeWiringTest > Eine Abbruchausnahme des Adapters ist ein Adapterfehler und kein Handlerfehler` |
| M19 | Startprobe im `TdsListener` ohne Wirkung (Fehlschlag wird nicht ausgewertet) | **rot** | `BootstrapTest > Eine fehlgeschlagene Startprobe bricht den Start vor dem Binden ab` |
| M21 | `Server.stop` ohne `listener.shutdown()` | **rot** | `BootstrapTest > Stop gibt Port und Threads frei`; `HandshakeWiringTest > Stop im Handshake ...`; `Stop trennt eine angemeldete Verbindung` |
| F-8 | `ConfigIssue` mit freiem Text statt `UserListError` (Passwort in die Diagnose) | **rot, schon beim Übersetzen** | Kompilierfehler: der Typ schließt den freien Text aus; stärker als ein roter Test |

Anmerkungen:
- M19: Meine erste Fassung (Zweig `Rejected` in `Bootstrap`) betrifft das Laden des Speichers (falsches Kennwort), nicht die Startprobe; dort war nur `Ein falsches Kennwort des Speichers bricht den Start ab` rot. Die Startprobe selbst liegt im `TdsListener`; die zweite Fassung ist oben aufgeführt. Beide Fassungen rot.
- M14 trifft Protokollcode (`AuthExecutor`), nicht den Bootstrap; der Wiring-Test ist der Beleg für die Annahme aus §6 des Plans ("Abbruchausnahme am echten Handler"): Handler endet als `HANDLER_RETURNED` ohne Handlerfehler, Log zeigt `Login: Adapterfehler`.

## 5. Ehrlich offen (kein Fehler der Umsetzung, aber nicht schließbar ohne Entscheidung)

1. **Dateirechte und Übergabe der Benutzerliste im Betrieb.** Die DoD verlangt, dass der Nutzer entscheidet "oder die Closure führt sie als weiter offen mit Grund". Beides steht aus. Spezifikation und Lader sagen ehrlich "nicht geprüft". Empfehlung: Ausgang "weiter offen" mit Grund (keine Nutzerentscheidung, nicht gemessen) in §6 und Register.
2. **Flag im Betrieb.** Das Risiko in §6 nennt [slice-release-artefakt-lizenz](../plan/planning/open/slice-release-artefakt-lizenz.md) als Träger und sagt selbst, dass dessen DoD und Risiken das Flag noch nicht führen (Suche nach "flag" und "newSessionTicket" in der Datei: kein Treffer). Die Paarung "Folge-Slice trägt das Risiko" ist damit **nicht getragen**. Offen beim Planner: Ergänzung im Träger-Slice, bevor dieser Slice nach `done/` geht. [SPEC-023](../../spec/spezifikation.md#sicherheitsprofil-vertrag-zu-spec-023) sagt weiter "beim Start des Serverprozesses zu setzen"; belegt ist nur die Prüfung.
3. **Vorschläge für die übrigen Ausgänge in §6** (Entscheidung beim Planner, ich habe nichts geändert): Konfigurationsweg/Format: eingetreten/entschieden ([ADR-0011](../plan/adr/0011-pruefwertformat.md)). Überschneidung mit der Transport-Welle: entfallen (Modell und Start liegen hier, Prüfung statt Setzen). Abbruchausnahme am echten Handler: belegt durch den Test unter M14 (Setzung bleibt vom Nutzer unbestätigt). Rohbytes statt Referenzclient: weiter offen (Einschränkung steht, Vergleichslauf liegt bei [slice-qa-kompatibilitaet](../plan/planning/open/slice-qa-kompatibilitaet.md)). Smoke in Docker: entschieden (Stage `smoke`, Kind-JVM über Loopback). Prüfwerte in der Konfiguration: Textdarstellung belegt, Dateirechte offen (Punkt 1). Defaults vorläufig: Festlegung, weiter offen. `AuthPool` je Prozess: belegt durch Test (M08); die Platzzahl bleibt ungemessen, Träger `slice-qa-sicherheit`. Größe: eingetreten/entfallen (drei Liefer-Punkte, keine Rückführung).

## 6. Plan-vs-Code-Diff und Out-of-Scope

| Plan (§3 Dateiliste und §1 Abgrenzung) | Ist-Zustand | Urteil |
|---|---|---|
| `config/` update: `ServerConfig`, `ConfigLoader`, `ConfigFiles` | vorhanden; `config` importiert nur `config.auth`, `EncryptionSetting` statt `EncryptionPolicy` | wie geplant |
| `runtime/` neu: `main`, Composition Root, Start/Stop | `Main.kt`, `Bootstrap.kt`, `Server.kt`, `ConnectionWiring.kt`, `ServerLog.kt` | wie geplant |
| `SwitchableChannel` neu | vorhanden, im Handler verdrahtet | wie geplant (Ergänzung im ersten Lauf, im Plan geführt) |
| Dockerfile Stage `smoke`, `tools/mk/smoke.mk` | vorhanden; kein Eintrag in `GATE_CHECKS` (Test läuft in Stage `test`) | wie geplant, F-5 bewusst |
| `harness/README.md` Zeile | vorhanden | wie geplant |
| `spec/spezifikation.md` (Tabelle, Sicherheitsprofil, SPEC-051) | angepasst | wie geplant |
| Abhängigkeiten, `gradle.lockfile`, `verification-metadata.xml` | in `d5b0270` und `61141fd` unverändert | wie geplant (keine neue Abhängigkeit; [ADR-0003](../plan/adr/0003-transportframework.md) hält) |
| Flag setzen und prüfen | nur prüfen; Plan nachgezogen | Abweichung geheilt (F-1) |

**Out-of-Scope** (Listener-Interna, Shutdown-Frist, Observability, Backend, Referenzclient/TLS-Smoke, Neu laden): eingehalten. Kein Package `observability`, keine Frist-Logik im Stop, keine Neu-Laden-Schleife. Der Smoke läuft nur im Modus `TEST_UNENCRYPTED`; der TLS-Pfad ist nur in `TlsWiringTest` belegt (nicht im `main`-Prozess).

**Backend aus [slice-sql-batch-dispatch](../plan/planning/in-progress/slice-sql-batch-dispatch.md):** `Wiring.backend` ist `QueryBackend?` mit Voreinstellung `null`; `Main.kt` baut `Wiring(log = ServerLog.STDOUT)` ohne Backend. Mit `backend == null` verhält sich der Handler wie im Plan beschrieben: nach dem Login `loginCompleted()`, dann `channel.receive()`, die erste Nachricht beendet die Verbindung als `HANDLER_RETURNED`. Beleg: `HandshakeWiringTest > Nach dem Login gilt LOGIN_TIMEOUT nicht mehr` läuft ohne Backend und ist grün. Die Anfrageschleife ist nur mit gesetztem Backend erreichbar und nur über `RequestWiringTest` (Test-Backend) verdrahtet; der Produktstart verdrahtet kein Simulations-Backend. Die KDoc des Handlers nennt beide Zweige. Sachlich offen für die Folgearbeit (nicht dieser Slice): ein SQL_BATCH im laufenden Produktprozess bleibt unbeantwortet, bis ein Backend verdrahtet wird.

## 7. Flake

Ein Test war in der Fix-Runde einmal rot, Ursache nicht erfasst. Von mir gefahren (Stage `smoke` mit Filter auf `runtime`, `config`, `SwitchableChannelTest`, jeweils die Test-Stage frisch ohne Cache): drei Läufe ohne Mutation, alle Exit 0; dazu die vollständige Stage `test` (500 PASSED, 0 FAILED). Insgesamt vier Läufe des `ServerSmokeTest` und der Wiring-Tests, unter der Last von Load Average 7 bis 10. **Kein Rot beobachtet; vier Läufe belegen keine Flake-Freiheit.** Der neue Treiber-Test hängt an einer Echtzeit-Frist von 1 s gegen ein Zeitfenster von 5 s; unter stärkerer Last ist er der naheliegendste Kandidat, aber das ist eine Vermutung, keine Messung. Empfehlung: bei dem nächsten roten Lauf die Ausgabe sichern, bevor neu gestartet wird; nicht als erledigt führen.

## 8. Weitere Beobachtungen

- INFO: Die Commits `61141fd` und `d5b0270` nennen als Anforderung [TDS-FA-06](../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände), der Slice-Plan bindet [TDS-FA-09](../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-FA-01](../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung) und [TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit). Der Commit-Hook prüft nur das Vorhandensein einer ID; die Verbindung zur Rückverfolgung ist dadurch schwächer. Kein Befund an Code.
- INFO: Gelaufen sind `make detekt` (Exit 0, ungekürzt, ohne Mount), die Stage `test` (frisch, Exit 0) und die Mutationsläufe; `make gates` insgesamt nicht (siehe N3).

## 9. Eigene Läufe (Zusammenfassung)

| Lauf | Ergebnis |
|---|---|
| Stage `smoke`, Filter `runtime`, `config`, `SwitchableChannelTest`, ohne Mutation, 3 Läufe | Exit 0 |
| Neun Mutationen, einzeln (Abschnitt 4) | alle rot |
| `make detekt` | Exit 0 |
| Stage `test`, ohne Cache, ganze Suite | Exit 0, 500 PASSED, 0 FAILED |
| `make docs-check` | Exit 0 nach Korrektur zweier Linkziele dieser Datei |

**Übergabe:** Bericht an den Planner. Offen für ihn: Ergänzung im Träger-Slice für das Flag (Punkt 5.2), Entscheidung zu Dateirechten/Übergabe (5.1), Ausgänge der Risiken, Closure-Notiz und Register. Vom Implementer zu klären: F-5, F-7 als angenommen vermerken, F-9 zweiter Teil.
