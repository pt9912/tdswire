# Review-Report: slice-backend-port-vertrag — 2026-10-30

**Review-Art:** Code (gegen Plan, ADR und Hard Rules)

**Gegenstand:** Merge `0d3f90c` (Commits `16d62ec`, `b177a48`), Diff `025bb11..HEAD` über `src`, `contracts` und den Slice-Plan.

**Skill:** `.harness/skills/reviewer.md` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-10-30

**Eingangs-Kontext:**

- Plan: `slice-backend-port-vertrag` (in-progress) und `welle-backend-streaming`
- [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md) (Accepted), [ADR-0005](../plan/adr/0005-rpc-und-prepared-statements.md), [ADR-0007](../plan/adr/0007-fehler-und-attention.md)
- [TDS-FA-08](../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-QA-05](../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [SPEC-002](../../spec/spezifikation.md#spec-002--interner-query-vertrag), [SPEC-003](../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog), [SPEC-031](../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität)
- `spec/architecture.md` §2, `config/detekt/detekt.yml`, `contracts/query-backend.md`, `AGENTS.md` §3.1–3.8
- Setzungen des Aufrufers (2026-10-30): Typmodelle in `domain`, Schnittstellen mit `Flow` in `port`; `execute()` wirft nicht, Fehler als `Failure` im Strom.

**Gate-Läufe in diesem Review:** `make detekt` Exit 0. `make test` nicht auswertbar: der Gradle-Lauf im Container scheiterte beim Auflösen von `kotlinx-coroutines-core:1.10.1` mit HTTP 429 von repo.maven.apache.org (Umgebung, kein Befund am Diff); das Lock ist unverändert. `make gates` nicht gelaufen. Die Testaussagen unten sind aus dem Quelltext gelesen, nicht aus einem grünen Lauf belegt.

---

## Findings

Kein HIGH.

### MEDIUM

**M1**
- kategorie: MEDIUM
- quelle: [SPEC-002](../../spec/spezifikation.md#spec-002--interner-query-vertrag), Slice-DoD Punkt 1
- pfad: `spec/spezifikation.md` · „Dies ist eine **Vertragsskizze**, kein bereits implementierter Code.“
- befund: Der Slice fordert, dass [SPEC-002](../../spec/spezifikation.md#spec-002--interner-query-vertrag) auf den gebauten Stand kommt; `spec/` ist im Diff unverändert. Die Spec sagt weiter „kein bereits implementierter Code“, definiert `SqlType` nicht und kennt weder den Ort der Typen (`domain`/`port`) noch die Fehlerform beim Start; der Vertrag (`contracts/query-backend.md`) nennt `SqlType` ausdrücklich als „Spec-Lücke“ und trägt damit eine Zusage, die in der höher rangierenden Spec fehlt. Zuständig für die Spec-Änderung ist laut Slice-Plan §3 der Slice, ein Träger ist im Diff nicht benannt (Architect bzw. Nutzer entscheidet).
- verifizierbar: nein (kein Gate prüft die Spec gegen den Quelltext)
- klasse: Spec-Nachführung laut DoD nicht geliefert

**M2**
- kategorie: MEDIUM
- quelle: Maintainability, [TDS-FA-08](../../spec/lastenheft.md#tds-fa-08--backend-anbindung), `AGENTS.md` §3.8
- pfad: `src/test/kotlin/io/github/pt9912/tdswire/port/QueryBackendContractTest.kt` · „Fehler vor dem Ergebnis ist ein Failure ohne ResultStart“ (ebenso „Fehler nach Zeilen ist ein Failure-Ereignis im Strom“, „Fehler beim Start wirft nicht“, „Ergebnis aus fertiger Liste kommt vollstaendig an“)
- befund: Vier der neun Vertragstests geben das Skript zurück, das `ScriptedBackend.ofList` bzw. `failingAtStart` unverändert über `flowOf` ausliefert; es gibt keine Mutation am Produktionscode, die sie rot macht, und keine Vertragssuite, die ein anderes Backend durchlaufen könnte (das Backend wird in jedem Test fest konstruiert). `contracts/query-backend.md` führt „`Failure` vor und nach `ResultStart`, Fehler beim Start als `Failure`“ unter „Belegt am Test-Backend“ — das trägt höchstens „der Port kann es ausdrücken“, und die Test-KDoc sagt das; der Vertragsabschnitt ist knapper. Failure-Szenario: ein späteres Backend (Simulation) verletzt „Fehler beim Start wirft nicht“, ohne dass ein Test des Slice reagiert.
- verifizierbar: nein
- klasse: Tautologie: Test belegt nur das Skript

### LOW

**L1**
- kategorie: LOW
- quelle: Maintainability (Slice-Plan §3), Planabweichung
- pfad: `src/test/kotlin/io/github/pt9912/tdswire/port/ScriptedBackend.kt` · „class ScriptedBackend“; Plan: `slice-backend-port-vertrag` §3 „src/test/kotlin/io/github/pt9912/tdswire/testsupport/“
- befund: Der Plan nennt `testsupport/`, der Code liegt in `port/`; der Slice-Plan wurde nicht angepasst (er ist im Diff unverändert, §3 verlangt das ausdrücklich). Die Begründung „wegen der Importregel“ ist in `config/detekt/detekt.yml` nicht nachvollziehbar: es gibt keine Regel für `testsupport`, die ein Fake dort träfe; belegbar ist der Gegenteil-Effekt, dass `ForbiddenImport/port` (`includes` auf `port/**`, Main und Test) den Fake an die Erlaubnisliste bindet und so „importiert nichts aus protocol, transport“ vom Gate durchgesetzt wird. Die Abweichung ist also sachlich vertretbar, aber begründungs- und plan-seitig nicht belegt; eine spätere Nutzung aus `protocol`-Tests importiert dann ein Test-Objekt aus dem Package `port`.
- verifizierbar: ja (`make detekt` bestätigt die Bindung des Fakes an die Regel)
- klasse: Planabweichung ohne Nachführung im Plan

**L2**
- kategorie: LOW
- quelle: [SPEC-003](../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog), Maintainability
- pfad: `src/main/kotlin/io/github/pt9912/tdswire/domain/SqlType.kt` · „eine obere Grenze von [precision] ist nicht festgelegt“
- befund: `SqlType.Decimal` akzeptiert jede Precision ≥ 1; der Encoder (`TypeInfoWriter`, `MAX_PRECISION = 38`) verlangt 1..38 und meldet sonst `INVALID_TYPE`. Das Modell lässt also Werte zu, die der Codec später ablehnt (zum Beispiel `Decimal(50, 2)`); das ist im KDoc als offen gekennzeichnet, aber die Grenze liegt mit 38 im Repo bereits fest, der Widerspruch ist damit kein offener Punkt der Spec, sondern eine bekannte Lücke zwischen zwei Schichten. Zuständig die Typabbildung (`slice-backend-typabbildung`).
- verifizierbar: ja (ein Test `Decimal(39, 0)` gegen den Encoder würde es zeigen)
- klasse: Modellgrenze weiter als Codec-Grenze

**L3**
- kategorie: LOW
- quelle: `AGENTS.md` §3.8, Maintainability
- pfad: `src/test/kotlin/io/github/pt9912/tdswire/domain/BackendModelTest.kt` · „QueryEvent kennt sieben Ereignisse“
- befund: `assertEquals(7, names.toSet().size)` gilt für die selbst gebaute Liste von sieben Elementen immer; belegt ist die Vollständigkeit nur durch das erschöpfende `when` zur Übersetzungszeit, und die Test-KDoc sagt das. Ein achter Untertyp bricht die Übersetzung, nicht den Test.
- verifizierbar: nein
- klasse: Tautologie: Test belegt nur das Skript

### INFO

**I1** — Schichtgrenzen (Frage 1): `domain` importiert nur `kotlin`-Standard; `port` nur `domain`, `kotlinx.coroutines.flow`; `make detekt` Exit 0, `detekt.yml` unverändert. Zähler in `ScriptedBackend` sind nicht threadsicher (`var` ohne `@Volatile`, `mutableListOf`); die Klassen-KDoc nennt die Grenze („nur `runBlocking` ohne eigenen Dispatcher“) und die Tests halten sie ein. Ausreichend dokumentiert.

**I2** — Typkatalog (Frage 2): Die acht `SqlType` entsprechen dem Mindestkatalog [SPEC-003](../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog); `INTN`, `BITN`, `NVARCHAR`, `DECIMALN`/`NUMERICN`, `DATEN`, `TIMEN`, `DATETIME2N` des Encoders sind Formen derselben Typen. Nicht darstellbar sind die Zeit-Scale (0..7) von `Time`/`DateTime`, die der Encoder trägt, das Längenlimit von `UnicodeText` und die `numeric`-Variante; alles drei steht im KDoc als offen. Zuständig: Typabbildung.

**I3** — Anfrage-Modelle (Frage 2): `RpcType`/`RpcParameter` (Parser) und `QueryParameter` (Port) überschneiden sich nicht; die Übersetzung ist ausdrücklich außerhalb dieses Slice. Zwei Lücken für die Übersetzung: der RPC-Status Bit 0 unterscheidet OUTPUT nicht von InputOutput, `ParameterDirection` kennt beide; und Parametertypen des Referenzclients ohne `SqlType` (float, money, GUID, char/varchar, binary, `datetimeoffset`) haben im Port keine Entsprechung. `deadlineEpochMillis` wird nur getragen und im KDoc als offen geführt, mit „Festlegung ohne Messung: `null` heißt: keine Frist“ — passt zu [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md).

**I4** — Mutationsprüfung (Frage 3), gedanklich: „`close erreicht die Zeilenquelle`“ (`sourceStopped = true` entfernen: Zeilenzahl wächst, Test rot; `closeCalls++` doppelt: rot), „`cancel beendet den laufenden Strom`“ und „`Generator laeuft nicht ueber die Abnahme hinaus`“ (Abnahme-Zähler 3) sind glaubhaft; `closeCalls == 1` ist ohne Gegenprobe zwar schwach, aber über die Mutation „doppelt zählen“ abgedeckt. `@spec`-Tags: alle Tests tragen einen Tag; [SPEC-031](../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität) für Schließen/Abbruch passt. Der „close“-Test setzt voraus, dass `events` ein zweites Mal gesammelt werden darf (kalter Strom); der Port sagt dazu nichts.

**I5** — Aussagen über Messung (Frage 4, [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md)): Die KDoc-Stellen zu Fehlerklasse, `Failure` in der Vorschau, Ereignisreihenfolge, `deadlineEpochMillis`, „klein“, Zeilendarstellung, blockierendes Backend sind als „offen“ bzw. „Festlegung ohne Messung“ gekennzeichnet und decken sich mit der Liste der ADR. Beiläufig ungestützt: „`ResultEnd.affectedRows` ist `null`, wenn das Backend keine Zahl liefert“ ist weder Setzung noch getestet. Ein per `cancel` beendeter Generator endet ohne Abschlussereignis; das Schicksal eines zurückgehaltenen Abschlusses ist in der ADR offen, im Port nicht erwähnt.

**I6** — Vertrag (Frage 5): Der Signatur-Abschnitt in `contracts/query-backend.md` stimmt mit dem Code überein (Ort der Typen, sieben Ereignisse, Setzung Fehler beim Start, `SqlType`-Liste). Die Spec-Nachführung siehe M1.

**I7** — Hard Rules (Frage 6): kein `@Suppress`/`@SuppressWarnings`; keine Änderung an `gradle.lockfile`, `verification-metadata.xml`, `detekt.yml`; Kommentare tragen Zusage/Abgrenzung/Grenze (§3.7), keine Konjunktive über verworfene Alternativen gefunden; Docker-only eingehalten.

---

## Geprüft, ohne Befund

- `src/main/kotlin/io/github/pt9912/tdswire/port/` — Schichtregel, Kommentare, Setzung „execute wirft nicht“ (KDoc) ohne Befund.
- `src/main/kotlin/io/github/pt9912/tdswire/domain/` (`QueryEvent`, `QueryRequest`) — ohne Befund; `SqlType` siehe L2.
- `config/detekt/`, `gradle*`, `build.gradle.kts` — unverändert, ohne Befund.
- `src/test/kotlin/io/github/pt9912/tdswire/port/` — siehe M2, L1; Zähler/Threads, `@spec`-Tags ohne Befund.
- `contracts/` — ohne Befund (Spec-Gegenstück siehe M1).

## Rückführung

Steering-Loop-Zähler: „Tautologie: Test belegt nur das Skript“ (M2, L3) — zweimal in diesem Lauf. Kein Konflikt-Pfad eröffnet; kein Finding wurde herabgestuft.
