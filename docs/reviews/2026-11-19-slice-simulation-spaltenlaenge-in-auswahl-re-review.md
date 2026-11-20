# Re-Review: slice-simulation-spaltenlaenge-in-auswahl — Nachbesserung zu `876e2e64` — 2026-11-20

**Review-Art:** Code und Doku der Nachbesserung, gegen Plan, [ADR-0056](../plan/adr/0056-laenge-einer-textspalte-erreicht-das-colmetadata-einer-auswahl.md) (`Accepted`, Option C), [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md) Entscheidung 7, [SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends), [SPEC-003](../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog), [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate) und die Hard Rules (`AGENTS.md` §3.1, §3.2, §3.5, §3.7, §3.8). Der Report ist ein Folgelauf zu [2026-11-19-slice-simulation-spaltenlaenge-in-auswahl.md](2026-11-19-slice-simulation-spaltenlaenge-in-auswahl.md) (Findings F-1 bis F-7); dessen Kennungen bleiben dort, die Findings dieses Laufs heißen R-1 bis R-7.

**Gegenstand:** `git diff 876e2e64..HEAD` im Worktree `slice-simulation-spaltenlaenge-in-auswahl`, HEAD `cc60026b`, ein Commit, 10 Dateien (Code: `RowRules.kt`, `TableStore.kt`, `TableStoreTest.kt`, `SpaltenlaengeAuswahlWireTest.kt`; Doku: `spec/spezifikation.md`, zwei Nutzer-Dokus, Slice, Mutationsbeleg, `spec-abdeckung.md`). Der Re-Review liest nur diese Commits.

**Skill:** `.harness/skills/reviewer.md` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-20

**Eingangs-Kontext:** Vorgänger-Report (ganz), Diff der Nachbesserung, Slice §3 und §6, [ADR-0056](../plan/adr/0056-laenge-einer-textspalte-erreicht-das-colmetadata-einer-auswahl.md) (ganz), `SimulationMerges.truncates`, `SimulationChanges.prepare`, `BulkLoads.call`, `TextMapping`, `SimModel`, `TableStore`, `RowRules`, Messprotokoll [Referenzabgleich](../messungen/referenzabgleich.md) (M-11, M-15), Nutzer-Doku `einbettung-und-betrieb.md` und `testserver-manuell.md`.

**Ausgeführte Sensoren:** Docker-Läufe unter der Messsperre (`free -m` available 10 bis 13 GB vor jedem Lauf), in einem Wegwerf-Kontext im Scratchpad (`git archive HEAD`, Kopie des `Dockerfile` mit einer Stage `rev`; nichts davon im Repo, Erzeuger liegt nicht im Repo):

1. `:testserver:test --tests TableStoreTest` auf `HEAD`: 22 Fälle, 0 Fehler. `:test --tests SpaltenlaengeAuswahlWireTest`: 4 Fälle, 0 Fehler. Das ganze Modul `:testserver:test`: 18 Klassen, 0 Fehler, `BUILD SUCCESSFUL`. `:test --tests TestserverLogProcessTest --tests TestserverSimulationProcessTest --tests SpaltenlaengeAuswahlWireTest`: 6, 2 und 4 Fälle, 0 Fehler (die einzigen Läufe, die den `TableStore` im Prozess-Testserver treiben).
2. Mutationen (je ein Lauf, `TableStoreTest`): Mut-11, Mut-15, Mut-16 des Belegs wiederholt; zwei eigene Mutationen (E-A, E-B). Ergebnisse unter „Mutationen“.
3. Ein Wegwerf-Test am Draht (Kopie von `SpaltenlaengeAuswahlWireTest`, Zeile mit 4001 Zeichen in einer Spalte mit `maxLength` -1): siehe R-1.

