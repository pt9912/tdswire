# Review-Report: slice-sp-execute-unbekanntes-handle-bauen — 2026-11-08

**Review-Art:** Code (gegen Plan, ADR und Hard Rules; nicht gegen die DoD, das ist die Frage des Verifiers).

**Gegenstand:** Diff `main` (`390e0078`) bis `ba72fe81` im Zweig `worktree-agent-a59a0d30bb47a0dc7`: `9d48a029` (reiner Move), `956c46fc` (Verweis-Nachzug), `0e450bcb` (Spezifikation, Nutzer-Doku, Matrix, Messprotokoll, Quelltext, Tests), `7919d0f4` (detekt-Befunde, Quelltext; hier mitgelesen, kein eigener Review), `ba72fe81` (Abdeckungsmatrix); 20 Dateien, +186/−109.

**Skill:** `.harness/skills/reviewer.md` @ `ba72fe81` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-08

**Eingangs-Kontext** (die Verträge, gegen die geprüft wurde):

- Slice [slice-sp-execute-unbekanntes-handle-bauen](../plan/planning/done/slice-sp-execute-unbekanntes-handle-bauen.md) (Plan §3, DoD, Risiken §6)
- [ADR-0045](../plan/adr/0045-sp-execute-unbekanntes-handle-ergibt-fehler-8179-state-4.md) (Accepted, Option A), [ADR-0037](../plan/adr/0037-sp-unprepare-unbekanntes-handle-ergibt-fehler-8179.md), [ADR-0036](../plan/adr/0036-handle-freigabe-und-grenze-je-sitzung.md)
- [TDS-FA-04](../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität); Abschnitt 4 der [Spezifikation](../../spec/spezifikation.md#4-fehler-codes-und-logging-felder) (`SPEC-057`, `SPEC-060`), [SPEC-030](../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten)
- Messprotokoll `docs/messungen/referenzabgleich.md` (M-4, M-6, M-7, M-22, M-23) und die Rohbytes unter `referenztest-dotnet/Mitschnitt/messung/`
- `AGENTS.md` §3.1 bis §3.8
- Register: [wanduhr-tests-flackern-unter-last](../plan/planning/observations/BEO-TDS/wanduhr-tests-flackern-unter-last/state.md)

**Selbst gefahren** (Sensor-Zuschnitt nach der Tabelle in `harness/README.md`: Änderung an `src/`): `make gates FORCE=1` im Arbeitsbaum des Zweigs, Exit 0 (35 s, alle Docker-Schichten aus dem Cache des Implementers, also dieselben Quellen; darin `make flaeche` und `make docs-check` grün, d-check 1300 Dateien, 0 Befunde; der Stempel `tests` wurde nicht geschrieben: „Eingangspfade nicht sauber“ wegen des ignorierten Build-Verzeichnisses `testserver/build` im Arbeitsbaum, ohne Bezug zum Diff). Mutationen in einer Kopie des Baums unter dem Scratchpad, je Mutation die fünf engen Testklassen (`ErrorMapperTest`, `PreparedHandlesDispatchTest`, `ResetConnectionDispatchTest`, `RequestSequenceTest`, `UnprepareDispatchTest`). Kein `make clean-build`, kein `make e2e`, kein `make matrix-zitate`, keine künstliche Last. Merge-Probe: `git merge-tree` gegen `main` `a830d6d1`.

## Gesamturteil

0 HIGH, 1 MEDIUM, 2 LOW, 6 INFO. Der Code trägt: Nummer 8179, Class 16, State 4, Text mit der Nummer des Handles, ein Fehler, offene Verbindung, Backend nicht gerufen, Zählung unverändert; die Batch-Freigabe behält State 8 (Gegenprobe rot, siehe Mutationen). Die Aussagen in Spezifikation, Matrix und Nutzer-Doku trennen Messung (Nummer, Class, State, Text für Handle 999999, offene Verbindung, vergeben-und-freigegeben) von Setzung (RETURNSTATUS, leere Felder, Text für andere Nummern, Fall nach `RESETCONNECTION`) und gehen an keiner Stelle über M-6 und M-7 hinaus. Einziger Mangel von Gewicht ist die Plan-Tabelle (F-1). Die Merge-Probe gegen den weitergelaufenen `main` ergibt Konflikte in vier Dokumentdateien (F-6), keinen im Quelltext.

## Prüfung der Schwerpunkte

**(a) Echtheit gegen die Messdateien.** M-6 und M-7 in `docs/messungen/referenzabgleich.md` und der Testcode `ReferenzMessungTests.cs` (Zeilen 210 bis 277: `sp_execute` als Prozedur und als Batch `EXEC sp_execute 999999`, Sondierung von `h1`, `h2`, `a1`, `a2` nach der Freigabe) tragen: 8179, Class 16, State 4, Text mit Handle 999999, ein Fehler, Verbindung offen, vergebenes und freigegebenes Handle State 4. Unter `referenztest-dotnet/Mitschnitt/messung/` liegt **kein** Mitschnitt von `sp_execute` mit unbekanntem Handle: 8179 (`f31f0000`) steht nur in `bulk-merge-katalog/{2022,2025}/zusatz-unprepare-curcmd.messung` und `zusatz-unprepare-viele.messung`, also bei `sp_unprepare` (State 8, ERROR mit Prozedur `sp_unprepare`, Zeile 1, Servername gesetzt, dann RETURNSTATUS 8179 und DONEPROC `0xe0` mit Fehlerbit, kein DONE). Das deckt die Behauptung von [ADR-0045](../plan/adr/0045-sp-execute-unbekanntes-handle-ergibt-fehler-8179-state-4.md) („nicht gemessen für `sp_execute`“) und die Kennzeichnung als Setzung in Spezifikation, Matrix, Nutzer-Doku und Testkommentaren. Handvektor in `ErrorMapperTest` nachgerechnet: Länge 0x6E = 4 + 1 + 1 + 2 + 96 (48 Zeichen UTF-16) + 1 + 1 + 4, State `04`, Class `10`, Text 48 Zeichen für Handle 1.

**(b) RETURNSTATUS (Entscheidung des Implementers).** `errorAnswerTail` (unverändert, `response/ResponseRun.kt`) setzt in der Form RPC bei einer Nummer unter 50000 den RETURNSTATUS; das ist die bestehende, in Spezifikation (Abschnitt „Fehler in der Antwort auf einen RPC“) und `ErrorMapper`-KDoc geführte Regel, keine neue. [ADR-0045](../plan/adr/0045-sp-execute-unbekanntes-handle-ergibt-fehler-8179-state-4.md) Entscheidung 4 nennt „ERROR und DONEPROC mit Fehlerstatus“ als Abschluss und führt den RETURNSTATUS ausdrücklich als offen (Offen 1: „akzeptiertes Negativ … die Bytes dahinter sind Setzung“); die Spezifikationsänderung der ADR verlangt „Tokenfolge und weitere Felder als Setzung kennzeichnen“. Das ist geschehen: `SPEC-057` trägt den RETURNSTATUS in der Zusage und nennt ihn in der Setzungsliste „nach der Regel für SQL-Server-Nummern in `SPEC-030`“; Matrix und Nutzer-Doku ebenso. Ein Widerspruch zum Text einer Accepted-ADR besteht nicht (Offen 1 deckt die Wahl), eine Folge-ADR ist dafür nicht nötig (F-4, INFO).

**(c) State je Aufrufform, Regression.** `ErrorRule.message(handle, state = this.state)`: nur ein Aufrufer übergibt einen State (`executeUnknownHandle`); `releaseAnswer` ruft `message(number)` und bleibt bei State 8 (Mutation E1 rot). `refuse(code, response)`: die drei Aufrufer (`TDS_SHUTDOWN`, `TDS_PREPARE_LIMIT`, `sp_execute`) bilden die Antwort wie zuvor (`errors.code(code, cause, form)` mit `formOf(message)` bzw. `ResponseForm.RPC`); `queryOutcome` hängt weiter nur an `TDS_SHUTDOWN`. `executeUnknownHandle` ist `ErrorMapper.response` im Zweig „synchroner Strom“ nachgebaut (Log-Eintrag, `TdsMessage(TABULAR_RESULT, encodeTokens(errorAnswer(RPC, …)))`, Fate der Regel); der Pfad hat keinen offenen Strom, der Unterschied (kein `CLOSE_WITHOUT_REPLY`) ist ohne Wirkung. Log-Marke `unknown-handle` bleibt, `UnprepareDispatchTest` filtert über `RELEASE_CAUSE = "unknown-prepared-handle"` statt über den Code; die Telemetrie führt den Code `TDS_SIM_UNKNOWN_PREPARED_HANDLE` (`RequestWiringTest`, `ZustandsereignisseProzessTest` ziehen den Namen), der Aufzählungswert `TDS_UNKNOWN_HANDLE` hat in `src/main`, `src/integrator`, `testserver` keinen Verweis mehr; `ErrorCode` ist `internal`, `make flaeche` grün.

**(d) Fälle.** Nie vergeben (2), −1, 0 (`PreparedHandlesDispatchTest`, jeweils mit der eigenen Nummer im Text), freigegeben (`UnprepareDispatchTest`: `sp_execute` auf 1 und 2 nach Batch-Freigabe), vor `RESETCONNECTION` (`ResetConnectionDispatchTest`, drei Handles, je mit Nummer im Text; zusätzlich über den echten Transport mit Status `0x09`), Folge-RPC und gehaltenes Handle werden danach beantwortet, Zählung unverändert (Folge `prepReply(2)`), mehrere `sp_execute` hintereinander (`RequestSequenceTest`, `PreparedHandlesDispatchTest`), `backend.requests.size` unverändert. Falscher Typ, zu kurzes Handle, fehlendes Parameterbyte: der Binder weist vor der Suche des Handles ab (`SPEC-013`, im Diff unberührt, `bound.handle` ist ein `Int`); ein neuer Absturzpfad entsteht durch den Diff nicht.

**(e) Mutationen** (Kopie des Baums, je eine Mutation, fünf enge Testklassen; Basis grün, 47 s):

| Nr. | Mutation | Wirkung gemessen |
|---|---|---|
| BASE | unverändert | grün (rc 0) |
| M1 | `STATE_EXECUTE_UNKNOWN_HANDLE` 4 → 8 | rot, 16 Tests |
| M3 | Backend wird bei unbekanntem Handle gerufen (`handles.sql(…) ?: "select 1"`) | rot, 15 Tests |
| M7 | Form `BATCH` statt `RPC` in `executeUnknownHandle` (DONE, kein RETURNSTATUS) | rot, 4 Tests |
| M8 | Text ohne die Nummer des Handles (nur in `executeUnknownHandle`) | rot, 16 Tests |
| E1 (eigen) | State 4 auch für die Batch-Freigabe (`releaseAnswer`) | rot, 10 Tests |
| E2 (eigen) | für Handle 0 weiter die alte Antwort 50009, State 1, „Unknown prepared handle.“ | rot, 2 Tests |

Die Mutationen M2, M4 bis M6 des Implementers habe ich nicht nachgefahren (Stichprobe wie beauftragt); keine meiner Mutationen blieb grün.

**(f) Clients und Skripte, die 50009 erwarten.** `grep -rnE "50009|TDS_UNKNOWN_HANDLE|Unknown prepared"` über das ganze Repo (ohne `.git`, Baseline, Reviews): keine Fundstelle in `referenztest-dotnet/`, `testserver/`, `tools/`, `src/main`, `src/integrator`; die verbleibenden Treffer sind Spezifikation (Zusage „gibt es nicht mehr“, Änderungsprotokoll), Messprotokoll, ADRs, Slices und Register in `done/` (Zeitdokumente) sowie die zwei Wächter-Zeilen in Testkommentaren. Risiko 4 des Slice kann den Ausgang „entfallen“ tragen.

**(g) Plan-Tabelle, zweite Fundstellen, Haken.** Siehe F-1 (Plan-Tabelle), F-5 (Matrix-Zeile), F-7 (Nutzer-Doku). Keine `- [x]` im Diff gesetzt (§2 des Slice: alle Haken offen, Rollen-Text nicht in der DoD). `harness/README.md` und `harness/conventions.md` im Diff unberührt (Zellenlängen nicht betroffen). Roadmap-Zeile: „Nichts in Arbeit.“ ersetzt durch „In Arbeit: … mit [slice]“, Drift-Log-Zeile trägt nur den nachgezogenen Link; Zustandsfeld ohne Chronik.

**(h) `ListenerAbortTest`, Einordnung** — siehe F-8.

## Findings

### F-1 MEDIUM — Plan-Tabelle und Kopf „Berührte Spec-Stellen“ nennen berührte Stellen nicht

* kategorie: MEDIUM
* quelle: Reviewer-Skill „Plan-Tabelle nennt die berührten Dateien nicht“; `AGENTS.md` §3.8
* pfad: `docs/plan/planning/done/slice-sp-execute-unbekanntes-handle-bauen.md` · „**Berührte Spec-Stellen:** SPEC-057 · SPEC-030 (Absätze `sp_execute` und `RESETCONNECTION`) · SPEC-060“; `spec/spezifikation.md` · „`SPEC-057` trägt die SQL-Server-Nummer 8179 und hat nach dieser Regel den RETURNSTATUS 8179“
* befund: Der Diff ändert einen Absatz im Vertrag zum Query-Backend ([SPEC-022](../../spec/spezifikation.md#query-backend-vertrag-zu-spec-022), „RPC im Backend-Vertrag“, Satz zu Ablehnungen vor dem Backend) und in `SPEC-030` außerdem die Zeile `sp_execute` der RPC-Tabelle und den Satz „zum Fehlerfall (unbekanntes Handle …)“ (der Kopf nennt dort nur die Absätze `sp_execute` und `RESETCONNECTION`); `SPEC-022` steht weder im Kopf noch dieser Absatz in §3. Ebenso fehlen in §3: `docs/plan/spec-abdeckung.md` (regeneriert), `docs/plan/planning/welle-testserver-manuell.md`, die Roadmap, das Register `…/sp-execute-unbekanntes-handle-spec-057-widerspricht-messung/state.md`, `done/slice-adr-sp-execute-unbekanntes-handle.md` und `open/slice-massenladen-atomar-bauen.md` (Verweis-Nachzug, `956c46fc`).
* Failure-Szenario: Ein Leser des Plans (oder der Planner beim Merge mit dem Slice `slice-massenladen-atomar-bauen`, der dieselbe Spezifikation und dieselbe Slice-Datei berührt) hält `SPEC-022` für unberührt; der Merge-Konflikt in drei dieser Dateien ist bereits eingetreten (F-6).
* verifizierbar: ja (Diff gegen §3)
* klasse: Plan-Tabelle ohne berührte Datei

### F-2 LOW — KDoc von `ErrorMapper.code` kennt nur eine eigene Antwort für Codes mit Handle im Text

* kategorie: LOW
* quelle: `AGENTS.md` §3.7 (Abgrenzung muss stimmen)
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/protocol/error/ErrorMapper.kt` · „Ein Code, dessen Text die Nummer eines Handles nennt ([ErrorRule.namesHandle]), hat eine eigene Antwort ([releaseAnswer])“
* befund: Der Code `TDS_SIM_UNKNOWN_PREPARED_HANDLE` hat jetzt zwei eigene Antworten (`releaseAnswer` für die Batch-Freigabe und `executeUnknownHandle` für `sp_execute`); der Satz nennt nur die erste.
* verifizierbar: nein
* klasse: Kommentar nennt nicht alle Fälle nach Zuwachs

### F-3 LOW — Nutzer-Doku: „nur 18456 und 8179 sind am echten SQL Server beobachtet“ in einer vom Slice geänderten Zeile

* kategorie: LOW
* quelle: `AGENTS.md` §5 Regel 5 (Ist-Zustand), §3.8
* pfad: `docs/user/referenz-konfiguration.md` · „die Nummern sind Festlegungen, nur 18456 und 8179 sind am echten SQL Server beobachtet“
* befund: Die Zeile steht im Diff (State-Ausnahmen ergänzt), der Nebensatz bleibt; die Tabelle darunter führt 2714, 2705, 4902 und 2627, die M-19 und der Mitschnitt-Lauf am echten Server messen. Die Aussage stammt nicht aus diesem Slice, steht aber in einer von ihm angefassten Zeile.
* verifizierbar: ja (Grep gegen M-19)
* klasse: Bestandsaussage in angefasster Zeile widerspricht Messung

### F-4 INFO — RETURNSTATUS 8179: von ADR-0045 Offen 1 gedeckt, keine Klarstellung nötig

* kategorie: INFO
* quelle: [ADR-0045](../plan/adr/0045-sp-execute-unbekanntes-handle-ergibt-fehler-8179-state-4.md) Entscheidung 4 und Offen 1
* pfad: `src/test/kotlin/io/github/pt9912/tdswire/protocol/dispatch/PreparedHandlesDispatchTest.kt` · „assertEquals(3, session.tokens(it).size, \"$it\")“
* befund: Die Antwort hat drei Tokens (ERROR, RETURNSTATUS 8179, DONEPROC) statt der im ADR-Wortlaut genannten zwei; die Wahl folgt der bestehenden Regel für Nummern unter 50000 und dem gemessenen `sp_unprepare`, ist in Spezifikation, Matrix, Nutzer-Doku und den Tests als Setzung gekennzeichnet. Die Closure-Notiz §7 muss die Zuordnung Messung | Setzung je Feld tragen (Closure-Trigger §5); gegen die Messung fällt sie, wenn der echte Server bei `sp_execute` keinen RETURNSTATUS sendet (Re-Evaluierungs-Trigger der ADR).
* verifizierbar: nein
* klasse: Setzung innerhalb der offenen Frage einer ADR

### F-5 INFO — Matrixzeile „Handle-Verwaltung je Sitzung“ unverändert, DoD Punkt 3 nennt sie

* kategorie: INFO
* quelle: Slice §2 Liefer-Punkt 3
* pfad: `docs/plan/kompatibilitaetsmatrix.md` · „Handle-Verwaltung je Sitzung (Begrenzung, unbekanntes Handle, Sitzungsende)“
* befund: Die Zeile ist im Diff nicht berührt; sie nennt keine Fehlernummer und keinen State für das unbekannte Handle und steht damit nicht im Widerspruch. Ob „führt 8179/State 4“ für sie erfüllt ist, entscheidet der Verifier. Das neue Zitat in der Zeile `sp_execute` (`ErrorMapperTest > sp_execute mit unbekanntem Handle ist ERROR 8179 State 4 in der Form RPC`) trifft den Testnamen wörtlich; `make matrix-zitate` habe ich nach dem Sensor-Zuschnitt nicht gefahren.
* verifizierbar: ja (`make matrix-zitate`, Rolle des Verifiers)
* klasse: DoD-Wortlaut gegen Diff

### F-6 INFO — Merge-Probe gegen `main` `a830d6d1`: vier Konflikte, nur in Dokumenten

* kategorie: INFO
* quelle: Merge-Probe (`git merge-tree`)
* pfad: `docs/plan/planning/in-progress/roadmap.md` (zwei Hunks: Zeile „In Arbeit“ und Drift-Log), `docs/plan/planning/welle-testserver-manuell.md` (zwei Zeilen mit Verweisen auf beide Slices), `docs/plan/planning/open/slice-massenladen-atomar-bauen.md` (Start-Zeile: `in-progress/…` gegen `../done/…`), `spec/spezifikation.md` (Änderungsprotokoll, beide Seiten fügen eine Zeile an derselben Stelle ein)
* befund: Alle vier sind Verweis- und Zeilenkonflikte, keine inhaltlichen. `main` hat `RequestLoop.kt`, `ResponseRun.kt` und weitere Dateien im Quelltext geändert (Transaktionszustand); der automatische Merge der Quelltexte lief ohne Konflikt, ein Bau des gemergten Stands war nicht Teil dieses Laufs. Die Änderungen von `main` an `ResponseRun.kt` berühren `errorAnswer` nicht.
* verifizierbar: ja (Merge, `make gates` nach dem Merge)
* klasse: Merge-Konflikt in Verweiszeilen

### F-7 INFO — Setzung der leeren ERROR-Felder gegen den nächsten gemessenen Fall

* kategorie: INFO
* quelle: `AGENTS.md` §3.8; [ADR-0045](../plan/adr/0045-sp-execute-unbekanntes-handle-ergibt-fehler-8179-state-4.md) Entscheidung 4
* pfad: `spec/spezifikation.md` · „die leeren Felder des ERROR (Servername, Prozedur, Zeile 0)“
* befund: Beim nächsten gemessenen Fall (`sp_unprepare`, Mitschnitte `zusatz-unprepare-*.messung`) trägt der ERROR Servername, Prozedur `sp_unprepare` und Zeile 1; `SPEC-060` führt das als „bekannte Abweichung, nicht angeglichen“. `SPEC-057` führt die leeren Felder als Setzung ohne diesen Hinweis auf den gemessenen Nachbarfall. Die ADR überlässt die Felder der Spezifikation; die Aussage ist gekennzeichnet und breiter als der Beleg nicht.
* verifizierbar: nein
* klasse: Setzung gegen nächsten Messfall ohne Hinweis

### F-8 INFO — `ListenerAbortTest > der Listener trennt nach CANCEL_TIMEOUT, wenn der Abbruch die Verbindung nicht beendet`: Ursache ist eine Ordnungswette, nicht die Wanduhr (für `BEO-TDS/wanduhr-tests-flackern-unter-last`)

* kategorie: INFO
* quelle: Maintainability; Register `BEO-TDS/wanduhr-tests-flackern-unter-last`
* pfad: `src/test/kotlin/io/github/pt9912/tdswire/transport/listener/ListenerAbortTest.kt` · „awaitWaiting(delayer, cancelTimeout)“ gefolgt von „assertEquals(1, handler.aborts.get())“; `src/main/kotlin/io/github/pt9912/tdswire/transport/listener/ManagedConnection.kt` · „scope.launch { action() }“
* befund: Aus dem Code gelesen, nicht gemessen: `expireGrace` startet den angemeldeten Abbruch asynchron im Scope der Verbindung (`scope.launch`), die Ablauf-Coroutine des Listeners wartet danach sofort auf `cancelTimeout`; der Test kehrt von `awaitWaiting(…, cancelTimeout)` zurück, sobald diese Frist anhängt, und liest `handler.aborts` ohne Synchronisation mit dem Lauf der Abbruch-Coroutine. Unter Last kann der Zähler noch 0 sein (die gemeldete „Erwartung 1 statt 0“). Das ist keine Wanduhr-Abhängigkeit (die Fristen laufen in gesteuerter Zeit, `ManualDelayer`; `eventuallyGuarded` und `withTimeout(hangGuard)` sind nicht beteiligt) und nicht der Test, den `slice-wanduhr-tests-lastunabhaengig` stabilisiert hat. Schwestertests: `… ruft den Abbruch nach der Frist …` liest `aborts` erst nach `shutdown.await()`, das den Lauf des Abbruchs voraussetzt (`aborts.incrementAndGet()` vor `aborted.complete`), und ist davon frei; `… ein Handler, der den Abbruch ignoriert …`, `… ohne angemeldeten Abbruch …` und `… sieht den Beginn des Beendens` lesen keinen Zähler des Abbruchs nach einer Frist; kein Schwestertest trägt dieselbe Schwäche. Die Hypothese im Register („die Reihenfolge, nicht die Wanduhr“) passt zu diesem Befund.
* verifizierbar: ja (Wiederholung des einen Tests unter Last)
* klasse: Zähler nach asynchronem Start ohne Wartepunkt gelesen

### F-9 INFO — Stempel `tests` im Arbeitsbaum nicht schreibbar

* kategorie: INFO
* quelle: [ADR-0024](../plan/adr/0024-lokale-stempel-zwei-klassen.md) (lokale Stempel)
* pfad: `testserver/build/classes/kotlin/main/META-INF/io.github.pt9912_testserver.kotlin_module`
* befund: `make gates FORCE=1` meldet „Eingangspfade nicht sauber“ und schreibt den Stempel nicht, weil im Arbeitsbaum ein ignoriertes Build-Verzeichnis von Gradle-Läufen auf dem Quellbaum liegt; die Gates selbst sind grün. Ohne Bezug zum Diff; wer den Stempel braucht, baut in einem sauberen Baum.
* verifizierbar: ja
* klasse: Nebenlauf im Arbeitsbaum verhindert Stempel

## Negativbefunde (geprüft, ohne Befund)

| Bereich | Ergebnis |
|---|---|
| `protocol/error/ErrorCode.kt`, `ErrorAnswers.kt` | geprüft, ohne Befund: `STATE_EXECUTE_UNKNOWN_HANDLE` nur dort und in `ErrorAnswers.kt`; KDoc trennt gemessen (Nummer, Class, State, Text für 999999) von gesetzt (andere Nummern); `N_HANDLE` und `TDS_UNKNOWN_HANDLE` ohne Rest; keine Suppression |
| `protocol/dispatch/RequestLoop.kt` | geprüft, ohne Befund: drei `refuse`-Aufrufer, `queryOutcome` unverändert, KDoc und Log-Marke stimmen mit Spezifikation; Zählung und Backend unberührt |
| `src/test/.../protocol/dispatch`, `protocol/error` | geprüft, ohne Befund: Handvektor für Handle 1, Struktur für 2, −1, 0, freigegeben, nach `RESETCONNECTION`, Gegenprobe State 8, Log-Marken getrennt; Mutationen M1, M3, M7, M8, E1, E2 rot |
| `src/integrator`, `testserver`, `referenztest-dotnet`, `tools` | geprüft, ohne Befund: keine Erwartung von 50009, `make flaeche` grün, `ErrorCode` bleibt `internal` |
| Spezifikation `SPEC-057`, `SPEC-060`, `SPEC-030` (Absätze, RPC-Tabelle), Fehlertabelle, State-Satz, Änderungsprotokoll | geprüft, ohne Befund bis auf F-1 und F-7: keine ADR-Kennung im Text der Spezifikation (`AGENTS.md` §3.4 gilt für `architecture.md`, die unberührt ist), Setzungen gekennzeichnet, kein `50009` außer der Aussage „gibt es nicht mehr“ |
| `docs/user/*` (fünf Dateien auf `sp_execute`, `8179`, `50009`, `TDS_UNKNOWN`, „nicht gebaut“, „Zielzustand“, „vorläufig“, „bisher“, „noch nicht“, „soll“, „seit Slice“ gegrept, `referenz-konfiguration.md` Abschnitte Prepared-Handles, Fehlercodes und Antwortformen gelesen) | geprüft, ohne Befund bis auf F-3; die Treffer in `testserver-manuell.md` und `einbettung-und-betrieb.md` betreffen andere Gegenstände (Soll-Schema, Anweisungsformen) |
| `docs/plan/kompatibilitaetsmatrix.md`, `docs/messungen/referenzabgleich.md` (M-6) | geprüft, ohne Befund bis auf F-5: Zitate stehen als Testnamen im Quelltext; M-6 löst den Hinweis ein und nennt die Tokenfolge als nicht gemessen |
| `docs/plan/spec-abdeckung.md` | geprüft, ohne Befund: Zeilen `SPEC-057` (8 Tests) und `SPEC-060` (15) vom Erzeuger neu geschrieben, Tests der Zeilen vorhanden |
| Haken, `harness/README.md`, Roadmap | geprüft, ohne Befund: keine Haken gesetzt, `harness/` unberührt, Roadmap-Zeile ohne Chronik |
| Commit-Struktur (`AGENTS.md` §3.3) | geprüft, ohne Befund: `9d48a029` reiner Move (0 Zeilen), Verweis-Nachzug getrennt, Commit-Nachrichten nennen `TDS-FA-04` und ADR-Kennungen |
| Hard Rules §3.1, §3.2, §3.5, §3.6 | geprüft, ohne Befund: nur `make`/`docker`, kein `@Suppress`, keine Accepted-ADR geändert, `config/detekt/detekt.yml` unberührt |
| Nachbesserung ohne zweiten Review | nicht anwendbar: erster Report zu diesem Slice; `7919d0f4` ist hier mitgelesen |

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 1 |
| LOW | 2 |
| INFO | 6 |

**Finding-Klassen dieses Laufs:** Plan-Tabelle ohne berührte Datei · Kommentar nennt nicht alle Fälle nach Zuwachs · Bestandsaussage in angefasster Zeile widerspricht Messung · Setzung innerhalb der offenen Frage einer ADR · DoD-Wortlaut gegen Diff · Merge-Konflikt in Verweiszeilen · Setzung gegen nächsten Messfall ohne Hinweis · Zähler nach asynchronem Start ohne Wartepunkt gelesen · Nebenlauf im Arbeitsbaum verhindert Stempel.

## Verdikt

**Merge-blockierend:** nein für den Code (kein HIGH, Gates grün, alle sechs gefahrenen Mutationen rot); F-1 (MEDIUM) ist eine Plan-Korrektur ohne Wirkung am Draht. Der Merge mit `main` verlangt die Auflösung der vier Dokumentkonflikte (F-6) und danach einen Lauf von `make gates`.

**Übergabe:** F-1 bis F-3 an den Implementer (Plan-Tabelle, KDoc, Zeile der Nutzer-Doku); F-4 und F-7 an die Closure-Notiz §7 (Zuordnung Messung | Setzung je Feld); F-5 an den Verifier; F-6 an den Planner beim Merge; F-8 als Eintrag für das Register `BEO-TDS/wanduhr-tests-flackern-unter-last` (Hypothese Ordnungswette, kein Auftreten des bereits stabilisierten Tests). Dieser Report ersetzt keine Verifikation.
