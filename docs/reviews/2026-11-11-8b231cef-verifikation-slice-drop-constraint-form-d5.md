# Verifikationsbericht: slice-drop-constraint-form-d5 (HEAD 8b231cef)

**Rolle:** Verifier (Modul 11). **Frage:** Bauen wir es richtig, gemessen an Plan, DoD und Entscheidungen? Gegenstand: Zweig `worktree-agent-acac5f7e404972974`, HEAD `8b231cef` (davor Merge `e14181bd` von `main`, Review `e5c345aa`), Slice `docs/plan/planning/done/slice-drop-constraint-form-d5.md`, [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), [ADR-0046](../plan/adr/0046-oeffentliche-flaeche-der-katalog-elemente.md), SPEC-031/035/036/059, Review [e5c345aa](2026-11-11-e5c345aa-review-slice-drop-constraint-form-d5.md) (0 HIGH, 4 MEDIUM, 2 LOW; Behebung in `8b231cef` ohne zweiten Review). Bezug: [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität).

## Eigene Läufe (Belege)

| Lauf | Ergebnis |
|---|---|
| `make clean-build` (einmal, End-Stand) | Exit 0, 608 s; 1349 PASSED, kein roter Testfall; `DropConstraintEmbeddingTest` (1) und `SimulationBackendDropConstraintTest` (7) PASSED |
| `make e2e` | Exit 0, 8 Stufen grün, 325 s (Stufe 7 in 36 s, kein Last-Flake, keine Wiederholung nötig) |
| `make gates FORCE=1` | Exit 0, 34 s (Docker-Schichten aus dem Cache des `clean-build`-Laufs mit gleichem Eingang; `d-check`, `gate-zuordnung` 16 Gates und 33 Fälle, `stempel-eingaenge` grün) |
| `make flaeche` | Exit 0 (prüft nur Typnamen); zusätzlich im Diff gegen `main...HEAD`: keine neue öffentliche Deklaration in `src/main` (`without`, `belongsToOther`, `dropConstraint` stehen in `internal object`; `ErrorCode` und `Statement` sind `internal`); Änderungen an `SimulationBackend.kt` und `StatementKind.kt` nur KDoc |
| `make docs-check` | Exit 0, 1345 Dateien, 0 Befunde (vor Anlage dieses Berichts) |
| `tools/matrix-zitate.sh docs/plan/kompatibilitaetsmatrix.md <clean-build.log>` | Exit 0: 1349 bestandene Testfälle, 349 Zitate, 0 ohne PASSED (Zitate im Diff nicht geändert, daher nicht `make matrix-zitate`) |
| Rohbytes selbst dekodiert (Abschnitt Messung) | Tabelle A stimmt für 2022 und 2025 |
| d-migrate 1.12.0 gegen den Testserver (Abschnitt d-migrate) | wie im Messprotokoll Tabelle C, Schritte s01 bis s15 |
| 7 Mutationen einzeln (Abschnitt Mutationen) | 7 von 7 rot |
| Merge-Probe gegen `main` `5c2e2804` und Semantik-Probe | 5 Textkonflikte, alle in Dokumenten und Messtest; `src/` und `spec/` ohne Konflikt; `docker build --target test` auf dem Merge-Stand: 1357 PASSED, 0 rot (Abschnitt Merge) |

## Gesamturteil

**Closure-fähig: ja, unter den Bedingungen B1 bis B5 unten (keine am Produktivcode).** Der Kern trägt und ist in eigenen Läufen belegt: die Antworten des echten Servers (Rohbytes selbst gelesen), die Erkennung und Planung der Form D5 (Tests, sieben Mutationen rot), das zweite `schema migrate --execute` gegen den Testserver (Exit 0, `IDENTICAL`), die öffentliche Fläche wächst nicht. Offen sind Planner-Handlungen (Haken, §6-Ausgänge, Closure-Notiz, Merge-Auflösung) und eine Doku-Zusage über der Messung (V-1).

## DoD-Punkte (Haken: alle offen, keiner vom Implementer gesetzt; ich setze keinen)

