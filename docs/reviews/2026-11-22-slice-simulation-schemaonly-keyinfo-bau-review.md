# Review-Report: slice-simulation-schemaonly-keyinfo (Bau von Form F, Liefer-Punkte 2 und 3) — 2026-11-22

**Review-Art:** Code, Tests, Nachzug von Spec, Matrix, Messprotokoll und Nutzer-Doku — gegen Plan (Slice §1 bis §6), [ADR-0059](../plan/adr/0059-schemaonly-ist-eine-erkannte-form-keyinfo-bleibt-abgewiesen.md), [ADR-0058](../plan/adr/0058-or-in-der-auswahl-ganzzahlkonstante-und-waitfor-delay-sind-erkannt.md), SPEC-031, SPEC-035, SPEC-036, SPEC-059 und `AGENTS.md` §3.1 bis §3.8. Nicht gegen die DoD (Sache des Verifiers).

**Gegenstand:** `git diff 7def244b..HEAD` des Zweigs `slice-simulation-schemaonly-keyinfo` (HEAD `527cef82`): `5d9f93fa` (Form F in `src/main`, Tests, `live-ausnahmen.tsv`), `43141595` (Negativtexte, `AnmeldungTests.cs`, `tools/kompatibilitaetstest.sh`), `1a784ea3` (Nachzug Spec, Matrix, Messprotokoll, Nutzer-Doku, Register, Mutationsbeleg, `spec-abdeckung`), `527cef82` (KDoc-Zeilenlänge). 33 Dateien, +1220/−99 Zeilen.

**Skill:** `.harness/skills/reviewer.md` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-22

**Eingangs-Kontext:** Diff; [ADR-0059](../plan/adr/0059-schemaonly-ist-eine-erkannte-form-keyinfo-bleibt-abgewiesen.md) (Accepted 2026-11-22, Nutzerentscheidung: nur `SchemaOnly`, zwei Konstanten, alle Bedingungen von S1, beide Abschlüsse vor dem Fehler, RPC-Form, schließendes `SET` optional, `KeyInfo` abgewiesen); [ADR-0058](../plan/adr/0058-or-in-der-auswahl-ganzzahlkonstante-und-waitfor-delay-sind-erkannt.md); `spec/lastenheft.md` (`TDS-FA-11`, `TDS-QA-01`); Messprotokoll W-26 bis W-36 mit den Messdateien `referenztest-dotnet/Mitschnitt/messung/{2022,2025}/`; `AGENTS.md` (Hard Rules); der Report der Messphase (`2026-11-22-slice-simulation-schemaonly-keyinfo-messung-review.md`) und sein Re-Review als vorherige Findings am selben Gegenstand.

**Ausgeführte Sensoren** (Docker, Wegwerf-Kontexte im Scratchpad `…/scratchpad/rv/`, Image der Stufe `deps`, ohne Netz, `--continue`; nichts davon im Repo):

1. Grundlauf der neun Klassen des Mutationsbeleges (`:test` 65 Testfälle, `integratorTest` 7): grün (BUILD SUCCESSFUL, 1 m 37 s einschließlich Bau).
2. Wegwerf-Test `RvProbeTest` (nicht im Repo) mit 17 Eingabetexten der Erkennung und zwei Läufen am Backend: Befund F-1.
3. Neun Mutationen (Tabelle unten): M2, M3, M10, M15, M17, M21, M22, M23 aus dem Mutationsbeleg des Implementers und eine eigene (X1). Alle rot.
4. `make flaeche`: Exit 0 (Selbsttest und Prüfung).
5. Abgleich der Handvektor-Bytes der Tests gegen die Messdateien beider Instanzen (`grep` je Hexkette), Abgleich Plan-Tabelle §3 und Kopf „Berührte Spec-Stellen“ gegen `git diff --stat` und die Überschriften der Hunks von `spec/spezifikation.md`, `grep` nach `@Suppress`, `ADR-`/`slice-` in den Spec-Hunks, nach Zeilen über 120 Zeichen in `src/main`.

