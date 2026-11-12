# Review-Report: slice-drop-index-if-exists — 2026-11-11

**Review-Art:** Code (gegen Plan, ADR und Hard Rules; nicht gegen die DoD, das ist die Frage des Verifiers).

**Gegenstand:** Diff `d019cf2d` (`main`, Merge von `slice-drop-constraint-form-d5`) .. `5b045ff9` im Zweig `worktree-agent-ac09ca337880435ea` (Commits `270e9617` Move, `29b6e084` Verweis-Nachzug, `d988c16d` Messung, `ad49c3ab` Bau, `2ee463fb` Spezifikation, Nutzer-Doku, Messprotokoll, Register, Plan, Roadmap, Matrix, `5b045ff9` detekt-Nachbesserung in `StructureChecks.kt`, `DMigrateWire.kt`, `SimulationBackendDropIndexTest.kt`: Code ohne Verhaltensänderung, Teil der Review-Fläche); 34 Dateien, +956/−80. Merge-Probe `git merge-tree` gegen `main` (`d019cf2d`): konfliktfrei.

**Skill:** `.harness/skills/reviewer.md` @ `5b045ff9` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-11

**Eingangs-Kontext** (die Verträge, gegen die geprüft wurde):

- Slice `slice-drop-index-if-exists` (Plan §3, DoD, Risiken §6, Nicht-Ziele), Muster: Closure-Notiz §7 von `slice-drop-constraint-form-d5`
- [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) (Accepted), [ADR-0046](../plan/adr/0046-oeffentliche-flaeche-der-katalog-elemente.md) (Accepted), [ADR-0042](../plan/adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) (Accepted, für die Entscheidungsfrage)
- [SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends), [SPEC-036](../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) (Form D6), SPEC-059, [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität)
- `AGENTS.md` §3.1 bis §3.8; Grundsatz des Nutzers „Spec ist Zielbild“ (der echte SQL Server ist die Referenz); vorheriger Report am Modul: `2026-11-11-e5c345aa`

## Selbst gefahren

| Lauf | Ergebnis |
|---|---|
| `make gates FORCE=1` | Exit 0, 30 s (Docker-Schichten `CACHED`, gleicher Eingang wie der Lauf des Implementers; kein frischer Beleg der Tests über diesen Cache hinaus) |
| `make flaeche` | Exit 0 (prüft Typnamen, keine Mitglieder) |
| `make docs-check` | 0 Befunde (1359 Dateien, vor Anlage dieses Reports) |
| `SimulationBackendDropIndexTest` allein (Kopie des Arbeitsbaums im Scratchpad, Image `i6-deps:lokal`, nicht als root) | 9 von 9 grün, 36 s |
| 12 Mutationen über enge Testklassen (`SimulationBackendDropIndexTest`, `ErrorMapperTest`) | alle rot, siehe Abschnitt Mutationen; 157 s |
| Dekodierung der Rohbytes `struktur-drop-index.messung`, 2022 und 2025 | siehe Abschnitt Echtheit |
| Wegwerf-Tests im Scratchpad: 13 Randformen des Parsers; Indexabfrage von d-migrate vor und nach `DROP INDEX` | siehe Abschnitt Parser und Katalog |
| `make clean-build`, `make e2e`, `make matrix-zitate`, `make spec-abdeckung`, d-migrate gegen einen laufenden Testserver | nicht gefahren (Sensor-Zuschnitt des Auftrags; der Lauf mit d-migrate ist Sache des Verifiers) |

Ablage: Scratchpad-Unterordner `rv/` meines Laufs (`p.py`, `rig.sh`, `mut.sh`, Logs); `i6/` des Implementers nur gelesen. Gesamtlaufzeit des Reviews rund 25 Minuten (Wanduhr 19:18 bis 19:43); größte Posten (Schätzung, außer den gemessenen): Lesen von Diff, Spezifikation, Nutzer-Doku und Ablage, Mutationslauf 157 s, Einzeltests je 37 s, `make gates` 30 s.

