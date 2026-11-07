# Verifikationsbericht: slice-katalog-und-ddl-zustand (HEAD 181979a)

* Rolle: Verifier (Modul 11), frischer Kontext. Gegenstand: Slice `slice-katalog-und-ddl-zustand` (`in-progress/`, Zweig `slice-katalog-und-ddl-zustand`, HEAD `181979a7`, Basis des Slice `fd6d5286`, `main` `652d5667` eingemerged) gegen DoD §2, Plan §3, [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) (`Accepted`), [ADR-0026](../plan/adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md), [ADR-0034](../plan/adr/0034-testserver-als-gradle-modul.md), `SPEC-035`, `SPEC-036`, `SPEC-040`, `SPEC-059`, `TDS-FA-11`, `TDS-FA-12`, das Review `2026-11-06-5fe078e7-review-slice-katalog-und-ddl-zustand.md` (F-1 bis F-9) und das Messprotokoll `docs/messungen/katalog-und-ddl-zustand-2026-11-06.md`. Maßstab des Nutzers (2026-11-06): „Spec ist Zielbild“, der echte SQL Server ist die Referenz; eine Abweichung braucht einen Träger, der die Closure überlebt.
* Selbst gefahren (Worktree `agent-a749990460ac34a31`, Stand `181979a7`, Arbeitsbaum sauber): `make gates FORCE=1` (Exit 0; die Stempel `werkzeug` und `tests` wurden geschrieben, die vier Selbsttests der Stempel 118/95/99/64 Fälle grün, `gate-zuordnung` 33 Fälle), `make docs-check` (Exit 0), `make flaeche` (Exit 0), `make clean-build` (Exit 0; Frischbeleg ohne Layer-Cache, 1013 Testfälle PASSED, darunter die vier neuen Tests der Behebung von F-1 und F-4), `make e2e` (Exit 0, 8 Stufen, 166 s), `make matrix-zitate` (1013 bestandene Testfälle, 252 Zitate, 0 ohne PASSED), d-migrate 1.12.0 (Digest `sha256:308f2775…6491`) gegen `make testserver-up` aus dem Arbeitsbaum (Port 24993, `TEST_UNENCRYPTED`, Last 14 bis 18), zwei Mutationen (unten).
* Nicht selbst gefahren: `make vergleichslauf-d` (die Gegenmessung am echten Server; Maßstab ist kein Code-Fehler, aber siehe V-1), `make test` einzeln (von `make gates FORCE=1` und `make clean-build` überdeckt; der Gate-Lauf zeigte die Stage `test` als `CACHED`, deshalb der Frischbeleg), ein Re-Review des Fix-Commits durch den Reviewer (es gibt keinen; ich habe ihn selbst bewertet, siehe Abschnitt „Bewertung der Behebung F-1“), die Statementzahlen des Messprotokolls (Wegwerf-Wrapper nicht im Repo; wiederholt sind Exit-Codes und Berichtsfelder).

## Gesamturteil

**Closure-fähig: nein, solange V-1 (HIGH) und V-3 (MEDIUM) offen sind; beides sind fehlende Träger und Wortlaut, kein Fehler im Code.** Die drei Liefer-Punkte sind in der Sache geliefert: alle Gates und Läufe grün, d-migrate `schema reverse`, `schema compare` und `schema migrate --execute` enden gegen den Testserver mit Exit 0 und bestehen die Nachprüfung, die Behebung von F-1 hält ihrer Mutation stand. Offen sind die Träger der Zielbild-Abweichungen (der benannte Träger der Katalog-Gegenmessung hat sie nie geliefert, und der Nachfolger schließt den Katalog ausdrücklich aus: ein Kreis, V-1), der Wortlaut von Liefer-Punkt 1 (V-3) und die vor der Verifikation gesetzten Haken (V-2). 1 HIGH, 2 MEDIUM, 3 LOW, 3 INFO.

## DoD-Punkte

