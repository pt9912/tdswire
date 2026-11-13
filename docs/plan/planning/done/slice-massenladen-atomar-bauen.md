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

**Berührte Spec-Stellen:** SPEC-035 (`onInsertBatch`, Rückfall auf `onInsert`, Zusage „keine Transaktion“) · SPEC-036 (Form B, Fehler des Callbacks, Satz zum Teilbestand) · SPEC-001 (Antwort bei Fehler des Callbacks) · §4 Fehlertabelle, Zeile `TDS_SIM_KEY_VIOLATION` · Schritt 10 von [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) `.a` (Massenladen) · SPEC-059 (öffentliche Fläche: das KDoc der Zusage von `onInsertBatch` gehört zur Fläche, die Namen ändern sich nicht).

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
- Messungen mit mssql-jdbc (Treiber von d-migrate) und Re-Evaluierungs-Trigger 2 — gehören zu [slice-abnahme-d-migrate](../done/slice-abnahme-d-migrate.md), dort läuft d-migrate mit seinem Treiber gegen den Testserver.
- Die Fehlernummern des `MERGE` (M-15, 2628, 2627, 208) — [slice-merge-antwort-an-messung-angleichen](../done/slice-merge-antwort-an-messung-angleichen.md); der `MERGE`-Pfad mit `onInsert` (`SimulationMerges`, `SimulationChanges`) bleibt unberührt, weil [ADR-0044](../../adr/0044-massenladen-nachricht-ist-atomar-wie-am-echten-server.md) nur Nr. 6 „für das Massenladen“ ersetzt.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung.

Liefer-Punkte (drei):

