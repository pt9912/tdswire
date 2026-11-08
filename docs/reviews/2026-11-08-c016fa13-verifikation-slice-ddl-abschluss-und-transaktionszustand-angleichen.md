# Verifikationsbericht: slice-ddl-abschluss-und-transaktionszustand-angleichen am Stand c016fa13 — 2026-11-08

**Rolle:** Verifier (Frage: „Bauen wir es richtig?“ gegen Plan, DoD, Spezifikation und Entscheidungen). Der Bericht repariert nichts, ändert keinen Produktivcode und setzt keinen Haken.

**Gegenstand:** [slice-ddl-abschluss-und-transaktionszustand-angleichen](../plan/planning/done/slice-ddl-abschluss-und-transaktionszustand-angleichen.md), Bezug [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-QA-07](../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität), [ADR-0042](../plan/adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) (Entscheidung 2, 5, 6, 8), [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md). Eingang: Review `2026-11-08-81c689c2-review-slice-ddl-abschluss-und-transaktionszustand-angleichen.md` (5 MEDIUM, 1 LOW, 4 INFO; vom Implementer in `c016fa13` behoben, keine zweite Review).

**Stand:** Zweig `worktree-agent-a8801e0535faa69ff`, HEAD `c016fa13`. `main` steht auf `c7d37a9a` (Merge des Wanduhr-Slice); der Zweig enthält ihn **nicht** und wurde nicht gemergt. Merge-Probe `git merge-tree --write-tree HEAD main`: zwei Inhaltskonflikte, `docs/plan/planning/in-progress/roadmap.md` und `testserver/test/kotlin/…/TableStoreTest.kt` (beide Seiten ändern die Datei; die Zweig-Seite die erwarteten Ergebnisse auf `UpdateCount(0, StatementKind.CREATE_TABLE)`, `main` die Wanduhr-Tests). `main` änderte seit der Basis außerdem `ListenerAbortTest.kt` und `ListenerSupport.kt`.

**Haken:** keine `- [x]` im Slice (geprüft). §7 ist leer, die vier Risiken aus §6 tragen noch „offen bis zur Closure“; beides ist Sache des Planners.

---

## 1. Selbst gefahrene Läufe

| Lauf | Ergebnis |
|---|---|
| `make clean-build` (genau einmal, End-Stand `c016fa13`, **ohne** Wanduhr-Merge) | Exit 0; 994 s Wanduhr (Load Average 6 bis 18 durch andere Läufe); Stufen: `build-jar` 46 s, `test` **8 min 57 s** (537 s; `BUILD SUCCESSFUL`, 1298 Testzeilen `PASSED`, keine `FAILED`), `detekt` 3 min 55 s |
| Dauer von `TableStoreTest` | nicht einzeln gemessen (Gradle druckt keine Klassendauer; die Zeilen der Klasse liegen im Log zwischen Sekunde 60 und 397 der Stufe `test`, also verteilt). Gemessen ist nur die Stufe `test` als Ganzes: 537 s. Mit dem Wanduhr-Merge ist die Dauer neu zu messen |
| `make e2e` | Exit 0, 8 Stufen grün, 485 s: smoke 136 s, laufzeittest 108 s, simulations-integration 59 s, `kompatibilitaet` Szenario 59 s / Anmeldung 21 s / Luecken 31 s / Synchronitaet 49 s, eigenes-backend 22 s |
| `make kompatibilitaet` einzeln | nicht gesondert gefahren: es sind die Stufen 4 bis 7 von `make e2e` (`harness/README.md`), ein zweiter Lauf wäre dieselbe Messung |
| `make gates FORCE=1` | Exit 0, 35 s (Docker-Schichten aus dem Cache; derselbe Eingang wie der Lauf davor, kein Frischbeleg; der Frischbeleg ist `make clean-build`) |
| `make flaeche` | Exit 0 (Selbsttest und Prüfung) |
| `make docs-check` | 1298 Dateien, 0 Befunde (vor Anlage dieses Berichts) |
| `make matrix-zitate` | nicht gefahren (der Implementer nennt 343 Zitate). Gegenprobe ohne das Ziel: alle 330 Zitate der Form `Klasse > Testfall` in `docs/plan/kompatibilitaetsmatrix.md` stehen im Log meines `clean-build` als `PASSED` (0 fehlend) |
| Mutationen (zwei, einzeln, Kopie des Baums im Scratchpad mit Wegwerf-Stage, 51 Tests der sechs engen Klassen) | siehe 3 |
| d-migrate am End-Stand | siehe 5 |
| Wegwerf-Probe des Referenzclients gegen den Testserver mit `CREATE TABLE`, `ALTER TABLE`, Transaktionstexten | siehe 4 |

