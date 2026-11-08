# Slice slice-katalog-und-struktur-an-messung-angleichen: Strukturabweisung (2714, 2705, 4902) und `CREATE TABLE` ohne Schlüssel an die Messung am echten SQL Server angleichen (M-19)

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-testserver-manuell

**Bezug:** [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md)

**Berührte Spec-Stellen:** SPEC-035 (Strukturabweisung mit Fehlernummern; Validierung der Tabellenbeschreibung ohne Schlüssel) · SPEC-036 (Form D: `CREATE TABLE` ohne Schlüssel nicht mehr abgewiesen) · Abschnitt Fehlercodes (drei Codes der Strukturabweisung). **Die Spezifikation ändert dieser Slice** (Spezifikation ist Zielbild: der Wortlaut folgt dem Messwert, die „bekannte Abweichung“ und die Messvermerke zu M-19 entfallen oder werden zur Zusage). Keine Änderung an `SPEC-059`, `SPEC-003`, `SPEC-031`.

**Verantwortlich:** Implementer-Agent (Auftrag des Nutzers 2026-11-07); Bau liegt im Zweig `worktree-agent-a95b80c5a5d35df3e` (HEAD `85d970bd`).

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-07 (Zuschnitt 2026-11-08)

---

## 1. Ziel und Abgrenzung


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Zuschnitt vom 2026-11-08 (Planner, nach der Annahme von [ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) und [ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md)).** Der Slice war mit drei Liefer-Punkten angelegt (Katalogspalten, Strukturabweisung, Änderungszähler) und ist nach den beiden Entscheidungen in drei Slices zerlegt, jeder mit höchstens drei Liefer-Punkten: [slice-katalog-und-struktur-an-messung-angleichen](slice-katalog-und-struktur-an-messung-angleichen.md) (Strukturabweisung, `CREATE TABLE` ohne Schlüssel), [slice-katalogspalten-smallint-tinyint-angleichen](slice-katalogspalten-smallint-tinyint-angleichen.md) (Katalogspalten, [ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md)) und [slice-ddl-abschluss-und-transaktionszustand-angleichen](slice-ddl-abschluss-und-transaktionszustand-angleichen.md) (Abschluss der Strukturänderung, Transaktionstexte, `ENVCHANGE`, [ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md)). Ein Punkt gehört genau einem Slice; die Anweisungsart am Port und die Kennungen für INSERT, UPDATE und DELETE gehören [slice-antwort-tokens-zielbild-luecken-bauen](slice-antwort-tokens-zielbild-luecken-bauen.md) (dieser Slice legt `StatementKind` und das Feld `kind` an).

