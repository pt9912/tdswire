# Verifikationsbericht: slice-katalog-und-struktur-an-messung-angleichen (Teil a, HEAD 53d89521)

* Rolle: Verifier (Modul 11), frischer Kontext. Gegenstand: Slice `slice-katalog-und-struktur-an-messung-angleichen` (verkleinerter Zuschnitt, zwei Liefer-Punkte; im Zweig `worktree-agent-a95b80c5a5d35df3e` unter `next/`), HEAD `53d89521`, `main` `6d75692a` ist eingemergt. Verlauf: Review `2026-11-08-85d970bd-review-slice-katalog-und-struktur-an-messung-angleichen.md` (8 Findings: 0 HIGH, 5 MEDIUM, 2 LOW, 3 INFO) auf `85d970bd`, vom Implementer in `7a136b49` und `53d89521` behandelt.
* Maßstab: DoD §2, Plan §3, [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 3, `SPEC-035`, `SPEC-036`, Fehlertabelle, [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität), `AGENTS.md` §3.5, §3.8; Messdateien `referenztest-dotnet/Mitschnitt/messung/bulk-merge-katalog/{2022,2025}/katalog-struktur.messung`.
* Grundsatz des Nutzers: „Spec ist Zielbild“, der echte SQL Server ist die Referenz; Abweichungen sichtbar mit Träger.
* Verifiziert wird die Zusage, nicht der Diff; Belege sind eigene Läufe. Am Produktivcode nichts geändert; Mutationen liefen in Kopien (`git archive HEAD`) im Scratchpad.

## Gesamturteil

DoD beider Liefer-Punkte ist durch Beleg gedeckt; alle 8 Review-Findings sind behoben oder benannt. Die gesperrten Haken sind nicht gesetzt (0 Treffer `- [x]` in der Slice-Datei). 0 HIGH, 0 MEDIUM, 3 LOW, 3 INFO neu. Offen sind nur Dinge, die der Planner bei der Closure schließt (§6 Ausgänge, §7, Frage Folge-ADR) und drei benannte Grenzen (nicht gefahren: Lauf gegen echten SQL Server, `data import`/`data export` an einer Tabelle ohne Schlüssel, frischer `make spec-abdeckung`).

## Eigene Läufe (Belege)

Host unter Last durch fremde Läufe (andere Agenten bauten gleichzeitig); Einzelmessungen.

| Lauf | Ergebnis |
|---|---|
| `make clean-build` (`--no-cache`: `build-jar`, `test`, `detekt`) | Exit 0; `:test`, `:testserver:test`, `integratorTest`: 1238 `PASSED`, 0 `FAILED` (Zeilen mit `FAILED` im Namen sind Testnamen wie `FAILED_CLOSE_CONNECTION`) |
| `make gates FORCE=1` | Exit 0. **Cache-Falle:** die Docker-Stufen kamen im Wesentlichen aus dem Layer-Cache des `clean-build`-Laufs davor; der Lauf belegt die Verdrahtung und die Gruppen (`gate-zuordnung` 16 Gates, Selbsttest 33 Fälle), den Frischbeleg liefert `clean-build` |
| `make flaeche` | Exit 0 |
| `make docs-check` | Exit 0, 1256 Dateien, 0 Befunde (vor diesem Bericht) |
| `make matrix-zitate` | Exit 0, 1238 bestandene Testfälle, 309 Zitate, 0 ohne PASSED |
| `make e2e` | Exit 0, 8 Stufen grün, 429 s (hohe Last); kein Last-Flake, keine Wiederholung nötig |
| d-migrate 1.12.0 (Digest `sha256:308f2775…`) gegen `make testserver-up` aus dem Arbeitsbaum (Name `verif-ks`, Port 24951, Image `tdswire-testserver:verif-ks`; Container und Image danach entfernt) | siehe nächster Abschnitt |
| Mutationen (5 einzeln, siehe unten) | alle 5 rot in den erwarteten Tests |
| Echter SQL Server | nicht gefahren: der Slice verlangt keinen neuen Lauf; M-19 stützt sich auf die vorhandenen Messdateien beider Instanzen (Dekodierung unten) |

## Läufe mit d-migrate (Rundlauf, vom Implementer nicht gefahren)

| Lauf | Ergebnis |
|---|---|
| `schema reverse` auf frischem Server | Exit 0, `kunden` und `bestellungen` mit `primary_key` |
| `schema migrate --execute`, Soll1 = Ist + `produkte` mit Schlüssel | Exit 0, `status: ok`, 1 Anweisung (`CREATE TABLE [produkte] … CONSTRAINT [pk_produkte] PRIMARY KEY ([Id])`); `schema compare` Exit 0 `IDENTICAL` |
| `schema migrate --execute`, Soll2 = Soll1 + `notizen` **ohne Schlüssel** | Exit 0, `status: ok`, 1 Anweisung (`CREATE TABLE [notizen] ([Id] INT NOT NULL, [Text] NVARCHAR(30));`), `transactionRolledBack: false`; `schema compare` Exit 0 `IDENTICAL` mit Warnung E008 (kein Schlüssel) an beiden Seiten; erneutes `migrate`: `no_op` |
| `schema reverse` danach | Exit 0; `notizen` ohne `primary_key` in der Datei (`rev2.yaml`) |
| **Rundlauf:** Testserver neu gestartet mit `TESTSERVER_BESCHREIBUNG=rev2.yaml` (enthält `notizen` ohne `primary_key`) | Start bereit (anders als im Review auf `85d970bd`, F-2 behoben); `schema compare` gegen Soll2 Exit 0 `IDENTICAL`; `schema reverse` erneut: Datei gleich (`rev2 == rev3`, ohne die Namenszeile) |
| zwei gleichzeitige `schema migrate` mit demselben neuen Objekt `rennen` | ein Lauf Exit 0 (`ok`); der andere Exit 5, `status: failed`, `executionError` = „There is already an object named in the database.“, `recoverability: FULL_ROLLBACK_CONFIRMED`, `transactionRolledBack` bestätigt |

Wirkung der neuen Nummern in mssql-jdbc (13.4.0, im d-migrate-Image): der Treiber reicht die Meldung des Servers durch, die Transaktion wird zurückgenommen, die Verbindung bleibt benutzbar; ob d-migrate die Nummer selbst auswertet, zeigt der Bericht nicht (nur der Text steht darin). Grenze: Einzelläufe auf einem Host, gegen den Testserver, nicht gegen echten SQL Server; `data import`/`data export` mit der Tabelle ohne Schlüssel nicht gefahren.

## DoD-Zeilen gegen Beleg

| DoD | Beleg | Urteil |
|---|---|---|
| Liefer-Punkt 1: `CREATE TABLE` vorhandener Tabelle = 2714 (Class 16, State 6) | `ErrorCode.TDS_SIM_OBJECT_EXISTS`; `ErrorMapperTest > Strukturabweisung ist ERROR 2714 State 6, 2705 State 4 und 4902 State 1 mit offener Verbindung`; `SimulationBackendStructureTest > Abweisung gegen den Katalog traegt die Codes der gemessenen Fehler`; Messdatei 2022 und 2025 selbst dekodiert: 2714, State 6, Class 16 | gedeckt |
| `ADD` vorhandener Spalte = 2705 (State 4) | `TDS_SIM_COLUMN_EXISTS`, gleiche Tests (auch andere Schreibweise `NAME`); Messdatei: 2705, State 4, Class 16 | gedeckt |
| `ALTER TABLE` unbekannter Tabelle = 4902 (State 1) | `TDS_SIM_OBJECT_NOT_FOUND`; Messdatei: 4902, State 1, Class 16 | gedeckt |
| Verbindung bleibt offen | `ConnectionFate.OPEN` in allen drei Regeln, im Test je Code geprüft; Messdatei: Folgeanweisung derselben Verbindung antwortet | gedeckt |
| `SPEC-035`, `SPEC-036`, Fehlertabelle angeglichen | Diff `spec/spezifikation.md`: Zeile „Prüfung gegen den Katalog“, drei neue Tabellenzeilen, State-Ausnahmen, Liste der erlaubten Backend-Codes, Form D, Form M (Grenze) | gedeckt |
| Tests je Fall; `ADD` mehrerer Spalten unverändert | Tests oben; `ADD [A] INT, [B] NVARCHAR(10)` weiter als Setzung mit Messvermerk in Form D | gedeckt |
| Liefer-Punkt 2: `CREATE TABLE` ohne Schlüssel gelingt, `SimTable`/`TableStore` verlangen keinen Schlüssel | `SimModel.kt` ohne `require(isNotEmpty)`, `StatementPlanner.create` (`key.orEmpty()`), `TableStore.insertBatch` und `update` ohne Eindeutigkeitsprüfung bei leerem Schlüssel, `SimDescriptionReader` mit optionalem `primary_key` | gedeckt |
| Test je Form: Anlegen, Einfügen, Katalogabfrage der Tabelle ohne Schlüssel | `CREATE TABLE ohne Schluessel gelingt mit leerem Schluessel`; `eine Tabelle ohne Schluessel nimmt gleiche Zeilen an`; `eine Tabelle ohne Schluessel steht im Katalog mit leerem Schluessel` (Tabellenliste, Spalten, Primärschlüssel-Abfrage mit Kopf und ohne Zeile); zusätzlich `onUpdate aendert auf einer Tabelle ohne Schluessel mehrere Zeilen`, `eine Datei mit Tabellen ohne Schluessel startet den Testserver` | gedeckt |
| `SPEC-035` (Validierung), `SPEC-036` (Form D) angeglichen | Diff; der Widerspruch „eine oder mehrere Spalten“ (Review F-1) ist aufgelöst | gedeckt |
| Fläche: keine neue öffentliche Fläche | `make flaeche` grün; `SimTable` ändert nur eine Prüfung | gedeckt |
| `make gates` grün | siehe Läufe | gedeckt |
| Review durchgeführt, Report unter `docs/reviews/` | liegt vor (`85d970bd`); die Findings stehen unten gegen den Stand | gedeckt |
| Haken setzt der Planner | keine `- [x]` in der Slice-Datei | gedeckt (Haken offen für den Planner) |
| Doku-Update | `docs/user/einbettung-und-betrieb.md` (Liste der erlaubten Codes, Tabelle „Typische Fehler“, Schlüssel), `referenz-konfiguration.md` (drei Zeilen), `testserver-manuell.md` (`primary_key` optional) | gedeckt |
| Closure-Notiz, Register, Ausgänge der Risiken, drei Paarungen | §7 „Wird bei der Closure gefüllt“; §6 alle Ausgänge „offen bis zur Closure“; Register-Nachtrag zu F-4 liegt im Zweig | **offen, Aufgabe des Planners** |

## Review-Findings gegen den Stand `53d89521`

| Finding | Stand | Beleg |
|---|---|---|
| F-1 `SPEC-035` widersprüchlich | behoben | `primaryKey` „keine, eine oder mehrere“; Validierung und Datei sagen „Schlüssel darf leer sein“ |
| F-2 Datei lehnt Tabelle ohne Schlüssel ab | behoben, **Rundlauf gefahren** | `SimDescriptionReader` mit optionalem `primary_key`; Test `eine Datei mit Tabellen ohne Schluessel startet den Testserver`; Rundlauf oben (Start mit `rev2.yaml`); Mutation m4/m5 rot |
| F-3 Doku-Liste der Codes | behoben | `einbettung-und-betrieb.md` nennt die drei Codes |
| F-4 [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) „mit Schlüssel“ | **benannt, nicht gelöst** | ADR unverändert (richtig, `AGENTS.md` §3.5); Form D nennt die Abweichung, Träger ist `BEO-TDS/katalog-strukturabweisung-und-aenderungszaehler-weichen-vom-echten-server-ab` (Nachtrag), Frage an den Nutzer steht in §6; Bewertung unten |
| F-5 `TableStore.update` nach Merge mit `main` | behoben | `update` prüft bei leerem Schlüssel nicht; Test `onUpdate aendert … mehrere Zeilen` ruft den echten Pfad; Mutation m3 rot |
| F-6 `MERGE` ohne Schlüssel | behoben als Grenze | Test `ein MERGE gegen eine Tabelle ohne Schluessel ist abgewiesen`; `SPEC-036` Form M: „Gegen eine Tabelle mit leerem Schlüssel ist jedes `MERGE` `TDS_UNSUPPORTED_SQL` (Grenze … der echte Server nimmt dieses `MERGE` an, nicht gemessen)“; Doku (`einbettung-und-betrieb.md`, `testserver-manuell.md`) und Messprotokoll-Zeile nennen sie |
| F-7 Text zu 2714 umformuliert | behoben | Text jetzt „There is already an object named in the database.“ = gemessener Text ohne den Namen; Details unten |
| F-8 Plan-Tabelle | behoben | §3 nennt `docs/plan/spec-abdeckung.md` und `testserver/test/kotlin` |
| INFO-3 „belegt durch Test“ im Messprotokoll | übernommen | Zeile M-19 sagt „angeglichen“ ohne Testnamen; Hinweis bleibt für die Closure |

## Prüfung der Schwerpunkte

1. **Nummern, State, Class gegen die Messdateien.** Eigener Dekoder (Skript im Scratchpad, ERROR-Tokens `0xAA` der Serverantworten) liest in `katalog-struktur.messung` beider Instanzen: 2714 State 6 Class 16, 2705 State 4 Class 16, 4902 State 1 Class 16; 2022 und 2025 gleich. Code: `N_OBJECT_EXISTS = 2714`/`STATE 6`, `N_COLUMN_EXISTS = 2705`/`STATE 4`, `N_OBJECT_NOT_FOUND = 4902` mit `STATE` (1), alle `REQUEST` (Class 16, vom Test mit 16 geprüft).
2. **Text „Auszug ohne Namen“ (§3.8).** Gemessen: „There is already an object named 'produkte' in the database.“; „Column names in each table must be unique. Column name 'Email' in table 'kunden' is specified more than once.“; „Cannot find the object "gibtesnicht" because it does not exist or you do not have permissions.“ Im Code: 2714 gleich dem gemessenen Text ohne `'produkte'`; 2705 gleich dem ersten Satz; 4902 gleich dem gemessenen Text ohne `"gibtesnicht"`. Spezifikation, KDoc und Tabellenzeilen sagen „Auszug ohne die Namen (Setzung)“. Die Aussage geht nicht über den Beleg hinaus. Der 2705-Text ist ein Auszug (erster Satz), so benannt.
3. **`primary_key` optional und Rundlauf.** gefahren, siehe oben.
4. **Mehrzeilen-`UPDATE` und `MERGE` ohne Schlüssel.** `UPDATE`: Setzung im KDoc von `TableStore.update` („Setzung nach der Semantik von `UPDATE`, am Server nicht gemessen“) und im Test. `MERGE`: Grenze in `SPEC-036` Form M, in zwei Nutzer-Dokumenten und im Messprotokoll gekennzeichnet; der Code lehnt mit `TDS_UNSUPPORTED_SQL` ab, wie dort steht.
5. **Katalogabfrage der Tabelle ohne Schlüssel.** Test vorhanden (Tabellenliste, Spalten, Primärschlüssel-Abfrage mit `ResultStart` und `ResultEnd(0)`); d-migrate-Lauf `reverse` zeigt dasselbe am Draht.
6. **`ADD` mit doppelter Spalte innerhalb einer Anweisung.** Bau: `TDS_UNSUPPORTED_SQL` (Test führt `ALTER TABLE kunden ADD x INT, X BIT` unter den „keine Form“-Fällen). Der Server meldet dort sehr wahrscheinlich 2705 (nicht gemessen). **Nicht in der Spezifikation benannt**, nur im Test — V-2.
7. **[ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 3 und Folge-ADR.** Die ADR ist unverändert. Bewertung unten.

## Mutationen (selbst gefahren, `docker build --target test` in Kopien)

| Mutation | Wirkung |
|---|---|
| m1: `STATE_OBJECT_EXISTS` 6 auf 1 | rot: `ErrorMapperTest > Strukturabweisung ist ERROR 2714 State 6, 2705 State 4 und 4902 State 1 mit offener Verbindung` (1 von 1092 im Modul `:test`) |
| m2: Codes vertauscht (`OBJECT_EXISTS` und `COLUMN_EXISTS` in `StatementPlanner`) | rot: `TableStoreTest > eine doppelte Tabelle aendert den Bestand nicht` (1 von 114 im Modul `:testserver:test`); das Modul `:test` lief in dieser Kopie nach dem Abbruch nicht weiter (`SimulationBackendStructureTest` prüft die Paare, nicht gefahren) |
| m3: Ausnahme für leeren Schlüssel in `TableStore.insertBatch` entfernt | rot: `eine Tabelle ohne Schluessel nimmt gleiche Zeilen an`, `ein MERGE gegen eine Tabelle ohne Schluessel ist abgewiesen`, `onUpdate aendert auf einer Tabelle ohne Schluessel mehrere Zeilen` |
| m4: `primary_key` im Leser wieder Pflicht | rot: `SimDescriptionReaderTest > prüft den Schlüssel`, `TestserverStartTest > eine Datei mit Tabellen ohne Schluessel startet den Testserver` |
| m5: `require(primaryKey.isNotEmpty())` in `SimTable` | rot: 5 Tests im Modul `:testserver:test` (`prüft den Schlüssel`, die drei `TableStoreTest`-Fälle, der Starttest); die Tests von `SimModelTest` im Modul `:test` liefen nach dem Abbruch nicht |

Die Kopien liegen im Scratchpad (`.../scratchpad/verifier-katalog/m1` bis `m5`), der Arbeitsbaum des Zweigs ist unverändert.

## Neue Befunde

* **V-1 (INFO) — Spezifikation, Tabellenzeile 2714 und Text im Code stimmen überein.** Der Text hat nach dem Auszug die Form „named in the database“ (ohne Platzhalter); ein Leser der Antwort sieht keine Lücke. Kein Handlungsbedarf, nur Hinweis, dass der Text eine Auslassung statt eines Platzhalters trägt.
* **V-2 (LOW) — `ADD` mit doppelter Spalte in einer Anweisung ist als Grenze nirgends in der Spezifikation benannt.** Bau: `TDS_UNSUPPORTED_SQL` (Test vorhanden), Server: wahrscheinlich 2705, nicht gemessen. Der Slice (Aufgabe des Verifiers) verlangt die Benennung; im Diff steht sie nur im Test. Empfehlung: ein Halbsatz in `SPEC-036` Form D („Grenze: dieselbe Spalte zweimal in einem `ADD` ist `TDS_UNSUPPORTED_SQL`, der Server meldet vermutlich 2705, nicht gemessen“) oder ein Eintrag im Register; die Closure kann es nachziehen. Kein Blocker.
* **V-3 (LOW) — Form D nennt die abgelöste Entscheidung nur als „eine frühere Entscheidung“.** [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 3 steht nicht beim Namen (Rückverfolgung: ein Leser der Spezifikation findet die ADR nur über das Register). Empfehlung: `[ADR-0033](…)` Nr. 3 in den Satz setzen; die Spezifikation darf ADRs nennen. Kein Blocker.
* **V-4 (LOW) — Slice-Datei trägt noch den Stand „HEAD `85d970bd` … Review und Verifikation stehen aus“** (Abschnitt „Stand der Arbeit“ und Verantwortlich-Zeile). Der Planner zieht es bei der Closure nach (Zahl der Review-Findings, HEAD, Verifikationsbericht).
* **V-5 (INFO) — [ADR-0042](../plan/adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) (Zeile Tabelle der Folgen) sagt „`CREATE TABLE` ohne Schlüssel weist der Server heute ab“.** Die ADR ist `Accepted` und bleibt; die Aussage ist nach diesem Slice überholt und war dort als „Folge der Spezifikation“ gekennzeichnet. Kein Befund gegen den Slice; Hinweis für die Closure.
* **V-6 (INFO) — nicht gefahren, benannt:** Lauf gegen echten SQL Server (nicht verlangt), `data import`/`data export` an einer Tabelle ohne Schlüssel, frischer `make spec-abdeckung` (die Matrix im Diff trägt der Implementer, `make matrix-zitate` ist grün und prüft jedes Zitat auf PASSED), Wirkung der Nummer 2714 in d-migrate über den Text hinaus.

## Plan-Tabelle §3 gegen den Diff

Alle Dateien des Diffs (Produktcode, Tests, Spezifikation, drei Nutzer-Dokumente, Messprotokoll, `spec-abdeckung.md`) stehen in §3; die weiteren Dateien (Roadmap, Welle, Register, Links in `open/` und `done/`) sind der Link-Nachzug von `slice-mv` und der Merge von `main`, nicht Bau. `harness/README.md` ist nicht berührt (Zellen ≤ 200 Zeichen: unverändert, ohne Befund). Zweite Fundstellen: Grep über `spec/`, `docs/user/`, `docs/messungen/`, `src/`, `testserver/` nach „ohne Schlüssel“, „mit Schlüssel“, „Pflicht“ zu `primary_key`: keine überholte Aussage außer V-5 (Accepted-ADR) und der benannten Stelle zu [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md).

## Bewertung der Frage „Folge-ADR oder Klarstellung genügt?“ (Entscheidung beim Nutzer)

* [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 3 sagt, die Bibliothek „erkennt `CREATE TABLE` mit Schlüssel“; die Zusage der Entscheidung ist der Callback („ruft den Callback und antwortet“). „Mit Schlüssel“ beschreibt den damaligen Umfang, kein Verbot der Form ohne Schlüssel. Die Spezifikation (Rang 2) steht über der ADR (Rang 4) und ist Zielbild; bei Konflikt gewinnt sie, ohne die ADR zu überschreiben (`AGENTS.md` §3.5).
* Für **Klarstellung genügt**: kein Gate, keine Fläche und keine Schwelle hängt an „mit Schlüssel“; die Abweichung ist in der Spezifikation und im Register mit Träger benannt; ein `Supersedes` für einen Halbsatz brächte eine ADR mit Nummernpflege und Index.
* Für **Folge-ADR**: wer die ADR liest, findet die abgelöste Lesart; [ADR-0042](../plan/adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) trägt dieselbe Aussage („weist heute ab“) weiter.
* Meine Empfehlung: Klarstellung genügt, sofern `SPEC-036` Form D [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 3 beim Namen nennt (V-3) und das Register der Träger bleibt. Die Entscheidung bleibt beim Nutzer (die Frage steht in §6 des Slice).

## Bedingungen für die Planner-Closure

1. Nutzerentscheid zur Frage Folge-ADR oder Klarstellung (§6, letzte zwei Risiken); bei „Klarstellung“ V-3 nachziehen.
2. Haken der zwei Liefer-Punkte nach diesem Bericht (beide gedeckt) und der Nachweis-Pflichten (Review, Doku-Update, Gates).
3. §6: jedes Risiko erhält einen Ausgang. Vorschlag: „Auswirkung am fremden Client“ und „nicht gefahren im Bau“: eingetreten/entfallen zugunsten des Laufs in diesem Bericht (Ergebnis unverändert, Rundlauf grün); „Dateikonflikte“: entfallen (`main` eingemergt, `make gates` und `clean-build` grün nach dem Merge); „Messung trägt Referenzclient, nicht mssql-jdbc“: weiter offen (Treiber reicht die Meldung durch, Nummer nicht ausgewertet; `slice-abnahme-d-migrate` prüft erneut); `MERGE` ohne Schlüssel: weiter offen, Träger `slice-merge-antwort-an-messung-angleichen` §6; Folge-ADR: nach Entscheid; Tabelle ohne Schlüssel im Bestand: entfallen (Rundlauf gefahren).
4. §7 mit Steering-Loop-Lerneintrag; naheliegender Lerneintrag: ein Merge von `main` in einen Zweig kann einen semantischen Konflikt tragen, den kein Textkonflikt zeigt (Review F-5); „Wirkung am fremden Client“ gehört in die Verifikation, nicht in den Bau.
5. Register fortschreiben (`BEO-TDS/katalog-strukturabweisung-und-aenderungszaehler-weichen-vom-echten-server-ab`: Teil a gebaut, Teile b/c bei ihren Slices), Zeile M-19 im Messprotokoll bleibt „angeglichen“ (M-16 und M-20 bleiben bei ihren Slices); V-4 und V-5 nachziehen.
6. Optional vor der Zusammenführung: V-2 als Halbsatz in `SPEC-036` Form D.
7. Reihenfolge: erst Inhalt, dann reiner `git mv` nach `done/` (`AGENTS.md` §3.3); die Zusammenführung nach `main` führt der Auftraggeber aus.
