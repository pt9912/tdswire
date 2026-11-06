# Review-Report: slice-cancel-referenzclient-szenario — 2026-11-06

**Review-Art:** Code (gegen Plan, ADR und Hard Rules)

**Gegenstand:** `slice-cancel-referenzclient-szenario`, Diff `1c9a85c9..9bf688b9` (Commits `c40adec8`, `1d9bfc1c`, `9bf688b9`)

**Skill:** `.harness/skills/reviewer.md` · **Modell:** Sonnet 5.5 · **Datum:** 2026-11-06

**Eingangs-Kontext:**

- `slice-cancel-referenzclient-szenario` (Plan, DoD, Risiken)
- [ADR-0027](../plan/adr/0027-abbruch-und-zuruecksetzen.md) (Entscheidungen 1 und 2, Re-Evaluierungs-Trigger), [ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md) (Testserver nur über die öffentliche Fläche), CO-004
- `TDS-FA-07`, `TDS-QA-07` (Lastenheft), SPEC-040, SPEC-036 (Form C)
- `AGENTS.md` §3.6, §3.7, §3.8

**Wiederholt (Docker, im Worktree):** `make kompatibilitaet KATEGORIE=Synchronitaet` (10 von 10 grün; 418 Zeilen nach Cancel in drei Runden, `Number 0, Class 11`; Command-Timeout 2 s: 0 Zeilen in 2005 ms, `Number -2`, Folgeabfrage nach 2011 ms; Frist 955 Zeilen in 5003 ms, `Number 50005, Class 16`; 11 Anfragen an `laufend`, 11 Cancels) und `make simulations-integration` (5 von 5 grün; 419 Zeilen nach Cancel; Quelle genau einmal geschlossen). Die Messzahlen der Matrix stimmen mit den Läufen überein (Schwankung 2006/2005 ms, 950/955 Zeilen im Rahmen von „rund“). **Nicht wiederholt:** die „Rot gesehen“-Mutationen der Matrix (Einzelläufe, `AbortableRun.watch` auf `SQL_BATCH`; `QUERY_TIMEOUT` 30 s) — ihre Wirkung ist hier weder bestätigt noch widerlegt.

