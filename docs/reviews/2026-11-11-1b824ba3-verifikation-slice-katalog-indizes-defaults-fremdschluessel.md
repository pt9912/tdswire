# Verifikationsbericht: slice-katalog-indizes-defaults-fremdschluessel (HEAD 1b824ba3)

**Rolle:** Verifier (Modul 11). **Frage:** Bauen wir es richtig, gemessen an Plan, DoD und Entscheidungen? Gegenstand: Zweig `worktree-agent-a05a5b16f4eadd942`, Basis `a4adc874`, HEAD `1b824ba3` (Bau, Review `9ae13838`, Nachbesserung `1b824ba3`). Zusätzlich ein Kurzreview des Leser-Codes der Nachbesserung (`SimDescriptionReader.kt`, `SimElementReader.kt`), den kein zweiter Review gesehen hat. Quellen: Slice `docs/plan/planning/done/slice-katalog-indizes-defaults-fremdschluessel.md`, Folge-ADR Nr. 0046 (auf `main`, Accepted vom Nutzer), [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), [ADR-0041](../plan/adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md), Review `2026-11-09-9ae13838`, SPEC-035, SPEC-036, SPEC-059. Ich habe nichts repariert und keinen Haken gesetzt.

## Eigene Läufe (Belege)

| Lauf | Ergebnis |
|---|---|
| `make clean-build` (einmal, End-Stand des Zweigs) | Exit 0, 571 s; 1341 PASSED, kein roter Testfall (der eine Treffer auf `FAILED` ist der Testname `FAILED_CLOSE_CONNECTION`); alle Gradle-Stages `BUILD SUCCESSFUL`; `SimDescriptionElementsTest > liest unique_constraint …` PASSED |
| `make e2e` | Exit 0, 8 Stufen grün, 341 s (Stufe 7 in 36 s, kein Last-Flake) |
| `make gates FORCE=1` | Exit 0, 171 s; `d-check` 1335 Dateien, 0 Befunde; `gate-zuordnung` 16 Gates; Selbsttests der Stempel 118/95/101/67 Fälle; `stempel-gruppe: tests gruen, Stempel nicht geschrieben` (kein Stempel, wie schon im Review; kein Befund) |
| `make flaeche` | Exit 0 (Docker-Stage `flaeche`, Prüfung und Selbsttest) |
| `make docs-check` | Exit 0, 1336 Dateien, 0 Befunde (nach Anlage dieses Berichts) |
| `tools/matrix-zitate.sh spec/spezifikation.md <clean-build.log>` | Exit 0: 1341 bestandene Testfälle, 1 Zitat, 0 ohne PASSED (Zitate im Diff nicht geändert, daher nicht `make matrix-zitate`) |
| Merge-Probe `git merge-tree --write-tree HEAD main` (`main` = `9f302beb`, enthält Folge-ADR Nr. 0046 und `slice-unbekannte-tabelle-class-16-angleichen`) | **zwei Textkonflikte, beide nur in Dokumenten**: `spec/spezifikation.md` (Änderungsprotokoll, Zeile 2171: beide Seiten haben dort eine Zeile angehängt) und `docs/plan/planning/in-progress/roadmap.md` (Zeile „In Arbeit“: `HEAD` nennt diesen Slice, `main` „keiner“). Code, Tests, Messprotokolle und Nutzer-Doku mergen sauber (auch `ErrorCode.kt`, `ErrorMapperTest.kt`, `MergeFehlerEmbeddingTest.kt` von `main`). Auflösung ist Planner-Hand: beide Protokollzeilen behalten; Roadmap-Zeile auf den Zustand nach dem Merge bringen |
| Semantik-Probe des Merge (Wegwerf-Worktree, nicht im Repo, entfernt): Konflikte mit `--ours` aufgelöst, dann `gradle test integratorTest :testserver:test` im Rig | 1270 PASSED, 10 rot, **alle zehn sind TLS-Tests ohne `build/testkeys/*.p12`** (`FileNotFoundException`; mein Wegwerf-Worktree hatte die Schlüssel der Stage `testkeys` nicht), keiner in Katalog-, Element-, Struktur- oder Leserklassen. Die neuen Abweisungen mit `TDS_SIM_UNKNOWN_OBJECT` (jetzt Class 16, Verbindung offen) bleiben im Merge grün. Keine Aussage über TLS im Merge; die fährt der Planner mit dem Gate nach dem Merge |
| Mutationen einzeln (Abschnitt unten) | 8 von 8 gültigen Mutationen rot |
| Roundtrip `schema reverse` → Testserver-Start mit der Datei (Abschnitt unten) | grün |

