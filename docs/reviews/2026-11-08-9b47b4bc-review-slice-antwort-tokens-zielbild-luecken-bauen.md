# Review-Bericht: slice-antwort-tokens-zielbild-luecken-bauen (HEAD 9b47b4bc, Basis 5114e9e7)

* Rolle: Reviewer (Modul 10), frischer Kontext. Gegenstand: `git diff 5114e9e7..HEAD` (59 Dateien; Commits `86a512cd` Code und Tests, `9b47b4bc` Spezifikation, Matrix, Messprotokolle, Nutzer-Doku, Register, Plan, Roadmap; vier `slice-mv`-Commits).
* Maßstab: Plan, DoD-Bezüge und Risiken des Slice [slice-antwort-tokens-zielbild-luecken-bauen](../plan/planning/done/slice-antwort-tokens-zielbild-luecken-bauen.md); [ADR-0042](../plan/adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md), [ADR-0038](../plan/adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md), [ADR-0039](../plan/adr/0039-abschluss-der-antwort-auf-eine-rpc-nachricht-die-der-parser-abweist.md), [ADR-0040](../plan/adr/0040-wirksame-konfiguration-im-ereignis-aus-typisiertem-wertobjekt.md); `SPEC-022`, `SPEC-030`, `SPEC-031`, `SPEC-059`, `SPEC-060`; [TDS-FA-04](../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-FA-10](../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate); `AGENTS.md` §3.1 bis §3.8. Keine Prüfung gegen die DoD (Verifier).
* Selbst gefahren (Arbeitsbaum unverändert; Mutationen in einer Kopie von `src` und `testserver` unter dem Scratchpad, Stage-Image `deps` der Implementer-Rolle nur gelesen, eigenes Build-Volume): `make gates FORCE=1` Exit 0 (Layer-Cache der Stage `test`, die Tests liefen nicht neu: gleicher Eingang wie der Lauf des Implementers; `docs-check` 1282 Dateien, 0 Befunde), `make flaeche` Exit 0; Merge-Probe `git merge-tree --write-tree HEAD main` (main `f32447ca…`/`9e97143c`); acht Mutationen über sechs enge Testklassen (59 Tests, Basislauf grün).

## Gesamturteil

0 HIGH, 5 MEDIUM, 2 LOW, 6 INFO. Die Antwortbytes stimmen mit den Messdateien überein; der Kern (Flag-Feld, `curCmd` nach Art, `OpenResultTracker`, gestreamte Freigabe) ist korrekt und von den Tests getragen (alle gefahrenen Mutationen rot). Die Findings liegen in Fundstellen und Aussagenreichweite: eine stehengebliebene alte Lesart in `SPEC-030`, der Fristablauf `0x00c1` als Übertragung ohne Kennzeichnung im selben Satz, Messzeilen aus einer Fassung außerhalb des Repos in der Matrix, ein Liefer-Punkt der DoD, den die Lieferung wörtlich nicht trägt, und die Plan-Tabelle. Der Merge mit `main` konfliktiert in Doku und Spec, nicht im Quelltext.

## (a) Echtheit der Antwortbytes (aus den Hex-Dateien gelesen)

Quelle: `referenztest-dotnet/Mitschnitt/messung/bulk-merge-katalog/{2022,2025}/zusatz-unprepare-curcmd.messung`, `…/zusatz-returnvalue-flag.messung`, `…/2022/dml-batch.messung`, `dml-rpc.messung`.

