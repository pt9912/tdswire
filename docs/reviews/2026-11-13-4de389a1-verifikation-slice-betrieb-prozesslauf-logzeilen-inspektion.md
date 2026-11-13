# Verifikationsbericht: slice-betrieb-prozesslauf-logzeilen-inspektion (HEAD 4de389a1) und Welle-Beleg welle-betrieb-diagnose

* Rolle: Verifier (Modul 11), frischer Kontext. Gegenstand: [slice-betrieb-prozesslauf-logzeilen-inspektion](../plan/planning/done/slice-betrieb-prozesslauf-logzeilen-inspektion.md). Maßstab: DoD §2, Plan §3 samt Fortschreibung, [TDS-FA-09](../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit), [ADR-0020](../plan/adr/0020-logformat-und-observability-schnittstelle.md), [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md). Zugleich der repo-weite Beleg für den Closure-Trigger von [welle-betrieb-diagnose](../plan/planning/welle-betrieb-diagnose.md) §3 (Abschnitt „Welle-Beleg“).
* Stand: Zweig `worktree-agent-a364f3d0e80dbeeb6`, HEAD `4de389a1`, Basis `main` `e7ca8929` (Merge `5f11ff2c`); Arbeitsbaum am Ende sauber, `git diff --stat e7ca8929..HEAD -- src/main testserver` leer (kein Produktcode). Haken in der DoD: keine gesetzt (`grep -c '^- \[x\]'` ergibt 0), und ich setze keine.
* Eingang: Reviews [2026-11-13-5b2f9c60-review-slice-betrieb-prozesslauf-logzeilen-inspektion.md](2026-11-13-5b2f9c60-review-slice-betrieb-prozesslauf-logzeilen-inspektion.md) (F-1 bis F-9) und [2026-11-13-b5c4e276-review-2-slice-betrieb-prozesslauf-logzeilen-inspektion.md](2026-11-13-b5c4e276-review-2-slice-betrieb-prozesslauf-logzeilen-inspektion.md) (F-10 bis F-14). Die Nacharbeit `4de389a1` (F-10, F-11, F-12) hatte keinen eigenen Review und ist hier gezielt geprüft (Abschnitt „Nacharbeit 4de389a1“).

## Urteil

**Konform, ohne HIGH und MEDIUM.** Alle drei Liefer-Punkte und die Nachweis-Pflichten sind am Endstand belegt, soweit sie vor der Closure belegbar sind; jede der elf gefahrenen Mutationen fiel so aus, wie die Zusage es verlangt (zehn rot an der benannten Behauptung oder an ihrer Voraussetzung, die Mutation „`AdapterFailed` bei jeder Ablehnung“ wie gefordert grün). Alle Findings beider Reviews sind aufgehoben oder benannt; die Nacharbeit `4de389a1` trägt. Drei LOW und vier INFO stehen unten, keines blockiert. Offen sind die Aufgaben des Planners (Haken, Closure-Notiz §7 mit Zuordnung *Test | Festlegung*, Liste der Wartestellen, Laufzeit, Steering-Loop, Ausgänge von §6, Register) und, für die Welle, die Closure-Notiz der Welle (Abschnitt „Welle-Beleg“, Punkt iv).

## Eigene Läufe (Belege)

`make clean-build`, `make e2e`, `make gates`, `make docs-check` und `make skript-selbsttest` liefen je einmal im Vordergrund auf dem End-Stand `4de389a1` (Arbeitsbaum sauber). Logs und Zeitmarken liegen im Scratchpad der Sitzung. Rechner-Last beim Start der Läufe 5 bis 6; keine künstliche Last; ein Gradle-Lauf zugleich.

| Lauf | Ergebnis |
|---|---|
| `make clean-build` | Exit 0, **563 s** (`--no-cache`); 1402 Zeilen `PASSED`, 0 Testfälle `FAILED`; `TestserverLaufInspektionTest > Ein Lauf ueber den Prozess ist vollstaendig erfasst und nennt nichts Verbotenes()` `PASSED` (Abstand zur vorigen Zeile im Log 3,4 s, ohne Aussage unter Last); größte Posten: Stage `test` 182 s, Stage `build-jar` 183 s |
| `make e2e` | Exit 0, **330 s**, 8 Stufen grün: 1 `make smoke` 68 s, 2 `make laufzeittest` 87 s, 3 48 s, 4 43 s, 5 17 s, 6 16 s, 7 36 s, 8 15 s; `docs/plan/e2e-abdeckung.md` danach unverändert (Arbeitsbaum sauber) |
| `make gates` | Exit 0, **14 s**; alle Docker-Schichten kamen aus dem Cache (gleicher Baum wie beim Implementer und wie in meinem `clean-build`); Klasse `werkzeug` und Gruppe `stempel` über den Beleg übersprungen, Klasse `tests` lief (kein Beleg für den Hash) und schrieb den Stempel (`refs/notes/gates`, Note auf `4de389a1`: `stempel tests … gruen`); `d-check` 1421 Dateien 0 Befunde; `flaeche`, `gate-zuordnung` (16 Gates, 33 Fälle), `stempel-eingaenge` grün. Frischbeleg der Tests ist der `clean-build` |
| `make docs-check` | Exit 0, 1421 Dateien, 0 Befunde, 2 s; mit diesem Bericht im Baum 1422 Dateien, 0 Befunde |
| `make skript-selbsttest` | Exit 0, 3 s, alle Schichten aus dem Cache (der Diff ändert in `tools/e2e.sh` nur den Text der Klausel; Textgleichheit mit `docs/plan/e2e-abdeckung.md` Zeile 23 zusätzlich von Hand geprüft: 2947 Zeichen, gleich) |
| Mutationen | elf Läufe der Wegwerf-Stage `rev` (Kopie des `Dockerfile` im Scratchpad, `sed` im Container, Filter `TestserverLaufInspektionTest`), 50 bis 109 s je Lauf; `git status` nach den Läufen leer |

Gesamtlaufzeit der Sensor- und Mutationsläufe rund 30 Minuten (clean-build 563 s, e2e 330 s, elf Mutationen zusammen rund 10 Minuten, gates, docs-check und skript-selbsttest zusammen 20 s); dazu Lesen von Slice, Welle, zwei Reports, Code, Nutzer-Doku und das Schreiben dieses Berichts.

