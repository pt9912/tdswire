# Review-Bericht 3: slice-sp-unprepare-ohne-obergrenze (HEAD 5c518231, Nachbesserung nach dem Report zu ebd197da)

* Rolle: Reviewer (Modul 10), frischer Kontext, schmaler Folgelauf zu `2026-11-08-ebd197da-review-2-slice-sp-unprepare-ohne-obergrenze.md` (Report-Stand: `db05c64a`). Gegenstand: nur `git diff db05c64a..HEAD`: `ba1fdf3b` (Quelltext und Tests: Freigabe-Antwort unter `AbortableRun`, neue Klasse `RunConclusion`, `UnprepareBatch.answer` mit `SequenceOutcome` und Senke) und `5c518231` (Spezifikation, Nutzer-Doku, Matrix, Messprotokoll, Plan, Abdeckungsmatrix). Der Rest des Slice ist nicht noch einmal gelesen.
* Maßstab: Plan, Entscheidungen und Hard Rules; [TDS-FA-04](../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-FA-07](../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-QA-07](../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität); [ADR-0027](../plan/adr/0027-abbruch-und-zuruecksetzen.md), [ADR-0036](../plan/adr/0036-handle-freigabe-und-grenze-je-sitzung.md) (beide `Accepted`, im Diff unverändert); `SPEC-029`, `SPEC-033`, `SPEC-060`, Anfragezustand in `spec/spezifikation.md`; `AGENTS.md` §3.1 bis §3.8. Nicht Gegenstand: die DoD (Verifier).
* Selbst gefahren (Arbeitsbaum unverändert): `make gates FORCE=1` Exit 0 (Layer-Cache, Stufe `test` aus dem Cache, nicht neu gelaufen), `make flaeche` Exit 0, `make docs-check` Exit 0 (1238 Dateien, 0 Befunde). Kein `make clean-build`, kein `make e2e` (Verifier). Mutationen in Kopien (`git archive HEAD`), Stage `mut` = Stage `test` begrenzt auf die Pakete `protocol.dispatch.*` und `protocol.error.*` (221 Tests; das schließt die Anfragen an das Backend, RPC, Batch und Bulk, mit ein).

## Gesamturteil

0 HIGH, 3 MEDIUM, 2 LOW, 5 INFO. Die Umstellung trägt in der Sache: Es entsteht genau ein Abschluss je Anfrage (`RunConclusion` ist die verbatim herausgelöste Logik von `execute`; die Zweige `Finished`, `Cut`, `Abandoned`, `Disconnected` sind Zeile für Zeile die des Standes `db05c64a`), die drei Wächter-Tests (Reihenfolge, Abweisung, ATTENTION) werden von den Mutationen m2, m3, m5, m6 rot, und die übrigen Anfragen sind unverändert gelaufen (221 Tests grün im Stand HEAD; m3 und m6 färben die Abbruchtests der Anfragen an das Backend rot, sie hängen also an `RunConclusion`). Offen: der Fristablauf auf dem neuen Pfad und `resetPending = false` tragen keinen Test (F-1, F-2), und die Plan-Angaben zur Fläche sind unvollständig (F-3).

## Antworten auf die Schwerpunkte

* **(a) Nebenläufigkeit, Zustandsmaschine.** Ein ATTENTION (oder die Frist) während des Streamens gewinnt den Übergang genau einmal (`claim`), `job.cancel()` unterbricht `answer` an der nächsten Suspendierung (`reply.write` unter der Sitzungssperre), `AbortedAnswer` hängt genau ein `DONE[ATTN]` an (Test: ein `DoneToken` in der Nachricht, kein weiterer ERROR, kein DONE mit Fehlerstatus). Ein Doppel-Abschluss in `RunConclusion` ist nicht möglich: je `RunEnd` genau ein Zweig, und `Finished` schließt über `reply.finish()` einmal. Senke weist ab: `answer` liefert `CLOSE_WITHOUT_COMPLETION`, `fateOf` ergibt `CLOSE_WITHOUT_REPLY`, die Sitzung ist `CLOSED`, keine Nachricht geht hinaus (m5 rot). Gelesen, nicht gefahren: ein ATTENTION, das nach `CLOSE_WITHOUT_COMPLETION` gewinnt, geht in `AbortedAnswer.conclude` den Zweig „angehängtes DONE[ATTN]“; das ist der unveränderte Weg aller Anfragen. Regression der übrigen Anfragen: `RunConclusion` wird mit dem Konstruktor `(ready.live, errors, aborted)` und dem Aufruf `conclude(end, reply, form, onOutcome)` verwendet; der Code der Zweige ist unverändert (Diff gelesen), `onOutcome` hat den Default `{}`; Wirkung für RPC, Batch und Bulk unverändert. Ein Unterschied bleibt: `LeadingToken` umhüllt die Senke jetzt immer (mit `lead = null` durchlässig, gelesen).
* **(b) Verzicht auf `StreamStarted`.** Spec-konform: der Anfragezustand wechselt `RUNNING → STREAMING` „mit dem Beginn des ersten Ergebnisses“; die Antwort der Freigabe trägt kein COLMETADATA, und „ATTENTION in `RUNNING` und `STREAMING` löst genau einen Cancel aus“ deckt den Zustand `RUNNING` ausdrücklich. `SPEC-060` nennt das („Anfragezustand bleibt `RUNNING`, kein COLMETADATA“). Kein Befund.
* **(c) m10 und Fristablauf.** Beide bleiben ohne Test, siehe F-1 und F-2: Ja, ein Test ist nötig.
* **(d) Aussage ≤ Beleg.** Siehe F-4 und I-3; die Zahlen 113 und 125 Byte stimmen mit der Rechnung (113 + 6 × 2 = 125; 40 318 263 / 322 000 = 125,2) überein.
* **(f) Merge-Probe** siehe I-4.

