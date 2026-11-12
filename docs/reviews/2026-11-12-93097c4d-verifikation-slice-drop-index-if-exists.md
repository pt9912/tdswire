# Verifikationsbericht: slice-drop-index-if-exists (HEAD 93097c4d)

* Rolle: Verifier (Modul 11), frischer Kontext. Gegenstand: [slice-drop-index-if-exists](../plan/planning/in-progress/slice-drop-index-if-exists.md). Maßstab: DoD §2, Plan §3, [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), [ADR-0046](../plan/adr/0046-oeffentliche-flaeche-der-katalog-elemente.md).
* Stand: Zweig `worktree-agent-ac09ca337880435ea`, HEAD `93097c4d`, Basis `main` `d019cf2d`; `git merge-tree --write-tree HEAD <main>` ist konfliktfrei. Am Produktivcode nichts geändert; Mutationen liefen in einer Kopie im Scratchpad. Haken in der DoD: keine gesetzt (0 `[x]`, 11 `[ ]`), und ich setze keine.
* Eingang: das Review [2026-11-11-5b045ff9-review-slice-drop-index-if-exists.md](2026-11-11-5b045ff9-review-slice-drop-index-if-exists.md) (0 HIGH, 3 MEDIUM, 2 LOW, 4 INFO), vom Implementer in `93097c4d` ohne zweiten Review behoben (F-1, F-2, F-4, F-8, F-9). Die Nachbesserung an `SimulationBackend.kt` (Zweig `Unchanged`) ist Planer-Code ohne Review; ich habe ihn gelesen und mutiert (M5).

## Urteil

**Konform.** Alle drei Liefer-Punkte sind belegt, die Nachweis-Pflichten bis auf die Planner-Aufgaben (Haken, Closure-Notiz, Ausgänge §6, Folge-Slice) erfüllt. Keine HIGH-, keine MEDIUM-Abweichung. Drei Hinweise (V-1 bis V-3), keiner blockiert die Closure.

## Eigene Läufe (Belege)

| Lauf | Ergebnis |
|---|---|
| `make clean-build` (einmal, auf `93097c4d`) | Exit 0; Gradle-Stufen `BUILD SUCCESSFUL` (Test-Stage 3 min 16 s, detekt-Stage 3 min 26 s); 1369 `PASSED`, 0 rote Testfälle (die eine Zeile mit `FAILED` ist der Testname `ResponseFailureTest > … FAILED_CLOSE_CONNECTION() PASSED`); darin `SimulationBackendDropIndexTest` 11 von 11, `DropIndexEmbeddingTest` 1 von 1 |
| `tools/matrix-zitate.sh` gegen das clean-build-Log | Exit 0: 1369 bestandene, 349 Zitate, 0 ohne PASSED, 0 Fremdformen (Zitate unverändert, kein `make matrix-zitate`) |
| `make e2e` | Exit 0, 8 Stufen grün, 285 s (Stufe 1 68 s, 2 56 s, 3 46 s, 4 41 s, 5 14 s, 6 14 s; 7 und 8 grün); kein Last-Flake, keine Wiederholung nötig |
| `make gates FORCE=1` | Exit 0 in 37 s; **Cache-Falle:** Stage `test` und die .NET-Tests kamen aus dem Cache (identische Eingänge, vorher clean-build bzw. Implementer-Lauf), der Lauf belegt die Verdrahtung; den Frischbeleg der Kotlin-Tests trägt `make clean-build`, die .NET-Tests (darunter `MessungBulkBelegTests`) habe ich nicht frisch gefahren (Tabelle der Sensoren sieht es nicht vor) |
| `make flaeche`, `make docs-check` | beide Exit 0 (`d-check: 1360 Datei(en) geprüft, 0 Befund(e)`) |
| Rohbytes der Ablage (24 Anfragen je Instanz) | selbst gelesen, siehe unten |
| Mutationen | 10 von 10 rot, siehe unten |
| d-migrate 1.12.0 gegen `make testserver-up` | siehe unten; Container und Images danach entfernt |

## (1) Rohbytes selbst gelesen

