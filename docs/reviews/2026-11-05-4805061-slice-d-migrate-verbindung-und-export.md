# Review-Report: slice-d-migrate-verbindung-und-export — 2026-11-05

**Review-Art:** Code (gegen Plan + Konventionen)

**Gegenstand:** Zweig `slice-d-migrate-verbindung-und-export`, Diff `6e91aec..4805061` (Commits 926fd58, 2eaca47, 95a3c99, 39c8872, 34ec7a2, 75ca5c7, 4805061)

**Skill:** `.harness/skills/reviewer.md` @ 6e91aec · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-05

**Eingangs-Kontext:**

- Plan: [slice-d-migrate-verbindung-und-export](../plan/planning/done/slice-d-migrate-verbindung-und-export.md) (DoD, §1, §3, §6)
- [ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md) und [ADR-0026](../plan/adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md) (siehe Hinweis unten)
- [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität)
- `AGENTS.md` §3.2, §3.6, §3.7, §3.8
- Messprotokoll [d-migrate-verbindung-und-export-2026-11-05](../messungen/d-migrate-verbindung-und-export-2026-11-05.md), Sonde [d-migrate-sonde-2026-11-05](../messungen/d-migrate-sonde-2026-11-05.md)

Hinweis zum Eingang: die Dateinamen der ADRs 0025 und 0026 habe ich nicht aufgelöst; der Diff berührt keine ADR, ein ADR-Verstoß wurde nicht gefunden. Es wurde kein Gate (`make gates`, `make test`) gefahren; Aussagen zu Tests stammen aus dem Lesen von Code und Tests, die Mutationen habe ich nicht gefahren, sondern im Kopf gegen die Tests geprüft.

---

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| F-1 | MEDIUM | Der Konstantenterm erkennt jedes Paar von Ganzzahlen (`1 = 1`, `7 = -7`, `007 = 7`), gemessen ist nur der Text `1 = 0`. Das Lastenheft nennt in [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) (Negative und Out-of-Scope) „Ausdruck“ als nicht erkannt und verbietet die Auswertung; die Spezifikation begründet die Ausnahme mit „kein Ausdruck im Sinn der Liste“, das ist eine Lesart des Implementers. Szenario: ein Client sendet `WHERE 2 = 2` und erhält Zeilen, wo die Lastenheft-Negative einen definierten SQL-Fehler vorsieht. | [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), `AGENTS.md` §3.8 | `spec/spezifikation.md` · „Der Term ist kein Ausdruck im Sinn der Liste „Nicht erkannt““; `src/main/kotlin/io/github/pt9912/tdswire/simulation/StatementParser.kt` · `return Term.Constant(integer(left) == integer(right))` | nein (Lesart des Lastenhefts, Entscheidung des Nutzers/Architects) | „Erweiterung über den Messbeleg und eine Lastenheft-Ausschlussliste hinaus“ |
| F-2 | MEDIUM | [SPEC-030](../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten) legt `@P<i>` für `sp_executesql`, `sp_prepexec` und `sp_execute` fest, getestet ist nur `sp_executesql` (`RpcPositionalNamesTest`); für `sp_prepexec`, `sp_execute` und einen Parameter ohne Namen mit OUTPUT-Bit gibt es keinen Test. Szenario: `nameInText = false` an den Zeilen 89 und 92 von `RpcBinder.kt` wird auf `true` gesetzt oder die Namensbildung auf `ExecuteSql` beschränkt, kein Test wird rot, und ein Client mit wiederholten Prepared Statements (JDBC nutzt nach `sp_executesql` `sp_prepexec` und `sp_execute`) bekommt `PARAMETER_NAME`. | `AGENTS.md` §3.8, Reviewer-Skill (Eigencode ohne rotmachenden Test) | `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/RpcBinder.kt` · `RpcParameters.map(call.parameters, nameInText = false)`; `src/test/kotlin/io/github/pt9912/tdswire/protocol/dispatch/RpcPositionalNamesTest.kt` | ja (Mutation, `make test`) | „Spezifizierter Pfad ohne Test, Mutation bleibt grün“ |
| F-3 | LOW | Der Befund aus Hürde 8 (`--filter "Id = 7"`: `bigint`-Parameter gegen `Int32`-Spalte, `Invalid value`; Date-Parameter „Ursache nicht untersucht“) hat laut Messprotokoll „kein Träger benannt“. Die DoD des Slice verlangt die Paarung Anker, Folge-Slice, Register; im Diff steht kein Register-Eintrag unter `docs/plan/planning/observations/`. Er betrifft auch die offenen Schreib-Slices, die laut Plan Int64-Parameter gegen Spalten senden. | Maintainability (DoD „Paarungen“ des Slice) | `docs/messungen/d-migrate-verbindung-und-export-2026-11-05.md` · „kein Träger benannt“ | nein | „Befund ohne Träger“ |
| F-4 | LOW | In `RpcCatalog.kt` fehlt dem KDoc-Absatz die Zeile mit führendem Stern: ` Festlegungen (ohne Orakel): Text und Deklarationsliste …` steht ohne ` * `. Der Block bleibt formal ein Kommentar, die Zeile ist ein Rest der Teilersetzung. | `AGENTS.md` §3.7 | `src/main/kotlin/io/github/pt9912/tdswire/protocol/rpc/RpcCatalog.kt` · „ Festlegungen (ohne Orakel): Text und Deklarationsliste“ | ja (ktlint/detekt hat es nicht gefangen; Lesen) | „Kommentarzeile aus Teilersetzung ohne Stern“ |
| F-5 | INFO | Das Messprotokoll nennt als gemessenen Stand den Commit `39c8872`; danach änderte `75ca5c7` noch `SimulationBackend.kt` (Spread-Operator, laut Commit nur Form). Die Läufe decken also nicht den letzten Stand des Backends. | `AGENTS.md` §3.8 | `docs/messungen/d-migrate-verbindung-und-export-2026-11-05.md` · „Quelltext des Commits `39c8872`“ | nein | „Messstand weicht vom Lieferstand ab“ |
| F-6 | INFO | `SET IMPLICIT_TRANSACTIONS ON` wird als Erfolg (Zähler 0) beantwortet, ohne dass die Sitzung ihr Verhalten ändert; die Option ist die einzige der Liste, die nicht gemessen ist (Sonde, „hier nicht erneut gemessen“). Die Spezifikation sagt beides ausdrücklich („nicht modelliert“, Setzung); Hinweis für den Verifier: [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) kennt keine Transaktionssemantik. | `AGENTS.md` §3.8 | `src/main/kotlin/io/github/pt9912/tdswire/simulation/FixedForms.kt` · `"IMPLICIT_TRANSACTIONS",` | nein | „Stiller Erfolg einer nicht modellierten Option“ |
| F-7 | INFO | Die Spezifikation beschreibt in SPEC-030 und SPEC-036 das Verhalten „vorher“ („Die Bibliothek verlangte zwei Parameter …“, „Vorher antwortete die Bibliothek …“) und die Zwischenstufe 50007 als Chronik. Die Kommentarregel §3.7 nennt Spezifikation nicht ausdrücklich; Hinweis an den Architect, ob die Regel dort gelten soll. | `AGENTS.md` §3.7 | `spec/spezifikation.md` · „Vorher antwortete die Bibliothek auf `SELECT 1`“ | nein | „Chronik in Spec-Prosa“ |

