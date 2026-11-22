# Verifikationsbericht: slice-simulation-schemaonly-keyinfo — 2026-11-22

**Rolle:** Verifier (Modul 11), frischer Kontext; DoD- und Entscheidungs-Konformität, Plan-vs-Code-Diff. Kein Review, kein Fix, keine DoD-Haken, kein Schließen, nichts nach außen.

**Stand:** Branch `slice-simulation-schemaonly-keyinfo`, End-Stand `94253d06` (Re-Review des Baus; danach nur dieser Bericht); Arbeitsbaum vor allen Läufen sauber. Alle Läufe unten gelten für `94253d06`.

**Gegenstand:** Slice [slice-simulation-schemaonly-keyinfo](../plan/planning/done/slice-simulation-schemaonly-keyinfo.md); [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-05](../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen), [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität); [ADR-0059](../plan/adr/0059-schemaonly-ist-eine-erkannte-form-keyinfo-bleibt-abgewiesen.md) (Accepted 2026-11-22: Form F `SchemaOnly` erkannt, `KeyInfo` bleibt `TDS_UNSUPPORTED_SQL`; ersetzt [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md) Entscheidung 14, erster Satz, teilweise), [ADR-0058](../plan/adr/0058-or-in-der-auswahl-ganzzahlkonstante-und-waitfor-delay-sind-erkannt.md), [ADR-0042](../plan/adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md). Reviews: [Messphase](2026-11-22-slice-simulation-schemaonly-keyinfo-messung-review.md), [Re-Review Messphase](2026-11-22-slice-simulation-schemaonly-keyinfo-messung-re-review.md), [Bau](2026-11-22-slice-simulation-schemaonly-keyinfo-bau-review.md) (0/3/3/3), [Re-Review Bau](2026-11-22-slice-simulation-schemaonly-keyinfo-bau-re-review.md) (0/0/2/2, Empfehlung: weiter an den Verifier).

## Urteil

**DoD erfüllt, soweit sie vor der Closure erfüllbar ist; kein Befund mit Aktion für den Code, zwei LOW-Doku-Korrekturen (R-1, R-2 des Re-Reviews) stehen unverändert offen und werden hier nur gemeldet.** Alle Läufe sind Exit 0: `make clean-build`, `make e2e`, `make kompatibilitaet`, `make gates` (mit Stempel und `FORCE=1`), `make docs-check`, `make matrix-zitate`, `make vergleichslauf-d LAUF=live` und `LAUF=mitschnitt` (beide Referenzinstanzen 2022 und 2025). Die Vorhersagen des Implementers sind bestätigt: `schema-person` und `schema-typen` sind im Live-Lauf **gleich** der Referenz ohne Regel, `schema-person-schluessel` bleibt als Lücke mit dem Server-Wert `Number 50001, Class 16, State 1, 'Unsupported SQL.'`, `GetSchemaTable()` liefert am Serverprozess `Id` und `Name`, `Read()` keine Zeile, die Verbindung bleibt `Open`, `KeyInfo` endet mit `Number 50001`, der Mitschnitt-Lauf ist unberührt. Der bekannte Rot-Fall des Zusatztextes ` Operation cancelled by user.` (A-7) trat **nicht** ein: die Regel `cancel-mit-folgeabfrage` führt den Zusatztext in der Server-Spalte, der Schritt steht als „Ausnahme (Luecke)“ grün.

Die offenen Kästchen der DoD (Liefer-Punkte 2 und 3, `make gates`, Review, Closure-Notiz, Register, Risiko-Ausgänge, Paarungen) sind Sache des Planners bei der Closure; der Inhalt hinter den Liefer-Punkten ist belegt (siehe DoD-Tabelle).

## Läufe (je einmal auf dem End-Stand)

