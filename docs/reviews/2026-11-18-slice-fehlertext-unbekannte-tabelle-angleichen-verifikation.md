# Verifikationsbericht: slice-fehlertext-unbekannte-tabelle-angleichen — 2026-11-18

**Rolle:** Verifier (Modul 11), frischer Kontext; DoD- und Entscheidungs-Konformität, Plan-vs-Code-Diff. Kein Review, kein Fix, keine DoD-Haken, kein Schließen, keine Änderung an `roadmap.md` oder am Lifecycle.

**Stand:** Branch `slice-fehlertext-unbekannte-tabelle-angleichen`, Spitze `75878be9` (Commits `f65df36c` Umsetzung, `caf7dc91` Review-Report, `261d9015` Nachbesserung, `75878be9` Merge von `main`); Arbeitsbaum sauber vor diesem Bericht.

**Gegenstand:** [slice-fehlertext-unbekannte-tabelle-angleichen](../plan/planning/in-progress/slice-fehlertext-unbekannte-tabelle-angleichen.md) (A-3, Option A „Code angleichen“, Nutzerentscheidung 2026-11-18); [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-06](../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände), [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0007](../plan/adr/0007-fehler-und-attention.md), [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md). Review: [Review-Report](2026-11-18-slice-fehlertext-unbekannte-tabelle-angleichen.md) (kein HIGH; MEDIUM F-1 bis F-4 mit `261d9015` behoben; der Report nennt die Messung noch „M-37“, das ist der Stand des Reviews, im Baum heißt sie M-42).

## Selbst gefahrene Sensoren (End-Stand `75878be9`, unter der Messsperre, Docker)

| Sensor | Ergebnis | Dauer |
|---|---|---|
| `make clean-build` | Exit 0 (Jar, Tests, detekt, `--no-cache`) | 730 s |
| `make gates` (ohne `FORCE`) | Exit 0, **11 s**: die Gruppenschritte `werkzeug`, `tests`, `stempel` sind „uebersprungen, Beleg“ (Stempel am Baum-Hash); gelaufen ist die Klasse `immer` (`docs-check`, `flaeche`, `gate-zuordnung` mit 33 Fällen, `stempel-eingaenge`). Der Frischbeleg für Tests und detekt ist `make clean-build`; die Klassen `werkzeug` und `stempel` stehen auf dem Stempel des Implementers, `FORCE=1` nicht gefahren | 11 s |
| `make e2e` | Exit 0, „gruen (9 Stufen, 513 s)“; Stufen 1 bis 9: 86, 120, 64, 55, 17, 21, 39, 17, 94 s | 513 s |
| `make vergleichslauf-d LAUF=live` | Exit 0, 2022 und 2025; Schritt `fehler-vor-zeilen` auf Referenz **und** Server: `SqlException Number 208, Class 16, State 1, Meldung 'Invalid object name 'gibtesnicht'.'; Zustand Open; Folgeabfrage 1`, Vergleich „ok gleich fehler-vor-zeilen“ ohne Ausnahmeregel (beide Instanzen) | 109 s |
| `make vergleichslauf-d LAUF=mitschnitt` | Exit 0, `mitschnitt-2022` und `mitschnitt-2025`, „Mitschnitt-Vergleich: gleich oder genau die gelistete Abweichung“; `fehler-vor-zeilen/01/fehler`: Referenz `…Meldung 'Invalid object name 'gibtesnicht'.', Server vorhanden, Prozedur '', Zeile 1`, Server `…Meldung 'Invalid object name 'gibtesnicht'.', Server leer, Prozedur '', Zeile 0`; nur dieses Feld läuft über die verkleinerte Regel (Art `Luecke`), Tokenfolge, Nummer, Class, State, Text sind gleich | 32 s |
| Mutationen je Zusage (`make test`, Quelle danach per `git checkout -- src` zurück) | alle drei rot, siehe unten | 196 s, 211 s, 67 s |
| Nachprüfung der Messablage | siehe unten | — |

Gesamt rund 33 Minuten; größte Posten `make clean-build` (730 s), `make e2e` (513 s, Stufe 2 mit 120 s) und die drei Mutationsläufe (zusammen 474 s). Messsperre `/tmp/claude-1000/-Development-tdswire/messlock` frei übernommen und je Lauf freigegeben (trap); `available` vor jedem Lauf 12,7 bis 17 GB (Grenze 4000 MB nie berührt). Keine künstliche Last. Nicht gefahren: `make spec-abdeckung`, `make docs-check` einzeln (lief in `make gates`), `make vergleichslauf-d LAUF=bulk` (die Messung M-42 selbst, siehe unten; der Reviewer verlangte sie nicht).

