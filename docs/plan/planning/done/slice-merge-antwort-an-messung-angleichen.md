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

**Berührte Spec-Stellen:** SPEC-036 (Form M: Typ von `$action`, Abschluss, Fehlernummern) · SPEC-031 (Abschluss der Antwort, `curCmd`) · SPEC-059 (Liste der öffentlichen Namen: `ResultColumn` mit optionalem Längenfeld, `ResultStart.kind`; `StatementKind.MERGE`; Änderungsprotokoll) · SPEC-002 (Query-Vertrag: `ResultStart`, `ResultColumn`, `StatementKind`, Absatz zu `kind` und `maxLength`) · SPEC-003 (Typ `UnicodeText` und `maxLength`) · SPEC-035 (Prüfung von `SimColumn.maxLength` beim `MERGE`) · Vertrag zu SPEC-022 · [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend).a Schritt 11. **Die Spezifikation ändert dieser Slice** (Zielbild: Wortlaut folgt dem Messwert).

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

**Entscheid vorab (Rückführung an Architect und Nutzer).** Liefer-Punkt (b) ist ein neues Feld an einem öffentlichen Typ (`ResultColumn`, [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 4) und folgt der Linie von [ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) („Fläche wächst bewusst“). Empfehlung des Planners: **ja**, als optionales Längenfeld mit Vorgabewert (kein Bruch für bestehenden Quelltext). Entschieden: der Nutzer hat am 2026-11-08 das Längenfeld bejaht (Entscheid des Nutzers); bei „nein“ wäre (b) eine benannte Grenze in `SPEC-036` geblieben.

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

- [x] **(a) Befehl `0x0117` des Abschlusses** ([TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md)): `StatementKind.MERGE` (Befehl `0x0117`, M-26) ist Mitglied von `StatementKind`; die Form M setzt es im Abschluss der Antwort; `SPEC-031`, `SPEC-036` und `SPEC-059` (Liste, Änderungsprotokoll) nennen es; Test gegen die Tokenfolge; die Bytes des Abschlusses stimmen mit M-26 überein.
- [x] **(b) `$action` als `nvarchar(10)`** ([TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 4; Entscheid des Nutzers vorab, siehe §1): optionales Längenfeld an `ResultColumn` (Vorgabewert: die Länge, die `SPEC-003` für den Typ nennt); die Form M meldet für `$action` die Länge 10; `make flaeche` grün, `SPEC-059` nennt das Feld; der Einbettungstest `integrator` meldet es; Test gegen das COLMETADATA (Typ, Länge, Nullbarkeit) gegen M-26.
- [x] **(c) Fehlernummern des `MERGE`** (Wortlaut in der Closure berichtigt, siehe §7) ([TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)): Text länger als die Spalte → 2628, derselbe Schlüssel zweimal in der Quelle → 2627, Tabelle unbekannt → 208 (M-15; Zuordnung je Ursache; die Bibliothek sendet heute `TDS_BACKEND_ERROR`); `SPEC-036` (Fehlercode-Tabelle) nennt sie; ein Test je Nummer; wo der echte Server vor dem Fehler Zeilen sendet (Schlüssel doppelt: die erste Zeile `INSERT` kommt vor dem Fehler, M-15), folgt die Reihenfolge der Messung oder die Abweichung ist benannt.

Nachweis-Pflichten für alle Punkte:

- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Haken der Liefer-Punkte setzt der Planner nach der Verifikation, nicht der Implementer (`BEO-TDS/haken-vor-verifikation-gesetzt`).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben oder „keine Beobachtung angefallen“ in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/domain/QueryEvent.kt` (`ResultColumn.maxLength`, `ResultStart.kind`) | update | optionales Längenfeld (Entscheid des Nutzers 2026-11-08) und die Art am Beginn des Ergebnisses: der Fehler im offenen Ergebnis kennt die Art nur dort, `UpdateCount.kind` ([ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md)) reicht für ein Ergebnis nicht; **zweite Erweiterung der Fläche; Entscheid des Nutzers am 2026-11-09 per Auswahl: das Feld bleibt in der öffentlichen Fläche** (Begründung: der Fehler im offenen Ergebnis braucht die Art am Ergebnisbeginn, `0x0117` für den Fehler M-15; keine Änderung der angenommenen [ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md), Klarstellung in `SPEC-002` und im Änderungsprotokoll von `SPEC-059`) |
| `src/main/kotlin/io/github/pt9912/tdswire/domain/StatementKind.kt` | update | Mitglied `MERGE` |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/token/Token.kt` (`DoneCommand`) | update | `0x0117`, `forResult`, `countsRows` |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/response/` (`Completion.kt`, `ResponseRun.kt`, `RpcTrailer.kt`) | update | Kennung des Abschlusses und des Fehlers im Ergebnis nach der Art, INFO 3621 nach dem Fehler im Ergebnis eines `MERGE` |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/result/ColumnMapping.kt` | update | `NVARCHAR(maxLength)` statt fest 4000 |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/error/` (`ErrorCode.kt`, `ErrorMapper.kt`) | update | neuer Code `TDS_SIM_STRING_TRUNCATED` (2628) und seine Aufnahme in die Positivliste |
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/` (`SimulationMerges.kt`, `StatementPlanner.kt`, `SimModel.kt`) | update | `$action` mit Länge 10 und Art `MERGE`; Prüfung der Textlänge je Aktion (2628); Fehler von `onInsert` als 2627; KDoc von `SimColumn.maxLength` nach der Prüfung beim `MERGE` |
| `src/integrator/` (`MergeEmbeddingTest.kt`, `StatementKindEmbeddingTest.kt`, `RpcParams.kt`; `MergeFehlerEmbeddingTest.kt` neu) | update / neu | Beleg der Felder, Bytes der Spalte und des Abschlusses, Fehler 2628, 2627, 208 |
| `src/test/` (`ResponseMergeTest.kt` neu, `SimulationBackendMergeTest.kt`, `ErrorMapperTest.kt`) | neu / update | Tokenfolge gegen die Bytes der Messung, Zuordnung der Fehler, Codeliste |
| `spec/spezifikation.md` (`SPEC-002`, `SPEC-003`, `SPEC-022`, `SPEC-031`, `SPEC-035`, `SPEC-036`, Abschnitt 4, `SPEC-059`, `TDS-FA-11.a` Schritt 11, Änderungsprotokoll) | update | Zielbild, Fläche, Fehlertabelle; Klarstellung zu `maxLength` (Zeile über der Länge wird abgewiesen) und `ResultStart.kind` |
| `docs/plan/planning/in-progress/roadmap.md`, `docs/plan/planning/welle-testserver-manuell.md`, `docs/plan/spec-abdeckung.md`, `docs/messungen/merge-output-2026-11-07.md` | update | Wellenzeile und Zustandsfeld, Link-Nachzug, Abdeckungsmatrix (erzeugt, `make spec-abdeckung`), Link auf diesen Slice |
| `docs/plan/planning/done/` (fünf Slices) und `docs/plan/planning/observations/BEO-TDS/` (Evidence, `state.md`) | update | Link-Nachzüge auf diesen Slice; Stand der Register |
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

- **Entscheid zu `ResultColumn`** (öffentliche Fläche): entschieden, Entscheid des Nutzers am 2026-11-08 (ja); ebenso `ResultStart.kind`, Entscheid des Nutzers am 2026-11-09 per Auswahl (das Feld bleibt). — **Ausgang:** entfallen (entschieden).
- **`MERGE` auf eine Tabelle ohne Schlüssel** endet mit `TDS_UNSUPPORTED_SQL` (`comparesKey` verlangt den Schlüssel), am echten Server ist das `MERGE` möglich; `CREATE TABLE` ohne Schlüssel gelingt nach [slice-katalog-und-struktur-an-messung-angleichen](../done/slice-katalog-und-struktur-an-messung-angleichen.md) (M-19). — **Ausgang:** weiter offen → [`BEO-TDS/merge-antwort-grenzen-der-angleichung`](../observations/BEO-TDS/merge-antwort-grenzen-der-angleichung/state.md) (zusätzlich die Grenze in `SPEC-036`).
- **Reihenfolge der Antwort im Fehlerfall** (Zeile vor dem Fehler, M-15) hängt am Zerlegen in Lesen und einen Callback; ob sie ohne Umbau der Form M zu erreichen ist, ist nicht gemessen. — **Ausgang:** weiter offen → [`BEO-TDS/merge-antwort-grenzen-der-angleichung`](../observations/BEO-TDS/merge-antwort-grenzen-der-angleichung/state.md) (zusätzlich die Grenze in `SPEC-036`).

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register · `grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln.

- **Gegenstand:** Die Antwort des `MERGE … OUTPUT $action` stimmt in den drei Punkten mit dem echten SQL Server überein, soweit unten als gemessen geführt: `$action` ist `nvarchar(10)` (optionales Längenfeld `ResultColumn.maxLength`, Vorgabe 4000), `StatementKind.MERGE` setzt den Befehl `0x0117` im Abschluss (auch des Fehlers; dazu `ResultStart.kind`), Fehlernummern 2628 (`TDS_SIM_STRING_TRUNCATED`, neuer Code), 2627 (Sammelcode für Fehler von `onInsert`) und 208. Spezifikation (`SPEC-002`, `SPEC-003`, `SPEC-022`, `SPEC-031`, `SPEC-035`, `SPEC-036`, `SPEC-059` samt Änderungsprotokoll, Schritt 11 von `TDS-FA-11.a`), Nutzer-Doku, Messprotokolle und Matrix nachgezogen. `make flaeche` grün.
- **Zuordnung Messung | Festlegung (Closure-Trigger §5):** *Gemessen* (Rohbytes beider Instanzen 2022 und 2025, vom Verifier selbst dekodiert, M-15 und M-26): Spalte `$action` Typ NVARCHAR, 10 Zeichen, nicht nullbar; DONEINPROC Status `0x0011` mit Befehl `0x0117`, Zähler auch ohne Zeile (0); Fehler-DONE Status `0x0003` mit Befehl `0x0117` ohne COUNT; INFO 3621 nach ERROR 2628 und 2627 vor dem Abschluss; 2628 Class 16 State 1; 2627 Class 14 State 1; 208 Class 16 State 1 mit der Folge ERROR, RETURNSTATUS, DONEPROC (Fehlerbit), ohne COLMETADATA. *Festlegung (Setzung, gekennzeichnet in `SPEC-031`, `SPEC-035`, `SPEC-036`):* 2627 als Sammelcode für jeden Fehler von `onInsert`; Prüfung der Textlänge je ausgeführter Aktion (`skip` auf vorhandenem Schlüssel mit langem Text bleibt ohne Fehler, am Server nicht gemessen); kein INFO bei einem Fehler der Klasse `STATEMENT` im Ergebnis; `UpdateCount(n, MERGE)` schließt mit `0x0117` und COUNT; Sperre je Backend; Class 20 und schließende Verbindung bei 208 (**Abweichung** vom Server: Class 16, offen).
- **Einordnung des DoD-Wortlauts von (c) (B1, Verifikation V-1; Entscheid des Nutzers als Standardantwort, 2026-11-09, umkehrbar):** Der Wortlaut „derselbe Schlüssel zweimal in der Quelle → 2627“ und die Erlaubnis „oder die Abweichung ist benannt“ nur für die **Reihenfolge** sind in dieser Fassung nicht erfüllt und hiermit **berichtigt**: erfüllt ist 2627 nur als Sammelcode für Fehler von `onInsert`. Zwei `VALUES`-Zeilen mit demselben Schlüssel sind nicht Form M und bleiben `TDS_UNSUPPORTED_SQL`; die Zeile `INSERT` vor dem Fehler (M-15) gibt es nicht. Beides ist eine **benannte Grenze** (`SPEC-036` „Grenzen der Angleichung“, Messprotokoll, Nutzer-Doku) mit Träger im Register ([`BEO-TDS/merge-antwort-grenzen-der-angleichung`](../observations/BEO-TDS/merge-antwort-grenzen-der-angleichung/state.md)), **kein Folge-Slice** (d-migrate braucht den Fall nicht). Die Abweichung zu 208 (Class 20 statt 16) trägt der Folge-Slice [slice-unbekannte-tabelle-class-16-angleichen](../open/slice-unbekannte-tabelle-class-16-angleichen.md). Der Satz des Slice „die Bibliothek sendet heute `TDS_BACKEND_ERROR`“ traf für 208 nicht zu (der Code war schon `TDS_SIM_UNKNOWN_OBJECT`).
- **Review und Verifikation:** Review `206d0baf` ([Report](../../../reviews/2026-11-08-206d0baf-review-slice-merge-antwort-an-messung-angleichen.md); 0 HIGH, 4 MEDIUM, 2 LOW, 5 INFO). Verifikation `6d6195f2` ([Bericht](../../../reviews/2026-11-09-6d6195f2-verifikation-slice-merge-antwort-an-messung-angleichen.md)): (a) und (b) erfüllt, (c) erfüllt mit benannten Grenzen (V-1, V-2); sieben Mutationen einzeln rot; closure-fähig unter B1 bis B5, in dieser Closure erfüllt. Haken erst nach der Verifikation gesetzt (`BEO-TDS/haken-vor-verifikation-gesetzt` eingehalten).
- **Nachbesserung ohne zweiten Review (Grenze, B4):** `b9eef33a` (Findings F-1 bis F-9: KDoc, drei Zeilen `ResultStart.kind`/`ResponseRun`/`SimModel`, Tests, Doku), `899fc346`, `c5f63876` und `6d6195f2` haben keinen eigenen Review. Der Verifier prüfte sie einzeln gegen F-1 bis F-9 und fuhr sieben Mutationen; Entscheid des Planners: sein Bericht genügt als Prüfung dieser Closure. Beleg im Register: `BEO-TDS/nachbesserung-ohne-zweiten-review`.
- **Quelle von `ResultStart.kind` (B5, Verifikation V-4; Entscheid des Planners, umkehrbar):** [ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) nennt als Fläche nur `StatementKind` und `UpdateCount.kind`; `ResultStart.kind` steht nicht darin, und die angenommene ADR bleibt unverändert (`AGENTS.md` §3.5). Ausreichende Quelle ist der Nutzerentscheid vom 2026-11-09 im Slice (§3, §6) mit `SPEC-002` und dem Änderungsprotokoll von `SPEC-059`; **keine Folge-ADR**. Folgerichtig zu Option A (kein neues Ereignis, kein gebrochenes `when` bei Integratoren).
- **Gemessen** (Einzelmessungen, Läufe des Verifiers auf `6d6195f2`, Host mit Last durch fremde Läufe): `make clean-build` Exit 0, 9 min 19 s, 1315 `PASSED`, 0 rote Testfälle; `make e2e` Exit 0, 8 Stufen, 330 s, kein Flake (kein Szenario fährt ein `MERGE`; das belegen die Integratortests und d-migrate); Zitate der Matrix: `tools/matrix-zitate.sh` gegen das Log des `clean-build`, 349 Zitate, alle `PASSED`; `make flaeche` und `make docs-check` Exit 0; `make gates FORCE=1` Exit 0 (29 s, Docker-Cache: belegt die Verdrahtung, nicht die Frische); d-migrate 1.12.0 gegen den Testserver, Läufe S1 bis S13 wie im Messprotokoll (Exit 0; S8 und S13 Exit 5, Grenzen von d-migrate). Closure: Zweig auf `main` `37551c87` (Merge-Basis unverändert, kein Merge von `main` nötig, keine Tests und Tags von `main`; `make spec-abdeckung` deshalb nicht gefahren; `docs/plan/spec-abdeckung.md` ist die Fassung des Verifiers). `make docs-check` Exit 0 (1330 Dateien, 0 Befunde, nach Nachzug der Verweise), `make gates` Exit 0 (15 s: Klasse `werkzeug` mit Beleg übersprungen, Klasse `tests` ohne Stempel, weil im Worktree ein unsauberer Eingangspfad `testserver/build/…` liegt, der Lauf selbst aus dem Docker-Cache, `flaeche`, `gate-zuordnung` 16 Gates und 33 Fälle, `stempel-eingaenge` ok; belegt die Verdrahtung, nicht die Frische: den Frischbeleg trägt der `clean-build` des Verifiers auf `6d6195f2`, `src/` und `testserver/` danach unverändert). Nicht gefahren, nach Zuschnitt der Closure: `make clean-build`, `make e2e`, `make matrix-zitate`.
- **Nutzer-Doku:** eine Zeile in `docs/user/testserver-manuell.md` zur Länge 100 der mitgelieferten Beschreibung (V-3): ein `MERGE` mit längerem Namen endet mit 2628; Ist-Zustand, aufgabenbezogen.
- **Steering-Loop-Eintrag:** Kein Eintrag verkörpert, daher kein `liegt in`; die Einträge sind gezählt, nicht verkörpert. **Benannte Spec-Lücken** (ohne `liegt in`, verkörpert in der versionierten Spezifikation): die Zuordnung von Class und Verbindungsfolge für `TDS_SIM_UNKNOWN_OBJECT` (§4 „Einzelzuordnung offen“, Träger [slice-unbekannte-tabelle-class-16-angleichen](../open/slice-unbekannte-tabelle-class-16-angleichen.md)) und die drei Grenzen der Form M in `SPEC-036`. Gelernt: (1) *Ein Liefer-Punkt, dessen Wortlaut eine Eingabe nennt (zwei `VALUES`-Zeilen), die die Form der Bauart nicht erreicht, und eine Ausweichklausel, die nur eines der Teilstücke deckt, ist beim Schnitt nicht lesbar und erst in der Verifikation aufgefallen*: die Regel von `.claude/commands/plan-welle.md` Schritt 6b (Frage „sieht das Werkzeug den Lieferpunkt“) besteht schon; siebter Beleg in `BEO-TDS/dod-wortlaut-ueber-der-bauart` (Ausdehnung dort ohne Entscheid, hier nicht geändert). (2) *Mitglieder der öffentlichen Fläche ohne Sensor*: dritter Beleg in `BEO-TDS/oeffentliche-flaeche-mitglieder-ohne-sensor` (`ResultColumn.maxLength`, `ResultStart.kind`); die Schwelle ist erreicht, der Lese-Schritt gehört in die Closure von [welle-testserver-manuell](../welle-testserver-manuell.md) (Träger Architect, Frage (a): Mitglieder gate-prüfen). (3) *Der Satz des Plans über den heutigen Zustand* („sendet heute `TDS_BACKEND_ERROR`“) wurde für 208 nicht gegen den Code gelesen: Einzelfall, kein Register.
- **Beobachtungs-Register (`../observations/`):** Neu: [BEO-TDS/merge-antwort-grenzen-der-angleichung](../observations/BEO-TDS/merge-antwort-grenzen-der-angleichung/state.md) (1×, Träger sind Register und `SPEC-036`). Fortgeschrieben: [BEO-TDS/unbekannte-tabelle-class-20-statt-16-am-server](../observations/BEO-TDS/unbekannte-tabelle-class-20-statt-16-am-server/state.md) (Träger: Folge-Slice), [BEO-TDS/zielbild-luecken-der-antwort-tokens](../observations/BEO-TDS/zielbild-luecken-der-antwort-tokens/state.md) (Nachträge entschieden). Evidence `evidence/slice-merge-antwort-an-messung-angleichen.md` ergänzt in [BEO-TDS/dod-wortlaut-ueber-der-bauart](../observations/BEO-TDS/dod-wortlaut-ueber-der-bauart/state.md), [BEO-TDS/nachbesserung-ohne-zweiten-review](../observations/BEO-TDS/nachbesserung-ohne-zweiten-review/state.md) und [BEO-TDS/oeffentliche-flaeche-mitglieder-ohne-sensor](../observations/BEO-TDS/oeffentliche-flaeche-mitglieder-ohne-sensor/state.md).
- **Folge-Slices:** [slice-unbekannte-tabelle-class-16-angleichen](../open/slice-unbekannte-tabelle-class-16-angleichen.md) (Class 16 und offene Verbindung bei 208; S bis M) — ist eine Datei in `open/`, in der Slice-Tabelle und der Kantenliste von [welle-testserver-manuell](../welle-testserver-manuell.md) geführt. Kein Slice für die Grenzen der Form M (Register).
- **Risiken aus §6:** alle 3 mit Ausgang eingetragen, gezählt in der Reihenfolge von §6 (entfallen: 1, Entscheid zu `ResultColumn` und `ResultStart.kind`; weiter offen: 2, `MERGE` auf Tabelle ohne Schlüssel, und 3, Reihenfolge der Antwort im Fehlerfall, beide mit Träger im Register).
- **Drei Paarungen:** Das Repo führt Wellen; die Paarungen prüft die Closure von [welle-testserver-manuell](../welle-testserver-manuell.md). Hier geprüft: *Anker*: kein Anker gesetzt (kein `liegt in`). *Folge-Slice*: [slice-unbekannte-tabelle-class-16-angleichen](../open/slice-unbekannte-tabelle-class-16-angleichen.md) besteht als Datei in `open/`. *Register*: die sechs genannten Kennungen (`merge-antwort-grenzen-der-angleichung`, `unbekannte-tabelle-class-20-statt-16-am-server`, `zielbild-luecken-der-antwort-tokens`, `dod-wortlaut-ueber-der-bauart`, `nachbesserung-ohne-zweiten-review`, `oeffentliche-flaeche-mitglieder-ohne-sensor`) bestehen als Verzeichnis mit nicht leerem `evidence/`.

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung.

**Vorgelagert — Sub-Area-Wahl prüfen:** `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); eine eigene Sub-Area besteht nicht.

**Vorgelagert — offene Beobachtungen sichten:** [`BEO-TDS/zielbild-luecken-der-antwort-tokens`](../observations/BEO-TDS/zielbild-luecken-der-antwort-tokens/state.md), [`BEO-TDS/spec-setzung-widerspricht-messung-am-echten-server`](../observations/BEO-TDS/spec-setzung-widerspricht-messung-am-echten-server/state.md).

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
