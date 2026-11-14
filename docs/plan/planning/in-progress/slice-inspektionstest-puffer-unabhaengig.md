# Slice slice-inspektionstest-puffer-unabhaengig: Der Prozesslauf-Test hängt nicht mehr an den TCP-Puffern des Hosts

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** ohne Welle — keine Closure-Bedingung, die von der DoD dieses Slice verschieden ist. Die Welle des Ursprungs ([welle-betrieb-diagnose](../done/welle-betrieb-diagnose.md)) ist geschlossen; der Fix ändert keine ihrer Zusagen, er stellt die Umgebungsunabhängigkeit eines ihrer Belege her.

**Bezug:** [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) (Scope: derselbe Test in jeder Umgebung gleich), [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit) und [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose) (die Zusagen, die der Test belegt und weiter belegen muss), [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung) (Backpressure als Mittel des Tests), [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md), [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md) (Prozesstests liegen in der Stage `test`)

**Berührte Spec-Stellen:** — (der Slice ändert `src/test` und das Register; die Rückdruck-Aussage in `spezifikation.md` Schritt 5 der Auswahl, Zeilen 209 bis 214, und die Festlegung zum gebauten Stand der Backpressure in `spezifikation.md` Zeile 1957 bleiben Wort für Wort stehen)

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** Planner-Agent, Auftrag des Nutzers 2026-11-14. **Datum:** 2026-11-14.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** `TestserverLaufInspektionTest` belegt dieselben Zusagen (Cancel über Rückdruck, `backend_active` 1 und `spans_open` 3 als Signal der offenen Anfrage, vollständige Erfassung, nichts Verbotenes) mit **einer festen Menge**, die auf dem GitHub-Runner und auf dem lokalen Host gleich ist, ohne `/proc/sys/net/ipv4/` zu lesen; die rote CI `gates` auf `main` wird wieder grün.

**Befund (belegt):** Die Läufe `43467852967`, `43567859346` und `43576427488` des Workflows `gates` auf `main` sind `failure` (`gh run list --workflow gates --branch main`); der vorangehende Lauf `43436286595` ist `success`. Im Log von `43576427488` (Datei `gates/3_make gates.txt`, Zeile 3153 f., gelesen über `gh api repos/pt9912/tdswire/actions/runs/43576427488/logs`) steht: `TestserverLaufInspektionTest > Ein Lauf ueber den Prozess ist vollstaendig erfasst und nennt nichts Verbotenes() FAILED`, `AssertionFailedError: Datenmenge 150994944 über der Grenze 134217728: Puffergrößen der Umgebung`. Die Menge ist `BUFFER_FACTOR` (4) mal die Summe der Obergrenzen von `tcp_wmem` und `tcp_rmem`; 150 994 944 Byte ergeben eine Summe von 37 748 736 Byte (36 MiB) auf dem Runner, am lokalen Host sind es 4 194 304 + 6 291 456 = 10 485 760 Byte und eine Menge von 41 943 040 Byte (40 MiB). Es ist kein Fehler des Produkts: die Zusicherung bricht vor dem Start des Servers ab. Die Beobachtung [BEO-TDS/annahme-ueber-puffer-der-umgebung](../observations/BEO-TDS/annahme-ueber-puffer-der-umgebung/observation.md) führte als Trigger „erster Host mit anderen Obergrenzen oder ein rotes Gate auf einer anderen Maschine“; er ist eingetreten.

**Entscheid des Nutzers (2026-11-14, Option 1 der Empfehlung):** Der Test hängt nicht mehr von den Puffern des Hosts ab. Konstruktion statt Umgebungsannahme: die Menge wird gedeckelt, und die Puffer, die den Rückdruck bestimmen, werden vom Test selbst klein gehalten, soweit das ohne Änderung am Produktcode geht.

