# Verifikationsbericht: slice-declare-df-standardwert-vorhandene-spalte (HEAD a23d0757)

* Rolle: Verifier (Modul 11), frischer Kontext. Gegenstand: [slice-declare-df-standardwert-vorhandene-spalte](../plan/planning/in-progress/slice-declare-df-standardwert-vorhandene-spalte.md). Maßstab: DoD §2, Plan §3, [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), [ADR-0046](../plan/adr/0046-oeffentliche-flaeche-der-katalog-elemente.md), `AGENTS.md` §3.1 bis §3.8; Vorgänger: [Review a70e06ef](2026-11-12-a70e06ef-review-slice-declare-df-standardwert-vorhandene-spalte.md) (0 HIGH, 3 MEDIUM, 3 INFO) und die Nachbesserung `a23d0757`.
* Stand: Zweig `worktree-agent-afc7e4a2dc7755c8c`, HEAD `a23d0757`, Basis `main` `206755ea`; `git merge-tree --write-tree $(git rev-parse main) HEAD` ist konfliktfrei (liefert nur den Baum-Hash `d02d4c7b`). Am Produktivcode nichts geändert; die Mutationen liefen im Arbeitsbaum mit Wiederherstellung aus Kopien im Scratchpad (`git status` danach leer, kein `git stash`).
* Entscheide des Nutzers (2026-11-12): Option A, die öffentliche Fläche wächst nicht, Namen von Standardwert und Schlüssel bleiben intern, Punkt 11 offen bis zur Abnahme; die drei Abweichungen vom echten Server (1 Antwortfolge, 2 Fehler 1781+1750 gegen 2714, 3 ENVCHANGE Typ 8 unter `IMPLICIT_TRANSACTIONS`) sind Grenze; der Entscheid „Code angleichen oder Zielbild anpassen“ steht aus. Geprüft wurde nur, dass sie überall gleich als offen geführt sind; gebaut wurde nichts.

## Urteil

**Konform bezüglich der Zusagen, mit einem MEDIUM- und fünf LOW/INFO-Befunden; keine HIGH-Abweichung.** Alle drei Liefer-Punkte sind belegt (eigene Läufe unten). Von den sechs Findings des Reviews (F-1 bis F-6) sind F-3, F-4, F-5 und F-6 behoben; **F-1 und F-2 sind an den im Review genannten Stellen behoben, aber nicht an allen zweiten Fundstellen** (V-1). Die Haken in §2 sind nicht gesetzt (geprüft: kein `[x]` in der Slice-Datei), §7 ist leer und §6 steht auf „offen bis zur Closure“: das ist Planner-Aufgabe.

## Eigene Läufe (Belege)

| Lauf | Ergebnis |
|---|---|
| `make clean-build` (einmal, auf `a23d0757`) | Exit 0, 558 s; `BUILD SUCCESSFUL in 3m 4s` (Test-Stage, detekt-Stage); 1383 `PASSED`, 0 rote Testfälle (die eine Zeile mit ` FAILED` ist der Testname `ResponseFailureTest > … FAILED_CLOSE_CONNECTION() PASSED`); die neuen Klassen im Log: `SimulationBackendDropDefaultTest` 8 von 8, `DropDefaultEmbeddingTest` 1 von 1 |
| `make e2e` | Exit 0, 8 Stufen grün, 383 s (Stufe 1 73 s, 2 108 s, 3 59 s, 4 55 s, 5 16 s, 6 20 s, 7 37 s, 8 15 s); Last Average 8,0 zu Beginn; kein Flake, keine Wiederholung |
| `make gates FORCE=1` | Exit 0 in 36 s. **Cache-Falle:** die Stages kamen aus dem Docker-Cache (gleiche Eingänge wie der clean-build); der Lauf belegt Verdrahtung, Selbsttests, Stempel, `d-check` (`1376 Datei(en) geprüft, 0 Befund(e)`) und `flaeche` frisch, die Testergebnisse stammen aus dem clean-build-Log |
| `make flaeche`, `make docs-check` | beide Teil von `make gates` (Exit 0); `flaeche` mit Selbsttest grün |
| `make referenztest-dotnet` | Exit 0, aber vollständig `CACHED` (kein Testausgabe-Beleg). Frischlauf der Stage `test` (`docker build --no-cache-filter test --target test referenztest-dotnet`): `Passed! Failed: 0, Passed: 160, Skipped: 0, Total: 160`, 6,7 s |
| `make matrix-zitate` | nicht gefahren: `docs/plan/kompatibilitaetsmatrix.md` ist nicht im Diff |
| Mutationen | 4 von 4 rot (unten) |
| d-migrate 1.12.0 gegen `make testserver-up` | 6 von 6 Schritten Exit 0 und `IDENTICAL` (unten); Container und eigenes Image entfernt |