Nicht gefahren (Auftrag und Tabelle `harness/README.md` §Minimal agent workflow): `make gates`, `make clean-build`, `make e2e`, `make kompatibilitaet`, `make vergleichslauf-d`, `make spec-abdeckung`, `make docs-check`, `make matrix-zitate`. Keine Messsperre angelegt; `free -m` verfügbar 9,3 bis 13,2 GB, frei 4,2 bis 5,0 GB (Grenze 4000 MB gehalten). Last des Hosts hoch (Lastmittel 17 bis 23, fremde Läufe).

## Findings

### F-1 — MEDIUM
- `kategorie`: MEDIUM
- `quelle`: [ADR-0059](../plan/adr/0059-schemaonly-ist-eine-erkannte-form-keyinfo-bleibt-abgewiesen.md) Entscheidung 2 und 6, SPEC-036 (Form F), `AGENTS.md` §3.8
- `pfad`: `src/main/kotlin/io/github/pt9912/tdswire/simulation/BulkForms.kt` · „else -> metadataOnly(read(tokens.subList(FMTONLY_LENGTH, tokens.size).withoutOff()))“; zugehörige Zusage in `SchemaForms.kt` · „überschneidet sich nicht mit Form F“ und in `spec/spezifikation.md` SPEC-036 · „überschneidet sich nicht mit Form F“
- `befund`: Die Probe liest den Rest hinter `SET FMTONLY ON` mit `::parse`, und `parse` ruft jetzt `SchemaForms.recognize`; `metadataOnly` gibt jede andere Erkennung unverändert weiter. Gefahren: `SET FMTONLY ON SET FMTONLY OFF; SET FMTONLY ON; SELECT Id FROM person` und dieselbe Folge mit schließendem `SET FMTONLY OFF` sind `Recognition.SchemaOnly`; das Backend antwortet mit `SET_FMTONLY_OFF`, `SET_FMTONLY_ON`, Spalten, `ResultEnd` (das schließende `SET` entfällt still: `closed=false`). ADR-0059 Entscheidung 2 nennt „eine dritte `SET`-Anweisung“ und `SET FMTONLY ON` vor der Form als nicht erkannt (`TDS_UNSUPPORTED_SQL` ohne Abschluss), die Spec und das KDoc sagen, die Probe überschneide sich nicht mit Form F. Dieselbe Rekursion gilt für den `sp_executesql`-Text der Probe (gelesen, nicht gefahren). Failure-Szenario: ein Text mit vorangestelltem `SET FMTONLY ON` wird beantwortet statt abgewiesen (alles oder nichts verletzt, ungemessener Text mit Antwort statt Abweisung). Kein Test deckt die Überschneidung (`SchemaFormsTest > die Probe ist nicht Form F …` prüft nur die zwei Richtungen mit je einem Text); die Probe selbst bleibt grün.
- `verifizierbar`: ja (Wegwerf-Test der Erkennung und des Backends, siehe Sensor 2; ein Handtest mit den zwei Texten genügt)
- `klasse`: Erkennung einer Nachbarform über die Rekursion einer anderen Form nimmt Text an, den die Spec ausschließt

