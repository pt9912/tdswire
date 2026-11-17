# Verifikationsbericht: slice-antwortform-nbcrow — 2026-11-17

**Rolle:** Verifier (Modul 11), Frage „Bauen wir es richtig?" gegen Plan und DoD; kein Review, keine Reparatur, keine DoD-Haken, kein Schließen.

**Gegenstand:** Branch `slice-antwortform-nbcrow`, Spitze `853239d5`, Basis `fbac7a7f`. Plan: [slice-antwortform-nbcrow](../plan/planning/in-progress/slice-antwortform-nbcrow.md). Bezug: [TDS-FA-05](../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen), [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md), [SPEC-003](../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog), SPEC-027. Messprotokoll: [zeilenform-nbcrow-2026-11-17](../messungen/zeilenform-nbcrow-2026-11-17.md). Vorangehendes Review: [2026-11-17-a40b50c3-slice-antwortform-nbcrow](2026-11-17-a40b50c3-slice-antwortform-nbcrow.md).

## Selbst gefahrene Sensoren (End-Stand `853239d5`, je einmal)

| Sensor | Ergebnis | Laufzeit |
|---|---|---|
| `make clean-build` | Exit 0; alle Gradle-Stages `BUILD SUCCESSFUL`, detekt grün, kein Testfall `FAILED` im Log | 11 min 15 s (Host-Last 11 bis 14) |
| `make e2e` | grün, 9 von 9 Stufen | 7 min 43 s (Stufe 1 95 s, 2 112 s, 3 55 s, 4 56 s, 5 16 s, 6 15 s, 7 34 s, 8 16 s, 9 64 s) |
| `make gates` | Exit 0; die Stempel der Klassen `werkzeug`, `tests`, `stempel` hatten für diesen Arbeitsbaum einen Beleg und wurden übersprungen (`FORCE=1` nicht gefahren; `clean-build` und `e2e` laufen die Tests frisch), die Gates der Klasse `immer` (u. a. `flaeche`: 72 Namen) liefen | 12 s |
| `make vergleichslauf-d LAUF=mitschnitt` | Exit 0, „gruen"; Abspielen am Server dieses Repos, keine SQL-Server-Instanz nötig (nur für DoD 2 gefahren) | 36 s |
| `make docs-check` | Exit 0, 1556 Dateien, 0 Befunde (mit diesem Bericht) | wenige Sekunden |

## Wichtigster Befund zur Aussagekraft von `make e2e` (Finding V-1)

**`make e2e` enthält keinen Lauf mit d-migrate oder mssql-jdbc.** Alle neun Stufen nutzen `Microsoft.Data.SqlClient` 7.1.1 (Stufen 3 bis 9) oder Rohbytes (1, 2); `grep -ci jdbc` über das Log: 0. Die Behauptung in Review-Finding F-5, `make e2e` mit der d-migrate-Abnahme sei der einzige Beleg zum Client mssql-jdbc, trifft nicht zu. Das einzige Ziel mit mssql-jdbc gegen diesen Server ist `make messung-treiber-handles` (Prepared-Handles; liest keine Zeilen mit NULL, nicht gefahren, nicht in `e2e`); `vergleichslauf-d LAUF=jdbc` läuft d-migrate gegen den echten SQL Server, nicht gegen diesen Server. **Ob mssql-jdbc NBCROW aus diesem Server liest, ist damit nicht gemessen.** Mildernd, aber kein Beleg am gebauten Stand: der echte Server schickt mssql-jdbc dasselbe NBCROW (Katalogabfrage von d-migrate, `SPEC-003`). Die Spec, die Matrix und das Messprotokoll nennen den Client als Setzung; das ist ehrlich, aber offen.

Zum Ergebnis von `e2e` für die Änderung: die Stufen 4 bis 7 (Referenzclient, alle acht Typen mit NULL, Einfügen mit NULL, Katalogabfragen, Lücken, Synchronität) und Stufe 3 und 9 (Massen- und Zeilenquellen) lesen jetzt NBCROW-Zeilen und sind grün. Das belegt `SqlClient`, keinen anderen Client.

## Befund je DoD-Punkt