Die Ablagen `referenztest-dotnet/Mitschnitt/messung/bulk-merge-katalog/{2022,2025}/struktur-drop-index.messung` habe ich mit einem eigenen Dekoder (ERROR: Nummer, State, Class; DONE: Status, Befehl; Anfragetext UTF-16LE hinter dem 22-Byte-Kopf) gelesen, unabhängig von der Auswertung des Repos. **2022 und 2025 stimmen in allen 24 Zeilen überein** und decken das Messprotokoll Tabelle A:

* Treffer (07, 12, 20): `DONE` Status 0, Befehl `0x00c9`, ohne Zusatz; `IF EXISTS` ohne Treffer (08, 11) und an unbekannter Tabelle (18): ebenso Erfolg.
* 3701 (Class 11): State 7 ohne Treffer (09, 10, 13), State 6 an unbekannter Tabelle (19); `DONE` Status `0x0002`, Befehl `0x00c9`.
* 3723 (Class 16): State 5 für `uq_idx_code` mit und ohne `IF EXISTS` (14, 15), State 4 für `pk_idx_kind` (16, 17); `DONE` Status `0x0002`, Befehl `0x00fd`.
* Zeile 22: zwei `DONE` (`MORE`, dann 0), beide `0x00c9`; Zeile 23: `ERROR 3701`, `DONE` `MORE|ERROR`, `DONE` 0 (die zweite Anweisung läuft); kein zweites ERROR irgendwo. Zeile 24: Ergebnis mit einer Zeile, die Verbindung bleibt offen.
* `CREATE INDEX` (03 bis 06): `DONE` Status 0, Befehl `0x00c8`, ohne `COUNT` (F-2 gemessen).
* Die Aussagen des Protokolls zu „Treffer gelesen“ (07→09, 12→13, 20) und „Zeilen 11 und 21 ohne Lesen“ (F-1, F-9) stimmen mit der Ablage überein; die Spezifikation trägt dieselbe Einschränkung.

## (2) Mutationen (einzeln, Kopie, enge Testklasse `SimulationBackendDropIndexTest`, je ein Gradle-Lauf)

| # | Mutation | Ergebnis |
|---|---|---|
| M1 | `ifExists` invertiert (`!ifExists` im Parser) | rot, 7 von 11 |
| M2 | `IF EXISTS` an unbekannter Tabelle als Fehler (`reject(NOT_AN_INDEX)` statt `Unchanged`) | rot: `IF EXISTS an einer unbekannten Tabelle …`, `die erneute Planung …` |
| M3 | Zweig der Eindeutigkeit (`constraintIndex` → 3723) entfernt | rot: `der Index einer Eindeutigkeit ist ein Fehler mit und ohne IF EXISTS` |
| M4 | `Regex("(?:[a-zA-Z]\|_)+")` (Alternation unter `+`) auf den Namen angewendet | rot: `ein Name von 100000 Zeichen ist erkannt und beantwortet` |
| M5 | F-4: Zweig `is PlanResult.Unchanged -> 0L` in `SimulationBackend.structure()` entfernt | rot: `die erneute Planung an einer inzwischen fehlenden Tabelle ist mit IF EXISTS ein Erfolg ohne Callback` |
| M6 | Callback bekommt `table` statt der gekürzten Tabelle | rot, 4 von 11 |
| M7 | `IF EXISTS` ohne Treffer ruft keinen Callback (`Unchanged` statt `result(table)`) | rot: `IF EXISTS ohne Treffer ist ein Erfolg ohne Aenderung` |
| M8 | Name wird mit Beachtung der Schreibweise verglichen | rot: `der Name gilt ohne Beachtung der Schreibweise und je Tabelle` |
| M9 | State von 3701 von 7 auf 6 (`ErrorCode.kt`) | rot: `ErrorMapperTest > Strukturabweisung ist ERROR … 3701 und 3723 mit gemessenem State, offen` |
| M10 | 3723 nur ohne `IF EXISTS` | rot: `der Index einer Eindeutigkeit ist ein Fehler mit und ohne IF EXISTS` |

