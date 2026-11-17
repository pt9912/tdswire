# Slice slice-qa-performance-rueckstau: Performance: Rückstau am Socket, bei blockierendem Backend und beim Simulations-Backend

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-qualitaet-grundlage

**Bezug:** [TDS-QA-04](../../../../spec/lastenheft.md#tds-qa-04--performance), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)

**Berührte Spec-Stellen:** `SPEC-022` · `SPEC-035` · `SPEC-009` · `SPEC-033` (Rückstau-Zusage in `spec/spezifikation.md`, Abschnitt „Pflichten des Backends“: „gilt für das Test-Backend an einem Draht ohne Netz, nicht für einen Socket, ein blockierendes Backend oder das Simulations-Backend“)

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

**Ziel:** Der Rückstau ist im Serverprozess über einen Socket mit mehreren Verbindungen, mit einem blockierenden Backend und mit dem Simulations-Backend gemessen; jeder der drei Punkte trägt eine Messzeile oder ist mit Begründung als nicht lieferbar benannt (`BEO-TDS/rueckstau-zusage-nur-test-backend-traeger-unvollstaendig`, `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`).

**Übernimmt:** aus dem DoD-Punkt „Lasttest“ von [slice-qa-performance](../done/slice-qa-performance.md) (Fassung vor der Zerlegung vom 2026-11-15) die Punkte Socket, blockierendes Backend und Simulations-Backend; die Träger-Zuweisung der beiden Beobachtungen bleibt bei diesem Gegenstand.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Heap beim Strom und Latenz des zurückgehaltenen Abschlusses — liegen bei [slice-qa-performance-heap-abschluss](../in-progress/slice-qa-performance-heap-abschluss.md).
- Durchsatz und p95 der Staffel — liegen bei [slice-qa-performance-durchsatz-sitzungen](../done/slice-qa-performance-durchsatz-sitzungen.md); hier zählt, wie weit das Backend dem Leser vorausläuft und was ein langsamer Leser den übrigen Sitzungen nimmt.
- Wirkung einer blockierenden Senke der Telemetrie auf den Anfragepfad — ein anderer Gegenstand (Senke, nicht Backend); `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` führt sie weiter, sie bleibt dort offen, bis ein Slice sie übernimmt.
- Ein Schreibvorgang, der am Rückstau die Sitzungssperre hält und damit Frist und Abbruch aufschiebt (`SPEC-009`, `SPEC-033`): er wird beobachtet, wo er in den Läufen auftritt, die Behebung ist kein Teil — eine Behebung wäre „Code angleichen“ oder „Zielbild anpassen“ und ein eigener Slice.

## 2. Definition of Done


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [x] **Socket, mehrere Verbindungen:** am Serverprozess (Aufbau B und C aus dem [Referenz-Workload](../../referenz-workload.md)) mit der Stufe 256 der Staffel: Zeilen, die das Backend dem langsamsten Leser vorausläuft, und Verhalten der übrigen Sitzungen; die Zahl ist neben die Einzelmessung (eine Maschine, Loopback, 32 MiB, 500 ms) gestellt, und das Protokoll sagt, was am Docker-Netz auf demselben Host anders ist als Loopback (nicht anders genug für „Netz“).
- [x] **Blockierendes Backend:** ein Backend, dessen Lesen oder Schreiben wartet (unterbrechbar und nicht unterbrechbar), unter Last: Threads, Dauer, Abbruch nach `CANCEL_TIMEOUT`, Wirkung auf andere Sitzungen — eine Messzeile oder „nicht lieferbar“ mit Begründung.
- [x] **Simulations-Backend:** Rückstau der Zeilenquelle und des Massenladens bei langsamem Leser beziehungsweise Sender — eine Messzeile oder „nicht lieferbar“ mit Begründung; die Aussage in `spec/spezifikation.md`, Abschnitt „Pflichten des Backends“ („gilt nicht für“) ist auf den Beleg zurückgeschnitten oder erweitert.
- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update: `spec/spezifikation.md` (Rückstau-Aussage), Messprotokoll unter `docs/messungen/`, `docs/user/` nur bei geändertem Verhalten.
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
| Szenarien für langsamer Leser, blockierendes Backend und Stufe 256 ([slice-qa-performance](../done/slice-qa-performance.md) liefert Aufbauten und Zählform) | neu (eigenes Werkzeug `tools/rueckstau.sh`, kein Umbau von `tools/lasttest.sh`) | Messung nach [TDS-QA-04](../../../../spec/lastenheft.md#tds-qa-04--performance) und [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) |
| Messende Backends (blockierend, zählend) im Testserver-Modul | neu | Aufbau mit Zähler, Erzeuger im Repo |
| `docs/messungen/performance-rueckstau-<datum>.md` | neu | Messprotokoll, Einzelmessung (`AGENTS.md` §5 Regel 7) |
| `testserver/kotlin/.../RueckstauServer.kt` und `testserver/test/kotlin/.../RueckstauTest.kt` | neu | Serverprozess mit zählendem, wartendem Backend (eigen und Simulation) und seine Tests |
| `referenztest-dotnet/Rueckstau/RueckstauTests.cs`, `referenztest-dotnet/Dockerfile` (Stage `rueckstau`) | neu, update | Szenarien Leser, Blockierend, Abbruch, Import am Referenzclient |
| `tools/rueckstau.sh`, `tools/mk/rueckstau.mk`, `tools/rueckstau-tabellen.sh`, beide Selbsttests, `tools/mk/skript-selbsttest.mk`, `Dockerfile` (Stage `skript-selbsttest`) | neu, update | Lauf der Zellen mit Fremdlast-Probe, Tabellen aus den Rohzeilen, Selbsttests |
| `harness/README.md`, `docs/user/einbettung-und-betrieb.md`, `docs/plan/referenz-workload.md`, Rohzeilen unter `docs/messungen/` | update, neu | Zeilen der Targets, Rückstau-Aussage der Nutzer-Doku, Zeiger auf das Protokoll, Rohzeilen des Protokolls |
| `spec/spezifikation.md`, Abschnitt „Pflichten des Backends“ | update | Aussage trägt höchstens so weit wie der Beleg (`AGENTS.md` §3.8) |


## 4. Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-qa-performance](../done/slice-qa-performance.md) in `done/` (Werkzeug und Aufbauten). Die Reihenfolge nach [slice-qa-performance-durchsatz-sitzungen](../done/slice-qa-performance-durchsatz-sitzungen.md) ist eine Setzung, damit die Streuung des Hosts zuerst an der einfacheren Messung bekannt ist; formal genügt der Start nach [slice-qa-performance](../done/slice-qa-performance.md).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert — Carveout?): Ein Aufbau (blockierendes Backend, Simulations-Backend am Serverprozess) ist nur mit einer Änderung der öffentlichen Fläche herstellbar (`SPEC-059`); der Punkt steht dann als „nicht lieferbar“ mit Begründung in der Messzeile, und die Fläche ist ein eigener Vorgang.

## 5. Closure-Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle Punkte der DoD in §2 erledigt, `make gates` grün, Messprotokoll liegt unter `docs/messungen/`, die Register-Einträge `BEO-TDS/rueckstau-zusage-nur-test-backend-traeger-unvollstaendig` und `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` tragen den Ausgang dieses Slice im `Stand:`, und die Closure-Notiz mit Steering-Loop-Lerneintrag ist geschrieben.

## 6. Risiken und offene Punkte


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Der Rückstau am Socket hängt von den Puffern der Umgebung ab (`BEO-TDS/annahme-ueber-puffer-der-umgebung`); die Zahl gilt für diesen Host und dieses Docker-Netz, nicht für ein Netz zwischen Hosts. — **Ausgang:** weiter offen, Träger Nutzer: gemessen an einem Docker-Netz auf einem Host (MTU 1500, `tcp_rmem`/`tcp_wmem` in den Rohzeilen); ein Netz zwischen Hosts ist nicht gemessen, die Zahl 614 bis 1499 Zeilen gilt für diesen Host (§7).
- Ein blockierendes Backend aus Treiberaufrufen verhält sich anders als der Wartezustand des Messaufbaus (der Spike nahm Interrupts als Annahme, [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)). — **Ausgang:** weiter offen, Träger Nutzer: der Pool mit 64 Threads ist eine Setzung des Messaufbaus, kein Adapter des Servers und kein echter Treiber; die Thread-Spitze 77 bis 78 belegt die Obergrenze (§7).
- Die Messung kann eine Zusage der Spezifikation widerlegen; dann entsteht ein Nachzug (Code angleichen oder Zielbild anpassen), nicht in diesem Slice. — **Ausgang:** entfallen: keine Zusage ist widerlegt (614 bis 1499 Zeilen sind „nicht beliebig“); die Aussage „gilt nicht für“ in `spec/spezifikation.md` ist auf den Beleg zurückgeschnitten, ein Nachzug entsteht nicht.

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

Geschlossen 2026-11-17 (Planner, Messslice der Welle `welle-qualitaet-grundlage`, die Welle bleibt offen). Geprüfter Stand: Zweig `slice-qa-performance-rueckstau`, Spitze vor der Closure `372509b8`. `main` ist in diesen Zweig eingemergt (Merge `cc08b028`, im Re-Review gelesen; die Spitze von `main` zum Zeitpunkt der Closure, `b5866069`, ist Vorfahr des Zweigs, ein weiterer Merge war nicht nötig). Der Messlauf steht auf Commit `4eecf536` (Zelle `leser-frist` auf `769de2cc`); `RueckstauServer.kt` ist seit `4eecf536` und `RueckstauTests.cs` seit `769de2cc` unverändert (Diff geprüft, Verifikation Prüfpunkt 11). Prüfung: Review [2026-11-17-slice-qa-performance-rueckstau](../../../reviews/2026-11-17-slice-qa-performance-rueckstau.md) (F-1 bis F-12: sieben MEDIUM, drei LOW, zwei INFO, kein HIGH), Re-Review [re-review](../../../reviews/2026-11-17-slice-qa-performance-rueckstau-re-review.md) (N-1 bis N-6: ein MEDIUM, drei LOW, zwei INFO), Re-Review 2 [re-review-2](../../../reviews/2026-11-17-slice-qa-performance-rueckstau-re-review-2.md) (M-1 MEDIUM, M-2 INFO), Verifikation [verifikation](../../../reviews/2026-11-17-slice-qa-performance-rueckstau-verifikation.md) (F-1 bis F-4: ein MEDIUM, drei LOW). Behoben nach dem Stand der Berichte: alle MEDIUM und LOW des Reviews, des Re-Reviews und des Re-Reviews 2 (M-1, M-2 in `adcfa665`) sowie F-1 bis F-4 der Verifikation (Messprotokoll und Spezifikation in `372509b8`); nicht umgesetzt: die INFO N-5 und N-6 des Re-Reviews (Wanduhr-Kontrollzweig, zwei benannte Grenzen des Tabellen-Werkzeugs stehen in dessen Kopf).

**Gegenstand.** Messung des Rückstaus im Serverprozess: ein langsamer Leser neben schnellen Sitzungen in den Aufbauten B (eigenes Backend) und C (Simulations-Backend) mit 1, 8, 64 und 256 Verbindungen (dazu 256 mit 128 langsamen Lesern), ein blockierendes Backend (suspendierend, unterbrechbar, nicht unterbrechbar, direkt) mit bis zu 256 Sitzungen, der Abbruch nach `CANCEL_TIMEOUT`, das Massenladen mit `SqlBulkCopy` gegen das Simulations-Backend und die Frist bei angehaltenem Leser; Messprotokoll [performance-rueckstau-2026-11-17](../../../messungen/performance-rueckstau-2026-11-17.md), Rohzeilen [performance-rueckstau-2026-11-17-rohzeilen.txt](../../../messungen/performance-rueckstau-2026-11-17-rohzeilen.txt). Erzeuger im Repo: der Serverprozess `testserver/kotlin/.../RueckstauServer.kt`, der Client `referenztest-dotnet/Rueckstau/RueckstauTests.cs`, das Werkzeug `tools/rueckstau.sh` (`make rueckstau`, kein Gate) **mit der Fremdlast-Probe** (sie liegt in `tools/rueckstau.sh`, anders als im Vorgänger-Slice) und `tools/rueckstau-tabellen.sh` (`make rueckstau-tabellen`), das die Tabellen des Protokolls aus den Rohzeilen setzt; nicht im Repo liegen nur die Ausgabedateien der Läufe (die Rohzeilen sind ihr gefilterter Auszug) und die Ausgaben der Vorläufe. Kein Gegenstand ist an einen anderen Slice gegangen.

**Gemessen, gesetzt, festgelegt.** *Gemessen* (Einzelmessung eines Tages, ein Durchlauf je Zelle, Größenordnung, kein Grenzwert; Zahlen nur so weit wie das Protokoll): Das Backend läuft einem Leser, der nach einer Zeile nicht mehr liest, 614 bis 1499 Zeilen voraus (rund 0,2 bis 0,5 MB bei der Umrechnung mit 329 Byte je Zeile aus der Probe des Referenz-Workloads, in diesem Lauf nicht gemessen) und bleibt dort stehen (Zuwachs 0 in allen neun Leser-Zellen); die übrigen Sitzungen lasen in allen Zellen bis zum Ende, ihre Zeilen/s lagen mit langsamem Leser nicht unter der Basis (die Basis kam zuerst, der Unterschied enthält den Anlauf der JVM und ist kein Maß für einen Einfluss). Bei 8 Sitzungen liegt der Vorlauf höher (1499 und 1032) als bei 64 bis 256 (614 bis 663); beide Zellen mit 8 Sitzungen tragen eine Probe über 300 % fremder CPU, eine unbelastete Zelle gibt es nicht, die Ursache ist nicht untersucht. Blockierendes Backend: Die Threads sind durch den Pool begrenzt (Spitze 77 bis 78 bei 64 bis 256 Sitzungen, die Obergrenze, kein Beleg für 64 gleichzeitig schlafende Threads); die Dauer bei 256 Sitzungen ist 4,1 s gegen 4,0 s Erwartung, die Sitzungen warteten auf den Pool; der Nachbar sah mit Last Antwortzeiten, die nicht über denen ohne Last liegen (Median 1,0 bis 1,5 ms gegen 1,7 bis 2,0 ms); ein Backend, das im Thread des Aufrufers schläft (`direkt`), hielt bei 8 Sitzungen und zwei Dispatcher-Threads (JVM mit 2 Prozessoren) die Anfrage einer anderen Sitzung an (Median 1,8 s, n = 3). Abbruch: ein unterbrechbarer Aufruf endet in 9 ms, ein nicht unterbrechbarer hält ihn bis `CANCEL_TIMEOUT` auf (2018 ms bei 2 s), der Server schließt dann die Verbindung. Massenladen: der Sender läuft dem Callback höchstens um einen Stapel je Sitzung voraus (1001, 8008, 7008, 996); der Heap blieb nach der Bereinigung bei 4 bis 11 MB. Frist: nach dem Weiterlesen kamen 1500 Zeilen, dann `Timeout.`. *Gesetzt* (Setzungen des Messaufbaus, nicht gemessen): der Pool mit 64 Threads, das Warten von 10 ms je Zeile, `QUERY_TIMEOUT` 3600 s und `CANCEL_TIMEOUT` 2 s in den Zellen, die Limits der Container; „nicht anders genug für Netz“ für das Docker-Netz (die MTU 1500 gegen 65 536 und die Kernel-Puffer stehen in den Rohzeilen, der Rest ist Annahme); die Vermutungen des Protokolls (Puffer des Betriebssystems unter Last, Muster des Clients beim Massenladen, Belegung des Pool-Threads beim Abbruch, Luft zwischen den Stapeln) sind dort als nicht geprüft gekennzeichnet. *Festgelegt* (Entscheidung, nicht Messung): die Aussage der Spezifikation „Pflichten des Backends“ ist von „gilt nicht für Socket, blockierendes Backend, Simulations-Backend“ auf „am Socket gemessen (Einzelmessung vom 2026-11-17, mit Bedingungen und Nicht gemessen)“ zurückgeschnitten, kein Gate gesenkt; keine Zusage der Spezifikation ist widerlegt (614 bis 1499 Zeilen sind „nicht beliebig“).

**Belege je DoD-Punkt (aus der Verifikation, nicht aus eigener Messung des Planners).** Socket, mehrere Verbindungen: neun Leser-Zellen, davon `leser-eigen-256`, `leser-eigen-256halb` (128 langsame Leser, Mittelwert 657) und `leser-sim-256`; Nachrechnung gegen die Rohzeilen (Verifikation „Nachrechnung der Protokollzahlen“, 11 Prüfpunkte, 42 Tabellenzeilen aus `tools/rueckstau-tabellen.sh`, 37 Zeile für Zeile im Protokoll, die fünf übrigen sind die Bedingungstabelle von Hand, ihre Werte stimmen). Blockierendes Backend: sieben Blockier-Zellen und zwei Abbruch-Zellen. Simulations-Backend: vier Leser-Zellen `leser-sim-*` und sechs Import-Zellen, dazu die Rückschnitt-Zeile in `spec/spezifikation.md` und die gleiche Aussage in `docs/user/einbettung-und-betrieb.md`. `make gates`: siehe unten. Review: Berichte oben. Doku-Update: `spec/spezifikation.md`, Messprotokoll mit Rohzeilen, `docs/user/einbettung-und-betrieb.md`, `docs/plan/referenz-workload.md` (Zeile „Rückstau“ mit Zeiger auf das Protokoll), `harness/README.md` (Zeilen `make rueckstau`, `make rueckstau-tabellen` und die des Selbsttests). Der Verifier fuhr auf `adcfa665` `make clean-build` (Exit 0, 530 s; die sieben Tests von `RueckstauTest` `PASSED`), `make e2e` (Exit 0, 9 Stufen, 418 s) und `make gates FORCE=1` (Exit 0); **`make e2e` belegt weder `RueckstauServer` noch den Client `RueckstauTests.cs` noch `tools/rueckstau*.sh`** (keine Stufe ruft `make rueckstau`); der Beleg für den Rückstau-Aufbau ist das Messprotokoll (ein Lauf) plus `RueckstauTest` und die Selbsttests der Werkzeuge ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md): `make rueckstau` ist kein Gate). `make gates` und `make docs-check` auf dem Stand der Closure: Exit 0 (`make gates FORCE=1`, alle Klassen, 30 s; `make docs-check` 0 Befunde) auf dem Stand nach dem Move der Closure; die Closure hat keinen Quelltext, kein Skript und keinen Workflow geändert.

