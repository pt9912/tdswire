# Re-Review: slice-cancel-antwort-zwei-nachrichten-angleichen, Nachbesserung zum Review vom 2026-11-19 (Lauf 2026-11-20)

**Review-Art:** Re-Review der Nachbesserung (Skill: Nachbesserung mit Code ohne zweiten Review) gegen Plan, ADR und Hard Rules; nicht gegen die DoD (die Frage des Verifiers). Gelesen sind nur die Commits nach dem Stand des ersten Reports, nicht der ganze Slice.

**Gegenstand:** Branch `slice-cancel-antwort-zwei-nachrichten-angleichen`, `git diff e9ed9b98..d74e49c4` (19 Dateien): `0a41a647` (Code und Tests: `AbortedAnswer.kt`, `RequestLoop.kt`, `ShutdownGate.kt`, `AttentionAck.kt`, `HandshakeAction.kt`; `PairConclusionTest.kt` neu, `ShutdownDispatchTest.kt`, `UnprepareDispatchTest.kt`, `SessionSupport.kt`, `ShutdownWiringTest.kt`, `ServerSmokeTest.kt`; Spezifikation, Matrix, Messprotokolle, Nutzer-Doku, Ausnahmeliste, Slice), `a3f9a1e7` (Zeilenumbruch zweier KDocs), `d74e49c4` (ein Link im Slice). Danach liegt `b692ac69` auf dem Zweig ([ADR-0057](../plan/adr/0057-abbruch-beim-beenden-endet-im-paar.md), nur Doku, Folge-ADR des Architect); er ist nicht Teil des Diffs und nur auf seine Wirkung für F-9 gelesen.

**Skill:** `.harness/skills/reviewer.md` @ `d74e49c4` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-20

