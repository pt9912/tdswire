# Slice slice-ddl-abschluss-und-transaktionszustand-angleichen: Abschluss von `CREATE TABLE` und `ALTER TABLE … ADD` und die Transaktionstexte der Form C an die Messung angleichen: `StatementKind.CREATE_TABLE` und `ALTER_TABLE`, `ENVCHANGE` Typ 8 und 9 zuletzt (M-20)

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-testserver-manuell

**Bezug:** [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität), [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md), [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md), [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), [ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md), [ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md)

**Berührte Spec-Stellen:** SPEC-035 (Abschluss von `CREATE TABLE` und `ALTER TABLE … ADD`: `DONE` ohne `COUNT`; die Setzung „Änderungszähler 0“ entfällt; Transaktionstexte) · SPEC-036 (Form C: die Texte der Transaktion) · SPEC-031 (Absatz „Befehlskennung (`curCmd`)“: Abschluss von Strukturänderungen; Zusage „keine Transaktion“ bei `ENVCHANGE`) · SPEC-030 (Abschluss nach Änderung im RPC, soweit berührt) · SPEC-059 (Konstanten von `StatementKind`, Grenze „die Art ist eine Zusage des Backends“). **Die Spezifikation ändert dieser Slice**; fällt `ENVCHANGE` zurück, steht an seiner Stelle eine benannte Grenze mit dem gemessenen Befund.

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-08

---

