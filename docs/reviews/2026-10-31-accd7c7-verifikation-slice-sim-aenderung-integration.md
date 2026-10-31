# Verifikation: slice-sim-aenderung-integration (Umsetzung f434704/ebfe3ba, Review F-1..F-12, Fix-Runde 76985bd) — 2026-10-31

**Rolle:** Verifier (Modul 11), frischer Kontext. Frage: Bauen wir es richtig? Geprüft wird gegen die DoD des Slice [slice-sim-aenderung-integration](../plan/planning/done/slice-sim-aenderung-integration.md), [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) mit `SPEC-035` bis `SPEC-040`, [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md), [ADR-0013](../plan/adr/0013-backend-grenzen.md) und `AGENTS.md` §3.6 bis §3.8. Der Review ([Report a7e6afa](2026-10-31-a7e6afa-slice-sim-aenderung-integration.md)) ist Eingang, nicht Beleg.

**Stand:** `main` bei accd7c7 (Merge der Fix-Runde 76985bd); Arbeitsbaum sauber vor dem Lauf, alle Mutationen zurückgenommen (`git status` leer). Der Slice liegt in `in-progress/`, §7 (Closure) ist leer, kein DoD-Haken gesetzt.

## Ergebnis in einem Satz

Liefer-Punkte 1 (Änderungsweitergabe) und 2 (Verdrahtung) sind erfüllt, Liefer-Punkt 3 ist wie bekannt **nicht** erfüllt (kein Cancel mit Folgeabfrage) und dieser Stand ist in Spec, README, Skript, C#-Test und Review ehrlich benannt; die Fix-Runde hat F-2 (zur Hälfte neu geöffnet), F-3, F-4, F-5, F-7 und F-8 behoben. Es bleiben **0 HIGH**, **1 MEDIUM** (ein neu entstandener Fehlerzweig ohne Test) und **5 LOW**.

## Sensor-Belege (selbst gefahren)

| Sensor | Ausgang |
|---|---|
| `docker build --no-cache-filter test --target test --progress=plain .` | Exit 0 nach 1 min 44 s: 775 Zeilen `PASSED`, 0 `FAILED`, 1 Gradle-Task `SKIPPED` (kein Test); `IntegrationZahlenTest`, `ein Pfad ohne reguelaere Datei ist unlesbar` und `unzulaessige Zeichen sind ein Ladefehler …` laufen und bestehen. Hinweis: ein Build des parallelen Verifiers lief zeitgleich; das Ergebnis ist davon nicht betroffen, die Laufzeit kann es sein. |
| `make gates` | Exit 0 (46 s, Test-Layer aus dem Cache des Laufs oben); `simulations-integration` steht in `GATE_CHECKS` (`tools/mk/simulationstest.mk`) und lief darin: „Alle 4 Tests des Referenzclients bestanden“ |
| `make simulations-integration` (einzeln) | Exit 0, Laufzeit 18 s laut Skript (Bau 3 s, Tests 15 s), 19,6 s Wanduhr; Heap max 50 331 648 Bytes, Text von `gross` 64 000 000 Zeichen in 500 000 Zeilen; Server läuft nach den Tests, Log ohne Handlerfehler, ohne Kennwort, ohne Text der Quellenausnahme. Die Zahl deckt die Angabe „19 s“ im Kopf von `tools/mk/simulationstest.mk` (Einzelmessung, keine Streuung). |
| Mutation A, vier Änderungen in einem Test-Build: Zähler `count >= 0` zu `true`; `readNBytes(limit + 1)` zu `readNBytes(limit)`; Listenkopie in `SimulationSetup.owned` entfernt (F-11 des Reviews-Begleiters); `Files.isRegularFile` zu `Files.exists`; Heap `48m` zu `40m` in `build.gradle.kts` | rot, 775 Tests, genau 5 fehlgeschlagen: `IntegrationZahlenTest > Heap und Text …`, `die Groessengrenze greift vor dem Parsen`, `der Katalog behaelt die Listen des Integrators als Kopie`, `ein Pfad ohne reguelaere Datei ist unlesbar`, `ein Fehler des Callbacks ist TDS_BACKEND_ERROR …`; jede Änderung wird von mindestens einem Test erkannt |
| Mutation B: Zeilen `org.yaml.*` aus `ForbiddenImport/simulation` in `config/detekt/detekt.yml` | `make detekt-selbsttest` Exit 2: „Regel ForbiddenImport/simulation meldet 10 von 11 verbotenen Importen (6 aus detekt.yml, 5 aus Fremd.kt)“ — der Wächter wird gehalten |
| Mutation C: `catch (IOException)` in `ConfigFiles.readAtMost` entfernt | **grün** (Exit 0): der Zweig trägt keinen Test, siehe M-1 |
| Mutation D: `catch (InvalidPathException)` in `ConfigFiles.readAtMost` entfernt | rot: `ein Pfad ohne reguelaere Datei ist unlesbar` (der NUL-Pfad) — F-2, Teil `InvalidPathException`, belegt |
| `make docs-check` | Exit 0: 491 Dateien, 0 Befunde (mit diesem Bericht) |

