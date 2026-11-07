# Review-Report: slice-schreibpfad-bulk-load — 2026-11-07

**Review-Art:** Code (gegen Plan, ADR und Hard Rules; nicht gegen die DoD, das ist die Frage des Verifiers).

**Gegenstand:** Diff `ed90c3ba..5c3843b9` (49 Dateien, Commits `d6f6e630`, `329f7f8d`, `5c3843b9`), Zweig `worktree-agent-a4845d02c1beaf126`. `main` steht inzwischen auf `32fcc71b`; Konfliktbewertung in F-7.

**Skill:** `.harness/skills/reviewer.md` @ `5c3843b9` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-07

**Eingangs-Kontext** (die Verträge, gegen die geprüft wurde):

- Slice `slice-schreibpfad-bulk-load` (Plan, DoD, Rückführungen, §6 Risiken), `welle-testserver-manuell`
- [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) (Accepted: Callback, `onInsertBatch`, Liste der öffentlichen Namen, Nr. 5 und Nr. 6), [ADR-0021](../plan/adr/0021-oeffentlicher-integrations-einstieg.md), [ADR-0005](../plan/adr/0005-rpc-und-prepared-statements.md) (PLP)
- `SPEC-001`, `SPEC-007`, `SPEC-035`, `SPEC-036`, `SPEC-040`, `SPEC-059`; [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit), [TDS-QA-05](../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [TDS-QA-07](../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität)
- `AGENTS.md` §3.1 bis §3.8; Grundsatz des Nutzers „Spec ist Zielbild“ (der echte SQL Server ist die Referenz, Massenladen dort nicht gemessen; Träger: `slice-qa-vergleichslauf-d-messungen-bulk-merge`)

## Selbst gefahren

| Lauf | Ergebnis |
|---|---|
| `make gates` | Exit 0; die drei Gruppen wurden bei passendem Stempel übersprungen |
| `make gates FORCE=1` | Exit 0 (Gruppen `werkzeug`, `tests`, `stempel` gelaufen, Stempel liegen an `5c3843b9`) |
| `make test`, `make flaeche`, `make docs-check` | grün; `docs-check`: 1090 Dateien, 0 Befunde |
| `make e2e` | Exit 0 |
| `make matrix-zitate` | erster Lauf rot (`TelemetryJsonTest > Die Kennung der Annahme steht escaped im Feld principal` im Modul `testserver`, Datei nicht im Diff, Host unter Last, siehe F-14); zweiter Lauf grün: 1101 bestandene Testfälle, 272 Zitate, 0 ohne PASSED |
| `make spec-abdeckung` | nicht gefahren (kein Gate; Aufgabe des Verifiers) |
| d-migrate (Digest `sha256:308f2775…`) gegen zwei Testserver aus dem Abbild des Stands | L1 `data import` 3 Zeilen Exit 0, Export dieselben Zeilen mit Umlauten und NULL; L3 `data transfer` Exit 0, Export A und B `cmp` byte-gleich; L6 zweiter Import Exit 5 „Backend error.“; L7 `--chunk-size 2` mit doppeltem Schlüssel in Nachricht 2: Exit 5, Bestand hat Schlüssel 1, 2, 3, 11, 12 (Nachricht 1 bleibt, Nachricht 2 ganz abgewiesen); H2 500 000 Zeilen mit `-Xmx64m`: Exit 5 („incomplete response“), Container Exit 3, Log `Terminating due to java.lang.OutOfMemoryError: Java heap space`. Die Messzahlen des Protokolls (Exit-Codes, byte-gleich, OOM-Grenze) sind wiederholt; die Eingabedateien sind meine eigenen (Skript im Scratchpad, nicht im Repo), nicht die des Protokolls |
| Mutationen | siehe Abschnitt Mutationen |

## Mutationen (selbst gefahren)

Drei Wegwerf-Kopien des Stands, `docker build --target test` mit `--continue`; Mutationen je Lauf gemeinsam angewandt, die Zuordnung der roten Tests ist aus den Assertions gelesen, nicht aus Einzelläufen.

| Lauf | Mutation | Rot |
|---|---|---|
| A | ein Aufruf von `onInsertBatch` je Zeile | `SimulationBackendBulkTest > die Zeilen gehen in einem Aufruf …`, `MassenladenEmbeddingTest > eineNachricht0x07Laedt…` |
| A | ungültiger Wert wird zu NULL statt Abbruch (Teilbestand) | `SimulationBackendBulkTest > ein Wert, der nicht passt, ruft keinen Callback` |
| A | IGNORE-Bit nicht gelesen | `BulkDispatchTest > das IGNORE-Bit verwirft …` |
| A | `bulk.clear()` beim RPC entfällt | `BulkDispatchTest > das Ziel bleibt bis zur naechsten anderen Anfrage` |
| A | Leser gibt Zeilen zurück, sobald die Nachricht endet (ohne DONE) | `BulkLoadReaderTest > jede Teilnachricht …`, `eine fehlerhafte Struktur …` |
| B | Prüfung `MAX_COLUMNS` / Byte hinter DONE / Spaltenzahl 0 entfällt (drei Mutationen) | `BulkLoadReaderTest > eine fehlerhafte Struktur …` (kein Einzellauf je Mutation) |
| B | Länge über Typlänge / ungerade Textlänge / exakte INTN-Länge entfällt (drei Mutationen) | `BulkLoadReaderTest > ein fehlerhafter Wert …` (kein Einzellauf je Mutation) |
| B | keine Vorzeichenerweiterung bei `INTN` | `BulkLoadReaderTest > die Ganzzahlen folgen …` |
| B | Int32-Bereichsprüfung entfällt | `SimulationBackendBulkTest > ein Wert, der nicht passt …` |
| B | Prüfung doppelter Spalten entfällt | `SimulationBackendBulkTest > eine fehlerhafte Zuordnung …` |
| C | `INSERT BULK` löscht das Ziel nicht mehr | `BulkDispatchTest > das Ziel bleibt …` |
| C | Bestand: Duplikate innerhalb der Nachricht / NULL in nicht nullbarer Spalte / Textschlüssel mit Schreibweise / Dezimalschlüssel ohne `stripTrailingZeros` / Typprüfung entfallen (fünf Mutationen) | `TableStoreTest > eine fehlerhafte Nachricht …`, `Dezimal- und Textschluessel …` |
| C | **`@Synchronized` an `TableStore.insertBatch` entfällt** | **kein Test rot** (F-2) |
| keine | `BulkLoadFuzzTest` | in keinem der drei Läufe rot (F-10) |

Der Lauf D (Sonde, nicht im Repo) ist in F-1 beschrieben.

## Findings

### F-1 MEDIUM — Heap-Bedarf einer gültigen Nachricht ein Vielfaches ihrer Größe; Zusage und Schutz breiter als der Beleg

* kategorie: MEDIUM
* quelle: [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität); `AGENTS.md` §3.8; [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 5 (i)
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/protocol/bulk/BulkLoadReader.kt` · „die Grenze der Zeilen schützt den Speicher vor einer Nachricht an der Grenze von 16 MiB mit Zeilen von zwei Byte“; `spec/spezifikation.md` · „der Speicherbedarf ist mindestens ihre Größe“
* befund: Die Grenzen 1024 Spalten und 262 144 Zeilen begrenzen nicht das Produkt: eine Nachricht von 15,7 MiB mit 16 000 Zeilen zu je 1024 nullbaren `INTN`-Spalten, alle NULL (1025 Byte je Zeile), hält nach dem Leser 63 MiB mehr als vorher und endet in der Überführung (eine Map je Zeile) mit `OutOfMemoryError` bei 128 MiB Heap (Wegwerf-Probe im Scratchpad, einmal gefahren, Test-JVM des Repos). Der Testserver startet mit `-XX:+ExitOnOutOfMemoryError`; ein einziger angemeldeter Client beendet damit den Prozess für alle Sitzungen. „Mindestens ihre Größe“ ist wahr, sagt aber nichts über das gemessene Vielfache; gemessen ist nur die Größe 15,9 MB gegen 32 MiB (Spike b12) und der Bestand mit 130-Byte-Zeilen.
* verifizierbar: ja (Probe mit 1024 Spalten und 16 000 NULL-Zeilen gegen `BulkLoadReader` und `BulkLoads`; kein Gate deckt es)
* klasse: Grenze begrenzt nicht das Produkt, Speicherzusage ohne Vielfaches

### F-2 MEDIUM — Die Sperre von `TableStore.insertBatch` trägt kein Test

* kategorie: MEDIUM
* quelle: [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 5/6 (Atomarität liegt beim Stapel-Callback des Integrators); Reviewer-Skill „Eigencode, dessen Entfernung keinen Test rot macht“
* pfad: `testserver/kotlin/io/github/pt9912/tdswire/testserver/TableStore.kt` · „fun insertBatch(schema: String, name: String, batch: List<Map<String, Any?>>): Long“ (mit `@Synchronized`)
* befund: Ohne `@Synchronized` an `insertBatch` bleibt jeder Test grün (Lauf C). `TableStoreTest > gleichzeitiger Zugriff verliert nichts` fährt nur `catalog()` und `replace`; zwei Sitzungen, die dieselbe Tabelle laden, sind nirgends getestet. Ohne die Sperre können zwei Nachrichten mit demselben Schlüssel die Prüfung `rowKey !in table.keys` beide bestehen (doppelte Zeile, Zähler 2 statt Fehler), und die `ArrayList` der Zeilen wird gleichzeitig geändert.
* verifizierbar: ja (Mutation per `sed`, `make test` bleibt grün)
* klasse: Eigencode ohne Test (Sperre, Atomarität)

### F-3 MEDIUM — Fortschreibung lässt drei zweite Fundstellen stehen

* kategorie: MEDIUM
* quelle: Maintainability; Reviewer-Skill „Fortschreibung lässt die zweite Fundstelle stehen“; `AGENTS.md` §3.7, §3.8; [TDS-QA-05](../../spec/lastenheft.md#tds-qa-05--wartbarkeit) (unterstützte Nachrichten dokumentiert)
* pfad: (a) `src/main/kotlin/io/github/pt9912/tdswire/simulation/SimModel.kt` · „dieser Stand der Bibliothek ruft ihn nicht, das Massenladen ist nicht gebaut“; (b) `docs/plan/kompatibilitaetsmatrix.md` · „andere Pakettypen (`PRE_TDS7_LOGIN`, `BULK_LOAD`, `FEDERATED_AUTH`, `SSPI`) | nicht unterstützt: als Typ bekannt, im Automaten `OTHER_MESSAGE`, die Verbindung endet ohne Antwort“; (c) `docs/user/testserver-manuell.md` · „Grenzen: Gespeichert wird die Struktur, keine Zeilen.“
* befund: (a) Die öffentliche KDoc von `SimTable` sagt dem Integrator, die Bibliothek rufe `onInsertBatch` nie; sie ruft es bei jeder Nachricht 0x07. (b) Die Matrix führt `BULK_LOAD` als nicht unterstützt und als `OTHER_MESSAGE`; der Automat führt ihn jetzt als Anfrage, und die Zeile nennt Typen, Zustand und Fehlerfälle nicht; `make matrix-zitate` bleibt grün, weil es nur Testnamen prüft. (c) Der Katalog-Abschnitt der Nutzer-Doku widerspricht dem neuen Abschnitt „Daten laden“ derselben Datei. Grenzfall zur Kommentar-Regel (HIGH, „Kommentar trägt keine der Klassen“): (a) ist ein falsch gewordener Kommentar im Zustand, nicht die verworfene Alternative; hier als MEDIUM geführt.
* verifizierbar: ja (Grep nach „ruft ihn nicht“, „nicht unterstützt“ und „keine Zeilen“; `make docs-check` fängt es nicht)
* klasse: Fortschreibung lässt die zweite Fundstelle stehen

### F-4 MEDIUM — Plan-Tabelle und Kopf „Berührte Spec-Stellen“ nennen berührte Dateien und Stellen nicht

* kategorie: MEDIUM
* quelle: Reviewer-Skill „Plan-Tabelle nennt die berührten Dateien nicht“
* pfad: `docs/plan/planning/in-progress/slice-schreibpfad-bulk-load.md` · „**Berührte Spec-Stellen:** Protokollteil Pakettypen und Dispatch (Katalog der Pakete) · SPEC-036 (`INSERT BULK`, `SET FMTONLY`) · SPEC-035 (Callbacks) · SPEC-059 (falls der Vertrag oder die Callbacks wachsen) · SPEC-040“
* befund: Im Diff stehen, aber nicht in der Tabelle: `src/integrator/…/MassenladenEmbeddingTest.kt` (neu, 156 Zeilen), `WireClient.kt` und `FlaecheEmbeddingTest.kt` (beide `src/integrator`), das Messprotokoll der Sonde und die Verweise in sieben `done/`- und `open/`-Slices, im Register und in der Welle (nur die Pfade der Lifecycle-Verschiebung). Der Kopf nennt `SPEC-001` (Zustand, Pakettyp) und `TDS-FA-11.a` (Schritt 10) nicht, die der Diff ändert.
* verifizierbar: ja (Abgleich `git diff --stat ed90c3ba..HEAD` gegen §3)
* klasse: Plan-Tabelle nennt die berührten Dateien nicht

### F-5 MEDIUM — Abschnittsnummern von `MS-TDS` ohne belegbare Quelle, mindestens eine vermutlich falsch

* kategorie: MEDIUM
* quelle: `AGENTS.md` §3.8 („Abschnittsnummern externer Standards stehen nur mit belegbarer Quelle“)
* pfad: `src/test/kotlin/io/github/pt9912/tdswire/protocol/bulk/BulkLoadReaderTest.kt` · „2.2.5.5 (TYPE_INFO, Wertebytes), 2.2.7.4 (COLMETADATA), 2.2.7.17 (ROW) und 2.2.7.6 (DONE)“; ebenso `docs/messungen/bulk-load-2026-11-07.md` und `BulkLoadReader.kt`/`BulkValueReader.kt` („2.2.6.1“, „2.2.5.5“), `spec/spezifikation.md` · „(`MS-TDS` 2.2.6.1)“
* befund: Es sind die ersten Abschnittsnummern von `MS-TDS` im Repo; das Dokument liegt nicht im Repo, und keine Stelle nennt Ausgabe oder Fundort. Nach der Erinnerung des Reviewers (nicht belegt, kein Zugriff auf `MS-TDS`) ist ROW nicht 2.2.7.17; der Verifier oder Planner prüft die Nummern gegen die Quelle.
* verifizierbar: nein (kein Gate; Abgleich gegen das Original von Hand)
* klasse: Abschnittsnummer eines Standards ohne Quelle

### F-6 MEDIUM — „Gedeckt ist, was d-migrate sendet“ gilt für drei der elf Typen, nicht für alle

* kategorie: MEDIUM
* quelle: `AGENTS.md` §3.8
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/protocol/bulk/BulkLoadReader.kt` · „Gedeckt ist, was d-migrate 1.12.0 mit mssql-jdbc 13.4.0 sendet“; `BulkLoadReaderTest.kt` · „kürzer als die Typlänge 17, wie der Treiber ihn sendet“
* befund: Aufgezeichnet ist die Nutzlast mit `INTN`(8), `NVARCHAR` und `BIGVARCHAR`. `DATEN`, `TIMEN` und `DATETIME2N` sendet d-migrate nicht (das Messprotokoll und `BulkValueReader` sagen selbst: Datum, Zeit und Zeitpunkt kommen als `VARCHAR`); `DECIMALN`, `BITN` und `INTN` mit 1, 2, 4 Byte sind Handvektoren nach `MS-TDS` ohne Mitschnitt, und die Länge von fünf Byte für `DECIMALN` „wie der Treiber ihn sendet“ ist für das Massenladen nicht aufgezeichnet (die Zeile stammt aus dem RPC, Spike a7). Die Läufe L2 und L4 beweisen nur, dass der Leser die Bytes des Treibers annimmt, nicht deren Länge.
* verifizierbar: nein (Mitschnitt der Dezimalspalte fehlt)
* klasse: Aussage breiter als ihr Beleg

### F-7 INFO — Konflikt mit `main` (`32fcc71b`): zwei Dateien mit Inhaltskonflikt, ein Folgefehler beim Übersetzen zu erwarten

* kategorie: INFO (Aktion beim Implementer, Merge-Voraussetzung)
* quelle: Maintainability
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/RequestLoop.kt` · „message.type == PacketType.BULK_LOAD -> when (val loaded = bulk.answer(message))“; `docs/plan/planning/open/slice-qa-vergleichslauf-d-messungen-bulk-merge.md`
* befund: `git merge-tree` meldet Konflikte in diesen beiden Dateien (die Roadmap, die Spec und die Welle mischen sich selbst). In `RequestLoop` hat `main` `execute(text, params, layout: CallLayout?, via, …)` und `refuse(…, formOf(message))` eingeführt; der Zweig ruft `execute(…, ResponseForm.BATCH, via)` positional. Die Auflösung ist nicht nur Text: der Aufruf in `BulkDispatch`-Anbindung muss `layout = null` nehmen, und die Antwortform für 0x07 (`formOf(message)` liefert `BATCH`) ist eine Setzung, die der Slice zu den Antwort-Tokens an `main` nicht kennt. Ob `DispatchSupport` und die Tests nach dem Merge übersetzen, ist nicht geprüft (nicht gemergt, wie beauftragt).
* verifizierbar: ja (Merge in einer Kopie, `make test`)
* klasse: Konflikt mit parallelem Slice an `RequestLoop`

### F-8 LOW — RESETCONNECTION leert das Ziel des Massenladens nicht

* kategorie: LOW
* quelle: Maintainability; `SPEC-001` (Sitzungsrahmen)
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/RequestLoop.kt` · „ready.live.onSessionReset { handles.clear() }“
* befund: Der Sitzungsreset leert die Handles, nicht `BulkDispatch.target`. Eine Nachricht 0x07 mit gesetztem RESETCONNECTION lädt in das Ziel vor dem Reset. Praktisch folgt auf einen Reset immer ein SQL_BATCH (das Ziel entfällt dort); der Fall setzt einen Client voraus, der gegen die Reihenfolge des Treibers sendet.
* verifizierbar: ja (Test mit Reset-Bit auf der Nachricht 0x07)
* klasse: Sitzungszustand nicht vom Reset erfasst

### F-9 LOW — Kein Test für ATTENTION während des Massenladens

* kategorie: LOW
* quelle: [TDS-QA-07](../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität) (Messmethode nennt ATTENTION)
* pfad: `src/test/kotlin/io/github/pt9912/tdswire/protocol/dispatch/BulkDispatchTest.kt` · „override suspend fun cancel(sessionId: String, requestId: String) = Unit“
* befund: Kein Test sendet ATTENTION, solange `load` läuft; das Messprotokoll führt es als nicht gemessen. Der Pfad ist der allgemeine (RUNNING, Abbruch des Flows, `cancel` der Hülle ist leer); ein Fehler ist nicht gezeigt, aber das Verhalten (DONE mit ATTENTION, eine Folgeabfrage) ist für 0x07 nicht belegt. Offene Frage für die Folge: ein Stapel-Callback des Integrators, der bei Abbruch mitten in der Weitergabe steht.
* verifizierbar: ja (Test mit hängendem `load` und ATTENTION)
* klasse: Negativtest für neuen Pakettyp fehlt

### F-10 LOW — `BulkLoadFuzzTest` belegt Absturzfreiheit auf einer Ausgangsnutzlast, nichts darüber hinaus

* kategorie: LOW
* quelle: [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität); Slice §6 (Fuzz-Umfang)
* pfad: `src/test/kotlin/io/github/pt9912/tdswire/stability/BulkLoadFuzzTest.kt` · „Das Orakel ist schwach und unabhängig vom Leser“
* befund: Bewertung des Orakels: für Absturzfreiheit trägt es (`guarded` fängt nur `ProtocolViolation`, jede andere Ausnahme fällt durch den Test) und ist unabhängig vom Leser. Es fängt keine fehlende Prüfung: in Lauf B (Längenprüfungen entfernt) blieb er grün, weil `ByteReader.need` die Grenzen hält. Die Ausgangsnutzlast hat nur `INTN`(8), `NVARCHAR`, `BIGVARCHAR`; die Pfade für `DECIMALN`, `DATEN`, `TIMEN`, `DATETIME2N`, NBCROW und PLP werden nie verändert; die Behauptung „beide Ausgänge kommen vor“ erfüllt schon die unveränderte Nutzlast. Das Protokoll nennt ihn „nicht mutiert“.
* verifizierbar: ja (Mutation der Typpfade, Fuzz mit einer Nutzlast aller elf Typen)
* klasse: Fuzz-Eingabe deckt nicht alle Pfade

### F-11 LOW — Setzungen nur im KDoc oder im Protokoll, nicht in der Spezifikation

* kategorie: LOW
* quelle: `AGENTS.md` §3.8; Slice §6 (Setzung bis zur Gegenmessung)
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/protocol/bulk/BulkValueReader.kt` · „Ein `BIGVARCHAR` und `BIGCHAR` wird als Windows-1252 gelesen, gleich welche Kollation die Spalte trägt“; `testserver/kotlin/io/github/pt9912/tdswire/testserver/TableStore.kt` · „Der Bestand wächst mit den Nachrichten ohne Grenze“
* befund: Als Setzung gekennzeichnet sind: DONE-Zähler 0, Kollation `SQL_Latin1_General_CP1_CI_AS`, Verengung Int64 auf Int32, Grenzen 1024 und 262 144, Textschlüssel ohne Schreibweise (Spec, Code oder Nutzer-Doku). Nur im KDoc stehen Windows-1252 für `VARCHAR` und die Dezimalzahl, die kürzer als die Typlänge sein darf (beides toleranter als der Standard, für ASCII-Text folgenlos). Die Obergrenze des Bestands ist als „ohne Grenze“ benannt (KDoc, Nutzer-Doku); eine Begründung gegen `TDS-QA-02` und ein Träger (ADR oder Carveout) fehlen. Die Spec-Stellen nennen die Setzungen ohne den Träger der Gegenmessung (`slice-qa-vergleichslauf-d-messungen-bulk-merge`); er steht im Slice §6 und im Messprotokoll.
* verifizierbar: nein
* klasse: Setzung ohne Träger an der Stelle der Zusage

### F-12 INFO — Messprotokoll: Belege wiederholt, Erzeuger nicht im Repo, Größenordnung benannt

* kategorie: INFO
* quelle: Reviewer-Skill „Messzahl aus einem Erzeuger, der nicht im Repo liegt“
* pfad: `docs/messungen/bulk-load-2026-11-07.md` · „Erzeuger der Zahlen“, „Aufzeichnung der Nutzlast“
* befund: Das Protokoll nennt die Herkunft und die Grenze selbst (Wegwerf-Skript, vorübergehende Instrumentierung, Zeiten als Größenordnung, Mutationserzeuger nicht im Repo); die Hex-Nutzlast ist als Vektor in `BulkLoadReaderTest`, `BulkDispatchTest` und `MassenladenEmbeddingTest` wiederholbar. Exit-Codes, Byte-Gleichheit und die OOM-Grenze (64 MiB bei 500 000 Zeilen) habe ich wiederholt (Tabelle oben); die Mutationen M02, M03, M04, M06, M07, M09, M01 haben bei mir die genannten Testfälle rot gemacht. Das Protokoll nennt als Stand `329f7f8d`; zwischen `329f7f8d` und `5c3843b9` ändert sich kein Quelltext in `src/` oder `testserver/`.
* verifizierbar: ja
* klasse: Messzahl aus nicht committetem Erzeuger (benannt)

### F-13 INFO — Custom-Backend ohne Massenladen ist mit ADR-0033 und ADR-0021 vereinbar

* kategorie: INFO
* quelle: [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Alternative A (verworfen) und Nr. 4; [ADR-0021](../plan/adr/0021-oeffentlicher-integrations-einstieg.md)
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/runtime/SimulationBulk.kt` · „if (backend is SimulationBackend) SimulationBulk(backend) else backend“
* befund: `QueryBackend` bleibt unverändert, die Fähigkeit ist intern; die Entscheidung der ADR hat die Erweiterung des Ports (Alternative A, Weitergabe Z2) verworfen, das Lastenheft nennt das Massenladen nur für Simulations-Backend und Testserver. Kein Zielbild-Träger nötig. Eine Hülle des Integrators um `SimulationBackend` (Delegation) schaltet das Massenladen still ab (`INSERT BULK` als Text ergibt dort Zähler 0, die Nachricht 0x07 `TDS_UNSUPPORTED_SQL`); `SPEC-001` sagt „heute das Simulations-Backend“, die Einbettungs-Doku `docs/user/einbettung-und-betrieb.md` nennt das Massenladen nicht. `make flaeche` grün: kein neuer öffentlicher Name.
* verifizierbar: ja
* klasse: Fähigkeit am konkreten Typ statt am Vertrag

### F-14 INFO — `make matrix-zitate` einmal rot durch einen Test außerhalb des Diffs

* kategorie: INFO
* quelle: Maintainability
* pfad: `testserver/test/kotlin/io/github/pt9912/tdswire/testserver/TelemetryJsonTest.kt` · „Die Kennung der Annahme steht escaped im Feld principal“
* befund: Der frisch gebaute Lauf (`--no-cache-filter test`) war rot an genau diesem Test, während gleichzeitig weitere Docker-Builds liefen; die Datei ist im Diff nicht berührt, der zweite Lauf war grün. Verdacht auf Abhängigkeit von Last, nicht untersucht.
* verifizierbar: nein (nicht wiederholt rot)
* klasse: Test rot unter Last

### F-15 INFO — `SimColumn.maxLength` wirkt nicht auf das Massenladen

* kategorie: INFO
* quelle: Zielbild (echter Server weist zu lange Texte ab, nicht gemessen); [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate) Negative („Schreibzugriff gegen die Struktur“)
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/simulation/BulkLoads.kt` · „SqlType.UnicodeText -> (value as? String)?.let { Converted.Value(it) }“
* befund: Ein Text über der `maxLength` der Beschreibung wird angenommen; die Länge begrenzt nur der Typ, den der Client in COLMETADATA nennt. Weder Spec noch Messprotokoll nennen das als Setzung. Für die Gegenmessung ein Kandidat.
* verifizierbar: ja
* klasse: Strukturprüfung der Länge fehlt

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `protocol/bulk` (Leser: COLMETADATA-Varianten, Flags, Längenpräfixe, NULL je Typ, Vorzeichen und Skala von DECIMAL, DATE/TIME/DATETIME2, PLP und NBCROW abgewiesen, DONE, Reste hinter DONE, Grenzen) | geprüft gegen die Wiedergabe von `MS-TDS` aus dem Gedächtnis des Reviewers (Quelle nicht im Repo); keine Abweichung gefunden außer F-5, F-6, F-10 |
| `protocol/dispatch` (Zustand, IGNORE, 0x07 ohne `INSERT BULK`, Rpc löscht das Ziel, Verbindung bleibt synchron, Class 20 nur bei kaputter Nachricht) | geprüft, außer F-8, F-9 ohne Befund; ein wohlgeformter, aber nicht unterstützter Typ (PLP, FLOAT, DATETIME) ergibt ebenfalls Class 20 und schließt, wie derselbe Fall im RPC; das ist die Linie des Bestands, kein Befund |
| `simulation` (Form B, Zuordnung, Überführung, Vorprüfung vor dem ersten Aufruf, `onInsert`-Rückfall, Fehlertexte ohne Werte) | geprüft, ohne Befund (außer F-15) |
| `testserver/TableStore.kt` (Schlüssel-, Typ-, NULL-Prüfung, ganz oder gar nicht, Ersatz bei `ALTER`) | geprüft; Befunde F-1, F-2; die Atomarität der Nachricht ist durch L7 am fremden Client bestätigt |
| `runtime`, `port` (Hülle, interne Schnittstelle) | geprüft, ohne Befund (F-13 ist Hinweis) |
| Öffentliche Fläche (`SPEC-059`, Liste der ADR Nr. 4) | `make flaeche` grün; kein neuer öffentlicher Name; geprüft, ohne Befund |
| Hard Rules `AGENTS.md` §3.1, §3.2, §3.5, §3.6 | kein Host-Aufruf, kein `@Suppress`, keine ADR geändert, keine Gate-Lockerung im Diff; geprüft, ohne Befund |
| Haken (Reviewer-Skill „Haken vor der Verifikation gesetzt“) | im Diff kein `- [x]` in DoD oder Nachweis-Pflichten des Slice; Closure-Notiz §7 leer; geprüft, ohne Befund |
| `harness/README.md` | im Diff nicht berührt; Zellenlänge nicht betroffen; geprüft, ohne Befund |
| `docs/user/testserver-manuell.md` (Abschnitt „Daten laden“) | Befehl und Grenzen stimmen mit den Läufen L1, L3, L6, L7 überein; Befund nur der Katalog-Abschnitt (F-3 c) |
| `spec/spezifikation.md` (Schritt 10, Pakettyp, Form B, Überführung, Änderungsprotokoll) | Schichtfolge eingehalten (keine neue Zusage ohne Lastenheft-Anker); Setzungen gekennzeichnet; geprüft, ohne Befund außer F-5, F-11 |
| Geltung für `spec/architecture.md` | nicht berührt, nennt keine Pakettypen; geprüft, ohne Befund |

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 6 |
| LOW | 4 |
| INFO | 5 |

**Finding-Klassen dieses Laufs:** Grenze begrenzt nicht das Produkt, Speicherzusage ohne Vielfaches · Eigencode ohne Test (Sperre, Atomarität) · Fortschreibung lässt die zweite Fundstelle stehen · Plan-Tabelle nennt die berührten Dateien nicht · Abschnittsnummer eines Standards ohne Quelle · Aussage breiter als ihr Beleg · Konflikt mit parallelem Slice an `RequestLoop` · Sitzungszustand nicht vom Reset erfasst · Negativtest für neuen Pakettyp fehlt · Fuzz-Eingabe deckt nicht alle Pfade · Setzung ohne Träger an der Stelle der Zusage

## Verdikt

**Merge-blockierend:** ja — sechs MEDIUM, kein HIGH. F-1 und F-2 tragen ein konkretes Versagen (Prozessende durch eine gültige Nachricht; doppelter Schlüssel bei zwei Sitzungen ohne Test der Sperre). F-3 bis F-6 sind Belegs- und Fortschreibungsfehler, die vor der Closure zu schließen sind. F-7 ist Voraussetzung für den Merge.

**Übergabe:** Findings gehen an den Implementer; die Finding-Klassen gehen in die Slice-Closure §7. Dieser Report ersetzt keine Verifikation; DoD-Konformität (Haken, `make spec-abdeckung`, Messpunkte (i) bis (iv)) prüft der Verifier. Nicht gemessen und nicht Teil dieses Reviews: das Massenladen am echten SQL Server (Träger `slice-qa-vergleichslauf-d-messungen-bulk-merge`).
