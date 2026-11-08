# Verifikationsbericht: slice-massenladen-atomar-bauen (HEAD 128378b8)

* Rolle: Verifier (Modul 11), frischer Kontext. Gegenstand: Slice `slice-massenladen-atomar-bauen` (`in-progress/`, Zweig `worktree-agent-ad2fa7ce14f75b1b2`, HEAD `128378b8`, Basis `a830d6d1`; `main` steht inzwischen auf `142bdb20`). Frage: Bauen wir es richtig, gegen Plan, DoD und Entscheidung.
* Maßstab: Slice §2 (DoD) und §3 (Plan), [ADR-0044](../plan/adr/0044-massenladen-nachricht-ist-atomar-wie-am-echten-server.md) (Accepted, Option A, K-a; Atomarität über M-12 hinaus ist Setzung mit Trigger 1), [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) (soweit nicht ersetzt), [SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends), [SPEC-036](../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig), [SPEC-001](../../spec/spezifikation.md#spec-001--zustandsautomat-einer-verbindung), [SPEC-059](../../spec/spezifikation.md#einstieg-des-integrators-vertrag-zu-spec-059).
* Grundsatz des Nutzers: „Spec ist Zielbild“, der echte SQL Server ist die Referenz.
* Verifiziert wird die Zusage, nicht der Diff; Belege sind eigene Läufe. Am Produktivcode habe ich nichts geändert (Mutationen liefen in Wegwerf-Kopien aus `git archive HEAD`, der Arbeitsbaum blieb sauber). Haken habe ich nicht gesetzt.
* Der Review `2026-11-08-9e144097-review-slice-massenladen-atomar-bauen.md` deckt `9e144097` (0 HIGH, 3 MEDIUM, 4 INFO). Die Nachbesserung `128378b8` hat keinen zweiten Review; ich habe sie unten gegen F-1 bis F-7 einzeln geprüft (Diff `9e144097..HEAD`: ein KDoc-Satz in `SimModel.kt`, Nutzer-Doku, Messprotokoll, Slice §3, ein Verweis in `done/`, der Review-Report; **keine Zeile Programmlogik**).

## Eigene Läufe (Belege)

Host: Last durch fremde Läufe, Einzelmessungen. Alles im Vordergrund bzw. mit Warten auf das Log; keine künstliche Last. Der Scratchpad-Ordner `verif/` war schon belegt (Dateien anderer Rollen); ich habe darin ungewollt gleichnamige Dateien überschrieben (siehe Abschnitt „Eigene Panne“), die übrigen Läufe liegen in `.../scratchpad/verifier-massenladen-2026-11-08/`.

| Lauf | Ergebnis |
|---|---|
| `make clean-build` (genau einmal, auf dem End-Stand `128378b8`) | Exit 0, 9 min 51 s real; 1300 `PASSED`, 0 `FAILED`, 0 `SKIPPED` (`:test`, `:testserver:test`, `integratorTest`); `BUILD SUCCESSFUL in 3m 10s` der Test-Stufe; `ListenerAbortTest` (alle fünf Fälle, auch der bekannte Wanduhr-Fall) grün |
| `tools/matrix-zitate.sh docs/plan/kompatibilitaetsmatrix.md <Log des clean-build>` (statt `make matrix-zitate`) | Exit 0: 1300 bestandene Testfälle, 343 Zitate, 0 ohne PASSED, 0 Fremdformen. Zitate nicht geändert: Menge der Zitate der Matrix gegen `a830d6d1` gleich (`diff` der extrahierten Zitate leer), der Diff der Matrix berührt nur die Prosa der Zeile `BULK_LOAD` |
| `make e2e` | Exit 0, „gruen (8 Stufen, 447 s)“, 7 min 28 s real; Stufe 3 (`simulations-integration`) 53 s, Stufen 4 bis 7 und 8 grün, **kein Flake**, keine Wiederholung nötig |
| `make gates FORCE=1` | Exit 0 in 35 s. **Cache-Falle:** die Docker-Schichten kamen aus dem Cache der Läufe davor (gleiche Quellen); der Lauf belegt die Verdrahtung der Gates (`baseline-verify` OK, 54 Dateien; `gate-zuordnung` 16 Gates, 33 Fälle; `stempel-eingaenge` ok), nicht die Frische der Tests. Den Frischbeleg trägt `make clean-build` |
| `make flaeche` | Exit 0 (Selbsttest und Prüfung) |
| `make docs-check` | Exit 0, 1309 Dateien, 0 Befunde (vor Anlage dieses Berichts) |
| Rohbytes `bulk-verstoss-schluessel.messung`, 2022 und 2025 | selbst dekodiert, siehe Abschnitt Draht |
| Mutationen (drei, einzeln, enge Testklassen) | alle drei rot, siehe Abschnitt Mutationen |
| d-migrate 1.12.0 (Digest `sha256:308f27753036…`) gegen `make testserver-up` (Name `tdswire-verif-mass`, Port 14437; für `data transfer` ein zweiter Container `tdswire-verif-mass2`, Port 14438) | siehe Abschnitt d-migrate; Container und Test-Image danach entfernt |
| Merge-Probe `git merge-tree --write-tree HEAD main` (`main` = `142bdb20`) | Konflikte, siehe Abschnitt Merge-Probe |
| `make spec-abdeckung`, `make matrix-zitate` (als Make-Target) | nicht gefahren (kein Gate bzw. nach Zuschnitt ersetzt durch das Skript gegen das Log) |

Gesamtlaufzeit: rund 45 min Wanduhr. Größte Zeitposten: `make clean-build` 9 min 51 s, `make e2e` 7 min 28 s, drei Mutationen zusammen rund 5 min (je 1 bis 1,5 min Gradle-Lauf plus Bau der Schichten), Lesen und Dekodieren der Aufnahme, d-migrate-Läufe und Merge-Probe je rund 2 bis 4 min.

## Gesamturteil

**Closure-fähig: ja, unter den Bedingungen B1 bis B3 unten (alle in Planner-Hand, keine am Code).** Der Kern trägt und ist in eigenen Läufen belegt: die Aufnahme M-12 zeigt in beiden Instanzen genau den Fall, auf den die Zusage gestützt ist; der Code ruft nur noch `onInsertBatch`, einmal je Nachricht, der Rückfall ist weg, und jede der drei Mutationen färbt die erwarteten Tests; `make clean-build` und `make e2e` sind frisch grün; `SqlBulkCopy` und `d-migrate` verhalten sich gegen den Testserver wie die Zusage sagt (Schlüsselverstoß: 2627 bzw. Exit 5, Bestand unverändert). Offen sind Entscheidungen und Pflege des Planners (Wortlaut der DoD-Zeile „Bestand des Testservers“, Merge mit `main`, Closure-Notiz), nicht Mängel des Baus.

## DoD-Punkte

Haken: keiner gesetzt (`grep -c '\- \[x\]'` über den Slice = 0; im Diff gegen die Basis kein neuer `- [x]` am Slice), die Regel `BEO-TDS/haken-vor-verifikation-gesetzt` ist eingehalten. Ich setze keine.

| # | DoD-Punkt | Urteil | Beleg |
|---|---|---|---|
| 1 | Spezifikation | **erfüllt** | `SPEC-035` (Absatz `onInsertBatch`: Zusage „ganz oder gar nicht“, Rückfall entfällt, Grenze „gemessen ist ein Fall (…, M-12)“ im selben Absatz, „keine Transaktion“ nur noch außerhalb einer Nachricht), `SPEC-036` (Form B: Tabelle braucht `onInsertBatch`, „auch mit `onInsert`“; Antwort bei Fehler; Tabellenzeile), `SPEC-001` (Antwort bei Fehler: Sammelcode für andere Ursachen, Atomarität über M-12 hinaus Setzung, die offenen Fälle einzeln benannt), Fehlertabelle (`TDS_SIM_KEY_VIOLATION`: „Nummer für andere Ursachen … Sammelcode (Setzung)“), Schritt 10 von `TDS-FA-11.a`, `SPEC-059` (Nachweis-Zeile nennt beide Einbettungstests; Änderungsprotokoll mit **Inkompatibel** für Integratoren mit nur `onInsert`), Änderungsprotokoll der Spezifikation. In den neuen Zeilen steht keine ADR-Kennung. Verweis auf [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 5 (iii)/Nr. 6 im Messprotokoll nachgezogen (`referenzabgleich.md`, Zeile M-11/M-12). `make docs-check` und `make flaeche` selbst gefahren, beide Exit 0. Zweite Fundstelle mit Rest: V-2 |
| 2 | Bibliothek und Tests | **erfüllt, Wortlaut mit Bedingung B1** | `BulkLoads.call` ruft nur `onInsertBatch` (einmal je Nachricht, leere Nachricht kein Aufruf, ohne `onInsertBatch` `TDS_UNSUPPORTED_SQL`); `StatementPlanner.bulk` prüft nur `onInsertBatch == null`; KDoc von `SimTable.onInsertBatch` (jetzt mit Grenze, F-7 behoben), `BulkLoads`, `SimulationBackend`. Tests: `SimulationBackendBulkTest` (Rückfall-Test ersetzt durch „eine Tabelle nur mit onInsert lehnt INSERT BULK ab und laedt keine Zeile“; Fehlerfall-Test prüft drei Zeilen in einem Aufruf, ein Aufruf, kein `onInsert`), `MassenladenEmbeddingTest` (zwei neue Fälle). `StatementPlannerTest` und `TableStoreTest` laut §3 geprüft, nicht im Diff (in `StatementPlannerTest` keine Stelle zum Rückfall; `grep` bestätigt). `KompatibilitaetsServer`/`TableStore` des Testservers: `TableStore` setzt `onInsertBatch` für jede Tabelle (`described`), `IntegrationServer` setzt es für die Dateitabelle, `person` in `KompatibilitaetsServer` hat nur `onInsert` und ist kein Ziel eines Massenladens. Belege: `make clean-build` (enthält `test` und `detekt`; 1300 PASSED), `make flaeche`, Mutationen. Das Wort „Bestand des Testservers“: V-1 / B1 |
| 3 | Doku | **erfüllt** | `docs/user/einbettung-und-betrieb.md`: der Absatz zum Rückfall entfällt, die Zusage des Stapel-Callbacks steht dort (mit der Grenze „ein Fall“ und der Zuordnung zu 2627); nach der Nachbesserung führt die Tabelle „Typische Fehler“ eine eigene Zeile `TDS_UNSUPPORTED_SQL` bei `INSERT BULK`, auch mit `onInsert` (F-2 behoben), die Zeile für `INSERT` als SQL-Text nennt `onInsert`, und der Aufzählungspunkt „Ohne Callback …“ nennt `onInsertBatch` für das Massenladen. `docs/messungen/referenzabgleich.md` (Grenze „ein Fall“), `docs/messungen/bulk-load-2026-11-07.md` (Mutationstabelle, M27 jetzt mit der gefahrenen Wirkung, F-1 behoben), Kompatibilitätsmatrix (Zeile `BULK_LOAD`, Zitate unverändert, `matrix-zitate` grün), Register `state.md` fortgeschrieben („Bau im Slice, Messung offen“, Anker auflösend). `harness/README.md` nicht berührt (Zellenlänge nicht betroffen) |
| – | Nachweis-Pflichten | Teil offen (Planner) | `make gates` grün (FORCE=1, mit Cache-Hinweis); Review liegt vor (`9e144097`); Haken: keiner gesetzt; Doku-Update öffentlicher Vertrag (`SPEC-059`) erfüllt; **Closure-Notiz §7: leer (Planner)**; Register: fortgeschrieben; §6: alle fünf Risiken tragen noch „offen bis zur Closure“ bzw. „weiter offen“ (Planner setzt die Ausgänge, siehe B3); die drei Paarungen prüft die Welle-Closure |

## Draht (Schwerpunkt 2): M-12 selbst dekodiert

`referenztest-dotnet/Mitschnitt/messung/bulk-merge-katalog/{2022,2025}/bulk-verstoss-schluessel.messung` (16 Nachrichten, 2022: SQL Server 2022 RTM-CU27-GDR 16.0.4305.5; 2025 analog, die Dateien unterscheiden sich im Kopf, die Nachrichten 9 bis 14 sind byte-gleich, SHA-256 der Dateien verschieden wegen des Kopfs). Ich habe die Hex-Nutzlast mit einem eigenen Skript gelesen:

* Nachricht 7 (Client): `insert bulk [bulk_schluessel] ([Id] BigInt, [Text] NVarChar(10) COLLATE …) with (CHECK_CONSTRAINTS, FIRE_TRIGGERS)`.
* Nachricht 9 (Client): Pakettyp `0x07`, Status `0x01` (letztes Paket), 108 Byte, **ein** Paket; COLMETADATA (`Id` BIGINT fest `0x7f`, `Text` NVARCHAR(10)) und **drei** ROW-Token (`0xd1`) mit `Id` 1, 2, 3 und den Texten `neu`, `doppelt`, `neu` (Offsets 40, 57, 82), dann DONE.
* Nachricht 10 (Server): ERROR `0xaa`, Nummer **2627**, State 1, Class **14**; INFO 3621 folgt; Abschluss `fd02 00f0 …` = DONE mit Status `0x0002` (ERROR), Befehl `0x00f0`, ohne Zähler.
* Nachrichten 11/12: ATTENTION (Pakettyp 6) und Bestätigung.
* Nachricht 13 (Client): `SELECT COUNT_BIG(*) FROM bulk_schluessel`; Nachricht 14 (Server): ROW mit 8 Byte Wert `01 00 00 00 00 00 00 00`, also **`COUNT_BIG(*)` = 1**.

Folgerung: die Meldung nennt den doppelten Schlüssel 2 (also lag die Zeile mit `Id` 2 vor dem Paket im Bestand); nach dem Verstoß in der Mitte (Zeile 2 von 3) steht genau 1 Zeile in der Tabelle. Die Zeile 1 vor dem Verstoß ist **nicht** im Bestand, die Zeile 3 danach auch nicht. Das trägt exakt den Fall der Zusage: Schlüsselverstoß, drei Zeilen, ein Paket, beide Instanzen. Mehr trägt die Aufnahme nicht; die Grenze „darüber hinaus Setzung“ steht überall im selben Satz bzw. Absatz an der Aussage (`SPEC-035`, `SPEC-036`, `SPEC-001`, KDoc `SimModel`/`BulkLoads`, Nutzer-Doku, Messprotokoll, Matrix). Nebenbefund zur Zählung: der Review spricht von „Nachrichten 17 bis 24“; die Datei führt 16 Nachrichten, gemeint sind offenbar die Dateizeilen 9 bis 24; keine Wirkung auf das Urteil.

## Mutationen (Schwerpunkt 4)

Je Lauf eine Wegwerf-Kopie (`git archive HEAD`), Dockerfile-Stage `test` mit `:test --tests "*SimulationBackendBulkTest" integratorTest --tests "*MassenladenEmbeddingTest" --continue`. Skript und Logs: `.../scratchpad/verifier-massenladen-2026-11-08/mut.sh`, `mut1.log` bis `mut3.log`, `mut-all.log`.

| Nr. | Mutation | Rote Tests | Erwartet? |
|---|---|---|---|
| 1 | `StatementPlanner.bulk`: wieder `table.onInsertBatch == null && table.onInsert == null` | `SimulationBackendBulkTest`: „INSERT BULK wird abgelehnt, wenn Tabelle, Spalte oder Callback fehlen“, „eine Tabelle nur mit onInsert lehnt INSERT BULK ab und laedt keine Zeile“; `MassenladenEmbeddingTest.eineTabelleNurMitOnInsertLehntInsertBulkAb` (11 bzw. 4 Tests, 2 bzw. 1 rot) | ja |
| 2 | `BulkLoads.call`: Rückfall auf `onInsert` je Zeile wieder eingebaut (Planer unverändert) | `SimulationBackendBulkTest`: „eine Tabelle nur mit onInsert lehnt INSERT BULK ab und laedt keine Zeile“ (1 rot). Der Einbettungstest bleibt grün, weil der Planer die Tabelle schon abweist; das ist erwartet und auch vom Review so gefunden | ja |
| 3 | `guarded { batch(rows) }` durch `guarded { rows.sumOf { batch(listOf(it)) } }` (Aufruf je Zeile statt einmal je Nachricht) | `MassenladenEmbeddingTest`: `eineNachricht0x07LaedtDieZeilenInEinemAufrufVonOnInsertBatch`, `eineNachrichtMitVerstossInDerMitteLaesstDenBestandUnveraendert` (die Zeile 1 steht dann vor dem Verstoß im Bestand); `SimulationBackendBulkTest`: „die Zeilen gehen in einem Aufruf an den Stapel-Callback und sind ueberfuehrt“ | ja |

Alle drei rot in den Klassen, die der Plan als Wächter nennt. Keine Mutation blieb grün, die grün bleiben sollte, ohne dass sie erklärt wäre (Nr. 2, Einbettungstest).

## Schwerpunkt 1: Wer trägt „Bestand des Testservers“? (Review F-5)

Der Wortlaut von DoD-Punkt 2: „Nachricht mit Verstoß in der Mitte lässt den Bestand des Testservers unverändert (Einbettungstest `integrator`, Modul `testserver`, nur öffentliche Fläche)“. Der Wortlaut ist in sich nicht erfüllbar, so wie er steht: Das Source-Set `integrator` übersetzt gegen die öffentliche Fläche des Hauptmoduls und kann `TableStore` des Moduls `testserver` nicht sehen (Modulgrenze, [ADR-0034](../plan/adr/0034-testserver-als-gradle-modul.md)); der Einbettungstest `MassenladenEmbeddingTest.eineNachrichtMitVerstossInDerMitteLaesstDenBestandUnveraendert` benutzt einen eigenen Fake (`CopyOnWriteArrayList`, der Test-Callback prüft selbst vor dem Übernehmen). Was die Dinge tragen:

| Träger | Was er belegt | Läuft in |
|---|---|---|
| `MassenladenEmbeddingTest` (Fake des Integrators) | der **Anteil der Bibliothek**: ein Aufruf mit allen drei Zeilen, bei Fehler DONE mit Fehler, Verbindung bleibt offen, `onInsert` wird nie gerufen; nicht die Fehlernummer 2627 (nur das Fehlerbit), die tragen `SimulationBackendBulkTest` und `ErrorMapperTest` | `make test`, `make clean-build` (grün) |
| `TableStoreTest > eine fehlerhafte Nachricht aendert den Bestand nicht` (nicht im Diff, Vorbestand) | der **Bestand des Testservers** (`TableStore.insertBatch`): sechs fehlerhafte Nachrichten (doppelt in der Nachricht, gegen den Bestand, falscher Typ, NULL im Schlüssel, fehlende Schlüsselspalte, falscher Typ in Name); der Bestand behält genau die Zeile davor. Aufruf direkt an `insertBatch`, nicht über den Draht | `make test` |
| `SqlBulkCopyTests.Schluesselverstoss_endet_mit_2627_ohne_Zeile_der_Nachricht_und_die_Verbindung_bleibt_nutzbar` (Vorbestand) | der **Bestand des Testservers über den Draht** mit dem Referenzclient: Zeile 20 vorhanden, Nachricht (21, 20): `SqlException` Nummer 2627, Verbindung offen, danach liest die Verbindung nur die Zeile 20; die Zeile 21 vor dem Verstoß ist nicht im Bestand | `make simulations-integration` / Stufe 3 von `make e2e` (in meinem Lauf bestanden, Log: „Schluesselverstoss: Number 2627, Class 14, State 1“) |
| `d-migrate` `data import`/`data transfer` gegen `make testserver-up` | derselbe Bestand mit dem Treiber von d-migrate (Abschnitt d-migrate) | von Hand, nicht im Gate |

**Entscheidung (Verifier):** die Zusage „Bestand des Testservers bleibt bei Verstoß in der Mitte unverändert“ ist **in der Sache getragen** (`TableStoreTest`, `SqlBulkCopyTests`, d-migrate), der Wortlaut „Einbettungstest … Modul `testserver`“ ist **nicht wörtlich** erfüllt, weil dieser Test den Bibliotheksanteil mit einem Fake trägt. Der Slice hat keinen neuen Test auf Ebene des Testservers hinzugefügt; die zwei tragenden Tests sind Vorbestand und von keiner Mutation dieses Laufs gefärbt (die Frage, ob eine Mutation in `TableStore.insertBatch` sie färbt, habe ich nicht gestellt; das ist Gegenstand von `slice-adr-massenladen-atomar-an-messung`/`slice-schreibpfad-bulk-load`, nicht dieses Slice). **Bedingung für den Planner (B1):** die Closure-Notiz benennt die Zuordnung der drei Träger ausdrücklich, statt den Wortlaut der DoD stillschweigend gelten zu lassen; ob der Planner den Wortlaut der DoD berichtigt (nicht durch mich) oder in der Notiz „Einbettungstest = Anteil der Bibliothek, Bestand des Testservers = `TableStoreTest` und `SqlBulkCopyTests`“ festhält, ist seine Wahl. Kein Test ist nachzuliefern, sofern er das annimmt; wollte er den Wortlaut buchstäblich, wäre ein Test im Modul `testserver` über `TdsServer.start` mit dem echten `TableStore` und Rohbytes nötig (ein neuer Slice-Punkt, nicht Teil dieser Verifikation).

## Schwerpunkt 3: Referenzclient und d-migrate

* `make e2e`: Exit 0, 8 Stufen, 447 s. Stufe 3 enthält `SqlBulkCopyTests` (drei Tests: Laden mit Zählen, mehrere Nachrichten je Batch, Schlüsselverstoß), alle bestanden; Stufen 4 bis 7 (Kompatibilitätsstufen mit Bulk, Stufe 7 `Synchronitaet`) grün, kein Last-Flake. Der Implementer fuhr `make simulations-integration` für sich (61 s); ich habe es nur als Stufe 3 des E2E-Laufs gefahren (53 s), nicht doppelt.
* d-migrate 1.12.0 (Digest `sha256:308f27753036c9cc423f716b9fc4c9e68016543a8003ecd2f8282e05a2ee6491`) gegen `make testserver-up` mit eigenem Namen und Port (`TESTSERVER_NAME=tdswire-verif-mass`, `TESTSERVER_IMAGE=tdswire-testserver:verif-mass`, `TESTSERVER_PORT=14437`), Tabelle `kunden`, `encrypt=false`. Log: `.../verifier-massenladen-2026-11-08/dmimport.log` und `dmtransfer.log`.

| Schritt | Ergebnis |
|---|---|
| `data import` (Id 1 und 2) | Exit 0, „2 rows processed, 2 inserted“ |
| `data export` | Exit 0, 2 Zeilen |
| `data import` derselbe Satz noch einmal | `Error: Import failed: Violation of PRIMARY KEY constraint. Cannot insert duplicate key.`, **Exit 5**; `data export` danach byte-gleich (`cmp`) |
| `data import` mit Ids 7, 1, 8 (Verstoß in der Mitte, Zeile 7 davor) | Exit 5; Export danach byte-gleich zum ersten: Zeile 7 und 8 nicht im Bestand |
| `data transfer` (Standardpfad, `INSERT BULK`) von Container 14437 nach 14438 | Exit 0, „Transferred: kunden“; Ziel enthält die 2 Zeilen |
| `data transfer` ein zweites Mal | `Transfer error: Violation of PRIMARY KEY constraint…`, **Exit 5**; Ziel byte-gleich |

Container `tdswire-verif-mass` und `tdswire-verif-mass2` und das Image `tdswire-testserver:verif-mass` sind entfernt; die vorhandenen d-migrate-Images habe ich nicht angelegt und nicht entfernt. Dass der Treiber mssql-jdbc mit dem echten Server dasselbe tut, ist weiter nicht gemessen (Re-Evaluierungs-Trigger 2 von [ADR-0044](../plan/adr/0044-massenladen-nachricht-ist-atomar-wie-am-echten-server.md), `slice-abnahme-d-migrate`).

## Schwerpunkt 5: Plan-Tabelle §3 gegen Diff, zweite Fundstellen

* **Plan-Tabelle §3 gegen den Diff `a830d6d1..HEAD` (20 Dateien):** jede Datei des Diffs steht in der ursprünglichen oder in der Ergänzungs-Tabelle (`MassenladenEmbeddingTest`, Matrix, Messprotokoll `bulk-load-2026-11-07`, `spec-abdeckung`, Roadmap, Welle, die drei Verweisdateien; die Verweisdatei `open/slice-sp-execute-unbekanntes-handle-bauen.md` ist wegen `main` Teil des Merge-Konflikts, siehe unten). F-3 ist behoben: die „prüfen“-Zeilen tragen jetzt den Vermerk „geprüft, nichts zu ändern“, Schritt 10 von `TDS-FA-11.a` steht im Kopf. Nicht im Diff, aber in der Tabelle genannt: `StatementPlannerTest`, `TableStoreTest`, `TableStore`, `KompatibilitaetsServer` (jeweils mit Vermerk). Eine Abweichung: der Kopf nennt `docs/messungen/referenzabgleich.md` als „update“ (steht im Diff: eine Zeile), ok.
* **Zweite Fundstellen (Grep über `spec/`, `docs/user`, `docs/messungen`, Matrix, `src`, `testserver`, `tools`, `harness`, `README.md`):** „Teilbestand“ steht nur noch in Sätzen der neuen Fassung (Zusage gebrochen erzeugt Teilbestand) und in Testkommentaren als Wächter-Beschreibung; „`onInsert` je Zeile“ nur in Testkommentaren als Wächter; „mindestens ein Callback“, „beide Callbacks“, „`onInsertBatch` oder `onInsert`“: keine Treffer. **Rest:** V-2.
* **Haken, `harness/README.md`:** unberührt (`git diff --stat a830d6d1..HEAD -- harness AGENTS.md` leer), Zellenlänge nicht betroffen.
* **`make flaeche`:** Exit 0; keine neue Fläche; Änderungsprotokoll von `SPEC-059` trägt „Inkompatibel … Integrator, der das Massenladen nur mit `onInsert` bediente: `INSERT BULK` ergibt `TDS_UNSUPPORTED_SQL`“; die Nutzer-Doku benennt die Abweisung in Absatz und Fehlertabelle.
* **Plan §3 Zusage „Fehler des Callbacks bleibt 2627 mit INFO 3621“:** `SimulationBackendBulkTest` und `BulkDispatchTest` (aus dem `clean-build`-Log bestanden) tragen die Antwortform; nicht neu am Diff.

## Prüfung der Nachbesserung gegen die Findings des Reviews

| Finding | Stand in `128378b8` | Urteil |
|---|---|---|
| F-1 (MEDIUM, M27 nennt gedachte Wirkung) | Zeile M27: nur „ein Fehler des Callbacks ergibt TDS_SIM_KEY_VIOLATION …“ rot, der Ersatztest „bleibt grün“ ausdrücklich genannt; stimmt mit Mutation 7 des Reviews überein | behoben |
| F-2 (MEDIUM, Nutzer-Doku Fehlertabelle) | eigene Zeile für `INSERT BULK`, Zeile `INSERT` als SQL-Text auf `onInsert` bezogen, Aufzählungspunkt nennt `onInsertBatch`; Ablehnungstext „Einfügen für diese Tabelle nicht verfügbar“ nennt die Ursache nicht und die Doku sagt das | behoben |
| F-3 (MEDIUM, Plan-Tabelle) | siehe oben | behoben |
| F-4 (INFO, Ablehnungstext) | unverändert, in der Doku benannt | bewusst offen (INFO), kein Befund der Verifikation |
| F-5 (INFO, DoD-Wortlaut) | unbeantwortet im Slice | an den Planner, B1 |
| F-6 (INFO, `done/` Prosa) | Satz „liegt in `in-progress/`“ gesetzt | behoben (der Slice liegt nach dem Merge ggf. anders, siehe B2) |
| F-7 (INFO, Grenze nur im internen KDoc) | KDoc von `SimTable.onInsertBatch` trägt jetzt Grenze und Sammelcode | behoben |

Der Diff der Nachbesserung enthält keine Programmlogik, nur den KDoc-Satz in `SimModel.kt`; das Fehlen eines zweiten Reviews ändert deshalb am Code nichts. Die Doku-Änderungen habe ich oben selbst gelesen.

## Merge-Probe gegen `main` (`142bdb20`, Merge-Basis `a830d6d1`)

`git merge-tree --write-tree HEAD main` ergibt Konflikte in fünf Dateien, alle Dokumentation, keine im Code:

| Datei | Art | Lösung |
|---|---|---|
| `spec/spezifikation.md` | Inhaltskonflikt **nur am Ende des Änderungsprotokolls** (beide Zweige hängen eine Zeile 2026-11-08 an) | beide Zeilen behalten (Reihenfolge: die von `main` zuerst, die dieses Slice danach oder umgekehrt, gleichrangig). **Die Fehlertabelle (§4) ist konfliktfrei:** die Zeile `TDS_SIM_KEY_VIOLATION` mit dem Sammelcode und die Streichung von `TDS_UNKNOWN_HANDLE` durch `main` mergen automatisch (am Merge-Baum geprüft). Die SPEC-059-Nachweis-Zeile und die Fehlertabelle sind unberührt |
| `docs/plan/planning/in-progress/roadmap.md` | zwei Konflikte: Wellenzeile „in `in-progress/`“ (dieser Zweig: Slice drin; `main`: „keiner; zuletzt geschlossen: slice-sp-execute-…“) und die Drift-Log-Zeile 2026-11-08 (Links `open/` gegen `in-progress/` bzw. `done/`) | Zeile von `main` übernehmen und den Slice-Eintrag dieses Zweigs ergänzen (`in-progress/: slice-massenladen-atomar-bauen`, „zuletzt geschlossen: slice-sp-execute-…“); Links der Drift-Zeile je Slice auf den dann geltenden Ort setzen |
| `docs/plan/planning/done/slice-massenladen-atomar-bauen.md` (bei `main` noch unter `open/`) | Inhaltskonflikt im Absatz „Start“ (Link `../open/slice-sp-execute-…` gegen `../done/slice-sp-execute-…`) | Fassung von `main` für den Link der `sp-execute`-Datei (`../done/…`) bei sonst unveränderter Nachbesserung |
| `docs/plan/planning/welle-testserver-manuell.md` | Inhaltskonflikt (Zeilen zu beiden Slices) | Zeile für `sp-execute` aus `main` (`done/`), Zeile für `massenladen` aus diesem Zweig (`in-progress/`) |
| `docs/plan/planning/open/slice-sp-execute-unbekanntes-handle-bauen.md` | geändert/gelöscht: in `main` nach `done/` verschoben, in diesem Zweig geändert (Link auf `massenladen` von `open/` nach `in-progress/`) | Datei in `done/` von `main` nehmen und deren **vier** Links `../open/slice-massenladen-atomar-bauen.md` auf `../in-progress/…` (bzw. `../done/…`, wenn der Planner vor dem Merge schließt) setzen |

Folgearbeit nach dem Merge, die der Merge allein nicht erledigt: in `main` stehen weitere Links `open/slice-massenladen-atomar-bauen` (`done/slice-adr-massenladen-atomar-an-messung.md`, `done/slice-ddl-abschluss-und-transaktionszustand-angleichen.md`, `done/slice-sp-execute-unbekanntes-handle-bauen.md`); dieser Zweig hat die ersten beiden bereits nachgezogen, die dritte ist der Konflikt oben. **`make docs-check` ist nach dem Merge zu fahren** (er ist der Wächter dieser Links) und `make gates`.

## Weitere Befunde der Verifikation

| ID | Kategorie | Befund | Beleg | Folge |
|---|---|---|---|---|
| V-1 | Wortlaut / Plan | DoD-Zeile „Bestand des Testservers (Einbettungstest `integrator`, Modul `testserver`, nur öffentliche Fläche)“ ist als Klammer nicht wörtlich erfüllbar; in der Sache getragen von `TableStoreTest`, `SqlBulkCopyTests` und d-migrate (Abschnitt Schwerpunkt 1) | Modulgrenze, Test-Quellen | B1 |
| V-2 | INFO, zweite Fundstelle | `spec/spezifikation.md`, Tabelle `SPEC-059`, Zeile „Zusage“: „der Parameter `SimTable.onInsertBatch` (Rahmen des Massenladens, am Ende von `SimTable`; **dieser Stand ruft ihn nicht**)“. Die Aussage ist seit `slice-schreibpfad-bulk-load` falsch (die Bibliothek ruft ihn einmal je Nachricht) und steht nicht im Diff (identisch in `a830d6d1`). Der Slice ändert dieselbe Tabelle (Nachweis-Zeile) und sagt in `SPEC-035` das Gegenteil; sein Grep nannte die Wendung nicht. `make flaeche` vergleicht Typnamen, kein Satz hält das | `git show a830d6d1:spec/spezifikation.md`, Grep im Arbeitsbaum | B2: vor oder nach dem Merge als eigene kleine Korrektur in `spec/` (Spec-Änderung, kein Code) oder als Eintrag im Register; kein Grund, die Closure zu halten |
| V-3 | INFO | `TableStore.kt` (KDoc) zitiert für das Massenladen „[ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 5“; die Zusage „ganz oder gar nicht“ steht jetzt in [ADR-0044](../plan/adr/0044-massenladen-nachricht-ist-atomar-wie-am-echten-server.md), die Aussage des KDocs bleibt wahr (der Bestand prüft vor dem ersten Einfügen) | `testserver/kotlin/…/TableStore.kt` Zeile 24 | keine Folge; nur Hinweis, falls das KDoc ohnehin angefasst wird |
| V-4 | INFO, Mess-Skala | Die Nummerierung „Nachrichten 17 bis 24“ des Review-Reports passt nicht zur Datei (16 Nachrichten, gemeint Zeilen 9 bis 24) | Aufnahme | keine |
| V-5 | Eigene Panne | Ich habe im Scratchpad den Unterordner `verif/` benutzt, der schon Dateien einer anderen Rolle enthielt, und dort gleichnamige Dateien überschrieben (`clean-build.log`, `mut1.log` bis `mut3.log`, `mut.sh`, `dec.py`, `dec2.py`, `tree`, `mut1/` bis `mut3/` samt Inhalt gelöscht und neu angelegt). Reproduzierbar blieb alles im Repo; die Belege der Vorgänger stehen in deren Berichten (`docs/reviews/`), nicht in diesen Dateien. Ab dem E2E-Lauf liegen meine Dateien im eigenen Ordner `verifier-massenladen-2026-11-08/` | Scratchpad | zur Kenntnis; wer die alten Scratch-Dateien brauchte, muss sie neu erzeugen |

## Bedingungen für die Planner-Closure

* **B1 (DoD-Wortlaut):** in der Closure-Notiz (§7) die Zuordnung des Wortes „Bestand des Testservers“ festhalten: Einbettungstest = Anteil der Bibliothek (Fake des Integrators, ein Aufruf mit allen Zeilen, Fehlerantwort, Verbindung offen); Bestand des Testservers = `TableStoreTest > eine fehlerhafte Nachricht aendert den Bestand nicht` und `SqlBulkCopyTests.Schluesselverstoss_…` (Stufe 3 von `make e2e`, hier bestanden), von Hand `d-migrate` (Exit 5, Export byte-gleich); oder den Wortlaut der DoD berichtigen. Kein Test ist dafür nachzuliefern.
* **B2 (Merge und zweite Fundstelle):** `main` einmergen (fünf Dokumentationskonflikte, Auflösung im Abschnitt Merge-Probe), danach `make docs-check` und `make gates`; V-2 (SPEC-059 „dieser Stand ruft ihn nicht“) vor der Closure berichtigen oder im Register führen.
* **B3 (Closure-Notiz und Risiken, Planner-Handlungen):** §7 füllen mit dem Steering-Loop-Lerneintrag und der Zuordnung *Messung | Festlegung* aus §5 (gemessen: M-12, ein Fall; Setzung: Atomarität darüber hinaus; gemessen: 2627 beim Schlüsselverstoß; Setzung: Sammelcode für andere Ursachen). Ausgänge der fünf Risiken aus §6: (1) Atomarität nur Setzung: **weiter offen** → Register `folge-adr-zu-adr-0033-massenladen-verstoss-und-teilbestand`, Trigger 1; (2) Bruch für Integratoren mit nur `onInsert`: **eingetreten** (akzeptiertes Negativ), in `SPEC-059`-Änderungsprotokoll und Nutzer-Doku benannt; (3) Zusage nicht erzwingbar: **weiter offen** (akzeptiertes Negativ, KDoc/`SPEC-035`); (4) zweite Fundstelle: **eingetreten in einem Rest** (V-2, vor der Closure beheben), sonst entfallen; (5) gemeinsame Dateien mit `slice-ddl-…`: **entfallen** (Start nach dessen `done/`, kein Konflikt im Code; Konflikte nur in Dokumentation wegen `sp-execute`). Die Haken der drei Liefer-Punkte setzt erst der Planner danach; die Finding-Klassen des Reviews („Mutationsbeschreibung nennt die gedachte statt der gemessenen Wirkung“, „Nutzer-Doku nicht komplett gegen den Slice gelesen“, „Plan-Tabelle ohne berührte Datei“, „Test deckt Fake und Bibliotheksanteil, DoD nennt den Bestand“, „Verweisziel nachgezogen, Prosa nicht“, „Grenze nur im internen KDoc“) gehören in die Closure; keine davon hat die 3×-Schwelle in diesem Lauf allein erreicht.

Keine Bedingung am Code. Der Befund „Mutation 2 färbt den Einbettungstest nicht“ ist ein erklärtes Verhalten, kein Mangel (der Planer weist vorher ab).