## Gesamturteil

**Closure-fähig: ja, unter den Bedingungen B1 bis B6 unten (alle in Planner-Hand, keine am Code).** Der Kern trägt und ist in eigenen Läufen belegt: Antwortbytes der sechs Katalogabfragen stimmen mit den Rohbytes beider Instanzen überein, die Strukturformen D1 bis D4 laufen mit d-migrate 1.12.0 bis `IDENTICAL`, der Roundtrip, den der Implementer nicht gefahren hat, besteht, jede Mutation fällt auf, die Fläche ist durch Folge-ADR Nr. 0046 entschieden. Die acht benannten Grenzen im Register sind wahr; die Bezeichnung „vollständig“ gilt mit einer Einschränkung (V-2).

## DoD-Punkte (Haken: alle offen, keiner vom Implementer gesetzt; ich setze keinen)

| DoD-Zeile | Beleg | Urteil |
|---|---|---|
| Liefer-Punkt 1: Modell und Katalog; ein Test je Abfragegruppe mit Handvektoren aus dem Mitschnitt und Zeilen der Messung | `SimElements.kt`, `SimModel.kt`, `ElementAnswers.kt`, `CatalogForms.kt`; `SimulationBackendCatalogElementsTest` (Zeilen M-21), `DMigrateWire.kt` (Handvektoren), `SimElementsTest`; clean-build grün; Antwortbytes selbst dekodiert (Abschnitt Draht) | erfüllt; Haken Planner |
| Liefer-Punkt 2: DDL-Formen erkannt, Callback, Katalog zeigt danach die Struktur; Formen am Soll gemessen | Formen D1 bis D4 (`StructureParts`, `ConstraintParts`, `ColumnParts`, `CheckExpressions`, `StructurePlanner`); `SimulationBackendElementsStructureTest`; die von d-migrate gesendeten Texte stehen im Bericht meines Laufs (`CREATE TABLE … CONSTRAINT [uq_…] UNIQUE`, `DEFAULT 1`, `DEFAULT N'neu'`, `CHECK`, `FOREIGN KEY … ON DELETE CASCADE`, `CREATE INDEX`) | erfüllt; Haken Planner |
| Liefer-Punkt 3: Testserver hält die Elemente, liest sie aus der Datei; `schema reverse`/`compare`/`migrate --execute` Exit 0 samt Nachprüfung; Messprotokoll mit Rechner, Last, Digest | eigener Lauf: `migrate --execute` Exit 0, `statementsAttempted` 3, `compare` danach `IDENTICAL`; Messprotokoll `docs/messungen/katalog-indizes-defaults-fremdschluessel-d-migrate-2026-11-09.md` nennt Host, Load, Digest, Abbild-Digest. Einschränkung V-1 (Stand des Protokolls `c285cf21`, nicht HEAD) | erfüllt mit V-1; Haken Planner |
| `make gates` grün | Exit 0 mit `FORCE=1` | erfüllt |
| Review durchgeführt, Report liegt vor | `2026-11-09-9ae13838-review-…`, 2 HIGH, 6 MEDIUM, 3 LOW; Behebung in `1b824ba3` (jede Zeile gegen den Diff geprüft: F-1 Slice §1/§6 und Protokollzeile, F-2 Kommentare, F-3/F-6/F-9/F-10/F-11/F-14 Grenzen in SPEC-035/036 und Nutzer-Doku, F-4/F-15 Register, F-5 Test und Mutation rot (m5 hier), F-7 Leser, F-8 Plan-Kopf) | erfüllt; der neue Leser-Code hat nur diesen Kurzreview |
| Haken setzt der Planner (`BEO-TDS/haken-vor-verifikation-gesetzt`) | alle `- [ ]` offen (Slice Zeilen 58 bis 73) | erfüllt |
| Doku-Update bei öffentlichem Vertrag | SPEC-035/036/059, Änderungsprotokoll, `docs/user/` (README, einbettung-und-betrieb, referenz-konfiguration, testserver-manuell), `AnleitungTest` und `FlaecheEmbeddingTest` übersetzen die Beispiele | erfüllt |
| Closure-Notiz mit Steering-Loop-Lerneintrag | §7 „Wird bei der Closure gefüllt“ | **offen: Planner (B2)** |
| Register fortgeschrieben | neues `BEO-TDS/katalog-elemente-grenzen-der-angleichung/` (`observation.md`, `state.md`, `evidence/`), Zeiger in zwei weiteren Beobachtungen | erfüllt; Zeiger aus `slice-abnahme-d-migrate` fehlt (B4) |
| Jedes Risiko aus §6 trägt einen Ausgang | drei Risiken stehen auf „offen bis zur Closure“, eines „weiter offen“ (Träger Register), zwei „entfallen“ | **offen: Planner (B2, B3)** |
| Drei Paarungen | Anker: keine Regel verkörpert (kein `liegt in`), Folge-Slice: vorgeschlagen, nicht angelegt, Register: getragen | Planner nach Entscheid (B3, B5) |