**Mutationen (an `src/main`, gefahren):**

| Mutation | Wirkung | rot durch |
|---|---|---|
| `ErrorMapper.nameOf`: Steuerzeichen-Prüfung entfernt, Grenze 515 auf 516 | rot, 6 `FAILED`-Zeilen | `ErrorMapperObjectNameTest` („Ein Name ausserhalb der Grenzen oder ohne Regel erreicht den Client nie“) |
| `StatementPlanner.shownName`: Grenze 128 auf 129 je Teil | rot | `StatementPlannerTest` („der Name ausserhalb der Grenzen und bei einer Spalte fehlt“) |
| `ErrorRule.message`: Text mit Namen ersetzt durch festen Text | rot, 4 `FAILED`-Zeilen | u. a. `MergeFehlerEmbeddingTest` (Text mit dem Namen der Tabelle auf dem Draht) |

## Plan-vs-Code-Diff (`git diff main...HEAD`, 24 Dateien, +665 −56)

- **`src/main` (5 Dateien):** `QueryEvent.Failure` um `objectName: String? = null` (`@JvmOverloads` hält die Konstruktoren mit zwei und drei Argumenten); `ErrorRule.namesObject`, `ErrorRule.message(objectName)`, `ErrorRule.nameOf` (Länge 1 bis 515, kein `isISOControl` außer Zeilenumbruch, sonst fester Text), `MAX_OBJECT_NAME`; `ErrorMapper.failure` und `sequencerMapping().backend` rufen beide `nameOf`; `StatementPlanner.shownName` (je Teil höchstens 128 Zeichen, Steuerzeichen) und `PlanResult.Rejected.objectName`; `SimulationBackend.failed` reicht den Namen im `Failure` weiter. In `261d9015` nur KDoc geändert (`QueryEvent.kt`, `ErrorMapper.kt`; Diff ohne Nicht-Kommentar-Zeilen geprüft).
- **Tests:** `ErrorMapperObjectNameTest` (neu), `StatementPlannerTest`, `SimulationBackendSelectTest`, `MergeFehlerEmbeddingTest` (Text auf dem Draht), `MessungBulkTests.cs` (Szenario `fehler-tabelle-unbekannt`), `MessungBulkBelegTests.cs` (Beleg der Ablage).
- **Ausnahmeliste:** `live-ausnahmen.tsv` verliert die Regel `fehler-vor-zeilen` (belegt: Live-Lauf „ok gleich“ auf beiden Instanzen); `mitschnitt-ausnahmen.tsv` führt `fehler-vor-zeilen/01/fehler` als A-3 auf Servername und Zeilennummer verkleinert (belegt: Mitschnitt-Lauf).
- **Doku:** Spezifikation (Abschnitt 4, `SPEC-002`, `SPEC-038`, `SPEC-045`, `SPEC-059`-Änderungsprotokoll), Messprotokoll (M-42, A-3 umbenannt), Kompatibilitätsmatrix, `docs/user/einbettung-und-betrieb.md`, `referenz-konfiguration.md`, `spec-abdeckung.md`, Register `BEO-TDS/unbekannte-tabelle-class-20-statt-16-am-server` (`state.md`), Slice §1, §3, §7.
- **Plan-Tabelle §3** nennt jetzt alle berührten Dateien (F-4 behoben); `SimulationBackendFixedTest.kt`, `…ChangeTest.kt`, `…BulkTest.kt`, `BulkCopyFormsTest.kt` stehen als „nicht berührt“, im Diff unberührt. Keine Datei außerhalb der Tabelle geändert.
- **Kennung M-37 aufgelöst:** `grep` findet M-37 außerhalb von Zeilenform-Dokumenten nur im Slice-§7-Vermerk (nennt den Konflikt); die Messung heißt überall M-42.

## DoD Punkt für Punkt (Haken im Slice sind noch leer, das ist Sache des Planners)