## (1) Draht: Rohbytes selbst dekodiert

Die Ablagen `referenztest-dotnet/Mitschnitt/messung/bulk-merge-katalog/{2022,2025}/struktur-standardwert-ersetzen.messung` habe ich mit einem eigenen Dekoder gelesen (Scratchpad, nicht der des Repos: SQL_BATCH-Text aus UTF-16 nach der Kopfstruktur, Antwort Token für Token). 27 Anfragen je Instanz; die Tokenfolgen und `DONE`-Felder von 2022 und 2025 sind in jeder Zeile gleich (nur Prelogin und Login unterscheiden sich in den Bytes, 41018 und 41038 Byte wie im Protokoll). Zeile = Nummer der Anfrage.

| Zeile | Dekodiert | Messprotokoll Tabelle A |
|---|---|---|
| 04 | `ERROR 1781`, `ERROR 1750`, `DONE ERROR 0x00fd` | stimmt |
| 05, 09, 12, 13, 18, 22 (Treffer) | `DONE MORE\|COUNT 0x00c1 n=1`, `DONE MORE 0x00c0`, `DONE MORE\|COUNT 0x00c1 n=1`, `DONEINPROC MORE 0x00d8`, `RETURNSTATUS 0`, `DONEPROC 0x00e0` (ohne weiteres `ENVCHANGE`, auch in 22) | stimmt |
| 10, 11, 14, 15, 23 (kein Treffer) | `DONE MORE\|COUNT 0x00c1 n=0`, `DONE 0x00c0` (ohne Bits) | stimmt |
| 07 | `DONE 0x00d8`, ohne Bits, ohne `COUNT` | stimmt |
| 21 | `ENVCHANGE` Typ 8, dann die zwei `DONE` wie Zeile 10 | stimmt |
| 24 | `DONE MORE 0x00c0`, `ENVCHANGE` Typ 9, `DONE 0x00d5` | stimmt |
| 03, 06, 08, 16, 17, 19, 26 (Kontrollen) | `COUNT` 4, 3, 4, 1, 1, (leer), (leer) | stimmt mit den Aussagen (vier, drei, vier, `Impl`, `df_zwei.Menge`, keiner, keiner) |

Der Wortlaut der Anfrage 05 (415 Zeichen für `df_kind`/`Menge`, entspricht 419 für `auftraege`/`Menge`) ist zeichengleich mit der Vorlage `dMigrateDeclareDefaultText` in `DMigrateWire.kt` und mit der Schablone in `DefaultDropForms.kt`; die Belegtests `MessungBulkBelegTests` (160 .NET-Tests grün) sprechen dieselben Tokenfolgen und Felder aus. Die Aussage „Ohne Treffer kürzer als mit Treffer“ und „1781/1750 am zweiten Standardwert“ sind belegt.

## (2) d-migrate 1.12.0 (Digest `sha256:308f27753036c9cc423f716b9fc4c9e68016543a8003ecd2f8282e05a2ee6491`) gegen `make testserver-up`

Container `verif-df7-ts`, Port 24981, Image `tdswire-testserver:verif-df7` (aus dem Arbeitsbaum gebaut), Beschreibung `sim.yaml`. Eigene Soll-Dateien `a` bis `e` nach dem Aufbau der Tabelle B des Messprotokolls (`lieferanten`, `auftraege` mit Fremdschlüssel, `Menge`, `Status`). Je `schema migrate --execute --report …`, danach `schema compare`:

| Soll | `migrate` Exit | Bericht | `compare` |
|---|---|---|---|
| `a` (ohne Standardwerte) | 0 | 2 Anweisungen (`CREATE TABLE`) | `IDENTICAL`, Exit 0 |
| `b` (Standardwerte neu: `Menge` 1, `Status` `neu`) | 0 | 4 Anweisungen, je Spalte der Text mit `DECLARE @df` und das `ADD CONSTRAINT` | `IDENTICAL` |
| `b` zweiter Lauf | 0 | 0 Anweisungen | `IDENTICAL` |
| `c` (`Menge` 1 auf 2: geändert) | 0 | 2 Anweisungen (Text und `ADD CONSTRAINT`) | `IDENTICAL` |
| `d` (`Status` weggenommen) | 0 | 1 Anweisung (Text für `Status`) | `IDENTICAL` |
| `e` (`Menge` weggenommen) | 0 | 1 Anweisung (Text für `Menge`) | `IDENTICAL` |

`docker logs`: 157 Zeilen, 0 mal `request_failed`, 0 mal `unsupported`. Das deckt sich mit Tabelle B (s03, s05, s06, s08, s10). Danach `make testserver-down` (eigener Name, eigener Port) und `docker rmi tdswire-testserver:verif-df7`; Images und Volumes anderer Läufe (`viper-*`, `u-boot-*`, `odata-recorder`, `tdswire-testserver:*`) sind nicht berührt. Die Soll-Dateien liegen im Scratchpad, nicht im Repo (das Messprotokoll sagt dasselbe von seinen).

## (3) Mutationen (jede einzeln, enge Testklassen `SimulationBackendDropDefaultTest` und `DropDefaultEmbeddingTest`, Kopie von `Dockerfile` im Scratchpad, Stage `test` mit Filter, je rund 60 s)

| Nr | Mutation (`StructurePlanner.dropDefault`) | Ergebnis |
|---|---|---|
| M1 | der Standardwert bleibt (`it.copy(defaultDefinition = null)` zu `it`) | rot: `der Text von d-migrate nimmt den Standardwert der Spalte aus der Tabelle`, `Tabelle und Spalte gelten ohne Beachtung der Schreibweise`, Einbettungstest (2 von 8 und 1 von 1) |
| M2 | alle Spalten statt einer verlieren den Standardwert | rot: `ohne Standardwert und an unbekannter Spalte oder Tabelle gelingt der Text ohne Änderung`, `ein Name von 100000 Zeichen …`, `der Text von d-migrate nimmt …`, `Tabelle und Spalte gelten …`, Einbettungstest (4 von 8 und 1) |
| M3 | unbekannte Tabelle lehnt ab (`Rejected` statt `Unchanged`) | rot: `ohne Standardwert und an unbekannter Spalte oder Tabelle …`, `ohne Callback ist der Text an einer bekannten Tabelle abgewiesen`, Einbettungstest |
| M4 | Art `UNKNOWN` statt `ALTER_TABLE` (beide Stellen der Funktion) | rot: fünf Testfälle der Klasse und der Einbettungstest (der Rohbytes-Test sieht die Kennung) |

Die Zeile `:testserver:test FAILED` in den Logs ist der Filter ohne Treffer im Nachbarmodul, nicht eine Wirkung der Mutation. Danach `git status` leer. Die vier Mutationen des Reviews (R-A bis R-D: Linearität, Rückverweis, Schreibweise, Abschluss `;`) habe ich nicht wiederholt; ihre Belege im Review sind plausibel gegen den Code (besitzende Quantoren `++` in `DefaultDropForms.kt`, Rückverweis `\\1`).

## (4) Öffentliche Fläche, `when` über `Statement`

* `Statement` ist `internal sealed interface`; `Statement.DropDefault` und `DefaultDropForms` sind nur dadurch sichtbar (`DefaultDropForms` ist `internal object`). Im Diff von `src/main` steht kein neues `public`; `src/main/…/domain` ist unverändert; `StatementKind` bekommt kein neues Mitglied. Neu unter `src/integrator` ist nur die Testklasse `DropDefaultEmbeddingTest` (nur öffentliche Fläche, grün im clean-build). `make flaeche` grün (Typnamen; Mitglieder vergleicht es nicht, das trägt hier die Tatsache, dass `src/main` ohne neues öffentliches Mitglied bleibt).
* Die drei erschöpfenden `when` über `Statement` (`SimulationChanges.kt` Zeile 62, `StatementPlanner.kt` Zeilen 154 und 188) tragen `is Statement.DropDefault`; der Compiler erzwingt es (Bau grün).

## (5) Plan-Tabelle §3 gegen Diff, `docs/user/`, `harness/`

