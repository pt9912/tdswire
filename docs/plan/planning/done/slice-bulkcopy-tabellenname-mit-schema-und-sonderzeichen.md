# Slice slice-bulkcopy-tabellenname-mit-schema-und-sonderzeichen: Die Probe der Zieltabelle von `SqlBulkCopy` nimmt Namen mit Schema und Namen mit `]` oder `'` an

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-testserver-manuell

**Bezug:** [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md)

**Berührte Spec-Stellen:** SPEC-036 (Form B, Probe der Zieltabelle von `SqlBulkCopy`: Grenze „Tabelle ohne Schema, Name ohne `]` und `'`“) · SPEC-001 (Absatz Massenladen, Verweis auf die Probe) · SPEC-040 (Zeile der Simulations-Tests: Erkennung der Probe, Bindung der Testtexte).

**Verantwortlich:** Implementer-Agent, Auftrag des Nutzers 2026-11-08.

**Autor:** pt9912 (Entwurf durch den Implementer-Agenten nach F-11 des Reviews, von der Planner-Rolle geprüft und bestätigt). **Datum:** 2026-11-08.

---

## 1. Ziel und Abgrenzung

**Ziel:** `SqlBulkCopy` lädt auch in Tabellen mit Schema (`DestinationTableName = "dbo.t"`) und in Tabellen, deren Name `]` oder `'` enthält, wie es der echte SQL Server annimmt (Zielbild: der Server ist die Referenz). Heute erkennt [BulkCopyForms](../../../../src/main/kotlin/io/github/pt9912/tdswire/simulation/BulkCopyForms.kt) nur den aufgezeichneten Wortlaut: sechs Tabellen ohne Schema, Name ohne `]` und `'`; jeder andere Text endet mit `TDS_UNSUPPORTED_SQL`.

**Herkunft.** F-11 des Reviews von [slice-bulk-load-feste-typen-und-referenzclient](../done/slice-bulk-load-feste-typen-und-referenzclient.md): eine Zielbild-Lücke ohne Träger. Aufgezeichnet ist die Form der Probe nur für Namen ohne Schema; wie der Referenzclient den Text bei `dbo.t`, `[dbo].[t]` oder `]]` im Namen ändert, ist nicht gemessen.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Weitere Spaltentypen der Tabelle (SMALLINT, Gleitkomma, Geld) in der Antwort der Probe — das Typmodell des Backends kennt sie nicht, Träger ist der Neuschnitt der Katalog-Slices der Welle.
- Eine Messung ohne Entscheid zur EULA des Images — die Messung am echten Server (`make vergleichslauf-d`) startet nur der Nutzer.

## 2. Definition of Done

Liefer-Punkte (drei):

- [x] **Messung** ([TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)): Der Wortlaut der Probe für `dbo.t`, `[dbo].[t]` und einen Namen mit `]` liegt als Messreihe am echten Server (2022 und 2025) im Messprotokoll; die Messreihe führt zusätzlich eine Tabelle mit nicht nullbaren Spalten INT, SMALLINT, TINYINT und BIT, deren Nachricht 0x07 die festen Typen zeigt (Risiko „weitere feste Typen“ aus §6 von [slice-bulk-load-feste-typen-und-referenzclient](../done/slice-bulk-load-feste-typen-und-referenzclient.md)).
- [x] **Erkennung und Antwort** ([TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate)): `BulkCopyForms` erkennt die gemessenen Formen; Test mit dem aufgezeichneten Text; ein `SqlBulkCopy`-Szenario mit Schema im Integrationslauf.
- [x] **Nachzug** ([TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit)): `SPEC-036` und die Nutzer-Doku nennen die Grenze nicht mehr.

Nachweis-Pflichten für alle Punkte:

- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor (`.harness/skills/reviewer.md`).
- [x] Haken der Liefer-Punkte setzt der Planner nach der Verifikation, nicht der Implementer.
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben; keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/BulkCopyForms.kt` | update | Erkennung nach der Messung: ein oder zwei Teile, `]]` und `''`, Leerraum außerhalb der Namen; Suche im genannten Schema; besitzende Wiederholung im Namensteil (Namen von 100 000 Zeichen ohne `StackOverflowError`) |
| `src/test/kotlin/io/github/pt9912/tdswire/simulation/BulkCopyFormsTest.kt`, `src/test/resources/messung/bulk-referenzclient/probe-*.sql` (neun Texte, aus der Ablage der Instanz 2022) | update, new | Test mit den aufgezeichneten Texten und den Varianten, mit langen Namen, mit der Setzung zu Leerraum und Schreibweise und mit der Summe je Textdatei (Liefer-Punkt 2) |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/bulk/BulkColumnType.kt`, `BulkLoadReader.kt` (nur KDoc) | update | die festen Typen INT, SMALLINT, TINYINT und BIT sind gemessen (M-36), nicht Handvektor |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/bulk/BulkLoadReaderReferenceClientTest.kt`, `src/test/resources/messung/bulk-referenzclient/typen-fest.hex` | update, new | die gemessene Nachricht 0x07 für INT, SMALLINT, TINYINT und BIT liest der Leser (Liefer-Punkt 1 und 2) |
| `referenztest-dotnet/Integration/SqlBulkCopyTests.cs`, `testserver/kotlin/io/github/pt9912/tdswire/testserver/IntegrationServer.kt`, `tools/simulationstest.sh` | update | Szenario mit Schema und mit `s1.bulk]ziel'x` im Integrationslauf (zweite Tabelle, Zahl der Tests 13 und der Tabellen 7 im Skript) |
| `referenztest-dotnet/Vergleich/MessungBulkTests.cs`, `referenztest-dotnet/Mitschnitt/MessungBulkBelegTests.cs`, `referenztest-dotnet/Mitschnitt/messung/bulk-merge-katalog/{2022,2025}/bulk-name-*.messung` (sechs Dateien), `tools/mk/vergleichslauf-d.mk` (Kommentar) | update, new | drei Szenarien der Messreihe (Liefer-Punkt 1), Belege ohne Instanz, Ablage |
| `referenztest-dotnet/Mitschnitt/MessungBulkBelegTests.cs` (Tabelle der Summen), `BulkCopyFormsTest.kt` (gleiche Tabelle) | update | Bindung: die Textdateien `probe-*.sql` gleichen der Probe in der Ablage beider Instanzen (SHA-256 auf beiden Seiten) |
| `docs/plan/planning/in-progress/roadmap.md`, `docs/plan/planning/done/welle-testserver-manuell.md`, `docs/plan/planning/observations/BEO-TDS/messung-am-echten-sql-server-fehlt/state.md`, `docs/plan/planning/observations/BEO-TDS/mitschnitt-luecken-ohne-traeger/state.md`, `docs/plan/planning/done/slice-bulk-load-feste-typen-und-referenzclient.md` | update | Zeiger auf den Slice und seinen Ort (`make slice-mv`), Zustand der Zelle |
| `docs/reviews/2026-11-08-6c0f7f62-verifikation-slice-bulk-load-feste-typen-und-referenzclient.md` | update | Zeiger auf den Slice nachgezogen (`make slice-mv`), der Satz zum Ort angeglichen |
| `docs/plan/spec-abdeckung.md` | update | erzeugt von `make spec-abdeckung` (Tests geändert) |
| `docs/plan/planning/observations/BEO-TDS/bulkcopy-drei-namensteile-gemessen-nicht-gebaut/` | new | Träger der Grenze „drei Namensteile gemessen, nicht gebaut“ (Register, kein Slice) |
| `spec/spezifikation.md` (`SPEC-036`, `SPEC-040`), `docs/user/` (`testserver-manuell.md`, `einbettung-und-betrieb.md`, `referenz-konfiguration.md`), `docs/plan/kompatibilitaetsmatrix.md`, `docs/messungen/referenzabgleich.md` (M-33 bis M-36) | update | Grenze entfällt, Messprotokoll (Liefer-Punkt 3) |

## 4. Trigger

**Start** (`next` → `in-progress`): [slice-bulk-load-feste-typen-und-referenzclient](../done/slice-bulk-load-feste-typen-und-referenzclient.md) liegt in `done/` (erfüllt mit dessen Closure), der Nutzer hat die Messung am echten Server freigegeben, und ein Verantwortlicher ist eingetragen.

**Rückführungen — vorab benennen:**

- `in-progress` → `next` (zu groß): die Antwort verlangt einen Typ, den das Typmodell nicht kennt; Schnittvorschlag: Messung | Erkennung | Nachzug.
- `in-progress` → `open` (blockiert): keine Freigabe der Messung am echten Server.

## 5. Closure-Trigger

Alle drei Liefer-Punkte der DoD erledigt, `make gates` grün, der Review-Report liegt vor, die Closure-Notiz trägt den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte

- Der Referenzclient ändert den Text der Probe bei anderen Namen so, dass eine Erkennung nach dem Wortlaut nicht reicht. — **Ausgang:** eingetreten und gedeckt. Der Referenzclient ändert den Text je nach Namen (Schema, `]]`, `''`, drei Teile mit `EXEC [<Datenbank>]..sp_tablecollations_100`; M-33 bis M-35); die Erkennung deckt ein und zwei Teile, drei Teile sind gemessen und nicht gebaut (Register [bulkcopy-drei-namensteile-gemessen-nicht-gebaut](../observations/BEO-TDS/bulkcopy-drei-namensteile-gemessen-nicht-gebaut/state.md)).

