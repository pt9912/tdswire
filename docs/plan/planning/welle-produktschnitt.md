# Welle welle-produktschnitt: Bibliothek als Produkt, Testserver nur zum Testen (Umbau nach [ADR-0025](../adr/0025-produktschnitt-bibliothek-und-testserver.md))

**Lifecycle:** Diese Datei entsteht bei der **Eröffnung** der Welle und liegt
flach unter `docs/plan/planning/`; bei Closure wandert sie per `git mv` nach
`done/` (neben ihre `welle-<Kennung>-results.md`). Der Zustand ist die
Verzeichnis-Position — kein Status-Feld. **Geplante Wellen bekommen noch keine
Datei:** Sie stehen in der Roadmap unter *Nächste Wellen* und nirgends sonst —
zwei Positionen, nicht drei.

**Zielmeilenstein:** kein Meilenstein-Bezug.

**Verantwortlich:** pt9912. **Datum:** 2026-11-01.

---

## 1. Welle-Ziel

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wann Arbeit eine Welle braucht.

`tdswire.jar` ist ein Bibliotheks-Jar ohne `Main-Class`, und `lib/` führt kein Jackson; der Server als Prozess besteht nur als Testserver im nicht assoziierten Source-Set `testserver`, der die Bibliothek ausschließlich über `TdsServer.start` und die öffentliche Fläche bindet. Spezifikation, Skripte, `Dockerfile` und `harness/README.md` sagen dasselbe, und die E2E-Stufen, die den Serverprozess ansprechen (Stufen 1 bis 3 und 5 bis 7 von `make e2e`), laufen gegen den Testserver ([ADR-0025](../adr/0025-produktschnitt-bibliothek-und-testserver.md), [TDS-RB-01](../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose)). Die Aussage steht an drei Belegen: dem Übersetzungslauf des Source-Sets (ein Zugriff auf `internal` bricht ihn), der Manifest-Prüfung im Bau (kein `Main-Class`-Eintrag) und einem grünen `make e2e`. Sie gilt nicht für Java-Quellen oder Reflection im Source-Set `testserver` (akzeptiertes Negativ der ADR), nicht für ein Betriebs-Image (es gibt keines mehr) und nicht für das Log der Bibliothek (bis [ADR-0020](../adr/0020-logformat-und-observability-schnittstelle.md) liest kein E2E-Lauf Zeilen der Bibliothek).

## 2. Trigger (Welle startet)

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Roadmap-Regeln — ein Trigger ist **beobachtbar** dann, wenn ein *anderer*
Mensch ohne Rückfrage sagen kann, ob er eingetreten ist; ein Datum darf erwähnt
werden, aber nie Trigger sein. Und der **Start**-Trigger ist **kein Ergebnis
dieser Welle**: Steht er in der Slice-Liste unten, ist er falsch platziert.

- [ADR-0025](../adr/0025-produktschnitt-bibliothek-und-testserver.md) steht im ADR-Index auf `Accepted` (erfüllt: Annahme am 2026-11-01).
- Die produktiven Wellen liegen in `done/`: [welle-anfrageverarbeitung](welle-anfrageverarbeitung.md) und [welle-fehler-abbruch](welle-fehler-abbruch.md) (Dispatch, Fehlerpfad und Stop-Pfad des Bootstrap, den der Umbau teilt) sowie [welle-betrieb-diagnose](welle-betrieb-diagnose.md) (Startdiagnose am Prozess und Log-Schnittstelle, die der Umbau berührt); der vorrätige Slice `slice-codec-datentypen-erweiterung` aus [welle-anfrageverarbeitung](welle-anfrageverarbeitung.md) ist davon ausgenommen. Der Zuschnitt „nach den produktiven Slices“ folgt der Nutzerentscheidung vom 2026-11-01; die Auswahl dieser drei Wellen ist die Lesart des Planners (Setzung) und gehört dem Nutzer zur Bestätigung.
- [slice-e2e-geruest](in-progress/slice-e2e-geruest.md) liegt in `done/`: `tools/e2e.sh` und die E2E-Abdeckungs-Sicht stehen, die der Umbau umstellt.
- Weitere Voraussetzungen gelten je Slice und stehen in dessen §4 (die Entscheidung des Nutzers zu Offen-Punkt 1 der ADR als Folge-ADR zu Entscheidung 3 von [ADR-0021](../adr/0021-oeffentlicher-integrations-einstieg.md); `slice-spec-nachzug-produktschnitt` vor `slice-testserver-herausloesen`): Sie verzögern einen Slice, nicht den Start der Welle.

## 3. Closure-Trigger (Welle schließt)

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wann Arbeit eine Welle braucht — der Trigger muss das *Mehr* gegenüber den
einzelnen Slice-DoDs benennen; kann er das nicht, liegt keine Welle vor.

