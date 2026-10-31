# Verifikation: slice-sim-erkennung (Umsetzung 7aa0763, Fix-Runde d0d932a und 188c4f3) — 2026-10-31

**Rolle:** Verifier (Modul 11), frischer Kontext. Frage: Bauen wir es richtig? Geprüft wird die DoD von [slice-sim-erkennung](../plan/planning/in-progress/slice-sim-erkennung.md) gegen Code, Tests, [SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends), [SPEC-036](../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig), [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md) und das Lastenheft 0.5.0 ([TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), `IS [NOT] NULL`). Kein Review, keine Reparatur.

**Stand:** `main` bei b339b77 (enthält die Fix-Runde d0d932a Doku und 188c4f3 Code). Der Worktree war sauber; die Fix-Runde ist die Eingabe, der Review-Report [2026-10-31-7aa0763-slice-sim-erkennung.md](2026-10-31-7aa0763-slice-sim-erkennung.md) (F-1 bis F-12) die Prüfliste.

**Eingang:** DoD-Bestätigung des Implementers und der Commit-Text der Fix-Runde („F-1 … F-12“). Ein Sensor-Beleg als Artefakt lag nicht vor; alle Sensoren sind selbst gefahren, siehe „Eigene Läufe“.

## Ergebnis in einem Satz

Die drei Liefer-Punkte der DoD sind im Code und in den Tests gehalten, alle zwölf Review-Befunde sind behoben oder ehrlich als Setzung geführt, und 24 von 25 eigenen Mutationen wurden gerötet; es bleibt **kein HIGH, kein MEDIUM**, aber **drei LOW** (eine Spec-Aussage ohne Test, die bekannte Listen-Frage F-11 als Rest, ein Datum im Plan).

## Eigene Läufe

Alle Docker-Builds nacheinander, nie zwei gleichzeitig. Mutationen auf einer Kopie des Stands im Scratchpad (`…/scratchpad/ver/m`, Quelltext je Lauf frisch aus dem Worktree), jede in einem eigenen `make test` bzw. `make detekt`; im Repo ist nichts verändert. Der Arbeitsordner `ver/` war aus einem früheren Lauf nicht leer (fremde Log-Dateien, darunter `results.txt`); ich habe nur meine eigenen Zeilen gewertet.

| Sensor | Ergebnis |
|---|---|
| `make test` | grün (Stage lag im Cache, daher kein Ausgabebeleg) |
| `make clean-build` (Jar, Tests und detekt, `--no-cache`) | grün; 647 Tests `PASSED`, 0 `FAILED`; davon 88 der Simulation (7 `SimModelTest`, 26 `SimDescriptionReaderTest`, 25 `StatementFormsTest`, 8 `StatementPlannerTest`, 17 `StatementScannerTest`, 3 `StatementOracleTest`, 2 `SimulationCodesTest`), übersprungen nur Gradle-Aufgaben, kein Test |
| `make detekt` | grün (Exit 0) |
| `@spec`-Tag je Test (eigenes Skript über die elf Testdateien) | 88 von 88 `@Test` tragen einen Block-Tag `@spec SPEC-<NNN>` (035, 036, 037, 038) |
| `make gates` | nicht gefahren; gefahren sind die Gates, die diese Fix-Runde berühren (`test`, `detekt`, `build` über `clean-build`) |
| Release-Liste d-migrate (`gh api`) | neuestes Release v1.12.0, veröffentlicht 2026-10-27; `spec/schema.json` zuletzt in fc94f12 am 2026-10-25, vor dem Belegstand a339849: der Belegstand ist nicht gealtert |

**Mutationen (25; Test rot = getötet):**

