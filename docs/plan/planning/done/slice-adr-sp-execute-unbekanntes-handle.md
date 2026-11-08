# Slice slice-adr-sp-execute-unbekanntes-handle: ADR-Entwurf zur Antwort auf `sp_execute` mit unbekanntem Handle (Spezifikation 50009 gegen echten Server 8179)

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-testserver-manuell

**Bezug:** [TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0037](../../adr/0037-sp-unprepare-unbekanntes-handle-ergibt-fehler-8179.md), [ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md), [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md), [ADR-0017](../../adr/0017-prepared-handles-grenzwert.md)

**Berührte Spec-Stellen:** SPEC-057 (`TDS_UNKNOWN_HANDLE`, `sp_execute` mit unbekanntem Handle) · SPEC-030 (RPC-Varianten, Handle-Verwaltung) · Abschnitt „Fehlercodes“, Zeile `TDS_UNKNOWN_HANDLE` (50009). Die Spezifikation ändert der Bau-Slice nach `Accepted`, nicht dieser Slice.

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912. **Datum:** 2026-11-06.

---

## 1. Ziel und Abgrenzung


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Eine ADR im Status `Proposed` legt dem Nutzer die Frage vor, mit welchem Fehler der Server dieses Repos auf `sp_execute` mit einem Handle antwortet, das die Sitzung nicht hält. Empfehlung des Planners: die Spezifikation folgt dem echten Server (Nummer 8179, State 4 laut Messung M-6), weil der Nutzergrundsatz vom 2026-11-06 die Spezifikation als Zielbild und den echten SQL Server als Referenz setzt. Der Slice entscheidet nichts; entscheiden tut der Nutzer über die ADR, geschrieben vom Architect.

**Herkunft.** Zielbild-Konflikt aus der Messreihe vom 2026-11-06: `SPEC-057` und die Fehlertabelle führen `TDS_UNKNOWN_HANDLE` mit Nummer 50009 als Festlegung (nicht gemessen); [ADR-0037](../../adr/0037-sp-unprepare-unbekanntes-handle-ergibt-fehler-8179.md) (`Accepted`) hat für `sp_unprepare` Nummer 8179 gewählt (M-4, State 8) und vermerkt in „Offen“, dass der echte Server für `sp_execute` vermutlich ebenfalls 8179 sendet. M-6 (Auftrag des Nutzers vom 2026-11-06, Angabe des Auftraggebers): `sp_execute` mit unbekanntem Handle liefert am echten Server 8179 mit State 4. **Beleg-Lage:** im Messprotokoll `docs/messungen/referenzabgleich.md` steht zum Planungszeitpunkt keine Zeile M-6 (die Tabelle endet bei M-5); die Aussage trägt nur so weit wie ihr Beleg (`AGENTS.md` §3.8), deshalb ist der Beleg Liefer-Punkt 1.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Bau und Nachzug der Spezifikation (`SPEC-057`, `SPEC-030`, Fehlertabelle, Tests, Kompatibilitätsmatrix) — Folge-Slice, den der Planner nach `Accepted` der ADR anlegt; vorher wäre er ein Bau gegen eine offene Entscheidung.
- Bau von Freigabe, Zählung und Antwortform der Handles — [slice-handle-freigabe-und-grenze-bauen](../done/slice-handle-freigabe-und-grenze-bauen.md) (geliefert, `done/`); dort liegen `SPEC-030` und der Pfad von `sp_unprepare`, dieser Slice berührt beide nicht (kein Konflikt zweier Slices an derselben Stelle).
- Die Reaktion eines Treibers auf den Fehler bei `sp_execute` mit unbekanntem Handle — in `docs/messungen/handle-grenze-optionen-2026-11-06.md` als nicht gemessen geführt; sie ist kein Teil der Frage „welche Nummer“.
- Eine inhaltliche Änderung von [ADR-0037](../../adr/0037-sp-unprepare-unbekanntes-handle-ergibt-fehler-8179.md) oder [ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md) — beide sind `Accepted` und unveränderlich (`AGENTS.md` §3.5); die neue ADR schärft sie über ihr `Schärft:`-Feld.