## DoD je Punkt (Endstand, Datei:Zeile)

Zeilen beziehen sich auf `src/test/kotlin/io/github/pt9912/tdswire/runtime/`: `T` = `TestserverLaufInspektionTest.kt`, `L` = `LogInspektion.kt`, `C` = `LaufClient.kt`, `S` = `ServerProcess.kt`, `R` = `RuntimeSupport.kt`.

| DoD-Punkt | Stand | Beleg |
|---|---|---|
| 1 Ein Lauf, vollständig erfasst | erfüllt, mit V-1 (LOW) | Kind-JVM über das Standard-`main` (`S` 31), `LOG_LEVEL=DEBUG` (`T` 78), `METRICS_INTERVAL_SECONDS=1` (`T` 76), Tabellen aus Beschreibungsdatei (`T` 79, `DESCRIPTION` 39 bis 60), Heap `-Xmx512m` (`T` 19, 121), Test-Umgebung wie `TestserverLogProcessTest` (`T` 67 bis 80). Reihenfolge in `T` 117 bis 137: Login abgelehnt (123), Login angenommen (125), Massenladen (`load` 140 bis 153: `INSERT BULK` und 0x07, 14 Schritte), SQL_BATCH (161), parametrisierte Anfrage `sp_executesql` mit Marke (162), unbekannte Tabelle (164), große Auswahl ohne Lesen und ATTENTION (169 bis 178), danach Auswahl auf derselben Verbindung (128), Schließen, SIGTERM (133), `gestoppt` (134). Wartestellen sind Signale: `awaitIndex` auf `metrics` (165 bis 173, 189 bis 197), Bytes (`LaufClient.answer`, `drainAnswer`), Zeile `gestoppt`; Zeit nur als Schranke (`S` 8 `WAIT_SECONDS`, `R` 27 `READ_MILLIS`). `BulkWire.message` liest jede Nachricht vor dem Senden gegen `BulkLoadReader` (`C` 76 bis 77). Mutation (c) rot an `DONE[ATTN]`, siehe unten |
| 2 Inspektion über alle Zeilen | erfüllt | (a) `L.plainLines` genau die zwei Zeilen des Testservers, `gestoppt` zuletzt (`T` 206 bis 207); jede `{`-Zeile muss gültiges JSON sein (`L` 99 bis 103); Pflichtfelder je Art (`L.required` 106 bis 127, `spanFields` 119 bis 127, `assertFields` 133 bis 146). (b) `KNOWN` (`L` 47), `ONLY_IN` (`L` 68 bis 96), `WITH_CONNECTION` (`L` 50 bis 62). (c) Verbotsmenge `T` 211 bis 215 (beide Passwörter, falsches Passwort, Kennung der Ablehnung, Parameterwert, `zz_` für alle Tabellen- und Spaltennamen, `select`, `insert`, `sp_executesql`, `nvarchar`, `@p`, Text der Fehlermeldung) plus `checkValueParts` (`T` 226 bis 233: Ganzes, Marke, `pbkdf2`, Salz und Hash in der Form der Datei, Hex, Base64URL ohne Padding); `L.assertNoneOf` (152 bis 156) sucht ohne Beachtung der Schreibweise und blendet nur `principal` der angenommenen Anmeldung aus (`S` 53 bis 55). Die Standardausgabe und die Fehlerausgabe des Kindes laufen zusammen in `output` (`S` 32, `redirectErrorStream`): die Inspektion deckt beides. Mutationen (a), (b), (d), (f), (g), (k) rot an der benannten Behauptung, (e) grün wie gefordert |
| 3 Spans und Metriken | erfüllt, mit V-4 (LOW) | `inspectSpans` (`T` 246 bis 273): `tds.connection` `login_rejected`/`handshake` und `ok`/`session` (249 bis 252); `tds.query` mit 14 Ladeschritten `ok` und fünf Anfragen `ok, ok, error, cancelled, ok` (254 bis 256, Arten 255, 257), eine Sitzung (258); `backend.execute` mit `request_id` gleich dem Ende von `tds.query` derselben Anfrage (259 bis 269); `request_failed` mit derselben `request_id` und `error_code` `TDS_SIM_UNKNOWN_OBJECT` (270 bis 272). `inspectMetrics` (275 bis 288): `requests_total` 19, eine Anmeldung, eine abgelehnte, ein Abbruch, `spans_open` 0, `backend_active` 0, `sessions_active` 0. Pflicht-Attribute je Span in `L.spanFields`. Mutationen (h), (i), (j) rot |
| Nachweis: `@spec`-Tags | erfüllt | `T` 111 bis 114: `SPEC-017`, `SPEC-018`, `SPEC-019`, `SPEC-052` (nur diese; der Test prüft sie wirklich). `docs/plan/spec-abdeckung.md` führt `TestserverLaufInspektionTest` bei diesen vier Kennungen, Zähler je +1 (17, 33, 12, 36); Kopf unverändert |
| Nachweis: Stage `test` grün, nicht in `smoke` | erfüllt | im `clean-build`-Log Stage `test` `PASSED`; `Dockerfile` Stage `smoke` nennt weiter nur `ServerSmokeTest` und `ZustandsereignisseProzessTest` (`Dockerfile` Zeile 102), Diff am `Dockerfile` leer |
| Nachweis: Wartestellen und Laufzeit | offen (Planner) | Wartestellen mit Signalen: unten am Code gelesen (Abschnitt „Wartestellen“), für §7. Laufzeit des Tests: 3,4 s Abstand im Log des `clean-build`, 4,7 s im Gradle-Bericht des Reviews; die Zahl steht in keiner Closure-Notiz, weil §7 leer ist |
| `make gates` grün | erfüllt | s. o. |
| Review, Report | erfüllt, Vermerk fehlt | zwei Reports; `4de389a1` hat keinen dritten: in §7 ist „Nachbesserung ohne zweiten Review“ zu führen (Commit, Grund, was stattdessen geprüft wurde: dieser Bericht, Abschnitt „Nacharbeit 4de389a1“) |
| Doku-Update | erfüllt | nur `docs/user/referenz-ereignisse.md` Zeile 78 geändert (Ist-Zustand, keine Chronik), Seite ganz gelesen, `testserver-manuell.md` §4 an der Stelle der Log-Zeilen gelesen (unverändert, widerspricht nicht: die Bereitschaftszeile steht dort nach den vier Ereignissen), `einbettung-und-betrieb.md` an den Stellen zu Log und Telemetrie gelesen (unberührt, widerspricht nicht); `tools/e2e.sh` Zeile 35 und `docs/plan/e2e-abdeckung.md` Zeile 23 textgleich |
| Closure-Notiz, Register, Ausgänge §6, Paarungen | offen (Planner) | §7 „Wird bei der Closure gefüllt“, §6 ohne Ausgänge, Register nicht fortgeschrieben (Vorschläge unten) |

