# Verifikationsbericht: slice-codec-datentypen-erweiterung — 2026-11-16

**Art:** Verifikation (gegen DoD §2, Plan §3, Spec und ADR; Frage „Bauen wir es richtig?“, nicht Review, nicht Validierung)

**Gegenstand:** Branch `slice-codec-datentypen-erweiterung`, HEAD `5ef12076`, `git diff b028ca04..5ef12076` (36 Dateien, +1456 −132); Vorgänger: [Review 246f3f8c](2026-11-16-246f3f8c-slice-codec-datentypen-erweiterung-review.md), [Re-Review f131a2c3](2026-11-16-f131a2c3-slice-codec-datentypen-erweiterung-re-review.md)

**Rolle:** Verifier · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-16

**Eingangs-Kontext:** Plan `slice-codec-datentypen-erweiterung` (§1 bis §8); [TDS-FA-05](../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen), [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität), [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md); `SPEC-003`, `SPEC-007`, `SPEC-013`, `SPEC-014`, `SPEC-021`, `SPEC-027`; Kompatibilitätsmatrix; `AGENTS.md` 3.2, 3.3, 3.7, 3.8; Nutzerentscheidungen 2026-11-16 laut Auftrag. Die DoD-Haken setzt der Planner; dieser Bericht setzt keinen.

---

## 1. DoD-Urteil je Zeile (§2)

| # | DoD-Zeile | Urteil | Beleg |
|---|---|---|---|
| 1 | PLP (SPEC-003): MAX-Typen in COLMETADATA, ROW, RETURNVALUE; Längenprüfung vor dem Puffern; auch als RPC-Parameter im Parser, SQL-Text und Deklaration als MAX; Test wechselt auf „gelesen“ | **erfüllt** | `PlpValues`, `PlpReader`, `RpcTypeInfoReader`; Tests `TokenPlpNbcRowTest`, `RpcPlpTest`; `RpcFailureTest > PLP wird gelesen, auch als SQL-Text und Deklaration` ersetzt die Ablehnung; `make test` im Lauf `clean-build` und in vier Mutationsläufen (1249 Tests, nur die mutierten rot). Einschränkung des Zuschnitts (Nutzerentscheidung 2): im Dispatch ist nur `nvarchar(max)` Wertparameter (`UnicodeText`); `varchar(max)`/`varbinary(max)` werden gelesen und dann mit `SPEC-013` abgelehnt. Die DoD-Zeile verlangt Lesen im Parser; das ist erfüllt |
| 2 | NBCROW (SPEC-003): Null-Bitmap-Zeile neben ROW; Encoder wählt nicht selbst | **erfüllt** (Encoder) | `NullBitmapRowToken`, `TokenWriters.writeNullBitmapRow`; `TokenPlpNbcRowTest`. NBCROW im Sequenzierer ist nach Entscheidung 3 ausdrücklich nicht Teil (A-12, Matrix Lücke 6: „Folge-Slice vorgesehen“, ohne Kennung); die DoD-Zeile verlangt ihn nicht |
| 3 | Weitere Typen: `text`, `ntext`, `image`, `sql_variant` nur, soweit SPEC-021 sie als gesendet führt; alles andere `TDS_UNSUPPORTED_TYPE` | **teilweise erfüllt, durch Nutzerentscheidung anders zu lesen** | `sql_variant` (Basistyp `int`, Encoder in COLMETADATA, ROW, NBCROW, RETURNVALUE) ist gebaut und an der Antwort eines echten SQL Servers (Mitschnitt `katalog-abfragen`, Instanzen 2022 und 2025) gemessen. Der Wortlaut „soweit `SPEC-021` sie als gesendet führt“ trägt für `sql_variant` **nicht**: das Clientprofil (SPEC-021) führt es nicht als gesendet (Spec: „Nicht gemessen: `text`, `ntext`, `image`, `sql_variant`“ für Anfragen); die Aufnahme folgt der Entscheidung 1, der Beleg ist die Server-Antwort (M-16), nicht SPEC-021. `text`, `ntext`, `image` sind **nicht gebaut** und bleiben `TDS_UNSUPPORTED_TYPE` (das ist nach dem Wortlaut „nur soweit gesendet“ konform: kein Beleg, kein Bau). Nicht erfüllt ist der Teil „alle vier Typen“, wenn man die Zeile als Lieferung aller vier liest. Andere `sql_variant`-Basistypen sind `TYPE_MISMATCH`/`TDS_UNSUPPORTED_TYPE` (Festlegung); NULL im ROW als `sql_variant` (Länge 0) ist Lesart, nicht gemessen; `sql_variant` als RPC-Parameter bleibt unsupported (Typkennung außerhalb der Typliste) |
| Nachweis 1 | Jeder Test trägt `@spec`; Tests in `make test`, grün | **erfüllt** | `make clean-build` Exit 0 (Tests und detekt, 1249 Tests); `make spec-abdeckung` Exit 0, `docs/plan/spec-abdeckung.md` danach unverändert (Arbeitsbaum sauber); Review F-3 (falsches `SPEC-028`) in `dc91934f` behoben |
| Nachweis 2 | Jede Aussage an einem Test oder als Festlegung gekennzeichnet; Closure-Notiz trennt gemessen/Handvektor/Selbstkonsistenz/Festlegung | **Code und Spec erfüllt; Closure-Notiz offen** | Spec, Matrix, KDoc tragen die Trennung (Matrix Zeilen 57, 77, 78, 126). Die Closure-Notiz §7 ist leer („Wird bei der Closure gefüllt“): Sache des Planners |
| Nachweis 3 | Zerstückelte, überlange und zufällige Eingaben enden in einem Ergebniswert (SPEC-027, [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität)) | **erfüllt** | siehe §3 |
| Nachweis 4 | `make gates` grün | **nicht von mir gefahren**; Auftrag sieht nur die Pflicht-Sensoren der Art „src/“ vor. `make clean-build` (Jar, Tests, detekt, `--no-cache`) und `make test` sind grün; der Implementer berichtet `make gates` grün, Beleg nicht eingesehen |
| Nachweis 5 | Review-Report liegt vor | **erfüllt** | Review `246f3f8c`, Re-Review `f131a2c3` (ein MEDIUM N-1 dort; nachgezogen in `5ef12076`, siehe §5) |
| Nachweis 6 | Doku-Update (SPEC-003-Zeilen) | **erfüllt** | Spec, Matrix, Änderungsliste (2026-11-16, zwei Zeilen), `referenzabgleich.md` |
| Nachweis 7 bis 10 | Closure-Notiz, Register, Risiko-Ausgänge, drei Paarungen | **offen, Closure-Handlung des Planners** | §7 leer; §6 trägt fünf Risiken mit „offen bis zur Closure“ und eines „entfallen“ |

