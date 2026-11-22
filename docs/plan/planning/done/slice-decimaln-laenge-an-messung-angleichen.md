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

- Die Antwort auf `SchemaOnly` und `KeyInfo` (A-8) — [slice-simulation-schemaonly-keyinfo](../done/slice-simulation-schemaonly-keyinfo.md); sie zeigt `ColumnSize` und braucht diese Länge, ist aber ein eigener Bau.
- Die Länge des Dezimaltyps in RPC-Parametern und im Massenladen (Eingabeseite, `RpcParameterReader`, `BulkValueReader`) — der Parser ist tolerant (Setzung, [SPEC-003](../../../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog)), keine Regel der Listen betrifft ihn.
- Andere Typen mit Längen im TYPE_INFO (`datetime2`, `time`, `date`, `float`) — keine Regel der Liste, Messung `batch-typen` zeigt sie gleich (nur `decimal(10,2)` wird in A-11 genannt, die übrigen Spalten der Zeile stehen gleich in der Regel).
- Clients außer `Microsoft.Data.SqlClient` 7.1.1 — Gegenstand eines Messauftrags, nicht dieses Slice.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit.

- [x] **Messung am echten Server:** die Länge im COLMETADATA von `decimal(p,s)` und `numeric(p,s)` je Precision-Klasse (1–9, 10–19, 20–28, 29–38; je eine Spalte), an 2022 und 2025, als Szenario der Kategorie `MessungWire` im Verfahren der Messreihe am Draht (`LAUF=wire`), Einzelmessung je Instanz mit Messzeile; die Hypothese „immer 17“ ist belegt oder widerlegt.
- [x] **Angleichung (nach Entscheidung des Nutzers, Option A):** `ColumnMapping.kt` und `TypeInfoWriter.kt` schreiben die gemessene Länge; Tests mit Handvektor aus den Messdateien (je Precision-Klasse, `numeric` und `decimal`, NULL-Wert, `SqlBulkCopy`-Probe bleibt 17); die zwei Mitschnitt-Regeln `batch-select/01/colmetadata` und `batch-typen/01/colmetadata` entfallen.
- [x] **Nachzug:** [SPEC-003](../../../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog) (Satz zur Länge und Aussage der Rechnung), [Messprotokoll des Referenzabgleichs](../../../messungen/referenzabgleich.md) (A-11), Kommentar von `TdsType.DecimalType`; bei Option B die Art mit Begründung.
- [x] `make gates` Exit 0, dazu je Art der Änderung nach `harness/README.md` §Minimal agent workflow: engste Testklasse und Mutation je Zusage über enge Testklassen (`src/`), `make docs-check`, `make flaeche`, `make kopplung-zahlen` (Doku/Spec), `make spec-abdeckung` einmal (Tests oder `@spec`-Tags) (`src/` oder `testserver/`: Verifier fährt `make clean-build` und `make e2e` je einmal auf dem End-Stand; Implementer und Reviewer nicht). Die Läufe `make vergleichslauf-d` sind Messung und Nachweis, keine Gates ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)).
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor (`.harness/skills/reviewer.md`), kein Self-Review.
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt: [SPEC-003](../../../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog), [SPEC-045](../../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig), [Messprotokoll des Referenzabgleichs](../../../messungen/referenzabgleich.md) (A-11), `mitschnitt-ausnahmen.tsv`; KDoc von `TdsType.DecimalType`.
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben: `BEO-TDS/mitschnitt-luecken-ohne-traeger` (Ausgang A-11), `BEO-TDS/aussagen-ueber-messung-hinaus`; keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

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
| `docs/plan/planning/done/slice-simulation-schemaonly-keyinfo.md` | update (Text) | A-11 ist aufgelöst; Verweise berichtigt |
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