Logs: `/tmp/claude-1000/-Development-tdswire/8562f454-84ba-456b-9970-945e69c035f4/scratchpad/ver-sim/` (`test1.log`, `gates.log`, `sim.log`, `mut1.log`, `mut2.log`, `mut3.log`, `mut4.log`).

## DoD Punkt für Punkt

| DoD-Punkt (Slice §2) | Befund |
|---|---|
| 1 Änderungsweitergabe: W1 bis W3 prüfen Werte, rufen den Callback (`where = null` ohne `WHERE`), senden dessen Zahl als `UpdateCount`; ohne Callback `TDS_UNSUPPORTED_SQL`; Fehler im Callback `TDS_BACKEND_ERROR`; bei Wertfehler kein Callback (zählende Attrappe mit Kontrollfall); jede Anweisung ruft ihren Callback genau einmal | **erfüllt.** Tests in `SimulationBackendChangeTest` (Einfügen, Parameter und Zahl null, Ändern, Löschen mit und ohne `WHERE`, ohne Callback, ungültiger Wert ohne Aufruf an Tabelle mit und ohne Callback, unbekannte Spalte, Fehler des Callbacks, Beenden vor dem Sammeln). Mutation A (negative Zahl nicht abgelehnt) wird von `ein Fehler des Callbacks ist TDS_BACKEND_ERROR …` erkannt. „Genau einmal“ gilt nach der Fix-Runde mit der Bedingung eines einzigen Sammlers (F-8, siehe L-3). Kein Callback-Text in Meldung (Test). Aufruf läuft in `guarded` (`suspend`); `Error` wie `OutOfMemoryError` wird weitergeworfen ([ADR-0013](../plan/adr/0013-backend-grenzen.md): suspendierend, kein Fangen von `Error` im Backend). |
| 2 Verdrahtung: optionale Verdrahtung in `runtime` (Kotlin-Schnittstelle und Umgebungsvariable `SIMULATION_DESCRIPTION_FILE`); `jackson-dataformat-yaml` 2.21.7 als `implementation`; Adapter mit Strom-API; Größengrenze 1 MiB vor dem Parsen; Startabbruch vor dem Binden; geladene Tabellen mit leerer Zeilenquelle und ohne Callbacks; keine andere Package importiert `simulation`; Wächter `ForbiddenImport/simulation` für die YAML-Bibliothek samt Selbsttest-Fall; Server startet ohne Backend | **erfüllt.** Tests: `SimulationWiringTest` (Datei liefert Metadaten und leeres Ergebnis, gemeinsamer Katalog, Kopie der Listen, Login ohne Backend, Ladefehler vor dem Binden, Größengrenze, Pfad ohne reguläre Datei, Widersprüche), `YamlTreeReaderTest`. Lock: `gradle.lockfile` +6 Zeilen, `verification-metadata.xml` +11 `<component>` — deckt sich mit der Größenordnung der ADR („6 Sperrzeilen, 11 Komponenten“); Jackson 2.21.7, `snakeyaml` 2.5. Wächter: `com.fasterxml.jackson.*` und `org.yaml.*` in `config/detekt/detekt.yml`; Selbsttest-Fall `Fremd.kt` hält ihn (Mutation B rot). Verschärfung, keine Lockerung (§3.6). Importregel der Schichten grün (`make gates`). |
| 3 Test-Gate nach `SPEC-040`: Auswahl mit Bedingung, leeres Ergebnis, Zeilenquelle oberhalb der Speichergrenze, Fehler der Zeilenquelle **und Cancel mit Folgeabfrage**; Make-Target in `harness/README.md` §Sensors; Aufnahme in `make gates` nach Laufzeit | **nicht erfüllt, wie bekannt:** vier von fünf Szenarien laufen (`SimulationBackendTests.cs`, vier Tests, grün in `make simulations-integration` und in `make gates`); das Cancel-Szenario fehlt. Das Make-Target steht in `harness/README.md` §Sensors und in `GATE_CHECKS`; die Laufzeit (Warmlauf 18 bis 19 s unter der Teststufe ohne Cache, 89 bis 102 s) trägt die Aufnahme. Ehrlichkeit der Dokumentation: siehe unten. |
| Nachweis: `@spec SPEC-<NNN>` über jedem Test, Tests in `make test` grün | erfüllt für die Kotlin-Tests (775 grün); `IntegrationZahlenTest` trägt bewusst keinen Tag (KDoc begründet es). Die .NET-Tests tragen keinen Tag (die Matrix kennt nur Kotlin-Tests), siehe L-5. |
| Nachweis: Zuordnung *Test \| Festlegung* in der Closure-Notiz | offen — Closure ist nicht geschrieben (Slice §7 „Wird bei der Closure gefüllt“); kein Befund, Closure ist ausdrücklich nicht Auftrag. |
| Nachweis: `make gates` grün | erfüllt (Exit 0, siehe oben). |
| Nachweis: Review-Report liegt vor | erfüllt: [Report a7e6afa](2026-10-31-a7e6afa-slice-sim-aenderung-integration.md) (0 HIGH, 2 MEDIUM, 10 INFO). |
| Nachweis: Doku-Update, Register, Risiko-Ausgänge, Paarungen | offen (Closure-Aufgaben; im Slice §6 stehen alle Ausgänge „offen bis zur Closure“). Kein Befund. |