| Aussage | Gelesenes Token / Byte | Urteil |
|---|---|---|
| `sp_unprepare` gehalten, eine Nummer | `79 00000000 FE 0000 E000 0000000000000000` (RETURNSTATUS 0, DONEPROC Status 0, Befehl `0x00e0`, kein DONE) | stimmt mit `ErrorAnswers.releaseAnswer`; Handvektor `HELD_LAST` |
| zwei Anweisungen | `79 0 FE 0100 E000 … 79 0 FE 0000 E000 …` (`MORE` an der ersten) | stimmt; `MORE` an jedem DONEPROC außer dem letzten |
| unbekannte Nummer | `AA … f31f0000 08 10 35 "Could not find prepared statement with handle 999999." … "sp_unprepare" 01000000` dann `79 F31F0000 FE 0200 E000 …` | Nummer 8179, State 8, Class 16, RETURNSTATUS 8179, DONEPROC `ERROR` (0x0002) ohne `MORE`; stimmt. Prozedurname und Zeile 1 im ERROR fehlen im Server dieses Repos: benannt (siehe (f)) |
| Flag-Feld RETURNVALUE | `AC 0200 02 4000 6f00 01 00000000 0000 26 04 04 …` und die übrigen Typen (`0x68`, `0x6c`, `0x6d`, `0xe7`, `0x28`, `0x29`, `0x2a`) | alle `0x0000`, mit Wert, NULL und ohne Zuweisung; `TokenWriters.RETURN_VALUE_FLAGS` ist eine Konstante, der Tokentyp spielt keine Rolle |
| `curCmd` Änderung | Batch `FD 1000 C3 00`, `C5`, `C4` (COUNT); RPC `FF 1100 C3`, `C5`, `C4` (`MORE|COUNT`), DONEPROC `FE 0000 E000` | INSERT `0x00c3`, UPDATE `0x00c5`, DELETE `0x00c4`; `DoneCommand.forKind` stimmt |
| Massenladen | bleibt `0x00f0` (`form == BULK` vor `forKind`) | stimmt |
| Fristablauf `0x00c1` | nicht gemessen: Übertragung des Fehlers im Ergebnis (`FD 1100 C1` bzw. DONEINPROC `MORE|ERROR` `0x00c1`, W-1 bis W-3, W-6) | Setzung, siehe M-2 |

## (b) `OpenResultTracker`

Zustandsmaschine: `open` wird mit dem *geschriebenen* `ColMetadataToken` wahr und mit jedem geschriebenen `DoneToken` (DONE, DONEINPROC, DONEPROC) falsch; ein vom Sequenzierer zurückgehaltener Abschluss ist nicht geschrieben und zählt nicht (Fristablauf nach `ResultEnd` und `UpdateCount` mit zurückgehaltenem DONE ergibt `open` noch wahr, wenn das COLMETADATA geschrieben und kein DONE folgte: richtig, denn der Client hat keinen Abschluss). Abbruch durch ATTENTION: `DONE[ATTN]` `0x00fd` über `AttentionAck`, vom Tracker unabhängig. Fehler im Strom: der Sequenzierer schreibt `failedDone(form, columns != null)` selbst; der Tracker wirkt nur in `AbortedAnswer.timedOut`. Form RPC: `failedDone(RPC)` ignoriert `resultOpen` (DONEPROC `0x00e0`), in `SPEC-031` ausdrücklich als „nicht gemessen und nicht gebaut“ benannt. Zwei Ergebnisse im Batch: `DONE(MORE)` setzt zurück, das zweite COLMETADATA setzt neu. Mutationen siehe unten.

## (c) Gestreamte Freigabe-Antwort, `RunConclusion`, `answerBatch`

* Genau ein Abschluss je Anfrage: `answerRelease` ruft einmal `conclusion.conclude`; `Finished` schreibt EOM über `finished`, `Cut` über `AbortedAnswer`. Kein zweiter Pfad schreibt ein DONE (das alte `UnprepareBatch.completed()` ist entfernt, kein Aufrufer übrig).
* Zeile `queryOutcome = if (unknown.isEmpty) OK else ERROR` in `answerRelease`: verhaltensgleich zur früheren Vorabprüfung. Doppelte Nummern in einem Batch (`…5;…5`): die erste ist gehalten, die zweite ergibt in `UnprepareBatch.release` eine gesetzte Stelle im `BitSet` (Position, nicht Wert), also genau ein ERROR an der zweiten Stelle; das ist dasselbe Ergebnis wie `filterNot` vorher. Kein Test dafür (INFO I-2).
* Verhaltensänderung, die der Plan nicht nennt, aber richtig ist: der Erfolgsfall lief vorher an `resetPending` und `LeadingToken` vorbei (ENVCHANGE nach RESETCONNECTION fehlte oder blieb für die nächste Antwort offen); jetzt führt ihn die gestreamte Antwort und setzt `resetPending = false`. Ein Test „Erfolg mit RESETCONNECTION“ besteht nicht, der Pfad ist aber derselbe wie im Fehlerfall (Test mit `resettingBatch("EXEC sp_unprepare 2")`, Zeilen 133 und 545 von `UnprepareDispatchTest`).
* `UnprepareLastProcessTest` b9: 131 statt 113 Byte je Nummer; Rechnung nachvollzogen (419 309 × 131 = 54 929 479; 419 309 × 113 + 13 = 47 381 930). Zeilen b10 und b11: im Messprotokoll und in `SPEC-030` mit „Fassung außerhalb des Repos (Größenordnung)“ gekennzeichnet; in der Matrix nicht (M-3).

