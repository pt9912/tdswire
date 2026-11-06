# Verifikation: slice-abbruch-ressourcen-shutdown — 2026-11-06

**Rolle:** Verifier (Modul 11), "Bauen wir es richtig?" gegen DoD (§2 des Slice) und Entscheidungen ([ADR-0027](../plan/adr/0027-abbruch-und-zuruecksetzen.md) Entscheidungen 1, 2, 2c, 4). Kein Review, keine Reparatur, keine Entscheidung des Nutzers gesetzt. Maßstab nach Nutzerentscheid vom 2026-11-06 "Spec ist Zielbild": der Code folgt `SPEC-044`, nicht umgekehrt.

**Gegenstand:** Zweig `slice-abbruch-ressourcen-shutdown`, HEAD `47d3020e` (Merge von `main` `7285c15b`; Implementierung `4e79d285`, Review `625484a1`, Nachbesserungen `bbebaf48`, `d3e07c00`, `ca6792c1`). Bezug: [TDS-FA-07](../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-QA-07](../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität), [ADR-0027](../plan/adr/0027-abbruch-und-zuruecksetzen.md). Reviews davor: [Code-Review 4e79d285](2026-11-06-4e79d285-slice-abbruch-ressourcen-shutdown.md), [Spec-Zielbild-Review](2026-11-06-f6ce18c7-spec-zielbild-abweichungen.md).

## Eigene Sensor-Läufe (am Stand `47d3020e`, sauberer Worktree)

| Lauf | Ergebnis |
|---|---|
| `make docs-check` | 874 Dateien, 0 Befunde, Exit 0 |
| `make gates FORCE=1` | Exit 0, 27 s. **Cache-Falle G1:** 72 Docker-Schichten `CACHED`; der Lauf belegt Stempel, Host-Selbsttests (stempel-selbsttest 118, stempel-eingaenge 95, stempel-abbild 99, stempel-gruppe 64, gate-zuordnung-selbsttest 33 Fälle), baseline-verify (54 Dateien) und Doku, nicht frische Gradle-Arbeit |
| `make clean-build` (Frischbeleg, `--no-cache`: Jar, Tests, detekt) | Exit 0, 446 s, `BUILD SUCCESSFUL` in allen Stages (darunter 1 m 49 s und 2 m 17 s für Tests), 972 Zeilen `PASSED`, 0 rote Tests (der eine Treffer auf `FAILED` ist der Testname `FAILED_CLOSE_CONNECTION`) |
| `make e2e` | Exit 0, "gruen (7 Stufen, 291 s)": Stufe 1 63 s, 2 78 s, 3 45 s, 4 40 s, 5 14 s, 6 15 s, 7 36 s; Stufe 1 (`make smoke`) trägt jetzt `TDS-FA-07` und `TDS-QA-02` |
| Mutation M1 (Kopie von `HEAD` im Scratchpad, `docker build --no-cache-filter test`, gezielte Tests): `ManagedConnection.busy` ohne Nachrichtenrest (`running` statt `running \|\| partial`) | **rot**, 4 von 32: `ListenerPartialMessageTest > eine unvollstaendige Nachricht haelt die Verbindung bis zum Ablauf der Frist offen`, `… > endet die unvollstaendige Nachricht vor der Frist, endet die Verbindung`, `ShutdownWiringTest > eine halbe Nachricht haelt die Verbindung offen und erhaelt nach dem Rest TDS_SHUTDOWN`, `… > eine Nachricht ohne Ende wird beim Ablauf der Frist getrennt` (SPEC-044 Nr. 2 und Schlussabsatz) |
| Mutation M2: `TdsListener.awaitConnections` `abandon` → `end` (Verbindung wird nach Frist plus `CANCEL_TIMEOUT` nicht aufgegeben) | **rot**, 1 von 32: `ListenerAbortTest > ein Handler, der den Abbruch ignoriert, haelt das Beenden nicht ueber die Obergrenze` (SPEC-044 Nr. 5) |
| Mutation M3: `RequestLoop.answer` `gate.begun ->` durch `false ->` ersetzt (kein `TDS_SHUTDOWN`) | **rot**, 6 von 32: drei `ShutdownDispatchTest` und drei `ShutdownWiringTest` (SPEC-044 Nr. 3) |
| Mutationen | nur in Kopien (`git archive`) im Scratchpad, der Worktree ist unverändert (`git status` sauber vor dem Report) |

