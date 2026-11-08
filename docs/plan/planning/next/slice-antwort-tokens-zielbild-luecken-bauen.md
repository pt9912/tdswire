# Slice slice-antwort-tokens-zielbild-luecken-bauen: Zielbild-Lücken der Antwort-Tokens bauen: Flag-Feld der Ausgabewerte, Anweisungsart am Port und `curCmd` des Änderungszählers und nach Fristablauf, Tokenfolge und `curCmd` von `sp_unprepare`

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-testserver-manuell

**Bezug:** [TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), [TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md), [ADR-0038](../../adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md), [ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md)

**Berührte Spec-Stellen:** SPEC-030 (Flag-Feld des RETURNVALUE von Ausgabewerten des Backends, `curCmd`, Tokenfolge der Handle-Freigabe) · SPEC-031 (Abschluss der Antwort, `curCmd`; Grenze „die Art ist eine Zusage des Backends“) · SPEC-059 (Liste der öffentlichen Namen: `StatementKind`, `UpdateCount.kind`; Änderungsprotokoll) · SPEC-060 (Antwortform bei `sp_unprepare`, nur Tokenfolge). **Die Spezifikation ändert dieser Slice** (Spezifikation ist Zielbild; die „bekannte Abweichung“ entfällt).

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-07 (Zuschnitt 2026-11-08)

---