## Draht: Antwortbytes gegen die Rohbytes (Schwerpunkt 3)

Dekodiert aus `referenztest-dotnet/Mitschnitt/messung/bulk-merge-katalog/{2022,2025}/katalog-abfragen.messung` mit einem eigenen Python-Dekoder (COLMETADATA mit Cek-Zähler, ORDER, ROW/NBCROW, PLP): Abfrage 5 (Indizes) vollständig, dazu Fremdschlüssel, Prüfbedingungen, Eindeutigkeiten, Primärschlüssel in Spaltenkopf und Zeilen.

| Abfrage | Rohbytes (2022 und 2025 gleich) | `CatalogForms` / Test |
|---|---|---|
| Indizes | `index_name` nvarchar nullbar, `is_unique`, `has_filter` BITN, `filter_definition` PLP nullbar, `type` tinyint nicht nullbar, `column_name` nullbar, `key_ordinal` tinyint nicht nullbar, `is_descending_key`, `is_included_column` BITN; Zeilen `['ix_bestellung_status',0,0,NULL,2,'Menge',0,0,1]`, dann `[…,'Status',1,0,0]`; Eindeutigkeit als `['uq_katalog_kunde_email',1,0,NULL,2,'Email',1,0,0]` | gleich (Test Zeilen 149/150/175), Reihenfolge eingeschlossene vor Schlüsselspalte |
| Fremdschlüssel | sieben Spalten, `column_name`, `referenced_column`, beide Aktionen nullbar, `constraint_column_id` int; Zeile `['fk_bestellung_kunde','KundeId','katalog_kunde','Id','CASCADE','NO_ACTION',1]` | gleich |
| Prüfbedingungen | `['ck_bestellung_menge','([Menge]>(0))']`, `definition` PLP nullbar | gleich |
| Eindeutigkeiten, Primärschlüssel | `column_name` nullbar; `['uq_katalog_kunde_email','Email']`, `['Id']` | gleich |

Grenze unverändert benannt: Text steht als `nvarchar(4000)`, wo der Server `nvarchar(128)` und `nvarchar(max)` sendet ([ADR-0041](../plan/adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) Entscheidung 7).

## Mutationen (Schwerpunkt 4; einzeln, Rig-Image `impl-katalog-rig:lokal` unverändert benutzt, `--user` 1000, Gradle offline, Quelle nach jedem Lauf zurückkopiert, `git status` sauber)

| Nr | Mutation | Testklassen | Ergebnis |
|---|---|---|---|
| m1 | Indexabfrage: Schlüsselspalten vor die eingeschlossenen | `SimulationBackendCatalogElementsTest` | rot (`Indizes und Eindeutigkeiten kommen aus der Tabelle`) |
| m2 | `default_definition` als leerer Text statt Wert | dto. | rot (`die Spalten melden ihren Standardwert in der Form des Servers`) |
| m3 | `<` und `>` aus dem Scanner | `SimulationBackendElementsStructureTest`, `StatementScannerTest` | rot (7) |
| m4 | `CREATE INDEX` als `ALTER_TABLE` | `SimulationBackendElementsStructureTest` | rot (2) |
| m5 | e5: `indexNameTaken` ohne die Namen der Eindeutigkeiten (im Review grün, F-5) | dto. | **rot** (`Elemente gegen den Katalog werden abgewiesen`): F-5 behoben |
| f7a | Leser ignoriert den Namen aus `unique_constraint` | `SimDescriptionElementsTest` | rot |
| f7b | Leser nimmt `unique_constraint` ohne `unique: true` an (`flag && !flag`) | dto. | rot (die erste Fassung der Mutation, `if (false)`, kompiliert nicht: kein Test; wiederholt als f7b2) |
| f7c | `unique_constraint` fehlt in `COLUMN_READ` (unbekanntes Feld) | dto. | rot |