| DoD-Punkt | Befund | Beleg |
|---|---|---|
| 1 Messung ROW/NBCROW am echten Server | **erfüllt** (am Beleg, nicht Lauf wiederholt: Läufe gegen die Instanzen sind nicht Teil der Verifikation) | Messprotokoll M-37 bis M-41 mit Bytes je Zeile; die Fälle des Plans (ohne NULL, eine NULL, nur NULL, NULL hinter der achten Spalte, ohne nullbare Spalte) stehen darin; Ablagen `zeilenform-null.messung` je Instanz liegen im Branch; Nicht-Gemessenes unter „Grenzen" benannt |
| 2 Sequenzierer schreibt die Regel; A-12 entfällt; Mitschnitt-Lauf grün | **erfüllt** | `RowForm.token` (`saved > ceil(n/8)`), `ResponseRun` ruft ihn je Zeile; `ResponseRowFormTest` (14 Tests, je Fall mit Bytes der Messung, `@spec SPEC-003`) und `ValueWriterNullSizeTest` liefen grün in `clean-build`; A-12 steht nirgends mehr in `mitschnitt-ausnahmen.tsv` oder den Tabellen (Grep); `LAUF=mitschnitt` grün, `batch-typen/01/tokenfolge` Referenz und Server beide `COLMETADATA ROW+ NBCROW+ DONE` ohne Ausnahme |
| 3 Zielbild-Prüfung | **erfüllt, mit Vorbehalt V-1** | Spec Absatz Zeilenform nennt die Bedingungen (ein Client, ein Paket, bis 17 Spalten) und die Setzungen (mehr als 17 Spalten, mehrere Dezimalspalten, anderer Client, mehrere Pakete); Matrix Zeile 6 „geschlossen" und `referenzabgleich.md` gleichziehend; Änderungsprotokoll-Zeile vorhanden; `make flaeche` und `docs-check` grün |
| Nachweis: `@spec` im KDoc, Tests in `make test` grün | erfüllt | Tags an jeder Testfunktion der Klasse; `clean-build` ohne roten Test |
| Nachweis: Aussage an Test oder als Festlegung | im Plan-Stand erfüllt; die Trennung *gemessen / Handvektor / Selbstkonsistenz / Festlegung* gehört in die Closure-Notiz (§7 „Wird bei der Closure gefüllt", noch offen) | Messprotokoll und `ValueWriterNullSizeTest` (Selbstkonsistenz) trennen es bereits |
| Nachweis: Zerstückelte, überlange, zufällige Eingaben (SPEC-027) | erfüllt für Zeilen- und Spaltenzahl | Tests `zufällige Zeilen …` und `überlange Zeilen und Ergebnisse …` mit `@spec SPEC-027`, grün; „zerstückelt" ist für die Zeilenwahl ohne Eingabestrom nicht anwendbar (keine Aufspaltung eines Eingabestroms, die Wahl liegt auf fertigen Werten) |
| `make gates` grün | erfüllt (Stempel-Belege, siehe oben) | Exit 0 |
| Review-Report liegt vor | erfüllt; F-1 bis F-3 laut Aufruf nachgebessert, gegengeprüft: Bedingungen in Spec und Matrix, Plan-Tabelle nennt `referenz-konfiguration.md`, Ziel angepasst | `docs/reviews/2026-11-17-a40b50c3-…`; F-5 des Reviews enthält die falsche e2e-Aussage (V-1) |
| Doku-Update öffentlicher Vertrag | erfüllt | SPEC-003, Nutzer-Doku `referenz-konfiguration.md` |
| Closure-Notiz mit Lerneintrag | **offen** (Closure-Sache, nicht Verifikation) | Slice §7 leer |
| Register fortgeschrieben | teilweise belegt: `mitschnitt-luecken-ohne-traeger/state.md` zur A-12 geändert; „keine weitere Beobachtung" ist in §7 zu notieren | offen mit Closure |
| Risiken in §6 mit Ausgang | **offen**; Ausgänge fehlen noch. Risiko „anderer Client lehnt NBCROW ab" kann nach diesem Bericht nur *weiter offen* lauten (V-1), nicht *entfallen* | Slice §6 |
| Drei Paarungen | Closure-Sache | — |

## Plan-vs-Code-Diff

Dateien des Diffs `fbac7a7f..853239d5` (26) gegen die Tabelle §3: alle dort genannten Dateien sind berührt, `RowForm.kt`, `ResponseRowFormTest.kt`, `ValueWriterNullSizeTest.kt`, die zwei `.messung`-Ablagen und das Messprotokoll sind neu wie geplant. Zusätzlich im Diff und in der Tabelle nachgetragen: `referenz-konfiguration.md`. Nicht in der Tabelle, aber im Diff: der Review-Report selbst (`docs/reviews/`, kein Plan-Gegenstand). Kein Quelltext außerhalb des Plans; `src/main` berührt nur `ResponseRun.kt`, `RowForm.kt`, `Token.kt` (KDoc), `ValueWriter.kt` (`nullSize`) wie in §3. Keine Abweichung vom Plan, keine Entscheidung ([ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md)) verletzt: der Encoder bleibt unverändert, der Sequenzierer wählt.

## Ungemessen (so zu führen)

- **Anderer Client** (mssql-jdbc von d-migrate): kein Lauf gegen diesen Server mit NBCROW-Zeilen; `make e2e` deckt ihn nicht ab (V-1).
- **Mehr als 17 Spalten** (Bitmap von vier und mehr Byte): Rechnung `ceil(n/8)` gesetzt.
- **Mehrere Dezimalspalten** und mehrere NULL daran; `decimal(10,2)` nur mit einer NULL in sechs Spalten gemessen; A-11 (Länge 17 statt 9) bleibt ungeklärt.
- **Mehrpaket-Ergebnisse** und mehrere Ergebnisse je Antwort: Wahl je Zeile, aber nicht an solchen Antworten gemessen.
- Größe eines NULL im ROW für `varchar`, `char`, `binary`, `float`, `money`, `datetime`, `smalldatetime`, `sql_variant`, `xml`, PLP am Server (nur Selbstkonsistenz); der Sequenzierer erreicht diese Typen nicht.
- Läufe gegen die echten Instanzen wurden hier nicht wiederholt; der Beleg ist das Protokoll und die Ablage.

## Findings

| ID | Kategorie | Befund |
|---|---|---|
| V-1 | MEDIUM | Die Beleg-Aussage in Review-F-5 (e2e mit d-migrate-Abnahme belegt mssql-jdbc) ist falsch: `make e2e` enthält keinen JDBC-Lauf. Folge: das Risiko „anderer Client" aus Slice §6 hat keinen Beleg; als *weiter offen* zu führen oder durch einen Lauf von mssql-jdbc (liest Zeilen mit NULL, ggf. NBCROW mit 9 und mehr Spalten) gegen den Testserver zu schließen — Entscheidung beim Planner/Nutzer. |
| V-2 | INFO | `make gates` lief mit Stempel-Belegen; die Tests sind durch `clean-build` frisch gelaufen. |
| V-3 | INFO | Slice §7, §6-Ausgänge und das Register sind noch offen; das ist Closure, nicht Mangel. |

Übergabe an den Planner. Kein DoD-Haken gesetzt, nicht geschlossen.
