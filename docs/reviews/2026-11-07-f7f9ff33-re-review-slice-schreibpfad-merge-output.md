# Review-Report: slice-schreibpfad-merge-output (Re-Review der Nachbesserung) — 2026-11-07

**Review-Art:** Code (gegen Plan, ADR und Hard Rules; nicht gegen die DoD, das ist die Frage des Verifiers). Re-Review nach Code-Nachbesserung ohne zweiten Review (Beobachtung `BEO-TDS/nachbesserung-ohne-zweiten-review`, verkörpert im Reviewer-Skill).

**Gegenstand:** Diff `b2ff295c..f7f9ff33` (Commits `fb2696da` Review-Report, `478c4332` Merge von `main`, `c8971793` Messung der Parameter von DECIMAL, TIME, DATETIME2, BIT und DATE mit d-migrate am echten SQL Server, `6d489e14` Parameter `DECIMALN` (kurzer Wert) und `DATETIMN`, Sperre je Backend für `MERGE`, Tests nach Review, `fa6e58e7` Nachbesserung in Messprotokoll, Plan, Risiken, Nutzer-Doku und Matrix, `f7f9ff33` Referenztest und Kompatibilitätsmatrix zu `DATETIMN` 8 und `DECIMALN`), Zweig `worktree-agent-a367dd0d063325ded`. Der Anteil des Merges von `main` (Nutzer-Doku aufgabenbezogen, `AnleitungTest`) ist nicht Gegenstand, außer wo die Nachbesserung ihn berührt (F-2 des ersten Reviews, R-3). Erster Review: `2026-11-07-b2ff295c-review-slice-schreibpfad-merge-output` (F-1 bis F-14).

**Skill:** `.harness/skills/reviewer.md` @ `f7f9ff33` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-07

**Eingangs-Kontext** (die Verträge, gegen die geprüft wurde):

- Slice `slice-schreibpfad-merge-output` (Plan §3, DoD, Rückführungen, §6 Risiken); der erste Review-Report
- [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) (Accepted), [ADR-0021](../plan/adr/0021-oeffentlicher-integrations-einstieg.md)
- `SPEC-030`, `SPEC-035`, `SPEC-036`, `SPEC-040`, `SPEC-059`; [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit), [TDS-QA-07](../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität)
- `AGENTS.md` §3.1 bis §3.8; Grundsatz des Nutzers „Spec ist Zielbild“ (der echte SQL Server ist die Referenz; Abweichungen sichtbar mit Träger)

## Behebung der Findings des ersten Reviews

