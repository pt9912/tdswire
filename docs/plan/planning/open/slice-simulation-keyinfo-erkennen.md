# Slice slice-simulation-keyinfo-erkennen: Simulations-Backend: `ExecuteReader(KeyInfo)` beantworten (TABNAME, COLINFO, versteckte Schlüsselspalte, `SET NO_BROWSETABLE`) (A-8)

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** ohne Welle (keine Closure-Bedingung außerhalb der DoD dieses Slice; Träger des Rests der Lücke A-8 der Ausnahmeliste des Referenzabgleichs nach der Nutzerentscheidung vom 2026-11-22: nur `SchemaOnly` in [slice-simulation-schemaonly-keyinfo](../in-progress/slice-simulation-schemaonly-keyinfo.md), `KeyInfo` hier).

**Bezug:** [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) (Scope: Katalog und Spaltenbeschreibung aus mitgeteilten Tabellen), [TDS-FA-05](../../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0059](../../adr/0059-schemaonly-ist-eine-erkannte-form-keyinfo-bleibt-abgewiesen.md) (Entscheidung 1: `KeyInfo` nicht gebaut, Folge-Slice mit Folge-ADR), [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) (Entscheidung 7 und Offen 5: Schlüssel als Metadatum zum Client)

**Berührte Spec-Stellen:** [SPEC-036](../../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) (neue Form für `KeyInfo`; Liste „Nicht erkannt“) · [SPEC-035](../../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) (Schlüssel der Tabellenbeschreibung) · [SPEC-031](../../../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität) (Befehlskennung der Abschlüsse, SESSIONSTATE) · [SPEC-045](../../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig) (A-8 entfällt) · [Kompatibilitätsmatrix](../../kompatibilitaetsmatrix.md) Abschnitt 5 Nr. 11 und die Zeile `KeyInfo`.

**Verantwortlich:** —

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-22.

---

## 1. Ziel und Abgrenzung

**Ziel:** Der Server beantwortet `ExecuteReader(KeyInfo)` mit `GetSchemaTable()` wie die Referenz: der Live-Schritt `schema-person-schluessel` ist gleich der Referenz, seine Regel und damit die Ausnahme A-8 entfallen.

**Ist-Stand** (gelesen am 2026-11-22; was nicht als gemessen steht, ist aus Datei oder Code gelesen):

- **Texte von `KeyInfo` sind nicht erkannt:** ` SET FMTONLY OFF; SET NO_BROWSETABLE ON;<Text> SET NO_BROWSETABLE OFF;` und, mit `SchemaOnly`, ` SET FMTONLY OFF; SET NO_BROWSETABLE ON; SET FMTONLY ON;<Text> SET FMTONLY OFF; SET NO_BROWSETABLE OFF;` enden mit `TDS_UNSUPPORTED_SQL`; sie sind **nicht gebaut**, nicht ausgeschlossen und nicht ungemessen ([ADR-0059](../../adr/0059-schemaonly-ist-eine-erkannte-form-keyinfo-bleibt-abgewiesen.md) Entscheidung 1). `SchemaOnly` (Form F) ist Gegenstand von [slice-simulation-schemaonly-keyinfo](../in-progress/slice-simulation-schemaonly-keyinfo.md).
- **Antwortform am Draht** (gemessen am 2026-11-22, Messpunkte W-30 bis W-35 im [Messprotokoll](../../../messungen/referenzabgleich.md), Ablage `referenztest-dotnet/Mitschnitt/messung/2022/` und `2025/`, Dateien `keyinfo-spalten.messung`, `keyinfo-sonderfaelle.messung`, `schemaonly-und-keyinfo.messung`; je ein Lauf je Instanz, `Microsoft.Data.SqlClient` 7.1.1, beide Instanzen gleich):
  - Antwort `DONE DONE COLMETADATA TABNAME COLINFO ROW+ DONE DONE`; die Auswahl läuft und liefert ihre Zeilen (W-30, W-31).
  - TABNAME: ein Eintrag, der Tabellenname mit einem Namensteil, bei `dbo.person` zwei Teile; COLINFO: **drei Byte je Spalte** (Spaltennummer ab 1, Tabellennummer, Status); Status `0x08` an der Schlüsselspalte, `0x00` sonst, `0x28` bei Alias (mit dem Spaltennamen der Tabelle dahinter); die **versteckte Schlüsselspalte** (nicht ausgewählt) kommt als zusätzliche letzte Spalte in COLMETADATA und Zeilen mit Status `0x18`; `0x10` trat nur zusammen mit `0x08` auf, das Bit `0x04` in keiner Messung (W-32).
  - Tabelle ohne Schlüssel: TABNAME und COLINFO stehen, alle Status `0x00`; Ausdruck ohne Tabelle: weder TABNAME noch COLINFO (W-32, W-33). Mit Parameter ein RPC mit `DONEINPROC` (W-33).
  - **Persistenz:** nach einem Fehler (unbekannte Tabelle) folgt SESSIONSTATE, und `NO_BROWSETABLE` bleibt in der Sitzung gesetzt: die folgenden Abfragen tragen TABNAME und COLINFO (W-34); bei `SchemaOnly` bleibt `FMTONLY` nicht in der Sitzung (W-29). `SchemaOnly|KeyInfo` zusammen: Zähler 0, TABNAME und COLINFO ohne Zeile (W-35).