### Wartestellen des Tests (für §7, am Code gelesen)

| Stelle | Signal | Schranke |
|---|---|---|
| `server.port()` | Zeile `tdswire: gestartet, Port <n>` | `WAIT_SECONDS` 60 s (`S` 8) |
| Schritte des Clients (`login`, `batch`, `parameterized`, `load`) | Bytes der Antwort, `readMessage` | 20 s je Lesen (`R` 27) |
| vor dem Cancel (`T` 165 bis 168) | Zeile `metrics` mit `requests_total` gleich der Zahl der beendeten Anfragen, `spans_open` 1, `backend_active` 0 | 60 s |
| Cancel (`T` 170 bis 175) | Zeile `metrics` mit `backend_active` 1 ab dem Index nach der Ruhezeile, dazu `spans_open` 3; danach ATTENTION | 60 s |
| `drainAnswer` (`T` 176) | Bytes bis zum Ende der Antwort mit `DONE[ATTN]` | 20 s je Lesen |
| `settle` (`T` 186 bis 198) | je Verbindung das Ende von `tds.connection`, dann `metrics` mit allen Anfragen und `spans_open` 0, dann `connection_closed` je Verbindung | je 60 s |
| `terminate`, `gestoppt` (`T` 133 bis 134) | Prozessende, Zeile `tdswire: gestoppt` | 60 s |

## Mutationen (selbst gefahren, eine je Lauf, nicht eingecheckt)

Je Mutation ein Lauf der Wegwerf-Stage `rev` (`Dockerfile` im Scratchpad, `sed` im Container mit anschließender Prüfung, dass die Mutation wirklich angewendet ist), Filter `TestserverLaufInspektionTest`. Alle Meldungen sind die erste Fehlermeldung des Gradle-Berichts (gekürzt).

| Mut. | Eingriff | Ergebnis | Meldung | Wanduhr |
|---|---|---|---|---|
| (a) | `duration_ms` fehlt in `TelemetryJson.PROPERTIES` | rot, an der Behauptung | `Pflichtfelder [duration_ms] fehlen in {… event=span_ended, … span=tds.connection, outcome=login_rejected …}` | 51 s |
| (b) | `RequestFailed.requestId` stets `null` | rot, an der Behauptung | `Pflichtfelder [request_id] fehlen in {… event=request_failed, … error_code=TDS_SIM_UNKNOWN_OBJECT}` | 50 s |
| (c) | `AbortedAnswer.conclude`: ATTENTION bestätigt mit leerer Antwort (`appended(ByteArray(0), …)` statt `AttentionAck.bytes`) | rot, an der Behauptung | `die Antwort endet mit DONE und ATTENTION` (`T` 177) | 51 s |
| (d) | `connection_id` im Ereignis `server_started` (Kontext mit `connectionId = 1`) | rot, an der Behauptung | `connection_id steht nicht in dieser Art: {… event=server_started, connection_id=1 …}` | 51 s |
| (e) | `AdapterFailed` bei jeder Ablehnung (`LoginTransitions.reject`, `failureType ?: "RuntimeException"`) | **grün, wie gefordert** (F-10 aufgehoben) | `PASSED`, `BUILD SUCCESSFUL` | 55 s |
| (f) | Prüfwert-Teil (Salz) als Base64URL ohne Padding `AQIDBAUGBwgJCgsMDQ4PEA` in `cause` von `request_failed` (Feld und Art erlaubt) | rot, an der Behauptung | `die Ausgabe nennt 'AQIDBAUGBwgJCgsMDQ4PEA':` (`L.assertNoneOf`) | 52 s |
| (g) | Passwort der Anmeldung (`falsch-pw-z3`) als `reason` von `login_rejected` | rot, an der Behauptung | `die Ausgabe nennt 'falsch-pw-z3':` | 51 s |
| (h) | `tds.connection` wird nicht beendet (`span.end(outcome)` entfällt in `HandshakeConnectionHandler.handle`) | rot, aber mittelbar (V-3) | `keine Ruhe vor dem Cancel: [… config_effective …]` (`T` 165 bis 168, `spans_open` bleibt über 1, Schranke 60 s) | 109 s |
| (i) | Ende von `backend.execute` mit `request_id + "x"` (Plan §2 Punkt 3) | rot, an der Behauptung | `backend.execute ohne tds.query: {… span=backend.execute … request_id=…fx …}` (`T` 262) | 52 s |
| (j) | `outcome` von `tds.query` bei ATTENTION ist `ok` statt `cancelled` (Plan §2 Punkt 3) | rot, an der Behauptung | `expected: <[ok … error, cancelled, ok]> but was: <[ok … error, ok, ok]>` (`T` 256) | 51 s |
| (k) | SQL-Text (`SELECT x FROM t`) in `cause` von `request_failed` (Plan §2 Punkt 2) | rot, an der Behauptung | `die Ausgabe nennt 'select':` | 52 s |

Nicht bestätigt, weil nicht gefahren: der „Server beantwortet ATTENTION nicht“ des Plans in der strengen Form (Antwort bleibt aus); (c) deckt den Teil „keine `DONE[ATTN]`-Antwort“ ab, die Schranke des Lesens (20 s) würde sonst den Test rot machen.

## Nacharbeit 4de389a1 (ohne eigenen Review, gezielt geprüft)

Diff: `LogInspektion.kt` (`WITH_CONNECTION` um `adapter_failed`, `connection_failed`, `tls_first_not_offered`, `tls_handshake_failed`), `TestserverLaufInspektionTest.kt` (`checkValueParts` um die Base64URL-Form ohne Padding, KDoc-Grenze), Plan §3 (Vermerk der Abweichungen auf den Ist-Stand). Kein Produktcode.

