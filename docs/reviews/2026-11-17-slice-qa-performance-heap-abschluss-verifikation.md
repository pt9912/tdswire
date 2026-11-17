# Verifikationsbericht: slice-qa-performance-heap-abschluss — 2026-11-17

**Rolle:** Verifier (Modul 11), DoD- und Entscheidungs-Konformität plus Plan-vs-Code-Diff. Frage: „Bauen wir es richtig?“ gegen Plan und DoD; kein Review, keine Reparatur, keine DoD-Haken, kein Schließen.

**Gegenstand:** Branch `slice-qa-performance-heap-abschluss`, Spitze `d3d55a91`, Merge-Basis `077e448c`. Slice: [slice-qa-performance-heap-abschluss](../plan/planning/done/slice-qa-performance-heap-abschluss.md). Messprotokoll: [performance-heap-abschluss-2026-11-17](../messungen/performance-heap-abschluss-2026-11-17.md), Rohzeilen: [performance-heap-abschluss-2026-11-17-rohzeilen.txt](../messungen/performance-heap-abschluss-2026-11-17-rohzeilen.txt). Quellen: [TDS-QA-04](../../spec/lastenheft.md#tds-qa-04--performance), [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md) (`Accepted`, im Diff unverändert), [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md), `SPEC-002`, `SPEC-031`. Eingang: [Review](2026-11-17-slice-qa-performance-heap-abschluss.md), [Re-Review](2026-11-17-slice-qa-performance-heap-abschluss-re-review.md).

## Urteil in Kürze

Die Zusagen des Slice sind am Beleg getragen, mit drei benannten Einschränkungen: (1) die DoD „Abstand letzte Zeile → DONE“ ist im jetzigen **Wortlaut** formal erfüllt, aber ohne Aussage (immer 0 µs); die Verzögerung steht in anderen Spalten — **Planner entscheidet vor der Closure über den Wortlaut**; (2) die Spalte „vorletztes Paket → DONE“ hat am Aufrufer keinen rot gesehenen Test (V-1); (3) die vier Nutzerfragen sind offen und stehen als offen im Protokoll. Kein HIGH. Kein Messlauf wiederholt: keine Rohzeile trägt ihre Aussage nicht.

## Sensoren (End-Stand `d3d55a91`, Worktree sauber)

| Sensor | Ergebnis | Laufzeit |
|---|---|---|
| `make clean-build` (unter Messsperre) | Exit 0; Jar, Tests und detekt (Hauptmodul und `testserver`) `BUILD SUCCESSFUL` | 563 s |
| `make e2e` (unter derselben Sperre, danach freigegeben) | Exit 0, 9 Stufen grün | 371 s |
| `make gates FORCE=1` | Exit 0 (Stages `detekt-selbsttest`, `kopplung-zahlen`, `skript-selbsttest`, `detekt`, `build-jar`, `test` (.NET und Kotlin), vier Stempel-Selbsttests mit 118 / 95 / 102 / 67 Fällen, `gate-zuordnung`, `flaeche`) | 35 s (Layer-Cache; die Frisch-Läufe sind clean-build und der Selbsttest unten) |
| `make docs-check` | Exit 0, 1612 Dateien, 0 Befunde (mit diesem Bericht erneut, siehe Ende) | rund 10 s |
| `docker build --no-cache --target skript-selbsttest .` | Exit 0; `heap-abschluss-selbsttest` 0 Abweichungen, 15 Mutationen rot; `heap-abschluss-tabellen-selbsttest` 13 Mutationen rot | 169 s |

Größte Zeitposten: `clean-build` 563 s (Gradle-Stages ohne Cache), `e2e` 371 s (Stufe 2 84 s, Stufe 1 68 s, Stufe 9 63 s). Last 1,9 bei Start, kein Speicherdruck, keine künstliche Last. Messläufe (`make heap-abschluss`) nicht wiederholt.

**Aussagekraft von `make e2e` für diesen Slice:** `testserver/` ist berührt (`HeapAbschlussServer`, `AbschlussClient`, `RohbytesClient`, `LoginProbe` als Wrapper). Belegt wird dadurch nur der **Bestand**: Stufe 2 (`laufzeittest`, Rohbytes-Login aus zweitem Container) trägt den Umbau von `LoginProbe` auf `RohbytesClient`; Stufe 1 (`smoke`, Prozesstests) und die Stufen 3 bis 8 belegen, dass `testserver/` mit den neuen Dateien weiter läuft und baut. **Nichts** belegt `make e2e` für die neuen Zusagen: Server der Heap-Messung, `AbschlussClient`, Stage `heap` im `referenztest-dotnet/Dockerfile` und `HeapTests.cs` laufen in keiner Stufe (`make heap-abschluss` ist kein Gate und nicht in `make e2e`, [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md)); die Stage `heap` baut allenfalls als Teil der .NET-Stage `test`, nicht als Stage `heap` selbst. Diese Teile tragen allein die Rohzeilen des Messlaufs und die 10 Tests von `HeapAbschlussTest` (in `make test`, grün).

## Plan-vs-Code-Diff

`git diff 077e448c HEAD --stat`: 23 Dateien. Jede Zeile der Plan-Tabelle (Slice §3) hat ihren Diff-Treffer: `HeapAbschlussServer.kt`, `AbschlussClient.kt`, `RohbytesClient.kt`, `HeapAbschlussTest.kt`, `LoginProbe.kt` (137 Zeilen weniger), `referenztest-dotnet/Heap/HeapTests.cs` und Dockerfile (Stage `heap`), `tools/heap-abschluss.sh`, `tools/mk/heap-abschluss.mk`, drei weitere Werkzeuge, `Dockerfile`, `tools/mk/skript-selbsttest.mk`, `harness/README.md` (zwei neue Zeilen), Messprotokoll mit Rohzeilen, `docs/plan/referenz-workload.md`, `spec/spezifikation.md`, `docs/user/einbettung-und-betrieb.md`. Nicht im Plan, aber im Diff: nur die zwei Review-Reports und der Slice selbst (Plan-Tabelle umgeschrieben, DoD-Text unverändert). **Nicht berührt** (gegen `077e448c` gelesen): `spec/lastenheft.md`, `spec/architecture.md`, alle ADRs (`git diff --stat` leer), `src/`. DoD-Haken im Slice: keine gesetzt, §6 trägt weiter „offen bis zur Closure“, §7 „Wird bei der Closure gefüllt“ (richtig für diesen Stand).

**Letzte Nachbesserung `d3d55a91`:** `git diff f114d8bf..HEAD` berührt vier Dateien (Messprotokoll, `docs/user/einbettung-und-betrieb.md`, `spec/spezifikation.md`, Re-Review-Report) und nur Text; kein Kotlin, kein Test, kein Skript, kein Dockerfile. Bestätigt.

## Nachrechnung der Protokollzahlen aus den Rohzeilen

Unabhängig mit awk aus den Rohzeilen gerechnet (nicht aus dem Werkzeug):

1. 18 Zeilen `heap: lauf`, 19 Zeilen `zelle:` (18 Heap, 1 Abschluss) — Protokoll „Zellen 19“.
2. Dauer `heap-eigen-1` erste Zelle 93 190 ms → 93,2 s; Zeilen/s 10 000 000 / 93,19 = 107 308 (Rohzeile 107 307, Rundung des Erzeugers; Tabelle zeigt den Rohwert).
3. Alle 18 Zeilen/s-Werte gegen n × 10 Mio / Dauer: Abweichung höchstens 1.
4. Maximum der Stichproben je Lauf in MiB (KiB / 1024): 3,5 / 3,8 / 3,5 / 3,9 / 3,5 / 3,9 / 4,0 / 4,6 / 4,3 / 4,3 / 4,1 / 4,4 / 4,5 / 4,8 / 4,4 / 4,7 / **7,5** / 4,8. „17 von 18 zwischen 3,5 und 4,8“ stimmt, der Ausreißer ist `heap-eigen-16` (3,5 bis 4,8 ohne ihn).
5. GC-Zeit `heap-sim-16` (14:27) 22 500 ms → 22,5 s; Spanne „1,6 bis 22,5 s“ stimmt.
6. cgroup-Spitze: 92 807 168 / 1 048 576 = 88,5 MiB (kleinster); 172 953 600 / 1 048 576 = 164,9 MiB (größter) — „88 bis 165 MiB“.
7. Median je Aufbau und Stufe: Aufbau B 1 Sitzung 3,5; C 3,7 bis 3,8; 8 Sitzungen B 3,5, C 3,8; 16 Sitzungen B 3,7 bis 3,8, C 4,0 bis 4,1 — stimmt mit den Rohzeilen.
8. Vier Zellen mit Proben über 300 %: `heap-eigen-1` (2 von 8, 452 %), `heap-sim-1` (2 von 8, 440 %), `heap-sim-16` (1 von 62, 389 %), `heap-sim-16` (1 von 62, 374 %); alle anderen Zellen `ueber300 0` — Zählung stimmt, auch der Abschluss (272 %).
9. Abschluss, Median über 5 Läufe (ohne `wdh 0`): `ende` D=0 765 µs (Werte 539, 556, 765, 865, 900), D=10 11 191, D=100 101 164, D=1000 1 001 257; `folge` D=0 390, D=1000 1 001 569 — stimmen.
10. Größter Überhang über D aller 40 Läufe: 2 039 µs (`ende`, D = 10 ms) → „2,0 ms“; Überhang im Median 0,39 bis 1,57 ms → „0,4 bis 1,6 ms“.
11. 40 von 40 Läufen: `zeile_und_done_im_selben_paket true`, `zeile_bis_done_client_us 0` (Summe 0), `zeilen_vor_dem_letzten_paket 960`; 8 Warmläufe (`wdh 0`) ausgeschlossen.
12. „vorletztes Paket → DONE“ minus „Zeile emittiert → DONE“, Differenz der Mediane: bis 364 µs davor (`ende`, D=0), bis 92 µs danach (`folge`, D=100) — deckt „höchstens 0,4 ms vor, 0,1 ms nach“.

`bash tools/heap-abschluss-tabellen.sh <Rohzeilen>`: Exit 0; alle 37 Tabellenzeilen des Erzeugers stehen im Protokoll (Zeilenvergleich `comm`, 0 Abweichungen). Die Tabellen sind reproduzierbar.

## Befund je DoD-Punkt

| DoD-Punkt | Befund |
|---|---|
| **Heap** (Live-Heap-Stichprobe, `-Xmx` fest, 10 Mio Zeilen, 1 Sitzung und Staffel bis 16, Aufbau B und C, Streuung, Zeile sagt „Stichprobe nach GC, keine Spitze“) | **Getragen.** 18 Läufe (1, 8, 16 Sitzungen × B, C × 3 Wiederholungen), jeder mit `gruen`, Streuung als Minimum/Median/95./Maximum und Vergleich der drei Läufe. Die Bedingung „Stichprobe alle 500 ms nach Bereinigung, eine Spitze zwischen zwei Stichproben ist nicht gemessen“ steht im Protokoll (Lesart und „Nicht gemessen“), in der Nutzer-Doku im Satz und in `docs/plan/referenz-workload.md`. Der 7,5-MiB-Ausreißer ist benannt und nicht untersucht. Die vier Fremdlast-Zellen sind als Größenordnung gekennzeichnet. Staffel: 1, 8, 16 von 1, 8, 16, 64 (S4); „bis 16“ ist erfüllt. Einschränkung: drei Läufe je Zelle auf einem Host, ein Ausreißer — Streuung gezeigt, keine Verteilung. |
| **Abschluss** (Verzögerung in ms mit Rohbytes-Client, **als Abstand letzte Zeile → DONE**, D = 0/10/100/1000, Messzeile je Wert) | **Messung getragen, Wortlaut nur formal erfüllt.** Eine Zeile je Wert und Form (8 Zeilen, je 5 Läufe + Warmlauf). Der DoD-Abstand ist in 40 von 40 Läufen 0 µs (letzte Zeile und DONE im selben Paket) und enthält keine Information über D. Der Slice liefert die Verzögerung stattdessen als „Zeile emittiert → DONE am Client“ (Median D plus 0,4 bis 1,6 ms). Das Protokoll sagt das offen. **Ob die DoD in diesem Wortlaut als erfüllt gilt, ist Sache des Planners vor der Closure** (nicht hier entschieden): Option Wortlaut ändern auf die gelieferte Größe, oder die Messgröße im Slice als Setzung benennen. |
| `make gates` grün | **Belegt** (Exit 0 mit `FORCE=1`, dazu `clean-build`, `e2e`, frischer Selbsttest, `docs-check` auf `d3d55a91`). |
| Review mit Report | **Belegt:** Review und Re-Review im Branch; alle Findings behoben bis auf N-1, siehe V-1. Letzter Commit nur Text. |
| Doku-Update (Messprotokoll; ADR-Satz bleibt; Nachzug in Spec nur wo genannt) | **Erfüllt:** Protokoll mit Rohzeilen unter `docs/messungen/` (`AGENTS.md` §5 Regel 7); [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md) im Diff unverändert (§3.5); Nachzug in `SPEC`-Abschnitt der Verzögerung (`spec/spezifikation.md`) und `docs/user/einbettung-und-betrieb.md`, beide mit Bedingungen im Satz (Rohbytes-Client, `TCP_NODELAY` des Clients, Wirkung nicht geprüft, Ursache aus dem Quelltext gelesen). |
| Closure-Notiz, Register, Risiken aus §6, drei Paarungen | **Noch offen, wie vorgesehen** (Closure steht aus; §6 „offen bis zur Closure“). Zum Eintragen: Risiko 1 (Spitze zwischen Stichproben) eingetreten als Grenze, nicht aufgelöst; Risiko 2 (Messbackend) gilt unverändert; Risiko 3 (Laufzeit) entfallen (längster Lauf 747 s, Messfenster 2 h 18 min). |

## Die vier offenen Nutzerfragen

Im Abschnitt „Offen, Entscheidung beim Nutzer“ des Protokolls (seit `d3d55a91`) als offen geführt, jeweils mit Optionen „Code angleichen“ und „Zielbild anpassen“: (1) letztes Paket flushen oder festschreiben, (2) `TCP_NODELAY`, (3) Wortlaut „Abstand“ der DoD, (4) Vorschau gegen Pufferung (nicht getrennt, keine Variante ohne Vorschau). Der Abschnitt sagt ausdrücklich, dass Spezifikation, DoD und Produktquelltext dazu nicht geändert sind; `git diff 077e448c HEAD -- src/` ist leer, die DoD-Zeile unverändert. Die Spec führt das Verhalten weiter als **gemessenen Ist-Zustand** mit Bedingungen („nicht geprüft“, „aus dem Quelltext gelesen“), nicht als Zusage. Nichts davon ist still festgeschrieben. Bestätigt.

## Findings

| ID | Schwere | Befund |
|---|---|---|
| V-1 | MEDIUM | **Spalte `vorletztes_paket_bis_done_us` ohne rot gesehenen Test am Aufrufer** (Re-Review N-1; offen gehalten, im Protokoll abschnitt „Keine rot gesehene Mutation“ ehrlich benannt). Der Kommentar im Protokoll-Punkt „Mit dem Abschluss warten auch Zeilen“ stützt „das vorletzte Paket traf höchstens 0,4 ms vor und 0,1 ms nach der Übergabe der letzten Zeile ein“ auf diese Spalte, ohne dort auf die Lücke zu verweisen (sie steht nur im Mutationsabschnitt). Gegenprobe aus den Rohzeilen: `erste_zeile_us` liegt bei D = 1000 ms in allen 10 Läufen bei 0,8 bis 1,3 ms, und „vorletztes Paket → DONE“ liegt nahe der Zeile „emittiert → DONE“ (Differenz unter 0,4 ms): die Aussage ist plausibel, aber nur plausibel. Vorschlag: den Satz am Ort mit „ungeprüft am Aufrufer“ versehen oder den Aufruf mit einem Test decken (Code, dann Re-Review). |
| V-2 | LOW | Spec und Nutzer-Doku sagen „Die Zeilen davor kamen ohne Verzögerung an (belegt an der Zahl der Zeilen vor dem letzten Paket …)“. Die Zahl (960 von 1000 vor dem letzten Paket, 40 von 40) belegt „vor dem Abschluss“, nicht „ohne Verzögerung“: ein Paket, das nach D − 1 ms ankäme, zählte ebenso. Getragen wird „ohne Verzögerung“ erst von der Zeitspalte (V-1) und von `erste_zeile_us`. AGENTS §3.8: den Satz auf „vor dem Abschluss, nicht um D verzögert, soweit die Zeit des vorletzten Pakets (ungeprüft am Aufrufer) trägt“ einschränken oder die Zeitspalte absichern. |
| V-3 | INFO | Der Beleg „Differenz der Mediane“ (Punkt 12 oben) vergleicht Mediane verschiedener Größen, nicht gepaarte Differenzen je Lauf; als Größenordnung zulässig, im Text aber ohne diese Einschränkung. |
| V-4 | INFO | Die Rohzeilen des Messlaufs tragen die alte Form der Zeile `Kopf` (Re-Review N-3, Inhalt gleich); Rohzeilen sind unverändert gelassen, richtig. Die Zellen `heap-*` laufen auf `e90c2399`, `abschluss` auf `ce8ff0ca`; die Spitze `d3d55a91` hat Server, Referenzclient und Abschlusslogik seither nur in Tests und Hilfen der Tabellen geändert (`822d6a21` berührt `AbschlussClient.kt` und `tools/heap-abschluss.sh`, laut Re-Review verhaltensgleich) — **der Messlauf ist nicht auf dem End-Stand gefahren**; der Zusammenhang stützt sich auf Review, nicht auf einen Lauf. |

## Nicht gemessen (aus Protokoll, bestätigt, ergänzt)

Spitze des Heaps zwischen zwei Stichproben (Stichprobe nach GC alle 500 ms ist keine Spitze); der Heap ohne Stichprobe (Sampler-Einfluss, GC-Zeit enthält die Bereinigungen der Stichprobe); drei Läufe je Zelle, ein Lauf mit 7,5 MiB ungeklärt; ein Host, ein Tag, Fremdlast (vier Zellen Größenordnung); Uhrenpräzision und Gleichlauf der Wanduhren von Backend und Client; Variante ohne `DONE_MORE`-Vorschau (Vorschau nicht von der Pufferung getrennt); anderer Client als `Microsoft.Data.SqlClient` 7.1.1 und der Rohbytes-Client; `TCP_NODELAY` am Server (Server setzt es nicht, Wirkung ungeprüft, 40-ms-Wert aus einem Vorlauf ohne Rohzeile); Aufbau C und Backend, das vor dem Ende wartet, beim Abschluss; echter Treiber, Netz zwischen Hosts, TLS; Messlauf auf dem End-Stand `d3d55a91` (V-4).

## Hand-off an den Planner

1. Entscheiden (mit dem Nutzer): DoD-Wortlaut „Abstand letzte Zeile → DONE“ (0 µs, ohne Aussage) gegen „Zeile emittiert → DONE“; die drei anderen Fragen laufen weiter beim Nutzer.
2. V-1 und V-2: Einschränkung der zwei Sätze (Text) oder Test am Aufrufer (Code, dann Re-Review) vor der Closure.
3. Closure-Notiz, Register, Risiken §6 sind noch zu schreiben; ich habe keinen Haken gesetzt und nichts bewegt.
