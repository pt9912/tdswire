# Verifikationsbericht: slice-backend-typabbildung am Stand 55fc860 — 2026-10-30

**Rolle:** Verifier (Frage: „Bauen wir es richtig?“ gegen Plan, DoD und Entscheidungen). Der Bericht repariert nichts und ändert keine Datei außer sich selbst; es wurde nichts committet oder gepusht.

**Gegenstand:** [slice-backend-typabbildung](../plan/planning/done/slice-backend-typabbildung.md) (Welle welle-backend-streaming), Merge `55fc860` („Merge slice-backend-typabbildung Umsetzung Typabbildung“) mit den Commits `3ee2750` (Umsetzung) und `ceef335` (Slice-Plan nachgeführt). Bezug: [TDS-FA-05](../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen), [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität), [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md), [SPEC-003](../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog), [SPEC-014](../../spec/spezifikation.md#4-fehler-codes-und-logging-felder), [SPEC-031](../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität), [SPEC-002](../../spec/spezifikation.md#spec-002--interner-query-vertrag). Review-Eingang: [Review-Report](2026-10-30-55fc860-slice-backend-typabbildung.md) (Findings F-1 bis F-9, 0 HIGH).

**Stand-Hinweis:** Der Lauf begann auf `2b8f56b` und endete auf `d9bab24` (inzwischen sind `slice-qa-reproduzierbarkeit` und `slice-transport-listener-lebenszyklus` gemergt). Seit `55fc860` änderte sich unter `src/main/kotlin/.../protocol/result/`, `src/test/kotlin/.../protocol/result/`, `spec/`, `contracts/`, `docs/plan/adr/` und `config/` nichts (`git diff 55fc860 HEAD` über diese Pfade ist leer). Alle Aussagen zur Abbildung gelten daher für den Code von `55fc860` und für `main`. Mutationen und Probeläufe liefen in Kopien (`git archive HEAD`) im Scratchpad unter `/tmp/claude-1000/-Development-tdswire/8562f454-84ba-456b-9970-945e69c035f4/scratchpad/` (`base/`, `m1/` bis `m5/`, `cur/`); nichts wurde gemountet, nur `docker build` und `make`. Der Arbeitsbaum des Repos blieb unverändert (`git status`: nur die fremden, unverfolgten `.claude/worktrees/`).

**Behauptung ≠ Bestätigung:** Der Slice trägt keine DoD-Häkchen und keine Closure-Notiz (§7: „Wird bei der Closure gefüllt“); es gibt keinen Implementer-Bericht mit Sensor-Belegen im Repo außer dem Review-Report. Alles unten ist selbst gefahren oder aus dem Code gelesen; wo nur gelesen, steht es dabei.

## 1. Selbst gefahrene Läufe

| Lauf | Ergebnis |
|---|---|
| frischer Testlauf auf `2b8f56b` (`docker build --target test --no-cache-filter test`, Kopie `base/`) | Exit 0, **307 PASSED, 0 FAILED** (einziges „SKIPPED“ ist die Gradle-Aufgabe `checkKotlinGradlePluginConfigurationErrors`), `BUILD SUCCESSFUL in 1m 7s` |
| Zuwachs durch den Slice | 27 `@Test` in `protocol/result`: `ColumnMappingTest` 8, `MappingFuzzTest` 2, `ValueMappingTest` 17; 307 − 27 = 280 (Stand vor dem Slice laut Verifikation `slice-handshake-login` (Stand bda6cd6, archiviert mit der Welle)) |
| frischer Testlauf auf `d9bab24` (Kopie `cur/`) | Exit 0, **331 PASSED, 0 FAILED** (die Differenz zu 307 sind die Tests des inzwischen gemergten Listener-Slice) |
| `docker build --target detekt --no-cache-filter detekt` auf `d9bab24` | Exit 0: `suppression-check: OK - keine Unterdrueckung unter src`, `detekt`, `detektMain`, `detektTest`, `BUILD SUCCESSFUL in 3m 6s` |
| `make gates` auf `d9bab24` | Exit 0 in 11 s (überwiegend Cache): `baseline-verify: v6.18.1 OK — 54 Dateien`, `d-check: 264 Datei(en) geprüft, 0 Befund(e)` |
| Abhängigkeiten, Gates | `git diff 04539b8 55fc860 --stat` nennt nur die elf Dateien (`protocol/result` 6 + 4 Testdateien, Slice-Plan); `gradle.lockfile`, `gradle/verification-metadata.xml`, `build.gradle.kts`, `config/`, `Dockerfile` unberührt, keine Gate-Lockerung ([AGENTS.md](../../AGENTS.md) §3.6), keine ADR überschrieben (§3.5) |

**Cache-Warnung:** `make test` allein liefert bei unverändertem `src/` einen Cache-Treffer (`BEO-TDS/test-stage-cache-verdeckt-wiederholung`); die Testzahlen oben stammen deshalb aus `--no-cache-filter test`.

**Läufe unter Last:** In der Mutationskopie `m4` schlug zusätzlich `ListenerLifecycleTest > login timeout closes a connection stuck in the handshake in both paths` fehl, während ein Probelauf parallel lief. Auf `d9bab24` ohne Parallellast ist er grün. Das betrifft den Listener-Slice (Wanduhr-Test), nicht diesen Slice; ich nenne es als Beobachtung, nicht als Befund.

## 2. DoD Punkt für Punkt

### Liefer-Punkt 1 — Metadatenabbildung ([TDS-FA-05.a](../../spec/spezifikation.md#tds-fa-05a--ergebniskodierung) Schritt 1, 2; [SPEC-003](../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog))

| Aussage | Urteil | Beleg |
|---|---|---|
| `SqlType` → `TdsType` für den Mindestkatalog | **bestätigt** | `ColumnMapping.tdsType` ist ein erschöpfendes `when` über alle acht `SqlType`; Test „Mindestkatalog wird in fester und N-Form abgebildet“ pinnt 12 Fälle als Hex. M11 (Int32 nullbar als IntN 8) und M13 (Bool nullbar als BIT) rot (Abschnitt 4) |
| nullable → N-Form | **bestätigt** | Hex-Vektoren `26 04`, `26 08`, `68 01` gegen `38`, `7F`, `32`; Date, Time, DateTime2 und Decimal sind ohnehin N-Typen; Test „Spaltendefinition traegt TdsType, Name und Nullbarkeit“ |
| Decimal Precision 1 bis 38 | **bestätigt** | Test „Decimal bis Precision 38 ist abbildbar, darueber TDS_UNSUPPORTED_TYPE“ (1, 38 ok; 39 Fehler); M03 (`>=`) rot. Die Scale-Grenze `0 <= scale <= precision` hält das Modell (`SqlType.Decimal.init`), nicht die Abbildung |
| Unicode-Text bis 4000 UTF-16-Einheiten, Kollation als Festlegung | **bestätigt** | Konstante `TEXT_CHARS = TypeInfoWriter.MAX_NVARCHAR_CHARS`, Kollation `LATIN1_GENERAL_CI_AS`; KDoc „Festlegungen ohne Messung“; Vektor `E7 40 1F 09 04 D0 00 34` |
| Scale 0 bis 7 für Zeit und Datum mit Zeit | **bestätigt mit Abweichung vom Wortlaut** | Die Abbildung setzt Scale 7 fest (`TIME_SCALE = TypeInfoWriter.MAX_SCALE`), weil `SqlType.Time` und `DateTime` keinen Scale tragen; der Bereich 0 bis 7 ist nur im Codec geprüft (`TypeInfoWriter.writeScaled`: Scale außerhalb 0..7 ist `INVALID_TYPE`). Der Plan lässt in §6 (b) beide Wege zu („setzt beide fest … oder verlangt ein erweitertes Modell“), die DoD-Zeile nennt aber „Scale 0 bis 7“. Siehe V-2 |
| komplette Spaltenliste vor jeder Ausgabe validiert ([SPEC-031](../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität), erster Satz) | **bestätigt** | `ColumnMapping.map` liefert `Ok(Liste)` oder den ersten `Failed`, nie eine Teilliste; Test „ein Fehler in der letzten Spalte verwirft die ganze Liste“; M06 (Teilliste) rot; Fuzz „Zufaellige Spaltenlisten werden ganz angenommen oder ganz abgewiesen“ (Precision bis 60) |
| leere Spaltenliste gültig | **bestätigt** | Test „leere Spaltenliste ist gueltig“ (`81 00 00`) |
| leeres Resultset mit Metadaten bleibt gültig | **bestätigt, nur über die Metadaten** | Die Abbildung kennt keine Zeilenzahl; Metadaten ohne Zeilen sind jede Liste, die `map` annimmt. Ein Test, der Metadaten mit null Zeilen nennt, fehlt; das Zusammensetzen gehört dem Sequenzierer (`slice-backend-sequenzierer`, [Slice §1](../plan/planning/done/slice-backend-typabbildung.md)) |
| Spaltennamen (Festlegung): leer und doppelt gültig, über 255 Zeichen und unpaares Surrogat abgewiesen | **bestätigt** | Test „Spaltennamen werden nach Laenge und Unicode geprueft“ (255 ok, 256 Fehler); M14 (Grenze 256) und der Mutant „`isWellFormed` nimmt ein einzelnes hohes Surrogat am Ende an“ rot |
| Grenze 65534 Spalten | **bestätigt** | Test „Spaltenzahl endet bei 65534“ mit Encoder-Gegenprobe |

### Liefer-Punkt 2 — Wertabbildung ([TDS-FA-05.a](../../spec/spezifikation.md#tds-fa-05a--ergebniskodierung) Schritt 3, 5; [SPEC-003](../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog))

| Aussage | Urteil | Beleg |
|---|---|---|
| NULL, Min/Max der Integer | **bestätigt** | Tests „Integer, Min und Max …“ und „NULL und Bit in nullbaren Spalten“ als Hex (`D1 00 00 00 80` usw.); M11 und M05 rot |
| leerer String ist nicht NULL | **bestätigt** | Test „leerer String ist nicht NULL …“ (`D1 00 00` gegen `D1 FF FF`); Mutant „leerer String wird `NullValue`“ (M10) rot |
| maximale Textlänge, Unicode mit Surrogatpaaren | **bestätigt** | Test „maximale Textlaenge zaehlt UTF-16-Einheiten“ (4000 ok, 4001 Fehler, 2000 Paare ok, 3999 + Paar Fehler); M01 (`>=`) rot; Paar `D83D DE00` → `3D D8 00 DE` |
| Decimal-Skalierung und -Überlauf | **bestätigt** | Tests „Decimal wird auf den Scale der Spalte skaliert“ und „Decimal-Ueberlauf und Stellenverlust …“ (999,99 / 1000,00; 10^38 − 1 als 16 Byte); M04 (rundet statt abzuweisen) und D1 (`>=` bei der Stellenzahl) rot. Die Bytes des Vektors für 10^38 − 1 habe ich mit Python nachgerechnet (`FF FF FF FF 3F 22 8A 09 7A C4 86 5A A8 4C 3B 4B`) |
| Datum und Zeit ohne Zeitzone (Festlegung, [Slice §6](../plan/planning/done/slice-backend-typabbildung.md)) | **bestätigt für den Code, im Test nur halb** | Kein Zonenbezug in `TemporalMapping`; Typen mit Zone werden als `TYPE_MISMATCH` abgewiesen; Vektoren nachgerechnet: 2000-01-01 = 730119 = `07 24 0B`, 9999-12-31 = 3652058 = `DA B9 37`, 2024-01-01 = 738885 = `45 46 0B`, 12:00 = 432·10^9 Ticks = `E0 34 95 64 00`, 23:59:59,9999999 = `FF BF 69 2A C9`. Der Test erzwingt die Zonen-Unabhängigkeit nicht: der Mutant, der `LocalDateTime` über `ZoneId.systemDefault()` nach UTC und zurück führt, überlebt (Z1, Abschnitt 4) |
| Rundung: Zeit feiner als Scale 7 und Decimal mit zu vielen Stellen werden abgewiesen (Nutzerentscheidung) | **bestätigt** | Test „feinere Zeit als Scale 7 wird abgewiesen“ (50 ns, 1 ns, 150 ns abgewiesen, 100 ns ok); M08 rot. Die Lesart „nachlaufende Nullen sind kein Verlust“ ist Auslegung (Review F-6, F-9) |
| Wert, der nicht zur Spaltenart passt, ist ein Fehlerwert vor Ausgabe der Zeile | **bestätigt** | `ValueMapping.row` bildet alle Werte ab und liefert erst dann `Ok`; Test „Zeile ist atomar“ (unpaares Surrogat in der letzten Spalte, falsche Wertzahl); M07 (Teilzeile) und M15 (Wertzahl nur einseitig geprüft) rot; Test „Wert der falschen Klasse ist ein Typkonflikt“ |
| NULL in nicht nullbarer Spalte (Festlegung) | **bestätigt** | Test „NULL in einer nicht nullbaren Spalte ist ein Fehler“; M05 rot |

### Liefer-Punkt 3 — Fehlerverhalten ([TDS-FA-05](../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen) Negative, [SPEC-014](../../spec/spezifikation.md#4-fehler-codes-und-logging-felder), [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität))

| Aussage | Urteil | Beleg |
|---|---|---|
| nicht abbildbarer Typ, ungültiger Unicode, Überlauf, Typkonflikt enden in einem Ergebniswert mit Kategorie `TDS_UNSUPPORTED_TYPE` | **bestätigt** | Alle Fehlerwerte laufen über `failed(...)` mit einem `ProtocolFailure` aus `TOO_LONG`, `VALUE_OUT_OF_RANGE`, `TYPE_MISMATCH`, `INVALID_TYPE`; deren Kategorie ist laut `ProtocolError.kt` für alle vier `TDS_UNSUPPORTED_TYPE`. Der Helfer `MappingSupport.error` prüft die Kategorie bei jedem Fehlerfall; M02 (Surrogat-Prüfung des Werts entfernt) rot |
| Zufalls- und Grenzeingaben mit festem Startwert: stets `Ok` oder Fehlerwert, nie eine Ausnahme | **bestätigt** | `MappingFuzzTest`: Startwert `20_261_030`, 30 000 Zeilen mit allen Spaltenarten, Grenzwerten, `Any()`, unpaaren Surrogaten, Decimal über Precision, Zeit feiner als 100 ns; Orakel ohne Aufruf der Abbildung (CharsetEncoder, `setScale` mit `RoundingMode.DOWN`); angenommene Zeilen laufen durch Encoder und Test-Decoder zurück; zweiter Test mit zufälligen Spaltenlisten (Precision bis 60). Der Fuzz-Test wurde in den Läufen mit M02, M03, M04, M11, M13 und T1 zusätzlich rot (Orakel stimmt dann nicht mehr überein) |
| Textdarstellung des Fehlerwerts enthält keine Zeilenwerte | **im Code bestätigt, im Test nicht erzwungen** | Gelesen: `detail` nennt Klassen, Längen, Positionen; `mismatch` nennt nur `simpleName`. Der Test „Fehlertext enthaelt keine Zeilenwerte“ deckt vier Pfade; drei Mutanten, die Text, Zeit oder Spaltenname in `detail` einsetzen (L1 bis L3, Abschnitt 4), überleben alle. Das bestätigt Review F-2 |
| Verhalten bei extremem Exponent | **bestätigt für den Exponenten, mit Rest für große Mantissen** | Test „Decimal mit extremem Exponent bindet keinen Speicher“ (`1E+999999999` abgewiesen, `0E-999999999` ist 0,00); siehe V-4 |

### Nachweis-Pflichten

| Pflicht | Urteil | Beleg |
|---|---|---|
| `@spec SPEC-<NNN>` an jedem neuen Test; Tests laufen in `make test` und sind grün | **bestätigt** | Skript über die drei Testdateien: 27 `@Test`, bei jedem steht im KDoc direkt darüber mindestens ein `@spec`-Tag (8, 2, 17); 307 und 331 Tests laufen grün |
| Jede Aussage an einem Test oder als Festlegung gekennzeichnet; Closure-Notiz trennt *gemessen*, *Handvektor*, *Selbstkonsistenz*, *Festlegung* | **Code und Tests: bestätigt mit Einschränkungen; Closure-Notiz: nicht ausgeführt** | Im KDoc der sechs Hauptdateien steht nichts als *gemessen*; die Festlegungen sind benannt (Kollation, Scale 7, Länge 4000, DECIMALN, Namen, JVM-Klassen, Null-Regel). Einschränkungen: die Abschnittsnummern von MS-TDS in den Tests haben keine Quelle im Repo (Review F-5); zwei Vektoren tragen das Etikett *Handvektor* bei Encoder-Herkunft (Review F-7). Die Vektorwerte selbst habe ich unabhängig mit Python nachgerechnet (Abschnitt 2, Liefer-Punkt 2). Die Zuordnungstabelle gehört in die Closure-Notiz, die fehlt (Abschnitt 6) |
| `make gates` grün | **bestätigt** | Abschnitt 1 (auf `d9bab24`; der Code der Abbildung ist seit `55fc860` unverändert) |
| Review durchgeführt, Report unter `docs/reviews/` | **bestätigt** | [Review-Report](2026-10-30-55fc860-slice-backend-typabbildung.md) (0 HIGH, 5 MEDIUM, 2 LOW, 2 INFO); F-1 bis F-3 habe ich unabhängig nachgestellt (Abschnitt 4 und V-4) |
| Doku-Update, falls ein öffentlicher Vertrag berührt ist | **nicht bestätigt** | Der Plan sah `spec/spezifikation.md` als „update, falls nötig“ vor; der Diff enthält keine Spec-, Vertrags- oder KDoc-Nachführung zu den Entscheidungen vom 2026-10-30. Siehe V-1 |
| Closure-Notiz, Lerneintrag, Beobachtungs-Register, Risiko-Ausgänge, drei Paarungen | **nicht bestätigt (noch nicht ausgeführt)** | Alle DoD-Häkchen offen, §7 „Wird bei der Closure gefüllt“, in §6 stehen zwei Risiken mit Ausgang „entschieden“ bzw. „weiter offen“, drei weitere „offen bis zur Closure“. Das ist der Zustand vor der Closure, kein Mangel des Codes |

## 3. [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md)-Konformität, Schichten, Out-of-Scope, Plan-vs-Code

**[ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md) (Accepted):**

| Zusage der ADR | Urteil | Beleg |
|---|---|---|
| Ergebnismodell ist ein `Flow<QueryEvent>`; das Backend kennt keine Tokens | **bestätigt, nicht berührt** | `protocol/result` importiert nichts aus `port`, `transport`, `simulation`, `runtime`; es schreibt keine Tokens in einen Strom (Test-Hilfe nutzt `TokenEncoder` nur zum Gegenlesen) |
| Zeilendarstellung ist offen; Festlegungen müssen als solche stehen | **bestätigt** | KDoc von `ValueMapping`: „Festlegung ohne Messung (die Zeilendarstellung ist in [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md) offen)“; `Row.values` bleibt `List<Any?>` ([ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md) Abschnitt „Offen“) |
| Typabbildbarkeit wird vor COLMETADATA validiert ([SPEC-031](../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität), von der ADR geschärft) | **bestätigt** | `ColumnMapping.map` als Ganzes; Test und Fuzz der Spaltenliste |
| Fehler im Strom: Zuständigkeit Mapping-Slice nur für die Abbildung, nicht für Sequenzierung oder ERROR/DONE | **bestätigt** | Kein Token- oder Statuscode im Paket; Fehlernummer, Severity, State ausdrücklich nicht im Slice ([Slice §1](../plan/planning/done/slice-backend-typabbildung.md)) |
| ADR bleibt unverändert (Accepted) | **bestätigt** | `git diff 04539b8 HEAD -- docs/plan/adr` ohne Änderung an [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md) |
| keine Aussage der Abbildung beruft sich auf die Messung des Spike als Beleg | **bestätigt** | Suche im KDoc und in den Tests nach „gemessen“ und „Messung“: nur „Festlegung ohne Messung“ und die Nutzerentscheidungen; die Messung der ADR gilt dem Spike ([Slice §2](../plan/planning/done/slice-backend-typabbildung.md)) |

**Schichten und Hard Rules:** Importliste aller sechs Hauptdateien: `domain` (`ResultColumn`, `SqlType`), `protocol.token`, `protocol.wire`, `java.math`, `java.time`; keine Fremdbibliothek, nichts aus `transport`, `simulation`, `runtime`. `make detekt`-Stufe grün (Importregeln der Schichten, `ForbiddenMethodCall`, Suppression-Verbot [AGENTS.md](../../AGENTS.md) §3.2). Die Hard Rule §3.1 (Docker-only) habe ich eingehalten.

**Plan-vs-Code:**

| Plan ([Slice §3](../plan/planning/done/slice-backend-typabbildung.md)) | Code | Urteil |
|---|---|---|
| `protocol/result/` neu: `ColumnMapping`, `ValueMapping` | sechs Dateien: die beiden genannten (öffentlich) und `DecimalMapping`, `TemporalMapping`, `TextMapping`, `MappingFailures` (intern) | **bestätigt, Zuschnitt feiner als der Plan** |
| Tests: Handvektoren je Typ, Roundtrip mit `TokenDecoder`, Fuzz mit festem Startwert, `@spec` je Test | `ColumnMappingTest`, `ValueMappingTest`, `MappingFuzzTest`, `MappingSupport`; Roundtrip im Fuzz-Test (`TokenDecoder`), Hex-Vektoren über den `TokenEncoder` | **bestätigt** |
| `spec/spezifikation.md`: update, falls nötig | nicht geändert | **offen, nötig** (V-1) |
| Keine Imports aus `transport`, `simulation`, `runtime` | eingehalten | **bestätigt** |
| Reihenfolge: Metadaten, Werte, Fehlerverhalten | Verhalten in dieser Reihenfolge testbar; Reihenfolge der Commits nicht geprüft | ohne Bedeutung |
| Ausdrücklich nicht im Slice: Sequenzierer, Typen außerhalb des Mindestkatalogs, Modell `SqlType`, Fehlernummer, Messung am Client | keines davon im Diff | **bestätigt** |

## 4. Selbst ausgeführte Mutationen

Jeder Mutant lief in einer frischen Kopie von `main` (`git archive HEAD`, nur `src/main/kotlin/.../protocol/result/` verändert), mit `docker build --target test --no-cache-filter test`. Keine Mutation scheiterte am Kompilieren. Weil mehrere Mutanten pro Lauf gemeinsam eingebaut waren, habe ich sie so aufgeteilt (`m1` gemeinsam; `m3` und `m4` je drei Mutanten, die verschiedene Tests treffen), dass jeder Mutant einem roten Test eindeutig zuzuordnen ist. „Rot“ heißt: Tests liefen und mindestens einer schlug fehl. Von 21 Mutationen waren **17 rot und 4 überlebten** (die vier sind die gezielt gesetzten Detail-Lecks und der Zonenmutant, die der Review in F-2 und F-3 vorhergesagt hat).

| Nr. | Mutation | Ergebnis | rote Tests (Auswahl) |
|---|---|---|---|
| M01 | Textgrenze `>=` statt `>` | rot | „maximale Textlaenge zaehlt UTF-16-Einheiten“ |
| M02 | Surrogat-Prüfung beim Textwert entfernt | rot | „unpaare Surrogate sind ein Fehler“, Fuzz |
| M03 | Precision-Grenze `>=` 38 | rot | „Decimal bis Precision 38 …“, Fuzz Spaltenlisten |
| M04 | Decimal rundet (`HALF_UP`) statt abzuweisen | rot | „Decimal-Ueberlauf und Stellenverlust …“, „… extremem Exponent …“ |
| M05 | NULL auch in nicht nullbarer Spalte angenommen | rot | „NULL in einer nicht nullbaren Spalte …“ |
| M06 | `ColumnMapping.map` liefert Teilliste statt Fehler | rot | „ein Fehler in der letzten Spalte …“ |
| M07 | `ValueMapping.row` liefert Teilzeile statt Fehler | rot | „Zeile ist atomar“ |
| M08 | Zeit feiner als 100 ns angenommen | rot | „feinere Zeit als Scale 7 …“ |
| M09 | Jahresgrenze 10000 statt 9999 | rot | „Datum von 0001 bis 9999“ |
| M10 | leerer String wird `NullValue` | rot | „leerer String ist nicht NULL …“ |
| M11 | Int32 nullbar als IntN der Größe 8 | rot | „Mindestkatalog …“, „Spaltendefinition …“, „Integer, Min und Max …“ |
| M12 | Jahresuntergrenze 0 statt 1 | rot | „Datum von 0001 bis 9999“ |
| M13 | Bool nullbar als festes BIT | rot | „Mindestkatalog …“, „NULL und Bit in nullbaren Spalten“ |
| M14 | Namensgrenze 256 statt 255 | rot | „Spaltennamen werden nach Laenge und Unicode geprueft“ |
| M15 | Wertzahl nur einseitig geprüft (`>` statt `!=`) | rot | „Zeile ist atomar“ |
| D1 | Decimal-Stellenzahl `>=` statt `>` | rot | „Decimal-Ueberlauf …“, „Decimal wird auf den Scale … skaliert“ |
| T1 | hohes Surrogat am Textende angenommen | rot | „unpaare Surrogate sind ein Fehler“, „Spaltennamen …“ |
| L1 | Text im Detail bei `TOO_LONG` | **überlebt** | 0 rote Tests (307 PASSED) |
| L2 | Uhrzeit im Detail bei `TOO_FINE` | **überlebt** | 0 rote Tests |
| L3 | Spaltenname im Detail bei zu langem Namen | **überlebt** | 0 rote Tests |
| Z1 | `LocalDateTime` über `ZoneId.systemDefault()` nach UTC und zurück | **überlebt** | 0 rote Tests (Container läuft in UTC; kein Test wechselt die Standardzone) |

**Weitere Probeläufe (Wegwerf-Test `ProbeTest` in `m5`, nicht im Repo):**

| Probe | Ergebnis |
|---|---|
| `ValueMapping.row` mit `BigDecimal(10^n)` in Spalte (5, 2) | n = 20 000: 203 ms; 50 000: 858 ms; 100 000: **5096 ms**; Ergebnis stets `Failed` (V-4) |
| `TokenEncoder.write` mit direkt gebautem `DecimalValue("1E+1000000")` in (5, 2) | 336 ms, `Failed`; `1E+3000000`: **1330 ms**, `Failed` (bestätigt Review F-1: Skalierung vor Grenzprüfung, Aufwand wächst mit dem Exponenten) |

## 5. Test-Stabilität

Drei unveränderte Gesamtläufe (`base` auf `2b8f56b`, `cur` auf `d9bab24`, `m2` mit den überlebenden Mutanten) waren vollständig grün; in den übrigen Läufen schlugen von den 27 Tests des Pakets nur solche fehl, die ein eingebauter Mutant treffen sollte. Mehr als diese Läufe habe ich nicht wiederholt, die Aussage trägt also nicht weiter als drei Läufe. Die Tests haben keine Wanduhr-Schwelle; das Paket läuft in unter 3 Sekunden. **Flakiness im Paket: keine beobachtet.** Auffällig war allein der Wanduhr-Test des Listener-Slice unter Last (Abschnitt 1).

## 6. Offene Punkte vor der Closure (keine Code-Mängel)

* Closure-Notiz in §7 mit der Zuordnung je Aussage (*gemessen*: keine; *Handvektor*: Integer, Bit, NULL, Text, Decimal, Datum, Zeit — die in Abschnitt 2 nachgerechneten; *Selbstkonsistenz*: Roundtrip mit Test-Decoder, Fuzz-Orakel; *Festlegung*: Kollation, Scale 7, Länge 4000, DECIMALN, Namen, JVM-Klassen, NULL in nicht nullbarer Spalte, Lesart der nachlaufenden Nullen) und dem Steering-Loop-Lerneintrag.
* Ausgang für jedes Risiko aus [Slice §6](../plan/planning/done/slice-backend-typabbildung.md): „Modellgrenzen“ (a) und (c) sind entschieden bzw. festgelegt, (b) ist halb (Scale fest 7, Länge fest 4000); „Nullbarkeit gegen Wert“ ist als Festlegung getragen; „Doppelung mit `slice-codec-datentypen-erweiterung`“ ist entfallen (der Katalog wuchs nicht).
* Beobachtungs-Register: `BEO-TDS/aussagen-ueber-messung-hinaus` (weitere Fälle: F-5, F-7) und `BEO-TDS/messung-am-echten-sql-server-fehlt` fortschreiben; neue Beobachtung möglich: „Negativtest nennt nicht alle Fehlerpfade“ (F-2, hier bestätigt) — das Register zählt aus den Dateien, ich habe nichts angelegt.
* Nutzerbestätigung der Lesart von F-6 / F-9 (nachlaufende Nullen) vor der Closure.

## 7. Findings

**HIGH: keine.**

**MEDIUM**

* **V-1 — Spec, Vertrag und KDoc sind nach den Entscheidungen vom 2026-10-30 falsch bzw. veraltet; die DoD-Zeile „Doku-Update“ ist nicht erfüllt.** (a) [SPEC-002](../../spec/spezifikation.md#spec-002--interner-query-vertrag) führt die Precision über 38 als offen und sagt, sie scheitere „erst beim Encodieren“; die Abbildung weist sie vor COLMETADATA ab. (b) Dieselbe Stelle führt Längenlimit, Zeitgenauigkeit, Zeitzonenannahmen und JVM-Typen als offen, obwohl die Abbildung sie festlegt. (c) [`contracts/query-backend.md`](../../contracts/query-backend.md) Zeile 24 nennt dasselbe als „Offen“; dieser Vertrag steht nicht in Review F-4 (der nennt Spec und `SqlType.kt`). (d) KDoc von `SqlType` („der Widerspruch ist nicht aufgelöst“, „Offen ([ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md)): welche JVM-Typen …“). Das ist die „falsche Aussage nach Entscheidung“-Klasse, kein Gate sieht sie; der Plan nannte die Spec-Nachführung ausdrücklich. Überschneidung: Review **F-4**, F-9; (c) ist neu gegenüber F-4.
* **V-2 — DoD-Wortlaut „Scale 0 bis 7 für Zeit und Datum mit Zeit“ ist im Verhalten „fest 7“.** Die Absicht (Zeit und Datum mit Zeit abbilden, Scale im zulässigen Bereich) ist erfüllt, der Bereich 0 bis 7 wird in der Abbildung nie ausgeübt, weil das Modell keinen Scale trägt; der Plan ([Slice §6](../plan/planning/done/slice-backend-typabbildung.md) (b)) ließ die Festlegung zu. Die Closure-Notiz muss die DoD-Zeile an den Ist-Zustand binden („Scale 7 als Festlegung; 0 bis 6 nur im Codec“), sonst bleibt die DoD wörtlich nicht erfüllt. Keine Überschneidung mit dem Review.

**LOW**

* **V-3 — Closure-Pflichten offen:** siehe Abschnitt 6; der Slice darf erst nach V-1, V-2 und der Notiz nach `done/` (kein Befund gegen den Code).
* **V-4 — Die KDoc von `DecimalMapping` trägt weiter als der Beleg.** „Abgewiesen wird vor jeder Skalierung, damit ein Wert wie `1E+999999999` keinen Speicher bindet“ stimmt für große Exponenten, aber `stripTrailingZeros()` läuft vor jeder Prüfung und ist für große Mantissen mit vielen nachlaufenden Nullen quadratisch: `10^100000` braucht 5,1 s (gemessen, Abschnitt 4), `10^20000` 0,2 s. Das Ergebnis ist korrekt (`Failed`), nur die Rechenzeit ist unbegrenzt; ein Backend liefert solche Werte kaum, aber [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität) und [AGENTS.md](../../AGENTS.md) §3.8 verlangen eine Aussage nur so weit wie der Beleg. Abhilfe wäre eine Stellenprüfung (`precision()` gegen die Grenze) vor `stripTrailingZeros()`. Überschneidung: Review **F-1** betrifft dieselbe Klasse in `DecimalValues.kt` (Encoder, nicht Teil des Diffs); die Fix-Runde sollte beide Stellen zusammen prüfen.
* **V-5 — Der Test der Fehlertexte und die Zonen-Unabhängigkeit sind nicht mutationsfest.** Bestätigt durch L1 bis L3 und Z1 (Abschnitt 4): kein Test wird rot. Die DoD-Aussagen „Textdarstellung enthält keine Zeilenwerte“ und „ohne Zeitzonenumrechnung“ sind damit im Code wahr, im Test aber nicht erzwungen. Überschneidung: Review **F-2** und **F-3**.
* **V-6 — Beleg-Etikett und Quelle:** Abschnittsnummern von MS-TDS ohne Quelle im Repo (Review **F-5**), Vektoren mit Etikett *Handvektor* und Encoder-Herkunft (Review **F-7**). Die Zahlen habe ich unabhängig nachgerechnet und sie stimmen; die Herkunftsstufe ist trotzdem erst mit genannter Quelle *Handvektor*.

**INFO**

* Review **F-6, F-8, F-9** unverändert gültig: Lesart der nachlaufenden Nullen als Auslegung, kein Kurzschluss im Fehlerpfad von `ValueMapping.row`, offene Nutzerfrage.
* Ein Test, der Metadaten mit null Zeilen nennt, fehlt (Abschnitt 2); das Zusammensetzen gehört dem Sequenzierer.
* `ListenerLifecycleTest > login timeout …` schlug unter Parallellast einmal fehl (Abschnitt 1): Hinweis an den Listener-Slice.

## 8. Überschneidung mit der laufenden Fix-Runde (Review F-1 bis F-9)

Die parallele Fix-Runde läuft im Worktree `.claude/worktrees/agent-a742e3aa5832efc74` (Zweig `worktree-agent-a742e3aa5832efc74`). Beim Lauf stand er auf `2b8f56b`, ohne Commits und ohne Änderungen im Arbeitsbaum; ich habe seinen Inhalt deshalb nicht bewertet. Maßgeblich ist allein `main`.

| Review-Befund | Dieser Bericht | Überschneidung |
|---|---|---|
| F-1 (Skalierung vor Grenzprüfung, `DecimalValues`) | V-4 (gemessen: 336 ms bei `1E+1000000`, 1330 ms bei `1E+3000000`) | gleiche Klasse; **neu:** dieselbe Klasse in `DecimalMapping` über `stripTrailingZeros()` bei großen Mantissen |
| F-2 (Fehlertext-Test) | V-5, Mutanten L1 bis L3 | bestätigt |
| F-3 (Zone) | V-5, Mutant Z1 | bestätigt |
| F-4 (Spec/KDoc nicht nachgeführt) | V-1 | bestätigt; (c) `contracts/query-backend.md` kommt hinzu |
| F-5, F-7 (Quelle, Etikett) | V-6 | bestätigt; Zahlen nachgerechnet |
| F-6, F-9 (Lesart der nachlaufenden Nullen) | Abschnitt 6, INFO | Closure-Notiz muss die Festlegung tragen |
| F-8 (Kurzschluss) | INFO | unberührt |
| — | V-2 (Scale 0 bis 7 im Wortlaut) | **nur hier** |

Sobald die Fix-Runde auf `main` liegt, sind V-1, V-4, V-5 und V-6 gegen den neuen Stand zu wiederholen; die Mutationstabelle (Abschnitt 4) lässt sich mit `mutate.py` und `mutate2.py` aus dem Scratchpad wiederholen (Muster: je Mutant eine Ersetzung in einer Kopie).

## 9. Verdikt

Die drei Liefer-Punkte sind im Code und in den Tests **bestätigt**, mit den Einschränkungen V-2 (Scale im Wortlaut), V-4 (Rechenzeit bei großen Mantissen, KDoc breiter als der Beleg) und V-5 (zwei Zusagen im Code wahr, im Test nicht erzwungen). Nicht bestätigt: die DoD-Zeile „Doku-Update“ (V-1) und die Closure-Pflichten (V-3), weil sie erst nach diesem Bericht entstehen. [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md) wird eingehalten (keine Berührung der Entscheidung, Festlegungen als solche gekennzeichnet).

**Empfehlung an den Planner:** V-1 (Spec, Vertrag, KDoc) und V-2 (DoD-Zeile an den Ist-Zustand binden) vor der Closure; V-4 gemeinsam mit F-1 in der Fix-Runde; V-5 durch Tests mit Mutantenprobe (L1 bis L3, Z1 als Vorgabe); Closure-Notiz mit der Zuordnung aus Abschnitt 6 und der Nutzerbestätigung zu F-6. Kein Rückweg nach `next/` oder `open/` nötig: der Slice hat drei Liefer-Punkte und blockiert nicht.