* **F-10.** Gegen `TelemetryEvent.kt` gelesen: die Unterklassen mit `context` sind `ConnectionOpened`, `ConnectionClosed`, `ConnectionFailed`, `LoginAccepted`, `LoginRejected`, `AdapterFailed`, `EncryptionNotOffered`, `TlsFirstNotOffered`, `TlsHandshakeFailed`, `RequestFailed`, `SpanEnded`: elf Arten, `WITH_CONNECTION` nennt genau diese elf. Mutation (e) belegt die Richtung (vorher rot an der falschen Stelle, jetzt grün); Mutation (d) belegt, dass `connection_id` in einer Art ohne Kontext weiter rot wird. Der Satz im KDoc „die `TelemetryJson` mit `connection_id` schreibt“ stimmt damit.
* **F-12.** Mutation (f) belegt das Base64URL-Salz in einem erlaubten Feld (rot, vorher grün: M-b2 des zweiten Reviews). Der Hash-Teil folgt derselben Abbildung (`urlForm` über beide Teile); nicht einzeln gefahren. Rest bleibt die benannte Grenze (Teilstück, auf mehrere Felder verteilter Wert, Iterationen).
* **F-11.** Plan §3 „Abweichungen der Erwartungswerte (am ersten Lauf abgeglichen; §2 nennt den Ist-Stand)“ stimmt mit §2 überein (14 Ladeschritte und fünf Anfragen, `gestartet` folgt dem laufenden Server, `metrics` ohne `connection_id`); Grep nach „zuerst“, „nicht vier“, „gehört dem Planner“ im Plan ohne Treffer. Eine andere Fundstelle des alten Wortlauts fand ich nicht; V-2 ist eine eigene.
* Die zwei Zeilen des Codes sind Daten in einer Menge und ein Aufruf mit bekannter Wirkung; die Mutationen (d), (e), (f) sind dafür der Beleg. Ein dritter Review ist damit nicht ersetzt: in §7 ist der Vermerk „Nachbesserung ohne zweiten Review“ zu führen (`BEO-TDS/nachbesserung-ohne-zweiten-review`, verkörpert).

## Plan-vs-Code-Diff (§3)

| Plan (§3 und Fortschreibung) | Code | Stand |
|---|---|---|
| neu `TestserverLaufInspektionTest.kt` mit `@spec` 017/018/019/052 | vorhanden, 298 Zeilen, vier Tags | wie geplant |
| Beschreibungsdatei zur Laufzeit (`SIMULATION_FILE`) | `file("sim.yaml", DESCRIPTION)` (`T` 79) unter dem Schlüssel `SIMULATION_DESCRIPTION_FILE` (`EnvKeys.kt` Zeile 17) | Code richtig, Plan nennt den Schlüssel falsch: V-2 (LOW) |
| `ServerProcess.awaitIndex`, `RawClient.receiveBufferBytes` | beide vorhanden (`S` 68 bis 82, `R` 98 bis 101), bestehende Aufrufe unverändert (Voreinstellung 0, neue Funktion) | wie geplant |
| `LaufClient.kt` (`BulkWire`, `LaufClient`) | vorhanden; `BulkWire.message` liest vor dem Senden gegen `BulkLoadReader` | wie geplant |
| `LogInspektion.kt` mit Pflichtfeldern, erlaubten Feldern, Prüfungen (a) bis (c) | vorhanden; zusätzlich `ONLY_IN` und `WITH_CONNECTION` (Zuordnung Feld zu Art), im Plan §3 Tabelle als „Tabellen der Pflichtfelder und der erlaubten Felder“ gedeckt | wie geplant, `ONLY_IN` ist Mehr aus Review F-1 |
| `docs/plan/spec-abdeckung.md`, `docs/user/referenz-ereignisse.md`, `tools/e2e.sh` Zeile 35, `docs/plan/e2e-abdeckung.md` Zeile 23 | alle vier berührt, Zeilen und Text wie genannt | wie geplant (F-2 aufgehoben) |
| Abweichung: Vorprüfung nach dem Code | im Plan vermerkt („entstand nach dem Code, am Lauf des fertigen Tests“) | benannt, F-5 aufgehoben; Klasse fürs Register |
| Abweichung: Erwartungswerte 14 Ladeschritte, fünf Anfragen | `loadSteps` kommt aus dem Lauf (`client.requests`), `T` 254 bildet die Erwartung daraus; am Mutationslauf (j): `ok` mal 14 vor `ok, ok, error, cancelled, ok`, also 19 Anfragen | belegt (14 aus 40 MiB Menge: 5243 Zeilen, 6 Nachrichten, 2 + 12 Anfragen, am eigenen Host mit `tcp_wmem` 4194304 und `tcp_rmem` 6291456 gerechnet); der Test fordert „14“ nicht, auf anderen Puffergrößen ändert sich die Zahl: V-5 (INFO) |
| `QUERY_TIMEOUT` 3600 | `T` 77 und 294, Beleg `SPEC-009` Maximum, F-13 des Reviews | wie fortgeschrieben |
| Datenmenge aus `/proc/sys/net/ipv4/tcp_wmem` und `tcp_rmem` | `upperLimit` (`T` 83 bis 87), Faktor 4, Grenze 128 MiB, Abbruch mit Meldung ohne Quelle (`T` 85, 119) | wie geplant (Annahme über Puffer: V-6) |
| `SO_RCVBUF` 4096 | `RECEIVE_BUFFER` (`T` 20, 124), `R` setzt `receiveBufferSize` vor dem Verbinden | wie geplant; die Antwort ist abgebrochen (`T` 178: weniger Bytes als die Menge) |
| `-Xmx512m` | `T` 19, 121; `S` setzt zuerst `-Xmx256m`, die späteren `jvmOptions` folgen | wie geplant; nicht in meinem Lauf gemessen, der Test lief grün |
| Schranken 60 s, 20 s | `S` 8 und `R` 27 | wie geplant |

## Review-Findings gegen den Endstand

