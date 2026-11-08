# Review-Report: slice-katalog-und-struktur-an-messung-angleichen (Teil a, HEAD 85d970bd) — 2026-11-08

**Review-Art:** Code (gegen Plan, Entscheidungen und Hard Rules)

**Gegenstand:** Diff `46607765..85d970bd`, Zweig `worktree-agent-a95b80c5a5d35df3e`. Maßstab ist der verkleinerte Slice auf `main` `0b3e990c` (zwei Liefer-Punkte: Strukturabweisung, `CREATE TABLE` ohne Schlüssel); der Zweig trägt die alte Fassung des Slice (drei Punkte) in `next/`, deren Inhalt nicht gilt.

**Skill:** `.harness/skills/reviewer.md` @ `85d970bd` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-08

**Eingangs-Kontext:**

- Slice `slice-katalog-und-struktur-an-messung-angleichen` (verkleinerte Fassung auf `main`: Ziel, Abgrenzung, DoD, §3 Plan-Tabelle, §4 Weg auf `main`, §6 Risiken)
- [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) (Accepted); die später angenommenen ADRs zu Katalogspalten und Anweisungsart liegen nur auf `main` und sind hier nur Abgrenzung
- [SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends), [SPEC-036](../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig), SPEC-059
- [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-QA-07](../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität)
- Messdateien `referenztest-dotnet/Mitschnitt/messung/bulk-merge-katalog/{2022,2025}/katalog-struktur.messung`, Zeile M-19 in `docs/messungen/referenzabgleich.md`
- `AGENTS.md` §3.1 bis §3.8

**Selbst gefahren** (Host unter Last, andere Läufe parallel): `make gates FORCE=1` Exit 0 (Stages aus dem Layer-Cache, die Stempel-Gruppen liefen); `make clean-build` Exit 0 (`--no-cache`, 1200 bestandene Testfälle, die neuen Tests darunter); `make flaeche` grün; `make docs-check` 0 Befunde; `make e2e` grün (8 Stufen, 182 s, der Last-Flake der Stufe 7 trat nicht auf); `make matrix-zitate` Exit 0 (1200 PASSED, 304 Zitate, 0 ohne PASSED); d-migrate 1.12.0 (Digest `sha256:308f2775…`) gegen `make testserver-up` mit eigenem Namen und Port; Mutationen und Merge-Probe in Kopien unter dem Scratchpad. Der Arbeitsbaum des Zweigs ist unverändert.

## Gesamturteil

0 HIGH, 5 MEDIUM, 2 LOW, 3 INFO. Die Codes selbst sind echt: Nummer, State, Class und offene Verbindung stimmen mit den Messdateien beider Instanzen überein, und alle drei Mutationen der Codes sind rot. Offen sind zweite Fundstellen (Spezifikation, Nutzer-Doku, angenommene ADR), die Lücke der YAML-Datei und ein Konflikt, der beim Zusammenführen nicht als Textkonflikt auftritt: `TableStore.update` von `main` lehnt auf einer Tabelle ohne Schlüssel jede Änderung von mehr als einer Zeile ab (gefahren).

## Findings

### F-1 MEDIUM — `SPEC-035` sagt zugleich „Schlüssel nennt eine oder mehrere Spalten“ und „Schlüssel darf leer sein“

* kategorie: MEDIUM
* quelle: Reviewer-Skill „Fortschreibung lässt die zweite Fundstelle stehen“; [SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends)
* pfad: `spec/spezifikation.md` · „`primaryKey` nennt eine oder mehrere Spalten der Tabelle; sie müssen vorhanden und nicht nullbar“
* befund: Der Aufzählungspunkt zu `primaryKey` verlangt weiter mindestens eine Spalte, während der Punkt „Validierung“ darunter „der Schlüssel darf leer sein“ sagt. Der Punkt „Validierung“ leitet außerdem mit „gilt für die Kotlin-Schnittstelle und die Datei“ ein, die Datei verlangt aber `primary_key` mit mindestens einem Eintrag (siehe F-2).
* verifizierbar: ja (Grep über `spec/`; `SimDescriptionReader.kt` „mindestens ein Schlüsseleintrag erforderlich“)
* klasse: Fortschreibung lässt die zweite Fundstelle stehen

