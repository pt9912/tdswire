# Slice slice-cancel-antwort-zwei-nachrichten-angleichen: Antwort auf ATTENTION: Zwei-Nachrichten-Form des echten Servers (A-7, A-13)

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** ohne Welle (keine Closure-Bedingung außerhalb der DoD dieses Slice; Träger der Lücken A-7 und A-13 der Ausnahmeliste des Referenzabgleichs, angelegt nach Nutzerentscheidung vom 2026-11-17: „ja slices anlegen“ auf die Frage der Closure von [welle-referenzabgleich-release](../done/welle-referenzabgleich-release.md)).

**Bezug:** [TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe) (Scope: Abbruch), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) (Abbruch und Zurücksetzen, Entscheidung 1), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md)

**Berührte Spec-Stellen:** [SPEC-029](../../../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung) (Antwort auf ATTENTION: „genau ein DONE … ohne zweite Nachricht“, Setzung des Nutzers vom 2026-11-01) · [SPEC-001](../../../../spec/spezifikation.md#spec-001--zustandsautomat-einer-verbindung) (Regel „doppelte DONE-Tokens sind verboten“) · [SPEC-045](../../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig) · [Kompatibilitätsmatrix](../../kompatibilitaetsmatrix.md) Abschnitt 5 Nr. 12.

**Verantwortlich:** pt9912

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-17.

---

## 1. Ziel und Abgrenzung

**Ziel:** Der Server beantwortet ATTENTION im Stream und bei einem wartenden Befehl so, wie die Referenzinstanzen es am Draht tun (zwei Nachrichten, erste mit `DONE[ERROR]`, zweite mit `DONE[ATTN]`, ohne ERROR-Token), oder die Regeln A-7 und A-13 gelten nach Entscheidung des Nutzers als *so gewollt*.

**Ist-Stand** (gelesen am 2026-11-17; was nicht als gemessen steht, ist aus Datei oder Code gelesen):

- **Messung am Draht (geliefert):** bei `Cancel()` im Stream sendet die Referenz zwei Nachrichten (`COLMETADATA ROW+ DONE` mit `ERROR`, dann `DONE` mit `ATTN`); bei einem wartenden Befehl zwei Nachrichten `DONE[ERROR]` ohne ERROR-Token und `DONE[ATTN]`; beide Instanzen (2022, 2025) ([Messprotokoll des Referenzabgleichs](../../../messungen/referenzabgleich.md), Mitschnitt-Lauf Ergebnis 5, A-13; Messreihe W-9 aus [slice-qa-vergleichslauf-d-messungen](../done/slice-qa-vergleichslauf-d-messungen.md)). Der Server sendet eine Nachricht `DONE[ATTN]` ([SPEC-029](../../../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung), [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) Entscheidung 1; dort steht die zweite Form als „nur wenn das Referenzclient-Szenario mit der Ein-Nachricht-Form scheitert“, bedingter Aufschub, kein Ziel).
- **Regeln:** Live-Liste 2 Regeln für den Schritt `cancel-mit-folgeabfrage` (A-7: Text „A severe error occurred on the current command.  The results, if any, should be discarded.“ der Referenz, in einem Teil der Läufe mit dem Zusatz „ Operation cancelled by user.“, gegen „Operation cancelled by user.“ des Servers; `Number 0, Class 11, State 0` ist gleich); Mitschnitt-Liste 4 Regeln `cancel-streaming/01/{done,nachrichten,tokenfolge@2022,tokenfolge@2025}` (A-13).
- **Herkunft des Textes (gelesen):** weder `src/` noch `testserver/` enthalten „Operation cancelled“; der Server schreibt bei ATTENTION keinen ERROR-Token (Setzung [SPEC-029](../../../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung)). Die Referenz sendet nach der Messung ebenfalls keinen ERROR-Token mit diesem Text. Beide Texte entstehen demnach im Client aus der Form der Antwort. **Das ist eine Folgerung aus Messung und Code, nicht gemessen:** dass die Zwei-Nachrichten-Form auch den Text des Clients gleich macht (A-7 entfiele mit A-13), zeigt erst der Lauf gegen den gebauten Server.
- **Rest, der bleibt:** die Regeln `cancel-streaming/01/tokenfolge@2022` und `…/02/tokenfolge@2022` nennen auf Seiten der Referenz auch SESSIONSTATE (A-18, bewusst „kein Bau“, Nutzerentscheid 2026-11-06); sie entfallen nicht, sondern werden auf den Grund A-18 verkleinert.

**Offene Frage an den Nutzer — „Code angleichen“ oder „Zielbild anpassen“.** Die Spezifikation ist das Zielbild, eine Abweichung des Servers ist Schuld; ob sie hier durch Bau behoben wird oder ob Spezifikation und Ausnahmeliste dem Ist-Stand folgen, entscheidet der Nutzer, nicht der Planner. Der Slice startet nicht, bevor die Frage beantwortet ist.

- **Option A — Code angleichen:** der Server sendet auf ATTENTION im Stream und bei wartendem Befehl die Zwei-Nachrichten-Form. Das ändert die Setzung des Nutzers vom 2026-11-01 in [SPEC-029](../../../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung) („genau ein `DONE` … ohne zweite Nachricht“), [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) Entscheidung 1 (`Accepted`, nicht änderbar: Folge-ADR mit `Supersedes`, Setzung des Planners) und berührt die Regel „doppelte DONE-Tokens sind verboten“ der Anfragezustandsmaschine ([SPEC-001](../../../../spec/spezifikation.md#spec-001--zustandsautomat-einer-verbindung); zwei Nachrichten sind zwei Antworten, aber die Zustandsfolge `CANCELLING` ist neu zu fassen). Der Referenzclient nimmt beide Formen an (Live-Lauf A-7: Verbindung bleibt offen, Folgeabfrage gelingt).
- **Option B — Zielbild anpassen:** die Ein-Nachricht-Form bleibt; A-7 und A-13 werden *so gewollt* mit der Begründung „beide Formen sind für den Referenzclient gemessen tragfähig, die Setzung des Nutzers vom 2026-11-01 bleibt“; die Matrix (Nr. 12) sagt, dass der echte Server zweiteilig antwortet und der Server dieses Repos bewusst nicht.
- **Empfehlung des Planners (Setzung, keine Entscheidung):** Option A, weil die Spezifikation das Zielbild ist, die Referenz das Verhalten des echten Servers zeigt (Muster des Nutzerentscheids vom 2026-11-06 „Spezifikation dem echten Server angleichen“ in [slice-antwort-tokens-an-messung-angleichen](../done/slice-antwort-tokens-an-messung-angleichen.md)) und die zweite Form jetzt belegt ist. Gegenargument: die Ein-Nachricht-Form ist eine ausdrückliche Setzung des Nutzers und genügt dem Referenzclient; wer sie als Vertrag liest, wählt B.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- SESSIONSTATE (A-18) — bewusst „kein Bau“ mit Re-Evaluierungs-Trigger „ein Treiber oder Pool zeigt Bedarf“ (Nutzerentscheid 2026-11-06, `BEO-TDS/mitschnitt-luecken-ohne-traeger`); steht in [Results-Notiz von welle-referenzabgleich-release](../done/welle-referenzabgleich-release-results.md) ausdrücklich ohne Slice.
- Das Abspielen des Abbruchs eines `WAITFOR`-Befehls am Server (A-15) — [slice-simulation-anweisungsformen-erweitern](../open/slice-simulation-anweisungsformen-erweitern.md); die Antwortform bei wartendem Befehl gehört hierher, der Abspielschritt mit `WAITFOR` zu jenem Slice.
- Der Ablauf der serverseitigen Frist `QUERY_TIMEOUT` (ERROR 50005, [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md)) — ein anderer Pfad (der Server entscheidet, nicht der Client); keine Regel der beiden Listen betrifft ihn.
- Andere Clients als `Microsoft.Data.SqlClient` 7.1.1 — die Antwort auf ATTENTION ist für mssql-jdbc nicht gemessen und kein Gegenstand von [slice-mssql-jdbc-gegen-testserver](../open/slice-mssql-jdbc-gegen-testserver.md) (NBCROW und Mehrfach-RPC).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit.

- [ ] **Antwortform:** der Server sendet auf ATTENTION im Stream und bei wartendem Befehl die gemessene Zwei-Nachrichten-Form (Option A); Handvektor aus den Messdateien beider Instanzen, Tests der Zustandsfolge (`AttentionCancelTest`, `AttentionWireTest`, Race zwischen DONE und ATTENTION, wiederholtes ATTENTION) — oder, bei Option B, die Art der sechs Regeln ist *so gewollt* mit Begründung.
- [ ] **Abspielen und Live-Lauf:** die zwei Live-Regeln (A-7) und die vier Mitschnitt-Regeln (A-13) entfallen oder werden auf A-18 verkleinert (nur die Regeln `tokenfolge@2022`); gemessen ist, ob der Text des Clients mit der neuen Form gleich wird (die Folgerung aus §1 ist damit belegt oder widerlegt — kein stilles Anpassen der Liste).
- [ ] **Spezifikation und Entscheidungen:** [SPEC-029](../../../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung), [SPEC-001](../../../../spec/spezifikation.md#spec-001--zustandsautomat-einer-verbindung) (falls betroffen), [Kompatibilitätsmatrix](../../kompatibilitaetsmatrix.md) Nr. 12 und das Messprotokoll sind nachgezogen; die Folge-ADR zu [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) (Option A) liegt vor Beginn des Baus vor und wird vom Architect geschrieben.
- [ ] `make gates` Exit 0, dazu je Art der Änderung nach `harness/README.md` §Minimal agent workflow: engste Testklasse und Mutation je Zusage über enge Testklassen (`src/`), `make docs-check`, `make flaeche`, `make kopplung-zahlen` (Doku/Spec), `make spec-abdeckung` einmal (Tests oder `@spec`-Tags) (`src/` oder `testserver/`: Verifier fährt `make clean-build` und `make e2e` je einmal auf dem End-Stand; Implementer und Reviewer nicht). Die Läufe `make vergleichslauf-d` sind Messung und Nachweis, keine Gates ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)).
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor (`.harness/skills/reviewer.md`), kein Self-Review.
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt: [SPEC-029](../../../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung), [SPEC-001](../../../../spec/spezifikation.md#spec-001--zustandsautomat-einer-verbindung), [SPEC-045](../../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig), [Kompatibilitätsmatrix](../../kompatibilitaetsmatrix.md) (Nr. 12), [Messprotokoll des Referenzabgleichs](../../../messungen/referenzabgleich.md) (A-7, A-13), `live-ausnahmen.tsv`, `mitschnitt-ausnahmen.tsv`.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben: `BEO-TDS/cancel-antwort-text-weicht-vom-echten-server-ab` (Ausgang nachführen); keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — Pfad-Kandidaten für §8; geprüft ist das Vorhandensein der Dateien am 2026-11-17, nicht ihre Eignung.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/AbortedAnswer.kt`, `RequestLoop.kt`, `AbortableRun.kt`, `src/main/kotlin/io/github/pt9912/tdswire/protocol/response/ResponseSequencer.kt` | update, falls Option A | Schreiben der zwei Nachrichten, Zustand `CANCELLING` und Rückkehr in `READY` |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/dispatch/AttentionCancelTest.kt`, `AttentionWireTest.kt`, `CancelTimeoutTest.kt`, `RpcSequenceAbortTest.kt` | update | Zustandsfolge und Wire-Bytes der Zwei-Nachrichten-Form; Race und wiederholtes ATTENTION |
| `referenztest-dotnet/Vergleich/live-ausnahmen.tsv`, `referenztest-dotnet/Vergleich/mitschnitt-ausnahmen.tsv` | update | Regeln entfallen, verkleinern oder wechseln die Art; der Lauf ist rot bei jeder Abweichung |
| `spec/spezifikation.md`, `docs/plan/kompatibilitaetsmatrix.md`, `docs/messungen/referenzabgleich.md` | update | SPEC-029, ggf. SPEC-001; Matrix Nr. 12; Tabelle der Ausnahmen |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf (Testdateien mit Kennung der Akzeptanzkriterien).

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`open` → `next` → `in-progress`): Nutzerzeichen, ein Verantwortlicher (`Verantwortlich:` ist bis dahin `—`) und die Antwort auf die Frage in §1 (Option A oder B). Das Zeichen für Option A schließt die Folge-ADR zu [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) (Entscheidung 1, `Supersedes`) vor dem Bau ein. Abspielen des Mitschnitts braucht nur den Server dieses Repos; der Live-Lauf als Messung braucht die gepinnten Referenzinstanzen (EULA bestätigt 2026-11-01 und 2026-11-17), läuft **nicht parallel** zu Last-Messslices, und die Messsperre `/tmp/claude-1000/-Development-tdswire/messlock` gilt für Messläufe.

**Rückführungen — vorab benennen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert): die Folge-ADR liegt nicht vor, oder die Referenzinstanzen sind nicht beziehbar.

## 5. Closure-Trigger

Alle Punkte der DoD in §2 erledigt, `make gates` und `make docs-check` Exit 0, die Regeln der Ausnahmeliste, die dieser Slice trägt, sind entfallen oder mit der Entscheidung des Nutzers in ihrer Art geändert (nicht still stehen gelassen), und die Closure-Notiz mit Steering-Loop-Lerneintrag ist geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang.

- Die Zustandsmaschine hat bei zwei Nachrichten einen neuen Zwischenzustand; Race zwischen DONE und ATTENTION oder Frist kann zu einer doppelten Antwort führen ([SPEC-029](../../../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung): „doppelte DONE-Tokens sind verboten“). — **Ausgang:** offen bis zur Closure; Tests der Race-Fälle sind Teil der DoD; Zustandsänderung steht in der Folge-ADR.
- Die Messung am Draht hat je Szenario einen Lauf je Instanz; ATTENTION kam in den Aufnahmen mit Frist und `Cancel()` nur einmal. — **Ausgang:** offen bis zur Closure; Aussagen tragen für diese Fälle; Abstände unter 100 ms und ATTENTION im Leerlauf über andere Wege als `Cancel()` bleiben „nicht gemessen“ ([Kompatibilitätsmatrix](../../kompatibilitaetsmatrix.md) Nr. 12).
- Die Folgerung „der Text des Clients wird mit der Form gleich“ ist nicht gemessen; der Text variiert in der Referenz zwischen Läufen (A-7: mit und ohne Zusatz). — **Ausgang:** offen bis zur Closure; die Liste muss beide Fassungen tragen oder die Variation bleibt eine Regel; Ausgang nach dem ersten Lauf gegen den gebauten Server.
- Ein Client könnte die Ein-Nachricht-Form erwarten (die Setzung stammt aus einem Spike, der dem Referenzclient genügte). — **Ausgang:** offen bis zur Closure; die Wirkung auf den Referenzclient ist mit `make kompatibilitaet` (Nicht-Gate, Stufe `Szenario`) im Verifier-Lauf zu belegen.
- Größe: drei Liefer-Punkte. — **Ausgang:** offen bis zur Closure; Rückführung nach §4.

## 7. Closure-Notiz

Wird bei der Closure gefüllt.

**Herkunft:** Lücken A-7 und A-13 der Ausnahmeliste, [Messprotokoll des Referenzabgleichs](../../../messungen/referenzabgleich.md); Register `BEO-TDS/cancel-antwort-text-weicht-vom-echten-server-ab` (Messung geliefert, Bau ohne Träger); [slice-qa-vergleichslauf-d-messungen](../done/slice-qa-vergleichslauf-d-messungen.md) (W-9), [slice-referenzabgleich-mitschnitt](../done/slice-referenzabgleich-mitschnitt.md); [Results-Notiz von welle-referenzabgleich-release](../done/welle-referenzabgleich-release-results.md). Nutzerentscheidung 2026-11-17: „ja slices anlegen“ (Frage der Closure von `welle-referenzabgleich-release`, Punkt: die 14 Lücken-Nummern ohne Träger-Slice).

## 8. Sub-Area-Prüfungen und Modus-Begründung

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register (`../observations/BEO-TDS/`) gegen die Verzeichnisliste gelesen; Treffer: `cancel-antwort-text-weicht-vom-echten-server-ab` (offen), `mitschnitt-luecken-ohne-traeger`. Zähler-Stände stehen in den Dateien des Registers, nicht hier.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
