# Review-Report: slice-simulation-anweisungsformen-erweitern — 2026-11-21

**Review-Art:** Code, Tests, Listen und Doku — gegen Plan (Slice §1 bis §6), [ADR-0058](../plan/adr/0058-or-in-der-auswahl-ganzzahlkonstante-und-waitfor-delay-sind-erkannt.md) (`Accepted`, Nutzerentscheidung 2026-11-21; Setzungen sind dort gekennzeichnet), [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 11, [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md), [ADR-0054](../plan/adr/0054-antwort-auf-attention-in-zwei-nachrichten.md), [ADR-0055](../plan/adr/0055-zwei-auswahlen-ohne-parameter-in-einem-text-sind-erkannt.md), [ADR-0027](../plan/adr/0027-abbruch-und-zuruecksetzen.md), SPEC-036, SPEC-029, SPEC-045, [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-07](../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe) und die Hard Rules (`AGENTS.md` §3.1 bis §3.8).

**Gegenstand:** Diff `git diff main...HEAD` des Zweigs `slice-simulation-anweisungsformen-erweitern` (HEAD `ee1c9009`; Commits ab `5bc2891f`: [ADR-0058](../plan/adr/0058-or-in-der-auswahl-ganzzahlkonstante-und-waitfor-delay-sind-erkannt.md) `Accepted`, Plan, `b8d33ee1` Code und Tests, `3a332367` Listen und Kompatibilitätstests, `fc7d4ee4` Spec, Messprotokoll, Matrix, Doku, Register, Mutationsbeleg, `ee1c9009` Abdeckungsmatrix), 44 Dateien, Slice [slice-simulation-anweisungsformen-erweitern](../plan/planning/in-progress/slice-simulation-anweisungsformen-erweitern.md).

**Skill:** `.harness/skills/reviewer.md` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-21

**Eingangs-Kontext:** Slice (§1 bis §6), [ADR-0058](../plan/adr/0058-or-in-der-auswahl-ganzzahlkonstante-und-waitfor-delay-sind-erkannt.md) (ganz), [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 11, [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md) Entscheidung 14, [ADR-0054](../plan/adr/0054-antwort-auf-attention-in-zwei-nachrichten.md)/0055/0027 (Auszüge), SPEC-036 (Tabelle, Term, Nicht erkannt, Form C, Form Z), SPEC-029 (Abbruch), [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend).a Schritte 1, 2 und 5, SPEC-045, `AGENTS.md` §3, die Reports `2026-11-19-slice-simulation-spaltenlaenge-in-auswahl*.md`.

**Ausgeführte Sensoren** (Docker, Wegwerf-Kontext im Scratchpad: Kopie von `git archive HEAD` mit einer eigenen Stage `rev` im kopierten `Dockerfile`; nichts davon im Repo; `free -m` available 9 bis 11 GB):

1. `:test --tests 'io.github.pt9912.tdswire.simulation.*' --tests '…runtime.WaitForWireTest'` auf `HEAD` plus ein Wegwerf-Test, der die Erkennung an 23 Randtexten ausgibt: 298 Testfälle grün, einer (der Wegwerf-Test selbst) rot, 64 s; die Texte und ihre Zuordnung stehen unter „Antworten auf die Schwerpunkte“.
2. Vier eigene Mutationen (Nr. R1 bis R4, siehe „Mutationen“), je ein Lauf mit den engsten Testklassen, 70 bis 140 s; davon R3 und R4 sind Wiederholungen von M15 und M4 des Mutationsbelegs des Implementers (Prüfung der gemessenen Wirkung).
3. `make flaeche` Exit 0 (Selbsttest und Prüfung; die öffentliche Typliste ist unverändert).

Nicht gefahren (Tabelle `harness/README.md` §Minimal agent workflow und Auftrag): `make gates` (auf `HEAD` liegen die Stempel `werkzeug` und `tests` als `gruen`: `git notes --ref=refs/notes/gates show HEAD`), `make clean-build`, `make e2e`, `make kompatibilitaet`, `make vergleichslauf-d` (Live- und Mitschnitt-Lauf; EULA und Referenzinstanzen, Verifier), `make spec-abdeckung`.

## Findings

### F-1 — MEDIUM
- `kategorie`: MEDIUM
- `quelle`: Skill „Plan-Tabelle nennt die berührten Dateien nicht“
- `pfad`: `docs/plan/planning/in-progress/slice-simulation-anweisungsformen-erweitern.md` · „Die Tabelle ist vom Implementer um die Dateien ergänzt, die der Bau berührt hat (Stand des Baus 2026-11-21).“ (Zeile 88, Tabelle §3 darüber)
- `befund`: Der Abgleich der Tabelle gegen `git diff --stat main...HEAD` ergibt berührte Dateien ohne Zeile: `BulkForms.kt`, `FixedAnswers.kt`, `SelectExecution.kt`, `Statement.kt` (alle `src/main/…/simulation/`), `FixedFormsTest.kt`, `StatementOracleTest.kt`, `StatementBuilders.kt`, `referenztest-dotnet/Vergleich/LiveSzenarioTests.cs` (die zwei Live-Schritte lesen jetzt Zeilen), `docs/user/referenz-konfiguration.md`, `docs/user/testserver-manuell.md`, `docs/plan/adr/README.md`, `docs/plan/spec-abdeckung.md` und die Register-Dateien. Umgekehrt stehen Dateien mit „update“ in der Tabelle, die der Diff nicht berührt: `SqlScanner.kt`, `AttentionCancelTest.kt`, `CancelTimeoutTest.kt` (die Zeile darunter sagt selbst „bleiben unverändert“), `MitschnittLaufTests.cs`. Der Satz darunter behauptet, die Tabelle sei um die berührten Dateien ergänzt.
- `verifizierbar`: ja (Abgleich Tabelle gegen `git diff --stat main...HEAD`)
- `klasse`: Plan-Tabelle gegen Diff nicht abgeglichen

### F-2 — MEDIUM
- `kategorie`: MEDIUM
- `quelle`: Skill „Eigencode, dessen Entfernung keinen Test rot macht“, Slice §6 (keine Leaks von Coroutinen oder Handles), `AGENTS.md` §3.8
- `pfad`: `src/main/kotlin/io/github/pt9912/tdswire/simulation/WaitExecution.kt` · „flow { if (waited()) emit(QueryEvent.UpdateCount(0)) }.onCompletion { onEnd(this@WaitExecution) }“ und in `close()` „onEnd(this)“; Verdrahtung in `SimulationBackend.kt` · „register(key, WaitExecution(result.delay, pause) { running.remove(key, it) })“
- `befund`: Die Austragung der Ausführung aus `running` (Ende, Fehler, Abbruch, `close()`) ist neu im Diff, und ihre Streichung macht keinen Test rot: Mutation R2 (beide `onEnd`-Aufrufe entfernt) lässt `SimulationBackendWaitTest` (6 Fälle) und `WaitForWireTest` (3 Fälle) grün (9 von 9 grün). Failure-Szenario: ein Server mit vielen `WAITFOR`-Anfragen behielte je Anfrage einen Eintrag `(Sitzung, Anfrage) → Ausführung` im `ConcurrentHashMap` des Backends (auch nach dem Ende der Sitzung); kein Test sieht das. Dasselbe Muster haben `SelectExecution` und `ChangeExecution` (dort nicht Teil des Diffs); der Slice nennt „keine Leaks“ als Prüfpunkt, der Mutationsbeleg führt die Mutation nicht.
- `verifizierbar`: ja (Mutation R2, siehe unten)
- `klasse`: Aufräumen einer Registrierung ohne beobachtenden Test

### F-3 — MEDIUM
- `kategorie`: MEDIUM
- `quelle`: `AGENTS.md` §3.8 (Aussage trägt höchstens so weit wie ihr Beleg), Skill „Aussage breiter als ihr Beleg“; Plan §2 DoD (Läufe sind Sache des Verifiers)
- `pfad`: `spec/spezifikation.md` · „und die vier Szenarien `WAITFOR` des\nMitschnitt-Laufs spielen den Abbruch eines wartenden Befehls am Server ab.“ (SPEC-029, Zeilen 647 bis 649) und der Änderungsprotokoll-Eintrag 2026-11-21 · „A-4, A-5 und A-15 entfallen, A-17 ist so gewollt“ (Zeile 2459)
- `befund`: Beide Stellen stehen im Indikativ des erreichten Zustands. Belegt ist nur der Bau und der Wire-Test mit gesteuerter Uhr (`WaitForWireTest`); `make vergleichslauf-d LAUF=mitschnitt` und `LAUF=live` sind nicht gelaufen, und die Server-Spalten der verkleinerten Regeln (`DONE || DONE`) sind aus dem Bau vorhergesagt, wie es das Messprotokoll (Abschnitt „Anweisungsformen erweitert“, „Nicht gelaufen“) und die Matrix („noch nicht gemessen“) selbst sagen. Die Spezifikation trägt diese Bedingung nicht im selben Satz; ein roter Lauf mit anderer Abweichung machte „spielen … ab“ und „entfallen“ falsch, bevor jemand es gemessen hat.
- `verifizierbar`: ja (`make vergleichslauf-d LAUF=mitschnitt` und `LAUF=live`, Verifier)
- `klasse`: Zusage ohne ihre gemessene Bedingung im selben Satz (Zustand vor dem Lauf als erreicht geschrieben)

### F-4 — LOW
- `kategorie`: LOW
- `quelle`: `AGENTS.md` §3.7 und §3.8; [ADR-0058](../plan/adr/0058-or-in-der-auswahl-ganzzahlkonstante-und-waitfor-delay-sind-erkannt.md) Entscheidung 4 („als Teil von S2 oder einer anderen Folge nicht erkannt“) und Entscheidung 5 („einziger Text“)
- `pfad`: `src/main/kotlin/io/github/pt9912/tdswire/simulation/BulkForms.kt` · „jede andere Form ist nicht erkannt. Eine“ (KDoc von `metadataOnly`) und `StatementParser.kt` · „BulkForms.probe(tokens, ::parse) ?: BulkForms.insertBulk(tokens)“
- `befund`: Setzung des Implementers, die [ADR-0058](../plan/adr/0058-or-in-der-auswahl-ganzzahlkonstante-und-waitfor-delay-sind-erkannt.md) nicht deckt (Auftrag Punkt 7): `SET FMTONLY ON SELECT 2` ist als Form C erkannt und liefert die Zeile 2 (gemessen mit dem Wegwerf-Test: `Fixed(IntegerConstant(2))`), obwohl der Text derselben Anweisung dahinter „nie eine Zeile“ liefern soll (KDoc der Klasse) und [ADR-0058](../plan/adr/0058-or-in-der-auswahl-ganzzahlkonstante-und-waitfor-delay-sind-erkannt.md) Entscheidung 4 eine Folge ausschließt; `SET FMTONLY ON WAITFOR DELAY '00:00:10'` dagegen ist ausdrücklich abgewiesen (`recognized is Recognition.Wait -> NotRecognized`). Der Satz „jede andere Form ist nicht erkannt“ im geänderten KDoc stimmt weder für `Fixed` noch für `Catalog` oder `Bulk` (`recognized !is Recognition.Recognized -> recognized` reicht sie durch); er stand vor dem Slice mit `SELECT 1` ebenso da, ist aber Teil der geänderten Zeilen. Die Spezifikation (Form Z) nennt „hinter `SET FMTONLY ON`“ als nicht erkannt, zu `SELECT <Ganzzahl>` dahinter schweigt sie.
- `verifizierbar`: ja (Test `recognize("SET FMTONLY ON SELECT 2")`)
- `klasse`: Setzung des Implementers ohne Entscheidungsgrundlage in der ADR, KDoc breiter als der Code

### F-5 — LOW
- `kategorie`: LOW
- `quelle`: `AGENTS.md` §3.7 (Zustandsfelder: Zustand und Beleg, nicht die Chronik)
- `pfad`: `referenztest-dotnet/Vergleich/mitschnitt-ausnahmen.tsv` · `gleichen sich Fehler und Tokenfolge sonst, A-15 entfaellt` (Grund der vier Regeln `…/01/tokenfolge@2022`, Zeilen 10, 12, 16 und 20)
- `befund`: Der Grund nennt den Zustand, den es nicht mehr gibt („A-15 entfaellt“, „gleichen sich … sonst“), statt den bleibenden Grund (A-18, SESSIONSTATE) allein; der Nachsatz erzählt, wie die Regel entstand und was mit dem Bau wegfiel. Die Prüfung der Liste vergleicht die Ergebnis-Spalten wortgleich, nicht den Grund; die vier Regeln `…/02/tokenfolge@2022` tragen denselben Grund A-18 ohne den Nachsatz.
- `verifizierbar`: nein
- `klasse`: Chronik im Grund einer Regel der Ausnahmeliste

### F-6 — LOW
- `kategorie`: LOW
- `quelle`: Skill „Test-Naht im Produktivcode“ (Prüfung: `internal`, KDoc nennt sie eine Naht und sagt, was sie belegt)
- `pfad`: `src/main/kotlin/io/github/pt9912/tdswire/simulation/SimulationBackend.kt` · „Wartet die Dauer von `WAITFOR DELAY` ab; die Voreinstellung ist `delay`, Tests setzen eine gesteuerte Uhr.“
- `befund`: Der Parameter `pause` des internen Primärkonstruktors hat keinen Produktnutzen (der öffentliche Konstruktor setzt `delay` fest) und ist damit eine Test-Naht dieser Art. Er ist `internal` (erfüllt), das KDoc nennt ihn aber nicht eine Naht und sagt nicht, dass sie die Reaktion der Klasse auf Ablauf und Cancel belegt, nicht dass ein echtes Ereignis sie auslöst; der Test der Voreinstellung (`die Voreinstellung der Uhr wartet wirklich und der Cancel beendet sie`, rot bei M17) trägt die echte Uhr.
- `verifizierbar`: nein
- `klasse`: Test-Naht ohne Kennzeichnung im KDoc

### F-7 — INFO
- `kategorie`: INFO
- `quelle`: [ADR-0058](../plan/adr/0058-or-in-der-auswahl-ganzzahlkonstante-und-waitfor-delay-sind-erkannt.md) Entscheidung 4 (Setzung), `AGENTS.md` §3.8
- `pfad`: `src/test/kotlin/io/github/pt9912/tdswire/simulation/FixedFormsTest.kt` · „assertFixed(FixedForm.IntegerConstant(Int.MIN_VALUE), "SELECT -2147483648")“; Setzungen `SELECT 01` und `SELECT -0` im selben Test
- `befund`: Auftrag Punkt 7: `SELECT 01` (Wert 1, nicht `Probe`) und `SELECT -0` (Wert 0) sind erkannt, [ADR-0058](../plan/adr/0058-or-in-der-auswahl-ganzzahlkonstante-und-waitfor-delay-sind-erkannt.md) nennt sie nicht; der Wortlaut „Ziffern mit optionalem führendem `-`“ trägt sie, die Spezifikation und die ADR sagen „führende Nullen“ und „-0“ nirgends, nur der Test hält sie fest. Offen bleibt die Grenze: nach meiner Kenntnis von T-SQL (nicht gemessen) ist `-2147483648` kein `int`-Literal, sondern die Negation von `2147483648` (Typ `numeric`); ADR und Spec setzen die Grenze „Bereich von `Int32`“ ohne Messung („der Typ der Konstante ist dort nicht gemessen“), der Test hält `Int.MIN_VALUE` als `Int32` fest. Zuständig: Architect, Nutzer (Setzung der ADR); der Re-Evaluierungs-Trigger der ADR („Messung … zu einer Ganzzahl außerhalb von `Int32`“) fängt es.
- `verifizierbar`: nein (keine Messung am echten Server im Repo)
- `klasse`: Rand einer Setzung nicht benannt

### F-8 — INFO
- `kategorie`: INFO
- `quelle`: Skill „Mutationsbeschreibung nennt die gedachte statt der gemessenen Wirkung“
- `pfad`: `docs/messungen/mutationen-anweisungsformen-2026-11-21.md` · „M4“ und „M15“ (Abschnitt „Lesarten und Lücken“)
- `befund`: Die zwei benannten Lücken sind akzeptabel und ihre Wirkung stimmt mit der Messung überein (R3 und R4 unten wiederholen M15 und M4: 4 von 6 und 1 von 36 rot, dieselben Testfälle). M4: `OR` in `UPDATE`/`DELETE` entsteht allein im Parser (`Parts.whereClause`); eine Mutation dort färbt `StatementFormsTest` rot; der Backend-Test bleibt grün, weil ohne Callback jede Änderung abgewiesen wird; ein Backend-Test mit gesetztem `onUpdate`, der „kein Aufruf“ prüft, fehlt (die Folge, die [ADR-0058](../plan/adr/0058-or-in-der-auswahl-ganzzahlkonstante-und-waitfor-delay-sind-erkannt.md) Entscheidung 3 nennt, ist „ein Callback ändert die falschen Zeilen“). M15: `stop()` ohne Wirkung färbt den Wire-Test nicht, weil der Protokollkern die Coroutine des Laufs abbricht; die Zusage `cancel()` trägt der Backend-Test. Kein Befund; zur Kenntnis für den Verifier.
- `verifizierbar`: ja (R3, R4)
- `klasse`: Benannte Lücke bestätigt

### F-9 — INFO
- `kategorie`: INFO
- `quelle`: [SPEC-059](../../spec/spezifikation.md#einstieg-des-integrators-vertrag-zu-spec-059), Skill „Mitglieder eines Typs der Liste in `SPEC-059` ohne Eintrag im Änderungsprotokoll“
- `pfad`: `src/main/kotlin/io/github/pt9912/tdswire/simulation/SimulationBackend.kt` · „public class SimulationBackend internal constructor(“
- `befund`: Der öffentliche Konstruktor `(catalog, onStructure = null)` ist jetzt ein Sekundärkonstruktor mit derselben Kotlin-Signatur; `make flaeche` ist grün (Typnamen). Der Eintrag im Änderungsprotokoll vom 2026-11-21 sagt zur Fläche nichts (die vorigen Einträge nennen „keine neue öffentliche Fläche“ ausdrücklich). Ein `internal`-Konstruktor ist in der JVM nach meiner Kenntnis des Kotlin-Verhaltens ein öffentlicher Konstruktor `(Function0, Function1, Function2)`, aus Java aufrufbar (nicht gemessen); die Kotlin-Fläche ändert sich nicht. Kein Befund, der Eintrag im Protokoll kann den Satz „Fläche unverändert“ tragen.
- `verifizierbar`: ja (`make flaeche`, Lesen des Bytecodes)
- `klasse`: Konstruktorumbau ohne Vermerk zur Fläche

### F-10 — INFO
- `kategorie`: INFO
- `quelle`: Skill „Roter Lauf … Wanduhr“ (hier nicht ausgelöst), Maintainability
- `pfad`: `src/test/kotlin/io/github/pt9912/tdswire/simulation/SimulationBackendWaitTest.kt` · „ (`ManualPause`), kein Test wartet auf die Wanduhr.“ (Zeile 21)
- `befund`: Die zweite Zeile des KDoc-Kopfes beginnt ohne ` * `; das Format wird nicht beanstandet (Gate grün). Die Wächter-Sätze der neuen Tests („dann bliebe die Uhr stehen“, „dann hinge der Befehl“) stehen im Konjunktiv über den abwesenden Zustand; der Idiom ist im Bestand üblich (313 Treffer `Wächter` unter `src/test` auf `main`) und benennt die Mutation, die der Test fängt; kein Befund nach dem Skill-Punkt, wenn der Planner den Idiom nicht ändern will.
- `verifizierbar`: nein
- `klasse`: Formatierung und Idiom ohne Konventions-Anker

### F-11 — INFO
- `kategorie`: INFO
- `quelle`: `AGENTS.md` §3.8, SPEC-029, SPEC-044
- `pfad`: `spec/spezifikation.md` · „eine wartende Anfrage wird beim geordneten Beenden nach `SHUTDOWN_GRACE` wie jede laufende\nabgebrochen (`SPEC-044`)“ (Absatz Form Z)
- `befund`: Der Wettlauf ATTENTION gegen Ende der Wartezeit („der erste Übergang gewinnt“) und das Beenden mit einer wartenden Anfrage sind in der Spezifikation für Form Z genannt; kein Test des Slice belegt sie für `WAITFOR` (`WaitForWireTest` hat drei Fälle: ATTENTION, `QUERY_TIMEOUT`, Ende). Beide Aussagen folgen dem Protokollkern und sind dort getestet (`ShutdownDispatchTest`, `AttentionCancelTest`); für das Backend `WaitExecution` ist der Pfad nicht eigens gefahren. Hinweis für den Verifier, kein Befund.
- `verifizierbar`: ja (Test mit gesteuerter Uhr, Wettlauf)
- `klasse`: Aussage über geerbtes Verhalten ohne eigenen Test

## Mutationen

Gefahren (Einzelmessung 2026-11-21, ein Host, je Mutation ein Gradle-Lauf in Docker mit den engsten Testklassen; das Skript liegt im Scratchpad, nicht im Repo; die Zahlen sind Einzelbeobachtungen, Last nicht erhoben):

| Nr | Mutation (Datei) | Klassen | Ergebnis |
|---|---|---|---|
| R1 | `WaitExecution.kt`: `waiting.cancel()` entfernt | `SimulationBackendWaitTest`, `WaitForWireTest` | rot, 2 von 9: `cancel beendet die Wartezeit sofort und ohne Ereignis`, `der Cancel trifft nur die eigene Anfrage`; der Wire-Test bleibt grün |
| R2 | `WaitExecution.kt`: `.onCompletion { onEnd(…) }` und `onEnd(this)` in `close()` entfernt | wie R1 | **grün, 9 von 9** (F-2) |
| R3 | `WaitExecution.kt`, `stop()`: `Unit` statt `stopped.complete(Unit)` (= M15) | wie R1 | rot, 4 von 9 (also 4 von 6 in `SimulationBackendWaitTest`, Wire-Test 3 von 3 grün): `die Voreinstellung der Uhr wartet wirklich und der Cancel beendet sie`, `Abbruch des Sammlers und close beenden die Wartezeit`, `cancel beendet die Wartezeit sofort und ohne Ereignis`, `der Cancel trifft nur die eigene Anfrage`; stimmt mit M15 des Belegs überein |
| R4 | `StatementParser.kt`, `Parts.whereClause` (`UPDATE`, `DELETE`): `while (acceptKeyword("AND") \|\| acceptKeyword("OR"))` (= M4) | `StatementFormsTest`, `SimulationBackendDisjunctionTest` | rot, 1 von 36: `lehnt OR ausserhalb der reinen Folge in S1 ab`; `SimulationBackendDisjunctionTest` bleibt grün; stimmt mit M4 des Belegs überein |

Gelesen, nicht gefahren: M1 bis M3, M5 bis M14, M16 bis M21 des Belegs; ihre Beschreibung nennt Datei, Änderung und rote Testfälle und passt zu meiner Lesung von Code und Tests (M16 hängt bis zur Frist des Testhelfers, M17 rot am Test der Voreinstellung, M20 `WaitFormsTest > WAITFOR DELAY hat keine Abwandlung` trägt den Text `SET FMTONLY ON WAITFOR DELAY …`). Der Beleg nennt den Erzeuger als nicht im Repo (Wegwerf-Skript) und die Bedingungen; das genügt dem Skill-Punkt zur Herkunft von Messzahlen.

## Antworten auf die Schwerpunkte des Auftrags

1. **Semantik `OR`.** `Parts.selectWhere` erkennt eine reine Folge (`disjunction` nur, wenn der erste Term kein Konstantenterm ist und das nächste Token `OR` ist; die Schleife nimmt danach nur `OR`). Wegwerf-Probe: `a = 1 OR a = 2 OR a = 3` erkannt (`disjunction=true`), `a = 1 AND a = 2 OR a = 3` und `a = 1 OR a = 2 AND a = 3` abgewiesen (`UNEXPECTED_TOKEN`), ebenso Klammer, Konstantenterm vorn und hinten, ein einzelnes `OR`, `NOT`, `<`, `LIKE`, `BETWEEN`, `<>`, `!=`, `NOT IN` (Tests `lehnt OR ausserhalb der reinen Folge in S1 ab` mit 25 Texten, davon `UPDATE`/`DELETE` und `INSERT`). Auswertung: `RowCondition.matches` nimmt bei `disjunction` `terms.any`; ein `NULL` in der Zelle trifft in `In` nie, `IsNull` ist nie unbekannt, `NULL` in `wanted` trifft nie: mit `OR` ohne `NOT` ist das Ergebnis zwei- und dreiwertig gleich (abgeleitet, richtig). `never` gilt nur für UND (ein Konstantenterm kommt in einer `OR`-Folge nicht vor); hinter `SET FMTONLY ON` wird die Folge mit `disjunction=false` zur UND-Folge mit `Constant(false)` (Wegwerf-Probe, beide Wege: Text und `sp_executesql`; Spalten und Werte werden weiter geprüft; Test `OR hinter FMTONLY liefert Metadaten ohne Zeile`, rot bei M7). Der Vorfilter `SimQuery.filter` ist bei `OR` `null` (Test `OR gibt der Zeilenquelle keinen Vorfilter`, rot bei M2). S2: jede Auswahl mit eigener Folge (`S2 erlaubt je Auswahl eine OR-Folge`). `OR` ist nur S1 (M4/R4, F-8).
2. **`SELECT <Int32>`.** Wegwerf-Probe: `SELECT 2147483647` und `-2147483648` erkannt, `2147483648` und `-2147483649` abgewiesen (Test), `SELECT 01` → Wert 1 als `IntegerConstant` (nicht `Probe`), `SELECT -0` → 0, `SELECT 00` → 0, `SELECT - 2` abgewiesen (`UNSUPPORTED_CHARACTER`), `SELECT 2 ;` und `SELECT 1 ;` erkannt, `SELECT 1` bleibt `Probe`; Alias, `FROM`, Folge, `1, 2`, `1.5`, `'a'`, `NULL`, `@p`, `@@VERSION`, `1 + 1`, `1-1` abgewiesen (`SELECT mit einer Ganzzahl hat keine Abwandlung`, 29 Texte). Randfälle: F-4 (`SET FMTONLY ON SELECT 2`), F-7 (`01`, `-0`, `-2147483648`).
3. **`WAITFOR`.** `WaitExecution` wartet mit `pause` in einem `async` unter `coroutineScope`, wählt zwischen Ende und `stopped`, bricht das Warten danach ab (R1: ohne `waiting.cancel()` werden zwei Cancel-Tests rot). Cancel vor dem Sammeln und `close()` lassen die Ausführung leer enden; der Abbruch des Sammlers bricht die Wartezeit ab (Test, M21). Fristform: `hh` 00 bis 23, `mm` und `ss` 00 bis 59, ASCII-Ziffern, ohne `N`, einziger Text, ein `;` (Test mit 37 Abwandlungen, darunter Unicode-Ziffern, Bruchteil, Variable, Folge, `SET FMTONLY ON WAITFOR …`). `QUERY_TIMEOUT` kürzer als die Wartezeit → ERROR 50005 und `DONE[ERROR]` (`WaitForWireTest`, gesteuerte Uhren), ATTENTION → Paar `DONE_ERROR` (`0x00fd`) und `DONE_ATTN` mit den Handvektoren, danach beantwortet die Verbindung `SELECT 1`; die Uhr des Backends ist nach beiden Abbrüchen abgebrochen (`isWaiting` falsch). Leaks: F-2 (Registrierung in `running`). Wettlauf und Beenden: F-11. Primärkonstruktor `internal`, öffentlicher Konstruktor unverändert in Kotlin, `make flaeche` Exit 0 (F-9, F-6).
4. **Ausnahmelisten.** Gezählt: `mitschnitt-ausnahmen.tsv` 26 → 18 Regeln, `live-ausnahmen.tsv` −2; entfallen sind genau die acht Regeln `…/01/fehler` und `…/01/tokenfolge@2025` der vier `WAITFOR`-Szenarien; `…/01/tokenfolge@2022` bleibt mit Server-Spalte `DONE || DONE` und Grund A-18 (die Referenz-Spalte `SESSIONSTATE DONE || DONE` unverändert), die `…/02/…@2022` und `cancel-streaming/…@2022` bleiben. Die sechs A-17-Regeln tragen Art *so gewollt* und je Szenario einen eigenen Grund: `batch-statement-abbrechend` „die erste Anweisung enthält einen Ausdruck“ (wahr: `SELECT 1/0; SELECT 2`), `batch-batch-abbrechend` „Folge aus einer Auswahl und einer Konstantenauswahl, kein Ausdruck“ (wahr; Probe: `SELECT a FROM t WHERE …; SELECT 2` und `SELECT 2; SELECT a FROM t …` sind `NotRecognized`); die Server-Spalten dieser sechs sind unverändert. Die Server-Spalte `DONE || DONE` der vier verkleinerten Regeln ist vorhergesagt (F-3, F-5). Der Kopf der Datei nennt die Selbsttest-Bedingung wortgleich; `make skript-selbsttest` ist über den Stempel `werkzeug` grün. Die Texte der Kompatibilitätstests (`SELECT 1 + 1` statt `SELECT 2`) stimmen mit der Matrix überein; mit dem neuen Text am Referenzclient ist nichts gemessen (die Matrix sagt es).
5. **Tests und Mutationsbeleg.** Jede Zusage der ADR trägt einen Test (Happy, Grenze, Negativ je Form, Orakel mit `OR`-Erzeuger und `gen.disjunctions >= 20`); der Beleg führt 21 Mutationen mit Wirkung je Testfall, die ich in vier Fällen (R1 bis R4, davon M4 und M15 wörtlich) wiederholt habe, ohne Abweichung. Die benannten Lücken M4 und M15 sind akzeptabel (F-8); nicht gefahren und nicht benannt ist die Entfernung der Registrierung (F-2).
6. **Spec-, Doku- und Register-Nachzug.** Zweite Fundstellen der alten Lesart: Suche über das Repo (ohne `docs/reviews`, `done/`, `.harness`, ADRs) nach „abschließend“, „nicht bestätigt“, „Offen 12“, „SELECT 1“, `OR` in den Nutzer-Seiten: die Fundstellen in `spec/spezifikation.md` (Schritte 1, 2 und 5, Term, Nicht erkannt, Form C), den KDocs (`StatementParser.kt`, `FixedForms.kt`, `FixedAnswers.kt`, `SimulationBackend.kt`), `docs/user/` (drei Dateien), Matrix und Messprotokoll sind nachgezogen; die übrigen Treffer sind andere Gegenstände (Dezimalkomma, Login). Das `Stand`-Feld der Register nennt Zustand und Beleg als Anker (kein Chronik-Befund); `lesart-einer-nutzerentscheidung-unbestaetigt` führt „offen (19×)“ weiter, wie der Text sagt. Nutzer-Doku: alle drei geänderten Dateien gelesen, Treffer auf die Wörter des Skills nur in alten Stellen (`Soll`, `bisher erzeugte Token`, `gemessen, nicht gebaut` zu SPEC-031); keine neue Aussage über Abwesendes oder Geplantes. §3.8: F-3, F-7.
7. **Setzungen des Implementers, die [ADR-0058](../plan/adr/0058-or-in-der-auswahl-ganzzahlkonstante-und-waitfor-delay-sind-erkannt.md) nicht deckt.** `SELECT 01` und `SELECT -0`: vom Wortlaut „Ziffern mit optionalem führendem `-`“ getragen, aber nirgends benannt (F-7). `WAITFOR` hinter `SET FMTONLY ON` abgewiesen: von „einziger Text“ getragen, in der Spezifikation benannt und mit M20 belegt, doch ungleich zu `SELECT <Ganzzahl>` dahinter, das erkannt wird (F-4). Beide ohne Änderung gemeldet.

## Geprüft, ohne Befund

- `src/main/kotlin/io/github/pt9912/tdswire/simulation/` (`SimConditions.kt`, `StatementParser.kt`, `FixedForms.kt`, `FixedAnswers.kt`, `WaitForms.kt`, `Statement.kt`, `StatementPlanner.kt`, `SelectExecution.kt`): Parser, Auswertung (`any` gegen `all`), Vorfilter `null`, Konstantenterm, FMTONLY-Umwandlung und die Planung von Form Z ohne Befund außer F-2, F-4, F-6; keine Schicht- oder Importregel berührt (`kotlinx.coroutines` in `simulation` wie bisher), kein `@Suppress`, `@SuppressWarnings`, `noqa`, `nolint` im Diff (Grep über den Diff), kein Host-Aufruf, `config/`, `Dockerfile`, `Makefile`, `tools/`, `build.gradle.kts` unberührt (`git diff --stat`).
- `src/test/kotlin/…/simulation/` und `…/runtime/WaitForWireTest.kt`: Happy, Grenze und Negativ je Zusage; die gesteuerten Uhren (`ManualPause`, `SteeredPause`) lassen keinen Test die Wanduhr abwarten (Grep in den vier neuen Testdateien: kein `sleep`, kein `Thread.`, kein `delay(`; die echte Uhr läuft nur im Test der Voreinstellung und wird sofort durch Cancel beendet); keine Eingabe ist einheitlich in einer Auswahl-/Gleichheitseigenschaft (die `OR`-Tests mischen Terme, Spalten und `NULL`, S2 mit je anderer Folge).
- `referenztest-dotnet/` (`SzenarienTests.cs`, `SynchronitaetTests.cs`, `LiveSzenarioTests.cs`, beide `.tsv`): nur die oben genannten Änderungen; die Live-Schritte lesen Spaltentyp und Werte (`Abfrage`), wie [ADR-0058](../plan/adr/0058-or-in-der-auswahl-ganzzahlkonstante-und-waitfor-delay-sind-erkannt.md) Konsequenzen es verlangt.
- `docs/plan/adr/`: `0058-…md` seit `5bc2891f` unverändert; keine `Accepted`-ADR inhaltlich geändert (`git diff main...HEAD -- docs/plan/adr` nur der Index und die neue ADR); der Index vermerkt [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md) und [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) als teilweise ersetzt (wörtlich benannt in der ADR). §3.5 ohne Befund.
- `docs/plan/planning/in-progress/…slice….md`: kein `- [x]` im Diff (alle Haken offen), §7 „Wird bei der Closure gefüllt“, Risiken tragen die Ausgänge „offen bis zur Closure“; Kopf „Berührte Spec-Stellen“ stimmt mit dem Diff (SPEC-036, [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend).a, SPEC-045, SPEC-029) überein.
- `docs/plan/spec-abdeckung.md`: Zähler passen zu den neuen Tests (SPEC-029 +6, SPEC-036 +22, SPEC-009 +1, SPEC-016 +1, SPEC-037/038/039 +2/+1/+1); Format unverändert; kein roter Test (Erzeuger-Lauf nicht von mir wiederholt).
- Hard Rules §3.1, §3.2, §3.4 (`spec/architecture.md` unberührt), §3.5, §3.6: ohne Befund. §3.7 und §3.8: F-3 bis F-7.

## Urteil

Kein HIGH. Drei MEDIUM (F-1 Plan-Tabelle gegen den Diff nicht abgeglichen, F-2 die Austragung der Ausführung aus `running` ohne Test, F-3 Zusagen zum Abspielen der Mitschnitt-Szenarien vor dem Lauf im Indikativ), drei LOW (F-4, F-5, F-6), fünf INFO (F-7 bis F-11). Die Semantik der drei Formen folgt [ADR-0058](../plan/adr/0058-or-in-der-auswahl-ganzzahlkonstante-und-waitfor-delay-sind-erkannt.md) (reine `OR`-Folge nur in S1, Vorfilter `null`, `Int32`-Konstante ohne Alias und Folge, `WAITFOR DELAY` als einziger Text mit Cancel, Frist und Abbruch des Sammlers); die Tests tragen die Zusagen, und vier von mir wiederholte Mutationen zeigen dieselbe Wirkung wie der Beleg (R3, R4) oder bestätigen den Wert der Tests (R1). Die Befunde liegen an der Plan-Tabelle, an einer nicht beobachteten Aufräum-Zeile und an der Reichweite von Aussagen, die erst der Lauf des Verifiers belegt.

**Empfehlung:** Nachbesserung der Doku und des Plans (F-1, F-3, F-5, F-6 sind Text; F-2 braucht eine Entscheidung des Planners, ob ein Test die Registrierung beobachtet oder der Beleg die Lücke benennt; F-4 und F-7 sind Setzungen, die der Architect oder der Nutzer in ADR oder Spezifikation benennt, nicht der Reviewer ändert); danach weiter an den Verifier mit `make clean-build`, `make e2e`, `make kompatibilitaet` und den Läufen `make vergleichslauf-d LAUF=live` und `LAUF=mitschnitt` (die vorhergesagten Server-Spalten der vier verkleinerten Regeln und der beiden Live-Schritte belegt erst dieser Lauf). Ein Re-Review nur, wenn die Nachbesserung Code oder Tests ändert (Skill „Nachbesserung mit Code ohne zweiten Review“).
