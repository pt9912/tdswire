# Verifikationsbericht: slice-wanduhr-tests-lastunabhaengig (HEAD eec118d3)

* Rolle: Verifier (Modul 11), frischer Kontext. Gegenstand: Slice `slice-wanduhr-tests-lastunabhaengig` (`in-progress/`, Zweig `worktree-agent-ace4075b0828e3268`, HEAD `eec118d3`). Frage: Bauen wir es richtig, gegen Plan und DoD.
* Maßstab: Slice §2 (DoD), §3 (Plan), §6 (Risiken); [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität); `AGENTS.md` §3.8. Kein ADR, keine Spec-Änderung zugesagt.
* Eingang: Review `2026-11-08-0624d431-review-slice-wanduhr-tests-lastunabhaengig.md` (0 HIGH, 3 MEDIUM, 1 LOW, 3 INFO), Nachbesserung `eec118d3`, Register `BEO-TDS/wanduhr-tests-flackern-unter-last`. Die Nachbesserung `eec118d3` hat keinen eigenen Review; ich habe sie ersatzweise unten gegen F-1 bis F-5 einzeln geprüft.
* Am Code nichts geändert. Haken habe ich nicht gesetzt.
* Merge-Probe `git merge-tree --write-tree HEAD main`: Exit 0, sauber (Basis älter als `main`; `main` änderte `TableStoreTest.kt` um 3/2 Zeilen, ohne Konflikt).

## Eigene Läufe (Belege)

Host: `make clean-build` unter Lastmittel 3 bis 11 (fremde Läufe, keine künstliche Last), Mutationen danach bei Lastmittel 3 bis 8. Alle Läufe im Vordergrund bzw. mit Warten auf das Log. Logs und Skripte: Scratchpad `/tmp/claude-1000/-Development-tdswire/3768ddc4-cff6-4152-b0be-815e9ed5f5cb/scratchpad/verif-wanduhr/` (nicht im Repo).

| Lauf | Ergebnis |
|---|---|
| `make clean-build` (genau einmal, End-Stand `eec118d3`) | Exit 0, `BUILD SUCCESSFUL`; **588 s (9 min 48 s) real**; 1269 Testfälle `PASSED`, 0 rot |
| `make gates FORCE=1` | Exit 0 in 31 s. **Cache-Falle:** 88 Docker-Schritte `CACHED` (Eingang gleich dem des `make clean-build` kurz davor); der Lauf belegt die Verdrahtung der Gates (`gate-zuordnung`: 16 Gates, Selbsttest 33 Fälle), nicht die Frische der Tests; den Frischbeleg trägt `make clean-build` |
| `make flaeche` | Exit 0 (1 s) |
| `make docs-check` | Exit 0 (2 s, vor Anlage dieses Berichts; nach Anlage siehe Commit-Nachricht) |
| `make e2e`, `make matrix-zitate`, `make spec-abdeckung` | nicht gefahren: kein Produktcode geändert (Tabelle `harness/README.md`); Zitate unverändert (Diff enthält keine geänderten `@Test`-Namen, keine Datei unter `docs/plan/kompatibilitaetsmatrix.md`; die dort zitierten `ListenerAbortTest`-Namen stimmen weiter) |
| Mutationen (4, je 3 Läufe, Klasse allein im Rig) | alle rot 3/3, siehe unten |

## 1. Laufzeit am frischen Bau (Zweck des Slice)

Zeiten aus dem Log von `make clean-build` (Stage `test`, Zeitmarken in Sekunden ab Stufenbeginn):

| Posten | Dauer |
|---|---|
| `make clean-build` gesamt | **588 s** (früher 27 bis 34 min; Verifikation `fa7adeaa` zuletzt 26 min 58 s) |
| Stage `test` (Gradle `test integratorTest`, mit Übersetzung) | 211 s (`BUILD SUCCESSFUL in 3m 30s`) |
| `:testserver:test` | 63,9 s bis 73,1 s Stufenzeit, rund 9 s für alle Tests des Moduls |
| `TableStoreTest > gleichzeitiger Zugriff verliert nichts` | im Log bei 66,67 s gemeldet, zwischen 65,07 s und 66,67 s: **höchstens 1,6 s** (Auflösung des Logs; die Review-Messung im Rig: 1,06 s) statt bis zu 24 min |
| `:test` (Hauptmodul) | 75,1 s bis rund 210 s, rund 135 s |
| Stage `detekt` | 253 s (`BUILD SUCCESSFUL in 4m 12s`) |
| Docker-Stufen davor (Jar, Image, `deps`) | je 8 bis 38 s |