- Alle Slices aus §4 in `done/`.
- `make gates` grün, einschließlich `make test`, `make skript-selbsttest` und `make kopplung-zahlen`.
- Das Mehr gegenüber den Slice-DoDs, an einem Stand nach dem letzten Slice: (1) `make e2e` ist grün (sieben Stufen), und kein Stufentext, Skript und kein `Dockerfile` nennt `MainKt`, die Stage `runtime` oder die Stage `build`; (2) `build/libs/lib` in der Stage `build-jar` führt kein Jackson, und `integratorTest` läuft ohne Jackson im Klassenpfad des Source-Sets `integrator` (die Aussage der Fitness Function der ADR, bisher nicht gemessen: `BEO-TDS/jackson-abhaengigkeit-fuer-embedder-nicht-gemessen`); (3) die Mutation „Testserver greift auf ein `internal`-Symbol zu“ macht die Übersetzung des Source-Sets `testserver` rot; (4) eine Suche über `spec/`, `harness/README.md`, der Wurzel-`README.md`, `tools/`, `Dockerfile` und `build.gradle.kts` findet keine zweite Fundstelle der abgelösten Aussagen (`simulationFile`, `SIMULATION_DESCRIPTION_FILE` als Eingang der Bibliothek, „Jar mit Startklasse“, „Laufzeit-Image“) außer in Aussagen über den Testserver (gesucht wird die Kernaussage, nicht die genannte Zeile: `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`).
- Die Closure-Notiz führt die Aussagen der Welle in zwei Spalten, *gemessen* und *Festlegung*, und nennt für den Verlust beim Log (Konsequenzen der ADR, Prüfungen (a) bis (c)) den Träger: die Skript-Prüfungen sind herausgenommen oder als Bibliotheks-Log-Prüfung gekennzeichnet, der Ersatz kommt aus [ADR-0020](../adr/0020-logformat-und-observability-schnittstelle.md).
- Closure-Notiz in `welle-produktschnitt-results.md`.

## 4. Slices in dieser Welle

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Lifecycle als State Machine — der Zustand eines Slice ist sein
Lifecycle-Verzeichnis und wird hier **nicht** gespiegelt.

