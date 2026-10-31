# Slice slice-fehler-zustandsuebergang: Zustandsübergänge nach Fehler: Verbindung offen, geschlossen nach ERROR und DONE oder ohne Antwort geschlossen

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-fehler-abbruch

**Bezug:** [TDS-FA-06](../../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände), [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [ADR-0004](../../adr/0004-session-zustandsautomat.md), [ADR-0007](../../adr/0007-fehler-und-attention.md)

**Berührte Spec-Stellen:** SPEC-001 · SPEC-011 · SPEC-029 · SPEC-031 · SPEC-048 · SPEC-049 · ARC-015

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

**Ziel:** Der Sitzungsrahmen setzt die Verbindung nach einem Fehler gemäß der Folge des Mappings fort — offen bei Class ≤ 19 mit vollständigem ERROR und DONE, geschlossen nach ERROR und DONE bei Class ≥ 20, `TDS_INTERNAL_ERROR` und `TDS_SHUTDOWN`, ohne ERROR und DONE geschlossen bei nicht abschließbarem Strom —, und die unmittelbar folgende Anfrage erhält nur ihre eigene Antwort.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Die Zuordnung der Codes zu Class und Folge — `slice-fehler-mapping`; dieser Slice führt die Folge aus, er legt sie nicht fest.
- ATTENTION, Cancel und Timeout — `slice-attention-cancel-timeout`; der Zustand `CANCELLING` entsteht dort.
- Dispatch, Batch-Zerlegung und RPC — Welle `welle-anfrageverarbeitung` (andere Planung). Dieser Slice setzt den Anfragezustand `IDLE → RUNNING → STREAMING → COMPLETED` voraus, er baut ihn nicht (Abstimmungsbedarf, §6).
- Port, Flow und Typabbildung des Backends — Welle `welle-backend-streaming`; dieser Slice konsumiert nur `Failure` und das Stromende.
- Gate über das Gesamtsystem mit dem Referenzclient (Fehler nach bereits gesendeten Zeilen am .NET-Client) — `slice-qa-synchronitaet` und `slice-qa-kompatibilitaet`; hier Rohbytes-Tests mit einem Fake-Backend.
- Wiederholungen oder Retry im Server — Option C der ADR, nicht gewählt.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [x] Anschluss des Mappings an den Sitzungsrahmen unter dem Mutex je Sitzung ([ADR-0004](../../adr/0004-session-zustandsautomat.md)): Fehler vor der ersten Zeile, nach Teilzeilen bei synchronem Strom (ERROR und DONE mit Fehlerstatus, genau ein Abschluss) und bei nicht abschließbarem Strom (Verbindung ohne ERROR und DONE geschlossen, kein zweiter Resultset-Anfang); ein Test je Folge der Mapping-Tabelle. ([TDS-FA-06](../../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände) Negative, `SPEC-031`) — als Beleg erfüllt: den Anschluss hat `slice-sql-batch-dispatch` geliefert, dieser Slice belegt je Folge (§7).
- [x] Rohbytes-Test über eine Verbindung: Nach einem Fehler der Class ≤ 19 erhält die unmittelbar folgende Anfrage nur ihre eigene Antwort, nach einer schließenden Folge wird keine weitere Anfrage angenommen und es entsteht kein zweites DONE. ([TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität), Protokollebene mit Fake-Backend) — „Rohbytes“ heißt die Nachrichten-Nutzlast hinter einem Kanal-Fake, nicht die Paketebene (§7).
- [x] Die vier offenen Zustandspunkte sind vom Nutzer entschieden und mit Test umgesetzt oder in der Closure als weiter offen mit Grund geführt: Antwort auf einen unzulässigen Übergang, Verhalten bei `RESETCONNECTION`, Sitzungszustand nach einem Fehler (offene Transaktion, R08/R09 der ADR-Messung), Kriterium für „Strom nicht mehr sicher abschließbar“. — zweite Alternative: keiner ist entschieden, alle in der Closure als weiter offen mit Grund geführt (§7); Träger der drei Punkte ohne eigenen Slice ist `BEO-TDS/zustandspunkte-nach-fehler-ohne-entscheidung`, `RESETCONNECTION` trägt `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`.
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
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/session/` (Package-Name ein Vorschlag) | entfällt | Die Folge führt `RequestLoop` in `protocol/dispatch/` aus (Ist-Zustand unten); `protocol/session/` bleibt unverändert |
| Anfragezustand (Response Sequencer, ARC-015) | entfällt | Fehlerpfad in `RUNNING` und `STREAMING` liegt im Bestand (`RequestLoop`, `ResponseSequencer`); keine Änderung |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/dispatch/DispatchFateTest.kt` | neu | Test je Folge, Folgeanfrage nach Fehler, kein zweites DONE ([TDS-FA-06](../../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände), [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität)) |
| `spec/spezifikation.md` | entfällt (bis zur Entscheidung) | `SPEC-001` und [TDS-FA-06.a](../../../../spec/spezifikation.md#tds-fa-06a--fehlerabbildung) ändern sich erst nach den Nutzerentscheidungen zu den vier offenen Punkten; sie stehen aus (§6), also keine Änderung in diesem Stand |

Ist-Zustand beim Eintritt (gemessen gegen `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/RequestLoop.kt`): Die Schleife liest `ErrorMapper.fateOf` des Ausgangs, sendet die Antwort mit dem Übergang `RequestFinished` unter der Sperre des Rahmens und lässt die Sitzung bei `OPEN` in `READY`, sonst in `CLOSED`; Produktivcode ist für die Folge nicht mehr zu ändern. Geliefert wird deshalb der Beleg: `src/test/kotlin/io/github/pt9912/tdswire/protocol/dispatch/DispatchFateTest.kt` (Folgeanfrage nach Fehler der Class 16, Folge offener Fehler, Class 20 nach Teilzeilen mit genau einem ERROR und einem DONE mit Fehlerstatus, nicht abschließbarer Strom ohne Antwort und ohne weiteres Token, jeweils [TDS-FA-06](../../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände) und [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität)). Die Tests der Folge vor dem Fehler (vor der ersten Zeile, schließende Ausgänge, Senke lehnt ab) liegen in `DispatchFailureTest.kt` und `DispatchClosingTest.kt` im selben Verzeichnis.

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf, inklusive einer Testdatei-Zeile mit der Akzeptanzkriterien-ID in `Begründung`.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): Der Start-Trigger der Welle `welle-fehler-abbruch` ist eingetreten, `slice-fehler-mapping` liegt in `done/`, `slice-sql-batch-dispatch` der Welle `welle-anfrageverarbeitung` mit dem Anfragezustand `IDLE → RUNNING → STREAMING → COMPLETED → IDLE` über einem Port-Strom liegt in `done/`, [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) steht im ADR-Index auf `Accepted`, und ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2. Schnittvorschlag: die vier offenen Zustandspunkte (DoD-Punkt 3) als eigener Slice.
- `in-progress` → `open` (blockiert — Carveout?): Die Welle `welle-anfrageverarbeitung` liefert keinen Anfragezustand, an dem der Fehlerpfad hängen kann, oder die Nutzerentscheidungen zu den offenen Punkten fehlen.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle Punkte der DoD in §2 erledigt, `make gates` grün (einschließlich `make test`) und die Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Wer `ErrorMapper.fateOf(outcome)` auswertet: `slice-sql-batch-dispatch` erzeugt die Antwort mit `ErrorMapper.sequencerMapping()` und liest die `ConnectionFate` aus dem `SequenceOutcome`; dieser Slice führt die Folge aus, die sie benennt (`OPEN`, `CLOSE_AFTER_REPLY`, `CLOSE_WITHOUT_REPLY`). Ohne diesen Leser bleibt die Zusage „der Ausgang trägt die Folge“ ohne Träger. — **Ausgang:** entfallen: `slice-sql-batch-dispatch` liegt in `done/`, `RequestLoop` liest `fateOf`; die Folge belegt `DispatchFateTest`
- Stand der Umsetzung der vier Zustandspunkte, ohne Entscheidung des Nutzers: unzulässiger Übergang schließt ohne Antwort (Setzung des Spike, `HandshakeMatrix`), `RESETCONNECTION` wertet der Automat nicht aus, der Sitzungszustand wird nach einem Fehler nicht angefasst (dass der Port keine Transaktion kennt, ist eine Bestandsaussage, die kein Test stützt: nicht belegt), „nicht abschließbar“ heißt hier: die Senke nimmt ein Token nicht an (Festlegung des Sequenzierers). Der Slice setzt keinen davon als entschieden; die Grundregel A für `TDS_UNSUPPORTED_TYPE`, `TDS_SIM_UNKNOWN_OBJECT` und `TDS_TIMEOUT` (Class 20, schließt) bleibt Festlegung, `DispatchFateTest` nutzt für die schließende Folge nur den Code, den ein Nutzer gesetzt hat (`TDS_INTERNAL_ERROR`, erreicht über einen fremden Code und über einen Reihenfolgeverstoß), für die offene Folge `TDS_UNSUPPORTED_SQL` und `TDS_BACKEND_ERROR` (Setzung) und `TDS_SIM_INVALID_VALUE` (Voreinstellung, nicht entschieden). — **Ausgang:** weiter offen: Entscheidung des Nutzers fehlt, keiner der vier Punkte ist entschieden; Träger der drei Punkte ohne eigenen Slice ist `BEO-TDS/zustandspunkte-nach-fehler-ohne-entscheidung`, Träger für `RESETCONNECTION` und Pooling ist `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`
- Statement- gegen batch-abbrechende Fehler (R04 gegen R05 bis R07 in [ADR-0007](../../adr/0007-fehler-und-attention.md); Träger seit der Closure von `slice-backend-sequenzierer` und `slice-fehler-mapping`): `Failure` trägt keine Klasse, der Sequenzierer beendet den Lauf bei jedem Fehler und schreibt für statement-abbrechende Fehler die Variante ohne `DONE_MORE`, das Mapping gibt die Verbindungsfolge nach Code. Ob ein statement-abbrechender Fehler den Rest des Batches laufen lassen muss und der Port die Unterscheidung liefern muss, ist nicht entschieden. Hier wirkt die Unterscheidung, weil dieser Slice die Folge nach dem Fehler ausführt. — **Ausgang:** weiter offen: Entscheidung des Nutzers fehlt; als offen benannt in der Spezifikation (§4 „Offen“ und die offenen Punkte des Backend-Vertrags), ein Port-Eingriff gehörte nach der Entscheidung zu `welle-backend-streaming`
- Einzelzuordnung der Codes ohne Gruppe (Träger seit der Closure von `slice-fehler-mapping`): `TDS_UNSUPPORTED_TYPE`, `TDS_SIM_UNKNOWN_OBJECT` und `TDS_TIMEOUT` stehen mit Class 20 und Folge „schließt“ in [§4 der Spezifikation](../../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder); die Folge folgt der Grundregel A, die Class 20 ist eine Festlegung des Mapping-Slice ohne Bestätigung des Nutzers. Der echte Server sendet 208 mit Class 16 (Messung R05 in [ADR-0007](../../adr/0007-fehler-und-attention.md)). Jede Änderung ändert die Folge, die dieser Slice ausführt: ein Typfehler schließt heute die Verbindung nach der Antwort. — **Ausgang:** weiter offen: Entscheidung des Nutzers fehlt; als „Einzelzuordnung offen“ benannt in der Tabelle in [§4 der Spezifikation](../../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder), danach Änderung an `ErrorCode` und der Tabelle
- Wer baut den Response Sequencer (ARC-015)? [TDS-FA-06](../../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände) und [TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe) sprechen vom Anfragezustand, die Wellen `welle-anfrageverarbeitung` und `welle-fehler-abbruch` schneiden ihn von zwei Seiten. Der Schnitt dieses Plans (Happy Path dort, Fehler- und Abbruchpfad hier) ist ein Vorschlag und mit den anderen Planern abzustimmen. — **Ausgang:** entfallen: `slice-backend-sequenzierer` und `slice-sql-batch-dispatch` liegen in `done/`; der Sequenzierer steht im Bestand, der Fehlerpfad hängt an ihm, und dieser Slice hat nur den Beleg gebaut
- Entscheidungsbedarf (Nutzer): Kriterium für „der Strom lässt sich nicht mehr sicher abschließen“ (`SPEC-031`, [ADR-0007](../../adr/0007-fehler-und-attention.md)) — die ADR nennt keines; das ist hier nicht entschieden. — **Ausgang:** weiter offen: Entscheidung des Nutzers fehlt; Träger: `BEO-TDS/zustandspunkte-nach-fehler-ohne-entscheidung`
- Entscheidungsbedarf (Nutzer): Sitzungszustand nach einem Fehler — am echten Server überlebt eine offene Transaktion einen Fehler ohne `XACT_ABORT` (R08/R09, ein Lauf je Zeile). Ob ein Backend dieses Repos Transaktionen kennt, hängt am Port-Vertrag. — **Ausgang:** weiter offen: Entscheidung des Nutzers fehlt; Träger: `BEO-TDS/zustandspunkte-nach-fehler-ohne-entscheidung`
- Entscheidungsbedarf (Nutzer): `RESETCONNECTION` (Pooling) und die Antwort auf einen unzulässigen Übergang (der ADR-Spike schloss ohne Antwort, eine Setzung des Spike). Pooling mit einer vom Server geschlossenen Verbindung ist nicht gemessen. — **Ausgang:** weiter offen: → `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`
- Die Messung stützt „offen nach Class ≤ 19“ am echten Server und „ERROR, DONE, Schließen“ am Testserver mit dem Referenzclient (T, S21), nicht an einem Server dieses Repos; TDS 8.0 ist nicht gemessen. — **Ausgang:** weiter offen: → `BEO-TDS/messung-am-echten-sql-server-fehlt`
- Mutex je Sitzung: Ein Schreiben unter der Sperre hält den Übergang bei langsamem Leser (Backpressure) auf; die Auswirkung auf dem echten Transport ist in [ADR-0004](../../adr/0004-session-zustandsautomat.md) nicht gemessen. — **Ausgang:** weiter offen: → `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`

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

- **Gegenstand:** entfällt nicht, der Slice lieferte den Beleg und keinen Produktivcode: die Folge nach Fehler führt `RequestLoop` aus (`ErrorMapper.fateOf`), seit `slice-sql-batch-dispatch`; dieser Slice belegt sie mit `DispatchFateTest` (vier Tests) und schärft die Pläne. Der Wortlaut „Anschluss des Mappings“ in DoD-Punkt 1 war breiter als die Lieferung (Verifikation, DoD A); Häkchen und Notiz sagen „als Beleg erfüllt“.
- **Was hat funktioniert:** Der Schnitt auf drei Liefer-Punkte trug, solange die Nutzerentscheidungen nicht Teil der Lieferung waren: Punkt 3 durfte in der Closure als weiter offen mit Grund stehen, und kein Punkt wurde als entschieden gesetzt. Der Beleg ist mutationsgeprüft: vier Eingriffe (M3, M3c, M4, M6) fallen nur an `DispatchFateTest`, vier weitere (M1, M2, Mstate, Mviol) zusätzlich an Bestandstests; die Verifikation fuhr alle acht einzeln, alle wurden erkannt.
- **Was ging anders als geplant:** (a) **Der Plan nannte Pfade und eine Spezifikations-Änderung, die der Slice nicht lieferte** (Review F-5): `protocol/session/` und der Anfragezustand blieben unverändert, die Spezifikation bis zur Entscheidung auch; die Tabelle in §3 führt es seit der Fix-Runde. (b) **Der Test des offenen Fehlers hatte kein Folgeereignis im Strom** (F-1): der Mutant M3c überlebte im Review und fällt seit der Fix-Runde `af212cb`. (c) **Die Variation des Eingabewegs war ein Pfad** (F-3), die Zustandszusicherung prüfte einen anderen Index als ihre Meldung sagte (F-2), und die Herkunftsstufe eines Testcodes stand nicht im Klassenkopf (F-4); behoben. (d) **Sechs Risiken in §6 standen auf „offen bis zur Closure“** (Verifikation V-3); jetzt tragen alle zehn einen Ausgang. (e) **Die Fix-Runde hat der Verifier nachverifiziert**, anders als die Restrunde von `slice-fehler-mapping`.
- **Reports:** [Review](../../../reviews/2026-10-30-ee0a6c7-slice-fehler-zustandsuebergang.md) (kein HIGH; F-1 MEDIUM, F-2 bis F-6 LOW, F-7 bis F-9 INFO) und [Verifikation](../../../reviews/2026-10-30-1194042-verifikation-slice-fehler-zustandsuebergang.md) (Stand `1194042`, Fix-Runde `af212cb`; kein HIGH; V-2 und V-3 MEDIUM, V-1 LOW, V-4 bis V-6 INFO). Stand der Befunde: F-1 bis F-6 behoben und von der Verifikation gemessen; F-7 bis F-9 bestätigt; V-1 benannt, nicht behoben (Register); V-2 und V-3 in dieser Closure bearbeitet; V-4 geprüft (siehe Tabelle unten); V-5 in DoD und Zuordnung benannt; V-6 siehe Gate. Ein Validator-Lauf entfällt: der Slice liefert keinen End-Nutzer-Wert, nur einen Beleg.
- **Gate:** Als Beleg zählt der Volllauf, nicht der gecachte `make gates` (V-6): die Verifikation fuhr `docker build --target test --no-cache-filter test` mit 507 grünen Tests auf einem Quellstand, an dem sich `src/main` seither nicht geändert hat. `make spec-abdeckung` in dieser Closure hat die Test-Stage frisch ausgeführt (der Gradle-Testlauf lief, kein Cache-Treffer); `DispatchFateTest` zählt in der Matrix als bestanden bei `SPEC-001` (+1), `SPEC-031` (+4) und `SPEC-048` (+1).

**Zuordnung Beleg | Aussage.** Fünf Stärken: *gemessen* heißt an einem echten Client oder Server beobachtet; *Handvektor* heißt von Hand abgeleitet; *Selbstkonsistenz* heißt Code gegen Code; *Festlegung* heißt Setzung des Nutzers oder des Slice ohne Messung; *nicht gemessen* nennt, was keine Aussage trägt. „Rohbytes“ heißt in diesem Slice die Nutzlast der TDS-Nachricht hinter einem Kanal-Fake, nicht die Paketebene.

| Stärke | Aussage | Beleg im Repo |
|---|---|---|
| gemessen | nichts: kein Test dieses Slice läuft gegen einen echten Server oder den Referenzclient | — (die Messungen aus [ADR-0007](../../adr/0007-fehler-und-attention.md), offen bis Class 19, schließt ab 20, sind Hintergrund, nicht Beleg dieses Slice) |
| Handvektor | Die Antwort der Folgeanfrage nach einem Fehler der Class 16 ist byte-gleich zu `COLMETADATA_N`, `row(7)`, `DONE_COUNT_1` und enthält kein ERROR; Nummer 50007, Class 20, State 1 des Falls Class 20 | `DispatchFateTest` (Test 1, Test 3); von Hand abgeleitet, ein Nachrechnen mit anderem Werkzeug liegt nicht vor |
| Selbstkonsistenz | Genau ein ERROR und ein DONE mit Fehlerstatus je Fehlerantwort; Tokenfolge `[ColMetadata, Row, Row, Done, Error, Done]` bei Class 20; Fehlerzahl `[1, 0, 1, 1, 0]` über fünf Anfragen; Zustandsliste `[READY, READY, READY]`; ein Leseaufruf nach schließender Folge; `offered == k` bei abgelehntem k-ten Token | `DispatchFateTest` (Test 1 bis 4), Prüfung mit dem Test-Dekoder; Mutanten: Review neun einzeln, Verifikation acht einzeln, alle erkannt (Mviol nur an der Zeitüberschreitung, Register `BEO-TDS/mutant-faellt-nur-an-der-zeitueberschreitung`) |
| Festlegung | Class 16 lässt offen, Class 20 schließt (Nutzer-Setzung für `TDS_INTERNAL_ERROR`); `TDS_UNSUPPORTED_SQL` und `TDS_BACKEND_ERROR` offen (Setzung); `TDS_SIM_INVALID_VALUE` offen (Voreinstellung, nicht entschieden); „nicht abschließbar“ heißt: die Senke nimmt ein Token nicht an (Festlegung des Sequenzierers); unzulässiger Übergang schließt ohne Antwort (Setzung des Spike) | [§4 der Spezifikation](../../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder), KDoc von `ErrorCode`, `HandshakeMatrix`; Register `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` für die Lesarten ohne Bestätigung |
| nicht gemessen | Annahme der Fehlerfolgen durch den Referenzclient; Paketierung, Socket und `ConnectionWiring` mit dem Fehlerpfad; TDS 8.0 und Pooling; Sitzungszustand und Transaktionen nach einem Fehler; Wirkung des Mutex bei langsamem Leser auf dem echten Transport; `TDS_UNSUPPORTED_RPC`, `TDS_SIM_UNKNOWN_OBJECT` und `TDS_SHUTDOWN` über eine Verbindung | Register `BEO-TDS/messung-am-echten-sql-server-fehlt`, `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`, `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`, `BEO-TDS/zustandspunkte-nach-fehler-ohne-entscheidung` |

**Rückführung nach §4 — angewandt oder nicht.** Die Bedingung der Rückführung `in-progress` → `open` nennt „die Nutzerentscheidungen zu den offenen Punkten fehlen“; wörtlich ist sie erfüllt, keine der vier Entscheidungen liegt vor. **Sie wurde nicht angewandt.** Grund: Der Blocker gilt für die Änderung der Spezifikation (§3 streicht sie), nicht für den Beleg; der gelieferte Beleg (DoD-Punkte 1 und 2) hängt an keiner Entscheidung, ist reviewt und verifiziert, und DoD-Punkt 3 lässt „weiter offen mit Grund“ ausdrücklich zu. Ein Slice mit Beleg-Wert in `open/` wäre falscher als ein Slice in `done/` mit benanntem offenem Rest. Die Rückführung `in-progress` → `next` (mehr als drei Liefer-Punkte) greift nicht: es sind drei. **Den Schnittvorschlag aus §4 (die vier Punkte als eigener Slice) habe ich nicht angelegt:** er wäre erst mit einer Entscheidung des Nutzers sinnvoll, denn ohne sie hat er keinen Gegenstand, der sich bauen lässt, und läge als Slice, der nicht starten kann, in `open/`; die Punkte sind ungleichartig (der unzulässige Übergang gehört zum Handshake und zu ATTENTION, der Sitzungszustand zum Port-Vertrag, das Kriterium zum Sequenzierer), ein gemeinsamer Slice schneidet quer. Bis zur Entscheidung trägt das Register. Ob ein Entscheidungs-Slice entstehen soll, ist die Frage an den Nutzer in der Übergabe dieser Closure.

**Offene Punkte der Closure mit Träger** (gegen den Inhalt der Dateien geprüft; „Nutzerentscheidung“ ist kein Träger):

| Offener Punkt | Träger | Stand |
|---|---|---|
| Sitzungszustand nach Fehler, Kriterium „nicht abschließbar“, Antwort auf unzulässigen Übergang | Register `BEO-TDS/zustandspunkte-nach-fehler-ohne-entscheidung` (neu, Stand *offen*) | kein Slice trägt sie: `slice-attention-cancel-timeout` schließt die Antwort auf unzulässige Übergänge aus (§1 „Ausdrücklich NICHT“) und setzt nur eine Entscheidung um, `slice-abbruch-ressourcen-shutdown` führt nur das Schließen nach `TDS_SHUTDOWN` |
| `RESETCONNECTION` und Pooling | Register `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` (*geplant*, `slice-qa-kompatibilitaet`) | Evidence dieses Slice ergänzt |
| Statement- gegen batch-abbrechende Fehler | benannte Spec-Lücke in der Spezifikation (§4 „Offen“, offene Punkte des Backend-Vertrags) | Entscheidung des Nutzers fehlt; kein Slice, `slice-adr-backend-grenzen` nimmt ihn laut seinem §1 aus (Stand dieser Closure) |
| Einzelzuordnung von `TDS_UNSUPPORTED_TYPE`, `TDS_SIM_UNKNOWN_OBJECT`, `TDS_TIMEOUT`, Class von 208 | benannte Spec-Lücke („Einzelzuordnung offen“ in der Tabelle in §4 der Spezifikation); `TDS_TIMEOUT` zusätzlich `slice-attention-cancel-timeout` §6 | Entscheidung des Nutzers fehlt |
| Mutex je Sitzung bei langsamem Leser auf dem echten Transport | Register `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` | Evidence dieses Slice ergänzt |
| `RequestLoop` lässt RPC unbeantwortet und schließt, die Spezifikation (`SPEC-013`) verlangt für `TDS_UNSUPPORTED_RPC` ERROR und DONE mit Fehlerstatus (Verifikation V-4) | `slice-rpc-executesql-parameter` (`open/`, Welle `welle-anfrageverarbeitung`) | DoD-Punkt 3 des Slice verlangt für unbekannte RPC einen definierten Fehler mit offener Verbindung und beantwortetem Folge-RPC; sein §6 trägt den Ist-Zustand seit dieser Closure |
| Der Beleg für den Reihenfolgeverstoß fällt nur an der Zeitüberschreitung (V-1) | Register `BEO-TDS/mutant-faellt-nur-an-der-zeitueberschreitung` | nicht behoben, kein Test geändert |
| Drei Tests tragen `@spec TDS-QA-07`; die Matrix führt es als unbekannte Festlegung | Register `BEO-TDS/spec-tag-nennt-lastenheft-kennung` | nicht behoben, kein Test geändert |

- **Steering-Loop-Eintrag:** Kein `liegt in`: mit diesem Slice ist keine Regel, kein Sensor und keine Gate-Zeile verkörpert worden; gezählt wird, nicht verkörpert. Vier Feststellungen. (1) **Gelernt: Die verkörperte Träger-Regel (Schritt 24, Absatz „Entscheidungs-Slice“) deckt nur den Slice, der die ADR entscheidet; der letzte Slice, der einen offenen ADR-Punkt mit „weiter offen“ nach `done/` bringt, ist dort nicht gemeint, und mit seinem Move hätte keine Datei die drei Punkte mehr getragen** (Verifikation V-2). Klasse `BEO-TDS/offene-punkte-ohne-traeger` (14×, schon *verkörpert*, mit Anker `seit welle-entscheidungen-grundlage`). Kandidat für eine Schärfung, keine Setzung: den Absatz auf jeden Slice erweitern, der einen ADR-Punkt weiter offen schließt; das ändert einen Workflow-Command und ist hier nicht getan. Der Träger der drei Punkte ist das neue Register-Verzeichnis `BEO-TDS/zustandspunkte-nach-fehler-ohne-entscheidung`. (2) **Schwellen erreicht, nicht verkörpert, wie bisher:** `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test` hat mit diesem Slice sechs Belege (F-1: M3c überlebte, obwohl der Implementer vier Mutanten gefahren hatte), `BEO-TDS/strukturtest-ohne-variation-der-eingabe` sechs (F-3); keine Regel ist geschrieben, die `state.md` bleiben `offen`, der Lese-Schritt liegt bei der Welle-Closure von `welle-fehler-abbruch`. Kandidat, keine Setzung: je behauptete Variation und je Zweig mit Wirkung vor dem Handoff eine Mutation fahren, die genau diesen Pfad ändert. `BEO-TDS/aussagen-ueber-messung-hinaus` (28×) bleibt *verkörpert*; die Aussagen dieses Slice waren breiter als der Beleg (F-2 bis F-5, V-3, V-5) trotz der Regel, keine Schärfung. (3) **Keine dritte Evidenz zu `BEO-TDS/dod-haengt-an-offenem-slice-ohne-rueckfuehrung`** (bleibt 2×): jene Klasse ist ein DoD-Punkt, der Eingänge eines Slice in `open/` braucht, die §4 nicht nennt; hier hängt Punkt 3 an einer Nutzerentscheidung, nicht an einem Slice, und §4 nennt die Rückführung vorab. Der Fund dieses Slice (der Träger endet mit der Datei) gehört zu `BEO-TDS/offene-punkte-ohne-traeger`; der Widerspruch zwischen Rückführungsbedingung und der DoD-Alternative „weiter offen“ ist oben entschieden und kommt einmal vor. (4) **Neue Beobachtungen, je einmal:** `BEO-TDS/mutant-faellt-nur-an-der-zeitueberschreitung` (V-1), `BEO-TDS/spec-tag-nennt-lastenheft-kennung` (beim Erzeugen der Matrix gefunden, weder Review noch Verifikation sahen es), `BEO-TDS/zustandspunkte-nach-fehler-ohne-entscheidung`. **Benannte Spec-Lücke, nicht verkörpert:** die drei Zustandspunkte stehen in keiner versionierten Spezifikation als offen (anders als Einzelzuordnung und statement- gegen batch-abbrechend); diese Closure ändert die Spezifikation nicht, weil ein Architect sie parallel schreibt; der Nachzug (ein Satz je Punkt unter §4 „Offen“) ist offen und in der Übergabe als Frage genannt.
- **Beobachtungs-Register (`../observations/`):** `evidence/slice-fehler-zustandsuebergang.md` ergänzt in `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test` (F-1), `BEO-TDS/strukturtest-ohne-variation-der-eingabe` (F-3), `BEO-TDS/aussagen-ueber-messung-hinaus` (F-2 bis F-5, V-3, V-5), `BEO-TDS/offene-punkte-ohne-traeger` (V-2), `BEO-TDS/messung-am-echten-sql-server-fehlt`, `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` und `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`; neu angelegt `BEO-TDS/zustandspunkte-nach-fehler-ohne-entscheidung`, `BEO-TDS/mutant-faellt-nur-an-der-zeitueberschreitung`, `BEO-TDS/spec-tag-nennt-lastenheft-kennung`. Kein neues Auftreten: `BEO-TDS/dod-haengt-an-offenem-slice-ohne-rueckfuehrung` (siehe oben), `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` und die übrigen. Die Zähler folgen aus den Dateien.
- **Risiken aus §6:** alle zehn tragen genau einen Ausgang (siehe §6): zwei entfallen (Leser von `fateOf`, Bau des Response Sequencers), acht weiter offen mit Träger: Stand der Zustandspunkte, Kriterium „nicht abschließbar“, Sitzungszustand und `RESETCONNECTION` im Register, statement- gegen batch-abbrechend und Einzelzuordnung in der Spezifikation, Messung am echten Server und Mutex im Register. Kein Risiko ist eingetreten, das einen Carveout verlangt.
- **Drei Paarungen:** Anker: kein Eintrag trägt `liegt in`, die Paarung entfällt. Folge-Slice: kein Folge-Slice angelegt (die Entscheidungen fehlen); die Träger sind Dateien: das Register für die drei Zustandspunkte, die Spezifikation für Einzelzuordnung und statement- gegen batch-abbrechend, `slice-rpc-executesql-parameter` §6 für den RPC-Fehler (gegen den Inhalt geprüft). Register: zehn Evidence-Dateien, siehe oben.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`). Die berührten Packages (`protocol`, `transport`, `runtime`) sind Pfad-Cluster (Achse 3), tragen aber keine eigene Strukturregel (Achse 1) und keine eigene Inventur-Linie (Achse 2): eine von drei Achsen, unter der Schwelle. Keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, acht Beobachtungen, Stand des gemergten `main`); alle tragen die Sub-Area `*`. Treffer mit Zähler-Stand (Zahl der Dateien unter `evidence/`):

- `BEO-TDS/messung-am-echten-sql-server-fehlt` — 3×, Stand *geplant*. Treffer: Verbindungsfolge nach Fehler ist nur für Class ≤ 19 und 20/25 am echten Server gemessen (§6).
- `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` — 3×, Stand *geplant* (Träger `slice-qa-kompatibilitaet`). Treffer: `RESETCONNECTION` und vom Server geschlossene Verbindung bei Pooling (§6).
- `BEO-TDS/aussagen-ueber-messung-hinaus` — 7×, Stand *verkörpert*. Treffer: Tests belegen den Rohbytes-Client mit Fake-Backend, nicht den Referenzclient.
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — 1×, offen. Treffer: Kennungen stehen als Link.
- Ohne Treffer: `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut`, `BEO-TDS/adapter-festlegungen-ohne-slice`, `BEO-TDS/betreiberanforderung-benutzerverwaltung-fehlt`, `BEO-TDS/vergleichslauf-d-ungeklaert`.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