## Antworten auf die Schwerpunkte

1. **Konstantenterm in UPDATE/DELETE:** bestätigt nicht erkannt. `Parts.whereClause(constants = false)` ist die Voreinstellung, nur `select(...)` ruft mit `constants = true`; in `UPDATE`/`DELETE` landet `1` in `term()` → `identifier()` und endet in `UNEXPECTED_TOKEN`. Ein Filter ohne Spaltenterm kann dort keine Löschung aller Zeilen auslösen; `Aenderungen mit Konstantenterm sind abgelehnt` prüft auch, dass kein Callback läuft. `SET`: `FixedForms.settings` zerlegt an jedem `;` und verlangt je Segment genau drei Token; ein Segment außerhalb der Liste (`DROP`, leer, Variable, Literal als Argument) macht den ganzen Text nicht erkannt (alles oder nichts), der Fall fällt dann in den Tabellen-Parser und endet in `TDS_UNSUPPORTED_SQL`. Kommentare entfernt der Scanner vorher, Semikolons in Literalen und Bezeichnern sind eigene Token (`Text`, `Quoted`) und trennen nichts; `[SET]` ist `Quoted`, kein Schlüsselwort. Kein Durchrutschen gefunden.
2. **Parameter ohne Namen:** `@P<i>` zählt über alle Wertparameter der Nachricht (Text, Deklaration, Handle sind vorher abgespalten); ein gesendetes `@P0`/`@p0` neben einem erzeugten gleichen Namen ist `PARAMETER_NAME` (getestet, beide Richtungen und Schreibweisen); benannt und unbenannt gemischt trägt `@x`, `@P1`. Per Prozedurname bleibt der leere Name abgelehnt (`nameInText = true`, getestet). Nicht getestet: `sp_prepexec`/`sp_execute` und OUTPUT ohne Namen (F-2). Der .NET-Pfad (benannte Parameter) berührt den neuen Zweig nicht (`parameter.name.isEmpty()` ist dort falsch); Risiko 3 ist durch den Code plausibel entkräftet, aber nicht gelaufen.
3. **Geänderte Tests:** `SELECT 1` → `SELECT 2` in `StatementFormsTest`, `SimulationBackendSelectTest` und `SzenarienTests.cs` behalten die Zusage („Anweisung außerhalb der Formen ist Anfragefehler, Verbindung bleibt nutzbar“); `SELECT 1` ist in `FixedFormsTest` und `SimulationBackendFixedTest` neu belegt. In `RpcFailureTest` entfällt der Fall „nur Text“, weil er jetzt gültig ist (die neue Zusage steht in `RpcPositionalNamesTest`, einschließlich des Falls ohne Parameter, NULL und Integer); `RpcBinderTest` verschiebt den leeren Namen vom `sp_executesql`-Fall in den Fall per Prozedurname. Keine Zusage nach §3.6 gelockert (kein Gate, keine Schwelle berührt). Mutationen im Kopf: `SELECT 1` anderer Wert (rot durch `SELECT 2/0/-1/01`), `WHERE 1 = 1` (rot: `WHERE 1 = 1 trifft jede Zeile` und `never`), Datei-Scanner (kein Bezug im Diff erkennbar, von mir nicht zuordenbar). Lücke: F-2.
4. **Handvektoren:** `TEXT_ONLY` und `POSITIONAL` sind im Test und im Messprotokoll als Aufzeichnung aus nicht committeter Instrumentierung benannt; ich habe die Längenfelder nachgerechnet (Textlänge 0x44 = 34 Zeichen, 0x52 = 41 Zeichen, Deklaration 0x14 = 10 Zeichen, ALL_HEADERS 0x16, `INTN` 8 mit Namenslänge 0) und sie stimmen mit dem TDS-RPC-Aufbau. Sie sind unabhängig vom Code (kein Selbstzitat); nicht nachprüfbar bleibt die Herkunft selbst, weil die Instrumentierung fehlt (das Protokoll sagt es offen).
5. **Spec und Messprotokoll:** Setzung/Messung sind in den neuen Absätzen getrennt; kein Pfadverweis von `spec/` nach `docs/messungen` (Diff durchsucht). Messprotokoll hat Datum, Host, Last, Versionen, Digest (Client und Server-Image), Befehle und Ergebnis; Abweichung F-5. Zu F-1: die Spezifikation führt eine Form ein, die das Lastenheft eng gesehen ausschließt, daher MEDIUM mit Entscheidung durch den Architect/Nutzer; wird die Lesart verworfen, wäre es eine Erweiterung der oberen Schicht (HIGH-Klasse).
6. **Öffentliche Fläche:** alle neuen Namen sind `internal` (`FixedForms`, `FixedAnswers`, `FixedForm`, `SetOption`, `Term.Constant`/`Named`, `RowTerm.Constant`, `RowCondition.never`, `PlanResult.Fixed`, `Recognition.Fixed`, `asciiUpper`, `isKeyword`); `SimulationBackend` hat nur KDoc geändert. Kein Name aus SPEC-059 betroffen.
7. **Kommentare:** Ist-Zustand, bis auf F-4 (und F-7 in der Spec).
8. **Befund `--filter "Id = 7"`:** gehört nicht in diesen Slice (Abgrenzung §1: nur Lesen, Export ohne Zeilen; Filter steht nicht im Soll von [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), dessen Happy Path einen Export ohne Filter beschreibt). Er braucht einen Träger (F-3); die Ursache ist nur aus dem Code gelesen. Das Messprotokoll und `docs/user/testserver-manuell.md` kennzeichnen sie als vermutet.