### F-2 — MEDIUM
- `kategorie`: MEDIUM
- `quelle`: [ADR-0059](../plan/adr/0059-schemaonly-ist-eine-erkannte-form-keyinfo-bleibt-abgewiesen.md) Entscheidung 3, [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität), `AGENTS.md` §3.8 (Zusage ohne Träger), Skill „Aussage breiter als ihr Beleg“
- `pfad`: `src/test/kotlin/io/github/pt9912/tdswire/protocol/response/ResponseSchemaOnlyTest.kt` · „die Spaltenflags `0x0008` der Referenz schreibt die Bibliothek für keine“; `docs/messungen/referenzabgleich.md` · „die Spaltenflags `0x0008` der Tabellenspalten schreibt er auch für eine Auswahl ohne `SET` nicht, sie gehören nicht zu dieser Angleichung“
- `befund`: Das COLMETADATA der Tests stammt nicht aus der Messung: `colmetadata(columns)` ist die Ausgabe der Bibliothek, verglichen wird Bibliothek mit Bibliothek (die DONE-Bytes sind zeichengleich mit beiden Instanzen, geprüft; COLMETADATA, ERROR-Token und der Abschluss `fd1000c1…` ohne `MORE` sind es nicht). Die Referenz schreibt für jede Tabellenspalte `0x0008`/`0x0009` (`usUpdateable` unbekannt, `TdsType.kt` KDoc; `schemaonly-spalten/01/spaltenform`), die Bibliothek `0x0000`/`0x0001` (`extraFlags` setzt im Simulationspfad nur `BulkCopyProbe`). Folge am Client: die Referenz meldet in `GetSchemaTable()` `IsReadOnly=False` (alle 16 Schemazeilen der Messdatei `schemaonly-spalten`), der Server nach dem Muster von W-33 (Flags ohne die Bits `0x0C`, `IsReadOnly=True`) voraussichtlich `True`; am Server nicht gemessen. Der Live-Lauf vergleicht das Feld nicht (`LiveSzenarioTests.SchemaSchritt`: fünf Felder), also bleibt `schema-person` ohne Regel grün, während Slice-Ziel („`GetSchemaTable()` wie die Referenz“) und ADR-Kontext („Abweichung des Servers ist Schuld“) die Abweichung nicht ausschließen. Die Abweichung steht neutral im Messprotokoll (Grenzen), mit Folge für den Client weder dort noch in der Spec (SPEC-045 nennt die fünf Felder ehrlich), und sie hat keinen Träger: keine Regel in `live-ausnahmen.tsv`, kein Register-Eintrag (`grep` über `observations/`, `open/`, `carveouts/`: nichts zu Spaltenflags oder `IsReadOnly`), kein Folge-Slice. Sie ist älter als dieser Slice (jede Auswahl aus einer Tabelle), wird aber hier an der Befehlsart sichtbar, deren einziger Zweck `GetSchemaTable()` ist.
- `verifizierbar`: ja (Mutation der Flags im Test; Lauf des Live-Laufs mit `IsReadOnly` in `SchemaSchritt` beim Verifier; nicht gefahren)
- `klasse`: Bekannte Abweichung vom Zielbild an der Grenze der Angleichung ohne Träger; Handvektor-Test vergleicht den Encoder mit sich selbst

### F-3 — MEDIUM
- `kategorie`: MEDIUM
- `quelle`: Skill „Plan-Tabelle nennt die berührten Dateien nicht“ (Klasse „Plan-Tabelle gegen Diff nicht abgeglichen“, im Report der Messphase F-4 schon einmal)
- `pfad`: `docs/plan/planning/in-progress/slice-simulation-schemaonly-keyinfo.md` · „**Berührte Spec-Stellen:**“ und „`src/main/kotlin/io/github/pt9912/tdswire/simulation/StatementParser.kt`, `BulkForms.kt`, `StatementPlanner.kt`, `FixedForms.kt`, `FixedAnswers.kt`“
- `befund`: Abgleich gegen den Diff. Datei berührt, in keiner Tabelle: `src/main/kotlin/io/github/pt9912/tdswire/simulation/Statement.kt` (neues `Recognition.SchemaOnly`) und `docs/plan/planning/observations/BEO-TDS/schemaonly-keyinfo-fmtonly-ohne-traeger/state.md` (nur die DoD nennt das Register). In der Tabelle, unberührt: `FixedForms.kt`. Spec-Stellen berührt, im Kopf nicht genannt: SPEC-002 (Aufzählung `StatementKind`), SPEC-004 (Testprofil, Zeile „`SET`-Text bei KeyInfo“), SPEC-030 (der Absatz „Offen“ steht dort; der Kopf nennt ihn ohne Kennung), `TDS-FA-11.a` (Ablauf, zwei Hunks), „Offene Punkte des Backend-Vertrags“ (Befehlskennung des Update-Zählers). Im Kopf genannt, unberührt: SPEC-035. Der Negativbefund „keine Datei darüber hinaus“ gilt erst nach diesem Abgleich.
- `verifizierbar`: ja (`git diff --stat 7def244b..HEAD` gegen §3; Hunk-Überschriften von `spec/spezifikation.md`)
- `klasse`: Plan-Tabelle gegen Diff nicht abgeglichen

