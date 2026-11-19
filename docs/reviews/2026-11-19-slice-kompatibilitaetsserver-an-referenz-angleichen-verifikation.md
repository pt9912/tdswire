# Verifikationsbericht: slice-kompatibilitaetsserver-an-referenz-angleichen — 2026-11-19

**Rolle:** Verifier (Modul 11), frischer Kontext; DoD- und Entscheidungs-Konformität, Plan-vs-Code-Diff. Kein Review, kein Fix, keine DoD-Haken, kein Schließen, keine Änderung an `roadmap.md` oder am Lifecycle.

**Stand:** Branch `slice-kompatibilitaetsserver-an-referenz-angleichen`, Spitze `07eec886` (Commits `a659f3f8` Umsetzung, `0814b9bc` Review-Report, `0bdd8db4` Nachbesserung ohne zweiten Review, `1d5193a5` Folge-Slice angelegt, `07eec886` Slice eingeengt); Arbeitsbaum sauber vor diesem Bericht.

**Gegenstand:** [slice-kompatibilitaetsserver-an-referenz-angleichen](../plan/planning/done/slice-kompatibilitaetsserver-an-referenz-angleichen.md), eingeengt auf A-9 und `laufend.Id` (Teil von A-14); `kaputt.Text` liegt bei [slice-simulation-spaltenlaenge-in-auswahl](../plan/planning/in-progress/slice-simulation-spaltenlaenge-in-auswahl.md) (Nutzerentscheidung 2026-11-19). Bezug: [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md).

## Selbst gefahrene Sensoren (End-Stand `07eec886`, unter der Messsperre, Docker)

| Sensor | Ergebnis | Dauer |
|---|---|---|
| `make clean-build` | Exit 0 (Jar, Tests, detekt, `--no-cache`) | 580 s |
| `make e2e` | Exit 0, „gruen (9 Stufen, 414 s)“; Stufen 1 bis 9: 70, 89, 51, 47, 18, 17, 36, 13, 73 s | 414 s |
| `make kompatibilitaet` | Exit 0, Kategorie `Szenario`: 14 Tests bestanden, darunter beide geänderten `SzenarienTests` (Zeilenzahlen der Anweisungen; Trefferzahlen 2, 0, 3) | 15 s |
| `make vergleichslauf-d LAUF=mitschnitt` | Exit 0, `mitschnitt-2022` und `mitschnitt-2025`, je 324 Schritte, „gleich oder genau die gelistete Abweichung“; `cancel-streaming/01/colmetadata`: „gleich“ (ohne Regel) | 30 s |
| `make vergleichslauf-d LAUF=live` | Exit 0, `live-2022` und `live-2025`, je 25 Schritte; `aendern` und `loeschen`: Referenz und Server je `ExecuteNonQuery 1`, „gleich“ (ohne Regel) | 88 s |

Gesamt rund 19 Minuten Messzeit, davor Wartezeit auf die Messsperre (der Lauf `impl-mehrere-anweisungen` hielt sie; Übernahme 07:16:20, Freigabe per `trap`). Größte Posten: `make clean-build` (580 s) und `make e2e` (414 s, Stufe 2 mit 89 s). `available` vor jedem Lauf von einem Skript gegen 4000 MB geprüft (Ausgangswert 10,6 GB). Keine künstliche Last. Nicht gefahren: `make gates`, `make docs-check`, `make spec-abdeckung`, Mutationen (Aufgabe des Implementers nach `harness/README.md` §Minimal agent workflow; der Review hat sie nur gelesen, siehe F-V3).

## Plan-vs-Code-Diff (`git diff main...HEAD`, 10 Dateien, +355 −46)

| Plan §3 | Diff | Urteil |
|---|---|---|
| `KompatibilitaetsServer.kt` update | `onUpdate`/`onDelete` zählen mit `RowRules.matches` die Zeilen des Bestands `personRows`; `UPDATED_ROWS`, `DELETED_ROWS` entfallen; `running()` macht `laufend.Id` nullbar, `primaryKey` leer | gleich |
| `SzenarienTests.cs` update | `Geaendert = 1`, `Geloescht = 1`, `ZeilenMitAlter36 = 2`; neuer Test mit Trefferzahlen 2, 0, 3 | gleich |
| `KompatibilitaetsTablesTest.kt` neu | zwei Tests mit `@spec SPEC-035` und `SPEC-045` (Nachbesserung F-7) | gleich |
| `live-ausnahmen.tsv`, `mitschnitt-ausnahmen.tsv` update | entfallen: `aendern`, `loeschen`, `cancel-streaming/01/colmetadata`; `fehler-nach-zeilen/01/colmetadata` bleibt, Grund auf `kaputt.Text` verengt | gleich |
| `referenzabgleich.md` update | A-9 entfällt, A-14 auf `kaputt.Text` verengt, Ergebnis-Absatz trennt Messstand 2026-11-06 vom Lauf 2026-11-19 | gleich |
| `testschema.sql`, `LiveSzenarioTests.cs` unberührt | im Diff unberührt | gleich |
| nicht in §3 | `spec-abdeckung.md` (erzeugt, Folge der neuen Tags), Slice-Datei, `slice-simulation-spaltenlaenge-in-auswahl.md` (neu, `open/`), Review-Report | Folge des Auftrags, kein Befund |

`src/main` ist im Diff unberührt (Plan: „NICHT in diesem Slice“). `UPDATED_ROWS` kommt außerhalb von `done/` nur in `FlaecheEmbeddingTest.kt` vor, als eigene Konstante (3 L) ohne Kopplung.

## DoD Punkt für Punkt (Haken im Slice sind leer, das ist Sache des Planners)

