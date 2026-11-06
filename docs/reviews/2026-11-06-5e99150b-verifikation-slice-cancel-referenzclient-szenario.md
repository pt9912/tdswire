# Verifikationsbericht: slice-cancel-referenzclient-szenario — 2026-11-06

**Art:** Verifikation (Modul 11) — DoD- und Entscheidungs-Konformität, Plan-vs-Code-Diff. Frage: „Bauen wir es richtig?“

**Gegenstand:** `slice-cancel-referenzclient-szenario`, Diff `1c9a85c9..HEAD` (Commits `c40adec8`, `1d9bfc1c`, `9bf688b9`, Review `f4a229ac`, Nachbesserung `f809e5e4`, DoD-Wortlaut `5e99150b`).

**Eigene Läufe (Docker, im Worktree, frisch):**

| Sensor | Ergebnis |
|---|---|
| `make simulations-integration` | grün: 5 von 5 Tests; „Quelle von laufend nach dem Cancel genau einmal geschlossen“; Laufzeit 20 s |
| `make kompatibilitaet KATEGORIE=Synchronitaet` | grün: 10 von 10 Tests; „Summe der Cancels gleicht der Summe der Anfragen an laufend“; 35 s |
| `make docs-check` | 770 Dateien, 0 Befunde |
| `make simulations-integration` / Kategorie auf Basis `1c9a85c9` (Wegwerf-Worktree, entfernt) | `Synchronitaet` rot: 3 von 4 grün, `Anfragefehler_im_Batch_und_in_der_RPC_…` schlägt an (`Assert.Throws() Failure: No exception was thrown`, `SynchronitaetTests.cs:line 90` = `SELECT 1`) |

Nicht gefahren: `make gates` (Begründung: Gate-Lauf war Sache des Implementers; der DoD-Haken „make gates grün“ ist hier nicht eigens belegt gesehen, nur die drei oben genannten Sensoren; `docs-check` ist Teil von `make gates`).

## Antwort auf die Basis-Frage (roter Stufe-7-Test)

**Belegt, nicht nur vermutet.** Ich habe `1c9a85c9` in einem Wegwerf-Worktree gebaut und `make kompatibilitaet KATEGORIE=Synchronitaet` gefahren: der Test `Anfragefehler_im_Batch_und_in_der_RPC_…` ist rot, weil `SELECT 1` (Form C, SPEC-036) beantwortet wird und keine `SqlException` entsteht. Der Tausch auf `SELECT 2` im Diff ist damit gerechtfertigt und keine Test-Lockerung (Erwartung 50001 bleibt). Die Vorbefund-Aussage des Reviews („nach Aktenlage nicht belegt“) ist damit erledigt. Die Matrix führt `SELECT 2` jetzt an beiden Stellen (F-1 behoben).

## DoD-Punkte (§2)