| ID | Stand am Endstand |
|---|---|
| F-1 (MEDIUM, Prüfwert nur als Marke) | aufgehoben für die gefahrenen Formen: Ganzes, Marke, Salz und Hash als Base64, Hex, Base64URL; Mutation (f) rot, M15/M16/M-b3 der Reviews rot. Rest benannt (V-7, Grenze im KDoc) |
| F-2 (MEDIUM, Plan-Tabelle) | aufgehoben: §3 nennt `docs/user/referenz-ereignisse.md`, `tools/e2e.sh` Zeile 35, `docs/plan/e2e-abdeckung.md` Zeile 23; §8 nennt die drei Pfade; §2 sagt „nur die Seite `referenz-ereignisse.md` berührt“ |
| F-3 (LOW, Wanduhr-Frist 30 s) | aufgehoben: `QUERY_TIMEOUT` 3600 (`T` 77); Rest F-13 (INFO) bleibt als Vorsorge |
| F-4 (LOW, Plan-Wortlaut) | aufgehoben im Wortlaut §2 („folgt dem laufenden Server“, 14 Ladeschritte und fünf Anfragen); zweite Fundstelle F-11 aufgehoben |
| F-5 (LOW, Vorprüfung nach dem Code) | benannt im Plan; als Beobachtung vorzuschlagen |
| F-6 (LOW, zweite Fundstelle) | benannt im KDoc (`L` 14 bis 22); F-10 daran aufgehoben; kein Sensor, `BEO-TDS/zahl-an-mehreren-orten-ohne-sensor` |
| F-7 (INFO, Puffer der Umgebung) | bleibt: V-6 |
| F-8 (INFO, Teilstrings) | bleibt, vereinbar mit dem Zweck |
| F-9 (INFO, Messzahl) | benannt als Größenordnung im Plan (F-14 des zweiten Reviews, INFO) |
| F-10 (MEDIUM) | aufgehoben: elf Arten, Mutation (e) grün |
| F-11 (LOW) | aufgehoben |
| F-12 (INFO) | Base64URL-Form ergänzt, Mutation (f) rot; Rest als Grenze benannt |
| F-13 (INFO) | bleibt als Vorsorge; `QUERY_TIMEOUT` 3600 ist `MAX_REQUEST_TIMEOUT` (Review: `ConfigValidation`) |
| F-14 (INFO) | bleibt als Größenordnung |

## Nutzer-Doku und Aussagen (AGENTS.md §3.8, §5 Regel 5)

Vollständig gelesen: `docs/user/referenz-ereignisse.md` (alle 78 Zeilen), `docs/user/testserver-manuell.md` §4 „Das Log lesen“, `docs/user/einbettung-und-betrieb.md` an den Stellen zu Log, Senke, Redaktion und Telemetrie (Aufgaben 5, 6, 10, Tabelle der Typen), `docs/plan/e2e-abdeckung.md` Zeile 23 und `tools/e2e.sh` Zeile 35.

* Zeile 78 von `referenz-ereignisse.md` nennt den neuen Test, den Lauf („Massenladen, Abfragen, Fehler und Abbruch durch ATTENTION am Prozess“) und die drei Prüfungen; keine Chronik, keine Forensik, keine Planung (Regel 5 erfüllt). Die Aussage „das Fehlen von Passwort, Prüfwert, Parameterwert und SQL-Text über alle Zeilen eines Laufs“ ist an „eines Laufs“ gebunden und trägt für die bekannten Werte und Formen; die Grenze (bekannter Wert, Teilstück, verteilter Wert) steht im KDoc des Tests, nicht in der Nutzer-Doku: V-7 (INFO).
* `e2e-abdeckung.md` und `tools/e2e.sh` setzen den Zusatz in den Satz der Grenzen („Nicht belegt: … die Zeilen des Log-Adapters über die genannten Ereignisse hinaus … `TestserverLaufInspektionTest` in make test“): das ist die Stufe-1-Sicht (E2E belegt es nicht, die Stage `test` belegt es), nicht eine Behauptung der Stufe.
* `testserver-manuell.md` §4 und `einbettung-und-betrieb.md` widersprechen dem neuen Satz nicht (Bereitschaftszeile nach den vier Ereignissen; Spans nur ab `DEBUG`; Handlauf ohne Anfrage ausdrücklich als solcher bezeichnet).

## Findings des Verifiers