## Ehrlichkeit der Cancel-Lücke (Liefer-Punkt 3)

Die Lücke ist an fünf Stellen übereinstimmend benannt: `SPEC-040` („Cancel mit Folgeabfrage gehört nicht zum Lauf: der Server beantwortet ein ATTENTION-Paket während einer Anfrage nicht“), das KDoc von `referenztest-dotnet/Integration/SimulationBackendTests.cs`, der Kopf von `tools/simulationstest.sh`, die Sensor-Zeile in `harness/README.md` (nennt nur die vier laufenden Szenarien) und der Review (F-1). Die Aussagen widersprechen sich nicht; keine Stelle behauptet Cancel als gebaut oder gemessen. Kein DoD-Haken ist gesetzt. Die Entscheidung über Carveout oder Folge-Slice steht beim Nutzer aus und ist kein Befund. Einziger Widerspruch in der Dokumentation: Slice-Plan §1 (Ziel) und §2 (Liefer-Punkt 3) fordern Cancel weiter, Spec und Sensor-Zeile schließen ihn aus; der Plan-Text wird erst mit der Entscheidung angepasst (L-4). Kein anderer Plan trägt den Cancel-Teil (`slice-attention-cancel-timeout` schließt den Referenzclient aus; `docs/plan/carveouts/` hat keinen Eintrag dazu).

## Fix-Runde 76985bd: Finding für Finding

