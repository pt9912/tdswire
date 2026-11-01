# Slice slice-cancel-referenzclient-szenario: Cancel-Szenario von SPEC-040 und Cancel-Teil von TDS-QA-07 am Referenzclient

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-fehler-abbruch

**Bezug:** [TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md), [CO-004](../../carveouts/CO-004-cancel-szenario-spec-040-fehlt.md)

**Berührte Spec-Stellen:** SPEC-040 · SPEC-032 · SPEC-029 · SPEC-009 · SPEC-016

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912. **Datum:** 2026-11-01.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Der Referenzclient `Microsoft.Data.SqlClient` bricht eine laufende Anfrage gegen den Serverprozess per `SqlCommand.Cancel()`, `CancellationToken` und Command-Timeout ab und erhält danach auf derselben Verbindung nur die Antwort der Folgeabfrage; läuft die Frist `QUERY_TIMEOUT` am Server ab, sieht der Client einen Serverfehler und behält die offene Verbindung. Der Lauf löst den Carveout [CO-004](../../carveouts/CO-004-cancel-szenario-spec-040-fehlt.md) auf und liefert den Cancel-Teil der Messmethode von [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität), den kein anderer Slice trägt.

Der Slice entstand am 2026-11-01 aus den ehemaligen DoD-Punkten 4 und 5 von [slice-attention-cancel-timeout](../in-progress/slice-attention-cancel-timeout.md) (Zerlegung nach [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md), Grenze höchstens drei Liefer-Punkte je Slice).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Der Abbruchpfad im Server (ATTENTION erkennen, genau ein Cancel, `DONE[ATTN]`, `CANCEL_TIMEOUT`, Frist mit `TDS_TIMEOUT`) — [slice-attention-cancel-timeout](../in-progress/slice-attention-cancel-timeout.md); er ist Voraussetzung (§4), dieser Slice misst ihn nur von außen.
- Die Zwei-Nachrichten-Form der Cancel-Antwort — [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) baut sie nur, wenn dieser Lauf mit der Ein-Nachricht-Form scheitert. Das ist dann eine Re-Evaluierung der ADR und ein neuer Zuschnitt, nicht Teil dieses Slice (Rückführung in §4).
- `RESETCONNECTION` und Pooling — [slice-resetconnection-zuruecksetzen](slice-resetconnection-zuruecksetzen.md); der Lauf hier ist TDS 7.4 ohne Verschlüsselung und ohne Pool (`SPEC-040`).
- Der Vergleich der Cancel-Antwort mit einem echten SQL Server (R11 bis R16) — `slice-qa-vergleichslauf-d`; dieser Slice misst den Referenzclient gegen den Server dieses Repos.
- Cancel unter TLS und TDS 8.0 — nicht gemessen, Register `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`.
- MARS und mehrere gleichzeitige Anfragen je Sitzung (`SPEC-004`).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [ ] **Cancel-Szenario von `SPEC-040`** ([CO-004](../../carveouts/CO-004-cancel-szenario-spec-040-fehlt.md)): `make simulations-integration` (Stufe 3 des E2E-Durchgangs, [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)) führt ein fünftes Szenario aus: der Referenzclient bricht eine laufende Anfrage über die Zeilenquelle des Simulations-Backends mit `SqlCommand.Cancel()` ab, die Anfrage ist beim Abbruch nachweislich im Lauf, und die Folgeabfrage auf derselben Verbindung erhält nur ihre eigene Antwort (`State` bleibt `Open`); der Server loggt danach keinen Handlerfehler. Der Satz „Cancel mit Folgeabfrage gehört nicht zum Lauf“ in `SPEC-040`, der Kopf von `tools/simulationstest.sh`, die Hinweise im KDoc von `SimulationBackendTests.cs` und die Sensor-Zeile `make simulations-integration` in `harness/README.md` werden berichtigt; der Carveout wandert als reiner `git mv` nach `docs/plan/carveouts/done/`. ([TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe) Happy Path, [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität), `SPEC-040`) **E2E-Stufe:** Stufe 3 trägt zusätzlich die Kennungen [TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe) und [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität) in `tools/e2e.sh`; `make e2e-abdeckung` zeigt sie danach in `docs/plan/e2e-abdeckung.md`.
- [ ] **Cancel-Teil der Messmethode von [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität)** (aus `slice-qa-synchronitaet`, dessen Fehler-Matrix diesen Teil nicht lieferte, [CO-004](../../carveouts/CO-004-cancel-szenario-spec-040-fehlt.md)): `SqlCommand.Cancel()`, `CancellationToken`, Command-Timeout und ATTENTION während des Streamings am Referenzclient, jeweils mit unmittelbar folgender Abfrage auf derselben Verbindung, die nur ihre eigene Antwort erhält. Die Fälle erweitern die Kategorie `Synchronitaet` der Stufe 8 des Produkt-E2E (`make kompatibilitaet KATEGORIE=Synchronitaet`, `tools/e2e.sh`), laufen über die Prozessgrenze und gehören zu den Läufen mit dem Testserver `KompatibilitaetsServer.kt`; der Text „ohne Cancel“ und die Abdeckungs-Zeile der Stufe 8 und der Absatz „nicht lieferbar bis [CO-004](../../carveouts/CO-004-cancel-szenario-spec-040-fehlt.md) aufgelöst ist“ in der Kompatibilitätsmatrix werden berichtigt, die Zeile 12 der bekannten Lücken („Wirkung von ATTENTION“) nachgetragen. Gemessen wird, nicht gesetzt: ob der Command-Timeout des Clients als ATTENTION ankommt (R15 der Messung in [ADR-0007](../../adr/0007-fehler-und-attention.md)).
- [ ] **`TDS_TIMEOUT` als Serverfehler am Referenzclient** ([ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) Abschnitt „Messungen“): ein Serverprozess mit kurzem `QUERY_TIMEOUT` und längerem Command-Timeout des Clients; gemessen und in der Kompatibilitätsmatrix eingetragen werden Exception, `Number`, `Class`, `State` der Verbindung und die Folgeabfrage auf derselben Verbindung. Erwartet ist die Setzung (`SqlException` mit Number 50005 und Class 16, Verbindung offen); weicht die Messung ab, ist das ein Re-Evaluierungs-Fall von [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) (Rückführung in §4), kein stilles Anpassen des Tests. Der Lauf gehört in dieselbe Stufe 8 (Setzung des Planners, weil die Kategorie `Synchronitaet` Fehler mit Wiederverwendung der Verbindung schon trägt).
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist: `SPEC-040` (Lauf mit Cancel-Szenario, Satz zur befristeten Ausnahme entfällt), Kompatibilitätsmatrix (Lücke 12, Absatz zum Cancel, gemessene Zeilen zu Cancel, Command-Timeout und `TDS_TIMEOUT`), `harness/README.md` (Sensor-Zeile `make simulations-integration` ohne die Bindung an [CO-004](../../carveouts/CO-004-cancel-szenario-spec-040-fehlt.md)), `docs/plan/carveouts/README.md` (Zeile von [CO-004](../../carveouts/CO-004-cancel-szenario-spec-040-fehlt.md) nach `done/`).
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
| `tools/simulationstest.sh` | update | Kopf und Zusage: Cancel-Szenario gehört zum Lauf ([CO-004](../../carveouts/CO-004-cancel-szenario-spec-040-fehlt.md)) |
| `referenztest-dotnet/Integration/SimulationBackendTests.cs` | update | fünftes Szenario: `Cancel()` einer laufenden Anfrage über die Zeilenquelle, Folgeabfrage ([TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität)) |
| `src/test/kotlin/io/github/pt9912/tdswire/runtime/IntegrationServer.kt` | update | falls die Zeilenquelle `gross` nicht lange genug läuft, um sicher im Lauf abgebrochen zu werden: eine Quelle, die auf den Abbruch wartet (Test-Quelltext, kein Produkt) |
| `referenztest-dotnet/Kompatibilitaet/SynchronitaetTests.cs` | update | `Cancel()`, `CancellationToken`, Command-Timeout, ATTENTION während des Streamings, `TDS_TIMEOUT`, je mit Folgeabfrage ([TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität)) |
| `src/test/kotlin/io/github/pt9912/tdswire/runtime/KompatibilitaetsServer.kt` | update | `QUERY_TIMEOUT` kurz einstellbar (nutzt die Einstellung aus [slice-attention-cancel-timeout](../in-progress/slice-attention-cancel-timeout.md)); Quelle für die Streaming-Fälle |
| `tools/e2e.sh`, `tools/kompatibilitaetstest.sh` | update | Text und Abdeckungs-Zeile der Stufen 3 und 8 (Kennungen [TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität), „ohne Cancel“ entfällt) |
| `spec/spezifikation.md`, `docs/plan/kompatibilitaetsmatrix.md`, `harness/README.md`, `docs/plan/carveouts/` | update | Doku-Update in §2, Auflösung von [CO-004](../../carveouts/CO-004-cancel-szenario-spec-040-fehlt.md) |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf, inklusive einer Testdatei-Zeile mit der Akzeptanzkriterien-ID in `Begründung`.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-attention-cancel-timeout](../in-progress/slice-attention-cancel-timeout.md) liegt in `done/` (der Abbruchpfad mit Fristen und die Einstellung von `QUERY_TIMEOUT` stehen auf `main`), [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) steht im ADR-Index auf `Accepted` (erfüllt), die Stufen 3 und 8 des Produkt-E2E laufen (`make e2e` ist auf `main` grün) und ein Verantwortlicher ist im Kopf eingetragen.

