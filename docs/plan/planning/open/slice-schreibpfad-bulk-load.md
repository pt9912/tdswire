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

**Berührte Spec-Stellen:** Protokollteil Pakettypen und Dispatch (Katalog der Pakete) · SPEC-036 (`INSERT BULK`, `SET FMTONLY`) · SPEC-035 (Callbacks) · SPEC-059 (falls der Vertrag oder die Callbacks wachsen) · SPEC-040

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-05.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert; jeder Ausschluss mit Begründung.

**Ziel:** Die Bibliothek decodiert das Massenladen (Paket 0x07), ordnet Spalten zu, wandelt Typen und gibt die Zeilen je Nachricht an `SimTable.onInsertBatch` weiter, ohne ihn an `onInsert` je Zeile (Weg A als Richtung, Z3 laut [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md)); der Testserver speichert sie in seinem Arbeitsspeicher, sodass `data import` und `data transfer` von d-migrate im Standardpfad (SQLServerBulkCopy) durchlaufen und ein anschließender Export die Zeilen enthält (Hürde 9 der Sonde).

**Quelle und Vorbehalt.** Auslöser ist die Sonde vom 2026-11-05 ([d-migrate-sonde-2026-11-05](../../../messungen/d-migrate-sonde-2026-11-05.md)). Ihr Vorbehalt gilt hier: alles, was dort „lief“, lief gegen erfundene Katalogantworten; belegt ist, was d-migrate sendet und dass Treiber und d-migrate die TDS-Antworten der Bibliothek verarbeiten. Aufwandsgrößen (S/M/L) sind Schätzungen der Sonde, nicht gemessen. Gemessen ist nur, was d-migrate vor dem Paket sendet (`INSERT BULK [dbo].[kunden] (...) with (...)`, `SET FMTONLY ON`-Proben, `select collation_name, is_computed from sys.columns ...`); das Paket 0x07 selbst und die Reaktion des Treibers auf unsere Antwort sind nicht gemessen. **Hülle:** Der Plan ist knapp; der Implementer erweitert §3 im ersten Lauf, nachdem die Entscheidung in [slice-adr-testserver-bestand-und-katalog](../done/slice-adr-testserver-bestand-und-katalog.md) vorliegt (Form der Weitergabe und Namen der Fläche hängen an ihr). **Aufteilung:** Die Funktion liegt in der Bibliothek (`src/main`, Packages `simulation`, `protocol`); der Testserver (`src/testserver`) ist Integrator: Bestand, Callbacks, Beispieltabellen. Jeder Liefer-Punkt unten nennt seinen Anteil.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Der Pfad `--on-conflict skip|update` mit `MERGE ... OUTPUT $action` — [slice-schreibpfad-merge-output](slice-schreibpfad-merge-output.md).
- Katalog und DDL — [slice-katalog-und-ddl-zustand](slice-katalog-und-ddl-zustand.md); das Massenladen liest die Spaltenbeschreibung (`sys.columns`) aus dem dort geschaffenen Katalog und setzt ihn voraus.
- Speichern in der Bibliothek, Persistenz über den Prozess hinaus und Transaktionssemantik: die Bibliothek speichert nichts ([TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend)), das Weitere ist Out-of-Scope von [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate).
- Alle Pakettypen und Optionen von [MS-TDS] jenseits von COLMETADATA und ROW für die Spaltentypen des Solls (Sonde: INT, NVARCHAR(4000), VARCHAR(10)); weitere Typen erst, wenn ein Lauf sie braucht.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit.

Liefer-Punkte (3):

