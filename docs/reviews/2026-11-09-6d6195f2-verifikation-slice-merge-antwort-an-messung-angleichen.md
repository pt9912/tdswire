# Verifikationsbericht: slice-merge-antwort-an-messung-angleichen (HEAD 6d6195f2)

* Rolle: Verifier (Modul 11), frischer Kontext. Gegenstand: Slice `slice-merge-antwort-an-messung-angleichen` (`in-progress/`, Zweig `worktree-agent-a1c6bb892ac862eb0`, HEAD `6d6195f2`, Basis `main` `37551c87`, unverändert; Merge-Probe `git merge-tree` gegen `git rev-parse main`: sauber, Exit 0). Frage: Bauen wir es richtig, gegen Plan, DoD und Entscheidung.
* Maßstab: DoD §2, Plan §3, [ADR-0042](../plan/adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) (Accepted, Option A), [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), [ADR-0044](../plan/adr/0044-massenladen-nachricht-ist-atomar-wie-am-echten-server.md), [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), SPEC-002, 003, 022, 031, 035, 036, 059.
* Grundsatz des Nutzers: „Spec ist Zielbild“, der echte SQL Server ist die Referenz. Verifiziert wird die Zusage, nicht der Diff; Belege sind eigene Läufe. Am Produktivcode nichts geändert (Mutationen liefen in Wegwerf-Kopien im Scratchpad, nicht im Repo). Haken: keiner gesetzt (`grep -c '\[x\]'` im Slice = 0), ich setze keine.
* Der Review `2026-11-08-206d0baf-review-slice-merge-antwort-an-messung-angleichen.md` (0 HIGH, 4 MEDIUM, 2 LOW) deckt `206d0baf`. Die Nachbesserung `b9eef33a` (und `899fc346`, `c5f63876`, `6d6195f2`) hat keinen zweiten Review; ich habe sie unten gegen F-1 bis F-9 einzeln geprüft.

## Eigene Läufe (Belege)

Host: Linux 6.8, andere Gradle-Läufe nebenher (Implementer eines anderen Slice); keine künstliche Last. Alle Läufe im Vordergrund bzw. mit Warten auf das Log und Exit-Code.

| Lauf | Ergebnis |
|---|---|
| `make clean-build` (genau einmal, auf dem End-Stand `6d6195f2`) | Exit 0, jede Gradle-Stufe `BUILD SUCCESSFUL`; 1315 `PASSED`, 0 rote Testfälle (die 7 „FAILED“-Treffer im Log sind Testnamen, die PASSED enden); 9 min 19 s real |
| `make matrix-zitate` | nicht neu gebaut: `tools/matrix-zitate.sh docs/plan/kompatibilitaetsmatrix.md <clean-build-Log>` Exit 0, 1315 bestandene Testfälle, 349 Zitate, 0 ohne PASSED, 0 nicht gelesene Formen (Zitate seit dem Review unverändert, `git diff 206d0baf..HEAD` an der Matrix leer) |
| `make e2e` | Exit 0, 8 Stufen grün, 330 s (Stufe 1 77 s, 2 84 s, 3 49 s, 4 44 s, 5 14 s, 6 16 s, 7 34 s, 8 12 s); kein Flake, keine Wiederholung. **Einordnung:** kein Treffer `merge` im Log; kein Szenario des Referenzclients (`referenztest-dotnet/*.cs`, Szenario, Anmeldung, Lücken, Synchronität) fährt ein `MERGE` gegen den Testserver (die zwei Dateien mit `MERGE` sind `Mitschnitt/MessungBulkBelegTests.cs` und `Vergleich/MessungBulkTests.cs`, nur gegen den echten Server). Das `MERGE` am Testserver belegen die Integratortests und der d-migrate-Lauf unten, nicht `make e2e` |
| `make gates FORCE=1` | Exit 0 in 29 s. **Cache-Falle:** 88 Docker-Schritte `CACHED` (Quellen identisch zu Implementer und Reviewer); der Lauf belegt die Verdrahtung der Gates, nicht die Frische der Tests; den Frischbeleg trägt `make clean-build`. `gate-zuordnung`: 16 Gates, Selbsttest 33 Fälle |
| `make flaeche` | Exit 0 (Selbsttest und Prüfung) |
| `make docs-check` | Exit 0, 1322 Dateien, 0 Befunde (vor Anlage dieses Berichts) |
| Rohbytes selbst dekodiert | siehe Abschnitt Draht |
| Mutationen (7, einzeln, enge Testklassen) | alle 7 rot, siehe Abschnitt Mutationen |
| d-migrate 1.12.0 (Digest `sha256:308f27753036…`) gegen `make testserver-up` (Container `verif-merge-A`/`-B`/`-C`, Ports 14433 bis 14435, Test-Image `tdswire-testserver:verif-merge`) | Exit-Codes und Ausgaben gleich dem Messprotokoll, siehe Abschnitt d-migrate; Container und mein Test-Image danach entfernt |

