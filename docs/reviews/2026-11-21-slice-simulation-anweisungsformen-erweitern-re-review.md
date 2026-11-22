# Re-Review: slice-simulation-anweisungsformen-erweitern — Nachbesserung nach dem Review vom 2026-11-21

**Review-Art:** Re-Review der Nachbesserung (Skill „Nachbesserung mit Code ohne zweiten Review“): nur die Commits nach dem Report-Stand, Diff `2adce6f0..6d002217`, gegen Plan, [ADR-0058](../plan/adr/0058-or-in-der-auswahl-ganzzahlkonstante-und-waitfor-delay-sind-erkannt.md) und die Hard Rules (`AGENTS.md` §3.1 bis §3.8). Nicht bewertet: der Doku-Commit `34e7be7e` zu F-4 und F-7 (Nutzerentscheid 2026-11-21, parallel durch einen Implementer) und Änderungen im Arbeitsbaum danach.

**Erstreview:** [2026-11-21-slice-simulation-anweisungsformen-erweitern.md](2026-11-21-slice-simulation-anweisungsformen-erweitern.md) (Commit `2adce6f0`; 0 HIGH, 3 MEDIUM, 3 LOW, 5 INFO).

**Skill:** `.harness/skills/reviewer.md` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-22 (Dateiname nach dem Auftrag)

**Gegenstand:** `0917eee2` (interne Test-Naht `SimulationBackend.registrations`, Test „die Registrierung endet mit Ablauf Cancel Abbruch und close“ in `SimulationBackendWaitTest.kt`, KDoc von `pause`), `18fcca07` (KDoc von `metadataOnly`, Grund der vier Regeln `tokenfolge@2022` in `mitschnitt-ausnahmen.tsv`), `6d002217` (Plan-Tabelle, SPEC-029, Änderungsprotokoll, Mutationsbeleg M22 bis M25, `spec-abdeckung.md`). 8 Dateien, davon Code: `SimulationBackend.kt`, `BulkForms.kt` (nur KDoc), `SimulationBackendWaitTest.kt`.

**Ausgeführte Sensoren** (Docker, Wegwerf-Kontext im Scratchpad: das vorhandene Deps-Image `anweisungsformen-deps:lokal` mit `COPY` der Quellen und einer Mutation per `sed` im Container; nichts davon im Repo, der Arbeitsbaum blieb unverändert; `free -m` available 9,5 bis 13 GB):

1. Ausgangslauf ohne Mutation: `*SimulationBackendWaitTest` und `*WaitForWireTest`, 10 Testfälle grün, 83 s.
2. Fünf Mutationen (siehe unten), je ein Lauf mit denselben zwei Klassen, zwei Läufe gleichzeitig, 64 bis 89 s je Lauf.
3. `make flaeche` Exit 0 (Selbsttest und Prüfung).
4. Abgleich der Plan-Tabelle gegen `git diff --name-only main...HEAD` (Skript im Scratchpad) und Grep nach der alten Lesart in `spec/`, `docs/`, `src/`, `referenztest-dotnet/`.

Nicht gefahren (Tabelle `harness/README.md` §Minimal agent workflow und Auftrag): `make gates` (auf `6d002217` liegen die Stempel `werkzeug` und `tests` als `gruen`: `git notes --ref=refs/notes/gates show 6d002217`), `make clean-build`, `make e2e`, `make kompatibilitaet`, `make vergleichslauf-d`, `make spec-abdeckung` (der Zähler SPEC-029 53 passt zum einen neuen `@spec SPEC-029`).

## Stand der Findings des Erstreviews

| Finding | Stand | Beleg |
|---|---|---|
| F-1 MEDIUM Plan-Tabelle | behoben | Abgleich unten |
| F-2 MEDIUM Austragung ohne Test | behoben | Mutationen R2, M22 bis M25, R5 rot |
| F-3 MEDIUM SPEC-029 im Indikativ des erreichten Zustands | behoben | Wortlaut unten |
| F-4 LOW `SET FMTONLY ON SELECT 2` | nicht Gegenstand (Nutzerentscheid, Doku-Commit folgt) | nicht bewertet |
| F-5 LOW Chronik im Grund der Regeln | behoben | Zeilen 10, 12, 16, 20 der Liste |
| F-6 LOW Naht ohne Kennzeichnung | behoben | KDoc von `pause` |
| F-7 INFO Rand einer Setzung | nicht Gegenstand (wie F-4) | nicht bewertet |
| F-8, F-10, F-11 INFO | unverändert, keine Aktion erwartet | — |
| F-9 INFO Fläche im Änderungsprotokoll | behoben | Eintrag 2026-11-21 nennt den öffentlichen Konstruktor und `make flaeche` |