### F-4 — LOW
- `kategorie`: LOW
- `quelle`: `AGENTS.md` §3.8 (Test pinnt ungemessenes Verhalten ohne Kennzeichnung)
- `pfad`: `src/test/kotlin/io/github/pt9912/tdswire/simulation/SimulationBackendSchemaOnlyTest.kt` · „Cancel vor dem Sammeln beendet die Auswahl vor dem Ergebnis (die Abschlüsse der `SET` stehen davor und danach, ein Ergebnis fehlt)“
- `befund`: Der Test erwartet nach einem Cancel vor dem Sammeln `[off, on, off]`: `SchemaOnlyExecution` hängt das schließende `SET` an, auch wenn die Auswahl abgebrochen wurde (`closed && !failed`; ein Abbruch zählt nicht als Fehler). Weder ADR-0059 noch SPEC-036 sagen etwas zu Cancel in Form F, am Draht ist es nicht gemessen; der KDoc kennzeichnet es nicht als Setzung. Am Draht verwirft die Sequenzierung den Rest bei ATTENTION voraussichtlich ohnehin; das Backend-Ereignis ist trotzdem festgeschrieben.
- `verifizierbar`: ja (Test und `SchemaOnlyExecution` lesen)
- `klasse`: Test hält ungemessenes Verhalten als Erwartung ohne Setzungs-Kennzeichen

### F-5 — LOW
- `kategorie`: LOW
- `quelle`: `AGENTS.md` §3.8 (Aussage ungenauer als der eigene Beleg)
- `pfad`: `docs/messungen/referenzabgleich.md` · „Spaltenflags `0x0008` (nicht nullbar) und `0x0009` (nullbar)“ (W-27)
- `befund`: Die Klammern nennen `0x0008` „nicht nullbar“. Die Nullbarkeit trägt das Bit `0x0001`; `0x0008` ist in beiden Werten das Bit `usUpdateable` unbekannt (`TdsType.kt` KDoc, `TypeInfoWriter.FLAG_NULLABLE = 0x0001`). Die Messdateien sind richtig (`spaltenform`-Zeilen), die Deutung im Protokoll nicht; sie verdeckt den Befund aus F-2.
- `verifizierbar`: ja (Messdatei `schemaonly-spalten`, Zeile `spaltenform`)
- `klasse`: Deutung von Flagbits im Messprotokoll widerspricht dem KDoc des Encoders

### F-6 — LOW
- `kategorie`: LOW
- `quelle`: `AGENTS.md` §3.7 (Zustandsfeld, Dauerdokument nennt eine Rolle statt des Zustands)
- `pfad`: `docs/plan/kompatibilitaetsmatrix.md` · „der Lauf des Tests gegen den Serverprozess (`make kompatibilitaet KATEGORIE=Anmeldung`) liegt beim Verifier und ist hier nicht belegt“; `docs/plan/planning/observations/BEO-TDS/schemaonly-keyinfo-fmtonly-ohne-traeger/state.md` · „Lauf von `make vergleichslauf-d LAUF=live` steht beim Verifier“
- `befund`: Die Zellen nennen, bei wem ein Lauf liegt, statt den Zustand (belegt/nicht belegt) mit einem Anker. Nach der Verifikation sind beide Sätze falsch, ohne dass ein Gate es meldet. Der Teilsatz „hier nicht belegt“ ist Zustand und trägt; „liegt beim Verifier“ ist Prozess. Die Zeile A-8 des Messprotokolls nennt den fehlenden Live-Lauf als Zustand und ist in Ordnung.
- `verifizierbar`: nein
- `klasse`: Rollen- oder Prozess-Text in Zustandsfeld eines Dauerdokuments

### F-7 — INFO
- `kategorie`: INFO
- `quelle`: Skill „Mutationsbeschreibung nennt die gedachte statt der gemessenen Wirkung“, „Messzahl aus einem Erzeuger, der nicht im Repo liegt“
- `pfad`: `docs/messungen/mutationen-schemaonly-form-f-2026-11-22.md` · „Die Skripte der Mutationen liegen **nicht im Repo**“
- `befund`: Die acht nachgefahrenen Mutationen des Beleges liefern dieselben roten Testfälle wie dort (siehe Tabelle); die Zahl „x von 72“ stimmt (65 `:test` plus 7 `integratorTest`). Der Beleg kennzeichnet den Erzeuger ehrlich als nicht im Repo; die Zahlen sind damit nachprüfbar, aber nicht aus dem Repo erzeugbar (Grenze benannt). Keine Aktion erwartet.
- `verifizierbar`: ja
- `klasse`: Mutationsbeleg ohne Erzeuger im Repo, Wirkung bestätigt

