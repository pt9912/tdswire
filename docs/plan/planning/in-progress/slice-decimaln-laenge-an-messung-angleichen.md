# Slice slice-decimaln-laenge-an-messung-angleichen: Länge von `DECIMALN` in COLMETADATA an die Messung angleichen (A-11)

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** ohne Welle (keine Closure-Bedingung außerhalb der DoD dieses Slice; Träger der Lücke A-11 der Ausnahmeliste des Referenzabgleichs, angelegt nach Nutzerentscheidung vom 2026-11-17: „ja slices anlegen“ auf die Frage der Closure von [welle-referenzabgleich-release](../done/welle-referenzabgleich-release.md)).

**Bezug:** [TDS-FA-05](../../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen) (Scope: Resultsets und Datentypen), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md)

**Berührte Spec-Stellen:** [SPEC-003](../../../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog) (Typmapping, Absatz zur Rechnung der Antwortgröße: „die Bytes von COLMETADATA und die Länge des Dezimaltyps sind nicht Teil dieser Aussage (Ausnahme A-11)“) · [SPEC-045](../../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig) (Ausnahmeliste). · [SPEC-036](../../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) (Probe der Zieltabelle von `SqlBulkCopy`, Größe 17). Tatsächlich geändert: SPEC-003 und SPEC-036; SPEC-045 (Ausnahmeliste) nur über das Messprotokoll und die Datei der Regeln.

**Verantwortlich:** pt9912

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-17.

---

## 1. Ziel und Abgrenzung

**Ziel:** Die Länge, die der Server im TYPE_INFO von `DECIMALN` und `NUMERICN` schreibt, ist je Precision-Klasse am echten SQL Server (2022, 2025) gemessen und gleich der Referenz — oder A-11 gilt nach Entscheidung des Nutzers als *so gewollt*.

**Ist-Stand** (gelesen am 2026-11-17; was nicht als gemessen steht, ist aus Datei oder Code gelesen):

- **Lücke A-11** ([Messprotokoll des Referenzabgleichs](../../../messungen/referenzabgleich.md)): in COLMETADATA meldet die Referenz für `decimal(10,2)` die Länge 17, der Server 9; Typ, Precision, Scale und die Werte der Zeilen sind gleich. Zwei Regeln `batch-select/01/colmetadata` und `batch-typen/01/colmetadata` (Mitschnitt-Liste). Die Messung deckt **nur `decimal(10,2)`** (`TdsType.DecimalType`: „aufgezeichnet ist nur `DECIMALN(10,2)`“).
- **Ursache im Code (gelesen):** `ColumnMapping.kt` baut `TdsType.DecimalType(precision, scale)` ohne Größe; `TypeInfoWriter.writeDecimal` setzt dann die Länge nach Precision (5, 9, 13 oder 17 Byte, Rechnung `decimalBytes`), für Precision 10 also 9. `DecimalType` hat das Feld `bytes` für eine abweichende Größe; die Probe der Zieltabelle von `SqlBulkCopy` sendet 17. Das Messprotokoll nannte die Ursache „nicht untersucht“; die Lesung des Codes zeigt, woher die 9 stammt, nicht, warum der echte Server 17 sendet.
- **Weitere Belege der Zahl 17 (aus den Dokumenten):** RPC-Parameter von mssql-jdbc 13.4.0 mit Größe 17 und Precision 38 ([SPEC-003](../../../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog), Messprotokoll M-31; das ist die Eingabeseite, nicht COLMETADATA); die Zeile `schema-person` der Referenz im Live-Lauf nennt `ColumnSize` 17 für `decimal(10,2)`.
- **Hypothese, nicht gemessen:** der echte Server schreibt im COLMETADATA jeder `decimal`-Spalte die Länge 17, unabhängig von der Precision. Die Hypothese ist DoD 1 zu messen; ohne Messung gilt sie nicht.
- **Nicht Gegenstand der Ausnahme:** die Wertbytes je Zeile (Länge 5/9/13/17 je Wert nach `MS-TDS`; die Werte der Zeilen sind in beiden Läufen gleich) — gemessen an `decimal(10,2)`.
- **Vorgeschlagen im Register** (Setzung des Planners): „ein eigener Slice, der die Längenwahl am echten Server und gegen `MS-TDS` belegt, oder ein vierter Liefer-Punkt in [slice-antwortform-nbcrow](../done/slice-antwortform-nbcrow.md) nach Zerlegung“; der zweite Weg entfällt, weil jener Slice in `done/` liegt. [slice-codec-datentypen-erweiterung](../done/slice-codec-datentypen-erweiterung.md) hat `DECIMALN` nach der Verifikation 2026-11-16 nicht berührt.