## Kurzreview des Leser-Codes (`SimDescriptionReader`, `SimElementReader`, `SimElementFields`)

- **Validierung:** `unique_constraint` ohne `unique: true` ist Ladefehler mit Pfad der Spalte; leerer Name wird von `SimTreeAccess.text` abgewiesen (Test deckt Pfad); ohne `unique_constraint` gilt `uq_<Tabelle>_<Spalte>`. Ziele von Fremdschlüsseln werden nach allen Tabellen geprüft, Spaltenlisten gegen die Tabelle, Aktionen aus fester Tabelle (`restrict` als `NO_ACTION`).
- **Bestehende Dateien:** `testserver/resources/manuell/sim.yaml` (Felder ohne Elemente) lädt unverändert (Server A und B starteten damit); die Dateien aus Aufgabe 7 laden.
- **Fehlermeldungen:** knapp und mit Pfad. Ein Name, der mit dem eines Index oder einer anderen Bedingung zusammenfällt, meldet „Name einer Bedingung oder eines Index doppelt“ am Pfad der **Tabelle**, nicht der Spalte mit `unique_constraint` (V-3, INFO).
- **`runCatching` (Review F-16):** unverändert. Nach den Vorprüfungen des Lesers (Spalten, Schlüssel, Spaltenlisten, Namen nicht leer) kann `SimTable` nur noch an `SimElementChecks.validate` scheitern: doppelte Bedingung, Index gegen Eindeutigkeit gleichen Namens, fehlende Spalte (vom Leser vorher abgefangen). Die Meldung trifft damit die Ursache; sie träfe sie nicht bei einem späteren neuen `require` im Konstruktor. `runCatching` fängt auch `Error` (zum Beispiel `OutOfMemoryError`) und meldet sie als Namensdopplung; INFO, kein Befund am heutigen Stand.
- **Gegenseite (Schreiben):** was `schema reverse` für `unique: true` schreibt (`unique_constraint: uq_lieferanten_Name`), liest der Leser jetzt; Name und Wert des Roundtrips sind unten belegt.

## Roundtrip `schema reverse` → Testserver mit der geschriebenen Datei (Schwerpunkt 1)

d-migrate 1.12.0, Image `ghcr.io/pt9912/d-migrate@sha256:308f27753036c9cc423f716b9fc4c9e68016543a8003ecd2f8282e05a2ee6491`, Testserver-Image frisch aus dem End-Stand (`tdswire-testserver:verif-katalog`), `make testserver-up` mit `TESTSERVER_NAME=verif-ts-a`/`-b`, Ports 24931/24932, `--network host --user 1000:1000`, Soll = `soll-elemente.yaml` aus Aufgabe 7 (Eindeutigkeit, Standardwerte, Fremdschlüssel mit `on_delete: cascade`, Index, Prüfbedingung). Host Linux 6.8.0-147, Load Average (1 Minute) 4 bis 5 laut `uptime`.

| Schritt | Exit | Ergebnis |
|---|---|---|
| Server A (Beschreibung `sim.yaml`): `schema compare` mit Soll | 1 | `DIFFERENT`, 2 Tabellen fehlen |
| `schema migrate --execute --report` | 0 | `status: ok`, `statementsAttempted` 3, `transactionRolledBack` false |
| `schema compare` | 0 | `IDENTICAL` |
| `schema reverse --output ist-elemente.yaml` | 0 | Datei mit `default: 1`, `default: neu`, Index, `fk_auftraege_LieferantId` (`on_delete: cascade`), Prüfbedingung `'"Menge">(0)'`, `lieferanten.Name` mit `unique: true` **und** `unique_constraint: uq_lieferanten_Name` |
| Server B startet mit `TESTSERVER_BESCHREIBUNG=ist-elemente.yaml` | 0 | bereit (im Review vor der Nachbesserung: Abbruch „Feld unbekannt“, F-7) |
| `schema compare` mit `soll-elemente.yaml` und mit `ist-elemente.yaml` gegen B | 0, 0 | `IDENTICAL` beide |
| `schema reverse` gegen B, `diff` mit der Datei von A | 0 | **byte-gleich** |