- **Nicht vorhanden:** der Encoder kennt weder TABNAME noch COLINFO (`grep` nach `COLINFO`, `TABNAME` und `BROWSE` in `src/main` findet nichts, 2026-11-22); `StatementKind` kennt keine Art für `SET NO_BROWSETABLE`; die Tabellenbeschreibung des Simulations-Backends kennt den Schlüssel ([SPEC-035](../../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends)), meldet ihn aber nicht an den Client ([ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) Entscheidung 7: „Schlüssel nur beschreibend“).
- **Nutzerentscheidung offen:** ob der Server die Persistenz von `NO_BROWSETABLE` nach einem Fehler nachbildet (W-34: Zustand der Sitzung samt SESSIONSTATE), ja oder nein. Ohne Entscheidung des Nutzers baut der Slice sie nicht und führt die Abweichung als benannte Grenze.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- `ExecuteReader(SchemaOnly)` und `SET FMTONLY` — [slice-simulation-schemaonly-keyinfo](../in-progress/slice-simulation-schemaonly-keyinfo.md) ([ADR-0059](../../adr/0059-schemaonly-ist-eine-erkannte-form-keyinfo-bleibt-abgewiesen.md)).
- `GetSchemaTable()` für andere Clients als `Microsoft.Data.SqlClient` 7.1.1 — nicht gemessen, kein Träger.
- Identität, berechnete Spalten, Eindeutigkeitsindizes (`IsUnique` blieb in allen Messungen `False`), Verbunde und Sichten, `StoredProcedure` — nicht gemessen.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit.

