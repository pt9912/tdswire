# Slice slice-qa-stabilitaet: Stabilität: ungültige und getrennte Verbindungen

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-qualitaet-grundlage

**Bezug:** [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [ADR-0003](../../adr/0003-transportframework.md)

**Berührte Spec-Stellen:** SPEC-006 · SPEC-007 · SPEC-027 · SPEC-034 · SPEC-046 · SPEC-048

**Verantwortlich:** pt9912.

**Autor:** pt9912. **Datum:** 2026-10-29.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Ungültige, unvollständige oder unerwartet getrennte Clientverbindungen bringen den Prozess nicht zum Absturz; das belegen Negativ-, Fragmentierungs- und Fuzz-Tests sowie parallele Verbindungen.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Protokoll-Synchronität nach Fehler und Abbruch — das ist `slice-qa-synchronitaet`, weil dort die Antwortzuordnung geprüft wird, nicht das Überleben des Prozesses.
- Die Durchsetzung von `MAX_CONNECTIONS` (`SPEC-034`) und der Test bis zur Grenze — `slice-abbruch-ressourcen-shutdown` (DoD-Punkt 1); dieser Slice belegt nur, dass der Listener 105 Handler zugleich zulässt.
- Fuzz, Fragmentierung und parallele Verbindungen mit SQL_BATCH in einer angemeldeten Sitzung — kein Träger beschlossen (§6, Register `BEO-TDS/fuzz-nach-verdrahtung-ohne-traeger`).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [x] Negativ- und Fragmentierungstests nach `SPEC-027` laufen grün — für Paket-Codec, Leser und Anmeldepfad (Listener, Handshake-Treiber, Sockets am Loopback); der Anfragepfad nach dem Login ist nicht belegt, siehe §6.
- [x] Fuzz-/Property-Tests: defekte Paketlängen, Offsets und Typwerte verursachen keinen ungefangenen Absturz — für Paketköpfe, Nachrichtengrenzen und Zerstückelung (Referenz `FramingOracle`) und für PRELOGIN/LOGIN7 durch den Treiber (überschriebene, eingefügte und herausgeschnittene Bytes, darunter Offsets und Längen innerhalb der Nachricht); RPC- und SQL_BATCH-Nachrichten belegen nur die Fuzz-Tests ihrer Parser ohne Transport.
- [x] Test mit parallelen Verbindungen — 105 Verbindungen mit fehlerhaften und getrennten Clients sind zugleich im Handler (Eintrittssperre, gemessene Spitze 105). Die Grenze `MAX_CONNECTIONS` (`SPEC-034`) gibt es im Code nicht; ihre Durchsetzung und der Test bis zur Grenze sind **nicht** Teil dieses Slice, sie stehen in DoD-Punkt 1 von `slice-abbruch-ressourcen-shutdown` (§6).
- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| Testquellen des Protokollpfads | neu | Negativ-, Fragmentierungs-, Fuzz-Tests nach [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität) |
| `src/test/kotlin/io/github/pt9912/tdswire/stability/` | neu | Referenz der Nachrichtengrenzen (`FramingOracle`), Erzeuger beschädigter Ströme (`WireGenerator`), Fuzz des Paket-Codecs und des Anmeldepfads, Abbruch an jeder Byteposition, Verbindungssturm gegen den Listener mit dem Handshake-Treiber |
| `tools/mk/` Make-Fragment | entfällt | `make test` (`tools/mk/test.mk`, `SPEC-024`) führt die neuen Tests bereits aus; ein zweites Target wäre eine Kopie |

Ist-Zustand gegen den Plan gemessen (Stand der Umsetzung): Es gab keinen verdrahteten Serverprozess und keinen SQL_BATCH-Pfad; seit den Merges von `slice-bootstrap-konfiguration` und `slice-sql-batch-dispatch` liegen `Main`, `Bootstrap` und die Anfrageschleife `RequestLoop` im Code. Die Stabilitätstests laufen weiter gegen den Listener mit dem Handshake-Treiber und dem Test-Handler `DriverHandler` (er verwirft jede Nachricht nach dem Login) und dem Paket-Codec im Prozess, mit JDK-Sockets am Loopback als Gegenstelle, nicht gegen die verdrahtete Schleife. Eine Begrenzung auf `MAX_CONNECTIONS` gibt es im Code nicht (`grep` über `src/main` findet weder `MAX_CONNECTIONS` noch `maxConnections`; der Treffer in `src/test` ist der KDoc des Sturmtests). Was dadurch nicht belegt ist, steht in §6 mit Träger.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): Transport und Protokollpfad nehmen Verbindungen an ([ADR-0003](../../adr/0003-transportframework.md) Accepted).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert — Carveout?): Fuzz-Läufe sind nicht reproduzierbar (Seed).

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle Punkte der DoD in §2 erledigt, `make gates` grün und die Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Das Transportframework ist nicht festgelegt, die Testart hängt daran. — **Ausgang:** entfallen: [ADR-0003](../../adr/0003-transportframework.md) steht auf `Accepted` (Coroutines), die Tests laufen gegen den Listener, der ihn umsetzt.
- `MAX_CONNECTIONS` (`SPEC-034`) ist im Code nicht vorhanden: der Listener nimmt unbegrenzt an, keine Verbindung wird wegen des Limits abgewiesen. Der Test belegt 105 Verbindungen zugleich im Handler (21 Fälle, 5 Läufe je Fall; eine Eintrittssperre hält jeden Handler, bis alle eingetreten sind, die gemessene Spitze ist 105) und zusätzlich 21 in der Aufwärmrunde, aber keine Grenze und keine Abweisung über der Grenze. Die Spitze ist durch die Sperre erzwungen, nicht natürlich: gemessen ist, dass der Listener 105 Handler zugleich zulässt. Der bisher genannte Träger `slice-abbruch-ressourcen-shutdown` trug die Grenze nicht (seine DoD hatte sie nicht und schob den Test auf diesen Slice zurück, `slice-betrieb-zustandsereignisse` nannte die Durchsetzung ohne Träger): ein Kreis, der den Punkt nie geschlossen hätte. Die Closure löst ihn auf: Durchsetzung, Konfigurationsschlüssel und Test bis zur Grenze stehen seit dieser Closure in DoD-Punkt 1 von `slice-abbruch-ressourcen-shutdown` (dort liegen Verbindungsregister, Aufräumpfad und Lecktest), die Verweise in `slice-betrieb-zustandsereignisse` und `welle-fehler-abbruch` sind berichtigt. — **Ausgang:** weiter offen: Träger `slice-abbruch-ressourcen-shutdown` (DoD-Punkt 1); Register `BEO-TDS/dod-haengt-an-offenem-slice-ohne-rueckfuehrung`.
- Kein Anfragepfad in der Sitzung der Tests: der Test-Handler `DriverHandler` verwirft jede Nachricht nach dem Login. Seit den Merges von `slice-sql-batch-dispatch` und `slice-backend-sequenzierer` liegt die Anfrageschleife im Code, die Stabilitätstests laufen nicht gegen sie. Ungemessen sind Fuzz mit defekten SQL_BATCH-, RPC- und Attention-Nachrichten, Fragmentierung und parallele Verbindungen mit SQL_BATCH in einer angemeldeten Sitzung; die Parser (`SqlBatchParser`, `RpcParser`) haben eigene Fuzz-Tests ohne Transport, die Tests von `slice-sql-batch-dispatch` sind feste Fälle ohne Zufall. Der Plan von `slice-sql-batch-dispatch` führt [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität) im Bezug, plant aber keinen Fuzz; `slice-qa-synchronitaet` prüft die Zuordnung von Antworten nach Fehler und Abbruch, nicht das Überleben unter defekten Eingaben, und trägt es nach seinem Zuschnitt nicht (seine DoD: Fehler-/Cancel-Matrix am Referenzclient, Test der Antwortzuordnung). Abbruch während eines Ergebnisstroms und hartes Trennen in `RUNNING` und `STREAMING` trägt `slice-abbruch-ressourcen-shutdown` (DoD-Punkt 1, Disconnect in jedem Zustand). Für Fuzz und Fragmentierung im Anfragepfad besteht kein Träger; ein neuer Slice wird hier nicht angelegt, die Frage geht an den Nutzer. — **Ausgang:** weiter offen: Register `BEO-TDS/fuzz-nach-verdrahtung-ohne-traeger`.
- Die Gegenstelle ist ein JDK-Socket, kein Referenzclient: wie `Microsoft.Data.SqlClient` trennt, zerstückelt und zurücksetzt, ist nicht gemessen. — **Ausgang:** weiter offen: Träger `slice-qa-kompatibilitaet` (DoD-Punkt 1, Szenario S15 „Verbindungsabbruch“ der Szenarienliste, Zuordnung zu [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität) dort offen).
- TLS und Anmeldung sind nicht zusammen geprüft: der Verbindungssturm nutzt den unverschlüsselten Testmodus (`TEST_UNENCRYPTED`), der Anmelde-Fuzz ersetzt den TLS-Handschlag durch eine Zusage; defekte TLS-Records stehen in den Tests der TLS-Schleife, beschädigte LOGIN7-Nachrichten hinter echtem TLS nicht. `slice-qa-sicherheit` (DoD-Punkt 3: TLS-Handshake- und Konfigurationsnegativtests) trägt beschädigtes LOGIN7 hinter TLS nicht. — **Ausgang:** weiter offen: kein Träger; Register `BEO-TDS/fuzz-nach-verdrahtung-ohne-traeger`.
- Die Ressourcenfreigabe ist an Dateideskriptoren (`UnixOperatingSystemMXBean`, Linux im Container) und an die Threads des Listeners gemessen, nicht am Heap und nicht an den gemeinsamen Pools von `Dispatchers.Default` und `Dispatchers.IO`. Der Deskriptorzähler gilt für den ganzen Prozess (Clients und Server in einer JVM), vergleicht die Hauptrunde mit der Grundlinie nach der Aufwärmrunde (Lecks der Aufwärmrunde sind unsichtbar) und trägt ohne Schlupf (`DESCRIPTOR_SLACK` 0) schon bei einem zusätzlich offenen Deskriptor (Mutation LK rot); ein Schlupf von 0 am Prozesszähler kann bei fremden Öffnungen flackern, fünf saubere Läufe blieben grün, mehr ist nicht gemessen. — **Ausgang:** weiter offen: die Grenze der Messung bleibt (Linux, ganzer Prozess, Flake-Vorbehalt über fünf Läufe); Register `BEO-TDS/schranken-test-mit-ungeprueftem-zaehler`.
- Die Typmenge der Referenz `FramingOracle` ist eine Festlegung, die mit `PacketType` gleichläuft (ein Test hält die Gleichheit): der Fuzz-Test belegt die Übereinstimmung von Codec und Referenz, nicht die mit einem Standardtext; die Regeln der Referenz stammen aus derselben Quelle wie der Codec, die Lagen der Offset- und Längenfelder aus dem Parser des Repos. Der Produktionscode (`PacketType`, `PacketHeader`, `PacketStatus`, `TdsMessage`) und `PacketHeaderTest` tragen weiter Abschnittsnummern „MS-TDS 2.2.3.1.x“ ohne Quelle im Repo; sie liegen außerhalb dieses Slice. — **Ausgang:** weiter offen: Register `BEO-TDS/fuzz-orakel-spiegelt-die-implementierung` und `BEO-TDS/aussagen-ueber-messung-hinaus`.
- Kopplung des Sturmtests: 10 der 105 Fälle (`GOOD_THEN_RESET`, `RESET_IN_LOGIN7`, je 5-mal) sind blockierende Clients auf den 12 Threads des Pools und warten auf Antworten, die der Handler erst nach der Eintrittssperre schickt. Bei `COPIES` von 6 oder mehr (12 blockierte Threads) blockiert der Lauf nach dem Lesen des Codes bis zur Zeitüberschreitung von 60 s, statt einen Fehler der Software zu zeigen (Lesebefund, nicht gemessen); bei den heutigen Werten (5 Kopien, 12 Threads) kein Fehler. Die Kopplung steht in keinem Kommentar der Klasse. — **Ausgang:** weiter offen: Register `BEO-TDS/test-kopplung-an-poolgroesse-unbenannt`; der Kommentar ist Arbeit an den Tests und wird hier nicht geändert.

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

