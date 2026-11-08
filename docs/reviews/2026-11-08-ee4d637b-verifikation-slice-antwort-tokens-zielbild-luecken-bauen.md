# Verifikationsbericht: slice-antwort-tokens-zielbild-luecken-bauen (HEAD ee4d637b)

* Rolle: Verifier (Modul 11), frischer Kontext. Gegenstand: Slice `slice-antwort-tokens-zielbild-luecken-bauen` (`in-progress/`, Zweig `worktree-agent-afdefa690160b4ab3`, HEAD `ee4d637b`; `main` ist `9e97143c` und liegt in HEAD, Merge-Probe `git merge-tree --write-tree HEAD main` ohne Konflikt). Frage: Bauen wir es richtig, gegen Plan, DoD und Entscheidungen.
* Maßstab: DoD §2, Plan §3, [ADR-0042](../plan/adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) (Accepted, Option A), [ADR-0038](../plan/adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md), [ADR-0039](../plan/adr/0039-abschluss-der-antwort-auf-eine-rpc-nachricht-die-der-parser-abweist.md), `SPEC-022`, `SPEC-030`, `SPEC-031`, `SPEC-059`, `SPEC-060`, [TDS-FA-04](../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-FA-10](../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate).
* Grundsatz des Nutzers: „Spec ist Zielbild“, der echte SQL Server ist die Referenz.
* Verifiziert wird die Zusage, nicht der Diff; Belege sind eigene Läufe. Am Produktivcode nichts geändert (Mutationen liefen in einer Kopie unter dem Scratchpad; der Arbeitsbaum war danach unverändert, `git status` sauber). Haken habe ich nicht gesetzt.
* Der Review `2026-11-08-9b47b4bc-review-slice-antwort-tokens-zielbild-luecken-bauen.md` deckt `9b47b4bc` (0 HIGH, 5 MEDIUM, 2 LOW); die Nachbesserung `ee4d637b` hat keinen eigenen Review. Ersatzweise habe ich sie unten gegen F-1 bis F-7 einzeln geprüft.

## Eigene Läufe (Belege)

Host: Lastmittel 5 bis 127 durch fremde Läufe (kein ruhiger Host); Einzelmessungen. Alles im Vordergrund bzw. mit Warten auf das Log.

| Lauf | Ergebnis |
|---|---|
| `make clean-build` (genau einmal, auf dem End-Stand `ee4d637b`) | Exit 0, `BUILD SUCCESSFUL`; 1283 `PASSED`, 0 rote Testfälle; 34 min 17 s real, davon rund 24 min `TableStoreTest > gleichzeitiger Zugriff verliert nichts` (PASSED nach 1434 s, bekannter Flake-Kandidat, nicht im Diff, dem Wanduhr-Register zuzuordnen) |
| `make gates FORCE=1` | Exit 0 in 34 s. **Cache-Falle:** die Docker-Schritte der Stage `test` waren `CACHED` (gleiche Quellen wie bei Implementer und Reviewer); der Lauf belegt die Verdrahtung der Gates (baseline-verify, d-check 1287 Dateien 0 Befunde, gate-zuordnung 33 Fälle, vier Stempel-Selbsttests), nicht die Frische der Tests. Den Frischbeleg tragen `make clean-build` und `make matrix-zitate` (frisch gebaute Stage `test`). Hinweis: `stempel-gruppe: tests gruen, Stempel nicht geschrieben` wegen ignoriertem `testserver/build/…` im Arbeitsbaum, ohne Wirkung auf das Urteil |
| `make flaeche` | Exit 0 (Selbsttest und Prüfung) |
| `make docs-check` | Exit 0, 1287 Dateien, 0 Befunde (vor Anlage dieses Berichts) |
| `make matrix-zitate` | Exit 0, 1283 bestandene Testfälle im Bericht, 338 Zitate, 0 ohne PASSED, 0 Fremdformen. Die neuen Tests stehen in der Matrix (`ResponseStatementKindTest` 1 Zeile, `ReturnValueFlagsTest`, `StatementKindEmbeddingTest` mit drei Zitaten, `UnprepareDispatchTest`, `QueryTimeoutTest`) |
| `make e2e` | Exit 0, 8 Stufen grün, 412 s (Stufe 7 `Synchronitaet` 37 s, **kein Flake**, keine Wiederholung nötig; Stufe 8 14 s) |
| Messdateien selbst gelesen | siehe Abschnitt Draht |
| Mutationen (5 gefahren, siehe unten) | alle 5 rot, in den erwarteten Testklassen; Basislauf der fünf Klassen grün (45 Testfälle) |
| d-migrate | nicht gefahren (der Slice verlangt es nicht) |

