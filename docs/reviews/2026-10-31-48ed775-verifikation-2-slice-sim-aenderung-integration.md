# Verifikation 2: slice-sim-aenderung-integration (Mini-Runde 1f1c8dd, Carveout CO-004) — 2026-10-31

**Rolle:** Verifier (Modul 11), frischer Kontext. Frage: Bauen wir es richtig? Geprüft wird gegen die DoD des Slice [slice-sim-aenderung-integration](../plan/planning/in-progress/slice-sim-aenderung-integration.md), [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) mit `SPEC-035` bis `SPEC-040`, [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md), `AGENTS.md` §3.6 bis §3.8 und das Baseline-Regelwerk `modul-07-carveouts.md`. Eingang sind die [erste Verifikation](2026-10-31-accd7c7-verifikation-slice-sim-aenderung-integration.md) (M-1, L-1 bis L-5), die Mini-Runde 1f1c8dd und der Carveout [CO-004](../plan/carveouts/CO-004-cancel-szenario-spec-040-fehlt.md). Die Eingänge sind nicht der Beleg; die Belege unten sind selbst gefahren.

**Stand:** Worktree-Branch `worktree-agent-ae17d705894ef95da` bei 48ed775 (enthält 1f1c8dd und CO-004). Arbeitsbaum sauber vor dem Lauf, alle Mutationen zurückgenommen. Der Slice liegt in `in-progress/`, §7 ist leer, kein DoD-Haken gesetzt, keine Closure.

**Wichtig:** CO-004 ist eine **Voreinstellung des Koordinators, vom Nutzer nicht entschieden** (Option A; B oder C bleiben möglich). Dieser Bericht bewertet den Carveout auf Regelkonformität, nicht die Wahl.

## Ergebnis in einem Satz

Liefer-Punkte 1 und 2 sind erfüllt; Liefer-Punkt 3 ist mit der Ausnahme CO-004 vier-von-fünf-Szenarien-erfüllt und der Carveout ist in den Pflichtfeldern, im messbaren Trigger, im Folge-Slice und im Index regelkonform, nennt sich aber an keiner der vier Stellen, an denen die Ausnahme sichtbar sein soll. M-1 und L-1 bis L-3 der ersten Verifikation sind behoben (alle sechs Mutationen werden erkannt). Es bleiben **0 HIGH**, **0 MEDIUM** und **4 LOW**.

## Sensor-Belege (selbst gefahren)

Logs: `/tmp/claude-1000/-Development-tdswire/8562f454-84ba-456b-9970-945e69c035f4/scratchpad/ver2-sim/` (`test1.log`, `gates.log`, `sim.log`, `mut.log`). Ein Build des parallelen Verifiers (slice-qa-wartbarkeit) konnte zeitgleich laufen; Laufzeiten sind davon möglicherweise beeinflusst.

| Sensor | Ausgang |
|---|---|
| `docker build --no-cache-filter test --target test --progress=plain .` (unter `timeout 540`) | Exit 0 nach 1 min 14 s: 779 Tests, 0 fehlgeschlagen (die zwei Treffer auf „FAILED“ sind Testnamen `…FAILED_CLOSE_CONNECTION`/`TDS_LOGIN_FAILED`), 1 Gradle-Task ohne Test übersprungen. Die drei neuen Tests laufen und bestehen: `eine regulaere Datei mit Lesefehler ist unlesbar`, `ein Symlink auf eine Datei laedt und einer auf ein Verzeichnis ist unlesbar`, `ein zweiter Sammler derselben Ausfuehrung ruft den Callback ein zweites Mal`. |
| `make gates` | Exit 0 nach 44 s (Test-Layer aus dem Cache des Laufs oben); `simulations-integration` lief darin: „Alle 4 Tests des Referenzclients bestanden“, „Laufzeit 17 s, davon Bau 4 s und Tests 13 s“, Heap max 50 331 648 Bytes, Text von `gross` 64 000 000 Zeichen in 500 000 Zeilen. |
| `make simulations-integration` (einzeln) | Exit 0; **Laufzeit 18 s laut Skript (Bau 2 s, Tests 16 s), 19,8 s Wanduhr**; Server läuft nach den Tests, Log ohne Handlerfehler, ohne Kennwort und ohne Text der Quellenausnahme. Deckt die Angabe „19 s“ im Kopf von `tools/mk/simulationstest.mk` (Einzelmessung, keine Streuung; Kaltlauf in dieser Verifikation nicht gemessen). |
| Mutationen (sechs Änderungen in **einem** Test-Build, Attribution über den Namen des roten Tests) | Exit 1, 779 Tests, genau 6 rot, siehe Tabelle unten. |
| `make docs-check` | siehe Abschnitt „Abschluss“. |