- [x] **Spezifikation** ([TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate)): `SPEC-035` (Absatz `onInsertBatch`: Zusage „ganz oder gar nicht“, Rückfall entfällt, Grenze „gemessen M-12“), `SPEC-036` (Form B: Fehler des Callbacks, Satz zum Teilbestand entfällt, `INSERT BULK` ohne `onInsertBatch` ergibt `TDS_UNSUPPORTED_SQL`), `SPEC-001` (Antwort bei Fehler des Callbacks), Zeile `TDS_SIM_KEY_VIOLATION` (Sammelcode als benannte Grenze), `SPEC-059` (Zusage am `onInsertBatch`, Änderungsprotokoll), Änderungsprotokoll der Spezifikation; der Verweis auf [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 5 (iii) und Nr. 6 in `spec/` und im Messprotokoll (Zeile „widerspricht“, Zeile M-12) ist nachgezogen. Beleg: `make docs-check`, `make flaeche`.
- [x] **Bibliothek und Tests** ([TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)): `simulation/BulkLoads.kt` ruft nur `onInsertBatch` (einmal je Nachricht), der Zweig mit `onInsert` je Zeile ist entfernt; `StatementPlanner` weist `INSERT BULK` ohne `onInsertBatch` mit `TDS_UNSUPPORTED_SQL` ab (heute prüft er `onInsertBatch == null && onInsert == null`); KDoc von `SimTable.onInsertBatch` und `BulkLoads` trägt die Zusage; Tests: die Fälle mit Rückfall auf `onInsert` in `SimulationBackendBulkTest` und `StatementPlannerTest` ersetzt durch „Tabelle nur mit `onInsert` lehnt `INSERT BULK` ab“, Nachricht mit Verstoß in der Mitte lässt den Bestand unverändert (Einbettungstest `integrator`, nur öffentliche Fläche, trägt den Anteil der Bibliothek an einem Fake des Integrators; den Bestand des Testservers tragen `TableStoreTest` und `SqlBulkCopyTests`, siehe §7 — das Source-Set `integrator` sieht `TableStore` nicht); `KompatibilitaetsServer` und `TableStore` des Testservers prüfen, ob sie `onInsertBatch` setzen. Beleg: `make test`, `make detekt`, `make flaeche`.
- [x] **Doku** ([TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)): Nutzer-Doku im Ist-Zustand (`docs/user/einbettung-und-betrieb.md`: Absatz „Ohne `onInsertBatch` ruft die Bibliothek `onInsert` je Zeile“ entfällt, die Zusage des Stapel-Callbacks steht dort, die Tabelle der Fehler führt `TDS_UNSUPPORTED_SQL` bei `INSERT BULK` ohne `onInsertBatch`); Messprotokoll `docs/messungen/referenzabgleich.md`: die Grenze „ein Fall“ und der Stand der Zusage bei M-12; Kompatibilitätsmatrix, soweit sie das Massenladen mit Teilbestand führt; Stand des Registers fortgeschrieben (Messung offen, Trigger 1). Beleg: `make docs-check`, `make matrix-zitate`.

Nachweis-Pflichten für alle Punkte:

- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Haken der Liefer-Punkte setzt der Planner nach der Verifikation, nicht der Implementer (`BEO-TDS/haken-vor-verifikation-gesetzt`).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist (hier: Zusage am `onInsertBatch`, `SPEC-059`; kein neuer Name).
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben; keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo mit Wellen von der nächsten Welle-Closure.

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
| `testserver/kotlin/io/github/pt9912/tdswire/testserver/{TableStore,KompatibilitaetsServer}.kt` | prüfen (geprüft, nichts zu ändern: beide Dateien liegen nicht im Diff; `TableStore`-Tabellen setzen `onInsertBatch`, `person` in `KompatibilitaetsServer` ist kein Ziel des Massenladens) | der Bestand übernimmt den Stapel atomar ([ADR-0044](../../adr/0044-massenladen-nachricht-ist-atomar-wie-am-echten-server.md) Kontext); Prüfung, dass der Testserver `onInsertBatch` für jede Tabelle setzt |
| `src/test/kotlin/io/github/pt9912/tdswire/simulation/{SimulationBackendBulkTest,StatementPlannerTest}.kt`, `testserver/test/kotlin/io/github/pt9912/tdswire/testserver/TableStoreTest.kt` | update (geändert: `SimulationBackendBulkTest`; `StatementPlannerTest` und `TableStoreTest` geprüft, nichts zu ändern, beide nicht im Diff) | Rückfall-Tests ersetzen; neu: Tabelle nur mit `onInsert` lehnt `INSERT BULK` ab (Negative), Fehler in der Mitte lässt den Bestand unverändert (Happy/Boundary), Fehler des Callbacks bleibt 2627 mit `INFO` 3621 ([TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)) |
| `docs/user/einbettung-und-betrieb.md` | update | Ist-Zustand: Zusage des Stapel-Callbacks, Fehlerzeile |
| `docs/messungen/referenzabgleich.md` (M-12, Zeile „widerspricht“) | update | Verweis auf [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 5 (iii) und Nr. 6 nachziehen, Grenze „ein Fall“ |
| `docs/plan/planning/observations/BEO-TDS/folge-adr-zu-adr-0033-massenladen-verstoss-und-teilbestand/state.md` | update | Stand nach dem Bau |

Ergänzt im Lauf des Implementers (gegen `git diff --name-only main...HEAD`):

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/integrator/kotlin/io/github/pt9912/tdswire/integrator/MassenladenEmbeddingTest.kt` | update | Einbettungstest: Nachricht mit Verstoß in der Mitte, nur öffentliche Fläche |
| `docs/plan/kompatibilitaetsmatrix.md` | update | Zeile `BULK_LOAD`: gemessen (ein Fall) getrennt von Festlegung |
| `docs/messungen/bulk-load-2026-11-07.md` | update | Mutationstabelle und Stand der Zusage |
| `docs/plan/spec-abdeckung.md` | update (erzeugt) | `make spec-abdeckung` nach den Testtags |
| `docs/plan/planning/in-progress/roadmap.md`, `docs/plan/planning/done/welle-testserver-manuell.md` | update | Stand der Wellenzeile |
| `docs/plan/planning/done/slice-adr-massenladen-atomar-an-messung.md`, `docs/plan/planning/done/slice-ddl-abschluss-und-transaktionszustand-angleichen.md`, `docs/plan/planning/done/slice-sp-execute-unbekanntes-handle-bauen.md` | update | Verweise auf den Slice nachgezogen |

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-ddl-abschluss-und-transaktionszustand-angleichen](../done/slice-ddl-abschluss-und-transaktionszustand-angleichen.md) liegt in `done/` (gleiche Dateien in `StatementPlanner`, `TableStore` und `spec/spezifikation.md` — `SPEC-035`, `SPEC-036`; Setzung des Planners wegen gemeinsamer Dateien, kein inhaltlicher Zwang), [ADR-0044](../../adr/0044-massenladen-nachricht-ist-atomar-wie-am-echten-server.md) ist `Accepted` (2026-11-08), und ein Verantwortlicher ist eingetragen. [slice-sp-execute-unbekanntes-handle-bauen](../done/slice-sp-execute-unbekanntes-handle-bauen.md) ist unabhängig (andere Spec-Stellen, anderer Pfad); beide ändern die Fehlertabelle in §4 an verschiedenen Zeilen.

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

- **Atomarität gilt über M-12 hinaus nur als Setzung.** Eine spätere Messung kann Teilbestand zeigen (Nachricht über mehrere Pakete, zweite Nachricht); das Entfernen des Rückfalls lässt sich zurücknehmen. — **Ausgang:** weiter offen: Setzung über den Fall M-12 hinaus; Träger ist das Register [`BEO-TDS/folge-adr-zu-adr-0033-massenladen-verstoss-und-teilbestand`](../observations/BEO-TDS/folge-adr-zu-adr-0033-massenladen-verstoss-und-teilbestand/state.md) (Stand „Bau geliefert, Messung offen“), Auslöser ist Re-Evaluierungs-Trigger 1 von [ADR-0044](../../adr/0044-massenladen-nachricht-ist-atomar-wie-am-echten-server.md); die Messung ist kein Liefer-Punkt dieses Slice.
- **Bruch für Integratoren mit nur `onInsert`.** `INSERT BULK` ergibt für sie `TDS_UNSUPPORTED_SQL` (Fläche besteht seit 2026-11-07, vor `1.0` nur durch Änderung der Spezifikation fest). — **Ausgang:** eingetreten (akzeptiertes Negativ der ADR): `INSERT BULK` ergibt für eine Tabelle nur mit `onInsert` `TDS_UNSUPPORTED_SQL`; benannt als **Inkompatibel** im Änderungsprotokoll von `SPEC-059`, in `SPEC-036` und in der Nutzer-Doku (eigene Zeile der Fehlertabelle); `person` im `KompatibilitaetsServer` ist kein Ziel des Massenladens und nicht betroffen.
- **Die Zusage ist nicht erzwingbar.** Ein Integrator, der sie bricht, erzeugt Teilbestand; die Bibliothek nimmt nichts zurück. — **Ausgang:** weiter offen (akzeptiertes Negativ der ADR): die Bibliothek nimmt nichts zurück; die Zusage steht im KDoc von `SimTable.onInsertBatch` (mit der Grenze „ein gemessener Fall“) und in `SPEC-035`.
- **Zweite Fundstelle bei der Fortschreibung.** Der Rückfall steht in Spec, KDoc (`SimModel`, `BulkLoads`, `SimulationBackend`), Nutzer-Doku und Messprotokoll (`BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`). — **Ausgang:** eingetreten in einem Rest: Verifikation V-2, die Zeile „Zusage“ der Tabelle `SPEC-059` sagte zu `onInsertBatch` „dieser Stand ruft ihn nicht“ (seit dem Schreibpfad des Massenladens falsch, vom Grep des Slice nicht genannt); behoben in der Closure (Aussage dem Ist-Zustand angeglichen, im Änderungsprotokoll vermerkt). Die Fundstellen im KDoc, in der Nutzer-Doku (F-2) und im Messprotokoll (F-1) hat der Review gefunden und der Implementer behoben; weitere Fundstelle von „Teilbestand“ und „`onInsert` je Zeile“ nach dem Merge: keine (Grep des Planners, nur Zeitdokumente).
- **Gemeinsame Dateien mit [slice-ddl-abschluss-und-transaktionszustand-angleichen](../done/slice-ddl-abschluss-und-transaktionszustand-angleichen.md).** Gleiche Pfade in `StatementPlanner`, `TableStore` und Spec; ein Start vor dessen `done/` erzeugt Merge-Konflikte. — **Ausgang:** entfallen: der Start lag nach dem `done/` von [slice-ddl-abschluss-und-transaktionszustand-angleichen](../done/slice-ddl-abschluss-und-transaktionszustand-angleichen.md); der Merge mit `main` ergab fünf Konflikte, alle in der Dokumentation und aus [slice-sp-execute-unbekanntes-handle-bauen](../done/slice-sp-execute-unbekanntes-handle-bauen.md), keiner im Code und keiner mit `slice-ddl-…`.

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register (vorhandene `BEO-<KUERZEL>/<slug>` **zitieren** statt neu
formulieren) · `grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln.

- **Gegenstand:** Die Nachricht des Massenladens (Pakettyp `0x07`) wird ganz oder gar nicht übernommen: `BulkLoads` ruft nur `onInsertBatch`, einmal je Nachricht (leere Nachricht kein Aufruf), der Rückfall auf `onInsert` je Zeile ist entfernt; `StatementPlanner` weist `INSERT BULK` ohne `onInsertBatch` mit `TDS_UNSUPPORTED_SQL` ab, auch wenn `onInsert` gesetzt ist; die Zusage steht im KDoc von `SimTable.onInsertBatch` (mit der Grenze), `BulkLoads` und `SimulationBackend`. Spezifikation (`SPEC-035`, `SPEC-036`, `SPEC-001`, Fehlertabelle Zeile `TDS_SIM_KEY_VIOLATION`, Schritt 10 von `TDS-FA-11.a`, `SPEC-059` samt Änderungsprotokoll mit **Inkompatibel**), Nutzer-Doku, Messprotokolle, Matrix-Zeile `BULK_LOAD` und Register nach [ADR-0044](../../adr/0044-massenladen-nachricht-ist-atomar-wie-am-echten-server.md) nachgezogen. Namen und Signaturen der öffentlichen Fläche unverändert (`make flaeche` grün).
- **Zuordnung Messung | Festlegung (Closure-Trigger §5):** *Atomarität:* gemessen ist **ein** Fall (M-12: Schlüsselverstoß, drei Zeilen, ein Paket, Verstoß in der Mitte; 2022 und 2025; vom Verifier aus den Rohbytes selbst dekodiert: Nachricht 9 Pakettyp `0x07` mit drei ROW-Token, Nachricht 10 ERROR 2627, Class 14, danach `COUNT_BIG(*)` = 1, also keine Zeile der Nachricht im Bestand) · Setzung: alles darüber hinaus (Verstoß an erster oder letzter Zeile, zweite von mehreren Nachrichten, Nachricht über mehrere Pakete, mehrere Verstöße, serverseitige Verstöße gegen Typ, NULL, Länge). *Fehlercode:* gemessen ist 2627 beim Schlüsselverstoß · Setzung: der Sammelcode 2627 für jede andere Ursache eines Fehlers des Callbacks (K-a, Entscheidung 3 der ADR). Die Grenze steht in `SPEC-035`, `SPEC-036`, `SPEC-001`, Fehlertabelle, KDoc, Nutzer-Doku, Messprotokoll und Matrix im selben Satz wie die Aussage.
- **Review und Verifikation:** Review `9e144097` ([Report](../../../reviews/2026-11-08-9e144097-review-slice-massenladen-atomar-bauen.md); 0 HIGH, 3 MEDIUM, 4 INFO; sieben Mutationen rot): F-1 (Mutationszeile M27 nannte die gedachte Wirkung), F-2 (Fehlertabelle der Nutzer-Doku ohne Zeile für `INSERT BULK`), F-3 (Plan-Tabelle ohne berührte Dateien), F-7 (Grenze nur im internen KDoc) behoben in `128378b8`; F-4 (Ablehnungstext „Einfügen für diese Tabelle nicht verfügbar“ nennt die Ursache nicht) bewusst offen, in der Nutzer-Doku benannt; F-6 behoben; F-5 siehe unten. Verifikation `128378b8` ([Bericht](../../../reviews/2026-11-08-128378b8-verifikation-slice-massenladen-atomar-bauen.md)): DoD-Zeilen 1 bis 3 erfüllt, closure-fähig unter B1 bis B3, in dieser Closure erfüllt. Drei Mutationen des Verifiers (Planer prüft wieder `onInsert`; Rückfall in `BulkLoads`; Aufruf je Zeile statt einmal je Nachricht) je rot in den vom Plan genannten Klassen; die Mutation „Rückfall in `BulkLoads`“ färbt den Einbettungstest nicht, weil der Planer die Tabelle vorher abweist (erklärtes Verhalten, kein Mangel).
- **Wer trägt „Bestand des Testservers“ (B1, Review F-5, Verifikation V-1):** Der Wortlaut des Liefer-Punkts 2 verlangte einen Einbettungstest `integrator` im Modul `testserver`; das ist nicht wörtlich erfüllbar, weil das Source-Set `integrator` gegen die öffentliche Fläche des Hauptmoduls übersetzt und `TableStore` des Moduls `testserver` nicht sieht ([ADR-0034](../../adr/0034-testserver-als-gradle-modul.md)). Entscheid des Planners: der Wortlaut der DoD ist berichtigt (§2), kein neuer Test nötig, die Zusage ist in der Sache getragen:
    - `MassenladenEmbeddingTest` (Source-Set `integrator`, Fake des Integrators) trägt den **Anteil der Bibliothek**: ein Aufruf mit allen Zeilen, Fehlerantwort, Verbindung bleibt offen, `onInsert` nie gerufen (nicht die Nummer 2627, nur das Fehlerbit; die Nummer tragen `SimulationBackendBulkTest` und `ErrorMapperTest`).
    - Den **Bestand des Testservers** tragen (a) `TableStoreTest > eine fehlerhafte Nachricht aendert den Bestand nicht` (Vorbestand; sechs Fehlerfälle direkt an `TableStore.insertBatch`, der Bestand behält genau die Zeile davor; nicht über den Draht), (b) `SqlBulkCopyTests.Schluesselverstoss_endet_mit_2627_ohne_Zeile_der_Nachricht_…` (Vorbestand; Draht, Referenzclient, Stufe 3 von `make e2e`, im Lauf des Verifiers bestanden) und (c) von Hand der Lauf mit d-migrate 1.12.0 gegen `make testserver-up` (Verifier): `data import` Exit 0; derselbe Satz noch einmal Exit 5 mit unverändertem Export (`cmp`); Import mit Verstoß in der Mitte (Ids 7, 1, 8) Exit 5, Zeilen 7 und 8 nicht im Bestand; `data transfer` Exit 0, ein zweites Mal Exit 5, Ziel unverändert. (c) ist kein Gate.
    - Grenze: (a) und (b) sind Vorbestand und von keiner Mutation dieses Slice gefärbt; ob eine Mutation in `TableStore.insertBatch` sie färbt, ist nicht gefragt worden. Der Treiber mssql-jdbc gegen den echten Server ist nicht gemessen (Re-Evaluierungs-Trigger 2 von [ADR-0044](../../adr/0044-massenladen-nachricht-ist-atomar-wie-am-echten-server.md), [slice-abnahme-d-migrate](../done/slice-abnahme-d-migrate.md)).
- **Nachbesserung ohne zweiten Review (Grenze):** `128378b8` (Behebung F-1 bis F-3, F-6, F-7; ein KDoc-Satz, Nutzer-Doku, Messprotokoll, Plan-Tabelle, ein Verweis, **keine Programmlogik**) hat keinen eigenen Review; der Verifier prüfte sie einzeln gegen F-1 bis F-7 und las die Doku-Änderungen selbst (Entscheid des Planners: genügt als Maßstab dieser Closure).
- **Gemessen** (Einzelmessungen 2026-11-08, Host mit Last durch fremde Läufe): Läufe des Verifiers auf `128378b8`: `make clean-build` Exit 0, 9 min 51 s, 1300 `PASSED`, 0 `FAILED`, 0 `SKIPPED` (`:test`, `:testserver:test`, `integratorTest`); `make e2e` Exit 0, „gruen (8 Stufen, 447 s)“, Stufe 3 53 s, kein Flake; Zitate der Matrix: `tools/matrix-zitate.sh` gegen das Log des `clean-build`, 343 Zitate, alle `PASSED` (Menge der Zitate gegen `a830d6d1` gleich; `make matrix-zitate` nicht gefahren); `make flaeche` und `make docs-check` Exit 0; `make gates FORCE=1` Exit 0 (35 s, Docker-Cache: belegt die Verdrahtung, nicht die Frische). Closure (Zweig nach Merge von `main` `142bdb20`; Konflikte nur in der Dokumentation): `make docs-check` Exit 0 (1315 Dateien, 0 Befunde, nach Nachzug von drei Verweisen; der erste Lauf fand sie, ein zweiter die Kennung `slice-` in der Spezifikation), `make flaeche` Exit 0, `make kopplung-zahlen` Exit 0, `make gates` Exit 0 (7 min 46 s, Test-Stage nach dem Merge frisch: 1300 `PASSED`, keine rote Zeile; die Zeile „stempel-gruppe: tests gruen, Stempel nicht geschrieben“ ist die Ausgabe des Stempel-Schritts im Worktree, das Gate selbst war grün), `make spec-abdeckung` Exit 0 (3 min 37 s; `docs/plan/spec-abdeckung.md` unverändert, also die Fassung des Merges; 6 Festlegungen ohne Nachweis, vorbestehend). Nicht gefahren, nach Zuschnitt der Closure: `make clean-build`, `make e2e`, `make matrix-zitate` (Läufe des Verifiers tragen den Stand, nach dem Verifikationsbericht änderte sich kein Quelltext, kein Test und keine Matrix-Zeile). Nicht belegt: das Verhalten des echten SQL Servers über M-12 hinaus.
- **Steering-Loop-Eintrag:** Kein Eintrag verkörpert, daher kein `liegt in`; der Eintrag ist gezählt, nicht verkörpert, und trägt als benannte Lücke die Setzung der Atomarität über M-12 hinaus im Register (`BEO-TDS/folge-adr-zu-adr-0033-massenladen-verstoss-und-teilbestand`, Trigger 1; die Setzung steht in der Spezifikation als Setzung, keine `LH-*` nötig). Gelernt: (1) *Eine Klammer im Liefer-Punkt, die zwei Source-Sets oder Module verbindet, die einander nicht sehen, ist beim Schnitt nicht lesbar und erst im Review aufgefallen*: der Planner schrieb „Einbettungstest `integrator`, Modul `testserver`“, die Regel von `.claude/commands/plan-welle.md` Schritt 6b (Frage 1, „sieht das Werkzeug den Lieferpunkt“) bestand schon, ihre Beispielliste nennt Source-Set und Modul nicht; Wiederauftreten in `BEO-TDS/dod-wortlaut-ueber-der-bauart` (Evidence dieser Closure). Ob 6b um „Source-Set oder Modul des Tests: welche Typen sieht er“ ergänzt wird, ist Entscheid des Nutzers (wie die offene Ausdehnung dort), hier nicht geändert. (2) *Finding-Klassen des Reviews* und ihre Zuordnung: „Mutationsbeschreibung nennt die gedachte statt der gemessenen Wirkung“ (F-1) und „Grenze nur im internen KDoc“ (F-7) → `BEO-TDS/aussagen-ueber-messung-hinaus` (verkörpert, `AGENTS.md` §3.8); „Nutzer-Doku nicht komplett gegen den Slice gelesen“ (F-2) und „Verweisziel nachgezogen, Prosa nicht“ (F-6) sowie V-2 → `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` (verkörpert im Reviewer-Skill; das Muster griff im Review, nicht im Plan); „Plan-Tabelle ohne berührte Datei“ (F-3) → `BEO-TDS/plan-tabelle-nennt-neue-datei-nicht` (verkörpert); „Test deckt Fake und Bibliotheksanteil, DoD nennt den Bestand“ (F-5) → `BEO-TDS/dod-wortlaut-ueber-der-bauart`. Keine Klasse ist neu, keine hat in diesem Lauf allein die Schwelle erreicht; die vier Zuordnungen sind als Evidence eingetragen. (3) *Eine zweite Fundstelle derselben Aussage kann in einer Tabelle stehen, die der Slice ohnehin ändert* (V-2: „dieser Stand ruft ihn nicht“ in der Zeile „Zusage“ von `SPEC-059`, in der der Slice die Nachweis-Zeile schrieb): der Grep des Slice nannte die Wendung nicht, `make flaeche` vergleicht Typnamen, nicht Sätze; gefunden hat sie der Verifier.
- **Beobachtungs-Register (`../observations/`):** Fortgeschrieben: [BEO-TDS/folge-adr-zu-adr-0033-massenladen-verstoss-und-teilbestand](../observations/BEO-TDS/folge-adr-zu-adr-0033-massenladen-verstoss-und-teilbestand/state.md) (Stand „Bau geliefert, Messung offen“, `observation.md` unverändert; Messung als Trigger 1 offen). Evidence ergänzt in [BEO-TDS/aussagen-ueber-messung-hinaus](../observations/BEO-TDS/aussagen-ueber-messung-hinaus/state.md), [BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen](../observations/BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen/state.md), [BEO-TDS/plan-tabelle-nennt-neue-datei-nicht](../observations/BEO-TDS/plan-tabelle-nennt-neue-datei-nicht/state.md) und [BEO-TDS/dod-wortlaut-ueber-der-bauart](../observations/BEO-TDS/dod-wortlaut-ueber-der-bauart/state.md) (je `evidence/slice-massenladen-atomar-bauen.md`). Haken erst nach der Verifikation gesetzt (`BEO-TDS/haken-vor-verifikation-gesetzt` eingehalten).
- **Folge-Slices:** keiner angelegt. Die erweiterte Messung der Atomarität ist ein Mess-Slice auf Entscheid des Nutzers (Eingang: EULA für das gepinnte Image, lokal), nicht angelegt; ohne sie bleibt die Setzung offen (Register, Trigger 1). Messung des Treibers mssql-jdbc: [slice-abnahme-d-migrate](../done/slice-abnahme-d-migrate.md).
- **Risiken aus §6:** alle 5 mit Ausgang eingetragen, gezählt in der Reihenfolge von §6 (weiter offen: 1 und 3; eingetreten: 2 (akzeptiertes Negativ der ADR) und 4 (in einem Rest, behoben); entfallen: 5).
- **Drei Paarungen:** Das Repo führt Wellen; die Paarungen prüft die Closure von [welle-testserver-manuell](welle-testserver-manuell.md). Hier geprüft: *Anker*: kein Anker gesetzt (kein `liegt in`). *Folge-Slice*: [slice-abnahme-d-migrate](../done/slice-abnahme-d-migrate.md) besteht als Datei in `open/`. *Register*: die fünf genannten Kennungen bestehen als Verzeichnis mit nicht leerem `evidence/`.

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung.

**Vorgelagert — Sub-Area-Wahl prüfen:** `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); der Slice berührt Bibliothek (`simulation/`), Modul `testserver`, Spezifikation und Doku, eine eigene Sub-Area besteht nicht.

**Vorgelagert — offene Beobachtungen sichten:** [`BEO-TDS/folge-adr-zu-adr-0033-massenladen-verstoss-und-teilbestand`](../observations/BEO-TDS/folge-adr-zu-adr-0033-massenladen-verstoss-und-teilbestand/state.md) (Gegenstand), `BEO-TDS/aussagen-ueber-messung-hinaus` (ein Fall, Allaussage; verkörpert in `AGENTS.md` §3.8), `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` (Risiko 4), `BEO-TDS/massenladen-ignore-bit-laesst-zustand-stehen` (anderer Gegenstand, gleicher Pfad `BulkLoads`; nicht Teil dieses Slice).

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
