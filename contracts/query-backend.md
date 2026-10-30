# Query-Backend

Vertrag zwischen Protokollkern und Backend (`TDS-FA-08.a`, `SPEC-002`, `SPEC-022`). Der Protokollkern erzeugt einen `QueryRequest` ohne TDS-Bytes und liest das Ergebnis als Strom von Ereignissen; das Backend kennt weder Pakete noch Tokens. Die Typen und Schnittstellen stehen als Kotlin-Quelltext (Abschnitt „Signatur“); alles darüber hinaus ist ein **Entwurf**. Gemessen ist das Ergebnismodell an einem Generator-Backend mit drei Spalten, nicht der Kotlin-Vertrag.

## Aufruf

| Teil | Inhalt |
|---|---|
| `execute(QueryRequest)` | suspendierend; liefert eine `QueryExecution` mit dem Strom `events` |
| `QueryRequest` | Sitzungs- und Anfragekennung, SQL-Text, typisierte Parameter, optionale Frist (`SPEC-002`) |
| `cancel(sessionId, requestId)` | suspendierend; beendet die laufende Operation |
| `QueryExecution.close()` | schließt Zeilenquelle und Ressourcen der Operation |

## Signatur

Stand des Quelltexts: Setzung des Aufrufers (2026-10-30), nicht gemessen.

| Typ | Package | Anmerkung |
|---|---|---|
| `QueryRequest`, `QueryParameter`, `ParameterDirection`, `QueryEvent` (sieben Ereignisse), `ResultColumn`, `SqlType` | `domain` | reine Modelle; `domain` importiert keine Fremdbibliothek |
| `QueryBackend`, `QueryExecution` | `port` | tragen den `Flow`; `port` importiert `domain`, die Standardbibliothek und `kotlinx.coroutines` |

- **Fehler beim Start:** `execute()` wirft für Fehler des Backends keine Ausnahme; ein Fehler beim Start (zum Beispiel Backend nicht erreichbar) ist ein `Failure` im Strom `events`. Die Abbruchausnahme der Coroutine bleibt davon unberührt. Setzung, nicht gemessen.
- **`SqlType`** (in `SPEC-002` nicht definiert, Spec-Lücke): `Int32`, `Int64`, `Bool`, `UnicodeText`, `Decimal(precision, scale)` mit `precision >= 1` und `0 <= scale <= precision`, `Date`, `Time`, `DateTime` (das Modell trägt keine Zone) — der Mindestkatalog von `SPEC-003`. Breiten unter 32 Bit, Gleitkomma, Geld, GUID und Binärtypen gehören nicht dazu. Setzung, nicht gemessen. Eine Precision über 38 ist im Modell darstellbar und scheitert in der Typabbildung mit `TDS_UNSUPPORTED_TYPE`, bevor COLMETADATA geschrieben wird (Nutzerentscheidung vom 2026-10-30, nicht gemessen). Längenlimit von `UnicodeText` (4000 UTF-16-Einheiten), Scale der Zeittypen (7) und Zeitzonenannahme (Werte ohne Zone) sind Setzungen der Typabbildung (`SPEC-003`). Offen: die JVM-Typen der Werte in `OutputParameter` und die endgültige Zeilendarstellung (`Row`, `QueryParameter`, siehe „Offen“).
- **Belegt am Test-Backend** (Test-Quelltext, suspendierend): Metadaten vor Zeilen, leeres Ergebnis mit Metadaten, `Failure` vor und nach `ResultStart`, Fehler beim Start als `Failure`, `close()` und `cancel()` erreichen die Zeilenquelle des Test-Backends. Das ist keine Aussage über blockierende oder echte Backends.

## Ergebnis

Das Ergebnis ist ein asynchroner Strom (`Flow`) von Ereignissen: `ResultStart` (Spalten), `Row`, `ResultEnd`, `UpdateCount`, `ReturnStatus`, `OutputParameter`, `Failure`. Ein Backend mit kleinem Ergebnis darf den Strom aus einer fertigen Liste erzeugen. Das Backend setzt kein `DONE_MORE`: der Response Sequencer hält ein `ResultEnd` bzw. `UpdateCount` ein Ereignis lang zurück, bis das nächste Ereignis oder das Ende bekannt ist, und setzt daraus das Bit.

## Pflichten

- Metadaten (`ResultStart`) kommen vor den Zeilen (`TDS-FA-08.a`).
- Gelesen wird nur so schnell, wie der Transport abnimmt (Backpressure); ein Backend läuft nicht beliebig voraus.
- Cancel und Disconnect beenden das Lesen und schließen die Zeilenquelle (`SPEC-029`, `SPEC-031`). Ein Backend, dessen Operation sich nicht unterbrechen lässt, hält die Sitzung bis `CANCEL_TIMEOUT` (`SPEC-033`); danach schließt der Server die Verbindung.
- Ein Backend, das blockiert, sorgt selbst für Abbruch und Thread-Verbrauch (Annahme). Gemessen ist das nur für einen blockierenden Iterator an einem fingierten Backend (Thread je laufendem Aufruf, Abbruch hängt am Interrupt), nicht für ein blockierendes Backend hinter dem `Flow` und nicht an einem echten Treiber; der Spike-Flow lief mit einem suspendierenden Backend.