- **Was hat funktioniert:** Der Schnitt trug, die Rückführung nach §4 war nicht nötig, und der Slice änderte keine Produktionsdatei. Die Tests fanden mit Mutationen, was sie versprechen: der Review fuhr 23 Mutationen (20 rot, 3 überlebt: D1, L2a, G3), der Verifier elf eigene am Stand der Fix-Runde (sein Text nennt zwölf, seine Tabelle führt elf; neun rot, L120 und D1 überleben in den Stabilitätstests, D1 ist im Volllauf durch `HandshakeDriverTest` gedeckt). Die kritischen Mutationen M2, M3, M6, G3, A6, R1, R2, Q1 und LK sind rot. Die Stabilitätstests allein liefen viermal grün (11 von 11), die Stage `test` frisch ohne Cache einmal (500 Tests bestanden); der Review fuhr den Verbindungssturm 12-mal bei 60 Busy-Loops grün. Die Regel „Kommentar nur in den fünf Klassen“ (HIGH-Liste des Reviewer-Skills) fing F-1, den einzigen HIGH-Befund.
- **Was ging anders als geplant:** (a) Der Kommentar „würde … wertlos machen“ beschrieb eine verworfene Alternative im Konjunktiv (Review F-1, `AGENTS.md` §3.7); behoben. (b) „105 gleichzeitige Verbindungen“ war nicht gemessen, die Spitze lag bei 48 (Review F-2); die Fix-Runde hält jeden Handler an einer Eintrittssperre (`Rendezvous`), und der Test misst die Spitze: ohne Sperre (R1) 41, bei zu früher Öffnung (R2) 103. Die Spitze ist damit erzwungen, nicht natürlich. (c) Die Typmenge der Referenz stand als „nach MS-TDS 2.2.3.1.1“ und „unabhängig“; sie ist eine Festlegung und läuft mit `PacketType` gleich (Review F-3); der Produktionscode trägt weiter Abschnittsnummern ohne Quelle im Repo, sie liegen außerhalb dieses Slice. (d) Die Zerstückelung hatte keinen Zähler, G3 blieb grün (Review F-4); jetzt zählen `ZERSTUECKELT` und `BYTENWEISE`, G3 und A6 sind rot. (e) Der Deskriptorzähler hatte einen Schlupf von 3 und trug erst ab vier Lecks (Review F-5); jetzt 0, LK ist rot. (f) `closed` und `bytesWritten` hatten keinen Leser, der Schlüssel `Closed:READ_FAILED` vermengte Rahmen- und Kanalfehler, Offsets und Längen wurden nur zufällig überschrieben, die Toleranz `extra in 0..hung` verbarg überzählige Lesefehler (Review F-6 bis F-9); behoben, mit benannten Rändern (Verifikation F-6, F-8). (g) `StormSupport.kt` (321 Zeilen) ist in drei Dateien geteilt (F-10). (h) Die Verifikation fand zwei MEDIUM-Befunde der Planung, die diese Closure bearbeitet: V-1 (Träger der Grenze `MAX_CONNECTIONS` trug nicht, ein Kreis zwischen drei Plänen; jetzt DoD-Punkt 1 von `slice-abbruch-ressourcen-shutdown`, Verweise berichtigt) und V-2 (Rückbezug auf `slice-sql-batch-dispatch` veraltet, der Träger des Fuzz im Anfragepfad trägt nicht; §3 und §6 sind an den Ist-Stand gebunden, der Fuzz bleibt ohne Träger im Register). V-3 (`src/` statt `src/main`) und V-4 (Kopplung des Sturmtests) stehen in §3 bzw. §6. (i) **L120 und LK (Verifikation V-5):** der Implementer nannte die Mutation L120 (der Kanal der Verbindung 120 wird nicht geschlossen) als Prüfstein; im Lauf des Verifiers überlebt sie, weil sie davon abhängt, welcher Fall die Nummer 120 trägt (die Annahmereihenfolge der 12 Threads ist nicht gesteuert). Als Prüfstein gilt LK (der `SocketChannelAdapter` schließt die 100. Instanz nie, reihenfolgeunabhängig, rot nach 20 s in „Dateideskriptoren frei“). Wer „L120“ als belegt führt, muss die Form nennen; hier ist es nicht belegt. (j) `docs/plan/spec-abdeckung.md` ist in dieser Closure neu erzeugt; `SPEC-034` steht dort „OHNE NACHWEIS“, die Tests tragen den Tag für `SPEC-034` nicht, weil die Grenze nicht existiert.
- **Reports:** [Review](../../../reviews/2026-10-30-cb4c853-slice-qa-stabilitaet.md) (1 HIGH F-1; F-2 bis F-5 MEDIUM, F-6 und F-7 LOW, F-8 bis F-12 INFO) gegen den Merge `cb4c853`, und [Verifikation](../../../reviews/2026-10-30-dcb6b27-verifikation-slice-qa-stabilitaet.md) am Stand `dcb6b27`, die die Fix-Runde `a04ab01` nachverifiziert (F-1 bis F-10 behoben oder ehrlich eingeschränkt; V-1 und V-2 MEDIUM, V-3 LOW, V-4 und V-5 INFO). Die Fix-Runde hat keinen eigenen Nach-Review; die Verifikation ersetzt ihn nicht. Der Verifier fuhr `make gates` mit gecachter Stage `test` (nur zusammen mit dem frischen Volllauf ein Beleg); `make gates` lief in dieser Closure vor jedem Inhalts-Commit und nach dem Move. Ein Validator-Lauf entfällt: der Slice liefert nur Tests, keinen Nutzerwert.
- **Laufzeit von `make test`:** der Filterlauf der Stabilitätstests brauchte 36 bis 40 s (Verifikation, Last des Hosts 5 bis 12), der Volllauf der Stage `test` 52 s; der Verbindungssturm allein 25 bis 30 s unter 60 Busy-Loops (Review). Keine Leistungszusage.