### F-8 — INFO
- `kategorie`: INFO
- `quelle`: Beobachtung `BEO-TDS/austragung-aus-der-registrierung-ohne-test-bei-select-change-merge` (Folge-Slice `slice-simulation-austragung-der-ausfuehrungen-testen` im Anspruch)
- `pfad`: `src/main/kotlin/io/github/pt9912/tdswire/simulation/SchemaOnlyExecution.kt` · „Cancel und `close()` wirken auf [inner], die Ausführung der Auswahl, die das Backend unter Sitzung und Anfrage führt.“
- `befund`: Die Form F trägt keine eigene Registrierung: Cancel erreicht die Auswahl über `running`, `close()` leitet weiter (M17 rot). Belegt sind `close()` und Cancel; das normale Ende ohne `close()` und der Fehlerzweig (`failed(...)`, nie registriert) tragen keine Austragungs-Behauptung in einem Test dieses Slice. Die Lücke ist dieselbe Klasse wie die der Beobachtung und nicht neu; die Austragung der Form F ändert nichts an `SelectExecution.onCompletion`.
- `verifizierbar`: nein
- `klasse`: Austragung aus der Registrierung nur für `close()` und Cancel belegt

### F-9 — INFO
- `kategorie`: INFO
- `quelle`: [ADR-0059](../plan/adr/0059-schemaonly-ist-eine-erkannte-form-keyinfo-bleibt-abgewiesen.md) Entscheidung 4 (Setzung), `AGENTS.md` §3.8
- `pfad`: `spec/spezifikation.md` · „nicht gemessen, es gilt die Regel von `SPEC-031` mit den zwei vorangehenden Abschlüssen“
- `befund`: Der Fehler der Auswahl im RPC ist als Setzung gekennzeichnet und hat keinen Test (nur Batch, `ResponseSchemaOnlyTest > Bei einer unbekannten Tabelle …`; der Mutationsbeleg führt ihn unter „Nicht mutiert“). Die Kennzeichnung trägt; kein Befund, nur der Hinweis, dass die Zusage ohne Beleg und ohne Test steht.
- `verifizierbar`: nein
- `klasse`: Setzung ohne Test, korrekt gekennzeichnet

## Befunde der Nachrechnung (Schwerpunkte 1 bis 7)

**(1) Erkennung `SchemaForms.kt`.** Gefahren (17 Texte, Wegwerf-Test): beide führenden `SET` mit `;` Pflicht und in Reihenfolge `OFF`, `ON`; Option exakt `FMTONLY`, Zustand exakt; Schreibweise gleichgültig (`set fmtonly off ; … from [person]`); Zeilenenden `\r\n`, Tabulator und Zeilenkommentar vor dem schließenden `SET`; `SET FMTONLY OFF;` am Ende; ein `;` vor dem schließenden `SET`, ein zweites schließendes `SET`, `WHERE (Id = 1)`, Mischung `AND`/`OR`, `EXEC sp_executesql N'SELECT 1'` hinter den `SET`, ein weiteres `SET FMTONLY ON` hinter der Auswahl und eine zweite Auswahl: alle nicht erkannt. `OR`-Folge erkannt und zur UND-Folge mit `Constant(false)`. Ein Name mit Schema (`sys.columns`) wird erkannt und erst im Planer abgewiesen (wie bei S1). Befund nur F-1 (Überschneidung mit der Probe über die Rekursion); kein Regress der Probe (`SimulationBackendBulkTest` 7 von 7 grün), die ADR-0058-Formen (Ganzzahlkonstante, `WAITFOR`) hinter den `SET` sind abgewiesen (Test und Wegwerf-Test).

