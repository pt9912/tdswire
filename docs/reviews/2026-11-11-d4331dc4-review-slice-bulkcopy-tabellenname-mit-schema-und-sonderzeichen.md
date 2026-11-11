# Review-Report: slice-bulkcopy-tabellenname-mit-schema-und-sonderzeichen — 2026-11-11

**Review-Art:** Code (gegen Plan, ADR und Hard Rules; nicht gegen die DoD, das ist die Frage des Verifiers).

**Gegenstand:** Diff `main` (`7412984a`) .. `d4331dc4` im Zweig `worktree-agent-ac37abe6cc0d45031` (41 Dateien, +1311/−131; Commits: vier `slice-mv`, `378f721a` Messreihe, `46a992a9` Bau, `cdd831eb` Spezifikation und Doku, `82150cb6` detekt-Korrektur, `7f373c6d` Merge `main`, `ec4be545` Abdeckungsmatrix, `d4331dc4` Roadmap-Link). Merge-Probe `git merge-tree --write-tree HEAD <main>`: ohne Konflikt; `main` stand beim Lauf auf `7412984a`, der Slice `slice-drop-constraint-form-d5` war nicht eingemergt. Die detekt-Korrektur `82150cb6` (Quelltext und Testcode) ist in dieser Prüfung enthalten und braucht keinen eigenen Review.

**Skill:** `.harness/skills/reviewer.md` @ `d4331dc4` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-11

**Eingangs-Kontext** (die Verträge, gegen die geprüft wurde):

