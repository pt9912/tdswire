# Review-Bericht: slice-sp-unprepare-ohne-obergrenze (HEAD 1645b7c2)

* Rolle: Reviewer (Modul 10), frischer Kontext. Gegenstand: Diff `0bc03b4d..1645b7c2` (Commits `f9b6136f`, `479ca367`, `e458b464`, `1645b7c2`; davor die Lifecycle-Commits des Planners), Zweig `worktree-agent-a7bc3c771ae8ca3bb`. Maßstab: der Slice (Plan §3, DoD, Rückführungen), [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit), [TDS-QA-07](../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität), [ADR-0036](../plan/adr/0036-handle-freigabe-und-grenze-je-sitzung.md), [ADR-0037](../plan/adr/0037-sp-unprepare-unbekanntes-handle-ergibt-fehler-8179.md) (beide `Accepted`, unverändert), `SPEC-007`, `SPEC-030`, `SPEC-060`, `AGENTS.md` §3.1 bis §3.8. Nicht Gegenstand: die DoD (Verifier).
* Selbst gefahren (Arbeitsbaum des Zweigs unverändert, Mutationen in Kopien unter dem Scratchpad): `make gates FORCE=1` (Exit 0; alle Stages kamen aus dem Layer-Cache, das ist der Stand des Implementers), `make flaeche` (Exit 0), `make docs-check` (Exit 0, 1220 Dateien, 0 Befunde), `make matrix-zitate` (Exit 0; baut die Stage `test` frisch: 1197 bestandene Testfälle, 305 Zitate, 0 ohne PASSED), `make clean-build` (**Exit 2**: `ConnectionStormTest > Fehlerhafte und getrennte Clients gleichzeitig …` FAILED mit `ShutdownChannelGroupException`, Last des Hosts 30 bis 60 durch parallele Läufe; im Frischlauf von `make matrix-zitate` derselben Minuten grün, siehe I-3), `make messung-treiber-handles` und `make e2e` grün (8 Stufen, 173 s), `make messung-treiber-handles` Exit 0.
* Mutationen: Tabelle unten, jeweils `docker build --target test` einer Kopie des Baums bei HEAD.

## Gesamturteil

0 HIGH, 2 MEDIUM, 2 LOW, 5 INFO. Die Mechanik ist in der Sache tragfähig: jede unbekannte Nummer wird gemeldet, in der Reihenfolge des Textes; gehaltene Handles inmitten unbekannter werden freigegeben; die Länge der Antwort stimmt in beiden Durchläufen überein (Deterministik der Meldung, geprüft durch Lesen; der Heap-Test liest die Tokens bis zum DONE ohne Rest); die Randfälle des Parsers sind unverändert und gehalten. Alle fünf Mutationen sind rot. Offen sind: die Aussage „Schutz nach [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität)“ trägt weiter als ihr Beleg (eine Verbindung im Dispatch-Test, F-1), und die Plan-Tabelle §3 nennt die tatsächlich berührten Dateien nicht, einen Namen im Plan gibt es im Code nicht (F-2).

## Mutationen (selbst gefahren)

Kopie des Baums bei HEAD (`git archive`), je eine Mutation, `docker build --target test` (Stage `test`, kein Cache-Treffer für die geänderten Quellen).

