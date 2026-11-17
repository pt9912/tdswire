# Slice slice-qa-performance-heap-abschluss: Performance: Heap beim Strom großer Ergebnisse und Latenz des zurückgehaltenen Abschlusses

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-qualitaet-grundlage

**Bezug:** [TDS-QA-04](../../../../spec/lastenheft.md#tds-qa-04--performance), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)

**Berührte Spec-Stellen:** `SPEC-002` · `SPEC-031` (Strom und Reihenfolge der Ereignisse; der zurückgehaltene Abschluss für `DONE_MORE`)

**Verantwortlich:** pt9912

**Autor:** pt9912. **Datum:** 2026-11-15.

---

## 1. Ziel und Abgrenzung


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Der Heap des Serverprozesses beim Strom großer Ergebnisse (10 Mio Zeilen aus dem [Referenz-Workload](../../referenz-workload.md)) und die Verzögerung durch den zurückgehaltenen Abschluss in Millisekunden sind am Serverprozess gemessen und an die Zahlen des Spike in [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) gestellt.

**Übernimmt:** aus dem DoD-Punkt „Lasttest“ von [slice-qa-performance](../done/slice-qa-performance.md) (Fassung vor der Zerlegung vom 2026-11-15) die Punkte Heap und Latenz des Abschlusses.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Rückstau bei langsamen Lesern und blockierenden Backends — liegt bei [slice-qa-performance-rueckstau](../done/slice-qa-performance-rueckstau.md); hier hat der Leser keine Pause.
- Durchsatz je Stufe der Staffel — liegt bei [slice-qa-performance-durchsatz-sitzungen](../done/slice-qa-performance-durchsatz-sitzungen.md); hier zählt die Heap-Linie, nicht die Zeilen/s.
- Die Grenze des Testserver-Bestands am Heap (`BEO-TDS/testserver-bestand-nicht-am-heap-begrenzt`) — der Bestand speichert, der Strom nicht; die Messung des Bestands ist ein anderer Gegenstand und bleibt im Register offen.
- Eine kompaktere Zeilendarstellung oder ein blockweise liefernder Iterator (im Spike von [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) als „nicht gebaut“ benannt) — Optimierung, ausgeschlossen aus der Welle (§6).

## 2. Definition of Done


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [x] **Heap:** Live-Heap-Stichprobe im Serverprozess mit festem `-Xmx` beim Strom von 10 Mio Zeilen (eine Sitzung und mit der Staffel bis 16 Sitzungen), Aufbau B und C, mit Streuung; die Zeile sagt, dass es eine Stichprobe nach GC ist und keine Spitze (Aussage wie im Spike).
- [x] **Abschluss:** Verzögerung des zurückgehaltenen Abschlusses (`DONE_MORE`-Vorschau) in ms, gemessen mit einem Rohbytes-Client am Serverprozess als Abstand zwischen der letzten Zeile eines Ergebnisses und seinem DONE, wenn das Backend das nächste Ereignis um eine einstellbare Zeit verzögert (0, 10, 100, 1000 ms; Setzung); eine Messzeile je Wert oder „nicht lieferbar“ mit Begründung.
      *Haken mit Zusatz:* der Wortlaut „Abstand zwischen der letzten Zeile und seinem DONE“ ist 0 µs und ohne Information über D; geliefert ist „Zeile emittiert → DONE am Client“ (D plus 0,4 bis 1,6 ms im Median). Der Wortlaut ist durch die gemessene Größe ersetzt, die DoD-Frage liegt beim Nutzer (§7, Entscheidung 3); der Haken sagt nur, dass die Frage gestellt ist, nicht dass sie entschieden ist.
- [ ] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update: Messprotokoll unter `docs/messungen/`; der Satz „Die Vorschau kostet … einen zurückgehaltenen Abschluss, dessen Verzögerung nicht gemessen ist“ in [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) bleibt unverändert (die ADR ist `Accepted`, `AGENTS.md` §3.5); der Beleg steht im Protokoll, ein Nachzug in der Spezifikation nur, wo sie die Verzögerung nennt.
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

## 3. Plan (vor Code)


Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `testserver/kotlin/io/github/pt9912/tdswire/testserver/HeapAbschlussServer.kt` | neu | Stichprobe des Live-Heaps nach GC im Serverprozess; Backend mit einstellbarer Verzögerung des nächsten Ereignisses (Erzeuger im Repo, Aufbau B und C) |
| `testserver/kotlin/io/github/pt9912/tdswire/testserver/AbschlussClient.kt`, `RohbytesClient.kt` | neu | Rohbytes-Client der Abschluss-Messung (Pakete mit Wanduhr); `RohbytesClient` löst den Anmeldeteil von `LoginProbe.kt` ab |
| `testserver/test/kotlin/io/github/pt9912/tdswire/testserver/HeapAbschlussTest.kt`, `LoginProbe.kt` | neu, update | Tests der Bausteine mit rot gesehenen Mutationen; `LoginProbe` ruft den Rohbytes-Client |
| `referenztest-dotnet/Heap/HeapTests.cs`, `referenztest-dotnet/Dockerfile` | neu, update | Strom von 10 Mio Zeilen je Sitzung am Referenzclient, Stage `heap` |
| `tools/heap-abschluss.sh`, `tools/mk/heap-abschluss.mk` | neu | Zellen, Wächter, Fremdlast-Probe (`make heap-abschluss`) |
| `tools/heap-abschluss-tabellen.sh`, `tools/heap-abschluss-selbsttest.sh`, `tools/heap-abschluss-tabellen-selbsttest.sh`, `Dockerfile`, `tools/mk/skript-selbsttest.mk`, `harness/README.md` | neu, update | Tabellen aus den Rohzeilen, Selbsttests mit Mutationen in `make skript-selbsttest`, Zeilen der Sensors-Tabelle |
| `docs/messungen/performance-heap-abschluss-2026-11-17.md`, `docs/messungen/performance-heap-abschluss-2026-11-17-rohzeilen.txt` | neu | Messprotokoll, Einzelmessung (`AGENTS.md` §5 Regel 7), und seine Rohzeilen |
| `docs/plan/referenz-workload.md`, `spec/spezifikation.md`, `docs/user/einbettung-und-betrieb.md` | update | zweite Fundstellen: Zeilen der Metriken, die Verzögerung des zurückgehaltenen Abschlusses (nur wo die Spezifikation sie nennt), der Satz zum Rückstau in der Nutzer-Doku |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-qa-performance](../done/slice-qa-performance.md) in `done/`; nach [slice-qa-performance-durchsatz-sitzungen](../done/slice-qa-performance-durchsatz-sitzungen.md) ist die Streuung des Hosts bekannt (Setzung der Reihenfolge). Die Reihenfolge zu [slice-qa-performance-rueckstau](../done/slice-qa-performance-rueckstau.md) ist offen; beide dürfen nacheinander laufen, das WIP-Limit gilt.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert — Carveout?): Der Heap des Serverprozesses ist am Container nicht von dem des Messwerkzeugs zu trennen (beide im selben Prozess), weil der Aufbau nur eine JVM trägt; dann steht der Punkt „Heap“ als nicht lieferbar mit Begründung.