| Wächter | Mutation | Ergebnis |
|---|---|---|
| Scanner je Codepunkt (F-1) | `wordEnd` rückt um 1 statt `charCount` | getötet (`StatementOracleTest` zweimal, `StatementScannerTest > liest Buchstaben außerhalb der Grundebene …`) |
| | Wortanfang rückt um 1 statt `charCount` | getötet (`StatementScannerTest`) |
| | `isLetter` auf `Char` statt Codepunkt | getötet (Oracle zweimal, Scanner) |
| Stelle der Ladefehler (F-2) | „Feld unbekannt“: Wert- statt Schlüsselstelle | getötet |
| | „precision fehlt bei decimal“: Stelle (0, 0) | getötet |
| | „ordinal doppelt“: Stelle der Abbildung statt des Werts | getötet |
| | „Name leer“: Stelle (0, 0) | getötet |
| | über die Fix-Runde hinaus: „Feld doppelt“, „scale größer als precision“, „Typ unbekannt/ohne Entsprechung“ je Stelle (0, 0) | alle getötet |
| quotierte Bezeichner an Wertstelle (F-3) | `[NULL]`/`"null"` als `NULL` lesen | getötet (`lehnt quotierte Bezeichner an Wertstellen ab`) |
| | jeden Quoted als Textliteral lesen | getötet (derselbe Test) |
| Zähler (F-4) | `repeat(450)` statt 600 | getötet (`erkennt erzeugte Anweisungen wieder`) |
| | Anhangsliste um einen Eintrag gekürzt (7 auf 6) | getötet (`lehnt erzeugte Anhänge und Verdopplungen ab`) |
| `SimCodes` gegen `ErrorCode` (F-8) | dritte Konstante `TDS_NOT_A_CODE` | getötet (`jede Konstante von SimCodes ist ein Code des Protokolls`) |
| | `UNKNOWN_OBJECT` mit angehängtem `S` | getötet (beide Tests der Kopplung) |
| Pfadbereinigung (F-10) | ohne `FORMAT` | getötet (`ersetzt Format- und Trennzeichen im Pfadabschnitt`) |
| | ohne `SURROGATE` | getötet (derselbe Test) |
| | ohne `PARAGRAPH_SEPARATOR` | getötet (derselbe Test) |
| | Kürzen je `Char` statt Codepunkt (Paar getrennt) | getötet (derselbe Test) |
| Decimal-Invariante (F-12) | Grenze 39 statt 38 | getötet (`weist eine Dezimalspalte über 38 Stellen ab`) |
| | Prüfung entfernt | getötet (derselbe Test) |
| Katalog kopiert (F-11) | `tables` ohne `toList()` | getötet (`hält Katalog und Spaltenindex …`) |
| `LongParameterList`-Begründung (F-11) | `SimTable` als `class` statt `data class` | `make detekt` rot: „The constructor(schema, name, …) has too many parameters. The current maximum allowed parameters are 6. [LongParameterList]“; die KDoc-Begründung trägt |
| Namensvergleich (SPEC-036) | `fold` nur mit `toLowerCase` (ohne `toUpperCase`) | **überlebt** (alle Tests grün), siehe V-1 |

## DoD Punkt für Punkt

| DoD-Punkt (§2 des Plans) | Befund | Beleg |
|---|---|---|
| 1 Tabellenbeschreibung (Typen, Validierung, Leser) | erfüllt | Typen in `SimModel.kt` entsprechen der Skizze in [SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) (acht Konstruktorparameter, `FilterTerm.In/IsNull`, `RowSource`); Validierung doppelt/leer/Schlüssel nullbar in `SimTable.init`, `SimCatalog.init`; Dezimalgrenze 38 jetzt auch in `SimColumn` (M7 getötet); Leser mit Ignorierliste, `tables` als Pflicht, Typabbildung der zehn Typen, Ladefehler mit Pfad und Stelle in 26 Tests; der Leser kennt keine YAML-Bibliothek (Importe nur `domain`/`simulation`) |
| 2 Anweisungserkennung (S1, W1, W2, W3; Scanner) | erfüllt | `SqlScanner`/`SqlLexemes`/`StatementParser`; jede Zeile der Liste „Nicht erkannt“ als Negativtest mit erkanntem Zwilling (`assertOnlyExtensionFails`), `IS [NOT] NULL` erkannt (`StatementFormsTest`, Zeile 36 und 284) und durch Lastenheft 0.5.0 gedeckt ([TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), Historie-Zeile 0.5.0); Orakel 600 Anweisungen, 64 Grenzfälle, 7 Anhänge, jetzt in der Zahl festgelegt (M4 getötet) |
| 3 Objektprüfung vor dem Lesen | erfüllt | `StatementPlanner.plan` ist keine `suspend`-Funktion; zählende Attrappe mit positivem Kontrollfall (`StatementPlannerTest`, in der Review-Runde mit zwei `runBlocking`-Mutationen gerötet; im Code seitdem unverändert); `TDS_UNSUPPORTED_SQL` und `TDS_SIM_UNKNOWN_OBJECT` über `SimCodes`, Kopplung an `ErrorCode` für alle Konstanten (M5) |
| Nachweis: `@spec`-Tag je Test, Tests in `make test` grün | erfüllt | 88 von 88; `make clean-build` grün |
| Nachweis: Aussage hat Test oder Kennzeichnung als Festlegung | im Wesentlichen erfüllt | Setzungen sind in SPEC-035/036 durchgehend „nicht am Server gemessen“ gekennzeichnet; Lücke: V-1 |
| `make gates` grün | nicht von mir gefahren | berührte Gates grün (`test`, `detekt`, `build` über `clean-build`); die Gate-Kette ist Sache des Implementers vor Handoff und der Closure |
| Review-Report liegt vor | erfüllt für 7aa0763 | [Report](2026-10-31-7aa0763-slice-sim-erkennung.md); die Fix-Runde 188c4f3 hatte kein eigenes Review, diese Verifikation deckt ihre Wächter durch Mutation ab (V-4) |
| Doku-Update, Closure-Notiz, Register, Risiko-Ausgänge, Paarungen | offen (Closure-Pflicht) | §7 des Plans ist „Wird bei der Closure gefüllt“, alle sieben Risiken in §6 tragen „offen bis zur Closure“; die DoD-Häkchen sind ungesetzt; das ist der Zustand vor der Closure und kein Befund |

