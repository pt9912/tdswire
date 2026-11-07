# Slice slice-schreibpfad-bulk-load: Massenladen: BULK_LOAD-Paket (0x07) in der Bibliothek, Weitergabe an den Integrator, data import und data transfer im Standardpfad

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-testserver-manuell

**Bezug:** [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-RB-03](../../../../spec/lastenheft.md#tds-rb-03--protokollstandard), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md), [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md)

**Berührte Spec-Stellen:** SPEC-001 (Pakettyp 0x07 als Anfrage, Zustand „Massenladen erwartet“, Zurücksetzen bei `RESETCONNECTION`) · Protokollteil Pakettypen und Dispatch (Katalog der Pakete) · SPEC-036 (`INSERT BULK`, `SET FMTONLY`, Grenzen, Setzungen) · SPEC-035 (Callbacks, Obergrenze des Bestands des Testservers, `SimColumn.maxLength`) · SPEC-059 (Vertrag und Callbacks wachsen nicht; `make flaeche` grün) · SPEC-040 (Tests) · [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend).a (Schritt 10, Massenladen)

**Verantwortlich:** Implementer-Agent, Auftrag des Nutzers 2026-11-07.

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-05.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert; jeder Ausschluss mit Begründung.

**Ziel:** Die Bibliothek decodiert das Massenladen (Paket 0x07), ordnet Spalten zu, wandelt Typen und gibt die Zeilen je Nachricht an `SimTable.onInsertBatch` weiter, ohne ihn an `onInsert` je Zeile (Weg A als Richtung, Z3 laut [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md)); der Testserver speichert sie in seinem Arbeitsspeicher, sodass `data import` und `data transfer` von d-migrate im Standardpfad (SQLServerBulkCopy) durchlaufen und ein anschließender Export die Zeilen enthält (Hürde 9 der Sonde).

**Quelle und Vorbehalt.** Auslöser ist die Sonde vom 2026-11-05 ([d-migrate-sonde-2026-11-05](../../../messungen/d-migrate-sonde-2026-11-05.md)). Ihr Vorbehalt gilt hier: alles, was dort „lief“, lief gegen erfundene Katalogantworten; belegt ist, was d-migrate sendet und dass Treiber und d-migrate die TDS-Antworten der Bibliothek verarbeiten. Aufwandsgrößen (S/M/L) sind Schätzungen der Sonde, nicht gemessen. Gemessen ist nur, was d-migrate vor dem Paket sendet (`INSERT BULK [dbo].[kunden] (...) with (...)`, `SET FMTONLY ON`-Proben, `select collation_name, is_computed from sys.columns ...`); das Paket 0x07 selbst und die Reaktion des Treibers auf unsere Antwort sind nicht gemessen. **Hülle:** Der Plan ist knapp; der Implementer erweitert §3 im ersten Lauf, nachdem die Entscheidung in [slice-adr-testserver-bestand-und-katalog](../done/slice-adr-testserver-bestand-und-katalog.md) vorliegt (Form der Weitergabe und Namen der Fläche hängen an ihr). **Aufteilung:** Die Funktion liegt in der Bibliothek (`src/main`, Packages `simulation`, `protocol`); der Testserver (`testserver`) ist Integrator: Bestand, Callbacks, Beispieltabellen. Jeder Liefer-Punkt unten nennt seinen Anteil.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Der Pfad `--on-conflict skip|update` mit `MERGE ... OUTPUT $action` — [slice-schreibpfad-merge-output](../open/slice-schreibpfad-merge-output.md).
- Katalog und DDL — [slice-katalog-und-ddl-zustand](../done/slice-katalog-und-ddl-zustand.md); das Massenladen liest die Spaltenbeschreibung (`sys.columns`) aus dem dort geschaffenen Katalog und setzt ihn voraus.
- Speichern in der Bibliothek, Persistenz über den Prozess hinaus und Transaktionssemantik: die Bibliothek speichert nichts ([TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend)), das Weitere ist Out-of-Scope von [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate).
- Alle Pakettypen und Optionen von [MS-TDS] jenseits von COLMETADATA und ROW für die Spaltentypen des Solls (Sonde: INT, NVARCHAR(4000), VARCHAR(10)); weitere Typen erst, wenn ein Lauf sie braucht.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit.

Liefer-Punkte (3):

- [ ] **Bibliothek, Protokoll:** Der Dispatch erkennt Paket 0x07, dekodiert COLMETADATA und ROW der Spaltentypen des Solls führt einen Sitzungszustand „Massenladen erwartet“ nach der Anweisung `INSERT BULK` (davor `SET FMTONLY` und die `sys.columns`-Abfrage laut Sonde), ordnet Spalten zu, wandelt Typen und gibt die Zeilen je Nachricht an `SimTable.onInsertBatch` weiter, ohne ihn an `onInsert` je Zeile (Z3 laut [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md); Gegendruck zwischen den Nachrichten, nicht innerhalb einer; Zeilenzahl am Ende; bei einem Fehler endet das Massenladen mit dem Fehler des Callbacks, vorher eingefügte Zeilen bleiben, keine Transaktionssemantik, die Zusage nennt es); Handvektoren mit Quelle ([MS-TDS] oder Mitschnitt von mssql-jdbc, `AGENTS.md` §3.8); Negativfälle (abgeschnittenes Paket, unbekannter Typ, Fehler des Integrators) enden mit definiertem Fehler, die Verbindung bleibt synchron ([TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität)). Der Vorlauf des Treibers (`SET FMTONLY ON`, Spaltenprobe) wird beantwortet. Die Felder der Antwort-Tokens stimmen mit der Messung am echten Server überein, gemessen mit `SqlBulkCopy` ([Messprotokoll M-8 bis M-12](../../../messungen/referenzabgleich.md); die Tests lesen die Antwort zu Token-Objekten zurück und vergleichen Felder, nicht Bytes; Servername, Prozedur und Zeilennummer des ERROR-Tokens und die Annahme der Antwort durch `SqlBulkCopy` am Testserver sind nicht belegt): `INSERT BULK` mit DONE ohne Zähler (Befehl `0x00fd`), die Nachricht mit DONE `COUNT` (Befehl `0x00f0`), ATTENTION nach `INSERT BULK` mit DONE `ERROR|ATTENTION`, Fehler des Callbacks als ERROR 2627 (Class 14) mit INFO 3621 und DONE `ERROR` (Befehl `0x00f0`); die Abweichung vom Wortlaut von [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 5 (iii) und Nr. 6 steht in §6. **Grenze:** gemessen ist die Form der Antworten an `SqlBulkCopy`; die **Nutzlast** von `SqlBulkCopy` liest der Leser heute nicht (der Referenzclient sendet nicht nullbare Spalten als feste Typen, etwa BIGINT `0x7f`; alle 10 aufgezeichneten Nachrichten 0x07 der Messreihe M-8 bis M-12 weist er ab), und sein Katalog-Batch ist dem Testserver unbekannt; belegt ist der Lader mit mssql-jdbc (d-migrate 1.12.0). Träger der Lücke: [slice-bulk-load-feste-typen-und-referenzclient](../open/slice-bulk-load-feste-typen-und-referenzclient.md).
- [ ] **Testserver:** Der Testserver implementiert die Massenlade-Weitergabe gegen seinen Bestand im Arbeitsspeicher (Schlüssel- und Typprüfung mit definiertem Fehler, [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate) Negative).
- [ ] **Beleg am fremden Client:** `data import` und `data transfer` von d-migrate 1.12.0 im Standardpfad (ohne `--on-conflict`) gegen den Testserver enden mit Exit 0; ein `data export` danach enthält die Zeilen; Ausgabe mit Rechner, Last und Image-Digest. Vier Messpunkte: (i) Zeilenzahl und Inhalt nach beiden Läufen stimmen; (ii) Fehler mitten im Strom: Fehlernummer, die d-migrate erhält (nur nennen, wenn gemessen); (iii) die Typen, die d-migrate sendet (Sonde: int, nvarchar, date; weitere nicht gemessen); (iv) beschränkter Speicher bei großem Import (Heap-Grenze, Gegendruck).

Nachweis-Pflichten für alle Punkte:

- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben oder „keine Beobachtung angefallen“ in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — Pfad-Kandidaten für §8; der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

Der Ist-Zustand wurde vor dem ersten Code gegen den Plan gemessen (Lesen von `SimModel.kt`, `RequestLoop.kt`, `SimulationBackend.kt`, `StatementParser.kt`, `HandshakeAutomaton.kt`, dem Bestand `TableStore.kt`): `SimTable.onInsertBatch` besteht schon (Fläche des Slice [slice-oeffentliche-flaeche-verkleinern-und-belegen](../done/slice-oeffentliche-flaeche-verkleinern-und-belegen.md)), es fehlt der Pakettyp im Automaten, der Leser der Nutzlast, der Sitzungszustand, die Formen B und die Probe der Kollation im Planer und jede Zeile im Bestand des Testservers. Ein neuer öffentlicher Name entsteht nicht: die Fähigkeit des Massenladens ist die interne Schnittstelle `BulkLoadingBackend` im Package `port`, die `runtime` dem `SimulationBackend` beim Verdrahten überstülpt (ein öffentlicher Typ darf keine interne Schnittstelle tragen).

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/port/BulkLoading.kt` | neu | interne Fähigkeit `BulkLoadingBackend`, `BulkTarget`, `BulkRows`; `QueryBackend` bleibt unverändert |
| `.../protocol/bulk/BulkLoadReader.kt`, `BulkValueReader.kt` | neu | Leser von COLMETADATA, ROW und DONE der Nachricht 0x07, Werte nach `MS-TDS`, Grenzen, Fehler als `ProtocolFailure` |
| `.../protocol/dispatch/BulkDispatch.kt`, `RequestLoop.kt` | neu / update (drei Stellen) | Zustand „Massenladen erwartet“ je Sitzung, `INSERT BULK` setzt das Ziel, Nachricht 0x07 lädt, RPC und andere Anfrage löschen es; minimale Eingriffe in `RequestLoop` wegen der parallelen Arbeit an den Antwort-Tokens |
| `.../protocol/response/ResponseForm.kt`, `ResponseRun.kt`, `.../protocol/token/Token.kt`, `.../protocol/error/ErrorCode.kt`, `ErrorMapper.kt`, `.../simulation/BulkLoads.kt`, `StatementPlanner.kt` | update (zweiter Durchgang) | Form BULK der Antwort (DONE `0x00f0`, INFO 3621), Code `TDS_SIM_KEY_VIOLATION` (2627, Class 14) für Fehler des Callbacks, gemessene Form nach M-8 bis M-12 |
| `.../protocol/session/AttentionAck.kt`, `HandshakeSession.kt`, `.../protocol/dispatch/BulkDispatch.kt`, `RequestLoop.kt` | update (zweiter Durchgang) | ATTENTION nach `INSERT BULK` (DONE `ERROR|ATTENTION`), `INSERT BULK` ohne Zähler, Zustand „erwartet“ |
| `.../protocol/session/HandshakeEvent.kt`, `HandshakeAutomaton.kt` | update | `BULK_LOAD` ist eine Anfrage (`RUNNING`), nicht mehr `OTHER_MESSAGE` |
| `.../runtime/SimulationBulk.kt`, `ConnectionWiring.kt` | neu / update | die Fähigkeit am `SimulationBackend` bereitstellen |
| `.../simulation/BulkForms.kt`, `Statement.kt`, `StatementParser.kt`, `StatementPlanner.kt` | neu / update | Form B: `INSERT BULK`, Probe mit `SET FMTONLY`, Planung gegen den Katalog |
| `.../simulation/CatalogForms.kt`, `CatalogAnswers.kt` | update | Probe der Kollation mit Literal in `OBJECT_ID('…')` |
| `.../simulation/BulkLoads.kt`, `SimTemporal.kt`, `SimValues.kt`, `SimulationBackend.kt` | neu / update | Zuordnung und Überführung der Werte der Nachricht, Weitergabe an `onInsertBatch` oder `onInsert`, Zeitpunkt auch mit Leerzeichen |
| `testserver/kotlin/.../TableStore.kt` | update | Integrator-Anteil: Zeilen im Arbeitsspeicher, `onInsertBatch` ganz oder gar nicht, Schlüssel-, Typ- und NULL-Prüfung |
| `src/integrator/kotlin/.../MassenladenEmbeddingTest.kt`, `WireClient.kt`, `FlaecheEmbeddingTest.kt` | neu / update | Einbettungstest über die öffentliche Fläche (Nachricht 0x07 lädt über `onInsertBatch`, ohne Jackson), Rohbytes-Client sendet das Paket 0x07, Fläche unverändert belegt |
| `src/main/kotlin/.../simulation/SimModel.kt`, `docs/plan/kompatibilitaetsmatrix.md` | update | KDoc von `SimTable.onInsertBatch` und Zeile `BULK_LOAD` der Matrix (Fortschreibung der zweiten Fundstelle, Review F-3) |
| `docs/plan/planning/observations/BEO-TDS/testserver-bestand-nicht-am-heap-begrenzt/` | neu | Register-Eintrag zur Grenze des Bestands (Review F-11) |
| Verweis-Anpassungen in `done/`-, `open/`-Slices, Welle, Roadmap und Register (nur Pfade der Lifecycle-Verschiebung) | update | Folge der Verschiebung des Slice nach `in-progress/` |
| `spec/spezifikation.md`, `docs/user/testserver-manuell.md`, `docs/messungen/bulk-load-2026-11-07.md` | update / neu | Pakettyp und Zustand (`SPEC-001`), Form B (`SPEC-036`), Weitergabe und Überführung (`SPEC-035`), Läufe (`SPEC-040`), Nutzer-Doku, Messprotokoll |
| Tests: `BulkLoadReaderTest`, `BulkLoadFuzzTest`, `BulkDispatchTest`, `SimulationBackendBulkTest`, `TableStoreTest`, `SimValuesTest` | neu / update | Happy/Boundary/Negative nach [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate); Fuzz der Nutzlast |

**Nachgeführt bei der Closure (Verifikation V-8; Plan-Tabelle gegen den Diff gelesen).** Im Diff, nicht in der Tabelle oben:

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/test/kotlin/.../protocol/error/ErrorMapperTest.kt`, `.../transport/framing/MessageAssemblerTest.kt` | update | zweiter Durchgang: Form des Massenladens (2627, INFO 3621, DONE `0x00f0`), Statusbit `0x04` im ersten Paket |
| `docs/plan/spec-abdeckung.md` | update | Abdeckungsmatrix, in der Closure frisch erzeugt (Verifikation V-2) |
| `docs/messungen/d-migrate-sonde-2026-11-05.md`, `d-migrate-verbindung-und-export-2026-11-05.md`, `referenzabgleich.md` | update | nur Pfade der Lifecycle-Verschiebung nach `in-progress/` |
| `docs/reviews/2026-11-07-5c3843b9-review-slice-schreibpfad-bulk-load.md`, `2026-11-07-631d273d-verifikation-slice-schreibpfad-bulk-load.md` | neu | Review und Verifikation |
| `docs/plan/planning/open/slice-bulk-load-feste-typen-und-referenzclient.md`, Welle, Roadmap, Register (`BEO-TDS/wanduhr-tests-flackern-unter-last`, `BEO-TDS/massenladen-ignore-bit-laesst-zustand-stehen`, Stände und Belege) | neu / update | Folgen der Verifikation (Closure) |
| `src/main/kotlin/.../protocol/bulk/BulkLoadReader.kt` (KDoc), `testserver/kotlin/.../TableStore.kt` (KDoc), `spec/spezifikation.md`, `docs/plan/kompatibilitaetsmatrix.md`, `docs/user/testserver-manuell.md` | update | Grenze „Nutzlast des Referenzclients“ und Nachzug auf 2627 (V-1, V-3); nur Kommentar im Code |

**Anschluss an die Entscheidung** ([ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), `Accepted` am 2026-11-06, Messungen: [Messprotokoll des Spikes](../../../messungen/callback-loesung-spike-2026-11-05.md); Liste in §3 von [slice-adr-testserver-bestand-und-katalog](../done/slice-adr-testserver-bestand-und-katalog.md)). Spec-Änderungen dieses Slice: Protokollteil der Pakettypen (0x07 im Katalog; `INSERT BULK` merkt das Ziel, die Nachricht gilt als Ganzes bis zur Grenze von `SPEC-007`; IGNORE-Bit verwirft sie; ein leerer Schlussteil mit COLMETADATA und ohne Zeile folgt jedem Import), `SPEC-036` (`INSERT BULK … with (…)`, `SET FMTONLY ON … OFF` auch als `sp_executesql N'…'` im SQL-Text, Probe der Sortierung aus `sys.columns`), `SPEC-035` (Überführung des Zeitpunkts `yyyy-MM-dd HH:mm:ss[.f]`, Weitergabe an den Stapel-Callback `onInsertBatch`, ohne ihn `onInsert` je Zeile, „keine Transaktion“), `SPEC-059` (Parameter `SimTable.onInsertBatch`), `SPEC-040` (`data import`, `data transfer`). Entschieden (ADR `Accepted`, 2026-11-06): Weitergabe Z3 mit Stapel-Callback `onInsertBatch` je Nachricht (ein Massenladen ist eine Anweisung), Gegendruck bleibt zwischen den Nachrichten, die Zusage nennt die Grenze von 16 MiB und den Heap-Bedarf; Beispieltabellen mit BIGINT-Schlüssel. Start-Trigger unverändert (§4 führt die Bedingung, die Reihenfolge der Entscheidung steht im Slice zur ADR); der Import fragt `sys.identity_columns`, `sys.computed_columns` und `SELECT SCHEMA_NAME()`). Aus dem Spike: INT-Spalten und ganzzahlig geschriebene DECIMAL-Werte scheitern im Treiber (b3, Messpunkt (iii) und Beleg „Exit 0“ nur für andere Spalten); eine Nachricht über 16 MiB schließt die Verbindung, 15,9 MB brauchen mehr als 32 MiB Heap (b11, b12); Fehler in der Mitte hinterlassen die Zeilen davor (b5, Entscheid der ADR vom 2026-11-06); die Variante Z1 (Weg über `QueryBackend.execute`, nicht gewählt) brauchte 2,4-mal so lang wie der direkte Weg (zwei Einzelmessungen, b14).

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-adr-testserver-bestand-und-katalog](../done/slice-adr-testserver-bestand-und-katalog.md) liegt in `done/` (Weitergabe, Katalog nach Änderungen und Fläche entschieden) und [slice-katalog-und-ddl-zustand](../done/slice-katalog-und-ddl-zustand.md) liegt in `done/` (Katalog und veränderlicher Bestand des Testservers bestehen); damit stehen [slice-testserver-gradle-modul](../done/slice-testserver-gradle-modul.md) und [slice-oeffentliche-flaeche-verkleinern-und-belegen](../done/slice-oeffentliche-flaeche-verkleinern-und-belegen.md) vor diesem Slice (Kette über den Katalog-Slice, Reihenfolge vom Nutzer am 2026-11-06 entschieden). Dieser Slice fügt `SimTable.onInsertBatch` hinzu ([ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md)).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte; Schnittvorschlag: Anteil der Bibliothek und Anteil des Testservers als zwei Slices.
- `in-progress` → `open` (blockiert): die Entscheidung in [slice-adr-testserver-bestand-und-katalog](../done/slice-adr-testserver-bestand-und-katalog.md) trägt den Fall nicht oder wird überstimmt.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln.