**Zuordnung gemessen | Handvektor | Selbstkonsistenz | Festlegung | nicht gemessen je Aussage.** *Gemessen* heißt beobachtet in einem Lauf dieses Repos, am Listener, am Handshake-Treiber, am Paket-Codec und an JDK-Sockets am Loopback; **gegen den Referenzclient und gegen einen echten SQL Server wurde in diesem Slice nichts gemessen.** *Handvektor* heißt hier ein von Hand gezählter Vektor ohne externe Quelle. *Selbstkonsistenz* heißt Code gegen Code, auch wo die Referenz eine zweite Implementierung ist. *Festlegung* heißt Setzung ohne Messung.

| Stärke | Aussage | Beleg im Repo |
|---|---|---|
| gemessen | Ein Abbruch des Stroms an jeder Byteposition (drei Puffergrößen, beide Enden) führt nie zu einer ungefangenen Ausnahme und liefert die erwartete Meldung | `StreamCutoffTest`; Mutationen M2, M6, Q1, A1 bis A5 rot (A1 bis A5 vom Review, nicht vom Verifier gefahren) |
| gemessen (Zufallsfolgen mit festem Startwert, Zähler je Fehlerart und Zerstückelung) | Paketköpfe, Nachrichtengrenzen und Zerstückelung: Codec und Referenz `FramingOracle` stimmen in 3 Grenzenpaaren mal 3000 Läufen überein; Zähler je Fehlerart und für `ZERSTUECKELT` und `BYTENWEISE` verlangen Mindestzahlen | `FramingFuzzTest`; M2, M3, M6, G3, A6 rot (Verifier) |
| gemessen (4000 Läufe) | PRELOGIN und LOGIN7 durch den Treiber mit zwölf Veränderungen (überschriebene, eingefügte, herausgeschnittene Bytes, gezielte Randwerte für Offset- und Längenfelder) enden nie mit einer ungefangenen Ausnahme; die Ausgänge entsprechen der Erwartung der Referenz, getrennt nach Rahmen- und Kanalfehler | `HandshakeFuzzTest`; Q1 rot. Der Zähler `Offset:Closed:PROTOCOL_VIOLATION` unterscheidet PRELOGIN nicht von LOGIN7 |
| gemessen | 105 Handler sind zugleich im Listener (21 Fälle, 5 Läufe je Fall, Eintrittssperre, Spitze 105); der Prozess stürzt nicht ab, kein ungefangener Fehler in den Threads | `ConnectionStormTest`; R1 (Spitze 41) und R2 (103) rot; U1 (Review) rot. Die Spitze ist erzwungen, nicht natürlich |
| gemessen (Linux, ganzer Prozess) | Die Zahl offener Dateideskriptoren wächst in der Hauptrunde gegenüber der Grundlinie nach der Aufwärmrunde nicht (Schlupf 0); die Threads des Listeners enden | `ConnectionStormTest`; LK rot, L1 (`registry.release`, Review) rot. Fünf saubere Läufe grün; L120 überlebt und gilt nicht |
| Handvektor (von Hand gezählt) | Die Paketlänge auf der Grenze (100 und 101), die Nachrichtengrenze (255, 256, 257) und der geteilte Paketkopf an jeder Teilungsstelle 1 bis 7 mit fünf Fehlerarten | `FramingFuzzTest`, `StreamCutoffTest`; M3 jetzt deterministisch rot |
| Selbstkonsistenz | Die Referenz `FramingOracle` teilt die Regeln und die Typmenge mit dem Codec; ein Test hält `KNOWN_TYPES` und `PacketType` gleich; die Lagen der Offset- und Längenfelder stammen aus dem Parser des Repos | KDoc von `FramingOracle` und `FramingFuzzTest`; Review T1 bis T4 rot. Kein Standardbeleg |
| Festlegung | Die Typmenge der Referenz (1, 2, 3, 4, 6, 7, 8, 14, 16, 17, 18); die Mindestzahlen der Zähler; Sturmparameter (12 Client-Threads, 5 Kopien, 60 s Frist); späte Lesefehler an der Verbindungsnummer von `LOGIN_TIMEOUT` werden nicht gezählt | `FramingOracle`, `HandshakeFuzzTest`, `ConnectionStormTest` |
| nicht gemessen | Die Grenze `MAX_CONNECTIONS` und eine Abweisung über der Grenze; Fuzz, Fragmentierung und parallele Verbindungen mit SQL_BATCH in einer angemeldeten Sitzung; beschädigtes LOGIN7 hinter echtem TLS; die Reaktion von `Microsoft.Data.SqlClient` auf Trennen, Zerstückeln und Zurücksetzen; Heap und gemeinsame Pools; ein Schlupf von 0 über mehr als fünf Läufe; eine natürliche Spitze von 105 gleichzeitigen Verbindungen; Mutationen A1 bis A5, M3b, T1 bis T4, L1, L2a/L2b, U1 vom Verifier | Register und Träger in der Tabelle unten |

