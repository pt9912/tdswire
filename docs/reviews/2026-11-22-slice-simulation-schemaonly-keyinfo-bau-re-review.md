# Review-Report: slice-simulation-schemaonly-keyinfo (Re-Review der Nachbesserung des Baus von Form F) — 2026-11-22

**Review-Art:** Re-Review (Skill „Nachbesserung mit Code ohne zweiten Review“): nur die Commits nach dem Report-Stand des Erstreviews, gegen Plan (Slice §1 bis §6), [ADR-0059](../plan/adr/0059-schemaonly-ist-eine-erkannte-form-keyinfo-bleibt-abgewiesen.md), SPEC-031, SPEC-036 und `AGENTS.md` §3.1 bis §3.8. Nicht gegen die DoD (Sache des Verifiers).

**Gegenstand:** `git diff 539cdb8f..HEAD` des Zweigs `slice-simulation-schemaonly-keyinfo` (HEAD `ee6d1c0c`), 17 Dateien, +211/−32 Zeilen: `709984d7` (Code: `StatementParser`, KDoc in `BulkForms` und `SchemaOnlyExecution`; Tests: ein neuer Fall in `SchemaFormsTest`, drei Texte und KDoc in `SimulationBackendSchemaOnlyTest`), `59f27531` (Register `BEO-TDS/spaltenflags-der-tabellenspalten-weichen-von-der-referenz-ab`, Folge-Slice `open/slice-simulation-spaltenflags-an-referenz-angleichen.md`), `f315db8e` (Nachzug: SPEC-036, Messprotokoll W-27, Matrix, Register-Stand, Plan-Tabelle, Mutationsbeleg M26, Link-Korrekturen im Erstreview), `3363fdd8` (Umbau nach detekt: `parse(…, schemaOnly)`), `ee6d1c0c` (Mutationsbeleg an den Umbau angepasst). Erstreview: [2026-11-22-slice-simulation-schemaonly-keyinfo-bau-review.md](2026-11-22-slice-simulation-schemaonly-keyinfo-bau-review.md) (0 HIGH, 3 MEDIUM, 3 LOW, 3 INFO).

**Skill:** `.harness/skills/reviewer.md` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-22

**Eingangs-Kontext:** Diff `539cdb8f..HEAD`; der Erstreview als vorheriger Befund am selben Gegenstand; [ADR-0059](../plan/adr/0059-schemaonly-ist-eine-erkannte-form-keyinfo-bleibt-abgewiesen.md) (Entscheidungen 2, 3, 4, 6); `spec/lastenheft.md` (`TDS-FA-11`, [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität)); `AGENTS.md` §3; Nutzerentscheidungen 2026-11-22 aus dem Auftrag (Spaltenflags: Register und Folge-Slice; drei Setzungen bleiben Setzung ohne Messung). Die Messung G-8 im anderen Worktree ist nicht Gegenstand; geprüft ist nur, ob die Aussagen hier als Setzung gekennzeichnet bleiben.

**Ausgeführte Sensoren** (Docker, Wegwerf-Kontexte im Scratchpad `…/scratchpad/rv2/`, Image der Stufe `deps` aus dem Erstreview, ohne Netz, `--continue`; nichts davon im Repo; Wirkung je Mutation aus der Ausgabe gezählt):

1. Grundlauf ohne Mutation mit dem Wegwerf-Test `RvProbe2Test` (nicht im Repo): sieben Klassen aus `:test` plus `RvProbe2Test`, `integratorTest` mit `SchemaOnlyEmbeddingTest` und `StatementKindEmbeddingTest`: BUILD SUCCESSFUL, 1 m 34 s einschließlich Bau. Der Test gibt `recognize` für 20 Texte aus (Tabelle unten) und die Ereignisse des Backends für zwei.
2. Elf Mutationen (Tabelle unten): M26 und M2, M3, M10, M15, M17, M21, M22, M23 aus dem Mutationsbeleg sowie zwei eigene (X3, X4). Alle rot.
3. Handzählung der Testfälle (`@Test`) der sieben Klassen und der Messdateien (`IsReadOnly=False` je Instanz), Abgleich Plan-Tabelle §3 und Kopf „Berührte Spec-Stellen“ gegen `git diff --stat 7def244b..HEAD` und die Abschnitte der Hunks von `spec/spezifikation.md` (Zeile je Hunk gegen die vorangehende Überschrift), `grep` nach den Namen der umbenannten Funktionen und nach „beim Verifier“.

