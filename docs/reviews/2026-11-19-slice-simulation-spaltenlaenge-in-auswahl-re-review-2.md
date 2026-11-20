# Re-Review 2: slice-simulation-spaltenlaenge-in-auswahl — zweite Nachbesserung zu `cc60026b` — 2026-11-20

**Review-Art:** Code und Doku der zweiten Nachbesserung, gegen Plan, [ADR-0056](../plan/adr/0056-laenge-einer-textspalte-erreicht-das-colmetadata-einer-auswahl.md) (`Accepted`, Option C), [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md) Entscheidung 7, [SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends), [SPEC-003](../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog), [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate) und die Hard Rules (`AGENTS.md` §3.1, §3.2, §3.5, §3.7, §3.8). Folgelauf zu [2026-11-19-slice-simulation-spaltenlaenge-in-auswahl-re-review.md](2026-11-19-slice-simulation-spaltenlaenge-in-auswahl-re-review.md) (Findings R-1 bis R-7); dessen Kennungen bleiben dort, die Findings dieses Laufs heißen N-1 bis N-3.

**Gegenstand:** `git diff e5b146f9..HEAD` im Worktree `slice-simulation-spaltenlaenge-in-auswahl`, HEAD `2a155040`, ein Commit, 10 Dateien. Code: `RowRules.kt`, `SimulationMerges.kt` (nur KDoc), `TableStoreTest.kt`, `SpaltenlaengeAuswahlWireTest.kt`; Doku: `spec/spezifikation.md`, zwei Nutzer-Dokus, Slice, Mutationsbeleg, `spec-abdeckung.md`. Der Re-Review liest nur diese Commits.

**Skill:** `.harness/skills/reviewer.md` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-20

**Eingangs-Kontext:** Vorgänger-Report (ganz), Diff der Nachbesserung, Slice §3 und §6, `SimulationMerges.truncates`, `SimModel` (`reportedLength`), `TextMapping`, `SimulationChanges.prepare`, `TableStore`, Messprotokoll [Referenzabgleich](../messungen/referenzabgleich.md) (M-11), Vorlage `slice.template.md` und `modul-05-planning-harness.md` (Ausgang eines Risikos), `docs/user/einbettung-und-betrieb.md` und `testserver-manuell.md` (geänderte Absätze und Grep auf Abwesendes und Geplantes).

**Ausgeführte Sensoren:** Docker-Läufe unter der Messsperre (`free -m` available 14 GB vor jedem Lauf) in einem Wegwerf-Kontext im Scratchpad (`git archive HEAD`, Kopie des `Dockerfile` mit einer Stage `rev`; nichts davon im Repo, der Erzeuger liegt nicht im Repo): `:testserver:test --tests '*TableStoreTest'` auf `HEAD`: `BUILD SUCCESSFUL` (24 Fälle); Mut-17, Mut-19, Mut-20 des Belegs je ein Lauf (Ergebnisse unter „Mutationen“). `make docs-check`: Exit 0, 0 Befunde auf dem Stand mit diesem Report.

Nicht gefahren: `make gates`, `make clean-build`, `make e2e`, `make test` ganz (die Tabelle in `harness/README.md` §Minimal agent workflow nimmt sie von Reviewer und Implementer aus), Mut-18 und Mut-21 (nur gelesen), `SpaltenlaengeAuswahlWireTest` (der neue Fall am Draht ist nicht von mir gefahren; gelesen). Gesamtlaufzeit rund 20 Minuten; größte Zeitposten: vier Gradle-Läufe in Docker zu je 45 bis 130 s (der letzte Lauf lief mit einem anderen Docker-Auftrag zugleich, daher 130 s).

## Findings

### N-1 — INFO
- `kategorie`: INFO
- `quelle`: Baseline-Regelwerk `modul-05-planning-harness.md` §Ausgang eines Risikos (Schablone `slice.template.md`: „weiter offen: → BEO-<KUERZEL>/<slug> im Register“), Vorgänger-R-3
- `pfad`: `docs/plan/planning/in-progress/slice-simulation-spaltenlaenge-in-auswahl.md` · „**Ausgang:** weiter offen, Träger Nutzer und Architect; SPEC-035 nennt die Nummern als Setzung.“
- `befund`: Die offene Frage ist jetzt in Slice §6 und in [SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) („Offen: ob die Bibliothek selbst auch dort abweist (etwa mit 2628), ist nicht entschieden“) als offen benannt und nicht entschieden; der Vorgänger-R-3 ist damit behoben. Die Schablone führt für „weiter offen“ einen Zeiger auf einen Eintrag im Beobachtungs-Register, den die Zeile nicht trägt; das ist ein Schritt der Closure (Planner), kein Mangel des Diffs.
- `verifizierbar`: nein
- `klasse`: Ausgang „weiter offen“ ohne Register-Zeiger (Closure-Schritt)

