# Slice slice-qa-performance-durchsatz-sitzungen: Performance: Durchsatz, p95-Latenz und gleichzeitige Sitzungen am Referenz-Workload

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-qualitaet-grundlage

**Bezug:** [TDS-QA-04](../../../../spec/lastenheft.md#tds-qa-04--performance), [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)

**Berührte Spec-Stellen:** `SPEC-017` · `SPEC-018` · `SPEC-019` · `SPEC-034` (Telemetrie als Quelle der Dauer je Schicht, `MAX_CONNECTIONS` als obere Grenze der Staffel)

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

**Ziel:** Der freigegebene [Referenz-Workload](../../referenz-workload.md) läuft mit dem Werkzeug aus [slice-qa-performance](../done/slice-qa-performance.md) in den Aufbauten B (Protokollschicht, Export) und C (Gesamt, Export und Import): Zeilen/s je Richtung, p95 der Punktabfrage und die Staffel gleichzeitiger Sitzungen sind als Messzeilen mit Streuung und Bedingungen festgehalten.

**Übernimmt:** aus dem DoD-Punkt „Lasttest“ von [slice-qa-performance](../done/slice-qa-performance.md) (Fassung vor der Zerlegung vom 2026-11-15) den Teil Durchsatz, p95-Latenz und gleichzeitige Sitzungen; der Rest liegt bei [slice-qa-performance-rueckstau](../in-progress/slice-qa-performance-rueckstau.md), [slice-qa-performance-heap-abschluss](../open/slice-qa-performance-heap-abschluss.md) und [slice-qa-performance-grenzwerte](../open/slice-qa-performance-grenzwerte.md).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Rückstau am Socket, blockierendes Backend und Simulations-Backend unter Last — liegen bei [slice-qa-performance-rueckstau](../in-progress/slice-qa-performance-rueckstau.md); die Staffel hier misst Durchsatz, keinen Rückstau.
- Heap beim Strom großer Ergebnisse und Latenz des zurückgehaltenen Abschlusses — liegen bei [slice-qa-performance-heap-abschluss](../open/slice-qa-performance-heap-abschluss.md) (anderer Aufbau, andere Zeilenzahl).
- Grenzwerte im Lastenheft — liegen bei [slice-qa-performance-grenzwerte](../open/slice-qa-performance-grenzwerte.md); dieser Slice liefert Messzeilen, keine Zahl, die schon Grenzwert ist.
- Import in ein zählendes eigenes Backend (Aufbau B) — setzt die öffentliche Fläche für das Massenladen eigener Backends voraus und liegt bei dem Slice zur Fläche nach [ADR-0052](../../adr/0052-oeffentliche-flaeche-fuer-massenladen-eigener-backends.md) oder einem Nachmess-Slice; hier gelten für den Import nur Aufbau C und die Staffel.
- Optimierung bei einer verfehlten oder schlechten Zahl — ein Slice je Befund nach der Messung (Welle §6).

## 2. Definition of Done


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [x] **Export:** Zeilen/s, erste Zeile und Dauer je Schicht (`tds.query`, `backend.execute`) für `SELECT * FROM ref_zeilen` in den Aufbauten B und C, je Stufe der Staffel, mit Median, Minimum und Maximum der Wiederholungen und den Bedingungen (Datum, Host, Last, Limits).
- [x] **Import:** Zeilen/s für `SqlBulkCopy` (`BatchSize` 1000) in das Simulations-Backend mit `onInsertBatch` (Aufbau C), je Stufe der Staffel, mit Streuung und Bedingungen. Das Ziel ist ein Zähler; der Bestand des Testservers mit seiner Grenze von 2 097 152 Zellen ist **nicht** das Ziel.
- **Ausdrücklich offen, nicht Teil dieses Slice (Nutzerentscheidung 2026-11-17 „DoD teilen, B später“):** der Import in ein zählendes eigenes Backend (Aufbau B). Er wird Liefer-Punkt des Slice zur Fläche nach [ADR-0052](../../adr/0052-oeffentliche-flaeche-fuer-massenladen-eigener-backends.md) oder eines Nachmess-Slice; bis dahin ist der Aufbau B im Import nicht gemessen.
- [x] **Sitzungen und p95:** p50, p95 und p99 der Punktabfrage (aus der Messung des Clients) während die übrigen Sitzungen streamen oder laden, für die Staffel 1, 8, 16, 64; jede Stufe mit und ohne Senke für Telemetrie; Messprotokoll unter `docs/messungen/` nach `AGENTS.md` §5 Regel 7, jede Zahl mit Erzeuger im Repo.
- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update: `docs/plan/referenz-workload.md` (Abschnitte, die eine Messung berührt) und `harness/README.md` §Sensors, falls ein Target dazukommt; Nutzer-Doku unter `docs/user/` nur, wenn sich Verhalten, Konfiguration oder Ereignisse ändern (nicht zu erwarten, der Slice misst).
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
| Läufe des Werkzeugs aus [slice-qa-performance](../done/slice-qa-performance.md) (Stufen, Staffel, Wiederholungen) | update | Messung nach [TDS-QA-04](../../../../spec/lastenheft.md#tds-qa-04--performance), keine neue Mechanik |
| `docs/messungen/performance-durchsatz-<datum>.md` | neu | Messprotokoll, Einzelmessung mit Datum, Host, Last (`AGENTS.md` §5 Regel 7) |
| `docs/plan/referenz-workload.md` | update | Beschreibung der Phasen und der Stufe N (Muster 3, S4) und Verweis auf das Messprotokoll im Kopf; „Messzeilen und Zeilengröße nachtragen“ entfällt (Entscheid des Planners bei der Closure, Verifikation V-3): das Dokument führt Setzungen, eine Kopie der Messzahlen driftete gegen das Protokoll, und die Zeilengröße in Abschnitt 3.1 widerspricht dem Protokoll nicht |
| `tools/lasttest.sh`, `tools/lasttest-selbsttest.sh` | update | das Werkzeug wächst um Sitzungen, Phasen (`export`, `import`, `punkt-export`, `punkt-import`), Punktabfrage, Dauer je Schicht aus dem Log und `LASTTEST_QUERY_TIMEOUT`; der Selbsttest hält die Auswertung (Handläufe, Mutationen) |
| `referenztest-dotnet/Lasttest/LasttestTests.cs`, `referenztest-dotnet/Lasttest/ReferenzZeilen.cs` | update | Client mit N Sitzungen und der Punktabfrage; die Prüfung, dass die Punktabfrage genau die Zeile der Id liefert, läuft in keinem Gate, nur in `make lasttest` und Stufe 9 von `make e2e` ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)) |
| `testserver/kotlin/io/github/pt9912/tdswire/testserver/LasttestBackends.kt`, `testserver/test/kotlin/io/github/pt9912/tdswire/testserver/LasttestTest.kt` | update | Punktabfrage `WHERE Id = @id` in Aufbau B und C, Tests |
| `tools/lasttest-tabellen.sh`, `tools/lasttest-tabellen-selbsttest.sh`, `tools/mk/lasttest.mk`, `Dockerfile` | neu bzw. update | Werkzeug, das die Tabellen des Messprotokolls aus den Rohzeilen setzt (`make lasttest-tabellen`, kein Gate), Selbsttest in der Stage `skript-selbsttest` |
| `harness/README.md` | update | Zeilen `make lasttest` (Parameter der Staffel) und `make lasttest-tabellen` |
| `docs/plan/adr/` ([ADR-0052](../../adr/0052-oeffentliche-flaeche-fuer-massenladen-eigener-backends.md) und Index) | nicht dieser Slice | vom Architect auf demselben Zweig angelegt (Nutzerentscheidung zum Import in Aufbau B) |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-qa-performance](../done/slice-qa-performance.md) in `done/` (Werkzeug, Aufbauten A bis C, freigegebener Workload).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert — Carveout?): Das Werkzeug aus [slice-qa-performance](../done/slice-qa-performance.md) liefert für eine Stufe keine wiederholbare Zahl (die Streuung überschreitet die Schwelle der Setzung S11 auf diesem Host, auch nach Verwerfen der Läufe unter Swap); dann steht die Stufe als „nicht belegt“ mit der Streuung im Protokoll, und ein Carveout oder ein Folge-Slice trägt die Umgebung (fester Runner, offene Frage O6 im Workload).