## 7. Closure-Notiz

- **Was hat funktioniert:** Die Messreihe M-33 bis M-36 (Einzelmessung 2026-11-11, `Microsoft.Data.SqlClient` 7.1.1, Instanz 2022 und 2025) lieferte die Probetexte für Namen mit Schema und mit `]` und `'`; die Rohbytes beider Instanzen sind gleich (Verifikationsbericht, Abschnitt „Rohbytes der Ablage“). `BulkCopyForms` erkennt ein und zwei Teile, `]]` und `''`, und sucht im genannten Schema; das Integrationsszenario läuft mit 13 von 13 Tests (Stufe 3 von `make e2e`); der Leser nimmt die gemessene Nachricht 0x07 für INT, SMALLINT, TINYINT und BIT an. Die Grenze „Tabelle ohne Schema, Name ohne `]` und `'`“ steht nirgends mehr.
- **Was ging anders als geplant:** Der Review fand einen `StackOverflowError` der neuen Erkennung bei Namen ab etwa 3000 Zeichen (F-1, Regression gegen `main`); behoben mit besitzender Wiederholung in `82f62eae`. Die Spezifikation nannte die Antwort auf eine unbekannte Tabelle weiter „nicht aufgezeichnet“ (F-2), obwohl M-35 sie aufzeichnet; nachgezogen.
- **Zuordnung Messung | Festlegung.** *Messung am echten Server* (M-33 bis M-36, Einzelmessungen je Instanz 2022 und 2025, Client `SqlBulkCopy`): Probetexte für `dbo.t`, `[dbo].[t]`, `"dbo"."t"`, Namen mit `]` (als `]]`) und `'` (als `''`, in `insert bulk` einfach), die Form mit drei Teilen, ERROR 208 für unbekannte Tabelle und Schema (mitten im Batch), die Nachricht 0x07 der festen Typen mit Zeilen und `DONE`. *Festlegung (Setzung, in `SPEC-036` gekennzeichnet):* Leerraum außerhalb der Namen und Semikolon am Ende (Trimmen), Schreibweise von Schema und Name bei der Suche im Katalog (ohne Beachtung der Schreibweise; am echten Server nicht gemessen), mehrere Leerzeichen in Literalen der Vorlage werden angenommen. *Abweichung dieses Servers (benannt):* 208 kommt vor den Ergebnissen statt mitten im Batch; der Text „Tabelle nicht beschrieben“ ist nicht der des echten Servers.
- **Grenze der Messung.** Die Messung am echten Server ist mit `SqlBulkCopy` gemacht und **kein Nachweis für `mssql-jdbc`** (d-migrate): mit Schema und Sonderzeichen in `--table` wurde dieser Client weder am echten Server noch am Testserver gefahren; für Namen ohne Schema ist d-migrate 1.12.0 gegen den Testserver unverändert (Import, Transfer, Export, Schlüsselverstoß; Verifikationsbericht, Abschnitt Referenzclients). Weitere nicht gemessene Grenzen: vier Teile, Länge 128, Zeichen außerhalb der Basic Multilingual Plane, Zeilenumbruch im Namen.
- **Setzung zur KDoc von `PART` (V-4, LOW, `AGENTS.md` §3.8).** Die KDoc der Konstante `PART` in `BulkCopyForms.kt` sagt „ein Name von einer Million Zeichen wird ohne `StackOverflowError` erkannt (`BulkCopyFormsTest`)“; der Test geht nur bis 100 000 Zeichen. Die Aussage ist wahr, der genannte Beleg trägt sie nicht. Der Beleg für die Million ist die Messung des Verifiers (Verifikationsbericht, Kurzreview des Parsers: Texte bis 12 MB in höchstens 83 ms, linear, kein Fehler), eine Einzelmessung im Rig, kein Test des Repos. Als Setzung geführt; die Korrektur der KDoc (ein Satz: „einhunderttausend“ oder Test auf eine Million) ist dem nächsten Code-Slice übergeben, weil dieser Abschluss keinen Code ändert.
- **Review und Verifikation.** Review `2026-11-11-d4331dc4` ([Report](../../../reviews/2026-11-11-d4331dc4-review-slice-bulkcopy-tabellenname-mit-schema-und-sonderzeichen.md)): 0 HIGH, 3 MEDIUM, 4 LOW, 3 INFO, alle zehn behoben in `82f62eae`. Einen zweiten Review der Nachbesserung gab es nicht; der Parser der Nachbesserung hat nur das Kurzreview im [Verifikationsbericht](../../../reviews/2026-11-11-82f62eae-verifikation-slice-bulkcopy-tabellenname-mit-schema-und-sonderzeichen.md) (Differentialtest alt gegen neu mit 40 000 Fällen, gleiche Ergebnisse; Mutationen R, MC, MD, LF rot). Verifikation am Stand `82f62eae`: `make clean-build` (560 s, 1349 PASSED), 349 Zitate der Matrix PASSED (`tools/matrix-zitate.sh` gegen das Log), `make e2e` (8 Stufen, Stufe 3: 13 von 13), `make referenztest-dotnet` frisch (136 bestanden), `make gates FORCE=1`, `make flaeche`, `make docs-check`. Nach dem Merge von `main` (`f2db5649`, nur Planungsdokumente; kein Test und kein Tag aus `main`, `make spec-abdeckung` nicht neu gefahren) `make gates` Exit 0: die drei Gruppen `werkzeug`, `tests`, `stempel` übersprungen, weil der Stempel den Eingang der Verifikation belegt (der Merge ändert keinen Eingang der Klassen), die Gates der Klasse `immer` (`docs-check`, `flaeche` u. a.) liefen.
- **Steering-Loop-Eintrag:** keine Regel, kein Sensor-Zielort und keine Skill-Zeile mit Herkunfts-Anker verkörpert; beide Einträge sind gezählt, nicht verkörpert (kein Feld `liegt in`). Gelernt: (a) Eine Alternation unter einem unbegrenzten `+` in einem Regex rekursiert je Zeichen der JVM und wirft `StackOverflowError`, den `guarded` als `Error` nicht fängt; die Lösung ist die besitzende Wiederholung `++` (die erkannte Menge bleibt gleich, Differentialtest), gehalten durch einen Test mit langem Namen (Mutation R rot). Register: `BEO-TDS/regex-alternation-unter-plus-rekursiert-je-zeichen` (1×). (b) Ein Testtext „aus der Aufzeichnung“ ist erst durch eine Summe (SHA-256) auf beiden Seiten an den Beleg gebunden, in der Test-Datei und gegen die Ablage; ohne sie prüfte nur ein einmaliger Handlauf. Register: `BEO-TDS/testtext-aus-aufzeichnung-ohne-summe-auf-beiden-seiten` (1×). *Benannte Spec-Lücke* (getragen in der versionierten Spezifikation, `SPEC-036` Form B): Name mit drei Teilen (gemessen, nicht gebaut), 208 mitten im Batch (Abweichung dieses Servers), `mssql-jdbc` mit Schema und Sonderzeichen (nicht gemessen).
- **Beobachtungs-Register (`../observations/`):** neu `BEO-TDS/bulkcopy-drei-namensteile-gemessen-nicht-gebaut` (Zähler 1, Vorgang Review `d4331dc4`; der Widerspruch „Benannt, nicht gezählt“ in `observation.md` ist in `state.md` berichtigt, keine zweite Evidence-Datei), `BEO-TDS/regex-alternation-unter-plus-rekursiert-je-zeichen` und `BEO-TDS/testtext-aus-aufzeichnung-ohne-summe-auf-beiden-seiten` (je 1×, Evidence: dieser Slice); Zeiger in `BEO-TDS/messung-am-echten-sql-server-fehlt` und `BEO-TDS/mitschnitt-luecken-ohne-traeger` nachgezogen. Haken nach der Verifikation gesetzt.
- **Folge-Slices:** keiner angelegt. Übergabe an den nächsten Code-Slice: Ein-Satz-Korrektur der KDoc von `PART` (V-4). Der Name mit drei Teilen hat seinen Träger im Register, kein Slice.
- **Risiken aus §6:** das eine Risiko trägt seinen Ausgang: eingetreten und gedeckt (V-3), Grenze drei Teile im Register.
- **V-1 und V-5.** V-1: der Kopf trägt den Verantwortlichen (Implementer-Agent, Auftrag des Nutzers 2026-11-08); die DoD-Zeile „Nachzug“ bleibt im Wortlaut, die neuen Grenzen stehen in `SPEC-036` und im Register. V-5 (INFO): zwei Sätze (`referenz-konfiguration.md` Zeile 69, Matrix-Zeile BULK_LOAD) nennen den Client im Satz, keine Falschaussage.
- **Drei Paarungen:** Anker — keine Regel mit `liegt in` verkörpert, nichts zu paaren. Folge-Slice — keiner genannt. Register — die drei genannten Verzeichnisse `BEO-TDS/…` existieren, jedes trägt ein nicht leeres `evidence/`. Getragen.

## 8. Sub-Area-Prüfungen und Modus-Begründung

**Vorgelagert — Sub-Area-Wahl prüfen:** `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); eine eigene Sub-Area besteht nicht.

**Vorgelagert — offene Beobachtungen sichten:** [`BEO-TDS/messung-am-echten-sql-server-fehlt`](../observations/BEO-TDS/messung-am-echten-sql-server-fehlt/state.md).

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
