# Slice slice-katalog-und-ddl-zustand: Katalog-Adapter (sys.*, INFORMATION_SCHEMA) und Zustand nach DDL für schema reverse, compare und migrate --execute

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-testserver-manuell

**Bezug:** [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md), [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md)

**Berührte Spec-Stellen:** SPEC-035 · SPEC-036 (Katalogabfragen, DDL-Formen) · SPEC-059 (falls die Fläche wächst) · SPEC-040 · [TDS-FA-11.a](../../../../spec/spezifikation.md#tds-fa-11a--simulations-backend)

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-05.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert; jeder Ausschluss mit Begründung.

**Ziel:** Die Katalogabfragen, die d-migrate für `schema reverse` (auch `--include-all`), `schema compare` und `schema migrate` stellt, werden aus dem Bestand der Tabellen beantwortet, und DDL (`CREATE TABLE`, `ALTER TABLE ... ADD`) ändert diesen Bestand, sodass die Nachprüfung von `schema migrate --execute` besteht (Hürden 6 und 7 der Sonde).

**Quelle und Vorbehalt.** Auslöser ist die Sonde vom 2026-11-05 ([d-migrate-sonde-2026-11-05](../../../messungen/d-migrate-sonde-2026-11-05.md)). Ihr Vorbehalt gilt hier: alles, was dort „lief“, lief gegen erfundene Katalogantworten; belegt ist, was d-migrate sendet und dass Treiber und d-migrate die TDS-Antworten der Bibliothek verarbeiten. Aufwandsgrößen (S/M/L) sind Schätzungen der Sonde, nicht gemessen. Die Sonde sah 26 bis 29 Katalogabfragen je `schema reverse` und die DDL des `--execute`-Laufs; die Antworten waren erfunden. **Hülle:** Der Plan ist knapp; der Implementer erweitert §3 im ersten Lauf, nachdem die Entscheidung in [slice-adr-testserver-bestand-und-katalog](slice-adr-testserver-bestand-und-katalog.md) vorliegt (Ort und Schnitt hängen an ihr).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Schreiben von Zeilen (Einfügen, Massenladen, MERGE) — [slice-schreibpfad-bulk-load](slice-schreibpfad-bulk-load.md) und [slice-schreibpfad-merge-output](slice-schreibpfad-merge-output.md); hier nur Struktur.
- Die Entscheidung über Ort von Katalog und Bestand — [slice-adr-testserver-bestand-und-katalog](slice-adr-testserver-bestand-und-katalog.md); dieser Slice setzt sie um.
- Anmeldung, SET-Anweisungen, `sp_executesql`-Formen und `WHERE 1 = 0` — [slice-d-migrate-verbindung-und-export](slice-d-migrate-verbindung-und-export.md).
- Katalogsichten, die kein Lauf des Solls stellt (zum Beispiel Partitionen oder Volltextindizes mit Inhalt): erst die Sichten, die die Läufe tatsächlich abfragen, im Rest antworten leere Resultsets mit den Spalten (die Sonde: leere Antworten genügen für `--include-all`, gemessen nur gegen ihr aufzeichnendes Backend).
- Typ FLOAT, soweit die Entscheidung ihn nicht aufnimmt (Hürde 12).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit.

Liefer-Punkte (3):

- [ ] **Katalog:** Die Abfragen von `schema reverse` und `schema compare` (Tabellen, Spalten mit Typ, Schlüssel, Indizes, Defaults, Prüfbedingungen; Sichten, Sequenzen, Routinen leer) werden aus dem Bestand beantwortet; ein Test je Abfragegruppe liest die Antwort mit dem Treiber oder mit Handvektoren aus einem erneuten Mitschnitt.
- [ ] **Zustand nach DDL:** `CREATE TABLE`, `ALTER TABLE ... ADD` und die Transaktionsanweisungen als SQL-Text (`set implicit_transactions on|off`, `IF @@TRANCOUNT > 0 COMMIT TRAN`) werden angenommen; ein folgendes `schema reverse` zeigt die geänderte Struktur. Rücknahme über mehrere Anweisungen ist Out-of-Scope von [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate).
- [ ] **Beleg am fremden Client:** `schema reverse`, `schema compare` und `schema migrate --execute` von d-migrate 1.12.0 gegen den Testserver enden mit Exit 0; die Ausgabe (inklusive der Nachprüfung) steht mit Rechner, Last und Image-Digest im Slice oder in einem Messprotokoll.

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
| Katalog-Adapter (Ort laut ADR) | neu | Ableitung von `sys.*` aus dem Bestand; Umfang laut Sonde Hürde 6 |
| Bestand mit DDL (Ort laut ADR) | neu | veränderlich, nur im Arbeitsspeicher ([TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate)) |
| `spec/spezifikation.md` | update | Formen und Katalog festlegen; Setzung gekennzeichnet |
| Tests; `tools/` und `make`-Lauf mit d-migrate | neu | Lauf mit Image per Digest, nicht in `make gates` (Muster [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)-Läufe: Docker, netzfrei prüfen) |

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-adr-testserver-bestand-und-katalog](slice-adr-testserver-bestand-und-katalog.md) liegt in `done/` (Ort des Katalogs und des Bestands entschieden).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte.
- `in-progress` → `open` (blockiert): die Entscheidung in [slice-adr-testserver-bestand-und-katalog](slice-adr-testserver-bestand-und-katalog.md) trägt den Fall nicht oder wird überstimmt.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln.

DoD vollständig, `make gates` grün, die Ausgabe der drei d-migrate-Läufe liegt vor, Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — jedes Risiko bekommt genau einen Ausgang.

- Umfang des Katalogs: 14 verschiedene `sys.*`-Quellen laut Sonde; ob der Adapter alle oder nur die abgefragten braucht, ist nicht gemessen (Aufwand der Sonde: L). — **Ausgang:** offen bis zur Closure.
- Die Nachprüfung von `schema migrate --execute` vergleicht Struktur gegen die Soll-Datei; Abweichungen in Typabbildung (zum Beispiel FLOAT, Hürde 12) können sie rot färben — nicht gemessen. — **Ausgang:** offen bis zur Closure.
- Wachsen Namen der öffentlichen Fläche, kollidiert das mit [slice-oeffentliche-flaeche-verkleinern-und-belegen](slice-oeffentliche-flaeche-verkleinern-und-belegen.md) (Reihenfolge in [welle-testserver-manuell](../welle-testserver-manuell.md) §5). — **Ausgang:** offen bis zur Closure.

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
