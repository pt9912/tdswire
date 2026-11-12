# Review-Report: slice-declare-df-standardwert-vorhandene-spalte — 2026-11-12

**Review-Art:** Code (gegen Plan, ADR und Hard Rules; nicht gegen die DoD, das ist die Frage des Verifiers).

**Gegenstand:** Diff `206755ea..a70e06ef` im Zweig `worktree-agent-afc7e4a2dc7755c8c` (Inhalts-Commit `a70e06ef`; davor `8c9b9aa3` Start, `5be233b9` und `d974511e` Zeigerkorrekturen des Slice-Moves).

**Skill:** `.harness/skills/reviewer.md` @ `a70e06ef` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-12

**Eingangs-Kontext** (die Verträge, gegen die geprüft wurde):

- Slice `slice-declare-df-standardwert-vorhandene-spalte` (Plan §3, DoD, Risiken §6)
- [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) und [ADR-0046](../plan/adr/0046-oeffentliche-flaeche-der-katalog-elemente.md) (beide Accepted, im Diff unverändert), SPEC-035, SPEC-036 (neu: Form D7, Grenzen von Form D und D5), SPEC-059
- Register `BEO-TDS/katalog-elemente-grenzen-der-angleichung` (Punkte 1, 11, 15), Messprotokoll `docs/messungen/standardwert-ersetzen-d-migrate-2026-11-12.md`, Ablagen `struktur-standardwert-ersetzen.messung` (2022, 2025)
- `AGENTS.md` §3.1 bis §3.8; Entscheide des Nutzers vom 2026-11-12 (öffentliche Fläche wächst nicht, Option A; Zielbild bei neuen Erkenntnissen prüfen; Messung am echten Server erlaubt); vorheriger Report am Modul: `2026-11-11-e5c345aa` (Form D5)

## Selbst gefahren

| Lauf | Ergebnis |
|---|---|
| `make gates FORCE=1` | Exit 0 in 35 s (Docker-Schichten fast alle `CACHED`, gleicher Eingang wie der Lauf des Implementers: kein frischer Beleg der Tests über diesen Cache hinaus) |
| 4 Mutationen über `SimulationBackendDropDefaultTest` (Dockerfile-Kopie im Scratchpad, Stage `test` mit Filter, je 46 bis 51 s) | alle rot, Tabelle unten; Quelltext danach wiederhergestellt, `git status` sauber |
| Abgleich Wortlaut Kotlin, C# und Mitschnitt (`jdbc-d-migrate/2022.schema.aufnahme.txt`, Bericht-Feld `sql` gegen Batch auf dem Draht) | Draht und Bericht gleich; Länge 419 Zeichen; Größen der Ablagen 41018 und 41038 Byte stimmen |
| Grep nach der alten Lesart („nicht erkannt“, „Exit 5“, „mehrere Anweisungen“, „nicht gemessen“) in `spec/`, `docs/user/`, Code | Treffer unter F-1 und F-2 |
| `make clean-build`, `make e2e`, `make matrix-zitate`, `make spec-abdeckung` | nicht gefahren (Sensor-Zuschnitt) |

Gesamtlaufzeit des Laufs rund 25 Minuten; größte Posten: Lesen von Diff, Spezifikation und Nutzer-Doku (etwa 15 Minuten), vier Mutationsläufe zu je rund 50 s, `make gates` 35 s.

## Mutationen

| Nr | Mutation (`DefaultDropForms.kt` bzw. `StructurePlanner.kt`) | Gemessene Wirkung |
|---|---|---|
| R-A | Spaltenname-Gruppe ohne besitzende Quantoren: `((?:[^']|'')+)` statt `((?:[^']++|'')++)` | rot: `ein Name von 100000 Zeichen ist erkannt und beantwortet` (1 von 8 Tests) |
| R-B | Rückverweis `\1` auf die Tabelle durch eine zweite freie Tabellengruppe ersetzt (Tabelle an beiden Stellen nicht verglichen) | rot: `eine Abweichung vom Wortlaut ist nicht erkannt` (1 von 8) |
| R-C | Spaltenvergleich im Planer `StructureChecks.same` durch `==` (Schreibweise beachtet) ersetzt | rot: `Tabelle und Spalte gelten ohne Beachtung der Schreibweise` (1 von 8) |
| R-D | `.removeSuffix(";")` entfernt (Abschluss `;` nicht akzeptiert; eigene Mutation) | rot: unter anderem `der Text ein zweites Mal …`, `ohne Standardwert und an unbekannter Spalte …`, `ohne Callback …`, `ein Name von 100000 Zeichen …` (mehrere Tests, der Text mit `;` ist die Normalform) |