**Nachbesserung ohne zweiten Review (Vermerk, nur dafür).** `adcfa665` (Kommentar-Korrektur M-1, M-2 nach dem Re-Review 2: Kopplung von Muster und Liste, belegter Stand im Kopf des Selbsttests) änderte laut Verifikation (`git diff 770cfb79 adcfa665`) nur Kommentarzeilen; sie ist nicht in frischem Kontext re-reviewed. Ersatzprüfung: der Verifier fuhr danach `make clean-build`, `make e2e`, `make gates FORCE=1` und den frischen Selbsttest (`docker build --no-cache --target skript-selbsttest`: elf Mutationen des Skripts und sieben der Tabellen je rot), alle Exit 0. Die Nachbesserungen `372509b8` und `8d89a69d` ändern nur Protokoll, Spezifikation und Bericht. Alle Änderungen mit Logik (`RueckstauTest.kt`, `tools/rueckstau.sh`, die Selbsttests, `tools/rueckstau-tabellen.sh`) hatten einen Re-Review.

**Ausdrücklich offen, nicht Teil des Slice; Träger Nutzer, keine Folge-Slices angelegt.**

- Nachbar-Latenz beim Abbruch nicht aufgeteilt: Zellen `abbruch-*-ohnelast` sind im Werkzeug vorgesehen, aber im Lauf `4eecf536` nicht gefahren; ob der 95. Rang von 17 und 39 ms am Abbruch oder an den acht lesenden Sitzungen hängt, ist offen. **Offen, Träger Nutzer.**
- Ursache der Streuung des Vorlaufs (1499 gegen 614): nicht untersucht, kein Wiederholungslauf (je Zelle ein Durchlauf), beide Zellen mit 8 Sitzungen unter Fremdlast. **Offen, Träger Nutzer.**
- CPU des Clients (2 Prozessoren, bis 256 Threads) je Zelle: nur die Summe beider Container fließt in die Fremdlast-Probe; ob der Client in den Zellen mit 256 Sitzungen der Engpass war, ist offen. **Offen, Träger Nutzer.**
- Andere Netze, Treiber, TLS: Docker-Netz auf einem Host, `Microsoft.Data.SqlClient` 7.1.1, TDS 7.4 ohne Verschlüsselung; kein Netz zwischen Hosts, kein JDBC-Treiber, kein TLS oder TDS 8.0. **Offen, Träger Nutzer.**
- Thread-Spitze nur als Poolgrenze belegt (77 bis 78), nicht als 64 gleichzeitig schlafende Threads; der Pool ist ein Aufbau der Messung, kein Adapter des Servers, ein echter Treiber ist nicht gemessen. **Offen, Träger Nutzer.**
- `direkt` nur mit 2 Prozessoren (zwei Dispatcher-Threads); mit mehr Prozessoren nicht gemessen. **Offen, Träger Nutzer.**
- Kein rot gesehene Mutation für die Tests `die Angaben der Anfrage werden gelesen` und `die Tabellen des Aufbaus C zaehlen Quelle und Import` und für die Zusagen des .NET-Clients: sie laufen nur über die Prozessgrenze mit `make rueckstau`, das kein Gate ist ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)); `make e2e` deckt `RueckstauServer` nicht ab. **Offen, Träger Nutzer.**
- Der Hauptlauf ist eine Einzelmessung (ein Lauf auf `4eecf536`, `leser-frist` auf `769de2cc`), keine Streuung, unter Fremdlast (Last 1 min 1,9 bis 4,3, 6,6 bei `leser-frist`; drei Zellen mit einer Probe über 300 %, nur Größenordnung); „0, 0, 0 %“ in der Spalte Fremdlast heißt keine Probe, nicht keine Fremdlast; ein ruhiger Host ist nicht gemessen. **Offen, Träger Nutzer.**
- Ein Schreibvorgang, der am Rückstau die Sitzungssperre hält und Frist und Abbruch aufschiebt (`SPEC-009`, `SPEC-033`): in den Abbruch-Zellen nicht aufgetreten und nicht gesucht; die Behebung wäre ein eigener Slice. Blockierendes Lesen der Zeilenquelle des Simulations-Backends, Abbruch beim Massenladen, die Wirkung einer blockierenden Senke der Telemetrie (`BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`, kein Träger) und der Vorlauf des Simulations-Backends mit mehreren langsamen Lesern: nicht gemessen. **Offen, Träger Nutzer** (Heap beim Strom und Abschluss: [slice-qa-performance-heap-abschluss](../in-progress/slice-qa-performance-heap-abschluss.md)).