### F-1 (Plan-Tabelle gegen den Diff)

Abgleich der Tabelle in Slice §3 gegen `git diff --name-only main...HEAD` (nach dem Namen der Datei): jede berührte Datei steht in der Tabelle, außer dem Plan selbst, den drei Belegen unter `observations/BEO-TDS/…/evidence/` (die Zeile `docs/plan/planning/observations/BEO-TDS/` nennt sie als Verzeichnis) und dem Report des Reviews (der Satz unter der Tabelle nimmt `docs/reviews/` aus). Umgekehrt stehen `SqlScanner.kt`, `AttentionCancelTest.kt`, `CancelTimeoutTest.kt` und `MitschnittLaufTests.cs` nur noch im Satz „bleiben unverändert“ beziehungsweise „stehen im Plan nicht mehr“, nicht als `update`; der Diff berührt sie nicht (bestätigt). Die Dateien des Erstreviews (`BulkForms.kt`, `FixedAnswers.kt`, `SelectExecution.kt`, `Statement.kt`, `FixedFormsTest.kt`, `StatementOracleTest.kt`, `StatementBuilders.kt`, `LiveSzenarioTests.cs`, `referenz-konfiguration.md`, `testserver-manuell.md`, `adr/README.md`, `spec-abdeckung.md`, Register) stehen jetzt mit Begründung in der Tabelle. Der Kopf „Berührte Spec-Stellen“ (SPEC-036, `TDS-FA-11.a`, SPEC-045, SPEC-029) deckt die Spec-Änderungen des Diffs; die Nachbesserung berührt nur SPEC-029 und das Änderungsprotokoll. Der Satz „der Abgleich gegen den Diff ist am Ende von Bau und Nachbesserung gelaufen“ stimmt mit meinem Abgleich überein. Das Zustandsfeld nennt keinen Haken (`- [x]` kommt im Diff nicht vor).

### F-3 (SPEC-029 und Änderungsprotokoll)

SPEC-029 sagt jetzt: die vier Szenarien `WAITFOR` „sollen den Abbruch eines wartenden Befehls am Server abspielen; belegt ist das Paar durch den Test mit gesteuerter Uhr, die Läufe `make vergleichslauf-d` mit `LAUF=mitschnitt` und `LAUF=live` gegen den Server sind nicht gelaufen, die Server-Spalten der verkleinerten Regeln der Ausnahmeliste sind aus dem Bau vorhergesagt“. Das Änderungsprotokoll trennt „aus den Ausnahmelisten genommen“ (Zustand der Listen, wahr) von „ob der Server die Referenz dort trifft, belegt erst der Lauf … der aussteht“. Beide Stellen tragen die Bedingung im selben Satz und gehen nicht weiter als der Beleg (§3.8). Zweite Fundstellen der alten Lesart („spielen … ab“, „A-4, A-5 und A-15 entfallen“): in `spec/` keine, im Messprotokoll `referenzabgleich.md` (Zeile A-15 und Abschnitt „Anweisungsformen erweitert“) steht die Bedingung („aus dem Bau vorhergesagt, ein Lauf von `LAUF=mitschnitt` belegt sie“) in derselben Zelle; die Matrix sagt „noch nicht gemessen“.

### F-5, F-6, F-9

- F-5: die Server-Spalten und Ergebnisse der vier Regeln `…/01/tokenfolge@2022` sind unverändert (`DONE || DONE`), nur der Grund verliert den Nachsatz „mit dem Bau von [ADR-0058](../plan/adr/0058-or-in-der-auswahl-ganzzahlkonstante-und-waitfor-delay-sind-erkannt.md) gleichen sich Fehler und Tokenfolge sonst, A-15 entfaellt“; der Grund nennt A-18 allein. Grep nach „A-15 entfaellt“ und „gleichen sich Fehler und Tokenfolge sonst“ über das Repo (ohne `docs/reviews`): kein Treffer. Die Prüfung der Liste vergleicht nur die Ergebnis-Spalten (Erstreview, Schwerpunkt 4); die Stempel `werkzeug` auf `6d002217` ist grün.
- F-6: das KDoc von `pause` nennt den Parameter „Test-Naht“, sagt, dass der öffentliche Konstruktor `delay` festsetzt, und dass Tests mit der gesteuerten Uhr „die Reaktion der Klasse auf Ablauf und Cancel“ belegen, „nicht dass ein echtes Ereignis sie auslöst“; es verweist auf den Test der Voreinstellung, der existiert (`die Voreinstellung der Uhr wartet wirklich und der Cancel beendet sie`). Der Parameter ist `internal`. Der Skill-Punkt „Test-Naht im Produktivcode“ ist erfüllt.
- F-9: der Eintrag 2026-11-21 nennt jetzt „die öffentliche Typliste von `SPEC-059` ist gleich (`make flaeche`, das Typnamen und keine Mitglieder vergleicht), der öffentliche Konstruktor `SimulationBackend(catalog, onStructure = null)` behält Parameter und Voreinstellung (Lesen des Codes)“. Der Code stimmt: der Sekundärkonstruktor trägt `(catalog, onStructure = null)` und ruft den Primärkonstruktor mit `{ delay(it) }`; der Einkonstruktor `(catalog: SimCatalog)` ist unverändert. `make flaeche` Exit 0.

