# Nachverifikation: slice-rpc-executesql-parameter (`@@x` und PLP) — 2026-10-31

**Rolle:** Verifier (Modul 11), frischer Kontext. Frage: Bauen wir es richtig? Geprüft wird, ob die Nutzerentscheidungen vom 2026-10-31 in Spec, Code und Tests übereinstimmend umgesetzt sind. Grundlage: der Vorbericht [V-1 bis V-5](2026-10-31-b339b77-verifikation-slice-rpc-executesql-parameter.md), der Slice [slice-rpc-executesql-parameter](../plan/planning/done/slice-rpc-executesql-parameter.md), [SPEC-011 und SPEC-013](../../spec/spezifikation.md#4-fehler-codes-und-logging-felder), [SPEC-030](../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten), [ADR-0005](../plan/adr/0005-rpc-und-prepared-statements.md), [ADR-0007](../plan/adr/0007-fehler-und-attention.md), [TDS-FA-08](../../spec/lastenheft.md#tds-fa-08--backend-anbindung). Nicht Gegenstand: Richtigkeit der Entscheidungen (Validator), Diff gegen Plan (Reviewer).

**Stand:** `main` bei fa4d6a9. Gegenstand: 76ca52f (Spec) und fc5d0be (Code, Tests), Merge fa4d6a9.

**Entscheidungen (Nutzer, 2026-10-31, nicht gemessen):** (1) `@@x` als Parametername nur beim RPC per Prozedurname (Name im Text) abgelehnt, bei `sp_executesql` zulässig. (2) Gültig kodierte, nicht unterstützte PLP-Typen: offene Ablehnung (`TDS_UNSUPPORTED_RPC`, Code 50002, Verbindung bleibt offen); Schließen nur bei Struktur- oder Kodierfehlern.

## Ergebnis in einem Satz

Beide Entscheidungen sind in Spec, Code und Tests übereinstimmend umgesetzt, der frische Testlauf ist grün, alle fünf gefahrenen Mutationen (darunter die bisher nicht gelaufene im `RpcTypeInfoReader`) wurden erkannt, und die Mehrpaket-Frage ist durch die Schichtung geklärt; es bleiben **keine HIGH- und keine MEDIUM-Befunde**, nur drei LOW-Lücken im Nachweis.

## Sensor-Belege

| Sensor | Ausgang | Beleg |
|---|---|---|
| `docker build --no-cache-filter test --target test .` (frisch, Worktree auf fa4d6a9) | Exit 0, `BUILD SUCCESSFUL`, Task `:test` im Lauf ausgeführt (kein Layer-Cache der Test-Stage) | Log im Scratchpad `/tmp/claude-1000/-Development-tdswire/8562f454-84ba-456b-9970-945e69c035f4/scratchpad/base.log` |
| Mutationen M1 bis M5 (Skript `/tmp/claude-1000/-Development-tdswire/8562f454-84ba-456b-9970-945e69c035f4/scratchpad/mut.sh`, je frischer Testlauf, danach `git checkout -- src`) | 5 von 5 erkannt (Exit 1, Tests rot) | Logs `m1.log` bis `m5.log` im selben Scratchpad |
| `make docs-check` | grün (nach Korrektur eines Ankers im Bericht) | — |

Nicht gefahren: voller `make gates`, `make detekt` (der Gegenstand sind Testwächter und Konsistenz; ein Docker-Build gleichzeitig). Der Implementer-Beleg für detekt liegt nicht vor; das ist offen, nicht behauptet (LOW, siehe N-3).

## Konsistenz Spec, Code, Tests

| Aussage | Spec | Code | Test | Befund |
|---|---|---|---|---|
| `@@x` nur beim RPC per Name abgelehnt | Absatz „RPC im Backend-Vertrag“ | `RpcParameters.checkName(name, nameInText)`, Aufrufe `nameInText = false` für `ExecuteSql`, `true` für `ByName` in `RpcBinder` | `RpcBinderTest` „Doppeltes At-Zeichen …“ (`@@x`, `@@rowcount`: ByName abgelehnt, ExecuteSql gebunden, Name unverändert, Text unverändert), `invalid`-Liste läuft zusätzlich über ByName | Erfüllt. `@@` in der Mitte (`@a#b$c@d`) bleibt in der `valid`-Liste gültig; der Code prüft nur `name[1]`. |
| PLP gültig kodiert → `TYPE` ohne Fehler, offen, 50002 | SPEC-013-Zeile, SPEC-011-Zeile, RPC-Absatz | `RpcTypeInfoReader.maxLength` → `UNSUPPORTED_PLP`; `RpcBinder.rejectedByParser` → `Rejected(TYPE)` ohne Fehler; `RequestLoop.answerRpc` → `TDS_UNSUPPORTED_RPC` mit `cause = TYPE` | `RpcFailureTest` (fünf PLP-Formen `UNSUPPORTED_PLP`, Kategorie unsupported), `RpcBinderTest` (vier Formen, Wert-, SQL-Text- und ByName-Position, `FE 00` bleibt `ENCODING`), `RpcDispatchTest` (ERROR 50002, DONE Fehlerstatus, Backend nicht gerufen, Folge-RPC beantwortet, 2 Antworten) | Erfüllt. |
| Struktur- und Kodierfehler schließen weiter | SPEC-011-Zeile | `rejectedByParser`: alles außer `UNSUPPORTED_PLP` bleibt `ENCODING` mit Parserfehler | `RpcFailureTest` (`F1 00`, `62`, `E5`, `00` → `INVALID_TYPE`), `RpcDispatchTest` „unbekannte Typkennung schliesst“ | Erfüllt. |
| Trennung SPEC-011 / SPEC-013 | beide Zeilen verweisen aufeinander | KDoc `RpcRejection`, `RpcBinding.Rejected`, `RpcTypeInfoReader` führen dieselbe Trennung | — | Erfüllt, kein Widerspruch der drei Spec-Stellen (Tabellenzeilen und Absatz) untereinander. |

**Spec-Regeln (AGENTS.md 3.4):** Der Diff 76ca52f ändert nur `spec/spezifikation.md`. Ein Suchlauf über die hinzugefügten Zeilen nach `ADR`, `src/`, `.kt`, `slice`, `welle` ergab keine Treffer; keine Pfade außerhalb der Spec, keine ADR-Wörter. Die Änderungsprotokoll-Zeile nennt Kennungen der Spec (`TDS-FA-04.a`, `SPEC-011`, `SPEC-013`), keine Meilensteine.

**AGENTS.md 3.7 / 3.8:** Die neuen Kommentare benennen den Ist-Zustand im Indikativ, jede nicht gemessene Aussage trägt „Festlegung ohne Messung“ oder „Nutzerentscheidung … nicht gemessen“. Eine Abwägung oder Chronik steht nicht in Kommentaren. Eine Ausnahme: das KDoc von `RpcParameters` (neuer Satz zu `@@x`) steht in einer Zeile über 120 Zeichen; ob `make detekt` (ktlint) das beanstandet, ist nicht gefahren (N-3).

## Grenzfälle

**PLP in der Mitte einer Parameterliste; Rest wird nicht gelesen; Folge-RPC.** Der RPC-Parser liest die Nachricht aus einem vollständigen Byte-Array (`rpc.payload`) und gibt bei `UNSUPPORTED_PLP` sofort ein `Failed` zurück; der Rest der Parameterliste wird nie interpretiert. Es gibt keinen Lesezeiger, der in einer nächsten Nachricht weiterläuft: `RequestLoop.step` holt mit `channel.receive()` je Schritt eine neue, vollständige `TdsMessage`. Die Folgenachricht ist deshalb unabhängig vom Verbleib der Bytes der abgelehnten. Der Dispatch-Test belegt Folge-RPC nach PLP-Ablehnung (zwei Antworten, zweite entspricht `oneRowReply`), allerdings mit PLP als *erstem* Parameter (N-1).

**Paketgrenzen und Framing nach offener Ablehnung eines mehrteiligen Requests.** Das Zusammensetzen liegt eine Schicht tiefer: `MessageAssembler` (`transport/framing`) fügt alle Pakete einer Nachricht bis zum EOM-Paket zusammen und liefert genau eine `TdsMessage`; `TransportHandshakeChannel` reicht sie als `Inbound.Message` weiter. Der Dispatch sieht nie eine Teilnachricht, die offene Ablehnung kann also keine Restpakete übrig lassen: die Nachricht ist vor dem Parsen vollständig verbraucht (auch Pakete hinter dem PLP-Typ). Beleg im Code, nicht durch einen Test mit zwei Paketen am RPC-Pfad (N-1). Die Zerstückelung als solche deckt das Framing-Gerüst (`FramingFuzzTest`, `HandshakeTransportTest`), nicht aber die Kombination mit RPC und PLP.

**`@@`-Prüfung für OUTPUT-Parameter und Namen mit `@@` in der Mitte.** `checkName` läuft für jeden Parameter vor der Richtungs- und Typprüfung, unabhängig vom Statusbyte, also gilt die Regel für OUTPUT und INPUT_OUTPUT gleich (Codepfad; der Test `@@x` nutzt nur INPUT-Werte, N-2). `@@` in der Mitte (`@a@@b`) ist erlaubt, weil nur `name[1]` geprüft wird und `@` im Zeichensatz liegt; die `valid`-Liste führt `@a#b$c@d` (ein einzelnes `@` in der Mitte), nicht `@a@@b` (N-2). Die Spec sagt „`@@` am Anfang“; Code und Spec stimmen überein.

## Mutationen

| # | Mutation | Erkannt durch |
|---|---|---|
| M1 | `RpcTypeInfoReader`: PLP wieder `INVALID_TYPE` statt `UNSUPPORTED_PLP` (die bisher nicht gelaufene Mutation) | `RpcBinderTest` „Ein PLP-Typ ist nicht unterstuetzt…“, `RpcDispatchTest` „Ein PLP-Typ wird offen abgelehnt…“, `RpcFailureTest` (zwei Tests) |
| M2 | `RpcBinder.rejectedByParser`: `UNSUPPORTED_PLP` → `ENCODING` mit Fehler (Verbindung schließt) | `RpcBinderTest` PLP-Test, `RpcDispatchTest` PLP-Test |
| M3 | `checkName`: `@@` überall abgelehnt (wie vor der Entscheidung; `sp_executesql` mit `@@x` abgelehnt) | `RpcBinderTest` „Doppeltes At-Zeichen …“ |
| M4 | `checkName`: Bedingung vertauscht (`@@` nur bei `sp_executesql` abgelehnt) | dieselbe Testfunktion |
| M5 | `checkName`: `@@` nie abgelehnt | dieselbe Testfunktion |

Fünf von fünf erkannt. M3 bis M5 werden alle von **einer** Testfunktion getragen; sie prüft beide Pfade und beide Namen, die Wächterdichte ist für die Entscheidung ausreichend. Nach den Läufen ist `src` per `git checkout` zurückgesetzt; `git status` ist sauber.

## Befunde

| ID | Stufe | Befund | Empfehlung |
|---|---|---|---|
| N-1 | LOW | Kein Test über den Zusammenbau: PLP-Typ in der *Mitte* einer Parameterliste (nicht als erster Parameter) und eine mehrpaketige RPC-Nachricht, gefolgt von einem zweiten RPC, auf dem Dispatch-/Transportpfad. Die Aussage „Rest wird nicht gelesen, Folge-RPC korrekt beantwortet, Nachricht vollständig verbraucht“ ruht auf dem Code (Schichtung `MessageAssembler` → Kanal → `RequestLoop`), nicht auf einem Test. AGENTS.md 3.8: die Aussage trägt so weit wie ihr Beleg; hier trägt sie über den Code, nicht über eine Messung. | Optional ein Dispatch-Test mit PLP als zweitem Parameter und ein Framing-Test, der einen zweipaketigen RPC mit PLP-Typ plus Folgenachricht durch Assembler und Schleife schickt. Kein Blocker. |
| N-2 | LOW | Die `@@`-Regel ist für OUTPUT/INPUT_OUTPUT und `@@` in der Mitte (`@a@@b`) nicht eigens getestet; der Code behandelt sie durch denselben Pfad richtig. | Optional `@a@@b` in die `valid`-Liste und ein OUTPUT-Parameter `@@x` beim RPC per Name in den `@@`-Test. |
| N-3 | LOW | `make detekt` und voller `make gates` sind in dieser Nachverifikation nicht gefahren und kein Implementer-Beleg liegt vor. Der Satz im KDoc von `RpcParameters` zu `@@x` ist lang (über 120 Zeichen in einer Zeile); ob ktlint ihn beanstandet, ist ungeprüft. | Implementer oder Reviewer belegt `make gates` vor der Closure. |

Die Punkte V-2 bis V-5 des Vorberichts (DoD-Wortlaut zur offenen Verbindung bei Parserfehlern, V-3 Grenzwerte nur im Binder, V-4, V-5 Closure-Notiz) sind nicht Gegenstand dieser Nachverifikation und bleiben unverändert; V-1 (PLP-Frage) ist mit der Entscheidung (2) beantwortet und umgesetzt. Der DoD-Wortlaut des Slice nennt weiterhin „Verbindung bleibt offen“ für nicht unterstützte Formen; mit Entscheidung (2) deckt er nun auch PLP, die Abweichung des Vorberichts zu Parserfehlern (Schließen) besteht fort.

## Abschluss

Keine Closure, kein Eingriff in den Slice-Plan, keine Reparatur. Der Bericht liegt im Worktree-Branch `worktree-agent-a6e258a3273242604`, nicht auf `main`.
