# Review-Report: slice-drop-constraint-form-d5 — 2026-11-11

**Review-Art:** Code (gegen Plan, ADR und Hard Rules; nicht gegen die DoD, das ist die Frage des Verifiers).

**Gegenstand:** Diff `main` (`7412984a`) .. `e5c345aa` im Zweig `worktree-agent-acac5f7e404972974` (Commits `96436470` und `640856b0` Slice-Move, `a0713aec` Messung, `66420eb6` Bau, `6a7efed1` Spezifikation, Nutzer-Doku, Messprotokoll, Register, Roadmap, Matrix, `8b42c371` Aufteilung nach detekt-Befund (Code ohne Verhaltensänderung, Teil der Review-Fläche), `e5c345aa` Plan-Tabelle; 36 Dateien, +4613/−71). Merge-Probe gegen `main` (jetzt `f2db5649`, dort kam der Folge-Slice `slice-drop-index-if-exists` hinzu): `git merge-tree --write-tree main HEAD` meldet Konflikte in drei Planungsdateien (siehe F-9), keiner in Code oder Spezifikation.

**Skill:** `.harness/skills/reviewer.md` @ `e5c345aa` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-11

**Eingangs-Kontext** (die Verträge, gegen die geprüft wurde):

- Slice `slice-drop-constraint-form-d5` (Plan §3, DoD, Rückführungen §4, Risiken §6)
- [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) (Accepted), [ADR-0046](../plan/adr/0046-oeffentliche-flaeche-der-katalog-elemente.md) (Accepted, öffentliche Fläche der Katalog-Elemente, Option A)
- [SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends), [SPEC-036](../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) (Form D5), SPEC-059, [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität)
- `AGENTS.md` §3.1 bis §3.8; Grundsatz des Nutzers „Spec ist Zielbild“ (der echte SQL Server ist die Referenz); vorheriger Report am Modul: `2026-11-09-9ae13838`

## Selbst gefahren

| Lauf | Ergebnis |
|---|---|
| `make gates FORCE=1` | Exit 0 (Docker-Schichten überwiegend `CACHED`, gleicher Eingang wie der Lauf des Implementers; kein frischer Beleg der Tests über diesen Cache hinaus) |
| `make flaeche` | Exit 0 (Selbsttest und Prüfung; prüft nur Typnamen, keine Mitglieder) |
| `make docs-check` | 0 Befunde (1343 Dateien, vor Anlage dieses Reports) |
| 6 Mutationen über enge Testklassen (`SimulationBackendDropConstraintTest`, `ErrorMapperTest`; Kopie des Arbeitsbaums im Scratchpad, Dockerfile-Stage `test` mit Filter) | siehe Abschnitt Mutationen |
| Dekodierung der Rohbytes `struktur-drop-constraint.messung` (2025 vollständig, 2022 gegen 2025 per `diff`) | siehe Abschnitt Echtheit |
| Wegwerf-Test im Scratchpad mit Namen von 1000 bis 1 000 000 Zeichen und Leerraum-, Kommentar- und Schreibweisen-Formen | kein Absturz, keine Rekursion, Ergebnis je Form wie erwartet (Abschnitt Parser) |
| Testserver (Image `tdswire-testserver:rev`, Name `rev-drop-ts`, Port 24977, mit `sim.yaml`; danach entfernt) mit d-migrate 1.12.0 (`sha256:308f2775…`), Soll aus `schemalauf` (+ Tabellen von `sim.yaml`) | `migrate a` Exit 0 (3 Anweisungen), `compare` IDENTICAL, zweiter `migrate a` Exit 0 (`DROP CONSTRAINT IF EXISTS [ck_auftraege_menge]`, `ADD CONSTRAINT`, 2 Anweisungen), `compare` IDENTICAL; `b` (Prüfbedingung und Fremdschlüssel geändert) Exit 0, 4 Anweisungen, IDENTICAL, zweiter Lauf Exit 0; `d` (Eindeutigkeit entfernt) Exit 0, 3 Anweisungen, IDENTICAL, zweiter Lauf Exit 0 |
| `make clean-build`, `make e2e`, `make matrix-zitate`, `make spec-abdeckung` | nicht gefahren (Sensor-Zuschnitt des Auftrags) |

