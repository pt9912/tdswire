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

**Verantwortlich:** pt9912.

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-05.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert; jeder Ausschluss mit Begründung.

**Ziel:** Die Bibliothek beantwortet die Katalogabfragen, die d-migrate für `schema reverse` (auch `--include-all`), `schema compare` und `schema migrate` stellt, aus den ihr mitgeteilten Tabellen, gibt DDL (`CREATE TABLE`, `ALTER TABLE ... ADD`) an den Integrator weiter und zeigt danach die geänderte Struktur; der Testserver legt als Integrator die Tabellen an, hält sie veränderlich im Arbeitsspeicher und führt die DDL aus, sodass die Nachprüfung von `schema migrate --execute` besteht (Hürden 6 und 7 der Sonde).

**Quelle und Vorbehalt.** Auslöser ist die Sonde vom 2026-11-05 ([d-migrate-sonde-2026-11-05](../../../messungen/d-migrate-sonde-2026-11-05.md)). Ihr Vorbehalt gilt hier: alles, was dort „lief“, lief gegen erfundene Katalogantworten; belegt ist, was d-migrate sendet und dass Treiber und d-migrate die TDS-Antworten der Bibliothek verarbeiten. Aufwandsgrößen (S/M/L) sind Schätzungen der Sonde, nicht gemessen. Die Sonde sah 26 bis 29 Katalogabfragen je `schema reverse` und die DDL des `--execute`-Laufs; die Antworten waren erfunden. **Hülle:** Der Plan ist knapp; der Implementer erweitert §3 im ersten Lauf, nachdem die Entscheidung in [slice-adr-testserver-bestand-und-katalog](../done/slice-adr-testserver-bestand-und-katalog.md) vorliegt (Form der Weitergabe und Namen der Fläche hängen an ihr). **Aufteilung:** Die Funktion liegt in der Bibliothek (`src/main`, Packages `simulation`, `protocol`); der Testserver (`testserver`) ist Integrator: Bestand, Callbacks, Beispieltabellen. Jeder Liefer-Punkt unten nennt seinen Anteil.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Schreiben von Zeilen (Einfügen, Massenladen, MERGE) — [slice-schreibpfad-bulk-load](../done/slice-schreibpfad-bulk-load.md) und [slice-schreibpfad-merge-output](../done/slice-schreibpfad-merge-output.md); hier nur Struktur.
- Die Entscheidungen zu Weitergabe, Katalog nach Änderungen, FLOAT und Fläche — [slice-adr-testserver-bestand-und-katalog](../done/slice-adr-testserver-bestand-und-katalog.md); dieser Slice setzt sie um. Die Verteilung (Bibliothek = Funktion ohne Speicher, Testserver = Integrator) ist entschieden (Nutzer, 2026-11-05).
- Anmeldung, SET-Anweisungen, `sp_executesql`-Formen und `WHERE 1 = 0` — [slice-d-migrate-verbindung-und-export](../done/slice-d-migrate-verbindung-und-export.md).
- Katalogsichten, die kein Lauf des Solls stellt (zum Beispiel Partitionen oder Volltextindizes mit Inhalt): erst die Sichten, die die Läufe tatsächlich abfragen; im Rest antworten leere Resultsets mit den Spalten (die Sonde: leere Antworten genügen für `--include-all`, gemessen nur gegen ihr aufzeichnendes Backend).
- Typ FLOAT, soweit die Entscheidung ihn nicht aufnimmt (Hürde 12).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit.

Liefer-Punkte (3):