## Echtheit der Messung (Schwerpunkt a)

Alle 24 Anfragen der Ablage `bulk-merge-katalog/2022/struktur-drop-index.messung` habe ich aus den Rohbytes selbst dekodiert (ERROR: Nummer, State, Class, Text; DONE: Status, Befehl, Zähler); die Dekodierung der Datei von 2025 ist Zeile für Zeile gleich (`diff` der Auswertung, Namen und Meldungstexte ausgenommen). Ergebnis: Tabelle A des Messprotokolls stimmt, mit einer Einschränkung (F-1).

- Aufbau 01 bis 02: `DONE` `0x00c6`; 03 bis 06 `CREATE INDEX` (gewöhnlich, eindeutig, zwei Spalten): `DONE` Status 0, Befehl `0x00c8`, ohne `COUNT`.
- Erfolg (07, 08, 11, 12, 18, 20, 21): `DONE` Status 0, Befehl `0x00c9`, Zähler 0, kein weiteres Token.
- 3701: Class 11, State 7 (09, 10, 13, 23), State 6 bei unbekannter Tabelle (19); danach `DONE` Status `ERROR`, Befehl `0x00c9`. 3723: Class 16, State 5 (14, 15, Eindeutigkeit), State 4 (16, 17, Schlüssel); `DONE` Status `ERROR`, Befehl `0x00fd`. Nie ein zweites ERROR.
- 22: zwei `DONE` (erstes `MORE`), beide `0x00c9`; 23: `ERROR DONE(MORE|ERROR) DONE`, die zweite Anweisung läuft.
- Der Name gilt je Tabelle: 20 (`DROP INDEX [ix_idx_name] ON [idx_eltern]` ohne `IF EXISTS` gelingt, nachdem 07 den gleichnamigen Index am Kind entfernte) und 09 (danach 3701 am Kind) belegen es; ebenso 12 und 13 für den eindeutigen Index und die Schreibweise.
- Die Aussagen des Protokolls zu Zeile 11 („der Index der Eltern bleibt“) und Zeile 21 („Treffer“) trägt die Ablage nicht (F-1).
- Image-Digests und Versionen in den Kopfzeilen der Ablage (2022, 2025) stimmen mit dem Protokoll überein.

## Parser, Planer und Katalog (Schwerpunkte b, c)

