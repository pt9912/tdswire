# Review-Report: slice-backend-typabbildung — 2026-10-30

**Review-Art:** Code (Diff gegen Plan, ADR, Spezifikation und Hard Rules aus `AGENTS.md`; nicht gegen die DoD)

**Gegenstand:** Diff `04539b8..55fc860` (Merge der Typabbildung), 11 Dateien: `src/main/kotlin/.../protocol/result/` (6 Dateien), `src/test/kotlin/.../protocol/result/` (4 Dateien), Slice-Plan `slice-backend-typabbildung`.

**Skill:** `.harness/skills/reviewer.md` @ 55fc860
**Modell:** claude-sonnet-5-5 · **Datum:** 2026-10-30

**Eingangs-Kontext:**

- Slice-Plan `slice-backend-typabbildung` (§1 Abgrenzung, §3 Plan, §6 Risiken mit den Nutzerentscheidungen vom 2026-10-30)
- [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md) (Accepted)
- [TDS-FA-05](../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen); [SPEC-002](../../spec/spezifikation.md#spec-002--interner-query-vertrag), [SPEC-003](../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog), [SPEC-014](../../spec/spezifikation.md#4-fehler-codes-und-logging-felder), [SPEC-031](../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität)
- [AGENTS.md](../../AGENTS.md) (Hard Rules 3.1, 3.2, 3.7, 3.8)
- vorherige Reports am Codec: `2026-10-30-bba672b-codec-tds-paket.md`, `2026-10-30-0d3f90c-slice-backend-port-vertrag.md`

**Ausgeführt (nur Docker, Spike-Code nur im Scratchpad, nichts in das Repo gemountet oder geschrieben):**

- Reproduktion des Befunds zu `DecimalValues.bytes` in einer Kopie des Repo-Stands (`git archive`) unter `/tmp/claude-1000/-Development-tdswire/8562f454-84ba-456b-9970-945e69c035f4/scratchpad/repro`, mit `docker build --target test` und gefiltertem Testlauf.
- Mutationsprobe in einer zweiten Kopie unter `/tmp/claude-1000/-Development-tdswire/8562f454-84ba-456b-9970-945e69c035f4/scratchpad/mut`: vier Mutanten gemeinsam eingebaut, Tests des Pakets `protocol.result` gelaufen.
- Handvektoren nachgerechnet (Tage seit 0001-01-01, Ticks, Little-Endian-Bytes, 10^38 - 1).
- `make docs-check` (siehe Verdikt).

---

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| F-1 | MEDIUM | Reproduziert: `DecimalValues.bytes` ruft `number.setScale(type.scale)` vor der Precision-Prüfung auf. Mit `DecimalValue(BigDecimal("1E+1000000"))` für (5, 2) dauerte der Aufruf 350 ms, mit `1E+30000000` lief er nach 20 s noch, mit `1E+999999999` wirft er `ArithmeticException: BigInteger would overflow supported range` statt `ProtocolViolation`. Über die Abbildung ist der Pfad nicht erreichbar (sie weist vor jeder Skalierung ab), über einen direkt gebauten `TdsValue.DecimalValue` im `TokenEncoder` schon. Die Datei liegt nicht in diesem Diff; die KDoc der Abbildung begründet ihre Reihenfolge mit genau diesem Risiko. | [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilit%C3%A4t), [SPEC-014](../../spec/spezifikation.md#4-fehler-codes-und-logging-felder) | `src/main/kotlin/io/github/pt9912/tdswire/protocol/token/DecimalValues.kt` · `val unscaled = number.setScale(type.scale).unscaledValue()` | ja — Test mit direkt gebautem `DecimalValue` und Zeitgrenze, in `make test` | Skalierung vor Grenzprüfung bindet Rechenzeit |
| F-2 | MEDIUM | Der Test der Fehlertexte ist nicht mutationsfest: Ein Mutant, der in die Details Text, Zeit oder Spaltenname einsetzt (`TextMapping` bei TOO_LONG, `TemporalMapping` bei TOO_FINE, `ColumnMapping` beim Namen), blieb in der Probe unentdeckt (Tests des Pakets grün). `Fehlertext enthaelt keine Zeilenwerte` deckt nur die Pfade unpaares Surrogat, Decimal-Überlauf, Datum außerhalb des Bereichs und Typkonflikt; der Fuzz-Test prüft die Details gar nicht. | [SPEC-014](../../spec/spezifikation.md#4-fehler-codes-und-logging-felder), Slice §2 (Fehlerwert ohne Zeilenwerte) | `ValueMappingTest.kt` · `Fehlertext enthaelt keine Zeilenwerte` | ja — der Mutant ist die Probe | Negativtest deckt nicht alle Fehlerpfade |
| F-3 | MEDIUM | Die Zeitzonen-Unabhängigkeit ist nur durch Lesen des Codes belegt (kein `ZoneId`, `atZone` oder `TimeZone` in `src/main`, per `grep` geprüft). Die Tests laufen in der Container-Zone (UTC) und setzen keine andere; ein Mutant, der `LocalDateTime` über `ZoneId.systemDefault()` nach UTC und zurück führt, blieb unentdeckt. Der Test `Werte ohne Zone unveraendert, Werte mit Zone abgewiesen` vergleicht nur das Mapping-Objekt, nicht die Bytes, und ändert die Standardzone der JVM nicht, obwohl er eine in Berlin nicht existierende Ortszeit verwendet. | [SPEC-003](../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog) (Zeitzonenannahmen), Slice §6 (Nutzerentscheidung) | `ValueMappingTest.kt` · `Werte ohne Zone unveraendert, Werte mit Zone abgewiesen` | ja — Mutant unter UTC; Lauf mit abweichender Standardzone | Zonen-Unabhängigkeit ohne Zonenwechsel im Test |
| F-4 | MEDIUM | Zwei Spec-Aussagen sind nach dem Diff falsch: [SPEC-002](../../spec/spezifikation.md#spec-002--interner-query-vertrag) führt die Precision über 38 als offen und sagt, sie scheitere „erst beim Encodieren“; die Abbildung weist sie vor COLMETADATA ab (Nutzerentscheidung, Slice §6). Die KDoc von `SqlType` nennt „Widerspruch nicht aufgelöst“ und die JVM-Typen je `SqlType` offen, obwohl `ValueMapping` sie festlegt. Der Plan sah `spec/spezifikation.md` als „update, falls nötig“ vor; der Diff enthält keine Spec-Änderung. | AGENTS.md 3.8 (Aussage breiter, hier: gegenteilig zum Beleg), Slice §3 | `spec/spezifikation.md` · `scheitert erst` · und `src/main/kotlin/io/github/pt9912/tdswire/domain/SqlType.kt` · `der Widerspruch ist nicht aufgelöst` | nein — kein Gate prüft die Spec-Nachführung | Spec und KDoc nach Entscheidung nicht nachgeführt |
| F-5 | MEDIUM | Abschnittsnummern externer Standards ohne belegbare Quelle im Repo: die Tests nennen MS-TDS 2.2.5.4, 2.2.7.4, 2.2.7.17 und 2.2.5.5.1.8. Das Repo führt keine Kopie der Spezifikation; die Nummern sind aus dem Gedächtnis plausibel, aber nicht belegt. Die Bytewerte selbst sind nachgerechnet und stimmen. | AGENTS.md 3.8 | `ColumnMappingTest.kt` · `Handvektoren der TYPE_INFO nach MS-TDS 2.2.5.4 und 2.2.7.4` und `ValueMappingTest.kt` · `Handvektoren nach MS-TDS 2.2.7.17` | nein | Abschnittsnummer ohne Quelle |
| F-6 | LOW | Die Decimal-Lesart „nachlaufende Nullen sind kein Verlust“ (1,500 in Scale 2 wird angenommen, weil `stripTrailingZeros` vor dem Vergleich läuft) ist eine Auslegung der Nutzerentscheidung „Genauigkeit des Werts größer als die Scale: abweisen“. Wörtlich hat `BigDecimal("1.500")` mehr Nachkommastellen als Scale 2. Die KDoc von `DecimalMapping` nennt die Lesart als Regel, nicht als Festlegung; nur der Test kennzeichnet sie („Festlegung ohne Messung“). Der Fuzz-Orakel (`setScale(DOWN)` und Vergleich) übernimmt dieselbe Lesart. Konsistent ist sie zu Zeit: ein `LocalTime` auf dem 100-ns-Raster wird ebenfalls angenommen. | AGENTS.md 3.8, Slice §6 (Entscheidung zur Rundung) | `DecimalMapping.kt` · `nachlaufende Nullen sind kein Verlust` | nein | Auslegung einer Entscheidung nicht als Festlegung gekennzeichnet |
| F-7 | LOW | Zwei Vektoren tragen das Etikett „Handvektor“, obwohl ihre Herkunft der Encoder ist: `23:59:59,9999999 ist FF BF 69 2A C9 (Vektor aus den Encoder-Tests)`. Nachgerechnet stimmt er (863 999 999 999 Ticks); die Herkunftsstufe ist trotzdem Selbstkonsistenz mit dem Codec, bis eine unabhängige Quelle genannt ist. Gleiches gilt für den Test `Mindestkatalog wird in fester und N-Form abgebildet`, dessen erwartete Bytes der TYPE_INFO ohne Quelle als „Handvektor“ stehen; die Closure-Notiz soll sie nach dem Slice-Plan als *Handvektor* oder *Selbstkonsistenz* trennen. | AGENTS.md 3.8, Slice §2 (Nachweis-Pflicht) | `ValueMappingTest.kt` · `Vektor aus den Encoder-Tests` | nein | Herkunftsstufe des Vektors zu hoch etikettiert |
| F-8 | INFO | `ValueMapping.row` bildet nach dem ersten Fehler die restlichen Spalten weiter ab und liefert dann den ersten Fehler. Das ist atomar, aber nicht kurzschließend; bei breiten Zeilen mit früh ungültigem Wert verbraucht jede Zeile die volle Abbildungszeit. Zuständig: Implementer, Entscheidung ohne Auswirkung auf Korrektheit. | Maintainability | `ValueMapping.kt` · `columns.indices.map { index -> at(index, value(columns[index], values[index])) }` | nein | Fehlerpfad ohne Kurzschluss |
| F-9 | INFO | Offene Nutzerfrage, kein Defekt: Die Lesart zu F-6 sollte in der Closure-Notiz als Festlegung stehen und vom Nutzer bestätigt werden. Zuständig: Nutzer (Entscheidung), Verifier (Zuordnung in der Closure-Notiz). | Slice §6 | `slice-backend-typabbildung.md` · `Wird bei der Closure gefüllt.` | nein | Offene Lesart zur Nutzerentscheidung |

## Prüfung der geforderten Schwerpunkte

- **Herkunftsstufen:** `ColumnMapping`, `ValueMapping`, `DecimalMapping` (Teil), `TemporalMapping` kennzeichnen Kollation, Scale 7, Textlänge 4000, DECIMALN statt NUMERICN, leere und doppelte Namen und die JVM-Klassen je Spalte als „Festlegung ohne Messung“; nichts davon steht als gemessen. Ausnahmen siehe F-5, F-6, F-7.
- **Mutationsfestigkeit:** Grenzwerte der Bereichsprüfungen werden von den Tests getroffen (999,99 / 1000,00, 10^38 - 1 / 10^38, 4000 / 4001 Einheiten, 255 / 256 Namenszeichen, Jahr 0, 1, 9999, 10000, 50 ns / 100 ns, Zero mit extremem Exponent). Gemeinsam eingebaute Mutanten außerhalb dieser Grenzen (Detail-Leaks, Zonenumrechnung) überlebten: F-2, F-3.
- **`DecimalValues.bytes`:** reproduziert, siehe F-1; mit `1E+1000` und `1E+1000000` kommt korrekt `ProtocolViolation` zurück, der Aufwand wächst mit dem Exponenten.
- **Decimal-Rundungslesart:** siehe F-6, F-9; die Implementierung ist in sich schlüssig (Abbildung, Encoder und Fuzz-Orakel stimmen überein), die Lesart ist nicht vom Nutzer bestätigt.
- **Zeitzonen-Unabhängigkeit:** im Code gegeben (kein Zonenbezug in `src/main`), im Test nicht erzwungen: F-3.
- **Fehlerdetails ohne Werte:** im Code gegeben (Details nennen Klassen, Längen, Positionen; `mismatch` nennt nur `simpleName`), im Test nur teilweise abgesichert: F-2.
- **Hard Rules:** 3.1 (kein Host-Build; Prüfläufe nur in Docker), 3.2 (kein `@Suppress` im Diff), 3.7 (Kommentare tragen Zusage, Abgrenzung oder Festlegung, keine Chronik), Schichtregel (`protocol/result` importiert `domain` und `protocol/*`, nichts aus `transport`, `simulation`, `runtime`): ohne Befund.

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/result/` (Mapping-Logik, Grenzen, Atomarität, Schichten) | geprüft, ohne Befund außer F-6, F-8 |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/result/` (Handvektoren nachgerechnet, `@spec`-Tags vorhanden, Fuzz mit festem Startwert, Orakel unabhängig von der Abbildung) | geprüft, ohne Befund außer F-2, F-3, F-5, F-7 |
| `docs/plan/planning/done/slice-backend-typabbildung.md` (Diff) | geprüft, ohne Befund |
| `spec/spezifikation.md`, `spec/architecture.md`, `docs/plan/adr/` | nicht im Diff; F-4 zur fehlenden Nachführung, [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md) nicht berührt, ohne Befund |
| `config/`, `build.gradle.kts`, `Dockerfile` | nicht im Diff, keine Lockerung von Gates |

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 5 |
| LOW | 2 |
| INFO | 2 |

**Finding-Klassen dieses Laufs:** Skalierung vor Grenzprüfung bindet Rechenzeit · Negativtest deckt nicht alle Fehlerpfade · Zonen-Unabhängigkeit ohne Zonenwechsel im Test · Spec und KDoc nach Entscheidung nicht nachgeführt · Abschnittsnummer ohne Quelle · Auslegung einer Entscheidung nicht als Festlegung gekennzeichnet · Herkunftsstufe des Vektors zu hoch etikettiert · Fehlerpfad ohne Kurzschluss · Offene Lesart zur Nutzerentscheidung

## Verdikt

**Merge-blockierend:** nein (Merge liegt vor; die fünf MEDIUM-Befunde sind vor der Closure des Slice zu beantworten). Kein HIGH: Kein Befund zeigt ein Versagen im kritischen Pfad; F-1 ist über die Abbildung nicht erreichbar.

**Übergabe:** Findings gehen an den Implementer; die Finding-Klassen gehen zusätzlich in die Slice-Closure §7. Kein Widerspruch einer Rolle liegt vor, der Konflikt-Pfad über den Architect ist nicht ausgelöst. Dieser Report ersetzt keine Verifikation gegen die DoD.