## Mutationen (selbst gefahren, einzeln)

| Nr. | Mutation | Wirkung gemessen |
|---|---|---|
| m1 | `reportUnknown`: `remaining` auf `null` (Frist der Anfrage wirkt nicht) | **grün** (221 Tests, Exit 0), F-1 |
| m2 | `reportUnknown`: ohne `AbortableRun`, `RunEnd.Finished(UnprepareBatch.answer(...))` | rot: `UnprepareDispatchTest > ATTENTION waehrend der Antwort der Freigabe bricht sie mit einem DONE ATTN ab`; 1 von 221 |
| m3 | `RunConclusion`, Zweig `Cut`: statt `aborted.conclude(...)` ein reguläres `finished(OPEN, reply)` | rot: `AttentionCancelTest` (fünf Tests, u. a. „ATTENTION in RUNNING löst einen Cancel aus …“) und weitere Abbruchtests |
| m4 | `reportUnknown`: `resetPending = false` gestrichen | **grün** (221 Tests, Exit 0), F-2 |
| m5 | `UnprepareBatch.answer`: `... \|\| true` (Ergebnis des Schreibens ignoriert, Ausgang immer `FAILED`) | rot: `UnprepareDispatchTest > Ein abgewiesenes Token mitten in der Antwort beendet die Verbindung ohne weitere Antwort`; 1 von 221 |
| m6 | `RunConclusion`, Zweig `Abandoned`: `live.abandon(CANCEL_TIMEOUT)` gestrichen | rot: `CancelTimeoutTest` (2) und `ShutdownDispatchTest > Ein Backend, das den Abbruch im Beenden ignoriert …`; 3 von 221 |
| m7 | `RunConclusion`, Zweig `Cut`: `onOutcome(...)` gestrichen | **grün** (221 Tests), I-1 (Zeile unverändert aus dem Stand davor) |

## Findings

### F-1 MEDIUM — Fristablauf (`QUERY_TIMEOUT`) auf dem Pfad der Freigabe-Antwort trägt keinen Test