### Mutationen (Zuordnung Änderung zu rotem Test)

| # | Mutation | Rot gemeldet von | Bewertung |
|---|---|---|---|
| 1 | `catch (ignored: IOException)` in `ConfigFiles.readAtMost` entfernt | `SimulationWiringTest > eine regulaere Datei mit Lesefehler ist unlesbar` | **M-1 behoben** (in Verifikation 1 überlebte genau diese Mutation) |
| 2 | Ergebnis im `ChangeExecution` zwischengespeichert (`cached ?: run().also { cached = it }`) | `SimulationBackendChangeTest > ein zweiter Sammler derselben Ausfuehrung ruft den Callback ein zweites Mal` | **L-3 behoben** |
| 3 | `Files.isRegularFile(file)` zu `Files.isRegularFile(file, NOFOLLOW_LINKS)` (Symlink wird nicht mehr gefolgt) | `SimulationWiringTest > ein Symlink auf eine Datei laedt und einer auf ein Verzeichnis ist unlesbar` | **L-1 behoben** (Symlink-Aussage jetzt gemessen) |
| 4 | Zähler `count >= 0` zu `true` (negative Callback-Zahl) | `SimulationBackendChangeTest > ein Fehler des Callbacks ist TDS_BACKEND_ERROR …` | erkannt |
| 5 | Listenkopie in `SimulationSetup.owned` entfernt (F-11 des Reviews) | `SimulationWiringTest > der Katalog behaelt die Listen des Integrators als Kopie` | erkannt |
| 6 | Heap `48m` zu `40m` in `build.gradle.kts` | `IntegrationZahlenTest > Heap und Text des Integrationslaufs stimmen in Gradle Spezifikation und README` | erkannt |

Hinweis zu Mutation 1 und 3: Beide liegen in derselben Funktion; die Attribution ist trotzdem eindeutig, weil der `/proc/self/mem`-Test nicht über einen Symlink läuft und der Symlink-Test keinen Lesefehler auslöst. Eine isolierte Einzelmutation von 1 wurde nicht eigens gebaut; das Ergebnis der ersten Verifikation (Mutation C grün) und dieser Lauf (rot) unterscheiden sich allein durch den neuen Test.

## DoD Punkt für Punkt