Messsperre `/tmp/claude-1000/-Development-tdswire/messlock`: beim Start hielt sie ein anderer Verifier (`slice-cancel-zusatztext-ursache-untersuchen`, seit 09:51:22); ich wartete mit einer Pausenschleife und legte die Sperre selbst an (`mkdir` erfolgreich, Datei `owner`) um **10:15:43**; Wartezeit rund 17 min. Die fremde Sperre habe ich nicht angefasst; meine gab ich nach dem letzten Lauf mit Referenzinstanz frei. `free -m` verfügbar vor und zwischen den Läufen 10,4 bis 14,8 GB (Grenze 4000 MB nie berührt).

| Sensor | Ergebnis | Dauer |
|---|---|---|
| `make clean-build` | **Exit 0** (Jar, Tests, detekt, `--no-cache`); alle Gradle-Stages `BUILD SUCCESSFUL`; 1586 Zeilen `PASSED`, keine rote Zeile (der eine Treffer auf `FAILED` ist der Testname `ResponseFailureTest > … FAILED_CLOSE_CONNECTION() PASSED`) | 653 s |
| `make e2e` | **Exit 0**, „gruen (9 Stufen, 468 s)“; Stufen 1 bis 9: 80, 100, 56, 50, 16, 18, 38, 17, 93 s, alle grün | 468 s |
| `make kompatibilitaet` | **Exit 0**, 15 bestandene Tests, 0 rote; Zeilen `messung: SchemaOnly: Spalten Id, Name; Zeile gelesen False; Verbindung Open` und `messung: KeyInfo: SqlException Number 50001, Class 16, State 1, Meldung 'Unsupported SQL.'`; der Test `AnmeldungTests > SchemaOnly_wird_beantwortet_und_KeyInfo_endet_mit_50001` ist `Passed` (auch in Stufe 5 von `e2e`), das Skript prüft die beiden `SET`-Texte im Log des Servers (`kompatibilitaetstest: ok   Log nennt die SET-Texte von SchemaOnly und KeyInfo`) | 24 s |
| `make gates` | **Exit 0**; die drei Gruppen (`werkzeug`, `tests`, `stempel`) kamen per **Stempel übersprungen** (Beleg des Implementers, Hash des Eingangs); deshalb zusätzlich `FORCE=1` | 11 s |
| `make gates FORCE=1` | **Exit 0**; 16 Gates, `gate-zuordnung` Selbsttest 33 Fälle, `stempel-eingaenge` ok, `stempel-selbsttest` 118, `stempel-eingaenge-selbsttest` 95, `stempel-abbild` 102, `stempel-gruppe` 67 Fälle, `d-check` 1767 Dateien 0 Befunde; die Stage `test` kam größtenteils aus dem Cache des eben gelaufenen `clean-build` | 51 s |
| `make docs-check` | **Exit 0**, `1767 Datei(en) geprüft, 0 Befund(e)` (vor diesem Bericht) | 3 s |
| `make matrix-zitate` | **Exit 0**, „1586 bestandene Testfaelle im Bericht, 408 verschiedene Zitate, 0 ohne PASSED, 0 nicht gelesene Zitat-Formen“ | 212 s |
| `make vergleichslauf-d LAUF=live` | **Exit 0**, `gruen`; je Instanz 25 Schritte: `live-2022` 57 s, `live-2025` 52 s | 119 s |
| `make vergleichslauf-d LAUF=mitschnitt` | **Exit 0**, `gruen`; `mitschnitt-2022` 23 s, `mitschnitt-2025` 28 s, „Mitschnitt-Vergleich: gleich oder genau die gelistete Abweichung“ | 58 s |
| `docker build --no-cache-filter test --target test referenztest-dotnet` (kein Gate, eigener Frischbeleg der .NET-Tests, weil `gates` sie aus dem Cache nahm) | **Exit 0**, `Passed!  - Failed: 0, Passed: 257, Skipped: 0, Total: 257` (ohne Filter, enthält `MessungSchemaBelegTests` und `MessungEinheitTests`) | 9 s |

