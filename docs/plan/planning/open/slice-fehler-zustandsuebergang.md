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

**Ziel:** Der Sitzungsrahmen setzt die Verbindung nach einem Fehler gemäß der Folge des Mappings fort — offen bei Class ≤ 19 mit vollständigem ERROR und DONE, geschlossen nach ERROR und DONE bei Class ≥ 20, `TDS_INTERNAL_ERROR` und `TDS_SHUTDOWN`, ohne ERROR und DONE geschlossen bei nicht abschließbarem Strom —, und die unmittelbar folgende Anfrage erhält nur ihre eigene Antwort.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Die Zuordnung der Codes zu Class und Folge — `slice-fehler-mapping`; dieser Slice führt die Folge aus, er legt sie nicht fest.
- ATTENTION, Cancel und Timeout — `slice-attention-cancel-timeout`; der Zustand `CANCELLING` entsteht dort.
- Dispatch, Batch-Zerlegung und RPC — Welle `welle-anfrageverarbeitung` (andere Planung, noch keine Datei). Dieser Slice setzt den Anfragezustand `IDLE → RUNNING → STREAMING → COMPLETED` voraus, er baut ihn nicht (Abstimmungsbedarf, §6).
- Port, Flow und Typabbildung des Backends — Welle `welle-backend-streaming`; dieser Slice konsumiert nur `Failure` und das Stromende.
- Gate über das Gesamtsystem mit dem Referenzclient (Fehler nach bereits gesendeten Zeilen am .NET-Client) — `slice-qa-synchronitaet` und `slice-qa-kompatibilitaet`; hier Rohbytes-Tests mit einem Fake-Backend.
- Wiederholungen oder Retry im Server — Option C der ADR, nicht gewählt.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [ ] Anschluss des Mappings an den Sitzungsrahmen unter dem Mutex je Sitzung ([ADR-0004](../../adr/0004-session-zustandsautomat.md)): Fehler vor der ersten Zeile, nach Teilzeilen bei synchronem Strom (ERROR und DONE mit Fehlerstatus, genau ein Abschluss) und bei nicht abschließbarem Strom (Verbindung ohne ERROR und DONE geschlossen, kein zweiter Resultset-Anfang); ein Test je Folge der Mapping-Tabelle. ([TDS-FA-06](../../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände) Negative, `SPEC-031`)
- [ ] Rohbytes-Test über eine Verbindung: Nach einem Fehler der Class ≤ 19 erhält die unmittelbar folgende Anfrage nur ihre eigene Antwort, nach einer schließenden Folge wird keine weitere Anfrage angenommen und es entsteht kein zweites DONE. ([TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität), Protokollebene mit Fake-Backend)
- [ ] Die vier offenen Zustandspunkte sind vom Nutzer entschieden und mit Test umgesetzt oder in der Closure als weiter offen mit Grund geführt: Antwort auf einen unzulässigen Übergang, Verhalten bei `RESETCONNECTION`, Sitzungszustand nach einem Fehler (offene Transaktion, R08/R09 der ADR-Messung), Kriterium für „Strom nicht mehr sicher abschließbar“.
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
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/session/` (Package-Name ein Vorschlag) | update | Fehlerereignis im Sitzungsrahmen: Mapping-Folge ausführen, Übergang und Schreiben unter dem Mutex |
| Anfragezustand (Response Sequencer, ARC-015) | update | Fehlerpfad in `RUNNING` und `STREAMING`; Ort hängt an der Abstimmung mit `welle-anfrageverarbeitung` |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/session/` | neu | Test je Folge, Folgeanfrage nach Fehler, kein zweites DONE ([TDS-FA-06](../../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände), [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität)) |
| `spec/spezifikation.md` | update | `SPEC-001` und [TDS-FA-06.a](../../../../spec/spezifikation.md#tds-fa-06a--fehlerabbildung) nach den Nutzerentscheidungen zu den vier offenen Punkten |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf, inklusive einer Testdatei-Zeile mit der Akzeptanzkriterien-ID in `Begründung`.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): Der Start-Trigger der Welle `welle-fehler-abbruch` ist eingetreten, `slice-fehler-mapping` liegt in `done/`, ein Slice der Welle `welle-anfrageverarbeitung` mit dem Anfragezustand `IDLE → RUNNING → STREAMING → COMPLETED → IDLE` über einem Port-Strom liegt in `done/` (Kennung legen die Planer jener Welle fest), [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) steht im ADR-Index auf `Accepted`, und ein Verantwortlicher ist im Kopf eingetragen.

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

- Wer baut den Response Sequencer (ARC-015)? [TDS-FA-06](../../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände) und [TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe) sprechen vom Anfragezustand, die Wellen `welle-anfrageverarbeitung` und `welle-fehler-abbruch` schneiden ihn von zwei Seiten. Der Schnitt dieses Plans (Happy Path dort, Fehler- und Abbruchpfad hier) ist ein Vorschlag und mit den anderen Planern abzustimmen. — **Ausgang:** offen bis zur Closure
- Entscheidungsbedarf (Nutzer): Kriterium für „der Strom lässt sich nicht mehr sicher abschließen“ (`SPEC-031`, [ADR-0007](../../adr/0007-fehler-und-attention.md)) — die ADR nennt keines; das ist hier nicht entschieden. — **Ausgang:** offen bis zur Closure
- Entscheidungsbedarf (Nutzer): Sitzungszustand nach einem Fehler — am echten Server überlebt eine offene Transaktion einen Fehler ohne `XACT_ABORT` (R08/R09, ein Lauf je Zeile). Ob ein Backend dieses Repos Transaktionen kennt, hängt am Port-Vertrag. — **Ausgang:** offen bis zur Closure
- Entscheidungsbedarf (Nutzer): `RESETCONNECTION` (Pooling) und die Antwort auf einen unzulässigen Übergang (der ADR-Spike schloss ohne Antwort, eine Setzung des Spike). Pooling mit einer vom Server geschlossenen Verbindung ist nicht gemessen. — **Ausgang:** weiter offen: → `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`
- Die Messung stützt „offen nach Class ≤ 19“ am echten Server und „ERROR, DONE, Schließen“ am Testserver mit dem Referenzclient (T, S21), nicht an einem Server dieses Repos; TDS 8.0 ist nicht gemessen. — **Ausgang:** weiter offen: → `BEO-TDS/messung-am-echten-sql-server-fehlt`
- Mutex je Sitzung: Ein Schreiben unter der Sperre hält den Übergang bei langsamem Leser (Backpressure) auf; die Auswirkung auf dem echten Transport ist in [ADR-0004](../../adr/0004-session-zustandsautomat.md) nicht gemessen. — **Ausgang:** offen bis zur Closure

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