### N-2 — INFO
- `kategorie`: INFO
- `quelle`: `AGENTS.md` §3.8, Maintainability
- `pfad`: `src/main/kotlin/io/github/pt9912/tdswire/simulation/SimulationMerges.kt` · „private fun truncates(action: String): Boolean“ und `docs/user/einbettung-und-betrieb.md` · „die Bibliothek prüft die Länge nur beim `MERGE` (2628)“
- `befund`: `truncates` nimmt `maxLength` -1 aus (`column.maxLength != UNBOUNDED_TEXT`): ein `MERGE` mit einem Text über 4000 Zeichen in eine `nvarchar(max)`-Spalte erreicht den Callback ohne 2628, und eine Auswahl der Zeile endet danach mit 50003, sofern der Callback des Integrators sie speichert; nur der Bestand des Testservers weist sie jetzt ab (`TDS_BACKEND_ERROR`). Das KDoc nennt die Abweichung („meldet für -1 aber 4000“, Bestand), die Nutzer-Doku und SPEC-035 sagen für `MERGE` nur „2628“ ohne die Ausnahme -1. Der Zustand des Codes ist unverändert und gehört zur offenen Frage aus N-1; die Aussage der Doku ist für -1 breiter als der Code. Kein Failure-Szenario im Diff, daher INFO.
- `verifizierbar`: ja (Test: `MERGE` mit 4001 Zeichen in eine Spalte mit `maxLength` -1)
- `klasse`: Sonderlänge -1 in der Aussage zur Prüfung beim `MERGE` nicht genannt

### N-3 — INFO
- `kategorie`: INFO
- `quelle`: Skill „Mutationsbeschreibung nennt die gedachte statt der gemessenen Wirkung“ (Gegenprobe), Maintainability
- `pfad`: `docs/messungen/spaltenlaenge-mutationen-2026-11-19.md` · „| Mut-21 | die Auswahl meldet für -1 genau 4000“
- `befund`: Mut-21 und Mut-18 habe ich nicht gefahren. Beim Lesen ist Mut-21 (`reportedLength` für -1 ergibt 3999) auch an `TableStoreTest` `eine Spalte mit maxLength -1 haelt 4000 Zeichen und die Auswahl liefert sie` rot zu erwarten (`assertEquals(listOf(4000), start.columns.map { it.maxLength })`); die Spalte „Ergebnis“ nennt nur `SpaltenlaengeAuswahlWireTest` und `SimulationBackendSelectLengthTest`. Gelesen, nicht gemessen; ohne erwartete Aktion, Hinweis für den Verifier. Die Zeile Mut-13 nennt „Stand cc60026b, danach durch die Grenze 4000 ersetzt“: das ist eine Bedingung der Messung (Stand), keine Chronik der Regel.
- `verifizierbar`: ja
- `klasse`: Wirkung einer Mutation vom Lesen, nicht vom Lauf

## Mutationen

Gefahren (Einzelmessung 2026-11-20, ein Host, je Mutation ein Gradle-Lauf in Docker, `:testserver:test --tests '*TableStoreTest'`; Skript im Scratchpad, nicht im Repo; Ausgangslage grün, 24 Fälle):

| Nr | Mutation | Ergebnis |
|---|---|---|
| Mut-17 (Beleg) | `RowRules.kt`: für -1 gilt `Int.MAX_VALUE` statt `RESULT_TEXT_LIMIT` | rot, 2 von 24: `eine Spalte mit maxLength -1 haelt 4000 Zeichen und die Auswahl liefert sie`, `der Bestand nimmt Texte nur bis zur Laenge der Spalte an` (gleich dem Beleg; ein zweiter Lauf wiederholte es mit den Namen) |
| Mut-19 (Beleg) | `RowRules.kt`: Zählung in Codepunkten (`codePointCount`) | rot, 1 von 24: `die Laenge zaehlt UTF-16-Einheiten und jeder Wert von SET wird geprueft` (gleich dem Beleg) |
| Mut-20 (Beleg) | `TableStore.kt`: `set.entries.take(1)` in `update` | rot, 1 von 24: derselbe Fall (gleich dem Beleg) |