## Plan-vs-Code-Diff

- **Dateitabelle (§3):** alle elf Quelldateien der Tabelle existieren in `src/main/.../simulation` (`SimModel`, `SimNames`, `SimTree` samt `SimLoadException`, `SimTreeAccess`, `SimColumnTypes`, `SimDescriptionReader`, `SqlLexemes`, `SqlScanner`, `StatementParser`, `Statement`, `StatementPlanner` samt `SimCodes`); keine Datei im Code fehlt in der Tabelle, keine Tabellenzeile ohne Datei (Review F-6 behoben). `SimulationCodesTest` liegt in `runtime`.
- **Maß der Rückführung (§4):** gemessen mit `wc -l`: 1044 Zeilen unter `simulation` in `src/main` (Grenze 1500, Plan „rund 1050“) und 2420 unter `simulation` in `src/test` (Grenze 3500, Plan „rund 2500“). Beide unter der Grenze; „rund 2500“ rundet 2420 großzügig auf, die Aussage trägt als Größenordnung.
- **Importregel:** `simulation` importiert aus `domain` (`SqlType`) und aus sich selbst; `ForbiddenImport/simulation` in `config/detekt/detekt.yml` unverändert; `make detekt` grün.
- **Berührte Spec-Stellen:** SPEC-035/036 in der Fix-Runde nachgezogen (Vertragsskizze nicht mehr „nicht implementiert“, Codepunkte, Pfadbereinigung, Namensvergleich, Dezimalgrenze, Kopierregel, Historie-Zeile). Der Text deckt den Code, bis auf V-1.

## Konformität zu ADR-0012 und den Hard Rules

- [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md) steht auf `Accepted`, im Index (`docs/plan/adr/README.md`) ebenso, und ist in der Fix-Runde **nicht** verändert (letzte Änderung 06d5b5a); AGENTS.md §3.5 gewahrt. Entscheidung 14 (Zeichenscanner statt Regex, Formen S1/W1/W2/W3, `IS [NOT] NULL`, fünfzehn Wörter) ist im Code umgesetzt; die Fix-Runde schärft nur Setzungen, die die ADR offen lässt (Wörter „Buchstaben“, Namensvergleich), und liegt innerhalb ihrer Aussagen. Offen 12 (a), die Lesart „Liste abschließend“, bleibt unbestätigt und ist in SPEC-036 und Plan §6 als Setzung geführt: kein Befund, aber ein Closure-Ausgang.
- [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend): `IS NULL`, `IS NOT NULL`, Gleichheit, Menge, UND, Einfügen/Ändern/Löschen als Form; Auswertung von SQL gibt es nicht (Zuordnung ohne Auswertung).
- §3.1 Docker-only: alle Läufe über `make`. §3.2: kein `@Suppress`/`@SuppressWarnings` (`make detekt` samt Suppression-Prüfung grün). §3.6: keine Änderung an `config/`, `Dockerfile`, `build.gradle.kts`, `tools/` in der Fix-Runde (`git show --stat`: zehn Dateien unter `src`, zwei Dokumente). §3.7: KDoc der Fix-Runde trägt Zusage, Kopplung oder Abgrenzung im Indikativ; V-5 ist ein Rest.
- §3.8: die Fix-Runde hat die genannten Überstände der Review-Runde nachgemessen oder eingeschränkt (siehe Mutationen); Rest V-1 und V-2.