| Plan-Zeile | Diff |
|---|---|
| sechs Quelldateien (`DefaultDropForms`, `Statement`, `StatementParser`, `StatementPlanner`, `StructurePlanner`, `SimulationChanges`) | alle geändert beziehungsweise neu wie geplant |
| `SimulationBackendDropDefaultTest`, `DMigrateWire.kt`, `DropDefaultEmbeddingTest` | wie geplant |
| `MessungBulkTests.cs`, `MessungBulkBelegTests.cs`, zwei Ablagen, `vergleichslauf-d.mk` | wie geplant (22 Szenarien in `.mk`, `referenzabgleich.md` und `Assert.Equal(22, …)`; nachgezählt: 22 Dateien je Stufe) |
| Messprotokoll, `referenzabgleich.md`, `spezifikation.md`, drei Nutzer-Dateien, `spec-abdeckung.md`, Register, Roadmap | alle im Diff; `spec-abdeckung.md` jetzt in der Tabelle (F-3 behoben) |
| nicht in der Plan-Tabelle, im Diff | Zeigerkorrekturen der Lifecycle-Verschiebung (`done/slice-drop-constraint-form-d5.md`, `done/slice-drop-index-if-exists.md`, `done/slice-katalog-indizes-defaults-fremdschluessel.md`, `open/slice-abnahme-d-migrate.md`, `welle-testserver-manuell.md`, `docs/reviews/…e5c345aa…`) und der Review-Bericht: Folge des `slice-mv` beziehungsweise des Reviews, kein Planabweichung |
| `harness/README.md` | **nicht berührt** (kein Eintrag unter `harness/` im Diff); die Zellenregel (≤ 200 Zeichen) hat keinen Gegenstand |
| Haken (`- [x]`) | keiner gesetzt (Slice-Datei: 0) |

**`docs/user/`, alle fünf Dateien.** `README.md` (31 Zeilen) vollständig gelesen; `referenz-ereignisse.md` (78 Zeilen) auf alle Fundstellen zu Standardwert, `DECLARE`, `ALTER_TABLE` und „abschließend“ durchsucht (kein Treffer, keine Änderung nötig); `referenz-konfiguration.md`, `einbettung-und-betrieb.md` und `testserver-manuell.md` habe ich nicht Zeile für Zeile gelesen, sondern über alle Treffer auf `Standardwert`, `DECLARE`, `Unsupported`, `Exit 5`, `mehrere Anweisungen`, `nicht erkannt`, `noch nicht`, `DROP CONSTRAINT`, `1781`, `2714`, `ENVCHANGE`, `implicit_transactions`, `ALTER_TABLE` und `DROP INDEX` und die umgebenden Zeilen. Befund: die alte Verneinung ist weg (`testserver-manuell.md` Zeile 691: „außer dem Text mit `DECLARE @df …`“), die Listen in Zeile 207 und 691 und `einbettung-und-betrieb.md` Zeile 253 nennen den Text, Zeile 316 trägt (a) bis (d) und die Abweichungen (b) und (c), `referenz-konfiguration.md` Zeile 88 (Antwort) und Zeile 92 (ENVCHANGE) ebenso. Offen bleiben V-1 (Ziel-Satz) und V-5.

## (6) Abdeckungsmatrix

`docs/plan/spec-abdeckung.md`: SPEC-036 236 → 245 (+9 = 8 Tests `SimulationBackendDropDefaultTest` + 1 `DropDefaultEmbeddingTest`), SPEC-045 88 → 92 (+4 Belegtests), SPEC-059 55 → 56 (+1 Einbettungstest), Testklasse `DropDefaultEmbeddingTest` in beiden Zeilen ergänzt. Die Differenzen stimmen mit den Testmethoden überein. Den Lauf selbst nicht wiederholt (nicht Teil der Sensor-Tabelle, kein Zweifel).

## (7) Nachprüfung der Findings F-1 bis F-6 des Reviews