1. **Messung am echten Server (M-42): erfüllt.** Ablagen `referenztest-dotnet/Mitschnitt/messung/bulk-merge-katalog/{2022,2025}/fehler-tabelle-unbekannt.messung` (Kopf: Instanz, Image-Digest, Client, Aufnahme 2026-11-18). Ich habe je Instanz die Serverantworten ausgelesen: **24** Antworten tragen den UTF-16-Text `Invalid object name`, 27 enthalten ein ERROR-Muster 208 (je 68 Zeilen); das entspricht der Aussage von M-42 (27 Fälle, 24 mit 208, 3 mit 103). Name, Schema, Hochkomma, Klammern, Zeilenumbruch, Länge 128 und 129, `INSERT`, `UPDATE`, `DELETE`, `MERGE`, `sp_executesql` sind Fälle der Messung. Den Erzeuger (`LAUF=bulk-2022`/`-2025`) habe ich nicht neu gefahren; der Live-Lauf oben bestätigt am echten Server denselben Text für den Fall `gibtesnicht` auf beiden Instanzen.
2. **Angleichung: erfüllt, mit einer Abweichung vom DoD-Wortlaut.** `TDS_SIM_UNKNOWN_OBJECT` meldet `Invalid object name '<Name>'.` (Test auf dem Draht in `MergeFehlerEmbeddingTest`, Mutation 3 rot); Tests für Happy, Hochkomma, langen Namen, Schema, Steuerzeichen, Sequenzierer-Weg (Mutationen 1 und 2 rot). Die Live-Regel `fehler-vor-zeilen` **entfällt** (belegt). Die Mitschnitt-Regel `fehler-vor-zeilen/01/fehler` **entfällt nicht**, sie bleibt verkleinert (Servername leer und Zeile 0 gegen vorhanden und 1): sachlich richtig, vom Lauf belegt, im Slice §7 als Abweichung vermerkt; der **Wortlaut der DoD 2 ist nicht angepasst** (siehe V-1).
3. **Nachzug: erfüllt.** Abschnitt 4 (Zeile `TDS_SIM_UNKNOWN_OBJECT`, der Satz „Text fest und englisch“ trägt jetzt die Ausnahme, F-2 behoben), Messprotokoll (A-3, M-42 mit Bedingungen und Grenzen), Register. Das Register nennt Zustand und Beleg.
4. **Gates und Sensoren der Art: erfüllt, mit der Einschränkung der Zeile `make gates` oben (V-2).** `make clean-build`, `make e2e`, `make gates` Exit 0; `docs-check` und `flaeche` liefen in `make gates` grün. `kopplung-zahlen` ist Klasse `werkzeug` und lief über den Stempel-Beleg, nicht frisch. `make spec-abdeckung` ist Implementer-Pflicht; nicht wiederholt (die Datei `docs/plan/spec-abdeckung.md` liegt im Diff).
5. **Review: erfüllt.** Ein Report, kein HIGH; F-1 bis F-4 mit `261d9015` behoben (F-1: die Setzungen 515 und Zeichenmenge sind in KDoc, Spezifikation, beiden Nutzer-Seiten und M-42 als solche gekennzeichnet; F-2: Satz „fest“ nachgezogen; F-3: M-42; F-4: Plan-Tabelle). F-5 bis F-8 (LOW/INFO) offen als Hinweise, siehe V-3. Die Nachbesserung änderte in `.kt` nur KDoc, ein Re-Review ist nicht nötig.
6. **Doku-Update öffentlicher Vertrag: erfüllt** (Spezifikation, `SPEC-045`/`SPEC-059`-Änderungsprotokoll, Messprotokoll, beide TSV). Die Mitgliederliste von `SPEC-059` nennt `Failure.objectName` im Änderungsprotokoll, nicht in der Typaufzählung (F-5, LOW, offen).
7. **Closure-Notiz, Register-Ausgang, Risiko-Ausgänge (§6), Paarungen: offen, Closure-Handlung des Planners.** §7 trägt nur die Vermerke des Implementers; alle fünf Risiken in §6 stehen auf „offen bis zur Closure“.

## AGENTS.md §3.8 (Aussage nicht breiter als Beleg)

- **515 Zeichen und die Zeichenmenge (`isISOControl` ohne Zeilenumbruch)** sind in KDoc (`Failure`, `MAX_OBJECT_NAME`), Spezifikation, Nutzer-Doku und Messprotokoll als **Setzung, nicht gemessen** gekennzeichnet; die 128 Zeichen je Teil nur für das Simulations-Backend und gemessen. Die zwei Grenzen stehen nicht mehr mit verschiedenem Wert an zwei Stellen (F-1 behoben). Keine Aussage breiter als der Beleg gefunden.
- **`INSERT BULK` und die Probe des Massenladens senden weiter „Unknown object.“** (`StatementPlanner.kt` Zeilen um 141 und 144, `BulkCopyForms.kt`; `BulkTargetResult.Rejected` und `BulkProbeResult.Rejected` tragen kein `objectName`); der echte Server nennt dort den Namen (M-35). Als Grenze an drei Stellen benannt (Spezifikation, Messprotokoll, Matrix).
- Die Strukturabweisung (`StructurePlanner`: Schlüsselspalte, Spalte, Zieltabelle eines Fremdschlüssels) sendet ebenfalls den festen Text; die Aufzählung der Anweisungen mit Namen in der Spezifikation nennt `SELECT`, `INSERT`, `UPDATE`, `DELETE`, `MERGE`, aber den Fremdschlüssel-Fall nicht ausdrücklich als „ohne Namen“ (V-4, INFO).
- Der Live-Lauf belegt den Text für `SELECT … gibtesnicht` (ein Fall, Referenz und Server gleich); die übrigen Anweisungsformen stützen sich auf M-42 (Referenz) und die Tests (Bibliothek), nicht auf einen Lauf der Bibliothek je Form am Draht. Das ist im Review (F-8) als „kein Mangel“ geführt; `make e2e` Stufe 3 bis 7 ist grün.

