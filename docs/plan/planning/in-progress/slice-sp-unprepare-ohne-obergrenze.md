# Slice slice-sp-unprepare-ohne-obergrenze: Obergrenze von 100 gemeldeten Nummern je Batch aufheben (`SPEC-060` an M-22 angleichen) und den Schutz nach TDS-QA-02 anders tragen

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-testserver-manuell

**Bezug:** [TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md), [ADR-0037](../../adr/0037-sp-unprepare-unbekanntes-handle-ergibt-fehler-8179.md)

**Berührte Spec-Stellen:** SPEC-060 (Fehlerzeile `TDS_SIM_UNKNOWN_PREPARED_HANDLE`: „höchstens für die ersten 100 Nummern“, Log „höchstens 100 Einträge je Batch“, Auflösungs-Trigger der Setzung) · SPEC-030 (Abschnitt Handle-Freigabe) · SPEC-029 (Abbruch der Freigabe-Antwort) · SPEC-009 (Frist der Freigabe-Antwort) · Abschnitt Grenzen und Stabilität der Spezifikation, soweit er den Schutz trägt. **Die Spezifikation ändert dieser Slice** (Teil des Auftrags: Spezifikation ist Zielbild).

**Verantwortlich:** Implementer-Agent, Auftrag des Nutzers 2026-11-07.

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-07.

---

## 1. Ziel und Abgrenzung


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Der Server dieses Repos meldet für `EXEC sp_unprepare <n>` mit vielen unbekannten Nummern in einem Batch jede Nummer, wie der echte SQL Server es nach M-22 tut (100, 101 und 150 Nummern auf dem Draht, 1 bis 2000 am Client); `SPEC-060` ist an den Messwert angeglichen, und der Schutz nach [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität) ist durch einen anderen Träger der Grenze erfüllt: Größe der Nachricht, Zeitbegrenzung und Zählung, ohne dass die Antwort mit der Zahl der Nummern ungebremst wächst.