Urteil: **erfüllt.** Der Zweck (kein 24-Minuten-Test im frischen Bau) ist am frischen Bau belegt. Einschränkung: eine Einzelmessung bei mäßiger Last (Lastmittel 3 bis 11), nicht unter der hohen Last von 15 bis 25 der früheren Zahlen; die Ursache der früheren Dauer (Leser hält die Sperre fast ununterbrochen) ist durch die Marken-Begrenzung beseitigt und nicht mehr von der Last abhängig, aber die Wiederholung unter hoher Last ist nicht gefahren (und, auf Auftrag, keine künstliche Last erzeugt).

## 2. Mutationen (Rig: Image `rev-deps`, Quellen einer Kopie gemountet, `--offline`, Klasse allein)

| Mutation | Test-Filter | Ergebnis | Zeit je Lauf |
|---|---|---|---|
| M1 `@Synchronized` von `catalog()` entfernt (`TableStore.kt`) | `*TableStoreTest` | `gleichzeitiger Zugriff verliert nichts` **rot 3/3**, 18 Tests grün | 42 s (mit Übersetzung), 7 s, 7 s |
| H1 Warten auf das erste Byte mit fester Frist `15.seconds` (`ConnectionWiring.kt:126`) | `*HandshakeWiringTest` | `Die Frist des Wartens auf das erste Byte traegt den konfigurierten Wert` **rot 3/3** | 64 s, 16 s, 17 s |
| H2 Treiber mit fester Frist `15.seconds` (`ConnectionWiring.kt:154`) | `*HandshakeWiringTest` | `Die Frist des Treibers traegt den konfigurierten Wert` **rot 3/3** | 59 s, 16 s, 15 s |
| A1 `expireGrace()` entfernt (`TdsListener.kt:145`) | `*ListenerAbortTest` | 4 von 5 Tests rot, darunter `der Listener ruft den Abbruch nach der Frist und wartet CANCEL_TIMEOUT ab`, **3/3** | 114 s, 67 s, 67 s |

Damit ist das Finding F-1 des Reviews (H1/H2 überlebten) am End-Stand nachgefahren behoben. Die Zeit bis zum Rot bei hängenden Tests (Slice §6 dritter Punkt) ist gemessen: 67 s je Klassenlauf bei A1 (vier rote Tests; die Aufteilung auf die Schutzgrenze 60 s je Test habe ich nicht aufgeschlüsselt), 7 s bei M1. Nicht von mir nachgefahren: `@Synchronized` von `replace` (M1c) und `structureLock.withLock` → `run` (M2) und `delay(cancelTimeout)` entfernt (A2); die drei hat der Review 3/3 rot gemessen, am Stand vor `eec118d3` (Testcode von `TableStoreTest` und `ListenerAbortTest` unterscheidet sich in `eec118d3` nur in den Findings F-3/F-1, siehe unten).

## 3. Findings des Reviews gegen `eec118d3`

| ID | Behoben? | Beleg |
|---|---|---|
| F-1 (MEDIUM, `eventually` auf 60 s verlängert) | ja | `git diff` (Basis..HEAD) an `ListenerSupport.kt`: **keine gelöschte Zeile**, `eventually` ist Zeichen für Zeichen die Basis (`POLL_ROUNDS`=1000, `POLL_MILLIS`=5); neue Funktion `eventuallyGuarded` (Uhrgrenze), nur von `ListenerAbortTest` gerufen; H1 und H2 rot 3/3 (siehe oben) |
| F-2 (MEDIUM, KDoc „fünf Sekunden“) | ja, durch F-1 gegenstandslos | `grep` in `src/test`: `HandshakeWiringTest.kt:187, 208` und `ListenerSupport.kt:38` sagen weiter „fünf Sekunden“ und das stimmt wieder mit `eventually` überein |
| F-3 (MEDIUM, „jeder Leser … genau einmal“) | ja | KDoc von `gleichzeitiger Zugriff verliert nichts`: „je Anweisung eines Schreibers werden `READERS` Lesevorgänge freigegeben; welcher Leser sie nimmt, bestimmt die Planung“; DoD-Punkt 1 und Plan-Tabelle §3 sagen dasselbe; `checkCatalog` prüft jeden gelesenen Katalog (Inhaltscheck im Leser) |
| F-4 (LOW, Ziel ohne Bedingung) | ja | Slice §1 Ziel: „(begründet, nicht gemessen; die Schutzgrenze von 60 s reißt unter ausreichend hoher Last auch)“ im selben Satz |
| F-5 (INFO, `spec-abdeckung`) | zur Entscheidung (siehe B2) | Plan-Tabelle §3 nennt `docs/plan/spec-abdeckung.md` als „unverändert“; der Diff berührt sie nicht, das ist mit der Tabelle konsistent |

## 4. Aussage zum Flake von `ListenerAbortTest` (Auftrag Punkt 2)