| DoD-Punkt (Slice §2) | Befund |
|---|---|
| 1 Änderungsweitergabe | **erfüllt.** Unverändert gegenüber Verifikation 1; durch Mutation 4 (negative Zahl) und Mutation 2 (genau einmal, nur ein Sammler) erneut belegt. Die Zusage „genau einmal“ trägt jetzt ihre Bedingung am Test (zwei Sammler zwei Aufrufe), L-3 damit zu. |
| 2 Verdrahtung | **erfüllt.** Mutation 3 und 5 belegen Symlink-Verhalten und Listenkopie, der neue `IOException`-Test den Startabbruch bei Lesefehler (`Konfiguration ungueltig: SIMULATION_DESCRIPTION_FILE: UNREADABLE`, `BootResult.Aborted`). Wächter und Selbsttest nicht neu gefahren (nicht berührt; `make gates` grün, `make detekt-selbsttest` ist Teil davon). |
| 3 Test-Gate nach `SPEC-040` | **mit CO-004 erfüllt für vier von fünf Szenarien; Cancel mit Folgeabfrage ausgenommen.** Vier Referenzclient-Tests grün in `make simulations-integration` und `make gates`; Target steht in `harness/README.md` §Sensors; Aufnahme in `make gates` trägt die gemessene Laufzeit (siehe Abschnitt [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md)). Kein DoD-Haken ist gesetzt; ob der Punkt mit dem Carveout als erfüllt gilt, ist Sache der Closure und der ausstehenden Nutzerentscheidung. |
| Nachweis: `@spec`-Tags, Tests grün | erfüllt; die drei neuen Tests tragen `@spec SPEC-035`. |
| Nachweis: `make gates` grün | erfüllt (Exit 0). |
| Nachweis: Review-Report | erfüllt ([Report a7e6afa](2026-10-31-a7e6afa-slice-sim-aenderung-integration.md)). |
| Nachweis: Zuordnung Test/Festlegung, Doku, Register, Risiko-Ausgänge, Paarungen, Closure | offen (Closure-Aufgaben, nicht Auftrag; §7 „Wird bei der Closure gefüllt“). Kein Befund. |

## Carveout CO-004 gegen Modul 7 und AGENTS.md §3.6

| Prüfpunkt | Befund |
|---|---|
| Sechs Pflicht-Header-Felder (Status, Datum angelegt, Letzte Prüfung, Betroffenes Gate, Geltungsbereich, Folge-Slice) | **vorhanden.** Status „Aktiv“, angelegt und geprüft 2026-10-31, Gate `make simulations-integration`, Geltungsbereich Cancel-Teil von `SPEC-040` und DoD-Punkt 3, Folge-Slice als Link, dazu Verantwortlich. |
| Schwelle | Das Gate hat keine Schwelle; der Carveout sagt das ausdrücklich („nimmt keine Schwelle und keine Regel aus“). Damit entfällt der Verweis auf eine ADR. |
| Auflösungs-Trigger beobachtbar und messbar | **ja.** Zwei Bedingungen, die ein Dritter ohne Rückfrage beurteilt: (a) `slice-attention-cancel-timeout` liegt in `done/`, (b) ein Cancel-Szenario mit Folgeabfrage läuft in `make simulations-integration` und besteht. Der Trigger bei Umstellung durch den Nutzer (B oder C) ist als „gilt die Auflösung, die er festlegt“ offen gelassen; das ist eine ehrliche Setzung, kein Mangel. |
| Folge-Slice trägt den Auflösungs-Punkt in der DoD | **ja.** [slice-attention-cancel-timeout](../plan/planning/open/slice-attention-cancel-timeout.md) §2 vierter Punkt „Cancel-Szenario von SPEC-040 am Referenzclient … (Auflösung des Carveouts CO-004)“ mit Gate-Lauf als Bedingung; §4 Hinweis zum Carveout. Siehe L-4 zur Punktzahl. |
| Index in `carveouts/README.md` | **ja.** Zeile CO-004 mit Gate, Trigger, Folge-Slice und Setzung des Koordinators. |
| Auflösung per `git mv` nach `done/`, Gate-Ausnahme zurücknehmen, Bindung zurücksetzen | in der Verifikations-Checkliste des Carveouts genannt, Bindung-Spalte aber siehe L-1 (sie nennt CO-004 nicht, kann also auch nicht zurückgesetzt werden). |
| `AGENTS.md` §3.6 (keine stille Senkung) | **eingehalten.** Das Gate `simulations-integration` existierte vorher nicht (`SPEC-040` führte „Gate noch einzurichten“), eine Schwelle oder Linterregel wurde nicht gesenkt; `config/detekt/detekt.yml` ist im Slice nur verschärft (Wächter für die YAML-Bibliothek). Der Carveout ist eine befristete Ausnahme eines Teils (Szenario), die Schwelle (alle Szenarien von `SPEC-040`) bleibt der Zielzustand. Einwand, der bleibt: `SPEC-040` selbst wurde in ebfe3ba von „… Fehler der Zeilenquelle, Cancel“ auf „Cancel gehört nicht zum Lauf“ umgeschrieben (Spec, Rang 2); das Lastenheft ([TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend)) verlangt Cancel im Gate nicht, daher kein Konflikt nach oben, aber der Satz liest sich als dauerhaft (L-1). |
| Werkzeug-Wahl (Modul 7 Trichter) | Carveout passt: einzelne, abgrenzbare Diskrepanz mit Folge-Slice; der Trigger ist ernst zu erreichen, hängt aber an offenen Nutzerentscheidungen des Folge-Slice (Inhalt der Cancel-Antwort, Cancel/Timeout als Fehler, ATTENTION im Handshake). Das benennt der Carveout selbst. |
| Gate-Output nennt die `CO-<NNN>` (Modul 7) | **nein**, siehe L-1. |

