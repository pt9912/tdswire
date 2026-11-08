# Slice slice-massenladen-atomar-bauen: Eine Nachricht des Massenladens wird ganz oder gar nicht übernommen (Zusage am Stapel-Callback `onInsertBatch`, Einzelzeilen-Rückfall entfällt) nach ADR-0044

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-testserver-manuell

**Bezug:** [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0044](../../adr/0044-massenladen-nachricht-ist-atomar-wie-am-echten-server.md), [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) (soweit von [ADR-0044](../../adr/0044-massenladen-nachricht-ist-atomar-wie-am-echten-server.md) nicht ersetzt), [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md)

**Berührte Spec-Stellen:** SPEC-035 (`onInsertBatch`, Rückfall auf `onInsert`, Zusage „keine Transaktion“) · SPEC-036 (Form B, Fehler des Callbacks, Satz zum Teilbestand) · SPEC-001 (Antwort bei Fehler des Callbacks) · §4 Fehlertabelle, Zeile `TDS_SIM_KEY_VIOLATION` · SPEC-059 (öffentliche Fläche: das KDoc der Zusage von `onInsertBatch` gehört zur Fläche, die Namen ändern sich nicht).

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-08.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar.

**Ziel:** Die Bibliothek erzeugt beim Massenladen (Pakettyp `0x07`) keinen Teilbestand mehr: der Stapel-Callback `onInsertBatch` hat die Zusage „ganz oder gar nicht“ (bei Erfolg die Zahl der übernommenen Zeilen, bei Fehler eine Ausnahme, ohne eine Zeile der Nachricht übernommen zu haben), der Rückfall auf `onInsert` je Zeile entfällt, und `INSERT BULK` ohne `onInsertBatch` ergibt `TDS_UNSUPPORTED_SQL`; die Spezifikation führt die Zusage als Zielbild mit der Grenze „gemessen: Schlüsselverstoß, drei Zeilen, ein Paket (M-12)“ ([ADR-0044](../../adr/0044-massenladen-nachricht-ist-atomar-wie-am-echten-server.md) Entscheidung 1, 2 und 4).

**Herkunft.** [ADR-0044](../../adr/0044-massenladen-nachricht-ist-atomar-wie-am-echten-server.md) (`Accepted`, Entscheid des Nutzers 2026-11-08, Option A, Sammelcode als Grenze). Träger der Folgepflicht Spezifikation und des Nachzugs (Entscheidung 4). Register: [`BEO-TDS/folge-adr-zu-adr-0033-massenladen-verstoss-und-teilbestand`](../observations/BEO-TDS/folge-adr-zu-adr-0033-massenladen-verstoss-und-teilbestand/state.md).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Eine neue öffentliche Fläche, insbesondere ein Ausnahme-Typ oder Signal für den Schlüsselverstoß (Option K-c) — von [ADR-0044](../../adr/0044-massenladen-nachricht-ist-atomar-wie-am-echten-server.md) verworfen für diesen Schnitt; sie hat Re-Evaluierungs-Trigger 3 und keinen Folge-Slice. Der Slice ändert die Zusage am KDoc von `SimTable.onInsertBatch`, keinen Namen (`make flaeche` bleibt grün, Änderungsprotokoll von `SPEC-059` prüfen).
- Die Zuordnung der Fehlercodes je Fehlerursache — der Sammelcode 2627 für jeden Fehler des Callbacks bleibt als benannte Grenze (K-a, Entscheidung 3); die Spezifikation sagt es bei der Zeile `TDS_SIM_KEY_VIOLATION` ausdrücklich („Nummer für andere Ursachen als den Schlüsselverstoß: Setzung des Sammelcodes“).
- Die erweiterte Messung der Atomarität am echten Server (Verstoß an erster und letzter Zeile, zweite von mehreren Nachrichten, Nachricht über mehrere Pakete, mehrere Verstöße, serverseitig erzeugte Verstöße gegen Typ, NULL, Länge) — keine Bedingung des Baus. Wortlaut von [ADR-0044](../../adr/0044-massenladen-nachricht-ist-atomar-wie-am-echten-server.md): die Zusage ist Setzung über den Fall M-12 hinaus („Akzeptiertes Negativ“), ein Fehlschlag der Annahme „ist harmlos für den Bau … und wird vom Trigger gefangen“, und ein Ergebnis der Messung „kann diese ADR nicht ändern, sondern löst Trigger 1 aus“. Die Messung bleibt deshalb als offener Punkt im Register (`state.md` der Beobachtung, Stand „Messung offen“) und ist Auslöser von Re-Evaluierungs-Trigger 1, kein Liefer-Punkt dieses Slice; wer sie will, legt einen Mess-Slice an (Entscheid des Nutzers, Eingang: EULA für das gepinnte Image, lokal, 2026-11-06).
- Eine Transaktions- oder Kompensationslogik in der Bibliothek (Option D) — von der ADR verworfen (Transaktionssemantik in den Anforderungen ausgeschlossen).
- Messungen mit mssql-jdbc (Treiber von d-migrate) und Re-Evaluierungs-Trigger 2 — gehören zu [slice-abnahme-d-migrate](slice-abnahme-d-migrate.md), dort läuft d-migrate mit seinem Treiber gegen den Testserver.
- Die Fehlernummern des `MERGE` (M-15, 2628, 2627, 208) — [slice-merge-antwort-an-messung-angleichen](slice-merge-antwort-an-messung-angleichen.md); der `MERGE`-Pfad mit `onInsert` (`SimulationMerges`, `SimulationChanges`) bleibt unberührt, weil [ADR-0044](../../adr/0044-massenladen-nachricht-ist-atomar-wie-am-echten-server.md) nur Nr. 6 „für das Massenladen“ ersetzt.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung.

