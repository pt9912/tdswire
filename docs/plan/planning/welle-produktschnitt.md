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

`tdswire.jar` ist ein Bibliotheks-Jar ohne `Main-Class`, und `lib/` führt kein Jackson; der Server als Prozess besteht nur als Testserver im nicht assoziierten Source-Set `testserver`, der die Bibliothek ausschließlich über `TdsServer.start` und die öffentliche Fläche bindet. Spezifikation, Skripte, `Dockerfile` und `harness/README.md` sagen dasselbe, und die E2E-Stufen, die den Serverprozess ansprechen (Stufen 1 bis 3 und 5 bis 7 von `make e2e`), laufen gegen den Testserver ([ADR-0025](../adr/0025-produktschnitt-bibliothek-und-testserver.md), [TDS-RB-01](../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose)). Die Aussage steht an drei Belegen: dem Übersetzungslauf des Source-Sets (ein Zugriff auf `internal` bricht ihn), der Manifest-Prüfung im Bau (kein `Main-Class`-Eintrag) und einem grünen `make e2e`. Sie gilt nicht für Java-Quellen oder Reflection im Source-Set `testserver` (akzeptiertes Negativ der ADR), nicht für ein Betriebs-Image (es gibt keines mehr) und nicht für das Log der Bibliothek ([ADR-0020](../adr/0020-logformat-und-observability-schnittstelle.md) ist `Accepted` und legt Modell, Senke und Parameter von `start` fest; gebaut wird es in [welle-betrieb-diagnose](welle-betrieb-diagnose.md), und bis [slice-betrieb-log-adapter-testserver](open/slice-betrieb-log-adapter-testserver.md) in `done/` liegt, liest kein E2E-Lauf Zeilen der Bibliothek). Die öffentliche Fläche der Bibliothek ist am Ende der Welle mit `explicitApi` festgelegt und verkleinert ([slice-explicit-api-umbau](done/slice-explicit-api-umbau.md)), und der Testserver übersetzt gegen genau diese Fläche.

## 2. Trigger (Welle startet)

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Roadmap-Regeln — ein Trigger ist **beobachtbar** dann, wenn ein *anderer*
Mensch ohne Rückfrage sagen kann, ob er eingetreten ist; ein Datum darf erwähnt
werden, aber nie Trigger sein. Und der **Start**-Trigger ist **kein Ergebnis
dieser Welle**: Steht er in der Slice-Liste unten, ist er falsch platziert.

- [ADR-0025](../adr/0025-produktschnitt-bibliothek-und-testserver.md) steht im ADR-Index auf `Accepted` (erfüllt: Annahme am 2026-11-01).
- Der Nutzer verlangt den Start der Welle (Nutzerentscheidung vom 2026-11-01: der frühere Zuschnitt „nach den produktiven Wellen“ entfällt; Drift-Log der Roadmap). Beobachtbar ist das als Nennung des Starts durch den Nutzer; der Planner trägt dann hier das Datum ein. **Stand: eingetreten am 2026-11-01** (der Nutzer verlangte den Start nach Lastenheft 0.6.1; Verantwortlicher: pt9912).
- Weitere Voraussetzungen gelten je Slice und stehen in dessen §4 (`slice-spec-nachzug-produktschnitt` vor `slice-testserver-herausloesen`; [slice-e2e-geruest](in-progress/slice-e2e-geruest.md) in `done/` vor `slice-testserver-herausloesen`, weil der Umbau `tools/e2e.sh` und die E2E-Abdeckungs-Sicht umstellt; `slice-testserver-herausloesen` vor `slice-explicit-api-umbau`): Sie verzögern einen Slice, nicht den Start der Welle. Die Folge-ADR zu Offen-Punkt 1 der [ADR-0025](../adr/0025-produktschnitt-bibliothek-und-testserver.md) ist mit [ADR-0026](../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md) angenommen (Variante (i)) und kein Trigger mehr.

## 3. Closure-Trigger (Welle schließt)

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wann Arbeit eine Welle braucht — der Trigger muss das *Mehr* gegenüber den
einzelnen Slice-DoDs benennen; kann er das nicht, liegt keine Welle vor.

