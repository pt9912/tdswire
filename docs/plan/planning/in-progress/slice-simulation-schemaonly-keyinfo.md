# Slice slice-simulation-schemaonly-keyinfo: Simulations-Backend: `SchemaOnly` beantworten (Form F, `SET FMTONLY`); `KeyInfo` bleibt abgewiesen (A-8)

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** ohne Welle (keine Closure-Bedingung außerhalb der DoD dieses Slice; Träger der Lücke A-8 der Ausnahmeliste des Referenzabgleichs, angelegt nach Nutzerentscheidung vom 2026-11-17: „ja slices anlegen“ auf die Frage der Closure von [welle-referenzabgleich-release](../done/welle-referenzabgleich-release.md)).

**Bezug:** [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) (Scope: Katalog und Spaltenbeschreibung aus mitgeteilten Tabellen), [TDS-FA-05](../../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0059](../../adr/0059-schemaonly-ist-eine-erkannte-form-keyinfo-bleibt-abgewiesen.md) (Form F; ersetzt [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) Entscheidung 14, erster Satz, teilweise), [ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) (Anweisungsart durch die Bibliothek)

**Berührte Spec-Stellen:** [SPEC-036](../../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) (neue Form F; Probe der Zieltabelle, Form B, bleibt; Liste „Nicht erkannt“) · [SPEC-031](../../../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität) (Befehlskennung der Abschlüsse: zwei Arten, im RPC wirksam) · [SPEC-059](../../../../spec/spezifikation.md#einstieg-des-integrators-vertrag-zu-spec-059) (Liste der öffentlichen Fläche: zwei Konstanten von `StatementKind`) · [SPEC-035](../../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) (Tabellenbeschreibung, gelesen) · [SPEC-045](../../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig) (A-8 verkleinert) · [Kompatibilitätsmatrix](../../kompatibilitaetsmatrix.md) Abschnitt 5 Nr. 11 und die Zeilen `SchemaOnly` und `KeyInfo` · der Absatz „Offen: die Behandlung der `SET`-Anweisungen …“ der Spezifikation (`SchemaOnly` beantwortet, `KeyInfo` bleibt offen mit [slice-simulation-keyinfo-erkennen](../open/slice-simulation-keyinfo-erkennen.md) als Träger).

**Verantwortlich:** pt9912

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-17.

---

## 1. Ziel und Abgrenzung

**Ziel:** Der Server beantwortet `ExecuteReader(SchemaOnly)` mit `GetSchemaTable()` wie die Referenz (Form F, [ADR-0059](../../adr/0059-schemaonly-ist-eine-erkannte-form-keyinfo-bleibt-abgewiesen.md)): die Schritte `schema-person` und `schema-typen` des Live-Laufs sind gleich der Referenz, ihre Regeln entfallen; `ExecuteReader(KeyInfo)` bleibt `TDS_UNSUPPORTED_SQL`, die Ausnahme A-8 gilt nur noch für `schema-person-schluessel`.

**Ist-Stand** (gelesen am 2026-11-22; was nicht als gemessen steht, ist aus Datei oder Code gelesen):

- **Lücke A-8** ([Messprotokoll des Referenzabgleichs](../../../messungen/referenzabgleich.md)): `SchemaOnly` sendet `SET FMTONLY`, `KeyInfo` sendet `SET NO_BROWSETABLE`; der Server erkennt beide `SET`-Texte nicht und antwortet `Number 50001`, „Unsupported SQL.“ ([SPEC-036](../../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig), [Kompatibilitätsmatrix](../../kompatibilitaetsmatrix.md) Nr. 11). Drei Regeln in `live-ausnahmen.tsv` (`schema-person`, `schema-typen`, `schema-person-schluessel`); keine Regel der Mitschnitt-Liste (die 17 Mitschnitt-Szenarien enthalten keinen Schema-Schritt).
- **Antwortform am Draht** (gemessen am 2026-11-22; je ein Lauf je Instanz 2022 und 2025, `Microsoft.Data.SqlClient` 7.1.1; Werte, Rohzeilen und Grenzen im [Messprotokoll](../../../messungen/referenzabgleich.md), Abschnitt „`SchemaOnly`, `KeyInfo` und `GetSchemaTable()` am Draht“, W-26 bis W-36; Ablage `referenztest-dotnet/Mitschnitt/messung/2022/` und `2025/`): Text von `SchemaOnly` ` SET FMTONLY OFF; SET FMTONLY ON;<Text> SET FMTONLY OFF;`, Antwort `DONE DONE COLMETADATA DONE DONE` mit Zähler 0 und ohne Zeile (W-26, W-27), im RPC ohne das schließende `SET` (W-28), bei unbekannter Tabelle `DONE DONE ERROR DONE` ohne SESSIONSTATE (W-29). `GetSchemaTable()` löst keine weitere Anfrage aus (W-36). Die Messung von `KeyInfo` (W-30 bis W-35) liegt vor und gehört dem Folge-Slice.
- **Ist am Server:** die Probe der Zieltabelle (Form B, `BulkForms.probe`) erkennt `SET FMTONLY ON <Auswahl> [SET FMTONLY OFF]` ohne Abschlüsse der `SET`-Anweisungen; der Text von `SchemaOnly` beginnt mit `SET FMTONLY OFF;` und ist keine erkannte Form. `StatementKind` kennt keine Art für `SET FMTONLY`; `IMPLICIT_TRANSACTIONS_ON` und `IMPLICIT_TRANSACTIONS_OFF` tragen dieselben Befehlskennungen (`0x00b9`, `0x00ba`), ändern aber den Zustand der Verbindung und taugen nicht ([ADR-0059](../../adr/0059-schemaonly-ist-eine-erkannte-form-keyinfo-bleibt-abgewiesen.md) Kontext).
- **Vorbedingung A-11 erfüllt:** die Länge von `DECIMALN` im COLMETADATA ist 17 ([slice-decimaln-laenge-an-messung-angleichen](../done/slice-decimaln-laenge-an-messung-angleichen.md)); die Spaltengröße von `decimal` in der Antwort auf `SchemaOnly` ist damit nicht durch A-11 verschieden von der Referenz (Folgerung aus den Zahlen der Ausnahmeliste, nicht gemessen).

**Entscheidungen des Nutzers (beantwortet, 2026-11-22; Beleg [ADR-0059](../../adr/0059-schemaonly-ist-eine-erkannte-form-keyinfo-bleibt-abgewiesen.md) Entscheidung 1 und Geschichte):**

| Frage | Antwort | Folge für diesen Slice |
|---|---|---|
| `SchemaOnly`: Code angleichen oder Zielbild anpassen | Code angleichen | Bau von Form F (DoD Liefer-Punkt 2); das Zielbild ist die Spezifikation |
| `KeyInfo` | nicht in diesem Slice; eigener Folge-Slice [slice-simulation-keyinfo-erkennen](../open/slice-simulation-keyinfo-erkennen.md) | bleibt `TDS_UNSUPPORTED_SQL` („nicht gebaut“); A-8 bleibt für `schema-person-schluessel` |
| fünf Fragen zur Annahme der ADR (zwei Konstanten von `StatementKind`; beide Abschlüsse vor dem Fehler; alle erkannten Bedingungen von S1; beide Arten im RPC; schließendes `SET` optional) | alle ja | die Form F des Baus; die Zulassung aller Bedingungen von S1 ist Setzung (ungemessen) |

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- `ExecuteReader(KeyInfo)` und `SchemaOnly|KeyInfo`: TABNAME, COLINFO, die versteckte Schlüsselspalte, die Persistenz von `SET NO_BROWSETABLE` (W-34), die Entscheidung zu [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) Entscheidung 7 und Offen 5 — Nutzerentscheidung 2026-11-22; Träger [slice-simulation-keyinfo-erkennen](../open/slice-simulation-keyinfo-erkennen.md). Die Texte mit `SET NO_BROWSETABLE` bleiben als Ganzes nicht erkannt.
- Die Länge von `DECIMALN` in COLMETADATA (A-11) — [slice-decimaln-laenge-an-messung-angleichen](../done/slice-decimaln-laenge-an-messung-angleichen.md); aufgelöst.
- `GetSchemaTable()` für andere Clients als `Microsoft.Data.SqlClient` 7.1.1 (mssql-jdbc, ODBC) — nicht gemessen, kein Träger; d-migrate stellt diese Texte bei `schema` nicht (`docs/messungen/katalog-und-ddl-zustand-2026-11-06.md`), `FMTONLY` stellt der Import (Probe von `SqlBulkCopy`, Form B, bleibt unverändert).
- `SchemaOnly` für `SELECT <Ganzzahl>`, Form S2, Änderungen, `WAITFOR`, `StoredProcedure`, Verbunde, Aliase — nicht gemessen und in Form F nicht erkannt ([ADR-0059](../../adr/0059-schemaonly-ist-eine-erkannte-form-keyinfo-bleibt-abgewiesen.md) Entscheidung 2); sie enden mit `TDS_UNSUPPORTED_SQL`.
- Neue Katalogantworten für Systemkataloge — Out-of-Scope von [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit. Die DoD hat drei Liefer-Punkte (Messung, Bau, Nachzug).

- [x] **Liefer-Punkt 1, Messung am Draht (erledigt):** Text und Antwort von `SchemaOnly`, `KeyInfo` und `GetSchemaTable()` an beiden Instanzen, Messpunkte W-26 bis W-36 im Messprotokoll, Ablage unter `referenztest-dotnet/Mitschnitt/messung/{2022,2025}/`, Belege `MessungSchemaBelegTests`, Mutationsbeleg `docs/messungen/mutationen-schemaonly-keyinfo-messphase-2026-11-22.md`, Review der Messphase `docs/reviews/2026-11-22-slice-simulation-schemaonly-keyinfo-messung-review.md`.
- [ ] **Liefer-Punkt 2, Bau von Form F** ([ADR-0059](../../adr/0059-schemaonly-ist-eine-erkannte-form-keyinfo-bleibt-abgewiesen.md) Entscheidungen 2 bis 7):
  - Erkennung `SET FMTONLY OFF; SET FMTONLY ON; <S1-Auswahl> [SET FMTONLY OFF]` (Auswahl der Form S1 aus einer Tabelle, alle erkannten Bedingungen von S1); zwei neue Konstanten `StatementKind.SET_FMTONLY_ON` und `SET_FMTONLY_OFF` (Kennungen `0x00b9`, `0x00ba`, ohne `COUNT`), auch im RPC.
  - Antwort wie W-26 bis W-29: je `SET` ein `UpdateCount(0)`-Abschluss, COLMETADATA ohne Zeile, `DONE` mit Zähler 0 und `0x00c1`; im RPC mit `DONEINPROC`; unbekannte Tabelle: beide Abschlüsse vor ERROR 208, ohne SESSIONSTATE; die Zeilenquelle wird nicht geöffnet.
  - Tests mit Handvektoren aus der Ablage der Messung (`schemaonly-spalten.messung`, Szenario `schemaonly-spalten/01`; `schemaonly-sonderfaelle.messung`, Szenarien `schemaonly-sonderfaelle/03`, `/04`, `/05`), Happy, Tabelle ohne Schlüssel, unbekannte Tabelle, RPC; Negativtests mit `TDS_UNSUPPORTED_SQL` ohne Abschluss: die Texte von `KeyInfo` (`SET NO_BROWSETABLE`) und `SchemaOnly|KeyInfo`, `SELECT <Ganzzahl>`, Form S2, Änderungen und `WAITFOR` hinter den `SET`; die Probe (Form B) bleibt mit allen bisherigen Tests grün.
  - Live-Lauf: die Regeln `schema-person` und `schema-typen` aus `live-ausnahmen.tsv` entfallen; `schema-person-schluessel` bleibt mit dem Grund „`KeyInfo` nicht gebaut“, die Art der Regel ist *Lücke* (kein *so gewollt*: das Zielbild bleibt die Antwort der Referenz), der Grund nennt `slice-simulation-keyinfo-erkennen` als Träger.
- [ ] **Liefer-Punkt 3, Nachzug:** [SPEC-036](../../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) (Zeile F, Abgrenzung zur Probe, Liste „Nicht erkannt“), [SPEC-031](../../../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität) (zwei Arten), [SPEC-059](../../../../spec/spezifikation.md#einstieg-des-integrators-vertrag-zu-spec-059) (Liste der Konstanten), der Absatz „Offen: die Behandlung der `SET`-Anweisungen …“ der Spezifikation, [SPEC-045](../../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig) und Messprotokoll (A-8 nur `KeyInfo`, Grund „nicht gebaut“), [Kompatibilitätsmatrix](../../kompatibilitaetsmatrix.md) Nr. 11 und die Zeilen `SchemaOnly` und `KeyInfo`, die Aufzählung der erkannten Formen in `docs/user/` (`einbettung-und-betrieb.md`, `referenz-konfiguration.md`, `testserver-manuell.md`); Zahlen in Spec, Gradle und README gleich (`make kopplung-zahlen`).
- [ ] `make gates` Exit 0, dazu je Art der Änderung nach `harness/README.md` §Minimal agent workflow: engste Testklasse und Mutation je Zusage über enge Testklassen (`src/`), `make docs-check`, `make flaeche`, `make kopplung-zahlen` (Doku/Spec), `make spec-abdeckung` einmal (Tests oder `@spec`-Tags), `make matrix-zitate` (falls Zitate der Kompatibilitätsmatrix berührt sind); Verifier: `make clean-build` und `make e2e` je einmal auf dem End-Stand (Implementer und Reviewer nicht). Die Läufe `make vergleichslauf-d` sind Messung und Nachweis, keine Gates ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)).
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor (`.harness/skills/reviewer.md`), kein Self-Review.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben: `BEO-TDS/schemaonly-keyinfo-fmtonly-ohne-traeger` (Ausgang nachführen: `SchemaOnly` beantwortet, `KeyInfo` beim Folge-Slice); keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen; der Folge-Slice ist [slice-simulation-keyinfo-erkennen](../open/slice-simulation-keyinfo-erkennen.md).

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — Pfad-Kandidaten für §8; geprüft ist das Vorhandensein der Dateien am 2026-11-22, nicht ihre Eignung.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/domain/StatementKind.kt` | update | zwei Konstanten `SET_FMTONLY_ON`, `SET_FMTONLY_OFF` (Liefer-Punkt 2, [ADR-0059](../../adr/0059-schemaonly-ist-eine-erkannte-form-keyinfo-bleibt-abgewiesen.md) Entscheidung 7) |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/token/Token.kt` | update | `DoneCommand.forKind` (`0x00b9`, `0x00ba`) und `countsRows` für die zwei Arten |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/response/Completion.kt` | update | `Completion.of`: die zwei Arten gelten auch im RPC (`DONEINPROC`), alle übrigen zählen dort weiter wie `UNKNOWN` |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/response/ResponseSequencer.kt` | update (KDoc) | die Zusage zu den Arten und zum RPC steht im KDoc |
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/StatementParser.kt`, `BulkForms.kt`, `StatementPlanner.kt`, `FixedForms.kt`, `FixedAnswers.kt` | update | Erkennung der Form F (Tokenfolge, S1-Auswahl), Prüfung der Auswahl wie bei der Probe (`metadataOnly`), Antwort mit den Abschlüssen; die Probe (Form B) bleibt |
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/SimulationBackend.kt` | update, falls nötig | Ereignisfolge `UpdateCount`, `ResultStart`, `ResultEnd(0)`, Fehler nach den zwei Abschlüssen |
| `src/test/kotlin/io/github/pt9912/tdswire/simulation/SimulationBackendFixedTest.kt`, `StatementFormsTest.kt`, `StatementPlannerTest.kt`, `RecognitionSupport.kt`, `SimulationBackendBulkTest.kt` | update | Tests der Form F nach DoD Liefer-Punkt 2 (Happy, Grenzen, Negativ); `SimulationBackendBulkTest` hält die Probe unverändert |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/response/ResponseStatementKindTest.kt` | update | Handvektoren der Messung (Batch, RPC, Fehler), die zwei Arten ändern `SessionTransaction` nicht, übrige Arten im RPC unverändert |
| `src/integrator/kotlin/io/github/pt9912/tdswire/integrator/StatementKindEmbeddingTest.kt` | update | nennt `SET_FMTONLY_ON` und `SET_FMTONLY_OFF` |
| `referenztest-dotnet/Vergleich/LiveSzenarioTests.cs`, `referenztest-dotnet/Vergleich/live-ausnahmen.tsv` | update | die Regeln `schema-person` und `schema-typen` entfallen, `schema-person-schluessel` bleibt mit Grund „nicht gebaut“ |
| `spec/spezifikation.md` | update | SPEC-036 (Zeile F, Abgrenzung zur Probe, „Nicht erkannt“), SPEC-031 (Arten), SPEC-059 (Liste), SPEC-045, Absatz „Offen: die Behandlung der `SET`-Anweisungen …“ |
| `docs/plan/kompatibilitaetsmatrix.md` | update | Abschnitt 5 Nr. 11 und die Zeilen `SchemaOnly` und `KeyInfo` (Zeilen 238 und 239) |
| `docs/messungen/referenzabgleich.md` | update | Tabelle der Ausnahmen (A-8 nur `KeyInfo`, Grund „nicht gebaut“), Messprotokoll |
| `docs/user/einbettung-und-betrieb.md`, `docs/user/referenz-konfiguration.md`, `docs/user/testserver-manuell.md` | update | Aufzählung der erkannten Formen und der Antworten um Form F |
| `docs/plan/spec-abdeckung.md` | update (erzeugt) | `make spec-abdeckung` nach neuen Tests |

Bereits gelieferte Dateien der Messphase (Liefer-Punkt 1; Zustand der Dateien, nicht Chronik):

| Datei / Komponente | Art | Begründung |
|---|---|---|
| `referenztest-dotnet/Vergleich/MessungWireTests.cs` | update | sechs Messszenarien der Kategorie `MessungWire` für `SchemaOnly` und `KeyInfo` |
| `referenztest-dotnet/Mitschnitt/TokenLeser.cs`, `MessAuswertung.cs` | update | Leser zerlegt Spaltenflags, TABNAME und COLINFO; Option `mitSpaltenform` der Auswertung (Zeilen `anfragetext`, `spaltenform`) |
| `referenztest-dotnet/Mitschnitt/MessungSchemaBelegTests.cs`, `MessungEinheitTests.cs`, `MessungBelegTests.cs` | neu, update | Belege W-26 bis W-36 an der Ablage beider Instanzen, Einheitstests von Leser und Auswertung |
| `referenztest-dotnet/Mitschnitt/messung/2022/`, `referenztest-dotnet/Mitschnitt/messung/2025/` | neu | je sechs Messdateien (`schemaonly-*`, `keyinfo-*`, `schemaonly-und-keyinfo`, `getschematable-ohne-verhalten`) |
| `tools/mk/vergleichslauf-d.mk` | update | Kopfkommentar: Zahl der Messdateien je Stufe |
| `docs/messungen/mutationen-schemaonly-keyinfo-messphase-2026-11-22.md`, `docs/reviews/2026-11-22-slice-simulation-schemaonly-keyinfo-messung-review.md` | neu | Mutationsbeleg und Review der Messphase |
| `docs/plan/adr/0059-schemaonly-ist-eine-erkannte-form-keyinfo-bleibt-abgewiesen.md`, `docs/plan/adr/README.md` | neu, update | Entscheidung nach der Messung (`Accepted`), Eintrag im ADR-Index |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf (Testdateien mit Kennung der Akzeptanzkriterien).

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`open` → `next` → `in-progress`): erfüllt (Nutzerzeichen, Verantwortlicher pt9912, Antwort auf die Frage in §1 am 2026-11-22; der Slice liegt in `in-progress/`). Die Messung (Liefer-Punkt 1) ist ausgeführt; weitere Messläufe an den gepinnten Instanzen (EULA bestätigt 2026-11-01 und 2026-11-17) laufen **nicht parallel** zu Last-Messslices, und die Messsperre `/tmp/claude-1000/-Development-tdswire/messlock` gilt für sie.

**Rückführungen — vorab benennen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2 oder der Bau von Form F verlangt einen größeren Eingriff in den Protokollkern als die zwei Arten und die Ausnahme im RPC (die Zerlegung in `SchemaOnly` und `KeyInfo` ist mit [ADR-0059](../../adr/0059-schemaonly-ist-eine-erkannte-form-keyinfo-bleibt-abgewiesen.md) Entscheidung 1 vollzogen; `KeyInfo` liegt im Folge-Slice).
- `in-progress` → `open` (blockiert): die gepinnten Referenzinstanzen sind nicht beziehbar, falls eine weitere Messung nötig wird, oder der Nutzer ändert die Entscheidung zur öffentlichen Fläche (Rückfall C der ADR: Draht ohne die Abschlüsse der `SET`).

## 5. Closure-Trigger

Alle Punkte der DoD in §2 erledigt, `make gates` und `make docs-check` Exit 0, die Regeln der Ausnahmeliste, die dieser Slice trägt, sind entfallen (`schema-person`, `schema-typen`) oder in ihrem Grund auf „nicht gebaut“ mit dem Folge-Slice als Träger geändert (`schema-person-schluessel`), nicht still stehen gelassen, und die Closure-Notiz mit Steering-Loop-Lerneintrag ist geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang.

- Die Antwort auf `KeyInfo` verlangt Tokens, die der Encoder nicht kennt (TABNAME, COLINFO). — **Ausgang:** eingetreten als Zerlegung: `KeyInfo` ist nicht Teil dieses Slice (Nutzerentscheidung 2026-11-22), Träger [slice-simulation-keyinfo-erkennen](../open/slice-simulation-keyinfo-erkennen.md).
- `GetSchemaTable()` ist für einen Client gemessen; andere Clients stellen andere Texte. — **Ausgang:** offen bis zur Closure; die Aussage trägt für `Microsoft.Data.SqlClient` 7.1.1 ([AGENTS.md](../../../../AGENTS.md) §3.8; `BEO-TDS/festlegung-gilt-nur-fuer-das-clientprofil`).
- A-11 war Vorbedingung für den Gleichlauf von `schema-person` und `schema-typen` für `decimal`; sie ist aufgelöst. — **Ausgang:** entfallen (A-11 ist mit slice-decimaln-laenge-an-messung-angleichen gebaut).
- Die Form F nimmt Bedingungen von S1 an (auch `OR`-Folgen und Parameter), die für `SchemaOnly` nicht gemessen sind (Setzung des Nutzers, [ADR-0059](../../adr/0059-schemaonly-ist-eine-erkannte-form-keyinfo-bleibt-abgewiesen.md) Entscheidung 2). — **Ausgang:** offen bis zur Closure; die Tests nennen sie als Setzung, nicht als Messung ([AGENTS.md](../../../../AGENTS.md) §3.8).
- Die neue Erkennung kann Texte annehmen, die der Server bisher abwies, und die Probe (Form B) berühren. — **Ausgang:** offen bis zur Closure; Negativtests für alle nicht erkannten Folgen und die unveränderte Probe bleiben.
- Größe: drei Liefer-Punkte, davon einer erledigt. — **Ausgang:** offen bis zur Closure; Rückführung nach §4.

## 7. Closure-Notiz

Wird bei der Closure gefüllt.

**Herkunft:** Lücke A-8 der Ausnahmeliste, [Messprotokoll des Referenzabgleichs](../../../messungen/referenzabgleich.md); Register `BEO-TDS/schemaonly-keyinfo-fmtonly-ohne-traeger`; [Kompatibilitätsmatrix](../../kompatibilitaetsmatrix.md) Nr. 11; [Results-Notiz von welle-referenzabgleich-release](../done/welle-referenzabgleich-release-results.md); [ADR-0059](../../adr/0059-schemaonly-ist-eine-erkannte-form-keyinfo-bleibt-abgewiesen.md). Nutzerentscheidung 2026-11-17: „ja slices anlegen“ (Frage der Closure von `welle-referenzabgleich-release`, Punkt: die 14 Lücken-Nummern ohne Träger-Slice); Nutzerentscheidung 2026-11-22: nur `SchemaOnly` bauen, `KeyInfo` im Folge-Slice.

## 8. Sub-Area-Prüfungen und Modus-Begründung

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register (`../observations/BEO-TDS/`) gegen die Verzeichnisliste gelesen; Treffer: `schemaonly-keyinfo-fmtonly-ohne-traeger` (offen), `festlegung-gilt-nur-fuer-das-clientprofil`, `messung-am-echten-sql-server-fehlt`. Zähler-Stände stehen in den Dateien des Registers, nicht hier.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