Nicht gefahren (Auftrag und Tabelle `harness/README.md` §Minimal agent workflow): `make gates`, `make clean-build`, `make e2e`, `make kompatibilitaet`, `make vergleichslauf-d`, `make spec-abdeckung`, `make detekt` (der Umbau ist am Diff gelesen). Keine Messsperre angelegt; `free -m` verfügbar 12,8 bis 15,0 GB, frei 5,9 bis 8,4 GB (Grenze 4000 MB gehalten). Last des Hosts mäßig (Lastmittel 6 bis 13).

## Stand der Findings des Erstreviews

| Nr | Stufe | Stand | Beleg |
|---|---|---|---|
| F-1 | MEDIUM | **behoben** (Code, Test, Beleg) | `StatementParser.parse(…, schemaOnly)` liest den Rest der Probe ohne Form F; alle neun Texte der Überschneidung sind im HEAD `NotRecognized` und werden unter M26 einzeln `SchemaOnly` (Tabelle unten); die Probe ist unverändert (`SimulationBackendBulkTest` grün; X4 macht vier Probe-Tests rot) |
| F-2 | MEDIUM | **behoben im Sinn der Nutzerentscheidung** (Träger statt Code) | Register mit Evidenz, Folge-Slice nach Vorlage, W-27 und „Grenzen“ im Messprotokoll nennen Abweichung, Folge `IsReadOnly` („nicht gemessen“) und beide Träger; der Code bleibt unverändert; Aussagen tragen (siehe Prüfpunkt 4) |
| F-3 | MEDIUM | **behoben bis auf einen Namen** (R-1) | `Statement.kt`, Register-Stand, Messprotokoll in der Tabelle; `FixedForms.kt` und SPEC-035 entfernt; SPEC-002, SPEC-004, SPEC-030, `TDS-FA-11.a` und „Offene Punkte“ im Kopf; alle 15 Hunks von `spec/spezifikation.md` liegen in genannten Abschnitten |
| F-4 | LOW | **behoben** | Cancel in Form F ist als Setzung gekennzeichnet in SPEC-036, KDoc von `SchemaOnlyExecution`, KDoc des Tests, Slice §6 |
| F-5 | LOW | **behoben** | W-27 trennt `0x0001` (Nullbarkeit) von `0x0008` (`usUpdateable` unbekannt, KDoc von `TdsType.kt`) |
| F-6 | LOW | **behoben** | Matrix: „hier nicht belegt“; Register-Stand: „nach dem Bau nicht belegt“; kein Rollen-Text mehr in den beiden Zellen |
| F-8 | INFO | zur Kenntnis genommen | Slice §6 nennt die Lücke und den Träger `slice-simulation-austragung-der-ausfuehrungen-testen`; KDoc des Tests nennt die Grenze der Austragung |
| F-9 | INFO | zur Kenntnis genommen | Setzung „Fehler im RPC“ als nicht gemessen und nicht getestet in SPEC-036, KDoc des Tests, Slice §6 |

## Findings

### R-1 — LOW
- `kategorie`: LOW
- `quelle`: `AGENTS.md` §3.7 (Slice-Plan nennt den unwirklichen Zustand), Skill-LOW „Test-KDoc oder Slice-Plan nennt den abwesenden oder unwirklichen Zustand“
- `pfad`: `docs/plan/planning/done/slice-simulation-schemaonly-keyinfo.md` · „liest ihren Rest nicht als Form F (`parseInProbe`)“
- `befund`: Die Plan-Tabelle §3 nennt eine Funktion `parseInProbe`, die es nicht gibt: der Umbau `3363fdd8` hat sie zugunsten von `parse(…, schemaOnly = false)` entfernt, den Mutationsbeleg zog `ee6d1c0c` nach, die Plan-Tabelle nicht (`grep parseInProbe` über `src/`, `docs/`, `spec/`: ein Treffer, nur dort). Eine Suche nach dem Namen führt ins Leere.
- `verifizierbar`: ja (`grep -rn parseInProbe`)
- `klasse`: Umbenennung zieht die zweite Fundstelle im Plan nicht nach

