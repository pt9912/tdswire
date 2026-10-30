# Nachverifikation: slice-transport-listener-lebenszyklus nach der Fix-Runde (a8072b5) — 2026-10-30

**Rolle:** Verifier (Frage: „Bauen wir es richtig?“ gegen Plan, DoD und Entscheidungen), frischer Kontext. Der Bericht repariert nichts; außer dieser Datei wurde im Repo nichts geändert, nichts committet (die vom Lauf von `make spec-abdeckung` neu geschriebene Matrix wurde per `git checkout` zurückgesetzt). Mutationen, Sonden und Lastläufe liefen in Kopien des Baums (Docker-Build-Kontext, nichts gemountet).

**Gegenstand:** [slice-transport-listener-lebenszyklus](../plan/planning/in-progress/slice-transport-listener-lebenszyklus.md), Fix-Commit `7a3032f` (einziger Commit in `git log 461e380..HEAD` unter `src/main/kotlin/io/github/pt9912/tdswire/transport`), gegen die Findings F-1 bis F-12 aus [dem Review](2026-10-30-d9bab24-slice-transport-listener-lebenszyklus.md). Geprüft: F-1 bis F-8 und F-12. Stand: `HEAD` = `a8072b5`; seit `7a3032f` änderte sich unter `src/` nichts.

**Ergebnis in einem Satz:** F-1, F-4, F-5, F-7 und F-12 sind behoben, F-3 und F-6 mit benanntem Rest, F-2 im Kern behoben (der Rest „läuft im abgebrochenen Aufruf zu Ende“ ist unbelegt), F-8 als Maßnahme getroffen, aber ohne Beleg ihrer Wirkung; zwei Aussagen der Fix-Runde tragen weiter, als ihr Test reicht (V-1, V-2), ein neu geschriebener Zweig hat keinen Test (V-3). Die drei Liefer-Punkte der DoD sind im Verhalten belegt; die formale DoD (Häkchen, Closure-Notiz, Risiko-Ausgänge, Register) ist noch nicht gezeichnet, weil der Slice in `in-progress/` steht.

## 1. Selbst gefahrene Läufe

| Lauf | Ergebnis |
|---|---|
| `docker build --target test --no-cache-filter test --progress=plain .` (Stage `test` frisch; die Stage `testkeys` mit der Änderung kam aus dem Cache, ihr Inhalt ist durch die Fix-Runde gebaut) | `BUILD SUCCESSFUL in 1m 13s`, 353 Tests PASSED, kein FAILED |
| `make detekt` | Exit 0 |
| `make spec-abdeckung` | Exit 0; Zeile je Festlegung unten in Abschnitt 4 |
| Flakiness 1: Test „login timeout closes a connection stuck in the handshake in both paths“, 200 Wiederholungen (je zwei Pfade, also 400 Läufe) in einem Lauf der Stage `test`, daneben 60 Endlosschleifen auf 20 Kernen, Laufzeit 3m 44s | grün, kein Fehlschlag |
| Flakiness 2: dieselbe Messung mit entferntem Warten auf den Handler (Mutation M9), 200 Wiederholungen, 60 Endlosschleifen | ebenfalls grün; der Fehler des Reviews (1 von 200) trat nicht auf, die Messung unterscheidet Fix und Rückbau nicht |
| Flakiness 3: sechs parallele Volläufe der Stage `test` (je Kopie mit eigener Datei, damit der Build-Cache sie nicht zusammenlegt), 20 Endlosschleifen | 6 von 6 Exit 0, kein FAILED |

Die Messungen 1 und 2 sind eine Stichprobe, kein Beleg der Stabilität: der Fehler ließ sich auch vor dem Fix in diesem Aufbau nicht erzeugen (Befund V-5).

## 2. Status der Befunde F-1 bis F-8, F-12