Nicht gefahren, mit Grund: `make spec-abdeckung`: `docs/plan/spec-abdeckung.md` wurde zuletzt mit `f315db8e` erzeugt; seither ändert der Diff `f315db8e..HEAD` an Tests nur den KDoc von `SimulationBackendSchemaOnlyTest` (kein `@Test`, kein Tag), und die Matrix nennt `SchemaOnlyEmbeddingTest`, `ResponseSchemaOnlyTest`, `SchemaFormsTest`, `SimulationBackendSchemaOnlyTest` unter `SPEC-036` (306 bestandene Tests); sie passt zum HEAD. Keine Mutationen nachgefahren: der Re-Review hat elf Mutationen gefahren (M26 und M2, M3, M10, M15, M17, M21, M22, M23 des Belegs, dazu X3 und X4), alle rot; die Tabelle weist den Verifier für diese Änderungsart auf `clean-build` und `e2e` (Mutationsläufe sind Sache des Implementers über enge Testklassen).

## Live-Lauf, die erwarteten Zeilen

Ausgabe von `LAUF=live` (beide Instanzen gleich):

| Schritt | Referenz | Server | Urteil des Laufs |
|---|---|---|---|
| `schema-person` | `Id:int(4,10,255) NULL=False; Name:nvarchar(4000,255,255) NULL=True; Age:int(4,10,255) NULL=True; Score:decimal(17,10,2) NULL=True` | gleich | `ok gleich`, keine Regel |
| `schema-typen` | acht Spalten (`Id`, `Gross`, `Flag`, `Text`, `Preis`, `Tag`, `Uhr`, `Zeit`) | gleich | `ok gleich`, keine Regel |
| `schema-person-schluessel` | vier Spalten mit `Schluessel` und `Tabelle=person` | `SqlException Number 50001, Class 16, State 1, Meldung 'Unsupported SQL.'` | `ok Ausnahme (Luecke)`, gleich der Regel |
| `cancel-mit-folgeabfrage` | … `discarded.` | … `discarded. Operation cancelled by user.` | `ok Ausnahme (Luecke)` (A-7, Regel unverändert) |

Die Datei `live-ausnahmen.tsv` führt von den drei Regeln des Schema-Teils nur noch eine (`schema-person` und `schema-typen` entfallen), `schema-person-schluessel` bleibt mit Art *Lücke*, Grund „KeyInfo nicht gebaut; Traeger slice-simulation-keyinfo-erkennen“ und Verweis `SPEC-036, [ADR-0059](../plan/adr/0059-schemaonly-ist-eine-erkannte-form-keyinfo-bleibt-abgewiesen.md)` (Diff gelesen).

## DoD-Prüfung (§2 des Plans)