- Der Zweig `DROP` in `StructureParts.drop()` ist Token-basiert: kein `Regex`, keine Rekursion je Zeichen. Der Test mit 100 000 Zeichen (quotiert, unquotiert, als Tabelle) ist rot, wenn `drop()` den Namen durch einen Regex mit Alternation unter `+` laufen lässt (Mutation M12: `StackOverflowError`).
- 13 Randformen im Scratchpad (Wegwerf-Test, nicht im Repo): `[a]]b]` und `[ix.name]` als Namen treffen den Index; ein Index namens `IF` (quotiert) und `ix_é` treffen; `;;`, dreiteiliger Tabellenname, `GO`, `WITH (ONLINE = ON)` und `OPTION (…)` hinter der Tabelle sind `TDS_UNSUPPORTED_SQL`; Zeilenumbruch und Tab zwischen den Wörtern, ein Zeilenkommentar am Ende und ein Name in doppelten Anführungszeichen sind erkannt; die alte Schreibweise `DROP INDEX t.ix` ist abgewiesen, zwei Namen und zwei Anweisungen ebenso (Tests des Slice). Ergebnis je Form wie erwartet, kein Absturz (F-9 zu einer Randform).
- `PlanResult.Unchanged`: der Callback wird nicht gerufen (Test `IF EXISTS an einer unbekannten Tabelle …`; Mutation E3 und E4 rot), der Zustand im Beispielspeicher bleibt unverändert; `IF EXISTS` ohne Treffer an einer bekannten Tabelle ruft den Callback mit der unveränderten Tabelle (Test zählt die Aufrufe).
- Katalog: ein Wegwerf-Test fragt die Indexabfrage von d-migrate vor und nach `DROP INDEX IF EXISTS [ix_a] ON [t];` und nach `DROP INDEX [ix_b] ON [t];`: Zeilen `ix_a, ix_b` → `ix_b` → leer. Die Abfrage liest den Zustand aus dem Anbieter; kein Test im Repo nennt das (F-8).
- `structureLock` und Bestand: `TableStore.replace` übernimmt die Tabelle nach der Anweisung und die Zeilen aus dem alten Stand, damit auch bei `DROP INDEX`; keine Änderung am Testserver nötig. Die erneute Planung unter der Sperre kennt `PlanResult.Unchanged` nicht (F-4).
- Der Name des Schlüssels ist im Modell nicht geführt: `DROP INDEX <pk> ON t` ergibt 3701 (mit `IF EXISTS` Erfolg), der Server 3723 State 4; benannt in Spezifikation (c), Nutzer-Doku, Register Punkt 11 und im Test (Zeilen `pk_kunden`). Eine bekannte Grenze, kein Befund.
- Beschreibungsdatei: `sim.yaml` und die Leser des Testservers sind unverändert; Indizes aus der Datei sind per `DROP INDEX` entfernbar, ohne dass die Datei neu gelesen wird.

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| F-1 | MEDIUM | Zeile 21 (`IF EXISTS … ON [dbo].[idx_eltern]`) und Zeile 11 antworten mit `DONE 0x00c9`, ob der Index vorhanden war oder nicht; nach beiden folgt kein Lesen, das Treffer von Nichttreffer trennt. Das Protokoll nennt Zeile 21 „Treffer“ und Zeile 11 „der Index der Eltern bleibt (Zeile 21 entfernt ihn)“, der Belegtest führt 21 unter „Treffer“, die Spezifikation (Grenze d) nennt das Schema `dbo` „gemessen“. | `AGENTS.md` §3.8, [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität) | `docs/messungen/drop-index-d-migrate-2026-11-11.md` · „(Tabelle mit Schema `dbo`, Treffer)“ und „der Index der Eltern bleibt (Zeile 21 entfernt ihn)“; `MessungBulkBelegTests.cs` · „21 (mit Schema dbo)“; `spec/spezifikation.md` · „das Schema der Tabelle ist nur `dbo` gemessen“ | ja: Rohbytes der Zeilen 11, 20, 21 | Aussage breiter als die Antwort, die sie belegt |
| F-2 | MEDIUM | Die Ablage dieses Diffs misst `CREATE INDEX` (Zeilen 03 bis 06, beide Instanzen: `DONE` ohne `COUNT`, Befehl `0x00c8`; der Belegtest prüft es), die Spezifikation und die Nutzer-Doku sagen weiter, die Kennung von `CREATE INDEX` am echten Server sei nicht gemessen; das Register (Punkt 13) und das Protokoll (Befunde) halten die Messung nicht fest. | `AGENTS.md` §3.8; [ADR-0042](../plan/adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) Entscheidung 2 | `spec/spezifikation.md` · „die Kennung des Servers ist nicht“ (SPEC-035, Absatz zum Callback; der Satz bricht hinter „nicht“ um); `docs/user/referenz-konfiguration.md` · „die Kennung von `CREATE INDEX` am echten Server ist nicht gemessen“ (Zeile der Tabelle `CREATE INDEX`, `DROP INDEX`); `docs/plan/planning/observations/BEO-TDS/katalog-elemente-grenzen-der-angleichung/observation.md` · Punkt 13 | ja: Grep über `spec/`, `docs/user/` | Fortschreibung lässt die zweite Fundstelle stehen |
| F-3 | MEDIUM | Die Plan-Tabelle §3 nennt drei im Diff berührte Dateien nicht: `docs/plan/planning/done/slice-drop-constraint-form-d5.md` (drei Zeiger auf den Slice), `docs/plan/planning/in-progress/slice-abnahme-d-migrate.md` und `docs/plan/planning/welle-testserver-manuell.md` (Tabellenzeile und Reihenfolge); der Plan von D5 hat die gleiche Gruppe als Zeile geführt. | Reviewer-Skill, Kategorie Plan-Tabelle nennt die berührten Dateien nicht | `docs/plan/planning/done/slice-drop-index-if-exists.md` · „`docs/plan/planning/in-progress/roadmap.md` \| update“ (Zeile der Tabelle; die drei Dateien fehlen) | ja: `git diff --name-status` gegen die Tabelle | Plan-Tabelle ohne die Zeiger-Dateien |
| F-4 | LOW | Die erneute Planung unter `structureLock` kennt `PlanResult.Unchanged` nicht: ändert ein Anbieter (ein anderer Schreiber, den die Sperre nicht bindet) zwischen Planung und Sammeln den Katalog so, dass die Tabelle fehlt, endet `DROP INDEX IF EXISTS` mit `TDS_BACKEND_ERROR` („Änderung fehlgeschlagen“) statt mit dem Erfolg, den der Server für eine unbekannte Tabelle sendet. Kein Test deckt den Zweig. | Maintainability; [SPEC-036](../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) (erneute Planung unter der Sperre) | `src/main/kotlin/io/github/pt9912/tdswire/simulation/SimulationBackend.kt` · „else -> -1L“ | nein: Wegwerf-Backend mit Katalogwechsel nötig | Fehlerzweig der erneuten Planung ohne Test und ohne Zuordnung des neuen Ergebnisses |
| F-5 | LOW | Die Nutzer-Doku sagt zu `DROP INDEX` „Der Name gilt je Tabelle (derselbe Name an einer anderen Tabelle bleibt)“ und „Gemessen am echten SQL Server … sind die Antworten für …“ ohne die Bedingung, dass alle Fälle Tabellen im Schema `dbo` sind (die Spezifikation nennt sie; der Absatz zu `DROP CONSTRAINT` in derselben Datei auch). | `AGENTS.md` §3.8 | `docs/user/einbettung-und-betrieb.md` · „Der Name gilt je Tabelle (derselbe Name an einer anderen Tabelle bleibt)“ | ja: Lesen | Zusage ohne ihre gemessene Bedingung im selben Satz |
| F-6 | INFO | Zwei Abweichungen vom gemessenen Verhalten sind gebaut, benannt (Spezifikation Grenze b, Register Punkt 13) und brauchen keine öffentliche Fläche: State 6 statt 7 bei unbekannter Tabelle ohne `IF EXISTS` und der Befehl `0x00c9` statt `0x00fd` im `DONE` nach 3701. `ErrorRule` ist intern; die Entscheidung des Nutzers über `StatementKind` betrifft beide nicht. Zuständig: Planner (Zielbild-Lücke ohne Träger, Trigger des Registers hängt an der Fläche). | Grundsatz „Spec ist Zielbild“ | `spec/spezifikation.md` · „(b) das `DONE` nach 3701 trägt bei der Bibliothek `0x00fd`“ | nein | Bekannte Abweichung mit Trigger an der falschen Frage |
| F-7 | INFO | Der DoD-Punkt „zweites `schema migrate --execute` mit geändertem Index endet mit Exit 0“ und der Satz der Spezifikation „gemessen am Testserver“ ruhen auf Tabelle C des Protokolls: ein Handlauf mit `docker run`, dessen Skript nicht im Repo liegt (das Protokoll nennt es). Die Indexabfrage nach `DROP` habe ich im Wegwerf-Test bestätigt (siehe oben), den Lauf von d-migrate nicht. Zuständig: Verifier. | Reviewer-Skill, Kategorie Messzahl aus einem Erzeuger, der nicht im Repo liegt | `docs/messungen/drop-index-d-migrate-2026-11-11.md` · „das Skript, das sie nacheinander ausführte, liegt nicht im Repo“ | nein | Beleg ohne wiederholbaren Erzeuger im Repo (benannt) |
| F-8 | INFO | Kein Test im Repo prüft, dass die Katalogabfrage nach `DROP INDEX` den Index nicht mehr liefert; die Wirkung folgt aus dem Zustand des Anbieters und aus dem Handlauf (Tabelle C). Kein neuer Code betroffen. | Maintainability | `src/test/kotlin/io/github/pt9912/tdswire/simulation/SimulationBackendDropIndexTest.kt` · „der Text von d-migrate nimmt den Index aus der Tabelle“ (prüft `SimTable.indexes`) | ja: Wegwerf-Test aus dieser Prüfung | Wirkung auf die Katalogantwort ohne Test |
| F-9 | INFO | Die Änderungsprotokoll-Zeile zu D6 steht vor der Zeile zu D5, obwohl D5 zeitlich vorausging; dazu nimmt `DROP INDEX IF EXISTS IF ON t` (unquotiertes `IF` als Name) die Form an, der Server wird das Schlüsselwort vermutlich abweisen (nicht gemessen). | Maintainability | `spec/spezifikation.md` · „\| 2026-11-11 \| `DROP INDEX [IF EXISTS] <Name> ON <Tabelle>` als Form D6 erkannt“ | nein | Reihenfolge des Änderungsprotokolls |