**Frage an den Nutzer — „Code angleichen“ oder „Zielbild anpassen“ — entschieden am 2026-11-18: Option A, Code angleichen.** Die Spezifikation ist das Zielbild, eine Abweichung des Servers ist Schuld; ob sie hier durch Bau behoben wird oder ob Spezifikation und Ausnahmeliste dem Ist-Stand folgen, entscheidet der Nutzer, nicht der Planner. Der Nutzer hat Option A gewählt (Auftrag an den Implementer, 2026-11-18); Option B unten bleibt als verworfene Alternative stehen.

- **Option A — Code angleichen:** nach der Messung schreibt der Server für `DECIMALN` und `NUMERICN` im COLMETADATA die gemessene Länge je Precision-Klasse (bei Bestätigung der Hypothese: 17). Der Parser nimmt für Werte jede Länge von 2 Byte bis zur Größe der TYPE_INFO (Setzung in [SPEC-003](../../../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog)); die Wertbytes bleiben nach `MS-TDS`. [SPEC-003](../../../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog) wird nachgezogen (der Satz „nicht Teil dieser Aussage“ entfällt oder wird auf gemessene Fälle begrenzt).
- **Option B — Zielbild anpassen:** A-11 wird *so gewollt* mit der Begründung „`MS-TDS` nennt 5, 9, 13 und 17 Byte je Precision; die Länge im TYPE_INFO ist für den Client folgenlos (die Werte sind gleich)“. Die Begründung „folgenlos“ ist für `Microsoft.Data.SqlClient` 7.1.1 an `decimal(10,2)` belegt (Werte gleich), für andere Clients nicht gemessen; der Nutzer müsste die Grenze der Aussage mittragen. Abschnittsnummern von `MS-TDS` stehen hier nicht, das Dokument liegt nicht im Repo.
- **Empfehlung des Planners (Setzung, keine Entscheidung):** Messen, dann Option A — die Spezifikation ist das Zielbild, die Abweichung ist Schuld, der Aufwand ist klein (das Feld `bytes` besteht); die Messung vorher, weil der Code heute für alle Precisions eine andere Länge schreibt als die Probe von `SqlBulkCopy` (17) und eine breite Annahme (immer 17) ohne Messung gegen [AGENTS.md](../../../../AGENTS.md) §3.8 verstieße.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Die Antwort auf `SchemaOnly` und `KeyInfo` (A-8) — [slice-simulation-schemaonly-keyinfo](../open/slice-simulation-schemaonly-keyinfo.md); sie zeigt `ColumnSize` und braucht diese Länge, ist aber ein eigener Bau.
- Die Länge des Dezimaltyps in RPC-Parametern und im Massenladen (Eingabeseite, `RpcParameterReader`, `BulkValueReader`) — der Parser ist tolerant (Setzung, [SPEC-003](../../../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog)), keine Regel der Listen betrifft ihn.
- Andere Typen mit Längen im TYPE_INFO (`datetime2`, `time`, `date`, `float`) — keine Regel der Liste, Messung `batch-typen` zeigt sie gleich (nur `decimal(10,2)` wird in A-11 genannt, die übrigen Spalten der Zeile stehen gleich in der Regel).
- Clients außer `Microsoft.Data.SqlClient` 7.1.1 — Gegenstand eines Messauftrags, nicht dieses Slice.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit.

