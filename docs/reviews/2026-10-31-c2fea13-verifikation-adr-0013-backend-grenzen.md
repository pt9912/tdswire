# Verifikation: [ADR-0013](../plan/adr/0013-backend-grenzen.md) Backend-Grenzen, Spec-Nachführung, Folge-Slices und Fix-Runde — 2026-10-31

**Rolle:** Verifier (Modul 11), frischer Kontext. Frage: Bauen wir es richtig? Geprüft wird die DoD von [slice-adr-backend-grenzen](../plan/planning/done/welle-backend-streaming/slice-adr-backend-grenzen.md) gegen [ADR-0013](../plan/adr/0013-backend-grenzen.md) (Status Proposed) und die Entscheidungen des Nutzers vom 2026-10-31. Nicht geprüft wird, ob das Richtige gebaut wird (Validator) oder der Diff gegen den Plan (Reviewer, [Review](2026-10-31-42bd58d-adr-0013-backend-grenzen.md)). Nichts repariert, nichts committet.

**Stand:** `main` bei c2fea13. Gegenstand: Merge 42bd58d, Fix-Runde e9ef7de (ADR und Spec) und 545cdd0 (Slice-Pläne) im Merge 329269c, dazu der gebaute Stand des Streaming-Slice (Merge 81d41d5 mit Fix-Runde bis c2fea13).

**Eingang:** DoD-Bestätigung des Planners und der Review-Report mit F-1 bis F-13. Ein Sensor-Beleg des Implementers liegt für diesen Slice nicht vor (er ändert keinen Produktionscode); die Sensoren sind selbst gefahren.

## Ergebnis in einem Satz

Alle Review-Befunde sind behoben oder ehrlich als offen geführt, die Schichtregeln und die Unverändertheit von [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md) und Lastenheft halten, `make gates` ist grün. **[ADR-0013](../plan/adr/0013-backend-grenzen.md) kann heute noch nicht auf Accepted gesetzt werden**: Die Entscheidung des Nutzers zu [TDS-FA-06](../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände) steht noch als „Offen“ in der ADR und muss vorher in den Entscheidungsteil (Vorbedingung V1, Abschnitt „Kann Accepted gesetzt werden?“).

## Sensor-Belege

| Sensor | Ausgang | Beleg |
|---|---|---|
| `make gates` (selbst gefahren, ein Docker-Build gleichzeitig) | grün: baseline-verify v6.18.1 OK (54 Dateien), d-check 388 Dateien 0 Befunde, detekt, Image-Build, .NET-Referenztest, Test-Stage ohne Fehlerzeile | Lauf im Scratchpad `/tmp/claude-1000/-Development-tdswire/8562f454-84ba-456b-9970-945e69c035f4/scratchpad/v/gates.log`. Die Docker-Layer von detekt, Referenztest und Test-Stage kamen aus dem Cache (Wanduhr 8 s): das ist ein Beleg für unveränderte Eingänge, kein frischer Testlauf (`BEO-TDS/test-stage-cache-verdeckt-wiederholung`). Der Slice ändert keinen Code; für die Frage dieser Verifikation genügt das. |
| `make docs-check` nach Anlage dieses Reports | siehe Abschnitt „Abschluss“ | — |
| Nachrechnen M4 | 128 MiB / 1000 = 134 217 Byte; / 173 = 775; / 454 = 295 | stimmt mit ADR |
| Handabgleich ADR gegen [Optionsbericht](2026-10-30-b1a23b2-optionen-adr-backend-grenzen.md) | siehe Abschnitt „Messzahlen“ | — |

## DoD-Konformität von slice-adr-backend-grenzen

