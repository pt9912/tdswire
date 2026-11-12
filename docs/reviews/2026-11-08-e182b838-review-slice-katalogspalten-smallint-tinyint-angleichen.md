# Review-Report: slice-katalogspalten-smallint-tinyint-angleichen — 2026-11-08

**Review-Art:** Code (gegen Plan, ADR und Hard Rules; nicht gegen die DoD, das ist die Frage des Verifiers).

**Gegenstand:** Diff `6d75692a..e182b838` im Zweig `worktree-agent-a8a6593e817bb4aaf` (Commits `87a1bd0e` und `bf060056` reine `slice-mv`, `c5a8d12f` Code und Tests, `e182b838` Spezifikation, Nutzer-Doku, Messprotokoll, Slice §3, Roadmap; 35 Dateien, +631/−113). Merge-Probe gegen `main` (`66fee6c1`) siehe unten.

**Skill:** `.harness/skills/reviewer.md` @ `e182b838` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-08

**Eingangs-Kontext** (die Verträge, gegen die geprüft wurde):

- Slice `slice-katalogspalten-smallint-tinyint-angleichen` (Plan §3, DoD, Rückführungen, §6 Risiken)
- [ADR-0041](../plan/adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) (Accepted, Option A), [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), [ADR-0026](../plan/adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md), [ADR-0021](../plan/adr/0021-oeffentlicher-integrations-einstieg.md)
- [SPEC-002](../../spec/spezifikation.md#spec-002--interner-query-vertrag), [SPEC-003](../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog), [SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends), [SPEC-059](../../spec/spezifikation.md#einstieg-des-integrators-vertrag-zu-spec-059); [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-QA-05](../../spec/lastenheft.md#tds-qa-05--wartbarkeit)
- `AGENTS.md` §3.1 bis §3.8; Grundsatz des Nutzers „Spec ist Zielbild“ (der echte SQL Server ist die Referenz)

## Selbst gefahren

| Lauf | Ergebnis |
|---|---|
| `make gates FORCE=1` | Exit 0 in 43 s; Docker-Schichten fast alle aus dem Cache (93 `CACHED`), also derselbe Eingang wie der Lauf des Implementers; `d-check` 1255 Dateien, 0 Befunde; `gate-zuordnung` 16 Gates |
| `make flaeche` | Exit 0 (Selbsttest und Prüfung) |
| `make docs-check` | 1255 Dateien, 0 Befunde (vor Anlage dieses Reports) |
| Baseline der engen Testklassen (`ColumnMappingTest`, `ValueMappingTest`, `MappingFuzzTest`, `TokenRoundtripTest`, `KatalogSpaltenWireTest`, `SimValuesTest`, `SimulationBackendBulkTest`, `SimulationBackendCatalogTest`, `TableStoreTest`, `FlaecheEmbeddingTest`) | `BUILD SUCCESSFUL`, 1 m 24 s |
| `make clean-build`, `make e2e` | nicht gefahren (Gate-Zuschnitt des Auftrags) |
| Messdateien `referenztest-dotnet/Mitschnitt/messung/bulk-merge-katalog/{2022,2025}/katalog-abfragen.messung` | selbst dekodiert (COLMETADATA der Spaltenabfrage), siehe Abschnitt Draht |

## Draht gegen Bau (Schwerpunkt a)

Das COLMETADATA der Spaltenabfrage (Nachricht 18 der Aufnahme `katalog-abfragen`, 14 Spalten) habe ich aus den Rohbytes beider Instanzen selbst gelesen; 2022 und 2025 sind gleich:

| Spalte | Typkennung am echten Server | Nullbar | Bau |
|---|---|---|---|
| `max_length` | `0x34` (INT2, fest) | nein (Flags `0x0008`) | `Int2Type`, gleich |
| `precision`, `scale` | `0x30` (INT1, fest) | nein | `Int1Type`, gleich |
| `is_nullable` | `0x68` (BITN, Länge 1) | ja (Flags `0x0021`) | `BitNType`, gleich |
| `is_identity` | `0x32` (BIT, fest) | nein | `BitType`, gleich |
| `is_computed` | `0x68` (BITN, Länge 1) | ja | `BitNType`, gleich (Frage b: gemessen, in beiden Instanzen) |
| `column_id` | `0x38` (INT4, fest) | nein | `Int4Type`, gleich |
| `default_definition`, `computed_definition` | `0xE7` Länge 65535 | ja | nicht Gegenstand ([ADR-0041](../plan/adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) Nr. 7) |
| `seed_value`, `increment_value` | `0x62` (`sql_variant`) | ja | nicht Gegenstand ([ADR-0041](../plan/adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) Nr. 7) |
| `computed_persisted` | `0x68` | ja | unverändert (`bool(…, nullable = true)`) |

Die Maße von `smallint` (2/5/0) und `tinyint` (1/3/0) stehen in `katalog-abfragen/03/zeilen` (`MessungBulkBelegTests`, Theorie über beide Stufen). `is_computed` als BITN trägt `MessungBulkBelegTests` (Zeile `is_computed:bitn(...) nullbar=ja`) für die Abfrage der Tabellenbeschreibung; für die Spaltenabfrage trägt es die Aufnahme selbst. Frage b ist damit beantwortet: das Ziehen von `is_computed` auf BITN ist gemessen, nicht Setzung.

## Mutationen (Schwerpunkt f)

Enge Testklassen (siehe Baseline), eine Mutation je Bau, danach `git checkout` der Quellen; Arbeitsbaum danach unverändert. M1 bis M7 sind die Mutationen des Implementers (Stand seines Scratchpads, von mir einzeln nachgefahren).

| Mutation | Wirkung gefahren | Rote Tests |
|---|---|---|
| M1 `Int16` nicht nullbar auf `Int4Type` | rot | 6: `FlaecheEmbeddingTest`, `ColumnMappingTest` (2), `MappingFuzzTest`, `ValueMappingTest` (2), `KatalogSpaltenWireTest` |
| M2 `max_length` wieder `Int32` | rot | `KatalogSpaltenWireTest` (allein) |
| M3 Bereichsprüfung in `ValueMapping.narrow` entfernt | rot | `MappingFuzzTest`, `ValueMappingTest` |
| M4 `UBYTE_MAX` 256 | rot | `SimValuesTest` (2), `SimulationBackendBulkTest` |
| M5 `RowRules` `UInt8` ohne Bereich | rot | `TableStoreTest` (allein) |
| M6 `is_nullable` nicht nullbar | rot | `KatalogSpaltenWireTest` (allein) |
| M7 `Int2Type` schreibt `u8` statt `u16Le` | rot (Lauf 14 m 43 s unter Last) | 5: `FlaecheEmbeddingTest`, `MappingFuzzTest`, `ValueMappingTest` (Ränder), `TokenRoundtripTest` (2), `KatalogSpaltenWireTest` |

Nicht gefahren (Laufzeit, Last; meine eigenen Mutationen `TypeCode.INT2` auf `0x35` und `is_computed` nicht nullbar wurden abgebrochen, bevor ein Ergebnis vorlag): `TypeCode.INT2`/`INT1` als Zahl, `is_computed` nullbar (nur über `KatalogSpaltenWireTest` gedeckt, dort per Assertion `typeOf("is_computed")`), `UInt8`-Untergrenze von `ValueSupport.byteRange`, Untergrenze von `SimIntegers.shortRange`, Entfernen des Zweigs `BYTE_BYTES, SHORT_BYTES -> narrow` in `ValueMapping.intN`. Für den ersten Fall tragen `ValueMappingTest` (−1 in nullbarer und fester Spalte) und der Handvektor `D1 00` die Untergrenze im Kopf nach; ein Fahrbeleg fehlt. Nicht gefahren heißt hier nicht „überlebt“. Die Typkennung ist in `ColumnMappingTest` als Handvektor (`34`, `30`) und im Einbettungstest als Rohbytes festgehalten.

## Findings

### F-1 MEDIUM — Gebauter Draht weicht vom Wortlaut von [ADR-0041](../plan/adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) Nr. 1 ab; Folge-Entscheidung und zweite Fundstellen fehlen

* kategorie: MEDIUM
* quelle: [ADR-0041](../plan/adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) Nr. 1; `AGENTS.md` §3.5; Reviewer-Skill „Fortschreibung lässt die zweite Fundstelle stehen“
* pfad: `docs/plan/adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md` · „(Ganzzahl mit 16 Bit, Draht: `INTN` mit 2 Byte)“; `docs/plan/planning/done/slice-katalogspalten-smallint-tinyint-angleichen.md` · „`SqlType.Int16` (Draht: `INTN` mit 2 Byte)“
* befund: Die ADR (Accepted) sagt für beide Mitglieder „Draht: `INTN`“; gebaut sind nicht nullbare Spalten als feste Typen INT2 (`0x34`) und INT1 (`0x30`), nullbare als `INTN` mit 2 und 1 Byte, weil die Aufnahme M-16 für `max_length`, `precision` und `scale` die festen Typen zeigt (selbst nachgelesen, beide Instanzen). Der Bau ist damit richtig gegenüber Messung und Zielbild und deckt sich mit der zweiten Klausel der Nr. 1 („Nullbarkeit wie bei `Int32`“, das ebenso fest/`INTN` führt); die Spezifikation (Rang 2, `SPEC-003`) trägt die Form ausdrücklich und nennt die nullbare Form „nicht gemessen“. Offen bleibt: die ADR bleibt wörtlich falsch (nicht umgeschrieben, `AGENTS.md` §3.5), kein Vermerk in der ADR-Kette, und im Slice stehen §1 Ziel und DoD-Punkt 1 weiter mit „Draht: `INTN`“; nur §3 trägt die Abweichung. Urteil zur Frage des Auftrags: inhaltlich genügt die Spezifikation als Zielbild; ob die Schärfung eine Folge-ADR ist oder ein Vermerk genügt, entscheidet der Architect.
* verifizierbar: ja (Grep `INTN` in [ADR-0041](../plan/adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) und im Slice gegen `TypeCode.INT1`/`INT2`)
* klasse: Bau weicht vom Wortlaut einer Accepted-ADR ab

### F-2 MEDIUM — Kompatibilitätsmatrix §2a führt `Int16` und `UInt8` nicht

* kategorie: MEDIUM
* quelle: Reviewer-Skill „Fortschreibung lässt die zweite Fundstelle stehen“; `AGENTS.md` §5 Regel 6; [TDS-QA-05](../../spec/lastenheft.md#tds-qa-05--wartbarkeit)
* pfad: `docs/plan/kompatibilitaetsmatrix.md` · „### 2a. Datentypen der Antwortseite (Mindestkatalog, `SPEC-003`)“ mit den Zeilen `Int32` und `Int64`; Zeile „Abbildung Backend nach TDS“ („der Mindestkatalog aus Abschnitt 2a in fester und N-Form“)
* befund: Der Mindestkatalog wächst um zwei Typen, die Matrix der Antwortseite hat für sie keine Zeile (Zieltyp, Grenzfälle, Test, Herkunft); `git diff` berührt die Datei nicht, und die Plan-Tabelle §3 nennt sie nicht. Die Zeile zu `tinyint`/`smallint` als RPC-Parametertyp („Dispatch: nicht unterstützt“) bleibt richtig. Die Matrix ist das Abdeckungs-Dokument, das `make matrix-zitate` gegen PASSED hält.
* verifizierbar: ja (Grep `Int16` in `docs/plan/kompatibilitaetsmatrix.md`: kein Treffer)
* klasse: Fortschreibung lässt zweite Fundstelle stehen

### F-3 MEDIUM — SPEC-035 nennt die Überführungsregeln für `Int16` und `UInt8` nicht; eine Folge der Setzung zu den Parametern bleibt unbenannt

* kategorie: MEDIUM
* quelle: Reviewer-Skill „Fortschreibung lässt die zweite Fundstelle stehen“; `AGENTS.md` §3.8; Grundsatz „Spec ist Zielbild“
* pfad: `spec/spezifikation.md` · „**Überführung im Massenladen** (Setzung, am Server nicht gemessen“ und „`Int` und `Long` gelten in einer `Int32`-Spalte, wenn sie in deren Wertebereich passen“; ebenso der Absatz „ein Parameter wird verlustfrei erweitert (Int32 zu Int64 zu Decimal); zwei Verengungen gelten“; `src/test/kotlin/io/github/pt9912/tdswire/simulation/SimValuesTest.kt` · „assertInvalid(via(par(SqlType.Int32, 7), SqlType.Int16), \"Int32 in Int16\")“
* befund: Der Code nimmt im Massenladen `Int` und `Long` in `Int16`/`UInt8` im Wertebereich an (`BulkLoads`-KDoc, Test), und die Spezifikation sagt weiter nur `Int32`/`Int64`; die Regeln für Zahlenliteral und Parameter in `Int16`/`UInt8` stehen nur im Test. Folge, die nirgends steht: ein Client kann keinen 1- oder 2-Byte-Parameter schicken (`RpcParameters` weist ihn ab, Setzung der ADR Nr. 5), jeder andere Ganzzahlparameter (`Int32`, `Int64`) ist für eine `Int16`/`UInt8`-Spalte ungültig (Test „Int32 in Int16“ als Wächter). Eine solche Spalte (nur über eine programmatische `SimTable` erreichbar) nimmt also über `INSERT`/`UPDATE`/`WHERE` mit Parameter nie einen Wert an; am echten Server konvertiert ein `int`-Parameter in eine `smallint`-Spalte mit Bereichsprüfung. Die Abweichung vom Zielbild ist weder in der Spezifikation noch in der Nutzer-Doku benannt.
* verifizierbar: ja (Grep `Int16` im Abschnitt „Überführung“ von `SPEC-035`; Test mit `INSERT … VALUES (@P0)` gegen eine `Int16`-Spalte)
* klasse: Überführungsregel im Code und Test, nicht in der Spezifikation

### F-4 MEDIUM — KDoc von `RpcParameters` begründet die Abweisung von `INTN` mit 1 und 2 Byte mit einer Aussage, die der Slice falsch macht

* kategorie: MEDIUM
* quelle: Reviewer-Skill „Fortschreibung lässt die zweite Fundstelle stehen“; `AGENTS.md` §3.7
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/RpcParameters.kt` · „ist [RpcRejection.TYPE]: `SqlType` führt ihn nicht, und es entsteht hier kein zweiter Katalog.“
* befund: Der Kommentar nennt `INTN` mit 1 und 2 Byte in der Reihe der Typen, die `SqlType` nicht führt; seit diesem Slice führt `SqlType` beide (`Int16`, `UInt8`). Die Abweisung bleibt (Setzung der ADR Nr. 5), ihr Grund im Kommentar stimmt nicht mehr; die Datei steht nicht im Diff und nicht in der Plan-Tabelle.
* verifizierbar: ja (Grep `SqlType` führt ihn nicht)
* klasse: Fortschreibung lässt zweite Fundstelle stehen

### F-5 MEDIUM — Plan-Tabelle §3 und Kopf „Berührte Spec-Stellen“ gegen den Diff nicht vollständig

* kategorie: MEDIUM
* quelle: Reviewer-Skill „Plan-Tabelle nennt die berührten Dateien nicht“
* pfad: `docs/plan/planning/done/slice-katalogspalten-smallint-tinyint-angleichen.md` · „**Berührte Spec-Stellen:** SPEC-003 (Mindestkatalog von `SqlType` wächst um `Int16` und `UInt8`)“; Tabelle „| Datei / Komponente | Änderungs-Art | Begründung |“
* befund: Abgleich gegen den Diff. Berührte Spec-Stelle ohne Eintrag im Kopf: `SPEC-002` (Absatz zu `SqlType` und JVM-Klassen; das Änderungsprotokoll der Spezifikation nennt sie). Berührte Dateien ohne Zeile: `docs/plan/planning/in-progress/roadmap.md` (Ruhe-Marker, Slice unter „In Arbeit“, Drift-Log-Link), die Links der `slice-mv` in vier `open/`-Slices, `welle-testserver-manuell.md`, dem Register-`state.md` und dem `done/`-Slice (reine Link-Nachzüge). Die Zeile mit `RpcOutputs` nennt eine Datei, die der Diff nicht berührt. Der Negativbefund „keine Datei darüber hinaus“ gilt erst nach diesem Abgleich.
* verifizierbar: ja (`git diff --stat 6d75692a..HEAD` gegen die Tabelle)
* klasse: Plan-Tabelle ohne berührte Datei

### F-6 LOW — Nutzer-Doku und KDoc stellen die Maße von `bigint`, `bit`, `time`, `datetime2` weiter als Setzung dar, die Spezifikation als gemessen

* kategorie: LOW
* quelle: `AGENTS.md` §3.7, §3.8; `AGENTS.md` §5 Regel 5
* pfad: `docs/user/einbettung-und-betrieb.md` · „sind Setzung nach dem Handbuch des Servers (die für `smallint` und `tinyint` sind gemessen)“; `src/main/kotlin/io/github/pt9912/tdswire/simulation/CatalogAnswers.kt` · „sind Setzung nach dem Handbuch des Servers (Skala der Zeittypen 7, `SPEC-003` offen)“
* befund: Das Messprotokoll (Zeile M-17, Zuordnung „stützt“) und `SPEC-035` (Messvermerk) führen `bigint`, `bit`, `time`, `datetime2` als am echten Server gemessen. Die Sätze, in die dieser Diff „smallint und tinyint sind gemessen“ einschiebt, setzen diese vier als Gegensatz zu „Setzung“; das Gegenüber ist falsch (Unterbehauptung, widerspricht der Spezifikation). Die Zeile stammt aus der Zeit vor M-17, der Diff berührt sie.
* verifizierbar: ja (Grep „Setzung nach dem Handbuch“ gegen Zeile M-17)
* klasse: Zweite Fundstelle hinter der Messung

### F-7 INFO — DoD-Punkt 3 ist überholt; Haken nicht gesetzt

* kategorie: INFO
* quelle: Reviewer-Skill „Haken vor der Verifikation gesetzt“
* pfad: `docs/plan/planning/done/slice-katalogspalten-smallint-tinyint-angleichen.md` · „**Der Nutzer entscheidet vor dem Bau:** (a) Nachmessung“
* befund: Haken: keiner gesetzt (`- [x]` zählt 0), also kein Verstoß. Der Wortlaut des dritten Liefer-Punkts („nicht gemessen, Nutzer entscheidet“, [ADR-0041](../plan/adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) Nr. 2 ebenso) ist überholt: M-17 belegt `smallint` 2/5/0 und `tinyint` 1/3/0 (Theorie über beide Instanzen in `MessungBulkBelegTests`, vor diesem Slice vorhanden), `CatalogAnswers` nennt sie gemessen. Der Planner passt den Punkt an; die Messung braucht keine Nachmessung, `referenztest-dotnet` ist im Diff unberührt.
* verifizierbar: nein
* klasse: Plan-Wortlaut hinter der Messung

### F-8 INFO — Eingabe und Anweisungen bleiben bei `smallint`/`tinyint` eng; benannt, kein Widerspruch zur Setzung

* kategorie: INFO
* quelle: [ADR-0041](../plan/adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) Nr. 5 und 6
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/simulation/StructureParts.kt` · „\"BIGINT\" -> SqlType.Int64“; `src/main/kotlin/io/github/pt9912/tdswire/protocol/bulk/BulkLoadReader.kt` · „RpcTypeKind.INTN -> if (type.length == LONG_SIZE) SqlType.Int64 else SqlType.Int32“
* befund: `CREATE TABLE … SMALLINT` bleibt `TDS_UNSUPPORTED_SQL` (Form D in der Spezifikation nennt `SMALLINT` ausdrücklich als nicht erkannt), 1- und 2-Byte-Parameter bleiben `TDS_UNSUPPORTED_RPC` (Matrix, Abweichungszeile 15), die Beschreibungsdatei weist `smallint` ab. Alles benannt in [ADR-0041](../plan/adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) Nr. 5 und 6, im Slice §1 und in der Spezifikation; im Zielbild gelten diese Eingaben am echten Server, die Abweichung ist also gesetzt, nicht übersehen. Nicht benannt ist in `docs/user/` der Gegensatz zur neuen Aussage über `Int16`/`UInt8` (siehe F-3). Nach dem Merge von `main` liest `BulkFixedType` (INT1/INT2) die Typen weiter als `Int32`; die Folge für `Int16`/`UInt8`-Spalten im Massenladen ist durch den Wertebereich der Überführung gedeckt.
* verifizierbar: nein
* klasse: Setzung zu Eingabetypen ohne Doku-Zeiger

### F-9 INFO — Wertebereichsfehler: keine Aussage über Nummer oder Text des echten Servers

* kategorie: INFO
* quelle: `AGENTS.md` §3.8
* pfad: `spec/spezifikation.md` · „ein Wert außerhalb ist `TDS_UNSUPPORTED_TYPE`, kein Abschneiden“
* befund: Die Aussage gilt für einen Wert des Backends im Ergebnis (wie der Überlauf bei `Int32`, Matrix: „2147483648 ist `VALUE_OUT_OF_RANGE`“) und behauptet nichts über den echten Server; ob der Server 220 oder 8115 meldet, ist weder gemessen noch behauptet (die Spezifikation führt „Fehlernummern bei Typüberlauf“ weiter als nicht gemessen). Ausreichend belegt.
* verifizierbar: nein
* klasse: Aussage innerhalb des Belegs

### F-10 INFO — Merge-Probe gegen `main`: Konflikte nur in Dokumenten und der Spezifikation

* kategorie: INFO
* quelle: Maintainability
* pfad: `git merge-tree --write-tree HEAD main` (Stand `66fee6c1`)
* befund: Inhaltskonflikte in elf Dateien, keiner im Quelltext: `docs/messungen/referenzabgleich.md`, `docs/plan/planning/done/slice-katalog-und-struktur-an-messung-angleichen.md`, `docs/plan/planning/in-progress/roadmap.md`, `docs/plan/planning/done/slice-katalogspalten-smallint-tinyint-angleichen.md`, das Register `…/katalog-strukturabweisung-und-aenderungszaehler-weichen-vom-echten-server-ab/state.md`, `done/slice-abnahme-d-migrate.md`, `open/slice-antwort-tokens-zielbild-luecken-bauen.md`, `done/slice-ddl-abschluss-und-transaktionszustand-angleichen.md`, `done/slice-katalog-indizes-defaults-fremdschluessel.md`, `welle-testserver-manuell.md`, `spec/spezifikation.md`. Der Quelltext von `main` ändert `TdsType.kt` und `TypeInfoWriter.kt` (Dezimalzahl mit Byteangabe) ohne Textkonflikt; ein Übersetzungs- und Testlauf der gemergten Fassung wurde nicht gefahren, die einzige neue Auswertung von `SqlType` auf `main` ist `BulkFixedType` (Erzeuger, `Int32`/`Int64`). Das Roadmap-Ruhezeichen „Nichts in Arbeit.“ steht auf `main` weiter und ist im Konflikt zu streichen, wie `main` es selbst vermerkt.
* verifizierbar: ja (`git merge-tree`)
* klasse: Merge-Konflikte vor Übernahme

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| Frage c: alle Auswerter (`grep` über `SqlType.Int32/Int64`, `TdsType.Int4Type/Int8Type/IntNType`, `is SqlType`, `when` über `SqlType`/`TdsType` in `src/main`, `testserver/kotlin`, `src/integrator`) | geprüft, ohne Befund: `ColumnMapping`, `ValueMapping`, `TypeInfoWriter`, `ValueWriter`, `SimValues` (Literal, Parameter, Klasse), `BulkLoads`, `CatalogAnswers`, `RowRules` tragen die neuen Fälle; `RpcParameters`, `BulkLoadReader`, `SimColumnTypes`, `StructureParts` sind Erzeuger und bleiben nach Setzung; `RpcOutputs` nimmt `Int16`/`UInt8` als nullbare `INTN` über `ColumnMapping`; `HandleReturningBackend`, `FixedAnswers` nur `Int32`. Ein Befund zur Spezifikation und zu einem Kommentar steht in F-3 und F-4 |
| Frage d: öffentliche Fläche | geprüft, ohne Befund: `make flaeche` grün; `SPEC-059` nennt `Int16` und `UInt8` in der Liste, Änderungsprotokoll trägt „Inkompatibel für ein Kotlin-`when` ohne `else`“; `FlaecheEmbeddingTest` zieht sein erschöpfendes `when` nach und belegt je Typ COLMETADATA und Wertbytes (Mutation M1 rot) |
| Frage e: `simulation/SimIntegers.kt` | geprüft, ohne Befund: ein Objekt für Wertebereich, Zahl, Parameter und Massenladen der vier Ganzzahltypen; Verhalten für `Int32`/`Int64` gegenüber dem Vorstand gleich (Verengung `Int64`→`Int32` im Bereich, Erweiterung `Int32`→`Int64`, Bitgrenze 31/63 gleich dem Intervall); `error(\"kein Ganzzahltyp\")` ist durch `isInteger` an den Aufrufstellen gedeckt |
| `protocol/token`, `protocol/result` (`TdsType.Int1Type/Int2Type`, `TypeCode`, Wertbytes) | geprüft, ohne Befund: INT1 ohne Vorzeichen, INT2 mit Vorzeichen Little-Endian (Handvektoren `D1 00 80`, `D1 FF 7F`, `D1 01 FF`); nullbare Spalte mit fester Form ist `INVALID_TYPE` wie zuvor |
| `testserver/kotlin` (`RowRules`, `TableStoreTest`) | geprüft, ohne Befund; Typnamen `smallint`/`tinyint` im Leser bleiben abgewiesen (Setzung Nr. 6) |
| `docs/user/*` (alle fünf Dateien auf `SqlType`, `smallint`, `tinyint`, `Int16`, `UInt8`, `max_length`, `precision`, `BITN`, „bisher“, „noch nicht“, „soll“, „nicht gebaut“ gegrept und die geänderten Absätze gelesen) | geprüft; einziger Befund F-6; weitere Treffer von `max_length`/`precision` sind YAML-Beispiele des Prüfclients ohne Bezug |
| Messprotokoll `docs/messungen/referenzabgleich.md` (Zeilen M-16, M-17, Zuordnung) | geprüft, ohne Befund: Zeile „angeglichen“ mit Träger, `smallint`/`tinyint` in M-17 gegen `MessungBulkBelegTests` belegt |
| Haken, `harness/README.md` | geprüft, ohne Befund: keine `- [x]` gesetzt; `harness/README.md` und `harness/conventions.md` im Diff unberührt (Zellenlängen nicht betroffen) |
| Roadmap (Frage h) | geprüft, ohne Befund: „Nichts in Arbeit.“ entfernt, Slice unter „in `in-progress/`“ eingetragen, Drift-Log-Zeile trägt nur den nachgezogenen Link; bei Merge siehe F-10 |
| Hard Rules `AGENTS.md` §3.1 (Docker-only), §3.2 (Suppression), §3.6 (Gates) | geprüft, ohne Befund: kein `@Suppress`, keine Änderung an `config/detekt/detekt.yml` oder Gate-Dateien, alle Läufe über `make`/`docker` |

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 5 |
| LOW | 1 |
| INFO | 4 |

**Finding-Klassen dieses Laufs:** Bau weicht vom Wortlaut einer Accepted-ADR ab · Fortschreibung lässt zweite Fundstelle stehen · Überführungsregel im Code und Test, nicht in der Spezifikation · Plan-Tabelle ohne berührte Datei · Zweite Fundstelle hinter der Messung · Plan-Wortlaut hinter der Messung · Setzung zu Eingabetypen ohne Doku-Zeiger · Aussage innerhalb des Belegs · Merge-Konflikte vor Übernahme

## Verdikt

**Merge-blockierend:** ja — fünf MEDIUM (F-1 bis F-5), kein HIGH. Der Code selbst trägt: Gates grün, die Drahttypen stimmen mit beiden Aufnahmen überein, die Mutationen M1 bis M7 färben je mindestens einen Test rot. Die Findings betreffen Spezifikation, Matrix, Plan und einen Kommentar, nicht das Verhalten der Antwortspalten.

**Übergabe:** Findings gehen an den Implementer; F-1 (Schärfung oder Vermerk zur ADR) an den Architect; die Anpassung von DoD-Punkt 3 (F-7) an den Planner. Die Finding-Klassen gehen in die Slice-Closure §7. Dieser Report ersetzt keine Verifikation.