DoD vollständig, `make gates` grün, die Ausgabe der d-migrate-Läufe liegt vor, Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — jedes Risiko bekommt genau einen Ausgang.

- Aufwand der Sonde: L (neuer Pakettyp, Zeilendekodierung); die Dekodierung ist ein Eingabepfad mit fremden Längen und gehört in den Fuzz-Umfang von [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität). — **Ausgang:** eingetreten: Aufwand L mit zwei Durchgängen und Behebungen; `BulkLoadFuzzTest` liegt im Umfang (im Review in keinem Mutationslauf rot, F-10, danach behoben; 6 von 6 Läufen der Verifikation grün).
- Offen, nicht gemessen: ob d-migrate eine Option zum Abschalten des Massenladens hat; sie wird nicht als Ausweg geplant (das Massenladen ist Soll von [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate)). — **Ausgang:** weiter offen, nicht gemessen und ohne Folge für den Soll (das Massenladen läuft im Standardpfad, Läufe Z1 bis Z4 der Verifikation); Träger Register [`BEO-TDS/parametertyp-des-clients-passt-nicht-zur-spalte`](../observations/BEO-TDS/parametertyp-des-clients-passt-nicht-zur-spalte/state.md) (Verhalten des Clients, das der Server nicht wählt).
- Der Speicherbedarf bei großem Import: gemessen sind die Heap-Grenzen des Messprotokolls (H1 bis H4) und, aus dem Review, dass eine gültige Nachricht von 15,7 MiB mit 1024 NULL-Spalten bei 128 MiB Heap in der Überführung endet; die Grenze von 262 144 Zellen je Nachricht und von 2 097 152 Zellen im Bestand des Testservers sind Setzungen, die Bytes je Zelle und der Heap, bei dem sie tragen, sind nicht gemessen (Register: `BEO-TDS/testserver-bestand-nicht-am-heap-begrenzt`). Gegendruck wirkt laut [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) zwischen den Nachrichten, nicht innerhalb einer. — **Ausgang:** weiter offen, Träger Register [`BEO-TDS/testserver-bestand-nicht-am-heap-begrenzt`](../observations/BEO-TDS/testserver-bestand-nicht-am-heap-begrenzt/state.md); die Grenze von 64 MiB bei 500 000 Zeilen hat die Verifikation reproduziert (OOM bei `-Xmx64m`, Erfolg bei `-Xmx256m`), die Zeilen "Heap je Zelle" des Messprotokolls stammen aus einem Wegwerf-Test außerhalb des Repos (Größenordnung).
- Vergleichswert eines echten SQL Servers: die Antworten auf das Massenladen sind gemessen ([Messprotokoll M-8 bis M-12](../../../messungen/referenzabgleich.md), Träger [slice-qa-vergleichslauf-d-messungen-bulk-merge](../done/slice-qa-vergleichslauf-d-messungen-bulk-merge.md), Register `BEO-TDS/messung-am-echten-sql-server-fehlt`) und im zweiten Durchgang umgesetzt: `INSERT BULK` DONE `0x00fd` ohne Zähler, Nachricht 0x07 DONE `COUNT` `0x00f0`, ATTENTION nach `INSERT BULK` (M-11) mit `ERROR|ATTENTION`, Fehler des Callbacks als 2627 (Class 14, State 1) mit INFO 3621 und DONE `ERROR` `0x00f0` (M-12), Statusbit `0x04` im ersten Paket angenommen (M-9). Gemessen mit `Microsoft.Data.SqlClient` (`SqlBulkCopy`), nicht mit mssql-jdbc von d-migrate: der Referenzclient schickt je Batch ein eigenes Paar, keine `SET FMTONLY`-Proben und keinen Nachlauf mit COLMETADATA ohne Zeile; diese Verhaltensweisen von d-migrate (Ziel bleibt nach der Nachricht stehen, Probe-Antworten) bleiben Setzung. Setzungen, die M-8 bis M-12 weder stützen noch widerlegen: Kollation `SQL_Latin1_General_CP1_CI_AS`, Windows-1252, Verengung Int64 zu Int32, die Grenzen 1024 Spalten und 262 144 Zellen, DONE-Zähler 0 bei IGNORE, der gekürzte Text von 2627, jeder Fehler des Callbacks als 2627. — **Ausgang:** umgesetzt im zweiten Durchgang; die Setzungen bleiben weiter offen (eingetreten für die Messung; Träger der Setzungen: Register [`BEO-TDS/messung-am-echten-sql-server-fehlt`](../observations/BEO-TDS/messung-am-echten-sql-server-fehlt/state.md)), und die Nutzlast des Referenzclients liest der Testserver nicht ([slice-bulk-load-feste-typen-und-referenzclient](../open/slice-bulk-load-feste-typen-und-referenzclient.md)).
- Abweichung vom Wortlaut der angenommenen Entscheidung: [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 5 (iii) („Fehler eines Callbacks beenden das Massenladen mit dem Fehler des Callbacks“, `TDS_BACKEND_ERROR`) und Nr. 6 („Teilbestand beim Integrator“) stehen unverändert; der Code folgt der Messung (2627, Class 14; der Testserver nimmt keine Zeile der Nachricht auf, wie sein Bestand es schon tat). Gemessen ist ein Fall (Schlüsselverstoß, drei Zeilen, ein Paket); ob der echte Server bei anderen Verstößen oder mehreren Paketen ebenso keine Zeile aufnimmt, ist nicht gemessen. — **Ausgang:** offen, Träger [slice-adr-massenladen-atomar-an-messung](../open/slice-adr-massenladen-atomar-an-messung.md) (Folge-ADR zu [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), Architect, Proposed) und Register [`BEO-TDS/folge-adr-zu-adr-0033-massenladen-verstoss-und-teilbestand`](../observations/BEO-TDS/folge-adr-zu-adr-0033-massenladen-verstoss-und-teilbestand/state.md). Der **Sammelcode** 2627 für jede Ausnahme des Callbacks (Typfehler, NULL, volle Zellgrenze erscheinen als „Violation of PRIMARY KEY“; Verifikation V-4) ist in den Umfang dieses Folge-Slice aufgenommen (Zuordnung der Codes je Fehlerursache) und im Register genannt; der Träger überlebt die Closure.
- Der Vertrag oder die Callbacks wachsen; Reihenfolge zu [slice-oeffentliche-flaeche-verkleinern-und-belegen](../done/slice-oeffentliche-flaeche-verkleinern-und-belegen.md) und [slice-e2e-backend-ausserhalb-modul](../done/slice-e2e-backend-ausserhalb-modul.md) siehe [welle-testserver-manuell](../welle-testserver-manuell.md) §5. — **Ausgang:** entfallen für Typen und Parameter (`make flaeche` grün, kein neuer öffentlicher Typ; `SimulationBackend` nimmt `catalog` als `private val`, ohne Änderung der Signatur). Für **Werte** eingetreten als Information (Verifikation V-7): `TDS_SIM_KEY_VIOLATION` ist ein zulässiger Code von `Failure`, ein Integrator mit eigenem `QueryBackend` kann ihn liefern und bekommt 2627 mit festem Text; `ErrorCode` ist `internal`, das Vokabular wächst um dieses Wort, und `make flaeche` vergleicht nur Typnamen und sieht es nicht.
- Der Client wählt den Typ seiner Parameter selbst: d-migrate 1.12.0 sendet Ganzzahlwerte als `bigint`, die strenge Überführung ([SPEC-035](../../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends)) weitet nur `Int32` auf `Int64`; ein Filter `Id = 7` gegen eine `Int32`-Spalte endete im Export mit „Invalid value“ (Beobachtung [parametertyp-des-clients-passt-nicht-zur-spalte](../observations/BEO-TDS/parametertyp-des-clients-passt-nicht-zur-spalte/observation.md), Ursache aus dem Code gelesen, nicht gemessen). Der Slice prüft die Parameter seines Pfades gegen die Spaltentypen. — **Ausgang:** weiter offen: der Pfad des Massenladens trägt keine Parameter des Clients (die Werte stehen in der Nachricht 0x07; die Überführung weitet nur nach `SimValues`, Verengung Int64 zu Int32 ist Setzung); der Filter `Id = 7` des Exports ist seit den BIGINT-Schlüsseln nicht erneut gemessen; Träger Register [`BEO-TDS/parametertyp-des-clients-passt-nicht-zur-spalte`](../observations/BEO-TDS/parametertyp-des-clients-passt-nicht-zur-spalte/state.md).

- Eine Nachricht mit IGNORE-Bit lässt den Zustand „wartend“ stehen (`BulkDispatch.answer`, Verifikation V-6): ein folgendes ATTENTION in `READY` wird mit `ERROR|ATTENTION` statt der gewöhnlichen Bestätigung beantwortet; von Hand gelesen, nicht gefahren, Randfall mit Antwort auf IGNORE als Setzung. — **Ausgang:** weiter offen, Träger Register [`BEO-TDS/massenladen-ignore-bit-laesst-zustand-stehen`](../observations/BEO-TDS/massenladen-ignore-bit-laesst-zustand-stehen/state.md) und [slice-bulk-load-feste-typen-und-referenzclient](../open/slice-bulk-load-feste-typen-und-referenzclient.md); die Closure behebt den Code nicht (Planner-Rolle), Auftrag an einen Implementer.
- Lastabhängige Tests außerhalb des Diffs (`TelemetryJsonTest`, `ListenerAbortTest`; Verifikation V-9) färbten Läufe rot und eine eingecheckte Matrix. — **Ausgang:** eingetreten als Beobachtung, kein Slice: Register [`BEO-TDS/wanduhr-tests-flackern-unter-last`](../observations/BEO-TDS/wanduhr-tests-flackern-unter-last/state.md); die Matrix ist in der Closure aus einem Lauf ohne roten Test erzeugt.

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register; `grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln.

- **Was hat funktioniert:** —
- **Was ging anders als geplant:** —
- **Steering-Loop-Eintrag:** —
- **Beobachtungs-Register (`../observations/`):** —
- **Folge-Slices:** —
- **Risiken aus §6:** —

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung.

**Vorgelagert — Sub-Area-Wahl prüfen:** Eine Sub-Area (`*`, Kürzel `TDS`); keine Aufteilung nötig.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (Namensliste der Eröffnung, Texte nicht einzeln gelesen); kein Treffer ausgemacht. Der Implementer sichtet das Register bei Start erneut.

Alle berührten Sub-Areas GF.