**Offene Punkte der Closure mit Träger** (gegen den Inhalt der Dateien geprüft):

| Offener Punkt | Träger | Stand |
|---|---|---|
| Durchsetzung von `MAX_CONNECTIONS` und Test bis zur Grenze | `slice-abbruch-ressourcen-shutdown` (`open/`) | DoD-Punkt 1 und ein Risiko in §6 (Spec-Lücke zur Abweisung) seit dieser Closure; Verweise in `slice-betrieb-zustandsereignisse` und `welle-fehler-abbruch` berichtigt |
| Abbruch während eines Ergebnisstroms, hartes Trennen in `RUNNING` und `STREAMING` | `slice-abbruch-ressourcen-shutdown` (`open/`) | DoD-Punkt 1 (Disconnect in jedem Zustand, Zähler unbehandelter Ausnahmen) |
| Verbindungsabbruch am Referenzclient | `slice-qa-kompatibilitaet` (`open/`) | DoD-Punkt 1 (Szenario S15 der Szenarienliste), Zuordnung zu [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität) dort offen |
| Fuzz, Fragmentierung und parallele Verbindungen mit SQL_BATCH in angemeldeter Sitzung; beschädigtes LOGIN7 hinter TLS | kein Träger; Register `BEO-TDS/fuzz-nach-verdrahtung-ohne-traeger` | Frage an den Nutzer: DoD-Zeile in `slice-sql-batch-dispatch` oder `slice-qa-sicherheit`, oder ein eigener Slice. `slice-qa-synchronitaet` trägt es nach seinem Zuschnitt nicht |
| Kopplung des Sturmtests an die Poolgröße | kein Träger; Register `BEO-TDS/test-kopplung-an-poolgroesse-unbenannt` | ein Kommentar in der Klasse (Arbeit an den Tests) |
| Abschnittsnummern „MS-TDS 2.2.3.1.x“ ohne Quelle im Produktionscode | kein Träger; Register `BEO-TDS/aussagen-ueber-messung-hinaus` | liegt außerhalb dieses Slice |

