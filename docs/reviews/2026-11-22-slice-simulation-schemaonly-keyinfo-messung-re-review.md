# Re-Review: slice-simulation-schemaonly-keyinfo (Nachbesserung der Messphase, Liefer-Punkt 1) — 2026-11-22

**Review-Art:** Re-Review der Nachbesserung nach dem [Erstreview](2026-11-22-slice-simulation-schemaonly-keyinfo-messung-review.md) (Stand `032a325b`, 0 HIGH, 5 MEDIUM, 2 LOW, 3 INFO). Gegen Plan (Slice §1 bis §3), [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md) Entscheidung 7, `AGENTS.md` §3.7 und §3.8. Nicht gegen die DoD (Sache des Verifiers). Nach dem Skill „Nachbesserung mit Code ohne zweiten Review“ gelesen werden nur die Commits nach dem Report-Stand, nicht der ganze Slice.

**Gegenstand:** drei Commits, der Reihe nach:

| Commit | Inhalt | ändert Code? |
|---|---|---|
| `dbd108c4` | `TokenLeser.cs` (Lesegrenze `BisEnde`), `MessungEinheitTests.cs` (3 neue Tests), `MessungSchemaBelegTests.cs` (Katalog-Abschriften, 2 neue Theorien, Zahlen 472 und 107, KDoc) | ja (Leser, Testcode) |
| `f23030b9` | Plan-Tabelle und Zustandsfelder im Slice, Kopplungssatz in W-36, Mutationsbeleg `docs/messungen/mutationen-schemaonly-keyinfo-messphase-2026-11-22.md` | nein (nur Doku) |
| `2a53e081` | `docs/plan/spec-abdeckung.md` (SPEC-045: 146 bestandene Tests, `MessungSchemaBelegTests` in der Liste) | nein (erzeugte Matrix) |

Nicht bewertet (parallele Commits von Planner und Architect, `9dfb6ec0`, `a6d014e7`, `4992ef38`): [ADR-0059](../plan/adr/0059-schemaonly-ist-eine-erkannte-form-keyinfo-bleibt-abgewiesen.md), ADR-Index, Slice-Nachzug, Folge-Slice, `state.md` des Registers. `MessAuswertung.cs` und die 44 Ablagedateien je Stufe sind in den drei Commits nicht berührt (`git diff 032a325b..2a53e081 --stat`).

**Skill:** `.harness/skills/reviewer.md` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-22

**Eingangs-Kontext:** Erstreview-Report, Diff der drei Commits, `TokenLeser.cs` (Leser in voller Länge um `TabName` und `ColInfo`), Slice §3, Messprotokoll W-34 bis W-36, `Vergleich/live-ausnahmen.tsv` (Zeilen `schema-*`), `referenztest-dotnet/Dockerfile` (Stage `test`).

**Ausgeführte Sensoren** (Docker, Wegwerf-Kontexte im Scratchpad `.../scratchpad/rr2/`: `git archive HEAD` von `referenztest-dotnet/` mit einem `Dockerfile`, dessen `DOTNET_TEST_ARGS` auf die drei Testklassen `MessungSchemaBelegTests`, `MessungEinheitTests`, `MessungBelegTests` filtert; je Mutation eine Zeichenkette genau einmal ersetzt; nichts davon im Repo; `free -m` frei 1,4 bis 2,6 GB, **verfügbar 10 bis 14 GB**, kein Auslagern, die geforderten 4000 MB gelten hier als verfügbarer Speicher):