Nicht gefahren: `make gates`, `make clean-build`, `make e2e`, `make test` ganz (die Tabelle in `harness/README.md` §Minimal agent workflow nimmt sie von Reviewer und Implementer aus), `make simulations-integration`, `make kompatibilitaet`, Mitschnitt-Lauf. `make docs-check`: Exit 0, 0 Befunde auf dem Stand mit diesem Report. Gesamtlaufzeit des Reviews rund 25 Minuten; größte Zeitposten: Warten auf die Messsperre (rund 5 Minuten), neun Gradle-Läufe in Docker zu je 40 bis 65 s.

## Findings

### R-1 — MEDIUM
- `kategorie`: MEDIUM
- `quelle`: `AGENTS.md` §3.8, Skill „Aussage breiter als ihr Beleg“, [ADR-0056](../plan/adr/0056-laenge-einer-textspalte-erreicht-das-colmetadata-einer-auswahl.md) Entscheidung 3, Vorgänger-Finding F-1
- `pfad`: `testserver/kotlin/io/github/pt9912/tdswire/testserver/RowRules.kt` · „ein Text über der Länge erreicht so nie den Bestand, den eine Auswahl dann nicht mehr ausliefern könnte“ und `testserver/test/kotlin/io/github/pt9912/tdswire/testserver/TableStoreTest.kt` · „`Lang` mit `maxLength` -1 hat keine Grenze“ (Test `der Bestand nimmt Texte nur bis zur Laenge der Spalte an`, `"y".repeat(5000)`)
- `befund`: Der Bestand behandelt `maxLength` -1 als „keine Grenze“ und nimmt in `Lang` 5000 Zeichen an (der Test legt das als Zusage fest), doch eine Auswahl meldet -1 als 4000 ([ADR-0056](../plan/adr/0056-laenge-einer-textspalte-erreicht-das-colmetadata-einer-auswahl.md) Entscheidung 3), und die Bibliothek weist einen Text über 4000 ab. Gemessen am Draht mit einem Wegwerf-Test (Spalte mit `maxLength` -1, Zeile mit 4001 Zeichen, `SELECT lang …`): ERROR 50003, Severity 20, kein ROW, `closed=true` (Wegwerf-Test nicht im Repo; die zwei Hälften, Annahme im Bestand und Abweisung in der Antwort, sind einzeln gemessen, die Kette `TableStore` bis Draht nicht in einem Lauf). Failure-Szenario: ein Client legt `NVARCHAR(MAX)` an, lädt einen Text über 4000 Zeichen, und jede folgende Auswahl dieser Zeile endet im Fehler und schließt die Verbindung, die Klasse von Vorgänger-F-1, jetzt für -1. Der Zustand ist nicht neu (gleiche Wirkung vor dem Slice), aber die Nachbesserung begründet die Prüfung mit „weil eine Auswahl ihn nicht mehr ausliefern könnte“ und lässt für -1 genau diesen Fall offen; weder SPEC-035 noch die Nutzer-Doku nennen ihn bei der Prüfung im Bestand.
- `verifizierbar`: ja (Test am Bestand mit -1 und 4001 Zeichen, danach Auswahl am Draht)
- `klasse`: Schutz gegen unausgelieferbaren Text trägt für die Sonderlänge nicht