## Mutationen (selbst gefahren)

Kopie des Baums bei HEAD, je eine Mutation, begrenzt auf `ResponseStatementKindTest`, `ReturnValueFlagsTest`, `UnprepareDispatchTest`, `QueryTimeoutTest`, `SimulationBackendChangeTest`, `ResponseMeasuredTest` (59 Tests, Basislauf grün). Die Läufe waren Gradle in Docker (`gradle :test --tests …`), nicht `make test`.

| Nr. | Mutation | Wirkung gemessen |
|---|---|---|
| r1 | `forKind` immer `NONE` (`else -> DoneCommand.NONE` in `ResponseRun.finish`) | rot: 4 Tests in `ResponseStatementKindTest` (Batch, RPC, drei Anweisungen, Ohne-Treffer) |
| r2 | `MORE` immer `FINAL` (`val more = DoneStatus.FINAL`) | rot: 4 Tests in `UnprepareDispatchTest` (Handvektor `HELD_MORE`, gemischter Batch, 1000 Handles, unbekannte Nummern) |
| r3 | `resultOpen` nie gesetzt (`is ColMetadataToken -> Unit`) | rot: `QueryTimeoutTest > Die Frist im Ergebnis haengt ERROR und DONE an die gesendeten Tokens` (1 Test) |
| r4 | RETURNSTATUS 8179 → 0 (`ReturnStatusToken(0)`) | rot: 2 Tests in `UnprepareDispatchTest` |
| r5 | Tracker setzt bei DONE nicht zurück (`is DoneToken -> Unit`) | rot: `QueryTimeoutTest > Die Frist nach einem abgeschlossenen Ergebnis traegt die Kennung ohne offenes Ergebnis` (1 Test; Batch mit zwei Ergebnissen: `ResultEnd` und `UpdateCount`) |
| r6 | Backend meldet bei UPDATE die Art `DELETE` (`SimulationChanges`) | rot: 4 Tests in `SimulationBackendChangeTest`; im Source-Set `integrator` `StatementKindEmbeddingTest`: 1 von 3 rot (der Vergleich mit dem Simulations-Backend) |
| r7 | Flag-Feld aus der Spalte (`TypeInfoWriter.flags(token.column)`, der Stand vor der Messung) | rot: beide Tests in `ReturnValueFlagsTest` |

Der Implementer meldet zwölf Mutationen; vier davon (`forKind`, `MORE`, `resultOpen`, 8179) und zwei eigene wurden hier einzeln gefahren, alle rot. Die übrigen acht des Implementers wurden nicht wiederholt.

## Findings

### F-1 MEDIUM — `SPEC-030` trägt neben dem neuen Wortlaut die alte Lesart der Antwort auf eine unbekannte Nummer

* kategorie: MEDIUM
* quelle: `AGENTS.md` §3.8; Reviewer-Skill „Fortschreibung lässt die zweite Fundstelle stehen“; [TDS-FA-04](../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc)
* pfad: `spec/spezifikation.md` · „ergibt `SPEC-060`: ein ERROR je solcher Nummer, danach ein DONE mit Fehlerstatus“ (Abschnitt `SPEC-030`, Absatz zur Batch-Freigabe, vor „Die Verbindung bleibt offen, das Backend wird nicht gerufen“)
* befund: Der Satz steht im selben Absatz wie die neue Tokenfolge („je unbekannter Nummer ein ERROR, ein RETURNSTATUS 8179 und ein DONEPROC … kein DONE“) und im neuen `SPEC-060` und widerspricht beiden: die Antwort endet nach diesem Slice nicht mit einem DONE. Die Änderungsliste des Commits nennt die Stelle nicht.
* verifizierbar: ja (Grep „danach ein DONE mit Fehlerstatus“ in `spec/spezifikation.md`)
* klasse: Fortschreibung lässt die zweite Fundstelle stehen

