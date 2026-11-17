# Verifikationsbericht: slice-qa-performance-durchsatz-sitzungen — 2026-11-17

**Rolle:** Verifier (Modul 11), frischer Kontext; DoD- und Entscheidungs-Konformität, Plan-vs-Code-Diff. Kein Review, kein Fix, keine DoD-Haken, kein Schließen, nichts nach außen.

**Stand:** Branch `slice-qa-performance-durchsatz-sitzungen`, Spitze `78a9e5d3`, Basis des Slice `c118e7e6`; Arbeitsbaum sauber vor diesem Bericht.

**Gegenstand:** Slice [slice-qa-performance-durchsatz-sitzungen](../plan/planning/in-progress/slice-qa-performance-durchsatz-sitzungen.md) (Messslice, welle-qualitaet-grundlage); [TDS-QA-04](../../spec/lastenheft.md#tds-qa-04--performance); [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md), [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md), [ADR-0052](../plan/adr/0052-oeffentliche-flaeche-fuer-massenladen-eigener-backends.md) (angenommen, nicht neu bewertet); [Messprotokoll](../messungen/performance-durchsatz-2026-11-17.md) und [Rohzeilen](../messungen/performance-durchsatz-2026-11-17-rohzeilen.txt); [Referenz-Workload](../plan/referenz-workload.md); Reviews [Review](2026-11-17-slice-qa-performance-durchsatz-sitzungen.md) und [Re-Review](2026-11-17-slice-qa-performance-durchsatz-sitzungen-re-review.md).

## Selbst gefahrene Sensoren (End-Stand `78a9e5d3`, Docker, parallel lief ein fremder Messslice, keine künstliche Last)

| Sensor | Ergebnis | Dauer |
|---|---|---|
| `make clean-build` | Exit 0 (Jar, Tests, detekt, `--no-cache`; Gradle-Stages `BUILD SUCCESSFUL`, 1432 Testzeilen `PASSED`, keine `FAILED`-Testzeile; die 3 Treffer auf „FAILED“ sind Testnamen mit PASSED) | 609 s |
| `make e2e` | Exit 0, „gruen (9 Stufen, 388 s)“; Stufe 9 `make lasttest LASTTEST_ZEILEN=20000 LASTTEST_WDH=3` grün in 35 s | 388 s |
| `make gates` | Exit 0 in 9 s: die Gruppen `werkzeug`, `tests`, `stempel` übersprungen (Beleg für den Stand); deshalb zusätzlich `FORCE=1 make gates`: Exit 0 | 9 s; 31 s (Layer-Cache des eben gelaufenen `clean-build`) |
| `make docs-check` | Exit 0 vor diesem Bericht (und nach ihm, siehe Schluss) | 2 s |
| `make flaeche` | Exit 0 | 1 s |
| `docker build --no-cache --target skript-selbsttest .` | Exit 0; Log: `lasttest-selbsttest: ok (6 Faelle, Eingabefehler, 3 Mutationen rot, elf abgewiesene Umgebungswerte, 1 Mutation rot; Handlaeufe a bis g, 14 Mutationen rot)` | 31 s |
| Nachrechnung der Rohzeilen | Python-Wegwerfskript im Scratchpad (nicht im Repo), siehe unten | — |

Gesamt etwa 21 Minuten; größte Posten `make clean-build` (609 s) und `make e2e` (388 s; Stufen 1 und 2 mit 76 s und 101 s, Stufe 9 mit 35 s). Speicher verfügbar während der Läufe 11 bis 14 GiB (Abbruchgrenze 4 GB nie berührt). Die Messläufe der Staffel habe ich nicht wiederholt.

**Aussagekraft von `make clean-build` und `make e2e` für diese Änderungsart:** `clean-build` hat volle Aussagekraft für `testserver/` (`LasttestBackends.kt`, `LasttestTest.kt` frisch gebaut und gelaufen). `make e2e` Stufe 9 hat Aussagekraft, aber **nur für den Standardpfad des neuen Werkzeugs**: der Lauf fuhr `LASTTEST_SITZUNGEN` 1 mit den Phasen `export import` (Vorgabe, `tools/lasttest.sh` Zeile 47 und 48), in den Läufen steht keine Zeile `punkt`. Der umgebaute C#-Client lief damit für Export und Import mit einer Sitzung gegen A, B und C (Import in B wird erwartungsgemäß abgewiesen, Zeile `import aufbau B abgewiesen`), **nicht** für die neuen Pfade dieses Slice (Gruppe mit N Sitzungen, Punktabfrage mit Prüfung jeder Zeile gegen den Erzeuger, Phasen `punkt-*`). Diese Pfade laufen in keinem Gate und in keiner Stufe von `make e2e`; belegt sind sie nur durch die Messläufe vom 2026-11-17 selbst (Rohzeilen, 156 Zeilen `lasttest: punkt lauf`). Das Protokoll nennt die Lücke unter „Nicht belegt“ nur für die Prüfung der Zeile (F-13 des Reviews), nicht für die Gruppe.

## Nachrechnung der Protokollzahlen (AGENTS §3.8)

Alle gegen die Rohzeilen, nichts aus dem Protokolltext übernommen:

1. **Zeilen/s je Zelle (32 `messung:`-Zeilen mit Median):** `Sitzungen × Zeilen × 1000 / median_ms` gleicht dem Feld `zeilen_je_s` in 32 von 32 Zeilen (Rundung ±1). Beispiele: Stufe 8 C Import mit Senke 8 × 1 000 000 × 1000 / 15 275 = 523 731; Stufe 64 B Export mit Senke 64 × 100 000 × 1000 / 27 989 = 228 661.
2. **Alle 48 Tabellenzeilen der vier Ergebnistabellen** (16 Export, 8 Import, 16 `punkt-export`, 8 `punkt-import`) stimmen mit der `messung:`-Zeile der Zelle überein (Median, Minimum, Maximum, Zeilen/s bzw. p50, p95, p99 je Median, Minimum, Maximum, in ms mit zwei Stellen): 48 von 48, keine Abweichung.
3. **Median aus den Läufen:** Stufe 16 B mit Senke `punkt-export`, Läufe 1 bis 5 (Lauf 0 ist der Warmlauf): p50 3726, 3495, 3349, 3408, 3467 µs, Median 3467 (Protokoll 3,47); p95 6732, 6544, 5155, 4854, 5407, Median 5407 (Protokoll 5,41, Minimum 4,85, Maximum 6,73).
4. **Senke:** 12 Paare (Export B, Export C, Import C je Stufe 1, 8, 16, 64): Median der einen Seite außerhalb von Minimum bis Maximum der anderen bei 7 von 12; Unterschiede −7,0 % bis +15,2 %; bei Stufe 1 mit Senke langsamer (+0,4, +1,9, +12,6 %). Alles wie im Protokoll.
5. **Plateau und Bereiche der Lesart:** Export ab 8 Sitzungen 198 029 bis 228 661 Zeilen/s; Import 436 323 bis 542 299 (8 bis 64), 240 963 bis 271 444 (1); `Stufe 64 C mit Senke` 32 346 gegen B 27 989 = +15,6 %, ohne Senke 28 070 gegen 29 802 = −5,8 %; alle Randwerte der Punktlesart (p50 0,21 bis 0,32 ms ohne Last; 1,71 bis 2,07 ms bei 8; p99 unter Export-Last 23,35 bis 33,21 ms, unter Import-Last 35,13 bis 43,87 / 60,15 bis 65,09 / 102,38 bis 102,77 ms) gleichen der Tabelle.
6. **Randzahlen der Bedingungen:** verfügbarer Speicher in den Rohzeilen 3816 bis 15 619 MiB (Protokoll gleich); Last 1 min 2,69 (Stufe 64 C ohne `import`) bis 15,38 (Stufe 8 B mit `punkt-export` v1) (gleich); Last vor den Zellen von `stufe1b` 4,13 bis 10,15 (gleich); Probe bis 224 % in der Zelle Stufe 1 C mit Senke `punkt-import` (gleich).
7. **Dauer je Schicht:** `n` = Sitzungen × 6 Läufe (48, 96, 384) gleich; die Zeilen der Zelle Stufe 64 C mit Senke `punkt-import` (`rpc` n = 6000, `sql_batch` n = 185 225 und 184 272, `backend.execute` Maximum 10 und 54 ms) gleich der Tabelle.
8. **Rohzeilen und Protokoll nach Re-Review:** die Zeile „Probe: nicht zugehörig …“ an Versuch 1 der Zelle Stufe 8 B mit Senke `punkt-export` (R-1 behoben, keine 729 Proben mehr); die Überschrift der Frist sagt „TDS_TIMEOUT ist aus der Dauer geschlossen“ (R-6 behoben); Quelltext des Messwerkzeugs seit `d5a751eb` unverändert (`git diff d5a751eb HEAD -- tools/lasttest.sh referenztest-dotnet/Lasttest testserver` leer).

Ergebnis: 32 + 48 + 11 Einzelzahlen und Zählungen nachgerechnet, **eine Abweichung** (V-2), sonst tragen alle Zahlen ihre Rohzeile.

## Befund je DoD-Punkt (Haken bleiben leer, kein Schließen)

Bewertet gegen die geteilte DoD (Nutzerentscheidung 2026-11-17 „DoD teilen, B später“).

| DoD-Punkt | Befund | Beleg |
|---|---|---|
| **Export:** Zeilen/s, erste Zeile, Dauer je Schicht, Aufbauten B und C, je Stufe 1, 8, 16, 64, Median/Min/Max, Bedingungen | **geliefert.** 16 Zellen (4 Stufen × B, C × mit, ohne Senke), je 5 Läufe nach Warmlauf; Dauer je Schicht (`tds.query`, `backend.execute`) nur „Senke mit“, weil ohne Senke keine Spans entstehen (Tabelle sagt es in der Überschrift, das Protokoll nicht ausdrücklich: V-5). Bedingungen: Datum, Host, Last vor und nach, Limits, Fremdlast-Probe. 7 der 16 Zellen als „gestört“ gekennzeichnet, belegt nur als Größenordnung. | Tabellen „export“ und „Dauer je Schicht“; Nachrechnung 1 und 2 |
| **Import (Aufbau C):** Zeilen/s, je Stufe, Streuung, Bedingungen | **geliefert.** 8 Zellen, alle Stufen, mit und ohne Senke, Streuung `ok`; Byte/Zeile 336 bis 337. Das Ziel ist ein Zähler (`onInsertBatch`). | Tabelle „import“ |
| **Import (Aufbau B)** | **ausdrücklich offen, nicht Teil dieses Slice**; in Slice (DoD, §1, §6) und Protokoll gleich benannt, Rohzeilen tragen keine B-Importzeile (Probelauf-Befund: ein eigenes Backend weist `SqlBulkCopy` ab, in Stufe 9 erneut gesehen). Kein Verstoß. | Slice §1, §2, §6; Protokoll „Stand der DoD-Zeile Import in Aufbau B“ |
| **Sitzungen und p95:** p50, p95, p99 der Punktabfrage, Staffel 1, 8, 16, 64, mit und ohne Senke; Messprotokoll nach `AGENTS.md` §5 Regel 7; „jede Zahl mit Erzeuger im Repo“ | **Zahlen geliefert, Wortlaut „jede Zahl mit Erzeuger im Repo“ nur teilweise erfüllt (V-1).** 16 Zellen `punkt-export` (B, C) und 8 Zellen `punkt-import` (C), je mit und ohne Senke, p50/p95/p99 mit Minimum bis Maximum über 5 Läufe zu je 1000 Anfragen; Protokoll liegt unter `docs/messungen/`. Erzeuger der Rohzahlen ist `tools/lasttest.sh` mit dem Client (im Repo, Selbsttest hält die Auswertung); die Tabellen sind mit einem Wegwerfskript gesetzt, die Fremdlast-Probe und die Schleife über die Zellen liegen außerhalb des Repos (im Protokoll offen gesagt). Zwei Zellen `zu-gross` (nach S11 nicht belegt), im Protokoll gekennzeichnet. | Tabellen „punkt-*“; Nachrechnung 2 und 3 |
| `make gates` grün | **bestätigt:** Exit 0; mit Stempel 9 s, `FORCE=1` 31 s. | siehe Sensoren |
| Review, Report unter `docs/reviews/` | **geliefert** (Review und Re-Review). Von den Findings des ersten Reviews F-1 bis F-14 und des Re-Reviews R-1 bis R-9 sind die nachgebesserten im Stand `78a9e5d3` am Text und an den Rohzeilen sichtbar behoben (R-1, R-3, R-4 mit den Randzahlen, R-5, R-6 stichprobenartig nachgesehen; R-2 und R-8 durch Grep gesehen (`A-Import` und `geliefert haben` stehen nirgends mehr), R-7 (der Satz „innerhalb der Streuung“ ist aus dem Protokoll entfernt, die +15,6 % und −5,8 % stehen), R-9 nicht gesondert geprüft). Die Codefläche der Nachbesserung (`tools/lasttest-selbsttest.sh`) ist re-reviewed. | Re-Review; Greps |
| Doku-Update (`referenz-workload.md`, `harness/README.md`) | **teilweise getan, offen bei Closure.** `harness/README.md` Zeile `make lasttest` angepasst; `referenz-workload.md` trägt die Beschreibung der Phasen und der Stufe N (S4, Muster 3). Die Messzeilen selbst stehen nur im Protokoll; die Zeilengröße auf dem Draht (Workload 3.1: 328 bis 330 Byte Export, 337 Byte Import) widerspricht den 328 bis 329 und 336 bis 337 Byte des Protokolls nicht. Plan-Zeile §3 „Messzeilen nachtragen“ ist damit nur für Beschreibung, nicht für Zahlen erledigt (V-3). | `git diff c118e7e6 HEAD -- docs/plan/referenz-workload.md harness/README.md` |
| Closure-Notiz, Beobachtungs-Register, Risiken mit Ausgang, drei Paarungen | **noch nicht getan** (§7 „Wird bei der Closure gefüllt“; zwei Risiken „offen bis zur Closure“, eines „weiter offen“ mit Träger). Erwartet vor dem Schließen; kein Verstoß. | Slice §6, §7 |

## Plan-vs-Code-Diff (`c118e7e6..HEAD`, 15 Dateien, +1691 −106)

| Plan (§3) | Diff | Befund |
|---|---|---|
| Läufe des Werkzeugs, Messprotokoll | `docs/messungen/…` (Protokoll, Rohzeilen) | vorhanden |
| `docs/plan/referenz-workload.md` | 2 Zeilen geändert (Muster 3, S4) | vorhanden, ohne Messzahlen (V-3) |
| `tools/lasttest.sh`, `tools/lasttest-selbsttest.sh` | +124/−, +117/− | vorhanden; im Zustand `d5a751eb` unverändert seit den Messläufen, danach nur der Selbsttest |
| `referenztest-dotnet/Lasttest/LasttestTests.cs`, `ReferenzZeilen.cs` | +433, +9 | vorhanden |
| `LasttestBackends.kt`, `LasttestTest.kt` | +39, +46 | vorhanden, `clean-build` grün |
| `harness/README.md` | 1 Zeile | vorhanden |
| [ADR-0052](../plan/adr/0052-oeffentliche-flaeche-fuer-massenladen-eigener-backends.md) und Index | „nicht dieser Slice“ | vorhanden, Architect, unbewertet |
| nicht im Plan | Review und Re-Review (`docs/reviews/`) | erwartet (DoD) |

Keine Datei im Diff außerhalb der Liste; kein `src/`, kein `Dockerfile`, kein `Makefile`, keine `tools/mk`-Änderung. Keine neue `SPEC-*`/`TDS-*`-Kennung, kein Gate gelockert (AGENTS §3.6), kein `@Suppress` (detekt grün).

## Entscheidungs-Konformität

- [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md): die Staffel läuft in keinem Gate, nur über `make lasttest`; eingehalten; der Protokollsatz zur C#-Seite ist wahr (belegt oben).
- [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md): Rückstau ist ausdrücklich ausgeschlossen (Folge-Slice); das Protokoll behauptet keinen.
- [ADR-0052](../plan/adr/0052-oeffentliche-flaeche-fuer-massenladen-eigener-backends.md): nur Konsistenz gelesen: Protokoll und Slice stellen den Import in B als offen und die Fläche als eigenen Slice dar, der Slice baut sie nicht. Konsistent.
- AGENTS §3.8 (Aussage trägt so weit wie die Rohzeile): im Wesentlichen eingehalten; Ausnahme V-2, Randfall V-1.
- Setzung S11 (Streuung): zwei Zellen `zu-gross` offen gekennzeichnet, nicht als belegt gelesen.

## Findings

| ID | Schwere | Befund | Beleg |
|---|---|---|---|
| V-1 | MEDIUM | Der DoD-Satz „jede Zahl mit Erzeuger im Repo“ ist für die Tabellen nicht erfüllt: sie sind mit einem Wegwerfskript außerhalb des Repos aus den Rohzeilen gesetzt, die Fremdlast-Probe (Spalte „Fremdlast“, Kennzeichnung „gestört“) und die Schleife mit Wiederholung bei Speicherabbruch liegen ebenfalls außerhalb. Das Protokoll sagt es offen und die Rohzeilen sind Beleg (ich habe 48 von 48 Zeilen neu nachgerechnet), der DoD-Wortlaut aber nicht. Entscheidung beim Planner: Aggregator ins Repo (oder `tools/`) legen oder den DoD-Wortlaut auf „Rohzahlen mit Erzeuger im Repo, Tabellen aus den Rohzeilen nachrechenbar“ einschränken. | Protokoll Kopf; Slice §2; Nachrechnung 2 |
| V-2 | LOW | Die Lesart „Aufbau B und C liegen bei 1, 8 und 16 Sitzungen innerhalb von etwa 6 % beieinander“ trägt nicht für Export ohne Senke bei 8 Sitzungen: C 211 522 gegen B 198 029 Zeilen/s = +6,8 % (bei 1 und 16 und mit Senke ≤ 4,6 %). Mit „bis etwa 7 %“ trägt der Satz. | Rohzeilen `stufe8-B-ohne-export-v1`, `stufe8-C-ohne-export-v1` |
| V-3 | LOW | Plan §3 nannte für `referenz-workload.md` „Messzeilen und Zeilengröße nachtragen“; die Datei trägt nur die Beschreibung der Phasen und der Stufe N. DoD-Zeile Doku-Update ist damit offen bis zur Closure oder der Plan-Wortlaut ist anzupassen. | Diff `referenz-workload.md` |
| V-4 | LOW | Stufe 9 von `make e2e` fährt nur Export und Import mit einer Sitzung; die neuen Pfade (N Sitzungen, Punktabfrage, Prüfung jeder Zeile) laufen in keiner Stufe und in keinem Gate, belegt nur durch die einmaligen Messläufe vom 2026-11-17. Das Protokoll benennt nur die Prüfung der Zeile. Für künftige Änderungen am Client fehlt ein Wächter; ein Folge-Punkt oder eine Beobachtung (nicht Pflicht dieses Slice, [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md) hält sie bewusst außerhalb der Gates). | `make e2e`-Log; `tools/lasttest.sh` Zeile 47 und 48 |
| V-5 | INFO | „Dauer je Schicht“ steht nur für „Senke mit“ (ohne Senke gibt es keine Spans); die DoD-Zeile Export nennt „je Stufe“ ohne diese Einschränkung; ein Satz im Protokoll würde es ausdrücklich machen. | Protokoll Tabelle „Dauer je Schicht (Senke mit)“ |
| V-6 | INFO | `make gates` lief mit Stempel in 9 s (alle drei Gruppen übersprungen, Beleg gilt für den Stand); der Frischbeleg ist `FORCE=1 make gates` (31 s, Exit 0) plus der frische Selbsttest und `clean-build`. | Gate-Log |

Kein HIGH. Die Findings des Reviews sind nach Auftrag bis auf eine INFO-Position nachgebessert; die geprüften Stellen sind im Text und an den Rohzeilen behoben.

## Ungemessen oder nicht belegt (Zusammenfassung)

- **Import in Aufbau B:** nicht gemessen, ausdrücklich offen (Nutzerentscheidung „DoD teilen, B später“, Folge-Slice nach [ADR-0052](../plan/adr/0052-oeffentliche-flaeche-fuer-massenladen-eigener-backends.md)).
- **Aufbau A:** in diesem Protokoll nicht gemessen (nur die Probe vom 2026-11-16, eine Sitzung); nicht Teil der DoD dieses Slice.
- **CPU des Clients und des Servers:** ungemessen; ob das Plateau ab 8 Sitzungen (198 000 bis 229 000 Zeilen/s Export, 436 000 bis 542 000 Import) am Server, am Client (2 CPUs für bis zu 64 Sitzungen) oder an beidem liegt, folgt aus den Zeilen nicht.
- **Einfluss der Senke:** weder belegt noch ausgeschlossen (7 von 12 Paaren außerhalb der Spanne, wechselndes Vorzeichen); Kosten einer blockierenden Senke ungemessen.
- **Fremdlast-Probe:** liegt außerhalb des Repos und ist nicht reproduzierbar; je Zelle steht nur die Zusammenfassung in den Rohzeilen. „Gestört“ ist eine Größenordnungs-Kennzeichnung (18 von 48 Tabellenzeilen tragen sie: Export 7, Import 2, `punkt-export` 8, `punkt-import` 1).
- **Ruhiger Host, Streuung über Tage und Hosts, TLS, anderer Client, d-migrate, Grenzwerte:** nicht belegt; zwei Zellen `zu-gross` nach S11 nicht belegt; Ursache des p99-Bandes unter Export-Last nicht untersucht; Versuch 1 der Zelle Stufe 8 B mit Senke `punkt-export` (p95 5,45 ms gegen 2,80 ms) zeigt Streuung Faktor 2 zwischen Läufen.
- **`TDS_TIMEOUT` bei der Frist:** aus der Dauer (30 002 bis 30 044 ms) geschlossen, in keiner Rohzeile als Code.

## Schluss

Der Slice liefert die geteilte DoD in den Zahlen: Export (B, C), Import (C), Sitzungen und p95 (Staffel, mit und ohne Senke), alle Rohzahlen nachrechenbar und nachgerechnet (80 Einzelprüfungen, eine Abweichung V-2). Offen bis zur Closure sind die erwarteten Pflichten (Closure-Notiz, Register, Risiko-Ausgänge, Paarungen) sowie V-1 (Wortlaut „Erzeuger im Repo“, Entscheidung beim Planner), V-3 und V-4. Keine DoD-Haken gesetzt.