| # | Punkt | Haken im Plan | Urteil des Verifiers |
|---|---|---|---|
| 1 | Cancel-Szenario von SPEC-040 / Stufe 3 | offen | **Inhaltlich erfüllt, Haken zu Recht noch offen.** Fünftes Szenario läuft grün (419 Zeilen nach Cancel, Folgeabfrage, `Open`); Quelle genau einmal geschlossen (Lauf sichtbar); Summe Cancels = Summe Anfragen (Stufe 7, Lauf sichtbar). Der DoD-Text sagt selbst, dass die Zählprüfung nicht rot gesehen wurde und kein Vergleich je Anfrage besteht: Aussage ≤ Beleg. **CO-004 liegt weiter in `docs/plan/carveouts/`** (nicht in `done/`), Geschichte „bleibt Aktiv“: Trigger (a) (beide Slices in `done/`) ist nicht erfüllt, das Nicht-Verschieben ist korrekt. Offen bis zur Closure: Move des Carveouts, Carveouts-README-Zeile (diese Doku-Punkte gehören zur Closure, siehe unten). SPEC-040, Kopf von `tools/simulationstest.sh`, KDoc und Sensorzeile in `harness/README.md` sind berichtigt. |
| 2 | Cancel-Teil von [TDS-QA-07](../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität) (Folgepflichten a–c) | offen | **Haken muss offen bleiben; der DoD-Text trägt keinen Haken.** Geliefert und im Lauf bestätigt: `Cancel()`, `CancellationToken`, Command-Timeout (R15: ATTENTION löst den Abbruch vor der Frist aus), Cancel+Command-Timeout, jeweils mit Folgeabfrage. Folgepflicht (a) „N ATTENTION ergeben N Bestätigungen“: **nicht gemessen** (Matrix: ob der Client zwei ATTENTION sendet, nicht beobachtet; TLS, kein Mitschnitt). (b) `DONE[ATTN]` allein ohne ERROR gegen den Referenzclient: nur mittelbar durch die Cancel-Tests abgedeckt (der Server antwortet so, der Client bleibt benutzbar); die Matrix benennt (b) nicht als eigene Messung. (c) ATTENTION im Leerlauf am Referenzclient: **nicht belegt**, in der Matrix ausdrücklich als nicht gemessen geführt. Der Punkt verlangt (a)–(c) und CO-004 „bleibt bestehen“, solange die Messung abweicht oder fehlt; er ist nicht abzuhaken. Folge-Entscheidung beim Planner: entweder (a)/(c) messen, oder den DoD-Text auf das Belegte setzen und (a)/(c) als offene Lücke (Matrix Zeile 12 führt sie bereits so) in einen benannten Folge-Slice/Carveout-Zeile führen. Keine Re-Evaluierung von [ADR-0027](../plan/adr/0027-abbruch-und-zuruecksetzen.md) ausgelöst: die Trigger (Entscheidungen 1 und 2) sind laut Matrix und Lauf nicht eingetreten. |
| 3 | `TDS_TIMEOUT` am Referenzclient | **[x]** | **Erfüllt, Haken berechtigt.** Test `Ablauf_der_Frist_am_Server_…` grün (5 s Wanduhr); Matrix: `SqlException` 50005, Class 16, State 1, `Open`, Folgeabfrage beantwortet (rund 950 Zeilen, 5002–5003 ms). Setzung von [ADR-0027](../plan/adr/0027-abbruch-und-zuruecksetzen.md) Entscheidung 2 hält. |
| – | `make gates` grün | **[x]** | Behauptet vom Implementer (Commit `9bf688b9`); von mir nicht als Gesamtlauf wiederholt. Die Teil-Sensoren oben sind grün. |
| – | Review, Doku-Update, Closure-Notiz, Register, Risiken, Paarungen | offen | Review-Report liegt vor (`docs/reviews/2026-11-06-9bf688b-…`), der Haken „Review durchgeführt“ ist offen und wäre jetzt setzbar; Doku-Update teilweise getan (Spec, Matrix, README, e2e-Abdeckung, Handbuch), CO-004-Move und Carveouts-README bleiben bis zur Closure offen. Closure-Notiz, Register, Risikoausgänge, Paarungen: nicht begonnen — Planner/Closure. |

DoD-Zählung: der Plan führt drei Liefer-Punkte; das Maximum (≤ 3) ist gewahrt.

## Review-Findings (Stichprobe gegen f809e5e4)

| ID | Stand | Beleg |
|---|---|---|
| F-1 (Matrix `SELECT 1`) | behoben | Matrix Zeilen 215 und 256 nennen `SELECT 2` und den Grund (Form C); Basis-Lauf bestätigt die Notwendigkeit |
| F-2 („je Anfrage genau ein Cancel“) | behoben durch Einschränkung | `tools/e2e.sh`, `docs/plan/e2e-abdeckung.md`, `tools/kompatibilitaetstest.sh` sagen jetzt „Summe der Cancels … Summe der Anfragen (kein Vergleich je Anfrage)“; DoD-Text trägt „nicht rot gesehen“. Die Prüfung selbst ist weiter nicht per Mutation belegt; die Aussage ist darauf zurückgenommen (AGENTS.md §3.8 gewahrt). Hinweis: die Lücke bleibt (zwei Cancels bei einer, keiner bei einer anderen Anfrage bleiben grün), ist aber benannt |
| F-3 („nicht vermischt“) | behoben | Matrix Zeile 269 sagt: Folgepflicht „N ATTENTION ergeben N Bestätigungen“ ist damit nicht gemessen |
| F-4 (CO-004 Geltungs-Konfiguration) | behoben | Tabelle beschreibt den Stand nach dem Szenario („nennt das Cancel-Szenario …“) |
| F-5 (DoD „Handlerfehler“) | behoben vom Planner (`5e99150b`) | DoD Punkt 1 lautet jetzt auf Gelieferte statt Serverlog; Ersatz bei `slice-betrieb-log-adapter-testserver` (Datei liegt in `open/`, siehe Prüfung unten) |
| F-6/F-7 (INFO) | offen, nicht blockierend | Command-Timeout-Test 2 s nahe an unerklärter Grenze; Zahlen (50 Zeilen, 2 s, 5 s) an mehreren Orten ohne Gate |