Befehle, Skripte und Logs liegen im Scratchpad `/tmp/claude-1000/-Development-tdswire/3768ddc4-cff6-4152-b0be-815e9ed5f5cb/scratchpad/` (`v/`, `dm/`, `mutall.log`; nicht im Repo).

## Gesamturteil

**Closure-fähig: ja, unter den Bedingungen B1 bis B5 unten (alle in Planner-Hand, keine am Code).** Der Kern trägt und ist in eigenen Läufen belegt: die Antwortbytes stimmen mit den Rohbytes beider Instanzen überein, `make clean-build` ist frisch grün, alle sieben Mutationen färben Tests rot, der d-migrate-Lauf entspricht dem Messprotokoll, `make flaeche` und `make docs-check` sind grün. Zwei Punkte der DoD-Zeile (c) sind mit benannten Abweichungen erfüllt, nicht ganz nach Wortlaut (V-1, V-2): das Planner-Urteil darüber ist B1.

## DoD-Punkte

| # | DoD-Punkt | Urteil | Beleg |
|---|---|---|---|
| (a) | Befehl `0x0117` des Abschlusses | **erfüllt** | `StatementKind.MERGE` (`StatementKind.kt`), `DoneCommand.forResult` und `countsRows` (`Token.kt`), Weitergabe über `ResponseRun.resultKind` an `Completion`, `failure()`, `report()`, `RpcTrailer.afterResult`; `SPEC-031`, `SPEC-036`, `SPEC-059` (Liste, Änderungsprotokoll) nennen es. Test gegen die Tokenfolge: `ResponseMergeTest` (11 Fälle, Bytes im Test = Messbytes, von mir gegengelesen, s. Draht); Mutation M2 rot |
| (b) | `$action` als `nvarchar(10)` | **erfüllt** | `ResultColumn.maxLength` (Vorgabe 4000, `@JvmOverloads`, `init` 1..4000), `ColumnMapping` schreibt `NVarcharType(column.maxLength, …)`, `SimulationMerges.ACTION_LENGTH = 10`; `make flaeche` grün, `SPEC-059` nennt das Feld; Einbettungstests `MergeEmbeddingTest`, `StatementKindEmbeddingTest` (Länge, Java-Konstruktor); COLMETADATA (Typ `e7`, Länge 20 Byte = 10 Zeichen, Flags, Nullbarkeit) gegen M-26 im `ResponseMergeTest`; Mutationen M1 und M7 rot |
| (c) | Fehlernummern des `MERGE` | **erfüllt mit benannten Abweichungen** (V-1, V-2) | 2628: `TDS_SIM_STRING_TRUNCATED` (Class 16, State 1, offen), Prüfung je ausgeführter Aktion; 2627: Fehler von `onInsert` (Sammelcode, Setzung, gekennzeichnet); 208: unbekannte Tabelle, Nummer, Tokenfolge, DONEPROC-Fehlerbit wie gemessen. Ein Test je Nummer: `ResponseMergeTest` (Draht), `SimulationBackendMergeTest` (Zuordnung), `MergeFehlerEmbeddingTest` (Integrator), `ErrorMapperTest` (Positivliste). `SPEC-036` (Fehlercode-Tabelle, „Grenzen der Angleichung“) nennt sie und die Abweichungen. Die Reihenfolge der Zeile vor dem Fehler (M-15) ist nicht gebaut, als Grenze benannt (DoD erlaubt „oder die Abweichung ist benannt“) |
| – | `make gates` grün | **erfüllt** | `make gates FORCE=1` Exit 0 (aus dem Cache, s. o.); Frischbeleg `make clean-build` Exit 0 |
| – | Review durchgeführt, Report liegt vor | **erfüllt** | `docs/reviews/2026-11-08-206d0baf-review-…`; Nachbesserung ohne zweiten Review (B4) |
| – | Haken nur durch den Planner | **eingehalten** | 0 gesetzte Haken |
| – | Doku-Update bei öffentlichem Vertrag | **erfüllt** | `docs/user/{einbettung-und-betrieb,referenz-konfiguration,testserver-manuell}.md` fortgeschrieben, gegen Code und Spec stimmig; `SPEC-059` Änderungsprotokoll 2026-11-08 |
| – | Closure-Notiz, Register, Risiken §6, Paarungen | **offen, Planner-Pflicht** | §7 „Wird bei der Closure gefüllt.“ (richtig); §6: zwei Risiken „offen bis zur Closure“ (Tabelle ohne Schlüssel; Reihenfolge der Antwort im Fehlerfall), ein Ausgang „zu bestätigen“ |