Der Nullauf ohne Mutation (Rig-Probe) war grün (11 von 11). Das Rig lief als `--user 1000:1000` mit kopiertem Gradle-Home im Scratchpad.

## (3) d-migrate 1.12.0 (Digest `sha256:308f2775…`) gegen `make testserver-up`

Eigener Container `ver-di`, Port 24999, Image `tdswire-testserver:verdi`, `TEST_UNENCRYPTED`; Soll-Dateien wie im Protokoll (Tabellen `kunden`, `bestellungen`, `lieferanten`, `auftraege` ohne Fremdschlüssel). Alle Exit-Codes und Befehlsfolgen decken Tabelle C des Messprotokolls:

| Schritt | Soll, Befehl | Exit | Beobachtung |
|---|---|---|---|
| t01 | `a`, `migrate --execute` | 0 | `CREATE TABLE` ×2, `CREATE INDEX [ix_auftraege_status]`; t02 `compare`: Exit 0 |
| t03 | `a`, zweiter Lauf | 0 | `no_op`, keine Anweisung |
| t05 | `h`, Index mit anderen Spalten | 0 | `DROP INDEX IF EXISTS [ix_auftraege_status] ON [auftraege];`, `CREATE INDEX … ([Status], [Menge]);`; t06 `IDENTICAL` |
| t07 | `i`, Index umbenannt | 0 | `CREATE INDEX [ix_auftraege_neu] …`, dann `DROP INDEX IF EXISTS [ix_auftraege_status] …`; t08 `IDENTICAL` |
| t10 | `j`, `unique: true` | 5 | `DROP INDEX IF EXISTS`, `CREATE UNIQUE INDEX`, `POST_EXECUTE_DRIFT`; t11 `DIFFERENT` (`index … [btree,unique]` gegen `constraint … (unique on [Status,Menge])`) |
| t12 | `k`, eindeutiger Index aus dem Soll | 5 | `ALTER TABLE [auftraege] DROP CONSTRAINT IF EXISTS [ix_auftraege_neu];`, `POST_EXECUTE_DRIFT`; t13 `DIFFERENT` (Register Punkt 14) |

Danach `make testserver-down` (Container entfernt), `docker rmi tdswire-testserver:verdi ver-deps:dropindex`; der d-migrate-Digest war schon lokal (nicht entfernt, gehört nicht mir).

## (4) Parser, Aussage ≤ Beleg, Fläche, Plan-vs-Code

