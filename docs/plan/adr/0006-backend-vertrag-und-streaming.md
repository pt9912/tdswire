# ADR-0006: Backend-Schnittstelle und Resultset-Streaming

**Status:** Proposed

**Datum:** 2026-10-29

**Autor:** pt9912

**Bezug:** [TDS-FA-05](../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen), [TDS-FA-08](../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-FA-10](../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), [TDS-FA-11](../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend)

**Schärft:** [SPEC-002](../../../spec/spezifikation.md#spec-002--interner-query-vertrag), [SPEC-035](../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends), [TDS-FA-11.a](../../../spec/spezifikation.md#tds-fa-11a--simulations-backend), [SPEC-031](../../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität), [ARC-006](../../../spec/architecture.md#1-komponenten-übersicht), [ARC-007](../../../spec/architecture.md#1-komponenten-übersicht), [ARC-015](../../../spec/architecture.md#1-komponenten-übersicht), [TDS-FA-05.a](../../../spec/spezifikation.md#tds-fa-05a--ergebniskodierung), [TDS-FA-08.a](../../../spec/spezifikation.md#tds-fa-08a--backend-vertrag), [TDS-FA-10.a](../../../spec/spezifikation.md#tds-fa-10a--mehrteilige-antworten-und-rpc-ausgabewerte)

---

## Kontext

Die eigene Query-Engine soll unabhängig vom TDS-Codec bleiben. Mehrere Resultsets, Update-Counts und Fehler während des Streamings müssen korrekt sequenziert werden.

`SPEC-002` skizziert den Backend-Vertrag mit einem `Flow<QueryEvent>` und `TDS-FA-08.a` verlangt Backpressure bis zum Transport. Die Zeilenquelle des Simulations-Backends (`SPEC-035`, `TDS-FA-11.a`) ist ein suspendierendes Pull-Interface; `TDS-FA-11` verlangt, dass eine Zeilenquelle über der Speichergrenze schrittweise übertragen wird. Beide Skizzen sprechen gegen Option A für große Ergebnisse. Sie entscheiden nichts; die Wahl trifft der Abschnitt „Entscheidung“.

## Messung (Spike, Stand 2026-10-30)

Gemessen in einem Spike per Dockerfile ohne Mount: JDK 21 mit `-Xmx256m`, Kotlin 2.1.20, `kotlinx-coroutines-core` 1.10.1. Der Transport ist der aus [ADR-0003](0003-transportframework.md) (Option C); die Sitzung ist der explizite Automat aus dem Spike zu [ADR-0004](0004-session-zustandsautomat.md) mit einer `CANCEL_TIMEOUT`-Frist von 5 s, ohne dass das dessen Entscheidung vorwegnimmt. Referenzclient `Microsoft.Data.SqlClient` 7.1.1 auf .NET 10 (SDK 10.0.403) im selben Container über `localhost`, `Pooling=false`, TDS 7.4 ohne TLS, wenn nichts anderes steht. Der Spike-Code liegt nur im Scratchpad des Spike-Agenten und ist nicht im Repo.

**Grenzen des Messobjekts, vorweg:** Das Backend ist ein Generator mit drei Spalten (`INT`, `NVARCHAR`, `BIGINT`, rund 40 Byte je Zeile auf der Leitung) ohne Query-Engine. Alle drei Optionen schrieb derselbe Autor. Es lief kein echter SQL Server und kein Server dieses Repos. Die Läufe sind Einzelläufe auf einem Rechner, auf dem andere Builds liefen; nur die 5-Mio-Zeilen-Messung ist dreimal wiederholt.

Gebaut sind die drei Ergebnismodelle hinter demselben Encoder (Zeilen in Pakete bis etwa 8 KiB, DONE-Familie, ERROR) und derselben Sitzung:

- **A**: `suspend fun materialize(Befehl): List<Part>` baut Ergebnisse als `ArrayList<List<Any?>>` vollständig im Heap; ein Fehler in einem Ergebnis verwirft dieses unvollständige Ergebnis und hängt einen Fehlerteil an, vorher abgeschlossene Ergebnisse bleiben. Dann kodiert der Encoder alles.
- **B**: `Flow<QEvent>` mit `ResultStart`, `Row`, `ResultEnd`, `UpdateCount` und `Failure` im Strom (wie die Skizze in `SPEC-002`); ein Antwort-Sequenzierer hält ein `ResultEnd` bzw. `UpdateCount` zurück, bis das nächste Ereignis oder das Ende bekannt ist, um das Bit `DONE_MORE` zu setzen (`b`), und eine Variante ohne diese Vorschau (`bn`: jedes Ergebnisende ohne `DONE_MORE`).
- **C**: ein blockierender `Iterator<QEvent>`; die Schleife läuft in `withContext(Dispatchers.IO)`, jedes `hasNext()`/`next()` in `runInterruptible` (`c`); `cn`: dieselbe Schleife, aber das Backend ignoriert Interrupts (Setzung, ein nicht unterbrechbarer Treiberaufruf); `cs`: die Schleife direkt auf den vier Threads des Sitzungs-Dispatchers.

Die Fehlersemantik im Strom ist in B und C ein `Failure`-Ereignis, das der Sequenzierer in ERROR plus DONE mit Fehlerstatus übersetzt, oder (Variante) das Schließen der Verbindung; eine Ausnahme aus dem Backend statt des Ereignisses ist nicht gebaut.

### Messzeilen (Referenzclient, TDS 7.4 ohne TLS)

| Messung | A materialisiert | B Flow | C Iterator (`c`) |
|---|---|---|---|
| 1 Mio Zeilen: erste Zeile / gesamt (ms) / Live-Heap-Maximum (MB) | 161 / 786 / 153 | 2 / 683 / 4 | 1 / 794 / 3 |
| 1,5 Mio Zeilen: erste Zeile / gesamt (ms) / Live-Heap-Maximum (MB) | 202 / 1146 / 228 | 1 / 693 / 4 | 2 / 1075 / 4 |
| 2 Mio Zeilen | `OutOfMemoryError` nach 1 691 118 materialisierten Zeilen (Heap 251 MB); der Client erhält einen Fehler, der Serverprozess läuft weiter | 1000 ms gesamt, 4 MB | 1364 ms gesamt, 4 MB |
| 5 Mio Zeilen, gesamt in ms (drei Wiederholungen) | `OutOfMemoryError` | 2236; 2378 / 2281 / 2170 | 3311; 3706 / 3239 / 3073 |
| 20 Mio Zeilen (rund 800 MB auf der Leitung): gesamt (ms) / Live-Heap-Maximum (MB) | nicht versucht | 9391 / 4 | 13 746 / 4 |
| Langsamer Leser (1 Mio Zeilen, 1000 gelesen, 5 s Pause): vom Backend gelieferte Zeilen / Live-Heap (MB) | 1 000 000 / 153 | 53 755 / 3 | 53 755 / 3 |
| Fehler nach Zeile 50 000 (ERROR + DONE): beim Client angekommene Zeilen / danach Folgebefehl auf derselben Verbindung | 0 / ja | 50 000 / ja | 50 000 / ja |
| Fehler vor der ersten Zeile: Zeilen / Folgebefehl | 0 / ja | 0 / ja | 0 / ja |
| Fehler nach Zeile 50 000, Verbindung schließen: Zeilen / Client-Meldung / Folgebefehl | 0 / „transport-level error“ / nein | 49 913 / „transport-level error“ / nein | 49 913 / „transport-level error“ / nein |
| Mehrere Ergebnisse (Zeilen 3, Update-Count 5, Zeilen 2, leeres Ergebnis): Resultsets / `RecordsAffected` / `ExecuteNonQuery` | 3 (3, 2, 0 Zeilen) / 5 / 5 | 3 (3, 2, 0) / 5 / 5 | 3 (3, 2, 0) / 5 / 5 |
| dasselbe mit Fehler nach dem zweiten Ergebnis | 2 Resultsets, dann `SqlException` beim `NextResult`, Folgebefehl ja | gleich | gleich |
| `Cancel()` 600 ms nach Start bei einem Backend, das 3000 ms bzw. 8000 ms blockiert: ms bis der Client fertig ist | 8 / 1 (Blockade in der Materialisierung, 0 Zeilen gelesen) | 7 / 1 | 7 / 1 |
| 500 000 Zeilen mit TDS 7.4 und TLS sowie mit TDS 8.0, Folgebefehl | ja / ja | ja / ja | ja / ja |
| Zeilen Code des Backend-Teils (A: `materialize` 26 + `produceA` 17; B: `flowFor` 24 + `produceB` 5; C: `iterFor` 25 + `produceC` 9; B und C teilen den Sequenzierer mit 15 Zeilen) | 43 | 29 + 15 | 34 + 15 |

| Messung zur Blockade und Nebenläufigkeit | A | B | C (`c`) | `cn` | `cs` |
|---|---|---|---|---|---|
| 200 gleichzeitige Verbindungen, je 5000 Zeilen mit 2 ms Backend-Wartezeit je 50 Zeilen: ok / Wanddauer (ms) | 200 / 464 | 200 / 354 | 200 / 708 | 200 / 734 | 200 / 10 757 |
| Server-Threads, Maximum seit Reset (enthält etwa 15 Grundthreads) | 16 | 16 | 94 | 93 | 15 |
| `Cancel()` bei 3000 ms Blockade: ms bis der Client fertig ist / Folgebefehl | – | – | 7 / ja | 2478 / ja | – |
| `Cancel()` bei 8000 ms Blockade: ms / Ergebnis | – | – | 1 / ja | 5006 / Sitzung schließt nach `CANCEL_TIMEOUT`, Client „transport-level error“, Verbindung zu | – |

`bn` (ohne `DONE_MORE`-Vorschau) lieferte bei der Mehrfach-Ergebnis-Antwort dasselbe wie `b`: 3 Resultsets, `RecordsAffected` 5, `ExecuteNonQuery` 5, Folgebefehl ja. Der Live-Heap ist eine Stichprobe alle 500 ms nach `System.gc()`; „0“ oder „3“ bei kurzen Läufen heißt: zwischen den Stichproben nichts Größeres beobachtet, nicht: nichts verbraucht. Die Zahlen stammen aus zwei Läufen desselben Spike: dem zweiten Hauptlauf mit allen Szenarien und einem Korrekturlauf für die Mehrfach-Ergebnis-Antwort und TLS. Ein erster Hauptlauf war nicht verwertbar und wurde wiederholt, weil der Client die Folgeanfrage falsch prüfte und der Testserver den Update-Count mit dem Befehlscode eines `SELECT` sendete; außerdem lief das Warten des nicht unterbrechbaren Backends in 20-ms-Schritten, was die 200-Ströme-Messung für `cn` verfälschte (6330 ms; nach der Korrektur auf 1-ms-Schritte 734 ms).

### Befunde

- **A** hält das ganze Ergebnis im Heap: bei dieser Zeilenform (`List<Any?>` je Zeile; gemessen 153 MB Live-Heap bei 1 Mio Zeilen, rund 150 Byte je Zeile gegen 40 Byte auf der Leitung) und 256 MB trägt die Messung 1,5 Mio Zeilen und scheitert bei 2 Mio. Die erste Zeile kommt erst nach dem Materialisieren (160 bis 200 ms bei 1 bis 1,5 Mio). Die Grenze hängt an der Zeilendarstellung (eine kompaktere Darstellung ist nicht gebaut) und am Heap, nicht an der Option allein. Der `OutOfMemoryError` wurde im Anfragepfad gefangen und als Fehler gemeldet; der Prozess bearbeitete danach weitere Anfragen. Ob das Fangen in einem Produktivcode tragbar ist, ist nicht gemessen.
- **B und C** halten den Heap flach (höchstens 4 MB Live-Heap von 1 Mio bis 20 Mio Zeilen) und liefern die erste Zeile nach 1 bis 2 ms. Bei einem langsamen Leser lief das Backend nur 53 755 Zeilen voraus (bei etwa 40 Byte je Zeile rund 2 MB; ob das den Socket-Puffern entspricht, ist nicht geprüft); in A hatte es vor der ersten Zeile alle 1 Mio geliefert. Die gleiche Zahl bei B und C ist gemessen, nicht erklärt.
- **Fehlersemantik:** A liefert bei einem Fehler nach 50 000 von 100 000 Zeilen keine Teilzeilen (atomar), B und C liefern die 50 000 Zeilen und dann den Fehler. In allen drei bleibt die Verbindung nach ERROR + DONE benutzbar. Beim Schließen der Verbindung statt ERROR kamen bei B und C 49 913 von 50 000 Zeilen an (die letzten, noch nicht gesendeten Zeilen im Paketpuffer gehen verloren), bei A keine; der Client meldet „transport-level error“ und die Verbindung ist danach nicht benutzbar. Welche Semantik das Lastenheft verlangt, ist keine Messfrage. Ein echter SQL Server lieferte im Fall „Zeilen, dann Fehler“ die beiden Zeilen, dann ERROR und DONE[ERR] in einer Nachricht ([ADR-0007](0007-fehler-und-attention.md), R03); das ist die Messung eines Servers, keine Festlegung für diesen.
- **Durchsatz:** C war bei 5 Mio und 20 Mio Zeilen langsamer als B (5 Mio: 3073 bis 3706 ms gegen 2170 bis 2378 ms; 20 Mio: 13 746 gegen 9391 ms, +46 %), bei 1 Mio kaum (794 gegen 683 ms). Die Ursache ist nicht isoliert (je Zeile zwei `runInterruptible`-Aufrufe und der `iterator`-Builder sind Kandidaten); ein Iterator, der Zeilenblöcke statt einzelner Zeilen liefert, ist nicht gebaut.
- **Blockierung:** Ein blockierendes Backend hält in C einen Thread je laufendem Aufruf: bei 200 Strömen stieg die Zahl der Server-Threads von 16 auf 94 (`Dispatchers.IO`), die Wanddauer lag bei 708 ms gegen 354 ms bei B. Auf den vier Sitzungs-Threads (`cs`) dauerten die 200 Ströme 10,8 s (30-fach) bei 15 Threads; ob dabei andere Verbindungen verhungern, ist nicht gemessen. Beim Abbruch hängt C am Interrupt-Verhalten des Backends: unterbrechbar kam der Client nach 1 bis 7 ms fertig wie bei B, nicht unterbrechbar (`cn`) erst nach dem Ende der Blockade (2478 ms bei 3000 ms), und bei einer Blockade über `CANCEL_TIMEOUT` ([SPEC-033](../../../spec/spezifikation.md#3-defaults-und-konstanten)) schloss die Sitzung die Verbindung nach 5 s. Wie sich echte Backends verhalten, ist eine Annahme des Spike. Die Blockade bei A und B ist im Spike ein suspendierendes Warten; ein blockierender, nicht unterbrechbarer Aufruf hinter dem Flow wurde nicht gebaut (`cn` gibt es nur für C).
- **DONE_MORE:** Ohne Vorschau (`bn`) akzeptierte der Referenzclient die Mehrfach-Ergebnis-Antwort in diesen Szenarien unverändert. Das zeigt, dass dieser Client das Bit hier nicht braucht, nicht, dass es entbehrlich ist. Ein echter SQL Server setzte `MORE` in den Mehrfach-Abfragen, die in [ADR-0007](0007-fehler-und-attention.md) gemessen sind (R04, R05); die Antwort eines echten Servers auf genau die hier gebauten Mehrfach-Ergebnisse wurde nicht gemessen. Die Vorschau kostet in B und C einen zurückgehaltenen Abschluss, dessen Verzögerung nicht gemessen ist.
- Der Sequenzierer des Spike vertraut der Reihenfolge der Ereignisse: unzulässige Ereignisfolgen aus dem Backend (Zeile vor `ResultStart`, doppeltes `ResultEnd`) wurden **nicht** erzeugt und eine Prüfung der Reihenfolge (`SPEC-031`) ist nicht gebaut.

### Nicht gemessen

- Eine echte Query-Engine oder ein echter Treiber (Blockierverhalten und Interrupts sind Annahmen); breitere Zeilen, LOBs und PLP, mehr als drei Spalten, `RETURNVALUE`/OUTPUT-Parameter im Strom.
- Ein blockweise liefernder Iterator, eine kompaktere Zeilendarstellung in A, andere Garbage Collectors, mehr als 200 Verbindungen, Latenz-Perzentile, Langzeitlauf.
- Die Antworten eines echten SQL Servers auf die gebauten Szenarien (auch zum Bit `DONE_MORE`), MARS, Transaktionen und `QUERY_TIMEOUT`/Deadline im Backend-Vertrag.
- Szenarien, in denen der Referenzclient nach wenigen Zeilen schließt (`Dispose` des Readers): er las bei den großen Ergebnissen jeweils bis zum Ende.
- Andere Clients.

## Entscheidung

Wir wählen **Option B**: Das Ergebnis eines Backend-Aufrufs ist ein asynchroner `Flow<QueryEvent>` (Entscheidung des Nutzers, 2026-10-30; die Wahl ist eine Setzung, die Messungen stützen sie in den unten genannten Punkten).

- **Ergebnismodell:** `Flow<QueryEvent>` mit den Ereignissen aus `SPEC-002` (`ResultStart`, `Row`, `ResultEnd`, `UpdateCount`, `Failure`, `ReturnStatus`, `OutputParameter`). Ein Backend mit einem kleinen Ergebnis darf den Strom aus einer fertigen Liste erzeugen; die vollständige Materialisierung ist damit ein Sonderfall des Stroms, keine eigene Option der Schnittstelle.
- **Vorschau für `DONE_MORE`:** Der Antwort-Sequenzierer hält ein `ResultEnd` bzw. `UpdateCount` ein Ereignis lang zurück, bis das nächste Ereignis oder das Ende des Stroms bekannt ist, und setzt daraus das Bit `DONE_MORE`. Das Backend setzt das Bit nicht und kennt es nicht.
- **Gestützt durch die Messung:** A scheitert in dieser Messung bei 2 Mio Zeilen mit 256 MB (B: 4 MB Live-Heap bis 20 Mio Zeilen); B liefert die erste Zeile nach 1 bis 2 ms (A: 160 bis 200 ms bei 1 bis 1,5 Mio Zeilen); B brauchte bei einem suspendierenden Backend keinen Thread je Aufruf (16 Server-Threads bei 200 Strömen; C mit blockierendem Iterator: 94) und schloss den Abbruch bei einem suspendierenden Backend schnell ab (7 / 1 ms; C nur bei unterbrechbarem Backend). Ein blockierendes Backend hinter dem Flow wurde nicht gebaut, für dieses ist beides nicht gemessen (siehe „Offen“); die Skizzen von Backend-Vertrag und Zeilenquelle sind bereits suspendierend (`SPEC-002`, `SPEC-035`). Die Zahlen gelten für den Spike (Generator mit drei Spalten, `-Xmx256m`) und sind keine Grenzen des Produkts.
- **Nicht entschieden und in dieser ADR als offen geführt:** die Fehlersemantik im Strom (Teilzeilen, dann ERROR und DONE, oder Verbindungsende; die Frage gehört zu Lastenheft und Spezifikation, `SPEC-031` beschreibt bisher beide Fälle je nach Zustand des Stroms), einschließlich der Unterscheidung zwischen statement-abbrechenden (R04) und batch-abbrechenden Fehlern (R05 bis R07, [ADR-0007](0007-fehler-und-attention.md)), die das `Failure`-Ereignis nicht trägt, und der Frage, ob ein `Failure` für die Vorschau als „nächstes Ereignis“ zählt, also ein zurückgehaltenes `ResultEnd` oder `UpdateCount` mit `DONE_MORE` abschließt, und was mit einem zurückgehaltenen Abschluss bei Cancel oder Disconnect geschieht; die Prüfung der Ereignisreihenfolge (`SPEC-031`); die Größe, bis zu der ein Ergebnis als fertige Liste gelten darf („klein“ ist nicht gemessen); die Zeilendarstellung und damit die Heap-Grenze eines Backends, das materialisiert; die Fehlerbehandlung bei `OutOfMemoryError`; Abbruch und Thread-Verbrauch eines blockierenden Backends hinter dem Flow (nicht gemessen); die Semantik der Frist `deadlineEpochMillis` im Vertrag (wer sie durchsetzt, nicht das Vorhandensein des Feldes in `SPEC-002`).
- **Nachweis:** Ein Spike mit drei Ergebnismodellen und dem Referenzclient (siehe Messung). Der Spike-Code liegt nicht im Repo.

## Verglichene Alternativen

Kennzeichnung: **(M)** am Spike gemessen, **(abgeleitet)** Folgerung aus den Messungen, nicht gemessen.

| Option                                     | Pro                                    | Contra                                           |
| ------------------------------------------ | -------------------------------------- | ------------------------------------------------ |
| A — vollständig materialisierte Ergebnisse | Einfache Serialisierung; atomar: bei einem Fehler nach Zeile 50 000 kommen keine Teilzeilen an (M); 43 Zeilen Backend-Teil (M) | Speicher: 153 MB Live-Heap bei 1 Mio Zeilen, `OutOfMemoryError` bei 2 Mio (M, 256 MB, diese Zeilendarstellung); erste Zeile nach 160 bis 200 ms statt 1 bis 2 ms (M); Backend lieferte bei langsamem Leser alle 1 Mio Zeilen voraus (M) |
| **B — asynchroner Event-Stream (`Flow`)** | Heap flach bis 20 Mio Zeilen (M, 4 MB); erste Zeile nach 1 bis 2 ms (M); 16 Server-Threads bei 200 Strömen (M); schneller als C bei 5 und 20 Mio Zeilen (M); Abbruch bei suspendierendem Backend (M: 7 / 1 ms); ein blockierendes Backend hinter dem Flow ist nicht gemessen | Komplexere Fehler- und Abbruchsemantik im Sequenzierer (Vorschau für `DONE_MORE`, Teilzeilen vor dem Fehler); der Sequenzierer prüft die Ereignisreihenfolge im Spike nicht; Verzögerung durch den zurückgehaltenen Abschluss nicht gemessen |
| C — synchroner Iterator                    | Einfaches Backend-Interface (34 + 15 Zeilen, M) | Thread je laufendem Aufruf: 94 gegen 16 Threads bei 200 Strömen (M); Abbruch hängt am Interrupt-Verhalten des Backends (M: 2478 ms bzw. Schließen nach `CANCEL_TIMEOUT` bei nicht unterbrechbarem Backend); langsamer als B bei 5 und 20 Mio Zeilen (M, Ursache nicht isoliert) |
| 0 — keine Festlegung vor den Prototypen | Keine Vorfestlegung der Backend-Schnittstelle | Blockiert `SPEC-002` und `SPEC-031`; die Prototypen liegen jetzt als Messung vor |

## Konsequenzen

- **Positiv:** Der Backend-Vertrag ist ohne TDS-Bytes beschrieben; große Ergebnisse brauchen in der Messung keinen Speicher proportional zur Größe (Spike, nicht Produkt). Kleine Ergebnisse bleiben als Liste möglich.
- **Negativ / Risiko:** Backpressure, Abbruch und die Vorschau für `DONE_MORE` machen den Sequenzierer komplexer als eine einfache Rückgabe. Für ein Backend mit blockierendem Treiber hinter dem Flow gibt es keine Messung (gemessen ist nur der blockierende Iterator der Option C an einem fingierten Backend: Thread je Aufruf, Abbruch hängt am Interrupt); Annahme, nicht Messung: ein solches Backend muss selbst für Abbruch und Thread-Verbrauch sorgen. Der Sequenzierer vertraut im Spike der Reihenfolge der Ereignisse.
- **Folgepflicht:** `SPEC-002` auf die gewählte Schnittstelle festlegen (Ergebnismodell und Vorschau, geschehen); Backend-Vertrag als Datei `contracts/query-backend.md` führen (Entwurf, geschehen); `SPEC-031` an der Fehlersemantik und der Reihenfolgeprüfung ausrichten, sobald diese entschieden sind; Tests für Ereignisfolgen, große Ergebnisse, Fehler nach der ersten Zeile und Abbruch.
- **Offen:** Fehlersemantik im Strom (Teilzeilen dann ERROR und DONE, oder Verbindungsende; gehört zu Lastenheft und Spezifikation) samt statement- und batch-abbrechenden Fehlern und dem Zusammenspiel mit der Vorschau für `DONE_MORE` (`Failure` als nächstes Ereignis, zurückgehaltener Abschluss bei Cancel oder Disconnect); Prüfung der Ereignisreihenfolge; Grenze für „kleine“ Ergebnisse; Zeilendarstellung; Behandlung von `OutOfMemoryError`; Abbruch und Thread-Verbrauch eines blockierenden Backends hinter dem Flow, insbesondere bei nicht unterbrechbarem Treiber (nicht gemessen); Semantik der Frist (`QUERY_TIMEOUT`, `deadlineEpochMillis`) im Vertrag; Verzögerung durch den zurückgehaltenen Abschluss.
- **Grenze der Aussage:** Die Messung stammt von einem Generator-Backend und einem Autor; sie trägt keine Aussage über eine echte Query-Engine, breitere Zeilen, LOBs oder die Antworten eines echten SQL Servers.

## Fitness Function (falls maschinell prüfbar)

| Tooling                                     | Regel                                                                                                                                 | Make-Target                         |
| ------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------- |
| JVM-/Protokolltests und .NET-Referenzclient | Ein Resultset oberhalb der Speichergrenze wird ohne vollständige Materialisierung übertragen; ein Fehler nach der ersten Zeile erzeugt ERROR mit Abschluss oder ein Verbindungsende (die Wahl ist offen); die Ereignisfolge wird geprüft. Noch kein bestandenes Gate. | — (Gate noch einzurichten) |

## Re-Evaluierungs-Trigger

Backpressure-Probleme oder zusätzliche Backend-Ergebnisarten; ein echtes Backend, dessen Treiber blockiert und sich nicht unterbrechen lässt; eine Messung am echten SQL Server, die der Vorschau für `DONE_MORE` widerspricht.

## Geschichte

| Datum      | Ereignis                      | Verweis |
| ---------- | ----------------------------- | ------- |
| 2026-10-29 | Proposed – Entscheidung offen | —       |
| 2026-10-29 | Bezug auf `TDS-FA-11` und `SPEC-035` ergänzt; Entscheidung weiter offen | [TDS-FA-11](../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) |
| 2026-10-30 | Messung ergänzt (Spike mit drei Ergebnismodellen und dem Referenzclient); Option B (`Flow` mit einer Vorschau für `DONE_MORE`, kleine Ergebnisse dürfen eine fertige Liste sein) gewählt (Entscheidung des Nutzers, 2026-10-30); Status weiter Proposed bis Review und Verifikation; Fehlersemantik im Strom offen | [SPEC-002](../../../spec/spezifikation.md#spec-002--interner-query-vertrag) |
| 2026-10-30 | Nach Review nachgeschärft (Status weiter Proposed): Aussagen zu Abbruch und Threads auf das suspendierende Backend im Spike begrenzt, blockierendes Backend hinter dem Flow als nicht gemessen und offen geführt, Fehlerarten (statement- und batch-abbrechend) und Zusammenspiel mit der `DONE_MORE`-Vorschau als offen ergänzt | [SPEC-002](../../../spec/spezifikation.md#spec-002--interner-query-vertrag) |