Der Roundtrip besteht für alle fünf Elementarten.

## Die Grenzen im Register (Schwerpunkt 2): wahr, mit einer Lücke

Eigene Läufe gegen den Testserver (Exit jeweils 5, `Unsupported SQL.`, `statementsAttempted` 1):

| Register-Punkt | Befund |
|---|---|
| 2 `DROP CONSTRAINT IF EXISTS` | **wahr, reproduziert**: zweites `schema migrate --execute` mit demselben Soll sendet als erste Anweisung `ALTER TABLE [auftraege] DROP CONSTRAINT IF EXISTS [ck_auftraege_menge]`, dann `WITH CHECK ADD CONSTRAINT …`. Gilt ebenso für einen Server, der aus der zurückgelesenen Datei startete (Server B) |
| 1 Standardwert an vorhandener Spalte | **wahr, reproduziert** an frischem Server: erster gesendeter Text `DECLARE @df sysname, @sql nvarchar(max); SELECT @df = dc.name FROM sy…`, danach `ALTER TABLE [bestellungen] ADD CONSTRAINT [df_bestellungen_Betrag] DEF…` |
| 3 Index `unique: true` | **wahr, reproduziert** an frischem Server: gesendet `CREATE UNIQUE INDEX [ux_kunden_name] ON [kunden] (…)`, Bericht `POST_EXECUTE_DRIFT`, Exit 5 |
| 4 Prüfbedingung roh | stimmt mit dem Reader-Code überein (`SimElementReader`: der Text der Datei geht unverändert in den Katalog); `schema compare` meldet trotzdem `IDENTICAL`, weil d-migrate normalisiert (Roundtrip) |
| 5 bis 8 | nicht am Lauf prüfbar (Setzungen, EULA-Lauf); als Setzung gekennzeichnet in SPEC-035/036 |

**Lücke V-2 (MEDIUM, Beschreibung, keine Code-Folge):** Punkt 2 maskiert Punkt 1 und 3, sobald das Soll eine Prüfbedingung führt: ein `schema migrate` gegen einen Server mit Prüfbedingung endet immer an der ersten Anweisung (`DROP CONSTRAINT`), gleich was sonst im Soll steht. Die Register-Aussagen zu 1 und 3 sind nur an einem Server **ohne** Prüfbedingung belegt (so gemessen, Zeilen c1, d1 im Messprotokoll, und von mir wiederholt). Ob Punkt 3 überhaupt eine Abweichung vom echten Server ist, ist offen: das Register sagt „am echten Server nicht gemessen“; d-migrate liest ein `CREATE UNIQUE INDEX` an den Katalogabfragen (`is_unique` 1 in der Indexabfrage, in der Abfrage der Eindeutigkeiten fehlend, die Zeilen entsprechen M-21) als Eindeutigkeit, sodass `POST_EXECUTE_DRIFT` am echten Server gleichermaßen zu erwarten ist. Das Register nennt das als Abweichung, ohne die Möglichkeit zu nennen, dass es keine ist.

### Welche Grenzen trifft `slice-abnahme-d-migrate`?

Der Validator-Lauf führt `schema migrate --execute` nach der Anleitung. Mit dem mitgelieferten Soll (`sim.yaml`: keine Elemente) trifft ihn keine Grenze. Er trifft sie, sobald er den Weg von Aufgabe 7 geht oder `migrate` wiederholt (Idempotenzprobe): **Punkt 2 immer, sobald das Soll eine Prüfbedingung führt und `migrate` zum zweiten Mal läuft** (das ist der wahrscheinlichste Fund), Punkt 1 bei einem Soll mit Standardwert an einer vorhandenen Spalte, Punkt 3 bei einem Index mit `unique: true`. `slice-abnahme-d-migrate` nennt das Register nicht (Grep ohne Treffer); das Register nennt ihn als Auslöser der Sichtung.

### Empfehlung zum Folge-Slice

**Ja, anlegen, und vor `slice-abnahme-d-migrate` einordnen, in zwei Slices statt einem:**

