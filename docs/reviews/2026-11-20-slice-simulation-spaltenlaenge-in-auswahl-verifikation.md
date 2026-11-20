# Verifikationsbericht: slice-simulation-spaltenlaenge-in-auswahl — 2026-11-20

**Rolle:** Verifier (Modul 11), frischer Kontext; DoD- und Entscheidungs-Konformität, Plan-vs-Code-Diff. Kein Review, kein Fix, keine DoD-Haken, kein Schließen, keine Änderung an `roadmap.md` oder am Lifecycle.

**Stand:** Branch `slice-simulation-spaltenlaenge-in-auswahl`, Spitze `25056715` (Code-Stand `2a155040`; Arbeitsbaum sauber vor diesem Bericht; `main` ist Vorfahr). Reviews: [Review](2026-11-19-slice-simulation-spaltenlaenge-in-auswahl.md), [Re-Review](2026-11-19-slice-simulation-spaltenlaenge-in-auswahl-re-review.md), [Re-Review 2](2026-11-19-slice-simulation-spaltenlaenge-in-auswahl-re-review-2.md).

**Gegenstand:** [slice-simulation-spaltenlaenge-in-auswahl](../plan/planning/in-progress/slice-simulation-spaltenlaenge-in-auswahl.md) (Rest von A-14, `kaputt.Text`; Nutzerentscheidung 2026-11-19: Code angleichen). Bezug: [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-05](../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen), [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0056](../plan/adr/0056-laenge-einer-textspalte-erreicht-das-colmetadata-einer-auswahl.md) (`Accepted`, Option C), [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md) Entscheidung 7 (teilweise ersetzt), [SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends), [SPEC-003](../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog), [SPEC-045](../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig).

## Selbst gefahrene Sensoren (End-Stand `25056715`, unter der Messsperre, Docker)

| Sensor | Ergebnis | Dauer |
|---|---|---|
| `make clean-build` | Exit 0: Jar, Tests mit `--no-cache` (1526 Zeilen PASSED, null FAILED, null SKIPPED; Stages `test`, `integratorTest`, `:testserver:test`), detekt „BUILD SUCCESSFUL in 3m 52s“ (Suppression-, Jackson-, Modulgrenze-Sperre „OK“). Die neuen Klassen `SimulationBackendSelectLengthTest` (5), `SpaltenlaengeAuswahlWireTest` (5), `KompatibilitaetsTablesTest > kaputt beschreibt Text …` stehen PASSED | 672 s |
| `make e2e` | Exit 0, „gruen (9 Stufen, 464 s)“; Stufen 1 bis 9: 70, 90, 98, 58, 15, 15, 36, 13, 69 s (Stufe 3 = `simulations-integration`, Stufe 9 = Lasttest, der `ref_zeilen` mit `maxLength` 12, 40, 100 liest) | 464 s |
| `make kompatibilitaet` | Exit 0, „Alle Tests der Kategorie Szenario bestanden“ (Standardkategorie; die Kategorien Anmeldung, Lücken, Synchronität laufen in den Stufen 5 bis 7 von `make e2e`, grün) | 14 s |
| `make simulations-integration` | Exit 0, „Alle 13 Tests des Referenzclients bestanden“, Server läuft nach den Tests, Log ohne Handlerfehler | 19 s |
| `make vergleichslauf-d LAUF=mitschnitt` | Exit 0, „gruen“, `mitschnitt-2022` und `mitschnitt-2025`: `fehler-nach-zeilen/01/colmetadata` in beiden Teilläufen „gleich“ ohne Regel; Referenz und Server melden je `Text:nvarchar(laenge=20,…)`; gesamt 605 Felder „gleich“, 43 als Ausnahme (zusammen 648 = 2 × 324, wie im Messprotokoll) | 29 s |
| `make vergleichslauf-d LAUF=live` | Exit 0, `live-2022` und `live-2025`, je 25 Schritte „gruen“; keine Regel zu `kaputt` in `live-ausnahmen.tsv` (siehe V-2 zur Reichweite) | 84 s |
| `make gates` | Exit 0; Gruppen `werkzeug` und `tests` liefen („kein Beleg … an einem Vorfahren“), `stempel` übersprungen (Beleg vorhanden, Eingänge nicht berührt); `d-check: 1699 Datei(en) geprüft, 0 Befund(e)`; `flaeche`, `kopplung-zahlen`, `gate-zuordnung` (33 Fälle), `stempel-eingaenge` grün; 97 Docker-Schichten aus dem Cache (der Frischbeleg der Tests ist `clean-build`); Stempel sind jetzt als Notes auf dem Stand geschrieben | 20 s |
| Mutationen (5, Wegwerf-Kopien, Skript im Scratchpad) | alle rot, siehe Abschnitt „Mutationen“ | 41 bis 74 s je Lauf |
| Probe außerhalb der Tests (Wegwerf-Kopie, 12 Texte am Rohbytes-Client) | siehe Abschnitt „Probe“ | 54 s |