| # | DoD-Punkt | Urteil | Beleg |
|---|---|---|---|
| 1 | Bibliothek, Katalog: Abfragen von `schema reverse` und `schema compare` aus den mitgeteilten Tabellen; ein Test je Abfragegruppe | erfüllt mit V-3 | `CatalogFormsTest`, `SimulationBackendCatalogTest`, `DMigrateWire.kt` laufen im Frischbeleg grün. Eigene Läufe k1 bis k4: `schema reverse` Exit 0 (`Id` als `biginteger`, `Name` `text` mit `max_length: 100`, `Betrag` `decimal` 10, 2), `--include-all` Exit 0, `schema compare` gegen die Rückgabe und gegen `sim.yaml` Exit 0 (`IDENTICAL`). **Abweichung vom Wortlaut:** Indizes, Defaults und Prüfbedingungen antworten leer; die DoD sagt „werden aus den mitgeteilten Tabellen beantwortet“ (V-3). Aus dem Review übernommen, nicht von mir nachgezählt: der Test prüft 17 von 20 Formen gegen Mitschnitt, drei (Identität, berechnete Spalten, größter Wert) tragen den Wortlaut aus dem Bytecode des Treibers (benannt im Messprotokoll). |
| 2 | Bibliothek, Strukturänderung: `CREATE TABLE`, `ALTER TABLE … ADD`, Transaktionstexte, Callback, Katalog zeigt danach die gemeldete Struktur | erfüllt | `SimulationBackendStructureTest` (auch die zwei Tests mit überlappenden Anfragen), `CatalogFormsTest` (Transaktionstexte), `FlaecheEmbeddingTest` (zweiter Konstruktor von außen) grün im Frischbeleg; `make flaeche` Exit 0 (kein neuer Typname). |
| 3 | Testserver und Beleg: veränderlicher Bestand, Callbacks, `schema reverse/compare/migrate --execute` mit Exit 0 samt Nachprüfung, Ausgabe mit Rechner, Last, Digest | erfüllt mit V-1 | Eigene Läufe (Soll: `rev.yaml` plus Spalte `Email` an `kunden`, Tabelle `notizen` mit `Text` ohne `max_length`, Tabelle `produkte` mit `biginteger`, `decimal`, `boolean`, `datetime`): `schema migrate --execute` Exit 0, `status: ok`, 3 Operationen, `statementsAttempted: 3`, `transactionRolledBack: false`, gesendet `CREATE TABLE [notizen] (… [Text] NVARCHAR(MAX) …)`, `CREATE TABLE [produkte] (… [Seit] DATETIME2 …)`, `ALTER TABLE [kunden] ADD [Email] NVARCHAR(200);`; `schema compare` danach Exit 0 (`IDENTICAL`); zweiter `migrate` `no_op`; Soll mit `identifier` Exit 1 (`identifier -> biginteger`); `schema reverse` danach zeigt `notizen.Text` ohne `max_length`. Das deckt k1 bis k9 und k11 des Messprotokolls im Befund, nicht in den Zahlen. Das Protokoll trägt Rechner, Last, Digest. V-1 betrifft nicht die Lieferung, sondern den Träger der Gegenmessung. |
| N1 | `make gates` grün | erfüllt | Eigener Lauf mit `FORCE=1`, Exit 0. |
| N2 | Doku-Update bei öffentlichem Vertrag | erfüllt | `spec/spezifikation.md` (`TDS-FA-11.a`, `SPEC-035`, `SPEC-036`, `SPEC-040`, `SPEC-059`, Änderungsprotokoll), `docs/user/testserver-manuell.md`, Messprotokoll im Diff; `make docs-check` und `make flaeche` grün. |
| N3 bis N7 | Review (Report liegt vor, Behebung ohne Re-Review), Closure-Notiz, Register, Risiken aus §6, drei Paarungen | offen, zu Recht ungehakt | §7 trägt nur „—“; §6 trägt dreimal „offen bis zur Closure“. |

**Haken (V-2).** Der Commit `5fe078e7` hat fünf Haken gesetzt (die drei Liefer-Punkte, `make gates`, Doku-Update), bevor Review und Verifikation abgeschlossen waren; die Regel „Haken vor der Verifikation“ (seit `slice-qa-vergleichslauf-d-messungen`) sagt: der Implementer behauptet im Bericht, der Planner setzt den Haken nach der Verifikation. Der Review-Report hat das nicht angemerkt. Ich bestätige mit diesem Bericht inhaltlich Punkt 2, N1 und N2; Punkt 1 und 3 nur mit den Vorbehalten V-3 und V-1. Wo der Haken am Ende steht, entscheidet der Planner nach den Bedingungen unten.

## Plan-Tabelle §3 gegen `git diff 652d5667..HEAD --stat` (28 Dateien)