| DoD-Punkt | Beleg | Urteil |
|---|---|---|
| A-9: Fixture meldet für `UPDATE` und `DELETE` die Zahlen der Referenz; Live-Regeln `aendern`, `loeschen` entfallen; Kopplung gleich | Live-Lauf 2022 und 2025: Referenz und Server 1 und 1, „gleich“; Regeln im Diff entfernt; Kopplungskommentar in `SzenarienTests.cs` nennt `person` mit Age 36, NULL, 36 | erfüllt (Grenze F-V1) |
| A-14 `laufend.Id`: nullbar, Regel `cancel-streaming/01/colmetadata` entfällt; `kaputt.Text` beim Folge-Slice | Mitschnitt 2022 und 2025: `cancel-streaming/01/colmetadata` „gleich“; `fehler-nach-zeilen/01/colmetadata` als Ausnahme (Luecke), genau diese Regel | erfüllt |
| Nachweis: Mitschnitt und Live grün, `make kompatibilitaet` im Verifier-Lauf | alle drei Läufe oben grün auf `07eec886`; der Schritt „Nachweis“ des Slice kann `live-2022`/`live-2025` als gefahren führen | erfüllt |
| `make gates`, engste Testklasse, Mutationen, `make test`, `docs-check`, `flaeche`, `kopplung-zahlen`, `spec-abdeckung` (Implementer) | nicht von mir gefahren (Implementer-Beleg); `make clean-build` (Tests, detekt frisch) und `make e2e` grün | nicht geprüft (Zuständigkeit) |
| Review, kein Self-Review | Report `2026-11-19-slice-kompatibilitaetsserver-an-referenz-angleichen.md` liegt vor; Nachbesserung `0bdd8db4` ohne zweiten Review (Text, Tag, Test) | erfüllt, Nachbesserung ohne Review |
| Doku-Update (`referenzabgleich.md`, Ausnahmelisten, Kopplung im Kopf) | im Diff; `testschema.sql`-Kopf bleibt zutreffend (Datei unberührt) | erfüllt |
| Closure-Notiz, Register, Risiken §6, Paarungen | §7 „Wird bei der Closure gefüllt“; Register und Risiko-Ausgänge offen | offen (Planner-Closure) |

## Zusagen des Auftrags

- **`aendern`, `loeschen`, `cancel-streaming/01/colmetadata` zu Recht entfernt:** ja. Der Live-Lauf zeigt auf beiden Instanzen „gleich“ ohne Regel, der Mitschnitt-Lauf „gleich“ ohne Regel.
- **Verbleibende A-14-Regel eng:** `fehler-nach-zeilen/01/colmetadata` allein, Feld `colmetadata`, Art `Luecke`, Grund nennt `kaputt.Text` (Länge 20 gegen 8000) und die Reichweite von `SimColumn.maxLength` mit Verweis auf `SPEC-035`. Die zweite Spalte (`Id`) steht im Vergleich ohne Abweichung.
- **§3.8, Aussagen gegen Beleg:** „`maxLength` ändert das COLMETADATA einer Auswahl nicht“ trägt `SPEC-035`; die Zeile A-14 nennt die Wirkung auf Katalogantworten, `MERGE` und die Probe des Massenladens (F-8 des Reviews behoben). „Referenz meldet 1 und 1“ trägt der Live-Lauf dieses Verifiers. Nicht getragen ist F-V1.

## Befunde

- **F-V1 · INFO · Die Trefferzahlen 2, 0, 3 sind nur am Server belegt.** Der Test `Aendern_und_Loeschen_melden_die_Zahl_der_Zeilen_auf_die_die_Bedingung_zutrifft` läuft gegen den Server (`make kompatibilitaet`); die Referenz zählt im Live-Lauf nur zwei Schritte mit je einer Zeile. Dass die Referenz bei zwei, null oder drei Treffern ebenso zählt, ist nicht gemessen; der Slice und der Kopplungskommentar behaupten es nicht, SQL Server zählt so, belegt ist es hier nicht.
- **F-V2 · INFO · Mitschnitt-Lauf belegt `laufend.Id` als Wiedergabe.** Der Vergleich liest aufgenommene Bytes der Referenz gegen die Antwort des Servers (`cancel-streaming/01/colmetadata` „gleich“, Aufnahme beider Instanzen); er belegt die Beschreibung von `laufend.Id` gegen die Aufnahme, nicht gegen eine frische Referenzabfrage.
- **F-V3 · INFO · Mutationen nicht von mir gefahren.** Der Review hat drei Mutationen nur gelesen (sein Bericht sagt „gelesen, nicht gefahren“); der Slice-§7 und die Nachbesserung tragen keinen gefahrenen Beleg. Für Zusagen von `testserver/` verlangt die Tabelle Mutationen vom Implementer; die Planner-Closure sollte den Beleg des Implementers nennen oder die Lücke vermerken.
- **F-V4 · INFO · Test „im_Batch“ ohne Trennschärfe.** Aus dem Review (F-9) bekannt: die drei Erwartungen sind alle 1; die Trennung leistet der neue Schwestertest. Kein Befund gegen die DoD.

Kein Befund der Klasse HIGH oder MEDIUM, keine DoD-Verletzung, keine Abweichung von [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md) oder den Spec-Stellen `SPEC-035`, `SPEC-045`.

## Ergebnis

Der Diff erfüllt die eingeengte DoD (A-9 und `laufend.Id`) nach Plan; die Läufe auf `07eec886` sind grün: `make clean-build`, `make e2e`, `make kompatibilitaet`, Mitschnitt und Live. Offen sind die Closure-Handlungen des Planners (Haken, §7, Register, Risiko-Ausgänge) und, sofern gewünscht, der Beleg der Mutationen des Implementers (F-V3).
