# Query-Backend

Vertrag zwischen Protokollkern und Backend (`TDS-FA-08.a`, `SPEC-002`, `SPEC-022`). Der Protokollkern erzeugt einen `QueryRequest` ohne TDS-Bytes und liest das Ergebnis als Strom von Ereignissen; das Backend kennt weder Pakete noch Tokens. Die Schnittstelle ist ein **Entwurf**: Die Typen stehen als Skizze in `SPEC-002`, gemessen ist das Ergebnismodell an einem Generator-Backend mit drei Spalten, nicht ein Kotlin-Vertrag.

## Aufruf

| Teil | Inhalt |
|---|---|
| `execute(QueryRequest)` | suspendierend; liefert eine `QueryExecution` mit dem Strom `events` |
| `QueryRequest` | Sitzungs- und Anfragekennung, SQL-Text, typisierte Parameter, optionale Frist (`SPEC-002`) |
| `cancel(sessionId, requestId)` | suspendierend; beendet die laufende Operation |
| `QueryExecution.close()` | schließt Zeilenquelle und Ressourcen der Operation |

## Ergebnis

Das Ergebnis ist ein asynchroner Strom (`Flow`) von Ereignissen: `ResultStart` (Spalten), `Row`, `ResultEnd`, `UpdateCount`, `ReturnStatus`, `OutputParameter`, `Failure`. Ein Backend mit kleinem Ergebnis darf den Strom aus einer fertigen Liste erzeugen. Das Backend setzt kein `DONE_MORE`: der Response Sequencer hält ein `ResultEnd` bzw. `UpdateCount` ein Ereignis lang zurück, bis das nächste Ereignis oder das Ende bekannt ist, und setzt daraus das Bit.

## Pflichten

- Metadaten (`ResultStart`) kommen vor den Zeilen (`TDS-FA-08.a`).
- Gelesen wird nur so schnell, wie der Transport abnimmt (Backpressure); ein Backend läuft nicht beliebig voraus.
- Cancel und Disconnect beenden das Lesen und schließen die Zeilenquelle (`SPEC-029`, `SPEC-031`). Ein Backend, dessen Operation sich nicht unterbrechen lässt, hält die Sitzung bis `CANCEL_TIMEOUT` (`SPEC-033`); danach schließt der Server die Verbindung.
- Ein Backend, das blockiert, sorgt selbst für Abbruch und Thread-Verbrauch (Annahme). Gemessen ist das nur für einen blockierenden Iterator an einem fingierten Backend (Thread je laufendem Aufruf, Abbruch hängt am Interrupt), nicht für ein blockierendes Backend hinter dem `Flow` und nicht an einem echten Treiber; der Spike-Flow lief mit einem suspendierenden Backend.

## Festgelegt

- Fehlersemantik im Strom (Setzung des Nutzers, nicht am Produkt gemessen): Solange der Strom synchron ist, folgen auf Teilzeilen ein `Failure` und damit ERROR und DONE; lässt er sich nicht mehr abschließen, schließt der Server die Verbindung ohne ERROR und DONE (`SPEC-031`).

## Offen

- Unterscheidung statement- und batch-abbrechender Fehler im Strom, die `Failure` nicht trägt, und ob ein `Failure` als „nächstes Ereignis“ der `DONE_MORE`-Vorschau zählt (auch: Schicksal eines zurückgehaltenen Abschlusses bei Cancel oder Disconnect): Zuständigkeit Backend-/Mapping-Slice, nach Messung.
- Prüfung der Ereignisreihenfolge im Sequencer (Zeile vor `ResultStart`, doppeltes `ResultEnd`): im Spike nicht gebaut.
- Grenze für „kleines“ Ergebnis (fertige Liste), Zeilendarstellung.
- Behandlung von `OutOfMemoryError`: im Spike wurde er im Anfragepfad gefangen und als Fehler gemeldet (nur bei vollständiger Materialisierung erzeugt, nicht im `Flow`); ob das Fangen im Produktivcode tragbar ist, ist nicht gemessen.
- Verzögerung durch den zurückgehaltenen Abschluss der `DONE_MORE`-Vorschau: nicht gemessen.
- Semantik der Frist: `deadlineEpochMillis` steht im `QueryRequest` (`SPEC-002`); offen ist, wer sie durchsetzt (Backend oder Protokollkern) und was bei Ablauf geschieht (`QUERY_TIMEOUT`, `TDS_TIMEOUT`), nicht das Vorhandensein des Feldes.

## Gemessen

Siehe die Messzeilen in [ADR-0006](../docs/plan/adr/0006-backend-vertrag-und-streaming.md): Heap, erste Zeile, Durchsatz, langsamer Leser, Fehler nach der ersten Zeile, Mehrfach-Ergebnisse, Cancel, Threads bei 200 Strömen (Flow-Messungen mit suspendierendem Backend, Blockade nur beim Iterator). Nicht gemessen: ein blockierendes Backend hinter dem `Flow`, echte Query-Engine oder Treiber, breitere Zeilen, LOBs und PLP, OUTPUT-Parameter im Strom, Antworten eines echten SQL Servers, MARS, Transaktionen, andere Clients.
