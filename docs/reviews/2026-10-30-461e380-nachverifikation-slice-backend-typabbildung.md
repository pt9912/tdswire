# Nachverifikation: slice-backend-typabbildung (Fix-Runde) — 2026-10-30

**Rolle:** Verifier (frischer Kontext) · **Modell:** claude-sonnet-5-5

**Gegenstand:** Diff `37c7d11..461e380` (Fix-Runde, Merge 03b1d40; Code-Stand ab d33534b) gegen die Befunde aus dem [Review-Report](2026-10-30-55fc860-slice-backend-typabbildung.md) (F-1 bis F-9) und dem [Verifikationsbericht](2026-10-30-55fc860-verifikation-slice-backend-typabbildung.md) (V-1 bis V-6), und die DoD des [Slice-Plans](../plan/planning/in-progress/slice-backend-typabbildung.md).

**Bezug:** [TDS-FA-05](../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen), [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilit%C3%A4t), [SPEC-002](../../spec/spezifikation.md#spec-002--interner-query-vertrag), [SPEC-003](../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog), [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md), [AGENTS.md](../../AGENTS.md) §3.7 und §3.8.

## 1. Gefahrene Sensoren (selbst, nur make und Docker, nichts gemountet)

| Lauf | Ergebnis |
|---|---|
| `docker build --target test --no-cache-filter test` (frisch) | BUILD SUCCESSFUL, 345 Tests PASSED, 0 FAILED |
| `make docs-check` vor diesem Bericht | 266 Dateien, 0 Befunde |
| `make gates` | Exit 0 |
| Flakiness: Kopie des Repo-Stands im Scratchpad, Testklassen `DecimalValuesTest`, `ValueMappingTest`, `ColumnMappingTest` zwölfmal mit `cleanTest`, dazu 60 Busy-Loop-Prozesse in einem Container; Host-Load im Mittel 60 bis 75 bei 20 Kernen | 12 von 12 Läufen grün; die Fristtests (5 s in `DecimalValuesTest`, 2 s in `Decimal mit riesiger Mantisse bindet keine Rechenzeit`) je zwölfmal bestanden |
| Mutationsprobe, 21 Mutanten je einzeln in eigener Kopie (Abschnitt 3) | 18 getötet, 3 überlebt |

`make docs-check` ist nach dem Schreiben dieser Datei erneut gelaufen (siehe Abschnitt 7).

## 2. Befunde aus Review und Verifikation: Zuordnung

| ID | Stand | Beleg |
|---|---|---|
| [F-1](2026-10-30-55fc860-slice-backend-typabbildung.md) Skalierung vor Grenzprüfung (`DecimalValues`) | **behoben** | Prüfung über `integerDigits` vor `setScale`; Mutant M1 (setScale vor der Prüfung wieder eingebaut) wird von `DecimalValuesTest` getötet; Exponenten bis `1E+999999999` und `-1E+2147483647` enden in `VALUE_OUT_OF_RANGE` |
| [F-2](2026-10-30-55fc860-slice-backend-typabbildung.md) Fehlertext-Test nicht mutationsfest | **behoben, mit Rest** (N-1) | Leck-Mutanten L1 bis L8 und L10 bis L12 getötet, je Fehlerpfad einzeln; L9 (Mantissenpfad) überlebt |
| [F-3](2026-10-30-55fc860-slice-backend-typabbildung.md) Zonen-Unabhängigkeit nicht erzwungen | **behoben für DateTime und Time, mit Rest** (N-2) | Zonentest mit Europe/Berlin und America/New_York (Sommerzeitlücke); Z1, Z2, Z4 getötet; Z3 (Date) überlebt |
| [F-4](2026-10-30-55fc860-slice-backend-typabbildung.md) Spec und KDoc nicht nachgeführt | **behoben, mit Rest** (N-3) | SPEC-002 und SPEC-003 nachgeführt, SqlType-KDoc nachgeführt, Änderungsvermerk in der Tabelle der Spec |
| [F-5](2026-10-30-55fc860-slice-backend-typabbildung.md) Abschnittsnummern ohne Quelle | **behoben im Gegenstand** | in `protocol/result`, den Tests und den vier Dateien `DecimalValues`, `TemporalValues`, `TypeInfoWriter` und `ValueWriter` ersetzt durch „Layout nach MS-TDS“; Slice §1 nennt die Regel. Nummern stehen weiter in `Token.kt`, `TdsType.kt`, `TokenWriters.kt` und den Parsern: nicht Gegenstand dieses Slice, kein Befund hier |
| [F-6](2026-10-30-55fc860-slice-backend-typabbildung.md) Decimal-Lesart nicht als Festlegung | **behoben** | KDoc von `DecimalMapping`, Testtext und Slice §6 führen die Lesart als „Festlegung ohne Messung und ohne Bestätigung des Nutzers“ |
| [F-7](2026-10-30-55fc860-slice-backend-typabbildung.md) Etikett Handvektor | **behoben** | Etiketten „von Hand gerechnet“ und „Selbstkonsistenz mit den Encoder-Tests“ in beiden Testklassen; Slice §1 trägt die Aussage |
| [F-8](2026-10-30-55fc860-slice-backend-typabbildung.md) Zeile ohne Kurzschluss | **nicht behoben und nicht als offen geführt** (N-4) | `ValueMapping.row` unverändert; weder Slice §6 noch Spec noch Vertrag nennen es |
| [F-9](2026-10-30-55fc860-slice-backend-typabbildung.md) Lesart braucht Nutzerbestätigung | **ehrlich offen** | Slice §6: „vom Nutzer noch nicht bestätigt“, die Closure-Notiz §7 ist noch leer („Wird bei der Closure gefüllt.“) |
| [V-1](2026-10-30-55fc860-verifikation-slice-backend-typabbildung.md) Spec, Vertrag, KDoc | **behoben** | (a) bis (d): SPEC-002 und SPEC-003, [contracts/query-backend.md](../../contracts/query-backend.md) und die KDoc von `SqlType` sagen dasselbe wie der Code; die „Offen“-Liste der Spec und des Vertrags nennt übereinstimmend OutputParameter und endgültige Zeilendarstellung |
| [V-2](2026-10-30-55fc860-verifikation-slice-backend-typabbildung.md) DoD-Wortlaut „Scale 0 bis 7“ | **behoben in der DoD, Rest im Risiko** (N-5) | DoD §2 sagt „fester Scale 7 (Festlegung …)“; Slice §6 (b) trägt die Alternative „Scale 0 bis 7“ noch (bekannt) |
| [V-3](2026-10-30-55fc860-verifikation-slice-backend-typabbildung.md) Closure-Pflichten | **offen, zu Recht** | Slice liegt in `in-progress/`, §7 leer, Risiken ohne Ausgang; kein Befund gegen den Code |
| [V-4](2026-10-30-55fc860-verifikation-slice-backend-typabbildung.md) KDoc breiter als Beleg, Mantisse | **behoben, mit Rest** (N-6) | Grenze 1000 Stellen vor `stripTrailingZeros`, als Festlegung gekennzeichnet; M2, M4, M5 getötet; M3 überlebt |
| [V-5](2026-10-30-55fc860-verifikation-slice-backend-typabbildung.md) Fehlertext und Zone nicht mutationsfest | **behoben, mit Rest** | wie F-2 und F-3 |
| [V-6](2026-10-30-55fc860-verifikation-slice-backend-typabbildung.md) Beleg-Etikett und Quelle | **behoben** | wie F-5 und F-7 |

## 3. Mutationsprobe (je Mutant einzeln, frischer Build der Stage test)

| Mutant | Eingriff | Ergebnis |
|---|---|---|
| M1 | `setScale` vor der Bereichsprüfung in `DecimalValues` | getötet: `DecimalValuesTest` |
| M2 | Mantissengrenze `>` zu `>=` | getötet: Test riesige Mantisse (1000 Stellen müssen passen) |
| M3 | Mantissengrenze `>` zu `> 1000 + 1` | **überlebt** |
| M4 | Mantissengrenze entfernt | getötet |
| M5 | Konstante auf 100 000 000 | getötet (Zeitgrenze 2 s) |
| L1 | Text zu lang: Textanfang im Detail | getötet |
| L2 | unpaares Surrogat: Text im Detail | getötet |
| L3 | Time zu fein: Wert im Detail | getötet |
| L4 | DateTime zu fein: Wert im Detail | getötet |
| L5 | Date außerhalb: Wert im Detail | getötet |
| L6 | DateTime außerhalb: Wert im Detail | getötet |
| L7 | Decimal Scale: Wert im Detail | getötet |
| L8 | Decimal Precision: Wert im Detail | getötet |
| L9 | Decimal Mantisse zu lang: Wert im Detail | **überlebt** |
| L10 | Typkonflikt: Wert im Detail | getötet |
| L11 | Spaltenname zu lang: Name im Detail | getötet |
| L12 | Spaltenname mit Surrogat: Name im Detail | getötet |
| Z1 | DateTime über Standardzone nach UTC und zurück | getötet (Sommerzeitlücke) |
| Z2 | DateTime in UTC umgerechnet | getötet |
| Z3 | Date in UTC umgerechnet | **überlebt** |
| Z4 | Time in UTC umgerechnet (festes Datum 2024-03-31) | getötet |

## 4. Interferenz des Zonentests

Der Test `Bytes sind unabhaengig von der Standardzone der JVM` setzt `TimeZone.setDefault` und stellt im finally-Block den gesicherten Wert wieder her. Die Test-JVM läuft ohne parallele Ausführung und ohne Forks (`build.gradle.kts`: nur `useJUnitPlatform()`, das Init-Skript setzt nur ein TLS-Flag und Pfade); es gibt kein `user.timezone` und kein `systemDefault`, `LocalDate.now` oder `Clock.system` in `src/main` oder `src/test` außerhalb dieses Tests. Eine Beeinflussung anderer Tests ist damit nicht möglich, solange die Ausführung sequenziell bleibt; das ist eine Voraussetzung, die kein Gate hält. Beobachtet: 345 grüne Tests im Gesamtlauf und zwölf grüne Läufe der Klassen unter Last.

## 5. Spec- und Vertragskonsistenz

- [SPEC-002](../../spec/spezifikation.md#spec-002--interner-query-vertrag): Precision über 38 scheitert in der Typabbildung vor COLMETADATA; deckt sich mit `ColumnMapping` (INVALID_TYPE, Kategorie `TDS_UNSUPPORTED_TYPE`) und dem Slice.
- [SPEC-003](../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog): Setzungen zu Zone, Scale 7, NVARCHAR(4000), Kollation und Decimal ohne Rundung stimmen mit `TemporalMapping`, `ColumnMapping` und `DecimalMapping` überein, mit einer Ausnahme (N-3).
- [contracts/query-backend.md](../../contracts/query-backend.md) und die KDoc von `SqlType` sagen dasselbe wie die Spec.
- Abwärtslinks: der Spec-Diff enthält keinen Link, keine ADR-, Slice- oder Wellenkennung; `make docs-check` meldet 0 Befunde (Klasse `spec-straten`, Richtung `no-downward`).
- Die Fix-Runde enthält außerdem Änderungen am Handshake-Slice (`protocol/session`, `contracts/client-profile.md`); die sind nicht Gegenstand dieser Nachverifikation.

## 6. Neue und verbleibende Befunde

| ID | Kategorie | Befund |
|---|---|---|
| N-1 | LOW | Mutant L9 überlebt: der Fehlerpfad „Mantisse über 1000 Stellen“ steht nicht in der Fallliste von `Fehlertext enthaelt keine Zeilenwerte`. Die Zusage „Fehlertext ohne Zeilenwerte“ ist für diesen einen Pfad im Code wahr, im Test nicht erzwungen. Abhilfe: Fall mit Marker-Ziffernfolge ergänzen. |
| N-2 | LOW | Mutant Z3 überlebt: der Zonentest prüft Time und DateTime, nicht Date. Ein Date, das über die Standardzone nach UTC läuft, verschiebt sich in Berlin um einen Tag; kein Test sieht es. Die DoD-Aussage „ohne Zeitzonenumrechnung“ ist für Date nur gelesen, nicht erzwungen. |
| N-3 | LOW | Die Spec ([SPEC-003](../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog), Setzung zu Decimal) sagt „nachlaufende Nullen zählen nicht“; die Abbildung weist aber eine Mantisse mit mehr als 1000 Stellen auch dann ab, wenn der Wert nach dem Kürzen passt (nur KDoc und Test nennen es). Die Aussage der Spec ist dort breiter als das Verhalten ([AGENTS.md](../../AGENTS.md) §3.8). Außerdem trägt die Spec die Decimal-Lesart als Setzung, nicht als vom Nutzer unbestätigt (der Slice tut es). |
| N-4 | INFO | [F-8](2026-10-30-55fc860-slice-backend-typabbildung.md) (Zeile ohne Kurzschluss) ist weder umgesetzt noch als offen geführt. Entscheidung des Implementers ohne Korrektheitswirkung; ein Satz im Slice (§6 oder Closure) genügt. |
| N-5 | LOW | Slice §6 (b) nennt noch die Alternative „Scale 0 bis 7“ neben der Festlegung; der Ausgang des Risikos „offen bis zur Closure“ muss in der Closure-Notiz auf „Scale 7 als Festlegung“ gebunden werden. Bekannt, nicht neu. |
| N-6 | LOW | Mutant M3 überlebt: die Abweisung bei 1001 Stellen ist nicht getestet, nur 1000 (passt) und 100 001 (abgewiesen). Die Grenze von 1000 ist so nur einseitig belegt. |

Keine HIGH-Befunde. Die Rechenzeit-Klasse (F-1, V-4) ist im Code geschlossen und durch Zeitgrenzen im Test erzwungen; die Tests waren in zwölf Läufen unter hoher Last stabil.

## 7. DoD des Slice, erneut bestätigt

| DoD-Zeile | Stand | Beleg |
|---|---|---|
| Metadatenabbildung | **bestätigt** | `ColumnMappingTest`; Precision 1 bis 38, Text 4000 Einheiten, Scale 7 als Festlegung (nicht 0 bis 7, V-2/N-5), N-Form, Liste vor Ausgabe validiert, leere Liste gültig |
| Wertabbildung | **bestätigt** | `ValueMappingTest`; Zonen-Unabhängigkeit für DateTime und Time erzwungen, für Date nur gelesen (N-2) |
| Fehlerverhalten | **bestätigt** | Kategorie `TDS_UNSUPPORTED_TYPE`, Fuzz mit festem Startwert, Fehlertext ohne Zeilenwerte bis auf den Mantissenpfad erzwungen (N-1) |
| `@spec`-Tag je Test, `make test` grün | **bestätigt** | 345 PASSED im frischen Lauf |
| Beleg-Zuordnung in der Closure-Notiz | **offen** | §7 leer; Zuordnung *gemessen* · *Handvektor* · *Selbstkonsistenz* · *Festlegung* steht noch aus |
| `make gates` grün | **bestätigt** | Exit 0 |
| Review-Report | **bestätigt** | [Review-Report](2026-10-30-55fc860-slice-backend-typabbildung.md) |
| Doku-Update bei Vertragsberührung | **bestätigt** (war V-1) | Abschnitt 5 |
| Closure-Notiz mit Lerneintrag, Register, Risiko-Ausgänge, drei Paarungen | **offen** | erst bei der Closure; Nutzerbestätigung der Decimal-Lesart (F-9) gehört dazu |

## Verdikt

Die Befunde F-1 bis F-7 und V-1, V-4 bis V-6 sind in Code, Test, Spec, Vertrag und KDoc behoben und durch eigene Mutanten belegt; F-9 und V-3 sind ehrlich offen geführt. Nicht behoben und nicht geführt: F-8 (N-4). Neue Reste sind LOW (N-1, N-2, N-3, N-6) und kein Hindernis für die Closure, sollten aber in der Closure-Notiz stehen oder vorher mit je einem Test beziehungsweise einem Satz in der Spec geschlossen werden. Kein Rückweg nach `next/` oder `open/`.

**Empfehlung an den Planner:** N-3 (Spec-Satz zur 1000-Stellen-Grenze und Kennzeichnung der unbestätigten Lesart) und N-5 (Ausgang von §6 (b)) in der Closure erledigen; N-1, N-2, N-6 als drei kleine Tests in einer Mini-Runde oder als Rest in der Closure-Notiz führen; F-8 benennen; Nutzerbestätigung zu F-9 einholen. Danach kann der Slice nach `done/`.