| DoD-Zeile | Beleg | Urteil |
|---|---|---|
| Liefer-Punkt 1: Messung am echten Server mit Rechner, Last, Digest; Frage zu Punkt 3 | Ablage `bulk-merge-katalog/{2022,2025}/struktur-drop-constraint.messung`: alle 22 Antworten selbst aus den Rohbytes dekodiert, 2022 und 2025 gleich (Abschnitt Messung); `jdbc-d-migrate/{2022,2025}.schema.aufnahme.txt`: je 16 Schritte, Exit 0 bis s13, **s14 Exit 5, s15 Exit 1**, s16 Exit 0 in beiden Versionen; Messprotokoll nennt Host, Last (4,2 bis 4,7), beide Digests und den Digest von d-migrate; Antwort zu Punkt 3 (kein Unterschied des Testservers) hält die Ablage und mein Lauf s14/s15 | erfüllt |
| Liefer-Punkt 2: Bau Form D5, Tests mit Handvektoren, zweites `schema migrate` Exit 0 `IDENTICAL`, Mutationen je Zusage | `Statement.DropConstraint`, `StructureParts.dropConstraint`, `StructurePlanner.dropConstraint`, `StructureChecks.without/belongsToOther`, `ErrorCode` 3728/3733 mit Positivliste; Tests und Embedding grün; eigener Lauf s03 Exit 0, `statementsAttempted` 2, s04 `IDENTICAL`; sieben Mutationen rot | erfüllt |
| Liefer-Punkt 3: Fortschreibung SPEC-036 (Form D5, Grenze entfällt), `docs/user/`, Register Punkt 2 und 3 | Spezifikation: Zeile D5, Absatz Form D5, Fehlertabelle, Positivliste, Ablaufpunkt 9, Änderungsprotokoll; `docs/user/` alle drei Dateien; Register: Punkte 2, 3 aktualisiert, 9 bis 12 neu, Träger in `state.md` | erfüllt mit V-1 (eine Zusage in `einbettung-und-betrieb.md` über der Messung) |
| `make gates` grün | `FORCE=1`, Exit 0 | erfüllt |
| Review durchgeführt, Report liegt vor | `2026-11-11-e5c345aa-review-…`; Behebung in `8b231cef` Zeile für Zeile gegen den Diff geprüft (Abschnitt Review-Behebung) | erfüllt; Nachbesserung ohne zweiten Review ist in §7 als Vermerk eingetragen |
| Haken setzt der Planner (`BEO-TDS/haken-vor-verifikation-gesetzt`) | alle `- [ ]` des Slice offen, kein `- [x]` im Diff | erfüllt |
| Doku-Update bei öffentlichem Vertrag | SPEC-031/035/036, Abschnitt 4, `docs/user/*`, `harness/README.md` (Zeile `make vergleichslauf-d`), `docs/plan/spec-abdeckung.md` (führt `DropConstraintEmbeddingTest`, SPEC-059: 52 Tests) | erfüllt |
| Closure-Notiz mit Steering-Loop-Lerneintrag | §7 nur Vermerk des Implementers, übrige Zeilen „—“ | **offen: Planner (B1)** |
| Register fortgeschrieben | `observation.md`, `state.md` (Punkte 9 bis 12, Träger je Punkt) | erfüllt |
| Jedes Risiko aus §6 trägt einen Ausgang | zwei „offen bis zur Closure“ (Form weicht ab; `unique: true`-Frage) sind nach der Messung beantwortet, der Ausgang fehlt noch; zwei „weiter offen“ mit Träger | **offen: Planner (B1)** |
| Drei Paarungen | Register: getragen; Folge-Slice: `slice-drop-index-if-exists` (Punkt 9) liegt in `open/`, `slice-declare-df-…` (Punkt 1, 11) liegt in `open/`; Anker: keine Regel verkörpert (kein `liegt in`) | Planner (B1) |

## Messung am echten Server: Rohbytes selbst (Schwerpunkt 1)

Eigener Dekoder (Python, `-I`, Scratchpad `ver/dec.py`) über die Token der Serverantworten beider Ablagen; Ergebnis für 2022 und 2025 byte-gleich in der Auswertung (`diff` der Ausgaben: leer). Tabelle A des Messprotokolls stimmt Zeile für Zeile:

- Erfolg: `DONE` Status `0x0000`, Befehl `0x00d8`, Zähler 0, kein `COUNT`, kein weiteres Token: Prüfbedingung in anderer Schreibweise ohne `IF EXISTS` (Zeile 04), `IF EXISTS` ohne Treffer (06, 11, 14, 17, 18), `IF EXISTS` mit dem Namen einer Bedingung einer anderen Tabelle (07), Eindeutigkeit und Fremdschlüssel mit `IF EXISTS` (09, 12), Standardwert (15), Schlüssel ohne Verweis (22).
- 3728: `ERROR` Nummer 3728, State 1, Class 16, danach `ERROR` 3727 (State 0, Class 16), danach `DONE` Status `0x0002`, Befehl `0x00fd` (Zeilen 05, 10, 13, 16, 19).
- 3733: State 2, Class 16, danach 3727 (Zeile 08). 3725: State 0, danach 3727 (Zeile 03).
- 4902: State 1, Class 16, **ein** ERROR, `DONE` `0x0002`/`0x00fd` (Zeilen 20, 21).

Damit ist je Fall mindestens eine vollständige Antwort selbst gelesen; die Bauzusagen (Befehl `0x00d8` ohne `COUNT`, 3728 State 1, 3733 State 2, 4902 mit einem ERROR, das zweite ERROR 3727 als Grenze) tragen. `DropConstraintEmbeddingTest` prüft nach `8b231cef` auf den Rohbytes Nummer (3728, 3733, 4902) und Befehl `0x00d8` mit Zähler 0.

## d-migrate 1.12.0 gegen den Testserver (Schwerpunkt 2)

Rig: `make testserver-up` mit `TESTSERVER_NAME=verif-d5`, `TESTSERVER_PORT=24990`, `TESTSERVER_IMAGE=tdswire-testserver:verif-d5` (Beschreibung `sim.yaml` mit `kunden`, `bestellungen`), d-migrate-Image `ghcr.io/pt9912/d-migrate@sha256:308f27753036c9cc423f716b9fc4c9e68016543a8003ecd2f8282e05a2ee6491` mit `--user "$(id -u):$(id -g)"`, Soll-Dateien aus Aufgabe 7 der Anleitung (`a`) und je einer Änderung (`b-check`, `c-fk`, `d-unique`, `e-default`, `f-index`), eine frische Datenbank, die Schritte nacheinander gegen denselben Server. Der Container wurde danach entfernt (`make testserver-down`), ebenso mein Image `tdswire-testserver:verif-d5`.

| Schritt | Soll, Befehl | Exit | Beobachtung |
|---|---|---|---|
| s01 | `a`, `migrate --execute` | 0 | `statementsAttempted` 3 |
| s02 | `a`, `compare` | 0 | `IDENTICAL` |
| s03 | `a`, `migrate --execute` (zweiter Lauf) | **0** | `statementsAttempted` 2: `ALTER TABLE [auftraege] DROP CONSTRAINT IF EXISTS [ck_auftraege_menge];` und `… WITH CHECK ADD CONSTRAINT [ck_auftraege_menge] CHECK (Menge > 0);`, `transactionRolledBack` false |
| s04 | `a`, `compare` | 0 | `IDENTICAL` |
| s06 / s07 | `b-check` (Prüfbedingung `> 1`) | 0 / 0 | `IDENTICAL` |
| s08 / s09 | `c-fk` (Fremdschlüssel ohne `on_delete`) | 0 / 0 | `IDENTICAL` |
| s10 / s11 | `d-unique` (Eindeutigkeit weg) | 0 / 0 | `IDENTICAL` |
| s12 / s13 | `e-default` (Standardwert an vorhandener Spalte) | **5** / 1 | `Unsupported SQL.`, der gesendete Text beginnt mit `DECLARE @df sysname, @sql nvarchar(max);` (Register Punkt 1, anderer Slice); `compare` `DIFFERENT` |
| s14 / s15 | `f-index` (Index mit `unique: true`) | **5** / 1 | `Unsupported SQL.`: `DROP INDEX IF EXISTS [ix_auftraege_status] ON [auftraege];` (Register Punkt 9, Folge-Slice `slice-drop-index-if-exists`) |