* kategorie: MEDIUM
* quelle: Reviewer-Skill „Eigencode, dessen Entfernung keinen Test rot macht“; [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität); `SPEC-009`
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/RequestLoop.kt` · „val remaining = deadline?.let { (it - times.now()).milliseconds }“ (in `reportUnknown`); `spec/spezifikation.md` · „der Ablauf von `QUERY_TIMEOUT` geht denselben Weg (nicht einzeln getestet)“
* befund: Die Zeilen `deadline` und `remaining` sind neu; Mutation m1 (Frist `null`) lässt alle 221 Tests grün. `SPEC-060` benennt die Lücke ehrlich („nicht einzeln getestet“), und `QueryTimeoutTest` belegt die Frist für `execute`, nicht für diese Kopie des Aufbaus. Failure-Szenario: ein Rückbau oder ein Tippfehler in `reportUnknown` lässt die mehrsekündige Freigabe-Antwort (47 MB) ohne Zeitgrenze laufen; genau diese Grenze war die offene Frage zu „begrenzte Zeit“ (Review 2, I-3). Der Test ist billig: `QueryTimeoutTest` steuert die Zeit mit `SteeredPause`, und die Senke aus `UnprepareDispatchTest` (`ProbeSink`) kann die Antwort an einer Stelle anhalten.
* verifizierbar: ja (Mutation m1)
* klasse: Eigencode, dessen Entfernung keinen Test rot macht

### F-2 MEDIUM — `resetPending = false` im Zweig der Freigabe ist nicht mehr wirkungslos und trägt keinen Test

* kategorie: MEDIUM
* quelle: Reviewer-Skill „Eigencode, dessen Entfernung keinen Test rot macht“; [TDS-QA-07](../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität); Messreihe W-7 (ENVCHANGE Typ 18 genau einmal, als erstes Token)
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/RequestLoop.kt` · „resetPending = false\n        active = run“ (in `reportUnknown`)
* befund: Mutation m4 (Zeile gestrichen) bleibt grün. Review 2, F-5 hielt die Zeile für wirkungslos, weil `withResetFirst` eine Antwort ohne `reply` nicht verändert; mit `RunConclusion` kann das Ereignis aber eine `reply` tragen (`AbortedAnswer.complete` liefert `RequestFinished(AttentionAck.message, …)`, wenn ein ATTENTION gewinnt, nachdem die Antwort vollständig geschrieben ist). Dann prüft `step` noch `resetPending` und `withResetFirst` stellt der Bestätigung ein ENVCHANGE voran (gelesen, nicht gefahren). Failure-Szenario: ein Batch mit `RESETCONNECTION` und unbekannten Nummern, ATTENTION trifft knapp nach dem letzten Token ein; die Bestätigung beginge mit einem zweiten ENVCHANGE Typ 18. Das Fenster ist eng, der Zweig ist aber erreichbar. Die Commit-Nachricht zu `ba1fdf3b` sagt, die wirkungslosen Schutzbedingungen „entfallen“; diese Zeile blieb und hat eine Wirkung, die niemand prüft.
* verifizierbar: ja (Mutation m4)
* klasse: Eigencode, dessen Entfernung keinen Test rot macht

### F-3 MEDIUM — Plan nennt die berührte Fläche nicht vollständig

* kategorie: MEDIUM
* quelle: Reviewer-Skill „Plan-Tabelle nennt die berührten Dateien nicht“
* pfad: `docs/plan/planning/done/slice-sp-unprepare-ohne-obergrenze.md` · „`conclude` (aus `execute` herausgelöst) und `leading` tragen beide Zweige“
* befund: (a) Die neue Datei `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/RunConclusion.kt` steht nicht in der Tabelle in §3; die Zeile zu `RequestLoop.kt` nennt `conclude` und `leading`, einen Namen `leading` gibt es im Diff nicht (die Klasse heißt `LeadingToken` und ist unverändert; die herausgelöste Funktion liegt in `RunConclusion`). (b) Der Kopf „Berührte Spec-Stellen“ nennt `SPEC-030` und `SPEC-060`; berührt sind auch `SPEC-029` (die Zeile `SPEC-060` stützt sich jetzt auf den Abbruch; der neue Test trägt den Tag, die Abdeckungsmatrix zählt dort einen Test mehr) und `SPEC-009` (Frist, im Text von `SPEC-060`). Der Negativbefund „keine Datei darüber hinaus“ gilt damit nicht.
* verifizierbar: ja (Abgleich Tabelle gegen `git diff --stat`)
* klasse: Plan-Tabelle nennt die berührten Dateien nicht

### F-4 LOW — Aussage zum Abbruch in `SPEC-060` ohne die Bedingung der Messung im selben Satz

* kategorie: LOW
* quelle: `AGENTS.md` §3.8; Reviewer-Skill „Zusage ohne ihre gemessene Bedingung im selben Satz“
* pfad: `spec/spezifikation.md` · „ein ATTENTION währenddessen beendet sie nach dem zuletzt geschriebenen Token mit genau einem `DONE[ATTN]` (Anfragezustand bleibt `RUNNING`, kein COLMETADATA), ohne die übrigen Nummern zu melden“
* befund: Der Beleg ist ein Test mit gestelltem Auslöser: ATTENTION wird aus der Senke nach dem dritten Token gespeist, anschließend `repeat(1000) { yield() }` (Fake-Kanal, eine Dispatcher-Schleife). Der Satz gilt für ein ATTENTION, das vor dem Ende der Antwort gewinnt; trifft es nach dem letzten Token ein, gilt der Zweig „Antwort war vollständig“ (ganze Antwort mit DONE-Fehlerstatus, danach eigene Bestätigung), den der Abschnitt Anfragezustand beschreibt, `SPEC-060` aber im Satz nicht abgrenzt. Mit einem echten Kanal ist „nach dem zuletzt geschriebenen Token“ nicht belegt (nicht gemessen).
* verifizierbar: nein
* klasse: Zusage ohne ihre gemessene Bedingung

