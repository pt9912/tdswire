# Slice slice-simulation-spaltenflags-an-referenz-angleichen: Spaltenflags der Tabellenspalten im COLMETADATA (`0x0008`/`0x0009` der Referenz gegen `0x0000`/`0x0001` der Bibliothek) und `IsReadOnly` in `GetSchemaTable()` messen und entscheiden

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** ohne Welle (keine Closure-Bedingung außerhalb der DoD dieses Slice; Folge-Slice des Reviews des Baus von Form F, Befund F-2 in `docs/reviews/2026-11-22-slice-simulation-schemaonly-keyinfo-bau-review.md`; Nutzerentscheidung 2026-11-22: Register-Eintrag und Folge-Slice, nicht starten).

**Bezug:** [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) (Scope: Spaltenbeschreibung aus mitgeteilten Tabellen), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität) (Kompatibilität: Abweichung des Servers ist Schuld), [ADR-0059](../../adr/0059-schemaonly-ist-eine-erkannte-form-keyinfo-bleibt-abgewiesen.md) (Form F; Entscheidung 3: Spalten der Antwort wie die der Auswahl)

**Berührte Spec-Stellen:** [SPEC-036](../../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) (Form F, Spalten der Antwort) · [SPEC-045](../../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig) (Live-Lauf und Ausnahmeliste; verglichene Felder von `GetSchemaTable()`) · [SPEC-002](../../../../spec/spezifikation.md#spec-002--interner-query-vertrag) (Spaltenbeschreibung des Query-Vertrags, falls ein Feld hinzukommt) · [Kompatibilitätsmatrix](../../kompatibilitaetsmatrix.md) (Zeile `SchemaOnly`).

**Verantwortlich:** —

**Autor:** pt9912 (Entwurf in der Implementer-Rolle auf Nutzerentscheidung 2026-11-22). **Datum:** 2026-11-22.

---

## 1. Ziel und Abgrenzung

**Ziel:** Für die Spaltenflags der Tabellenspalten im COLMETADATA ist am Server gemessen, was sie bei der Referenz bedeuten, und entschieden, ob der Code an die Referenz angeglichen wird oder die Abweichung eine benannte Ausnahme wird; `IsReadOnly` in `GetSchemaTable()` ist dabei entweder gleich der Referenz oder als Abweichung benannt.

**Ist-Stand** (gelesen am 2026-11-22; was nicht als gemessen steht, ist aus Datei oder Code gelesen):

- **Gemessen** (Messprotokoll [W-27](../../../messungen/referenzabgleich.md), Zeilen `spaltenform` der Messdateien beider Instanzen): die Referenz (SQL Server 2022 und 2025) schreibt für jede Tabellenspalte im COLMETADATA die Flags `0x0008` (so bei `person.Id`) und `0x0009` (die übrigen Spalten von `person`); das Bit `0x0001` trägt die Nullbarkeit, die Bedeutung von `0x0008` ist nicht gemessen (im KDoc von `TdsType.kt` das Bit `usUpdateable` mit Wert „unbekannt“). Der Referenzclient meldet in `GetSchemaTable()` für alle 16 Schemazeilen je Instanz `IsReadOnly=False` (`referenztest-dotnet/Mitschnitt/messung/2022/schemaonly-spalten.messung` und `…/2025/…`, Zeilen `# beobachtung: schemaonly-spalten … IsReadOnly=False`).
- **Gelesen am Code:** die Bibliothek schreibt für Tabellenspalten `0x0000` (nicht nullbar) und `0x0001` (nullbar); `extraFlags` setzt im Simulationspfad nur `BulkCopyProbe`. Das gilt für jede Auswahl aus einer Tabelle, nicht nur für Form F.
- **Nicht gemessen:** was der Referenzclient bei den Flags `0x0000`/`0x0001` als `IsReadOnly` meldet. Nach dem Muster von W-33 (Flags ohne die Bits `0x0C`, `IsReadOnly=True`) voraussichtlich `True`; das ist eine Folgerung, kein Beleg.
- **Sensor:** der Live-Lauf vergleicht `IsReadOnly` nicht: `LiveSzenarioTests.SchemaSchritt` liest Name, Typname, Größe, Precision, Scale und Nullbarkeit (bei `KeyInfo` zusätzlich `IsKey` und `BaseTableName`); das Messprotokoll nennt für `SchemaOnly` die fünf Felder Typname, Größe, Precision, Scale und Nullbarkeit (Abschnitt „Server-Seite (Schema)“). Ein Gleichlauf der Flags und von `IsReadOnly` ist damit nicht belegt.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Die Erweiterung von Form F oder `KeyInfo` — [slice-simulation-keyinfo-erkennen](slice-simulation-keyinfo-erkennen.md) trägt `KeyInfo`; die Flags betreffen jede Auswahl aus einer Tabelle, nicht eine Form.
- Weitere Felder von `GetSchemaTable()` außer `IsReadOnly` (Identität, berechnete Spalten, Eindeutigkeit) — nicht gemessen, kein Träger.
- `GetSchemaTable()` für andere Clients als `Microsoft.Data.SqlClient` 7.1.1 — nicht gemessen ([AGENTS.md](../../../../AGENTS.md) §3.8).
- Eine Änderung an Form F: sie bleibt gebaut, und der Vergleich des Live-Laufs bleibt bei den fünf Feldern, bis dieser Slice entschieden hat.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit.

- [ ] **Messung am Server:** die Flags der Spalten im COLMETADATA (Bit `0x0008` und die übrigen) und `IsReadOnly` in `GetSchemaTable()` an beiden gepinnten Referenzinstanzen, für Spalten einer Tabelle (Schlüssel, nullbar, nicht nullbar, `typen`), eine Auswahl ohne `SET` und `SchemaOnly`; Messpunkte im Messprotokoll, Ablage unter `referenztest-dotnet/Mitschnitt/messung/{2022,2025}/`, Erzeuger im Repo (`MessungWire`); ein Lauf je Instanz, Grenzen genannt.
- [ ] **Entscheidung:** Code an die Referenz angleichen (die Bibliothek schreibt die gemessenen Flags für Tabellenspalten, `IsReadOnly` im Live-Lauf verglichen) **oder** die Abweichung als Ausnahme mit Art und Träger in der Ausnahmeliste führen; die Wahl trifft der Nutzer (Spezifikation ist das Zielbild: eine Abweichung ist Schuld, kein Merkmal).
- [ ] **Nachzug:** je nach Entscheidung Bau mit Handvektor aus der Messung (Encoder der Spaltenflags, Tests, Mutation je Zusage) oder Ausnahme in `referenztest-dotnet/Vergleich/live-ausnahmen.tsv`; [SPEC-036](../../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig), [SPEC-045](../../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig) (verglichene Felder), Messprotokoll, Matrix, `docs/user/` soweit die Spalten berührt sind.
- [ ] `make gates` Exit 0, dazu je Art der Änderung nach `harness/README.md` §Minimal agent workflow.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor (`.harness/skills/reviewer.md`), kein Self-Review.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben: `BEO-TDS/spaltenflags-der-tabellenspalten-weichen-von-der-referenz-ab` (Ausgang nachführen); keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — Pfad-Kandidaten für §8; geprüft ist das Vorhandensein der Dateien am 2026-11-22, nicht ihre Eignung; der Umfang folgt der Messung und der Entscheidung.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `referenztest-dotnet/Vergleich/MessungWireTests.cs`, `referenztest-dotnet/Mitschnitt/messung/2022/`, `referenztest-dotnet/Mitschnitt/messung/2025/` | update, neu | Messszenarien für Flags und `IsReadOnly` |
| `docs/messungen/referenzabgleich.md` | update | Messpunkte und Grenzen |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/token/TypeInfoWriter.kt`, `src/main/kotlin/io/github/pt9912/tdswire/protocol/token/TdsType.kt` | update, falls angeglichen wird | Spaltenflags der Tabellenspalten |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/`, `src/test/kotlin/io/github/pt9912/tdswire/simulation/` | update, falls angeglichen wird | Handvektoren der Flags |
| `referenztest-dotnet/Vergleich/LiveSzenarioTests.cs`, `referenztest-dotnet/Vergleich/live-ausnahmen.tsv` | update | `IsReadOnly` im Vergleich, oder die Ausnahme |
| `spec/spezifikation.md`, `docs/plan/kompatibilitaetsmatrix.md` | update | Nachzug nach DoD |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf (Testdateien mit Kennung der Akzeptanzkriterien).

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`open` → `next` → `in-progress`): Nutzerzeichen und ein Verantwortlicher (`Verantwortlich:` ist bis dahin `—`); die Messung an den Referenzinstanzen verlangt die Messsperre und die EULA-Bestätigung für das gepinnte Image (bestätigt 2026-11-01, 2026-11-17). **Nicht starten**, bevor der Nutzer es beauftragt (Entscheidung 2026-11-22: Register-Eintrag und Folge-Slice; die Messung folgt später).

