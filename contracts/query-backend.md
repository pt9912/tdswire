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
- Ein Backend, das blockiert, braucht einen Thread je laufendem Aufruf und sorgt selbst für Abbruch (im Spike an einem fingierten Backend gemessen, nicht an einem echten Treiber).

## Offen

- Fehlersemantik im Strom: Teilzeilen, dann `Failure` und damit ERROR und DONE, oder Verbindungsende (`SPEC-031`).
- Prüfung der Ereignisreihenfolge im Sequencer (Zeile vor `ResultStart`, doppeltes `ResultEnd`): im Spike nicht gebaut.
- Grenze für „kleines“ Ergebnis (fertige Liste), Zeilendarstellung, Frist (`QUERY_TIMEOUT`) im Vertrag.

## Gemessen

Siehe die Messzeilen in [ADR-0006](../docs/plan/adr/0006-backend-vertrag-und-streaming.md): Heap, erste Zeile, Durchsatz, langsamer Leser, Fehler nach der ersten Zeile, Mehrfach-Ergebnisse, Cancel, Threads bei 200 Strömen. Nicht gemessen: echte Query-Engine oder Treiber, breitere Zeilen, LOBs und PLP, OUTPUT-Parameter im Strom, Antworten eines echten SQL Servers, MARS, Transaktionen, andere Clients.