- [x] **Bibliothek, Katalog:** Die Abfragen von `schema reverse` und `schema compare` (Tabellen, Spalten mit Typ, Schlüssel; Indizes, Standardwerte, Prüfbedingungen, Fremdschlüssel, Sichten, Sequenzen, Routinen leer (Setzung)) werden aus den mitgeteilten Tabellen beantwortet, soweit sie sie führen; ein Test je Abfragegruppe liest die Antwort mit dem Treiber oder mit Handvektoren aus einem erneuten Mitschnitt. Beleg: `CatalogFormsTest` und `SimulationBackendCatalogTest` (Handvektoren: `DMigrateWire.kt`, Mitschnitt vom 2026-11-06), Läufe k1 bis k4 im [Messprotokoll](../../../messungen/katalog-und-ddl-zustand-2026-11-06.md). **Abweichung vom Plan-Wortlaut (Verifikation V-3):** der Plan nannte Indizes, Defaults und Prüfbedingungen unter den aus den Tabellen beantworteten Abfragen; `SimColumn` führt sie nicht, sie antworten leer. Träger des Füllens: [slice-katalog-indizes-defaults-fremdschluessel](../done/slice-katalog-indizes-defaults-fremdschluessel.md). Träger der Gegenmessung am echten SQL Server (Typen der Katalogspalten, Setzungen `bigint`, `bit`, `time`, `datetime2`, Sonderwert `maxLength` -1, Fehlernummern der Strukturabweisung; Verifikation V-1): Liefer-Punkt 2 von [slice-qa-vergleichslauf-d-messungen-bulk-merge](../done/slice-qa-vergleichslauf-d-messungen-bulk-merge.md).
- [x] **Bibliothek, Strukturänderung:** `CREATE TABLE`, `ALTER TABLE ... ADD` und die Transaktionsanweisungen als SQL-Text (`set implicit_transactions on|off`, `IF @@TRANCOUNT > 0 COMMIT TRAN`) werden erkannt, die DDL geht nach der Entscheidung an den Integrator, und der Katalog zeigt danach die vom Integrator gemeldete Struktur. Rücknahme über mehrere Anweisungen ist Out-of-Scope von [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate). Beleg: `SimulationBackendStructureTest`, `CatalogFormsTest` (Transaktionstexte), Einbettungstest `FlaecheEmbeddingTest` (zweiter Konstruktor von außen).
- [x] **Testserver und Beleg:** Der Testserver hält die Tabellen veränderlich im Arbeitsspeicher, implementiert die Struktur-Callbacks und teilt der API die Tabellen mit; `schema reverse`, `schema compare` und `schema migrate --execute` von d-migrate 1.12.0 enden mit Exit 0; die Ausgabe (inklusive der Nachprüfung) steht mit Rechner, Last und Image-Digest im Slice oder in einem Messprotokoll: [katalog-und-ddl-zustand-2026-11-06](../../../messungen/katalog-und-ddl-zustand-2026-11-06.md), Läufe k1 bis k11. `schema compare` endet mit Exit 0, wenn das Soll `biginteger` statt `identifier` führt; ein Soll mit `identifier` endet mit Exit 1 (k8, Identität ohne Feld).

Nachweis-Pflichten für alle Punkte:

- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben oder „keine Beobachtung angefallen“ in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — Pfad-Kandidaten für §8; der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/CatalogForms.kt`, `CatalogAnswers.kt` (`internal`) | neu | Wortlaut der 20 Katalogabfragen von d-migrate 1.12.0 (Bytecode des Treibers und Mitschnitt) und die Antworten aus dem Katalog des Anbieters; Hürde 6 der Sonde |
| `.../simulation/StructureParts.kt`, `TransactionForms.kt`, `Statement.kt`, `StatementParser.kt`, `StatementPlanner.kt`, `FixedAnswers.kt`, `SimulationChanges.kt` (`internal`) | neu / update | Formen D1 und D2 (`CREATE TABLE`, `ALTER TABLE … ADD`), Transaktionstexte in Form C, Planung gegen den Stand des Anbieters |
| `.../simulation/SimulationBackend.kt`, `SimModel.kt` | update | zweiter Konstruktor (Anbieter, Strukturänderungs-Callback), `SimColumn.maxLength`, `SimTable.onInsertBatch`; kein neuer Typname (Fassung F) |
| `testserver/kotlin/.../TableStore.kt`, `Main.kt`, `SimDescriptionReader.kt`, `testserver/resources/manuell/sim.yaml` | neu / update | Integrator-Anteil: Bestand im Arbeitsspeicher, `max_length` als Länge, `BIGINT`-Schlüssel der Beispieltabellen; keine Katalog- oder Parserlogik dort |
| `src/test/kotlin/.../simulation/` (`CatalogFormsTest`, `SimulationBackendCatalogTest`, `SimulationBackendStructureTest` auch mit dem Test gleichzeitiger Strukturänderungen, `DMigrateWire`, `StatementFormsTest`: eine Zeile `CREATE TABLE` durch `CREATE INDEX` ersetzt, weil `CREATE TABLE` jetzt erkannt ist; `SimDescriptionReaderTest`: Erwartung `maxLength` und ein Test für `max_length`), `testserver/test/kotlin/.../TableStoreTest.kt` (auch gleichzeitiger Zugriff und die Quelle des Prozesses ohne Datei), `src/integrator/kotlin/.../FlaecheEmbeddingTest.kt` | neu / update | Handvektoren aus dem Mitschnitt, Antworten, Callback, Bestand; Beleg der neuen Namen von außen |
| `spec/spezifikation.md` (`TDS-FA-11.a`, `SPEC-035`, `SPEC-036`, `SPEC-040`, `SPEC-059`), `docs/user/testserver-manuell.md`, `docs/messungen/katalog-und-ddl-zustand-2026-11-06.md` | update / neu | Formen, Katalog, Zusage der Fläche; Läufe mit d-migrate (kein Gate, kein `make`-Ziel) |

**Anschluss an die Entscheidung** ([ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), `Accepted` am 2026-11-06, Messungen: [Messprotokoll des Spikes](../../../messungen/callback-loesung-spike-2026-11-05.md); Liste in §3 von [slice-adr-testserver-bestand-und-katalog](../done/slice-adr-testserver-bestand-und-katalog.md)). Spec-Änderungen dieses Slice: `SPEC-035` (Katalog-Anbieter, Strukturänderungs-Callback, `SimColumn.maxLength`, Abbildung von `SqlType` auf die Typen des Katalogs; gemessen nur `integer`, `nvarchar(4000)`, `date`, `decimal`; `bigint`, `bit`, `time`, `datetime2` sind Setzung), `SPEC-036` (Katalogabfragen mit dem Wortlaut aus d-migrate 1.12.0, `CREATE TABLE`, `ALTER TABLE … ADD`, `SELECT SCHEMA_NAME()`, `IF @@TRANCOUNT > 0 COMMIT|ROLLBACK TRAN`, Satz „abschließend“; dazu [TDS-FA-11.a](../../../../spec/spezifikation.md#tds-fa-11a--simulations-backend) Schritt 1 und die Form C: Texte mit mehreren Anweisungen ohne `;` sind gemessen), `SPEC-059` (zweiter Konstruktor von `SimulationBackend` und `SimColumn.maxLength`, kein neuer Typname), `SPEC-040` (Läufe `schema reverse`, `schema compare`, `schema migrate --execute`). Lesart zu `maxLength`: die Entscheidung nennt die Länge und den Vorgabewert 4000, aber keinen Wertebereich und keinen Sonderwert; der Wert -1 für `NVARCHAR(MAX)` ist eine Setzung der Spezifikation (`SPEC-035`, Änderungsprotokoll 2026-11-06), belegt durch den Lauf mit d-migrate; die angenommene Entscheidung bleibt unverändert, eine Folge-Entscheidung ist nicht erzwungen. Entschieden (ADR `Accepted`, 2026-11-06): Fassung F (zweiter Konstruktor, kein neuer Typname) und `SimColumn.maxLength` für die Länge von Texten; Reihenfolge: nach `slice-testserver-gradle-modul` und nach dem Schnitt der öffentlichen Fläche. Start-Trigger: beide in `done/`. Aus dem Spike: 26 Anweisungen für `schema reverse`, 28 mit `--include-all` (2 Tabellen); ein Soll mit Textlängen außer 4000 endet mit Exit 5, weil der Katalog `nvarchar(4000)` meldet; `schema compare` meldet `identifier -> integer` (bleibt ungelöst, Identität ohne Feld); die Länge löst `maxLength`. Ohne Anbieter besteht die Nachprüfung von `schema migrate --execute` nicht (Gegenprobe c4).

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-adr-testserver-bestand-und-katalog](../done/slice-adr-testserver-bestand-und-katalog.md) liegt in `done/` (Weitergabe, Katalog nach Änderungen und Fläche entschieden), [slice-testserver-gradle-modul](../done/slice-testserver-gradle-modul.md) liegt in `done/` (der Testserver hat seinen Pfad im Modul, damit dieser Slice ihn nur einmal schreibt) und [slice-oeffentliche-flaeche-verkleinern-und-belegen](../done/slice-oeffentliche-flaeche-verkleinern-und-belegen.md) liegt in `done/` (der Schnitt der Fläche steht vor den Namen, die dieser Slice hinzufügt: zweiter Konstruktor von `SimulationBackend`, `SimColumn.maxLength`). Reihenfolge vom Nutzer am 2026-11-06 entschieden ([ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md)).

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

- Umfang des Katalogs: 14 verschiedene `sys.*`-Quellen laut Sonde; ob die Ableitung alle oder nur die abgefragten braucht, ist nicht gemessen (Aufwand der Sonde: L). — **Ausgang:** entfallen: 20 Formen im Wortlaut von d-migrate 1.12.0, 17 von den Läufen berührt, 3 (Identität, berechnete Spalten, größter Wert) nur aus dem Bytecode des Treibers; die übrigen Quellen antworten leer mit den Spalten (`CatalogFormsTest`, `SimulationBackendCatalogTest`, Läufe k1 bis k4 im Messprotokoll). Die Zahl der Anfragen je Lauf (26/27) ist Größenordnung, siehe Grenze unten.
- Die Nachprüfung von `schema migrate --execute` vergleicht Struktur gegen die Soll-Datei; Abweichungen in der Typabbildung (zum Beispiel FLOAT, Hürde 12) können sie rot färben — nicht gemessen. — **Ausgang:** entfallen: die Nachprüfung besteht mit den Typen der Beispieltabellen (Läufe k5 bis k11, vom Verifier wiederholt: `migrate --execute` Exit 0, `compare` danach `IDENTICAL`); FLOAT ist nicht aufgenommen ([ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md)). Die Grenze Identität ohne Feld (`identifier -> biginteger`, Exit 1, k8) trägt [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Konsequenzen und `SPEC-035`.
- Wachsen Namen der öffentlichen Fläche (Callbacks, Anbieter der Tabellenliste), kollidiert das mit [slice-oeffentliche-flaeche-verkleinern-und-belegen](../done/slice-oeffentliche-flaeche-verkleinern-und-belegen.md) (Reihenfolge in [welle-testserver-manuell](welle-testserver-manuell.md) §5). — **Ausgang:** entfallen: `make flaeche` grün, kein neuer Typname (Fassung F: zweiter Konstruktor, `SimColumn.maxLength`); `FlaecheEmbeddingTest` übersetzt die Namen von außen.
- Die Typen der Katalogspalten (`Int32` statt `smallint`/`tinyint`), die Setzungen für `bigint`, `bit`, `time`, `datetime2`, der Sonderwert `maxLength` -1 und die Fehlerarten der Strukturabweisung sind nicht am echten SQL Server gemessen (Review F-6 bis F-8, Verifikation V-1). — **Ausgang:** weiter offen: Liefer-Punkt 2 von [slice-qa-vergleichslauf-d-messungen-bulk-merge](../done/slice-qa-vergleichslauf-d-messungen-bulk-merge.md), Register `BEO-TDS/messung-am-echten-sql-server-fehlt`; in `SPEC-035` als Setzung gekennzeichnet.
- Indizes, Standardwerte, Prüfbedingungen und Fremdschlüssel antworten leer; `schema compare` mit einem Soll, das sie führt, ist nicht gemessen (Verifikation V-3). — **Ausgang:** weiter offen: [slice-katalog-indizes-defaults-fremdschluessel](../done/slice-katalog-indizes-defaults-fremdschluessel.md).
- `SimTable.onInsertBatch` wird in diesem Stand nicht gerufen (Review F-9). — **Ausgang:** weiter offen: [slice-schreibpfad-bulk-load](../done/slice-schreibpfad-bulk-load.md) führt `onInsertBatch`; KDoc und `SPEC-035` sagen es.
- Die Sperre der Strukturänderung ist nicht reentrant und gilt je Backend, nicht je Bestand; ein hängender Callback blockiert weitere Strukturänderungen bis zum Abbruch seines Sammlers (Verifikation V-4). — **Ausgang:** entfallen: die Grenze steht im KDoc von `structure` und in `TDS-FA-11.a` Schritt 9; im Repo gibt es keinen Callback, der das Backend selbst aufruft.
- Die Zahlen des Messprotokolls (26/27 Anfragen, 22/24 mit `sys.`, 69/70/85 in k5 bis k10) stammen aus einem Wegwerf-Wrapper, der nicht im Repo steht (Review F-5, Verifikation V-6). — **Ausgang:** weiter offen: Grenze, im Messprotokoll benannt; Exit-Codes und Berichtsfelder sind vom Verifier wiederholt, die Zahlen nicht; Register `BEO-TDS/spike-prototyp-nicht-im-repo-messung-nicht-wiederholbar` (vierte Datei, Entscheidung des Nutzers steht aus).

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register; `grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln.