### R-2 — LOW
- `kategorie`: LOW
- `quelle`: `AGENTS.md` §3.8 (Zahl ungenauer als der Beleg), Skill „Mutationsbeschreibung nennt die gedachte statt der gemessenen Wirkung“ (nur die Zahl)
- `pfad`: `docs/messungen/mutationen-schemaonly-form-f-2026-11-22.md` · „die sieben Klassen aus `:test` des Ausgangslaufs (ohne die zwei Einbettungstests) mit der Änderung: 67 Testfälle grün“
- `befund`: Die sieben Klassen aus `:test` haben 66 Testfälle (Handzählung: 7, 12, 6, 11, 12, 13, 5; im Lauf mit dem Wegwerf-Test stehen „67 tests“, davon einer der Wegwerf-Test). Die 67 des Beleges ergeben sich nur mit `SchemaOnlyEmbeddingTest` (ein Testfall) aus dem `integratorTest`, der Satz sagt „ohne die Einbettungstests“. Die Zahl „2 von 29“ der drei Klassen stimmt (6 + 12 + 11), die Wirkung der Mutation ist richtig beschrieben.
- `verifizierbar`: ja (Zählung der `@Test` je Klasse)
- `klasse`: Zahl im Mutationsbeleg passt nicht zur genannten Klassenmenge

### R-3 — INFO
- `kategorie`: INFO
- `quelle`: [ADR-0059](../plan/adr/0059-schemaonly-ist-eine-erkannte-form-keyinfo-bleibt-abgewiesen.md) Entscheidung 6 (Probe unverändert), `AGENTS.md` §3.8
- `pfad`: `src/main/kotlin/io/github/pt9912/tdswire/simulation/BulkForms.kt` · „else -> metadataOnly(read(tokens.subList(FMTONLY_LENGTH, tokens.size).withoutOff()))“
- `befund`: Die Probe liest ihren Rest weiter mit der vollen Erkennung außer Form F, also auch mit der Probe selbst: `SET FMTONLY ON SET FMTONLY ON SELECT Id FROM person` ist `Recognized` mit zwei Konstantentermen (gefahren, Zeile „doppelt“ in der Tabelle unten). Das Verhalten ist älter als Form F und nicht Gegenstand der Nachbesserung; weder SPEC-036 noch ein Test nennt die Doppelung. Keine Aktion erwartet; die Grenzen der Probe misst, soweit sie Träger haben, der Slice [slice-qa-messung-anweisungsformen-grenzen](../plan/planning/in-progress/slice-qa-messung-anweisungsformen-grenzen.md).
- `verifizierbar`: ja
- `klasse`: Probe nimmt eine Doppelung ihrer eigenen Einleitung an (vorbestehend)