Keine künstliche Last. Alle eigenen Container, Netzwerke und Images (`tdswire-testserver:verif-V`, Probe-Client-Image, Rig-Container) sind entfernt; Rig-Container liefen mit `--user`/ohne Mounts, keine root-eigenen Dateien im Arbeitsbaum (`git status` sauber).

## 2. Antwortbytes gegen die Messdateien (Schwerpunkt 1)

Gelesen aus `referenztest-dotnet/Mitschnitt/messung/bulk-merge-katalog/{2022,2025}/katalog-struktur.messung` (Server nach Client, mit eigenem Dekodierer, nicht mit dem des Implementers):

| Text | Bytes am echten Server | Bau (Test und Code) | Urteil |
|---|---|---|---|
| `set implicit_transactions on ` | `FD 0000 00B9` + 8 Byte 0 | `IMPLICIT_TRANSACTIONS_ON`, ohne `COUNT` | gleich |
| `CREATE TABLE …` danach | `E3 000B 08 08 0100000035000000 00`, dann `FD 0000 00C6` | ENVCHANGE Typ 8, Länge 0x000b, neuer Wert 8 Byte, alter leer, **vor** dem DONE | gleich |
| `ALTER TABLE … ADD` | `FD 0000 00D8` (Kopf der Anfrage trägt den Deskriptor) | `ALTER_TABLE`, ohne ENVCHANGE | gleich |
| `IF @@TRANCOUNT > 0 COMMIT TRAN` | `FD 0100 00C0`, `E3 000B 09 00 08 0100000035000000`, `FD 0000 00D5` | `DONE MORE 0xc0`, Typ 9 (neuer Wert leer, alter = Deskriptor), `DONE 0xd5` | gleich |
| `set implicit_transactions off IF … COMMIT TRAN ` ohne offene Transaktion | `FD 0100 00BA`, `FD 0000 00C0` | `IMPLICIT_TRANSACTIONS_OFF`, `COMMIT_IF_OPEN`, ohne ENVCHANGE | gleich |
| `CREATE TABLE` ohne Schlüssel (ohne `set`) | `FD 0000 00C6` | `CREATE_TABLE` | gleich |
| Fehler 2714 / 4902 / 4902-Variante | `AA …` + `FD 0200 00FD` | unverändert | gleich |

Instanz 2025: Bytes identisch bis auf den Deskriptor (`…33…` statt `…35…`, im ENVCHANGE und im Kopf der Folgeanfragen). Der Bau schreibt `SessionTransaction.DESCRIPTOR = 0x0000_0035_0000_0001` (little endian `01 00 00 00 35 00 00 00`) und kennzeichnet ihn als Setzung (KDoc nennt `35` für 2022, `33` für 2025). Das ist durch die Messung gedeckt (der Server wählt den Wert je Transaktion, der Client sendet ihn zurück), AGENTS.md §3.8 ist erfüllt. Offen bleibt, was die Messung nicht trägt: der Server-Wert ist je Transaktion verschieden, der Testserver sendet immer denselben.

## 3. Mutationen F-2 und F-5 (Schwerpunkt 2)

Ausgangslage der Kopie: `c016fa13`; 51 Tests der Klassen `ResponseTransactionTest`, `TransactionDispatchTest`, `ResponseStatementKindTest`, `SimulationBackendStructureTest`, `SimulationBackendFixedTest`, `CatalogFormsTest`.