| Finding | Stand | Beleg |
|---|---|---|
| F-1 (Aufzählungen, Verneinung) | **teilweise behoben** | behoben: `SPEC-036` „außer Form C und dem Text der Form D7“ (Zeile 1189), `SPEC-035` Liste (Zeile 1103), `testserver-manuell.md` Zeilen 207 und 691. Nicht behoben an weiteren Fundstellen: V-1 |
| F-2 (Grenzen D7 (a) bis (e) an allen Fundstellen) | **behoben für die Grenzen (b), (c) an den im Review genannten Stellen; (e) nicht in der Nutzer-Doku, Änderungsprotokoll ohne (c)** | (b): `SPEC-035` Zeile 1373, Einbettung 316, Testserver 657, Register, Messprotokoll, D7; (c): `SPEC-031` Zeile 799, D7 (c), Referenz-Konfiguration 92, Einbettung 316, Testserver 657, Register, Messprotokoll; (a) steht weiter überall gleich. Rest: V-4, V-5 |
| F-3 (Plan-Tabelle) | behoben | Zeile `spec-abdeckung.md` in §3; SPEC-031, SPEC-045, SPEC-059 im Kopf |
| F-4 (Ziel „ADD CONSTRAINT“) | behoben | §1 „Ziel“ nennt `DROP CONSTRAINT` mit dem gefundenen Namen und dass das `ADD CONSTRAINT` als Form D3 folgt; Liefer-Punkt „Bau“: Parameter Tabelle und Spalte |
| F-5 (`sqlLength` 418 gegen 419) | behoben | Spezifikation D7 und Messprotokoll nennen beides; ich habe `"sqlLength":418` in der Aufnahme gezählt (1) und 419 aus dem Draht |
| F-6 (Begründung als Fakt) | behoben | Spezifikation (a), Register 15 (a), Messprotokoll: „Entscheid des Nutzers … Option A … nicht untersucht, ob die vorhandenen Mittel die Folge ausdrücken können“; die Nutzer-Doku sagt „Entscheid: die öffentliche Fläche wächst dafür nicht“ |

## Befunde

**HIGH:** keine.