## 5. Closure-Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle Punkte der DoD in §2 erledigt, `make gates` grün, das Messprotokoll liegt unter `docs/messungen/` und die Closure-Notiz mit Steering-Loop-Lerneintrag ist geschrieben.

## 6. Risiken und offene Punkte


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Die Importzahl gegen ein zählendes Backend trägt nicht für ein Backend, das Zeilen speichert; der Bestand des Testservers ist mit 2 097 152 Zellen begrenzt (`BEO-TDS/testserver-bestand-nicht-am-heap-begrenzt`). — **Ausgang:** weiter offen, Träger Nutzer: der Import in Aufbau B ist aus diesem Slice herausgenommen (Nutzerentscheidung 2026-11-17); der Slice zur Fläche nach [ADR-0052](../../adr/0052-oeffentliche-flaeche-fuer-massenladen-eigener-backends.md) ist nicht angelegt, über ihn oder einen Nachmess-Slice entscheidet der Nutzer; [ADR-0052](../../adr/0052-oeffentliche-flaeche-fuer-massenladen-eigener-backends.md) liegt bisher nur in diesem Zweig und kommt mit dessen Merge nach `main`; das Register führt die Beobachtung weiter.
- Der Host läuft unter Last 5 bis 9 und swappt; die Streuung kann jede Stufe unbrauchbar machen, ohne dass das Werkzeug fehlerhaft ist. — **Ausgang:** eingetreten: Last 1 min 2,69 bis 15,38, Swap des Hosts 12 018 bis 16 383 MiB belegt, 18 von 48 Tabellenzeilen „gestört“, zwei Zellen `zu-gross` (nach S11 nicht belegt); die Zahlen gelten als Größenordnung. Die Rückführung nach §4 wurde nicht ausgelöst (die übrigen Zellen tragen). Rest weiter offen: ein ruhiger Host, die Streuung über Tage und Hosts; Träger der Benennung [slice-qa-performance-grenzwerte](../open/slice-qa-performance-grenzwerte.md), eine Nachmessung hat keinen Slice (Träger Nutzer).
- Die Kosten der Telemetrie selbst sind ungemessen (Abschnitt 5 der Spezifikation); die Messung „mit und ohne Senke“ trennt sie nur für die Stufen, die sie fährt. — **Ausgang:** teils gemessen: 12 Paare mit und ohne Senke, 7 davon außerhalb der Spanne der Gegenseite, Vorzeichen wechselt (Stufe 1 gleich); ein Einfluss ist weder belegt noch ausgeschlossen. Weiter offen: die Kosten einer blockierenden Senke; Träger `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` (bis ein Slice sie übernimmt).

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

