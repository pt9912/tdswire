# Slice slice-schreibpfad-bulk-load: Schreibpfad Massenladen: BULK_LOAD-Paket (0x07) für data import und data transfer im Standardpfad

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-testserver-manuell

**Bezug:** [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-RB-03](../../../../spec/lastenheft.md#tds-rb-03--protokollstandard), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md), [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md)

**Berührte Spec-Stellen:** Protokollteil Pakettypen und Dispatch (Katalog der Pakete) · SPEC-036 (Einfügeform W1, `INSERT BULK`, `SET FMTONLY`) · SPEC-035 · SPEC-059 (falls der Backend-Vertrag wächst) · SPEC-040

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-05.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert; jeder Ausschluss mit Begründung.

**Ziel:** `data import` und `data transfer` von d-migrate laufen im Standardpfad (SQLServerBulkCopy: `INSERT BULK`, danach Paket BULK_LOAD mit COLMETADATA und ROW) durch, die Zeilen stehen im Bestand, und ein anschließender Export enthält sie (Hürde 9 der Sonde).

**Quelle und Vorbehalt.** Auslöser ist die Sonde vom 2026-11-05 ([d-migrate-sonde-2026-11-05](../../../messungen/d-migrate-sonde-2026-11-05.md)). Ihr Vorbehalt gilt hier: alles, was dort „lief“, lief gegen erfundene Katalogantworten; belegt ist, was d-migrate sendet und dass Treiber und d-migrate die TDS-Antworten der Bibliothek verarbeiten. Aufwandsgrößen (S/M/L) sind Schätzungen der Sonde, nicht gemessen. Gemessen ist nur, was d-migrate vor dem Paket sendet (`INSERT BULK [dbo].[kunden] (...) with (...)`, `SET FMTONLY ON`-Proben, `select collation_name, is_computed from sys.columns ...`); das Paket 0x07 selbst und die Reaktion des Treibers auf unsere Antwort sind nicht gemessen. **Hülle:** Der Plan ist knapp; der Implementer erweitert §3 im ersten Lauf, nachdem die Entscheidung in [slice-adr-testserver-bestand-und-katalog](slice-adr-testserver-bestand-und-katalog.md) vorliegt (Ort und Schnitt hängen an ihr).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Der Pfad `--on-conflict skip|update` mit `MERGE ... OUTPUT $action` — [slice-schreibpfad-merge-output](slice-schreibpfad-merge-output.md).
- Katalog und DDL — [slice-katalog-und-ddl-zustand](slice-katalog-und-ddl-zustand.md); das Massenladen liest die Spaltenbeschreibung (`sys.columns`) aus dem dort geschaffenen Katalog und setzt ihn voraus.
- Persistenz über den Prozess hinaus und Transaktionssemantik: Out-of-Scope von [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate).
- Alle Pakettypen und Optionen von [MS-TDS] jenseits von COLMETADATA und ROW für die Spaltentypen des Solls (Sonde: INT, NVARCHAR(4000), VARCHAR(10)); weitere Typen erst, wenn ein Lauf sie braucht.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit.

Liefer-Punkte (3):

- [ ] **Protokoll:** Der Dispatch erkennt Paket 0x07, dekodiert COLMETADATA und ROW der Spaltentypen des Solls und leitet die Zeilen an das Backend (Form laut Entscheidung); Handvektoren mit Quelle ([MS-TDS] oder Mitschnitt von mssql-jdbc, `AGENTS.md` §3.8), Negativfälle (abgeschnittenes Paket, unbekannter Typ) enden mit definiertem Fehler und die Verbindung bleibt synchron ([TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität)).
- [ ] **Vorlauf des Treibers:** `SET FMTONLY ON` und die Spaltenprobe vor dem Paket werden beantwortet.
- [ ] **Beleg am fremden Client:** `data import` und `data transfer` von d-migrate 1.12.0 im Standardpfad (ohne `--on-conflict`) gegen den Testserver enden mit Exit 0; ein `data export` danach enthält die Zeilen; Ausgabe mit Rechner, Last und Image-Digest.

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
| Backend-Vertrag (`src/main/kotlin/io/github/pt9912/tdswire/port/QueryBackend.kt`) oder Einfügeform | update | laut Entscheidung; berührt die öffentliche Fläche |
| `spec/spezifikation.md` | update | Pakettyp im Katalog, W1/Massenladen, Fehlerfälle |
| Tests, Fuzz-Eingaben für Paket 0x07 | neu | Happy/Boundary/Negative nach [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate); Fuzz nach den Mustern der Protokolltests |

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-adr-testserver-bestand-und-katalog](slice-adr-testserver-bestand-und-katalog.md) liegt in `done/` (Ort des Katalogs und des Bestands entschieden) und [slice-katalog-und-ddl-zustand](slice-katalog-und-ddl-zustand.md) liegt in `done/` (der Katalog und der Bestand, auf denen das Massenladen schreibt, bestehen).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte.
- `in-progress` → `open` (blockiert): die Entscheidung in [slice-adr-testserver-bestand-und-katalog](slice-adr-testserver-bestand-und-katalog.md) trägt den Fall nicht oder wird überstimmt.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln.

DoD vollständig, `make gates` grün, die Ausgabe der d-migrate-Läufe liegt vor, Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — jedes Risiko bekommt genau einen Ausgang.

- Aufwand der Sonde: L (neuer Pakettyp, Zeilendekodierung); die Dekodierung ist ein Eingabepfad mit fremden Längen und gehört in den Fuzz-Umfang von [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität). — **Ausgang:** offen bis zur Closure.
- Vergleichswert eines echten SQL Servers fehlt (`BEO-TDS/messung-am-echten-sql-server-fehlt`): die Antwort auf das Massenladen ist eine Setzung, bis der Referenzabgleich sie misst. — **Ausgang:** offen bis zur Closure.
- Der Backend-Vertrag wächst; Reihenfolge zu [slice-oeffentliche-flaeche-verkleinern-und-belegen](slice-oeffentliche-flaeche-verkleinern-und-belegen.md) und [slice-e2e-backend-ausserhalb-modul](slice-e2e-backend-ausserhalb-modul.md) siehe [welle-testserver-manuell](../welle-testserver-manuell.md) §5. — **Ausgang:** offen bis zur Closure.

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