## Draht: Antwortbytes gegen die Rohbytes

Selbst dekodiert aus `referenztest-dotnet/Mitschnitt/messung/bulk-merge-katalog/{2022,2025}/merge-{update,skip,fehler}.messung` (Server-Nachrichten `nachricht s`; die zwei Instanzen sind byteweise gleich in den Antwortnachrichten):

| Gegenstand | Rohbytes | Befund |
|---|---|---|
| Spalte `$action` | `81 01 00 | 00000000 00 00 | e7 1400 0904d00034 | 07 24 00 61 00 …` | Typ `e7` (NVARCHAR), Länge 0x0014 = 20 Byte = 10 Zeichen, Kollation `09 04 d0 00 34`, Name `$action` (7 Zeichen). Ergebnis `nvarchar(10)`, Flags 0 (nicht nullbar). Die zwei Byte vor dem Typ (`00 00`, Lesart CekTable-Zähler, nicht belegt) schreibt die Bibliothek wie bisher nicht; Bytes ab dem Typ stimmen mit `ResponseMergeTest` |
| Erfolg | `d1 0c00 INSERT` oder `d1 0c00 UPDATE`, dann `ff 1100 1701 <Zähler 8 Byte>` | DONEINPROC Status `0x0011` (`MORE|COUNT`), Befehl `0x0117`, Zähler 1; danach RETURNSTATUS `79 00000000`, DONEPROC `fe 0000 e000` (Status 0) |
| Ohne Aktion (`merge-skip`, Schlüssel 1) | `ff 1100 1701 0000…` (Zähler 0), keine ROW | COUNT auch ohne Zeile, Befehl `0x0117` |
| 2628 | `aa 2c01 44 0a 0000 01 10 …` | Nummer 0x0a44 = 2628, State 1, Class 16 (0x10) |
| INFO nach ERROR | `ab …` 3621 „The statement has been terminated.“ nach dem ERROR (2628 und 2627), vor dem Abschluss | Reihenfolge ERROR, INFO, DONEINPROC, RETURNSTATUS, DONEPROC; so in `ResponseRun.report` (`BULK_TERMINATED` nach dem ERROR) |
| Abschluss des Fehlers | `ff 0300 1701 00000000 00000000` | Status 0x0003 (`MORE|ERROR`), Befehl `0x0117`, ohne COUNT; RETURNSTATUS `79 440a0000` (2628) bzw. `79 430a0000` (2627), DONEPROC `fe 0000 e000` |
| 2627 | `aa 4601 43 0a 0000 01 0e …` | Nummer 2627, State 1, Class 14 (0x0e); in der Messung nach der Zeile `INSERT` (Schlüssel doppelt in zwei `VALUES`-Zeilen) |
| 208 | `aa 7200 d0000000 01 10 …`, dann `79 d0000000`, `fe 0200 e000` | Nummer 208, Class 16 (0x10), State 1; **kein** COLMETADATA, kein INFO, kein DONEINPROC; DONEPROC mit Fehlerbit (Status 0x0002). Die Bibliothek sendet dieselbe Tokenfolge, aber Class 20 (siehe V-2) |

