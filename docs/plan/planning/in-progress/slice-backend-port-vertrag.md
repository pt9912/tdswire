# Slice slice-backend-port-vertrag: Backend-Port als Kotlin-Schnittstelle mit minimalem Test-Backend und Vertragstest

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-backend-streaming

**Bezug:** [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)

**Berührte Spec-Stellen:** SPEC-002 · SPEC-022 · ARC-001 · ARC-006 · ARC-015 (nur Ereignistypen)

**Verantwortlich:** pt9912

**Autor:** pt9912. **Datum:** 2026-10-30.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Der Backend-Port ([TDS-FA-08.a](../../../../spec/spezifikation.md#tds-fa-08a--backend-vertrag), SPEC-002) steht als Kotlin-Quelltext: `QueryBackend`, `QueryExecution`, `QueryRequest`, `QueryParameter`, `QueryEvent`, `ResultColumn` und das Backend-Typmodell `SqlType`, ohne Import aus `protocol` oder `transport`; ein minimales Test-Backend im Test-Quelltext und ein Vertragstest belegen die Pflichten aus `contracts/query-backend.md`, die ohne Sequenzierer prüfbar sind (Metadaten vor Zeilen, leeres Ergebnis mit Metadaten, Fehler als Ereignis, Schließen der Ausführung).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Simulations-Backend, Tabellenbeschreibung, Zeilenquelle nach `SPEC-035`, Anweisungserkennung ([TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend)) — Welle [welle-simulations-backend](../welle-simulations-backend.md); hier nur ein Fake im Test-Quelltext, damit er nicht zum Produktionsbestandteil wird.
- Sequenzierer, Token-Abbildung, `DONE_MORE`-Vorschau — `slice-backend-sequenzierer`; der Port kennt kein `DONE_MORE` und keine Tokens.
- Typabbildung auf TDS-Typen — `slice-backend-typabbildung`; der Port definiert nur das Backend-Typmodell, nicht seine Abbildung.
- Unterscheidung statement- und batch-abbrechender Fehler im Strom: [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) führt sie offen; das `Failure`-Ereignis bleibt wie in `SPEC-002` skizziert (`code`, `message`) und trägt keine Fehlerklasse. Ein Eingriff wäre eine Entscheidung des Nutzers, kein Quelltext-Detail (§6).
- Durchsetzung der Frist `deadlineEpochMillis`, Grenze für „kleine“ Ergebnisse, kompaktere Zeilendarstellung, Behandlung von `OutOfMemoryError`: in [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) offen, Träger der Entscheidung ist `slice-adr-backend-grenzen`; das Feld und `Row(values: List<Any?>)` werden wie skizziert übernommen, entschieden wird nichts.
- Blockierendes Backend hinter dem `Flow`: nicht gemessen ([ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)); das Test-Backend ist suspendierend.
- Koordination mit der Anfrageseite (`slice-codec-rpc-parser`) — er liefert Parameter in eigenem Ergebnismodell; die Übersetzung in `QueryParameter` gehört zur Anfrageverarbeitung (`welle-anfrageverarbeitung`).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [x] **Port-Typen** ([TDS-FA-08.a](../../../../spec/spezifikation.md#tds-fa-08a--backend-vertrag), SPEC-002): `QueryBackend`, `QueryExecution`, `QueryRequest`, `QueryParameter`, `ParameterDirection`, `QueryEvent` (sieben Ereignisse), `ResultColumn`, `SqlType` mit dem Mindestkatalog von `SPEC-003` (Integer 32/64, Boolean, Unicode-Text, Decimal mit Precision und Scale, Datum, Zeit, Datum mit Zeit); `make detekt` hält die Schichtenregeln (Port importiert nur `domain`, Standardbibliothek und Laufzeitbibliothek für asynchrone Streams); `SPEC-002` und `contracts/query-backend.md` stehen auf dem gebauten Stand (der Typ `SqlType` ist dort bisher nicht definiert), Rest bleibt als „Entwurf“ gekennzeichnet.
- [x] **Test-Backend** (Test-Quelltext, nicht Produktionscode): liefert Ergebnisse aus einer Liste oder einem Generator als `Flow<QueryEvent>`, ist skriptbar (Ereignisfolge, `Failure` vor und nach `ResultStart`, Fehler beim Start als `Failure` im Strom) und zählt, wie viele Ereignisse es geliefert hat und wie oft `close()` und `cancel()` kamen; es ist suspendierend und importiert nichts aus `protocol`, `transport`.
- [x] **Vertragstest** ([TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), `SPEC-002`): Happy (Ergebnis aus fertiger Liste), Boundary (leeres Resultset mit Metadaten), Negative (Backend-Fehler als `Failure`-Ereignis; die Form eines Fehlers beim Start nach Entscheidung aus §6), Metadaten vor der ersten Zeile, `close()` und `cancel()` erreichen die Zeilenquelle; das Test-Backend läuft über den Port ohne TDS-Bytes ([TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), Happy Path: „ohne TDS-spezifische Abhängigkeit“).

Nachweis-Pflichten für alle drei Punkte:

- [x] Jeder Test trägt im KDoc über der Testfunktion den Block-Tag `@spec SPEC-<NNN>`; die Tests laufen in `make test` und sind grün.
- [x] Jede Aussage im Quelltext (KDoc) und in Spec oder Vertrag steht an einem Test oder ist als Festlegung gekennzeichnet; die Closure-Notiz trennt *gemessen*, *Handvektor*, *Selbstkonsistenz* und *Festlegung* wie `slice-codec-tds-paket` (`BEO-TDS/aussagen-ueber-messung-hinaus`). Die Messung in [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) gilt für den Spike, nicht für den Quelltext dieses Slice.
- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
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
| `src/main/kotlin/io/github/pt9912/tdswire/port/` | neu | `QueryBackend`, `QueryExecution`; `kotlinx-coroutines-core` (`Flow`) für den Strom |
| `src/main/kotlin/io/github/pt9912/tdswire/domain/` (Ort der Modelltypen nach Entscheidung aus §6; ersetzt `Placeholder.kt`) | neu | `QueryRequest`, `QueryParameter`, `QueryEvent`, `ResultColumn`, `SqlType`, ohne Netzwerk- und Bibliotheksabhängigkeit |
| `src/test/kotlin/io/github/pt9912/tdswire/port/` | neu | Test-Backend (skriptbar, zählend) und Vertragstest nach [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung) (Happy, Boundary, Negative), `@spec`-Tag je Test |
| `spec/spezifikation.md`, `contracts/query-backend.md` | update | `SPEC-002`/`SPEC-022` auf den gebauten Stand: `SqlType` definiert, Ort der Typen, Fehlerform; Entwurfs-Aussagen, die der Quelltext nicht trägt, bleiben Entwurf |
| `config/detekt/detekt.yml` | nur prüfen | die Importregeln für `port` und `domain` stehen; eine Lockerung ist ohne ADR nicht zulässig (`AGENTS.md` §3.6) |

- Das Test-Backend liegt im Package `port` des Test-Quellsatzes, nicht in `testsupport/`. Belegt in `config/detekt/detekt.yml`: `ForbiddenImport/port` hat `includes` auf `**/port/**` und gilt nach dem Kommentar der Datei für Main- und Test-Quellsatz; das Test-Backend ist damit an die Erlaubnisliste (`kotlin`, `kotlinx.coroutines`, `java.*`-Auswahl, `domain`, `port`) gebunden, und „importiert nichts aus `protocol`, `transport`“ setzt das Gate durch. Für ein Package `testsupport` führt die Datei keine Regel; dort gäbe es diese Bindung nicht. Eine Importregel, die den Ort erzwänge, gibt es nicht; es ist eine Wahl des Implementers, die Wirkung ist belegt. Folge: ein Test aus einem anderen Package, der das Test-Backend nutzt, importiert es aus `port`.
- Reihenfolge: Typen und Schnittstellen, dann Test-Backend, dann Vertragstest.
- Der Implementer liest die Importregeln in `config/detekt/detekt.yml` (Abschnitte `domain` und `port`) und passt diese Tabelle an.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) steht auf `Accepted`, der Slice `slice-transport-coroutines-geruest` (Pin von `kotlinx-coroutines-core` in `gradle/libs.versions.toml`, `gradle.lockfile` und `gradle/verification-metadata.xml`) liegt in `done/` (erfüllt: der Pin steht), `make gates` ist auf `main` grün, die Nutzerentscheidungen zum Ort der Typen (`domain` oder `port`) und zur Fehlerform beim Start (§6) liegen vor, und ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2; Schnittvorschlag: Slice A = Typen und Schnittstellen, Slice B = Test-Backend und Vertragstest.
- `in-progress` → `open` (blockiert — Carveout?): [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) wird nicht `Accepted` oder ändert die Option (neuer Ergebnisvertrag); oder der Pin von `kotlinx-coroutines-core` ist nicht gesetzt und `make deps-lock` scheitert.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün, der Review-Report liegt vor, und die Closure-Notiz trägt die Zuordnung je Aussage des Vertragstests sowie den Steering-Loop-Lerneintrag; `SPEC-002` und `contracts/query-backend.md` nennen den gebauten Stand.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **Ort der Modelltypen — Entschieden (Aufrufer, 2026-10-30; Setzung, nicht gemessen):** Ereignis-, Anfrage- und Typmodelle (`QueryRequest`, `QueryParameter`, `ParameterDirection`, `QueryEvent`, `ResultColumn`, `SqlType`) in `domain`, die Schnittstellen `QueryBackend` und `QueryExecution` mit `Flow` in `port`. Herleitung: `ARC-001` (`domain`) führt „Ergebnis-, Fehler- und Typmodelle“, `contracts/query-backend.md` und `ARC-006` nennen den Port; `domain` darf keine Fremdbibliothek importieren, `Flow` braucht `port`. — **Ausgang:** entfallen: entschieden (Aufrufer, 2026-10-30) und umgesetzt; `domain` trägt die Modelltypen, `port` die Schnittstellen, der Layerwächter `ForbiddenImport` hält es.
- **Fehlerform beim Start — Entschieden (Aufrufer, 2026-10-30; Setzung, nicht gemessen):** `execute()` wirft für Fehler des Backends keine Ausnahme, ein Fehler beim Start ist ein `Failure` im Strom `events`; die Abbruchausnahme der Coroutine bleibt unberührt. Herleitung: [TDS-FA-08.a](../../../../spec/spezifikation.md#tds-fa-08a--backend-vertrag) nennt „Ergebnisstream oder typisierter Fehler“; [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) setzt `Failure` im Strom, sagt aber nicht, ob `execute()` selbst eine Ausnahme werfen darf (Backend nicht erreichbar, [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung) Negative). — **Ausgang:** entfallen: entschieden (Aufrufer, 2026-10-30) und umgesetzt (`Failure` im Strom, Setzung in KDoc und Vertrag); am Test-Backend belegt, am Port nicht erzwingbar (§7).
- **Statement- und batch-abbrechende Fehler** (R04 gegen R05 bis R07 in [ADR-0007](../../adr/0007-fehler-und-attention.md)) sind im Port nicht darstellbar, weil `Failure` keine Fehlerklasse trägt (Zuständigkeit laut [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md): Backend-/Mapping-Slice, nach Messung am echten Server). Dieser Slice führt keine Fehlerklasse ein, wenn der Nutzer sie nicht entscheidet. — **Ausgang:** weiter offen: → `slice-backend-sequenzierer` §6 (Mapping-Slice), dort Entscheidung oder Übergabe an `welle-fehler-abbruch`.
- **`SqlType` ist in `SPEC-002` nicht definiert** (benannte Spec-Lücke): Umfang hier = Mindestkatalog `SPEC-003`; Breiten unter 32 Bit, Gleitkomma, Geld, GUID, Binär gehören nicht dazu. — **Ausgang:** entfallen: `SPEC-002` definiert `SqlType` seit dem Merge `f42a18f`; die verbliebenen Modellgrenzen (Zeit-Scale, Länge von `UnicodeText`, Decimal-Obergrenze, JVM-Typen je `SqlType`) sind eigene Punkte in `slice-backend-typabbildung` §6, der Erweiterungsweg bleibt `slice-codec-datentypen-erweiterung`.
- **Zeilendarstellung `List<Any?>`:** typunsicher, der Spike maß rund 150 Byte je Zeile bei drei Spalten ([ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)); der Port übernimmt die Skizze, entschieden wird die Darstellung nicht. — **Ausgang:** weiter offen: → `slice-adr-backend-grenzen` (S1; Heap-Grenze eines materialisierenden Backends, auch in `slice-backend-streaming-nachweis` §6).
- **Test-Backend als Vorwegnahme des Simulations-Backends:** wächst der Fake um Anweisungserkennung oder Tabellen, ist es [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend). — **Ausgang:** entfallen: nicht eingetreten; der Fake erkennt keine Anweisung und kennt keine Tabellen (Verifikation §6), die Grenze steht in §1.
- **Aussagen über das Backend-Verhalten:** Der Vertragstest läuft an einem suspendierenden Fake; Aussagen zu blockierenden Backends, zum Cancel-Verhalten echter Treiber und zur Frist stehen nirgends als gemessen (`BEO-TDS/aussagen-ueber-messung-hinaus`); die Entscheidung zu blockierendem Backend und Frist trägt `slice-adr-backend-grenzen` (S3). — **Ausgang:** weiter offen: → `slice-adr-backend-grenzen` (S3) und Register `BEO-TDS/aussagen-ueber-messung-hinaus` (Evidence `slice-backend-port-vertrag`).

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

- **Was hat funktioniert:** Der Schnitt trug: Typen und Schnittstellen, Test-Backend und Vertragstest lagen in einem Slice, ohne dass die Rückführung nach §4 nötig wurde. Die Schichtgrenze ist durch das Gate belegt, nicht durch Lesen: ein verbotener Import in `port/` macht `ForbiddenImport/port` rot (Verifikation M6), und das Test-Backend im Package `port` bindet dieselbe Regel (§3). Die Erschöpfung der sieben Ereignisse sichert der Compiler (ein achter Untertyp bricht die Übersetzung, M5). Die Verifikation fuhr 15 Mutationen, alle erkannt; nach den Befunden sah der Implementer vier weitere Mutationen an der neuen Suite rot (Angabe im Handoff, die Liste liegt nicht im Repo).
- **Was ging anders als geplant:** (a) **Hinweis zur Reichweite: Der Port hat keine Logik, nur Signaturen.** Greifende Wächter sind der Schichtwächter `ForbiddenImport/port` und die Erschöpfung zur Übersetzungszeit; die Tests belegen keinen Port, sondern Zusagen, die ein Backend einhalten soll. (b) Der Vertragstest belegte zunächst nur das Test-Backend: vier Tests gaben das Skript des Fakes zurück, der Sieben-Ereignisse-Test zählte eine selbst gebaute Liste (Review M2, L3; Verifikation V3). Behoben durch die abstrakte Suite `QueryBackendContract` (das Backend ist Parameter) mit `ScriptedBackendContractTest` als erster Implementierung, `ScriptedBackendTest` für die Eigenschaften des Fakes und eine Zuordnung von Ereignis zu Untertyp statt der Zählung (Merge `859e455`). (c) `SPEC-002` und `SPEC-022` sagten weiter „kein implementierter Code“ und kannten `SqlType` nicht (Review M1, V1); nachgeführt im Merge `f42a18f`, zusammen mit dem Slice-Plan: Ort des Test-Backends (`port/` statt `testsupport/`, mit der Begründung aus §3), „Fehler beim Start“ als `Failure` im Strom statt Ausnahme, die beiden Setzungen des Aufrufers in §6 (V2). (d) KDoc-Befunde (V5 bis V10, Review I4, I5, L2): `OutOfMemoryError` steht jetzt als offen im KDoc, `failingAtStart` ist gestrichen (gleich `ofList`), der Strom wird „genau einmal gesammelt“ (Festlegung), `ResultEnd.affectedRows` ist als Festlegung gekennzeichnet, die `@spec`-Tags sind begrenzt. (e) **Der Widerspruch bleibt:** `SqlType.Decimal` nimmt jede Precision ab 1 an, der Codec (`TypeInfoWriter.MAX_PRECISION`) 1 bis 38; ob das Modell eine Obergrenze bekommt oder die Abbildung einen Fehlerwert liefert, ist eine Nutzerentscheidung, geführt in `slice-backend-typabbildung` §6.
- **Reports:** [Review](../../../reviews/2026-10-30-0d3f90c-slice-backend-port-vertrag.md) (kein HIGH; M1, M2 MEDIUM; L1 bis L3 LOW; I1 bis I7 INFO) und [Verifikation](../../../reviews/2026-10-30-0d3f90c-verifikation-slice-backend-port-vertrag.md) (kein HIGH; V1 bis V4 MEDIUM, V5 bis V10 LOW; 15 Mutationen), beide gegen den Merge `0d3f90c`. Alle MEDIUM sind eingearbeitet: M1, V1, V2 und L1 in der Spec und im Slice-Plan (`f42a18f`), M2, L3, V3 sowie die KDoc-Befunde im Quelltext (`859e455`); V4 (Gate-Lauf wegen HTTP 429 der Registry nicht bestätigt) ist mit dem grünen `make gates` auf dem Closure-Stand erfüllt, der `test`-Schritt kam dabei aus dem Image-Cache (gleicher Quelltext, kein neuer Gradle-Lauf). Ein Validator-Lauf entfällt: der Slice liefert keinen End-Nutzer-Wert, außerhalb der Tests ruft bisher niemand den Port auf.

**Zuordnung Test | Festlegung je Aussage.** Vier Stärken: *gemessen* heißt an einem echten Backend oder Server beobachtet; *Selbstkonsistenz* heißt hier: gegen das Test-Backend `ScriptedBackend` im Test-Quelltext, also Code gegen Code; *Festlegung ohne Messung* heißt Setzung ohne Messung; *nicht gemessen* nennt, was keine Aussage trägt.

| Stärke | Aussage | Beleg im Repo |
|---|---|---|
| gemessen | **nichts.** Kein Backend, kein SQL Server und kein Treiber wurden beobachtet; die Messung in [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) gilt für den Spike, nicht für diesen Quelltext | — |
| Selbstkonsistenz (am Test-Backend) | Metadaten vor Zeilen (`ResultStart` kommt vor der ersten Zeile, auch bei einem Generator); leeres Ergebnis trägt Metadaten und keine Zeile; `Failure` nach Zeilen und vor `ResultStart` ist ein Ereignis im Strom; ein Fehler beim Start erscheint als einziges `Failure` im Strom und wirft nicht; der Generator läuft nicht über die Abnahme hinaus; `close()` und `cancel()` erreichen die Zeilenquelle | `QueryBackendContract` (acht Tests, `@spec SPEC-002`/`SPEC-031`), ausgeführt von `ScriptedBackendContractTest`; Mutationen der Verifikation M1, M2, M4, M7, M9, M10, M14 rot. Die Tests belegen, dass das Test-Backend die Zusage hält und der Port sie ausdrücken lässt, nicht dass ein anderes Backend sie hält; ein künftiges Backend durchläuft die Suite, indem es sie erbt |
| Selbstkonsistenz | Eigenschaften des Fakes: er protokolliert Anfrage, gelieferte Ereignisse, `close` und `cancel` | `ScriptedBackendTest` (zwei Tests); Verifikation M7, M10 rot |
| Selbstkonsistenz (Modell) | Decimal akzeptiert Precision und Scale des Mindestkatalogs und die Ränder von Scale und lehnt ungültige Werte ab; ein Parameter ohne Richtung ist `INPUT`; ein Ereignis ist seinem Untertyp zugeordnet | `BackendModelTest` (fünf Tests); Verifikation M3, M11, M12 rot |
| Selbstkonsistenz (Compiler) | Die Untertyp-Menge von `QueryEvent` ist erschöpfend (sieben Ereignisse); ein achter Untertyp bricht die Übersetzung | `BackendModelTest` (Zuordnung per erschöpfendem `when`); Verifikation M5 als Übersetzungsfehler. Das ist ein Wächter zur Übersetzungszeit, kein Testlauf |
| Selbstkonsistenz (Gate) | Der Port importiert nur `domain`, die Standardbibliothek und `kotlinx.coroutines.flow` | `make detekt` (`ForbiddenImport/port`); Verifikation M6 rot, `config/detekt/detekt.yml` unverändert |
| Festlegung ohne Messung | Die Orte der Typen: Modelltypen in `domain`, Schnittstellen in `port` (Aufrufer, 2026-10-30) | KDoc, `SPEC-002`, `contracts/query-backend.md`; die Schichtregel hält die Importrichtung, nicht die Wahl |
| Festlegung ohne Messung | `execute()` wirft für Fehler des Backends nicht; ein Fehler beim Start ist ein `Failure` im Strom (Aufrufer, 2026-10-30) | KDoc, Vertrag; der Test belegt es am Fake, der Port kann es nicht erzwingen |
| Festlegung ohne Messung | Der Umfang von `SqlType` (Mindestkatalog `SPEC-003`); `Decimal` verlangt `precision >= 1` und `0 <= scale <= precision`; `DateTime` trägt keine Zone | KDoc von `SqlType`, `SPEC-002`; die Decimal-Grenzen belegen zwei Tests, der Tag `SPEC-003` deckt davon nur Precision und Scale |
| Festlegung ohne Messung | `deadlineEpochMillis` gleich `null` heißt: keine Frist; der Strom wird genau einmal gesammelt (kalt oder fortsetzend legt der Port nicht fest); `ResultEnd.affectedRows` ist `null`, wenn das Backend keine Zahl liefert | KDoc von `QueryRequest`, `QueryExecution`, `QueryEvent`; kein Test |
| nicht gemessen | Ein blockierendes Backend hinter dem `Flow`, das Cancel-Verhalten echter Treiber, die Durchsetzung der Frist, `OutOfMemoryError` bei einem materialisierenden Backend, die Heap-Grenze von `List<Any?>`, ein Backend, das die Ereignisreihenfolge verletzt | Träger unten; Register `BEO-TDS/aussagen-ueber-messung-hinaus` |

**Offene Punkte der Closure mit Träger** (gegen den Inhalt der Dateien geprüft; jeder Träger existiert als Datei im Planning-Lifecycle und führt den Punkt in seiner DoD oder seinem Risiko):

| Offener Punkt | Träger | Stand |
|---|---|---|
| `Failure` in der `DONE_MORE`-Vorschau | `slice-backend-sequenzierer` (`open/`) | §6 führt die Frage als Entscheidungsbedarf (Nutzer) |
| statement- und batch-abbrechende Fehler (R04 gegen R05 bis R07, [ADR-0007](../../adr/0007-fehler-und-attention.md)) | `slice-backend-sequenzierer` (`open/`) | §6 führt die Unterscheidung; der Port trägt keine Fehlerklasse |
| Grenze „kleines Ergebnis“ und Zeilendarstellung `List<Any?>` | `slice-adr-backend-grenzen` (`open/`), S1 | DoD und §3 S1; auch in `slice-backend-streaming-nachweis` §6 |
| `OutOfMemoryError` | `slice-adr-backend-grenzen` (`open/`), S2 | DoD und §3 S2; `slice-backend-streaming-nachweis` §6 verweist |
| blockierendes Backend und Frist `deadlineEpochMillis` | `slice-adr-backend-grenzen` (`open/`), S3 | DoD und §3 S3; `slice-backend-streaming-nachweis` §6 verweist |
| JVM-Typen der Werte je `SqlType` (Zeilendarstellung) | `slice-backend-typabbildung` (`open/`) | §6, seit dieser Closure (vorher ohne Träger) |
| Decimal-Precision-Obergrenze (Modell ohne Grenze gegen `MAX_PRECISION` 38; Nutzerentscheidung) | `slice-backend-typabbildung` (`open/`) | §6, seit dieser Closure; die Rückführungs-Zeile in §4 ist angepasst |
| Genauigkeit der Zeittypen, Zeitzonenannahme, Länge von `UnicodeText` (im Modell nicht darstellbar bzw. offen) | `slice-backend-typabbildung` (`open/`) | §6 (Zeitzonen- und Genauigkeitsannahme; Modellgrenzen), DoD Metadatenabbildung (Scale 0 bis 7, Text bis 4000) |

- **Gegenstand:** entfällt (der Slice lieferte).
- **Steering-Loop-Eintrag:** Kein `liegt in`: mit diesem Slice ist keine Regel, kein Sensor und keine Gate-Zeile verkörpert worden; gezählt wird, nicht verkörpert. Drei Feststellungen. (1) **Ein Test gegen einen Port ohne Logik belegt nur den Fake.** Vier Vertragstests gaben das Skript zurück, das der Fake unverändert ausliefert, und kein anderes Backend konnte sie durchlaufen; die Verifikation fand das per Mutation am Fake gegen den Port (M8a gegen M8b und M8c). Die abstrakte Suite mit dem Backend als Parameter ist die Antwort an diesem Slice; als Prüffrage für den Reviewer („Welche Änderung am Produktionscode macht diesen Test rot?“) und Mutation für den Verifier ist sie noch keine Regel. Register: `BEO-TDS/test-belegt-nur-den-fake` (neu, 1×); nah verwandt, aber anderer Mechanismus: `BEO-TDS/strukturtest-ohne-variation-der-eingabe`. `BEO-TDS/schranken-test-mit-ungeprueftem-zaehler` trat nicht wieder auf: die Zähler des Fakes sind durch Mutationen (M7, M10) geprüft. (2) **Das Muster der Aussagen über den Beleg hinaus trat trotz der verkörperten Regel wieder auf** (`affectedRows`, `OutOfMemoryError` fehlte in der Liste, „ohne Zeitzone“ neben „offen“, `@spec`-Tag breiter als der Beleg, Spec „kein implementierter Code“): ein weiteres Auftreten von `BEO-TDS/aussagen-ueber-messung-hinaus`, keine Schärfung; `AGENTS.md` §3.8 war der Maßstab. (3) **Offene Punkte ohne Träger bei der Closure:** die Prüfung der Träger fand drei Punkte, die der Port offen lässt und kein Slice führte (Decimal-Obergrenze, Zeit-Scale und Text-Länge, JVM-Typen je `SqlType`); sie stehen jetzt in `slice-backend-typabbildung` §6. Ein weiteres Auftreten von `BEO-TDS/offene-punkte-ohne-traeger`; die Regel steht in `.claude/commands/implement-slice.md` (Schritt 24) und griff hier als Prüfung. **Benannte Spec-Lücke:** `SPEC-002` und `SPEC-003` lassen die Genauigkeit der Zeittypen, die Zeitzonenannahme und die Länge von `UnicodeText` offen; der Quelltext setzt sie nicht (`SqlType` trägt weder Scale noch Länge, `DateTime` keine Zone). Die Lücke ist benannt und hat einen Träger (`slice-backend-typabbildung`), aber noch keine Zeile in einer versionierten Spec; die Lücke zu `SqlType` selbst ist mit `f42a18f` geschlossen.
- **Beobachtungs-Register (`../observations/`):** `evidence/slice-backend-port-vertrag.md` ergänzt in `BEO-TDS/aussagen-ueber-messung-hinaus` (Review I4, I5, L2, M1; Verifikation V1, V5 bis V7, V9, V10) und in `BEO-TDS/offene-punkte-ohne-traeger`; eine neue Beobachtung mit dieser Evidence-Datei: `BEO-TDS/test-belegt-nur-den-fake` (Review M2, L3; Verifikation V3). Kein neues Auftreten: `BEO-TDS/messung-am-echten-sql-server-fehlt` (der Slice behauptet nichts über einen echten Server; die Grenze steht in der Zeile *nicht gemessen*), `BEO-TDS/schranken-test-mit-ungeprueftem-zaehler` und `BEO-TDS/strukturtest-ohne-variation-der-eingabe` (anderer Mechanismus, siehe oben), `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` (`make docs-check` ohne Befund) und die übrigen. Die Zähler folgen aus den Dateien; `aussagen-ueber-messung-hinaus` und `offene-punkte-ohne-traeger` stehen schon auf `verkörpert`, ihr `state.md` bleibt unverändert.
- **Risiken aus §6:** alle sieben tragen genau einen Ausgang (siehe §6): vier entfallen (zwei davon entschieden und umgesetzt), drei weiter offen mit Träger.
- **Drei Paarungen:** Anker: kein Eintrag trägt `liegt in`, die Paarung entfällt. Folge-Slice: die Träger in der Tabelle oben existieren als Dateien in `open/` und führen die Punkte in ihrem Inhalt. Register: drei Evidence-Dateien dieses Vorgangs, siehe oben.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); `port`, `protocol` und `domain` sind keine eigenen Pfad-Cluster mit eigener Strukturregel (die Schichten-Importregeln in `config/detekt/detekt.yml` sind Architektur-, keine Modus-Grenzen). Keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, acht Beobachtungen, alle Sub-Area `*`). Treffer: `BEO-TDS/aussagen-ueber-messung-hinaus` (7×, die Regel steht in `AGENTS.md` §3.8; sie bindet KDoc, Vertrag und Spec-Nachführung dieses Slice, Gegenmittel in §2) und `BEO-TDS/messung-am-echten-sql-server-fehlt` (3×; berührt nur mittelbar, weil dieser Slice keine Antwort eines echten Servers behauptet). Ohne Treffer: die übrigen sechs.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