## 2. Entscheidungs-Konformität

| Entscheidung | Urteil | Beleg |
|---|---|---|
| [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md) (Backend-Vertrag): Codec kennt `SqlType` nicht, Vertrag bleibt unverändert | konform | `SqlType` führt keinen MAX-Typ; der Dispatch bildet `nvarchar(max)` auf das vorhandene `UnicodeText` ab; `make flaeche` Exit 0 (keine neue öffentliche Deklaration; alle neuen Typen `internal`) |
| SPEC-003 (Typkatalog), SPEC-013 (Ablehnung) | konform | Spec-Zeile `SPEC-013` und Matrix Zeilen 126, 128, 151, 164 stimmen mit Code und Tests überein (`RpcBinderTest`, `RpcDispatchTest`) |
| SPEC-027 / [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität) | konform | siehe §3 |
| Nutzerentscheidung 4 (Beobachtungsaussage eingeschränkt) | konform, am Mitschnitt nachgemessen | `grep` über alle Anfrage-Nachrichten (`nachricht c 03`) der Ablage `referenztest-dotnet/Mitschnitt`: `e7ffff` 16-mal (8 je Instanz in `zusatz-unprepare-curcmd.messung`, 2022 und 2025), `a7ffff` und `a5ffff` 0-mal. Die Spec-Zahl „16-mal“ stimmt |
| Nutzerentscheidung 3 (NBCROW nicht im Sequenzierer) | konform | Sequenzierer unverändert (`RequestSequenceTest`-KDoc nur Text); A-12 und Matrix Lücke 6 sagen es |

## 3. TDS-QA-02: zerstückelte, überlange, zufällige Eingaben

| Eingabeart | Test | Beleg |
|---|---|---|
| zerstückelt (mehrere Stücke, unbekannte Gesamtlänge) | `RpcPlpTest > PLP-Werte aus einem Stueck, mehreren Stuecken, unbekannter Laenge, leer und NULL`; `Zufaellige Zerlegungen kommen unveraendert zurueck, …` | grün in `clean-build` |
| Verkürzung jeder Nachricht | `RpcPlpTest > Jede Verkuerzung einer Nachricht mit PLP endet in einem Ergebniswert` | grün |
| überlang | `RpcPlpTest > Ueberlange PLP-Laengen enden vor dem Puffern in einem Ergebniswert`; `PLP-Koerper gegen Gesamtlaenge und Nachrichtenende`; Encoder: `TokenPlpNbcRowTest > Die Laengenpruefung steht vor dem Puffern` | grün; `PlpReader` prüft Gesamtlänge gegen `Int.MAX_VALUE` und Rest vor dem Anlegen eines Puffers (Code gelesen); Summe gegen Gesamtlänge erst am Terminator, durch die Nachricht (SPEC-007) begrenzt (Review F-10, im KDoc benannt) |
| zufällig | `RpcPlpTest > … zufaellige Koerper enden in Ergebniswerten`; `RpcFuzzTest`; `TokenRoundtripTest > Zufaellige Tokens enden in Ergebniswerten und lesen sich zurueck` | grün |