Gesamt rund 27 Minuten Messzeit (Sensoren 1302 s, Mutationen 291 s, Probe 54 s); die Messsperre war bei jedem Lauf sofort frei (Wartezeit 0 s), `available` 15,3 bis 17,2 GB vor jedem Lauf (Skript gegen 4000 MB). Lastmittel 81 (Beginn, andere Aufträge; fiel binnen Minuten) bis 5. Größte Posten: `make clean-build` (672 s, davon detekt 233 s), `make e2e` (464 s), `live` (84 s), Gradle-Läufe der Mutationen. Keine künstliche Last. `make docs-check` Exit 0, 0 Befunde auf dem Stand mit diesem Bericht (Zeile am Ende). Nicht gefahren: `make spec-abdeckung`, `make test` einzeln (Zuständigkeit Implementer; `clean-build` fährt dieselben Tests frisch).

**Betriebsvorfall (INFO, V-6):** Die von mir bei `clean-build` angelegte Sperre `messlock` war nach rund sechs Minuten nicht mehr vorhanden (der Lauf lief weiter; eine eigene Mutation legte sie neu an, Wartezeit 0 s). Ich habe sie nicht entfernt (mein Skript räumt nur im `trap` am Ende des Laufs). Wer sie entfernte, ist nicht festzustellen; ein anderer Auftrag kann in dieser Zeit ungesperrt gelaufen sein. `clean-build` und `e2e` zeigen keine Last-Flakes.

## Plan-vs-Code-Diff (`git diff main...HEAD`, 25 Dateien)

| Plan §3 | Diff | Urteil |
|---|---|---|
| `SelectExecution.kt` update (beide `ResultColumn(...)`) | beide Stellen rufen `resultStart(part)`, das `it.toResultColumn()` nutzt (Zweig „Bedingung nie wahr“ und `readSource`) | gleich |
| `SimModel.kt` update (KDoc) | KDoc `SimColumn.maxLength` neu; dazu `reportedLength` (-1 ergibt 4000) und `toResultColumn()` als `internal` | gleich (die zwei Hilfen sind die „eine Abbildung“ des Plans) |
| `ColumnMapping.kt` prüfen | unberührt, KDoc („ohne Angabe `NVARCHAR(4000)`“) bleibt wahr | gleich |
| `BulkCopyForms.kt` update (`reportedLength`) | `column()` nutzt `column.reportedLength`; keine zweite Kopie von „-1 gleich 4000“ im `main` | gleich |
| Spec (SPEC-035, SPEC-003, Datei-Abbildung, Änderungslog) | alle vier Stellen im Diff, Setzungen gekennzeichnet | gleich |
| `KompatibilitaetsServer.kt`, `mitschnitt-ausnahmen.tsv`, `referenzabgleich.md`, `testschema.sql` | `kaputt.Text` `maxLength = BROKEN_TEXT_CHARS` (10); genau die eine Regel `fehler-nach-zeilen/01/colmetadata` entfernt (1 Zeile); A-14 aus der Tabelle genommen, Abschnitt „Stand der Ausnahme A-14“ ergänzt; Kommentar in `testschema.sql` | gleich |
| neue Tests `SimulationBackendSelectLengthTest`, `SpaltenlaengeAuswahlWireTest`; `KompatibilitaetsTablesTest`, `TableStoreTest` update | im Diff, je Zusage ein Fall (Länge S1, S2, nie wahr, Vorgabe, -1, Grenzen 1 und 4000, Text genau und über der Länge, `sp_executesql`) | gleich |
| `RowRules.kt`, `TableStore.kt` update (Nachbesserung zum Review) | Länge im Bestand (Massenladen, `UPDATE`), -1 gilt als 4000; Kopplungskommentar | gleich |
| `docs/user/` (2), Mutationsbeleg, `spec-abdeckung.md`, ADR-Index | im Diff; Zähler von `spec-abdeckung.md` (SPEC-003 +8, SPEC-035 +14, SPEC-036 +2, SPEC-045 +1) stimmen zu den `@spec`-Tags der neuen Fälle (von mir gezählt, `make spec-abdeckung` nicht gefahren) | gleich |
| **nicht in §3** | `SimulationMerges.kt` (nur KDoc: Kopplungsvermerk an `truncates`) | **Abweichung, V-1** |
| nicht in §3 (Auftrag, kein Befund) | die drei Review-Reports, [ADR-0056](../plan/adr/0056-laenge-einer-textspalte-erreicht-das-colmetadata-einer-auswahl.md) (Eingang), die Slice-Datei selbst | kein Befund |