**(2) Antwort `SchemaOnlyExecution.kt`.** Reihenfolge `OFF`, `ON`, Auswahl, optional `OFF` (M12, M13, M14 im Beleg; M15 hier rot). Fehlerfall: beide Abschlüsse, dann `Failure`, kein schließendes `SET` (M15 rot, 2 Testfälle), kein SESSIONSTATE (Token-Liste des Tests vollständig). Cancel und `close()` wirken über `running`/`inner` (M17 rot). Zeilenquelle nie geöffnet (`probe.opens == 0` in allen Backend-Tests; Konstantenterm aus `withoutRows`, M16 im Beleg). Befund nur F-4 und F-8.

**(3) Token- und Completion-Ebene.** `DoneCommand.forKind` bildet `SET_FMTONLY_ON` auf `0x00b9`, `…_OFF` auf `0x00ba` ab, `countsRows` falsch; `Completion.effectiveKind` lässt die zwei Arten nur im RPC durch (M21 rot, M22 rot, M23 rot: im Massenladen bleibt `0x00f0`); `SessionTransaction` unverändert (nicht im Diff), Test `Die Arten von SET FMTONLY aendern die Transaktion …` belegt es. Öffentliche Fläche: `StatementKind` wächst um zwei Konstanten am Ende der Aufzählung; `make flaeche` Exit 0; `StatementKindEmbeddingTest` nennt beide; das Änderungsprotokoll von SPEC-059 nennt die Mitglieder (Skill-Punkt „Mitglieder eines Typs der Liste“ erfüllt).

**(4) Handvektoren gegen die Messdateien.** Die DONE-/DONEINPROC-Hexketten und `RETURNSTATUS DONEPROC` der Tests stehen zeichengleich in `schemaonly-spalten.messung` und `schemaonly-sonderfaelle.messung` beider Instanzen (2022 und 2025, je Kette gezählt, Treffer in beiden). Nicht aus der Messung: COLMETADATA, ERROR-Token, `fd1000c1…` (als Setzung benannt), und die Spaltenflags — siehe F-2 und F-5. Der Implementer-Befund „COLMETADATA nicht aus der Messung, Flags `0x0008` gegen `0x0000`“ ist richtig und trägt weiter, als der KDoc sagt (Folge `IsReadOnly`).

**(5) Ausnahmelisten.** `schema-person` und `schema-typen` sind entfernt; `schema-person-schluessel` bleibt als Art `Lücke` mit Grund „`KeyInfo` nicht gebaut“, Träger `slice-simulation-keyinfo-erkennen`, Verweis `SPEC-036, ADR-0059`; die Zeile hat sechs durch Tabulator getrennte Spalten wie die übrigen. Der Selbsttest der Liste (`make skript-selbsttest`) wurde nicht gefahren (Klasse `werkzeug`, nicht Pflicht der Änderungsart). Der Abschnitt A-8 des Messprotokolls nennt, dass beide entfernten Schritte nur fünf Felder vergleichen (ehrlich); der Lauf von `make vergleichslauf-d LAUF=live` nach dem Bau liegt nicht vor (ausgesprochen; Sache des Verifiers).

**(6) Mutationen** (Docker, engste Klassen, `--continue`; Zahl der roten Testfälle aus der Ausgabe gezählt, 65 `:test` plus 7 `integratorTest`; ein Lauf 69 bis 107 s einschließlich Bau, zwei Läufe parallel, hohe Last des Hosts):