**Carveout:** [CO-004](../../carveouts/CO-004-cancel-szenario-spec-040-fehlt.md) nennt diesen Slice als Folge-Slice. Er ist aufgelöst, wenn dieser Slice in `done/` liegt und das Cancel-Szenario in `make simulations-integration` besteht (Auflösungs-Trigger des Carveouts).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2, oder der dritte Punkt (`TDS_TIMEOUT` am Client) verlangt einen Umbau des Testservers, der den Rahmen sprengt; Schnittvorschlag: Punkt 3 als eigener Slice hinter diesem. Auslöser, am Slice-Plan beobachtbar: die Häkchen der Punkte 1 und 2 sind gesetzt und für Punkt 3 gibt es noch keinen Test.
- `in-progress` → `open` (blockiert — Carveout?): Der Referenzclient scheitert mit der Ein-Nachricht-Form (hängt bis zum Timeout oder schließt die Verbindung nach `Cancel()`) oder mit `TDS_TIMEOUT` Class 16: das ist der Re-Evaluierungs-Trigger von [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md). Der Slice wartet dann auf eine Folge-ADR (Zwei-Nachrichten-Form, andere Class); der Carveout bleibt bestehen.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle Punkte der DoD in §2 erledigt, `make gates` grün (einschließlich `make test`), `make e2e` grün, das Cancel-Szenario besteht in `make simulations-integration`, der Carveout liegt nach der Auflösung in `docs/plan/carveouts/done/`, und die Closure-Notiz mit Steering-Loop-Lerneintrag ist geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Die Ein-Nachricht-Form der Cancel-Antwort und Class 16 für `TDS_TIMEOUT` sind Setzungen des Nutzers ([ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md)), am Referenzclient nicht gemessen; dieser Slice misst sie. Ein Fehlschlag ist ein Re-Evaluierungs-Fall, kein Defekt des Slice. — **Ausgang:** offen bis zur Closure
- Dass eine Anfrage beim `Cancel()` nachweislich läuft, ist beim Referenzclient nicht selbstverständlich: wird der Abbruch zu früh oder zu spät ausgelöst, ist der Test ein Zufallsbeleg. Die Zeilenquelle muss auf den Abbruch warten, statt nach einer Wanduhr zu enden. — **Ausgang:** offen bis zur Closure
- Der Testserver ist Testwerkzeug, kein Produkt ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md), [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md)); der Lauf belegt das Verhalten des Referenzclients gegen den Server dieses Repos in TDS 7.4 ohne Verschlüsselung und ohne Pool, nicht gegen einen echten SQL Server und nicht unter Pooling. — **Ausgang:** weiter offen: → `BEO-TDS/messung-am-echten-sql-server-fehlt`
- Cancel und Timeout unter Pooling und TDS 8.0 am Referenzclient sind nicht Teil des Laufs. — **Ausgang:** weiter offen: → `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`
- Stufe 8 und Stufe 3 laufen in `make e2e`, nicht in `make gates` ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)); ein Fehler im Cancel-Pfad wird erst im E2E-Durchgang sichtbar. — **Ausgang:** offen bis zur Closure

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`). Die berührten Pfade (`referenztest-dotnet`, `tools`, `src/test`) tragen keine eigene Strukturregel (Achse 1) und keine eigene Inventur-Linie (Achse 2): eine von drei Achsen, unter der Schwelle. Keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, Stand des gemergten `main` am 2026-11-01); alle tragen die Sub-Area `*`. Treffer mit Zähler-Stand (Zahl der Dateien unter `evidence/`):

- `BEO-TDS/messung-am-echten-sql-server-fehlt` — 25×, Stand *geplant*. Treffer: Cancel-Antwort und Timeout am Server dieses Repos statt an einem echten SQL Server (§6).
- `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` — 17×, Stand *offen* (über der Schwelle, ohne Ausgang, Entscheidung beim Nutzer). Treffer: Cancel unter Pooling und TDS 8.0 (§6).
- `BEO-TDS/aussagen-ueber-messung-hinaus` — 45×, Stand *verkörpert*. Treffer: die Erwartung Number 50005, Class 16 steht als Setzung, nicht als Messung (Punkt 3).
- `BEO-TDS/mutation-haengt-an-ungesteuerter-reihenfolge` — 1×, offen. Treffer: Abbruch zum nachweislich laufenden Zeitpunkt (§6).
- `BEO-TDS/client-schliesst-bei-class-20-selbst` — 1×, offen. Treffer: Verhalten des Clients bei Serverfehlern der Class 16 gegen Class 20 (Punkt 3).
- Ohne Treffer: die übrigen Einträge des Registers.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
