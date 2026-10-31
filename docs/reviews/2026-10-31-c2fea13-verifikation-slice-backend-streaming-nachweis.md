# Verifikation: slice-backend-streaming-nachweis (Umsetzung c362b6b, Fix-Runde 8740036 und 69b1f51) — 2026-10-31

**Rolle:** Verifier (Modul 11), frischer Kontext. Frage: Bauen wir es richtig? Geprüft wird die DoD von [slice-backend-streaming-nachweis](../plan/planning/done/slice-backend-streaming-nachweis.md) gegen Code, [SPEC-022](../../spec/spezifikation.md#query-backend-vertrag-zu-spec-022), [SPEC-029](../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung), [SPEC-031](../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität), [SPEC-048](../../spec/spezifikation.md#4-fehler-codes-und-logging-felder), [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md) (Accepted) und [ADR-0013](../plan/adr/0013-backend-grenzen.md) (Proposed). Nicht geprüft wird, ob das Richtige gebaut wird (Validator), und nicht der Diff gegen den Plan ([Review](2026-10-31-c362b6b-slice-backend-streaming-nachweis.md)). Nichts repariert, nichts committet.

**Stand:** Ausgangspunkt `main` bei c2fea13 (Merge der Fix-Runde). Während des Laufs rückte `main` auf 5952d16 vor (Merge der Fix-Runde von [slice-rpc-executesql-parameter](../plan/planning/in-progress/slice-rpc-executesql-parameter.md)); die Mutationsläufe laufen auf einer Kopie von c2fea13. Die Streaming-Dateien (`MessageStream`, `MessageWriter`, `PacketWriter`, `StreamedResponse`, `ResponseSequencer`, `ResponseRun`, `HandshakeSession`) sind zwischen c2fea13 und 5952d16 unverändert; `RequestLoop` ist nur im Zweig der RPC-Ablehnung geändert (siehe unten).

**Eingang:** DoD-Bestätigung des Implementers und Review-Report F-1 bis F-9. Ein eigener Sensor-Beleg des Implementers lag dem Verifier nicht als Artefakt vor; alle Sensoren sind selbst gefahren.

## Ergebnis in einem Satz

Die drei Liefer-Punkte sind im Code und in den Tests gehalten, und die Review-Befunde F-1 bis F-9 sind behoben oder ehrlich offen geführt; die eigenen Mutationen werden bis auf **eine** gerötet: der Fang von `OutOfMemoryError` beim Schreiben des abschließenden DONE (`endGuarded`) ist von keinem Test gehalten (V-1, LOW). Der Slice ist noch nicht geschlossen: alle Häkchen und die Closure-Notiz fehlen, und der KDoc-Nachzug wartet auf `Accepted` der [ADR-0013](../plan/adr/0013-backend-grenzen.md), wie der Plan es vorsieht.

## Sensor-Belege

| Sensor | Ausgang | Beleg |
|---|---|---|
| `make test` (ungefiltert, im Repo) | Exit 0, aber die Stage `test` kam vollständig aus dem Layer-Cache (Schritt 4 von 4 „CACHED“) | Beleg für unveränderte Eingänge eines früheren grünen Laufs, kein frischer Volllauf. Log: `scratchpad/full.log` |
| gefilterte Test-Stage (Kopie von c2fea13, Klassen StreamingDispatchTest, MessageStreamTest, DispatchFailureTest, StreamingSmokeTest, RpcDispatchTest) | M0: 40 Tests, alle PASSED, Exit 0, rund 65 s | `scratchpad/M0.log` |
| `make detekt` | Exit 0; der Fang von `OutOfMemoryError` wird nicht beanstandet, kein `@Suppress` | eigener Lauf (Docker-Layer teilweise aus dem Cache) |
| Rechnung der Zähler-Schranke | stimmt, siehe F-6 | Handrechnung unten |
| Packet-ID-Äquivalenz | stimmt, siehe F-5 | Rechnung unten |
| `make docs-check` nach Anlage dieses Reports | siehe Abschnitt „Abschluss“ | — |

Nicht gefahren: der volle `make gates`-Lauf (der Rechner war ausgelastet; Auftrag: ein Docker-Build gleichzeitig, keine Wiederholungsserien).

## DoD-Konformität

### Punkt 1 — Senke und Verdrahtung

| Zusage der DoD | Befund |
|---|---|
| suspendierende Senken-Schnittstelle, Bytes hinein, Rückstau durch Suspendieren | Bestätigt. `ReplyStream` (`protocol/session`) mit `write` und `finish`, `MessageStream` (`transport/messaging`) implementiert sie über `SuspendingChannel.writeFully`; `StreamedResponse.accept` kehrt erst zurück, wenn `reply.write` die Bytes angenommen hat. Mutation M1, M2, Mheap (unten) halten das. |
| Verdrahtung Sequenzierer, `TokenEncoder`, Paket-Schreiber mit Packet Size der Sitzung | Bestätigt. `RequestLoop.run` ruft `channel.usePacketSize(ready.session.packetSize)` vor der Schleife; `MessageWriter.open` übernimmt die Größe. M5 (Aufruf entfernt) rötet 7 Tests, darunter den Test der Paketgrößen bei 512 und 1000 (beide kleiner als die Voreinstellung 4096 des Schreibers vor dem Login, wie §6 es verlangt). |
| `BufferedResponse` ersetzt | Bestätigt. Unter `src/main` kommt `BufferedResponse` nicht mehr vor; `StreamedResponse` steht an der Stelle. |
| höchstens eine feste Zahl Pakete im Speicher; Zähler-Schranke schließt den Speicher des Schreibers ein; Festlegung gekennzeichnet | Bestätigt als Festlegung. KDoc von `MessageStream` und [SPEC-022](../../spec/spezifikation.md#query-backend-vertrag-zu-spec-022) nennen übereinstimmend: Puffer von einem Paket Nutzdaten, Paket im Schreiben samt Kopien beim Paketbau, Stück des Aufrufers (ein Token); „eine Byte-Zahl ist nicht gemessen“. Das Stück des Aufrufers ist ein ganzes Token und damit nicht durch eine Zahl beschränkt; das steht in der Spec als „(ein Token)“. |
| `MessageWriter.write` hält weiter die ganze Nachricht (Review F8) | Bleibt als Festlegung im KDoc von `MessageWriter` stehen; Ergebnisströme laufen über `open`. Kein Mangel. |
| Abstimmung mit `slice-transport-coroutines-geruest` | `MessageStream` und `open` liegen im selben Paket wie `MessageWriter`; die Schnittstelle `ReplyStream` liegt in `protocol/session` wie `HandshakeChannel`. Schichtregeln: `make detekt` grün. |

### Punkt 2 — Backpressure und Speichergrenze

| Zusage der DoD | Befund |
|---|---|
| deterministischer Zähler-Beleg, kein Zeitverhalten | Bestätigt, und exakt: der Test `Ein haltender Draht begrenzt die Ereignisse, die das Backend vorausliefert` verlangt bei Packet Size 520 genau 409 gelieferte Ereignisse. Der Stillstand ist bewiesen (der Draht meldet sein Warten, die Schleife ist nicht beendet), nicht abgewartet. |
| Heap-Beleg nur als Zusatz, mit Heap-Einstellung und Zeilenform | Bestätigt. 128 MiB in `gradle/test-jvm.init.gradle.kts`, 1 200 000 Zeilen zu 128 Zeichen, Prüfung der Zeilenzahl gegen `Runtime.maxMemory()` zur Laufzeit. Mheap (Senke behält alle Bytes) rötet genau diesen Test. |
| KDoc-Nachzug (Zeilendarstellung, „klein“, blockierendes Backend, Obergrenze) erst nach `Accepted` | Korrekt noch nicht geliefert: [ADR-0013](../plan/adr/0013-backend-grenzen.md) steht auf Proposed, die Sätze „offen ([ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md))“ in `QueryExecution` und weiteren Quelldateien stehen unverändert. Der Plan setzt den Nachzug ausdrücklich nach die Annahme; das ist keine Lücke, sondern offene Lieferung. |

### Punkt 3 — Abbruch und Senkenfehler, Ende-zu-Ende

| Zusage der DoD | Befund |
|---|---|
| Cancel des Collectors und Senkenfehler: `close()` und `cancel()` je genau einmal, kein weiteres Ereignis danach | Bestätigt. Tests `Der Abbruch bei haltendem Draht ruft cancel und close genau einmal` und `Ein Senkenfehler beendet das Lesen und schliesst die Ausfuehrung einmal`; M1 (`cancel()` entfernt) rötet 4 Tests. `Eine beendete Antwort ruft cancel nicht` hält die Gegenseite. |
| Ende-zu-Ende mit Test-Decoder: Metadaten, Zeilenzahl, `DONE_MORE`-Folge, Fehler nach Teilzeilen | Bestätigt in `StreamingSmokeTest` (zwei Tests, beide PASSED). Der Decoder belegt Selbstkonsistenz mit dem Encoder, keine Protokolltreue; das sagt das Klassen-KDoc. |
| `OutOfMemoryError` aus Backend, Encoder oder Senke ergibt `TDS_INTERNAL_ERROR`, solange der Strom synchron ist, sonst Schließen ohne Antwort | Teilweise gehalten, siehe V-1. Backend und Senke an einer Token-Grenze (`acceptGuarded`, `ResponseRun.fault`): gehalten (M12a und M12c rot). Mitten im Token (Paketbau, Kanal): gehalten (Broken-Flag-Mutationen rot). **Beim Schreiben des abschließenden DONE (`endGuarded`): nicht gehalten (M12b grün).** Der Encoder allein ist nicht unabhängig auslösbar; das steht im Plan §6 als Grenze. |
| andere `Error` und Abbruchausnahme werden nicht gefangen | Bestätigt: `Ein anderer Error laeuft nach aussen` (PASSED); der Kanal wird geschlossen, die Ausführung geschlossen. |
| KDoc-Satz zum `OutOfMemoryError` in `QueryExecution` und `ResponseSequencer` nach Annahme der ADR | `ResponseSequencer` und `ResponseRun` tragen den geltenden Sachverhalt ohne Statusvermerk der ADR (F-9 behoben, `grep` auf Proposed unter `src/main` ohne Treffer); `QueryExecution` bleibt bewusst unberührt bis `Accepted`. |

### Nachweis-Pflichten

| Pflicht | Stand |
|---|---|
| jeder Test trägt `@spec` | Stichprobe der vier Streaming-Klassen: jeder neue Test trägt `@spec SPEC-<NNN>` im KDoc. |
| §3.8: Aussagen stehen an einem Test oder sind Festlegung | Für Spec und KDoc der Streaming-Stellen bestätigt (F-2, F-3 behoben). Gemessen ist nichts am Produkt oder an einem Socket; alles gilt für das Test-Backend und den Draht ohne Netz. |
| `make gates` grün | von mir nicht vollständig gefahren; `make detekt` grün, `make test` aus dem Cache grün. |
| Review liegt vor | ja, [Review](2026-10-31-c362b6b-slice-backend-streaming-nachweis.md). |
| Häkchen der DoD, Closure-Notiz, Register, Ausgänge der Risiken in §6 | Alle Kästchen stehen leer, §7 steht auf „Wird bei Closure gefüllt“, die Risiken in §6 tragen größtenteils „offen bis zur Closure“. Vor der Closure zulässig; es ist keine Verletzung, aber auch noch keine Closure. |

## Eigene Mutationen

Kopie von c2fea13 im Scratchpad (`scratchpad/v`), gefilterte Test-Stage, eine Mutation je Lauf, ein Build gleichzeitig. Die Streaming-Tests heißen kurz Dispatch (StreamingDispatchTest), Strom (MessageStreamTest), Smoke (StreamingSmokeTest).

| Lauf | Änderung | Ergebnis |
|---|---|---|
| M0 | keine | grün, 40 Tests |
| M1 | `cancel()` am Backend entfernt | rot, 4 Tests, alle Dispatch (Abbruch, Senkenfehler, OOM, OOM mitten im Token) |
| M2 | volles Paket schon beim letzten Byte schreiben | rot, 6 Tests: **Dispatch (Rückstau-Test, exakt 409) und 5 in Strom**; im Review (M2 damals grün in Dispatch) war das anders, F-6 ist behoben |
| M5 | `usePacketSize` entfernt | rot, 7 Tests (Dispatch 5, Smoke 2) |
| M7 | Sperre in `HandshakeSession.guarded` entfernt | rot, 2 Tests: `DispatchFailureTest` und **Dispatch (Ein haltender Schreibvorgang haelt die Sperre)**; F-7 behoben |
| M12a | Fang von `OutOfMemoryError` in `acceptGuarded` auf anderen Typ | rot, 2 Tests, beide als FAILED mit Testnamen (nicht mehr SKIPPED, F-4 behoben) |
| **M12b** | Fang in `endGuarded` auf anderen Typ | **grün, 40 von 40 PASSED: überlebende Mutation, V-1** |
| M12c | Fang in `ResponseRun.fault` auf anderen Typ | rot, 1 Test |
| Mheap | `StreamedResponse` behält alle Bytes | rot, 1 Test (Smoke, Heap-Test) als FAILED |
| Bflag_after_build | `broken = true` erst nach dem Paketbau | rot, 2 Tests (Paketbau in Dispatch und in Strom) |
| Bflag_only_on_fail | Fehlerflag nur bei Fehler des Kanals (Stand des Reviews) | rot, 3 Tests (Paketbau, Kanal, Dispatch Paketbau) |
| Bflag_never_cleared | Flag nach Erfolg nicht zurückgesetzt | rot, 17 Tests (erwartet, nur zur Gegenprobe) |
| Id_stream_nomod | Modulo der Packet-ID im Strom entfernt | rot, 1 Test (`Der Strom uebergibt dem Paketbau Packet-IDs von 0 bis 255`) |
| Id_stream_mod255 | Modulo 255 statt 256 im Strom | rot, 2 Tests |
| Id_header_mask7f | Header maskiert die ID mit 0x7F | rot, 1 Test (Drahtbytes, `Die Packet-ID laeuft nach 255 auf 0 ueber`) |
| Id_packet_mod255 | in `PacketWriter.packet` Modulo 255 | rot, 1 Test (dieselben Drahtbytes) |

Heap: Mheap ist der einzige Lauf, der den Heap-Test trifft; der Test brach im Review den Lauf ab (SKIPPED), jetzt steht er als FAILED.

## Review-Befunde F-1 bis F-9

| ID | Klasse (Review) | Stand | Beleg |
|---|---|---|---|
| F-1 | MEDIUM, Fehlerantwort hinter halbem Token | behoben | `flush` setzt `broken = true` vor dem Paketbau und setzt es erst nach erfolgreichem Kanal zurück (`broken = sent is ChannelResult.Failed`); damit ist der Strom nach einer Ausnahme im Paketbau und im Kanal nach Entnahme fehlerhaft. Drei Tests halten das (`MessageStreamTest` zwei, `StreamingDispatchTest` Mitte-im-Token); beide Verschiebungen des Flags (nach dem Paketbau, nur bei Fehler) sind rot, die Gegenprobe (nie zurückgesetzt) auch. Die drei OOM-Stellen des Sequenzierers: zwei gehalten (M12a, M12c), eine nicht (M12b, V-1). |
| F-2 | LOW, Speichergröße Spec gegen KDoc | behoben | [SPEC-022](../../spec/spezifikation.md#query-backend-vertrag-zu-spec-022) und KDoc von `MessageStream` nennen dieselben drei Bestandteile und „eine Byte-Zahl ist nicht gemessen“. |
| F-3 | LOW, Herkunft im Klassen-KDoc | behoben | Klassen-KDoc von `StreamingSmokeTest` trennt Zeilenteil (von Hand) und COLMETADATA/DONE (Selbstkonsistenz aus einem Lauf mit drei Zeilen). |
| F-4 | INFO, OOM bricht Lauf, Test SKIPPED | behoben | `serveOrFail` wandelt einen durchgelaufenen `OutOfMemoryError` in `AssertionError`; M12a und Mheap stehen als FAILED mit Testnamen. |
| F-5 | INFO, Redundanz verdeckt Mutation | behoben, und die Redundanz ist äquivalent | siehe unten |
| F-6 | INFO, Schranke gröber als Mechanismus | behoben | exakt 409, siehe unten; M2 rötet jetzt den Rückstau-Test. |
| F-7 | INFO, Sperre nicht von Streaming-Tests gehalten | behoben | eigener Test `Ein haltender Schreibvorgang haelt die Sperre der Sitzung`; M7 rötet ihn. Der Befund zur Dauer der gehaltenen Sperre bleibt als Risiko im Plan §6 („Antwort außerhalb einer einzigen Sperre“) und bei [slice-abbruch-ressourcen-shutdown](../plan/planning/open/slice-abbruch-ressourcen-shutdown.md). |
| F-8 | INFO, Client-Verhalten ungemessen | ehrlich offen | Plan §6 führt den Ausgang „weiter offen: → BEO-TDS/messung-am-echten-sql-server-fehlt“. Nichts gemessen, nichts behauptet. |
| F-9 | LOW, Statusvermerk der ADR im KDoc | behoben | kein „Proposed“ und kein Verweis auf [ADR-0013](../plan/adr/0013-backend-grenzen.md) mehr unter `src/main`. |

### F-5: Packet-ID ohne Modulo in PacketWriter.packet

`PacketWriter.packet` reicht die ID ungekürzt an `PacketHeader`, dessen `toBytes` mit `packetId.toByte()` auf ein Byte kürzt. Äquivalent ist das, weil für jede Ganzzahl `x` gilt: das niedrige Byte von `x % 256` (Rest mit Vorzeichen des Dividenden, wie in Kotlin) ist das niedrige Byte von `x`. Nachgerechnet für alle `x` von -100000 bis 99999 (Python, mit abgeschnittenem Rest): null Abweichungen. Der Draht ist also byte-gleich, mit oder ohne Modulo in `packet`.

Das Modulo im Strom (`nextId = (nextId + 1) % ID_MODULUS`) ist dagegen nicht redundant im Sinne der Tests: der Paketbau-Test (`builder` sieht die ID vor dem Kürzen) verlangt `(k + 1) % 256` und rötet das Entfernen (Id_stream_nomod) und ein falsches Modul (Id_stream_mod255). Die Kürzung im Header ist über die Drahtbytes bei 300 Paketen gehalten (Id_header_mask7f und Id_packet_mod255 rot). Damit ist jede der drei Stellen einzeln gegen Änderung abgesichert; das ersatzlose Entfernen des Modulos in `packet` ist ein äquivalenter Mutant und mit Recht grün. Die Aussage im KDoc von `PacketWriter.packet` („der Header kürzt sie auf ein Byte“) ist damit durch den Quelltext von `toBytes` und die Rechnung gedeckt; ein Test, der die Kürzung im Header allein hält, ist `Die Packet-ID laeuft nach 255 auf 0 ueber`.

### F-6: Rechnung der exakten Schranke (Packet Size 520)

Nutzdaten je Paket 520 - 8 = 512. Das vierte Paket enthält 4 mal 512 = 2048 Byte seit Nachrichtenbeginn. COLMETADATA einer nicht nullbaren Int-Spalte: 1 + 2 + 4 + 2 + 1 + 3 = 13 Byte (Handvektor aus dem Review, nachgerechnet); ROW: 1 + 4 = 5 Byte. 13 + 407 mal 5 = 13 + 2035 = 2048: das vierte Paket endet auf einer Token-Grenze (der Test prüft das selbst per `assertEquals(0, (heldBytes - metaBytes) % rowBytes)`). Der Draht hält nach drei angenommenen Paketen; das vierte Paket geht erst auf den Kanal, wenn das erste Byte der 408. ROW vorliegt, und stoppt dort. Geliefert sind dann: ResultStart (1), 407 Zeilen und die 408. Zeile in Arbeit (408), zusammen 1 + 407 + 1 = 409. Der Test rechnet `1 + (heldBytes - metaBytes) / rowBytes + 1 = 409` und prüft `assertEquals(409, expected)` und `assertEquals(expected, backend.delivered)`. Das Ergebnis eines Paketbaus beim letzten Byte statt beim nächsten (M2) wäre 408 und rötet den Test; ein Vorauslesen um ein ganzes Paket (rund 100 Zeilen) ebenso. Nach `release()` kommen alle `rows + 2` Ereignisse und die volle Bytezahl an.

## Merge-Stelle RequestLoop

SQL_BATCH und RPC laufen beide durch `execute(text, parameters)`, das mit `live.openReply(PacketType.TABULAR_RESULT)` einen `StreamedResponse` baut und `sequencer.run` ruft; danach `finished(fate, reply)` (EOM-Paket) oder `null` bei `CLOSED`. Eine Ablehnung der RPC-Bindung und ein Parserfehler des Batch laufen als ganze Nachricht über `requestFinished` und berühren die Senke nicht. `RpcDispatchTest` (sieben Tests) ist Teil des gefilterten Laufs und grün. Die Fix-Runde des RPC-Slice (543a3db, Merge 5952d16) ändert in `RequestLoop` nur den Zweig `RpcBinding.Rejected` (Protokollfehler des Parsers gegen `TDS_UNSUPPORTED_RPC` mit Grund im Log) und KDoc; `execute`, `finished` und `StreamedResponse` sind unverändert, die Interaktion mit dem Streaming ist damit nicht berührt. Ein Lauf der Streaming-Tests auf 5952d16 ist von mir nicht gefahren.

## Befunde der Verifikation

| ID | Klasse | Befund | Quelle |
|---|---|---|---|
| V-1 | LOW, Mutation überlebt | Der Fang von `OutOfMemoryError` in `ResponseSequencer.endGuarded` (OOM beim Schreiben des abschließenden DONE an einer Token-Grenze) ist von keinem Test gehalten: M12b ist grün, und in `src/test` verweist kein Test auf diese Stelle (die einzigen Dateien mit `OutOfMemoryError` sind `StreamingDispatchTest`, `StreamingSmokeTest`, `MessageStreamTest` und `ScriptedWire`; der Wrapper `ThrowOnce` wirft beim vierten Token, einer ROW). Ohne den Fang liefe der Fehler aus dem Sequenzierer nach außen, der Client bekäme kein `TDS_INTERNAL_ERROR`. Die DoD verlangt „OutOfMemoryError aus einem Testdouble in Backend, Encoder oder Senke ergibt TDS_INTERNAL_ERROR“; der Fall „im letzten Token“ ist ungeprüft. Behebung: ein Test mit `ThrowOnce`, der erst beim DONE wirft, oder ein Streichen der Stelle mit Begründung. | [SPEC-048](../../spec/spezifikation.md#4-fehler-codes-und-logging-felder), [ADR-0013](../plan/adr/0013-backend-grenzen.md) Entscheidung 2 |
| V-2 | INFO, Closure noch offen | Alle Häkchen des Plans fehlen, §7 ist leer, die Risiken in §6 tragen bis auf einige „weiter offen“ keinen Ausgang. Die Closure-Notiz muss *gemessen*, *Handvektor*, *Selbstkonsistenz* und *Festlegung* trennen; die Zähler-Schranke ist ein Handvektor mit Selbstkonsistenz (13 und 5 Byte von Hand, Lauf bestätigt 409), der Heap-Beleg ist gemessen am Test-Backend. Nichts gilt für Socket, Simulations-Backend oder blockierendes Backend. | Plan §2, §5 |
| V-3 | INFO, Ausführungsumgebung | `main` rückte während der Verifikation vor (5952d16); `make test` im Repo lief gegen den Cache und ist kein frischer Volllauf. Vor der Closure ist ein frischer `make gates` auf dem dann aktuellen Stand nötig. | — |

Nichts davon rührt an die Richtigkeit des Rückstaus, der Packet Size oder des Abbruchs.

## Herkunftsstufen und §3.8

Gemessen im Sinne des Projekts ist nichts am Produkt, an einem Socket oder am echten Server; alle Zahlen dieses Reports stammen aus Handrechnung und aus Läufen des geprüften Codes mit dem Test-Backend. Die Spike-Zahlen aus [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md) und [ADR-0013](../plan/adr/0013-backend-grenzen.md) habe ich nicht neu gemessen und nicht übernommen. Die Aussage „Fangen schützt im Spike nicht verlässlich vor hängenden Verbindungen“ ist ein Spike-Befund und bleibt als akzeptiertes Negativ in der ADR.

## Abschluss

Verdikt: **Die DoD ist bis auf V-1 im Code und in Tests gehalten; der Slice kann nach Behebung oder ausdrücklicher Zurückstellung von V-1 und nach der Closure-Arbeit geschlossen werden.** V-1 ist ein LOW und blockiert den Rückstau-Nachweis nicht; er blockiert die Zusage „OutOfMemoryError ergibt TDS_INTERNAL_ERROR“ an der Stelle des abschließenden DONE.

Übergabe an den Planner: V-1 an den Implementer, V-2 und V-3 für die Closure. Scratchpad: Kopie und Logs unter `/tmp/claude-1000/-Development-tdswire/8562f454-84ba-456b-9970-945e69c035f4/scratchpad/` (Unterverzeichnis `v`, Logs M0 bis Id_packet_mod255, `mut.py`, `run.sh`, `batch2.out`).