1. Grundlauf der drei Klassen, Stage `test` ohne Netz: **130 Testfälle grün** (Ausgangslauf des Mutationsbelegs: 130, gleich; vor der Nachbesserung 123).
2. Zwölf Mutationen (Tabelle unten), je 6 bis 11 s, mit den Testfall-Namen aus der Ausgabe des Laufs (Passed/Failed einzeln gezählt, kein `-q`).
3. Maschineller Abgleich der drei Abschrift-Konstanten (`KatalogPerson`, `KatalogTypen`, `KatalogPersonSchluessel`) mit Spalte 2 der Zeilen `schema-person`, `schema-typen`, `schema-person-schluessel` der `live-ausnahmen.tsv`: Zeichen für Zeichen gleich.
4. `git diff 032a325b..HEAD -- referenztest-dotnet/Mitschnitt/messung`: leer; je Stufe 44 Dateien; `git diff main...HEAD --name-status` unter `messung/`: nur zwölf `A`.
5. `make docs-check` Exit 0 (1759 Dateien, 0 Befunde, 3 s).

Nicht gefahren (Auftrag und Tabelle `harness/README.md` §Minimal agent workflow): `make gates`, `make clean-build`, `make e2e`, `make kompatibilitaet`, `make vergleichslauf-d`, `make spec-abdeckung`. Keine Messsperre angelegt. **Gesamtlaufzeit:** etwa 8 Minuten; größte Posten: Lesen von Diff, Leser und Messprotokoll (etwa 3 Minuten), die zwölf Mutationsläufe zusammen etwa 2 Minuten (je Lauf 6 bis 11 s, kein Cache-Kaltstart), Fehlersuche im eigenen Wegwerf-Skript (ein Lauf ohne Auswertung, 1 Minute).

## Stand der Findings des Erstreviews

| Finding | Stufe | Stand | Beleg |
|---|---|---|---|
| F-1 PLP-Zweig ohne beobachtenden Test | MEDIUM | **behoben** | M5 (Zeile entfällt) und M5b (nur der erste PLP-Teil) färben `Die_Auswertung_gibt_mit_mitSpaltenform_den_Text_eines_nvarchar_max_Parameters_aus_allen_PLP_Teilen_ungekuerzt_aus` rot; der Test trägt zwei Teile (3000 und 2008 Zeichen) mit Zeilenwechsel. |
| F-2 Rückfallzweige `nicht lesbar` ohne Test | MEDIUM | **behoben** | M6 und M7 (`throw;` statt Rückfall): je 2 rot, `Ein_TABNAME_und_ein_COLINFO_die_ueber_das_Ende_…` und `TABNAME_und_COLINFO_lesen_nicht_ueber_ihre_Laenge_hinaus_…`. |
| F-3 KDoc breiter als die Asserts | MEDIUM | **behoben** | (a) Anfragen gleich Befehle: M11 (eine Anfrage doppelt) rot, 3; (b) `Katalog`-Abschriften für `person`, `typen`, Schlüsselfall; (c) SESSIONSTATE-Inhalt in beiden Fehlerszenarien, M10 rot, 8; (d) Ausnahme bei `ExecuteReader` für `KeyInfo` und `SchemaOnly`; (e) Paartest über drei Paare mit genau fünf unterscheidenden Feldern. Die Aufzählung im KDoc deckt sich mit den Asserts (siehe unten). |
| F-4 Plan-Tabelle gegen Diff | MEDIUM | **behoben** | Slice §3 (Stand `HEAD`) nennt `TokenLeser.cs`, `MessAuswertung.cs`, `MessungSchemaBelegTests.cs`, `MessungEinheitTests.cs`, `MessungBelegTests.cs`, die Ablage beider Stufen, `vergleichslauf-d.mk`, `spec-abdeckung.md`, den Mutationsbeleg und den Erstreview. |
| F-5 Zahlen und Abschrift ohne Sensor | MEDIUM | **behoben bis auf die Gegenseite der Abschrift** | 472 und 107 steht als `Assert.Equal` im Test; eine Mutation (eine Zeile der Auswertung weniger) färbt allein diesen Test rot (471 gegen 472, M472), der Mutationsbeleg nennt 108 gegen 107 (M15). Die Abschrift: Kopplungssatz an beiden Orten (KDoc `Katalog`, Messprotokoll W-36), Gegenseite siehe N-3. |
| F-6 Zustand und Vorzustand im selben Satz | LOW | **behoben** | Slice §1: „die Antwortform ist gemessen ([Messprotokoll] W-30 bis W-35)“, „(nächster Punkt)“ entfallen; `git grep` nach den alten Wortlauten findet keinen Treffer in `docs/plan/planning/in-progress/`. |
| F-7 Lesegrenze nicht durchgesetzt | LOW | **behoben** | `BisEnde(ende)` nach jedem Namensteil und nach dem Spaltennamen; M8a und M8b je 1 rot (`TABNAME_und_COLINFO_lesen_nicht_ueber_ihre_Laenge_hinaus_…`); der Ausgang `ý\0` aus den Bytes des DONE bei M8a ist gemessen. Nuance: N-1. |
| F-8 Messsperre ohne Sensor | INFO | unverändert (keine Aktion) | |
| F-9 `spec-abdeckung.md` nicht im Diff | INFO | erledigt | `2a53e081`: SPEC-045 146, `MessungSchemaBelegTests` in der Liste. |
| F-10 Rohbytes beider Instanzen gleich | INFO | unverändert | Ablage byte-gleich unverändert. |