Die Tests der Erkennung tragen die Zusagen; die Regex ist linear (besitzend, Test mit 100 000 Zeichen trägt sie).

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| F-1 | MEDIUM | Die Fortschreibung lässt Aufzählungen und eine Verneinung der alten Lesart stehen. `SPEC-036` nennt „mehrere Anweisungen in einem Text (außer Form C)“ als nicht erkannt, der Text der Form D7 besteht aus mehreren Anweisungen und ist erkannt. `SPEC-035` zählt die Strukturformen des Callbacks ohne den Text auf. `testserver-manuell.md` sagt im Abschnitt „Erkannt werden“ ohne den Text und mit „mehrere Anweisungen in einem Text enden mit `TDS_UNSUPPORTED_SQL`“, und die Liste in „Der Server hält Tabellen im Arbeitsspeicher …“ nennt ihn ebenfalls nicht. Ein Leser schließt, der Text sei abgewiesen. | AGENTS.md §3.8; Reviewer-Skill „Fortschreibung lässt die zweite Fundstelle stehen“ | `spec/spezifikation.md` · „mehrere Anweisungen in einem Text (außer Form C) und alles, was keine Form“; ebd. · „`ALTER TABLE … DROP CONSTRAINT`, `CREATE INDEX` und `DROP INDEX [IF EXISTS]` die Tabelle im Zustand **nach** der Anweisung“; `docs/user/testserver-manuell.md` · „mehrere Anweisungen in einem Text enden mit `TDS_UNSUPPORTED_SQL`“ und „ändert sie auf `CREATE TABLE`, `ALTER TABLE … ADD`“ | ja: Grep nach den Zitaten | Fortschreibung lässt zweite Fundstelle stehen |
| F-2 | MEDIUM | Die Grenzen von D7 stehen nicht an allen Fundstellen gleich. (b) Der zweite Standardwert (1781 und 1750 am Server): `SPEC-035` sagt weiter „die Nummern des Servers für diese Fälle sind nicht gemessen“, obwohl die Nummern für diesen Fall gemessen sind (D7 (b)); die Nutzer-Doku nennt (b) nirgends. (c) `ENVCHANGE` Typ 8 bei dem Text unter `IMPLICIT_TRANSACTIONS`: nur im Register (Punkt 15 (c)) und im Messprotokoll; die Grenzen D7 (a) bis (d) der Spezifikation und die Nutzer-Doku nennen ihn nicht, `SPEC-031` führt weiter „ENVCHANGE bei … ALTER_TABLE … nicht gemessen“, wo der Text mit der Art `ALTER_TABLE` nun gemessen ist. (a) steht dagegen an allen Fundstellen gleich (Spezifikation, `einbettung-und-betrieb.md`, `referenz-konfiguration.md`, `testserver-manuell.md`, Register, Messprotokoll, KDoc). | AGENTS.md §3.8; Reviewer-Skill „Fortschreibung lässt die zweite Fundstelle stehen“ | `spec/spezifikation.md` · „die Nummern des Servers für diese Fälle sind nicht gemessen“; ebd. · „nicht bei `CREATE_INDEX` und `DROP_INDEX`: ENVCHANGE bei diesen Arten nach `set implicit_transactions on` ist nicht gemessen“; ebd. · „**Grenzen** (Träger je Punkt: `BEO-TDS/katalog-elemente-grenzen-der-angleichung`): (a) der Server antwortet“ | ja: Grep | Grenze nur an einem Teil der Fundstellen |
| F-3 | MEDIUM | Die Plan-Tabelle (§3) nennt `docs/plan/spec-abdeckung.md` nicht, die der Diff ändert (Zähler SPEC-036, SPEC-045, SPEC-059; Testklassen), und der Kopf „Berührte Spec-Stellen“ nennt `SPEC-045` und `SPEC-059` nicht, deren Zähler und Tags der Diff berührt (Tags in `DropDefaultEmbeddingTest`, `MessungBulkBelegTests`). | Reviewer-Skill „Plan-Tabelle nennt die berührten Dateien nicht“ | `docs/plan/planning/done/slice-declare-df-standardwert-vorhandene-spalte.md` · „**Berührte Spec-Stellen:** SPEC-036 (erkannte Anweisungsformen, Form D, Absatz Grenzen) · SPEC-035“ | nein | Plan-Tabelle nennt die berührten Dateien nicht |
| F-4 | INFO | Zuständig Planner: §1 „Ziel“ des Slice sagt, der Text führe über `EXEC sp_executesql` ein `ALTER TABLE … ADD CONSTRAINT` aus, und der Liefer-Punkt „Bau“ nennt den „Standardwert“ als Parameter der Form und „ersetzt“; gemessen und gebaut ist `DROP CONSTRAINT` mit den Parametern Tabelle und Spalte, das `ADD CONSTRAINT` sendet d-migrate danach als Form D3. Die Aussagen der Spezifikation und des Messprotokolls sind richtig; der Slice-Text ist es nicht. | Maintainability | `docs/plan/planning/done/slice-declare-df-standardwert-vorhandene-spalte.md` · „über `EXEC sp_executesql` ein `ALTER TABLE … ADD CONSTRAINT [df_…] DEFAULT …` ausführt“ | nein | Plan beschreibt anderes als gebaut |
| F-5 | INFO | Das Feld `sqlLength` im Bericht von d-migrate nennt 418, das Feld `sql` hat 419 Zeichen (und der Batch auf dem Draht denselben Text). Spezifikation, Test und Messprotokoll führen 419 (das Feld `sql`); dass d-migrate selbst 418 meldet, steht nirgends. Keine Auswirkung auf die Erkennung. | AGENTS.md §3.8 | `referenztest-dotnet/Mitschnitt/messung/jdbc-d-migrate/2022.schema.aufnahme.txt` · „\"sqlLength\":418“; `spec/spezifikation.md` · „419 Zeichen für Tabelle `auftraege`“ | ja: Zeichenzahl des Felds `sql` | Zahl ohne Hinweis auf abweichendes Nachbarfeld |
| F-6 | INFO | Die Aussage „die Antwortform des Servers verlangte eine Erweiterung der öffentlichen Fläche“ (Spezifikation D7 (a), Register Punkt 15 (a), Nutzer-Doku) ist eine Begründung des Entscheids, kein Messwert; kein Test zeigt, dass ein Backend die Folge der Anweisungen nicht mit den vorhandenen Mitteln ausdrücken kann. Sie ist als Grenze geführt, und der Nutzer hat Option A entschieden; die Zeile trägt nur so weit wie diese Begründung. | AGENTS.md §3.8 | `spec/spezifikation.md` · „die Antwortform des Servers verlangte eine Erweiterung der öffentlichen Fläche“ | nein | Begründung als Fakt formuliert |

