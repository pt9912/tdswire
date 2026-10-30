# Review-Report: slice-fehler-zustandsuebergang (Merge ee0a6c7) — 2026-10-30

**Review-Art:** Code (Test-Diff) gegen Plan, Entscheidungen und Hard Rules. Gegen die DoD wurde nicht geprüft, das ist Sache des Verifiers.

**Gegenstand:** `git diff ee0a6c7^1 ee0a6c7`: `src/test/kotlin/io/github/pt9912/tdswire/protocol/dispatch/DispatchFateTest.kt` (neu, 4 Tests) und der Slice-Plan `slice-fehler-zustandsuebergang` (§3 Ist-Zustand, §6 ein Punkt). Produktivcode unverändert (bestätigt: der Diff berührt `src/main` nicht).

**Skill:** `.harness/skills/reviewer.md` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-10-30

**Eingangs-Kontext:**

- Slice-Plan `slice-fehler-zustandsuebergang` (§1 Ziel, §2 DoD nur zur Einordnung, §3, §6)
- [ADR-0007](../plan/adr/0007-fehler-und-attention.md), [ADR-0004](../plan/adr/0004-session-zustandsautomat.md)
- [TDS-FA-06](../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände), [TDS-QA-07](../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität)
- [SPEC-001](../../spec/spezifikation.md#spec-001--zustandsautomat-einer-verbindung), [SPEC-029](../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung), [SPEC-031](../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität), SPEC-011 bis SPEC-016 und SPEC-048 (ohne eigenen Anker in der Spezifikation, nur im Text)
- `AGENTS.md` §3.1, §3.2, §3.6, §3.7, §3.8; Nachbartests `DispatchFailureTest`, `DispatchClosingTest`, `SqlBatchDispatchTest`

## Vorgehen und Messung

Die Mutanten liefen in Docker (`docker build --target test`, nur die Tests des Pakets `protocol.dispatch`, eine Kopie des Dockerfiles im Scratchpad, nichts gemountet, ein Lauf zugleich, je Mutant ein Lauf). Nach jedem Lauf wurde der Quelltext per `git checkout` zurückgesetzt; `git status` ist sauber. Baseline: alle Tests des Pakets grün.

| Mutant | Eingriff | Rot in |
|---|---|---|
| M1 | ein `BufferedResponse` für alle Anfragen (Feld der Schleife) | `DispatchFateTest` (Test 1, Test 2), `SqlBatchDispatchTest` (zwei Anfragen in Folge) |
| M2 | `fateOf`: `FAILED_CLOSE_CONNECTION` gibt `OPEN` | `DispatchFateTest` (Test 3), `DispatchFailureTest` (schließender Ausgang), `DispatchClosingTest` (Ausnahme) |
| M3 | `ResponseRun.report` gibt `false` zurück (Strom wird nach dem Fehler weitergelesen) | nur `DispatchFateTest` (Test 3) |
| M4 | `ResponseRun.write` gibt nach Ablehnung `true` | nur `DispatchFateTest` (Test 4) |
| M5 | Reihenfolge DONE vor ERROR | sechs Tests, darunter Test 1 und Test 3 |
| M6 | Antwort nach `CLOSE_AFTER_REPLY` um ein zweites DONE ergänzt | nur `DispatchFateTest` (Test 3) |
| M7 | `fateOf`: `FAILED` gibt `CLOSE_AFTER_REPLY` (Class 16 schließt) | Test 1, Test 2, `DispatchFailureTest` (Fehler vor jeder Zeile) |
| M8 | `fateOf`: `CLOSE_WITHOUT_COMPLETION` gibt `CLOSE_AFTER_REPLY` (Antwort trotz Ablehnung) | Test 4, `DispatchClosingTest` (Senke lehnt ab) |
| M3c | eigener Mutant: `report` gibt nur bei schließendem Fehler `false`, bei Class 16 `true` | keiner: **überlebt** |

M1 bis M4 des Implementers sind nachgefahren und fallen wie behauptet; M3 und M4 und M6 fallen nur an `DispatchFateTest`. Der Zustand nach Fehler (nicht `READY`) fällt über die Antwortanzahl, nicht über die Zustandsprüfung (siehe F-2).

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| F-1 | MEDIUM | Bei Class 16 steht hinter dem Fehler kein weiteres Ereignis im Strom (Test 1 und Test 2 enden mit `Failure`). Der Mutant M3c (der Sequenzierer liest nach einem offenen Fehler weiter) bleibt grün. Szenario: Ein Backend meldet nach `Failure` ein `ResultStart`; liest der Sequenzierer weiter, folgt ein COLMETADATA hinter dem DONE mit Fehlerstatus und die nächste Antwort wird dem falschen Ergebnis zugeordnet ([TDS-QA-07](../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität)). Die Variation „hinter dem Fehler stehen weitere Ereignisse“ trägt nur der Class-20-Test. | [TDS-QA-07](../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität), [SPEC-031](../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität) | `DispatchFateTest.kt` · `val failures = mapOf(` und `rowsThenFailure` | ja: Mutant M3c, `make test` | Offener Fehler ohne Folgeereignisse im Strom getestet |
| F-2 | LOW | Die Zusicherung trägt die Meldung „Zustand vor dem Lesen der Folgeanfrage“, prüft aber `states[2]`. Der Haken `onReceive` wird nach dem Login gesetzt, also ist Index 0 der Zustand vor der ersten Anfrage, Index 1 der vor der Folgeanfrage und Index 2 der vor dem Ende des Stroms (nach der erfolgreichen zweiten Anfrage). Die Aussage ist breiter als der Beleg; ein Zustand nach dem Fehler, der nicht `READY` ist, fällt nur über die Antwortanzahl, weil die Schleife dann endet. | `AGENTS.md` §3.8 (Aussage trägt höchstens so weit wie ihr Beleg) | `DispatchFateTest.kt` · `states[2], "$name: Zustand vor dem Lesen der Folgeanfrage"` | ja: Index lesen, Reihenfolge der `receive`-Aufrufe in `FakeHandshakeChannel` | Zustandsprüfung an falschem Index |
| F-3 | LOW | Die Variation in Test 3 ist schwächer, als der Kommentar sagt: „fremder Code“ (`NOPE`) und `TDS_LOGIN_FAILED` werden beide auf `TDS_INTERNAL_ERROR` abgebildet und laufen denselben Pfad; es gibt nur einen Fall. Die Aussage „Eingabe variiert“ gilt für die Strom-Eingabe nicht. | `AGENTS.md` §3.8 | `DispatchFateTest.kt` · `fremder Code und ein Code, den nur der Server selbst erzeugt` | nein (Bewertung der Variation) | Variation behauptet, ein Pfad |
| F-4 | LOW | Test 1 führt `TDS_SIM_INVALID_VALUE` als „anderer Code“ der Class 16. Dessen Class und Folge sind laut `ErrorCode` eine Voreinstellung, keine Setzung des Nutzers; der Slice-Plan §6 sagt nur zur schließenden Folge, dass der Test ausschließlich vom Nutzer gesetzte Codes nutzt, und lässt die offene Folge unerwähnt. Der Klassenkopf nennt „Setzung des Nutzers oder Festlegung“, nicht die Voreinstellung. | `AGENTS.md` §3.8 | `DispatchFateTest.kt` · `"anderer Code" to listOf<QueryEvent>(QueryEvent.Failure("TDS_SIM_INVALID_VALUE", "x"))` | nein | Herkunftsstufe eines Testcodes nicht benannt |
| F-5 | LOW | Planabweichung unvollständig benannt: Die Tabelle §3 führt weiter `protocol/session/` (update und neu) und `spec/spezifikation.md` (update) als Änderungsarten. Der neue Absatz „Ist-Zustand beim Eintritt“ nennt den Pfad `dispatch/` und dass Produktivcode nicht zu ändern ist, streicht oder vermerkt aber die drei Tabellenzeilen nicht. Dass die Spec-Zeile nicht erfolgt, weil die vier Punkte offen sind, steht nirgends bei der Zeile. | Maintainability (Planpflege), `AGENTS.md` §3.7 | `slice-fehler-zustandsuebergang.md` · `Ist-Zustand beim Eintritt (gemessen gegen` | nein | Plan-Tabelle und Ist-Absatz widersprechen sich |
| F-6 | LOW | In §6 fehlt dem neuen Punkt „Stand der Umsetzung der vier Zustandspunkte“ der Ausgang. Der Punkt ist zugleich der Träger der DoD-Offenhaltung; ohne `— **Ausgang:**` trägt er kein Ende, obwohl der Plan jedem Risiko genau einen Ausgang verlangt. Der erste Punkt („Wer wertet `fateOf` aus“) steht weiter auf „weiter offen: → `slice-sql-batch-dispatch` §2“, obwohl dieser Slice in `done/` liegt und die Schleife `fateOf` liest; der Verweis zeigt auf einen erledigten Träger. | Maintainability (Slice-Plan §6), `AGENTS.md` §3.7 | `slice-fehler-zustandsuebergang.md` · `Stand der Umsetzung der vier Zustandspunkte, ohne Entscheidung des Nutzers` | nein | Risiko ohne Ausgang, Ausgang veraltet |
| F-7 | INFO | Überschneidung mit Bestandstests: Test 4 (k=1,2) und `DispatchClosingTest` „Senke lehnt ab“, Test 3 und `DispatchFailureTest` „schließender Ausgang“, Test 1 („vor der ersten Zeile“) und `DispatchFailureTest` „Fehler vor jeder Zeile“ prüfen Teile desselben. Der Mehrwert ist belegt: M3, M4 und M6 fallen nur an dieser Datei (Weiterlesen nach Fehler, Annahme nach Ablehnung, zweites DONE), dazu die Folgeanfrage nach Fehler mit Handvektor-Bytes und k=3..5. Eine Doppelung ohne eigenen Beleg ist es nicht. | Maintainability | `DispatchFateTest.kt` | ja: Mutanten M3, M4, M6 | — |
| F-8 | INFO | Der Slice liefert keinen Produktivcode; DoD-Punkt 1 („Anschluss des Mappings“) und die Rückfall-Kante „Slice zu groß“ stehen damit auf einem Test-Nachtrag zu `slice-sql-batch-dispatch`. Der Punkt 3 der DoD (vier Zustandspunkte) hängt an Nutzerentscheidungen, die der Slice nicht herbeiführt; er blockiert die Closure unabhängig vom Code. Ein Schnitt (Entscheidungen als eigener Gegenstand, Beleg-Slice für die Folge) wäre Sache des Planers; hier nur benannt. Der Slice führt den Zustand ehrlich: kein Punkt wird als entschieden gesetzt. | Maintainability (Planschnitt) | `slice-fehler-zustandsuebergang.md` · `Der Slice setzt keinen davon als entschieden` | nein | Slice-Schnitt hängt an offener Nutzerentscheidung |
| F-9 | INFO | Nicht getestet, aber im Ziel genannt: `TDS_SHUTDOWN` (Class ≥ 20, schließt) und die drei Codes ohne Gruppe (`TDS_UNSUPPORTED_TYPE`, `TDS_SIM_UNKNOWN_OBJECT`, `TDS_TIMEOUT`). `TDS_SHUTDOWN` ist ein Code des Servers und über `Failure` nicht erreichbar; die drei anderen sind Festlegung und im Slice als solche ausgespart. `DispatchFailureTest` deckt `TDS_TIMEOUT` und Typfehler ab. | [ADR-0007](../plan/adr/0007-fehler-und-attention.md) | `DispatchFateTest.kt` · `listOf("NOPE", "TDS_LOGIN_FAILED")` | nein | — |

## Antworten auf die Schwerpunkte

1. **Tragen die Tests, was behauptet wird?** Test 1 (Handvektor `oneRowReply` aus `BatchVectors`, genau ein ERROR, DONE mit Fehlerstatus; Variation vor der ersten Zeile, nach Teilzeilen, anderer Code): trägt für die Folgeanfrage, M1 und M7 fallen; Lücke F-1 (kein Folgeereignis hinter dem Fehler), Schwäche F-2. Test 2 (fünf Anfragen, Zähler 5, Fehlerzahl je Antwort): trägt M1 und M7; die fünf Antworten sind nur an den Erfolgsfällen byteweise belegt, die Fehlerantworten strukturell über den Dekoder. Test 3 (Class 20 nach Teilzeilen): trägt genau eine Fehlerantwort, ein Leseaufruf, `CLOSED`; M2, M3, M5, M6 fallen. Test 4 (k=1..5): trägt `offered == k` und keine Antwort; M4 und M8 fallen. Auch die Zahl 5 der Token (ein `MORE`-DONE, zweites COLMETADATA) stimmt mit dem Ablauf überein.
2. **Mutanten:** siehe Tabelle; M1 bis M4 fallen wie vom Implementer behauptet, die eigenen M5 bis M8 ebenfalls. M3c überlebt (F-1).
3. **Herkunftsstufen:** nichts am echten Server oder Referenzclient gemessen, und der Klassenkopf sagt das. Handvektoren: nur Test 1 und 2 für die Erfolgsantwort. Selbstkonsistenz: Strukturprüfungen mit dem Test-Dekoder. Festlegung: Verbindungsfolge nach `ErrorMapper`. Die Nummer `50007`, Class 20, State 1 in Test 3 ist von Hand eingetragen und stimmt mit `ErrorCode` überein. Die Class-20-Folge für `TDS_INTERNAL_ERROR` ist Nutzer-Setzung; die Grundregel A für die drei Codes ohne Gruppe ist als Festlegung ausgespart. Kennzeichnung korrekt bis auf F-3 und F-4.
4. **Vier Zustandspunkte:** ehrlich als offen geführt (§6, kein Punkt entschieden, DoD-Häkchen nicht gesetzt). Fehlt: Ausgang und die Aktualisierung des Pfads in der Plan-Tabelle (F-5, F-6). Die Aussagen im neuen §6-Punkt (unzulässiger Übergang schließt, `RESETCONNECTION` nicht ausgewertet) sind in `HandshakeMatrix` und `HandshakeEvent` belegt; „der Port kennt keine Transaktion“ ist eine Bestandsaussage über den Port, die der Diff nicht mit einem Test stützt.
5. **Doppelung:** siehe F-7, kein Befund.
6. **`dispatch/` statt `session/`:** der Test liegt neben dem Code, den er prüft (`RequestLoop`), und neben den Nachbartests; der Ist-Absatz nennt den Pfad, die Tabelle zieht nicht nach (F-5). Kein Produktfehler.

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `src/main` (Diff) | unverändert, geprüft ohne Befund |
| `@Suppress` / `@SuppressWarnings` in `DispatchFateTest.kt` (`AGENTS.md` §3.2) | geprüft, ohne Befund |
| Docker-only (`AGENTS.md` §3.1), Gates nicht gelockert (§3.6), `config/detekt` | geprüft, ohne Befund |
| Kommentare in `DispatchFateTest.kt` (§3.7) | geprüft, ohne Befund (Zusage, Kopplung, Abgrenzung; keine verworfene Alternative) |
| Schichtregeln (Importe des Tests) | geprüft, ohne Befund |
| `docs/plan/planning/in-progress/slice-fehler-zustandsuebergang.md` Kennungen und Verweise | geprüft, außer F-5 und F-6 ohne Befund |
| `spec/`, `docs/plan/adr/` | nicht Teil des Diffs, unberührt |

## Summary

Kein HIGH. Ein MEDIUM (F-1: Fehler der Class 16 ohne Folgeereignis im Strom; Mutant M3c überlebt), fünf LOW (F-2 bis F-6), drei INFO. Die vier Tests tragen den Kern des Slice; M3, M4 und M6 fallen nur an dieser Datei, die Datei ist deshalb keine Doppelung. Die vier Zustandspunkte sind offen geführt, was der Plan verlangt, aber §6 braucht noch Ausgänge.
