# Verifikation slice-sql-batch-dispatch (DoD- und Entscheidungs-Konformität)

**Rolle:** Verifier (Modul 11) · **Stand:** `main` bei Beginn `ca83322` (enthält Umsetzung `2bd1a49` und Fix-Runde `aeab8a9`), bei Ende `3e78067`; `git diff ca83322 HEAD -- src spec config build.gradle.kts Dockerfile` ist leer, alle Läufe betreffen denselben Code · **Datum:** 2026-10-30
**Gegenstand:** [slice-sql-batch-dispatch](../plan/planning/done/slice-sql-batch-dispatch.md), Welle welle-anfrageverarbeitung
**Bezug:** [TDS-FA-03](../../spec/lastenheft.md#tds-fa-03--einfache-sql-batches), [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität), [ADR-0004](../plan/adr/0004-session-zustandsautomat.md), [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md), [ADR-0007](../plan/adr/0007-fehler-und-attention.md); Spezifikation [SPEC-001](../../spec/spezifikation.md#spec-001--zustandsautomat-einer-verbindung), [TDS-FA-03.a](../../spec/spezifikation.md#tds-fa-03a--sql_batch)
**Vorgänger:** Review [2026-10-30-2bd1a49-slice-sql-batch-dispatch](2026-10-30-2bd1a49-slice-sql-batch-dispatch.md) (F-1 bis F-10)
**Adressat:** Planner. Der Verifier repariert nichts und entscheidet den Schnitt nicht. Nichts committet; geändert ist nur diese Datei.

## 1. Urteil

**Die drei Liefer-Punkte und der Zusatzpunkt (Verdrahtung des Fehler-Mappings) sind im Code bestätigt; die Fix-Runde hält der Nachprüfung stand, mit einem Mangel am Träger und zwei kleinen Restpunkten.** Alle Mutanten außer einem (K5, ein Zusatzmutant ohne Auftrag) sind rot. Der Slice ist noch nicht schließbar: die Closure-Pflichten (Häkchen, Closure-Notiz §7 mit Zuordnung *Test | Festlegung*, Risiko-Ausgänge, Register, Paarungen) stehen aus, wie es vor der Closure sein soll.

Vor der Closure zu klären:

1. **V-1 (MEDIUM):** Der Träger für den Disconnect während einer Anfrage ist falsch benannt. Die Fix-Runde nennt `slice-attention-cancel-timeout`; dieser Slice schließt den Disconnect aus und verweist selbst auf `slice-abbruch-ressourcen-shutdown`. Die Datei von `slice-attention-cancel-timeout` ist in der Fix-Runde nicht geändert worden, entgegen der Commit-Nachricht.
2. V-2 (LOW): SPEC-001 trägt zwei Sätze, die gebautes Verhalten lesen lassen, obwohl der folgende Absatz es als nicht gebaut führt.
3. V-3 (INFO): ein Zusatzmutant (K5) überlebt; die Aussage im KDoc des Tests ist für COLMETADATA enger belegt als formuliert.

## 2. Selbst gefahrene Läufe (Belege, nicht Behauptungen)

Alle Läufe in einer Kopie des Baums (`git archive` nach `scratchpad/v9`, Arbeitsbaum des Repos unverändert), `docker build --target test --no-cache-filter test`, nichts gemountet, ein Build zur Zeit, keine Lasttests, keine Wiederholungsserien.

| Lauf | Ergebnis |
|---|---|
| Grundlinie, frisch (Test-Stage ohne Cache) | grün: 503 Tests `PASSED`, 0 `FAILED`, `BUILD SUCCESSFUL in 55s` |
| `make gates` | Exit 0 in 9 s; **Hinweis:** die Stage `test` kam aus dem Cache (`CACHED`), für sich kein frischer Beleg; den liefert der Grundlinienlauf darüber. d-check: 355 Dateien, 0 Befunde; `baseline-verify` v6.18.1 ok; detekt-Stage durchlaufen |
| `make docs-check` mit diesem Bericht | siehe Nachtrag am Ende |

Die Belege des Implementers (Mutanten M1 bis M16 und K bis K4 im gemeinsamen Scratchpad) habe ich nicht übernommen. Ihre Definitionen lagen mir nicht vor; ich kenne nur die Dateinamen der Logs (zum Beispiel `M10-Senden-ausserhalb-Sperre`, `M15-Typfehler-offen`) und habe die Mutanten danach nachgebaut. Die Kürzel unten sind meine; K, K2, K3 und K4 sind nach dem Auftrag und den Befunden des Reviews eigen definiert.

## 3. Selbst gemutete Fehler (einzeln, Grundlinie grün)

| Kürzel | Mutation | Ergebnis |
|---|---|---|
| M1 | `RequestLoop.execute`: Verbindungsfolge ignoriert (`ConnectionFate.OPEN` statt `errors.fateOf(...)`) | rot, 4 Tests: Ausnahme aus execute/Strom, Senke lehnt Token ab, schließender Ausgang (`DispatchFailureTest`), schließender Ausgang am Socket (`RequestWiringTest`) |
| M8 | `HandshakeAutomaton.forward`: SQL_BATCH setzt nicht `RUNNING` | rot, 20 Tests (Dispatch-, Matrix- und Wiring-Tests) |
| M10 | `HandshakeSession.dispatch`: Übergang unter der Sperre, Senden außerhalb | rot, 2 Tests: „Die Antwort wird unter der Sperre der Sitzung geschrieben“, „Das Schliessen wartet auf das laufende Schreiben“ |
| M11 | `RequestLoop.run`: kein `channel.close()` im `finally` | rot, 2 Tests: Abbruch mitten in der Antwort; RPC, TM und ATTENTION |
| M12 | Matrix: `RUNNING` nimmt eine Anfragenachricht an | rot, 3 Tests (Handtabelle beider Reihenfolgen, SQL_BATCH vor READY, Zellzählung) |
| M14 | `requestFinished`: Antwort auch bei `CLOSE_WITHOUT_REPLY` gesendet | rot, 2 Tests: Senke lehnt Token ab; Verbindungsfolge bestimmt Antwort und Zustand |
| M15 | `ErrorMapper.fateOf`: `FAILED_CLOSE_CONNECTION` als `OPEN` | rot, 8 Tests, darunter Ausnahme aus execute/Strom, schließender Ausgang, Socket-Test, `ErrorMapperTest` |
| K | `BufferedResponse.accept`: Ergebnis von `encoder.write` ignoriert | rot, 1 Test: „BufferedResponse lehnt ein Token ab, das der Encoder nicht schreibt“ |
| K2 | `ResponseRun.write`: Ablehnung der Senke ignoriert (`CLOSE_WITHOUT_COMPLETION` nie gesetzt) | rot, 9 Tests, darunter „Eine Senke, die ein Token ablehnt, schliesst ohne Antwort“ |
| K3 | `ResponseSequencer.run`: Ausnahme aus `execute()` nicht gefangen | rot, 3 Tests, darunter „Eine Ausnahme aus execute oder aus dem Strom …“ |
| K4 | `ResponseSequencer.run`: Ausnahme des Stroms verschluckt statt an `fault` gegeben | rot, 5 Tests (nur `Response*`-Tests; **nicht** der Dispatch-Test, siehe unten) |
| K5 | Zusatzmutant: `BufferedResponse` ruft `onResultStart()` vor dem Schreiben des COLMETADATA | **überlebt**, 503 Tests grün (V-3) |

Der Review-Mutant K (Fehler der Senke ignoriert) ist mit K und K2 doppelt gedeckt: K an der Senke selbst, K2 am Sequenzierer; die Schleife reagiert auf den Ausgang, was M14 und M1 halten.

Zu K4: Der Dispatch-Test „Eine Ausnahme aus execute oder aus dem Strom“ fällt unter K4 nicht. Der Strom des Testfalls emittiert vor der Ausnahme ein ResultStart; wird die Ausnahme verschluckt, endet er in einem offenen Ergebnis, `end()` meldet einen Reihenfolgeverstoß, und der Ausgang (ERROR `TDS_INTERNAL_ERROR`, schließt) ist von außen derselbe. Den Unterschied (Ausnahme gegen Verstoß) halten die `Response*`-Tests, die `internal` mit der Ausnahme prüfen. Das ist keine Lücke der DoD, nur die Auskunft, wer K4 tötet (Ursache aus dem Code gelesen, nicht eigens nachgemessen).

## 4. Review F-1 bis F-10: behoben oder ehrlich offen?

| ID | Review-Befund | Urteil | Beleg |
|---|---|---|---|
| F-1 | SPEC-001 sagt „Nachricht in RUNNING/STREAMING“ unzulässig neben „ATTENTION zulässig“ ohne Kennzeichnung | **behoben, mit Rest V-2** | Spec: „Eine Anfragenachricht (SQL_BATCH, RPC) in `RUNNING` oder `STREAMING` ist ein unzulässiger Übergang“; „passenden laufenden Anfragezustand zulässig (Zielbild, Stand der Festlegung unten)“; eigener Absatz „ATTENTION in jedem Anfragezustand ist offen … Gebaut ist sie nicht … (Festlegung, nicht gemessen)“. Der Absatz benennt, dass `CANCELLING` kein Zustand der Aufzählung ist und der Automat ATTENTION in `RUNNING`/`STREAMING` als unzulässig behandelt; `HandshakeMatrix` und der Matrixtest halten das (M12 rot) |
| F-2 | Code schließt bei ATTENTION in `READY`, Spec sagt Bestätigung | **behoben (Festlegung gekennzeichnet)** | Spec: „ATTENTION in `READY` (`IDLE`) bleibt unbeantwortet, und die Verbindung schließt“, „Festlegung, nicht als Zusage“, die Bestätigung als „Zielbild“. Träger des Zielbilds: [slice-attention-cancel-timeout](../plan/planning/open/slice-attention-cancel-timeout.md) DoD-Punkt 2 und §6 (ATTENTION in `IDLE`) |
| F-3 | `BufferedResponse` ohne Obergrenze, Träger nennt die Stelle nicht | **ehrlich offen, Träger benannt** | [slice-backend-streaming-nachweis](../plan/planning/next/slice-backend-streaming-nachweis.md) DoD-Punkt 1: „Die Verdrahtung ersetzt `BufferedResponse` (`protocol/dispatch`)“, §6 trägt das Risiko mit Ausgang; im Dispatch-Slice §6 ebenso, mit „ein Größentest fehlt“. Heute verdrahtet `Main` kein Backend (`Wiring(log = ServerLog.STDOUT)`, `ConnectionWiring`: ohne Backend nur `channel.receive()`), das Risiko ist nicht erreichbar. Nebenbefund zum Träger des Disconnects: V-1 |
| F-4 | Überlebender Mutant K, Ausgang `CLOSE_WITHOUT_COMPLETION` nie durch die Schleife | **behoben, nachgemessen** | `DispatchClosingTest` fährt die Senke, die das erste und das zweite Token ablehnt: keine Antwort, Kanal zu, Folgenachricht ungelesen, Backend einmal geschlossen, Zustand `CLOSED`. Ausnahme aus `execute()` und aus dem Strom: ERROR `TDS_INTERNAL_ERROR` Class 20 plus DONE mit Fehlerstatus, Verbindung schließt. K, K2, K3, M14, M1 rot (Abschnitt 3) |
| F-5 | Abschnittsnummer „MS-TDS 2.2.5.3“ ohne Quelle | **behoben** | `DispatchSupport.kt`: „ALL_HEADERS nach MS-TDS“; `grep` nach `2.2.5.3` und `MS-TDS 2.` unter `protocol/dispatch` (Haupt- und Testcode) und im `RequestWiringTest`: null Treffer |
| F-6 | KDoc von `ConnectionState` beschreibt abwesenden Text | **behoben** | „`SPEC-001` legt ihn flach fest (Setzung des Nutzers, für den Anfragezustand nicht gemessen)“; deckt sich mit der Spec |
| F-7 | Sicht nur zur Hälfte nachgeführt | **behoben** | `spec/architecture.md`: Titel von ARC-015 „Response Sequencer“ (Komponentendiagramm Zeile 19 und Tabelle Zeile 66), in beiden Sequenzdiagrammen `T->>S: ATTENTION melden` und `S->>Q: cancel`, Absatz „Zustands- und Ownership-Grenzen“ nennt Handshake / Session als Halter des Anfragezustands samt Sperre und den Sequenzierer „ohne den Anfragezustand zu halten“. `grep` nach „Cancel State“ in `spec/`, `docs/plan/adr/` und `README.md`: null Treffer. Gate-Regeln: kein „ADR“, kein Pfad außerhalb `spec/`, kein Slice, keine Welle, kein Hash in `spec/*.md` (`grep` null Treffer) |
| F-8 | Folge-ADR-Frage (INFO) | **bestätigt, ehrlich offen** | [ADR-0004](../plan/adr/0004-session-zustandsautomat.md) ist im Diff unverändert (§3.5 gehalten) und führt den Zuschnitt weiter als offen neben der Spec-Festlegung „flach“; die Entscheidung liegt beim Architect, nicht bei diesem Slice |
| F-9 | Disconnect erst beim Senden bemerkt (INFO) | **ehrlich offen, Träger falsch benannt** | V-1 |
| F-10 | Namen `STREAMING` (INFO) | **bestätigt** | Code, Spec und ADR sagen `STREAMING` |

## 5. Test-Naht `sinkAround` (Produktionscode für Tests)

**Befund der Prüfung:** `RequestLoop` (öffentliche Klasse) trägt einen sechsten Konstruktorparameter `sinkAround: (TokenSink) -> TokenSink = { it }`. Einziger Aufrufer mit einem Wert: `DispatchSupport.serve` im Test; `ConnectionWiring` ruft ohne ihn. Der Parameter wird in `execute` einmal angewandt (`sequencer.run(backend, request, sinkAround(response))`).

**Urteil: akzeptabel, nicht blockierend.** Gründe:

- Keine Hard Rule verbietet sie. §3.1 (Docker-only), §3.2 (Suppression), §3.4 (Sicht meilensteinfrei), §3.5 und §3.6 sind nicht berührt; das Gate (`detekt`, Schichtregeln) läuft grün. §3.7 verlangt, dass ein Kommentar eine der Klassen Zusage, Kopplung, Abgrenzung, Rang-Zeiger, Grenze trägt: der Absatz ist mit „Kopplung:“ eingeleitet und beschreibt den Zustand im Indikativ. Architektur: der Parameter trägt `TokenSink`, ein Typ derselben Schicht (`protocol.response`), keinen Testtyp und keine Abhängigkeit nach außen; die Schichtregeln aus ARC (Protocol darf Domain, Backend Port, Transport-Schnittstellen) sind unberührt.
- Die Voreinstellung ist die Identität; der Produktionspfad ändert sich nicht (Mutant M14, M1 laufen darüber).
- Ohne die Naht wäre der Ausgang `CLOSE_WITHOUT_COMPLETION` durch die Schleife nicht fahrbar, wenn die Aussage stimmt, dass kein Backend-Ereignis den Encoder scheitern lässt; es bliebe der überlebende Mutant des Reviews oder eine Prüfung nur auf der Ebene der reinen Funktion. Die Naht ist der kleinste Einhängepunkt (ein Parameter, eine Anwendungsstelle).

Vorbehalte, die der Planner kennen soll:

- Der Satz im KDoc „Kein Backend-Ereignis lässt den Encoder ein Token ablehnen (die Abbildungen von Spalten und Werten prüfen dieselben Grenzen)“ ist eine Aussage über den Bestand und nicht als Festlegung gekennzeichnet (AGENTS.md §3.8). Ein Teilbeleg liegt in den Abbildungs-Fuzz-Tests (`MappingFuzzTest`, `MappingSupport` schreiben abgebildete Werte durch den Encoder); ob er für alle Ereignisse trägt, habe ich nicht nachgewiesen.
- Die Sichtbarkeit ist öffentlich. Ein sekundärer `internal`-Konstruktor würde die Naht auf das Modul begrenzen; ob das den Zusatzaufwand lohnt, ist Stilfrage des Architect, kein Mangel.
- Die Naht beweist die Reaktion der Schleife auf eine ablehnende Senke, nicht, dass ein echtes Ereignis eine ablehnt (dasselbe Argument wie oben).

## 6. DoD Punkt für Punkt

### Liefer-Punkte

| Punkt | Urteil | Beleg |
|---|---|---|
| **Annahme und Dispatch** ([TDS-FA-03.a](../../spec/spezifikation.md#tds-fa-03a--sql_batch), SPEC-001, ARC-005) | **bestätigt** | `RequestLoop.step`: Nachricht → Rahmen → `RUNNING`; `SqlBatchParser.parse` liest ALL_HEADERS und dekodiert UTF-16. Test „Das Backend empfaengt Sitzung, neue Anfrage-ID und den Text unveraendert“ (fünf Texte, darunter leer, `SET`, Unicode, Steuerzeichen; leere Parameterliste, Frist `null`); „Die Anfrage-ID ist je Anfrage neu“; „Eine ungueltige Kodierung ergibt eine Fehlerantwort ohne Backend-Aufruf“. M8 rot (20 Tests), M12 rot. SQL_BATCH außerhalb `READY`: `HandshakeMatrixTest` „SQL_BATCH vor READY beendet die Verbindung, in READY wird sie weitergegeben“ und „Ein SQL_BATCH vor dem Login wird nicht beantwortet“ (Test, kein neues Verhalten) |
| **Ergebnis und leeres Ergebnis** ([TDS-FA-03](../../spec/lastenheft.md#tds-fa-03--einfache-sql-batches), SPEC-031) | **bestätigt** | „Ein Ergebnis wird als COLMETADATA, ROW und DONE beantwortet“; „Ein leeres Ergebnis hat Metadaten und einen Abschluss ohne ROW“ (beide Varianten `ResultEnd(0)` und `ResultEnd(null)` gegen Handvektoren); „Zwei Anfragen in Folge werden beantwortet und die Sitzung ist dazwischen READY“ (drei Leseaufrufe nach dem Login); „Der Anfragezustand laeuft von READY ueber RUNNING nach STREAMING“ |
| **Nicht unterstütztes SQL** ([TDS-FA-03](../../spec/lastenheft.md#tds-fa-03--einfache-sql-batches) Negativ) | **bestätigt** | „Ein Fehler des Backends vor jeder Zeile laesst die Verbindung offen“: `TDS_UNSUPPORTED_SQL` (Class 16), ERROR plus DONE mit Fehlerstatus, Text des Backends nicht in der Antwort, Folgebatch erfolgreich, zwei Backend-Schließungen. Die Aussage „Verbindung bleibt offen“ ist Setzung des Nutzers und nicht gemessen; der Plan sagt das selbst |
| **Verdrahtung des Fehler-Mappings** (kein eigener Punkt) | **bestätigt, sechs Fälle selbst nachgeprüft** | `RequestLoop` baut `ResponseSequencer(errors.sequencerMapping())` und liest `errors.fateOf(sequencer.run(...))` (M1 und M15 rot). Der Test „Ein schliessender Ausgang endet nicht als offene Verbindung“ hat sechs Fälle: `ReturnStatus`, `OutputParameter`, Zeile ohne Ergebnis, fremder Code, Code des Servers, `TDS_TIMEOUT`. Jeder prüft: genau eine Antwort, ERROR-Meldung des Falls, DONE-Fehlerstatus am Ende, Kanal geschlossen, Folgenachricht ungelesen (`exhausted` falsch), ein Backend-Aufruf, ein `close()`, Zustand `CLOSED`, Leseaufrufe. Dazu in `DispatchClosingTest` vier weitere schließende Ausgänge (Senke lehnt das erste oder zweite Token ab, Ausnahme aus `execute()`, Ausnahme aus dem Strom) und der Socket-Test in `RequestWiringTest`. Zusammen decken die Tests alle vier Ausgänge von `fateOf` ab (`COMPLETED`/`FAILED` offen, `FAILED_CLOSE_CONNECTION`, `CLOSE_WITHOUT_COMPLETION`) |

### Nachweis-Pflichten

| Pflicht | Urteil | Beleg |
|---|---|---|
| `@spec SPEC-<NNN>` über jeder Testfunktion, Tests in `make test` grün | **bestätigt** | alle Testfunktionen in `SqlBatchDispatchTest`, `DispatchFailureTest`, `DispatchClosingTest` tragen den Tag; die Matrix `docs/plan/spec-abdeckung.md` zählt SPEC-001 und SPEC-031 mit bestandenen Tests dieser Klassen; Grundlinie 503 grün |
| Jede Aussage an einem Test oder als Festlegung (KDoc); Zuordnung in der Closure-Notiz | **teils; Closure-Notiz steht aus** | KDoc von `RequestLoop`, `BufferedResponse`, `HandshakeMatrix`, `ConnectionState` kennzeichnen Festlegungen; die Zuordnungstabelle gehört in §7, der noch „Wird bei der Closure gefüllt“ trägt. Restaussage: V-3 und der Satz in Abschnitt 5 |
| `make gates` grün | **bestätigt** | Abschnitt 2 |
| Review durchgeführt | **bestätigt** | [Review 2bd1a49](2026-10-30-2bd1a49-slice-sql-batch-dispatch.md) liegt vor; die Fix-Runde hat keinen eigenen Nach-Review (dieser Bericht ersetzt ihn nicht) |
| Doku-Update bei öffentlichem Vertrag | **bestätigt** | SPEC-001 und ARC-004/ARC-015 nachgeführt (Abschnitt 4) |
| Closure-Notiz, Register, Risiko-Ausgänge, Paarungen | **offen, wie vor der Closure** | alle Häkchen leer; §6 trägt zwei Ausgänge „weiter offen: →“, fünf „offen bis zur Closure“ |

### Out-of-Scope

| Grenze | Urteil | Beleg |
|---|---|---|
| Main verdrahtet kein Backend | **eingehalten** | `Main` ruft `Wiring(log = ServerLog.STDOUT)`; `ConnectionWiring` nimmt das Backend als optionalen Konstruktorparameter und nutzt ohne Backend den alten Pfad (`channel.receive()`) |
| Kein Simulations-Backend, kein zweites Backend im Produktivcode | **eingehalten** | `ScriptedBackend` ist Test-Fixture (`src/test`); `grep` nach `RequestLoop(` in `src/main`: nur `ConnectionWiring` |
| Parameter, RPC, Mehrfach-Ergebnisse, ATTENTION, Cancel, Timeout, Frist | **eingehalten** | RPC, Transaction Manager und ATTENTION in `READY` schließen unbeantwortet (Festlegung in SPEC-001 und KDoc, Test hält es); Parameterliste leer, Frist `null` (Test) |
| SET, Transaktionen, SQL-Parsen | **eingehalten** | Der Text geht unverändert weiter (Test mit `SET FMTONLY ON; select 1`); kein SQL-Parser im Diff |
| Kein Gate gelockert | **eingehalten** | `config/`, `build.gradle.kts`, `Dockerfile` im Bereich `ca83322` bis `HEAD` unverändert; `grep` nach `@Suppress` im Dispatch-Diff: null |

## 7. Findings des Verifiers

| ID | Kategorie | Befund | Quelle | Verifizierbar |
|---|---|---|---|---|
| V-1 | MEDIUM | **Träger des Disconnects falsch benannt.** [slice-sql-batch-dispatch](../plan/planning/done/slice-sql-batch-dispatch.md) §6 und [slice-backend-streaming-nachweis](../plan/planning/next/slice-backend-streaming-nachweis.md) §6 führen „Disconnect während einer Anfrage erst beim Senden bemerkt“ mit Ausgang „weiter offen: → [slice-attention-cancel-timeout](../plan/planning/open/slice-attention-cancel-timeout.md)“. Dieser Slice schließt „Cancel beim Disconnect“ aus und nennt `slice-abbruch-ressourcen-shutdown`; seine Datei wurde in der Fix-Runde nicht geändert (`git show aeab8a9 --stat` führt sie nicht), die Commit-Nachricht („Träger in … slice-attention-cancel-timeout“) stimmt insoweit nicht. Der Träger, der den Disconnect in `RUNNING` und `STREAMING` als DoD-Punkt führt („Cancel höchstens einmal, Backend-Strom geschlossen, … keine Schreibversuche auf die tote Verbindung“), ist [slice-abbruch-ressourcen-shutdown](../plan/planning/open/slice-abbruch-ressourcen-shutdown.md) DoD-Punkt 1. Folge: der Verweis führt über einen Umweg (Dispatch → Attention → Shutdown), und die Trägerzusage des ersten Hops trägt nicht. Das Risiko selbst ist heute nicht erreichbar (kein Backend im Betrieb). | Plan §6, [TDS-FA-07](../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe) | ja (`grep`, Abschnitt 4 F-9) |
| V-2 | LOW | **SPEC-001 trägt zwei Sätze im Indikativ über Ungebautes.** „Bei gleichzeitigem DONE und ATTENTION gewinnt der erste Übergang“ steht im ersten Absatz, nach dem Satz „Der Transport meldet ein erkanntes ATTENTION-Paket an den Sitzungsrahmen“; erst der folgende Absatz sagt, dass ATTENTION in `RUNNING`/`STREAMING` als unzulässiger Übergang schließt und die Schleife während einer Anfrage nicht liest. Die Wettlauf-Regel hat heute keinen Träger im Code (kein gleichzeitiges Lesen). Ein Leser des ersten Absatzes allein liest gebaut, was Zielbild ist (AGENTS.md §3.8). Die Zielbild-Kennzeichnung des Absatzes „ATTENTION in jedem Anfragezustand ist offen“ ist sauber; die beiden Sätze stehen davor. | AGENTS.md §3.8 | nein (Spec-Lesung) |
| V-3 | INFO | **K5 überlebt.** Ruft `BufferedResponse.accept` `onResultStart()` vor dem Schreiben des COLMETADATA statt danach auf, bleibt die Suite grün. Der KDoc-Satz des Tests „Die Senke … lehnt eines ab, das er nicht schreiben kann (ROW ohne vorheriges COLMETADATA); ein abgelehntes Token … löst den Beginn des Ergebnisses nicht aus“ ist nur für ein ROW belegt, das ohnehin kein COLMETADATA ist; ein ablehnbares COLMETADATA ist nicht gebaut (der Sequenzierer erzeugt keines, das der Encoder ablehnt). Der Mutant ist deshalb praktisch unerreichbar und kein Mangel der DoD; wer ihn beheben will, formuliert den Satz enger oder kürzt die Reihenfolge in der Implementierung an einem Test, der ein ablehnbares COLMETADATA baut. | AGENTS.md §3.8 | ja (K5 gefahren) |
| V-4 | INFO | **Befund aus dem Review zur Wiedervorlage, kein Mangel dieses Slice:** [ADR-0004](../plan/adr/0004-session-zustandsautomat.md) führt den Zuschnitt (flach oder getrennt) weiter als offen, SPEC-001 legt ihn flach fest. Bis der Architect die ADR schließt oder eine Folge-ADR schreibt, stehen beide Aussagen nebeneinander (F-8 des Reviews, dort offen gelassen). | [ADR-0004](../plan/adr/0004-session-zustandsautomat.md) | nein |

## 8. Plan-vs-Code-Diff

| Plan (§3) | Code | Urteil |
|---|---|---|
| `protocol/dispatch` neu: Nachricht lesen, `QueryRequest` bilden, Port rufen, Ereignisse an den Sequenzierer | `RequestLoop`, `BufferedResponse`; importieren Domain, Port, Codec-Typen und `protocol`-Schichten, nichts aus `transport.*` außer `TdsMessage` und `PacketType` (Transport-Schnittstellen, nach ARC-Tabelle zulässig), `simulation`, `runtime` | konform |
| `protocol/session` update: Anschluss an `READY`, Anfragezustand | `ConnectionState` (+ `RUNNING`, `STREAMING`), `HandshakeAutomaton` (`forward`, `requestFinished`), `HandshakeEvent` (`StreamStarted`, `RequestFinished`), `HandshakeMatrix`, `HandshakeSession` (Sperre), `HandshakeOutcome.Ready.live` | konform; nicht in der Plan-Liste einzeln benannt, die erlaubt die Erweiterung („erweitert die Liste in seinem ersten Lauf“) |
| `SPEC-021`, nur falls nötig | nicht berührt | konform; der Dispatch reicht den Text unverändert |
| `src/test/…/dispatch` neu, `@spec` je Test | `SqlBatchDispatchTest`, `DispatchFailureTest`, `DispatchClosingTest`, `DispatchSupport` | konform |
| Test-Backend ist Fixture, kein Simulations-Backend | `ScriptedBackend.inSequence` (Erweiterung der Fixture), kein Produktivcode | konform |
| (nicht im Plan) Wiring | `ConnectionWiring`: optionales Backend am `HandshakeConnectionHandler`, `Main` setzt keines | zulässig; die DoD verlangt den Anschluss an die Sitzung, der Betrieb bleibt unverändert |
| (nicht im Plan) Spec-Änderung | SPEC-001, ARC-004, ARC-015 | zulässig (Doku-Update bei öffentlichem Vertrag); Gate-Regeln der Spec gehalten |

## 9. Rückwirkung (Rückwärtspfad)

- **Spec-Lücke (benennen, nicht schließen):** ATTENTION in jedem Anfragezustand und in `IDLE` ist offen; die Spec führt sie als Zielbild und Festlegung. Träger: `slice-attention-cancel-timeout`.
- **Lerneintrag-Kandidat für die Closure:** Eine Test-Naht im Produktivcode (`sinkAround`) entsteht, wenn ein Ausgang nur über ein Token erreichbar ist, das der Sequenzierer nie erzeugt. Die Regel „Ausgang ohne erreichbares Ereignis → Einhängepunkt statt Mock der Klasse“ ist ein Kandidat; ob sie die 3×-Schwelle erreicht, entscheidet das Register, nicht dieser Bericht.
- **Commit-Nachricht gegen Diff (V-1):** Aussagen in einer Commit-Nachricht über geänderte Dateien sind für den Verifier eine Behauptung; die Prüfung gegen `git show --stat` hat den Unterschied sichtbar gemacht.

## 10. Urteil im Satz

DoD-Punkte 1 bis 3 und der Zusatzpunkt sind bestätigt; Review F-1, F-2, F-4, F-5, F-6, F-7 behoben, F-3 und F-8 ehrlich offen mit Träger, F-9 offen mit falschem Träger (V-1). Vor dem Schließen: V-1 korrigieren (Träger), V-2 abwägen, Closure-Notiz mit Zuordnung *Test | Festlegung* schreiben. Nichts davon betrifft Code.

## Nachtrag: docs-check

`make docs-check` mit diesem Bericht: d-check 356 Dateien, 0 Befunde (der erste Lauf fand zwei unverlinkte Kennungen im Bericht; behoben und neu gefahren).