**Rückführungen — vorab benennen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2, oder die gemessenen Flags verlangen eine Änderung der Spaltenbeschreibung im öffentlichen Query-Vertrag.
- `in-progress` → `open` (blockiert): die gepinnten Referenzinstanzen sind nicht beziehbar.

## 5. Closure-Trigger

Alle Punkte der DoD in §2 erledigt, `make gates` und `make docs-check` Exit 0, und der Ausgang des Registers `BEO-TDS/spaltenflags-der-tabellenspalten-weichen-von-der-referenz-ab` ist nachgeführt (angeglichen oder Ausnahme mit Träger); die Closure-Notiz mit Steering-Loop-Lerneintrag ist geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang.

- Die Bedeutung des Bits `0x0008` ist nicht gemessen; ein Angleichen auf `0x0008`/`0x0009` ohne Messung der Wirkung am Client kann `IsReadOnly` und weitere Felder anders setzen als gedacht. — **Ausgang:** offen bis zur Closure; die Messung (Liefer-Punkt 1) kommt vor dem Bau.
- Die Flags betreffen jede Auswahl aus einer Tabelle, nicht nur Form F; eine Änderung berührt Handvektoren bestehender Tests. — **Ausgang:** offen bis zur Closure; die Tests mit Handvektor werden an der Messung nachgezogen.
- `GetSchemaTable()` ist für einen Client gemessen; andere Clients lesen die Flags anders. — **Ausgang:** offen bis zur Closure; die Aussage trägt für `Microsoft.Data.SqlClient` 7.1.1 ([AGENTS.md](../../../../AGENTS.md) §3.8; `BEO-TDS/festlegung-gilt-nur-fuer-das-clientprofil`).
- Größe: drei Liefer-Punkte. — **Ausgang:** offen bis zur Closure; Rückführung nach §4.

## 7. Closure-Notiz

Wird bei der Closure gefüllt.

**Herkunft:** Review des Baus von Form F, [Befund F-2](../../../reviews/2026-11-22-slice-simulation-schemaonly-keyinfo-bau-review.md); Register `BEO-TDS/spaltenflags-der-tabellenspalten-weichen-von-der-referenz-ab`; Nutzerentscheidung 2026-11-22: Register-Eintrag und Folge-Slice, Messung später, Form F wird nicht aufgehalten.

## 8. Sub-Area-Prüfungen und Modus-Begründung

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register (`../observations/BEO-TDS/`) gelesen; Treffer: `spaltenflags-der-tabellenspalten-weichen-von-der-referenz-ab` (offen), `festlegung-gilt-nur-fuer-das-clientprofil`, `messung-am-echten-sql-server-fehlt`. Zähler-Stände stehen in den Dateien des Registers, nicht hier.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