Grenze: der Encoder-Grenzfall (`limit`) ist nur über die Prüf-Naht mit kleiner Grenze belegt, nicht mit einem Wert nahe 2 GiB; im KDoc von `PlpValues` benannt (Review F-1, behoben). Die Antwortseite eines `nvarchar(max)`-OUTPUT-Werts über 4000 Zeichen ist nicht getestet und in Matrix Zeile 126 so gesagt (Review F-11).

## 4. Plan-vs-Code (`git diff b028ca04..HEAD --stat` gegen §3)

| §3-Zeile | Diff | Urteil |
|---|---|---|
| `protocol/token/` (`TdsType`, `Token`, `PlpValues` neu, `VariantValues` neu, `TypeInfoWriter`, `ValueWriter`, `TokenWriters`) | alle sieben geändert oder neu | konform |
| `protocol/rpc/` (`RpcTypeInfoReader`, `PlpReader` neu, `RpcParameterReader`, `RpcRequest`); `RpcCatalog` unverändert | alle vier geändert, `RpcCatalog` nicht im Diff | konform |
| `dispatch/RpcParameters.kt`, `result/ValueMapping.kt`, `bulk/BulkLoadReader.kt`, `wire/ByteReader.kt`, `wire/ProtocolError.kt`, `dispatch/RpcBinder.kt`, `dispatch/RequestLoop.kt` | alle im Diff | konform (die Tabelle nennt alle sieben ausdrücklich; Re-Review N-2 ist erledigt) |
| Tests (`token/`, `rpc/`, `dispatch/`, `testsupport/`) | `TokenPlpNbcRowTest`, `RpcPlpTest` neu; `TokenDecoder`, `TokenRoundtripTest`, `RpcFuzzTest`, `RpcFailureTest`, `RpcBinderTest`, `RpcDispatchTest`, `RequestSequenceTest`, `RpcWire` geändert | konform |
| Spec, Matrix, `spec-abdeckung.md`, `referenzabgleich.md`, Register `mitschnitt-luecken-ohne-traeger` | alle fünf im Diff; zusätzlich Slice selbst und die beiden Review-Berichte | konform |

Nicht in §3 und nicht im Diff: nichts gefunden. Kein `@Suppress`/`SuppressWarnings` im Diff (Grep, Stichprobe über `+`-Zeilen in `src`); `git mv` kommt im Diff nicht vor (3.3 nicht berührt).

## 5. Behauptung „kein Verhalten geändert seit Review 246f3f8c“

`git diff 246f3f8c..HEAD -U0 -- src`, jede geänderte Zeile gefiltert auf „beginnt nicht mit `/**`, `*`, `*/`, `//`“: **kein Treffer**. Geändert sind 11 Dateien (+38 −21), ausschließlich KDoc-Zeilen, Umbrüche darin und `@spec`-Tags. Die Behauptung trägt. Der Diff `f131a2c3..HEAD` berührt `src` nicht (nur Matrix, Plan, Spec).

Der Beleg zu Re-Review N-1 („beobachtet“ nur für `nvarchar(max)`) ist nachgemessen und stimmt (siehe §2); `5ef12076` hat Spec (Absatz „RPC-Parametertypen“), Matrix und KDoc darauf eingeschränkt.

## 6. Gegenproben (Mutationen, je Lauf `make test` auf dem Arbeitsbaum der Worktree, danach `git checkout -- src`)