Nicht gefahren: `make test` des ganzen Bestands einzeln (läuft in `clean-build`), `make smoke` einzeln (läuft in `e2e` Stufe 1 und `clean-build`), `make kompatibilitaet` einzeln (Stufen 4 bis 7 von `e2e`). Die Mutationen liefen gegen die gezielten Klassen (`ListenerPartialMessageTest`, `ShutdownDispatchTest`, `ListenerAbortTest`, `ShutdownWiringTest`, `ListenerShutdownTest`), nicht gegen den ganzen Bestand; die Rot-Zahlen sind deshalb "von 32".

## Prüfpunkt 1: SPEC-044 Nr. 2, 3, 5 im Code

| Nr. | Zusage | Code | Beleg |
|---|---|---|---|
| 2 | Halbe Nachricht bleibt bis Fristablauf offen | `MessageReader.onMidMessage` meldet den Rest, `Connection.messageInProgress`, `ManagedConnection.busy = running \|\| partial`; `TdsListener.shutdown` beendet nur `filterNot { busy }`; `expireGrace` trennt ohne angemeldeten Abbruch sofort | M1 rot (4 Tests), selbst gefahren; `clean-build` grün |
| 3 | Jede nach dem Beginn vollständig werdende Anfrage erhält `TDS_SHUTDOWN` | `RequestLoop.answer`: `gate.begun -> refuse(TDS_SHUTDOWN)`; nach regulärem Ende bleibt die Verbindung "läuft" (`announce`), solange `gate.begun`; Schließen erst nach ERROR und DONE | M3 rot (6 Tests), selbst gefahren |
| 5 | Rückkehr nach höchstens Frist plus `CANCEL_TIMEOUT`, ohne Zugabe | `awaitConnections`: `delay(grace)`, `expireGrace`, `delay(cancelTimeout)`, `abandon` aller verbliebenen; `HANDLER_EXIT_WAIT` ist aus dem Code entfernt (Grep: nur noch in historischen Review-Reports) | M2 rot (1 Test), selbst gefahren; die Zeit ist gesteuert, der Test belegt die Ordnung der Fristen, nicht reale Dauer unter Last (im Slice §6 "Zeitverhalten" geführt) |

Befund: erfüllt. Eine Rest-Schwäche der Nr. 2 ist im Code selbst benannt (KDoc `MessageReader`): kommt eine Nachricht in einem Stück an, ist die Verbindung zwischen "Nachricht da" und "Anfrage läuft" nicht als offen gemeldet; das ist ein Fenster im Augenblick des Beginns, keine Verletzung der Zusage (die Anfrage ist vor dem Beginn nicht "vollständig nach dem Beginn").

## Prüfpunkt 2: die benannten Reste

| Rest | Slice §6 | Spec | Nutzer-Doku | Befund |
|---|---|---|---|---|
| SPEC-044 Nr. 3 Satz "Von den übrigen Anweisungen eines Batches läuft nach dem Beginn nichts mehr": nicht gebaut | geführt (Risiko 3: "nicht gebaut … der Server kennt sie nicht"), Ausgang "offen bis zur Closure" | `SPEC-044` Nr. 3 "Nicht gebaut: …" mit Grund, Änderungsprotokoll 2026-11-06 "mit einer Ausnahme" | nicht gesondert | als Code-Schuld geführt, die Zusage steht unverändert; kein Abschwächen |
| Handshake-Verhalten | geführt (Risiko 3: "Offen bleibt der Handshake … gebaut ist Nr. 2 wörtlich") | `SPEC-044` Nr. 2 deckt die Verbindung ohne laufende Anfrage wörtlich, keine Aussage zum Handshake | nicht gesondert | als offene Frage im Slice geführt; in Spec und Doku nicht als offen gekennzeichnet (siehe V-1) |
| Spannung Nr. 2 / Nr. 3 (eine ruhende Verbindung endet beim Beginn; ein sequentieller Client erhält nie `TDS_SHUTDOWN`, sondern ein Schließen) | **nicht geführt** | **nicht gekennzeichnet**: Nr. 3 verspricht `TDS_SHUTDOWN` für "eine nach dem Beginn eingetroffene Anfrage", Nr. 2 beendet die Verbindung vorher | `docs/user/einbettung-und-betrieb.md`: "ein Client, der danach sendet, sieht ein Schließen" (sachlich richtig, aber ohne Hinweis auf die Spannung zur Zusage) | **offen, siehe V-1** |

## Prüfpunkt 3: Review-Findings und Spec-Zielbild-Review

