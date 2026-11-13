# Review-Report: slice-request-failed-mit-request-id, zweiter Lauf (Nachbesserung) — 2026-11-13

**Review-Art:** Code, Folgelauf nach [erstem Report](2026-11-13-3029d9c8-review-slice-request-failed-mit-request-id.md); gegen Plan, ADR und Hard Rules, nicht gegen die DoD. Nur die Commits nach dem Report-Stand.

**Gegenstand:** Diff `ff6b95d0..646bc0ad` im Zweig `worktree-agent-ad732f1155c96c6cd`: `b786b860` (Quelltext `RequestLoop.kt`, `ErrorMapper.kt` nur KDoc, Tests `AbandonedFaultRequestIdTest` neu und `RequestFailedRequestIdTest` umgebaut, Spezifikation, Nutzer-Doku, Plan §3) und `646bc0ad` (nur `docs/plan/spec-abdeckung.md`, neu erzeugt).

**Skill:** `.harness/skills/reviewer.md` @ `646bc0ad` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-13

**Eingangs-Kontext:** erster Report (F-1, F-4, F-5), Slice `slice-request-failed-mit-request-id` §1 und §3 samt Nacharbeit-Absatz, [ADR-0020](../plan/adr/0020-logformat-und-observability-schnittstelle.md), [ADR-0027](../plan/adr/0027-abbruch-und-zuruecksetzen.md) (Abbruch), `SPEC-017`, `SPEC-018`, `SPEC-048`, `SPEC-052`, Spezifikation Abschnitt 4, `AGENTS.md` §3.7 und §3.8.

## Selbst gefahren

Alle Mutationen in einer Kopie des Worktrees im Scratchpad (Dockerfile-Stage `test`, auf die zwei Testklassen `AbandonedFaultRequestIdTest` und `RequestFailedRequestIdTest` verengt); der Worktree blieb unverändert.

| Lauf | Ergebnis |
|---|---|
| Ausgangsstand, beide Klassen | grün, 63 s |
| M-A: `if (!abandoned)` im `finally` von `answer` entfernt (Kennung wird immer gelöscht) | `AbandonedFaultRequestIdTest` rot: erwartet `requestId=r1`, war `requestId=null`; `RequestFailedRequestIdTest` grün; 63 s |
| M-B: `abandoned = end is RunEnd.Abandoned` in `execute` entfernt | `AbandonedFaultRequestIdTest` rot, `RequestFailedRequestIdTest` grün; 61 s |
| M-C: `abandoned = end is RunEnd.Abandoned` in `answerRelease` entfernt | **alles grün**, 64 s (siehe R2-1) |
| `make docs-check` | siehe unten, vor dem Commit dieses Reports |

Gesamtlaufzeit rund 10 Minuten; größte Posten: vier Docker-Läufe der Stage `test` (je 59 bis 64 s, davon rund 59 s Gradle). Kein `make clean-build`, kein `make e2e`, keine Last, ein Gradle-Lauf zugleich.

## Prüfung der Schwerpunkte

