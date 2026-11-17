# Slice slice-simulation-schemaonly-keyinfo: Simulations-Backend: `SchemaOnly` und `KeyInfo` (`SET FMTONLY`, `SET NO_BROWSETABLE`) beantworten (A-8)

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** ohne Welle (keine Closure-Bedingung außerhalb der DoD dieses Slice; Träger der Lücke A-8 der Ausnahmeliste des Referenzabgleichs, angelegt nach Nutzerentscheidung vom 2026-11-17: „ja slices anlegen“ auf die Frage der Closure von [welle-referenzabgleich-release](../done/welle-referenzabgleich-release.md)).

**Bezug:** [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) (Scope: Katalog und Spaltenbeschreibung aus mitgeteilten Tabellen), [TDS-FA-05](../../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) (Offen: `SET`-Behandlung), [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) (Entscheidung 7: Schlüssel als Metadatum zum Client, Folgeschritt nach Messung von `KeyInfo` und `SchemaOnly`)

**Berührte Spec-Stellen:** [SPEC-036](../../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) (Form C: erkannte `SET`-Anweisungen; Form B: Probe der Zieltabelle) · [SPEC-035](../../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) (Tabellenbeschreibung) · [SPEC-045](../../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig) · [Kompatibilitätsmatrix](../../kompatibilitaetsmatrix.md) Abschnitt 5 Nr. 11.

**Verantwortlich:** —

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-17.

---

## 1. Ziel und Abgrenzung

**Ziel:** Der Server beantwortet `ExecuteReader(SchemaOnly)` und `ExecuteReader(KeyInfo)` mit `GetSchemaTable()` so, dass der Live-Lauf für die drei Schritte `schema-person`, `schema-typen`, `schema-person-schluessel` gleich der Referenz ausfällt, oder A-8 gilt nach Entscheidung des Nutzers als *so gewollt*.

**Ist-Stand** (gelesen am 2026-11-17; was nicht als gemessen steht, ist aus Datei oder Code gelesen):

- **Lücke A-8** ([Messprotokoll des Referenzabgleichs](../../../messungen/referenzabgleich.md)): `SchemaOnly` sendet `SET FMTONLY`, `KeyInfo` sendet `SET NO_BROWSETABLE`; der Server erkennt beide `SET`-Texte nicht und antwortet `Number 50001`, „Unsupported SQL.“ ([SPEC-036](../../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig), [Kompatibilitätsmatrix](../../kompatibilitaetsmatrix.md) Nr. 11: „die `SET`-Texte deutet der Server nicht, sie gehen unverändert ans Backend“). Die Referenz liefert die Spalten mit Typ, Größe, Nullbarkeit und bei `KeyInfo` Schlüssel und Tabelle. Drei Regeln in `live-ausnahmen.tsv`; keine Regel der Mitschnitt-Liste (die 17 Mitschnitt-Szenarien enthalten keinen Schema-Schritt).
- **Bereits verstanden:** `SET FMTONLY ON` als Teil der Probe der Zieltabelle von `SqlBulkCopy` (`sp_executesql N'SET FMTONLY ON SELECT * FROM …'`, Form B; Messprotokoll `docs/messungen/bulk-load-2026-11-07.md`, Zeile M12; `BulkCopyProbe.kt`). Diese Erkennung ist an die Probe gebunden, nicht an die Texte von `SchemaOnly`.
- **Nicht vorhanden:** der Encoder kennt keine Tokens für Browse-Informationen (Schlüssel, Tabellenname je Spalte); `grep` nach `COLINFO`, `TABNAME` und `BROWSE` in `src/main` findet nichts (2026-11-17). Ob `KeyInfo` solche Tokens verlangt, ist aus dem Messprotokoll nicht ablesbar und am Draht zu messen; die Live-Zeilen der Referenz nennen `Schluessel` und `Tabelle`, nicht die Tokens.
- **Vorbedingung A-11:** die Zeile `schema-person` der Referenz nennt `ColumnSize` 17 für `decimal(10,2)`, dieselbe Zahl wie die Länge in A-11 (dort Referenz 17, Server 9). Solange A-11 offen ist, bliebe auch eine gebaute Antwort auf `SchemaOnly` für `decimal` verschieden von der Referenz ([slice-decimaln-laenge-an-messung-angleichen](../open/slice-decimaln-laenge-an-messung-angleichen.md)). Das ist eine Folgerung aus den Zahlen der Ausnahmeliste, nicht gemessen.
- **Träger-Vorschlag im Register** (Setzung des Planners, offen): [slice-katalog-und-ddl-zustand](../done/slice-katalog-und-ddl-zustand.md) wäre der Ort gewesen, nennt `SchemaOnly`, `KeyInfo` und `FMTONLY` aber nicht und ist geschlossen; deshalb ein eigener Slice.