## Offene Nutzerfragen (Träger Nutzer, in §7 des Slice, NICHT entschieden, hier nicht als erledigt geführt)

1. Servername und Zeilennummer im ERROR (leer, 0): ein Name (welcher?) und `1` senden, oder bleibt A-3 im Mitschnitt? Solange offen, trägt die verkleinerte Mitschnitt-Regel.
2. Das öffentliche Mitglied `Failure.objectName` (`SPEC-059`; `copy` und `component4` ändern sich für Kotlin-Aufrufer): bestätigen oder an den Architect; steigt dabei `y` von `0.y.z`?
3. `INSERT BULK` und die Probe senden weiter „Unknown object.“: Folge-Slice mit einem Feld an `BulkTargetResult.Rejected` und `BulkProbeResult.Rejected`?
4. Bezeichner über 128 Zeichen: der echte Server antwortet 103 (Class 15), hier 208 mit festem Text; 103 abbilden?
5. Die Grenzen 515 und die Zeichenmenge sind Setzungen (U+2028, U+2029, Bidi, U+FEFF laufen durch, `NUL` und Tabulator nicht gemessen): gelten sie?

## Befunde

- **V-1 (offen, Planner):** der Wortlaut von DoD 2 („die Regeln `fehler-vor-zeilen` und `fehler-vor-zeilen/01/fehler` entfallen — oder Art *so gewollt*“) steht unverändert im Slice; §7 sagt „Der Wortlaut ist vor der Verifikation anzupassen; der Haken bleibt ungesetzt“. Die Anpassung ist nicht erfolgt. Der Code und die Listen tun das Richtige (Live-Regel entfallen, Mitschnitt-Regel verkleinert); vor dem Haken muss der Wortlaut zur Lieferung passen.
- **V-2 (Hinweis):** `make gates` lief mit Stempel-Belegen (11 s). Frischbeleg der Klassen `tests`: `make clean-build`; `werkzeug` und `stempel`: Stempel des Implementers. Wer Frisch will, fährt `make gates FORCE=1` (nicht gefahren).
- **V-3 (Hinweis):** die LOW/INFO-Befunde F-5 (Mitgliederliste von `SPEC-059`, kein Einbettungstest mit `Failure(…, objectName)` über ein `Custom`-Backend), F-7 (Unicode-Formatzeichen laufen durch, Setzung, Frage 5) und F-8 sind nicht behoben; sie sind keine DoD-Verletzung. F-6 ist mit dem §7-Vermerk und den beiden Läufen oben erledigt.
- **V-4 (INFO):** die Strukturabweisung mit unbekannter Zieltabelle (Fremdschlüssel) sendet „Unknown object.“; das Dokument nennt diese Ausnahme nicht ausdrücklich.
- Keine DoD-Verletzung, keine Entscheidungs-Verletzung (Option A, Code angleichen, wie am 2026-11-18 entschieden; Option B nicht stillschweigend übernommen), keine Aussage breiter als der Beleg, keine Hard Rule (§3.1, §3.2, §3.5, §3.6) verletzt.

## Urteil

**DoD erfüllt für die Liefer-Punkte 1 bis 3 und die Sensoren der Änderungsart (Punkte 4 bis 6): ja**, mit der Abweichung der Mitschnitt-Regel (verkleinert statt entfallen), die der Slice vermerkt und die vom Mitschnitt-Lauf belegt ist. Offen sind nur Handlungen des Planners (Wortlaut DoD 2 anpassen V-1; Closure-Notiz mit Steering-Loop-Eintrag, Register, Ausgänge der Risiken §6, Paarungen) und die **fünf Fragen beim Nutzer** oben. Der Slice darf nach `done/`, sobald der Planner V-1 und die Closure-Handlungen erledigt und die Fragen entweder entschieden oder als offen mit Träger Nutzer geführt hat.
