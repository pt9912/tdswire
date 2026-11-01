# Verifikationsbericht: slice-attention-cancel-timeout

* Rolle: Verifier (Modul 11), frischer Kontext. Branch `worktree-agent-a003345c735119746`, HEAD `8cacbb7`; Plan/DoD `docs/plan/planning/in-progress/slice-attention-cancel-timeout.md` §2; Entscheidungen [ADR-0027](../plan/adr/0027-abbruch-und-zuruecksetzen.md), [ADR-0004](../plan/adr/0004-session-zustandsautomat.md), [ADR-0007](../plan/adr/0007-fehler-und-attention.md), [ADR-0013](../plan/adr/0013-backend-grenzen.md), [ADR-0015](../plan/adr/0015-spannungen-adr-gegen-spec.md); Review [2026-11-01-0000999-slice-attention-cancel-timeout.md](2026-11-01-0000999-slice-attention-cancel-timeout.md) (F1 bis F12). Verdichtet vom Aufrufer abgelegt; Findings vollständig.

## Gesamturteil: closure-fähig mit Bedingungen

Kein HIGH. Alle drei Liefer-Punkte und die Doku-Haken (a) bis (e), (h) sind durch eigene Läufe und Rohbytes-Tests bestätigt. Bedingungen vor `done/`: (M1) KDoc-Satz in `RequestLoop` korrigieren; (M2) Closure-Notiz §7 füllen, alle Risiken aus Plan §6 mit Ausgang, Register und Paarungen; (M3) die ungemessene Setzung „N ATTENTION, N Bestätigungen“ als Folgepflicht an `slice-cancel-referenzclient-szenario` und als Re-Evaluierungs-Fall von [ADR-0027](../plan/adr/0027-abbruch-und-zuruecksetzen.md).

## Sensoren (selbst gefahren)

* `make gates`: Exit 0, 9 s (alle Docker-Stages CACHED; Warmwert, kein Maß der echten Laufzeit), `baseline-verify` OK, d-check 641 Dateien, 0 Befunde.
* `make matrix-zitate`: Exit 0, 92 s, Stage `test` frisch: 872 bestandene Testfälle, 237 verschiedene Zitate, 0 ohne PASSED.
* `make e2e`: Exit 0, 294 s, 7 Stufen grün (Stufe 3: 4 Tests, 4: 13, 5: 19, 6: 5, 7: 4). Belegt keine Regression der Anfragewege am Referenzclient, nicht das Cancel-Verhalten (CO-004 bleibt). Zur Bestätigung der DoD war `make e2e` nicht nötig (der Slice behauptet keinen E2E-Beleg).

## Eigene Mutationen (7, Wegwerf-Kopien, ein Prozess zugleich)

M1 `AbortableRun.watch` ohne `tray.drop()`: rot (97 s). M2 `InboundTray.peek` ohne `held = inbound`: rot (178 s). **M3 `RequestTimes.DEFAULT_CANCEL_TIMEOUT` 5 s auf 6 s: überlebt** (folgenlos für die Produktion, F-4). M4 Matrix ATTENTION in `CANCELLING` nicht ohne Wirkung: rot. M5 `TDS_TIMEOUT` Class 16 auf FATAL: rot. M6 `if (first != null) job.cancel()` entfällt: rot (Hänger bis zur Zeitgrenze, 267 s). M7 `remaining <= ZERO` zu `< ZERO`: rot. Die zwei vom Reviewer als überlebend gemeldeten Mutationen (F1, F5) sind nach der Fix-Runde tot.

## DoD

