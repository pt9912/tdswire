# Slice slice-qa-synchronitaet: Protokoll-Synchronität nach Fehler und Abbruch

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-qualitaet-grundlage

**Bezug:** [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität), [ADR-0007](../../adr/0007-fehler-und-attention.md)

**Berührte Spec-Stellen:** SPEC-029 · SPEC-031 · ARC-015

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

**Ziel:** Nach Fehler, Streaming-Fehler und ATTENTION liefert die unmittelbar folgende Anfrage auf derselben Verbindung nur ihre eigene Antwort; ist das nicht sicher, ist die Verbindung geschlossen.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Die Wahl der Recovery-Regel — sie ist in [ADR-0007](../../adr/0007-fehler-und-attention.md) (`Accepted`) entschieden: enge Form von Option B mit A als Grundregel (die Verbindung bleibt offen nur bei Class ≤ 19 mit vollständigem ERROR und DONE sowie nach Cancel oder Timeout mit gesendetem DONE mit ATTENTION-Status, sonst schließt der Server); offen bleiben dort Cancel und Timeout als Fehler, der Inhalt der Cancel-Antwort, die Abbildung statement- und batch-abbrechender Fehler, ATTENTION im Handshake, `RESETCONNECTION` und der Sitzungszustand nach einem Fehler sowie die Zuordnung weiterer Codes (Träger: die Slices der Welle `welle-fehler-abbruch`). Der Slice setzt die Regel um und prüft sie.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [x] Fehler-Matrix gegen den .NET-Client geprüft, einschließlich Wiederverwendung der Verbindung (der Cancel-, Timeout- und ATTENTION-Teil der Messmethode von [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität) ist DoD-Punkt 5 von [slice-attention-cancel-timeout](../in-progress/slice-attention-cancel-timeout.md); Entscheidung des Planners zu Verifikation F-1, siehe §7). **E2E-Stufe:** Die Fehler-Matrix mit Wiederverwendung der Verbindung ist als Stufe im Produkt-E2E ([slice-e2e-geruest](../in-progress/slice-e2e-geruest.md)) mit den Kennungen [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität) deklariert und ruft den Lauf über die Prozessgrenze (Referenzclient oder Rohbytes-Client gegen den Serverprozess), nicht über `internal`-Zugriff im Modul; `make e2e-abdeckung` zeigt sie danach in `docs/plan/e2e-abdeckung.md`.
- [x] Test: keine Antwort wird einer späteren Anfrage zugeordnet.
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
| Response Sequencer und Cancel-Zustand (`ARC-015`) | update | Antwortgrenzen nach [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität) |
| Testquellen | neu | Fehler-/Cancel-Matrix |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

**Ist-Zustand gegen den Plan (Lauf vom 2026-11-01).** Der Response Sequencer (ARC-015) kennt keinen Cancel-Zustand: Die Anfrageschleife liest während einer Anfrage nicht vom Transport und beantwortet ATTENTION nicht ([CO-004](../../carveouts/CO-004-cancel-szenario-spec-040-fehlt.md), Folge-Slice [slice-attention-cancel-timeout](../in-progress/slice-attention-cancel-timeout.md)). Deshalb ändert dieser Lauf keinen Produktquelltext; die Zeile „Response Sequencer und Cancel-Zustand“ bleibt ungebaut. Geliefert ist der Teil, der an keiner offenen Nutzerentscheidung hängt: die **Fehler-Matrix mit Wiederverwendung der Verbindung** und die **Antwortgrenzen am Socket**. Der Cancel-Teil von DoD-Punkt 1 (`SqlCommand.Cancel()`, `CancellationToken`, Timeout, ATTENTION während Streaming mit unmittelbar folgender Abfrage; die Messmethode von [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität)) ist **nicht lieferbar bis [CO-004](../../carveouts/CO-004-cancel-szenario-spec-040-fehlt.md) aufgelöst ist**, weil er den Inhalt der Cancel-Antwort, Cancel und Timeout als Fehler und ATTENTION im Handshake voraussetzt, die der Nutzer noch nicht entschieden hat.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `referenztest-dotnet/Kompatibilitaet/SynchronitaetTests.cs` | neu | Fehler-Matrix am Referenzclient, vier Tests (Kategorie `Synchronitaet`) |
| `src/test/kotlin/io/github/pt9912/tdswire/runtime/SynchronitaetWiringTest.kt` | neu | Rohbytes-Test: mehrere Anfragen in einem Schreibvorgang hinter einem Fehler der Class 16 und der Class 20 |
| `tools/e2e.sh`, `tools/kompatibilitaetstest.sh`, `tools/e2e-selbsttest.sh`, `tools/mk/kompatibilitaet.mk` | update | Stufe 8 mit Kennung `TDS-QA-07`, Kategorie `Synchronitaet`, Selbsttest um Abbruch an Stufe 8 |
| `docs/plan/e2e-abdeckung.md`, `docs/plan/kompatibilitaetsmatrix.md` (Abschnitt 7d), `harness/README.md` | update | Sicht neu erzeugt, Messungen, Zeilen der Targets |

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [ADR-0007](../../adr/0007-fehler-und-attention.md) Accepted und `slice-qa-kompatibilitaet` done.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert — Carveout?): Die Matrix zeigt Desynchronisation, die ein Folge-ADR klären muss.

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