**Der Slice führt ihn nicht als behoben.** `grep behoben` im Slice: kein Treffer. §1 Ziel („begründet, nicht gemessen“), §6 zweiter Punkt („begründet, nicht gemessen … weiter offen → `BEO-TDS/wanduhr-tests-flackern-unter-last`“). Das Register ist im Diff unverändert (`git diff --name-only`: nur Slice, Roadmap, Review, drei Testdateien); sein Ausgang liegt beim Nutzer, und die Zählung (7×) ist nicht angefasst. Der Nachweis „Flake weg“ ist nicht erbracht und nicht behauptet: der Flake wurde im Rig nicht reproduziert, dieser Lauf hat ihn ebenfalls nicht gesehen (`ListenerAbortTest`: 5 von 5 PASSED im `make clean-build` bei Lastmittel 3 bis 11), was bei fehlender Reproduktion nichts belegt.

## 5. Plan-vs-Code (Plan-Tabelle §3, Auftrag Punkt 4)

| Plan-Zeile | Diff | Urteil |
|---|---|---|
| `TableStoreTest.kt` update (Leser an Marken eines Semaphors, `READERS`=2, Inhaltsprüfung, Ende über letzte Marke, 4×150 Schreiber, KDoc) | `readCatalog`, `checkCatalog`, `writeStep`, `Semaphore`, `READERS`; `steps.release(READERS)` nach `done.set(true)`; Endprüfung unverändert | konform |
| `ListenerSupport.kt` update (`awaitPending`, `eventuallyGuarded`, `eventually` unverändert) | `MutableStateFlow` `changes`, `awaitPending`, `eventuallyGuarded`; `eventually` ohne gelöschte Zeile | konform |
| `ListenerAbortTest.kt` update (`awaitWaiting` über `awaitPending`, `eventuallyGuarded(…, hangGuard)`, `shortTimeout` 3 s → `hangGuard` 60 s) | wie beschrieben; Erwartungen der Tests unverändert | konform |
| `docs/plan/spec-abdeckung.md` unverändert | unverändert | konform |
| nicht in §3, im Diff: Roadmap-Zeile „In Arbeit (ohne Welle): slice-wanduhr-tests-lastunabhaengig“, Review-Report | Zustandsfeld der Roadmap, nennt Zustand und Anker | kein Befund |

Kein Produktcode (`src/main`, `testserver/kotlin`) im Diff, keine Spec, keine ADR, keine Nutzer-Doku. Keine Inline-Suppression (`@Suppress` Grep: 0 Treffer im Diff). `make flaeche` grün.

## 6. DoD-Zeilen

Haken: keiner gesetzt (`grep -c '\- \[x\]'` = 0), die Regel `BEO-TDS/haken-vor-verifikation-gesetzt` ist eingehalten. Ich setze keine.

| # | DoD-Zeile | Urteil | Beleg |
|---|---|---|---|
| 1 | `TableStoreTest`: Last begrenzt, Inhaltsprüfung, Größenordnung Sekunde, Mutationen „ohne Sperre“ und „ohne `structureLock`“ rot | **erfüllt, mit Lücke im Beleg des Verifiers** | Laufzeit am frischen Bau ≤ 1,6 s (Abschnitt 1); M1 (nur `catalog()`) rot 3/3 von mir; „`replace` ohne Sperre“ und „ohne `structureLock`“ hat der Review rot 3/3 gemessen (M1c, M2, Stand vor `eec118d3`), von mir nicht wiederholt. Die Zeile „vorher/nachher gemessen, drei Läufe“ trägt der Slice selbst nicht (kein Messprotokoll im Slice; die Zahlen stehen im Review und in diesem Bericht) |
| 2 | `ListenerAbortTest`/`ListenerSupport`: Warten am Ereignis, Schutzgrenze 60 s, `eventually` unverändert, Mutationen „kein Abbruch“, „keine Wartezeit `CANCEL_TIMEOUT`“ rot | **erfüllt** | `awaitPending`/`eventuallyGuarded`/`hangGuard` im Diff; `eventually` wortgleich; A1 rot 3/3 von mir; „keine Wartezeit“ (A2) vom Review 3/3 rot, von mir nicht wiederholt; H1/H2 rot 3/3 (die übrigen rund 84 `eventually`-Aufrufe tragen ihre Aussagekraft) |
| 3 | `make gates` grün | erfüllt | Exit 0 (Cache-Falle genannt); Frischbeleg `make clean-build` Exit 0 |
| 4 | `make flaeche`, `make docs-check`, `make spec-abdeckung` einmal gelaufen | **teilweise** | `flaeche`, `docs-check` Exit 0 (eigene Läufe); `spec-abdeckung` nicht gefahren (kein Gate, nicht im Sensor-Zuschnitt, Plan-Tabelle sagt „unverändert“); B2 |
| 5 | Review durchgeführt, Report liegt vor | erfüllt | `docs/reviews/2026-11-08-0624d431-review-slice-wanduhr-tests-lastunabhaengig.md`; Nachbesserung ohne zweiten Review, ersatzweise hier geprüft |
| 6 | Haken setzt der Planner | eingehalten | 0 Haken gesetzt |
| 7 | Doku-Update (keiner berührt) | erfüllt | `docs/user/` unverändert |
| 8 | Closure-Notiz mit Steering-Loop-Lerneintrag | offen (Planner) | §7 „Wird vom Planner … geschrieben“ |
| 9 | Beobachtungs-Register fortgeschrieben | offen (Planner) | Register unverändert |
| 10 | Jedes Risiko aus §6 trägt einen Ausgang | offen (Planner) | Risiko 1 „offen bis zur Messung“ (jetzt gemessen: M1/M1b/M1c/M2 rot, siehe Review und Abschnitt 2), Risiko 3 „offen bis zur Messung“ (gemessen: 67 s bei A1), Risiko 2 weiter offen → Register |
| 11 | Drei Paarungen (Anker · Folge-Slice · Register) | offen (Planner) | Anker: keine Regel mit `liegt in` im Slice; Register: nicht fortgeschrieben |