| Mutation | Wirkung gemessen |
|---|---|
| `UnprepareBatch.answer`: `.take(100)` wieder eingeführt (`numbers.filterNot { handles.release(it) }.take(100)`) | rot: `UnprepareDispatchTest > Jede unbekannte Nummer ergibt einen ERROR, das Log einen Eintrag je Batch` und `… Ein Batch bis zur Grenze der Nachricht mit unbekannten Nummern endet im Heap` (2 von 1063) |
| `ErrorMapper.code`: Log-Eintrag je Nummer statt je Antwort (`repeat(handles.size) { log.record(…) }`) | rot: `UnprepareDispatchTest > Jede unbekannte Nummer ergibt einen ERROR, das Log einen Eintrag je Batch` und `ErrorMapperTest > TDS_SIM_UNKNOWN_PREPARED_HANDLE ist ERROR 8179 State 8 Class 16 mit der Nummer im Text` (2 von 1063) |
| `handleAnswer`: Antwort ohne Puffer, als Listen von Meldungen und Tokens (`handles.map { rule.message(it) }`, `errorAnswerTail`, `encodeTokens`) | rot nur im Heap-Test: `UnprepareDispatchTest > Ein Batch bis zur Grenze der Nachricht mit unbekannten Nummern endet im Heap` mit `OutOfMemoryError: Java heap space` (1 von 1063); die Zeile s1 des Messprotokolls ist damit wiederholt (bei 128 MiB). Alle anderen Tests bleiben grün: den Speicher hält allein dieser eine Test |
| `answer`: erste unbekannte Nummer überspringen (`drop(1)`) | rot: 3 von 1063 (`Ein Batch mit gehalten, unbekannt, gehalten …`, `Jede unbekannte Nummer …`, `Ein Batch bis zur Grenze …`) |
| `answer`: das letzte Handle des Textes nicht freigeben und nicht melden | rot: `HandleLimitEmbeddingTest > dieEingestellteGrenzeUndDieBatchFreigabeWirkenUeberDenEinstieg()` in `integratorTest` (Gradle bricht dort vor `test` ab; die Unit-Tests dieser Kopie liefen nicht, die Wirkung dort nicht gemessen) |
| Gegenprobe zu F-1: der Heap-Test ruft nach der Antwort zusätzlich `PacketWriter.split(TABULAR_RESULT, reply, 4096)` (die zweite Kopie der Antwort, die `MessageWriter.write` im Serverprozess anlegt) | grün bei 128 MiB (ein Lauf); der Test ohne diesen Aufruf deckt die Kopie nicht ab |

## Findings

### F-1 MEDIUM — „Schutz nach TDS-QA-02“ ruht auf einer Verbindung im Dispatch-Test

* kategorie: MEDIUM
* quelle: `AGENTS.md` §3.8; [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität) (Messmethode: „parallele Verbindungen“); Reviewer-Skill „Zusage ohne ihre gemessene Bedingung im selben Satz“
* pfad: `spec/spezifikation.md` · „(Setzung): die Zahl der Nummern begrenzt die Nachricht der Anfrage“ (Zeile `SPEC-060`, der Satz zum Schutz); `docs/messungen/unprepare-antwort-speicher-2026-11-07.md` · „Nicht gemessen: der Serverprozess unter Last mit mehreren Verbindungen“
* befund: Die Setzung nennt die Begrenzung durch `MAX_MESSAGE_BYTES` und die Antwort in einem Puffer „Schutz“ und trägt als Beleg einen Dispatch-Test mit einem Kanal im Speicher, einer Verbindung und ohne `MessageWriter.write` (dort legt `PacketWriter.split` alle Pakete als zweite Kopie der Antwort an); die Grenze „eine Verbindung, nicht der Serverprozess“ steht im Messprotokoll, nicht in der Spezifikationszeile. Failure-Szenario: bis 1000 Verbindungen (`MAX_CONNECTIONS`, Voreinstellung) senden gleichzeitig je einen Batch aus 16 MiB `EXEC sp_unprepare 1;`; je Verbindung entstehen Anfrage, Nummernlisten und eine Antwort von rund 45 MiB samt Paketkopie (vorher höchstens 100 ERROR, also etwa das Einfache der Anfrage); der Testserver startet mit `-XX:+ExitOnOutOfMemoryError` (`Dockerfile`, Stage des Testserver-Images), ein `OutOfMemoryError` beendet dort den Prozess; die Nutzer-Doku sagt selbst, ob das Fangen im Anfragepfad hängende Verbindungen hinterlässt, sei nicht gemessen. Gemessen ist allein: eine Verbindung besteht bei 96 und 128 MiB Heap.
* verifizierbar: ja (Lauf mit mehreren gleichzeitigen Verbindungen oder Serverprozess mit kleinem `-Xmx`; im Repo kein Test dafür)
* klasse: Zusage ohne ihre gemessene Bedingung im selben Satz

### F-2 MEDIUM — Plan-Tabelle §3 nennt die berührten Dateien nicht; ein Name im Plan gibt es im Code nicht

