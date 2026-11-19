# Review-Report: slice-simulation-mehrere-anweisungen-und-ausgabewert, Re-Review der Nachbesserung — 2026-11-19

**Review-Art:** Re-Review (Folgelauf, neue Datei) gegen den [ersten Report](2026-11-19-slice-simulation-mehrere-anweisungen-und-ausgabewert.md), den Plan, [ADR-0055](../plan/adr/0055-zwei-auswahlen-ohne-parameter-in-einem-text-sind-erkannt.md) (`Proposed`), [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md), [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md) und die Hard Rules (`AGENTS.md` §3.1 bis §3.8).

**Gegenstand:** Diff `0e639555...HEAD` des Zweigs `slice-simulation-mehrere-anweisungen-und-ausgabewert` (HEAD `b2d46e27`): Merge `6aef21e9` von `main`, ADR-Commit `78676037` (Architect), Nachbesserung `b2d46e27`. Gelesen sind nur diese Commits, nicht der ganze Slice noch einmal (Slice [slice-simulation-mehrere-anweisungen-und-ausgabewert](../plan/planning/in-progress/slice-simulation-mehrere-anweisungen-und-ausgabewert.md)).

**Skill:** `.harness/skills/reviewer.md` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-19

**Eingangs-Kontext:** erster Report (F-1 bis F-13), Slice (§1, §3, §4, §6, DoD), [TDS-FA-10](../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität), [SPEC-036](../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig), [SPEC-030](../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten), [TDS-FA-11.a](../../spec/spezifikation.md#tds-fa-11a--simulations-backend), Messprotokoll des Referenzabgleichs (A-2, A-16, M-24), Mutationslogs des Implementers (`mut2-invalid.log`, `mut2-noreturn.log`, `mut2-emptypiece.log` im Scratchpad `impl-mehrere-anweisungen`).

**Ausgeführte Sensoren:** `make docs-check` Exit 0 (1678 Dateien, 0 Befunde) und `make flaeche` Exit 0 auf dem Stand mit diesem Report. Nicht neu gefahren: `make gates`, Gradle, Mutationen; auf `HEAD` liegen die Stempel `werkzeug` und `tests` als grün (`git notes --ref=refs/notes/gates show HEAD`). Die drei Mutationen des Implementers sind an den Logs nachvollzogen (Wirkung je Log gelesen), nicht wiederholt. Nicht gefahren, weil nicht Teil der Reviewer-Pflicht für `src/`: `make clean-build`, `make e2e`, Live-Lauf.

## Zustand der Nachbesserung gegen den ersten Report

| Finding | Urteil | Beleg im Diff (Kurzfassung) |
|---|---|---|
| F-1 (ADR-Träger) | nicht Gegenstand; Konsistenz geprüft | [ADR-0055](../plan/adr/0055-zwei-auswahlen-ohne-parameter-in-einem-text-sind-erkannt.md) `Proposed`, Index ergänzt; Befunde N-2, N-3 |
| F-2 (RETURNVALUE „gemessen“) | behoben | Matrix 7a und Nr. 16, Kommentar in `SzenarienTests.cs`; Rest N-6 |
| F-3 (Live-Beleg zitiert) | behoben | Nr. 16 „der Live-Lauf ist nicht gefahren“, 7a „Lauf … noch nicht gefahren“; Rest N-5 |
| F-4 (OUTPUT, Bedingung) | behoben | [SPEC-030](../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten) und Nutzer-Doku: nur `sp_executesql` gemessen; Rest N-6 |
| F-5 (zweite Fundstellen) | behoben | [TDS-FA-11.a](../../spec/spezifikation.md#tds-fa-11a--simulations-backend) Schritte 1 und 5, `einbettung-und-betrieb.md` |
| F-6 (Plan-Tabelle) | behoben bis auf die ADR-Dateien | §3 gegen `git diff --name-only` gelesen; Rest N-1 |
| F-7 (zwei Zweige ohne Test) | behoben | zwei Tests, Mutationen rot (unten) |
| F-8 (Reihenfolge der Prüfung) | behoben durch Beschreibung | [SPEC-036](../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) und Test des Planers; Rest N-2 |
| F-9 (Setzung nicht gekennzeichnet) | behoben | Zeile S2 „Festlegungen, nicht gemessen“, Slice §1, Test `ein nicht überführbarer Wert …` |
| F-10 (Konjunktiv im Kommentar) | behoben | Kommentar in `SzenarienTests.cs` im Indikativ |
| F-11 (Historie) | behoben | Zeile 2026-11-19 in `spec/spezifikation.md` Abschnitt 8 |
| F-12 (Grund leerer Stücke) | behoben | zwei Fälle in `StatementFormsTest`; Hinweis in N-7 |

## Findings

### N-1 — MEDIUM
- `kategorie`: MEDIUM
- `quelle`: Skill „Plan-Tabelle nennt die berührten Dateien nicht“; [ADR-0055](../plan/adr/0055-zwei-auswahlen-ohne-parameter-in-einem-text-sind-erkannt.md)
- `pfad`: `docs/plan/planning/in-progress/slice-simulation-mehrere-anweisungen-und-ausgabewert.md` · „Die Folge-ADR zu“ (§1, Satz über die Folge-ADR zu [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md), Entscheidung 14) und die Tabelle in §3
- `befund`: Der Zweig trägt `docs/plan/adr/0055-zwei-auswahlen-ohne-parameter-in-einem-text-sind-erkannt.md` und `docs/plan/adr/README.md`; §3 nennt beide nicht, und der Slice nennt die Kennung [ADR-0055](../plan/adr/0055-zwei-auswahlen-ohne-parameter-in-einem-text-sind-erkannt.md) nirgends (§1, §4, §6 sagen „Folge-ADR … schreibt der Architect als `Proposed`“, `Bezug:` führt sie nicht). Wer bei der Closure den Plan liest, findet die offene Entscheidung nicht über die Kennung.
- `verifizierbar`: ja (Abgleich Tabelle und `Bezug:` gegen `git diff --name-only`; Grep „0055“ im Slice ohne Treffer)
- `klasse`: Plan-Tabelle gegen Diff nicht abgeglichen

### N-2 — LOW
- `kategorie`: LOW
- `quelle`: [ADR-0055](../plan/adr/0055-zwei-auswahlen-ohne-parameter-in-einem-text-sind-erkannt.md) Entscheidung 4 und Fitness Function, [SPEC-036](../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) (Liste „Nicht erkannt“), `AGENTS.md` §3.8
- `pfad`: `docs/plan/adr/0055-zwei-auswahlen-ohne-parameter-in-einem-text-sind-erkannt.md` · „Alles andere bleibt nicht erkannt und endet mit `TDS_UNSUPPORTED_SQL`“
- `befund`: Die ADR führt „zwei Auswahlen mit Parametern“ unter „bleibt nicht erkannt, endet mit `TDS_UNSUPPORTED_SQL`“. Spezifikation und Code (`SelectSeries.prepare`, Test `zwei Auswahlen mit Parametern …` in `SimulationBackendSelectTest`) erkennen den Text im Parser und weisen ihn erst nach der Prüfung gegen die Beschreibung ab; mit unbekannter Tabelle antwortet der Server `TDS_SIM_UNKNOWN_OBJECT`. Die Lesart „nicht erkannt“ der ADR und die der Spezifikation (F-8 des ersten Reports) fallen für diesen Fall auseinander; die ADR ist `Proposed`, ihre Wortwahl ist noch änderbar.
- `verifizierbar`: ja (Test: Folge mit unbekannter Tabelle und einem Parameter, in `SimulationBackendSelectTest` seit `b2d46e27`)
- `klasse`: ADR und Spezifikation lesen „nicht erkannt“ verschieden

### N-3 — LOW
- `kategorie`: LOW
- `quelle`: Skill-Punkt „Test-KDoc oder Slice-Plan nennt den abwesenden … Zustand“ sinngemäß, `AGENTS.md` §3.7
- `pfad`: `docs/plan/adr/0055-zwei-auswahlen-ohne-parameter-in-einem-text-sind-erkannt.md` · „Beides ist nach Annahme nachzuziehen (Review F-5)“
- `befund`: Die Konsequenz sagt, [TDS-FA-11.a](../../spec/spezifikation.md#tds-fa-11a--simulations-backend) Schritt 1 und die Anleitung seien nach der Annahme nachzuziehen und die Schritte 5 und 7 beschrieben ein Resultset je Auswahl; der Zweig hat Schritte 1 und 5 und die Anleitung bereits fortgeschrieben (`b2d46e27`), Schritt 7 ist unverändert und widerspricht der Form S2 nicht. Die ADR beschreibt damit einen Zustand, den der Zweig nicht mehr hat.
- `verifizierbar`: ja (Diff `b2d46e27`)
- `klasse`: Zustandsbeschreibung der ADR überholt

### N-4 — LOW
- `kategorie`: LOW
- `quelle`: Skill „Fortschreibung lässt die zweite Fundstelle stehen“
- `pfad`: `docs/plan/planning/in-progress/slice-simulation-mehrere-anweisungen-und-ausgabewert.md` · „die beiden Zeilen der Matrix (Nr. 16, Abschnitt 7) auf „gemessen“ gestellt“ (§1, Option A)
- `befund`: Die Nachbesserung stellt die beiden Zeilen der Matrix gerade nicht auf „gemessen“ (7a trägt „Erwartung des Tests; Lauf … noch nicht gefahren“, Nr. 16 „am Mitschnitt gemessen“). Der Absatz der Option steht als Plan-Text weiter im Slice; ebenso „Offen für den Nutzer: welche Kombinationen gelten“, das §1 oben als Setzung beantwortet.
- `verifizierbar`: ja (Grep „auf „gemessen“ gestellt“)
- `klasse`: Fortschreibung lässt zweite Fundstelle stehen

### N-5 — LOW
- `kategorie`: LOW
- `quelle`: `AGENTS.md` §3.8, Skill „Zusage ohne ihre gemessene Bedingung im selben Satz“
- `pfad`: `docs/plan/kompatibilitaetsmatrix.md` · „7a misst am Referenzclient die Ergebnismengen, nicht das RETURNVALUE“ (Abschnitt 5, Nr. 16)
- `befund`: Der Satz steht im Präsens („misst“), die Zeilen von 7a sagen im selben Dokument, der Lauf am Referenzclient sei noch nicht gefahren. Gemeint ist, was der Test messen wird; der Satz sagt es nicht.
- `verifizierbar`: nein
- `klasse`: Messung im Präsens vor dem Lauf

### N-6 — INFO
- `kategorie`: INFO
- `quelle`: `AGENTS.md` §3.8 (Zuständig: Verifier, nach dem Lauf von `make kompatibilitaet`)
- `pfad`: `docs/plan/kompatibilitaetsmatrix.md` · „der Client lässt den Eingabewert auch ohne stehen“ und `spec/spezifikation.md` · „M-24 misst nur das Flag-Feld“
- `befund`: Beide Sätze sind enger als die Fassung vorher und deshalb sicher, tragen aber ihren Beleg nicht im Stand: das Verhalten des Clients ohne RETURNVALUE stand als gemessene Zeile in der Matrix vom 2026-10-31 (nur noch in `git`); M-24 nennt Typen mit Wert und einen nicht zugewiesenen Ausgabewert (`int`, `nvarchar`), „nur das Flag-Feld“ ist eine Lesart der Zeile, nicht ihr Wortlaut. Nach dem Lauf der Stufe 4 trägt die Matrix die Messung selbst.
- `verifizierbar`: nein
- `klasse`: Beleg einer Einschränkung nicht im Stand

### N-7 — INFO
- `kategorie`: INFO
- `quelle`: Skill „Eigencode, dessen Entfernung keinen Test rot macht“
- `pfad`: `src/test/kotlin/io/github/pt9912/tdswire/simulation/SimulationBackendSelectTest.kt` · „nach einem Fehler der ersten Quelle folgen weder die zweite Auswahl noch der Ausgabewert“
- `befund`: Die Mutation `read(part)` ohne `return` ist rot, aber an der ersten Zusicherung (Zeile 450, „List has more than one element“); die zweite Hälfte des Tests (Fehler bei einer Auswahl mit `InputOutput`-Parameter, kein Echo) ist im Lauf nicht erreicht worden, sie fängt nach Lesart des Codes (`readAll`: `echo` nur nach `return`-freiem Durchlauf) die Mutation „Schleife stoppen, `echo` trotzdem“. Zu F-12 des ersten Reports: `mut2-emptypiece.log` zeigt, dass auch `StatementOracleTest` (`SELECT a FROM t; /* x */ ;`, erwartet `UNEXPECTED_TOKEN`) den Grund schon vor der Nachbesserung prüfte; die Aussage „kein Test prüft den Grund“ war zu weit, die zwei neuen Fälle sind zusätzlich, nicht allein tragend.
- `verifizierbar`: ja (Mutation `break` statt `return`)
- `klasse`: Zweiter Teil eines Tests ohne eigene gefahrene Mutation

### N-8 — INFO
- `kategorie`: INFO
- `quelle`: Slice §2 und §4, [ADR-0055](../plan/adr/0055-zwei-auswahlen-ohne-parameter-in-einem-text-sind-erkannt.md), `AGENTS.md` §3.4
- `pfad`: `docs/plan/planning/in-progress/slice-simulation-mehrere-anweisungen-und-ausgabewert.md` · „(Stand: der Bau liegt im Zweig, die Entscheidung steht aus)“
- `befund`: Die Start-Bedingung „Folge-ADR vor dem Bau“ ist im Plan auf den tatsächlichen Ablauf umgeschrieben; die DoD hat keinen Liefer-Punkt, der die Entscheidung über [ADR-0055](../plan/adr/0055-zwei-auswahlen-ohne-parameter-in-einem-text-sind-erkannt.md) vor der Closure verlangt. Verwirft der Nutzer die Lesart, tragen [SPEC-036](../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) (Zeile S2), Code und Tests eine Form ohne Träger. Zuständig: Planner (Closure-Reihenfolge), Nutzer (Entscheidung).
- `verifizierbar`: nein
- `klasse`: Entscheidung des Nutzers nicht als Closure-Bedingung geführt

## Antworten auf die Schwerpunkte

1. **F-2 bis F-12 wirklich behoben?** Ja, mit den Resten N-1, N-4 bis N-7. Aussagen sind nicht breiter als der Beleg: die Matrix trennt Mitschnitt (gemessen) von Referenzclient (nicht gefahren), [SPEC-030](../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten) nennt die gemessene Bedingung (`sp_executesql`, ein Parameter, `INTN` 4) im selben Satz und die übrigen RPC-Formen als Setzung, die Nutzer-Doku sagt dasselbe. Die Festlegungen ohne Messung stehen in Zeile S2 und im Slice. Treffer auf die Wörter des Skills in `docs/user/` im Diff: keine; die Treffer außerhalb des Diffs sind dieselben wie im ersten Report.
2. **Tests und Mutationen.** Drei Mutationen, drei Logs, je am richtigen Test rot: `Invalid`-Zweig entfernt, `ClassCastException` in `SelectSeries.prepare` (Test `ein nicht überführbarer Wert in einer Auswahl weist die Folge ab`, beide Stellungen der Auswahl, `opens == 0`); `if (!read(part)) return` ohne `return`, `events.single()` scheitert (N-7); leeres Stück ohne Zweig, `UNEXPECTED_END` statt `UNEXPECTED_TOKEN` (`StatementFormsTest`, `StatementOracleTest`). Die Skripte stellen die Quelle per `trap` wieder her; `git status` des Worktrees ist sauber. Die Tests nutzen verschiedene Eingaben (beide Stellungen, mit und ohne `InputOutput`), keine einheitliche Eingabe. Plausibel und nachvollziehbar.
3. **Merge ohne Code-Auflösung.** `git merge-tree --write-tree 0e639555 091c96b0` meldet genau einen Konflikt, `docs/plan/spec-abdeckung.md` (erzeugte Datei); `git diff <Merge-Baum> 6aef21e9` unterscheidet sich nur in dieser Datei. Quelltext, Testcode, Skripte, TSV und Konfiguration kommen ohne Auflösung von `main`; die Datei ist in `b2d46e27` neu erzeugt (`SPEC-036` 259, `SPEC-030` 136; die Zählung deckt sich mit den zwei neuen getaggten Tests (`SPEC-036` +2, `SPEC-030` +1), `make spec-abdeckung` Exit 0 laut Log).
4. **Nachbesserung mit Code.** `b2d46e27` ändert Testcode (`SimulationBackendSelectTest.kt`, `StatementFormsTest.kt`) und einen Kommentar in `SzenarienTests.cs`; kein Produktionscode, keine Skripte, Make-, Build- oder Konfigurationsdatei (Grep auf `@Suppress`, `nolint`, `noqa` im Diff ohne Treffer; `config/`, `Dockerfile`, `tools/` unberührt). Mit diesem Report ist die Fläche zweimal gelesen; der Vermerk „Nachbesserung ohne zweiten Review“ entfällt für diese Commits.
5. **[ADR-0055](../plan/adr/0055-zwei-auswahlen-ohne-parameter-in-einem-text-sind-erkannt.md) gegen Spezifikation S2 und Code.** Obergrenze zwei: `SERIES_LIMIT = 2`, Test der Grenze, Zeile S2, Entscheidung 2 und 4 stimmen. Ohne Parameter: Entscheidung 2 und Zeile S2 stimmen, das Backend weist Parameter ab; die Lesart „nicht erkannt“ gegenüber „vom Parser erkannt, vom Backend abgewiesen“ und die Reihenfolge vor der Objektprüfung stehen nur in der Spezifikation (N-2). `TDS_UNSUPPORTED_SQL`: gilt für dritte Auswahl, andere Anweisung, leeres Stück und Parameter bei bekannten Tabellen. Messgrenze und Setzungen (Entscheidung 3) decken sich mit Zeile S2 und Messprotokoll. Status `Proposed` in der ADR, im Index und im Slice (§4) gleich; keine `Accepted`-ADR geändert (§3.5), `spec/architecture.md` nennt keine ADR (§3.4).
6. **Spezifikation, Matrix, Nutzer-Doku.** Grep über das Repo (ohne Baseline, Reviews, `done/`) auf „mehrere Anweisungen“, „eine Anweisung je Text“, „kein Ausgabewert“, „A-2“, „A-16“: jeder Treffer steht in der Änderungsliste oder trägt einen Grund (Messprotokolle zu anderen Formen, Katalog der Form D, Beobachtungs-Register, das der Slice bei der Closure fortschreibt). `docs/user/testserver-manuell.md` Zeile „Erkannt werden …“ und `einbettung-und-betrieb.md` „Was das Backend erkennt“ sind gleich zur Zeile S2. Die Aussage „mit Parametern endet mit `TDS_UNSUPPORTED_SQL`“ der Anleitung gilt wie in der Spezifikation für bekannte Tabellen.
7. **Hard Rules.** §3.1, §3.2, §3.5, §3.6: ohne Befund. §3.7: Kommentare der Nachbesserung im Indikativ (C#-Kommentar, Test-KDocs). §3.8: Reste N-5, N-6. Keine `- [x]` im Slice (keine vorweggenommenen Haken).

## Geprüft, ohne Befund

- `src/test/kotlin/io/github/pt9912/tdswire/simulation/` (`SimulationBackendSelectTest.kt`, `StatementFormsTest.kt`): drei neue beziehungsweise erweiterte Tests tragen die Zusage, Mutationen rot (N-7 als Hinweis).
- `src/main/kotlin/io/github/pt9912/tdswire/simulation/`: seit dem ersten Report unverändert (Diff `0e639555...HEAD` ohne Eintrag in `src/main`); `SelectExecution.kt` und `SeriesForms.kt` gegen die Aussagen von Spezifikation und Tests gelesen.
- `referenztest-dotnet/` (`SzenarienTests.cs`): nur Kommentar geändert; die TSV-Listen kamen ohne Auflösung aus dem Merge und dem ersten Commit.
- `docs/messungen/referenzabgleich.md`, `docs/plan/kompatibilitaetsmatrix.md`, `docs/user/`: Reichweite der Aussagen gelesen, Reste N-5, N-6.
- `spec/spezifikation.md`: [TDS-FA-11.a](../../spec/spezifikation.md#tds-fa-11a--simulations-backend) Schritte 1, 5, 7, [SPEC-030](../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten), [SPEC-036](../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig), Historie: ohne Befund außer N-2 (Gegenstelle ADR).
- `docs/plan/adr/` ([ADR-0055](../plan/adr/0055-zwei-auswahlen-ohne-parameter-in-einem-text-sind-erkannt.md), Index): Befunde N-2, N-3; Nummernlücke 0054 gehört zu einer ADR auf einem anderen Zweig.
- `docs/plan/spec-abdeckung.md`: Stand erzeugt, Zählung stimmig.
- `config/`, `Dockerfile`, `tools/`, `Makefile`: unberührt.

## Urteil

Kein HIGH. Ein MEDIUM neu (N-1, Plan-Tabelle und Kennung der ADR), vier LOW (N-2 bis N-5), drei INFO (N-6 bis N-8). F-2 bis F-12 sind behoben; die Nachbesserung ändert keinen Produktionscode, der Testcode ist durch gefahrene Mutationen getragen, der Merge enthält keine Auflösung in Code. Zurück an den Implementer für N-1, N-4, N-5 (Slice und Matrix), an den Architect für N-2, N-3 (ADR im Status `Proposed`), danach Verifier; ein weiterer Re-Review ist nur nötig, wenn die Nachbesserung über Dokumentation hinaus Quelltext, Testcode oder Skripte ändert. Die Entscheidung des Nutzers über [ADR-0055](../plan/adr/0055-zwei-auswahlen-ohne-parameter-in-einem-text-sind-erkannt.md) bleibt Bedingung der Closure (N-8).