**Vorschlag an den Nutzer (nicht angelegt, nicht entschieden).** Ein Nachmess-Slice für die Zellen `abbruch-*-ohnelast` und die Streuung des Vorlaufs (Wiederholungen je Zelle, Zellen mit 8 Sitzungen auf einem ruhigen Host), falls gewünscht; er hätte den Start-Trigger „Host frei“ wie die Messslices der Welle. Bis dahin tragen die Punkte oben den Träger Nutzer.

**Risiken (Ausgänge in §6).** Puffer der Umgebung: weiter offen (die Zahl gilt für diesen Host und dieses Docker-Netz). Blockierender Treiber gegen Messaufbau: weiter offen (der Pool ist eine Setzung). Widerlegung einer Zusage: entfallen (keine Zusage widerlegt, die „gilt nicht für“-Aussage ist eingeschränkt).

**Was die Welle noch trägt.** `welle-qualitaet-grundlage` bleibt offen: [slice-qa-performance-heap-abschluss](../in-progress/slice-qa-performance-heap-abschluss.md), [slice-qa-performance-grenzwerte](../open/slice-qa-performance-grenzwerte.md) (erst nach den Messungen und der Bestätigung des Nutzers), [slice-qa-passwortvergleich-gemischte-liste](../open/slice-qa-passwortvergleich-gemischte-liste.md) und die drei Slices der Kompatibilitätsmatrix ([slice-kompatibilitaetsmatrix-erzeugen](../open/slice-kompatibilitaetsmatrix-erzeugen.md), [slice-kompatibilitaetsmatrix-migration](../open/slice-kompatibilitaetsmatrix-migration.md), [slice-kompatibilitaetsmatrix-gate](../open/slice-kompatibilitaetsmatrix-gate.md)); die Liste führt die [Welle-Datei](../welle-qualitaet-grundlage.md).