**Eingangs-Kontext:** Vorgänger-Report [2026-11-19-slice-cancel-antwort-zwei-nachrichten-angleichen.md](2026-11-19-slice-cancel-antwort-zwei-nachrichten-angleichen.md) (F-1 bis F-10), Slice (§1 bis §6, Entscheidung des Nutzers vom 2026-11-20), [ADR-0054](../plan/adr/0054-antwort-auf-attention-in-zwei-nachrichten.md), [ADR-0027](../plan/adr/0027-abbruch-und-zuruecksetzen.md), [TDS-FA-07](../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität), [SPEC-029](../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung), [SPEC-044](../../spec/spezifikation.md#spec-044--geordnetes-beenden), [SPEC-001](../../spec/spezifikation.md#spec-001--zustandsautomat-einer-verbindung), `AGENTS.md` §3.1, §3.2, §3.5, §3.7, §3.8.

**Selbst gefahren** (Messsperre `messlock` für den ganzen Batch gehalten, nach rund 4 Minuten Warten auf einen anderen Reviewer; `available` 16,8 GiB vor dem Lauf; keine künstliche Last; kein `make gates`, `make clean-build`, `make e2e`, `make spec-abdeckung`):

- Eine Kopie von `src/`, `testserver/` und `LICENSE` des Stands `d74e49c4` (Quelltext von `b692ac69` identisch, dort nur Doku), Stage `deps` und `testkeys` aus dem Layer-Cache, `gradle :test` auf den engsten Klassen. Der Arbeitsbaum des Slice blieb unverändert.
- Ausgangslauf ohne Mutation: `PairConclusionTest`, `ShutdownDispatchTest`, `ShutdownWiringTest`, `ServerSmokeTest`: `BUILD SUCCESSFUL` (23 Testfälle, 2 min 48 s, kalter Gradle-Daemon im Wegwerf-Image).
- Fünf Mutationen einzeln, je ein Lauf, die Wirkung gemessen:

| Nr | Mutation | Wirkung (gemessen) |
|---|---|---|
| N1 | `AbortedAnswer.conclude`: `SHUTDOWN` wieder als einzelnes `DONE[ATTN]` (`appended(AttentionAck.bytes, …)`) | rot, 3 von 23: `ShutdownDispatchTest > Der Ablauf der Frist beendet die Antwort mit DONE ERROR und DONE ATTN und die Verbindung`, `ShutdownWiringTest > nach Ablauf der Frist wird die Anfrage abgebrochen und die Verbindung endet`, `ShutdownWiringTest > SHUTDOWN_GRACE 0 bricht die laufende Anfrage sofort ab`; **`ServerSmokeTest > Der Prozess-Stop beendet eine ruhende Verbindung …` bleibt grün** (F-11) |
| N5 | `SHUTDOWN` mit `connectionOpen = true` | rot, 1 von 17: `ShutdownDispatchTest > Der Ablauf der Frist beendet die Antwort …` |
| R11 | `inTwoMessages`: `else null` entfällt | rot, 2 von 17: `PairConclusionTest > Scheitert die erste Nachricht des Paares …` und `… zweite Nachricht …` (`PROTOCOL_VIOLATION` statt `WRITE_FAILED`) |
| R12 | `concludeWith`: Ergebnis von `channel.send(second)` verworfen | rot, 1 von 17: `PairConclusionTest > Scheitert die zweite Nachricht des Paares …` (`PEER_CLOSED` statt `WRITE_FAILED`) |
| R13 | `concludeWith`: `guarded(emptySet())` statt `guarded(CLOSED_ONLY)` | rot, 1 von 17: `PairConclusionTest > Das Paar schreibt in CLOSED nichts` |

Zahlen und Gründe des Schließens stimmen mit dem Mutationsbeleg [mutationen-cancel-antwort-2026-11-20.md](../messungen/mutationen-cancel-antwort-2026-11-20.md) Runde 3 überein (dort „3 von 14“ für N1 und „1 von 14“ für N5 sind die Klassen `ShutdownDispatchTest` und `ShutdownWiringTest` allein; meine Läufe führen `PairConclusionTest` mit, daher 23 und 17 Testfälle).

- Nicht gefahren: `make flaeche`, `make kopplung-zahlen`, `make spec-abdeckung`, die .NET-Tests und die Live- und Mitschnitt-Läufe (Sache von Implementer und Verifier); die Runden 1 und 2 des Mutationsbelegs habe ich nicht wiederholt (Runde 2 deckt sich mit den Zahlen meines ersten Reports, Runde 1 sind Läufe des Implementers, nicht von mir geprüft).

## Zustand der Findings des ersten Reports

| ID | Zustand | Beleg |
|---|---|---|
| F-1 | behoben | [SPEC-044](../../spec/spezifikation.md#spec-044--geordnetes-beenden) Nr. 4 nennt das Paar, kennzeichnet es als Setzung des Nutzers vom 2026-11-20 („am echten Server nicht gemessen“), Nr. 3 nennt das Ende der Verbindung nach dem Paar; Änderungsprotokoll hat den Eintrag vom 2026-11-20 mit „ersetzt die Form eines einzelnen `DONE` mit `ATTN`“; Nutzer-Doku `einbettung-und-betrieb.md` Z. 644 trägt Form und Setzung im selben Satz; Code und Tests pinnen sie (N1, N5 rot). Der Satz zu SPEC-044 im Eintrag vom 2026-11-19 ist berichtigt (INFO I-2). |
| F-2 | behoben | Grep über `src/`, `testserver/`, `spec/`, `docs/user/`, `docs/plan/kompatibilitaetsmatrix.md`, `docs/messungen/`, `tools/`, `harness/` auf „genau ein `DONE[ATTN]`“, „einzelnes `DONE`“, „wie bei ATTENTION“ im Beenden: die Treffer in `AbortedAnswer.kt`, `RequestLoop.kt`, `ShutdownGate.kt`, `HandshakeAction.kt`, `AttentionAck.kt` und in den Tests nennen das Paar; der Test heißt `Der Ablauf der Frist beendet die Antwort mit DONE ERROR und DONE ATTN und die Verbindung`; Zeilenlänge der geänderten Quelldateien ≤ 120. Verbleibende Treffer sind richtig (ATTENTION nach Ende der Antwort, [SPEC-001](../../spec/spezifikation.md#spec-001--zustandsautomat-einer-verbindung), [SPEC-029](../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung)) oder Aussagen „die Antwort endet mit `DONE[ATTN]`“ (`tools/e2e.sh`, `docs/plan/e2e-abdeckung.md`, wahr für beide Formen). |
| F-3 | behoben | `PairConclusionTest` (drei Fälle, `FakeHandshakeChannel.failSendIf`); R11, R12, R13 rot (oben). |
| F-4 | behoben | Zeile BULK_LOAD der Matrix: „ATTENTION während des Ladens mit dem Paar `DONE` mit `ERROR` (Befehl `0x00f0`, Setzung) und `DONE` mit ATTENTION-Status“; die Nennung der einzelnen Form für ATTENTION nach `INSERT BULK` bleibt (richtig). |
| F-5 | behoben | Dateiweiser Abgleich `git diff --name-only main...d74e49c4` gegen §3 des Slice: jede berührte Datei steht darin (außer dem Slice und den Reports selbst); `ResponseSequencer.kt` steht als „unverändert“; der Kopf „Berührte Spec-Stellen“ nennt [SPEC-029](../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung), [SPEC-001](../../spec/spezifikation.md#spec-001--zustandsautomat-einer-verbindung), SPEC-030, [SPEC-044](../../spec/spezifikation.md#spec-044--geordnetes-beenden) und SPEC-060 und sagt, dass SPEC-045 unverändert bleibt. |
| F-6 | behoben | Test heißt `ATTENTION waehrend der Antwort der Freigabe bricht sie mit DONE ERROR und DONE ATTN ab`; Matrix und `docs/plan/spec-abdeckung.md` tragen den neuen Namen. |
| F-7 | behoben | Spalte „Grund“ von `cancel-mit-folgeabfrage` und A-7 nennen den geltenden Zustand; „seit dem Bau“, „der frühere Text … erscheint nicht mehr“ und „die Ausnahmeliste führte damals zwei Regeln“ sind weg. Messdaten mit Datum (Läufe vom 2026-11-06 und 2026-11-19) bleiben, sie sind Messung, nicht Chronik. |
| F-8 | von Planner und Nutzer beantwortet | DoD-Punkt „Abspielen und Live-Lauf“ und §5 tragen den Wortlaut der Entscheidung vom 2026-11-20 (A-13 entfällt, A-7 eine Regel, Ursache Folge-Slice); kein DoD-Haken gesetzt (`git diff` ohne `- [x]`). |
| F-9 | formal durch den Architect behoben | [ADR-0057](../plan/adr/0057-abbruch-beim-beenden-endet-im-paar.md) (`b692ac69`, `Accepted`) ersetzt wörtlich die drei Aussagen von [ADR-0027](../plan/adr/0027-abbruch-und-zuruecksetzen.md); die Datei von [ADR-0027](../plan/adr/0027-abbruch-und-zuruecksetzen.md) ist unverändert (`AGENTS.md` §3.5). Nicht Teil dieses Diffs, nicht inhaltlich zu Ende geprüft. |
| F-10 | behoben | Der Mutationsbeleg liegt im Repo; er benennt, dass das Setz-Skript nicht im Repo liegt, und trennt Runden und Urheber; seine Zahlen der Runde 3 habe ich vollständig nachgefahren (N1, N5, R11, R12, R13 stimmen auf die Zahl und den Grund), die der Runde 2 stimmen mit meinen Läufen vom 2026-11-19 überein. |

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| F-11 | LOW | Das KDoc des Prozess-Stop-Tests sagt, die laufende Anfrage ende nach der Frist „mit dem Paar `DONE[ERROR]` und `DONE[ATTN]`“; die Prüfung liest nur die letzten 13 Byte (`takeLast`), das sind die Bytes von `DONE[ATTN]`. Unter N1 (einzelnes `DONE[ATTN]`) bleibt der Test grün (gefahren, `ServerSmokeTest` 6 von 6 `PASSED`); das Paar belegen `ShutdownDispatchTest` und `ShutdownWiringTest`, nicht dieser Test. | `AGENTS.md` §3.8 (Test-KDoc trägt weiter als die Prüfung) | `src/test/kotlin/io/github/pt9912/tdswire/runtime/ServerSmokeTest.kt` · „endet nach der Frist mit dem Paar `DONE[ERROR]` und `DONE[ATTN]`“ | ja, Mutation N1 (Klasse `ServerSmokeTest` bleibt grün) | Test-KDoc breiter als die Prüfung |
| F-12 | LOW | Das KDoc des Tests `Scheitert die erste Nachricht des Paares, geht die zweite nicht hinaus` steht im Konjunktiv über den abwesenden Zustand: „ein solches in `CLOSED` wäre ein unzulässiger Übergang und änderte den Grund des Schließens“. | `AGENTS.md` §3.7 (Skill: Test-KDoc nennt den abwesenden oder unwirklichen Zustand) | `src/test/kotlin/io/github/pt9912/tdswire/protocol/dispatch/PairConclusionTest.kt` · „wäre ein unzulässiger Übergang und änderte den Grund des Schließens“ | nein | Test-KDoc nennt den abwesenden Zustand |
| I-1 | INFO | Die Zeilen des Mutationsbelegs Runde 3 tragen die Nummern R11 bis R13, N1 und N5; N2 bis N4 kommen nicht vor. Die Lücke ist weder benannt noch erklärt (gelaufen oder verworfen). Zuständig: Implementer (Kennzeichnung) für die Closure-Notiz §7. | Maintainability | `docs/messungen/mutationen-cancel-antwort-2026-11-20.md` · „| N5 | `AbortedAnswer.conclude`: `SHUTDOWN` mit `connectionOpen = true`“ | nein | Nummernlücke im Messprotokoll |
| I-2 | INFO | Der Eintrag vom 2026-11-19 im Änderungsprotokoll sagt weiter „der Abbruch im geordneten Beenden endet weiter mit einem einzelnen `DONE` mit `ATTN`“; der Eintrag vom 2026-11-20 nennt ihn als ersetzt. Ein Änderungsprotokoll ist datiert und trägt die Folge; kein Widerspruch zur geltenden Spezifikation (SPEC-044 Nr. 4), nur zu beachten beim Lesen. | Maintainability | `spec/spezifikation.md` · „der Abbruch im geordneten Beenden endet weiter mit einem einzelnen `DONE` mit `ATTN`“ | nein | historischer Eintrag im Änderungsprotokoll |

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `src/main/kotlin/…/protocol/dispatch/AbortedAnswer.kt` (`inTwoMessages` mit `connectionOpen`, Zweige von `conclude`) | geprüft: `SHUTDOWN` läuft über dieselbe Funktion wie ATTENTION mit `connectionOpen = false`; die Zusage je Zweig trägt ein Test: Form (N1 rot), Schließen (N5 rot), Fehlerzweige des Schreibens (R11, R12 rot). Kein Befund. |
| `src/main/kotlin/…/protocol/session/HandshakeSession.kt` (`concludeWith`) | im Diff nicht geändert; Zweig `CLOSED_ONLY` jetzt durch `Das Paar schreibt in CLOSED nichts` getragen (R13 rot). Kein Befund. |
| `src/main/kotlin/…/protocol/dispatch/RequestLoop.kt`, `ShutdownGate.kt`, `session/AttentionAck.kt`, `session/HandshakeAction.kt` | geprüft: Diff nur Kommentar; die Kommentare nennen Zusage und Grenze („Setzung“) und den geltenden Zustand (`AGENTS.md` §3.7). Kein Befund. |
| `src/test/kotlin/…/protocol/dispatch/PairConclusionTest.kt`, `ShutdownDispatchTest.kt`, `UnprepareDispatchTest.kt` | geprüft: drei Fälle des Paares je mit eigener Zusage (zweite Nachricht scheitert, erste scheitert, `CLOSED`); Gründe des Schließens unterscheiden die Mutationen (`WRITE_FAILED` gegen `PROTOCOL_VIOLATION` und `PEER_CLOSED`); der Test des Beendens prüft Anzahl der Nachrichten, Ende der ersten (`DONE_ERROR_OPEN`), zweite Nachricht (`DONE_ATTN`), kein ERROR-Token, Cancel einmal, Zustand `CLOSED`. Keine Auswahl- oder Gleichheitseigenschaft mit einheitlicher Eingabe. Befund nur F-12. |
| `src/test/kotlin/…/protocol/session/SessionSupport.kt` (`failSendIf`) | geprüft: Test-Naht nur im Testcode (`FakeHandshakeChannel`), kein Parameter im Produktivcode. Kein Befund. |
| `src/test/kotlin/…/runtime/ShutdownWiringTest.kt`, `ServerSmokeTest.kt` | geprüft: beide Wiring-Tests lesen die erste und zweite Nachricht über den Socket (N1 rot); der Prozess-Stop-Test belegt nur das Ende. Befund nur F-11. |
| `spec/spezifikation.md` ([SPEC-044](../../spec/spezifikation.md#spec-044--geordnetes-beenden) Nr. 3 und 4, Änderungsprotokoll) | geprüft: Gemessenes (Antwort auf ATTENTION des Clients, [SPEC-029](../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung)) und Setzung (Form im Beenden) im selben Satz getrennt; keine Zusage der unteren Schicht über dem Lastenheft. Kein Befund außer I-2. |
| `docs/user/einbettung-und-betrieb.md` (Z. 644 und der Abschnitt zum Beenden), `docs/user/referenz-konfiguration.md` | geprüft: die geänderte Zeile nennt Form und Setzung („am echten SQL Server nicht gemessen“); keine Treffer auf „nicht gebaut“, „bisher“, „noch nicht“, „vorläufig“, „soll“, „seit Slice“ in den geänderten Zeilen; keine zweite Fundstelle der einzelnen Form im Beenden in `docs/user/`. Kein Befund. |
| `docs/plan/kompatibilitaetsmatrix.md`, `docs/plan/spec-abdeckung.md` | geprüft: BULK_LOAD-Zeile und Zitate tragen die neuen Namen; `spec-abdeckung.md` zählt `PairConclusionTest` unter SPEC-001, SPEC-029, SPEC-031 und führt die zwei neuen Testfälle unter [TDS-FA-07](../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe) (nicht neu erzeugt). Kein Befund. |
| `docs/messungen/referenzabgleich.md`, `referenztest-dotnet/Vergleich/live-ausnahmen.tsv` | geprüft: A-7 und die Regel nennen den geltenden Zustand, die Grenze der Liste („Ausnahme entfällt“ bei Zusatz der Referenz) und den Nutzerentscheid; „Mit den Regeln der früheren Liste war der Lauf rot“ im Absatz des Mitschnitt-Laufs ist Bericht über einen Lauf (Messprotokoll), kein Zustandsfeld. Kein Befund. |
| `docs/messungen/mutationen-cancel-antwort-2026-11-20.md` | geprüft: Erzeuger (nicht im Repo), Grenze, Urheber je Runde und „gemessene Wirkung“ stehen im Dokument; Runde 3 vollständig nachgefahren und gleich (siehe Tabelle oben). Befund nur I-1. |
| Plan-Tabelle und Kopf des Slice (§3) | geprüft (F-5). Kein Befund. |
| Zweite Fundstellen der alten Lesart („einzelnes `DONE[ATTN]`“ im Beenden) | Grep über `spec/`, `docs/`, `harness/`, `tools/`, `src/`, `testserver/`, `referenztest-dotnet/`, `README.md`: keine weitere widersprechende Stelle außerhalb von Zeitdokumenten (`docs/plan/planning/done/`, Beobachtungs-Evidenz, Reports), `Accepted`-ADRs ([ADR-0027](../plan/adr/0027-abbruch-und-zuruecksetzen.md), [ADR-0007](../plan/adr/0007-fehler-und-attention.md), [ADR-0054](../plan/adr/0054-antwort-auf-attention-in-zwei-nachrichten.md), durch [ADR-0057](../plan/adr/0057-abbruch-beim-beenden-endet-im-paar.md) abgedeckt oder nicht Teil dieses Diffs) und dem datierten Änderungsprotokoll (I-2). |
| `AGENTS.md` §3.1, §3.2 | geprüft, ohne Befund (kein Host-Aufruf von `gradle` oder `java` im Diff, kein `@Suppress` in den geänderten Quelldateien). |
| `AGENTS.md` §3.5 | geprüft, ohne Befund (`docs/plan/adr/` im Diff `e9ed9b98..d74e49c4` unverändert; [ADR-0057](../plan/adr/0057-abbruch-beim-beenden-endet-im-paar.md) ist eine neue ADR mit `Supersedes`). |
| Haken der DoD im Slice | geprüft, ohne Befund (kein `- [x]` im Diff, DoD-Wortlaut ohne Rollen-Text). |
| `harness/`, `tools/`, `testserver/`, `config/`, `Dockerfile`, Makefile-Fragmente | geprüft, ohne Befund (nicht im Diff). |
| Nachbesserung nach diesem Report | `0a41a647`, `a3f9a1e7` (Kommentar-Umbruch), `d74e49c4` (Doku) sind hier gelesen und gefahren; kein weiterer Commit mit Code nach `d74e49c4` (`b692ac69` nur Doku). Ein weiterer Code-Commit nach diesem Report braucht einen eigenen Re-Review. |

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 0 |
| LOW | 2 |
| INFO | 2 |

**Finding-Klassen dieses Laufs:** Test-KDoc breiter als die Prüfung · Test-KDoc nennt den abwesenden Zustand · Nummernlücke im Messprotokoll · historischer Eintrag im Änderungsprotokoll

## Verdikt

**Merge-blockierend:** nein. F-1 bis F-7 und F-10 sind behoben und nicht breiter als ihr Beleg: die Form im Beenden ist überall als Setzung des Nutzers gekennzeichnet, die Zusagen des Paares (Form, Schließen, Fehlerzweige) tragen je einen unterscheidenden Test (N1, N5, R11, R12, R13 gefahren und rot, Zahlen wie im Mutationsbeleg). Die neuen Findings sind LOW (F-11, F-12) und INFO (I-1, I-2).

**Übergabe:** F-11, F-12 und I-1 an den Implementer (kein Blocker; I-1 gehört in die Closure-Notiz §7); I-2 ohne Aktion. F-8 und F-9 sind bei Planner und Architect beantwortet. Der Slice kann an den Verifier; dieser Report ersetzt keine Verifikation, DoD- und Spec-Konformität prüft der Verifier. Die Finding-Klassen gehen in die Slice-Closure §7.