---

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| F-1 | MEDIUM | Die Matrix führt in Abschnitt 7 „Anfragefehler im Batch und in der RPC“ weiter `SELECT 1` und `SELECT 1 + @p` mit `Number 50001`; der Test fragt seit diesem Diff `SELECT 2` (`SELECT 1` ist Form C und wird beantwortet, SPEC-036). Auch die Zeile „Fehler außerhalb der Formen“ in Abschnitt 7a nennt `SELECT 1` mit 50001, während `SzenarienTests.cs` bereits `SELECT 2` fragt. Die Matrix behauptet damit eine Messung, die der Test nicht mehr macht und die mit `SELECT 1` nicht zutrifft. | AGENTS.md §3.8 | `docs/plan/kompatibilitaetsmatrix.md` · „`SELECT 1` und `SELECT 1 + @p` (RPC): je `Number 50001, Class 16`“ und „`SELECT 1`: `Number 50001, Class 16`, `Unsupported SQL.`“ | ja — Lesen des Testtexts gegen die Zeile; `make matrix-zitate` prüft nur Testnamen und fängt es nicht | Matrix zitiert geänderten Testtext nicht nach |
| F-2 | MEDIUM | „Das Backend erhält je Anfrage genau einen Cancel“ (E2E-Abdeckung Stufe 7, Matrix) steht breiter als der Beleg: `cancel_je_laufende_anfrage` vergleicht nur die Summe der Cancel-Zeilen (11) mit der Summe der Anfragen an `laufend` (11). Zwei Cancels bei einer und keiner bei einer anderen Anfrage bleiben grün; die Matrix räumt „über die Kategorie, nicht je Test“ ein, die Abdeckungs-Zeile in `tools/e2e.sh` nicht. Für diese beiden Skript-Prüfungen und die Prüfung „Quelle genau einmal geschlossen“ steht keine Rot-gesehen-Mutation (die Matrix nennt nur Mutationen am Sitzungsrahmen). | AGENTS.md §3.8; Skill (Aussage breiter als Beleg; Eigencode ohne rot machende Mutation) | `tools/e2e.sh` · „das Backend erhält je Anfrage genau einen Cancel“; `tools/kompatibilitaetstest.sh` · `[ "$anfragen" -gt 0 ] && [ "$anfragen" = "$cancels" ]` | ja — Mutation: ein Backend, das einen Cancel doppelt und einen nicht meldet | Aussage „je Anfrage“ belegt nur als Summe |
| F-3 | LOW | Die Zelle „`Cancel()` und Command-Timeout zusammen“ schließt mit „gemessen ist, dass er die Folgeabfrage nicht mit einem zweiten `DONE[ATTN]` vermischt“. Der vorige Satz sagt, dass nicht beobachtet ist, ob der Client ein zweites ATTENTION sendet; sendet er keines, gibt es nichts zu vermischen. Die Folgepflicht (a) des Slice (N ATTENTION ergeben N Bestätigungen) ist damit nicht gemessen, der Wortlaut „nicht vermischt“ liest sich wie ein Befund dazu. | AGENTS.md §3.8 | `docs/plan/kompatibilitaetsmatrix.md` · „gemessen ist, dass er die Folgeabfrage nicht mit einem zweiten `DONE[ATTN]` vermischt“ | nein | Zusage ohne ihre Bedingung im selben Satz |
| F-4 | LOW | Die Geltungs-Konfiguration von CO-004 nennt für die vier Stellen weiter „nennt die Lücke“ bzw. „nennt die vier laufenden Szenarien und diesen Carveout als Bindung“, die Geschichte sagt in derselben Datei, sie seien berichtigt. Die Tabelle beschreibt einen Zustand, der nicht mehr da ist. | AGENTS.md §3.7 (Zustandsfeld nennt den Zustand) | `docs/plan/carveouts/CO-004-cancel-szenario-spec-040-fehlt.md` · „nennt die Lücke“ | ja — Lesen gegen `SPEC-040`, `tools/simulationstest.sh`, `harness/README.md` | Zustandsfeld hinter dem Stand |
| F-5 | INFO | Plan-Befund: Die DoD verlangt „der Server loggt danach keinen Handlerfehler“. Die Prüfung `Handlerfehler` ist in `tools/simulationstest.sh` und `tools/kompatibilitaetstest.sh` schon vor diesem Slice herausgenommen (Ersatz: `slice-betrieb-log-adapter-testserver`), geliefert ist stattdessen „Quelle von `laufend` genau einmal geschlossen“. Der Slice-Text ist nicht berichtigt; der Verifier trifft auf einen Wortlaut, den kein Lauf trägt. Zuständig: Planner/Architect (Wortlaut) und Verifier. | Plan (DoD Punkt 1) | `docs/plan/planning/done/slice-cancel-referenzclient-szenario.md` · „der Server loggt danach keinen Handlerfehler“ | nein | DoD-Wortlaut nicht mehr lieferbar |
| F-6 | INFO | Zum Command-Timeout-Test (2 s): Der Befund „0 Zeilen in 2005 ms“ hängt daran, dass vor dem Ablauf kein Paket mit Zeilen beim Client ankommt; bei 3 s kam das Command-Timeout nicht, die Zeilen flossen (Erkundungslauf der Matrix, „nicht untersucht“). Die Cancel-Läufe zeigen 418 Zeilen nach dem Abbruch, also einen ersten Block von rund 470 Zeilen bei 5 ms Pause. Die 2 s liegen nahe an dieser nicht erklärten Grenze: ein schnellerer Host könnte Zeilen vor 2 s liefern, der Test wäre dann rot (nicht grün aus falschem Grund). Der Erkundungslauf bei 3 s ist kein Test und nicht nachlesbar. Offen gelegt ist das in der Matrix; hier nur als Wartungsrisiko vermerkt. | Maintainability | `referenztest-dotnet/Kompatibilitaet/SynchronitaetTests.cs` · `ClientTimeoutSekunden = 2` | ja — Lauf unter Last / mit anderer Pause | Test nahe an ungeklärter Grenze |
| F-7 | INFO | Zahlen an mehreren Orten ohne Gate: „50 Zeilen vor dem Abbruch“ (`ZeilenVorDemCancel` in `SimulationBackendTests.cs`, `ZeilenVorDemAbbruch` in `SynchronitaetTests.cs`, SPEC-040, `tools/e2e.sh`), „2 s“ und „5 s“ (Test-Konstanten, `tools/kompatibilitaetstest.sh`, `tools/e2e.sh`, Matrix). Für die 5 s steht ein Kopplungskommentar auf beiden Seiten; `make kopplung-zahlen` deckt nur Heap und Text von `gross`. Die README-Zellen (`make simulations-integration`, `make kompatibilitaet`) tragen die neuen Zahlen nicht und liegen mit je 196 Zeichen unter 200. | Maintainability | `tools/kompatibilitaetstest.sh` · „FristDesServersSekunden in SynchronitaetTests.cs ist dieselbe Zahl“ | nein | Zahl an mehreren Orten ohne Gate |

## Antworten auf die gestellten Fragen

