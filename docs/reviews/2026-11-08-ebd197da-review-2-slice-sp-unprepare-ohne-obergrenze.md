# Review-Bericht 2: slice-sp-unprepare-ohne-obergrenze (HEAD ebd197da, Nachbesserung nach dem Report zu 1645b7c2)

* Rolle: Reviewer (Modul 10), frischer Kontext, Folgelauf zu `2026-11-08-1645b7c2-review-slice-sp-unprepare-ohne-obergrenze.md` (Report-Stand: Commit `4a06a9de`). Gegenstand: die Commits nach diesem Report, `git diff 4a06a9de..HEAD`: `5ba270e9` (Quelltext und Tests: Antwort der Freigabe als Strom), `4ec0deca` (Spezifikation, Nutzer-Doku, Matrix, Messprotokoll, Plan-Tabelle), `ebd197da` (Abdeckungsmatrix) und der Merge `78a5266f` von `main` (Auflösungen nur in Planungsdokumenten, `git show --cc`: sieben Dateien unter `docs/plan/planning/`, kein Quelltext). Ein Re-Review liest nur diese Fläche, nicht den ganzen Slice noch einmal.
* Maßstab: Plan, DoD-Bezüge und Rückführungen des Slice; [TDS-FA-04](../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-FA-07](../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-QA-07](../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität); [ADR-0027](../plan/adr/0027-abbruch-und-zuruecksetzen.md), [ADR-0036](../plan/adr/0036-handle-freigabe-und-grenze-je-sitzung.md), [ADR-0037](../plan/adr/0037-sp-unprepare-unbekanntes-handle-ergibt-fehler-8179.md), [ADR-0038](../plan/adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md), [ADR-0039](../plan/adr/0039-abschluss-der-antwort-auf-eine-rpc-nachricht-die-der-parser-abweist.md) (alle `Accepted`, im Diff unverändert), `SPEC-007`, `SPEC-030`, `SPEC-031`, `SPEC-033`, `SPEC-060`, `AGENTS.md` §3.1 bis §3.8. Nicht Gegenstand: die DoD (Verifier).
* Selbst gefahren (Arbeitsbaum des Zweigs unverändert, `git status` sauber; Mutationen und Messläufe in Kopien unter dem Scratchpad, je `docker build --target` einer Kopie des Baums bei HEAD): `make gates FORCE=1` Exit 0 (41 s, Layer-Cache), `make clean-build` Exit 0 (1042 s, parallel liefen meine Mutationsbauten, Last 21 bis 38), `make flaeche` Exit 0, `make docs-check` Exit 0 (1237 Dateien, 0 Befunde), `make matrix-zitate` Exit 0 (1197 bestandene Testfälle, 305 Zitate, 0 ohne PASSED), `make e2e` Exit 0 (210 s, Last 35; kein Last-Flake in diesem Lauf, `ConnectionStormTest` blieb grün).

## Gesamturteil

0 HIGH, 4 MEDIUM, 2 LOW, 5 INFO. Die Umstellung auf Streaming trägt in der Sache: die Antwortbytes sind die des Puffer-Standes (Handvektoren in `ErrorMapperTest` für 1 und 2 Nummern unverändert), die Länge der vollständigen Antwort stimmt am Draht (47 381 930 Byte, DONE-Status am Ende, Paketgrenzen durch `MessageStream` auf Paketgröße), die Mutationen „Obergrenze 100“ und „Streaming zurück auf Puffer“ sind rot, Heap und Prozess halten bei zwei Verbindungen (Test) und, nach eigener Probe, bei drei Verbindungen, auch mit lauter verschiedenen siebenstelligen Nummern. Offen sind: die Lage des ENVCHANGE und der Abbruch mitten im Strom tragen keinen Test (F-1, F-2), ein ATTENTION während des Stroms wirkt erst nach der ganzen Antwort und steht nirgends (F-3), und der Messvermerk in `SPEC-060` lässt das schwerere Ergebnis bei vier Verbindungen aus (F-4). Die vier Findings des ersten Reports sind behoben (siehe „Stand der Findings des ersten Reports“).