| Finding (erster Review) | Stand | Beleg |
|---|---|---|
| F-1 MEDIUM, `DECIMALN` kurz, `DATETIMN` | **behoben** | Parser (`RpcParameterReader.lengthFits`, `RpcWireValues.datetime`, `RpcParameters.datetimeType`), Überführung `DateTime` in `Time` (`SimValues`), Messung `M-31`/`M-32`; Handvektoren stammen aus der Aufnahme (siehe Abschnitt Messung); `d-migrate`-Läufe unten |
| F-2 MEDIUM, Doku und Spec nennen die Lücke nicht | **behoben**, aber zweite Fundstellen stehen (R-1, R-2, R-3) | `docs/user/testserver-manuell.md` Zeile `--on-conflict`, `SPEC-030`, `SPEC-036` |
| F-3 MEDIUM, Träger der drei Zielbild-Abweichungen | **behoben** | Risiko in `slice-antwort-tokens-zielbild-luecken-bauen` und im Slice §6, mit Verweis auf den Entscheid des Nutzers zu [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 4 |
| F-4 MEDIUM, `stopped` zwischen Lesen und Callback | **behoben** | Test `ein Abbruch zwischen Lesen und Callback schreibt nichts`; Mutation m2 allein rot (genau dieser Test) |
| F-5 MEDIUM, Plan-Tabelle | **teilweise**: neue Zeilen, neue Lücken | R-4 |
| F-6 LOW, Plan nach dem Code | **behoben** (Absatz „Abweichung vom Plan-Wortlaut“ nennt es offen) | Slice §3 |
| F-7 LOW, Negativtests | (a) Zwei-Spalten-Schlüssel, (b) `sp_prepexec`/`sp_execute` über den Draht, (c) Fehler hinter `COLMETADATA` **behoben**; (Rest von c) Cancel mitten im `MERGE` am Draht und (d) Gestalten mit Grund **offen ohne Begründung** | R-7 für Cancel; (d) R-8 |
| F-8 LOW, nicht atomar | **behoben** durch die Sperre | Test, Mutation m1; `d-migrate`-Lauf P1 unten |
| F-9 LOW, Aliase in „Nicht erkannt“ | **behoben** | `SPEC-036`: „(außer `AS <Alias>` der Form M)“ |
| F-10 bis F-14 INFO | F-11: Register-Stand bleibt Sache des Planners; sonst keine Aktion | |

## Selbst gefahren

| Lauf | Ergebnis |
|---|---|
| `make gates FORCE=1` | Exit 0 (Stempel an `f7f9ff33` bestätigt; Docker-Schichten aus dem Cache, 118/95/100/67 Fälle der Selbsttests der Stempel, `d-check` 1189 Dateien 0 Befunde, `gate-zuordnung` 16 Gates) |
| `make clean-build` | Exit 0 (`--no-cache`, Last 17 bis 60): 1186 `PASSED`, 0 `FAILED`, detekt grün; `SimulationBackendMergeTest` mit den drei neuen Tests grün |
| `make test` | nicht als Frischbeleg gefahren: Cache-Treffer (Cache-Falle); Frischbeleg ist `make clean-build` |
| `make flaeche` | Exit 0 (Selbsttest und Prüfung) |
| `make docs-check` | 1189 Dateien, 0 Befunde (vor Anlage dieses Reports) |
| `make e2e` | **Exit 2**: Stufen 1 bis 6 grün (Stufe 5 mit dem neuen Test `Parameter_DATETIMN_8_wird_gelesen_und_beantwortet`), **Stufe 7 rot** (`make kompatibilitaet KATEGORIE=Synchronitaet`, 83 s, Last 50 bis 60): `SynchronitaetTests.Cancel_und_Command_Timeout_kurz_hintereinander_lassen_die_Folgeabfrage_nur_ihre_Antwort_erhalten`, siehe R-6 |
| `make matrix-zitate` | Exit 0: 1186 bestandene Testfälle, 308 Zitate, 0 ohne `PASSED` |
| `make kompatibilitaet KATEGORIE=Synchronitaet` (Stufe 7 allein, Wiederholung) | Stand `f7f9ff33` bei Last 50 bis 60: Lauf 1 grün, Läufe 2 und 3 rot (derselbe Test, `Expected: 2, Actual: 0`); bei Last 14 bis 30: Läufe 4 bis 6 grün. Stand `b2ff295c` (Auszug des Baums per `git archive`, nicht im Repo) bei Last 44 bis 55: Läufe 1 bis 3 grün; bei Last 14 bis 30: Läufe 4 bis 6 grün |
| `make vergleichslauf-d LAUF=jdbc-2022` (EULA bestätigt, lokal; `MESSUNG_NUR` mit allen 19 Schritten, ohne Ablage in den Arbeitsbaum; Arbeitsbaum danach unverändert) | Exit 0, „gruen“, 289 s unter Last 17 bis 60. Vergleich mit der abgelegten Aufnahme `2022.aufnahme.txt`: alle Exits gleich (0 bis auf `bulk-int` mit 5), die Menge der Parameter-Bytes (`DECIMALN`, `DATETIMN`, `DATETIME2N`, `BITN`, `DATEN`) **identisch** (`6a1126020301e204`, `6a112602020005`, `6a1126020601ffe30b5402`, `6a11260200`, `6f0808df630000b425da00`, `6f0808df63000000000000`, `6f0800` je gleich oft), 39 Abschlüsse `0x0117` in beiden, Exporte von `bestellungen` und `zeiten` byteweise gleich. Verschieden nur die Reihenfolge der Katalogabfragen von `data transfer` (Zeilen sortiert verglichen) und die Kennungen. Instanz 2025 nicht wiederholt |
| `d-migrate` (Digest `sha256:308f2775…`) gegen zwei Testserver (Stand `f7f9ff33`, Abbild aus dem Arbeitsbaum), Beschreibung mit `kunden`, `bestellungen` (`Betrag` `DECIMAL(10,2)`) und `zeiten` (`TIME`, `DATETIME`, `BOOLEAN`, `DATE`) | `import --on-conflict skip` und `update` in `bestellungen`: Exit 0, „5 inserted“, „5 updated“, Export `"12.50"`, NULL, `"-0.05"`, `"99999999.99"`, `"0.00"`; in `zeiten` (vier Zeilen): Exit 0, „4 inserted“, „4 updated“, Export einschließlich `"0001-01-01"`, `"9999-12-31"`, `"2026-01-02T23:59:59.123"`; `data transfer --tables bestellungen,zeiten --on-conflict skip` und `update`: Exit 0 (beide), Exporte von A und B byteweise gleich; Betrag über der Precision (`123456789.12`): Exit 5 „Invalid value.“ (Überlauf); drei gleichzeitige `skip`-Importe (P1) in dieselbe leere Tabelle: **alle Exit 0**, einmal „3 inserted“, zweimal „3 skipped“ (im ersten Review: zwei Exit 5). Beobachtung: die Zeit `23:59:59.997` der Eingabe steht im Export des Testservers als `"23:59:59"` (INFO unten) |

## Findings

### R-1 MEDIUM — `DATETIMN` gilt in zwei weiteren Fundstellen weiter als abgelehnt

* kategorie: MEDIUM
* quelle: Reviewer-Skill „Fortschreibung lässt die zweite Fundstelle stehen“; `AGENTS.md` §3.8
* pfad: `spec/spezifikation.md` · „mit einem Typ ohne Entsprechung in `SqlType` (`INTN` mit 1 und 2 Byte, Gleitkomma, Geld, GUID, Binär, `varchar`, `DATETIMN`, `DATETIMEOFFSETN`) ergibt ebenfalls `SPEC-013`“; `tools/e2e.sh` · „RPC-Parametertypen FLTN, MONEYN, DATETIMN, GUID und DATETIMEOFFSETN gelesen und als RPC abgewiesen“ (dieselbe Zeile in `docs/plan/e2e-abdeckung.md`)
* befund: Die Nachbesserung ändert `DATETIMN` mit 8 Byte von „abgelehnt“ zu `DateTime` (`SPEC-030`, Matrix, Parser). Die Aufzählung in der Spezifikation (Abschnitt zum RPC im Backend-Vertrag) nennt `DATETIMN` weiter ohne die Größe, und die Abdeckungszeile des E2E-Laufs behauptet, `DATETIMN` werde als RPC abgewiesen, obwohl der Referenztest die Zeile `DateTime` aus der Reihe der abgewiesenen Typen entfernt hat (`AnmeldungTests`, Theorie `Parametertypen`) und nur noch `SmallDateTime` abgewiesen wird. Ein Leser der Spezifikation oder der Abdeckungs-Sicht erwartet `TDS_UNSUPPORTED_RPC` für ein `datetime`-Parameter, der Testserver bindet ihn. Die Liste der `VALUE`-Ablehnungen in derselben Spec-Zeile („Dezimalbetrag ab 10 hoch Precision, … Zeit ab 24 Uhr“) nennt die neuen Gründe (Ticks ab 24 Uhr, Tage außerhalb 0001 bis 9999 für `datetime`) nicht.
* verifizierbar: ja (Grep `DATETIMN` über `spec/`, `docs/plan/`, `tools/`)
* klasse: Fortschreibung lässt zweite Fundstelle stehen

### R-2 MEDIUM — `testserver-manuell.md`: „Daten nur über das Massenladen“ widerspricht dem `MERGE`-Pfad im selben Dokument

* kategorie: MEDIUM
* quelle: Reviewer-Skill „Nutzer-Doku nicht komplett gegen den Slice gelesen“; `AGENTS.md` §5 Regel 5
* pfad: `docs/user/testserver-manuell.md` · „Daten nur über das Massenladen“; „Zeilen kommen über das Massenladen in den Arbeitsspeicher“; „Erkannt werden nach“
* befund: Der Slice fügt Zeilen über `MERGE … OUTPUT $action` ein (Abschnitt „Eigenschaften“, Punkt `--on-conflict`, in derselben Nachbesserung fortgeschrieben). Die Referenz „Grenzen“ sagt weiter „Daten nur über das Massenladen“, der Abschnitt zu den Beispiel-Tabellen „Zeilen kommen über das Massenladen“, und die Liste der erkannten Formen („Erkannt werden …“) führt Form M nicht. Der Leser der Grenzen schließt, `--on-conflict` sei nicht möglich; der Leser der Formenliste, ein `MERGE` ende mit `TDS_UNSUPPORTED_SQL`. Die Nachbesserung hat `UPDATE` in genau diesen drei Absätzen fortgeschrieben, `MERGE` nicht.
* verifizierbar: ja (Grep `Massenladen` und `Erkannt werden` in der Datei; Lauf D1 oben)
* klasse: Nutzer-Doku nicht komplett gegen den Slice gelesen

### R-3 MEDIUM — `einbettung-und-betrieb.md` sagt nichts zu `MERGE`; zwei Aussagen sind für `MERGE` falsch, die Sperre und ihre Fallen fehlen

* kategorie: MEDIUM
* quelle: Reviewer-Skill „Nutzer-Doku nicht komplett gegen den Slice gelesen“; [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend); `AGENTS.md` §5 Regel 5
* pfad: `docs/user/einbettung-und-betrieb.md` · „`primaryKey` beschreibt nur; er wirkt weder auf das Resultset noch auf die Callbacks und prüft keine Eindeutigkeit“; „Jede Anweisung ruft ihren Callback genau einmal“; „**Was das Backend erkennt.**“
* befund: Für einen Einbettenden ändert der Slice das Verhalten: ein `MERGE` liest die Zeilenquelle, die Bibliothek vergleicht den Schlüssel selbst (`primaryKey` wirkt also auf die Entscheidung `INSERT`/`UPDATE`), ruft höchstens einen Callback (oder keinen), und gleichzeitige `MERGE` desselben Backends laufen unter einer nicht reentranten Sperre; ein Callback, der dasselbe Backend aufruft und abwartet, hängt, und ein hängender Callback hält alle weiteren `MERGE`. Die Anleitung nennt die Form in „Was das Backend erkennt“ nicht, widerspricht sich in den beiden zitierten Sätzen und führt die Sperre nur für die Strukturänderung. Die Zusage steht nur in `internal`-KDoc und in der Spezifikation; der Einbettende liest keines von beiden. Der Plan §3 nennt für die Nutzer-Doku nur `testserver-manuell.md`.
* verifizierbar: ja (Grep `MERGE` in `docs/user/einbettung-und-betrieb.md`: kein Treffer)
* klasse: Nutzer-Doku nicht komplett gegen den Slice gelesen

### R-4 MEDIUM — Plan-Tabelle §3 nennt weiter nicht alle berührten Dateien (Wiederholung von F-5)

* kategorie: MEDIUM
* quelle: Reviewer-Skill „Plan-Tabelle nennt die berührten Dateien nicht“
* pfad: `docs/plan/planning/in-progress/slice-schreibpfad-merge-output.md` · „| `src/integrator/kotlin/io/github/pt9912/tdswire/integrator/RpcWire.kt`, `MergeEmbeddingTest.kt` | update, neu |“
* befund: Abgleich der Tabelle mit dem Diff der Nachbesserung. Berührt, ohne Zeile: `referenztest-dotnet/Kompatibilitaet/AnmeldungTests.cs` (Theorie geändert, neuer Test), `docs/plan/kompatibilitaetsmatrix.md` (drei Zeilen), `src/integrator/.../RpcParams.kt` (neu), `src/test/.../RpcBinderTest.kt`, `RpcTypesTest.kt`, `RpcFailureTest.kt` (die Zeile „Tests“ nennt nur vier andere Klassen), `tools/jdbc-messung/Aufnahme.java` (Ausgabe `[hex=…]`; die Zeile nennt die Datei, aber nicht diese Änderung). Die Zeile zur Ablage und zu `referenzabgleich.md` nennt „M-25 bis M-30“, die Nachbesserung fügt `M-31` und `M-32` hinzu. Der Negativbefund „keine Datei darüber hinaus“ gilt damit nicht. Dasselbe Muster war im ersten Review MEDIUM (F-5).
* verifizierbar: ja (Abgleich Diff gegen Tabelle)
* klasse: Plan-Tabelle ohne berührte Datei

### R-5 MEDIUM — „Gemessene Vektoren“ trägt Werte, die die Aufnahme nicht enthält

* kategorie: MEDIUM
* quelle: `AGENTS.md` §3.8; Reviewer-Skill „Aussage breiter als ihr Beleg“
* pfad: `src/test/kotlin/io/github/pt9912/tdswire/protocol/dispatch/RpcBinderTest.kt` · „fun `Dezimalwerte kurzer Laenge und datetime sind gemessene Vektoren`“; `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/RpcWireValues.kt` · „Ein Tick ist 3,33 ms; die Millisekunden werden gerundet“
* befund: Test-Name und KDoc nennen die Vektoren „gemessen“ (M-31). Die Aufnahme am echten Server enthält für `DATETIMN` nur `6F 08 08 DF 63 00 00 B4 25 DA 00` (13:14:15), `6F 08 08 DF 63 00 00 00 00 00 00` und die NULL `6F 08 00` (zwei Instanzen, je dreimal). Der Test setzt zusätzlich `00 00 00 00 00 00 00 00` (1900-01-01), `FF 81 8B 01` (letzter Tick, „23:59:59,997 auf Millisekunden gerundet“) und `A5 6A F5 FF` (0001-01-01) unter dieselbe Behauptung. Die Rundung auf Millisekunden und die Untergrenze 0001-01-01 (der echte `datetime` beginnt am 1753-01-01) sind Festlegungen; das KDoc kennzeichnet nur die Bereichsprüfung als „Festlegung ohne Messung“, die Rundung nicht. Failure-Szenario: wer den Parser für ein `time(7)`-Feld mit Bruchteilen anfasst, liest „gemessen“ und hält die Rundung für das Verhalten des echten Servers; die Aufnahme zeigt keinen Bruchteil in der `time`-Spalte (`zeiten.json` hat nur ganze Sekunden).
* verifizierbar: ja (Grep `hex=6f` in `2022.aufnahme.txt`)
* klasse: Test-Name „gemessen“ trägt synthetische Vektoren

### R-6 MEDIUM — `make e2e` Stufe 7 rot: Folgeabfrage nach gleichzeitigem Cancel und Command-Timeout liefert ein Ergebnis ohne Spalten

* kategorie: MEDIUM
* quelle: [TDS-QA-07](../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität); [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität)
* pfad: `referenztest-dotnet/Kompatibilitaet/SynchronitaetTests.cs` · „Cancel_und_Command_Timeout_kurz_hintereinander_lassen_die_Folgeabfrage_nur_ihre_Antwort_erhalten“
* befund: Im ersten `make e2e` (Last 50 bis 60) und in zwei von vier Wiederholungen der Stufe 7 auf `f7f9ff33` endet die Runde 3 („Cancel() 0 ms um den Command-Timeout von 1 s“, Ausgang `Execution Timeout Expired`, Zustand `Open`) damit, dass die Folgeabfrage derselben Verbindung ein Ergebnis mit `FieldCount` 0 statt 2 liefert (`ErwarteEigeneAntwort`, `Expected: 2, Actual: 0`): die Antwort der Folgeabfrage trägt kein `COLMETADATA`, also gehört sie einer früheren Anfrage oder fehlt. Der Diff berührt weder den Abbruchpfad noch `SELECT` (die Nachbesserung ändert in `src/main` nur Parameter-Abbildung und `MERGE`); auf `b2ff295c` blieb derselbe Test in sechs Läufen bei Last 14 bis 55 grün, auf `f7f9ff33` in drei Läufen bei Last 14 bis 30 grün. Die Zuordnung zur Nachbesserung ist damit nicht belegt und nicht widerlegt (kleine Zahl, schwankende Last). Der Test ist kein Teil der Gates (`make e2e` ist kein Gate).
* verifizierbar: ja (`make kompatibilitaet KATEGORIE=Synchronitaet` unter Last wiederholen, auf beiden Ständen)
* klasse: Synchronität nach doppeltem Abbruch, nicht zugeordnet

### R-7 LOW — Zusagen zur Sperre ohne Test: Wartefall, Nicht-Reentranz, hängender Callback, Cancel am Draht

* kategorie: LOW
* quelle: [TDS-QA-07](../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität); `AGENTS.md` §3.8; Reviewer-Skill „Eigencode, dessen Entfernung keinen Test rot macht“
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/simulation/SimulationMerges.kt` · „if (prepared.key.values.any { it == null } || stopped.get()) return false“; „while (!found && !stopped.get())“; `spec/spezifikation.md` · „Die Sperre ist nicht reentrant, ein Callback, der dasselbe Backend“
* befund: (a) Mutation m3 (beide `stopped`-Prüfungen in `exists()` entfernt) allein gefahren: `BUILD SUCCESSFUL`, kein Test rot. Die Zeilen sind älter als die Nachbesserung, tragen aber den neuen Satz des KDoc „Ein `stop` ist auch nach dem Warten auf die Sperre … wirksam“: ein `cancel`, das eintrifft, während der `MERGE` auf die Sperre wartet, öffnet die Zeilenquelle weiter; kein Test setzt `stop` während des Wartens (der Callback bleibt durch die Prüfung vor dem Callback verhindert, F-4). (b) Die Zusagen „nicht reentrant“ und „ein hängender Callback hält die Sperre für alle weiteren `MERGE`“ stehen in der Spezifikation; kein Test belegt sie (ein `withTimeout` um einen zweiten `MERGE` aus dem Callback genügte). (c) Cancel mitten im `MERGE` ist am Draht nicht getestet (nur die Bibliothek, F-4); ein ATTENTION, der in der Warteschlange der Sperre steht, ebenso nicht. Die Wirkung bleibt ohne Verlust von Daten oder Synchronität; belegt ist die Aussage dadurch nur durch den Kotlin-Mutex, nicht durch den Slice.
* verifizierbar: ja (Mutation m3; ein Test mit zweitem `MERGE` im Callback)
* klasse: Abbruch-Prüfung ohne Test

### R-8 INFO — Gestalten-Test (F-7 d) und Messprotokoll-Tabelle

* kategorie: INFO
* quelle: Maintainability
* pfad: `docs/messungen/merge-output-2026-11-07.md` · „| N15 | `DECIMALN` darf kürzer sein als die Größe“; `src/test/kotlin/io/github/pt9912/tdswire/simulation/SimulationBackendMergeTest.kt` · „fun `Schreibweise, Umbrueche und Aliase aendern die Erkennung nicht`“
* befund: Eine Leerzeile trennt die Zeilen N15 bis N22 von der Tabelle bis N14, sie erscheinen als Fließtext. Der Punkt (d) des ersten Reviews (neun Gestalten in einem Test, nur der Code geprüft) bleibt ohne Änderung und ohne Begründung im Diff; Wirkung unverändert gering.
* verifizierbar: ja
* klasse: Tabelle zerrissen

### R-9 INFO — Bruchteile einer `time`-Spalte über `datetime`

* kategorie: INFO
* quelle: `AGENTS.md` §3.8
* pfad: `docs/user/testserver-manuell.md` · „Der Testserver legt eine fehlende Zeile an“
* befund: Selbst gefahren: `{"Uhr":"23:59:59.997"}` aus `zeiten.json` wird mit `--on-conflict` eingefügt (Exit 0), der Export des Testservers zeigt `"23:59:59"`. Ob der Verlust im Export von d-migrate (Darstellung von `java.sql.Time`) oder in der Bibliothek liegt, ist nicht getrennt; am echten Server ist ein Bruchteil in der `time`-Spalte nicht gemessen (die Eingabe der Messung hat nur ganze Sekunden). Kein Befund gegen die Aussage der Doku, die den Fall nicht nennt.
* verifizierbar: ja (Lauf mit der Zeile `Id 4`)
* klasse: Messlücke Bruchteile

### R-10 INFO — Wert 0 einer `decimal`-Spalte im Transfer

* kategorie: INFO
* quelle: `AGENTS.md` §3.8
* pfad: `referenztest-dotnet/Mitschnitt/messung/jdbc-d-migrate/2025.aufnahme.txt` · „DECIMALN(38,2) = 0x0100 [hex=6a112602020100]“
* befund: `M-31` und `SPEC-030` sagen, der ganzzahlig geschriebene Wert 0 einer `decimal`-Spalte komme als `bigint`. Das gilt für die Eingabe aus JSON; im `data transfer` der Instanz 2025 geht der gelesene Wert `0.00` als `DECIMALN` mit der Länge 2 (`01 00`). Die Länge 2 deckt der Test mit `00 05` ab; ein eigener Vektor fehlt. Kein Fehler.
* verifizierbar: ja
* klasse: Messzeile enger als Spec-Satz

## Mutationen (selbst gefahren)

Wegwerf-Kopien des Stands `f7f9ff33` (je eine Mutation, Skript und Kopien im Scratchpad, nicht im Repo), `docker build --target test` mit gefilterten Tests (`:test` auf `SimulationBackendMergeTest`, `SimValuesTest`, `RpcBinderTest`, `RpcTypesTest`, `RpcFailureTest`, `:testserver:test` auf `TableStoreTest`, `:integratorTest` auf `MergeEmbeddingTest`, `--continue`). Jede einzeln:

| Nr. | Mutation | Rot |
|---|---|---|
| m1 | Sperre entfernt (`withLock` durch einen Aufruf ohne Sperre ersetzt) | genau `SimulationBackendMergeTest > zwei gleichzeitige MERGE desselben Schluessels laufen nacheinander` (= Protokoll N19) |
| m2 | `\|\| stopped.get()` vor dem Callback entfernt | genau `ein Abbruch zwischen Lesen und Callback schreibt nichts` (= N20; behebt F-4) |
| m3 | `stopped`-Prüfungen in `exists()` (Anfang und Schleife) entfernt | **keiner** (R-7) |
| m4 | Rundung der Ticks zu Millisekunden entfällt (Abschneiden) | `RpcBinderTest > Dezimalwerte kurzer Laenge und datetime sind gemessene Vektoren` |
| m5 | Tage von `DATETIMN` ohne Vorzeichen gelesen | derselbe Test |
| m6 | Ticks ab 24 Uhr nicht abgelehnt (= N16) | `RpcBinderTest > datetime und kurzer Dezimalwert ausserhalb des Bereichs sind abgelehnt` |
| m7 | Jahresbereich 1 bis 9999 nicht geprüft (= N22) | derselbe Test (einzeln wie N16: beide Mutationen röten ihn) |
| m8 | Untergrenze des Dezimalwerts 1 Byte statt 2 (= N21) | `RpcFailureTest > Dezimalwert ausserhalb von 2 Byte bis zur Groesse ist ein Ergebniswert` |
| m9 | Obergrenze des Dezimalwerts 17 statt Größe der TYPE_INFO | derselbe Test |
| m10 | `DATETIMN` jeder Größe als Zeitpunkt gelesen (= N17) | `RpcBinderTest > Typen ausserhalb des Mindestkatalogs sind abgelehnt` |
| m11 | Prüfung „Betrag ≥ 10 hoch Precision“ entfernt | `RpcBinderTest > datetime und kurzer Dezimalwert …` und `Dezimalwert ausserhalb des Bereichs ist abgelehnt` |

Nicht einzeln gefahren: N15 (nur die volle Größe wird angenommen), N18 (Überführung `DateTime` in `Time`), N14 und die übrigen Zeilen N1 bis N13 (im ersten Review geprüft). Die Zuordnung der Zeilen N16, N17, N19 bis N22 im Messprotokoll stimmt mit den Einzelläufen überein.

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `protocol/rpc/RpcParameterReader.kt` (`lengthFits`: Dezimalwert 2 bis Größe, andere Typen genau die Länge der TYPE_INFO), `protocol/dispatch/RpcWireValues.kt` (`datetime`: Tage `Int32` mit Vorzeichen, Ticks `UInt32`, Rundung `(10t+1)/3`, Bereich; `decimal`: Vorzeichen, Betrag unsigned bis 128 Bit, Precision), `RpcParameters.kt` (`DATETIMN` nur mit 8 Byte) | geprüft, ohne Befund: Grenzen (Länge 1 und 18, Vorzeichen außer 0 und 1, Betrag gleich 10 hoch Precision, Tag 0001-01-01 und 10000-01-01, letzter Tick), NULL (Länge 0), Mutationen m4 bis m11 rot; eine Fuzz-Abdeckung für `DECIMALN` und `DATETIMN` gibt es nicht, die Grenzfälle sind einzeln getestet |
| Sperre je Backend (`SimulationMerges.settle`, `SimulationBackend.mergeLock`) | geprüft, ohne Befund außer R-7: Ereignisse werden nach der Sperre gesendet, `CancellationException` wird von `guarded` weitergereicht (`withLock` ist abbrechbar, ein wartender `MERGE` gibt bei Abbruch oder Frist frei), Callbacks des Testservers (`TableStore`, `@Synchronized`) rufen das Backend nicht auf: kein Zyklus der Sperrenfolge; drei gleichzeitige Importe von `d-migrate` alle Exit 0 |
| Öffentliche Fläche (`SPEC-059`, [ADR-0021](../plan/adr/0021-oeffentlicher-integrations-einstieg.md)) | `make flaeche` grün; `mergeLock` ist `private`, `MergeExecution` `internal`, kein neuer öffentlicher Typ oder Mitglied im Diff (`src/main`); geprüft, ohne Befund |
| Schichten, detekt, Suppression ([AGENTS.md](../../AGENTS.md) §3.1, §3.2, §3.6) | `config/`, `Dockerfile`, `build.gradle.kts`, `Makefile`, `tools/mk` im Diff unberührt; kein `@Suppress` o. Ä.; detekt grün in `make gates` und `make clean-build`; geprüft, ohne Befund |
| Handvektoren der Draht-Tests (`MergeEmbeddingTest`, `RpcTypesTest`) | `6A 11 26 02 03 01 E2 04`, `6A 11 26 02 02 00 05`, `6A 11 26 02 06 01 FF E3 0B 54 02`, `6A 11 26 02 00`, `2A 07 08 80 ED 97 F4 6E 21 49 0B`, `68 01 01 01`, `28 03 21 49 0B`, `6F 08 08 DF 63 00 00 B4 25 DA 00` stehen byteweise in beiden Aufnahmen und in der Wiederholung; geprüft, ohne Befund (außer R-5 für die synthetischen) |
| `sp_prepexec` und `sp_execute` mit `DECIMALN` über den Draht, Fehler hinter `COLMETADATA` mit Folgeabfrage, Zwei-Spalten-Schlüssel | Tests vorhanden und lesen Antwort und Folge; geprüft, ohne Befund (Cancel am Draht: R-7) |
| `tools/jdbc-messung/Aufnahme.java`, `tools/vergleichslauf-d.sh` | Docker-only, Ausgabe `[hex=…]` ohne Kennwort (kein Treffer „passw“, „secret“, „Kennwort“ in beiden Aufnahmen); Wiederholung (Instanz 2022) ohne Eingriff grün; geprüft, ohne Befund |
| `docs/messungen`, `spec/spezifikation.md` (SPEC-030/036), Kompatibilitätsmatrix | Messvermerke `M-31`/`M-32` ohne Slice- und ADR-Verweise in der Spec; Setzungen gekennzeichnet (Bereich, Toleranz, Sperre); geprüft, ohne Befund außer R-1, R-5 |
| Haken (Reviewer-Skill „Haken vor der Verifikation gesetzt“) | im Slice kein `- [x]` im Diff, Closure-Notiz §7 leer; geprüft, ohne Befund |
| Commit-Nachrichten, `AGENTS.md` §3.4/§3.5 | jede nennt `TDS-*` und `ADR-*`; `spec/architecture.md`, `spec/lastenheft.md`, `docs/plan/adr/` unberührt; geprüft, ohne Befund |
| Sicherheit ([TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit)) | feste Meldungen des Backends, keine Werte oder Kennwörter in Ablage und Meldungen; geprüft, ohne Befund |

## Summary

Sechs MEDIUM (R-1 bis R-6), ein LOW (R-7), drei INFO (R-8 bis R-10), kein HIGH. Die Kernbehebungen halten: F-1 (Parser und Überführung für `DECIMALN` mit kurzem Wert und `DATETIMN`) ist gegen die Aufnahme, gegen die Wiederholung am echten SQL Server und gegen `d-migrate` am Testserver belegt (alle Läufe in `bestellungen` und `zeiten` Exit 0, Exporte gleich), die Sperre macht gleichzeitige Importe sicher (drei Importe, drei Exit 0), und zehn von elf Einzelmutationen röten genau die Tests, die das Protokoll nennt. Das Gewicht liegt bei den zweiten Fundstellen und der Nutzer-Doku: die Spezifikation und die E2E-Abdeckung führen `DATETIMN` weiter als abgelehnt (R-1), `testserver-manuell.md` sagt in zwei Absätzen „nur Massenladen“ (R-2), und `einbettung-und-betrieb.md` kennt `MERGE`, die Rolle des Schlüssels und die Fallen der Sperre nicht (R-3). Die Plan-Tabelle ist wieder unvollständig (R-4). Der Test, der „gemessen“ im Namen trägt, enthält Vektoren, die die Aufnahme nicht hat (R-5). Offen und nicht dem Diff zugeordnet: `make e2e` Stufe 7 ist unter hoher Last rot (R-6); ob das ein bestehendes Rennen beim doppelten Abbruch ist, klärt nur eine Messreihe auf beiden Ständen.

Der Konflikt-Pfad (Rollen-Widerspruch ab HIGH) ist nicht ausgelöst; keine Findings sind herabgestuft.