- [ ] **Messung am echten Server:** die Länge im COLMETADATA von `decimal(p,s)` und `numeric(p,s)` je Precision-Klasse (1–9, 10–19, 20–28, 29–38; je eine Spalte), an 2022 und 2025, als Szenario der Kategorie `MessungWire` im Verfahren der Messreihe am Draht (`LAUF=wire`), Einzelmessung je Instanz mit Messzeile; die Hypothese „immer 17“ ist belegt oder widerlegt.
- [ ] **Angleichung (nach Entscheidung des Nutzers, Option A):** `ColumnMapping.kt` und `TypeInfoWriter.kt` schreiben die gemessene Länge; Tests mit Handvektor aus den Messdateien (je Precision-Klasse, `numeric` und `decimal`, NULL-Wert, `SqlBulkCopy`-Probe bleibt 17); die zwei Mitschnitt-Regeln `batch-select/01/colmetadata` und `batch-typen/01/colmetadata` entfallen.
- [ ] **Nachzug:** [SPEC-003](../../../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog) (Satz zur Länge und Aussage der Rechnung), [Messprotokoll des Referenzabgleichs](../../../messungen/referenzabgleich.md) (A-11), Kommentar von `TdsType.DecimalType`; bei Option B die Art mit Begründung.
- [ ] `make gates` Exit 0, dazu je Art der Änderung nach `harness/README.md` §Minimal agent workflow: engste Testklasse und Mutation je Zusage über enge Testklassen (`src/`), `make docs-check`, `make flaeche`, `make kopplung-zahlen` (Doku/Spec), `make spec-abdeckung` einmal (Tests oder `@spec`-Tags) (`src/` oder `testserver/`: Verifier fährt `make clean-build` und `make e2e` je einmal auf dem End-Stand; Implementer und Reviewer nicht). Die Läufe `make vergleichslauf-d` sind Messung und Nachweis, keine Gates ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)).
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor (`.harness/skills/reviewer.md`), kein Self-Review.
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt: [SPEC-003](../../../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog), [SPEC-045](../../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig), [Messprotokoll des Referenzabgleichs](../../../messungen/referenzabgleich.md) (A-11), `mitschnitt-ausnahmen.tsv`; KDoc von `TdsType.DecimalType`.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben: `BEO-TDS/mitschnitt-luecken-ohne-traeger` (Ausgang A-11), `BEO-TDS/aussagen-ueber-messung-hinaus`; keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — Pfad-Kandidaten für §8; geprüft ist das Vorhandensein der Dateien am 2026-11-17, nicht ihre Eignung.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/result/ColumnMapping.kt`, `src/main/kotlin/io/github/pt9912/tdswire/protocol/token/TypeInfoWriter.kt`, `TdsType.kt` | update, falls Option A | Länge des Dezimaltyps im TYPE_INFO; `DecimalType.bytes` ist das vorhandene Mittel |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/result/` und `src/test/kotlin/io/github/pt9912/tdswire/protocol/token/` (Dateien im ersten Lauf zu bestimmen) | update | Tests der Länge je Precision-Klasse; Handvektor aus der Messung |
| `referenztest-dotnet/Vergleich/MessungWireTests.cs`, `referenztest-dotnet/Mitschnitt/messung/` | update, neu | Messszenario und Ablage der Messdateien je Instanz |
| `referenztest-dotnet/Vergleich/mitschnitt-ausnahmen.tsv` | update | zwei Regeln A-11 entfallen |
| `spec/spezifikation.md`, `docs/messungen/referenzabgleich.md` | update | SPEC-003 (Länge im TYPE_INFO), Tabelle der Ausnahmen |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/BulkCopyProbe.kt` | update | die eigene Größe 17 der Probe entfällt, der Default des Writers trägt sie (nicht im ersten Plan) |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/{token,result,dispatch,response}/` (`TokenEncoderTest`, `DecimalValuesTest`, `ColumnMappingTest`, `BulkCopyProbeTest`, `ResponseRowFormTest`), `referenztest-dotnet/Mitschnitt/MessungBelegTests.cs` | update | Handvektor je Precision, Eigenschaft der ROW-Länge, Ablagezahl 38, Beleg der Ablage |
| `docs/messungen/zeilenform-nbcrow-2026-11-17.md`, `docs/messungen/unterschiede-2022-2025-bestand-2026-11-17.md`, `docs/plan/kompatibilitaetsmatrix.md`, `docs/plan/spec-abdeckung.md`, `tools/mk/vergleichslauf-d.mk` | update | Verweise auf A-11, Testzitat, Szenarienzahl; erzeugte Matrix |
| `docs/plan/planning/open/slice-simulation-schemaonly-keyinfo.md` | update (Text) | A-11 ist aufgelöst; Verweise berichtigt |
| `docs/plan/planning/observations/BEO-TDS/mitschnitt-luecken-ohne-traeger/evidence/` | neu | Beleg W-25 ohne Träger |

