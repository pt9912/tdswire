# Slice slice-schreibpfad-merge-output: Schreibpfad MERGE ... OUTPUT $action für data import und data transfer mit --on-conflict skip und update

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-testserver-manuell

**Bezug:** [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md), [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md)

**Berührte Spec-Stellen:** SPEC-036 (neue Form für `MERGE` mit VALUES-Zeile, Alias und `OUTPUT $action`; bisher „OUTPUT-Klauseln nicht erkannt“) · SPEC-030 (Parametertypen) · SPEC-035 · SPEC-040

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-05.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert; jeder Ausschluss mit Begründung.

**Ziel:** `data import` und `data transfer` von d-migrate mit `--on-conflict skip` und `--on-conflict update` laufen durch: das `MERGE` mit Parametern wird gegen den Bestand ausgeführt, die Ergebniszeile `$action` stimmt (INSERT, UPDATE) und ein anschließender Export enthält die Zeilen (Hürde 10 der Sonde).

**Quelle und Vorbehalt.** Auslöser ist die Sonde vom 2026-11-05 ([d-migrate-sonde-2026-11-05](../../../messungen/d-migrate-sonde-2026-11-05.md)). Ihr Vorbehalt gilt hier: alles, was dort „lief“, lief gegen erfundene Katalogantworten; belegt ist, was d-migrate sendet und dass Treiber und d-migrate die TDS-Antworten der Bibliothek verarbeiten. Aufwandsgrößen (S/M/L) sind Schätzungen der Sonde, nicht gemessen. Gemessen ist der Text und die Parametertypen (Int64, UnicodeText, Date, vom Binder angenommen); die Sonde beantwortete das MERGE mit einer festen Zeile, nichts wurde gespeichert. Der Abbruch-Pfad (`abort`) nimmt laut Sonde den Massenpfad; ein `INSERT` mit Parametern wurde nicht beobachtet. **Hülle:** Der Plan ist knapp; der Implementer erweitert §3 im ersten Lauf, nachdem die Entscheidung in [slice-adr-testserver-bestand-und-katalog](slice-adr-testserver-bestand-und-katalog.md) vorliegt (Ort und Schnitt hängen an ihr).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Der Standardpfad mit Paket 0x07 — [slice-schreibpfad-bulk-load](slice-schreibpfad-bulk-load.md).
- Allgemeines `MERGE` (mehrere Quellen, mehrere WHEN-Zweige, `DELETE`-Zweig, beliebige `OUTPUT`-Spalten): erkannt wird nur die Gestalt, die d-migrate sendet; alles andere bleibt `TDS_UNSUPPORTED_SQL`.
- Katalog und DDL — [slice-katalog-und-ddl-zustand](slice-katalog-und-ddl-zustand.md).
- Konfliktsemantik jenseits von Schlüsselvergleich (Bedingungen, Trigger).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit.

Liefer-Punkte (3):

- [ ] **Form:** SPEC-036 legt die Form des MERGE fest, das d-migrate sendet (Erkennung am Text, Zuordnung der Parameter); die Ergebnismenge `$action` und der Änderungszähler sind festgelegt (Setzung, nicht am echten SQL Server gemessen).
- [ ] **Ausführung gegen den Bestand:** Einfügen, wenn der Schlüssel fehlt; bei `skip` unverändert, bei `update` ändern; Tests je Zweig und für NULL-Parameter und Typgrenzen ([TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc) Boundary).
- [ ] **Beleg am fremden Client:** `data import` und `data transfer` mit `--on-conflict skip` und `--on-conflict update` gegen den Testserver enden mit Exit 0; ein `data export` danach zeigt die erwarteten Zeilen; Ausgabe mit Rechner, Last und Image-Digest.

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
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/` (Scanner, Parser, Planer) | update | neue Form; SPEC-036 schließt `OUTPUT`-Klauseln heute aus |
| Bestand (Ort laut ADR) | update | Ausführung des Konfliktzweigs |
| `spec/spezifikation.md` SPEC-036 | update | Form M, Setzungen gekennzeichnet |
| Tests | neu | Happy/Boundary/Negative nach [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate) |

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-adr-testserver-bestand-und-katalog](slice-adr-testserver-bestand-und-katalog.md) liegt in `done/` (Ort des Katalogs und des Bestands entschieden) und [slice-katalog-und-ddl-zustand](slice-katalog-und-ddl-zustand.md) liegt in `done/` (Bestand besteht).

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

- Aufwand der Sonde: M bis L; die Erkennung von `MERGE` weitet die Liste der Formen, die SPEC-036 „abschließend“ nennt (nicht vom Nutzer bestätigt). — **Ausgang:** offen bis zur Closure.
- Die Gestalt des MERGE hängt an d-migrate 1.12.0 und seiner Spaltenliste; eine andere Version kann einen anderen Text senden (nicht gemessen). — **Ausgang:** offen bis zur Closure.
- Wo der Bestand lebt, bestimmt, wo die Erkennung lebt: im Testserver (Custom-Backend) entsteht ein zweiter Scanner neben dem der Bibliothek, wenn die Entscheidung nicht die Bibliothek wählt. — **Ausgang:** offen bis zur Closure.

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