Ablage: Scratchpad-Unterordner `rev/` meines Laufs (`mut.sh`, `dec.py`, `work/`, Logs); `scratchpad/drop/` des Implementers nicht angefasst.

## Echtheit der Messung (Schwerpunkt a)

Alle 22 Antworten der Ablage `bulk-merge-katalog/2025/struktur-drop-constraint.messung` habe ich aus den Rohbytes selbst dekodiert (ERROR: Nummer, State, Class, Text; DONE: Status, Befehl, Zähler); die Datei von 2022 ist bis auf Kopfzeilen, Login-Antwort und die Server-Version im Login-Teil gleich (`diff`). Ergebnis: Tabelle A des Messprotokolls stimmt Zeile für Zeile.

- Erfolg: `DONE` Status `0x0000`, Befehl `0x00d8`, Zähler 0, kein weiteres Token (Zeilen 04, 06, 07, 09, 11, 12, 14, 15, 17, 18, 22). Der Befehl `0x00d8` ohne `COUNT` ist gemessen.
- 3728: State 1, Class 16, danach 3727 (State 0, Class 16), danach `DONE` Status `0x0002`, Befehl `0x00fd` (Zeilen 05, 10, 13, 16, 19). 3733: State 2 (Zeile 08). 4902: State 1, nur ein ERROR, dann `DONE` `0x0002`/`0x00fd` (Zeilen 20, 21). 3725: State 0, danach 3727 (Zeile 03).
- Gemessen und Setzung sind im Messprotokoll getrennt: die Grenzen („Nicht gemessen“: mehrere Namen, Schemanamen, Sonderzeichen, Zeilen in der Tabelle, Transaktion, 3727 im RPC, 3728/3733 nur mit `IF EXISTS` bei Eindeutigkeit und Fremdschlüssel) stehen dort ehrlich. Die Spezifikation und die KDoc tragen sie nicht in jedem Satz mit (F-3).
- Das zweite ERROR 3727: die Bibliothek sendet nur das erste ERROR. Es ist als Grenze benannt (Spezifikation D5, Fehlertabelle, `ErrorCode`, Nutzer-Doku); die Bibliothek kennt den Mechanismus eines Folge-Tokens nach dem ERROR (INFO 3621 nach 2628, `SPEC-031`). Eine Zielbild-Abweichung ohne Träger (F-2).

## Parser und Planer (Schwerpunkte b, c)

- Parser `StructureParts.dropConstraint`: keine Regex, kein rekursiver Aufruf je Zeichen; der Bezeichner kommt aus dem bestehenden Scanner. Wegwerf-Test mit Namen von 1000, 10 000, 100 000 und 1 000 000 Zeichen (Tabellen- und Bedingungsname, Namen mit `]]`, Leerraum aus Leerzeichen, Tab, CR, LF): Ergebnis in 45 s einschließlich Bau, kein Absturz. Formen: `dbo . t`, `[CK_X]` bei `ck_x`, nachgestelltes `;`, Kommentar vor dem Namen, `"ck_x"` (angenommen) gehen; `a; b`, `[dbo].[ck_x]`, `'ck_x'` sind `TDS_UNSUPPORTED_SQL`. Mehrere Namen, `DROP COLUMN`, `WITH CHECK DROP`, `IF` ohne `EXISTS` sind abgewiesen (Test in der Klasse des Implementers).
- Planer: Namen je Tabelle (Register Punkt 5, benannt); `without` entfernt über die drei Listen, `belongsToOther` sucht nur im selben Schema; die Tabelle kommt als Beschreibung ohne Zeilenquelle und Callbacks an `onStructure`. Eine Verletzung der referentiellen Integrität prüft die Bibliothek nicht (3725 nur am echten Server): benannt in der Spezifikation und der Nutzer-Doku.
- `structureLock`, Zweitplanung unter der Sperre (`Rejected` wird zu `ChangeRejected`), Callback-Aufruf: unverändert; die Tabelle nach `DROP` stimmt im Katalog (Beleg: der zweite `schema migrate` gegen den Testserver endet mit IDENTICAL, siehe oben).