| ID | Kategorie | Befund | Pfad |
|---|---|---|---|
| V-1 | LOW | DoD §2 Punkt 1 sagt „die gesammelte Ausgabe steht in jeder Fehlermeldung“; im Code tragen nur die Wartestellen auf Zeilen (`T` 134, 167, 172, 174, 190, 192, 196) die Ausgabe. Ohne sie: `login` (123, 125), die Antworten des Clients (126 bis 129, 161 bis 164), die Zusicherungen zu `DONE[ATTN]` und zur Byte-Zahl (177, 178) und `terminate` (133). Bei Rot an einer dieser Stellen fehlt die Standardausgabe des Servers, die die Ursache zeigen würde. Zusage und Beleg gehen auseinander; der Wortlaut der DoD ist enger als der Test. Vorschlag: Wortlaut auf „an den Wartestellen auf Zeilen“ oder die Meldungen ergänzen. | Plan §2 Punkt 1; `T` 123 bis 178 |
| V-2 | LOW | Plan §1 (b) und §3 Tabelle nennen die Beschreibungsdatei `SIMULATION_FILE`; der Schlüssel des Testservers ist `SIMULATION_DESCRIPTION_FILE` (`testserver/kotlin/…/EnvKeys.kt` Zeile 17; der Test nutzt ihn, `T` 79). Wer dem Plan folgt, setzt einen Schlüssel, den der Server nicht liest. Plan-Wortlaut an Code angleichen. | Plan §1 (b), §3 Tabelle |
| V-3 | INFO | Mutation (h) (Ende von `tds.connection` fehlt) ist rot, aber nicht an der fehlenden Zeile `span_ended`, sondern an der Ruhe-Bedingung vor dem Cancel (`spans_open` bleibt über 1; die Schranke läuft 60 s, Wanduhr 109 s); die Meldung nennt die Ausgabe, die Ursache ist ablesbar. `settle` und `inspectSpans` würden danach ebenfalls rot. Der Beleg der DoD („die drei Spans liegen vor“) trägt, der Weg dahin ist mittelbar. | `T` 165 bis 168 |
| V-4 | LOW | `inspectSpans` prüft die Zuordnung `backend.execute` zu `tds.query` nur in einer Richtung für alle Anfragen (jedes Ende von `backend.execute` hat ein `tds.query`, `T` 259 bis 264) und in beiden Richtungen nur für die letzten fünf (`T` 265 bis 269, `singleOrNull` mit `assertNotNull`). Ein fehlendes Ende von `backend.execute` bei einem der 14 Ladeschritte bliebe unbemerkt; die Vorprüfung §3 (4) behauptet es für jeden Ladeschritt („hat ein Ende von `backend.execute` mit derselben `request_id`“). DoD-Wortlaut Punkt 3 („`request_id` gleich der des `tds.query` derselben Anfrage“) ist für die Ladeschritte nur halb belegt. Aus dem Code gelesen, nicht gefahren. | `T` 259 bis 269; Plan §3 Vorprüfung (4) |
| V-5 | INFO | „14 Ladeschritte“ im Plan (§2, §3) ist ein Wert des Hosts mit `tcp_wmem` 4194304 und `tcp_rmem` 6291456 (Menge 40 MiB, 5243 Zeilen, 6 Nachrichten). Der Test fordert die Zahl nicht, er leitet sie aus der Menge ab (`T` 129, 135, 254); auf einem Host mit anderen Obergrenzen ändert sich die Zahl, der Plan-Satz bliebe als Erwartungswert falsch. Die Zahl ist rechnerisch (aus der Menge) und am Lauf (19 Anfragen in der Meldung von Mutation (j)) bestätigt. | Plan §2, §3 |
| V-6 | INFO | Annahme über die Puffer der Umgebung: Menge gleich 4 mal die Summe der Obergrenzen aus `/proc/sys/net/ipv4`, im Test gelesen im Namensraum der Kind-JVM im Container; auf einem Host mit großen Puffern bricht der Test mit „Datenmenge … über der Grenze“ ab, rot ohne Fehler des Produkts (F-7 des ersten Reviews). Mein Host: 4194304 und 6291456, Test grün in `clean-build`, `gates` (Cache) und allen Mutationsläufen. Nicht belegt: ein Host mit anderen Obergrenzen; die Grenze 128 MiB ist Setzung. | `T` 83 bis 87, 118 bis 120 |
| V-7 | INFO | Grenze der Redaktionssuche (F-12): bekannte Werte und Formen; Teilstücke, auf mehrere Felder verteilte Werte und `Iterationen` bleiben unsichtbar. Die Nutzer-Doku Zeile 78 trägt „das Fehlen“ ohne diese Grenze. Aussage und Beleg liegen eng genug beieinander; Vorschlag nach Ermessen des Planners: in Zeile 78 „der bekannten Werte“. | `docs/user/referenz-ereignisse.md` Zeile 78 |

Eine DoD-Verletzung oder Spec-Lücke, die Tests und Review nicht sähen, fand ich nicht.

## Öffentliche Fläche von Hand (SPEC-059, Java-Lesart)

Kein Diff in `src/main` und `testserver` (`git diff --stat e7ca8929..HEAD -- src/main testserver` leer): keine neue Deklaration, kein neuer Typname, kein neuer Zugriff, keine geänderte Signatur. Die neuen Typen des Diffs liegen in `src/test` und sind `internal` (`BulkMessage`, `BulkWire`, `LaufClient`, `LogInspektion`, `TestserverLaufInspektionTest`); `RawClient` und `ServerProcess` sind `internal`, der neue Parameter `receiveBufferBytes` (Voreinstellung 0) und die neue Funktion `awaitIndex` ändern kein öffentliches Verhalten. `make flaeche` lief im `make gates` grün. Matrix: `docs/plan/spec-abdeckung.md` enthält `TestserverLaufInspektionTest` bei `SPEC-017`, `SPEC-018`, `SPEC-019`, `SPEC-052` (Diff gelesen: je eine Namenszeile und Zähler +1).

## Welle-Beleg: Closure-Trigger welle-betrieb-diagnose §3, Schritt 1

Maßstab: [welle-betrieb-diagnose](../plan/planning/welle-betrieb-diagnose.md) §3; Stand `main` `e7ca8929` plus dieser Zweig. Der Beleg ist erst mit dem Merge dieses Slice nach `done/` vollständig; er bezieht sich auf den Endstand `4de389a1`.

| Punkt | Aussage | Stand | Beleg |
|---|---|---|---|
| (i) | Alle Slices in `done/` außer diesem | **erfüllt** | `ls docs/plan/planning/done` gegen §4 der Welle: alle elf anderen Slices stehen in `done/` (`slice-adr-observability`, `slice-spec-nachzug-telemetrie`, `slice-betrieb-log-schnittstelle`, `slice-betrieb-log-adapter-testserver`, `slice-betrieb-telemetrie-verdrahtung-belegen`, `slice-listener-annahmeschleife-bei-erschoepften-dateien`, `slice-betrieb-zustandsereignisse`, `slice-betrieb-metriken-tracing`, `slice-oeffentliche-flaeche-verkleinern-und-belegen`, `slice-request-failed-mit-request-id`, `slice-einstellungen-in-typen-buendeln`); in `in-progress/` liegt nur `slice-betrieb-prozesslauf-logzeilen-inspektion`; `next/` ist leer; kein weiterer Slice der Welle in `open/` (dort `slice-qa-performance` als Nachbar, in §5 „Blockiert“) |
| (ii) | `make gates` grün einschließlich `make test` und des Prozesstests aus `slice-betrieb-zustandsereignisse` | **erfüllt** | `make gates` Exit 0 am Endstand (14 s, aus dem Cache); Frischbeleg: `make clean-build` Exit 0 mit 1402 `PASSED`, darunter die Tests der Stage `test` und `ZustandsereignisseProzessTest` (Stage `smoke` lief in `make e2e` Stufe 1 grün, 68 s); `make e2e` grün (8 Stufen, 330 s) |
| (iii) | „Das Mehr“: ein Lauf, der die Zusage belegt | **erfüllt**, mit den Grenzen V-1, V-4, V-7 | Teilaussagen unten |
| (iv) | Was für die Closure-Notiz der Welle fehlt | **offen** (nur benannt) | unten |