- Die Länge im COLMETADATA hängt am Client: ein Client könnte nach der Länge Puffer wählen; eine Angleichung auf 17 ändert, was andere Clients (mssql-jdbc) sehen. — **Ausgang: weiter offen, Träger [slice-mssql-jdbc-gegen-testserver](../open/slice-mssql-jdbc-gegen-testserver.md).** Die Wirkung auf mssql-jdbc ist ungemessen: `make e2e` benutzt den Referenzclient, `make messung-treiber-handles` wurde nicht gefahren (Verifikation V-2). Die Länge 17 gleicht dem Vektor der RPC-Eingabeseite von mssql-jdbc ([SPEC-003](../../../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog), M-31); das ist keine Messung der Ausgabeseite.
- Die Messung deckt nur `decimal`/`numeric` je Precision-Klasse einer Spalte; große Skalen und Grenzfälle (Precision 38, Scale 0) sind nur teilweise Teil. — **Ausgang: eingetreten, als Grenze geführt.** Gemessen sind die Precisions 1, 9, 10, 19, 20, 28, 29 und 38, je `decimal` und `numeric`, beide Instanzen; die dazwischen sind nicht einzeln gemessen; die Grenze steht in SPEC-003, im Messprotokoll (W-23, W-24) und in der KDoc von `DecimalType`.
- Die Länge im TYPE_INFO und die Länge der Werte in ROW stehen in Beziehung (Wert höchstens so lang wie die TYPE_INFO); der Parser der Eingabeseite nimmt „bis zur Größe der TYPE_INFO“. — **Ausgang: eingetreten, Träger Nutzer (W-25).** Der Test der Ausgabeseite prüft, dass kein Wert länger als die TYPE_INFO ist (`DecimalValuesTest`); der echte Server schreibt die Wertlänge nach dem Wert, der Encoder nach der Precision (W-25). Die Folgerung „Wert höchstens so lang wie die TYPE_INFO“ ist gekennzeichnet, nicht gemessen.
- Größe: drei Liefer-Punkte. — **Ausgang: entfallen.** Drei Liefer-Punkte, keine Rückführung nach §4; `ColumnMapping.kt` blieb unverändert (der Default im Writer trägt die Länge).

## 7. Closure-Notiz

Geschlossen 2026-11-18 (Planner, Slice-Closure ohne Welle). Geprüfter Stand: Zweig `slice-decimaln-laenge-an-messung-angleichen`, Spitze `aa856672` (fünf Commits, 28 Dateien, +414 −50 gegen `main`); danach nur Doku (diese Closure). Prüfung: Review [2026-11-18-slice-decimaln-laenge-an-messung-angleichen](../../../reviews/2026-11-18-slice-decimaln-laenge-an-messung-angleichen.md) (kein HIGH; F-1 und F-2 MEDIUM, F-3 bis F-5 LOW; alle in `36529248` nachgebessert), [Re-Review](../../../reviews/2026-11-18-slice-decimaln-laenge-an-messung-angleichen-re-review.md) (kein HIGH, kein MEDIUM; N-1 bis N-4), [Verifikation](../../../reviews/2026-11-18-slice-decimaln-laenge-an-messung-angleichen-verifikation.md) (V-1 bis V-3).

**Gegenstand.** Der Server schreibt die Länge 17 im TYPE_INFO von `DECIMALN` und `NUMERICN` im COLMETADATA, wie am echten SQL Server 2022 und 2025 gemessen (Precisions 1, 9, 10, 19, 20, 28, 29, 38, je `decimal` und `numeric`; Messprotokoll W-23, W-24). Die Hypothese „immer 17“ ist an diesen Precisions belegt. A-11 ist aufgelöst, beide Regeln der Ausnahmeliste (`batch-select/01/colmetadata`, `batch-typen/01/colmetadata`) sind entfallen. Entscheidung des Nutzers 2026-11-18: Option A, Code angleichen.

**Beleg je DoD-Punkt (Bestätigung durch den Verifier).**

