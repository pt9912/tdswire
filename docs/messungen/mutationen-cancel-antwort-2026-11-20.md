# Mutationsbeleg: Antwort auf ATTENTION in zwei Nachrichten (2026-11-20)

Messprotokoll der Mutationen zum Bau von [ADR-0054](../plan/adr/0054-antwort-auf-attention-in-zwei-nachrichten.md) im Slice [slice-cancel-antwort-zwei-nachrichten-angleichen](../plan/planning/in-progress/slice-cancel-antwort-zwei-nachrichten-angleichen.md). Es sagt, welche Änderung am geprüften Code welchen Test rot machte; es sagt nicht, ob eine Anforderung belegt ist ([AGENTS.md](../../AGENTS.md) §5 Regel 7).

**Erzeuger.** Das Setz-Skript der Mutationen liegt **nicht im Repo**: je Mutation ersetzte ein Wegwerf-Skript eine Zeile der Quelle (Zeichenkette alt, Zeichenkette neu, genau ein Treffer), baute in einem Wegwerf-Image die Stage `deps` plus die Quellen und führte `gradle :test` mit den genannten Klassen aus (Filter `--tests '*<Klasse>'`), stellte die Datei danach wieder her. Die Zeile „Mutation“ unten nennt Ort und Änderung; ein Verifier wiederholt sie von Hand. Einzelläufe, ein Host, Last nicht erhoben; ein Lauf dauert 45 bis 75 s. „rot“ heißt mindestens ein Testfall des Laufs rot, „grün“ heißt alle Testfälle der genannten Klassen grün.

**Grenze.** Gemessen ist die Wirkung auf die genannten Klassen, nicht auf `make gates`. Die Mutationen R1 bis R13 stammen aus dem Review ([Report](../reviews/2026-11-19-slice-cancel-antwort-zwei-nachrichten-angleichen.md)), nicht vom Implementer; die Zeilen m4 und R4 betreffen eine Zeile von `AbortableRun.kt`, deren Diff im Slice nur Kommentare enthält (kein neuer Code).

## Runde 1: Implementer, vor dem Review (2026-11-19)

Klassen: `AttentionCancelTest`, `AttentionWireTest` (m1 bis m5); m6 `AttentionCancelTest`; m7 `AttentionCancelTest`, `RpcSequenceAbortTest`, `BulkDispatchTest`, `PreparedHandlesDispatchTest`, `UnprepareDispatchTest`; m8 `CancelTimeoutTest`, `AttentionCancelTest`; m9 `DisconnectTest`, `AttentionCancelTest`.

| Nr | Mutation (Ort) | Wirkung (gemessen) |
|---|---|---|
| m1 | `AbortedAnswer.conclude`: ATTENTION wieder als einzelnes `DONE[ATTN]` angehängt | rot, 8 von 12: sieben in `AttentionCancelTest` (unter anderem Zustandsfolge, wiederholtes ATTENTION, Paar unter einer Sperre), eines in `AttentionWireTest` |
| m2 | `inTwoMessages`: `failedDone(form, false)` statt `failedDone(form, resultOpen)` | rot, 2 von 12: `ATTENTION in STREAMING …`, `ATTENTION vor dem naechsten Token …` |
| m3 | `concludeWith`: zwei Aufrufe von `guarded` statt eines | rot, 1 von 12: `Das Paar geht unter einer Sperre hinaus` |
| m4 | `AbortableRun.watch`: `if (!won) { tray.drop(); return }` | **grün**; die Mutation lag an einem Pfad, den die Tests dieser Klassen nicht erreichen (siehe R4, dort rot) |
| m5 | `HandshakeSession.REGULAR_STOPS` ohne `CANCELLING` | rot, 2 von 12: `Ein ATTENTION waehrend CANCELLING …`, `ATTENTION vor dem naechsten Token …` |
| m6 | `AbortableRun.watch`: das zweite ATTENTION nach einem gewonnenen ATTENTION wird verworfen | rot, 2 von 10: die zwei Fälle zu wiederholtem ATTENTION |
| m7 | `inTwoMessages`: `failedDone(ResponseForm.BATCH, resultOpen)` für jede Form | rot, 3 von 52: `RpcSequenceAbortTest`, `PreparedHandlesDispatchTest`, `BulkDispatchTest` (Freigabe der Handles bleibt grün: ihre Form ist BATCH) |
| m8 | `RunConclusion`: bei `RunEnd.Abandoned` wird vor dem Schließen das Paar geschrieben | rot, 2 von 13: beide `CancelTimeoutTest`-Fälle zum Schließen nach `CANCEL_TIMEOUT` |
| m9 | `AbortableRun.hangUp`: `PeerClosed` wird nicht gemeldet | rot, 4 von 16: vier Fälle in `DisconnectTest`, darunter `Ende der Gegenstelle in CANCELLING …` |