Das entspricht dem Messprotokoll Tabelle C (s01 bis s15); die Schritte s17 bis s19 (Soll `g-index`) habe ich nicht wiederholt. Gegen den echten Server habe ich nicht gefahren (EULA, nur lokal, Nutzer): dafür habe ich die Exit-Codes der 16 Schritte beider Ablagen `*.schema.aufnahme.txt` gelesen (s01 bis s13 Exit 0, s14 Exit 5, s15 Exit 1, s16 Exit 0, 2022 und 2025 gleich) und die Tabelle B des Messprotokolls dagegen gehalten.

## Mutationen einzeln (Schwerpunkt 3)

Kopie des Eingangs (`git archive HEAD`) im Scratchpad `ver/mut`, Docker-Stage `test` mit Filter auf die engen Klassen (Dockerfile in der Kopie angepasst), je Mutation ein Bau, danach die Datei zurückgesetzt; im Arbeitsbaum ist nichts verändert (`git status` sauber).

| Mutation | Klassen | Ergebnis |
|---|---|---|
| M1 Planer ignoriert den Namen (`without` leert alle drei Listen) | `*DropConstraintTest`, `*ErrorMapperTest`, `DropConstraintEmbeddingTest` | rot (`DropConstraintEmbeddingTest`; Gradle brach danach ab, die Unit-Tests zeigen die Rotfärbung des Reviews, hier nicht gesondert gesehen) |
| M2 `IF EXISTS` ohne Treffer ist ein Fehler | dto. | rot (`DropConstraintEmbeddingTest`, dto.) |
| M3 Name mit Beachtung der Schreibweise (`it.name == name`) | dto. | rot (`SimulationBackendDropConstraintTest > der Name gilt ohne Beachtung der Schreibweise und die Neuplanung endet mit der Bedingung`) |
| F6a Nummer 3728 → 3729 | nur `DropConstraintEmbeddingTest` | rot |
| F6b Nummer 3733 → 3734 | dto. | rot |
| F6c Art im Treffer-Zweig `UNKNOWN` | dto. | rot |
| F6d Art bei `IF EXISTS` ohne Treffer `UNKNOWN` | dto. | rot |

Damit belegt der Einbettungstest nach `8b231cef` die vier Aussagen aus F-6 des Reviews allein (für F6a bis F6d lief nur diese Klasse).

## Aussage ≤ Beleg (Schwerpunkt 4)

- Spezifikation, `StructureChecks`, `StructurePlanner`, `ErrorCode`: Schreibweise nur für den Namen einer Prüfbedingung ohne `IF EXISTS` und in der Voreinstellung der Kollation als gemessen gekennzeichnet, sonst Setzung; „desselben Schemas“ als Setzung (gemessen nur `dbo`); Eindeutigkeit und Fremdschlüssel nur mit `IF EXISTS` gemessen; das zweite ERROR 3727 als Grenze. Das trägt, soweit ich es gegen Tabelle A und das Messprotokoll gelesen habe.
- **V-1 (zweite Fundstelle, MEDIUM):** `docs/user/einbettung-und-betrieb.md` Zeile 313 („Der Name gilt ohne Beachtung der Schreibweise.“ ohne Bedingung, „…der Name einer Bedingung einer anderen Tabelle desselben Schemas…“ ohne Setzung, „Gemessen am echten SQL Server (2022 und 2025).“ über die ganze Zeile, Eindeutigkeit und Fremdschlüssel ohne den Hinweis auf `IF EXISTS`) trägt die Bedingungen nicht, die `8b231cef` in Spezifikation und KDoc ergänzt hat (F-3 des Reviews betraf Spezifikation und KDoc; die Nutzer-Doku blieb stehen). Aussage über dem Beleg nach `AGENTS.md` §3.8. Kein Code, keine Gate-Wirkung; Korrektur durch den Implementer oder Planner vor der Closure (B2).

## Öffentliche Fläche und Plan-Tabelle (Schwerpunkt 5)