Geschlossen 2026-11-17 (Planner, Messslice der Welle `welle-qualitaet-grundlage`, die Welle bleibt offen). Geprüfter Stand: Zweig `slice-qa-performance-durchsatz-sitzungen`, Basis `c118e7e6`; das Messwerkzeug ist seit `d5a751eb` unverändert (Messläufe 2026-11-17, 05:29 bis 07:12 UTC); die Verifikation lief auf `78a9e5d3`; danach `fb0173fc` (Werkzeug `tools/lasttest-tabellen.sh`) und `d88e04a9` (Protokolltext, Plan-Tabelle, Kommentare); `main` ist in diesen Zweig eingemergt (`7d8942df`). **Der Merge:** Konflikt nur im ADR-Index; [ADR-0051](../../adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) (mehrere RPCs, aus `main`) und [ADR-0052](../../adr/0052-oeffentliche-flaeche-fuer-massenladen-eigener-backends.md) (Massenladen, aus diesem Zweig) koexistieren, beide Zeilen stehen im Index. **[ADR-0052](../../adr/0052-oeffentliche-flaeche-fuer-massenladen-eigener-backends.md) liegt bisher nur in diesem Zweig und kommt erst mit dem Merge dieses Zweigs nach `main`.** Die Roadmap-Zeile 13 wurde von Hand angepasst; `docs/plan/spec-abdeckung.md` ist unverändert gegenüber `main` (der Slice hat keine `@spec`-Tags geändert, geprüft am Diff). Prüfung: Review [2026-11-17-slice-qa-performance-durchsatz-sitzungen](../../../reviews/2026-11-17-slice-qa-performance-durchsatz-sitzungen.md) (F-1 bis F-14: neun MEDIUM, drei LOW, zwei INFO, kein HIGH), Re-Review [re-review](../../../reviews/2026-11-17-slice-qa-performance-durchsatz-sitzungen-re-review.md) (R-1 bis R-9: drei MEDIUM, fünf LOW, ein INFO), Re-Review 2 [re-review-2](../../../reviews/2026-11-17-slice-qa-performance-durchsatz-sitzungen-re-review-2.md) (R-1 bis R-7 zum Werkzeug `tools/lasttest-tabellen.sh`: zwei MEDIUM, drei LOW, zwei INFO), Verifikation [verifikation](../../../reviews/2026-11-17-slice-qa-performance-durchsatz-sitzungen-verifikation.md) (V-1 bis V-6: ein MEDIUM, drei LOW, zwei INFO). Behoben nach dem Stand der Berichte: alle MEDIUM; die Verifikation prüfte die nachgebesserten Befunde stichprobenartig, R-9 des Re-Reviews (Nennung der zwei Nutzerentscheidungen im Protokoll) und die INFO R-6, R-7 des Re-Reviews 2 sind nicht umgesetzt: R-6 und R-7 stehen als Grenzen im Kopf von `tools/lasttest-tabellen.sh`.