(`fd6d5286..HEAD` zeigt 144 Dateien, weil `main` eingemerged ist; Abgleich gegen den Anteil des Slice.)

| Plan-Zeile | Diff | Urteil |
|---|---|---|
| `CatalogForms.kt`, `CatalogAnswers.kt` (neu) | beide neu (370, 144 Zeilen) | stimmt |
| `StructureParts.kt`, `TransactionForms.kt`, `Statement.kt`, `StatementParser.kt`, `StatementPlanner.kt`, `FixedAnswers.kt`, `SimulationChanges.kt` | alle im Diff; `StructureParts.kt` neu | stimmt |
| `SimulationBackend.kt`, `SimModel.kt` | beide geändert | stimmt |
| `TableStore.kt`, `Main.kt`, `SimDescriptionReader.kt`, `sim.yaml` | alle im Diff | stimmt |
| Tests (`CatalogFormsTest`, `SimulationBackendCatalogTest`, `SimulationBackendStructureTest`, `DMigrateWire`, `StatementFormsTest`, `SimDescriptionReaderTest`, `TableStoreTest`, `FlaecheEmbeddingTest`) | alle im Diff | stimmt (F-2 des Reviews behoben) |
| `spec/spezifikation.md`, `docs/user/testserver-manuell.md`, Messprotokoll | alle im Diff | stimmt |
| nicht in der Tabelle | der Slice selbst und der Review-Report (Prozessartefakte) | kein Befund |
| Kopf „Berührte Spec-Stellen“ | Diff berührt `TDS-FA-11.a`, `SPEC-035`, `SPEC-036`, `SPEC-040`, `SPEC-059` | stimmt |

Keine Datei im Diff außerhalb des Plans; keine Plan-Datei ohne Diff.

## Bewertung der Behebung F-1 (Commit `181979a7`, ohne eigenen Re-Review)

Gelesen: `SimulationBackend.structure`, `ChangeRejected`, `ChangeExecution.run`, `TableStore`, die vier neuen Tests. Gefahren: zwei Mutationen an einer Kopie des Stands (`git archive`, nichts am Arbeitsbaum geändert).

| Prüfpunkt | Ergebnis |
|---|---|
| Mutation M1: `structureLock.withLock { … }` durch `run { … }` ersetzt, `make test`-Stage | **rot**: `TableStoreTest > gleichzeitiger Zugriff verliert nichts` schlägt fehl (`expected: <602> but was: <460>`, es gehen Spalten verloren). Ein Lauf; die Wahrscheinlichkeit der Rötung über mehrere Läufe ist nicht gemessen. |
| Mutation M1 mit entferntem `TableStoreTest`, damit Gradle die Bibliothekstests erreicht | **rot**: `SimulationBackendStructureTest > zwei gleichzeitige CREATE TABLE derselben Tabelle - genau eine gewinnt` und `… ALTER TABLE ADD verlieren keine Spalte` schlagen fehl. Diese beiden Tests sind deterministisch (der Callback ruft `delay(50)` vor dem Ersetzen, ein Thread, überlappende Coroutinen), nicht probabilistisch. |
| Reentrancy/Deadlock | `Mutex` ist nicht reentrant; `TableStore.replace` ruft nicht ins Backend zurück, es gibt keinen Zyklus im Repo. Lock-Reihenfolge: `structureLock`, dann Monitor von `TableStore`; die Gegenrichtung gibt es nicht. Ein Integrator-Callback, der dasselbe Backend aufruft und dessen Antwort abwartet, hinge für immer (V-4). |
| Fehler im Callback | `guarded` fängt die Ausnahme, `withLock` gibt die Sperre frei, Ergebnis `QueryEvent.Failure(TDS_BACKEND_ERROR, "Änderung fehlgeschlagen")`; ein werfender Katalog-Anbieter im erneuten `plan` ebenso. Gelesen, nicht eigens gefahren. |
| Cancel/ATTENTION | Eine Anfrage, die auf die Sperre wartet, wird vom Dispatch über `job.cancel()` beendet (`AbortableRun`); `Mutex.lock` ist abbrechbar, die Sperre bleibt unberührt. `stop()` vor dem Sammeln lässt die Planung und den Callback ungerufen. Ein laufender Callback endet nur mit dem Abbruch des Sammlers (so dokumentiert) und hält die Sperre bis dahin: ein hängender Callback blockiert alle weiteren Strukturänderungen dieses Backends (V-4). |
| `TDS-QA-07` (je Anfrage genau eine Antwort) | Der Strom liefert genau ein Ereignis (`UpdateCount` oder `Failure`); `ChangeRejected` wird zum `Failure` mit dem Code der Planung, jede andere Ausnahme zum festen `TDS_BACKEND_ERROR`. `make e2e` Stufe 7 (Synchronität) grün. |
| Semantik „zweite abgewiesen“ | Die Vorprüfung in `execute` läuft ohne Sperre gegen den Stand der Anfrage, die zweite Prüfung unter der Sperre: ein `ALTER` auf eine Tabelle, die eine noch nicht gesammelte `CREATE`-Anfrage anlegen wird, wird sofort abgewiesen (Reihenfolge der Ankunft); das ist eine Reihenfolgefrage, kein Fehler, und steht nicht in der Spec. |
| Sperre je Backend, nicht je Bestand | Aussage ist in `TableStore`-KDoc, `structure`-KDoc und Änderungsprotokoll auf „Schreiber über dasselbe Backend“ eingeschränkt. Die Hauptklasse baut genau ein Backend; zwei Backends über einen Bestand sind kein Fall des Repos. §3.8 gewahrt. |