1. **`ALTER TABLE … DROP CONSTRAINT [IF EXISTS]` (Form D5)**: klein (Parser, Planer, Katalog entfernt Prüfbedingung, Eindeutigkeit, Fremdschlüssel oder Standardwert mit dem Namen, Tests, Mutationen), schließt Punkt 2, den der Validator ohne Soll-Besonderheit trifft. Nach dem Maßstab „Spezifikation ist Zielbild“ ist das Schuld. Vorbedingung: die Form am echten Server messen (Antwortart `ALTER_TABLE`, Fehlerfälle `IF EXISTS` mit und ohne Treffer), sonst bleibt der Abschluss Setzung.
2. **Text mit `DECLARE @df … EXEC sp_executesql`**: größer (Variablen und dynamisches SQL im Scanner, Auswahl des vorhandenen Standardwerts im Text), mit eigener Messung, und nur nötig, wenn der Validator einen Standardwert an vorhandenen Spalten verlangt. Zurückstellen und über das Register triggern.

Punkt 3 vor jedem Bau am echten Server messen (Vergleichslauf D, EULA-Entscheid des Nutzers); möglicherweise keine Abweichung.

## Entscheidungs-Konformität

| Entscheidung | Prüfung | Urteil |
|---|---|---|
| Folge-ADR Nr. 0046 (Accepted, Option A: Fläche wie gebaut) | vier Typen `SimUniqueKey`, `SimForeignKey`, `SimIndex`, `SimCheck`, fünf Felder, `onStructure` mit einer `SimTable`: alle im Code (`SimElements.kt`, `SimModel.kt`, `SimulationBackend.kt`), SPEC-059-Liste und Änderungsprotokoll (Inkompatibilität `onStructure`, „vor der ersten Veröffentlichung“) stimmen; `make flaeche` grün; die Mitglieder und die Signatur fängt nur der Einbettungstest (`FlaecheEmbeddingTest`, `AnleitungTest`, `StatementKindEmbeddingTest` PASSED in clean-build), wie die ADR als Lücke benennt | konform. Auf dem Zweig liegt Folge-ADR Nr. 0046 noch nicht (nur auf `main`); Slice §1 und §6 sagen „Folge-ADR wird geschrieben“, nach dem Merge ist sie geschrieben (B1) |
| [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Entscheidung 4 (öffentliche Namen) | nur die Formulierung „kein neuer Typname“ wird für die Katalog-Elemente durch Folge-ADR Nr. 0046 abgelöst; [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) unverändert (immutable) | konform |
| [ADR-0041](../plan/adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) Entscheidung 7 (`SqlType` wächst nicht für `nvarchar(max)`, `sql_variant`) | Text bleibt `UnicodeText`, `sql_variant` in der Spalten-Abfrage nicht gebaut; als Grenze im KDoc benannt | konform |
| AGENTS.md §3.1 bis §3.8 | Docker-only gewahrt; kein `@Suppress` (detekt grün); keine Gate-Änderung; Kommentare der Nachbesserung im Indikativ (`TableStore.kt`, `TableStoreTest.kt`); Setzungen im Satz gekennzeichnet; `git mv` nicht betroffen | konform |
| Plan-Tabelle §3 gegen Diff (63 Dateien) | jede Datei im Diff steht in der Tabelle (Bibliothek, Tests, Leser, Spec, vier Nutzer-Dokus, Messprotokolle, Roadmap, Welle, Register, fünf `done/`-Zeiger, Reviews); `sim.yaml` unverändert wie zugesagt; `harness/README.md` nicht berührt (keine neuen Zellen über 200 Zeichen) | konform |
| Zweite Fundstellen / `docs/user/*` | Grep auf veraltete Aussagen („leer“, „ignoriert“, „kennt er nicht“ für Standardwert, Eindeutigkeit, Fremdschlüssel, Index, Prüfbedingung) ohne stale Treffer (die Treffer sind die neue Aussage, „alles andere als leeres Ergebnis“); `onStructure`-Beispiele in `einbettung-und-betrieb.md` stimmen mit der neuen Signatur; die Grenzen 1 und 2 stehen in `testserver-manuell.md` Zeilen 659/660 und `einbettung-und-betrieb.md` Zeile 329, Grenze 3 in SPEC-036 | konform; Hinweis V-4 |

## Abweichungen und Befunde

| ID | Schwere | Befund | Folge |
|---|---|---|---|
| V-1 | LOW | Das Messprotokoll nennt als Stand `c285cf21` und das Abbild `671e6ce5…`; HEAD hat Leser (`unique_constraint`) und Namensraum von Index und Eindeutigkeit seither geändert. Zeile b3 („zurückgeschriebene Datei“) beschreibt `reverse` und `compare`, nicht den Start des Testservers mit der Datei; genau dieser Start brach vor der Nachbesserung ab. | Mein Roundtrip oben ist der Beleg am End-Stand; Planner entscheidet, ob er in das Messprotokoll als Zeile (Stand `1b824ba3`) aufgenommen wird (B6) |
| V-2 | MEDIUM | Register Punkt 1 und 3 sind nur an Servern ohne Prüfbedingung belegt (Punkt 2 maskiert sie); Punkt 3 ist möglicherweise keine Abweichung vom echten Server. | Register-Text schärfen (B4), vor Bau messen |
| V-3 | INFO | Fehlerpfad des Lesers bei Namenskollision mit `unique_constraint` zeigt auf die Tabelle statt auf die Spalte. | keine Folge nötig |
| V-4 | INFO | Slice §6 enthält „Round-Trip … für übrige Felder weiter offen“; der Roundtrip meines Laufs belegt `unique_constraint`, `default`, `references`, Index, Prüfbedingung, nicht die übrigen Felder von `schema reverse` (`description`, Typen wie `date`/`decimal` sind im Beispiel enthalten und laden). | Ausgang des Risikos vom Planner |

Keine DoD-Verletzung gefunden, die Test und Review nicht sähen; keine Behauptung ohne Beleg: die Aussagen des Implementers (Zahl der Mutationen, Sensor-Läufe) habe ich mit eigenen Läufen bestätigt.

## Bedingungen für die Planner-Closure

- **B1 (Merge, Reihenfolge):** `main` (`9f302beb`) einmal in den Zweig oder umgekehrt zusammenführen. Zwei Dokumentkonflikte lösen: Änderungsprotokoll `spec/spezifikation.md` (beide Zeilen behalten, Datum und Reihenfolge ordnen) und Roadmap-Zeile „In Arbeit“. Danach `make gates` auf dem Merge-Stand (TLS-Tests, `d-check` mit Folge-ADR Nr. 0046 und Verweisen im Slice).
- **B2 (Closure-Notiz und Risiken):** §7 füllen (Steering-Loop-Lerneintrag oder „keine Beobachtung/Regel“), und für die drei Risiken „offen bis zur Closure“ je einen Ausgang eintragen (Neue öffentliche Namen: eingetreten, Folge-ADR Nr. 0046 liegt vor; Form der Antworten: Ausgang nach Messung M-21 gesetzt; DDL-Formen ungemessen: entfallen, gemessen am Soll mit d-migrate 1.12.0).
- **B3 (Folge-Slice):** Entscheid des Nutzers zur Empfehlung oben; bei Ja die Slices anlegen und im Risiko „Grenzen gegen das Zielbild“ den Träger statt „vorgeschlagen, nicht angelegt“ nennen.
- **B4 (Register und Abnahme):** Register `katalog-elemente-grenzen-der-angleichung` um den Satz aus V-2 ergänzen (Maskierung durch Punkt 2; Punkt 3 möglicherweise ohne Abweichung); in `slice-abnahme-d-migrate` §4 oder §6 auf das Register zeigen, damit die Paarung in beide Richtungen steht.
- **B5 (Paarungen):** Anker: keine Regel verkörpert, das in §7 sagen; Folge-Slice nach B3; Register getragen.
- **B6 (optional):** Messprotokoll um eine Zeile für den End-Stand ergänzen (mein Roundtrip), oder den Stand `c285cf21` im Kopf ausdrücklich als Stand der Läufe belassen.
- Haken der drei Liefer-Punkte und der Nachweis-Pflichten setzt der Planner nach B1 bis B5; danach `git mv` nach `done/` als eigener Commit (AGENTS.md §3.3, Punkt 2).

## Zeit

Verifier-Lauf 15:18 bis etwa 15:55 Uhr (rund 37 Minuten Wanduhr). Größte Posten: `make clean-build` 571 s, `make e2e` 341 s, Mutationen 8 Läufe zusammen rund 8 Minuten (je Lauf 40 bis 90 s), `make gates FORCE=1` 171 s, Semantik-Probe des Merge 184 s, Roundtrip und Grenzen rund 3 Minuten (Image-Bau des Testservers im Rig-Cache). Lesen, Dekodieren der Rohbytes und Bericht der Rest. Es lief gleichzeitig ein weiterer Gradle-Agent (Last 4 bis 7); keine künstliche Last. `make docs-check` zuletzt grün.