**Gegenstand.** Messung des Referenz-Workloads in den Aufbauten B und C (Export) und C (Import) für die Staffel 1, 8, 16, 64 gleichzeitige Sitzungen, mit und ohne Senke, und der Punktabfrage unter Last (p50, p95, p99), als Einzelmessung unter Fremdlast; Messprotokoll [performance-durchsatz-2026-11-17](../../../messungen/performance-durchsatz-2026-11-17.md), Rohzeilen [performance-durchsatz-2026-11-17-rohzeilen.txt](../../../messungen/performance-durchsatz-2026-11-17-rohzeilen.txt). Das Werkzeug aus [slice-qa-performance](slice-qa-performance.md) wuchs um Sitzungen, die Phasen `punkt-export` und `punkt-import`, die Dauer je Schicht, `LASTTEST_QUERY_TIMEOUT` und den Aggregator `tools/lasttest-tabellen.sh`. Kein Gegenstand ist an einen anderen Slice gegangen; der Import in Aufbau B ist aus der DoD herausgenommen (siehe unten).

**Gemessen, gesetzt, festgelegt.** *Gemessen* (Einzelmessung, Größenordnung, nicht Beleg für einen Grenzwert; Zahlen nur so weit wie das Protokoll): Export ab 8 Sitzungen ein Plateau der Gesamtzeilen/s bei 198 000 bis 229 000, Import (Aufbau C) 436 000 bis 542 000 bei 8 bis 64 Sitzungen, bei einer Sitzung 241 000 bis 271 000; die Grenze des Plateaus kann Server oder Client sein (CPU beider ungemessen). Die Punktabfrage hat ohne Last p50 0,2 bis 0,3 ms, unter Export-Last bei 64 Sitzungen p50 12,6 bis 13 ms und p95 26 bis 29 ms, unter Import-Last bei 64 Sitzungen p95 rund 95 ms; der p99 unter Export-Last liegt bei 23 bis 33 ms, unter Import-Last steigt er mit der Stufe. Die Senke: bei 7 von 12 Paaren liegt der Median einer Seite außerhalb der Spanne der anderen, Vorzeichen wechselt, bei Stufe 1 gleich; ein Einfluss ist weder belegt noch ausgeschlossen. Die Standardfrist von 30 s ließ den ersten Export der Stufe 8 nach 30 002 bis 30 044 ms enden; **dass der Fehlercode `TDS_TIMEOUT` war, ist aus der Dauer geschlossen, nicht belegt**; die Stufen 8, 16, 64 liefen mit 3600 s. *Gesetzt* (Setzungen des Workloads, nicht neu gemessen): S4 (Staffel), S11 (Streuung: zwei Zellen `zu-gross`, nach S11 nicht belegt), die Limits des Servers und des Clients. *Festgelegt* (Entscheidungen, nicht Messung): die Wahl des Versuchs je Zelle durch `tools/lasttest-tabellen.sh` (erster Versuch mit höchstens einer Probe über 300 %), die Kennzeichnung „gestört“ ab zwei Proben, „Median der Perzentile der 5 Läufe“ und der Rang (aufgerundet) der Perzentile.

