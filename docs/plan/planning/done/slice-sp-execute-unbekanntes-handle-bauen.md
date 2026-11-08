# Slice slice-sp-execute-unbekanntes-handle-bauen: `sp_execute` mit unbekanntem Handle ergibt Fehler 8179, State 4 (Spezifikation, Quelltext, Tests, Matrix nach ADR-0045)

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-testserver-manuell

**Bezug:** [TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0045](../../adr/0045-sp-execute-unbekanntes-handle-ergibt-fehler-8179-state-4.md), [ADR-0037](../../adr/0037-sp-unprepare-unbekanntes-handle-ergibt-fehler-8179.md), [ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md)

**Berührte Spec-Stellen:** SPEC-057 · SPEC-030 (RPC-Tabelle Zeile `sp_execute`, Satz zum Fehlerfall unter der Tabelle, Absätze `sp_execute` und `RESETCONNECTION`) · SPEC-022 (Query-Backend-Vertrag, Absatz „RPC im Backend-Vertrag“, Satz zu Ablehnungen vor dem Backend) · SPEC-060 · §4 „Fehler, Codes und Logging-Felder“ (Zeilen `TDS_UNKNOWN_HANDLE`, `TDS_SIM_UNKNOWN_PREPARED_HANDLE`, Satz zu den State-Ausnahmen). Die Liste der Änderungen steht in [ADR-0045](../../adr/0045-sp-execute-unbekanntes-handle-ergibt-fehler-8179-state-4.md), Abschnitt „Liste der Spec-Änderungen“.

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-08.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar.

**Ziel:** Der Server dieses Repos antwortet auf `sp_execute` mit einem Handle, das die Sitzung nicht hält (nie vergeben, −1, 0, freigegeben, nach `RESETCONNECTION`), mit ERROR 8179, Class 16, State 4 und dem Text `Could not find prepared statement with handle <n>.`; die Spezifikation führt es als Zielbild mit Beleg M-6 und M-7, und `TDS_UNKNOWN_HANDLE` (50009) wird für diesen Fall nicht mehr gesendet ([ADR-0045](../../adr/0045-sp-execute-unbekanntes-handle-ergibt-fehler-8179-state-4.md) Entscheidung 1 bis 3).

**Herkunft.** [ADR-0045](../../adr/0045-sp-execute-unbekanntes-handle-ergibt-fehler-8179-state-4.md) (`Accepted`, Entscheid des Nutzers 2026-11-08, Option A); Träger der Folgepflicht ist dieser Slice. Register: [`BEO-TDS/sp-execute-unbekanntes-handle-spec-057-widerspricht-messung`](../observations/BEO-TDS/sp-execute-unbekanntes-handle-spec-057-widerspricht-messung/state.md).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Eine Messung der Tokenfolge und der weiteren Felder (RETURNSTATUS, Name der Prozedur, Zeilennummer) für `sp_execute` mit unbekanntem Handle — [ADR-0045](../../adr/0045-sp-execute-unbekanntes-handle-ergibt-fehler-8179-state-4.md) Offen 1: bleibt Setzung, ein Messauftrag ist keine Bedingung der Entscheidung; die Spezifikation kennzeichnet die Felder als Setzung (Entscheidung 4).
- Eine Messung von `sp_execute` nach `RESETCONNECTION` am echten Server — [ADR-0045](../../adr/0045-sp-execute-unbekanntes-handle-ergibt-fehler-8179-state-4.md) Offen 2: bleibt Setzung; eine Abweichung löst den Re-Evaluierungs-Trigger der ADR aus.
- Die Reaktion eines Treibers auf den Fehler — in `docs/messungen/handle-grenze-optionen-2026-11-06.md` als nicht gemessen geführt, nicht Teil der Frage „welche Nummer“.
- Der State der Batch-Freigabe (`sp_unprepare`, State 8) und ihre Tokenfolge — gemessen (M-4, M-22, M-23) und gebaut in [slice-handle-freigabe-und-grenze-bauen](../done/slice-handle-freigabe-und-grenze-bauen.md) und [slice-antwort-tokens-zielbild-luecken-bauen](../done/slice-antwort-tokens-zielbild-luecken-bauen.md); sie bleibt unverändert und ist Gegenprobe der Tests.
- Eine Vereinheitlichung der Log-Marken `unknown-handle` und `unknown-prepared-handle` — [ADR-0045](../../adr/0045-sp-execute-unbekanntes-handle-ergibt-fehler-8179-state-4.md) Entscheidung 5 überlässt sie dem Bau; sie ist Gelegenheit, keine Pflicht, und ändert den Draht nicht.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung.

