# Review-Bericht: slice-bulk-load-feste-typen-und-referenzclient (HEAD dcb5196b)

* Rolle: Reviewer, frischer Kontext. Review-Art: Code gegen Plan und Konventionen. Gegenstand: Diff `0bc03b4d..HEAD` (Commits `c456c186`, `c5941c3d`, `dcb5196b`; davor die Lifecycle-Commits des Planners), Zweig `worktree-agent-a8329d681f4d810e9`. Maßstab: der Slice (Plan, DoD, Rückführungen), [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit), [TDS-QA-07](../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität), [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) (Accepted), [ADR-0021](../plan/adr/0021-oeffentlicher-integrations-einstieg.md), [SPEC-001](../../spec/spezifikation.md#spec-001--zustandsautomat-einer-verbindung), [SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends), [SPEC-036](../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig), `SPEC-040`, `SPEC-059`, `AGENTS.md` §3.1 bis §3.8. Skill: `.harness/skills/reviewer.md`. Modell: Sonnet 5.5. Datum: 2026-11-08. Grundsatz des Auftrags: „Spec ist Zielbild“, der echte SQL Server ist die Referenz, eine Aussage trägt höchstens so weit wie ihr Beleg.
* Der Arbeitsbaum des Zweigs ist bis auf diesen Bericht unverändert; alle Mutationen und Gegenproben liefen in einer Kopie unter dem Scratchpad (`git archive HEAD`).
* Der Scratchpad-Ordner `rev` wurde während des Laufs von einem zweiten Lauf mitbenutzt (Dateien `m1` bis `m6`); die eigenen Läufe dieses Berichts liegen unter `rv-a8329` und `rev/wt2`.

## Selbst gefahren

| Lauf | Ergebnis |
|---|---|
| `make gates FORCE=1` | Exit 0 in 32 s: die Stages kamen aus dem Layer-Cache (kein Frischbeleg, siehe `make clean-build`); alle Gruppen und Selbsttests grün |
| `make test` | Exit 0 in 5 s (Cache) |
| `make flaeche` | Exit 0 |
| `make docs-check` | Exit 0 |
| `make matrix-zitate` | Exit 0 |
| `make simulations-integration` | Exit 0, 8 von 8 Tests des Referenzclients (3 neue `SqlBulkCopyTests`) |
| `make e2e` | Exit 0, 8 Stufen grün, 219 s; Stufe 7 ohne den bekannten Last-Flake |
| `make clean-build` (Frischbeleg) | erster Lauf Exit 2: 1 von 1078 Tests rot, `ListenerAbortTest > der Listener ruft den Abbruch nach der Frist und wartet CANCEL_TIMEOUT ab()` („nicht eingetreten: Beenden wartet auf 7s“, Last des Hosts 20 bis 35 durch parallele Docker-Bauten); derselbe Test fiel in zwei Mutationsläufen unter Last (M5, M7) ebenfalls; er gehört zu `BEO-TDS/wanduhr-tests-flackern-unter-last` und berührt den Diff nicht. Wiederholung bei Last 8 bis 25: Exit 0, `BUILD SUCCESSFUL in 7m 19s` der Stage `test` (1212 bestandene Tests), danach Jar, detekt und Integrator grün; der Rotlauf gehört damit zur Klasse „Wanduhr-Test unter Last“ |
| `d-migrate` 1.12.0 (Digest `sha256:308f2775…`) gegen zwei Testserver (`make testserver-up`, Ports 24891 und 24892) | `data import` Exit 0 (2 Zeilen), `data export` Exit 0 (2 Zeilen), erneuter `data import` Exit 5 (`Violation of PRIMARY KEY constraint. Cannot insert duplicate key.`), Export danach unverändert; `data transfer` A nach B Exit 0 (die Lücke des Implementers), Export von B und von A mit `cmp` byte-gleich. Das Test-Image `tdswire-testserver:lokal` ist ein gemeinsamer Tag; ob zwischen den beiden `up` ein anderer Lauf ihn überschrieb, ist nicht ausgeschlossen |

## Echtheit der Vektoren (selbst nachgelesen)

* Die fünf Hex-Dateien (`typen-null`, `verstoss-schluessel`, `mehrere-1` bis `mehrere-3`) sind byte-gleich der Nachricht `0x07` in `bulk-typen-null`, `bulk-verstoss-schluessel` und `bulk-mehrere-nachrichten` beider Instanzen (`2022` und `2025`; 327, 108, 28835, 31049 und 15549 Byte; je Paar gleich). Quelle steht in den KDoc der Tests.
* `antwort-probe-verstoss-schluessel.hex` (696 Byte) ist die Antwort des echten Servers (704 Byte, Nachricht 6 von `bulk-verstoss-schluessel`, beide Instanzen gleich) nach Entfernen der zwei Nullbyte hinter der Spaltenzahl der vier COLMETADATA: byte-gleich. Die Aussage „bis auf die zwei Nullbyte je COLMETADATA“ stimmt; die Deutung der Nullbyte steht in `SPEC-001` und im KDoc als „nicht belegt“: ehrlich.
* `probe-bulk_typen.sql` ist der aufgezeichnete Wortlaut; der Wortlaut ist in allen sechs Aufzeichnungen (`bulk_menge`, `bulk_typen`, `bulk_laenge`, `bulk_null`, `bulk_schluessel`, `bulk_typ`; beide Instanzen) bis auf den Namen gleich (selbst geprüft).
* Zusatzprobe: die Antwort des echten Servers für die Tabelle `bulk_typen` (9 Spalten mit BIT, DECIMAL, DATE, TIME, DATETIME2, NVARCHAR; 964 Byte) gegen die Ausgabe von `BulkCopyProbe.answer` für dieselben Spalten (Wegwerf-Test in der Kopie): gleich bis auf zwei Stellen, die Spalte `Klein` (am Server SMALLINT, `26 02`; das Typmodell kennt nur INT, `26 04`) und die Textlänge, die mein Wegwerf-Test falsch setzte (100 statt 50 Zeichen). Die Antwort trägt also über die zwei Spalten des Tests hinaus.
* INT `0x38`, SMALLINT `0x34`, TINYINT `0x30`, BIT `0x32` sind in `BulkFixedType` und im Test als Handvektor nach `MS-TDS` ohne Mitschnitt benannt (KDoc, `SPEC-001`, Matrix); ein `SqlBulkCopy`-Lauf mit einer nicht nullbaren INT-Spalte fehlt (`bulk_ziel.Zahl` ist nullbar), die Benennung ist ehrlich.

## Mutationen (selbst gefahren, einzeln, vollständiger Lauf der Stage `test` bzw. `simulations-integration`, Wirkung gemessen)

| Mutation | Wirkung gemessen |
|---|---|
| M1 `BulkFixedType.INT8`: Typkennung `0x7F` → `0x7E` | rot, 5 Tests: alle vier Tests der Messnachrichten und der Handvektor der festen Typen (`BulkLoadReaderReferenceClientTest`) |
| M2 `BulkDispatch.answer`: IGNORE ohne Zurücksetzen von `awaiting` | rot, 1 Test: `BulkDispatchTest > IGNORE, dann ATTENTION in READY ist die gewoehnliche Bestaetigung` |
| M3 `BulkCopyForms`: Bindung des Namens an der zweiten und dritten Stelle entfernt (freie Gruppe statt `\1`) | rot, 1 Test: `BulkCopyFormsTest > ein abweichender Name, ein Schema, ein Zusatz oder ein anderer Text sind nicht erkannt` (alle drei Stellen hält dieser eine Test) |
| M4 `BulkLoadReader.done`: DONE mit 8 Byte entfernt | rot, 4 Tests (`bulk-typen-null`, `bulk-mehrere-nachrichten`, `bulk-verstoss-schluessel`, Nutzlast am Testserver); `jede Teilnachricht der Aufzeichnung` bleibt grün |
| M5 `BulkCopyProbe`: Dezimalgröße 17 → 9 | rot, 1 Test: `BulkCopyProbeTest > die Beschreibung der Tabelle meldet Dezimalzahlen mit der Groesse 17`. Das Verhalten des Referenzclients bei Größe 9 (KDoc, F-1) habe ich nicht nachgemessen |
| M6 Stapel-Callback von `bulk_ziel`: Duplikat-Prüfung entfernt | rot, 1 von 8: `SqlBulkCopyTests > Schluesselverstoss_endet_mit_2627_…`; die zwei anderen Szenarien bleiben grün |
| M7 `BulkLoadReader.read`: Form mit zwei Nullbyte entfernt | rot, 3 Tests (`bulk-typen-null`, `bulk-mehrere-nachrichten`, `bulk-verstoss-schluessel`); der Test „am Testserver ohne Nullbyte“ bleibt grün |
| M8 `BulkLoadReader.metadata`: die zwei Bytes hinter der Spaltenzahl müssen nicht 0 sein (`!= 0` → `< 0`) | **grün**, Exit 0 der ganzen Stage `test`: kein Test trägt die Prüfung (F-6) |

## Findings

### F-1 HIGH — Kommentar beschreibt die verworfene Alternative und einen nicht belegten Lauf

* kategorie: HIGH
* quelle: `AGENTS.md` §3.7 und Reviewer-Skill „Kommentar trägt keine der Kommentar-Klassen“; zugleich §3.8
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/BulkCopyProbe.kt` · „bei der Größe 9 sendete er im Lauf am“
* befund: Das KDoc von `BulkCopyProbe` erzählt, was der Referenzclient bei der Größe 9 gesendet hätte („sendete er im Lauf am Testserver ein Längenbyte 9 vor diesen 17 Byte“), also die verworfene Alternative in der Vergangenheit; der zugehörige Lauf liegt nirgends im Repo (kein Messprotokoll, kein Test; M5 zeigt, dass nur die Meldung der Größe 17 in der Beschreibung bewacht ist, nicht das Verhalten des Clients).
* verifizierbar: nein (kein Gate prüft Kommentare)
* klasse: Kommentar beschreibt verworfene Alternative

### F-2 MEDIUM — `SPEC-001` hält neben dem neuen Absatz den alten über die gelesenen Typen

* kategorie: MEDIUM
* quelle: Reviewer-Skill „Fortschreibung lässt die zweite Fundstelle stehen“; `AGENTS.md` §3.8
* pfad: `spec/spezifikation.md` · „gelesen werden die Typen `INTN`, `BITN`,“ (derselbe Absatz zum Massenladen in `SPEC-001`, weiter unten als „Nutzlast des Referenzclients:“)
* befund: Der Absatz sagt weiter, durch einen Mitschnitt gedeckt seien „nur `INTN` mit 8 Byte, `NVARCHAR` und `BIGVARCHAR`“ und `INTN` 2 und 4, `BITN`, `NUMERICN`, `DATEN`, `TIMEN`, `DATETIME2N` seien „Handvektoren … ohne Mitschnitt“, nennt die festen Typen nicht, und lässt „jede andere Typkennung“ und „eine abgeschnittene Nachricht“ in den Fehler münden; der neue Absatz darüber (und die Matrix, die Messdateien, KDoc von `BulkLoadReader`) sagen das Gegenteil (BIGINT fest und die genannten Typen mit `SqlBulkCopy` gemessen; DONE von 8 Byte; eine um vier Byte gekürzte Nachricht mit DONE von 12 Byte ist angenommen). Der Absatz zum IGNORE-Bit („die Antwort ist DONE …“) erwähnt das Zurücksetzen von „Massenladen erwartet“ nicht, das nur das KDoc von `BulkDispatch` und die Zeile im Änderungsverzeichnis tragen. `SPEC-001` steht zudem nicht im Kopf „Berührte Spec-Stellen“ des Slice.
* verifizierbar: nein (Grep über `spec/` nach der alten Lesart)
* klasse: Fortschreibung lässt zweite Fundstelle stehen

### F-3 MEDIUM — Plan-Tabelle §3 und Kopf nennen nicht, was der Diff berührt

* kategorie: MEDIUM
* quelle: Reviewer-Skill „Plan-Tabelle nennt die berührten Dateien nicht“; Slice §3 („Der Implementer-Agent erweitert die Liste in seinem ersten Lauf“)
* pfad: `docs/plan/planning/in-progress/slice-bulk-load-feste-typen-und-referenzclient.md` · „`testserver/resources/integration/sim.yaml`, `tools/simulationstest.sh`“
* befund: Die Tabelle ist seit dem Move unverändert. Sie nennt `sim.yaml`, geändert ist `testserver/kotlin/…/IntegrationServer.kt` (Tabelle `bulk_ziel`); es fehlen `tools/mk/simulationstest.mk`, `protocol/dispatch/BulkCopyProbeSets.kt`, `protocol/token/TdsType.kt` und `TypeInfoWriter.kt` (Dezimalgröße), `harness/README.md` und `docs/plan/spec-abdeckung.md`. Im Kopf „Berührte Spec-Stellen“ fehlt `SPEC-001` (dort stehen `SPEC-036`, `SPEC-035`, `SPEC-040`, `SPEC-059`).
* verifizierbar: nein
* klasse: Plan-Tabelle nennt berührte Dateien nicht

### F-4 MEDIUM — Klassenkommentar des Referenztests nennt die falsche Datei für `bulk_ziel`

* kategorie: MEDIUM
* quelle: `AGENTS.md` §3.7 (Kopplung), §3.8
* pfad: `referenztest-dotnet/Integration/SqlBulkCopyTests.cs` · „`bulk_ziel` steht in `testserver/resources/integration/sim.yaml` und füllt der Bestand des Testservers“
* befund: Die Tabelle steht in `IntegrationTables.bulkTarget()` in `IntegrationServer.kt` (Kotlin, mit Stapel-Callback), nicht in `sim.yaml`; wer die Tabelle ändert und dem Kommentar folgt, ändert eine Datei, die `bulk_ziel` nicht kennt (`simulationstest.sh` zählt die sechste Tabelle richtig als Kotlin-Tabelle).
* verifizierbar: nein
* klasse: Kopplung im Kommentar nennt die falsche Stelle

### F-5 MEDIUM — Zusage „ruft keinen Callback des Integrators“ gilt für `bulkProbe` nicht

* kategorie: MEDIUM
* quelle: `AGENTS.md` §3.8; Reviewer-Skill „Zusage ohne ihre gemessene Bedingung“
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/port/BulkLoading.kt` · „baut, oder die Ablehnung. Die Funktion ruft keinen Callback des Integrators.“
* befund: `SimulationBulk.bulkProbe` ruft `BulkCopyForms.probe`, das den Katalog-Anbieter `catalog()` des Integrators aufruft (Parameter des öffentlichen Konstruktors von `SimulationBackend`, jetzt `internal val`); das KDoc von `SimulationBackend.bulkTarget` sagt dagegen richtig „Nur ein Text dieser Form fragt den Katalog-Anbieter“. Ein blockierender oder werfender Anbieter wirkt in der Anfrageschleife (er wird gefangen und ergibt `TDS_BACKEND_ERROR`, das Blockieren nicht).
* verifizierbar: ja (Lesen von `BulkCopyForms.probe`; `BulkCopyFormsTest` fährt den werfenden Anbieter)
* klasse: Zusage im KDoc breiter als der Code

### F-6 MEDIUM — Die Prüfung der zwei Bytes hinter der Spaltenzahl trägt kein Test

* kategorie: MEDIUM
* quelle: Reviewer-Skill „Eigencode, dessen Entfernung keinen Test rot macht“
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/protocol/bulk/BulkLoadReader.kt` · „if (gap && reader.u16Le() != 0) {“
* befund: Mutation M8 (die Bedingung ergibt nie einen Fehler) lässt die ganze Stage `test` grün (Exit 0): keine Nachricht im Bestand trägt Werte ungleich 0 an dieser Stelle, und die Deutung der Bytes ist nach `SPEC-001` nicht belegt. Eine Nachricht, deren zwei Bytes etwas anderes zählen, wird in der Form mit Lücke angenommen, sobald der Rest zufällig lesbar ist.
* verifizierbar: ja (Mutation, Lauf der Stage `test`)
* klasse: Eigencode ohne Test, dessen Entfernung nichts rot macht

### F-7 LOW — Fuzz der Nutzlast deckt die neuen Pfade nicht

* kategorie: LOW
* quelle: `SPEC-027` / [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität)
* pfad: `src/test/kotlin/io/github/pt9912/tdswire/stability/BulkLoadFuzzTest.kt` · „private val bases = listOf(mitschnitt, typen)“
* befund: `SPEC-040` führt „Fuzz der Nutzlast“; die Ausgangsnutzlasten des Tests sind unverändert (weder feste Typen noch die Form mit Nullbyte noch DONE von 8 Byte, noch die Messnachrichten des Referenzclients). Meine eigene Wiederholung mit den Messnachrichten als Basis habe ich begonnen, sie kam wegen eines hängenden Baus nicht zu einem Ergebnis; der Befund beruht auf dem Lesen des Tests.
* verifizierbar: ja (erweiterter Test, Lauf der Stage `test`)
* klasse: Fuzz-Basis nicht um neuen Pfad erweitert

### F-8 LOW — „zwei Tabellen“ ist weniger, als aufgezeichnet ist

* kategorie: LOW
* quelle: `AGENTS.md` §3.8 (Aussage ≠ Beleg, hier zu eng)
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/simulation/BulkCopyForms.kt` · „für zwei Tabellen\n * ohne Schema“ (ebenso `SPEC-036` „zwei Tabellen ohne Schema“, `docs/user/testserver-manuell.md` „aufgezeichnet sind zwei Tabellen ohne Schema“)
* befund: In den Messdateien stehen sechs Tabellen (`bulk_menge`, `bulk_typen`, `bulk_laenge`, `bulk_null`, `bulk_schluessel`, `bulk_typ`), alle ohne Schema, je Instanz; die Zahl ist falsch, die Richtung der Abweichung ungefährlich.
* verifizierbar: ja (Grep nach `OBJECT_ID('[` in den Messdateien)
* klasse: Zählung im Kommentar weicht vom Beleg ab

### F-9 LOW — Messzeile in `harness/README.md` ohne Host und Bedingung

* kategorie: LOW
* quelle: `AGENTS.md` §3.8; Zeile der Sensors-Tabelle für `make simulations-integration`
* pfad: `harness/README.md` · „55 s nach Quelländerung (Einzelmessung 2026-11-07)“
* befund: Die Zeile ersetzt „warm 19 s, nach Quelländerung 49 s (Einzelmessung 2026-11-01, Last 9)“ durch eine Zahl ohne Last und ohne Warmwert, während die Nachbarzeilen Last und Datum nennen; mein Lauf brauchte bei Last 13 bis 35 andere Zeiten.
* verifizierbar: nein
* klasse: Messzahl ohne Bedingung

### F-10 INFO — Abschneide-Erkennung geschwächt: Bewertung

* kategorie: INFO
* quelle: [TDS-QA-07](../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität)
* pfad: `src/test/kotlin/io/github/pt9912/tdswire/protocol/bulk/BulkLoadReaderTest.kt` · „if (length == full.size - SHORT_DONE_GAP) continue“
* befund: Angenommen wird genau eine Teilnachricht je Nachricht mit DONE von 12 Byte: die um vier Byte gekürzte. Sie enthält alle Zeilen vollständig; es fehlen die oberen vier Byte der Zeilenzahl von DONE, die der Leser nicht auswertet. Der Leser liest Zeilen bis zum Token DONE; eine andere Teilnachricht endet vor oder in einer Zeile oder im DONE (weniger als 8 Byte) und bleibt `TRUNCATED`, ein Inhalt wird also nie verkürzt angenommen. Test, KDoc von `done` und der neue Absatz in `SPEC-001` benennen die Ausnahme; der alte Satz in `SPEC-001` („eine abgeschnittene Nachricht … ergibt den Fehler“) nicht (F-2). Die Aussage, dass nur eine Länge angenommen wird, beruht auf dem Lesen von `done`, nicht auf einem Lauf über alle Längen.
* verifizierbar: ja
* klasse: Abschneide-Erkennung bewusst geschwächt

### F-11 INFO — Schema-qualifizierte Namen und Namen mit `]` oder `'` ohne Träger

* kategorie: INFO (Verweis an den Planner)
* quelle: Slice §6, zweites Risiko; [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate)
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/simulation/BulkCopyForms.kt` · „Regex(parts[0] + "\\[([^\\]']+)\\]"“
* befund: `DestinationTableName = "dbo.t"` oder `"[a]]b]"` ergibt `TDS_UNSUPPORTED_SQL`; `SPEC-036` und die Nutzer-Doku sagen es ehrlich. Eine Lücke gegen das Zielbild (der echte Server nimmt beide), für die weder eine Aufzeichnung noch ein Folge-Slice noch ein Register-Eintrag besteht; der Ausgang des Risikos ist in §6 noch „offen bis zur Closure“. Injektion: der Name wird nur im Katalog gesucht, die Meldung nennt ihn nicht; die Bindung an alle drei Stellen hält M3.
* verifizierbar: ja
* klasse: Zielbild-Lücke ohne Träger

### F-12 INFO — Zustandsfelder des Planners nennen noch die alte Grenze; Merge mit `main`

* kategorie: INFO (Verweis an den Planner und Verifier)
* quelle: `AGENTS.md` §3.7 (Zustandsfelder)
* pfad: `docs/plan/planning/in-progress/roadmap.md` · „die Nutzlast des Referenzclients liest der Testserver nicht“; `docs/plan/planning/observations/BEO-TDS/messung-am-echten-sql-server-fehlt/state.md` · „die **Nutzlast** des Referenzclients liest der Testserver nicht“
* befund: Beide Zellen behaupten die Grenze, die der Slice aufhebt; sie gehören zur Closure des Planners. Der Merge nach `main` (`c8aa1847`) ist textlich sauber (`git merge-tree`); überlappt sind `roadmap.md`, `harness/README.md` und die erzeugte Datei `docs/plan/spec-abdeckung.md` (nach dem Merge neu erzeugen); `main` ändert `Dockerfile`, `referenztest-dotnet/Dockerfile` und `test-lauf.sh`, die die Stage `integration` berühren können: nach dem Merge sind `make gates` und `make simulations-integration` zu wiederholen. Konflikte mit dem MERGE- oder Katalog-Slice im Code gibt es nicht (`main` ändert keine Quelle unter `src/`); die zwei auf `main` neuen ADR-Entwürfe (Proposed) zu Katalogspalten und Anweisungsart sprechen davon und nennen die Probe von `SqlBulkCopy` nicht.
* verifizierbar: ja (`git merge-tree`, Grep)
* klasse: Zustandsfeld nennt überholte Grenze

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `src/main/…/protocol/bulk/` (Größen der festen Typen, Vorzeichen, kein NULL bei festen Typen, PLP und NBCROW abgewiesen, Zellgrenze 262 144 vor dem Anlegen der Zeile, zweiter Lesedurchgang nur bei Fehler des ersten) | geprüft, ohne Befund |
| `src/main/…/protocol/dispatch/` (`BulkDispatch`, `RequestLoop.answerBatch`: Probe vor `bulk.statement`, `awaiting` bei IGNORE und Probe, Antwort ohne `execute`) | geprüft, ohne Befund; Synchronität [TDS-QA-07](../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität) unberührt (eine Antwort je Anfrage) |
| `src/main/…/port/`, `runtime/SimulationBulk.kt`, `simulation/SimulationBackend.kt` (interne Methode `bulkProbe`, `internal val catalog`) | geprüft, ohne Befund außer F-5; `make flaeche` grün, kein neuer öffentlicher Name ([ADR-0021](../plan/adr/0021-oeffentlicher-integrations-einstieg.md), `SPEC-059`); `make gates` mit `detekt` grün, keine Suppression, keine Schichtverletzung |
| `src/main/…/protocol/token/` (`DecimalType.bytes`, Standard 0) | geprüft, ohne Befund; bisherige Aufrufer unverändert |
| Sicherheit [TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit) | geprüft, ohne Befund: kein Name und kein Wert in den Meldungen der Probe („Tabelle nicht beschrieben“), kein Log |
| `src/test/` (Referenzclient-Tests, Probe, Formen, Dispatch) | geprüft, ohne Befund außer F-7 und F-10; Mutationen M1 bis M7 rot, M8 grün (F-6) |
| `referenztest-dotnet/Integration/SqlBulkCopyTests.cs`, `testserver/…/IntegrationServer.kt`, `tools/simulationstest.sh`, `tools/mk/simulationstest.mk` | geprüft, ohne Befund außer F-4; Tabellenzahl 6 und Testzahl 8 an allen Stellen nachgezogen (Grep) |
| `docs/user/*` (alle fünf Dateien gegen den Stand nach dem Slice; Grep auf „nicht gebaut“, „Zielzustand“, „bisher“, „noch nicht“, „heute“ im Umfeld des Massenladens) | geprüft, ohne Befund außer F-8: die Aussagen zu `SqlBulkCopy` sind Ist-Zustand; die Grenze „Schema und Sonderzeichen im Namen“ steht |
| `docs/plan/kompatibilitaetsmatrix.md` (Zeile BULK_LOAD) | geprüft, ohne Befund; alle zitierten Testfälle bestehen (`make matrix-zitate`) |
| Haken, Closure, Register | geprüft, ohne Befund: kein Liefer-Haken gesetzt, §7 des Slice unberührt, Register nur um Zeiger nachgezogen (Move) |
| Bezug auf `main` | siehe F-12 |

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 1 |
| MEDIUM | 5 |
| LOW | 3 |
| INFO | 3 |

**Finding-Klassen dieses Laufs:** Kommentar beschreibt verworfene Alternative · Fortschreibung lässt zweite Fundstelle stehen · Plan-Tabelle nennt berührte Dateien nicht · Kopplung im Kommentar nennt die falsche Stelle · Zusage im KDoc breiter als der Code · Fuzz-Basis nicht um neuen Pfad erweitert · Zählung im Kommentar weicht vom Beleg ab · Messzahl ohne Bedingung · Zielbild-Lücke ohne Träger · Eigencode ohne Test, dessen Entfernung nichts rot macht

## Verdikt

**Merge-blockierend:** ja, wegen F-1 (HIGH, ein Satz) und F-2 bis F-6 (MEDIUM); keiner der Befunde betrifft das Verhalten der Bibliothek. Der Code selbst trägt: der Leser liest die 10 Messnachrichten byte-genau, die Antwort der Probe ist für zwei Tabellen der Aufzeichnung byte-gleich (bis auf die Nullbyte), `d-migrate` (Import, Export, doppelter Schlüssel, Transfer) und `SqlBulkCopy` laufen gegen den Testserver, und die Mutationen M1 bis M7 sind rot.

**Übergabe:** Findings gehen an den Implementer; die Finding-Klassen gehen in die Slice-Closure §7. Dieser Bericht ersetzt keine Verifikation gegen die DoD.