| Finding | Behoben? | Beleg |
|---|---|---|
| F-2 (Fehlerzweige ohne Test) | **zur Hälfte.** `InvalidPathException` belegt (NUL-Pfad im Test; Mutation D rot). Der `IOException`-Zweig im Adapter `YamlTreeReader.read` ist entfernt, der Test `unzulaessige Zeichen sind ein Ladefehler …` hält die Eingaben mit `\u0000`, `\u0007`, `\u0001` als Ladefehler „YAML nicht lesbar“; die Begründung „aus dem Speicher, kein `IOException`“ ist eine Ableitung aus `createParser(String)`, nicht gemessen, aber der Test deckt unzulässige Zeichen. Der `IOException`-Zweig in `ConfigFiles.readAtMost` bleibt und ist durch die Fix-Runde unerreichbar für den Test geworden (fehlende Datei, Verzeichnis, Gerät laufen jetzt in den `else`-Zweig): Mutation C überlebt. | M-1 |
| F-3 (Zahlen an mehreren Orten) | **ja.** `IntegrationZahlenTest` vergleicht `maxHeapSize` aus `build.gradle.kts` mit `SPEC-040` (Heap und Zeichenzahl aus `BIG_ROWS` mal `BIG_TEXT_CHARS`) und mit der Zeile in `harness/README.md`; Heap `48m` zu `40m` macht ihn rot (Mutation A). Die Zahlen in Kotlin und C# hält weiter der .NET-Test; nicht neu gefahren. | Mutation A |
| F-4 (Fangklausel zu breit) | **ja.** `catalogOf` fängt `IllegalArgumentException` nur um `SimCatalog(all)` und übersetzt sie in die private `DuplicateNameException`; die äußere Klausel fängt nur diese. Der Test `Widersprueche zwischen Datei Schnittstelle und Backend brechen den Start ab` deckt den Namenskonflikt; die Umhüllung selbst wurde nicht eigens mutiert. | Quelltext, Test grün |
| F-5 (Sonderdateien) | **ja, bis auf zwei Aussagen im KDoc.** `Files.isRegularFile` vor `newInputStream`; der Test prüft Verzeichnis, `/dev/zero`, `/dev/null` und einen NUL-Pfad, jeweils `UNREADABLE`; `isRegularFile` zu `exists` macht ihn rot (Mutation A). Symlink und FIFO behauptet das KDoc, kein Test führt sie aus (L-1). | Mutation A |
| F-7 (Laufzeit nur an einem Ort) | **ja.** Kopf von `tools/mk/simulationstest.mk` nennt Warmlauf 25 s und den späteren 19 s; die eigene Messung 18 bis 19 s deckt sich. Die Sensor-Zeile in `harness/README.md` nennt weiter keine Laufzeit (Review F-7 verlangte sie nicht zwingend). | `sim.log` |
| F-8 (Zusage „genau einmal“) | **ja im Text, nicht am Test.** `SPEC-035` sagt jetzt „solange ein einziger Sammler sie sammelt (ein zweiter Sammler derselben Ausführung ruft ihn ein zweites Mal)“. Der zweite Halbsatz ist eine Aussage über den kalten `flow`, die kein Test ausführt (L-3). | Spec Zeile „Jede Anweisung ruft ihren Callback genau einmal“ |
| F-1 | offen, Entscheidung des Nutzers | siehe oben |
| F-6, F-9, F-10, F-11, F-12 | unverändert (INFO). F-9 (Zeile `SPEC-040` in `docs/plan/spec-abdeckung.md` „Gate-Festlegung, ungemessen“) bleibt: der Beleg des Gates steht jetzt in diesem Bericht (L-5). F-12 geprüft, siehe nächster Abschnitt. | — |

## Jackson-Aussagen der Spec gegen `YamlTreeReaderTest`