- Die Recovery-Regel ([ADR-0007](../../adr/0007-fehler-und-attention.md), `Accepted`) ist eine Setzung des Nutzers; gemessen sind am echten Server nur Class 20 und 25 und die Fehler der Class ≤ 19, an keinem Server dieses Repos. Die Matrix kann sie widerlegen, und die in der ADR offenen Punkte können sie ändern. — **Ausgang:** weiter offen: Die Matrix hat die Regel für Class 16 und 20 am Server dieses Repos nicht widerlegt; Cancel, Timeout und ATTENTION sind ungemessen, Träger sind [CO-004](../../carveouts/CO-004-cancel-szenario-spec-040-fehlt.md) und [slice-attention-cancel-timeout](../in-progress/slice-attention-cancel-timeout.md); ein echter SQL Server → `BEO-TDS/messung-am-echten-sql-server-fehlt`
- Der Cancel-Teil der Messmethode von [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität) (`SqlCommand.Cancel()`, `CancellationToken`, Command-Timeout, ATTENTION während des Streamings mit unmittelbar folgender Abfrage) ist nicht geliefert, weil er den Inhalt der Cancel-Antwort und die Behandlung von Cancel und Timeout als Fehler voraussetzt, die der Nutzer nicht entschieden hat. [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität) ist damit nur für Fehler und Wiederverwendung der Verbindung belegt. — **Ausgang:** weiter offen: DoD-Punkt 5 von [slice-attention-cancel-timeout](../in-progress/slice-attention-cancel-timeout.md) trägt den Teil, [CO-004](../../carveouts/CO-004-cancel-szenario-spec-040-fehlt.md) bleibt bis dahin offen.

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

