# Review-Report: slice-massenladen-atomar-bauen — 2026-11-08

**Review-Art:** Code (gegen Plan, ADR und Hard Rules; nicht gegen die DoD, das ist die Frage des Verifiers).

**Gegenstand:** Diff `a830d6d1..9e144097` im Zweig `worktree-agent-ad2fa7ce14f75b1b2` (Commits `07846b16` Code, Tests und erzeugte Abdeckungsmatrix, `9e144097` Spezifikation und Doku, vier reine `slice-mv`; 19 Dateien, +172/−86). Merge-Probe: `main` steht noch auf `a830d6d1` (Merge-Basis), `git merge-tree --write-tree` ohne Konflikt.

**Skill:** `.harness/skills/reviewer.md` @ `9e144097` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-08

**Eingangs-Kontext** (die Verträge, gegen die geprüft wurde):

- Slice `slice-massenladen-atomar-bauen` (Plan §3, DoD, Rückführungen, §6 Risiken)
- [ADR-0044](../plan/adr/0044-massenladen-nachricht-ist-atomar-wie-am-echten-server.md) (Accepted, Option A, Sammelcode 2627 als Grenze), [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) (Nr. 5 (iii), Z3, Nr. 6, Nr. 7 teilweise ersetzt), [ADR-0021](../plan/adr/0021-oeffentlicher-integrations-einstieg.md)
- [SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends), [SPEC-036](../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig), [SPEC-001](../../spec/spezifikation.md#spec-001--zustandsautomat-einer-verbindung), [SPEC-059](../../spec/spezifikation.md#einstieg-des-integrators-vertrag-zu-spec-059)
- [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität)
- `AGENTS.md` §3.1 bis §3.8; Grundsatz des Nutzers „Spec ist Zielbild“ (der echte SQL Server ist die Referenz)

## Selbst gefahren

| Lauf | Ergebnis |
|---|---|
| `make gates FORCE=1` | Exit 0 nach rund 1 bis 2 min; Docker-Schichten fast alle aus dem Cache (derselbe Eingang wie der Lauf des Implementers, die Schichten sind inhaltsadressiert); `d-check` 1308 Dateien, 0 Befunde; `gate-zuordnung` 16 Gates |
| `make flaeche` | Exit 0 (Selbsttest und Prüfung) |
| `make docs-check` | 1308 Dateien, 0 Befunde (vor Anlage dieses Reports) |
| Mutationen (sieben, in einer Wegwerf-Kopie des Arbeitsbaums, Stage `test` mit nur `SimulationBackendBulkTest` und `MassenladenEmbeddingTest`) | alle sieben rot, siehe Abschnitt Mutationen |
| `make clean-build`, `make e2e`, `make matrix-zitate` | nicht gefahren (Sensor-Zuschnitt der Änderungsart `src/`) |
| Rohbytes `referenztest-dotnet/Mitschnitt/messung/bulk-merge-katalog/{2022,2025}/bulk-verstoss-schluessel.messung` | selbst dekodiert, siehe Abschnitt Echtheit |

## Echtheit und Belegtiefe (Schwerpunkt a)

Die Aufnahme `bulk-verstoss-schluessel` (2022 und 2025 byte-gleich in den Nachrichten 17 bis 24) habe ich selbst gelesen. Nachricht 17 (Pakettyp `0x07`, Status `0x01`, 108 Byte, **ein** Paket) trägt COLMETADATA (`Id` BIGINT fest `0x7f`, `Text` NVARCHAR(10)) und drei ROW-Token mit `Id` 1, 2, 3. Die Antwort (Nachricht 18) beginnt mit ERROR 2627 (Class 14, State 1, „Violation of PRIMARY KEY constraint 'pk_bulk_schluessel' … The duplicate key value is (2)“), danach INFO 3621 und DONE mit Status `0x0002` (ERROR), Befehl `0x00f0` und ohne Zähler. Nach dem ATTENTION (Nachricht 19/20) liefert `SELECT COUNT_BIG(*) FROM bulk_schluessel` (Nachricht 21/22) den Wert 1 (ROW `d1` mit 8 Byte Wert `01 00 00 00 00 00 00 00`): die Zeile 1 vor dem Verstoß ist nicht im Bestand, die vorhandene Zeile mit `Id` 2 ist es. Das trägt genau den Fall des Slice: Schlüsselverstoß, drei Zeilen, ein Paket, Verstoß in der Mitte, beide Instanzen. Mehr trägt die Aufnahme nicht, und das Schreiben des Slice sagt auch nicht mehr:

- Die Grenze „gemessen ist ein Fall, darüber hinaus Setzung“ steht im selben Satz bzw. Absatz an: `SPEC-035` (Absatz `onInsertBatch`), `SPEC-036` Form B (Absatz zur Antwort bei Fehler, Tabellenzeile `INSERT BULK`), Nutzer-Doku `einbettung-und-betrieb.md` (Absatz zum Stapel-Callback), Zeile M-12/M-11 des Messprotokolls, Matrix-Zeile `BULK_LOAD`, KDoc von `BulkLoads`.
- Die Zeile „Fehlernummer 2627 für andere Ursachen“ ist überall als Sammelcode (Setzung) gekennzeichnet; die Zeile `TDS_SIM_KEY_VIOLATION` der Fehlertabelle trägt es.
- Die Spezifikation nennt in den hinzugefügten Zeilen keine ADR-Kennung (Grep über den Diff: kein Treffer).

## Mutationen (Schwerpunkt e)

Wegwerf-Kopie je Lauf, Aufruf der Stage `test` mit `:test --tests …SimulationBackendBulkTest` und `integratorTest --tests …MassenladenEmbeddingTest` (`--continue`), Arbeitsbaum unverändert.

| Nr. | Mutation | Rote Tests |
|---|---|---|
| 1 | `StatementPlanner`: Prüfung wieder `onInsertBatch == null && onInsert == null` | `SimulationBackendBulkTest` („INSERT BULK wird abgelehnt, wenn Tabelle, Spalte oder Callback fehlen“, „eine Tabelle nur mit onInsert lehnt INSERT BULK ab …“), `MassenladenEmbeddingTest.eineTabelleNurMitOnInsertLehntInsertBulkAb` |
| 2 | `BulkLoads.call`: Rückfall auf `onInsert` je Zeile wieder eingebaut (Planer unverändert) | `SimulationBackendBulkTest` („eine Tabelle nur mit onInsert lehnt INSERT BULK ab …“) |
| 3 | beides (1 und 2) | beide Einbettungs- und beide Bulk-Tests wie in 1 |
| 4 | `batch(rows)` ersetzt durch `rows.sumOf { batch(listOf(it)) }` (Aufruf je Zeile) | `MassenladenEmbeddingTest.eineNachricht0x07Laedt…` und `eineNachrichtMitVerstossInDerMitteLaesstDenBestandUnveraendert`, `SimulationBackendBulkTest` („die Zeilen gehen in einem Aufruf …“) |
| 5 | eigene: `batch(rows); batch(rows)` (Callback zweimal) | `MassenladenEmbeddingTest.eineNachricht0x07Laedt…`, `SimulationBackendBulkTest` („die Zeilen gehen in einem Aufruf …“, „Int16 und UInt8 nehmen Zahlen …“); der Fehlerfall-Test bleibt grün (der erste Aufruf wirft) |
| 6 | eigene: Fehler erst nach dem Aufruf des Callbacks gemeldet (`batch(rows); error(…)`) | `MassenladenEmbeddingTest.eineNachricht0x07Laedt…`, `SimulationBackendBulkTest` („ein Fehler des Callbacks …“, „die Zeilen gehen in einem Aufruf …“, „Int16 und UInt8 …“) |
| 7 | eigene: 2627 durch 50004 (`SimCodes.BACKEND_ERROR`) ersetzt | **nur** `SimulationBackendBulkTest` („ein Fehler des Callbacks ergibt TDS_SIM_KEY_VIOLATION …“); `eine Tabelle nur mit onInsert lehnt INSERT BULK ab …` bleibt grün (siehe F-1) |

Der Zweig „kein `onInsertBatch`“ in `BulkLoads.call` (nicht erreichbar über den Planer) ist durch Nr. 2 und den Direkt-Aufruf von `load` im Bulk-Test getragen. Der Einbettungstest „Verstoß in der Mitte“ ist nicht nur der Fake: Mutation 4 färbt ihn rot, weil Zeile 1 dann vor dem Verstoß im Bestand steht.

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| F-1 | MEDIUM | Die Mutationstabelle des Messprotokolls führt bei M27 („ein Fehler des Callbacks ist 2627“, mutiert zu `TDS_BACKEND_ERROR`) als rote Tests „ein Fehler des Callbacks ergibt TDS_SIM_KEY_VIOLATION …“ **und** „eine Tabelle nur mit onInsert lehnt INSERT BULK ab …“. Der Diff hat den zweiten Namen anstelle des ersetzten Tests eingesetzt; Mutation 7 färbt aber nur den ersten Test rot, der Ersatztest prüft keine Fehlernummer. Die Spalte nennt eine Wirkung, die nicht gefahren wurde. | `AGENTS.md` §3.8; Reviewer-Skill (Mutationsbeschreibung nennt die gedachte statt der gemessenen Wirkung) | `docs/messungen/bulk-load-2026-11-07.md` · „M27 | ein Fehler des Callbacks ist 2627“ | ja – Mutation 7 oben einmal fahren und die roten Testnamen vergleichen | Mutationsbeschreibung nennt die gedachte statt der gemessenen Wirkung |
| F-2 | MEDIUM | Die Tabelle „Typische Fehler“ der Nutzer-Doku führt `TDS_UNSUPPORTED_SQL` bei `INSERT` mit der Ursache „die Tabelle hat keinen `onInsert`“ und der Abhilfe „Callback setzen“; eine Zeile für `INSERT BULK` ohne `onInsertBatch` fehlt (der Plan nennt sie ausdrücklich). Ein Integrator mit nur `onInsert`, der den Fehler von `data import` nachschlägt, findet eine Ursache, die auf ihn nicht zutrifft; der Satz „Ohne Callback endet Einfügen, Ändern und Löschen mit `TDS_UNSUPPORTED_SQL`“ weist ebenfalls nicht auf den Stapel-Callback hin. | `AGENTS.md` §5 Regel 5; Reviewer-Skill (Nutzer-Doku nicht komplett gegen den Slice gelesen); Slice §2 Liefer-Punkt 3 | `docs/user/einbettung-und-betrieb.md` · „| `TDS_UNSUPPORTED_SQL` bei `INSERT` | die Tabelle hat keinen `onInsert` | Callback setzen |“ | nein – kein Gate liest die Tabelle | Nutzer-Doku nicht komplett gegen den Slice gelesen |
| F-3 | MEDIUM | §3 des Slice ist nicht fortgeschrieben (der Slice-Diff enthält nur nachgezogene Links): nicht in der Tabelle stehen die berührten `MassenladenEmbeddingTest.kt`, `docs/plan/kompatibilitaetsmatrix.md`, `docs/messungen/bulk-load-2026-11-07.md`, die erzeugte `docs/plan/spec-abdeckung.md`, `roadmap.md`, `welle-testserver-manuell.md`; die Zeilen für `StatementPlannerTest`, `TableStoreTest`, `KompatibilitaetsServer`, `TableStore` stehen als „update“/„prüfen“, ohne dass eine Datei sich ändert oder ein Vermerk die Prüfung belegt. Der Kopf „Berührte Spec-Stellen“ nennt `SPEC-035`, `SPEC-036`, `SPEC-001`, §4 und `SPEC-059`, nicht Schritt 10 von `TDS-FA-11.a`, den der Diff ebenfalls ändert. | Reviewer-Skill (Plan-Tabelle nennt die berührten Dateien nicht); Slice §3 | `docs/plan/planning/in-progress/slice-massenladen-atomar-bauen.md` · „Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.“ | nein | Plan-Tabelle ohne berührte Datei |
| F-4 | INFO | Die Abweisung einer Tabelle nur mit `onInsert` trägt die Meldung „Einfügen für diese Tabelle nicht verfügbar“; sie nennt weder `onInsertBatch` noch die Ursache. Der Zeitpunkt ist richtig (DONE mit Fehler schon auf die Anweisung `INSERT BULK`, belegt vom Einbettungstest), der Text ist unverändert gegenüber dem Stand davor. | Maintainability | `src/main/kotlin/io/github/pt9912/tdswire/simulation/StatementPlanner.kt` · „Einfügen für diese Tabelle nicht verfügbar“ | ja – `SimulationBackendBulkTest` | Ablehnungstext ohne Ursache |
| F-5 | INFO | Der Einbettungstest „Verstoß in der Mitte“ belegt die Zusage am Fake des Integrators (der Test-Callback prüft selbst vor dem Übernehmen) und den Anteil der Bibliothek (ein Aufruf mit allen Zeilen, Fehlerantwort, Verbindung offen); die Fehlernummer 2627 prüft er nicht (nur das Fehlerbit), sie ist von `SimulationBackendBulkTest` und `ErrorMapperTest` getragen. Der Wortlaut der DoD („Bestand des Testservers“) weicht davon ab: der Bestand des Testservers (`TableStore`) ist von `TableStoreTest` getragen, nicht vom Einbettungstest. Die Zuordnung ist Sache des Verifiers. | Maintainability | `src/integrator/kotlin/io/github/pt9912/tdswire/integrator/MassenladenEmbeddingTest.kt` · „eineNachrichtMitVerstossInDerMitteLaesstDenBestandUnveraendert“ | ja | Test deckt Fake und Bibliotheksanteil, DoD nennt den Bestand |
| F-6 | INFO | Der reine `slice-mv` hat in `done/slice-adr-massenladen-atomar-an-messung.md` den Link auf `in-progress/` gesetzt, der Satz davor sagt weiter „ist eine Datei in `open/`“ (Zeitdokument der Closure, ebenso im Geschwister-Slice zu `sp-execute`). | Maintainability | `docs/plan/planning/done/slice-adr-massenladen-atomar-an-messung.md` · „ist eine Datei in `open/`“ | nein | Verweisziel nachgezogen, Prosa nicht |
| F-7 | INFO | Die öffentliche KDoc von `SimTable.onInsertBatch` nennt die Zusage („gilt ganz oder gar nicht“), nicht die Grenze „gemessen ein Fall, darüber hinaus Setzung“ und nicht die Zuordnung zu 2627; beides steht im KDoc des internen `BulkLoads`, in `SPEC-035` und in der Nutzer-Doku. Als Zusage an den Integrator (kein Messbefund über den Server) ist das tragbar. | Maintainability | `src/main/kotlin/io/github/pt9912/tdswire/simulation/SimModel.kt` · „Zusage an den Integrator: der Aufruf gilt ganz oder gar“ | nein | Grenze nur im internen KDoc |

## Antworten auf die Schwerpunkte (soweit ohne Finding)

- **(b) `BulkLoads.call`:** ruft nur `onInsertBatch`, einmal je Nachricht (leere Nachricht: kein Aufruf, Zähler 0); eine Nachricht über mehrere Pakete ist nach dem Zusammensetzen eine Nachricht und ein Aufruf (aus dem Code der Aufrufkette gelesen, nicht mit einem Mehr-Paket-Test gefahren; die Messung dazu steht aus, Trigger 1 der ADR). Die Zusage gilt je Nachricht: `SPEC-035` („mehrere Nachrichten und mehrere Callbacks sind keine Transaktion, Nachrichten davor bleiben“), `SPEC-036` und Nutzer-Doku sagen es; ein Teilbestand zwischen Nachrichten ist so benannt, nicht verschwiegen. Jeder Fehler des Callbacks ergibt 2627 mit der festen Meldung; die Fälle Typ/NULL/Länge, die der Client nach M-11 vor dem Senden abweist, erreichen den Callback nicht (Überführung schlägt vorher mit `TDS_SIM_INVALID_VALUE` an), ein Fehler des Callbacks aus diesen Ursachen im Bestand des Testservers wird als Sammelcode gemeldet und ist als Setzung benannt. `StatementPlanner.bulk` prüft nur `onInsertBatch == null`; Ablehnung auf die Anweisung, vor dem Paket.
- **(c) Bruch für Integratoren mit nur `onInsert`:** `SPEC-059` Änderungsprotokoll trägt „Inkompatibel …“, die Nutzer-Doku nennt die Abweisung (mit F-2 als Lücke in der Fehlertabelle). `person` in `KompatibilitaetsServer` hat nur `onInsert` und ist kein Ziel eines Massenladens (Grep über `referenztest-dotnet/`, `tools/`, `testserver/`, `src/integrator`: `SqlBulkCopy` lädt nur `bulk_ziel`, das `onInsertBatch` hat; Tabellen aus `TableStore` setzen `onInsertBatch` immer). Kein E2E-Schritt bricht durch den Wegfall des Rückfalls.
- **(d) `make flaeche`:** Exit 0; keine neue Fläche, Zusage am Mitglied, `SPEC-059` Änderungsprotokoll vorhanden.
- **(g) `docs/plan/spec-abdeckung.md`:** der Diff ändert drei Zeilen (`SPEC-035` 121→122, `SPEC-036` 177→180, `SPEC-059` 45→47), die Spalte „Nicht bestanden“ dieser Zeilen bleibt 0; die Werte 5/4/6 bei `SPEC-002`, `SPEC-031`, `SPEC-045` sind gegenüber der Basis `a830d6d1` unverändert (nicht gelaufene E2E-Zeilen, Vorbestand). Die Matrix trägt keinen roten Test dieses Slice. Mein Gate-Lauf fand nach dem Regenerieren statt und ist grün.

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `src/main/kotlin/…/simulation/BulkLoads.kt`, `StatementPlanner.kt`, `SimModel.kt`, `SimulationBackend.kt` | geprüft, ohne Befund (außer F-4 und F-7 als INFO): Verhalten gegen [ADR-0044](../plan/adr/0044-massenladen-nachricht-ist-atomar-wie-am-echten-server.md) Entscheidung 1 bis 3; Zahl des Callbacks bleibt Änderungszähler, negative Zahl bleibt `TDS_BACKEND_ERROR`; kein `@Suppress`, keine neue Importbeziehung |
| `src/test`, `src/integrator` | geprüft, ohne Befund außer F-1/F-5: Wächter-Beschreibungen der Testkommentare nennen gemessene Wirkung der Mutationen 1 bis 4, `@spec`-Tags gesetzt |
| `testserver/kotlin` (`TableStore`, `IntegrationServer`, `KompatibilitaetsServer`) | geprüft, ohne Befund: unverändert; `TableStore` setzt `onInsertBatch` für jede Tabelle |
| `spec/spezifikation.md` (`SPEC-035`, `SPEC-036`, `SPEC-001`, Fehlertabelle, `SPEC-059` samt Nachweis-Zeile und Änderungsprotokoll, Schritt 10 von `TDS-FA-11.a`) | geprüft, ohne Befund: keine ADR-Kennung in den neuen Zeilen, Grenze „ein Fall“ im selben Absatz, kein Satz erweitert das Lastenheft ([TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate)); Grep nach „Teilbestand“, „je Zeile“, „beide Callbacks“, „mindestens ein Callback“ über `spec/`, `docs/`, `src`, `testserver`, `tools`: keine veraltete Fundstelle außer F-6 |
| `docs/user/*` (alle fünf Dateien; Grep auf „nicht gebaut“, „Zielzustand“, „vorläufig“, „bisher“, „noch nicht“, „soll“, „seit Slice“ und „Massenlad“/„INSERT BULK“/„onInsert“) | geprüft, einziger Befund F-2; `testserver-manuell.md` (Aufgabe 6, Eigenschaften) und `referenz-konfiguration.md` sagen den Stand des Testservers und die Grenze der Zuordnung zu 2627 korrekt; „nicht gebaut“ in `einbettung-und-betrieb.md` (ENVCHANGE Typ 8) ist Vorbestand und betrifft diesen Slice nicht |
| `docs/messungen/*`, `docs/plan/kompatibilitaetsmatrix.md` | geprüft, einziger Befund F-1; Zeile M-12/M-11 „angeglichen“ mit Grenze und Träger; Matrix-Zeile `BULK_LOAD` trennt gemessen (ein Fall) von Festlegung |
| ADR-Dateien | geprüft, ohne Befund: keine Accepted-ADR geändert (`AGENTS.md` §3.5) |
| Haken, `harness/README.md` | geprüft, ohne Befund: im Diff steht kein neuer `- [x]` am Slice (der einzige `- [x]`-Treffer ist die Linkkorrektur in der geschlossenen Datei `done/slice-adr-massenladen-atomar-an-messung.md`); `harness/README.md` unberührt, Zellenlänge nicht betroffen |
| Roadmap, Welle, Register-`state.md` | geprüft, ohne Befund: „Nichts in Arbeit.“ entfernt, Slice unter `in-progress/` eingetragen, Drift-Log nur Linkkorrektur; `Stand:` des Registers nennt Zustand und Anker, keine Chronik |
| Hard Rules `AGENTS.md` §3.1 (Docker-only), §3.2 (Suppression), §3.6 (Gates), §3.7 (Kommentar) | geprüft, ohne Befund: alle Läufe über `make`/`docker`; kein `@Suppress`; keine Änderung an `config/detekt/detekt.yml`; neue Kommentare tragen Zusage, Abgrenzung oder Grenze |

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 3 |
| LOW | 0 |
| INFO | 4 |

**Finding-Klassen dieses Laufs:** Mutationsbeschreibung nennt die gedachte statt der gemessenen Wirkung · Nutzer-Doku nicht komplett gegen den Slice gelesen · Plan-Tabelle ohne berührte Datei · Ablehnungstext ohne Ursache · Test deckt Fake und Bibliotheksanteil, DoD nennt den Bestand · Verweisziel nachgezogen, Prosa nicht · Grenze nur im internen KDoc

## Verdikt

**Merge-blockierend:** nein für den Code (Gates grün, sieben Mutationen rot, die Zusage und ihre Grenze stehen im selben Absatz wie die Aussage); die drei MEDIUM (F-1 bis F-3) betreffen Messprotokoll, Nutzer-Doku und Plan und gehören vor der Verifikation behoben. Kein HIGH, kein Rollen-Widerspruch, kein Konflikt-Pfad.

**Übergabe:** Findings gehen an den Implementer; INFO F-5 an den Verifier (Zuordnung der DoD-Zeile „Bestand des Testservers“); die Finding-Klassen gehen in die Slice-Closure §7. Dieser Report ersetzt keine Verifikation.