**Register.** Fortgeschrieben (je eine Datei `evidence/slice-qa-performance-rueckstau.md` an neun bestehenden Einträgen, kein Zähler gesetzt): die beiden Träger `BEO-TDS/rueckstau-zusage-nur-test-backend-traeger-unvollstaendig` und `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` (Ausgang im `Stand:`); die wiederkehrenden Klassen `BEO-TDS/aussagen-ueber-messung-hinaus` (Review F-3, F-4, F-8, F-9, Verifikation F-2, F-3), `BEO-TDS/spike-prototyp-nicht-im-repo-messung-nicht-wiederholbar` (Review F-2), `BEO-TDS/plan-tabelle-nennt-neue-datei-nicht` (Review F-7, Re-Review N-2), `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` (Review F-10, Verifikation F-1: die Zahl der Mutationen im Protokoll blieb nach den Nachbesserungen bei „sechs“, gefahren sind elf), `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test` (Review F-1, F-5; die Unterart „Test, der nie lief“ wird dort mitgezählt, nicht als eigene Klasse geführt) und `BEO-TDS/skript-pruefung-ohne-rot-gegenprobe` (Review F-6, Re-Review N-1; mit diesem Auftreten 3 Dateien) und `BEO-TDS/zahl-an-mehreren-orten-ohne-sensor` (Re-Review N-3, Re-Review 2 M-1: die Zellennamen stehen an drei Orten, der Kommentar behauptete einen Beleg der Kopplung, den der Selbsttest nicht hatte). Neues Verzeichnis, ein Auftreten, unter der Schwelle: `BEO-TDS/messaufbau-gleichzeitigkeit-nicht-belegt` (Vorläufe 3 des Protokolls: der Thread-Pool des Clients startete 256 Sitzungen bis zu 38 s gestaffelt, die Zahlen waren ein Artefakt; erst `start_max_ms` in den Blockier-Zeilen belegt die Gleichzeitigkeit, in den Leser-, Abbruch- und Import-Zeilen fehlt er). Gesichtet, kein neuer Eintrag: `BEO-TDS/annahme-ueber-puffer-der-umgebung` (derselbe Host mit denselben `tcp_wmem`/`tcp_rmem`-Obergrenzen, der Trigger „anderer Host“ nicht eingetreten; Risiko 1 trägt ihn), `BEO-TDS/nachbesserung-ohne-zweiten-review` (der Vermerk oben ist die Regel, kein neues Auftreten: die Regel ist eingehalten, nicht verletzt).