### R-2 — MEDIUM
- `kategorie`: MEDIUM
- `quelle`: Skill „Zahl, Feldmenge oder Zuordnung steht an einem zweiten Ort ohne Sensor“, `AGENTS.md` §3.7 (Klasse Kopplung)
- `pfad`: `testserver/kotlin/io/github/pt9912/tdswire/testserver/RowRules.kt` · „private fun fitsLength(column: SimColumn, value: Any): Boolean“ gegen `src/main/kotlin/io/github/pt9912/tdswire/simulation/SimulationMerges.kt` · „private fun truncates(action: String): Boolean“
- `befund`: Die Regel (nur `UnicodeText`, -1 ohne Grenze, sonst `length > maxLength`) und die Konstante -1 stehen zweimal: in der Bibliothek (`truncates`, `UNBOUNDED_TEXT`, `reportedLength`, alle `internal`) und im Testserver (`RowRules`, eigene `UNBOUNDED_TEXT`). Kein Test liest beide, und kein Kopplungskommentar steht auf einer der Seiten (das KDoc von `RowRules.check` nennt nur, wie die Bibliothek die Ausnahme meldet, nicht `truncates`). Die Begründung des Implementers, `internal` sei aus dem Modul `testserver` nicht lesbar und die öffentliche Fläche solle nicht wachsen, trägt: [ADR-0056](../plan/adr/0056-laenge-einer-textspalte-erreicht-das-colmetadata-einer-auswahl.md) Entscheidung 4 schreibt die nicht wachsende Fläche fest, und ein öffentliches Mitglied bräuchte den Eintrag im Änderungsprotokoll zu `SPEC-059`. Die Kopie ist also eine Folge der Entscheidung und kein Fehler; fehlt die Kopplung, ist es nach dem Skill MEDIUM. Failure-Szenario: ändert die Bibliothek die Zählung oder die Sonderlänge, nimmt der Bestand weiter Text an, den die Auswahl nicht ausliefert (R-1, derselbe Ausgang); der Mutationsbeleg bleibt grün (E-B unter „Mutationen“).
- `verifizierbar`: ja (Kopplungskommentar auf beiden Seiten oder ein Test, der beide Orte liest)
- `klasse`: Regel kopiert über eine Modulgrenze, Kopplung nicht benannt

### R-3 — LOW
- `kategorie`: LOW
- `quelle`: Baseline-Regelwerk `modul-05-planning-harness.md` §Offene Risiken werden bei Closure aufgelöst (Slice §6: jedes Risiko genau ein Ausgang)
- `pfad`: `docs/plan/planning/in-progress/slice-simulation-spaltenlaenge-in-auswahl.md` · „Ein Text einer Zeile über `ResultColumn.maxLength` weist die Bibliothek ab“ (§6, zweites Risiko)
- `befund`: Die offene Frage, ob die Bibliothek bei `INSERT`, `UPDATE` und Massenladen selbst abweist (etwa mit 2628) und das eine Folge-ADR zu [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md) Entscheidung 7 und [ADR-0056](../plan/adr/0056-laenge-einer-textspalte-erreicht-das-colmetadata-einer-auswahl.md) Entscheidung 2 verlangt, steht an keiner Stelle als offen: Spezifikation und Nutzer-Doku sagen nur den Ist-Zustand („Die Bibliothek selbst prüft die Länge dort nicht“, der Integrator prüft selbst), das zweite Risiko in §6 ist nicht fortgeschrieben, und kein Beobachtungs-Eintrag existiert. Entschieden wird die Frage hier nicht; benannt fehlt sie. Der Nutzer-Weg ist über die Nutzer-Doku abgedeckt, es ist ein Nachweis der Entscheidungslage, kein Fehler des Codes.
- `verifizierbar`: nein
- `klasse`: offene Entscheidung ohne Träger im Slice

### R-4 — LOW
- `kategorie`: LOW
- `quelle`: `AGENTS.md` §3.7, Skill LOW „nennt den abwesenden oder unwirklichen Zustand“, `AGENTS.md` §5 Regel 5
- `pfad`: `docs/user/testserver-manuell.md` · „denn eine Zeile mit einem solchen Text endete in jeder Auswahl mit `TDS_UNSUPPORTED_TYPE` (50003) und schlösse die Verbindung“
- `befund`: Die Begründung steht im Konjunktiv über den Zustand, den die Prüfung verhindert (`endete`, `schlösse`); die Nutzer-Doku bildet den Ist-Zustand ab. Dasselbe Muster, schwächer, im KDoc von `RowRules.check` („den eine Auswahl dann nicht mehr ausliefern könnte“), dort als Begründung der geltenden Zusage; als HIGH-Punkt „Kommentar trägt keine der Kommentar-Klassen“ habe ich es nicht gewertet, weil es keine verworfene Alternative und keinen abwesenden Text nennt.
- `verifizierbar`: nein
- `klasse`: Begründung im Konjunktiv über den verhinderten Zustand