## 2. Definition of Done


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [x] **Messbeleg M-6** ([TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)): Das Messprotokoll `docs/messungen/referenzabgleich.md` führt die Zeile M-6 mit Instanz und Digest, Client, Aufruf (`sp_execute` mit einem Handle, das die Sitzung nie vergeben hat, und eines nach `sp_unprepare`, falls gemessen), Nummer, Class, State, Text und Zustand der Verbindung, als Einzelmessung gekennzeichnet; liegt die Messung nur beim Auftraggeber, wird sie am gepinnten Image (EULA vom Nutzer bestätigt) wiederholt. Beleg: Messprotokoll, `make docs-check`. — **Beleg:** Zeile M-6 (und M-7) in `docs/messungen/referenzabgleich.md` (Einzelmessung 2026-11-06, `Execute_mit_unbekanntem_Handle_und_gemischter_Unprepare_Batch`, 2022 und 2025); die Beleg-Lage des Plans („Tabelle endet bei M-5“) ist überholt.
- [x] **ADR `Proposed`** ([TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc)): Der Architect schreibt die ADR aus der ADR-Vorlage (`.harness/baseline/v6.18.1/templates`): Optionen mindestens A (Spezifikation folgt dem Server: 8179 mit dem State der Messung, gemeinsamer Fehler mit `sp_unprepare`), B (50009 bleibt als bewusste Abweichung, Setzung) und die Frage, ob der State je Pfad (4 gegenüber 8 bei `sp_unprepare`) oder einheitlich geführt wird; Empfehlung A; `Schärft:`-Feld auf `SPEC-057`, `SPEC-030` und die Fehlertabelle; Folgepflicht-Liste; Eintrag im ADR-Index. Beleg: `make docs-check`. — **Beleg:** [ADR-0045](../../adr/0045-sp-execute-unbekanntes-handle-ergibt-fehler-8179-state-4.md), Eintrag im ADR-Index; Optionen A und B, `Schärft:`-Feld, Folgepflicht-Liste.
- [x] **Entscheid und Folge-Slice** ([TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc)): Der Nutzer entscheidet; mit `Accepted` legt der Planner den Bau-Slice (Spezifikation, Fehlerpfad, Tests, Matrix) als Datei in `open/` an und trägt ihn in die Welle ein. Beleg: ADR-Status, Datei des Folge-Slice. — **Beleg:** [ADR-0045](../../adr/0045-sp-execute-unbekanntes-handle-ergibt-fehler-8179-state-4.md) steht auf `Accepted` (Nutzerentscheid 2026-11-08, Option A); Folge-Slice [slice-sp-execute-unbekanntes-handle-bauen](../in-progress/slice-sp-execute-unbekanntes-handle-bauen.md) liegt in `open/` und in der Welle.

Nachweis-Pflichten für alle Punkte:

- [x] `make gates` grün. — *reine Dokumentation: nach der Tabelle der Änderungsart gilt `make docs-check` (grün); ein Lauf von `make gates` fand nicht statt.*
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Haken der Liefer-Punkte setzt der Planner nach der Verifikation, nicht der Implementer (`BEO-TDS/haken-vor-verifikation-gesetzt`).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist. — kein öffentlicher Vertrag berührt (ADR und Planung, keine Spec- oder Quelltextänderung).
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert. — Stand von `BEO-TDS/sp-execute-unbekanntes-handle-spec-057-widerspricht-messung` fortgeschrieben; keine neue Beobachtung angefallen.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

## 3. Plan (vor Code)


Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `docs/messungen/referenzabgleich.md` | update | Zeile M-6 mit Messbeleg ([TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)) |
| `docs/plan/adr/` (neue ADR, Nummer über den ADR-Index) | neu | Entscheidung zu Nummer und State des Fehlers bei `sp_execute` mit unbekanntem Handle |
| `docs/plan/adr/README.md` | update | Index der ADRs |
| `docs/plan/planning/open/` (Bau-Slice, Kennung nach `Accepted`) | neu | Folge-Slice für Spezifikation und Bau; heute ohne Datei |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [ADR-0037](../../adr/0037-sp-unprepare-unbekanntes-handle-ergibt-fehler-8179.md) steht auf `Accepted` (erfüllt am 2026-11-06), und ein Verantwortlicher ist eingetragen. Keine Wartebedingung auf andere Slices: der Slice schreibt nur Messprotokoll, ADR und Index.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): die ADR trägt mehr als die Fehlerantwort bei unbekanntem Handle (etwa die Treiberreaktion oder die Antwortform von `sp_prepare`); Schnittvorschlag: Messbeleg | ADR | Folge-Slice.
- `in-progress` → `open` (blockiert): der Messbeleg lässt sich nicht erneuern (gepinntes Image nicht startbar, EULA nicht mehr bestätigt); der Nutzer entscheidet, ob die ADR auf der Angabe des Auftraggebers als Setzung gekennzeichnet entsteht.

## 5. Closure-Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle drei Liefer-Punkte der DoD erledigt, die ADR steht auf `Accepted` (Entscheidung des Nutzers), der Bau-Slice liegt als Datei in `open/`, `make gates` grün, der Review-Report liegt vor, und die Closure-Notiz trägt den Steering-Loop-Lerneintrag sowie die Zuordnung *Messung | Festlegung* für die Nummer und den State.