### F-5 LOW — Abdeckungsmatrix führt einen roten Test, der mit dem Diff nichts zu tun hat

* kategorie: LOW
* quelle: Maintainability; `AGENTS.md` §5 Regel 6 („ein Beleg ist ein bestandener Test- oder Gate-Lauf“)
* pfad: `docs/plan/spec-abdeckung.md` · „`io.github.pt9912.tdswire.transport.listener.ListenerAbortTest.der Listener ruft den Abbruch nach der Frist und wartet CANCEL_TIMEOUT ab` | rot“
* befund: Die mit `5c518231` erzeugte Matrix trägt zwei Zeilen „rot“ (`SPEC-044`, `SPEC-033`) für einen Test, der im Diff nicht berührt ist, und senkt dadurch die Zählung (`SPEC-044` 53 → 52, `SPEC-033` 14 → 13). In meinem `make gates FORCE=1` (Stufe `test` aus dem Layer-Cache) stand kein roter Test; ob der Test unter Last flackert, habe ich nicht gemessen. Die Matrix belegt „den Stand jenes Testlaufs“ (ihr Kopf sagt das), die Commit-Nachricht erwähnt den roten Eintrag nicht.
* verifizierbar: ja (`make spec-abdeckung`)
* klasse: Erzeugtes Dokument trägt einen Last-Befund ohne Vermerk

### I-1 INFO — Zeile `onOutcome` im Zweig `Cut` von `RunConclusion` ohne Test (aus dem Stand davor)

