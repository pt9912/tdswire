# Review-Report: slice-zweites-error-folgefehler-angleichen, Nachbesserung (Re-Review) — 2026-11-12

**Review-Art:** Code (gegen Plan, ADR und Hard Rules; nicht gegen die DoD). Re-Review nach dem Reviewer-Skill „Nachbesserung mit Code ohne zweiten Review“: gelesen werden nur die Commits nach dem Report-Stand.

**Gegenstand:** Diff `27665520..f37c39e6` im Zweig `worktree-agent-a78a494dfdd9c96ed` (ein Commit `f37c39e6`). Basis: der Erst-Review `2026-11-12-952af14c-review-slice-zweites-error-folgefehler-angleichen` (Findings F-1 bis F-6).

**Skill:** `.harness/skills/reviewer.md` @ `f37c39e6` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-12

**Eingangs-Kontext:** Slice `slice-zweites-error-folgefehler-angleichen` (Plan §3), [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), [ADR-0038](../plan/adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md), SPEC-031, SPEC-035, SPEC-036, `AGENTS.md` §3.7 und §3.8, der Erst-Report.

## Selbst gefahren

| Lauf | Ergebnis |
|---|---|
| Diff `27665520..f37c39e6` vollständig gelesen (Plan, Referenzseite, Spezifikation, `ResponseRun.kt`, `ResponseFollowUpTest.kt`, Erst-Report mit ADR-Links) | siehe Befunde |
| `RpcTrailer.statusValue` gelesen | bei `continued = true` wird die Nummer nicht gelesen (`continued -> outputs.status ?: 0`); die Nummer wirkt nur bei `continued = false` (`report()`, Zeile bleibt `(mapped.followUp ?: mapped.message).number`) |
| Grep nach der alten Lesart (`1750`, `3727`, „ERROR und DONE“, „ein ERROR“, „sendet nicht“, „nur das erste“) über `spec/`, `docs/user/`, `src/main` | keine neue zweite Fundstelle; alle Treffer zu 1750/3727 tragen das zweite ERROR |
| Test `ResponseFollowUpTest` | nicht gefahren (kein Make-Ziel für eine einzelne Klasse; der volle `make test` ist Sache von Implementer und Verifier); Bewertung aus dem Quelltext, siehe F-5 unten |
| `make docs-check` | grün vor dem Commit dieses Reports |

Gesamtlaufzeit rund 10 Minuten; größter Posten: Lesen der Spezifikationsstellen und der Greps.

## Stand der Findings des Erst-Reviews

| ID | Erst-Stufe | Stand | Beleg |
|---|---|---|---|
| F-1 | MEDIUM | behoben | `docs/user/referenz-konfiguration.md`: Satz zu den Fehlern nennt State 0 der zweiten ERROR, 1750 und 3727 unter den beobachteten Nummern (Class 16); die Zeilen 1781, 3728 und 3733 der Codetabelle tragen „danach zweites ERROR …“; die Zeile „Fehler in der Anfrage“ der Antwortformen nennt das zweite `ERROR`. Gegen `ErrorCode.kt` und SPEC-035 (Tabelle) abgeglichen: Nummern, State 0, Class 16 stimmen |
| F-2 | MEDIUM | behoben mit Rest (LOW unten) | Plan §3: Zeile 1 nennt jetzt `ErrorCode.kt`, `ErrorMapper.kt`, `ResponseRun.kt`, `ResponseSequencer.kt`, `StatementPlanner.kt`, `StructurePlanner.kt`, `StructureChecks.kt`; Zeile 2 nennt `ErrorMapperTest.kt`, `ResponseFollowUpTest.kt` (neu), die zwei simulationsseitigen Testklassen, die zwei Einbettungstests und `WireClient.kt`; `SimulationChanges.kt` ist entfernt. Abgleich mit `git diff e5e48313..f37c39e6 --stat`: jede Quell- und Testdatei steht in der Tabelle |
| F-3 | LOW | offen, nicht Gegenstand der Nachbesserung | `docs/plan/spec-abdeckung.md` ist nicht im Diff (Erzeugung per `make spec-abdeckung` steht aus) |
| F-4 | INFO | behoben, Verhalten unverändert | `ResponseRun.failure()` ruft `trailer.record(mapped.message.number, continued = true)`; die Nummer wird bei `continued = true` nie gelesen, die Antwort bleibt Byte für Byte gleich. Die Zeile in `report()` bleibt unverändert und ist durch den RETURNSTATUS-Test getragen |
| F-5 | INFO | (a) behoben, (b) behoben | (a) neuer Test `Ein abgelehntes zweites ERROR im Pfad der Anweisung schliesst ohne DONE`: `rejectAt = 2`, `FailureScope.STATEMENT`, danach ein `UpdateCount`; erwartet `CLOSE_WITHOUT_COMPLETION` und genau ein Token. Der Test wird rot, wenn der Pfad das Ergebnis von `write` ignoriert (dann stünden `held`-DONE und der Zähler in der Senke). (b) SPEC-031: „In der Antwort auf das Massenladen und im offenen Ergebnis eines `MERGE` ist die Folge nicht gemessen (Setzung: beide ERROR vor dem INFO 3621 und dem Abschluss)“; SPEC-035: „gemessen im SQL_BATCH … im RPC, im Massenladen und im `MERGE` Setzung, `SPEC-031`“ |
| F-6 | INFO | unverändert | datierte Messprotokolle, Stand der Messung; keine Aktion |

