# Review-Report: slice-backend-streaming-nachweis — 2026-10-31

**Review-Art:** Code (Diff gegen Plan, Entscheidungen und Hard Rules). Gegen die DoD wird nicht geprüft; das ist die Frage des Verifiers.

**Gegenstand:** Merge-Commit 81d41d5 („Merge slice-backend-streaming-nachweis Umsetzung“), Diff gegen den ersten Parent; Commits c362b6b (Code und Tests), 8250825 (Spec und Plan), 14e5f66 (Plan-Tabelle). Die Zusammenführung mit slice-rpc-executesql-parameter (Merge 092b7a1) ist für `RequestLoop` mitgeprüft.

**Skill:** `.harness/skills/reviewer.md` @ 24a76c3
**Modell:** claude-sonnet-5-5 · **Datum:** 2026-10-31

**Eingangs-Kontext:**

- Slice-Plan slice-backend-streaming-nachweis (Abschnitte 1 bis 3 und 6)
- [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md) (Accepted) und [ADR-0013](../plan/adr/0013-backend-grenzen.md) (Proposed)
- [TDS-FA-08](../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilit%C3%A4t)
- [SPEC-001](../../spec/spezifikation.md#spec-001--zustandsautomat-einer-verbindung), [SPEC-028](../../spec/spezifikation.md#spec-028--prelogin-tls-und-login7-zustands--und-antwortvertrag), [SPEC-029](../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung), [SPEC-031](../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität), [SPEC-022](../../spec/spezifikation.md#query-backend-vertrag-zu-spec-022), [SPEC-048](../../spec/spezifikation.md#4-fehler-codes-und-logging-felder)
- [AGENTS.md](../../AGENTS.md) §3.1, §3.2, §3.6, §3.7, §3.8
- Quelltext zum Abgleich: `MessageStream`, `StreamedResponse`, `ReplyStream`, `HandshakeSession`, `RequestLoop`, `ResponseSequencer`, `ResponseRun`, `TokenEncoder`, `PacketWriter`, `ScriptedWire`, `StreamingDispatchTest`, `StreamingSmokeTest`, `MessageStreamTest`

**Eigene Läufe (Kopie des Repos im Scratchpad, höchstens ein Docker-Build gleichzeitig, nichts gemountet, gefilterte Test-Stage über die Klassen Streaming, MessageStream und Dispatch):**

Die Nummern M1 bis M14 des Implementers lagen dem Review nicht vor; die Mutationen sind eigene und heißen hier Mx.

| Lauf | Änderung | Ergebnis |
|---|---|---|
| M0 | keine | grün (17 Tests der drei Klassen) |
| M1 | `cancel()` am Backend im Sequenzierer entfernt | rot, 3 Tests (Abbruch, Senkenfehler, OutOfMemoryError) |
| M2 | volles Paket sofort statt beim nächsten Byte schreiben | rot, 4 Tests, alle in `MessageStreamTest`; die Dispatch-Tests bleiben grün |
| M4a, M4b, M4c | Modulo der Packet-ID im Strom, in `PacketWriter.packet`, in beiden entfernt | alle drei grün (siehe F-5) |
| M5 | `usePacketSize` in `RequestLoop.run` entfernt | rot, 6 Tests |
| M7 | Sperre in `HandshakeSession.guarded` entfernt | rot, 1 Test (`DispatchFailureTest`, nicht die Streaming-Tests) |
| M12 | Fang von `OutOfMemoryError` in `acceptGuarded` entfernt (Fang auf anderen Typ) | rot, aber der Test steht als SKIPPED, siehe F-4 |
| Mheap | `StreamedResponse` behält alle Bytes | rot, der Heap-Test steht als SKIPPED, siehe F-4 |
| Mprint | Messwert der Zähler-Schranke ausgegeben | geliefert: 503, erlaubt 400 bis 504 |
| detekt | Stage `detekt` auf dem Stand des Merge | grün; der Fang von `OutOfMemoryError` wird nicht beanstandet |
| Volllauf | Stage `test` ungefiltert | aus dem Layer-Cache bestätigt, Exit 0 (der Lauf des Implementers hat denselben Eingang); kein eigener Neulauf |

Rechnung der Zähler-Schranke (`StreamingDispatchTest`, Paketgröße 512, Nutzdaten 504 je Paket): Der Draht nimmt vier Pakete (2016 Byte) an und hält beim fünften. Das fünfte wird erst geschrieben, wenn ein weiteres Byte vorliegt; es steht dann mit 504 Byte im Schreiben, und der Puffer ist leer. Damit liegen höchstens 5 mal 504 = 2520 Byte vor der Zeile in Arbeit. Mit COLMETADATA 13 Byte und ROW 5 Byte sind das höchstens (2520 − 13) / 5 = 501 vollständige Zeilen, dazu das ResultStart und die Zeile in Arbeit: 503 Ereignisse. Gemessen sind 503. Der Test erlaubt oben 1 + (2525 − 13) / 5 + 1 = 504: die Zeile in Arbeit ist darin zweimal gezählt (als 5 Byte und als Ereignis). Unten erlaubt er (4 · 504 − 13) / 5 = 400, die Zahl der Zeilen ohne das ResultStart. Handvektor-Arithmetik der Token (COLMETADATA einer nicht nullbaren Int-Spalte 1 + 2 + 4 + 2 + 1 + 3 = 13 Byte, ROW 1 + 4 = 5 Byte, DONE 1 + 2 + 2 + 8 = 13 Byte) nachgerechnet: stimmt.

---

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| F-1 | MEDIUM | Wirft ein Schritt der Senke einen `OutOfMemoryError`, nachdem ein Token teilweise in den Paketpuffer des Stroms kopiert wurde (Ausnahme aus `flush` beim Anlegen des Pakets) oder nachdem ein Paket aus dem Puffer entnommen, aber nicht angenommen wurde (Ausnahme aus dem Kanal), schreibt `fault` ERROR und DONE hinter das halbe oder fehlende Stück. Der Client bekommt dann einen Tokenstrom, dessen Token-Grenze nicht stimmt, statt `TDS_INTERNAL_ERROR`; die ADR verlangt Fehlerantwort nur, „soweit der Strom synchron ist“, sonst Schließen ohne Antwort, und der Code prüft die Synchronität nicht. Der Test wirft den Fehler in einem Wrapper vor dem Encoder, also an einer Token-Grenze, und deckt diesen Fall nicht. | [ADR-0013](../plan/adr/0013-backend-grenzen.md) Entscheidung 2 · [SPEC-031](../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität) | `src/main/kotlin/io/github/pt9912/tdswire/transport/messaging/MessageStream.kt` · „open = fill < buffer.size \|\| flush(endOfMessage = false)“; `src/test/kotlin/io/github/pt9912/tdswire/protocol/dispatch/StreamingDispatchTest.kt` · „if (offered == at \|\| (repeated && offered > at)) throw OutOfMemoryError“ | ja — Test mit einem Kanal oder Paketbau, der mitten in einem Token `OutOfMemoryError` wirft, danach Tokenstrom zurücklesen | Fehlerantwort ohne Prüfung der Token-Grenze |
| F-2 | LOW | `SPEC-022` sagt „der Speicher des Schreibers beträgt ein Paket“, das KDoc von `MessageStream` nennt ein Paket Nutzdaten, dazu das Paket im Schreiben und das Stück des Aufrufers. Je Paket entstehen zusätzlich `copyOfRange` und die Verkettung mit dem Header, und `drain` legt je Token einen neuen `ByteWriter` an. Die Spezifikation nennt eine Größe, die keine Messung trägt und dem KDoc widerspricht. | AGENTS.md §3.8 · Maintainability | `spec/spezifikation.md` · „der Speicher des Schreibers beträgt ein Paket und hängt nicht an der Größe des Ergebnisses“; `src/main/kotlin/io/github/pt9912/tdswire/transport/messaging/MessageStream.kt` · „dazu das Paket im Schreiben und das Stück des Aufrufers“ | nein — kein Gate vergleicht Spec und KDoc | Speichergröße in Spec und KDoc verschieden |
| F-3 | LOW | Das KDoc der Klasse `StreamingSmokeTest` nennt die Bytes der großen Antwort „Handvektor-Arithmetik“. Der Anteil von COLMETADATA und DONE kommt aus einem Lauf des geprüften Codes mit drei Zeilen (Selbstkonsistenz, so steht es im KDoc der Testfunktion); von Hand gerechnet ist nur der Zeilenteil. Die Klassenzeile ist breiter als die Funktionszeile. | AGENTS.md §3.8 | `src/test/kotlin/io/github/pt9912/tdswire/smoke/StreamingSmokeTest.kt` · „die Bytes der großen Antwort sind\n * Handvektor-Arithmetik“ | nein | Beleg-Herkunft im Klassen-KDoc breiter als im Test |
| F-4 | INFO | Fehlt der Fang von `OutOfMemoryError` (M12) oder hält die Senke alle Bytes (Mheap), steht der betroffene Test als SKIPPED, die Folgetests der Klasse laufen nicht mehr, und die Meldung nennt nur „There were failing tests“ ohne Testnamen; der Fehler verlässt die Testfunktion und bricht den Lauf ab. Das Gate bleibt rot, die Ursache ist im Log nicht benannt. Keine Aktion erwartet; Hinweis für den, der ein rotes `make test` liest. | Maintainability | `src/test/kotlin/io/github/pt9912/tdswire/protocol/dispatch/StreamingDispatchTest.kt` · „Ein OutOfMemoryError im Anfragepfad ergibt TDS_INTERNAL_ERROR und schliesst“ | ja — M12 und Mheap, Logs im Scratchpad | OutOfMemoryError bricht den Testlauf statt den Test zu rotten |
| F-5 | INFO | Die Packet-ID wird in `MessageStream` (`% ID_MODULUS`), in `PacketWriter.packet` (`% ID_MODULUS`) und beim Schreiben des Headers (`toByte`) gekürzt. Jedes der beiden Modulo allein und beide zusammen zu entfernen, lässt die Tests grün (M4a, M4b, M4c), weil der Test die Bytes des Drahts liest. Das Verhalten ist richtig; ein Test, der das Modulo im Quelltext hält, ist an dieser Stelle nicht möglich. | Maintainability | `src/main/kotlin/io/github/pt9912/tdswire/transport/messaging/MessageStream.kt` · „nextId = (nextId + 1) % ID_MODULUS“ | ja — M4a bis M4c | Redundanz verdeckt Mutation |
| F-6 | INFO | Die Zähler-Schranke des Dispatch-Tests trägt den Rückhalt eines vollen Pakets nicht: M2 verschiebt das Ergebnis um höchstens ein Ereignis (503 gegen 502), beide liegen in 400 bis 504; rot wird M2 nur in `MessageStreamTest` (vier Tests). Die obere Grenze liegt um eins über dem hergeleiteten Höchstwert 503, die untere um 103 unter dem gemessenen Wert. Die Schranke erfasst ein Vorauslaufen um ein ganzes Paket (rund 100 Ereignisse), nicht um eines. Die Aussage in `SPEC-022` („höchstens so viele Ereignisse, wie … Platz haben“) ist damit gedeckt, weil die Spec zusätzlich ein gehaltenes Paket zählt. | [SPEC-022](../../spec/spezifikation.md#query-backend-vertrag-zu-spec-022) · [SPEC-031](../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität) | `src/test/kotlin/io/github/pt9912/tdswire/protocol/dispatch/StreamingDispatchTest.kt` · „val upper = 1 + (consumedMax - metaBytes) / rowBytes + 1“ | ja — M2, Mprint | Schranke gröber als der Mechanismus |
| F-7 | INFO | Die Sperre je Schreibvorgang wird von den Streaming-Tests nicht gehalten; M7 wird allein von `DispatchFailureTest` gerötet. Die Sperre bleibt während eines suspendierten Schreibvorgangs gehalten: ein haltender Peer lässt jeden anderen `dispatch` der Sitzung (PeerClosed, LoginTimeout, später Shutdown) bis zum Ende des Schreibvorgangs warten. Das ist vor diesem Slice beim Senden einer ganzen Nachricht ebenso; neu ist die Dauer einer Antwort. Der Plan führt „Antwort außerhalb einer einzigen Sperre“ als offenes Risiko; zuständig ist slice-abbruch-ressourcen-shutdown. | [ADR-0004](../plan/adr/0004-session-zustandsautomat.md) · [SPEC-001](../../spec/spezifikation.md#spec-001--zustandsautomat-einer-verbindung) | `src/main/kotlin/io/github/pt9912/tdswire/protocol/session/HandshakeSession.kt` · „private suspend fun guarded(step: suspend () -> Boolean): Boolean = lock.withLock {“ | ja — M7 | Sperre über suspendierenden Schreibvorgang |
| F-8 | INFO | Der Rückstau-Test für Packet Size und Zähler läuft mit dem Test-Backend; nicht gemessen sind das Verhalten eines Clients bei einer Antwort ohne EOM-Paket (Schließen mitten in der Nachricht) und das Verhalten bei Antworten des Logins in der Größe des Schreibers vor dem Login (4096). Beides ist im Plan als Risiko mit Ausgang geführt (`BEO-TDS/messung-am-echten-sql-server-fehlt`); der Code schließt den Kanal sofort. | [SPEC-031](../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität) | `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/RequestLoop.kt` · „bereits geschriebene Pakete auf dem Draht, ein\n * gehaltenes Paket und das EOM-Paket entfallen“ | nein — braucht einen echten Server | Client-Verhalten ungemessen |
| F-9 | LOW | Das KDoc von `ResponseSequencer` und `ResponseRun` ist zum `OutOfMemoryError` schon umgeschrieben und nennt „([ADR-0013](../plan/adr/0013-backend-grenzen.md), Proposed)“ als Zustand der ADR; der Plan setzt den Nachzug dieses Satzes nach die Annahme der ADR. Der Zustandsvermerk im Kommentar stimmt nach der Annahme nicht mehr und trägt keine Kommentar-Klasse (Zusage, Kopplung, Abgrenzung, Rang-Zeiger, Grenze). `QueryExecution` ist richtig unberührt geblieben. | AGENTS.md §3.7 | `src/main/kotlin/io/github/pt9912/tdswire/protocol/response/ResponseSequencer.kt` · „Proposed); gefangen wird nur dieser Fehler“ | nein | Statusvermerk der ADR im KDoc |

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `transport/messaging/MessageStream.kt`, `MessageWriter.kt` (Rückstau, Rückhalt eines vollen Pakets, EOM, Packet-ID, Fehlerpfad) | geprüft; Befunde F-1, F-2, F-5 |
| `protocol/dispatch/StreamedResponse.kt` | geprüft, ohne Befund (das Schreiben vor dem nächsten Token trägt den Rückstau; M1 und Mheap rot) |
| `protocol/dispatch/RequestLoop.kt` (Merge mit RPC) | geprüft, ohne Befund: `execute` bedient SQL_BATCH und RPC mit derselben Senke; eine abgelehnte Bindung und ein Parserfehler gehen als ganze Nachricht über den Rahmen (`requestFinished`); `finished` und der Fall `CLOSED` (Rückgabe `null`) sind für beide Wege gleich; `usePacketSize` vor der Schleife (M5 rot) |
| `protocol/session/HandshakeSession.kt`, `HandshakeChannel.kt`, `HandshakeEvent.kt`, `HandshakeAutomaton.kt` (Sperre, `replyWritten`, Schließen) | geprüft; Befund F-7; die Fälle `finish` nach Fehler und `RequestFinished` mit geschriebener Antwort sind konsistent |
| `protocol/response/ResponseSequencer.kt`, `ResponseRun.kt` (`cancel()` genau einmal, Fang von `OutOfMemoryError`) | geprüft; Befunde F-1, F-9; `cancel()` und `close()` je einmal in M1; andere `Error` und die Abbruchausnahme laufen nach außen (Test vorhanden) |
| `protocol/token/TokenEncoder.kt` (`drain`) | geprüft, ohne Befund: ein Token wird in einem eigenen Puffer gebaut und erst bei Erfolg angehängt, ein Fehler im Encoder ändert den Strom nicht |
| `gradle/test-jvm.init.gradle.kts` (128 MiB für alle Test-Tasks) | geprüft, ohne Befund: verschärft das Gate (`AGENTS.md` §3.6 verlangt keine ADR); der Heap-Test prüft zur Laufzeit gegen `Runtime.maxMemory()` (1,2 Mio mal 128 Zeichen sind größer als 134 217 728 Byte); Mheap rot; der ungefilterte Lauf ist grün |
| `AGENTS.md` §3.1, §3.2 | geprüft, ohne Befund: kein Host-Aufruf, kein `@Suppress`; `make detekt` grün, der Fang von `OutOfMemoryError` wird nicht beanstandet, keine Änderung an `config/detekt/detekt.yml` |
| Schichtregeln (`ReplyStream` in `protocol/session`, `MessageStream` in `transport/messaging` implementiert ihn) | geprüft, ohne Befund: die Richtung entspricht der des vorhandenen `HandshakeChannel`; kein Eingriff in die Importregeln |
| `spec/spezifikation.md` `SPEC-022` und Plan §3, §6 | geprüft; Befund F-2; die Aussagen sind als Festlegung am Test-Backend gekennzeichnet, nichts vorweggenommen, was erst nach `Accepted` der [ADR-0013](../plan/adr/0013-backend-grenzen.md) stünde (die Sätze in `QueryExecution`, `QueryBackend`, `QueryEvent` bleiben unberührt) |
| `src/test` (`StreamingDispatchTest`, `StreamingSmokeTest`, `MessageStreamTest`, `ScriptedWire`, `WireSession`, `SessionSupport`) | geprüft; Befunde F-1, F-3, F-4, F-6; jeder Test trägt `@spec` |

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 1 |
| LOW | 3 |
| INFO | 5 |

**Finding-Klassen dieses Laufs:** Fehlerantwort ohne Prüfung der Token-Grenze · Speichergröße in Spec und KDoc verschieden · Beleg-Herkunft im Klassen-KDoc breiter als im Test · OutOfMemoryError bricht den Testlauf statt den Test zu rotten · Redundanz verdeckt Mutation · Schranke gröber als der Mechanismus · Sperre über suspendierenden Schreibvorgang · Client-Verhalten ungemessen · Statusvermerk der ADR im KDoc

## Verdikt

**Merge-blockierend:** ja für die Closure — F-1 ist ein MEDIUM mit Failure-Szenario (Fehlerantwort hinter einem halben Token nach `OutOfMemoryError`); es ist vor der Closure zu beheben oder im Plan als benannte Grenze mit Ausgang zu führen. Kein HIGH. Der Merge liegt auf `main`; die Lage ist über die Closure zu entscheiden, nicht über ein Zurücknehmen. Der Rückstau selbst (Suspendieren, Rückhalt eines vollen Pakets, EOM, Packet Size der Sitzung, Abbruch mit `cancel()` und `close()` je einmal) ist durch die eigenen Mutationen M1, M2, M5, M7 gehalten.

**Übergabe:** Findings gehen an den Implementer; die Finding-Klassen gehen zusätzlich in die Slice-Closure §7. Dieser Report ersetzt keine Verifikation.