Befehle, Logs und Skripte liegen im Scratchpad `/tmp/claude-1000/-Development-tdswire/3768ddc4-cff6-4152-b0be-815e9ed5f5cb/scratchpad/ver-antwort/` (nicht im Repo). Hinweis: Das Verzeichnis `…/scratchpad/ver/` gehörte schon früheren Läufen; ich habe dort nur `clean-build.log`, `cb.t0`, `cb.t1` und `start` geschrieben, die Kopie des Logs liegt in `ver-antwort/`.

## Gesamturteil

**Closure-fähig: ja, unter den Bedingungen B1 bis B4 unten (alle in Planner-Hand, keine am Code).** Der Kern trägt und ist in eigenen Läufen belegt: Flag-Feld, `curCmd` nach Art, `0x00c1` nach Fristablauf im Batch, die gestreamte Freigabe-Antwort und `OpenResultTracker` stimmen mit den Messdateien überein (Abschnitt Draht), `make clean-build` ist frisch grün, die fünf gefahrenen Mutationen sind rot. Die Nachbesserung `ee4d637b` erledigt F-1 bis F-7 des Reviews (Abschnitt Nachbesserung). Offen ist ein Wortlaut-Punkt der DoD (Liefer-Punkt 1: zehn Typen, „je Typ ein Test“), kein Codefehler.

## DoD-Punkte

Haken: keiner gesetzt (`grep -c '\[x\]'` = 0 im Slice), die Regel `BEO-TDS/haken-vor-verifikation-gesetzt` ist eingehalten. Ich setze keine.

| # | DoD-Punkt | Urteil | Beleg |
|---|---|---|---|
| 1 | Flag-Feld der Ausgabewerte | **erfüllt in der Sache, Wortlaut nicht erfüllbar (B1)** | `TokenWriters.RETURN_VALUE_FLAGS = 0x0000` ist eine Konstante, unabhängig von Typ und Nullbarkeit; Mutation m5 (Flag aus `ColumnDef.nullable`) macht beide Tests in `ReturnValueFlagsTest` rot. `ReturnValueFlagsTest` belegt neun der zehn gemessenen Typen über `SqlType` am Sequenzierer (`int`, `smallint` über `Int16`, `bigint`, `bit`, `decimal`, `nvarchar`, `date`, `time`, `datetime2`, je Wert und NULL), dazu `tinyint` über `UInt8` ausdrücklich als „nicht gemessen“ gekennzeichnet (M-24 nennt es nicht), und `INTN` 2 und 8 nullbar am Token. `float` hat weder `SqlType` noch Tokentyp: nicht gebaut, in `SPEC-030`, KDoc und Register als Grenze benannt. „ohne Zuweisung“ hat keinen eigenen Fall (der Server schreibt es wie NULL, das Flag ist eine Konstante). `SPEC-030` ohne die bekannte Abweichung: bestätigt |
| 2 | Anweisungsart am Port und `curCmd` | **erfüllt** | `StatementKind` (`UNKNOWN`, `INSERT`, `DELETE`, `UPDATE`) und `UpdateCount(affectedRows, kind = UNKNOWN)` mit `@JvmOverloads`, öffentlich (`make flaeche` grün; `StatementKindEmbeddingTest`: Konstanten, Standardwert, Java-Konstruktor mit einem Argument, eigenes Backend mit Art bekommt die Antwort des Simulations-Backends, ein altes Backend ohne Art bleibt bei Kennung 0 mit `COUNT`). Abbildung nur in `DoneCommand.forKind`; Massenladen bleibt `0x00f0` (Test `In der Form des Massenladens bleibt die Kennung 0xf0`); kein Text-Klassifizierer im Protokoll (Entscheidung 3, im Diff keiner); `0x00c1` nach Fristablauf nur im Batch bei offenem Ergebnis (`OpenResultTracker`, `QueryTimeoutTest` zwei Fälle), in `SPEC-031` als aus dem Fehler im Ergebnis übertragen gekennzeichnet; `SPEC-059` nennt `StatementKind` und führt den Eintrag im Änderungsprotokoll (2026-11-08, mit `copy`, `componentN`, `equals`), `SPEC-031` die Grenze „die Art ist eine Zusage des Backends“. Tests je Anweisungsart: Batch und RPC, drei Anweisungen im RPC, ohne Treffer, Simulations-Backend (`SimulationBackendChangeTest`) |
| 3 | `sp_unprepare` | **erfüllt** | Erfolg: `RETURNSTATUS 0`, `DONEPROC` Status 0, Befehl `0x00e0`, kein `DONE`; mehrere Anweisungen mit `MORE` an jedem DONEPROC außer dem letzten; unbekannte Nummer: `ERROR`, `RETURNSTATUS 8179`, `DONEPROC` mit `ERROR`. Handvektoren (`HELD_LAST`, `HELD_MORE`, `UNKNOWN_LAST`) gleich den Messbytes (Abschnitt Draht). Belegtest gegen die Ablage: `MessungBulkBelegTests` prüft die Tokenfolge der Ablage (`ERROR RETURNSTATUS DONEPROC`, `RETURNSTATUS DONEPROC RETURNSTATUS DONEPROC`); er liest die Ablage, nicht die Antwort des Servers dieses Repos (der Vergleich Server gegen Ablage läuft über die Handvektoren der Kotlin-Tests). `SPEC-030`, `SPEC-060`, Beobachtung angeglichen |