Kein Verstoß gegen Hard Rules im Diff: `@Suppress`-Grep leer (detekt-Sperre „OK“), keine Datei unter `docs/plan/adr/` nach `Accepted` geändert (`git log -- docs/plan/adr/`: [ADR-0056](../plan/adr/0056-laenge-einer-textspalte-erreicht-das-colmetadata-einer-auswahl.md) zuletzt im Accepted-Commit, [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md) unberührt, nur sein Vermerk im Index), keine öffentliche Fläche neu (`SimColumn`, `ResultColumn` unverändert; `reportedLength` und `toResultColumn` sind `internal`; `make flaeche` grün), Docker-only eingehalten.

## DoD Punkt für Punkt (Haken im Slice sind leer, das ist Sache des Planners)

| DoD-Punkt | Beleg | Urteil |
|---|---|---|
| **Bibliothek:** beide Stellen reichen `maxLength` durch, -1 ergibt 4000, Zeile über der Länge endet im Fehler; KDoc, SPEC-035, SPEC-003, Änderungslog sagen dasselbe; Tests je Zusage | Code gelesen und Tests PASSED (`clean-build`); Mutationen rot (unten); die Fundstellen sind einheitlich (Abschnitt „Einheitlichkeit“) | erfüllt |
| **Fixture und Ausnahmeliste:** `kaputt.Text` `maxLength = 10`, Regel entfällt, A-14 im Messprotokoll geschlossen | Fixture und Regel im Diff; A-14 steht nicht mehr in der Ausnahmetabelle, nur im Abschnitt „Stand der Ausnahme A-14“; Mitschnitt-Lauf (unten) bestätigt | erfüllt |
| **Nachweis:** `LAUF=mitschnitt` grün gegen die verkleinerte Liste; `make kompatibilitaet` auf dem End-Stand | beide von mir gefahren, grün; die Zahlen des Messprotokolls (605 gleich, 43 Ausnahme, 648 gesamt) reproduziert | erfüllt |
| `make gates` Exit 0, engste Testklasse, Mutation je Zusage, `make test`, `make referenztest-dotnet`, `docs-check`, `flaeche`, `kopplung-zahlen`, `spec-abdeckung`; Verifier: `clean-build`, `e2e` | `gates` Exit 0 (von mir); `clean-build` und `e2e` grün (von mir); Mutationsbeleg Mut-1 bis Mut-21, fünf davon von mir wiederholt; `spec-abdeckung.md` im Diff, Zähler plausibel; `test`, `referenztest-dotnet`, `docs-check`, `flaeche`, `kopplung-zahlen` liefen in `gates` und `clean-build` | erfüllt (`spec-abdeckung` nicht von mir gefahren, Zuständigkeit Implementer) |
| Review durchgeführt, kein Self-Review | drei Reports vorhanden; Findings R-1 bis R-7 und N-1 bis N-3: kein HIGH und kein MEDIUM offen (Re-Review 2: Urteil „Verifier ja“) | erfüllt |
| Doku-Update öffentlicher Vertrag | SPEC-035, SPEC-003, Änderungslog, Messprotokoll, beide Nutzer-Dokus im Diff, gegen den Code gelesen | erfüllt, Rest V-3 |
| Closure-Notiz mit Steering-Loop-Lerneintrag | §7 „Wird bei der Closure gefüllt“ | offen (Planner-Closure) |
| Beobachtungs-Register fortgeschrieben (`BEO-TDS/live-lauf-ausserhalb-der-gates-haelt-kopplungen-nicht`, A-14 Rest) | `docs/plan/planning/observations/` nicht im Diff | offen (Planner-Closure) |
| Jedes Risiko aus §6 trägt einen Ausgang | alle „offen bis zur Closure“ (Vorschläge: V-4) | offen (Planner-Closure) |
| Die drei Paarungen (Anker · Folge-Slice · Register) | Closure-Schritt; „weiter offen“ in §6 (Durchsetzung der Länge beim `INSERT`/`UPDATE`/Massenladen) braucht einen Register-Zeiger (N-1 des Re-Reviews 2) | offen (Planner-Closure) |

