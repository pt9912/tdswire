# Slice slice-merge-antwort-an-messung-angleichen: Antwort des `MERGE … OUTPUT $action` an die Messung angleichen: `$action` als `nvarchar(10)`, Abschluss `0x0117`, Fehlernummern 2628, 2627 und 208 (M-15, M-26)

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-testserver-manuell

**Bezug:** [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), [ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md), [ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md)

**Berührte Spec-Stellen:** SPEC-036 (Form M: Typ von `$action`, Abschluss, Fehlernummern) · SPEC-031 (Abschluss der Antwort, `curCmd`) · SPEC-059 (Liste der öffentlichen Namen: `ResultColumn` mit optionalem Längenfeld; `StatementKind.MERGE`; Änderungsprotokoll). **Die Spezifikation ändert dieser Slice** (Zielbild: Wortlaut folgt dem Messwert).

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912 (Entwurf in der Planner-Rolle, aus der Verifikation V-1 von [slice-schreibpfad-merge-output](../done/slice-schreibpfad-merge-output.md)). **Datum:** 2026-11-08.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Die Antwort des Servers auf das `MERGE … OUTPUT $action` von d-migrate 1.12.0 stimmt in den drei Punkten mit dem echten SQL Server (2022 und 2025) überein, in denen der Slice [slice-schreibpfad-merge-output](../done/slice-schreibpfad-merge-output.md) bewusst von der Messung abweicht: die Spalte `$action` ist `nvarchar(10)` (M-26), der Abschluss trägt den Befehl `0x0117` (M-26), und die Fehlerfälle tragen die Nummern 2628, 2627 und 208 (M-15). Die Messung steht im [Messprotokoll](../../../messungen/referenzabgleich.md#messreihe-zu-massenladen-merge-katalog-und-strukturänderung-messungen-vom-2026-11-07); die Spezifikation ist Zielbild, der echte SQL Server die Referenz.

**Herkunft.** Verifikation V-1 von [slice-schreibpfad-merge-output](../done/slice-schreibpfad-merge-output.md): drei gemessene Abweichungen ohne Lieferpunkt in einem Slice von `main`. Register [`BEO-TDS/zielbild-luecken-der-antwort-tokens`](../observations/BEO-TDS/zielbild-luecken-der-antwort-tokens/state.md). Nutzerentscheid 2026-11-07 (Spezifikation ist Zielbild) und 2026-11-08 (Option A von [ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) und [ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md): die öffentliche Fläche wächst bewusst).

**Reihenfolge.** Dieser Slice folgt [slice-antwort-tokens-zielbild-luecken-bauen](../done/slice-antwort-tokens-zielbild-luecken-bauen.md), das `StatementKind` anlegt ([ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md)); dieser Slice fügt das Mitglied `MERGE` hinzu und setzt es im Pfad der Form M.

**Entscheid vorab (Rückführung an Architect und Nutzer).** Liefer-Punkt (b) ist ein neues Feld an einem öffentlichen Typ (`ResultColumn`, [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 4) und folgt der Linie von [ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) („Fläche wächst bewusst“). Empfehlung des Planners: **ja**, als optionales Längenfeld mit Vorgabewert (kein Bruch für bestehenden Quelltext). Der Slice startet erst, wenn der Nutzer entschieden hat; bei „nein“ bleibt (b) eine benannte Grenze in `SPEC-036` und der Slice schrumpft auf (a) und (c).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Die Zuordnung der Fehlercodes je Fehlerursache **des Massenladens** (Schlüsselverstoß, Typfehler, NULL) — [slice-adr-massenladen-atomar-an-messung](../done/slice-adr-massenladen-atomar-an-messung.md). Ein Punkt gehört genau einem Slice: die Codes 2628, 2627, 208 **des `MERGE`** gehören diesem Slice, die des Massenladens (M-11, M-12) jenem.
- Die Anweisungsart der übrigen Anweisungen (`INSERT`, `DELETE`, `UPDATE`, `UNKNOWN`) und das Anlegen von `StatementKind` — [slice-antwort-tokens-zielbild-luecken-bauen](../done/slice-antwort-tokens-zielbild-luecken-bauen.md).
- Ein `MERGE` gegen eine Tabelle ohne Schlüssel: bleibt `TDS_UNSUPPORTED_SQL` (siehe §6); der Fall braucht zuerst `CREATE TABLE` ohne Schlüssel aus [slice-katalog-und-struktur-an-messung-angleichen](../done/slice-katalog-und-struktur-an-messung-angleichen.md).
- Eine Aussage über andere Clients als den Referenzclient und mssql-jdbc 13.4.0 (`AGENTS.md` §3.8).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **(a) Befehl `0x0117` des Abschlusses** ([TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md)): `StatementKind.MERGE` (Befehl `0x0117`, M-26) ist Mitglied von `StatementKind`; die Form M setzt es im Abschluss der Antwort; `SPEC-031`, `SPEC-036` und `SPEC-059` (Liste, Änderungsprotokoll) nennen es; Test gegen die Tokenfolge; die Bytes des Abschlusses stimmen mit M-26 überein.
- [ ] **(b) `$action` als `nvarchar(10)`** ([TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 4; Entscheid des Nutzers vorab, siehe §1): optionales Längenfeld an `ResultColumn` (Vorgabewert: die Länge, die `SPEC-003` für den Typ nennt); die Form M meldet für `$action` die Länge 10; `make flaeche` grün, `SPEC-059` nennt das Feld; der Einbettungstest `integrator` meldet es; Test gegen das COLMETADATA (Typ, Länge, Nullbarkeit) gegen M-26.
- [ ] **(c) Fehlernummern des `MERGE`** ([TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)): Text länger als die Spalte → 2628, derselbe Schlüssel zweimal in der Quelle → 2627, Tabelle unbekannt → 208 (M-15; Zuordnung je Ursache; die Bibliothek sendet heute `TDS_BACKEND_ERROR`); `SPEC-036` (Fehlercode-Tabelle) nennt sie; ein Test je Nummer; wo der echte Server vor dem Fehler Zeilen sendet (Schlüssel doppelt: die erste Zeile `INSERT` kommt vor dem Fehler, M-15), folgt die Reihenfolge der Messung oder die Abweichung ist benannt.

Nachweis-Pflichten für alle Punkte:

- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Haken der Liefer-Punkte setzt der Planner nach der Verifikation, nicht der Implementer (`BEO-TDS/haken-vor-verifikation-gesetzt`).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben oder „keine Beobachtung angefallen“ in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/domain/QueryEvent.kt` (`ResultColumn.maxLength`, `ResultStart.kind`) | update | optionales Längenfeld (Entscheid des Nutzers 2026-11-08) und die Art am Beginn des Ergebnisses: der Fehler im offenen Ergebnis kennt die Art nur dort, `UpdateCount.kind` ([ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md)) reicht für ein Ergebnis nicht; **zweite Erweiterung der Fläche, vom Nutzer nicht ausdrücklich entschieden (Bericht)** |
| `src/main/kotlin/io/github/pt9912/tdswire/domain/StatementKind.kt` | update | Mitglied `MERGE` |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/token/Token.kt` (`DoneCommand`) | update | `0x0117`, `forResult`, `countsRows` |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/response/` (`Completion.kt`, `ResponseRun.kt`, `RpcTrailer.kt`) | update | Kennung des Abschlusses und des Fehlers im Ergebnis nach der Art, INFO 3621 nach dem Fehler im Ergebnis eines `MERGE` |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/result/ColumnMapping.kt` | update | `NVARCHAR(maxLength)` statt fest 4000 |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/error/` (`ErrorCode.kt`, `ErrorMapper.kt`) | update | neuer Code `TDS_SIM_STRING_TRUNCATED` (2628) und seine Aufnahme in die Positivliste |
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/` (`SimulationMerges.kt`, `StatementPlanner.kt`) | update | `$action` mit Länge 10 und Art `MERGE`; Prüfung der Textlänge je Aktion (2628); Fehler von `onInsert` als 2627 |
| `src/integrator/` (`MergeEmbeddingTest.kt`, `StatementKindEmbeddingTest.kt`) | update | Beleg der Felder, Bytes der Spalte und des Abschlusses, Fehler 2628, 2627, 208 |
| `src/test/` (`ResponseMergeTest.kt` neu, `SimulationBackendMergeTest.kt`, `ErrorMapperTest.kt`) | neu / update | Tokenfolge gegen die Bytes der Messung, Zuordnung der Fehler, Codeliste |
| `spec/spezifikation.md` (`SPEC-003`, `SPEC-022`, `SPEC-031`, `SPEC-035`, `SPEC-036`, Abschnitt 4, `SPEC-059`, Änderungsprotokoll) | update | Zielbild, Fläche, Fehlertabelle |
| `docs/messungen/referenzabgleich.md` | update | Zeilen M-15 und M-26 der Zuordnung auf „angeglichen“ mit den Grenzen |
| `docs/plan/kompatibilitaetsmatrix.md` | update | Zeilen Befehlskennung, `UnicodeText`, Fehlerantwort; neue Zitate von `ResponseMergeTest` |
| `docs/user/` (`einbettung-und-betrieb.md`, `referenz-konfiguration.md`, `testserver-manuell.md`) | update | Felder, Antwortform, Fehler; `README.md` und `referenz-ereignisse.md` gelesen, unverändert |

Die Liste ist im Lauf des Implementers gegen den Diff vervollständigt.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-schreibpfad-merge-output](../done/slice-schreibpfad-merge-output.md) in `done/` (Form M besteht); [slice-antwort-tokens-zielbild-luecken-bauen](../done/slice-antwort-tokens-zielbild-luecken-bauen.md) in `done/` (legt `StatementKind` an, [ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md)); [ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) `Accepted` (erfüllt, 2026-11-08); der Nutzer hat über das Längenfeld an `ResultColumn` entschieden (Liefer-Punkt b); kein anderer Bau-Slice in `in-progress/` (WIP 1).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): die Fehlernummern brauchen eine eigene Fehlerabbildung je Ursache; Schnittvorschlag: (a) und (b) | (c).
- `in-progress` → `open` (blockiert): der Nutzer entscheidet gegen das Längenfeld; dann entfällt (b) (Grenze in `SPEC-036`), der Rest läuft weiter.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln.

Alle Liefer-Punkte der DoD erledigt, `make gates` grün (auch `make flaeche`), der Review-Report liegt vor, die Zeilen M-15 und M-26 des Messprotokolls sind von „widerspricht“ auf „angeglichen“ gezogen (oder (b) als Grenze benannt), die Closure-Notiz trägt den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang.

- **Entscheid zu `ResultColumn` steht aus** (öffentliche Fläche, Empfehlung ja). — **Ausgang:** offen bis zur Closure.
- **`MERGE` auf eine Tabelle ohne Schlüssel** endet mit `TDS_UNSUPPORTED_SQL` (`comparesKey` verlangt den Schlüssel), am echten Server ist das `MERGE` möglich; `CREATE TABLE` ohne Schlüssel gelingt nach [slice-katalog-und-struktur-an-messung-angleichen](../done/slice-katalog-und-struktur-an-messung-angleichen.md) (M-19). — **Ausgang:** offen bis zur Closure.
- **Reihenfolge der Antwort im Fehlerfall** (Zeile vor dem Fehler, M-15) hängt am Zerlegen in Lesen und einen Callback; ob sie ohne Umbau der Form M zu erreichen ist, ist nicht gemessen. — **Ausgang:** offen bis zur Closure.

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register · `grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln.

Wird bei der Closure gefüllt.

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung.

**Vorgelagert — Sub-Area-Wahl prüfen:** `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); eine eigene Sub-Area besteht nicht.

**Vorgelagert — offene Beobachtungen sichten:** [`BEO-TDS/zielbild-luecken-der-antwort-tokens`](../observations/BEO-TDS/zielbild-luecken-der-antwort-tokens/state.md), [`BEO-TDS/spec-setzung-widerspricht-messung-am-echten-server`](../observations/BEO-TDS/spec-setzung-widerspricht-messung-am-echten-server/state.md).

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