| DoD-Punkt | Befund |
|---|---|
| Liefer-Punkt 1, Messung am Draht (im Plan abgehakt) | **Erfüllt.** Ablage beider Instanzen mit je 44 `*.messung`-Dateien (6 neue je Instanz: `schemaonly-spalten`, `schemaonly-sonderfaelle`, `keyinfo-spalten`, `keyinfo-sonderfaelle`, `schemaonly-und-keyinfo`, `getschematable-ohne-verhalten`); `MessungSchemaBelegTests` und `MessungEinheitTests` im frischen .NET-Lauf grün (257 von 257); Mutationsbeleg und beide Reviews der Messphase liegen vor. |
| Liefer-Punkt 2, Bau von Form F | **Erfüllt im Code.** Alle Zusagen aus dem Plan sind am Diff und an den Läufen belegt: zwei Konstanten `SET_FMTONLY_ON`, `SET_FMTONLY_OFF` (Kennungen `0x00b9`, `0x00ba`, ohne `COUNT`, im RPC als `DONEINPROC` über `DoneCommand.inRpc` und `Completion.effectiveKind`); Erkennung in `SchemaForms.recognize` (beide führenden `SET` mit `;` Pflicht, schließendes `SET` optional, Auswahl genau S1); Antwort in `SchemaOnlyExecution` (zwei Abschlüsse, dann Spalten ohne Zeile, schließendes `SET` nur ohne Fehler; Fehler nach den zwei Abschlüssen; `close` und Cancel wirken auf die Auswahl); die Probe (Form B) bleibt (`parse(…, schemaOnly = false)`, Wächter X4 im Re-Review); Negativtests für `KeyInfo`, `SchemaOnly\|KeyInfo` und die nicht erkannten Folgen in `SimulationBackendSchemaOnlyTest` und `SchemaFormsTest`; Handvektoren der Messung in `ResponseSchemaOnlyTest`; Live-Regeln wie geplant (siehe oben). Der Mutationsbeleg `mutationen-schemaonly-form-f-2026-11-22.md` ist vorhanden (R-2 siehe unten). |
| Liefer-Punkt 3, Nachzug | **Erfüllt im Diff.** Hunks von `spec/spezifikation.md` in SPEC-002, SPEC-004, SPEC-030, SPEC-031, SPEC-036, SPEC-045, `TDS-FA-11.a`, „Offene Punkte des Backend-Vertrags“, SPEC-059 und Änderungsprotokoll; Matrix Nr. 11 und die Zeilen `SchemaOnly` und `KeyInfo`; `docs/user/` (drei Dateien); Messprotokoll (A-8 nur `KeyInfo`); `make kopplung-zahlen` und `make flaeche` laufen in `make gates` (`FORCE=1`) grün. |
| `make gates` Exit 0 und Sensoren je Art | **Erfüllt:** `make gates` (Stempel) und `make gates FORCE=1` Exit 0; `make docs-check` Exit 0; `make clean-build` und `make e2e` Exit 0 (je einmal, Verifier); `make matrix-zitate` Exit 0; `make spec-abdeckung` laut Plan einmal beim Implementer (Matrix passt zum HEAD, siehe oben). |
| Review durchgeführt | **Erfüllt:** vier Reports (Messphase, Re-Review, Bau, Re-Review); keine HIGH, keine offene MEDIUM. |
| Closure-Notiz, Register, Risiko-Ausgänge, Paarungen | **Offen, Sache der Closure.** §7 des Plans „Wird bei der Closure gefüllt“; die Risiken in §6 tragen „offen bis zur Closure“ oder „weiter offen“ (kein Risiko ohne Zeile); das Register `BEO-TDS/schemaonly-keyinfo-fmtonly-ohne-traeger` führt den Stand ohne den Live-Lauf (siehe offene Punkte); die drei Paarungen sind getragen: Folge-Slices `slice-simulation-keyinfo-erkennen`, `slice-simulation-spaltenflags-an-referenz-angleichen` und der Austragungs-Slice liegen in `open/`, das Register `BEO-TDS/spaltenflags-der-tabellenspalten-weichen-von-der-referenz-ab` ist angelegt. |

## Entscheidungs-Konformität

- **[ADR-0059](../plan/adr/0059-schemaonly-ist-eine-erkannte-form-keyinfo-bleibt-abgewiesen.md) Entscheidung 1 (`KeyInfo` nicht gebaut):** belegt. Der Text mit `SET NO_BROWSETABLE` ist als Ganzes nicht erkannt (`TDS_UNSUPPORTED_SQL`, 50001) — am Draht gesehen in `kompatibilitaet` (`KeyInfo: SqlException Number 50001`) und im Live-Lauf (`schema-person-schluessel`).
- **Entscheidungen 2 bis 5 (Erkennung, Antwort, Fehler, kein Zustand):** der Code folgt dem Wortlaut; gemessen ist die Antwortform (Stichprobe unten), die Grenzen sind in Spec, KDoc und Slice §6 als Setzung geführt.
- **Entscheidung 6 (Probe unverändert):** `SimulationBackendBulkTest`, `SimulationBackendDisjunctionTest` bleiben grün (clean-build); `e2e` Stufe 3 (Massenladen, Probe von `SqlBulkCopy`) grün.
- **Entscheidung 7 (zwei Konstanten):** `StatementKindEmbeddingTest` und `SchemaOnlyEmbeddingTest` grün; `make flaeche` grün (Typname `StatementKind` schon in SPEC-059).
- **[ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md) Entscheidung 14, erster Satz:** nur teilweise ersetzt, die Datei von [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md) ist im Diff unberührt (AGENTS.md §3.5); [ADR-0058](../plan/adr/0058-or-in-der-auswahl-ganzzahlkonstante-und-waitfor-delay-sind-erkannt.md) gilt weiter (`FixedForms`, `WaitForms` unverändert).
- **AGENTS.md §3.8:** nichts breiter als der Beleg gefunden (siehe Besondere Fragen 2).