### F-2 MEDIUM — Die Datei des Testservers lehnt eine Tabelle ohne Schlüssel ab, die der Server selbst anlegt und d-migrate zurückliest

* kategorie: MEDIUM
* quelle: [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate); Maßstab „echter Server ist Referenz“; `AGENTS.md` §3.8
* pfad: `testserver/kotlin/io/github/pt9912/tdswire/testserver/SimDescriptionReader.kt` · „val keyEntry = required(f, "primary_key", map, path)“
* befund: Gefahren: `schema migrate --execute` legt `notizen` ohne Schlüssel an (Exit 0), `schema reverse` schreibt sie ohne `primary_key` zurück, und `TESTSERVER_BESCHREIBUNG` mit dieser Datei bricht den Start mit „Pflichtfeld primary_key fehlt“ ab. Dieselbe Tabelle ist per `CREATE TABLE` zulässig und per Datei nicht; die Spezifikation führt „Pflicht“ als Setzung, nennt den Widerspruch zur neuen Zusage aber nicht (Frage des Implementers an den Nutzer, siehe INFO-1).
* verifizierbar: ja (`make testserver-up` mit `TESTSERVER_BESCHREIBUNG=<reverse-Datei ohne Schlüssel>`)
* klasse: Zwei Wege zur selben Tabelle mit verschiedener Regel

### F-3 MEDIUM — Nutzer-Doku nennt die erlaubten Codes eines Backends ohne die drei neuen

* kategorie: MEDIUM
* quelle: Reviewer-Skill „Fortschreibung lässt die zweite Fundstelle stehen“ und „Nutzer-Doku nicht komplett gegen den Slice gelesen“; `AGENTS.md` §5 Regel 5
* pfad: `docs/user/einbettung-und-betrieb.md` · „erlaubt sind `TDS_BACKEND_ERROR`, `TDS_UNSUPPORTED_SQL`, `TDS_UNSUPPORTED_TYPE`, `TDS_SIM_UNKNOWN_OBJECT`, `TDS_SIM_INVALID_VALUE`, `TDS_SIM_KEY_VIOLATION`, `TDS_TIMEOUT`“
* befund: Die Spezifikation nimmt die drei Codes in das Vokabular der erlaubten `Failure`-Codes auf (`ErrorMapper.BACKEND_CODES`), diese Zeile der Tabelle „Typische Fehler“ in Aufgabe 2 bleibt bei der alten Liste. Ein Integrator, der nach ihr einen der Codes meldet, hält ihn für „außerhalb der erlaubten Menge“.
* verifizierbar: ja (Grep `erlaubt sind` in `docs/user/`)
* klasse: Fortschreibung lässt die zweite Fundstelle stehen

### F-4 MEDIUM — Die angenommene ADR-0033 sagt „`CREATE TABLE` mit Schlüssel“, der Bau und die Spezifikation das Gegenteil