* **V-1 (MEDIUM, Fortschreibung lässt zweite Fundstelle stehen, `AGENTS.md` §3.8 und Reviewer-Skill):** die Aufzählung der Strukturformen nennt den Text der Form D7 an vier weiteren Stellen nicht, obwohl dieselben Listen `DROP INDEX` und `DROP CONSTRAINT` (Formen D5, D6) führen und der Slice sie in „Berührte Spec-Stellen“ nennt (`TDS-FA-11.a`, Ablaufpunkt 9). Keine der Stellen widerspricht D7; sie geben eine kürzere Liste als die Wirklichkeit an. Fundstellen: (a) `spec/spezifikation.md` Ablaufpunkt 9 (Zeilen 223 bis 227): „Strukturänderung (Form D): `CREATE TABLE`, `ALTER TABLE … ADD`, `ALTER TABLE … DROP CONSTRAINT`, `CREATE INDEX` und `DROP INDEX` werden gegen den Katalog dieser Anfrage geprüft … Abschluss der Art `CREATE_TABLE`, `ALTER_TABLE`, `CREATE_INDEX` bzw. `DROP_INDEX`"; (b) `spec/spezifikation.md` `SPEC-031` Zeile 760: „`ALTER_TABLE` `0x00d8` (`CREATE TABLE`, `ALTER TABLE … ADD`, `ALTER TABLE … DROP CONSTRAINT`)“; (c) der KDoc des öffentlichen `StatementKind.ALTER_TABLE` (`src/main/…/domain/StatementKind.kt` Zeilen 28 bis 30: „`ALTER TABLE … ADD` und `ALTER TABLE … DROP CONSTRAINT`“) und des öffentlichen `SimulationBackend` (`src/main/…/simulation/SimulationBackend.kt` Zeilen 43 bis 47: „Form D (… `DROP INDEX [IF EXISTS]` eines Index) ruft den Strukturänderungs-Callback … `ALTER_TABLE`, `CREATE_INDEX` oder `DROP_INDEX`“); (d) `docs/user/einbettung-und-betrieb.md` Zeile 278 („**Ziel.** … nehmen Bedingungen mit `ALTER TABLE … DROP CONSTRAINT` und Indizes mit `DROP INDEX` weg“, nennt den Standardwert nicht). Auffindbar mit `git grep -n "DROP CONSTRAINT" -- spec src/main docs/user`. Die Zusage trägt der Slice trotzdem (die Form D7 steht in `SPEC-036` Tabelle und Absatz, `SPEC-031` Zeile 1109, der Callback-Beschreibung); gemäß F-1 des Reviews ist die Klasse dieselbe, deshalb MEDIUM, nicht HIGH, und kein Hindernis für die Zusage, aber für die Closure des Planners, wenn „Spec nachgezogen“ der Maßstab ist.
* **V-2 (LOW, Änderungsprotokoll unvollständig):** der Eintrag 2026-11-12 in `spec/spezifikation.md` (Zeile 2244) nennt als Grenzen die Antwort (a) und den zweiten Standardwert (b), nicht das `ENVCHANGE` Typ 8 (c), obwohl (c) in D7 und überall sonst steht. Der Eintrag ist eine Zusammenfassung; er nennt aber ausdrücklich „Grenzen:“ und zählt zwei von drei Abweichungen auf.
* **V-3 (LOW, „überall gleich als offen geführt“):** die drei Abweichungen sind als **offen** nur an zwei Stellen ausdrücklich geführt: im Register (`state.md`: „Trigger: Entscheid des Nutzers, ob der Testserver dem echten Server angeglichen oder das Zielbild angepasst wird“; Punkt 15, Trigger: „Entscheid des Nutzers (Code angleichen oder Zielbild anpassen) …“) und im Messprotokoll („nicht gebaut, Entscheid beim Nutzer“). `SPEC-036` D7 (a) bis (c) und die Nutzer-Doku nennen sie als Tatsache der Grenze und (a) mit „Entscheid des Nutzers: die öffentliche Fläche wächst dafür nicht (Option A)“, aber nicht, dass der Entscheid über das Angleichen aussteht; die Spezifikation verweist per „Träger je Punkt“ auf das Register. Lesbar ist das als Rang-Zeiger (§3.7), nicht als Widerspruch; der Satz „Entscheid des Nutzers: … Option A“ in (a) könnte aber als Abschluss der Frage gelesen werden, die für (a) noch offen ist, solange „Zielbild anpassen“ eine Möglichkeit ist. Empfehlung: in D7 (a) bis (c) einen Satz „Entscheid, ob angeglichen oder das Zielbild angepasst wird: Nutzer, offen (Register Punkt 15)“, oder bewusst bei dem Rang-Zeiger belassen.
* **V-4 (INFO, Nutzer-Doku und Grenze (e)):** die Grenze D7 (e) („nicht gemessen: der Text im RPC, an einer Tabelle mit Zeilen, in einer Transaktion des Referenzclients, d-migrate am echten Server mit einem neu gegebenen oder weggenommenen Standardwert“) steht in Spezifikation, Register und Messprotokoll, nicht in der Nutzer-Doku (nur der RPC-Fall indirekt in `referenz-konfiguration.md` Zeile 88). Die Nutzer-Doku bildet den Ist-Zustand ab (`AGENTS.md` §5 Regel 5); eine Messgrenze gehört dort nicht zwingend hin. Ich melde es, weil der Review (F-2) (a) bis (e) an allen Fundstellen verlangte.
* **V-5 (INFO, Planner-Closure):** `docs/plan/planning/welle-testserver-manuell.md` Zeile 143 führt den Slice weiter als „M (Setzung, zurückgestellt)“, und `docs/plan/planning/open/slice-abnahme-d-migrate.md` Zeile 85 sagt „ist zurückgestellt und wird ausgelöst, wenn …“, während der Slice in `in-progress/` liegt. Beides sind Zustandsfelder (§3.7); sie gehören in die Closure des Planners (Zeiger sind durch den `slice-mv` korrekt, der Text nicht).
* **INFO-1 (Cache):** `make gates FORCE=1` (36 s) und `make referenztest-dotnet` (1,9 s) zeigen wegen des Docker-Caches keine Testausgabe; die frischen Belege sind das clean-build-Log (Kotlin) und mein Frischlauf der .NET-Stage (160 grün). Das Messprotokoll sagt ehrlich, dass die Läufe gegen den Testserver (Tabelle B) von Hand gefahren wurden und die Skripte nicht im Repo liegen; ich habe die Tabelle B mit eigenen Soll-Dateien (Scratchpad) nachgefahren und stimmend gefunden.
* **INFO-2 (Aussage ohne Wächter):** „Tabelle und Spalte gelten ohne Beachtung der Schreibweise (gemessen, Voreinstellung der Kollation)“ (Zeilen 12 und 13 der Ablage) steht im Messprotokoll mit dem Vorbehalt „Kollation der Datenbank … nicht eigens erhoben“; die Spezifikation und die Nutzer-Doku tragen denselben Vorbehalt. Kein Befund, nur festgehalten.