- Alle Slices aus §4 in `done/`.
- `make gates` grün, einschließlich `make test`, `make skript-selbsttest` und `make kopplung-zahlen`.
- Das Mehr gegenüber den Slice-DoDs, an einem Stand nach dem letzten Slice: (1) `make e2e` ist grün (sieben Stufen), und kein Stufentext, Skript und kein `Dockerfile` nennt `MainKt`, die Stage `runtime` oder die Stage `build`; (2) `build/libs/lib` in der Stage `build-jar` führt kein Jackson, und `integratorTest` läuft ohne Jackson im Klassenpfad des Source-Sets `integrator` (die Aussage der Fitness Function der ADR, bisher nicht gemessen: `BEO-TDS/jackson-abhaengigkeit-fuer-embedder-nicht-gemessen`); (3) die Mutation „Testserver greift auf ein `internal`-Symbol zu“ macht die Übersetzung des Source-Sets `testserver` rot; (4) eine Suche über `spec/`, `harness/README.md`, der Wurzel-`README.md`, `tools/`, `Dockerfile` und `build.gradle.kts` findet keine zweite Fundstelle der abgelösten Aussagen (`simulationFile`, `SIMULATION_DESCRIPTION_FILE` als Eingang der Bibliothek, „Jar mit Startklasse“, „Laufzeit-Image“) außer in Aussagen über den Testserver (gesucht wird die Kernaussage, nicht die genannte Zeile: `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`); (5) `explicitApi` strict übersetzt `src/main` ohne Diagnose, und beide nicht assoziierten Source-Sets (`integrator`, `testserver`) übersetzen gegen die verkleinerte Fläche (die Fläche aus [ADR-0021](../adr/0021-oeffentlicher-integrations-einstieg.md) Entscheidung 3, geschärft durch [ADR-0026](../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md) und [ADR-0020](../adr/0020-logformat-und-observability-schnittstelle.md)).
- Die Closure-Notiz führt die Aussagen der Welle in zwei Spalten, *gemessen* und *Festlegung*, und nennt für den Verlust beim Log (Konsequenzen der ADR, Prüfungen (a) bis (c)) den Träger: die Skript-Prüfungen sind herausgenommen oder als Bibliotheks-Log-Prüfung gekennzeichnet; den Ersatz (Prüfung der Ereignisse im Testserver nach [ADR-0020](../adr/0020-logformat-und-observability-schnittstelle.md)) trägt [slice-betrieb-log-adapter-testserver](open/slice-betrieb-log-adapter-testserver.md) in [welle-betrieb-diagnose](welle-betrieb-diagnose.md). Liegt jener Slice bei der Closure dieser Welle noch nicht in `done/`, steht er als Träger mit seinem Verzeichnis in der Notiz; die Welle wartet nicht auf ihn.
- Closure-Notiz in `welle-produktschnitt-results.md`.

## 4. Slices in dieser Welle

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Lifecycle als State Machine — der Zustand eines Slice ist sein
Lifecycle-Verzeichnis und wird hier **nicht** gespiegelt.