## Mutationen (selbst nachgefahren)

| Nr | Mutation | Gemessene Wirkung |
|---|---|---|
| M5 | `MessAuswertung.cs` `EinRpc`: die Zeile `texte?.Add(pname + "=" + …)` des PLP-Zweigs entfällt | 1 rot: `Die_Auswertung_gibt_mit_mitSpaltenform_den_Text_eines_nvarchar_max_Parameters_…` |
| M5b | `plp.AddRange` nur für den ersten Teil (`plp.Count == 0`) | 1 rot: derselbe Testfall |
| M6 | `TabName`: `tabellen.Add("nicht lesbar")` durch `throw;` ersetzt | 2 rot (die zwei Einheitstests der Lesegrenze) |
| M7 | `ColInfo`: `eintraege.Add("nicht lesbar")` durch `throw;` ersetzt | 2 rot (dieselben) |
| M8a | `TabName`: `BisEnde(ende)` nach dem Namensteil entfernt | 1 rot: `TABNAME_und_COLINFO_lesen_nicht_ueber_ihre_Laenge_hinaus_…`, Zeile 651 (Fall „Länge 3“); Actual `[ý\0]` |
| M8b | `ColInfo`: `BisEnde(ende)` nach dem Spaltennamen entfernt | 1 rot: derselbe Testfall, Zeile 657 |
| M8c | `BisEnde`: `_p > ende` zu `_p >= ende` | 12 rot (Einheitstest Spaltenform, Gegenprobe „genau“, zehn Belege) |
| M8d | `BisEnde`: `_p > ende` zu `_p > ende + 1` | **0 rot** (N-2) |
| M10 | SESSIONSTATE-Zeile `Zustaende` zu `Zustand` | 8 rot: zwei Belege je Stufe in `MessungSchemaBelegTests`, drei in `MessungBelegTests`, ein Einheitstest |
| M11 | Ablage 2022 `getschematable-ohne-verhalten.messung`: die letzte Anfrage doppelt | 3 rot: `GetSchemaTable_loest_keine_Anfrage_aus_…` (2022), `Beide_Instanzen_haben_…`, `MessungBelegTests > Ausser_SESSIONSTATE_nach_Cancel_…` |
| M472 | Auswertung: die Zeile `anfragetext` des zweiten Austauschs von `getschematable-ohne-verhalten` entfällt | 1 rot: `Beide_Instanzen_haben_…` (Expected 472, Actual 471), alle anderen grün |
| M13 | Ablage 2022 `keyinfo-spalten.messung`: `IsKey=False` der Spalte `Age` (`person`, `KeyInfo`) zu `True` | 2 rot: `Ein_Schluesselstatus_im_COLINFO_und_IsKey_…` (2022), `Beide_Instanzen_haben_…` |

