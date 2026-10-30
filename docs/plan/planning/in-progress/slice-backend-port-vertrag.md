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

- [ ] **Port-Typen** ([TDS-FA-08.a](../../../../spec/spezifikation.md#tds-fa-08a--backend-vertrag), SPEC-002): `QueryBackend`, `QueryExecution`, `QueryRequest`, `QueryParameter`, `ParameterDirection`, `QueryEvent` (sieben Ereignisse), `ResultColumn`, `SqlType` mit dem Mindestkatalog von `SPEC-003` (Integer 32/64, Boolean, Unicode-Text, Decimal mit Precision und Scale, Datum, Zeit, Datum mit Zeit); `make detekt` hält die Schichtenregeln (Port importiert nur `domain`, Standardbibliothek und Laufzeitbibliothek für asynchrone Streams); `SPEC-002` und `contracts/query-backend.md` stehen auf dem gebauten Stand (der Typ `SqlType` ist dort bisher nicht definiert), Rest bleibt als „Entwurf“ gekennzeichnet.
- [ ] **Test-Backend** (Test-Quelltext, nicht Produktionscode): liefert Ergebnisse aus einer Liste oder einem Generator als `Flow<QueryEvent>`, ist skriptbar (Ereignisfolge, `Failure` vor und nach `ResultStart`, Fehler beim Start als `Failure` im Strom) und zählt, wie viele Ereignisse es geliefert hat und wie oft `close()` und `cancel()` kamen; es ist suspendierend und importiert nichts aus `protocol`, `transport`.
- [ ] **Vertragstest** ([TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), `SPEC-002`): Happy (Ergebnis aus fertiger Liste), Boundary (leeres Resultset mit Metadaten), Negative (Backend-Fehler als `Failure`-Ereignis; die Form eines Fehlers beim Start nach Entscheidung aus §6), Metadaten vor der ersten Zeile, `close()` und `cancel()` erreichen die Zeilenquelle; das Test-Backend läuft über den Port ohne TDS-Bytes ([TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), Happy Path: „ohne TDS-spezifische Abhängigkeit“).

Nachweis-Pflichten für alle drei Punkte:

- [ ] Jeder Test trägt im KDoc über der Testfunktion den Block-Tag `@spec SPEC-<NNN>`; die Tests laufen in `make test` und sind grün.
- [ ] Jede Aussage im Quelltext (KDoc) und in Spec oder Vertrag steht an einem Test oder ist als Festlegung gekennzeichnet; die Closure-Notiz trennt *gemessen*, *Handvektor*, *Selbstkonsistenz* und *Festlegung* wie `slice-codec-tds-paket` (`BEO-TDS/aussagen-ueber-messung-hinaus`). Die Messung in [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) gilt für den Spike, nicht für den Quelltext dieses Slice.
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

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

- **Ort der Modelltypen — Entschieden (Aufrufer, 2026-10-30; Setzung, nicht gemessen):** Ereignis-, Anfrage- und Typmodelle (`QueryRequest`, `QueryParameter`, `ParameterDirection`, `QueryEvent`, `ResultColumn`, `SqlType`) in `domain`, die Schnittstellen `QueryBackend` und `QueryExecution` mit `Flow` in `port`. Herleitung: `ARC-001` (`domain`) führt „Ergebnis-, Fehler- und Typmodelle“, `contracts/query-backend.md` und `ARC-006` nennen den Port; `domain` darf keine Fremdbibliothek importieren, `Flow` braucht `port`. — **Ausgang:** wird bei der Closure gesetzt.
- **Fehlerform beim Start — Entschieden (Aufrufer, 2026-10-30; Setzung, nicht gemessen):** `execute()` wirft für Fehler des Backends keine Ausnahme, ein Fehler beim Start ist ein `Failure` im Strom `events`; die Abbruchausnahme der Coroutine bleibt unberührt. Herleitung: [TDS-FA-08.a](../../../../spec/spezifikation.md#tds-fa-08a--backend-vertrag) nennt „Ergebnisstream oder typisierter Fehler“; [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) setzt `Failure` im Strom, sagt aber nicht, ob `execute()` selbst eine Ausnahme werfen darf (Backend nicht erreichbar, [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung) Negative). — **Ausgang:** wird bei der Closure gesetzt.
- **Statement- und batch-abbrechende Fehler** (R04 gegen R05 bis R07 in [ADR-0007](../../adr/0007-fehler-und-attention.md)) sind im Port nicht darstellbar, weil `Failure` keine Fehlerklasse trägt (Zuständigkeit laut [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md): Backend-/Mapping-Slice, nach Messung am echten Server). Dieser Slice führt keine Fehlerklasse ein, wenn der Nutzer sie nicht entscheidet. — **Ausgang:** weiter offen: → `slice-backend-sequenzierer` §6 (Mapping-Slice), dort Entscheidung oder Übergabe an `welle-fehler-abbruch`.
- **`SqlType` ist in `SPEC-002` nicht definiert** (benannte Spec-Lücke): Umfang hier = Mindestkatalog `SPEC-003`; Breiten unter 32 Bit, Gleitkomma, Geld, GUID, Binär gehören nicht dazu. — **Ausgang:** offen bis zur Closure; Erweiterungsweg über `slice-codec-datentypen-erweiterung`.
- **Zeilendarstellung `List<Any?>`:** typunsicher, der Spike maß rund 150 Byte je Zeile bei drei Spalten ([ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)); der Port übernimmt die Skizze, entschieden wird die Darstellung nicht. — **Ausgang:** weiter offen: → `slice-adr-backend-grenzen` (S1; Heap-Grenze eines materialisierenden Backends, auch in `slice-backend-streaming-nachweis` §6).
- **Test-Backend als Vorwegnahme des Simulations-Backends:** wächst der Fake um Anweisungserkennung oder Tabellen, ist es [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend). — **Ausgang:** offen bis zur Closure; Grenze in §1.
- **Aussagen über das Backend-Verhalten:** Der Vertragstest läuft an einem suspendierenden Fake; Aussagen zu blockierenden Backends, zum Cancel-Verhalten echter Treiber und zur Frist stehen nirgends als gemessen (`BEO-TDS/aussagen-ueber-messung-hinaus`); die Entscheidung zu blockierendem Backend und Frist trägt `slice-adr-backend-grenzen` (S3). — **Ausgang:** offen bis zur Closure.

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

Wird bei der Closure gefüllt.

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