| Nr | Mutation | Ergebnis |
|---|---|---|
| F-2 | `FixedAnswers.kt`, `else -> StatementKind.UNKNOWN` zurück auf `else -> StatementKind.IMPLICIT_TRANSACTIONS_OFF` | **rot**: 2 Tests (`SimulationBackendFixedTest > SET endet als Aenderungszaehler 0`, `SimulationBackendStructureTest > Nur das einzelne Setting implicit_transactions meldet eine Art`), 51 Tests, 2 rot |
| F-5 | `RequestLoop.kt`, `transaction = SessionTransaction()` durch ein Feld eines `private object Shared` ersetzt (Zustand von allen Verbindungen geteilt) | **rot**: genau 1 Test (`TransactionDispatchTest > Eine zweite Verbindung sieht die offene Transaktion der ersten nicht`), 51 Tests, 1 rot |

Beide Behebungen sind im Code (`FixedAnswers.kt`, `StatementKind.kt`-KDoc, Test) und wirksam; kein anderer Test fängt F-5, der neue Test ist der einzige Wächter (so wie das Review es verlangte). Die Mutationen sind aus der Kopie, der Arbeitsbaum blieb unberührt.

## 4. Referenzclient gegen die neuen Antworten (Schwerpunkt 3, Review F-8)

**`make e2e` ist grün (8 Stufen), belegt aber F-8 nicht:** keine Stufe von `make kompatibilitaet` stellt `CREATE TABLE`, `ALTER TABLE`, `set implicit_transactions` oder den bedingten `COMMIT` (Suche in `referenztest-dotnet/Kompatibilitaet/*.cs`: nur `BeginTransaction` in `AnmeldungTests.cs`), und das Backend des Kompatibilitätsservers (`KompatibilitaetsServer.kt`) hat keine Strukturänderung. Die Stufen belegen, dass der Umbau der Abschlüsse (Kennungen, `COUNT`-Bits) für `INSERT`/`UPDATE`/`DELETE`/`SELECT` den Referenzclient nicht stört, nicht mehr.

**Wegwerf-Probe (nicht im Repo, Scratchpad):** `Microsoft.Data.SqlClient` 7.1.1 im Probe-Image (Stage `kompatibilitaet` der Kopie plus eine Testklasse `EnvchangeProbeTests`) gegen den gebauten Testserver (`make testserver-up`, `sim.yaml`, `TEST_UNENCRYPTED`, eigener Name und Port 14391). Zwei Tests, beide `Passed`:

- Die Folge von `schema migrate` auf einer Verbindung: `SELECT 1`, `SET LOCK_TIMEOUT 30000`, `set implicit_transactions on `, `CREATE TABLE`, `ALTER TABLE [kunden] ADD`, `IF @@TRANCOUNT > 0 COMMIT TRAN`, `set implicit_transactions off IF … COMMIT TRAN `, danach ein zweiter Durchgang auf derselben Verbindung und ein `SELECT`: jede Anweisung gelingt, `ExecuteNonQuery` liefert -1 für die Anweisungen ohne `COUNT` (0 für `SET LOCK_TIMEOUT`, das unverändert ist), die Verbindung bleibt `Open`.
- Zwei Verbindungen, die Zustände sind unabhängig (zweite Verbindung: bedingter `COMMIT` ohne Transaktion), und `Close()` bei offener Transaktion (letztes `CREATE TABLE` nach `set implicit_transactions on`): kein Fehler, die andere Verbindung bleibt benutzbar.
- Das Log des Testservers nennt weder `request_failed` noch `connection_failed` (0 Treffer); die Verbindungen enden mit `HANDLER_RETURNED`.

Was die Probe **nicht** zeigt: den zurückgesendeten Deskriptor am Draht (kein Mitschnitt der Richtung Client nach Server); die Wirkung ist dieselbe wie bei den Läufen mit d-migrate, der Test mit zurückgesendetem Deskriptor steht in `TransactionDispatchTest` und im Einbettungstest, und M-20 zeigt, dass der Referenzclient ihn sendet. Auch nicht: TLS (die Probe lief mit `Encrypt=Optional` gegen den unverschlüsselten Testserver; die Stufen von `make e2e` laufen mit TLS, aber ohne diese Texte), den Fehlerpfad `ROLLBACK`, und mehr als zwei Verbindungen. Die Probe ist Einzelmessung am Referenzclient (AGENTS.md §3.8), keine Aussage über andere Clients.