## 5. Closure-Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle Punkte der DoD in §2 erledigt, `make gates` grün, Messprotokoll liegt unter `docs/messungen/`, die Register-Einträge zum Heap und zum zurückgehaltenen Abschluss tragen den Ausgang im `Stand:`, die Closure-Notiz mit Steering-Loop-Lerneintrag ist geschrieben.

## 6. Risiken und offene Punkte


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Der Live-Heap ist eine Stichprobe alle n ms nach GC (im Spike alle 500 ms); eine Spitze zwischen zwei Stichproben bleibt unsichtbar. — **Ausgang:** weiter offen (die Spitze ist nicht gemessen; Träger Nutzer, siehe §7).
- Die Verzögerung des Abschlusses hängt am Verhalten des Backends; ein Backend, das das nächste Ereignis sofort liefert, zeigt keine. Die Zahl gilt für das Messbackend, nicht für ein Produktivbackend. — **Ausgang:** weiter offen (die Zahl gilt für das Messbackend; siehe §7).
- Mit 10 Mio Zeilen (Zeilengröße auf dem Draht ungemessen) kann eine Stufe auf diesem Host länger laufen als ein Tag-Fenster der Last trägt. — **Ausgang:** entfallen (längster Lauf rund 747 s, alle Läufe des Tages von 13:13 bis 15:31 UTC).