Suppression-Verbot (`AGENTS.md` §3.2): im Diff keine `@Suppress`, `#noqa`, `//nolint`; `config/`, `build.gradle.kts` und `Dockerfile` unverändert.

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `src/main/.../simulation/` (Parser, Planer, Backend, FixedForms, FixedAnswers) | geprüft, ohne Befund außer F-1, F-4 nicht dort |
| `src/main/.../protocol/dispatch/RpcParameters.kt` | geprüft, ohne Befund |
| `src/main/.../protocol/rpc/RpcCatalog.kt` | geprüft, Befund F-4 (Kommentar) |
| `src/test/.../simulation/` | geprüft, ohne Befund |
| `src/test/.../protocol/` | geprüft, Befund F-2 |
| `referenztest-dotnet/` | geprüft, ohne Befund (nur Textwechsel) |
| `spec/spezifikation.md` | geprüft, Befunde F-1, F-7 |
| `docs/messungen/`, `docs/user/` | geprüft, Befunde F-3, F-5 |
| `docs/plan/planning/in-progress/` (Slice) | geprüft, ohne Befund |

## Summary

HIGH 0 · MEDIUM 2 · LOW 2 · INFO 3.

Kein Befund wurde herabgestuft; es gibt keinen Widerspruch des Implementers, der einen Konflikt-Pfad auslöst. F-1 braucht eine Entscheidung des Architects über die Lesart des Lastenhefts.