## Mutationen (selbst gefahren)

Kopie des Baums bei HEAD, je eine Mutation, Stage `mut` (Kopie der Stage `test`, begrenzt auf `UnprepareDispatchTest`, `UnprepareLastProcessTest`, `ErrorMapperTest`, `RequestWiringTest`, `UnprepareBatchTest`; 55 Tests). Die übrigen Testdateien, die `sp_unprepare` nennen (`ZustandsereignisseTest`, `ZustandsereignisseProzessTest`, `SpanOutcomesDispatchTest`, `RequestSequenceTest`, `RpcDispatchTest`, `RpcBinderTest`, `RpcCatalogFormsTest`, `PreparedHandlesDispatchTest`), sind in diesen Läufen nicht gelaufen; per Grep enthält keine davon `RESETCONNECTION` oder einen fehlschlagenden Kanal.

| Nr. | Mutation | Wirkung gemessen |
|---|---|---|
| m1 | `UnprepareBatch.release`: `.take(100)` wieder eingeführt | rot: `UnprepareDispatchTest > Jede unbekannte Nummer ergibt einen ERROR, das Log einen Eintrag je Batch` (Meldung „101 Nummern“) und `UnprepareLastProcessTest > Zwei gleichzeitige Batches …` (Länge der Antwort); 2 von 55 |
| m2 | Streaming zurück auf Puffer: `answer` sammelt in einem `TokenEncoder` und schreibt mit einem `reply.write(encoder.toByteArray())` | rot nur im Prozesstest (`UnprepareLastProcessTest`, Zeile „Verbindung … ohne Antwort“); alle Dispatch-Tests grün. Der Prozesstest ist damit der einzige Wächter gegen die Pufferung (der Heap-Test im Dispatch-Test ist mit `5ba270e9` entfernt) |
| m3 | ENVCHANGE hinter alle Tokens (nach dem DONE) | rot: `UnprepareDispatchTest > Ein unbekanntes Handle ergibt 8179 mit State 8 bei offener Verbindung` (über `lastDone()`); 1 von 55 |
| m4 | ENVCHANGE weggelassen (`val sink = response`) | rot: derselbe Test (Zählung 3 Tokens); 1 von 55 |
| m5 | `if (lead != null) resetPending = false` in `RequestLoop` gestrichen | grün (siehe F-5: `withResetFirst` ändert eine Antwort ohne `reply` nicht) |
| m6 | `answer`: bei Schreibfehler `connectionOpen = true` statt `false` | grün (F-2) |
| m7 | `unknownHandles`: Rückgabe von `sink.accept` für die ERROR-Tokens ignoriert | grün (F-2) |
| m8 | ENVCHANGE nach dem ersten ERROR statt vor ihm (Reihenfolge ENVCHANGE nicht führend) | grün (F-1) |
| m9 | `&& unknown.isNotEmpty()` aus der Bedingung des ENVCHANGE gestrichen | grün; äquivalent (F-5) |

## Messläufe (eigene Probe-Tests in Kopien; nicht im Repo, Einzelmessungen unter Last 20 bis 35)