| DoD-Punkt | Häkchen im Plan | Befund des Verifiers |
|---|---|---|
| 1 Spikes S1 bis S3, Messtabellen | gesetzt | Bestätigt: M1 bis M8 stehen im Optionsbericht und mit den Grenzen des Spike in den Messzeilen der ADR. Der Wortlaut des Punktes („als Tabelle in diesem Slice und in der Entscheidung“) widerspricht der Klammer „nicht in diesem Slice“ am Ende desselben Punktes; die Abweichung ist im Plan selbst benannt (V6, LOW). |
| 2 Entscheidung des Nutzers, neue ADR, Status Accepted nach Review und Verifikation | offen | Korrekt offen. ADR liegt als Proposed vor, Index und Geschichte stehen, `Bezug` auf [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md), kein `Supersedes`, jede Aussage trägt Messzeile oder ist als Setzung gekennzeichnet (Ausnahme: V4). Review liegt vor, Verifikation ist dieser Report; Vorbedingungen für Accepted siehe unten. |
| 3 Anschlussstellen nachgezogen | gesetzt | Bestätigt für Spezifikation (SPEC-002, SPEC-022 samt Vertrag, SPEC-031, SPEC-035, SPEC-009, SPEC-016, SPEC-048, Historie) und die offenen Slices. Die Slices in `done/` bleiben unverändert, der Anschluss läuft über die Folgepflicht der ADR; das steht im Punkt selbst. |
| Nachweis-Pflichten (Gates, Review, Doku, Closure, Register, Risiken, Paarungen) | offen | Korrekt offen. `make gates` ist jetzt grün; die Häkchen setzt der Planner mit der Closure. Das Doku-Update zu SPEC-022 und SPEC-002 ist faktisch geliefert, das Kästchen noch leer. |

**Weitere Stellen im Plan:** Der Kopf „Berührte Spec-Stellen“ nennt SPEC-002, SPEC-031, SPEC-035 und SPEC-033. Geändert wurden SPEC-002, SPEC-009, SPEC-016, SPEC-022, SPEC-031, SPEC-035 und SPEC-048; SPEC-033 ist nur Verweis (V6, LOW). Die Risiken in §6 tragen noch keinen Ausgang; das ist vor der Closure zulässig.

## Review-Befunde F-1 bis F-13