Gelesen, nicht gefahren: Mut-18 (`RESULT_TEXT_LIMIT = 3999`: der Test legt 4000 Zeichen in `Lang` an, der Aufruf wirft, rot plausibel), Mut-21 (siehe N-3). Die Mutationen des Vorgängers (E-A, E-B) sind damit durch Mut-20 und Mut-19 in den Zusagen des Tests belegt.

## Antworten auf die Schwerpunkte des Auftrags

1. **R-1 (`-1` im Bestand):** behoben. `RowRules.fitsLength` begrenzt -1 auf `RESULT_TEXT_LIMIT` 4000, wie `SimColumn.reportedLength` (`SimModel.kt`, -1 ergibt `MAX_TEXT_LENGTH`) es für die Auswahl meldet. Beleg im Bestand: `der Bestand nimmt Texte …` (4000 angenommen, 4001 abgewiesen, Massenladen und `UPDATE`) und `eine Spalte mit maxLength -1 haelt 4000 Zeichen und die Auswahl liefert sie` (der Test liest die Länge 4000 am `ResultStart` der Bibliothek und die Abweisung 4001 im Bestand in einem Fall: damit liest ein Test beide Orte der Zahl). Beleg am Draht: `ein Text von 4000 Zeichen in einer Spalte mit minus eins ist ein Ergebnis` (Länge 4000 im COLMETADATA, kein ERROR; gelesen, die Kette Bestand bis Draht ist nicht in einem Lauf gefahren). Mut-17 rot.
2. **R-2 und R-6 (Kopplung, Zählung, SET):** behoben. Kopplungskommentar an beiden Orten: KDoc von `SimulationMerges.truncates` („Kopplung: der Bestand des Testservers prüft … nach derselben Zählung (UTF-16-Einheiten, `RowRules.fitsLength`, Modul `testserver`), meldet für -1 aber 4000“) und KDoc von `RowRules.fitsLength` („Kopplung: dieselbe Regel und Zählung wie `SimulationMerges.truncates` und `SimColumn.reportedLength`“). Die Zählung (`String.length`, UTF-16-Einheiten) ist in `truncates` und `TextMapping` (`text.length > type.maxChars`) gelesen. Mut-19 und Mut-20 rot; der Test deckt `SET` mit dem langen Wert an erster und an zweiter Stelle und als SQL-Text. Die Zusage ist nicht breiter als der Beleg: „wie bei der Bibliothek“ ist am Code gelesen, am Draht nicht gemessen (UTF-16 am echten Server für Zeichen außerhalb der Grundebene ist nicht Teil der Zusage).
3. **R-3 (offene Frage):** behoben. Die Frage steht in Slice §6 (neues Risiko, „weiter offen, Träger Nutzer und Architect“) und in SPEC-035 („Offen: … nicht entschieden und braucht eine Folge-Entscheidung zur Durchsetzung der Länge; bis dahin sind die Nummern 2627 und 50004 des Bestands Setzung“) als offen, nicht als entschieden. Rest N-1.
4. **R-4 (Indikativ), R-5 (`onInsert`), R-7 (M-11):** R-4 behoben: `testserver-manuell.md` sagt im Indikativ („damit jede Auswahl eine gespeicherte Zeile ausliefert; ein solcher Text in einer Zeile beendet eine Auswahl mit `TDS_UNSUPPORTED_TYPE` (50003) und schließt die Verbindung“), das KDoc von `RowRules.check` ebenso („hält so nur Text, den eine Auswahl ausliefert“). R-5 behoben: `einbettung-und-betrieb.md` nennt `onInsert`, `onUpdate` und `onInsertBatch` und die Grenze 4000 bei -1; die Zeile `onInsert` ist an `SimulationChanges.prepare` gelesen (keine Prüfung vor dem Callback). R-7 behoben: SPEC-035 nennt „mit einer `DataTable`-Quelle“ und „ein Fall mit 23 Zeichen in `nvarchar(5)`“, wie M-11 es führt. Treffer im geänderten Text auf „bisher“, „noch nicht“, „soll“, „vorläufig“, „Zielzustand“, „seit Slice“: keine. Die Aussage „damit jede Auswahl eine gespeicherte Zeile ausliefert“ gilt für Text über den Bestand der Callbacks Massenladen und `UPDATE` (ein `INSERT` als SQL-Text hat im Bestand keinen Callback, SPEC-035 sagt es); nicht breiter als der Beleg (`TableStoreTest`, Auswahl nach `UPDATE` und Massenladen).
5. **Hard Rules:** §3.1 ohne Befund (kein Host-Aufruf im Diff). §3.2 ohne Befund (Grep des Diffs auf `@Suppress`, `@SuppressWarnings`, `noqa`, `nolint`, `SuppressMessage`: kein Treffer). §3.5 ohne Befund (`git diff e5b146f9..HEAD --name-only` nennt keine Datei unter `docs/plan/adr/`). §3.7: Kommentare in Indikativ und mit Klasse Kopplung; die Wächter-Halbsätze der zwei neuen Test-KDocs („er nähme 4001 an“, „drei Zeichen passten“) beschreiben die mutierte Implementierung wie im Vorgängerlauf und sind wie dort nicht gezählt. §3.8: N-2 (Reichweite der Aussage zu `MERGE` und -1, INFO). Keine `- [x]` im Slice.

