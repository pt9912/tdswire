# Review: slice-kompatibilitaetsserver-an-referenz-angleichen (A-9, A-14 nur `laufend.Id`)

* Datum: 2026-11-19 · Rolle: Reviewer (frischer Kontext) · Diff: `git diff main...HEAD` (a659f3f8, ein Commit gegen 4cd1eed6)
* Geprüft gegen: Slice-Plan (§1, §2, §3, §6), `AGENTS.md` §3.1 bis 3.8, [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md), [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität), `SPEC-035`, `SPEC-045`, `.harness/skills/reviewer.md`. Nicht gegen die DoD (Verifier).
* Sensoren: keine gefahren. Die Messsperre hielt ein anderer Lauf (`impl-mehrere-anweisungen`); der Diff ist klein genug, um die Mutationen aus dem Code zu lesen. Gelesen, nicht gefahren: (a) `onUpdate` zurück auf eine Konstante 2 macht `onUpdate und onDelete von person melden die Zahl der zutreffenden Zeilen` rot (erste Zusicherung erwartet 1); (b) `matching` ohne Filterauswertung (`personRows.size`) macht dieselbe Zusicherung rot (`delete(id(99))` erwartet 0); (c) `Id` von `laufend` wieder `nullable = false` oder `primaryKey` zurück macht `laufend beschreibt Id nullbar ohne Schluessel` rot. Gefahren sind diese Mutationen nicht (Skill: Mutationsbeschreibung nennt gedachte Wirkung), die Wirkung ist aus dem Code gelesen.

## Antworten auf die Schwerpunkte

1. **A-9, Zählung aus dem Bestand.** Die Callbacks zählen mit `RowRules.matches` über die unveränderte Liste `personRows`; `where == null` zählt alle Zeilen. Die Zahlen 1, 1, 0, 3, 2, 3, 1 des Kotlin-Tests folgen aus den Daten (Age 36, NULL, 36). Der .NET-Test `Aendern_und_Loeschen_melden_die_Zahl_der_Zeilen_auf_die_die_Bedingung_zutrifft` trennt die Anweisungen über Trefferzahlen (2, 0, 3); das Risiko 1 aus §6 (Trennung geht verloren) ist damit im Test gelöst. Dass die Referenz für die zwei Schritte 1 und 1 meldet, trägt die alte Zeile A-9 (Messung 2026-11-06), nicht ein neuer Lauf.
2. **A-14 `laufend.Id`.** `nullable = true` und `primaryKey = emptyList()` sind gelesen konsistent mit `ColumnMapping` (nullbares `Int32` ist `INTN` Länge 4, wie die entfernte Referenzseite der Regel). Ein Lauf des Mitschnitts belegt das in diesem Review nicht (F-3).
3. **Aussagen zu `kaputt.Text` gegen §3.8.** Die Aussage „`SimColumn.maxLength` erreicht das COLMETADATA einer Auswahl nicht“ trägt: `SPEC-035` („ändert die Metadaten eines Ergebnisses nicht“), `SimulationBackend.kt` baut `ResultColumn(it.name, it.type, it.nullable)` ohne Länge (zwei Stellen). Die Regel `fehler-nach-zeilen/01/colmetadata` und die Zeile A-14 sind darauf eng geschnitten. Nicht tragen die älteren Sätze des Slice (F-1).
4. **Ausnahmelisten.** Aus `live-ausnahmen.tsv` entfallen `aendern` und `loeschen`, aus `mitschnitt-ausnahmen.tsv` `cancel-streaming/01/colmetadata`. Die Regel `fehler-nach-zeilen/01/colmetadata` ist im Grund umgeschrieben und trägt `SPEC-045` und A-14. Kein Treffer auf die entfernten Regeln mehr im Repo außerhalb von `done/` und dem Slice.
5. **Kommentare (§3.7).** Die KDocs in `KompatibilitaetsServer.kt`, `SzenarienTests.cs` und `KompatibilitaetsTablesTest.kt` stehen im Indikativ und tragen Zusage oder Kopplung; keiner nennt eine verworfene Alternative. Das gilt auch für den Kommentar zu `running()`.

## Findings

### F-1 · MEDIUM · Slice §1 und §3 nennen weiter, was der Befund widerlegt
* quelle: Reviewer-Skill (Fortschreibung lässt die zweite Fundstelle stehen), `AGENTS.md` §3.8
* pfad: `docs/plan/planning/in-progress/slice-kompatibilitaetsserver-an-referenz-angleichen.md` · „`kaputt.Text` mit `maxLength = 10` beschreiben, ohne die Bibliothek zu ändern“
* befund: Der neue Absatz „Befund des Implementers“ sagt, `kaputt.Text` lasse sich ohne Bibliotheksänderung nicht angleichen. Fünf andere Stellen sagen das Gegenteil und bleiben unverändert: der Ist-Stand-Punkt „Mittel auf Seiten des Servers“, die Empfehlung („kein Verhalten der Bibliothek betroffen“, „nicht `src/main`“), der Absatz „Entscheidung des Nutzers“ („`kaputt.Text` mit `maxLength = 10` in der Fixture, ohne Bibliotheksänderung“), der Punkt „Ausdrücklich NICHT“ („keine der beiden Lücken ist ein Verhalten der Bibliothek“) und §4 („der Mitschnitt-Teil von A-14 (Option A)“). Ein Leser von §1 liest zwei Aussagen zur selben Frage.
* verifizierbar: ja (Grep auf `ohne die Bibliothek`, `kein Verhalten der Bibliothek`, `maxLength = 10`)
* klasse: Zweite Fundstelle nach Fortschreibung