- **Gegenstand:** entfällt nicht, der Slice lieferte die Fehler-Matrix mit Wiederverwendung der Verbindung und die Antwortgrenzen am Socket, keinen Produktivcode (`src/main` unverändert, aus `src` nur ein neuer Test). Der Cancel-, Timeout- und ATTENTION-Teil der Messmethode von [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität) ist nicht geliefert.
- **Entscheidung zu Verifikation F-1 (Planner):** Option 1, DoD-Punkt 1 auf „Fehler-Matrix mit Wiederverwendung der Verbindung“ gekürzt; der Cancel-Teil steht als DoD-Punkt 5 in [slice-attention-cancel-timeout](../in-progress/slice-attention-cancel-timeout.md) (nur DoD, §1 und §6 geändert, der Start-Trigger und die offenen Nutzerentscheidungen dort bleiben). Gründe: (a) Der Teil ist laut [CO-004](../../carveouts/CO-004-cancel-szenario-spec-040-fehlt.md) bis zu einer Nutzerentscheidung nicht lieferbar; Option 2 hielte einen Slice mit belegter Teillieferung und belegtem Review in `in-progress/` und belegte das WIP-Limit der Welle an einer Entscheidung, die der Slice nicht beeinflusst. (b) Option 3 (Gegenstand an einen anderen Slice) passt nicht, weil der größere Teil geliefert ist; sie ist für den Fall gedacht, dass der Gegenstand ganz übernommen wird. (c) Die Kürzung ist keine Lockerung eines Gates (3.6) und keine Änderung einer `Accepted`-ADR (3.5): [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität) bleibt unverändert, und die Teilung ist sichtbar, weil Stufe 8 „ohne Cancel“ sagt, das Risiko in §6 zwei Zeilen trägt und der Folge-Slice den Teil mit eigener DoD führt. (d) Rest-Risiko, benannt: [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität) ist bis zum Folge-Slice nur zum Teil belegt, und `slice-attention-cancel-timeout` hat nach dieser Ergänzung fünf Liefer-Punkte (Grenze: drei); der Slice hatte schon vorher vier und überlässt die Zerlegung ausdrücklich dem Start, die Ergänzung verschärft das, macht es aber nicht neu. Die Kennung `TDS-QA-07` in Stufe 8 bleibt gröber als die Aussage (Review S-3, Verifikation F-4); die Begrenzung trägt der Text, die Berichtigung der Zeile ist Teil von DoD-Punkt 5 des Folge-Slice.
- **Was hat funktioniert:** Die Teilung nach dem, was an keiner Nutzerentscheidung hängt, trug: Der Slice legte den Ist-Zustand (kein Cancel-Zustand im Response Sequencer) vor dem Code in §3 fest und änderte keinen Produktquelltext. Der Beleg ist doppelt: Rohbytes-Tests (Antwortgrenzen am Socket, Mutationen A1 und B von der Verifikation gefahren) und vier Referenzclient-Tests über die Prozessgrenze (Mutation A2 gefahren: zwei von vier rot, Class 20 und Batch/RPC grün). Die Fix-Runde (66a6e51, eb35e76, 7ede9ed) schloss die Mediums des Reviews (S-1, S-2) und wurde von der Verifikation nachgemessen.
- **Was ging anders als geplant:** (a) **DoD-Punkt 1 nannte eine „Fehler-/Cancel-Matrix“, deren Cancel-Teil der Plan selbst in §3 als nicht lieferbar vorfand** (F-1): der Wortlaut war breiter als die Lieferung; entschieden oben. (b) **Die Matrix beschrieb eine Mutation, die ihre Wirkung nicht hatte** (S-1): `finish()` doppelt schließt die Verbindung, schreibt keine zweite Nachricht; behoben durch Einschränken, die wirklich überzählige Nachricht fuhr die Verifikation. (c) **Der Rohbytes-Test antwortete dreimal mit derselben Nutzlast** (S-2): eine Verschiebung um eins war nicht sichtbar; behoben, die Zeilen 7, 8 und 9 sind verschieden. (d) **Die Fix-Runde hatte keinen eigenen Review** (F-3); die Verifikation fand bei der Gegenprüfung keinen Mangel. (e) **Der Merge mit `main` konfligierte in der erzeugten `docs/plan/spec-abdeckung.md`** (F-2): gelöst durch Neuerzeugung mit `make spec-abdeckung`, nicht von Hand.
- **Reports:** [Review](../../../reviews/2026-11-01-018eb7d-slice-qa-synchronitaet.md) (kein HIGH; S-1 und S-2 MEDIUM, S-3 bis S-9 LOW oder INFO) und [Verifikation](../../../reviews/2026-11-01-7ede9ed-verifikation-slice-qa-synchronitaet.md) (Stand `7ede9ed`; kein HIGH; F-1 und F-2 MEDIUM, F-3 bis F-6 LOW). Stand der Befunde: S-1, S-2, S-4, S-5 in der Fix-Runde behoben und von der Verifikation gemessen; S-3 und F-4 (Kennung gröber als Aussage) benannt, Berichtigung bei DoD-Punkt 5 des Folge-Slice; F-1 oben entschieden; F-2 in der Merge-Auflösung bearbeitet; F-3 benannt, nicht nachgeholt; F-5 und F-6 im Register (`BEO-TDS/dispose-verschluckt-quellfehler`, `BEO-TDS/server-schliesst-bei-class-20-pipeline`). INFO der Verifikation: `SynchronitaetWiringTest` hängt an `internal`-Typen und wandert beim Umbau nach [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) mit; das trägt `welle-produktschnitt`. Ein Validator-Lauf entfällt: der Slice liefert keinen End-Nutzer-Wert, nur einen Beleg.
- **Gate:** `make gates` auf dem Stand dieser Closure: Exit 0. Als Frischbeleg der Tests gelten `make spec-abdeckung` dieser Closure (führt die Tests aus und schreibt die Matrix neu; `SynchronitaetWiringTest` zählt bei `SPEC-031` und `SPEC-048` als bestanden) und der Lauf `make kompatibilitaet KATEGORIE=Synchronitaet` der Verifikation (4 von 4 bestanden). Nicht gefahren: `make e2e` als Ganzes, `make matrix-zitate`.