Folge für die Closure: F-8 ist als „Referenzclient nimmt die Antworten an“ belegt, aber **nicht reproduzierbar im Repo**; ob die Probe als Test der Kategorie `Luecken` oder `Szenario` ins Repo kommt, entscheidet der Planner (siehe Bedingungen).

## 5. d-migrate 1.12.0 am End-Stand (Schwerpunkt 4)

Image `ghcr.io/pt9912/d-migrate@sha256:308f27753036c9cc423f716b9fc4c9e68016543a8003ecd2f8282e05a2ee6491`, Testserver aus dem Arbeitsbaum `c016fa13` (`make testserver-up`, `TESTSERVER_NAME=verif-ts-V`, Port 14390, `sim.yaml`), frischer Server, Soll-Dateien `soll2.yaml`, `soll3.yaml` und `sim.yaml` aus dem Lauf des Implementers kopiert (nicht neu geschrieben). 140 s.

| Befehl | Exit (End-Stand) | Exit A/B des Implementers | Ergebnis |
|---|---|---|---|
| k1 `schema reverse` | 0 | 0 | `rev.yaml` gleich B |
| k3 `schema compare` (gegen k1) | 0 | 0 | `IDENTICAL`, Ausgabe gleich B |
| k4 `compare` gegen `sim.yaml` | 0 | 0 | `IDENTICAL`, gleich B |
| k5 `schema migrate soll2.yaml --execute` | 0 | 0 | `status: ok`, `statementsAttempted` 2, `transactionRolledBack` false; Bericht gleich B (nur der Port der URL verschieden) |
| k6 `compare` gegen `soll2.yaml` | 0 | 0 | `IDENTICAL` |
| k7 `schema reverse` danach | 0 | 0 | `rev7.yaml` gleich B |
| k9 zweiter `migrate` | 0 | 0 | `status: no_op`, `statementsAttempted` 0, gleich B |
| k10 `migrate soll3.yaml --execute` | 0 | 0 | `status: ok`, `statementsAttempted` 1, gleich B |
| k11 `compare` gegen `soll3.yaml` | 0 | 0 | `IDENTICAL` |
| `data export --format csv --split-files` | 0 | nicht gelaufen | vier Dateien (`kunden`, `bestellungen`, `produkte`, `notizen`) |
| `data import notizen.csv --table notizen` (2 Zeilen) | 0 | nicht gelaufen | `2 inserted` |
| `data export --tables notizen` danach | 0 | nicht gelaufen | `Id,Text` mit den zwei Zeilen: Rundlauf gleich |

Vergleich mit B: `diff` der Dateien `rev.yaml`, `rev7.yaml`, `k1`/`k3`/`k4`/`k6`/`k11.log`, `rep5`/`rep9`/`rep10.yaml` nach Ersatz der Zeitstempel, Kennungen und des Ports: **kein Unterschied** außer dem Port. Der End-Stand verhält sich damit wie Lauf B; die Aussage des Implementers („unverändert“) trägt für den End-Stand, nicht nur für `279289f7`. `data export` und `data import` waren in A/B nicht gelaufen; ihre Exit-Codes sind neu und grün, es gibt keine Vergleichsbasis davor (der Import läuft über RPC und Massenladen, wo der Testserver kein ENVCHANGE schreibt; das ist die benannte Grenze F-1, mit d-migrate unschädlich).

Aufgeräumt: Container `verif-ts-V`/`verif-ts-P`, Image `tdswire-testserver:verif-V`; keine fremden Images berührt.

## 6. DoD-Zeile für Zeile