### R-5 — LOW
- `kategorie`: LOW
- `quelle`: `AGENTS.md` §3.8, Skill „Nutzer-Doku nicht komplett gegen den Slice gelesen“
- `pfad`: `docs/user/einbettung-und-betrieb.md` · „ein Integrator, dessen Callbacks `onUpdate` und `onInsertBatch` Zeilen speichern, prüft die Länge dort selbst“
- `befund`: [SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) sagt, die Bibliothek prüfe die Länge nur beim `MERGE`, „nicht bei `INSERT`, `UPDATE` und Massenladen“; die Nutzer-Doku nennt `onUpdate` und `onInsertBatch`, nicht `onInsert`. Der Pfad ist real: `SimulationChanges.prepare` ruft `onInsert` für ein `INSERT` als SQL-Text ohne Längenprüfung (gelesen); nur über `MERGE` prüft die Bibliothek vor dem Callback.
- `verifizierbar`: nein (Gegenlesung Code und Spezifikation)
- `klasse`: Aufzählung der Callbacks unvollständig gegenüber der Spezifikation

### R-6 — INFO
- `kategorie`: INFO
- `quelle`: Skill „Auswahl- oder Gleichheitseigenschaft mit einheitlicher Eingabe“ (Gegenprobe), Maintainability
- `pfad`: `testserver/test/kotlin/io/github/pt9912/tdswire/testserver/TableStoreTest.kt` · „fun `der Bestand nimmt Texte nur bis zur Laenge der Spalte an`“
- `befund`: Zwei eigene Mutationen bleiben grün: E-A (`UPDATE` prüft nur den ersten Wert von `SET`; der Test ändert immer eine Spalte) und E-B (Länge in Codepunkten statt UTF-16-Einheiten; der Test nimmt nur ASCII, die Bibliothek zählt Einheiten, `TextMapping`). Keine der beiden liegt in den Zusagen der Nachbesserung als Satz; E-A betrifft auch die ältere Klassenprüfung. Ohne erwartete Aktion, Hinweis für den Verifier.
- `verifizierbar`: ja
- `klasse`: Mutation in Nachbarachse der Zusage bleibt grün

### R-7 — INFO
- `kategorie`: INFO
- `quelle`: `AGENTS.md` §3.8
- `pfad`: `spec/spezifikation.md` · „beim Massenladen erkennt der Referenzclient den Verstoß vor dem Senden (M-11, ein serverseitiger Verstoß ist nicht hergestellt)“
- `befund`: Das Messprotokoll ([M-11](../messungen/referenzabgleich.md)) nennt die Bedingung „mit einer `DataTable`-Quelle“ und einen Fall (23 Zeichen in `nvarchar(5)`); der Satz in SPEC-035 trägt sie nicht, und ihm folgt der Halbsatz, der die Grenze nennt („nicht hergestellt“). Das Vorgänger-F-2 ist damit behoben (kein „beim Schreiben“ mehr, `MERGE` getrennt von `INSERT`, `UPDATE` und Massenladen); der Rest ist ein Hinweis, kein Befund.
- `verifizierbar`: nein
- `klasse`: Bedingung der Messung im Protokoll, nicht im Satz

## Mutationen

Gefahren (Einzelmessung 2026-11-20, ein Host, je Mutation ein Gradle-Lauf in Docker, `:testserver:test --tests TableStoreTest`, 41 bis 45 s je Lauf einschließlich Bau; Skript im Scratchpad, nicht im Repo):

