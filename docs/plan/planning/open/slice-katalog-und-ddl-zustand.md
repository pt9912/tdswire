# Slice slice-katalog-und-ddl-zustand: Katalog aus den mitgeteilten Tabellen und Strukturänderung über den Integrator: schema reverse, compare und migrate --execute

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

**Ziel:** Die Bibliothek beantwortet die Katalogabfragen, die d-migrate für `schema reverse` (auch `--include-all`), `schema compare` und `schema migrate` stellt, aus den ihr mitgeteilten Tabellen, gibt DDL (`CREATE TABLE`, `ALTER TABLE ... ADD`) an den Integrator weiter und zeigt danach die geänderte Struktur; der Testserver legt als Integrator die Tabellen an, hält sie veränderlich im Arbeitsspeicher und führt die DDL aus, sodass die Nachprüfung von `schema migrate --execute` besteht (Hürden 6 und 7 der Sonde).

**Quelle und Vorbehalt.** Auslöser ist die Sonde vom 2026-11-05 ([d-migrate-sonde-2026-11-05](../../../messungen/d-migrate-sonde-2026-11-05.md)). Ihr Vorbehalt gilt hier: alles, was dort „lief“, lief gegen erfundene Katalogantworten; belegt ist, was d-migrate sendet und dass Treiber und d-migrate die TDS-Antworten der Bibliothek verarbeiten. Aufwandsgrößen (S/M/L) sind Schätzungen der Sonde, nicht gemessen. Die Sonde sah 26 bis 29 Katalogabfragen je `schema reverse` und die DDL des `--execute`-Laufs; die Antworten waren erfunden. **Hülle:** Der Plan ist knapp; der Implementer erweitert §3 im ersten Lauf, nachdem die Entscheidung in [slice-adr-testserver-bestand-und-katalog](../done/slice-adr-testserver-bestand-und-katalog.md) vorliegt (Form der Weitergabe und Namen der Fläche hängen an ihr). **Aufteilung:** Die Funktion liegt in der Bibliothek (`src/main`, Packages `simulation`, `protocol`); der Testserver (`src/testserver`) ist Integrator: Bestand, Callbacks, Beispieltabellen. Jeder Liefer-Punkt unten nennt seinen Anteil.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Schreiben von Zeilen (Einfügen, Massenladen, MERGE) — [slice-schreibpfad-bulk-load](slice-schreibpfad-bulk-load.md) und [slice-schreibpfad-merge-output](slice-schreibpfad-merge-output.md); hier nur Struktur.
- Die Entscheidungen zu Weitergabe, Katalog nach Änderungen, FLOAT und Fläche — [slice-adr-testserver-bestand-und-katalog](../done/slice-adr-testserver-bestand-und-katalog.md); dieser Slice setzt sie um. Die Verteilung (Bibliothek = Funktion ohne Speicher, Testserver = Integrator) ist entschieden (Nutzer, 2026-11-05).
- Anmeldung, SET-Anweisungen, `sp_executesql`-Formen und `WHERE 1 = 0` — [slice-d-migrate-verbindung-und-export](../done/slice-d-migrate-verbindung-und-export.md).
- Katalogsichten, die kein Lauf des Solls stellt (zum Beispiel Partitionen oder Volltextindizes mit Inhalt): erst die Sichten, die die Läufe tatsächlich abfragen; im Rest antworten leere Resultsets mit den Spalten (die Sonde: leere Antworten genügen für `--include-all`, gemessen nur gegen ihr aufzeichnendes Backend).
- Typ FLOAT, soweit die Entscheidung ihn nicht aufnimmt (Hürde 12).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit.

Liefer-Punkte (3):