| DoD-Zeile | Beleg | Urteil |
|---|---|---|
| Punkt 1: `CREATE_TABLE` `0x00c6`, `ALTER_TABLE` `0x00d8`, `DONE` ohne `COUNT`; `affectedRows` bei diesen Arten ignoriert; Simulations-Backend meldet die Art; Backend ohne Art bleibt `UNKNOWN`; SPEC-035/036 ohne „Änderungszähler 0“; SPEC-059 nennt die Mitglieder; Einbettungstest `integrator`; Tests je Art gegen M-20; Zeile M-20 gezogen | Bytes 2: gleich; `ResponseStatementKindTest`, `StatementKindEmbeddingTest` in meinem `clean-build` grün; SPEC-059 Liste (Zeile 390) und Änderungsprotokoll nennen die Mitglieder; M-20 in `referenzabgleich.md` und Matrix angepasst | **erfüllt** |
| Punkt 2: Transaktionstexte (`0x00b9`; `0x00ba` `MORE` + `0x00c0`); Zustand je Sitzung wie Handles, endet mit ihr; Mitglieder nur für Gemessenes, Rest `UNKNOWN`; Tests je Text | Bytes 2: gleich; Zustand in `RequestLoop` je Schleife, `RESETCONNECTION` setzt zurück (Test grün); Mutation F-2 und F-5 rot (3); `set implicit_transactions off` allein, `ROLLBACK`, `set … on` mit Bedingung = `UNKNOWN` (`SimulationBackendStructureTest`) | **erfüllt**; Abweichung (bedingter `COMMIT` ohne ENVCHANGE gehört schon zu Punkt 2, `COMMIT_IF_OPEN`) ist im Slice §3 offen als Abweichung geführt und durch die Messdatei gedeckt (`ba`/`c0` ohne ENVCHANGE) |
| Punkt 3: ENVCHANGE Typ 8 und 9 zuletzt; Deskriptor aus Handvektor; Test mit zurückgesendetem Deskriptor; behalten nur bei unverändertem Lauf mit d-migrate (Läufe A und B im Slice) | Bytes 2: gleich; Deskriptor als Setzung gekennzeichnet; Test `TransactionDispatchTest > Die Anfragen mit zurueckgesendetem Deskriptor …` grün; Läufe A/B im Slice §3 und Messprotokoll; mein End-Stand-Lauf = B | **erfüllt**; der Rückfall D war nicht nötig (Entscheidung 8 erfüllt: Lauf nach Punkt 3 unverändert) |
| `make gates` grün | `make gates FORCE=1` Exit 0 (35 s, aus dem Cache) und `make clean-build` Exit 0 (Frischbeleg) | **erfüllt** (Stand vor dem Wanduhr-Merge) |
| Review durchgeführt, Report liegt vor | `docs/reviews/2026-11-08-81c689c2-review-…`; Findings F-1 bis F-7 in `c016fa13` behoben (siehe 7); keine zweite Review | **erfüllt**; die Behebungen hat der Verifier nachgeprüft (7) |
| Haken setzt der Planner | keine Haken gesetzt | **erfüllt** |
| Doku-Update bei öffentlichem Vertrag | SPEC-059 (Liste, Änderungsprotokoll mit „Inkompatibel“ für `when` ohne `else`), `docs/user/einbettung-und-betrieb.md`, `referenz-konfiguration.md`, Kompatibilitätsmatrix | **erfüllt** |
| Closure-Notiz mit Steering-Loop-Lerneintrag | §7 „Wird bei der Closure gefüllt“ | **offen** (Planner) |
| Beobachtungs-Register fortgeschrieben | neues Verzeichnis `BEO-TDS/envchange-bei-dml-und-rpc-fehlt-gegen-den-echten-server` (observation, state, evidence), Eintrag in Verweis-Zeilen anderer Register | **erfüllt** (siehe Hinweis in 8) |
| Jedes Risiko aus §6 trägt einen Ausgang | alle vier stehen auf „offen bis zur Closure“ | **offen** (Planner); Vorschlag in 8 |
| Drei Paarungen | Anker/Folge-Slice/Register: Register ja; Folge-Slice für ENVCHANGE außerhalb von `CREATE TABLE`: nicht angelegt, Träger-Vorschlag im Register | Planner |

## 7. Behebung der Review-Findings, nachgeprüft

