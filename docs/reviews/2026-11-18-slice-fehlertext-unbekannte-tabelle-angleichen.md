# Review-Report: slice-fehlertext-unbekannte-tabelle-angleichen — 2026-11-18

**Review-Art:** Code — gegen Plan, [ADR-0007](../plan/adr/0007-fehler-und-attention.md), [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md) und die Hard Rules (`AGENTS.md` §3.1 bis §3.8).

**Gegenstand:** Diff `main...HEAD` des Zweigs `slice-fehlertext-unbekannte-tabelle-angleichen`, Commit f65df36c, 23 Dateien.

**Skill:** `.harness/skills/reviewer.md` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-18

**Eingangs-Kontext:** Slice `slice-fehlertext-unbekannte-tabelle-angleichen` (§1, DoD, §3), [ADR-0007](../plan/adr/0007-fehler-und-attention.md), [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md), `AGENTS.md` §3, Spezifikation (Abschnitt 4, `SPEC-002`, `SPEC-038`, `SPEC-059`), Messprotokoll `docs/messungen/referenzabgleich.md` (M-37, A-3), Diff.

**Ausgeführte Sensoren:** `make test` (Exit 0, Ergebnis aus dem Layer-Cache, 2 s; der Stand der Quellen war schon gebaut; keine roten Tests). Nicht gefahren, weil nicht Teil der Reviewer-Pflicht für `src/`: `make clean-build`, `make e2e`, Messläufe (`make vergleichslauf-d`). Die Mutationen unten sind im Kopf an Code und Testdaten geprüft, nicht gefahren.

## Findings

