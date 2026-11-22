# Review-Report: slice-simulation-schemaonly-keyinfo (Messphase, Liefer-Punkt 1) — 2026-11-22

**Review-Art:** Code, Tests, Ablage und Messprotokoll — gegen Plan (Slice §1 bis §3), [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md) Entscheidung 7, `AGENTS.md` §3.7 und §3.8. Nicht gegen die DoD (Sache des Verifiers).

**Gegenstand:** `git diff main...HEAD` des Zweigs `slice-simulation-schemaonly-keyinfo` (HEAD `0c702389`): Commit `e1ff218f` (Messung, Auswertung mit `mitSpaltenform`, Ablage `referenztest-dotnet/Mitschnitt/messung/{2022,2025}/`, `MessungWireTests.cs`, `MessungSchemaBelegTests.cs`, `MessungEinheitTests.cs`, `MessungBelegTests.cs`, `tools/mk/vergleichslauf-d.mk`) und `0c702389` (Messprotokoll `docs/messungen/referenzabgleich.md`, Abschnitt „`SchemaOnly`, `KeyInfo` und `GetSchemaTable()` am Draht“, W-26 bis W-36, Ist-Stand im Slice §1). 21 Dateien, 1196 Zeilen.

**Skill:** `.harness/skills/reviewer.md` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-22

**Eingangs-Kontext:** Slice §1 bis §6 (`slice-simulation-schemaonly-keyinfo`), `spec/lastenheft.md` (`TDS-FA-11`, `TDS-QA-01`), [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md), `AGENTS.md` (Hard Rules), `harness/README.md` §Minimal agent workflow; frühere Reports am selben Gegenstand (Messreihe am Draht): `slice-decimaln-laenge-an-messung-angleichen`, `slice-simulation-spaltenlaenge-in-auswahl`, `slice-simulation-anweisungsformen-erweitern` (nur die Klassen der Findings gelesen).

**Ausgeführte Sensoren** (Docker, Wegwerf-Kontexte im Scratchpad: `git archive HEAD` von `referenztest-dotnet/` mit einem `Dockerfile`, dessen `DOTNET_TEST_ARGS` auf die drei Testklassen `MessungSchemaBelegTests`, `MessungEinheitTests`, `MessungBelegTests` filtert; nichts davon im Repo; `free -m` frei 4392 bis 4943 MB, verfügbar 12 bis 13 GB):

1. Grundlauf der drei Testklassen, Stage `test` ohne Netz: 123 Testfälle grün (2 s je Lauf, 7 s je Bau einschließlich Cache).
2. Auswertung der Ablage mit einem Wegwerf-Test (nicht im Repo): Zählung und Ausgabe aller Auswertungszeilen und Beobachtungen der 2022er Ablage, Abgleich mit dem Messprotokoll (Abschnitt „Befunde der Nachrechnung“).
3. Neun Mutationen (M1 bis M9) mit den engsten Testklassen, je 7 bis 10 s (Tabelle „Mutationen“); dazu ein Wegwerf-Test zur Lesegrenze von `TABNAME`.
4. Stage `vergleichslauf` (nur Bau, `dotnet build --no-restore`, ohne Referenzinstanz): `Build succeeded`, 0 Warnungen.
5. `make docs-check` Exit 0 (1755 Dateien, 0 Befunde).
6. `git diff --name-status main...HEAD -- referenztest-dotnet/Mitschnitt/messung`: nur 12 Zeilen `A`.

Nicht gefahren (Auftrag und Tabelle `harness/README.md` §Minimal agent workflow): `make gates`, `make clean-build`, `make e2e`, `make kompatibilitaet`, `make vergleichslauf-d` (Referenzinstanzen), `make spec-abdeckung`. Keine Messsperre angelegt. Gesamtlaufzeit des Reviews: etwa 11 Minuten (gemessen bis vor dem Schreiben dieses Reports 9,5 Minuten); größte Posten: Lesen von Diff, Slice und Messprotokoll sowie Nachrechnen der Rohzeilen; die Docker-Läufe sind klein (Bau 6 bis 8 s je Lauf, neun Mutationen und drei Wegwerf-Läufe zusammen unter 2 Minuten).