## Antworten auf die Schwerpunkte (Kurzfassung, Belege oben)

1. `DefaultDropForms.kt`: Text gleich dem Mitschnitt (Draht und Bericht), Leerraum ist als Setzung gekennzeichnet; Regex linear, besitzende Quantoren, Test mit 100 000 Zeichen trägt sie (R-A); Rückverweis auf die Tabelle getragen (R-B); `''` wird entschachtelt und getestet; `#`, `$` und `ä` im Test; ohne Treffer und bei unbekannter Spalte gelingt der Text, bei unbekannter Tabelle ohne Callback (Test); ohne Callback bleibt der Text an einer bekannten Tabelle abgewiesen (Test). Kein Befund.
2. Abschluss `ALTER_TABLE` ohne `COUNT`: Spezifikation D7 (a) und Register Punkt 15 (a) nennen die Antwortfolge des Servers (vier `DONE`, `RETURNSTATUS`, `DONEPROC`; ohne Treffer zwei `DONE`) wörtlich wie das Messprotokoll und die Belegtests und sagen, die Bibliothek sende den einen Abschluss, auch ohne Treffer. Nicht breiter als der Beleg; nur die Begründung siehe F-6.
3. Abweichungen 2 und 3 an den Fundstellen: siehe F-2.
4. Die drei `when` über `Statement` (`SimulationChanges`, zweimal `StatementPlanner`) tragen `DropDefault` wie `DropIndex`; die Übersetzung prüft die Exhaustivität. Eine „`COMMIT_IF_OPEN`-Auslassung“ habe ich im Diff nicht gefunden (keine Zeile in `src/main` berührt `COMMIT_IF_OPEN`); dazu keine Aussage.
5. Punkt 11 bleibt im Register offen mit Trigger Abnahme oder ein Slice, der die Namen braucht, und der Begründung, dass D7 sie nicht braucht: nachvollziehbar, die Fläche wächst nicht ([ADR-0046](../plan/adr/0046-oeffentliche-flaeche-der-katalog-elemente.md) Option A unverändert, `make flaeche` in `make gates` grün).
6. Mutationen: siehe Abschnitt Mutationen; die gemessene Wirkung stimmt mit den Meldungen des Implementers für R-A bis R-C überein.
7. `referenztest-dotnet`: Belegtests prüfen Tokenfolge, `DONE`-Felder und Fehler je Zeile, Ablage-Zahl 22 an den Szenarien nachgezogen (`MessungBulkBelegTests`, `vergleichslauf-d.mk`, `referenzabgleich.md`). Kein Befund.
8. Nutzer-Doku: alle fünf Dateien auf die Wörter „Exit 5“, „Unsupported“, „nicht gebaut“, „noch nicht“, „bisher“, „Zielzustand“ gegrept; die frühere Zeile „Standardwert an vorhandener Spalte nicht anlegbar“ ist entfernt, die frühere Fehlertabellenzeile ersetzt. Offen bleibt F-1 (Listen) und der Mangel von (b) und (c) in F-2.
9. d-check-Regeln: die Spezifikation nennt in den neuen Zeilen keine ADR-Kennung; keine ADR geändert; keine Datei unter `config/`, `build.gradle.kts`, `Dockerfile` oder `Makefile` berührt; kein `@Suppress`; `docs/plan/spec-abdeckung.md` ist erzeugt (F-3).

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `src/main/kotlin/.../simulation/` (`DefaultDropForms.kt`, `Statement.kt`, `StatementParser.kt`, `StatementPlanner.kt`, `StructurePlanner.kt`, `SimulationChanges.kt`) | geprüft, ohne Befund (Hard Rules §3.1, §3.2, §3.7 eingehalten; Kommentare Zusage, Kopplung, Abgrenzung) |
| `src/test/` und `src/integrator/` (`SimulationBackendDropDefaultTest`, `DMigrateWire.kt`, `DropDefaultEmbeddingTest`) | geprüft, ohne Befund (Wächter je Test genannt; Mutationen R-A bis R-D rot) |
| `referenztest-dotnet/`, `tools/mk/vergleichslauf-d.mk` | geprüft, ohne Befund |
| `docs/messungen/` | geprüft, ohne Befund (Erzeuger im Repo benannt, Testserver-Läufe als nicht im Repo geführt, Zeiten als Größenordnung) |
| `docs/user/` | geprüft, Befunde unter F-1 und F-2 |
| `spec/spezifikation.md` | geprüft, Befunde unter F-1, F-2 und F-6; keine ADR-Kennung in neuen Zeilen, Schichtregel gewahrt |
| ADR, `spec/architecture.md`, `config/`, Build-Dateien | unverändert, geprüft, ohne Befund |
| Haken der DoD, Zustandsfelder (Register, Roadmap) | geprüft, ohne Befund (kein `- [x]` im Diff; Zustandsfelder nennen Zustand und Beleg) |

## Summary

0 HIGH, 3 MEDIUM, 0 LOW, 3 INFO. Die Form ist im Code, in den Tests und im Messprotokoll belegt; die Mängel liegen in der Fortschreibung der Listen und der Grenzen an den zweiten Fundstellen (F-1, F-2) und in der Plan-Tabelle (F-3).