**Lese-Schritt (Register, Einträge mit 3 oder mehr Dateien in `evidence/`).** Gelesen wurden die fortgeschriebenen Einträge mit 3 oder mehr Dateien: `aussagen-ueber-messung-hinaus`, `spike-prototyp-nicht-im-repo-messung-nicht-wiederholbar`, `plan-tabelle-nennt-neue-datei-nicht`, `fortschreibung-laesst-zweite-fundstelle-stehen`, `zeile-oder-zweig-ohne-unterscheidenden-test`, `zahl-an-mehreren-orten-ohne-sensor`, `rueckstau-im-produktivbetrieb-nicht-gemessen` und `skript-pruefung-ohne-rot-gegenprobe` (mit diesem Auftreten 3 Dateien). Sechs davon haben den Ausgang `verkörpert`, jede Klasse ist in diesem Slice wiedergekehrt und jedes Mal vom Reviewer, Re-Review oder Verifier gefangen, nicht vom Implementer (Retirement-Check: ja, die Regeln bleiben, keine Lockerung). `skript-pruefung-ohne-rot-gegenprobe` erreicht mit diesem Slice die Schwelle (3×) und bleibt `offen`: Verkörperung (Regel im Reviewer-Skill oder in `implement-slice.md`, Sensor oder Streichung) entscheidet der Nutzer, diese Closure ändert weder Regel noch Gate. `rueckstau-im-produktivbetrieb-nicht-gemessen` (12 Dateien) bleibt `geplant` mit dem Rest der Träger. Der Lese-Schritt der Welle bleibt der Welle-Closure vorbehalten; diese Closure greift ihm nicht vor.

