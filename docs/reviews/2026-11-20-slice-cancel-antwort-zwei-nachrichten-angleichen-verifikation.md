# Verifikationsbericht: slice-cancel-antwort-zwei-nachrichten-angleichen — 2026-11-20

**Rolle:** Verifier (Modul 11), frischer Kontext; DoD- und Entscheidungs-Konformität, Plan-vs-Code-Diff. Kein Review, kein Fix, keine DoD-Haken, kein Schließen, nichts nach außen.

**Stand:** Branch `slice-cancel-antwort-zwei-nachrichten-angleichen`, Spitze `47c61816` (Code-Stand `a3f9a1e7`, Quelltext seitdem unverändert; `b692ac69` und `689e64be`/`47c61816` nur Doku, Kommentare und Berichte); Arbeitsbaum sauber vor und nach allen Läufen. **Der Zweig ist hinter `main`** (siehe F-2): alle Läufe unten gelten für den Stand `47c61816`, nicht für den Stand nach dem Merge.

**Gegenstand:** Slice [slice-cancel-antwort-zwei-nachrichten-angleichen](../plan/planning/in-progress/slice-cancel-antwort-zwei-nachrichten-angleichen.md); [TDS-FA-07](../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität); [ADR-0054](../plan/adr/0054-antwort-auf-attention-in-zwei-nachrichten.md) und [ADR-0057](../plan/adr/0057-abbruch-beim-beenden-endet-im-paar.md) (beide `Accepted`), [ADR-0027](../plan/adr/0027-abbruch-und-zuruecksetzen.md), [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md); [SPEC-029](../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung), [SPEC-001](../../spec/spezifikation.md#spec-001--zustandsautomat-einer-verbindung), [SPEC-044](../../spec/spezifikation.md#spec-044--geordnetes-beenden); Messprotokoll [referenzabgleich.md](../messungen/referenzabgleich.md), Mutationsbeleg [mutationen-cancel-antwort-2026-11-20.md](../messungen/mutationen-cancel-antwort-2026-11-20.md); Reviews [Review](2026-11-19-slice-cancel-antwort-zwei-nachrichten-angleichen.md) und [Re-Review](2026-11-19-slice-cancel-antwort-zwei-nachrichten-angleichen-re-review.md).

## Selbst gefahrene Sensoren (End-Stand `47c61816`, Docker, unter der Messsperre, `available` vor jedem Lauf 16,3 bis 17,0 GiB, keine künstliche Last)

| Sensor | Ergebnis | Dauer |
|---|---|---|
| `make clean-build` | Exit 0 (Jar, Tests, detekt, `--no-cache`); 1499 Testfälle `PASSED`, keine `FAILED`-Zeile eines Tests; alle Gradle-Stages `BUILD SUCCESSFUL`; die in der Matrix zu ATTENTION, BULK_LOAD und Freigabe der Handles zitierten Testfälle (Zeilen 37, 39, 104) stehen alle als `PASSED` im Log | 568 s |
| `make vergleichslauf-d LAUF=mitschnitt` | Exit 0, grün, beide Teilläufe; 2022: 33 Felder mit gelisteter Abweichung, 2025: 27 (Messprotokoll: 33 und 27, 37 Regeln; Datei hat 37 Regeln); `cancel-streaming/01/{nachrichten,done}` gleich, `tokenfolge` bei 2025 gleich, bei 2022 nur die Ausnahme A-18 (SESSIONSTATE) | 60 s |
| `make vergleichslauf-d LAUF=live`, vier Läufe hintereinander (je zwei Teilläufe) | **drei Läufe grün, ein Lauf rot** (Lauf 2, Teillauf `live-2025`, Exit 2); Einzelheiten unten und F-1 | 293 s (je Lauf 68 bis 83 s) |
| `make kompatibilitaet` (Standard `KATEGORIE=Szenario`) | Exit 0, 13 von 13 Tests bestanden. **Die Kategorie `Szenario` enthält keinen Cancel-Test**; die Cancel-Tests liegen in `Synchronitaet` (Stufe 7), siehe nächste Zeile und F-6 | 13 s |
| `make kompatibilitaet KATEGORIE=Synchronitaet` (zusätzlich, einzeln) | Exit 0, 10 von 10 bestanden (`Cancel_waehrend_des_Streamings_…`, `CancellationToken_bricht_…`, `Cancel_und_Command_Timeout_kurz_hintereinander_…`, `Cancel_eines_beendeten_Befehls_…`) | 35 s |
| `make e2e` | Exit 0, „gruen (9 Stufen, 358 s)“; Stufen 1 bis 9 je grün (68, 83, 46, 14, 15, 15, 35, 13, 69 s); Stufe 3 enthält `Cancel_einer_laufenden_Anfrage_…` | 358 s |
| `make gates FORCE=1` | Exit 0; Stage `test` kam aus dem Layer-Cache des eben gelaufenen `clean-build` (`CACHED`), Frischbeleg der Tests ist `clean-build`; darin `docs-check` (`1661 Datei(en) geprüft, 0 Befund(e)`), `flaeche`, `kopplung-zahlen`, detekt, `referenztest-dotnet` (enthält `AbbruchAntwortBelegTests`), Selbsttests der Stempel | 34 s |
| `make docs-check` | Exit 0, 0 Befunde (vor Anlegen dieses Berichts; nach ihm siehe Schluss) | 2 s |
| Sieben Mutationen von Hand (Setz-Skript im Scratchpad, nicht im Repo), je ein Lauf, Wegwerf-Kopie des Stands `47c61816` | alle sieben rot, Zahlen gleich dem Beleg; Tabelle unten | 400 s (Ausgangslauf eingeschlossen) |

Gesamt rund 30 Minuten Wanduhr, davon rund 29 Minuten aktive Docker-Läufe. Größte Posten: `make clean-build` 568 s, Mutationen 400 s (acht Builds), `make e2e` 358 s (Stufe 2 mit 83 s, Stufe 9 mit 69 s, Stufe 1 mit 68 s), vier Live-Läufe 293 s. Die Mutationen warteten 1275 s auf die Messsperre (Verifier des Slice spaltenlaenge); diese Wartezeit ist nicht Arbeit.

**Aussagekraft von `clean-build` und `e2e` für diese Änderung.** `make clean-build` baut `src/` und `testserver/` frisch und belegt die JVM-Tests des Slice (alle Klassen des Abbruchs, `PairConclusionTest` und `ShutdownDispatchTest` stehen als `PASSED`). `make e2e` belegt den Serverprozess am Referenzclient: Stufe 7 (`Synchronitaet`) trägt die Cancel-Tests mit der neuen Form, Stufe 1 (`make smoke`) den Prozess-Stop mit laufender Anfrage. Stufe 7 belegt **nicht** die Form im Beenden (kein Referenzclient-Test stoppt den Server bei laufender Anfrage); diese Form tragen nur `ShutdownDispatchTest`, `ShutdownWiringTest` und die Setzung des Nutzers ([ADR-0057](../plan/adr/0057-abbruch-beim-beenden-endet-im-paar.md)).

### Live-Lauf und Zusatz (A-7), vier Läufe am 2026-11-20

| Lauf | Teillauf | Text der Referenz | Text des Servers | Ergebnis |
|---|---|---|---|---|
| 1 | 2022 | ohne Zusatz | mit „ Operation cancelled by user.“ | grün (Ausnahme) |
| 1 | 2025 | ohne Zusatz | mit Zusatz | grün |
| 2 | 2022 | ohne Zusatz | mit Zusatz | grün |
| 2 | 2025 | **mit Zusatz** | mit Zusatz | **rot**: „Schritt `cancel-mit-folgeabfrage` ist gleich, die Ausnahme dazu entfaellt“ |
| 3 | 2022 | ohne Zusatz | mit Zusatz | grün |
| 3 | 2025 | ohne Zusatz | mit Zusatz | grün |
| 4 | 2022 | ohne Zusatz | mit Zusatz | grün |
| 4 | 2025 | ohne Zusatz | mit Zusatz | grün |

Der Zusatz erscheint beim **Server in 8 von 8**, bei der **Referenz in 1 von 8** Teilläufen (zusammen mit den 13 Läufen des Messprotokolls: Server 21 von 21, Referenz 1 von 21). `Number 0, Class 11, State 0`, Zustand `Open` und die Folgeabfrage waren in allen acht Teilläufen gleich; die 24 übrigen Schritte der roten Teilläufe waren grün. Alle sonstigen Schritte des roten Teillaufs sind gleich oder gelistete Ausnahme; die Rot-Ursache ist allein die in [Slice](../plan/planning/in-progress/slice-cancel-antwort-zwei-nachrichten-angleichen.md) §1 und im Messprotokoll vorhergesagte Fragilität. Der Lauf nennt die Ursache selbst (`Ausnahme entfaellt`), er bleibt also nicht still.

### Mutationen (je eine Änderung am Quelltext von `47c61816`, Wegwerf-Kopie, `gradle :test` auf den genannten Klassen)

| Nr | Mutation (Ort) | Gefahrene Klassen | Wirkung (gemessen) | Beleg-Zahl |
|---|---|---|---|---|
| — | keine (Ausgangslauf) | `AttentionCancelTest`, `AttentionWireTest`, `ShutdownDispatchTest`, `PairConclusionTest`, `ShutdownWiringTest` | grün, 29 von 29, `BUILD SUCCESSFUL` | — |
| m1 | `AbortedAnswer.conclude`: ATTENTION wieder als einzelnes `DONE[ATTN]` (`appended(AttentionAck.bytes, …)`) | `AttentionCancelTest`, `AttentionWireTest` | rot, 8 von 12 (sieben in `AttentionCancelTest`, eines in `AttentionWireTest`) | 8 von 12, gleich |
| N1 | `AbortedAnswer.conclude`: `SHUTDOWN` wieder als einzelnes `DONE[ATTN]` | `ShutdownDispatchTest`, `ShutdownWiringTest` | rot, 3 von 14 (`Der Ablauf der Frist beendet die Antwort mit DONE ERROR und DONE ATTN und die Verbindung`, `SHUTDOWN_GRACE 0 bricht …`, `nach Ablauf der Frist wird die Anfrage abgebrochen …`) | 3 von 14, gleich |
| N5 | `SHUTDOWN` mit `connectionOpen = true` | dieselben | rot, 1 von 14 (`Der Ablauf der Frist …`, Zustand `CLOSED` erwartet) | 1 von 14, gleich |
| m2 | `inTwoMessages`: `failedDone(form, false)` statt `resultOpen` | `AttentionCancelTest`, `AttentionWireTest` | rot, 2 von 12 (`ATTENTION in STREAMING …`, `ATTENTION vor dem naechsten Token …`) | 2 von 12, gleich |
| m3 | `concludeWith`: zwei Aufrufe von `guarded` statt eines | dieselben | rot, 1 von 12 (`Das Paar geht unter einer Sperre hinaus`) | 1 von 12, gleich |
| m5 | `REGULAR_STOPS` ohne `CANCELLING` | dieselben | rot, 2 von 12 (`Ein ATTENTION waehrend CANCELLING …`, `ATTENTION vor dem naechsten Token …`) | 2 von 12, gleich |
| R12 | `concludeWith`: Ergebnis von `channel.send(second)` verworfen | `PairConclusionTest` | rot, 1 von 3 (`Scheitert die zweite Nachricht des Paares …`) | 1 von 3, gleich |

Grenze: Einzelläufe auf einem Host, Last nicht erhoben; „rot“ heißt mindestens ein Testfall rot, die Namen stammen aus dem Log. Die Mutationen R11, R13 und die Runden 1 und 2 des Beleges habe ich nicht wiederholt; der Re-Review hat R11 bis R13, N1 und N5 nachgefahren.

## Entscheidungs-Konformität

**[ADR-0054](../plan/adr/0054-antwort-auf-attention-in-zwei-nachrichten.md)** (gelesen gegen `AbortedAnswer.kt`, `AbortableRun.kt`, `HandshakeSession.kt`, `ResponseRun.kt`, Tests):

| Nr | Zusage | Befund |
|---|---|---|
| 1 | Im Stream: `DONE[ERROR]` (Befehl `0x00c1` bei offenem Ergebnis), dann `DONE[ATTN]` (`0x00fd`); bei wartendem Befehl beide ohne ERROR-Token; kein SESSIONSTATE | **Erfüllt.** `failedDone(form, resultOpen)` liefert `0x00c1` bzw. `0x00fd`; die Bytes stehen als `DONE_ERROR_OPEN`, `DONE_ERROR`, `DONE_ATTN` in den Tests, und `AbbruchAntwortBelegTests` (Referenztest, `PASSED` in `make gates`) liest sie aus den Aufnahmen beider Instanzen (`cancel-streaming`, `cancel-wartend`, `timeout-wartend`, `cancel-kurz-vor-timeout`, `cancel-kurz-nach-timeout`). Der Befehl der ersten Nachricht beim wartenden Befehl (`0x00fd`), den die ADR offen ließ, ist damit als gemessen (ein Lauf je Szenario und Instanz) eingetragen. m1 und m2 rot. |
| 2 | Zustand `CANCELLING`: ein Cancel; erste Nachricht ersetzt den regulären Abschluss; zweite unter derselben Sperre; danach `IDLE` | **Erfüllt.** `GuardedReply.concludeWith` schreibt beide unter einem `guarded(CLOSED_ONLY)`; m3 rot (`Das Paar geht unter einer Sperre hinaus`). |
| 3 | Lesart „doppelte DONE-Tokens sind verboten“ | **Erfüllt** und als Setzung der Lesart gekennzeichnet ([SPEC-001](../../spec/spezifikation.md#spec-001--zustandsautomat-einer-verbindung)/[SPEC-029](../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung): „Setzung der Lesart“); `ATTENTION vor dem naechsten Token verwirft den Rest und schreibt nur das Paar` (m2, m5 rot). |
| 4 | Race DONE/ATTENTION, Frist/ATTENTION, wiederholtes ATTENTION, `CANCEL_TIMEOUT`, Disconnect | **Erfüllt**, alle als Setzung gekennzeichnet (nicht gemessen). `complete()` bestätigt ein ATTENTION nach vollständiger Antwort einzeln (Test `nach dem abschliessenden DONE …`); ein verlierendes oder zweites ATTENTION bleibt im Eingang und wird nach `READY` einzeln bestätigt (`Ein ATTENTION waehrend CANCELLING …`, m5 rot; `Ein wiederholtes ATTENTION …`); Frist gegen ATTENTION (`QueryTimeoutTest`, zwei Fälle); `CANCEL_TIMEOUT` schreibt nichts (`CancelTimeoutTest`, `ShutdownDispatchTest … ignoriert`: `replies.size == 0`). |
| 5 | Folgen für die Regeln (Erwartung) | **Eingetreten** für A-13 (vier Regeln entfallen, `tokenfolge@2022` auf A-18 verkleinert, Lauf grün); **zur Hälfte widerlegt** für A-7 (Zusatz bleibt, siehe oben), wie das Messprotokoll es sagt. |

**[ADR-0057](../plan/adr/0057-abbruch-beim-beenden-endet-im-paar.md)** (Form beim Beenden): Code und Tests stehen so (`inTwoMessages(…, connectionOpen = false)`; N1 und N5 rot; `ShutdownWiringTest` liest erste und zweite Nachricht über den Socket; `CANCEL_TIMEOUT` ohne weitere Bytes). Die Aussage ist überall als Setzung des Nutzers vom 2026-11-20, am echten Server nicht gemessen, gekennzeichnet: [SPEC-044](../../spec/spezifikation.md#spec-044--geordnetes-beenden) Nr. 3 und 4, Änderungsprotokoll, `docs/user/einbettung-und-betrieb.md` Z. 644, KDoc von `AbortedAnswer`, `RequestLoop`, `HandshakeAction`, Test-KDoc. **Erfüllt.** Der Prozess-Stop-Test von `ServerSmokeTest` prüft nur die letzten 13 Byte und sagt das jetzt im KDoc (F-11 des Re-Reviews ist behoben, gelesen).

**Nutzerentscheidungen 2026-11-20:** (a) Paar beim Beenden: umgesetzt (oben). (b) A-7 als **eine** Live-Regel: `live-ausnahmen.tsv` führt genau eine Regel für `cancel-mit-folgeabfrage` (Referenz ohne, Server mit Zusatz), der DoD-Wortlaut im Slice nennt sie, Ursache als Folge-Slice. Die Regel sagt in der Spalte „Grund“ den geltenden Zustand, die Zahlen („alle Laeufe vom 2026-11-19 … Zahl im Messprotokoll“; „am 2026-11-06 teils mit ihm“) und die Fragilität („zeigt sie ihn, sind beide Texte gleich, die Regel entfaellt und der Lauf ist rot“); Messprotokoll (A-7-Zeile, Abschnitt „Antwort auf ATTENTION …“, „Grenze der Liste“) und Slice §1 sagen dasselbe. **Nichts davon ist breiter als das Gemessene**: jede Zahl ist an die 13 Läufe vom 2026-11-19 gebunden, „grün“ an den abschließenden Lauf; meine Läufe bestätigen die Aussage zum Server (Zusatz 8 von 8) und treffen die benannte Fragilität einmal (F-1).

## DoD Punkt für Punkt

Keine Haken gesetzt; `[x]` ist Sache des Planners bei der Closure.

| DoD-Punkt (§2) | Befund |
|---|---|
| **Antwortform** (Zwei-Nachrichten-Form im Stream und bei wartendem Befehl; Handvektor aus den Messdateien beider Instanzen; Tests Zustandsfolge, Race, wiederholtes ATTENTION) | **Erfüllt.** Handvektoren von `AbbruchAntwortBelegTests` gegen die Aufnahmen beider Instanzen belegt; Tests der Zustandsfolge, Race (DONE gegen ATTENTION, Frist gegen ATTENTION), wiederholtes ATTENTION, ATTENTION in `CANCELLING`, Fehlerzweige des Paares; sieben Mutationen rot. |
| **Abspielen und Live-Lauf** (A-13 entfällt oder auf A-18 verkleinert; A-7 eine Regel; gemessen, ob der Text gleich wird; kein stilles Anpassen) | **Erfüllt**, mit Einschränkung: Mitschnitt-Lauf grün, A-13 entfallen, `tokenfolge@2022` auf A-18; Live-Liste führt eine Regel, die Hälfte „Text gleich“ ist widerlegt und benannt. Der Live-Lauf ist **nicht reproduzierbar grün** (ein Lauf von vier rot, F-1), wie der Wortlaut selbst vorhersagt. |
| **Spezifikation und Entscheidungen** (SPEC-029, SPEC-001, Matrix Nr. 12, Messprotokoll nachgezogen; Folge-ADR `Accepted`) | **Erfüllt.** SPEC-029, SPEC-001 (Satz zu `CANCELLING` und Lesart), SPEC-044, SPEC-030/060 (Form im RPC und bei der Freigabe), Matrix Nr. 12 und Zeilen ATTENTION, BULK_LOAD, Freigabe der Handles; [ADR-0054](../plan/adr/0054-antwort-auf-attention-in-zwei-nachrichten.md) und [ADR-0057](../plan/adr/0057-abbruch-beim-beenden-endet-im-paar.md) `Accepted`, ADR-Index nachgeführt, [ADR-0027](../plan/adr/0027-abbruch-und-zuruecksetzen.md) unverändert. Die Folge-ADR zum Beenden (0057) nennt der Slice nicht (F-3). |
| `make gates` Exit 0 und Sensoren je Art | **Erfüllt auf `47c61816`:** `make gates FORCE=1` Exit 0, `clean-build`, `e2e`, `docs-check` grün (siehe Tabelle oben). `make spec-abdeckung`, `make flaeche` einzeln, `make kopplung-zahlen` einzeln habe ich nicht gefahren (Sache des Implementers; `flaeche` und `kopplung-zahlen` laufen in `make gates`, beide grün); `docs/plan/spec-abdeckung.md` ist im Diff und hat im Konflikt mit `main` die Regel „Fassung von `main`, neu erzeugen“ (F-2). |
| Review durchgeführt, Report unter `docs/reviews/` | **Erfüllt:** Review und Re-Review liegen vor, alle Findings behoben oder benannt (F-11, F-12 des Re-Reviews im Stand `47c61816` behoben). |
| Doku-Update (SPEC-029, SPEC-001, SPEC-045, Matrix Nr. 12, Messprotokoll, beide `.tsv`) | **Erfüllt.** SPEC-045 bleibt wie im Slice-Kopf gesagt unverändert; beide Listen und das Messprotokoll stehen auf dem Zustand. |
| Closure-Notiz mit Steering-Loop-Lerneintrag | **Offen:** §7 steht auf „Wird bei der Closure gefüllt.“ |
| Beobachtungs-Register fortgeschrieben (`BEO-TDS/cancel-antwort-text-weicht-vom-echten-server-ab`) | **Offen:** das Register ist im Diff unverändert; die Träger-Zeile nennt den Slice noch als „`open/`, nicht gestartet“. Sache der Closure (Ausgang nachführen; ein Folge-Slice für den Zusatz ist zu benennen). |
| Jedes Risiko aus §6 trägt einen Ausgang | **Offen:** alle fünf stehen auf „offen bis zur Closure“. Vorschläge nach Belegen, Entscheidung beim Planner: (1) Zustandsmaschine und Race: **entfallen** (Race-Tests und m1 bis m5 grün bzw. rot wie erwartet, kein doppelter Abschluss gefunden); (2) ein Lauf je Szenario: **weiter offen** (Matrix Nr. 12 benennt die Grenze); (3) Text des Clients: **eingetreten zur Hälfte** (Grundtext gleich, Zusatz bleibt; Folge-Slice; meine Zahl 1 von 8 in F-1); (4) Ein-Nachricht-Erwartung eines Clients: **entfallen für den Referenzclient** (Stufe 7 10 von 10, `Szenario` 13 von 13, Live 8 Teilläufe, Mitschnitt), **weiter offen für andere Clients**; der Slice nennt als Beleg die Stufe `Szenario`, die keinen Cancel-Test enthält (F-6); (5) Größe: **entfallen** (drei Liefer-Punkte). |
| Die drei Paarungen (Anker · Folge-Slice · Register) | **Offen:** `liegt in`-Zeile fehlt bis zur Closure; kein Slice für die Ursache des Zusatzes liegt unter `docs/plan/planning/` (die Entscheidung nennt ihn „eigenen Folge-Slice“); Register siehe oben. |

Der Slice hat drei Liefer-Punkte (Rückführungs-Trigger „mehr als drei“ nicht erfüllt).

## Plan-vs-Code-Diff (§3, `git diff main...HEAD`, 41 Dateien)

Dateiweiser Abgleich der 41 Dateien gegen die Tabelle in §3: jede Quell- und Testdatei steht dort (`AbortableRun.kt` nur Kommentare, `ResponseSequencer.kt` „unverändert“ und nicht im Diff); `AbbruchAntwortBelegTests.cs`, beide `.tsv`, Spec, Matrix, Messprotokolle, Nutzer-Doku, `spec-abdeckung.md`, ADR-Index und Mutationsbeleg stehen dort. **Nicht in §3, aber im Diff:** [ADR-0054](../plan/adr/0054-antwort-auf-attention-in-zwei-nachrichten.md) und [ADR-0057](../plan/adr/0057-abbruch-beim-beenden-endet-im-paar.md) (ADR-Dateien; §3 nennt nur den Index), die Reviews. Keine Produktionsdatei außerhalb der Liste (`src/main`: sieben Dateien, fünf davon nur Kommentare; neuer Code nur in `AbortedAnswer.kt` (`inTwoMessages`) und `HandshakeSession.kt` (`concludeWith`)). Kein `@Suppress` im Diff, keine Host-Aufrufe von `gradle` oder `java`, kein `- [x]` im Slice-Diff, `docs/plan/adr/` ohne Änderung an einer `Accepted`-Datei (nur neue Dateien und der Index).

## Probe außerhalb der Tests (gegen den Code gelesen)

| Fall | Befund |
|---|---|
| ATTENTION in `RUNNING` / wartender Befehl | `resultOpen = false` ergibt `DONE[ERROR]` `0x00fd`, dann `DONE[ATTN]`; Test `ATTENTION in RUNNING …` |
| ATTENTION in `STREAMING` | `resultOpen = true` ergibt `0x00c1` hinter den schon geschriebenen Tokens; nie zwei Abschlüsse (`ATTENTION vor dem naechsten Token …`) |
| ATTENTION in `CANCELLING` | bleibt im Eingang (`REGULAR_STOPS` enthält `CANCELLING`), nach `READY` einzeln bestätigt; Test und m5 |
| ATTENTION im Leerlauf und nach vollständiger Antwort | unverändert einzelnes `DONE[ATTN]` ohne Cancel (`complete()`, `ATTENTION im Leerlauf …`), als Setzung gekennzeichnet |
| Race DONE gegen ATTENTION, Frist gegen ATTENTION | erster Übergang gewinnt; `QueryTimeoutTest` (zwei Fälle), `AttentionCancelTest` |
| wiederholtes ATTENTION | ein Cancel, ein Paar, jedes weitere einzeln; `Ein wiederholtes ATTENTION …` |
| RPC und Massenladen | `failedDone`: `DONEPROC` `0x00e0` ohne RETURNSTATUS, beim Massenladen `DONE` `0x00f0`; in [SPEC-029](../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung), Matrix, KDoc von `DONE_ERROR_BULK` und Nutzer-Doku ausdrücklich als Setzung (nicht gemessen) gekennzeichnet; Tests `RpcSequenceAbortTest`, `BulkDispatchTest`, `UnprepareDispatchTest` |
| `CANCEL_TIMEOUT`, Disconnect | keine Bytes mehr, auch nicht die erste Nachricht (`CancelTimeoutTest`, `ShutdownDispatchTest`, `DisconnectTest`) |
| zweite Fundstelle der alten Lesart („einzelnes `DONE[ATTN]`“ bei ATTENTION im Stream oder im Beenden) | Grep über `spec/`, `docs/user/`, `docs/plan/*.md`, `docs/messungen/`, `src/`, `testserver/`, `tools/`, `harness/`, `README.md`, `referenztest-dotnet/`: **keine**. Verbleibende Treffer sind richtig (ATTENTION nach Ende der Antwort, im Leerlauf, nach `INSERT BULK`, gemessen M-11), Aussagen „die Antwort endet mit `DONE[ATTN]`“ (`tools/e2e.sh`, `docs/plan/e2e-abdeckung.md`: wahr, `DONE[ATTN]` ist die letzte Nachricht des Paares), das datierte Änderungsprotokoll (Eintrag vom 2026-11-19 nennt das einzelne `DONE` im Beenden, der Eintrag vom 2026-11-20 ersetzt es) und der Beleg zu den Mutationen. |

## Findings

| ID | Schwere | Befund | Quelle | Pfad |
|---|---|---|---|---|
| F-1 | LOW | Der Live-Lauf ist mit der Ein-Regel-Liste **nicht reproduzierbar grün**: in vier Läufen (acht Teilläufen) am 2026-11-20 zeigte die Referenz einmal (`live-2025`, Lauf 2) den Zusatz „ Operation cancelled by user.“, die Regel entfiel, `make vergleichslauf-d LAUF=live` Exit 2. Das ist die im Slice §1, im Messprotokoll („Grenze der Liste“) und in der Liste benannte Fragilität, vom Nutzer am 2026-11-20 angenommen; neu ist die Häufigkeit (Referenz mit Zusatz 1 von 8 Teilläufen; Server mit Zusatz 8 von 8), die das Messprotokoll mit „trat in diesen 13 Läufen nicht ein“ nicht trägt (und nicht behauptet). Hinweis für den Folge-Slice: im roten Teillauf waren beide Texte gleich. Empfehlung: die Läufe dieses Berichts im Messprotokoll nachtragen (Planner/Implementer), im Folge-Slice die Bedingung des Zusatzes untersuchen; bis dahin ist ein roter `LAUF=live` ohne weitere Abweichung kein Befund des Servers. | [AGENTS.md](../../AGENTS.md) §3.8; Slice §1, §6 (Risiko 3) | `referenztest-dotnet/Vergleich/live-ausnahmen.tsv` · `cancel-mit-folgeabfrage`; `docs/messungen/referenzabgleich.md` · „Live-Lauf (A-7)“ |
| F-2 | LOW | **Der Zweig ist hinter `main`** (`122c1f54` Basis; `main` trägt unter anderem eine ADR zur Form S2 (Datei `0055-…` unter `docs/plan/adr/`, im Zweig nicht vorhanden) und die Closure von `slice-simulation-mehrere-anweisungen-und-ausgabewert`). `git merge-tree` meldet Inhaltskonflikte in fünf Dateien: `docs/messungen/referenzabgleich.md`, `docs/plan/adr/README.md`, `docs/plan/spec-abdeckung.md`, `referenztest-dotnet/Vergleich/mitschnitt-ausnahmen.tsv`, `spec/spezifikation.md`. Der Konflikt in der `.tsv` ist inhaltlich: `main` hat die Regel `cancel-streaming/01/colmetadata` (A-14) entfernt, der Zweig die vier A-13-Regeln; eine falsche Auflösung ändert Rot und Grün des Mitschnitt-Laufs. Alle Läufe dieses Berichts gelten für `47c61816`, nicht für das Merge-Ergebnis. Vor der Closure: Konflikte lösen (`spec-abdeckung.md` nach der Regel der Tabelle in `harness/README.md`: Fassung von `main`, neu erzeugen, weil der Slice Tests geändert hat), danach mindestens `make vergleichslauf-d LAUF=mitschnitt`, `make docs-check`, `make gates`. | `harness/README.md` §Minimal agent workflow | siehe Spalte Befund |
| F-3 | LOW | Der Slice nennt [ADR-0057](../plan/adr/0057-abbruch-beim-beenden-endet-im-paar.md) nirgends (Kopf `Bezug`, §3, DoD-Punkt „Spezifikation und Entscheidungen“ nennt nur [ADR-0054](../plan/adr/0054-antwort-auf-attention-in-zwei-nachrichten.md)); die Entscheidung des Nutzers vom 2026-11-20 (a) steht in §1 ohne die ADR, die sie trägt. | Slice §1, §2; [AGENTS.md](../../AGENTS.md) §2 | `docs/plan/planning/in-progress/slice-cancel-antwort-zwei-nachrichten-angleichen.md` |
| F-4 | INFO | Die Ursache des Zusatzes hat noch keinen Träger unter `docs/plan/planning/` (die Entscheidung spricht von einem „eigenen Folge-Slice“); das Register `BEO-TDS/cancel-antwort-text-weicht-vom-echten-server-ab` führt den Slice als „`open/`, nicht gestartet“. Beides ist Closure-Arbeit des Planners (DoD-Punkte Register und Paarungen). | Slice §2 | `docs/plan/planning/observations/BEO-TDS/cancel-antwort-text-weicht-vom-echten-server-ab/state.md` |
| F-5 | INFO | Die Zahlen „13 Läufe (7 × 2022, 6 × 2025)“ und „13 von 13“ im Messprotokoll haben keine abgelegte Rohausgabe; ich konnte sie nicht nachrechnen. Nachgerechnet habe ich nur die Zahlen des Mitschnitt-Laufs (33, 27, 37) und den Mutationsbeleg (alle sieben gefahrenen Zeilen gleich). Die Aussage ist als Einzelmessung gekennzeichnet, kein Verstoß gegen §3.8. | [AGENTS.md](../../AGENTS.md) §3.8 | `docs/messungen/referenzabgleich.md` · „Live-Lauf (A-7)“ |
| F-6 | LOW | Slice §6 (Risiko 4) und der Auftrag nennen `make kompatibilitaet` mit „Stufe `Szenario`“ als Beleg der Wirkung auf den Referenzclient; die Kategorie `Szenario` hat 13 Tests ohne `Cancel()`. Die Cancel-Tests stehen in `Synchronitaet` (Stufe 7, 10 Tests), die ich einzeln und in `make e2e` gefahren habe (grün). Korrektur im Wortlaut des Ausgangs. | Slice §6 | `docs/plan/planning/in-progress/slice-cancel-antwort-zwei-nachrichten-angleichen.md` · Risiko „Ein Client könnte die Ein-Nachricht-Form erwarten“ |
| F-7 | INFO | Die Form beim Beenden ist an keinem Referenzclient-Lauf belegt (Stufe 1 von `make e2e` prüft nur das Ende der Antwort mit den letzten 13 Byte); getragen ist sie von JVM-Tests, Setzung ohne Messung ([ADR-0057](../plan/adr/0057-abbruch-beim-beenden-endet-im-paar.md)). Kein Befund, nur die Grenze der Belege. | [AGENTS.md](../../AGENTS.md) §3.8 | `src/test/kotlin/io/github/pt9912/tdswire/runtime/ServerSmokeTest.kt` |

| Schwere | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 0 |
| LOW | 4 (F-1, F-2, F-3, F-6) |
| INFO | 3 (F-4, F-5, F-7) |

## Empfehlung

**Closure: ja, nach Behebung von F-2 und Nachführung von F-3, F-4 und F-6 durch den Planner.** Die Zusagen der DoD sind am Code belegt (Frischbau, 1499 Testfälle, `e2e` 9 von 9, `gates` Exit 0, Mitschnitt grün, sieben von sieben Mutationen rot), die Entscheidungen [ADR-0054](../plan/adr/0054-antwort-auf-attention-in-zwei-nachrichten.md) und [ADR-0057](../plan/adr/0057-abbruch-beim-beenden-endet-im-paar.md) sind umgesetzt, Setzungen sind als Setzung gekennzeichnet, und die Aussagen zu A-7 und A-13 sind nicht breiter als das Gemessene. Zwei Bedingungen: (1) nach dem Merge von `main` (F-2) den Mitschnitt-Lauf und die Gates wiederholen; (2) der rote Live-Lauf (F-1) gehört in das Messprotokoll und in den Folge-Slice, damit die Closure-Notiz „`LAUF=live` grün“ nicht ohne die Rate behauptet.

Schluss: `make docs-check` nach Anlegen dieses Berichts, siehe Commit.
