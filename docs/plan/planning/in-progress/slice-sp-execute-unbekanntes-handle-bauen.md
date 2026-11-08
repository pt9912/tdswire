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

- [ ] **Spezifikation** ([TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc)): `spec/spezifikation.md` folgt der Liste der Spec-Änderungen von [ADR-0045](../../adr/0045-sp-execute-unbekanntes-handle-ergibt-fehler-8179-state-4.md): `SPEC-057` (Nummer 8179, Class 16, State 4, Text; „Behandlung nicht entschieden“ entfällt; Tokenfolge und weitere Felder als Setzung), `SPEC-030` (Absätze `sp_execute` und `RESETCONNECTION`), `SPEC-060` und Zeile `TDS_SIM_UNKNOWN_PREPARED_HANDLE` (State 8 bei Batch-Freigabe, 4 bei `sp_execute`; beides gemessen), Zeile `TDS_UNKNOWN_HANDLE` (entfällt oder „nicht mehr gesendet“), Satz zu den State-Ausnahmen in §4 (State 4 zusätzlich), Änderungsprotokoll der Spezifikation; dieselbe Fehlertabelle in `docs/user/referenz-konfiguration.md` (zweite Fundstelle, die Liste der ADR nennt sie nicht). Beleg: `make docs-check`.
- [ ] **Quelltext und Tests** ([TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)): der Zweig von `sp_execute` mit nicht gehaltenem Handle in `protocol/dispatch/RequestLoop.kt` (bisher `ErrorCode.TDS_UNKNOWN_HANDLE`) sendet `TDS_SIM_UNKNOWN_PREPARED_HANDLE` mit State 4; Katalog der Codes in `protocol/error/ErrorCode.kt` und `ErrorMapper` tragen den State je Aufrufform (Form im Quelltext: Bau nach den Regeln der Schichten); Backend nicht gerufen, Zähler unverändert, Verbindung offen. Tests: bestehende angepasst (`PreparedHandlesDispatchTest`, `ResetConnectionDispatchTest`, `RequestSequenceTest`, `ErrorMapperTest`, `UnprepareDispatchTest`), neu ein Handvektor 8179/State 4 für nie vergeben, −1, 0, freigegeben und nach `RESETCONNECTION`, Gegenprobe State 8 bei der Batch-Freigabe; Referenzclient-Tests gegen den Serverprozess, soweit einer 50009 erwartet (zum Planungszeitpunkt keine Fundstelle in `referenztest-dotnet/`). Beleg: `make test`, `make detekt`.
- [ ] **Doku** ([TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)): Kompatibilitätsmatrix (`docs/plan/kompatibilitaetsmatrix.md`, Zeilen `sp_execute` und „Handle-Verwaltung je Sitzung“) führt 8179/State 4; Messprotokoll `docs/messungen/referenzabgleich.md`, Zeile M-6: der Hinweis „`SPEC-057` und `TDS_UNKNOWN_HANDLE` bleiben bis dahin unverändert“ ist eingelöst; Stand des Registers fortgeschrieben. Beleg: `make docs-check`, `make matrix-zitate`.

Nachweis-Pflichten für alle Punkte:

- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Haken der Liefer-Punkte setzt der Planner nach der Verifikation, nicht der Implementer (`BEO-TDS/haken-vor-verifikation-gesetzt`).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist (hier: Fehlertabelle der Nutzer-Doku; `SPEC-059` nur, falls der Aufzählungswert `TDS_UNKNOWN_HANDLE` zu einem öffentlichen Typ gehört, `make flaeche` entscheidet).
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben; keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo mit Wellen von der nächsten Welle-Closure.

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

- **Zweite Fundstelle bei der Fortschreibung.** Die Liste der Spec-Änderungen der ADR nennt `docs/user/referenz-konfiguration.md` nicht, die Suche findet dort die Zeile `TDS_UNKNOWN_HANDLE` (`BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`). — **Ausgang:** offen bis zur Closure (Grep über das ganze Repo vor dem Abschluss).
- **Tokenfolge und Felder sind Setzung.** Der Client sieht Nummer, Class, State und Text wie am echten Server, die Bytes dahinter sind nicht gemessen; ein Treiber, der sie auswertet, kann abweichen. — **Ausgang:** offen bis zur Closure; akzeptiertes Negativ der ADR (Offen 1), bei Abweichung Re-Evaluierungs-Trigger.
- **Wanduhr-Tests unter Last.** Die Tests im Paket `protocol/dispatch` laufen im selben Gate wie bekannte Flakes (`BEO-TDS/wanduhr-tests-flackern-unter-last`); ein roter Lauf ohne Bezug zu diesem Slice ist ein Flake-Fall, kein Befund dieses Slice. — **Ausgang:** offen bis zur Closure.
- **Ein Client prüfte auf 50009.** Ein Test oder Skript außerhalb von `src/test` erwartet 50009. — **Ausgang:** offen bis zur Closure (Suche in `referenztest-dotnet/`, `testserver/`, `tools/`; zum Planungszeitpunkt keine Fundstelle in Test- und Quelldateien dort).

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register (vorhandene `BEO-<KUERZEL>/<slug>` **zitieren** statt neu
formulieren) · `grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln.

Wird bei der Closure gefüllt.

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung.

**Vorgelagert — Sub-Area-Wahl prüfen:** `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); der Slice berührt Protokollschicht, Spezifikation, Tests und Doku, eine eigene Sub-Area besteht nicht.

**Vorgelagert — offene Beobachtungen sichten:** [`BEO-TDS/sp-execute-unbekanntes-handle-spec-057-widerspricht-messung`](../observations/BEO-TDS/sp-execute-unbekanntes-handle-spec-057-widerspricht-messung/state.md) (Gegenstand), `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` (Risiko 1), `BEO-TDS/aussagen-ueber-messung-hinaus` (verkörpert, `AGENTS.md` §3.8), `BEO-TDS/wanduhr-tests-flackern-unter-last` (Risiko 3).

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