* **Parser:** `StructureParts.drop()` ist token-basiert (`stream.keyword`, `acceptKeyword`, `parts.identifier`, `parts.tableRef`); die `Regex`-Fundstellen in `simulation/` gehören zu anderen Formen (`INTEGER`, `BulkCopyForms`, `CatalogForms`, `TransactionForms`), keine in D6. Das Gewicht trägt der Test mit 100 000 Zeichen (M4 rot).
* **Aussage ≤ Beleg:** Spezifikation (Form D6, Grenzen a bis f, Fehlertabelle, Ablaufpunkt 9, Änderungsprotokoll), KDoc (`ErrorCode`, `StructureChecks`, `StructurePlanner`, `StructureParts`, `SimulationBackend`) und Messprotokoll nennen: Schema `dbo` nur in Zeile 21 ohne Lesen; Namen je Tabelle (Zeilen 07, 09, 20); Schreibweise gemessen am eindeutigen Index, am gewöhnlichen Setzung; Treffer ohne Lesen in Zeilen 11 und 21; State 6 und Befehl `0x00c9` als Abweichung; Schlüsselname nicht im Modell; unquotiertes `IF` als Setzung (F-9, im Test). Ich fand keine Aussage, die über die Ablage hinausgeht.
* **`docs/user/` — alle fünf Dateien vollständig gelesen** (`README.md`, `referenz-ereignisse.md`, `referenz-konfiguration.md`, `testserver-manuell.md` ganz, `einbettung-und-betrieb.md` Abschnitte 1 bis 10 ganz): `README.md` und `referenz-ereignisse.md` unberührt und ohne Bezug zu D6; keine veraltete Aussage „`DROP INDEX` ist nicht erkannt“ mehr (Grep über das ganze Repo ohne Fund außerhalb von historischen Protokollen, `done/` und Reviews); die Fehlercodes-Positivliste (`einbettung-und-betrieb.md`), die Tabelle in `referenz-konfiguration.md` (3701 Class 11, 3723 Class 16, State-Zusatz) und die Zeile der Antwortformen stimmen mit `ErrorCode.kt` und der Spezifikation überein; `testserver-manuell.md` Aufgabe 7 und die Grenzen decken Tabelle C (t05, t07, t12).
* **Fläche:** `make flaeche` Exit 0; `Statement.DropIndex`, `PlanResult.Unchanged`, die zwei Codes und `SimCodes` sind `internal`; `SimModel.kt` und `SimElements.kt` unverändert, `SPEC-059` nicht geändert. `StatementKind` bleibt (Entscheid des Nutzers 2026-11-12) für den Folge-Slice; hier `UNKNOWN`.
* **Plan §3 gegen Diff:** jede geplante Datei steht im Diff, ungeplante Dateien gibt es nicht außer dem erwarteten Review-Report. Geplant „unverändert“ und unverändert: `SimModel.kt`, `SimElements.kt`, `tools/vergleichslauf-d.sh`, die `*.schema.aufnahme.txt`. `docs/plan/spec-abdeckung.md` erzeugt (Tests geändert). `harness/README.md` nicht berührt, die 200-Zeichen-Grenze der Zellen ist daher nicht berührt.
* **ADR-Konformität:** [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) (Callback mit Tabelle nach der Anweisung, keine Rücknahme, Sperre; die erneute Planung unter der Sperre behandelt jetzt `Unchanged`) und [ADR-0046](../plan/adr/0046-oeffentliche-flaeche-der-katalog-elemente.md) (Fläche wächst nicht) eingehalten.

## DoD-Zeilen

| Zeile | Befund |
|---|---|
| Messung am echten Server (Messprotokoll mit Rechner, Last, Digest) | **belegt.** Abschlusskennung `0x00c9` und Tokenfolge, 3701 ohne `IF EXISTS` und ohne Treffer, Index einer anderen Tabelle, Eindeutigkeit und Schlüssel (3723, State 5/4), Schreibweise: alle in der Ablage (1). Das Protokoll nennt Host, Load Average, beide Digests, Clients |
| Bau (Parser, Planer, Katalog; Codes nur für Gemessenes; Fläche wächst nicht; Tests mit Handvektoren und Zeilen der Messung; zweites `schema migrate` Exit 0; Mutationen je Zusage) | **belegt.** Zwei neue Codes (3701, 3723), beide gemessen; t05 und t07 Exit 0; Mutationen M1 bis M10 rot, je Zusage mindestens eine |
| Fortschreibung (`SPEC-036`, `docs/user/`, Register Punkt 9) | **belegt.** Form D6 und die Grenzen a bis f; Register: Punkt 9 gebaut, 13 (a/b geteilt) und 14 neu, 11 ergänzt |
| `make gates` grün | belegt (`FORCE=1`, Exit 0; Cache-Hinweis oben) |
| Review durchgeführt, Report liegt vor | belegt (0 HIGH, 3 MEDIUM, 2 LOW, 4 INFO); Behebung ohne zweiten Review, von mir gegengelesen |
| Haken setzt der Planner | offen, keine gesetzt |
| Doku-Update, falls öffentlicher Vertrag berührt | belegt (`docs/user/`, Spezifikation) |
| Closure-Notiz mit Steering-Loop-Eintrag, Register, Ausgänge §6, die drei Paarungen | **Planner:** §7 steht leer, §6 trägt „offen bis zur Closure“ |

## Hinweise (nicht blockierend)