Der Mutationsbeleg `mutationen-schemaonly-keyinfo-messphase-2026-11-22.md` stimmt in den Zahlen und Testfall-Namen mit meinen Läufen überein, soweit ich sie nachgefahren habe (M5 bis M8b, M10, M11, M13 ähnlich M12b/M13 des Belegs). M12, M12b, M14, M15, M16 sind nicht nachgefahren und gelten als Angabe des Implementers; der Beleg nennt den Erzeuger „nicht im Repo“ und das Verfahren der Wiederholung (Grenze benannt, kein Befund).

## Prüfung der Schwerpunkte

**Leser (`BisEnde`).** `TabName` und `ColInfo` rufen `BisEnde(ende)` innerhalb des `try`; die ausgelöste `IndexOutOfRangeException` fängt dieselbe `catch`-Klausel wie die Lesefehler am Datenende, `_p = ende` setzt danach den Leser auf das Folge-Token. `COLINFO` mit Status ohne Bit `0x20` ruft `BisEnde` nicht (kein Name gelesen) und bleibt durch die Schleifenbedingung `_p + 3 <= ende` begrenzt. Die 38 früheren und die 6 neuen Dateien je Stufe laufen unverändert ohne `PROBLEM` (`MessungBelegTests`, 44 Dateien, grün). **Korrekt.**

**Ablage.** Je Stufe 44 Dateien, `git diff 032a325b..HEAD` unter `messung/` leer; gegen `main` nur zwölf `A`. **Unverändert.**

**KDoc von `MessungSchemaBelegTests` gegen die Asserts (§3.8).** Das KDoc nennt: gesendeter Text (`anfragetext`-Asserts), Tokenfolge, DONE-Befehle und Zähler (`done`, zusätzlich `MORE|COUNT, Befehl 0x00c1, Zeilen 2` bei `typen`), Spaltenflags, TABNAME- und COLINFO-Bytes (`spaltenform`), SESSIONSTATE (Inhalt in `keyinfo-sonderfaelle/10` und `schemaonly-und-keyinfo/02`), Schemazeilen von `person` und `typen` (Abschrift), Ausnahmen des Clients (208 bei `KeyInfo` und `SchemaOnly`), Zahl der Anfragen je Szenario (als Gleichheit mit der Zahl der Befehle) und Gleichheit der Instanzen (mit 472 und 107). Zu jedem Punkt steht mindestens ein Assert, M10, M11, M13 und M472 belegen es an der Wirkung. Was das KDoc ausnimmt (Laufzeit, Messsperre, Host, Grenzen des Protokolls), prüft die Klasse auch nicht. Die Schemazeile von `typen` ist nur ohne Schlüssel geführt, wie die Zeile `schema-typen` der Referenz. **Nicht breiter als die Asserts.**

**Kopplungssatz zur Abschrift `live-ausnahmen.tsv`.** KDoc `Katalog`: „die Datei liegt nicht im Build-Kontext der Stage `test`, kein Test vergleicht beide“. Wahr: die Stage `test-quellen` im `referenztest-dotnet/Dockerfile` kopiert `Smoke`, `Mitschnitt` und `test-lauf.sh`, nicht `Vergleich`; kein Test liest die TSV. Messprotokoll W-36: „am 2026-11-22 von Hand geprüft“: **wahr** (maschineller Abgleich der drei Konstanten, siehe oben). Der Satz „gleich den Werten der Referenz in `live-ausnahmen.tsv`“ trägt damit am heutigen Stand.

## Findings (neu)