### F-2 MEDIUM — Fristablauf mit `0x00c1` ist eine Übertragung, Spezifikation, Matrix und Nutzer-Doku führen ihn als gemessen oder ohne Kennzeichnung

* kategorie: MEDIUM
* quelle: `AGENTS.md` §3.8; Reviewer-Skill „Aussage breiter als ihr Beleg“ / „Zusage ohne ihre gemessene Bedingung im selben Satz“; [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität)
* pfad: `spec/spezifikation.md` · „Der Fehlerabschluss nach einem Abbruch durch die Frist trägt im Batch `0x00c1` (DONE), wenn der Client ein Ergebnis ohne Abschluss hat“; `docs/plan/kompatibilitaetsmatrix.md` · „nach Fristablauf trägt das DONE im Batch `0x00c1`, wenn der Client ein Ergebnis ohne Abschluss hat“; `docs/user/referenz-konfiguration.md` · „Nach Ablauf von `QUERY_TIMEOUT` trägt das DONE der Fehlerantwort im Batch die Befehlskennung `0x00c1`“
* befund: Gemessen ist der Fehler im Ergebnis (W-6, ein Fehler der Ausführung), nicht die Frist: die Frist ist am echten Server nicht gemessen, und ein Client-Timeout erzeugt dort ein ATTENTION (`0x00fd`), kein serverseitiges ERROR. Nur das Messprotokoll (`referenzabgleich.md`, Zeile „W-6 … übertragen auf den Fristablauf“) sagt „Kennung ist aus dem Fehler im Ergebnis übertragen“; die drei Fundstellen oben nennen das nicht im selben Satz, die Spezifikation setzt „gemessen: … bei einem Fehler im Ergebnis“ und schließt mit „für den Fristablauf im RPC nicht gemessen“, was für den Batch eine Messung nahelegt.
* verifizierbar: nein (Lesart)
* klasse: Übertragung als Messung geführt (Aussage breiter als der Beleg)

### F-3 MEDIUM — Matrixzeile nennt b10 und b11 ohne den Vorbehalt der Messfassung außerhalb des Repos

* kategorie: MEDIUM
* quelle: `AGENTS.md` §3.8; Reviewer-Skill „Messzahl aus einem Erzeuger, der nicht im Repo liegt“
* pfad: `docs/plan/kompatibilitaetsmatrix.md` · „Messprotokoll zum Speicher der Antwort, Zeilen b10 und b11: mit der Antwort zu 131 Byte je Nummer erhalten drei gleichzeitige Nachrichten die vollständige Antwort, bei vier endete der Prozess“
* befund: b10 und b11 stammen aus einer Fassung des Tests mit `CONNECTIONS` gleich 3 und 4, die nicht im Repo liegt (Messprotokoll: „Größenordnung“, ein Lauf je Zeile, Load Average 13; `SPEC-030` benennt das im selben Satz). Die Matrixzeile führt beide Ergebnisse ohne diese Angabe im selben Satz; im Gegensatz zum Satz davor („in Einzelmessungen“).
* verifizierbar: nein
* klasse: Zusage ohne ihre gemessene Bedingung im selben Satz

### F-4 MEDIUM — DoD-Liefer-Punkt 1 nennt zehn Typen mit „je Typ ein Test“; geliefert sind acht über `SqlType`, `smallint` am Token, `float` fehlt; nach dem Merge mit `main` stimmt der Begründungssatz nicht mehr