| Nr | Mutation | Ergebnis |
|---|---|---|
| Mut-11 (Beleg) | `RowRules.kt`: die Zeile `require(fitsLength(column, value))` entfällt | rot, 2 von 22: `der Bestand nimmt Texte nur bis zur Laenge der Spalte an`, `ein UPDATE ueber der Laenge ist abgewiesen und die Auswahl liefert ein Ergebnis` (stimmt mit dem Beleg überein) |
| Mut-15 (Beleg) | `TableStore.kt`: die Zeile `RowRules.check` in `update` entfällt | rot, 3 von 22: dieselben zwei und `onUpdate weist ungueltige Aenderungen ab und laesst den Bestand` (stimmt) |
| Mut-16 (Beleg) | `TableStore.kt`: die Zeile `RowRules.check` in `insertBatch` entfällt | rot, 3 von 22: `der Bestand nimmt Texte …`, `der Bestand haelt Int16 und UInt8 im Wertebereich`, `eine fehlerhafte Nachricht aendert den Bestand nicht` (stimmt) |
| E-A (eigen) | `update`: `set.entries.take(1).forEachIndexed …` (nur der erste Wert wird geprüft) | grün, 22 von 22 (siehe R-6) |
| E-B (eigen) | `RowRules.fitsLength`: `codePointCount(0, length)` statt `length` | grün, 22 von 22 (siehe R-6) |

Nicht wiederholt: Mut-12, Mut-13, Mut-14. Mut-12 und Mut-13 liegen in `fitsLength`, derselben Zeile wie Mut-11; die Randtests im Testfall (`"x".repeat(NAME_LENGTH)` angenommen, `NAME_LENGTH + 1` abgewiesen, `4000`/`4001`, -1 mit 5000) lesen sich als rot für beide. Mut-14 (`toResultColumn` ohne `reportedLength`) ist rot an den drei Fällen, die `lang` oder die Länge lesen; ein Weg, der nur den RPC-Pfad ändert, existiert nicht (`resultStart(part)` ist für Batch und RPC derselbe Code, gelesen); mein Wegwerf-Test machte beim Ändern der Zeile 1 beide Wire-Fälle, den Batch- und den `sp_executesql`-Fall, gemeinsam rot (Artefakt des Wegwerf-Fixtures, kein Befund). Der Zusatzfall `sp_executesql` ist damit ein Test des Weges, kein Test einer eigenen Verzweigung.

## Antworten auf die Schwerpunkte des Auftrags

1. **F-1 bis F-7 behoben?**
   - **F-1 (Bestand prüft die Länge):** behoben für gebundene Längen. `RowRules.check` weist einen Text über `SimColumn.maxLength` ab, `TableStore.insertBatch` und `TableStore.update` rufen es für jeden Wert; die zwei Pfade sind einzeln durch Mutation belegt (Mut-15, Mut-16, wiederholt). Der Fall der Sonderlänge -1 ist offen: R-1. Der Pfad `INSERT` als SQL-Text hat im Bestand keinen Callback (`onInsert = null`, `TDS_UNSUPPORTED_SQL`), das nennt der Mutationsbeleg.
   - **Fehlernummern:** die Abbildung steht im Code, nicht im Testserver: `BulkLoads.call` meldet jeden Fehler des Callbacks als `TDS_SIM_KEY_VIOLATION` (2627), `SimulationChanges` (KDoc „Ein Fehler des Callbacks … `TDS_BACKEND_ERROR`“) jeden Fehler von `onUpdate` als `TDS_BACKEND_ERROR` (50004, Class 16, Verbindung offen). Beide als Setzung gekennzeichnet in SPEC-035 („Setzung, am echten Server nicht gemessen; gemessen ist nur 2628 beim `MERGE`“) und im Änderungsprotokoll. Der Test `ein UPDATE ueber der Laenge …` belegt `TDS_BACKEND_ERROR` an der Antwort; die 2627 beim Massenladen ist für den Fall „Länge“ nicht eigens an der Antwort belegt (der Test ruft `onInsertBatch` direkt und erwartet `IllegalArgumentException`), die Abbildung selbst ist die vorhandene für jeden Fehler des Callbacks.
   - **Abweichung „2628“:** 2627 beim Massenladen und `TDS_BACKEND_ERROR` beim `UPDATE` sind für den Testserver die einzige erreichbare Folge, solange der Bestand eine Ausnahme wirft und die Bibliothek sie abbildet; 2628 bräuchte eine Änderung der Bibliothek (neue Fläche oder Prüfung im Backend), und das ist die Frage aus R-3. SPEC-035, Nutzer-Doku und Mutationsbeleg sagen die Abweichung korrekt und nicht breiter als der Beleg: „nicht als 2628 des `MERGE`“, nur `MERGE` gemessen, `INSERT` und `UPDATE` ohne Messung, Massenladen nur Client-seitig (M-11). Der Nutzer-Satz in `testserver-manuell.md` nennt die Nummern ohne das Wort Setzung, was für `docs/user` zulässig ist (Ist-Zustand des Servers).
   - **Zweite Kopie der Regel:** die Begründung „öffentliche Fläche soll nicht wachsen“ trägt ([ADR-0056](../plan/adr/0056-laenge-einer-textspalte-erreicht-das-colmetadata-einer-auswahl.md) Entscheidung 4, `SPEC-059`); fehlt die Kopplung: R-2.
   - **F-2, F-4:** behoben (SPEC-035 trennt `MERGE` von `INSERT`, `UPDATE`, Massenladen, nennt 50003, Class 20 und das Ende der Verbindung, `SPEC-014`); Rest in R-7. **F-3:** behoben, die Plan-Tabelle ist gegen `git diff --stat main...HEAD` abgeglichen (22 Dateien in `main...HEAD`; jede steht in der Tabelle oder im Kopf, ausgenommen der erste Review-Report und die Datei [ADR-0056](../plan/adr/0056-laenge-einer-textspalte-erreicht-das-colmetadata-einer-auswahl.md), die Eingang ist). **F-5:** INFO, ohne Aktion. **F-6:** behoben (`Mut-<n>`, Mut-1 bis Mut-16; Mut-10 ist die Gegenprobe am Mitschnitt, Mut-11 bis Mut-16 sind ergänzt). **F-7:** behoben (Fall `die Auswahl mit Parameter ueber sp_executesql traegt die Laenge`, am Draht grün, siehe Anmerkung zu Mut-14).