| Finding | Prüfung | Urteil |
|---|---|---|
| F-1 Grenze ENVCHANGE bei DML und RPC | SPEC-031 (Zeile ~790: je Instanz 27 Typ 8 und 26 Typ 9, `#14` `MERGE`, `#19` Katalogabfrage), M-20, Messprotokoll der d-migrate-Läufe und Register nennen die Abweichung als gemessen und nicht gebaut; Träger `BEO-TDS/envchange-bei-dml-und-rpc-fehlt-gegen-den-echten-server` | behoben; Aussage ≤ Beleg |
| F-2 Setzung gegen [ADR-0042](../plan/adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) Entscheidung 2 | Code `else -> UNKNOWN`, KDoc `StatementKind.IMPLICIT_TRANSACTIONS_OFF`/`FixedAnswers`/SPEC-036/SPEC-059 angepasst; Mutation rot | behoben |
| F-3 zweite Fundstellen | Kompatibilitätsmatrix (ENVCHANGE-Zeile mit Typ 8/9, `StatementKind`-Zeile), KDoc in `SimulationBackend.kt`, `TransactionForms.kt`, `CatalogFormsTest.kt` | behoben |
| F-4 Plan-Tabelle und Kopf | `Completion.kt` steht in §3; Kopf nennt SPEC-002, SPEC-022-Vertrag und [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend).a; die Zeile „SPEC-030“ ist im Kopf noch genannt (Diff ohne Hunk dort) und `docs/plan/kompatibilitaetsmatrix.md` sowie `docs/user/referenz-konfiguration.md` fehlen in der Tabelle | im Wesentlichen behoben; Restpunkt ohne Gewicht (siehe 8) |
| F-5 Test „Zustand je Verbindung“ | `TransactionDispatchTest > Eine zweite Verbindung …`; Mutation rot | behoben |
| F-6 Inkompatibilität `StatementKind` | Änderungsprotokoll SPEC-059 („Inkompatibel … `when` ohne `else` … fünf neuen Mitglieder“), Nutzer-Doku | behoben |
| F-7 SESSIONSTATE (INFO) | Ausnahme benannt (Messprotokoll, Grenze); d-migrate-Lauf grün ohne | unverändert, als Unterschied festgehalten |

## 8. Befunde des Verifiers

