# Verifikationsbericht: slice-fehler-zustandsuebergang (Umsetzung c5475ca, Fix-Runde af212cb) am Stand 1194042 — 2026-10-30

**Rolle:** Verifier (Frage: „Bauen wir es richtig?“ gegen Plan, DoD, Spezifikation und Entscheidungen). Der Bericht repariert nichts und ändert keine Datei außer sich selbst.

**Gegenstand:** [slice-fehler-zustandsuebergang](../plan/planning/done/slice-fehler-zustandsuebergang.md) (Welle welle-fehler-abbruch), Bezug [TDS-FA-06](../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände), [TDS-QA-07](../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität), [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität), [ADR-0004](../plan/adr/0004-session-zustandsautomat.md), [ADR-0007](../plan/adr/0007-fehler-und-attention.md). Berührte Spezifikation: [SPEC-001](../../spec/spezifikation.md#spec-001--zustandsautomat-einer-verbindung), [SPEC-011](../../spec/spezifikation.md#4-fehler-codes-und-logging-felder) bis [SPEC-016](../../spec/spezifikation.md#4-fehler-codes-und-logging-felder), [SPEC-029](../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung), [SPEC-031](../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität), [SPEC-048](../../spec/spezifikation.md#4-fehler-codes-und-logging-felder). Geprüft ist das Review [2026-10-30-ee0a6c7-slice-fehler-zustandsuebergang.md](2026-10-30-ee0a6c7-slice-fehler-zustandsuebergang.md) (F-1 bis F-9) gegen die Fix-Runde.

**Stand-Hinweis:** `main` steht auf `45a939b`; `git diff 1194042 HEAD` betrifft nur eine Datei unter `docs/reviews/`. `git diff c5475ca~1 HEAD --stat` nennt genau drei Dateien: `DispatchFateTest.kt`, den Slice-Plan und den Review-Report. `src/main` ist unverändert (Befehl ausgeführt), alle Läufe betreffen denselben Produktivcode.

**Nummerierung:** Der Auftrag zählt „DoD 1“ für den Rohbytes-Test und „DoD 3“ für die vier Zustandspunkte. Im Plan stehen die Liefer-Punkte in der Reihenfolge A (Anschluss des Mappings), B (Rohbytes-Test), C (vier Zustandspunkte). Dieser Bericht nennt sie A, B, C.

**Behauptung ≠ Bestätigung:** Der Plan trägt kein einziges DoD-Häkchen und keine Closure-Notiz (§7 „Wird bei der Closure gefüllt“). Belege des Implementers habe ich nicht übernommen; die Mutanten sind selbst nachgebaut (Abschnitt 2), die Tests selbst gelaufen (Abschnitt 1).

---

## 1. Selbst gefahrene Läufe

| Lauf | Ergebnis |
|---|---|
| Tests des Pakets `protocol.dispatch` (Kopie des Repo-Stands im Scratchpad, Dockerfile-Kopie mit Testfilter, `--no-cache-filter test`, nichts gemountet, ein Build zur Zeit), Grundlinie | grün, `BUILD SUCCESSFUL in 38s`, alle vier Tests von `DispatchFateTest` `PASSED` |
| Volllauf `docker build --target test --no-cache-filter test` im Repo | grün, `BUILD SUCCESSFUL in 56s`, 507 Tests `PASSED`, kein Test rot (die eine „FAILED“-Zeile im Log ist der Testname `Eine schliessende Abbildung ergibt FAILED_CLOSE_CONNECTION`, Status `PASSED`) |
| `make gates` | Exit 0 in 10 s; **Hinweis:** die Stage `test` kam aus dem Cache (`CACHED`), das Ergebnis ist das eines früheren Laufs derselben Quellen und für sich kein frischer Beleg; den frischen Beleg liefert der Volllauf darüber. `baseline-verify` v6.18.1 ok, d-check 366 Dateien, 0 Befunde, detekt-Stage grün |
| `make docs-check` (mit diesem Bericht) | Ergebnis im Nachtrag am Ende |

Wiederholungsserien habe ich wegen der Last des Rechners (Load 5 bis 9) nicht gefahren; die Tests des Slice sind deterministisch (kein Takt, keine Threads außer `blocking`), ein Flake-Befund liegt weder im Review noch in meinen Läufen vor, ist aber über zwei bis vier Läufe hinaus nicht bewiesen.

## 2. Selbst gemutete Fehler (einzeln, Test-Stage mit Filter auf `protocol.dispatch`; Quelltext nach jedem Lauf aus der Originalkopie zurückgesetzt)

| Kürzel | Mutation (Datei, Eingriff) | Ergebnis |
|---|---|---|
| M1 | `RequestLoop`: ein `BufferedResponse` als Feld der Schleife statt je Anfrage | rot: `DispatchFateTest` (Class 16, Folge offener Fehler), `SqlBatchDispatchTest` (zwei Anfragen in Folge) |
| M2 | `ErrorMapper.fateOf`: `FAILED_CLOSE_CONNECTION` gibt `OPEN` | rot: `DispatchFateTest` (Class 20), `DispatchFailureTest` (schließender Ausgang), `DispatchClosingTest` (Ausnahme) |
| M3 | `ResponseRun.report` gibt `false` zurück (der Sequenzierer liest nach jedem Fehler weiter) | rot, **nur** `DispatchFateTest`: alle drei Fehler-Tests (Class 16, Folge offener Fehler, Class 20) |
| M3c | `ResponseRun.report` gibt `mapped.closesConnection` zurück (weiterlesen nur bei Class 16) | rot, **nur** `DispatchFateTest` (Class 16, Folge offener Fehler). Im Review überlebte M3c (F-1); die Fix-Runde tötet ihn |
| M4 | `ResponseRun.write` gibt nach Ablehnung `true` zurück (der Sequenzierer bietet weitere Tokens an) | rot, **nur** `DispatchFateTest` (nicht abschließbarer Strom) |
| M6 | `ResponseRun.report`: bei schließendem Fehler ein zweites DONE nach dem DONE mit Fehlerstatus | rot, **nur** `DispatchFateTest` (Class 20): erwartet `[ColMetadata, Row, Row, Done, Error, Done]`, war `[…, Done, Done]` |
| Mstate | `HandshakeAutomaton.requestFinished`: nach offener Folge `STREAMING` statt `READY` | rot: `DispatchFateTest` (Class 16: erwartet `[READY, READY, READY]`, war `[READY]`; Folge offener Fehler), `DispatchFailureTest`, `SqlBatchDispatchTest` (zwei Tests) |
| Mviol | `ResponseRun.violation` bildet eine Meldung mit `closesConnection = false` (Reihenfolgeverstoß lässt die Verbindung offen) | rot: `DispatchFateTest` (Class 20, Pfad Reihenfolgeverstoß), `DispatchFailureTest` (schließender Ausgang, „Zeile ohne Ergebnis“). **Der Fall in `DispatchFateTest` fällt nicht an einer Zusicherung, sondern an der 20-Sekunden-Zeitüberschreitung von `blocking`:** die Schleife liest die Nachricht „never“ und wartet dann am erschöpften Drehbuch (`TimeoutCancellationException` in `FakeHandshakeChannel.receive`). Der Mutant ist erkannt, die Meldung sagt es nicht |

Nicht gefahren (Last): M5, M7, M8 des Reviews; ihre Aussage tragen die Läufe des Reviewers, nicht meine. Meine Kürzel sind eigene Definitionen nach der Beschreibung im Auftrag; die Definitionen des Reviewers lagen mir nicht vor.

## 3. Review F-1 bis F-9: behoben oder ehrlich offen?

| ID | Klasse | Urteil | Beleg |
|---|---|---|---|
| F-1 | MEDIUM | **behoben, selbst gemessen** | `behindError` (ResultStart, Row 99, ResultEnd) steht hinter jedem Fehler in Test 1 (alle drei Variationen), Test 2 (alle Fehler) und Test 3; `fromError().size == 2` (nur ERROR und sein DONE). M3c ist rot, im Review war er grün. Test 2 trägt zusätzlich die Liste `[2, 0, 2, 2, 0]` der Restlängen je Antwort |
| F-2 | LOW | **behoben, selbst gemessen** | Die Zusicherung prüft `[READY, READY, READY]` über alle drei Leseaufrufe und benennt `[1]` als den vor der Folgeanfrage. Die Reihenfolge stimmt mit `FakeHandshakeChannel.receive` überein (der Haken `onReceive` läuft am Anfang jedes `receive`, nach dem Login gesetzt, Index 0 = erste Anfrage). Mstate liefert `[READY]` und ist an dieser Zusicherung rot |
| F-3 | LOW | **behoben** | Der zweite Weg in die Class 20 ist der Reihenfolgeverstoß (`Row` ohne offenes Ergebnis nach `ResultEnd`); beide Wege ergeben dieselbe Tokenfolge, Fehlerzahl, Nummer 50007/Class 20, `CLOSED`, einen Leseaufruf. Der KDoc sagt ehrlich, dass `TDS_LOGIN_FAILED` denselben Weg wie der fremde Code läuft und kein eigener Fall ist. Die Zusicherung „Variation des Eingabewegs“ ist durch Mviol belegt (zweiter Pfad rot) |
| F-4 | LOW | **behoben** | Klassenkopf nennt „Voreinstellung“; der Fall heißt „anderer Code (Voreinstellung)“; der Plan §6 führt `TDS_SIM_INVALID_VALUE` als Voreinstellung, nicht entschieden. Stimmt mit der Herkunftsspalte der Tabelle in [§4 der Spezifikation](../../spec/spezifikation.md#4-fehler-codes-und-logging-felder) überein |
| F-5 | LOW | **behoben** | Plan §3: `protocol/session/` und Anfragezustand stehen auf „entfällt“ mit Verweis auf den Ist-Zustand, die Spezifikation „entfällt (bis zur Entscheidung)“, die Testzeile benennt `protocol/dispatch/DispatchFateTest.kt` mit [TDS-FA-06](../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände) und [TDS-QA-07](../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität). Der Diff `src/main` ist leer, die Tabelle stimmt mit dem Code überein |
| F-6 | LOW | **behoben mit Rest, siehe V-3** | Beide Punkte tragen einen Ausgang: Punkt 1 „entfallen“ (richtig: `RequestLoop` liest `ErrorMapper.fateOf`, `slice-sql-batch-dispatch` liegt in `done/`), Punkt 2 „weiter offen“. Rest: sechs weitere Punkte stehen auf „offen bis zur Closure“, das ist keiner der drei zulässigen Ausgänge (V-3) |
| F-7 | INFO | bestätigt | M3, M3c, M4, M6 fallen nur an `DispatchFateTest`; die Datei ist keine Doppelung |
| F-8 | INFO | siehe Abschnitt 6 | Der Slice hat keinen Produktivcode; Einordnung und Rückfallkante dort |
| F-9 | INFO | bestätigt | `TDS_SHUTDOWN` ist über `Failure` nicht erreichbar (jeder Code des Servers wird `TDS_INTERNAL_ERROR`); `TDS_UNSUPPORTED_TYPE`, `TDS_TIMEOUT` decken `DispatchFailureTest` ab; `TDS_SIM_UNKNOWN_OBJECT` und `TDS_UNSUPPORTED_RPC` stehen nur auf Mapper-Ebene in `ErrorMapperTest` (V-4) |

## 4. DoD Punkt für Punkt

### A — Anschluss des Mappings an den Sitzungsrahmen unter dem Mutex; ein Test je Folge der Mapping-Tabelle

**Urteil: als Beleg erfüllt, im Wortlaut „Anschluss“ nicht von diesem Slice geliefert.** Der Anschluss steht in `RequestLoop` (liest `ErrorMapper.fateOf`, sendet mit `RequestFinished` unter der Sperre des Rahmens, `READY` bei `OPEN`, sonst `CLOSED`) und stammt aus `slice-sql-batch-dispatch`; dieser Slice ändert ihn nicht (Diff leer) und sagt das in §3 ehrlich. Die drei Verbindungsfolgen der Tabelle sind je Fall belegt:

| Folge | Fall | Test |
|---|---|---|
| offen (Class ≤ 19) | vor der ersten Zeile, nach Teilzeilen, anderer Code | `DispatchFateTest` Test 1 und 2, `DispatchFailureTest` |
| schließt nach ERROR und DONE (Class ≥ 20) | Failure mit fremdem Code, Reihenfolgeverstoß, Ausnahme, Typfehler, `TDS_TIMEOUT`, `TDS_PROTOCOL_ERROR` | `DispatchFateTest` Test 3, `DispatchFailureTest` (schließender Ausgang, ungültige Kodierung), `DispatchClosingTest` (Ausnahme) |
| geschlossen ohne ERROR und DONE | Senke lehnt das k-te Token ab, k = 1 bis 5, kein zweiter Resultset-Anfang | `DispatchFateTest` Test 4 (M4 rot), `DispatchClosingTest` |
| unter der Sperre je Sitzung | Peer-Ende während des Schreibens wird nach dem Ende des Schreibens ausgeführt | `DispatchFailureTest` „Die Antwort wird unter der Sperre der Sitzung geschrieben“ (Bestand, nicht in diesem Slice neu, von mir nicht gemutet) |

Nicht über die Verbindung belegt, je Code der Tabelle: `TDS_UNSUPPORTED_RPC`, `TDS_SIM_UNKNOWN_OBJECT`, `TDS_SHUTDOWN` (V-4). „Ein Test je Folge“ ist erfüllt, „ein Test je Code“ stand nicht in der DoD.

### B — Rohbytes-Test über eine Verbindung ([TDS-QA-07](../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität))

**Urteil: erfüllt auf Nachrichtenebene, mit benannter Grenze.**

- **Nach Class ≤ 19 nur die eigene Antwort:** Test 1 vergleicht die Antwort der Folgeanfrage Byte für Byte mit dem Handvektor `COLMETADATA_N`, `row(7)`, `DONE_COUNT_1` (`assertEquals(oneRowReply, session.hex(1))`), null ERROR-Token darin, und die Fehlerantwort davor hat genau ein ERROR und ein DONE mit Fehlerstatus; Test 2 fährt fünf Anfragen in Folge (Fehler, Erfolg, Fehler, Fehler, Erfolg) mit Fehlerzahl `[1, 0, 1, 1, 0]`. M1 (geteilter Puffer) und M3/M3c (Weiterlesen hinter dem Fehler) sind rot.
- **Nach schließender Folge keine weitere Anfrage, kein zweites DONE:** Test 3 prüft die exakte Tokenfolge `[ColMetadata, Row, Row, Done, Error, Done]`, die Nachricht „never“ bleibt ungelesen (`receiveCalls - 2 == 1`, `exhausted` falsch), Backend einmal aufgerufen, Kanal und Backend geschlossen, `CLOSED`; M2, M6 sind rot. Test 4 prüft dasselbe für die Folge ohne Antwort.
- **Grenze (LOW, V-5):** „Rohbytes“ heißt hier: die Nutzlast der TDS-Nachricht (`TdsMessage`, Handvektor-Hex in beide Richtungen) über einen `FakeHandshakeChannel`. Paketierung, Socket und `ConnectionWiring` mit dem Fehlerpfad sind nicht beteiligt; der Plan schließt Gesamtsystem und Referenzclient ausdrücklich aus (§1 „Ausdrücklich NICHT“, Träger `slice-qa-synchronitaet` und `slice-qa-kompatibilitaet`). Die DoD sagt „Protokollebene mit Fake-Backend“; das ist getragen.

### C — Die vier Zustandspunkte: entschieden und getestet oder in der Closure als weiter offen mit Grund geführt

**Urteil: ehrlich als offen geführt (kein Punkt als entschieden gesetzt), Closure-fähig nur mit Träger (V-2).** Der Plan führt den Stand in §6 (zweiter Punkt) und setzt keinen der vier als entschieden; geprüft gegen den Code:

| Punkt | Aussage im Plan | Befund |
|---|---|---|
| Antwort auf unzulässigen Übergang | schließt ohne Antwort (Setzung des Spike, `HandshakeMatrix`) | stimmt mit dem Kopf von `HandshakeMatrix` überein; [ADR-0004](../plan/adr/0004-session-zustandsautomat.md) führt den Punkt als offen; **kein Test dieses Slice** |
| `RESETCONNECTION` | der Automat wertet es nicht aus | stimmt (`HandshakeEvent` „wertet der Automat nicht aus (offen …)“; im Quelltext nur der Codec trägt das Bit); Träger `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` besteht |
| Sitzungszustand nach Fehler | nicht angefasst; „Port kennt keine Transaktion“ als nicht belegt gekennzeichnet | ehrlich (§3.8): die Einschränkung steht im Plan; die Zustandsliste `[READY, READY, READY]` belegt nur den Anfragezustand |
| Kriterium „nicht abschließbar“ | die Senke nimmt ein Token nicht an (Festlegung des Sequenzierers) | stimmt mit dem KDoc des `ResponseSequencer` überein; Test 4 belegt genau dieses Kriterium und sonst nichts |

Die DoD-Zeile erlaubt ausdrücklich die zweite Hälfte („in der Closure als weiter offen mit Grund geführt“); die Grundangabe „Entscheidung des Nutzers fehlt“ ist vorhanden.

### D — Übrige Punkte (kein Liefer-Punkt)

| Punkt | Stand |
|---|---|
| `make gates` grün | getragen (Abschnitt 1; Cache-Hinweis beachten, frischer Volllauf grün) |
| Review durchgeführt, Report liegt vor | Report liegt vor, Fix-Runde behoben (Abschnitt 3); das Häkchen im Plan steht offen |
| Doku-Update | Plan §3/§6 nachgezogen; keine Spec-Änderung, der Plan begründet sie bis zur Entscheidung |
| Closure-Notiz, Beobachtungs-Register, Risiko-Ausgänge, drei Paarungen | **offen**, wie es vor der Closure sein soll (V-3) |

## 5. Findings des Verifiers

| ID | Kategorie | Befund | Quelle | Verifizierbar |
|---|---|---|---|---|
| V-1 | LOW | **Mutant Mviol fällt an der Zeitüberschreitung, nicht an einer Zusicherung.** In `DispatchFateTest` Test 3 steht hinter dem Fehler zwar eine ungelesene Nachricht („never“), aber `Session.login(…, never, never)` liefert sie dem Backend, wenn die Schleife fälschlich weiterliest; die Folge ist ein Warten am erschöpften Drehbuch bis `blocking` nach 20 s abbricht. Der Fehler wird erkannt, aber langsam und mit der Meldung `TimeoutCancellationException` statt der Aussage „Schleife las weiter“. Die Zusicherungen `receiveCalls - 2 == 1` und `exhausted` stehen hinter dem Aufruf von `serve` und werden nie erreicht | `AGENTS.md` §3.8 (Beleg trägt die Aussage nur im Erfolgsfall mit Meldung) | ja: Mviol, Abschnitt 2 |
| V-2 | MEDIUM | **Die Träger der drei offenen Zustandspunkte ohne eigenes Register enden mit diesem Slice.** [ADR-0004](../plan/adr/0004-session-zustandsautomat.md) und [ADR-0007](../plan/adr/0007-fehler-und-attention.md) nennen „die Slices der Welle welle-fehler-abbruch“ als Träger; dieser Slice ist der einzige in der Welle, der die Punkte „Sitzungszustand nach Fehler“, „Kriterium nicht abschließbar“ und „Antwort auf unzulässigen Übergang“ führt. [slice-attention-cancel-timeout](../plan/planning/open/slice-attention-cancel-timeout.md) setzt die Entscheidung zum unzulässigen Übergang nur um und entscheidet sie nicht; [slice-qa-synchronitaet](../plan/planning/open/slice-qa-synchronitaet.md) nennt die Punkte als offen und verweist auf die Slices der Welle zurück. Nur `RESETCONNECTION` hat einen Träger außerhalb ([BEO-TDS/tds-8-0-und-pooling-nicht-gemessen](../plan/planning/observations/BEO-TDS/tds-8-0-und-pooling-nicht-gemessen/observation.md)). Geht dieser Slice mit „weiter offen: Entscheidung des Nutzers fehlt“ nach `done/`, trägt keine lebende Datei die drei Punkte mehr (derselbe Schleifentyp wie V-1 in [2026-10-30-dcb6b27-verifikation-slice-qa-stabilitaet.md](2026-10-30-dcb6b27-verifikation-slice-qa-stabilitaet.md)) | [AGENTS.md](../../AGENTS.md) §3.8, Plan §6 | ja (Lesen der Pläne) |
| V-3 | MEDIUM | **Sechs Risiken in §6 stehen auf „offen bis zur Closure“, einer davon überholt.** Zulässige Ausgänge sind eingetreten, entfallen, weiter offen. Betroffen: Statement- gegen batch-abbrechende Fehler, Einzelzuordnung der Codes ohne Gruppe, „Wer baut den Response Sequencer (ARC-015)?“, Kriterium „nicht mehr sicher abschließbar“, Sitzungszustand nach Fehler, Mutex je Sitzung. Der dritte ist beantwortet: [slice-backend-sequenzierer](../plan/planning/done/welle-backend-streaming/slice-backend-sequenzierer.md) und [slice-sql-batch-dispatch](../plan/planning/done/slice-sql-batch-dispatch.md) liegen in `done/`, also „entfallen“. Das ist vor der Closure kein Mangel, aber die Closure darf nicht ohne Umstellung erfolgen | Plan §2, §6 | ja |
| V-4 | INFO | **Spec-Abweichung außerhalb dieses Slice:** [SPEC-013](../../spec/spezifikation.md#4-fehler-codes-und-logging-felder) verlangt für RPC ERROR und DONE mit Fehlerstatus (`TDS_UNSUPPORTED_RPC`, Class 16, offen); `RequestLoop` lässt RPC unbeantwortet und schließt (Festlegung im KDoc der Schleife, `DispatchFailureTest` „RPC, Transaction Manager und ATTENTION bleiben unbeantwortet“). Das ist Sache der Welle welle-anfrageverarbeitung ([slice-rpc-executesql-parameter](../plan/planning/done/slice-rpc-executesql-parameter.md)), kein Befund gegen diesen Slice; für die „Folge je Code“ ist `TDS_UNSUPPORTED_RPC` damit über die Verbindung unerreichbar | [SPEC-013](../../spec/spezifikation.md#4-fehler-codes-und-logging-felder) | ja (Lesen) |
| V-5 | INFO | Das Wort „Rohbytes“ in Klassenkopf und DoD meint die Nachrichten-Nutzlast hinter dem Kanal-Fake, nicht die Paketebene; der Plan grenzt das aus (§1), der KDoc sagt „über Rohbytes einer Sitzung“. Wer die Closure-Notiz schreibt, nenne die Ebene | `AGENTS.md` §3.8 | nein |
| V-6 | INFO | `make gates` meldet die Stage `test` aus dem Cache; als Beleg für eine Closure-Notiz zählt der Volllauf mit `--no-cache-filter test` (hier: 507 Tests grün) | `make gates` | ja |

## 6. Trägt der Slice seinen Lieferwert? Ist die Closure mit offenem Punkt C zulässig?

**Lieferwert: ja, als Beleg-Slice; der Produktivcode liefert er nicht.** Alle vier Tests prüfen Verhalten, das durch `slice-sql-batch-dispatch` bereits da ist. Der Wert ist die mutationsgeprüfte Absicherung der Folge nach Fehler an der Nahtstelle Mapping-Sequenzierer-Rahmen: vier Eingriffe (M3, M3c, M4, M6) fallen **nur** an dieser Datei, vier weitere (M1, M2, Mstate, Mviol) zusätzlich an Bestandstests. Ohne diesen Slice blieben das Weiterlesen hinter einem offenen Fehler, die Annahme von Tokens nach Ablehnung und ein zweites DONE ungeprüft. Der Plan benennt die Lage ehrlich (§3 „Geliefert wird deshalb der Beleg“). Eingeschränkt wird der Wert durch die Ebene (V-5: Nachrichtenebene, kein Transport) und durch den Wortlaut von DoD A, die einen „Anschluss“ nennt, den dieser Slice nicht baut; die Closure-Notiz sagt besser „Anschluss belegt, geliefert von `slice-sql-batch-dispatch`“.

**Closure mit offener DoD C: zulässig im Wortlaut, mit zwei Bedingungen, und die Rückfallkante des Plans fällt dabei nicht um.**

1. Die DoD C hat zwei Alternativen („entschieden und mit Test umgesetzt“ oder „in der Closure als weiter offen mit Grund geführt“); die zweite ist ausdrücklich zugelassen. Die Rückfallkante `in-progress` → `next` greift bei „mehr als drei Liefer-Punkte“; hier sind es genau drei, die Bedingung ist nicht eingetreten.
2. **Der Plan widerspricht sich an einer Stelle:** §4 nennt für `in-progress` → `open` (blockiert) unter anderem „die Nutzerentscheidungen zu den offenen Punkten fehlen“. Das ist der Zustand heute. Zwei Lesarten sind verträglich: Der Blocker gilt für die Spezifikations-Änderung (die der Plan ohnehin streicht, §3), nicht für den Beleg; dann gehört das in die Closure-Notiz in einem Satz („Rückführung nicht angewandt, weil der gelieferte Beleg von keiner Entscheidung abhängt“). Oder der Planner folgt der Rückführung und schneidet den Schnittvorschlag („die vier Zustandspunkte als eigener Slice“) vor der Closure.
3. **Bedingung für „weiter offen“:** ein Träger außerhalb dieses Slice für die drei Punkte ohne eigenen (V-2), sonst endet das Risiko mit der Datei. Der Schnittvorschlag aus §4 ist dafür der naheliegende Träger: ein eigener Entscheidungs-Slice oder je Punkt eine Beobachtung im Register. Die Frage, ob dieser Fall eine dritte Evidenz zu [BEO-TDS/dod-haengt-an-offenem-slice-ohne-rueckfuehrung](../plan/planning/observations/BEO-TDS/dod-haengt-an-offenem-slice-ohne-rueckfuehrung/observation.md) ist, beantwortet der Planner: dort hängt der DoD-Punkt an einem Slice in `open/`, hier an einer Nutzerentscheidung, und §4 nennt es hier vorab; die Gleichung ist nicht eindeutig, deshalb hier nicht gesetzt.

## 7. Plan-vs-Code-Diff

| Plan | Code | Urteil |
|---|---|---|
| §3: `protocol/session/` entfällt | `git diff` auf `src/main` leer | entspricht |
| §3: Anfragezustand (Response Sequencer) entfällt | `ResponseSequencer`/`ResponseRun` unverändert; Fehlerpfad im Bestand | entspricht |
| §3: `DispatchFateTest.kt` neu, Test je Folge, Folgeanfrage, kein zweites DONE | vier Tests: Class 16 mit Folgeanfrage, Folge offener Fehler, Class 20 mit exakter Tokenfolge, nicht abschließbarer Strom | entspricht |
| §3: Spezifikation entfällt bis zur Entscheidung | `spec/` im Diff nicht berührt | entspricht |
| §3: `DispatchFailureTest`/`DispatchClosingTest` tragen die Folge vor dem Fehler | vorhanden, M2, M4-nahe Fälle rot dort | entspricht |
| §6: „`DispatchFateTest` nutzt nur Setzungs-Codes für die schließende Folge“ | `NOPE` → `TDS_INTERNAL_ERROR` (Setzung), Reihenfolgeverstoß → `TDS_INTERNAL_ERROR` | entspricht |
| §6: „offene Folge `TDS_UNSUPPORTED_SQL`, `TDS_BACKEND_ERROR` (Setzung), `TDS_SIM_INVALID_VALUE` (Voreinstellung)“ | alle drei in Test 1; Herkunft stimmt mit §4 der Spezifikation | entspricht |
| §6 Punkt 1 „entfallen: `RequestLoop` liest `fateOf`“ | `RequestLoop.execute` ruft `errors.fateOf(sequencer.run(…))` | entspricht |

## 8. Rückwirkung (Rückwärtspfad)

- **Benannte Spec-Lücke / Träger:** die drei Zustandspunkte ohne eigenen Träger (V-2) gehören in die Closure-Notiz und in einen Slice oder Beobachtungen des Planners.
- **Steering-Loop-Kandidat (einmalig, keine Schwelle):** „Ein Mutant, der nur an der Zeitüberschreitung fällt, ist erkannt, aber nicht belegt“ (V-1): die Zusicherung `receiveCalls`/`exhausted` gehört vor das Warten, etwa über ein Drehbuch, das beim Weiterlesen eine Ausnahme wirft statt zu warten.
- **Closure-Pflichten (noch nicht erfüllt, kein Mangel):** Häkchen im Plan, Doku-Update (keines nötig), Closure-Notiz mit Lerneintrag (§7 leer), Beobachtungs-Register, Risiko-Ausgänge (V-3) und die drei Paarungen sind offen.

## 9. Urteil

**Fix-Runde:** F-1 bis F-5 sind behoben und selbst gemessen (M3c jetzt rot, Zustandsliste, zweiter Class-20-Pfad rot an Mviol, Plan-Tabelle stimmt mit dem Code überein); F-6 ist behoben mit Rest in V-3; F-7 bis F-9 bestätigt.

**DoD:** A als Beleg erfüllt (Wortlaut „Anschluss“ nicht von diesem Slice), B auf Nachrichtenebene erfüllt, C ehrlich als offen geführt und im Wortlaut der DoD closure-fähig. Die Slice-Datei ist nicht schließbar, bevor V-2 (Träger der drei Punkte) und V-3 (Ausgänge in §6) bearbeitet sind und §7 die Anwendung oder Nichtanwendung der Rückführung nach `open` nennt. Beides geht an den Planner.

**Übergabe:** Bericht an den Planner. Gate-Ergebnis für diesen Bericht: `make docs-check` nach Ablage, Ergebnis im Nachtrag unten.

## Nachtrag: docs-check

`make docs-check` mit diesem Bericht: 367 Dateien geprüft, 0 Befunde (Exit 0). Nicht committet.