* kategorie: INFO
* quelle: Maintainability
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/RunConclusion.kt` · „onOutcome(end.outcome ?: SequenceOutcome.CLOSE_WITHOUT_COMPLETION)“
* befund: Mutation m7 bleibt grün. Die Zeile ist unverändert aus `RequestLoop.execute` übernommen (Diff `db05c64a..HEAD`), die Lücke liegt also vor diesem Lauf; sie betrifft die Freigabe des Handles bei `sp_prepexec`, das abgebrochen wird. Kein Befund dieses Diffs.
* verifizierbar: ja (Mutation m7)
* klasse: Hinweis, Lücke aus dem Stand davor

### I-2 INFO — Reichweite des Tests „abgewiesenes Token“ und Zeitsteuerung des ATTENTION-Tests

* kategorie: INFO
* quelle: Maintainability
* pfad: `src/test/kotlin/io/github/pt9912/tdswire/protocol/dispatch/UnprepareDispatchTest.kt` · „listOf(2, 3, 4).forEach { refused ->“ und „repeat(1000) { yield() }“
* befund: Der Test weist das zweite, dritte und vierte Token ab (ERROR in der Mitte, letztes ERROR, DONE); das erste ERROR und das ENVCHANGE als Vorspann sind nicht gefahren. Die Senke `ProbeSink` schreibt das Token, bevor sie es abweist; der Test modelliert also „Senke meldet Ausfall“, nicht einen Kanal, der das Schreiben verweigert (der echte Weg über `GuardedReply` hat eigene Tests an `execute`). Die 1000 `yield`s sind eine Zählschleife als Wartebedingung auf einen Dispatcher; unter einem Ein-Thread-Dispatcher deterministisch, bei zu kleiner Zahl rot (nicht flackernd grün).
* verifizierbar: ja
* klasse: Hinweis

### I-3 INFO — Proben b7 und b8: Zahl der Läufe aus einem Erzeuger außerhalb des Repos

* kategorie: INFO
* quelle: `AGENTS.md` §3.8; Reviewer-Skill „Messzahl aus einem Erzeuger, der nicht im Repo liegt“
* pfad: `docs/messungen/unprepare-antwort-speicher-2026-11-07.md` · „alle vier ohne Antwort geschlossen nach rund 0,5 s, der Prozess endet (zwei von zwei Läufen)“
* befund: Der Vorbehalt steht jetzt im Kopf des Protokolls, in `SPEC-060` und in der Nutzer-Doku, und die Zeilen nennen „kein Test im Repo“; die Aussagen sind damit eingeschränkt. „Zwei von zwei Läufen“ ist eine Laufzahl des Reviews 2 (siehe dessen Report), nicht eine Größenordnung; das Protokoll verlinkt den Report nicht als Quelle. Kein Befund über die Einschränkung hinaus.
* verifizierbar: nein
* klasse: Hinweis

### I-4 INFO — Merge-Probe gegen `main` (`7fe16d0c`)

* kategorie: INFO
* quelle: Slice §6 (Konflikte beim gleichzeitigen Lauf)
* pfad: `git merge-tree --write-tree --name-only main HEAD`
* befund: Quelltext, Tests, `spec/`, Nutzer-Doku und Matrix mergen ohne Konflikt. Inhaltskonflikte nur in acht Planungs- und Messdokumenten: `docs/messungen/referenzabgleich.md`, `done/slice-qa-vergleichslauf-d-messungen-bulk-merge.md`, `in-progress/roadmap.md`, Register `messung-am-echten-sql-server-fehlt/state.md`, `open/slice-antwort-tokens-zielbild-luecken-bauen.md`, `open/slice-katalog-und-struktur-an-messung-angleichen.md`, `open/slice-katalogspalten-smallint-tinyint-angleichen.md`, `welle-testserver-manuell.md`. `docs/plan/spec-abdeckung.md` mergt textlich sauber, ist aber ein erzeugtes Dokument: nach dem Merge ist es mit `make spec-abdeckung` neu zu erzeugen (der rote Eintrag aus F-5 und die Zählungen kommen sonst unverändert mit).
* verifizierbar: ja
* klasse: Konflikt nur in Doku

### I-5 INFO — Nutzer-Doku und Beenden

* kategorie: INFO
* quelle: `AGENTS.md` §5 Regel 5
* pfad: `docs/user/referenz-konfiguration.md` · „Die Zahl der Nummern begrenzt `MAX_MESSAGE_BYTES`; die Antwort (113 Byte für eine einstellige Nummer, bei siebenstelligen rund 125 Byte)“
* befund: Der geänderte Absatz ist Ist-Zustand ohne die Wörter der Prüfliste (Grep auf „nicht gebaut“, „vorläufig“, „bisher“, „noch nicht“, „soll“, „seit Slice“ im Absatz: kein Treffer). Er sagt nichts zum Abbruch der Antwort (das gilt für jede Anfrage und steht in `SPEC-060`/`SPEC-029`); das Beenden mit `SHUTDOWN_GRACE` (`abortForShutdown` über `active`) ist auf diesem Pfad nicht getestet, aber durch dieselbe Zuweisung `active = run` wie in `execute` getragen (gelesen).
* verifizierbar: ja
* klasse: Hinweis

## Geprüft, ohne Befund

* `src/main/kotlin/…/protocol/dispatch/RunConclusion.kt`, `RequestLoop.kt` (`reportUnknown`, `execute`): Zweige `Finished`, `Cut`, `Abandoned`, `Disconnected` unverändert gegenüber `db05c64a`; `active` wird in `finally` zurückgesetzt; Span-Ausgang: `queryOutcome` bleibt bei `Finished` der vor dem Lauf gesetzte (`OK`/`ERROR`), bei Abbruch `end.spanOutcome()`; `LeadingToken` mit `lead = null` durchlässig.
* `src/main/kotlin/…/protocol/dispatch/UnprepareBatch.kt`, `…/error/ErrorAnswers.kt`: Ausgang `FAILED` bei vollständig geschriebener Antwort, `CLOSE_WITHOUT_COMPLETION` bei abgewiesenem Token; der entfernte Parameter `form` hatte keinen Aufrufer (I-5 aus Review 2 erledigt); KDoc von `answer`, `completed`, `unknownHandles` beschreibt den Ist-Zustand, kein `@Suppress`.
* `src/test/kotlin/…/protocol/dispatch/UnprepareDispatchTest.kt`: die Reihenfolge ENVCHANGE, ERROR, DONE ist durch `tokens(5)` und die Hex-Anker geprüft; Test-KDoc trägt `@spec`-Tags; die zwei neuen Testnamen stehen in der Matrix der Spezifikation und existieren.
* `docs/plan/kompatibilitaetsmatrix.md`, `docs/plan/spec-abdeckung.md` (Zeilen `SPEC-060`, `SPEC-029`), `docs/messungen/unprepare-antwort-speicher-2026-11-07.md`, `spec/spezifikation.md` (`SPEC-060`): Byte-Rechnung (113, 125) stimmt; b4 bis b6 mit Vorbehalt, b7, b8 als Proben ohne Test benannt; keine Obergrenze, kein Rest der alten Lesart in den geänderten Zeilen.
* Hard Rules `AGENTS.md` §3.1 (Docker), §3.2 (kein Suppress im Diff), §3.4, §3.5 (ADRs unverändert), §3.6, §3.7 (Kommentare nennen Zusage, Kopplung, Grenze), §3.8: kein Verstoß im Diff außer den Fundstellen oben. Haken der DoD: im Diff keine gesetzt.