| Mutation | Ergebnis | Rote Tests |
|---|---|---|
| A: `PlpValues.writeNull` schreibt `0` statt `-1` (NULL-Zweig von PLP) | **rot** (1249 Tests, 3 rot, 202 s) | `TokenPlpNbcRowTest > PLP-Wert ist Gesamtlaenge, ein Stueck und Terminator`; `TokenRoundtripTest > Resultset aus allen Typen laeuft hin und zurueck`; `TokenRoundtripTest > Zufaellige Tokens …` |
| B: `ValueWriter.requireNullable(column)` im NBCROW gestrichen | **rot** (1 rot, 196 s) | `TokenPlpNbcRowTest > Fehlgeschlagene NBCROW aendern den Strom nicht` |
| C (Stichprobe): Terminator `0` → `1` | **rot** (5 rot) | `TokenPlpNbcRowTest > NBCROW mit PLP-Wert gibt die Bytes des echten Servers wieder`, `… PLP-Wert ist Gesamtlaenge, ein Stueck und Terminator`, `… RETURNVALUE mit MAX-Typ`, zwei Tests in `TokenRoundtripTest` |
| D (Stichprobe): `sql_variant` Basistyp `0x38` → `0x39` | **rot** (4 rot) | `TokenPlpNbcRowTest > sql_variant in ROW, NULL und andere Werte`, `… sql_variant mit Basistyp int gibt die Bytes des echten Servers wieder`, zwei Tests in `TokenRoundtripTest` |

Alle vier Mutationen wurden zurückgenommen; der Arbeitsbaum war danach sauber (`git status --short` leer, auch nach `make spec-abdeckung`).

Anmerkung zu A: der NULL-Zweig von PLP ist durch `PLP-Wert ist Gesamtlaenge, …` und zwei Roundtrip-Tests gebunden, nicht durch einen eigenen, nach dem NULL-Fall benannten Test; die Zuordnung ist trotzdem eindeutig (einzige Änderung).

## 7. Sensoren

| Sensor | Exit | Dauer | Anmerkung |
|---|---|---|---|
| `make docs-check` | 0 | 2 s | |
| `make flaeche` | 0 | 3 s | |
| `make kopplung-zahlen` | 0 | 1 s | |
| `make matrix-zitate` | 0 | 211 s | Matrix geändert; jedes Zitat steht als PASSED |
| `make spec-abdeckung` | 0 | 216 s | Datei danach unverändert |
| `make clean-build` | 0 | 667 s | Gradle-Teil `BUILD SUCCESSFUL in 3m 38s` (detekt `detektMain`, `detektTest`, `testserver:*`, `detektIntegrator` grün); Rest Image-Export und Docker-Stages |
| `make e2e` | 0 | 431 s | 9 Stufen grün: 1 Smoke 79 s, 2 Laufzeittest 101 s, 3 Simulations-Integration 53 s, 4 Szenario 49 s, 5 Anmeldung 18 s, 6 Lücken 18 s, 7 Synchronität 36 s, 8 eigenes Backend 15 s, 9 Lasttest 62 s |
| 4 Mutationsläufe `make test` | 2 (rot wie erwartet) | je rund 190 bis 200 s | |

Gesamtlaufzeit der Messläufe rund 4260 s: Mutationen 4 × rund 195 s (rund 780 s), `matrix-zitate` und `spec-abdeckung` zusammen 427 s, `clean-build` 667 s, `e2e` 431 s, Vorarbeit und Warten dazwischen. Größte Zeitposten: `clean-build`, `e2e` Stufen 1 und 2, jeder `make test` (Gradle-Tests rund 130 bis 190 s je Lauf; ein Filter unterhalb von `make test` fehlt, daher kostet jede Einzelmutation den ganzen Lauf).

**Speicher:** `free -m` verfügbar vor dem ersten Lauf 12 247 MiB, nach den Mutationen 12 321 bis 12 836 MiB, vor `clean-build` 12 555 MiB, nach `clean-build` 12 596 MiB, nach `e2e` 13 362 MiB; nie unter 11 400 MiB (Mutation D). Der Schwellwert von 4 GB wurde nie berührt (Vergleichswert früherer `clean-build` rund 8,6 GB wurde nicht unterschritten). Keine künstliche Last. Fremde Container und Images nicht angefasst; eigene getaggte Images haben die Läufe nicht angelegt (nur Build-Cache, der unter `docker images` nicht erscheint), daher gab es nichts aufzuräumen.

## 8. Zweite Fundstellen (Grep)