**Offene Frage an den Nutzer — „Code angleichen“ oder „Zielbild anpassen“.** Die Spezifikation ist das Zielbild, eine Abweichung des Servers ist Schuld; ob sie hier durch Bau behoben wird oder ob Spezifikation und Ausnahmeliste dem Ist-Stand folgen, entscheidet der Nutzer, nicht der Planner. Der Slice startet nicht, bevor die Frage beantwortet ist.

- **Option A — Code angleichen:** der Server erkennt `SET FMTONLY ON|OFF` und `SET NO_BROWSETABLE ON|OFF` als Teil der Texte von `SchemaOnly` und `KeyInfo` und beantwortet sie aus der Beschreibung der Tabelle (Spalten, Typen, Nullbarkeit, Schlüssel; bei `KeyInfo` mit den Browse-Angaben, soweit die Messung sie verlangt). [SPEC-036](../../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) wird erweitert (Form C oder neue Form), [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) Entscheidung 7 („Schlüssel als Metadatum“) ist die Entscheidung, die dafür aufzumachen ist (Folge-ADR, Setzung des Planners).
- **Option B — Zielbild anpassen:** A-8 wird *so gewollt* mit der Begründung „die Katalogabfragen und `SELECT … WHERE 1 = 0` genügen den gelisteten Clients; `SchemaOnly` und `KeyInfo` sind für das Simulations-Backend nicht Teil des Profils“. Das Lastenheft ([TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend)) nennt „Spaltenbeschreibung“ über den Vergleich zweier Ganzzahlkonstanten als Weg („wie ihn Clientwerkzeuge zum Lesen der Spaltenbeschreibung ohne Zeilen senden“); ob `SchemaOnly` dazugehört, entscheidet der Nutzer.
- **Empfehlung des Planners (Setzung, keine Entscheidung):** Messen vor Bauen. Option A für `SchemaOnly` (Spalten ohne Schlüssel), weil der Weg (`FMTONLY` und Spaltenbeschreibung) im Lastenheft schon steht; `KeyInfo` erst nach der Messung der Antwort am Draht, weil die Antwortform unbekannt ist (siehe Ist-Stand). Hängt an [slice-decimaln-laenge-an-messung-angleichen](../open/slice-decimaln-laenge-an-messung-angleichen.md) (A-11).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Die Länge von `DECIMALN` in COLMETADATA (A-11) — [slice-decimaln-laenge-an-messung-angleichen](../open/slice-decimaln-laenge-an-messung-angleichen.md); eigene Ursache, eigener Messauftrag, Vorbedingung für den Gleichlauf von `schema-person` und `schema-typen`.
- `GetSchemaTable()` für andere Clients als `Microsoft.Data.SqlClient` 7.1.1 (mssql-jdbc, ODBC) — nicht gemessen, kein Träger; d-migrate stellt diese Texte bei `schema` nicht (`docs/messungen/katalog-und-ddl-zustand-2026-11-06.md`), `FMTONLY` stellt der Import (Probe von `SqlBulkCopy`).
- Neue Katalogantworten für Systemkataloge — Out-of-Scope von [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) (soweit sie der Referenzclient nach Clientprofil nicht selbst abfragt).
- Die Einrichtung der Referenzinstanz für Schema-Szenarien im Mitschnitt-Lauf — eine neue Aufnahme ist nur nötig, wenn die Messung am Draht (DoD 1) sie verlangt; sie ist dann Teil dieses Slice, kein eigener.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit.