**Negativbefund-Pflicht** (die Suche, die nichts fand): kein `@Suppress`/`@SuppressWarnings` im Diff (Grep über `src`, `tools`, `referenztest-dotnet`; detekt grün); kein neues `public` in `src/main`; kein `[x]` in der Slice-Datei; `harness/` und `config/`, `Dockerfile`, `Makefile`, `build.gradle.kts` nicht berührt (keine Lockerung eines Gates, `AGENTS.md` §3.6); keine ADR geändert (§3.5), `spec/architecture.md` nicht berührt (§3.4); keine `ADR-`-Kennung in den neuen Zeilen von `SPEC-036`; kein `docker run` ohne `--user` auf dem Host-Verzeichnis; kein lokales Gradle (§3.1); die Kommentare in `DefaultDropForms.kt` und `StructurePlanner.dropDefault` beschreiben den Zustand (Zusage, Abgrenzung, Setzung), nicht die verworfene Alternative (§3.7); die Aussagen der KDoc und der Spezifikation tragen so weit wie die Ablage (Ausnahme: siehe V-1, dort nur Unvollständigkeit); Register und Roadmap nennen Zustand und Beleg als auflösbaren Anker (`state.md`: Messprotokoll und Slice verlinkt), die Roadmap-Zeile „In Arbeit“ ist ein Zustand, die Drift-Zeile ist nur ein Zeiger-Nachzug.

## Bedingungen für die Planner-Closure

1. Die Haken der drei Liefer-Punkte und der Nachweis-Pflichten setzt der Planner (kein Haken ist gesetzt); der Haken „Review durchgeführt“ trägt der Bericht [a70e06ef](2026-11-12-a70e06ef-review-slice-declare-df-standardwert-vorhandene-spalte.md).
2. V-1 (MEDIUM) bereinigen oder bewusst als offen führen: Ablaufpunkt 9 und `SPEC-031` Zeile 760 in `spec/spezifikation.md`, KDoc von `StatementKind.ALTER_TABLE` und `SimulationBackend`, Ziel-Satz in `einbettung-und-betrieb.md` Zeile 278 um den Text mit `DECLARE @df` ergänzen. Die KDoc-Änderung ist ein Eingriff in `src/main` (kein neues Mitglied, keine Fläche); sie braucht nach der Änderung `make gates`, nicht `make clean-build`. V-2 bis V-5 nach Ermessen oder als Beobachtung im Register `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` (Zähler) fortschreiben.
3. Die Entscheidung des Nutzers „Code angleichen oder Zielbild anpassen“ bleibt offen; sie ist **keine** Bedingung der Closure dieses Slice (Register Punkt 15 trägt sie; Trigger: Entscheid des Nutzers oder ein Client, der die Antwortform oder die Nummer auswertet). Punkt 11 bleibt offen bis zur Abnahme (Option A).
4. §6 des Slice: jedes Risiko erhält seinen Ausgang. Vorschlag: „Version von d-migrate“: weiter offen (Wortlaut ist der von 1.12.0, Digest gepinnt, Test mit dem Wortlaut des Mitschnitts); „anderer Client mit eigenem `DECLARE`“: weiter offen (abgewiesen, `TDS_UNSUPPORTED_SQL`, in der Nutzer-Doku genannt).
5. §7 Closure-Notiz mit Steering-Loop-Lerneintrag (Anker-Paarung); Lerneintrag-Kandidat: die Fortschreibung der Aufzählungen (V-1) war im Review zum zweiten Mal Gegenstand, ein Wächter wäre die Suche nach der Liste der Formen (`DROP INDEX` zusammen mit `DROP CONSTRAINT`) in einer Checkliste des Implementers.
6. Register `BEO-TDS/katalog-elemente-grenzen-der-angleichung`: Beleg-Datei `evidence/slice-declare-df-standardwert-vorhandene-spalte.md` wie bei den Vorgängern (Punkt 1 gebaut, Punkt 15 neu, Punkt 11 offen); `state.md` bei der Closure auf „in `done/`“ umstellen; V-5 (Welle-Tabelle, Abnahme-Slice) im selben Zug.
7. Danach der reine `git mv` nach `done/` und der Zeiger-Nachzug (Register, Roadmap, Welle); `make gates` auf dem End-Stand nach dem Move. Merge-Probe gegen `main`: konfliktfrei auf `206755ea`; bei weiteren Merges in `main` vor dem Zusammenführen wiederholen.