## Findings dieses Laufs

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| R-1 | LOW | Die Zeile 3 der Plan-Tabelle (§3) nennt `SPEC-035`, `SPEC-036` und das Änderungsprotokoll, nicht `SPEC-031`; der Kopf „Berührte Spec-Stellen“ nennt `SPEC-031`, und die Spezifikation ist dort im Diff (Folge-Token, Massenladen/`MERGE`-Setzung). Die Tabelle ist damit gegen den Kopf nicht vollständig. | Reviewer-Skill „Plan-Tabelle nennt die berührten Dateien nicht“ (Maintainability) | `docs/plan/planning/in-progress/slice-zweites-error-folgefehler-angleichen.md` · „`spec/spezifikation.md` (`SPEC-035`, `SPEC-036`, Änderungsprotokoll)“ | ja: Abgleich Tabelle mit Kopf | Plan-Tabelle nennt die berührten Stellen nicht |
| R-2 | INFO | `spec/spezifikation.md` führt an zwei Stellen (Strukturabweisung in der Liste der Backend-Codes, „gemessen, Form D7“; Absatz zu 3727 „im RPC gilt es als Setzung ebenso“) die Messung ohne „im SQL_BATCH“. Form D7 ist ein Batch, die Lesart ist belegt; keine Breite über den Beleg. Ohne Aktion. | `AGENTS.md` §3.8 | `spec/spezifikation.md` · „beide Class 16; gemessen, Form D7“ | nein | Bedingung nicht im selben Satz |

Die Nachbesserung trägt keinen Verstoß gegen `AGENTS.md` §3.7 (die neuen KDoc- und Spezifikationssätze sind Zusage, Kopplung oder Grenze, keine verworfene Alternative, keine Chronik) und §3.8 (jede neue Zahl und Folge ist als gemessen mit Quelle oder als Setzung gekennzeichnet).

## Negativbefund (geprüft, ohne Befund)

- `src/main/kotlin/…/protocol/response/ResponseRun.kt`: eine Zeile, ohne Verhaltensänderung — geprüft, ohne Befund
- `src/test/kotlin/…/protocol/response/ResponseFollowUpTest.kt`: neuer Test, trägt die Zusage (Wächter-Beschreibung im KDoc stimmt mit dem Quelltext) — geprüft, ohne Befund
- `docs/user/`: nur `referenz-konfiguration.md` geändert; `einbettung-und-betrieb.md` und `testserver-manuell.md` unverändert und im Erst-Review ohne Befund; Grep ohne neue zweite Fundstelle — geprüft, ohne Befund
- `spec/spezifikation.md`: keine `ADR-`/`ARC-`-Kennung im Zugefügten, keine neue Zusage ohne Lastenheft-Anker — geprüft, ohne Befund außer R-2
- `docs/reviews/` (Erst-Report): nur ADR-Links ergänzt — geprüft, ohne Befund
- Hard Rules §3.1, §3.2, §3.5, §3.6: nicht berührt — geprüft, ohne Befund