## Zu den fünf besonderen Fragen

### (1) Trägt der Lauf die Einschränkung zu `IsReadOnly` ehrlich?

**Ja.** Der Live-Lauf vergleicht fünf Felder (Typname, Größe, Precision, Scale, Nullbarkeit); das steht im Messprotokoll („Server-Seite (Schema)“) und in Slice §6. Die Abweichung der Spaltenflags (`0x0008`/`0x0009` der Referenz in W-27, `0x0000`/`0x0001` der Bibliothek; `IsReadOnly=False` in allen 16 Schemazeilen je Instanz der Referenz, am Server „nicht gemessen“) steht in den „Grenzen“ des Messprotokolls, in W-27 (mit der Trennung von Nullbarkeits-Bit `0x0001` und unbekanntem Bit `0x0008`), im Register `BEO-TDS/spaltenflags-der-tabellenspalten-weichen-von-der-referenz-ab` und im Folge-Slice `slice-simulation-spaltenflags-an-referenz-angleichen`. Meine Läufe bestätigen die Grenze: „gleich“ im Live-Lauf heißt „gleich in diesen fünf Feldern“, nicht „gleich im Draht“; kein Satz in Spec, Matrix oder Nutzer-Doku behauptet das Gegenteil.

### (2) Die Setzungen nirgends als gemessen geführt?

**Bestätigt.** SPEC-036 (Absatz Form F) nennt Cancel vor dem Sammeln „Setzung, nicht gemessen“, den Fehler im RPC „nicht gemessen (Setzung)“, `FMTONLY` nach dem Fehler „Setzung, am Server nicht gemessen; W-29 zeigt nur, dass die Folgeabfrage … gewöhnlich ist“, die übrigen Bedingungen von S1 „Setzung des Nutzers, nicht gemessen“, unbekannte Spalte und Wert „Setzung“. Dieselben Aussagen stehen in `SchemaForms.kt`, `SchemaOnlyExecution.kt` (KDoc), im Test-KDoc und in Slice §6. Kein Satz führt eine dieser Setzungen als gemessen.

### (3) W-26 bis W-36 gegen die Ablage (Stichprobe)

Gelesen und gegen die Rohbytes der Ablage gehalten (`schemaonly-spalten` 2022, `schemaonly-sonderfaelle` 2022 und 2025, Diff beider Instanzen von `schemaonly-sonderfaelle` außer Anmeldung und Version leer):

- **W-26:** Text ` SET FMTONLY OFF; SET FMTONLY ON;SELECT Id, Name, Age, Score FROM person SET FMTONLY OFF;` (Leerzeichen am Anfang, kein `;` vor dem schließenden `SET`) im `nachricht c`-Block — stimmt.
- **W-27:** Antwort `fd01 00ba … fd01 00b9 … 81 …COLMETADATA… fd11 00c1 … fd00 00ba` (DONE `MORE` `0x00ba`, `0x00b9`, COLMETADATA, `MORE|COUNT` `0x00c1` mit Zähler 0, DONE ohne Bits `0x00ba`); Spaltenflags `0800` für `Id` (nicht nullbar) und `0900` für nullbare Spalten — stimmt mit `0x0008`/`0x0009`.
- **W-28:** RPC `sp_executesql` mit `@i int` und Text ohne schließendes `SET`; Antwort `ff01 00ba`, `ff01 00b9`, COLMETADATA, `ff11 00c1`, `79 …` (RETURNSTATUS), `fe00 00e0` (DONEPROC) — stimmt.
- **W-29:** Antwort `fd01 00ba`, `fd01 00b9`, `aa …` (ERROR 208, „Invalid object name 'gibtesnicht'.“), `fd02 00fd`, kein viertes DONE, kein SESSIONSTATE; Folgeabfrage liefert `81 01 … d1 … fd10 00c1` — stimmt.

