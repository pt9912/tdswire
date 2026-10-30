# Welle welle-simulations-backend: Mitgeliefertes Simulations-Backend: Beschreibung, Erkennung, Auswahl mit Streaming und Änderungsweitergabe

**Lifecycle:** Diese Datei entsteht bei der **Eröffnung** der Welle und liegt
flach unter `docs/plan/planning/`; bei Closure wandert sie per `git mv` nach
`done/` (neben ihre `welle-<Kennung>-results.md`). Der Zustand ist die
Verzeichnis-Position — kein Status-Feld. **Geplante Wellen bekommen noch keine
Datei:** Sie stehen in der Roadmap unter *Nächste Wellen* und nirgends sonst —
zwei Positionen, nicht drei.

**Zielmeilenstein:** kein Meilenstein-Bezug.

**Verantwortlich:** pt9912. **Datum:** 2026-10-30.

---

## 1. Welle-Ziel

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wann Arbeit eine Welle braucht.

Ein Integrator beschreibt Tabellen (Spalten, Typen) und liefert eine Zeilenquelle; der Server beantwortet daraus die erkannten Anweisungsformen von SPEC-036 gegenüber dem Referenzclient, ohne SQL auszuwerten und ohne Daten zu speichern, und überträgt eine Zeilenquelle oberhalb der Speichergrenze schrittweise ([TDS-FA-11](../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), Backend-Vertrag [TDS-FA-08](../../../spec/lastenheft.md#tds-fa-08--backend-anbindung)). Der Protokollkern läuft unverändert ohne das Backend. Die Aussage steht am Backend gegen die Pflichten des Ports und am Referenzclient im Integrationstest (SPEC-040), nicht am Vergleich mit einem echten SQL Server ([slice-referenzabgleich-live](open/slice-referenzabgleich-live.md)).

**Grenze zum Fake im Test.** Der Fake (`ScriptedBackend` im Test-Quelltext, [slice-backend-port-vertrag](done/slice-backend-port-vertrag.md)) spielt ein Skript von Ereignissen ab und erkennt keinen Text; das Simulations-Backend liegt im Package `simulation` des Produktivcodes, ordnet Anweisungstext Formen zu und liest Zeilen aus einer Zeilenquelle. Beide implementieren denselben Port und erfüllen dieselben Port-Pflichten ([slice-sim-auswahl-streaming](open/slice-sim-auswahl-streaming.md)); der Fake lernt keine Erkennung, das Simulations-Backend kein Skript. Die Nachweise der Welle [welle-backend-streaming](welle-backend-streaming.md) bleiben Aussagen über den Fake und werden hier nicht umgedeutet.

**Register-Sichtung (Eröffnung, Schritt 2).** `observations/` durchgegangen: Treffer führen die Slices in §8. Keine Beobachtung erreicht mit dieser Welle 3×; `BEO-TDS/spec-einzelfaelle-ohne-lastenheft-deckung` (2×) und `BEO-TDS/offene-punkte-ohne-traeger` (6×, verkörpert) sind im Entscheidungs-Slice als Risiko geführt.

## 2. Trigger (Welle startet)

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Roadmap-Regeln — ein Trigger ist **beobachtbar** dann, wenn ein *anderer*
Mensch ohne Rückfrage sagen kann, ob er eingetreten ist; ein Datum darf erwähnt
werden, aber nie Trigger sein. Und der **Start**-Trigger ist **kein Ergebnis
dieser Welle**: Steht er in der Slice-Liste unten, ist er falsch platziert.

- `make gates` ist auf `main` grün.
- Die Welle [welle-backend-streaming](welle-backend-streaming.md) liegt in `done/`: Port, Typabbildung, Sequenzierer und der Streaming-Nachweis am Fake stehen auf `main`, `QueryBackendContractTest` ist als Port-Test vorhanden.
- Weitere Voraussetzungen gelten je Slice und stehen in dessen §4 (zum Beispiel [slice-adr-backend-grenzen](open/slice-adr-backend-grenzen.md) für die Zeilendarstellung und der startende Serverprozess für den Integrationstest): sie verzögern einen Slice, nicht den Start der Welle.

## 3. Closure-Trigger (Welle schließt)

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wann Arbeit eine Welle braucht — der Trigger muss das *Mehr* gegenüber den
einzelnen Slice-DoDs benennen; kann er das nicht, liegt keine Welle vor.

- Alle Slices in `done/`.
- `make gates` grün, einschließlich des Test-Gates nach SPEC-040, oder die Closure-Notiz begründet die Nicht-Aufnahme in `make gates` mit der gemessenen Laufzeit.
- Das Mehr gegenüber den Slice-DoDs: Ein Lauf des Referenzclients gegen den Serverprozess mit Simulations-Backend liest eine Zeilenquelle, die mehr Zeilen erzeugt, als in den gesetzten Heap passen, vollständig bei eingehaltener Heap-Grenze, und derselbe Server startet und beantwortet den Login ohne das Simulations-Backend. Einzelne Slices belegen nur ihren Teil.
- Die Closure-Notiz führt die Aussagen der Welle in zwei Spalten, *gemessen* und *Festlegung*, und ordnet jeden offenen Punkt der ADR aus [slice-adr-simulation-formen](in-progress/slice-adr-simulation-formen.md) einem Träger zu; die Nutzerentscheidungen dieser Welle (Beschreibungsform, Schlüssel, Form C, Verdrahtung) sind getroffen und in den Slice-Notizen festgehalten.
- Closure-Notiz in `welle-simulations-backend-results.md`.

## 4. Slices in dieser Welle

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Lifecycle als State Machine — der Zustand eines Slice ist sein
Lifecycle-Verzeichnis und wird hier **nicht** gespiegelt.

| Slice | Titel | Bezug |
|---|---|---|
| slice-adr-simulation-formen | Entscheidung: erkannte Anweisungsformen, Typen und Beschreibungsform; neue ADR, Fortschreibung SPEC-035 und SPEC-036 | [TDS-FA-11](../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [ADR-0006](../adr/0006-backend-vertrag-und-streaming.md), [ADR-0008](../adr/0008-dotnet-kompatibilitaetstests.md) |
| slice-sim-erkennung | Tabellenbeschreibung, Zeichenscanner und Zuordnung der Formen S1, W1 bis W3, Objektprüfung vor dem Lesen | [TDS-FA-11](../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-08](../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-QA-05](../../../spec/lastenheft.md#tds-qa-05--wartbarkeit) |
| slice-sim-auswahl-streaming | Auswahl mit Bedingung und Parametern, schrittweises Lesen der Zeilenquelle, Port-Pflichten, Heap-Nachweis | [TDS-FA-11](../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-08](../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-QA-02](../../../spec/lastenheft.md#tds-qa-02--stabilität), [ADR-0006](../adr/0006-backend-vertrag-und-streaming.md) |
| slice-sim-aenderung-integration | Änderungsweitergabe an Callbacks, Verdrahtung in `runtime`, Integrationstest mit dem Referenzclient (SPEC-040) | [TDS-FA-11](../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-08](../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-QA-05](../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [ADR-0008](../adr/0008-dotnet-kompatibilitaetstests.md) |

Reihenfolge: Die Entscheidung steht vor allem (ohne sie ist [TDS-FA-11](../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) nach dem Lastenheft nicht abnehmbar und die Beschreibungsform offen). Die Erkennung braucht keine Zeilenquelle und steht vor der Auswahl. Die Auswahl braucht die Zeilendarstellung aus [slice-adr-backend-grenzen](open/slice-adr-backend-grenzen.md). Der Integrationstest schließt die Welle, weil er Erkennung, Auswahl, Callbacks und den startenden Serverprozess braucht.

## 5. Abhängigkeiten

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Roadmap-Struktur: fünf Abschnitte.

- Blockiert: [welle-referenzabgleich-release](welle-referenzabgleich-release.md) ([slice-referenzabgleich-live](open/slice-referenzabgleich-live.md) braucht die Server-Seite mit Simulations-Backend, SPEC-045). Berührt: [slice-qa-kompatibilitaet](open/slice-qa-kompatibilitaet.md), falls der Nutzer deren Szenarien auf das Simulations-Backend legt (Frage 4 in §6 von [slice-sim-aenderung-integration](open/slice-sim-aenderung-integration.md)); [slice-qa-performance](open/slice-qa-performance.md) (Referenz-Workload mit getrennter Messung von Protokollschicht und Backend kann das Simulations-Backend als Backend nutzen).
- Wird blockiert von: [welle-backend-streaming](welle-backend-streaming.md) (Port, Typabbildung, Sequenzierer; Start-Trigger); [slice-adr-backend-grenzen](open/slice-adr-backend-grenzen.md) (ohne Welle; Zeilendarstellung, nur [slice-sim-auswahl-streaming](open/slice-sim-auswahl-streaming.md)); [welle-handshake-login](welle-handshake-login.md) und [welle-anfrageverarbeitung](welle-anfrageverarbeitung.md) (der Serverprozess beantwortet Login und SQL_BATCH; nur [slice-sim-aenderung-integration](open/slice-sim-aenderung-integration.md)).
- Berührt, blockiert aber nicht: [welle-fehler-abbruch](welle-fehler-abbruch.md). Die Codes `TDS_UNSUPPORTED_SQL`, `TDS_SIM_UNKNOWN_OBJECT` und `TDS_SIM_INVALID_VALUE` ordnet [slice-fehler-mapping](open/slice-fehler-mapping.md) einer Nummer und Class zu; das Simulations-Backend liefert nur den Code im `Failure`. Bis dahin gilt für jeden eigenen Code ohne Gruppe „im Zweifel schließen“ ([TDS-FA-06.a](../../../spec/spezifikation.md#tds-fa-06a--fehlerabbildung)).
- Berührt, blockiert aber nicht: [welle-qualitaet-grundlage](welle-qualitaet-grundlage.md). Die Kompatibilitätsmatrix ([slice-qa-wartbarkeit](in-progress/slice-qa-wartbarkeit.md)) führt die unterstützten Nachrichten und Datentypen des Servers; das Simulations-Backend erweitert den Typkatalog des Ports nicht.

## 6. Out-of-Scope für diese Welle

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Eröffnung Schritt 1 — Out-of-Scope gehört zur
Zielsetzung: Was nicht ausdrücklich ausgeschlossen ist, dehnt die Welle, bis
der Closure-Trigger unerreichbar wird.

- Auswertung beliebigen SQLs (Verbunde, Aggregate, Ausdrücke, Unterabfragen, Sortierung), Persistenz und Transaktionssemantik über die Weitergabe hinaus: nach [TDS-FA-11](../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) ausgeschlossen. Eine Anforderung dafür wäre eine Änderung des Lastenhefts.
- Vergleich des Simulations-Backends mit einer SQL-Server-Referenzinstanz (Live-Lauf, Mitschnitt-Lauf): [slice-referenzabgleich-live](open/slice-referenzabgleich-live.md) und [slice-referenzabgleich-mitschnitt](open/slice-referenzabgleich-mitschnitt.md) in [welle-referenzabgleich-release](welle-referenzabgleich-release.md).
- Fehlernummer, Severity und Verbindungsfolge der Simulations-Codes: [slice-fehler-mapping](open/slice-fehler-mapping.md) ([welle-fehler-abbruch](welle-fehler-abbruch.md)).
- Füllung der Form C mit den Texten, die der Referenzclient stellt: erst nach Freigabe im Clientprofil (SPEC-004) und der Messung in [slice-qa-kompatibilitaet](open/slice-qa-kompatibilitaet.md).
- Kompatibilitäts-Szenarien S1 bis S18: [slice-qa-kompatibilitaet](open/slice-qa-kompatibilitaet.md); Last und Durchsatz: [slice-qa-performance](open/slice-qa-performance.md).
- Blockierende Zeilenquellen, Frist `deadlineEpochMillis`, Zeilendarstellung: [slice-adr-backend-grenzen](open/slice-adr-backend-grenzen.md) (ohne Welle).
- Logging und Metriken des Simulations-Backends: [welle-betrieb-diagnose](welle-betrieb-diagnose.md); das Backend ruft keine Observability (Importregel `ForbiddenImport/simulation`).
- Neue Anforderungen oder Änderungen am Lastenheft.

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-traceability.md`
§Herkunfts-Anker für Steering-Loop-Regeln — dort die **Ruheort-Regel**: Die
beiden Zeiger unten sind so zu schreiben, wie sie vom Ruheort `done/` auflösen,
nicht vom Schreibort.

Ergebnis: `welle-simulations-backend-results.md`
Zähler: `../observations/` <!-- d-check:ignore (Ruheort done/: der Zeiger löst erst nach dem Verschieben auf) -->