| Finding | Soll | Befund am Text |
|---|---|---|
| Code-Review F-1 (MEDIUM, Doku sagt unbedingt "nach dem Beginn erhält `TDS_SHUTDOWN`") | Reichweite benannt oder Code nachgezogen | behoben: Code liefert jetzt für jede danach vollständig werdende Nachricht einer offenen Verbindung; Nutzer-Doku nennt die Ausnahme (ruhende Verbindung endet sofort); Rest als V-1 |
| F-2 (MEDIUM, halbe Nachricht) | Code an `SPEC-044` | behoben, M1 rot |
| F-3 (LOW, RST-Prozesstest trug `@spec SPEC-029` ohne zu diskriminieren) | Tag entfernt | behoben: KDoc nennt "trägt deshalb kein `@spec`" |
| F-4 (LOW, `refusal()` im zweiten Sperrfenster) | Grund aus derselben Sektion | behoben: `lastRefusal` wird in `admit` unter der Sperre gesetzt (Annahme in einer Coroutine, im KDoc benannt); ein Test für die Rennlage fehlt weiterhin (nicht verlangt) |
| F-5 (LOW, Matrix-Zeile TCP-Listener) | nachgezogen | behoben: Zeile nennt Abbruch nach Frist, Trennen nach `CANCEL_TIMEOUT`, Handler ohne Abbruch, `MAX_CONNECTIONS`, Disconnect; Zitate laufen im Gate (`gates`) |
| F-6 (LOW, `docker stop` in `testserver-manuell.md`) | nachgezogen | behoben: Satz mit "nicht gemessen" |
| Spec-Review F-1 (HIGH, 250-ms-Zugabe in `SPEC-044` Nr. 5) | Zugabe aus Spec und Code | erledigt: `SPEC-044` Nr. 5 ohne "Gebaut"-Satz; `HANDLER_EXIT_WAIT` aus `src/main` entfernt; M2 belegt die Obergrenze |
| Spec-Review F-2 (HIGH, `TDS_SHUTDOWN` auf "schon eingetroffene" Anfragen verengt) | Verengung aus Spec, Code erfüllt Zusage | erledigt, soweit die Verbindung offen ist: `SPEC-044` Nr. 3 und `SPEC-049` ohne Verengung; die Spannung zu Nr. 2 bleibt (V-1) |

## Prüfpunkt 4: zweite Fundstellen (Grep über das ganze Repo)

| Suchbegriff | Treffer außerhalb historischer Reports | Befund |
|---|---|---|
| `HANDLER_EXIT_WAIT`, "250 ms" | `src/test/…/ListenerAbortTest.kt:126` ("eine kurze Wartezeit auf das Ende der Handler (Festlegung, 250 ms Wanduhr)"); sonst nur `SPEC-053` und Tests der Anmeldedrosselung (anderer Gegenstand) | **V-2**: veralteter KDoc des Obergrenzen-Tests, beschreibt eine Zugabe, die der Code nicht mehr hat |
| "Gebaut:" | `SPEC-048` (anderer Gegenstand), Abschnitte 4/5 und `docs/user/einbettung-und-betrieb.md` (Telemetrie); in `SPEC-044`/`SPEC-049` kein "Gebaut:" mehr, nur "Nicht gebaut:" zum Batch-Satz | sauber |
| "Frist 0" | Matrix-Zeile TCP-Listener ("Frist 0 trennt sofort", für die Listener-Ebene ohne Abbruch richtig, die Zeile nennt den Abbruch nach Fristablauf zuvor) | sauber |
| "nicht gebaut" zu `MAX_CONNECTIONS`/`SHUTDOWN_GRACE` | keiner mehr in `spec/`, `docs/user`, Matrix, `harness/README.md`, `src` | sauber (Entfall der Zielzustand-Vermerke in `SPEC-034`, `SPEC-047`, Nutzer-Doku "Nicht einstellbar", "Nicht gebaut"-Liste) |
| `TDS_SHUTDOWN`-Einschränkung | `SPEC-044` Nr. 3, `SPEC-049`, Nutzer-Doku, Änderungsprotokoll: ohne Verengung; Nutzer-Doku nennt die ruhende Verbindung | sauber bis auf V-1 |
| Abschwächung/Zielzustand-Vermerke zu `SHUTDOWN_GRACE`, `MAX_CONNECTIONS` in `README.md`, `harness/README.md`, `AGENTS.md` | keine | sauber |

## Prüfpunkt 5: Setzungen ohne Eintrag in der Spec, Nutzerentscheid steht aus

