# Verifikation 2: slice-qa-wartbarkeit (Kompatibilitätsmatrix, Stand 68598df, Fix-Runde 75533b0) — 2026-10-31

**Rolle:** Verifier (Modul 11), frischer Kontext, zweite Verifikation. Frage: Bauen wir es richtig? Geprüft wird gegen die DoD des Slice [slice-qa-wartbarkeit](../plan/planning/done/slice-qa-wartbarkeit.md), die Matrix [kompatibilitaetsmatrix](../plan/kompatibilitaetsmatrix.md), [TDS-QA-05](../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [ADR-0005](../plan/adr/0005-rpc-und-prepared-statements.md), [ADR-0019](../plan/adr/0019-kompatibilitaetstests-zuschnitt.md) (`Proposed`), [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md) (`Proposed`) und `AGENTS.md` §3.6 bis §3.8. Nicht Gegenstand: Richtigkeit der Anforderung (Validator), Diff gegen Plan (Reviewer).

**Vorliegend:** erste Verifikation [fa6c4a4](2026-10-31-fa6c4a4-verifikation-slice-qa-wartbarkeit.md) (M-1, M-2, L-1 bis L-4), Fix-Runde `754743d`, Review [754743d](2026-10-31-754743d-neuaufnahme-kompatibilitaetsmatrix.md) (F-1 bis F-9), Fix-Runde `75533b0`, Stand `main` bei `48ed775`.

## Ergebnis in einem Satz

Alle Befunde des Neuaufnahme-Reviews (F-1 bis F-6) und der ersten Verifikation (M-1, M-2, L-1 bis L-4) sind behoben oder als benannt-offen ausgewiesen; Matrix, Wächter und Modulgrenzen halten der Prüfung stand, alle Sensoren sind grün. Es bleiben **kein HIGH**, **kein MEDIUM** und fünf LOW-Befunde (Nachzug von Plan-Text, Träger-Tiefe von Lücke 15, ein ungeschlossener Wächter-Rand, nicht neu erzeugte Abdeckungsdatei, nicht durchgeführte Wiederholung des Rot-Sehens).

## Sensor-Belege (selbst gefahren, alle unter `timeout 540`)

| Sensor | Ausgang |
|---|---|
| `docker build --no-cache-filter test --target test --progress=plain .` (HEAD `48ed775`) | Exit 0, `BUILD SUCCESSFUL`: 779 Zeilen `PASSED`, 106 verschiedene Klassen, 0 `FAILED`, kein Testfall übersprungen (die eine Zeile `SKIPPED` im Log ist die Gradle-Aufgabe `checkKotlinGradlePluginConfigurationErrors`) |
| `make matrix-zitate` (baut `--no-cache-filter test` selbst) | Exit 0: „779 bestandene Testfaelle im Bericht, 212 verschiedene Zitate, 0 ohne PASSED, 0 nicht gelesene Zitat-Formen“ |
| `tools/matrix-zitate.sh` gegen den eigenen Bericht | gleiches Ergebnis (Kreuzprobe) |
| `make gates` | Exit 0 (43 s, Teststufe aus dem Cache der beiden Läufe oben); `matrix-zitate` steht nicht in `GATE_CHECKS` (`grep` über `Makefile`, `tools/mk`, `harness/mk`: nur der Kommentar „KEIN GATE“) |
| `docker build --no-cache-filter detekt-selbsttest --target detekt-selbsttest .` | Exit 0: „9 Importregeln, 65 verbotene Importe gemeldet, 0 Fehlmeldungen“, „12 Aufruf-Faelle gemeldet“, „10 Formen von @Suppress gemeldet“ — 9/65/12/10 wie in Abschnitt 6a |
| `make spec-abdeckung` (Ziel in den Scratchpad umgeleitet) | Exit 0; Zeile `SPEC-052` identisch mit `docs/plan/spec-abdeckung.md`; zwei Zeilen weichen ab, siehe L-4 |
| `make docs-check` | Exit 0 (mit diesem Bericht), siehe Abschnitt „Abschluss“ |

Mutationen am Wächter `tools/matrix-zitate.sh` (Kopien der Matrix gegen den frischen Bericht, Skript `…/scratchpad/verif2/mut.sh`):

| Mutation | Ergebnis |
|---|---|
| M0 unverändert | Exit 0 |
| M1 ein Zitat ohne Backticks | Exit 1: „1 nicht gelesene Zitat-Formen“, die Zeile 30 steht in der Liste, 211 statt 212 Zitate |
| M2 Zitat über einen Zeilenumbruch im Code-Span | Exit 1, gleiche Form |
| M3 Testfall umbenannt | Exit 1: „1 ohne PASSED“, Name in der Liste |
| M4 ein Zitat in einer Zeile mit mehreren Zitaten ohne Backticks | Exit 1, Zeile 34 genannt |
| M5 `PacketHeaderTest >` ohne Testfall hinter einem gültigen Zitat | Exit 1, Zeile 125 genannt |
| M6 Fremdform in Prosa ohne jedes Zitat der Zeile | Exit 1, Zeile 3 genannt |

F-4 des Reviews (Fremdform entfällt still, Exit 0) ist damit behoben: jede Fremdform ergibt Exit 1 mit Zeilennummer. Grenze, die bleibt (INFO): erkannt wird nur `<Name>Test >`; ein Klassenname ohne Suffix `Test` in Fremdform bliebe unbemerkt.

## Stand-Zeile „68598df, 775 Testfälle in 106 Klassen“

* **Testfälle:** `@Test` in `src/test` am Stand `68598df` (`git grep`): 766; dazu die vererbten 9 Fälle von `QueryBackendContract` (laufen in `ScriptedBackendContractTest` und `SimulationBackendContractTest`, geschrieben einmal): 766 + 9 = 775. Das deckt die Aussage der Matrix. Am Kopf `48ed775` sind es 770 + 9 = 779, gleich dem frischen Lauf; die vier Fälle mehr stammen aus `1f1c8dd` (Mini-Runde `slice-sim-aenderung-integration`) und liegen nicht im Gegenstand der Matrix. Ein Lauf am Stand `68598df` selbst wurde nicht gefahren (kein Worktree dieses Standes); die 775 sind abgeleitet, nicht gemessen.
* **Klassen:** 106 verschiedene Klassennamen vor `>` in den `PASSED`-Zeilen. Abweichung zu den 105 Dateien `*Test.kt` (am Stand `68598df` wie am Kopf je 105): Die Datei `Tds8WiringTest.kt` enthält zusätzlich die Klassen `Tds8DefenseTest` und `Tds8ProcessTest` (+2), und `config/TempFilesTest.kt` ist eine offene Basisklasse ohne eigenen Testfall (−1): 105 + 2 − 1 = 106. Die „99 Test-Dateien“ der Anfrage und der Review-Zeile gelten für den Stand `754743d`, nicht für `68598df`.
* **Slice-Plan §3 (738 Testfälle in 98 Klassen):** gehört zum Stand `9ff2913` und ist als „Erster Lauf“ historisch richtig, widerspricht der Matrix also nicht als Zahl. Die Zeile „Die Matrix ist auf den Stand `9ff2913` neu aufgenommen“ (Abschnitt „Fix-Runde nach dem Review“) ist dagegen seit `75533b0` nicht mehr wahr, siehe L-1.
* **`git diff --stat 68598df..HEAD -- src/main`:** nicht leer, genau eine Datei: `src/main/.../config/ConfigFiles.kt` (3 Einfügungen, 2 Löschungen), aus `1f1c8dd`. Der Diff ändert nur das KDoc von `readAtMost` (FIFO „nicht getestet“ statt „ist `UNREADABLE`“). Keine Import-, Signatur- oder Verhaltensänderung: Die Zahlen und Aussagen der Matrix (Abschnitt 6a, Zeile `config`: 2 Importe `domain`, 7 `config.auth`, Fremdimporte) bleiben wahr. Das Package `config` gehört nach dem Kopf der Matrix nicht zum Gegenstand der Nachrichten und Typen (nur Abschnitt 6), der Auslöser des Abschnitts „Pflege“ greift nicht; die Stand-Zeile sagt für diesen Fall „sonst gilt der Auslöser“ und ist damit richtig. Die Matrix wird nicht falsch.

## Behebung der Befunde: Punkt für Punkt

| Befund | Prüfung | Ergebnis |
|---|---|---|
| Review F-1 (3b gegen `RpcParameters.sqlType`) | Der Kopf von 3b trennt Parser und Dispatch. Parser: `RpcParameterReader.readValue` deutet `INTN` (`IntegerValue`), `BITN` (`BitValue`) und `NVARCHAR`/`NCHAR` (`TextValue`), alles andere ist `RawValue`. Dispatch: `RpcParameters.sqlType` bildet nur `INTN` 4/8 (`integerType`, sonst `RpcRejection.TYPE`), `BITN`, `NVARCHAR`/`NCHAR`, `DECIMALN`/`NUMERICN`, `DATEN`, `TIMEN`, `DATETIME2N` ab, `else -> RpcRejection.TYPE`. Das stimmt mit dem Kopf der Matrix überein. Jede Zeile der abgelehnten Typen (`tinyint`/`smallint`, `float`/`real`, `money`, `datetime`, `uniqueidentifier`, `datetimeoffset`, `char`/`varchar`, `binary`/`varbinary`) trägt „Dispatch: nicht unterstützt“, ERROR 50002 Class 16, kein Backend-Aufruf, Verbindung offen. Belegt: die zitierten Fälle `26 01 01 07`, `26 02 02 07 00`, `6D 08 00`, `6D 04 04 …`, `6E 08 00`, `6F 08 00`, `24 10 00`, `2B 07 00`, `AF …`, `A7 …`, `AD …`, `A5 …` stehen wörtlich in `RpcBinderTest > Typen ausserhalb des Mindestkatalogs sind abgelehnt`; `RpcDispatchTest > Eine Ablehnung beantwortet mit ERROR und DONE …` fährt `6D 08 00` (`FLTN`) bis zur Antwort `50002`, kein Backend-Aufruf. Für `smallmoney`, `smalldatetime`, `tinyint`/`smallint` und `real` benennt die Matrix selbst, dass kein eigener Dispatch-Test besteht („dieselbe Verzweigung“, Fall „Parametertyp“ ist `FLTN`). `xml`/Katalogfremde: 50003, Class 20, schließt (`RpcDispatchTest > Eine unbekannte Typkennung schliesst mit dem Fehler des Parsers`, Kennung `FE`; ausdrücklich „nicht `xml`“). | **behoben** |
| Review F-1, neue Lücke 15 | Die Zeile nennt die 14 abgelehnten Typen, die Abweichung von `SPEC-003` (dort stehen `tinyint`/`smallint`, `float`/`real`, `money`, `datetime`, `GUID`, `char`/`varchar`, `binary` unter den beobachteten RPC-Parametertypen; `SPEC-003` ist so im Text vorhanden), ERROR 50002 und „`SqlType` führt diese Typen nicht“ (stimmt, Abschnitt 2a). Träger `slice-codec-datentypen-erweiterung`; Tiefe siehe L-2. | **behoben**, Träger-Tiefe LOW |
| Review F-2 (`SPEC-052`) | `spec/spezifikation.md` `SPEC-052`: „Der ausführbare Server läuft in einem Laufzeit-Image ([TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit))“, „Das Lastenheft verlangt davon nur den ausführbaren Server in Docker; die Wahl des Images ist Setzung dieser Festlegung“ (Distroless `nonroot`, Digest, Nutzer 65532, Port 1433 mit Verweis auf `SPEC-005`), Konfiguration „nie im Image (Setzung)“. [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) im Lastenheft: „… ausführbarer Server in einer dokumentierten Docker-basierten Umgebung reproduzieren“ — der Anker trägt. `SPEC-005` nennt `LISTEN_PORT` 1433. | **behoben** |
| Review F-3 (Abdeckungszeile) | Titel von `SPEC-052` nennt „der Tag `@spec` nennt nur den Lizenztext“; der Erzeuger (`make spec-abdeckung`, frisch) schreibt dieselbe Zeile wie die Datei im Repo. | **behoben** |
| Review F-4 (Wächter) | siehe Mutationen M1 bis M6: Exit 1 mit Zeilen; Kopf des Skripts und Abschnitt „Pflege“ der Matrix beschreiben die Fremdform. | **behoben** |
| Review F-5 (fehlende Zitate) | Die Zeile „TCP-Listener“ zitiert `ListenerStartTest > invalid port ends the start and leaves no thread`, die TLS-Negativzeile `TlsNegativeTest > peer without a common version is rejected`; beide stehen im frischen Bericht als `PASSED`. | **behoben** |
| Review F-6 (Auslöser eingetreten) | Abschnitt 6a Zeile `runtime` und die Constraint-Zeile nennen `simulation` (14 Importe, `SimulationSetup`, `Bootstrap`, `YamlTreeReader`), 6b „Grenze der Aussage“ nennt `SimulationWiringTest` über den Socket. Nachgezählt: `runtime` 14 Importe `simulation` (1 `Bootstrap`, 5 `SimulationSetup`, 8 `YamlTreeReader`); kein Import von `tdswire.simulation` in `src/main` außerhalb von `simulation` und `runtime`. | **behoben** |
| Review F-7 (KDoc `RequestLoop`) | unverändert: Zeile 44 sagt für einen abgewiesenen RPC `TDS_PROTOCOL_ERROR`, das Verhalten ist `TDS_UNSUPPORTED_TYPE` (50003) bzw. 50002 (Matrix richtig). Außerhalb der Matrix, Implementer-Gegenstand. | offen, INFO |
| Review F-8 (Zahl 65532 nicht gepinnt) | zur Kenntnis, kein Mangel | INFO |
| Verifikation 1: M-1 (Träger der Lücken 3, 4, 14) | Abschnitt 5 Zeilen 3, 4, 14 und die `xml`-Zeile von 3b nennen die DoD Punkt „Lücken 3, 4 und 14“ von [slice-qa-kompatibilitaet](../plan/planning/next/slice-qa-kompatibilitaet.md) (Punkt vorhanden); Windows-SSPI: DoD-Punkt „Anmeldemessungen …“ und [ADR-0019](../plan/adr/0019-kompatibilitaetstests-zuschnitt.md) als `Proposed`, „Einschätzung, nicht gemessen“ ([ADR-0019](../plan/adr/0019-kompatibilitaetstests-zuschnitt.md) nennt „nicht lieferbar“ für SSPI/NTLM). | **behoben** |
| Verifikation 1: M-2 (kein Review der Neuaufnahme) | Report [754743d](2026-10-31-754743d-neuaufnahme-kompatibilitaetsmatrix.md) liegt vor. Die Fix-Runde `75533b0` hat keinen eigenen Review; sie wird mit dieser Verifikation geprüft (kein Befund). | **behoben** |
| Verifikation 1: L-1 bis L-4 | L-1 (Plan-Text): 738 Testfälle und `matrix-zitate` im Plan, „Wegwerf-Prüfung“ entfernt, aber Rest siehe L-1 unten. L-2 (Stand-Zeile): `-- src/main`, Auslöser im Text. L-3 (Bindung): Zeile in `harness/README.md`, Skript und Fragment führen [TDS-QA-05](../../spec/lastenheft.md#tds-qa-05--wartbarkeit). L-4 (Simulation): 6b nennt die Verdrahtung. | **behoben** (L-1 mit Rest) |

## DoD Punkt für Punkt (Slice §2)

| DoD-Punkt | Befund |
|---|---|
| Kompatibilitätsmatrix der Nachrichten und Datentypen als Abdeckungs-Dokument unter `docs/plan/` | erfüllt: Datei unter `docs/plan/`; 212 verschiedene Zitate, alle im frischen Bericht `PASSED`; Handprüfung der Spalte „Status“: jede Zeile „unterstützt“ trägt einen Testfall; Zeilen „nicht unterstützt“ ohne Test tragen „—“ oder „nicht einzeln getestet“ (Batch-Flag, TVP, `NBCROW`, weitere Tokens, `text`/`ntext`/`image`) — ehrlich ausgewiesen |
| `make gates` grün | erfüllt (eigener Lauf, Exit 0) |
| Review durchgeführt, Report unter `docs/reviews/` | erfüllt für den Stand bis `754743d`; `75533b0` ist durch diese Verifikation geprüft, ein Review-Report für `75533b0` allein liegt nicht vor (kein HIGH/MEDIUM-Befund hier; Rollen-Sequenz nicht ausgelöst) |
| Doku-Update bei öffentlichem Vertrag | erfüllt: `harness/README.md` Zeile `make matrix-zitate`, Bindung [TDS-QA-05](../../spec/lastenheft.md#tds-qa-05--wartbarkeit); `SPEC-052` nachgezogen |
| Closure-Notiz, Register, Risiko-Ausgang, drei Paarungen | offen (Closure nicht Gegenstand); §6 trägt „offen bis zur Closure“ |

Liefer-Punkte ≤ 3 (einer). „Modulgrenzen maschinell geprüft“: durch das bestehende Gate `make detekt` und den Selbsttest 9/65/12/10 getragen; der Abgleich Tabelle gegen Konfiguration ist von Hand und als solcher ausgewiesen.

## Stichproben Matrix gegen Quelltext und Tests (mindestens 3 je Abschnitt, hier mehr)

| Abschnitt | Aussage | Beleg | Ergebnis |
|---|---|---|---|
| 1 | PRELOGIN-Optionen mit festen Längen (VERSION 6, ENCRYPTION 1, THREADID 4, MARS 1, TRACEID 36, FEDAUTHREQUIRED 1, NONCEOPT 32), INSTOPT beliebig | `PreloginCodec.kt` Zeilen 16 bis 32 | stimmt |
| 1 | Pakettyp `0x0E` und ATTENTION: Automat gibt in `READY` weiter, Schleife schließt ohne Antwort | in Verifikation 1 gegen `HandshakeAutomaton`/`RequestLoop.step` belegt, `src/main` seit dort nur im KDoc von `ConfigFiles` geändert | stimmt |
| 1 | SQL_BATCH: Parserfehler → `TDS_PROTOCOL_ERROR`, schließt, ohne Backend | `RequestLoop` KDoc Zeile 54 und `ErrorCode.TDS_PROTOCOL_ERROR` (`CLOSE_AFTER_REPLY`) | stimmt |
| 2 | Fehlercodes: 50001 bis 50008, 18456, 208, 245; Class 16/20/14; Folge offen/schließt | `ErrorCode.kt` Zeilen 22 bis 99: `TDS_UNSUPPORTED_SQL` 50001 (16, offen), `…_RPC` 50002 (16, offen), `…_TYPE` 50003 (20, schließt), `TDS_BACKEND_ERROR` 50004 (16, offen), `TDS_TIMEOUT` 50005 (20), `TDS_PROTOCOL_ERROR` 50006 (20), `TDS_INTERNAL_ERROR` 50007 (20), `TDS_SHUTDOWN` 50008 (20), `TDS_LOGIN_FAILED` 18456 (14), 208 (20, schließt), 245 (16, offen) | stimmt |
| 2 | ENVCHANGE nur Typ 1, 4, 7; DONE-Statusbits FINAL, MORE, ERROR, IN_TRANSACTION, COUNT, ATTENTION, RPC_IN_BATCH, SERVER_ERROR | `Token.kt` (`EnvChange.Database`/`PacketSize`/`SqlCollation`, `DoneStatus` mit 0x0000 bis 0x0100) | stimmt |
| 2a | Decimal: Modell ohne Obergrenze, Abbildung weist Precision über 38 ab | `SqlType`-KDoc, `SPEC-002`-Text (spezifikation Zeile 299) und `ColumnMappingTest` | stimmt |
| 3a | `sp_prepexec`/`sp_execute` geparst, Dispatch lehnt ab | `RpcBinder` (`NOT_BUILT`), Test `RpcBinderTest > Unbekannte ProcIDs und nicht gebaute Katalogformen sind abgelehnt` | stimmt |
| 3b | Parser deutet nur INTN/BITN/Unicode, sonst `RawValue` | `RpcParameterReader.kt` Zeilen 59 bis 99 | stimmt |
| 3b | Dispatch-Abbildung und `RpcRejection.TYPE` | `RpcParameters.kt` Zeilen 100 bis 126, KDoc Zeilen 52 bis 57 (nennt dieselbe Liste der abgelehnten Typen) | stimmt |
| 3b | `char`/`varchar`: Parser `RawValue` mit `FF FF` als NULL | `RpcParameterReader.readBytes` (`countedLength`, `NULL_COUNTED`) | stimmt |
| 4 | Header 8 Byte; Vor-Login-Grenze 65536; Packet Size 9 bis 65535 | `FramingLimits.MAX_PRELOGIN_MESSAGE_BYTES = 65536`; `PacketWriter` KDoc „9..65535“ | stimmt |
| 4 | Pfadwahl je Verbindung: erstes Byte `0x16` wählt den Pfad ab dem ersten Byte; nur mit TLS-Material und `REQUIRED`; Klartext nach `0x16` ohne Rückfall; Frist `LOGIN_TIMEOUT` | `ConnectionWiring.kt` Zeilen 49 bis 76, 109 bis 112, 144 (`TLS_HANDSHAKE_RECORD = 0x16`); Tests `Tds8WiringTest`, `Tds8DefenseTest`, `PeekedChannelTest` im Bericht `PASSED` | stimmt |
| 4 | Listener: Start prüft Port und TLS vor dem Binden, geordnetes Beenden mit Frist | fünf Zitate aus `ListenerStartTest`, `ListenerLifecycleTest`, `ListenerShutdownTest` im Bericht `PASSED`, Namen passen zur Aussage | stimmt |
| 4 | TLS-Negativfälle: „0 geschriebene Bytes“ nur für die genannten Fälle, beim abgeschnittenen Handshake nur der Ergebniswert | Zeilentext der Matrix grenzt es ab; Namen der Testfälle passen | stimmt |
| 5 | Lücke 7 Träger: DoD-Punkt „Anmeldemessungen und RPC-Messungen“ mit `FLTN`, `MONEYN`, `DATETIMN`, `GUID`, `DATETIMEOFFSETN` und „Lücke 7“ | `slice-qa-kompatibilitaet` DoD Zeile 48 | stimmt |
| 5 | Lücke 15 Träger | `slice-codec-datentypen-erweiterung` §6 Zeile 113 nennt `smallint`, `char`, `float`, GUID, binär, Zonenzeit | stimmt, Tiefe siehe L-2 |
| 6a | Importzählung je Package | frisch nachgezählt (`grep ^import`): `port` 2 `domain`; `config` 2 `domain`, 7 `config.auth`, Fremdimporte `java.io`, `java.nio`, `java.util`, `javax.crypto`; `protocol` 17 `config.auth`, 18 `domain`, 2 `port` (nur `RequestLoop`, `ResponseSequencer`), 21 `transport.framing`; `transport` 4 `protocol.session`; `simulation` 11 `domain`, 3 `port`, 6 eigene; `runtime` 14 `simulation`; `domain` kein Projektimport | alle Zahlen stimmen |
| 6a | „Sechs der acht Package-Zeilen und beide Constraints decken sich; zwei weichen ab (`protocol`, `transport`)“ | Die Tabelle hat acht Package-Zeilen; `protocol` und `transport` stehen als „teilweise“, die übrigen als „deckt“ | stimmt |
| 6b | Testimporte | `src/test/.../protocol`: `config` 33, `domain`, `port`, `protocol`, `testsupport`, `transport`, kein `simulation`; `port` 14 `domain`; `simulation` nur `domain` 22 und `port` 5 | stimmt |
| 6b | Vertragssuite mit 9 Fällen, gefahren gegen Skript-Backend und Simulations-Backend | `QueryBackendContract.kt` 9 `@Test`; beide Klassen erben | stimmt |
| 6b | Protokoll und Simulations-Backend über einen Socket zusammen | `SimulationWiringTest` (Zeile 101: „über den Socket“); `Bootstrap.kt` Zeile 126 ruft `SimulationSetup.build` | stimmt |

## ADR- und Regel-Konformität

* [ADR-0005](../plan/adr/0005-rpc-und-prepared-statements.md): wird nicht überschrieben; PLP und `xml` stehen als Abweichung von [ADR-0005](../plan/adr/0005-rpc-und-prepared-statements.md) und `SPEC-003` ausgewiesen, nicht stillschweigend (`AGENTS.md` §3.5). Die neuen Dispatch-Ablehnungen (Lücke 15) sind als Abweichung von `SPEC-003` benannt.
* [ADR-0019](../plan/adr/0019-kompatibilitaetstests-zuschnitt.md) (`Proposed`): wird nur als Vorschlag geführt („als nicht lieferbar vorgeschlagen, Einschätzung, nicht gemessen“), nicht als Entscheidung.
* [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md) (`Proposed`): `make matrix-zitate` bleibt Hilfsziel ohne Gate (nicht in `GATE_CHECKS`), die Matrix nennt weder die ADR noch ein Gate für Läufe gegen den Server; der Entwurf schärft `SPEC-052` und `SPEC-040`, die Matrix zitiert beide Festlegungen nicht und bleibt davon unberührt.
* `AGENTS.md` §3.6: kein Gate gelockert oder gesenkt, kein neues Gate eingeführt. §3.7: Kommentare im Skript tragen Zusage und Grenze, keine Chronik. §3.8: die Stichproben fanden keine Aussage breiter als ihr Beleg; die Matrix weist ihre Grenzen aus (Referenzclient nicht gemessen, Zitat prüft Namen, nicht Inhalt, Stand abgeleitet).

## Befunde

### HIGH

Keine.

### MEDIUM

Keine.

### LOW

**L-1 — Plan-Text des Slice nennt weiter „Stand 9ff2913“ für die Matrix**
* quelle: `AGENTS.md` §3.7 (ein Text beschreibt, was da ist), §3.8
* pfad: `docs/plan/planning/done/slice-qa-wartbarkeit.md` §3, Absatz „Fix-Runde nach dem Review“: „Die Matrix ist auf den Stand `9ff2913` neu aufgenommen“
* befund: Die Matrix steht seit `75533b0` auf `68598df` (775 Testfälle in 106 Klassen). Die 738/98 im „Ersten Lauf“ sind als Messung am Stand `9ff2913` richtig und widersprechen der Matrix nicht; der Satz zur Neuaufnahme ist überholt. Kein Einfluss auf die DoD.

**L-2 — Lücke 15: der Träger trägt den Typkatalog, nicht ausdrücklich den Dispatch-Pfad, und steht nicht in der DoD**
* quelle: `AGENTS.md` §3.8; Definition „Kein Träger“ im Abschnitt 5 der Matrix („keine Slice-Datei führt den Punkt in DoD oder Risiko“)
* pfad: `docs/plan/kompatibilitaetsmatrix.md` Abschnitt 5 Zeile 15; `docs/plan/planning/open/slice-codec-datentypen-erweiterung.md` §6 Zeile 113
* befund: Der Slice führt `smallint`, `char`, `float`, GUID, binär und Zonenzeit als **Risiko** (§6, Ausgang „weiter offen“, „Aufnahme in die Liefer-Punkte folgt erst, wenn der Referenzclient-Beleg vorliegt“), nicht in der DoD (§2 Punkt „Weitere Typen“ nennt nur `text`, `ntext`, `image`, `sql_variant`). Nach der eigenen Definition der Matrix ist der Träger damit vorhanden, aber schwach; die Matrix sagt nur „nennt … als Erweiterung des Typkatalogs“. Dass die Aufnahme in `SqlType` auch die Dispatch-Ablehnung der RPC-Parameter aufhebt, ist eine Folge, die der Slice nicht nennt. Ein Leser, der die Matrix liest, könnte „Träger = geliefert in der DoD“ annehmen.

**L-3 — Wächter: Fremdform nur mit Suffix `Test`**
* quelle: `AGENTS.md` §3.8
* pfad: `tools/matrix-zitate.sh` (Muster `[A-Za-z0-9]*Test >`)
* befund: Ein Zitat in Fremdform, dessen Klassenname nicht auf `Test` endet (z. B. eine Klasse `TempFiles` oder `Contract`), wird weder gelesen noch gemeldet. Alle Klassen mit Testfällen im Bericht enden auf `Test`; die Lücke ist theoretisch und im Kopf des Skripts nicht genannt (dort steht „Fremdform“ ohne Einschränkung auf den Suffix).

**L-4 — `docs/plan/spec-abdeckung.md` nicht neu erzeugt**
* quelle: `AGENTS.md` §5 Regel 6 (Abdeckungsmatrix weist nach)
* pfad: `docs/plan/spec-abdeckung.md` Zeilen `SPEC-035` (76 statt 79) und `SPEC-010` (22 statt 23)
* befund: Der frische Lauf von `make spec-abdeckung` am Kopf liefert zwei höhere Zahlen; die Datei wurde in `1f1c8dd` nicht nachgeführt. Die Zeile `SPEC-052` (Gegenstand von F-3) ist identisch. Die Abweichung hängt an den vier Tests der Mini-Runde `slice-sim-aenderung-integration`, nicht an diesem Slice; hier nur gemeldet.

**L-5 — Rot-Sehen der Importregeln nicht wiederholt**
* quelle: `AGENTS.md` §3.8
* pfad: `docs/plan/kompatibilitaetsmatrix.md` Abschnitt 6a „Belege“, dritter Punkt
* befund: Die Matrix nennt das Rot-Sehen als „einmalig am Stand `bda6cd6`, nicht wiederholt“ und begründet es mit der unveränderten Konfiguration der Regel `protocol`. Ich habe es ebenfalls nicht wiederholt (nur der grüne Selbsttest 9/65/12/10); die Aussage ist also ehrlich, aber auch hier ungeprüft. Die Zahl der verbotenen Importe ist von 58 (Verifikation 1) auf 65 gestiegen, ohne dass die Matrix eine Zahl für 58 nennt; die Matrix nennt 65 und der Lauf bestätigt es.

### INFO

* F-7 (KDoc von `RequestLoop` enger als das Verhalten) bleibt offen; Gegenstand des Implementers, nicht der Matrix.
* Der Teststand der Matrix (775) liegt vier Fälle unter dem Kopf (779); `make matrix-zitate` am Kopf: 212 Zitate, 0 ohne `PASSED`. Die Matrix gilt weiter, die Stand-Zeile erklärt den Abstand (Stand-Hash, Auslöser).
* Die 775 sind aus `@Test`-Zählung plus vererbten Fällen abgeleitet, nicht in einem Lauf am Stand `68598df` gemessen.
* Keine Closure, kein Verschieben, kein Commit auf `main`; Arbeit auf Branch `worktree-agent-a01d6b61e84f6c87a`.

## Logs

`/tmp/claude-1000/-Development-tdswire/8562f454-84ba-456b-9970-945e69c035f4/scratchpad/verif2/`: `test.log`, `mz.log`, `gates.log`, `selbst.log`, `sa.log`, `spec-abdeckung.md`, `mut.sh`, `imp.sh`.
