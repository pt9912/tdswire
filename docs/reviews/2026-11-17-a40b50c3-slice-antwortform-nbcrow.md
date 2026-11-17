# Review-Report: slice-antwortform-nbcrow — 2026-11-17

**Review-Art:** Code, gegen Plan, Spec, ADR und Hard Rules (nicht gegen die DoD)

**Gegenstand:** Branch `slice-antwortform-nbcrow`, `git diff fbac7a7f..a40b50c3` (Commits `b55e028a` Messung, `3d22a33b` Bau, `56bb58a4` Doku, `a40b50c3` Matrix-Zitat). Plan: `slice-antwortform-nbcrow`.

**Skill:** `.harness/skills/reviewer.md` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-17

**Eingangs-Kontext:** [TDS-FA-05](../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen), [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md), [SPEC-003](../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog), `SPEC-027`, `AGENTS.md` §3.2, §3.7, §3.8, Messprotokoll `docs/messungen/zeilenform-nbcrow-2026-11-17.md` (M-37 bis M-41), Ablagen `zeilenform-null.messung` beider Instanzen.

Selbst gefahren: `make docs-check` (Exit 0, 1555 Dateien, 0 Befunde). Die Testklassen `ResponseRowFormTest`, `ValueWriterNullSizeTest`, `ResponseOrderTest`, `KatalogSpaltenWireTest` in Docker (Stage `test`, auf eine Kopie des Worktrees im Scratchpad mit gefiltertem `--tests`): grün (52 s). Fünf Mutationen einzeln, je ein Lauf (Tabelle unten). Nicht gefahren: `make gates`, `make flaeche`, `make kopplung-zahlen` (Diff berührt keinen Typ aus `SPEC-059`, keine Zahl aus `kopplung-zahlen`), `clean-build`, `e2e`, `vergleichslauf-d`, .NET-Mutationen. Keine künstliche Last; Host-Last beim Lauf 5, Speicher über 5 GB frei.

## Mutationen (selbst gefahren, je einzeln)

| Nr | Mutation | Wirkung gemessen |
|---|---|---|
| a | `RowForm`: `saved > bitmap` zu `saved >= bitmap` (Gleichstand NBCROW) | `ResponseRowFormTest` 12 Tests rot |
| b | Bitmap `ceil(n/8)` zu `n/8` (Aufrundung weg) | 22 Tests, 9 rot |
| c | Bitmap konstant 1 Byte | 22 Tests, 6 rot (alle Fälle ab 9 Spalten) |
| d | `ValueWriter.writeNull`: NVARCHAR-NULL ein Byte statt zwei | 4 rot, darunter `ValueWriterNullSizeTest` (die Größen stehen dort als Literale, nicht nur als Gleichheit beider Seiten) |
| e | `RowForm.nullSizes`: jede Spalte Größe 1 (Nullbarkeit und Typ ignoriert) | 3 rot |