- **Was hat funktioniert:** Der Schnitt in Bibliothek (Katalog), Bibliothek (Strukturänderung) und Testserver mit Beleg hielt; die Verteilung „Bibliothek = Funktion ohne Speicher, Testserver = Integrator“ ([ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md)) trug: der Testserver kennt weder Katalog- noch Parserlogik (`make detekt` und die Sperre der Modulgrenze grün). `schema reverse`, `schema compare` und `schema migrate --execute` von d-migrate 1.12.0 enden gegen den Testserver mit Exit 0 samt Nachprüfung; der Verifier wiederholte die Läufe in frischem Kontext (Exit 0, `status: ok`, 3 Anweisungen, zweiter `migrate` `no_op`) und `make clean-build` (1013 Testfälle PASSED), `make e2e` (8 Stufen), `make matrix-zitate`. Die Behebung von F-1 (Sperre je Backend, erneute Planung beim Sammeln) hielt zwei Mutationen stand: der deterministische Bibliothekstest färbt rot, der Bestandstest allein ist probabilistisch (V-9).
- **Was ging anders als geplant:** (1) Die Gegenmessung der Katalogantworten am echten SQL Server hatte keinen Träger: das Messprotokoll nannte den geschlossenen `slice-qa-vergleichslauf-d-messungen`, der Nachfolger `slice-qa-vergleichslauf-d-messungen-bulk-merge` schloss den Katalog aus und verwies auf diesen Slice zurück (V-1, ein Kreis). Behoben: Liefer-Punkt 2 des Nachfolgers trägt die Messung, Messprotokoll, Register und Nachbar-Slices zeigen darauf. (2) Liefer-Punkt 1 nannte Indizes, Defaults und Prüfbedingungen als beantwortet, geliefert sind sie leer (V-3); der Wortlaut ist angeglichen, ein neuer Slice [slice-katalog-indizes-defaults-fremdschluessel](../done/slice-katalog-indizes-defaults-fremdschluessel.md) trägt das Füllen. (3) Fünf Haken standen seit `5fe078e7` vor Review und Verifikation (V-2); der Planner hat sie in der Closure bestätigt, nachdem die Vorbehalte Träger hatten (Punkt 2 und die Nachweis-Pflichten sind durch die Läufe des Verifiers gedeckt, Punkt 1 und 3 nur mit den Vorbehalten V-3 und V-1, die jetzt Träger haben). (4) Das Review fand eine nicht atomare Folge „prüfen im Planer, ändern im Callback“ (F-1); die Plan-Tabelle nannte zwei berührte Testdateien nicht (F-2); die Fix-Runde ließ drei zweite Fundstellen stehen (F-3, V-5). (5) `schema compare` mit `identifier` endet mit Exit 1 (Identität ohne Feld): Grenze von [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), keine Behebung.
- **Steering-Loop-Eintrag:** Nichts neu verkörpert; kein `liegt in`, gezählt wird. Finding-Klassen mit Zähler (Dateien in `evidence/`): *Träger zeigt auf Stelle, die ihn nicht trägt* (`BEO-TDS/offene-punkte-ohne-traeger`, verkörpert, hier ein weiteres Auftreten; die Gegenrichtung „die Kennung muss den Punkt auch annehmen“ steht schon in der Vorlage `slice.template.md` §1 und wurde beim Anlegen des Nachfolger-Slice nicht geprüft); *DoD-Wortlaut weiter als Lieferung* (`BEO-TDS/dod-wortlaut-ueber-der-bauart`, 4, verkörpert); *Haken vor der Verifikation* (`BEO-TDS/haken-vor-verifikation-gesetzt`, 5, verkörpert; der Plan dieses Slice entstand vor der Verkörperung, der Review-Report merkte es nicht an, der Verifier fand es); *Plan-Tabelle nennt die berührten Dateien nicht* (`BEO-TDS/plan-tabelle-nennt-neue-datei-nicht`, 6, verkörpert); *Fortschreibung lässt die zweite Fundstelle stehen* (`BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`, 23, verkörpert); *Zeile ohne unterscheidenden Test* (`BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test`, 21, verkörpert); *Messzahl ohne wiederholbaren Erzeuger* (`BEO-TDS/spike-prototyp-nicht-im-repo-messung-nicht-wiederholbar`, 4×, über der Schwelle, nicht verkörpert, Entscheidung des Nutzers steht aus). *Review-Report bricht `make docs-check`* ist bei diesem Slice nicht aufgetreten (der Verifier fuhr `make docs-check` mit Exit 0). **Benannte Spec-Lücke:** `SPEC-035` und `SPEC-036` führen die Typen der Katalogspalten, die Setzungen für `bigint`, `bit`, `time`, `datetime2`, den Sonderwert `maxLength` -1, die Fehlerarten der Strukturabweisung und die leeren Sichten als Setzung ohne Messung am echten Server; Träger der Messung: Liefer-Punkt 2 von [slice-qa-vergleichslauf-d-messungen-bulk-merge](../done/slice-qa-vergleichslauf-d-messungen-bulk-merge.md), Träger des Füllens der leeren Sichten: [slice-katalog-indizes-defaults-fremdschluessel](../done/slice-katalog-indizes-defaults-fremdschluessel.md). **Grenzen:** (a) Die Zahlen des Messprotokolls (26/27, 22/24, 69/70/85 Anfragen) stammen aus einem Wegwerf-Wrapper außerhalb des Repos und tragen als Größenordnung (V-6); der Verifier wiederholte Exit-Codes und Berichtsfelder, nicht die Zahlen. (b) Die Behebung von F-1 hat kein zweiter Reviewer gelesen; der Verifier bewertete sie und mutierte die Sperre (V-9 benennt den probabilistischen Bestandstest). (c) Die Behebungen von V-4 und V-5 sowie die Änderungen dieser Closure (Nachbar-Slices, Welle, Roadmap, Register) liest kein weiterer Reviewer; `make docs-check` und `make gates` sind der Maßstab. (d) V-7 (`TelemetryRedactionTest` einmal rot in der mutierten Kopie unter Last) ist ein Flake-Kandidat außerhalb dieses Slice und ohne Träger; V-8 ist Kosmetik.
- **Beobachtungs-Register (`../observations/`):** neue Evidence-Dateien `slice-katalog-und-ddl-zustand.md` in `BEO-TDS/messung-am-echten-sql-server-fehlt` (Stand nachgezogen: Träger Liefer-Punkt 2 und der neue Slice), `BEO-TDS/offene-punkte-ohne-traeger`, `BEO-TDS/dod-wortlaut-ueber-der-bauart`, `BEO-TDS/haken-vor-verifikation-gesetzt` (Stand: fünfter Beleg), `BEO-TDS/plan-tabelle-nennt-neue-datei-nicht`, `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`, `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test`, `BEO-TDS/spike-prototyp-nicht-im-repo-messung-nicht-wiederholbar` (Stand: vierte Datei, über der Schwelle). Kein neues Verzeichnis. Kein Eintrag erreicht neu die Schwelle von 3×, der nicht schon verkörpert ist, außer `spike-prototyp-nicht-im-repo-messung-nicht-wiederholbar` (gelesen, Entscheidung beim Nutzer).
- **Folge-Slices:** [slice-katalog-indizes-defaults-fremdschluessel](../done/slice-katalog-indizes-defaults-fremdschluessel.md) (neu, Indizes, Standardwerte, Prüfbedingungen, Fremdschlüssel); [slice-qa-vergleichslauf-d-messungen-bulk-merge](../done/slice-qa-vergleichslauf-d-messungen-bulk-merge.md) (Liefer-Punkt 2: Katalogmessung am echten Server); [slice-schreibpfad-bulk-load](../done/slice-schreibpfad-bulk-load.md) (`onInsertBatch`) und [slice-schreibpfad-merge-output](../done/slice-schreibpfad-merge-output.md) (Start-Trigger „Katalog-Slice in `done/`“ erfüllt).
- **Risiken aus §6:** acht, jedes mit genau einem Ausgang in §6: entfallen 4 (Umfang des Katalogs, Typabbildung und Nachprüfung, Fläche, Sperre), weiter offen 4 (Gegenmessung am echten Server, leere Sichten, `onInsertBatch`, Zahlen des Messprotokolls als Grenze) mit je einem Träger, der die Closure überlebt.
- **Zuordnung Messung | Handvektor | Festlegung (`AGENTS.md` §3.8):** *Messung (am Testserver mit d-migrate 1.12.0, nicht am echten Server):* Läufe k1 bis k11, Exit-Codes und Berichtsfelder, die Nachprüfung von `schema migrate --execute`; *Handvektor:* 17 der 20 Katalogformen aus dem Mitschnitt vom 2026-11-06 (`DMigrateWire.kt`); *aus dem Bytecode des Treibers:* Identität, berechnete Spalten, größter Wert (nicht gelaufen); *Festlegung:* Typnamen und Maße für `bigint`, `bit`, `time`, `datetime2`, `maxLength` -1, Änderungszähler 0, Fehlerarten der Strukturabweisung, leere Sichten, Reihenfolge der Ankunft bei zwei gleichzeitigen Strukturänderungen.
- **Drei Paarungen:** geprüft nach dem Move (2026-11-06): *Anker* — kein Steering-Loop-Eintrag trägt ein Zielort-Feld, nichts zu paaren (die Wörter stehen nur in der Trigger-Formulierung von §1 und §4 und im Satz „kein `liegt in`“ dieser Notiz, außerhalb der Feld-Form); *Folge-Slice* — die vier genannten Folge-Slices liegen als Dateien in `open/`; *Register* — jede genannte `BEO-TDS/<slug>` (acht) ist ein Verzeichnis mit nicht leerem `evidence/`. Rot: keine.

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung.

**Vorgelagert — Sub-Area-Wahl prüfen:** Eine Sub-Area (`*`, Kürzel `TDS`); keine Aufteilung nötig.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (Namensliste der Eröffnung, Texte nicht einzeln gelesen); kein Treffer ausgemacht. Der Implementer sichtet das Register bei Start erneut.

Alle berührten Sub-Areas GF.
