# Slice slice-katalogspalten-smallint-tinyint-angleichen: Katalogspalten `max_length` (`smallint`), `precision` und `scale` (`tinyint`) und `is_nullable` (`BITN`) an die Messung angleichen: öffentliche Mitglieder `SqlType.Int16` und `SqlType.UInt8` (M-16, M-17, M-18)

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-testserver-manuell

**Bezug:** [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md), [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md), [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), [ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md)

**Berührte Spec-Stellen:** SPEC-002 (Absatz zu `SqlType` und den JVM-Klassen der Werte) · SPEC-003 (Mindestkatalog von `SqlType` wächst um `Int16` und `UInt8`) · SPEC-035 (Katalog der Spalten: Drahttypen der Antwortspalten; die „bekannte Abweichung `Int32`“ entfällt; Überführung von Literal, Parameter und Massenladen für `Int16`/`UInt8`) · SPEC-059 (Liste der öffentlichen Namen: `SqlType` mit `Int16` und `UInt8`; Änderungsprotokoll: inkompatibel für ein erschöpfendes `when` über `SqlType`). **Die Spezifikation ändert dieser Slice** (Zielbild: Wortlaut folgt dem Messwert).

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

**Ziel:** Der Server dieses Repos antwortet auf die Katalogabfrage der Spalten von d-migrate 1.12.0 mit den Spaltentypen des echten SQL Servers (2022 und 2025) nach den Zeilen M-16 bis M-18 des [Messprotokoll](../../../messungen/referenzabgleich.md#messreihe-zu-massenladen-merge-katalog-und-strukturänderung-messungen-vom-2026-11-07): `max_length` als `smallint` (2 Byte), `precision` und `scale` als `tinyint`, `is_nullable` als `bit` nullbar (`BITN`), `is_identity` als `bit` nicht nullbar. Dazu gibt es nach [ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) (Option A, Nutzerentscheid 2026-11-08) die öffentlichen Mitglieder `SqlType.Int16` (Draht: nicht nullbar der feste Typ INT2, nullbar `INTN` mit 2 Byte) und `SqlType.UInt8` (Draht: nicht nullbar INT1, nullbar `INTN` mit 1 Byte; gebaut nach M-16, die Entscheidung nennt nur `INTN`, die Spezifikation `SPEC-003` stellt es klar, siehe §6); die Antwortspalten der Katalogformen benutzen sie wie jede andere Spalte, es entsteht kein zweiter Mechanismus neben `ResultColumn`.

**Herkunft.** Aus dem Zuschnitt des Slice [slice-katalog-und-struktur-an-messung-angleichen](../done/slice-katalog-und-struktur-an-messung-angleichen.md) vom 2026-11-08 (dessen früherer Liefer-Punkt „Katalogspalten“), den Zeilen M-16, M-17, M-18 des [Messprotokoll](../../../messungen/referenzabgleich.md#messreihe-zu-massenladen-merge-katalog-und-strukturänderung-messungen-vom-2026-11-07) und dem Register [`BEO-TDS/katalog-strukturabweisung-und-aenderungszaehler-weichen-vom-echten-server-ab`](../observations/BEO-TDS/katalog-strukturabweisung-und-aenderungszaehler-weichen-vom-echten-server-ab/state.md). Nutzerentscheid 2026-11-07 (Katalogspalten `smallint`, `tinyint`, `bit` nullbar) und 2026-11-08 ([ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) `Accepted`, Option A). Die Spezifikation ist Zielbild, der echte SQL Server die Referenz.

**Reihenfolge und Fläche.** Dieser Slice ändert **jede Stelle, die `SqlType` auswertet** ([ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) Entscheidung 5: Typmodell, Typabbildung der Spalten, Wertschreiber, Ausgabewerte, Werte der Simulation, Katalog, Massenladen, Planer, Bestand im Testserver, Tests, Einbettungstest). Er berührt damit viele Dateien und kollidiert mit **jedem** anderen Zweig, der diese Dateien ändert. Deshalb startet er allein (WIP 1 für Bau-Slices an `SqlType`), nach [slice-katalog-und-struktur-an-messung-angleichen](../done/slice-katalog-und-struktur-an-messung-angleichen.md) und nach den parallelen Zweigen zu [slice-bulk-load-feste-typen-und-referenzclient](../done/slice-bulk-load-feste-typen-und-referenzclient.md), [slice-schreibpfad-merge-output](../done/slice-schreibpfad-merge-output.md) und [slice-sp-unprepare-ohne-obergrenze](slice-sp-unprepare-ohne-obergrenze.md), und **vor** [slice-antwort-tokens-zielbild-luecken-bauen](../done/slice-antwort-tokens-zielbild-luecken-bauen.md) und [slice-ddl-abschluss-und-transaktionszustand-angleichen](../done/slice-ddl-abschluss-und-transaktionszustand-angleichen.md) (siehe §4).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Die weiteren Typen von M-16: `nvarchar(256)` (`type_name`), `nvarchar(max)` (`default_definition`) und `sql_variant` (`seed_value`, `increment_value`) — benannte Grenze nach [ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) Entscheidung 7; [slice-katalog-indizes-defaults-fremdschluessel](../done/slice-katalog-indizes-defaults-fremdschluessel.md) braucht sie und entscheidet dort, ob `SqlType` weiter wächst oder die Spalten eine andere Form bekommen.
- Die Erzeuger von `SqlType` aus dem Draht (`RpcParameters`, `BulkLoadReader`): `INTN` mit 1 oder 2 Byte als Parameter bleibt ein Typfehler (Setzung nach [ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) Entscheidung 5, kein Lastenheft-Punkt verlangt es).
- Die Typnamen `smallint` und `tinyint` im Bestand des Testservers: der Leser der Tabellenbeschreibung nimmt sie nicht auf (Setzung nach [ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) Entscheidung 6, wie `float`).
- Strukturabweisung und `CREATE TABLE` ohne Schlüssel — [slice-katalog-und-struktur-an-messung-angleichen](../done/slice-katalog-und-struktur-an-messung-angleichen.md); Abschluss der Strukturänderung und Transaktionstexte — [slice-ddl-abschluss-und-transaktionszustand-angleichen](../done/slice-ddl-abschluss-und-transaktionszustand-angleichen.md); Anweisungsart am Port — [slice-antwort-tokens-zielbild-luecken-bauen](../done/slice-antwort-tokens-zielbild-luecken-bauen.md).
- Eine Aussage über andere Clients als den Referenzclient (`AGENTS.md` §3.8).

## 2. Definition of Done


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [x] **Öffentliche Mitglieder und alle Auswerter** ([TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) Entscheidung 1, 3, 4, 5): `SqlType.Int16` und `SqlType.UInt8` (Setzung der Namen), JVM-Klasse der Werte `Int`, Bereichsprüfung bei der Abbildung (`Int16` −32768 bis 32767, `UInt8` 0 bis 255; ein Wert außerhalb ist ein Fehler des Backends, kein Abschneiden); jede Stelle, die `SqlType` auswertet, kennt die neuen Fälle (die Suche nach Auswertern mit `==`, die der Übersetzer nicht fängt, ist Teil des Baus, Stand der Suche in [ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) Entscheidung 5 ist nicht erschöpfend); `SPEC-059` nennt beide Namen in der Liste, Änderungsprotokoll mit dem Vermerk „inkompatibel für ein Kotlin-`when` ohne `else` über `SqlType`“, `SPEC-003` nimmt sie in den Mindestkatalog auf; der Einbettungstest `integrator` meldet je Typ eine Spalte und zieht sein erschöpfendes `when` nach; `make flaeche` grün; Tests der Grenzwerte (`UInt8`: 0, 255, 256, −1; `Int16`: −32768, 32767, ±1 darüber).
- [x] **Katalogspalten** ([TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)): In der Antwort der Katalogabfragen von d-migrate 1.12.0 sind `max_length` `smallint`, `precision` und `scale` `tinyint`, `is_nullable` `BITN`, `is_identity` `bit` nicht nullbar (M-16); `SPEC-035` nennt den Typ der Spalte statt der „bekannten Abweichung `Int32`“; je Spalte ein Test gegen das COLMETADATA (Typ, Länge, Nullbarkeit); die Maße von `bigint`, `bit`, `time`, `datetime2` und der Sonderwert −1 (M-17, M-18) bleiben unverändert (die Messung stützt sie); Zeile M-16 der Zuordnung im Messprotokoll ist auf „angeglichen“ gezogen.
- [x] **Katalogmaße von `smallint` und `tinyint`** ([TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), `AGENTS.md` §3.8; [ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) Entscheidung 2): Die Maße, die der Katalog für eine Spalte dieser Typen meldet, sind gemessen: Zeile M-17 des Messprotokolls belegt `smallint` mit Länge 2, Precision 5, Scale 0 und `tinyint` mit Länge 1, Precision 3, Scale 0 in beiden Instanzen (2022 und 2025); Träger der Erwartung ist `MessungBulkBelegTests` (Aufnahme `katalog-abfragen/03/zeilen`). Der Bau folgt dem Messwert; eine Nachmessung und eine Setzung sind nicht nötig. Wegen der Setzung zum Bestand (kein `smallint`/`tinyint` in Tabellen des Testservers, s. o.) ist der Wert heute nur über eigene Backends erreichbar.

Nachweis-Pflichten für alle Punkte:

- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Haken der Liefer-Punkte setzt der Planner nach der Verifikation, nicht der Implementer (`BEO-TDS/haken-vor-verifikation-gesetzt`).
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
| `src/main/kotlin/io/github/pt9912/tdswire/domain/` (`SqlType`) | update | zwei neue öffentliche Mitglieder |
| `src/main/kotlin/io/github/pt9912/tdswire/` (Typabbildung `TypeInfoWriter`, `ColumnMapping`; Wertschreiber `ValueWriter`, `ValueMapping`) | update | Draht: fest INT1/INT2, nullbar `INTN` mit 1 und 2 Byte, Bereichsprüfung |
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/` (`SimValues`, `CatalogAnswers.typeOf`, `CatalogForms`, `BulkLoads`, Planer) | update | Auswerter, Antwortspalten des Katalogs |
| `testserver/kotlin/` (`TableStore`, Leser der Tabellenbeschreibung) | update | Zeilenprüfung für `Int16`/`UInt8` als Auswerter; Typnamen `smallint`/`tinyint` bleiben nicht aufgenommen |
| `src/integrator/` (Einbettungstest) | update | Beleg der Namen, erschöpfendes `when` |
| `spec/spezifikation.md` (`SPEC-003`, `SPEC-035`, `SPEC-059`) | update | Zielbild, Liste der Fläche, Änderungsprotokoll |
| `referenztest-dotnet/` und `docs/messungen/referenzabgleich.md` | update (falls Nachmessung) | Maße von `smallint`/`tinyint` |
| Tests (Kotlin, `src/test`) | neu / update | Grenzwerte, COLMETADATA je Spalte |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/token/` (`TdsType` mit `Int1Type` und `Int2Type`, `TypeCode`, `TypeInfoWriter`, `ValueWriter`) | update | feste Typen `tinyint` (`0x30`) und `smallint` (`0x34`) für nicht nullbare Spalten, nullbar `INTN` mit 1 und 2 Byte (M-16: `max_length` ist am Draht der feste Typ `smallint`) |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/result/ValueMapping.kt` | update | Klasse `Int` und Bereichsprüfung für die Größen 1 und 2 |
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/SimIntegers.kt` | neu | die vier Ganzzahltypen (Wertebereich, Zahl, Parameter, Massenladen) an einer Stelle statt je Auswerter; hält `SimValues` und `BulkLoads` unter den Grenzen von detekt |
| `testserver/kotlin/io/github/pt9912/tdswire/testserver/RowRules.kt` | update | der Auswerter im Bestand (`TableStore` ruft ihn) |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/RpcParameters.kt` | update (nur KDoc) | der Grund der Abweisung von `INTN` mit 1 und 2 Byte ist die Setzung, nicht mehr „`SqlType` führt ihn nicht“ |
| `docs/plan/kompatibilitaetsmatrix.md` | update | Abschnitt 2a: Zeilen `Int16` und `UInt8` mit Belegtests |
| `docs/plan/planning/in-progress/roadmap.md` und die Links der `slice-mv` (`open/`-Slices, `welle-testserver-manuell.md`, Register `state.md`, `done/`-Slice) | update | Ruhe-Marker, Slice unter „In Arbeit“, reine Link-Nachzüge |
| `docs/user/einbettung-und-betrieb.md` und `docs/user/testserver-manuell.md` | update | Ist-Zustand: neue Mitglieder, Wertebereich, Typen der Katalogantwort, Überführung; die Maße von `bigint`, `bit`, `time`, `datetime2` als gemessen |
| `docs/plan/spec-abdeckung.md` | update (erzeugt) | von `make spec-abdeckung` neu geschrieben, weil der Slice Tests und `@spec`-Tags ändert; keine Handarbeit |
| `docs/messungen/referenzabgleich.md` | update | Zeile M-16 der Zuordnung auf „angeglichen“; M-17 nennt `smallint` und `tinyint` |
| Tests (Kotlin): `ColumnMappingTest`, `ValueMappingTest`, `MappingFuzzTest`, `TokenRoundtripTest`, `TokenDecoder` (Testhilfe), `SimValuesTest`, `SimulationBackendBulkTest`, `SimulationBackendCatalogTest`, `KatalogSpaltenWireTest` (neu, Package `runtime`), `TableStoreTest`, `FlaecheEmbeddingTest` | update / neu | Grenzwerte, Drahttypen je Antwortspalte, Auswerter, Beleg der Namen |

Stand der Suche nach Auswertern (Lauf des Implementers): Der Übersetzer fand jedes `when` als Ausdruck (`ColumnMapping.tdsType`, `SimValues.number`, `SimValues.text`, `SimValues.hasClassOf`, `CatalogAnswers.typeOf`, `BulkLoads.converted`, `RowRules.hasClassOf`, dazu `ValueMapping.nonNull` und `TypeInfoWriter`/`ValueWriter` für die neuen `TdsType`). Die Suche mit `grep` nach `== SqlType.` und `.type ==` fand die Auswerter, die der Übersetzer nicht fängt: `SimValues.parameter` (Gleichheit des Parametertyps, Verengung `Int64` zu `Int32`) und `CatalogAnswers.collationOf` (nur `UnicodeText`, unberührt). Unberührt bleiben nach Setzung `RpcParameters` und `BulkLoadReader` (Erzeuger aus dem Draht) und `SimColumnTypes`/`StructureParts` (Typnamen `smallint`/`tinyint` nicht aufgenommen).

## 4. Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) `Accepted` (erfüllt, 2026-11-08); [slice-qa-vergleichslauf-d-messungen-bulk-merge](../done/slice-qa-vergleichslauf-d-messungen-bulk-merge.md) in `done/` (erfüllt); [slice-katalog-und-ddl-zustand](../done/slice-katalog-und-ddl-zustand.md) in `done/` (erfüllt); [slice-katalog-und-struktur-an-messung-angleichen](../done/slice-katalog-und-struktur-an-messung-angleichen.md) in `done/` (gleiche Dateien im Katalog und im Testserver; Setzung des Planners); die parallelen Zweige zu [slice-bulk-load-feste-typen-und-referenzclient](../done/slice-bulk-load-feste-typen-und-referenzclient.md), [slice-schreibpfad-merge-output](../done/slice-schreibpfad-merge-output.md) und [slice-sp-unprepare-ohne-obergrenze](slice-sp-unprepare-ohne-obergrenze.md) sind in `main` (Setzung des Planners wegen der Fläche: dieser Slice berührt die Auswerter, die diese Zweige ändern; kein inhaltlicher Zwang); kein anderer Bau-Slice in `in-progress/` (WIP 1); ein Verantwortlicher ist eingetragen; die Maße von `smallint` und `tinyint` sind durch M-17 gemessen (erfüllt, kein Entscheid des Nutzers nötig).

**Reihenfolge der drei Bau-Slices (Setzung des Planners):** [slice-katalog-und-struktur-an-messung-angleichen](../done/slice-katalog-und-struktur-an-messung-angleichen.md) → dieser Slice → [slice-antwort-tokens-zielbild-luecken-bauen](../done/slice-antwort-tokens-zielbild-luecken-bauen.md) → [slice-ddl-abschluss-und-transaktionszustand-angleichen](../done/slice-ddl-abschluss-und-transaktionszustand-angleichen.md) → [slice-katalog-indizes-defaults-fremdschluessel](../done/slice-katalog-indizes-defaults-fremdschluessel.md). Begründung: dieser Slice ist die breiteste Änderung und kollidiert mit allen Zweigen; er geht früh und allein, damit [slice-antwort-tokens-zielbild-luecken-bauen](../done/slice-antwort-tokens-zielbild-luecken-bauen.md), [slice-ddl-abschluss-und-transaktionszustand-angleichen](../done/slice-ddl-abschluss-und-transaktionszustand-angleichen.md) und [slice-katalog-indizes-defaults-fremdschluessel](../done/slice-katalog-indizes-defaults-fremdschluessel.md) auf dem neuen `SqlType` aufsetzen. [slice-katalog-indizes-defaults-fremdschluessel](../done/slice-katalog-indizes-defaults-fremdschluessel.md) startet nach diesem Slice (die Typen der Spalten wirken in jede Sichtantwort).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): die Suche nach Auswertern findet Stellen, die ein eigenes Verhalten brauchen (nicht nur einen weiteren Fall); Schnittvorschlag: Mitglieder und Auswerter | Katalogspalten | Maße. Die Rückführung „Fläche wächst“ entfällt, sie ist durch [ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) entschieden.
- `in-progress` → `open` (blockiert): ein Lauf mit d-migrate (`schema reverse`, `compare`, `schema migrate --execute`) wird durch `smallint`/`tinyint` statt `int` rot oder ändert sein Ergebnis; der Nutzer entscheidet dann über den Weg (Rückfall C von [ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) für die betroffene Spalte).

## 5. Closure-Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle drei Liefer-Punkte der DoD erledigt, `make gates` grün (auch `make flaeche`), der Review-Report liegt vor, ein Lauf mit d-migrate (`schema reverse`, `compare`, `schema migrate --execute`) nach dem Bau ist unverändert gegenüber einem Lauf unmittelbar davor (oder die Abweichung als Befund benannt, [ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) Konsequenzen), die Zeile M-16 des [Messprotokoll](../../../messungen/referenzabgleich.md#messreihe-zu-massenladen-merge-katalog-und-strukturänderung-messungen-vom-2026-11-07) ist von „widerspricht“ auf „angeglichen“ gezogen und, falls nachgemessen, trägt das Messprotokoll die neue Zeile, die Closure-Notiz trägt den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **Breite Änderung der Auswerter.** Eine Stelle, die `SqlType` nur mit `==` prüft, fängt der Übersetzer nicht; eine übersehene Stelle bricht still ein Verhalten. Gegenmittel: die Suche im ersten Lauf, Tests je Auswerter. — **Ausgang:** entfallen. Die Suche mit `grep` fand `SimValues.parameter` und `CatalogAnswers.collationOf`, beide behandelt bzw. begründet unberührt; die Verifikation fuhr sieben Mutationen, alle rot in den erwarteten Testklassen.
- **Dateikonflikte mit allen Zweigen** an den Auswertern (Reihenfolge in §4 ist eine Setzung). — **Ausgang:** eingetreten, in Dokumenten. Die Merge-Probe gegen `main` (`95e89d05`) fand fünf Inhaltskonflikte, alle in Dokumenten (Roadmap, dieser Slice, zwei `open/`-Slices, Welle-Datei), keinen in Quelltext oder Spezifikation.
- **Auswirkung auf den Beleg am fremden Client.** d-migrate liest `max_length` und `precision` über JDBC; ob `smallint` und `tinyint` statt `int` etwas ändert, ist nicht gemessen ([ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) Kontext). [slice-abnahme-d-migrate](../open/slice-abnahme-d-migrate.md) prüft es am Ende erneut. — **Ausgang:** weiter offen, Träger [slice-abnahme-d-migrate](../open/slice-abnahme-d-migrate.md). Der Lauf der Verifikation (`schema reverse`, `compare`, `schema migrate --execute`, `data export`) ist gegenüber der Basis `66fee6c1` unverändert; die Wirkung auf andere Clients ist nicht belegt.
- **Inkompatible Änderung für Kotlin-`when` ohne `else`** vor der ersten Veröffentlichung ([ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) Entscheidung 4, Setzung: trifft keinen Dritten). — **Ausgang:** eingetreten, vermerkt: das Änderungsprotokoll von `SPEC-059` trägt den Vermerk „inkompatibel für ein Kotlin-`when` ohne `else` über `SqlType`“.
- **Katalogmaße ungemessen**, bis der Nutzer (a) oder (b) wählt. — **Ausgang:** entfallen. Zeile M-17 des Messprotokolls belegt `smallint` 2/5/0 und `tinyint` 1/3/0 in beiden Instanzen; der Bau stimmt mit der Messung überein, Nachmessung und Setzung sind nicht nötig.
- **Abweichung des gebauten Drahts vom Wortlaut der Entscheidung** ([ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) Nr. 1 sagt „Draht: `INTN`“; gebaut ist die nicht nullbare Spalte als fester Typ INT1/INT2 nach M-16, die nullbare als `INTN`). Entscheid (Nutzer, 2026-11-08, Standardantwort „Empfehlungen“): Klarstellung in `SPEC-003` (Rang 2 steht über der ADR), die Accepted-ADR bleibt unverändert (`AGENTS.md` §3.5), keine Folge-ADR. — **Ausgang:** eingetreten, mit Klarstellung getragen (`SPEC-003`; Beleg Zeile M-16, Aufnahmen beider Instanzen; Closure-Notiz §7).

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

- **Gegenstand:** Katalogspalten `max_length` (`smallint`), `precision` und `scale` (`tinyint`) und `is_nullable` (`BITN`) an die Messung angeglichen (M-16, M-17, M-18) über die öffentlichen Mitglieder `SqlType.Int16` und `SqlType.UInt8`; alle Auswerter von `SqlType` kennen die neuen Fälle. Zeile M-16 der Zuordnung im [Messprotokoll](../../../messungen/referenzabgleich.md#messreihe-zu-massenladen-merge-katalog-und-strukturänderung-messungen-vom-2026-11-07): „angeglichen“.
- **Abweichung des Drahts vom Wortlaut von [ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) Nr. 1:** die Entscheidung nennt als Draht beider Mitglieder `INTN`; gebaut ist die nicht nullbare Spalte als fester Typ (INT2 `0x34`, INT1 `0x30`), die nullbare als `INTN` mit 2 bzw. 1 Byte. Beleg: Zeile M-16, die Aufnahmen `katalog-abfragen.messung` beider Instanzen (2022 und 2025), vom Verifier und vom Reviewer selbst dekodiert. Entscheid: Klarstellung in `SPEC-003` (Rang 2 steht über der ADR), keine Folge-ADR, die Accepted-ADR bleibt unverändert (`AGENTS.md` §3.5) und trägt keinen Zeiger auf die Klarstellung; Auffindbarkeit über `SPEC-003` und diese Notiz. Die Entscheidung ist als Standardantwort auf die Empfehlungen des Nutzers angesetzt (2026-11-08); der Nutzer kann sie noch bestätigen oder umkehren. Der Draht der nullbaren Form ist nicht gemessen, `SPEC-003`, `SPEC-035` und die Matrix sagen es so (`AGENTS.md` §3.8).
- **Zu [ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) Nr. 2:** die Aussage „Maße nicht gemessen“ ist durch M-17 überholt (`smallint` 2/5/0, `tinyint` 1/3/0, beide Instanzen); die ADR bleibt unverändert (`AGENTS.md` §3.5), maßgeblich ist das Messprotokoll.
- **Was hat funktioniert:** (1) Die Suche nach Auswertern mit `grep` zusätzlich zum Übersetzer fand die zwei Stellen, die kein `when` als Ausdruck zeigt. (2) Review und Verifikation dekodierten den Draht selbst aus den Rohbytes beider Instanzen, statt dem Test zu trauen; Mutationen färbten jeweils genau die Tests, die die Zusage tragen. (3) Der Verifier fuhr d-migrate gegen Zweig und Basis nacheinander und verglich Ausgaben byte-genau. (4) Der Implementer setzte keine Haken.
- **Was ging anders als geplant:** (1) Der Plan nannte den Draht der Spalten `INTN`; die Messung zeigte feste Typen, die Klarstellung folgte erst nach dem Review (F-1). (2) Der dritte Liefer-Punkt (Entscheid über Nachmessung oder Setzung) war beim Zuschnitt überholt: M-17 trug die Maße schon (V-1). (3) Der Plan nannte nicht alle berührten Dateien (F-5), `docs/plan/spec-abdeckung.md` als erzeugte Datei fehlte (L-1), eine Tabellenzeile hatte eine Zelle zu viel (L-2); beides in §3 nachgezogen.
- **Review und Verifikation:** Review `e182b838` ([Report](../../../reviews/2026-11-08-e182b838-review-slice-katalogspalten-smallint-tinyint-angleichen.md); 0 HIGH, 5 MEDIUM, 1 LOW, 4 INFO): F-1 bis F-6 behoben in `bd66e2dc`. Verifikation `fa7adeaa` ([Bericht](../../../reviews/2026-11-08-fa7adeaa-verifikation-slice-katalogspalten-smallint-tinyint-angleichen.md)): closure-fähig unter B1 bis B5, in dieser Closure erfüllt; eigene Befunde V-1, L-1, L-2 nachgezogen, I-1 im Register, I-2 bis I-4 Kenntnisnahme.
- **Nachbesserung ohne zweiten Review (Grenze):** `bd66e2dc` und die Merges hatten keinen eigenen Review; der Verifier prüfte sie einzeln gegen F-1 bis F-6.
- **Gemessen** (Einzelmessungen 2026-11-08, Host unter Last durch fremde Läufe; Läufe des Verifiers auf `fa7adeaa`): `make clean-build` Exit 0, 1263 Testfälle `PASSED`, 26 min 58 s; `make gates FORCE=1` Exit 0 (Cache-Falle: Docker-Stufen aus dem Layer-Cache); `make flaeche` Exit 0; `make matrix-zitate` Exit 0 (321 Zitate, 0 ohne PASSED); `make e2e` Exit 0, 8 Stufen, 363 s, kein Flake; d-migrate 1.12.0 gegen den Testserver aus dem Arbeitsbaum und gegen die Basis `66fee6c1`: Ausgaben und Exit-Codes gleich; sieben Mutationen rot. Läufe der Closure auf dem gemergten Stand (Lastmittel 15 bis 25): `make gates` Exit 0; `make docs-check` Exit 0 (0 Befunde); `make spec-abdeckung` Exit 0, 1268 Testfälle `PASSED`, ein roter Fall `ListenerAbortTest > der Listener ruft den Abbruch nach der Frist und wartet CANCEL_TIMEOUT ab` (Wanduhr unter Last, im `make gates` derselben Stunde grün, außerhalb des Diffs), die erzeugte Matrix wurde deshalb nicht übernommen und die Fassung nach dem Merge bleibt (Evidence in `BEO-TDS/wanduhr-tests-flackern-unter-last`, 7×).
- **Grenzen ohne Sensor:** (a) der Draht der nullbaren Form `INTN` 1/2 Byte ist nicht gemessen; (b) die Überführung eines `int`-Parameters in eine `smallint`-Spalte ist am echten Server nicht gemessen, im Testserver nehmen `Int16`/`UInt8`-Spalten über Parameter nie einen Wert an (Setzung, [ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) Nr. 5 und 6); (c) kein Lauf gegen den echten SQL Server in diesem Slice; (d) Wirkung auf andere Clients als den Referenzclient und d-migrate: keine Aussage; (e) die Untergrenze von `shortRange` und `ValueSupport.byteRange` ist nicht mutationsgeprüft, nur durch Grenztests und Handvektor getragen.
- **Beobachtungs-Register (`../observations/`):** fortgeschrieben: [BEO-TDS/katalog-strukturabweisung-und-aenderungszaehler-weichen-vom-echten-server-ab](../observations/BEO-TDS/katalog-strukturabweisung-und-aenderungszaehler-weichen-vom-echten-server-ab/state.md) (Teil b geliefert; neue Abweichung: `column_name` am echten Server nullbar, im Bau nicht, Verifikation I-1; Evidence-Datei dieses Vorgangs). Zitiert: [BEO-TDS/oeffentliche-flaeche-mitglieder-ohne-sensor](../observations/BEO-TDS/oeffentliche-flaeche-mitglieder-ohne-sensor/state.md) (`make flaeche` fängt hier die Typnamen, die Lücke bei Mitgliedern bleibt). Fortgeschrieben außerdem: [BEO-TDS/wanduhr-tests-flackern-unter-last](../observations/BEO-TDS/wanduhr-tests-flackern-unter-last/state.md) (Evidence-Datei dieses Vorgangs). Keine neue Beobachtung.
- **Steering-Loop-Eintrag:** Kein Eintrag verkörpert, daher kein `liegt in`; der Eintrag ist gezählt, nicht verkörpert. Gelernt: *Ein Entscheidungspunkt im Plan kann durch eine Messung überholt sein, die das Messprotokoll schon trägt*: DoD-Punkt 3 und das Risiko „Katalogmaße ungemessen“ verlangten einen Entscheid des Nutzers, den M-17 längst beantwortete; der Zuschnitt las das Messprotokoll nicht gegen jede Aussage „nicht gemessen“. Prüfpunkt beim Zuschnitt: jede Aussage „nicht gemessen“ gegen die Zeilen des Messprotokolls grepen, bevor sie ein Entscheid-Punkt wird (1×, unter der Schwelle). Benannte Spec-Lücke: keine; die Klarstellung des Drahts steht in `SPEC-003`, die Abweichung `column_name` nullbar führt das Register.
- **Folge-Slices:** [slice-abnahme-d-migrate](../open/slice-abnahme-d-migrate.md) (Risiko 3), [slice-katalog-indizes-defaults-fremdschluessel](../done/slice-katalog-indizes-defaults-fremdschluessel.md) (die weiteren Typen von M-16, `column_name` nullbar), [slice-antwort-tokens-zielbild-luecken-bauen](../done/slice-antwort-tokens-zielbild-luecken-bauen.md), [slice-ddl-abschluss-und-transaktionszustand-angleichen](../done/slice-ddl-abschluss-und-transaktionszustand-angleichen.md); alle bestehen als Dateien, nichts gestartet.
- **Risiken aus §6:** alle 6 mit Ausgang eingetragen, gezählt in der Reihenfolge von §6 (entfallen: 1, 5; eingetreten: 2, 4, 6 (6 mit Klarstellung getragen); weiter offen mit Träger: 3).
- **Drei Paarungen:** Das Repo führt Wellen; die Paarungen prüft die Closure von [welle-testserver-manuell](../welle-testserver-manuell.md). Hier geprüft: *Anker*: kein Anker gesetzt (kein `liegt in`). *Folge-Slice*: die vier genannten Dateien bestehen. *Register*: beide genannten Kennungen bestehen als Verzeichnis mit nicht leerem `evidence/`.

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

**Vorgelagert — offene Beobachtungen sichten:** [`BEO-TDS/katalog-strukturabweisung-und-aenderungszaehler-weichen-vom-echten-server-ab`](../observations/BEO-TDS/katalog-strukturabweisung-und-aenderungszaehler-weichen-vom-echten-server-ab/state.md), [`BEO-TDS/spec-setzung-widerspricht-messung-am-echten-server`](../observations/BEO-TDS/spec-setzung-widerspricht-messung-am-echten-server/state.md), [`BEO-TDS/messung-am-echten-sql-server-fehlt`](../observations/BEO-TDS/messung-am-echten-sql-server-fehlt/state.md), [`BEO-TDS/oeffentliche-flaeche-mitglieder-ohne-sensor`](../observations/BEO-TDS/oeffentliche-flaeche-mitglieder-ohne-sensor/state.md).

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