`ColumnMapping.kt` blieb unverändert: der Default im `TypeInfoWriter` trägt die Länge.

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf (Testdateien mit Kennung der Akzeptanzkriterien).

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`open` → `next` → `in-progress`): Nutzerzeichen, ein Verantwortlicher (`Verantwortlich:` ist bis dahin `—`) und die Antwort auf die Frage in §1 für den Bau (die Messung ist davon unabhängig und kann vorher starten). DoD 1 ist eine Messung an den gepinnten Instanzen (EULA bestätigt 2026-11-01 und 2026-11-17), läuft **nicht parallel** zu Last-Messslices, und die Messsperre `/tmp/claude-1000/-Development-tdswire/messlock` gilt für Messläufe.

**Rückführungen — vorab benennen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2 oder die Messung zeigt eine Längenwahl, die mehr als `ColumnMapping.kt` und `TypeInfoWriter.kt` ändert.
- `in-progress` → `open` (blockiert): die gepinnten Referenzinstanzen sind nicht beziehbar (Pull, Platz, geänderter EULA-Text).

## 5. Closure-Trigger

Alle Punkte der DoD in §2 erledigt, `make gates` und `make docs-check` Exit 0, die Regeln der Ausnahmeliste, die dieser Slice trägt, sind entfallen oder mit der Entscheidung des Nutzers in ihrer Art geändert (nicht still stehen gelassen), und die Closure-Notiz mit Steering-Loop-Lerneintrag ist geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang.

- Die Länge im COLMETADATA hängt am Client: ein Client könnte nach der Länge Puffer wählen; eine Angleichung auf 17 ändert, was andere Clients (mssql-jdbc) sehen. — **Ausgang:** offen bis zur Closure; Wirkung auf mssql-jdbc ist nicht gemessen; `make e2e` und `make messung-treiber-handles` laufen im Verifier-Lauf; die Aussage trägt für den Referenzclient.
- Die Messung deckt nur `decimal`/`numeric` je Precision-Klasse einer Spalte; große Skalen und Grenzfälle (Precision 38, Scale 0) sind nur teilweise Teil. — **Ausgang:** offen bis zur Closure; Grenze steht im Messprotokoll ([AGENTS.md](../../../../AGENTS.md) §3.8).
- Die Länge im TYPE_INFO und die Länge der Werte in ROW stehen in Beziehung (Wert höchstens so lang wie die TYPE_INFO); der Parser der Eingabeseite nimmt „bis zur Größe der TYPE_INFO“. — **Ausgang:** offen bis zur Closure; Tests der Ausgabeseite prüfen, dass kein Wert länger als die TYPE_INFO ist.
- Größe: drei Liefer-Punkte. — **Ausgang:** offen bis zur Closure; Rückführung nach §4.

## 7. Closure-Notiz

Wird bei der Closure gefüllt.

**Offen, Träger Nutzer (Stand der Implementation):** die Länge der Dezimalwerte im ROW (Messprotokoll W-25: der Server schreibt nach dem Wert, der Encoder nach der Precision) hat weder Slice noch Regel der Ausnahmeliste; ob sie im Slice, als Folge-Slice oder als Ausnahme geführt wird, entscheidet der Nutzer. Beleg im Register: `BEO-TDS/mitschnitt-luecken-ohne-traeger`, `evidence/slice-decimaln-laenge-an-messung-angleichen.md`.

**Herkunft:** Lücke A-11 der Ausnahmeliste, [Messprotokoll des Referenzabgleichs](../../../messungen/referenzabgleich.md); Register `BEO-TDS/mitschnitt-luecken-ohne-traeger` (A-11: „ohne Träger“, Verifikation 2026-11-16 von [slice-codec-datentypen-erweiterung](../done/slice-codec-datentypen-erweiterung.md)); [Results-Notiz von welle-referenzabgleich-release](../done/welle-referenzabgleich-release-results.md). Nutzerentscheidung 2026-11-17: „ja slices anlegen“ (Frage der Closure von `welle-referenzabgleich-release`, Punkt: die 14 Lücken-Nummern ohne Träger-Slice).

## 8. Sub-Area-Prüfungen und Modus-Begründung

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register (`../observations/BEO-TDS/`) gegen die Verzeichnisliste gelesen; Treffer: `mitschnitt-luecken-ohne-traeger` (Träger entschieden, A-11 ohne Träger), `aussagen-ueber-messung-hinaus` (verkörpert). Zähler-Stände stehen in den Dateien des Registers, nicht hier.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