## Findings

### F-1 — MEDIUM
- `kategorie`: MEDIUM
- `quelle`: Skill „Eigencode, dessen Entfernung keinen Test rot macht“, `AGENTS.md` §3.8
- `pfad`: `referenztest-dotnet/Mitschnitt/MessAuswertung.cs` · „if (gesamt != ulong.MaxValue) texte?.Add(pname + "=" + Roh(Encoding.Unicode.GetString(plp.ToArray())));“ (NVARCHAR-PLP-Zweig in `EinRpc`)
- `befund`: Der PLP-Zweig der neuen Zeile `anfragetext` (Text eines `nvarchar(max)`-Parameters) ist neu im Diff, und seine Streichung macht keinen Test rot: Mutation M5 (Zeile und Sammeln der Teile entfernt) lässt alle 123 Testfälle grün. Der KDoc der Option sagt „ungekürzt“; `sp_executesql` mit einem Text über 4000 Zeichen läuft über diesen Pfad, und keine der sechs Messungen und kein Einheitstest erreicht ihn (der Einheitstest nutzt `NvarcharParam`, den Pfad ohne PLP).
- `verifizierbar`: ja (Mutation M5)
- `klasse`: Neuer Lesepfad ohne beobachtenden Test

### F-2 — MEDIUM
- `kategorie`: MEDIUM
- `quelle`: Skill „Eigencode, dessen Entfernung keinen Test rot macht“
- `pfad`: `referenztest-dotnet/Mitschnitt/TokenLeser.cs` · „tabellen.Add("nicht lesbar");“ und „eintraege.Add("nicht lesbar");“ (Fehlerzweige von `TabName` und `ColInfo`)
- `befund`: Beide Fehlerzweige sind neu, und ihr Ersatz durch `throw;` lässt alle 123 Testfälle grün (M6, M7). Der Einheitstest „Der_Leser_zerlegt_Spaltenflags_TABNAME_und_COLINFO_mit_den_Bytes_der_Messung“ belegt nur den Fall, dass die Länge über das Datenende reicht (`Nimm` außerhalb des `try`), nicht den Zweig, der `nicht lesbar` schreibt.
- `verifizierbar`: ja (Mutationen M6, M7)
- `klasse`: Neuer Fehlerzweig ohne beobachtenden Test

### F-3 — MEDIUM
- `kategorie`: MEDIUM
- `quelle`: `AGENTS.md` §3.8, Skill „Aussage breiter als ihr Beleg“
- `pfad`: `referenztest-dotnet/Mitschnitt/MessungSchemaBelegTests.cs` · „jede Aussage des Messprotokolls zu A-8 (<c>docs/messungen/referenzabgleich.md</c>) hat hier eine Zeile.“
- `befund`: Der KDoc der Klasse sagt mehr, als die Tests tragen; ohne Zeile bleiben: (a) „`GetSchemaTable()` löst keine weitere Anfrage aus“ (W-36; keine Prüfung der Zahl der Austausche je Szenario), (b) die Gleichheit der Schemazeile für `typen` (acht Spalten) und für den Schlüsselfall mit den Werten der Referenz (geprüft sind nur `person`, vier Spalten), (c) der Inhalt von SESSIONSTATE (`0:9=0x20608114ffe7ffff00`, Länge 16, Folge 1; geprüft ist nur der Tokenname), (d) die Ausnahme am Client bei `KeyInfo` auf eine unbekannte Tabelle (nur bei `SchemaOnly` über `Ausgang(...)` geprüft), (e) „zwischen `SchemaOnly` und `KeyInfo` unterscheiden sich allein `IsKey`, `BaseTableName`, `IsAliased`, `IsExpression`, `IsHidden`“ (W-36; je ein Test prüft nur die eine Seite). Die Ablage trägt (a) bis (e) (nachgerechnet, siehe unten); die Tests führen sie nicht.
- `verifizierbar`: ja (Abgleich der Zusagen von W-34, W-36 gegen die Asserts der Klasse)
- `klasse`: Test-KDoc „jede Aussage hat eine Zeile“ breiter als die Zeilen