| Slice | Titel | Bezug |
|---|---|---|
| [slice-spec-nachzug-produktschnitt](done/slice-spec-nachzug-produktschnitt.md) | Spezifikation auf den Produktschnitt nachziehen (`SPEC-052`, `SPEC-005`, `SPEC-010`, `SPEC-023`, `SPEC-035`, `SPEC-040`, `SPEC-059`), mit der Öffentlichkeit von `SimulationBackend` und `SimCatalog` | [TDS-RB-01](../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-FA-08](../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-FA-11](../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [ADR-0025](../adr/0025-produktschnitt-bibliothek-und-testserver.md), [ADR-0026](../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md) |
| [slice-testserver-herausloesen](done/slice-testserver-herausloesen.md) | Testserver im Source-Set `testserver` herauslösen, `simulationFile` und Jackson aus der Bibliothek nehmen, Test-Einstiege auf `TdsServer.start` umstellen | [TDS-RB-01](../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [ADR-0025](../adr/0025-produktschnitt-bibliothek-und-testserver.md) |
| [slice-jar-ohne-main-und-image](in-progress/slice-jar-ohne-main-und-image.md) | Bibliotheks-Jar ohne `Main-Class`, Stages `runtime` und `build` entfernt, Container-Lauf auf den Testserver umgestellt | [TDS-RB-01](../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [ADR-0025](../adr/0025-produktschnitt-bibliothek-und-testserver.md) |
| [slice-explicit-api-umbau](done/slice-explicit-api-umbau.md) | Öffentliche Fläche mit `explicitApi` festlegen und verkleinern (Namen nach [ADR-0021](../adr/0021-oeffentlicher-integrations-einstieg.md), [ADR-0026](../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md), [ADR-0020](../adr/0020-logformat-und-observability-schnittstelle.md)) | [TDS-FA-08](../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-RB-04](../../../spec/lastenheft.md#tds-rb-04--externes-backend), [ADR-0021](../adr/0021-oeffentlicher-integrations-einstieg.md), [ADR-0026](../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md) |

Reihenfolge: `slice-spec-nachzug-produktschnitt`, dann `slice-testserver-herausloesen`, dann `slice-jar-ohne-main-und-image` und `slice-explicit-api-umbau`; jede Kante ist ein Start-Trigger in §4 des Folge-Slice (der Umbau (b) setzt (a) voraus, [ADR-0025](../adr/0025-produktschnitt-bibliothek-und-testserver.md) Folgepflicht 2). `slice-explicit-api-umbau` wartet nur auf `slice-testserver-herausloesen`, damit er nicht zweimal umbaut: Lader, YAML-Leser, Beschreibungs-Leser und Jackson verlassen `src/main` vorher, und die Zahl der zu schließenden Deklarationen wird danach gemessen. Die beiden letzten Slices sind voneinander unabhängig (der eine berührt `Dockerfile`, Manifest und Skripte, der andere `src/main`; gemeinsam ist nur `build.gradle.kts`), ihre Reihenfolge ist keine Kante. Die Slices der Log-Schnittstelle liegen in [welle-betrieb-diagnose](welle-betrieb-diagnose.md) und hängen an `slice-testserver-herausloesen` (siehe §5), nicht umgekehrt.

**Register-Sichtung bei der Eröffnung** (`observations/BEO-TDS/`, durchgegangen am 2026-11-01). Unter der Schwelle von 3× oder verkörpert, ohne eigenen Slice: `BEO-TDS/jackson-abhaengigkeit-fuer-embedder-nicht-gemessen` (1×, Träger: Closure-Kriterium (2)), `BEO-TDS/spec-059-zusagen-vor-produktschnitt` (1×, Träger: `slice-spec-nachzug-produktschnitt`), `BEO-TDS/zwei-artefakt-stages-nebeneinander` (1×, entfällt mit `slice-jar-ohne-main-und-image`), `BEO-TDS/nachweis-von-der-falschen-seite-der-grenze` (2×) und `BEO-TDS/java-quelle-umgeht-kotlin-regel` (1×; Grenze des Nachweises des Testservers), `BEO-TDS/zahl-an-mehreren-orten-ohne-sensor` (1×; `kopplung-zahlen` zieht mit), `BEO-TDS/move-commit-laesst-detekt-rot` (1×; reine Move-Commits in `slice-testserver-herausloesen`). Über der Schwelle und berührt: `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` (3×, offen; Closure-Kriterium (4) und die DoD des Spec-Nachzugs nehmen sie auf, ein eigener Slice ist nicht nötig, weil die Suche über beide Orte Teil der DoD ist) und `BEO-TDS/dod-haengt-an-offenem-slice-ohne-rueckfuehrung` (3×, offen; die Rückführungen in §4 der Slices nennen die Kanten vorab).

**Fortschreibung der Sichtung (2026-11-01, nach [ADR-0020](../adr/0020-logformat-und-observability-schnittstelle.md), [ADR-0026](../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md), [ADR-0027](../adr/0027-abbruch-und-zuruecksetzen.md) und der Lockerung des Start-Triggers).** Register erneut durchgegangen. Neu berührt: `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` (10×, offen; die Auswahl „drei Wellen“ des früheren Triggers war eine solche Lesart des Planners und ist mit der Nutzerentscheidung vom 2026-11-01 hinfällig, die Zeile im Drift-Log der Roadmap hält den Grund), `BEO-TDS/dod-haengt-an-offenem-slice-ohne-rueckfuehrung` (3×, offen; die neuen Kanten zu `slice-betrieb-log-schnittstelle` und `slice-betrieb-log-adapter-testserver` stehen als Start-Trigger und Rückführung in deren §4), `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` (geplant bei `slice-qa-performance`; die Warteschlange des Adapters ist Setzung). Die Lockerung des Triggers lässt die Welle gleichzeitig mit [welle-fehler-abbruch](welle-fehler-abbruch.md) und den Abbruch-Slices nach [ADR-0027](../adr/0027-abbruch-und-zuruecksetzen.md) laufen; beide ändern `Bootstrap` und den Stop-Pfad. Das ist ein Risiko der Slices, kein Register-Fund; es steht in §6 von `slice-testserver-herausloesen`.

## 5. Abhängigkeiten

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Roadmap-Struktur: fünf Abschnitte.

- Blockiert: [welle-referenzabgleich-release](welle-referenzabgleich-release.md) nur im Wortlaut ihres Closure-Kriteriums („Läufe gegen das Laufzeit-Image aus `slice-release-artefakt-lizenz`“) und der Nutzer-Doku zum Start im Container: [ADR-0025](../adr/0025-produktschnitt-bibliothek-und-testserver.md) entfernt das Produkt-Image. Die Umschreibung jener Welle ist Teil von `slice-jar-ohne-main-und-image` (Nutzerentscheidung 2026-11-01: umschreiben erst dann) und nicht Inhalt dieser Welle. Ebenso blockiert diese Welle teilweise [welle-betrieb-diagnose](welle-betrieb-diagnose.md): die Slices `slice-betrieb-log-schnittstelle` und `slice-betrieb-log-adapter-testserver` starten erst, wenn `slice-testserver-herausloesen` in `done/` liegt (`ServerLog.STDOUT` und `main()` verlassen dort `src/main`; der Adapter braucht den Testserver).
- Wird blockiert von: [ADR-0025](../adr/0025-produktschnitt-bibliothek-und-testserver.md) `Accepted` (erfüllt) und der Nennung des Starts durch den Nutzer (§2). Entfallen sind die Kanten zu [welle-anfrageverarbeitung](welle-anfrageverarbeitung.md), [welle-fehler-abbruch](welle-fehler-abbruch.md) und [welle-betrieb-diagnose](welle-betrieb-diagnose.md) (Nutzerentscheidung 2026-11-01) und die Folge-ADR zu Offen-Punkt 1 der ADR ([ADR-0026](../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md) angenommen).
- Berührt, blockiert aber nicht: [welle-fehler-abbruch](welle-fehler-abbruch.md) und die Slices zu [ADR-0027](../adr/0027-abbruch-und-zuruecksetzen.md) (gleichzeitige Änderungen an `Bootstrap` und Stop-Pfad, Risiko in `slice-testserver-herausloesen`); [slice-release-doku-prozess](open/slice-release-doku-prozess.md) (Lizenzhinweise der Bibliotheken, Doku zum Start im Container).

## 6. Out-of-Scope für diese Welle

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Eröffnung Schritt 1 — Out-of-Scope gehört zur
Zielsetzung: Was nicht ausdrücklich ausgeschlossen ist, dehnt die Welle, bis
der Closure-Trigger unerreichbar wird.

- Das Log der Bibliothek (Modell, Senke, Parameter von `start`, Sammler, Ablösung von `ServerLog`) und der Log-Adapter des Testservers: entschieden in [ADR-0020](../adr/0020-logformat-und-observability-schnittstelle.md) (`Accepted`), gebaut in [welle-betrieb-diagnose](welle-betrieb-diagnose.md) ([slice-betrieb-log-schnittstelle](open/slice-betrieb-log-schnittstelle.md), [slice-betrieb-log-adapter-testserver](open/slice-betrieb-log-adapter-testserver.md)); diese Welle nimmt die betroffenen Skript-Prüfungen heraus oder kennzeichnet sie, sie ersetzt sie nicht. Die Spezifikation dazu (Abschnitte 4 und 5, `TDS-FA-09.a`) zieht [slice-spec-nachzug-telemetrie](done/slice-spec-nachzug-telemetrie.md) nach, ebenfalls in [welle-betrieb-diagnose](welle-betrieb-diagnose.md).
- Änderungen an der öffentlichen Fläche über die Namen hinaus, die [ADR-0021](../adr/0021-oeffentlicher-integrations-einstieg.md) Entscheidung 3 mit [ADR-0026](../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md) und [ADR-0020](../adr/0020-logformat-und-observability-schnittstelle.md) nennt: braucht der Testserver einen weiteren Typ, ist das ein Befund und eine Folge-ADR, kein `internal`-Öffnen. Die Namen der Senke (Arbeitsnamen der [ADR-0020](../adr/0020-logformat-und-observability-schnittstelle.md)) legt der Bau von `slice-betrieb-log-schnittstelle` fest, nicht diese Welle.
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