## Mutationen (einzeln, Wegwerf-Kopie des Zweigs, `docker run --user 1000:1000 --network none`, Gradle offline; Basislauf ohne Mutation Exit 0)

| Nr. | Mutation | Testklassen | Ergebnis |
|---|---|---|---|
| M1 | `ACTION_LENGTH` 4000 statt 10 (`SimulationMerges.kt`) | `SimulationBackendMergeTest`, `MergeEmbeddingTest` | rot: 12 von 22 und 1 von 4 |
| M2 | `DoneCommand.forResult` immer `SELECT` (`0x00c1`) | `ResponseMergeTest`, `MergeEmbeddingTest`, `MergeFehlerEmbeddingTest` | rot: 6 von 11 und 3 von 6 |
| M3 | kein INFO 3621 (`terminated = false`) | `ResponseMergeTest`, `MergeFehlerEmbeddingTest` | rot: 3 von 11 und 1 von 2 |
| M4 | `ResultStart` ohne Art im Simulations-Backend | `ResponseMergeTest`, `MergeEmbeddingTest`, `MergeFehlerEmbeddingTest` | rot: 3 von 6 Integratorfällen (der Draht-Unit-Test baut seine Ereignisse selbst und fällt hier erwartungsgemäß nicht) |
| M5 | keine Prüfung der Textlänge (`truncates` immer falsch) | `SimulationBackendMergeTest`, `MergeFehlerEmbeddingTest` | rot: 1 von 22 und 1 von 2 |
| M6 | Fehler von `onInsert` wieder `TDS_BACKEND_ERROR` statt 2627 | `SimulationBackendMergeTest`, `MergeFehlerEmbeddingTest` | rot: 1 von 22 |
| M7 | `ColumnMapping` schreibt fest 4000 statt `maxLength` | `ResponseMergeTest`, `MergeEmbeddingTest` | rot: 5 von 11 und 1 von 4 |

Alle sieben fielen in den erwarteten Klassen; kein Eigencode der Änderung blieb in diesen Zweigen ungetragen.

## d-migrate 1.12.0 gegen den Testserver (MERGE-Pfad)

Zwei Testserver (Port 14433 = A, 14434 = B) mit der Beschreibung der Tabellen aus `docs/messungen/merge-output-2026-11-07.md` (`kunden`, `kunden_int`, `bestellungen`, `zeiten`, `Name` 4000); Eingabedateien aus dem Protokoll (`zeiten.json`: drei Zeilen, das Protokoll nennt vier Zeilen, kein Einfluss auf die Aussage). Ergebnis je Lauf gleich der Spalte „Ergebnis“ des Protokolls:

| Lauf | Exit | Ergebnis (eigener Lauf) |
|---|---|---|
| S1 `import … --on-conflict skip` (leer) | 0 | 3 inserted; Export gleich Eingabe (Umlaute, NULL) |
| S2 `skip` auf vorhandenen | 0 | „1 inserted, 2 skipped“; Zeilen 1 bis 3 unverändert, 4 neu |
| S3 `update` | 0 | „3 updated“; Zeile 1 `Ada geändert`, Zeile 3 Name NULL |
| S4/S5 `data transfer skip`/`update` | 0 | „Transferred: kunden“; Export B = Export A |
| S6/S7 `kunden_int` `skip`/`update` | 0 | „3 inserted“; „1 inserted, 2 updated“ |
| S8 Standardpfad `kunden_int` | 5 | „converting the Long value to JDBC data type INTEGER“ (Grenze von d-migrate, wie M-28) |
| S9 `bestellungen` `skip`/`update` | 0 | 5 inserted; 5 updated; Export `"12.50"`, NULL, `"-0.05"`, `"99999999.99"`, `"0.00"` |
| S10 `zeiten` `skip`/`update` | 0 | inserted/updated; Export einschließlich `"00:00:00"`, `"2026-01-02T23:59:59.123"`, `false`, `"0001-01-01"` |
| S11/S12 `transfer bestellungen,zeiten` `skip`/`update` | 0 | „Transferred“ beide; Exporte B = A |
| S13 Standardpfad `transfer bestellungen` | 5 | „Violation of PRIMARY KEY constraint. Cannot insert duplicate key.“ |

Neu gegenüber dem Protokoll belegt (Server C, voreingestelltes `sim.yaml`, `Name` 100): `import --on-conflict skip` und `update` mit einem Namen von 101 Zeichen auf neuen Schlüssel sowie `update` auf vorhandenen Schlüssel enden mit Exit 5 „String or binary data would be truncated.“ (2628 erreicht den Treiber); `skip` auf vorhandenen Schlüssel mit langem Text bleibt ohne Fehler (Setzung der Spec, am echten Server nicht gemessen); der Server bleibt danach verfügbar, folgender `update` Exit 0. Ein Name über 4000 Zeichen (Server A) endet mit „Unsupported RPC“, weil d-migrate dann einen `nvarchar(max)`-Parameter sendet; das ist eine Grenze der Parameterform, nicht dieser Änderung (nicht gegen die Basis nachgefahren).

**Eine Wirkung für Nutzer:** die mitgelieferte Beschreibung `testserver/resources/manuell/sim.yaml` führt `Name` mit `max_length: 100`; seit diesem Slice weist ein `MERGE` (d-migrate `--on-conflict`) einen längeren Namen ab, wie es der echte Server bei `nvarchar(100)` täte. Das ist Zielbild-konform, aber eine Verhaltensänderung des Testservers gegenüber dem Stand des Messprotokolls (V-3).

## Entscheidungs-Konformität