## Festgelegt

- Fehlersemantik im Strom (Setzung des Nutzers, nicht am Produkt gemessen): Solange der Strom synchron ist, folgen auf Teilzeilen ein `Failure` und damit ERROR und DONE; lässt er sich nicht mehr abschließen, schließt der Server die Verbindung ohne ERROR und DONE (`SPEC-031`).
- Festlegungen des Response Sequencers (Nutzer, 2026-10-30, nicht am Produkt gemessen; der Tokenstrom ist Handvektor und Selbstkonsistenz): ein `Failure` zählt in der `DONE_MORE`-Vorschau als nächstes Ereignis (ein zurückgehaltenes `ResultEnd` oder `UpdateCount` bekommt `DONE_MORE`, danach ERROR und DONE mit Fehlerstatus). Beleg nur für das Bit vor dem ERROR: der Referenzclient gegen einen echten SQL Server zeigt `DONE[MORE|COUNT]` vor dem ERROR eines batch-abbrechenden Fehlers; am Produkt nicht gemessen. Für statement-abbrechende Fehler, bei denen der echte Server `DONE[MORE|ERR]` sendet, schreibt der Sequenzierer die Variante ohne `MORE`, weil `Failure` keine Klasse trägt); ein zurückgehaltener Abschluss wird bei Cancel oder Disconnect verworfen; ein Verstoß gegen die Ereignisreihenfolge ist ein Fall von `SPEC-048` (ERROR, DONE mit Fehlerstatus, Verbindung schließen); `ReturnStatus` und `OutputParameter` werden bis zu ihrer Abbildung mit einem Fehler der Typabbildung abgelehnt. Der Ausgang des Sequenzierers trägt die Verbindungsfolge (Fehler offen oder schließt nach der Antwort, je nach Abbildung; Reihenfolgeverstoß und Ausnahme schließen; ein nicht abschließbarer Strom schließt ohne Antwort). Eine Ausnahme des Backends wird dem Fehler-Mapping mit der Ausnahme übergeben, damit sie ins Log geht; ihr Text erreicht den Client nie. Eine Abbruchausnahme läuft aus `execute()` wie aus dem Strom nach außen. Setzung des Implementers: ein `Failure` beendet den Lauf, spätere Ereignisse werden nicht gelesen; ein Strom ohne Ereignis endet in einem DONE ohne Bits; eine Ausnahme aus `execute()` oder aus dem Strom ist ein Fall von `SPEC-048`.

## Offen

- Unterscheidung statement- und batch-abbrechender Fehler im Strom, die `Failure` nicht trägt: der Sequencer schreibt eine Variante (ERROR und DONE mit Fehlerstatus, Lauf beendet); die Verbindungsfolge nach Severity und die Abbildung von `Failure.code` bleiben Sache der Fehlerabbildung.
- Herkunft der Befehlskennung (`curCmd`) der DONE-Tokens: der Sequencer schreibt 0; der Port trägt keine Kennung (Spec-Lücke).
- Grenze für „kleines“ Ergebnis (fertige Liste), Zeilendarstellung.
- Behandlung von `OutOfMemoryError`: im Spike wurde er im Anfragepfad gefangen und als Fehler gemeldet (nur bei vollständiger Materialisierung erzeugt, nicht im `Flow`); ob das Fangen im Produktivcode tragbar ist, ist nicht gemessen.
- Verzögerung durch den zurückgehaltenen Abschluss der `DONE_MORE`-Vorschau: nicht gemessen.
- Semantik der Frist: `deadlineEpochMillis` steht im `QueryRequest` (`SPEC-002`); offen ist, wer sie durchsetzt (Backend oder Protokollkern) und was bei Ablauf geschieht (`QUERY_TIMEOUT`, `TDS_TIMEOUT`), nicht das Vorhandensein des Feldes.

## Gemessen

Siehe die Messzeilen in [ADR-0006](../docs/plan/adr/0006-backend-vertrag-und-streaming.md): Heap, erste Zeile, Durchsatz, langsamer Leser, Fehler nach der ersten Zeile, Mehrfach-Ergebnisse, Cancel, Threads bei 200 Strömen (Flow-Messungen mit suspendierendem Backend, Blockade nur beim Iterator). Nicht gemessen: ein blockierendes Backend hinter dem `Flow`, echte Query-Engine oder Treiber, breitere Zeilen, LOBs und PLP, OUTPUT-Parameter im Strom, Antworten eines echten SQL Servers, MARS, Transaktionen, andere Clients.