Die Behauptung des Implementers (fünf Mutationen für `ResponseRowFormTest`) trägt für a bis c und e; d belegt zusätzlich `ValueWriterNullSizeTest`.

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| F-1 | MEDIUM | Die Regel steht in `SPEC-003`, im Nutzer-Satz und in der Matrix ohne ihre gemessene Bedingung: gemessen ist sie an einem Client (`Microsoft.Data.SqlClient` 7.1.1), an Antworten aus einem Paket und an Ergebnissen bis 17 Spalten. Die Setzungsliste der Spec nennt nur „mehr als 17 Spalten“ und „mehrere Dezimalspalten“; Client und Mehrpaket-Ergebnisse stehen nur im Abschnitt „Grenzen“ des Messprotokolls. Failure-Szenario: ein Leser der Spec nimmt die Wahl als clientunabhängig (mssql-jdbc von d-migrate bekommt jetzt NBCROW von diesem Server) und als für große Ergebnisse gemessen. | `AGENTS.md` §3.8; Skill „Zusage ohne ihre gemessene Bedingung im selben Satz“ | `spec/spezifikation.md` · „Setzung, nicht gemessen: die Rechnung für mehr als 17 Spalten und für mehrere Dezimalspalten“; `docs/user/referenz-konfiguration.md` · „am echten SQL Server 2022 und 2025 gemessen“ | nein (Lesen) | Aussage breiter als Beleg / Bedingung im anderen Absatz |
| F-2 | MEDIUM | Die Plan-Tabelle in Slice §3 nennt `docs/user/referenz-konfiguration.md` nicht; der Diff ergänzt dort einen Absatz (Zeilenform). Die Datei steht weder in der Zeile „`spec/spezifikation.md`, … update“ noch sonst. | Skill „Plan-Tabelle nennt die berührten Dateien nicht“ | `docs/plan/planning/in-progress/slice-antwortform-nbcrow.md` · „Register `BEO-TDS/mitschnitt-luecken-ohne-traeger` (`state.md`) | update“ | nein | Plan-Tabelle nennt berührte Datei nicht |
| F-3 | LOW | Das Ziel in Slice §1 („eine Zeile mit NULL-Wert so als `NBCROW`“) und der Plantext in §6 („Zeile mit NULL als NBCROW“) widersprechen dem Befund: eine Zeile mit einer NULL in `INTN` ist ROW. Die DoD („gemessene Regel“) ist richtig gefasst, das Ziel nicht; der Hinweis des Implementers trifft zu. | Maintainability (Plan trägt den abgelösten Zustand, `AGENTS.md` §3.7) | `docs/plan/planning/in-progress/slice-antwortform-nbcrow.md` · „Der Sequenzierer schreibt eine Zeile mit NULL-Wert so als `NBCROW`“ | nein | Plantext hinter dem Befund |
| F-4 | INFO | Die Größe eines NULL im ROW für PLP-Typen (acht Byte) und `sql_variant` (vier Byte) ist nur durch den Encoder gedeckt; am Server gemessen ist für PLP nur „NBCROW“ (Austausch 10), für `sql_variant` nichts. `ValueWriterNullSizeTest` und das Messprotokoll sagen das ehrlich („Selbstkonsistenz“, „der Sequenzierer erreicht diese Typen nicht“); `ColumnMapping` bildet `UnicodeText` nur auf `NVARCHAR(<n>)` ab. Keine Aktion. | `AGENTS.md` §3.8 | `src/test/kotlin/io/github/pt9912/tdswire/protocol/token/ValueWriterNullSizeTest.kt` · „Die Gleichheit mit dem Encoder ist Selbstkonsistenz“ | nein | — |
| F-5 | INFO | Die Wahl hängt nur an der Gleichung `Summe NULL-Größen > ceil(n/8)`; für nicht nullbare Spalten ist die Größe 0, für 0 Spalten ist das Ergebnis ROW. Das deckt die Messung (M-40) und `ResponseRowFormTest > ohne nullbare Spalte …`. Eine Verbindung in einem Ergebnis über mehrere Pakete ändert die Gleichung nicht, ist aber nicht gemessen (F-1). Für Verifier: ein Lauf `make simulations-integration` oder der Mitschnitt-Lauf gegen mssql-jdbc ist die einzige Stelle, die den Gegenclient berührt; nicht Teil der Tabelle für `src/`. | `harness/README.md` §Minimal agent workflow | `src/main/kotlin/io/github/pt9912/tdswire/protocol/response/RowForm.kt` · „if (saved > bitmap)“ | nein | — |

## Antworten auf die Schwerpunkte