## Konsistenz der Dokumentation zur Ausnahme

| Stelle | Nennt die Ausnahme? | Nennt CO-004? |
|---|---|---|
| `spec/spezifikation.md` `SPEC-040` | ja: „Cancel mit Folgeabfrage gehört nicht zum Lauf: der Server beantwortet ein ATTENTION-Paket während einer Anfrage nicht“ | **nein** |
| `harness/README.md` §Sensors, Zeile `make simulations-integration` | indirekt: nennt nur die vier laufenden Szenarien | **nein**; Bindung nennt [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md) und [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md), nicht den Carveout (anders als `make referenztest-dotnet` mit CO-001/CO-002) |
| `referenztest-dotnet/Integration/SimulationBackendTests.cs` KDoc | ja, gleiche Begründung | **nein** |
| `tools/simulationstest.sh` Kopf (Zusage-Absatz) | ja: „nicht belegt: Cancel (der Server beantwortet ATTENTION während einer Anfrage nicht) …“ | **nein**; das Skript gibt bei Lauf auch keine Zeile zur Ausnahme aus |
| Slice §1, §2 | ja, mit Link | ja |
| `docs/plan/carveouts/README.md`, CO-004, Folge-Slice | ja | ja |

Die Aussagen widersprechen sich nicht; die Begründung ist an allen vier Code- und Spec-Stellen dieselbe. **Niemand außer Plan, Carveout-Index und Folge-Slice nennt CO-004.** Die Geltungs-Konfiguration von CO-004 selbst führt diese vier Stellen als „nennt die Lücke“ auf, setzt aber den Verweis in die Gegenrichtung nicht voraus. Das ist ein LOW-Befund (L-1).

## ADR-0022 (Proposed): was an diesem Slice davon abhängt

Nicht Gegenstand dieser Verifikation. [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md) nimmt `simulations-integration` aus `GATE_CHECKS`. Abhängig davon sind in diesem Slice, wenn die ADR `Accepted` wird:

- **Slice §2 Punkt 3** („ob es in `make gates` aufgenommen wird, entscheidet die gemessene Laufzeit“) und **§6 Risiko „Laufzeit des Integrationstests im Gate“**: Die Messung (18 bis 19 s im Warmlauf, in dieser Verifikation 17 und 18 s; Kaltlauf 131 s laut Kopf des Fragments) bliebe belegt, die Aufnahme entfiele; das Risiko bekäme den Ausgang „entfallen“ statt „Aufnahme begründet“.
- `tools/mk/simulationstest.mk`: Zeile `GATE_CHECKS += simulations-integration` und die Begründung im Kopf (Setzung zum Maßstab der Aufnahme).
- `spec/spezifikation.md` `SPEC-040`: „(`make simulations-integration`, Teil von `make gates`)“.
- `harness/README.md`: der Satz „ein roter Test macht `make gates` rot“ in der Sensor-Zeile.
- **CO-004** selbst: „Betroffenes Gate: … (in `make gates`)“ und die Bedingung „`make gates` grün ohne Ausnahme“ in der Verifikations-Checkliste; der Auflösungs-Trigger (b) „in `make simulations-integration`“ bleibt davon unberührt.
- Slice §5 (Closure-Trigger „`make gates` ist grün“) belegt den Integrationslauf dann nicht mehr; die Closure müsste ihn gesondert nennen. Auch die `docs/plan/spec-abdeckung.md`-Zeile zu `SPEC-040` (L-5 der ersten Verifikation, weiter offen) ändert sich dadurch nicht.

