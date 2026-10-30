# Verifikationsbericht: slice-qa-stabilitaet (Umsetzung cb4c853, Fix-Runde a04ab01) am Stand dcb6b27 — 2026-10-30

**Rolle:** Verifier (Frage: „Bauen wir es richtig?“ gegen Plan, DoD, Spezifikation und Entscheidungen). Der Bericht repariert nichts und ändert keine Datei außer sich selbst.

**Gegenstand:** [slice-qa-stabilitaet](../plan/planning/done/slice-qa-stabilitaet.md) (Welle welle-qualitaet-grundlage), Bezug [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität) und [ADR-0003](../plan/adr/0003-transportframework.md); berührte Spezifikation: [SPEC-006](../../spec/spezifikation.md#3-defaults-und-konstanten), [SPEC-007](../../spec/spezifikation.md#3-defaults-und-konstanten), [SPEC-027](../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge), [SPEC-034](../../spec/spezifikation.md#3-defaults-und-konstanten), [SPEC-046](../../spec/spezifikation.md#3-defaults-und-konstanten), [SPEC-048](../../spec/spezifikation.md#4-fehler-codes-und-logging-felder). Geprüft ist das Review `2026-10-30-cb4c853-slice-qa-stabilitaet.md` (F-1 bis F-12) gegen die Fix-Runde.

**Stand-Hinweis:** `main` steht auf `7c374a6`. `git diff dcb6b27 HEAD` betrifft nur zwei Dateien unter `docs/reviews/`; `src/` ist seit `dcb6b27` unverändert (Befehl ausgeführt). Alle Läufe betreffen deshalb denselben Code. Der Stand enthält auch die Merges von `slice-sql-batch-dispatch` und `slice-bootstrap-konfiguration`; die Stabilitätstests laufen unverändert gegen sie.

**Behauptung ≠ Bestätigung:** Der Slice trägt zwei Häkchen für Liefer-Punkte und eines für `make gates`, aber keine Closure-Notiz (§7: „Wird bei der Closure gefüllt“). Die Belege des Implementers (Mutationen M1 bis M14, L120, D1, R1, R2 im gemeinsamen Scratchpad) habe ich nicht übernommen. Ich habe 12 eigene Mutationen einzeln gefahren (Abschnitt 2) und die Tests selbst laufen lassen. Mutationsnamen ohne Nachweis aus meinem Lauf sind unten als nicht belegt gekennzeichnet.

---

## 1. Selbst gefahrene Läufe

| Lauf | Ergebnis |
|---|---|
| Stabilitätstests allein (Stage `test` mit Filter `io.github.pt9912.tdswire.stability.*`, Kopie von `src/` im Scratchpad, ein Build zur Zeit), Grundlinie | grün, 11 von 11 Tests `PASSED`, `BUILD SUCCESSFUL in 40s` |
| dasselbe, drei weitere Läufe nacheinander (`--no-cache-filter test`) | je 11 von 11 `PASSED` (37 s, 36 s, 40 s); Last des Hosts 5 bis 12 |
| Volllauf `docker build --target test --no-cache-filter test` im Repo | grün, `BUILD SUCCESSFUL in 52s`, 500 Tests `PASSED`, 0 `FAILED`; der Verbindungssturm lief darin ein fünftes Mal grün |
| `make gates` | Exit 0 in 7 s; **Hinweis:** die Stage `test` kam aus dem Cache (`CACHED`), das Ergebnis ist das eines früheren Laufs derselben Quellen und für sich kein frischer Beleg; den frischen Beleg liefert der Volllauf darüber. d-check: 329 Dateien, 0 Befunde; `baseline-verify` v6.18.1 ok |
| `make docs-check` (mit diesem Bericht) | 330 Dateien, 0 Befunde |

Flakiness: Der Verbindungssturm (mit Deskriptorzähler ohne Schlupf) lief in 5 sauberen Läufen 5-mal grün (Grundlinie, drei Wiederholungen, Volllauf). In weiteren sieben Mutationsläufen, deren Mutation den Sturm nicht berührt, blieb er ebenfalls grün; diese zählen nicht als saubere Läufe. Die Aussage trägt für diese Last und diese Läufe. Mehr als fünf Läufe habe ich wegen der Last des Rechners bewusst nicht gefahren: „Schlupf 0 ohne Flake“ ist damit nicht bewiesen, nur nicht widerlegt.

## 2. Selbst gemutete Fehler (einzeln, Test-Stage mit Stabilitätsfilter; D1 gegen den Volllauf)

| Kürzel | Mutation | Ergebnis |
|---|---|---|
| M2 | `PacketHeader`: Untergrenze `length < 4` statt `< SIZE` (8) | rot: Verbindungssturm, FramingFuzz (Fuzz und Handvektor geteilter Kopf), HandshakeFuzz, StreamCutoff |
| M3 | `PacketHeader`: `>=` statt `>` an `maxPacketBytes` | rot: nur FramingFuzz, aber jetzt deterministisch durch den Handvektor „Paketgrenze nimmt die Länge auf der Grenze an“ (100/101), zusätzlich Fuzz und geteilter Kopf |
| M6 | `MessageReader`: fertige Nachrichten vor dem Fehler verworfen | rot: HandshakeFuzz, StreamCutoff („Fehler hinter vollendeten Nachrichten“) |
| G3 | `WireGenerator.chunks` liefert immer den ganzen Strom | rot: FramingFuzz und HandshakeFuzz (Zerstückelungs-Zähler trägt); im Review lief G3 grün |
| A6 | `MessageAssembler`: Nachrichtengrenze nicht geprüft, wenn der Kopf über zwei Lesevorgänge ankommt | rot: Verbindungssturm, FramingFuzz (Handvektor „Geteilter Paketkopf“ und Fuzz) |
| R1 | `Rendezvous.enter` wartet nicht auf die anderen | rot: Sturm, Meldung „gleichzeitig gemessene Handler expected 105 but was 41“ (natürliche Spitze ohne Sperre: 41) |
| R2 | `Rendezvous` öffnet schon bei 100 statt 105 | rot: Sturm, „expected 105 but was 103“ |
| LK | statt L120 (siehe unten): `SocketChannelAdapter` schließt die 100. Instanz nie (ein Deskriptor) | rot: Sturm, Zeitüberschreitung in „Dateideskriptoren frei“ nach 20 s; der Zähler ohne Schlupf trägt schon bei einem Leck |
| L120 | `ManagedConnection.finish`: Kanal von Verbindung 120 nicht schließen | **überlebt** (alle Tests grün); die Mutation hängt davon ab, welcher Fall die Nummer 120 trägt (Annahmereihenfolge der 12 Threads ist nicht gesteuert), und die Fälle schließen den Kanal meist schon im Treiber (Vermutung, nicht einzeln gemessen); als Prüfstein untauglich, deshalb LK |
| D1 | `HandshakeDriver`: kein `channel.close()` im `finally` bei Ausnahme oder Abbruch | in den Stabilitätstests **überlebt** (elf grün); im Volllauf rot durch `HandshakeDriverTest` („Bricht der Aufrufer ab, ist der Kanal geschlossen“ und „Eine Abbruchausnahme …“); der Pfad liegt nicht in diesem Slice, ist aber gedeckt |
| Q1 | `MessageReader`: Kanalfehler als Ende des Stroms | rot: Sturm, HandshakeFuzz (Zählschlüssel KANAL), StreamCutoff |

Nicht gefahren (Zeit und Last): A1 bis A5, M3b, T1 bis T4, L1, L2a/L2b, U1 aus dem Review; die Zuordnung zu M1 bis M14 des Implementers kann ich nicht belegen. Meine Kürzel sind eigene; M2, M3, M6, G3, A6 sind nach der Beschreibung des Auftrags nachgebaut, R1 und R2 sowie LK nach eigener Definition (die Definitionen des Implementers für R1/R2/L120 lagen mir nicht vor).

Hinweis zur Methode: Beim Zurückschreiben nach A6 blieb in meiner Scratch-Kopie die Zeile `splitHeader = headerFill > 0` samt Feld im `MessageAssembler` stehen (ohne Wirkung, kein Leser); L120, D1, Q1, R1, R2 und LK liefen damit. Der Lauf des Volllaufs und alle Aussagen über den Repo-Stand sind davon nicht berührt (Repo-`src/` blieb unverändert, die Kopie ist danach neu aus `git archive` gebaut).

## 3. Review F-1 bis F-12: behoben oder ehrlich offen?

| ID | Review-Befund | Urteil | Beleg |
|---|---|---|---|
| F-1 | Konjunktiv-Kommentar „würde … wertlos machen“ (§3.7) | **behoben** | `grep` nach würde, wäre, hätte, könnte, sollte, früher, bisher, nicht mehr über alle zehn Dateien unter `stability/`: null Treffer; die Treffer „ersetzt TLS“ und „statt FIN“ beschreiben den Zustand im Indikativ. Der KDoc von `FramingFuzzTest` zur Typmenge ist Indikativ |
| F-2 | „105 gleichzeitige Verbindungen“ nicht gemessen (Spitze 48) | **behoben, selbst gemessen** | `Rendezvous` in `DriverHandler.kt` hält jeden Handler am Eingang; `assertEquals(cases.size, gate.peak)` in jeder Runde. Ohne Sperre (R1) ist die Spitze 41, mit zu früher Öffnung (R2) 103: der Test erzwingt und misst 105. Einschränkung: die Spitze ist durch die Sperre erzwungen, nicht natürlich; was gemessen ist, ist, dass der Listener 105 Handler zugleich zulässt |
| F-3 | Typmenge „nach MS-TDS 2.2.3.1.1“, unabhängig behauptet | **behoben (Festlegung gekennzeichnet)** | `FramingOracle.KNOWN_TYPES`: „Festlegung der Referenz … kein Beleg gegen einen Standardtext“, keine Abschnittsnummer im Verzeichnis `stability/`; Test „Typmenge der Referenz läuft mit PacketType gleich“; §6 des Slice nennt es offen mit Begründung. Rest außerhalb des Diffs: Produktionscode (`PacketType`, `PacketHeader`, `PacketStatus`, `TdsMessage`) und `PacketHeaderTest` tragen weiter Abschnittsnummern „MS-TDS 2.2.3.1.x“, im Repo findet sich dafür keine Quelle ([AGENTS.md](../../AGENTS.md) §3.8); nicht Gegenstand dieses Slice |
| F-4 | Zerstückelung ohne Zähler (G3 grün) | **behoben, nachgemessen** | Zähler `ZERSTUECKELT` und `BYTENWEISE` in `FramingFuzzTest` und `HandshakeFuzzTest`; G3 jetzt rot (Abschnitt 2); A6 rot, zusätzlich durch Handvektor „Geteilter Paketkopf“ an jeder Teilungsstelle 1 bis 7 (fünf Fehlerarten) |
| F-5 | Deskriptorzähler mit Schlupf 3, trägt ab vier Lecks | **behoben, mit Vorbehalt** | `DESCRIPTOR_SLACK = 0`; LK (ein Leck) rot. Vorbehalt: ein Schlupf von 0 am Prozesszähler kann bei fremden Öffnungen (Klassenlader, Zufallsquellen) flackern; fünf saubere Läufe grün, mehr nicht gemessen. Die Aussage im Slice („trägt gemessen schon bei einem zusätzlich offenen Deskriptor“) ist durch LK belegt; „Lecks der Aufwärmrunde unsichtbar“ ist ehrlich genannt |
| F-6 | `closed` und `bytesWritten` ohne Leser (D1 grün) | **teils behoben** | `assertOutcome` in `HandshakeFuzzTest` liest beide (`script.closed` nach Closed, `bytesWritten > 0` nach Ready). D1 in der Form des Reviews (kein Schließen bei Ausnahme oder Abbruch) bleibt in den Stabilitätstests grün, ist aber durch `HandshakeDriverTest` gedeckt (Abschnitt 2); kein offener Mangel, der Befund war eine Lücke dieser Testgruppe |
| F-7 | Zählschlüssel `Closed:READ_FAILED` vermengt Rahmen- und Kanalfehler | **behoben** | Schlüssel `Closed:READ_FAILED:RAHMEN` und `…:KANAL`, je mindestens 30 verlangt; Q1 rot im HandshakeFuzz. Der Schlüssel entsteht aus Erwartung der Referenz und Skriptende, nicht aus einer eigenen Beobachtung des Treibers; das ist für die Trennung ausreichend |
| F-8 | Offsets und Längen nur zufällig überschrieben | **behoben** | Veränderung Nr. 11 (`offsetField`) setzt gezielt ein Offset- oder Längenfeld in PRELOGIN (Big-Endian, Tabelleneintrag) oder LOGIN7 (Little-Endian, Slot-Tabelle ab Byte 36 und Längenfeld) auf Randwerte; Zähler `Offset:Closed:PROTOCOL_VIOLATION` mindestens 30. Offen, aber ehrlich begrenzt: der Zähler unterscheidet nicht PRELOGIN von LOGIN7, und die Lagen der Felder stammen aus dem Parser des Repos (KDoc: „beide Lagen stehen im Parser des Repos“), nicht aus einem Standardtext; ich habe keine Parser-Mutation gefahren, die das Feld-Wissen prüft |
| F-9 | Toleranz `extra in 0..hung` im Sturm | **behoben** | `handshakeOutcomes` erkennt späte Lesefehler an der Verbindungsnummer (Ende `LOGIN_TIMEOUT`), verlangt dort nur `READ_FAILED` und zählt sie nicht mit; die übrigen Ausgänge müssen genau den Erwartungen entsprechen |
| F-10 | `StormSupport.kt` 321 Zeilen | **behoben** | geteilt in `DriverHandler.kt` (118), `StormPeers.kt` (112), `StormCases.kt` (148); keine Datei des Pakets über 261 Zeilen |
| F-11 | Abgrenzung geprüft, INFO | **bestätigt, mit Nachtrag** | siehe V-1, V-2 |
| F-12 | Flakiness INFO | **fortgeführt** | Abschnitt 1; keine Fehlschläge in meinen Läufen |

## 4. DoD Punkt für Punkt

### Liefer-Punkte

| Punkt | Urteil | Beleg |
|---|---|---|
| **Negativ- und Fragmentierungstests nach [SPEC-027](../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge)** (Codec, Leser, Anmeldepfad; Anfragepfad nicht belegt) | **bestätigt** | `StreamCutoffTest` (Abbruch an jeder Byteposition, drei Puffer, beide Enden, Grenzen von Hand gezählt), `FramingFuzzTest` (Handvektoren Paketgrenze 100/101, Nachrichtengrenze 255/256/257, geteilter Kopf), `ConnectionStormTest` (21 Fälle am Loopback); M2, M3, M6, Q1 rot. Das Häkchen ist durch den Gegenstand gedeckt, der Zusatz „Anfragepfad nach dem Login nicht belegt“ steht im Punkt selbst |
| **Fuzz-/Property-Tests: Paketlängen, Offsets, Typwerte** | **bestätigt, mit Einschränkung** | Fuzz des Codecs gegen `FramingOracle` (3 Grenzenpaare mal 3000 Läufe, Zähler je Fehlerart und Zerstückelung), Anmelde-Fuzz (4000 Läufe, zwölf Veränderungen einschließlich Offset- und Längenfeldern). Einschränkungen, die der Punkt selbst nennt: die Referenz teilt Regeln und Typmenge mit dem Codec (Selbstkonsistenz, kein Standardbeleg); RPC und SQL_BATCH nur als Parser-Fuzz ohne Transport. Der Wortlaut „RPC- und SQL_BATCH-Nachrichten belegen nur die Fuzz-Tests ihrer Parser“ stimmt weiterhin (`SqlBatchParserTest`, `RpcFuzzTest` vorhanden) |
| **Test mit parallelen Verbindungen bis `MAX_CONNECTIONS` ([SPEC-034](../../spec/spezifikation.md#3-defaults-und-konstanten))** | **offen, ehrlich geführt** (Häkchen leer) | `grep` über `src/main` für `MAX_CONNECTIONS` und `maxConnections`: leer; über `src/` nur der KDoc des Sturmtests („im Code nicht vorhanden“). Der Test belegt 105 Handler zugleich, keine Grenze und keine Abweisung. Die Matrix `docs/plan/spec-abdeckung.md` führt `SPEC-034` als „OHNE NACHWEIS“, die Tests tragen den Tag für `SPEC-034` nicht. **Aber** der benannte Träger trägt nicht, siehe V-1 |
| `make gates` grün | **bestätigt** | Abschnitt 1 (Exit 0; frischer Volllauf der Tests 500 grün) |

### Closure-Pflichten (noch nicht erfüllt, kein Mangel)

Review liegt vor (`2026-10-30-cb4c853-slice-qa-stabilitaet.md`), das Häkchen steht offen; die Fix-Runde hat keinen eigenen Nach-Review, dieser Bericht ersetzt ihn nicht (anderer Blick). Doku-Update, Closure-Notiz mit Lerneintrag (§7 leer), Beobachtungs-Register, Risiko-Ausgänge (alle sechs „offen bis zur Closure“) und die drei Paarungen sind offen, wie es vor der Closure sein soll. Das Register `../observations/` führt laut Slice-§8 „nur die README“; im Verzeichnis liegen inzwischen Beobachtungen unter `BEO-TDS` (der Review zitiert drei); die Aussage in §8 ist beim Schließen zu erneuern.

## 5. Findings des Verifiers

| ID | Kategorie | Befund | Quelle | Verifizierbar |
|---|---|---|---|---|
| V-1 | MEDIUM | **Träger der Grenze `MAX_CONNECTIONS` trägt nicht.** §6 und der DoD-Punkt nennen `slice-abbruch-ressourcen-shutdown` als Träger („führt `SPEC-034`“, „offen bis … sie liefert“). Dessen DoD enthält keine Begrenzung der Verbindungszahl; `SPEC-034` steht nur in der Zeile „Berührte Spec-Stellen“, und sein §1 schließt „Test mit parallelen Verbindungen bis `MAX_CONNECTIONS` als Gate“ ausdrücklich aus und verweist auf `slice-qa-stabilitaet` zurück; `welle-fehler-abbruch` sagt dasselbe. `slice-betrieb-zustandsereignisse` (§6) hält fest, die Durchsetzung habe „keinen eindeutigen Träger“. Die Schleife (Stabilität wartet auf Shutdown, Shutdown verweist auf Stabilität) schließt den DoD-Punkt nie. Die Aussage „offen“ ist ehrlich, die Zuordnung des Trägers ist es nicht. Folge für den Planner: einen Träger bestimmen (eigener Slice oder ausdrücklicher DoD-Punkt im Shutdown-Slice) oder den Punkt aus diesem Slice herauslösen | [AGENTS.md](../../AGENTS.md) §3.8, Traceability [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität) | ja (Lesen der drei Pläne) |
| V-2 | MEDIUM | **Rückbezug auf `slice-sql-batch-dispatch` veraltet und der Träger trägt die Fuzz-Messung nicht.** `slice-sql-batch-dispatch` ist gemerged (`2bd1a49`): im Code liegen `RequestLoop`, `ConnectionWiring`, `Server` und `Main`; SQL_BATCH und Sequenzierer sind angebunden. Der Slice-Text sagt weiter „Es gibt keinen verdrahteten Serverprozess, keinen SQL_BATCH-Pfad“ (§3, Ist-Zustand) und „Kein SQL_BATCH- und kein RPC-Pfad in der Sitzung, keine Anbindung des Sequenzierers“ (§6). Das stimmt für den Test-Handler `DriverHandler` (verwirft Nachrichten nach dem Login), nicht mehr für das Repo. **Fuzz mit SQL_BATCH in angemeldeter Sitzung ist weiter ungemessen:** in `protocol/dispatch` und `runtime` kommt weder `Random` noch ein Fuzz-Test vor; die Tests dort sind feste Fälle (ungültige Kodierung, Ende der Gegenstelle, Abbruch mitten in der Antwort, RPC/ATTENTION unbeantwortet). Der Plan von `slice-sql-batch-dispatch` führt [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität) im Bezug, plant aber weder Fuzz noch Fragmentierung noch Parallelität; die Nennung als Träger in §6 trägt also nicht. Die Stabilitätstests laufen gegen den Treiber, nicht gegen die verdrahtete Schleife; eine Anbindung an `RequestLoop` ist jetzt möglich | AGENTS.md §3.8 | ja (`grep` Random in `dispatch/` und `runtime/`) |
| V-3 | LOW | Zeile 72 des Slice: „`grep` über `src/` findet weder `MAX_CONNECTIONS` noch `maxConnections`“ ist wörtlich falsch, `src/` enthält den Treffer im KDoc des Sturmtests; richtig ist `src/main` | AGENTS.md §3.8 | ja |
| V-4 | INFO | Der Sturmtest trägt eine unausgesprochene Kopplung: 10 der 105 Fälle (`GOOD_THEN_RESET`, `RESET_IN_LOGIN7`, je 5-mal) sind blockierende Clients auf den 12 Threads des Pools und warten auf Antworten, die der Handler erst nach der Eintrittssperre schickt. Bei `COPIES` von 6 oder mehr (12 blockierte Threads) blockiert der Lauf nach dem Lesen des Codes bis zur 60-Sekunden-Zeitüberschreitung statt einen Fehler der Software zu zeigen (Lesebefund, nicht gemessen). Bei den heutigen Werten (5 Kopien, 12 Threads) kein Fehler; die Kopplung gehört in einen Kommentar der Klasse (Kopplung), sie steht dort nicht | AGENTS.md §3.7 | nein (Lesen) |
| V-5 | INFO | Zwei Prüfsteine sind nur mit Vorbehalt Prüfsteine: L120 (abhängig von der Annahmereihenfolge) überlebt, LK (reihenfolgeunabhängig) ist rot. Wer „L120“ in einer Closure-Notiz als belegt führt, muss die Form nennen | – | ja (Abschnitt 2) |

## 6. Plan-vs-Code-Diff

| Plan (§3) | Code | Urteil |
|---|---|---|
| Testquellen des Protokollpfads, neu | zehn Dateien unter `stability/`, 1499 Zeilen | entspricht |
| Referenz `FramingOracle`, Erzeuger `WireGenerator`, Fuzz des Codecs und des Anmeldepfads, Abbruch an jeder Position, Verbindungssturm gegen den Listener | vorhanden (`FramingOracle`, `WireGenerator`, `FramingFuzzTest`, `HandshakeFuzzTest`, `StreamCutoffTest`, `ConnectionStormTest` mit `DriverHandler`, `StormPeers`, `StormCases`) | entspricht; die Teilung des Sturm-Hilfscodes ist gegenüber dem Plan eine Verfeinerung |
| `tools/mk/`-Fragment entfällt | `make test` führt die Tests aus (11 Tests im Filterlauf, 500 im Volllauf) | entspricht, Begründung steht im Plan |
| Produktionscode unverändert | `git diff cb4c853^1 cb4c853` und die Fix-Runde berühren `src/main` nicht | entspricht |
| Ist-Zustand „kein Serverprozess, kein SQL_BATCH-Pfad“ | am Stand `dcb6b27` überholt (V-2) | Abweichung des Plans vom Repo, nicht des Codes vom Plan |

## 7. Rückwirkung (Rückwärtspfad)

- **Spec-Lücke:** `SPEC-034` hat keine Durchsetzung und keinen Träger (V-1). Das ist kein Mangel dieses Slice, sondern ein Planungsfehler; die Lücke gehört in die Closure-Notiz und in einen Slice des Planners.
- **Steering-Loop-Kandidat:** „Mutation, die von einem nicht gesteuerten Ablauf (Annahmereihenfolge) abhängt, ist kein Prüfstein“ (V-5) und „Träger-Zeile eines Risikos mit Rückverweis des Trägers abgleichen“ (V-1, V-2). Beide sind einmalige Beobachtungen, keine Schwelle.

## 8. Urteil

**Fix-Runde:** F-1 bis F-10 sind behoben oder ehrlich eingeschränkt (F-6 und F-8 mit benannten Rändern, F-5 mit offenem Flake-Vorbehalt über fünf Läufe hinaus). Die kritischen Mutationen M2, M3, M6, G3, A6, R1, R2, Q1 und LK sind rot; L120 und D1 überleben in den Stabilitätstests, aus den genannten Gründen ohne offenen Mangel.

**DoD:** Die zwei gesetzten Liefer-Häkchen und `make gates` sind getragen; der dritte Liefer-Punkt (`MAX_CONNECTIONS`) ist offen und als offen geführt. Die Slice-Datei ist nicht schließbar, bevor V-1 (Träger) und V-2 (veralteter Ist-Zustand, fehlender Träger der Fuzz-Messung in angemeldeter Sitzung) bearbeitet sind; V-1 und V-2 gehen an den Planner.

**Übergabe:** Bericht an den Planner. Gate-Ergebnis des Prüfers für diesen Bericht: `make docs-check` nach Ablage, Ergebnis im Nachtrag unten.

## Nachtrag: docs-check

`make docs-check` mit diesem Bericht: 330 Dateien geprüft, 0 Befunde (Exit 0). Nicht committet.