1. **F-1 richtig und vollständig (Pfade am Code gelesen).** `RunEnd.Abandoned` entsteht nur in `AbortableRun.supervise` (`!ended` nach Abbruch oder Auslöser `Gone` ohne Ende) und wird nur in `RunConclusion.conclude` verbraucht: dort `live.abandon(CANCEL_TIMEOUT)`, das den Kanal schließt, dann den Zustand auf `CLOSED` setzt und `null` liefert. `RequestLoop.step` macht daraus `false` (Zustand `CLOSED`), `run()` endet; `answer` wird nicht wieder gerufen. Die Behauptung „danach folgt keine weitere Anfrage“ trägt am Code, und `AbandonedFaultRequestIdTest` hält sie mit `serving.join()` vor der Freigabe des Backends. Eine Kollision mit einer Folgeanfrage ist damit nicht möglich; selbst bei einer künftig offenen Verbindung setzt `answer` die Kennung neu und setzt `abandoned` zurück. Der `ErrorMapper` ist je Verbindung gebaut (`ConnectionWiring`), ein Mapping über Verbindungen hinweg gibt es nicht. Die übrigen Wege (Cancel-Antwort `RunEnd.Cut`, Frist, Ende der Gegenstelle mit beendetem Lauf `Disconnected`, mehrere Nachrichten in Folge) enden mit `ended == true`; dort kehrt das Backend vor dem Löschen zurück, kein später Fehler. Eine Lücke bleibt am Test, nicht am Code: R2-1.
2. **`AbandonedFaultRequestIdTest` trägt seine Zusage aus dem richtigen Grund.** Die Zeit steuert `SteeredPause` (`pause.fire(5.seconds)`), das Zurückkehren des Backends ein `CompletableDeferred` (`withContext(NonCancellable) { gate.await() }`), das Warten auf den Eintrag ein `yield`-Kreisen (`awaitUntil`), kein Wanduhr-Warten. Rot ohne den Fix gemessen (M-A, M-B). Nebenläufigkeit: `ErrorMapper.requestId` ist `@Volatile` und wird vom Strang der Schleife geschrieben, von der abgelösten Coroutine gelesen; `abandoned` ist nicht `@Volatile`, wird aber nur von der Schleife (in `execute`, `answerRelease`, `answer`) geschrieben und gelesen, über Suspendierungspunkte derselben Coroutine; das genügt.
3. **Begründung „mehrere Signaturen“.** Die Zahl fehlt im Plan. Von mir gezählt: 13 Aufrufe von `errors.*` außerhalb von `ErrorMapper.kt` in 8 Dateien (`RequestLoop`, `RunConclusion`, `AbortedAnswer`, `UnprepareBatch`, `BulkCopyProbe`, `ResponseSequencer`, `ResponseRun`, `ConnectionWiring`), dazu `sequencerMapping()` als gespeicherte Lambda. Die Begründung trägt damit so weit, wie sie reicht; R2-3 hält die fehlende Zahl fest. Das Zustandsfeld ist mit dem Fix nicht fragiler im Sinne eines neuen Fehlerpfads, hängt aber nun an zwei Zuständen (`errors.requestId`, `abandoned`) und an der Invariante „Aufgeben schließt die Verbindung“, die nur KDoc und der Test tragen.
4. **Spezifikation und Nutzer-Doku.** Spezifikation Abschnitt 4 und `docs/user/referenz-ereignisse.md` nennen denselben Zusatz (später Fehler eines Backends, das den Abbruch ignoriert, trägt die Kennung; Test `AbandonedFaultRequestIdTest`) und sind im Umfang nicht breiter als der Beleg (ein Backend-Fehler als Ausnahme im Pfad `execute`; der Pfad `answerRelease` ist nicht belegt, aber auch nicht genannt). Grep nach „keiner Anfrage“, „keine Anfrage“, „heute keinen“, „löscht“, „nur während einer Anfrage“ in `spec/`, `docs/user/`, `docs/plan/`, `src/`, `harness/`: zweite Fundstelle der alten Lesart nur in R2-2 (KDoc `ErrorLogEntry`, `TelemetryEvent.requestId`, Spezifikation und Doku selbst); die übrigen Treffer (`BulkDispatch`, Passwortlöschen, `ManagedConnection`) gehören nicht zur Aussage.
5. **F-4.** `RequestFailedRequestIdTest` fährt fünf Anfragen mit der Voreinstellung 30 s; der Ausgang hängt nicht an der Last (keine Frist läuft ab). Der Frist-Fall ist ein eigener Test mit einer Anfrage, `QUERY_TIMEOUT` 1 s, das Backend wartet auf einen Latch, den der Test erst am Ende löst; der Ablauf tritt bei jeder Last ein, nur später. Beide Klassen grün in M-Ausgangslauf.
6. **Öffentliche Fläche.** Von Hand gelesen: `RequestLoop` und `ErrorMapper` sind `internal`, im Diff ändern sich nur ein privates Feld, ein Zugriff auf ein `internal var` und KDoc; keine Änderung an `src/integrator`, `domain` oder `TelemetryEvent`. Unverändert.

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| R2-1 | MEDIUM | `abandoned = end is RunEnd.Abandoned` in `answerRelease` (Freigabe von Handles per `sp_unprepare`, ebenfalls unter `AbortableRun`) ist neu, und seine Entfernung lässt alle Tests grün (M-C, 64 s); nur der Zweig in `execute` ist belegt. Failure-Szenario: ein Client liest die Antwort einer Freigabe nicht, der Lauf wird nach `CANCEL_TIMEOUT` aufgegeben, ein später Fehler im abgelösten Lauf (etwa ein Schreibfehler der Senke der Antwort) trüge `requestId = null`, obwohl er zu dieser Anfrage gehört; Spezifikation und Doku nennen den Fall nicht ausdrücklich, aber „nur während einer Anfrage“ gilt für ihn ebenso unbelegt wie vor dem ersten Report. | `AGENTS.md` §3.8; Reviewer-Skill (Eigencode ohne roten Test) | `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/RequestLoop.kt` · `abandoned = end is RunEnd.Abandoned` (zweites Vorkommen, vor `conclusion.conclude(end, reply, ResponseForm.BATCH)`) | ja — Mutation M-C | Eigencode, dessen Entfernung keinen Test rot macht |
| R2-2 | LOW | Die Fortschreibung stellt den neuen Satz neben die alte Aussage, ohne sie anzupassen: Spezifikation Abschnitt 4 und `referenz-ereignisse.md` sagen weiter „der Server ruft das Fehler-Mapping nur während einer Anfrage“, der neue Satz beschreibt einen Aufruf nach dem Ende von `answer`; die KDoc von `ErrorLogEntry` („`null`, wo keine Anfrage läuft“) und `TelemetryEvent.requestId` („`null`, wo keine Anfrage läuft“) ist unverändert, die Anfrage „läuft“ beim späten Fehler aus Sicht der Schleife nicht mehr. Die Aussagen widersprechen sich nicht in der Sache, aber im Wortlaut. | Reviewer-Skill (Fortschreibung lässt die zweite Fundstelle stehen); `AGENTS.md` §3.8 | `spec/spezifikation.md` · „der Server ruft das Fehler-Mapping nur während einer Anfrage“; `src/main/kotlin/io/github/pt9912/tdswire/protocol/error/ErrorMapper.kt` · „`null`, wo keine Anfrage läuft“ | nein | Fortschreibung lässt die zweite Fundstelle stehen |
| R2-3 | INFO | Die verworfene Alternative „Kennung am Lauf statt am Mapper“ nennt „mehrere Signaturen“ ohne Zahl. Die Begründung trägt (13 Aufrufstellen in 8 Dateien, `sequencerMapping()` als gespeicherte Lambda), steht im Plan aber ohne Messzahl. | `AGENTS.md` §3.8 | `docs/plan/planning/in-progress/slice-request-failed-mit-request-id.md` · „eine Änderung an mehreren Signaturen für denselben Wert“ | ja — Grep `errors\.` in `src/main` | Zustandsfeld statt Parameter (F-5 des ersten Laufs, Rand benannt) |
| R2-4 | INFO | Die Invariante „Aufgeben schließt die Verbindung, danach folgt keine Anfrage“ trägt am Code über `RunConclusion` und `HandshakeSession.abandon`; fällt sie künftig (eine offene Verbindung nach dem Aufgeben), bliebe eine spät fehlende Kennung der Anfrage am Feld hängen, bis `answer` sie ersetzt. Heute kein Fehlerpfad; Hinweis für den Verifier und künftige Änderer. | [ADR-0027](../plan/adr/0027-abbruch-und-zuruecksetzen.md) | `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/RunConclusion.kt` · `live.abandon(CloseReason.CANCEL_TIMEOUT)` | nein | Zustandsfeld statt Parameter |

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `src/main/.../protocol/dispatch/RequestLoop.kt` (Pfade nach `Abandoned`, Cancel, Frist, Verbindungsende, Folgeanfrage) | geprüft, ohne Befund außer R2-1 und R2-4 |
| `src/main/.../protocol/error/ErrorMapper.kt` | geprüft, ohne Befund außer R2-2: nur KDoc geändert, ein `ErrorMapper` je Verbindung |
| `src/test/.../AbandonedFaultRequestIdTest.kt` | geprüft, ohne Befund: gesteuerte Zeit und Latch, kein Wanduhr-Warten, rot ohne Fix (M-A, M-B), Wächter gegen `null` und einen zweiten Eintrag |
| `src/test/.../RequestFailedRequestIdTest.kt` | geprüft, ohne Befund: keine Lastabhängigkeit der fünf Anfragen, Frist-Fall getrennt mit Latch |
| `spec/spezifikation.md`, `docs/user/referenz-ereignisse.md` | geprüft, ohne Befund außer R2-2: gleichlautender Zusatz, nicht breiter als der Beleg; `docs/user/einbettung-und-betrieb.md` Aufgabe 10 (Zeile 624) bleibt gültig |
| `docs/plan/planning/in-progress/slice-request-failed-mit-request-id.md` | geprüft, ohne Befund außer R2-3: Tabelle nennt `ErrorAnswers.kt` und die geänderten Dateien, Spec-Kopf nennt `SPEC-048`, `SPEC-052`; keine Haken gesetzt |
| `docs/plan/spec-abdeckung.md` | geprüft, ohne Befund: nur Matrixzeilen des neuen Tests |
| Öffentliche Fläche (`src/integrator`, `domain`, `TelemetryEvent`) | geprüft, ohne Befund: unverändert, Mitglieder von Hand gelesen |
| Hard Rules (`@Suppress`, Host statt Docker, Schichten, Accepted-ADR) | geprüft, ohne Befund |

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 1 |
| LOW | 1 |
| INFO | 2 |

**Finding-Klassen dieses Laufs:** Eigencode, dessen Entfernung keinen Test rot macht · Fortschreibung lässt die zweite Fundstelle stehen · Zustandsfeld statt Parameter.

## Verdikt

**F-1 des ersten Reports ist aufgehoben** (Fix richtig für den Pfad `execute`, Test rot ohne Fix gemessen); F-5 bleibt INFO mit benanntem Rand (R2-4). **Merge-blockierend:** ja nach Skill-Maßstab wegen R2-1 (MEDIUM, Mutation M-C grün); das Finding entfällt, wenn ein Test den Zweig in `answerRelease` trägt oder die Zusage ihn nicht mehr einschließt. HIGH liegt nicht vor.

**Übergabe:** Findings an den Implementer; die Klassen in die Slice-Closure §7. Dieser Report ersetzt keine Verifikation.