## Behebung der Befunde der ersten Verifikation

| Befund | Behoben? | Beleg |
|---|---|---|
| M-1 (`IOException`-Zweig ohne Test) | **ja** | Test über `/proc/self/mem` (regulär für Java, Lesen endet mit „Input/output error“, auch als Root); der Test prüft die Voraussetzung selbst (`isRegularFile` wahr, `IOException` beim Lesen). Mutation 1 rot. Ressourcen: Der Test ist an Linux und `/proc` gebunden; in der Docker-Stage `test` (Linux) gemessen grün. |
| L-1 (Symlink und FIFO ungemessen) | **ja für den Symlink, Text für die FIFO.** | Symlink auf Datei lädt, Symlink auf Verzeichnis ist `UNREADABLE`; Mutation 3 rot. FIFO ist im KDoc als nicht getestet gekennzeichnet. Restpunkt siehe L-2. |
| L-2 (Spec „Anker an Skalar“) | **ja** | `SPEC-035` „Skalare und Dokument“ lautet jetzt „kein Anker an einer Abbildung oder Liste, kein Alias, kein Tag und kein Merge-Schlüssel (ein Anker an einem Skalar ohne Alias lädt und bleibt ohne Wirkung, gemessen am Adapter)“; deckt sich mit dem Absatz Verdrahtung und mit `YamlTreeReaderTest`. |
| L-3 (zweiter Sammler) | **ja** | Test mit zwei Sammlern, Zähler 1 dann 2; Mutation 2 rot. |
| L-4 (Plan nennt die Lücke nicht) | **ja** | Slice §1 und §2 nennen die Ausnahme mit CO-004-Link und der Setzung; Folge: siehe L-1 dieses Berichts für die Rückrichtung. |
| L-5 (`spec-abdeckung.md`, .NET-Tests ohne Tag) | unverändert | nicht Teil der Mini-Runde; der Beleg des Gate-Laufs ist diese und die erste Verifikation. |

## Findings