- [ ] **Bibliothek, Katalog:** Die Abfragen von `schema reverse` und `schema compare` (Tabellen, Spalten mit Typ, Schlüssel, Indizes, Defaults, Prüfbedingungen; Sichten, Sequenzen, Routinen leer) werden aus den mitgeteilten Tabellen beantwortet; ein Test je Abfragegruppe liest die Antwort mit dem Treiber oder mit Handvektoren aus einem erneuten Mitschnitt.
- [ ] **Bibliothek, Strukturänderung:** `CREATE TABLE`, `ALTER TABLE ... ADD` und die Transaktionsanweisungen als SQL-Text (`set implicit_transactions on|off`, `IF @@TRANCOUNT > 0 COMMIT TRAN`) werden erkannt, die DDL geht nach der Entscheidung an den Integrator, und der Katalog zeigt danach die vom Integrator gemeldete Struktur. Rücknahme über mehrere Anweisungen ist Out-of-Scope von [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate).
- [ ] **Testserver und Beleg:** Der Testserver hält die Tabellen veränderlich im Arbeitsspeicher, implementiert die Struktur-Callbacks und teilt der API die Tabellen mit; `schema reverse`, `schema compare` und `schema migrate --execute` von d-migrate 1.12.0 enden mit Exit 0; die Ausgabe (inklusive der Nachprüfung) steht mit Rechner, Last und Image-Digest im Slice oder in einem Messprotokoll.

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
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/` (Katalog-Ableitung, DDL-Formen, `internal`) | neu / update | Ableitung von `sys.*` aus den mitgeteilten Tabellen; Umfang laut Sonde Hürde 6 |
| `src/main/kotlin/io/github/pt9912/tdswire/runtime/SimulationSetup.kt` und die Weitergabe (Callback oder Vertrag nach ADR) | update | DDL an den Integrator, Katalog danach (Frage 2 der ADR); öffentliche Namen nur nach Begründung |
| `src/testserver/kotlin/` (Bestand, Callbacks, Beispieltabellen) | update | Integrator-Anteil: veränderlicher Bestand im Arbeitsspeicher; keine Katalog- oder Parserlogik dort |
| `spec/spezifikation.md` SPEC-035, SPEC-036, SPEC-040, SPEC-059 | update | Formen, Katalog und Weitergabe festlegen; Setzungen gekennzeichnet |
| Tests; Lauf mit d-migrate | neu | Lauf mit Image per Digest, nicht in `make gates`; ob ein `make`-Ziel entsteht, entscheidet [slice-abnahme-d-migrate](slice-abnahme-d-migrate.md) |

**Anschluss an die Entscheidung** ([ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), `Accepted` am 2026-11-06, Messungen: [Messprotokoll des Spikes](../../../messungen/callback-loesung-spike-2026-11-05.md); Liste in §3 von [slice-adr-testserver-bestand-und-katalog](../done/slice-adr-testserver-bestand-und-katalog.md)). Spec-Änderungen dieses Slice: `SPEC-035` (Katalog-Anbieter, Strukturänderungs-Callback, `SimColumn.maxLength`, Abbildung von `SqlType` auf die Typen des Katalogs; gemessen nur `integer`, `nvarchar(4000)`, `date`, `decimal`; `bigint`, `bit`, `time`, `datetime2` sind Setzung), `SPEC-036` (Katalogabfragen mit dem Wortlaut aus d-migrate 1.12.0, `CREATE TABLE`, `ALTER TABLE … ADD`, `SELECT SCHEMA_NAME()`, `IF @@TRANCOUNT > 0 COMMIT|ROLLBACK TRAN`, Satz „abschließend“; dazu [TDS-FA-11.a](../../../../spec/spezifikation.md#tds-fa-11a--simulations-backend) Schritt 1 und die Form C: Texte mit mehreren Anweisungen ohne `;` sind gemessen), `SPEC-059` (zweiter Konstruktor von `SimulationBackend` und `SimColumn.maxLength`, kein neuer Typname), `SPEC-040` (Läufe `schema reverse`, `schema compare`, `schema migrate --execute`). Entschieden (ADR `Accepted`, 2026-11-06): Fassung F (zweiter Konstruktor, kein neuer Typname) und `SimColumn.maxLength` für die Länge von Texten; Reihenfolge: nach `slice-testserver-gradle-modul` und nach dem Schnitt der öffentlichen Fläche. Start-Trigger: beide in `done/`. Aus dem Spike: 26 Anweisungen für `schema reverse`, 28 mit `--include-all` (2 Tabellen); ein Soll mit Textlängen außer 4000 endet mit Exit 5, weil der Katalog `nvarchar(4000)` meldet; `schema compare` meldet `identifier -> integer` (bleibt ungelöst, Identität ohne Feld); die Länge löst `maxLength`. Ohne Anbieter besteht die Nachprüfung von `schema migrate --execute` nicht (Gegenprobe c4).

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-adr-testserver-bestand-und-katalog](../done/slice-adr-testserver-bestand-und-katalog.md) liegt in `done/` (Weitergabe, Katalog nach Änderungen und Fläche entschieden), [slice-testserver-gradle-modul](../next/slice-testserver-gradle-modul.md) liegt in `done/` (der Testserver hat seinen Pfad im Modul, damit dieser Slice ihn nur einmal schreibt) und [slice-oeffentliche-flaeche-verkleinern-und-belegen](slice-oeffentliche-flaeche-verkleinern-und-belegen.md) liegt in `done/` (der Schnitt der Fläche steht vor den Namen, die dieser Slice hinzufügt: zweiter Konstruktor von `SimulationBackend`, `SimColumn.maxLength`). Reihenfolge vom Nutzer am 2026-11-06 entschieden ([ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md)).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte; Schnittvorschlag: Anteil der Bibliothek und Anteil des Testservers als zwei Slices.
- `in-progress` → `open` (blockiert): die Entscheidung in [slice-adr-testserver-bestand-und-katalog](../done/slice-adr-testserver-bestand-und-katalog.md) trägt den Fall nicht oder wird überstimmt.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln.

DoD vollständig, `make gates` grün, die Ausgabe der drei d-migrate-Läufe liegt vor, Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — jedes Risiko bekommt genau einen Ausgang.

- Umfang des Katalogs: 14 verschiedene `sys.*`-Quellen laut Sonde; ob die Ableitung alle oder nur die abgefragten braucht, ist nicht gemessen (Aufwand der Sonde: L). — **Ausgang:** offen bis zur Closure.
- Die Nachprüfung von `schema migrate --execute` vergleicht Struktur gegen die Soll-Datei; Abweichungen in der Typabbildung (zum Beispiel FLOAT, Hürde 12) können sie rot färben — nicht gemessen. — **Ausgang:** offen bis zur Closure.
- Wachsen Namen der öffentlichen Fläche (Callbacks, Anbieter der Tabellenliste), kollidiert das mit [slice-oeffentliche-flaeche-verkleinern-und-belegen](slice-oeffentliche-flaeche-verkleinern-und-belegen.md) (Reihenfolge in [welle-testserver-manuell](../welle-testserver-manuell.md) §5). — **Ausgang:** offen bis zur Closure.

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