Die Texte W-30 bis W-36 (`KeyInfo`, `GetSchemaTable()`) habe ich nicht Byte für Byte nachgelesen; sie liegen in `MessungSchemaBelegTests` (frisch grün) und gehören zum Folge-Slice `KeyInfo`.

### (4) Hinweis G-8 (kein Befund dieses Slice)

`BulkForms.kt` und SPEC-036 setzen, dass `SET FMTONLY ON SELECT 2` (Form C hinter der Probe) die Zeile liefert; sie führen das als **Setzung** („am Server nicht gemessen“, Absatz „Ungemessene Setzungen“), nicht als Messung. Die parallele Messung G-8 im Worktree `slice-qa-messung-anweisungsformen-grenzen` (COLMETADATA ohne Zeile hinter `SET FMTONLY ON`) widerspricht dieser Setzung; die Angleichung gehört dem Folge-Slice `slice-simulation-fmtonly-hinter-set-angleichen`, nicht diesem Slice (Hinweis des Re-Reviews R-4, hier bestätigt).

### (5) Merge-Hinweise

Die Zweige ändern dieselben neun Dateien. `git merge-tree` meldet **Inhalts-Konflikte** in `docs/plan/spec-abdeckung.md` (Fassung von `main` übernehmen, nur neu erzeugen, wenn Tests oder Tags sich geändert haben — hier ja), `docs/user/einbettung-und-betrieb.md`, `docs/user/testserver-manuell.md`, `spec/spezifikation.md` und `tools/mk/vergleichslauf-d.mk`. **Still und ohne Konflikt** merged `referenztest-dotnet/Mitschnitt/MessungBelegTests.cs`: beide Zweige ändern dieselbe Zeile gleich (`Die_Ablage_hat_38_Messungen…` → `…_44_Messungen…`, `Assert.Equal(38, …)` → `Assert.Equal(44, …)`), Git nimmt das ohne Konflikt an — nach beiden Merges liegen aber 50 Dateien je Instanz vor (38 + 6 + 6), der Test bliebe bei 44 und wäre rot (und der Name mit „44“ falsch). Der Nachzieher setzt die Zahl **von Hand auf 50** und prüft `MessungWireTests.cs` (merged ohne Konflikt) und die Liste `abweichend` der Datei, die der andere Zweig ändert, am zusammengeführten Stand. Dieses stille Zusammenfallen ist neu gegenüber dem Hinweis des Auftrags „44→50“ und lässt sich nur durch den Lauf der .NET-Tests nach dem Merge entdecken.

## Plan-vs-Code-Diff (`git diff main...HEAD`)

63 Dateien, +3703/−151. Gegen §3 des Plans (drei Tabellen) gelesen: jede Datei unter `src/` und `referenztest-dotnet/` steht in einer der Tabellen; zusätzlich zu den geplanten Dateien liegen die in „Dateien des Baus, die die Tabelle oben nicht nennt“ aufgeführten (`SchemaForms.kt`, `SchemaOnlyExecution.kt`, vier Testklassen, `AnmeldungTests.cs`, `kompatibilitaetstest.sh`, Mutationsbeleg) — erklärt. `SimulationBackend.kt` wird auf mehr als „falls nötig“ umgebaut (Auszug von `bulk` nach `FixedAnswers.kt` und `answer(...)` als eigene Funktion wegen der Funktionszahl der Klasse): Verhalten gleich (Reihenfolge der Zweige, Tests grün); der Plan nennt den Auszug in der Tabelle „Dateien des Baus“. Kein `@Suppress`, kein Host-Aufruf von `gradle` (detekt in `clean-build` und `gates` grün). `spec/architecture.md`, `spec/lastenheft.md` und Accepted-ADRs sind im Diff unberührt (außer der neuen [ADR-0059](../plan/adr/0059-schemaonly-ist-eine-erkannte-form-keyinfo-bleibt-abgewiesen.md) samt Index).