* [ADR-0042](../plan/adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) Option A (ein Feld, kein neues Ereignis; Entscheidung 1, 3, 4, 7): eingehalten für `StatementKind.MERGE` und `UpdateCount.kind`; kein Text-Klassifizierer im Protokoll (die Art kommt vom Backend); `@JvmOverloads` und Vorgabe `UNKNOWN` halten bestehende Backends (`new ResultStart(columns)` aus Java belegt im Einbettungstest `ResultStartKindEmbeddingTest`). **Offen (V-4):** die ADR nennt als Fläche nur `StatementKind` und `UpdateCount.kind`; `ResultStart.kind` steht nicht in der angenommenen ADR (unveränderlich, AGENTS.md §3.5). Der Entscheid des Nutzers vom 2026-11-09 steht im Slice (§3, §6), in `SPEC-002` und im Änderungsprotokoll von `SPEC-059`, nicht in einer ADR. Inhaltlich folgerichtig (kein neues Ereignis, kein `when` bei Integratoren gebrochen); formal eine Quelle unter dem Rang der ADR.
* [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 4 (Fläche wächst bewusst): `ResultColumn.maxLength` per Nutzerentscheid 2026-11-08, `make flaeche` grün, `SPEC-059` Liste und Änderungsprotokoll mit der Inkompatibilität für `when`/`copy`/`componentN`/`equals`.
* [ADR-0044](../plan/adr/0044-massenladen-nachricht-ist-atomar-wie-am-echten-server.md): nicht berührt; das `MERGE` ruft weiter höchstens einen Callback, die Atomarität des Massenladens ist unverändert (`SimColumn.maxLength` wird dort weiter nicht geprüft, SPEC-035).
* Hard Rules: kein `@Suppress`, kein Host-Aufruf, `config/detekt/detekt.yml`, `harness/`, `AGENTS.md`, `Dockerfile`, `Makefile`, `build.gradle.kts` unberührt (`git diff main...HEAD --stat` leer für diese Pfade); `harness/README.md` unberührt, keine neue Zelle über 200 Zeichen.

## Nachbesserung gegen die Review-Findings (ohne zweiten Review)

| Finding | Stand in `6d6195f2` |
|---|---|
| F-1 `ResultStart.kind` | Nutzerentscheid 2026-11-09 vermerkt (Slice §3/§6, SPEC-002, SPEC-059 Änderungsprotokoll); Einbettungstest in eigener Klasse; ADR-Lücke bleibt (V-4) |
| F-2 KDoc `maxLength` „prüft keine Zeile“ | KDoc und SPEC-002 korrigiert („weist … ab“); Test `Ein Text einer Zeile ueber der Laenge der Spalte endet in einem Fehler`; Mutation M7 trägt ihn |
| F-3 KDoc `SimColumn.maxLength` | angepasst (`SimModel.kt`: „geprüft nur beim MERGE“) |
| F-4 Plan-Tabelle | Kopf und Tabelle §3 gegen den Diff ergänzt (SPEC-002, 003, 022, 035, `TDS-FA-11.a`, Roadmap, Welle, Abdeckung, Messprotokoll, Register); gegen `git diff --stat` abgeglichen: keine Datei ohne Zeile |
| F-5 STATEMENT-Scope ohne INFO | als Setzung gekennzeichnet (SPEC-031: „Setzung, nicht gemessen“), Test `Ein Fehler der Klasse STATEMENT im Ergebnis eines MERGE hat kein INFO` |
| F-6 Zustandsfelder | Register und Slice §6 auf „entschieden“ gezogen |
| F-7 `UpdateCount(n, MERGE)` | Test `Ein Aenderungszaehler der Art MERGE schliesst mit 0x0117 und COUNT`, Satz in `docs/user` („nicht am echten Server gemessen“) |
| F-9 208 | Nachtrag im Register `zielbild-luecken-der-antwort-tokens`; Träger der Class-Abweichung weiter offen |

## Abweichungen und Befunde

| ID | Kategorie | Befund |
|---|---|---|
| V-1 | DoD-Wortlaut (c) | „derselbe Schlüssel zweimal in der Quelle → 2627“: gebaut ist 2627 nur für einen Fehler von `onInsert` (Sammelcode). Zwei `VALUES`-Zeilen mit demselben Schlüssel bleiben `TDS_UNSUPPORTED_SQL`, die Zeile `INSERT` vor dem Fehler (M-15) gibt es nicht. Beides ist in `SPEC-036` („Grenzen der Angleichung“), im Messprotokoll und in `docs/user` benannt; die DoD erlaubt die benannte Abweichung nur für die Reihenfolge. Planner entscheidet, ob (c) so schließt (B1) |
| V-2 | Zielbild-Abweichung | 208: Nummer, Tokenfolge und DONEPROC-Fehlerbit wie gemessen; die Bibliothek sendet Class 20 und schließt die Verbindung, der Server Class 16 und offen. Benannt (SPEC-036, `ErrorCode.kt`, Messprotokoll); Träger: Register `unbekannte-tabelle-class-20-statt-16-am-server`. Der Slice-Satz „die Bibliothek sendet heute `TDS_BACKEND_ERROR`“ trifft für 208 nicht zu (war schon `TDS_SIM_UNKNOWN_OBJECT`) |
| V-3 | Verhaltensänderung | Das `MERGE` prüft `SimColumn.maxLength` (2628); die mitgelieferte Beschreibung des Handlaufs hat `Name` 100. Konform mit dem echten Server, dokumentiert in `docs/user/einbettung-und-betrieb.md` (Zeile zu `maxLength`) und `SPEC-035`; `docs/user/testserver-manuell.md` ist fortgeschrieben. Eine Zeile dort zu der Länge 100 der mitgelieferten Tabelle habe ich nicht gesucht |
| V-4 | Entscheidung unter ADR-Rang | `ResultStart.kind` ohne Träger in einer ADR (s. o.) |
| V-5 | Beleg-Lücke | `make e2e` fährt kein `MERGE` (s. o.); die Belege sind Integratortests (Rohbytes) und d-migrate. Kein Referenzclient-Szenario des Referenzclients für die Antwort des `MERGE` am Testserver; vor dem Slice ebenso nicht |
| V-6 | Zeitdokument | `docs/messungen/merge-output-2026-11-07.md` nennt weiter „zwei Merkmale … nicht gebaut“; Beleg-Stand `6d07620b` steht im Kopf, nur der Slice-Link ist nachgezogen (Review F-10, zulässig) |

## Bedingungen für die Planner-Closure

* **B1:** Planner entscheidet über V-1 und V-2: (c) als erfüllt mit benannten Grenzen schließen und den DoD-Wortlaut in §7 entsprechend einordnen, oder die doppelten `VALUES`-Zeilen / Class 16 über einen Folge-Slice führen. Für die Class-Abweichung ist das Register der Träger; „offen — kein Träger“ im Register bleibt ein Zustandsfeld, das der Closure-Notiz bekannt sein muss.
* **B2:** §6-Ausgänge setzen: Entscheid zu `ResultColumn` und `ResultStart.kind` = entfallen (entschieden); `MERGE` auf eine Tabelle ohne Schlüssel = weiter offen (bleibt `TDS_UNSUPPORTED_SQL`) mit Träger oder Beobachtung; Reihenfolge der Antwort im Fehlerfall = weiter offen (nicht gebaut, Grenze in SPEC-036) mit Träger. Closure-Notiz mit Steering-Loop-Lerneintrag, Register fortschreiben oder „keine Beobachtung angefallen“, die drei Paarungen tragen. Haken (a), (b), (c) und die Nachweis-Pflichten setzt der Planner.
* **B3:** `make gates` und der Frischbeleg liegen auf `6d6195f2`. Jede weitere Änderung an `src/` oder `testserver/` vor der Closure erfordert `make clean-build` neu; nach Merge auf `main` die Merge-Probe gegen den dann aktuellen `main` (heute `37551c87`, sauber).
* **B4:** Nachbesserung `b9eef33a` bis `6d6195f2` ohne zweiten Review: der Planner nimmt diesen Bericht als Prüfung dazu (F-1 bis F-9 einzeln oben geprüft) oder lässt einen Kurzreview folgen. Kein weiterer Code-Commit ohne Review.
* **B5:** V-4 klären: Bestätigung des Nutzers in einer ADR-Schärfung (Folge-ADR mit `Schärft`/`Supersedes` nach AGENTS.md §3.5) oder ausdrücklich im Slice als ausreichende Quelle belassen. V-3: eine Zeile zur Länge 100 der mitgelieferten Beschreibung in `docs/user/testserver-manuell.md` ist wünschenswert, kein Hindernis.

## Zeit

Gesamtlaufzeit dieser Verifikation bis zur Anlage des Berichts etwa 31 Minuten. Größte Posten: `make clean-build` 9 min 19 s, sieben Mutationen mit eigenem Gradle-Lauf zusammen 7 min 27 s (je 57 s bis 1 min 5 s), `make e2e` 5 min 29 s (Stufe 1 77 s, Stufe 2 84 s), d-migrate-Läufe samt Server-Start etwa 3 min, `make gates FORCE=1` 29 s (aus dem Cache), `make flaeche` und `make docs-check` je unter 1 min. Kein Flake, keine Wiederholung.