| ID | Schwere | Befund | Quelle | Verifizierbar |
|---|---|---|---|---|
| V-1 | MEDIUM | `docs/plan/spec-abdeckung.md` (Zeile 119) trägt `SPEC-043 … ServerSmokeTest.Ein Prozess ohne JVM-Option startet mit TLS … rot` und `SPEC-043 … 42 bestanden / 1 rot`. Der Test ist **kein** Fehler dieses Slice: der Diff berührt weder `ServerSmokeTest` noch Start/TLS; in meinem `make clean-build` (frisch, ohne Cache) steht er `PASSED`, ebenso im Lauf von `make gates` des Implementers. Einordnung: Zeitabhängiger Prozesstest (Kind-JVM mit TLS-Start) unter hoher Last der Entwicklungsläufe (Load Average 9 bis 18); Last-Flake, nicht reproduziert. Auf `main` steht die Zeile ohne Rot (`SPEC-043 … 43 … 0`). Die Matrix behauptet damit einen roten Test, den das Repo nicht hat, und fällt bei der Zusammenführung mit `main` (Wanduhr-Slice ändert Tests) ohnehin neu an. **Die Matrix muss nach dem Merge neu erzeugt werden** (`make spec-abdeckung`, auf dem zusammengeführten Stand), nicht von Hand ändern | AGENTS.md §3.8, §5 Regel 6 | `grep -n "SPEC-043" docs/plan/spec-abdeckung.md`; `main` gegenlesen |
| V-2 | MEDIUM | Der Beleg für Review F-8 (Referenzclient gegen die neuen Antworten) liegt nur als Wegwerf-Probe außerhalb des Repos; `make e2e`/`make kompatibilitaet` stellen die Texte nicht (siehe 4). Die Aussage „der Referenzclient nimmt ENVCHANGE Typ 8 und 9 des Testservers an“ ist damit Einzelmessung des Verifiers, nicht wiederholbar. Entscheidung des Planners: Probe als Test in `referenztest-dotnet/Kompatibilitaet` (mit einem Kompatibilitätsserver, dessen Tabellen `onStructure` kennen) aufnehmen oder den Satz als Einzelmessung im Messprotokoll stehen lassen | [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität), AGENTS.md §3.8 | Probe-Quelltext liegt im Scratchpad dieses Laufs |
| V-3 | MEDIUM | Der Zweig ist nicht gegen `main` (`c7d37a9a`) zusammengeführt; `TableStoreTest.kt` und `roadmap.md` haben Inhaltskonflikte, `main` hat `src/`/`testserver/`-Tests geändert (`ListenerAbortTest`, `ListenerSupport`, `TableStoreTest`). Mein `clean-build` (994 s, Stufe `test` 537 s) und `make e2e` gelten für den **Zweig-Stand**; nach dem Merge ist mindestens `make gates` neu zu fahren (die Stufe `test` ändert sich) | harness/README.md (Sensor-Zuschnitt) | `git merge-tree --write-tree HEAD main` |
| V-4 | LOW | Das Register `BEO-TDS/envchange-bei-dml-und-rpc-fehlt-gegen-den-echten-server` (evidence) schreibt „Entscheid des Nutzers (Standardantwort)“. Ein Nutzerentscheid ist in diesem Lauf nicht belegt (der Planner-Auftrag nennt nur den Implementer-Vorschlag „kein Folge-Slice angelegt“). Die Formulierung ist zu belegen oder auf „Setzung des Implementers“ zu ändern | AGENTS.md §3.8 | `grep -n "Entscheid des Nutzers" docs/plan/planning/observations/BEO-TDS/envchange-bei-dml-und-rpc-fehlt-gegen-den-echten-server -r` |
| V-5 | LOW | Plan-Kopf nennt `SPEC-030 (Abschluss nach Änderung im RPC, soweit berührt)`; kein Hunk in SPEC-030, und im Plan-Abschnitt §3 fehlen die Zeilen `docs/plan/kompatibilitaetsmatrix.md` und `docs/user/referenz-konfiguration.md` (beide im Diff). Ohne Wirkung auf Verhalten | Reviewer-Skill (Plan-Tabelle) | Diff gegen §3 |
| V-6 | INFO | Läufe A und B des Implementers sind an `7b478a2e`/`279289f7` geführt, mein End-Stand-Lauf (5) ist der fehlende Lauf am Endstand; er liegt im Scratchpad, nicht im Messprotokoll des Repos. Das Protokoll sagt „ein erneuter Lauf am Endstand steht aus“: der Planner kann den Satz mit meinem Ergebnis nachführen (Einzelmessung, Verifier, 2026-11-08, ein Host unter Last) | AGENTS.md §3.8 | Abschnitt 5 |
| V-7 | INFO | Offene Grenzen unverändert und benannt: `ROLLBACK` bei offener Transaktion ohne ENVCHANGE Typ 10 (Review F-10, Fehlerpfad nie gelaufen), ENVCHANGE bei DML/RPC (V-6/F-1), SESSIONSTATE (F-7), TLS und mehr als zwei Verbindungen | [ADR-0042](../plan/adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) Entscheidung 6 | SPEC-031, Nutzer-Doku |

## 9. Plan-Tabelle §3 gegen Diff, öffentliche Fläche