**Die Sendeseite des Servers — gelesen, nicht angenommen.** `TdsListener` öffnet den Server mit `AsynchronousServerSocketChannel.open(group).bind(InetSocketAddress(host, port))` (`src/main/kotlin/io/github/pt9912/tdswire/transport/listener/TdsListener.kt` Zeile 233); `SO_SNDBUF` und `SO_RCVBUF` werden in `src/main` und `testserver` nirgends gesetzt (Suche nach `SO_SNDBUF`, `SO_RCVBUF`, `sendBuffer`, `receiveBuffer`, `setOption` ohne Treffer, 2026-11-14). Der Test kann den Sendepuffer der angenommenen Verbindung also nicht setzen; ein Schalter dafür im Produktcode wäre eine Test-Naht ([BEO-TDS/test-naht-im-produktivcode](../observations/BEO-TDS/test-naht-im-produktivcode/observation.md), verkörpert im Reviewer-Skill) und ist **nicht** Teil dieses Slice. Was der Test setzen kann, ist die Empfangsseite des Clients (`RECEIVE_BUFFER` 4096 über `LaufClient` und `RawClient`, schon vorhanden) und die Menge. Die Zusage trägt dann an **zwei** Bedingungen, die in §3 getrennt belegt werden: (a) die Summe der Obergrenzen beider Puffer liegt auf den bekannten Hosts unter dem Deckel (Rechnung), (b) der Sendepuffer des Servers wächst bei einem Empfangsfenster von 4096 Byte nicht auf seine Obergrenze (**Hypothese H1**, in der Vorprüfung zu messen).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Eine Naht im Produktcode für `SO_SNDBUF` (Konfigurationsschlüssel, Parameter, Hook) — widerspräche der Regel zur Test-Naht; sie wäre eine Änderung der öffentlichen Fläche ([TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), `make flaeche`) und ein eigener Entscheid. Sie ist nur die Alternative aus §6, falls H1 fällt, und dann Sache des Nutzers.
- Das Überspringen des Tests auf Hosts mit großen Puffern (Option 3) — verworfen: die CI würde grün melden, ohne dass der Cancel über Rückdruck und die Inspektion aller Zeilen am Prozess belegt sind; das ist die Aussage, die der Slice [slice-betrieb-prozesslauf-logzeilen-inspektion](../done/slice-betrieb-prozesslauf-logzeilen-inspektion.md) liefern sollte, und sie bliebe unbelegt, ohne dass es jemand liest.
- Die Grenze `MAX_VOLUME` anzuheben (Option 2) — löst das Rot, aber die Menge wüchse mit dem Host (Runner: 144 MiB gegen den Heap von 512 MiB, der Host des Nutzers swappt); die Umgebungsabhängigkeit bliebe, nur weiter oben. Option 2 ist die Rückfall-Alternative, falls H1 fällt (§6).
- Änderungen an den Zusagen des Tests (Wartestellen, Inspektion, Felder, Spans, Metriken) — der Slice ändert die Menge und die Meldungen, nicht das, was belegt wird; jede Änderung dort ist ein anderer Vorgang.
- Eine Aussage über den Rückdruck an einem anderen Backend oder an einem Netz-Draht — `spezifikation.md` Zeile 1957 begrenzt die Aussage zum gebauten Stand auf das Test-Backend ohne Netz; der Prozesslauf belegt den Rückdruck am Standard-`main` des Testservers über einen Loopback-Socket, mehr nicht.
- Lasttests, künstliche Last, Messung der Laufzeit unter Last — Lastunabhängigkeit folgt aus der Bauart (Signale statt Zeit), nicht aus einem Lauf unter Last.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [ ] **Liefer-Punkt 1 — Test ohne Puffer des Hosts.** `TestserverLaufInspektionTest` liest `/proc/sys/net/ipv4/` nicht mehr (`upperLimit`, `BUFFER_FACTOR` und `MAX_VOLUME` entfallen); die Menge ist ein fester Deckel, eine Setzung mit Begründung im KDoc (Kandidat 40 MiB, der Wert der lokal gemessenen Läufe; die Vorprüfung in §3 bestätigt oder ändert ihn). Der Lauf belegt unverändert: die Anfrage bleibt offen (`metrics.backend_active` 1, `metrics.spans_open` 3), ATTENTION wird bei blockiertem Schreiber gelesen, die Antwort endet mit `DONE` mit Bit `ATTENTION` und hat weniger Byte als die Menge, die Verbindung bleibt danach benutzbar, jede Zeile der Standardausgabe ist erfasst und nichts Verbotenes steht darin. Bleibt die Anfrage nicht offen, weil die Puffer die Menge fassen, nennt die Meldung der Zusicherung diesen Grund (Puffer der Umgebung größer als die Menge) und nicht nur „keine offene Anfrage“. **Mutationsnachweis** je Zusage über die engste Testklasse: (M1) Deckel auf einen Wert unter den Puffern (Kandidat 1 MiB) → der Test wird rot an der Zusicherung „keine offene Anfrage“ oder „die Antwort ist abgebrochen“, mit der Meldung zu den Puffern; (M2) `RECEIVE_BUFFER` aus dem Aufruf von `LaufClient` entfernt (Voreinstellung des Hosts) → Ergebnis gemessen und in der Closure-Notiz genannt: rot an derselben Stelle, oder grün, dann trägt der Parameter keinen Beitrag auf diesem Host und die Aussage dazu in KDoc und §7 wird eingeschränkt (Aussage nicht breiter als der Beleg, `AGENTS.md` §3.8); (M3) der Zugriff auf `/proc/sys/net/ipv4/` ist wieder im Quelltext → die Suche des Verifiers (`grep`) findet ihn; kein Gate prüft das (Bestand der Gate-Liste unverändert, keine Senkung und kein neuer Sensor in diesem Slice).
- [ ] `make gates` grün (einschließlich `make test`); die engste Testklasse `TestserverLaufInspektionTest` grün; `make spec-abdeckung` einmal (Tests geändert, die `@spec`-Tags bleiben; `docs/plan/spec-abdeckung.md` nur fortgeschrieben, wenn sich dort etwas ändert).
- [ ] **Beleg unter großer Umgebung oder ehrlich „nicht gemessen“.** Ein Lauf des Tests in einem Container mit `--sysctl net.ipv4.tcp_wmem="4096 16384 150994944" --sysctl net.ipv4.tcp_rmem="4096 131072 150994944"` (Obergrenzen so groß wie auf dem Runner oder größer) ist gemessen und mit Ergebnis in §7 genannt; geht das nicht (Weg der Stage `test` ohne Sysctl-Option), steht in §7 „nicht gemessen“ mit dem Grund, und der Beleg ist der CI-Lauf. Der Test darf auch in diesem Lauf die Datei nicht lesen (Konstruktion), der Lauf zeigt, dass die Menge trotz großer Obergrenzen offen bleibt.
- [ ] **Beleg am CI-Lauf auf `main` (Closure-Bedingung, nicht ersetzbar).** Der Workflow `gates` auf `main` hat nach dem Merge der Umsetzung den Stand `success` (`gh run list --workflow gates --branch main`, Lauf mit `head_sha` gleich dem Merge-Commit; Kennung des Laufs in §7). Ohne diesen Lauf geht der Slice nicht nach `done/`: der Runner ist lokal nicht reproduzierbar (§6).
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update: keiner (kein öffentlicher Vertrag berührt: `src/test`, Register; `docs/user/referenz-ereignisse.md` und die Spezifikation bleiben unverändert).
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben: `evidence/slice-inspektionstest-puffer-unabhaengig.md` in [BEO-TDS/annahme-ueber-puffer-der-umgebung](../observations/BEO-TDS/annahme-ueber-puffer-der-umgebung/observation.md) (mit dem Plan angelegt, Beleg: die drei Läufe der CI); `state.md` bei der Closure auf den Ausgang gesetzt; **kein Zähler wird gesetzt**, er folgt aus den Dateien.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/test/kotlin/io/github/pt9912/tdswire/runtime/TestserverLaufInspektionTest.kt` | update | `upperLimit`, `BUFFER_FACTOR`, `MAX_VOLUME` und die Zusicherung auf die Grenze entfallen; `volume` ist eine Konstante (Setzung, KDoc nennt Rechnung und Grenze der Aussage); die Meldungen von „keine offene Anfrage“ und „die Antwort ist abgebrochen“ nennen die Puffer als Grund; `TDS-QA-06`, `TDS-QA-03`, `TDS-FA-09` |
| `src/test/kotlin/io/github/pt9912/tdswire/runtime/RuntimeSupport.kt`, `LaufClient.kt` | unverändert erwartet | `RawClient(receiveBufferBytes)` setzt `SO_RCVBUF` vor dem Verbinden (Zeile 99 ff.), `LaufClient` reicht ihn durch; ändert die Vorprüfung etwas an der Hilfe (etwa eine Rückfrage des wirksamen Werts), steht es in §7 |
| `docs/plan/planning/observations/BEO-TDS/annahme-ueber-puffer-der-umgebung/` | update | `evidence/slice-inspektionstest-puffer-unabhaengig.md` (Beleg der CI-Läufe) und `state.md` (Stand: eingetreten auf dem Runner, Träger dieser Slice) |

**Weg:**

1. **Vorprüfung (Implementer, vor dem Umbau, Ergebnisse in §7).** Drei Messungen, jede mit Ausgabe, keine Vermutung:
   - *Host-Buffer und Rechnung (a).* Auf dem Runner ist die Summe der Obergrenzen 36 MiB (aus der Menge 144 MiB im Log, Faktor 4), am lokalen Host 10 MiB. Ein Deckel von 40 MiB liegt über beiden Summen, **auch wenn** beide Puffer ganz gefüllt würden; das ist Rechnung, kein Beleg für den Runner, und sie trägt nicht für einen Host, dessen Summe über dem Deckel liegt.
   - *Hypothese H1 (b).* Bei `SO_RCVBUF` 4096 des Clients bleibt die Antwort am Server so weit unter der Obergrenze des Sendepuffers, dass die Menge sie übersteigt. Zu messen im Container mit großen Obergrenzen; **vom Host gemessen am 2026-11-14:** Docker nimmt `--sysctl net.ipv4.tcp_wmem="4096 16384 150994944"` und `--sysctl net.ipv4.tcp_rmem="4096 131072 150994944"` an (Kernel 6.8.0-147), und `cat /proc/sys/net/ipv4/tcp_wmem` im Container nennt `4096 16384 150994944`, am Host bleiben `4096 16384 4194304`; die Werte gelten also je Namensraum. Offen ist, wie der Test in diesem Container läuft (die Stage `test` des `Dockerfile` ist ein Bau-Schritt ohne Sysctl-Option); der Implementer wählt den kleinsten Weg (ein `docker run --sysctl` auf dem Image der Stage mit dem Gradle-Aufruf der engsten Testklasse, ohne neues Make-Ziel) oder benennt „nicht gemessen“.
   - *Heap.* Der Heap des Kindes ist `-Xmx512m`, die Menge wird zuerst in die Tabelle geladen (in der Größenordnung der Menge im Speicher): am lokalen Host 40 MiB, gemessen im Vorgänger-Slice; der Deckel darf diese Größe nicht übersteigen (Host swappt).
2. **Umbau.** Konstante für die Menge, Zeilenzahl daraus (`bigRows`, Zeichen je Zeile und Bytes je Zeichen bleiben), Meldungen mit dem Grund. KDoc der Konstante in der Form Zusage · Kopplung · Grenze: Setzung des Deckels, Bezug zu `RECEIVE_BUFFER` und zu Heap und `CELL_LIMIT`, Grenze „gilt für Hosts, deren Sendepuffer die Menge bei einem Empfangsfenster von 4096 Byte nicht erreicht (H1, gemessen am Container mit 144 MiB Obergrenzen)“; kein Satz über abwesenden Text.
3. **Mutationen M1 bis M3** (§2) mit einer Zeile je Mutation in §7: Änderung, engste Testklasse, Ergebnis, Meldung.
4. **Register.** Evidence-Datei mit Beleg (die drei Läufe, die Meldung, die Summe der Obergrenzen), `state.md` bei der Closure.

**Zuordnung Zusage → Beleg (für die Closure-Notiz).**

| Aussage | Art | Beleg |
|---|---|---|
| Der Test liest keinen Wert des Hosts für die Menge | Konstruktion | Quelltext des Tests, Suche M3 |
| Die Anfrage bleibt bei Menge Deckel und `SO_RCVBUF` 4096 offen | gemessen an: lokalem Host, Container mit 144 MiB Obergrenzen (oder „nicht gemessen“), CI-Lauf auf `main` | Lauf der engsten Testklasse, Gradle-Bericht, CI |
| Deckel 40 MiB liegt über der Summe der Obergrenzen auf Runner (36 MiB) und lokalem Host (10 MiB) | Rechnung, abgeleitet aus der Meldung im Log und aus `/proc` am lokalen Host | §1 Befund |
| Der Sendepuffer des Servers erreicht bei Empfangsfenster 4096 nicht die Menge | **Hypothese H1** bis zur Messung, danach gemessen an dem genannten Host | Vorprüfung |

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): Nutzer priorisiert den Slice (`open` → `next` mit `Verantwortlich`) und gibt ihn frei; keine Vorbedingung in anderen Slices, die rote `gates` auf `main` ist der Anlass. WIP-Limit nach Roadmap beachten.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): die Vorprüfung zeigt, dass der Umbau mehr als die Menge und die Meldungen ändern muss (etwa eine Änderung an `RawClient` und weiteren Tests), also mehr als ein Liefer-Punkt wird.
- `in-progress` → `open` (blockiert): H1 fällt (die Antwort bleibt bei dem Deckel in den Puffern des Containers mit großen Obergrenzen nicht offen) und der Nutzer hat die offene Frage in §6 noch nicht entschieden; der Slice wartet dann auf diesen Entscheid, statt eine Option zu wählen.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün, der Review-Report liegt vor, **und** der Workflow `gates` auf `main` ist nach dem Merge der Umsetzung `success`. Der letzte Punkt hat der Verifier zu belegen: `gh run list --workflow gates --branch main --limit 5` nennt den Lauf mit dem Merge-Commit; die Zeilen des Tests stehen in `gh api repos/pt9912/tdswire/actions/runs/<Kennung>/logs` (liefert ein ZIP, mit `unzip -p` in `gates/3_make gates.txt` suchen: `TestserverLaufInspektionTest`). `gh run view <Kennung> --log-failed` kann für ältere Läufe **leer** sein (die Logs sind nach Ablauf der Aufbewahrung nicht mehr abrufbar, oder der Lauf war nicht rot); ein leeres Ergebnis ist kein Beleg für Grün, der Beleg ist `conclusion: success` des Laufs. Der Lerneintrag benennt die Regel oder den Sensor, den dieser Fund schärft (Kandidat: „ein Prozesstest leitet keine Menge aus Werten des Hosts ab“ — als **Zählung** im Register geführt, verkörpert nur, wenn die Schwelle erreicht ist).

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **Der Runner ist lokal nicht reproduzierbar.** Ein Wert, der in der CI anders ist als lokal (Puffer, Last, Kernel), kann auch nach dem Umbau anders wirken: das Rot dieses Slice entstand aus genau so einem Wert. Gegenmaßnahme: Konstruktion statt Annahme (keine Host-Werte im Test), ein Lauf im Container mit simulierten Obergrenzen, und der CI-Lauf auf `main` als **Teil der Closure-Bedingung** (§5). — **Ausgang:** offen bis zur Closure; der CI-Lauf nach dem Merge entscheidet (eingetreten / entfallen).
- **H1 fällt: der Sendepuffer des Servers fasst die Menge.** Dann bleibt die Anfrage nicht offen, und der Test wird rot an „keine offene Anfrage“, ohne dass der Deckel etwas dagegen hat; ohne Änderung am Produktcode lässt sich der Sendepuffer des Servers nicht begrenzen (§1, gelesen). **Offene Frage an den Nutzer — nicht vom Planner entschieden:** (2) die Grenze anheben, etwa auf einen Wert über der größten bekannten Summe (Runner 36 MiB, Faktor 4 ergäbe 144 MiB gegen `-Xmx512m`; der Host des Nutzers swappt); (3) den Test auf Hosts mit großen Puffern überspringen — **verworfen**: die CI verschwiege die Aussage; (4) einen `internal` Hook für `SO_SNDBUF` im Produktcode (`TdsListener`), Nutzen: Test-Konstruktion unabhängig von Kernel-Autotuning; Kosten: Test-Naht nach `BEO-TDS/test-naht-im-produktivcode` (nur `internal`, mit KDoc, Reviewer-Prüfpunkt), Änderung der Fläche zu prüfen (`make flaeche`), ADR-Pflicht zu klären. Der Planner empfiehlt: Messung der Vorprüfung abwarten, dann Option 4 erwägen, bevor Option 2 den Speicher belastet. — **Ausgang:** offen; entscheidet der Nutzer, falls die Vorprüfung H1 widerlegt (sonst entfallen: H1 gemessen).
- **Deckel über der Summe der Obergrenzen eines dritten Hosts.** Ein Host mit Summe der Obergrenzen über dem Deckel wird von der Rechnung (a) nicht gedeckt; (b) ist an Hosts mit 10 MiB und im Container mit 144 MiB gemessen (H1), an keinem weiteren. — **Ausgang:** weiter offen → `BEO-TDS/annahme-ueber-puffer-der-umgebung` im Register (die Annahme ist enger, nicht beseitigt).
- **Speicher.** Der Deckel gleich 40 MiB lädt und hält dieselbe Menge wie der lokale Lauf; auf dem Runner sinkt sie von 144 MiB auf 40 MiB. Ein anderer Deckel erhöht den Bedarf. — **Ausgang:** entfallen, wenn der Deckel nicht über 40 MiB liegt (Messung im Gradle-Bericht, ein Lauf); sonst Begründung in §7.
- **Laufzeit.** Der Lauf lädt 40 MiB per Massenladen und sendet sie bis zum Cancel; die Dauer im Vorgänger-Slice ist in dessen Closure-Notiz genannt, hier nicht neu gemessen. Der Umbau ändert sie nicht, wenn der Deckel gleich bleibt. — **Ausgang:** entfallen: Menge am lokalen Host unverändert; auf dem Runner kürzer (144 MiB → 40 MiB); Zeit nur als Gradle-Bericht eines Laufs genannt, kein Messversprechen.
- **Mutation M2 ergibt grün.** Dann ist `SO_RCVBUF` 4096 auf dem Host kein tragender Teil der Zusage; die Aussage im KDoc von `RawClient` und im Vorgänger-Slice wäre breiter als der Beleg. — **Ausgang:** offen bis zur Messung; gemessen → Aussage eingeschränkt (eingetreten) oder bestätigt (entfallen).

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

Noch nicht geschlossen. Die Notiz entsteht bei der Closure (Vorprüfung mit Ausgaben, Mutationen M1 bis M3 mit Ergebnis, Lauf im Container mit großen Obergrenzen oder „nicht gemessen“, Kennung des CI-Laufs auf `main`, Laufzeit der engsten Testklasse, Lerneintrag).

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung — dort die **zwei vorgelagerten
Schritte** (sie stehen in jedem Slice-Plan, unabhängig von Modus und
Slice-Typ) und die **vier Pflichtkriterien** (Konventionen-Dichte ·
Phase-Reife · Evidenz-/Diskrepanz-Risiko · Reconciliation-Aufwand), vier und
nicht mehr.

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); der Slice berührt `src/test` und das Register, keine eigene Sub-Area. Alle berührten Sub-Areas GF.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`), nachgesehen am 2026-11-14. Treffer: `BEO-TDS/annahme-ueber-puffer-der-umgebung` (Stand offen, 1 Datei in `evidence/`; Trigger „rotes Gate auf einer anderen Maschine“ eingetreten; dieser Slice legt die zweite Evidence-Datei an, die Zahl folgt aus den Dateien); `BEO-TDS/test-naht-im-produktivcode` (verkörpert; bestimmt die Grenze von §1: kein Hook für `SO_SNDBUF` im Produktcode ohne Entscheid des Nutzers); `BEO-TDS/aussagen-ueber-messung-hinaus` (verkörpert; bestimmt die Einschränkung der Aussagen in KDoc und §7 und die Mutation M2); `BEO-TDS/wanduhr-tests-flackern-unter-last` (offen; der Test bleibt signalgebunden, die Wartestellen werden nicht berührt, keine Zeit-Annahme neu). Sonst keine Treffer.

**Modus-Begründungsblock:** alle berührten Sub-Areas GF; entfällt.