### N-1 — LOW
- `kategorie`: LOW
- `quelle`: `AGENTS.md` §3.8 (Aussage trägt nur so weit wie ihr Beleg), Skill „Auswahl- oder Gleichheitseigenschaft mit einheitlicher Eingabe“ (Abgrenzung)
- `pfad`: `referenztest-dotnet/Mitschnitt/MessungEinheitTests.cs` · „// Länge 5, Zähler 2: der zweite Namensteil läge hinter dem Token und würde aus den Bytes des DONE gelesen.“
- `befund`: Der Fall „Länge 5, Zähler 2“ belegt die Lesegrenze nicht: Der Test bleibt bei M8a (ohne `BisEnde`) grün, und der Lauf bricht erst an der Zeile 651 (Fall „Länge 3“). Der zweite Namensteil liest aus dem DONE die Länge `0x00fd` und läuft über das Pufferende, die Ausnahme kommt also aus dem Puffer, nicht aus der Lesegrenze. Die Lesegrenze tragen allein die Fälle „Länge 3“, COLINFO und die Gegenprobe „genau“; der in F-7 gemessene Fall (`[a.]`, Namensteil der Länge 0 aus den Bytes des Folge-Tokens) ist nicht nachgestellt.
- `verifizierbar`: ja (M8a: der erste Assert grün, der zweite rot)
- `klasse`: Testfall trägt die Zusage nicht, die sein Kommentar nennt

### N-2 — INFO
- `kategorie`: INFO
- `quelle`: Skill „Eigencode, dessen Entfernung keinen Test rot macht“ (Grenzfall, nicht Streichung)
- `pfad`: `referenztest-dotnet/Mitschnitt/TokenLeser.cs` · „if (_p > ende) throw new IndexOutOfRangeException();“
- `befund`: Die Mutation `_p > ende + 1` bleibt grün (M8d), ein Überlauf um genau ein Byte (z. B. TABNAME mit Länge 6 und einem Namensteil von zwei Zeichen, 7 Bytes) ist nicht nachgestellt; `>=` (M8c) und die Streichung (M8a, M8b) färben rot. Keine Aktion erwartet; Gemessene Ströme erreichen den Fall nicht.
- `verifizierbar`: ja (M8d)
- `klasse`: Grenzwert der Lesegrenze nicht gefahren

### N-3 — LOW
- `kategorie`: LOW
- `quelle`: `AGENTS.md` §3.7 (Klasse Kopplung), Skill „Zahl, Feldmenge oder Zuordnung steht an einem zweiten Ort ohne Sensor“ (die Lage ist halb erfüllt, siehe Begründung)
- `pfad`: `referenztest-dotnet/Mitschnitt/MessungSchemaBelegTests.cs` · „kein Test vergleicht beide (Kopplung von Hand).“ und `referenztest-dotnet/Vergleich/live-ausnahmen.tsv` · „schema-person-schluessel“
- `befund`: Die Abschrift der drei Zeilen steht im Test mit Kopplungskommentar und im Messprotokoll (W-36), auf der Seite der TSV steht kein Hinweis, und kein Test oder Gate liest beide Orte; die Gleichheit ist von Hand am 2026-11-22 geprüft (wahr, siehe oben). Der Skill liest „beide Seiten“ wörtlich als MEDIUM; hier ist die Lücke benannt und die Gegenseite ändert der Bau (Slice §3: die drei Regeln der TSV entfallen), danach nennt das KDoc von `Katalog` Zeilen, die nicht mehr existieren („Zeilen `schema-*` der Referenz“). Ein MEDIUM mit Failure-Szenario (Drift der TSV-Werte, ohne dass ein Test rot wird) liegt nicht vor, weil die Abschrift gegen die Ablage (gemessen) geprüft wird; das Szenario ist ein veralteter Verweis nach dem Bau.
- `verifizierbar`: nein (Hinweis an den Bau und den Verifier)
- `klasse`: Kopplung einseitig, Gegenseite im Folge-Schritt entfernt