**Lerneintrag (Steering Loop).**

- **Geschärft, keine neue Regel:** (i) Ein Messprotokoll, dessen Tabellen „aus den Zeilen übernommen“ sind, hat keinen Erzeuger im Repo, obwohl der DoD-Wortlaut ihn verlangt: Der Review fand es als F-2 (zwei Fehler der Handarbeit: `weiterlesen_ms` 12 statt 9, Speicher und Swap in MiB durch 1000 statt 1024 geteilt), erst danach kam `tools/rueckstau-tabellen.sh`. Der Slice-Plan nannte „Erzeuger im Repo“ und baute ihn erst nach dem Review; die Verkörperung in `plan-welle.md` Schritt 6d und `implement-slice.md` Schritt 20a liest „Erzeuger“ im Singular und sagt nichts zur Kette (Rohzahl, Tabellen, Probe) — dieselbe Schärfung wie in `slice-qa-performance-durchsatz-sitzungen` (`BEO-TDS/spike-prototyp-nicht-im-repo-messung-nicht-wiederholbar`); ob der Wortlaut zu schärfen ist, entscheidet der Nutzer, diese Closure ändert keinen Command. (ii) Ein Test kann grün sein und nie gelaufen sein: `die Tabelle der Zaehler liefert eine Zeile` gab einen Wert aus `runBlocking` zurück, JUnit meldete „must not return a value“ und führte 6 statt 7 Tests aus; der Fund (F-1) war die Mutation, die grün blieb, nicht das Lesen des Tests. Gelernt: Die Zahl der ausgeführten Tests (hier 7) gehört in den Beleg, und eine Mutation je Zusage ist der Sensor, der es fängt (`BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test`). (iii) Ein Selbsttest, der eine eigene Liste fährt, belegt nicht die Kopplung zum Muster, das er schützt: der Kommentar „der Selbsttest belegt, dass jeder Name des Musters einen Zweig hat“ war falsch (M-1, Mutation: Name im Muster ohne Zweig blieb grün); behoben als Kommentar, die Kopplung selbst ist als Grenze im Kopf benannt (`BEO-TDS/zahl-an-mehreren-orten-ohne-sensor`); der Wächter des Werkzeugs hatte zuerst gar keinen Selbsttest (F-6, N-1: `BEO-TDS/skript-pruefung-ohne-rot-gegenprobe`). (iv) Eine Messlesart ist breiter als ihre Rohzeilen, wenn ihr Teil an einer belasteten Zelle hängt („bei 8 Sitzungen höher“) oder eine Erklärung als Feststellung steht (Verifikation F-2, F-3); die Nachrechnung gegen die Rohzeilen war wieder der Fund (`BEO-TDS/aussagen-ueber-messung-hinaus`).
- **Neuer Sensor im Repo, ohne Regel-Eintrag und deshalb ohne Herkunfts-Anker:** `tools/rueckstau-tabellen.sh` (Selbsttest `tools/rueckstau-tabellen-selbsttest.sh` mit sieben Mutationen, `make rueckstau-tabellen`) setzt die Tabellen des Protokolls aus den Rohzeilen; `tools/rueckstau-selbsttest.sh` (elf Mutationen: Wächter für Speicher, Zellennamen, Fremdlast-Summe; Teil von `make skript-selbsttest`, Klasse `werkzeug`) prüft das Messwerkzeug. Beide sind kein Gate für den Messlauf selbst ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)).
- **Benannte Spec-Lücke:** keine neue; die Aussage in „Pflichten des Backends“ ist auf den Beleg zurückgeschnitten, die Lücken stehen in „Nicht gemessen“ der Spezifikation und des Protokolls. Die Zusage trägt weiterhin keinen Grenzwert (`slice-qa-performance-grenzwerte`).
- **Nicht gemessen, benannt:** siehe „Ausdrücklich offen“ oben.