`SPEC-035` (Absatz Verdrahtung) sagt „Gemessen am Adapter mit Jackson 2.21.7“: (a) ein Anker an einem Skalar ohne Alias bleibt für die Strom-API unsichtbar und ohne Wirkung — `Anker an Sammlungen Aliase und Tags werden abgewiesen` lädt `a: &geheim-anker 1\nb: 2` zu zwei Einträgen (Wirkung belegt; die „Unsichtbarkeit“ folgt daraus); (b) ein Anker an Abbildung oder Liste, ein Alias und ein Tag sind Ladefehler — im selben Test, fünf Eingaben, Meldung „Anker, Alias oder Tag nicht erlaubt“, Name des Ankers und Tag stehen nicht in der Meldung, Pfad und Zeile stimmen; (c) ein leerer Wert ohne Anführungszeichen ist ein leerer Text — `schlicht ist nur ein Skalar …` (`k:` ergibt `""`, `plain = false`). Alle drei Aussagen tragen ihr Test; keine ist breiter als die Messung. Eine Spannung zum Absatz „Skalare und Dokument“ („keine Anker, Aliase, Tags“) steht in L-2.

## ADR-Konformität

[ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md): Kotlin-Schnittstelle und Umgebungsvariable (Weg gewählt wie festgelegt), Jackson 2.21.7 mit Strom-API und Zeile/Spalte, Größengrenze vor dem Parsen, Startabbruch vor dem Binden (Test), Wächter für die Bibliothek mit Selbsttest, Lock im Umfang der ADR; ADR im Diff unverändert (`AGENTS.md` §3.5). [ADR-0013](../plan/adr/0013-backend-grenzen.md): Callback und Zeilenquelle sind `suspend`, `Error` wird weitergeworfen, Fehlermeldung enthält nie den Text der Ausnahme; keine Abweichung gefunden.

## Plan-vs-Code-Diff

| Plan (§3 und Erweiterung) | Code | Befund |
|---|---|---|
| `simulation/` update (Weitergabe) | `SimulationChanges.kt`, `SimulationBackend.kt` | wie geplant |
| `runtime/` update (Verdrahtung) | `Bootstrap.kt`, `Main.kt`, `SimulationSetup.kt`, `YamlTreeReader.kt` | wie geplant |
| `referenztest-dotnet/` Szenarien nach `SPEC-040` | `Integration/SimulationBackendTests.cs`, vier Tests | **Abweichung:** vier von fünf Szenarien (Cancel fehlt) |
| `tools/mk/` Make-Fragment, `GATE_CHECKS` nach Entscheidung | `tools/mk/simulationstest.mk`, `GATE_CHECKS += simulations-integration` | wie geplant, Entscheidung für die Aufnahme mit Messung begründet |
| Erweiterung: `config/`, `Dockerfile`, `build.gradle.kts`, Lock, detekt-Wächter, `spec/spezifikation.md`, `harness/README.md` | alle vorhanden | wie in der Erweiterungstabelle genannt; die Fix-Runde berührt zusätzlich `Dockerfile` (8 Zeilen), im Plan nicht gesondert genannt (Stage-Eingänge des Integrationslaufs, ohne eigenen Befund) |

## AGENTS.md §3.6 bis §3.8

- §3.6: keine Schwelle gesenkt, `LongParameterList` unverändert; der Wächter ist eine Verschärfung; kein `@Suppress` im Diff der Fix-Runde (`make detekt` in `make gates` grün).
- §3.7: Kommentare der Fix-Runde tragen Zusage, Kopplung, Abgrenzung oder Grenze im Indikativ (`catalogOf`-KDoc, `readAtMost`-KDoc, Kopf von `simulationstest.mk`); kein Kommentar beschreibt eine verworfene Alternative.
- §3.8: zwei Stellen tragen weiter als ihr Beleg (L-1, L-3).

## Findings

