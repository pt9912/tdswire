# Slice slice-katalog-indizes-defaults-fremdschluessel: Katalogsichten für Indizes, Standardwerte, Prüfbedingungen und Fremdschlüssel aus den mitgeteilten Tabellen, am Beispiel von d-migrate

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-testserver-manuell

**Bezug:** [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md)

**Berührte Spec-Stellen:** SPEC-035 (Tabellenbeschreibung, Katalog der Spalten: was `SimColumn` und `SimTable` führen) · SPEC-036 (Katalogabfragen für Eindeutigkeiten, Fremdschlüssel, Indizes, Prüfbedingungen; DDL-Formen mit Fremdschlüssel und Index) · SPEC-059 (Liste der Typnamen, Callback, Änderungsprotokoll) · `TDS-FA-11.a` (Ablaufpunkte 8 und 9 in der Spezifikation: Katalogantworten, Strukturänderung)

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-06.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Die Bibliothek beantwortet die Katalogabfragen von d-migrate für Eindeutigkeiten, Fremdschlüssel, Indizes, Standardwerte und Prüfbedingungen aus den ihr mitgeteilten Tabellen (heute antworten sie leer), sodass `schema reverse` und `schema compare` mit einem Soll, das diese Elemente führt, gegen den Testserver das Ergebnis des echten SQL Servers zeigen (Maßstab: „Spezifikation ist Zielbild, der echte SQL Server ist die Referenz“, Nutzer 2026-11-06).