| Setzung | Kennzeichnung am Text |
|---|---|
| Wertebereich `MAX_CONNECTIONS` 1 bis 100000 | `SPEC-034`: "konfigurierbar als ganze Zahl von 1 bis 100000, Festlegung, nicht gemessen" |
| Wertebereich `SHUTDOWN_GRACE` 0 bis 3600 s | `SPEC-047`: "von 0 bis 3600, Festlegung, nicht gemessen" |
| Abweisung ohne Antwortbytes | `SPEC-034`: "ist Festlegung und am Referenzclient nicht gemessen"; Nutzer-Doku: "Was der Referenzclient … sieht, ist nicht gemessen"; Slice §6 Risiko 2 "offen bis zur Closure" |
| Form `DONE[ATTN]` ohne ERROR im Beenden | KDoc `AbortedAnswer`: "Festlegung, nicht gemessen"; in der Spec nicht ausdrücklich (`SPEC-044` Nr. 4 sagt "wie bei ATTENTION") |
| Ende der Gegenstelle als Disconnect auch bei halb geschlossener Verbindung | `SPEC-029`-Absatz: "Festlegung, nicht gemessen" |

**Offener Nutzerpunkt (nicht von mir gesetzt):** Ob die Wertebereiche und die Abweisung ohne Antwortbytes (statt ERROR vor dem Login) als Festlegung in der Spec bleiben, ist entscheidungsbedürftig; die Kennzeichnung als Festlegung ist an `SPEC-034`, `SPEC-047` und in der Nutzer-Doku vorhanden. Die Nutzer-Doku-Tabelle (`1 bis 100000`, `0 bis 3600`) trägt das Wort "Festlegung" nicht selbst, verweist aber auf `SPEC-034`/`SPEC-047` (kein Befund).

## Prüfpunkt 6: Merge von `main`

- Elternteile `ca6792c1` (Zweig) und `7285c15b` (`main`); `git diff 7285c15b..HEAD` zeigt die Dateien des Slice, die Testserver-Dateien stehen unter `testserver/kotlin/…` und `testserver/test/kotlin/…` (nicht mehr `src/testserver`); kein Rest von `src/testserver` in `Dockerfile`, Skripten, `harness/`, Nutzer-Doku.
- `docs/plan/e2e-abdeckung.md` und `docs/plan/spec-abdeckung.md` sind gegenüber `main` erzeugt neu (Stufe 1 trägt `TDS-FA-07`, `TDS-QA-02`); `tools/e2e.sh` ändert eine Zeile.
- Kein Gate gelockert: gegenüber `main` keine Änderung an `config/detekt/detekt.yml`, `build.gradle.kts`, `Dockerfile`, `tools/mk/`, `tools/stempel/`; kein `@Suppress` im Bestand (`git grep`: keine Treffer). §3.5: keine ADR gegenüber `main` geändert.

## DoD (§2)

| # | Punkt | Befund | Haken im Slice |
|---|---|---|---|
| 1 | Disconnect in jedem Zustand, Lecktest, `MAX_CONNECTIONS` | Erfüllt. `RunEnd.Disconnected`, `hangUp`, Cancel einmal; `ConnectionLeakTest` (250 Verbindungen, 50 RST je Zustand, Handshake und Leerlauf), `ListenerLimitTest`, `DisconnectTest` grün im Frischbeleg; Wertebereich und Abweisung als Festlegung gekennzeichnet | `[x]`, vom Befund getragen |
| 2 | Geordnetes Beenden `SPEC-044` Nr. 1 bis 5 | Erfüllt für Nr. 1 bis 5 mit der Ausnahme "übrige Anweisungen eines Batches" (als Code-Schuld geführt); Nr. 2, 3, 5 mutiert und rot gesehen. Die Spannung Nr. 2/Nr. 3 ist offen (V-1) | `[x]`, getragen mit dem Vorbehalt V-1 |
| 3 | Stop des Bootstrap ruft den Ablauf; Rohbytes-Smoke, E2E-Stufe | Erfüllt. `Bootstrap` reicht `SHUTDOWN_GRACE`, `CANCEL_TIMEOUT`, `MAX_CONNECTIONS` durch; `e2e` Stufe 1 grün mit den Kennungen `TDS-FA-07`, `TDS-QA-02`; `e2e-abdeckung.md` zeigt sie | `[x]` |
| N | `make gates` grün | Erfüllt (Vorbehalt G1, ergänzt durch `clean-build` und `e2e`) | `[x]` |
| — | Doku-Update | Erfüllt (Spec, Nutzer-Doku, Matrix, `testserver-manuell`, Slice-Plan) | `[x]` |
| — | Review durchgeführt, Report liegt vor | Reports liegen vor (Code-Review und Spec-Zielbild-Review); Haken im Slice noch `[ ]` | `[ ]`, Sache des Planners |
| — | Closure-Notiz, Register, Risiko-Ausgänge (§6 alle "offen bis zur Closure"), Paarungen | Offen, wie im Slice: `[ ]`, keine Haken von mir gesetzt | `[ ]` (richtig) |