**Entscheidungen des Nutzers, 2026-11-17, im Slice als solche benannt.** (1) „Code angleichen“: [ADR-0052](../../adr/0052-oeffentliche-flaeche-fuer-massenladen-eigener-backends.md) (Option N1, angenommen). (2) „DoD teilen, B später“: der Import in Aufbau B verlässt diesen Slice (DoD, §1, §6, Protokoll). (3) „Aggregator ins Repo“ (zu V-1): `tools/lasttest-tabellen.sh` mit Selbsttest, `make lasttest-tabellen`, kein Gate.

**Entscheidungen des Planners bei der Closure.** V-1 ist durch das Werkzeug behoben (Re-Review 2: die Ausgabe gleicht den Tabellen des Protokolls ohne Abweichung, mawk und gawk byte-gleich); die Fremdlast-Probe bleibt außerhalb des Repos und ist als nicht reproduzierbar benannt, **der Wortlaut „jede Zahl mit Erzeuger im Repo“ gilt für die Rohzahlen und die Tabellen, nicht für die Spalte „Fremdlast“**. V-3: der Plan-Wortlaut in §3 für `docs/plan/referenz-workload.md` ist geändert (kein „Messzeilen nachtragen“): das Dokument ist eine Sammlung von Setzungen, eine Kopie der Messzahlen dort driftete gegen das Protokoll, und die Zeilengröße auf dem Draht (328 bis 330 und 337 Byte, Abschnitt 3.1) widerspricht den 328 bis 329 und 336 bis 337 Byte des Protokolls nicht; stattdessen nennt der Kopf des Dokuments das neue Protokoll. V-4 ist eine **benannte Grenze, nicht erledigt**: die neuen Pfade des Werkzeugs (Gruppe mit N Sitzungen, Punktabfrage mit Prüfung jeder Zeile gegen den Erzeuger, Phasen `punkt-*`) laufen in keinem Gate und in keiner Stufe von `make e2e` (Stufe 9 fährt `export` und `import` mit einer Sitzung), nur durch die Messläufe vom 2026-11-17 belegt, konform zu [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md); das Protokoll nennt es unter „Nicht belegt“. V-5 ist im Protokoll gesetzt (Dauer je Schicht nur mit Senke), V-2 berichtigt („bis etwa 7 %“).

**Belege je DoD-Punkt.** Export, Import (Aufbau C), Sitzungen und p95: Verifikation „Nachrechnung der Protokollzahlen“ (32 Zeilen `zeilen_je_s`, 48 Tabellenzeilen, 11 Zählungen und Randzahlen gegen die Rohzeilen; eine Abweichung V-2, berichtigt), Re-Review (432 Einzelprüfungen und 66 Zeilen „Dauer je Schicht“, keine Abweichung) und Re-Review 2 (Neuerzeugung der Tabellen, kein Unterschied). `make gates`: Exit 0 mit `FORCE=1` auf dem Stand nach dem Merge von `main` (`7d8942df`, alle Klassen frisch), die Verifikation fuhr dasselbe auf `78a9e5d3` (Exit 0, 31 s) sowie `make clean-build` (Exit 0, 609 s) und `make e2e` (Exit 0, 9 Stufen, 388 s) und den Selbsttest frisch. Review, Re-Review, Re-Review 2: Berichte oben. Doku-Update: `harness/README.md` (Zeilen `make lasttest` und `make lasttest-tabellen`), `docs/plan/referenz-workload.md` (Phasen, Stufe N, Verweis auf das Protokoll).