## Mutationen (Schwerpunkt d)

Eigene Läufe in einer Kopie des Arbeitsbaums (je Mutation frisch gespiegelt, Worktree unberührt); Klassen: `SimulationBackendDropIndexTest`, `ErrorMapperTest`. Die Nummern M4 bis M20 folgen dem Auftrag, E1 bis E6 sind eigene. Spalte „rot“: rote Testfälle laut Log.

| Nr. | Mutation | Klasse | rot | Erster roter Test |
|---|---|---|---|---|
| M4 | `ifExists` invertiert im Parser | DropIndex | 6 | `die Form D6 wird … erkannt`, `der Index einer Eindeutigkeit …`, `ein Name von 100000 Zeichen …` |
| M5 | `IF EXISTS` an unbekannter Tabelle als Fehler `NOT_AN_INDEX` | DropIndex | 1 | `IF EXISTS an einer unbekannten Tabelle ist ein Erfolg ohne Callback` |
| M6 | Zweig der Eindeutigkeit entfernt (`false ->`) | DropIndex | 1 | `der Index einer Eindeutigkeit ist ein Fehler mit und ohne IF EXISTS` |
| M12 | `drop()` prüft den Namen mit `Regex("(?:a\|b\|[^ab])+")` | DropIndex | 1 | `ein Name von 100000 Zeichen ist erkannt und beantwortet` (`StackOverflowError`) |
| M17 | Callback bekommt `table` statt der gekürzten Tabelle | DropIndex | 3 | `der Name gilt …`, `der Text von d-migrate …`, `ohne IF EXISTS …` |
| M20 | Vergleich der Namen mit Beachtung der Schreibweise | DropIndex | 2 | `der Name gilt ohne Beachtung der Schreibweise und je Tabelle` |
| E1 | State 6 statt 7 für 3701 | ErrorMapperTest | 1 | `Strukturabweisung ist ERROR …` |
| E2 | Abschluss `ALTER_TABLE` (`0x00d8`) statt `UNKNOWN` beim Treffer | DropIndex | 1 | `der Text von d-migrate nimmt den Index aus der Tabelle` |
| E3 | `PlanResult.Unchanged` mit Zähler 1 | DropIndex | 1 | `IF EXISTS an einer unbekannten Tabelle …` |
| E4 | `IF EXISTS` ohne Treffer an bekannter Tabelle ohne Callback | DropIndex | 1 | `IF EXISTS ohne Treffer ist ein Erfolg ohne Aenderung` |
| E5 | Treffer in jeder Tabelle statt nur in der genannten | DropIndex | 3 | `IF EXISTS ohne Treffer …`, `ohne IF EXISTS …`, `IF EXISTS an einer unbekannten Tabelle …` |
| E6 | Class 16 statt 11 für 3701 | ErrorMapperTest | 2 | `Strukturabweisung ist ERROR …`, `Gruppen der Entscheidung …` |