## Geprüft, ohne Befund

- `testserver/kotlin/io/github/pt9912/tdswire/testserver/RowRules.kt`: `RESULT_TEXT_LIMIT` und `fitsLength` im Indikativ, nur `UnicodeText` begrenzt, Rückgabe `value.length <= limit`; Randfälle 4000/4001 und 5/6 belegt.
- `testserver/test/kotlin/io/github/pt9912/tdswire/testserver/TableStoreTest.kt`: die drei geänderten oder neuen Fälle haben verschiedene Eingaben je Zusage (4000/4001, Zeichen außerhalb der Grundebene, `SET` mit zwei Werten in beiden Reihenfolgen, SQL-Text und Callback); kein Test mit einheitlicher Eingabe.
- `src/test/kotlin/io/github/pt9912/tdswire/runtime/SpaltenlaengeAuswahlWireTest.kt`: neue Zeile 3 im Fixture ändert die anderen Fälle nicht (sie wählen über `id`); der neue Fall hat Name, Boundary-Kopf und `@spec`.
- `src/main/kotlin/io/github/pt9912/tdswire/simulation/SimulationMerges.kt`: nur KDoc geändert (Diff gelesen), kein Code.
- `spec/spezifikation.md` (SPEC-035, Änderungsprotokoll): Setzungen gekennzeichnet, Änderungsprotokoll nennt -1 über 4000; keine neue Zusage ohne Anker im Lastenheft.
- `docs/user/` (beide Dateien): geänderte Absätze gegen Code und Spezifikation gelesen.
- `docs/plan/spec-abdeckung.md`: Zähler SPEC-003 plus 2 und SPEC-035 plus 3 stimmen zu den drei neuen Fällen mit `@spec`-Tags (Tests: Wire-Fall plus Fall `Laenge zaehlt` plus Fall `maxLength -1 haelt`); Format unverändert.
- `docs/plan/planning/in-progress/slice-simulation-spaltenlaenge-in-auswahl.md`: jede berührte Datei des Diffs steht in Slice §3 (Grep je Dateiname: `SimulationMerges`, `RowRules`, `TableStoreTest`, `SpaltenlaengeAuswahlWireTest`, beide Nutzer-Dokus, Mutationsbeleg).
- `docs/messungen/spaltenlaenge-mutationen-2026-11-19.md`: Mut-17, Mut-19, Mut-20 wiederholt und gleich; Mut-18 und Mut-21 gelesen (N-3).

## Urteil

Kein HIGH, kein MEDIUM, kein LOW. Drei INFO (N-1 Ausgang „weiter offen“ ohne Register-Zeiger, Closure-Schritt; N-2 Ausnahme -1 im `MERGE` nicht in der Aussage der Doku; N-3 Mut-18 und Mut-21 nicht gefahren). Die Vorgänger-Findings R-1 bis R-7 sind behoben und nicht breiter als ihr Beleg; Mut-17, Mut-19, Mut-20 sind rot wie im Beleg.

**Empfehlung:** Verifier ja; kein weiterer Re-Review nötig (die Code-Fläche dieser Nachbesserung ist `RowRules.kt`, ein KDoc und Tests, alle hier gelesen und gefahren). Der Verifier fährt `make clean-build` und `make e2e` je einmal auf dem End-Stand; N-1 gehört in die Closure (Planner).