**Nachbesserung ohne zweiten Review (Vermerk).** `d88e04a9` (nach Re-Review 2) änderte in `tools/` nur Kommentare (`tools/lasttest-tabellen.sh`) und Hilfetext und Kopf (`tools/mk/skript-selbsttest.mk`), keine Logik; ein weiterer Review in frischem Kontext lief nicht. Alle Skript- und Client-Änderungen mit Logik (`tools/lasttest-selbsttest.sh`, `tools/lasttest-tabellen.sh`) hatten einen Re-Review.

**Ausdrücklich offen, nicht Teil des Slice.** Import in Aufbau B (zählendes eigenes Backend): **offen, Träger Nutzer.** Der Slice zur öffentlichen Fläche nach [ADR-0052](../../adr/0052-oeffentliche-flaeche-fuer-massenladen-eigener-backends.md) ist **nicht angelegt**; ob und wann er angelegt wird und ob der Import in B dort oder in einem Nachmess-Slice gemessen wird, entscheidet der Nutzer. Ebenso offen: Aufbau A in der Staffel (nur die Probe vom 2026-11-16 mit einer Sitzung; kein Gegenstand dieser DoD).

**Ungemessen (aus der Verifikation, im Protokoll unter „Nicht belegt“).** Import in B; Aufbau A in der Staffel; CPU von Client und Server (ob das Plateau am Server, am Client mit 2 CPUs für bis zu 64 Sitzungen oder an beiden liegt, folgt aus den Zeilen nicht); Einfluss der Senke und Kosten einer blockierenden Senke; die zwei Zellen `zu-gross` (Stufe 16 C mit Senke `punkt-export`, Stufe 1 C mit Senke `punkt-import`); Versuch 1 der Zelle Stufe 8 B mit Senke `punkt-export` (p95 5,45 gegen 2,80 ms, Faktor 2 zwischen Läufen derselben Zelle); die Ursache des p99-Bandes unter Export-Last; der Fehlercode `TDS_TIMEOUT` (aus der Dauer geschlossen); die Fremdlast-Probe (außerhalb des Repos, nicht reproduzierbar: 18 von 48 Tabellenzeilen tragen „gestört“, nur Größenordnung); ein ruhiger Host, die Streuung über Tage und Hosts, TLS, ein anderer Client, d-migrate als Gegenprobe, Grenzwerte. **Träger:** die Benennung der Lücken „nicht belegt“ trägt [slice-qa-performance-grenzwerte](../open/slice-qa-performance-grenzwerte.md) (§6 dort: eine Messreihe fehlt oder ist „nicht belegt“, das Lastenheft trägt dann die Benennung der Lücke, keinen Wert); ein Slice, der auf einem ruhigen Host nachmisst, existiert nicht (Träger Nutzer). Kosten der Senke: [BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen](../observations/BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen/state.md) führt sie weiter.

**Risiken (Ausgänge in §6).** Importzahl gegen ein zählendes Backend: weiter offen, Träger Nutzer. Host, Swap, Streuung: eingetreten, Rest weiter offen mit Träger. Kosten der Telemetrie: teils gemessen, Rest weiter offen mit Träger.