1. **Messaussagen und Belege.** 418–419 Zeilen, `Number 0/Class 11`, `-2` nach 2006 ms (Lauf: 2005 ms), 50005, Rohbefunde in Matrix und `messung:`-Zeilen des Berichts: nachvollzogen und im Lauf wiederholt. Die Messzeilen stehen nur im Testbericht des Laufs (Konvention der Matrix), nicht als abgelegtes Messprotokoll; sie sind durch Wiederholung nachlesbar. Nicht nachlesbar: der Erkundungslauf bei 3 s (F-6). Rot-gesehen-Gegenproben sind einmalige Läufe, hier nicht wiederholt; für die Skript-Prüfungen fehlen sie (F-2).
2. **`SELECT 1` → `SELECT 2`.** Gerechtfertigt: `SELECT 1` ist Form C (SPEC-036; `FixedForms.kt`, `FixedAnswers.kt`) und wird beantwortet, der Test erwartet 50001. Dass der Test auf der Basis rot war, folgt aus dem Code und der Commit-Nachricht („seit Form C gültig“); ein Lauf auf der Basis wurde weder hier noch (nach Aktenlage) vom Implementer belegt. Die Matrix zieht die Änderung nicht nach (F-1).
3. **Öffentliche Fläche ([ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md)).** `LaufendeQuelle.kt` importiert `SqlType`, `RowSource`, `SimColumn`, `SimTable` (alle `public`); `internal` betrifft nur Deklarationen im Source-Set `testserver` selbst. Kein Zugriff auf `internal` der Bibliothek.
4. **Grün aus falschem Grund.** Cancel-Tests: Der Client-Fehler `Number 0, Class 11` und der Server-Fristfehler 50005 unterscheiden sich; ein Ende durch die Frist färbt die Tests rot. Token- und Command-Timeout-Test binden die Folgeabfrage zusätzlich an `< 5 s`. Der Frist-Test fordert 4 bis 10 s. Der kombinierte Test (Cancel + Command-Timeout) fordert nur `Open` und die eigene Antwort, nicht die Art der Ausnahme; die Matrix zitiert die Nummern (0 vor, −2 gleichzeitig und danach) als Messung, im Lauf bestätigt, vom Test nicht erzwungen. Kein Test ist nach Aktenlage grün, wenn die Frist des Servers statt des Client-Cancels die Anfrage beendet, mit Ausnahme der Lücke in F-2 (Summenprüfung).
5. **DoD „Handlerfehler“.** Siehe F-5.
6. **CO-004 nicht verschoben.** Korrekt: Trigger (a) verlangt beide Slices in `done/`; die Geschichte hält „bleibt Aktiv“. Die DoD-Punkte 1 und 2 sind zu Recht nicht abgehakt (Verschieben des Carveouts gehört zur Closure; Folgepflicht (c) ATTENTION im Leerlauf ist am Referenzclient ausdrücklich „nicht belegt“, (a) nur teilweise, siehe F-3) — für den Verifier. Verbleibend: F-4.
7. **Skripte und README-Zellen.** Siehe F-7; `tools/kompatibilitaetstest.sh` ruft nur Docker (§3.1 gewahrt), das leere-Array-Idiom ist bash-sicher.

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `src/testserver/kotlin/.../LaufendeQuelle.kt`, `IntegrationServer.kt`, `KompatibilitaetsServer.kt` | geprüft, ohne Befund (kein `@Suppress`, keine `internal`-Zugriffe auf die Bibliothek, KDoc im Indikativ) |
| `referenztest-dotnet/Integration/SimulationBackendTests.cs`, `Kompatibilitaet/SynchronitaetTests.cs` (neue Tests) | geprüft, ohne Befund über F-2, F-6 hinaus; Zeitgrenzen und Ausnahmetypen siehe Frage 4 |
| `referenztest-dotnet/Kompatibilitaet/SzenarienTests.cs` | geprüft, ohne Befund |
| `tools/simulationstest.sh`, `tools/kompatibilitaetstest.sh`, `tools/e2e.sh` | geprüft, Befund F-2 (Summenprüfung), sonst ohne Befund |
| `spec/spezifikation.md` (SPEC-040) | geprüft, ohne Befund (präzisiert, erweitert das Lastenheft nicht; Fehler/Abbruch deckt `TDS-FA-07`) |
| `harness/README.md` (Sensor-Zeilen) | geprüft, ohne Befund (196 Zeichen je Zelle; die Bindung an CO-004 entfällt, der Carveout nennt die Zeilen weiter, F-4) |
| `docs/plan/e2e-abdeckung.md`, `docs/user/testserver-manuell.md` | geprüft, ohne Befund über F-2 hinaus |
| `docs/plan/kompatibilitaetsmatrix.md` | Befunde F-1, F-3; [ADR-0027](../plan/adr/0027-abbruch-und-zuruecksetzen.md)-Trigger-Aussage (Entscheidungen 1 und 2 nicht eingetreten) vom Lauf getragen, ohne Befund |
| `docs/plan/carveouts/CO-004-…` | Befund F-4 |
| `docs/plan/planning/done/slice-cancel-referenzclient-szenario.md` | Befund F-5 |
| Hard Rules §3.1, §3.2, §3.5, §3.6 | geprüft, ohne Befund (kein Gate gelockert, keine ADR geändert, keine Suppression) |

## Summary

0 HIGH, 2 MEDIUM (F-1, F-2), 2 LOW (F-3, F-4), 3 INFO (F-5 bis F-7). Die Messaussagen der Matrix tragen im wiederholten Lauf; die Re-Evaluierungs-Trigger von [ADR-0027](../plan/adr/0027-abbruch-und-zuruecksetzen.md) sind nicht eingetreten. Konflikt-Pfad nicht ausgelöst (kein HIGH, kein Rollen-Widerspruch bisher).