2. **Tests unterscheiden je Zusage:** `TableStoreTest` hat zwei Fälle mit verschiedenen Eingaben (Länge 5 mit 5/6 Zeichen, Vorgabe mit 4000/4001, -1 mit 5000; einmal am Callback, einmal als SQL-Text mit anschließender Auswahl und `ResultStart`-Länge); keine einheitliche Eingabe. Der `sp_executesql`-Fall unterscheidet die drei Längen 10, 4000, 4000 und prüft, dass kein ERROR fällt. Mutationsbeleg Mut-11 bis Mut-16: plausibel, drei wiederholt und gleich.
3. **Regression:** `RowRules.check` wird nur von `TableStore` gerufen (`rg`), `TableStore` nur vom Prozess-Testserver (`Main.kt`, `storeSource`) und von `TableStoreTest`; `IntegrationServer`, `LasttestBackends`, `RueckstauServer`, `EigenesBackend` und `KompatibilitaetsServer` haben eigene Callbacks und gehen nicht durch den Bestand. Läufe: ganzes Modul `:testserver:test` und die zwei Prozess-Tests grün (siehe Sensoren). Die Beispiel-Beschreibung `testserver/resources/manuell/sim.yaml` führt `max_length` 100 für `Name`; Zeilen über der Länge hat keine Datei der Läufe in diesem Repo (gelesen für `sim.yaml`, `docs/user`-Beispiele nicht Zeile für Zeile). Nicht gefahren: `make smoke`, `make simulations-integration`, `make kompatibilitaet`, der Lauf mit d-migrate; das gehört dem Verifier.
4. **Die offene Frage (Bibliothek weist bei `INSERT`, `UPDATE`, Massenladen mit 2628 ab, Folge-ADR zu [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md) Nr. 7):** nicht als offen benannt, R-3. Entschieden habe ich sie nicht; sie ist Sache von Planner und Nutzer.
5. **Hard Rules:** §3.1 ohne Befund (kein Host-Aufruf im Diff). §3.2 ohne Befund (Grep des Diffs auf `@Suppress`, `@SuppressWarnings`, `noqa`, `nolint`, `SuppressMessage`: kein Treffer). §3.5 ohne Befund (`git diff 876e2e64..HEAD --name-only` nennt keine Datei unter `docs/plan/adr/`). §3.7: R-4 (Konjunktiv in der Nutzer-Doku und im KDoc, LOW); die Wächter-Zeilen der Test-KDocs im Konjunktiv folgen dem Muster der Mutationsbeschreibung und sind nicht gezählt. §3.8: R-1 und R-5 (Reichweite), R-7 (Hinweis). Keine `- [x]` im Slice.