### (iii) Teilaussagen von „Das Mehr“

| Teilaussage | Erfüllt | Beleg |
|---|---|---|
| Ein Rohbytes-Lauf über den Testserver-Prozess mit Start | ja | `ServerProcess` startet `main` des Testservers, Port aus der Zeile `tdswire: gestartet` (`T` 121 bis 122), Ereignis `server_started` einmal mit Port (`T` 242) |
| Login angenommen | ja | `T` 125; `login_accepted` mit `principal` `alice` (`T` 240) |
| Login abgelehnt | ja | `T` 123; `login_rejected` mit `reason` `WRONG_PASSWORD` (`T` 241); `tds.connection` `login_rejected` in `handshake` (`T` 250) |
| SQL_BATCH | ja | `T` 128, 161, 164; 14 Ladeschritte und vier weitere als `sql_batch` (`T` 255) |
| parametrisierte Anfrage | ja | `T` 162 bis 163, `request_kind` `rpc` (`T` 255); Ergebnis mit einer Zeile |
| Fehler | ja | `T` 164, `request_failed` einmal mit `error_code` `TDS_SIM_UNKNOWN_OBJECT` und `request_id` gleich der des `tds.query`-Endes mit `outcome` `error` (`T` 270 bis 272); Verbindung bleibt offen (Auswahl danach, `T` 128) |
| Cancel | ja | ATTENTION am offenen Strom (`T` 169 bis 178), `DONE[ATTN]`, `outcome` `cancelled` (`T` 254), `aborts_total` 1 (`T` 281); Mutation (c) und (j) rot |
| Beenden | ja | SIGTERM, `server_stopped`, Zeile `gestoppt` zuletzt (`T` 133 bis 135, 207, 209) |
| erzeugt Logzeilen und Telemetrie (Ereignisse `metrics` und Span-Ende) | ja | Zeilen `metrics` im Takt 1 s, `span_ended` der drei Spans, `LOG_LEVEL=DEBUG`; geprüft in `inspectSpans` und `inspectMetrics` |
| findet weder Passwort noch Prüfwert noch Parameterwert noch SQL-Text | ja, für bekannte Werte und Formen | `T` 211 bis 215 und `checkValueParts`; Mutationen (f), (g), (k) rot; Grenze V-7 |
| jede Zeile trägt die Pflichtfelder (soweit vorhanden) | ja | `L.assertFields` über alle Ereignisse; Mutationen (a), (b), (d) rot; die Pflichtfelder je Art sind Festlegung aus `spezifikation.md` §4 und §5, nicht gemessen (KDoc `L` 14 bis 16) |
| die drei Spans liegen mit ihren Pflicht-Attributen vor | ja, mit V-4 | `inspectSpans`, `L.spanFields`; `tds.connection` zweimal, `tds.query` 19-mal, `backend.execute` je Anfrage; Mutationen (h), (i), (j) rot |

### (iv) Für die Closure-Notiz der Welle noch zu liefern (nur benannt)