## 7. Offene Punkte und Risiken

* Die Marken-Begrenzung macht den Test lastunabhängig in der Laufzeit; ein grüner Lauf beweist die Sperre nicht (zufallsabhängiger Wettlauf). Belegt: M1 3/3 rot (hier), M1b/M1c/M2 3/3 rot (Review). Mehr Läufe unter hoher Last sind nicht gefahren.
* `ListenerAbortTest`-Flake: nicht reproduziert, nicht behoben (Abschnitt 4).
* Die Schutzgrenze von 60 s bleibt eine Wanduhr-Zahl; unter ausreichend hoher Last reißt sie auch (Slice sagt es).

## Gesamturteil

**Closure-fähig: ja, unter den Bedingungen B1 bis B4 (alle in Planner-Hand, keine am Code).** Der Zweck ist am frischen Bau belegt (588 s statt 27 bis 34 min, der Test unter 2 s), die Zusagen tragen (4 Mutationen rot 3/3, dazu die des Reviews), die Findings F-1 bis F-4 sind behoben, kein Produktcode berührt, der Flake wird nicht als behoben geführt.

**Bedingungen für die Planner-Closure**

* B1: Closure-Notiz §7 mit Steering-Loop-Lerneintrag; ein Lerneintrag zur Klasse „Gemeinsamer Helfer verlängert implizit die Zeitaussage anderer Tests“ (F-1) steht zur Entscheidung; trägt der Eintrag ein Feld `liegt in`, braucht der Zielort den Anker `seit slice-wanduhr-tests-lastunabhaengig`.
* B2: DoD-Zeile `make spec-abdeckung`: nicht gefahren, nicht im Sensor-Zuschnitt, Plan sagt „unverändert“; Planner entscheidet, ob die Zeile mit dieser Begründung erfüllt gilt oder der Lauf bei der Closure nachgeholt wird. Die Closure hat die Matrix in der Vergangenheit rot erwischt (`ListenerAbortTest`, siehe Register); ein roter Fall dort wäre ein Flake-Beleg, keine Verletzung dieses Slice.
* B3: Risiken aus §6 mit Ausgang versehen: Risiko 1 eingetreten/entfallen → gemessen (M1/M1b/M1c/M2 rot 3/3); Risiko 3 → gemessen (A1: 67 s je Klassenlauf, M1: 7 s); Risiko 2 weiter offen mit Verweis auf das Register.
* B4: Register `BEO-TDS/wanduhr-tests-flackern-unter-last` fortschreiben (Evidence und Zähler beim Planner/Nutzer): dieser Slice ändert die Lage so: `ListenerAbortTest > …nach der Frist…` wartet jetzt am Ereignis; der Ausgang „behoben“ ist **nicht** belegt und darf nicht gesetzt werden, bevor ein Lauf unter hoher Last ohne Rot vorliegt (vier Läufe der Vorverifikationen waren Stichproben).
* Danach: Haken setzen, `git mv` nach `done/` (erst Inhalt, dann reiner Move, `AGENTS.md` §3.3).

## Gesamtlaufzeit und größte Zeitposten (dieser Verifikation)

Rund 50 min Wanduhr, davon: `make clean-build` 588 s (Stage `test` 211 s, `detekt` 253 s), vier Mutationen im Rig zusammen rund 8,5 min (A1 249 s, H1 98 s, H2 92 s, M1 58 s), `make gates FORCE=1` 31 s, `make flaeche` 1 s, `make docs-check` 2 s; Rest Lesen, Warten auf Logs.
