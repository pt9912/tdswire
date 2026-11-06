# Öffentliche Fläche der Bibliothek: Ausgangszahlen vom 2026-11-06

Messprotokoll zu [TDS-QA-05](../../spec/lastenheft.md#tds-qa-05--wartbarkeit) und [TDS-RB-04](../../spec/lastenheft.md#tds-rb-04--externes-backend) (`AGENTS.md` §5 Regel 7). Es hält fest, was gemessen wurde, nicht, ob eine Anforderung belegt ist und nicht, was der Schnitt ist: er steht in [slice-oeffentliche-flaeche-verkleinern-und-belegen](../plan/planning/in-progress/slice-oeffentliche-flaeche-verkleinern-und-belegen.md) §3 (Liefer-Punkt 1). Gegenstand ist die Neumessung der Ausgangszahlen nach der Anweisung des Slice: öffentliche Deklarationen des Hauptmoduls gegen die Namensliste von [SPEC-059](../../spec/spezifikation.md#einstieg-des-integrators-vertrag-zu-spec-059) und gegen die Verwendungen im Einbettungstest und im Testserver.

## Bedingungen

| Angabe | Wert |
|---|---|
| Datum | 2026-11-06 |
| Stand | Commit `4b3fc138` (Zweig `slice-flaeche-schnitt-vorlage`), Arbeitsbaum ohne Änderung am Quelltext |
| Werkzeug | `grep`, `sed`, `awk`, `sort`, `comm` auf dem Host, nur lesend; kein Übersetzungslauf, keine Toolchain (`AGENTS.md` §3.1 betrifft JDK und Gradle) |
| Pfade | Hauptmodul `src/main`, Einbettungstest `src/integrator`, Testserver `src/testserver/kotlin` (Quelltext) und `src/testserver/test` (Tests des Testservers, gesondert gezählt); der Pfad des Testservers liegt auf diesem Stand unter `src/testserver` |
| Zählung | Einzelmessung, ein Lauf je Zahl; Wortvergleich auf Nicht-Kommentarzeilen (Zeilen, die mit `*`, `//` oder `/*` beginnen, zählen nicht), `import`-Zeilen zählen mit |

## Befehle und Zahlen

Alle Befehle laufen im Wurzelverzeichnis des Repos.

| Nr. | Zahl | Befehl | Ergebnis |
|---|---|---|---|
| Z1 | öffentliche Typdeklarationen im Hauptmodul (Klassen, Schnittstellen, Objekte, Aufzählungen, geschachtelte Fälle) | `grep -rhoE '^\s*public (sealed \|data \|fun \|enum \|abstract )?(class\|interface\|object) [A-Za-z0-9]+' src/main --include=*.kt \| awk '{print $NF}' \| sort -u \| wc -l` | 83 |
| Z2 | Zeilen mit `public` im Hauptmodul (Typen und Mitglieder zusammen; `explicitApi` verlangt das Wort an jeder öffentlichen Deklaration) | `grep -rn '^\s*public ' src/main --include=*.kt \| wc -l` | 130 |
| Z3 | Typnamen in `SPEC-059` („Die Namen nach Package“) | `grep '^\| Zusage \|' spec/spezifikation.md \| sed 's/.*Die Namen nach Package//; s/Für die Signaturen.*//' \| grep -oE '`[^`]+`' \| tr -d '`' \| sed 's/(.*//' \| grep -E '^[A-Z]' \| grep -vx NONE \| sort -u \| wc -l` | 66 |
| Z4 | Namen der Spezifikation, die im Code nicht öffentlich sind | Z3-Liste gegen Z1-Liste mit `comm -23` | 0 |
| Z5 | öffentliche Typen des Codes, die die Spezifikation nicht mit Namen nennt | `comm -13` | 17: die 15 Fälle von `QueryEvent` (7) und `SqlType` (8), für die die Spezifikation „mit seinen Fällen“ sagt, und `FilterTerm.In`, `FilterTerm.IsNull` (die Spezifikation nennt `FilterTerm`, nicht seine Fälle) |
| Z6 | Typnamen, die der Einbettungstest `src/integrator` per `import` nennt | `grep -rhoE '^import io\.[a-z]*\.pt9912\.tdswire\.(config\|domain\|port\|runtime\|simulation)[A-Za-z.]*' src/integrator --include=*.kt \| awk -F. '{print $NF}' \| sort -u \| wc -l` | 30 |
| Z7 | dasselbe für `src/testserver/kotlin` | Befehl wie Z6 | 24 |
| Z8 | Vereinigung Z6 und Z7 (nur `import`; geschachtelte Fälle stehen nicht in `import`) | `sort -u` über beide Listen | 35 |
| Z9 | dasselbe für `src/testserver/test` | Befehl wie Z6 | 18 (39 mit Z8 zusammen) |
| Z10 | von den 83 Typen aus Z1 mit mindestens einem Treffer (Wortvergleich, auch geschachtelte Fälle) in `src/integrator` | Schleife unter der Tabelle | 42 |
| Z11 | dasselbe in `src/testserver/kotlin` | Schleife unter der Tabelle | 38 |
| Z12 | dasselbe in einem von beiden | Schleife unter der Tabelle | 54 |
| Z13 | dasselbe in einem von beiden oder in `src/testserver/test` | Schleife unter der Tabelle | 58 |
| Z14 | Typen ohne Treffer in `src/integrator` und `src/testserver/kotlin` | Schleife unter der Tabelle | 29, davon 4 nur in `src/testserver/test` (`ConfigValidation`, `SimQuery`, `UserListError`, `UserListErrorKind`) und 25 nirgends |
| Z15 | Typen der Spezifikation (Z3) ohne Treffer in `src/integrator` und `src/testserver/kotlin` | Z14-Liste ohne `FilterTerm.In`, `FilterTerm.IsNull`, `OutputParameter`, `ReturnStatus`, `UpdateCount` | 24 |

Die Schleife für Z10 bis Z14 (Spalten der Tabelle unten):

```
for n in $(cat typen.txt); do
  i=$(grep -rh --include=*.kt -v -E '^\s*(\*|//|/\*)' src/integrator | grep -cw "$n")
  t=$(grep -rh --include=*.kt -v -E '^\s*(\*|//|/\*)' src/testserver/kotlin | grep -cw "$n")
  tt=$(grep -rh --include=*.kt -v -E '^\s*(\*|//|/\*)' src/testserver/test | grep -cw "$n")
  echo "$n $i $t $tt"
done
```

`typen.txt` ist die Ausgabe von Z1 (ohne `wc -l`). Die Spalte „erste Stelle“ nennt je Quellbaum die erste Zeile mit einem Treffer ohne `import` und ohne Kommentar (`grep -rnw`, Datei und Zeilennummer).

## Gegenüber der Ausgangsmessung des Slice (grobe Namenssuche vom 2026-11-05)

| Angabe des Slice | Neumessung | Anmerkung |
|---|---|---|
| 67 Namen in `SPEC-059` | 66 Typnamen (Z3) | Die Zählweise des Slice ist nicht rekonstruiert; die Differenz von 1 ist ungeklärt (keine Behauptung, welche Zählung stimmt) |
| 31 vom Testserver verwendet | 24 `import`-Namen (Z7), 38 Typen mit Treffer (Z11) | Die Zählweisen sind verschieden (`import` gegen Wortvergleich einschließlich geschachtelter Fälle); der Vergleich mit 31 ist nicht möglich |
| 37 vom Einbettungstest verwendet | 30 `import`-Namen (Z6), 42 Typen mit Treffer (Z10) | wie oben |
| 23 von keinem verwendet | 24 Namen der Spezifikation (Z15), davon 20 ohne jeden Treffer, auch nicht in den Tests des Testservers | Neu gegenüber den 23: `ConfigValidation` (Treffer nur in `ConfigLoaderTest`; der Lader des Testservers nennt sie nur noch im Kommentar). Dass der Slice die Fälle `OutputParameter`, `ReturnStatus`, `UpdateCount` nicht zählte, passt zu seiner Liste |

## Grenzen der Zahlen

- Wortvergleich ist kein Aufrufbeleg: ein Treffer kann in einer Zeichenkette oder einem toten Aufruf stehen (`AGENTS.md` §3.8). Kurze Wörter (`In`, `None`, `Row`, `Date`, `Time`, `Failure`, `Custom`, `Valid`, `Invalid`, `Simulation`, `Running`, `Aborted`) können fremde Namen treffen: `Invalid` trifft im Testserver auch `ConfigResult.Invalid` des Testservers (`Main.kt:69`); der Treffer für `UserListResult.Invalid` steht in `ConfigFiles.kt:89`; die Zahlen 4 (T) und 1 (TT) der Zeile `Invalid` zählen `ConfigResult.Invalid` mit, in `src/testserver/test` trifft nur dieser.
- Ein Name, der nur als Typ eines Rückgabewerts oder einer Eigenschaft vorkommt, hat keinen Treffer, ist aber erreichbar (zum Beispiel `ResultColumn` in `QueryEvent.ResultStart`).
- Unit-Tests in `src/test` mit `internal`-Zugriff sind nicht Teil der Zählung: sie belegen Verhalten, nicht Erreichbarkeit über die öffentliche Fläche.
- Mitglieder (Felder, Konstanten, Funktionen) sind nicht gezählt; Z2 zählt sie nur als Zeilen. Die Tabelle gilt für Typnamen.
- Z6 bis Z9 zählen `import`; wer ohne `import` qualifiziert (`io.github…`), wird nicht gefunden (ein Platzhalter-`import` kommt in beiden Quellbäumen nicht vor: `grep -rn 'tdswire\.[a-z.]*\*' src/integrator src/testserver` ohne Treffer).

## Aborted.reason gegen die Felder der Abbruch-Arten

Gelesen aus dem Quelltext, nicht ausgeführt; Stand wie oben.

| Art (Felder) | Text von `StartOutcome.Aborted.reason` | Stelle |
|---|---|---|
| `StartRefused` (`reason`: `StartRefusal`) | fester Text je Grund: „Backend und Simulations-Backend gleichzeitig konfiguriert“ (`BACKEND_AND_SIMULATION`), „Tabellen ungueltig: ein Name kommt doppelt vor“ (`TABLE_NAME_DUPLICATE`) | `Bootstrap.kt:133-142`, `SimulationSetup.kt:32, 39` |
| `TlsStartRejected` (`part`, `form`) | „TLS-Konfiguration: <Teil> (<Form>)“ | `Bootstrap.kt:113`, `ListenerConfig.kt:54` |
| `BindFailed` (`address`, `port`, `exceptionType`) | „Port belegt: <Adresse>:<Port>“ (Typname `null`) oder „Binden an <Adresse>:<Port> gescheitert: <Typname>“ | `Bootstrap.kt:193-194`, `ListenerConfig.kt:59, 64` |

Alle Felder stehen im Text, als Prosa; `Aborted.reason` ist ein `String`, kein Feld ist strukturiert lesbar. `ConfigInvalid` dagegen deckt sich mit `Aborted.issues` (`TelemetryEmbeddingTest.kt:135`).

## Tabelle je Typname

Spalten: Package; `SPEC-059`: `Name` (die Spezifikation nennt den Namen), `Fall` (gedeckt durch „mit seinen Fällen“), `-` (nicht genannt); Zahl der Zeilen mit Treffer in `src/integrator` (I), `src/testserver/kotlin` (T) und `src/testserver/test` (TT); erste Stelle je Quellbaum.

| Typ | Package | `SPEC-059` | I | T | TT | erste Stelle |
|---|---|---|---|---|---|---|
| `Aborted` | `runtime` | Name | 3 | 5 | 9 | I: TelemetryEmbeddingTest.kt:133; T: Main.kt:45; TT: TestserverStartTest.kt:107 |
| `AcceptFailed` | `domain.telemetry` | Name | 0 | 0 | 0 | I: -; T: -; TT: - |
| `AdapterFailed` | `domain.telemetry` | Name | 0 | 0 | 0 | I: -; T: -; TT: - |
| `AuthSettings` | `config` | Name | 6 | 2 | 0 | I: TelemetryEmbeddingTest.kt:50; T: ConfigLoader.kt:79; TT: - |
| `BackendSource` | `runtime` | Name | 12 | 10 | 6 | I: TelemetryEmbeddingTest.kt:77; T: Main.kt:42; TT: TestserverStartTest.kt:58 |
| `BindFailed` | `domain.telemetry` | Name | 0 | 0 | 0 | I: -; T: -; TT: - |
| `Bool` | `domain` | Fall | 0 | 2 | 2 | I: -; T: SimColumnTypes.kt:33; TT: SimDescriptionReaderTest.kt:79 |
| `ConfigInvalid` | `domain.telemetry` | Name | 2 | 0 | 0 | I: TelemetryEmbeddingTest.kt:135; T: -; TT: - |
| `ConfigIssue` | `config` | Name | 0 | 34 | 32 | I: -; T: ConfigLoader.kt:24; TT: ConfigLoaderTest.kt:94 |
| `ConfigValidation` | `config` | Name | 0 | 0 | 4 | I: -; T: -; TT: ConfigLoaderTest.kt:101 |
| `ConnectionFailed` | `domain.telemetry` | Name | 0 | 0 | 0 | I: -; T: -; TT: - |
| `Custom` | `runtime` | Name | 2 | 2 | 0 | I: TelemetryEmbeddingTest.kt:77; T: Main.kt:91; TT: - |
| `Date` | `domain` | Fall | 0 | 2 | 2 | I: -; T: SimColumnTypes.kt:35; TT: SimDescriptionReaderTest.kt:81 |
| `DateTime` | `domain` | Fall | 0 | 2 | 3 | I: -; T: SimColumnTypes.kt:49; TT: SimDescriptionReaderTest.kt:83 |
| `Decimal` | `domain` | Fall | 0 | 4 | 6 | I: -; T: SimColumnTypes.kt:64; TT: SimDescriptionReaderTest.kt:80 |
| `EncryptionNotOffered` | `domain.telemetry` | Name | 2 | 0 | 0 | I: TelemetryEmbeddingTest.kt:107; T: -; TT: - |
| `EncryptionSetting` | `config` | Name | 5 | 8 | 3 | I: TelemetryEmbeddingTest.kt:56; T: ConfigLoader.kt:104; TT: ConfigLoaderTest.kt:66 |
| `EncryptionWeakened` | `domain.telemetry` | Name | 2 | 0 | 0 | I: TelemetryEmbeddingTest.kt:105; T: -; TT: - |
| `EventLevel` | `domain.telemetry` | Name | 0 | 0 | 0 | I: -; T: -; TT: - |
| `Failure` | `domain` | Fall | 1 | 0 | 0 | I: EmbeddingTest.kt:210; T: -; TT: - |
| `FilterTerm` | `simulation` | Name | 0 | 0 | 0 | I: -; T: -; TT: - |
| `In` | `simulation` | - | 0 | 0 | 0 | I: -; T: -; TT: - |
| `Int32` | `domain` | Fall | 1 | 6 | 6 | I: EmbeddingTest.kt:169; T: SimColumnTypes.kt:31; TT: SimDescriptionReaderTest.kt:75 |
| `Int64` | `domain` | Fall | 0 | 2 | 2 | I: -; T: SimColumnTypes.kt:32; TT: SimDescriptionReaderTest.kt:78 |
| `Invalid` | `config.auth` | Name | 0 | 4 (1 echt) | 1 (0 echt) | I: -; T: ConfigFiles.kt:89; TT: - (ConfigTestSupport.kt:17 ist `ConfigResult.Invalid`) |
| `IsNull` | `simulation` | - | 0 | 0 | 0 | I: -; T: -; TT: - |
| `IssueKind` | `config` | Name | 4 | 19 | 27 | I: EmbeddingTest.kt:125; T: ConfigLoader.kt:100; TT: ConfigLoaderTest.kt:94 |
| `ListenSettings` | `config` | Name | 7 | 3 | 2 | I: TelemetryEmbeddingTest.kt:56; T: ConfigLoader.kt:54; TT: ConfigUsersFileTest.kt:40 |
| `LoginAccepted` | `domain.telemetry` | Name | 3 | 0 | 0 | I: TelemetryEmbeddingTest.kt:108; T: -; TT: - |
| `LoginRejected` | `domain.telemetry` | Name | 0 | 0 | 0 | I: -; T: -; TT: - |
| `LoginRejectionReason` | `domain.telemetry` | Name | 0 | 0 | 0 | I: -; T: -; TT: - |
| `LoginSettings` | `config` | Name | 0 | 3 | 0 | I: -; T: ConfigLoader.kt:55; TT: - |
| `None` | `runtime` | Name | 5 | 0 | 1 | I: TelemetryEmbeddingTest.kt:131; T: -; TT: ConfigUsersFileTest.kt:45 |
| `OutputParameter` | `domain` | Fall | 0 | 0 | 0 | I: -; T: -; TT: - |
| `ParameterDirection` | `domain` | Name | 0 | 0 | 0 | I: -; T: -; TT: - |
| `PasswordChars` | `config.auth` | Name | 2 | 3 | 0 | I: EmbeddingTest.kt:127; T: ConfigLoader.kt:154; TT: - |
| `PlainTestModeActive` | `domain.telemetry` | Name | 2 | 0 | 0 | I: TelemetryEmbeddingTest.kt:106; T: -; TT: - |
| `Principal` | `domain` | Name | 0 | 0 | 0 | I: -; T: -; TT: - |
| `QueryBackend` | `port` | Name | 4 | 3 | 0 | I: TelemetryEmbeddingTest.kt:154; T: KompatibilitaetsServer.kt:141; TT: - |
| `QueryEvent` | `domain` | Name | 9 | 0 | 0 | I: TelemetryEmbeddingTest.kt:158; T: -; TT: - |
| `QueryExecution` | `port` | Name | 5 | 2 | 0 | I: TelemetryEmbeddingTest.kt:157; T: KompatibilitaetsServer.kt:143; TT: - |
| `QueryParameter` | `domain` | Name | 0 | 0 | 0 | I: -; T: -; TT: - |
| `QueryRequest` | `domain` | Name | 4 | 2 | 0 | I: TelemetryEmbeddingTest.kt:157; T: KompatibilitaetsServer.kt:143; TT: - |
| `RequestFailed` | `domain.telemetry` | Name | 0 | 0 | 0 | I: -; T: -; TT: - |
| `RequestSettings` | `config` | Name | 0 | 3 | 0 | I: -; T: ConfigLoader.kt:56; TT: - |
| `ResultColumn` | `domain` | Name | 4 | 0 | 0 | I: TelemetryEmbeddingTest.kt:155; T: -; TT: - |
| `ResultEnd` | `domain` | Fall | 2 | 0 | 0 | I: TelemetryEmbeddingTest.kt:159; T: -; TT: - |
| `ResultStart` | `domain` | Fall | 2 | 0 | 0 | I: TelemetryEmbeddingTest.kt:159; T: -; TT: - |
| `ReturnStatus` | `domain` | Fall | 0 | 0 | 0 | I: -; T: -; TT: - |
| `Row` | `domain` | Fall | 2 | 0 | 0 | I: TelemetryEmbeddingTest.kt:159; T: -; TT: - |
| `RowFilter` | `simulation` | Name | 0 | 0 | 0 | I: -; T: -; TT: - |
| `RowSource` | `simulation` | Name | 2 | 13 | 4 | I: EmbeddingTest.kt:177; T: SimDescriptionReader.kt:86; TT: TestserverStartTest.kt:68 |
| `Running` | `runtime` | Name | 2 | 2 | 6 | I: TelemetryEmbeddingTest.kt:78; T: Main.kt:47; TT: TestserverStartTest.kt:47 |
| `ServerConfig` | `config` | Name | 9 | 3 | 6 | I: TelemetryEmbeddingTest.kt:56; T: ConfigLoader.kt:20; TT: ConfigLoaderTest.kt:259 |
| `ServerStarted` | `domain.telemetry` | Name | 3 | 0 | 0 | I: TelemetryEmbeddingTest.kt:104; T: -; TT: - |
| `ServerStopped` | `domain.telemetry` | Name | 2 | 0 | 0 | I: TelemetryEmbeddingTest.kt:109; T: -; TT: - |
| `SimCatalog` | `simulation` | Name | 0 | 4 | 2 | I: -; T: Main.kt:91; TT: NameFoldTest.kt:44 |
| `SimColumn` | `simulation` | Name | 2 | 25 | 18 | I: EmbeddingTest.kt:169; T: SimDescriptionReader.kt:84; TT: SimDescriptionReaderTest.kt:75 |
| `SimQuery` | `simulation` | Name | 0 | 0 | 2 | I: -; T: -; TT: SimDescriptionReaderTest.kt:653 |
| `SimTable` | `simulation` | Name | 3 | 22 | 9 | I: EmbeddingTest.kt:168; T: DescriptionFile.kt:8; TT: SimDescriptionReaderTest.kt:24 |
| `Simulation` | `runtime` | Name | 1 | 3 | 3 | I: EmbeddingTest.kt:98; T: Main.kt:42; TT: TestserverStartTest.kt:58 |
| `SimulationBackend` | `simulation` | Name | 0 | 4 | 0 | I: -; T: Main.kt:91; TT: - |
| `SimulationBackendReady` | `domain.telemetry` | Name | 0 | 0 | 0 | I: -; T: -; TT: - |
| `SqlType` | `domain` | Name | 5 | 37 | 31 | I: TelemetryEmbeddingTest.kt:155; T: SimDescriptionReader.kt:29; TT: SimDescriptionReaderTest.kt:75 |
| `StartOutcome` | `runtime` | Name | 7 | 10 | 18 | I: TelemetryEmbeddingTest.kt:78; T: Main.kt:45; TT: TestserverStartTest.kt:47 |
| `StartRefusal` | `domain.telemetry` | Name | 0 | 0 | 0 | I: -; T: -; TT: - |
| `StartRefused` | `domain.telemetry` | Name | 0 | 0 | 0 | I: -; T: -; TT: - |
| `TdsServer` | `runtime` | Name | 9 | 2 | 2 | I: TelemetryEmbeddingTest.kt:43; T: Main.kt:76; TT: ConfigUsersFileTest.kt:45 |
| `TelemetryEvent` | `domain.telemetry` | Name | 3 | 0 | 0 | I: TelemetryEmbeddingTest.kt:98; T: -; TT: - |
| `TelemetrySink` | `domain.telemetry` | Name | 4 | 0 | 0 | I: TelemetryEmbeddingTest.kt:76; T: -; TT: - |
| `Time` | `domain` | Fall | 0 | 2 | 2 | I: -; T: SimColumnTypes.kt:36; TT: SimDescriptionReaderTest.kt:82 |
| `TlsFirstNotOffered` | `domain.telemetry` | Name | 0 | 0 | 0 | I: -; T: -; TT: - |
| `TlsFirstRefusal` | `domain.telemetry` | Name | 0 | 0 | 0 | I: -; T: -; TT: - |
| `TlsHandshakeFailed` | `domain.telemetry` | Name | 0 | 0 | 0 | I: -; T: -; TT: - |
| `TlsKeyMaterial` | `config` | Name | 2 | 4 | 2 | I: EmbeddingTest.kt:127; T: ConfigLoader.kt:127; TT: ConfigUsersFileTest.kt:213 |
| `TlsStartRejected` | `domain.telemetry` | Name | 0 | 0 | 0 | I: -; T: -; TT: - |
| `UnicodeText` | `domain` | Fall | 3 | 9 | 5 | I: TelemetryEmbeddingTest.kt:155; T: SimColumnTypes.kt:34; TT: SimDescriptionReaderTest.kt:76 |
| `UpdateCount` | `domain` | Fall | 0 | 0 | 0 | I: -; T: -; TT: - |
| `UserList` | `config.auth` | Name | 4 | 7 | 0 | I: TelemetryEmbeddingTest.kt:51; T: ConfigLoader.kt:113; TT: - |
| `UserListError` | `config.auth` | Name | 0 | 0 | 6 | I: -; T: -; TT: ConfigUsersFileTest.kt:31 |
| `UserListErrorKind` | `config.auth` | Name | 0 | 0 | 8 | I: -; T: -; TT: ConfigUsersFileTest.kt:30 |
| `UserListResult` | `config.auth` | Name | 4 | 3 | 0 | I: TelemetryEmbeddingTest.kt:51; T: ConfigFiles.kt:87; TT: - |
| `Valid` | `config.auth` | Name | 2 | 1 | 0 | I: TelemetryEmbeddingTest.kt:51; T: ConfigFiles.kt:87; TT: - |

## Nachmessung nach der Umsetzung (Sensor)

Stand: Arbeitsbaum der Umsetzung (Commits nach `6b28cd69`), derselbe Tag, Einzelmessung je Lauf. `make flaeche` (Stage `flaeche`, `tools/flaeche-pruefung.sh`) meldete 61 Namen der Liste in `SPEC-059`, alle öffentlich deklariert und in `src/integrator` oder `testserver/kotlin` belegt (Pfad des Testservers jetzt `testserver/kotlin`, nicht `src/testserver`). Vor der Ergänzung des Einbettungstests meldete derselbe Lauf genau einen Namen ohne Beleg: `EventLevel`; mit `Principal` wieder öffentlich meldete er `Principal` als öffentliche Deklaration ohne Eintrag. Die Zahl 61 ist die Zahl der Typnamen der Liste, nicht der Mitglieder. Die Zählweise des Sensors weicht von der der Tabelle oben ab: ein geschachtelter Name zählt nur als `Eltern.Name`, ein Treffer in `src/testserver/test` zählt nicht.

## Mutationen nach dem Review (Einzelläufe von `make test`, 2026-11-06)

Jede Mutation einzeln am Produktcode, danach zurückgesetzt. (1) Zwei Klassen mit derselben Art (`StartRefused` mit `SERVER_STOPPED`) und ein zusätzliches Textfeld in `LoginAccepted`: rot waren `TelemetryEventTest` („Jede Art der Aufzaehlung gehoert zu genau einer Klasse“) und `TelemetryRedactionTest`; `integratorTest` blieb dabei grün, die Unit-Tests liefen. Davor, in einem früheren Lauf mit Mutation an `ServerStopped`, brach `integratorTest` vor `test` ab, dort lief der Unit-Test nicht. (2) `address` des Bindefehlers und `errorCode` fest verdrahtet: `TelemetryFelderEmbeddingTest` beide Tests rot. (3) `PasswordChars` des Schlüsselmaterials im Lader leer: `ServerSmokeTest` („Ein Prozess ohne JVM-Option startet mit TLS“) und `Tds8ProcessTest` („Serverprozess nimmt TLS ab dem ersten Byte an“) rot; `ConfigUsersFileTest` blieb grün (neun Tests bestanden), er sieht den Inhalt von `PasswordChars` nicht.