## 1. Ziel und Abgrenzung


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Der Server dieses Repos schließt `CREATE TABLE` und `ALTER TABLE … ADD` (SQL_BATCH) so ab, wie der echte SQL Server (2022 und 2025) es nach der Zeile M-20 des [Messprotokoll](../../../messungen/referenzabgleich.md#messreihe-zu-massenladen-merge-katalog-und-strukturänderung-messungen-vom-2026-11-07) tut: `DONE` ohne `COUNT`, Befehl `0x00c6` bzw. `0x00d8`; die Transaktionstexte der Form C (`set implicit_transactions on` mit `0x00b9`, der bedingte `COMMIT TRAN` mit `DONE MORE` `0x00c0`, `ENVCHANGE` Typ 9 und `DONE` `0x00d5`) in den gemessenen Fällen; `ENVCHANGE` Typ 8 bei `CREATE TABLE` nach `set implicit_transactions on` **nur**, wenn ein Lauf mit d-migrate danach unverändert ist ([ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) Entscheidung 8). Die Bibliothek leitet das aus der Anweisungsart ab, die das Backend meldet (`StatementKind` am Port, [ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) Entscheidung 1 bis 5).

**Wer was anlegt.** `StatementKind`, das Feld `UpdateCount.kind` und die Mitglieder `UNKNOWN`, `INSERT`, `DELETE`, `UPDATE` legt [slice-antwort-tokens-zielbild-luecken-bauen](slice-antwort-tokens-zielbild-luecken-bauen.md) an (Punkt 2 dort); dieser Slice ergänzt `CREATE_TABLE` und `ALTER_TABLE` und die Mitglieder der Transaktionstexte (Namen Setzung, im Bau an den gemessenen Fällen) und **setzt** die Befehlskennungen dieser Mitglieder in der Abbildung der Bibliothek. Kein Mitglied entsteht ohne Test. Das Simulations-Backend meldet die Art; eigene Backends ohne Art bleiben bei Kennung 0 mit `COUNT` ([ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) Entscheidung 4).

**Herkunft.** Aus dem Zuschnitt des Slice [slice-katalog-und-struktur-an-messung-angleichen](../next/slice-katalog-und-struktur-an-messung-angleichen.md) vom 2026-11-08 (dessen früherer Liefer-Punkt „Änderungszähler und Transaktionstexte“), der Zeile M-20 des [Messprotokoll](../../../messungen/referenzabgleich.md#messreihe-zu-massenladen-merge-katalog-und-strukturänderung-messungen-vom-2026-11-07) und dem Register [`BEO-TDS/katalog-strukturabweisung-und-aenderungszaehler-weichen-vom-echten-server-ab`](../observations/BEO-TDS/katalog-strukturabweisung-und-aenderungszaehler-weichen-vom-echten-server-ab/state.md). Nutzerentscheid 2026-11-07 (Abschluss ohne `COUNT`, `ENVCHANGE` 8 und 9) und 2026-11-08 ([ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) `Accepted`, Option A; `ENVCHANGE` zuletzt, Rückfall auf benannte Grenze vorentschieden). [ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) löst die Klammer „Änderungszähler 0“ in [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Entscheidung 3 ab (nur diese Klammer).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- `StatementKind` und `UpdateCount.kind`, `curCmd` von INSERT, DELETE, UPDATE, Flag-Feld, `sp_unprepare` — [slice-antwort-tokens-zielbild-luecken-bauen](slice-antwort-tokens-zielbild-luecken-bauen.md); dieser Slice setzt dessen Port voraus.
- Strukturabweisung und `CREATE TABLE` ohne Schlüssel — [slice-katalog-und-struktur-an-messung-angleichen](../next/slice-katalog-und-struktur-an-messung-angleichen.md); Katalogspalten — [slice-katalogspalten-smallint-tinyint-angleichen](slice-katalogspalten-smallint-tinyint-angleichen.md).
- Nicht gemessene Fälle: `implicit_transactions off` außerhalb des gemessenen Textes, `ROLLBACK`, `ENVCHANGE` 8 bei INSERT/UPDATE/DELETE, Ende der Sitzung mit offener Transaktion. Sie bekommen kein Mitglied und enden als `UNKNOWN` (`ROLLBACK TRAN` mit Zähler 0, [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Entscheidung 6); die Zusage „keine Transaktion“ bleibt ([ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) Entscheidung 6).
- Der Abschluss im RPC (`sp_executesql`, `DONEPROC`): die Zeilen „kein Zähler“ in M-20 gelten für SQL_BATCH, nicht für `sp_executesql`.
- Eine Aussage über andere Clients als den Referenzclient (`AGENTS.md` §3.8).

## 2. Definition of Done


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei, in dieser Reihenfolge zu bauen; Punkt 3 zuletzt):

- [ ] **Abschluss der Strukturänderung** ([TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität); [ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) Entscheidung 2): `StatementKind.CREATE_TABLE` (Befehl `0x00c6`) und `ALTER_TABLE` (`0x00d8`), beide `DONE` Status 0 ohne `COUNT`; die Bibliothek ignoriert `affectedRows` bei diesen Arten; das Simulations-Backend meldet die Art bei gelungenem `CREATE TABLE` und `ALTER TABLE … ADD`; eigene Backends ohne Art bleiben `UNKNOWN` (Kennung 0 mit `COUNT`); `SPEC-035` und `SPEC-036` ohne die Setzung „Änderungszähler 0“, `SPEC-059` nennt die Mitglieder; Einbettungstest `integrator` (ein Backend meldet die Art und bekommt die Antwort des Simulations-Backends); Tests je Art (Kennung, `COUNT`-Bit) gegen M-20; Zeile M-20 der Zuordnung im Messprotokoll für diesen Teil gezogen.
- [ ] **Transaktionstexte der Form C** ([TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität); [ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) Entscheidung 2 und 5): `set implicit_transactions on` antwortet mit Befehl `0x00b9` und `set implicit_transactions off IF …` mit `DONE MORE` `0x00ba`, `DONE` `0x00c0` (M-20); der Zustand je Sitzung (`implicit_transactions` an/aus) liegt bei der Verbindung wie die Prepared-Handles ([ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md)) und endet mit ihr; Mitglieder nur für gemessene Fälle (Namen Setzung); was nicht gemessen ist, endet als `UNKNOWN`. Der bedingte `COMMIT TRAN` ist nur **mit** `ENVCHANGE` 9 gemessen und gehört deshalb zu Punkt 3 (Setzung des Planners; der Implementer prüft es an der Ablage der Messung und verschiebt, wenn sie etwas anderes zeigt). Tests je Text.
- [ ] **`ENVCHANGE` Typ 8 und 9 — zuletzt** ([TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität); [ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) Entscheidung 5, 6, 8): `ENVCHANGE` 8 bei `CREATE TABLE` nach `set implicit_transactions on` und der bedingte `COMMIT TRAN` mit `DONE MORE` `0x00c0`, `ENVCHANGE` 9, `DONE` `0x00d5`; die Bytes des Deskriptors aus einem Handvektor der Ablage der Messung (`AGENTS.md` §3.8), nicht aus dem Entwurf; ein Test, der nach `ENVCHANGE` 8 eine Anfrage mit zurückgesendetem Deskriptor im Kopf der Nachricht bedient und eine normale Antwort erwartet. **Behalten nur, wenn** ein Lauf mit d-migrate (`schema migrate --execute`, `compare`) nach diesem Punkt unverändert ist gegenüber einem Lauf unmittelbar davor (vor Punkt 3, nach Punkt 2); das Ergebnis beider Läufe steht im Slice. **Sonst bleibt `ENVCHANGE` ungebaut** (Rückfall D, vorentschieden): der Code wird zurückgenommen, `SPEC-035`/`SPEC-031` nennen die Grenze mit dem gemessenen Befund, der Punkt gilt als erledigt mit der Grenze; keine weitere Entscheidung nötig. Die Zusage „keine Transaktion“ steht bei der Zusage zu `ENVCHANGE` ausdrücklich.

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
| `src/main/kotlin/io/github/pt9912/tdswire/domain/` (`StatementKind`) | update | Mitglieder `CREATE_TABLE`, `ALTER_TABLE`, Transaktionstexte (Typ legt [slice-antwort-tokens-zielbild-luecken-bauen](slice-antwort-tokens-zielbild-luecken-bauen.md) an) |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/` (Sequenzierer, Abbildung Art → Kennung/`COUNT`, `RequestLoop`/Verbindung: Sitzungszustand, `ENVCHANGE` 8/9) | update | Abschluss, Zustand je Sitzung wie die Prepared-Handles |
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/` (Planer, Formen `CREATE TABLE`, `ALTER TABLE`, Form C) | update | Backend meldet die Art |
| `src/integrator/` (Einbettungstest) | update | Backend mit und ohne Art; Anfrage mit zurückgesendetem Deskriptor |
| `spec/spezifikation.md` (`SPEC-035`, `SPEC-036`, `SPEC-031`, `SPEC-030`, `SPEC-059`) | update | Zielbild, Konstanten, Grenzen (ggf. `ENVCHANGE`) |
| `docs/messungen/referenzabgleich.md`, `docs/user/` | update | Zeile M-20 auf „angeglichen“ oder Grenze; Nutzer-Doku |
| Tests (Kotlin, `src/test`; Referenzclient gegen den Serverprozess: `make kompatibilitaet`, kein Gate) | neu / update | je Art und Text; Deskriptor |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) `Accepted` (erfüllt, 2026-11-08); [slice-qa-vergleichslauf-d-messungen-bulk-merge](../done/slice-qa-vergleichslauf-d-messungen-bulk-merge.md) in `done/` (erfüllt); [slice-antwort-tokens-zielbild-luecken-bauen](slice-antwort-tokens-zielbild-luecken-bauen.md) in `done/` (legt `StatementKind` und das Feld an; Setzung nach [ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) „wer zuerst kommt“, hier festgelegt); [slice-katalog-und-struktur-an-messung-angleichen](../next/slice-katalog-und-struktur-an-messung-angleichen.md) in `done/` (gleiche Planer-Dateien); [slice-katalogspalten-smallint-tinyint-angleichen](slice-katalogspalten-smallint-tinyint-angleichen.md) in `done/` (Reihenfolge, Setzung des Planners); ein Verantwortlicher ist eingetragen; ein Messstand für den Vergleichslauf mit d-migrate (Image `ghcr.io/pt9912/d-migrate:latest`, Anleitung im [Messprotokoll Katalog und DDL-Zustand](../../../messungen/katalog-und-ddl-zustand-2026-11-06.md)) ist lauffähig.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte; Schnittvorschlag: Abschluss der Strukturänderung | Transaktionstexte mit `ENVCHANGE`. Die Rückführungen „Fläche wächst“ und „Art im Port braucht eine Entscheidung“ entfallen, sie sind durch [ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) entschieden.
- `in-progress` → `open` (blockiert): der Lauf mit d-migrate ist schon **vor** Punkt 3 nicht reproduzierbar (Vergleichsbasis fehlt) — der Nutzer entscheidet. Ändert der Lauf **nach** Punkt 3 etwas, ist das **keine** Rückführung, sondern der vorentschiedene Rückfall (Punkt 3 mit Grenze).

## 5. Closure-Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle drei Liefer-Punkte der DoD erledigt (Punkt 3 mit `ENVCHANGE` gebaut oder als Grenze mit gemessenem Befund), `make gates` grün (auch `make flaeche`), der Review-Report liegt vor, beide Läufe mit d-migrate (vor und nach Punkt 3) stehen im Slice, die Zeile M-20 ist in der Zuordnung des [Messprotokoll](../../../messungen/referenzabgleich.md#messreihe-zu-massenladen-merge-katalog-und-strukturänderung-messungen-vom-2026-11-07) von „widerspricht“ auf „angeglichen“ (oder „Grenze“) gezogen, die Closure-Notiz trägt den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **`ENVCHANGE` 8 meldet dem Client einen Zustand, den die Bibliothek nicht hat** (keine Transaktion, [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Entscheidung 6); die Reaktion von mssql-jdbc und Referenzclient auf einen Deskriptor des Testservers ist nicht gemessen. Gegenmittel: zuletzt bauen, Lauf vorher/nachher, Test mit zurückgesendetem Deskriptor. — **Ausgang:** offen bis zur Closure.
- **Eine falsche Art vom Backend** liefert die falsche Kennung oder den falschen Zustand an den Client ([TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität)); `SPEC-031` und `SPEC-059` nennen die Grenze. — **Ausgang:** offen bis zur Closure.
- **Der bedingte `COMMIT TRAN` ist nur mit `ENVCHANGE` 9 gemessen**; fällt `ENVCHANGE` zurück, bleibt er `UNKNOWN`. — **Ausgang:** offen bis zur Closure.
- **Einzelmessung** je Instanz mit dem Referenzclient (ein Fall je Text); die Wirkung im Treiber ist nicht gemessen (`AGENTS.md` §3.8). — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — offene Beobachtungen sichten:** [`BEO-TDS/katalog-strukturabweisung-und-aenderungszaehler-weichen-vom-echten-server-ab`](../observations/BEO-TDS/katalog-strukturabweisung-und-aenderungszaehler-weichen-vom-echten-server-ab/state.md), [`BEO-TDS/spec-setzung-widerspricht-messung-am-echten-server`](../observations/BEO-TDS/spec-setzung-widerspricht-messung-am-echten-server/state.md), [`BEO-TDS/messung-am-echten-sql-server-fehlt`](../observations/BEO-TDS/messung-am-echten-sql-server-fehlt/state.md).

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
