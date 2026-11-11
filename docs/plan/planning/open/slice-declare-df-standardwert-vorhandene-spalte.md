# Slice slice-declare-df-standardwert-vorhandene-spalte: Text mit `DECLARE @df … EXEC sp_executesql` für einen Standardwert an einer vorhandenen Spalte

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-testserver-manuell

**Bezug:** [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), [ADR-0046](../../adr/0046-oeffentliche-flaeche-der-katalog-elemente.md)

**Berührte Spec-Stellen:** SPEC-036 (erkannte Anweisungsformen, Form D, Absatz Grenzen) · SPEC-035 (Standardwert im Katalog) · `TDS-FA-11.a` (Ablaufpunkt 9, Strukturänderung)

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912 (Entwurf in der Planner-Rolle, Closure von [slice-katalog-indizes-defaults-fremdschluessel](../done/slice-katalog-indizes-defaults-fremdschluessel.md)). **Datum:** 2026-11-11.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung**; die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks.

**Zurückgestellt.** Der Slice startet nur über das Register oder die Abnahme (Start-Trigger in §4), nicht von selbst. Anlage als Folge-Slice: Standardantwort auf die Empfehlung des Verifiers, **umkehrbar** (Entscheid des Nutzers steht aus; der Planner hat sie als Standard gesetzt).

**Ziel:** `schema migrate --execute` von d-migrate mit einem Standardwert an einer vorhandenen Spalte endet gegen den Testserver mit Exit 0 wie gegen den echten SQL Server. Heute sendet d-migrate dafür einen Text, der mit `DECLARE @df sysname, @sql nvarchar(max); SELECT @df = dc.name FROM sy…` beginnt und über `EXEC sp_executesql` ein `ALTER TABLE … ADD CONSTRAINT [df_…] DEFAULT …` ausführt; der Text ist nicht erkannt, der Lauf endet mit Exit 5 (`Unsupported SQL.`, Messprotokoll [Zeile c1](../../../messungen/katalog-indizes-defaults-fremdschluessel-d-migrate-2026-11-09.md), im Verifikationsbericht zu [slice-katalog-indizes-defaults-fremdschluessel](../done/slice-katalog-indizes-defaults-fremdschluessel.md) reproduziert). Ein Standardwert in `CREATE TABLE` oder an einer neuen Spalte ist nicht betroffen.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- `ALTER TABLE … DROP CONSTRAINT [IF EXISTS]` (Punkt 2 des Registers) — kleiner, trifft den Validator zuerst: [slice-drop-constraint-form-d5](../in-progress/slice-drop-constraint-form-d5.md).
- Allgemeines T-SQL (Variablen, Verzweigungen, Auswahl aus Katalogsichten) — der Slice erkennt den Text, den d-migrate 1.12.0 rendert, als feste Form; ein Interpreter für T-SQL ist nicht Gegenstand der Zusage in `TDS-FA-11`.
- Identität und berechnete Spalten — Grenze von [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), Entscheidung des Nutzers.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **Messung** ([TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)): der vollständige Text, den d-migrate 1.12.0 für einen Standardwert an einer vorhandenen Spalte sendet (Mitschnitt), und die Antwort des echten SQL Servers darauf (Developer-Image, gepinnter Digest, EULA bestätigt, nur lokal): Tokenfolge, Zählung, Verhalten bei vorhandenem und fehlendem Standardwert; Messprotokoll in `docs/messungen/`.
- [ ] **Bau** ([TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend)): der Text wird als feste Form erkannt (Parameter: Tabelle, Spalte, Standardwert), plant gegen den Katalog (Tabelle und Spalte vorhanden, ein vorhandener Standardwert wird ersetzt wie am Server gemessen) und geht an den Integrator; Tests mit dem Handvektor des Mitschnitts und den Zeilen der Messung; Mutationen je Zusage.
- [ ] **Beleg und Fortschreibung** ([TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate)): `schema migrate --execute` mit `default` an einer vorhandenen Spalte endet gegen den Testserver mit Exit 0 und `IDENTICAL`; `SPEC-036`, Nutzer-Doku und das Register [katalog-elemente-grenzen-der-angleichung](../observations/BEO-TDS/katalog-elemente-grenzen-der-angleichung/state.md) (Punkt 1) nachgezogen.

Nachweis-Pflichten für alle Punkte:

- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Haken der Liefer-Punkte setzt der Planner nach der Verifikation, nicht der Implementer (`BEO-TDS/haken-vor-verifikation-gesetzt`).
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
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/Statement.kt`, `StatementParser.kt`, `SqlScanner.kt`, `StructureParts.kt`, `ColumnParts.kt`, `StatementPlanner.kt`, `StructurePlanner.kt` | update / neu | feste Form für den Text mit `DECLARE @df`, Planung gegen den Katalog |
| `src/test/kotlin/.../simulation/` (`DMigrateWire.kt`, Formen- und Strukturtests), `src/integrator/kotlin/` | update / neu | Handvektor des Mitschnitts, Zeilen der Messung, Mutationen |
| `docs/messungen/` — Messprotokoll des Textes am echten Server <!-- d-check:ignore (geplant: neue Datei) --> | neu | Eingang des Baus |
| `spec/spezifikation.md` (`SPEC-036`, Änderungsprotokoll), `docs/user/testserver-manuell.md`, `docs/user/einbettung-und-betrieb.md` | update | Zusage und Grenzen |
| `docs/plan/planning/observations/BEO-TDS/katalog-elemente-grenzen-der-angleichung/` | update | Zustand und Beleg von Punkt 1 |

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): Auslösung über das Register [katalog-elemente-grenzen-der-angleichung](../observations/BEO-TDS/katalog-elemente-grenzen-der-angleichung/state.md) (Punkt 1) oder über [slice-abnahme-d-migrate](slice-abnahme-d-migrate.md), wenn der Validator einen Standardwert an einer vorhandenen Spalte verlangt; [slice-drop-constraint-form-d5](../in-progress/slice-drop-constraint-form-d5.md) liegt in `done/` (Konflikte am Planer); die EULA für das gepinnte Developer-Image ist für diesen Lauf bestätigt; ein Verantwortlicher ist eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): der Scanner braucht Variablen und dynamisches SQL über die feste Form hinaus; Schnittvorschlag: Scanner-Erweiterung | Planung.
- `in-progress` → `open` (blockiert): der Nutzer bestätigt die EULA nicht, oder d-migrate rendert einen anderen Text als im Mitschnitt.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln.

DoD vollständig, `make gates` grün, das `schema migrate --execute` mit Standardwert an vorhandener Spalte endet mit Exit 0, Review und Verifikation liegen vor, Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — jedes Risiko bekommt genau einen Ausgang.

- Der Text hängt an der Version von d-migrate (1.12.0); eine andere Version rendert ihn anders. — **Ausgang:** offen bis zur Closure.
- Die feste Form trägt nur den Text von d-migrate; ein anderer Client mit eigenem `DECLARE` bleibt abgewiesen. — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — offene Beobachtungen sichten:** `BEO-TDS/katalog-elemente-grenzen-der-angleichung` (Herkunft, Punkt 1), `BEO-TDS/messung-am-echten-sql-server-fehlt` (Eingang der Messung).

Alle berührten Sub-Areas GF.