Nachweis-Pflichten: `make gates` grün (eigener Lauf, siehe Cache-Falle); Review-Report liegt vor (Nachbesserung ohne zweiten Review, B3); Doku-Update (Spezifikation, Matrix, `docs/user/einbettung-und-betrieb.md`, `docs/user/referenz-konfiguration.md`, Messprotokoll, Register) im Diff; Closure-Notiz §7 ist leer („Wird bei der Closure gefüllt.“), Haken und Risiko-Ausgänge in §6 stehen aus: Planner-Aufgabe.

## Draht (Messdateien selbst gelesen)

Quelle: `referenztest-dotnet/Mitschnitt/messung/bulk-merge-katalog/{2022,2025}/zusatz-unprepare-curcmd.messung`, `…/zusatz-returnvalue-flag.messung`; W-6 aus dem Messprotokoll.

| Aussage | Gelesen | Urteil |
|---|---|---|
| `sp_unprepare` gehalten | `79 00000000 FE 0000 E000 0000000000000000`, beide Instanzen, kein DONE | gleich `HELD_LAST` |
| zwei Anweisungen im Batch | `79 … FE 0100 E000 … 79 … FE 0000 E000 …` | gleich `HELD_MORE` plus `HELD_LAST` |
| unbekannte Nummer | `AA … f31f0000 08 10 …`, danach `79 F31F0000 FE 0200 E000 …` (Mitschnitt 2022 und 2025 gleich) | gleich `UNKNOWN_LAST` (8179, `ERROR` ohne `MORE`) |
| Flag-Feld RETURNVALUE | `AC 0200 4000 6f00 01 00000000 0000 26 04 04 …` (Ordinal, Name, Status, UserType, Flag-Feld, Typ): Flag-Feld `0x0000` | gleich |
| `curCmd` Änderung | W-6: INSERT `0x00c3`, DELETE `0x00c4`, UPDATE `0x00c5`; Batch `DONE COUNT`, RPC `DONEINPROC MORE\|COUNT`, DONEPROC `0x00e0` | gleich `DoneCommand.forKind`; Handvektoren in `ResponseStatementKindTest` |
| Massenladen | `0x00f0` | bleibt (Formvorrang) |
| `0x00c1` nach Fristablauf | **nicht gemessen**: die Referenz schreibt `0x00c1` bei einem Fehler im Ergebnis (W-6, W-1 bis W-3), die Frist selbst ist nicht gemessen | ehrlich gekennzeichnet („aus dem Fehler im Ergebnis übertragen, die Frist ist nicht gemessen“) in `SPEC-031`, Matrix, `referenz-konfiguration.md`, Messprotokoll |

Größe der Freigabe-Antwort nachgerechnet: ERROR 113 Byte, RETURNSTATUS 5, DONEPROC 13, also 131 Byte je einstellige Nummer; 419 309 × 131 = 54 929 479 Byte, wie `SPEC-060` und Messprotokoll sagen.