## Abweichungen und offene Fragen

**Abweichungen von der DoD: keine.** Zwei Doku-Korrekturen aus dem Re-Review stehen **unverändert offen** (melde ich, ändere ich nicht):

- **R-1 (LOW):** Plan §3 (Zeile zu `StatementParser.kt`, `BulkForms.kt` …) nennt „`parseInProbe`“, die Funktion gibt es nicht mehr (Umbau `3363fdd8` zu `parse(…, schemaOnly)`); `grep -rn parseInProbe src docs spec` trifft nur diese Plan-Zeile und den Re-Review-Text selbst. Der Planner soll den Namen im Plan nachziehen.
- **R-2 (LOW):** `docs/messungen/mutationen-schemaonly-form-f-2026-11-22.md` Zeile 55 nennt „67 Testfälle grün“ für „die sieben Klassen aus `:test` (ohne die zwei Einbettungstests)“; es sind 66 (die 67 ergeben sich nur mit `SchemaOnlyEmbeddingTest`). Die Wirkung der Mutation ist richtig beschrieben.

Weitere Beobachtungen ohne Aktion im Code:

1. **Zustandszellen nennen noch „Live-Lauf nach dem Bau nicht belegt“:** `state.md` des Registers `schemaonly-keyinfo-fmtonly-ohne-traeger`, Messprotokoll (Absatz „Server-Seite (Schema)“: „ein Lauf von `make vergleichslauf-d LAUF=live` nach dem Bau von Form F liegt in diesem Protokoll nicht vor“) und die Zeile `SchemaOnly` der Kompatibilitätsmatrix („der Lauf des Tests gegen den Serverprozess … ist hier nicht belegt“). Beide Läufe liegen jetzt vor (`live` 2022 und 2025, `kompatibilitaet` Anmeldung, je Exit 0). Ob das Protokoll sie aufnimmt und die Zellen bei der Closure nachgeführt werden, entscheidet der Planner; die Zellen sagen heute wahr, was im Repo steht.
2. **Gates mit Stempel:** `make gates` ohne `FORCE=1` überspringt alle drei Gruppen; der Verifier-Beleg ist deshalb `FORCE=1`. Der .NET-Teil kam auch dort aus dem Layer-Cache; der eigene Frischlauf der Stage `test` ist oben genannt.
3. **Nicht gemessen und nicht verifiziert (bleibt im Rahmen der Setzungen):** das Verhalten der Form F bei Cancel, Fehler im RPC und `FMTONLY` nach einem Fehler gegen einen echten SQL Server; `GetSchemaTable()` für andere Clients als `Microsoft.Data.SqlClient` 7.1.1; was der Client bei den Flags des Servers (`0x0000`/`0x0001`) für `IsReadOnly` meldet. Träger: `BEO-TDS/messung-am-echten-sql-server-fehlt`, `BEO-TDS/festlegung-gilt-nur-fuer-das-clientprofil`, `slice-simulation-spaltenflags-an-referenz-angleichen`.

## Gesamtlaufzeit und größte Posten

Gesamt rund 55 Minuten Wanduhr (Beginn 09:55, Ende 10:50), davon rund 17 Minuten Warten auf die Messsperre eines anderen Verifiers (kein Arbeitsaufwand). Größte Zeitposten der Läufe: `make clean-build` 653 s, `make e2e` 468 s, `make matrix-zitate` 212 s, `make vergleichslauf-d LAUF=live` 119 s, `LAUF=mitschnitt` 58 s, `make gates FORCE=1` 51 s. Der Rest war Lesen von Plan, [ADR-0059](../plan/adr/0059-schemaonly-ist-eine-erkannte-form-keyinfo-bleibt-abgewiesen.md), Diff, Messprotokoll, Ablage und Reviews. Last des Hosts während der Läufe: Lastmittel 7,8 bis 14,4 (andere Läufe liefen parallel), Speicher verfügbar nie unter 10 GB.
