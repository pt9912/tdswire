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

**Verantwortlich:** — bis zur Priorisierung.

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

- [ ] **Messung** ([TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)): Der Wortlaut der Probe für `dbo.t`, `[dbo].[t]` und einen Namen mit `]` liegt als Messreihe am echten Server (2022 und 2025) im Messprotokoll; die Messreihe führt zusätzlich eine Tabelle mit nicht nullbaren Spalten INT, SMALLINT, TINYINT und BIT, deren Nachricht 0x07 die festen Typen zeigt (Risiko „weitere feste Typen“ aus §6 von [slice-bulk-load-feste-typen-und-referenzclient](../done/slice-bulk-load-feste-typen-und-referenzclient.md)).
- [ ] **Erkennung und Antwort** ([TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate)): `BulkCopyForms` erkennt die gemessenen Formen; Test mit dem aufgezeichneten Text; ein `SqlBulkCopy`-Szenario mit Schema im Integrationslauf.
- [ ] **Nachzug** ([TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit)): `SPEC-036` und die Nutzer-Doku nennen die Grenze nicht mehr.

Nachweis-Pflichten für alle Punkte:

- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor (`.harness/skills/reviewer.md`).
- [ ] Haken der Liefer-Punkte setzt der Planner nach der Verifikation, nicht der Implementer.
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben; keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

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
| `docs/plan/planning/in-progress/roadmap.md`, `docs/plan/planning/welle-testserver-manuell.md`, `docs/plan/planning/observations/BEO-TDS/messung-am-echten-sql-server-fehlt/state.md`, `docs/plan/planning/observations/BEO-TDS/mitschnitt-luecken-ohne-traeger/state.md`, `docs/plan/planning/done/slice-bulk-load-feste-typen-und-referenzclient.md` | update | Zeiger auf den Slice und seinen Ort (`make slice-mv`), Zustand der Zelle |
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

- Der Referenzclient ändert den Text der Probe bei anderen Namen so, dass eine Erkennung nach dem Wortlaut nicht reicht. — **Ausgang:** offen bis zur Closure.

## 7. Closure-Notiz

Wird bei der Closure gefüllt.

## 8. Sub-Area-Prüfungen und Modus-Begründung

**Vorgelagert — Sub-Area-Wahl prüfen:** `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); eine eigene Sub-Area besteht nicht.

**Vorgelagert — offene Beobachtungen sichten:** [`BEO-TDS/messung-am-echten-sql-server-fehlt`](../observations/BEO-TDS/messung-am-echten-sql-server-fehlt/state.md).

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