1. **Trägt die Regel so weit wie die Messung?** Ja für Ergebnisse bis 17 Spalten im Batch und RPC aus einem Paket, für die acht Typen des Typmodells und `decimal(10,2)`/`nvarchar`. Ich habe die Ablage `2022/zeilenform-null.messung` gelesen: die beiden Ablagen unterscheiden sich nur in Anmeldung und Server-Kennungen, nicht in den Zeilen; die Bytes aus `ResponseRowFormTest` (z. B. `d1 01000000 04 0a000000 0200 7800`, `d2 06 04000000`, `d2 03`, 17 Spalten: drei NULL ROW `d1 000000 …`, vier NULL `d2 0f 00 00 …`, alle NULL `d2 ff ff 01`) stehen so in der Ablage. Die Trennung der Alternativen (Gleichstand ROW, Zahl-Schwelle gegen Größen-Schwelle) trägt die Messung. Nicht gemessen und als Setzung benannt: mehr als 17 Spalten, mehrere Dezimalspalten (KDoc von `RowForm`, Spec, Messprotokoll). Nicht gemessen und in der Spec nicht benannt: Client, Mehrpaket-Ergebnis (F-1).
2. **Code.** Bitmap `(n + 7) / 8` stimmt (Mutation b, c rot). NULL-Größen kommen aus `writeNull`, nicht aus einer zweiten Tabelle; ein Typ, den `writeNull` nicht kennt, fällt in `else -> 1 Byte` wie vor dem Slice, der Sequenzierer erreicht nur die zehn Typen des `SqlType`, alle gemessen. Nicht nullbare Spalte mit `NullValue` scheitert vorher in `ValueMapping.row`; `nullSize` einer nicht nullbaren Spalte ist ein Verstoß, aber `nullSizes` fragt sie nicht. Keine Index-Differenz: `values` und `nullSizes` stammen aus denselben Spalten. 0 Spalten ergibt ROW; 3000 Spalten und 20000 Zeilen stehen im Test.
3. **Tests.** Die Orakel sind die Bytes der Messung, nicht eine Neuberechnung der Regel; die Mutationen a bis e röten. `ResponseOrderTest` (Form „ROW“ für beide Token) und `KatalogSpaltenWireTest` (Werte beider Formen) sind sinnvoll nachgezogen: sie prüfen Reihenfolge und Werte, nicht die Form, und die Form hat nun `ResponseRowFormTest`.
4. **Zielbild.** `SPEC-003`, Matrix (Zeile NBCROW, Lücke 6) und `referenzabgleich.md` stimmen mit dem Messprotokoll überein; A-12 ist aus Tabelle und `mitschnitt-ausnahmen.tsv` entfernt, es gibt keine weitere Fundstelle des Namens außer in der Historie (Grep `\bA-12\b` über `spec/`, `docs/`, `src/`, `tools/`, `referenztest-dotnet/`). Die DoD-Haken stehen alle offen (kein Haken vor der Verifikation). Das Ziel des Slice ist enger als der Befund (F-3): die Wahl ist eine Regel je Zeile, nicht „NULL ergibt NBCROW“.
5. **Anderer Client.** Das Messprotokoll nennt ihn ehrlich unter „Grenzen“, Slice §6 führt ihn als offen; Spec, Matrix und Nutzer-Doku nennen ihn nicht (F-1). Mildernd: der echte Server schickt mssql-jdbc dieselben NBCROW (Katalogabfrage von d-migrate, `SPEC-003`); gelaufen gegen diesen Server ist es nicht.

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `src/main/kotlin/…/protocol/response/` (`RowForm.kt`, `ResponseRun.kt`) | geprüft, ohne Befund |
| `src/main/kotlin/…/protocol/token/` (`ValueWriter.nullSize`, `Token.kt`-KDoc) | geprüft, ohne Befund |
| `src/test/kotlin/…/protocol/response/`, `…/token/ValueWriterNullSizeTest.kt`, `…/runtime/KatalogSpaltenWireTest.kt`, `…/protocol/dispatch/RequestSequenceTest.kt` | geprüft, ohne Befund (Mutationen a bis e rot; kein `@Suppress`) |
| `referenztest-dotnet/` (`TokenLeser`, `MessAuswertung`, `MessungBulkBelegTests`, `MessungBulkTests`, Ablagen, `mitschnitt-ausnahmen.tsv`) | gelesen, nicht gefahren; Ablagen beider Instanzen zeilengleich geprüft, ohne Befund |
| `docs/messungen/` (Messprotokoll, `referenzabgleich.md`) | geprüft, ohne Befund; Erzeuger der Zahlen liegen im Repo |
| `docs/plan/` (Matrix, `spec-abdeckung.md`, Register `state.md`) | geprüft; F-2, F-3 |
| `docs/user/` | `referenz-konfiguration.md` ganz gelesen, kein Treffer auf Abwesendes/Geplantes im neuen Absatz; Grep `ROW`/`NBCROW` in den übrigen Dateien ohne Treffer (F-1 zur Bedingung) |
| `spec/spezifikation.md` | geprüft; F-1; Änderungsprotokoll-Zeile vorhanden |
| `tools/mk/vergleichslauf-d.mk` | geprüft, ohne Befund |
| Hard Rules `AGENTS.md` §3.2 (Suppression), §3.7 (Kommentare im Code) | geprüft, ohne Befund |

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 2 |
| LOW | 1 |
| INFO | 2 |

**Finding-Klassen dieses Laufs:** Aussage breiter als Beleg / Bedingung im anderen Absatz · Plan-Tabelle nennt berührte Datei nicht · Plantext hinter dem Befund

## Verdikt

**Merge-blockierend:** ja, wegen F-1 und F-2 (beide Doku, kein Code). Die Findings F-1 und F-2 betreffen Spec, Nutzer-Doku und Plan; ein Re-Review in frischem Kontext ist **nur** nötig, wenn die Nachbesserung Quelltext, Testcode, Skripte oder Konfiguration ändert. Eine reine Doku-Nachbesserung braucht keinen Re-Review.

**Übergabe:** Findings gehen an den Implementer; die Finding-Klassen in die Slice-Closure §7. Dieser Report ersetzt keine Verifikation.