## Einheitlichkeit der Aussagen (Grep auf `main`-Stand der Dateien, ohne `docs/reviews/`)

- „Metadaten eines Ergebnisses“ / „ändert die Metadaten“ / „Metadatum eines Ergebnisses“: keine Fundstelle mit der alten Aussage; verbleibend die Zitate im Slice (Ist-Stand, §3-Zeile) und in [ADR-0056](../plan/adr/0056-laenge-einer-textspalte-erreicht-das-colmetadata-einer-auswahl.md) (Folgepflicht) sowie `SPEC-035` Zeile 1128 mit der **neuen** Aussage („das Metadatum eines Ergebnisses ist `NVARCHAR(<max_length>)`“).
- „`NVARCHAR(4000)`“ in `spec/spezifikation.md`: SPEC-003 (Zeile 501, „ohne Angabe“, mit Verweis auf SPEC-035), SPEC-035 (Zeile 976, „ohne Angabe der Beschreibung“) und ein Änderungslog von 2026-10-30; in `src/main` die KDoc von `ColumnMapping` („ohne Angabe“, wahr), `CatalogForms` (Spalten der Katalogantworten, anderer Gegenstand) und `StructureParts`. [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md) Entscheidung 7 und Ausnahmeliste tragen den Satz unverändert (immutable); der Index nennt „teilweise ersetzt durch [ADR-0056](../plan/adr/0056-laenge-einer-textspalte-erreicht-das-colmetadata-einer-auswahl.md)“ mit den wörtlichen Teilsätzen. [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) und [ADR-0041](../plan/adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) sprechen über andere Gegenstände (Massenladen, Katalogspalten). Keine zweite Fundstelle der alten Aussage.
- A-14: nur noch im Abschnitt des Messprotokolls, im Änderungslog, in den Kommentaren der Tests und in Slice/[ADR-0056](../plan/adr/0056-laenge-einer-textspalte-erreicht-das-colmetadata-einer-auswahl.md); die Zeile in `live-ausnahmen.tsv` gab es nie (V-2).

## Setzungen als Setzung (gegen Code und Beleg gelesen)

| Aussage | Kennzeichnung | Urteil |
|---|---|---|
| -1 ergibt im Ergebnis 4000 | SPEC-035 „(Setzung, nicht gemessen …)“, [ADR-0056](../plan/adr/0056-laenge-einer-textspalte-erreicht-das-colmetadata-einer-auswahl.md) Entscheidung 3 „Setzung“, KDoc „Setzung, [ADR-0056](../plan/adr/0056-laenge-einer-textspalte-erreicht-das-colmetadata-einer-auswahl.md)“, Test-KDoc „Abbildung von -1 ist Setzung“ | gekennzeichnet |
| Text über der Länge endet mit 50003, Class 20, Verbindung schließt | SPEC-035 „Setzung, die Nummer ist nicht gemessen, Test `SpaltenlaengeAuswahlWireTest`“; [ADR-0056](../plan/adr/0056-laenge-einer-textspalte-erreicht-das-colmetadata-einer-auswahl.md) Entscheidung 2 „Setzung“; die Nutzer-Doku beschreibt das Verhalten **dieses** Servers ohne Aussage über den echten (Test pinnt 50003/20, Probe bestätigt) | gekennzeichnet |
| 2627 (Massenladen) und 50004 / `TDS_BACKEND_ERROR` (`UPDATE`) im Bestand | SPEC-035 „bis dahin sind die Nummern 2627 und 50004 des Bestands Setzung … am echten Server nicht gemessen; gemessen ist nur 2628 beim `MERGE`“; M-11 und M-15 zitiert wie im Messprotokoll (ein Fall mit 23 Zeichen in `nvarchar(5)`, Client erkennt vor dem Senden) | gekennzeichnet, nicht breiter als der Beleg |
| Gemessen: `nvarchar(10)`, Länge 20 | Mitschnitt von mir reproduziert (Referenz und Server je `laenge=20`) | belegt |

