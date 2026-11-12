# Verifikationsbericht: slice-statementkind-index-mitglieder (HEAD 7d8bb877)

* Rolle: Verifier (Modul 11), frischer Kontext; der Diff ist dabei kritisch wie von einem Reviewer gelesen (Entscheid des Orchestrators: kleiner Slice, kein eigener Reviewer). Gegenstand: [slice-statementkind-index-mitglieder](../plan/planning/done/slice-statementkind-index-mitglieder.md). Maßstab: DoD §2, Plan §3, [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-05](../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [ADR-0042](../plan/adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) (Entscheidung 2: Mitglied erst nach Messung), [ADR-0046](../plan/adr/0046-oeffentliche-flaeche-der-katalog-elemente.md), Hard Rules `AGENTS.md` §3.1 bis §3.8.
* Stand: Zweig `worktree-agent-a715650d459be884c`, HEAD `7d8bb877`, Basis `main` `ea792f47`; `git merge-tree --write-tree <main> HEAD` ist konfliktfrei (liefert nur den Baum-Hash `08b1b7b4`). Am Produktivcode nichts geändert; Mutationen liefen in Kopien im Scratchpad, nicht im Arbeitsbaum.
* Entscheid des Nutzers (2026-11-12, per Auswahl): `CREATE_INDEX` (`0x00c8`) und `DROP_INDEX` (`0x00c9`), beide ohne `COUNT`; `DROP CONSTRAINT` bleibt bei `ALTER_TABLE`. Die Enum-Position am Ende ist eine Setzung des Planners, nicht des Nutzers.

## Urteil

**Konform, mit vier LOW- und vier INFO-Befunden; keine HIGH-, keine MEDIUM-Abweichung.** Alle drei Liefer-Punkte sind belegt. Die Haken in §2 sind nicht gesetzt (geprüft: kein `[x]` in der Datei), die Closure-Notiz §7 und die Ausgänge in §6 sind leer beziehungsweise „offen bis zur Closure“: das ist Planner-Aufgabe.

## Eigene Läufe (Belege)

| Lauf | Ergebnis |
|---|---|
| `make clean-build` (einmal, auf `7d8bb877`) | Exit 0, 547 s; Gradle-Stufen `BUILD SUCCESSFUL` (Test-Stage 2 min 53 s, detekt-Stage 3 min 2 s); 1374 `PASSED`, 0 rote Testfälle (die eine Zeile mit ` FAILED` ist der Testname `ResponseFailureTest > … FAILED_CLOSE_CONNECTION() PASSED`) |
| `tools/matrix-zitate.sh` gegen das clean-build-Log | Exit 0: 1374 bestandene, 352 Zitate, 0 ohne PASSED, 0 Fremdformen (die Matrix trägt neue Zitate, daher gefahren) |
| `make e2e` | Exit 0, 8 Stufen grün, 324 s (Stufe 1 68 s, 2 88 s, 3 48 s, 4 43 s, 5 16 s, 6 15 s, 7 34 s, 8 12 s); Last Average 5,7 vorher; kein Flake, keine Wiederholung |
| `make gates FORCE=1` | Exit 0 in 35 s. **Cache-Falle:** die Stages (auch `test` und die .NET-Tests) kamen aus dem Docker-Cache (identische Eingänge nach dem clean-build); der Lauf belegt Verdrahtung, Selbsttests, Stempel und die schnellen Gates frisch, die Test-Ergebnisse stammen aus dem clean-build-Log |
| `make flaeche`, `make docs-check` | beide Exit 0 (`d-check: 1367 Datei(en) geprüft, 0 Befund(e)`) |
| Mutationen | 7 von 7 rot (siehe unten) |
| d-migrate 1.12.0 gegen `make testserver-up` | siehe unten; Container und Image entfernt |

## (2) Draht: Rohbytes selbst gelesen

Die Ablagen `referenztest-dotnet/Mitschnitt/messung/bulk-merge-katalog/{2022,2025}/struktur-drop-index.messung` habe ich mit einem eigenen Dekoder gelesen (Client-SQL aus dem SQL_BATCH, Antwort als Hex). Die „Zeilen“ des Messprotokolls sind die Nummern der Anweisungen (Anweisung 1 und 2 sind `CREATE TABLE`, `0x00c6`).

* Anweisungen 3 bis 6 (`CREATE INDEX [ix_idx_name] ON [idx_kind]`, `CREATE UNIQUE INDEX [ux_idx_menge] …`, `CREATE INDEX [ix_idx_name] ON [idx_eltern]`, `CREATE INDEX [ix_idx_nur_eltern] … ([Name], [Id])`): Antwort `FD 00 00 C8 00` und acht Nullbytes, in 2022 und 2025 gleich. Anweisung 7 (`DROP INDEX IF EXISTS … ON [idx_kind]`): `FD 00 00 C9 00` + 8 Nullbytes; ebenso der `DROP INDEX IF EXISTS [x] ON [idx_gibtesnicht]` (Anweisung 18). Status 0, also kein `COUNT`, kein `MORE`.
* Die Handvektoren in `ResponseStatementKindTest > Im Batch schliessen CREATE INDEX und DROP INDEX ohne COUNT` (`FD 00 00 C8 00 00 00 00 00 00 00 00 00`, 13 Bytes, und `…C9…`) stimmen byte-gleich mit der Ablage überein. Die Zusage „drei Formen“ (gewöhnlich, eindeutig, zweispaltig) trägt die Ablage mit vier Anweisungen.
* Im RPC und im Massenladen gilt die Kennung nicht: der Test `Im RPC und im Massenladen gilt die Kennung der Strukturaenderung nicht` fordert für `CREATE_INDEX`/`DROP_INDEX` im RPC `DONEPROC` mit Kennung 0 und `COUNT`. Das ist keine Messung (die Strukturänderungen sind nur im SQL_BATCH gemessen), sondern dieselbe Setzung wie bei `CREATE_TABLE` und `ALTER_TABLE`; die Spezifikation und die Nutzer-Doku sagen es („im RPC … steht die Kennung 0 mit `COUNT`“). `DONE` mit `COUNT` oder `MORE` in der Ablage gibt es nur in Anweisung 22 (zwei `DROP INDEX` in einem Text, `MORE` `0x0001` zwischen den Abschlüssen): außerhalb des Slice (Grenze (e) der Form D6).

## (1), (4) Mutationen (jede einzeln, enge Testklasse, Kopie im Scratchpad, `docker build --target test`)

| Nr | Mutation | Testklasse | Ergebnis |
|---|---|---|---|
| M1 | `forKind(CREATE_INDEX) → NONE` | `ResponseStatementKindTest` | rot: `Im Batch schliessen CREATE INDEX und DROP INDEX ohne COUNT` |
| M2 | `DROP_INDEX` in `countsRows` (wahr statt falsch) | `ResponseStatementKindTest` | rot: dieselbe Methode und `Im RPC und im Massenladen gilt die Kennung der Strukturaenderung nicht` |
| M3 | `CREATE_INDEX` beginnt eine Transaktion (Zweig von `CREATE_TABLE` in `SessionTransaction.apply`) | `ResponseTransactionTest` | rot: `CREATE INDEX und DROP INDEX beginnen keine Transaktion` |
| M5 | `dropIndex` an unbekannter Tabelle meldet `UNKNOWN` | `SimulationBackendDropIndexTest` | rot: `IF EXISTS an einer unbekannten Tabelle ist ein Erfolg ohne Callback` |
| M7 | `forKind(DROP_INDEX) → CREATE_INDEX`-Kennung | `ResponseStatementKindTest` | rot: `Im Batch schliessen CREATE INDEX und DROP INDEX ohne COUNT` |
| M8 | Enum-Reihenfolge: `CREATE_INDEX`, `DROP_INDEX` vor `MERGE` (Ordinal von `MERGE` verschoben) | `StatementKindEmbeddingTest` (Source-Set `integrator`) | rot: `dieFlaecheNenntDieKonstantenDasFeldUndDenKonstruktorFuerJava` (Listenvergleich `StatementKind.entries.map { it.name }`; die Meldung zeigt beide Reihenfolgen) |
| M9 | Form D4 (`createIndex`) meldet `UNKNOWN` | `SimulationBackendElementsStructureTest` | rot: `CREATE INDEX meldet die Art CREATE_INDEX` und `die Texte von d-migrate legen Elemente an` |

(1) Die Ordinale der alten Mitglieder bleiben: die Position am Ende ist durch den Listenvergleich festgehalten, und er wurde rot gesehen (M8). Ein Wächter für die Ordinalzahl selbst (`ordinal`) gibt es nicht; der Listenvergleich ist bei einer Aufzählung gleichwertig.

## (5) d-migrate 1.12.0 (Digest `sha256:308f27753036c9cc423f716b9fc4c9e68016543a8003ecd2f8282e05a2ee6491`) gegen `make testserver-up`

Container `tdswire-verif-skm`, Port 24981, Image `tdswire-testserver:verif-skm` (aus dem Arbeitsbaum gebaut), Beschreibung `sim.yaml` (`kunden`, `bestellungen`). Eigene Soll-Dateien (Tabelle `auftraege` mit `Id`, `Status`, `Menge` und einem Index), je `schema migrate --execute`, danach `schema compare`:

| Soll | `migrate` Exit | Bericht | `compare` |
|---|---|---|---|
| neuer Index `ix_auftraege_status (Status)` | 0 | `ok`, 2 Anweisungen (`CREATE TABLE`, `CREATE INDEX`) | `IDENTICAL`, Exit 0 |
| zweiter Lauf, gleiches Soll | 0 | `no_op`, 0 Anweisungen | `IDENTICAL` |
| Index geändert (`Status, Menge`) | 0 | `ok`, 2 Anweisungen (`DROP INDEX IF EXISTS`, `CREATE INDEX`) | `IDENTICAL` |
| Index umbenannt (`ix_auftraege_neu`) | 0 | `ok`, 2 Anweisungen (`CREATE INDEX`, `DROP INDEX IF EXISTS`) | `IDENTICAL` |
| Index entfernt | 0 | `ok`, 1 Anweisung (`DROP INDEX IF EXISTS`) | `IDENTICAL` |

`docker logs` des Testservers: 125 Zeilen, 0 mal `request_failed`. Danach `make testserver-down` und `docker rmi` des eigenen Images; die Images anderer Läufe (`tdswire-testserver:verif` und weitere) sind nicht berührt. Das deckt sich mit dem Messprotokoll [statementkind-index-d-migrate-2026-11-12](../messungen/statementkind-index-d-migrate-2026-11-12.md) (dort andere Soll-Dateien, die nicht im Repo liegen; das Protokoll sagt das ehrlich unter „Erzeuger der Zahlen“ und belegt den Draht nicht durch den d-migrate-Lauf, sondern durch den Handvektor).

## (6) Öffentliche Fläche

* `StatementKind` wächst um genau `CREATE_INDEX` und `DROP_INDEX`, beide am Ende; sonst keine Änderung an `src/main` mit öffentlicher Sichtbarkeit (Diff gelesen: `Token`, `StructurePlanner`, `StatementPlanner`, `ResponseSequencer` sind `internal` beziehungsweise nur KDoc). Der Einbettungstest hält die zwölf Konstanten in Reihenfolge (M8 rot). `make flaeche` vergleicht nur Typnamen und ist grün; die Lücke (Mitglieder) trägt der Einbettungstest, wie im Plan §6 gesagt.
* SPEC-059: Zusage- und Nachweis-Zeile und Änderungsprotokoll (Eintrag 2026-11-12) tragen die Aufnahme; „Inkompatibel“ für Kotlin-`when` ohne `else` steht im Eintrag, ebenso der Entscheid „Mitglied nach Messung, keine Folge-ADR“ und die Setzung der Position.
* Fremde `when`-Aufrufer: Grep über `src/`, `testserver/`, `referenztest-dotnet/`, `tools/` und `docs/user/` auf `when (… kind …)`: die einzigen `when` über `StatementKind` sind `Token.forKind`, `Token.countsRows` (beide vollständig, nach Ergänzung) und `SessionTransaction.apply` (mit `else`); der Test `StatementKindEmbeddingTest` hat `else`. `testserver` übersetzt (clean-build). In `docs/user/` steht nur der Satz „ein Kotlin-`when` ohne `else` über `StatementKind` muss alle Mitglieder nennen“, kein Beispielcode.

## (7) Plan-Tabelle §3 gegen Diff

| Plan-Zeile | Diff |
|---|---|
| `StatementKind.kt`, `Token.kt`, `ResponseSequencer.kt`, `StructurePlanner.kt`, `StatementPlanner.kt`, `SimulationBackend.kt` | geändert wie geplant |
| `SessionTransaction.kt` | **nicht geändert** (Plan: „der Compiler erzwingt die Ergänzung“): `apply` hat `else -> NONE`, der Compiler erzwingt nichts; der Zustand „kein Wechsel“ gilt durch den `else`-Zweig und ist durch einen Test festgehalten (M3 rot), siehe INFO-1 |
| `ResponseStatementKindTest`, `ResponseTransactionTest`, `SimulationBackendDropIndexTest`, `SimulationBackendElementsStructureTest`, `DropIndexEmbeddingTest`, `StatementKindEmbeddingTest`, `IndexKindEmbeddingTest` (neu) | geändert beziehungsweise neu |
| `TransactionDispatchTest`, `SimulationBackendStructureTest` | **nicht geändert** (Plan: update beziehungsweise prüfen): `SimulationBackendStructureTest` hat keine Index-Art-Zusicherung mit `UNKNOWN` (dort stehen `UNKNOWN`-Zusicherungen nur für `SET`-Anweisungen), `TransactionDispatchTest` braucht nichts; INFO-2 |
| `spec/spezifikation.md`, `kompatibilitaetsmatrix.md`, `docs/user/` (zwei Dateien mit Änderungen, `referenz-konfiguration.md` Zeilen 88 bis 91, `einbettung-und-betrieb.md`, `testserver-manuell.md`), Messprotokolle, Register, Roadmap, Welle, `slice-drop-index-if-exists`, `slice-abnahme-d-migrate`, `spec-abdeckung.md` | alle im Diff; keine Datei im Diff, die der Plan nicht nennt (die Verschiebung der Slice-Datei nach `in-progress/` ist der Lifecycle-Commit) |
| `harness/README.md` | nicht berührt (Diff leer): die Zellenregel (höchstens 200 Zeichen) hat keinen Gegenstand |

`docs/user/` (alle fünf Dateien): `referenz-konfiguration.md` (110 Zeilen), `referenz-ereignisse.md` (78) und `README.md` (31) habe ich nach den Fundstellen gesucht und die Zeilen zu Abschluss, Index und Kennungen gelesen; `einbettung-und-betrieb.md` (634) und `testserver-manuell.md` (711) habe ich über alle Treffer auf `INDEX`, `StatementKind`, `0x00c`, `COUNT`, `Befehlskennung`, `UNKNOWN` und `Art` gelesen, nicht Zeile für Zeile. Ergebnis: keine Fundstelle mit „Art `UNKNOWN`“ für `CREATE INDEX` oder `DROP INDEX` mehr; die Aussagen zu `ENVCHANGE` bei Index-Anweisungen („nicht gemessen“) und zum RPC stimmen mit der Spezifikation überein. Die Frage „vollständig gelesen“ des Implementers kann ich über diese Suchen hinaus nicht bestätigen.

`make spec-abdeckung`: `docs/plan/spec-abdeckung.md` im Diff (SPEC-031 148 → 152, SPEC-036 234 → 236, SPEC-059 53 → 55; zwei neue Zeilen für `TDS-QA-01` und `TDS-QA-07`); die Differenz 4 + 2 + 2 stimmt mit den vier neuen Tests je SPEC-031 und den neuen Methoden überein. Den Lauf selbst habe ich nicht wiederholt (nicht Teil der Sensor-Tabelle).

## Befunde

**HIGH:** keine. **MEDIUM:** keine.

* **LOW-1 (Zustandsfeld, `AGENTS.md` §3.7):** `docs/plan/planning/done/slice-drop-index-if-exists.md` §7 Folge-Slices: der Link zeigt auf `../done/slice-statementkind-index-mitglieder.md`, der Satz sagt weiter „die Datei steht in `open/`“. Ein Zustandsfeld nennt den Zustand; hier ist es falsch. Fundstellen: `git grep -n "steht in .open/" docs/plan/planning/done/slice-drop-index-if-exists.md`.
* **LOW-2 (Aussage breiter als Beleg / KDoc nicht nachgezogen, `AGENTS.md` §3.8):** `Token.kt` `forKind` („Messprotokoll W-6 und M-20“) und `countsRows` („die Arten aus M-20 schließen ohne“) nennen als Beleg nur M-20, `CREATE_INDEX` und `DROP_INDEX` stehen aber in der Ablage `struktur-drop-index` (nicht M-20). Ebenso der Kopf-KDoc von `StatementKind` („Messprotokoll W-6 und M-20 … je ein Fall“) neben den Mitglieder-KDocs, die auf `drop-index-d-migrate-2026-11-11` zeigen. Keine falsche Aussage über das Verhalten, aber der Beleg-Zeiger ist enger als der Code.
* **LOW-3 (Aussage ohne einheitlichen Wortlaut, Prüfpunkt 3):** „`CREATE INDEX` mit `INCLUDE` meldet dieselbe Art als Setzung“ steht in `SPEC-036` D4, `SPEC-035` und im Test `SimulationBackendElementsStructureTest > CREATE INDEX meldet die Art CREATE_INDEX` (pinnt es), aber nicht im KDoc von `CREATE_INDEX`, nicht im Satz zu `SPEC-031`, nicht in der Matrix-Zeile und nicht in `referenz-konfiguration.md`: dort steht nur „nicht gemessen“ für `INCLUDE`, Filter und absteigende Spalte. Ein Leser dieser Stellen erfährt nicht, dass `INCLUDE` die Art meldet, der Filter und die absteigende Spalte aber vom Planer abgewiesen werden (`TDS_UNSUPPORTED_SQL`, Test `…StructureTest`, Zeilen 194 bis 196). Keine Wahrheitsverletzung, aber die Setzung steht nicht an allen Fundstellen.
* **LOW-4 (Messprotokoll, `AGENTS.md` §5 Regel 7):** das Messprotokoll verweist auf „Zeilen 03 bis 07“ der Ablage; die Ablage hat keine Zeilennummern, „Zeile“ ist die Nummer der Anweisung (so auch im Vorgänger-Protokoll definiert). Das Protokoll sagt es nicht selbst, es verweist nur auf das ältere. Zudem liegen die Soll-Dateien nicht im Repo (ehrlich benannt); die Messung ist damit nicht wiederholbar ohne Nachbau, wie ich es getan habe.
* **INFO-1:** Der Plan setzt voraus, dass der Compiler `SessionTransaction.apply` erzwingt; er tut es nicht (`else`). Der Schutz „keine Transaktion“ liegt allein im Test (M3 rot). Das ist ausreichend, aber die Plan-Aussage in §2/§3 war falsch.
* **INFO-2:** Plan-Zeilen `TransactionDispatchTest` und `SimulationBackendStructureTest` ohne Änderung; keine Lücke, nur Abweichung der Tabelle vom Diff.
* **INFO-3:** `ENVCHANGE` 8/9 bei den Index-Anweisungen nach `set implicit_transactions on` ist ungemessen und ungebaut (Setzung, in Spezifikation, Messprotokoll und Doku genannt); das Risiko ist im Plan §6 offen und wandert in die Closure.
* **INFO-4:** `make gates FORCE=1` ist wegen des Docker-Caches schnell (35 s); der frische Beleg der Tests ist das clean-build-Log.

**Negativbefund-Pflicht** (die Suche, die nichts fand): kein `@Suppress`/`@SuppressWarnings` im Diff (detekt grün, Suppression-Sperre im Gate); keine Aussage „Kennung 0xc8 gemessen“ über die vier Formen hinaus im Code; keine zweite Fundstelle von „`UNKNOWN`“ für `CREATE INDEX`/`DROP INDEX` in Spezifikation, `docs/user/`, Matrix, KDoc oder Tests (Grep über `spec/`, `docs/user/`, `docs/plan/kompatibilitaetsmatrix.md`, `src/`, `testserver/`, `tools/`); keine Zahl „zehn Konstanten/Mitglieder“ mehr (Grep); keine Änderung an `harness/`, `.githooks`, `config/detekt` (keine Lockerung eines Gates, §3.6); keine ADR geändert (§3.5); kein `git mv` mit Inhaltsänderung in einem Commit (Move-Commits `4a379939`, `7d8d0b94` getrennt vom Inhaltscommit `7d8bb877`, §3.3); `spec/architecture.md` unverändert (§3.4); Docker-only (§3.1: alle Läufe über `make` beziehungsweise `docker build`).

## Bedingungen für die Planner-Closure

1. Die Haken der drei Liefer-Punkte und der Nachweis-Pflichten setzt der Planner (kein Haken ist gesetzt).
2. LOW-1 bereinigen (Satz „steht in `open/`“ im Done-Slice); LOW-2 bis LOW-4 nach Ermessen des Planners (KDoc-Beleg-Zeiger, Wortlaut der `INCLUDE`-Setzung an allen Fundstellen, Hinweis „Zeile = Nummer der Anweisung“ im Messprotokoll) oder als Beobachtung im Register `BEO-TDS/aussagen-ueber-messung-hinaus` und `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` (Zähler) fortschreiben.
3. §6 des Slice: jedes Risiko erhält seinen Ausgang (Inkompatibilität: eingetreten, im Änderungsprotokoll; Draht: entfallen, belegt durch Handvektor und diesen Lauf; Fläche: entfallen, genau zwei Mitglieder; `ENVCHANGE`: weiter offen als Setzung; `INCLUDE`/Filter/absteigend: weiter offen; Backends: weiter offen).
4. §7 Closure-Notiz mit Steering-Loop-Lerneintrag (Anker-Paarung); Plan-Aussage zu `SessionTransaction.apply` („der Compiler erzwingt“) als Lerneintrag oder in „Was ging anders“ nennen (INFO-1).
5. Danach der reine `git mv` nach `done/` und der Zeiger-Nachzug (Register Punkt 13 (a), Roadmap, Welle); `make gates` auf dem End-Stand nach dem Move.
6. Merge-Probe gegen `main`: konfliktfrei auf `ea792f47`; bei weiteren Merges in `main` vor dem Zusammenführen wiederholen.