* kategorie: MEDIUM
* quelle: [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Entscheidung 3; `AGENTS.md` §3.5; Reviewer-Skill „Fortschreibung lässt die zweite Fundstelle stehen“
* pfad: `docs/plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md` · „Die Bibliothek erkennt `CREATE TABLE` mit Schlüssel und `ALTER TABLE … ADD`“
* befund: Die ADR bleibt unverändert (richtig), aber weder der Slice noch der Diff nennt eine Folge-ADR oder einen Eintrag, der die Aussage „mit Schlüssel“ ablöst; `SPEC-036` Form D und `SPEC-035` sagen jetzt „Schlüssel optional“. Wer die ADR liest, findet die abgelöste Lesart.
* verifizierbar: ja (Grep „mit Schlüssel“ in `docs/plan/adr/`)
* klasse: Angenommene ADR widerspricht der Fortschreibung ohne Folge-ADR

### F-5 MEDIUM — Zusammenführen mit `main` bricht `UPDATE` auf einer Tabelle ohne Schlüssel, ohne Textkonflikt

* kategorie: MEDIUM
* quelle: Slice §4 „Konflikte mit anderen Zweigen“ und §6 (Risiko `MERGE`); [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate)
* pfad: `testserver/kotlin/io/github/pt9912/tdswire/testserver/TableStore.kt` · „for (row in changed) require(keys.add(keyOf(table, row))) { "Schlüssel nicht eindeutig" }“ (in `update`, nur auf `main`; der Zweig ändert nur `insertBatch`)
* befund: `git merge-tree` gegen `0b3e990c` meldet `TableStore.kt`, `StatementPlanner.kt` und `TableStoreTest.kt` als sauber zusammengeführt. In der zusammengeführten Fassung (Scratchpad-Kopie, Probe-Test) endet `onUpdate` auf einer schlüssellosen Tabelle mit zwei Zeilen und ohne Filter mit `IllegalArgumentException: Schlüssel nicht eindeutig` (`TableStore.update`), weil alle Zeilen den Schlüssel `[]` tragen; ein `UPDATE` oder `MERGE … WHEN MATCHED` mehrerer Zeilen kommt als `TDS_BACKEND_ERROR` beim Client an. Die Tests beider Seiten bleiben grün, weil keiner `update` auf einer schlüssellosen Tabelle ruft.
* verifizierbar: ja (Zusammenführung, dann ein Test `update` mit zwei Zeilen an `CREATE TABLE` ohne Schlüssel)
* klasse: Semantischer Konflikt in textlich sauberer Zusammenführung

### F-6 MEDIUM — Risiko „`MERGE` gegen eine Tabelle ohne Schlüssel“ hat im Diff weder Test noch benannte Grenze

* kategorie: MEDIUM
* quelle: Slice §6, letzter Punkt (Verifikation V-9 von `slice-schreibpfad-merge-output`); Reviewer-Skill „Zusage ohne ihre gemessene Bedingung im selben Satz“
* pfad: `spec/spezifikation.md` · „`CREATE TABLE` ohne Schlüssel gelingt: der Callback bekommt einen leeren Schlüssel (gemessen, M-19)“
* befund: Nach dem Zusammenführen weist `SimulationMerges.comparesKey` (auf `main`) ein `MERGE` gegen eine Tabelle mit leerem Schlüssel mit `TDS_UNSUPPORTED_SQL` ab (aus dem Code gelesen, nicht gefahren: der Vergleich nennt Spalten, der Schlüssel ist leer); der echte Server nimmt es an. Der Slice verlangt, den Fall als Test oder Grenze in `SPEC-036` zu benennen und zu entscheiden; im Diff steht keines von beidem (der Zweig kennt `MERGE` nicht, der Satz der Spezifikation „gelingt“ steht ohne die Grenze).
* verifizierbar: nein (kein Gate; erst nach dem Zusammenführen als Test fassbar)
* klasse: Offenes Risiko des Plans ohne Ausgang im Diff

### F-7 LOW — Der Text zu 2714 ist umformuliert, nicht gekürzt

* kategorie: LOW
* quelle: `AGENTS.md` §3.8; [SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) „der feste Text des Codes ohne den Namen des Objekts (Setzung …)“
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/protocol/error/ErrorCode.kt` · „There is already an object with this name in the database.“
* befund: Der gemessene Text lautet „There is already an object named '<Name>' in the database.“ (Messdatei `katalog-struktur.messung`, Fehlertoken 2714); der Code führt eine andere Wortwahl, KDoc und Spezifikation sagen „ohne den Namen“. Gefahren: d-migrate zeigt den festen Text im Bericht (`executionError`: „There is already an object with this name in the database.“, Exit 5, `FULL_ROLLBACK_CONFIRMED`) bei zwei gleichzeitigen `schema migrate` desselben neuen Objekts. Die Texte zu 2705 und 4902 sind echte Teilsätze.
* verifizierbar: ja (Vergleich Messdatei gegen `ErrorCode.kt`)
* klasse: Aussage über einen Text breiter als der Beleg

### F-8 LOW — Plan-Tabelle §3 nennt `docs/plan/spec-abdeckung.md` nicht, der Pfad der Tests stimmt nicht

* kategorie: LOW
* quelle: Reviewer-Skill „Plan-Tabelle nennt die berührten Dateien nicht“
* pfad: `docs/plan/planning/done/slice-katalog-und-struktur-an-messung-angleichen.md` · „Tests (Kotlin, `src/test`, `testserver/kotlin`)“
* befund: Der Diff berührt `docs/plan/spec-abdeckung.md` (erzeugte Matrix, Zähler und ein Zitat), die Tabelle nennt sie nicht; der Test des Testservers liegt unter `testserver/test/kotlin`, nicht `testserver/kotlin`. Die Lifecycle-Dateien (Roadmap, Welle, Register, Link-Nachzug in `open/`) sind Folge von `slice-mv`, nicht des Baus.
* verifizierbar: ja (Diff gegen Tabelle)
* klasse: Plan-Tabelle nennt die berührten Dateien nicht

### INFO-1 — Frage des Implementers: soll die YAML-Datei Tabellen ohne Schlüssel erlauben?

Bewertung (Entscheidung beim Nutzer): Für „Ja“ spricht der Maßstab „echter Server ist Referenz“ und der gefahrene Rundlauf aus F-2 (d-migrate schreibt eine schlüssellose Tabelle ohne `primary_key`, und warnt dort nur mit E008). Für „Nein“ spricht, dass die Datei ohnehin strenger als das Schema von d-migrate ist (`tables` ist Pflicht, Setzung) und der Heap-Bestand ohne Schlüssel keine Eindeutigkeit prüft. Jede der beiden Antworten verlangt, dass `SPEC-035` die Datei und die Validierung getrennt sagt (F-1).

### INFO-2 — Echtheit der Codes (Negativbefund zu Schwerpunkt a)

Aus den Fehlertoken beider Messdateien gelesen: 2714 (`9a0a0000`), State 6, Class 16; 2705 (`910a0000`), State 4, Class 16; 4902 (`26130000`), State 1, Class 16; je `DONE` Status `0x0002` (`ERROR`), Befehl `0x00fd`; die nachfolgende Anweisung derselben Verbindung antwortet, die Verbindung bleibt offen. 2022 und 2025 unterscheiden sich in diesen Fällen nicht. Nicht gemessen und im Diff als Folge richtig nicht behauptet: `ADD` mit doppelter Spalte innerhalb einer Anweisung (`ADD x INT, X BIT`) endet im Bau weiter mit `TDS_UNSUPPORTED_SQL`, der Server meldet dort wahrscheinlich 2705; d-migrate wertet nach den Läufen höchstens den Text aus (die Nummer steht nicht im Bericht), ob mssql-jdbc die Nummer anders behandelt als der Referenzclient, ist nicht gemessen.

### INFO-3 — Messprotokoll trägt „belegt durch …Test“

Die Zuordnung der Zeilen M-19 nennt Tests als Beleg („angeglichen … belegt durch `SimulationBackendStructureTest`“); `AGENTS.md` §5 Regel 7 trennt Messprotokoll und Abdeckung. Die Spalte „Zuordnung“ trug schon vorher Aussagen über den Bau; kein Verstoß, nur Hinweis an den Planner für die Closure.

## Mutationen (selbst gefahren, Kopien unter dem Scratchpad, `docker build --target test`)

| Mutation | Wirkung gemessen |
|---|---|
| `STATE_OBJECT_EXISTS` 6 auf 1 | rot: `ErrorMapperTest > Strukturabweisung ist ERROR 2714 State 6, 2705 State 4 und 4902 State 1 mit offener Verbindung` (1 von 1065 im Modul `:test`) |
| Codes vertauscht (`OBJECT_EXISTS` und `COLUMN_EXISTS` in `StatementPlanner`) | rot: `TableStoreTest > eine doppelte Tabelle aendert den Bestand nicht` (`:testserver:test`, 1 von 107); Gradle brach danach ab, `SimulationBackendStructureTest` lief in dieser Kopie nicht (nach dem Lesen prüft `Abweisung gegen den Katalog traegt die Codes der gemessenen Fehler` genau diese Paare) |
| Ausnahme für leeren Schlüssel in `TableStore.insertBatch` entfernt | rot: `TableStoreTest > eine Tabelle ohne Schluessel nimmt gleiche Zeilen an` (1 von 107) |
| Merge-Probe: `main` `0b3e990c` in den Zweig, Probe-Test `update` zweier Zeilen an schlüsselloser Tabelle | rot: `IllegalArgumentException: Schlüssel nicht eindeutig` in `TableStore.update` (F-5); weitere Tests der zusammengeführten Fassung liefen nach dem Abbruch nicht |
| Nicht gefahren | `require(primaryKey.isNotEmpty())` wieder in `SimTable`: nach dem Lesen rot durch `SimModelTest > weist eine leere Spaltenliste und leere Namen ab und nimmt einen leeren Schluessel an` |

## Merge-Probe gegen `main` `0b3e990c` (`git merge-tree`)

Textkonflikte nur in Dokumenten: `docs/plan/planning/done/slice-katalog-und-struktur-an-messung-angleichen.md` (Rename/Modify gegen `open/` auf `main`; Auflösung nach §4: Inhalt von `main`, Position folgt dem Lifecycle), `roadmap.md`, `welle-testserver-manuell.md`, die zwei Register-`state.md`, `open/slice-abnahme-d-migrate.md`, `done/slice-antwort-tokens-zielbild-luecken-bauen.md`, `open/slice-katalog-indizes-defaults-fremdschluessel.md`, `done/slice-qa-vergleichslauf-d-messungen-bulk-merge.md`, `docs/plan/spec-abdeckung.md` (erzeugt, neu zu erzeugen) und `docs/user/einbettung-und-betrieb.md`. Automatisch zusammengeführt: `spec/spezifikation.md`, `docs/messungen/referenzabgleich.md`, `StatementPlanner.kt`, `TableStore.kt`, `TableStoreTest.kt`. Keine Code-Textkonflikte mit dem MERGE-Zweig; der semantische Konflikt steht in F-5 und F-6. Die Zweige zu `sp_unprepare` und Massenladen mit festen Typen liegen nicht auf `main` und wurden nicht geprüft.

## Läufe mit d-migrate (Digest `sha256:308f2775…`, gegen den Testserver dieses Zweigs)

| Lauf | Ergebnis |
|---|---|
| `schema reverse` | Exit 0 |
| `schema compare` gegen `soll.yaml` (Anleitung Aufgabe 3) | Exit 1, `DIFFERENT`: `produkte` fehlt, `kunden` ohne `Email` (wie dokumentiert) |
| `schema migrate --execute` | Exit 0, `status: ok`, 2 Anweisungen; danach `compare` Exit 0 `IDENTICAL` |
| `schema migrate --execute` mit zusätzlicher Tabelle `notizen` ohne Schlüssel | Exit 0, `status: ok`; `compare` Exit 0 `IDENTICAL` mit Warnung E008 (kein Schlüssel); `reverse` schreibt `notizen` ohne `primary_key`; erneutes `migrate`: `no_op` |
| zwei gleichzeitige `schema migrate` mit demselben neuen Objekt | ein Lauf Exit 0; der andere Exit 5, `executionError` der feste Text zu 2714, `transactionRolledBack: true`, `FULL_ROLLBACK_CONFIRMED` |
| Testserver mit der `reverse`-Datei der Tabelle ohne Schlüssel als Beschreibung | Start bricht ab: „Pflichtfeld primary_key fehlt“ (F-2) |

Grenzen: Einzelläufe auf einem Host, kein Lauf von d-migrate gegen den echten SQL Server, `data import` und `data export` nicht gefahren; die Nummer 2714 erscheint im Bericht von d-migrate nicht, gemessen ist nur der Pfad des Textes.

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `src/main/.../protocol/error` (`ErrorCode.kt`, `ErrorMapper.kt`): Nummern, State, Class, `ConnectionFate.OPEN`, Aufnahme in `BACKEND_CODES` | geprüft gegen Messdateien 2022 und 2025, ohne Befund (Text: F-7) |
| `src/main/.../simulation` (`StatementPlanner.kt`, `SimModel.kt`): Codes der Abweisung, leerer Schlüssel, Antwort auf die Primärschlüssel-Abfrage (leere Liste) | geprüft, ohne Befund; Katalogabfrage der schlüssellosen Tabelle gefahren über `schema reverse`, kein eigener Test im Diff (DoD-Frage des Verifiers) |
| Nebenläufigkeit: `structureLock` und die Abweisung zweier gleichzeitiger `CREATE TABLE`/`ALTER TABLE` | geprüft, ohne Befund: der Diff ändert keine Sperre, die zweite Anfrage sieht die erste und trägt jetzt `TDS_SIM_OBJECT_EXISTS` bzw. `TDS_SIM_COLUMN_EXISTS` (zwei angepasste Tests grün, d-migrate-Wettlauf gefahren) |
| `testserver/kotlin` (`TableStore.kt` auf dem Zweig) | geprüft, ohne Befund; auf `main` siehe F-5 |
| Suppression, Schichtregel, Host statt Docker, Gate-Lockerung (`detekt.yml`, `build.gradle.kts`, `Dockerfile`) | geprüft, ohne Befund (Diff berührt keine dieser Dateien; `make gates` grün) |
| Kommentare in `ErrorCode.kt`, `StatementPlanner.kt`, `SimModel.kt`, `TableStore.kt` und Tests | geprüft, ohne Befund (Zusage und Grenze im Indikativ; „Wächter:“-Zeilen sind die gelebte Form der Tests) |
| `docs/user/referenz-konfiguration.md`, `docs/user/testserver-manuell.md`, `docs/user/einbettung-und-betrieb.md` (komplett gelesen; Grep auf „nicht gebaut“, „Zielzustand“, „bisher“, „noch nicht“, „seit Slice“) | ohne Befund außer F-3; „vorläufig“ steht nur im Anker `SPEC-036` |
| `docs/plan/kompatibilitaetsmatrix.md`, `README.md` | geprüft, ohne Befund (die Zeile „Fehlerantwort“ nennt die Simulationscodes 208 und 245, nicht die Codes des Backends; unverändert, kein neuer Widerspruch) |
| `harness/README.md` (Zellen ≤ 200 Zeichen) | nicht berührt, ohne Befund |
| Haken in der DoD (`- [x]`) | geprüft: keiner im Diff gesetzt, ohne Befund |
| `SPEC-059` und `make flaeche` | ohne Befund: `SimTable` ändert nur eine Prüfung, kein neuer öffentlicher Name |

## Summary

Der Bau trägt, was die Messung trägt. Zu klären sind vor der Closure: der Widerspruch in `SPEC-035` (F-1), die Linie der Datei (F-2, INFO-1), die Liste in der Nutzer-Doku (F-3), die abgelöste Aussage der ADR (F-4) und beim Zusammenführen `TableStore.update` samt der Grenze zu `MERGE` (F-5, F-6). Nicht gefahren: Läufe von d-migrate gegen den echten SQL Server, `data import` und `data export` mit der Tabelle ohne Schlüssel, die Tests der zusammengeführten Fassung über den Probe-Test hinaus. Review durchgeführt; Verifikation gegen die DoD steht aus.