## Prüfung des neuen Tests und der Naht

**Naht.** `internal val registrations: Int get() = running.size` ist `internal`, trägt ein KDoc mit der Klasse Kopplung („Zahl der unter Sitzung und Anfrage registrierten Ausführungen (Kopplung an `cancel`)“) und liest nur. Sie ändert weder die Signatur der öffentlichen Typen noch ein öffentliches Verhalten; die öffentliche Typliste von `SPEC-059` bleibt gleich (`make flaeche` grün). `registrations` zählt alle Ausführungen unter einem Schlüssel, nicht nur `WAITFOR`; der Test benutzt nur `WAITFOR`.

**Test.** Er beobachtet die Zahl der Registrierungen nach vier Wegen (Ablauf der Wartezeit, `cancel`, Abbruch des Sammlers, `close()` ohne Sammeln), mit einer Zählung vorher (0 und 1) und je einer Meldung je Assertion. Er wartet keine Wanduhr ab (gesteuerte Uhr `ManualPause`, `awaitWaiting`, `join`), es steht kein `sleep` und kein `delay(` darin. Das KDoc nennt Wächter und Beobachtung im Indikativ; der Konjunktiv „dann bliebe je `WAITFOR`-Anfrage ein Eintrag zurück“ gehört zum Idiom des Bestands (Erstreview F-10, INFO).

**Mutationen** (Einzelmessung 2026-11-22, ein Host, je Mutation ein Gradle-Lauf in Docker mit `*SimulationBackendWaitTest` und `*WaitForWireTest`, 10 Testfälle; das Skript liegt im Scratchpad, nicht im Repo; Last nicht erhoben):

| Nr | Mutation | Ergebnis | Erste fehlgeschlagene Assertion |
|---|---|---|---|
| (Ausgang) | keine | grün, 10 von 10 | — |
| R2 (= M24) | `WaitExecution.kt`: `.onCompletion { onEnd(…) }` und `onEnd(this)` in `close()` entfernt (die Mutation, die im Erstreview 9 von 9 grün blieb) | rot, 1 von 10: `die Registrierung endet mit Ablauf Cancel Abbruch und close` | „nach dem Ablauf der Wartezeit ==> expected: <0> but was: <1>“ |
| M22 | nur `.onCompletion { onEnd(…) }` entfernt | rot, 1 von 10, derselbe Testfall | „nach dem Ablauf der Wartezeit“ |
| M23 | nur `onEnd(this)` in `close()` entfernt | rot, 1 von 10, derselbe Testfall | „nach close() ohne Sammeln“ |
| M25 | `SimulationBackend.kt`: `WaitExecution(result.delay, pause) { }` statt `{ running.remove(key, it) }` | rot, 1 von 10, derselbe Testfall | „nach dem Ablauf der Wartezeit“ |
| R5 (eigene Mutation) | `.onCompletion { cause -> if (cause == null) onEnd(…) }`: die Austragung nur beim regulären Ende, nicht beim Abbruch des Sammlers | rot, 1 von 10, derselbe Testfall | „nach dem Abbruch des Sammlers ==> expected: <0> but was: <1>“ |

Die Wirkung von M22 bis M25 stimmt mit der Beschreibung im Mutationsbeleg überein (rot, 1 von 10, Klassen und Testfall; Laufzeit 58 bis 91 s dort, 64 bis 89 s hier). R5 zeigt, dass der Test den Weg „Abbruch des Sammlers“ eigens trägt, nicht nur den Ablauf; M23 zeigt, dass er `close()` ohne Sammeln eigens trägt. Die Lücke der Austragung bei `SelectExecution`, `ChangeExecution` und `MergeExecution` ist im Beleg benannt (nicht Teil des Diffs) und kein Befund dieses Re-Reviews.

## Findings (neu durch die Nachbesserung)

Kein HIGH, kein MEDIUM, kein LOW.

