# Verifikation: slice-qa-wartbarkeit (Kompatibilitätsmatrix, Neuaufnahme 1b2c060) — 2026-10-31

**Rolle:** Verifier (Modul 11), frischer Kontext. Frage: Bauen wir es richtig? Geprüft wird gegen die DoD des Slice [slice-qa-wartbarkeit](../plan/planning/done/slice-qa-wartbarkeit.md), die Matrix [kompatibilitaetsmatrix](../plan/kompatibilitaetsmatrix.md), [TDS-QA-05](../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [ADR-0005](../plan/adr/0005-rpc-und-prepared-statements.md), [ADR-0010](../plan/adr/0010-detekt-pre-release.md), `AGENTS.md` §3.6 bis §3.8. Nicht Gegenstand: Richtigkeit der Anforderung (Validator), Diff gegen Plan (Reviewer). Vorliegend: Review [2026-10-30-71122af](2026-10-30-71122af-slice-qa-wartbarkeit.md), Fix-Runde c181255, Neuaufnahme 1b2c060, Merge fa6c4a4.

**Stand:** `main` bei fa6c4a4. `src/main` ist seit dem Beleg-Stand 9ff2913 unverändert; geändert ist `src/test/.../runtime/LizenzTest.kt` (ein Kommentar-Tag, Package `runtime` liegt nicht im Gegenstand der Matrix).

## Ergebnis in einem Satz

Matrix, Zitat-Wächter und Importabgleich halten der Prüfung stand (alle Stichproben stimmen mit Quelltext und Tests überein, alle Sensoren grün, der Wächter wird durch Mutation rot); es bleiben **kein HIGH-**, **zwei MEDIUM-** (veraltete „kein Träger“-Angaben der Lücken 3, 4, 14; Neuaufnahme ohne Review-Report) und vier LOW-Befunde.

## Sensor-Belege (selbst gefahren)

| Sensor | Ausgang |
|---|---|
| `make matrix-zitate` (baut `--no-cache-filter test`) | Exit 0: „738 bestandene Testfaelle im Bericht, 197 verschiedene Zitate, 0 ohne PASSED“ (2 min 10 s) |
| `docker build --no-cache-filter test --target test --progress=plain .` | Exit 0: 738 Zeilen `PASSED` in 98 Klassen, 0 `FAILED`, 0 `SKIPPED` — deckt sich mit „738 Testfälle in 98 Klassen“ der Matrix |
| `make gates` | Exit 0 (26 s, Layer aus dem Cache der beiden Läufe oben; Gate-Kette unverändert, `matrix-zitate` steht nicht in `GATE_CHECKS`) |
| `docker build --no-cache-filter detekt-selbsttest --target detekt-selbsttest .` | Exit 0: „9 Importregeln, 58 verbotene Importe gemeldet, 0 Fehlmeldungen“; „12 Aufruf-Faelle gemeldet“; „10 Formen von @Suppress gemeldet“ — alle drei Zahlen wie in Abschnitt 6a |
| Mutation 1: Testfall in der Matrix-Kopie umbenannt | `make matrix-zitate` (gegen den frischen Bericht) rot: „1 ohne PASSED“, die Liste nennt den Namen, Make-Exit 2 (Exit 1 des Skripts) |
| Mutation 2: Klassenname umbenannt (`RpcBinderXTest`) | rot, gleiche Form |
| Mutation 3/4: Zitat ohne Treffer / Matrix ohne Zitat | Exit 1 bzw. Exit 2 mit Meldung; kein stilles Grün |
| `make docs-check` | Exit 0: 488 Dateien, 0 Befunde (mit diesem Bericht) |

Logs: `/tmp/claude-1000/-Development-tdswire/8562f454-84ba-456b-9970-945e69c035f4/scratchpad/verif/` (`mz.log`, `test.log`, `gates.log`, `selbst.log`, `mut.sh`).

## DoD Punkt für Punkt

| DoD-Punkt (Slice §2) | Befund |
|---|---|
| Kompatibilitätsmatrix der Nachrichten und Datentypen als Abdeckungs-Dokument unter `docs/plan/` | erfüllt: Datei liegt unter `docs/plan/`, 197 verschiedene Zitate, alle im frischen Bericht `PASSED`; jede Zeile „unterstützt“ trägt einen Testfall (Handprüfung der Tabellenzeilen: keine „unterstützt“-Zeile ohne Zitat) |
| `make gates` grün | erfüllt (eigener Lauf, Exit 0) |
| Review durchgeführt, Report unter `docs/reviews/` | **teilweise**: Report liegt für den Stand 71122af vor; die Neuaufnahme 1b2c060 (Matrix um 81 Zeilen geändert, neues Skript `tools/matrix-zitate.sh`, Make-Fragment) hat keinen Report, siehe M-2 |
| Doku-Update bei öffentlichem Vertrag | erfüllt: `harness/README.md` führt die Zeile `make matrix-zitate`, `make help` nennt das Ziel (Bindung siehe L-3) |
| Closure-Notiz, Register, Risiko-Ausgang, drei Paarungen | offen (Closure nicht Gegenstand dieser Verifikation); das Risiko in §6 trägt „offen bis zur Closure“ |

Der Liefer-Punkt-Umfang (≤ 3) ist eingehalten (ein Punkt). Das Ziel „Modulgrenzen maschinell geprüft“ trägt Abschnitt 6a mit dem bestehenden Gate `make detekt` und dem Selbsttest (siehe Sensoren).

## Stichproben Matrix gegen Quelltext (mindestens 3 je Abschnitt)

| Abschnitt | Aussage | Beleg im Quelltext | Ergebnis |
|---|---|---|---|
| 1 | SQL_BATCH wird ausgeführt: Backend bekommt Sitzungs-ID, neue Anfrage-ID, Text, keine Parameter | `RequestLoop.execute` baut `QueryRequest(ready.session.id, requestIds(), text, parameters, null)`; Batch ruft `execute(text, emptyList(), BATCH)` | stimmt |
| 1 | Parserfehler bei SQL_BATCH: `TDS_PROTOCOL_ERROR`, schließt, ohne Backend | `answerBatch`: `Failed -> errors.protocol(...)`, kein `execute`; `ErrorCode.TDS_PROTOCOL_ERROR` mit `CLOSE_AFTER_REPLY` | stimmt |
| 1 | Transaction Manager und ATTENTION in `READY`: Automat gibt weiter, Zustand bleibt `READY`; Schleife antwortet nicht und schließt | `HandshakeAutomaton.forward` (`starts` nur für SQL_BATCH/RPC, sonst Zustand unverändert); `RequestLoop.step`: bei Zustand ≠ `RUNNING` Rückgabe `false`, `run()` schließt den Kanal | stimmt |
| 1 | ATTENTION vor `READY` beendet; Matrix-Zellen | `HandshakeMatrix.kt` (`READY to {REQUEST_MESSAGE, ATTENTION_MESSAGE}`, Kopfkommentar) | stimmt |
| 2 | Fehlercodes 50001 bis 50008, 18456, 208, 245, Class und Folge | `ErrorCode.kt` (Konstanten `N_*`, `REQUEST=16`, `FATAL=20`, `LOGIN=14`, Folgen) | stimmt |
| 2 | ENVCHANGE nur Typ 1, 4, 7; Tokens außerhalb von `Token` nicht darstellbar | `Token.kt`: `EnvChange.Database`, `PacketSize`, `SqlCollation`; keine weiteren Token-Klassen für NBCROW, ORDER usw. | stimmt |
| 2a | Precision über 38: Modell nimmt an, Abbildung weist mit `TDS_UNSUPPORTED_TYPE` ab | KDoc `SqlType.Decimal` (`precision >= 1`, keine Obergrenze) und `ColumnMapping.kt` Zeile 22 | stimmt |
| 3a | unbekannte ProcID: `UnknownProcedure`, Dispatch `TDS_UNSUPPORTED_RPC` 50002 Class 16, Verbindung offen | `RpcBinder.bind` (`UnknownProcedure -> Rejected(UNKNOWN_PROCEDURE)`), `RequestLoop.answerRpc` (`errors.code(TDS_UNSUPPORTED_RPC)`), `ErrorCode`: `REQUEST`, `OPEN` | stimmt |
| 3a | `sp_prepexec`/`sp_execute` geparst, nicht ausgeführt | `RpcBinder`: `PrepExec, Execute -> Rejected(NOT_BUILT)` | stimmt |
| 3b | PLP: Parser `UNSUPPORTED_PLP` (Kategorie `TDS_UNSUPPORTED_TYPE`), ganze Nachricht, offen als `TDS_UNSUPPORTED_RPC` | `RpcTypeInfoReader` Zeile 91; `ProtocolError.kt` Zeile 30 (Kategorie); `RpcBinder.rejectedByParser` (`UNSUPPORTED_PLP -> Rejected(TYPE)` ohne Fehler) | stimmt |
| 3b | `xml`: `INVALID_TYPE` → `TDS_UNSUPPORTED_TYPE` 50003 Class 20, schließt | `ProtocolError.kt` Zeile 27; `rejectedByParser` übergibt den Parserfehler (ENCODING); `ErrorMapper.protocolCode` bildet die Kategorie auf `TDS_UNSUPPORTED_TYPE` (`FATAL`, `CLOSE_AFTER_REPLY`) | stimmt |
| 4 | TLS-Tests, Listener-Tests | Zitate stehen im frischen Bericht als `PASSED`; Namen und Aussagen passen (Stichprobe `ListenerShutdownTest`, `TlsNegativeTest`) | stimmt |

**Importzählung 6a nachgezählt** (`grep ^import` über `src/main`, Skript `imports.sh` im Scratchpad): `domain` 0 Projekt-Importe; `port` 2 `domain`; `config` 2 `domain`, 7 `config.auth`, Fremdimporte `javax.crypto`; `protocol` 17 `config.auth`, 18 `domain`, 2 `port`, 21 `transport.framing`, keine aus `simulation`/`runtime`; `transport` 4 `protocol.session` (`HandshakeChannel`, `Inbound`, 2× `ReplyStream`); `simulation` 9 `domain`, 2 `port`, 6 eigene; `runtime` ohne `simulation`. Außerhalb von `simulation` kein Import von `tdswire.simulation` in `src/main`. Test-Seite (6b): `protocol` importiert `config`, `domain`, `port`, `protocol`, `transport`, `testsupport` (kein `simulation`); `port` 14 `domain`; `simulation` nur `domain` und `port`. Alle Zahlen der Matrix stimmen.

## Lücken-Tabelle: Träger

| Lücke | Träger laut Matrix | Prüfung |
|---|---|---|
| 1 Referenzclient | `slice-qa-kompatibilitaet` | Datei existiert (`open/`), DoD Punkt 1 führt die Szenarien: erfüllt |
| 5 TVP | kein Träger; `slice-rpc-executesql-parameter` §1 schließt aus | Text dort vorhanden (§1 „Table-Valued-Parameter … nicht im Katalog“): erfüllt |
| 7 Typgrößen | `slice-qa-kompatibilitaet` DoD | DoD Punkt 2 nennt `FLTN`, `MONEYN`, `DATETIMN`, `GUID`, `DATETIMEOFFSETN` und „Lücke 7“: erfüllt |
| 8 Antworten eines echten Servers | `slice-qa-vergleichslauf-d` | Datei existiert, Ziel und DoD führen Tokenreihenfolge, DONE-Statusbits, RETURNVALUE-Zählung: erfüllt |
| 9 TDS 8.0, Pooling | `slice-qa-kompatibilitaet` | Ziel, DoD Punkt 2 und `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`: erfüllt |
| 10 Decimal-Grenze | geschlossen | `ColumnMappingTest > Decimal bis Precision 38 ist abbildbar, darueber TDS_UNSUPPORTED_TYPE` bestanden: erfüllt |
| 14 SSPI usw. | SSPI-Messung in `slice-qa-kompatibilitaet`; Kennwortwechsel, FeatureExt `0x02`: „kein Träger“ | **veraltet**, siehe M-1 |
| 3, 4 | „kein Träger“ | **veraltet**, siehe M-1 |
| 2, 6, 12, 13 | `slice-codec-datentypen-erweiterung`, `slice-attention-cancel-timeout`, `slice-rpc-prepared-handles` | Dateien existieren in `open/`; die erste nennt NBCROW (6 Treffer) und PLP (7 Treffer) |

## Herkunftsstufen

Keine Zeile trägt „gemessen“ ohne Quelle. „gemessen (Spike)“ stützt sich auf [ADR-0005](../plan/adr/0005-rpc-und-prepared-statements.md) (Stichprobe: Form UTF-16-Text mit ALL_HEADERS, ein DONE/DONEPROC genügt, Collation-ENVCHANGE nötig, `OptionFlags 0x0002`, Typwahl `int`/`bigint`/`bit`/`varchar`/`decimal` — alle in der Messmatrix); „gemessen (JDK-Gegenstelle)“ hat Tests mit `SSLEngine` im selben Prozess. Die Stufen der Bestandszeilen sind im Kopf der Matrix ausdrücklich als „nicht neu gemessen“ gekennzeichnet; der Referenzclient-Vorbehalt steht im Kopf. `tinyint`/`smallint` und `xml` führt die Matrix als „laut `SPEC-003`“ beobachtet, nicht als Messmatrix-Wert (so seit der Fix-Runde). Einzelne Typen (`uniqueidentifier`, `varbinary`, `datetime2`) sind eher zu vorsichtig (Handvektor statt „gemessen“), das ist zulässig.

## Wächter `make matrix-zitate`

Ehrlich, wie dokumentiert: er prüft, dass jedes Zitat im Bericht `PASSED` steht, nicht, dass die Aussage zum Testfall passt (Abschnitt „Pflege“ sagt das). Mutation (siehe Sensoren) macht ihn rot. Grenzen, die der Kopf des Skripts nennt: ein Zitat mit Zeilenumbruch im Code-Span wird nicht gelesen; eine Zeile, der man das Zitat nimmt, bleibt unbemerkt (hier per Handprüfung der aktuellen Zeilen ohne Befund). Kein Gate, keine Schwelle gesenkt (`AGENTS.md` §3.6): das Ziel steht nicht in `GATE_CHECKS`.

## Befunde

### HIGH

Keine.

### MEDIUM

**M-1 — „kein Träger“ für die Lücken 3, 4, 14 widerspricht der DoD von `slice-qa-kompatibilitaet`**
* quelle: `AGENTS.md` §3.8, Matrix Abschnitt 5 (eigene Definition: „Kein Träger heißt: keine Slice-Datei führt den Punkt in DoD oder Risiko“)
* pfad: `docs/plan/kompatibilitaetsmatrix.md` Abschnitt 5 Zeilen 3, 4, 14 und Abschnitt 3b Zeile `xml` („Kein Träger-Slice nennt `xml` in der DoD“)
* befund: [slice-qa-kompatibilitaet](../plan/planning/next/slice-qa-kompatibilitaet.md) DoD Punkt 3 (Commit e35e882, auf `main` im Merge d32aa45 gelandet; nicht in der Basis von 1b2c060, die Neuaufnahme entstand auf einem Zweig ohne diese Änderung) trägt `xml` als RPC-Parameter (Lücke 3), das Batch-Flag (Lücke 4) sowie Kennwortwechsel und FeatureExt `0x02` (Lücke 14), „am Referenzclient gemessen … oder je Punkt mit Begründung als nicht lieferbar benannt“. Die Matrix führt alle drei weiter als „kein Träger“. Die Angabe unterschätzt den Stand (Gegenrichtung zum Befund M-1 des Reviews), bleibt aber nach der eigenen Definition falsch; zwei Dokumente, die einander verweisen (die DoD nennt „Lücken 3, 4 und 14 der Kompatibilitätsmatrix, die heute keinen Träger haben“), widersprechen sich nach dem Nachziehen des Slice.
* klasse: Träger-Angabe ohne Abgleich mit dem tragenden Slice-Text (Drift nach Neuaufnahme)

**M-2 — Die Neuaufnahme 1b2c060 hat keinen Review-Report**
* quelle: Slice §2 („Review durchgeführt, Report unter `docs/reviews/`“), `harness/README.md` §Minimal agent workflow
* pfad: `docs/reviews/2026-10-30-71122af-slice-qa-wartbarkeit.md` (Gegenstand: Diff `cff52a9..71122af`)
* befund: Der Review prüfte die erste Fassung der Matrix (280 Testfälle, Stand `bda6cd6`). Die heutige Datei ist in 1b2c060 um 81 Zeilen umgeschrieben (neue Packages, korrigierte Aussagen) und bringt neuen Code (`tools/matrix-zitate.sh`, `tools/mk/matrix-zitate.mk`) und SPEC-052. Ein Review-Report dazu liegt nicht vor (letzte Reviews in `docs/reviews/` nennen ihn nicht). Die DoD-Zeile „Review durchgeführt“ ist damit nur für den alten Stand belegt. Diese Verifikation ersetzt den Review nicht (sie prüft Zusage gegen Beleg, nicht den Diff).
* klasse: DoD-Punkt belegt für überholten Stand

### LOW

**L-1** — Der Plan-Text des Slice (§3, „Fix-Runde“ und „Erster Lauf“) nennt weiter „280 Testfälle“ und „Der Stand `bda6cd6` bleibt“, die Matrix steht auf 9ff2913 mit 738 Testfällen. Der Slice ist in diesem Punkt nicht nachgezogen (`AGENTS.md` §3.7: ein Text beschreibt, was da ist). Die „Wegwerf-Prüfung im Lauf, kein Werkzeug-Target“ im selben Abschnitt ist durch `make matrix-zitate` überholt.

**L-2** — Die Stand-Zeile der Matrix sagt: `git diff --stat 9ff2913..HEAD -- src` „leer: sie passt“. Der Befehl ist heute nicht leer (`src/test/.../runtime/LizenzTest.kt`, ein Kommentar-Tag, Package außerhalb des Gegenstands). Die Aussage über den Auslöser (Abschnitt „Pflege“, „Datei unter den Packages des Gegenstands“) deckt das, die Stand-Zeile ist wörtlich nicht mehr wahr; ein Zusatz („bis auf Dateien außerhalb des Gegenstands“) oder `-- src/main` löst es.

**L-3** — Die Bindung des Ziels `make matrix-zitate` in `harness/README.md` und die Commit-Kennung führen [TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit) (Sicherheit); die Matrix gehört zu [TDS-QA-05](../../spec/lastenheft.md#tds-qa-05--wartbarkeit) (Wartbarkeit). Die Spur Anforderung → Beleg läuft für dieses Ziel in die falsche Anforderung.

**L-4** — Abschnitt 6b „Grenze der Aussage“ sagt, das Simulations-Backend sei in `src/main` nicht am Dispatch verdrahtet; das gilt bis `slice-sim-aenderung-integration` (läuft parallel) auf `main` liegt. Der Auslöser im Abschnitt „Pflege“ greift dann (`src/main`-Diff); die Aussage ist heute richtig, aber kurzlebig. Zur Kenntnis, kein Mangel.

### INFO

* Keine ADR berührt: [ADR-0005](../plan/adr/0005-rpc-und-prepared-statements.md) wird nicht überschrieben, die Abweichungen (PLP, `xml`) sind als Abweichung von [ADR-0005](../plan/adr/0005-rpc-und-prepared-statements.md) und `SPEC-003` ausgewiesen, nicht stillschweigend (`AGENTS.md` §3.5). Kein Gate gesenkt (§3.6). Aussagen tragen höchstens so weit wie ihr Beleg (§3.8) — die Stichproben fanden keine Aussage breiter als ihr Beleg.
* Keine Closure, kein Verschieben, kein Commit auf `main` (Arbeit auf Branch `worktree-agent-a78154ef47e0123aa`).