- Punkt 1, Messung: erfüllt. Ablagen `referenztest-dotnet/Mitschnitt/messung/2022/` und `2025/`, Länge `0x11` je Spalte (Verifier las die Bytes); `MessungBelegTests` in `make clean-build` grün. `make vergleichslauf-d LAUF=wire` hat der Verifier nicht wiederholt (EULA-Lauf); die Rohbytes der Ablage sind der Beleg.
- Punkt 2, Angleichung: erfüllt. Default 17 in `TypeInfoWriter.writeDecimal`; Handvektoren je Precision (`TokenEncoderTest`), NULL-Wert (`DecimalValuesTest`), Probe von `SqlBulkCopy` unverändert 17 (`BulkCopyProbeTest`); `ColumnMapping.kt` blieb unverändert (Abweichung vom Wortlaut der DoD, in §3 benannt, zulässig).
- Punkt 3, Nachzug: erfüllt. SPEC-003, SPEC-036, Messprotokoll (W-23 bis W-25, A-11 aus der Tabelle), KDoc von `DecimalType`; SPEC-045 nur über Protokoll und Regeldatei.
- Sensoren der Art: `make clean-build` Exit 0 (1495 `PASSED`, 795 s), `make e2e` Exit 0 (9 Stufen, 723 s), `make gates` Exit 0 (Verifier, 12 s; die Klassen `werkzeug`, `tests`, `stempel` mit Stempel-Beleg übersprungen, Klasse `immer` frisch; V-3). Mutation je Zusage und `make spec-abdeckung`: Implementer-Pflicht, vom Verifier nicht wiederholt.

**Zuordnung je Aussage.**

- *Gemessen* (echter Server, 2022 und 2025, Einzelmessung je Instanz, `Microsoft.Data.SqlClient` 7.1.1): Länge 17 im COLMETADATA an den acht Precisions; die Wertlänge im ROW an drei Werten (5 Byte für 1,5 in allen 16 Spalten, 9 Byte für `CAST(1.5 AS numeric(38,10))`; W-25).
- *Handvektor:* die COLMETADATA-Bytes der Messdateien je Precision.
- *Selbstkonsistenz:* `DecimalValuesTest` (Wertlänge höchstens TYPE_INFO, gelesen aus dem Längenbyte des Encoders).
- *Folgerung, nicht gemessen:* die Länge 17 an Precisions zwischen den gemessenen; Wert im ROW höchstens so lang wie die TYPE_INFO.
- *Festlegung (Setzung ohne Messung):* keine neue.

**Was hat funktioniert.** Messung vor Bau: die Hypothese „immer 17“ stand im Plan ausdrücklich als nicht gemessen und hat sich an acht Precisions bestätigt; die Messung deckte außerdem W-25 auf, das der Plan nicht kannte. Der Zuschnitt (Default im Writer, `bytes` des Typs als Ausnahme) hielt die Änderung klein.

**Was ging anders als geplant.** (a) Review F-1: die an der Messung entdeckte Lücke W-25 hatte weder Slice noch Regel der Ausnahmeliste; sie ist als offene Schuld mit Träger Nutzer geführt, nicht entschieden. (b) Review F-2: die Plan-Tabelle nannte berührte Dateien nicht; nachgetragen. (c) Review F-3: „Wert im ROW höchstens so lang wie die TYPE_INFO“ stand unter „gemessen“; als Folgerung gekennzeichnet. (d) Review F-4, F-5: Gegenstelle im offenen Slice `slice-simulation-schemaonly-keyinfo` (A-11 aufgelöst, der Eintrag dort bleibt wie vom Implementer gesetzt) und der ROW-Test gegen die TYPE_INFO statt gegen eine Konstante.

**Steering-Loop-Eintrag.** Benannte Spec-Lücke: die Länge der Dezimalwerte im ROW (W-25) weicht vom echten Server ab (der Server schreibt nach dem Wert, der Encoder nach der Precision) und ist in [SPEC-003](../../../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog) und im Messprotokoll (`docs/messungen/referenzabgleich.md`, W-25) als offen geführt. Kein `liegt in`: es wurde keine Regel und kein Sensor verkörpert. Auslöser: `BEO-TDS/mitschnitt-luecken-ohne-traeger`. Lerneffekt, gezählt, nicht verkörpert: eine Messung, die eine Lücke schließen soll, findet die nächste Lücke am selben Gegenstand (hier W-25 neben A-11); der Mitschnitt-Vergleich sieht Unterschiede der Länge nicht, weil er Werte vergleicht, nicht Bytes der Länge.

**Beobachtungs-Register (`../observations/`).** `evidence/slice-decimaln-laenge-an-messung-angleichen.md` in `BEO-TDS/mitschnitt-luecken-ohne-traeger/` (W-25 ohne Träger; Beleg liegt seit der Nachbesserung, die Datei im Register zählt damit fünf Dateien) und `state.md` dort fortgeschrieben: A-11 gebaut, W-25 offen. Der Eintrag ist über der Schwelle und `Träger entschieden`; er wird nicht verkörpert, weil W-25 offen bleibt und die Entscheidung beim Nutzer liegt. Keine neue Datei in `BEO-TDS/aussagen-ueber-messung-hinaus` (F-3 trifft die verkörperte Klasse; der Review fing es, Retirement-Check: die Regel bleibt) und `BEO-TDS/plan-tabelle-nennt-neue-datei-nicht` (F-2 gleiche Klasse, verkörpert im Reviewer-Skill; nicht neu gezählt, Review fing es).