### N-1 — INFO
- `kategorie`: INFO
- `quelle`: Skill „Mitglieder eines Typs der Liste in `SPEC-059` ohne Eintrag im Änderungsprotokoll“
- `pfad`: `spec/spezifikation.md` · „der öffentliche Konstruktor `SimulationBackend(catalog, onStructure = null)` behält Parameter und Voreinstellung (Lesen des Codes)“ (Änderungsprotokoll 2026-11-21); `src/main/kotlin/io/github/pt9912/tdswire/simulation/SimulationBackend.kt` · „internal val registrations: Int get() = running.size“
- `befund`: Die neue interne Eigenschaft `registrations` an einem Typ der Liste von `SPEC-059` steht nicht im Änderungsprotokoll; sie ist `internal` und gehört nicht zur öffentlichen Kotlin-Fläche (Aussage des Skills gilt für Mitglieder der Fläche). Kein Befund, nur zur Kenntnis; nach meiner Kenntnis des Kotlin-Verhaltens (nicht gemessen) ist sie in der JVM mit verändertem Namen öffentlich, wie der interne Konstruktor, den das Protokoll schon erwähnt.
- `verifizierbar`: nein (`make flaeche` vergleicht keine Mitglieder)
- `klasse`: Interne Naht ohne Vermerk zur Fläche

## Geprüft, ohne Befund

- `src/main/kotlin/io/github/pt9912/tdswire/simulation/` (`SimulationBackend.kt`, `BulkForms.kt`): die Nachbesserung ändert nur KDoc und die Eigenschaft `registrations`; der KDoc von `metadataOnly` stimmt mit dem Code überein (`Wait` und Änderung hinter `SET FMTONLY ON` abgewiesen, jede andere Erkennung und jede Abweisung unverändert; `SET FMTONLY ON SELECT 2` als Setzung des Baus gekennzeichnet); kein `@Suppress`, `@SuppressWarnings`, `noqa`, `nolint` im Diff; `config/`, `Dockerfile`, `Makefile`, `tools/`, `build.gradle.kts` unberührt.
- `src/test/kotlin/io/github/pt9912/tdswire/simulation/SimulationBackendWaitTest.kt`: ein neuer Testfall, trägt Austragung (R2, M22 bis M25, R5), keine Wanduhr, `@spec SPEC-029`.
- `docs/plan/planning/done/slice-simulation-anweisungsformen-erweitern.md`: Tabelle §3 gegen den Diff abgeglichen (F-1), kein Haken gesetzt.
- `docs/messungen/mutationen-anweisungsformen-2026-11-21.md`: M22 bis M25 und die Klassenliste stimmen mit meinen Läufen überein; die Mutationen beschreiben die gemessene Wirkung.
- `docs/plan/spec-abdeckung.md`: eine Zeile (SPEC-029 52 auf 53), passt zum einen neuen getaggten Test.
- `referenztest-dotnet/Vergleich/mitschnitt-ausnahmen.tsv`: nur die Grund-Spalte von vier Zeilen; Ergebnis-Spalten und Regeln unverändert.
- `spec/spezifikation.md`: SPEC-029 und Änderungsprotokoll (F-3); Aussagen tragen höchstens so weit wie ihr Beleg.
- Hard Rules §3.1, §3.2, §3.4, §3.5, §3.6 ohne Befund; §3.7 und §3.8: F-3 und F-5 behoben.

## Urteil

Alle sechs geprüften Findings (F-1, F-2, F-3, F-5, F-6, F-9) sind behoben, nicht nur umformuliert: die Plan-Tabelle stimmt mit dem Diff überein, die Austragung aus `running` trägt jetzt ein Test (die Mutation R2, die im Erstreview 9 von 9 grün blieb, färbt ihn rot; ebenso M22, M23, M25 und die eigene Mutation R5), die interne Naht erweitert die öffentliche Fläche nicht (`make flaeche` grün), und SPEC-029 und das Änderungsprotokoll tragen die Bedingung im selben Satz. Die Nachbesserung bringt kein neues Finding über INFO hinaus (N-1).

Anzahl: 0 HIGH, 0 MEDIUM, 0 LOW, 1 INFO (N-1); offen aus dem Erstreview: F-4 und F-7 (Nutzerentscheid, Doku-Commit parallel, nicht bewertet), F-8, F-10, F-11 (INFO, unverändert).

**Empfehlung:** weiter an den Verifier (mit `make clean-build`, `make e2e`, `make kompatibilitaet` und den Läufen `make vergleichslauf-d LAUF=live` und `LAUF=mitschnitt`, die die vorhergesagten Server-Spalten belegen); den Doku-Commit zu F-4 und F-7 bewertet dieser Report nicht.
