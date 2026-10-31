# Slice slice-sim-auswahl-streaming: Simulations-Backend, Teil 2: Auswahl mit Bedingung, Parameter und schrittweises Lesen der Zeilenquelle

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-simulations-backend

**Bezug:** [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md), [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md), [ADR-0013](../../adr/0013-backend-grenzen.md)

**Berührte Spec-Stellen:** SPEC-002 · SPEC-029 · SPEC-031 · SPEC-035 · SPEC-036 · SPEC-039 · ARC-006 · ARC-016 · [TDS-FA-11.a](../../../../spec/spezifikation.md#tds-fa-11a--simulations-backend)

**Verantwortlich:** pt9912.

**Autor:** pt9912. **Datum:** 2026-10-30.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Das Simulations-Backend ist eine `QueryBackend`-Implementierung, die eine erkannte Auswahl (S1) beantwortet: Metadaten auch ohne Treffer, passende Zeilen schrittweise aus der Zeilenquelle, Abschluss mit Zeilenzahl; ein Ergebnis oberhalb der Heap-Grenze eines Testlaufs wird nicht vollständig gehalten.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Einfügen, Ändern und Löschen — [slice-sim-aenderung-integration](../open/slice-sim-aenderung-integration.md).
- Sequenzierer, Tokens und Transport — der Slice endet am Strom `events` des Ports; die Kette bis zur Senke belegt [slice-backend-streaming-nachweis](../done/welle-backend-streaming/slice-backend-streaming-nachweis.md) am Fake, der Referenzclient-Lauf am Simulations-Backend [slice-sim-aenderung-integration](../open/slice-sim-aenderung-integration.md).
- Zeilendarstellung je `SqlType`, Grenze für „kleine“ Ergebnisse und die Frist `deadlineEpochMillis` — entschieden in [ADR-0013](../../adr/0013-backend-grenzen.md): `List<Any?>` mit den Klassen je Spaltenart (`Int`, `Long`, `Boolean`, `String`, `BigDecimal`, `LocalDate`, `LocalTime`, `LocalDateTime`, `null`), keine Grenze für „kleine“ Ergebnisse im Vertrag (Setzung), die Frist setzt der Protokollkern durch und das Backend darf sie ignorieren (Träger: [slice-attention-cancel-timeout](../open/slice-attention-cancel-timeout.md)); dieser Slice übernimmt die Entscheidung.
- Blockierende Zeilenquellen und nicht unterbrechbare Aufrufe — entschieden in [ADR-0013](../../adr/0013-backend-grenzen.md): Vertragspflicht „suspendierend“, Umhüllung beim Integrator, Hilfsadapter erst beim Trigger „echtes blockierendes Backend“; geprüft wird eine suspendierende Quelle.
- Ein Vorfilter in der Zeilenquelle als Optimierung — der Hinweis `SimQuery.filter` wird übergeben, die Bedingung wendet das Backend immer selbst an.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [x] **Auswahl** ([TDS-FA-11.a](../../../../spec/spezifikation.md#tds-fa-11a--simulations-backend) Schritte 4 und 5): Parameter typisiert einsetzen (nie als Text) und in den Spaltentyp überführen — streng und verlustfrei nach SPEC-035 und [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) (gleiche Typfamilie, ISO-Formate, keine Rundung, keine Zahl↔Text-Überführung, Parameter nur verlustfrei erweitert, Überlauf immer ungültig), sonst `TDS_SIM_INVALID_VALUE` vor dem Lesen; Dezimalvergleich über `compareTo`, Textvergleich ohne Beachtung der Schreibweise; `ResultStart` mit den Spalten der Auswahlliste (bei `*` alle in der Reihenfolge der Beschreibung) auch ohne Treffer; die Bedingung wendet das Backend auf jede Zeile an (`In`, `IsNull`, UND, NULL trifft nie, ein Widerspruch trifft keine Zeile); `ResultEnd` mit der Zeilenzahl.
- [x] **Schrittweises Lesen und Ende** (Schritte 5 und 7, SPEC-029, SPEC-031): `next()` wird nur auf Abnahme gerufen, `close()` genau einmal nach Ende, Fehler, Cancel und Disconnect; ein Fehler der Zeilenquelle vor `ResultStart` ist ein `Failure` mit `TDS_BACKEND_ERROR` ohne Metadaten, nach `ResultStart` endet der Strom nach SPEC-031 (die Folge für die Verbindung hält der Sequenzierer).
- [x] **Port-Pflichten und Speichergrenze:** Die Pflichten, die die abstrakte Suite `QueryBackendContract` am Test-Backend prüft (`ScriptedBackendContractTest` ist ihre erste Implementierung), gelten auch für das Simulations-Backend (ein Backend durchläuft die Suite, indem es sie erbt; ein Gegenstück legt der Implementer fest); eine Zeilenquelle mit mehr Daten, als der gesetzte Heap eines Testlaufs hält, wird an einer langsamen Senke gelesen, der Heap bleibt unter der Grenze, und ein Zähler belegt, dass die Quelle bis zu einer bekannten Stelle gelesen wurde.

Nachweis-Pflichten für alle drei Punkte:

- [x] Jeder Test trägt im KDoc über der Testfunktion den Block-Tag `@spec SPEC-<NNN>` (Konvention nach [slice-qa-spec-abdeckung](../done/slice-qa-spec-abdeckung.md)); die Tests laufen in `make test` und sind grün.
- [x] Jede Aussage steht an einem Test oder ist als Festlegung gekennzeichnet; die Zuordnung *Test | Festlegung* je Aussage steht in der Closure-Notiz (`BEO-TDS/aussagen-ueber-messung-hinaus`, siehe §8).
- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
      Der Review sah den Stand 7c8917d; die Fix-Runde 31ae343 hat **kein eigenes Review**,
      sie sah nur der Verifier (Einzelheiten in §7).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
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
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/SimulationBackend.kt` | neu | `QueryBackend` für S1: Planung, Bedingung, Lesen auf Abnahme, Schließen, Fehler der Quelle, Cancel |
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/SimValues.kt` | neu | Überführung von Literal und Parameter in den Spaltentyp, Gleichheit nach Typ |
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/SimConditions.kt` | neu | Bedingung vorbereiten (Werte vor dem Lesen überführen), Vorfilter-Hinweis, Anwendung je Zeile |
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/StatementPlanner.kt` | update | `SimCodes` um `TDS_SIM_INVALID_VALUE` und `TDS_BACKEND_ERROR` |
| `spec/spezifikation.md` | update | SPEC-035: Einzelfestlegungen der Überführung, Spalten und Filter der Quelle, Fehler der Quelle; Historie |
| `src/test/kotlin/io/github/pt9912/tdswire/port/QueryBackendContract.kt` | update | zwei Haken (`sql`, `seen`) mit Voreinstellung des Test-Backends; benannt in §6 |
| `src/test/kotlin/io/github/pt9912/tdswire/simulation/SimValuesTest.kt`, `src/test/kotlin/io/github/pt9912/tdswire/simulation/SimulationBackendSelectTest.kt`, `src/test/kotlin/io/github/pt9912/tdswire/simulation/SimulationBackendSourceTest.kt` | neu | Überführung, Auswahl und Bedingung, Lesen/Schließen/Fehler/Cancel; `@spec`-Tags SPEC-002, SPEC-029, SPEC-031, SPEC-035, SPEC-036, SPEC-037, SPEC-038, SPEC-039 |
| `src/test/kotlin/io/github/pt9912/tdswire/simulation/SimulationBackendContractTest.kt`, `src/test/kotlin/io/github/pt9912/tdswire/simulation/SimulationMemoryTest.kt`, `src/test/kotlin/io/github/pt9912/tdswire/simulation/SimulationSupport.kt` | neu | Suite `QueryBackendContract` am Simulations-Backend, Heap-Nachweis mit Zähler, gemeinsame Hilfen |
| `src/test/kotlin/io/github/pt9912/tdswire/runtime/SimulationCodesTest.kt` | update | die zwei neuen Codes von `SimCodes` gegen `ErrorCode` |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-sim-erkennung](../done/slice-sim-erkennung.md) und [slice-backend-streaming-nachweis](../done/welle-backend-streaming/slice-backend-streaming-nachweis.md) liegen in `done/` (das Heap-Verfahren des Testlaufs wird übernommen, nicht neu erfunden), und die Zeilendarstellung ist entschieden ([ADR-0013](../../adr/0013-backend-grenzen.md): Klassen je Spaltenart; `Accepted`, erfüllt), und ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2 oder der Heap-Nachweis sprengt den Rahmen; Schnittvorschlag: Heap-Nachweis als eigener Slice.
- `in-progress` → `open` (blockiert — Carveout?): [ADR-0013](../../adr/0013-backend-grenzen.md) wird durch eine Folge-Entscheidung zur Zeilendarstellung abgelöst, oder der Port ändert sich unter dem Slice.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün (einschließlich `make test`), der Review-Report liegt vor, und die Closure-Notiz trägt die Zuordnung *Test | Festlegung* mit Messbedingungen des Heap-Laufs sowie den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Überführungsregel, Textvergleich und Dezimalvergleich sind Setzungen ([ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md): Messung nur im JDK-Spike, nichts am echten SQL Server); der Slice testet sie als Festlegung, nicht als Messung, und führt sie in der Closure so (`BEO-TDS/aussagen-ueber-messung-hinaus`, `BEO-TDS/messung-am-echten-sql-server-fehlt`). Sie gelten für die JVM-Typen der Werte, die [ADR-0013](../../adr/0013-backend-grenzen.md) festlegt (die Überführungsregel ist für diese Klassen nicht neu zu prüfen), und sind bei einer anderen Zeilendarstellung neu zu prüfen. — **Ausgang:** *weiter offen* — Die Überführungsregel, der Textvergleich und der Dezimalvergleich sind als Festlegung getestet und in [SPEC-035](../../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) als „Setzung, nicht am Server gemessen“ gekennzeichnet; die Messung am echten Server fehlt. Ins Register: `BEO-TDS/messung-am-echten-sql-server-fehlt` (Evidence-Datei dieses Slice); Träger [slice-qa-vergleichslauf-d](../open/slice-qa-vergleichslauf-d.md) führt diese Punkte nicht in seiner DoD (so im Register geführt).
- Die Heap-Aussage gilt für einen Testlauf mit gesetztem Heap und einer Test-Senke, nicht für das Produkt unter Last (Grenze der Aussage in [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md); `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`). — **Ausgang:** *weiter offen* — Das Heap-Ergebnis gilt nur für diesen Lauf (ein Messpunkt: 1 200 000 × 128 Zeichen gegen 128 MiB Heap, wartende Test-Senke); es sagt nichts über den Produktivbetrieb. Ins Register: `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` (Evidence-Datei dieses Slice).
- Eine Zeilenquelle, die den Hinweis `SimQuery.filter` ignoriert oder mehr liefert, muss trotzdem zum richtigen Ergebnis führen; ein Test mit einer solchen Quelle belegt die Pflicht, dass das Backend die Bedingung selbst anwendet. Eine Quelle, die zu wenig liefert, ist Sache des Integrators und wird nicht erkannt. — **Ausgang:** *entfallen* — Die Quelle, die den Hinweis ignoriert, ist belegt: `SimulationBackendSelectTest` (Vorfilter wird als Hinweis übergeben, die Bedingung wendet das Backend selbst an) und die Mutationen M3 (Vorfilter immer `null`) und M4 (Bedingung trifft immer) der Verifikation sind rot. Eine Quelle, die zu wenig liefert, bleibt Sache des Integrators (so im Plan).
- Die dreiwertige Logik (`ANSI_NULLS ON`) ist als vorläufig gekennzeichnet und nur in einer Stichprobe an einer Instanz bestätigt ([ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md)); die Tests führen sie als Festlegung. — **Ausgang:** *weiter offen* — Die dreiwertige Logik bleibt eine Festlegung (Test mit NULL trifft nie, Widerspruch trifft keine Zeile), vorläufig und nur in einer Stichprobe an einer Instanz bestätigt. Ins Register: `BEO-TDS/messung-am-echten-sql-server-fehlt` (dieselbe Evidence-Datei wie das erste Risiko, eine Datei je Vorgang).
- Das Wiederverwenden der Suite `QueryBackendContract` kann eine Änderung an ihr verlangen, etwa wenn das Simulations-Backend das Skript nicht in der Form des Test-Backends entgegennimmt; sie wird in diesem Slice benannt und nicht still vorgenommen. Vorgenommen (Implementer): `QueryBackendContract` bekam zwei überschreibbare Haken, `sql` (Voreinstellung `SELECT 1`) und `seen` (Voreinstellung: der Fehler unverändert); das Test-Backend verhält sich wie zuvor, das Simulations-Backend überschreibt beide, weil es den Text auswertet und Code und Meldung eines Fehlers der Quelle selbst festlegt (`TDS_BACKEND_ERROR`). — **Ausgang:** *eingetreten, im Slice aufgelöst* — Die Suite bekam zwei Haken (`sql`, `seen`) und in der Fix-Runde einen Test (`Generator laeuft waehrend der Wartezeit des Lesers nicht voraus`), den ein vorauslesendes Backend an beiden Implementierungen rot macht (Mutationen M1 und M1b der Verifikation). Kein Carveout, kein Folge-Slice: die Grenze steht im Kopf-KDoc der Suite (bei festem Fehler wird nicht geprüft, dass Code und Meldung eines Skripts durchgereicht werden).

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

- **Was hat funktioniert:** Die drei Liefer-Punkte (Auswahl mit Überführung und Bedingung, Lesen auf Abnahme mit genau einem `close()`, Port-Pflichten und Heap-Nachweis) liegen in einem Package ohne Verstoß gegen die Importregel; das Simulations-Backend erbt die Suite `QueryBackendContract` und besteht ihre Tests wie das Test-Backend. Die Mutationen der Verifikation röteten M1 (Vorauslesen), M1b (Vorauslesen am Test-Backend), M2 (verengter Zeit-Parser), M3 (Vorfilter immer `null`), M4 (Bedingung trifft immer), M5 (`close()` ohne Einmaligkeits-Sperre), M6 (Schließen im `finally` entfernt) und M8 (Zeitgrenze des Heap-Tests); M7 (jede Zeile behalten) macht den Lauf rot, nicht den Test (L-1, unten).
- **Was ging anders als geplant:** (a) Der Review fand ein MEDIUM (F-1: der Suite-Test zur Abnahme blieb grün, wenn der Strom vorausliest), drei LOW (F-2 Setzungen nur im Test-KDoc, F-3 Formatraum des Zeit-Parsers ungeprüft, F-4 Heap-Test ohne Zeitgrenze) und drei INFO (F-5 Grenze des Speicherbelegs, F-6 Suite-Haken, F-7 Registrierung ohne Abmeldepflicht im KDoc); die Fix-Runde 31ae343 behob alle sieben. (b) Die Verifikation fand kein HIGH und kein MEDIUM, zwei LOW und zwei INFO: L-1 (die Heap-Mutation endet als abgebrochener Test-Worker statt als roter Test, `BEO-TDS/oom-bricht-testlauf-statt-test`), L-2 (die Matrix `docs/plan/spec-abdeckung.md` lag hinter dem Bestand), I-1 (Closure-Pflichten, hiermit erfüllt) und I-2 (Gliederung des Absatzes „Fehler der Zeilenquelle“ in [SPEC-035](../../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends)). (c) L-2 und I-2 hat der Planner in aac2726 nachgezogen; L-1 bleibt offen (Register). (d) Der Review ließ die Mutation „jede Zeile behalten“ 23 Minuten ohne Ausgang laufen und konnte sie nicht beenden; die Verifikation maß 76 s bis zum Abbruch des Test-Workers (M7). Die Ursache des langen Laufs beim Review ist nicht geklärt.
- **Reports:** [Review F-1 bis F-7](../../../reviews/2026-10-31-7c8917d-slice-sim-auswahl-streaming.md), [Verifikation L-1, L-2, I-1, I-2](../../../reviews/2026-10-31-b123ecb-verifikation-slice-sim-auswahl-streaming.md). **Wer was gesehen hat:** (1) Den Stand 7c8917d (Umsetzung f65f486 und 72a3b04) hat der Reviewer gesehen. (2) Die Fix-Runde 31ae343 hat **kein eigenes Review**; sie haben nur der Verifier (Mutationen M1, M1b, M2, M8, Lesen von Spec und KDoc) und der Implementer gesehen. (3) Der Doku-Nachzug aac2726 (Umgliederung des Absatzes in SPEC-035, Matrix neu, acht Evidence-Dateien) hat nur der Planner gesehen: dass die Umgliederung keine neue Aussage enthält, folgt aus dem Lesen des Diffs von `spec/spezifikation.md`, nicht aus einem zweiten Kontext; `make gates` war danach grün. (4) Diese Closure-Notiz hat nur der Planner gesehen.
- **Frischer Lauf der Closure:** `docker build --no-cache-filter test --target test .` auf dem Stand c0ffd7a (`git merge main` meldete „Bereits aktuell“), einmal: Exit 0, 738 Tests PASSED, keine rote Zeile, `BUILD SUCCESSFUL in 1m 18s`, die Stage `test` nicht aus dem Cache; darin `SimulationMemoryTest` PASSED. Der Doku-Nachzug aac2726 berührt nur `spec/`, `docs/plan/spec-abdeckung.md` und das Register, keinen Quelltext. `make spec-abdeckung`: Matrix neu erzeugt (SPEC-035: 56 Tests, SPEC-039: 13; „14 Festlegung(en) ohne Nachweis“ in der Matrix insgesamt, keine neue Waise dieses Slice; die Tests der abstrakten Suite stehen wie bisher als „nicht gelaufen“, weil ihre Ergebnisse unter den erbenden Klassen stehen). `make gates` nach dem Doku-Nachzug: Exit 0; das Ergebnis nach dieser Notiz und nach dem Move steht im Bericht der Closure, nicht in dieser Zeile.

**Messbedingungen des Heap-Laufs** (`SimulationMemoryTest`): Docker-Stage `test`, Heap des Test-Workers 128 MiB (134,2 MB), Zeilenquelle mit 1 200 000 Zeilen zu je 128 Zeichen (Untergrenze 153,6 MB Text), also 14 % über dem Heap; die Senke wartet je Zeile; ein Zähler hat eine Unter- und eine Obergrenze und belegt, bis zu welcher Stelle die Quelle gelesen wurde; Zeitgrenze 90 s (M8: `LIMIT_NANOS = 1` rötet mit „Zeitgrenze bei Zeile 0“). **Ein Messpunkt, eine Maschine, ein Lauf.** Der Beleg trägt „das Ergebnis wird nicht vollständig gehalten“; er trägt **nicht** „Speicher unabhängig von der Zeilenzahl“ und nicht den Produktivbetrieb. Rot unter M1 (Vorauslesen); unter M7 ein roter Lauf ohne roten Test (L-1).

**Zuordnung je Aussage.** *gemessen* heißt ein bestandener Lauf am Code dieses Slice mit handgebauten Eingaben und Attrappen, **nicht** an einem echten SQL Server und nicht am Referenzclient; *Handvektor* ein Fall mit Rechnung von Hand; *Selbstkonsistenz* Code gegen Code aus demselben Slice; *Festlegung* eine Entscheidung ohne Messung.

| Stärke | Aussage | Beleg |
|---|---|---|
| gemessen (Eigencode, handgebaute Eingabe) | `ResultStart` trägt die Spalten der Auswahlliste (bei `*` alle in der Reihenfolge der Beschreibung), auch ohne Treffer; `ResultEnd` trägt die Zeilenzahl; die Bedingung wendet das Backend auf jede Zeile an (`In`, `IsNull`, UND, NULL trifft nie, ein Widerspruch trifft keine Zeile) | `SimulationBackendSelectTest` (13 Tests); M4 rot (10 Tests) |
| gemessen (Eigencode) | Die Werte der Bedingung werden vor dem Öffnen der Quelle in den Spaltentyp überführt; ein ungültiger Wert ergibt `TDS_SIM_INVALID_VALUE`, ohne dass gelesen wird; Überlauf ist immer ungültig; der Parameter wird nur verlustfrei erweitert | `SimValuesTest` (13 Tests), `SimulationBackendSelectTest`; die Mutationen des Reviews und M2 der Verifikation rot |
| gemessen (Eigencode) | Der Formatraum der Zeit ist der des ISO-Parsers der Laufzeit (Sekunden optional, Nanosekunden, Jahr mit Vorzeichen, `T` Pflicht, keine Zeitzone) | `SimValuesTest` `der Formatraum der Zeit ist der der ISO-Parser`; M2 rot |
| gemessen (Eigencode, Zähler) | `next()` wird nur auf Abnahme gerufen, `close()` genau einmal nach Ende, Fehler, Cancel und Disconnect; ein Fehler der Quelle vor `ResultStart` ist ein `Failure` mit `TDS_BACKEND_ERROR` ohne Metadaten, danach endet der Strom nach [SPEC-031](../../../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität); `Error` und Abbruch werden nicht gefangen; die Meldung nennt den Text der Ausnahme nie | `SimulationBackendSourceTest` (12 Tests); M1, M5, M6 und das Leck der Meldung (M2 des Reviews) rot |
| gemessen (Suite, zwei Implementierungen) | Die Pflichten der Suite `QueryBackendContract` gelten am Simulations-Backend wie am Test-Backend; die Quelle läuft auch bei wartendem Leser nicht voraus | `SimulationBackendContractTest` und `ScriptedBackendContractTest` (je 9); M1 und M1b rot |
| gemessen (ein Messpunkt) | Ein Ergebnis über dem Heap des Testlaufs wird an einer wartenden Senke Zeile für Zeile gelesen und nicht vollständig gehalten (Bedingungen oben) | `SimulationMemoryTest`; frischer Lauf PASSED; M1 rot, M8 rot, M7 roter Lauf |
| gemessen (Eigencode) | Die zwei neuen Codes von `SimCodes` sind Namen von `ErrorCode` | `SimulationCodesTest` |
| Handvektor | 153,6 MB Text (1 200 000 × 128) gegen 134,2 MB (128 MiB), 14 % darüber | Rechnung im KDoc des Heap-Tests, im Review (F-5) nachgerechnet |
| Selbstkonsistenz | keine in diesem Slice | — |
| Festlegung | Überführungsregel von Literal und Parameter (gleiche Typfamilie, ISO-Format, keine Rundung, keine Überführung zwischen Zahl und Text, Parameter nur verlustfrei erweitert) und ihre Einzelfestlegungen: Zahl ohne Rest gilt (`1.0`, `3.500`), `0` und `1` sind in einer Bool-Spalte falsch und wahr, NULL als Parameter gilt für jeden Spaltentyp, ein nicht getragener Parameter ist ungültig, die Richtung des Parameters bleibt unbeachtet | [SPEC-035](../../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) „Setzung, nicht am Server gemessen“; [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) Entscheidung 6 trägt die Familien, nicht die Einzelfälle |
| Festlegung | Textvergleich ohne Beachtung der Schreibweise, Dezimalvergleich über `compareTo` (1.0 gleich 1.00) — [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) Entscheidung 9; Messung nur im JDK-Spike | SPEC-035; Tests halten die Regel, nicht das Verhalten eines Servers |
| Festlegung | Dreiwertige Logik (`ANSI_NULLS ON`), vorläufig, in einer Stichprobe an einer Instanz bestätigt ([ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md)) | `SimulationBackendSelectTest` als Festlegung |
| Festlegung | Die Namen im Metadatum sind die der Beschreibung (`SELECT AGE` liefert `Age`); eine Zelle anderer JVM-Klasse ist ungleich (`1` gegen `1L`); ein Fehler von `close()` bleibt ohne Folge; eine Zeile mit anderer Zahl von Werten als Spalten ist ein Fehler der Quelle | SPEC-035 (eigene Punkte seit aac2726) |
| Festlegung | Der Vorfilter `SimQuery.filter` wird übergeben und nie als Ersatz verwendet; Cancel und `close` beenden das Lesen vor der nächsten Zeile (ein hängendes `next()` endet nur mit dem Abbruch des Sammlers) | SPEC-035, KDoc von `SimulationBackend` |
| Festlegung | Die Suite-Haken `sql` und `seen`: bei festem Fehler prüft die Suite nicht, dass Code und Meldung eines Skripts durchgereicht werden | Kopf-KDoc von `QueryBackendContract` |
| nicht gemessen | Alles am echten SQL Server (Überführung, Textvergleich, Dezimalvergleich, dreiwertige Logik); das Verhalten am Referenzclient (Sequenzierer, Tokens, Transport: [slice-sim-aenderung-integration](../open/slice-sim-aenderung-integration.md)); der Heap unter Last und im Produktivbetrieb; die Frist `deadlineEpochMillis` (das Backend darf sie ignorieren, durchgesetzt wird sie im Protokollkern: [slice-attention-cancel-timeout](../open/slice-attention-cancel-timeout.md)); ein `next()`, das selbst hängt; eine Heap-Messung bei anderer Zeilenzahl oder Zeilengröße | Plan §1 und §6; Register |

- **Gegenstand:** entfällt (der Slice lieferte).
- **Risiken aus §6:** alle fünf tragen genau einen Ausgang (siehe §6): eins entfallen (ignorierende Quelle), zwei weiter offen im Register (Überführungsregel und Vergleiche; Heap-Ergebnis nur dieser Lauf), eins weiter offen im Register (dreiwertige Logik, dieselbe Evidence-Datei wie das erste), eins eingetreten und im Slice aufgelöst (Suite geändert).
- **Steering-Loop-Eintrag:** (1) **Lerneintrag, geschärfte Regel, nicht verkörpert:** Eine Port-Pflicht, die eine gemeinsame Suite zusagt, trägt erst, wenn ein Backend, das sie verletzt, die Suite rötet; der Review fand (F-1) und die Verifikation bestätigte (M1, M1b), dass der Suite-Test zur Abnahme ein vorauslesendes Backend durchließ, bis der Wartezeit-Test dazukam. Das ist die Wiederkehr von `BEO-TDS/test-belegt-nur-den-fake` und von `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test`; beide stehen über der Schwelle, **nicht verkörpert; Entscheidung beim Nutzer** (Regel, Sensor, Träger-Slice oder Streichung). Der neue Test in `QueryBackendContract` ist ein Sensor für genau diese Pflicht und gilt für jedes Backend, das die Suite erbt; ein Anker mit `liegt in` steht hier nicht, weil mit diesem Slice keine Regel mit Herkunfts-Anker verkörpert wurde. (2) **Benannte Spec-Lücke**, ohne `liegt in`, weil sie in einer versionierten Spec steht: [SPEC-035](../../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) führt „`0` und `1` sind in einer Bool-Spalte falsch und wahr“ und weitere Einzelfälle der Überführung als Setzung, Voreinstellung, nicht gemessen; die Familie trägt [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) Entscheidung 6, die Einzelfälle trägt sie nicht (Review, Antwort 2), und der Nutzer hat sie nicht bestätigt. Gezählt und nicht verkörpert, ohne Neuanlage: (3) `BEO-TDS/oom-bricht-testlauf-statt-test` steht mit diesem Slice bei 2× (L-1), unter der Schwelle, offen. (4) `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor` steht bei 4× (L-2), über der Schwelle, nicht verkörpert; Entscheidung beim Nutzer. (5) `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` steht bei 7×, über der Schwelle, nicht verkörpert; Entscheidung beim Nutzer. (6) `BEO-TDS/test-stage-cache-verdeckt-wiederholung` steht bei 5× und ist verkörpert (`make clean-build`); der Review fand die Stage im Cache. (7) `BEO-TDS/aussagen-ueber-messung-hinaus` steht bei 35× und ist verkörpert (`AGENTS.md` §3.8, Reviewer-Skill); die Wiederkehr (F-2, F-5, F-6) trat nach der Verkörperung auf und ist behoben. Kein Eintrag ohne Zählung: F-7 (KDoc ohne Abmeldepflicht) und F-4 (Zeitgrenze des Heap-Tests) sind einmalig und behoben.
- **Beobachtungs-Register (`../observations/`):** `evidence/slice-sim-auswahl-streaming.md` ergänzt in [BEO-TDS/oom-bricht-testlauf-statt-test](../observations/BEO-TDS/oom-bricht-testlauf-statt-test/observation.md) (M7; Zähler 2), [BEO-TDS/test-belegt-nur-den-fake](../observations/BEO-TDS/test-belegt-nur-den-fake/observation.md) (F-1; Zähler 4), [BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test](../observations/BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test/observation.md) (F-1, F-3; Zähler 9), [BEO-TDS/aussagen-ueber-messung-hinaus](../observations/BEO-TDS/aussagen-ueber-messung-hinaus/observation.md) (F-2, F-5, F-6; Zähler 35), [BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen](../observations/BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen/observation.md) (Risiko 2; Zähler 7), [BEO-TDS/messung-am-echten-sql-server-fehlt](../observations/BEO-TDS/messung-am-echten-sql-server-fehlt/observation.md) (Risiken 1 und 4; Zähler 21), [BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor](../observations/BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor/observation.md) (L-2; Zähler 4) und [BEO-TDS/test-stage-cache-verdeckt-wiederholung](../observations/BEO-TDS/test-stage-cache-verdeckt-wiederholung/observation.md) (Review; Zähler 5). Der Stand (`state.md`) ist für die vier Einträge über der Schwelle und ohne Ausgang nachgezogen (`test-belegt-nur-den-fake`, `zeile-oder-zweig-ohne-unterscheidenden-test`, `abdeckungsmatrix-veraltet-ohne-sensor`, `rueckstau-im-produktivbetrieb-nicht-gemessen`). Keine neue Beobachtung angelegt: jede Klasse war vorhanden. Nicht angefasst, trotz Nennung im Plan: `BEO-TDS/schranken-test-mit-ungeprueftem-zaehler` (der Zähler des Heap-Tests hat eine Unter- und eine Obergrenze, es trat keine Wiederkehr auf), `BEO-TDS/strukturtest-ohne-variation-der-eingabe` und `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` (keine Wiederkehr gefunden).
- **Folge-Slices:** keine neuen. Das hängende `next()` einer Quelle und die Frist `deadlineEpochMillis` trägt [slice-attention-cancel-timeout](../open/slice-attention-cancel-timeout.md) (Test „Backend ignoriert den Hinweis“ und `CANCEL_TIMEOUT`); den Referenzclient-Lauf am Simulations-Backend trägt [slice-sim-aenderung-integration](../open/slice-sim-aenderung-integration.md); die Messung am echten Server trägt [slice-qa-vergleichslauf-d](../open/slice-qa-vergleichslauf-d.md), das die Punkte dieses Slice nicht in seiner DoD führt (im Register geführt).
- **Drei Paarungen:** Anker: der Eintrag trägt kein `liegt in`, die Paarung entfällt. Folge-Slice: [slice-attention-cancel-timeout](../open/slice-attention-cancel-timeout.md), [slice-sim-aenderung-integration](../open/slice-sim-aenderung-integration.md) und [slice-qa-vergleichslauf-d](../open/slice-qa-vergleichslauf-d.md) existieren als Datei in `open/`. Register: jede genannte Kennung ist ein Verzeichnis mit nicht leerem `evidence/`.
- **Startbarkeit des Nachfolgers:** Der Start-Trigger von [slice-sim-aenderung-integration](../open/slice-sim-aenderung-integration.md) nennt sechs Bedingungen: [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) `Accepted`, [slice-sim-erkennung](../done/slice-sim-erkennung.md), slice-sim-auswahl-streaming, [slice-qa-dotnet-referenztest-geruest](../done/slice-qa-dotnet-referenztest-geruest.md), [slice-bootstrap-konfiguration](../done/welle-handshake-login/slice-bootstrap-konfiguration.md) und [slice-sql-batch-dispatch](../done/slice-sql-batch-dispatch.md) in `done/`, dazu ein Verantwortlicher im Kopf. Mit diesem Move liegen alle fünf Slices in `done/`; **offen bleibt allein der Verantwortliche** (der Kopf trägt „— bis zur Priorisierung.“). Er ist startbar, sobald ein Verantwortlicher eingetragen und der Slice nach `next/` und `in-progress/` bewegt ist. **Die Welle [welle-simulations-backend](../welle-simulations-backend.md) ist danach nicht komplett**: ihr vierter und letzter Slice, [slice-sim-aenderung-integration](../open/slice-sim-aenderung-integration.md), liegt in `open/`; die Wellen-Closure folgt erst nach ihm.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`). Das Package `simulation` ist ein eigener Pfad-Cluster (Achse 3) mit Importgrenze im Gate (`ForbiddenImport/simulation` in `config/detekt/detekt.yml`; Achse 1 plausibel) und mit einer Doku-Aussage (SPEC-036), die gegen den Code abgleichbar ist (Achse 2 plausibel). Die Schwelle wäre damit erreicht; weil der Modus Greenfield derselbe bliebe und eine eigene Zeile in `harness/conventions.md` keine andere Regel auslöste, bleibt `*`. Eine Ausdifferenzierung ist Sache des Nutzers und hier nicht vorgenommen.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, 19 Beobachtungen, Stand des Worktrees auf `main`); alle tragen die Sub-Area `*`. Treffer mit Zähler-Stand (Zahl der Dateien unter `evidence/`):

- `BEO-TDS/aussagen-ueber-messung-hinaus` — 16×, Stand *verkörpert* (`AGENTS.md` §3.8, Reviewer-Skill). Betrifft diesen Slice: Jede Aussage über das Lesen und die Speichergrenze nennt ihre Messbedingung.
- `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` — 1×, offen. Betrifft die Heap-Aussage (Testlauf, nicht Produkt, §6).
- `BEO-TDS/schranken-test-mit-ungeprueftem-zaehler` — 1×, offen. Betrifft den Nachweis „höchstens so viel gelesen“: ein Zähler mit Untergrenze (§2).
- `BEO-TDS/strukturtest-ohne-variation-der-eingabe` — 1×, offen. Betrifft die Bedingungstests: Eingabe variieren (§6).
- `BEO-TDS/test-stage-cache-verdeckt-wiederholung` — 2×, offen. Betrifft den Heap-Lauf in einer Docker-Stage: ein gecachter grüner Lauf belegt die Stabilität nicht erneut; die Closure nennt `--no-cache-filter test` für die Wiederholung.
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — 1×, offen. Slice und Closure-Notiz nennen `ADR-*`- und `TDS-*`-Kennungen als Link.
- Ohne Treffer: `BEO-TDS/messung-am-echten-sql-server-fehlt`, `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`, `BEO-TDS/vergleichslauf-d-ungeklaert`, `BEO-TDS/offene-punkte-ohne-traeger`, `BEO-TDS/spec-einzelfaelle-ohne-lastenheft-deckung`, `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut`, `BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen`, `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor`, `BEO-TDS/ablage-im-repo-bricht-docs-check`, `BEO-TDS/katalog-und-lock-ohne-gegenpruefung`, `BEO-TDS/java-quelle-umgeht-kotlin-regel`, `BEO-TDS/betreiberanforderung-benutzerverwaltung-fehlt`, `BEO-TDS/adapter-festlegungen-ohne-slice`.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
