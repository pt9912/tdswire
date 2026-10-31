# ADR-0021: Öffentlicher Integrations-Einstieg: Kotlin-Einstieg, Artefakt, Stabilitätszusage, Nachweis

**Status:** Proposed

**Datum:** 2026-10-31

**Autor:** pt9912

**Bezug:** [TDS-FA-08](../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-RB-04](../../../spec/lastenheft.md#tds-rb-04--externes-backend), [TDS-FA-11](../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-RB-01](../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [ADR-0006](0006-backend-vertrag-und-streaming.md), [ADR-0012](0012-simulations-backend-beschreibung-und-formen.md), [ADR-0013](0013-backend-grenzen.md), [ADR-0018](0018-laufzeit-artefakt.md)

**Schärft:** [SPEC-022](../../../spec/spezifikation.md#query-backend-vertrag-zu-spec-022) (Backend-Vertrag: wer ihn erreicht), [SPEC-035](../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) (Kotlin-Schnittstelle der Zeilenquelle: wie sie den Server erreicht), [SPEC-052](../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge) (Jar mit Startklasse: Verhältnis zum Einstieg), [ARC-008](../../../spec/architecture.md#1-komponenten-übersicht) (Composition Root). Kein angenommener ADR-Satz wird überstimmt; kein `Supersedes`. Die Spezifikation führt bisher keine Zeile für den Einstieg (siehe „Offen“).

---

## Kontext

[TDS-FA-08](../../../spec/lastenheft.md#tds-fa-08--backend-anbindung) verlangt eine „dokumentierte Integrationsschnittstelle für Abfrageausführung, Parameter, Resultsets, Fehler und Abbruchsignale“, [TDS-RB-04](../../../spec/lastenheft.md#tds-rb-04--externes-backend) ein austauschbares Backend ohne TDS-Paketkodierung (Nachweis: Integration mit einem In-Memory-Testbackend), [TDS-FA-11](../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) eine vom Integrator bereitgestellte Zeilenquelle, [TDS-RB-01](../../../spec/lastenheft.md#tds-rb-01--implementierungssprache) Betrieb auf der JVM.

**Ist-Zustand (gelesen aus dem Quelltext, nicht gemessen):**

- Der Vertrag ist öffentlich: `QueryBackend`, `QueryExecution` (Package `port`), die Modelle des Packages `domain`, die Typen der Zeilenquelle (`SimTable`, `SimColumn`, `SimQuery`, `RowFilter`, `FilterTerm`, `RowSource`) und `ServerConfig`/`ConfigLoader` (Package `config`).
- Der Weg vom Vertrag zum laufenden Server ist nicht öffentlich: `Bootstrap`, `Wiring`, `RequestSource`, `Server`, `BootResult` und `HandlerSetup` in `runtime` sind `internal`. `main()` (Startklasse des Jars, `SPEC-052`) startet nur ohne Backend oder mit der Simulation aus `SIMULATION_DESCRIPTION_FILE`.
- Folge: Ein Fremd-Integrator kann `QueryBackend` implementieren, aber den Server nicht damit starten; ebenso kann er `SimTable`-Werte mit Zeilenquelle bauen, aber nicht übergeben. Die Kotlin-Schnittstelle, die [ADR-0012](0012-simulations-backend-beschreibung-und-formen.md) Entscheidung 1 beibehält, ist nur für Tests im selben Modul erreichbar. Eine Integration mit dem In-Memory-Testbackend nach `TDS-RB-04` läuft heute über `internal`-Zugriff und belegt damit nicht, dass ein Außenstehender sie ausführen kann.
- Das Projekt ist ein einziges Gradle-Modul mit einem Jar (`tdswire.jar`, `Main-Class` gesetzt, Bibliotheken in `lib/`). Jackson (YAML) wird nur von der internen Datei-Leseschicht der Simulation benutzt; `kotlinx-coroutines` steckt im öffentlichen Vertrag (`suspend`, `Flow`). `explicitApi` ist nicht eingeschaltet.

Das Lastenheft verlangt keine Veröffentlichung in einem Artefakt-Repository; „dokumentiert“ und „austauschbar“ sind der Maßstab. Annahme: Integratoren sind Kotlin- oder Java-Projekte auf der JVM, die das Jar samt `lib/` auf ihren Klassenpfad nehmen. Kippt sie (Integration aus anderen Sprachen, Veröffentlichung über ein Repository), kippt Entscheidung 2.

## Entscheidung

Wir wählen **a1 (Fassade `TdsServer` in `runtime`), b1 (ein Artefakt), c1 (`explicitApi`, Zusage vor 1.0 eng), d1 (`main()` nutzt den Einstieg) und e1 (eigenes Source-Set ohne Zugriff auf `internal`)**.

1. **Kotlin-Einstieg (Option a1).** Eine öffentliche Fassade im Package `runtime` (Composition Root, `ARC-008`):
   - `TdsServer.start(config: ServerConfig, source: BackendSource, …): StartOutcome` als `suspend`-Funktion. `StartOutcome` ist ein versiegeltes Ergebnis (laufender Server oder Abbruch mit Grund), wie `BootResult` heute; der Start wirft keine Ausnahme für Konfigurations- oder Bindefehler (Festlegung im Stil von `QueryBackend.execute`).
   - Der laufende `TdsServer` bietet `port` (der gebundene Port) und `suspend stop()`. Lebenszyklus: `start` bis `stop`; `stop` ist idempotent (Setzung, vom Test zu belegen).
   - `BackendSource` ist ein versiegeltes Gebilde: `Custom(backend: QueryBackend)`, `Simulation(tables: List<SimTable>)` und `None`. Der heute geprüfte Konflikt (Backend zugleich mit Simulation bricht den Start ab) ist damit nicht darstellbar. Die Tabellen aus `ServerConfig.simulationFile` kommen bei `Simulation` und `None` dazu und bilden mit den übergebenen Tabellen einen Katalog ([ADR-0012](0012-simulations-backend-beschreibung-und-formen.md)); `Custom` zusammen mit gesetzter Datei bricht den Start ab (Verhalten wie heute).
   - `ServerConfig` entsteht über `ConfigLoader.load` (validiert) oder den öffentlichen Konstruktor. Ob der Konstruktor ohne `ConfigLoader` eine gleichwertige Prüfung braucht, ist offen.
   - Nicht öffentlich werden `Wiring` (Test-Naht für Log, Zeitgeber, Auth-Pool, Adapter) und `Bootstrap`; sie bleiben Implementierung der Fassade. Der Einstieg prüft das JVM-Flag `-Djdk.tls.server.newSessionTicketCount=0` wie der Prozess und setzt es nicht selbst; der Embedder setzt es (zu dokumentieren, [SPEC-023](../../../spec/spezifikation.md#6-externe-verträge)).
2. **Artefakt (Option b1).** Ein Gradle-Modul, ein Jar bleibt. Der Prozess ist die Startklasse im selben Jar, der Embedder nimmt Jar und `lib/` auf den Klassenpfad. Das Jar wird nicht in ein Repository veröffentlicht, solange das Lastenheft es nicht verlangt. Eine Bibliothek ohne `Main-Class` und ohne Jackson für Embedder entsteht nicht jetzt. Jackson und `kotlinx-coroutines` sind damit für den Embedder sichtbare Abhängigkeiten; dass Jackson nur für die Datei-Konfiguration gebraucht wird, ist gelesen, nicht durch einen Lauf ohne Jackson belegt.
3. **Stabilitätszusage (Option c1).** Der Compiler-Schalter `explicitApi` (Modus `strict`) wird eingeschaltet; die öffentliche Fläche ist ausdrücklich: `port`, `domain`, die Modell-Typen der Zeilenquelle aus `simulation` (`SimTable`, `SimColumn`, `SimQuery`, `RowFilter`, `FilterTerm`, `RowSource`) samt `SqlType`, die für `ServerConfig` nötigen Typen aus `config`, und `TdsServer`, `BackendSource`, `StartOutcome` in `runtime`. Was heute öffentlich ist und nicht in dieser Liste steht (zum Beispiel `StatementPlanner`, `SimCatalog`, `SimDescriptionReader`, `Statement`, die `SimTree`-Typen), wird `internal`, soweit Aufrufer im Hauptmodul es zulassen; die endgültige Liste legt die Umsetzung fest und belegt sie mit dem Übersetzungslauf. Zusage (Setzung, nicht gemessen): bis zur ersten Version `1.0` ändert sich die öffentliche Fläche nur über eine ADR oder eine Spezifikations-Änderung und wird dann im Änderungsprotokoll genannt; ab `1.0` gilt semantische Versionierung. Ein Werkzeug für Binärkompatibilität kommt nicht mit (siehe Alternativen und „Konsequenzen“).
4. **Grenze zum Prozess (Option d1).** `main()` bleibt der Prozess: Umgebung lesen, Shutdown-Hook, Warten bis zum Ende der JVM, Exit-Code bei Abbruch. Er startet über denselben Einstieg `TdsServer.start` mit `BackendSource.None`, nicht über einen zweiten Pfad. Die Startklasse im Manifest ([SPEC-052](../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge)) ändert sich nicht.
5. **Nachweis (Option e1).** Ein eigenes Gradle-Source-Set (Arbeitsname `integrator`) ohne Assoziation zum Hauptmodul: Kotlin gibt `internal` nur Quelltext desselben Moduls oder eines assoziierten Source-Sets frei; ein nicht assoziiertes Source-Set kann `internal`-Namen nicht übersetzen. Es enthält ein In-Memory-`QueryBackend`, startet den Server über `TdsServer.start`, spricht PRELOGIN, LOGIN7 und eine Anfrage mit einem minimalen Rohbytes-Client (nur `java.net`), liest Zeilen, Leermenge und Fehler und stoppt. Das Source-Set läuft in `make test` bzw. `make gates`. Damit ist `TDS-RB-04` ohne `internal`-Zugriff belegt; der .NET-Referenzclient ist dafür nicht nötig.
6. **Zeilenquelle des Integrators (f).** Der Einstieg ist der fehlende Teil von `TDS-FA-11`: `BackendSource.Simulation(tables)` übergibt die `SimTable`-Werte mit `rows`-Zeilenquelle und Callbacks, die [SPEC-035](../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) und [ADR-0013](0013-backend-grenzen.md) festlegen (suspendierend, `close()` genau einmal). Die Bedeutung der Typen ändert diese ADR nicht. Der Nachweis nach Punkt 5 deckt auch einen Lauf mit `Simulation` und einer Zeilenquelle des Integrators ab.

## Verglichene Alternativen

| Option | Pro | Contra |
|---|---|---|
| A0 — nichts tun | keine Änderung | `TDS-FA-08` („dokumentierte Integrationsschnittstelle“) und `TDS-RB-04` sind für Außenstehende nicht erfüllbar; die Kotlin-Schnittstelle aus [ADR-0012](0012-simulations-backend-beschreibung-und-formen.md) bleibt unerreichbar |
| **a1 — Fassade `TdsServer` mit `BackendSource` (gewählt)** | kleinste neue Fläche; `Wiring`/`Bootstrap` bleiben frei änderbar; Konflikt Backend gegen Simulation nicht darstellbar; ein Pfad für Prozess und Embedder | neue öffentliche Typen verpflichten; der Konstruktor von `ServerConfig` umgeht ggf. den `ConfigLoader` |
| a2 — `Bootstrap`/`Wiring` öffentlich machen | keine neue Fassade | zieht die Test-Naht (Zeitgeber, Auth-Pool) in die Zusage; jede interne Umstellung bricht Integratoren |
| a3 — Einstieg über `ServiceLoader` oder Klassenname in der Umgebung | kein Code beim Embedder | verlagert Typfehler auf die Laufzeit; Tabellen mit Callbacks passen nicht in eine Umgebungsvariable |
| **b1 — ein Jar für Prozess und Bibliothek (gewählt)** | nichts zu bauen und zu pflegen; Laufzeit-Image ([ADR-0018](0018-laufzeit-artefakt.md)) und Laufzeittest bleiben unberührt | `Main-Class` und Jackson liegen beim Embedder auf dem Klassenpfad; keine Repository-Veröffentlichung |
| b2 — getrennte Module (Kern ohne Jackson und `Main-Class`, Prozess daneben) | saubere Abhängigkeiten für Embedder | zwei Module, zweites Jar im Dockerfile, Datei-Lesen der Konfiguration muss aufgeteilt werden; Nutzen ohne Veröffentlichungswunsch nicht belegt |
| **c1 — `explicitApi` strict, Zusage vor 1.0 eng (gewählt)** | der Compiler erzwingt die Liste; keine neue Abhängigkeit im Build | einmaliger Umbau der heute öffentlichen Deklarationen; Verträglichkeit mit ktlint/detekt (`warningsAsErrors`) nicht gemessen |
| c2 — zusätzlich Prüfwerkzeug für Binärkompatibilität (Gradle-Plugin, API-Dump) | erkennt Brüche mechanisch | neues Plugin im Lock und in den Prüfsummen ([TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)), Pflege der Dump-Datei; vor einer ersten Veröffentlichung gibt es keine Version, gegen die zu prüfen wäre |
| c3 — keine Zusage, „public heißt nicht stabil“ | kein Aufwand | `TDS-FA-08` verlangt eine dokumentierte Schnittstelle; ohne Zusage ist sie keine |
| d2 — `main()` baut seinen Weg über `Wiring` weiter | Prozess unverändert | zwei Wege zum selben Server; der Test über den Einstieg belegt den Prozess nicht |
| **e1 — nicht assoziiertes Source-Set (gewählt)** | der Compiler erzwingt „nur öffentlich“; kein zusätzliches Werkzeug | eigener minimaler Rohbytes-Client im Source-Set (Aufwand nicht geschätzt); neues Source-Set in Build und detekt |
| e2 — Prüfung nur durch `explicitApi` | kein neuer Test | belegt nicht, dass die Fläche ausreicht, um einen Server zu starten und zu bedienen |
| e3 — Test im .NET-Referenzprojekt | echter Client | langsamer, Container-Abhängigkeit; `TDS-RB-04` verlangt nur ein In-Memory-Testbackend |

## Konsequenzen

- Positiv: `TDS-FA-08` und `TDS-RB-04` sind für Außenstehende erfüllbar und aus Sicht des Compilers belegbar; `TDS-FA-11` bekommt seinen Weg für Zeilenquellen des Integrators; Prozess und Embedder teilen einen Startpfad.
- Negativ: Die öffentliche Fläche wächst um drei Typen und verpflichtet; der Umbau auf `explicitApi` fasst viele Dateien an (Umfang nicht gemessen); Embedder erben Jackson und die Startklasse im selben Jar; das JVM-Flag zu [SPEC-023](../../../spec/spezifikation.md#6-externe-verträge) bindet auch Embedder.
- Folgepflicht (nach `Accepted`): Spezifikation: neue Festlegung für den Einstieg (Signatur, Lebenszyklus, öffentliche Fläche, Zusage); `SPEC-035` nennt den Einstieg als Weg der Kotlin-Schnittstelle; `ARC-008` nennt die Fassade ohne ADR-Bezug (`AGENTS.md` §3.4). Umsetzung: Fassade, `explicitApi`, Source-Set, `main()` auf den Einstieg. Dokumentation des Einstiegs für Integratoren (KDoc und ein Beispiel; Träger entscheidet die Planung).
- **Akzeptiertes Negativ (einmalig):** Ein Prüfwerkzeug für Binärkompatibilität kommt nicht mit; vor einer ersten Veröffentlichung gibt es keine Version, gegen die zu prüfen wäre. Der Grund steht in Option c2 und im Trigger.

## Fitness Function (falls maschinell prüfbar)

| Tooling | Regel | Make-Target |
|---|---|---|
| Kotlin-Compiler (`explicitApi` strict) | jede öffentliche Deklaration trägt `public` ausdrücklich; die Übersetzung bricht sonst | `make build` |
| Gradle-Source-Set `integrator` (nicht assoziiert) | Test startet den Server mit In-Memory-Backend und mit `BackendSource.Simulation` nur über öffentliche Namen; ein Zugriff auf `internal` bricht die Übersetzung | `make test` (und damit `make gates`) |

Beide Zeilen sind Absicht, nicht Bestand: ob `explicitApi` mit `make detekt` (ktlint, `warningsAsErrors`) verträglich ist und ob das Source-Set in der Stage `test` läuft, belegt die Umsetzung.

## Offen

- Gleichwertige Prüfung beim öffentlichen Konstruktor von `ServerConfig` (heute validiert nur `ConfigLoader.load`): Prüfung im Einstieg, nicht öffentlicher Konstruktor mit Fabrik oder Dokumentationspflicht. Keine Empfehlung ohne Sichtung der Prüfungen in `ConfigLoader`.
- Ob die Fläche in `domain` und `config` vollständig und minimal ist (zum Beispiel `Principal`, `AuthSettings`, `UserList`): Aufgabe der Umsetzung mit dem Übersetzungslauf; die Liste in Entscheidung 3 ist die Absicht, nicht ein Bestand.
- Lebensdauerregeln und Threading des Einstiegs (Aufruf aus mehreren Threads, `stop` während `start`): nicht festgelegt, vom Test zu belegen.
- Java-Aufrufer: `suspend`-Funktionen sind aus Java umständlich; ein Java-freundlicher Einstieg ist nicht entschieden. Das Lastenheft verlangt Kotlin-basierte Entwicklung ([TDS-RB-01](../../../spec/lastenheft.md#tds-rb-01--implementierungssprache)), keine Java-Schnittstelle.
- Dokumentationsform der Schnittstelle (KDoc, Nutzer-Doku, Beispielprojekt): Entscheidung der Planung.

## Re-Evaluierungs-Trigger

Der Wunsch, das Jar in ein Artefakt-Repository zu veröffentlichen (dann b2 und c2 neu); ein Konflikt des Embedders mit der Jackson-Version des Jars; die erste Version `1.0`; ein Embedder, der aus Java oder einer anderen JVM-Sprache einsteigt; ein Bruch der öffentlichen Fläche, den ein Review findet, ohne dass ein Gate ihn anzeigte.

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-10-31 | Proposed – Folge des Reviews zur Änderungs-Weitergabe des Simulations-Backends und der Frage des Implementers, wie ein Fremd-Integrator Backend oder Zeilenquellen einbettet; Status bleibt Proposed bis Review und Annahme durch den Nutzer | Review 2026-10-31 zur Simulations-Änderung |

Nach `Accepted` wird diese Datei **nicht mehr inhaltlich überschrieben**.
Spätere Korrekturen oder Schärfungen entstehen als neue ADR mit
`Supersedes ADR-NNNN` (Baseline-Regelwerk `modul-04-adrs.md`
§Hard Rule für Accepted-ADRs).