## Entscheidungs-Konformität

* [ADR-0042](../plan/adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) Option A: ein Feld `kind` am `UpdateCount`, kein neues Ereignis, kein interner Weg, kein Klassifizierer; Mitglieder und Kennungen nach Tabelle der ADR (nur die vier, die der Slice selbst belegt; `CREATE_TABLE`, `ALTER_TABLE`, Transaktionstexte gehören dem Folge-Slice und sind nicht angelegt). Standardwert `UNKNOWN` erhält Backends ohne Art. Konform.
* [ADR-0038](../plan/adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md)/[ADR-0039](../plan/adr/0039-abschluss-der-antwort-auf-eine-rpc-nachricht-die-der-parser-abweist.md): Abschluss der RPC-Antwort im Fehlerfall unverändert; Fristablauf im RPC schließt weiter mit ERROR und DONEPROC `ERROR` `0x00e0` (auch bei offenem Ergebnis), in `SPEC-031` als nicht gemessen und nicht gebaut benannt.
* `OpenResultTracker`: wirkt nur auf geschriebene Tokens (`ColMetadataToken` öffnet, jedes `DoneToken` schließt); ein vom Sequenzierer zurückgehaltener Abschluss zählt nicht. Mutation m3 (nie gesetzt) und der Fall „nach abgeschlossenem Ergebnis“ (Test `Die Frist nach einem abgeschlossenen Ergebnis …`) decken beide Richtungen.
* Streaming der Freigabe-Antwort: `ErrorMapper.releaseAnswer` schreibt Token für Token in die Senke, kein Puffer; ein Abschluss je Anfrage über `RunConclusion` (`Finished` und `Cut`); das alte `UnprepareBatch.completed()` ist entfernt; Verhaltensgleichheit bei doppelter Nummer im Batch ist seit `ee4d637b` getestet. `UnprepareLastProcessTest` b9 (131 Byte je Nummer, zwei gleichzeitige Nachrichten zu 16 MiB bei 256 MiB Heap) lief im frischen `clean-build` grün.
* AGENTS.md: kein `@Suppress`, Docker-only, keine Gate-Lockerung, kein Accepted-ADR geändert, `spec/architecture.md` nicht berührt, Kommentare im Indikativ (stichprobenartig `ResponseSequencer`, `Token.kt`, `OpenResultTracker`, `ErrorAnswers`).

## Plan-vs-Code-Diff

§3 gegen `git diff 9e97143c..HEAD --stat` (62 Dateien, 1205/304 Zeilen): alle Quell- und Testdateien der Tabelle stehen im Diff, und alle Dateien des Diffs stehen in der Tabelle, seit `ee4d637b` auch `TableStoreTest` (im Test des Moduls), `TokenRoundtripTest`, `CodecSmokeTest`, `RequestWiringTest` und der Kopf „Berührte Spec-Stellen“ (`SPEC-002`, Simulations-Algorithmus Schritt 6, `SPEC-022`). Nicht in der Tabelle, mechanisch: die Nachzüge der `slice-mv`-Commits (Register-`state.md`, Welle, offene Slices, Reports), `docs/plan/spec-abdeckung.md` (vom Werkzeug erzeugt, Zahlen nicht nachgerechnet) und der Review-Report. Quelltext nicht in der Tabelle: keiner. `testserver/kotlin` (Produktionscode) unberührt, wie die Tabelle sagt. `harness/README.md` nicht berührt: die Zellenregel (höchstens 200 Zeichen) hat nichts zu prüfen.

Zweite Fundstellen: Grep über Spec, Matrix, Nutzer-Doku, Quellkommentare nach „weil der Port die Art nicht trägt“, „DONE ohne Bits“ (Freigabe), „0x0001“ (Flag), „danach ein DONE mit Fehlerstatus“: kein stehengebliebener alter Wortlaut außerhalb der Gegenüberstellung im Messprotokoll (Spalte „Stand des Servers“ vor dem Bau, zulässig) und zeitlosen Beständen (`DispatchSupport` „DONE ohne Bits“ gehört dem Strom ohne Ereignis). `docs/user/*`: `einbettung-und-betrieb.md` (UpdateCount mit `kind`) und `referenz-konfiguration.md` (Handles, Antwortform, Tabelle der Antworten, Fehlerabschluss) sind auf den Ist-Zustand gezogen, ohne Chronik.