* kategorie: MEDIUM
* quelle: Reviewer-Skill „Plan-Tabelle nennt die berührten Dateien nicht“; `AGENTS.md` §3.7
* pfad: `docs/plan/planning/done/slice-sp-unprepare-ohne-obergrenze.md` · „passt an `errorAnswerTail` (ResponseRun.kt) und `ErrorMapper.serializeHandles` an“
* befund: Die Tabelle in §3 nennt `ErrorMapper.kt` und `ResponseRun.kt`, nicht aber die beim Bau neu entstandene `protocol/error/ErrorAnswers.kt` (`encodeTokens`, `handleAnswer`); ebenso fehlen `docs/messungen/referenzabgleich.md` (M-22, Link), `docs/plan/spec-abdeckung.md` (erzeugt) und die Link-Nachzüge in `welle-testserver-manuell.md`, `roadmap.md`, `slice-abnahme-d-migrate.md`, `slice-antwort-tokens-zielbild-luecken-bauen.md`, `slice-qa-vergleichslauf-d-messungen-bulk-merge.md` und dem Register `messung-am-echten-sql-server-fehlt`. `ErrorMapper.serializeHandles` gibt es nicht (Grep über `src`: kein Treffer; die Stelle ist `handleAnswer` in `ErrorAnswers.kt`); wer den Token-Slice baut, sucht die falsche Stelle.
* verifizierbar: ja (`git diff --name-only 0bc03b4d..HEAD` gegen die Tabelle; `grep -rn serializeHandles src`)
* klasse: Plan-Tabelle nennt die berührten Dateien nicht

### F-3 LOW — Faktor „2,7-Fache“ ist 2,8

* kategorie: LOW
* quelle: `AGENTS.md` §3.8
* pfad: `docs/messungen/unprepare-antwort-speicher-2026-11-07.md` · „belegt die Antwort etwa das 2,7-Fache der Anfrage“ (ebenso Slice §3 Option 3 „etwa das 2,7-Fache“)
* befund: Aus den Zahlen der Zeile s3 selbst folgt 419 429 × 112 Byte = 46 976 048 Byte gegen 16 777 182 Byte Anfrage, also 2,80; „45 MiB“ und „112 Byte“ sind Rundungen des Tests und eines Abtasters, der nicht im Repo liegt (das Protokoll nennt das und führt die Zahlen als Größenordnung).
* verifizierbar: ja (Rechnung aus den Zahlen des Protokolls)
* klasse: Messzahl aus einem Erzeuger, der nicht im Repo liegt (Rundung)

### F-4 LOW — Der Heap-Test setzt 128 MiB voraus; die Init-Datei nennt ihn nicht

* kategorie: LOW
* quelle: `AGENTS.md` §3.7 (Kommentar der Klasse Kopplung)
* pfad: `gradle/test-jvm.init.gradle.kts` · „Die Test-JVM läuft mit 128 MiB Heap: der Speicher-Beleg in `StreamingSmokeTest`“; `UnprepareDispatchTest` · „endet im Heap der Test-JVM (128 MiB, `gradle/test-jvm.init.gradle.kts`)“
* befund: Der Kommentar an `maxHeapSize` nennt als Abhängigen nur `StreamingSmokeTest`, der den Wert zur Laufzeit gegen `Runtime.maxMemory()` prüft; der neue Heap-Test hängt ebenso am Wert (Mutation „Antwort ohne Puffer“ ist bei 128 MiB rot, mit größerem Heap nicht gezeigt) und prüft ihn nicht. Wer den Wert anhebt, entschärft den Wächter, ohne dass ein Test rot wird.
* verifizierbar: ja (Heap in der Init-Datei anheben und die Mutation „Antwort ohne Puffer“ fahren)
* klasse: Kopplungs-Kommentar nennt nicht jeden Abhängigen

### I-1 INFO — `check(size <= Int.MAX_VALUE)` in `handleAnswer` ist nicht erreichbar