**Herkunft.** Aus dem Auflösungs-Trigger der Setzung in `SPEC-060` (eingetreten durch die Zusatzmessung M-22 des [Messprotokoll](../../../messungen/referenzabgleich.md#messreihe-zu-massenladen-merge-katalog-und-strukturänderung-messungen-vom-2026-11-07)). Träger-Register: [`BEO-TDS/obergrenze-gemeldete-nummern-je-batch-weicht-vom-echten-server-ab`](../observations/BEO-TDS/obergrenze-gemeldete-nummern-je-batch-weicht-vom-echten-server-ab/state.md). Nutzerentscheid 2026-11-07 (per Rückfrage an den Nutzer, Beleg im Register-Eintrag dort). Nutzerentscheid: die Obergrenze 100 aufheben, `SPEC-060` an den Messwert angleichen, der Server meldet jede unbekannte Nummer; der Schutz nach [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität) wird anders getragen. Die Spezifikation ist Zielbild, der echte SQL Server die Referenz (Nutzergrundsatz 2026-11-06). Die Messung [slice-qa-vergleichslauf-d-messungen-bulk-merge](../done/slice-qa-vergleichslauf-d-messungen-bulk-merge.md) liegt vor (Einzelmessungen je Instanz 2022 und 2025, Referenzclient `Microsoft.Data.SqlClient`, nicht mssql-jdbc von d-migrate).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Die Tokenfolge und der `curCmd` der Antwort auf `EXEC sp_unprepare` (`RETURNSTATUS`, `DONEPROC`, Befehl `0x00e0`, kein `DONE`) — [slice-antwort-tokens-zielbild-luecken-bauen](../open/slice-antwort-tokens-zielbild-luecken-bauen.md); beide Slices berühren denselben Pfad (Batch-Freigabe), wer später startet, zieht die Antwortform des anderen nach.
- Handles je Sitzung, Speicher je Handle und die Grenze der Handles ([ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md)) — geliefert in [slice-handle-freigabe-und-grenze-bauen](../done/slice-handle-freigabe-und-grenze-bauen.md); dieser Slice ändert nur die Meldung unbekannter Nummern.
- Eine Messung über 2000 Nummern am Client hinaus oder auf dem Draht über 150 — nicht gemessen (Grenze der Messung M-22); der Schutz muss mit beliebig großen Nachrichten rechnen.

## 2. Definition of Done


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [x] **Spezifikation und Antwort** ([TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)): `SPEC-060` nennt keine Obergrenze der Meldungen und keine Begrenzung der Log-Einträge je Batch mehr; die Zeile trägt den Messwert M-22 (Draht: 100, 101, 150; Client: bis 2000) als Messvermerk mit der Grenze „Einzelmessung je Instanz“; die „Setzung, nicht gemessen“ der Obergrenze entfällt, der Auflösungs-Trigger ist als erfüllt vermerkt; die Bibliothek meldet je unbekannter Nummer einen Fehler; Tests an den Rändern 99, 100, 101, 150 und 2000.
- [x] **Schutz nach [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität)** ([TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität)): Die Wahl des Trägers (Größe der Nachricht, Zeitbegrenzung, Zählung ohne Antwort-Explosion) ist im Slice begründet und belegt: ein Batch mit mindestens 2000 unbekannten Nummern endet mit begrenztem Speicher, die Verbindung bleibt offen, die Folgeabfrage gelingt; **Heap gemessen, Zeit des Strom-Standes nicht gemessen** (Einschränkung der Zeile bei der Closure, Grenze in §7); die Zeit begrenzen Frist und ATTENTION als Test, nicht als Messzahl; kein Satz ohne Messzeile (`AGENTS.md` §3.8). Wird die Grenze zu einer neuen Konfiguration oder einem neuen öffentlichen Namen, ist die Rückführung benannt.
- [x] **Nachzug** ([TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit)): Nutzer-Doku und Kompatibilitätsmatrix nennen die Obergrenze nicht mehr als Grenze; Beobachtung `BEO-TDS/obergrenze-gemeldete-nummern-je-batch-weicht-vom-echten-server-ab` ist fortgeschrieben.

Nachweis-Pflichten für alle Punkte:

- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Haken der Liefer-Punkte setzt der Planner nach der Verifikation, nicht der Implementer (`BEO-TDS/haken-vor-verifikation-gesetzt`).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

## 3. Plan (vor Code)


Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/UnprepareBatch.kt` | update | `MAX_REPORTED` entfällt; `release` liefert die nicht gehaltenen Nummern, `answer` schreibt ein ERROR je Nummer in die Senke und liefert den Ausgang des Laufs (`FAILED`, `CLOSE_WITHOUT_COMPLETION`) |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/RequestLoop.kt` | update | der Zweig der Freigabe ruft `release` und `answer` unter `AbortableRun` (ATTENTION und Frist brechen die Antwort ab, Anfragezustand bleibt `RUNNING`); `RunConclusion` (aus `execute` herausgelöst, `conclude`) trägt beide Zweige, `LeadingToken` bleibt unverändert; ENVCHANGE nach `RESETCONNECTION` als führendes Token |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/RunConclusion.kt` | neu | das Ende einer Anfrage nach dem Lauf unter `AbortableRun` (`Finished`, `Cut`, `Abandoned`, `Disconnected`), aus `RequestLoop.execute` herausgelöst; `execute` und die Freigabe-Antwort rufen dieselbe Klasse |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/error/ErrorAnswers.kt` | neu | `unknownHandles` (ein Log-Eintrag je Antwort, ein ERROR je Nummer in eine `TokenSink`), `encodeTokens` |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/error/ErrorMapper.kt`, `…/protocol/response/ResponseRun.kt` | update | `code` ohne `handles`-Parameter, `log` für die Erweiterung lesbar; `errorAnswerTail` aus `errorAnswer` abgespalten |
| `spec/spezifikation.md` (`SPEC-060`, `SPEC-030`) | update | Zielbild folgt dem Messwert M-22; Schutz als Setzung mit Messvermerk. Der Abschnitt Grenzen nennt die Obergrenze nicht (geprüft per `grep`), `SPEC-007` bleibt unverändert |
| `docs/user/referenz-konfiguration.md`, `docs/plan/kompatibilitaetsmatrix.md` | update | Aussage über die Obergrenze entfällt, Schutz mit seiner Grenze genannt |
| `docs/messungen/unprepare-antwort-speicher-2026-11-07.md` | neu | Messreihen a und b (Heap, Verbindungen) |
| `docs/plan/spec-abdeckung.md` | erzeugt | `make spec-abdeckung` |
| `UnprepareDispatchTest` (auch Abweisung eines Tokens, ATTENTION mitten in der Antwort, Lage des ENVCHANGE), `ErrorMapperTest`, `UnprepareLastProcessTest` (neu), `ServerProcess` (Parameter `jvmOptions`), `RuntimeSupport` (`RawClient.drainMessage`) | update / neu | Ränder 99/100/101/150/2000, Log je Antwort, Serverprozess mit zwei gleichzeitigen Batches von 16 MiB bei 256 MiB Heap |
| `docs/plan/planning/` (`welle-testserver-manuell.md`, `in-progress/roadmap.md`, `open/slice-abnahme-d-migrate.md`, `open/slice-antwort-tokens-zielbild-luecken-bauen.md`, `open/slice-katalog-und-struktur-an-messung-angleichen.md`, `open/slice-katalogspalten-smallint-tinyint-angleichen.md`, `done/slice-qa-vergleichslauf-d-messungen-bulk-merge.md`), Register `messung-am-echten-sql-server-fehlt` und `obergrenze-gemeldete-nummern-je-batch-weicht-vom-echten-server-ab`, `docs/messungen/referenzabgleich.md` | update | Link-Nachzug beim Lifecycle-Wechsel; Stand des Registers |

**Abgrenzung zu [slice-antwort-tokens-zielbild-luecken-bauen](../open/slice-antwort-tokens-zielbild-luecken-bauen.md):** dieser Slice liefert die Obergrenze und die Speichersicherheit; Tokenfolge je Nummer (`RETURNSTATUS`, `DONEPROC`, kein `DONE`) und `curCmd` `0x00e0` bleiben dort. Wer von beiden später startet, passt an `errorAnswerTail` (ResponseRun.kt) und `unknownHandles` (ErrorAnswers.kt) an; die Größe je Nummer (113 Byte) wächst dann um RETURNSTATUS und DONEPROC, die Messreihe b ist neu zu messen.

**Optionen für den Schutz nach [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität):**

1. *Obergrenze der Meldungen beibehalten (Status quo)*: widerspricht M-22; verworfen (Nutzerentscheid 2026-11-07).
2. *`MAX_MESSAGE_BYTES` trägt die Obergrenze der Zahl*: die Nachricht der Anfrage ist schon begrenzt (rund 419 000 Anweisungen der kürzesten Form bei 16 MiB); kein neuer Name, keine Konfiguration. Bedingung: die Antwort wächst nicht mit einem Vielfachen im Speicher.
3. *Antwort als Puffer genau ihrer Größe* (zuerst gebaut, wieder entfernt): Antwort rund 2,8-mal die Anfrage (Messreihe a); im Serverprozess mit 256 MiB Heap endet schon die zweite gleichzeitige Nachricht von 16 MiB im Heap-Überlauf, der Prozess endet (Messzeile b0, Review F-1).
4. *Antwort gestreamt, Token für Token in den Strom der Antwort* (gewählt, zusammen mit 2): `StreamedResponse` und `ReplyStream` der Schleife tragen die Antwort; gehalten wird ein Paket. Die Antwort läuft unter `AbortableRun`: ein ATTENTION währenddessen beendet sie mit einem `DONE[ATTN]` (Test `UnprepareDispatchTest`). Zwei gleichzeitige Nachrichten von 16 MiB bei 256 MiB Heap bestehen (b1, b2); die Grenze liegt danach bei den gleichzeitigen Anfragen dieser Größe (b4 bis b6, auch mit Leerraum).
5. *Log begrenzen oder zusammenfassen*: zusammengefasst, ein Eintrag je Antwort (Setzung); ein Zähler im Ereignis wäre ein neues öffentliches Feld von `RequestFailed`/`TelemetryEvent` und gehört bei Bedarf in einen eigenen Schnitt.
6. *Zeitbegrenzung*: nicht neu gebaut. Die Antwort läuft unter `AbortableRun`: der Ablauf von `QUERY_TIMEOUT` beendet sie mit ERROR 50005 und DONE mit Fehlerstatus (Test `UnprepareDispatchTest > Der Ablauf der Frist waehrend der Antwort der Freigabe bricht sie mit TDS_TIMEOUT ab`, gesteuerte Zeit), ein ATTENTION mit `DONE[ATTN]`. **Grenze:** eine Zeitzahl des Strom-Standes (2000 oder 419 000 Nummern) ist nicht gemessen und steht nicht im Messprotokoll; eine Aussage „begrenzte Zeit“ trägt allein die Frist als Setzung mit Test, keine Messung.

## 4. Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-qa-vergleichslauf-d-messungen-bulk-merge](../done/slice-qa-vergleichslauf-d-messungen-bulk-merge.md) liegt in `done/` (erfüllt mit der Closure dieses Slice) und ein Verantwortlicher ist eingetragen. Kein weiterer Slice als Voraussetzung.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): die Wahl des Trägers des Schutzes verlangt eine ADR (neue Konfiguration oder Fläche); Schnittvorschlag: ADR | Bau.
- `in-progress` → `open` (blockiert): der Schutz lässt sich ohne die Obergrenze nicht belegen; der Nutzer entscheidet über eine Grenze, die vom echten Server abweicht und als solche gekennzeichnet bleibt.

## 5. Closure-Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle drei Liefer-Punkte der DoD erledigt, `make gates` grün, der Review-Report liegt vor, `SPEC-060` trägt keine Obergrenze mehr, die Messzeile des Schutzes (Heap bei zwei gleichzeitigen Nachrichten der Grenze; eine Zeitzahl des Strom-Standes liegt nicht vor, Grenze in §7) liegt vor, die Closure-Notiz trägt den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Der Server schreibt je Nummer einen ERROR, RETURNSTATUS und DONEPROC (nach [slice-antwort-tokens-zielbild-luecken-bauen](../open/slice-antwort-tokens-zielbild-luecken-bauen.md)); die Antwort wächst mit der Zahl der Nummern, die Größe der Nachricht begrenzt sie. — **Ausgang:** weiter offen. Eingetreten ist das Wachstum (rund 2,8-mal die Anfrage, 47 381 930 Byte für 419 000 Nummern); der Speicher wächst nicht mit, weil nur ein Paket gehalten wird (Messzeilen b1, b2, Prozesstest). Offen: die Grenze bei vier gleichzeitigen Nachrichten von 16 MiB (Prozessende im Image des Testservers, Einzelmessung, benannt in `SPEC-060` und Nutzer-Doku) und RETURNSTATUS und DONEPROC je Nummer mit neuer Messreihe b, Träger [slice-antwort-tokens-zielbild-luecken-bauen](../open/slice-antwort-tokens-zielbild-luecken-bauen.md).
- Der Wert der echten Grenze (Nachrichtengröße des echten Servers) ist nicht gemessen; der Schutz des Servers dieses Repos ist eigene Setzung mit Messzeile. — **Ausgang:** weiter offen. Die Nachrichtengröße, an der der echte Server die Anfrage abweist, ist nicht gemessen (`SPEC-060`: „nicht gemessen“); der Schutz bleibt Setzung (`MAX_MESSAGE_BYTES`, `SPEC-007`) mit Heap-Messzeile; Träger der Messlücke ist das Register `BEO-TDS/messung-am-echten-sql-server-fehlt`.
- Der gemeinsame Pfad mit [slice-antwort-tokens-zielbild-luecken-bauen](../open/slice-antwort-tokens-zielbild-luecken-bauen.md) erzeugt bei gleichzeitigem Lauf Konflikte (Reihenfolge, Setzung des Planners 2026-11-08: dieser Slice zuerst, [slice-antwort-tokens-zielbild-luecken-bauen](../open/slice-antwort-tokens-zielbild-luecken-bauen.md) startet nach seinem `done/` und zieht die Antwortform nach). — **Ausgang:** entfallen. Dieser Slice ging zuerst nach `done/`, der andere ist unberührt und startet danach; die Stellen, die er anpasst, sind `errorAnswerTail` (`ResponseRun.kt`) und `unknownHandles` (`ErrorAnswers.kt`).

## 7. Closure-Notiz


Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register (vorhandene `BEO-<KUERZEL>/<slug>` **zitieren** statt neu
formulieren — sonst zählt das Register zwei Namen getrennt) ·
`grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln (das
Feld `liegt in` steht **nur**, wenn mit diesem Slice wirklich etwas verkörpert
wurde; Feld und Zielort auf **einer** Zeile, Sektionsangabe innerhalb der
Backticks). Ging der Gegenstand an einen anderen Slice oder entfiel er, trägt
diese Sektion die Zeile `Gegenstand:` mit Kennung oder Grund und jedes Risiko
aus §6 seinen Ausgang; die Liefer-Punkte der DoD bleiben leer
(`modul-05-planning-harness.md` §Ein Slice, dessen Gegenstand ein anderer
übernimmt).

Closure am 2026-11-08 (Planner). Zuerst die Belege der Rollen, dann die Entscheide zur Verifikation, die Ausgänge, die Grenzen und die Closure-Teile.

**Belege der Rollen.** Umsetzung `479ca367` (Puffer), `5ba270e9` (Strom, nach Review 1), `ba1fdf3b` (Freigabe-Antwort unter `AbortableRun`, nach Review 2), `55dd6e4f` (Tests für Fristablauf und `resetPending`, nach Review 3). Reviews: [Review 1](../../../reviews/2026-11-08-1645b7c2-review-slice-sp-unprepare-ohne-obergrenze.md) (0 HIGH, 2 MEDIUM, 2 LOW, 5 INFO), [Review 2](../../../reviews/2026-11-08-ebd197da-review-2-slice-sp-unprepare-ohne-obergrenze.md) (0 HIGH, 4 MEDIUM, 2 LOW, 5 INFO), [Review 3](../../../reviews/2026-11-08-5c518231-review-3-slice-sp-unprepare-ohne-obergrenze.md) (0 HIGH, 3 MEDIUM, 2 LOW, 5 INFO). Verifikation: [2026-11-08-55dd6e4f-verifikation-slice-sp-unprepare-ohne-obergrenze](../../../reviews/2026-11-08-55dd6e4f-verifikation-slice-sp-unprepare-ohne-obergrenze.md) (0 HIGH, 1 MEDIUM, 1 LOW, 4 INFO): Liefer-Punkt 1 belegt, Liefer-Punkt 3 belegt bis auf V-2, Liefer-Punkt 2 beim Heap belegt, bei der Zeit nicht; eigene Läufe `make clean-build` (1202 Tests), `make gates FORCE=1`, `make matrix-zitate`, `make e2e` (8 Stufen) und sieben Mutationen auf `55dd6e4f`. **Haken** setzt der Planner nur dort, wo dieser Bericht den Beleg trägt.

**Entscheide des Planners zu den Bedingungen der Verifikation.**

| Finding | Entscheid | Ausgang im Text |
|---|---|---|
| V-1 (MEDIUM) Zeitzahl des Strom-Standes fehlt | **Aussage auf den Beleg zurückgenommen, keine zusätzliche Messung** (Auftrag des Planners an die Closure): die DoD-Zeile von Liefer-Punkt 2 sagt „Heap gemessen, Zeit des Strom-Standes nicht gemessen“; Option 6 im Plan §3 trägt keine Zahl des entfernten Puffer-Standes mehr und nennt die Frist (Test mit gesteuerter Zeit) statt „nicht untersucht“; die Grenze steht unten. Der Nutzer kann stattdessen eine Messung verlangen (Zeit je Verbindung des Prozesstests `UnprepareLastProcessTest`, mit Last des Hosts); dann wird die DoD-Zeile wieder in ihrem ursprünglichen Wortlaut gesetzt. | §2, §3 Option 6, §5, Grenzen |
| V-2 (LOW) Matrix nennt den Schutz nicht | die Zeile „Freigabe der Handles“ der Kompatibilitätsmatrix nennt `MAX_MESSAGE_BYTES`, Strom statt Puffer, Abbruch, die Heap-Messung bei zwei Nachrichten und die Grenze bei vier (Quellen: `SPEC-060`, Nutzer-Doku) | `docs/plan/kompatibilitaetsmatrix.md` |
| V-3 (INFO) Tokenlisten-Puffer wird vom Prozesstest nicht gefangen | als Grenze geführt (unten); kein Sensor nachgebaut | Grenzen |
| V-4 (INFO) Stand `55dd6e4f` ohne eigenen Review | hingenommen und vermerkt (unten); der Zuwachs ist Testcode, Plan, ein Satz in `SPEC-060` und die Abdeckungsmatrix | Grenzen |
| V-5 (INFO) Merge-Probe, zehn Dokumentkonflikte | beim Merge von `main` aufgelöst: Lifecycle- und Link-Nachzug, `docs/plan/spec-abdeckung.md` neu erzeugt | Merge-Hinweis |
| V-6 (INFO) gemeinsamer Scratchpad, Gates aus dem Layer-Cache | Verfahren; der Frischbeleg der Tests liegt im `make clean-build` des Verifiers, die Gates zeigen denselben Stand | Gate-Läufe |

**Grenzen dieser Closure.**

- *Zeit des Strom-Standes nicht gemessen.* Für den gebauten Stand (Antwort als Strom unter `AbortableRun`) liegt keine Zeitzahl im Repo; das Messprotokoll hat keine Zeitspalte. Die Zeit begrenzen Frist und ATTENTION als Zusage mit Test (gesteuerte Zeit, Fake-Kanal; am echten Kanal ist ATTENTION nicht gemessen, `SPEC-060`), nicht als Messwert. `AGENTS.md` §3.8: die Aussage „endet in begrenzter Zeit“ trägt nicht über die Frist hinaus.
- *Der Wächter des Prozesstests fängt den Puffer als Bytes, nicht als Tokenliste* (V-3). Die Variante „alle ERROR-Tokens in einer Liste, danach schreiben“ lässt 66 Tests grün; der Byte-Puffer macht `UnprepareLastProcessTest` rot. Die Zusage „steht nie als Liste oder Puffer im Speicher“ in `SPEC-060` und im KDoc von `unknownHandles` ist für die Liste durch Lesen des Codes gehalten, nicht durch einen Test.
- *Der Stand `55dd6e4f` hat keinen eigenen Review* (V-4): geändert sind Testcode (`UnprepareDispatchTest`, `PreparedHandlesDispatchTest`), Plan, ein Satz in `SPEC-060` und die Abdeckungsmatrix, kein Produktivcode; der Verifier hat die Wirkung der Tests per Mutation (m1, m4, m5) gelesen, ein Re-Review mit dem Reviewer-Skill hat nicht stattgefunden. Das ist wie bei früheren Closures eine Nachbesserung ohne zweiten Review (Register `BEO-TDS/nachbesserung-ohne-zweiten-review`).
- *Mehr als zwei gleichzeitige Nachrichten der Grenze* sind im Repo nicht belegt; die Folge bei vier ist als Einzelmessung außerhalb des Repos in `SPEC-060` und Nutzer-Doku benannt.

**Beobachtungs-Register.** Fortgeschrieben: `BEO-TDS/obergrenze-gemeldete-nummern-je-batch-weicht-vom-echten-server-ab` (Stand gestrichen, Ursache entfallen, `observation.md` unverändert); `BEO-TDS/wanduhr-tests-flackern-unter-last` (vierter Vorgang: `ListenerAbortTest` einmal rot in der Matrix von `5c518231`, `ConnectionStormTest` einmal rot im Frischbau von Review 1, danach vier Läufe ohne Flake; die Verifikation fuhr `ConnectionStormTest` grün; Ausgang weiter beim Nutzer, siehe dessen `state.md`); `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test` (weitere Evidence-Datei, verkörpert im Reviewer-Skill); neu `BEO-TDS/umbau-uebernimmt-zusage-ohne-test` (1×).

**Steering-Loop-Eintrag (nichts verkörpert, daher ohne Zielort-Feld).** *Zusage ohne Test nach Umbau:* der Umbau von Puffer auf Strom unter `AbortableRun` übernahm die Zusagen der Spezifikation (ATTENTION, Fristablauf, Lage des ENVCHANGE, Schreibfehler, `resetPending`) in den neuen Pfad, und die Tests zogen nicht mit: in Review 1, 2 und 3 fehlte je eine Gruppe, jede wurde erst durch Mutation sichtbar. Der Prozesstest trug den Zusammenbau, nicht die einzelne Zusage. Der Reviewer fand die Lücke jedes Mal (die Regel `zeile-oder-zweig-ohne-unterscheidenden-test` ist im Reviewer-Skill verkörpert), der Implementer legte sie jedes Mal neu an. Gezählt als eigene Klasse `BEO-TDS/umbau-uebernimmt-zusage-ohne-test` (1×, unter der Schwelle); Prüfpunkt für die nächste Slice-Planung (Sichtungs-Schritt): bei einem Umbau des Pfades einer Zusage die Zusagen der Spezifikation in §3 einzeln nennen und je einen Test, der unter Entfernen der Zeile rot wird. Eine **benannte Spec-Lücke** entsteht daraus nicht; die Grenze der Zeitmessung steht oben.

**Anwendung der Schritte 6a bis 6d von `/plan-welle` auf diese Closure.** 6a: keine ADR wechselt mit dieser Closure auf `Accepted` ([ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md) und [ADR-0037](../../adr/0037-sp-unprepare-unbekanntes-handle-ergibt-fehler-8179.md) sind angenommen, keine neue Konfiguration, kein neuer öffentlicher Name: `make flaeche` grün). 6b: die Liefer-Punkte sind lieferbar; Punkt 2 mit der Einschränkung der Zeile (V-1). 6c: die Haken setzt der Planner nach der Verifikation (oben). 6d: der Erzeuger der Proben b4 bis b8 liegt nicht im Repo; `SPEC-060` und Nutzer-Doku nennen es.

**Gate-Läufe und Belege.** Verifier auf `55dd6e4f`: `make clean-build`, `make gates FORCE=1`, `make flaeche`, `make docs-check`, `make matrix-zitate`, `make e2e` grün (Last 8 bis 160). Planner nach den Änderungen dieser Closure und dem Merge von `main`: siehe Bericht an den Auftraggeber (`make gates`, `make docs-check`, `make spec-abdeckung`); `make clean-build`, `make e2e` und `make matrix-zitate` nicht erneut gefahren (die Änderungen der Closure sind Dokument-Zeilen; Zitate der Matrix unverändert).

**Merge-Hinweis.** `main` ist in den Zweig gemergt; die zehn Konflikte lagen in Dokumenten (Lifecycle- und Link-Nachzug, Roadmap, Welle-Datei, `spec-abdeckung.md`).

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung — dort die **zwei vorgelagerten
Schritte** (sie stehen in jedem Slice-Plan, unabhängig von Modus und
Slice-Typ) und die **vier Pflichtkriterien** (Konventionen-Dichte ·
Phase-Reife · Evidenz-/Diskrepanz-Risiko · Reconciliation-Aufwand), vier und
nicht mehr.

**Der Abschnitt selbst entfällt nie.** Die zwei vorgelagerten Prüfungen laufen
in **jedem** Slice-Plan — sie hängen weder am Modus noch am Slice-Typ. Bedingt
ist allein der Modus-Begründungsblock am Ende; deshalb nennt der Titel beide
Hälften.

**Vorgelagert — Sub-Area-Wahl prüfen:** `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); eine eigene Sub-Area besteht nicht.

**Vorgelagert — offene Beobachtungen sichten:** [`BEO-TDS/obergrenze-gemeldete-nummern-je-batch-weicht-vom-echten-server-ab`](../observations/BEO-TDS/obergrenze-gemeldete-nummern-je-batch-weicht-vom-echten-server-ab/state.md) (Gegenstand), [`BEO-TDS/spec-setzung-widerspricht-messung-am-echten-server`](../observations/BEO-TDS/spec-setzung-widerspricht-messung-am-echten-server/state.md).

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