* Zwei Verbindungen, je 419 309 Anweisungen `EXEC sp_unprepare 1;` (der Test des Repos, als Probe mit Zeit): 2,97 s bis zur vollständigen Antwort beider (47 381 930 Byte je Verbindung), Prozess lebt. Der Test selbst dauert damit Sekunden; die rund 17 Minuten stammen vom ganzen `make clean-build` (1042 s) und nicht von diesem Test.
* Eine bis drei Verbindungen mit 322 000 **verschiedenen** siebenstelligen Nummern: je vollständige Antwort (40 318 263 Byte), 2,6 bis 2,8 s, Prozess lebt; vier Verbindungen: alle ohne Antwort geschlossen nach 0,55 s, der Prozess endet („Terminating due to java.lang.OutOfMemoryError: Java heap space“, `-XX:+ExitOnOutOfMemoryError`).
* Vier Verbindungen, lauter `1`, mit `ExitOnOutOfMemoryError`: ohne Antwort, Prozess endet nach 0,5 s (wie oben). **Ohne** die Option: drei Verbindungen erhalten die vollständige Antwort, eine endet mit `connection_failed … OutOfMemoryError`, `reason HANDLER_FAILED` (keine `TDS_INTERNAL_ERROR`-Antwort), der Prozess lebt.
* ATTENTION hinter dem Batch (eine Verbindung): die vollständige Antwort (47 381 930 Byte) geht zu Ende (2,5 s), danach folgt die Bestätigung (`FD 20 00 FD 00 00 00 00 00 00 00 00 00`, DONE mit ATTENTION-Status), die nächste Anfrage wird beantwortet (126 Byte). Protokollsynchron, aber nicht abgebrochen (F-3).

## Findings

### F-1 MEDIUM — Lage des ENVCHANGE in der gestreamten Freigabe-Antwort trägt kein Test

* kategorie: MEDIUM
* quelle: `AGENTS.md` §3.8; Reviewer-Skill „Eigencode, dessen Entfernung keinen Test rot macht“; [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität) (ENVCHANGE Typ 18 als erstes Token, Messreihe W-7 des Referenzabgleichs)
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/UnprepareBatch.kt` · „val sink = if (lead != null) LeadingToken(response, lead) else response“; `src/test/kotlin/io/github/pt9912/tdswire/protocol/dispatch/UnprepareDispatchTest.kt` · „Die Antwort auf die Anfrage mit RESETCONNECTION (5) beginnt mit ENVCHANGE Typ 18.“
* befund: Der Test prüft für die Antwort mit `RESETCONNECTION` nur die Zahl der Tokens (3) und den Status des letzten DONE; der Kommentar behauptet „beginnt mit ENVCHANGE“, kein Aufruf prüft Typ oder Platz des ersten Tokens. Mutation m8 (ENVCHANGE zwischen ERROR und DONE) bleibt grün; nur die Lage hinter dem DONE (m3) und das Fehlen (m4) werden rot. Bis `5ba270e9` hat die Voranstellung der Bytes (`withResetFirst`) den Platz konstruktiv gesichert, jetzt hängt er an `LeadingToken` und am Aufruf in `answer`. Failure-Szenario: eine spätere Änderung am Aufbau der Senke setzt das ENVCHANGE hinter das erste ERROR; ein Pool-Client, der das Zurücksetzen am ersten Token erkennt (W-7), liest die Antwort anders, und die Tests bleiben grün.
* verifizierbar: ja (Mutation m8 in `UnprepareBatch.answer`)
* klasse: Eigencode, dessen Entfernung keinen Test rot macht

### F-2 MEDIUM — Abbruch mitten im Strom (Schreibfehler) hat keinen Test

* kategorie: MEDIUM
* quelle: Reviewer-Skill „Eigencode, dessen Entfernung keinen Test rot macht“; [TDS-QA-07](../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität)
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/UnprepareBatch.kt` · „HandshakeEvent.RequestFinished(null, connectionOpen = false)“; `src/main/kotlin/io/github/pt9912/tdswire/protocol/error/ErrorAnswers.kt` · „if (!sink.accept(ErrorToken(code.rule.message(handle)))) return false“
* befund: Beide Zweige sind neu und gehören zum Fall „Senke nimmt ein Token nicht an“ (Client schließt mitten in der Antwort, Kanal bricht). Mutation m6 (Verbindung bleibt offen) und m7 (Ergebnis des Schreibens ignoriert) bleiben grün; weder der Dispatch-Test noch der Prozesstest lässt ein Schreiben scheitern. Failure-Szenario: ein Rückfall in den Zweig „offen“ nach halb geschriebener Nachricht setzt die Schleife auf dem Strom fort; die nächste Antwort hinge hinter einem halben Token (Schutzgut `TDS-QA-07`); heute fängt es vermutlich `GuardedReply` (`abortWrite` schließt die Sitzung), ein Beleg dafür für diesen Pfad fehlt.
* verifizierbar: ja (Mutationen m6, m7; ein Kanal, der nach n Bytes scheitert)
* klasse: Eigencode, dessen Entfernung keinen Test rot macht