### F-4 — MEDIUM
- `kategorie`: MEDIUM
- `quelle`: Skill „Plan-Tabelle nennt die berührten Dateien nicht“ (die Klasse „Plan-Tabelle gegen Diff nicht abgeglichen“ steht schon in früheren Reports, zuletzt `slice-simulation-spaltenlaenge-in-auswahl`, `slice-simulation-anweisungsformen-erweitern`)
- `pfad`: `docs/plan/planning/in-progress/slice-simulation-schemaonly-keyinfo.md` · „| `referenztest-dotnet/Vergleich/MessungWireTests.cs`, `referenztest-dotnet/Vergleich/LiveSzenarioTests.cs`, `referenztest-dotnet/Vergleich/live-ausnahmen.tsv` | update | Messszenarien für `SchemaOnly` und `KeyInfo`, drei Regeln entfallen |“ (§3)
- `befund`: Der Abgleich der Tabelle gegen `git diff --stat main...HEAD` ergibt berührte Dateien ohne Zeile: `referenztest-dotnet/Mitschnitt/TokenLeser.cs`, `referenztest-dotnet/Mitschnitt/MessAuswertung.cs`, `referenztest-dotnet/Mitschnitt/MessungSchemaBelegTests.cs` (neu), `referenztest-dotnet/Mitschnitt/MessungEinheitTests.cs`, `referenztest-dotnet/Mitschnitt/MessungBelegTests.cs`, `referenztest-dotnet/Mitschnitt/messung/2022/` und `2025/` (je sechs neue Dateien), `tools/mk/vergleichslauf-d.mk`. Die Spec-Stelle SPEC-045 steht im Kopf; `LiveSzenarioTests.cs` und `live-ausnahmen.tsv` sind unverändert (nach Plan dem Bau vorbehalten).
- `verifizierbar`: ja (`git diff --stat main...HEAD` gegen §3)
- `klasse`: Plan-Tabelle gegen Diff nicht abgeglichen

### F-5 — MEDIUM
- `kategorie`: MEDIUM
- `quelle`: Skill „Zahl, Feldmenge oder Zuordnung steht an einem zweiten Ort ohne Sensor“, `AGENTS.md` §3.7 (Klasse Kopplung)
- `pfad`: `docs/messungen/referenzabgleich.md` · „472 Zeilen der Auswertung und 107 Beobachtungen des Clients je Instanz verglichen“ (Kopf der Spalte „Befund“ der Tabelle W-26 bis W-36) und „sie sind **gleich** den Werten der Referenz in `live-ausnahmen.tsv`“ (W-36)
- `befund`: Die Zahlen 472 und 107 stehen nur im Messprotokoll; der Test `Beide_Instanzen_haben_in_allen_sechs_Szenarien_…` prüft nur `a.Count > 20` und die Gleichheit, nicht die Zahlen (nachgerechnet: 472 und 107 je Instanz stimmen heute). Ebenso liest kein Test `live-ausnahmen.tsv`: die Werte der Referenz stehen als Kopie in den Asserts, und ein Kopplungskommentar steht auf keiner Seite.
- `verifizierbar`: ja (Zählung im Wegwerf-Test; `grep` nach den Zahlen in `referenztest-dotnet`)
- `klasse`: Zahl an zweitem Ort ohne Sensor

### F-6 — LOW
- `kategorie`: LOW
- `quelle`: `AGENTS.md` §3.7 (ein Kommentar beschreibt, was da ist; Zustandsfelder)
- `pfad`: `docs/plan/planning/in-progress/slice-simulation-schemaonly-keyinfo.md` · „`KeyInfo` erst nach der Messung der Antwort am Draht, weil die Antwortform unbekannt ist (gemessen am 2026-11-22, siehe Ist-Stand: TABNAME und COLINFO)“ und „(gemessen am 2026-11-22, nächster Punkt)“ (§1)
- `befund`: Die Empfehlung nennt die Antwortform „unbekannt“ und im selben Satz „gemessen“; der Zustand (Antwortform gemessen, Quelle W-26 bis W-36) steht an der Stelle nicht im Indikativ. „nächster Punkt“ ist ein Positionsverweis auf einen Absatz, kein auflösbarer Anker.
- `verifizierbar`: nein
- `klasse`: Zustand und Vorzustand im selben Satz