- [ ] **Folge-ADR (Entscheidung):** eine ADR zu [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) Entscheidung 7 und Offen 5 (Schlüssel als Metadatum zum Client; Form der Erkennung von `KeyInfo`; Benennung der Arten für `SET NO_BROWSETABLE`; Persistenz nach einem Fehler: nachbilden ja oder nein, Entscheidung des Nutzers), vom Architect geschrieben, vom Nutzer angenommen; sie entscheidet auch, ob die Benennung der Arten aus [ADR-0059](../../adr/0059-schemaonly-ist-eine-erkannte-form-keyinfo-bleibt-abgewiesen.md) Entscheidung 7 geändert wird.
- [ ] **Bau:** TABNAME und COLINFO im Encoder, Status `0x08`, `0x00`, `0x18` (versteckte letzte Spalte), `0x28` (Alias), Erkennung von `KeyInfo` und `SchemaOnly|KeyInfo`, Antwort mit der Auswahl (Zeilen) und den Abschlüssen der `SET`, nach der Entscheidung der ADR die Persistenz von `NO_BROWSETABLE`; Tests mit Handvektor aus der Ablage der Messung (W-30 bis W-35: Happy, Tabelle ohne Schlüssel, zusammengesetzter Schlüssel, Schlüsselspalte nicht ausgewählt, Alias, Ausdruck ohne Tabelle, RPC, unbekannte Tabelle); die Regel `schema-person-schluessel` entfällt.
- [ ] **Nachzug:** [SPEC-036](../../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig), [SPEC-035](../../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends), [SPEC-031](../../../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität), [SPEC-045](../../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig), [Kompatibilitätsmatrix](../../kompatibilitaetsmatrix.md) (Nr. 11, Zeile `KeyInfo`), Messprotokoll (A-8 entfällt), `docs/user/`, der Absatz „Offen: die Behandlung der `SET`-Anweisungen …“ der Spezifikation (`KeyInfo` beantwortet).
- [ ] `make gates` Exit 0, dazu je Art der Änderung nach `harness/README.md` §Minimal agent workflow (`src/` oder `testserver/`: engste Testklasse und Mutation je Zusage; Verifier fährt `make clean-build` und `make e2e` je einmal auf dem End-Stand; Doku/Spec: `make docs-check`, `make flaeche`, `make kopplung-zahlen`; Tests: `make spec-abdeckung` einmal). Die Läufe `make vergleichslauf-d` sind Messung und Nachweis, keine Gates ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)).
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor (`.harness/skills/reviewer.md`), kein Self-Review.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben: `BEO-TDS/schemaonly-keyinfo-fmtonly-ohne-traeger` (Ausgang nachführen); keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — Pfad-Kandidaten für §8; geprüft ist das Vorhandensein der Dateien am 2026-11-22, nicht ihre Eignung; der Umfang folgt der Folge-ADR.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/token/Token.kt` | update | neue Tokens TABNAME und COLINFO im Encoder; Befehlskennung der Abschlüsse |
| `src/main/kotlin/io/github/pt9912/tdswire/domain/StatementKind.kt`, `protocol/response/Completion.kt` | update, falls die ADR neue Arten entscheidet | Arten für `SET NO_BROWSETABLE` |
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/StatementParser.kt`, `StatementPlanner.kt`, `FixedForms.kt`, `FixedAnswers.kt`, `SimulationBackend.kt` | update | Erkennung der Texte von `KeyInfo`, Antwort aus Tabellenbeschreibung samt Schlüssel |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/session/` | update, falls Persistenz nachgebildet wird | Zustand `NO_BROWSETABLE` je Sitzung, SESSIONSTATE |
| `src/test/kotlin/io/github/pt9912/tdswire/simulation/`, `src/test/kotlin/io/github/pt9912/tdswire/protocol/` | update | Tests mit Handvektor der Messung (W-30 bis W-35) |
| `referenztest-dotnet/Vergleich/live-ausnahmen.tsv`, `referenztest-dotnet/Vergleich/LiveSzenarioTests.cs` | update | die Regel `schema-person-schluessel` entfällt |
| `spec/spezifikation.md`, `docs/plan/kompatibilitaetsmatrix.md`, `docs/messungen/referenzabgleich.md`, `docs/user/` | update | Nachzug nach DoD |
| `docs/plan/adr/` | neu (Architect) | Folge-ADR zu [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) Entscheidung 7 und Offen 5; Eintrag im ADR-Index |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf (Testdateien mit Kennung der Akzeptanzkriterien).

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`open` → `next` → `in-progress`): Nutzerzeichen und ein Verantwortlicher (`Verantwortlich:` ist bis dahin `—`); [slice-simulation-schemaonly-keyinfo](../in-progress/slice-simulation-schemaonly-keyinfo.md) liegt in `done/` (Reihenfolge, nicht Bedingung für die Folge-ADR); die Entscheidung des Nutzers zur Persistenz von `NO_BROWSETABLE` ist ausstehend und gehört zur Folge-ADR. **Nicht starten**, bevor der Nutzer es beauftragt.

**Rückführungen — vorab benennen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2 oder die Persistenz mit SESSIONSTATE verlangt einen eigenen Slice im Protokollkern (Zerlegung in Browse-Tokens und Sitzungszustand).
- `in-progress` → `open` (blockiert): die Folge-ADR ist nicht angenommen.

## 5. Closure-Trigger

Alle Punkte der DoD in §2 erledigt, `make gates` und `make docs-check` Exit 0, die Regel `schema-person-schluessel` der Ausnahmeliste und mit ihr A-8 sind entfallen (nicht still stehen gelassen), und die Closure-Notiz mit Steering-Loop-Lerneintrag ist geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang.

- Die Persistenz von `NO_BROWSETABLE` nach einem Fehler (W-34) ist ein Zustand der Sitzung samt SESSIONSTATE, den der Server bisher nicht führt; Umfang unbekannt. — **Ausgang:** offen bis zur Closure; die Entscheidung des Nutzers (nachbilden ja/nein) steht in der Folge-ADR.
- `GetSchemaTable()` ist für einen Client gemessen; andere Clients stellen andere Texte. — **Ausgang:** offen bis zur Closure; die Aussage trägt für `Microsoft.Data.SqlClient` 7.1.1 ([AGENTS.md](../../../../AGENTS.md) §3.8; `BEO-TDS/festlegung-gilt-nur-fuer-das-clientprofil`).
- Der Status `0x10` und das Bit `0x04` sind nur teilweise belegt (`0x10` nur mit `0x08`, `0x04` nie). — **Ausgang:** offen bis zur Closure; der Bau setzt nur die gemessenen Statuswerte (`0x00`, `0x08`, `0x18`, `0x28`).
- Größe: drei Liefer-Punkte. — **Ausgang:** offen bis zur Closure; Rückführung nach §4.

## 7. Closure-Notiz

Wird bei der Closure gefüllt.

**Herkunft:** Rest der Lücke A-8 der Ausnahmeliste, [Messprotokoll des Referenzabgleichs](../../../messungen/referenzabgleich.md) (W-30 bis W-35); [ADR-0059](../../adr/0059-schemaonly-ist-eine-erkannte-form-keyinfo-bleibt-abgewiesen.md) Entscheidung 1 und Folgepflicht (Träger der Lücke `KeyInfo`); Register `BEO-TDS/schemaonly-keyinfo-fmtonly-ohne-traeger`; Nutzerentscheidung 2026-11-22: `KeyInfo` nicht in `slice-simulation-schemaonly-keyinfo`, eigener Folge-Slice.

## 8. Sub-Area-Prüfungen und Modus-Begründung

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register (`../observations/BEO-TDS/`) gelesen; Treffer: `schemaonly-keyinfo-fmtonly-ohne-traeger` (offen), `festlegung-gilt-nur-fuer-das-clientprofil`, `messung-am-echten-sql-server-fehlt`. Zähler-Stände stehen in den Dateien des Registers, nicht hier.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
