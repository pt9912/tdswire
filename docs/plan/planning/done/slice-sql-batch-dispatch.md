# Slice slice-sql-batch-dispatch: SQL_BATCH-Dispatch in READY auf den Backend-Port

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-anfrageverarbeitung

**Bezug:** [TDS-FA-03](../../../../spec/lastenheft.md#tds-fa-03--einfache-sql-batches), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [ADR-0004](../../adr/0004-session-zustandsautomat.md), [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md), [ADR-0007](../../adr/0007-fehler-und-attention.md)

**Berührte Spec-Stellen:** SPEC-001 · SPEC-002 · SPEC-031 · ARC-005 · ARC-007 · ARC-015 · [TDS-FA-03.a](../../../../spec/spezifikation.md#tds-fa-03a--sql_batch)

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

**Ziel:** Eine Sitzung im Zustand `READY` nimmt ein SQL_BATCH an, dekodiert den Batchtext, übergibt ihn als `QueryRequest` ohne Parameter an den Backend-Port und gibt die Ereignisse des Backends an den Antwort-Sequenzierer weiter, sodass ein Test-Backend mit einem Ergebnis, einem leeren Ergebnis und einem Fehler jeweils zu einer vollständigen, geordneten Antwort führt und die Sitzung danach für die nächste Anfrage frei ist ([TDS-FA-03](../../../../spec/lastenheft.md#tds-fa-03--einfache-sql-batches)).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Backend-Port, `Flow<QueryEvent>`, Ereignistypen, Typkatalog, Serialisierung von COLMETADATA und ROW sowie Backpressure — die parallel geplante Welle `welle-backend-streaming` ([ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)); dieser Slice ruft den Port und nimmt den Sequenzierer als Schnittstelle, er schneidet beide nicht.
- Fehlerabbildung (Class, State, Meldung), ERROR- und DONE-Kodierung, `TDS_*`-Codes, ATTENTION, Cancel, Timeout und die Semantik der Frist `deadlineEpochMillis` — die parallel geplante Welle `welle-fehler-abbruch` ([ADR-0007](../../adr/0007-fehler-und-attention.md)); dieser Slice setzt die Frist auf `null` und reicht einen Backend-Fehler an den Fehlerpfad jener Welle weiter.
- Parameter, RPC und Mehrfach-Ergebnisse in einer Antwort — die Slices `slice-rpc-executesql-parameter` und `slice-antwort-mehrteilig-output` dieser Welle; ein Batch ohne Parameter ist hier der kleinste Fall, an dem Dispatch und Anfragezustand gezeigt werden.
- Verbindungs- und Handshake-Zustände, Login, Zustand `READY` herstellen — `slice-handshake-login`; dieser Slice setzt `READY` voraus.
- Behandlung der `SET`-Anweisungen im Batchtext (`SET FMTONLY`, `SET NO_BROWSETABLE`) und Transaktionen (Pakettyp `0x0E`) — offen in [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) §Offen; der Dispatch reicht den Text unverändert weiter und entscheidet nichts darüber.
- Parsen von SQL — der Server prüft nicht, ob der Text gültig ist; "nicht unterstütztes SQL" ist eine Antwort des Backends ([TDS-FA-03](../../../../spec/lastenheft.md#tds-fa-03--einfache-sql-batches) Out-of-Scope: keine T-SQL-Kompatibilität).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [x] **Annahme und Dispatch** ([TDS-FA-03.a](../../../../spec/spezifikation.md#tds-fa-03a--sql_batch), SPEC-001, ARC-005): In `READY` löst ein SQL_BATCH den Anfragezustand `IDLE → RUNNING` aus; ALL_HEADERS wird gelesen, der Batchtext UTF-16 dekodiert, ungültige Kodierung ergibt ein definiertes Fehlerergebnis ohne Absturz; das Test-Backend empfängt Sitzungs-ID, neue Anfrage-ID und den Text unverändert, mit leerer Parameterliste. Ein SQL_BATCH außerhalb `READY` bleibt ein unzulässiger Übergang im Automaten des Handshake-Slice (Test, kein neues Verhalten).
- [x] **Ergebnis und leeres Ergebnis** ([TDS-FA-03](../../../../spec/lastenheft.md#tds-fa-03--einfache-sql-batches) Happy Path und Boundary, SPEC-031): Ein Test-Backend mit einem Ergebnis (Spalten, Zeilen) und eins mit einem Ergebnis ohne Zeilen führen je zu einer Antwort, die der Sequenzierer vollständig abschließt; beim leeren Ergebnis stehen Spaltenmetadaten und ein ordnungsgemäßer Abschluss in der Antwort. Der Anfragezustand kehrt nach `IDLE` zurück, und ein zweites Batch auf derselben Sitzung wird ebenso beantwortet (zwei Anfragen in Folge, eine Sitzung).
- [x] **Nicht unterstütztes SQL** ([TDS-FA-03](../../../../spec/lastenheft.md#tds-fa-03--einfache-sql-batches) Negative): Meldet das Test-Backend einen Fehler (Failure-Ereignis vor jeder Zeile), erhält der Client den definierten SQL-Fehler über den Fehlerpfad der Welle `welle-fehler-abbruch`, und die Sitzung nimmt das nächste Batch an, soweit [ADR-0007](../../adr/0007-fehler-und-attention.md) die Verbindung offen lässt (Anfragefehler-Gruppe, Class 16, Setzung des Nutzers, nicht gemessen); der Test zeigt Fehlerantwort und anschließend ein erfolgreiches Folgebatch.
- Verdrahtung des Fehler-Mappings (Träger für `ErrorMapper`, der in `src/main` bis dahin keinen Leser hat): Der Dispatch erzeugt die Anfrageantwort über `ResponseSequencer` mit `ErrorMapper.sequencerMapping()`; die Verbindungsfolge liest er über `ErrorMapper.fateOf(outcome)` aus dem `SequenceOutcome` des Laufs. Ein Test zeigt, dass ein Ausgang, der die Verbindung schließt (zum Beispiel ein Typfehler), nicht als offene Verbindung endet. Gehört zum Punkt „Nicht unterstütztes SQL“ und zählt nicht als eigener Liefer-Punkt.

Nachweis-Pflichten für alle Punkte:

- [x] Jeder Test trägt im KDoc über der Testfunktion den Block-Tag `@spec SPEC-<NNN>` (Konvention nach `slice-qa-spec-abdeckung`); die Tests laufen in `make test` und sind grün.
- [ ] Jede Aussage steht an einem Test oder ist als Festlegung gekennzeichnet (KDoc des Elements). Die Zuordnung *Test | Festlegung* je Aussage steht in der Closure-Notiz (§7). **Nicht erfüllt, benannt:** zwei KDoc-Sätze tragen mehr, als der Beleg trägt (`BufferedResponse`, `RequestLoop`), siehe §6; der Code bleibt in dieser Closure unverändert. Die Tests laufen gegen ein Test-Backend, das den Backend-Port implementiert, nicht gegen einen echten SQL Server und nicht gegen den Referenzclient; Aussagen über deren Verhalten trägt dieser Slice nicht.
- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo mit Wellen von der Welle-Closure geprüft.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/` (Unterpackage `dispatch`) <!-- d-check:ignore (geplant: neues Package) --> | neu | Dispatch von SQL_BATCH in `READY` (ARC-005): Nachricht lesen, `QueryRequest` bilden, Backend-Port rufen, Ereignisse an den Sequenzierer geben; importiert Domain, Backend-Port und die Codec-Typen, nichts aus `transport`, `simulation`, `runtime` (SPEC-041) |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/` (Sitzung aus `slice-handshake-login`) | update | Anschluss des Dispatch an die Sitzung im Zustand `READY`; Anfragezustand `IDLE → RUNNING → STREAMING → COMPLETED → IDLE` nach [ADR-0004](../../adr/0004-session-zustandsautomat.md), soweit dort entschieden |
| `spec/spezifikation.md` `SPEC-021` | update, nur falls nötig | Festlegung, was der Server mit `SET`-Text und leerem Batch tut, soweit der Slice sie trifft |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/` (Unterpackage `dispatch`) <!-- d-check:ignore (geplant: neues Package) --> | neu | Dispatch-Tests mit einem Test-Backend (Happy/Boundary/Negative nach [TDS-FA-03](../../../../spec/lastenheft.md#tds-fa-03--einfache-sql-batches)); `@spec`-Tag je Test |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

- Reihenfolge: Annahme und Dekodierung, dann Ergebnisabbildung über den Sequenzierer, dann der Fehlerfall.
- Das Test-Backend ist ein Test-Fixture, kein Simulations-Backend (ARC-016); es gibt keine zweite Backend-Implementierung im Produktivcode.
- `make detekt` läuft auch über `detektTest`; große `when`-Blöcke werden im Code zerlegt, nie unterdrückt (`AGENTS.md` §3.2, §3.6).

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): Der Start-Trigger der Welle `welle-anfrageverarbeitung` ist eingetreten; zusätzlich liegen der Backend-Port mit dem Antwort-Sequenzierer (`slice-backend-port-vertrag` und `slice-backend-sequenzierer` der Welle `welle-backend-streaming`) und das Fehler-Mapping mit der ERROR-/DONE-Kodierung (`slice-fehler-mapping` der Welle `welle-fehler-abbruch`) in `done/` und ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2; Schnittvorschlag: der Fehlerfall wird ein eigener Slice nach den übrigen Slices der Welle.
- `in-progress` → `open` (blockiert — Carveout?): Backend-Port oder Fehlerpfad der Nachbarwellen liegen nicht vor, oder ihre Schnittstelle passt nicht zum hier gewählten Anschluss des Dispatch.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün (einschließlich `make test`), der Review-Report liegt vor, und die Closure-Notiz trägt die Zuordnung *Test | Festlegung* je Aussage sowie den Steering-Loop-Lerneintrag (geschärfte Regel, neuer Sensor oder benannte Spec-Lücke; eine Closure ohne Lerneintrag ist keine). Gemessen ist in diesem Slice nur, was ein Test gegen das Test-Backend belegt; was der Referenzclient oder ein echter SQL Server sendet oder annimmt, ist nicht gemessen und bleibt bei `slice-qa-kompatibilitaet` und `slice-qa-reproduzierbarkeit`.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Der Antwort-Sequenzierer (ARC-015) ist in keiner Welle eindeutig zugeordnet: [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) führt die `DONE_MORE`-Vorschau, [ADR-0007](../../adr/0007-fehler-und-attention.md) und [ADR-0004](../../adr/0004-session-zustandsautomat.md) den Anfragezustand und ATTENTION. Dieser Plan nimmt den Sequenzierer als Schnittstelle von den Nachbarwellen; passt sie nicht, entsteht Umbau hier. — **Ausgang:** entfallen: Der Sequenzierer (`ResponseSequencer`) passte als Schnittstelle. Der Dispatch ruft ihn mit `ErrorMapper.sequencerMapping()` ohne Umbau; der Diff des Slice berührt `protocol/response` nicht (Review). Den Anfragezustand hält der Sitzungsrahmen, nicht der Sequenzierer (SPEC-001, ARC-015 in der Sicht nachgeführt).
- Das Zusammenspiel des Anfragezustands mit dem Zustandsautomaten ist in [ADR-0004](../../adr/0004-session-zustandsautomat.md) nicht festgelegt (Zuschnitt: eine Aufzählung oder getrennt nach Verbindungs- und Anfragezustand; [ADR-0004](../../adr/0004-session-zustandsautomat.md) ist `Accepted` und führt den Zuschnitt offen, `slice-handshake-login` trägt die Entscheidung). Der Slice startet erst, wenn der Zuschnitt entschieden ist. — **Ausgang:** entfallen: SPEC-001 legt den Zuschnitt flach fest (Setzung des Nutzers vom 2026-10-30, für den Anfragezustand nicht gemessen), und der Code folgt ihm (Matrixtest, 126 Zellen: 32 zulässig, 6 ignoriert, 88 unzulässig). Die Spannung zu [ADR-0004](../../adr/0004-session-zustandsautomat.md), die den Zuschnitt weiter als offen führt, ist kein Hindernis dieses Slice; sie steht in §7 als „Folge-ADR nötig“.
- Die Reihenfolge der Tokens und die DONE-Statusbits eines echten SQL Servers sind nicht gemessen (`BEO-TDS/messung-am-echten-sql-server-fehlt`); die Tests belegen die Festlegung aus MS-TDS und dem Spike-Testserver. — **Ausgang:** weiter offen: → Register `BEO-TDS/messung-am-echten-sql-server-fehlt` (Stand geplant, Träger `slice-qa-vergleichslauf-d`); die Tests belegen Handvektoren und Festlegungen, nichts am echten Server.
- Ein Batch mit mehreren Anweisungen, das der Client als mehrere Ergebnisse liest, ist Gegenstand des Slice `slice-antwort-mehrteilig-output`; schneidet die Closure des Backend-Slices die Ereignisse anders, ist dort nachzuziehen. — **Ausgang:** weiter offen: → [slice-antwort-mehrteilig-output](../open/slice-antwort-mehrteilig-output.md). Der Dispatch reicht die Ereignisse des Backends unverändert an den Sequenzierer; die Ereignisse sind nicht anders geschnitten worden, ein Nachziehen war nicht nötig. Ein Batch mit mehreren Ergebnissen ist hier nicht getestet.
- Die Frist `deadlineEpochMillis` bleibt `null`; wer sie durchsetzt, ist offen ([ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)), Träger der Entscheidung ist `slice-adr-backend-grenzen` (S3). Ein Test-Backend ohne Frist sagt nichts über das Verhalten bei Ablauf. — **Ausgang:** weiter offen: → [slice-adr-backend-grenzen](../in-progress/slice-adr-backend-grenzen.md) (S3, Durchsetzung der Frist). Die Frist bleibt `null`, ein Test hält es.
- **Die Antwort wird vollständig gepuffert (`BufferedResponse`):** ohne Obergrenze und ohne Rückstau, `TokenEncoder.toByteArray` kopiert zusätzlich; ein großes Ergebnis füllte den Heap des Prozesses, sobald ein Backend verdrahtet ist (heute setzt `Main` keines). Die Abweichung von [TDS-FA-08.a](../../../../spec/spezifikation.md#tds-fa-08a--backend-vertrag) ist als Festlegung gekennzeichnet; ein Größentest fehlt. — **Ausgang:** weiter offen: → [slice-backend-streaming-nachweis](../open/slice-backend-streaming-nachweis.md) (ersetzt `BufferedResponse`).
- **Disconnect der Gegenstelle während einer Anfrage:** Die Schleife liest während der Anfrage nicht und bemerkt den Disconnect erst beim Senden; das Backend läuft bis zum Ende ([TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe)). Der Träger ist DoD-Punkt 1 von [slice-abbruch-ressourcen-shutdown](../open/slice-abbruch-ressourcen-shutdown.md) („Disconnect in jedem Zustand“, auch `RUNNING` und `STREAMING`); [slice-attention-cancel-timeout](../open/slice-attention-cancel-timeout.md) schließt den Disconnect aus (Verifikation V-1, Verweis berichtigt). Das Risiko ist heute nicht erreichbar, weil `Main` kein Backend verdrahtet. — **Ausgang:** weiter offen: → [slice-abbruch-ressourcen-shutdown](../open/slice-abbruch-ressourcen-shutdown.md).
- **KDoc-Satz von `BufferedResponse` ist nur für ROW belegt (Verifikation V-3, Zusatzmutant K5):** Der Satz, ein abgelehntes Token löse den Beginn des Ergebnisses nicht aus, ist für ein ROW ohne COLMETADATA belegt; ein COLMETADATA, das der Encoder ablehnt, erzeugt der Sequenzierer nicht. K5 (`onResultStart()` vor dem Schreiben) überlebt folgenlos, weil kein erreichbares Ereignis den Unterschied zeigt. Der Satz ist enger zu fassen oder an einem Test mit ablehnbarem COLMETADATA zu belegen; in dieser Closure bleibt der Code unverändert. — **Ausgang:** weiter offen: → Register `BEO-TDS/aussagen-ueber-messung-hinaus` (Stand verkörpert); Hinweis an den Architect, die KDoc zu korrigieren.
- **KDoc-Vorbehalt zu `sinkAround` (Verifikation §5):** Der Satz „Kein Backend-Ereignis lässt den Encoder ein Token ablehnen“ ist eine Aussage über den Bestand und nicht als Festlegung gekennzeichnet; belegt ist sie nur teilweise (`MappingFuzzTest` schreibt abgebildete Werte durch den Encoder), für alle Ereignisse nicht nachgewiesen. Die Naht beweist die Reaktion der Schleife auf eine ablehnende Senke, nicht, dass ein echtes Ereignis eine ablehnt. Die Sichtbarkeit (öffentlicher Konstruktorparameter statt `internal`) ist eine Stilfrage des Architect. — **Ausgang:** weiter offen: → Register `BEO-TDS/aussagen-ueber-messung-hinaus` und `BEO-TDS/test-naht-im-produktivcode` (neu); Hinweis an den Architect, den Satz als Festlegung zu kennzeichnen oder zu belegen.
- **[ADR-0004](../../adr/0004-session-zustandsautomat.md) und SPEC-001 stehen beim Zuschnitt nebeneinander (Review F-8, Verifikation V-4):** Die ADR (Accepted, unverändert nach `AGENTS.md` §3.5) führt „flach oder getrennt“ als offen, die Spec legt „flach“ fest. Ob eine Folge-ADR den Zuschnitt schließt, entscheidet der Architect mit dem Nutzer; diese Closure schreibt keine ADR. — **Ausgang:** weiter offen: → Register `BEO-TDS/offene-punkte-ohne-traeger` (weiteres Auftreten, Stand bleibt verkörpert); „Folge-ADR nötig“ in §7.

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

- **Was hat funktioniert:** Der Schnitt trug, die Rückführung nach §4 war nicht nötig. Drei Liefer-Punkte und der Zusatzpunkt (Verdrahtung des Fehler-Mappings) sind im Code bestätigt. Die Tests fanden mit Mutationen, was sie versprechen: Der Verifier fuhr am Stand der Fix-Runde die Mutanten M1, M8, M10, M11, M12, M14, M15 sowie K, K2, K3 und K4, alle rot; der Zusatzmutant K5 überlebt (V-3). Die Stage `test` lief frisch ohne Cache mit 503 bestandenen Tests. Die Hard-Rule-Prüfungen des Reviews (§3.4 Sicht meilensteinfrei, §3.5 ADR unverändert, §3.6 kein Gate gelockert) waren ohne Befund, und der Verbindungsfolge-Ausgang des Fehler-Mappings ist an allen vier Ausgängen von `ErrorMapper.fateOf` gefahren.
- **Was ging anders als geplant:** (a) Der Anfragezustand musste in der Sitzung und in der Spec festgelegt werden: SPEC-001 und ARC-004/ARC-015 sind nachgeführt (flacher Zuschnitt, Sperre je Sitzung, der Sequenzierer hält keinen Zustand). (b) Der Review fand fünf MEDIUM-Befunde (F-1 bis F-5) und zwei LOW (F-6, F-7); die Fix-Runde `aeab8a9` behob F-1, F-2 (als Festlegung gekennzeichnet), F-4, F-5, F-6 und F-7, ließ F-3 und F-8 ehrlich offen und nannte für F-9 einen falschen Träger. (c) Der Verifier fand V-1 (MEDIUM, Träger des Disconnects), V-2 (LOW, zwei Spec-Sätze im Indikativ über Ungebautes), V-3 und V-4 (INFO); diese Closure bearbeitet sie: V-1 in diesem Slice (§6) und in `slice-backend-streaming-nachweis` (§6) berichtigt, V-2 in SPEC-001 als Zielbild gekennzeichnet, V-3 als Risiko geführt, V-4 als „Folge-ADR nötig“ unten. (d) Der Ausgang `CLOSE_WITHOUT_COMPLETION` war nur über einen Einhängepunkt im Produktivcode fahrbar (`RequestLoop.sinkAround`); der Verifier hielt ihn für akzeptabel mit drei Vorbehalten (§6). (e) Die Commit-Nachricht der Fix-Runde behauptete eine Änderung an `slice-attention-cancel-timeout`, die der Diff nicht enthält (V-1); für den Verifier war sie eine Behauptung, kein Beleg. (f) Der Plan nannte `slice-attention-cancel-timeout` als Träger des Disconnects; der Slice schließt ihn aus.
- **Reports:** [Review](../../../reviews/2026-10-30-2bd1a49-slice-sql-batch-dispatch.md) gegen den Merge `2bd1a49` (0 HIGH, 5 MEDIUM, 2 LOW, 3 INFO) und [Verifikation](../../../reviews/2026-10-30-ca83322-verifikation-slice-sql-batch-dispatch.md) am Stand `ca83322`, die die Fix-Runde `aeab8a9` nachverifiziert. Die Fix-Runde hat keinen eigenen Nach-Review; die Verifikation ersetzt ihn nicht. Der Verifier fuhr `make gates` mit gecachter Stage `test` (nur zusammen mit dem frischen Volllauf ein Beleg); `make gates` lief in dieser Closure vor jedem Inhalts-Commit und nach dem Move. Ein Validator-Lauf entfällt: der Slice liefert keinen Betriebsweg, `Main` verdrahtet kein Backend.

**Zuordnung gemessen | Handvektor | Selbstkonsistenz | Festlegung | nicht gemessen je Aussage.** *Gemessen* heißt beobachtet in einem Lauf dieses Repos gegen das Test-Backend (`ScriptedBackend`, eine Fixture des Backend-Ports) und gegen den Sitzungsrahmen; **gegen den Referenzclient und gegen einen echten SQL Server wurde in diesem Slice nichts gemessen.** *Handvektor* heißt ein von Hand gezählter Vektor ohne externe Quelle, unabhängig vom Codec gebaut. *Selbstkonsistenz* heißt Code gegen Code. *Festlegung* heißt Setzung ohne Messung.

| Stärke | Aussage | Beleg im Repo |
|---|---|---|
| gemessen (gegen das Test-Backend) | Das Backend empfängt Sitzungs-ID, eine je Anfrage neue Anfrage-ID und den Batchtext unverändert (fünf Texte, darunter leer, `SET`, Unicode, Steuerzeichen), mit leerer Parameterliste und Frist `null` | `SqlBatchDispatchTest`; Mutanten M8 und M12 rot |
| gemessen | Eine ungültige Kodierung ergibt eine Fehlerantwort ohne Backend-Aufruf; ein SQL_BATCH vor `READY` wird nicht beantwortet | `DispatchFailureTest`, `HandshakeMatrixTest` |
| gemessen | Der Anfragezustand läuft von `READY` über `RUNNING` nach `STREAMING`; zwei Anfragen in Folge auf einer Sitzung werden beantwortet, die Sitzung ist dazwischen `READY` | `SqlBatchDispatchTest` (zwei Tests); M8 rot |
| gemessen | Ein Fehler des Backends vor jeder Zeile ergibt `TDS_UNSUPPORTED_SQL` (Class 16), ERROR und DONE mit Fehlerstatus, der Text des Backends steht nicht in der Antwort, ein Folgebatch gelingt | `DispatchFailureTest`; die Aussage „Verbindung bleibt offen“ ist Festlegung (unten) |
| gemessen | Sechs schließende Ausgänge (`ReturnStatus`, `OutputParameter`, Zeile ohne Ergebnis, fremder Code, Code des Servers, `TDS_TIMEOUT`) enden mit Fehlerantwort, geschlossenem Kanal, `CLOSED` und ungelesener Folgenachricht; Senke lehnt das erste oder zweite Token ab: keine Antwort, Kanal zu; Ausnahme aus `execute()` oder aus dem Strom: `TDS_INTERNAL_ERROR` (Class 20), Verbindung schließt | `DispatchFailureTest`, `DispatchClosingTest`, `RequestWiringTest` (Socket); M1, M11, M14, M15, K, K2, K3 rot |
| gemessen | Antwort und Schließen laufen unter der Sperre der Sitzung; ein Abbruch mitten in der Antwort schließt Kanal und Backend | `DispatchFailureTest`; M10 und M11 rot |
| gemessen (Zählung) | Die Matrix hat 126 Zellen: 32 zulässig, 6 ignoriert, 88 unzulässig; eine Anfragenachricht in `RUNNING` oder `STREAMING` ist unzulässig | `HandshakeMatrixTest`; der Review zählte unabhängig nach; M12 rot |
| Handvektor | Die Antwortbytes für ein Ergebnis (COLMETADATA, ROW, DONE), für das leere Ergebnis (`ResultEnd(0)` und `ResultEnd(null)`) und für die Fehlerantwort; das SQL_BATCH mit ALL_HEADERS und UTF-16 | `SqlBatchDispatchTest`, `DispatchSupport`; der Vektor ist nicht am echten Server gemessen |
| Selbstkonsistenz | ERROR-Meldung, Class und Verbindungsfolge je Fall kommen aus `ErrorMapper` der Vorgänger-Slices; der Dispatch wird gegen diesen Mapper geprüft, nicht gegen eine zweite Quelle | `DispatchFailureTest`; M15 rot |
| Festlegung | Die Antwort wird vollständig gepuffert (`BufferedResponse`), ohne Obergrenze und ohne Rückstau; ein Parserfehler ergibt `TDS_PROTOCOL_ERROR`; RPC, Transaction Manager und ATTENTION in `READY` bleiben unbeantwortet und schließen die Verbindung; der Zuschnitt der Zustände ist flach, `COMPLETED` ist ein Übergang und kein eigener Zustand der Aufzählung; die Verbindung bleibt nach einem Anfragefehler offen (Class 16, Setzung des Nutzers); der Text des Backends erreicht den Client nicht | KDoc von `RequestLoop`, `BufferedResponse`, `HandshakeMatrix`, `ConnectionState`; SPEC-001 |
| nicht gemessen | Tokenfolge und DONE-Statusbits am echten SQL Server und am Referenzclient; die Bestätigung eines ATTENTION; Cancel, Timeout und Frist; Disconnect der Gegenstelle während einer Anfrage; ein großes Ergebnis gegen einen begrenzten Heap; Mehrfach-Ergebnisse, Parameter und RPC; ob ein echtes Backend-Ereignis den Encoder ein Token ablehnen lässt; die Mutation K5 | Register und Träger in der Tabelle unten |

**Offene Punkte der Closure mit Träger** (gegen den Inhalt der Dateien geprüft):

| Offener Punkt | Träger | Stand |
|---|---|---|
| Disconnect der Gegenstelle in `RUNNING` und `STREAMING` | [slice-abbruch-ressourcen-shutdown](../open/slice-abbruch-ressourcen-shutdown.md) (`open/`) | DoD-Punkt 1 (Disconnect in jedem Zustand, keine Schreibversuche auf die tote Verbindung); `slice-attention-cancel-timeout` schließt ihn aus (§1 dort) |
| `BufferedResponse` ersetzen, Heap-Beleg | [slice-backend-streaming-nachweis](../open/slice-backend-streaming-nachweis.md) (`open/`) | erster Liefer-Punkt und Risiko in §6 |
| ATTENTION in jedem Anfragezustand und in `IDLE`, Wettlauf DONE gegen ATTENTION | [slice-attention-cancel-timeout](../open/slice-attention-cancel-timeout.md) (`open/`) | DoD-Punkt 2 |
| Mehrfach-Ergebnisse in einer Antwort | [slice-antwort-mehrteilig-output](../open/slice-antwort-mehrteilig-output.md) (`open/`) | Ziel des Slice |
| Durchsetzung der Frist | [slice-adr-backend-grenzen](../in-progress/slice-adr-backend-grenzen.md) (`open/`), S3 | Entscheidung offen |
| Zuschnitt der Zustandsaufzählung: [ADR-0004](../../adr/0004-session-zustandsautomat.md) führt ihn offen, SPEC-001 legt „flach“ fest | kein Träger-Slice; **Folge-ADR nötig**, Entscheidung beim Architect und beim Nutzer, nicht in dieser Closure; der Re-Evaluierungs-Trigger der ADR (neue Nachrichtentypen, Schreibwettlauf, Messung gegen die Setzung bei unzulässigen Übergängen) deckt den Zuschnitt nicht | Register `BEO-TDS/offene-punkte-ohne-traeger` |
| Zwei KDoc-Sätze tragen mehr als der Beleg (`BufferedResponse`, `sinkAround`) | kein Träger; Hinweis an den Architect, die KDoc zu korrigieren (kein Code in dieser Closure) | Register `BEO-TDS/aussagen-ueber-messung-hinaus`, `BEO-TDS/test-naht-im-produktivcode` |
| Fuzz, Fragmentierung und Last in einer angemeldeten Sitzung mit SQL_BATCH | kein Träger; dieser Slice plante keinen Fuzz | Register `BEO-TDS/fuzz-nach-verdrahtung-ohne-traeger` (kein neues Auftreten) |

- **Gegenstand:** entfällt (der Slice lieferte).
- **Steering-Loop-Eintrag:** Kein `liegt in`: mit diesem Slice ist keine Regel, kein Sensor und keine Spec-Zeile an einem Zielort verkörpert worden; gezählt wird, nicht verkörpert. Fünf Feststellungen. (1) **Die Träger-Zeile eines Risikos wird gegen den Inhalt der Träger-Datei geprüft, auch in funktionalen Slices:** Plan und Fix-Runde nannten für den Disconnect `slice-attention-cancel-timeout`, dessen Plan ihn ausschließt (Verifikation V-1); die Commit-Nachricht behauptete eine Änderung, die der Diff nicht enthielt. Es ist das zweite Auftreten nach `slice-qa-stabilitaet` (V-1). Register: `BEO-TDS/offene-punkte-ohne-traeger` (jetzt 13×, Stand bleibt verkörpert); die verkörperte Regel in `.claude/commands/implement-slice.md` Schritt 24 gilt nur für den Entscheidungs-Slice, ob sie auf alle Slices ausgedehnt wird, ist eine Frage an den Nutzer und hier nicht entschieden. (2) **Eine Test-Naht im Produktivcode** (`RequestLoop.sinkAround`) entsteht, wenn ein Ausgang nur über ein Token erreichbar ist, das der Erzeuger nie bildet; sie beweist die Reaktion, nicht die Auslösung. Neue Beobachtung `BEO-TDS/test-naht-im-produktivcode`, 1×. (3) **Ausgänge nur als reine Funktion geprüft:** der Review-Mutant K überlebte, bis die Naht ihn fahrbar machte; K5 überlebt weiter. Register: `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test` (jetzt 5×, über der Schwelle, nicht verkörpert; der Sensor war der Review per Mutation). (4) **Aussagen über den Beleg hinaus trotz verkörperter Regel:** Zielbild im Indikativ in SPEC-001 (F-1, F-2, V-2), Abschnittsnummer ohne Quelle (F-5), zwei KDoc-Sätze (V-3, `sinkAround`); `BEO-TDS/aussagen-ueber-messung-hinaus` (jetzt 27×, verkörpert), keine Schärfung. (5) **Benannte Spec-Lücke:** SPEC-001 führt ATTENTION in jedem Anfragezustand und in `IDLE` als offen (Zielbild, Träger `slice-attention-cancel-timeout`), und der Zuschnitt der Aufzählung steht in der Spec „flach“, in [ADR-0004](../../adr/0004-session-zustandsautomat.md) „offen“ (Folge-ADR nötig, siehe Tabelle). Keine unverkörperte Beobachtung erreicht mit diesem Slice 3× neu; `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test` (5×) und `BEO-TDS/strukturtest-ohne-variation-der-eingabe` (5×) stehen über der Schwelle, nicht verkörpert. Einzelfälle ohne Klasse im Register: F-6 (Kommentar beschrieb abwesenden Spec-Text, §3.7) und F-7 (Sicht nur zur Hälfte nachgeführt), beide behoben.
- **Beobachtungs-Register (`../observations/`):** `evidence/slice-sql-batch-dispatch.md` ergänzt in `BEO-TDS/aussagen-ueber-messung-hinaus` (Review F-1, F-2, F-5; Verifikation V-2, V-3), `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test` (Review F-4; Verifikation V-3), `BEO-TDS/messung-am-echten-sql-server-fehlt` (Tokenfolge, DONE-Bits, Schließen bei ATTENTION), `BEO-TDS/offene-punkte-ohne-traeger` (Verifikation V-1, V-4) und `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` (zwei unverlinkte Kennungen im ersten `make docs-check`-Lauf des Verifikationsberichts). Eine neue Beobachtung mit dieser Evidence-Datei: `BEO-TDS/test-naht-im-produktivcode`. Kein Auftreten: die übrigen. Die Zähler folgen aus den Dateien.
- **Risiken aus §6:** alle zehn tragen genau einen Ausgang (siehe §6): zwei entfallen, acht bleiben weiter offen (vier mit Träger-Slice, vier im Register).
- **Drei Paarungen:** Anker: kein Eintrag trägt `liegt in`, die Paarung entfällt. Folge-Slice: `slice-abbruch-ressourcen-shutdown`, `slice-backend-streaming-nachweis`, `slice-attention-cancel-timeout`, `slice-antwort-mehrteilig-output` und `slice-adr-backend-grenzen` existieren in `open/` und führen die Punkte in ihrem Inhalt (gegen die Dateien geprüft; der Verweis in `slice-backend-streaming-nachweis` kam mit dieser Closure). Register: eine Evidence-Datei dieses Slice in fünf bestehenden Klassen und eine neue Beobachtung.
- **Für die Welle-Closure von `welle-anfrageverarbeitung`:** die Welle ist mit diesem Slice nicht vollständig; `slice-rpc-executesql-parameter`, `slice-antwort-mehrteilig-output` und `slice-rpc-prepared-handles` liegen in `open/`. Die Zuordnung der Welle führt die Welle-Closure zusammen.

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung — dort die **zwei vorgelagerten
Schritte** (sie stehen in jedem Slice-Plan, unabhängig von Modus und
Slice-Typ) und die **vier Pflichtkriterien** (Konventionen-Dichte ·
Phase-Reife · Evidenz-/Diskrepanz-Risiko · Reconciliation-Aufwand), vier und
nicht mehr.

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`). Das Package `protocol` (Dispatch, Antwortabbildung) ist ein Pfad-Cluster (Achse 3), trägt aber keine eigene Strukturregel (Achse 1) und keine eigene Inventur-Linie (Achse 2): eine von drei Achsen, unter der Schwelle. Keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, acht Beobachtungen, Stand des gemergten `main`); alle tragen die Sub-Area `*`. Treffer mit Zähler-Stand (Zahl der Dateien unter `evidence/`):

- `BEO-TDS/aussagen-ueber-messung-hinaus` — 7×, verkörpert (`AGENTS.md` §3.8, Anker `seit welle-protokollcodec`). Treffer: Die Regel gilt für diesen Slice; die Zuordnung *Test | Festlegung* (§2) und die Messgrenze im KDoc setzen sie um.
- `BEO-TDS/messung-am-echten-sql-server-fehlt` — 3×, geplant (Träger `slice-qa-reproduzierbarkeit`, Vergleichslauf D). Treffer: Tokenfolge und DONE-Statusbits der Antwort auf einen Batch. Die Schwelle ist erreicht und der Träger benannt; ein weiterer Beleg aus der Closure dieses Slice hebt die Beobachtung nicht in einen eigenen Slice, er wird als Beleg ergänzt.
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — 1×, offen. Treffer: `ADR-*`- und `TDS-*`-Kennungen stehen in diesem Plan als Link; `make docs-check` belegt es.
- Ohne Treffer: `BEO-TDS/adapter-festlegungen-ohne-slice`, `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut`, `BEO-TDS/betreiberanforderung-benutzerverwaltung-fehlt`, `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`, `BEO-TDS/vergleichslauf-d-ungeklaert`.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