Hinweis zur Nachbesserung `f809e5e4`: der Implementer hat im Review-Report (Artefakt des Reviewers) ADR-Verweise in Links umgewandelt (reine Link-Form, Inhalt unverändert; Anlass: `docs-check`). Das ist inhaltlich unbedenklich, aber ein Eingriff in das Reviewer-Artefakt, vom Planner zur Kenntnis zu nehmen.

## Entscheidungs-Konformität

- **[ADR-0027](../plan/adr/0027-abbruch-und-zuruecksetzen.md):** Entscheidung 1 (Cancel/ATTENTION): Referenzclient hängt nicht, schließt nicht, ordnet Folgeantwort richtig zu — im Lauf bestätigt. Entscheidung 2 (`TDS_TIMEOUT` = 50005, Class 16, Verbindung offen) — bestätigt. Re-Evaluierungs-Trigger nicht eingetreten.
- **[ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md):** `LaufendeQuelle.kt` liegt im Source-Set `testserver`, importiert nur öffentliche Typen; kein Bibliothekslog erwartet, Handlerfehler-Prüfung nicht wieder eingeführt.
- **Hard Rules:** kein `@Suppress` im Diff (einzige Treffer sind Text im Review-Report); keine Gate-Konfiguration, kein Makefile, keine detekt-/Workflow-Datei im Diff geändert; kein Gate gelockert, keine Schwelle gesenkt (§3.6); die Test-Erwartung 50001 blieb, nur die Eingabe wurde von `SELECT 1` auf `SELECT 2` gesetzt, was durch Spec (SPEC-036) und Basis-Lauf getragen ist. Docker-only gewahrt (§3.1).
- **Aussagen ≤ Beleg (§3.8):** die offen gebliebenen Messungen (a), (c), Cancel unter Pooling/TDS 8.0, echter Server stehen als „nicht gemessen“ in Matrix und DoD; die „nicht rot gesehen“-Vermerke stehen im DoD-Text und in der Matrix. Eine Restunschärfe: Rot-gesehen-Mutationen der Matrix (`AbortableRun.watch`, `QUERY_TIMEOUT` 30 s) habe ich nicht wiederholt.

## Plan-vs-Diff

Alle Zeilen der Dateiliste in §3 des Plans haben ihre Entsprechung im Diff (Skripte, Tests, `LaufendeQuelle.kt`, `IntegrationServer.kt`, `KompatibilitaetsServer.kt`, Spec, Matrix, README, Abdeckung, Handbuch, CO-004-Geschichte). Abweichung vom Plan-Text: §3 nennt die Kotlin-Dateien unter `src/test/kotlin/.../runtime/`, geliefert sind sie unter `src/testserver/kotlin/.../testserver/` (die Plan-Zeilen für `LaufendeQuelle.kt` sind im Diff selbst nachgetragen; die zwei älteren Pfadzeilen für `IntegrationServer.kt`/`KompatibilitaetsServer.kt` zeigen weiter auf den alten Ort). Nicht-blockierend, Plan-Pflege.

## Offene Punkte für den Planner

1. **DoD Punkt 2 nicht abhaken**, solange (a) und (c) nicht gemessen sind. Entscheidung nötig: messen oder DoD auf das Belegte einschränken und die Lücke an einen benannten Träger binden; erst dann Closure, CO-004 bleibt bis dahin und Trigger (a) greift erst mit beiden Slices in `done/`.
2. Plan-Pfade in §3 für `IntegrationServer.kt`/`KompatibilitaetsServer.kt` berichtigen.
3. Haken „Review durchgeführt“ setzbar (Report liegt vor, F-1…F-5 behoben).
4. F-6/F-7 als Beobachtungen für das Register (Zahl an mehreren Orten ohne Gate; Wartungsrisiko Timeout-Grenze).
5. `make gates` ist von mir nicht als Gesamtlauf bestätigt.

## Fazit

Punkt 3 erfüllt. Punkt 1 liefert, was er zusagt (Haken folgt mit der Closure samt CO-004-Move, der noch nicht erfolgen darf). **Punkt 2 ist nur teilweise erfüllt und darf nicht abgehakt werden** (Folgepflichten (a) und (c) ungemessen, (b) nur mittelbar). Die Basis war nachweislich rot (Eigenlauf). Keine Gate-Lockerung, keine DoD-Verletzung im geprüften Umfang jenseits des benannten Punktes 2. Der Slice ist noch nicht schließbar.