**Zuordnung Beleg | Aussage.** Fünf Stärken: *gemessen* heißt an einem echten Client oder Server beobachtet; *Handvektor* heißt von Hand abgeleitet; *Selbstkonsistenz* heißt Code gegen Code; *Festlegung* heißt Setzung des Nutzers oder des Slice ohne Messung; *nicht gemessen* nennt, was keine Aussage trägt.

| Stärke | Aussage | Beleg im Repo |
|---|---|---|
| gemessen | Microsoft.Data.SqlClient 7.1.1 gegen den Serverprozess dieses Repos (nicht gegen einen echten SQL Server): sechs Runden Fehler nach drei Zeilen (50004, Class 16) mit Folgeabfrage auf derselben Verbindung, Antwort an Id und Name erkannt; Batch- und RPC-Fehler (50001) mit Folgeabfragen; nach Class 20 (208) scheitert die Folgeanfrage, `State` ist `Closed` (Verhalten des Clients); ein verworfener Leser hinter einem Zeilenstrom mit Fehler | `SynchronitaetTests` (4 Tests, Kategorie `Synchronitaet`), Matrix 7d in `docs/plan/kompatibilitaetsmatrix.md`; Stufe 8 von `make e2e` |
| Handvektor | Die Zeilenantworten 7, 8 und 9 und die Tokenfolge ColMetadata, Row, Row, Error, Done mit `DONE_ERROR` der ersten Antwort sind von Hand gebaut | `SynchronitaetWiringTest`, von Hand abgeleitet, ein Nachrechnen mit anderem Werkzeug liegt nicht vor |
| Selbstkonsistenz | Drei Anfragen in einem Schreibvorgang hinter einem Fehler der Class 16 erhalten je ihre eigene Antwort in der Reihenfolge der Anfragen, vier Request-Ids am Backend; hinter einem Fehler der Class 20 folgt kein weiteres Byte, das Backend sieht nur die erste Anfrage; Mutationen A1, A2 und B von der Verifikation gefahren, alle erkannt | `SynchronitaetWiringTest` (Rohbytes-Client, Test-Backend, Socket auf 127.0.0.1) |
| Festlegung | Class 16 lässt die Verbindung offen, Class 20 schließt, `TDS_BACKEND_ERROR` offen, `TDS_INTERNAL_ERROR` schließt; die Class-Werte sind Setzungen | [ADR-0007](../../adr/0007-fehler-und-attention.md), `ErrorCode` |
| nicht gemessen | `SqlCommand.Cancel()`, `CancellationToken`, Command-Timeout, ATTENTION während des Streamings mit Folgeabfrage; mehrere Anfragen in einem Schreibvorgang am Referenzclient; Pooling mit vom Server geschlossener Verbindung; MARS; ein echter SQL Server; der RST beim Schließen mit ungelesenen Bytes; eine überzählige Nachricht, die einer echten Antwort gleicht; `finish()` zweimal und `make matrix-zitate` (nicht gefahren) | [CO-004](../../carveouts/CO-004-cancel-szenario-spec-040-fehlt.md), [slice-attention-cancel-timeout](../in-progress/slice-attention-cancel-timeout.md), Register `BEO-TDS/messung-am-echten-sql-server-fehlt`, `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`, `BEO-TDS/server-schliesst-bei-class-20-pipeline` |