### F-3 MEDIUM — ATTENTION während der gestreamten Freigabe-Antwort wirkt erst nach der ganzen Antwort; das steht nirgends

* kategorie: MEDIUM
* quelle: [TDS-FA-07](../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), `SPEC-033` und der Abschnitt zum Anfragezustand in `spec/spezifikation.md` („Die Anfrageschleife liest während einer Anfrage mit und meldet ein erkanntes ATTENTION-Paket … ATTENTION in `RUNNING` und `STREAMING` löst genau einen Cancel aus“); [ADR-0027](../plan/adr/0027-abbruch-und-zuruecksetzen.md)
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/UnprepareBatch.kt` · „val response = StreamedResponse(reply) { }“; `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/RequestLoop.kt` · „UnprepareBatch.answer(unknown, { ready.live.openReply(PacketType.TABULAR_RESULT) }, lead, errors)“
* befund: Der Zweig der Freigabe läuft ohne `AbortableRun` und meldet kein `StreamStarted` (der Zustand bleibt `RUNNING`); gemessen (Probe, eine Verbindung): ein ATTENTION hinter dem Batch bricht die Antwort nicht, die 47 381 930 Byte gehen vollständig auf den Draht (2,5 s), dann folgt die Bestätigung. Mit dem Puffer war die Antwort ebenso nicht abbrechbar, aber vor dieser Änderung war sie auf 100 ERROR begrenzt; nun ist sie eine mehrsekündige Nachricht, und weder `SPEC-060` noch die Nutzer-Doku noch Matrix oder Messprotokoll nennen, dass ein Cancel auf diesem Pfad erst am Ende wirkt oder dass `QUERY_TIMEOUT` ihn nicht begrenzt. Failure-Szenario: ein Client mit kurzem Befehls-Timeout sendet ATTENTION nach 1 s; der Server schreibt weiter, bis die ganze Antwort (bei langsamem Leser proportional länger, der Rückstau hält die Sitzungssperre, `SPEC-033`) gesendet ist.
* verifizierbar: ja (Probe: Batch bis zur Grenze der Nachricht, ATTENTION-Paket dahinter, zweite Nachricht lesen)
* klasse: Aussage über den Abbruch ohne Träger auf dem neuen Pfad

### F-4 MEDIUM — Messvermerk zu vier gleichzeitigen Nachrichten lässt das Ende des Prozesses aus und führt Zeilen, die im Repo nicht wiederholbar sind

* kategorie: MEDIUM
* quelle: `AGENTS.md` §3.8; Reviewer-Skill „Zusage ohne ihre gemessene Bedingung im selben Satz“, „Messzahl aus einem Erzeuger, der nicht im Repo liegt“; [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität) („den Gesamtprozess nicht zum Absturz bringen“)
* pfad: `spec/spezifikation.md` · „vier gleichzeitige Nachrichten von 16 MiB schließen die Verbindungen ohne Antwort, mit einem Batch aus Leerraum gleicher Größe ebenso, also auf der Seite der Anfrage und nicht der Antwort“; `docs/user/referenz-konfiguration.md` · „vier gleichzeitige Nachrichten dieser Größe überlasten diesen Heap schon durch die Anfragen, mit Nummern wie mit Leerraum“; `docs/messungen/unprepare-antwort-speicher-2026-11-07.md` · „b4 | wie b1 | 4 | alle vier ohne Antwort geschlossen; Prozess endete in einem Lauf, lebte in einem anderen“
* befund: Das Messprotokoll nennt für b4 „Prozess endete in einem Lauf, lebte in einem anderen“ und für b5 „Prozess lebt“; `SPEC-060` sagt nur „schließen die Verbindungen“ und „ebenso“. Eigene Läufe: mit `-XX:+ExitOnOutOfMemoryError` beendet sich der Prozess bei vier Verbindungen (zwei von zwei Läufen, auch mit verschiedenen Nummern); ohne die Option endet nur eine von vier Verbindungen (`HANDLER_FAILED`, `OutOfMemoryError`), drei erhalten die Antwort. Ein Betreiber liest aus der Spezifikation Verbindungsende ohne Antwort und nicht Prozessende; im Image des Testservers steht die Option, das Ergebnis ist dort der Verlust aller Sitzungen. Die Zeilen b4 bis b6 stammen aus einer Messfassung, die nicht im Repo liegt (das Protokoll nennt das für b1 bis b3 und sagt, wiederholbar sei allein b2); Spezifikation und Nutzer-Doku führen b4 bis b6 ohne diesen Vorbehalt.
* verifizierbar: ja (Probe mit vier Verbindungen, mit und ohne die Option)
* klasse: Zusage ohne ihre gemessene Bedingung im selben Satz

### F-5 LOW — zwei Schutzbedingungen im Zweig der Freigabe ohne Wirkung

* kategorie: LOW
* quelle: Reviewer-Skill „Eigencode, dessen Entfernung keinen Test rot macht“ (hier ohne Failure-Szenario, äquivalente Mutanten)
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/RequestLoop.kt` · „if (lead != null) resetPending = false“ und „EnvChange.ResetConnection.takeIf { resetPending && unknown.isNotEmpty() }“
* befund: Die Zeile `resetPending = false` ist ohne Wirkung, weil `withResetFirst` eine Antwort ohne `reply` (der gestreamte Fall) nicht verändert und `step` danach ohnehin zurücksetzt (m5 grün). Die Bedingung `unknown.isNotEmpty()` ist bei gesetztem `resetPending` immer wahr: `RESETCONNECTION` leert die Handles vor `release`, jede Nummer ist unbekannt (m9 grün, äquivalent); der Zweig `completed()` mit `resetPending` ist damit nicht erreichbar.
* verifizierbar: ja (Mutationen m5, m9)
* klasse: Eigencode, dessen Entfernung keinen Test rot macht (äquivalent)

