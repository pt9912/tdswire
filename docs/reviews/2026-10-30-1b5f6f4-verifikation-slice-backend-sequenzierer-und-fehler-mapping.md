# Verifikationsbericht: slice-backend-sequenzierer und slice-fehler-mapping am Stand 1b5f6f4 — 2026-10-30

**Rolle:** Verifier (Frage: „Bauen wir es richtig?“ gegen Plan, DoD und Entscheidungen). Der Bericht repariert nichts und ändert keine Datei außer sich selbst. Beide Slices werden gemeinsam verifiziert, weil sie über die Schnittstelle `ErrorMapping` / `ErrorMapper.sequencerMapping()` verklammert sind.

**Gegenstand:**

- [slice-backend-sequenzierer](../plan/planning/done/slice-backend-sequenzierer.md) (Welle welle-backend-streaming), Review [2026-10-30-d7a7536-slice-backend-sequenzierer.md](2026-10-30-d7a7536-slice-backend-sequenzierer.md) (F-1 bis F-10), Fix-Runde bis Merge `371252b`.
- [slice-fehler-mapping](../plan/planning/done/slice-fehler-mapping.md) (Welle welle-fehler-abbruch), Review [2026-10-30-a8072b5-slice-fehler-mapping.md](2026-10-30-a8072b5-slice-fehler-mapping.md) (F-1 bis F-9), Fix-Runde Merge `e7017dc`.
- Bezug: [TDS-FA-05](../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen), [TDS-FA-06](../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände), [TDS-FA-08](../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-FA-10](../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit), [TDS-QA-07](../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität); [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md), [ADR-0007](../plan/adr/0007-fehler-und-attention.md); [SPEC-031](../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität), [SPEC-048](../../spec/spezifikation.md#4-fehler-codes-und-logging-felder) und die Zuordnung in [§4](../../spec/spezifikation.md#4-fehler-codes-und-logging-felder).

**Stand-Hinweis (wichtig):** Geprüft ist der Baum von `1b5f6f4` (HEAD bei Beginn), als `git archive` im Scratchpad (`.../scratchpad/pin/`). Der Arbeitsbaum des Repos wird von parallelen Agenten benutzt: HEAD wanderte während des Laufs auf `cb4c853` (neu: `slice-qa-stabilitaet`, Lastenheft 0.5.0, [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md)-Nachführung; `git diff 1b5f6f4 cb4c853` berührt weder `protocol/response` noch `protocol/error` noch die beiden Slice-Pläne). Ein erster `make detekt` auf dem laufenden Arbeitsbaum scheiterte an zwei Meldungen `InjectDispatcher` in `src/test/.../runtime/HandshakeWiringTest.kt`; diese Datei liegt weder in `1b5f6f4` noch in `cb4c853` (sie existierte nur kurz im Arbeitsbaum, ein Exemplar liegt unter `.claude/worktrees/`). Sie gehört nicht zu den beiden Slices und wurde nicht bewertet; dieser Lauf zählt nicht. Maßgeblich sind die Läufe auf dem gepinnten Baum. Danach kam `e52a0dd`: der Ordner `contracts/` ist in `spec/spezifikation.md` Abschnitt 6 aufgegangen; wo dieser Bericht `contracts/query-backend.md` nennt, meint er den Vertragstext am gepinnten Stand, dessen Festlegungen des Response Sequencers heute unverändert in der Spezifikation stehen (Abschnitt 6, Zeile „Festlegungen des Response Sequencers“).

**Behauptung ≠ Bestätigung:** Beide Slices tragen weder Closure-Notiz noch DoD-Häkchen (§7: „Wird bei der Closure gefüllt“, alle Kästchen leer). Die Zusagen des Implementers (Tests grün, Mutanten tot, Befunde behoben) liegen nur im Auftrag, in den Reviews und in §6 der Pläne vor; jede wurde selbst gefahren.

**Ergebnis in einem Satz:** Code, Spezifikation und Tests tragen die Zusagen beider Slices; alle 20 gefahrenen Mutanten sind tot, alle Befunde beider Reviews sind behoben oder ehrlich als offen geführt; es bleiben 0 HIGH, 2 MEDIUM, 5 LOW und 3 INFO an Verifier-Befunden (Abschnitt 8), und beide Slices sind **nicht schließbar**, solange Closure-Notiz, §6-Ausgänge und Register-Eintrag fehlen. Der gemeldete `AuthExecutorTest` ist **flaky** (Abschnitt 3): 6 rote Wiederholungen in 4000 unter Last, Ursache eine Ordnungsannahme des Tests, kein Leck des Passworts.

## 1. Selbst gefahrene Läufe

| Lauf | Ergebnis |
|---|---|
| `docker build --target test --no-cache-filter test` auf `1b5f6f4` (gepinnt) | Exit 0, `BUILD SUCCESSFUL`, **420 Tests PASSED, 0 FAILED**, kein Test übersprungen; darin `ErrorMapperTest` 25, `ResponseErrorMapperTest` 7, `ResponseFailureTest` 15, `ResponseMappingTest` 11, `ResponseOrderTest` 4 (62 Tests der beiden Slices) |
| dasselbe auf dem Arbeitsbaum zu Beginn | Exit 0, 420 PASSED (Basis der Mutationen) |
| `docker build --target detekt` auf `1b5f6f4` (gepinnt) | Exit 0 (`tools/suppression-check.sh src`, `detekt`, `detektMain`, `detektTest`) |
| `make docs-check` (Arbeitsbaum vor diesem Bericht) | 313 Dateien, 0 Befunde |
| Suche `Suppress`, `noqa`, `nolint` in `protocol/response`, `protocol/error` (main und test) | kein Treffer |
| `make gates` als Ganzes | **nicht gefahren**: der Arbeitsbaum wechselt unter den Händen (siehe Stand-Hinweis); Einzelsensoren `test`, `detekt`, `docs-check` wurden auf festem Stand gefahren, `baseline-verify` und die Selbsttests wurden nicht neu belegt |

## 2. Mutationen (einzeln, frische Läufe)

Je Mutante eine eigene Kopie des Baums im Scratchpad (`.../scratchpad/mut/V*`), je ein `docker build --target test --no-cache-filter test`, nichts gemountet, bis zu sechs Läufe nebeneinander. „Tot“ heißt: mindestens ein Test rot. Alle Läufe endeten mit Exit 1 und benannten rote Tests; die Läufe liefen unter hoher Hostlast, ein roter Test ohne Bezug zur Mutation kam nicht vor.

Die zehn Mutanten der Fix-Runde:

| Nr | Mutation | Ergebnis | Rote Tests (Auswahl) |
|---|---|---|---|
| V01 | `close()` ohne `NonCancellable` | tot | `Abbruch laesst ein suspendierendes close zu Ende laufen` (ersetzt den überlebenden Mutanten E aus Sequenzierer-F-3) |
| V02 | `CancellationException` aus dem Strom gefangen | tot | `Abbruchausnahme aus dem Strom laeuft nach aussen` |
| V03 | Ausnahme nicht an `internal` gereicht (`null`) | tot | `internal erhaelt die Ausnahme, bei einem Verstoss null`, `Ausnahme und Reihenfolgeverstoss erreichen das Log` (+1) |
| V04 | `closesConnection` ignoriert | tot | 10 Tests, u. a. `Backend-Fehler liefern fuer jeden Code Bytes und Folge des Mappers` |
| V05 | `failedDone()` mit `MORE` | tot | 26 Tests, alle Handvektoren des Mappers, `Die Login-Ablehnung des Mappings gleicht den Bytes des Handshakes` |
| V06 | `fateOf(CLOSE_WITHOUT_COMPLETION)` → `OPEN` | tot | `fateOf bildet jeden Ausgang des Sequenzierers ab`, `Ausgang ohne Abschluss entspricht der Folge ohne Antwort des Mappers` |
| V07 | Ablehnung des ERROR durch die Senke wird nicht ausgewertet (DONE trotzdem geschrieben) | tot | `abgelehntes ERROR schliesst ohne DONE`, `Zufallsfolgen …` |
| V07b | Ablehnung des **DONE hinter dem ERROR** ignoriert (`write(failedDone()).let { true }`) | tot, **nur** durch `Zufallsfolgen stimmen mit dem Modell der erlaubten Folgen ueberein` | siehe V-5 |
| V08 | Ablehnung des **letzten DONE** ignoriert | tot, **nur** durch `Zufallsfolgen …` | siehe V-5 |
| V09 | Mapper loggt nicht (alle `log.record`) | tot | 8 Tests, u. a. `Der Adapter fuer den Sequenzierer loggt wie die Einstiege` |
| V10 | Reihenfolgeverstoß ohne Log-Eintrag | tot | `Der Adapter fuer den Sequenzierer loggt wie die Einstiege`, `Ausnahme und Reihenfolgeverstoss erreichen das Log` |

Klassenschwelle, Gruppenzuordnung, Login-Bytes und weitere:

| Nr | Mutation | Ergebnis | Rote Tests (Auswahl) |
|---|---|---|---|
| V11a | `TDS_BACKEND_ERROR` Class 20, Folge offen | tot | `Verbindungsfolge und Severity folgen der Schwelle 19`, `Gruppen der Entscheidung …` (5 Tests) |
| V11b | `TDS_PROTOCOL_ERROR` Class 19, Folge schließt | tot | `Verbindungsfolge und Severity folgen der Schwelle 19`, Handvektor, Gruppentest |
| V12a | `TDS_SIM_INVALID_VALUE` Class 20, schließt | tot | `Gruppen der Entscheidung …`, Handvektor des Codes |
| V12b | `TDS_UNSUPPORTED_RPC` schließt bei Class 16 | tot | `Verbindungsfolge und Severity folgen der Schwelle 19`, Gruppentest, Handvektor |
| V13 | Text der Login-Ablehnung ohne Punkt | tot | `Die Login-Ablehnung des Mappings gleicht den Bytes des Handshakes`, Handvektor `TDS_LOGIN_FAILED …` |
| V14 | `Failure` zählt in der Vorschau nicht als nächstes Ereignis | tot | `Failure nach ResultEnd gibt dem zurueckgehaltenen Abschluss DONE_MORE`, `Zufallsfolgen …` |
| V15 | `Failure` beendet den Lauf nicht | tot | `Failure vor ResultStart endet in ERROR und DONE und liest nichts weiter` (+2) |
| V16 | `TDS_LOGIN_FAILED` in der Positivliste des Backends | tot | `Failure mit einem Code, den nur der Server erzeugt, wird zum internen Fehler` (+1) |
| V17 | `typeMapping` hängt das `detail` des `ProtocolError` an den Meldungstext | tot | `Typabbildung liefert Bytes und Folge von protocol` |

20 Mutanten (die zehn des Auftrags sind V01 bis V06, V07b und V08 bis V10; dazu V07, V11a bis V13 und V14 bis V17): **20 von 20 tot, keiner überlebt.** Grenze: V17 stirbt, weil der Vergleichstest `protocol()` unverändert lässt; ein Mapper, der `detail` in **beiden** Pfaden übernimmt, wäre nicht abgedeckt, wie der Plan in §6 selbst sagt (Rest-Punkt zur Geheimnisfreiheit).

## 3. Flakiness: `AuthExecutorTest`

Gegenstand: `Ein wartender Aufruf beginnt nach dem Abbruch nie und das Passwort ist ueberschrieben` in [AuthExecutorTest.kt](../../src/test/kotlin/io/github/pt9912/tdswire/protocol/session/AuthExecutorTest.kt) (gehört zu `slice-handshake-login`, nicht zu den beiden Slices).

Die Frage: war der eine rote Lauf in einer Mutationskopie Zufall oder ein Fehler? Antwort: ein **reproduzierbarer Testfehler** (Ordnungsannahme), kein Leck.

| Lauf | Aufbau | Ergebnis |
|---|---|---|
| A | Docker-Stufe im Scratchpad (`.../scratchpad/stress`): Kopie des Baums plus ein Zusatztest, der den Rumpf des Tests 1000-mal in einer JVM wiederholt; im selben Container 60 Busy-Loops (`while :; do :; done`); Hostlast 85 bis 118 (andere Agenten liefen mit) | **2 rot** (Lauf 35, Lauf 59), beide mit „Passwort des wartenden Aufrufs nicht ueberschrieben“ |
| B | wie A (`.../scratchpad/stress2`), 3000 Wiederholungen; bei einem roten Lauf wird bis zu 5 s weiter gelesen, wann das Passwort überschrieben ist; Hostlast bis 238 | **4 rot** (Lauf 126, 129, 152, 389); das Passwort war **1, 2, 7 und 11 ms nach `join()`** überschrieben, in keinem Fall „nie“; jedes Mal `calls=1`, zweiter Aufruf abgebrochen und abgeschlossen |
| C | das Original, je eigene JVM (`gradle cleanTest test --tests '*AuthExecutorTest*'`), 60 Busy-Loops | **7 von 7 grün**; 12 waren geplant, abgebrochen nach dem siebten (110 bis 230 s je Lauf unter der Last) |
| D | Basislauf und gepinnter Lauf (Abschnitt 1), keine zusätzliche Last | grün (zwei Läufe, jeweils mit dem Test) |

Zusammen **4007 Läufe des Testrumpfs** (≥ 50 gefordert): 6 rot, das sind 0,15 % bei Hostlast über 100; ohne Last nicht gemessen.

**Ursache** (aus Code und Messung gelesen, nicht mit einem Profiler bewiesen): `AuthExecutor.start` überschreibt das Passwort eines nie begonnenen Aufrufs in einem Abschluss-Handler (`invokeOnCompletion`). In `kotlinx.coroutines` wird der Job zuerst als abgeschlossen markiert, dann laufen die Handler; ein `join()`, das in dieses Fenster fällt, kehrt zurück, bevor der Handler lief, und der Test liest das Passwort sofort danach. Die gemessene Spätschreibung (1 bis 11 ms, nie ausbleibend) passt dazu. Ein laufender Aufruf (der Rumpf überschreibt in `finally`) ist nicht Gegenstand des Tests und wurde nicht gemessen.

**Bewertung.**

- Der **Test** nimmt an, `join()` ordne den Handler davor. Das ist nicht zugesagt; er ist unter Last flaky. Die Mutationskopie, in der er einmal rot war, ist mit dieser Rate erklärbar; eine Mutation war dafür nicht nötig.
- Das **Produkt** überschreibt das Passwort, aber nicht geordnet vor dem Ende, das ein Beobachter sieht: bis zu 11 ms (gemessen, Last über 100). Die KDoc von `AuthExecutor` sagt „sobald der Aufruf endet“; für den wartenden, abgebrochenen Aufruf gilt das nur mit diesem Fenster. Kein Leck, kein Befund gegen `[TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit)` im Sinn eines dauerhaft stehenden Passworts, aber die Zusage ist breiter als der Beleg ([AGENTS.md](../../AGENTS.md) §3.8).
- Zuständig ist `slice-handshake-login`; die beiden hier verifizierten Slices sind nicht betroffen. Reparatur ist nicht meine Rolle. Der Fall passt zu `BEO-TDS/flake-fix-ohne-reproduzierten-fehler`: hier gibt es jetzt eine Reproduktion (Befund V-10).

## 4. DoD Punkt für Punkt

### 4.1 slice-backend-sequenzierer

| DoD-Punkt | Urteil | Beleg |
|---|---|---|
| 1 Ereignis → Token ([TDS-FA-05.a](../../spec/spezifikation.md#tds-fa-05a--ergebniskodierung) Schritt 2 bis 4): `ResultStart` → COLMETADATA nach Validierung, `Row` → ROW, `ResultEnd`/`UpdateCount` → DONE mit Zeilenzahl, `DONE_MORE` genau bei Folgeereignis, Szenario aus [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md), leeres Ergebnis | **bestätigt** | `ResponseRun.kt` (`start`, `row`, `finish`, `flushHeld`); Tests `Szenario Zeilen, Update-Count, Zeilen, leeres Ergebnis …` (Bits `COUNT\|MORE` ×3, dann `COUNT`), `leeres Ergebnis mit Metadaten`, `nicht abbildbare Metadaten schreiben kein COLMETADATA`, `ResultEnd wird bis zum naechsten Ereignis zurueckgehalten` (Steuerung über `CompletableDeferred`, keine Wanduhr); V14, V05, V08 tot |
| 1 (Rest) „stimmt mit Handvektoren **und dem Test-Decoder** überein“ | **teilweise** | zwei Handvektoren (`Handvektor einer Antwort mit einer Zeile`, `Handvektor DONE mit COUNT und MORE`); in `protocol/response` kommt `TokenDecoder` in **keinem** Test vor (nur `ErrorMapperTest` nutzt ihn). Befund V-2 |
| 2 Fehler im Strom (`Failure` vor `ResultStart`, nach Zeilen, Ausnahme aus `execute()`/`Flow`, Stellvertreter-Mapping, Ausgang „Verbindung ohne Abschluss schließen“, kein zweiter Resultset-Anfang, `close()` in jedem Ausgang) | **bestätigt** | `ResponseFailureTest` (15 Tests): alle genannten Fälle; Ausgang als Ergebniswert `SequenceOutcome.CLOSE_WITHOUT_COMPLETION`; `close()` einmal je Lauf, auch bei Abbruch (V01 tot); `kein zweites COLMETADATA nach einer Teilantwort` |
| 3 Reihenfolgeprüfung und Robustheit | **bestätigt** | `Reihenfolgeverstoss endet in TDS_INTERNAL_ERROR` (sieben Verstöße: Row vor ResultStart, doppeltes ResultEnd, ResultEnd ohne Start, ResultStart im offenen Ergebnis, UpdateCount im offenen Ergebnis, Row nach ResultEnd, Strom endet im offenen Ergebnis) und `Zufallsfolgen …`: 4000 Läufe, fester Startwert 20261030, getrenntes Modell, mit Ausnahme im Strom, Abbruch und abgelehntem Token; nie eine Ausnahme nach außen |
| Nachweis: jeder Test trägt `@spec SPEC-<NNN>` | **bestätigt** | alle 37 Tests in `protocol/response` tragen den Block-Tag (11, 4, 15, 7) |
| Nachweis: Aussage in KDoc/Spec/Vertrag am Test oder als Festlegung gekennzeichnet | **bestätigt, mit Rest** | KDoc des `ResponseSequencer` (Liste der Festlegungen), `SPEC-031`, `contracts/query-backend.md` stimmen überein (Abschnitt 5); Rest: V-6 |
| `make gates` grün | **teilweise belegt** | `test`, `detekt` auf gepinntem Stand grün, `docs-check` grün; Gesamtlauf nicht gefahren (Abschnitt 1) |
| Review-Report liegt vor | **bestätigt** | [2026-10-30-d7a7536-slice-backend-sequenzierer.md](2026-10-30-d7a7536-slice-backend-sequenzierer.md) |
| Doku-Update (`SPEC-031`, `contracts/query-backend.md`) | **bestätigt** | beide führen die zwölf Festlegungen deckungsgleich (Abschnitt 5) |
| Closure-Notiz mit Lerneintrag; Register fortgeschrieben; Paarungen | **offen** | §7 leer; unter `docs/plan/planning/observations/BEO-TDS/*/evidence/` liegt kein Beleg dieses Slice (Closure-Pflicht, kein Defekt des Codes) |
| Jedes Risiko aus §6 trägt einen Ausgang | **offen** | §6 trägt für die Nutzerentscheidungen und den Rest-Block Texte, aber noch „offen bis zur Closure“ (Befund V-8) |

### 4.2 slice-fehler-mapping

| DoD-Punkt | Urteil | Beleg |
|---|---|---|
| 1 Mapping-Funktion ohne Netz/Schreiben; je Code Nummer, Class, State, Text, Folge; ein Test je Code gegen Handvektoren; Vollständigkeitstest | **bestätigt, Vollständigkeit enger als geschrieben** | `ErrorCode.kt` (11 Codes, je eine `ErrorRule`), 11 Handvektor-Tests; der Vollständigkeitstest vergleicht die Enum mit einer im Test ausgeschriebenen Liste (Name ehrlich: `Die Enum gleicht der im Test ausgeschriebenen Liste …`), nicht mit der Tabelle in §4 (V-4); V11a/b, V12a/b, V13, V05 tot |
| 2 Ausnahme ohne Code erreicht den Client nur als `TDS_INTERNAL_ERROR` mit festem Text; Ausnahmetext weder in Bytes noch im Log, Log ohne Parameterwerte | **bestätigt** | `Der Text einer Ausnahme steht weder in der Antwort noch im Log` (ASCII und UTF-16LE, Log nur Typname), `Failure mit bekanntem Code …` (Text eines `Failure` nie in der Antwort), `Ausnahme und Reihenfolgeverstoss erreichen das Log` über Sequenzierer und echten Mapper; V09, V10 tot |
| 3 `spec/spezifikation.md` fortgeschrieben: Tabelle je Code, je Zeile Herkunft | **bestätigt, mit Rest** | Tabelle in [§4](../../spec/spezifikation.md#4-fehler-codes-und-logging-felder) hat 11 Zeilen, Herkunftsspalte je Zeile; Abgleich Tabelle ↔ Enum von Hand (Abschnitt 5.3): kein Unterschied; Rest V-3 (Widerspruch „nichts gemessen“ gegen `SPEC-012`) |
| `make gates` grün | **teilweise belegt** | wie 4.1 |
| Review-Report liegt vor | **bestätigt** | [2026-10-30-a8072b5-slice-fehler-mapping.md](2026-10-30-a8072b5-slice-fehler-mapping.md) |
| Doku-Update | **bestätigt** | §4 und `TDS-FA-06.a`-Verweis, Geschichte-Zeile |
| Closure-Notiz, Register, Paarungen | **offen** | §7 leer; kein Register-Beleg |
| Jedes Risiko aus §6 trägt einen Ausgang | **offen, aber näher** | sieben der neun Risiken tragen „weiter offen: → …“ (gültig), zwei „offen bis zur Closure“ (Abstimmung statement-/batch-abbrechend; Überschneidung mit dem Login, deren Gegenmittel der Gleichheitstest ist und die als „entfallen“ schließbar wäre) |

## 5. Entscheidungs-Konformität

### 5.1 Nutzerentscheidungen vom 2026-10-30

| Entscheidung | Umsetzung | Kennzeichnung als Festlegung | Test |
|---|---|---|---|
| `Failure` zählt als nächstes Ereignis der `DONE_MORE`-Vorschau | `ResponseRun.accept` flusht den zurückgehaltenen Abschluss mit `MORE` vor **jedem** Ereignis (auch `Failure`) und in `fault` vor der Ausnahme; für statement-abbrechende Fehler (R04) die Variante ohne `MORE` am DONE mit Fehlerstatus | KDoc des `ResponseSequencer` („Nutzer“), `SPEC-031`, `contracts/query-backend.md`, Slice §6 | `Failure nach ResultEnd gibt …`, `Ausnahme nach ResultEnd gibt …`, `Verstoss nach ResultEnd gibt …`; V14 tot |
| Zurückgehaltener Abschluss wird bei Cancel/Disconnect verworfen | Abbruchausnahme verlässt `run` vor `end()`; nichts wird geschrieben, `close()` läuft unter `NonCancellable` | wie oben | `Abbruch verwirft den zurueckgehaltenen Abschluss …`, im Fuzz der Zweig `HANG`; V01 tot |
| Reihenfolgeverstoß = Fall von `SPEC-048` | `violation()` → `errors.internal(null)` → `TDS_INTERNAL_ERROR`, `FAILED_CLOSE_CONNECTION`; Log-Marke `sequence-violation` | KDoc („Nutzer“), `SPEC-031` | `Reihenfolgeverstoss endet in TDS_INTERNAL_ERROR`, `Ausnahme und Reihenfolgeverstoss erreichen das Log`; V10 tot |
| `ReturnStatus`/`OutputParameter` abgelehnt, nicht abgebildet | `unsupported(…)` → `typeMapping(ProtocolError(INVALID_TYPE, …))`; kein RETURNSTATUS/RETURNVALUE | KDoc („Nutzer“), `SPEC-031`, Vertrag | `ReturnStatus und OutputParameter werden abgelehnt`, `Typabbildung liefert Bytes und Folge von protocol` |

Die Entscheidungen liegen **nur** als Text in KDoc, Spec, Vertrag und in Slice §6 („Quelle: Anweisung des Nutzers an den Implementer der Fix-Runde; sie liegen nur hier … nicht als eigenes Dokument“) vor; das ist offen gelegt, nicht belegt. Ich habe sie nicht gegen eine Primärquelle prüfen können (vgl. `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt`).

Eine Folge ist im Plan nicht ausgesprochen (V-6): Mit dem echten Mapper ist „abgelehnt“ gleich `TDS_UNSUPPORTED_TYPE`, Class 20, und schließt die Verbindung. Der Test `Typabbildung liefert Bytes und Folge von protocol` belegt `CLOSE_AFTER_REPLY`; die KDoc des Sequenzierers schreibt für diese Ablehnung unbedingt `SequenceOutcome.FAILED`.

### 5.2 Herkunftsstufen

- **Ausgangs-Hinweis des Auftrags richtiggestellt:** Die Messung R in [ADR-0007](../plan/adr/0007-fehler-und-attention.md) stammt von einem **echten SQL Server** 2022 (Proxy zerlegt die Tokens), der Referenzclient war der Client; der „Spike-Testserver“ ist dort die Messreihe T. Die KDoc des Sequenzierers („der Referenzclient gegen einen echten SQL Server zeigt `DONE[MORE|COUNT]` vor dem ERROR eines batch-abbrechenden Fehlers; am Produkt nicht gemessen“) gibt R05 richtig wieder; der Satz zu R04 (`DONE[MORE|ERR]`) ebenfalls. Die Stufe „Beleg nur für das Bit vor dem ERROR“ ist nicht breiter als der Beleg (Sequenzierer-F-7 behoben).
- **Nichts als gemessen ausgegeben, was es nicht ist:** Handvektoren heißen „von Hand abgeleitet“, die Standard-Abschnittsnummern aus Sequenzierer-F-4 sind aus den Tests entfernt (`grep 2.2.7` in `protocol/response` und `protocol/error`: leer). Fuzz heißt „zweiter Leser derselben Festlegungen, keine Messung“.
- **Gemessen am echten Server und im Spec-/Code-Text:** nur `DONE`-Bit vor dem ERROR (R05), Class ≤ 19 offen / 20 und 25 schließt (R10) als Hintergrund. Die Mapping-Tabelle sagt: kein Wert gemessen (siehe V-3 zur Ausnahme `SPEC-012`).
- **Zwei Taxonomien (Mapping-F-5):** behoben; `ErrorCode.kt` und §4 führen dieselben Stufen *Vorschlag · Setzung des Nutzers · Voreinstellung · Grundregel · Festlegung*, mit „Class 20 der Codes ohne Gruppe = Festlegung, nur die Folge = Grundregel“ (Mapping-F-3 behoben, Zeilen in §4 stimmen).
- `SPEC-031` unterscheidet die Festlegungen nicht je Satz nach Nutzer und Implementer (Kopf: „Setzungen des Nutzers und des Implementers“); KDoc und Vertrag tun es je Punkt. Das ist enger gelesen kein Widerspruch.

### 5.3 Spec-§4-Tabelle ↔ `ErrorCode` ↔ Tests

Von Hand abgeglichen (Nummer · Class · Folge; Tabelle gegen `ErrorCode.kt`):

| Code | Tabelle | Enum | Handvektor-Test |
|---|---|---|---|
| `TDS_PROTOCOL_ERROR` | 50006 · 20 · schließt | gleich | ja (Bytes `56 C3 00 00`, Class `14` hex) |
| `TDS_LOGIN_FAILED` | 18456 · 14 · schließt | gleich | ja, plus Byte-Gleichheit mit `LoginResponses.rejection` |
| `TDS_UNSUPPORTED_RPC` | 50002 · 16 · offen | gleich | ja |
| `TDS_UNSUPPORTED_TYPE` | 50003 · 20 · schließt | gleich | ja |
| `TDS_BACKEND_ERROR` | 50004 · 16 · offen | gleich | ja |
| `TDS_TIMEOUT` | 50005 · 20 · schließt | gleich | ja |
| `TDS_UNSUPPORTED_SQL` | 50001 · 16 · offen | gleich | ja |
| `TDS_SIM_UNKNOWN_OBJECT` | 208 · 20 · schließt | gleich | ja |
| `TDS_SIM_INVALID_VALUE` | 245 · 16 · offen | gleich | ja |
| `TDS_INTERNAL_ERROR` | 50007 · 20 · schließt | gleich | ja |
| `TDS_SHUTDOWN` | 50008 · 20 · schließt | gleich | ja |

Elf Zeilen, elf Enum-Einträge, elf Tests; die Positivliste des Backends in [§4](../../spec/spezifikation.md#4-fehler-codes-und-logging-felder) (sechs Codes) stimmt mit `ErrorMapper.BACKEND_CODES` überein. Dieser Abgleich ist **meine** Handarbeit, kein Sensor (V-4).

### 5.4 [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md) und [ADR-0007](../plan/adr/0007-fehler-und-attention.md)

- [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md): Option B (`Flow` mit Vorschau) umgesetzt wie entschieden; die Punkte, die der ADR „offen“ führt (`Failure` in der Vorschau, Schicksal des zurückgehaltenen Abschlusses, Reihenfolgeprüfung), sind jetzt Festlegungen und so gekennzeichnet; offen bleiben `OutOfMemoryError`, statement-/batch-abbrechend, Verzögerung durch die Vorschau, `curCmd` — alle in §6 und im Vertrag als offen geführt.
- [ADR-0007](../plan/adr/0007-fehler-und-attention.md): Grundregel A (im Zweifel schließen) umgesetzt (`ConnectionFate`), Class ≤ 19 offen, ≥ 20 schließt (Test der Schwelle, V11 tot), Login Class 14 schließt als benannte Ausnahme, kein Antwort-Teil bei nicht abschließbarem Strom (`CLOSE_WITHOUT_COMPLETION` → `CLOSE_WITHOUT_REPLY`). Nicht umgesetzt und benannt: SRVERR und weitere ERROR-Token bei Class 20/25 (R10).

## 6. Befunde der beiden Reviews: behoben oder offen?

### 6.1 Sequenzierer F-1 bis F-10

| Befund | Stand | Beleg |
|---|---|---|
| F-1 MEDIUM Schnittstelle zum Mapper | **behoben** | `ErrorMapping` liefert `MappedError(message, closesConnection)`; `ErrorMapper.sequencerMapping()`; `ResponseErrorMapperTest` (Bytes und Folge je Backend-Code); V04, V16 tot. Rest: kein Leser von `fateOf` (V-1) |
| F-2 MEDIUM Ausnahme verschwindet vor dem Log | **behoben** | `internal: (Throwable?) -> MappedError`; Log-Eintrag mit Typname bzw. Marke `sequence-violation`; V03, V09, V10 tot |
| F-3 MEDIUM Mutant E (kein `NonCancellable`) überlebte | **behoben** | Test mit suspendierendem `close()` (`onClose = { yield() }`); V01 tot |
| F-4 MEDIUM Standard-Abschnittsnummern ohne Quelle | **behoben** | aus Tests entfernt |
| F-5 LOW Fuzz-Orakel spiegelt Implementierung | **behoben, Grenze benannt** | getrenntes Modell (`Model`), Tokenformen und Ausgang geprüft; V07b und V08 sterben nur dort (V-5) |
| F-6 LOW Abbruchausnahme in zwei Pfaden | **behoben** | `rethrowCancellation`, Test `Abbruchausnahme aus dem Strom laeuft nach aussen`; V02 tot |
| F-7 INFO Stufe enger als Beleg | **behoben** | Text auf R05/R04 gestellt |
| F-8 INFO Geheimnisfreiheit hängt am Mapper | **ehrlich offen** | Zusage am Parameter (KDoc `ErrorMapping`), Plan §6: „ein Test … gibt es im Sequenzierer nicht“; V17 tot nur, weil der Vergleich mit `protocol()` greift |
| F-9 INFO Nutzerentscheidungen nur im Diff | **bearbeitet** | Block „Nutzerentscheidungen vom 2026-10-30“ in §6; Quelle bleibt eine Anweisung, kein Dokument |
| F-10 INFO Rückstau-Test fehlt bewusst | **unverändert, Zuständigkeit benannt** | `slice-backend-streaming-nachweis` |

### 6.2 Mapping F-1 bis F-9

| Befund | Stand | Beleg |
|---|---|---|
| F-1 MEDIUM Schnittstelle | **behoben** | siehe 6.1 F-1; `ErrorMapper.failure`/`protocol` bleiben als Einzelantwort |
| F-2 MEDIUM Vollständigkeitstest ohne Spec-Abgleich | **teilweise**: Name ehrlich, `@spec` entfernt, Lücke offen | Test `Die Enum gleicht der im Test ausgeschriebenen Liste …`; KDoc nennt, dass die Testumgebung die Spezifikation nicht enthält; in Plan §6 **nicht** als Risiko geführt (V-4) |
| F-3 MEDIUM „Grundregel“ überdehnt | **behoben** | §4: „Folge: Grundregel; Class 20: Festlegung, Einzelzuordnung offen“ |
| F-4 LOW Backend-Code ohne Positivliste | **behoben** | `BACKEND_CODES`, Test `Failure mit einem Code, den nur der Server erzeugt …`; V16 tot |
| F-5 LOW zwei Taxonomien | **behoben** | siehe 5.2 |
| F-6 INFO Negativpfad nur über einen Einstieg | **behoben** | `Jeder Einstieg schliesst bei nicht synchronem Strom ohne Antwort` |
| F-7 INFO `streamSynchronous = true` als Default | **unverändert** (INFO, kein Aufrufer) | Default steht weiter; es gibt keinen Aufrufer außerhalb der Tests |
| F-8 INFO Abweichung vom DONE-Muster bei Class 20/25 | **behoben** | §4 nennt SRVERR und weitere ERROR-Token (R10) |
| F-9 INFO Diagnose gegen Geheimnisfreiheit | **bestätigt, in §4 benannt** | „enger als der Satz in `SPEC-048`“ |

## 7. Schichten, Geheimnisfreiheit, `fateOf`

- **Schichtgrenzen.** `protocol.error` importiert `protocol.response` (`ErrorMapping`, `MappedError`, `SequenceOutcome`, `failedDone`), `protocol.token`, `protocol.wire`, `domain`, `transport.framing`; `protocol.response` importiert **nichts** aus `protocol.error` (Importliste gelesen). **Kein Zyklus**, Richtung eindeutig `error → response`. Kein Import aus `simulation`/`runtime`; `detekt` mit den Schichtregeln ist auf dem gepinnten Stand grün. Die Verklammerung läuft über `internal fun failedDone()`; ein DONE mit Fehlerstatus wird nur dort gebildet (Sequenzierer-Rest-Punkt 1 stimmt).
- **Geheimnisfreiheit.** Der Ausnahmetext erreicht weder den Tokenstrom noch den Log: `internal(Throwable)` bekommt die Ausnahme, der Mapper nimmt nur `javaClass.name`; der Text eines `Failure` wird nie gelesen; `ProtocolError.detail` wird nicht übernommen; Tests prüfen Bytes (ASCII, UTF-16LE) und `toString()` der Log-Einträge. Das Log ist in den Tests ein Sammler; ein Produktiv-Log gibt es noch nicht (Träger: Welle `welle-betrieb-diagnose`).
- **`ErrorMapper.fateOf(outcome)`.** **Total**: `when` über das Enum ohne `else`, der Compiler erzwingt es, und `fateOf bildet jeden Ausgang des Sequenzierers ab` iteriert `SequenceOutcome.entries`; V06 tot. **Von niemandem außer Tests gelesen**: `grep fateOf src/main` findet nur die eigene Definition und zwei KDoc-Zeilen; ebenso `sequencerMapping()`, `failure()`, `protocol()`, `unexpected()` — der ganze `ErrorMapper` hat keinen Aufrufer in `src/main`. Das ist die Zusage des Plans („der Aufrufer entsteht dort“), aber im Plan des Trägers nicht vermerkt (V-1).

## 8. Verifier-Befunde

| ID | Kat. | Befund | Quelle | Pfad |
|---|---|---|---|---|
| V-1 | MEDIUM | **Die Verdrahtung hat in keinem Plan einen benannten Träger.** `ErrorMapper` (und damit `fateOf`, `sequencerMapping`) hat keinen Leser in `src/main`. Das Mapping-§6 hängt es an `slice-fehler-zustandsuebergang`, der Auftrag nennt `slice-sql-batch-dispatch`; keiner der beiden Pläne enthält `fateOf`, `sequencerMapping`, `SequenceOutcome` oder `ErrorMapper` (grep leer), und `slice-fehler-zustandsuebergang` §1 sagt nur „führt die Folge aus, er legt sie nicht fest“. Szenario: der Dispatch-Slice baut `ResponseSequencer` mit einem eigenen `ErrorMapping` oder liest `FAILED` und lässt die Verbindung offen, weil die Zusage „Ausgang trägt die Folge“ an keiner Aufgabenliste hängt. Gegenmittel: in beide Pläne (Start-Trigger oder §3-Tabelle) den Satz „ruft `ErrorMapper.sequencerMapping()` und führt `fateOf(outcome)` aus“ aufnehmen. Passt zu `BEO-TDS/folge-einer-festlegung-im-nachbar-slice-unbenannt` (bisher 1×) und `BEO-TDS/offene-punkte-ohne-traeger`. | [slice-fehler-mapping §6](../plan/planning/done/slice-fehler-mapping.md), `slice-fehler-zustandsuebergang`, `slice-sql-batch-dispatch` | `ErrorMapper.kt` · `fun fateOf(outcome: SequenceOutcome): ConnectionFate` |
| V-2 | MEDIUM | **DoD-Wortlaut „Test-Decoder“ im Sequenzierer nicht erfüllt.** DoD-Punkt 1 verlangt Übereinstimmung der Tokenfolge „mit Handvektoren und dem Test-Decoder“. `TokenDecoder` kommt in `protocol/response` nirgends vor; belegt sind zwei Handvektoren (eine Zeile mit COUNT; `COUNT\|MORE`) und Tokenobjekt-Vergleiche. Ein Decoder-Lauf über die Bytes eines vollständigen Szenarios (Zeilen, Update-Count, Zeilen, leeres Ergebnis) fehlt. Entweder nachliefern oder die Closure-Notiz benennt die Abweichung. | [slice-backend-sequenzierer §2](../plan/planning/done/slice-backend-sequenzierer.md) | `ResponseMappingTest.kt` (kein `TokenDecoder`) |
| V-3 | LOW | **Widerspruch im Spec-Text zur Messung.** §4 sagt vor der Zuordnungstabelle „Kein Wert dieser Tabelle ist an einem echten SQL Server gemessen“; die `SPEC-012`-Zeile derselben Tabelle hält fest, an einem echten SQL Server sei einmal Nummer 18456, State 1, Class 14 beobachtet worden. `ErrorCode.kt` übernimmt die schwächere Fassung („gemessen … nur ‚genau ein ERROR vor DONE‘“). Das ist konservativ, aber ein Satz widerspricht dem anderen; der gemessene Text („nennt den Benutzernamen“) weicht zudem vom festen Text `Login failed.` ab. | AGENTS.md §3.8 | `spec/spezifikation.md` · „Kein Wert dieser Tabelle“ |
| V-4 | LOW | **Spec-Tabelle ↔ Enum nicht maschinell gehalten, und die Lücke steht nicht in Plan §6.** Der Vollständigkeitstest prüft die Enum gegen eine Kopie im Test (ehrlich benannt); ein Abgleich mit §4 existiert nur als meine Handarbeit (5.3). Eine geänderte Nummer in §4 lässt alles grün. Ebenso ist der einzige Test ohne `@spec` (`Die Enum gleicht …`) eine bewusste Abweichung von der Nachweis-Pflicht des Schwester-Slice-Musters; im Mapping-Plan ist die Lücke kein Risiko. | AGENTS.md §3.8, Plan §6 | `ErrorMapperTest.kt` · `Die Enum gleicht der im Test ausgeschriebenen Liste der Codes mit eigener Nummer` |
| V-5 | LOW | **Zwei Mutanten sterben nur am Fuzz.** V07b (Ablehnung des DONE hinter dem ERROR ignoriert) und V08 (Ablehnung des letzten DONE ignoriert) färbt kein deterministischer Test; es gibt `abgelehntes ERROR schliesst ohne DONE`, aber keinen Test „abgelehntes DONE hinter ERROR“ und keinen „abgelehntes abschließendes DONE“. Der Fuzz ist mit festem Startwert deterministisch (kein Befund gegen die Wirksamkeit), aber die Zusage „synchron heißt: jedes Token angenommen“ hängt an einem einzigen Test. | [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität) | `ResponseFailureTest.kt` (fehlende Fälle), `ResponseOrderTest.kt` |
| V-6 | LOW | **KDoc sagt `FAILED`, der echte Mapper liefert `FAILED_CLOSE_CONNECTION`.** Die KDoc des `ResponseSequencer` schreibt für `ReturnStatus`/`OutputParameter` „[`SequenceOutcome.FAILED`]“; mit dem echten Mapper ist die Ablehnung `TDS_UNSUPPORTED_TYPE` (Class 20) und schließt die Verbindung. Dasselbe gilt für jeden Typfehler (zum Beispiel Decimal mit Precision über 38): der Client verliert die Verbindung statt einen definierten Fehler zu sehen und weiterzuarbeiten. Die Einzelzuordnung ist im Mapping-Plan als offen geführt (Entscheidung des Nutzers steht aus), die Wirkung auf diese Fälle ist nicht ausgesprochen. | [TDS-FA-10](../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), AGENTS.md §3.8 | `ResponseSequencer.kt` · KDoc „ReturnStatus … werden mit einem Fehler der Typabbildung abgelehnt (… SequenceOutcome.FAILED)“ |
| V-7 | INFO | **`make gates` nicht als Ganzes gefahren und Arbeitsbaum nicht stabil.** Siehe Stand-Hinweis. Wer den Gate-Nachweis braucht, fährt `make gates` auf einem festen Stand erneut; ein roter `detekt`-Lauf durch eine fremde Datei im Arbeitsbaum ist keine Aussage über diese Slices. | AGENTS.md §6 | – |
| V-8 | INFO | **Closure-Pflichten stehen aus (kein Code-Defekt).** Beide Pläne: §7 leer, DoD unabgehakt, kein Register-Beleg, §6 enthält im Sequenzierer-Plan neben den Nutzerentscheidungen noch die älteren Punkte „Entscheidungsbedarf … Ausgang: offen bis zur Closure“ für bereits entschiedene Fragen (Failure in der Vorschau, Schicksal des Abschlusses, Reihenfolgeverstoß, ReturnStatus). Jedes dieser Risiken braucht bei der Closure genau einen der drei Ausgänge. | Plan §6, `modul-05-planning-harness.md` | beide Slice-Pläne |
| V-9 | INFO | **Ausnahmen und `close()`.** Eine Ausnahme aus `close()` (und eine fremde Ausnahme der Senke) läuft unverändert nach außen und verdeckt dabei den Ausgang; KDoc: „dafür gibt es keine Zusage“. Ehrlich benannt, hier nur der Vollständigkeit halber; zuständig `slice-backend-streaming-nachweis`. | [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md) | `ResponseSequencer.kt` · KDoc letzter Punkt |
| V-10 | LOW | **`AuthExecutorTest` ist unter Last flaky; die Zusage „Passwort überschrieben, sobald der Aufruf endet“ ist für den wartenden, abgebrochenen Aufruf nicht geordnet.** Reproduziert: 6 rot in 4000 Wiederholungen bei Hostlast über 100, Spätschreibung 1 bis 11 ms nach `join()` (Abschnitt 3). Kein Produkt-Leck, ein Test mit ungedeckter Ordnungsannahme und eine KDoc-Zusage, die breiter ist als ihr Beleg. Gegenmittel zur Wahl des Trägers: im Test begrenzt warten (kein `Thread.sleep` ohne Grenze) oder das Überschreiben im Produkt vor die Abschlussmeldung legen. | AGENTS.md §3.8, `BEO-TDS/flake-fix-ohne-reproduzierten-fehler` | `AuthExecutorTest.kt` · `Ein wartender Aufruf beginnt nach dem Abbruch nie und das Passwort ist ueberschrieben`; `AuthExecutor.kt` · `call.invokeOnCompletion { request.password.wipe() }` |

## 9. Was ich nicht geprüft habe

- Keine Messung am echten SQL Server und am Referenzclient (nicht Teil der Slices; `BEO-TDS/messung-am-echten-sql-server-fehlt`, Träger `slice-qa-reproduzierbarkeit`).
- Kein Gesamtlauf `make gates`, kein `make spec-abdeckung`; die Abdeckungsmatrix `docs/plan/spec-abdeckung.md` wurde nicht neu erzeugt.
- Nutzerentscheidungen nicht gegen eine Primärquelle; der Text des Auftrags und die Pläne sind die einzige Quelle.
- Keine Last- oder Rückstau-Messung des Sequenzierers (ausdrücklich `slice-backend-streaming-nachweis`).

## 10. Übergabe an den Planner

- **Slice-Zustand:** beide Slices bleiben in `in-progress/`. Code, Spec und Tests tragen die Zusagen; schließbar werden sie erst mit Closure-Notiz, §6-Ausgängen und Register-Eintrag.
- **Vor der Closure:** V-1 (Träger der Verdrahtung in die Pläne schreiben), V-2 (Decoder-Test nachliefern oder Abweichung in §7 benennen), V-8 (Ausgänge je Risiko).
- **Kann mit Begründung angenommen werden:** V-3 bis V-6; V-10 gehört dem Träger von `slice-handshake-login` (Spec-Satz glätten, Lücke in Mapping-§6 aufnehmen, zwei deterministische Tests ergänzen, KDoc-Satz angleichen).
- **Register-Kandidaten (Entscheidung beim Planner):** V-1 als zweiter Beleg für `BEO-TDS/folge-einer-festlegung-im-nachbar-slice-unbenannt`; V-5 und V-4 als Beleg für `BEO-TDS/aussagen-ueber-messung-hinaus` nur, wenn die Aussage breiter als der Test war — hier ist sie enger geschrieben, daher kein Beleg.