### F-7 — LOW
- `kategorie`: LOW
- `quelle`: Maintainability (Grenze des Lesers, `AGENTS.md` §3.8)
- `pfad`: `referenztest-dotnet/Mitschnitt/TokenLeser.cs` · „private void TabName(Antwort a)“ und „private void ColInfo(Antwort a)“ (Lesen innerhalb von `ende`)
- `befund`: Beide Leser begrenzen das Lesen nicht auf `ende`, sondern nur auf das Ende des Puffers. Ein TABNAME mit Länge 5 und Zähler 2 (Wegwerf-Test) ergibt `TABNAME Tabellen [a.]` statt „nicht lesbar“, weil der zweite Namensteil aus den Bytes des Folge-Tokens gelesen wird; erst das Folge-Token bricht die Zerlegung ab. Bei den gemessenen Strömen tritt das nicht auf (alle 44 Dateien je Instanz ohne `PROBLEM`).
- `verifizierbar`: ja (Wegwerf-Test an `TokenLeser.Lesen`)
- `klasse`: Lesegrenze des Tokens nicht durchgesetzt

### F-8 — INFO
- `kategorie`: INFO
- `quelle`: Skill „Messzahl aus einem Erzeuger, der nicht im Repo liegt“ (Abgrenzung)
- `pfad`: `docs/messungen/referenzabgleich.md` · „unter der Messsperre des Hosts (kein anderer Messlauf des Repos zur selben Zeit)“
- `befund`: Die Messsperre ist keine Datei des Repos und kein Gate (`/tmp/claude-1000/-Development-tdswire/messlock`, nur im Slice §4 genannt); das Protokoll belegt sie durch Behauptung. Die Zahlen der Laufzeit (67 s, Lastmittel 3,5) stammen aus der Ausgabe des Laufs, deren Erzeuger (`make vergleichslauf-d LAUF=wire`) im Repo liegt; ihr Wert ist hier nicht nachgefahren (Referenzinstanzen nicht Teil des Auftrags). Keine Aktion; Verifier kann die Aussagen nur als Angabe des Implementers lesen.
- `verifizierbar`: nein
- `klasse`: Umgebungsangabe ohne Sensor

### F-9 — INFO
- `kategorie`: INFO
- `quelle`: Skill „Haken vor der Verifikation gesetzt“ (Abgrenzung), `harness/README.md` §Minimal agent workflow
- `pfad`: `docs/plan/planning/in-progress/slice-simulation-schemaonly-keyinfo.md` · „- [ ] **Messung am Draht:**“ (§2) und `docs/plan/spec-abdeckung.md`
- `befund`: Kein DoD-Punkt trägt ein `- [x]` (richtig, Review und Verifikation stehen aus). `docs/plan/spec-abdeckung.md` ist nicht im Diff; die neuen Tests tragen `@spec SPEC-045`, die Tabelle `harness/README.md` verlangt `make spec-abdeckung` einmal bei neuen Tests; der Lauf steht in der DoD-Zeile der Gate-Läufe aus. `docs/user/` ist von der Messphase nicht berührt (kein Verhalten geändert).
- `verifizierbar`: nein
- `klasse`: Hinweis auf offenen Sensorlauf

### F-10 — INFO
- `kategorie`: INFO
- `quelle`: Maintainability (Beleg gestärkt)
- `pfad`: `referenztest-dotnet/Mitschnitt/messung/2022/keyinfo-spalten.messung` und `.../2025/keyinfo-spalten.messung` · „nachricht s“
- `befund`: Die Aussage „beide Instanzen gleich in allen Austauschen nach der Anmeldung“ trägt über die Auswertung hinaus: die `nachricht`-Zeilen (Rohbytes) aller sechs Szenarien sind in 2022 und 2025 bis auf PRELOGIN-Antwort, PRELOGIN und LOGIN-Antwort (Indizes 0, 1, 3) byte-gleich (nachgerechnet, SESSIONSTATE eingeschlossen).
- `verifizierbar`: ja (Vergleich der `nachricht`-Zeilen)
- `klasse`: Aussage durch Rohbytes gedeckt