| ID | Kategorie | Befund | Ort | Verifizierbar |
|---|---|---|---|---|
| M-1 | MEDIUM | Der `IOException`-Zweig in `ConfigFiles.readAtMost` hat keinen Test: Mutation C (Zweig entfernt) bleibt grün. Er ist durch die Fix-Runde entstanden — vorher fing er Verzeichnis und fehlende Datei, jetzt laufen beide in den `else`-Zweig von `Files.isRegularFile`; erreichbar bleiben nur eine nicht lesbare reguläre Datei (Rechte) oder ein Fehler beim Lesen. Szenario: Eine Datei ohne Leserecht ergäbe ohne den Zweig eine ungefangene Ausnahme beim Start statt `UNREADABLE`; kein Test merkt es. Dieselbe Klasse wie F-2 des Reviews. | `src/main/kotlin/io/github/pt9912/tdswire/config/ConfigFiles.kt`, `catch (ignored: IOException)` in `readAtMost` | ja (Mutation C) |
| L-1 | LOW | Das KDoc von `readAtMost` sagt „(auch über einen Symlink)“ und „eine FIFO ist `UNREADABLE`, bevor ein Öffnen ohne Frist blockieren kann“; kein Test führt einen Symlink oder eine FIFO aus. Die Aussagen folgen aus `Files.isRegularFile` (folgt Symlinks, FIFO ist keine reguläre Datei), sind aber nicht gemessen (`AGENTS.md` §3.8). | `ConfigFiles.kt`, KDoc von `readAtMost` | ja (Test mit Symlink und FIFO) |
| L-2 | LOW | `SPEC-035` sagt im Absatz „Skalare und Dokument“ „keine Anker, Aliase, Tags und Merge-Schlüssel“ und im Absatz „Verdrahtung“, ein Anker an einem Skalar ohne Alias lade und bleibe ohne Wirkung. Der erste Satz ist breiter als das Verhalten; der Leser muss beide Absätze zusammenlesen. | `spec/spezifikation.md`, `SPEC-035` | nein (Textbefund) |
| L-3 | LOW | „Ein zweiter Sammler derselben Ausführung ruft ihn ein zweites Mal“ (`SPEC-035`, F-8-Fix) ist eine Ableitung aus `flow { … }` ohne Test; die Bedingung „ein einziger Sammler“ ist belegt (KDoc, Tests mit einem Sammler), das Verhalten bei zwei Sammlern nicht. | `spec/spezifikation.md`, `SPEC-035`; `SimulationChanges.kt` | ja (Test mit doppeltem Sammeln) |
| L-4 | LOW | Slice-Plan §1 und §2 (Liefer-Punkt 3) fordern „Cancel mit Folgeabfrage“; `SPEC-040`, Sensor-Zeile und C#-KDoc schließen ihn aus. Kein Wortlaut des Plans nennt die Lücke, kein Carveout und kein Folge-Slice trägt sie. Der Widerspruch löst sich mit der ausstehenden Nutzerentscheidung; bis dahin sagt der Plan mehr als die Spec. Kein Befund über die Entscheidung. | `docs/plan/planning/done/slice-sim-aenderung-integration.md` §1, §2 | nein |
| L-5 | LOW | `docs/plan/spec-abdeckung.md` führt `SPEC-040` weiter als „0 … Gate-Festlegung, ungemessen“, obwohl `make simulations-integration` besteht und in `make gates` läuft (Review F-9, unverändert). Die .NET-Tests tragen keinen `@spec`-Tag. Der Beleg des Gate-Laufs ist dieser Bericht (18 bis 19 s, 4 von 4 Tests, 2026-10-31). | `docs/plan/spec-abdeckung.md` | nein |

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 1 (M-1) |
| LOW | 5 (L-1 bis L-5) |

## Verdikt

Der Code der Liefer-Punkte 1 und 2 erfüllt Plan, Spec und beide ADRs; die Sensoren sind grün und die Mutationen werden bis auf M-1 erkannt. Die Closure ist nicht möglich, solange Liefer-Punkt 3 (Cancel) ohne Entscheidung offen ist und M-1 steht. Übergabe an den Planner: Entscheidung des Nutzers über den Träger des Cancel-Teils (Carveout nach `AGENTS.md` §3.6 oder Folge-Slice), danach Anpassung von Plan §1/§2; M-1 und L-1 bis L-3 an den Implementer.