## 6. Risiken und offene Punkte


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **M-6 ohne Beleg im Repo.** Die Angabe 8179 mit State 4 kommt vom Auftraggeber; ohne Zeile im Messprotokoll trägt die ADR eine Aussage breiter als ihr Beleg (`AGENTS.md` §3.8, `BEO-TDS/aussagen-ueber-messung-hinaus`). — **Ausgang:** entfallen: die Zeile M-6 steht im Messprotokoll (Einzelmessung 2026-11-06, 2022 und 2025); die ADR stützt Nummer, Class, State und Text darauf.
- **State je Pfad.** M-4 zeigt State 8 bei `sp_unprepare`, M-6 State 4 bei `sp_execute`; ob der Server den State je Pfad führt oder einen Wert setzt, ist eine Setzung der ADR. — **Ausgang:** entfallen: [ADR-0045](../../adr/0045-sp-execute-unbekanntes-handle-ergibt-fehler-8179-state-4.md) Entscheidung 3 führt den State je Aufrufform (8 bei der Batch-Freigabe nach M-4, 4 bei `sp_execute` nach M-6), beides gemessen.
- **Kante zu `slice-handle-freigabe-und-grenze-bauen` an `SPEC-030`.** Der Handle-Slice liegt in `done/` und hat `SPEC-030` geschrieben; der Bau-Slice nach der ADR ändert danach dieselbe Stelle, ein Konflikt zweier Slices entsteht nicht mehr. — **Ausgang:** entfallen: der Handle-Slice liegt in `done/`; der Bau-Slice [slice-sp-execute-unbekanntes-handle-bauen](../in-progress/slice-sp-execute-unbekanntes-handle-bauen.md) ändert `SPEC-030` ohne zweiten offenen Slice an derselben Stelle.

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

- **Was hat funktioniert:** Die Messung vor der ADR trug: M-6 und M-7 lagen im Messprotokoll, als der Architect schrieb, und die ADR trennt gemessen (Nummer, Class, State, Text, ein Fehler, offene Verbindung) von Setzung (Tokenfolge, weitere Felder, `RESETCONNECTION`) mit Offen-Liste; die Aussage ist nicht breiter als ihr Beleg (`AGENTS.md` §3.8). Der Nutzer entschied am 2026-11-08 für Option A („Spec folgt dem echten Server“).
- **Was ging anders als geplant:** (a) Der Plan sah vor, dass der Slice die Zeile M-6 erst anlegt (Liefer-Punkt 1); sie lag zur Closure schon vor, der Punkt wurde zur Prüfung. (b) Die Liste der Spec-Änderungen der ADR nennt die Fehlertabelle in `docs/user/referenz-konfiguration.md` nicht; der Bau-Slice führt sie als eigene Fundstelle (zweite Fundstelle, Klasse `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`, verkörpert im Reviewer-Skill). (c) Kein Review-Report: der Review-Haken der DoD bleibt offen, `Accepted` liegt durch Nutzerentscheid vor; ein Review des ADR-Textes ist nicht durchgeführt worden.
- **Steering-Loop-Eintrag:** Nichts verkörpert; kein `liegt in`, gezählt wird. Benannt: Der Re-Evaluierungs-Trigger einer angenommenen ADR (hier [ADR-0037](../../adr/0037-sp-unprepare-unbekanntes-handle-ergibt-fehler-8179.md) Offen 2) hat den Weg Messung, Folge-ADR, Bau-Slice getragen; die Kette ist im Register und in der Welle nachvollziehbar, ein neuer Sensor entsteht daraus nicht.
- **Beobachtungs-Register (`../observations/`):** `state.md` von `BEO-TDS/sp-execute-unbekanntes-handle-spec-057-widerspricht-messung` fortgeschrieben (Träger entschieden, Bau-Slice); keine neue Beobachtung angefallen.
- **Folge-Slices:** [slice-sp-execute-unbekanntes-handle-bauen](../in-progress/slice-sp-execute-unbekanntes-handle-bauen.md) (Spezifikation, Quelltext, Tests, Matrix) — ist eine Datei in `open/`.
- **Risiken aus §6:** alle drei entfallen, Ausgänge in §6.
- **Drei Paarungen:** Anker: kein `liegt in` in diesem Slice, nichts zu paaren; Folge-Slice: Datei in `open/` vorhanden; Register: Stand fortgeschrieben. Prüfung der Welle-Closure vorbehalten.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); der Slice berührt Messprotokoll, ADR-Verzeichnis und Index, eine eigene Sub-Area besteht nicht.

**Vorgelagert — offene Beobachtungen sichten:** `BEO-TDS/messung-am-echten-sql-server-fehlt` (Evidence `adr-0037-rpc-sp-unprepare-abweichung`; der Gegenstand folgt dem Muster), `BEO-TDS/spec-setzung-widerspricht-messung-am-echten-server` (Träger entschieden: Spezifikation dem echten Server angleichen), `BEO-TDS/aussagen-ueber-messung-hinaus` (verkörpert; Risiko 1).

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