## Entscheidungs-Konformität gegen ADR-0027

| Entscheidung | Befund |
|---|---|
| 1 (Abbruch bei Disconnect, Cancel einmal) | konform: `AbortableRun`/`hangUp`, `DisconnectTest` (CANCELLING), Lecktest |
| 2 / 2c (Ende im Beenden nach `DONE[ATTN]`) | konform: `AbortedAnswer` `CancelCause.SHUTDOWN` → `connectionOpen = false`; `ShutdownDispatchTest > Der Ablauf der Frist bricht die Anfrage wie ATTENTION ab und beendet die Verbindung` grün |
| 4 (ERROR und DONE vollständig, danach Schließen) | konform: Antwort wird vor dem Übergang nach `CLOSED` gesendet; Herkunft (Setzung des Nutzers 2026-11-01) in `SPEC-044` und `SPEC-049` gekennzeichnet |

## Findings

| ID | Schwere | Befund | Maßnahme (Sache von Planner/Implementer) |
|---|---|---|---|
| V-1 | MEDIUM | Die Spannung zwischen `SPEC-044` Nr. 2 (eine ruhende Verbindung endet beim Beginn sofort) und Nr. 3 (eine nach dem Beginn eingetroffene Anfrage erhält `TDS_SHUTDOWN`) ist weder im Slice §6 noch in der Spec als offene Frage geführt. Gebaut sieht ein sequentieller Client nach dem Beginn nur ein Schließen; `TDS_SHUTDOWN` erreicht nur eine Verbindung mit laufender Anfrage oder halber Nachricht. Die Nutzer-Doku sagt es sachlich, ohne Bezug zur Zusage. Dasselbe gilt für den Handshake: im Slice geführt, in Spec und Doku nicht. Das ist keine Abschwächung der Zusage (Text unverändert), aber eine Code-Schuld bzw. offene Spec-Frage ohne Träger. | Risiko in §6 aufnehmen (Ausgang "weiter offen" mit Träger oder Nutzerentscheid), in `SPEC-044` als offene Frage kennzeichnen |
| V-2 | LOW | `src/test/kotlin/io/github/pt9912/tdswire/transport/listener/ListenerAbortTest.kt` (KDoc vor `ein Handler, der den Abbruch ignoriert, …`): "und eine kurze Wartezeit auf das Ende der Handler (Festlegung, 250 ms Wanduhr)"; die Zugabe ist aus dem Code entfernt, der Kommentar beschreibt Abwesendes (AGENTS.md §3.7, §3.8); zusätzlich ein Zeilenumbruch mitten in "(gesteuerte / Zeit)". | KDoc auf die geltende Zusage kürzen (Frist plus `CANCEL_TIMEOUT`, gesteuerte Zeit) |
| V-3 | INFO | `Offener Nutzerpunkt`: Setzungen (Wertebereiche, Abweisung ohne Antwortbytes) sind nicht vom Nutzer bestätigt; Kennzeichnung ist vorhanden. | Nutzerentscheid ausstehend |
| V-4 | INFO | Gezielte Mutationsläufe belegen die Tests, nicht reale Dauer unter Last (Fristen sind gesteuert); die Wanduhr-Anteile (`ServerSmokeTest`, `ShutdownWiringTest`) liefen im Frischbeleg grün. | im Slice §6 "Zeitverhalten" geführt |

## Urteil

Der Code erfüllt `SPEC-044` Nr. 2, 3 und 5 und die DoD-Punkte 1 bis 3 (Rot-gesehen M1 bis M3 selbst gefahren; `docs-check`, `gates FORCE=1`, `clean-build`, `e2e` mit 7 Stufen grün). Zwei Reste sind wie angegeben als Schuld geführt (Batch-Anweisungen) bzw. im Slice benannt (Handshake). Offen sind V-1 (Spannung Nr. 2/Nr. 3 nirgends geführt) und V-2 (veralteter KDoc); beide sind kein Code-Mangel. Die Haken für Review, Closure, Register, Risiko-Ausgänge und Paarungen stehen richtig auf `[ ]`.
