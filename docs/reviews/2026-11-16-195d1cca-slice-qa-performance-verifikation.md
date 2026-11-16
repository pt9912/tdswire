# Verifikationsbericht: slice-qa-performance, Stand 195d1cca — 2026-11-16

**Rolle:** Verifier (Frage: bauen wir es richtig, gegen DoD, Plan und Entscheidungen). Kein Review, keine Reparatur; die DoD-Haken im Plan sind absichtlich offen und bleiben es.

**Gegenstand:** Zweig `slice-qa-performance`, HEAD `195d1cca`, Basis `main` `321fd7b1`. Plan: `slice-qa-performance` (`in-progress/`), [TDS-QA-04](../../spec/lastenheft.md#tds-qa-04--performance), [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md), [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 5, [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md), Spec-Stellen `SPEC-017`, `SPEC-018`, `SPEC-019` (Abschnitt 5 der [Spezifikation](../../spec/spezifikation.md)). Eingang: Reviews `2026-11-16-b163a5ce-slice-qa-performance-review.md` und `2026-11-16-77d485a6-slice-qa-performance-re-review.md`, Messprotokoll `docs/messungen/lasttest-probe-2026-11-15.md`, `docs/plan/referenz-workload.md`.

**Änderungsart:** `src/` unberührt (`git diff 321fd7b1..HEAD --stat -- src` leer), `testserver/` berührt (neue Dateien), dazu Docker, Skripte, .NET-Client, Doku. Daher laut `harness/README.md` §Minimal agent workflow: `make clean-build` und `make e2e` je einmal auf dem End-Stand, Mutationen über enge Testklassen. Der Skill `closure-note-reviewer` ist nicht einschlägig (§7 des Plans ist noch leer, kein Slice in `done/`).

## Urteil in Kürze

- **Code und Werkzeug tragen.** `make clean-build` Exit 0 (alle fünf Fälle von `LasttestTest` PASSED), `make e2e` Exit 0 in 9 Stufen (Stufe 9 grün), `make skript-selbsttest` ohne Cache grün. Kein HIGH, kein MEDIUM.
- **Die DoD ist nicht vollständig**, wie vorgesehen: Workload-Freigabe, Closure-Notiz, Register, Risiko-Ausgänge, Paarungen stehen aus (Tabelle unten). Das ist kein Mangel des Codes.
- Drei LOW, vier INFO. Die Gegenproben der vier vom Implementer nicht rot gesehenen Prüfungen sind unten einzeln benannt; **zwei bleiben ohne Sensor im Repo** (A-Auswertung, Vermerk in A-Zeilen).

## 1. DoD, Zeile für Zeile (§2 des Plans)

| # | DoD-Zeile | Urteil | Beleg |
|---|---|---|---|
| 1 | Workload-Dokument freigegeben | **nicht erfüllt, offener Punkt der DoD (kein Mangel des Codes)** | Kopf von `docs/plan/referenz-workload.md`: „die Freigabe dieses eingearbeiteten Stands steht aus (Review, dann Nutzer)“. Die Antworten O1 bis O7 sind eingearbeitet (Abschnitte 3 bis 6, O5 in der Fragetabelle mit Auslegung „Stufe 9 führt aus, Probengröße“, vom Nutzer bestätigt 2026-11-16). Der Plan nennt die Freigabe als Bedingung; sie liegt beim Nutzer. |
| 2 | Lasttest-Werkzeug im Repo | **erfüllt** (Hinweis F-4) | `tools/mk/lasttest.mk`: Target `lasttest`, nicht in `GATE_CHECKS` (`grep` in `tools/mk`, `harness/mk`, `repo.mk`, `Makefile`: nur der Kommentar). Docker: `Dockerfile` Stage `lasttestserver` (von `deps`/`basis`, Basis per Digest), `referenztest-dotnet/Dockerfile` Stage `lasttest` (von `restore`/`basis`, per Digest). `--cpus`, `--memory`, `--memory-swap` für Server, Client und Aufbau A (`tools/lasttest.sh`); Seed 42; `LASTTEST_WDH` Läufe nach Warmlauf mit Median, Minimum, Maximum (`tools/lasttest-statistik.sh`, Selbsttest); Messzeile mit Datum, Last und Swap (`vorher [..] nachher [..]`). Aufbauten A (eigenes Backend, Simulation, Import am Callback), B, C: im eigenen `make e2e`-Lauf je Zeile gesehen (Auszug unten, Bytes je Zeile 328 bis 337 auf dem Draht, A „kein Draht“). Erzeuger der Zahlen: `ReferenzZeilen.kt` und `ReferenzZeilen.cs` im Repo. Zeile in `harness/README.md` §Sensors vorhanden (`make lasttest`). |
| 3 | E2E-Stufe | **erfüllt** | `tools/e2e.sh` Stufe 9 mit `e2e_abdeckung "TDS-QA-04" …` ruft `make lasttest LASTTEST_ZEILEN=20000 LASTTEST_WDH=3` (Referenzclient `Microsoft.Data.SqlClient` im zweiten Container gegen den Serverprozess, nicht `internal`-Zugriff). `docs/plan/e2e-abdeckung.md` trägt die Zeile `TDS-QA-04` (nach meinem `make e2e` blieb der Arbeitsbaum sauber: die erzeugte Sicht gleicht der eingecheckten). O5 und [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md): Stufe führt aus, prüft keine Schwelle, das Target steht nicht in `make gates`; `tools/e2e-selbsttest.sh` hält die Stufe 9 (Abbruch, Reihenfolge, Argumente). |
| 4 | `make gates` grün | **belegt über Stempel und Teilläufe, kein eigener Voll-Lauf** | `git notes --ref=gates show HEAD`: Stempel `werkzeug` und `stempel` auf `195d1cca` grün; die Klasse `tests` trägt keinen Stempel auf HEAD. Von mir gefahren: `make clean-build` (Tests, detekt, Jar; Exit 0), `make skript-selbsttest` (ohne Cache, grün), `make docs-check` (1495 Dateien, 0 Befunde), `make flaeche` (grün), `make gate-zuordnung` (33 Fälle). Ein Voll-Lauf `make gates` liegt nicht in der Verifier-Pflicht der Tabelle und wurde nicht gefahren. |
| 5 | Review durchgeführt | **erfüllt** | Zwei Reports: Erst-Review `b163a5ce` (F-1 bis F-12) und Re-Review `77d485a6` (N-1 bis N-4). Zustand der Nachbesserung `195d1cca`: N-1 behoben (`referenz-workload.md` Z. 106 nennt „zuzüglich … ungemessen“, kein „tut dasselbe“ mehr), N-2 behoben (`harness/README.md` nennt `lasttest*.sh`), N-3 behoben (Regex `^(0\|[1-9][0-9]{0,8})$`, Selbsttest hat den Fall „führende Null“), N-4 siehe Gegenproben. |
| 6 | Doku-Update | **erfüllt** | `harness/README.md` (Zeile `make lasttest`, Stufenzahl 9), `README.md` („neun Stufen“), `tools/mk/e2e.mk`, `skript-selbsttest.mk`; `docs/user/` nicht berührt (kein geändertes Verhalten, wie im Plan erwartet). |
| 7 | Closure-Notiz mit Steering-Loop-Lerneintrag | **nicht erfüllt (vor Closure erwartbar offen)** | §7: „Wird bei der Closure gefüllt.“ |
| 8 | Beobachtungs-Register fortgeschrieben | **nicht erfüllt (offen)** | Im Diff keine Datei unter `docs/plan/planning/observations/`. |
| 9 | Jedes Risiko aus §6 mit Ausgang | **nicht erfüllt (offen)** | §6: drei Risiken „offen bis zur Closure“ (Workload-Freigabe, Swap und Limits, Laufzeit der Hauptstufe). Material für die Ausgänge liegt im Messprotokoll (63 s und 92 s für 100 000 Zeilen, 173 s für 1 Mio Zeilen; `memory.swap.max 0` im Container gelesen) und in meinem Lauf unten. |
| 10 | Drei Paarungen (Anker, Folge-Slice, Register) | **nicht erfüllt (offen)** | An die Closure gebunden. |

Nach dem Plan zählen die drei Liefer-Punkte (Workload, Werkzeug, Stufe); von diesen ist das Werkzeug und die Stufe erfüllt, das Workload-Dokument wartet auf den Nutzer.

## 2. Entscheidungs-Konformität

| Maßstab | Ergebnis |
|---|---|
| [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md) (Strom, Rückstau, kein Voraus-Lesen) | `ZeilenBackend` erzeugt Zeilen erst beim Zug des `Flow` (`for … emit`), hält keine Zeile; `ZeilenQuelle` ebenso. Der Slice misst nicht den Rückstau (Folge-Slices, im Plan so ausgeschlossen); die Probe-Aussagen im Messprotokoll tragen diese Grenze. Konform. |
| [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 5 (`BulkLoadingBackend` nicht öffentlich) | `grep BulkLoading` über `testserver`, `referenztest-dotnet/Lasttest`, `tools/lasttest*`: kein Treffer. Der Import in C geht über den öffentlichen `SimTable.onInsertBatch` (Nr. 4 der ADR); Aufbau B nimmt Massenladen **nicht** an und der Lauf hält den Client-Fehler fest (`InvalidOperationException … Cannot access destination table`, im `make e2e`-Lauf gesehen). Es entsteht keine öffentliche Fläche für Massenladen eines eigenen Backends. Konform. |
| [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md) | siehe DoD 3: kein Gate, Stufe im E2E, Selbsttest über die Skripte in `skript-selbsttest` (Klasse `werkzeug`). Konform. |
| `SPEC-017`, `SPEC-018`, `SPEC-019` | Im Plan als „Dauer je Schicht als Quelle der getrennten Messung“ benannt. `src/main` und die Spans sind **nicht berührt**; das Werkzeug trennt die Schichten über die Differenz der Aufbauten (A, B, C), nicht über `durationMs` der Spans. „Mit Senke“ lässt `LOG_LEVEL=DEBUG` laufen (Telemetrie als Last), liest die Spans aber nicht aus. Konform; Hinweis F-7. |
| Keine neue öffentliche API, kein ADR | `git diff 321fd7b1..HEAD --stat -- src spec docs/plan/adr` leer; neue Typen in `testserver/` sind `internal` oder `fun main()`; `make flaeche` grün; kein `@Suppress` in den neuen Dateien (`grep`: 0). Konform. |

## 3. Plan-vs-Code-Diff (`git diff 321fd7b1..HEAD --stat`)

25 Dateien, +1396 −79. Gegen die Tabelle §3 des Plans (nachgezogen in `008bc6f4`):

| Plan-Zeile | Im Diff |
|---|---|
| `docs/plan/referenz-workload.md` update | ja (131 Zeilen) |
| Lasttest-Werkzeug (Skript, Make-Fragment, Erzeuger, Backends) | `tools/lasttest.sh`, `lasttest-statistik.sh`, `lasttest-selbsttest.sh`, `tools/mk/lasttest.mk`; `testserver/kotlin/…/ReferenzZeilen.kt`, `LasttestBackends.kt`, `LasttestServer.kt`, `LasttestAllein.kt`; `testserver/test/…/LasttestTest.kt` |
| `tools/e2e.sh`, `docs/plan/e2e-abdeckung.md` | ja |
| `harness/README.md`, `README.md` | ja |
| `Dockerfile`, `referenztest-dotnet/Dockerfile`, `Lasttest/*.cs` | ja (Stages `lasttestserver`, `lasttest`; COPY in `skript-selbsttest`; `LasttestTests.cs`, `ReferenzZeilen.cs`) |
| `tools/e2e-selbsttest.sh`, `tools/mk/e2e.mk`, `tools/mk/skript-selbsttest.mk` | ja |
| `docs/messungen/lasttest-probe-2026-11-15.md` | ja (neu, 70 Zeilen) |
| nicht in der Plan-Tabelle | der Plan selbst (5 Zeilen) und die zwei Review-Reports unter `docs/reviews/` (Artefakte der Rollen, kein Code). Keine Datei im Diff ohne Plan-Zeile; keine Plan-Zeile ohne Diff, außer der Freigabe des Workload-Dokuments (DoD 1). |

## 4. Eigene Läufe (End-Stand `195d1cca`, Arbeitsbaum sauber, nacheinander, im Vordergrund)

| Lauf | Ergebnis | Dauer |
|---|---|---|
| `make skript-selbsttest` (Cache), dann `BUILDER_FLAGS=--no-cache` | Exit 0; `lasttest-selbsttest: ok (6 Faelle, Eingabefehler, 3 Mutationen rot, sieben abgewiesene Umgebungswerte, 1 Mutation rot)`; `e2e-selbsttest: ok (… Stufen 1, 2, 5, 7, 8 und 9 …, 9 Stufen)` | 1 s, 9 s |
| Probe: `awk` in der Stage (siehe Punkt 6) | `mawk 1.3.4 20240123` unter `/usr/bin/awk` | 12 s |
| `make clean-build` (`--no-cache`) | **Exit 0**; `LasttestTest`: 5 von 5 Fällen PASSED; detekt, Jar, Tests grün | **637 s** |
| `make e2e` | **Exit 0, 9 Stufen grün, 420 s**: Stufe 1 84 s, 2 109 s, 3 56 s, 4 40 s, 5 15 s, 6 15 s, 7 34 s, 8 11 s, **9 56 s** (`make lasttest`, 20 000 Zeilen, 3 Läufe; davon Bau 30 s, Messung 25 s) | 420 s |
| Mutationen an `LasttestTest` (ein `make test`, Punkt 5) | Exit 2, vier erwartete rote Fälle | 46 s |
| `make docs-check`, `make flaeche`, `make gate-zuordnung` | grün (0 Befunde; 33 Fälle) | 3 s |

Auszug der Messzeilen aus Stufe 9 (20 000 Zeilen, Median ms): A Erzeuger 26, A Simulation 32, A Import 25; B mit Senke 213, ohne 191; C Export mit 201, ohne 217; C Import mit 134, ohne 141; Server-Spitze (cgroup) 100 bis 112 MB bei `-Xmx256m`; Streuung in jeder Zeile `ok`. Die vier Zeilen von C trugen den Vermerk `swap-gewachsen` (fremde Prozesse auf dem Host), A und B nicht. Diese Zahlen sind eine Einzelmessung des Verifiers, **keine Messreihe und kein Grenzwert**; sie stützen die Aussage des Messprotokolls nur dahin, dass das Werkzeug läuft und trennt.

**Gesamtlaufzeit der Sensoren** rund 1130 s (19 min: 637 + 420 + 46 + Kleinkram), Wanduhr der ganzen Verifikation rund eine Stunde mit Lesen und Warten. **Größte Posten:** `make clean-build` 637 s (die zwei größten Gradle-Schritte 218 s und 194 s, nach dem Log je ein Stage-Lauf mit `--no-cache`, die Zuordnung zu Stages ist im Log nicht eindeutig), `make e2e` 420 s (Stufen 1 und 2 zusammen 193 s, Stufe 9 56 s).

**Speicher des Hosts** (`free -m`, verfügbar): vor `skript-selbsttest` 11 607 MiB; nach 11 518; vor `clean-build` 11 555, mitten im Lauf Minimum 8 571, nach 11 664; vor `make e2e` 11 571, nach 12 376; vor und nach dem Mutationslauf 12 652 und 12 900. Nie unter 4 GB (Schwelle der Anweisung); der Wächter von `tools/lasttest.sh` (4096 MiB) griff nicht. Swap des Hosts belegt (Auslager 14 171 MiB vor dem Lauf), von fremden Prozessen. Keine künstliche Last, keine parallelen Docker-Builds; Last des Hosts 5,6 bis 9 durch fremde Läufe. Aufgeräumt: die Container und Images des Werkzeugs (`tdswire-lt-*`) trägt der Lauf selbst ab (`docker ps -a`, `docker images`, `docker network ls`: kein Rest); fremde Images und Container nicht angefasst.

## 5. Gegenproben (rot sehen)

Der Implementer hat im Messprotokoll („Nicht belegt“) fünf Prüfungen ohne gesehenes Rot genannt. Eigene Gegenproben, alle an Kopien im Scratchpad oder mit sofortigem `git checkout`; Arbeitsbaum am Ende sauber (`git status --short` leer).

| Prüfung | Gegenprobe | Ergebnis |
|---|---|---|
| Positionelle awk-Auswertung der A-Zeilen (`$5 >= 1`, `$9`) in `tools/lasttest.sh` | Kopie mit docker-Stub, der A-Zeilen mit unterscheidbaren Werten liefert (Erzeuger 110, 120, 130; Simulation 210, 220, 230; Import 310, 320, 330; Warmlauf 900). Original: Mediane 120, 220, 320 richtig. Mutation `print $9` → `print $7`: Mediane 100, 100, 100 (die Zeilenzahl statt der Zeit). | **Mutation ändert die Zahlen, bleibt aber grün (Exit 0)**: kein Sensor im Repo fängt sie. Die Auswertung ist durch meinen Stub-Handlauf belegt, nicht durch einen Test im Repo (F-1). |
| Vermerk `last-hoch` in A-Zeilen | `LASTTEST_LAST_MAX=0` im Stub-Lauf | alle drei A-Zeilen tragen ` last-hoch`; ohne die Angabe keine (Last des Hosts 8 bis 11). Rot für den Fall „Vermerk fehlt“ in der **Messzeile des echten Skripts** nur über Handlauf, nicht über einen Test (F-1). |
| Vermerk `swap-gewachsen` in A-Zeilen | Kopie, die `/proc/meminfo` durch eine Datei ersetzt, die der Stub beim Lauf des A-Containers wachsen lässt (SwapFree sinkt um rund 97 MiB) | alle drei A-Zeilen tragen ` swap-gewachsen`; ohne Wachstum keine. Mutation `vermerke` aus der Import-Zeile entfernt: Import-Zeile ohne Vermerk, Export-Zeilen mit; **bleibt grün**, kein Sensor im Repo (F-1). Im echten `make e2e`-Lauf trugen die C-Zeilen den Vermerk (Positivbeleg, `vermerke()` wirkt auch an B und C). |
| Zweig „Import in B nicht abgewiesen“ | Stub-Client für Aufbau B ohne und mit der Zeile `lasttest: import aufbau B abgewiesen` | ohne: `lasttest: FEHLER Aufbau B: Import nicht abgewiesen`, Exit 1, `ROT`; mit: `gruen`. **Rot gesehen**, und im echten Lauf (`make e2e`) ist die Zeile mit der Meldung `Cannot access destination table 'ref_zeilen'` gegen den echten Server gesehen. Der Zweig ist **nicht** im Selbsttest (nur im Handlauf und im E2E). |
| `LasttestTest` außer dem Import-Fall | vier Mutationen in einem Bau (`make test`): `NOTE_NULL_EVERY` 10 → 9; `1..rows` → `1 until rows` im `ZeilenBackend`; Bedingung des Zählers `>` → `>=`; Vorgabe-Aufbau `C` → `B` | vier Fälle `FAILED`: `der Erzeuger ist deterministisch …`, `das eigene Backend liefert den Export …`, `die Tabelle des Aufbaus C … zaehlt den Import`, `die Einstellung liest die Umgebung`; der Import-Fall blieb grün (nicht mutiert, vom Implementer rot gesehen). **Rot gesehen**; Mutationen mit `git checkout testserver` zurückgenommen. |

Nicht rot gesehen, ehrlich benannt: der Zweig „Import in B nicht abgewiesen“ im Selbsttest (Handlauf ja, Test nein); die awk-Auswertung und die Vermerke in A-Zeilen (Handlauf ja, Test nein); die Summe der Container-Limits (nicht geprüft, so im Skript gesagt); der Wächter der 4096 MiB als Laufüberwachung (nur Eintrittsprüfung). Eine Prüfung der Hauptstufe (1 Mio Zeilen) liegt nicht in der Verifier-Pflicht und wurde nicht gefahren.

## 6. `awk` in der Basis der Stage `skript-selbsttest`

Der Lauf von `make skript-selbsttest` allein **belegt es nicht**: die unmutierte `tools/lasttest.sh` endet bei falschen Umgebungswerten vor dem ersten `awk`, und die mutierte Kopie (`ohne-pruefung`) erreicht `awk` nur in `speicherwacht`; ob `awk` fehlt oder da ist, ändert den Exit-Code des Stub-Laufs dort nicht (beide Male Exit 1, nicht 2). Beleg deshalb durch einen einmaligen Probeaufruf: eine vorübergehende Zeile in `tools/lasttest-selbsttest.sh` rief in der Stage `awk` auf (`awk-da`, `/usr/bin/awk`, `mawk 1.3.4 20240123`); die Zeile ist zurückgenommen. Ergebnis: `awk` liegt in der Basis (`gradle:8.14-jdk21`). Siehe F-2.

## Findings

| ID | Kategorie | Befund | Pfad | Verifizierbar |
|---|---|---|---|---|
| F-1 | LOW | Drei Prüfungen im Lasttest-Skript sind **ohne Sensor im Repo**: die positionelle awk-Auswertung der A-Zeilen (`$5`, `$9`), der Vermerk `last-hoch` und `swap-gewachsen` in den A-Zeilen, der Zweig „Import in B nicht abgewiesen“. Meine Mutationen (`$9` → `$7`; `vermerke` aus der Import-Zeile genommen) bleiben grün. Der Selbsttest hat Fälle nur für die Statistik und die Umgebungswerte. Der Implementer nennt das im Messprotokoll („Nicht belegt“) ehrlich; der Plan verlangt keinen Test dafür. Für die Folge-Slices, die auf diese A-Zeilen aufbauen, wäre ein docker-Stub-Fall im Selbsttest (wie mein Handlauf) die billigste Stütze. | `tools/lasttest.sh` · `awk '$5 >= 1 {print $9}'`; `tools/lasttest-selbsttest.sh` | ja: Mutation, Handlauf mit Stub |
| F-2 | LOW | Das Skript `tools/lasttest-selbsttest.sh` nennt `awk` im Kopf als Bedarf, **übt es aber in keinem Fall aus**: fehlte `awk` in der Basis, bliebe der Selbsttest grün. Heute liegt `awk` da (mawk 1.3.4, Probe in Punkt 6), der Bedarf ist also derzeit gedeckt, aber nicht gehalten. | `tools/lasttest-selbsttest.sh` · Kopf, `abweisung()` | ja: Probe in Punkt 6 |
| F-3 | LOW | Die Messzeile trägt Datum, Last und Swap, **den Host nicht**; DoD 2 nennt „Messzeile mit Datum, Host, Last und Swap“. Der Host (Kerne, RAM, Kernel, Docker, Commit, Image-IDs, Limits) steht im Kopf des Laufs (`lasttest: Kopf …`), je Lauf einmal. Wer nur die Zeilen weitergibt, verliert den Host. Lesart des Plans trägt (ein Lauf, ein Host); ob die DoD-Zeile Zeilen oder Lauf meint, entscheidet der Planner. | `tools/lasttest.sh` · `echo "messung: …"` | ja |
| F-4 | INFO | Das Messprotokoll („Nicht belegt“) ist durch diesen Lauf teilweise überholt: der Zweig B ist rot gesehen, vier Fälle von `LasttestTest` sind rot gesehen, die Vermerke liegen im Handlauf rot vor. Fortschreiben ist Sache des Implementers; nicht geändert. | `docs/messungen/lasttest-probe-2026-11-15.md` · Abschnitt „Nicht belegt“ | ja |
| F-5 | INFO | Die Probezahlen des Messprotokolls stammen vom Stand `321fd7b1` plus ungeprüften Änderungen und vom Nachtrag; der Verifier-Lauf auf `195d1cca` liegt in derselben Größenordnung (B 191 bis 213 ms für 20 000 Zeilen gegen 1008 bis 1183 ms für 100 000 in der Probe; C-Import 134 bis 141 ms gegen 464 bis 571 ms), mit Faktor fünf in der Zeilenzahl und ohne Anspruch auf Gleichheit. Kein Befund an der Aussage des Protokolls (Einzelmessung, kein Grenzwert). | `docs/messungen/lasttest-probe-2026-11-15.md` | ja |
| F-6 | INFO | Offene DoD-Punkte, die der Closure gehören und hier **nicht** als Mangel gelten: Workload-Freigabe (Nutzer), Closure-Notiz, Register, Risiko-Ausgänge (drei Risiken „offen bis zur Closure“), Paarungen. Zwei der Risiken lassen sich mit vorhandenen Belegen schließen (Laufzeit der Hauptstufe: 173 s für 1 Mio Zeilen und beide Aufbauten, 91 s und 64 s je Aufbau; Swap: `memory.swap.max 0` gelesen, Trennung vom Swap des Hosts nur über das Limit belegt). | `docs/plan/planning/done/slice-qa-performance.md` · §6, §7 | ja |
| F-7 | INFO | Spec-Bezug `SPEC-017` bis `SPEC-019`: das Werkzeug liest `durationMs` der Spans nicht; die Trennung von Protokollschicht und Backend ist die Differenz der Aufbauten, im Dokument und im Protokoll mit den Grenzen (Client liest mit, Zeilenbau ungemessen) benannt. Die Spans bleiben Quelle für spätere Aufschlüsselung (Folge-Slices). | `docs/plan/referenz-workload.md` · S6, Abschnitt 4 | ja |

Keine HIGH, keine MEDIUM.

## Negativbefunde (geprüft, ohne Befund)

| Bereich | Ergebnis |
|---|---|
| `src/` (Produkt) | im Diff nicht berührt; keine Schicht-, Jackson-, Modulgrenze-Frage; `make flaeche` grün |
| Öffentliche Fläche, [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 5 | kein Treffer für `BulkLoading` in den neuen Dateien; nur öffentliche Fläche der Bibliothek (`QueryBackend`, `SimTable`, `SimulationBackend`, `BackendSource`) |
| `@Suppress`, Jackson im Hauptquelltext | keine Treffer in den neuen Kotlin-Dateien; detekt in `make clean-build` grün |
| Dockerfile-Stages | Basis per Digest, `lasttestserver` vor `kompatibilitaetsserver` wie die Prüfung der letzten Stage verlangt (`make gate-zuordnung`, `make clean-build` grün); `skript-selbsttest` COPY und RUN um `lasttest-selbsttest.sh` erweitert, im Lauf grün |
| Gate-Klassen und Stempel | `tools/` als Eingang der Klasse `werkzeug`; Stempel `werkzeug` und `stempel` auf HEAD grün (Note); `make gate-zuordnung` 33 Fälle |
| E2E-Abdeckungssicht | Zeile `TDS-QA-04` mit Verweis auf `tools/e2e.sh:68`; nach dem Lauf Arbeitsbaum sauber, die Sicht gleicht der erzeugten |
| Eingabeprüfung des Skripts (N-3) | `LASTTEST_ZEILEN=08` und `…=12345678901234567890` werden im Selbsttest abgewiesen (Exit 2 vor `docker`), Mutation ohne die Prüfung rot |
| Zweite Fundstellen der Zahlen (Stufenzahl 9, `lasttest*.sh`) | `README.md`, `harness/README.md`, `tools/mk/e2e.mk`, `e2e-selbsttest.sh`-Ausgabe („in 9 Stufen“) gleich |
| Messprotokoll gegen Quelle | die Aussagen („Trennung greift“, „Import etwa doppelt so schnell“, Senke ohne Einfluss) tragen die Tabellen; keine Aussage breiter als die Messzeilen (Re-Review bestätigt, meine Stichprobe der Stufe 9 widerspricht nicht) |

## Offene Punkte für den Planner

1. Freigabe von `docs/plan/referenz-workload.md` durch den Nutzer (DoD 1); danach Kopf und Haken nachführen.
2. Entscheidung zu F-3 (Host je Zeile oder je Lauf) und zu F-1 (Stub-Fall im Selbsttest) vor oder nach der Closure.
3. Closure: Notiz mit Lerneintrag, Register, Ausgänge der drei Risiken, Paarungen; die Haken der DoD erst dann.