### F-6 LOW — „rund 113 Byte je Nummer“ gilt für einstellige Nummern

* kategorie: LOW
* quelle: `AGENTS.md` §3.8
* pfad: `spec/spezifikation.md` · „die Antwort (rund 113 Byte je Nummer) wird Token für Token“; `docs/user/referenz-konfiguration.md` · „die Antwort (rund 113 Byte je Nummer)“
* befund: 113 Byte hat das ERROR-Token für Handle `1` (Test: `ERROR_BYTES = 113`); der Text nennt die Nummer, jede weitere Ziffer macht zwei Byte mehr (eigene Probe mit siebenstelligen Nummern: 40 318 263 Byte für 322 000 Nummern, rund 125 Byte je Nummer). Die Zahl steht ohne diese Bedingung im Satz.
* verifizierbar: ja (Rechnung aus den genannten Zahlen)
* klasse: Messzahl ohne ihre Bedingung (Eingabe)

### I-1 INFO — Bewertung des Prozesstests als Gate (Dauer, Flake-Risiko, Träger)

* kategorie: INFO
* quelle: Maintainability; [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)
* pfad: `src/test/kotlin/io/github/pt9912/tdswire/runtime/UnprepareLastProcessTest.kt` · „Zwei gleichzeitige Batches bis zur Grenze der Nachricht erhalten die vollstaendige Antwort“
* befund: Die Wanduhr des Tests liegt bei 2,6 bis 3,0 s (Probe, Last 20 bis 35); der Test läuft in `make test` und damit in `make gates` (Klasse `tests`), die rund 17 Minuten sind die Gesamtdauer von `make clean-build`. Der Client liest mit 20 s Zeitgrenze je Lesevorgang (`READ_MILLIS`); der Heap-Rand ist deterministisch (zwei und drei Verbindungen bestehen, vier nicht), das Flake-Risiko liegt in der Wanduhr, nicht im Speicher. Der Test ist der einzige Wächter gegen eine gepufferte Antwort (m2) und gehört nach dieser Messung in die Gates; als reines Messwerkzeug wäre die Pufferung nicht mehr rot zu machen. Der Test prüft Länge und DONE-Ende der Antwort, nicht die Tokens dazwischen (alle Nummern sind `1`); die Bytes je Nummer halten die Dispatch-Tests.
* verifizierbar: ja (`make test`)
* klasse: Hinweis an Planner und Verifier