- Die Dateien des Diffs `e8757e09..c016fa13` außerhalb der Planungsunterlagen decken sich mit §3 und der Erweiterung des Implementers; zusätzlich zu §3: `docs/plan/kompatibilitaetsmatrix.md`, `docs/user/referenz-konfiguration.md` (V-5), `docs/reviews/*`. `harness/README.md` ist **nicht** geändert, die 200-Zeichen-Grenze der Zellen ist damit nicht berührt.
- Öffentliche Fläche: `make flaeche` Exit 0 (prüft Typnamen, nicht Mitglieder). SPEC-059 Liste und Änderungsprotokoll nennen `CREATE_TABLE`, `ALTER_TABLE`, `IMPLICIT_TRANSACTIONS_ON`, `IMPLICIT_TRANSACTIONS_OFF`, `COMMIT_IF_OPEN`; der Einbettungstest nennt alle neun Konstanten; das Änderungsprotokoll trägt „Inkompatibel“ für ein `when` ohne `else`. Die Klarstellung „Namen sind Setzung“ steht ([ADR-0042](../plan/adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) Entscheidung 2 erlaubt den Bau der Mitglieder an gemessenen Fällen).
- [ADR-0042](../plan/adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) Entscheidung 2 („Nichtgemessenes bekommt kein Mitglied, endet als `UNKNOWN`“): nach `c016fa13` im Code erfüllt (`IMPLICIT_TRANSACTIONS_OFF` nur in der gemessenen Kombination). Entscheidung 8 (d-migrate-Läufe): erfüllt (Läufe A, B, End-Stand).
- ADRs unverändert (AGENTS.md §3.5), keine Suppression (detekt grün), keine Docker-Verstöße.

## 10. Bedingungen für die Closure des Planners

1. **Nach dem Merge mit `main`** (Konflikte in `roadmap.md` und `TableStoreTest.kt` auflösen; im Test beide Seiten erhalten: die Erwartung `UpdateCount(0, StatementKind.CREATE_TABLE)` und die Tests des Wanduhr-Slice): `make gates` neu fahren (die Stufe `test` ändert sich); `make clean-build` ist nur nötig, wenn der Planner die Frischprobe der Wanduhr-Tests mit dem neuen `TableStoreTest` will (Dauer neu messen, mein Wert 537 s für die Stufe `test` gilt ohne den Merge).
2. **`docs/plan/spec-abdeckung.md` neu erzeugen** (`make spec-abdeckung` auf dem zusammengeführten Stand), damit die Zeile `SPEC-043 … rot` verschwindet (V-1); den roten Eintrag nicht von Hand streichen.
3. **Entscheiden zu V-2:** Probe des Referenzclients als Test ins Repo oder den Beleg als Einzelmessung im Messprotokoll der d-migrate-Läufe nachtragen (Abschnitt 4 und 5 dieses Berichts sind die Vorlage).
4. Die Zeile „ein erneuter Lauf am Endstand steht aus“ im Messprotokoll der d-migrate-Läufe nachführen (V-6) und V-4 klären (Nutzerentscheid belegen oder Wortlaut ändern).
5. §6 Risiken je mit Ausgang: (1) ENVCHANGE-Reaktion von Treiber/Referenzclient: **entfallen** für mssql-jdbc (d-migrate-Lauf) und Referenzclient (Probe, Einzelmessung), mit der offenen Grenze Fehlerpfad `ROLLBACK`; (2) falsche Art vom Backend: **weiter offen** (benannte Grenze SPEC-031/SPEC-059); (3) bedingter `COMMIT` nur mit ENVCHANGE 9: **entfallen** (ENVCHANGE gebaut); (4) Einzelmessung je Instanz: **weiter offen**.
6. Haken der drei Liefer-Punkte und die Nachweis-Pflichten setzt der Planner nach diesen Bedingungen; die DoD-Zeilen 1 bis 3 sind nach meiner Prüfung erfüllt, offen sind Closure-Notiz, Risiko-Ausgänge und der Folge-Slice-Träger.
7. Der Folge-Slice für ENVCHANGE bei DML und RPC (`BEO-TDS/envchange-bei-dml-und-rpc-fehlt-gegen-den-echten-server`) bleibt ohne Träger; die Anlage ist Sache des Planners.

## 11. Zeit

Gesamtlauf des Verifiers etwa 30 Minuten Wanduhr; größte Posten: `make clean-build` 994 s (davon Stufe `test` 537 s, `detekt` 235 s), `make e2e` 485 s, d-migrate-Rig 140 s, zwei Mutationen je etwa 80 s, Probe des Referenzclients (Bau und Lauf) etwa 3 min, `make gates FORCE=1` 35 s. Last Average des Hosts 6 bis 18 durch parallele Läufe anderer Rollen; die Werte sind Einzelmessungen.