| ID | Status | Beleg |
|---|---|---|
| F-1 (Test nimmt an, dass der Handler vor der Frist lief) | **behoben (nicht rot gesehen)** | Der Test wartet jetzt mit `eventually` auf `handler.wires.size == 1`, bevor er die Frist freigibt (`ListenerLifecycleTest.kt`). Der Handler legt den Draht beim Start des Rumpfs an (`ListenerSupport.kt`, `TlsEchoHandler`), die Bedingung trifft also den Fehlerweg. Dass ohne die Zeile der Fehler auftritt, konnte ich nicht zeigen (Abschnitt 1, Messung 2). Mutation M9 (Zeile entfernt) überlebt, auch unter Last. |
| F-2 (shutdown() nicht abbruchfest) | **behoben mit Rest** | `shutdown()` ruft `root.cancel()` und `io.stop()` jetzt in einem `finally`. Mutation M1 (ohne `try/finally`) färbt den neuen Test „a cancelled shutdown still releases the connections and the threads“ rot. Rest: V-2. |
| F-3 (Port 70000 und Konstruktor-Ausnahme) | **behoben mit Rest** | Der Fang von `IllegalArgumentException` in `open` ist belegt: Mutation M2 (Fang entfernt) färbt „invalid port ends the start and leaves no thread“ rot (Ports 70000 und −1). Der zweite Teil des Findings (Ausnahme beim Aufbau des Listeners nach dem Binden, Zweig `launched`): Mutation M8 (Aufräumen im `finally` entfernt) überlebt; der Zweig hat keinen Test. Rest: V-3. |
| F-4 (Abweisungszweig ungetestet) | **behoben** | `ConnectionRegistry` ist `open`, `TdsListener.start(ListenerSetup)` nimmt ein Register an, der Test „the listener closes a connection its registry refuses and starts no handler“ erzwingt die Abweisung. Mutation M3 (Rückgabewert von `registry.admit` ignoriert, Verbindung immer gestartet) färbt genau diesen Test rot. |
| F-5 (Kette nur mit erstem Glied) | **behoben** | Neuer Test „every certificate of the chain must be valid“ mit Blatt, abgelaufenem Zwischenzertifikat und Wurzel; Gegenprobe mit `gueltig.p12`. Mutation M4 (`chain.first().checkValidity()`) färbt ihn rot. |
| F-6 (Threads nach Fehlstart) | **behoben mit Rest** | `IoGroup.stop` wartet auf `isTerminated` und auf `threads.any { it.isAlive }`; `assertNoNewListenerThreads` prüft sofort, kein `eventually` mehr; der Test wiederholt den Fehlstart 300-mal. Mutation M5 (`stop` ohne Warten) färbt „a failed start returns only after its threads have ended“ und „valid material starts the listener and shutdown releases the threads“ rot. Rest: V-1 (der Teil `threads.any { it.isAlive }` ist unbelegt). |
| F-7 (Herkunft der PKCS#12-Festlegung) | **behoben** | `contracts/security-profile.md` hat den Abschnitt „Schlüsselmaterial“, eingeleitet mit „Setzung dieses Profils, nicht gemessen und nicht aus dem Referenzclient abgeleitet“; das KDoc von `TlsMaterial` zeigt auf ihn statt auf „Festlegung des Nutzers vom 2026-10-30“. Die vier Zusagen stimmen mit dem Code überein (erster Schlüsseleintrag, gleiches Kennwort, RSA ab 2048, Kette auf Gültigkeit). Der vierte Punkt hat jetzt seinen Test (F-5). Siehe Abschnitt 5. |
| F-8 (Listener-Tests räumen nicht auf) | **Maßnahme getroffen, Wirkung unbelegt** | `ListenerTestBase` mit `@AfterTest` beendet jeden über `startOnLoopback` oder `track` gestarteten Listener; die Annahme sequenzieller Tests steht im KDoc. Mutationen M6 und M10 (Aufräumen entfernt) überleben. Mit einem eingeschleusten, absichtlich nach dem Start fehlschlagenden Test (Wegwerf-Datei in der Kopie) schlug mit und ohne Aufräumen nur dieser eine Test fehl; es gab in beiden Fällen keine Folgefehler. Das Aufräumen ist harmlos, sein Nutzen in diesem Aufbau nicht gezeigt (V-4). |
| F-12 (@spec-Tag) | **behoben** | „a crashing handler ends only its own connection“ trägt `@spec SPEC-005`; die Matrix zeigt [SPEC-005](../../spec/spezifikation.md#3-defaults-und-konstanten) mit 4 bestandenen Tests (ListenerLifecycleTest, ListenerSmokeTest, ListenerStartTest). [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität) ist keine SPEC-Zeile und braucht keinen Tag. |

## 3. Mutationen der Aufgabe und weitere

Jede Mutation einzeln auf einer Kopie, Lauf der Stage `test` ohne Cache-Treffer für den Quelltext.

| Nr. | Mutation | Ergebnis |
|---|---|---|
| M1 | `shutdown()` ohne `try/finally` | rot: a cancelled shutdown still releases the connections and the threads |
| M2 | Fang von `IllegalArgumentException` in `open` entfernt | rot: invalid port ends the start and leaves no thread |
| M3 | Ergebnis von `registry.admit` ignoriert | rot: the listener closes a connection its registry refuses and starts no handler |
| M4 | `ValidityOnlyTrust` prüft nur `chain.first()` | rot: every certificate of the chain must be valid |
| M5 | `IoGroup.stop` ohne Warten | rot: a failed start returns only after its threads have ended; valid material starts the listener and shutdown releases the threads |
| M5b | `stop` wartet nur auf `group.isTerminated`, nicht auf `threads.any { it.isAlive }` | **überlebt** |
| M5c | `NonCancellable` um das Warten in `stop` entfernt | **überlebt** |
| M6 / M10 | Aufräumen in `ListenerTestBase` entfernt (M6 Körper, M10 `return` am Anfang) | **überleben** |
| M8 | Aufräumen im Zweig `launched` (`server.close()`, `io.stop()`) entfernt | **überlebt** |
| M9 | Warten auf `handler.wires.size == 1` im Login-Timeout-Test entfernt | **überlebt** (auch unter Last, Abschnitt 1) |

## 4. Verifier-Befunde

| ID | Kat. | Befund | Pfad | Klasse |
|---|---|---|---|---|
| V-1 | MEDIUM | Das KDoc von `IoGroup.stop` und der Startvertrag ([SPEC-043](../../spec/spezifikation.md#spec-043--startprüfung-tls-konfiguration-und-port): nach jedem fehlgeschlagenen Start laufen keine Threads weiter) sagen „kehrt zurück, wenn kein Thread mehr lebt“. Mutation M5b entfernt `threads.any { it.isAlive }`, und alle Tests bleiben grün, auch die 300 Fehlstarts mit sofortiger Prüfung. Was `isTerminated` allein nicht sichert, ist nicht gemessen; die Zeile ist Eigencode ohne Beleg. Behebung: Nachmessen, ob die Bedingung ein Verhalten ändert (sonst entfernen), oder einen Test, der sie braucht. | `TdsListener.kt` · `IoGroup.stop` | Wartebedingung ohne unterscheidenden Test |
| V-2 | MEDIUM | Das KDoc von `IoGroup.stop` sagt „es läuft auch in einem abgebrochenen Aufruf zu Ende“. Mutation M5c (ohne `NonCancellable`) überlebt: im abgebrochenen `shutdown()` bricht `delay` dann sofort ab, `stop` kehrt zurück, und der Test „a cancelled shutdown …“ besteht trotzdem, weil `shutdownNow` die Threads schon beendet hat. Der Teil „Warten im abgebrochenen Aufruf“ ist unbelegt; die Obergrenze „kein Thread lebt, sobald der abgebrochene Aufruf zurück ist“ gilt nur, solange die Threads rechtzeitig enden. | `TdsListener.kt` · `withContext(NonCancellable)`; `ListenerShutdownTest.kt` | Zusage im KDoc breiter als ihr Test |
| V-3 | MEDIUM | Der neue Zweig `launched` (Aufräumen, wenn der Aufbau von `TdsListener` nach dem Binden scheitert) hat keinen Test; Mutation M8 überlebt. Das Review nannte diesen Fall in [F-3](2026-10-30-d9bab24-slice-transport-listener-lebenszyklus.md); die Fix-Runde nennt ihn in der Commit-Nachricht nicht, der Code ist da, der Beleg fehlt. Die Aussage „nach jedem fehlgeschlagenen Start laufen keine Threads“ ([SPEC-043](../../spec/spezifikation.md#spec-043--startprüfung-tls-konfiguration-und-port), Liefer-Punkt 1) trägt für diesen Weg nicht. Behebung: Test mit einem Register, das beim Aufbau wirft, oder den Zweig als ungeprüft in die Closure-Notiz. | `TdsListener.kt` · `launched` | Neuer Fehlerzweig ohne Test |
| V-4 | LOW | Das Aufräumen in `ListenerTestBase` (F-8) ist durch keinen Test belegt und in einem Lauf mit absichtlich fehlschlagendem Test auch nicht als nötig sichtbar (kein Folgefehler ohne es). Das Review beobachtete Folgefehler unter Last. Kein Schaden; der Nutzen bleibt eine Annahme. Gilt als Test-Infrastruktur, nicht als Zusage. | `ListenerSupport.kt` · `ListenerTestBase` | Maßnahme ohne gezeigte Wirkung |
| V-5 | LOW | Der Fehler aus F-1 ist in meinem Aufbau (60 Endlosschleifen, 200 Wiederholungen, 20 Kerne, parallele Last durch den Docker-Build) weder mit noch ohne Fix aufgetreten; der Beleg für F-1 ist die Analyse (Reihenfolge Timer, Handler) und die Lesart der Bedingung, nicht ein roter Lauf. Die Mehrdeutigkeit gehört in die Closure-Notiz zu [N-4 aus slice-handshake-login](../plan/planning/done/slice-handshake-login.md). | `ListenerLifecycleTest.kt` | Flakiness-Fix ohne reproduzierten Fehler |
| V-6 | INFO | Die Abdeckungsmatrix führt für [SPEC-023](../../spec/spezifikation.md#6-externe-verträge) nur TlsLoopbackTest und TlsNegativeTest (9 Tests). Liefer-Punkt 3 nennt SPEC-023 für die Startprobe, aber kein Test in `ListenerStartTest` oder `TlsStartProbeTest` trägt den Tag; die Probe nutzt die Parameter (`TlsParameters.SPEC_023`), ein Test, der die Konfigurierten gegen die Voreinstellung unterscheidet, ist nicht zu finden (Review F-9: „Probe-Client ohne Suitenliste“ als äquivalent eingestuft). Kein Waise, aber der Bezug aus dem Plan hat keine eigene Belegzeile. | `TlsStartProbeTest.kt` | DoD-Bezug ohne eigenen Tag |
| V-7 | INFO | `make spec-abdeckung` meldet 14 Festlegungen ohne Nachweis; keine davon gehört zu diesem Slice ([SPEC-005](../../spec/spezifikation.md#3-defaults-und-konstanten): 4, [SPEC-008](../../spec/spezifikation.md#3-defaults-und-konstanten): 16, [SPEC-043](../../spec/spezifikation.md#spec-043--startprüfung-tls-konfiguration-und-port): 12, [SPEC-044](../../spec/spezifikation.md#spec-044--geordnetes-beenden): 11, [SPEC-047](../../spec/spezifikation.md#3-defaults-und-konstanten): 3, jeweils bestandene getaggte Tests). Die eingecheckte Matrix `docs/plan/spec-abdeckung.md` unterscheidet sich in 13 Zeilen von der frisch erzeugten (Stand, nicht Inhalt der Zusagen); sie wurde nicht angefasst. | `docs/plan/spec-abdeckung.md` | – |

## 5. Dockerfile-Stage `testkeys` und Sicherheitsprofil

* **Nur diese Stage:** `git show 7a3032f -- Dockerfile` ändert ausschließlich die Stage `testkeys` (Kommentar und das `RUN`). Die Stages `test`, `detekt`, `spec-abdeckung` und ihre `COPY`-Zeilen sind unverändert.
* **Keine Schlüssel im Repo:** `git ls-files` listet keine Datei mit `.p12`, `.pem`, `.jks` oder `.key`. Die neuen Schlüsselspeicher entstehen im Layer: `wurzel.p12`, `zwischen.p12` und die Anfragen werden am Ende der Stage gelöscht (`rm`), übrig bleiben `wurzel.pem`, `zwischen-abgelaufen.pem`, `blatt.pem` und `blatt.p12` (Schlüssel des Blatts) im Image der Stage, nicht im Quellbaum.
* **Keine Gate-Lockerung:** `config/detekt/detekt.yml`, `build.gradle.kts`, `gradle.lockfile` und `Makefile` stehen nicht im Diff von `7a3032f`; `make detekt` ist grün. Die Stage erzeugt Testdaten; es wurde keine Schwelle gesenkt und kein Test abgeschaltet.
* **Zeitabhängigkeit:** das Zwischenzertifikat hat ein festes `-startdate` (2020/01/01, 30 Tage) und hängt nicht an der Bauzeit; das Blatt und die Wurzel gelten 3650 Tage ab Bauzeit, wie bereits der Kommentar der Stage für `gueltig.p12` festhält. Kein neuer Fehlermodus.
* **Cache-Hinweis:** `--no-cache-filter test` baut die Stage `testkeys` nicht neu; sie war aus dem Cache. Der Frischbeleg der Kette (`blatt.p12` und die drei Zertifikate) liegt darin, dass die Tests M4 und die Gegenprobe tragen.
* **Sicherheitsprofil:** [contracts/security-profile.md](../../contracts/security-profile.md) §Schlüsselmaterial ist als Setzung gekennzeichnet und enthält keine Messbehauptung ([AGENTS.md](../../AGENTS.md) §3.8 erfüllt). [SPEC-043](../../spec/spezifikation.md#spec-043--startprüfung-tls-konfiguration-und-port) nennt die Form (PKCS#12) nicht; sie nennt „nicht zugelassene Form“ ohne Festlegung, die Setzung steht nur im Vertrag. Kein Widerspruch, aber die Spezifikation verweist nicht darauf. Eine Zeile in SPEC-043 mit Verweis auf den Abschnitt würde den Anker schließen; das ist Sache des Planners.

## 6. Folge aus dem Handshake-Slice: Einordnung der Abbruchausnahme

Das Risiko steht in §6 des Slice als „offen bis zur Closure“ ([slice-handshake-login](../plan/planning/done/slice-handshake-login.md), Nachverifikation N-3). Der Code von `ManagedConnection.finish` ist seit `f888bcc` unverändert (`failure is CancellationException -> ConnectionEnd.SHUTDOWN`). Wegwerf-Test in der Kopie: ein Handler, der ohne Shutdown eine `CancellationException` wirft, führt zum Ereignis `Closed` mit Ende `SHUTDOWN` (Erwartung `HANDLER_FAILED` schlug mit „expected: HANDLER_FAILED but was: SHUTDOWN“ fehl). Das Risiko ist damit **eingetreten und ehrlich offen geführt**; weder entschieden noch als Grenze in KDoc oder Vertrag benannt. Vor der Closure braucht es eine Entscheidung (Abbruch ohne Shutdown trennen) oder eine Grenze im KDoc von `ManagedConnection` und der Closure-Notiz; der Bootstrap-Slice hängt daran.

## 7. DoD-Konformität (Stand der Verifikation)

| Liefer-Punkt | Verhalten | Beleg |
|---|---|---|
| 1 Listener ([SPEC-005](../../spec/spezifikation.md#3-defaults-und-konstanten), [TDS-FA-09](../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität)) | bestätigt, mit Rest | Smoke, Absturz einer Verbindung, belegter Port, ungültiger Port bestanden; Rest V-3: „nach jedem fehlgeschlagenen Start laufen keine Threads“ für den Weg nach erfolgreichem Binden unbelegt. |
| 2 Lebenszyklus ([SPEC-008](../../spec/spezifikation.md#3-defaults-und-konstanten), [SPEC-044](../../spec/spezifikation.md#spec-044--geordnetes-beenden), [SPEC-047](../../spec/spezifikation.md#3-defaults-und-konstanten), [TDS-FA-07](../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe)) | bestätigt, mit Rest | Frist, Beenden, zweiter Aufruf, abgebrochenes Beenden belegt (M1, M3). Rest V-2. Einordnung der Abbruchausnahme offen (Abschnitt 6). |
| 3 Startprobe ([SPEC-043](../../spec/spezifikation.md#spec-043--startprüfung-tls-konfiguration-und-port), [SPEC-023](../../spec/spezifikation.md#6-externe-verträge), [ADR-0002](../plan/adr/0002-tls-und-verschluesselung.md), [TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit)) | bestätigt | Je ein Test für Zertifikat, unlesbare Kette, Schlüssel, Paar; ganze Kette (M4); Port danach frei. V-6 für den Tag SPEC-023. |

Nicht gezeichnet (Stand `in-progress/`, alle Häkchen in §2 offen, §7 „Wird bei der Closure gefüllt“): Zuordnung Test | Festlegung in der Closure-Notiz, Steering-Loop-Lerneintrag, Beobachtungs-Register, Ausgang jedes Risikos aus §6, `make gates` als Abschlusslauf. Das ist der Zustand vor der Closure, kein Verstoß; die Punkte V-1 bis V-3 und Abschnitt 6 gehören in die Ausgänge.

Der [ADR-0003](../plan/adr/0003-transportframework.md)-Bezug (Scope je Verbindung, Listener schließt zuerst) ist durch die Tests von M1 und M3 belegt; ein Verstoß gegen eine Entscheidung wurde nicht gefunden.

## 8. Übergabe an den Planner

1. V-1 bis V-3 vor der Closure einarbeiten oder in der Closure-Notiz als ungeprüft führen (Test, Entfernen der Zeile oder Einschränken des KDoc).
2. Die Einordnung der Abbruchausnahme (Abschnitt 6) entscheiden oder als Grenze benennen.
3. F-1 in der Closure-Notiz als „durch Analyse behoben, Fehler in der Nachmessung nicht reproduziert“ führen (V-5).
4. Optional: SPEC-043 um den Verweis auf [contracts/security-profile.md](../../contracts/security-profile.md) §Schlüsselmaterial ergänzen; Tag SPEC-023 an den Test der konfigurierten Parameter (V-6).