### N-4 — INFO
- `kategorie`: INFO
- `quelle`: Skill „Plan-Tabelle nennt die berührten Dateien nicht“ (Abgrenzung), `AGENTS.md` §3.7
- `pfad`: `docs/messungen/mutationen-schemaonly-keyinfo-messphase-2026-11-22.md` · „ein Test der Zahl der Auswertungszeilen (472) hat keine eigene Mutation; die Zahl ist über dieselbe Summe geprüft, ihre Mutation (eine Zeile der Auswertung weniger je Instanz) wurde nicht gefahren.“
- `befund`: Diese Mutation ist gefahren (M472, hier: eine Zeile weniger, 471 gegen 472, nur der Test der Summe rot); der Absatz „Lesarten und Lücken“ kann sie nennen. Dazu: Dieser Re-Review ist eine neue Datei unter `docs/reviews/`, die Plan-Tabelle in Slice §3 nennt sie nicht; Sache des Planners nach dem Report.
- `verifizierbar`: ja
- `klasse`: Beleg stärker als beschrieben

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `referenztest-dotnet/Mitschnitt/TokenLeser.cs` (`BisEnde`, `TabName`, `ColInfo`) | geprüft, Befund N-2 (INFO); Lesegrenze korrekt |
| `referenztest-dotnet/Mitschnitt/MessungEinheitTests.cs` (drei neue Tests, `NvarcharMaxParam`) | geprüft, Befund N-1 |
| `referenztest-dotnet/Mitschnitt/MessungSchemaBelegTests.cs` (Katalog, Paartest, Anfragen, Zahlen, KDoc) | geprüft, Befund N-3; KDoc nicht breiter als die Asserts |
| `referenztest-dotnet/Mitschnitt/messung/2022/`, `2025/` | geprüft, ohne Befund (44 Dateien je Stufe, seit dem Erstreview unverändert) |
| `docs/messungen/mutationen-schemaonly-keyinfo-messphase-2026-11-22.md` | geprüft, Befund N-4 (INFO); gemessene Wirkung gleich meinen Läufen |
| `docs/messungen/referenzabgleich.md` (W-36, Kopplung) | geprüft, ohne Befund (Aussage wahr) |
| `docs/plan/planning/in-progress/slice-simulation-schemaonly-keyinfo.md` §1, §3 | geprüft, ohne Befund (F-4, F-6 behoben); Rest des Slice ist nicht Gegenstand (Planner) |
| `docs/plan/spec-abdeckung.md` | geprüft, ohne Befund (erzeugt, SPEC-045 146, Klasse in der Liste); `make spec-abdeckung` nicht gefahren |
| Suppression (`#pragma warning disable`, `[SuppressMessage]`) in den geänderten C#-Dateien | geprüft, ohne Befund |
| Quelltext außerhalb `referenztest-dotnet/Mitschnitt/` (`src/`, `testserver/`, `spec/`, `docs/user/`) | von den drei Commits nicht berührt |

## Summary

**HIGH 0 · MEDIUM 0 · LOW 2 · INFO 2** (neue Findings dieses Laufs; die fünf MEDIUM und zwei LOW des Erstreviews sind behoben, F-5 mit der Gegenseite N-3).

Alle fünf MEDIUM des Erstreviews (F-1 bis F-5) sind in Wirkung behoben, nicht nur umformuliert: jede der gemessenen Mutationen M5 bis M8b, M10, M11 und M472 färbt mindestens einen Testfall mit dem im Beleg genannten Namen rot, die Lesegrenze im Leser ist korrekt, das KDoc der Belegtests trägt nicht mehr als die Asserts, und der Kopplungssatz zur Abschrift ist am heutigen Stand wahr. Offen sind ein Testfall, der die Lesegrenze nicht isoliert (N-1), die einseitige Kopplung der Abschrift (N-3) und zwei Hinweise.

**Empfehlung:** Weitergang zum Verifier; die Nachbesserung ist für den Code (Leser, Testcode) hiermit gelesen und gefahren, ein weiterer Re-Review ist nur nötig, wenn Quelltext, Testcode oder Skripte nach `2a53e081` noch geändert werden (die Commits `9dfb6ec0`, `a6d014e7`, `4992ef38` ändern keinen Code). N-1 und N-3 sind keine Hürde: N-3 ist beim Bau zu berücksichtigen, wenn die drei Zeilen aus der `live-ausnahmen.tsv` entfallen.