## Mutationen (Schwerpunkt e)

| Mutation | Testklassen | Ergebnis |
|---|---|---|
| M1 Planer ignoriert den Namen (`without` entfernt alle drei Listen vollständig) | `SimulationBackendDropConstraintTest`, `ErrorMapperTest` | rot (1: `der Name gilt ohne Beachtung der Schreibweise …`) |
| M2 `IF EXISTS` ohne Treffer ist ein Fehler | dto. | rot (1: `IF EXISTS ohne Treffer ist ein Erfolg ohne Aenderung`) |
| M3 Eindeutigkeit mit Beachtung der Schreibweise | dto. | rot (1: `der Name gilt ohne Beachtung …`) |
| M6 Parser nimmt weitere Namen hinter `,` und ignoriert sie | dto. | rot (1: `Formen ausserhalb von D5 sind nicht erkannt`) |
| E1 Name einer anderen Tabelle wird ohne Fehler angenommen (Erfolg) | dto. | rot (1: `ohne IF EXISTS ist ein Name ohne Treffer ein Fehler`) |
| E2 Callback bei `DROP` nicht gerufen | dto. | rot (4) |

Ein erster Versuch von M6 scheiterte am Übersetzen (falscher Name der Konstante, Fehler der Mutation, nicht des Codes) und wurde korrigiert wiederholt. Alle Mutationen sind in der Kopie im Scratchpad gefahren; im Arbeitsbaum ist nichts verändert. Hinweis zur Schärfe: unter M1 bleibt `die Texte von d-migrate nehmen die Bedingung aus der Tabelle` grün, weil die drei Texte zusammen alle drei Elemente entfernen; die Mutation fängt erst der Schreibweisen-Test.

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| F-1 | MEDIUM | Die öffentlichen KDoc `SimulationBackend` und `StatementKind.ALTER_TABLE` nennen `DROP CONSTRAINT` nicht: die erste zählt die Formen auf, die den Strukturänderungs-Callback rufen („`ALTER TABLE … ADD` einer Spalte oder einer Bedingung, `CREATE INDEX`“), die zweite beschreibt `ALTER_TABLE` als „`ALTER TABLE … ADD`: Kennung `0x00d8`“. Beide stehen nicht in der Plan-Tabelle und nicht im Diff; ein Integrator, der die KDoc liest, erfährt von D5 nicht, obwohl der Callback bei D5 läuft. | `AGENTS.md` §3.8, Reviewer-Skill „Fortschreibung lässt die zweite Fundstelle stehen“ | `src/main/kotlin/io/github/pt9912/tdswire/simulation/SimulationBackend.kt` · „Form D (`CREATE TABLE`, `ALTER TABLE … ADD` einer Spalte oder einer Bedingung, `CREATE INDEX`) ruft den Strukturänderungs-Callback“; `src/main/kotlin/io/github/pt9912/tdswire/domain/StatementKind.kt` · „`ALTER TABLE … ADD`: Kennung `0x00d8`“ | nein (Grep) | Fortschreibung lässt die zweite Fundstelle stehen |
| F-2 | MEDIUM | Drei Abweichungen vom echten Server haben keinen Träger: das zweite ERROR 3727 nach 3728 und 3733, der Name von Standardwert und Schlüssel (`DROP CONSTRAINT <df-/pk-Name>` ist am echten Server ein Erfolg, am Testserver 3728), die fehlende Prüfung des Verweises (3725). `state.md` nennt als Träger nur den Slice zu Punkt 1 und sagt „Die Punkte 4 bis 9 bleiben Setzung ohne Träger“; Punkt 2 gilt als „gebaut“, seine drei Grenzen stehen in dessen Text, ohne Träger oder Entscheid. Das Zielbild wird damit auf unbestimmte Zeit unterschritten, ohne dass die Stelle sagt, wer es ändert. | Grundsatz „Spec ist Zielbild“; [ADR-0046](../plan/adr/0046-oeffentliche-flaeche-der-katalog-elemente.md) (Fläche); Register-Paarung | `docs/plan/planning/observations/BEO-TDS/katalog-elemente-grenzen-der-angleichung/state.md` · „Die Punkte 4 bis 9 bleiben Setzung ohne Träger“ | nein | Zielbild-Abweichung ohne Träger |
| F-3 | MEDIUM | Zusagen der Form D5 sind breiter als die Messung: (a) „der Name gilt ohne Beachtung der Schreibweise“ steht ohne Bedingung, gemessen ist ein Treffer ohne `IF EXISTS` (Zeile 04) in der Voreinstellung der Instanz, deren Kollation das Messprotokoll „nicht eigens erhoben“ nennt; (b) „Name einer Bedingung einer anderen Tabelle **desselben Schemas**“ ist in der Spezifikation, in `StructureChecks` und `ErrorCode` ohne Kennzeichnung als Setzung, gemessen sind nur Tabellen im Schema `dbo`; (c) „Eindeutigkeit, Fremdschlüssel … fällt aus der Tabelle“ gilt in der Form D5 für beide Schreibweisen, gemessen sind Eindeutigkeit und Fremdschlüssel nur mit `IF EXISTS` (Zeilen 09, 12; das Messprotokoll sagt es, die Spezifikation nicht). | `AGENTS.md` §3.8 | `spec/spezifikation.md` · „der Name gilt ohne Beachtung der Schreibweise“ und „desselben Schemas“ (Form D5); `src/main/kotlin/io/github/pt9912/tdswire/simulation/StructurePlanner.kt` · „desselben Schemas“ | nein | Zusage ohne ihre gemessene Bedingung |
| F-4 | MEDIUM | Der Kopf „Berührte Spec-Stellen“ des Slice nennt `SPEC-036`, `SPEC-035` und `TDS-FA-11.a`; der Diff berührt außerdem `SPEC-031` (Abschlussbefehl `0x00d8` für `DROP CONSTRAINT`) und Abschnitt 4 (Fehlertabelle und Positivliste der Backends). Die Plan-Tabelle nennt die Zeigerkorrekturen in `done/slice-katalog-indizes-defaults-fremdschluessel.md`, `open/slice-abnahme-d-migrate.md`, `open/slice-declare-df-standardwert-vorhandene-spalte.md`, `welle-testserver-manuell.md` und `roadmap.md` nicht (Zeigerkorrekturen und Zustandsfeld). Die Zeile `docs/plan/spec-abdeckung.md | erzeugt` stimmt nach `8b42c371` nicht mehr (siehe F-7). | Reviewer-Skill „Plan-Tabelle nennt die berührten Dateien nicht“ | `docs/plan/planning/done/slice-drop-constraint-form-d5.md` · „**Berührte Spec-Stellen:** SPEC-036 (erkannte Anweisungsformen, Form D, Absatz Grenzen) · SPEC-035“ | nein | Plan-Tabelle nennt die berührten Dateien nicht |
| F-5 | LOW | Kommentare zählen nach der Erweiterung falsch oder widersprechen dem Stand: `ErrorMapperTest` („Wächter: Class 20 mit schließender Verbindung, State 1 für alle drei, …“ bei jetzt sechs Codes), `StructureChecks` („Namen gelten ohne Beachtung der Schreibweise (Setzung, nicht am Server gemessen)“, während der Kopf desselben Kommentars jetzt das gemessene `DROP` einschließt). | `AGENTS.md` §3.7 | `src/test/kotlin/io/github/pt9912/tdswire/protocol/error/ErrorMapperTest.kt` · „State 1 für alle drei“; `src/main/kotlin/io/github/pt9912/tdswire/simulation/StructureChecks.kt` · „(Setzung, nicht am Server gemessen)“ | nein | Kommentar zählt falsch / widerspricht gemessenem Stand |
| F-6 | LOW | Der Einbettungstest `DropConstraintEmbeddingTest` prüft auf den Rohbytes nur das Fehlerbit im Abschluss (`doneStatus and DONE_ERROR`), nicht die Nummer (3728/3733) und nicht den Befehl `0x00d8`; beides trägt nur der Test des Backends und `ErrorMapperTest` (Draht nur über die Zusammenstellung). Keine der sechs Mutationen blieb grün, daher kein MEDIUM. | `AGENTS.md` §3.8 (Aussage der KDoc: „auf den Rohbytes“) | `src/integrator/kotlin/io/github/pt9912/tdswire/integrator/DropConstraintEmbeddingTest.kt` · „assertEquals(DONE_ERROR, missing.doneStatus and DONE_ERROR)“ | ja (`make test`) | Test belegt weniger als seine KDoc sagt |
| F-7 | INFO | `docs/plan/spec-abdeckung.md` führt `DropConstraintEmbeddingTest` nicht (SPEC-059: 52 bestandene Tests ohne die Klasse; sie entstand durch die Aufteilung nach `8b42c371`, nach dem Erzeugen). Ich habe die Matrix nicht neu erzeugt; Bedingung für den Planner: `make spec-abdeckung` nach den Korrekturen aus diesem Report einmal wiederholen. | Auftrag (g) | `docs/plan/spec-abdeckung.md` · „SPEC-059“ | ja (`make spec-abdeckung`) | Abdeckungsmatrix vor dem Verschieben erzeugt |
| F-8 | INFO | Entscheidungsfrage zur öffentlichen Fläche (Schwerpunkt d): der Implementer hat nichts hinzugefügt, das ist nach [ADR-0046](../plan/adr/0046-oeffentliche-flaeche-der-katalog-elemente.md) richtig (ein Mitglied wie `SimColumn.defaultName` wäre eine Erweiterung der Fläche, `make flaeche` sähe sie nicht, weil es nur Typnamen vergleicht). Bewertung: das Zielbild wird nur an einer Stelle vertagt, die d-migrate nicht trifft; der Katalog zeigt den Namen eines Standardwerts oder Schlüssels nirgends (Antwortspalten tragen nur `default_definition` und Spaltennamen), und d-migrate sendet für den Standardwert den Text mit `DECLARE @df …`, nicht `DROP CONSTRAINT <Name>`. Offen bleiben ein Client, der selbst benannte Standardwerte und Schlüssel mit `DROP CONSTRAINT <Name>` löscht, und `slice-declare-df-standardwert-vorhandene-spalte`, falls er den Namen am Standardwert statt am Text der Anweisung auflöst. Die Frage an den Nutzer steht in der Zusammenfassung der Übergabe. | [ADR-0046](../plan/adr/0046-oeffentliche-flaeche-der-katalog-elemente.md) | `docs/plan/planning/done/slice-drop-constraint-form-d5.md` · „das Modell führt keinen Namen für Standardwert und Schlüssel“ | nein | Entscheid zur Fläche offen |
| F-9 | INFO | Merge-Probe gegen `main` `f2db5649`: Konflikte nur in `docs/plan/planning/in-progress/roadmap.md`, `open/slice-abnahme-d-migrate.md`, `welle-testserver-manuell.md` (Zeiger auf den verschobenen Slice und neue Zeilen für `slice-drop-index-if-exists`); der parallele Slice zu `BulkCopyForms.kt`/Form B und die Spezifikation berühren den Diff nicht. Die Zeile `make vergleichslauf-d` in `harness/README.md` verlor beim Kürzen auf 199 Zeichen die Angabe „(2022, 2025)“: die Instanzen der Läufe `jdbc` und `schema` stehen jetzt nur noch im Messprotokoll. Der Messprotokoll-Teil „Tabelle C“ stammt aus einem Skript, das nicht im Repo liegt (benannt); mein eigener Lauf mit denselben Soll-Dateien ergab dieselben Exit-Codes und `IDENTICAL`. | Maintainability | `harness/README.md` · „d-migrate (`jdbc`, `schema`) gegen echten SQL Server; nur lokal“ | ja (`git merge-tree`) | Merge-Konflikt in Planungsdateien |

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `src/main/.../protocol/error/` (`ErrorCode`, `ErrorMapper`) | geprüft, ohne Befund (Nummer, Class, State, offene Verbindung, Positivliste gegen Rohbytes und Test) |
| `src/main/.../simulation/` (Parser `StructureParts`, Planer `StructurePlanner`, `StructureChecks`, `StatementPlanner`, `SimulationChanges`) | geprüft, ohne Befund außer F-3 (Zusagen) und F-5 (Kommentare); keine Regex, keine Rekursion je Zeichen, Namen bis 1 000 000 Zeichen ohne Absturz |
| `config/detekt/detekt.yml`, `build.gradle.kts`, `Dockerfile`, Suppression | nicht berührt (`make gates FORCE=1` grün); kein `@Suppress` im Diff |
| `src/test/`, `src/integrator/` | geprüft; sechs Mutationen rot, F-6 für die Schärfe auf dem Draht |
| `referenztest-dotnet/` (Szenario `struktur-drop-constraint`, Belegtests, Ablage 2022/2025) | geprüft, ohne Befund (Ablage stichprobenartig; `*.schema.aufnahme.txt` nur im Kopf und an den Schritten s03, s08, s14 gelesen) |
| `spec/spezifikation.md` | geprüft: keine ADR-Kennung im Diff, kein Stratum-Verstoß; Aussagen gegen Messprotokoll gelesen (F-3, F-4) |
| `docs/user/*` (alle drei geänderten Dateien vollständig an den Stellen zu D5, Fehlercodes, Grenzen; Grep auf „nicht gebaut/bisher/noch nicht/Zielzustand/vorläufig/seit Slice“) | geprüft, ohne Befund; die Treffer (`einbettung-und-betrieb.md` 317, `referenz-konfiguration.md` 100) sind älter und unabhängig von D5; der Ist-Zustand ist beschrieben |
| Register `katalog-elemente-grenzen-der-angleichung`, `slice-declare-df-…`, `slice-abnahme-d-migrate` | geprüft; Punkt 2 ist als gebaut geführt (F-2); `slice-declare-df` und `slice-abnahme-d-migrate` nennen Punkt 2 nicht mehr offen (Zeiger auf `in-progress/`), die Aussage „liegt in `done/`“ im Start-Trigger von `slice-declare-df` ist eine Vorschau auf den Zustand nach der Closure |
| Haken, Roadmap-Zustandsfeld | geprüft, ohne Befund: kein `- [x]` im Diff; „In Arbeit: … mit slice-drop-constraint-form-d5“ nennt Zustand und Anker, keine Chronik |
| `tools/vergleichslauf-d.sh`, `tools/mk/vergleichslauf-d.mk` | geprüft, ohne Befund (Lauf `schema` ist nicht Teil von `alle`, wie `jdbc`; Hilfe und Kopfkommentar nennen ihn) |

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 4 |
| LOW | 2 |
| INFO | 3 |

**Finding-Klassen dieses Laufs:** Fortschreibung lässt die zweite Fundstelle stehen · Zielbild-Abweichung ohne Träger · Zusage ohne ihre gemessene Bedingung · Plan-Tabelle nennt die berührten Dateien nicht · Kommentar zählt falsch / widerspricht gemessenem Stand · Test belegt weniger als seine KDoc sagt

## Verdikt

**Merge-blockierend:** nein für Code und Messung; die vier MEDIUM (F-1 bis F-4) sind vor der Verifikation zu klären oder zu begründen. Kein HIGH: keine Verletzung einer Hard Rule, keine Fläche gewachsen, Gates grün, alle Mutationen rot, der zweite `schema migrate` gegen den Testserver endet mit Exit 0 und `IDENTICAL`.

**Übergabe:** Findings gehen an den Implementer; F-2 und F-8 brauchen den Planner und den Nutzer (Träger bzw. Entscheid zur Fläche), F-7 ist eine Bedingung für den Planner. Nachbesserung am Code nach diesem Report (F-1, F-5 sind Kommentar-/Doku-Korrekturen; F-6 wäre Testcode) wäre bei Testcode ein Fall für einen zweiten Review oder den Vermerk „Nachbesserung ohne zweiten Review“ in §7.