| ID | Klasse (Review) | Stand | Beleg im Repo |
|---|---|---|---|
| F-1 | MEDIUM, Fang des Sequenzierers | behoben | ADR Entscheidung 7 und [SPEC-031](../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität) sagen übereinstimmend: jedes Throwable aus Aufruf und Strom des Backends außer der Abbruchausnahme, auch ein Error, gebauter Stand. Die Quelle (ResponseSequencer: runCatching um execute, catch am Strom) stimmt damit überein; nur ein OutOfMemoryError aus Encoder und Senke ist neu gefangen (acceptGuarded, endGuarded), andere Error laufen nach außen (Test „Ein anderer Error laeuft nach aussen“ in StreamingDispatchTest). |
| F-2 | MEDIUM, Spannung zu [TDS-FA-06](../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände) | ehrlich offen | Benannt in Bezug, Alternativen (Contra von Option B) und Abschnitt „Offen“ der ADR. Die Frage ist inzwischen vom Nutzer beantwortet und noch nicht übernommen (V1). |
| F-3 | MEDIUM, KDoc-Nachzug ohne Träger | behoben | Umfang auf die geschlossenen Sätze begrenzt, die zwei weiteren Sätze in QueryEvent bleiben; Träger sind Liefer-Punkt 2 und 3 von [slice-backend-streaming-nachweis](../plan/planning/done/welle-backend-streaming/slice-backend-streaming-nachweis.md) und Punkt 3 von [slice-attention-cancel-timeout](../plan/planning/open/slice-attention-cancel-timeout.md), jeweils nach Accepted. Die Zuordnung ist teilweise überholt (V3). |
| F-4 | MEDIUM, Betriebstext ohne Liefer-Punkt | behoben | Betriebstext steht im Liefer-Punkt 1 von [slice-release-doku-prozess](../plan/planning/open/slice-release-doku-prozess.md); der Startparameter im Liefer-Punkt 1 von [slice-release-artefakt-lizenz](../plan/planning/open/slice-release-artefakt-lizenz.md) samt Test, Bezug ergänzt. Fund am Rand: Im Plan von slice-release-artefakt-lizenz stehen die Liefer-Punkte „Laufzeit-Image“ und „Laufzeittest“ auf einer Zeile (`ist).- [ ] **Laufzeittest**`). Das war schon vor der Fix-Runde so (Stand 545cdd0~1), gehört nicht zu F-4, und die Zählung der Liefer-Punkte leidet darunter (V6, LOW). |
| F-5 | MEDIUM, Größe des dritten Punktes | behoben | Der Schnitt hat einen am Plan beobachtbaren Auslöser (Häkchen der Punkte 1 und 2, Teil (a) grün, Teil (b) ohne Test) statt „DoD-Punkt 1 in done/“. |
| F-6 | MEDIUM, Aussage breiter als Messung | behoben | M5 und M6 benennen das Beobachtungsfenster (20 s, 600 s) und „Hänger“ als „am Ende des Fensters nicht beendet“; M5 nennt Läufe 1, 3, 4 mit 5, 4, 4 regulär beendeten von 8 großen Anfragen. Stimmt mit den Rohzeilen des Optionsberichts. |
| F-7 | MEDIUM, Aussage breiter als Messung | behoben | [SPEC-022](../../spec/spezifikation.md#query-backend-vertrag-zu-spec-022): je Zeile 77 Threads und 8,2-fach (3099 gegen 377 ms), andere Umhüllungen 26 bis 78 Threads und 707 bis 3127 ms; ADR Entscheidung 4: bei nicht unterbrechbarem Aufruf setzt auch der Kern die Frist erst mit dessen Ende durch. Nachgerechnet 707/377 = 1,9 und 3127/377 = 8,3. |
| F-8 | LOW, Schärft-Feld | behoben | SPEC-016 steht im Feld Schärft; alle sieben geänderten Stellen sind dort gelistet. |
| F-9 | LOW, Konjunktiv | behoben | Der Satz „Ohne diese Zeile hätte …“ ist ersetzt durch eine Zusage über das Risiko. |
| F-10 | LOW, Träger-Zuordnung | behoben | Folgepflicht nennt den ersten Liefer-Punkt für den Ersatz der puffernden Senke und den dritten für das Fangen. |
| F-11 | INFO, detekt-Risiko | geklärt, aber in der ADR veraltet | Der Probe-Build des Reviews und der Gate-Lauf des Streaming-Reviews zeigen, dass detekt das Fangen von OutOfMemoryError nicht beanstandet (keine Änderung an `config/detekt/detekt.yml`). Die ADR führt es weiter als „Risiko, nicht geprüft“ (V3). |
| F-12 | INFO, Bezeichnungen | behoben | M6/C, M7/Abbruch, M7/Folgen im Text erläutert. |
| F-13 | INFO, Spec vor Annahme der ADR | offen, wie vom Review benannt | Die Spezifikation führt „Nutzerentscheidung vom 2026-10-31“ bei Proposed; die Kennzeichnung ist ehrlich. Mit Accepted entfällt der Vorbehalt; bei Ablehnung sind die Stellen mit abzulösen. |

Ergebnis: 12 von 13 behoben, F-2 inhaltlich beantwortet und noch nicht übernommen. Es entsteht kein neuer Befund der Klasse „Aussage breiter als Messung“ in den geprüften Zeilen.

## Entscheidung ADR gegen gebauten Stand (OutOfMemoryError)

Der Streaming-Slice hat die Behandlung gebaut und im Review (Merge 81d41d5) samt Fix-Runde (8740036, Merge c2fea13) geprüft.

| ADR-Zusage (Entscheidung 2 und 7) | Gebauter Stand | Deckung |
|---|---|---|
| OutOfMemoryError beim Erzeugen oder Senden der Antwort wird gefangen, `TDS_INTERNAL_ERROR` (ERROR Severity 20 und DONE mit Fehlerstatus, fester Text), Verbindung schließt | ResponseSequencer: Fang um Backend (jedes Throwable außer Abbruchausnahme), acceptGuarded und endGuarded für Encoder und Senke; Test „Ein OutOfMemoryError im Anfragepfad ergibt TDS_INTERNAL_ERROR und schliesst“ | gedeckt |
| „soweit der Strom synchron ist, sonst schließt die Verbindung ohne ERROR und DONE“ | MessageStream setzt den Strom nach einer Ausnahme aus Paketbau oder Kanal auf fehlerhaft (womöglich mitten in einem Token); danach `false`, kein ERROR, kein DONE, Ergebnis CLOSE_WITHOUT_COMPLETION; ebenso wenn das Schreiben von ERROR und DONE selbst am Fehler scheitert; Test „Ein OutOfMemoryError mitten in einem Token schliesst ohne ERROR und DONE“ | **gedeckt, mit einer Lücke im Wortlaut** |
| andere Error aus Encoder und Senke und die Abbruchausnahme werden nicht gefangen | acceptGuarded fängt nur OutOfMemoryError; Test „Ein anderer Error laeuft nach aussen“ | gedeckt |

Antwort auf die Frage des Auftrags: Ja, Entscheidung 2 deckt „schließt ohne Antwort, wenn der Strom nicht an einer Token-Grenze ist“, aber nur über die Definition in [SPEC-031](../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität) („synchron heißt, dass der Transport jedes bisher erzeugte Token vollständig angenommen hat“). Der Text der ADR verwendet „synchron“ in Entscheidung 2, Entscheidung 4 und der Fitness Function ohne Definition und sagt für den Senkenfehler nur „gelingt es nicht, ist der Strom nicht abschließbar“. Der gebaute Stand ist strenger: Er schließt ohne Antwort auch dann, wenn der Fehler im Schreiben fiel und womöglich noch Platz für ERROR und DONE wäre, weil ein halbes Token nicht ausgeschlossen ist. Das ist die richtige, vorsichtige Lesart, steht aber nur in Spec und KDoc. Die ADR sollte den Satz vor Accepted aufnehmen (V2); ein Widerspruch liegt nicht vor.

Zwei weitere Beobachtungen zum gebauten Stand: (1) Die KDoc der Quelle nennt [ADR-0013](../plan/adr/0013-backend-grenzen.md) und „Proposed“ nirgends mehr (Suche unter `src/main` ohne Treffer), der Review-Befund zu diesem Zustandsvermerk ist also behoben. (2) `QueryExecution.kt` trägt weiter den Satz „Offen ([ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md)): die Behandlung von OutOfMemoryError“, obwohl der Code den Fehler fängt. Das ist laut Plan so vorgesehen (Nachzug erst nach Accepted, Liefer-Punkt 3 des Streaming-Slice) und kein Befund, aber nach Accepted nachzuziehen.

## Messzahlen M1 bis M8 gegen den Optionsbericht (Stichprobe)

| ID | Stichprobe | Ergebnis |
|---|---|---|
| M1 | 173 / 454 / 453 / 151 / 429 / 72 / 202 Byte je Zeile | stimmt Zeile für Zeile |
| M2 | 1 540 000 / 580 000, 1 790 000 / 610 000, 3 690 000 / 1 320 000 | stimmt |
| M3 | 1 Mio Zeilen: 140 / 180 ms gegen 0,039 / 36 ms | stimmt |
| M4 | 134 000 Byte, 775 und 295 Zeilen | nachgerechnet, stimmt; als abgeleitet gekennzeichnet |
| M5 | Läufe 1, 3, 4: 5, 4, 4 von 8 regulär; Lauf 2 Totalhänger über 600 s; 200 von 200 auf neuer Verbindung | stimmt mit den Rohzeilen (Lauf 1: 3 Fehler, Läufe 3 und 4: je 2 Fehler und ein CoroutinesInternalError) |
| M6, M6/C | vier Läufe mit Hänger, drei davon mit unbeteiligter kleiner Verbindung (10 bis 14 von 300); Exit-Code 3 nach 1,8 bis 2,1 s | stimmt |
| M7, M7/Abbruch | 377 / 3099 / 1061 / 707 / 3127 / 2613 ms, Threads 11 / 77 / 75 / 78 / 26 / 26; Abbruch 2400 bis 2412 ms und länger als 5000 ms | stimmt |
| M8 | 0 bis 3, 0 bis 1, 1500 bis 1513, 7 bis 10, 300 bis 301, 3042 ms | stimmt |

Nicht nachgemessen: Der Spike liegt nicht im Repo und ich habe ihn nicht neu gefahren (Rechner ausgelastet, kein Lastlauf). Die Verifikation der Zahlen ist ein Abgleich Quelle gegen Zitat, keine Wiederholung. Die Wiederholung von M6 im Review (zwei Läufe) steht in der ADR als solche.

## Spec-Regeln und unveränderte Dokumente

| Prüfung | Ergebnis |
|---|---|
| Wort „ADR“ in `spec/spezifikation.md` | 0 Treffer in der ganzen Datei |
| Zugefügte Zeilen der Spezifikation seit 42bd58d~1 (66 Zeilen, auch die fremden Nachträge zu SPEC-013, SPEC-035, SPEC-036 und RPC) auf Slice, Welle, BEO, `docs/`, `src/`, Abwärtslinks | 0 Treffer |
| [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md) | im Diff unverändert |
| [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md) | Inhalt unverändert; Status Proposed zu Accepted und Geschichtszeile durch den eigenen Commit 06d5b5a „[ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md) auf Accepted“, nicht durch diese ADR. Die Aussage der ADR, der Text von [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md) werde „mit dieser ADR nicht geändert“, stimmt. |
| `spec/lastenheft.md`, `spec/architecture.md` | im Diff unverändert |
| ADR-Index | Zeile für [ADR-0013](../plan/adr/0013-backend-grenzen.md) mit Status Proposed |
| Schichtung der ADR | enthält keine Slice- oder Welle-Kennung (Träger werden in Worten beschrieben, der Verweis läuft vom Slice zur ADR) |

## Folge-Slices

| Slice | Befund |
|---|---|
| [slice-backend-streaming-nachweis](../plan/planning/done/welle-backend-streaming/slice-backend-streaming-nachweis.md) | Punkte 2 und 3 nehmen KDoc-Nachzug und OutOfMemoryError auf; Risikozeile „folgt einer ADR im Status Proposed“ steht. Die Punkte sind im Plan noch nicht abgehakt, der Code ist gebaut und gemerged; die Häkchen gehören zur Closure dieses Slice (nicht Gegenstand hier). |
| [slice-attention-cancel-timeout](../plan/planning/open/slice-attention-cancel-timeout.md) | Frist als Teil (b) des dritten Punktes mit Tests und Auslöser für den Schnitt; Nachzug der Frist-KDoc erst mit dem Code. |
| [slice-sim-auswahl-streaming](../plan/planning/open/slice-sim-auswahl-streaming.md) | Start-Trigger „Zeilendarstellung entschieden“ ist an Accepted gebunden und mit Rückführung bei Nichtannahme versehen; Klassen je Spaltenart und suspendierende Quelle übernommen. |
| [slice-release-doku-prozess](../plan/planning/open/slice-release-doku-prozess.md) | Betriebstext in Liefer-Punkt 1, Risiko „Spike-Zahlen als Produktverhalten“ benannt. |
| [slice-release-artefakt-lizenz](../plan/planning/open/slice-release-artefakt-lizenz.md) | Image nimmt JVM-Optionen entgegen, Schalter nicht voreingestellt, Test verlangt; ADR im Bezug. Formatfehler am Rand (V6). |

## Verbleibende offene Punkte (ehrlich geführt, keine Blocker)

- Hänger beim Fangen: akzeptiertes Negativ, mit Betreiberschalter als Abhilfe; Ursache nicht isoliert, nichts am Produkt gemessen.
- Class von `TDS_TIMEOUT` und Verbindungsfolge: ausdrücklich nicht entschieden.
- Hilfsadapter für blockierende Quellen: kein Träger, Trigger benannt.
- Messbasis ist ein Spike mit anderen Versionen als das Repo (Kotlin 2.1.20, coroutines 1.10.1), Einzel- bis Vierfachläufe auf ausgelastetem Rechner.
- Setzungen 5 bis 10 (unter anderem „keine Grenze für kleine Ergebnisse“) hat der Nutzer nicht ausdrücklich gewählt; sie gelten laut ADR, bis er widerspricht.

## Kann Accepted gesetzt werden?

**Nein, noch nicht.** Nach Accepted ist die ADR unveränderlich ([AGENTS.md](../../AGENTS.md) §3.5), eine Korrektur wäre eine neue ADR mit `Supersedes`. Solange sie Proposed ist, sind die folgenden Änderungen ohne Umweg möglich.

### Vorbedingungen (vor dem Setzen von Accepted)

**V1 (Blocker): Die Entscheidung des Nutzers zu [TDS-FA-06](../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände) ist aufzunehmen.** Der Abschnitt „Offen“ führt die Frage noch als offen („Bis dahin gilt B“). Der Nutzer hat entschieden: Option A, das Negative wird als erfüllt gelten gelassen; Fangen im Anfragepfad, Betreiberschalter als Abhilfe, Negativ benannt. Diese ADR kann das Lastenheft nicht ändern und soll es auch nicht; die Entscheidung gehört als Entscheidung des Nutzers (Datum 2026-10-31) in den Entscheidungsteil, im Wortlaut der Setzung der Abnahme: Lastenheft unverändert, die Zusage „statt eines Hängers oder stillen Verbindungsabbruchs“ gilt für diese Fehlerklasse als erfüllt, obwohl der Spike Hänger zeigt (M6) und die Verbindung ohne Antwort schließen kann. Dazu gehören:
- Abschnitt „Offen“ ersetzen: die FA-06-Frage entfernen oder auf „entschieden, siehe Entscheidung 2“ verkürzen (nichts anderes steht dort offen).
- Contra-Zelle von Option B (Alternativen) und Konsequenz „Negativ (a)“ an die Entscheidung anpassen: aus „in Spannung zu [TDS-FA-06](../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände)“ wird „Spannung benannt und vom Nutzer als erfüllt gelten gelassen“; Option C bleibt als Betreiberschalter.
- Geschichtszeile (Proposed, Nutzerentscheidung zu FA-06 aufgenommen).
- Kein Satz, der behauptet, das Lastenheft sei durch Messung erfüllt: es ist eine Abnahme-Entscheidung, keine Messung ([AGENTS.md](../../AGENTS.md) §3.8).
- Optional: in [SPEC-022](../../spec/spezifikation.md#query-backend-vertrag-zu-spec-022) beim Punkt „OutOfMemoryError“ ein Halbsatz „Zusage aus [TDS-FA-06](../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände) hierfür als erfüllt gelassen (Nutzerentscheidung vom 2026-10-31)“, damit die Spec den Befund nicht allein trägt.

**V2 (vor Accepted, klein): „synchron“ in Entscheidung 2 an den gebauten Stand binden.** Ein Satz: „synchron“ wie in SPEC-031; ein Fehler aus Paketbau oder Kanal macht den Strom nicht synchron, auch wenn kein Byte verloren ging; dann schließt die Verbindung ohne ERROR und DONE.

**V3 (vor Accepted, klein, Stände angleichen):** (a) Folgepflicht „Risiko, nicht geprüft: ob detekt das Fangen beanstandet“ durch den Beleg ersetzen (Review F-11 und Gate-Lauf des Streaming-Reviews: keine Beanstandung). (b) „Sieben Quelldateien tragen Sätze offen“: Stand heute fünf (QueryBackend, QueryExecution, QueryEvent, QueryRequest, ValueMapping); BufferedResponse ist entfernt, die KDoc des ResponseSequencer schon angepasst. Die Zuordnung „Träger je Satz“ ist entsprechend zu kürzen.

**V4 (Hinweis an den Nutzer, kein Blocker):** Die Setzungen 5 bis 10 hat der Nutzer nicht ausdrücklich gewählt. Mit Accepted werden sie ohne neue ADR nicht mehr änderbar. Eine Rückfrage „Setzungen bestätigen?“ vor dem Setzen kostet nichts.

**V5 (Verfahren):** Die Änderungen V1 bis V3 sind Text nach der Verifikation. Eine kurze Nachprüfung durch den Verifier oder den Reviewer (nur diese Änderungen, wie bei [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md) mit Nachreview und Verifikation 2) hält die Zusage „Accepted nach Review und Verifikation“ aus dem DoD-Punkt 2 ein.

**Beim Setzen von Accepted mitzuziehen:** Status in der ADR, Indexzeile, Geschichtszeile; danach beginnen die zurückgestellten Nachzüge (KDoc-Sätze, Liefer-Punkte der Folge-Slices, Start-Trigger von slice-sim-auswahl-streaming).

**V6 (LOW, Planner, nicht Voraussetzung für Accepted, aber für die Closure):** Kopf „Berührte Spec-Stellen“ des Slice-Plans ergänzen; Wortlaut von DoD-Punkt 1 angleichen („nicht in diesem Slice“); die zwei zusammengelaufenen Liefer-Punkte in slice-release-artefakt-lizenz auf getrennte Zeilen bringen.

### Gründe, die nicht blockieren

- Alle Review-Befunde behoben oder ehrlich offen; keine DoD-Verletzung gefunden; keine Aussage breiter als ihr Beleg in den geprüften Stellen.
- [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md), [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md) (Inhalt) und Lastenheft unverändert; Spec-Schichtregeln gewahrt.
- Der gebaute Stand des Streaming-Slice widerspricht der ADR nicht.

## Abschluss

`make docs-check` nach Anlage dieses Reports: Ergebnis steht in der Rückmeldung an den Aufrufer.