**Was die Welle noch trägt.** `welle-qualitaet-grundlage` bleibt offen: [slice-qa-performance-rueckstau](slice-qa-performance-rueckstau.md) läuft (`in-progress/`, anderer Zweig, nicht Teil dieser Closure), [slice-qa-performance-heap-abschluss](../open/slice-qa-performance-heap-abschluss.md) und [slice-qa-performance-grenzwerte](../open/slice-qa-performance-grenzwerte.md) (erst nach den Messungen und der Bestätigung des Nutzers), [slice-qa-passwortvergleich-gemischte-liste](../open/slice-qa-passwortvergleich-gemischte-liste.md) und die Slices der Kompatibilitätsmatrix ([slice-kompatibilitaetsmatrix-erzeugen](../open/slice-kompatibilitaetsmatrix-erzeugen.md), [slice-kompatibilitaetsmatrix-migration](../open/slice-kompatibilitaetsmatrix-migration.md), [slice-kompatibilitaetsmatrix-gate](../open/slice-kompatibilitaetsmatrix-gate.md)); die Liste führt die [Welle-Datei](../welle-qualitaet-grundlage.md).

**Register.** Fortgeschrieben (je eine Datei `evidence/slice-qa-performance-durchsatz-sitzungen.md`, kein Zähler gesetzt): `BEO-TDS/spike-prototyp-nicht-im-repo-messung-nicht-wiederholbar` (F-1, V-1; jetzt 5 Dateien), `BEO-TDS/aussagen-ueber-messung-hinaus` (F-3 bis F-5, F-10, R-4, R-5, R-7, V-2; 95), `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test` (F-8; 27), `BEO-TDS/plan-tabelle-nennt-neue-datei-nicht` (F-9, R-2; 27), `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` (R-3, R-5; 50), `BEO-TDS/dod-haengt-an-offenem-slice-ohne-rueckfuehrung` (Review Schwerpunkt 3; 4). Nur der Stand fortgeschrieben, keine neue Datei: `BEO-TDS/testserver-bestand-nicht-am-heap-begrenzt` (Risiko 1, Träger Nutzer), `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` (Teil „gleichzeitige Sitzungen“ gemessen). Gesichtet, kein neuer Eintrag: `BEO-TDS/skript-pruefung-ohne-rot-gegenprobe` und `BEO-TDS/kuerzung-einer-tabellenzelle-verliert-angaben` (kein Auftreten in den vier Berichten); die Messklasse „Erzeuger außerhalb des Repos trotz DoD-Wortlaut“ steht in `spike-prototyp-nicht-im-repo-messung-nicht-wiederholbar` und wird dort mitgezählt, nicht als eigene Klasse geführt (Nutzerentscheid 2026-11-06).

**Lese-Schritt (Register, Einträge mit 3 oder mehr Dateien in `evidence/`).** Gelesen wurden die sechs fortgeschriebenen Einträge (alle sechs hatten vor dieser Closure 3 oder mehr Dateien); fünf haben den Ausgang `verkörpert`, jeder ist in diesem Slice wiedergekehrt und jedes Mal vom Reviewer, Re-Review oder Verifier gefangen, nicht vom Implementer (Retirement-Check: ja, die Regeln bleiben, keine Lockerung). `dod-haengt-an-offenem-slice-ohne-rueckfuehrung` hat 4 Dateien und bleibt `offen` (Entscheidung beim Nutzer). Der Lese-Schritt der Welle (alle Einträge mit 3 oder mehr Dateien) bleibt der Welle-Closure vorbehalten; diese Closure greift ihm nicht vor.

**Lerneintrag (Steering Loop).**