## Bewertung der Review-Befunde nach der Fix-Runde

| Review | Zustand | Beleg |
|---|---|---|
| F-1 Codepunkte | behoben | M1a bis M1c getötet; Test mit U+20000, U+1D400, Nicht-Buchstabe U+1F600, einzelnem Ersatzzeichen |
| F-2 Stelle der Ladefehler | behoben | vier Mutationen der Review-Runde und drei weitere getötet |
| F-3 quotierte Bezeichner an Wertstelle | behoben | M3a, M3b getötet; sechs Schablonen mal sieben Quotierungen, Zwilling erkannt |
| F-4 Zähler | behoben | M4a, M4b getötet; `assertEquals(600, generated)`, `assertEquals(7, suffixes.size)` |
| F-5 Spec-Satz „Vertragsskizze“ | behoben | Text in SPEC-035 an den Ist-Stand gebunden (Zeilen 509 bis 511) |
| F-6 Plan-Tabelle | behoben | Diff oben |
| F-7 Maß der Rückführung | behoben | Maß in §4, gemessen oben |
| F-8 Kopplung aller `SimCodes` | behoben | M5, M5b getötet |
| F-9 Namensvergleich nur im KDoc | **teilweise**: jetzt in SPEC-036, aber die Aussage `s` gleich `ſ` hat keinen Test | V-1 |
| F-10 Pfadbereinigung | behoben | M6a bis M6d getötet |
| F-11 `SimTable` ohne Kopie | **bewusst offen, dokumentiert**; Katalog behoben | V-2 |
| F-12 Decimal über 38 in `SimColumn` | behoben | M7, M7b getötet; Hinweis an [slice-sim-auswahl-streaming](../plan/planning/open/slice-sim-auswahl-streaming.md) bleibt für die Überführung |

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Klasse |
|---|---|---|---|---|---|
| V-1 | LOW | [SPEC-036](../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) sagt „danach sind `i` und `İ` sowie `s` und `ſ` gleich“ und das KDoc von `SimNames` legt die Regel `toLowerCase(toUpperCase(cp))` fest. Getestet ist nur `i` gegen `İ` (und `ss` gegen `ß` als verschieden). Die Mutation „nur `toLowerCase`“ (ohne `toUpperCase`) bleibt grün; `i`/`İ` sind schon nach `toLowerCase` allein gleich, und `s`/`ſ` kommt in keinem Test vor. Die Regel stimmt nach den Unicode-Abbildungen (U+017F wird zu `S`), ist aber an dieser Stelle weder gemessen noch getestet; als „Festlegung ohne Messung“ gekennzeichnet, trägt sie also nur die Kennzeichnung, nicht den Test. | AGENTS.md §3.8 · [SPEC-036](../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) | src/test/kotlin/io/github/pt9912/tdswire/simulation/SimModelTest.kt · `weist doppelte Spaltennamen ab` (kein Paar `s`/`ſ`); src/main/kotlin/io/github/pt9912/tdswire/simulation/SimNames.kt · `Character.toLowerCase(Character.toUpperCase(it))` | Festlegung ohne Test an der Aussage |
| V-2 | LOW | Zum bekannten Rest F-11: Der Katalog kopiert, die Tabelle nicht. Bewertung: **die Begründung hält** (M9: als `class` bricht `make detekt` mit `LongParameterList`, maximal 6 Parameter, `SimTable` hat acht), die Zusage steht ehrlich in KDoc und [SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) („behält ihre Listen unkopiert, der Aufrufer ändert sie nicht“), und es ist keine Gate-Lockerung (§3.6): die Schwelle bleibt. Was bleibt: (a) Die Validierung (Schlüssel nicht nullbar, doppelte Namen, Dezimalgrenze) und der Spaltenindex gelten nur zum Zeitpunkt der Übergabe; ändert der Integrator die Liste später, stehen `columns` und `column()` auseinander. (b) Der Test `hält Katalog und Spaltenindex gegen spätere Änderungen der Listen` trägt im Titel eine Abwehr, die er für die Tabelle nicht belegt: er hält nur fest, dass der Index den Stand der Übergabe behält (`assertNull(t.column("late"))`), während `t.columns` die neue Spalte schon enthält; das ist der dokumentierte Rest, kein Schutz. Weg zu einer Lösung ohne Gate-Änderung: ein Fabrikaufruf oder ein sekundärer Konstruktor, der kopiert; das ist Entscheidung des Implementers, kein Auftrag der Verifikation. Vor der Closure als Risiko „weiter offen“ mit dem Zeiger auf den Integrationsslice führen oder schließen. | AGENTS.md §3.8 · Maintainability | src/main/kotlin/io/github/pt9912/tdswire/simulation/SimModel.kt · „`columns` und `primaryKey` werden nicht kopiert“; src/test/kotlin/io/github/pt9912/tdswire/simulation/SimModelTest.kt · `hält Katalog und Spaltenindex …` | Rest eines dokumentierten Befunds |
| V-3 | LOW | Plan §6 nennt den Belegstand „Commit a339849, Release 1.12.0, 2026-10-30“; das Release v1.12.0 ist vom 2026-10-27 (`gh api`), der 2026-10-30 ist der Prüftag. §3 desselben Plans sagt richtig „(2026-10-27)“. [SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) sagt „geprüft … 2026-10-30“ und ist richtig. | AGENTS.md §3.8 | docs/plan/planning/in-progress/slice-sim-erkennung.md · §6 „Release 1.12.0, 2026-10-30“ | Datum widerspricht §3 |
| V-4 | INFO | Die Fix-Runde 188c4f3 hat kein Review: Der Report [2026-10-31-7aa0763](2026-10-31-7aa0763-slice-sim-erkennung.md) prüft den Stand davor. Diese Verifikation ersetzt kein Review; sie hat die Wächter der Fix-Runde durch Mutation gegriffen (24 von 25 rot) und den Code der Fix-Runde (`SqlLexemes.wordEnd`, `SimTreeAccess.child`, `SimColumn.init`, `SimCatalog`) gelesen, ohne Befund außer V-1 und V-2. Der Planner entscheidet, ob ein kurzes Nachreview vor der Closure verlangt wird. | Prozess | docs/reviews/2026-10-31-7aa0763-slice-sim-erkennung.md | Fix-Runde ohne eigenes Review |
| V-5 | INFO | Im KDoc von `weist eine Dezimalspalte über 38 Stellen ab` steht eine verwaiste Zeile „Precision.“ unter dem ersten Satz. Kosmetik. | AGENTS.md §3.7 | src/test/kotlin/io/github/pt9912/tdswire/simulation/SimModelTest.kt · Zeile 104 | verwaister KDoc-Rest |

