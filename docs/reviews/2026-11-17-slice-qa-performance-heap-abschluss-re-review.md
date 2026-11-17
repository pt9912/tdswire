# Review-Report: slice-qa-performance-heap-abschluss, Re-Review der Nachbesserung — 2026-11-17

**Review-Art:** Re-Review nach dem Skill-Punkt „Nachbesserung mit Code ohne zweiten Review“; nur die Commits nach dem Report-Stand, gegen Plan, ADR und Hard Rules; nicht gegen die DoD.

**Gegenstand:** `git diff 0fb14ce1..f114d8bf` (zwei Commits `822d6a21`, `f114d8bf`; 10 Dateien): `testserver/kotlin/.../AbschlussClient.kt`, `testserver/test/kotlin/.../HeapAbschlussTest.kt`, `tools/heap-abschluss.sh`, `tools/heap-abschluss-selbsttest.sh`, `tools/heap-abschluss-tabellen-selbsttest.sh`, Messprotokoll, `spec/spezifikation.md`, `docs/user/einbettung-und-betrieb.md`, `docs/plan/referenz-workload.md`, der Report des ersten Reviews.

**Skill:** `.harness/skills/reviewer.md` @ `f114d8bf` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-17

**Eingangs-Kontext:** erster Review [2026-11-17-slice-qa-performance-heap-abschluss](2026-11-17-slice-qa-performance-heap-abschluss.md) (kein HIGH, F-1 bis F-4 MEDIUM, F-5 bis F-10 LOW, F-11 bis F-13 INFO), [TDS-QA-04](../../spec/lastenheft.md#tds-qa-04--performance), [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md) (`Accepted`, im Diff unverändert), `AGENTS.md` 3.2, 3.7, 3.8.

**Selbst gefahren** (Last 2,7 beim Start, 15 GiB verfügbar; keine künstliche Last, kein `make heap-abschluss`, kein `clean-build`, kein `e2e`):

- `make flaeche`, `make kopplung-zahlen`, `make gate-zuordnung`, `make stempel-eingaenge-pruefung`: je Exit 0. `make docs-check`: siehe Ende.
- Frischer `docker build --no-cache --target skript-selbsttest .` auf dem Stand `f114d8bf`: Exit 0; `heap-abschluss-selbsttest` 0 Abweichungen, 15 Mutationen rot (darunter `grenzen-des-servers` 2 Prüfungen, `grenzen-des-clients` 2); `heap-abschluss-tabellen-selbsttest` 13 Mutationen rot (darunter `zellenzahl`, `schwelle-ueber300`, `more-bit-fest`, je 1 Zeile). Die Zahlen 15 und 13 stehen in Kopf der Skripte und im Protokoll und stimmen mit der Zahl der `mutiere`-Aufrufe überein (Grep: 15 und 13).
- Enge Testklasse an einer Kopie des Stands (`git archive`, geänderte Stage `test`: `:testserver:test --tests '*HeapAbschlussTest'`, je Lauf rund 37 s): `BUILD SUCCESSFUL`, XML-Bericht `tests="10" skipped="0" failures="0"`, zehn `testcase`-Namen (die zwei neuen `das Paket vor dem letzten bestimmt Zeit und Zeilen` und `die Tabelle des Heap-Fensters im Aufbau C liefert das Fenster` stehen darunter). Der Umbau `f114d8bf` (Erweiterungsfunktionen `rowsBefore`, `arrivalBefore`) ändert das Verhalten nicht: gleiche Formel, gleiche Leerwerte (0, −1), 10 von 10 laufen. Der neue Test `die Tabelle des Heap-Fensters im Aufbau C …` hat `= runBlocking<Unit> { … }`, also Rückgabe `Unit`, und läuft (im Bericht gezählt).
- Mutationen des Kotlin-Codes an Kopien, einzeln gefahren:

| Mutation | Wirkung (gemessen) |
|---|---|
| `rowsBefore`/`arrivalBefore`: `packetArrivals[packet - 2]` zu `packetArrivals[0]` (die Mutation aus F-1, an der Stelle der Formel) | rot: `das Paket vor dem letzten bestimmt Zeit und Zeilen` (1) |
| `messeAbschluss`: `answer.rowsBefore(lastRow.packet)` zu `rowsBefore(2)` | rot: `die Zeilen des letzten Pakets warten mit dem Abschluss` (1) |
| `messeAbschluss`: `answer.arrivalBefore(lastRow.packet)` zu `arrivalBefore(2)` (am Aufrufer; bei mehr als zwei Paketen das Paket 0 statt des vorletzten) | **grün**, 10 Tests ohne roten (N-1) |
| `heapTabellen`: das Fenster `heap.read().values()` durch acht Nullen ersetzt (die Mutation aus F-2) | rot: `die Tabelle des Heap-Fensters im Aufbau C liefert das Fenster` (1) |
| `einzeilTabelle`: `gelesen = true` zu `gelesen = false` (zweites Lesen liefert noch eine Zeile) | rot: derselbe Test (1) |
| `heapTabellen`: Tabellenname `HEAP_TABLE` zu `"x"` | rot: derselbe Test (1) |

- Drei Mutationen des Tabellen-Werkzeugs an einer Kopie, Selbsttest von Hand gefahren (bash, awk), anders geformt als die eingebauten: Zeile „Zellen mit Proben über 300 %“ fest `keine`: **rot** (1 Abweichung); die Angabe `max` aus der Aufzählung entfernt: **rot** (1); Spalte `DONE_MORE` (`kmore[key]`) fest `"x"`: **rot** (2). F-3 ist behoben.

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| N-1 | MEDIUM | Die Zeit des vorletzten Pakets trägt am Aufrufer weiter kein Test: `previousPacketToDone = … done.micros - answer.arrivalBefore(lastRow.packet)` mit `arrivalBefore(2)` statt `arrivalBefore(lastRow.packet)` (bei `packets > 2` das frühe Paket 0 statt des vorletzten, also dieselbe Wirkung wie die Mutation `packetArrivals[0]` aus F-1) lässt alle 10 Tests grün (gefahren): `die Zeilen des letzten Pakets warten mit dem Abschluss` prüft nur die Untergrenze `>= DELAY_MS * MICROS_PER_MILLI / 2`, und der neue Test prüft nur den Helfer, nicht den Aufruf. Für die Zeilen (`rowsBefore`) trägt der Aufruf ein Test (rot). Das Protokoll sagt „die Zeit des vorletzten Pakets (`arrivalBefore` am Paket 0 statt am Paket vor dem letzten) färbt `das Paket vor dem letzten bestimmt Zeit und Zeilen` rot“ und streicht sie aus der Liste „Keine rot gesehene Mutation“: das gilt für den Helfer, nicht für die Spalte `vorletztes_paket_bis_done_us`. Szenario: ein falscher Index am Aufrufer ändert die Spalte, auf der der Satz „das vorletzte Paket traf höchstens 0,4 ms vor und höchstens 0,1 ms nach der Übergabe der letzten Zeile ein“ und daraus „die Zeilen davor kommen ohne Verzögerung“ ruht, und kein Test wird rot. | Skill MEDIUM „Eigencode, dessen Entfernung keinen Test rot macht“ und „Mutationsbeschreibung nennt die gedachte statt der gemessenen Wirkung“ (F-1 zur Hälfte behoben) | `AbschlussClient.kt` · „previousPacketToDone = if (lastRow.packet > 1) done.micros - answer.arrivalBefore(lastRow.packet) else -1“; Protokoll · „färbt `das Paket vor dem letzten bestimmt Zeit und Zeilen` rot“ | ja (Mutation, Tabelle oben) | Formel getestet, ihr Aufruf nicht |
| N-2 | INFO | Die vier offenen Fragen des Nutzers (letztes Paket flushen oder festschreiben, `TCP_NODELAY`, DoD-Wortlaut „Abstand“, Vorschau gegen Pufferung) werden in keinem Text des Diffs als „offen, Entscheidung beim Nutzer“ geführt (Grep `entscheid`, `offen`, `gewollt`, `festschreib` in Protokoll und Slice-Plan: keine Treffer zu diesen Fragen). Nichts davon ist still festgeschrieben: Spezifikation und Nutzer-Doku nennen `TCP_NODELAY` jetzt mit Bedingung („der Server setzt es nicht; ob das eine Rolle spielt, ist nicht geprüft“), das Protokoll nennt „nicht getrennt von der Pufferung“, „eine Variante ohne Vorschau ist nicht gemessen“ und die DoD ist im Diff unverändert. Dass die Zeilen des letzten Pakets warten, steht weiter als Ist-Zustand ohne den Satz, dass die Entscheidung aussteht (unverändert gegenüber F-11 des ersten Reviews, kein neuer Befund). | `AGENTS.md` 3.8 | Protokoll · „Mit dem Abschluss warten auch Zeilen“ | nein | Entscheidung offen, nicht als solche geführt |
| N-3 | INFO | `tools/heap-abschluss.sh` schreibt die Zeile `Kopf server [--cpus 2 --memory 4g --memory-swap 4g] xmx 256m client [--cpus 2 --memory 2g --memory-swap 2g] …`; die Rohzeilen des Messlaufs (`performance-heap-abschluss-2026-11-17-rohzeilen.txt`) tragen die alte Form `Kopf server cpus 2 memory 4g memory-swap 4g xmx 256m client cpus 2 memory 2g …`. Der Inhalt ist gleich (`--memory-swap 2g` des Clients stand im Lauf schon im `docker run`), kein Werkzeug liest die Zeile (Grep: nur Tabellen-Werkzeug, es liest sie nicht). Ein Leser, der die Rohzeilen mit einem neuen Lauf vergleicht, sieht zwei Schreibweisen. | Maintainability | `tools/heap-abschluss.sh` · „Kopf server [$server_grenzen] xmx $xmx client [$client_grenzen]“ | nein | Zeilenformat der Rohdaten ändert sich |

## Prüfung je Finding des ersten Reviews

- **F-1** (Formel `vorletztes Paket → DONE`): am Helfer behoben (Mutation `packetArrivals[0]`: rot), am Aufruf nicht (N-1).
- **F-2** (Aufbau C): behoben. Fenster aus Nullen, zweites Lesen und Tabellenname: je rot. Der Test liest die Werte in der Reihenfolge der Spalten (`2, 1, 100, 300, 300, 300, 777, 0`) aus drei Stichproben mit verschiedenen Werten, nicht aus einer einheitlichen Eingabe.
- **F-3** (Tabellen-Selbsttest): behoben; die drei eingebauten Mutationen sind rot, drei eigene, anders geformte ebenfalls. Die Erwartung enthält die Zeile `| Zellen | 2 |`, die Zeile „Zellen mit Proben über 300 % fremder CPU“ und eine Form `folge` mit `more_bit true`.
- **F-4** (Bedingungen im Satz): behoben. Spezifikation und Nutzer-Doku nennen den Rohbytes-Client, `TCP_NODELAY` des Clients, dass der Server es nicht setzt, die Wirkung „nicht geprüft“, den Vorlauf mit rund 40 ms und die Ursache als „aus dem Quelltext gelesen und nicht gemessen“.
- **F-5** (Zählung der Zellen): behoben; die Lesart nennt `heap-eigen-1` (13:15, 2 Proben), `heap-sim-1` (13:17, 2 Proben), `heap-sim-16` (14:27 und 15:17, je 1 Probe); die Tabelle zeigt 452 %, 440 %, 389 % und 374 % bei 2, 2, 1 und 1 Proben.
- **F-6** (Grenzen aus einer Quelle): behoben. `server_grenzen`, `client_grenzen`, `xmx` stehen einmal im Skript, `docker run` und die Zeile `Kopf` lesen sie; die Mutationen `8g` am Server und `1g` am Client sind rot (je 2 Prüfungen: am `docker run` und in der Zeile `Kopf`). Der Kommentar nennt die Zusage (einzige Quelle) und die Kopplung zum Selbsttest. `HEAP_PROBE_S` ist als Umgebung des Selbsttests im Kommentar ausgewiesen; der Selbsttest fährt damit den ganzen Lauf mit einem Docker-Stub (`fall ganz`). Das ungequotete `$server_grenzen` ist gewollt (Wortteilung der Optionen); kein ShellCheck-Gate im Repo.
- **F-7** (Zahl der roten Tests): behoben, die Zeile nennt zwei Tests; gefahren im ersten Review.
- **F-8** (Genauigkeit ohne Rohzeile): behoben („rund 101 ms“, „rund 40 ms“, „Größenordnung, ohne Rohzeile“, „nicht reproduzierbar“).
- **F-9** (KDoc „25 ms danach“): behoben, die Beobachtung ist durch die Abgrenzung „weil der Kanal es später senden darf“ ersetzt. Die neuen KDoc (`Die Zeit des vorletzten Pakets …`, `Aufbau C: …`) stehen im Indikativ über den geltenden Zustand (Klasse Zusage, `AGENTS.md` 3.7); kein Konjunktiv, keine verworfene Alternative. Die Kommentare im Skript (`Grenzen der Container: die einzige Quelle …`, `Abstand der Fremdlast-Proben …`) tragen Zusage und Kopplung.
- **F-10** (Satz mit Widerspruch, `und`, Folge-Slices): behoben. Nutzer-Doku: „3,5 bis 4,8 MiB als größtem Wert je Lauf (in einem von 18 Läufen … 7,5 MiB)“ und die 329 Byte „aus einer früheren Messung, hier nicht gemessen“ (Quelle: `performance-durchsatz-2026-11-17.md`, Spalte 329, und `lasttest-probe-2026-11-15.md`); `referenz-workload.md`: Komma im Kopfsatz, die Zeile „Container gegen Swap“ verweist auf die Heap-Messung (`memory.swap.max` 0 in jeder Serverzeile). Greps `Folge-Slices` in den geänderten Dateien: kein Treffer zur Heap-Messung mehr.
- **F-11, F-12, F-13** (INFO): F-11 siehe N-2; F-12 und F-13 gehören zur Closure beziehungsweise sind Hinweise; die Zeile `# Host: bash, awk, git, docker, nproc, mktemp, sed, grep, tr, seq, sleep.` in `tools/heap-abschluss.sh` nennt die Host-Programme jetzt im Kopf des Skripts (F-13).

## Negativbefund (geprüft, ohne Befund)

- `testserver/kotlin/` (`AbschlussClient.kt`): Umbau `f114d8bf` verhaltensgleich (Erweiterungsfunktionen `internal`, KDoc mit Zusage und Leerwert); kein `@Suppress`, `@SuppressWarnings`, `noqa`, `nolint`, `SuppressMessage` im Diff (Grep, 0 Treffer); keine Schichtregel berührt.
- `testserver/test/kotlin/` (`HeapAbschlussTest.kt`): 10 von 10 Tests laufen, keiner mit Rückgabe ungleich `Unit`; keine Test-Naht im Produktivcode (die Helfer haben Produktnutzen in `messeAbschluss`).
- `tools/` (`heap-abschluss.sh`, beide Selbsttests): kein Host-Aufruf von Gradle oder Java (`AGENTS.md` 3.1); der Selbsttest läuft in der Stage `skript-selbsttest` frisch grün; `make gate-zuordnung` und `make stempel-eingaenge-pruefung` Exit 0 (keine neue Datei, die Eingangslisten bleiben gültig).
- `docs/messungen/`: Zahlen des Diffs (10 Tests, 15 und 13 Mutationen, Zählung der Zellen) gegen Code und Rohzeilen gelesen, stimmen; die Tabelle der Mutationen hat die zwei Tests bei `delay(...)`.
- `spec/`, `docs/user/`, `docs/plan/`: nur die genannten Sätze berührt; Grep auf `nicht gebaut`, `Zielzustand`, `vorläufig`, `bisher`, `noch nicht`, `soll`, `seit Slice` in den neuen Sätzen: keine Treffer; `spec/lastenheft.md`, `spec/architecture.md` und alle Accepted-ADRs im Diff unverändert; DoD-Haken im Diff nicht gesetzt.
- Plan-Tabelle gegen Diff: der Diff berührt keine Datei, die nicht schon im ersten Review gelistet war (alle zehn stehen in der Tabelle von Slice §3 beziehungsweise sind Doku zu den dort genannten Stellen); kein neuer Befund.

## Re-Review-Pflicht

N-1 ist eine Test-Ergänzung (Code); sie braucht vor dem Verifier einen weiteren Re-Review nur, wenn sie den Quelltext ändert. Eine Änderung nur am Protokolltext (Einschränkung der Aussage auf den Helfer) ist eine Doku-Korrektur.

## Nach dem Anlegen

`make docs-check` nach dem Anlegen: siehe die Meldung des Reviewers an den Aufrufer (Exit-Code und Dateizahl).