- [ ] **Messung am Draht:** Text und Antwort von `SchemaOnly`, `KeyInfo` und `GetSchemaTable()` an beiden Instanzen (2022, 2025) mit dem Referenzclient, als Szenarien der Kategorie `MessungWire` (oder neue Mitschnitt-Szenarien), Einzelmessung je Instanz mit Messzeile im Messprotokoll; ein Fall, der nicht herstellbar ist, steht als „nicht lieferbar“ mit Begründung.
- [ ] **Bau (nur nach Entscheidung des Nutzers, Option A):** der Server erkennt die `SET`-Texte und liefert die gemessene Antwort aus der Tabellenbeschreibung; Tests mit Handvektor aus der Messung (Happy, Tabelle ohne Schlüssel, unbekannte Tabelle); die drei Live-Regeln entfallen.
- [ ] **Nachzug:** [SPEC-036](../../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig), [Kompatibilitätsmatrix](../../kompatibilitaetsmatrix.md) Nr. 11 und das Messprotokoll; bei Option B die Art der drei Regeln mit Begründung.
- [ ] `make gates` Exit 0, dazu je Art der Änderung nach `harness/README.md` §Minimal agent workflow: engste Testklasse und Mutation je Zusage über enge Testklassen (`src/`), `make docs-check`, `make flaeche`, `make kopplung-zahlen` (Doku/Spec), `make spec-abdeckung` einmal (Tests oder `@spec`-Tags) (`src/` oder `testserver/`: Verifier fährt `make clean-build` und `make e2e` je einmal auf dem End-Stand; Implementer und Reviewer nicht). Die Läufe `make vergleichslauf-d` sind Messung und Nachweis, keine Gates ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)).
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor (`.harness/skills/reviewer.md`), kein Self-Review.
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt: [SPEC-036](../../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig), [SPEC-035](../../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends), [SPEC-045](../../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig), [Kompatibilitätsmatrix](../../kompatibilitaetsmatrix.md) (Nr. 11), [Messprotokoll des Referenzabgleichs](../../../messungen/referenzabgleich.md) (A-8), `live-ausnahmen.tsv`; eine Folge-ADR zu [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) Entscheidung 7 schreibt der Architect.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben: `BEO-TDS/schemaonly-keyinfo-fmtonly-ohne-traeger` (Ausgang nachführen); keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — Pfad-Kandidaten für §8; geprüft ist das Vorhandensein der Dateien am 2026-11-17, nicht ihre Eignung.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/FixedForms.kt`, `StatementParser.kt`, `StatementPlanner.kt`, `CatalogForms.kt`, `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/BulkCopyProbe.kt` | update, falls Option A | Erkennung der `SET`-Texte und Antwort aus der Tabellenbeschreibung; die Probe von `SqlBulkCopy` ist das Muster für `FMTONLY` |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/token/` | update, falls `KeyInfo` Browse-Tokens verlangt | neue Tokens im Encoder; Umfang erst nach der Messung bekannt |
| `src/test/kotlin/io/github/pt9912/tdswire/simulation/SimulationBackendFixedTest.kt`, `StatementFormsTest.kt`, `SimulationBackendBulkTest.kt` | update | Tests der neuen Texte; `SimulationBackendBulkTest` trägt die Probe mit `SET FMTONLY` als Vorlage |
| `referenztest-dotnet/Vergleich/MessungWireTests.cs`, `referenztest-dotnet/Vergleich/LiveSzenarioTests.cs`, `referenztest-dotnet/Vergleich/live-ausnahmen.tsv` | update | Messszenarien für `SchemaOnly` und `KeyInfo`, drei Regeln entfallen |
| `spec/spezifikation.md`, `docs/plan/kompatibilitaetsmatrix.md`, `docs/messungen/referenzabgleich.md` | update | SPEC-036, Matrix Nr. 11, Tabelle der Ausnahmen, Messprotokoll |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf (Testdateien mit Kennung der Akzeptanzkriterien).

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`open` → `next` → `in-progress`): Nutzerzeichen, ein Verantwortlicher (`Verantwortlich:` ist bis dahin `—`) und die Antwort auf die Frage in §1 und, für den Gleichlauf, [slice-decimaln-laenge-an-messung-angleichen](../open/slice-decimaln-laenge-an-messung-angleichen.md) entschieden (Reihenfolge, nicht Bedingung für die Messung). DoD 1 ist eine Messung an den gepinnten Instanzen (EULA bestätigt 2026-11-01 und 2026-11-17), läuft **nicht parallel** zu Last-Messslices, und die Messsperre `/tmp/claude-1000/-Development-tdswire/messlock` gilt für Messläufe.