## Befunde der Nachrechnung (Schwerpunkt 1 bis 5)

**(1) Messprotokoll gegen Ablage.** Die Auswertung der 2022er Ablage (alle 562 Zeilen mit Anmeldung) und die 107 Beobachtungen wurden gegen jede Zeile von W-26 bis W-36 und gegen die 39 Rohzeilen gelegt. Gleich, ohne Abweichung:

- Tokenfolgen und Befehlscodes: `DONE DONE COLMETADATA DONE DONE` mit `0x00ba`, `0x00b9`, `0x00c1` (MORE|COUNT, Zähler 0), `0x00ba` (`schemaonly-spalten/01..03`, `schemaonly-sonderfaelle/03`); `DONEINPROC`-Form mit `DONEPROC 0x00e0` (`/04`); `DONE DONE ERROR DONE` mit `0x00fd`, ohne SESSIONSTATE (`/05`); `KeyInfo`: Zähler des `0x00c1`-`DONE` gleich der Zeilenzahl in allen zehn `KeyInfo`-Batches (`keyinfo-spalten/01..04`: 3, 2, 3, 3; `keyinfo-sonderfaelle/04..09`: 1, 0 (leere Tabelle), 1, 3, 3, 1); `schemaonly-und-keyinfo`: `0x00ba 0x00b9 0x00b9 0x00c1 0x00ba 0x00ba`.
- COLINFO-Status `0x08`, `0x00`, `0x18` (nicht ausgewählte Schlüsselspalte als letzte Spalte, Zeilen `('Ada',36,1)`), `0x28` mit Name `Id` (Bytes `0x0101280249006400020100`); zusammengesetzter Schlüssel `0x08 0x08 0x00`; ohne Schlüssel alle `0x00`; Ausdruck: weder TABNAME noch COLINFO, Spaltenflags `0x0020`.
- TABNAME-Bytes: `person` `0x01060070…6e00`, `dbo.person` `0x020300640062006f00…`, `typen`, `wire_schema_ohne_k`, `wire_schema_zwei_k`; SESSIONSTATE `Laenge 16, Folge 1, wiederherstellbar 1, [0:9=0x20608114ffe7ffff00]`.
- `NO_BROWSETABLE` bleibt: `keyinfo-sonderfaelle/11` und `/12` und `schemaonly-und-keyinfo/03` tragen `TABNAME COLINFO`; nach `keyinfo-spalten/01..04` trägt die Folgeabfrage `/05` keine. Der RPC `/06` (ohne schließendes `SET`) stört den Schluss nicht: `/07` ist ein Batch mit schließendem `SET NO_BROWSETABLE OFF` und setzt vor `/10` zurück; `schemaonly-und-keyinfo/01..03` zeigt dasselbe ohne RPC. `FMTONLY` bleibt nicht (`schemaonly-sonderfaelle/06`: `COLMETADATA ROW+ DONE`).
- Schemazeilen: `person` (`ColumnSize` 4, 4000, 4, 17; Precision 10, 255, 10, 10; Scale 255, 255, 255, 2; `AllowDBNull` False, True, True, True) und `typen` (acht Spalten, `bigint(8,19,255)` bis `datetime2(8,255,7)`) gleich den Zeilen `schema-person` und `schema-typen` der `live-ausnahmen.tsv`; zwischen `SchemaOnly` und `KeyInfo` unterscheiden sich nur `IsKey`, `BaseTableName`, `IsAliased`, `IsExpression`, `IsHidden` (an allen 31 Feldern der Schemazeile verglichen); `IsUnique` ist in allen 30 `KeyInfo`-Spaltenzeilen der Ablage `False`. `0x04` im COLINFO und `IsExpression=True` kommen nicht vor, wie das Protokoll sagt.
- Instanzgleichheit: 472 Auswertungszeilen und 107 Beobachtungen je Instanz (Wegwerf-Zählung ohne `/00/`); Rohbytes ab Austausch 1 byte-gleich (F-10).
- Instanz- und Clientkopf: Digests, `16.0.4305.5`, `17.0.5035.5`, Client `7.1.1` auf .NET `10.0.14` gleich dem Kopf der Dateien; die Aufnahme trägt das Datum 2026-11-22; keine Rechnername-Bytes (`legion7i16` als UTF-16) in den zwölf neuen Dateien.