* **Punkt 1 (ATTENTION, Cancel, Bestätigung): bestätigt.** Eigenes Paket auch im Streaming (`AttentionCancelTest`); genau ein `cancel()` und `close()` (`cancelled.size == 1`, `closeCalls == 1`); `DONE[ATTN]` allein ohne ERROR (13-Byte-Handvektor `BatchVectors.DONE_ATTN`, Hex-Vergleich); folgende Anfrage erhält nur ihre Antwort; suspendierendes und blockierendes Fake-Backend; Paket im Schreiben (`AttentionWireTest > ATTENTION mitten in einem Paket`); `CANCELLING` ist Zustand der flachen Aufzählung (KDoc `ConnectionState`).
* **Punkt 2 (Wettlauf, Handshake): bestätigt.** Der erste Übergang gewinnt (nur der Gewinner bekommt `Cancel`); gesteuerte Reihenfolge statt Wiederholung (Eingriffe der Senke, `SteeredPause`); Fälle ATTENTION nach DONE, nach Timeout, wiederholt, verlierend; ATTENTION in PRELOGIN, TLS-first und LOGIN7 schließt ohne Antwortbytes (`AttentionHandshakeTest`).
* **Punkt 3 (Zeitlimits): bestätigt.** (a) `CancelTimeoutTest`: Schließen erst nach `CANCEL_TIMEOUT` ab dem Gewinn; (b) `deadlineEpochMillis` = Beginn plus `QUERY_TIMEOUT`, Frist in der Vergangenheit und genau null ohne Backend-Aufruf, `null` ohne Zeitlimit, Ablauf mit Folgeanfrage, Frist und ATTENTION kreuzen sich; Weg der Einstellung (`ConfigKeys`, `RequestSettings` 30/5, Bereich 1 bis 3600, Loader, Bootstrap, `ConnectionWiring`; `AbbruchWiringTest`, M5/M6 rot).
* **Doku-Haken (a) bis (e), (h): bestätigt** (SPEC-016, Zuordnungstabelle, [TDS-FA-06.a](../../spec/spezifikation.md#tds-fa-06a--fehlerabbildung) Nr. 6, SPEC-029, SPEC-001, Backend-Vertrag „Offene Punkte“, Matrixzeilen). Nicht beanspruchbar: E2E-Stufe für Cancel (Folge-Slice); Haken „Review durchgeführt“ ist Sache des Planners.

## Spec Wort für Wort

Keine ADR-/Slice-/Welle-Wörter in `spec/` (grep 0 Treffer); `spec/lastenheft.md` und `spec/architecture.md` unverändert. SPEC-001, 009, 016, 029, 033, [TDS-FA-06.a](../../spec/spezifikation.md#tds-fa-06a--fehlerabbildung), Backend-Vertrag und Matrix tragen so weit wie ihre Belege; Setzungen sind gekennzeichnet. `TDS_TIMEOUT` 50005, Class 16, State 1, Text „Timeout.“, Folge OPEN gleich `ErrorCode.kt`; Handvektor `AA 1E 00 55 C3 00 00 01 10 …` in `ErrorMapperTest`.

## Plan gegen Code

65 Dateien, alles im Slice-Umfang. Plan §3 Zeile 78 („`transport/` update“) ist überholt: kein Eingriff in `transport/`, die gleichzeitige Leselogik liegt in `dispatch/InboundTray`. Nicht berührt: `config/detekt`, `build.gradle.kts`, `Dockerfile`, `tools/`, `harness/`, `.github`, `Makefile`, `spec/architecture.md`, `spec/lastenheft.md`. Kein `@Suppress`; keine Accepted-ADR verändert; Lifecycle-Commits getrennt (AGENTS §3.3).

## Findings

* **M1 (MEDIUM), KDoc widerspricht Spec und Code (§3.7):** `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/RequestLoop.kt` Zeile 86: „die Pakete einer Antwort gehen außerhalb der Sperre des Rahmens hinaus (die Schleife ist während einer Anfrage der einzige Schreiber)“. `HandshakeSession.guarded` schreibt jedes Paket unter derselben Sperre; SPEC-029/009/033 sagen das, `QueryTimeoutTest > Ein haengender Schreibvorgang ...` belegt es. Bedingung: Satz ersetzen (Sperre je Schreibvorgang, Verweis auf `HandshakeSession.guarded`).
* **M2 (MEDIUM), Closure-Pflichten offen:** §7 „Wird bei der Closure gefüllt.“ Von acht Risiken (Plan §6) tragen zwei einen Ausgang. Vorschlag: Cancel-Antwort-Inhalte als Setzung: weiter offen (Referenzclient-Slice); fehlende E2E-Stufe: weiter offen (`slice-cancel-referenzclient-szenario`, getragen von `docs/plan/e2e-abdeckung.md`); Wettlauftests mit gesteuerter Reihenfolge: entfallen für Tests mit Senken-Eingriff (Ausnahme `AttentionWireTest > mitten in einem Paket`, F-2); Fake-Backend statt echtem Treiber: weiter offen (`BEO-TDS/test-belegt-nur-den-fake`); zwei Auslöser, ein Pfad: entfallen (`Nur der erste Ausloeser ...`, M1/M4 rot); Weg der Einstellung: entfallen (Rückführung nicht ausgelöst); Leerlauf am echten Server: weiter offen (`BEO-TDS/messung-am-echten-sql-server-fehlt`); Pooling/TDS 8.0: weiter offen (`BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`).
* **M3 (MEDIUM), ungemessene Setzung mit Wirkung auf [TDS-QA-07](../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität):** „Jedes weitere ATTENTION wird einzeln bestätigt“ ist als Setzung gekennzeichnet (Spec und Testname), aber ein Client mit Cancel und Command-Timeout kurz hintereinander könnte das zweite `DONE[ATTN]` seiner Folgeanfrage zuordnen. Folgepflicht: in die Closure-Notiz als Re-Evaluierungs-Fall von [ADR-0027](../plan/adr/0027-abbruch-und-zuruecksetzen.md), nicht als erledigt.
* **F-1 (LOW):** `DispatchFailureTest`: Meldung „die naechste Nachricht wurde beantwortet“, geprüft wird aber `replies.size == 1` (nicht beantwortet): Text verkehrt herum.
* **F-2 (LOW):** `AttentionWireTest > ATTENTION mitten in einem Paket`: Synchronisation mit `repeat(HOPS = 8) { yield() }`, Reihenfolge danach nicht strikt gesteuert; nicht mutiert (verwandt `BEO-TDS/mutation-haengt-an-ungesteuerter-reihenfolge`).
* **F-3 (LOW):** `AbbruchWiringTest` misst `deadline - before in 1_000L..2_000L` mit echter Uhr (Review F10; Fix-Runde hat die Obergrenze laut Implementer entfernt: gegen den Stand `8cacbb7` prüfen).
* **F-4 (LOW):** M3 überlebt: `RequestTimes.DEFAULT_QUERY_TIMEOUT`/`DEFAULT_CANCEL_TIMEOUT` sind eine zweite Quelle neben `ConfigDefaults`; `DEFAULT_CANCEL_TIMEOUT` ohne Test; Wartungsrisiko.
* **F-5 (LOW):** SPEC-029 „sobald das Paket ganz auf dem Draht steht“: tatsächlich hält ein `write(bytes)` die Sperre für alle Pakete, die ein Token erzeugt; Aussage enger als der Code, harmlos.
* **F-6 (LOW):** `ServerConfig.requests` mutable neben dem Konstruktor (Review F9); KDoc-Umbruch in `ServerConfig.kt` ohne inhaltliche Änderung.
* **I-1:** Öffentliche Fläche geändert: `RequestTimes` (public), `RequestLoop(times)`, `ServerConfig.requests`; Einbettende ohne Angabe bekommen nach 30 s `TDS_TIMEOUT` (Spec nennt es; `integratorTest` grün). **I-2:** KDocs nennen [ADR-0027](../plan/adr/0027-abbruch-und-zuruecksetzen.md) im Quelltext (dort erlaubt). **I-3:** `AttentionAck` „als Handvektor belegt“ ohne Quelle (Praxis wie `DONE_ERROR`). **I-4:** Doku-Haken (f), (g), (i) liegen in `slice-resetconnection-zuruecksetzen` und `slice-abbruch-ressourcen-shutdown`, nicht angefasst.

## Folgepflichten

1. KDoc-Satz in `RequestLoop` (M1) korrigieren, bevor der Slice nach `done/` geht. 2. Closure-Notiz §7 füllen (Risiken mit Ausgang, Steering-Loop-Lerneintrag, Register ohne Zähler; Kandidaten: „Test synchronisiert mit `yield()`-Hops“ (F-2), „Voreinstellung in zwei Quellen“ (F-4)). 3. `slice-cancel-referenzclient-szenario` muss messen: N Bestätigungen (M3), `DONE[ATTN]` allein gegen den Referenzclient, ATTENTION im Leerlauf; Fehlverhalten dort ist Re-Evaluierungs-Fall von [ADR-0027](../plan/adr/0027-abbruch-und-zuruecksetzen.md); `CO-004` bleibt bis dahin bestehen. 4. Nach dem Merge mit dem Testserver-Umbau `make build` und `make test` (der Testserver greift auf `internal`-Symbole `ConfigValidation::queryTimeout`, `ConfigDefaults.QUERY_TIMEOUT_SECONDS` zu); Doku-Konflikte in `spec/spezifikation.md` von Hand lösen. 5. Plan §3 Zeile 78 im Ist-Zustand vermerken.