## Offene Punkte für die Closure (kein Befund, Hinweis an den Planner)

- §7 (Closure-Notiz) braucht die Zuordnung *Test | Festlegung* je Aussage; V-1 gehört in die Spalte „Festlegung ohne Test“, wenn es nicht vorher einen Test bekommt.
- Die sieben Risiken in §6 tragen „offen bis zur Closure“. Nach diesem Lauf: Scanner-Fehlzuordnung (Mutationen und Orakel tragen, Ausgang „eingetreten/entfallen“ ist Sache der Closure; eingetreten ist sie nicht), Belegstand d-migrate (nicht gealtert, V-3 beachten), Offen 7 und 8 der ADR (Festlegungen im Leser, keine Obergrenze), Strukturtest/Zähler (durch F-3 und F-4 gedeckt), Groß-/Kleinschreibung (Festlegung, V-1), Lesart „Liste abschließend“ (weiter offen, Offen 12 (a) der ADR), Größe (Maß unterschritten).
- Beobachtungs-Register: Treffer für `BEO-TDS/strukturtest-ohne-variation-der-eingabe`, `BEO-TDS/schranken-test-mit-ungeprueftem-zaehler` und `BEO-TDS/aussagen-ueber-messung-hinaus` in der Review-Runde liegen vor; ob die Fix-Runde eine weitere Datei unter `evidence/` verlangt, entscheidet der Planner.

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 0 |
| LOW | 3 (V-1, V-2, V-3) |
| INFO | 2 (V-4, V-5) |

**Verdikt:** Die DoD-Liefer-Punkte sind erfüllt, die Fix-Runde trägt; kein Befund blockiert die Closure. V-1 und V-2 sind vor der Closure zu beantworten (Test, Einschränkung der Aussage oder Eintrag in §7), V-3 ist eine Zeile im Plan.

**Übergabe:** Bericht an den Planner. Der Verifier hat nichts repariert, nichts nach `done/` bewegt und die DoD-Häkchen nicht gesetzt.