**Entscheid zur öffentlichen Fläche (Nutzer, 2026-11-11, per Auswahl):** die Fläche bleibt (vier neue Typnamen, fünf Mitglieder, der Strukturänderungs-Callback mit einer `SimTable`), die Folge-ADR zu [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Entscheidung 4 ist [ADR-0046](../../adr/0046-oeffentliche-flaeche-der-katalog-elemente.md) (`Accepted`). Die Spezifikation führt die Fläche in `SPEC-059` und im Änderungsprotokoll.

**Herkunft.** Verifikation V-3 von [slice-katalog-und-ddl-zustand](../done/slice-katalog-und-ddl-zustand.md): dessen Liefer-Punkt 1 sagte „Indizes, Defaults, Prüfbedingungen“ aus den Tabellen beantwortet; geliefert sind sie leer (Setzung, `SimColumn` führt sie nicht), und kein offener Slice trug Fremdschlüssel, Indizes oder Standardwerte. Dieser Slice ist der Träger (Nutzerentscheid 2026-11-06, „deine Empfehlungen“). Das Messprotokoll [katalog-und-ddl-zustand-2026-11-06](../../../messungen/katalog-und-ddl-zustand-2026-11-06.md) nennt „Soll-Dateien mit Fremdschlüsseln, Indizes oder Standardwerten“ als nicht gemessen.

**Messung liegt vor:** die Antworten des echten SQL Servers auf die Katalogabfragen von d-migrate 1.12.0 für Standardwerte, Identität, Fremdschlüssel, Indizes, Prüfbedingungen und Eindeutigkeiten sind gemessen (Messung M-16 bis M-21, [Messprotokoll](../../../messungen/referenzabgleich.md#messreihe-zu-massenladen-merge-katalog-und-strukturänderung-messungen-vom-2026-11-07); je ein Fall je Instanz 2022 und 2025, Mehrspalten-Schlüssel, Sequenzen, Routinen, Sichten und Partitionen nicht gemessen); die Setzungen in `SPEC-035` und in diesem Slice sind daran zu messen.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Die Messung der Antworten am echten SQL Server — [slice-qa-vergleichslauf-d-messungen-bulk-merge](../done/slice-qa-vergleichslauf-d-messungen-bulk-merge.md) (Liefer-Punkt 2, Katalog); sie ist der Eingang dieses Slice, nicht sein Gegenstand (EULA, Nutzer-Lauf).
- Schreiben von Zeilen (Einfügen, Massenladen, MERGE) — [slice-schreibpfad-bulk-load](../done/slice-schreibpfad-bulk-load.md) und [slice-schreibpfad-merge-output](../done/slice-schreibpfad-merge-output.md).
- Sichten, Sequenzen, Routinen, Partitionen und Volltextindizes mit Inhalt — kein Lauf des Solls stellt sie mit Inhalt; sie bleiben leere Resultsets mit den Spalten ([slice-katalog-und-ddl-zustand](../done/slice-katalog-und-ddl-zustand.md) §1).
- Identität (`is_identity`) und berechnete Spalten — Grenze von [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) (Identität ohne Feld); eine Aufnahme ist eine eigene Entscheidung des Nutzers.
- Referentielle Integrität im Bestand (Fremdschlüssel erzwingen, Indizes nutzen) — der Katalog beschreibt, der Testserver prüft nichts.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [x] **Bibliothek, Modell und Katalog** ([TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend)): `SimColumn`/`SimTable` (oder ein Mitglied, das die Entscheidung benennt) führen Eindeutigkeiten, Fremdschlüssel, Indizes, Standardwerte und Prüfbedingungen; die Katalogabfragen von d-migrate 1.12.0 für diese Elemente werden daraus beantwortet; ein Test je Abfragegruppe liest die Antwort mit Handvektoren aus dem Mitschnitt (`DMigrateWire.kt`) und, soweit vorhanden, mit den Zeilen der Messung am echten Server.
- [x] **Bibliothek, Strukturänderung** ([TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend)): die DDL-Formen, mit denen `schema migrate --execute` diese Elemente anlegt (Fremdschlüssel in `CREATE TABLE`, `ALTER TABLE ... ADD CONSTRAINT`, `CREATE INDEX`), werden erkannt und gehen nach der Entscheidung an den Integrator; der Katalog zeigt danach die gemeldete Struktur. Welche Formen d-migrate sendet, ist am Soll zu messen, nicht zu raten.
- [x] **Testserver und Beleg** ([TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate)): der Testserver hält die Elemente im Arbeitsspeicher und liest sie aus der Beschreibungsdatei; `schema reverse`, `schema compare` und `schema migrate --execute` von d-migrate gegen ein Soll mit Fremdschlüssel, Index, Standardwert und Prüfbedingung enden mit Exit 0 samt Nachprüfung; die Ausgabe steht mit Rechner, Last und Image-Digest in einem Messprotokoll.

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
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/SimModel.kt`, `SimElements.kt` (neu) | update / neu | Modell: `SimColumn.defaultDefinition`, `SimTable.uniqueKeys/foreignKeys/indexes/checks`, die Typen `SimUniqueKey`, `SimForeignKey`, `SimIndex`, `SimCheck` und ihre Prüfungen |
| `.../simulation/CatalogForms.kt`, `CatalogAnswers.kt`, `ElementAnswers.kt` (neu) | update / neu | Antworten statt leerer Resultsets; Antwortspalten mit den gemessenen Typen und Nullbarkeiten |
| `.../simulation/Statement.kt`, `StatementParser.kt`, `StructureParts.kt`, `ColumnParts.kt` (neu), `ConstraintParts.kt` (neu), `CheckExpressions.kt` (neu), `SqlScanner.kt` | update / neu | DDL-Formen D1 bis D4; `<` und `>` als Symbole des Scanners |
| `.../simulation/StatementPlanner.kt`, `StructurePlanner.kt` (neu), `StructureChecks.kt` (neu), `SimulationBackend.kt`, `SimulationChanges.kt` | update / neu | Planung gegen den Katalog, der Strukturänderungs-Callback bekommt eine `SimTable` |
| `testserver/kotlin/.../TableStore.kt`, `SimDescriptionReader.kt`, `SimElementReader.kt` (neu), `SimElementFields.kt` (neu) | update / neu | Integrator-Anteil: Bestand mit Elementen, Beschreibung liest `unique`, `default`, `references`, `indices`, `constraints` |
| `testserver/resources/manuell/sim.yaml` | unverändert | die Anleitungen führen die Beispiel-Tabellen und `schema reverse`-Läufe gegen sie; die Elemente zeigt eine eigene Soll-Datei in `docs/user/testserver-manuell.md` |
| `src/test/kotlin/.../simulation/` (`SimElementsTest`, `SimulationBackendCatalogElementsTest`, `SimulationBackendElementsStructureTest` neu; `DMigrateWire.kt`, `SimulationBackendCatalogTest`, `SimulationBackendStructureTest`, `StatementFormsTest`, `StatementScannerTest` angepasst), `testserver/test/kotlin/` (`SimDescriptionElementsTest` neu; `SimDescriptionReaderTest`, `TableStoreTest`), `src/integrator/kotlin/.../` (`FlaecheEmbeddingTest`, `AnleitungTest`, `StatementKindEmbeddingTest`) | update / neu | Handvektoren und Zeilen von M-21, Antworten, Bestand, Namen von außen |
| `spec/spezifikation.md` (`SPEC-035`, `SPEC-036`, `SPEC-059` Liste, Callback und Änderungsprotokoll, `TDS-FA-11.a` Ablaufpunkte 8 und 9), `docs/user/README.md`, `docs/user/einbettung-und-betrieb.md`, `docs/user/testserver-manuell.md`, `docs/user/referenz-konfiguration.md`, `docs/messungen/katalog-indizes-defaults-fremdschluessel-d-migrate-2026-11-09.md` (neu), `docs/messungen/referenzabgleich.md` (Zeile M-21), `docs/messungen/katalog-und-ddl-zustand-2026-11-06.md`, `docs/plan/spec-abdeckung.md` (erzeugt) | update / neu | Zusage, Formen, Läufe, Nutzer-Doku |
| `docs/plan/planning/in-progress/roadmap.md`, `docs/plan/planning/welle-testserver-manuell.md` | update | Zustand des Slice (in Arbeit), Zeiger auf `in-progress/` |
| `docs/plan/planning/observations/BEO-TDS/katalog-elemente-grenzen-der-angleichung/` (neu: `observation.md`, `state.md`, `evidence/`), die Zeiger in `docs/plan/planning/observations/BEO-TDS/katalog-strukturabweisung-und-aenderungszaehler-weichen-vom-echten-server-ab/` und `docs/plan/planning/observations/BEO-TDS/messung-am-echten-sql-server-fehlt/` | neu / update | Träger der Grenzen gegen das Zielbild (§6); Zeiger nach `in-progress/` |
| `docs/plan/planning/done/slice-antwort-tokens-zielbild-luecken-bauen.md`, `slice-katalog-und-ddl-zustand.md`, `slice-katalog-und-struktur-an-messung-angleichen.md`, `slice-katalogspalten-smallint-tinyint-angleichen.md`, `slice-qa-vergleichslauf-d-messungen-bulk-merge.md` (alle unter `docs/plan/planning/done/`) | update | Zeiger auf diesen Slice nach `in-progress/` nachgezogen |
| `docs/reviews/` (drei ältere Reports und Verifikation `2026-11-07-4d73a9d2`: Zeiger; der Report `2026-11-09-9ae13838` zu diesem Slice) | update / neu | Zeiger nachgezogen, Review-Bericht |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf. **Wachsen öffentliche Namen** (ein Typ für Fremdschlüssel oder Index), listet [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Entscheidung 4 jeden neuen Namen; dann ist vor dem Bau eine Entscheidung nötig (Ergänzung oder Folge-ADR), und `make flaeche` führt die Prüfung. Entschieden am 2026-11-11 durch den Nutzer (per Auswahl, nach dem Bau): die Fläche bleibt, die Folge-ADR ist [ADR-0046](../../adr/0046-oeffentliche-flaeche-der-katalog-elemente.md) (§1).

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-katalog-und-ddl-zustand](../done/slice-katalog-und-ddl-zustand.md) liegt in `done/`, die Messung der Katalogantworten ([slice-qa-vergleichslauf-d-messungen-bulk-merge](../done/slice-qa-vergleichslauf-d-messungen-bulk-merge.md), Liefer-Punkt 2, Zeile (e)) liegt vor — oder der Nutzer entscheidet, ohne Messung zu bauen (dann bleibt die Antwort Setzung, nachträglich verglichen) — und ein Verantwortlicher ist eingetragen. Dieser Slice blockiert die Abnahme nicht, solange das Soll von [slice-abnahme-d-migrate](../open/slice-abnahme-d-migrate.md) keine dieser Elemente führt; führt es sie, startet die Abnahme erst danach. Reihenfolge (Setzung des Planners, Nutzerentscheid 2026-11-07 zum neuen Slice; Zuschnitt 2026-11-08 nach [ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md)): [slice-katalog-und-struktur-an-messung-angleichen](../done/slice-katalog-und-struktur-an-messung-angleichen.md) (Strukturabweisung) und [slice-katalogspalten-smallint-tinyint-angleichen](../done/slice-katalogspalten-smallint-tinyint-angleichen.md) (Katalogspalten, `SqlType.Int16` und `SqlType.UInt8`) liegen vor diesem Slice, weil die Typen der Katalogspalten in jede Sichtantwort wirken; ein gleichzeitiger Lauf erzeugt Konflikte am Katalog. Ob `SqlType` für `nvarchar(256)`, `nvarchar(max)` und `sql_variant` weiter wächst, entscheidet dieser Slice (Grenze nach [ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) Entscheidung 7).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte; Schnittvorschlag: Fremdschlüssel und Eindeutigkeiten | Indizes, Standardwerte, Prüfbedingungen.
- `in-progress` → `open` (blockiert): die Fläche wächst und die Entscheidung über die neuen Namen steht aus; oder die Messung am echten Server widerspricht der Setzung des Planners über die Form.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln.

DoD vollständig, `make gates` grün, die Ausgabe der d-migrate-Läufe liegt vor, Review und Verifikation liegen vor, Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **Neue öffentliche Namen.** Fremdschlüssel und Index brauchen ein Modell; es kann ein neuer Typname werden, den [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) nicht listet. — **Ausgang:** eingetreten (vier Typnamen, fünf Mitglieder, neue Parameterliste des Callbacks); Entscheid des Nutzers am 2026-11-11 per Auswahl: die Fläche bleibt; die Folge-ADR liegt vor: [ADR-0046](../../adr/0046-oeffentliche-flaeche-der-katalog-elemente.md) (`Accepted`, `make flaeche` grün).
- **Form der Antworten: gemessen mit dem Referenzclient, je ein Fall.** Die Antworten des echten Servers sind für Standardwerte, Identität, Fremdschlüssel, Indizes, Prüfbedingungen und Eindeutigkeiten gemessen (M-16 bis M-21, [Messprotokoll](../../../messungen/referenzabgleich.md#messreihe-zu-massenladen-merge-katalog-und-strukturänderung-messungen-vom-2026-11-07)); Grenzen: je Instanz ein Fall, Texte als `sp_executesql` des Referenzclients, mehrspaltige Schlüssel und Sequenzen, Routinen, Sichten, Partitionen nicht gemessen. Gebaut wird gegen diese Zeilen; was sie nicht tragen, bleibt Setzung (Spalten, Reihenfolge, Typen). — **Ausgang:** entfallen: der Verifier dekodierte die Rohbytes beider Instanzen (2022, 2025) und fand Spaltenköpfe, Nullbarkeiten, Zeilen und Reihenfolge der sechs Abfragen gleich den Antworten der Bibliothek (Verifikationsbericht, Abschnitt „Draht“); was die Aufnahmen nicht tragen, steht als Grenze im Register (Punkte 4 bis 8) und in §7.
- **DDL-Formen von d-migrate ungemessen.** Welche Anweisungen `schema migrate --execute` für Fremdschlüssel, Indizes und Standardwerte sendet, ist nicht gemessen. — **Ausgang:** entfallen: gemessen am Soll mit d-migrate 1.12.0 (Messprotokoll Zeilen a3 und e2: `CREATE TABLE` mit Elementen, `ALTER TABLE … WITH CHECK ADD CONSTRAINT`, `CREATE INDEX`, Formen D1 bis D4); Texte, die d-migrate zusätzlich sendet (`DROP CONSTRAINT IF EXISTS`, `DECLARE @df`), gehen an die Folge-Slices. Die Messung gilt gegen den Testserver, nicht gegen den echten Server.

- **Grenzen gegen das Zielbild ohne Folge-Slice.** Standardwert an vorhandener Spalte (Text mit `DECLARE @df`), `DROP CONSTRAINT IF EXISTS` (zweites `schema migrate` mit Prüfbedingung endet mit Exit 5), Index mit `unique: true` (`POST_EXECUTE_DRIFT`), Prüfbedingung der Beschreibungsdatei roh, Namen von Bedingungen je Tabelle statt je Schema, Prüfbedingung mit `< =` und ungeprüften Spalten, Sortierung der Namen, nicht angeglichene Antwortspalten der übrigen Katalogformen. — **Träger:** das Register [katalog-elemente-grenzen-der-angleichung](../observations/BEO-TDS/katalog-elemente-grenzen-der-angleichung/state.md) (Auslöser der Sichtung: [slice-abnahme-d-migrate](../open/slice-abnahme-d-migrate.md)); Folge-Slices (Standardantwort auf die Empfehlung des Verifiers, umkehrbar): [slice-drop-constraint-form-d5](../in-progress/slice-drop-constraint-form-d5.md) (Punkt 2, dazu die Messung zu Punkt 3) und [slice-declare-df-standardwert-vorhandene-spalte](../open/slice-declare-df-standardwert-vorhandene-spalte.md) (Punkt 1, zurückgestellt). — **Ausgang:** weiter offen: → `BEO-TDS/katalog-elemente-grenzen-der-angleichung` im Register.
- **Round-Trip von `schema reverse`.** Der Leser der Beschreibungsdatei nahm `unique_constraint` nicht an, das `schema reverse` neben `unique: true` schreibt; er liest es jetzt als Namen der Eindeutigkeit (`SPEC-035`). Andere Felder, die `schema reverse` schreibt, sind nur für die Fälle des Messprotokolls (Zeile a5) belegt. — **Ausgang:** entfallen: der Roundtrip am End-Stand besteht für alle fünf Elementarten (Messprotokoll Zeilen f1 bis f7: Server startet mit der geschriebenen Datei, `IDENTICAL`, erneutes `schema reverse` byte-gleich); die übrigen Felder, die `schema reverse` schreibt, sind nicht Gegenstand dieses Slice und gehören zum Validator-Lauf von [slice-abnahme-d-migrate](../open/slice-abnahme-d-migrate.md) (Verifikation V-4, INFO).
- **Zahl der Mutationen.** Der Rig des Implementers führt 20 Mutationen (`mut-summary.txt`), nicht 21; der Review fuhr 11 weitere, die Nachbesserung nach dem Review vier (Namensraum von Index und Eindeutigkeit, `unique_constraint`), der Verifier acht einzeln (alle rot). — **Ausgang:** entfallen (Zahl berichtigt).

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

- **Was hat funktioniert:** Die Katalogabfragen für Eindeutigkeiten, Fremdschlüssel, Indizes, Standardwerte und Prüfbedingungen antworten aus den mitgeteilten Tabellen; die Rohbytes beider Instanzen des echten Servers (M-21) und die Antworten der Bibliothek stimmen im Spaltenkopf und in den Zeilen überein (Verifikationsbericht, Abschnitt „Draht“). Die Strukturformen D1 bis D4 (`CREATE TABLE` mit Elementen, `ALTER TABLE … ADD CONSTRAINT`, `CREATE INDEX`) laufen mit d-migrate 1.12.0 bis `IDENTICAL`. Der Roundtrip `schema reverse` → Start des Testservers mit der geschriebenen Datei → `schema compare` → `schema reverse` besteht byte-gleich für alle fünf Elementarten (Messprotokoll Zeilen f1 bis f7). Gegen die Mutationen trägt die Testbasis: 20 (Implementer), 11 (Review), 4 (Nachbesserung), 8 von 8 gültigen (Verifier) fielen rot.
- **Was ging anders als geplant:** Die öffentliche Fläche wuchs (vier Typnamen, fünf Mitglieder, neue Parameterliste des Callbacks); der Nutzer entschied nach dem Bau per Auswahl, die Fläche zu behalten, die Folge-ADR ist [ADR-0046](../../adr/0046-oeffentliche-flaeche-der-katalog-elemente.md). Der Leser der Beschreibungsdatei nahm `unique_constraint` nicht an, das `schema reverse` schreibt: der Review fand es (F-7), der Roundtrip belegt die Behebung. Zwei Anweisungen von d-migrate sind nicht erkannt (`DROP CONSTRAINT IF EXISTS`, `DECLARE @df`); sie gehen an Folge-Slices.
- **Zuordnung Messung | Festlegung.** *Messung am echten Server* (M-21, je ein Fall je Instanz 2022 und 2025): Spaltenköpfe, Typen, Nullbarkeiten, Zeilen und Reihenfolge der Abfragen für Eindeutigkeiten, Primärschlüssel, Fremdschlüssel (mit Aktionen), Indizes (eingeschlossene Spalten vor Schlüsselspalten) und Prüfbedingungen (`([Menge]>(0))`). *Messung am Testserver mit d-migrate 1.12.0* (nicht am echten Server): die gesendeten Texte der Formen D1 bis D4, die Exit-Codes und `IDENTICAL`, der Roundtrip. *Festlegung (Setzung, in `SPEC-035` und `SPEC-036` gekennzeichnet):* Form der Prüfbedingung bei mehreren Vergleichen, Texten und Klammern, mehrspaltige Elemente, Namen der Fehler, Kennung von `CREATE INDEX`, Text als `nvarchar(4000)` ([ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) Entscheidung 7), Reihenfolge der Namen, Namen von Bedingungen je Tabelle statt je Schema.
- **Review und Verifikation.** Review `2026-11-09-9ae13838` ([Report](../../../reviews/2026-11-09-9ae13838-review-slice-katalog-indizes-defaults-fremdschluessel.md)): 2 HIGH, 6 MEDIUM, 3 LOW, behoben in `1b824ba3`. Einen zweiten Review der Nachbesserung gab es nicht; der Leser-Code der Nachbesserung (`SimDescriptionReader.kt`, `SimElementReader.kt`, `SimElementFields.kt`) hat nur das Kurzreview im [Verifikationsbericht](../../../reviews/2026-11-11-1b824ba3-verifikation-slice-katalog-indizes-defaults-fremdschluessel.md). Verifikation am End-Stand `1b824ba3`: `make clean-build` (571 s, 1341 PASSED), `make e2e` (8 Stufen), `make gates FORCE=1`, `make flaeche`, `make docs-check`, Zitate mit `tools/matrix-zitate.sh` gegen das Log, Roundtrip, 8 Mutationen, Merge-Probe. Closure am Merge-Stand (Basis `main` `9f302beb`, Merge `1213a249`): `make spec-abdeckung` einmal (1341 PASSED, kein roter Test, Matrix unverändert), `make gates` Exit 0 (die Test-Stage frisch nach dem Merge ohne roten Testfall; ein erster Lauf stand an einem toten Roadmap-Link des Merge, nach der Korrektur grün).
- **Grenzen.** (1) Die acht Grenzen im Register [katalog-elemente-grenzen-der-angleichung](../observations/BEO-TDS/katalog-elemente-grenzen-der-angleichung/state.md) gelten; Punkt 1 und 3 sind nur an einem Server ohne Prüfbedingung belegt, weil Punkt 2 sie maskiert (V-2), und Punkt 3 ist möglicherweise keine Abweichung vom echten Server (dann fällt er ohne Bau weg). (2) V-1 erledigt: das Messprotokoll trägt den Stand `c285cf21` und zusätzlich den Lauf am End-Stand `1b824ba3` (Zeilen f1 bis f7). (3) V-3 (INFO): die Fehlermeldung bei einem Namen, der mit `unique_constraint` zusammenfällt, zeigt auf die Tabelle statt auf die Spalte. (4) V-4 (INFO): `runCatching` im Leser fängt auch `Error` (zum Beispiel `OutOfMemoryError`) und meldet es als Namensdopplung; heute ohne Folge, weil nach den Vorprüfungen des Lesers nur noch `SimElementChecks.validate` scheitern kann. (5) Das Messprotokoll der d-migrate-Läufe gilt gegen den Testserver, nicht gegen einen echten SQL Server.
- **Steering-Loop-Eintrag:** keine Regel, kein Sensor und keine Skill-Zeile verkörpert; der Eintrag ist gezählt, nicht verkörpert. *Benannte Spec-Lücke* (getragen in der versionierten Spezifikation, `SPEC-036` Form D Absatz Grenzen und `SPEC-035`): die Grenzen 1 bis 8 des Registers. Gelernt: (a) Eine Beleg-Aussage im Register nennt die Bedingung, unter der sie gemessen ist; ein Fund, der andere maskiert (Punkt 2 maskiert 1 und 3), gehört mit seiner Maskierung ins Register, sonst trägt eine Aussage weiter als ihr Beleg (`AGENTS.md` §3.8). (b) Ein Slice, der den Leser der Beschreibungsdatei erweitert, fährt den Roundtrip `schema reverse` → Start des Testservers mit der Datei im eigenen Lauf; der Roundtrip fehlte im Bau und fiel erst im Review auf. Beide Punkte stehen als Einzelfall, ohne Schwelle von 3×.
- **Beobachtungs-Register (`../observations/`):** `evidence/slice-katalog-indizes-defaults-fremdschluessel.md` in `BEO-TDS/katalog-elemente-grenzen-der-angleichung/` ergänzt (Maskierung durch Punkt 2, Möglichkeit „Punkt 3 keine Abweichung“); Zeiger in `BEO-TDS/katalog-strukturabweisung-und-aenderungszaehler-weichen-vom-echten-server-ab` und `BEO-TDS/messung-am-echten-sql-server-fehlt` nachgezogen; Haken nach der Verifikation gesetzt (`BEO-TDS/haken-vor-verifikation-gesetzt` eingehalten).
- **Folge-Slices:** [slice-drop-constraint-form-d5](../in-progress/slice-drop-constraint-form-d5.md) (Größe S bis M) und [slice-declare-df-standardwert-vorhandene-spalte](../open/slice-declare-df-standardwert-vorhandene-spalte.md) (Größe M, zurückgestellt) — je eine Datei in `open/`, vor [slice-abnahme-d-migrate](../open/slice-abnahme-d-migrate.md) eingeordnet. Angelegt als Standardantwort auf die Empfehlung des Verifiers; **umkehrbar**, der Entscheid des Nutzers steht aus.
- **Risiken aus §6:** alle sechs tragen einen Ausgang: neue öffentliche Namen eingetreten ([ADR-0046](../../adr/0046-oeffentliche-flaeche-der-katalog-elemente.md)); Form der Antworten entfallen; DDL-Formen entfallen; Grenzen gegen das Zielbild weiter offen (Register, zwei Folge-Slices); Round-Trip entfallen; Zahl der Mutationen entfallen.
- **Drei Paarungen:** Anker — keine Regel verkörpert, nichts zu paaren. Folge-Slice — beide Slices liegen als Dateien in `open/`. Register — `BEO-TDS/katalog-elemente-grenzen-der-angleichung` trägt Zustand und Beleg, die Folge-Slices und die Abnahme zeigen darauf. Getragen.

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung.

**Vorgelagert — Sub-Area-Wahl prüfen:** Eine Sub-Area (`*`, Kürzel `TDS`); keine Aufteilung nötig.

**Vorgelagert — offene Beobachtungen sichten:** `BEO-TDS/messung-am-echten-sql-server-fehlt` (Eingang), `BEO-TDS/offene-punkte-ohne-traeger` (Herkunft: Verifikation V-3), `BEO-TDS/dod-wortlaut-ueber-der-bauart` (der Wortlaut der DoD folgt der Lieferung).

Alle berührten Sub-Areas GF.