### I-2 INFO — Messung nur mit einheitlicher Eingabe; eigene Probe mit verschiedenen Nummern gleicher Grenze

* kategorie: INFO
* quelle: Reviewer-Skill „Auswahl- oder Gleichheitseigenschaft mit einheitlicher Eingabe“
* pfad: `src/test/kotlin/io/github/pt9912/tdswire/runtime/UnprepareLastProcessTest.kt` · „"EXEC sp_unprepare 1;".repeat(statements - 1)“
* befund: Lauter dieselbe Nummer ist für Speicher der Anfrage der günstigste Fall (gecachte `Integer`, ein Element in `distinct()`); befürchtet war eine engere Grenze bei verschiedenen Nummern. Die eigene Probe (322 000 verschiedene siebenstellige Nummern) hat dieselbe Grenze: drei Verbindungen bestehen, vier enden im Heap. Kein Befund, aber die Probe liegt nicht im Repo (Größenordnung).
* verifizierbar: ja
* klasse: Hinweis, Negativbefund einer Befürchtung

### I-3 INFO — Zeitmessung des Schutzes für den Strom-Stand fehlt im Messprotokoll (Frage des Verifiers)

* kategorie: INFO
* quelle: Slice DoD Liefer-Punkt 2 („Messung mit Zahlen (Zeit, Heap)“); Zuständigkeit Verifier
* pfad: `docs/messungen/unprepare-antwort-speicher-2026-11-07.md` · „Reihe a: Antwort als Puffer, Dispatch-Test, eine Verbindung“
* befund: Die Zeit (3,6 s für 419 429 Nummern) steht nur für den Puffer-Stand; die Reihe b des Strom-Standes trägt keine Zeit. Meine Probe: 2,5 bis 3,0 s für 419 309 Nummern über den Draht (Last 20 bis 35, nicht im Repo). Ob „begrenzte Zeit“ des DoD damit getragen ist, und ob `QUERY_TIMEOUT` diesen Pfad begrenzt (er läuft ohne `AbortableRun`, vgl. F-3), entscheidet dieser Review nicht.
* verifizierbar: nein
* klasse: Hinweis an Verifier

### I-4 INFO — Merge-Probe gegen `main` (`6d75692a`) und gegen die anderen Zweige

* kategorie: INFO
* quelle: Slice §6 (Risiko „Konflikte beim gleichzeitigen Lauf“)
* pfad: `git merge-tree --write-tree main HEAD`
* befund: Quelltext, Tests, Spezifikation und Matrix mergen ohne Konflikt. Inhaltskonflikte nur in acht Planungs- und Messdokumenten: `docs/messungen/referenzabgleich.md`, `slice-qa-vergleichslauf-d-messungen-bulk-merge.md`, `roadmap.md`, Register `messung-am-echten-sql-server-fehlt/state.md`, `slice-antwort-tokens-zielbild-luecken-bauen.md`, `slice-katalog-und-struktur-an-messung-angleichen.md`, `slice-katalogspalten-smallint-tinyint-angleichen.md`, `welle-testserver-manuell.md`. Gegen die Zweige `worktree-agent-a8329d681f4d810e9` (MERGE) und `worktree-agent-a95b80c5a5d35df3e` (Katalog): ebenfalls kein Quelltext-Konflikt, Dokumente wie oben. Ein Zweig mit Code für `slice-antwort-tokens-zielbild-luecken-bauen` besteht nicht; wer ihn baut, ändert `errorAnswerTail` und `unknownHandles` und misst die Reihe b neu (Plan §3 nennt das).
* verifizierbar: ja
* klasse: Konflikt nur in Doku