* kategorie: MEDIUM
* quelle: Slice §2 Liefer-Punkt 1; `AGENTS.md` §3.8; Reviewer-Skill „Aussage breiter als ihr Beleg“
* pfad: `docs/plan/planning/done/slice-antwort-tokens-zielbild-luecken-bauen.md` · „je Typ ein Test“; `src/test/kotlin/io/github/pt9912/tdswire/protocol/response/ReturnValueFlagsTest.kt` · „`smallint` (`INTN` 2, M-24) kennt der Backend-Weg nicht ([SqlType] hat keinen Typ dafür)“; `spec/spezifikation.md` · „Belegt sind acht Typen, die ein Backend über `SqlType` liefern kann“
* befund: (1) `float` hat weder `SqlType` noch Tokentyp: der Fall ist nicht gebaut und ehrlich als solcher benannt (Spezifikation, Messprotokoll M-24, Register), der DoD-Wortlaut bleibt aber bei zehn Typen und „je Typ ein Test“; der Planner entscheidet, ob der Wortlaut angepasst wird oder `float` einen Träger bekommt, sonst ist der Punkt wörtlich nicht erfüllbar. Das Flag-Feld ist eine Konstante und hängt am Typ nicht; die Aussage „jedes RETURNVALUE `0x0000`“ ist durch Konstruktion wahr, der Beleg je Typ ist es nicht. (2) In `main` (`SqlType.Int16`, Slice Katalogspalten, Merge-Basis 5114e9e7 noch ohne) kann ein Backend `smallint` über `SqlType` liefern: die drei Sätze oben (KDoc, Spezifikation, Matrixzelle „acht Typen über `SqlType`“) sind nach dem Merge falsch, und `smallint` ließe sich dann am Sequenzierer wie die übrigen belegen.
* verifizierbar: ja (Grep „smallint“ in den drei Dateien nach dem Merge; `SqlType.Int16` in `main`)
* klasse: Liefer-Punkt-Wortlaut trägt mehr als die Lieferung; Aussage über die Fläche nicht gegen `main` gehalten

### F-5 MEDIUM — Plan-Tabelle §3 und Kopf „Berührte Spec-Stellen“ nennen nicht alle berührten Stellen