**Ziel:** Der Server dieses Repos weist `CREATE TABLE` einer vorhandenen Tabelle, `ALTER TABLE … ADD` einer vorhandenen Spalte und `ALTER TABLE` einer unbekannten Tabelle so ab, wie der echte SQL Server (2022 und 2025) es nach der Zeile M-19 des [Messprotokoll](../../../messungen/referenzabgleich.md#messreihe-zu-massenladen-merge-katalog-und-strukturänderung-messungen-vom-2026-11-07) tut: Fehlernummern 2714 (State 6), 2705 (State 4) und 4902 (State 1), Class 16, die Verbindung bleibt offen; `CREATE TABLE` ohne Schlüssel gelingt.

**Herkunft.** Aus der Zuordnung der Befunde im [Messprotokoll](../../../messungen/referenzabgleich.md#messreihe-zu-massenladen-merge-katalog-und-strukturänderung-messungen-vom-2026-11-07) (M-19 widerspricht der Setzung der Spezifikation und dem Bau von [slice-katalog-und-ddl-zustand](../done/slice-katalog-und-ddl-zustand.md); Verifikation V-1 dieses Slice). Träger-Register: [`BEO-TDS/katalog-strukturabweisung-und-aenderungszaehler-weichen-vom-echten-server-ab`](../observations/BEO-TDS/katalog-strukturabweisung-und-aenderungszaehler-weichen-vom-echten-server-ab/state.md). Nutzerentscheid 2026-11-07 (per Rückfrage an den Nutzer, Beleg im Register-Eintrag dort): Strukturabweisung mit 2714, 2705, 4902; `CREATE TABLE` ohne Schlüssel erlaubt. Die Spezifikation ist Zielbild, der echte SQL Server die Referenz (Nutzergrundsatz 2026-11-06). Die Messung [slice-qa-vergleichslauf-d-messungen-bulk-merge](../done/slice-qa-vergleichslauf-d-messungen-bulk-merge.md) liegt vor (Einzelmessungen je Instanz 2022 und 2025, Referenzclient `Microsoft.Data.SqlClient`, nicht mssql-jdbc von d-migrate).

**Stand der Arbeit (2026-11-08).** Beide Liefer-Punkte sind im Zweig `worktree-agent-a95b80c5a5d35df3e` (Worktree `.claude/worktrees/agent-a95b80c5a5d35df3e`, HEAD `85d970bd`) gebaut: die drei Codes `TDS_SIM_OBJECT_EXISTS` (2714, State 6), `TDS_SIM_COLUMN_EXISTS` (2705, State 4) und `TDS_SIM_OBJECT_NOT_FOUND` (4902, State 1) mit Class 16 und offener Verbindung, fester Text ohne Objektnamen; `SimTable` verlangt keinen Schlüssel mehr; Spezifikation, Nutzer-Doku und Tests nachgezogen; `make gates` grün. **Nicht gefahren:** `make clean-build` und die Läufe mit d-migrate; Review und Verifikation stehen aus. Der Weg auf `main` steht in §4.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Die Katalogspalten `max_length` (`smallint`), `precision` und `scale` (`tinyint`), `is_nullable` (`BITN`) — [slice-katalogspalten-smallint-tinyint-angleichen](slice-katalogspalten-smallint-tinyint-angleichen.md): braucht die öffentlichen Mitglieder `SqlType.Int16` und `SqlType.UInt8` ([ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md)), eine breite Änderung der Auswerter von `SqlType`; hier würde sie den bereits gebauten und vor dem Review stehenden Teil aufhalten.
- Der Abschluss von `CREATE TABLE` und `ALTER TABLE … ADD` als `DONE` ohne `COUNT` mit Befehl `0x00c6` bzw. `0x00d8`, die Transaktionstexte der Form C und `ENVCHANGE` Typ 8 und 9 — [slice-ddl-abschluss-und-transaktionszustand-angleichen](slice-ddl-abschluss-und-transaktionszustand-angleichen.md): braucht `StatementKind` am Port ([ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md)), den [slice-antwort-tokens-zielbild-luecken-bauen](slice-antwort-tokens-zielbild-luecken-bauen.md) anlegt.
- `curCmd` von INSERT, DELETE und UPDATE, die Fehlerabbildung von `sp_executesql` und der Abschluss im RPC — [slice-antwort-tokens-zielbild-luecken-bauen](slice-antwort-tokens-zielbild-luecken-bauen.md).
- Die Sichten für Indizes, Standardwerte, Prüfbedingungen und Fremdschlüssel sowie weitere DDL-Formen — [slice-katalog-indizes-defaults-fremdschluessel](slice-katalog-indizes-defaults-fremdschluessel.md); gemessen ist nur, was die Messreihe trägt.
- Eine Aussage über andere Clients als den Referenzclient: Die Texte sind der Wortlaut von d-migrate (`DMigrateTexte.cs`), gemessen ist die Antwort des Servers auf den Text, nicht das Verhalten von d-migrate oder mssql-jdbc (`AGENTS.md` §3.8).

## 2. Definition of Done


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (zwei; gebaut im Zweig, Haken setzt der Planner nach der Verifikation):

- [ ] **Strukturabweisung** ([TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)): `CREATE TABLE` einer vorhandenen Tabelle ergibt 2714 (Class 16, State 6), `ADD` einer vorhandenen Spalte 2705 (State 4), `ALTER TABLE` einer unbekannten Tabelle 4902 (State 1); die Verbindung bleibt nach dem Fehler offen (M-19); `SPEC-035`, `SPEC-036` und die Fehlertabelle sind angeglichen; Tests je Fall; `ADD` mehrerer Spalten bleibt unverändert (M-19 stützt es).
- [ ] **`CREATE TABLE` ohne Schlüssel** ([TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)): `CREATE TABLE` ohne Schlüssel gelingt (die Abweisung nach Form D entfällt, `SimTable` und `TableStore` verlangen und prüfen keinen Schlüssel mehr, eine Tabelle ohne Schlüssel prüft keine Eindeutigkeit); `SPEC-035` (Validierung) und `SPEC-036` (Form D) sind angeglichen; Test je Form (Anlegen, Einfügen, Katalogabfrage der Tabelle ohne Schlüssel).

**Fläche:** keine neue öffentliche Fläche (die drei Codes sind intern, `make flaeche` unverändert); die Rückführung „Fläche wächst“ des früheren Plans entfällt für diesen Zuschnitt.

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
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/error/ErrorCode.kt`, `ErrorMapper.kt` | update | drei Codes für 2714, 2705, 4902 (intern, `BACKEND_CODES`) |
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/StatementPlanner.kt`, `SimModel.kt` | update | Codes der Abweisung; `CREATE TABLE` ohne Schlüssel, `SimTable` mit leerem Schlüssel (kein neuer Name, gelockerte Prüfung) |
| `testserver/kotlin/io/github/pt9912/tdswire/testserver/TableStore.kt` | update | Tabelle ohne Schlüssel prüft keine Eindeutigkeit |
| `spec/spezifikation.md` (`SPEC-035`, `SPEC-036`, Fehlertabelle) | update | Zielbild: Wortlaut folgt dem Messwert |
| `docs/user/einbettung-und-betrieb.md`, `docs/user/referenz-konfiguration.md`, `docs/messungen/referenzabgleich.md` | update | Fehlercodes, Schlüssel, Zuordnung M-19 auf „angeglichen“ (M-16 und M-20 bleiben bei ihren Slices) |
| Tests (Kotlin, `src/test`, `testserver/kotlin`) | neu / update | Happy/Boundary/Negative nach [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate) |

Die Liste ist der Stand des Zweigs; ein Reviewer liest sie gegen den Diff.

## 4. Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): erfüllt und verbraucht — der Bau lief (Zweig `worktree-agent-a95b80c5a5d35df3e`); [slice-qa-vergleichslauf-d-messungen-bulk-merge](../done/slice-qa-vergleichslauf-d-messungen-bulk-merge.md) und [slice-katalog-und-ddl-zustand](../done/slice-katalog-und-ddl-zustand.md) liegen in `done/`. Die Zeile bleibt als Beleg, kein offener Trigger.

**Weg auf `main` (Setzung des Planners, 2026-11-08).** Der Zweig trägt den gebauten Teil und bleibt bestehen (nicht gelöscht, nicht neu aufgesetzt). In dieser Reihenfolge, jede Rolle in frischem Kontext:

1. **Reviewer** (`.harness/skills/reviewer.md`) prüft den Diff des Zweigs gegen diesen verkleinerten Plan (zwei Liefer-Punkte), schreibt den Report unter `docs/reviews/` (im Zweig).
2. **Verifier** prüft gegen DoD und Spec und fährt, was bisher nicht lief: `make clean-build` und die Läufe mit d-migrate (`schema migrate --execute` mit einem Soll mit und ohne Schlüssel, `compare`) nach der Anleitung im [Messprotokoll Katalog und DDL-Zustand](../../../messungen/katalog-und-ddl-zustand-2026-11-06.md); ein nicht gefahrener Lauf ist eine benannte Grenze, kein stilles Grün.
3. **Planner-Closure** im Zweig, nachdem der Planner `main` in den Zweig geführt hat (`git merge main` dort; erwartete Konflikte: diese Datei — der Zweig trägt sie in `next/` bzw. `in-progress/` mit dem alten Text, `main` in `open/` mit dem verkleinerten —, das Register, die Welle-Datei und die Roadmap; **bei der Slice-Datei gilt der Inhalt von `main`** samt dem Abschnitt „Stand der Arbeit“, die Position folgt dem Lifecycle-Stand, ein Move ist ein eigener Commit). Dann: Haken nach Deckung, §7 mit Steering-Loop-Lerneintrag, Register, Zuordnungszeile M-19; erst der Inhalt, dann der reine `git mv` nach `done/` (`AGENTS.md` §3.3).
4. **Zusammenführung nach `main`** durch den Auftraggeber; dieser Plan und diese Closure führen sie nicht aus. Danach liegt der Slice in `done/` und die drei Folge-Slices ([slice-katalogspalten-smallint-tinyint-angleichen](slice-katalogspalten-smallint-tinyint-angleichen.md), [slice-ddl-abschluss-und-transaktionszustand-angleichen](slice-ddl-abschluss-und-transaktionszustand-angleichen.md), [slice-antwort-tokens-zielbild-luecken-bauen](slice-antwort-tokens-zielbild-luecken-bauen.md)) sehen `CREATE TABLE` ohne Schlüssel und die Codes im Bestand.

**Konflikte mit anderen Zweigen.** Der Zweig ändert `StatementPlanner.kt`, `SimModel.kt`, `ErrorCode.kt`, `ErrorMapper.kt` und `TableStore.kt`; die parallelen Zweige zu [slice-bulk-load-feste-typen-und-referenzclient](slice-bulk-load-feste-typen-und-referenzclient.md), [slice-schreibpfad-merge-output](slice-schreibpfad-merge-output.md) und [slice-sp-unprepare-ohne-obergrenze](slice-sp-unprepare-ohne-obergrenze.md) berühren Teile davon. Wer nach `main` kommt, wird zuerst zusammengeführt; wer zweiter ist, löst die Konflikte (Setzung: die Reihenfolge entscheidet der Auftraggeber beim Zusammenführen, der Zweig dieses Slice ist der kleinste Diff der vier).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `open` (blockiert): die Läufe mit d-migrate zeigen eine Änderung durch die schlüssellose Tabelle oder die neuen Fehlernummern (ob d-migrate Fehlernummern auswertet, ist nicht gemessen); der Nutzer entscheidet dann über den Weg.
- Zu groß entfällt: zwei Liefer-Punkte, keine Fläche.

## 5. Closure-Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Beide Liefer-Punkte der DoD erledigt, `make gates` und `make clean-build` grün, der Review-Report liegt vor, der Verifier-Bericht liegt vor und nennt die Läufe mit d-migrate (gefahren, Ergebnis unverändert gegenüber dem Lauf davor; oder als Grenze benannt), die Zeilen M-19 sind in der Zuordnung des [Messprotokoll](../../../messungen/referenzabgleich.md#messreihe-zu-massenladen-merge-katalog-und-strukturänderung-messungen-vom-2026-11-07) von „widerspricht“ auf „angeglichen“ gezogen (die Zeilen M-16 und M-20 bleiben, sie gehören [slice-katalogspalten-smallint-tinyint-angleichen](slice-katalogspalten-smallint-tinyint-angleichen.md) und [slice-ddl-abschluss-und-transaktionszustand-angleichen](slice-ddl-abschluss-und-transaktionszustand-angleichen.md)), die Closure-Notiz trägt den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **Auswirkung auf den Beleg am fremden Client.** ob die neuen Nummern 2714, 2705, 4902 und schlüssellose Tabellen etwas ändern, ist nicht gemessen (nur Läufe mit den alten Antworten sind belegt). Die Verifikation fährt den Lauf (§4 Schritt 2); [slice-abnahme-d-migrate](slice-abnahme-d-migrate.md) prüft es am Ende erneut. — **Ausgang:** offen bis zur Closure.
- **Nicht gefahren im Bau:** `make clean-build` und die Läufe mit d-migrate; die grünen Gates des Zweigs ersetzen sie nicht. — **Ausgang:** offen bis zur Closure.
- **Dateikonflikte mit den parallelen Zweigen** (Massenladen mit festen Typen, MERGE, `sp_unprepare`; gemeinsame Dateien in `simulation/` und `testserver/`). — **Ausgang:** offen bis zur Closure.
- Die Messung trägt den Referenzclient, nicht mssql-jdbc; die Antworten auf die Texte von d-migrate sind gemessen, ihre Wirkung im Treiber nicht (`AGENTS.md` §3.8). — **Ausgang:** offen bis zur Closure.
- **`CREATE TABLE` ohne Schlüssel und `MERGE`** (Verifikation V-9 von [slice-schreibpfad-merge-output](../done/slice-schreibpfad-merge-output.md)): nach diesem Slice gelingt `CREATE TABLE` ohne Schlüssel; ein `MERGE` gegen eine solche Tabelle endet im Testserver mit `TDS_UNSUPPORTED_SQL` (`comparesKey` verlangt den Schlüssel), am echten Server ist es möglich. Der Slice benennt den Fall (Test oder Grenze in `SPEC-036`) und entscheidet, ob die Abweisung Setzung bleibt oder angeglichen wird; Register [`BEO-TDS/katalog-strukturabweisung-und-aenderungszaehler-weichen-vom-echten-server-ab`](../observations/BEO-TDS/katalog-strukturabweisung-und-aenderungszaehler-weichen-vom-echten-server-ab/state.md). — **Ausgang:** offen bis zur Closure.

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