## Mutationen

Wiederholt von mir (Wegwerf-Kopie aus `git archive HEAD`, Stage mit enger Testauswahl, Skript und Diffs im Scratchpad, nichts im Repo; Einzelmessung 2026-11-20, ein Host). Der Erzeuger liegt wie beim Beleg nicht im Repo.

| Nr | Mutation (Diff gelesen) | Testauswahl | Ergebnis | gleich dem Beleg |
|---|---|---|---|---|
| Mut-3 | `SimModel.kt`: `toResultColumn()` ohne `reportedLength` | `SimulationBackendSelectLengthTest`, `SpaltenlaengeAuswahlWireTest` | rot, 8 von 10 (5 der Längenklasse, 3 am Draht: `die Auswahl mit Parameter ueber sp_executesql …`, `das COLMETADATA …`, `ein Text ueber der Laenge …`) | ja |
| Mut-17 | `RowRules.kt`: für -1 `Int.MAX_VALUE` statt 4000 | `TableStoreTest` | rot, 2 von 24: `eine Spalte mit maxLength -1 haelt 4000 Zeichen …`, `der Bestand nimmt Texte nur bis zur Laenge …` | ja |
| Mut-6 | `BulkCopyForms.kt`: `column.maxLength` statt `reportedLength` | `BulkCopyFormsTest` | rot, 1 von 9: `die Probe liefert die Spalten der Tabelle, sonst die Ablehnung` | ja |
| Mut-1 | `SelectExecution.kt`: Zweig „Bedingung nie wahr“ ohne Länge | beide neue Klassen | rot, 2 von 10: `eine Bedingung die nie wahr ist meldet die Laenge`, `das COLMETADATA einer Auswahl …` | ja |
| Mut-21 | `SimModel.kt`: `reportedLength` für -1 ergibt 3999 | beide neue Klassen und `TableStoreTest` | rot: 4 von 10 (`ein Text von 4000 Zeichen …`, `die Auswahl mit Parameter …`, `das COLMETADATA …`, `eine Auswahl meldet die Laenge …`) **und** `TableStoreTest > eine Spalte mit maxLength -1 haelt 4000 Zeichen …` (1 von 24) | Beleg nennt den vierten Ort nicht (V-5) |

Jede Mutation war wirksam (die Datei änderte sich, Skript bricht sonst ab) und die Ausgangslage grün (`clean-build`). Mut-3 und Mut-17, die der Auftrag nannte, sind rot wie im Beleg.

## Probe außerhalb der Tests (Wegwerf-Kopie, Rohbytes-Client, Tabelle mit `A` (1), `Lang` (-1), `Eins` (1), `Zwei` (2))

| Text | gemeldete Länge(n) | Zeilen | ERROR |
|---|---|---|---|
| `Lang`, Zeile mit 4001 Zeichen (-1-Spalte) | 4000 | 0 | 50003 / Class 20 |
| `Lang`, Zeile mit 4000 Zeichen | 4000 | 1 | — |
| `Eins`: Zeile mit `""` / `"a"` (Länge 1: leer und genau) | 1 | 1 | — |
| `Zwei`: ein Zeichen außerhalb der Grundebene (2 UTF-16-Einheiten, genau) | 2 | 1 | — |
| `Zwei`: zwei solche Zeichen (4 Einheiten, +2) | 2 | 0 | 50003 / Class 20 |
| Auswahl nur mit NULL-Werten in `Eins`, `Zwei`, `Lang` | 1, 2, 4000 | 1 | — |
| S2: zweite Auswahl ohne Verstoß | [1], [2] | 2 | — |
| S2: zweite Auswahl mit Verstoß (erste Zeile des ersten Ergebnisses geliefert) | [1], [2] | 1 | 50003 / Class 20 |
| S2: erste Auswahl mit Verstoß | [2] | 0 | 50003 / Class 20 (die zweite Auswahl entfällt) |