Die Zahl „rot“ zählt Zeilen mit `FAILED` ohne Gradle-Aufgabenzeilen; jede Mutation hat mindestens einen roten Testfall. Die Mutation „Befehl `0x00c9` statt `0x00d8`“ gibt es in der Bibliothek nicht, weil die Art `UNKNOWN` die Kennung 0 schreibt (E2 deckt die Art); die Kennung `0x00c9` des Servers trägt nur die Ablage und ihr Belegtest (`MessungBulkBelegTests`), den `make gates` aus dem Cache fuhr; eine Mutation der Ablage habe ich nicht gefahren.

## Urteil zur Entscheidungsfrage: `StatementKind` um `DROP_INDEX` und `CREATE_INDEX` erweitern?

Die Frage gehört dem Nutzer; hier mein Urteil für ihn.

- **Zuschnitt und Namen.** Zwei Mitglieder, `DROP_INDEX` (Kennung `0x00c9`, ohne `COUNT`) und `CREATE_INDEX` (`0x00c8`, ohne `COUNT`), nach dem Muster `CREATE_TABLE`/`ALTER_TABLE`. Beide gehören zusammen: die Ablage misst beide in einem Szenario, vier Fälle von `CREATE INDEX` je Instanz (gewöhnlich, eindeutig, zwei Spalten; `INCLUDE`, Filter und absteigende Spalte nicht), und `DROP CONSTRAINT` bleibt bei `ALTER_TABLE` wie am Server. Wer nur `DROP_INDEX` anlegt, lässt `CREATE INDEX` bei `UNKNOWN`, obwohl es gemessen ist, und hinterlässt F-2.
- **[ADR-0042](../plan/adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md).** Entscheidung 2 (Nichtgemessenes ohne Mitglied) legt die Bedingung selbst fest: „bekommt kein Mitglied, bis es gemessen ist“. Mit der Messung ist sie erfüllt; die ADR wird nicht widersprochen und nicht überschrieben (`AGENTS.md` §3.5), ein `Supersedes` ist nicht nötig. Die späteren Mitglieder (`MERGE` und andere) kamen ebenfalls ohne eigene ADR, mit Eintrag im Änderungsprotokoll der Spezifikation und in der Liste von SPEC-059. Eine Folge-ADR ist nur nötig, wenn der Nutzer von Entscheidung 2 abweichen will (ein Mitglied für Nichtgemessenes, eine andere Zählregel) oder die Regel „Fläche wächst nur mit Entscheid“ ([ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Entscheidung 4, [ADR-0046](../plan/adr/0046-oeffentliche-flaeche-der-katalog-elemente.md) als Folge des Befunds F-1 vom 2026-11-09) neu fassen will. Der Entscheid des Nutzers vor dem Bau gehört in den Plan des Folge-Slice und in den Eintrag der Spezifikation.
- **Wirkung auf `when`-Aufrufer.** Ein Kotlin-`when` ohne `else` über `StatementKind` in Fremdcode übersetzt danach nicht mehr (Quelltext-Bruch, vor `1.0` nach [ADR-0021](../plan/adr/0021-oeffentlicher-integrations-einstieg.md) hinnehmbar, die Nutzer-Doku warnt davor: „muss alle Mitglieder nennen“). Der Bruch trifft vor allem die Bibliothek selbst (`Token.forKind`, `Token.countsRows`, `SessionTransaction.apply`), und der Compiler erzwingt dort die Ergänzung; Embedder schreiben die Art, sie lesen sie selten. Java-`switch` bleibt übersetzbar. Ein Backend, das `UNKNOWN` meldet, bleibt bei Kennung 0 mit `COUNT`.
- **Wirkung auf die Antwort.** Mit den Mitgliedern schreibt die Bibliothek `DONE` ohne `COUNT` und die Kennungen `0x00c8`/`0x00c9`: der Draht ändert sich für `CREATE INDEX`, das heute schon als `UNKNOWN` läuft, und für `DROP INDEX`. Die Reaktion von mssql-jdbc auf ein `DONE` ohne `COUNT` bei DDL ist für `CREATE TABLE` gefahren; für die beiden neuen Arten steht ein Lauf von d-migrate noch aus (Sensor des Verifiers). Zustandswechsel (`ENVCHANGE` 8/9) bleiben ungemessen und ungebaut ([ADR-0042](../plan/adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) Entscheidung 5); `SessionTransaction.apply` kennt nur `CREATE_TABLE` als Beginn, die neuen Arten ließen den Zustand unverändert (Setzung, wie `ALTER_TABLE`).
- **Empfehlung.** Erweitern, in einem eigenen kleinen Folge-Slice nach `slice-drop-index-if-exists` (Fläche: zwei Konstanten, Liste von SPEC-059, Einbettungstest, Tabelle der Kennungen in SPEC-031, Nutzer-Doku, Register Punkt 13 entfällt). Grund: dieser Slice verspricht in DoD und Plan „die öffentliche Fläche wächst nicht“, Review und Verifikation laufen darauf; ein Nutzer-Entscheid darüber kann nachgezogen werden, ohne diese Closure zu verschieben. F-2 ist unabhängig davon in diesem Slice zu berichtigen.

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `src/main/kotlin/…/simulation/` (Parser, Planer, Prüfungen, Backend) | geprüft, ohne Befund außer F-4; keine Regex im neuen Pfad, keine Suppression |
| `src/main/kotlin/…/protocol/error/` | geprüft, ohne Befund (Nummern, Class, State, offene Verbindung, Positivliste vollständig; Mutationen E1, E6 rot) |
| `src/test/kotlin/…`, `src/integrator/kotlin/…` | geprüft, ohne Befund außer F-8; neue Tests haben Wächter, KDoc nennt Messzeilen und Setzungen |
| `referenztest-dotnet/` (Szenario, Ablage, Belegtests) | geprüft, Befund F-1; Zeilenzahl der Ablage (21) und Szenarienzahl im Make-Fragment stimmen |
| `spec/spezifikation.md` | geprüft, Befunde F-2, F-9; Fehlertabelle, Positivliste und Form D6 sind gegen Code und Ablage gleich |
| `docs/user/*` (alle fünf Dateien gelesen, Grep auf Abwesendes und Geplantes) | geprüft, Befunde F-2, F-5; keine Chronik, kein „bisher“/„noch nicht“/„Zielzustand“ in den neuen Sätzen; `README.md` und `referenz-ereignisse.md` ohne Bezug |
| `docs/messungen/` | geprüft, Befund F-1; Erzeuger im Repo und Handlauf getrennt benannt |
| `docs/plan/planning/` (Slice, Roadmap, Welle, Register) | geprüft, Befund F-3; keine Haken gesetzt (alle `- [ ]`), Risiken §6 ohne Ausgang (Closure steht aus), Zustandsfelder ohne Chronik, Roadmap-Ruhe-Marker stimmt |
| `harness/README.md`, `config/`, `Dockerfile`, `Makefile`, `tools/` außer Kommentarzahl | nicht berührt; Zellen ≤ 200 Zeichen entfällt |
| `5b045ff9` (detekt-Nachbesserung) | geprüft, ohne Befund: Verhalten gleich (`withoutIndex`), `const val`, Zeilenumbruch |

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 3 |
| LOW | 2 |
| INFO | 4 |

**Finding-Klassen dieses Laufs:** Aussage breiter als die Antwort, die sie belegt · Fortschreibung lässt die zweite Fundstelle stehen · Plan-Tabelle ohne die Zeiger-Dateien · Fehlerzweig der erneuten Planung ohne Test · Zusage ohne ihre gemessene Bedingung im selben Satz · Bekannte Abweichung mit Trigger an der falschen Frage · Beleg ohne wiederholbaren Erzeuger im Repo · Wirkung auf die Katalogantwort ohne Test · Reihenfolge des Änderungsprotokolls