- Slice `slice-bulkcopy-tabellenname-mit-schema-und-sonderzeichen` (Plan §3, DoD, Risiken §6)
- [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) und `ADR-0044` (Atomarität des Stapel-Callbacks, unverändert berührt: kein Eingriff in `SimTable.onInsertBatch`)
- [SPEC-001](../../spec/spezifikation.md#spec-001--zustandsautomat-einer-verbindung), [SPEC-036](../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) Form B, SPEC-040, [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), TDS-QA-01
- `AGENTS.md` §3.1 bis §3.8; Grundsatz „Spec ist Zielbild“ (der echte SQL Server ist die Referenz, Aussage ≤ Beleg); vorheriger Report am Modul: `2026-11-08-6c0f7f62` (Verifikation des Eltern-Slice)

## Selbst gefahren

| Lauf | Ergebnis |
|---|---|
| `make gates FORCE=1` | Exit 0 (rund 3 Minuten, die Docker-Schichten überwiegend `CACHED`: gleicher Eingang wie der Lauf des Implementers) |
| `make flaeche` | grün (keine neue öffentliche Fläche; `recognize` und `TableRef` sind `internal`) |
| `make docs-check` | 1342 Dateien, 0 Befunde (vor Anlage dieses Reports) |
| `make simulations-integration` | Exit 0, 13 von 13 Tests, 7 Tabellen im Log, 51 s |
| Ablage gegen Testtexte (Skript im Scratchpad, einmalig, kein Gate) | alle 10 Dateien `probe-*.sql` sind Zeichen für Zeichen gleich je einem Probetext der Ablage, in beiden Instanzen (2022 und 2025); `typen-fest.hex` ist gleich der Nachricht 0x07 von `bulk-name-feste-typen` in beiden Instanzen; die Dateigrößen 757797 und 758177 Byte des Messprotokolls stimmen |
| Ablage gelesen (Probetexte der drei Szenarien, beide Instanzen) | `OBJECT_ID('<Name>')`, `FROM <Name>`, `sp_tablecollations_100 N'…'` wie in SPEC-036 beschrieben: ohne Schema `.[t]`, mit Schema `[s].[t]`, drei Teile `EXEC [db]..sp_tablecollations_100 N'[s].[t]'`; `'` als `''` an allen drei Stellen, in `insert bulk` einfach; `]` als `]]` an allen drei Stellen; unbekannte Tabelle: ERROR 208, Class 16, State 1 mitten im Batch (nach den Ergebnissen) |
| Eigener Rig (Image `impl-bulkname-deps:lokal`, Benutzer 1000, Kopie der Quellen, eigenes Gradle-Home im Scratchpad) | Mutationen und Sonden unten |

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| F-1 | MEDIUM | `BulkCopyForms.recognize` wirft `StackOverflowError` bei einem Namen ab etwa 3000 Zeichen (gefahren: 1000 Zeichen erkannt, 3000, 5000, 10 000, 100 000 und 1 000 000 Zeichen werfen; mit `]]`-Paaren, mit `a` und mit Schema ebenso; ein Name von 10 000 Zeichen mit fehlendem Ende ebenso). Die Fassung auf `main` erkannte denselben Text mit 1 000 000 Zeichen in 67 ms ohne Fehler. Der Fehler (kein `Exception`) wird in `probe` nicht gefangen (`guarded` reicht `Error` weiter, wie vorgesehen) und beendet die Verbindung als Handlerfehler ohne Antwort; ein Client, der einen langen Tabellennamen an `SqlBulkCopy` gibt, erhält keine Antwort statt `208` oder `TDS_UNSUPPORTED_SQL`. Kein Test deckt lange Namen. | Maintainability (Robustheit am Rand des Spec-Bereichs; `SPEC-027`: keine ungefangene Folge defekter Eingaben) | `BulkCopyForms.kt` · `private const val PART = "\\[((?:[^\\]']\|\\]\\]\|'')+)\\]"` | ja (Testfall mit Namen von 5000 Zeichen in `BulkCopyFormsTest`; gefahren im Rig) | Regex mit Alternation unter `+` rekursiert je Zeichen; Eingabelänge ungeprüft |
| F-2 | MEDIUM | `SPEC-036` Form B sagt weiter „Setzung, die Antwort des Servers auf eine unbekannte Tabelle ist nicht aufgezeichnet“. Sie ist aufgezeichnet (M-35, `bulk-name-schema/21` und `/23`, beide Instanzen: ERROR 208 mitten im Batch, Text „Invalid object name …“), und der Server dieses Repos antwortet anders (Fehler vor den Ergebnissen, Text „Tabelle nicht beschrieben“). Die Grenze steht nur im Messprotokoll (Zuordnungstabelle), nicht in der Spezifikation. | `AGENTS.md` §3.8; Reviewer-Skill „Fortschreibung lässt die zweite Fundstelle stehen“ | `spec/spezifikation.md` · „Setzung, die Antwort des Servers auf eine unbekannte Tabelle ist nicht aufgezeichnet“ | nein (Textprüfung) | zweite Fundstelle einer berichtigten Aussage bleibt stehen |
| F-3 | MEDIUM | Die Plan-Tabelle §3 nennt nicht alle berührten Dateien, und der Kopf „Berührte Spec-Stellen“ nennt `SPEC-040` nicht, obwohl der Diff sie ändert. Nicht in der Tabelle: `docs/plan/planning/in-progress/roadmap.md`, `docs/plan/planning/welle-testserver-manuell.md`, die Register `BEO-TDS/messung-am-echten-sql-server-fehlt/state.md` und `BEO-TDS/mitschnitt-luecken-ohne-traeger/state.md`, `docs/plan/planning/done/slice-bulk-load-feste-typen-und-referenzclient.md`, `docs/reviews/2026-11-08-6c0f7f62-…`, `docs/plan/spec-abdeckung.md`; `SPEC-001` steht im Kopf, nicht in der Tabellenzeile der Spezifikation. | Reviewer-Skill „Plan-Tabelle nennt die berührten Dateien nicht“ | Slice · „**Berührte Spec-Stellen:** SPEC-036 (Form B, Probe der Zieltabelle von `SqlBulkCopy`“ | nein | Plan-Tabelle gegen Diff unvollständig |
| F-4 | LOW | Die Zeile `val text = sql.trim(…).removeSuffix(";").trimEnd(…)` in `recognize` ist neu im Diff (vorher Teil von `CatalogForms.normalize`); ohne sie bleibt jeder Test grün (Mutation MC). Ebenso nimmt die Erkennung mehrere Leerzeichen in Literalen der Vorlage an (`'',  ''` in `STRING_AGG(…)` erkannt, gefahren); die Aufzeichnung zeigt das nicht. | Reviewer-Skill „Eigencode, dessen Entfernung keinen Test rot macht“ (als LOW gewertet: kein konkretes Versagen, der Spielraum stammt aus der Vorfassung) | `BulkCopyForms.kt` · `sql.trim(' ', '\t', '\r', '\n').removeSuffix(";")` | ja (Mutation MC) | Eigencode ohne tragenden Test |
| F-5 | LOW | Kein Test bindet die Dateien `probe-*.sql` und `typen-fest.hex` an die Ablage: dass sie dem Beleg gleichen, hat dieser Review einmalig von Hand geprüft (alle gleich). Ändert eine Zeilenenden-Normalisierung oder ein Edit eine Datei, bleiben `BulkCopyFormsTest` und `BulkLoadReaderReferenceClientTest` grün, und der Satz „aufgezeichnete Texte“ in ihren KDocs trüge nicht mehr. | Maintainability; `AGENTS.md` §3.8 (Aussage ≤ Beleg) | `BulkCopyFormsTest.kt` · „Die Texte sind die aufgezeichneten Anfragen“ | ja (Vergleich Datei gegen Ablage, im Scratchpad wiederholbar) | Testdaten ohne Bindung an ihren Beleg |
| F-6 | LOW | Das Drei-Teile-Format (Datenbank, Schema, Tabelle) ist gemessen (M-33), aber nicht gebaut (`TDS_UNSUPPORTED_SQL`); die Grenze steht in `SPEC-036`, im Messprotokoll und in der Nutzer-Doku, ein Träger (Folge-Slice oder Register-Eintrag) existiert nicht, und die DoD-Zeile „Nachzug: … nennen die Grenze nicht mehr“ spricht nur von der alten Grenze. Gleiches gilt für den Unterschied bei einer unbekannten Tabelle (F-2). Zielbild-Lücke ohne Träger, bis die Closure sie paart. | Grundsatz „Spec ist Zielbild“; Slice §6 | Slice · „**Ausgang:** offen bis zur Closure“ | nein | Zielbild-Lücke ohne Träger (zur Closure) |
| F-7 | LOW | `SqlBulkCopyTests.cs` nennt als Gegenstück am echten Server weiter „Messreihe M-8 bis M-12“; die Messreihe dieses Slice (M-33 bis M-36) fehlt in der Klassen-KDoc. | Maintainability (zweite Fundstelle) | `SqlBulkCopyTests.cs` · „(dort: Messreihe M-8 bis M-12, `make vergleichslauf-d`)“ | nein | zweite Fundstelle |
| F-8 | INFO | Der Satz, dass mssql-jdbc (d-migrate) mit Schema und Sonderzeichen nicht gemessen ist, steht im Messprotokoll („Grenzen“, im Absatz der Messreihe) und am Ende des Punktes in `docs/user/testserver-manuell.md`; `SPEC-036` Form B, `einbettung-und-betrieb.md` und `referenz-konfiguration.md` benennen den Client im Satz der Aussage (`SqlBulkCopy`, `Microsoft.Data.SqlClient` 7.1.1), nennen die ungemessene Gegenseite aber nicht. Die Probe selbst ist eine Eigenheit von `SqlBulkCopy`; der Text von `INSERT BULK` für d-migrate ist für `[dbo].[kunden]` in M-28 belegt. | `AGENTS.md` §3.8 | `spec/spezifikation.md` · „Probe der Zieltabelle von `SqlBulkCopy` (gemessen: `Microsoft.Data.SqlClient` 7.1.1“ | nein | Zusage ohne die ungemessene Gegenseite im selben Satz (eingeschränkt durch den Gegenstand der Aussage) |
| F-9 | INFO | Die Schreibweise von Schema und Name im Katalog (`SimCatalog.find`, ohne Beachtung der Schreibweise) ist am Weg der Probe nicht getestet: die Mutation „Schema exakt“ lässt `BulkCopyFormsTest` grün und färbt nur `SimModelTest` rot. Am echten Server ist die Schreibweise des Schemas nicht gemessen (Messprotokoll benennt es), im Katalog ist sie Setzung. | Maintainability | `SimModel.kt` · `SimNames.fold(schema) + "\u0000"` | ja (Mutation) | Auswahleigenschaft nur am Katalog getestet |
| F-10 | INFO | Der `slice-mv` hat im geschlossenen Report `2026-11-08-6c0f7f62-verifikation-…` `open/` zu `in-progress/` nachgezogen; ein Satz dort sagt weiter „Lifecycle `open/`“ neben dem Pfad `in-progress/…`. | Maintainability | `docs/reviews/2026-11-08-6c0f7f62-verifikation-slice-bulk-load-feste-typen-und-referenzclient.md` · „Lifecycle `open/`, alle acht Abschnitte“ | nein | maschinell nachgezogener Zeiger widerspricht dem Satz um ihn |

## Mutationen

Gefahren im Rig an einer Kopie, enge Testklassen, Original unverändert.

| ID | Mutation | Klasse | Ergebnis |
|---|---|---|---|
| Z4 | Namen der FROM-Stelle bei Schema nicht an Gruppe 1 und 2 gebunden (`PART` statt `\1`, `\2`) | `BulkCopyFormsTest` | rot (1 von 5: `abweichende Namen …`) |
| Z6 | `BLANK` = ein Leerzeichen statt `[ \t\r\n]+` | `BulkCopyFormsTest` | rot (4 von 5) |
| Z7 | `find(null, name)` statt `find(schema, name)` | `BulkCopyFormsTest` | rot (1 von 5: `die Probe sucht im genannten Schema …`) |
| Z8 | `size >= TINY` statt `size > TINY` im Leser | `BulkLoadReaderReferenceClientTest` | rot (2 von 9, darunter `bulk-name-feste-typen …`) |
| MA | Kollationsstelle ohne Schema nimmt `[^\]']*` (kein `]]`, keine Bindung) | `BulkCopyFormsTest` | rot (3 von 5) |
| MB | `PART` ohne die Alternative `''` | `BulkCopyFormsTest` | rot (2 von 5) |
| MC | Trimmen und `removeSuffix(";")` in `recognize` entfernt | `BulkCopyFormsTest` | **grün** (F-4) |
| MD | Schema im Katalogschlüssel exakt statt ohne Beachtung der Schreibweise | `BulkCopyFormsTest`, `SimModelTest`, `SimulationBackendBulkTest`, `BulkDispatchTest` | nur `SimModelTest` rot (F-9) |

Sonden im Rig (Wegwerf-Tests, nicht übernommen): `]` und `'` aufgelöst, Schema mit `]`, Unicode (Basic Multilingual Plane, Surrogatpaar, Zeilenumbruch im Namen), `x''); DROP TABLE t;--` (als Name erkannt, im Katalog nicht gefunden, nie ausgeführt), Namensteile mit unterschiedlichem Leerraum an den Stellen (nicht erkannt), Tabulator zwischen Wörtern (erkannt), Nicht-Leerraum-Zeichen `U+00A0` und `U+000B` statt Leerzeichen (nicht erkannt), Anführungszeichen statt Klammern (nicht erkannt), Treffer in zwei Schemata (je Schema die eigene Tabelle, ohne Schema `dbo`). Laufzeit: bei keiner Eingabe über 1 Million Zeichen Verzug, aber F-1 für lange Namen.

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| Messreihe und Ablage (`referenztest-dotnet/Mitschnitt/messung/bulk-merge-katalog/{2022,2025}/bulk-name-*.messung`, `MessungBulkTests.cs`, `MessungBulkBelegTests.cs`) | geprüft, ohne Befund: Probetexte, `insert bulk` und Nachricht 0x07 stimmen mit Spezifikation und Erkennung überein; beide Instanzen gleich; der Client ist in Kopf und Messprotokoll genannt; Grenzen (vier Teile, 128 Zeichen, Zeichen außerhalb der Basic Multilingual Plane, Zeilenumbruch, Schreibweise des Schemas) benannt |
| `BulkColumnType.kt`, `BulkLoadReader.kt` (nur KDoc) | geprüft, ohne Befund: die Typkennungen, Größen und Vorzeichen (TINYINT ohne) gleichen `typen-fest.hex`; die Handvektor-Kennzeichnung für `INTN` 1 Byte, `DECIMALN`, `NCHAR`, `BIGCHAR` bleibt |
| `IntegrationServer.kt`, `SqlBulkCopyTests.cs`, `tools/simulationstest.sh` | geprüft, ohne Befund: zweite Tabelle `s1.bulk]ziel'x` mit eigenem Speicher je Tabelle; vier Theorien und ein Fakt laufen grün; Erwartung 13 Tests und 7 Tabellen stimmt mit dem Lauf; der Lauf selbst bricht bei Abweichung |
| Zusammenspiel mit `SimTable.onInsertBatch` (`ADR-0044`) und den Elementen des Merge `main` | geprüft, ohne Befund: der Weg der Nachricht 0x07 und die Atomarität sind unberührt; der Merge von `main` ist konfliktfrei (Merge-Probe gegen `main` `7412984a`) |
| `docs/user/` (alle fünf Dateien) | geprüft: Fundstellen zum Massenladen sind fortgeschrieben (`einbettung-und-betrieb.md`, `referenz-konfiguration.md`, `testserver-manuell.md`); Treffer auf „nicht gebaut“, „Zielzustand“, „vorläufig“, „bisher“, „noch nicht“, „soll“, „seit Slice“ im Umfeld des Massenladens: keine; ohne Befund außer F-8 |
| Haken, Roadmap, Zustandsfelder | geprüft, ohne Befund: keine Liefer-Haken gesetzt (alle `- [ ]`), kein Rollen-Text in der DoD, Roadmap-Zelle nennt Zustand und Zeiger |
| `harness/README.md`, Hard Rules §3.1 bis §3.8 | geprüft, ohne Befund: keine Zelle geändert, kein Host-Aufruf, keine Suppression, kein Accepted-ADR geändert, `spec/architecture.md` unberührt |
| Öffentliche Fläche (`make flaeche`) | geprüft, ohne Befund |

## Summary

Kein HIGH. Drei MEDIUM: ein Absturz-artiger Fehler der neuen Erkennung bei langen Namen (F-1, Regression gegen `main`), eine stehengebliebene Aussage in `SPEC-036` (F-2) und eine unvollständige Plan-Tabelle (F-3). Die Messung ist echt und trägt: die Probetexte der Tests sind gleich der Ablage, die Erkennung stimmt mit den Rohbytes überein, und alle gefahrenen Mutationen bis auf MC und MD (F-4, F-9) färben Tests rot. Gates, `make flaeche`, `make docs-check` und `make simulations-integration` sind grün.
