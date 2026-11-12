# Verifikationsbericht: slice-katalogspalten-smallint-tinyint-angleichen (HEAD fa7adeaa)

* Rolle: Verifier (Modul 11), frischer Kontext. Gegenstand: Slice `slice-katalogspalten-smallint-tinyint-angleichen` (`in-progress/`, Zweig `worktree-agent-a8a6593e817bb4aaf`, HEAD `fa7adeaa`, enthält `main` bis `66fee6c1`). Frage: Bauen wir es richtig, gegen Plan, DoD und Entscheidung.
* Maßstab: DoD §2, Plan §3, [ADR-0041](../plan/adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) (Accepted), [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), [ADR-0021](../plan/adr/0021-oeffentlicher-integrations-einstieg.md), [SPEC-002](../../spec/spezifikation.md#spec-002--interner-query-vertrag), [SPEC-003](../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog), [SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends), [SPEC-059](../../spec/spezifikation.md#einstieg-des-integrators-vertrag-zu-spec-059); [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-QA-05](../../spec/lastenheft.md#tds-qa-05--wartbarkeit).
* Grundsatz des Nutzers: „Spec ist Zielbild“, der echte SQL Server ist die Referenz.
* Verifiziert wird die Zusage, nicht der Diff; Belege sind eigene Läufe. Am Produktivcode nichts geändert (Mutationen wurden nach jedem Lauf per `git checkout` zurückgenommen; `git status` danach sauber). Haken habe ich nicht gesetzt.
* Der Review `2026-11-08-e182b838-review-slice-katalogspalten-smallint-tinyint-angleichen.md` deckt `e182b838`; die Nachbesserung `bd66e2dc` (und die Merges `64210c3c`, `fa7adeaa`) hat keinen eigenen Review. Ersatzweise habe ich sie unten gegen F-1 bis F-6 einzeln geprüft.

## Eigene Läufe (Belege)

Host: Lastmittel 5 bis 17 durch fremde Läufe (kein ruhiger Host); Einzelmessungen. Alles im Vordergrund bzw. mit Warten auf das Log.

| Lauf | Ergebnis |
|---|---|
| `make clean-build` (genau einmal, auf dem End-Stand `fa7adeaa`) | Exit 0, `BUILD SUCCESSFUL`; 1263 `PASSED`, 0 rote Testfälle (`:test`, `:testserver:test`, `integratorTest`); 26 min 58 s real (davon `:test`-Stufe 21 min 54 s unter Last) |
| `make gates FORCE=1` | Exit 0 in 31 s. **Cache-Falle:** 89 Docker-Schritte `CACHED` (identische Quellen wie beim Implementer und Reviewer); der Lauf belegt die Verdrahtung der Gates, nicht die Frische der Tests; den Frischbeleg trägt `make clean-build` und `make matrix-zitate` (baut die Stage `test` neu). `gate-zuordnung`: 16 Gates, Selbsttest 33 Fälle |
| `make flaeche` | Exit 0 (Selbsttest und Prüfung) |
| `make docs-check` | Exit 0, 1269 Dateien, 0 Befunde (vor Anlage dieses Berichts) |
| `make matrix-zitate` | Exit 0, 1263 bestandene Testfälle im Bericht, 321 Zitate, 0 ohne PASSED, 0 Fremdformen; 25 min 10 s |
| `make e2e` | Exit 0, 8 Stufen grün, 363 s (Stufe 7 `Synchronitaet` 38 s, **kein Flake**, keine Wiederholung nötig) |
| `make spec-abdeckung` | nicht gefahren (kein Gate); `docs/plan/spec-abdeckung.md` ist in `fa7adeaa` vom Implementer neu erzeugt, ich habe die Zahlen nicht nachgerechnet |
| Messdateien selbst dekodiert | siehe Abschnitt Draht |
| Mutationen (7 gefahren, siehe unten) | alle 7 rot, in den erwarteten Testklassen |
| d-migrate (Digest `sha256:308f27753036…`) gegen `make testserver-up` (Name `verif-katalog-ts`, Port 14341) und gegen die Basis `66fee6c1` (Name `verif-katalog-base`, Port 14342) | Exit-Codes und Ausgaben gleich, siehe Abschnitt d-migrate; Container und Test-Images danach entfernt, das Basis-Worktree entfernt |

Befehle, Logs und Skripte liegen im Scratchpad `/tmp/claude-1000/-Development-tdswire/3768ddc4-cff6-4152-b0be-815e9ed5f5cb/scratchpad/verif-katalog/` (nicht im Repo).

## Gesamturteil

**Closure-fähig: ja, unter den Bedingungen B1 bis B4 unten (alle in Planner-Hand, keine am Code).** Der Kern trägt und ist in eigenen Läufen belegt: der Draht stimmt Byte für Byte mit beiden Aufnahmen (2022 und 2025) überein, `make clean-build` ist frisch grün, alle Mutationen färben Tests rot, der d-migrate-Lauf ist gegenüber der Basis unverändert, `make flaeche` und `make docs-check` sind grün. Die sechs Findings F-1 bis F-6 des Reviews sind behoben. Offen sind Wortlaut-Nachzüge im Slice (überholter DoD-Punkt 3) und die Pflichten der Closure-Notiz.

## DoD-Punkte

Haken: keiner gesetzt (`grep -c '\- \[x\]'` = 0), die Regel `BEO-TDS/haken-vor-verifikation-gesetzt` ist eingehalten. Ich setze keine.

| # | DoD-Punkt | Urteil | Beleg |
|---|---|---|---|
| 1 | Öffentliche Mitglieder und alle Auswerter | **erfüllt** | `SqlType.Int16`, `SqlType.UInt8` (`SqlType.kt`), JVM-Klasse `Int`, Bereichsprüfung in `ValueMapping.narrow` (`VALUE_OUT_OF_RANGE`, Kategorie `TDS_UNSUPPORTED_TYPE`, wie SPEC-002 sagt) und im Draht in `ValueSupport`; Auswerter: `ColumnMapping`, `ValueMapping`, `TypeInfoWriter`, `ValueWriter`, `SimValues`, `SimIntegers` (neu), `BulkLoads`, `CatalogAnswers.typeOf`, `RowRules`; Gegenprobe Erzeuger (`RpcParameters`, `BulkLoadReader`, `StructureParts`) bewusst unberührt, in SPEC-035 und Slice benannt. SPEC-059 nennt beide Namen, Änderungsprotokoll trägt „Inkompatibel für ein Kotlin-`when` ohne `else` über `SqlType` in fremdem Code“; SPEC-003 hat sie im Mindestkatalog; `FlaecheEmbeddingTest` meldet je Typ eine Spalte und zieht das erschöpfende `when` nach (Mutation M1 rot dort). `make flaeche` grün (eigener Lauf). Grenzwerte: `ValueMappingTest > Int16 und UInt8, Raender …` (−32768, 32767, 0, 255, Handvektoren `D1 00 80`, `D1 FF 7F`, `D1 01 FF`), `… weisen Werte ausserhalb des Bereichs … ab` (−32769, 32768, −1, 256 in fester und N-Form), `SimValuesTest`, `SimulationBackendBulkTest`, `TableStoreTest`; alle in 1263 PASSED |
| 2 | Katalogspalten | **erfüllt** | Draht gegen die Messdateien selbst gelesen (Abschnitt Draht): `max_length` INT2 fest nicht nullbar, `precision`/`scale` INT1 fest nicht nullbar, `is_nullable` und `is_computed` BITN nullbar, `is_identity` BIT nicht nullbar, `column_id` INT4 fest; der Bau (`CatalogForms.COLUMNS`) hat genau diese Typen. `KatalogSpaltenWireTest` prüft Typ und Nullbarkeit je Spalte auf den Rohbytes (eine Testmethode, sieben Assertionen, nicht ein Test je Spalte; die DoD-Zeile „je Spalte ein Test“ ist damit dem Sinn nach erfüllt, dem Buchstaben nach nicht: Planner entscheidet, ob das genügt — ich halte es für genügend, weil die Mutationen X3, M2, M6 jeweils genau diesen Test allein färben). Maße von `bigint`, `bit`, `time`, `datetime2`, −1 (M-17, M-18) unverändert (`git diff` an `CatalogAnswers` nur `smallint`/`tinyint` und KDoc). SPEC-035 nennt die Typen statt „bekannte Abweichung `Int32`“. Zeile M-16 im Messprotokoll steht auf „angeglichen“ mit Träger |
| 3 | Katalogmaße von `smallint` und `tinyint` | **erfüllt, Wortlaut überholt** | siehe B1: der Punkt ist als Entscheidungspunkt („Nutzer entscheidet vor dem Bau“) gestellt, die Messung liegt aber vor: `MessungBulkBelegTests` (Theorie über beide Stufen, `katalog-abfragen/03/zeilen`) erwartet `('Klein','smallint',2,5,0,…` und `('Winzig','tinyint',1,3,0,…`; M-17 nennt sie; der Bau (`SMALLINT_BYTES`=2, Precision 5; `TINYINT_BYTES`=1, Precision 3, Scale 0) stimmt. Eine Nachmessung ist nicht nötig, `referenztest-dotnet` ist im Diff unberührt. Die KDoc von `CatalogAnswers` sagt jetzt „gemessen“ |
| N1 | `make gates` grün | **erfüllt** | `FORCE=1` Exit 0 (aus dem Cache, siehe Tabelle), `make clean-build` Exit 0 frisch, `make matrix-zitate` baut die Stage `test` frisch: 1263 bestanden, `make flaeche`, `make docs-check`, `make e2e` grün |
| N2 | Review liegt vor | **erfüllt, mit Einschränkung** | Review `e182b838` liegt vor (0 HIGH, 5 MEDIUM, 1 LOW, 4 INFO); die Nachbesserung `bd66e2dc` ist ohne eigenen Review, ersatzweise von mir einzeln geprüft (Abschnitt Review-Findings). Der Rollenwechsel ist eingehalten (Reviewer ≠ Implementer ≠ Verifier) |
| N3 | Haken vom Planner nach der Verifikation | **offen, vorschriftsgemäß** | 0 Haken gesetzt |
| N4 | Doku-Update bei Vertragsberührung | **erfüllt** | SPEC-002/003/035/059, `docs/user/einbettung-und-betrieb.md`, `docs/user/testserver-manuell.md`, Matrix 2a, Messprotokoll; `docs-check` 0 Befunde. Zweite Fundstellen gegrept: „Breiten unter 32 Bit“ (SqlType.kt, SPEC-003, SPEC-059 nachgezogen), „bekannte Abweichung `Int32`“ (nur noch in der Zeile M-16 als Zitat der früheren Setzung, gewollt), `max_length` in `docs/user` (nur YAML-Beispiele ohne Bezug) |
| N5 | Closure-Notiz mit Lerneintrag | **offen (Planner-Closure)** | §7 trägt „Wird bei der Closure gefüllt.“ |
| N6 | Register fortgeschrieben, Risiken mit Ausgang | **offen (Planner-Closure)** | §6: sechs Risiken, alle „offen bis zur Closure“; Vorschläge unten |
| N7 | Drei Paarungen | **offen (Planner-Closure)** | Anker/Folge-Slice/Register: erst bei der Closure prüfbar |

## Draht gegen die Messdateien (selbst nachgelesen)

Ich habe die Aufnahmen `referenztest-dotnet/Mitschnitt/messung/bulk-merge-katalog/{2022,2025}/katalog-abfragen.messung` mit einem eigenen kleinen Dekodierer (`dec.py` im Scratchpad) gelesen: Nachricht 18 (Server, `810e00…`, 14 Spalten). COLMETADATA hier mit zwei Bytes hinter der Spaltenzahl (CekTable-Zähler 0), dann je Spalte UserType (4), Flags (2), Typkennung. Ergebnis, 2022 und 2025 gleich:

| Spalte | Typkennung am echten Server | Flags-Bit 0 (nullbar) | Bau (`CatalogForms.COLUMNS`) | Urteil |
|---|---|---|---|---|
| `max_length` | `0x34` INT2 fest | nein | `Int16`, nicht nullbar → `Int2Type` | gleich |
| `precision`, `scale` | `0x30` INT1 fest | nein | `UInt8`, nicht nullbar → `Int1Type` | gleich |
| `is_nullable` | `0x68` BITN | ja | `Bool`, nullbar → `BitNType` | gleich |
| `is_identity` | `0x32` BIT fest | nein | `Bool` nicht nullbar → `BitType` | gleich |
| `is_computed` | `0x68` BITN | ja | `Bool`, nullbar → `BitNType` | gleich |
| `column_id` | `0x38` INT4 fest | nein | `Int32` nicht nullbar | gleich |
| `column_name` | `0xE7` nvarchar, **nullbar** | ja | `UnicodeText` nicht nullbar | siehe I-1 |
| `type_name` | `0xE7` nvarchar | nein | `UnicodeText` nicht nullbar | Länge Grenze ([ADR-0041](../plan/adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) Nr. 7) |
| `default_definition`, `computed_definition` | `0xE7` | ja | `UnicodeText` nullbar | Grenze (Nr. 7) |
| `seed_value`, `increment_value` | `0x62` `sql_variant` | ja | `Int64` nullbar | Grenze (Nr. 7) |
| `computed_persisted` | `0x68` BITN | ja | `Bool` nullbar | unverändert |

Die Maße von `smallint` (2/5/0) und `tinyint` (1/3/0) stehen im Test `MessungBulkBelegTests` (Zeile 295 ff., `katalog-abfragen/03/zeilen`) für beide Stufen; der Bau stimmt.

**Draht der nullbaren Form von `Int16`/`UInt8`** (`INTN` mit 2 und 1 Byte) ist nicht gemessen; SPEC-003, SPEC-035-Zeile und Matrix 2a sagen das so („nicht gemessen“). Aussage reicht nicht weiter als ihr Beleg (`AGENTS.md` §3.8): erfüllt.

## Wertebereichsprüfung

Zwei Stufen, beide mutationsgeprüft: (a) `ValueMapping.narrow` (Ergebnis des Backends → `VALUE_OUT_OF_RANGE`, ausdrücklich kein Abschneiden; nur `Int`, `Long`/`Short`/`Byte` sind `TYPE_MISMATCH`), (b) `ValueWriter` über `ValueSupport.rangeOfSize` (zweite Verteidigungslinie am Token). Simulation: `SimIntegers.range`/`of`/`number`/`parameter`/`load` prüfen denselben Bereich für Literal, Parameter und Massenladen. Verhalten für `Int32`/`Int64` bleibt gegenüber der Basis gleich (Review und eigene Lektüre: Bitgrenze 31/63 gleich dem Intervall; Verengung `Int64`→`Int32` im Bereich; Erweiterung `Int32`→`Int64`). Mutation Y1 (Untergrenze von `byteRange` auf −1) färbt `SimValuesTest` (2) und `SimulationBackendBulkTest`.

## `make flaeche` und SPEC-059

`make flaeche` Exit 0. SPEC-059 führt `SqlType` mit `Int16` und `UInt8`, Beleg als `SqlType.Int16`/`SqlType.UInt8` in `src/integrator` (`FlaecheEmbeddingTest`). Änderungsprotokoll-Zeile vom 2026-11-08 enthält den Vermerk „**Inkompatibel** für ein Kotlin-`when` ohne `else` über `SqlType` in fremdem Code“ plus die Klarstellung zum Draht. Der Wortlaut deckt die Anforderung der DoD (Vermerk „inkompatibel für ein Kotlin-`when` ohne `else` über `SqlType`“) und [ADR-0041](../plan/adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) Nr. 4.

## Entscheidungs-Konformität gegen ADR-0041

| Nr. | Entscheidung | Konform? |
|---|---|---|
| 1 | Mitglieder, JVM-Klasse `Int`, Bereichsprüfung, Nullbarkeit wie `Int32` | ja. **Draht:** die ADR sagt „Draht: `INTN`“; gebaut ist die nicht nullbare Spalte als fester Typ (INT2/INT1), die nullbare als `INTN`. Der Bau folgt der Messung (M-16) und dem Zielbild; die Klarstellung steht in `SPEC-003` (Rang 2 über der ADR), die Accepted-ADR bleibt unverändert (`AGENTS.md` §3.5), keine Folge-ADR — Entscheid des Nutzers (2026-11-08, Standardantwort), der Rang der Spezifikation trägt es. Die ADR hat keinen Zeiger auf die Klarstellung (kann sie nicht haben, immutable); Auffindbarkeit nur über SPEC-003 und Slice §6/§7 → Bedingung B2 |
| 2 | Umfang, Maße | ja; die Aussage „Maße nicht gemessen“ der ADR ist durch M-17 überholt (B1) |
| 3 | Liste der Fläche, `make flaeche`, Einbettungstest | ja |
| 4 | Inkompatibilität vermerkt | ja |
| 5 | Alle Auswerter; Erzeuger unberührt | ja; die zwei vom Übersetzer nicht gefangenen Stellen (`SimValues.parameter`, `CatalogAnswers.collationOf`) sind im Slice benannt und behandelt bzw. unberührt |
| 6 | Leser der Beschreibung nimmt `smallint`/`tinyint` nicht auf | ja (`SimDescriptionReader` und `StructureParts` unberührt) |
| 7 | Abgrenzung `nvarchar(256)`, `nvarchar(max)`, `sql_variant` | ja; in SPEC-035 als „bleiben abweichend“ geführt |
| 8 | Zusage in SPEC-035/003 | ja |

## Review-Findings F-1 bis F-6 (Nachprüfung von `bd66e2dc`)

| Finding | Stand | Beleg |
|---|---|---|
| F-1 MEDIUM Draht ≠ Wortlaut ADR | **behoben (per Klarstellung)** | SPEC-003 Absatz „Die Entscheidung zu den Mitgliedern von `SqlType` … nennt als Draht beider Mitglieder `INTN`; diese Spezifikation schärft das auf die gemessene Form …“, SPEC-059-Protokollzeile; Slice §1 und DoD-Punkt 1 nennen die tatsächliche Form; §6 trägt den Ausgang als „zur Closure-Notiz zu tragen“ (B2) |
| F-2 MEDIUM Matrix 2a | **behoben** | zwei Zeilen `Int16`, `UInt8` mit Zitaten; `make matrix-zitate` Exit 0, 0 ohne PASSED |
| F-3 MEDIUM Überführung in SPEC-035 / Doku | **behoben** | SPEC-035: Absatz „Für `Int16` … und `UInt8` … gilt: ein Zahlenliteral im Wertebereich …“, Folge für Parameter und die Abweichung vom Zielbild („der echte Server konvertiert einen `int`-Parameter in eine `smallint`-Spalte; … nicht in diesem Repo gemessen“); `docs/user/einbettung-und-betrieb.md` Zeile 239 sagt dasselbe; Massenladen-Satz um `Int16`/`UInt8` ergänzt |
| F-4 MEDIUM KDoc `RpcParameters` | **behoben** | „`INTN` mit 1 und 2 Byte entspricht `Int16` und `UInt8` …; als Parameter bleibt er abgewiesen (Setzung)“; `grep` nach „SqlType führt ihn nicht“ ohne Treffer |
| F-5 MEDIUM Plan-Tabelle / Kopf | **behoben, mit einem Rest (L-2)** | Kopf nennt SPEC-002; Tabelle §3 hat Zeilen für Roadmap, `slice-mv`-Links, `RowRules`, `RpcParameters`, Matrix, Messprotokoll, Nutzer-Doku, alle Testklassen (Abgleich unten) |
| F-6 LOW Unterbehauptung „Setzung“ | **behoben** | `docs/user` und `CatalogAnswers`-KDoc nennen `bigint`, `bit`, `time`, `datetime2` zusammen mit `smallint`, `tinyint` als gemessen (M-17) |

## Plan-Tabelle §3 gegen den Diff

Diff `66fee6c1..HEAD`: 40 Dateien. Jede Code-, Test- und Spec-Datei hat eine Zeile oder fällt unter die Sammelzeile (Tests: alle elf genannten Klassen stimmen mit dem Diff überein). Die Zeile `RpcOutputs` aus der Erstfassung ist entfernt. Nicht genannt: `docs/plan/spec-abdeckung.md` (erzeugte Datei, von `make spec-abdeckung`; L-1) und die beiden Review-Dateien (Rollen-Artefakte, gehören nicht in §3). Die vier `open/`-Slices, `welle-testserver-manuell.md`, das Register-`state.md` und der `done/`-Slice sind durch die Zeile zu den `slice-mv`-Links gedeckt. Mehr Dateien im Diff, als die Tabelle nennt: keine Produktivdatei.

## Mutationen

Enge Testklassen in Docker (eigene Stage in einer Kopie des Dockerfile im Scratchpad: `:test --tests …` plus `integratorTest`; Skript `mut.sh`, Logs `mut-*.log`). Eine Mutation je Lauf, danach `git checkout`; Arbeitsbaum danach sauber.

| Mutation | Wirkung | Rote Tests |
|---|---|---|
| **X1** (nachgeholt, Reviewer abgebrochen) `TypeCode.INT2` von `0x34` auf `0x35` | rot | `FlaecheEmbeddingTest > einBackendMeldetSpaltenVonInt16UndUInt8` (Rohbytes), `ColumnMappingTest > Mindestkatalog …` (Handvektor), `TokenRoundtripTest` (2), `KatalogSpaltenWireTest` |
| **X3** (nachgeholt) `is_computed` nicht nullbar | rot | `KatalogSpaltenWireTest` (allein) |
| **M1** `Int16` nicht nullbar → `Int4Type` | rot | `FlaecheEmbeddingTest`, `ColumnMappingTest` (2), `MappingFuzzTest`, `ValueMappingTest` (2), `KatalogSpaltenWireTest` |
| **M2** `max_length` wieder `int(...)` (`Int32`) | rot | `KatalogSpaltenWireTest` (allein) |
| **M3** Bereichsprüfung `narrow` ausgeschaltet | rot | `MappingFuzzTest`, `ValueMappingTest` (Bereich) |
| **M6** `is_nullable` nicht nullbar | rot | `KatalogSpaltenWireTest` (allein) |
| **Y1** (eigene) `SimIntegers.byteRange` Untergrenze 0 → −1 | rot | `SimValuesTest` (2), `SimulationBackendBulkTest` |

Nicht gefahren: M4 (`UBYTE_MAX` 256), M5 (`RowRules` ohne Bereich), M7 (`Int2Type` schreibt `u8`): sie sind vom Reviewer einzeln gefahren (rot) und vom Auftrag nur als Stichprobe verlangt. Grenze: Y1 deckt `byteRange`; die Untergrenze von `shortRange` und `ValueSupport.byteRange` sind nicht gefahren (der Handvektor `D1 00 80` und die Grenztests tragen sie im Kopf nach). Nicht gefahren heißt nicht „überlebt“.

## d-migrate (Closure-Trigger §5)

Image `ghcr.io/pt9912/d-migrate@sha256:308f27753036…`, Testserver aus dem Arbeitsbaum (`make testserver-up`, Name `verif-katalog-ts`, Port 14341, Voreinstellung: Beispiel-Tabellen `kunden`, `bestellungen`), Schrittfolge der Nutzer-Doku Aufgabe 3 (Soll `soll.yaml` aus der Doku). Dann dieselbe Folge gegen den Testserver der Basis `66fee6c1` (Worktree im Scratchpad, Name `verif-katalog-base`, Port 14342; Bau 50 s aus dem Layer-Cache, also billig). Ergebnis:

| Schritt | HEAD | Basis `66fee6c1` | Vergleich |
|---|---|---|---|
| `schema reverse` | Exit 0 | Exit 0 | Ausgabe und `ist.yaml` byte-gleich |
| `schema compare` (Soll gegen Server) | Exit 1, `DIFFERENT`, 1 Tabelle entfernt (`produkte`), 1 geändert (`kunden`: `column Email`) | gleich | gleich bis auf den Port |
| `schema migrate --execute` | Exit 0, `status ok`, 2 Anweisungen (`CREATE TABLE [produkte]`, `ALTER TABLE [kunden] ADD [Email]`) | gleich | `statements` und `status` gleich |
| `schema compare` erneut | Exit 0, `IDENTICAL` | gleich | gleich bis auf den Port |
| `data export --tables kunden` | Exit 0, 0 Zeilen, `kunden.json` | gleich | `kunden.json` byte-gleich |

Der Lauf ist gegenüber dem Lauf unmittelbar davor **unverändert**; die Rückführung „in-progress → open“ (Lauf rot oder verändert) ist nicht eingetreten. Grenze: Einzelläufe auf einem Host, Beleg am Prüfclient d-migrate gegen den Testserver, nicht am echten Server (`vergleichslauf-d` nicht gefahren, nicht verlangt); die Wirkung von `smallint`/`tinyint` auf andere Clients ist nicht belegt (`AGENTS.md` §3.8, [ADR-0041](../plan/adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) Kontext).

## Merge-Probe gegen `main`

`git merge-tree --write-tree --name-only HEAD main` mit `main` = `95e89d05` (zum Zeitpunkt der Probe; enthält den Merge von `slice-sp-unprepare-ohne-obergrenze`): **fünf Inhaltskonflikte, alle in Dokumenten, keiner in Quelltext oder Spezifikation:**

1. `docs/plan/planning/in-progress/roadmap.md`
2. `docs/plan/planning/done/slice-katalogspalten-smallint-tinyint-angleichen.md`
3. `docs/plan/planning/next/slice-abnahme-d-migrate.md`
4. `docs/plan/planning/open/slice-antwort-tokens-zielbild-luecken-bauen.md`
5. `docs/plan/planning/welle-testserver-manuell.md`

Automatisch gemergt (also textlich ohne Konflikt, inhaltlich beim Planner zu prüfen): `spec/spezifikation.md`, `docs/plan/spec-abdeckung.md` (erzeugte Datei: nach dem Merge neu erzeugen), `docs/messungen/referenzabgleich.md`, `docs/plan/kompatibilitaetsmatrix.md`, der `done/`-Slice. `main` ändert Quelltext in `RequestLoop`, `UnprepareBatch`, `ErrorMapper`, `ResponseRun`, `Bootstrap` und weiteren, ohne `SqlType` auszuwerten; ein Übersetzungs- und Testlauf der **gemergten** Fassung wurde nicht gefahren (Grenze, B4). `harness/README.md` ist im Zweig unberührt und auf `main` geändert: kein Konflikt.

## Eigene Befunde

| # | Klasse | Befund | Wirkung |
|---|---|---|---|
| V-1 | INFO, Wortlaut | DoD-Punkt 3 („nicht gemessen, Nutzer entscheidet“), [ADR-0041](../plan/adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) Nr. 2, Slice §4 Start-Trigger („der Nutzer hat über den dritten Liefer-Punkt entschieden“) und §6 Risiko „Katalogmaße ungemessen“ sind durch M-17 überholt (`smallint` 2/5/0, `tinyint` 1/3/0 gemessen, beide Instanzen) | Planner passt an (B1); kein Code betroffen |
| L-1 | LOW | `docs/plan/spec-abdeckung.md` steht im Diff, aber nicht in Plan-Tabelle §3 | erzeugte Datei; Zeile oder Vermerk nachtragen |
| L-2 | LOW | Plan-Tabelle §3, Zeile zu `docs/user/*`: vier statt drei Zellen (der Text „Ist-Zustand: neue Mitglieder …“ steht doppelt, `awk`: Zeile 100 hat 4 Zellen, alle anderen 3) | Tabelle rendert mit überzähliger Spalte; kosmetisch, `docs-check` meldet nichts |
| I-1 | INFO, Beobachtung | Im Draht ist `column_name` am echten Server **nullbar** (Flags-Bit 0), im Bau nicht (`text("column_name")` ohne `nullable`). SPEC-035 nennt als Abweichung nur die Länge (`nvarchar(256)`), nicht die Nullbarkeit. Außerhalb dieses Slice ([ADR-0041](../plan/adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) Nr. 7 und M-16 nennen `column_name` nicht), `KatalogSpaltenWireTest` prüft es nicht | Register-Eintrag oder Aufnahme in den Slice `slice-katalog-indizes-defaults-fremdschluessel`; kein Befund gegen diesen Slice |
| I-2 | INFO | Die Accepted-ADR [ADR-0041](../plan/adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) Nr. 1 bleibt wörtlich falsch („Draht: `INTN`“) und trägt keinen Zeiger auf die Klarstellung (`AGENTS.md` §3.5 verbietet die Änderung) | Auffindbarkeit nur über SPEC-003 und die Closure-Notiz (B2) |
| I-3 | INFO | `harness/README.md` hat im Zweig und auf der Basis 11 Tabellenzellen über 200 Zeichen (z. B. Zeile 108 mit 236); der Zweig berührt die Datei nicht | vorbestehend, nicht Gegenstand; wird hier nur benannt, weil der Auftrag nach den Zellen fragte |
| I-4 | INFO | Reviewer-Befund F-8 bleibt wahr: `CREATE TABLE … SMALLINT` ist `TDS_UNSUPPORTED_SQL`, 1-/2-Byte-Parameter `TDS_UNSUPPORTED_RPC`, eine `Int16`/`UInt8`-Spalte nimmt über Parameter nie einen Wert an (SPEC-035 sagt es jetzt) | Setzung nach [ADR-0041](../plan/adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) Nr. 5/6, benannt; Re-Evaluierungs-Trigger der ADR beachten |

## Grenzen dieses Berichts

* `make gates FORCE=1` kam aus dem Layer-Cache (siehe Tabelle); den Frischbeleg tragen `make clean-build` (alle drei Gradle-Stufen `--no-cache`) und `make matrix-zitate`.
* Nicht gefahren: `make spec-abdeckung`, `make kompatibilitaet` einzeln (läuft als Stufen 4 bis 7 in `make e2e`), `make vergleichslauf-d` (EULA-Lauf, nicht verlangt), ein Bau der gemergten Fassung mit `main`.
* d-migrate-Läufe: Einzelläufe, ein Host, Basis und HEAD nacheinander (kein paralleler Lauf), Last 5 bis 17.
* Aussage über andere Clients als Referenzclient und d-migrate: keine.

## Bedingungen für die Planner-Closure

* **B1 (Wortlaut, Planner):** DoD-Punkt 3 von „Der Nutzer entscheidet vor dem Bau“ auf den Ist-Stand ziehen (M-17 belegt `smallint` 2/5/0 und `tinyint` 1/3/0, beide Instanzen; Träger `MessungBulkBelegTests`; Setzung (b) und Nachmessung (a) entfallen); §4 Start-Trigger und §6 Risiko „Katalogmaße ungemessen“ entsprechend (Ausgang „entfallen“, Beleg M-17).
* **B2 (Closure-Notiz §7):** die Abweichung des Drahts vom Wortlaut von [ADR-0041](../plan/adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) Nr. 1 mit dem Entscheid (Nutzer 2026-11-08: Klarstellung in `SPEC-003`, Rang 2 über der ADR, keine Folge-ADR) und dem Beleg (Aufnahmen M-16, `0x34`/`0x30` fest, beide Instanzen) tragen; Risiko 6 in §6 bekommt den Ausgang „eingetreten, mit Klarstellung getragen“. Die Zeile `liegt in` nur, wenn mit dem Slice wirklich eine Regel/Sensor verkörpert wurde (hier: Sensor `make flaeche` trägt die Namen; eine neue Regel sehe ich nicht).
* **B3 (Risiken und Register):** alle sechs Risiken in §6 mit genau einem Ausgang: (1) Auswerter — entfallen/getragen (Suche mit `grep`, Übersetzer, 7 Mutationen); (2) Dateikonflikte — eingetreten (5 Konflikte, nur Dokumente, siehe Merge-Probe); (3) Beleg am fremden Client — d-migrate unverändert gegenüber der Basis (dieser Bericht), `slice-abnahme-d-migrate` prüft am Ende erneut: „weiter offen“ an `slice-abnahme-d-migrate`; (4) Kotlin-`when` ohne `else` — eingetreten, vermerkt in SPEC-059; (5) Katalogmaße — entfallen; (6) siehe B2. Register `BEO-TDS/katalog-strukturabweisung-und-aenderungszaehler-weichen-vom-echten-server-ab` fortschreiben (Katalogspalten angeglichen; I-1 `column_name` nullbar als neue Abweichung) und `BEO-TDS/oeffentliche-flaeche-mitglieder-ohne-sensor` zitieren: `make flaeche` fängt Mitglieder weiterhin nicht, hier sind es geschachtelte Typnamen und damit gefangen; die Mitglieder-Lücke bleibt.
* **B4 (Merge):** die fünf Dokument-Konflikte lösen (Roadmap: „Nichts in Arbeit.“ streichen, wie `main` es vermerkt; Slice-Datei: Links der `slice-mv`), danach `docs/plan/spec-abdeckung.md` neu erzeugen und einen Lauf der gemergten Fassung (mindestens `make flaeche`, `make docs-check`, `make test`) vor dem Haken; ein Merge ohne diesen Lauf ist Grenze, kein Befund. Der Merge-Commit trägt die Kennung ([TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [ADR-0041](../plan/adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md)).
* **B5 (Haken):** die Haken der drei Liefer-Punkte setzt der Planner nach diesem Bericht und nach B1; die Nachweis-Pflichten „Review“ und „Gates“ sind nach den Belegen oben erfüllt, wobei zum Review gilt: die Nachbesserung `bd66e2dc` hat keinen eigenen Review (Grenze, hier ersatzweise verifiziert).
* **L-1/L-2:** Plan-Tabelle §3 nachziehen (Zeile zu `spec-abdeckung.md`, doppelte Zelle entfernen); kein Blocker.

## Übergabe

An den Planner: B1 bis B5, L-1, L-2, I-1 (Register). An den Architect: nichts offen (der Entscheid zu F-1 ist getroffen und von der Spezifikation getragen); I-2 ist Kenntnisnahme. Der Implementer hat nichts offen.