Offene Bedenken zum Protokoll: nur die in F-3, F-5 und F-8 genannten. Die Aussagen sind durchgehend an Client, Version und Szenario gebunden; die Grenzen (andere Clients, Verbunde, Identität, `0x04`, `0x0020` über die Zuordnung hinaus) stehen im selben Abschnitt.

**(2) Leser und Auswerter.** `TABNAME`: Länge, je Tabelle ein Byte Namensteile und US_VARCHAR je Teil; `COLINFO`: je Eintrag drei Bytes und bei Bit `0x20` ein B_VARCHAR; beide wie in den Bytes der Messung. `_p` wird nach dem Token auf `ende` gesetzt, die Zerlegung der folgenden Tokens ist unverändert. Regressionsschutz: `Nimm(U16())` und der neue Zweig verbrauchen dieselben Bytes; die 38 früheren Dateien laufen ohne `PROBLEM` (`MessungBelegTests`, 44 Dateien je Stufe, grün); `mitSpaltenform: false` erzeugt keine neue Zeile (Einheitstest, M3 rot) und die Felder der übrigen Zeilen sind nicht verändert (der Auswerter schreibt nur zusätzliche Zeilen). Offen: F-1, F-2, F-7.

**(3) Mutationen** (je ein Lauf der drei Testklassen im Wegwerf-Kontext; Wirkung gemessen):

| Nr | Mutation | Gemessene Wirkung |
|---|---|---|
| M1 | `COLINFO`: Spaltenname bei Status `0x20` nicht gelesen (fester Text) | 3 rot: Einheitstest Spaltenform, Alias-Beleg (2022, 2025) |
| M2 | `TABNAME`: Namensteile mit `/` statt `.` verbunden | 3 rot: Einheitstest Spaltenform, Alias/`dbo.person`-Beleg (2022, 2025) |
| M3 | `mitSpaltenform` ignoriert (`if (true)`) | 1 rot: `Die_Auswertung_gibt_mit_mitSpaltenform_…` |
| M4 | Schlüsselbit `0x08` durch `0x10` ersetzt | 9 rot: Einheitstest, acht Belege |
| M5 | PLP-Text nicht erfasst | **0 rot** (F-1) |
| M6 | `TabName`: Rückfallzweig durch `throw;` ersetzt | **0 rot** (F-2) |
| M7 | `ColInfo`: Rückfallzweig durch `throw;` ersetzt | **0 rot** (F-2) |
| M8 | `Roh` (Zeilenwechsel als Escape) durch Leerzeichen-Ersatz ersetzt | 1 rot: `Die_Auswertung_gibt_mit_mitSpaltenform_…` |
| M9 | `COLINFO`: Schleifenbedingung `_p + 3 <= ende` zu `<` | 11 rot |

Der Grundlauf ist grün (123). Die Belegtests der Ablage lesen die Dateien und rotten bei jeder Änderung der Ablage oder des Lesers; sie belegen, dass die Ablage die Aussage trägt, und der KDoc nennt das (Grenze, kein Befund).

**(4) Reproduzierbarkeit.** Szenarien legen ihre Tabellen unter je Szenario verschiedenen Namen an (`wire_schema_ohne_s`, `wire_schema_ohne_k`, `wire_schema_zwei_k`), wie `wire_batch*`; die Reihenfolge der Szenarien trennt `NO_BROWSETABLE` je Verbindung (Pooling aus, eine Verbindung je Szenario). Die Referenzinstanz ist je Lauf frisch (das Protokoll nennt den verworfenen ersten Lauf mit Fehler 2714 und seine Ursache). Die Messsperre ist F-8. `MessungWireTests.cs` baut (Stage `vergleichslauf`, 0 Warnungen).