| Slice | Titel | Bezug |
|---|---|---|
| [slice-spec-nachzug-produktschnitt](open/slice-spec-nachzug-produktschnitt.md) | Spezifikation und Harness-Index auf den Produktschnitt nachziehen (`SPEC-052`, `SPEC-005`, `SPEC-010`, `SPEC-023`, `SPEC-035`, `SPEC-040`, `SPEC-059`) | [TDS-RB-01](../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [ADR-0025](../adr/0025-produktschnitt-bibliothek-und-testserver.md) |
| [slice-testserver-herausloesen](open/slice-testserver-herausloesen.md) | Testserver im Source-Set `testserver` herauslösen, `simulationFile` und Jackson aus der Bibliothek nehmen, Test-Einstiege auf `TdsServer.start` umstellen | [TDS-RB-01](../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [ADR-0025](../adr/0025-produktschnitt-bibliothek-und-testserver.md) |
| [slice-jar-ohne-main-und-image](open/slice-jar-ohne-main-und-image.md) | Bibliotheks-Jar ohne `Main-Class`, Stages `runtime` und `build` entfernt, Container-Lauf auf den Testserver umgestellt | [TDS-RB-01](../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [ADR-0025](../adr/0025-produktschnitt-bibliothek-und-testserver.md) |

Reihenfolge: `slice-spec-nachzug-produktschnitt`, dann `slice-testserver-herausloesen`, dann `slice-jar-ohne-main-und-image`; jede Kante ist ein Start-Trigger in §4 des Folge-Slice (der Umbau (b) setzt (a) voraus, [ADR-0025](../adr/0025-produktschnitt-bibliothek-und-testserver.md) Folgepflicht 2).

**Register-Sichtung bei der Eröffnung** (`observations/BEO-TDS/`, durchgegangen am 2026-11-01). Unter der Schwelle von 3× oder verkörpert, ohne eigenen Slice: `BEO-TDS/jackson-abhaengigkeit-fuer-embedder-nicht-gemessen` (1×, Träger: Closure-Kriterium (2)), `BEO-TDS/spec-059-zusagen-vor-produktschnitt` (1×, Träger: `slice-spec-nachzug-produktschnitt`), `BEO-TDS/zwei-artefakt-stages-nebeneinander` (1×, entfällt mit `slice-jar-ohne-main-und-image`), `BEO-TDS/nachweis-von-der-falschen-seite-der-grenze` (2×) und `BEO-TDS/java-quelle-umgeht-kotlin-regel` (1×; Grenze des Nachweises des Testservers), `BEO-TDS/zahl-an-mehreren-orten-ohne-sensor` (1×; `kopplung-zahlen` zieht mit), `BEO-TDS/move-commit-laesst-detekt-rot` (1×; reine Move-Commits in `slice-testserver-herausloesen`). Über der Schwelle und berührt: `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` (3×, offen; Closure-Kriterium (4) und die DoD des Spec-Nachzugs nehmen sie auf, ein eigener Slice ist nicht nötig, weil die Suche über beide Orte Teil der DoD ist) und `BEO-TDS/dod-haengt-an-offenem-slice-ohne-rueckfuehrung` (3×, offen; die Rückführungen in §4 der drei Slices nennen die Kanten vorab).

## 5. Abhängigkeiten

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Roadmap-Struktur: fünf Abschnitte.

- Blockiert: [welle-referenzabgleich-release](welle-referenzabgleich-release.md) nur im Wortlaut ihres Closure-Kriteriums („Läufe gegen das Laufzeit-Image aus `slice-release-artefakt-lizenz`“) und der Nutzer-Doku zum Start im Container: [ADR-0025](../adr/0025-produktschnitt-bibliothek-und-testserver.md) entfernt das Produkt-Image. Die Umschreibung jener Welle ist nicht Inhalt dieser Welle (Frage an den Nutzer im Bericht der Planung).
- Wird blockiert von: [welle-anfrageverarbeitung](welle-anfrageverarbeitung.md), [welle-fehler-abbruch](welle-fehler-abbruch.md) und [welle-betrieb-diagnose](welle-betrieb-diagnose.md) (Start-Trigger); der Folge-ADR zu Offen-Punkt 1 der ADR (Öffentlichkeit von `SimulationBackend` und `SimCatalog`) für `slice-spec-nachzug-produktschnitt` und `slice-testserver-herausloesen`.
- Berührt, blockiert aber nicht: [slice-explicit-api-umbau](open/slice-explicit-api-umbau.md) (ohne Welle, Nutzerentscheidung offen): `simulationFile`, `ConfigKeys` und Jackson verlassen die Fläche, die Menge der Namen ändert sich; [slice-release-doku-prozess](open/slice-release-doku-prozess.md) (Lizenzhinweise der Bibliotheken, Doku zum Start im Container).

## 6. Out-of-Scope für diese Welle

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Eröffnung Schritt 1 — Out-of-Scope gehört zur
Zielsetzung: Was nicht ausdrücklich ausgeschlossen ist, dehnt die Welle, bis
der Closure-Trigger unerreichbar wird.

- Das Log der Bibliothek und die Entscheidung, wie Embedder und Testserver es erhalten: [ADR-0020](../adr/0020-logformat-und-observability-schnittstelle.md) (`Proposed`) und [welle-betrieb-diagnose](welle-betrieb-diagnose.md); diese Welle nimmt die betroffenen Skript-Prüfungen heraus oder kennzeichnet sie, sie ersetzt sie nicht.
- Der Umbau auf `explicitApi` und die Verkleinerung der öffentlichen Fläche ([slice-explicit-api-umbau](open/slice-explicit-api-umbau.md)): eigene Nutzerentscheidung über Zuordnung und Zuschnitt; die Welle ändert die Öffentlichkeit nur für das, was der Testserver nach Offen-Punkt 1 der ADR braucht, und nur über die Folge-ADR.
- Ein Produkt-`main`, ein Betriebs-Image oder die Veröffentlichung in einem Artefakt-Repository: Re-Evaluierungs-Trigger der ADR, eigene Entscheidung.
- Ein Sensor gegen Java-Quellen oder Reflection im Source-Set `testserver` (`BEO-TDS/java-quelle-umgeht-kotlin-regel`): akzeptiertes Negativ der ADR.
- Änderungen an Format, Teilmenge, Typabbildung und Fehlerfällen der Tabellenbeschreibung ([ADR-0012](../adr/0012-simulations-backend-beschreibung-und-formen.md)): sie gelten für die Datei des Testservers unverändert; die Welle verschiebt den Leser, sie ändert ihn nicht.
- Lizenzhinweise der Bibliotheken in `lib/` (Rechtsfrage, Träger [slice-release-doku-prozess](open/slice-release-doku-prozess.md)) und Änderungen am Protokollverhalten: nicht Gegenstand dieses Schnitts.

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-traceability.md`
§Herkunfts-Anker für Steering-Loop-Regeln — dort die **Ruheort-Regel**: Die
beiden Zeiger unten sind so zu schreiben, wie sie vom Ruheort `done/` auflösen,
nicht vom Schreibort.

Ergebnis: `welle-produktschnitt-results.md`
Zähler: `../observations/` <!-- d-check:ignore (Ruheort done/: der Zeiger löst erst nach dem Verschieben auf) -->