## Geprüft, ohne Befund

- `testserver/kotlin/io/github/pt9912/tdswire/testserver/TableStore.kt`: die zwei Aufrufe von `RowRules.check` stehen vor jeder Änderung des Bestands (die Atomarität des Massenladens und des `UPDATE` bleibt: Test `eine fehlerhafte Nachricht aendert den Bestand nicht` grün); KDoc nennt die Länge bei Massenladen und `UPDATE` im Indikativ.
- `src/test/kotlin/io/github/pt9912/tdswire/runtime/SpaltenlaengeAuswahlWireTest.kt`: `exchange` erhält das alte Verhalten für den Batch (4 von 4 Fällen grün); der neue Hilfsweg `askedWithParameter` benutzt `RpcWire.executeSql` des vorhandenen Testbestands.
- `spec/spezifikation.md` (SPEC-035, Änderungsprotokoll): Nummern, Class und Verbindung des 50003, Trennung der Belege (`MERGE` gemessen, `INSERT`/`UPDATE` nicht, Massenladen Client-seitig), Setzungen gekennzeichnet; das Änderungsprotokoll nennt den Bestand als Setzung. Keine neue Zusage ohne Anker im Lastenheft ([TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate) Negative: Schreibzugriff gegen die Struktur wird abgewiesen, Bestand unverändert).
- `docs/user/` (beide Dateien): die geänderten Absätze gegen Code und Spezifikation gelesen; Treffer auf „bisher“, „noch nicht“, „soll“, „vorläufig“, „Zielzustand“ im geänderten Text: keine.
- `docs/messungen/spaltenlaenge-mutationen-2026-11-19.md`: Bedingungen und Grenze nennen den Erzeuger außerhalb des Repos; Mut-11, Mut-15, Mut-16 wiederholt und gleich; Kennungen `Mut-<n>` kollidieren nicht mit `M-<n>`.
- `docs/plan/spec-abdeckung.md`: Zähler der vier geänderten Zeilen stimmen zu den vier neuen Fällen (SPEC-003 plus 2, SPEC-035 plus 3 mit dem Wire-Fall; Gegenprobe über die Klassen in der Spalte); Tabellenformat unverändert.
- `docs/plan/planning/in-progress/slice-simulation-spaltenlaenge-in-auswahl.md`: Tabelle in §3 und Kopf „Berührte Spec-Stellen“ gegen den Diff abgeglichen; keine Haken gesetzt (kein `- [x]`).

## Urteil

Kein HIGH. Zwei MEDIUM (R-1: die Sonderlänge -1 nimmt der Bestand ohne Grenze, die Auswahl liefert über 4000 nicht aus; R-2: die Regel steht ein zweites Mal im Testserver, ohne Kopplung), drei LOW (R-3 die offene Frage nicht benannt, R-4 Konjunktiv, R-5 `onInsert` fehlt in der Aufzählung der Doku), zwei INFO (R-6, R-7). Die Vorgänger-Findings F-1 bis F-4, F-6 und F-7 sind behoben, F-5 bleibt INFO; die drei Mutationen des Belegs sind wiederholt und rot an den genannten Fällen. Die Abweichung 2627 und `TDS_BACKEND_ERROR` statt 2628 ist erklärt, als Setzung gekennzeichnet und nicht breiter als der Beleg.

**Empfehlung:** Nachbesserung für R-1 und R-2 (Code, Test, Kopplungskommentar), danach ein weiterer Re-Review nur der betroffenen Dateien; R-3 bis R-5 sind Doku und Planung und brauchen keinen Re-Review. Die Entscheidung zur Frage in R-3 liegt beim Planner und Nutzer.
