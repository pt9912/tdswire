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

**Verantwortlich:** — bis zur Priorisierung.

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

- [ ] **Annahme und Dispatch** ([TDS-FA-03.a](../../../../spec/spezifikation.md#tds-fa-03a--sql_batch), SPEC-001, ARC-005): In `READY` löst ein SQL_BATCH den Anfragezustand `IDLE → RUNNING` aus; ALL_HEADERS wird gelesen, der Batchtext UTF-16 dekodiert, ungültige Kodierung ergibt ein definiertes Fehlerergebnis ohne Absturz; das Test-Backend empfängt Sitzungs-ID, neue Anfrage-ID und den Text unverändert, mit leerer Parameterliste. Ein SQL_BATCH außerhalb `READY` bleibt ein unzulässiger Übergang im Automaten des Handshake-Slice (Test, kein neues Verhalten).
- [ ] **Ergebnis und leeres Ergebnis** ([TDS-FA-03](../../../../spec/lastenheft.md#tds-fa-03--einfache-sql-batches) Happy Path und Boundary, SPEC-031): Ein Test-Backend mit einem Ergebnis (Spalten, Zeilen) und eins mit einem Ergebnis ohne Zeilen führen je zu einer Antwort, die der Sequenzierer vollständig abschließt; beim leeren Ergebnis stehen Spaltenmetadaten und ein ordnungsgemäßer Abschluss in der Antwort. Der Anfragezustand kehrt nach `IDLE` zurück, und ein zweites Batch auf derselben Sitzung wird ebenso beantwortet (zwei Anfragen in Folge, eine Sitzung).
- [ ] **Nicht unterstütztes SQL** ([TDS-FA-03](../../../../spec/lastenheft.md#tds-fa-03--einfache-sql-batches) Negative): Meldet das Test-Backend einen Fehler (Failure-Ereignis vor jeder Zeile), erhält der Client den definierten SQL-Fehler über den Fehlerpfad der Welle `welle-fehler-abbruch`, und die Sitzung nimmt das nächste Batch an, soweit [ADR-0007](../../adr/0007-fehler-und-attention.md) die Verbindung offen lässt (Anfragefehler-Gruppe, Class 16, Setzung des Nutzers, nicht gemessen); der Test zeigt Fehlerantwort und anschließend ein erfolgreiches Folgebatch.
- Verdrahtung des Fehler-Mappings (Träger für `ErrorMapper`, der in `src/main` bis dahin keinen Leser hat): Der Dispatch erzeugt die Anfrageantwort über `ResponseSequencer` mit `ErrorMapper.sequencerMapping()`; die Verbindungsfolge liest er über `ErrorMapper.fateOf(outcome)` aus dem `SequenceOutcome` des Laufs. Ein Test zeigt, dass ein Ausgang, der die Verbindung schließt (zum Beispiel ein Typfehler), nicht als offene Verbindung endet. Gehört zum Punkt „Nicht unterstütztes SQL“ und zählt nicht als eigener Liefer-Punkt.

Nachweis-Pflichten für alle Punkte:

- [ ] Jeder Test trägt im KDoc über der Testfunktion den Block-Tag `@spec SPEC-<NNN>` (Konvention nach `slice-qa-spec-abdeckung`); die Tests laufen in `make test` und sind grün.
- [ ] Jede Aussage steht an einem Test oder ist als Festlegung gekennzeichnet (KDoc des Elements). Die Zuordnung *Test | Festlegung* je Aussage steht in der Closure-Notiz. Die Tests laufen gegen ein Test-Backend, das den Backend-Port implementiert, nicht gegen einen echten SQL Server und nicht gegen den Referenzclient; Aussagen über deren Verhalten trägt dieser Slice nicht.
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo mit Wellen von der Welle-Closure geprüft.

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

- Der Antwort-Sequenzierer (ARC-015) ist in keiner Welle eindeutig zugeordnet: [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) führt die `DONE_MORE`-Vorschau, [ADR-0007](../../adr/0007-fehler-und-attention.md) und [ADR-0004](../../adr/0004-session-zustandsautomat.md) den Anfragezustand und ATTENTION. Dieser Plan nimmt den Sequenzierer als Schnittstelle von den Nachbarwellen; passt sie nicht, entsteht Umbau hier. — **Ausgang:** offen bis zur Closure.
- Das Zusammenspiel des Anfragezustands mit dem Zustandsautomaten ist in [ADR-0004](../../adr/0004-session-zustandsautomat.md) nicht festgelegt (Zuschnitt: eine Aufzählung oder getrennt nach Verbindungs- und Anfragezustand; [ADR-0004](../../adr/0004-session-zustandsautomat.md) ist `Accepted` und führt den Zuschnitt offen, `slice-handshake-login` trägt die Entscheidung). Der Slice startet erst, wenn der Zuschnitt entschieden ist. — **Ausgang:** offen bis zur Closure.
- Die Reihenfolge der Tokens und die DONE-Statusbits eines echten SQL Servers sind nicht gemessen (`BEO-TDS/messung-am-echten-sql-server-fehlt`); die Tests belegen die Festlegung aus MS-TDS und dem Spike-Testserver. — **Ausgang:** offen bis zur Closure.
- Ein Batch mit mehreren Anweisungen, das der Client als mehrere Ergebnisse liest, ist Gegenstand des Slice `slice-antwort-mehrteilig-output`; schneidet die Closure des Backend-Slices die Ereignisse anders, ist dort nachzuziehen. — **Ausgang:** offen bis zur Closure.
- Die Frist `deadlineEpochMillis` bleibt `null`; wer sie durchsetzt, ist offen ([ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)), Träger der Entscheidung ist `slice-adr-backend-grenzen` (S3). Ein Test-Backend ohne Frist sagt nichts über das Verhalten bei Ablauf. — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`). Das Package `protocol` (Dispatch, Antwortabbildung) ist ein Pfad-Cluster (Achse 3), trägt aber keine eigene Strukturregel (Achse 1) und keine eigene Inventur-Linie (Achse 2): eine von drei Achsen, unter der Schwelle. Keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, acht Beobachtungen, Stand des gemergten `main`); alle tragen die Sub-Area `*`. Treffer mit Zähler-Stand (Zahl der Dateien unter `evidence/`):

- `BEO-TDS/aussagen-ueber-messung-hinaus` — 7×, verkörpert (`AGENTS.md` §3.8, Anker `seit welle-protokollcodec`). Treffer: Die Regel gilt für diesen Slice; die Zuordnung *Test | Festlegung* (§2) und die Messgrenze im KDoc setzen sie um.
- `BEO-TDS/messung-am-echten-sql-server-fehlt` — 3×, geplant (Träger `slice-qa-reproduzierbarkeit`, Vergleichslauf D). Treffer: Tokenfolge und DONE-Statusbits der Antwort auf einen Batch. Die Schwelle ist erreicht und der Träger benannt; ein weiterer Beleg aus der Closure dieses Slice hebt die Beobachtung nicht in einen eigenen Slice, er wird als Beleg ergänzt.
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — 1×, offen. Treffer: `ADR-*`- und `TDS-*`-Kennungen stehen in diesem Plan als Link; `make docs-check` belegt es.
- Ohne Treffer: `BEO-TDS/adapter-festlegungen-ohne-slice`, `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut`, `BEO-TDS/betreiberanforderung-benutzerverwaltung-fehlt`, `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`, `BEO-TDS/vergleichslauf-d-ungeklaert`.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
