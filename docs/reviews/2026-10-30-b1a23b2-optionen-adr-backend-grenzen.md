# Optionen für die Folge-ADR zu [slice-adr-backend-grenzen](../plan/planning/done/slice-adr-backend-grenzen.md): Zeilendarstellung, kleine Ergebnisse, OutOfMemoryError, blockierendes Backend, Frist

* Rolle und Stand: Architect, Stand `main` bei Commit b1a23b2 (Worktree). Es ist **keine Entscheidung gesetzt** und keine ADR geschrieben: dieser Bericht ist die Grundlage, aus der der Nutzer jede Option wählt. Eine ADR entsteht erst nach der Wahl (nächste freie Nummer laut ADR-Index, Status zuerst Proposed, `Accepted` nach Review und Verifikation, `Bezug` auf [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md), ohne `Supersedes`).
* Bezug: [TDS-FA-05](../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen), [TDS-FA-08](../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität), [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md), [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md) (Offen 10), Spec-Stellen [SPEC-002](../../spec/spezifikation.md#spec-002--interner-query-vertrag), [SPEC-022](../../spec/spezifikation.md#query-backend-vertrag-zu-spec-022), [SPEC-031](../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität), [SPEC-033](../../spec/spezifikation.md#3-defaults-und-konstanten), [SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends), [SPEC-009](../../spec/spezifikation.md#3-defaults-und-konstanten), [SPEC-034](../../spec/spezifikation.md#3-defaults-und-konstanten), [SPEC-048](../../spec/spezifikation.md#4-fehler-codes-und-logging-felder).
* Gelesen: der Slice (§1 bis §3, §6), [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md) (Messung, Entscheidung, Offen-Liste), [SPEC-002](../../spec/spezifikation.md#spec-002--interner-query-vertrag), [SPEC-022](../../spec/spezifikation.md#query-backend-vertrag-zu-spec-022) (Vertrag Query-Backend), [SPEC-031](../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität), [SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends), die Konstanten in §3 der Spezifikation, der Backend-Port und die Modelle im Code (Port: `QueryBackend`, `QueryExecution`; Domäne: `QueryEvent`, `QueryRequest`, `SqlType`), `ResponseSequencer`, `ResponseRun`, `BufferedResponse`, `RequestLoop`, `ManagedConnection`, `AuthExecutor`, `ValueMapping`, die Closure-Notizen von [slice-backend-port-vertrag](../plan/planning/done/slice-backend-port-vertrag.md), [slice-backend-sequenzierer](../plan/planning/done/slice-backend-sequenzierer.md) und [slice-sql-batch-dispatch](../plan/planning/done/slice-sql-batch-dispatch.md) sowie die Folge-Slices [slice-backend-streaming-nachweis](../plan/planning/done/slice-backend-streaming-nachweis.md), [slice-sim-auswahl-streaming](../plan/planning/open/slice-sim-auswahl-streaming.md), [slice-attention-cancel-timeout](../plan/planning/open/slice-attention-cancel-timeout.md).

## 0. Kurzfassung für die Rückfrage

| Frage | Optionen | Empfehlung |
|---|---|---|
| 1 Zeilendarstellung und JVM-Typen | A `List<Any?>` bleibt, Klassen je Spaltenart festgeschrieben · B `Array<Any?>` · C Spaltenblöcke mit typisierten Arrays (neues Ereignis) | A |
| 2 Grenze für „kleine“ Ergebnisse | A keine Grenze im Vertrag, Pflichttext · B unverbindlicher Richtwert im Vertrag · C Erzwingung im Server | A |
| 3 OutOfMemoryError | A nicht fangen (Verbindungsgrenze fängt, schließt ohne Antwort) · B im Anfragepfad fangen, TDS_INTERNAL_ERROR, Verbindung schließt · C Prozess beenden | B, mit C als Betreiberschalter im Betriebstext |
| 4 Blockierendes Backend hinter dem Flow | A Vertragspflicht: suspendierend, Umhüllung beim Integrator · B Hilfsadapter im Server · C Server isoliert jeden Aufruf auf eigenem Pool | A, B als benannter Folgeschritt |
| 5 Frist `deadlineEpochMillis` | A Protokollkern setzt durch, Feld bleibt Hinweis · B Backend setzt durch · C Feld entfällt, Kern setzt per Dauer durch | A |

## 1. Was seit der Annahme von [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md) gebaut ist und was es ändert

| Stand im Code | Wirkung auf die offenen Punkte |
|---|---|
| Port, Modelle, `ResponseSequencer` und der SQL_BATCH-Dispatch (`RequestLoop`) stehen; `Main` setzt kein Backend, es gibt keinen Betriebsweg mit Backend. | Alle fünf Punkte sind heute nur über Testdoubles erreichbar; die Entscheidung greift in Code, der noch nicht im Betrieb läuft. |
| `BufferedResponse` puffert die **ganze Antwort** ohne Obergrenze und ohne Rückstau (`TokenEncoder.toByteArray` kopiert zusätzlich); Träger des Ersatzes ist [slice-backend-streaming-nachweis](../plan/planning/done/slice-backend-streaming-nachweis.md) (`open/`). | Der Vorteil des Stroms (flacher Heap) gilt im Produkt noch nicht: jedes Ergebnis ist am Dispatch heute „klein“, und ein großes Ergebnis erzeugt den OutOfMemoryError dort, nicht im Backend. Frage 2 und 3 sind dadurch dringlicher, als [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md) es annahm. |
| `ValueMapping` legt die JVM-Klassen je Spaltenart fest (`Int`, `Long`, `Boolean`, `String`, `BigDecimal`, `LocalDate`, `LocalTime`, `LocalDateTime`), als „Festlegung ohne Messung“ gekennzeichnet; `Row` trägt `List<Any?>`. | Frage 1 hat einen gebauten Ist-Stand; Option A schreibt ihn fest. |
| `ResponseSequencer.run` fängt `Throwable` (`runCatching` um `execute()`, `catch` am Strom) und bildet es auf TDS_INTERNAL_ERROR ab ([SPEC-048](../../spec/spezifikation.md#4-fehler-codes-und-logging-felder)); das schließt den OutOfMemoryError **aus dem Backend** ein. Eine Ausnahme der Senke oder des Encoders (`firstOrNull { run.accept(it) }`) läuft nach außen: `RequestLoop.run` schließt den Kanal, `ManagedConnection` wertet es als `HANDLER_FAILED`, der Scope trägt einen leeren `CoroutineExceptionHandler`. | Für Frage 3 gibt es zwei gebaute, nicht gemessene Pfade: Fehler im Backend wird gemeldet, Fehler im Encoder und in der Senke schließt die Verbindung ohne Antwort. Keiner nennt den OutOfMemoryError ausdrücklich; die KDoc sagt „offen“. |
| `RequestLoop` setzt `deadlineEpochMillis = null`; der Port nennt das Feld „wird nur getragen“. `QUERY_TIMEOUT` ([SPEC-009](../../spec/spezifikation.md#3-defaults-und-konstanten), 30 s) hat keinen Leser. | Frage 5: Das Feld hat heute weder Quelle noch Leser. |
| `RowSource.next()` ([SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends)) ist `suspend`; der Port ist suspendierend. Der einzige geplante Backend-Typ, das Simulations-Backend, blockiert nicht. | Frage 4 betrifft nur Integratoren mit eigenem, blockierendem Treiber; im Repo gibt es keinen. |
| `AuthExecutor` nutzt `Dispatchers.IO.limitedParallelism(4)` (Festlegung ohne Messung). | Relevant für Frage 4: ob ein blockierendes Backend den Anmeldepfad verhungern ließe, wurde gemessen (M7). |
| Sieben Dateien tragen KDoc-Sätze „offen ([ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md))“: `QueryBackend`, `QueryExecution`, `QueryEvent`, `QueryRequest`, `ResponseSequencer`, `ValueMapping`, `BufferedResponse`. | Nach der Wahl stehen dort veraltete Sätze; Träger der Korrektur ist ein offener Punkt (Abschnitt 8, Punkt 1). |

## 2. Messobjekt und Grenzen (vorweg)

* **Messobjekt:** ein Spike aus rund 600 Zeilen Kotlin im Scratchpad (`/tmp/claude-1000/-Development-tdswire/8562f454-84ba-456b-9970-945e69c035f4/scratchpad/grenzen`), gebaut per Dockerfile ohne Mount mit dem Gradle-Image des Repos (Digest gepinnt), gestartet mit `docker run --memory=2g --cpus=4`, JDK 21.0.12.1, G1, `-Xmx256m`. Kotlin 2.1.20 und `kotlinx-coroutines-core` 1.10.1 wie in [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md), **nicht** die Versionen des Repos. Der Code ist nicht im Repo und wird nach dem Bericht gelöscht.
* **Kein Netz, kein Encoder, kein Referenzclient:** alle Messungen laufen im Prozess. Zeilenformen sind erfunden: „schmal“ (Int, Text mit rund 10 Zeichen, Long, wie die drei Spalten in [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md)) und „breit“ (zehn Spalten: Int, Long, Boolean, zwei Texte mit 40 und 20 Zeichen, BigDecimal, LocalDate, LocalTime, LocalDateTime, eine NULL-Spalte). Der Autor der Optionen ist der Autor des Spike.
* **Einzel- und Dreifachläufe auf einem ausgelasteten Rechner** (Load Average beim Start 2,64 über 1 min, 4,24 über 5 min, 5,70 über 15 min; 20 Kerne). Zeiten sind Größenordnungen; die Heap-Zahlen (Bytes je Zeile) sind nach `System.gc()` gemessen und stabil genug für den Vergleich der Darstellungen.
* **Das Verhalten eines echten Treibers ist nicht gemessen.** Blockieren ist `Thread.sleep`; „nicht unterbrechbar“ heißt: der Spike fängt die Unterbrechung und wartet weiter (Setzung wie `cn` in [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md)).
* Die Messung zu OutOfMemoryError (M5, M6) ist **schwankend** und wurde deshalb wiederholt; eine Ursache der beobachteten Hänger ist nicht isoliert (Abschnitt 3, M5).

## 3. Messungen

### M1 Heap je Zeile (200 000 Zeilen im Heap gehalten, Bytes je Zeile einschließlich der äußeren Liste)

| Darstellung | schmal (3 Spalten) | breit (10 Spalten) |
|---|---|---|
| `Row(List)` aus `listOf(...)` (Ist-Stand) | 173 | 454 |
| `Row(ArrayList)` mit genauer Kapazität | 173 | 453 |
| `Row(Array<Any?>)` | 151 | 429 |
| Spaltenblock je 1024 Zeilen, typisierte Arrays (`IntArray`, `LongArray`, `BooleanArray`, Textspalten als `String`-Array; BigDecimal als unskalierte `Long`, Datum und Zeit als Zahlen) | 72 | 202 |

Die Textwerte bleiben Objekte; sie bestimmen die Spalte „breit“ (202 Byte je Zeile in der kompakten Form). Eine Nullmaske für nullbare Spalten ist im Spaltenblock **nicht** gebaut. Auf der Leitung sind schmale Zeilen rund 40 Byte; [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md) maß rund 150 Byte je Zeile für `List<Any?>`.

### M2 Zeilen bis zum OutOfMemoryError bei `-Xmx256m` (in Schritten von 10 000 Zeilen, ein Lauf je Zelle)

| Darstellung | schmal | breit |
|---|---|---|
| `Row(List)` | 1 540 000 | 580 000 |
| `Row(ArrayList)` | 1 530 000 | 580 000 |
| `Row(Array<Any?>)` | 1 790 000 | 610 000 |
| Spaltenblock | 3 690 000 | 1 320 000 |

[ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md) maß für Option A 1,5 Mio tragende und 2 Mio scheiternde Zeilen; M2 bestätigt die Größenordnung (1,54 Mio schmal).

### M3 Zeit bis zur ersten Zeile und Gesamtzeit, schmal, Median von fünf Läufen, im Prozess (ohne Netz)

| Zeilen | materialisiert: erste Zeile / gesamt | Strom: erste Zeile / gesamt |
|---|---|---|
| 10 000 | 0,44 ms / 1,5 ms | 0,013 ms / 0,71 ms |
| 100 000 | 3,7 ms / 4,9 ms | 0,015 ms / 4,1 ms |
| 1 000 000 | 140 ms / 180 ms | 0,039 ms / 36 ms |

Ereigniskosten ohne Netz (5 Mio schmale Zeilen, fünf Läufe, Median): ein Ereignis je Zeile 117 ms, ein Ereignis je 256 Zeilen 108 ms (alle Läufe 111 bis 166 ms bzw. 102 bis 158 ms). Der Unterschied liegt in der Streuung.

### M4 Rechnung zur Grenze „klein“ (abgeleitet, nicht gemessen)

Annahmen als Setzung (Voreinstellung, nicht gemessen): höchstens die Hälfte des Heaps (128 MiB von 256 MiB) darf fertigen Listen gehören, und alle Verbindungen bis [SPEC-034](../../spec/spezifikation.md#3-defaults-und-konstanten) (1000) halten gleichzeitig eine. Das sind rund 134 000 Byte je Verbindung, also rund 775 schmale Zeilen (173 Byte, M1) oder rund 295 breite Zeilen (454 Byte). Bei der Hälfte der Verbindungen verdoppeln sich die Zahlen. Das Ergebnis hängt vollständig an den Annahmen; es ist keine Aussage über das Produkt.

### M5 und M6 OutOfMemoryError (Nachbildung von `BufferedResponse`)

Modell: ein Verbindungs-Scope wie `ManagedConnection` (`SupervisorJob`, leerer `CoroutineExceptionHandler`, `Dispatchers.Default.limitedParallelism(4)`); eine Anfrage erzeugt Zeilen aus einem Flow, hängt je Zeile ein `ByteArray` zu 40 Byte an und hält die Antwort 3 s lang („langsame Senke“). **Sturm:** 8 große Verbindungen mit je 800 000 Zeilen (rund 48 MB je Antwort, zusammen mehr als der Heap) und zwei kleine Verbindungen, die 300-mal eine Anfrage mit 1000 Zeilen stellen. **Einzeln:** eine Verbindung mit 6 Mio Zeilen und dieselben kleinen Verbindungen.

| Lauf | Ausgang |
|---|---|
| Einzeln, Verbindungs-Scope fängt (ein Lauf) | die große Verbindung endet mit OutOfMemoryError (Handler zählt 1); alle 600 kleinen Anfragen ok; 200 von 200 Anfragen auf neuer Verbindung ok. |
| Sturm, nicht fangen (A), vier Läufe | (1) 5 große ok, 3 OutOfMemoryError am Handler, alle 600 kleinen ok, Lauf endet regulär. (2) Der **ganze Spike-Prozess hängt** über 600 s, auch `withTimeout` löst nicht aus; erst `timeout` des Aufrufers beendet ihn (Exit 124). (3) und (4): 4 große ok, 2 OutOfMemoryError am Handler, 1 `CoroutinesInternalError`, **eine Coroutine endet nie** (Lauf nach 20 s abgebrochen). In den Läufen ohne Totalhänger waren die 200 Anfragen auf neuer Verbindung danach ok. |
| Sturm, im Anfragepfad fangen (B), vier Läufe | Jeweils 1 bis 3 große Anfragen mit gefangenem OutOfMemoryError und Folgeanfrage auf derselben Verbindung ok (6 bis 7 von 8); in **allen vier Läufen** endete mindestens eine Coroutine nie, in drei davon eine **unbeteiligte kleine Verbindung** (nach 10 bis 14 von 300 Anfragen); in einem Lauf traf der Fehler zusätzlich eine kleine Anfrage (gefangen). Erholung auf neuer Verbindung: 200 von 200 in allen vier Läufen. |
| Sturm, Prozess beenden (C): `-XX:+ExitOnOutOfMemoryError`, drei Läufe | Jeweils Meldung „Terminating due to java.lang.OutOfMemoryError“, Exit-Code 3, Prozessende 1,8 bis 2,1 s nach dem Start (JVM-Start und Füllen des Heaps eingeschlossen). |

Beobachtet auf stderr in den Läufen mit Hängern: „Exception in thread DefaultDispatcher-worker-N: OutOfMemoryError“, „thread kotlinx.coroutines.DefaultExecutor: OutOfMemoryError“, „OutOfMemoryError thrown from the UncaughtExceptionHandler“. Der OutOfMemoryError trifft also auch Threads der Coroutinen-Laufzeit (Dispatcher-Worker und den Timer-Thread für `delay`), nicht nur den Aufruf, der zu viel hielt. **Ursache der Hänger nicht isoliert** (Hypothese: eine Fortsetzung geht in der Laufzeit verloren; nicht geprüft). Das Gemessene trägt: Fangen im Anfragepfad schützt in diesem Modell nicht verlässlich vor hängenden Verbindungen, und in einem von acht Läufen ohne Fangen hing der ganze Prozess. Es trägt nicht: wie häufig das im Produkt vorkäme.

### M7 Blockierendes Backend hinter dem Flow (200 Ströme à 5000 Zeilen, 2 ms Wartezeit je 50 Zeilen; Threads zu Beginn 6)

| Adapter | Wanddauer | Threads, Maximum |
|---|---|---|
| suspendierendes Backend (`delay`), Referenz | 377 ms | 11 |
| je Zeile `runInterruptible` auf `Dispatchers.IO` (W1) | 3099 ms | 77 |
| blockierende Schleife mit `flowOn(Dispatchers.IO)` (W3) | 1061 ms | 75 |
| blockweise (50 Zeilen je Aufruf) `runInterruptible` auf `Dispatchers.IO` (W4) | 707 ms | 78 |
| W1 auf eigenem Pool mit 16 Threads | 3127 ms | 26 |
| W4 auf eigenem Pool mit 16 Threads | 2613 ms | 26 |

Die Wanddauer des Pools mit 16 Threads entspricht der Rechnung 200 Ströme × 100 Aufrufe × 2 ms / 16 = 2,5 s: ein kleiner Pool begrenzt die Threads und serialisiert die Aufrufe.

**Abbruch** (der erste Aufruf blockiert 3000 ms bzw. 8000 ms, Cancel nach 600 ms, `CANCEL_TIMEOUT` 5 s aus [SPEC-033](../../spec/spezifikation.md#3-defaults-und-konstanten)):

| Blockade | Backend unterbrechbar (alle Adapter W1, W4, eigener Pool) | Backend nicht unterbrechbar (W1, W2, W3, W4, eigener Pool) |
|---|---|---|
| 3000 ms | Collector fertig nach 0 bis 6 ms | Collector fertig nach 2400 bis 2412 ms (der Rest der Blockade); der Thread ist nach 3000 ms frei |
| 8000 ms | 0 ms | länger als 5000 ms: die Sitzung würde nach `CANCEL_TIMEOUT` schließen; der Thread bleibt bis 8000 ms belegt |

**Bleibt der Thread nach dem Schließen belegt, wen trifft es?** 80 nicht unterbrechbare Aufrufe zu je 10 s, die Verbindungen sind nach 0,6 s abgebrochen. Danach: eine weitere Backend-Anfrage (10 Zeilen, 2 ms) wartet **länger als 4 s**, sowohl auf `Dispatchers.IO` (Obergrenze 64 Threads) als auch auf dem eigenen Pool (16 Threads). Der Anmeldepfad (leerer Aufruf auf `Dispatchers.IO.limitedParallelism(4)`, wie `AuthExecutor`) braucht dagegen 5 ms, dann 0 ms: er **verhungert nicht**, weil die begrenzte Sicht auf `Dispatchers.IO` nicht gegen dessen 64 Plätze zählt (gemessen, nicht aus der Dokumentation abgeleitet). Ein eigener Pool schützt also andere Teilsysteme nicht besser als die gebaute Lösung; er begrenzt nur die Zahl der Threads des Backends.

### M8 Frist (Frist 500 ms nach Beginn; Ueberschreitung = Ende des Collectors minus Frist; fünf Läufe, drei bei „Backend prüft vor dem Aufruf“)

| Fall | Überschreitung der Frist |
|---|---|
| A Kern: `withTimeout` um die Sammlung, Backend suspendierend (alle 10 ms eine Zeile) | 0 bis 3 ms |
| A Kern, Backend blockierend und unterbrechbar (Aufruf blockiert 2000 ms) | 0 bis 1 ms |
| A Kern, Backend blockierend, nicht unterbrechbar, Aufruf blockiert 2000 ms (`runInterruptible` und `flowOn`) | 1500 bis 1513 ms (der Rest des Aufrufs) |
| B Backend prüft die Frist vor jeder Zeile, suspendierend (10 ms je Zeile) | 7 bis 10 ms |
| B Backend prüft die Frist vor jedem blockierenden Aufruf (400 ms je Aufruf) | 300 bis 301 ms |
| B Backend ignoriert die Frist, der Strom endet nach 300 Zeilen zu 10 ms | kein Ablauf: der Collector endet nach 3042 ms |

Fall C (Feld entfällt, der Kern setzt per Dauer durch) hat dieselbe Messung wie A; der Unterschied liegt im Vertrag, nicht im Verhalten.

## 4. Fragen an den Nutzer

### Frage 1 — Zeilendarstellung und JVM-Typen der Werte (schließt Offen 10 von [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md))

Wie stellt der Port eine Zeile dar, und welche JVM-Klasse hat der Wert je Spaltenart? Die Überführungsregel von [SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) (streng und verlustfrei, `compareTo` für Dezimalzahlen) gilt für die gewählten Klassen.

* **A — `Row(values: List<Any?>)` bleibt; die Klassen je `SqlType` werden festgeschrieben:** `Int32` als `Int`, `Int64` als `Long`, `Bool` als `Boolean`, `UnicodeText` als `String`, `Decimal` als `java.math.BigDecimal`, `Date` als `LocalDate`, `Time` als `LocalTime`, `DateTime` als `LocalDateTime`, NULL als `null`. Das ist der gebaute Stand von `ValueMapping`. 173 Byte je schmale Zeile, 454 je breite (M1); 1,54 Mio schmale Zeilen tragen bei 256 MiB (M2). Kein Umbau.
* **B — `Row(values: Array<Any?>)`:** spart 22 Byte (13 %) bei schmalen und 25 Byte (5 %) bei breiten Zeilen (M1), trägt 16 % mehr Zeilen (M2). Als `data class` vergleicht ein Array nach Identität; alle Aufrufer (Port, Sequenzierer, `ValueMapping`, Testsuite, RowSource des Simulations-Backends) ändern sich.
* **C — Spaltenblöcke mit typisierten Arrays als neues Ereignis** (zum Beispiel `RowBlock` neben `Row`): 72 bzw. 202 Byte je Zeile (-58 % / -55 %), 3,69 Mio schmale und 1,32 Mio breite Zeilen bis zum OutOfMemoryError (M2). Ein Ereignis je Zeile kostet im Prozess nicht messbar mehr als eines je Block (M3). Kosten: neues Ereignis im Port ([SPEC-002](../../spec/spezifikation.md#spec-002--interner-query-vertrag), achtes Ereignis), Umbau von Sequenzierer und `ValueMapping` auf Blöcke, eine Nullmaske, die der Spike nicht baute; `RowSource.next()` ([SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends)) liefert Zeilen einzeln und müsste gepuffert werden.
* **Empfehlung: A.** Die Heap-Grenze der Verbindung löst nicht die Darstellung, sondern der Strom plus die Rückstau-Grenze des Dispatch ([slice-backend-streaming-nachweis](../plan/planning/done/slice-backend-streaming-nachweis.md)); C verschiebt die Grenze um den Faktor 2,3 bis 2,4 und kostet einen Port-Umbau, für ein Problem, das der Strom ohnehin trägt (4 MB Live-Heap bis 20 Mio Zeilen in [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md)). A ist kein Aufwand und schließt Offen 10 von [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md).
* **Konsequenz bei A:** [SPEC-002](../../spec/spezifikation.md#spec-002--interner-query-vertrag) und [SPEC-022](../../spec/spezifikation.md#query-backend-vertrag-zu-spec-022) nennen die Klassen (die Zeile „Offen“ zu Zeilendarstellung und JVM-Typen entfällt); die KDoc von `Row`, `QueryParameter`, `ValueMapping` verweist auf die ADR statt „offen“; die Überführungsregel von [SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) bleibt gültig und ist **nicht** neu zu prüfen; [slice-sim-auswahl-streaming](../plan/planning/open/slice-sim-auswahl-streaming.md) übernimmt die Klassen (Start-Trigger „Zeilendarstellung entschieden“ erfüllt). Bei C: [SPEC-002](../../spec/spezifikation.md#spec-002--interner-query-vertrag), Port, Sequenzierer, `ValueMapping` und `RowSource` ändern sich; die Überführungsregel ist neu zu prüfen (Nullmaske, Blockgrenzen); der Slice wächst um einen Port-Umbau, der als eigener Slice geschnitten werden müsste.

### Frage 2 — Grenze für „kleine“ Ergebnisse (fertige Liste)

[ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md) erlaubt dem Backend, den Strom aus einer fertigen Liste zu erzeugen, wenn das Ergebnis „klein“ ist. Wo liegt die Grenze, und wer setzt sie durch?

* **A — keine Grenze im Vertrag.** Der Vertrag sagt: Ein Backend, das materialisiert, begrenzt sein Ergebnis selbst; der Server schützt sich durch die Rückstau-Grenze der Senke und die Behandlung aus Frage 3. Der Port kann eine Liste nicht von einem Generator unterscheiden (beide sind ein `Flow`); eine Grenze im Vertrag wäre nicht prüfbar.
* **B — unverbindlicher Richtwert im Vertrag**, zum Beispiel ein Ergebnis, das bei 1000 gleichzeitigen Verbindungen höchstens die Hälfte des Heaps hält: rund 775 schmale oder 295 breite Zeilen (M4, Setzung, nicht erzwungen). Der Richtwert hängt an Heap, Verbindungszahl und Zeilenform.
* **C — Erzwingung im Server:** eine Obergrenze für Zeilen oder Bytes je Antwort mit Fehler bei Überschreitung. Das widerspricht dem Randfall von [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) (eine Zeilenquelle über der Speichergrenze wird schrittweise übertragen, nicht abgelehnt) und lässt sich von einem Strom aus Generator nicht trennen; eine Grenze auf den *Rückstau* ist die Aufgabe der Senke in [slice-backend-streaming-nachweis](../plan/planning/done/slice-backend-streaming-nachweis.md), keine Grenze für „klein“.
* **Empfehlung: A.** Kein Aufwand, und die einzige durchsetzbare Grenze liegt in der Senke und in Frage 3. B ist ohne Durchsetzung nur Text, der mit Heap und Verbindungszahl veraltet; der Rechenweg (M4) kann als Begründung in der ADR stehen, ohne Vertragswert zu werden.
* **Konsequenz bei A:** [SPEC-022](../../spec/spezifikation.md#query-backend-vertrag-zu-spec-022) §Pflichten bekommt einen Satz; das Risiko „Grenze für kleine Ergebnisse“ in [slice-backend-streaming-nachweis](../plan/planning/done/slice-backend-streaming-nachweis.md) §6 bekommt den Ausgang „entschieden: keine Grenze im Vertrag“. Bei B kommt ein Richtwert-Satz mit seinen Annahmen dazu; bei C ein Fehlercode, eine Konstante in §3 der Spezifikation und ein Widerspruch zu [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), den das Lastenheft (Rang 1) tragen müsste.

### Frage 3 — Behandlung von OutOfMemoryError im Anfragepfad

Was geschieht, wenn der Heap im Anfragepfad erschöpft ist, etwa durch `BufferedResponse`, ein materialisierendes Backend oder viele gleichzeitige Verbindungen? Zur Grenze der Aussage: Das Modell M5 und M6 ist ein Spike; die Hänger sind nicht erklärt.

* **A — nicht fangen.** Der Fehler läuft bis zur Verbindungsgrenze (`ManagedConnection`, `HANDLER_FAILED`); die Verbindung schließt ohne Antwort, der Prozess läuft weiter. Gebaut ist das heute für Fehler in Senke und Encoder; für Fehler im Backend fängt der Sequenzierer ohnehin `Throwable`. Gemessen (M5, vier Läufe): die großen Verbindungen enden mit OutOfMemoryError am Handler, in drei von vier Läufen blieb mindestens eine Coroutine hängen, in einem Lauf der ganze Prozess.
* **B — im Anfragepfad fangen und als TDS_INTERNAL_ERROR melden** ([SPEC-048](../../spec/spezifikation.md#4-fehler-codes-und-logging-felder): ERROR, DONE mit Fehlerstatus, Verbindung schließt nach der Antwort), wie [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md) es im Spike tat. Gemessen (M6, vier Läufe): gefangene Fehler und Folgeanfragen auf derselben Verbindung gehen, in allen vier Läufen blieb aber mindestens eine Coroutine hängen, in drei davon eine unbeteiligte kleine Verbindung. Die Antwort selbst braucht Speicher; ob ERROR und DONE im erschöpften Heap noch geschrieben werden, ist nicht gemessen.
* **C — Prozess beenden:** `-XX:+ExitOnOutOfMemoryError` (oder `CrashOnOutOfMemoryError`) als Startparameter des Laufzeitabbilds; ein Supervisor startet neu. Gemessen (M6): Exit 3 in drei von drei Läufen, rund 2 s nach dem Start, keine Hänger. Alle Verbindungen gehen verloren. Das steht in Spannung zu [TDS-FA-08](../../spec/lastenheft.md#tds-fa-08--backend-anbindung) (Negative: „der Server bleibt verfügbar“) und [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität) (Verbindungen dürfen den Gesamtprozess nicht zum Absturz bringen; dort sind ungültige und getrennte Verbindungen gemeint, ein gültiges, zu großes Ergebnis fällt nicht wörtlich darunter).
* **Empfehlung: B, mit C als dokumentiertem Betreiberschalter (Betriebstext, kein Server-Code).** Nur A und B erfüllen den Wortlaut „Server bleibt verfügbar“; B meldet dem Client eine definierte Antwort statt eines stillen Abbruchs ([TDS-FA-06](../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände) Negative) und braucht wenige Zeilen im Dispatch. Die gemessene Schwäche (hängende Coroutinen) bleibt benannt; wer sie nicht hinnimmt, wählt C. Die eigentliche Abhilfe ist, den OutOfMemoryError gar nicht zu erreichen: begrenzte Puffer in [slice-backend-streaming-nachweis](../plan/planning/done/slice-backend-streaming-nachweis.md) (dessen Auftrag schon heute).
* **Konsequenz bei B:** [SPEC-031](../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität) und [SPEC-022](../../spec/spezifikation.md#query-backend-vertrag-zu-spec-022) nennen den Fall (nur `OutOfMemoryError`, andere `Error` bleiben unberührt, Setzung); Träger des Codes ist der dritte Liefer-Punkt von [slice-backend-streaming-nachweis](../plan/planning/done/slice-backend-streaming-nachweis.md) („Abbruch und Senkenfehler“) mit einem Satz zusätzlich — die Ausnahme der Senke und des Encoders ist dort ohnehin Gegenstand, kein neuer Slice; der Test erzeugt den Fehler mit einem Testdouble, nicht mit einem echten Heap-Überlauf. Ob detekt das Fangen von `OutOfMemoryError` beanstandet, ist nicht geprüft; ein `@Suppress` ist verboten (`AGENTS.md` §3.2). Bei A: nur Text in [SPEC-031](../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität) und die Eintragung als akzeptiertes Negativ; bei C: Startparameter im Release-Artefakt ([slice-release-artefakt-lizenz](../plan/planning/in-progress/slice-release-artefakt-lizenz.md)) und eine Ausnahme-Begründung gegenüber [TDS-FA-08](../../spec/lastenheft.md#tds-fa-08--backend-anbindung) Negative, die das Lastenheft tragen müsste.

### Frage 4 — Blockierendes Backend (und blockierende Zeilenquelle) hinter dem Flow

Darf ein Backend oder eine Zeilenquelle (`RowSource.next()`) blockieren, und wer sorgt für Abbruch und Thread-Verbrauch?

* **A — Vertragspflicht: suspendierend.** Ein Backend und jede Zeilenquelle blockieren nicht auf dem Thread des Aufrufers; ein blockierender Treiber wird vom Integrator hinter einen eigenen, begrenzten Pool gelegt und unterbrechbar aufgerufen. Der Server stellt keinen Adapter. Gemessene Folgen des Gegenteils (M7): bei 200 Strömen 75 bis 78 Threads statt 11 und die 2- bis 8-fache Wanddauer; der Cancel eines nicht unterbrechbaren Aufrufs wartet den Rest der Blockade ab (2400 ms von 3000 ms), danach schließt die Sitzung nach `CANCEL_TIMEOUT`, und der Thread bleibt bis zur Rückkehr belegt.
* **B — Hilfsadapter im Server** für blockierende Quellen: blockweise (50 Zeilen je Aufruf) und unterbrechbar auf einem begrenzten Pool. Gemessen (M7): blockweise 707 ms gegen 3099 ms je Zeile bei 200 Strömen; der Pool mit 16 Threads hält die Threads bei 26 und serialisiert die Aufrufe (2613 ms). Ein nicht unterbrechbarer Aufruf bleibt auch im Adapter nicht abbrechbar (M7, Abbruch). Kosten: Code, Test, eine Konstante (Poolgröße) in §3 der Spezifikation.
* **C — der Server führt jeden Backend-Aufruf auf einem eigenen Pool aus** (`flowOn`): kostet für suspendierende Backends einen Thread-Wechsel je Wechsel des Dispatchers (nicht gemessen) und löst das Abbruchproblem nicht (M7, `flowOn` nicht unterbrechbar: 2412 ms bzw. über 5000 ms).
* **Empfehlung: A, B als benannter Folgeschritt.** Im Repo gibt es kein blockierendes Backend (Simulations-Backend und Test-Backend suspendieren); [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md) führt als Re-Evaluierungs-Trigger „ein echtes Backend, dessen Treiber blockiert und sich nicht unterbrechen lässt“. Bis dahin trägt ein Pflichttext mit den gemessenen Folgen; ein Adapter ohne Nutzer ist ein Dokument mehr, das altern kann.
* **Konsequenz bei A:** [SPEC-022](../../spec/spezifikation.md#query-backend-vertrag-zu-spec-022) §Pflichten ersetzt die Annahme („ein blockierendes Backend sorgt selbst …“) durch die Pflicht und die Messzeile; [SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) bekommt für `RowSource.next()` denselben Satz; [slice-sim-auswahl-streaming](../plan/planning/open/slice-sim-auswahl-streaming.md) prüft eine suspendierende Quelle (wie dort schon vorgesehen), das Out-of-Scope „blockierende Zeilenquellen“ bekommt den Ausgang „entschieden“; die Aussage zum Thread-Verbrauch in der Vertragsdatei verweist auf M7. Bei B kommt ein Slice mit Adapter, Test und Konstante dazu, Start-Trigger: ein Integrator mit blockierendem Treiber.

### Frage 5 — Semantik der Frist `deadlineEpochMillis`

[ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md) hat das Feld im Vertrag bestätigt; offen ist, wer es durchsetzt und was bei Ablauf geschieht. Die einzige serverseitige Quelle einer Frist ist `QUERY_TIMEOUT` ([SPEC-009](../../spec/spezifikation.md#3-defaults-und-konstanten), 30 s, vorläufig, konfigurierbar); der Client-Timeout kommt als ATTENTION (Spec-Lücke in [slice-attention-cancel-timeout](../plan/planning/open/slice-attention-cancel-timeout.md) §6: welche Frist `TDS_TIMEOUT` auslöst).

* **A — der Protokollkern setzt durch; das Feld bleibt als Hinweis für kooperative Backends.** Der Dispatch setzt die Frist auf „Beginn plus `QUERY_TIMEOUT`“ und umschließt die Sammlung des Stroms mit einem Zeitlimit; bei Ablauf geht es den Weg von Cancel (Strom abbrechen, `close()`, zurückgehaltenen Abschluss verwerfen) und endet mit ERROR `TDS_TIMEOUT` ([SPEC-016](../../spec/spezifikation.md#4-fehler-codes-und-logging-felder)) plus DONE mit Fehlerstatus, die Verbindungsfolge nach der Entscheidung von [slice-attention-cancel-timeout](../plan/planning/open/slice-attention-cancel-timeout.md). Ein Backend darf die Frist ignorieren. Gemessen (M8): Überschreitung 0 bis 3 ms bei suspendierendem und unterbrechbarem Backend; bei nicht unterbrechbarem Aufruf bis zum Ende des Aufrufs (1500 ms bei 2000 ms Blockade), begrenzt durch `CANCEL_TIMEOUT`.
* **B — das Backend setzt durch**, der Kern prüft nichts. Gemessen (M8): ein Backend, das die Frist ignoriert, läuft bis zum Stromende (3042 ms bei 500 ms Frist); ein kooperatives Backend überschreitet um eine Zeilenzeit (7 bis 10 ms) bzw. eine Aufrufzeit (300 ms). `QUERY_TIMEOUT` hätte für das Simulations-Backend keine Wirkung, solange nicht jeder Integrator die Frist beachtet; `TDS_TIMEOUT` müsste als `Failure` aus dem Backend kommen.
* **C — das Feld entfällt**; der Kern setzt per Dauer durch (monoton, ohne Wanduhr). Verhalten wie A (M8). Es nimmt die Bestätigung des Feldes durch den Nutzer in [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md) zurück und ändert `QueryRequest` ([SPEC-002](../../spec/spezifikation.md#spec-002--interner-query-vertrag), Port, Tests, drei Aufrufstellen).
* **Empfehlung: A.** Nur der Kern kann die Frist unabhängig vom Backend garantieren (B bietet das gemessen nicht), und A behält das bestätigte Feld; die Umrechnung der Wanduhr in eine Dauer geschieht einmal beim Start (Setzung unten), danach läuft das Zeitlimit monoton. Der Nachteil, die Verlängerung durch nicht unterbrechbare Aufrufe, trifft jede Option gleich und ist durch `CANCEL_TIMEOUT` begrenzt.
* **Konsequenz bei A:** [SPEC-022](../../spec/spezifikation.md#query-backend-vertrag-zu-spec-022) und [SPEC-002](../../spec/spezifikation.md#spec-002--interner-query-vertrag) beantworten „wer“ und „was bei Ablauf“; [SPEC-009](../../spec/spezifikation.md#3-defaults-und-konstanten) bekommt als Leser den Dispatch; die Spec-Lücke in [slice-attention-cancel-timeout](../plan/planning/open/slice-attention-cancel-timeout.md) §6 („welche Frist löst `TDS_TIMEOUT` aus“) ist beantwortet (`QUERY_TIMEOUT`), der Slice behält die Entscheidung über Class und Verbindungsfolge und wird zum Träger des Setzens der Frist und des Zeitlimits (sein Liefer-Punkt „`CANCEL_TIMEOUT`“ ist verwandt, der Plan ist anzupassen); [slice-sql-batch-dispatch](../plan/planning/done/slice-sql-batch-dispatch.md) bleibt `done/` (Risiko „Frist bleibt `null`“ hat dort Ausgang „weiter offen“ mit Verweis hierher, der Test hält `null`) und der Test ändert sich mit dem Träger. Bei B entfällt die Änderung des Dispatch, jeder Integrator trägt die Pflicht; bei C ändern sich Port und [SPEC-002](../../spec/spezifikation.md#spec-002--interner-query-vertrag).

## 5. Setzungen ohne Frage (Voreinstellung, nicht gemessen)

Alle als „Setzung (Voreinstellung 2026-10-30, nicht gemessen)“ in die ADR zu übernehmen, außer der Nutzer widerspricht.

* **Zeilen:** `Row.values` hat so viele Werte wie die Spalten des offenen Ergebnisses (der Sequenzierer prüft es heute) und wird vom Backend nach der Übergabe nicht mehr verändert oder wiederverwendet. Die Klassen aus Frage 1 gelten auch für `QueryParameter.value` und `OutputParameter.value`; Letzteres bleibt bis zur Abbildung abgelehnt ([SPEC-031](../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität)).
* **OutOfMemoryError (bei B):** nur `OutOfMemoryError` wird gefangen, andere `Error` nicht; die Antwort ist die feste Meldung von [SPEC-048](../../spec/spezifikation.md#4-fehler-codes-und-logging-felder) ohne den Text der Ausnahme; die Ausnahme geht mit ihrem Typnamen ins Log; die Verbindung schließt nach der Antwort (nicht offen wie im Spike von [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md), weil der Zustand der Sitzung nach einem Heap-Überlauf nicht gesichert ist). Die Folgeanfrage auf derselben Verbindung ist gemessen (M6: ok), die Konsistenz der Sitzung nicht.
* **Frist (bei A):** `null` heißt keine Frist; eine Frist in der Vergangenheit endet sofort in `TDS_TIMEOUT`; die Wanduhr wird einmal beim Start der Sammlung in eine Dauer umgerechnet; `CANCEL_TIMEOUT` ([SPEC-033](../../spec/spezifikation.md#3-defaults-und-konstanten)) bleibt unabhängig davon die Frist für den Abbruch selbst. Ein nicht unterbrechbarer Aufruf bleibt nach dem Schließen der Verbindung bis zur Rückkehr belegt; der Server kann das nicht verhindern (M7).
* **Blockierende Quellen (bei A):** Der Hinweis an Integratoren, blockweise statt je Zeile aufzurufen, steht als Empfehlung (M7: 707 ms gegen 3099 ms), nicht als Pflicht.
* Der Anmeldepfad (`AuthExecutor`, `Dispatchers.IO.limitedParallelism(4)`) bleibt unverändert; M7 zeigt keinen Anlass.

## 6. Folgen für die Folge-Slices (nach Empfehlung A / B / A / A / A)

| Slice | Folge |
|---|---|
| [slice-backend-streaming-nachweis](../plan/planning/done/slice-backend-streaming-nachweis.md) (`open/`) | Risiken „Zeilendarstellung“, „kleine Ergebnisse“, „OutOfMemoryError“, „blockierendes Backend und Frist“ in §6 bekommen ihren Ausgang aus der ADR; der dritte Liefer-Punkt nimmt Fehler der Senke und des Encoders einschließlich `OutOfMemoryError` auf (Frage 3 B); `BufferedResponse` wird dort ersetzt, bis dahin gilt das Gemessene (M5, M6) für den Dispatch. Start-Trigger unverändert. |
| [slice-sim-auswahl-streaming](../plan/planning/open/slice-sim-auswahl-streaming.md) (`open/`) | Start-Trigger „Zeilendarstellung entschieden“ erfüllt; Klassen je Spaltenart aus Frage 1 A; Quelle ist suspendierend (Frage 4 A); die Frist darf das Backend ignorieren (Frage 5 A). Das Heap-Verfahren kommt aus dem Nachweis-Slice. |
| [slice-sql-batch-dispatch](../plan/planning/done/slice-sql-batch-dispatch.md) (`done/`) | bleibt unverändert (Closure-Notiz sind Zeitdokument); seine Risiken „Frist bleibt `null`“ und „vollständig gepuffert“ zeigen auf diese ADR und auf die Träger oben. Das Setzen der Frist (Frage 5 A) trägt [slice-attention-cancel-timeout](../plan/planning/open/slice-attention-cancel-timeout.md). |
| [slice-attention-cancel-timeout](../plan/planning/open/slice-attention-cancel-timeout.md) (`open/`) | Träger von Frist und Zeitlimit (Frage 5 A); die Spec-Lücke „welche Frist löst `TDS_TIMEOUT` aus“ ist beantwortet. |
| [slice-backend-port-vertrag](../plan/planning/done/slice-backend-port-vertrag.md), [slice-backend-sequenzierer](../plan/planning/done/slice-backend-sequenzierer.md) (`done/`) | bleiben unverändert; der KDoc-Nachzug an sieben Dateien (Abschnitt 1) braucht einen Träger (Abschnitt 8, Punkt 1). |
| Vertragsdatei `contracts/query-backend.md` (von [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md) genannt) | liegt im Repo nicht; der Vertrag steht in Abschnitt 6 der Spezifikation ([SPEC-022](../../spec/spezifikation.md#query-backend-vertrag-zu-spec-022)), dort ist die Anschlussstelle. |

## 7. Akzeptierte Negative, nicht Gemessenes und Träger

* **Akzeptiertes Negativ:** Der Spike hat einen Autor, erfundene Zeilenformen und eigene Erwartungen; er belegt Größenordnungen und Verhalten im Modell, nicht das Verhalten eines echten Treibers oder des Produkts (`BEO-TDS/aussagen-ueber-messung-hinaus`). Einmalig und im Bericht benannt; die ADR führt es als „Grenze der Aussage“.
* **Akzeptiertes Negativ:** Die Hänger in M5 und M6 sind nicht erklärt. Die Aussage in der ADR lautet nur: im Spike trat das auf; ob und wie häufig es im Produkt vorkäme, ist nicht gemessen. Wer C wählt, braucht die Ursache nicht.
* **Nicht gemessen, Träger vorhanden:** Rückstau und Heap im Produktivbetrieb unter Last → [slice-qa-performance](../plan/planning/open/slice-qa-performance.md) und `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`; Verzögerung durch den zurückgehaltenen Abschluss → ebenda (Risiko in [slice-backend-streaming-nachweis](../plan/planning/done/slice-backend-streaming-nachweis.md) §6); Disconnect während der Anfrage → [slice-abbruch-ressourcen-shutdown](../plan/planning/open/slice-abbruch-ressourcen-shutdown.md).
* **Nicht gemessen, kein neuer Träger nötig:** Verhalten mit den Versionen des Repos (Kotlin, `kotlinx-coroutines`), andere Garbage Collectors, mehr als 200 Ströme, echte Treiber. Der Re-Evaluierungs-Trigger aus [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md) („ein echtes Backend, dessen Treiber blockiert“) deckt den wiederkehrenden Fall.

## 8. Offene Punkte für den Nutzer

1. **Träger des KDoc-Nachzugs** (sieben Dateien tragen „offen ([ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md))“): Vorschlag: ein Doku-Commit des Implementers nach der Annahme der ADR, als Anschlussstelle dieses Slice (der Slice-Text schließt Produktionscode aus; KDoc-Änderung verändert kein Verhalten). Alternative: die jeweils berührenden Slices ziehen nach; ohne Träger bleiben die Sätze veraltet, und kein Sensor meldet es.
2. **Frage 3, Hänger:** Soll die Ursache der hängenden Coroutinen (M5, M6) vor der Wahl isoliert werden? Vorschlag: nein; sie ändert die Wahl nur, wenn sie ein Fehler des Spike wäre, und C umgeht sie.
3. **Frage 5:** Die Wahl A macht [slice-attention-cancel-timeout](../plan/planning/open/slice-attention-cancel-timeout.md) zum Träger des Setzens der Frist; dessen Plan und Größe (≤ 3 Liefer-Punkte) sind danach zu prüfen.

## 9. Ablauf nach der Wahl

1. Der Nutzer wählt je Frage; der Architect schreibt die ADR als Proposed (Bezug [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md) und [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md), ohne `Supersedes`) mit den Messzeilen aus Abschnitt 3 und den Setzungen aus Abschnitt 5, Offen-Liste mit Träger je Punkt, und trägt sie in den ADR-Index ein.
2. [SPEC-002](../../spec/spezifikation.md#spec-002--interner-query-vertrag) und [SPEC-022](../../spec/spezifikation.md#query-backend-vertrag-zu-spec-022) (und, je nach Wahl, [SPEC-031](../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität), [SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends), [SPEC-009](../../spec/spezifikation.md#3-defaults-und-konstanten)) werden fortgeschrieben; die Messtabellen kommen in den Slice (DoD, Punkt 1).
3. Start-Trigger und Risiken der Folge-Slices werden an die Entscheidung angeschlossen; danach Review und Verifikation in frischem Kontext.