- **Steering-Loop-Eintrag:** Kein `liegt in`: mit diesem Slice ist keine Regel, kein Sensor und keine Gate-Zeile verkörpert worden; gezählt wird, nicht verkörpert. Drei Feststellungen. (1) **Gelernt: Eine E2E-Stufe trägt eine Anforderungs-Kennung nur so weit, wie ihre Messung reicht; die Kennung `TDS-QA-07` stand an einer Stufe, die den Kern der Messmethode (Cancel, ATTENTION) nicht ausführt, und eine Abdeckungs-Auswertung, die Kennungen zählt, sieht die Anforderung als gedeckt** (Review S-3, Verifikation F-4). Dasselbe Muster trägt Stufe 5. Klasse `BEO-TDS/aussagen-ueber-messung-hinaus` (45×, *verkörpert* mit Anker `seit welle-protokollcodec`, die Regel umfasst KDoc, Spec und Tests, nicht die Kennungs-Spalte einer Abdeckungs-Sicht). Kandidat für eine Schärfung, keine Setzung: eine Teil-Abdeckung einer Anforderung nennt in der Zeile der Abdeckungs-Sicht, was nicht belegt ist, und die Anforderung behält einen Träger für den Rest in einer DoD; das ändert `tools/e2e.sh` oder den Reviewer-Skill und ist hier nicht getan. (2) **Die Teilung einer Anforderung zwischen zwei Slices ist ein Planner-Akt mit Träger-Pflicht**: die Entscheidung zu F-1 zeigt, dass der Move nach `done/` den Träger des Rests mitnähme, wenn er nur im Text des gelieferten Slice stünde. Klasse `BEO-TDS/offene-punkte-ohne-traeger` (18×, *verkörpert* in `.claude/commands/implement-slice.md` Schritt 24, Absatz „Entscheidungs-Slice“, Anker `seit welle-entscheidungen-grundlage`): der Absatz deckt den Slice, der eine ADR entscheidet, nicht den Slice, der eine Anforderung teilweise liefert. Kandidat für eine Schärfung, keine Setzung: den Absatz auf jeden Slice erweitern, der einen Teil einer Anforderung oder eines ADR-Punkts weiter offen nach `done/` bringt; das ändert einen Workflow-Command und ist hier nicht getan. (3) **Neue Beobachtungen, je einmal:** `BEO-TDS/server-schliesst-bei-class-20-pipeline`, `BEO-TDS/client-schliesst-bei-class-20-selbst` (ein Referenzclient-Test belegt das Clientverhalten, nicht das des Servers), `BEO-TDS/dispose-verschluckt-quellfehler`. Keine Beobachtung angefallen zum RST bei Schließen mit ungelesenen Bytes (F-6): aus dem Code geschlossen, nicht aufgetreten, ohne Evidence-Datei außer der Notiz in `BEO-TDS/server-schliesst-bei-class-20-pipeline`. Keine Spec-Lücke benannt: die Spezifikation bleibt für Fehler und Wiederverwendung unverändert; die offenen Punkte stehen in [ADR-0007](../../adr/0007-fehler-und-attention.md) und tragen Slices.
- **Beobachtungs-Register (`../observations/`):** `evidence/slice-qa-synchronitaet.md` ergänzt in `BEO-TDS/messung-am-echten-sql-server-fehlt`, `BEO-TDS/aussagen-ueber-messung-hinaus` (S-1, S-3, F-4), `BEO-TDS/strukturtest-ohne-variation-der-eingabe` (S-2; die Zuordnung zu diesem Verzeichnis statt zu `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test` folgt dem Mechanismus: die Eingabe des Tests variierte nicht, ein Test zur Zeile war da) und `BEO-TDS/offene-punkte-ohne-traeger` (F-1); neu angelegt `BEO-TDS/server-schliesst-bei-class-20-pipeline`, `BEO-TDS/client-schliesst-bei-class-20-selbst`, `BEO-TDS/dispose-verschluckt-quellfehler`. Die vier bestehenden Klassen standen schon über der Schwelle und tragen einen Ausgang; keine Entscheidung dieser Closure ändert einen Stand. Kein neues Auftreten: `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test`, `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` und die übrigen. Die Zähler folgen aus den Dateien.
- **Risiken aus §6:** beide tragen genau einen Ausgang (siehe §6): beide weiter offen mit Träger, der Cancel-Teil in `slice-attention-cancel-timeout` DoD-Punkt 5 und [CO-004](../../carveouts/CO-004-cancel-szenario-spec-040-fehlt.md), die Messung am echten Server im Register. Kein Risiko ist eingetreten, das einen Carveout verlangt; [CO-004](../../carveouts/CO-004-cancel-szenario-spec-040-fehlt.md) bleibt offen, sein Folge-Slice ist unverändert `slice-attention-cancel-timeout`.
- **Drei Paarungen:** Anker: kein Eintrag trägt `liegt in`, die Paarung entfällt. Folge-Slice: kein neuer Slice; `slice-attention-cancel-timeout` führt den Cancel-Teil als DoD-Punkt 5 (gegen den Inhalt der Datei geprüft). Register: sieben Evidence-Dateien dieses Slice (vier ergänzt, drei neue Verzeichnisse), siehe oben.

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

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/` trägt nur die `README.md`): keine Treffer.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