| Suche | Ergebnis |
|---|---|
| `nvarchar(max)` + abgelehnt in Spec, Matrix, `docs/messungen`, `docs/user`, `src` | alle Treffer stimmen mit dem Stand überein (`varchar(max)`/`varbinary(max)` abgelehnt, `nvarchar(max)` als `UnicodeText`); Matrix 126, Spec `SPEC-013`-Zeile und Absatz „RPC im Backend-Vertrag“ konsistent |
| `SPEC-028` neben PLP, NBCROW, sql_variant, MAX | kein Treffer in `spec/`, `docs/` (ohne die Review-Berichte), `src/` |
| „vom Referenzclient beobachtet“, „am Referenzclient beobachtet“, „Client sendet MAX“ | ein Treffer: Überschrift des Absatzes „RPC-Parametertypen (Eingabe, vom Referenzclient beobachtet)“ in `spec/spezifikation.md`; der Absatz schränkt MAX direkt darunter auf `nvarchar(max)` `E7 FF FF` ein (siehe F-1) |
| A-12 | `referenzabgleich.md` Zeile A-12, Matrix Lücke 6, Plan §6: einheitlich „Encoder schreibt NBCROW auf Anweisung, Sequenzierer ROW, Folge-Slice (Nutzerentscheidung 2026-11-16)“ |
| Matrix Lücke 6, 15 | Lücke 6 „im Encoder geschlossen … Folge-Slice vorgesehen“; Lücke 15 „kein Träger“ für die Erweiterung des Typkatalogs; konsistent mit Spec |
| `text`, `ntext`, `image` | Spec (Absatz MAX/NBCROW, Zeile Änderungsliste), Matrix 128: einheitlich „bleiben `TDS_UNSUPPORTED_TYPE`, kein gemessener Sender“ |

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| F-1 | LOW | Die Überschrift des Absatzes „RPC-Parametertypen (Eingabe, vom Referenzclient beobachtet)“ führt als beobachtet eine Typenliste an, deren Nachsatz nur `nvarchar(max)` als aufgezeichnet nennt und `varchar(max)`, `varbinary(max)`, `xml` als Handvektor ausnimmt. Die Überschrift gilt für die übrigen Typen; der Nachsatz ist richtig. Lesbar, aber die Überschrift allein trägt breiter als der Nachsatz für die MAX-Typen. | `AGENTS.md` 3.8 | `spec/spezifikation.md` · `RPC-Parametertypen (Eingabe, vom Referenzclient beobachtet)` | ja: Grep | Überschrift breiter als Nachsatz |
| F-2 | LOW | Plan §2 DoD-Zeile 3 verlangt `text`, `ntext`, `image`, `sql_variant` nur, „soweit SPEC-021 sie als gesendet führt“. SPEC-021 führt keinen der vier als gesendet; die Aufnahme von `sql_variant` stützt sich auf die Server-Antwort (M-16) und die Nutzerentscheidung 2026-11-16, nicht auf den DoD-Wortlaut. Der DoD-Wortlaut bildet das Geliefert nicht mehr ab; ob er geändert oder ein Rest-Slice für `text`/`ntext`/`image` geschnitten wird, ist Sache des Planners nach Rückfrage. | DoD Zeile 3 | `docs/plan/planning/done/slice-codec-datentypen-erweiterung.md` · `soweit spec/spezifikation.md SPEC-021 sie als gesendet führt` | nein | DoD-Wortlaut hinter Nutzerentscheidung |
| F-3 | INFO | Die Zuordnung der NULL-Probe von PLP zu einem eigens benannten Test fehlt (A: gebunden über drei breite Tests). | Maintainability | `src/test/…/token/TokenPlpNbcRowTest.kt` | nein | NULL-Fall nur in breiten Tests rot |
| F-4 | INFO | `make gates` habe ich nicht gefahren (Tabelle für die Art „src/“ nennt es nicht für den Verifier; `clean-build`, `e2e`, `test`, `docs-check`, `flaeche`, `kopplung-zahlen` grün). Das „gates grün“ der DoD stützt sich auf den Implementer-Bericht. | `harness/README.md` §Minimal agent workflow | — | ja: `make gates` | Belegumfang |
| F-5 | INFO | Kein HIGH und kein MEDIUM. Die zwei MEDIUM-Funde der Vorgänger (F-3 Spec-Zeiger, N-1 Beobachtungsumfang) sind am Stand nachgewiesen behoben. | — | — | — | — |

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| Verhaltensänderung nach Review 246f3f8c in `src/` | geprüft per Diff-Filter, ohne Befund (nur KDoc, Umbrüche, Tags) |
| Mutationen NULL-Zweig PLP, `requireNullable` im NBCROW, Terminator, `sql_variant`-Basistyp | geprüft, je rot und zurückgenommen, ohne Befund |
| Beobachtungsumfang `E7 FF FF` im Mitschnitt (16, nur `nvarchar(max)`) | nachgemessen, stimmt |
| Öffentliche Fläche, Suppression, `git mv` | `make flaeche` Exit 0; kein `@Suppress`; kein Move im Diff |
| Matrix-Zitate | `make matrix-zitate` Exit 0 |
| Arbeitsbaum nach allen Läufen | sauber; Bericht bleibt ungetrackt |