### F-1 — MEDIUM
- `kategorie`: MEDIUM
- `quelle`: `AGENTS.md` §3.8 (Aussage trägt höchstens so weit wie ihr Beleg)
- `pfad`: `docs/user/referenz-konfiguration.md` · „der Name ist auf höchstens 128 Zeichen je Teil und Zeichen ohne Steuerzeichen außer dem Zeilenumbruch begrenzt“ (zugleich `src/main/kotlin/io/github/pt9912/tdswire/domain/QueryEvent.kt` · „höchstens 515 Zeichen lang ist und kein Steuerzeichen“, `spec/spezifikation.md` · „Name der unbekannten Tabelle (Nutzerentscheidung vom 2026-11-18“, `docs/user/einbettung-und-betrieb.md` · „höchstens 515 Zeichen lang ist“)
- `befund`: Die Grenzen 515 Zeichen und „Steuerzeichen außer Zeilenumbruch“ sind nach dem Messprotokoll nicht gemessen (M-37 nennt Steuerzeichen und vier Teile ausdrücklich als nicht gemessen). Nur die Konstante `MAX_OBJECT_NAME` trägt das Wort „Setzung“; KDoc von `Failure`, Spezifikation, beide Nutzer-Doku-Seiten und die Zeile der Interpretation in M-37 nennen sie ohne diese Kennzeichnung. Die Nutzer-Doku nennt dazu zwei verschiedene Grenzen: `referenz-konfiguration.md` „128 je Teil“ (gilt nur für das Simulations-Backend), `einbettung-und-betrieb.md` und `QueryEvent` „515“ (gilt für jedes Backend); ein eigenes Backend mit einem 300 Zeichen langen Teil sieht die erste Aussage widerlegt.
- `verifizierbar`: nein (Doku)
- `klasse`: Setzung als Messung lesbar / Grenze an zwei Stellen mit verschiedenem Wert

### F-2 — MEDIUM
- `kategorie`: MEDIUM
- `quelle`: Reviewer-Skill „Fortschreibung lässt die zweite Fundstelle stehen“; `AGENTS.md` §3.7
- `pfad`: `spec/spezifikation.md` · „Text fest und englisch (bei `TDS_SIM_UNKNOWN_PREPARED_HANDLE` endet er mit der Nummer des Handles)“ und `src/main/kotlin/io/github/pt9912/tdswire/protocol/error/ErrorMapper.kt` · „Der Text jeder Antwort ist der feste Text der Regel“
- `befund`: Die Kernaussage „der Text ist fest“ ist nach dem Diff für `TDS_SIM_UNKNOWN_OBJECT` falsch. Der Spezifikationssatz (Abschnitt 4, vor der Tabelle der Codes) nennt nur das Handle als Ausnahme und steht außerhalb der geänderten Zeilen; der KDoc der Klasse `ErrorMapper` sagt im selben Absatz „fester Text“ und danach „die eine Eingabe im Text ist der Name“, der erste Satz widerspricht dem zweiten. Beide stehen nicht in der Änderungsliste.
- `verifizierbar`: ja (Grep „fest“ in Abschnitt 4 und im KDoc)
- `klasse`: Fortschreibung lässt zweite Fundstelle stehen

### F-3 — MEDIUM
- `kategorie`: MEDIUM
- `quelle`: Reviewer-Skill „Messzahl aus einem Erzeuger / Zuordnung an zweitem Ort“; `AGENTS.md` §3.8; Maintainability
- `pfad`: `docs/messungen/referenzabgleich.md` · „| M-37 | Text des ERROR 208 bei einer unbekannten Tabelle“ gegen `docs/messungen/zeilenform-nbcrow-2026-11-17.md` · „| M-37 | Zeile ohne, mit einer und mit nur NULL, Batch“
- `befund`: Die Kennung M-37 ist doppelt vergeben: auf `main` führt das Messprotokoll der Zeilenform M-37 bis M-41, und `spec/spezifikation.md` (Zeilenform, Änderungsprotokoll 2026-11-17) und `docs/messungen/unterschiede-2022-2025-bestand-2026-11-17.md` zitieren „M-37 bis M-41“ ohne Dateinamen. Das neue M-37 in `referenzabgleich.md`, Spezifikation, Matrix, Slice und Register meint eine andere Messung; ein Leser löst „M-37“ je nach Fundstelle auf die falsche Datei auf.
- `verifizierbar`: ja (Grep `^| M-37`)
- `klasse`: Messzeilen-Kennung doppelt vergeben

### F-4 — MEDIUM
- `kategorie`: MEDIUM
- `quelle`: Reviewer-Skill „Plan-Tabelle nennt die berührten Dateien nicht“
- `pfad`: `docs/plan/planning/done/slice-fehlertext-unbekannte-tabelle-angleichen.md` · „Der Implementer-Agent erweitert die Liste in seinem ersten Lauf“
- `befund`: §3 ist im Diff unverändert. Nicht in der Tabelle stehen `QueryEvent.kt`, `SimulationBackend.kt`, `ErrorMapperObjectNameTest.kt`, `StatementPlannerTest.kt`, `SimulationBackendSelectTest.kt`, `MergeFehlerEmbeddingTest.kt`, `MessungBulkTests.cs`, `MessungBulkBelegTests.cs`, die beiden `.messung`-Dateien, `kompatibilitaetsmatrix.md`, `docs/user/einbettung-und-betrieb.md`, `referenz-konfiguration.md` und das Register. Die genannten `SimulationBackendFixedTest.kt`, `SimulationBackendChangeTest.kt`, `SimulationBackendBulkTest.kt`, `BulkCopyFormsTest.kt` sind im Diff nicht berührt (der Plan nennt sie als „update“). Der Kopf „Berührte Spec-Stellen“ nennt `SPEC-059` und `SPEC-002` nicht, die der Diff berührt.
- `verifizierbar`: ja (Abgleich Tabelle gegen `git diff --stat`)
- `klasse`: Plan-Tabelle gegen Diff nicht abgeglichen

### F-5 — LOW
- `kategorie`: LOW
- `quelle`: Reviewer-Skill „Mitglieder eines Typs der Liste in `SPEC-059` ohne Eintrag“; `SPEC-059`
- `pfad`: `spec/spezifikation.md` · „Feld `Failure.objectName` erweitert (Voreinstellung `null`“ (Änderungsprotokoll 2026-11-18) gegen die Aufzählung der Mitglieder in `SPEC-059` („`ResultColumn` mit dem Feld `maxLength`“)
- `befund`: Das Änderungsprotokoll nennt das Feld (Pflicht erfüllt). Die Aufzählung der Typen in `SPEC-059` nennt `Failure` ohne das neue Feld, anders als `ResultColumn.maxLength` oder `ResultStart.kind`; der Eintrag sagt nicht, ob `y` steigt (frühere Einträge sagen es). Für Kotlin-Aufrufer ändern sich `copy` und `component4` der `data class` (binär nicht abwärtskompatibel); Java behält dank `@JvmOverloads` den Konstruktor mit zwei und drei Argumenten. Kein Einbettungstest setzt `objectName` von außen (`MergeFehlerEmbeddingTest` prüft den Weg über das Simulations-Backend, nicht ein `Custom`-Backend mit `Failure(…, objectName = …)`).
- `verifizierbar`: nein (kein Sensor für Mitglieder, `make flaeche` hält nur Typnamen)
- `klasse`: öffentliches Feld ohne Eintrag in der Mitgliederliste / ohne Beleg von außen

### F-6 — LOW
- `kategorie`: LOW
- `quelle`: `AGENTS.md` §3.7, Plan DoD („die Regeln … entfallen — oder Art *so gewollt*“)
- `pfad`: `referenztest-dotnet/Vergleich/mitschnitt-ausnahmen.tsv` · „fehler-vor-zeilen/01/fehler“ und `docs/plan/planning/done/slice-fehlertext-unbekannte-tabelle-angleichen.md` · DoD „entfallen“
- `befund`: Die Mitschnitt-Regel bleibt mit Servername leer und Zeile 0 und ändert ihren Inhalt (A-3 neu betitelt, Art `Luecke`); das ist sachlich richtig (der Unterschied besteht, M-37 misst Servername vorhanden und Zeile 1), weicht aber vom DoD-Wortlaut ab, und weder DoD noch §7 vermerken die Abweichung. Die Zeile der neuen Regel und das Entfallen der Live-Regel ruhen auf Ableitung aus dem Code; im Messprotokoll steht kein Lauf von `make vergleichslauf-d` (Live, `mitschnitt-2022/2025`) nach der Angleichung (die alte A-3-Zeile sagte es selbst).
- `verifizierbar`: ja (Verifier, `make vergleichslauf-d`, ohne Docker-Image-Änderung)
- `klasse`: DoD-Abweichung nicht vermerkt / Ausnahmeliste ohne Lauf nach Änderung

### F-7 — INFO
- `kategorie`: INFO
- `quelle`: Schwerpunkt (2), Maintainability
- `pfad`: `src/main/kotlin/io/github/pt9912/tdswire/protocol/error/ErrorMapper.kt` · „name.none { it.isISOControl() && it != '\n' }“
- `befund`: `isISOControl` lässt Unicode-Formatzeichen (U+2028, U+2029, Bidi-Steuerzeichen, U+FEFF, U+0085 ist erfasst) durch; sie stehen im ERROR-Text. Wirkung nur bei einem Client, der den Text roh protokolliert oder anzeigt; die Grenze ist eine Setzung und im Slice nicht als „nicht gemessen“ benannt. Zuständig: Planner (Entscheidung, ob die Setzung gilt).
- `verifizierbar`: nein
- `klasse`: Zeichenmenge der Setzung enger gedacht als gebaut

### F-8 — INFO
- `kategorie`: INFO
- `quelle`: Schwerpunkt (2), (4)
- `pfad`: `src/test/kotlin/io/github/pt9912/tdswire/protocol/error/ErrorMapperObjectNameTest.kt`
- `befund`: Weg einzeln und Sequenzierer sind je mit denselben Namen getestet (beide Tests prüfen beide Wege); eine Antwort auf einen RPC, einen späten Fehler nach Zeilen und eine Mehrfach-RPC-Nachricht tragen den Namen nur über denselben Pfad (`sequencerMapping().backend`), nicht per Test auf dem Draht (gemessen ist M-37 für `sp_executesql` am Referenzclient, nicht für diese Bibliothek im RPC). Der Verifier kann es mit `make e2e` Stufe 3 belegen; kein Mangel im Code gefunden.
- `verifizierbar`: ja (Verifier)
- `klasse`: Weg ohne eigenen Drahttest

## Antworten auf die Schwerpunkte

1. **Regeln gegen M-37.** Text, `<Schema>.<Name>` nur bei genanntem Schema (kein `dbo.`), Sonderzeichen unverändert, 128 Zeichen ok, 129 ohne 208-Text (`objectName` null, fester Text; echter Server 103 Class 15, als „nicht abgebildet“ benannt): im Code und in der Messzeile gedeckt. 515 und „Steuerzeichen“ sind Setzungen; im Code (`MAX_OBJECT_NAME`) gekennzeichnet, in KDoc, Spezifikation und Nutzer-Doku nicht (F-1). Die Planerseite begrenzt je Teil auf 128, der Mapper insgesamt auf 515 (vier Teile des Servers, im Simulations-Backend nie erreicht); die zweite Grenze liegt nur für `Custom` offen.
2. **Robustheit.** Der Name erreicht den ERROR nur über `nameOf` (Länge, nicht leer, Steuerzeichen, Regel `namesObject`); das Log bekommt ihn nicht (`record` trägt nur Code und Marke); Länge 515 liegt weit unter der Grenze des ERROR-Textes (US_VARCHAR). Einzelner Weg (`failure`) und Sequenzierer (`sequencerMapping().backend`) rufen dieselbe Funktion, beide sind getestet. Ein Code ohne `namesObject` wirft bei einem Namen nicht, sondern verwirft ihn (`nameOf` gibt `null`), und `message()` verlangt zusätzlich die Regel (`require`): doppelt gesichert. Unicode-Format-Zeichen: F-7.
3. **Öffentliche Fläche.** Feld mit Voreinstellung `null`, `@JvmOverloads` hält Java-Konstruktoren; `make flaeche` hält nur Typnamen und bleibt unberührt (Änderungsprotokoll trägt das Mitglied); Rest siehe F-5. `ErrorRule.namesObject` ist `internal`.
4. **Mitschnitt-Regel und M-35.** Die Abgrenzung ist richtig und an drei Stellen benannt (Spezifikation, Messprotokoll, Matrix): `INSERT BULK` und die Probe senden weiter „Unknown object.“, weil `BulkTargetResult.Rejected` kein `objectName` trägt; M-35 nennt dort den Namen des echten Servers. Abweichung vom DoD-Wortlaut: F-6.
5. **Tests und Mutationen.** Gedanklich gefangen: Name entfernen, Hochkomma verdoppeln oder entfernen, Standardschema `dbo` voranstellen, Grenze bei 514/516, Steuerzeichen durchlassen (Planner- und Mapper-Test getrennt), `namesObject`-Prüfung entfernen (Test mit `TDS_BACKEND_ERROR` und Namen `geheim` wirft), Sequenzierer-Weg ohne Namen, Planner reicht für `INSERT`/`UPDATE`/`DELETE`/`MERGE` den Namen nicht weiter. Der Test des Backends (`SimulationBackendSelectTest`) prüft `dbo.Nothing` und `null` bei der Spalte. Die Behauptung der Test-KDocs („Wächter: …“) sind keine gefahrenen Mutationen; sie stehen als Wächterliste, nicht als gemessene Wirkung (Skill: Mutation beschreibt gedachte Wirkung): vom Verifier einmal zu fahren. Ablage: 2 neue `.messung`-Dateien, 24 je Instanz im Baum, 23 Altablagen unberührt (`git ls-tree`: 48 Dateien).

## Geprüft, ohne Befund

- `src/main/kotlin/.../protocol/error/` (Logik von `nameOf`, `message`, `response`, Weg des Sequenzierers): ohne Befund außer F-2, F-7.
- `src/main/kotlin/.../simulation/` (`StatementPlanner.shownName`, `SimulationBackend.failed`): ohne Befund.
- `src/main/kotlin/.../domain/QueryEvent.kt`: Feld abwärtskompatibel für Konstruktoren, Befund nur F-1, F-5.
- `src/test/` und `src/integrator/`: Tests tragen die Zusagen des Codes; Lücken F-5, F-8.
- `referenztest-dotnet/Vergleich/` (Szenario `fehler-tabelle-unbekannt`: 26 Anweisungen plus RPC plus Folgeabfrage = 28 Schritte, 27 Fälle, damit stimmt „24 mit 208, 3 mit 103“): ohne Befund.
- `docs/plan/kompatibilitaetsmatrix.md`, Register `BEO-TDS/unbekannte-tabelle-class-20-statt-16-am-server` (Stand nennt Zustand und Beleg, keine Chronik): ohne Befund.
- Hard Rules §3.1 (Docker-only), §3.2 (kein `@Suppress`), §3.5 (keine Accepted-ADR geändert), §3.6 (kein Gate gelockert): ohne Befund.

## Urteil

Kein HIGH. Vier MEDIUM (F-1 bis F-4), zwei LOW, zwei INFO. Der Code trägt die Zusagen der Messung M-37; die Befunde liegen bei der Kennzeichnung der Setzungen, der doppelten Kennung M-37, der nicht nachgezogenen Aussage „Text fest“ und der Plan-Tabelle. Zurück an den Implementer (F-1 bis F-4, F-6), danach Verifier; ein Re-Review ist nur nötig, wenn die Nachbesserung Code ändert.