**Drei Paarungen.** Anker: keine Regel mit `liegt in` verkörpert, daher nichts zu paaren. Folge-Slice: die Slices der Welle in `open/` tragen Heap und Abschluss und die Grenzwerte; **für die offenen Punkte oben (Abbruch ohne Last, Streuung, Client-CPU, andere Netze, Mutationen über die Prozessgrenze) existiert kein Slice** (nicht angelegt, Träger Nutzer, ausdrücklich so geführt; Vorschlag oben), die Paarung ist dafür durch die Benennung hier, in §6 und im Protokoll („Nicht gemessen“) getragen, nicht durch eine Datei. Register: neun `evidence/slice-qa-performance-rueckstau.md` und ein neues Verzeichnis unter `../observations/BEO-TDS/`, Link auf diese Datei in `done/`.


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

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen: `BEO-TDS/rueckstau-zusage-nur-test-backend-traeger-unvollstaendig` (1 Datei, Träger dieser Slice), `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` (11 Dateien: die Schwelle von 3× ist überschritten, die Lücke trägt diese Zerlegung), `BEO-TDS/annahme-ueber-puffer-der-umgebung` (2 Dateien, Risiko oben); den Zähler bildet die Zahl der Dateien unter `evidence/`, hier nicht gesetzt.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