Das Verhalten stimmt mit SPEC-035, [ADR-0056](../plan/adr/0056-laenge-einer-textspalte-erreicht-das-colmetadata-einer-auswahl.md) Entscheidungen 1 bis 3 und der Nutzer-Doku überein; die Zählung in UTF-16-Einheiten ist die der Bibliothek. Die Fälle „Länge genau“, „+1“, -1/4000, S2 und „Bedingung nie wahr“ sind außerdem im Repo getestet (Ausnahme: -1/4001 und das Zeichen außerhalb der Grundebene am Draht sind nur in der Probe, im Bestand dagegen in `TableStoreTest`).

## Findings

### V-1 — LOW
- `kategorie`: Plan-vs-Code, Maintainability
- `quelle`: Slice §3 („Die Liste ist nach dem Bau gegen `git diff --stat` abgeglichen.“)
- `pfad`: `docs/plan/planning/in-progress/slice-simulation-spaltenlaenge-in-auswahl.md` §3 · `src/main/kotlin/io/github/pt9912/tdswire/simulation/SimulationMerges.kt`
- `befund`: `SimulationMerges.kt` ist im Diff (KDoc von `truncates`: Kopplungsvermerk zum Bestand des Testservers, kein Code), steht aber nicht in der Tabelle §3. Der Re-Review 2 nennt ihn „in §3 gefunden (Grep)“; der Grep trifft nur §1 (Ist-Stand). Der Satz zum Abgleich ist für diese Datei nicht wahr. Kein Verhaltensunterschied.
- `empfehlung`: Planner ergänzt die Zeile (`update`, KDoc, Kopplung) bei der Closure.

### V-2 — INFO
- `kategorie`: Beleg, Reichweite
- `quelle`: Auftrag („`kaputt.Text` nvarchar(10) im Live-Vergleich ohne Ausnahmeregel gleich“), SPEC-045
- `pfad`: `referenztest-dotnet/Vergleich/LiveSzenarioTests.cs` Schritt `fehler-nach-zeilen`, `live-ausnahmen.tsv`
- `befund`: Der Live-Lauf ist grün, beobachtet die Länge von `kaputt.Text` aber nicht: der Schritt liest `SELECT Id, Text FROM kaputt` und vergleicht nur „3 Zeilen gelesen; SqlException …; Zustand Open; Folgeabfrage 1“ (als „so gewollt“ geführt, A-6), keine Spaltenmetadaten. Eine Regel zu A-14 stand in `live-ausnahmen.tsv` nie; es entfällt dort nichts. Der Beleg der Länge ist der Mitschnitt (Aufnahme der Referenz gegen den Server, beide Instanzen, „gleich“ ohne Regel), und genau so führt es das Messprotokoll und die DoD („Nachweis: Mitschnitt-Lauf“). Das Messprotokoll behauptet keinen Live-Beleg der Länge. Die Erwartung des Auftrags ist damit nicht im Live-Lauf, sondern im Mitschnitt erfüllt.
- `empfehlung`: keine; wer die Länge auch live belegen will, ergänzt einen Schritt, der die Spaltenmetadaten von `kaputt` liest (eigener Gegenstand).

### V-3 — INFO
- `kategorie`: Aussage breiter als der Code (Rest von N-2 des Re-Reviews 2)
- `quelle`: `AGENTS.md` §3.8
- `pfad`: `SimulationMerges.kt` `truncates`; `SimModel.kt` KDoc („Geprüft wird sie beim `MERGE` (ein Text darüber ist 2628)“); `docs/user/einbettung-und-betrieb.md`; SPEC-035
- `befund`: `truncates` prüft für `maxLength` -1 nicht (kein 2628, auch nicht über 4000), während Ergebnis und Bestand für -1 die Grenze 4000 haben. Die Sätze nennen die Ausnahme -1 nicht in derselben Zeile (die KDoc von `truncates` und `RowRules` nennen sie). Folge: ein `MERGE` mit über 4000 Zeichen in eine -1-Spalte erreicht den Callback; speichert ihn der Integrator, endet die nächste Auswahl der Zeile mit 50003. Das ist in [ADR-0056](../plan/adr/0056-laenge-einer-textspalte-erreicht-das-colmetadata-einer-auswahl.md) (akzeptiertes Negativ, Entscheidung 3) und in der offenen Frage von SPEC-035 angelegt, kein neuer Mangel.
- `empfehlung`: keine Aktion für diesen Slice; Träger der offenen Frage ist der Nutzer/Architect (Slice §6).

