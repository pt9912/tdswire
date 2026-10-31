# Slice slice-backend-streaming-nachweis: Backpressure, Speichergrenze und Abbruch über die Kette Test-Backend → Sequenzierer → Encoder → Senke

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-backend-streaming

**Bezug:** [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) (Vorleistung), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilit%C3%A4t), [TDS-QA-04](../../../../spec/lastenheft.md#tds-qa-04--performance) (Vorleistung), [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md), [ADR-0013](../../adr/0013-backend-grenzen.md)

**Berührte Spec-Stellen:** SPEC-002 · SPEC-031 · SPEC-035 (nur Pflicht „nicht vollständig gehalten“) · ARC-003 (nur Senken-Schnittstelle) · ARC-007 · ARC-015

**Verantwortlich:** pt9912.

**Autor:** pt9912. **Datum:** 2026-10-30.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Die Kette aus Test-Backend, Port, Typabbildung, Sequenzierer, `TokenEncoder` und Paket-Schreiber ist an eine suspendierende Senke gebunden und belegt in einem Lauf: ein Ergebnis, dessen Materialisierung im Testlauf den gesetzten Heap überschritte, wird übertragen; das Backend läuft bei einer langsamen Senke nur begrenzt voraus; ein Senkenfehler oder ein Cancel beendet das Lesen und schließt die Ausführung ([TDS-FA-08.a](../../../../spec/spezifikation.md#tds-fa-08a--backend-vertrag) Schritt 3, `SPEC-031` letzter Satz). Der Nachweis gilt für Fake und Testsenke, nicht für das Simulations-Backend ([TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) Boundary) und nicht für einen Socket.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Das Simulations-Backend und das Boundary-Kriterium von [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) am Simulations-Backend — Welle [welle-simulations-backend](../welle-simulations-backend.md); hier ist der Beleg am Fake Vorleistung, kein Beleg für [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend).
- Lasttest mit Durchsatz, p95 und gleichzeitigen Sitzungen ([TDS-QA-04](../../../../spec/lastenheft.md#tds-qa-04--performance)) — `slice-qa-performance`; hier nur Zähler- und Heap-Belege im Unit-Test-Maßstab, keine Leistungsaussage.
- Nachrichtenschreiber über einem Kanal, Socket, TLS — `welle-transport-tls`; die Senke ist eine Schnittstelle mit Testimplementierung. Eine zweite, abweichende Definition derselben Schnittstelle neben `slice-transport-coroutines-geruest` wird vermieden (§6).
- Parallele Verbindungen und Last — `slice-qa-stabilitaet`.
- Blockierendes Backend hinter dem `Flow`, nicht unterbrechbare Aufrufe und `CANCEL_TIMEOUT` — entschieden in [ADR-0013](../../adr/0013-backend-grenzen.md) (Status Accepted): Vertragspflicht „suspendierend“, Umhüllung beim Integrator; das Test-Backend ist suspendierend, der Slice prüft die Pflicht nicht (nicht maschinell prüfbar). Die Frist `deadlineEpochMillis` setzt `slice-attention-cancel-timeout` durch, nicht dieser Slice.
- Ein echter Heap-Überlauf im Test — die Behandlung des `OutOfMemoryError` ist entschieden ([ADR-0013](../../adr/0013-backend-grenzen.md): im Anfragepfad fangen, `TDS_INTERNAL_ERROR`, Verbindung schließt) und gehört in den dritten Liefer-Punkt; der Test erzeugt den Fehler mit einem Testdouble, nicht mit einem echten Überlauf.
- ATTENTION-Paket und DONE mit ATTENTION-Status — `welle-fehler-abbruch`; hier endet das Lesen durch Abbruch des Collectors und Senkenfehler, nicht durch ein Protokollereignis.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [x] **Senke und Verdrahtung** ([TDS-FA-08.a](../../../../spec/spezifikation.md#tds-fa-08a--backend-vertrag) Schritt 3): suspendierende Senken-Schnittstelle (Bytes hinein, Rückstau durch Suspendieren), Verdrahtung Sequenzierer → `TokenEncoder` → Paket-Schreiber mit Packet Size, und zwar der Packet Size je Sitzung (`AuthenticatedSession.packetSize`, vom Handshake angekündigt, nicht angewandt; §6): die Verdrahtung stellt den Schreiber darauf ein oder nennt die Lücke weiter; Puffergröße als gekennzeichnete Festlegung; die Schnittstelle ist mit `slice-transport-coroutines-geruest` abgestimmt (§6). Der `MessageWriter` des Transports erzeugt alle Pakete einer Nachricht auf einmal (`PacketWriter.split`) und prüft keine Obergrenze der Nachrichtengröße (Review F8, Verifikation V-6); der Speicher des Schreibers wächst damit mit der Nachricht. Die Verdrahtung ersetzt `BufferedResponse` (`protocol/dispatch`), die Senke des SQL_BATCH-Dispatch, die heute die ganze Antwort ohne Obergrenze und ohne Rückstau puffert (Festlegung des Slice `slice-sql-batch-dispatch`, §6 dort). Die Verdrahtung dieses Slice hält höchstens eine feste Zahl Pakete im Speicher, und die Zähler-Schranke des folgenden Punkts schließt den Speicher des Schreibers ein; wo der Schreiber dafür erweitert oder die Nachricht geschnitten wird, ist eine als Festlegung gekennzeichnete Entscheidung des Slice.
- [x] **Backpressure und Speichergrenze** ([TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), `SPEC-031` „Backpressure begrenzt Puffer“): bei einer langsamen oder angehaltenen Senke zählt das Test-Backend höchstens eine feste Zahl vorausgelieferter Ereignisse (deterministischer Beleg über Zähler, kein Zeitverhalten); ein Ergebnis oberhalb des gesetzten Heaps des Testlaufs wird vollständig übertragen, Heap-Belege nur als Zusatz zur Zähler-Schranke und mit Angabe von Heap-Einstellung und Zeilenform (Messbedingung). Zu diesem Punkt gehört der KDoc-Nachzug der Sätze, die [ADR-0013](../../adr/0013-backend-grenzen.md) schließt, soweit sie nicht den `OutOfMemoryError` betreffen: Zeilendarstellung und Klassen je Spaltenart (`QueryEvent.Row`, `QueryParameter`, `ValueMapping`), Grenze für „klein“ (`QueryExecution`), blockierendes Backend (`QueryBackend`) und die Obergrenze der Antwort (`BufferedResponse`, falls die Datei nach Punkt 1 noch besteht). Die Sätze „offen“ in `QueryEvent` zur Unterscheidung statement- und batch-abbrechender Fehler und zur Fehlerklasse von `Failure` bleiben, weil die ADR sie nicht schließt; die Frist gehört nicht hierher. Der Nachzug beginnt erst, wenn die ADR `Accepted` ist, und ändert nur Kommentare.
- [x] **Abbruch und Senkenfehler, Ende-zu-Ende** ([TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilit%C3%A4t)): ein Cancel des Collectors und ein Fehler der Senke (Schreiben schlägt fehl) beenden das Lesen und rufen `close()` und `cancel()` am Backend genau einmal; kein weiteres Ereignis wird danach gelesen; der Ende-zu-Ende-Lauf (Welle-Datei §3) liest die Senkenausgabe mit dem Test-Decoder des Codecs zurück und belegt Metadaten, Zeilenzahl, `DONE_MORE`-Folge und den Fehler nach Teilzeilen. Der Punkt nimmt die Fehler von Senke und Encoder einschließlich `OutOfMemoryError` auf ([ADR-0013](../../adr/0013-backend-grenzen.md), ohne neuen Slice): ein `OutOfMemoryError` aus einem Testdouble in Backend, Encoder oder Senke ergibt `TDS_INTERNAL_ERROR` (ERROR und DONE mit Fehlerstatus, Verbindung schließt nach der Antwort), solange der Strom synchron ist, sonst schließt die Verbindung ohne Antwort; die Fangstelle des Sequenzierers um das Backend bleibt, wie sie ist (jedes `Throwable` außer der Abbruchausnahme); aus Encoder und Senke werden andere `Error` und die Abbruchausnahme nicht gefangen. Mit dem Code des Fangens zieht dieser Punkt den KDoc-Satz zum `OutOfMemoryError` in `QueryExecution` und `ResponseSequencer` nach (nach der Annahme der ADR, nur Kommentar).

Nachweis-Pflichten für alle drei Punkte:

- [x] Jeder Test trägt im KDoc über der Testfunktion den Block-Tag `@spec SPEC-<NNN>`; die Tests laufen in `make test` und sind grün.
- [x] Jede Aussage im Quelltext (KDoc) und in Spec oder Vertrag steht an einem Test oder ist als Festlegung gekennzeichnet; die Closure-Notiz trennt *gemessen*, *Handvektor*, *Selbstkonsistenz* und *Festlegung* wie `slice-codec-tds-paket` (`BEO-TDS/aussagen-ueber-messung-hinaus`). Die Messung in [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) gilt für den Spike, nicht für den Quelltext dieses Slice.
- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `protocol/session` (`ReplyStream`, `HandshakeChannel.openReply`/`usePacketSize`), `protocol/dispatch` (`StreamedResponse` ersetzt `BufferedResponse`, `RequestLoop`), `protocol/token` (`TokenEncoder.drain`), `protocol/response` (`ResponseSequencer`: `cancel()` bei Abbruch, `OutOfMemoryError`), `transport/messaging` (`MessageStream`, `MessageWriter.open`), `transport/framing` (`PacketWriter.packet`) | neu / update | suspendierende Senke: Bytes hinein, Rückstau durch Suspendieren; höchstens ein gehaltenes Paket im Schreiber; Packet Size der Sitzung wird am Schreiber eingestellt |
| `src/test/kotlin/io/github/pt9912/tdswire/testsupport/` | update | Test-Backend: Generator für große Ergebnisse (Zeilen on demand), Zähler der vorausgelieferten Ereignisse; langsame und angehaltene Testsenke |
| `src/test/kotlin/io/github/pt9912/tdswire/smoke/` | neu | Ende-zu-Ende-Lauf der Welle; `@spec`-Tag je Test; der Smoke-Test darf die Schichten im Test-Quelltext verbinden, die Importregeln für Produktionscode gelten unverändert |
| `gradle/test-jvm.init.gradle.kts` | update | Heap-Einstellung des Testlaufs für den Speicher-Beleg (der Init-Skript-Weg lässt `build.gradle.kts` und damit die Stage `deps` unberührt); eine Einstellung, die das Gate verschärft, braucht keine ADR (`AGENTS.md` §3.6) |
| `spec/spezifikation.md` `SPEC-022` | update | §Pflichten: „nicht beliebig voraus“ mit dem gebauten Stand und der Grenze der Aussage |

- Reihenfolge: Senke und Verdrahtung, dann Zähler-Beleg, dann Heap-Beleg, dann Abbruch, zuletzt der Ende-zu-Ende-Lauf.
- Der Implementer liest `PacketWriter` und die Senken-Schnittstelle aus `slice-transport-coroutines-geruest` und passt diese Tabelle an.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): `slice-backend-sequenzierer` liegt in `done/`, `slice-transport-coroutines-geruest` liegt in `done/` (erfüllt; dessen Nachrichtenschreiber ist die Referenz für die Senke), `make gates` ist auf `main` grün, und ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2; Schnittvorschlag: Slice A = Senke, Zähler-Beleg und Abbruch, Slice B = Heap-Beleg und Ende-zu-Ende-Lauf.
- `in-progress` → `open` (blockiert — Carveout?): die Senken-Schnittstelle widerspricht der des Transports und lässt sich nicht abstimmen, oder der Heap-Beleg ist im Test-Gate nicht stabil reproduzierbar (siehe §6).

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün (einschließlich `make test`), der Review-Report liegt vor, und die Closure-Notiz trägt die Zuordnung je Aussage (*gemessen* · *Handvektor* · *Selbstkonsistenz* · *Festlegung*) mit Messbedingungen sowie den Steering-Loop-Lerneintrag; sie benennt, was der Nachweis nicht trägt (Simulations-Backend, Socket, blockierendes Backend).

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **Speicher des Nachrichtenschreibers wächst mit der Nachricht:** `MessageWriter` aus `slice-transport-coroutines-geruest` hält alle Pakete einer Nachricht im Speicher; ein Ergebnisstrom über einem Schreiber dieser Form hielte das Ergebnis im Speicher, und der Nachweis der Speichergrenze wäre leer. — **Ausgang:** entfallen: `MessageStream` (`MessageWriter.open`) hält höchstens ein Paket Nutzdaten und eines im Schreiben, die Ergebnisströme laufen darüber, und die Zähler-Schranke schließt den Speicher des Schreibers ein (exakt 409 Ereignisse bei Packet Size 520, Mutation M2 rot); `MessageWriter.write` hält weiter die ganze Nachricht und steht als Festlegung im KDoc.
- **Zwei Definitionen der Senken-Schnittstelle:** `slice-transport-coroutines-geruest` schneidet den rückstaufesten Nachrichtenschreiber; dieser Slice braucht dieselbe Rolle im Test. — **Ausgang:** entfallen: es gibt eine Definition (`ReplyStream` in `protocol/session`, umgesetzt von `MessageStream` im Paket von `MessageWriter`); die Verifikation fand keine zweite, die Schichtregeln (`make detekt`) sind grün.
- **Heap-Beleg ist GC-abhängig:** Der Spike nahm Stichproben nach `System.gc()` alle 500 ms ([ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)), ein Wert „0“ bei kurzen Läufen hieß „nichts Größeres beobachtet“; ein Heap-Test im Gate kann flackern. Der deterministische Beleg ist die Zähler-Schranke. — **Ausgang:** entfallen: der Heap-Beleg ist ein Zusatz zur Zähler-Schranke und flackerte in keinem der Läufe (Implementer, Verifikation, frischer Lauf der Closure ohne Cache); der Heap steht bei 128 MiB, die Zeilenzahl wird gegen `Runtime.maxMemory()` geprüft. Nicht nachgewiesen ist die Stabilität auf anderen Rechnern oder bei anderem Collector.
- **Aussagekraft der Zahlen:** Die Spike-Werte (53 755 vorausgelieferte Zeilen, 4 MB Live-Heap) sind weder erklärt noch übertragbar ([ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)); der Slice übernimmt keine davon als Soll. Die Schranke ist eine Festlegung dieses Slice. — **Ausgang:** entfallen: keine Spike-Zahl ist übernommen; die Schranke 409 ist eine Festlegung des Slice aus Handrechnung (13 und 5 Byte je Token) und Lauf.
- **Zeilendarstellung und Heap-Grenze eines materialisierenden Backends:** entschieden in [ADR-0013](../../adr/0013-backend-grenzen.md) (Status Accepted): `List<Any?>` mit festgeschriebenen Klassen je Spaltenart; die Heap-Grenze löst der Strom mit Rückstau, nicht die Darstellung. Der Nachweis betrifft ein streamendes Backend. — **Ausgang:** entfallen als offener Punkt dieses Slice (entschieden in [ADR-0013](../../adr/0013-backend-grenzen.md)); die ADR ist `Accepted`.
- **Grenze für „kleine“ Ergebnisse (fertige Liste):** entschieden in [ADR-0013](../../adr/0013-backend-grenzen.md) (Status Accepted) als Setzung (Voreinstellung, vom Nutzer nicht ausdrücklich gewählt): keine Grenze im Vertrag, Pflichttext; der Nachweis misst sie nicht. — **Ausgang:** entfallen als offener Punkt dieses Slice (entschieden in [ADR-0013](../../adr/0013-backend-grenzen.md)); die ADR ist `Accepted`.
- **`OutOfMemoryError`:** entschieden in [ADR-0013](../../adr/0013-backend-grenzen.md) (Status Accepted): im Anfragepfad fangen, `TDS_INTERNAL_ERROR`, Verbindung schließt; `-XX:+ExitOnOutOfMemoryError` ist Betreiberschalter im Betriebstext. Träger des Codes ist der dritte Liefer-Punkt dieses Slice. Akzeptiertes Negativ der Entscheidung: im Spike blieben beim Fangen Coroutinen hängen (vier von vier Läufen). — **Ausgang:** entfallen als offener Punkt: der Fang ist gebaut und belegt (Backend und Senke an der Token-Grenze, mitten im Token, beim abschließenden DONE; andere `Error` laufen nach außen); das akzeptierte Negativ der ADR (Hänger im Spike) ist nicht widerlegt, der Test löst den Fehler mit einem Testdouble aus.
- **detekt und das Fangen von `OutOfMemoryError`:** ob detekt das Fangen von `OutOfMemoryError` beanstandet, ist nicht geprüft; ein `@Suppress` ist verboten (`AGENTS.md` §3.2), eine Regel-Ausnahme stünde nur in `config/detekt/detekt.yml` mit Begründung und einer ADR oder einem Carveout. Der Implementer prüft das vor dem Fang (engster Sensor: `make detekt`). — **Ausgang:** entfallen: `make detekt` beanstandet den Fang nicht (Review und Verifikation je ein Lauf, grün), kein `@Suppress`, keine Änderung an `config/detekt/detekt.yml`.
- **Blockierendes Backend und Frist (`deadlineEpochMillis`):** entschieden in [ADR-0013](../../adr/0013-backend-grenzen.md) (Status Accepted): Pflicht „suspendierend“, Hilfsadapter nur beim Trigger „echtes blockierendes Backend“; die Frist setzt der Protokollkern durch (Träger: `slice-attention-cancel-timeout`). Dieser Slice prüft beides nicht. — **Ausgang:** weiter offen: → `slice-attention-cancel-timeout` (Frist) und [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) Re-Evaluierungs-Trigger (blockierendes Backend).
- **Verzögerung durch den zurückgehaltenen Abschluss:** am Fake nur als Ereigniszahl messbar, nicht als Latenz. — **Ausgang:** weiter offen: → `slice-qa-performance`.
- **Test-Aussagen über Streaming gelten für den Fake:** Ohne Simulations-Backend und ohne Socket sagt der Lauf nichts über [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) und den Durchsatz im Produkt (`BEO-TDS/aussagen-ueber-messung-hinaus`). — **Ausgang:** weiter offen: → [welle-simulations-backend](../welle-simulations-backend.md) (Boundary-Kriterium von [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) am Simulations-Backend), [slice-qa-performance](../open/slice-qa-performance.md) (Durchsatz) und `BEO-TDS/test-belegt-nur-den-fake`.
- **`BufferedResponse` hält die ganze Antwort im Speicher:** Die Senke des SQL_BATCH-Dispatch ([slice-sql-batch-dispatch](../done/slice-sql-batch-dispatch.md)) puffert ohne Obergrenze und ohne Rückstau; `TokenEncoder.toByteArray` kopiert zusätzlich. Ein Backend im Betriebsweg würde damit [TDS-FA-08.a](../../../../spec/spezifikation.md#tds-fa-08a--backend-vertrag) („nicht vollständig gehalten“) unterschreiten. Heute setzt kein Betriebsweg ein Backend; bis zum Ersatz gilt das im Spike Gemessene zum `OutOfMemoryError` ([ADR-0013](../../adr/0013-backend-grenzen.md), M5, M6) für den Dispatch. Dieser Slice ersetzt die Stelle (erster Liefer-Punkt); belegt wird sie mit dem Zähler- und Heap-Beleg des zweiten Punkts am Dispatch, nicht nur an einer Testsenke. — **Ausgang:** entfallen: `BufferedResponse` ist unter `src/main` ersetzt (`StreamedResponse`); Zähler-Schranke und Heap-Beleg laufen am Dispatch, und die Mutation „`StreamedResponse` behält alle Bytes“ rötet den Heap-Test.
- **Disconnect während der Anfrage erst beim Senden bemerkt:** Die Schleife des Dispatch liest während einer Anfrage nicht; das Backend läuft bis zum Ende. Dieser Slice behandelt den Abbruch durch Senkenfehler, nicht den Disconnect der Gegenstelle. Der Träger des Disconnects in `RUNNING` und `STREAMING` ist DoD-Punkt 1 von [slice-abbruch-ressourcen-shutdown](../open/slice-abbruch-ressourcen-shutdown.md); [slice-attention-cancel-timeout](../open/slice-attention-cancel-timeout.md) schließt ihn aus. — **Ausgang:** weiter offen: → [slice-abbruch-ressourcen-shutdown](../open/slice-abbruch-ressourcen-shutdown.md).
- **Packet Size der Sitzung ohne Anwender:** [slice-handshake-login](../done/welle-handshake-login/slice-handshake-login.md) nennt dem Client per ENVCHANGE eine Packet Size und führt sie in `AuthenticatedSession.packetSize`; der Schreiber des Transports arbeitet mit der Größe, mit der er gebaut wird, und nichts stellt ihn auf die Größe der Sitzung ein. Fordert ein Client eine kleinere Größe als die des Schreibers, kann er Pakete über seiner Größe sehen. Dieser Slice verdrahtet den Paket-Schreiber als Erster mit einer Größe und ist der Träger der Anwendung (erster Liefer-Punkt); belegt wird sie mit einem Test, der eine Sitzungsgröße kleiner als die Voreinstellung des Schreibers setzt und die Paketgrößen der Ausgabe prüft. Gemessen ist nicht, wie ein Client auf Pakete über seiner Größe reagiert. — **Ausgang:** entfallen: `RequestLoop.run` ruft `usePacketSize(packetSize)` vor der Schleife, die Tests prüfen die Paketgrößen bei 512 und 1000 (Mutation ohne den Aufruf: 7 Tests rot); das Client-Verhalten bei Paketen über seiner Größe bleibt ungemessen (`BEO-TDS/messung-am-echten-sql-server-fehlt`).
- **`cancel()` am Backend aus dem Sequenzierer und aus ATTENTION:** Der `ResponseSequencer` ruft `cancel()` bei Abbruch der Coroutine und bei Senkenfehler genau einmal je Anfrage. [slice-attention-cancel-timeout](../open/slice-attention-cancel-timeout.md) signalisiert Cancel bei ATTENTION ebenfalls; kommen beide Wege zusammen, wäre es zweimal. Dieser Slice prüft nicht, wie beide zusammenpassen. — **Ausgang:** weiter offen: → [slice-attention-cancel-timeout](../open/slice-attention-cancel-timeout.md).
- **Antwort außerhalb einer einzigen Sperre:** Die Pakete der Antwort gehen je Schreibvorgang unter der Sperre des Sitzungsrahmens hinaus, nicht die ganze Antwort unter einer Sperre ([ADR-0004](../../adr/0004-session-zustandsautomat.md)); ein Übergang einer zweiten Coroutine läuft zwischen zwei Schreibvorgängen. Das ist die Voraussetzung dafür, dass ATTENTION während des Streamens ankommen kann; getestet ist nur der Übergang zwischen zwei Schreibvorgängen, nicht ATTENTION. — **Ausgang:** weiter offen: → [slice-attention-cancel-timeout](../open/slice-attention-cancel-timeout.md) (ATTENTION während des Streamens); die Sperre je Schreibvorgang hält ein eigener Test (Mutation ohne Sperre rot), die Dauer eines haltenden Schreibvorgangs trägt [slice-abbruch-ressourcen-shutdown](../open/slice-abbruch-ressourcen-shutdown.md).
- **Antwort bei Senkenfehler bleibt abgebrochen auf dem Draht:** Bereits geschriebene Pakete einer Antwort ohne EOM-Paket stehen auf dem Draht, wenn die Verbindung ohne Abschluss schließt (`SPEC-031`); ein gehaltenes Paket entfällt. Wie ein Client das liest, ist nicht gemessen. — **Ausgang:** weiter offen: → `BEO-TDS/messung-am-echten-sql-server-fehlt`.
- **`OutOfMemoryError`-Behandlung folgt [ADR-0013](../../adr/0013-backend-grenzen.md) (Status Accepted):** Der Code fängt `OutOfMemoryError` im Sequenzierer nach dieser ADR. Der Test löst den Fehler mit einem Testdouble in Backend und Senke aus (der Fehler im Encoder allein ist nicht unabhängig auslösbar, er läuft durch dieselbe Senke). — **Ausgang:** entfallen: der Code folgt der ADR (`Accepted`) und ist an Backend und Senke belegt; der Kanal-Fall (Ausnahme aus dem Kanal nach Entnahme eines Pakets) ist nur am `ScriptedWire` belegt, nicht an einem echten Kanal (`BEO-TDS/test-belegt-nur-den-fake`).

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register (vorhandene `BEO-<KUERZEL>/<slug>` **zitieren** statt neu
formulieren — sonst zählt das Register zwei Namen getrennt) ·
`grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln (das
Feld `liegt in` steht **nur**, wenn mit diesem Slice wirklich etwas verkörpert
wurde; Feld und Zielort auf **einer** Zeile, Sektionsangabe innerhalb der
Backticks). Ging der Gegenstand an einen anderen Slice oder entfiel er, trägt
diese Sektion die Zeile `Gegenstand:` mit Kennung oder Grund und jedes Risiko
aus §6 seinen Ausgang; die Liefer-Punkte der DoD bleiben leer
(`modul-05-planning-harness.md` §Ein Slice, dessen Gegenstand ein anderer
übernimmt).

- **Was hat funktioniert:** Der Schnitt trug: eine Senke (`ReplyStream`, `MessageStream`), die Verdrahtung mit Packet Size der Sitzung, der Ersatz von `BufferedResponse` durch `StreamedResponse` und der Nachweis in einem Lauf (Zähler-Schranke, Heap, Abbruch, Ende-zu-Ende) lagen in drei Liefer-Punkten. Die Mutationen der Verifikation (M1 bis M12c, Mheap, Packet-ID) röteten bis auf eine jede Zusage; die eine überlebende (V-1) ist behoben.
- **Was ging anders als geplant:** (a) Der Review fand F-1 (MEDIUM): eine Fehlerantwort hinter einem halben Token; die Lösung (Fehlerflag im Strom vor dem Paketbau) war im Plan nicht vorgesehen. (b) Die Zähler-Schranke wurde nach F-6 von einem Bereich auf die exakte Zahl 409 geschärft. (c) V-1: der Fang beim abschließenden DONE hatte keinen Test; Behebung im Commit 0af01b5 (Test, kein Produktionscode). (d) Der KDoc-Nachzug lag hinter `Accepted` der [ADR-0013](../../adr/0013-backend-grenzen.md) und kam als eigener Commit 24f09a9.
- **Reports:** [Review F-1 bis F-9](../../../reviews/2026-10-31-c362b6b-slice-backend-streaming-nachweis.md), [Verifikation V-1 bis V-3](../../../reviews/2026-10-31-c2fea13-verifikation-slice-backend-streaming-nachweis.md). Die Fix-Runden (8740036, 69b1f51) prüfte die Verifikation. **Wer was gesehen hat:** die Mini-Runde 0af01b5 (Test zu V-1, einschließlich der Aussage „Mutante M12b danach rot“) hat nur der Implementer gesehen; ihr Test läuft im frischen Lauf der Closure grün, die Mutation ist von keinem anderen Kontext wiederholt. Der KDoc-Nachzug 24f09a9 ist vom Planner als frische Sicht geprüft (nur Kommentare in `QueryEvent`, `QueryRequest`, `QueryBackend`, `QueryExecution`, `ValueMapping`; Sätze entsprechen [ADR-0013](../../adr/0013-backend-grenzen.md); die Sätze „offen“ zu Fehlerklasse, statement-/batch-abbrechenden Fehlern und Frist bleiben; `grep` auf die alten „offen ([ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md))“-Sätze zur Zeilendarstellung unter `src/main` ohne Treffer); ein Review durch einen weiteren Kontext liegt nicht vor.
- **Frischer Lauf der Closure:** `docker build --no-cache-filter test --target test .` auf dem Stand nach dem Merge von main (7330905), einmal, Rechner ausgelastet: Exit 0, 647 Tests PASSED, keine FAILED, BUILD SUCCESSFUL in 1 m 20 s, die Stage `test` nicht aus dem Cache. Darin der Test `Ein OutOfMemoryError beim abschliessenden DONE ergibt ERROR oder schliesst ohne Antwort` (Behebung V-1).

**Zuordnung je Aussage.** *gemessen* heißt ein Lauf, der besteht, am Test-Backend (Fake) und an einer Testsenke, nicht am Produkt; *Handvektor* ein Fall mit Rechnung von Hand; *Selbstkonsistenz* Code gegen Code (Decoder gegen Encoder); *Festlegung* eine Entscheidung ohne Messung.

| Stärke | Aussage | Beleg |
|---|---|---|
| gemessen (Fake, Testsenke) | Ein haltender Draht begrenzt die vorausgelieferten Ereignisse auf exakt 409 bei Packet Size 520; nach `release()` kommen alle Ereignisse und alle Bytes an | `StreamingDispatchTest` (Test „Ein haltender Draht begrenzt die Ereignisse, die das Backend vorausliefert“); Mutationen M2 (rot in Dispatch und `MessageStreamTest`) |
| gemessen (Fake, Testsenke) | Ein Ergebnis von 1 200 000 Zeilen zu 128 Zeichen, größer als der gesetzte Heap von 128 MiB (zur Laufzeit gegen `Runtime.maxMemory()` geprüft), wird vollständig übertragen | `StreamingSmokeTest` (Heap-Test); Mutation Mheap rot; Messbedingung: `gradle/test-jvm.init.gradle.kts`, 128 MiB |
| gemessen (Fake, Testsenke) | Cancel des Collectors und Senkenfehler rufen `close()` und `cancel()` genau einmal; danach wird nichts mehr gelesen; eine beendete Antwort ruft `cancel()` nicht | `StreamingDispatchTest`; Mutation M1 rot (4 Tests) |
| gemessen (Fake, Testsenke) | Die Packet Size der Sitzung wird am Schreiber angewandt (512 und 1000 kleiner als die Voreinstellung 4096) | `StreamingDispatchTest`, `StreamingSmokeTest`; Mutation M5 rot (7 Tests) |
| gemessen (Fake, Testsenke) | `OutOfMemoryError` aus Backend und Senke ergibt ERROR und DONE bei synchronem Strom, sonst schließt die Verbindung ohne Antwort (an der Token-Grenze, mitten im Token, beim abschließenden DONE); andere `Error` und die Abbruchausnahme werden nicht gefangen | `StreamingDispatchTest`, `MessageStreamTest`; Mutationen M12a, M12c, Fehlerflag-Mutationen rot; die Mutation M12b (abschließendes DONE) rot nur laut Implementer |
| gemessen (Fake, Testsenke) | Die Sperre der Sitzung bleibt während eines haltenden Schreibvorgangs gehalten | `StreamingDispatchTest` („Ein haltender Schreibvorgang haelt die Sperre der Sitzung“); Mutation M7 rot |
| Handvektor | Token-Größen COLMETADATA einer nicht nullbaren Int-Spalte 13 Byte, ROW 5 Byte, DONE 13 Byte; die Rechnung 13 + 407 · 5 = 2048 Byte (vier Pakete zu 512) und daraus 409 Ereignisse; die Zeilenbytes der großen Antwort | Review (Nachrechnen), Verifikation (F-6), Test rechnet selbst mit `assertEquals(409, …)` |
| Selbstkonsistenz | Die Senkenausgabe wird vom Test-Decoder des Codecs zurückgelesen (Metadaten, Zeilenzahl, `DONE_MORE`-Folge, Fehler nach Teilzeilen); COLMETADATA und DONE der großen Antwort stammen aus einem Lauf mit drei Zeilen | `StreamingSmokeTest`; der Decoder belegt Übereinstimmung mit dem Encoder, keine Protokolltreue |
| Festlegung | Puffer von einem Paket Nutzdaten plus Paket im Schreiben plus Stück des Aufrufers (ein Token), keine Byte-Zahl gemessen; `MessageWriter.write` hält die ganze Nachricht; Heap 128 MiB; Zählerschranke 409 als Schranke des Slice; Fehlerflag vor dem Paketbau | `SPEC-022`, KDoc von `MessageStream` und `MessageWriter`, Plan §2 |
| nicht gemessen | Simulations-Backend und Boundary-Kriterium von [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend); Socket und Kanal; blockierendes Backend und Frist; Last, Durchsatz, p95; Client-Verhalten bei einer Antwort ohne EOM-Paket und bei Paketen über seiner Größe; ATTENTION während des Streamens; die Verzögerung durch den zurückgehaltenen Abschluss; der Kanal-Fall des `OutOfMemoryError` an einem echten Kanal; das Hängen von Coroutinen nach dem Fang (Spike-Befund der ADR); die Obergrenze eines einzelnen Tokens | Plan §1 und §6; Verifikation „Herkunftsstufen und §3.8“ |

- **Gegenstand:** entfällt (der Slice lieferte).
- **Steering-Loop-Eintrag:** Eine benannte Spec-Lücke, ohne `liegt in`, weil sie in keiner versionierten Spec steht: Der Speicher des Schreibers hat keine Zahl, weil das Stück des Aufrufers ein ganzes Token ist (`SPEC-022` nennt „ein Token“, „eine Byte-Zahl ist nicht gemessen“); ein einzelnes Token ist nicht durch eine Zahl beschränkt, und die Zähler-Schranke belegt nur Tokens der Größe einer Int-Zeile. Verankert wird sie, wenn Spec oder ADR eine Obergrenze je Token setzen oder `slice-codec-datentypen-erweiterung` (lange Werte) sie trägt; bis dahin ist sie benannt, nicht verkörpert. Kein neuer Sensor, keine neue Regel in diesem Slice. Daneben gezählt und nicht verkörpert: (1) `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test` steht bei 7× (V-1, F-6, F-7), über der Schwelle, nicht verkörpert; Entscheidung beim Nutzer (Regel, Sensor, Träger-Slice oder Streichung). Die Verifikation fand alle drei durch Mutation. (2) `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` steht bei 5×, über der Schwelle, nicht verkörpert; Entscheidung beim Nutzer, der Träger in [slice-qa-performance](../open/slice-qa-performance.md) bleibt unvollständig. (3) `BEO-TDS/test-belegt-nur-den-fake` (3×) und `BEO-TDS/risikozeile-nennt-behobenen-zustand` (3×) stehen über der Schwelle, nicht verkörpert; Entscheidung beim Nutzer. (4) `BEO-TDS/aussagen-ueber-messung-hinaus` steht verkörpert (`AGENTS.md` §3.8, Reviewer-Skill); F-2 und F-3 sind die Wiederkehr nach der Verkörperung und stehen als Beleg. (5) `BEO-TDS/messung-am-echten-sql-server-fehlt` steht bei 17× und ist geplant (`slice-qa-vergleichslauf-d`).
- **Beobachtungs-Register (`../observations/`):** `evidence/slice-backend-streaming-nachweis.md` ergänzt in [BEO-TDS/aussagen-ueber-messung-hinaus](../observations/BEO-TDS/aussagen-ueber-messung-hinaus/observation.md) (F-2, F-3; Zähler 31), [BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test](../observations/BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test/observation.md) (V-1, F-6, F-7; Zähler 7), [BEO-TDS/messung-am-echten-sql-server-fehlt](../observations/BEO-TDS/messung-am-echten-sql-server-fehlt/observation.md) (F-8; Zähler 17), [BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen](../observations/BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen/observation.md) (Zähler 5), [BEO-TDS/test-belegt-nur-den-fake](../observations/BEO-TDS/test-belegt-nur-den-fake/observation.md) (Zähler 3) und [BEO-TDS/risikozeile-nennt-behobenen-zustand](../observations/BEO-TDS/risikozeile-nennt-behobenen-zustand/observation.md) (F-9; Zähler 3). Neue Beobachtungen mit dieser Evidence-Datei: [BEO-TDS/fehlerantwort-hinter-halbem-token](../observations/BEO-TDS/fehlerantwort-hinter-halbem-token/observation.md) (F-1; Zähler 1) und [BEO-TDS/oom-bricht-testlauf-statt-test](../observations/BEO-TDS/oom-bricht-testlauf-statt-test/observation.md) (F-4; Zähler 1). F-5 (Redundanz der Packet-ID-Kürzung) ist als äquivalenter Mutant belegt und kein Eintrag.
- **Folge-Slices:** keine neuen. Die weiter offenen Risiken haben Träger als Datei: [slice-attention-cancel-timeout](../open/slice-attention-cancel-timeout.md), [slice-abbruch-ressourcen-shutdown](../open/slice-abbruch-ressourcen-shutdown.md), [slice-qa-performance](../open/slice-qa-performance.md), [welle-simulations-backend](../welle-simulations-backend.md).
- **Risiken aus §6:** alle 18 tragen genau einen Ausgang (siehe §6): elf entfallen, sieben weiter offen mit Träger oder Register.
- **Drei Paarungen:** Anker: der Eintrag trägt kein `liegt in`, die Paarung entfällt. Folge-Slice und Register: werden nach dem Move in `done/` geprüft (Häkchen dort).

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung — dort die **zwei vorgelagerten
Schritte** (sie stehen in jedem Slice-Plan, unabhängig von Modus und
Slice-Typ) und die **vier Pflichtkriterien** (Konventionen-Dichte ·
Phase-Reife · Evidenz-/Diskrepanz-Risiko · Reconciliation-Aufwand), vier und
nicht mehr.

**Der Abschnitt selbst entfällt nie.** Die zwei vorgelagerten Prüfungen laufen
in **jedem** Slice-Plan — sie hängen weder am Modus noch am Slice-Typ. Bedingt
ist allein der Modus-Begründungsblock am Ende; deshalb nennt der Titel beide
Hälften.

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); `port`, `protocol` und `domain` sind keine eigenen Pfad-Cluster mit eigener Strukturregel (die Schichten-Importregeln in `config/detekt/detekt.yml` sind Architektur-, keine Modus-Grenzen). Keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, acht Beobachtungen, alle Sub-Area `*`). Treffer: `BEO-TDS/aussagen-ueber-messung-hinaus` (7×, die Regel steht in `AGENTS.md` §3.8; sie bindet KDoc, Vertrag und Spec-Nachführung dieses Slice, Gegenmittel in §2) und `BEO-TDS/messung-am-echten-sql-server-fehlt` (3×; der Nachweis behauptet nichts über Antworten eines echten Servers; die Grenze steht in §1 und §6). Ohne Treffer: die übrigen sechs.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