Liefer-Punkte (drei):

- [x] **Spezifikation** ([TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc)): `spec/spezifikation.md` folgt der Liste der Spec-Änderungen von [ADR-0045](../../adr/0045-sp-execute-unbekanntes-handle-ergibt-fehler-8179-state-4.md): `SPEC-057` (Nummer 8179, Class 16, State 4, Text; „Behandlung nicht entschieden“ entfällt; Tokenfolge und weitere Felder als Setzung), `SPEC-030` (Absätze `sp_execute` und `RESETCONNECTION`), `SPEC-060` und Zeile `TDS_SIM_UNKNOWN_PREPARED_HANDLE` (State 8 bei Batch-Freigabe, 4 bei `sp_execute`; beides gemessen), Zeile `TDS_UNKNOWN_HANDLE` (entfällt oder „nicht mehr gesendet“), Satz zu den State-Ausnahmen in §4 (State 4 zusätzlich), Änderungsprotokoll der Spezifikation; dieselbe Fehlertabelle in `docs/user/referenz-konfiguration.md` (zweite Fundstelle, die Liste der ADR nennt sie nicht). Beleg: `make docs-check`.
- [x] **Quelltext und Tests** ([TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)): der Zweig von `sp_execute` mit nicht gehaltenem Handle in `protocol/dispatch/RequestLoop.kt` (bisher `ErrorCode.TDS_UNKNOWN_HANDLE`) sendet `TDS_SIM_UNKNOWN_PREPARED_HANDLE` mit State 4; Katalog der Codes in `protocol/error/ErrorCode.kt` und `ErrorMapper` tragen den State je Aufrufform (Form im Quelltext: Bau nach den Regeln der Schichten); Backend nicht gerufen, Zähler unverändert, Verbindung offen. Tests: bestehende angepasst (`PreparedHandlesDispatchTest`, `ResetConnectionDispatchTest`, `RequestSequenceTest`, `ErrorMapperTest`, `UnprepareDispatchTest`), neu ein Handvektor 8179/State 4 für nie vergeben, −1, 0, freigegeben und nach `RESETCONNECTION`, Gegenprobe State 8 bei der Batch-Freigabe; Referenzclient-Tests gegen den Serverprozess, soweit einer 50009 erwartet (zum Planungszeitpunkt keine Fundstelle in `referenztest-dotnet/`). Beleg: `make test`, `make detekt`.
- [x] **Doku** ([TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)): Kompatibilitätsmatrix (`docs/plan/kompatibilitaetsmatrix.md`): die Zeile `sp_execute` trägt 8179/State 4; die Zeile „Handle-Verwaltung je Sitzung“ blieb unverändert, weil sie weder Fehlernummer noch State noch ein Verhalten bei unbekanntem Handle nennt (Entscheid des Planners in der Closure, §7); Messprotokoll `docs/messungen/referenzabgleich.md`, Zeile M-6: der Hinweis „`SPEC-057` und `TDS_UNKNOWN_HANDLE` bleiben bis dahin unverändert“ ist eingelöst; Stand des Registers fortgeschrieben. Beleg: `make docs-check`; die Zitate der Matrix prüfte der Verifier mit `tools/matrix-zitate.sh` gegen das Log seines `make clean-build` (344 Zitate, alle `PASSED`).

Nachweis-Pflichten für alle Punkte:

- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Haken der Liefer-Punkte setzt der Planner nach der Verifikation, nicht der Implementer (`BEO-TDS/haken-vor-verifikation-gesetzt`).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist (hier: Fehlertabelle der Nutzer-Doku; `SPEC-059` nur, falls der Aufzählungswert `TDS_UNKNOWN_HANDLE` zu einem öffentlichen Typ gehört, `make flaeche` entscheidet).
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben; keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo mit Wellen von der nächsten Welle-Closure.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `spec/spezifikation.md` (`SPEC-057`, `SPEC-030`, `SPEC-060`, §4 Fehlertabelle und State-Satz, Änderungsprotokoll) | update | Liste der Spec-Änderungen von [ADR-0045](../../adr/0045-sp-execute-unbekanntes-handle-ergibt-fehler-8179-state-4.md) ([TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc)) |
| `docs/user/referenz-konfiguration.md` | update | Fehlertabelle führt `TDS_UNKNOWN_HANDLE` (50009) heute als gesendet; Ist-Zustand nachziehen |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/RequestLoop.kt` (Zeile ~377, KDoc Zeilen ~70 und ~85) | update | Zweig `sp_execute` mit nicht gehaltenem Handle; KDoc nennt `TDS_UNKNOWN_HANDLE` |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/error/ErrorCode.kt` | update | `TDS_UNKNOWN_HANDLE` und `N_HANDLE` (50009) entfernt; Konstante `STATE_EXECUTE_UNKNOWN_HANDLE` (4) neben dem State 8 der Regel von `TDS_SIM_UNKNOWN_PREPARED_HANDLE` |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/error/ErrorMapper.kt` | update | `ErrorRule.message` nimmt den State der Aufrufform |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/error/ErrorAnswers.kt` | update | neu `ErrorMapper.executeUnknownHandle` (ERROR 8179, State 4, Form RPC, Log-Marke als Argument), neben `releaseAnswer` der Batch-Freigabe (State 8); die Suche fand dort keinen Bezug zu 50009 |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/dispatch/{PreparedHandlesDispatchTest,ResetConnectionDispatchTest,RequestSequenceTest,UnprepareDispatchTest}.kt`, `src/test/kotlin/io/github/pt9912/tdswire/protocol/error/ErrorMapperTest.kt` | update | behaupten 50009 und `TDS_UNKNOWN_HANDLE` ([TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)) |
| Handvektor 8179/State 4 (Testdatei im Paket `protocol/dispatch` oder `protocol/error`) | neu | Happy: nie vergeben, −1, 0, freigegeben, nach `RESETCONNECTION`; Negative: kein Backend-Aufruf, Zähler unverändert, Verbindung offen; Gegenprobe State 8 der Batch-Freigabe |
| `docs/plan/kompatibilitaetsmatrix.md` (Zeile `sp_execute`; die Zeile „Handle-Verwaltung je Sitzung“ nennt keine Fehlernummer und bleibt unverändert) | update | [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität); Zitate prüft `make matrix-zitate` |
| `spec/spezifikation.md` (`SPEC-022`, Absatz „RPC im Backend-Vertrag“; `SPEC-030`, Zeile `sp_execute` der RPC-Tabelle und Satz zum Fehlerfall) | update | Ablehnung vor dem Backend nennt 8179/State 4; die Zeile und der Satz zum Fehlerfall folgen der Liste der Spec-Änderungen |
| `docs/plan/spec-abdeckung.md` | update (erzeugt) | `make spec-abdeckung`; Matrix steht sonst hinter dem Bestand |
| `docs/plan/planning/welle-testserver-manuell.md`, `docs/plan/planning/in-progress/roadmap.md` | update | Verweise auf den Slice nach `in-progress/`, Stand der Wellenzeile |
| `docs/plan/planning/observations/BEO-TDS/sp-execute-unbekanntes-handle-spec-057-widerspricht-messung/state.md` | update | Register-Zustand der Beobachtung |
| `docs/plan/planning/done/slice-adr-sp-execute-unbekanntes-handle.md`, `docs/plan/planning/open/slice-massenladen-atomar-bauen.md` | update | Verweis-Nachzug auf den Slice in `in-progress/` |
| `docs/messungen/referenzabgleich.md` (Zeile M-6) | update | Hinweis eingelöst |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/dispatch/UnprepareDispatchTest.kt` (Filter der Log-Einträge nach Marke statt nach Code) | update | `sp_execute` und Batch-Freigabe teilen den Code `TDS_SIM_UNKNOWN_PREPARED_HANDLE`; die Marken `unknown-handle` und `unknown-prepared-handle` unterscheiden sie |