**(5) Ablage.** `git diff --name-status main...HEAD` unter `Mitschnitt/messung/`: zwölf Zeilen `A`, keine `M`, keine `D`; die 38 früheren Dateien je Instanz sind gegen den Merge-Base byte-unverändert. Die neuen Dateien tragen im Kopf `testschema.sql sha256:514ef7f7…` (die 38 früheren `01d752a7…`, der Stand des Testschemas zur Zeit ihrer Aufnahme); der Aufbau der Messreihe hat denselben Hash `152d9787…`. Das ist keine Abweichung.

**(6) Kommentare und Doku-Links.** KDoc von `TabName`, `ColInfo`, `MessAuswertung` und der Belegtests nennen Zusage, Kopplung und Setzung im Indikativ; die Bit-Namen sind als „Lesart des Auswerters (Setzung)“ gekennzeichnet; keine Abschnittsnummer eines externen Standards. `make docs-check` Exit 0. Im Slice F-6. Die Aussage „die 38 früheren Dateien sind unverändert (zurückgesetzt)“ im Protokoll ist Auskunft über den Lauf, keine Chronik eines Zustandsfeldes.

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `referenztest-dotnet/Mitschnitt/messung/2022/`, `2025/` (zwölf neue Dateien) | geprüft, ohne Befund (Inhalt gegen W-26 bis W-36; Rohbytes byte-gleich; Redaktion; frühere Dateien unverändert) |
| `referenztest-dotnet/Vergleich/MessungWireTests.cs` | geprüft, ohne Befund (Szenarien, Tabellennamen, `Schema`, `SpaltenformNoetig`; Bau grün) |
| `referenztest-dotnet/Mitschnitt/MessungBelegTests.cs` | geprüft, ohne Befund (44 Dateien je Stufe, Umbenennung des Tests) |
| `tools/mk/vergleichslauf-d.mk` | geprüft, ohne Befund (Kommentar: 44 = 22 + 14 + 2 + 6; andere Fundstellen der alten Zahl 38 per `grep` nicht gefunden) |
| `docs/messungen/referenzabgleich.md` (Zeile A-8, Abschnitt W-26 bis W-36, Rohzeilen) | geprüft, Befunde F-5 und F-8 |
| `docs/plan/planning/in-progress/slice-simulation-schemaonly-keyinfo.md` §1, §2 | geprüft, Befund F-6; kein vorzeitiger Haken (F-9) |
| `src/`, `testserver/`, `spec/`, `docs/user/` | nicht berührt, geprüft, ohne Befund |
| Suppression (`@Suppress`, `[SuppressMessage]`, `#pragma warning disable`) in den geänderten C#-Dateien | geprüft, ohne Befund |

## Summary

**HIGH 0 · MEDIUM 5 · LOW 2 · INFO 3.**

Das Messprotokoll (W-26 bis W-36, 39 Rohzeilen) ist an der Ablage nachgerechnet und trägt: kein Wert weicht ab, und die Aussage „beide Instanzen gleich“ ist durch Rohbytes gedeckt. Die Funde liegen im Test- und Planungsgerüst um die Messung: drei Eigencode-Zweige ohne beobachtenden Test (F-1, F-2), ein Beleg-KDoc, das breiter ist als seine Asserts (F-3), die nicht abgeglichene Plan-Tabelle §3 (F-4, dritte Wiederholung dieser Klasse; Steering-Loop-Zähler im Register nachführen) und zwei Zahlen ohne Sensor (F-5).

**Empfehlung:** Nachbesserung vor dem Weitergang zum Architect und Bau; keine Wiederholung der Messung nötig, das Protokoll trägt. Die fünf MEDIUM-Findings liegen in Tests, Plan-Tabelle und Kopplung der Zahlen; über ihre Behebung entscheidet der Implementer. Ändert die Nachbesserung Testcode oder Leser, ist ein Re-Review der Commits nach `0c702389` einzuplanen (Skill „Nachbesserung mit Code ohne zweiten Review“).