- [ ] **Bibliothek, Protokoll:** Der Dispatch erkennt Paket 0x07, dekodiert COLMETADATA und ROW der Spaltentypen des Solls führt einen Sitzungszustand „Massenladen erwartet“ nach der Anweisung `INSERT BULK` (davor `SET FMTONLY` und die `sys.columns`-Abfrage laut Sonde), ordnet Spalten zu, wandelt Typen und gibt die Zeilen je Nachricht an `SimTable.onInsertBatch` weiter, ohne ihn an `onInsert` je Zeile (Z3 laut [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md); Gegendruck zwischen den Nachrichten, nicht innerhalb einer; Zeilenzahl am Ende; bei einem Fehler endet das Massenladen mit dem Fehler des Callbacks, vorher eingefügte Zeilen bleiben, keine Transaktionssemantik, die Zusage nennt es); Handvektoren mit Quelle ([MS-TDS] oder Mitschnitt von mssql-jdbc, `AGENTS.md` §3.8); Negativfälle (abgeschnittenes Paket, unbekannter Typ, Fehler des Integrators) enden mit definiertem Fehler, die Verbindung bleibt synchron ([TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität)). Der Vorlauf des Treibers (`SET FMTONLY ON`, Spaltenprobe) wird beantwortet.
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

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/` und `.../protocol/token/` | update / neu | Paket 0x07 und Dekodierung; genaue Dateien erweitert der Implementer |
| Weitergabe: Stapel-Callback `SimTable.onInsertBatch` (neuer öffentlicher Name) und Rückfall `onInsert` in `src/main/kotlin/io/github/pt9912/tdswire/simulation/SimModel.kt`; `QueryBackend` unverändert | update | Z3 laut [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) |
| `src/testserver/kotlin/` (Bestand, Callback) | update | Integrator-Anteil: Speichern der Zeilen |
| `spec/spezifikation.md` | update | Pakettyp im Katalog, Weitergabe, Fehlerfälle |
| Tests, Fuzz-Eingaben für Paket 0x07 | neu | Happy/Boundary/Negative nach [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate); Fuzz nach den Mustern der Protokolltests |

**Anschluss an die Entscheidung** ([ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), `Accepted` am 2026-11-06, Messungen: [Messprotokoll des Spikes](../../../messungen/callback-loesung-spike-2026-11-05.md); Liste in §3 von [slice-adr-testserver-bestand-und-katalog](../done/slice-adr-testserver-bestand-und-katalog.md)). Spec-Änderungen dieses Slice: Protokollteil der Pakettypen (0x07 im Katalog; `INSERT BULK` merkt das Ziel, die Nachricht gilt als Ganzes bis zur Grenze von `SPEC-007`; IGNORE-Bit verwirft sie; ein leerer Schlussteil mit COLMETADATA und ohne Zeile folgt jedem Import), `SPEC-036` (`INSERT BULK … with (…)`, `SET FMTONLY ON … OFF` auch als `sp_executesql N'…'` im SQL-Text, Probe der Sortierung aus `sys.columns`), `SPEC-035` (Überführung des Zeitpunkts `yyyy-MM-dd HH:mm:ss[.f]`, Weitergabe an den Stapel-Callback `onInsertBatch`, ohne ihn `onInsert` je Zeile, „keine Transaktion“), `SPEC-059` (Parameter `SimTable.onInsertBatch`), `SPEC-040` (`data import`, `data transfer`). Entschieden (ADR `Accepted`, 2026-11-06): Weitergabe Z3 mit Stapel-Callback `onInsertBatch` je Nachricht (ein Massenladen ist eine Anweisung), Gegendruck bleibt zwischen den Nachrichten, die Zusage nennt die Grenze von 16 MiB und den Heap-Bedarf; Beispieltabellen mit BIGINT-Schlüssel. Start-Trigger unverändert (§4 führt die Bedingung, die Reihenfolge der Entscheidung steht im Slice zur ADR); der Import fragt `sys.identity_columns`, `sys.computed_columns` und `SELECT SCHEMA_NAME()`). Aus dem Spike: INT-Spalten und ganzzahlig geschriebene DECIMAL-Werte scheitern im Treiber (b3, Messpunkt (iii) und Beleg „Exit 0“ nur für andere Spalten); eine Nachricht über 16 MiB schließt die Verbindung, 15,9 MB brauchen mehr als 32 MiB Heap (b11, b12); Fehler in der Mitte hinterlassen die Zeilen davor (b5, Entscheid der ADR vom 2026-11-06); die Variante Z1 (Weg über `QueryBackend.execute`, nicht gewählt) brauchte 2,4-mal so lang wie der direkte Weg (zwei Einzelmessungen, b14).

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-adr-testserver-bestand-und-katalog](../done/slice-adr-testserver-bestand-und-katalog.md) liegt in `done/` (Weitergabe, Katalog nach Änderungen und Fläche entschieden) und [slice-katalog-und-ddl-zustand](slice-katalog-und-ddl-zustand.md) liegt in `done/` (Katalog und veränderlicher Bestand des Testservers bestehen); damit stehen [slice-testserver-gradle-modul](../next/slice-testserver-gradle-modul.md) und [slice-oeffentliche-flaeche-verkleinern-und-belegen](slice-oeffentliche-flaeche-verkleinern-und-belegen.md) vor diesem Slice (Kette über den Katalog-Slice, Reihenfolge vom Nutzer am 2026-11-06 entschieden). Dieser Slice fügt `SimTable.onInsertBatch` hinzu ([ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md)).

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

- Aufwand der Sonde: L (neuer Pakettyp, Zeilendekodierung); die Dekodierung ist ein Eingabepfad mit fremden Längen und gehört in den Fuzz-Umfang von [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität). — **Ausgang:** offen bis zur Closure.
- Offen, nicht gemessen: ob d-migrate eine Option zum Abschalten des Massenladens hat; sie wird nicht als Ausweg geplant (das Massenladen ist Soll von [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate)). — **Ausgang:** offen bis zur Closure.
- Der Speicherbedarf bei großem Import ist nicht gemessen (Messpunkt iv); Gegendruck wirkt laut [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) zwischen den Nachrichten, nicht innerhalb einer. — **Ausgang:** offen bis zur Closure.
- Vergleichswert eines echten SQL Servers fehlt (`BEO-TDS/messung-am-echten-sql-server-fehlt`): die Antwort auf das Massenladen ist eine Setzung, bis der Referenzabgleich sie misst. — **Ausgang:** offen bis zur Closure.
- Der Vertrag oder die Callbacks wachsen; Reihenfolge zu [slice-oeffentliche-flaeche-verkleinern-und-belegen](slice-oeffentliche-flaeche-verkleinern-und-belegen.md) und [slice-e2e-backend-ausserhalb-modul](slice-e2e-backend-ausserhalb-modul.md) siehe [welle-testserver-manuell](../welle-testserver-manuell.md) §5. — **Ausgang:** offen bis zur Closure.
- Der Client wählt den Typ seiner Parameter selbst: d-migrate 1.12.0 sendet Ganzzahlwerte als `bigint`, die strenge Überführung ([SPEC-035](../../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends)) weitet nur `Int32` auf `Int64`; ein Filter `Id = 7` gegen eine `Int32`-Spalte endete im Export mit „Invalid value“ (Beobachtung [parametertyp-des-clients-passt-nicht-zur-spalte](../observations/BEO-TDS/parametertyp-des-clients-passt-nicht-zur-spalte/observation.md), Ursache aus dem Code gelesen, nicht gemessen). Der Slice prüft die Parameter seines Pfades gegen die Spaltentypen. — **Ausgang:** offen bis zur Closure.

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