| Nr | Mutation (Ort) | Wirkung (gefahren) | Roter Testfall |
|---|---|---|---|
| M2 | `SchemaForms.hasPrefix`: `;` hinter dem ersten `SET` nicht geprüft | rot, 2 | `SchemaFormsTest > lehnt jede andere Folge ab`; `SimulationBackendSchemaOnlyTest > Texte ausserhalb der Form F …` |
| M3 | `hasPrefix`: `;` hinter dem zweiten `SET` nicht geprüft | rot, 2 | dieselben zwei Testfälle |
| M10 | `StatementPlanner.plan`: `closed` immer falsch | rot, 8 (7 `:test`, 1 `integratorTest`) | `SchemaOnlyEmbeddingTest > schemaOnlySchliesstMit …`; `StatementPlannerTest > Form F plant die Auswahl …`; fünf Testfälle in `SimulationBackendSchemaOnlyTest` |
| M15 | `SchemaOnlyExecution`: schließendes `SET` auch nach Fehler | rot, 2 | `SimulationBackendSchemaOnlyTest > SchemaOnly auf eine unbekannte Tabelle …`; `… > Andere Fehler der Auswahl …` |
| M17 | `SchemaOnlyExecution.close` ruft `inner.close()` nicht | rot, 1 | `SimulationBackendSchemaOnlyTest > close und cancel wirken auf die Auswahl der Form F` |
| M21 | `Completion.effectiveKind`: Ausnahme der zwei Arten im RPC entfällt | rot, 2 | `ResponseSchemaOnlyTest > Im RPC tragen die zwei SET …`; `ResponseStatementKindTest > Im RPC gelten nur die zwei Arten …` |
| M22 | `DoneCommand.inRpc` gilt für jede Art ohne Zeilenzahl (`!countsRows`) | rot, 2 | `ResponseStatementKindTest > Im RPC gelten nur …`; `… > Im RPC und im Massenladen gilt die Kennung der Strukturaenderung nicht` |
| M23 | `effectiveKind`: Ausnahme gilt auch im Massenladen | rot, 1 | `ResponseStatementKindTest > Im RPC gelten nur die zwei Arten …` |
| X1 (eigene) | `DoneCommand.inRpc` nur für `SET_FMTONLY_ON` | rot, 2 | `ResponseSchemaOnlyTest > Im RPC tragen die zwei SET …`; `ResponseStatementKindTest > Im RPC gelten nur …` |

M2 und M3 sind nach der Ergänzung der Negativtexte rot (im Beleg ein erster grüner Lauf von M2 benannt). Die Zahlen decken sich mit dem Mutationsbeleg. Nicht nachgefahren: M1, M4 bis M9, M11 bis M14, M16, M18 bis M20, M24, M25 (Belegtabelle gelesen, plausibel gegen die Tests).