## Runde 2: Review (2026-11-19)

Zwölf Klassen aus dem Umfeld des Abbruchs, unverändert grün (49 s). Fünf der acht Mutationen rot, die drei grünen (R11 bis R13) waren Befund F-3 des Reviews.

| Nr | Mutation (Ort) | Wirkung (gemessen) |
|---|---|---|
| R1 | `SHUTDOWN` läuft über `inTwoMessages` (Stand vor dem Nutzerentscheid vom 2026-11-20) | rot, 3 von 89 (Tests des Beendens, die das einzelne `DONE[ATTN]` prüften) |
| R2 | `complete()`: ein ATTENTION nach vollständiger Antwort wird nicht einzeln bestätigt | rot, 4 von 81 |
| R4 | `AbortableRun.watch`: ein ATTENTION, das den Übergang verliert, wird aus dem Eingang verworfen | rot, 1 von 81: `QueryTimeoutTest > Ein ATTENTION, das den Uebergang an die Frist verliert, wird danach bestaetigt` |
| R5 | `AbortableRun.watch`: kein zweites Lesen nach einem gewonnenen ATTENTION | rot, 1 von 81: `DisconnectTest > Ende der Gegenstelle in CANCELLING …` |
| R10 | `inTwoMessages`: Verbindung nach dem Paar geschlossen | rot, 6 von 81 |
| R11 | `inTwoMessages`: `else null` entfällt | grün (Befund F-3), Wirkung in Runde 3 |
| R12 | `concludeWith`: Ergebnis von `channel.send(second)` verworfen | grün (Befund F-3), Wirkung in Runde 3 |
| R13 | `concludeWith`: `guarded(emptySet())` statt `guarded(CLOSED_ONLY)` | grün (Befund F-3), Wirkung in Runde 3 |

## Runde 3: Nachbesserung (2026-11-20)

Neue Klasse `PairConclusionTest` (drei Fälle) und die Tests des Beendens im Paar. Klassen je Lauf: `PairConclusionTest` (R11 bis R13), `ShutdownDispatchTest`, `ShutdownWiringTest` (N1, N5). Ausgangslauf ohne Mutation: `PairConclusionTest`, `ShutdownDispatchTest`, `ShutdownWiringTest`, `ServerSmokeTest`, `UnprepareDispatchTest`, `AttentionCancelTest` grün (`BUILD SUCCESSFUL`, 72 s).

| Nr | Mutation (Ort) | Wirkung (gemessen) |
|---|---|---|
| R11 | `inTwoMessages`: `return RequestFinished(…)` ohne die Fallunterscheidung `else null` | rot, 2 von 3: `Scheitert die erste Nachricht des Paares …` und `Scheitert die zweite Nachricht des Paares …` (Grund des Schließens `PROTOCOL_VIOLATION` statt `WRITE_FAILED`) |
| R12 | `concludeWith`: `run { channel.send(second); true }`, das Ergebnis der zweiten Nachricht wird verworfen | rot, 1 von 3: `Scheitert die zweite Nachricht des Paares …` (Grund `PEER_CLOSED` statt `WRITE_FAILED`) |
| R13 | `concludeWith`: `guarded(emptySet())` | rot, 1 von 3: `Das Paar schreibt in CLOSED nichts` |
| N1 | `AbortedAnswer.conclude`: `SHUTDOWN` wieder als einzelnes `DONE[ATTN]` angehängt | rot, 3 von 14: `ShutdownDispatchTest > Der Ablauf der Frist beendet die Antwort mit DONE ERROR und DONE ATTN und die Verbindung`, zwei in `ShutdownWiringTest` (Ablauf der Frist, `SHUTDOWN_GRACE` 0) |
| N5 | `AbortedAnswer.conclude`: `SHUTDOWN` mit `connectionOpen = true` | rot, 1 von 14: `ShutdownDispatchTest > Der Ablauf der Frist beendet …` (Zustand `CLOSED` erwartet) |

Die Form im Beenden (Paar statt einzelnem `DONE[ATTN]`) ist Setzung des Nutzers vom 2026-11-20 und am echten Server nicht gemessen; N1 belegt nur, dass die Tests sie unterscheiden.
