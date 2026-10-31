# Verifikation: [slice-antwort-mehrteilig-output](../plan/planning/done/slice-antwort-mehrteilig-output.md) nach Fix-Runde — 2026-10-31

**Rolle:** Verifier (Modul 11), frischer Kontext. Frage: Bauen wir es richtig? Geprüft wird gegen Plan, DoD ([§2 des Slice](../plan/planning/done/slice-antwort-mehrteilig-output.md)), [ADR-0005](../plan/adr/0005-rpc-und-prepared-statements.md), [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md), [ADR-0007](../plan/adr/0007-fehler-und-attention.md) und `AGENTS.md` §3.6 bis §3.8. Der Reviewer sieht den Diff, der Verifier die Zusage; gebaut oder repariert wird hier nichts.

**Stand:** Worktree-Kopf `d28604a` (Merge der Fix-Runde). Gegenstand: Umsetzung 609a846 und 5bb99b2, Fix-Runde 89aad43 gegen den [Review-Report](2026-10-31-5bb99b2-slice-antwort-mehrteilig-output.md) (F-1 bis F-9). Anforderungen: [TDS-FA-10](../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), [TDS-FA-10.a](../../spec/spezifikation.md#tds-fa-10a--mehrteilige-antworten-und-rpc-ausgabewerte), [SPEC-030](../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten), [SPEC-031](../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität).

## Ergebnis in einem Satz

Die DoD-Liefer-Punkte sind im Code und in den Tests getragen, der frische Testlauf und `make gates` sind grün, alle acht gefahrenen Mutationen wurden erkannt, und acht der neun Review-Befunde sind behoben; **F-1 ist nur teilweise behoben** — [SPEC-031](../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität) ist widerspruchsfrei, aber eine dritte Stelle der Spezifikation (Typklassen, Zeile 306) sagt weiter, `OutputParameter` bleibe bis zur Abbildung abgelehnt (MEDIUM, V-1). Befunde: **0 HIGH, 1 MEDIUM, 3 LOW, 4 INFO.**

## Sensor-Belege

| Sensor | Ausgang | Beleg |
|---|---|---|
| `docker build --no-cache-filter test --target test --progress=plain .` (frisch, Worktree bei `d28604a`) | Exit 0, `BUILD SUCCESSFUL in 1m 38s`; 735 Tests `PASSED`, keine `FAILED`; Task `:test` im Lauf ausgeführt (Zeile `#19 RUN gradle … test` nicht aus dem Cache); alle 6 Tests von `ResponseRpcTest` und alle 8 von `AnswerFormsDispatchTest` unter den bestandenen | Log `/tmp/claude-1000/-Development-tdswire/8562f454-84ba-456b-9970-945e69c035f4/scratchpad/fresh.log` |
| `make gates` (einmal, Worktree-Kopf) | Exit 0; die Test-Stage kam aus dem Layer-Cache des Frischlaufs darüber (gleiche Quellen), nicht aus einem zweiten Lauf | Log `/tmp/claude-1000/-Development-tdswire/8562f454-84ba-456b-9970-945e69c035f4/scratchpad/av-gates.log` |
| Mutationen M1 bis M8 (Kopien des Worktrees im Scratchpad, je ein frischer `docker build --target test`, der Worktree blieb unverändert) | 8 von 8 erkannt (Exit 1, Tests rot), Tabelle unten | `m-M1text.log` bis `m-M8open.log` im selben Scratchpad, Zusammenfassung `mut.out` |
| `make docs-check` | siehe Ende | — |

Die Ausgabe von `make gates` ist von einem Cache-Treffer der Test-Stage geprägt; die Frischebelege für Tests sind der `--no-cache-filter`-Lauf und die acht Mutationsläufe. Ein Docker-Build lief von mir zur Zeit; auf dem Host liefen zugleich fremde Builds (hohe Last), kein Lauf überschritt 10 Minuten.

## Mutationen (Wächter der Fix-Runde)

| ID | Mutation | Erkannt durch |
|---|---|---|
| M1 | F-4 Fall Text: `RpcOutputs.value` kürzt einen `String` auf 4000 Einheiten, bevor `ValueMapping.value` ihn sieht | nur `ResponseRpcTest` „Ein nicht abbildbarer Ausgabewert ist ein Fehler der Typabbildung“ |
| M2 | F-4 Fall Dezimalwert: `RpcOutputs.value` ersetzt jeden `BigDecimal` durch `BigDecimal.ONE` | nur derselbe Test |
| M3 | `statusSeen`-Prüfung in `RpcOutputs.status` entfernt (Endstand nach F-7) | `ResponseRpcTest` „RPC-Ereignisse in falscher Folge sind ein Reihenfolgeverstoss“ |
| M4 | `MORE` am letzten DONEINPROC: `flushHeld(more = false)` vor dem DONEPROC in `ResponseRun.end` | `AnswerFormsDispatchTest` „Ohne Ausgabewerte …“, `ResponseRpcTest` „RPC ohne Ausgabewerte …“, sieben Tests von `RpcDispatchTest` |
| M5 | Formprüfung Batch: Ablehnung von `ReturnStatus` und `OutputParameter` im Batch entfernt | neun Tests, darunter `ResponseRpcTest` „Im Batch bleiben …“ und `AnswerFormsDispatchTest` „Nicht unterstuetzte Formen …“ |
| M6 | Formprüfung RPC: Abschluss immer DONE statt DONEINPROC | zwölf Tests in `AnswerFormsDispatchTest`, `RpcDispatchTest`, `ResponseRpcTest` |
| M7 | Formprüfung RPC am Strom-Ende: DONE statt DONEPROC | elf Tests |
| M8 | Prüfung „kein `ReturnStatus` oder `OutputParameter` im offenen Ergebnis“ entfernt | `ResponseRpcTest` „RPC-Ereignisse in falscher Folge …“ |

Die beiden F-4-Fälle wurden **einzeln** gefahren und jeder für sich getötet; der Wächter ist je Fall wirksam. M3, M5 und M8 hängen jeweils an einem einzigen Test; das ist ausreichend (Wächter-Satz im KDoc nennt den Fall), aber ohne Redundanz.

## Fix-Runde: Stand der Befunde F-1 bis F-9

| ID | Erwartung | Befund der Verifikation |
|---|---|---|
| F-1 | [SPEC-031](../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität) widerspruchsfrei | **Teilweise.** Die Stelle im Absatz zu [SPEC-031](../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität) und die im Backend-Vertrag nennen jetzt „in der Antwort auf einen Batch“. Eine dritte Stelle, `spec/spezifikation.md` Zeile 305 f. („`OutputParameter` bleibt bis zur Abbildung abgelehnt (`SPEC-031`)“), ist unverändert und ohne die Ausnahme der Form RPC; siehe V-1. |
| F-2 | Gegenbeleg R02 genannt | **Behoben.** Spec und KDoc des Sequenzierers nennen die Messzeile R02 von [ADR-0007](../plan/adr/0007-fehler-und-attention.md) (Zeile dort: `SELEC 1` als Prozedurname gelesen, ERROR 2812, `DONEPROC[ERR]`, am echten Server) und lassen die Entscheidung bei der Fehlerabbildung (`welle-fehler-abbruch`). Die Setzung „DONE im Fehlerfall“ bleibt als Setzung ohne Messung gekennzeichnet; sie steht gegen die einzige Messzeile und wird von `ResponseRpcTest` „Ein Fehler nach einem Ergebnis …“ gepinnt, nicht belegt (INFO, V-6). |
| F-3 | Ordinal einheitlich als Setzung | **Behoben.** Spec („Ordinal ab 0 in der Reihenfolge der Ereignisse und nicht als Position in der Aufrufliste, Setzung ohne Messung“), KDoc des Sequenzierers und KDoc von `RpcOutputs.value` sagen dasselbe. |
| F-4 | zwei Negativfälle am Pfad `OutputParameter` | **Behoben**, durch Mutation M1 und M2 einzeln belegt. Der KDoc des Tests nennt weiter nur drei der fünf Fälle (V-7). |
| F-5 | Abdeckungsmatrix neu erzeugt | **Behoben.** `docs/plan/spec-abdeckung.md` führt keine Zeile `TDS-FA-10.a` mehr unter „Tags auf unbekannte Festlegungen“ (Suche über die Datei: kein Treffer); `SPEC-030` zeigt `AnswerFormsDispatchTest` und `ResponseRpcTest`. |
| F-6 | Abweichung vom Testserver in der Spec | **Behoben.** Die Spec-Setzung nennt „der Testserver des Spikes schrieb den letzten Abschluss ohne `MORE`, die Abweichung ist eine Setzung ohne Messung“; die Zeile in [ADR-0005](../plan/adr/0005-rpc-und-prepared-statements.md) („zwei Gruppen mit DONEINPROC `0x11` und `0x10`“) deckt die Aussage über den Testserver. Mutation M4 zeigt die Setzung gepinnt. |
| F-7 | `RpcOutputs.map` aufgeteilt, Verhalten unverändert | **Behoben.** `status` (liefert `null` nur noch für den zweiten `ReturnStatus`) und `value` (nie `null`) sind getrennt, `ResponseRun.rpcOutput` bekommt den Aufruf als Lambda; die Ablehnung im Batch und die Prüfung „offenes Ergebnis“ stehen vor dem Aufruf, also unverändert. Verhalten gleich: alle Tests des Vorstands grün, M3 und M8 erkannt. Der Zweig `null -> violation()` bleibt für `value` unerreichbar (INFO, V-8). |
| F-8 | offene Spec-Aussage | **Behoben.** Spec und KDoc: „ein Ergebnis nach einem Ausgabewert ist nicht ausgeschlossen, ob der Client diese Folge annimmt, ist offen“. Kein Test pinnt die Folge; das ist mit „offen“ ehrlich. |
| F-9 | Ordinal-Test mit verschiedenen Namen | **Behoben.** `AnswerFormsDispatchTest` „NULL im OUTPUT bleibt NULL und die Ordinale zaehlen“ nutzt `@o` und `@p`; der Name `@p` steht als `40 00 70 00` im Vektor. |

## DoD Punkt für Punkt

| DoD-Punkt | Getragen durch | Urteil |
|---|---|---|
| 1. Mehrere Ergebnisse in Reihenfolge: zwei Ergebnisse und Update-Zähler, `MORE` an jedem Abschluss außer dem letzten, `COUNT` nur bei Zahl, leeres Ergebnis mit Metadaten und Abschluss, Byte-Vektor je Fall | `AnswerFormsDispatchTest` „Zwei Ergebnisse und ein Update-Zaehler …“ (Batch: `FD 11 …` zweimal mit `MORE`, `FD 10 …` zuletzt) und „Ein leeres Ergebnis zwischen zwei Ergebnissen …“ (`FD 01 00 …` ohne `COUNT`); M4 und M6 zeigen die Bits wirksam | erfüllt |
| 2. RPC-Antwortform und OUTPUT: RETURNSTATUS, je OUTPUT ein RETURNVALUE (Name, Typ, Wert, Ordinal), DONEINPROC bzw. DONEPROC, NULL bleibt NULL, ohne OUTPUT kein RETURNVALUE | `AnswerFormsDispatchTest` „RPC mit OUTPUT-Parameter …“, „NULL im OUTPUT …“, „Ohne Ausgabewerte …“; `ResponseRpcTest` „RPC schreibt DONEINPROC …“ und „RPC ohne Ausgabewerte …“; `RpcDispatchTest` auf DONEINPROC und DONEPROC umgestellt | erfüllt; Ordinal und Statusbyte sind als Setzung gekennzeichnet |
| 3. Nicht unterstützte Antwortform: Zeile ohne `ResultStart`, doppeltes `ResultEnd`, `OutputParameter` im Batch, Typ außerhalb der Abbildung; definierter Fehler, keine Erfolgsantwort davor, kein vollständiger Erfolgsabschluss auf eine Teilantwort | `AnswerFormsDispatchTest` „Eine Zeile ohne ResultStart …“, „Auf eine Teilantwort folgt kein vollstaendiger Erfolgsabschluss“, „Nicht unterstuetzte Formen …“; `ResponseRpcTest` „Im Batch bleiben …“ und „Ein nicht abbildbarer Ausgabewert …“; „ERROR und Abschluss mit Fehlerstatus, solange der Strom synchron ist, sonst schließt der Server“ stammt aus dem Kern und seinen Tests, nicht aus diesem Slice (nicht neu gebaut) | erfüllt |
| Nachweis: `@spec`-Tag je Test | alle 14 Tests der beiden neuen Klassen tragen `@spec SPEC-<NNN>`; die Matrix führt sie | erfüllt |
| Nachweis: Aussage an einem Test oder als Festlegung gekennzeichnet; Zuordnung *Test \| Festlegung* in der Closure-Notiz | Kennzeichnung im KDoc und in der Spec vorhanden; die Zuordnungstabelle fehlt, weil §7 des Slice noch nicht gefüllt ist | Closure-Pflicht, noch nicht fällig |
| `make gates` grün | Exit 0 (siehe Sensor-Belege) | erfüllt |
| Review durchgeführt, Report liegt vor | [Review-Report](2026-10-31-5bb99b2-slice-antwort-mehrteilig-output.md) | erfüllt |
| Doku-Update, Closure-Notiz, Register, Risiko-Ausgänge, Paarungen | §7 steht auf „Wird bei der Closure gefüllt“, die §6-Risiken tragen „offen bis zur Closure“, die Häkchen der DoD sind nicht gesetzt | Closure-Pflicht, nicht Gegenstand dieser Verifikation (keine Closure) |

## ADR-Konformität

| Entscheidung | Prüfung | Urteil |
|---|---|---|
| [ADR-0005](../plan/adr/0005-rpc-und-prepared-statements.md) Antwortpflicht: RETURNSTATUS, RETURNVALUE (OUTPUT), DONEPROC bzw. DONEINPROC | der Sequenzierer schreibt in der Form RPC genau diese Tokens; DONEINPROC je Abschluss, DONEPROC zum Schluss; ohne Ereignis kein RETURNSTATUS und kein RETURNVALUE (so auch die `min`-Variante der Messung) | erfüllt |
| [ADR-0005](../plan/adr/0005-rpc-und-prepared-statements.md) Reihenfolge RETURNSTATUS, RETURNVALUE, DONEPROC | die Tests belegen die Folge, wenn das Backend `ReturnStatus` vor `OutputParameter` liefert; die Reihenfolge schreibt der Strom vor, der Sequenzierer erzwingt sie nicht, und kein Test liefert `OutputParameter` vor `ReturnStatus` (V-3) | erfüllt für den belegten Fall, Lücke benannt |
| [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md) Vertrag und Strom: Port unverändert, Ereignisse in Strom-Reihenfolge, `DONE_MORE` aus der Vorschau, Reihenfolgeverstoß als Fall von [SPEC-048](../../spec/spezifikation.md#4-fehler-codes-und-logging-felder) | `QueryEvent` und Port im Diff nicht berührt; der Reihenfolgeverstoß für `ReturnStatus` und `OutputParameter` schließt die Verbindung (`FAILED_CLOSE_CONNECTION`) | erfüllt |
| [ADR-0007](../plan/adr/0007-fehler-und-attention.md) Fehler: ERROR und DONE mit Fehlerstatus; Zuständigkeit der Fehlerabbildung bei `welle-fehler-abbruch` | die RPC-Antwort nutzt für den Fehlerfall dasselbe `failedDone()` wie der Batch; die Setzung ist als solche benannt und R02 als Gegenbeleg genannt | erfüllt, offene Frage an `welle-fehler-abbruch` benannt |
| `AGENTS.md` §3.5 (ADR immutable) | `docs/plan/adr/` im Diff 89aad43 und 609a846 nicht berührt | erfüllt |
| `AGENTS.md` §3.6 (keine Lockerung) | `config/`, `build.gradle.kts`, `Dockerfile`, `tools/` im Fix-Runden-Diff nicht berührt | erfüllt |
| `AGENTS.md` §3.7 (Kommentar beschreibt, was da ist) | Fix-Runden-KDoc im Indikativ, jede Aussage trägt Zusage, Grenze oder Abgrenzung | erfüllt; ein stehengelassener Satz, V-7 |
| `AGENTS.md` §3.8 (Aussage trägt so weit wie der Beleg) | Statusbyte 1, Ordinal, DONE-Familie, Tokenfolge als „Festlegung ohne Messung“ gekennzeichnet; eine externe Abschnittsnummer nennt der Diff nicht | erfüllt |

## Plan gegen Code

Der Plan (Ist-Zustand-Tabelle des Slice, §3) listet sieben Dateien. Der Diff 7b5b20e bis 5bb99b2 berührt elf: die sieben, dazu `spec/spezifikation.md`, `docs/plan/spec-abdeckung.md`, den Plan selbst (§3 verlangt das Spec-Update; Abdeckungsmatrix und Plan sind erwartbare Begleiter) und **`src/main/kotlin/io/github/pt9912/tdswire/protocol/response/RpcOutputs.kt`**, eine neue Datei (53 Zeilen), die in der Tabelle nicht steht (V-2). Das Gegenstück im Review-Report („alle sieben Dateien der Tabelle im Diff, keine darüber hinaus“) trifft die Quelltext-Datei nicht. Die Fix-Runde 89aad43 bleibt innerhalb dieser Dateien (sieben: Spec, Matrix, `ResponseRun`, `ResponseSequencer`, `RpcOutputs`, beide Test-Klassen); kein Befund gegen die Reihenfolge des Plans (Mehrfach-Ergebnisse, RPC-Form, Negativfall).

## Handvektoren (nur Arithmetik)

Die Byte-Vektoren in `AnswerFormsDispatchTest` und `RpcDispatchTest` habe ich von Hand gegen das Tabellenlayout von MS-TDS nachgezählt. **Das ist Arithmetik, keine Messung**: kein Referenzclient und kein echter SQL Server wurde befragt, und eine Abschnittsnummer des Standards nenne ich nicht, weil ich keine belegbare Quelle im Repo habe (`AGENTS.md` §3.8).

| Token | Layout | Vektor im Test | Zählung |
|---|---|---|---|
| DONE (`FD`), DONEPROC (`FE`), DONEINPROC (`FF`) | 1 Byte Kennung, 2 Byte Status, 2 Byte CurCmd, 8 Byte Zeilenzahl (TDS 7.2 und höher) | `FD 11 00 00 00` plus 8 Byte, `FE` plus 12 Nullbyte, `FF 11 00 00 00` plus 8 Byte | je 13 Byte; Status `0x0011` = `MORE` (0x01) plus `COUNT` (0x10), `0x0010` nur `COUNT`, `0x0001` nur `MORE` (leeres Ergebnis: `FD 01` und 11 weitere Nullbyte = 13) |
| RETURNSTATUS (`79`) | Kennung, 4 Byte Wert | `79 00 00 00 00` | 5 Byte |
| RETURNVALUE (`AC`) | Kennung, 2 Byte Ordinal, B_VARCHAR-Name (1 Byte Länge in Zeichen, dann UTF-16LE), 1 Byte Status, 4 Byte UserType, 2 Byte Flags, TYPE_INFO, Wert | `AC 00 00 02 40 00 6F 00 01 00 00 00 00 01 00 26 04 04 2A 00 00 00` | Ordinal `00 00`, Länge 2, `@o` = `40 00 6F 00`, Status `01`, UserType `00 00 00 00`, Flags `01 00` (nullbar), TYPE_INFO `26 04` (INTN, 4 Byte), Wert `04` plus `2A 00 00 00` (42); NULL: `00`; zweites Ordinal `01 00`, `@p` = `40 00 70 00` |

Die Zählung stimmt in allen betrachteten Vektoren. Offen bleibt, was nur eine Messung klärt: die Ordinal-Zählung, die Tokenfolge und die Statusbits eines echten SQL Servers (`BEO-TDS/messung-am-echten-sql-server-fehlt`, Träger `slice-qa-reproduzierbarkeit`).

## Findings

| ID | Kategorie | Befund | Quelle | Pfad |
|---|---|---|---|---|
| V-1 | MEDIUM | **F-1 nur teilweise behoben.** `spec/spezifikation.md` sagt in den Typklassen des Backends weiter: „Die Klassen gelten auch für `QueryParameter.value` und `OutputParameter.value` …; `OutputParameter` bleibt bis zur Abbildung abgelehnt (`SPEC-031`).“ Ohne die Ausnahme der Form RPC widerspricht das den beiden berichtigten Stellen („in der Antwort auf einen RPC werden sie abgebildet“); ein Leser dieser Stelle schließt, ein RPC mit OUTPUT-Parameter ergebe einen Fehler. Dieselbe Aussage steht in der angenommenen [ADR-0013](../plan/adr/0013-backend-grenzen.md) (Entscheidung 6, „Letzteres bleibt bis zur Abbildung abgelehnt“); die ADR ist unveränderlich, die Spezifikation rangiert über ihr (Fortschreibung als Folge-ADR oder Hinweis ist Sache des Architects, kein Eingriff hier). | [TDS-FA-10.a](../../spec/spezifikation.md#tds-fa-10a--mehrteilige-antworten-und-rpc-ausgabewerte), `AGENTS.md` §3.8 | `spec/spezifikation.md` · „`OutputParameter` bleibt bis zur Abbildung abgelehnt“; `docs/plan/adr/0013-backend-grenzen.md` Entscheidung 6 |
| V-2 | LOW | **Plan gegen Code:** `RpcOutputs.kt` (neu, `RpcOutputs` zählt `RETURNSTATUS` und Ordinale) steht nicht in der Datei-Tabelle des Plans; der Review-Report behauptet im Negativbefund das Gegenteil. Die Datei ist im Plan-Sinn eine Erweiterung der Zerlegung von `ResponseRun`; ein Verstoß gegen eine Entscheidung ist es nicht, aber die Tabelle ist die Zusage des Implementers vor dem Code. | Plan §3, `AGENTS.md` §6 | `docs/plan/planning/done/slice-antwort-mehrteilig-output.md` · Ist-Zustand-Tabelle |
| V-3 | LOW | Die Reihenfolge RETURNSTATUS, RETURNVALUE, DONEPROC aus [ADR-0005](../plan/adr/0005-rpc-und-prepared-statements.md) („Antwortpflicht“) wird von den Tests nur für `ReturnStatus` vor `OutputParameter` belegt. Liefert das Backend `OutputParameter` vor `ReturnStatus`, schreibt der Sequenzierer RETURNVALUE vor RETURNSTATUS (Spec: „die Tokens stehen in der Reihenfolge des Stroms“); weder Spec noch Test sagen, ob das gewollt ist. Der Client nahm die Folge RETURNSTATUS, RETURNVALUE, DONEPROC vom Testserver, für die andere Folge fehlt jede Aussage. | [ADR-0005](../plan/adr/0005-rpc-und-prepared-statements.md), `AGENTS.md` §3.8 | `ResponseRun.kt` · `rpcOutput`; Tests `ResponseRpcTest`, `AnswerFormsDispatchTest` |
| V-4 | LOW | Die Closure-Pflichten sind offen (erwartet vor der Closure, daher nicht blockierend, aber der Planner muss sie tragen): §7 leer, die §6-Risiken ohne Ausgang, die DoD-Häkchen nicht gesetzt, keine Zuordnung *Test \| Festlegung*, kein Beobachtungs-Register-Eintrag. | Plan §2, §5, §6 | `docs/plan/planning/done/slice-antwort-mehrteilig-output.md` |
| V-5 | INFO | Die Abdeckungsmatrix stammt aus dem Stand der Fix-Runde auf einem Zweig, der zugleich Sim-Tests führt; die Zeilen für `SPEC-030` und `SPEC-031` nennen `AnswerFormsDispatchTest` und `ResponseRpcTest`. Dass die eingecheckte Matrix auch den Stand von `d28604a` abbildet, habe ich nicht neu erzeugt (`make spec-abdeckung` nicht gefahren, ein Docker-Build gleichzeitig). | `AGENTS.md` §5 Regel 6 | `docs/plan/spec-abdeckung.md` |
| V-6 | INFO | Die Setzung „ERROR und Abschluss mit Fehlerstatus sind in der RPC-Antwort ein DONE“ steht gegen die einzige Messzeile (R02, `DONEPROC[ERR]`). Die Fix-Runde nennt den Gegenbeleg, das ist die verlangte Kennzeichnung; ob die Setzung hält, entscheidet `welle-fehler-abbruch`. | [ADR-0007](../plan/adr/0007-fehler-und-attention.md) | `ResponseSequencer.kt` · „dagegen zeigt die Messzeile R02“ |
| V-7 | INFO | Der KDoc von `ResponseRpcTest` „Ein nicht abbildbarer Ausgabewert …“ nennt weiter drei Fälle („Precision über 38, Klasse des Werts passt nicht zum Typ, Name über 255 Zeichen“); die Tabelle im Test hat fünf. Der Satz ist unvollständig, nicht falsch. Im KDoc von `RequestLoop` stehen zwei Zeilenumbrüche mitten im Satz (Folge der Umformulierung, ktlint grün). | `AGENTS.md` §3.7 | `ResponseRpcTest.kt`; `RequestLoop.kt` · Klassen-KDoc |
| V-8 | INFO | `ResponseRun.rpcOutput` behandelt `null` aus dem Lambda als Reihenfolgeverstoß; nur `status` liefert `null`. Für `value` ist der Zweig unerreichbar (Typ `ProtocolResult<Token>?` für beide Lambdas). Kein Verhaltensunterschied, ein kleiner toter Zweig. | Maintainability | `ResponseRun.kt` · `rpcOutput` |

**Gegenprobe, ohne Befund:** `spec/spezifikation.md` führt in den Fix-Runden-Zeilen keine `ADR-`-Kennung und keinen Pfad außerhalb der Spec (Schichtregel); Lastenheft und Architektur sind im Diff nicht berührt; die Tests laufen gegen `ScriptedBackend`, nicht gegen einen Server oder den Referenzclient (so die DoD).

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 1 |
| LOW | 3 |
| INFO | 4 |

## Verdikt

Konform mit der DoD und den Entscheidungen, mit einer offenen Korrektur: **V-1** (MEDIUM) gehört vor der Closure in `spec/spezifikation.md` berichtigt (ein Satz, die Ausnahme der Form RPC); die [ADR-0013](../plan/adr/0013-backend-grenzen.md)-Stelle ist Sache des Architects. V-2 bis V-4 nimmt der Planner in die Closure-Notiz. Keine Closure, kein Eingriff in Quelltext oder Spec durch den Verifier.

**Übergabe:** Bericht an den Planner (Befunde V-1 bis V-8); die Finding-Klasse „Fortschreibung an einer Stelle, dieselbe Aussage an einer zweiten stehen gelassen“ ist eine Wiederholung aus dem Review (F-1) und gehört in die Steering-Loop-Zählung der Closure.

**Eigener Hinweis zum Lauf:** Das Scratchpad ist von mehreren Läufen geteilt. Beim Anlegen meines Mutationsskripts habe ich eine vorhandene Datei `mut.sh` im Scratchpad überschrieben (Kopie meiner Fassung: `verifier-mut-run.sh`); an ihrer Stelle liegt ein Platzhalter, der mit Exit 1 endet. Der frühere Inhalt ist nicht wiederherstellbar.

**Ergebnis `make docs-check`** nach Anlage des Berichts: 440 Dateien geprüft, 0 Befunde.