### F-2 · MEDIUM · Ergebnis-Absatz des Live-Laufs mischt Messstand und aktuelle Liste
* quelle: `AGENTS.md` §3.7 (Zustandsfeld ohne Chronik), §3.8; Reviewer-Skill (Zahl an zweitem Ort ohne Sensor)
* pfad: `docs/messungen/referenzabgleich.md` · „und 13 gleich der gelisteten Abweichung (A-1 bis A-9; die Ausnahme A-9 steht nicht mehr in der Liste)“
* befund: Der Absatz beschreibt die Läufe vom 2026-11-06 („12 gleich, 13 gleich der gelisteten Abweichung“). Der Diff fügt eine Klammer über den heutigen Stand der Liste ein, die dem Messstand widerspricht: mit der Liste ohne A-9 sind es 11 gelistete Schritte, nicht 13, und kein Lauf belegt 14 gleiche Schritte. Die Zahlen 12 und 13 stehen außerdem im Slice `done/slice-referenzabgleich-live.md` und in `welle-referenzabgleich-release-results.md` (dort als Messstand zu Recht).
* verifizierbar: ja (Zählen der Regeln mit Schritt `live-ausnahmen.tsv` gegen die Zahl)
* klasse: Messstand und Ist-Zustand in einem Satz

### F-3 · MEDIUM · Plan-Tabelle nennt die neue Testdatei des Moduls `testserver` nicht
* quelle: Reviewer-Skill (Plan-Tabelle gegen Diff), Slice §3
* pfad: `testserver/test/kotlin/io/github/pt9912/tdswire/testserver/KompatibilitaetsTablesTest.kt` · „internal class KompatibilitaetsTablesTest“
* befund: Slice §3 führt `KompatibilitaetsServer.kt`, `SzenarienTests.cs`, `testschema.sql`/`LiveSzenarioTests.cs`, beide TSV und `referenzabgleich.md`. Im Diff liegt zusätzlich die neue Datei `KompatibilitaetsTablesTest.kt` (Beleg für beide Zusagen). Der Negativbefund „keine Datei darüber hinaus“ gilt erst nach dem Abgleich. Zudem fehlt in der Tabelle der Eintrag zum Slice selbst (§1-Absätze) und `testschema.sql`/`LiveSzenarioTests.cs` sind (richtig) unberührt, der Grund steht nicht im Plan.
* verifizierbar: ja (Abgleich Diff und Tabelle)
* klasse: Plan-Tabelle unvollständig gegenüber dem Diff

### F-4 · LOW · Der Test „Bestand bleibt unverändert“ kann nicht rot werden
* quelle: Maintainability, Reviewer-Skill (Eigencode, dessen Entfernung keinen Test rot macht)
* pfad: `testserver/test/kotlin/io/github/pt9912/tdswire/testserver/KompatibilitaetsTablesTest.kt` · „assertEquals(ALL_ROWS, drain(person))“
* befund: `personRows` ist eine unveränderliche Liste, `rows` liefert bei jedem Aufruf eine frische Quelle über sie; kein Callback kann den Bestand ändern. Die letzte Zusicherung bleibt unter jeder Mutation der Callbacks grün und trägt die Aussage des KDoc („der Bestand bleibt unverändert“) nur durch die Bauart der Liste. Die Zählung selbst ist durch die übrigen Zusicherungen getragen.
* verifizierbar: ja (Mutation: Callback entfernt eine Zeile aus einer Kopie; Test bleibt grün)
* klasse: Zusicherung ohne Mutationswirkung

### F-5 · LOW · Zahlen der Zeilen von `person` stehen an einem dritten Ort ohne Kopplungskommentar im Test
* quelle: `AGENTS.md` §3.7 (Klasse Kopplung), Reviewer-Skill (Zahl an zweitem Ort)
* pfad: `testserver/test/kotlin/io/github/pt9912/tdswire/testserver/KompatibilitaetsTablesTest.kt` · „private const val AGE = 36“
* befund: Alter 36 und drei Zeilen stehen in `KompatibilitaetsServer.kt` (`ADA_AGE`, privat), in `testschema.sql`, in `SzenarienTests.cs` (Kommentar „Kopplung“) und jetzt als `AGE`, `ALL_ROWS`, `AGE_ROWS` im Kotlin-Test. Der Kotlin-Test nennt die Kopplung nicht. Ändert jemand `ADA_AGE`, wird der Test rot (der Sensor), aber seine Ursache steht nicht im Test.
* verifizierbar: nein
* klasse: Kopplung ohne Kommentar auf einer Seite