Reihenfolge innerhalb des Slice: Spezifikation zuerst (Zielbild), dann Test, dann Quelltext. Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): keine Start-Bedingung außer einem eingetragenen Verantwortlichen; [ADR-0045](../../adr/0045-sp-execute-unbekanntes-handle-ergibt-fehler-8179-state-4.md) ist `Accepted` (2026-11-08). `SPEC-030` teilt der Slice mit keinem offenen Slice ([slice-handle-freigabe-und-grenze-bauen](../done/slice-handle-freigabe-und-grenze-bauen.md) liegt in `done/`). [slice-massenladen-atomar-bauen](../open/slice-massenladen-atomar-bauen.md) berührt dieselbe Fehlertabelle in §4 an anderen Zeilen (Merge-Konflikt möglich, kein inhaltlicher).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): der State je Aufrufform verlangt einen Umbau des Katalogs der Codes über `ErrorCode.kt` hinaus; Schnittvorschlag: Spezifikation | Quelltext und Tests | Doku.
- `in-progress` → `open` (blockiert): eine Messung am echten Server (andere Version oder anderer Digest) liefert eine andere Nummer oder einen anderen State (Re-Evaluierungs-Trigger von [ADR-0045](../../adr/0045-sp-execute-unbekanntes-handle-ergibt-fehler-8179-state-4.md)); dann Folge-ADR vor dem Weiterbau.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle drei Liefer-Punkte der DoD erledigt, `make gates` grün, der Review-Report liegt vor, die Verifikation hat die Haken bestätigt, und die Closure-Notiz trägt den Steering-Loop-Lerneintrag sowie die Zuordnung *Messung | Festlegung* je Feld der Antwort (Nummer, Class, State, Text: gemessen in M-6 und M-7; Tokenfolge, weitere Felder und `RESETCONNECTION`: Setzung).

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **Zweite Fundstelle bei der Fortschreibung.** Die Liste der Spec-Änderungen der ADR nennt `docs/user/referenz-konfiguration.md` nicht, die Suche findet dort die Zeile `TDS_UNKNOWN_HANDLE` (`BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`). — **Ausgang:** eingetreten und behoben: die zweite Fundstelle `docs/user/referenz-konfiguration.md` trat wieder auf und wurde im Review gefunden (F-1, F-3, F-7), nicht vom Autor der Fortschreibung; behoben in `65d80508`, Grep des Verifiers über das ganze Repo ohne weitere gesendete Fundstelle von 50009 (Evidence `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`).
- **Tokenfolge und Felder sind Setzung.** Der Client sieht Nummer, Class, State und Text wie am echten Server, die Bytes dahinter sind nicht gemessen; ein Treiber, der sie auswertet, kann abweichen. — **Ausgang:** weiter offen als akzeptiertes Negativ der ADR (Offen 1 und 2): Tokenfolge, RETURNSTATUS, leere ERROR-Felder und der Fall nach `RESETCONNECTION` sind Setzung und in Spezifikation, Matrix, Nutzer-Doku, KDoc und Test so gekennzeichnet; Träger: das geschlossene Register `BEO-TDS/sp-execute-unbekanntes-handle-spec-057-widerspricht-messung` nennt sie im Stand, bei Abweichung am echten Server gilt der Re-Evaluierungs-Trigger der ADR.
- **Wanduhr-Tests unter Last.** Die Tests im Paket `protocol/dispatch` laufen im selben Gate wie bekannte Flakes (`BEO-TDS/wanduhr-tests-flackern-unter-last`); ein roter Lauf ohne Bezug zu diesem Slice ist ein Flake-Fall, kein Befund dieses Slice. — **Ausgang:** nicht eingetreten durch diesen Slice: im Lauf des Verifiers (`make clean-build`, `make e2e`) kein Flake, `ListenerAbortTest` komplett grün; im Gate des Implementers zweimal `ListenerAbortTest > der Listener trennt nach CANCEL_TIMEOUT …` rot, Test außerhalb des Diffs (Ordnungswette laut Review F-8, aus dem Code gelesen, nicht gemessen). Gehört nicht in diesen Slice: Evidence in `BEO-TDS/wanduhr-tests-flackern-unter-last`, Folge-Slice [slice-listener-abort-test-ordnungswette](../open/slice-listener-abort-test-ordnungswette.md).
- **Ein Client prüfte auf 50009.** Ein Test oder Skript außerhalb von `src/test` erwartet 50009. — **Ausgang:** entfallen: keine Fundstelle in `referenztest-dotnet/`, `testserver/`, `tools/` (Suche des Verifiers über Produkt-, Test- und Werkzeugdateien); verbleibende Treffer sind Wächter-Kommentare, die Zusage „gibt es nicht mehr“ und Zeitdokumente.

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register (vorhandene `BEO-<KUERZEL>/<slug>` **zitieren** statt neu
formulieren) · `grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln.

- **Gegenstand:** `sp_execute` mit einem Handle, das die Sitzung nicht hält (nie vergeben, −1, 0, freigegeben, nach `RESETCONNECTION`), ergibt ERROR 8179, Class 16, State 4, Text `Could not find prepared statement with handle <n>.` (`TDS_SIM_UNKNOWN_PREPARED_HANDLE` mit State 4 je Aufrufform, `ErrorMapper.executeUnknownHandle`); Backend nicht gerufen, Zähler unverändert, Verbindung offen; `TDS_UNKNOWN_HANDLE` (50009) entfällt. Spezifikation (`SPEC-057`, `SPEC-030`, `SPEC-022`, `SPEC-060`, Fehlertabelle samt State-Satz, Änderungsprotokoll), Nutzer-Doku, Matrix (Zeile `sp_execute`) und Messprotokoll (M-6) nach [ADR-0045](../../adr/0045-sp-execute-unbekanntes-handle-ergibt-fehler-8179-state-4.md) nachgezogen.
- **Zuordnung Messung | Setzung je Feld der Antwort:** *Nummer 8179, Class 16, State 4, Text mit der Nummer des Handles, ein Fehler, Verbindung offen:* gemessen (M-6, Handle 999999 auf 2022 und 2025; M-7, freigegebene Handles, State 4) · *Tokenfolge (ERROR, RETURNSTATUS 8179, DONEPROC), RETURNSTATUS, leere ERROR-Felder (Servername, Prozedur, Zeile 0), Text für andere Nummern als 999999, Fall nach `RESETCONNECTION`:* Setzung (kein Mitschnitt von `sp_execute` mit unbekanntem Handle unter `referenztest-dotnet/Mitschnitt/messung/`; die Mitschnitte `zusatz-unprepare-*` sind `sp_unprepare`, State 8, M-22/M-23). Zuordnung Test ↔ Festlegung: Handvektor `ErrorMapperTest > sp_execute mit unbekanntem Handle ist ERROR 8179 State 4 in der Form RPC`; nie vergeben, −1, 0: `PreparedHandlesDispatchTest`; freigegeben: `UnprepareDispatchTest`; vor `RESETCONNECTION`: `ResetConnectionDispatchTest`; Folge: `RequestSequenceTest`; Gegenprobe State 8 der Batch-Freigabe in den Unprepare-Tests.
- **Review und Verifikation:** Review `ba72fe81` ([Report](../../../reviews/2026-11-08-ba72fe81-review-slice-sp-execute-unbekanntes-handle-bauen.md); 0 HIGH, 1 MEDIUM, 2 LOW, 6 INFO): F-1, F-2, F-3, F-7 behoben in `65d80508`, F-4 und F-7 in dieser Notiz (Zuordnung oben), F-5 entschieden (siehe unten), F-6 beim Merge, F-8 ins Register. Verifikation `65d80508` ([Bericht](../../../reviews/2026-11-08-65d80508-verifikation-slice-sp-execute-unbekanntes-handle-bauen.md)): DoD-Zeilen 1 bis 3 erfüllt, konform unter den Bedingungen 1 bis 5, in dieser Closure erfüllt. Vier Mutationen des Verifiers (State 4 → 8, Backend gerufen, Form BATCH, Gegenprobe Batch-Freigabe mit State 4) je rot.
- **Nachbesserung ohne zweiten Review (Grenze):** `65d80508` (Behebung F-1, F-2, F-3, F-7; 4 Dateien, Plan-Tabelle, Nutzer-Doku, ein Satz in `SPEC-057`, ein KDoc) hat keinen eigenen Review; der Verifier prüfte sie einzeln gegen den Diff (kein ausführbarer Code); die Einzelprüfung genügt als Maßstab dieser Closure (Entscheid des Planners).
- **Entscheid zur Matrixzeile „Handle-Verwaltung je Sitzung“ (Verifier, Punkt 3; Review F-5):** Die Zeile nennt weder Fehlernummer noch State noch ein Verhalten bei unbekanntem Handle (nur der Titel enthält „unbekanntes Handle“); es gibt nichts Falsches nachzuziehen, die Aussage steht in der Zeile `sp_execute`. Entscheid des Planners: Zeile bleibt unverändert, der DoD-Wortlaut (Liefer-Punkt 3) ist an den Ist-Zustand angeglichen („Zeile `sp_execute` trägt 8179/State 4; die Zeile ‚Handle-Verwaltung je Sitzung‘ blieb unverändert, weil sie keine Nummer/State nennt“). Die Liste der Spec-Änderungen von [ADR-0045](../../adr/0045-sp-execute-unbekanntes-handle-ergibt-fehler-8179-state-4.md) (Accepted, nach AGENTS.md §3.5 nicht überschrieben) nennt die Zeile weiter; die Abweichung steht hier.
- **Gemessen** (Einzelmessungen 2026-11-08, Läufe des Verifiers auf `65d80508`, Lastmittel 14 bis 28 durch fremde Läufe): `make clean-build` Exit 0, 674 s, 1298 Testzeilen `PASSED`, 0 rote; `make e2e` Exit 0, 8 Stufen, 376 s; `make gates FORCE=1` Exit 0; `make flaeche` Exit 0; `make docs-check` Exit 0 (0 Befunde); Zitate der Matrix: `tools/matrix-zitate.sh` gegen das Log des `clean-build`, 344 Zitate, alle `PASSED` (`make matrix-zitate` nicht gefahren). Läufe der Closure nach dem Merge mit `main`: Berichtsabschnitt des Planners (Stand der Gates), nicht hier fortgeschrieben.
- **Grenzen ohne Sensor:** (a) Tokenfolge, RETURNSTATUS, leere ERROR-Felder und der Fall nach `RESETCONNECTION` sind Setzung (ADR-0045 Offen 1 und 2), ein Treiber, der sie auswertet, kann abweichen; (b) Text für andere Handle-Nummern als 999999 ist als gleich gesetzt; (c) die Reaktion eines Treibers auf den Fehler ist nicht gemessen; (d) `docs/plan/spec-abdeckung.md` ist vom Implementer erzeugt und vom Verifier nicht nachgerechnet.
- **Steering-Loop-Eintrag:** Kein Eintrag verkörpert, daher kein `liegt in`; der Eintrag ist gezählt, nicht verkörpert. Gelernt (je unter der Schwelle): (1) *Eine Messung ist Beleg für Nummer, Class, State und Text, nicht für die Bytes dahinter*: die ADR-Entscheidung („zwei Tokens“) und der Bau (drei Tokens nach der Regel für Nummern unter 50000 und dem gemessenen `sp_unprepare`) wichen in einer Setzung voneinander ab; der Review fand sie (F-4), weil die Zuordnung *Messung | Setzung* je Feld im Closure-Trigger verlangt war, nicht weil ein Sensor es sah. Die Klasse führt `BEO-TDS/aussagen-ueber-messung-hinaus` (verkörpert, `AGENTS.md` §3.8); kein neuer Eintrag, keine neue Evidence. (2) *Die verkörperte Prüfung „zweite Fundstelle“ des Reviewer-Skills hat gegriffen, die Vorab-Benennung im Risiko 1 nicht*: der Autor der Fortschreibung ließ `docs/user/referenz-konfiguration.md`, `SPEC-022` und die Tabellenzeile `sp_execute` in `SPEC-030` in der Plan-Tabelle aus; der Planner-Vorschlag „Grep vor dem Abschluss“ lief erst beim Verifier. Gezählt in `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` (verkörpert; Evidence dieses Vorgangs). (3) *Eine Ursache aus dem Code, die ein Test-Flake erklärt, gehört in einen Folge-Slice mit Mutation, nicht in den Slice, in dem er auffiel*: `ListenerAbortTest` (Ordnungswette, F-8). Benannte Spec-Lücke: keine.
- **Beobachtungs-Register (`../observations/`):** geschlossen: [BEO-TDS/sp-execute-unbekanntes-handle-spec-057-widerspricht-messung](../observations/BEO-TDS/sp-execute-unbekanntes-handle-spec-057-widerspricht-messung/state.md) (Stand „geliefert“, `observation.md` unverändert). Fortgeschrieben: [BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen](../observations/BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen/state.md) (Evidence `evidence/slice-sp-execute-unbekanntes-handle-bauen.md`: zweite Fundstelle trat wieder auf, im Review gefunden, nicht vom Autor) und [BEO-TDS/wanduhr-tests-flackern-unter-last](../observations/BEO-TDS/wanduhr-tests-flackern-unter-last/state.md) (Evidence `evidence/slice-sp-execute-unbekanntes-handle-bauen.md` und Nachtrag im Stand: `ListenerAbortTest` zweimal rot im Gate des Implementers, Ursachenhinweis Ordnungswette aus dem Code gelesen, vier Auftritte in Läufen mehrerer Slices; kein Zähler gesetzt, Ausgang beim Nutzer).
- **Folge-Slices:** [slice-listener-abort-test-ordnungswette](../open/slice-listener-abort-test-ordnungswette.md) (S, Welle welle-qualitaet-grundlage: der Test wartet auf den asynchron gestarteten Abbruch, bevor er den Zähler liest; Mutation „`expireGrace()` entfernt“ oder „Abbruch verzögert“ färbt ihn rot) — ist eine Datei in `open/`. Der Bau-Slice [slice-massenladen-atomar-bauen](../open/slice-massenladen-atomar-bauen.md) besteht und folgt in der Reihenfolge.
- **Risiken aus §6:** alle 4 mit Ausgang eingetragen, gezählt in der Reihenfolge von §6 (eingetreten und behoben: 1; weiter offen: 2 (akzeptiertes Negativ der ADR); nicht eingetreten durch diesen Slice: 3 (Evidence und Folge-Slice); entfallen: 4).
- **Drei Paarungen:** Das Repo führt Wellen; die Paarungen prüft die Closure von [welle-testserver-manuell](../welle-testserver-manuell.md). Hier geprüft: *Anker*: kein Anker gesetzt (kein `liegt in`). *Folge-Slice*: [slice-listener-abort-test-ordnungswette](../open/slice-listener-abort-test-ordnungswette.md) und [slice-massenladen-atomar-bauen](../open/slice-massenladen-atomar-bauen.md) bestehen als Dateien in `open/`. *Register*: die drei genannten Kennungen bestehen als Verzeichnis mit nicht leerem `evidence/`.

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung.

**Vorgelagert — Sub-Area-Wahl prüfen:** `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); der Slice berührt Protokollschicht, Spezifikation, Tests und Doku, eine eigene Sub-Area besteht nicht.

**Vorgelagert — offene Beobachtungen sichten:** [`BEO-TDS/sp-execute-unbekanntes-handle-spec-057-widerspricht-messung`](../observations/BEO-TDS/sp-execute-unbekanntes-handle-spec-057-widerspricht-messung/state.md) (Gegenstand), `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` (Risiko 1), `BEO-TDS/aussagen-ueber-messung-hinaus` (verkörpert, `AGENTS.md` §3.8), `BEO-TDS/wanduhr-tests-flackern-unter-last` (Risiko 3).

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