- **Neuer Sensor im Repo, ohne Regel-Eintrag und deshalb ohne Herkunfts-Anker:** `tools/lasttest-tabellen.sh` (Selbsttest `tools/lasttest-tabellen-selbsttest.sh` in der Stage `skript-selbsttest`, Klasse `werkzeug`, sechs Mutationen rot, `make lasttest-tabellen`, kein Gate) setzt die Tabellen eines Messprotokolls aus den Rohzeilen. Auslöser: Der Reviewer (F-1) und der Verifier (V-1) rechneten die 48 Tabellenzeilen je mit einem eigenen Wegwerf-Skript nach, weil der Erzeuger der Tabellen außerhalb des Repos lag, obwohl die DoD „jede Zahl mit Erzeuger im Repo“ verlangte; erst nach dem Verifikationsbefund (V-1, Nutzerentscheidung „Aggregator ins Repo“) kam das Werkzeug. Die Fremdlast-Probe fehlt weiter (nicht reproduzierbar, benannt).
- **Geschärft, keine neue Regel:** Der Erzeuger eines Messprotokolls ist eine Kette (Rohzahl, Aggregation zu Tabellen, Probe der Umgebung); „Erzeuger im Repo“ im Plan eines Messslice nennt jedes Glied, nicht nur das der Rohzahl (`BEO-TDS/spike-prototyp-nicht-im-repo-messung-nicht-wiederholbar`; die Verkörperung in `.claude/commands/plan-welle.md` Schritt 6d und `.claude/commands/implement-slice.md` Schritt 20a liest „Erzeuger“ im Singular; ob der Wortlaut zu schärfen ist, entscheidet der Nutzer, diese Closure ändert keinen Command).
- **Gelernt, benannt:** (i) Der DoD-Punkt, dessen Eingang ein nicht gebauter Slice liefert, fiel im Review auf, nicht in der Planung (`BEO-TDS/dod-haengt-an-offenem-slice-ohne-rueckfuehrung`, jetzt 4×, offen beim Nutzer). (ii) Ein Messprotokoll, dessen Lesart nachgerechnet wurde, war an mehreren Stellen breiter als seine Rohzeilen (F-3 bis F-5, F-10, V-2); die Nachrechnung war jedes Mal der Fund (`BEO-TDS/aussagen-ueber-messung-hinaus`), jetzt ersetzt das Werkzeug die händische Nachrechnung der Tabellen, nicht die der Lesart. (iii) V-4: ein Messwerkzeug, dessen neue Pfade nur durch die Messläufe belegt sind, hat keinen Wächter gegen spätere Änderungen; [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md) hält Messläufe bewusst außerhalb der Gates, die Grenze ist benannt.
- **Frage an die Grenzwerte (kein Sensor, keine Regel):** die Standardfrist `QUERY_TIMEOUT` von 30 s ([SPEC-009](../../../../spec/spezifikation.md#5-metriken-und-tracing-felder)) beendete bei der Referenzlast der Stufe 8 den ersten Export; ob 30 s für diese Last passt, entscheidet der Nutzer über [slice-qa-performance-grenzwerte](../open/slice-qa-performance-grenzwerte.md). Keine benannte Spec-Lücke in einer versionierten Spec; die Lücke „Grenzwerte in [TDS-QA-04](../../../../spec/lastenheft.md#tds-qa-04--performance)“ bleibt unverändert benannt (kein `liegt in`, nichts verkörpert).
- **Nicht gemessen, benannt:** siehe „Ungemessen“ oben.

**Drei Paarungen.** Anker: keine Regel mit `liegt in` verkörpert, daher nichts zu paaren. Folge-Slice: die Slices der Welle in `open/` und `in-progress/` tragen die Lücken „nicht belegt“ ([slice-qa-performance-grenzwerte](../open/slice-qa-performance-grenzwerte.md)) und Rückstau, Heap und Abschluss; **der Import in Aufbau B hat keinen Slice** (nicht angelegt, Träger Nutzer, ausdrücklich so geführt), die Paarung ist dafür nicht durch eine Datei getragen, sondern durch die Benennung hier, in DoD, §6 und im Protokoll. Register: sechs `evidence/slice-qa-performance-durchsatz-sitzungen.md` unter `../observations/BEO-TDS/`, Link auf diese Datei in `done/`.

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

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen: `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` (11 Dateien, Träger dieser Zerlegung; der Teil „gleichzeitige Sitzungen“ aus `slice-transport-listener-lebenszyklus` liegt hier), `BEO-TDS/testserver-bestand-nicht-am-heap-begrenzt` (1 Datei, Risiko oben) und `BEO-TDS/spike-prototyp-nicht-im-repo-messung-nicht-wiederholbar` (Erzeuger im Repo, Regel 6d des Plan-Commands) sind Treffer; die Zahl der Dateien unter `evidence/` ist der Zähler, hier nicht gesetzt.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