- `make flaeche` grün; im Diff keine neue öffentliche Deklaration; `StatementKind` und `SimulationBackend` nur KDoc (F-1 des Reviews behoben). [ADR-0046](../plan/adr/0046-oeffentliche-flaeche-der-katalog-elemente.md) (Option A, Fläche) ist eingehalten; die Nutzerfrage (Namen von Standardwert und Schlüssel) ist als Standardantwort des Orchestrators „Fläche nicht erweitert“ in Slice §6, `SPEC-036` Form D5 und Register Punkt 11 festgehalten, umkehrbar, mit Trigger.
- Plan-Tabelle §3 gegen den Diff (40 Dateien): jede geänderte Datei steht in einer Zeile, außer dem Review-Report selbst (Zeile fehlt, unkritisch); die Kopfzeile „Berührte Spec-Stellen“ nennt jetzt SPEC-031 und Abschnitt 4 (F-4 behoben); die Zeile `spec-abdeckung.md` stimmt (die Matrix führt `DropConstraintEmbeddingTest`).
- `harness/README.md`: die Zelle der Zeile `make vergleichslauf-d` ist 200 Zeichen lang (inklusive Leerzeichen am Rand), nennt jetzt „SQL Server 2022, 2025“; andere Zellen über 200 Zeichen sind alt und nicht im Diff.
- `docs/user/*`: alle drei geänderten Dateien an den Stellen zu D5, Fehlercodes, Grenzen gelesen; die Liste der erlaubten Codes (`einbettung-und-betrieb.md`), die beobachteten Nummern (`referenz-konfiguration.md`) und die Zeile zum Abschluss `0x00d8` sind nachgezogen; die Grenze zu `DROP INDEX IF EXISTS` und zum Standardwert steht in der Fehlertabelle (Exit 5); keine Chronik; Ausnahme V-1.

## Review-Behebung in `8b231cef` (Zeile für Zeile)

| Finding | Behebung | Urteil |
|---|---|---|
| F-1 KDoc `SimulationBackend`, `StatementKind.ALTER_TABLE` | beide nennen `DROP CONSTRAINT` | erfüllt |
| F-2 Träger der Abweichungen | Register Punkte 10 bis 12 mit Trigger bzw. Träger, `state.md` nennt sie | erfüllt |
| F-3 Zusagen mit Bedingung | Spezifikation, `StructureChecks`, `StructurePlanner` | erfüllt für Spezifikation und KDoc, **V-1** für die Nutzer-Doku |
| F-4 Kopf und Plan-Tabelle | ergänzt | erfüllt |
| F-5 Kommentare | `ErrorMapperTest` („alle sechs“), `StructureChecks` | erfüllt |
| F-6 Einbettungstest | Nummer und Befehl auf den Rohbytes, vier Mutationen rot (selbst gefahren) | erfüllt |
| F-7 Abdeckungsmatrix | neu erzeugt, führt die Klasse | erfüllt |
| F-8 Fläche | nicht erweitert, Standardantwort, umkehrbar | erfüllt; Entscheid des Nutzers steht aus (Planner-Frage, B3) |
| F-9 Merge, Zelle | Zelle mit Instanzen; Merge siehe unten | Merge: B4 |

## Merge gegen `main` `5c2e2804` (der Slice `slice-bulkcopy-tabellenname-mit-schema-und-sonderzeichen` ist dort bereits)

`git merge-tree --write-tree HEAD main`: **fünf Textkonflikte**, alle außerhalb von `src/` und `spec/`: `docs/messungen/referenzabgleich.md`, `docs/plan/planning/in-progress/roadmap.md`, `docs/plan/spec-abdeckung.md` (erzeugte Matrix: nach der Auflösung mit `make spec-abdeckung` neu erzeugen), `referenztest-dotnet/Mitschnitt/MessungBulkBelegTests.cs` (beide Seiten fügen Belegtests an; beide behalten), `tools/mk/vergleichslauf-d.mk`. `spec/spezifikation.md`, `harness/README.md`, `docs/user/*` und der Produktivcode mischen sich automatisch. Semantik-Probe: der Merge-Tree (Konfliktmarker in Nicht-Code-Dateien, Wegwerf-Kopie im Scratchpad `ver/mrg`, nicht im Repo) mit `docker build --target test`: 1357 PASSED, 0 rot (dazu `BulkCopyFormsTest` und die D5-Tests). Das ersetzt keinen Lauf von `make gates` nach der Auflösung.