## Nachbesserung `ee4d637b` gegen den Review

| Finding | Stand |
|---|---|
| F-1 `SPEC-030` alte Lesart | behoben (Tokenfolge „RETURNSTATUS 8179 und DONEPROC, kein DONE“); kein Rest im Grep |
| F-2 Fristablauf als Messung geführt | behoben in Spezifikation, Matrix, Nutzer-Doku („aus dem Fehler im Ergebnis übertragen, die Frist ist nicht gemessen“) |
| F-3 b10/b11 ohne Vorbehalt in der Matrix | behoben (Matrix: Einzelmessung, Fassung außerhalb des Repos); Messprotokoll sagt es im selben Satz |
| F-4 zehn Typen / Sätze nach dem Merge | Sätze behoben (neun belegt, `smallint` über `SqlType.Int16`, `tinyint` ungemessen, `float` Grenze); der **DoD-Wortlaut** steht unverändert → B1 |
| F-5 Plan-Tabelle | behoben (siehe Plan-vs-Code-Diff) |
| F-6 KDoc `unknownHandles` | behoben (`releaseAnswer`) |
| F-7 Wächter-Beschreibung | behoben (Indikativ, Wächter `ColumnDef.nullable`; Mutation m5 rot) |
| I-2 doppelte Nummer | Test ergänzt (`Dieselbe Nummer zweimal in einem Batch ergibt genau einen ERROR an der zweiten Stelle`), in Mutation m2 mitrot |

## Mutationen (selbst gefahren)

Kopie von `src` und `testserver` bei HEAD im Scratchpad, je eine Mutation, begrenzt auf `ResponseStatementKindTest`, `ReturnValueFlagsTest`, `UnprepareDispatchTest`, `QueryTimeoutTest`, `SimulationBackendChangeTest` (Gradle in Docker über das Image der Stage `deps`, nicht `make test`). Der Source-Set `integrator` lief hier nicht; er ist im frischen `clean-build` grün und war im Review (Mutation r6) rot für die Art.

| Nr. | Mutation | Wirkung |
|---|---|---|
| m1 | `forKind` immer `NONE` (`else -> DoneCommand.NONE` in `ResponseRun.finish`) | rot: 4 Tests in `ResponseStatementKindTest` |
| m2 | `MORE` immer `FINAL` (`val more = DoneStatus.FINAL`) | rot: 5 Tests in `UnprepareDispatchTest` |
| m3 | `resultOpen` nie gesetzt (`is ColMetadataToken -> Unit`) | rot: `QueryTimeoutTest > Die Frist im Ergebnis haengt ERROR und DONE an die gesendeten Tokens` |
| m4 | UPDATE meldet `DELETE` (`SimulationChanges`) | rot: 4 Tests in `SimulationBackendChangeTest` |
| m5 | Flag-Feld aus der Nullbarkeit (`TypeInfoWriter.flags(token.column)`) | rot: beide Tests in `ReturnValueFlagsTest` |

Basislauf der fünf Klassen: 45 Testfälle grün.

## Grenzen und Reste (benannt)

* Gemischter Batch aus gehaltenen und unbekannten Nummern: die Tokenfolge ist nur für reine Batches gemessen (M-22, M-23); die Folge im gemischten Batch ist in `SPEC-060` als Setzung nach derselben Regel je Anweisung gekennzeichnet. Ehrlich.
* ERROR-Token der Freigabe: der echte Server schreibt Prozedur `sp_unprepare` und Zeile 1, der Server dieses Repos leer und 0; als „bekannte Abweichung, nicht angeglichen“ in `SPEC-060`, Messprotokoll und Register benannt, ohne eigenen Träger-Slice (offene Schuld gegen das Zielbild, Review I-3).
* Fristablauf bei der Freigabe-Antwort schließt mit DONE (Form BATCH) statt DONEPROC; Gestalt ungemessen. `SPEC-060` beschreibt „DONE mit Fehlerstatus hinter den bis dahin geschriebenen Tokens“, ohne das Wort „Setzung“ im selben Satz (Review I-4, unverändert).
* Fristablauf im RPC bei offenem Ergebnis (DONEINPROC `MORE|ERROR` `0x00c1` am echten Server bei einem Fehler der Ausführung): nicht gebaut, in `SPEC-031` und Matrix benannt.
* b10 und b11 des Messprotokolls stammen aus einer Fassung außerhalb des Repos (Konstante `CONNECTIONS` 3 und 4), ein Lauf je Zeile; ich habe sie nicht nachgemessen. Die Aussage des Messprotokolls ist auf den Beleg zugeschnitten („ob die längere Antwort die Grenze verschiebt, zeigt ein Lauf je Zeile nicht“); b9 ist durch den Test im Repo belegt.
* Der Merge von `main` ist durch: `main` (`9e97143c`) liegt in HEAD, `git merge-tree` gegen `main` ohne Konflikt; ein weiterer Merge ist vor der Closure nicht nötig, sofern `main` nicht weiterläuft.