| ID | Kategorie | Befund | Ort | Verifizierbar |
|---|---|---|---|---|
| L-1 | LOW | CO-004 wird an keiner der vier Stellen genannt, die die Ausnahme sichtbar machen (`SPEC-040`, Sensor-Zeile `harness/README.md`, C#-KDoc, Kopf von `tools/simulationstest.sh`); die Bindung-Spalte der Sensor-Zeile nennt [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md) und [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md), nicht den Carveout, und der Gate-Output nennt keine `CO-<NNN>`. Modul 7 verlangt, dass die Gate-Konfiguration die Kennung im Gate-Output nennt und dass die Auflösung die Bindung zurücksetzt. Dazu liest sich der Satz in `SPEC-040` („gehört nicht zum Lauf“) als dauerhafter Ausschluss, nicht als befristete Ausnahme. Wirkung: Wer eines dieser vier Dokumente liest, findet nicht den Trigger und den Folge-Slice. | `spec/spezifikation.md` `SPEC-040`; `harness/README.md` Sensor-Zeile; `referenztest-dotnet/Integration/SimulationBackendTests.cs`; `tools/simulationstest.sh` | ja (`grep -rn CO-004` nennt nur Plan, Index, Folge-Slice und den Carveout selbst) |
| L-2 | LOW | Das KDoc von `ConfigFiles.readAtMost` sagt „ein Verzeichnis, ein Gerät und eine Datei ohne Leserecht sind `UNREADABLE` (getestet)“. „Ohne Leserecht“ ist **nicht** getestet: Der Test über `/proc/self/mem` deckt einen Lesefehler an einer regulären Datei, und sein eigenes KDoc sagt ausdrücklich, dass Rechte-Entzug nicht taugt (Tests laufen als Root). Die Aussage ist breiter als der Beleg (`AGENTS.md` §3.8); richtig wäre „eine reguläre Datei mit Lesefehler“. | `src/main/kotlin/io/github/pt9912/tdswire/config/ConfigFiles.kt`, KDoc von `readAtMost` | ja (Textvergleich KDoc gegen Test-KDoc) |
| L-3 | LOW | `CO-004` und `SPEC-040` nennen „in `make gates`“; das ist an die Aufnahme in `GATE_CHECKS` gebunden, die [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md) (Proposed) zurücknehmen würde. Bei `Accepted` stehen vier Stellen (Abschnitt [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md)) falsch; die Carveout-Checkliste („`make gates` grün ohne Ausnahme“) verlöre ihren Bezug. Heute korrekt, daher LOW und ohne Maßnahme vor der Entscheidung. | `docs/plan/carveouts/CO-004-cancel-szenario-spec-040-fehlt.md`; `spec/spezifikation.md`; `tools/mk/simulationstest.mk`; `harness/README.md` | nein (Vorhersage) |
| L-4 | LOW | [slice-attention-cancel-timeout](../plan/planning/open/slice-attention-cancel-timeout.md) §2 trägt jetzt vier Liefer-Punkte (Cancel/ATTENTION, Wettlauf, Zeitlimits, Cancel-Szenario von `SPEC-040`); die Regel der Vorlage erlaubt höchstens drei. Der Punkt selbst benennt die Spannung („Ob es als vierter Liefer-Punkt oder in einem Folge-Slice steht, entscheidet die Zerlegung“); der Auflösungs-Punkt des Carveouts liegt damit in einem Plan, der vor dem Start zerlegt werden muss. Kein Fehler dieses Slice. | `docs/plan/planning/open/slice-attention-cancel-timeout.md` §2 | ja (Zählung der Checkboxen) |

## AGENTS.md §3.6 bis §3.8 (Mini-Runde)

- §3.6: keine Schwelle gesenkt, kein `@Suppress`; die Mini-Runde berührt weder `config/detekt/detekt.yml` noch Gate-Ziele.
- §3.7: die geänderten KDocs (`readAtMost`, Test-KDocs) sind im Indikativ und tragen Zusage oder Grenze; kein Kommentar beschreibt eine verworfene Alternative.
- §3.8: eine Stelle trägt weiter als ihr Beleg (L-2); die Symlink-Aussage ist jetzt gemessen.

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 0 |
| LOW | 4 (L-1 bis L-4) |

## Verdikt

Liefer-Punkte 1 und 2 sind erfüllt, die sechs geprüften Mutationen werden alle erkannt, M-1 und L-1 bis L-3 der ersten Verifikation sind behoben. CO-004 ist als Dokument regelkonform (Pflichtfelder, messbarer Trigger, Folge-Slice mit Auflösungs-Punkt, Index; keine Senkung nach §3.6, weil das Gate vorher nicht bestand), aber nur an seiner eigenen Stelle sichtbar (L-1). Übergabe an den Planner: Der Carveout ist eine Voreinstellung; die Nutzerentscheidung (A, B oder C) steht aus und ist Voraussetzung der Closure. Wird A bestätigt, ziehen L-1 und L-2 den Text nach (Implementer, kleine Änderung), L-3 folgt der Entscheidung über [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md), L-4 der Zerlegung von slice-attention-cancel-timeout.