Liefer-Punkte (drei):

- [ ] **Spezifikation** ([TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate)): `SPEC-035` (Absatz `onInsertBatch`: Zusage „ganz oder gar nicht“, Rückfall entfällt, Grenze „gemessen M-12“), `SPEC-036` (Form B: Fehler des Callbacks, Satz zum Teilbestand entfällt, `INSERT BULK` ohne `onInsertBatch` ergibt `TDS_UNSUPPORTED_SQL`), `SPEC-001` (Antwort bei Fehler des Callbacks), Zeile `TDS_SIM_KEY_VIOLATION` (Sammelcode als benannte Grenze), `SPEC-059` (Zusage am `onInsertBatch`, Änderungsprotokoll), Änderungsprotokoll der Spezifikation; der Verweis auf [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 5 (iii) und Nr. 6 in `spec/` und im Messprotokoll (Zeile „widerspricht“, Zeile M-12) ist nachgezogen. Beleg: `make docs-check`, `make flaeche`.
- [ ] **Bibliothek und Tests** ([TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)): `simulation/BulkLoads.kt` ruft nur `onInsertBatch` (einmal je Nachricht), der Zweig mit `onInsert` je Zeile ist entfernt; `StatementPlanner` weist `INSERT BULK` ohne `onInsertBatch` mit `TDS_UNSUPPORTED_SQL` ab (heute prüft er `onInsertBatch == null && onInsert == null`); KDoc von `SimTable.onInsertBatch` und `BulkLoads` trägt die Zusage; Tests: die Fälle mit Rückfall auf `onInsert` in `SimulationBackendBulkTest` und `StatementPlannerTest` ersetzt durch „Tabelle nur mit `onInsert` lehnt `INSERT BULK` ab“, Nachricht mit Verstoß in der Mitte lässt den Bestand des Testservers unverändert (Einbettungstest `integrator`, Modul `testserver`, nur öffentliche Fläche); `KompatibilitaetsServer` und `TableStore` des Testservers prüfen, ob sie `onInsertBatch` setzen. Beleg: `make test`, `make detekt`, `make flaeche`.
- [ ] **Doku** ([TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)): Nutzer-Doku im Ist-Zustand (`docs/user/einbettung-und-betrieb.md`: Absatz „Ohne `onInsertBatch` ruft die Bibliothek `onInsert` je Zeile“ entfällt, die Zusage des Stapel-Callbacks steht dort, die Tabelle der Fehler führt `TDS_UNSUPPORTED_SQL` bei `INSERT BULK` ohne `onInsertBatch`); Messprotokoll `docs/messungen/referenzabgleich.md`: die Grenze „ein Fall“ und der Stand der Zusage bei M-12; Kompatibilitätsmatrix, soweit sie das Massenladen mit Teilbestand führt; Stand des Registers fortgeschrieben (Messung offen, Trigger 1). Beleg: `make docs-check`, `make matrix-zitate`.

Nachweis-Pflichten für alle Punkte:

- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Haken der Liefer-Punkte setzt der Planner nach der Verifikation, nicht der Implementer (`BEO-TDS/haken-vor-verifikation-gesetzt`).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist (hier: Zusage am `onInsertBatch`, `SPEC-059`; kein neuer Name).
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben; keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo mit Wellen von der nächsten Welle-Closure.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `spec/spezifikation.md` (`SPEC-035`, `SPEC-036`, `SPEC-001`, Zeile `TDS_SIM_KEY_VIOLATION`, `SPEC-059`, Änderungsprotokoll) | update | Folgepflicht Spezifikation von [ADR-0044](../../adr/0044-massenladen-nachricht-ist-atomar-wie-am-echten-server.md) ([TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate)) |
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/BulkLoads.kt` | update | Rückfall auf `onInsert` je Zeile entfernen (Zeilen ~79 f.); KDoc der Zusage |
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/StatementPlanner.kt` (Zeilen ~104 bis ~114) | update | `INSERT BULK` verlangt `onInsertBatch`; `TDS_UNSUPPORTED_SQL` sonst |
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/SimModel.kt` (KDoc `onInsertBatch`, Zeilen ~15 bis ~17) | update | Zusage „ganz oder gar nicht“, kein Rückfall; Name und Signatur unverändert |
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/SimulationBackend.kt` (KDoc Zeile ~37) | update | nennt „`onInsertBatch` oder `onInsert`“ |
| `testserver/kotlin/io/github/pt9912/tdswire/testserver/{TableStore,KompatibilitaetsServer}.kt` | prüfen | der Bestand übernimmt den Stapel atomar ([ADR-0044](../../adr/0044-massenladen-nachricht-ist-atomar-wie-am-echten-server.md) Kontext); Prüfung, dass der Testserver `onInsertBatch` für jede Tabelle setzt |
| `src/test/kotlin/io/github/pt9912/tdswire/simulation/{SimulationBackendBulkTest,StatementPlannerTest}.kt`, `testserver/test/kotlin/io/github/pt9912/tdswire/testserver/TableStoreTest.kt` | update | Rückfall-Tests ersetzen; neu: Tabelle nur mit `onInsert` lehnt `INSERT BULK` ab (Negative), Fehler in der Mitte lässt den Bestand unverändert (Happy/Boundary), Fehler des Callbacks bleibt 2627 mit `INFO` 3621 ([TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)) |
| `docs/user/einbettung-und-betrieb.md` | update | Ist-Zustand: Zusage des Stapel-Callbacks, Fehlerzeile |
| `docs/messungen/referenzabgleich.md` (M-12, Zeile „widerspricht“) | update | Verweis auf [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 5 (iii) und Nr. 6 nachziehen, Grenze „ein Fall“ |
| `docs/plan/planning/observations/BEO-TDS/folge-adr-zu-adr-0033-massenladen-verstoss-und-teilbestand/state.md` | update | Stand nach dem Bau |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-ddl-abschluss-und-transaktionszustand-angleichen](slice-ddl-abschluss-und-transaktionszustand-angleichen.md) liegt in `done/` (gleiche Dateien in `StatementPlanner`, `TableStore` und `spec/spezifikation.md` — `SPEC-035`, `SPEC-036`; Setzung des Planners wegen gemeinsamer Dateien, kein inhaltlicher Zwang), [ADR-0044](../../adr/0044-massenladen-nachricht-ist-atomar-wie-am-echten-server.md) ist `Accepted` (2026-11-08), und ein Verantwortlicher ist eingetragen. [slice-sp-execute-unbekanntes-handle-bauen](../in-progress/slice-sp-execute-unbekanntes-handle-bauen.md) ist unabhängig (andere Spec-Stellen, anderer Pfad); beide ändern die Fehlertabelle in §4 an verschiedenen Zeilen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): der Entfall des Rückfalls verlangt Änderungen am `MERGE`-Pfad oder an der Fläche über die Zusage hinaus; Schnittvorschlag: Spezifikation | Bibliothek und Tests | Doku.
- `in-progress` → `open` (blockiert): eine Messung am echten Server zeigt Teilbestand nach einem Fehler in einer Nachricht (Re-Evaluierungs-Trigger 1 von [ADR-0044](../../adr/0044-massenladen-nachricht-ist-atomar-wie-am-echten-server.md)); dann Folge-ADR zur Zusage vor dem Weiterbau.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle drei Liefer-Punkte der DoD erledigt, `make gates` grün, der Review-Report liegt vor, die Verifikation hat die Haken bestätigt, und die Closure-Notiz trägt den Steering-Loop-Lerneintrag sowie die Zuordnung *Messung | Festlegung* für die Atomarität (gemessen: M-12, ein Fall; Setzung: darüber hinaus) und für den Fehlercode (gemessen: 2627 beim Schlüsselverstoß; Setzung: Sammelcode für andere Ursachen).

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **Atomarität gilt über M-12 hinaus nur als Setzung.** Eine spätere Messung kann Teilbestand zeigen (Nachricht über mehrere Pakete, zweite Nachricht); das Entfernen des Rückfalls lässt sich zurücknehmen. — **Ausgang:** weiter offen: → `BEO-TDS/folge-adr-zu-adr-0033-massenladen-verstoss-und-teilbestand` im Register (Stand „Messung offen“, Re-Evaluierungs-Trigger 1 der ADR); Ausgang zur Closure festzuhalten.
- **Bruch für Integratoren mit nur `onInsert`.** `INSERT BULK` ergibt für sie `TDS_UNSUPPORTED_SQL` (Fläche besteht seit 2026-11-07, vor `1.0` nur durch Änderung der Spezifikation fest). — **Ausgang:** offen bis zur Closure; akzeptiertes Negativ der ADR, in der Nutzer-Doku benannt.
- **Die Zusage ist nicht erzwingbar.** Ein Integrator, der sie bricht, erzeugt Teilbestand; die Bibliothek nimmt nichts zurück. — **Ausgang:** offen bis zur Closure; akzeptiertes Negativ der ADR, Zusage im KDoc und in `SPEC-035`.
- **Zweite Fundstelle bei der Fortschreibung.** Der Rückfall steht in Spec, KDoc (`SimModel`, `BulkLoads`, `SimulationBackend`), Nutzer-Doku und Messprotokoll (`BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`). — **Ausgang:** offen bis zur Closure (Grep über das ganze Repo nach „Teilbestand“ und „`onInsert` je Zeile“).
- **Gemeinsame Dateien mit [slice-ddl-abschluss-und-transaktionszustand-angleichen](slice-ddl-abschluss-und-transaktionszustand-angleichen.md).** Gleiche Pfade in `StatementPlanner`, `TableStore` und Spec; ein Start vor dessen `done/` erzeugt Merge-Konflikte. — **Ausgang:** offen bis zur Closure (Start-Trigger in §4 fängt es).

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register (vorhandene `BEO-<KUERZEL>/<slug>` **zitieren** statt neu
formulieren) · `grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln.

Wird bei der Closure gefüllt.

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung.

**Vorgelagert — Sub-Area-Wahl prüfen:** `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); der Slice berührt Bibliothek (`simulation/`), Modul `testserver`, Spezifikation und Doku, eine eigene Sub-Area besteht nicht.

**Vorgelagert — offene Beobachtungen sichten:** [`BEO-TDS/folge-adr-zu-adr-0033-massenladen-verstoss-und-teilbestand`](../observations/BEO-TDS/folge-adr-zu-adr-0033-massenladen-verstoss-und-teilbestand/state.md) (Gegenstand), `BEO-TDS/aussagen-ueber-messung-hinaus` (ein Fall, Allaussage; verkörpert in `AGENTS.md` §3.8), `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` (Risiko 4), `BEO-TDS/massenladen-ignore-bit-laesst-zustand-stehen` (anderer Gegenstand, gleicher Pfad `BulkLoads`; nicht Teil dieses Slice).

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
