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

**Bezug:** [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) (Vorleistung), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilit%C3%A4t), [TDS-QA-04](../../../../spec/lastenheft.md#tds-qa-04--performance) (Vorleistung), [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)

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
- Blockierendes Backend hinter dem `Flow`, nicht unterbrechbare Aufrufe und `CANCEL_TIMEOUT` ([ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) §Offen) — nicht gemessen; Träger der Entscheidung ist `slice-adr-backend-grenzen`; das Test-Backend ist suspendierend.
- Behandlung von `OutOfMemoryError` als Produktfunktion — Entscheidung des Nutzers offen ([ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)), Träger `slice-adr-backend-grenzen`; der Test erzeugt keinen `OutOfMemoryError` im Produktpfad.
- ATTENTION-Paket und DONE mit ATTENTION-Status — `welle-fehler-abbruch`; hier endet das Lesen durch Abbruch des Collectors und Senkenfehler, nicht durch ein Protokollereignis.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **Senke und Verdrahtung** ([TDS-FA-08.a](../../../../spec/spezifikation.md#tds-fa-08a--backend-vertrag) Schritt 3): suspendierende Senken-Schnittstelle (Bytes hinein, Rückstau durch Suspendieren), Verdrahtung Sequenzierer → `TokenEncoder` → Paket-Schreiber mit Packet Size, und zwar der Packet Size je Sitzung (`AuthenticatedSession.packetSize`, vom Handshake angekündigt, nicht angewandt; §6): die Verdrahtung stellt den Schreiber darauf ein oder nennt die Lücke weiter; Puffergröße als gekennzeichnete Festlegung; die Schnittstelle ist mit `slice-transport-coroutines-geruest` abgestimmt (§6). Der `MessageWriter` des Transports erzeugt alle Pakete einer Nachricht auf einmal (`PacketWriter.split`) und prüft keine Obergrenze der Nachrichtengröße (Review F8, Verifikation V-6); der Speicher des Schreibers wächst damit mit der Nachricht. Die Verdrahtung ersetzt `BufferedResponse` (`protocol/dispatch`), die Senke des SQL_BATCH-Dispatch, die heute die ganze Antwort ohne Obergrenze und ohne Rückstau puffert (Festlegung des Slice `slice-sql-batch-dispatch`, §6 dort). Die Verdrahtung dieses Slice hält höchstens eine feste Zahl Pakete im Speicher, und die Zähler-Schranke des folgenden Punkts schließt den Speicher des Schreibers ein; wo der Schreiber dafür erweitert oder die Nachricht geschnitten wird, ist eine als Festlegung gekennzeichnete Entscheidung des Slice.
- [ ] **Backpressure und Speichergrenze** ([TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), `SPEC-031` „Backpressure begrenzt Puffer“): bei einer langsamen oder angehaltenen Senke zählt das Test-Backend höchstens eine feste Zahl vorausgelieferter Ereignisse (deterministischer Beleg über Zähler, kein Zeitverhalten); ein Ergebnis oberhalb des gesetzten Heaps des Testlaufs wird vollständig übertragen, Heap-Belege nur als Zusatz zur Zähler-Schranke und mit Angabe von Heap-Einstellung und Zeilenform (Messbedingung).
- [ ] **Abbruch und Senkenfehler, Ende-zu-Ende** ([TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilit%C3%A4t)): ein Cancel des Collectors und ein Fehler der Senke (Schreiben schlägt fehl) beenden das Lesen und rufen `close()` und `cancel()` am Backend genau einmal; kein weiteres Ereignis wird danach gelesen; der Ende-zu-Ende-Lauf (Welle-Datei §3) liest die Senkenausgabe mit dem Test-Decoder des Codecs zurück und belegt Metadaten, Zeilenzahl, `DONE_MORE`-Folge und den Fehler nach Teilzeilen.

Nachweis-Pflichten für alle drei Punkte:

- [ ] Jeder Test trägt im KDoc über der Testfunktion den Block-Tag `@spec SPEC-<NNN>`; die Tests laufen in `make test` und sind grün.
- [ ] Jede Aussage im Quelltext (KDoc) und in Spec oder Vertrag steht an einem Test oder ist als Festlegung gekennzeichnet; die Closure-Notiz trennt *gemessen*, *Handvektor*, *Selbstkonsistenz* und *Festlegung* wie `slice-codec-tds-paket` (`BEO-TDS/aussagen-ueber-messung-hinaus`). Die Messung in [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) gilt für den Spike, nicht für den Quelltext dieses Slice.
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/` (Senken-Schnittstelle; Ort nach Abstimmung mit `slice-transport-coroutines-geruest`) | neu | suspendierende Senke und Verdrahtung Sequenzierer → Encoder → Paket-Schreiber |
| `src/test/kotlin/io/github/pt9912/tdswire/testsupport/` | update | Test-Backend: Generator für große Ergebnisse (Zeilen on demand), Zähler der vorausgelieferten Ereignisse; langsame und angehaltene Testsenke |
| `src/test/kotlin/io/github/pt9912/tdswire/smoke/` | neu | Ende-zu-Ende-Lauf der Welle; `@spec`-Tag je Test; der Smoke-Test darf die Schichten im Test-Quelltext verbinden, die Importregeln für Produktionscode gelten unverändert |
| `build.gradle.kts` | update, falls nötig | Heap-Einstellung des Testlaufs für den Speicher-Beleg; eine Einstellung, die das Gate verschärft, braucht keine ADR, eine Lockerung ja (`AGENTS.md` §3.6) |
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

- **Speicher des Nachrichtenschreibers wächst mit der Nachricht:** `MessageWriter` aus `slice-transport-coroutines-geruest` hält alle Pakete einer Nachricht im Speicher; ein Ergebnisstrom über einem Schreiber dieser Form hielte das Ergebnis im Speicher, und der Nachweis der Speichergrenze wäre leer. — **Ausgang:** offen bis zur Closure; Träger ist der erste Liefer-Punkt.
- **Zwei Definitionen der Senken-Schnittstelle:** `slice-transport-coroutines-geruest` schneidet den rückstaufesten Nachrichtenschreiber; dieser Slice braucht dieselbe Rolle im Test. — **Ausgang:** offen bis zur Closure; Abstimmung mit dem Planer von `welle-transport-tls` vor dem Start (Nutzerentscheidung, falls sie sich nicht einigen).
- **Heap-Beleg ist GC-abhängig:** Der Spike nahm Stichproben nach `System.gc()` alle 500 ms ([ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)), ein Wert „0“ bei kurzen Läufen hieß „nichts Größeres beobachtet“; ein Heap-Test im Gate kann flackern. Der deterministische Beleg ist die Zähler-Schranke. — **Ausgang:** offen bis zur Closure; bei Flackern → Heap-Beleg als Messung außerhalb des Gates mit Verweis in der Closure.
- **Aussagekraft der Zahlen:** Die Spike-Werte (53 755 vorausgelieferte Zeilen, 4 MB Live-Heap) sind weder erklärt noch übertragbar ([ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)); der Slice übernimmt keine davon als Soll. Die Schranke ist eine Festlegung dieses Slice. — **Ausgang:** offen bis zur Closure.
- **Zeilendarstellung und Heap-Grenze eines materialisierenden Backends:** offen in [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md); der Nachweis betrifft ein streamendes Backend. — **Ausgang:** weiter offen: → `slice-adr-backend-grenzen` (S1).
- **Grenze für „kleine“ Ergebnisse (fertige Liste):** nicht entschieden; der Nachweis misst sie nicht. — **Ausgang:** weiter offen: → `slice-adr-backend-grenzen` (S1).
- **`OutOfMemoryError`:** Fangen im Anfragepfad (der Spike tat es, Tragbarkeit nicht gemessen) oder nicht — Nutzerentscheidung offen. — **Ausgang:** weiter offen: → `slice-adr-backend-grenzen` (S2).
- **Blockierendes Backend und Frist (`deadlineEpochMillis`):** nicht geprüft, keine Aussage. — **Ausgang:** weiter offen: → `slice-adr-backend-grenzen` (S3) und [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) Re-Evaluierungs-Trigger.
- **Verzögerung durch den zurückgehaltenen Abschluss:** am Fake nur als Ereigniszahl messbar, nicht als Latenz. — **Ausgang:** weiter offen: → `slice-qa-performance`.
- **Test-Aussagen über Streaming gelten für den Fake:** Ohne Simulations-Backend und ohne Socket sagt der Lauf nichts über [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) und den Durchsatz im Produkt (`BEO-TDS/aussagen-ueber-messung-hinaus`). — **Ausgang:** offen bis zur Closure.
- **`BufferedResponse` hält die ganze Antwort im Speicher:** Die Senke des SQL_BATCH-Dispatch ([slice-sql-batch-dispatch](../done/slice-sql-batch-dispatch.md)) puffert ohne Obergrenze und ohne Rückstau; `TokenEncoder.toByteArray` kopiert zusätzlich. Ein Backend im Betriebsweg würde damit [TDS-FA-08.a](../../../../spec/spezifikation.md#tds-fa-08a--backend-vertrag) („nicht vollständig gehalten“) unterschreiten. Heute setzt kein Betriebsweg ein Backend. Dieser Slice ersetzt die Stelle (erster Liefer-Punkt); belegt wird sie mit dem Zähler- und Heap-Beleg des zweiten Punkts am Dispatch, nicht nur an einer Testsenke. — **Ausgang:** offen bis zur Closure; Träger ist der erste Liefer-Punkt.
- **Disconnect während der Anfrage erst beim Senden bemerkt:** Die Schleife des Dispatch liest während einer Anfrage nicht; das Backend läuft bis zum Ende. Dieser Slice behandelt den Abbruch durch Senkenfehler, nicht den Disconnect der Gegenstelle. Der Träger des Disconnects in `RUNNING` und `STREAMING` ist DoD-Punkt 1 von [slice-abbruch-ressourcen-shutdown](../open/slice-abbruch-ressourcen-shutdown.md); [slice-attention-cancel-timeout](../open/slice-attention-cancel-timeout.md) schließt ihn aus. — **Ausgang:** weiter offen: → [slice-abbruch-ressourcen-shutdown](../open/slice-abbruch-ressourcen-shutdown.md).
- **Packet Size der Sitzung ohne Anwender:** [slice-handshake-login](../done/slice-handshake-login.md) nennt dem Client per ENVCHANGE eine Packet Size und führt sie in `AuthenticatedSession.packetSize`; der Schreiber des Transports arbeitet mit der Größe, mit der er gebaut wird, und nichts stellt ihn auf die Größe der Sitzung ein. Fordert ein Client eine kleinere Größe als die des Schreibers, kann er Pakete über seiner Größe sehen. Dieser Slice verdrahtet den Paket-Schreiber als Erster mit einer Größe und ist der Träger der Anwendung (erster Liefer-Punkt); belegt wird sie mit einem Test, der eine Sitzungsgröße kleiner als die Voreinstellung des Schreibers setzt und die Paketgrößen der Ausgabe prüft. Gemessen ist nicht, wie ein Client auf Pakete über seiner Größe reagiert. — **Ausgang:** offen bis zur Closure; Träger ist der erste Liefer-Punkt.

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

Wird bei der Closure gefüllt.

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