* **Zwei Spalten *gemessen* und *Festlegung*:** die Aussagen der Welle sind aus den §7 der elf Slices und aus diesem Slice zu sammeln. Am Verifikationsstand dieses Slice gemessen: der Lauf am Prozess (Cancel am Serverprozess, `request_failed` mit `request_id`, 14 Ladeschritte plus fünf Anfragen, `backend_active` 1 bei offener Anfrage, Standard-`main` ohne Zeilen `kompatibilitaet:`), die Kindprozess-Läufe der Stage `test`. Festlegung, nicht gemessen: Pflichtfelder je Art und die Menge der Felder (`L`), `outcome`-Werte, die Zuordnung Feld zu Art (`ONLY_IN`), `request_kind` `sql_batch` für ein Bulk-Load-Paket, Setzung der Datenmenge (Faktor 4, Grenze 128 MiB), `QUERY_TIMEOUT` 3600. Im Plan als Zahl nicht gemessen: Heap-Bedarf (`-Xmx512m` Größenordnung).
* **Anschluss `slice-qa-sicherheit` (Sammler):** `slice-qa-sicherheit` liegt in `done/` und führt die Loginspektion über das Gesamtsystem als „nicht gemessen“ (§7, Zeile 149) mit Träger `slice-betrieb-log-schnittstelle`; die Closure der Welle nennt, dass die Gesamtinspektion nun am Prozess durch `TestserverLaufInspektionTest` ([TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit)) belegt ist, mit den Grenzen V-7 und der Beobachtung `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` (3×, hier keine neue Zahl).
* **Anschluss `slice-qa-performance` (Telemetrie als Dauer je Schicht):** `slice-qa-performance` liegt in `open/`, Start-Trigger „`slice-qa-kompatibilitaet` done und die Telemetrie (`SPEC-017`..`SPEC-019`) liefert Dauer je Schicht“ (§4 des Slice). Die Welle liefert `duration_ms` in `span_ended` aller drei Spans am Prozess (Pflichtfeld, Mutation (a) rot) und den Eingang für [TDS-QA-04](../../spec/lastenheft.md#tds-qa-04--performance); Last und Overhead der Telemetrie bleiben dort (§6 der Welle), die Dauer des Tests (3 bis 5 s) ist keine Messung.
* **Zuordnung der offenen Punkte (a) bis (h) der [ADR-0020](../plan/adr/0020-logformat-und-observability-schnittstelle.md) zu Trägern:** (a) Lesart Happy Path und (b) Satz in [ADR-0021](../plan/adr/0021-oeffentlicher-integrations-einstieg.md) sind Nutzerfragen (in der ADR als „für den Nutzer“ geführt; Annahme der ADR 2026-11-01 „offene Punkte bleiben offen“): Träger ist ein Nutzerentscheid, der in der Closure zu nennen ist. (c) Zeitstempelformat: Träger `slice-spec-nachzug-telemetrie` (UTC ISO-8601 im Adapter, `TelemetryJson`). (d) Perzentile in `metrics`: Träger `slice-betrieb-metriken-tracing` (Felder `metrics.request_latency_p50/95/99_ms` in `L` und in `referenz-ereignisse.md`). (e) Quelladresse im Ereignis: Träger die Anmeldedrosselung (`slice-qa-anmeldedrosselung`, [ADR-0016](../plan/adr/0016-anmeldedrosselung.md); dort „weiter offen“ → `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut`); das Modell trägt keine Quelladresse eines Clients (kein solches Feld in `TelemetryJson.PROPERTIES`). (f) Instanztrennung: Träger `slice-betrieb-log-schnittstelle` (benannte Grenze statt Feld, dort §7). (g) Namen und Form von Senke, Parameter und Sammler: Träger `slice-betrieb-log-schnittstelle` und `slice-oeffentliche-flaeche-verkleinern-und-belegen` (`SPEC-059`). (h) Speicherkopien und Gesamtinspektion: `BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen` und `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut`; die Gesamtinspektion am Prozess ist mit diesem Slice als Test gebaut, die Speicherkopien bleiben ohne Träger-Datei. Ich habe (c) bis (g) nicht Wort für Wort gegen die §7 der Träger-Slices abgeglichen, nur die genannten Fundstellen gelesen; der Planner prüft die Zuordnung dort.
* Zusätzlich für die Closure-Notiz der Welle: der Vermerk „Nachbesserung ohne zweiten Review“ und die Reihenfolge der Slices nach `slice-request-failed-mit-request-id`; die Datei `welle-betrieb-diagnose-results.md` gibt es noch nicht.

## Register (Vorschläge, nicht geschrieben)

Verzeichnis `docs/plan/planning/observations/BEO-TDS/` (168 Einträge) durchgesehen. Vorschläge:

| Klasse | Vorschlag |
|---|---|
| Redaktion sucht nur den bekannten Wert (F-1, F-8, F-12, V-7) | bestehender Eintrag `BEO-TDS/redaktion-sucht-nur-den-bekannten-wert` (Stand „offen — 2×, unter der Schwelle“): weitere Datei `evidence/slice-betrieb-prozesslauf-logzeilen-inspektion.md`; damit dritte Datei, die Schwelle ist erreicht, der Lese-Schritt der Welle-Closure entscheidet. Gegenmaßnahme dieses Slice (Menge der erlaubten Felder, `ONLY_IN`, Formen des Prüfwerts) als Sensor-Kandidat nennen |
| Zweite Fundstelle ohne Sensor (F-6, F-10) | bestehender Eintrag `BEO-TDS/zahl-an-mehreren-orten-ohne-sensor` (über der Schwelle, Entscheid beim Nutzer): weitere Evidenz; Instanz: `LogInspektion.kt` führt Felder und Zuordnung von `TelemetryJson.PROPERTIES` und `TelemetryEvent` als Kopie, F-10 ist das gefahrene Beispiel, an dem die Kopie falsch wurde |
| Wanduhr-Frist im Test (F-3, F-13) | neu, 1×, `BEO-TDS/frist-des-servers-unter-schranke-einer-wartestelle` (Name Vorschlag): Wartestelle mit Schranke über der Frist des Servers; Vorsorge `QUERY_TIMEOUT` 3600. Alternativ als Evidenz zu `BEO-TDS/zeitgrenze-des-tests-nahe-an-ungeklaerter-schwelle` (Stand „offen“), wenn der Planner dort denselben Gegenstand sieht |
| Vorprüfung nach dem Code (F-5) | neu, 1×, `BEO-TDS/vorpruefung-nach-dem-code` (Name Vorschlag): ein Plan sagt „Vorprüfung vor dem Bau“, das Ergebnis entsteht am fertigen Test; Ausgang hier folgenlos (keine Hypothese fiel) |
| Plan-Wortlaut nicht an Vorprüfung angeglichen (F-4, F-11, V-2, V-5) | bestehender Eintrag `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` (verkörpert, 41 Dateien): weitere Evidenz (F-4, F-11 vom Reviewer gefangen, V-2 und V-5 vom Verifier); Retirement-Check: Regel bleibt, die Klasse ist wiedergekehrt |
| Annahme über Puffer der Umgebung (F-7, V-6) | neu, 1×, `BEO-TDS/annahme-ueber-puffer-der-umgebung` (Name Vorschlag): Testmenge aus `/proc/sys/net/ipv4`, Grenze 128 MiB als Setzung, anderer Host nicht belegt; Trigger: erster Host mit anderen Obergrenzen oder rotes Gate auf anderer Maschine |
| Messzahl aus einem Erzeuger, der nicht im Repo liegt (F-9, F-14) | bestehender Eintrag `BEO-TDS/spike-prototyp-nicht-im-repo-messung-nicht-wiederholbar` (verkörpert, vier Belege): weitere Evidenz „1 225 136 Byte“ (Größenordnung, so gekennzeichnet; Regel gehalten, kein Verstoß) |
| Nachbesserung ohne zweiten Review (Commit `4de389a1`) | bestehender Eintrag `BEO-TDS/nachbesserung-ohne-zweiten-review` (verkörpert, 12 Dateien): weitere Datei; in §7 der Closure-Notiz der Vermerk mit Commit, Grund und Ersatzprüfung (dieser Bericht, Mutationen (d), (e), (f)) |
| Plan-Tabelle nennt die berührten Dateien nicht (F-2) | bestehender Eintrag `BEO-TDS/plan-tabelle-nennt-neue-datei-nicht` (verkörpert): weitere Evidenz, vom Reviewer gefangen, im Plan behoben |
| Eigene Klasse aus dieser Verifikation | `V-1`: DoD-Wortlaut „in jeder Fehlermeldung“ breiter als der Test; kein bestehender Eintrag trifft genau; Kandidat unter `BEO-TDS/dod-wortlaut-ueber-der-bauart` (Stand nicht gelesen) |

Wiederauftreten dieser Beobachtungen zählt nach den vorhandenen Dateien; ich habe keine Zahl gesetzt.

## Was ich nicht gefahren habe

`make spec-abdeckung` und `make matrix-zitate` (laut Tabelle in `harness/README.md` nicht Pflicht für den Verifier bei dieser Änderung); eine Wiederholung unter Last (keine künstliche Last, Wanduhr-Test: der Plan begründet die Lastunabhängigkeit aus der Bauart, die der Review bestätigt hat); die Mutation „Server beantwortet ATTENTION gar nicht“ in der strengen Form; einen Host mit anderen Puffergrößen.