* kategorie: MEDIUM
* quelle: Reviewer-Skill „Plan-Tabelle nennt die berührten Dateien nicht“
* pfad: `docs/plan/planning/done/slice-antwort-tokens-zielbild-luecken-bauen.md` · „Tests Kotlin (`ReturnValueFlagsTest`, `ResponseStatementKindTest` neu;“ und „**Berührte Spec-Stellen:** SPEC-030“
* befund: Der Diff berührt außerdem `testserver/test/kotlin/io/github/pt9912/tdswire/testserver/TableStoreTest.kt` (Zeile „`testserver/kotlin` nicht betroffen“ nennt nur den Produktionscode), `src/test/…/protocol/token/TokenRoundtripTest.kt`, `…/smoke/CodecSmokeTest.kt` und `…/runtime/RequestWiringTest.kt`, die alle in der Testzeile fehlen. Im Kopf fehlen `SPEC-002` (Zeilen `UpdateCount(…, kind)`, `enum class StatementKind` im Query-Vertrag) und der Algorithmus des Simulations-Backends in Abschnitt 1 Schritt 6 („mit der Art der Anweisung“); `SPEC-022` steht nur in der Tabellenzeile, nicht im Kopf. Die Dateien der `slice-mv`-Nachzüge (drei weitere Register-`state.md`, Welle, offene Slices, Review-Reports) sind mechanisch und hier nicht gezählt.
* verifizierbar: ja (Abgleich `git diff --stat` gegen Tabelle)
* klasse: Plan-Tabelle nennt die berührten Dateien nicht

### F-6 LOW — KDoc verweist auf eine Funktion, die es nicht mehr gibt

* kategorie: LOW
* quelle: `AGENTS.md` §3.7 (Kommentar beschreibt, was da ist)
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/protocol/error/ErrorMapper.kt` · „hat eine eigene Antwort ([unknownHandles])“
* befund: `unknownHandles` ist in `ErrorAnswers.kt` zu `releaseAnswer` geworden; der Verweis in `ErrorMapper.code` zeigt ins Leere.
* verifizierbar: ja (Grep `unknownHandles` in `src/main`)
* klasse: Kommentar verweist auf umbenannte Deklaration

### F-7 LOW — Wächter-Beschreibung nennt den abgelösten Stand

* kategorie: LOW
* quelle: `AGENTS.md` §3.7
* pfad: `src/test/kotlin/io/github/pt9912/tdswire/protocol/response/ReturnValueFlagsTest.kt` · „(`0x0001` bei jedem Ausgabewert des Backends, der Stand vor der Messung)“
* befund: Der Halbsatz beschreibt den abwesenden Vorgängerstand („der Stand vor der Messung“) statt der geltenden Zusage; der Wächter selbst (Flag-Feld aus der Nullbarkeit) ist wirksam (Mutation r7 rot).
* verifizierbar: nein
* klasse: Kommentar beschreibt abwesenden Stand

## INFO

* I-1 Merge-Probe (`git merge-tree --write-tree HEAD main`): Konflikte nur in Doku und Spezifikation, keiner in `src/` oder `testserver/`: `spec/spezifikation.md` (Liste `SqlType` und Zusage/Nachweis von `SPEC-059`, Änderungsprotokoll, beide mit Eintrag vom 2026-11-08), `docs/user/einbettung-und-betrieb.md`, `docs/plan/planning/in-progress/roadmap.md`, `…/done/slice-antwort-tokens-zielbild-luecken-bauen.md`, `…/welle-testserver-manuell.md`, `…/done/slice-katalog-und-struktur-an-messung-angleichen.md`, `…/open/slice-abnahme-d-migrate.md`, `…/in-progress/slice-ddl-abschluss-und-transaktionszustand-angleichen.md`, Register `BEO-TDS/katalog-strukturabweisung-und-aenderungszaehler-weichen-vom-echten-server-ab/state.md`, dazu Modify/Delete: `open/slice-katalogspalten-smallint-tinyint-angleichen.md` (in `main` nach `done/`, hier geändert durch den `slice-mv`-Nachzug). Textlich sauber gemergt, aber zu prüfen: `TokenWriters`, `ErrorAnswers.kt`, `RequestLoop.kt`, `SimulationBackend` (`PreparedChange.Ready(StatementKind.UNKNOWN)` neben dem Katalog-Code von `main`) und die Tests `TableStoreTest`, `TokenRoundtripTest` (beide Seiten ändern sie). Die neuen Aufrufe `QueryEvent.UpdateCount(n)` in `main` (`BulkLoads`, `FixedAnswers`, `SimulationBackend`) bleiben mit dem Standardwert `UNKNOWN` übersetzbar; ein Übersetzungs- und Testlauf des Merge-Ergebnisses ist nicht gefahren (Sensor-Zuschnitt), der Verifier fährt ihn nach der Auflösung.
* I-2 Doppelte Nummer innerhalb eines Batches (`EXEC sp_unprepare 5;EXEC sp_unprepare 5`) hat keinen Test; die Lesart (zweite Stelle ERROR) folgt aus dem `BitSet` nach Position und stimmt mit dem früheren Ergebnis überein.
* I-3 (f) ERROR-Token der Freigabe: der echte Server schreibt Prozedur `sp_unprepare` und Zeile 1, der Server dieses Repos leer und 0; in `SPEC-060`, Messprotokoll M-23 und Register als „bekannte Abweichung, nicht angeglichen“ benannt. Ein eigener Träger besteht nicht (nur das Register); nach dem Grundsatz „Spezifikation ist Zielbild“ ist das eine offene Schuld.
* I-4 Fristablauf bei der Freigabe-Antwort schließt mit DONE (Form BATCH), nicht mit DONEPROC; die Gestalt ist nicht gemessen (die Freigabe ist ein Batch, ihre Antwort aber ein DONEPROC-Strom). Als Setzung in `SPEC-060` („DONE mit Fehlerstatus hinter den bis dahin geschriebenen Meldungen“) lesbar, nicht als Messung gekennzeichnet.
* I-5 (i) `TableStoreTest > gleichzeitiger Zugriff verliert nichts` (bis 27 Minuten im Gate-Lauf, 128 s im Rig): nicht Teil des Diffs (geändert sind nur `UpdateCount(1, UPDATE)` und der Import an anderen Tests der Datei). Der Test hat einen Leser, der ohne Pause `store.catalog()` ruft, und vier Schreiber mit je 300 Anfragen unter einer Sperre, deren Planung mit der Zahl der Tabellen wächst; bei Last 12 und mehr konkurriert die Schleife mit den Schreibern um die CPU. Das ist ein Last-/Planungsproblem (Kandidat für das Wanduhr-Register), kein Befund dieses Slice. In diesem Review lief die Stage aus dem Layer-Cache und hat die Zeit nicht neu gemessen.
* I-6 Nutzer-Doku: alle fünf Dateien unter `docs/user/` gegen den Stand gelesen; die Wörter „nicht gebaut“, „Zielzustand“, „bisher“, „noch nicht“, „seit Slice“ treffen nur Vorbestand außerhalb des Gegenstands (`testserver-manuell.md` „Soll“ als Begriff des Schemavergleichs, `vorläufig` im Ankernamen von `SPEC-036`). `einbettung-und-betrieb.md` Zeile zu `UpdateCount` und `referenz-konfiguration.md` (Handles, Antwortform, Tabelle der Antworten, Fehlerabschluss) sind fortgeschrieben; `referenz-ereignisse.md` und `testserver-manuell.md` nennen weder `curCmd` noch `sp_unprepare`-Antworten. Die Plan-Tabelle nennt nur zwei der Dateien, die Fortschreibung ist vollständig. Haken: keine gesetzt (`grep -c "\[x\]"` = 0 im Slice); `harness/README.md` nicht berührt; Roadmap zeigt den Slice unter `in-progress/`.

## geprüft, ohne Befund

* `src/main/kotlin/…/domain/` (`StatementKind.kt`, `QueryEvent.kt`): `UpdateCount(affectedRows, kind = UNKNOWN)` mit `@JvmOverloads`; `copy`, `componentN` und `equals` ändern sich und stehen im Änderungsprotokoll von `SPEC-059`; `StatementKindEmbeddingTest` trägt Konstanten, Standardwert und den Java-Konstruktor; keine Fremdbibliothek.
* `…/protocol/token/` (`Token.kt`, `TokenWriters.kt`): Kennungen gegen die Messdateien gelesen; Flag-Feld als Konstante.
* `…/protocol/response/` (`ResponseRun`, `ResponseSequencer`, `RpcOutputs`): Abbildung nach Art, Massenladen bleibt `0x00f0`, Kommentare in Indikativ.
* `…/protocol/dispatch/` (`OpenResultTracker`, `RequestLoop`, `RunConclusion`, `AbortedAnswer`, `UnprepareBatch`) und `…/protocol/error/ErrorAnswers.kt`: siehe (b) und (c); kein `@Suppress`, keine Schichtverletzung (`protocol` darf nach `spec/architecture.md` §2 `domain` importieren, `make detekt` im Gate-Lauf grün).
* `…/simulation/` (`SimulationChanges`, `SimulationBackend`): INSERT, UPDATE, DELETE melden ihre Art, Strukturänderung bleibt `UNKNOWN`; Mutation r6 rot.
* `src/integrator/`: `StatementKindEmbeddingTest` prüft ein eigenes Backend mit und ohne Art gegen die Antwort des Simulations-Backends und die Fläche von außen; `make flaeche` grün.
* Docker-only (`AGENTS.md` §3.1): keine Host-Aufrufe im Diff. Accepted-ADRs: keine geändert. Suppression: keine.
* `docs/messungen/`, `docs/plan/kompatibilitaetsmatrix.md`, `spec/spezifikation.md` (übrige Stellen), Register, Roadmap: Zeilen M-23, M-24, A-20 (Zeile entfernt, Fortschreibung bleibt im Text), W-6 gegen die Messdateien gelesen.

## Zusammenfassung der Klassen (Steering-Loop-Zähler)

* „Fortschreibung lässt die zweite Fundstelle stehen“ (F-1, F-4 Teil 2)
* „Aussage breiter als ihr Beleg“ / „Zusage ohne gemessene Bedingung im selben Satz“ (F-2, F-3)
* „Plan-Tabelle nennt die berührten Dateien nicht“ (F-5)
* „Kommentar beschreibt abwesenden Stand“ (F-6, F-7)