## Plan-vs-Code und Entscheidungs-Konformität

| Entscheidung | Urteil | Beleg |
|---|---|---|
| [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 1 und 2 (Katalog-Anbieter bei jeder Anfrage, `maxLength`, Fassung F: zweiter Konstruktor, kein neuer Typname) | konform | `make flaeche` grün; der Planer liest den Anbieter je `plan`; `FlaecheEmbeddingTest` übersetzt die Namen von außen. Der Sonderwert -1 für `NVARCHAR(MAX)` ist Setzung der Spec (Änderungsprotokoll), nicht Teil der ADR; kein Widerspruch (F-3 des Reviews, behoben). |
| [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 3 (Strukturänderung über Callback, keine Transaktion) | konform | Zähler 0 (Setzung), `ROLLBACK TRAN` = Zähler 0, nichts wird zurückgenommen. |
| [ADR-0026](../plan/adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md) (Öffentlichkeit), [ADR-0034](../plan/adr/0034-testserver-als-gradle-modul.md) (Testserver als Modul) | konform | Testserver kennt weder Katalog- noch Parserlogik (`TableStore` hält nur den Bestand); `make detekt` und `modulgrenze-sperre` grün. |
| Hard Rules §3.1 bis §3.8 | konform, mit V-5 | kein `@Suppress`, nur `make`/Docker, keine Gate-Lockerung im Diff. |

## Zielbild-Abweichungen und ihre Träger

Maßstab: sichtbar benannt **und** ein Träger, der die Closure überlebt (Slice in `open/`/`next/`, Eintrag im Beobachtungs-Register, oder DoD-Zeile eines Folge-Slice).

| Abweichung | Benannt | Träger, der die Closure überlebt | Urteil |
|---|---|---|---|
| `max_length`, `precision`, `scale` als `Int32` statt `smallint`/`tinyint` | ja (`SPEC-035`, Messprotokoll, KDoc) | **keiner**: das Messprotokoll nennt `slice-qa-vergleichslauf-d-messungen` (liegt in `done/`, hat keine Katalogmessung geliefert); `slice-qa-vergleichslauf-d-messungen-bulk-merge` (`open/`) schließt den Katalog ausdrücklich aus und verweist auf diesen Slice zurück | **V-1** |
| Setzung `bigint`, `bit`, `time`, `datetime2` (Typnamen, Byte, Precision, Skala) | ja (Spec, KDoc, Messprotokoll) | keiner (wie oben) | V-1 |
| Fehlerarten der Strukturabweisung (`TDS_UNSUPPORTED_SQL`, `TDS_SIM_UNKNOWN_OBJECT` statt der Nummern des Servers) | ja (`SPEC-035`) | keiner; der echte Server wird in keinem offenen Slice zu `CREATE TABLE`/`ALTER TABLE` gefragt | V-1 |
| `maxLength = -1` für `NVARCHAR(MAX)` | ja (Spec, Änderungsprotokoll); der Server meldet -1 ebenso, die Setzung ist die Form in `SimColumn` | Beleg: Lauf mit d-migrate (Exit 5 ohne, Exit 0 mit -1; eigener Lauf bestätigt die Hälfte „mit -1: Exit 0“, die Gegenprobe ist ein Altstand) | tragbar; Gegenmessung ebenfalls unter V-1 |
| Indizes, Defaults, Prüfbedingungen, Fremdschlüssel leer | ja (Spec, Messprotokoll „nicht gemessen“) | **keiner**: kein Slice, keine DoD-Zeile, kein Register-Eintrag | **V-3** |
| `SimTable.onInsertBatch` ungerufen | ja (KDoc, `SPEC-035`) | ja: `slice-schreibpfad-bulk-load` führt `onInsertBatch` (5 Treffer) | tragbar |
| Hauptklasse des Testservers mit immer einem Backend | ja (`SPEC-035`, KDoc von `storeSource`, Test) | Dokument-Träger (Spec, Test) genügt: es ist ein Zustand, keine Lücke | tragbar |
| Sperre je Backend-Instanz | ja (KDoc, Änderungsprotokoll) | Dokument-Träger; keine Lücke im Repo | tragbar (V-4 ergänzt) |

**Zum Träger der Katalog-Gegenmessung (V-1).** Fünf Fundstellen, ein Kreis: (a) das Messprotokoll nennt `slice-qa-vergleichslauf-d-messungen` als Träger; der Slice liegt in `done/` und hat die Katalogmessung nicht geliefert (sein Messvermerk und das Register nennen „Massenladen, MERGE und Katalog“ als nicht gemessen). (b) `slice-qa-vergleichslauf-d-messungen-bulk-merge` in `open/` sagt unter „Herkunft“ und „Nicht Gegenstand“: der Katalog ist **nicht** Gegenstand, seine Messung liege „im Katalog-Slice“, und sein Start-Trigger hängt am Katalog-Slice. (c) Das Register `BEO-TDS/messung-am-echten-sql-server-fehlt` sagt: „die Katalogantworten im Katalog-Slice“. (d) Dieser Slice führt weder in der DoD noch in §6 noch in §7 eine Zeile zur Gegenmessung. (e) Die Spec nennt keinen Träger. Jede Stelle zeigt auf die nächste; keine trägt. Das ist die Klasse „Träger zeigt auf eine Stelle, die ihn nicht trägt“.

## Findings

| ID | Kategorie | Befund | Pfad | Klasse |
|---|---|---|---|---|
| V-1 | HIGH | Die Gegenmessung der Katalogantworten am echten SQL Server (Typen `smallint`/`tinyint` der Spalten, Setzungen `bigint`, `bit`, `time`, `datetime2`, Fehlernummern der Strukturabweisung) hat keinen Träger: das Messprotokoll nennt den abgeschlossenen `slice-qa-vergleichslauf-d-messungen`; `slice-qa-vergleichslauf-d-messungen-bulk-merge` (`open/`) schließt den Katalog aus und verweist auf diesen Slice; das Register verweist ebenso; dieser Slice hat weder DoD-Zeile noch Risiko dafür. Der Kreis endet bei keinem Slice, der es misst. Die Abweichungen sind benannt, aber nach der Closure trägt sie niemand. | `docs/messungen/katalog-und-ddl-zustand-2026-11-06.md` (Vorbehalt-Absatz) · `docs/plan/planning/done/slice-qa-vergleichslauf-d-messungen-bulk-merge.md` (Herkunft, Nicht Gegenstand) · `docs/plan/planning/observations/BEO-TDS/messung-am-echten-sql-server-fehlt/state.md` | Träger zeigt auf Stelle, die ihn nicht trägt |
| V-2 | MEDIUM | Fünf Haken (drei Liefer-Punkte, `make gates`, Doku-Update) stehen seit `5fe078e7` vor Review und Verifikation; die Regel „Haken vor der Verifikation“ weist sie dem Planner nach der Verifikation zu. Der Review-Report hat den Pfad der Regel nicht angemerkt. Die Haken sind inhaltlich durch meine Läufe gedeckt (Punkte 2, N1, N2), Punkt 1 und 3 nur mit V-3 und V-1. | `docs/plan/planning/done/slice-katalog-und-ddl-zustand.md` §2 | Haken vor der Verifikation gesetzt |
| V-3 | MEDIUM | Liefer-Punkt 1 nennt „Indizes, Defaults und Prüfbedingungen“ unter den aus den Tabellen beantworteten Abfragen; geliefert sind sie leer („Setzung, `SimColumn` führt sie nicht“, hinter dem Beleg-Satz). Wortlaut der DoD und Lieferung weichen ab; `schema compare` gegen ein Soll mit Fremdschlüsseln, Indizes oder Standardwerten ist laut Messprotokoll „nicht gemessen“, und kein offener Slice (auch nicht `slice-abnahme-d-migrate`) trägt Fremdschlüssel, Indizes oder Standardwerte. Zielbild: der echte Server liefert sie. | `docs/plan/planning/done/slice-katalog-und-ddl-zustand.md` §2 Punkt 1 · Messprotokoll „Grenzen der Messung“ · `spec/spezifikation.md` Katalog der Spalten | DoD-Wortlaut weiter als die Lieferung |
| V-4 | LOW | Fix-Review F-1: Die Sperre ist ein nicht reentrantes `Mutex`, das den Callback einschließt. Ein Callback, der dasselbe Backend aufruft und abwartet, hängt für immer; ein hängender Callback blockiert alle Strukturänderungen des Backends bis zum Abbruch seines Sammlers. Weder KDoc von `structure` noch Spec nennen es als Grenze (die Grenze „ein laufender Callback endet nur mit dem Abbruch des Sammlers“ steht nur an `ChangeExecution`). Im Repo gibt es keinen solchen Callback. | `src/main/kotlin/io/github/pt9912/tdswire/simulation/SimulationBackend.kt` · `structureLock.withLock` | Grenze der Sperre nicht benannt |
| V-5 | LOW | Fix-Review F-1: (a) KDoc von `ChangeExecution` sagt „Ein Fehler des Callbacks und eine negative Zahl sind ein `QueryEvent.Failure` mit `TDS_BACKEND_ERROR` und fester Meldung“; der neue Pfad `ChangeRejected` ergibt einen `Failure` mit dem Code der Planung (`TDS_UNSUPPORTED_SQL`, `TDS_SIM_UNKNOWN_OBJECT`) und der Meldung der Planung. (b) Das Änderungsprotokoll nennt die Sperre unter `SPEC-035`; der Text steht in `TDS-FA-11.a` Schritt 9, im Absatz „Katalog-Anbieter“ von `SPEC-035` nicht. (c) Der Test „ALTER TABLE ADD derselben Spalte“ prüft nur, dass genau ein `Failure` entsteht, nicht den Code. | `src/main/kotlin/io/github/pt9912/tdswire/simulation/SimulationChanges.kt` (KDoc `ChangeExecution`) · `spec/spezifikation.md` (Änderungsprotokoll 2026-11-06) · `SimulationBackendStructureTest` | Zusage und zweite Fundstelle hinter dem Code |
| V-6 | LOW | Das Messprotokoll nennt Zahlen (26/27, 69/70/85 Anfragen) aus einem Wrapper, der nicht im Repo steht; die Grenze ist benannt (F-5 des Reviews, behoben). Meine Wiederholung bestätigt Exit-Codes und Berichtsfelder, nicht die Zahlen. Dazu: der Link im Messprotokoll auf den Träger zeigt auf `done/` und trägt nicht (V-1). | `docs/messungen/katalog-und-ddl-zustand-2026-11-06.md` | Messzahl ohne wiederholbaren Erzeuger |
| V-7 | INFO | `TelemetryRedactionTest > Die Kennung steht gekuerzt und nur im Ereignis der Annahme` schlug in der mutierten Kopie unter Last einmal fehl (`NoSuchElementException: List is empty`); in `make gates FORCE=1` und `make clean-build` ist er grün. Kein Gegenstand dieses Slice; nach den Läufen ein Flake-Kandidat unter Last. | `src/test/kotlin/io/github/pt9912/tdswire/runtime/TelemetryRedactionTest.kt` Zeile 261 | Test unter Last unzuverlässig (einmal beobachtet) |
| V-8 | INFO | Test-KDoc der Behebung enthält Zeilenumbrüche mitten im Satz („die / zweite“, „Spalten im / Bestand“); kosmetisch. Die „Wächter:“-Zeilen sind die Konvention des Repos für Tests, keine Konjunktive über verworfene Alternativen im Produktivcode (Grep über den Diff ohne Treffer in `src/main`). | `SimulationBackendStructureTest.kt`, `TableStoreTest.kt` | Kosmetik |
| V-9 | INFO | Die Mutation M1 am Bestandstest (`TableStoreTest`) ist probabilistisch (vier Threads, 150 Runden, ein Lauf rot); verlässlich rot macht sie der deterministische Bibliothekstest. Wer den Bestandstest als Wächter zählt, hat nur einen Lauf. | `TableStoreTest.kt` | Wächter probabilistisch |

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| Plan-Tabelle gegen Diff | geprüft, ohne Befund |
| Gates (`make gates FORCE=1`), `docs-check`, `flaeche`, `e2e`, `matrix-zitate`, Frischbeleg `clean-build` | geprüft, alle Exit 0 |
| d-migrate `reverse`, `reverse --include-all`, `compare` (Rückgabe, `sim.yaml`), `migrate --execute` (3 Anweisungen), zweiter `migrate` (`no_op`), `compare` mit `identifier` (Exit 1, benannte Grenze) | geprüft, ohne Befund |
| Reentrancy, Fehler im Callback, Cancel/ATTENTION, `TDS-QA-07` der Behebung F-1 | geprüft, ohne Befund über V-4 und V-5 hinaus |
| Schichten, Suppression-Verbot, Gate-Lockerung | geprüft, ohne Befund |
| Roadmap und Welle: Zustandsfelder | geprüft, ohne Befund: der Slice steht in `in-progress/` (Roadmap Zeile „in `in-progress/`“, Welle-Tabelle), der Diff verändert Roadmap und Welle nicht, keine Schließung ist behauptet; Drift-Log trägt nur die Start-Umplanung |
| `AGENTS.md` §3.7 im Produktivcode des Diffs | geprüft, ohne Befund (Grep auf Konjunktive und Chronik, keine Treffer in `src/main`, `testserver/kotlin`) |
| `AGENTS.md` §3.8 (Aussage höchstens so weit wie der Beleg) | geprüft: Setzungen sind als Setzung gekennzeichnet, Grenze der Sperre auf dasselbe Backend begrenzt; offen V-4, V-5, V-6 |

## Bedingungen vor der Closure

1. **V-1:** Einen Träger für die Katalog-Gegenmessung schaffen, der trägt: entweder einen Liefer-Punkt oder ein Risiko mit Ausgang „weiter offen, Träger `slice-…`“ in diesem Slice (mit Folge-Slice in `open/`), oder die Katalogmessung in den Umfang von `slice-qa-vergleichslauf-d-messungen-bulk-merge` aufnehmen (dann dessen Abgrenzung und Start-Trigger ändern, damit kein Kreis bleibt). Danach Messprotokoll (Link auf `done/`), Register-Zustandsfeld und Spec auf den Träger zeigen lassen. `make vergleichslauf-d` ist laut Nutzer lokal erlaubt; ich habe es nicht gefahren, weil eine Katalogmessung neuen Messcode bräuchte.
2. **V-3:** Wortlaut von Liefer-Punkt 1 an die Lieferung angleichen („Indizes, Defaults, Prüfbedingungen leer“) und einen Träger für das Füllen benennen (Folge-Slice oder Register-Eintrag), oder bewusst als Grenze von `slice-abnahme-d-migrate` führen.
3. **V-2:** Haken durch den Planner setzen oder zurücknehmen nach Erfüllung von 1 und 2; der Implementer setzt keine.
4. §6 jedes Risiko mit Ausgang (das erste „Umfang des Katalogs“ und das zweite „Typabbildung/FLOAT“ sind durch die Läufe beantwortbar, das dritte „Fläche“ durch `make flaeche`), §7 Closure-Notiz mit Steering-Loop-Eintrag (Klassen dieses Laufs: Träger zeigt auf Stelle, die ihn nicht trägt · Haken vor der Verifikation gesetzt · DoD-Wortlaut weiter als die Lieferung), Register fortgeschrieben, die drei Paarungen.
5. V-4 und V-5 (LOW) in derselben oder einer Folge-Änderung; kein Re-Review der Behebung F-1 nötig, ich habe ihn bewertet (Mutation rot, kein HIGH/MEDIUM-Finding an der Behebung).

## Übergabe

An den Planner: V-1 und V-3 vor der Closure; Haken nach den Bedingungen; die Findings-Klassen in die Closure-Notiz §7.