**(7) Nachzug.** Spec: kein `ADR-`, `slice-`, `welle-` in den Hunks (`grep`); die Form-F-Aussagen kennzeichnen Setzung gegen gemessen konsequent (Bedingungen von S1, `;` vor dem schließenden `SET`, Fehler im RPC, `FMTONLY` als Zustand nach einem RPC ohne schließendes `SET`); Cancel in Form F wird nirgends behauptet (siehe F-4). Zählung der Arten „Neun“ und „die neun Arten ab `CREATE_TABLE` außer `MERGE`“ in Spec und Nutzer-Doku stimmt mit der Aufzählung überein; die zweite Fundstelle `docs/user/einbettung-und-betrieb.md` ist fortgeschrieben, `StatementKindEmbeddingTest` nennt „die vierzehn Konstanten“. Nutzer-Doku: die drei Dateien lesen den Ist-Zustand; keine der Wörter „bisher“, „noch nicht“, „soll“, „seit Slice“ in den neuen Zeilen (`grep`, Treffer nur in Link-Ankern mit „vorläufig“). Kein vorzeitiger Haken in der DoD (`git diff` des Slice: nur die neue Tabelle). `AnmeldungTests.cs` (Verbindung bleibt `Open`, die Schemazeile wird gelesen, `KeyInfo` weiter 50001) und `tools/kompatibilitaetstest.sh` (nur Kommentar) sind in sich stimmig; die Matrix zitiert den Testfall ohne die Form `…Test > …` (kein Fund für `make matrix-zitate`). Befunde: F-3, F-5, F-6.

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/domain/StatementKind.kt` | geprüft, ohne Befund (zwei Konstanten am Ende, KDoc nennt Messpunkte und den Zustand im Indikativ) |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/token/Token.kt`, `protocol/response/Completion.kt`, `ResponseSequencer.kt` | geprüft, ohne Befund (Kennungen, `countsRows`, `inRpc`, BULK unverändert; KDoc trägt Zusage und Kopplung) |
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/SchemaOnlyExecution.kt`, `SimulationBackend.kt`, `FixedAnswers.kt` | geprüft, Befunde F-4, F-8 (Umzug von `bulk` nach `FixedAnswers` ohne Verhaltensänderung, `BulkTargetResult` import sauber) |
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/SchemaForms.kt`, `StatementParser.kt`, `StatementPlanner.kt`, `Statement.kt`, `BulkForms.kt` | geprüft, Befund F-1, F-3 |
| `src/test/…/SchemaFormsTest.kt`, `SimulationBackendSchemaOnlyTest.kt`, `StatementPlannerTest.kt`, `ResponseStatementKindTest.kt` | geprüft, Befund F-4 (Negativtexte decken ADR-0059 Fitness Function ab) |
| `src/test/…/ResponseSchemaOnlyTest.kt`, `src/integrator/…/SchemaOnlyEmbeddingTest.kt`, `StatementKindEmbeddingTest.kt` | geprüft, Befund F-2 |
| `referenztest-dotnet/Vergleich/live-ausnahmen.tsv`, `referenztest-dotnet/Kompatibilitaet/AnmeldungTests.cs`, `referenztest-dotnet/Mitschnitt/MessungSchemaBelegTests.cs`, `tools/kompatibilitaetstest.sh` | geprüft, ohne Befund (Spaltenzahl der Zeile, Test und Kommentar stimmig; F-2 betrifft das Fehlen eines Feldes im Live-Vergleich, nicht diese Dateien) |
| `spec/spezifikation.md` | geprüft, Befund F-3, F-9 (Rang: keine ADR-Bezüge nach unten) |
| `docs/plan/kompatibilitaetsmatrix.md`, `docs/messungen/referenzabgleich.md`, `docs/user/*.md`, Register `state.md`, `docs/plan/spec-abdeckung.md` | geprüft, Befunde F-5, F-6 (`spec-abdeckung.md` nicht neu erzeugt; nur gelesen: neue Testfälle stehen, Namen unverändert nach dem KDoc-Commit) |
| `docs/messungen/mutationen-schemaonly-form-f-2026-11-22.md` | geprüft, Befund F-7 |
| Suppression (`@Suppress`, `@SuppressWarnings`, `noqa`, `nolint`, `SuppressMessage`), Host-Aufrufe von `gradle`/`java`, Zeilen über 120 Zeichen in `src/main` | geprüft, ohne Befund |
| Accepted-ADR (`docs/plan/adr/`) | nicht berührt, geprüft, ohne Befund |
| `spec/architecture.md`, `spec/lastenheft.md` | nicht berührt, geprüft, ohne Befund |

## Summary

**HIGH 0 · MEDIUM 3 · LOW 3 · INFO 3.**

Der Bau trägt die Zusagen von ADR-0059: Erkennung, Reihenfolge der Abschlüsse, Fehlerfall, Kennungen und die Ausnahme im RPC sind durch Tests belegt, die DONE-Bytes sind zeichengleich mit der Messung beider Instanzen, und alle neun gefahrenen Mutationen machen mindestens einen Testfall rot (Zahlen wie im Mutationsbeleg). Die drei MEDIUM-Findings sind: F-1 eine Überschneidung der Probe mit Form F über die Rekursion, die Spec und KDoc ausschließen und kein Test hält; F-2 die Spaltenflags (`0x0000` statt `0x0008`) mit der Folge `IsReadOnly` an `GetSchemaTable()`, die der Live-Lauf nicht vergleicht und die weder Ausnahme noch Träger hat; F-3 eine Plan-Tabelle, die `Statement.kt` und fünf Spec-Stellen nicht nennt.

**Empfehlung:** Nachbesserung vor dem Verifier für F-1 (Code und Test) und F-3 (Plan-Tabelle, Kopf); für F-2 eine Entscheidung (Ausnahme, Folge-Slice oder Register-Eintrag) beim Architect/Planner, kein Umbau des Baus nötig; F-4 bis F-6 sind Kleinigkeiten ohne Gate. Weil F-1 Produktionscode berührt, ist ein Re-Review der Nachbesserung nach dem Skill-Punkt „Nachbesserung mit Code ohne zweiten Review“ nötig, sonst Vermerk in der Closure-Notiz §7. Über die Behebung entscheidet der Implementer; ein Finding wird nicht herabgestuft, weil jemand widerspricht.