## Nicht gemessen / Grenzen dieses Berichts

- `make gates FORCE=1` lief gegen den Docker-Cache des `clean-build`-Laufs mit gleichem Eingang (34 s); kein frischer Beleg über den Frischlauf hinaus (der steht im `clean-build`-Lauf).
- Gegen den echten SQL Server habe ich nicht gefahren; die Ablagen sind gelesen (Rohbytes dekodiert, Exit-Codes), die Instanzen sind nicht erneut abgefragt.
- Tabelle C, Schritte s17 bis s19, nicht wiederholt. Das Skript der Läufe gegen den Testserver liegt nicht im Repo (Messprotokoll sagt es); mein Lauf hat dieselben Exit-Codes.
- Bei M1 und M2 sah ich die Rotfärbung nur im Einbettungstest (Gradle brach am ersten roten Task ab); die Unit-Klassen waren für diese zwei Mutationen im Lauf nicht mehr erreicht, der Review zeigt sie rot.
- Ablage: Scratchpad-Unterordner `ver/` (Logs, `mut/`, `mrg/`, `run/`); dort lagen schon Dateien eines früheren Laufs; überschrieben habe ich `dec.py`, `t0` und `clean-build.log`.

## Bedingungen für die Planner-Closure

- **B1** Closure-Notiz §7 füllen (Was hat funktioniert / anders / Steering-Loop-Eintrag mit `liegt in`, falls eine Regel verkörpert wird, sonst „gezählt, nicht verkörpert“), jedem Risiko in §6 einen Ausgang geben (Form weicht ab: eingetreten für die Grenzen 3727, Namen, 3725, sonst entfallen; Maskierung Punkt 1 und 3: entfallen; `unique: true`-Frage: entfallen, Antwort im Messprotokoll; Namen/Fläche und die zwei Abweichungen: weiter offen mit Träger), die Haken der drei Liefer-Punkte und der Nachweis-Pflichten setzen, Slice per `git mv` nach `done/` (erst Inhalt, dann reiner Move), Zeiger nachziehen.
- **B2** V-1: `docs/user/einbettung-und-betrieb.md` Zeile 313 an Spezifikation und KDoc angleichen (Schreibweise nur für den Namen einer Prüfbedingung bei Voreinstellung der Kollation gemessen, sonst Setzung; „desselben Schemas“ Setzung, gemessen nur `dbo`; Eindeutigkeit und Fremdschlüssel nur mit `IF EXISTS` gemessen); Nachbesserung ohne Code, danach `make docs-check`.
- **B3** Entscheid des Nutzers zur öffentlichen Fläche (Namen von Standardwert und Schlüssel, Folge-Entscheid zu [ADR-0046](../plan/adr/0046-oeffentliche-flaeche-der-katalog-elemente.md)) bleibt ausstehend; bis dahin gilt die Standardantwort „nicht erweitert“, umkehrbar; Register Punkt 11 trägt den Trigger.
- **B4** Merge mit `main` `5c2e2804` auflösen (fünf Dateien oben), `make spec-abdeckung` nach der Auflösung einmal neu, `make gates` auf dem Merge-Stand (insbesondere `docs-check` und die Zelle der Zeile `make vergleichslauf-d` ≤ 200 Zeichen nach dem Zusammenführen von `tools/mk/vergleichslauf-d.mk` und `harness/README.md`).
- **B5** Folge-Slices tragen die Grenzen weiter: `slice-drop-index-if-exists` (Punkt 9), `slice-declare-df-standardwert-vorhandene-spalte` (Punkte 1 und 11), `slice-abnahme-d-migrate` (Punkte 10 und 12 als Trigger); die Start-Trigger der offenen Slices nennen „liegt in `done/`“ als Vorschau auf den Zustand nach der Closure, das stimmt erst nach dem Move.
