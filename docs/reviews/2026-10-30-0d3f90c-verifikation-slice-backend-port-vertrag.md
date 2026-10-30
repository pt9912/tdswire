# Verifikations-Report: slice-backend-port-vertrag — 2026-10-30

**Rolle:** Verifier (Modul 11, „Bauen wir es richtig?“ gegen Plan und DoD) · **Modell:** claude-sonnet-5-5

**Gegenstand:** Merge `0d3f90c` (Commits `16d62ec`, `b177a48`), Diff `025bb11..0d3f90c` über `src` und `contracts`. Slice: `docs/plan/planning/in-progress/slice-backend-port-vertrag.md` (Welle welle-backend-streaming). Bezug: [TDS-FA-08](../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-QA-05](../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md), [TDS-FA-08.a](../../spec/spezifikation.md#tds-fa-08a--backend-vertrag).

**Eingang:** DoD des Slice (§2) und der Review-Report `docs/reviews/2026-10-30-0d3f90c-slice-backend-port-vertrag.md`. Der Review hatte `make test` und `make gates` nicht ausgewertet; die Belege unten sind eigene Läufe, keine Übernahme.

**Stand des Slice:** alle DoD-Kästchen in §2 sind offen, §7 trägt „Wird bei der Closure gefüllt“. Der Slice ist nicht geschlossen; dieser Report liefert die Bestätigung, die die Closure braucht.

---

## 1. Eigene Läufe (Docker-only)

| Lauf | Ergebnis | Anmerkung |
|---|---|---|
| `make detekt` | grün (Exit 0) | Stage lag im Layer-Cache, also dasselbe `src`/`config` wie 0d3f90c im Arbeitsbaum; zusätzlich ein Lauf `detekt detektMain detektTest` im Container gegen den frischen Klon bei 0d3f90c: grün |
| `make detekt-selbsttest` | grün (Exit 0) | |
| `make docs-check` | grün, 208 Dateien, 0 Befunde | gelaufen vor dem Schreiben dieses Reports |
| `make test` | **nicht aus der Gate-Kette bestätigt** | `docker build --target test` scheitert wiederholt beim Auflösen von `kotlinx-coroutines-core:1.10.1`, `kotlin-stdlib:2.4.10` und `junit-platform-launcher` mit HTTP 429 von repo.maven.apache.org (Umgebung, kein Befund am Diff; mehrere Versuche über rund 20 Minuten) |
| Tests im Container, offline | grün | `gradle --no-daemon --offline test` im Image der Stage `test` (Lock und Prüfsummen des Repos, Abhängigkeiten aus dem Image-Cache von `025bb11`), `src` vom Klon bei 0d3f90c eingehängt: 9 Tests `QueryBackendContractTest` und 4 Tests `BackendModelTest` PASSED, 0 FAILED. Das ist dieselbe Gradle-Aufgabe wie in der Stage, aber nicht der Stage-Lauf |
| `make build` | **nicht bestätigt** | Stage `compile` scheitert an derselben 429; die Übersetzung des Hauptquelltexts lief im Offline-Lauf durch, das Jar nicht |
| `make gates` | **nicht gelaufen** | `test` und `build` hängen an der Registry; ein Gate-Nachweis (`record-gates`) entsteht nur aus grünen Checks, ihn zu stempeln wäre eine bleibende Änderung |

Folge: „`make gates` grün“ ist **nicht bestätigt**. Das ist ein offener Beleg, kein Befund am Code.

## 2. DoD Punkt für Punkt

### Liefer-Punkt 1 — Port-Typen

| Teilzusage | Bestätigt? | Beleg |
|---|---|---|
| `QueryBackend`, `QueryExecution` in `port` | bestätigt | `port/QueryBackend.kt` (`execute`, `cancel`, beide `suspend`), `port/QueryExecution.kt` (`events: Flow<QueryEvent>`, `suspend close()`); Signaturen stimmen mit der Skizze in [SPEC-002](../../spec/spezifikation.md#spec-002--interner-query-vertrag) überein |
| `QueryRequest`, `QueryParameter`, `ParameterDirection`, `ResultColumn` in `domain` | bestätigt | `domain/QueryRequest.kt`, `domain/QueryEvent.kt` (ResultColumn); Felder und Default `INPUT` wie Skizze; Test „Parameter ohne Richtung ist INPUT“ (Mutation M12 rot) |
| `QueryEvent` mit sieben Ereignissen | bestätigt | sealed interface mit `ResultStart`, `Row`, `ResultEnd`, `UpdateCount`, `ReturnStatus`, `OutputParameter`, `Failure`; ein achter Untertyp bricht die Übersetzung (Mutation M5) |
| `SqlType` mit Mindestkatalog [SPEC-003](../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog) | bestätigt | `Int32`, `Int64`, `Bool`, `UnicodeText`, `Decimal(precision, scale)`, `Date`, `Time`, `DateTime`; Decimal-Grenzen durch Mutationen M3 und M11 belegt |
| `make detekt` hält die Schichtenregeln, Port importiert nur `domain`, Standardbibliothek, Laufzeitbibliothek | bestätigt | detekt grün; Importzeilen in `port/` sind `domain.QueryRequest`, `domain.QueryEvent`, `kotlinx.coroutines.flow.Flow`; Mutation M6 (Import `protocol.wire.ByteReader` in `QueryBackend.kt`) macht `ForbiddenImport/port` rot; `config/detekt/detekt.yml` unverändert (`git diff 025bb11 0d3f90c`) |
| `contracts/query-backend.md` auf gebautem Stand | bestätigt | Abschnitt „Signatur“ (Ort der Typen, Fehler beim Start, `SqlType`-Liste, „Belegt am Test-Backend“) stimmt mit dem Quelltext überein; Rest bleibt „Entwurf“ gekennzeichnet |
| `SPEC-002` auf gebautem Stand | **nicht bestätigt** | `spec/` ist im Diff unverändert; siehe Finding V1 |
| Vertrag: Entwurf-Abgrenzung | bestätigt | Einleitung des Vertrags trennt „Kotlin-Quelltext“ von „Entwurf“; Abschnitte „Festgelegt“ und „Offen“ unverändert |

Urteil Punkt 1: **teilweise bestätigt** (Spec-Nachführung fehlt).

### Liefer-Punkt 2 — Test-Backend (`ScriptedBackend`)

| Teilzusage | Bestätigt? | Beleg |
|---|---|---|
| Test-Quelltext, nicht Produktionscode | bestätigt | `src/test/.../port/ScriptedBackend.kt`; nichts in `src/main` |
| Liste (`ofList`) | bestätigt | Test „Ergebnis aus fertiger Liste kommt vollstaendig an“ |
| Generator (`generating`) | bestätigt | Tests „ResultStart kommt vor…“, „Generator laeuft nicht ueber die Abnahme hinaus“, `close`, `cancel` |
| `Failure` vor und nach `ResultStart` | bestätigt | zwei Tests über `ofList` |
| „Ausnahme beim Start“ skriptbar | **abweichend** | Das Test-Backend kann keine Ausnahme aus `execute` werfen; `failingAtStart` liefert ein `Failure` im Strom. Das folgt der Setzung „`execute()` wirft nicht“ ([ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md) lässt es offen, der Aufrufer hat es gesetzt), der DoD-Wortlaut „Ausnahme beim Start“ ist nicht nachgezogen; siehe V2 |
| zählt gelieferte Ereignisse | bestätigt | `delivered` (Mutation M10 rot) |
| zählt `close()` | bestätigt | `closeCalls` (Mutation M7 rot) |
| zählt `cancel()` | bestätigt, schwach | `cancelled` ist eine Liste von Paaren; der Test prüft sie auf genau ein Paar `("s1","r1")` |
| suspendierend | bestätigt | `flowOf`/`flow` mit `yield()`; der Fake blockiert nirgends (kein `Thread.sleep`, kein `runBlocking`) |
| importiert nichts aus `protocol`, `transport` | bestätigt | Importe: `domain`, `kotlinx.coroutines`; `ForbiddenImport/port` bindet den Fake wegen seines Packages `port` |
| Ort | abweichend vom Plan | Plan §3 nennt `testsupport/`, der Code liegt in `port/`; Slice §3 nicht nachgezogen (Slice-Datei im Diff unverändert); siehe V2 |

Urteil Punkt 2: **bestätigt in der Sache, Plan nicht nachgezogen**.

### Liefer-Punkt 3 — Vertragstest (9 Tests) und Modelltests (4)

| Test | Zusage | `@spec` | Mutation, die ihn rot macht |
|---|---|---|---|
| Ergebnis aus fertiger Liste kommt vollstaendig an | Happy: Skript-Reihenfolge, 4 gelieferte Ereignisse, Anfrage beim Backend | SPEC-002 | M10 (Zähler), M8a (Fake) |
| Leeres Ergebnis traegt Metadaten und keine Zeile | Boundary | SPEC-002 | M8a (Fake) |
| Fehler nach Zeilen ist ein Failure-Ereignis im Strom | Negative | SPEC-002 | keine am Port; nur Fake-Änderung |
| Fehler vor dem Ergebnis ist ein Failure ohne ResultStart | Negative | SPEC-002 | M8a (Fake) |
| Fehler beim Start wirft nicht und erscheint als Failure im Strom | Setzung „kein Wurf“ | SPEC-002 | M2 |
| ResultStart kommt vor der ersten Zeile | Metadaten vor Zeile (Generator) | SPEC-002 | M4 |
| Generator laeuft nicht ueber die Abnahme hinaus | Abnahme-Zähler 3 (Eigenschaft des Fakes) | SPEC-031 | M4 (Folgeeffekt) |
| close erreicht die Zeilenquelle | `close()` erreicht die Quelle | SPEC-031 | M1, M7, M14 |
| cancel beendet den laufenden Strom an der Zeilenquelle | `cancel()` erreicht die Quelle | SPEC-031 | M1, M9 |
| Decimal akzeptiert / lehnt ab (2 Tests) | Modellgrenzen | SPEC-003 | M3, M11 |
| Parameter ohne Richtung ist INPUT | Default | SPEC-002 | M12 |
| QueryEvent kennt sieben Ereignisse | Vollständigkeit | SPEC-002 | M5 (Übersetzungsfehler, nicht Testfehler) |

- 9 plus 4 `@Test`, 13 `@spec`-Tags (`grep`), jeder Test trägt genau einen; im Lauf 13 PASSED, 0 FAILED. `@spec`-Tags und SPEC-IDs passen zu dem, was die Spezifikation an der Stelle sagt ([SPEC-031](../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität): „Backpressure begrenzt Puffer; Cancel und Disconnect schließen den Backendstream“). Der Tag `SPEC-003` an den Decimal-Tests trägt nur „Precision/Scale“; die Grenze `0 <= scale <= precision` ist eine Setzung des Quelltexts (KDoc und Vertrag kennzeichnen sie so).
- „Das Test-Backend läuft über den Port ohne TDS-Bytes“: bestätigt; die Tests konstruieren `QueryRequest` aus `domain` und lesen nur `QueryEvent`.
- Aussage über Messung hinaus: Test-KDoc und Klassen-KDoc sagen, was belegt ist („Eigenschaft des Test-Backends“, „keine Aussage über blockierende oder echte Backends“). Bestätigt.

Urteil Punkt 3: **bestätigt**, mit der Einschränkung aus V3 (die Tests belegen das Test-Backend und die Form des Ports, nicht ein beliebiges Backend).

### Nachweis-Pflichten und Closure-Pflichten

| Pflicht | Stand |
|---|---|
| `@spec` an jedem Test, Tests in `make test` grün | bestätigt für Tag-Vollständigkeit und grünen Gradle-Lauf offline; Gate-Lauf nicht bestätigt (429) |
| Aussagen an Test oder als Festlegung gekennzeichnet; Closure-Notiz trennt gemessen / Handvektor / Selbstkonsistenz / Festlegung | Quelltext bestätigt (Abschnitt 5); Closure-Notiz noch leer, also nicht erfüllt |
| `make gates` grün | **nicht bestätigt** (Abschnitt 1) |
| Review durchgeführt, Report liegt vor | bestätigt (`docs/reviews/2026-10-30-0d3f90c-slice-backend-port-vertrag.md`, Commit `91e0415`) |
| Doku-Update (öffentlicher Vertrag) | Vertrag ja, Spec nein (V1) |
| Closure-Notiz, Lerneintrag, Register, Risiko-Ausgänge, Paarungen | offen (Closure steht aus); §6 trägt noch fünf „offen bis zur Closure“ und die Entscheidungen zu Ort und Fehlerform stehen nur im Review-Eingang, nicht im Slice |

## 3. Mutationen (eigene Ausführung)

Jede Mutation lief in einer frischen Kopie des Klons bei `0d3f90c` im Scratchpad (`/tmp/claude-1000/-Development-tdswire/8562f454-84ba-456b-9970-945e69c035f4/scratchpad/`), ein Gradle-Lauf je Mutation. Der Arbeitsbaum des Repos blieb unverändert (`git status`: nur der nicht verfolgte Ordner `.claude/worktrees/`).

| # | Mutation | Ergebnis |
|---|---|---|
| M1 | Generator ignoriert `sourceStopped` (`&& !backend.sourceStopped` entfernt) | rot: „close erreicht die Zeilenquelle“, „cancel beendet den laufenden Strom…“ |
| M2 | `failingAtStart` wirft statt `Failure` zu liefern | rot: „Fehler beim Start wirft nicht und erscheint als Failure im Strom“ |
| M3 | `require(scale in 0..precision)` entfernt | rot: „Decimal lehnt ungueltige Precision und Scale ab“ |
| M4 | Zeile vor `ResultStart` im Generator | rot: „ResultStart kommt vor der ersten Zeile“ und „Generator laeuft nicht ueber die Abnahme hinaus“ |
| M5 | achter Untertyp `QueryEvent.Extra` | rot als Übersetzungsfehler: „`when` expression must be exhaustive“ in `BackendModelTest.kt:66` (nicht als fehlgeschlagener Test) |
| M6 | verbotener Import `protocol.wire.ByteReader` in `port/QueryBackend.kt` | `make detekt`-Äquivalent rot: `ForbiddenImport/port` meldet den Import mit der Begründung der Regel |
| M7 | `closeCalls` nicht erhöht | rot: „close erreicht die Zeilenquelle“ |
| M8a | Fake geändert: `ofList` stellt jedem Skript ein `ResultStart` voran (Failure-vor-ResultStart-Skript im Fake) | rot: drei Tests (Happy, Leeres Ergebnis, Fehler vor dem Ergebnis) |
| M8b | Port geändert: `Failure` bekommt ein Pflichtfeld `severity` | rot als Übersetzungsfehler in den Tests (Form) |
| M8c | Port geändert: `events` wird `suspend fun events()` | rot als Übersetzungsfehler in den Tests (Form) |
| M9 | `cancel()` setzt `sourceStopped` nicht | rot: „cancel beendet den laufenden Strom…“ |
| M10 | Zähler `delivered` entfernt (`onEach`) | rot: „Ergebnis aus fertiger Liste kommt vollstaendig an“ |
| M11 | `require(precision >= 1)` entfernt | rot: „Decimal lehnt ungueltige Precision und Scale ab“ |
| M12 | Default `direction = OUTPUT` | rot: „Parameter ohne Richtung ist INPUT“ |
| M14 | `close()` setzt `sourceStopped` nicht | rot: „close erreicht die Zeilenquelle“ |

15 Mutationen, alle erkannt (Soll: mindestens acht). Einzeln lesen:

- **Belegt der Test den Port oder nur den Fake?** M8a gegen M8b/M8c: Ändert man das Skript im Fake, werden die Failure-Tests rot; ändert man den Port, werden sie nur rot, wenn sich die **Form** (Felder, Signatur) ändert. Es gibt keine Verhaltensmutation am Port, die einen Vertragstest röte: der Port hat kein Verhalten, nur Signaturen. Die vier Tests „Ergebnis aus fertiger Liste“, „Leeres Ergebnis“, „Fehler nach Zeilen“, „Fehler vor dem Ergebnis“ geben im Kern das Skript zurück, das der Fake unverändert ausliefert (M8a zeigt das). Sie belegen, dass der Port diese Ereignisfolgen ausdrücken lässt, nicht, dass ein Backend sie einhält. Test-KDoc und der Abschnitt „Belegt am Test-Backend“ im Vertrag sagen das; die DoD sagt „Vertragstest“.
- „Fehler nach Zeilen“ hat keine eigene Mutation, die ihn und nur ihn röte (Folge von V3).

## 4. Schichtgrenzen und Ort des Test-Backends

- `ForbiddenImport/port` und `ForbiddenImport/domain` greifen und sind in dieser Prüfung durch M6 belegt; `domain/*.kt` importiert nichts außer der Standardbibliothek (keine Importzeile außer Standardbibliothek).
- `ScriptedBackend` liegt in `src/test/.../port/`, nicht in `testsupport/`. Wirkung: die Regel `ForbiddenImport/port` (`includes: **/port/**`) bindet den Fake an die Erlaubnisliste; in `testsupport/` gäbe es diese Bindung nicht. Das ist sachlich vertretbar, die Begründung steht aber weder im Slice (§3 nennt weiter `testsupport/`) noch im Quelltext. Siehe V2.
- Keine neue Abhängigkeit: `git diff 025bb11 0d3f90c --stat -- gradle gradle.lockfile settings-gradle.lockfile build.gradle.kts config` ist leer; kein `@Suppress` in `port`, `domain` oder den neuen Tests.

## 5. Aussagen über Messung hinaus (`AGENTS.md` §3.8) und offene Punkte der [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md)

| Offener Punkt | Im Quelltext (KDoc) | Im Vertrag |
|---|---|---|
| `Failure` in der Vorschau von `DONE_MORE` | gekennzeichnet (`QueryEvent`: „Offen“ mit Verweis auf die ADR) | „Offen“ |
| statement-/batch-abbrechende Fehler | gekennzeichnet (`QueryEvent`, `Failure`) | „Offen“ |
| Grenze für „kleines“ Ergebnis | gekennzeichnet (`QueryExecution`) | „Offen“ |
| Zeilendarstellung `List<Any?>` | gekennzeichnet (`Row`, `QueryParameter`, `SqlType`) | „Offen“ |
| `OutOfMemoryError` | **nicht im Quelltext** | „Offen“ |
| blockierendes Backend | gekennzeichnet (`QueryBackend.execute`, `ScriptedBackend`) | „Pflichten“, „Gemessen“ |
| Frist `deadlineEpochMillis` | gekennzeichnet (`QueryRequest`, „`null` heißt: keine Frist“ als Festlegung) | „Offen“ |
| Prüfung der Ereignisreihenfolge | gekennzeichnet (`QueryExecution.events`) | „Offen“ |

Die Setzung „`execute()` wirft nicht“ trägt im KDoc und im Vertrag „Festlegung ohne Messung“ bzw. „Setzung, nicht gemessen“. Keine Aussage in den geprüften KDoc-Blöcken geht erkennbar über Messung, Setzung oder Test hinaus; die Ausnahmen stehen in V4 und V5.

## 6. Out-of-Scope

Bestätigt eingehalten: kein Sequenzierer, kein `DONE_MORE`, keine Token im Port; keine Typabbildung auf TDS-Typen (`SqlType` verweist nicht auf `protocol`); kein echtes und kein Simulations-Backend (der Fake erkennt keine Anweisung, `request.sql` wird nur mitgeschrieben; keine Tabellen); keine Fehlerklasse im `Failure`; keine Durchsetzung der Frist; keine neue Abhängigkeit.

## 7. Spec-Lücke (Frage 8, ohne Änderung an `spec/`)

[SPEC-002](../../spec/spezifikation.md#spec-002--interner-query-vertrag) sagt weiter: „Dies ist eine **Vertragsskizze**, kein bereits implementierter Code. Konkrete Typen, Fehler und Lebensdauerregeln werden vor der Implementierung festgelegt.“ Der Quelltext existiert; `SqlType` ist in der Skizze verwendet und nirgends definiert; Ort der Typen (`domain`/`port`) und Fehlerform beim Start stehen nur im Vertrag. Die Zeile `SPEC-022` in [§6 der Spezifikation](../../spec/spezifikation.md#6-externe-verträge) führt den Vertrag weiter als „Projektvertrag v0 (Entwurf)“. Der Vertrag trägt damit Zusagen, die die höher rangierende Spezifikation nicht kennt. Als Finding V1 geführt.

---

## Findings

Kein HIGH.

### MEDIUM

**V1 — Spec-Nachführung fehlt (DoD Punkt 1, Teilzusage nicht bestätigt)**
- Quelle: Slice §2 Punkt 1 („`SPEC-002` … stehen auf dem gebauten Stand“), Slice §3 (Zeile `spec/spezifikation.md`), [SPEC-002](../../spec/spezifikation.md#spec-002--interner-query-vertrag).
- Befund: siehe Abschnitt 7. `spec/` ist im Diff nicht berührt; die Spec widerspricht dem Quelltext („kein bereits implementierter Code“) und lässt `SqlType` undefiniert.
- Behebung gehört nicht in diese Rolle (Auftrag: `spec/` nicht ändern); Träger: Planner/Nutzer, vor der Closure oder als benannte Spec-Lücke mit Folge-Slice im Register.

**V2 — Plan und DoD-Wortlaut nicht an den gebauten Stand angepasst**
- Quelle: Slice §3 („Der Implementer … passt diese Tabelle an“), §2 Punkt 2, §6.
- Befund: (a) §3 nennt `testsupport/` für das Test-Backend, gebaut ist `port/`; (b) §2 Punkt 2 verlangt „Ausnahme beim Start“ skriptbar, gebaut ist ein `Failure` im Strom (Setzung); (c) die beiden Nutzerentscheidungen aus §6 (Ort der Modelltypen, Fehlerform beim Start) und die „offen bis zur Closure“-Ausgänge stehen nicht im Slice. Ohne diese Nachführung weicht die Closure-Notiz still vom Plan ab.
- Behebung: Slice-Plan nachziehen oder die Abweichung in §7 benennen (Closure-Schritt).

**V3 — Vertragstest belegt das Test-Backend und die Form des Ports, nicht eine Pflicht an beliebigen Backends**
- Quelle: Slice §2 Punkt 3, `AGENTS.md` §3.8, Mutationen M8a/M8b/M8c.
- Befund: Vier Tests geben das Skript zurück, das der Fake unverändert liefert; „Fehler beim Start wirft nicht“ prüft die Eigenschaft von `failingAtStart`, die der Port weder erzwingen noch ausdrücken kann (`execute` kann jede Ausnahme werfen). Die Vertragssuite ist an `ScriptedBackend` fest verdrahtet, ein späteres Backend (Simulation) durchläuft sie nicht. Der Vertragsabschnitt „Belegt am Test-Backend“ und die Test-KDoc grenzen das ehrlich ab; die DoD-Bezeichnung „Vertragstest“ und die Closure-Zuordnung „Aussage je Test“ müssen dieselbe Grenze tragen. Überlappung mit Review M2.
- Behebung: in der Closure-Notiz als *Selbstkonsistenz* führen (nicht *gemessen*); eine wiederverwendbare Suite (Backend als Parameter) ist ein Folge-Punkt, kein Fehler dieses Slice.

**V4 — `make test`-Stage, `make build` und `make gates` nicht bestätigt**
- Quelle: Slice §2 („`make gates` grün“), Modul 11.
- Befund: Die Registry lieferte HTTP 429 (Abschnitt 1). Bestätigt sind detekt, detekt-selbsttest, docs-check und ein Offline-Gradle-Testlauf mit den 13 Tests; die Gate-Kette selbst nicht. Niemand im Prozess (Review und Verifikation) hat bisher einen grünen `make gates` gesehen.
- Behebung: `make gates` vor der Closure einmal auf einem Host mit erreichbarer Registry laufen lassen; kein Befund am Diff.

### LOW

**V5 — `OutOfMemoryError` nicht im Quelltext als offen gekennzeichnet.** Der Port sagt dazu nichts; der Vertrag führt es unter „Offen“. Der Port macht keine Aussage, die es brauchte; die Liste der offenen Punkte im KDoc ist damit unvollständig gegenüber [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md) (Hinweis für `slice-adr-backend-grenzen`).

**V6 — `SqlType.DateTime` ist „ohne Zeitzone“, die Zeitzonenannahmen stehen als offen.** KDoc und Vertrag legen „ohne Zeitzone“ fest und führen „Zeitzonenannahmen“ ([SPEC-003](../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog)) zugleich als offen. Beides unter „Festlegung ohne Messung“ erklärt, aber leicht widersprüchlich; eine der beiden Aussagen enger fassen.

**V7 — `Decimal` lässt Precision über 38 zu, `TypeInfoWriter.MAX_PRECISION = 38`.** Bestätigt gegen den Quelltext; das KDoc nennt die fehlende Obergrenze als offen. Das Modell nimmt Werte an, die der Codec ablehnt; Träger `slice-backend-typabbildung`. Deckt sich mit Review L2.

**V8 — `failingAtStart(failure)` und `ofList(failure)` sind im Verhalten gleich** (`flowOf(failure)`); der Unterschied ist nur die Absicht im Namen. Der Test „Fehler beim Start“ unterscheidet sich vom Test „Fehler vor dem Ergebnis“ nur durch den Factory-Namen.

**V9 — `@spec SPEC-003` an den Decimal-Tests** belegt die Zeile „Precision/Scale“ der Tabelle; die Bedingung `0 <= scale <= precision` steht dort nicht, sondern ist eine Setzung des Quelltexts. Der Tag ist vertretbar, deckt aber weniger, als die Abdeckungsmatrix daraus lesen wird.

**V10 — Die Tests lesen `events` ein zweites Mal** („close erreicht die Zeilenquelle“); der Port sagt nicht, ob der Strom kalt ist. Die Aussage ist am Fake wahr, am Port unentschieden (deckt sich mit Review I4).

---

## Rückführung

- Spec-Lücke ([SPEC-002](../../spec/spezifikation.md#spec-002--interner-query-vertrag), `SqlType`) ist benannt (V1); sie gehört in das Beobachtungs-Register bzw. die Closure-Notiz mit Träger, nicht in einen Quelltext-Kommentar.
- Steering-Loop-Kandidat: „Test belegt nur das Skript“ (V3; im Review M2 und L3) — der Zähler folgt aus den Dateien im Register.
- Der Slice kann nach Behebung von V1/V2 (Plan und Spec) und einem grünen `make gates` (V4) geschlossen werden; die Mutationsergebnisse tragen die Zuordnung „Aussage je Test“ für die Closure-Notiz.
