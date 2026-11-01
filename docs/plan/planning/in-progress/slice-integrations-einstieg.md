# Slice slice-integrations-einstieg: Öffentlicher Einstieg zum Einbetten des Servers mit eigenem Backend und eigenen Zeilenquellen

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-anfrageverarbeitung

**Bezug:** [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-RB-04](../../../../spec/lastenheft.md#tds-rb-04--externes-backend), [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) (Zeilenquelle des Integrators); [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) (öffentlicher Integrations-Einstieg, `Accepted`), [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md), [ADR-0013](../../adr/0013-backend-grenzen.md).

**Berührte Spec-Stellen:** [TDS-FA-08.a](../../../../spec/spezifikation.md#tds-fa-08a--backend-vertrag) · SPEC-002 · SPEC-035 · ARC-006 · ARC-008 · ARC-016

**Verantwortlich:** pt9912.

**Autor:** pt9912. **Datum:** 2026-10-31.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Ein Integrator kann den Server mit einem eigenen `QueryBackend` und eigenen Zeilenquellen aus seinem Programm starten und beenden, ohne dass er `internal`-Typen des Moduls berührt; der Nachweis läuft ausschließlich über die öffentliche API gegen ein In-Memory-Backend ([TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-RB-04](../../../../spec/lastenheft.md#tds-rb-04--externes-backend), [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend)).

**Befund, der den Slice begründet** (Stand des Repos am Tag der Anlage, nicht an einem Integrator gemessen): `QueryBackend` (Paket `port`) ist `public`; `Bootstrap`, die Verdrahtung, `RequestSource` und `HandlerSetup` im Paket `runtime` sind `internal`; `main()` startet den Server nur ohne Backend oder mit `SIMULATION_DESCRIPTION_FILE`. Ein Weg, auf dem ein Integrator ein eigenes Backend übergibt, ist nicht öffentlich; kein Slice trägt ihn. Der Befund fiel in der Übergabe des Implementers von `slice-sim-aenderung-integration` auf (Review `2026-10-31-a7e6afa-slice-sim-aenderung-integration`, mit der Archivierung von welle-simulations-backend aus `docs/reviews/` entfernt, im Archiv der Welle), nicht in einem Test.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Die Entscheidung über Form, Zuschnitt und Stabilitätszusage des Einstiegs — sie ist [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) (`Accepted`); der Slice setzt sie um und entscheidet sie nicht. Die zwei Punkte, die die ADR unter „Offen“ der Umsetzungsplanung überlässt (Form der Prüfung, Inhalt von `StartOutcome.Aborted`), schließt §3 per Setzung.
- Der Umbau auf `explicitApi` (Modus `strict`) und die Verkleinerung der öffentlichen Fläche nach Entscheidung 3 der ADR — die ADR misst 738 Diagnosen in 76 von 136 Dateien und nennt den Umbau als Anlass zur Zerlegung; er ist ein eigener Slice (`slice-explicit-api-umbau`, `open/`). Dieser Slice liefert den Einstieg und belegt von außen, dass die Fläche zum Starten und Bedienen reicht; die Zusage „nur diese Fläche ist öffentlich“ trägt erst der Folge-Slice.
- Änderungen am Backend-Port (`QueryBackend`, `QueryEvent`, `QueryRequest`) — der Port-Vertrag ist [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md); eine Änderung wäre eine Folge-ADR.
- Das Simulations-Backend und seine Beschreibung — `welle-simulations-backend`; der Einstieg nimmt eine Zeilenquelle des Integrators entgegen, er baut sie nicht.
- Ein veröffentlichtes Artefakt in einem Paket-Repository und eine Versionierungs-Zusage — das Release-Artefakt ist `slice-release-artefakt-lizenz` (geschlossen), die Prozess-Doku `slice-release-doku-prozess`; dieser Slice liefert die Schnittstelle im Quelltext, keine Veröffentlichung.
- Das Trennen in ein eigenes Gradle-Modul, falls die ADR es nicht verlangt — Modulgrenzen sind der Gegenstand von `slice-qa-wartbarkeit`; ob der Nachweis nur außerhalb des Moduls läuft, entscheidet die ADR (§6).
- Backend-Betrieb (Verbindungspools, Authentifizierung gegen ein echtes Backend) — kein Träger im Repo; der Einstieg übergibt, er betreibt nicht.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Wortlaut und Zuschnitt folgen [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) (beim Übergang `open` → `next` gegen sie geprüft); `explicitApi` ist nicht Teil dieses Slice (§1).

Liefer-Punkte (drei):

- [x] **Öffentlicher Einstieg und Schnitt der Konfiguration** ([TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-RB-04](../../../../spec/lastenheft.md#tds-rb-04--externes-backend), [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose)): `TdsServer.start(config, source)` mit `BackendSource` und `StartOutcome` sind `public` in `runtime`; `Wiring`, `Bootstrap` und der Lesepfad bleiben `internal`. Die Prüfung der Konfiguration und die Defaults stehen in `config` und gelten für Lader und Embedder (`ConfigValidation.check`); `ConfigLoader` und `ConfigFiles` liegen als `internal` in `runtime`; `main()` startet über `Bootstrap.start`, das `launch` ruft, den auch `TdsServer.start` ruft (Zwischenstand).
- [x] **Lebenszyklus** (start/stop): Der Einstieg startet den Server und gibt dem Integrator ein Handle (`port`, `stop()`); ein zweites `stop` und ein Start mit ungültiger Konfiguration (`REQUIRED` ohne TLS-Material, `TEST_UNENCRYPTED` mit TLS-Material, Wert außerhalb des Bereichs) enden definiert (`StartOutcome.Aborted`, keine Ausnahme), ohne offene Ressourcen (Test).
- [x] **Nachweis ohne `internal`-Zugriff** ([TDS-RB-04](../../../../spec/lastenheft.md#tds-rb-04--externes-backend)): ein Test im eigenen, nicht assoziierten Source-Set `integrator` startet den Server über den Einstieg mit einem In-Memory-Backend und mit `BackendSource.Simulation` und einer Zeilenquelle des Integrators und beantwortet mit Rohbytes eine Anfrage; er läuft in `make test`; eine Mutation, die den Einstieg `internal` macht, macht die Übersetzung rot (Beleg in der Closure-Notiz).

Nachweis-Pflichten für alle Punkte:

- [x] Doku: KDoc der öffentlichen Typen (Zusage, Lebenszyklus, Threading, Einmal-Konfiguration, Zeilenquelle schließt mit der Anfrage); die Festlegung des Einstiegs in `spec/spezifikation.md` (Abschnitt Externe Verträge, neue `SPEC-059`), `SPEC-035` nennt den Einstieg als Weg der Kotlin-Schnittstelle, `ARC-008` in `spec/architecture.md` nennt die Fassade (ohne ADR-Bezug); Aussagen stehen an einem Test oder sind als Festlegung gekennzeichnet (`AGENTS.md` §3.8).
- [x] Jeder Test trägt im KDoc den Block-Tag `@spec SPEC-<NNN>`; die Tests laufen in `make test` und sind grün.
- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo mit Wellen von der Welle-Closure geprüft.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `config/ConfigValidation.kt` | neu | `ConfigValidation.check(ServerConfig)`: Wertebereiche, Paarung Verschlüsselung und Schlüsselmaterial, 1-MiB-Grenze von `simulationFile` ([SPEC-035](../../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends)); dieselben Teilprüfungen ruft der Lader je Schlüssel, damit mehrere Fehler zusammen gemeldet bleiben |
| `config/ServerConfig.kt` | update | Defaults an den Typen, Kommentar zur Entstehung korrigiert |
| `config/ConfigLoader.kt`, `config/ConfigFiles.kt` und ihre Tests | Verschieben, dann update | reiner Move-Commit nach `runtime` (`AGENTS.md` §3.3), danach Paket und `internal`; Bereichstests werden Tests der Prüfung in `config` |
| `runtime/Bootstrap.kt` | update | `start` (Flag, Lader, Abbruch-Log) und `launch` (Prüfung, Simulation, TLS, Binden) |
| `runtime/Server.kt` nach `runtime/TdsServer.kt` | Verschieben, dann update | öffentlich: `TdsServer` (`port`, `stop`), `BackendSource`, `StartOutcome`, `TdsServer.start` |
| `runtime/Main.kt` | update | `main()` → `Bootstrap.start` → `launch`, den auch `TdsServer.start` ruft (Zwischenstand) |
| `build.gradle.kts`, `Dockerfile`, `tools/mk/build.mk` | update | Source-Set `integrator` (nicht assoziiert) und Task `integratorTest`; Stages `test`, `spec-abdeckung` und `detekt`; `DEPS_TASKS`; Lock |
| `src/integrator/kotlin/` | neu | Einbettungstest nur über die öffentliche API (In-Memory-Backend, `Simulation` mit Zeilenquelle, Abbruch bei ungültiger Konfiguration, doppeltes `stop`); `@spec`-Tag je Test |
| `spec/spezifikation.md`, `spec/architecture.md` | update | `SPEC-059` (Einstieg), `SPEC-035` und `ARC-008` nennen ihn |

Setzungen (Voreinstellung, nicht gemessen), die [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) der Umsetzung überlässt:

- **Form der Prüfung:** (a), `ConfigValidation.check(ServerConfig): List<ConfigIssue>`; `TdsServer.start` ruft sie, der Lader ruft dieselben Teilprüfungen.
- **`StartOutcome.Aborted`** trägt `reason` (Text ohne Werte, derselbe, den der Prozess ins Log schreibt) und `issues` (Liste der `ConfigIssue`, leer, wenn der Abbruch nicht aus der Konfiguration kam).
- **Log des Einstiegs:** `TdsServer.start` schreibt kein Log (Voreinstellung `ServerLog.NONE`); der Grund steht in `Aborted`.
- **`explicitApi`:** ausgegliedert (§1).
- **Fläche:** `ConfigValidation` und `ConfigKeys` gehören zur zugesagten Fläche (Setzung, Fix-Runde nach Review; die ADR bleibt unverändert).
- **Passwort:** gelöscht wird beim Laden des TLS-Materials, nicht bei jedem Abbruch (Setzung, getestet in `TdsServerLifecycleTest`).
- **Sensor Assoziation:** die Gradle-Task `integratorOhneAssoziation` bricht, sobald das Source-Set `integrator` mit einer anderen Kompilierung assoziiert ist; `compileIntegratorKotlin` und `integratorTest` hängen an ihr.

Reihenfolge: (a) Prüfung und Defaults nach `config`, (b) reiner Move von Lader und Dateileser mit Tests nach `runtime`, danach Inhalt, (c) `Bootstrap` teilen und Einstieg, dann Source-Set und Nachweis; die Mutation (Einstieg `internal`) vor der Closure.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) steht im ADR-Index auf `Accepted`, `slice-sim-aenderung-integration` liegt in `done/` (im Archiv von `welle-simulations-backend`; die Verdrahtung der Zeilenquelle, die der Einstieg übergibt), und ein Verantwortlicher ist im Kopf eingetragen. Alle drei Bedingungen sind am 2026-10-31 erfüllt.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2, etwa weil die ADR ein eigenes Modul verlangt.
- `in-progress` → `open` (blockiert — Carveout?): die ADR wird `Superseded` oder ihre Form ändert sich während der Umsetzung.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün, der Review-Report liegt vor, der Einbettungstest läuft ohne Zugriff auf `internal`-Typen und die Mutation „Einstieg `internal`“ macht ihn rot, und die Closure-Notiz trägt den Steering-Loop-Lerneintrag (geschärfte Regel, neuer Sensor oder benannte Spec-Lücke). Gemessen ist nur, was der Test gegen ein In-Memory-Backend belegt; ein echtes Backend und der Referenzclient sind nicht Gegenstand.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Stabilitätszusage: Was `public` wird, ist eine Zusage an Dritte; welche Typen dazugehören, wie lange sie halten und wie Änderungen angekündigt werden, ist nicht entschieden. Die Entscheidung ist die ADR; ohne sie liefert der Slice nichts. — **Ausgang:** entfallen. [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) ist `Accepted`, `SPEC-059` legt die Fläche fest (Setzung bis `1.0`). Gemessen ist nur, dass die Fläche übersetzt und vom Source-Set `integrator` genutzt wird, nicht, dass eine Zusage über Zeit hält.
- Jackson-Abhängigkeit für Embedder: Die YAML-Bibliothek (`libs.jackson.dataformat.yaml`) hängt als `implementation` am Projekt; ob ein Embedder sie auf den Klassenpfad bekommt oder von ihr frei bleiben muss, ist nicht gemessen. Der Einstieg darf keinen Jackson-Typ in seiner öffentlichen Signatur tragen. — **Ausgang:** weiter offen. Das Source-Set `integrator` nimmt Jackson nur als `runtimeOnly`; ein Lauf ohne Jackson ist nicht gemessen, die Signatur trägt keinen Jackson-Typ (gelesen). Träger: [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) (`Proposed`) nimmt `simulationFile` und Jackson aus der Bibliothek; Register `BEO-TDS/jackson-abhaengigkeit-fuer-embedder-nicht-gemessen`.
- Verhältnis zu `Main.kt`: zwei Wege zum Start (Prozess und Einbettung) können auseinanderlaufen; die Konfigurationsprüfung (Konflikt Backend und Simulation, Flags, Port) soll einmal stehen. Ob `main()` den öffentlichen Einstieg ruft, entscheidet die ADR. — **Ausgang:** eingetreten, entschärft. `main()` ruft `Bootstrap.start`, das `launch` ruft, den auch `TdsServer.start` ruft; die Prüfung steht einmal (`ConfigValidation.check`), die Mutation m1 (Prüfung aus `launch` entfernt) macht `EmbeddingTest` rot. Der Prozessweg bleibt Zwischenstand: [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) (`Proposed`) verlagert `main()` in den Testserver.
- Nachweis von der falschen Seite der Grenze: Tests im Modul sehen `internal`; ein Test, der den Einstieg von innen ruft, bestünde auch bei fehlender Öffentlichkeit. Der Test muss dort laufen, wo `internal` nicht sichtbar ist, und die Mutation beweist es (`BEO-TDS/nachweis-von-der-falschen-seite-der-grenze`). — **Ausgang:** entfallen. Der Test liegt im nicht assoziierten Source-Set `integrator`; die Mutation m3 (Einstieg `internal`) macht `compileIntegratorKotlin` rot, der Sensor `integratorOhneAssoziation` bricht bei Assoziation. Die Beobachtung bleibt im Register (2×).
- Lebenszyklus in einem fremden Prozess: Der Integrator ruft `stop` aus beliebigem Thread, hält Zeilenquellen offen oder wirft aus dem Backend; [ADR-0013](../../adr/0013-backend-grenzen.md) regelt die Grenzen für den Protokollkern, das Beenden aus dem Embedder ist dort nicht festgelegt. — **Ausgang:** teils eingetreten, weiter offen. Behoben und getestet: `stop` ist idempotent, nacheinander aus einem Thread (ohne die Sperre ist `TdsServerLifecycleTest` rot). Weiter offen: `stop` aus mehreren Threads, `stop` während `start`, Zeilenquellen, die beim Beenden offen bleiben oder werfen (`SPEC-059`: „nicht festgelegt“), und das Wiederbinden des Ports nach `stop` (nur „Verbindung abgelehnt“ belegt). Register `BEO-TDS/lebenszyklus-im-fremden-prozess-nicht-gemessen`.
- Der Ort des Einbettungstests hängt an den Modulgrenzen von `slice-qa-wartbarkeit` (in Arbeit); ein dort beschlossenes Modul-Layout verändert den Zuschnitt dieses Slice. — **Ausgang:** entfallen. `slice-qa-wartbarkeit` liegt in `done/`; seine „Modulgrenzen“ sind Importregeln der Pakete (detekt), kein Gradle-Modul-Layout. Der Zuschnitt dieses Slice (Source-Set `integrator`) blieb.
- Die Abdeckungsmatrix (`make spec-abdeckung`) las nur `src/test`; der Erzeuger liest jetzt auch `src/integrator` und `build/test-results/integratorTest` (Selbsttest `make spec-abdeckung-selbsttest` deckt zwei Verzeichnisse ab). Die Matrix selbst ist noch nicht neu erzeugt. — **Ausgang:** eingetreten. Der Erzeuger liest beide Verzeichnisse; die Matrix ist mit `make spec-abdeckung` neu erzeugt und im Inhalts-Commit der Closure committet (`SPEC-059`: 6 Tests, keine Waise).
- Dependency-Verifikation: Das Source-Set löst beim Übersetzen eine abgetrennte Konfiguration mit JUnit 5.10.1 (nur Metadaten) auf; `gradle/verification-metadata.xml` führt dafür neue Einträge. — **Ausgang:** eingetreten. Die neuen Einträge betreffen nur `.module` und `.pom` der abgetrennten Konfiguration; `make gates` ist grün.

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

- **Gegenstand:** entfällt (der Slice lieferte, mit den Einschränkungen unten).
- **Was hat funktioniert:** Die drei Liefer-Punkte liegen vor. `TdsServer.start(config, source)` mit `BackendSource` und `StartOutcome` ist öffentlich; `ConfigValidation.check` prüft für Lader und Embedder gleich; `Wiring`, `Bootstrap` und der Lesepfad sind `internal`. Das Source-Set `integrator` (nicht assoziiert) startet den Server mit einem In-Memory-Backend und mit `BackendSource.Simulation` samt Zeilenquelle des Integrators und beantwortet Rohbytes-Anfragen (`EmbeddingTest`, `TdsServerLifecycleTest`; `make test`). Die Verifikation fuhr drei Mutationen selbst: m1 (`launch` ohne `ConfigValidation.check`) rot, m2 (`Custom` verliert sein Backend) rot, m3 (Einstieg `internal`) macht `compileIntegratorKotlin` rot ohne Zugriff auf das Hauptmodul (Fundstelle `EmbeddingTest.kt:57` und `:175`). Der Implementer fuhr zusätzlich die Mutationen ohne `AtomicBoolean`-Sperre in `stop` und ohne `wipe()` beim Laden des Materials (`TdsServerLifecycleTest` rot) und die Assoziation des Source-Sets (Sensor `integratorOhneAssoziation` meldet).
- **Was ging anders als geplant:** (a) Der Review (kein HIGH, 5 MEDIUM, 3 LOW) fand Aussagen breiter als der Beleg: `stop`-Idempotenz ohne Test (M-1), „der Prozess ruft denselben Start“ (M-2, tatsächlich `launch`), „der Start löscht das Passwort“ (M-3, nur beim Laden des Materials), der Mutations-Nachweis ohne Sensor (M-4); behoben in den Fix-Runden `df5f530`, `8c55a7b`, `5eb5cbd` durch Test, Einschränken und Sensor. (b) Die Verifikation (kein HIGH, 2 MEDIUM, 4 LOW) nannte den Slice closure-fähig mit den Bedingungen B-1 bis B-4; sie sind hier erfüllt. (c) Der Rahmen änderte sich während des Slice: Nutzerentscheidung 2026-11-01, das Produkt ist die JVM-Bibliothek, der ausführbare Server ein Testserver ([ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md), `Proposed`). `main()` → `Bootstrap.start` → `launch`, der Lader in `runtime` und die Zusagen zu `ConfigKeys`, `simulationFile` und „ein Artefakt“ sind **Zwischenstand vor dem Produktschnitt** (Folgepflicht unten).
- **Reports:** [Review M-1 bis M-5, L-1 bis L-3, I-1 bis I-8](../../../reviews/2026-11-01-83f7a47-slice-integrations-einstieg.md), [Verifikation F-1 bis F-6, I-1 bis I-5](../../../reviews/2026-11-01-dcdbbf7-verifikation-slice-integrations-einstieg.md). **Wer was gesehen hat:** (1) Den Stand `83f7a47` hat der Reviewer gesehen. (2) Die Fix-Runden haben kein eigenes Review; der Verifier hat sie auf Stand `dcdbbf7` geprüft. (3) Diese Closure-Notiz, die Entscheidungen, die Folgepflichten und die Register-Einträge hat nur der Planner gesehen; ein Gegenlesen durch einen zweiten Kontext liegt nicht vor.
- **Frischer Lauf der Closure:** `make spec-abdeckung` auf dem gemergten Stand (Exit 0, 37 von 56 Festlegungen mit Nachweis, 15 ohne; `SPEC-059` 6 Tests); `make gates` im Gate-Lauf vor dem Inhalts-Commit, Ergebnis im Bericht des Planners. Das gemergte Ergebnis aus Zweig und `main` (Stand `d2d29ca`) ist damit zusammen gebaut.

**Zuordnung je Aussage.** *gemessen* heißt ein bestandener Test oder eine bestandene Mutation in diesem Repo, gegen das In-Memory-Backend und die Simulation mit Zeilenquelle des Repos, **nicht** an einem fremden Programm; *Handvektor* gibt es nicht; *Selbstkonsistenz* ist die Gleichheit von Spec, KDoc und Test; *Festlegung* eine Entscheidung ohne Messung.

| Stärke | Aussage | Beleg |
|---|---|---|
| gemessen | Der Einstieg ist von außerhalb des Hauptmoduls aufrufbar und beantwortet Rohbytes-Anfragen mit In-Memory-Backend und mit Simulation samt Zeilenquelle (2/1/0 Zeilen, ein Fehler, danach weiter; Zeilenquelle schließt je Anfrage) | `EmbeddingTest`; Mutation m3 rot am Übersetzen |
| gemessen | `ConfigValidation.check` im Start: ohne sie nimmt der Start eine ungültige Konfiguration an | `EmbeddingTest` (Abbruch ohne Port); Mutation m1 rot |
| gemessen | `Aborted(reason, issues)` für `REQUIRED` ohne Material, `TEST_UNENCRYPTED` mit Material, Port 65536 und `Custom` plus Datei; danach „Verbindung abgelehnt“ | `EmbeddingTest`; Mutation m2 rot |
| gemessen | Zweites `stop` wirkungslos (Logzeile genau einmal); Passwort wird beim Laden des Materials gelöscht, bei Flag- und Prüfungsabbruch nicht | `TdsServerLifecycleTest`; Mutationen des Implementers rot |
| Selbstkonsistenz | `ConfigIssue`, `IssueKind` und Reihenfolge der Schlüssel des Laders vor und nach dem Schnitt gleich; Lader, Dateileser und `Server.kt` per R95/R100 verschoben | Vergleich des Reviews und der Verifikation; Move-Commits `969ced4`, `8c31aac` |
| Festlegung (Setzung) | Form (a) `ConfigValidation.check`; `Aborted(reason, issues)` mit `reason` ohne Werte; kein Log im Einstieg; Passwort-Löschung beim Laden; `ConfigValidation` und `ConfigKeys` gehören zur zugesagten Fläche; Zusage ab `1.0` semantisch versioniert | `SPEC-059`, [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) (Setzungen in §3) |
| nicht gemessen | Aufruf von `stop` aus mehreren Threads, `stop` während `start`, Zeilenquellen, die werfen oder offen bleiben | `SPEC-059` „nicht festgelegt“; Register `BEO-TDS/lebenszyklus-im-fremden-prozess-nicht-gemessen` |
| nicht gemessen | Lauf ohne Jackson auf dem Klassenpfad des Einbettenden (gelesen: keine Jackson-Typen in der Signatur) | Register `BEO-TDS/jackson-abhaengigkeit-fuer-embedder-nicht-gemessen` |
| nicht gemessen | Wiederbinden des Ports nach `stop` (nur „Verbindung abgelehnt“) | Register `BEO-TDS/lebenszyklus-im-fremden-prozess-nicht-gemessen`, Kleinigkeit F-3 |

**Entscheidungen des Planners zu den Bedingungen der Verifikation:**

- **B-1 (Belege):** diese Notiz trägt die Mutationen m1 bis m3 der Verifikation und die des Implementers (Tabelle oben).
- **B-2 (Risiken):** alle acht Risiken aus §6 tragen je genau einen Ausgang (entfallen: Stabilitätszusage, Nachweis von der falschen Seite, Ort des Tests; eingetreten: Verhältnis zu `Main.kt`, Abdeckungsmatrix, Dependency-Verifikation; weiter offen: Jackson, Lebenszyklus im fremden Prozess). Die Vorschläge I-4 des Verifiers sind übernommen; abweichend: „Ort des Tests“ entfallen statt offen, weil `slice-qa-wartbarkeit` in `done/` liegt und seine Modulgrenzen Importregeln der Pakete sind, kein gradle-Layout.
- **B-3 (Matrix):** `make spec-abdeckung` neu erzeugt und im Inhalts-Commit committet (F-4: 15 Festlegungen ohne Nachweis stammen aus älterer Drift, nicht aus diesem Slice).
- **B-4 / M-2 / F-2 (Zusagen im Rahmen von [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md)):** `SPEC-059` sagt `ConfigKeys`, `ServerConfig` mit `simulationFile` und „Jar und `lib/` (ein Artefakt, `SPEC-052`)“ zu; sie sind **Zwischenstand vor dem Produktschnitt**, nicht die Endzusage. Die Zusage-Zeile von `SPEC-059` trägt dazu einen Satz (eigener Commit). **Folgepflicht (4):** Nach Annahme von [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) passt ein eigener Spec-Schritt `SPEC-059` an (Wegfall von `simulationFile`, `ConfigKeys`, Jackson, „ein Artefakt“); Träger: der Nutzer, Register `BEO-TDS/spec-059-zusagen-vor-produktschnitt`. Dieser Slice ändert die Spezifikation darüber hinaus nicht.
- **M-1 (Ort von `slice-explicit-api-umbau`):** Nutzerentscheidung 2026-11-01, Option (c): der Slice bleibt vorerst ohne Welle und ohne Verantwortlichen in `open/`. Die Menge der Namen ändert sich mit [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md); Zuordnung zu einer Welle und Neuzuschnitt erst danach. Der Compiler erzwingt die zugesagte Liste bis dahin nicht (`SPEC-059`: „heute mehr Namen öffentlich“).
- **Kleinigkeiten ohne Slice, für einen Implementer:** F-3 (ein `ServerSocket(port)` nach `stop` in `EmbeddingTest` trüge „Port frei“ ganz), F-5 (`--continue` oder ein Satz im Stage-Kommentar: ohne `--continue` bleiben Unit-Tests bei rotem `integratorTest` unbewertet), F-6 (Zeilenlänge der Kommentare), I-1 (Proposed-Verweise zu [ADR-0013](../../adr/0013-backend-grenzen.md) in `StreamingDispatchTest.kt`).

**Ausgang je Risiko aus §6:** Stabilitätszusage entfallen · Jackson weiter offen · Verhältnis zu `Main.kt` eingetreten · Nachweis von der falschen Seite entfallen · Lebenszyklus im fremden Prozess weiter offen (teils eingetreten) · Ort des Tests entfallen · Abdeckungsmatrix eingetreten · Dependency-Verifikation eingetreten. Je genau einer.

**Steering-Loop-Einträge.**

- **Geschärfte Regel / neuer Sensor:** keine mit `liegt in` deklariert. Der Sensor `integratorOhneAssoziation` (`build.gradle.kts`) trägt die Lehre, dass ein Nachweis von außen nur zählt, wo `internal` nicht sichtbar ist, und die Mutation „Einstieg `internal`“ ihn belegt; er trägt keinen Herkunfts-Anker, deshalb steht das Feld nicht da (Zielort ohne Anker wäre nicht paarbar). Gezählt, nicht verkörpert: `BEO-TDS/nachweis-von-der-falschen-seite-der-grenze` (2×), `BEO-TDS/aussagen-ueber-messung-hinaus` (weitere Wiederkehr nach der Verkörperung, M-2 und M-3 des Reviews), `BEO-TDS/kommentar-nennt-abwesenden-oder-unwirklichen-zustand` (2×, L-1), `BEO-TDS/move-commit-laesst-detekt-rot` (1×).
- **Benannte Spec-Lücke:** `SPEC-059` ist bis zur Annahme von [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) Zwischenstand; der Nachzug steht aus: `BEO-TDS/spec-059-zusagen-vor-produktschnitt`. Dazu die ungemessenen Teile des Lebenszyklus und der Jackson-Freiheit (Register oben).
- **Lehre, ohne Regel:** Ein Slice, dessen Zusagen von einer `Proposed`-ADR zum Produktschnitt überholt werden, trägt die Zwischenstand-Kennzeichnung in der Zusage selbst, nicht nur an einem Nebensatz; einmal beobachtet, keine Regel.

**Register.** Fortgeschrieben: `BEO-TDS/nachweis-von-der-falschen-seite-der-grenze` (neue Evidence `slice-integrations-einstieg`), `BEO-TDS/aussagen-ueber-messung-hinaus` (Evidence), `BEO-TDS/kommentar-nennt-abwesenden-oder-unwirklichen-zustand` (Evidence), `BEO-TDS/oeffentlicher-einstieg-ohne-traeger` (`gestrichen`: der Einstieg ist mit diesem Slice getragen); neu: `BEO-TDS/spec-059-zusagen-vor-produktschnitt` (1×), `BEO-TDS/jackson-abhaengigkeit-fuer-embedder-nicht-gemessen` (1×), `BEO-TDS/lebenszyklus-im-fremden-prozess-nicht-gemessen` (1×), `BEO-TDS/move-commit-laesst-detekt-rot` (1×). Über der Schwelle liegt nur `BEO-TDS/aussagen-ueber-messung-hinaus` (43 Evidence-Dateien); sie ist bereits verkörpert (`AGENTS.md` §3.8), der Planner ändert ihren Ausgang nicht. Ob eine weitere Schärfung nötig ist, weil die Wiederkehr nach der Verkörperung anhält, entscheidet der Nutzer.

**Paarungen.** Anker: kein `liegt in`, nichts zu paaren. Folge-Slice: `slice-explicit-api-umbau` (`open/`, ohne Welle, Option (c)); die Lebenszyklus- und Jackson-Messung und der Spec-Nachzug haben keinen Folge-Slice (Entscheidung beim Nutzer, abhängig von [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md)). Register: oben.

Belege des Implementers für die Closure-Notiz (Eingabe, nicht das Urteil):

- Mutation „Einstieg `internal`“: `TdsServer.start` als `internal` macht `compileIntegratorKotlin` rot („Cannot access ‚suspend fun start …‘: it is internal“, an beiden Aufrufstellen in `EmbeddingTest`). `class TdsServer` als `internal` ist keine Mutation für diesen Beleg: das Hauptmodul bricht vorher.
- Mutation „Source-Set assoziiert“: `associateWith(main)` ohne die Abhängigkeit auf `main.output` bricht schon die Auflösung in der Stage `deps` (Lock); mit der Task `integratorOhneAssoziation` allein (Einzellauf) meldet der Sensor „Source-Set integrator ist assoziiert mit [main]“.
- Mutationen der Tests: ohne die Sperre (`AtomicBoolean`) in `stop` und ohne `wipe()` beim Laden des Materials sind `TdsServerLifecycleTest` (beide Tests) rot; weitere Mutationen stehen im Bericht des Implementers.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`). Das Package `runtime` (Composition Root) ist ein Pfad-Cluster (Achse 3), trägt aber keine eigene Strukturregel (Achse 1) und keine eigene Inventur-Linie (Achse 2): eine von drei Achsen, unter der Schwelle. Keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`). Treffer:

- `BEO-TDS/oeffentlicher-einstieg-ohne-traeger` — 1×, offen (Beleg `evidence/slice-sim-aenderung-integration.md`). Treffer: der Befund, den dieser Slice trägt.
- `BEO-TDS/nachweis-von-der-falschen-seite-der-grenze` — 1×, offen. Treffer: der Test von außen (§2 Punkt 3, §6).
- `BEO-TDS/test-belegt-nur-den-fake` — verkörpert (`.harness/skills/reviewer.md`, Anker `seit welle-backend-streaming`). Treffer: der In-Memory-Test darf nicht nur sein Skript ausliefern; die Eingaben variieren.
- `BEO-TDS/aussagen-ueber-messung-hinaus` — verkörpert (`AGENTS.md` §3.8). Treffer: Aussagen zur Stabilität des Einstiegs stehen an einem Test oder sind als Festlegung gekennzeichnet.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