- **Gegenstand:** entfällt (der Slice lieferte; der Punkt `MAX_CONNECTIONS` ging an `slice-abbruch-ressourcen-shutdown`).
- **Steering-Loop-Eintrag:** Kein `liegt in`: mit diesem Slice ist keine Regel, kein Sensor und keine Spec-Zeile verkörpert worden; gezählt wird, nicht verkörpert. Fünf Feststellungen. (1) **Die Träger-Zeile eines Risikos wird gegen den Rückverweis des Trägers abgeglichen, bevor sie steht.** Der Plan gab `MAX_CONNECTIONS` an `slice-abbruch-ressourcen-shutdown`, dessen Plan es ausschloss und zurückverwies; `slice-betrieb-zustandsereignisse` sagte „keinen eindeutigen Träger“; der Kreis fiel erst der Verifikation auf (V-1). Dasselbe Muster für `slice-sql-batch-dispatch` als Träger des Fuzz (V-2): der Träger führt [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität) im Bezug, plant aber keinen Fuzz. Register: `BEO-TDS/dod-haengt-an-offenem-slice-ohne-rueckfuehrung` (jetzt 2×) und `BEO-TDS/offene-punkte-ohne-traeger` (weiteres Auftreten, Stand bleibt verkörpert). Die Handarbeit dieser Closure (Punkt in den passenden Slice schreiben, andere Verweise berichtigen) ist kein Sensor. (2) **Zähler und Variation in Zufalls- und Sturmtests:** die Zerstückelung hatte keinen Zähler (G3 grün), der Deskriptorzähler hatte einen Schlupf ohne Messung (L2a grün), zwei Zähler am Kanal hatten keinen Leser, ein Zählschlüssel vermengte zwei Ursachen. Register: `BEO-TDS/strukturtest-ohne-variation-der-eingabe` (jetzt 5×) und `BEO-TDS/schranken-test-mit-ungeprueftem-zaehler` (jetzt 2×); die Mutation des Reviews war der Sensor, kein Gate prüft es. (3) **Eine Mutation, die von einem nicht gesteuerten Ablauf abhängt, ist kein Prüfstein** (L120 überlebt, LK ist rot): neue Beobachtung `BEO-TDS/mutation-haengt-an-ungesteuerter-reihenfolge`, 1×. (4) **Aussagen über den Beleg hinaus trotz verkörperter Regel:** „105 gleichzeitig“ bei einer Spitze von 48, Typmenge „nach MS-TDS 2.2.3.1.1“, „unabhängig“ bei gleicher Quelle; ein weiteres Auftreten von `BEO-TDS/aussagen-ueber-messung-hinaus` und von `BEO-TDS/fuzz-orakel-spiegelt-die-implementierung` (jetzt 2×), keine Schärfung. (5) **Benannte Spec-Lücke:** `SPEC-034` nennt das Ressourcenlimit (1000, vorläufig, konfigurierbar) und als Fehlermodus der Anmeldung, hat aber keine Durchsetzung und beschreibt nicht, was der Client bei einer Abweisung sieht; der Träger ist jetzt `slice-abbruch-ressourcen-shutdown`. Keine unverkörperte Beobachtung erreicht mit diesem Slice 3×; `BEO-TDS/strukturtest-ohne-variation-der-eingabe` (5×) steht über der Schwelle, nicht verkörpert (die Frage an den Nutzer, ob daraus eine Reviewer-Regel oder ein Sensor wird, bleibt offen).
- **Beobachtungs-Register (`../observations/`):** `evidence/slice-qa-stabilitaet.md` ergänzt in `BEO-TDS/aussagen-ueber-messung-hinaus` (Review F-2, F-3, F-5; Verifikation V-3), `BEO-TDS/strukturtest-ohne-variation-der-eingabe` (Review F-4), `BEO-TDS/schranken-test-mit-ungeprueftem-zaehler` (Review F-5, F-6, F-7), `BEO-TDS/fuzz-orakel-spiegelt-die-implementierung` (Review F-3, F-8), `BEO-TDS/dod-haengt-an-offenem-slice-ohne-rueckfuehrung` (Verifikation V-1) und `BEO-TDS/offene-punkte-ohne-traeger` (Verifikation V-2). Drei neue Beobachtungen, je mit dieser Evidence-Datei: `BEO-TDS/fuzz-nach-verdrahtung-ohne-traeger`, `BEO-TDS/test-kopplung-an-poolgroesse-unbenannt` und `BEO-TDS/mutation-haengt-an-ungesteuerter-reihenfolge`. Kein Auftreten: `BEO-TDS/flake-fix-ohne-reproduzierten-fehler` (kein roter Lauf in den Läufen von Review und Verifikation), `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` (`make docs-check` ohne Befund) und die übrigen. Die Zähler folgen aus den Dateien.
- **Risiken aus §6:** alle acht tragen genau einen Ausgang (siehe §6): eines entfallen, sieben bleiben weiter offen (zwei mit Träger-Slice, fünf im Register).
- **Drei Paarungen:** Anker: kein Eintrag trägt `liegt in`, die Paarung entfällt. Folge-Slice: `slice-abbruch-ressourcen-shutdown` und `slice-qa-kompatibilitaet` existieren in `open/` und führen die Punkte in ihrem Inhalt (gegen die Dateien geprüft; DoD-Zeile und Risiko in `slice-abbruch-ressourcen-shutdown` kamen mit dieser Closure). Register: eine Evidence-Datei dieses Slice in sechs bestehenden Klassen und drei neue Beobachtungen.
- **Für die Welle-Closure von `welle-qualitaet-grundlage`:** die Welle ist mit diesem Slice nicht vollständig; `slice-qa-wartbarkeit` liegt in `in-progress/` und weitere Slices der Welle in `open/`.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` steht noch als Vorlage; der Slice berührt das ganze Repo (`*`), eine feinere Sub-Area ist nicht deklariert.

**Vorgelagert — offene Beobachtungen sichten:** Beim Schreiben des Plans trug `../observations/` nur die `README.md` (keine Treffer). Zur Closure trägt es Beobachtungen unter `BEO-TDS`; Treffer sind `BEO-TDS/schranken-test-mit-ungeprueftem-zaehler`, `BEO-TDS/strukturtest-ohne-variation-der-eingabe` und `BEO-TDS/flake-fix-ohne-reproduzierten-fehler` (Eingang des Reviews); die Treffer der Closure stehen in §7.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