### I-5 INFO — Kleinigkeiten

* kategorie: INFO
* quelle: Maintainability
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/protocol/error/ErrorAnswers.kt` · „form: ResponseForm = ResponseForm.BATCH“; `docs/plan/planning/observations/BEO-TDS/obergrenze-gemeldete-nummern-je-batch-weicht-vom-echten-server-ab/observation.md` · „höchstens die ersten 100 nicht gehaltenen Nummern“
* befund: (a) Der Parameter `form` von `unknownHandles` hat keinen Aufrufer, der ihn setzt. (b) `observation.md` des Registers trägt die alte Lesart (Obergrenze 100); `state.md` nennt sie überholt (Übertrag aus I-4 des ersten Reports, ausstehend bis zur Closure).
* verifizierbar: ja (Grep)
* klasse: unbenutzter Parameter; Fortschreibung lässt die zweite Fundstelle stehen (mit Hinweis)

## Stand der Findings des ersten Reports

* F-1 (Schutz ruht auf einer Verbindung im Dispatch-Test): behoben in der Sache; die Antwort wird gestreamt, der Prozesstest hält zwei Verbindungen im Serverprozess mit 256 MiB; die Grenze (vier) steht im Messprotokoll, in `SPEC-060` und der Nutzer-Doku. Rest: F-4.
* F-2 (Plan-Tabelle, `serializeHandles`): behoben; `ErrorAnswers.kt`, `RequestLoop.kt`, Tests, `spec-abdeckung.md` und die Link-Nachzüge stehen in §3, `unknownHandles` ersetzt den Namen; die Fläche `4a06a9de..HEAD` gegen die Tabelle gelesen: keine Datei fehlt.
* F-3 („2,7-Fache“): behoben („2,8-mal“, Slice §3 Option 3).
* F-4 (Heap-Kommentar in der Init-Datei): hinfällig; der Heap-Test im Dispatch-Test ist entfernt, `StreamingSmokeTest` bleibt der einzige Abhängige vom Wert.
* I-1 (`check(size <= Int.MAX_VALUE)`): entfallen mit `handleAnswer`.

## Geprüft, ohne Befund

* `src/main/kotlin/…/protocol/dispatch/UnprepareBatch.kt`, `…/error/ErrorAnswers.kt`, `…/error/ErrorMapper.kt`: Token-Reihenfolge (ERROR je Nummer in der Reihenfolge des Textes, danach `errorAnswerTail`: DONE mit Fehlerstatus, Form BATCH, `last` bestimmt nur den RETURNSTATUS der Form RPC), Log ein Eintrag je Antwort (vor dem Schreiben angelegt), Handvektoren unverändert, `code` verweigert Codes mit Nummer (`require(!rule.namesHandle)`, Test hält es); `OutOfMemoryError` mitten im Strom: die Ausnahme verlässt `run()`, der Kanal schließt in jedem Ausgang, hinter ein halbes Token gelangt nichts (`MessageStream` setzt `broken`); Paketgrenzen: ein volles Paket geht erst bei folgendem Byte oder `finish`, EOM nur am letzten (gemessen: Gesamtlänge und DONE-Ende der Antwort am Draht).
* `src/main/kotlin/…/protocol/dispatch/RequestLoop.kt`, `StreamedResponse.kt`, `LeadingToken.kt`: Schreibsperre (jedes `write` unter der Sitzungssperre in `GuardedReply`), Rückstau (der Aufrufer suspendiert, gehalten wird ein Paket), Gnadenfrist des Beendens (`active == null` schließt den Kanal), `queryOutcome` vor dem Schreiben gesetzt, Span endet in `finally`; ENVCHANGE steht bei `RESETCONNECTION` vor dem ersten ERROR (gelesen; Prüfung durch Test siehe F-1) und wird nicht doppelt geschrieben (`withResetFirst` lässt die gestreamte Antwort unverändert).
* Speicher je Verbindung: gehalten werden Anfrage, Text, Nummernlisten und ein Paket; die Probe mit verschiedenen Nummern (I-2) zeigt keine engere Grenze.
* `src/test/kotlin/…/runtime/UnprepareLastProcessTest.kt`, `ServerProcess.kt`, `RuntimeSupport.kt` (`drainMessage`): Kind-JVM wird in `use` beendet, `drainMessage` liest Pakete bis EOM und behält 32 Byte Ende; kein `@Suppress`; die Aussagen der KDoc („Wächter: …“) sind für die Pufferung (m2) und die Obergrenze (m1) gefahren.
* `spec/spezifikation.md` (`SPEC-030`, `SPEC-060`), `docs/plan/kompatibilitaetsmatrix.md` (Zeilen „Freigabe der Handles“ und Lücke 13), `docs/user/referenz-konfiguration.md`: keine Obergrenze mehr, Messvermerk M-22 mit „Einzelmessung je Instanz“; zitierte Testnamen existieren (`make matrix-zitate` grün); `SPEC-007` unverändert, keine Zusage der unteren Schicht ohne Anker im Lastenheft (Schutz als Setzung gekennzeichnet). Grep auf die alte Lesart (`höchstens … 100`, `MAX_REPORTED`, `serializeHandles`, `handleAnswer`, `Puffer genau`, `begrenzte Antwort`) in `spec/`, `docs/` (ohne `reviews/` und ADR), `src/`, `testserver/`, `tools/`, `harness/`: Treffer nur im Messprotokoll (Reihe a, Puffer-Stand, benannt), in `referenzabgleich.md` M-22 (Zeile des Befunds, die die frühere Setzung nennt), in `done/slice-handle-freigabe-und-grenze-bauen.md` (Zeitdokument) und im Register (I-5).
* [ADR-0036](../plan/adr/0036-handle-freigabe-und-grenze-je-sitzung.md), [ADR-0037](../plan/adr/0037-sp-unprepare-unbekanntes-handle-ergibt-fehler-8179.md), [ADR-0038](../plan/adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md), [ADR-0039](../plan/adr/0039-abschluss-der-antwort-auf-eine-rpc-nachricht-die-der-parser-abweist.md): im Diff unverändert; die Antwortform BATCH (DONE, kein DONEPROC) bleibt Gegenstand des Slice `slice-antwort-tokens-zielbild-luecken-bauen`, kein Widerspruch zu [ADR-0038](../plan/adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md) und [ADR-0039](../plan/adr/0039-abschluss-der-antwort-auf-eine-rpc-nachricht-die-der-parser-abweist.md) (RPC-Formen).
* `harness/README.md` (Zellen der Sensors-Tabelle, `make test`, `make smoke`, `make matrix-zitate`): nicht im Diff, keine Zelle nennt den Prozesstest oder die Grenze; `make smoke` führt den neuen Test nicht (Stage nennt zwei Klassen), er läuft in `make test`. `tools/stempel/*.eingaenge` unverändert nötig (Quellen unter `src/`).
* Plan-Tabelle §3 gegen `git diff main...HEAD --name-only`: jede berührte Datei steht in der Tabelle, `SPEC-060` und `SPEC-030` im Kopf, `SPEC-007` unberührt; keine `- [x]` im Slice (Grep: 0).
* Nachbesserung ohne zweiten Review: die Commits nach Report-Stand sind `78a5266f` (Merge, nur Dokumente), `5ba270e9` (Quelltext und Tests, Gegenstand dieses Reports), `4ec0deca` (Doku), `ebd197da` (erzeugte Matrix); mit diesem Report liegt für die Code-Fläche ein Re-Review in frischem Kontext vor.