### V-4 — INFO
- `kategorie`: Closure-Vorschläge (Planner entscheidet)
- `befund`: Ausgänge der Risiken §6 nach meinem Befund: (1) -1 als 4000: Setzung bleibt, Test und Probe belegen sie, am echten Server nicht gemessen ([ADR-0056](../plan/adr/0056-laenge-einer-textspalte-erreicht-das-colmetadata-einer-auswahl.md) Re-Evaluierungs-Trigger) — „weiter offen“ mit Zeiger auf [ADR-0056](../plan/adr/0056-laenge-einer-textspalte-erreicht-das-colmetadata-einer-auswahl.md) oder „eingetreten“ als dokumentierte Setzung; (2) Text über der Länge: „eingetreten“ als Setzung (Test, Probe, SPEC-035, Nutzer-Doku), Fehlernummer nicht gemessen; (3) Embedder und Fixtures mit Zeilen über der Beschreibung: **entfallen** — `integratorTest`, `:testserver:test`, `make e2e` (Stufen 3, 8, 9) und `simulations-integration` grün, nur `kaputt` und `ref_zeilen` tragen Längen, ihre Zeilen passen (`"zeile"` zu 10, Erzeuger zu 12, 40, 100); (4) Folge-ADR: eingetreten ([ADR-0056](../plan/adr/0056-laenge-einer-textspalte-erreicht-das-colmetadata-einer-auswahl.md)); (5) Durchsetzung der Länge bei `INSERT`/`UPDATE`/Massenladen: weiter offen, braucht Register-Zeiger (Schablone); (6) Größe: drei Liefer-Punkte, entfallen.

### V-5 — INFO
- `kategorie`: Mutationsbeleg unvollständig in einer Zeile (N-3 des Re-Reviews 2 bestätigt)
- `pfad`: `docs/messungen/spaltenlaenge-mutationen-2026-11-19.md` Zeile Mut-21
- `befund`: Mut-21 ist zusätzlich in `TableStoreTest > eine Spalte mit maxLength -1 haelt 4000 Zeichen und die Auswahl liefert sie` rot (gemessen). Die Zeile nennt den Ort nicht; die Zusage trägt der Beleg trotzdem (er nennt weniger, als wahr ist).
- `empfehlung`: optional beim nächsten Anfassen der Datei ergänzen.

### V-6 — INFO
- `kategorie`: Betrieb
- `befund`: Sperre `messlock` während meines `clean-build` von dritter Seite entfernt (siehe oben); keine Auswirkung auf die Ergebnisse erkennbar. Die Stempel der Klassen `werkzeug` und `tests` stehen nach `make gates` als Notes auf `25056715`.

HIGH: keines. MEDIUM: keines. LOW: V-1. INFO: V-2 bis V-6.

## Urteil

Der Slice setzt seine Zusagen um: Die Länge der Beschreibung erreicht das COLMETADATA jeder Auswahl (S1, S2, „Bedingung nie wahr“, `sp_executesql`), -1 ergibt 4000, ein Text über der Länge endet im Fehler der Antwort, `kaputt.Text` ist `nvarchar(10)` wie die Referenz, und die Regel `fehler-nach-zeilen/01/colmetadata` ist weg, ohne dass ein Lauf rot wird. Die schweren Sensoren (`clean-build`, `e2e`, `kompatibilitaet`, `simulations-integration`, Mitschnitt, Live, `gates`) sind auf dem End-Stand grün; fünf Mutationen sind rot wie im Beleg; die Texte sind nicht breiter als der Code, die Setzungen sind gekennzeichnet.

**Empfehlung: Closure ja.** Offen sind nur Schritte des Planners: DoD-Haken, Closure-Notiz mit Steering-Loop-Lerneintrag, Ausgänge der Risiken §6 (Vorschläge V-4), Register `BEO-TDS/live-lauf-ausserhalb-der-gates-haelt-kopplungen-nicht` und ein Register-Zeiger für „weiter offen“ (Durchsetzung der Länge), V-1 (Zeile in §3).

`make docs-check`: Exit 0, „1700 Datei(en) geprüft, 0 Befund(e)“ auf dem Stand mit diesem Bericht (Lauf 08:46 Uhr, Wartezeit auf die Sperre 19 Minuten wegen laufender Aufträge anderer Agenten).