* **V-1 (INFO, Planner, §6):** Vorschlag für die Ausgänge. (1) Mehrere Anweisungen in einem Text: *weiter offen / Grenze* — der Server antwortet mit zwei `DONE` (gemessen, Zeilen 22, 23), die Bibliothek erkennt mehrere Anweisungen nicht (`Unsupported SQL.`, Spezifikation Grenze e); (2) Indexname je Tabelle: *entfallen* (gemessen Zeilen 07, 09, 20, Test `je Tabelle`); (3) zweites ERROR: *entfallen* (Zeilen 09 bis 23 ohne zweites ERROR); (4) Form weicht von der Lesart ab: *eingetreten und eingearbeitet* (`IF EXISTS` an unbekannter Tabelle ist Erfolg; Eindeutigkeit und Schlüssel sind 3723, auch mit `IF EXISTS`); (5) Fläche wächst: *entfallen* (`make flaeche`; Entscheid des Nutzers zu `StatementKind` als Folge-Slice).
* **V-2 (INFO):** Die .NET-Belege (`MessungBulkBelegTests`) liefen im Gate-Lauf aus dem Layer-Cache; die Rohbytes habe ich selbst gelesen (1), damit trägt die Messaussage ohne den Cache.
* **V-3 (INFO, Prozess des Verifiers):** Ich habe versehentlich den bestehenden Scratchpad-Unterordner `.../scratchpad/ver/` weiterverwendet: dort überschrieben meine Läufe `ver/clean-build.log` (Geburtszeit 2026-11-11, ein Log einer früheren Rolle) und `ver/probe/{a,h,i,j,k}.yaml` (am 2026-11-12 06:47 neu geschrieben, vermutlich dieselben Namen wie beim Implementer). Ab da lief alles in `.../scratchpad/vdi/`. Kein Repo-Inhalt betroffen; die überschriebenen Dateien sind Zwischenstände, nicht Belege des Repos.

## Bedingungen für die Planner-Closure

1. Haken der drei Liefer-Punkte und der Nachweis-Pflichten setzt der Planner, nicht der Implementer und nicht ich (`BEO-TDS/haken-vor-verifikation-gesetzt`).
2. **Folge-Slice `slice-statementkind-index-mitglieder` anlegen** (`StatementKind` bekommt `CREATE_INDEX` `0x00c8` und `DROP_INDEX` `0x00c9`, beide ohne `COUNT`; Entscheid des Nutzers 2026-11-12), und die Paarung Folge-Slice ↔ Register Punkt 13 (a) ↔ Spezifikation Form D6 Grenze (a) schließen; die Datei ist heute noch nicht angelegt, die Verweise im Register zeigen auf einen noch nicht vorhandenen Slice (kann der `docs-check` heute nicht rot machen, weil sie als Name in Backticks stehen, nicht als Link; beim Anlegen in Link umstellen).
3. §6: jedes Risiko bekommt genau einen Ausgang (Vorschlag V-1); §7 ausfüllen (Closure-Notiz, Steering-Loop-Eintrag mit `liegt in <Zielort>` oder „keine“, Register-Zeile, Folge-Slices, Risiken).
4. Merge-Probe erneut gegen den dann aktuellen `main` (heute konfliktfrei gegen `d019cf2d`), reine `git mv`-Commits nach `done/` getrennt vom Inhalt (`AGENTS.md` §3.3), Roadmap-Ruhe-Marker und Wellenzeile, `make gates` auf dem Merge-Stand.
5. Offene Grenzen im Register bleiben Trigger-Punkte, keine Blocker: Punkt 13 (b) State 6/`0x00c9` nach 3701, Punkt 14 (d-migrate entfernt eindeutigen Index; am echten Server nicht gemessen) → [slice-abnahme-d-migrate](../plan/planning/open/slice-abnahme-d-migrate.md).

## Laufzeit

Gesamt grob eine Stunde Wanduhr (nicht eigens gemessen). Größte Posten: `make clean-build` etwa 12 Minuten (Test-Stage 3 min 16 s, detekt-Stage 3 min 26 s), Lesen von `docs/user/` und Spezifikation (fünf Dateien, etwa 162 KB), `make e2e` 285 s, zehn Mutationsläufe je 10 bis 47 s (Rig-Aufbau mit Gradle-Home-Kopie einmal 47 s), Aufbau des Testservers 34 s und d-migrate-Folge etwa 1 Minute, `make gates FORCE=1` 37 s.