## 1. Ziel und Abgrenzung


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Die Antwort-Tokens des Servers dieses Repos stimmen mit den gemessenen des echten SQL Servers überein, wo die Spezifikation es als Zielbild verlangt: (1) das Flag-Feld des RETURNVALUE aller Ausgabewerte des Backends ist `0x0000` (10 gemessene Typen, mit Wert, NULL und ohne Zuweisung, M-24); (2) `curCmd` des Abschlusses nach einer Änderung: INSERT `0x00c3`, DELETE `0x00c4`, UPDATE `0x00c5` ([Messreihe am Draht](../../../messungen/referenzabgleich.md#messreihe-am-draht-messungen-vom-2026-11-06), W-6), und nach Fristablauf bei offenem Ergebnis `0x00c1`; die Anweisungsart meldet das Backend über das öffentliche Feld `kind` ([ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) Option A); (3) `EXEC sp_unprepare` schließt mit `RETURNSTATUS` und `DONEPROC` Befehl `0x00e0`, ohne `DONE` (M-23), im Fehlerfall je Nummer `ERROR`, `RETURNSTATUS 8179`, `DONEPROC`.

**Wer was anlegt (festgelegt 2026-11-08, [ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) „wer von beiden Bauten zuerst kommt“).** Dieser Slice legt `StatementKind` (Package `domain`) und das Feld `UpdateCount.kind` an: `UpdateCount(affectedRows: Long, kind: StatementKind = StatementKind.UNKNOWN)` mit `@JvmOverloads`, und die Mitglieder, die er selbst setzt und belegt: `UNKNOWN` (Kennung 0 mit `COUNT`), `INSERT` (`0x00c3`), `DELETE` (`0x00c4`), `UPDATE` (`0x00c5`), alle mit `COUNT`. Die Abbildung Art → Kennung/`COUNT` steht in der Bibliothek (Sequenzierer); das Simulations-Backend meldet die Art; eigene Backends ohne Art bleiben bei Kennung 0 ([ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) Entscheidung 4). **Nicht hier:** `CREATE_TABLE`, `ALTER_TABLE` und die Mitglieder der Transaktionstexte — sie legt [slice-ddl-abschluss-und-transaktionszustand-angleichen](../open/slice-ddl-abschluss-und-transaktionszustand-angleichen.md) an und setzt ihre Kennungen (ein Mitglied ohne Test entsteht nicht). `0x00c1` nach Fristablauf, das Flag-Feld und `sp_unprepare` bestimmen Protokoll und Sequenzierer allein und hängen nicht am Port ([ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) Kontext).

**Herkunft.** Aus dem Auflösungs-Trigger „nach der A-20-Messung wird der Bau-Slice angelegt“ in [slice-qa-vergleichslauf-d-messungen-bulk-merge](../done/slice-qa-vergleichslauf-d-messungen-bulk-merge.md) §2 (eingetreten: M-23 und M-24 des [Messprotokoll](../../../messungen/referenzabgleich.md#messreihe-zu-massenladen-merge-katalog-und-strukturänderung-messungen-vom-2026-11-07)) und aus dem Register [`BEO-TDS/zielbild-luecken-der-antwort-tokens`](../observations/BEO-TDS/zielbild-luecken-der-antwort-tokens/state.md). Nutzerentscheid 2026-11-07 (Bau-Slice: Flag-Feld `0x0000` aller Backend-Ausgabewerte, `curCmd` des Update-Zählers, `curCmd` von `EXEC sp_unprepare`, `curCmd` nach Fristablauf, Tokenfolge von `sp_unprepare`) und 2026-11-08 ([ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) `Accepted`, Option A: Anweisungsart öffentlich am Port). **Umplanung** gegenüber dem Plan von [slice-antwort-tokens-an-messung-angleichen](../done/slice-antwort-tokens-an-messung-angleichen.md) (Nr 8, Lücken ohne Bau): im Drift-Log der Roadmap eingetragen. Die Spezifikation ist Zielbild, der echte SQL Server die Referenz (Nutzergrundsatz 2026-11-06). Die Messung [slice-qa-vergleichslauf-d-messungen-bulk-merge](../done/slice-qa-vergleichslauf-d-messungen-bulk-merge.md) liegt vor (Einzelmessungen je Instanz 2022 und 2025, Referenzclient `Microsoft.Data.SqlClient`, nicht mssql-jdbc von d-migrate).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Die Menge der gemeldeten Nummern bei `sp_unprepare` — [slice-sp-unprepare-ohne-obergrenze](../done/slice-sp-unprepare-ohne-obergrenze.md); beide Slices berühren die Batch-Freigabe (gleicher Pfad); dieser Slice startet nach ihm und zieht die Antwortform nach (Setzung, §4); die Messreihe b (Heap, zwei und vier gleichzeitige Nachrichten von 16 MiB) des Messprotokolls zum Speicher der Antwort ist nach der Änderung der Größe je Nummer (113 Byte) neu zu messen).
- SESSIONSTATE (Nr 8 des Vorgängers): „vorerst nicht senden“, Nutzerentscheid 2026-11-06, Re-Evaluierungs-Trigger dort; unberührt.
- Die Ursache des SESSIONSTATE-Unterschieds nach dem Abbruch (nur Instanz 2022, Zustandskennung 254) und `COLMETADATA` hinter `RETURNVALUE` — Lücken ohne Träger (Nutzerentscheid 2026-11-06).
- Der Abschluss von `CREATE TABLE` und `ALTER TABLE … ADD` (SQL_BATCH, `0x00c6`, `0x00d8`), die Transaktionstexte und `ENVCHANGE` — [slice-ddl-abschluss-und-transaktionszustand-angleichen](../open/slice-ddl-abschluss-und-transaktionszustand-angleichen.md).
- Strukturabweisung (2714, 2705, 4902) — [slice-katalog-und-struktur-an-messung-angleichen](../done/slice-katalog-und-struktur-an-messung-angleichen.md); Katalogspalten — [slice-katalogspalten-smallint-tinyint-angleichen](../open/slice-katalogspalten-smallint-tinyint-angleichen.md).
- `curCmd` weiterer Anweisungsarten (MERGE `0x0117`, SELECT): nur dort gebaut, wo W-6 und M-13 messen; sonst `UNKNOWN`.

## 2. Definition of Done


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **Flag-Feld der Ausgabewerte** ([TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)): Das Flag-Feld des RETURNVALUE jedes Ausgabewerts des Backends (`int`, `smallint`, `bigint`, `bit`, `decimal`, `float`, `nvarchar`, `date`, `time`, `datetime2`; mit Wert, NULL und ohne Zuweisung) ist `0x0000` (M-24); `SPEC-030` ohne die bekannte Abweichung; je Typ ein Test.
- [ ] **Anweisungsart am Port und `curCmd`** ([TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität); [ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) Entscheidung 1, 2, 4, 7): `StatementKind` mit `UNKNOWN`, `INSERT`, `DELETE`, `UPDATE` und `UpdateCount.kind` (öffentlich, `@JvmOverloads`, Standardwert `UNKNOWN`: bestehende Backends und der Aufruf `new UpdateCount(n)` aus Java bleiben unverändert); der Abschluss nach INSERT, DELETE und UPDATE trägt `0x00c3`, `0x00c4`, `0x00c5` mit `COUNT`, `UNKNOWN` Kennung 0 mit `COUNT`, nach Fristablauf bei offenem Ergebnis `0x00c1` (W-6); das Simulations-Backend meldet die Art; kein Text-Klassifizierer im Protokoll ([ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) Entscheidung 3); `SPEC-059` nennt `StatementKind` in der Liste (`make flaeche` grün; Konstanten und Feld belegt der Einbettungstest) und das Änderungsprotokoll den Eintrag zur Fläche, `SPEC-031` die Grenze „die Art ist eine Zusage des Backends“; Einbettungstest im Source-Set `integrator` (ein eigenes Backend meldet `kind` und bekommt die Antwort des Simulations-Backends; ein altes Backend ohne `kind` bleibt bei Kennung 0); Tests je Anweisungsart.
- [ ] **`sp_unprepare`** ([TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)): Erfolg schließt mit `RETURNSTATUS` 0 und `DONEPROC` Status 0, Befehl `0x00e0`, ohne `DONE`; unbekannte Nummer: `ERROR`, `RETURNSTATUS 8179`, `DONEPROC` (`MORE|ERROR` in der Mitte), Befehl `0x00e0` (M-22, M-23); `SPEC-030`, `SPEC-060` und die Beobachtung [`BEO-TDS/zielbild-luecken-der-antwort-tokens`](../observations/BEO-TDS/zielbild-luecken-der-antwort-tokens/state.md) sind angeglichen; Tests auf dem Draht, Belegtest gegen die Ablage der Messung.

Nachweis-Pflichten für alle Punkte:

- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Haken der Liefer-Punkte setzt der Planner nach der Verifikation, nicht der Implementer (`BEO-TDS/haken-vor-verifikation-gesetzt`).
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
| `src/main/kotlin/io/github/pt9912/tdswire/domain/` (`StatementKind`, `QueryEvent.UpdateCount`) | neu / update | öffentliche Fläche nach [ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md): Aufzählung und Feld `kind` |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/` (Antwortbildung, Sequenzierer, Abbildung Art → Kennung/`COUNT`, `UnprepareBatch`) | update | Flag-Feld, `curCmd`, Tokenfolge |
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/` (Planer, Formen W1 bis W3) | update | Backend meldet die Art bei INSERT, UPDATE, DELETE |
| `testserver/kotlin/` (Integrator, falls er `UpdateCount` baut) | update (falls betroffen) | Anteil des Integrators |
| `src/integrator/` (Einbettungstest) | update | Beleg von `StatementKind`, Backend mit und ohne Art |
| `spec/spezifikation.md` (`SPEC-030`, `SPEC-031`, `SPEC-059`, `SPEC-060`) | update | Zielbild, Liste der Fläche, Änderungsprotokoll |
| Kompatibilitätsmatrix der Spezifikation, `docs/messungen/` (Ausnahmeliste A-20) | update | Stand des Servers gegen den echten |
| Tests (Kotlin; Referenztest .NET nur lesend) | neu / update | Happy/Boundary/Negative nach [TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte) |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-qa-vergleichslauf-d-messungen-bulk-merge](../done/slice-qa-vergleichslauf-d-messungen-bulk-merge.md) in `done/` (erfüllt: Messungen M-23 und M-24 liegen vor); [slice-antwort-tokens-an-messung-angleichen](../done/slice-antwort-tokens-an-messung-angleichen.md) in `done/` (erfüllt); [ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) `Accepted` (erfüllt, 2026-11-08); [slice-sp-unprepare-ohne-obergrenze](../done/slice-sp-unprepare-ohne-obergrenze.md) in `done/` (Setzung des Planners: gleicher Pfad der Batch-Freigabe, dessen Zweig liegt im Review; wer später startet, zieht die Antwortform nach); die parallelen Zweige zu [slice-bulk-load-feste-typen-und-referenzclient](../done/slice-bulk-load-feste-typen-und-referenzclient.md) und [slice-schreibpfad-merge-output](../done/slice-schreibpfad-merge-output.md) sind in `main` (Setzung des Planners wegen der Dateien an `UpdateCount` und der Antwortbildung; kein inhaltlicher Zwang); [slice-katalog-und-struktur-an-messung-angleichen](../done/slice-katalog-und-struktur-an-messung-angleichen.md) in `done/` und [slice-katalogspalten-smallint-tinyint-angleichen](../open/slice-katalogspalten-smallint-tinyint-angleichen.md) in `done/` (Reihenfolge, Setzung des Planners: [slice-katalogspalten-smallint-tinyint-angleichen](../open/slice-katalogspalten-smallint-tinyint-angleichen.md) geht als breitester Eingriff früh und allein); kein anderer Bau-Slice an `QueryEvent` in `in-progress/`.

**Reihenfolge:** [slice-katalog-und-struktur-an-messung-angleichen](../done/slice-katalog-und-struktur-an-messung-angleichen.md) → [slice-katalogspalten-smallint-tinyint-angleichen](../open/slice-katalogspalten-smallint-tinyint-angleichen.md) → dieser Slice → [slice-ddl-abschluss-und-transaktionszustand-angleichen](../open/slice-ddl-abschluss-und-transaktionszustand-angleichen.md) → [slice-katalog-indizes-defaults-fremdschluessel](../open/slice-katalog-indizes-defaults-fremdschluessel.md); [slice-ddl-abschluss-und-transaktionszustand-angleichen](../open/slice-ddl-abschluss-und-transaktionszustand-angleichen.md) setzt `StatementKind` dieses Slice voraus.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte; Schnittvorschlag: Flag-Feld | Anweisungsart mit `curCmd` | `sp_unprepare`. Die Rückführungen „Fläche wächst“ und „Art im Port braucht eine Entscheidung“ entfallen für INSERT, UPDATE und DELETE, sie sind durch [ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) entschieden.
- `in-progress` → `open` (blockiert): ein Test mit einem alten Backend ohne `kind` zeigt eine Änderung der Antwort gegenüber heute (Kennung 0 mit `COUNT` ist die Zusage); der Nutzer entscheidet dann über den Weg.

## 5. Closure-Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle drei Liefer-Punkte der DoD erledigt, `make gates` grün (auch `make flaeche`), der Review-Report liegt vor, die Zeilen M-23 und M-24 sind im [Messprotokoll](../../../messungen/referenzabgleich.md#messreihe-zu-massenladen-merge-katalog-und-strukturänderung-messungen-vom-2026-11-07) von „widerspricht“ auf „angeglichen“ gezogen, die Closure-Notiz trägt den Steering-Loop-Lerneintrag und nennt, welche Mitglieder von `StatementKind` angelegt sind (Übergabe an [slice-ddl-abschluss-und-transaktionszustand-angleichen](../open/slice-ddl-abschluss-und-transaktionszustand-angleichen.md)).

## 6. Risiken und offene Punkte


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **Die Änderung von `UpdateCount`** (`copy`, `componentN`, `equals`; ein Vergleich `UpdateCount(1) == UpdateCount(1, INSERT)` ist ungleich) trifft Quelltext, der es destrukturiert; vor der ersten Veröffentlichung nicht zugesagt ([ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) Konsequenzen). — **Ausgang:** offen bis zur Closure.
- Gemessen sind Einzelfälle je Instanz mit dem Referenzclient; `curCmd` von weiteren Anweisungsarten (MERGE `0x0117`, SELECT) ist nur dort gebaut, wo W-6 und M-13 messen. — **Ausgang:** offen bis zur Closure; für das `MERGE` (`0x0117`, M-26) trägt es [slice-merge-antwort-an-messung-angleichen](../open/slice-merge-antwort-an-messung-angleichen.md) (setzt das Mitglied `StatementKind.MERGE` nach diesem Slice).
- Aus [slice-schreibpfad-merge-output](../done/slice-schreibpfad-merge-output.md): drei gemessene Abweichungen der Antwort des `MERGE` (Länge `nvarchar(10)` der Spalte `$action`, Befehl `0x0117`, Fehlernummern 2628, 2627, 208; M-26, M-15) gehören **nicht** in diesen Slice. — **Ausgang:** Verweis auf [slice-merge-antwort-an-messung-angleichen](../open/slice-merge-antwort-an-messung-angleichen.md) (Träger; Kante: jener Slice folgt diesem).
- Der gemeinsame Pfad mit [slice-sp-unprepare-ohne-obergrenze](../done/slice-sp-unprepare-ohne-obergrenze.md) erzeugt bei gleichzeitigem Lauf Konflikte (Reihenfolge in §4 gesetzt). — **Ausgang:** offen bis zur Closure.
- Eine falsche Art vom Backend liefert die falsche Kennung an den Client ([TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität)); die Grenze steht in `SPEC-031` und `SPEC-059`. — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); eine eigene Sub-Area besteht nicht.

**Vorgelagert — offene Beobachtungen sichten:** [`BEO-TDS/zielbild-luecken-der-antwort-tokens`](../observations/BEO-TDS/zielbild-luecken-der-antwort-tokens/state.md), [`BEO-TDS/spec-setzung-widerspricht-messung-am-echten-server`](../observations/BEO-TDS/spec-setzung-widerspricht-messung-am-echten-server/state.md).

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