### F-6 · INFO · Entfernte Regeln und `laufend.Id` nullbar sind in diesem Review nicht gegen den Draht belegt
* quelle: Slice §2 DoD 3 (Verifier)
* pfad: `referenztest-dotnet/Vergleich/mitschnitt-ausnahmen.tsv` · (entfernte Zeile `cancel-streaming/01/colmetadata`) und `live-ausnahmen.tsv` (entfernte Zeilen `aendern`, `loeschen`)
* befund: Dass `LAUF=mitschnitt` ohne die Regel grün ist und `LAUF=live-2022`/`live-2025` ohne `aendern` und `loeschen`, zeigt nur der Lauf. Slice §7 ist leer; DoD 3 verlangt „gefahren“ oder „nicht gefahren“ mit Grund. Zuständig: Verifier.
* verifizierbar: ja (Verifier)
* klasse: Belegt nur im Folgeschritt

### F-7 · INFO · Die neue Testklasse trägt kein `@spec`
* quelle: Maintainability, `harness/README.md` §Minimal agent workflow (`make spec-abdeckung` bei Tests)
* pfad: `testserver/test/kotlin/io/github/pt9912/tdswire/testserver/KompatibilitaetsTablesTest.kt` · „Die Tabellen des Kompatibilitätslaufs ohne Server“
* befund: Andere Testklassen des Moduls (`ConfigLoaderTest`, `SimDescriptionReaderTest`) tragen `@spec`-Tags, die neue nicht; `docs/plan/spec-abdeckung.md` zählt sie nicht. Ob ein Tag (`SPEC-035`, `SPEC-045`) gewollt ist, entscheidet der Planner.
* verifizierbar: ja (`make spec-abdeckung`)
* klasse: Test ohne Spec-Tag

### F-8 · INFO · Die Zeile A-14 nennt die Wirkung von `SimColumn.maxLength` nicht vollständig
* quelle: `AGENTS.md` §3.8
* pfad: `docs/messungen/referenzabgleich.md` · „wirkt auf Katalogantworten und `MERGE`, nicht auf das COLMETADATA einer Auswahl“
* befund: Die negative Aussage (nicht auf das COLMETADATA einer Auswahl) trägt `SPEC-035` und `SimulationBackend.kt`. Die positive Liste ist enger als der Code: `BulkCopyForms.kt` (Probe des Massenladens) liest `maxLength` ebenfalls; `SPEC-035` nennt dort nur „Geprüft wird sie nur beim `MERGE`“. Kein Einfluss auf die Entscheidung zu A-14.
* verifizierbar: ja (Grep `maxLength` in `src/main/.../simulation`)
* klasse: Aufzählung enger als Beleg

### F-9 · INFO · Der Test „im_Batch“ trennt die drei Anweisungen nicht mehr
* quelle: Maintainability
* pfad: `referenztest-dotnet/Kompatibilitaet/SzenarienTests.cs` · „Einfuegen_Aendern_und_Loeschen_im_Batch_melden_die_Zeilenzahlen_der_Anweisungen“
* befund: Die drei Erwartungen sind jetzt alle 1; der Test belegt die Zählung je Anweisung nicht mehr, der Name sagt „im Batch“, obwohl drei getrennte Befehle laufen (schon vorher). Die Trennung leistet der neue Test; kein Befund, nur Hinweis für den Planner bei der Closure.
* verifizierbar: nein
* klasse: Test verliert Trennschärfe, ersetzt durch Schwestertest

## Negativbefund (geprüft, ohne Befund)

* `testserver/kotlin/.../KompatibilitaetsServer.kt` · geprüft, ohne Befund außer F-5 an der Gegenseite: kein `@Suppress`, keine Fremdbibliothek, Import `RowFilter` und `RowRules` nur aus der öffentlichen Fläche des Moduls.
* `src/main` · nicht berührt (keine Findings zu `SPEC-059`, `make flaeche` nicht betroffen).
* `referenztest-dotnet/Vergleich/testschema.sql` und `LiveSzenarioTests.cs` · nicht berührt, Kopplungskommentar im Kopf der Datei bleibt zutreffend.
* `spec/` · nicht berührt; `SPEC-035` und `SPEC-045` widersprechen dem Diff nicht (Grep `UPDATED_ROWS`, `DELETED_ROWS`, „feste Zahl“ ohne Treffer außerhalb `done/`).
* `docs/user/` · nicht berührt; Grep auf `Zeilenzahl`, `Callback` ohne Treffer, der den Fixture-Zahlen widerspricht.
* Beobachtungs-Register: `callback-zeilenzahl-ist-fixture-konstante` und `live-lauf-ausserhalb-der-gates-haelt-kopplungen-nicht` sind nicht fortgeschrieben; die DoD lässt das bis zur Closure offen, kein Finding.
* DoD-Haken im Diff: keiner gesetzt, kein Befund zu „Haken vor der Verifikation“.