## Bedingungen für die Planner-Closure

* **B1 (DoD-Wortlaut Liefer-Punkt 1).** Der Punkt nennt zehn Typen mit „je Typ ein Test“ und ist so nicht erfüllbar: belegt sind neun (zehn mit dem ungemessenen `tinyint`), `float` hat weder `SqlType` noch Tokentyp. Entweder passt der Planner den Wortlaut an („neun der zehn gemessenen Typen, die ein Backend über `SqlType` liefern kann; `float` ohne Träger“) oder er legt einen Träger für `float` an (Bedingung dafür: ein öffentlicher `SqlType` für `float`/`real` und ein Tokentyp `FLOATN` in der Bibliothek, gebaut mit einem Test und einer Messzeile; ein Träger ist ein eigener Slice, weil er die Fläche `SqlType` (`SPEC-059`) erweitert). Ohne eine der beiden Handlungen bleibt der Haken gesetzt-aber-wörtlich-falsch.
* **B2 (Haken, Closure-Notiz, Risiken).** Haken der drei Liefer-Punkte (nach B1) setzt der Planner; §7 füllen (Steering-Loop-Lerneintrag, nennt die angelegten Mitglieder `UNKNOWN`, `INSERT`, `DELETE`, `UPDATE` als Übergabe an `slice-ddl-abschluss-und-transaktionszustand-angleichen`); jedes Risiko in §6 bekommt seinen Ausgang (Änderung von `UpdateCount` eingetreten und in `SPEC-059` protokolliert; weitere Anweisungsarten bei `slice-merge-antwort-an-messung-angleichen`; gemeinsamer Pfad mit `slice-sp-unprepare-ohne-obergrenze` ohne Konflikt entfallen; falsche Art vom Backend: Grenze in `SPEC-031` und `SPEC-059` benannt, weiter offen als Zusage).
* **B3 (Nachbesserung ohne zweiten Review).** `ee4d637b` hat keinen eigenen Review; ich habe F-1 bis F-7 einzeln gegen den Diff geprüft und den frischen Bau gefahren. Der Planner entscheidet, ob das genügt (Docs und Tests, ein Kommentar im Quelltext, kein Verhalten im Produktivcode geändert).
* **B4 (Messprotokoll und Register).** Die Zeilen M-23 und M-24 stehen auf „angeglichen“ (Messprotokoll, Zuordnung); das Register `BEO-TDS/zielbild-luecken-der-antwort-tokens` bekommt bei der Closure den Stand „gebaut“ und benennt die Reste (ERROR-Felder Prozedur und Zeile, Fristablauf der Freigabe, `float`, `0x00c1` im RPC) mit Träger oder als bewusst ohne Träger; eine Beobachtung anfällt ohnehin: der Flake `TableStoreTest > gleichzeitiger Zugriff verliert nichts` (24 min im frischen Bau, Last 5 bis 127) gehört ins Wanduhr-Register, nicht in diesen Slice.
* Der Haken bleibt beim Planner: nach B1 bis B4 ist der Slice aus meiner Sicht closure-fähig; `git mv` nach `done/` erst nach dem Inhalt (AGENTS.md §3.3 Nr. 2).

## Zeit

Gesamtlaufzeit dieser Verifikation rund 58 Minuten (15:46 bis 16:44). Größte Posten: `make clean-build` 34 min 17 s (davon rund 24 min der Flake-Kandidat `TableStoreTest > gleichzeitiger Zugriff verliert nichts`), `make e2e` 412 s, `make matrix-zitate` rund 4 min (Cache), fünf Mutationen mit Basislauf rund 6 min, `make gates FORCE=1` 34 s (Cache), `make flaeche` und `make docs-check` je rund 3 s.