**Rückführungen — vorab benennen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2 oder die Messung zeigt, dass `KeyInfo` einen größeren Eingriff in den Encoder verlangt (Zerlegung in `SchemaOnly` und `KeyInfo`).
- `in-progress` → `open` (blockiert): die gepinnten Referenzinstanzen sind nicht beziehbar, oder der Nutzer entscheidet Option B erst nach der Messung (dann schließt der Slice nach DoD 1 und 3 ohne Bau).

## 5. Closure-Trigger

Alle Punkte der DoD in §2 erledigt, `make gates` und `make docs-check` Exit 0, die Regeln der Ausnahmeliste, die dieser Slice trägt, sind entfallen oder mit der Entscheidung des Nutzers in ihrer Art geändert (nicht still stehen gelassen), und die Closure-Notiz mit Steering-Loop-Lerneintrag ist geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang.

- Die Antwort auf `KeyInfo` verlangt Tokens, die der Encoder nicht kennt; Umfang und Aufwand sind vor der Messung unbekannt. — **Ausgang:** offen bis zur Closure; Rückführung nach §4; Option „nur `SchemaOnly`“ ist die Fallback-Zerlegung.
- `GetSchemaTable()` ist für einen Client gemessen; andere Clients stellen andere Texte. — **Ausgang:** offen bis zur Closure; die Aussage trägt für `Microsoft.Data.SqlClient` 7.1.1 ([AGENTS.md](../../../../AGENTS.md) §3.8; `BEO-TDS/festlegung-gilt-nur-fuer-das-clientprofil`).
- Ohne A-11 bleibt der Gleichlauf von `schema-person` und `schema-typen` für `decimal` verfehlt. — **Ausgang:** offen bis zur Closure; Reihenfolge der Slices; die Regel bliebe mit dem Grund A-11 stehen und wäre dann kleiner, nicht gelöscht.
- Eine neue `SET`-Erkennung kann Anweisungen annehmen, die der Server bisher abwies (`SET` ist eine Liste in Form C). — **Ausgang:** offen bis zur Closure; Negativtests für nicht gelistete `SET`-Anweisungen bleiben (Abweisung unverändert).
- Größe: drei Liefer-Punkte. — **Ausgang:** offen bis zur Closure; Rückführung nach §4.

## 7. Closure-Notiz

Wird bei der Closure gefüllt.

**Herkunft:** Lücke A-8 der Ausnahmeliste, [Messprotokoll des Referenzabgleichs](../../../messungen/referenzabgleich.md); Register `BEO-TDS/schemaonly-keyinfo-fmtonly-ohne-traeger` (Träger-Vorschlag: Aufnahme in [slice-katalog-und-ddl-zustand](../done/slice-katalog-und-ddl-zustand.md) oder eigener Slice); [Kompatibilitätsmatrix](../../kompatibilitaetsmatrix.md) Nr. 11; [Results-Notiz von welle-referenzabgleich-release](../done/welle-referenzabgleich-release-results.md). Nutzerentscheidung 2026-11-17: „ja slices anlegen“ (Frage der Closure von `welle-referenzabgleich-release`, Punkt: die 14 Lücken-Nummern ohne Träger-Slice).

## 8. Sub-Area-Prüfungen und Modus-Begründung

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register (`../observations/BEO-TDS/`) gegen die Verzeichnisliste gelesen; Treffer: `schemaonly-keyinfo-fmtonly-ohne-traeger` (offen), `festlegung-gilt-nur-fuer-das-clientprofil`, `messung-am-echten-sql-server-fehlt`. Zähler-Stände stehen in den Dateien des Registers, nicht hier.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