**Folge-Slices.** Keiner angelegt. [slice-mssql-jdbc-gegen-testserver](../open/slice-mssql-jdbc-gegen-testserver.md) (Datei in `open/`) trägt das Risiko mssql-jdbc gegen die Länge 17; für W-25 gibt es keinen Folge-Slice (Entscheidung beim Nutzer).

**Offen, Träger Nutzer (nicht entschieden, nicht erledigt).**

1. **W-25, Länge der Dezimalwerte im ROW:** im Slice, als Folge-Slice oder als Ausnahme der Liste? Der Mitschnitt-Vergleich sieht die Abweichung nicht; gemessen an drei Werten, die Grenzen zu 13 und 17 Byte sind nicht gemessen. Beleg: Register `BEO-TDS/mitschnitt-luecken-ohne-traeger`, `evidence/slice-decimaln-laenge-an-messung-angleichen.md` (Verifikation V-1).
2. **Wirkung der Länge 17 auf mssql-jdbc: ungemessen** (Verifikation V-2); Träger [slice-mssql-jdbc-gegen-testserver](../open/slice-mssql-jdbc-gegen-testserver.md).
3. **LOW N-1 und N-2 des Re-Reviews (nur Text):** offen, nicht blockierend. N-1: Satzzeichen in der Zeile „Berührte Spec-Stellen“ des Kopfes; N-2: „(nicht im ersten Plan)“ in §3 und „War Vorbedingung … ist aufgelöst“ im Risiko von `slice-simulation-schemaonly-keyinfo` beschreiben einen früheren Zustand (AGENTS.md §3.7). Die Closure ändert den Text nicht über den reinen Zustandswechsel hinaus; der Eintrag in `slice-simulation-schemaonly-keyinfo` bleibt wie gesetzt.

**Risiken (Ausgänge in §6).** Weiter offen mit Träger: mssql-jdbc (`slice-mssql-jdbc-gegen-testserver`). Eingetreten: Precisions zwischen den gemessenen (als Grenze geführt), ROW-Länge (W-25, Träger Nutzer). Entfallen: Größe.

**Gates.** Siehe Beleg je DoD-Punkt; `make docs-check` und `make gates` Exit 0 auf dem Stand dieser Closure stehen in der Übergabe des Planners, nicht in dieser Notiz.

**Herkunft:** Lücke A-11 der Ausnahmeliste, [Messprotokoll des Referenzabgleichs](../../../messungen/referenzabgleich.md); Register `BEO-TDS/mitschnitt-luecken-ohne-traeger` (A-11: „ohne Träger“, Verifikation 2026-11-16 von [slice-codec-datentypen-erweiterung](../done/slice-codec-datentypen-erweiterung.md)); [Results-Notiz von welle-referenzabgleich-release](../done/welle-referenzabgleich-release-results.md). Nutzerentscheidung 2026-11-17: „ja slices anlegen“.

**Drei Paarungen** (Repo ohne Wellen-Betrieb, geprüft nach dem Move). *Anker* — diese Notiz verkörpert nichts und trägt kein Feld `liegt in`; nichts zu paaren. *Folge-Slice* — `slice-mssql-jdbc-gegen-testserver` liegt in `open/`; für W-25 gibt es bewusst keinen. *Register* — `evidence/slice-decimaln-laenge-an-messung-angleichen.md` zeigt auf diesen Slice in `done/`.

## 8. Sub-Area-Prüfungen und Modus-Begründung

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register (`../observations/BEO-TDS/`) gegen die Verzeichnisliste gelesen; Treffer: `mitschnitt-luecken-ohne-traeger` (Träger entschieden, A-11 ohne Träger), `aussagen-ueber-messung-hinaus` (verkörpert). Zähler-Stände stehen in den Dateien des Registers, nicht hier.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