* kategorie: INFO
* quelle: Maintainability
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/protocol/error/ErrorAnswers.kt` · „check(size <= Int.MAX_VALUE) { "Fehlerantwort zu groß" }“
* befund: Rechnung: größte Voreinstellungsgrenze `MAX_MESSAGE_LIMIT` ist 256 MiB (`ConfigValidation`); kürzeste Anweisung 40 Byte ergibt rund 6,7 Millionen Nummern zu je etwa 113 Byte, also rund 760 MB, unter 2^31; längere Nummern verschlechtern das Verhältnis (Anfrage 58 Byte, Antwort 114 Byte). Der Zweig bleibt bei Streichung ohne roten Test, ist aber durch die Grenze der Nachricht nicht auslösbar. Realer Engpass bei 256 MiB ist der Heap (Antwort 760 MB, dazu die Paketkopie), nicht die Int-Grenze; das Protokoll sagt „wer `MAX_MESSAGE_BYTES` erhöht, bemisst den Heap“.
* verifizierbar: nein
* klasse: Verteidigungszweig ohne Test, durch Eingangsgrenze unerreichbar

### I-2 INFO — Zeitbegrenzung ist nicht untersucht (DoD-Frage des Verifiers)

* kategorie: INFO
* quelle: Slice DoD Liefer-Punkt 2; Zuständigkeit Verifier
* pfad: `docs/plan/planning/done/slice-sp-unprepare-ohne-obergrenze.md` · „ob `QUERY_TIMEOUT` diesen Pfad begrenzt, ist nicht untersucht“
* befund: Der Slice benennt die Lücke selbst; gemessen ist 3,6 s für 419 429 Nummern in einer Einzelmessung unter Last. Ob „begrenzte Zeit“ des DoD-Wortlauts damit erfüllt ist, entscheidet nicht dieser Review.
* verifizierbar: nein
* klasse: Hinweis an Verifier

### I-3 INFO — `ConnectionStormTest` flackerte einmal in `make clean-build`

* kategorie: INFO
* quelle: Maintainability
* pfad: `src/test/kotlin/io/github/pt9912/tdswire/stability/ConnectionStormTest.kt` · „Fehlerhafte und getrennte Clients gleichzeitig lassen Listener und gute Verbindungen unberuehrt“
* befund: Ein Lauf von `make clean-build` meldet „ungefangene Ausnahmen in Threads ==> … `ShutdownChannelGroupException`“ (Host unter Last, parallele Läufe anderer Rollen); `make matrix-zitate` mit frischer Stage `test` derselben Minuten ist grün. Pfade des Diffs berührt der Test nicht; die Ausnahme steht in keinem Register-Eintrag (Grep `ShutdownChannelGroup` in `docs/`: kein Treffer). Hinweis an den Planner für das Register `BEO-TDS/wanduhr-tests-flackern-unter-last` oder einen eigenen Eintrag.
* verifizierbar: nein (ein Auftreten)
* klasse: flackernder Test unter Last

### I-4 INFO — Register: Stand ohne Evidenz-Datei, `observation.md` bleibt im alten Wortlaut

* kategorie: INFO
* quelle: Slice DoD („Beobachtungs-Register fortgeschrieben“)
* pfad: `docs/plan/planning/observations/BEO-TDS/obergrenze-gemeldete-nummern-je-batch-weicht-vom-echten-server-ab/observation.md` · „meldet nach `SPEC-060` höchstens die ersten 100 nicht gehaltenen Nummern eines Batches“
* befund: Fortgeschrieben ist nur `state.md` (Stand nennt Zustand und Anker und sagt, dass `observation.md` überholt ist); eine Datei in `evidence/` für diesen Slice fehlt, `observation.md` trägt die alte Lesart. Das ist zulässig, solange die Closure es vermerkt. `state.md` erzählt die Entstehung nicht (kein Befund nach „Zustandsfeld trägt Chronik“).
* verifizierbar: ja
* klasse: Fortschreibung lässt die zweite Fundstelle stehen (mit Hinweis)

### I-5 INFO — Konflikte mit den anderen Zweigen

* kategorie: INFO
* quelle: Slice §6 (Risiko „Konflikte beim gleichzeitigen Lauf“)
* pfad: Zweig `worktree-agent-a7bc3c771ae8ca3bb` gegen `main`, `worktree-agent-a8329d681f4d810e9` (MERGE), `worktree-agent-a95b80c5a5d35df3e` (Katalog), `worktree-agent-a367dd0d063325ded` (Token)
* befund: `git merge-tree` gegen `main` (09c94c05): ohne Konflikt. Gegen die drei Slice-Zweige: Quelltext ohne Konflikt (`ErrorMapper.kt` des Katalog-Zweigs fügt drei Codes in eine Liste ein, `RequestLoop.kt` des MERGE-Zweigs ändert den Zweig `released == null`; beides berührt `ErrorAnswers.kt`, `errorAnswerTail`, `UnprepareBatch.answer` nicht). Inhaltskonflikte nur in Dokumenten: `roadmap.md` (alle drei), `welle-testserver-manuell.md` (MERGE, Katalog), `slice-qa-vergleichslauf-d-messungen-bulk-merge.md`, Register `messung-am-echten-sql-server-fehlt/state.md`, `slice-abnahme-d-migrate.md` (Katalog, Token), `referenzabgleich.md` und `slice-antwort-tokens-zielbild-luecken-bauen.md` (Token). Der Token-Slice ändert `errorAnswerTail` und die Größe je Nummer; die Messzeilen s1 bis s4 sind danach neu zu messen (Slice §3 nennt das).
* verifizierbar: ja (`git merge-tree --write-tree`)
* klasse: Konflikt nur in Doku

## Geprüft, ohne Befund

* `src/main/kotlin/…/protocol/dispatch/UnprepareBatch.kt`: jede Nummer gemeldet, Reihenfolge des Textes, Freigabe der gehaltenen Handles am Ende des Textes (Test mit Handle 1 am Ende für 99, 100, 101, 150, 2000), Parser unverändert (`;;`, Leerraum, führende Nullen, `Int.MAX_VALUE`+1, leerer Text führen zu `null`, durch Lesen von `parse`/`statement`/`handle` nachvollzogen), kein `@Suppress`.
* `src/main/kotlin/…/protocol/error/ErrorAnswers.kt`, `ErrorMapper.kt`, `response/ResponseRun.kt`: zwei Durchläufe konsistent (`errorBytes` ist deterministisch, die Länge des ersten Durchlaufs ist die des zweiten; der Test liest die ERROR-Tokens bis zum DONE ohne Rest), `errorAnswer` unverändert in der Wirkung (`messages.map { ErrorToken } + errorAnswerTail`), Fall `streamSynchronous == false` ruft `handleAnswer` nicht; Schichtregeln und `TooManyFunctions` (detekt grün).
* `src/test/…/UnprepareDispatchTest.kt`, `ErrorMapperTest.kt`: Ränder 99/100/101/150/2000, Log je Batch, Heap-Test fängt `OutOfMemoryError` und meldet ihn als Assertion; Mutationen oben rot.
* `spec/spezifikation.md` (`SPEC-060`, `SPEC-030`): keine Obergrenze und keine Begrenzung des Logs mehr; Messvermerk M-22 trägt „Einzelmessung je Instanz“ und die Grenzen „nicht gemessen“ im selben Satz; `SPEC-007` unverändert und von der Spezifikation nicht berührt; keine Zusage ohne Anker im Lastenheft (Schutz in `SPEC-060` ist als Setzung gekennzeichnet).
* [ADR-0036](../plan/adr/0036-handle-freigabe-und-grenze-je-sitzung.md) und [ADR-0037](../plan/adr/0037-sp-unprepare-unbekanntes-handle-ergibt-fehler-8179.md): unverändert, keine Stelle widerspricht der neuen Lesart (Grep `Obergrenze`, `höchstens die`).
* `docs/user/referenz-konfiguration.md` (komplett gelesen, Grep auf „bisher“, „noch nicht“, „nicht gebaut“, „Zielzustand“, „vorläufig“, „seit Slice“, „soll“): Treffer außerhalb des Prepared-Handles-Absatzes betreffen anderes (Zeile 85 „bisher erzeugte Token“ ist Fachwort); `docs/user/einbettung-und-betrieb.md` und `testserver-manuell.md` nennen die Obergrenze nicht; die Aussage zum `OutOfMemoryError` im Anfragepfad (`einbettung-und-betrieb.md`, Aufgabe Betrieb, Punkt 4) bleibt unverändert und ist zu F-1 zu lesen.
* `docs/plan/kompatibilitaetsmatrix.md` (Zeile „Freigabe der Handles“, Lücke 13): Obergrenze entfällt, zitierte Testnamen existieren (`make matrix-zitate` grün).
* Haken: keine `- [x]` im Slice gesetzt (Grep: 0). Messprotokoll: Vorbehalt „Dispatch-Test, nicht Serverprozess“ und „Abtaster nicht im Repo, Größenordnung“ stehen im Kopf; Wiederholbarkeit: die Aussage „besteht bei 128 MiB“ ist im Repo wiederholbar (`make matrix-zitate`, `make clean-build`), die Zahlen zu 96 und 64 MiB nicht (von Hand geänderter Heap).
* `make flaeche`: grün, öffentliche Fläche unverändert (alle geänderten Typen `internal`).

## Läufe

* `make messung-treiber-handles`: Exit 0 (Treiber mssql-jdbc 13.4.0, Testserver-Container gestartet und entfernt). `make e2e`: grün, 8 Stufen, 173 s.
* Hinweis zum Scratchpad: ein erster Satz Läufe (Mutationen, `make clean-build`) lag in einem Unterordner, den auch andere Läufe der Sitzung nutzen; ein dort angestoßener `make matrix-zitate` wurde von mir abgebrochen und im eigenen Unterordner wiederholt (Ergebnis oben). Die Ergebnisse der Mutationen stehen in Logs, deren Zeilen zu den genannten Tests passen (1063 Tests je Lauf).
