# ADR-0026: Öffentlichkeit von `SimulationBackend` und `SimCatalog`

**Status:** Accepted (vom Nutzer entschieden)

**Datum:** 2026-11-01

**Autor:** pt9912

**Bezug:** [TDS-FA-08](../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-FA-11](../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [ADR-0006](0006-backend-vertrag-und-streaming.md), [ADR-0012](0012-simulations-backend-beschreibung-und-formen.md), [ADR-0019](0019-kompatibilitaetstests-zuschnitt.md), [ADR-0021](0021-oeffentlicher-integrations-einstieg.md), [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md)

**Schärft:** [SPEC-059](../../../spec/spezifikation.md#einstieg-des-integrators-vertrag-zu-spec-059) (Liste der öffentlichen Fläche und Zusage), [SPEC-035](../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) (Kotlin-Schnittstelle des Simulations-Backends). Betroffen ohne eigene Schärfung: `SPEC-040` (Läufe gegen den Testserver, Nachzug siehe Konsequenzen).

**Supersedes (teilweise), nur wörtlich benannt; die Datei des Vorgängers bleibt unverändert und `Accepted` ([AGENTS.md](../../../AGENTS.md) §3.5):** [ADR-0021](0021-oeffentlicher-integrations-einstieg.md) Entscheidung 3, **nur** (a) die Aufzählung der öffentlichen Fläche, soweit sie `SimulationBackend` und `SimCatalog` nicht nennt (die Liste gilt mit diesen beiden Namen zusätzlich) und (b) im Satz „Was heute öffentlich ist und nicht in dieser Liste steht (zum Beispiel `StatementPlanner`, `SimCatalog`, …) wird `internal`“ den Namen `SimCatalog` sowie die Folge für `SimulationBackend`, das in der Liste ebenfalls fehlte. Es bleiben: die Wahl von `explicitApi`, die Zusage bis `1.0` in ihrer Form, alle übrigen Namen der Liste, die Aussage, dass `StatementPlanner`, `SimDescriptionReader`, `Statement` und die `SimTree`-Typen `internal` werden, und die Entscheidungen 1, 2, 4, 5 und 6. [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md) wird nicht ersetzt: der Punkt „Öffentlichkeit von `SimulationBackend` und `SimCatalog`“ unter Offen ist keine Entscheidung; diese ADR schließt ihn.

---

## Kontext

**Nutzerentscheidung vom 2026-11-01:** Zum offenen Punkt 1 von [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md) wird Empfehlung (i) übernommen: `SimulationBackend` und `SimCatalog` werden Teil der öffentlichen Fläche der Bibliothek.

**Gelesen am 2026-11-01 im Quelltext und in der Spezifikation, nichts davon gemessen oder ausgeführt:**

- `SimulationBackend(catalog: SimCatalog) : QueryBackend` und `SimCatalog(tables: List<SimTable>)` (mit `tables` und `find`) tragen heute keinen Sichtbarkeitsmodifikator und sind damit öffentlich; sie stehen in `simulation/SimulationBackend.kt` und `simulation/SimModel.kt`. Verdrahtet werden sie in `runtime/SimulationSetup.kt`, also hinter `BackendSource.Simulation(tables)`.
- Der Aufrufer des Einstiegs `TdsServer.start(config, source)` braucht für `BackendSource.Simulation(tables)` nur `SimTable` samt `SimColumn`, `SimQuery`, `RowFilter`, `FilterTerm`, `RowSource` und `SqlType`; alle stehen in der Fläche von [ADR-0021](0021-oeffentlicher-integrations-einstieg.md) Entscheidung 3. Weder `SimulationBackend` noch `SimCatalog` sind dafür nötig.
- Der Aufzeichner des Kompatibilitätslaufs (`RecordingBackend` in `KompatibilitaetsServer.kt`) ist ein `QueryBackend`, das `execute` und `cancel` an ein inneres `QueryBackend` weiterreicht und je Anfrage eine Zeile aus Sitzungskennung, SQL-Text und Parametern schreibt; `main` baut `RecordingBackend(SimulationBackend(SimCatalog(KompatibilitaetsTables.all())))`. Er braucht `QueryBackend`, `QueryRequest` und `QueryExecution` (`port` und `domain`, in der Fläche von Entscheidung 3) und, um das innere Backend zu bauen, `SimulationBackend` und `SimCatalog`.
- Der Testserver bindet die Bibliothek nur über die öffentliche Fläche ([ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md) Kontext 4). Er reicht den Aufzeichner als `BackendSource.Custom(backend)` an den Einstieg; `Custom` zusammen mit gesetzter `simulationFile` bricht den Start ab, und die Datei liest nach [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md) ohnehin der Testserver selbst.
- Weder `spec/spezifikation.md` noch `spec/lastenheft.md` nennen `SimulationBackend`, `SimCatalog` oder den Aufzeichner (Textsuche). [SPEC-059](../../../spec/spezifikation.md#einstieg-des-integrators-vertrag-zu-spec-059) führt in der Zeile „Zusage“ als verbindlich: `TdsServer`, `BackendSource`, `StartOutcome`, `ConfigValidation`, `ConfigKeys` und die Typen ihrer Signaturen aus `port`, `domain`, der Zeilenquelle in `simulation` und `config`.

**Annahme, die kippt:** Dass der Aufzeichner im Testserver das ausgelieferte Simulations-Backend der Bibliothek umhüllen soll (so, wie [ADR-0012](0012-simulations-backend-beschreibung-und-formen.md) und [ADR-0019](0019-kompatibilitaetstests-zuschnitt.md) den Kompatibilitätslauf verstehen). Entfällt diese Anforderung, entfällt der Grund für die Öffentlichkeit.

## Entscheidung

Wir wählen **Option (i): `SimulationBackend` und `SimCatalog` sind öffentlich.**

1. **Minimalmenge neuer öffentlicher Namen: genau zwei.**
   - `SimulationBackend` mit dem Konstruktor `SimulationBackend(catalog: SimCatalog)` und der Schnittstelle `QueryBackend` (`execute`, `cancel`).
   - `SimCatalog` mit dem Konstruktor `SimCatalog(tables: List<SimTable>)` und der Eigenschaft `tables`.
   - `SimCatalog.find` wird `internal`, soweit der Planer im Hauptmodul der einzige Aufrufer bleibt (Setzung; die Umsetzung belegt es mit dem Übersetzungslauf). Wer Tabellen nachschlägt, hat sie in seiner eigenen Liste.
   - Alles Übrige in `simulation` bleibt oder wird `internal`: `StatementPlanner`, `Statement`, `SimDescriptionReader`, die `SimTree`-Typen, `SimColumnTypes`, `SimulationChanges`, `SimNames` und die Lexer-Typen. Der Testserver braucht keines davon aus der Bibliothek (der Leser der Beschreibung zieht nach [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md) in den Testserver um). Die Schnittstellen der beiden Namen verlangen keinen weiteren Typ, der nicht schon in Entscheidung 3 von [ADR-0021](0021-oeffentlicher-integrations-einstieg.md) steht (`SimTable`, `QueryBackend`).
2. **Der Aufzeichner ist über Port und `SimulationBackend` möglich** (gelesen, nicht übersetzt): ein `QueryBackend`, das ein inneres `QueryBackend` umhüllt, braucht nur öffentliche Namen; mit `SimulationBackend` und `SimCatalog` öffentlich liegt der gesamte Aufbau von `main` im Source-Set des Testservers, ohne `internal` und ohne `RequestSource`. Mehr als diese zwei Namen ist dafür nicht nötig.
3. **Stabilitätszusage, Form wie [ADR-0021](0021-oeffentlicher-integrations-einstieg.md) Entscheidung 3 (Setzung, nicht gemessen):** bis zur ersten Version `1.0` ändert sich die Signatur dieser zwei Namen (Konstruktoren, `tables`) nur über eine Änderung der Spezifikation und wird im Änderungsprotokoll genannt; ab `1.0` gilt semantische Versionierung. Das **Verhalten** des Backends (Anweisungsformen, Fehlercodes, Lebensdauer der Zeilenquelle) bleibt durch [SPEC-035](../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) und [ADR-0013](0013-backend-grenzen.md) festgelegt; die Zusage der Namen erweitert es nicht.
4. **Nachweis ohne `internal`:** Die Source-Sets `integrator` und `testserver` ([ADR-0021](0021-oeffentlicher-integrations-einstieg.md) Entscheidung 5, [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md)) sind nicht mit `main` assoziiert und übersetzen nur gegen die öffentliche Fläche. Der Testserver, der den Aufzeichner um `SimulationBackend(SimCatalog(…))` legt, ist der Nachweis, dass die zwei Namen genügen: ein weiterer nötiger Name wäre ein Befund über diese Entscheidung (Trigger), kein Anlass, `internal` zu öffnen.

## Verglichene Alternativen

| Option | Pro | Contra |
|---|---|---|
| (ii) — ohne Aufzeichner: `BackendSource.Simulation(tables)`, kein inneres Backend im Testserver; entspricht „nichts tun“ | Keine neue Zusage; Fläche bleibt wie in [ADR-0021](0021-oeffentlicher-integrations-einstieg.md) Entscheidung 3 | Der Kompatibilitätslauf verliert die Anfragetexte (SET-Texte von SchemaOnly und KeyInfo, Sitzungskennung der Marker-Anmeldung) im Backend-Log, deren Prüfung [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md) als „bleibt“ führt; die Prüfung entfiele oder wäre leer wahr |
| (iii) — eigenes `QueryBackend` im Testserver, das Anfragen aufzeichnet und selbst beantwortet | Keine neue öffentliche Fläche; Aufzeichnung bleibt | Der Lauf prüfte nicht mehr das Simulations-Backend der Bibliothek, sondern eine zweite Nachbildung; das berührt [ADR-0012](0012-simulations-backend-beschreibung-und-formen.md) und [ADR-0019](0019-kompatibilitaetstests-zuschnitt.md) (Kompatibilität des ausgelieferten Backends) und verdoppelt die Pflege der Anweisungsformen |
| **(i) — beide öffentlich (gewählt)** | Ein Aufzeichner um das ausgelieferte Backend ist über den Port möglich; derselbe Weg steht jedem Embedder offen (Backend wählen, umhüllen, als `Custom` übergeben); der Kompatibilitätslauf prüft das echte Backend; nur zwei Namen | Die Fläche wächst um zwei Namen und verpflichtet; `SimulationBackend` als `Custom` ist ein zweiter Weg neben `BackendSource.Simulation`, der `simulationFile` und die Namensprüfung des Einstiegs nicht durchläuft |

Ein Teil-Angebot, nur `SimulationBackend` zu öffnen, entfällt: dessen Konstruktor verlangt `SimCatalog`.

## Konsequenzen

- Positiv: Der Testserver baut den Kompatibilitätslauf ohne `internal` wie bisher; Embedder können das Simulations-Backend umhüllen (Aufzeichnung, Zählung, Fehlerinjektion) und über `BackendSource.Custom` übergeben.
- Negativ: Zwei Namen mehr unter der Zusage bis `1.0`. Wer `SimulationBackend` als `Custom` übergibt, umgeht Zusammenführung mit `simulationFile` und die Namensprüfung des Einstiegs; `SimCatalog` prüft Doppelungen selbst und wirft `IllegalArgumentException`. Das ist zu dokumentieren, nicht zu verhindern.
- **Folgepflicht Spezifikation (nach `Accepted`):** [SPEC-059](../../../spec/spezifikation.md#einstieg-des-integrators-vertrag-zu-spec-059) nennt in der Zeile „Zusage“ `SimulationBackend` und `SimCatalog` zusätzlich als verbindliche Namen mit der Signaturzusage nach Entscheidung 3; [SPEC-035](../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) beschreibt den Weg „Backend mit Katalog bauen und als `Custom` übergeben“ neben `Simulation`. `SPEC-040` nennt den Aufzeichner heute nicht (Textsuche); sie ändert sich nur, wenn der Nachzug zu [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md) die Läufe neu beschreibt.
- **Folgepflicht Umsetzung:** Die Umstellung auf `explicitApi` ([ADR-0021](0021-oeffentlicher-integrations-einstieg.md) Entscheidung 3) nimmt beide Namen mit ausdrücklicher Sichtbarkeit und KDoc in die öffentliche Liste auf; `SimCatalog.find` und die übrigen Typen aus Entscheidung 1 werden `internal`. Die Zahl der Diagnosen aus der Messung von [ADR-0021](0021-oeffentlicher-integrations-einstieg.md) ist damit nicht fortgeschrieben (nicht nachgemessen). Der Testserver-Umbau ([ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md)) verwendet den Aufbau aus Entscheidung 2.
- **Akzeptiertes Negativ (einmalig):** Ob sich der Aufzeichner gegen die öffentliche Fläche ohne weiteren Namen übersetzen lässt, ist gelesen und nicht übersetzt; der Übersetzungslauf des Umbaus belegt es, ein Befund dort folgt dem Trigger.

## Fitness Function (falls maschinell prüfbar)

| Tooling | Regel | Make-Target |
|---|---|---|
| Source-Set `testserver` ohne Assoziation zu `main` | der Testserver mit Aufzeichner um `SimulationBackend(SimCatalog(…))` übersetzt nur gegen die öffentliche Fläche; ein Zugriff auf `internal` bricht die Übersetzung (Kotlin-Quellen; `internal` ist im Bytecode öffentlich) | `make test` |
| Source-Set `integrator` | übersetzt ohne `internal`-Namen und startet den Server mit `BackendSource.Simulation` und `Custom` | `make test` |

## Re-Evaluierungs-Trigger

Der Übersetzungslauf des Testserver-Umbaus braucht über diese zwei Namen hinaus einen weiteren Typ aus `simulation` (dann Folge-ADR statt `internal` für den Testserver); der Kompatibilitätslauf verzichtet auf den Aufzeichner (dann entfällt der Grund, Öffentlichkeit prüfen); ein Embedder verlangt Zugriff auf die Planung der Anweisungen oder auf `SimCatalog.find` (dann Folge-ADR).

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-11-01 | Nutzerentscheidung: Empfehlung (i) zum offenen Punkt 1 von ADR-0025 übernommen | Sitzung |
| 2026-11-01 | Accepted – vom Nutzer entschieden | Sitzung |

Nach `Accepted` wird diese Datei **nicht mehr inhaltlich überschrieben**.
Spätere Korrekturen oder Schärfungen entstehen als neue ADR mit
`Supersedes ADR-NNNN` (Baseline-Regelwerk `modul-04-adrs.md`
§Hard Rule für Accepted-ADRs).