## 7. Closure-Notiz


Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register (vorhandene `BEO-<KUERZEL>/<slug>` **zitieren** statt neu
formulieren — sonst zählt das Register zwei Namen getrennt) ·
`grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln (das
Feld `liegt in` steht **nur**, wenn mit diesem Slice wirklich etwas verkörpert
wurde; Feld und Zielort auf **einer** Zeile, Sektionsangabe innerhalb der
Backticks). Ging der Gegenstand an einen anderen Slice oder entfiel er, trägt
diese Sektion die Zeile `Gegenstand:` mit Kennung oder Grund und jedes Risiko
aus §6 seinen Ausgang; die Liefer-Punkte der DoD bleiben leer
(`modul-05-planning-harness.md` §Ein Slice, dessen Gegenstand ein anderer
übernimmt).

Geschlossen 2026-11-17 (Planner, Messslice der Welle `welle-qualitaet-grundlage`, die Welle bleibt offen). Geprüfter Stand: Zweig `slice-qa-performance-heap-abschluss`; der Verifier prüfte die Spitze `d3d55a91`; danach `main` eingemergt (Merge `35c0e1df`, ohne Konflikt) und die Textkorrektur zu V-1 und V-2 (`b79b58ad`, nur Messprotokoll, Spezifikation, Nutzer-Doku). Die Zellen `heap-*` des Messlaufs liefen auf `e90c2399`, die Zelle `abschluss` auf `ce8ff0ca`. Prüfung: Review [2026-11-17-slice-qa-performance-heap-abschluss](../../../reviews/2026-11-17-slice-qa-performance-heap-abschluss.md) (F-1 bis F-13: vier MEDIUM, sechs LOW, drei INFO, kein HIGH), Re-Review [re-review](../../../reviews/2026-11-17-slice-qa-performance-heap-abschluss-re-review.md) (N-1 MEDIUM, N-2 und N-3 INFO), Verifikation [verifikation](../../../reviews/2026-11-17-slice-qa-performance-heap-abschluss-verifikation.md) (V-1 MEDIUM, V-2 LOW, V-3 und V-4 INFO, kein HIGH). Behoben: alle Findings des Reviews; aus dem Re-Review N-2 und N-3 (Text); aus der Verifikation V-1 und V-2 durch Einschränkung des Textes (nicht durch einen Test am Aufrufer, denn Code und Tests bleiben unverändert); nicht behoben, als Grenze geführt: N-1 und V-1 als Lücke des Tests (der Aufruf der Zeit des vorletzten Pakets in `messeAbschluss` hat keinen rot gesehenen Test), V-3 und V-4.

**Gegenstand.** Heap des Serverprozesses beim Strom von 10 Mio Zeilen je Sitzung (1, 8 und 16 Sitzungen, Aufbau B mit eigenem Backend und Aufbau C mit Simulations-Backend, `-Xmx256m`, drei Läufe je Zelle, 18 Läufe) und Verzögerung des zurückgehaltenen Abschlusses (`DONE_MORE`-Vorschau) bei einem Messbackend, das das nächste Ereignis um 0, 10, 100 und 1000 ms verzögert, mit einem Rohbytes-Client (zwei Formen, je fünf Läufe nach einem Warmlauf); Messprotokoll [performance-heap-abschluss-2026-11-17](../../../messungen/performance-heap-abschluss-2026-11-17.md), Rohzeilen [performance-heap-abschluss-2026-11-17-rohzeilen.txt](../../../messungen/performance-heap-abschluss-2026-11-17-rohzeilen.txt). Erzeuger im Repo: `testserver/kotlin/.../HeapAbschlussServer.kt`, `AbschlussClient.kt`, `RohbytesClient.kt`, `referenztest-dotnet/Heap/HeapTests.cs`, `tools/heap-abschluss.sh` (`make heap-abschluss`, kein Gate) und `tools/heap-abschluss-tabellen.sh` (`make heap-abschluss-tabellen`); `LoginProbe.kt` ruft den neuen `RohbytesClient`. Nicht im Repo liegen die Ausgabedateien der Läufe (die Rohzeilen sind ihr gefilterter Auszug) und die Ausgaben der Vorläufe. Kein Gegenstand ist an einen anderen Slice gegangen.

**Gemessen, gesetzt, festgelegt.** *Gemessen* (Einzelmessung eines Tages auf einem Host, kein Grenzwert; Zahlen nur so weit wie das Protokoll): Der Live-Heap nach einer Speicherbereinigung, Stichprobe alle 500 ms (keine Spitze), lag im Median der Stichproben bei 3,5 bis 4,1 MiB; der größte Wert einer Stichprobe lag in 17 von 18 Läufen zwischen 3,5 und 4,8 MiB, in einem Lauf (`heap-eigen-16`) bei 7,5 MiB, Ursache nicht untersucht; die cgroup-Spitze des Containers lag bei 88 bis 165 MiB. Vier Zellen tragen Proben über 300 % fremder CPU und gelten nur als Größenordnung. Der Abschluss kam im Median D plus 0,4 bis 1,6 ms nach der Übergabe der letzten Zeile am Client an (der größte Überhang aller 40 Läufe 2,0 ms); letzte Zeile und DONE kamen in 40 von 40 Läufen im selben Paket (die 40 Zeilen des letzten, nicht gefüllten Pakets warten mit dem Abschluss); 960 von 1000 Zeilen kamen vor dem Abschluss an (an ihrer Zahl belegt). *Plausibel, nicht belegt* (V-1): dass die Zeilen davor nicht um D verzögert waren; die Spalte `vorletztes_paket_bis_done_us` hat am Aufrufer keinen rot gesehenen Test, und ihre Aussage („höchstens 0,4 ms vor, 0,1 ms nach“) ist die Differenz zweier Mediane, keine gepaarte Differenz je Lauf (V-3). *Gesetzt:* die Werte D (0, 10, 100, 1000 ms), das Messbackend, `-Xmx256m`, 2 Prozessoren und die Limits der Container, Packet Size 4096, ein Rohbytes-Client mit `TCP_NODELAY`. *Festgelegt:* nichts in Produkt, Spezifikation oder ADR. Nicht gemessen und nicht entschieden: `TCP_NODELAY` am Server (setzt es nicht; Wirkung ungeprüft) und die Ursache des Zurückhaltens (aus dem Quelltext gelesen, nicht gemessen).

**Belege je DoD-Punkt (aus der Verifikation, nicht aus eigener Messung des Planners).** Heap: 18 Läufe mit je `gruen`, Streuung als Minimum, Median, 95. Rang und Maximum und im Vergleich der drei Läufe; die Verifikation rechnete die Zahlen des Protokolls unabhängig aus den Rohzeilen nach (12 Prüfpunkte, 37 Tabellenzeilen des Erzeugers stehen im Protokoll). Abschluss: 8 Zeilen (zwei Formen, vier Werte), je fünf Läufe und ein Warmlauf. `make gates`: der Verifier fuhr auf `d3d55a91` `make clean-build` (Exit 0, 563 s), `make e2e` (Exit 0, 9 Stufen, 371 s), `make gates FORCE=1` (Exit 0) und den frischen Selbsttest (`docker build --no-cache --target skript-selbsttest`, Exit 0: 15 Mutationen des Messwerkzeugs und 13 der Tabellen rot); **`make e2e` belegt für die neuen Zusagen nichts** (Server der Heap-Messung, `AbschlussClient`, Stage `heap` laufen in keiner Stufe, [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)), er belegt den Bestand (Stufe 2 trägt den Umbau von `LoginProbe` auf den `RohbytesClient`); die Zusagen tragen die Rohzeilen und die 10 Tests von `HeapAbschlussTest` in `make test`. Doku-Update: Messprotokoll mit Rohzeilen, `spec/spezifikation.md` (Verzögerung des Abschlusses, mit Bedingungen im Satz), `docs/user/einbettung-und-betrieb.md`, `docs/plan/referenz-workload.md`, `harness/README.md` (Zeilen `make heap-abschluss`, `make heap-abschluss-tabellen` und Selbsttests); [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) ist im Diff unverändert (`AGENTS.md` §3.5). Treffer der alten Lesart, die bleiben dürfen (Review F-12): `docs/plan/adr/0013-backend-grenzen.md` (`Accepted`), `docs/plan/e2e-abdeckung.md` und `tools/e2e.sh` Stufe 9 (gelten für die Probengröße der Stufe), der Abschnitt „Gemessen am Backend-Vertrag“ der Spezifikation (Stand des Spike), die Sätze zum Spike in ADR-0006.

**DoD-Punkt „Abschluss“: Wortlaut ersetzt, Frage beim Nutzer.** Der Wortlaut „Abstand zwischen der letzten Zeile eines Ergebnisses und seinem DONE“ ist in 40 von 40 Läufen 0 µs und trägt keine Information über D; das Protokoll liefert „Zeile emittiert → DONE am Client“ (D plus 0,4 bis 1,6 ms im Median). Der Haken in §2 steht mit diesem Zusatz: der Wortlaut ist durch die gemessene Größe ersetzt, ob die DoD so gilt, entscheidet der Nutzer (Frage 3 unten); der Haken sagt nur, dass die Frage gestellt ist, nicht dass sie entschieden ist.

**Nachbesserung ohne zweiten Review (Vermerk, nur dafür).** `d3d55a91` (Nachbesserung nach dem Re-Review) änderte laut Verifikation (`git diff f114d8bf..HEAD`) nur Text in vier Dateien (Messprotokoll, Nutzer-Doku, Spezifikation, Re-Review-Report), kein Kotlin, keinen Test, kein Skript; sie ist nicht in frischem Kontext re-reviewed. Ersatzprüfung: der Verifier fuhr danach `make clean-build`, `make e2e`, `make gates FORCE=1`, den frischen Selbsttest und `make docs-check`, alle Exit 0. Alle Änderungen mit Logik (`822d6a21`, `f114d8bf`) hatten einen Re-Review. Die Textkorrektur `b79b58ad` nach der Verifikation ist ebenfalls nur Text.

**Vier offene Entscheidungen des Nutzers (nicht entschieden; Spezifikation, DoD und Quelltext des Produkts sind dazu nicht geändert).** Folge-Slices sind nicht angelegt.

1. *Zeilen des letzten, nicht gefüllten Pakets.* Sie warten mit dem Abschluss auf das nächste Ereignis des Backends. Option *Code angleichen:* den Puffer vor dem Warten auf ein stockendes Backend senden. Option *Zielbild anpassen:* die Eigenschaft in Spezifikation und Nutzer-Doku festschreiben (beide führen sie bisher als gemessenen Ist-Zustand). Empfehlung des Planners: Code angleichen, weil ein Client die Zeilen sehen soll, sobald das Backend sie liefert; mit einer Nachmessung der Paketzahl.
2. *`TCP_NODELAY`.* `src/main` setzt es nicht; ein Vorlauf zeigte einmal rund 40 ms (ohne Rohzeile, Zusammenhang nicht geprüft). Option *Code angleichen:* setzen und nachmessen. Option *Zielbild anpassen:* als Setzung festhalten. Empfehlung: erst den Zusammenhang messen; Code angleichen, wenn er sich zeigt, sonst die Setzung.
3. *DoD-Wortlaut „Abstand“.* Option *Wortlaut ersetzen* durch „Zeile emittiert → DONE am Client“. Option *Wortlaut behalten* und die Lieferung als Abweichung führen. Empfehlung: ersetzen.
4. *Kosten der `DONE_MORE`-Vorschau.* Sie sind nicht von der Pufferung des letzten Pakets zu trennen (im Produkt gibt es keine Variante ohne Vorschau). Option *Nachmessen:* eine Variante ohne Vorschau im Testserver (nicht im Produkt) zum Vergleich messen. Option *Zielbild anpassen:* als nicht getrennt festhalten. Empfehlung: nach der Entscheidung zu 1 nachmessen; sendet der Server vor dem Warten, entfällt der größte Teil der Pufferung.

Mögliche Träger (Vorschlag an den Nutzer, nicht angelegt): ein Slice „Zeilen vor dem Warten auf ein stockendes Backend senden“ für 1 und 4 (Code in `src/main`, dann Re-Messung der Abschluss-Zellen mit `make heap-abschluss`), ein Slice für `TCP_NODELAY` am Server (2, mit Messung des Vorlaufs von 40 ms), und eine Anpassung der DoD-Zeile in diesem Slice (3, nach der Entscheidung).

**Ungemessen, Träger Nutzer, keine Folge-Slices angelegt** (aus der Verifikation, bestätigt):

- Eine Spitze des Heaps zwischen zwei Stichproben; der Heap ohne Stichprobe (Einfluss des Samplers: die GC-Zeit enthält die Bereinigungen der Stichprobe).
- Drei Läufe je Zelle auf einem Host an einem Tag, ein Lauf mit 7,5 MiB ohne Ursache, vier Zellen mit Fremdlast (Größenordnung); die Staffel bis 16, nicht 64 Sitzungen; mehr als 10 Mio Zeilen und andere Zeilenformen oder LOB.
- Uhrenpräzision und Gleichlauf der Wanduhren von Backend und Client (gleicher Kernel, Genauigkeit nicht gemessen); die Mediane der Abschluss-Spalten sind keine gepaarte Differenz (V-3).
- Ein anderer Client als `Microsoft.Data.SqlClient` 7.1.1 und der Rohbytes-Client; ein echter Treiber, ein Netz zwischen Hosts, TLS und TDS 8.0.
- Der Abschluss bei Aufbau C und bei einem Backend, das vor dem Ende des Ergebnisses wartet.
- Der Messlauf liegt nicht auf dem End-Stand (V-4): `heap-*` auf `e90c2399`, `abschluss` auf `ce8ff0ca`; spätere Commits berühren Server, Referenzclient und Abschlusslogik laut Diff nicht (nur Tests, Hilfen der Tabellen, `AbschlussClient.kt` und `tools/heap-abschluss.sh` verhaltensgleich laut Re-Review); der Zusammenhang stützt sich auf Review, nicht auf einen Lauf.
- Wirkung einer blockierenden Senke der Telemetrie und Beenden unter Last (`BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`, kein Träger).

**Risiken (Ausgänge in §6).** Stichprobe statt Spitze: weiter offen. Verzögerung hängt am Messbackend: weiter offen. Laufzeit von 10 Mio Zeilen: entfallen (längster Lauf rund 747 s, die Läufe des Tages von 13:13 bis 15:31 UTC).

**Was die Welle noch trägt.** `welle-qualitaet-grundlage` bleibt offen: [slice-qa-performance-grenzwerte](../open/slice-qa-performance-grenzwerte.md) (erst nach den Messungen und der Bestätigung des Nutzers), [slice-qa-passwortvergleich-gemischte-liste](../open/slice-qa-passwortvergleich-gemischte-liste.md) und die drei Slices der Kompatibilitätsmatrix ([slice-kompatibilitaetsmatrix-erzeugen](../open/slice-kompatibilitaetsmatrix-erzeugen.md), [slice-kompatibilitaetsmatrix-migration](../open/slice-kompatibilitaetsmatrix-migration.md), [slice-kompatibilitaetsmatrix-gate](../open/slice-kompatibilitaetsmatrix-gate.md)); die Liste führt die [Welle-Datei](../welle-qualitaet-grundlage.md).

**Register.** Fortgeschrieben (je eine Datei `evidence/slice-qa-performance-heap-abschluss.md`, kein Zähler gesetzt) an neun bestehenden Einträgen: der Träger `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` (Heap und Abschluss gemessen, Rest offen; die vier Entscheidungen mit Optionen in der Evidence-Datei), und die wiederkehrenden Klassen `BEO-TDS/aussagen-ueber-messung-hinaus` (Review F-4, F-5, F-8, F-10, Verifikation V-2, V-3), `BEO-TDS/zusage-ohne-ihre-bedingung-im-selben-satz` (F-4; 2 Dateien, unter der Schwelle), `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test` (F-1, F-2, N-1, V-1), `BEO-TDS/skript-pruefung-ohne-rot-gegenprobe` (F-3; mit diesem Auftreten 4 Dateien), `BEO-TDS/zahl-an-mehreren-orten-ohne-sensor` (F-6), `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` (F-10 (b), (c), F-12), `BEO-TDS/kommentar-nennt-abwesenden-oder-unwirklichen-zustand` (F-9) und `BEO-TDS/dod-wortlaut-ueber-der-bauart` (der Wortlaut „Abstand“, vom Verifier gefangen). `BEO-TDS/plan-tabelle-nennt-neue-datei-nicht` bekommt keine Datei: der Plan-Tabelle fehlte laut Review (Prüfpunkt 6) und Re-Review keine Datei des Diffs. Kein neues Verzeichnis: keine Beobachtung ohne bestehenden Eintrag.

**Lese-Schritt (Register, Einträge mit 3 oder mehr Dateien in `evidence/`).** Gelesen wurden die fortgeschriebenen Einträge mit 3 oder mehr Dateien: `aussagen-ueber-messung-hinaus` (99), `zeile-oder-zweig-ohne-unterscheidenden-test` (30), `skript-pruefung-ohne-rot-gegenprobe` (4), `zahl-an-mehreren-orten-ohne-sensor` (11), `fortschreibung-laesst-zweite-fundstelle-stehen` (54), `kommentar-nennt-abwesenden-oder-unwirklichen-zustand` (14), `dod-wortlaut-ueber-der-bauart` (11) und `rueckstau-im-produktivbetrieb-nicht-gemessen` (13). Sechs Klassen haben den Ausgang `verkörpert` und sind in diesem Slice wiedergekehrt, jedes Mal von Reviewer, Re-Review oder Verifier gefangen, nicht vom Implementer (Retirement-Check: ja, die Regeln bleiben, keine Lockerung). `skript-pruefung-ohne-rot-gegenprobe` bleibt `offen`, jetzt 4×, über der Schwelle: Verkörperung entscheidet der Nutzer, diese Closure ändert weder Regel noch Gate. `rueckstau-im-produktivbetrieb-nicht-gemessen` bleibt `geplant` mit dem Rest der Träger. Der Lese-Schritt der Welle bleibt der Welle-Closure vorbehalten; diese Closure greift ihm nicht vor.

**Lerneintrag (Steering Loop).**

- **Geschärft, keine neue Regel:** (i) Ein Lieferpunkt der DoD kann formal erfüllt und ohne Aussage sein: der „Abstand zwischen der letzten Zeile und ihrem DONE“ ist durch die Paketierung des Servers 0 µs. Die Planung prüfte nicht, ob die Größe von der Bauart überhaupt einen anderen Wert zulässt; erst der Verifier fand es (`BEO-TDS/dod-wortlaut-ueber-der-bauart`, in `plan-welle.md` Schritt 6b verkörpert). Gelernt: bei einer Messgröße nach einem Probelauf am Rohwert prüfen, ob sie über die Zellen variiert. (ii) Ein Messwerkzeug trägt nur, was seine Tests am Aufrufer rot sehen: die Zeit des vorletzten Pakets hatte einen Test am Helfer, nicht am Aufruf; der Satz darauf musste auf „plausibel, nicht belegt“ zurück (`BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test`). (iii) Ein Satz über „ohne Verzögerung“ braucht die Zeitspalte, nicht die Zahl der Zeilen (`BEO-TDS/aussagen-ueber-messung-hinaus`). Ob eine Regel zu schärfen ist, entscheidet der Nutzer; diese Closure ändert keinen Command und keinen Reviewer-Skill.
- **Neuer Sensor im Repo, ohne Regel-Eintrag und deshalb ohne Herkunfts-Anker:** `tools/heap-abschluss-tabellen.sh` (Selbsttest `tools/heap-abschluss-tabellen-selbsttest.sh`, 13 Mutationen, `make heap-abschluss-tabellen`) setzt die Tabellen des Protokolls aus den Rohzeilen; `tools/heap-abschluss-selbsttest.sh` (15 Mutationen: Wächter, Zellennamen, Grenzen, Fremdlast-Summe) prüft das Messwerkzeug; beide laufen in `make skript-selbsttest` (Klasse `werkzeug`). Sie sind kein Gate für den Messlauf selbst ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)).
- **Benannte Spec-Lücke:** die Eigenschaft „die Zeilen des letzten, nicht gefüllten Pakets warten mit dem Abschluss“ steht in `spec/spezifikation.md` und in der Nutzer-Doku als gemessener Ist-Zustand, nicht als Zusage und nicht als Zielbild; ob sie gewollt ist, steht in keiner Spec-Stelle (Entscheidung 1). Dasselbe gilt für `TCP_NODELAY` (Entscheidung 2).
- **Nicht gemessen, benannt:** siehe „Ungemessen“ oben.

**Drei Paarungen.** Anker: keine Regel mit `liegt in` verkörpert, daher nichts zu paaren. Folge-Slice: die Slices der Welle in `open/` tragen die Grenzwerte, den Passwortvergleich und die Kompatibilitätsmatrix; **für die vier Entscheidungen und die ungemessenen Punkte oben existiert kein Slice** (nicht angelegt, Träger Nutzer, ausdrücklich so geführt; Vorschläge oben), die Paarung ist dafür durch die Benennung hier, in §6, im Register und im Protokoll („Offen, Entscheidung beim Nutzer“, „Nicht gemessen“) getragen, nicht durch eine Datei. Register: neun `evidence/slice-qa-performance-heap-abschluss.md` unter `../observations/BEO-TDS/`, jedes Verzeichnis mit nicht leerem `evidence/`, Link auf diese Datei in `done/`.

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung — dort die **zwei vorgelagerten
Schritte** (sie stehen in jedem Slice-Plan, unabhängig von Modus und
Slice-Typ) und die **vier Pflichtkriterien** (Konventionen-Dichte ·
Phase-Reife · Evidenz-/Diskrepanz-Risiko · Reconciliation-Aufwand), vier und
nicht mehr.

**Der Abschnitt selbst entfällt nie.** Die zwei vorgelagerten Prüfungen laufen
in **jedem** Slice-Plan — sie hängen weder am Modus noch am Slice-Typ. Bedingt
ist allein der Modus-Begründungsblock am Ende; deshalb nennt der Titel beide
Hälften.

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` gilt für das ganze Repo (`*`, Greenfield); der Slice berührt `*`, eine feinere Sub-Area ist nicht deklariert.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen: `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` (11 Dateien; Heap und zurückgehaltener Abschluss sind dort genannt) und `BEO-TDS/testserver-bestand-nicht-am-heap-begrenzt` (1 Datei, ausgeschlossen, siehe §1); den Zähler bildet die Zahl der Dateien unter `evidence/`, hier nicht gesetzt.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