### R-4 — INFO
- `kategorie`: INFO
- `quelle`: Auftrag (Messung G-8 im anderen Worktree), `AGENTS.md` §3.8
- `pfad`: `src/main/kotlin/io/github/pt9912/tdswire/simulation/BulkForms.kt` · „`SET FMTONLY ON SELECT 2` ist also die Form C und liefert die Zeile (Setzung des“; `spec/spezifikation.md` · „hinter `SET FMTONLY ON` ist `SELECT <Ganzzahl>` erkannt und liefert die Zeile (`SET FMTONLY ON SELECT 2`)“
- `befund`: Beide Fundstellen kennzeichnen die Aussage als Setzung („am Server nicht gemessen“, Absatz „Ungemessene Setzungen“); die Nachbesserung ändert sie nicht und behauptet sie nicht als gemessen. Der Grundlauf bestätigt das Verhalten des Codes (`SET FMTONLY ON SELECT 2` → `Fixed(IntegerConstant(2))`). Bestätigt G-8 die Messung (COLMETADATA ohne Zeile), widerspricht sie der Setzung an diesen zwei Stellen und einer Zeile der Plan-Tabelle des Slice `slice-qa-messung-anweisungsformen-grenzen`; die Angleichung gehört dann dorthin, nicht in diesen Slice.
- `verifizierbar`: ja (`grep`)
- `klasse`: Setzung korrekt gekennzeichnet, mögliche Gegenmessung außerhalb des Slice

## Befunde der Nachrechnung

**(1) F-1, Erkennung.** Der Grundlauf (Image `deps`, HEAD) gibt für alle neun Texte der Überschneidung `NotRecognized(UNEXPECTED_TOKEN)`: `SET FMTONLY ON` vor `SET FMTONLY OFF; SET FMTONLY ON; <Auswahl>` mit und ohne schließendes `SET`, mit und ohne `;`, `EXEC` und `EXECUTE sp_executesql N'…'` (auch ohne `EXEC`), verschachtelt (`SET FMTONLY ON SET FMTONLY ON …`, auch als `sp_executesql`-Text), mit `OR`-Folge. Das Backend antwortet auf die zwei ersten Texte mit einem einzigen `Failure(TDS_UNSUPPORTED_SQL)` ohne Abschluss. Unter M26 (Rekursion wieder mit `parse(it)`) wird jeder dieser neun Texte einzeln `SchemaOnly` (nur `… SELECT 2` bleibt nicht erkannt, es ist kein Text der Form S1), das Backend antwortet mit `UpdateCount(SET_FMTONLY_OFF)`, `UpdateCount(SET_FMTONLY_ON)`, `ResultStart`, `ResultEnd`; das schließende `SET` entfällt still (`closed=false`), also der Fehler des Erstreviews. Die Negativtexte im neuen Test (sieben) und im Backend-Test (drei) decken Batch und `sp_executesql`, mit und ohne schließendes `SET`; sie sind nicht einzeln mutiert (der erste rote Text bricht den Test ab); die Wirkung jedes Textes einzeln steht in der Tabelle.

**(2) Probe, kein Regress.** Im Grundlauf bleiben `SET FMTONLY ON SELECT Id FROM person` (mit und ohne schließendes `SET`), `EXEC sp_executesql N'SET FMTONLY ON …'`, `SET FMTONLY ON INSERT BULK …` und `SET FMTONLY ON SELECT Id …; SELECT Id …` (Folge) unverändert erkannt; `SimulationBackendBulkTest`, `SimulationBackendDisjunctionTest` und die Probe-Fälle von `SchemaFormsTest` und `SimulationBackendSchemaOnlyTest` sind grün. X4 (der Rest der Probe wird abgewiesen, sobald `schemaOnly = false`) macht vier Testfälle rot, darunter den Probe-Test des Bulk-Backends, den Disjunktions-Test und den Test „Die Probe der Zieltabelle antwortet weiter ohne Abschluesse der SET“: der Umbau ist an der Probe belegt.

**(3) Umbau `parse(…, schemaOnly)`, ohne Verhaltensänderung.** Vergleich der Fassung `709984d7` (zwei Funktionen `parse` und `parseInProbe` über `forms`) mit `3363fdd8` (eine Funktion, Standardwert `true`, Lambda mit `false`): gleiche Reihenfolge der Alternativen, gleiche Kette; der Standardwert gilt für den einzigen Aufrufer außerhalb der Probe (`scan`), die Probe ruft mit `false`. M26 und X3 (die Bedingung entfällt) sind in beiden Fassungen dieselbe Wirkung (zwei rote Testfälle). Die Zeilenlänge der KDoc in `BulkForms.kt` ist umbrochen (≤ 120). Den Lauf von `make detekt` habe ich nicht gefahren (nicht Pflicht des Reviewers nach der Tabelle); das Argument des Commits „nach detekt“ ist am Diff nachvollziehbar (eine Funktion weniger), nicht gemessen.

**(4) F-2, Register und Folge-Slice.** Register `spaltenflags-der-tabellenspalten-weichen-von-der-referenz-ab`: `observation.md` (Titel, Sub-Area, ein Absatz), `state.md` (eine Zeile mit Zustand „offen — 1×, unter der Schwelle, nicht verkörpert“ und Träger als auflösbarer Link), `evidence/review-2026-11-22-slice-simulation-schemaonly-keyinfo-bau.md` (Vorgang, Fund): Form der Vorlage und der Nachbar-Einträge, Zähler 1 aus der Zahl der Evidenzdateien; kein Zähler-Feld. Folge-Slice: Kopf, Ist-Stand mit Trennung „gemessen / gelesen am Code / nicht gemessen“, Abgrenzung mit Begründung je Punkt, §2 mit **drei** Liefer-Punkten (Messung, Entscheidung, Nachzug) vor den Gate-/Closure-Zeilen (≤ 3 erfüllt), keine gesetzten Haken, §3 mit Dateien, §4 mit Start „nicht starten“, Rückführungen, §6 mit Ausgängen, §7 leer („Wird bei der Closure gefüllt“), §8 mit Sub-Area-Prüfung. Aussagen gegen den Beleg: „16 Schemazeilen je Instanz `IsReadOnly=False`“ stimmt (je Instanz 19 Zeilen mit `IsReadOnly` in `schemaonly-spalten.messung`, davon 3 Kopf-/Feldzeilen, 16 mit `False`, 0 mit `True`); `IsReadOnly` am Server „voraussichtlich `True`“ ist als Folgerung nach dem Muster von W-33 („Flags ohne die Bits `0x0C`“; W-33 hat `0x0020`) und als „kein Beleg“ gekennzeichnet (§3.8 gehalten); die Bedeutung von `0x0008` ist als nicht gemessen geführt. Das Messprotokoll („Grenzen“) nennt Abweichung, Folge, Nicht-Gemessen und beide Träger.

**(5) §3.8 und Setzungen.** Die drei Setzungen (Cancel vor dem Sammeln, Fehler im RPC, `FMTONLY` nach dem Fehler) tragen in SPEC-036 das Wort „Setzung“ mit „nicht gemessen“; Plan §6 sagt zusätzlich, was getestet ist (der Cancel ja, aber nicht gemessen; der Fehler im RPC und der Zustand nach dem Fehler nein). SPEC-036 sagt nicht „gemessen“ über eine der drei. Die Aussage zu `FMTONLY` nach dem Fehler ist enger als zuvor („W-29 zeigt nur, dass die Folgeabfrage eine gewöhnliche Antwort hat“), also nicht breiter als der Beleg. Die Sätze im KDoc des Tests und im Spec sind Indikativ über den geltenden Zustand. Keine neue Aussage steht breiter als ihr Beleg (R-2 nennt nur eine Zahl).

**(6) Plan-Tabelle gegen Diff** (`git diff --stat 7def244b..HEAD`, 38 Dateien): jede Datei von `src/` und `referenztest-dotnet/` steht in einer der drei Tabellen von §3 (`MessungSchemaBelegTests.cs` in der Tabelle der Messphase; die Änderung des Baus an ihm ist ein KDoc und steht dort nicht eigens, kein Befund), `docs/messungen/mutationen-schemaonly-form-f-2026-11-22.md` steht in der Tabelle des Baus, ebenso Register, Folge-Slice und Erstreview; die `SPEC-*`-Stellen im Kopf decken alle 15 Hunks von `spec/spezifikation.md` (SPEC-002, SPEC-004, SPEC-030, SPEC-031, SPEC-036, SPEC-045, `TDS-FA-11.a`, Offene Punkte des Backend-Vertrags, Einstieg des Integrators = SPEC-059, Historie = Änderungsprotokoll). Offen nur R-1. DoD: keine neu gesetzten Haken im Diff.

**(7) Nachzug-Texte.** In den neuen Zeilen kein „ADR-“ oder „slice-“ in `spec/` (Rang: keine Bezüge nach unten), „bisher“, „noch nicht“, „soll“, „seit Slice“ treten nicht auf; „beim Verifier“ steht nur noch im Mutationsbeleg (Messprotokoll) und im Plan; die Zustandszellen in Matrix und Register nennen Zustand statt Rolle. Die Link-Korrekturen im Erstreview ändern nur Linkformen (ADR-Verweise), nicht den Inhalt eines Findings.

## Mutationen und Probe-Texte (gefahren)

| Nr | Mutation (Ort) | Wirkung (gefahren) | Roter Testfall |
|---|---|---|---|
| M26 | `StatementParser.parse`: Lambda der Probe `parse(it, schemaOnly = false)` zu `parse(it)` | rot, 2 von 67 | `SchemaFormsTest > die Probe liest ihren Rest nicht als Form F`; `SimulationBackendSchemaOnlyTest > Texte ausserhalb der Form F sind TDS_UNSUPPORTED_SQL ohne Abschluss` |
| X3 (eigen) | `parse`: Bedingung `if (schemaOnly)` vor `SchemaForms.recognize` entfällt (Parameter ohne Wirkung) | rot, 2 von 67 | dieselben zwei Testfälle |
| X4 (eigen) | `parse`: bei `schemaOnly = false` statt `null` ein `NotRecognized` (die Probe liest hinter sich nichts) | rot, 4 von 67 | `SchemaFormsTest > die Probe ist nicht Form F und Form F nicht die Probe`; `SimulationBackendBulkTest > die Probe mit SET FMTONLY liefert Spalten ohne Zeilen`; `SimulationBackendDisjunctionTest > OR hinter FMTONLY liefert Metadaten ohne Zeile`; `SimulationBackendSchemaOnlyTest > Die Probe der Zieltabelle antwortet weiter ohne Abschluesse der SET` |
| M2 | `SchemaForms.hasPrefix`: `;` hinter dem ersten `SET` nicht geprüft | rot, 2 von 67 | `SchemaFormsTest > lehnt jede andere Folge ab`; `SimulationBackendSchemaOnlyTest > Texte ausserhalb der Form F …` |
| M3 | `hasPrefix`: `;` hinter dem zweiten `SET` nicht geprüft | rot, 2 von 67 | dieselben zwei Testfälle |
| M10 | `StatementPlanner.plan`: `closed` immer falsch | rot, 8 (7 `:test`, 1 `integratorTest`) | `SchemaOnlyEmbeddingTest > schemaOnlySchliesstMitDenZweiArtenVonSetFmtonly`; `StatementPlannerTest > Form F plant die Auswahl ohne die Quelle zu rufen`; fünf Testfälle in `SimulationBackendSchemaOnlyTest` |
| M15 | `SchemaOnlyExecution`: schließendes `SET` auch nach Fehler | rot, 2 von 67 | `SimulationBackendSchemaOnlyTest > SchemaOnly auf eine unbekannte Tabelle meldet den Fehler …`; `… > Andere Fehler der Auswahl folgen den zwei Abschluessen` |
| M17 | `SchemaOnlyExecution.close` ruft `inner.close()` nicht | rot, 1 von 67 | `SimulationBackendSchemaOnlyTest > close und cancel wirken auf die Auswahl der Form F` |
| M21 | `Completion.effectiveKind`: Ausnahme der zwei Arten im RPC entfällt | rot, 2 von 67 | `ResponseSchemaOnlyTest > Im RPC tragen die zwei SET ihre Kennungen als DONEINPROC`; `ResponseStatementKindTest > Im RPC gelten nur die zwei Arten …` |
| M22 | `DoneCommand.inRpc` gilt für jede Art ohne Zeilenzahl | rot, 2 von 67 | `ResponseStatementKindTest > Im RPC gelten nur die zwei Arten …`; `… > Im RPC und im Massenladen gilt die Kennung der Strukturaenderung nicht` |
| M23 | `effectiveKind`: Ausnahme gilt auch im Massenladen | rot, 1 von 67 | `ResponseStatementKindTest > Im RPC gelten nur die zwei Arten …` |

(„von 67“ zählt den Wegwerf-Test `RvProbe2Test` mit; im Repo sind es 66 Testfälle in `:test`.) Die Zahlen von M2, M3, M10, M15, M17, M21, M22, M23 decken sich mit dem Erstreview und dem Mutationsbeleg; M26 mit dem Beleg („2 Testfälle“).

Texte der Erkennung (Auszug der gefahrenen 20; HEAD gegen M26):

| Text hinter dem Einstieg `SET FMTONLY ON …` | HEAD | M26 |
|---|---|---|
| `SET FMTONLY OFF; SET FMTONLY ON; SELECT Id FROM person` | nicht erkannt | `SchemaOnly`, `closed=false` |
| dasselbe mit schließendem `SET FMTONLY OFF` | nicht erkannt | `SchemaOnly`, `closed=false` (das schließende `SET` fällt weg) |
| dasselbe mit schließendem `SET FMTONLY OFF;` und mit `;` am Ende | nicht erkannt | `SchemaOnly` |
| `EXEC sp_executesql N'SET FMTONLY ON SET FMTONLY OFF; SET FMTONLY ON; …'` | nicht erkannt | `SchemaOnly` |
| `sp_executesql N'… SET FMTONLY OFF'` (ohne `EXEC`) | nicht erkannt | `SchemaOnly` |
| `EXECUTE sp_executesql N'SET FMTONLY ON SET FMTONLY ON SET FMTONLY OFF; …'` (verschachtelt) | nicht erkannt | `SchemaOnly` |
| `SET FMTONLY ON SET FMTONLY ON SET FMTONLY OFF; SET FMTONLY ON; …` (doppelt davor) | nicht erkannt | `SchemaOnly` |
| mit `WHERE Id = 1 OR Id = 2 SET FMTONLY OFF` | nicht erkannt | `SchemaOnly` mit UND-Folge |
| `SET FMTONLY ON SET FMTONLY OFF; SET FMTONLY ON; SELECT 2` | nicht erkannt | nicht erkannt |
| Zwilling `SET FMTONLY OFF; SET FMTONLY ON; SELECT Id FROM person` (ohne Einstieg) | `SchemaOnly` | `SchemaOnly` |

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/StatementParser.kt`, `BulkForms.kt`, `SchemaOnlyExecution.kt` | geprüft, Befund R-3 (vorbestehend); Umbau ohne Verhaltensänderung, KDoc im Indikativ |
| `src/test/kotlin/io/github/pt9912/tdswire/simulation/SchemaFormsTest.kt`, `SimulationBackendSchemaOnlyTest.kt` | geprüft, ohne Befund (Zwilling geprüft, Texte decken Batch und `sp_executesql`; die Wächter-Sätze nennen die Mutation, nicht den abwesenden Zustand) |
| `spec/spezifikation.md` | geprüft, ohne Befund (Setzungen gekennzeichnet; keine `ADR-`/`slice-`-Bezüge in den neuen Zeilen; alle Hunks im Kopf der Plan-Stellen) |
| `docs/messungen/referenzabgleich.md`, `docs/plan/kompatibilitaetsmatrix.md`, `docs/plan/planning/observations/BEO-TDS/`, `docs/plan/planning/open/slice-simulation-spaltenflags-an-referenz-angleichen.md` | geprüft, ohne Befund (Form der Vorlage, Aussagen nicht breiter als der Beleg) |
| `docs/messungen/mutationen-schemaonly-form-f-2026-11-22.md` | geprüft, Befund R-2 |
| `docs/plan/planning/done/slice-simulation-schemaonly-keyinfo.md` | geprüft, Befund R-1 |
| `docs/plan/spec-abdeckung.md` | gelesen, ohne Befund (SPEC-036: 306 statt 305 Tests, der neue Fall); nicht neu erzeugt |
| Suppression (`@Suppress`, `@SuppressWarnings`, `noqa`, `nolint`, `SuppressMessage`), Host-Aufrufe von `gradle`/`java`, Zeilen über 120 Zeichen in den geänderten `src/main`-Dateien | geprüft, ohne Befund |
| Accepted-ADR (`docs/plan/adr/`), `spec/architecture.md`, `spec/lastenheft.md`, `docs/user/` | nicht berührt, geprüft, ohne Befund |

## Summary

**HIGH 0 · MEDIUM 0 · LOW 2 · INFO 2** (neue Findings dieses Re-Reviews). Von den Findings des Erstreviews sind F-1 bis F-6 behoben, nicht nur umformuliert: F-1 ist an Code, Test und Mutation belegt (jeder der neun Überschneidungstexte wird unter M26 einzeln zu Form F, im HEAD keiner); F-2 trägt Register, Folge-Slice und Messprotokoll mit Aussagen innerhalb des Belegs; F-3 bis F-6 sind am Diff erledigt bis auf den Namen in R-1. F-8 und F-9 bleiben INFO ohne Aktion. Elf Mutationen sind rot, die Zahlen decken sich mit dem Mutationsbeleg; die Probe (Form B) ist unverändert und bleibt grün (X4 rot, kein Regress).

**Empfehlung:** weiter an den Verifier. R-1 und R-2 sind Korrekturen an Plan und Beleg ohne Gate und ohne Auswirkung auf Code oder Verhalten; sie können beim Planner nachgezogen werden, ohne dass ein weiterer Re-Review nötig ist (reine Doku-Korrektur). Für die Closure-Notiz §7 ist die Nachbesserung `709984d7` und `3363fdd8` mit diesem Re-Review gedeckt. Über die Behebung entscheidet der Implementer; ein Finding wird nicht herabgestuft, weil jemand widerspricht.
