# Slice slice-backend-typabbildung: Typabbildung Backendtyp → TDS-Typ und -Wert, Validierung der Metadaten vor COLMETADATA

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-backend-streaming

**Bezug:** [TDS-FA-05](../../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilit%C3%A4t), [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)

**Berührte Spec-Stellen:** SPEC-003 · SPEC-014 · SPEC-031 · ARC-001 · ARC-007 (Typabbildung ist zentral und deterministisch, `spec/architecture.md` §2)

**Verantwortlich:** pt9912

**Autor:** pt9912. **Datum:** 2026-10-30.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Eine zentrale, deterministische Abbildung überführt `ResultColumn`/`SqlType` in die `ColumnDef` des Codecs (`TdsType`, Nullbarkeit, Name) und einen Backendwert in einen `TdsValue`; die gesamte Spaltenliste ist vor der Ausgabe von COLMETADATA validiert, und ein nicht abbildbarer Typ oder Wert endet als definierter Fehlerwert (`TDS_UNSUPPORTED_TYPE`, `SPEC-014`) statt als fehlerhaftes Token ([TDS-FA-05.a](../../../../spec/spezifikation.md#tds-fa-05a--ergebniskodierung), [TDS-FA-05](../../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen) Negative).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Sequenzierer, Ereignisfolge, `DONE_MORE`, Fehlertokens — `slice-backend-sequenzierer`; die Abbildung liefert Ergebniswerte, schreibt aber keine Tokens in einen Strom.
- Typen außerhalb des Mindestkatalogs (tinyint/smallint-Breiten, Gleitkomma, Geld, GUID, Binär, MAX/PLP, NBCROW) — `slice-codec-datentypen-erweiterung` und der Katalog von `SPEC-003`; ein solcher Backendtyp ist nicht abbildbar und endet im definierten Fehler ([TDS-FA-05](../../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen), Out-of-Scope: vollständiger SQL-Server-Typkatalog).
- Das Backend-Typmodell selbst (`SqlType`) — `slice-backend-port-vertrag`; dieser Slice setzt es voraus.
- Fehlernummer, Severity und State des Fehlerwerts — `welle-fehler-abbruch` ([TDS-FA-06.a](../../../../spec/spezifikation.md#tds-fa-06a--fehlerabbildung)); der Fehlerwert trägt die interne Kategorie, nicht die TDS-Nummer.
- Messung der Typkodierung am Referenzclient und am echten SQL Server — `slice-qa-reproduzierbarkeit`, `slice-qa-kompatibilitaet` (`BEO-TDS/messung-am-echten-sql-server-fehlt`); die Abbildung belegt von Hand gerechnete Vektoren und Selbstkonsistenz mit dem Encoder; die erwarteten TYPE_INFO-Bytes und der Vektor 23:59:59,9999999 stammen aus den Orakeln der Encoder-Tests und sind Selbstkonsistenz, keine unabhängige Quelle. Abschnittsnummern der MS-TDS-Spezifikation stehen nicht in Tests und KDoc, solange das Repo keine Quelle dafür führt (`AGENTS.md` §3.8).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [x] **Metadatenabbildung** ([TDS-FA-05.a](../../../../spec/spezifikation.md#tds-fa-05a--ergebniskodierung) Schritt 1, 2; `SPEC-003`): `SqlType` → `TdsType` für den Mindestkatalog mit Precision/Scale-Grenzen (Decimal 1 bis 38), Unicode-Text bis 4000 UTF-16-Einheiten (Kollation als gekennzeichnete Festlegung), fester Scale 7 für Zeit und Datum mit Zeit (Festlegung; das Modell trägt keinen Scale, die Spanne 0 bis 7 des Codecs wird nicht genutzt), nullable → N-Form; die komplette Spaltenliste wird vor jeder Ausgabe validiert (`SPEC-031`, erster Satz); leere Spaltenliste und leeres Resultset mit Metadaten bleiben gültig.
- [x] **Wertabbildung** ([TDS-FA-05.a](../../../../spec/spezifikation.md#tds-fa-05a--ergebniskodierung) Schritt 3, 5; `SPEC-003`): Backendwert → `TdsValue` mit NULL, Min/Max der Integer, leerem String (nicht NULL), maximaler Textlänge, Unicode einschließlich Surrogatpaaren, Decimal-Skalierung und -Überlauf, Datum und Zeit ohne Zeitzone (Zeitzonenannahme als Festlegung, §6); ein Wert, der nicht zur Spaltenart passt, ist ein Fehlerwert vor Ausgabe der Zeile.
- [x] **Fehlerverhalten** ([TDS-FA-05](../../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen) Negative, `SPEC-014`, [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilit%C3%A4t)): nicht abbildbarer Backendtyp, ungültiger Unicode (unpaarer Surrogat), Überlauf und Typkonflikt enden in einem Ergebniswert mit interner Kategorie `TDS_UNSUPPORTED_TYPE`; Zufalls- und Grenzeingaben mit festem Startwert enden stets in `Ok` oder dem Fehlerwert, nie in einer Ausnahme; die Textdarstellung des Fehlerwerts enthält keine Zeilenwerte (Nutzerdaten).

Nachweis-Pflichten für alle drei Punkte:

- [x] Jeder Test trägt im KDoc über der Testfunktion den Block-Tag `@spec SPEC-<NNN>`; die Tests laufen in `make test` und sind grün.
- [x] Jede Aussage im Quelltext (KDoc) und in Spec oder Vertrag steht an einem Test oder ist als Festlegung gekennzeichnet; die Closure-Notiz trennt *gemessen*, *Handvektor*, *Selbstkonsistenz* und *Festlegung* wie `slice-codec-tds-paket` (`BEO-TDS/aussagen-ueber-messung-hinaus`). Die Messung in [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) gilt für den Spike, nicht für den Quelltext dieses Slice.
- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/result/` | neu | Typabbildung (`ColumnMapping`, `ValueMapping`); liegt in `protocol`, weil `protocol` laut Importregeln `domain` und `protocol/token` zugleich nutzen darf (`domain` und `port` dürfen `protocol` nicht importieren); Festlegung dieses Slice. Nutzt `ColumnDef`, `TdsType`, `TdsValue` aus `protocol/token` und `SqlType`/`ResultColumn` aus `domain`; keine Imports aus `transport`, `simulation`, `runtime` |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/result/` | neu | Handvektoren je Typ (Happy, Boundary, Negative nach [TDS-FA-05](../../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen)), Roundtrip mit `TokenDecoder`, Fuzz mit festem Startwert; `@spec`-Tag je Test |
| `spec/spezifikation.md` | update, falls nötig | `SPEC-003` nennt „zu verifizieren“ und Grenzfälle ohne Wert (Zeitzone, Kollation); ändert die Umsetzung eine Zeile, wird sie im selben Slice nachgezogen |

- Reihenfolge: Metadaten, dann Werte, dann Fehlerverhalten.
- Der Implementer liest `TdsType`, `ColumnDef`, `TdsValue` und die Writer-Grenzen (`TypeInfoWriter`, `ValueWriter`) und passt diese Tabelle an.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): `slice-backend-port-vertrag` liegt in `done/` (erfüllt; `SqlType`, `ResultColumn`), `make gates` ist auf `main` grün, die Nutzerentscheidung zur Zeitzonenannahme und zum Kollationswert (§6) liegt vor, und ein Verantwortlicher ist im Kopf eingetragen. Der Slice braucht den Sequenzierer nicht.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2; Schnittvorschlag: Slice A = Metadatenabbildung, Slice B = Wertabbildung und Fehlerverhalten.
- `in-progress` → `open` (blockiert — Carveout?): `SqlType` trägt den Mindestkatalog nicht (Zeit-Scale, Länge von `UnicodeText`; der Port-Slice liegt in `done/`) und die Änderung am Modell ist nicht Teil dieses Slice, sondern braucht einen eigenen Folge-Slice.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün, der Review-Report liegt vor, und die Closure-Notiz trägt die Zuordnung je Aussage der Abbildung (*gemessen* · *Handvektor* · *Selbstkonsistenz* · *Festlegung*) sowie den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **Zeitzonen- und Genauigkeitsannahme — Entscheidungsbedarf (Nutzer):** `SPEC-003` nennt „Zeitzonenannahmen“ und „nach Profil“ ohne Wert; Backendwerte mit Zone (`Instant`, `OffsetDateTime`) sind weder im Mindestkatalog noch abgebildet, und die Rundung bei Scale kleiner als die Genauigkeit des Werts ist offen. — **Entschieden (Nutzer, 2026-10-30):** Werte ohne Zone (`LocalDate`, `LocalTime`, `LocalDateTime`) werden wie geliefert abgebildet, ohne Zeitzonenumrechnung; Werte mit Zone (`Instant`, `OffsetDateTime`) sind nicht im Mindestkatalog und enden in `TDS_UNSUPPORTED_TYPE`; ist die Genauigkeit des Werts größer als die Scale der Spalte, wird abgewiesen statt gerundet (der Encoder rundet nie). Die JVM-Typen je `SqlType` sind eine Festlegung dieses Slice; die endgültige Zeilendarstellung entscheidet `slice-adr-backend-grenzen`. — **Ausgang:** entfallen (entschieden und umgesetzt); Belege in der Closure-Notiz.
- **Lesart der Nutzerentscheidung zur Genauigkeit:** bei Decimal zählen nachlaufende Nullen nicht (1,500 passt in Scale 2, 1,234 wird abgewiesen), aber eine Mantisse mit mehr als 1000 Stellen wird auch dann abgewiesen, wenn der Wert nach dem Kürzen passen würde (Grenze vor `stripTrailingZeros`); bei Zeit gilt das 100-ns-Raster. Alles Festlegungen dieses Slice, ungemessen und vom Nutzer nicht bestätigt (`SPEC-003`, KDoc von `DecimalMapping`). — **Ausgang:** weiter offen: → `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt`.
- **Kollation von Unicode-Text** (Wert in `Collation`): der Codec legt `SQL_Latin1_General_CP1_CI_AS` als Konstante fest, ob sie zum Clientprofil passt, ist am Testserver gemessen, nicht am Produkt. — **Ausgang:** weiter offen: → `BEO-TDS/messung-am-echten-sql-server-fehlt` (kein Slice trägt die Kollation; `slice-qa-reproduzierbarkeit` liegt in `done/`).
- **Modellgrenzen von `SqlType` gegen die Abbildung (aus `slice-backend-port-vertrag`):** (a) `SqlType.Decimal` nimmt jede Precision ab 1 an, der Codec (`TypeInfoWriter.MAX_PRECISION`) 1 bis 38; wie der Widerspruch aufgelöst wird (Obergrenze im Modell oder Fehlerwert in der Abbildung), ist eine Nutzerentscheidung — **Entschieden (Nutzer, 2026-10-30):** das Modell (`domain/SqlType.Decimal`) bleibt unverändert; die Typabbildung validiert die Spaltenliste vor COLMETADATA und meldet `TDS_UNSUPPORTED_TYPE` bei Precision über 38. (b) `SqlType` trägt weder die Scale von `Time` und `DateTime` noch ein Längenlimit von `UnicodeText`; die Abbildung setzt beide fest (Scale 7 als Festlegung, Länge bis 4000); ein erweitertes Modell ist nicht verlangt. (c) Die JVM-Typen der Werte in `Row`, `QueryParameter` und `OutputParameter` je `SqlType` sind im Port offen ([ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)); die Wertabbildung legt sie als Festlegung fest (Zwischenfestlegung; die endgültige Zeilendarstellung bleibt Gegenstand von `slice-adr-backend-grenzen`, `SPEC-002` führt sie unter *Offen*). — **Ausgang:** entfallen: (a) entschieden und umgesetzt, (b) und (c) als Festlegung getragen; kein Widerspruch zwischen Modell und Abbildung bleibt.
- **Nullbarkeit gegen Wert:** ein `null`-Wert in einer als nicht nullbar beschriebenen Spalte, doppelte oder leere Spaltennamen: die Spezifikation schweigt (benannte Spec-Lücke); der Slice legt sie als Festlegung fest oder nennt sie offen. — **Ausgang:** entfallen: als Festlegung getragen (NULL in nicht nullbarer Spalte ist `TDS_UNSUPPORTED_TYPE`; leere und doppelte Namen sind gültig, über 255 Zeichen und unpaares Surrogat werden abgewiesen); die Spec-Lücke bleibt benannt (Closure-Notiz).
- **Ohne Messung am Referenzclient:** Die Abbildung belegt Handvektoren und Selbstkonsistenz; die Annahme der Spaltenmetadaten durch den Client ist nicht gemessen (`BEO-TDS/aussagen-ueber-messung-hinaus`, `BEO-TDS/messung-am-echten-sql-server-fehlt`). — **Ausgang:** weiter offen: → `BEO-TDS/messung-am-echten-sql-server-fehlt`, `BEO-TDS/aussagen-ueber-messung-hinaus`; Träger der Messung am Referenzclient: `slice-qa-kompatibilitaet`.
- **Doppelung mit `slice-codec-datentypen-erweiterung`:** wächst der Katalog hier, wird der Folge-Slice überflüssig oder doppelt. — **Ausgang:** entfallen: der Katalog wuchs nicht (`TdsType` und `TypeInfoWriter` unverändert bis auf Kommentare), `slice-codec-datentypen-erweiterung` bleibt unberührt.
- **Zonentest setzt die Standardzone der JVM:** der Test `Bytes sind unabhaengig von der Standardzone der JVM` ruft `TimeZone.setDefault` auf und stellt den Wert im finally-Block zurück; das ist nur bei sequenzieller Ausführung der Tests sicher. Die Test-JVM läuft ohne parallele Ausführung und ohne Forks (Nachverifikation), aber kein Gate hält diese Voraussetzung. — **Ausgang:** weiter offen: → `BEO-TDS/test-aendert-jvm-standardzone-ohne-sensor`.
- **MS-TDS-Abschnittsnummern ohne Quelle in Dateien außerhalb dieses Slice:** `Token.kt`, `TdsType.kt`, `TokenWriters.kt` und die Parser nennen Abschnittsnummern, das Repo führt keine Kopie der Spezifikation (`AGENTS.md` §3.8). In `protocol/result`, den Tests und vier Dateien des Encoders sind sie ersetzt. — **Ausgang:** weiter offen: → `BEO-TDS/aussagen-ueber-messung-hinaus`; kein Eingriff in diesem Slice.

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register (vorhandene `BEO-<KUERZEL>/<slug>` **zitieren** statt neu
formulieren — sonst zählt das Register zwei Namen getrennt) ·
`grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln (das
Feld `liegt in` steht **nur**, wenn mit diesem Slice wirklich etwas verkörpert
wurde; Feld und Zielort auf **einer** Zeile, Sektionsangabe innerhalb der
Backticks). Ging der Gegenstand an einen anderen Slice oder entfiel er, trägt
diese Sektion die Zeile `Gegenstand:` mit Kennung oder Grund und jedes Risiko
aus §6 seinen Ausgang; die Liefer-Punkte der DoD bleiben leer
(`modul-05-planning-harness.md` §Ein Slice, dessen Gegenstand ein anderer
übernimmt).

- **Was hat funktioniert:** Der Schnitt trug: Metadaten-, Wert- und Fehlerabbildung blieben in einem Slice mit drei Liefer-Punkten, ohne Rückführung nach §4. Die Abbildung ließ sich gegen den Codec prüfen, weil `protocol/result` nur `domain` und `protocol/*` importiert; die Schichtregel hält `make detekt`. Die Prüfung durch Mutation fand, was Lesen und grüne Tests nicht fanden: Verifikation fuhr 21 Mutanten (17 rot, vier überlebten), die Nachverifikation weitere 21 (18 rot, drei überlebten). Zeit- und Mantissengrenzen sind als Zeitgrenze im Test erzwungen (5 s in `DecimalValuesTest`, 2 s im Test zur riesigen Mantisse) und blieben in zwölf Läufen der Nachverifikation unter hoher Last grün.
- **Was ging anders als geplant:** (a) **Der Encoder skalierte vor der Grenzprüfung.** `DecimalValues.bytes` rief `setScale` vor der Precision-Prüfung auf; mit direkt gebautem `DecimalValue("1E+3000000")` dauerte der Aufruf 1330 ms, bei `1E+30000000` über 20 s, bei `1E+999999999` kam eine `ArithmeticException` statt `ProtocolViolation` (Review F-1, Verifikation V-4). Über die Abbildung war der Pfad nicht erreichbar, weil sie vor jeder Skalierung abweist; über den `TokenEncoder` schon. Behoben (Fix-Runde `d33534b`): Prüfung über `integerDigits` vor `setScale`. Dieselbe Klasse trat in `DecimalMapping` über `stripTrailingZeros` auf (10^100000 brauchte 5,1 s); behoben mit der Grenze von 1000 Stellen der Mantisse vor dem Kürzen, als Festlegung gekennzeichnet. (b) **Die Fehlertexte und die Zonen-Unabhängigkeit waren nur im Code wahr, nicht im Test** (Review F-2, F-3; Verifikation L1 bis L3, Z1): vier Mutanten überlebten. Behoben mit je einem Fall pro Fehlerpfad und einem Zonentest mit Europe/Berlin und America/New_York (Sommerzeitlücke). (c) **Spec, Vertrag und KDoc waren nach den Entscheidungen vom 2026-10-30 falsch** (Review F-4, Verifikation V-1): `SPEC-002` sagte, die Precision über 38 scheitere „erst beim Encodieren“, `contracts/query-backend.md` und die KDoc von `SqlType` führten Entschiedenes als offen. Nachgeführt in Spec, Vertrag und KDoc; `SPEC-003` trägt jetzt die Setzungen. (d) **Abschnittsnummern und Etiketten** (Review F-5, F-7): MS-TDS-Nummern ohne Quelle im Repo sind in `protocol/result`, den Tests und vier Dateien des Encoders durch „Layout nach MS-TDS“ ersetzt; zwei Vektoren heißen jetzt „Selbstkonsistenz mit den Encoder-Tests“ statt Handvektor. (e) **Zweite Fix-Runde (`48e7154`)** schloss die Reste der Nachverifikation: Mantissenpfad im Fehlertext-Test (N-1), Date im Zonentest (N-2), `SPEC-003` an das Verhalten der 1000-Stellen-Grenze gebunden (N-3), `ValueMapping.row` bricht beim ersten Fehler ab (N-4, Review F-8), Scale 7 in §6 (b) (N-5), Test für 1000 und 1001 Stellen (N-6). **Diese Runde hat kein Verifier nachgeprüft;** die Angabe, dass die Mutanten L9, Z3, M3 und ein Eager-Mutant dabei rot wurden, steht in der Commit-Message des Implementers.
- **Reports:** [Review](../../../reviews/2026-10-30-55fc860-slice-backend-typabbildung.md) (kein HIGH; F-1 bis F-5 MEDIUM, F-6, F-7 LOW, F-8, F-9 INFO), [Verifikation](../../../reviews/2026-10-30-55fc860-verifikation-slice-backend-typabbildung.md) (kein HIGH; V-1, V-2 MEDIUM, V-3 bis V-6 LOW) und [Nachverifikation](../../../reviews/2026-10-30-461e380-nachverifikation-slice-backend-typabbildung.md) (kein HIGH; N-1, N-2, N-3, N-5, N-6 LOW, N-4 INFO), gegen die Stände `55fc860` und `461e380`. Stand der Befunde: F-1 bis F-8 und V-1 bis V-6 behoben, N-1 bis N-6 laut Fix-Runde `48e7154` behoben (ungeprüft, siehe oben); offen ist allein F-9, die Bestätigung der Decimal-Lesart durch den Nutzer. Ein Validator-Lauf entfällt: der Slice liefert keinen End-Nutzer-Wert, außerhalb der Tests ruft die Abbildung bisher niemand auf.

**Zuordnung Beleg | Aussage.** Vier Stärken: *gemessen* heißt an einem echten Client oder Server beobachtet; *Handvektor* heißt von Hand gerechnet, im Test-KDoc nachvollziehbar und vom Verifier mit Python nachgerechnet, ohne unabhängige Quelle im Repo (das Layout folgt MS-TDS, das Repo führt keine Kopie); *Selbstkonsistenz* heißt Code gegen Code (Encoder, Test-Decoder, Orakel im Test); *Festlegung ohne Messung* heißt Setzung ohne Messung. *Nicht gemessen* nennt, was keine Aussage trägt.

| Stärke | Aussage | Beleg im Repo |
|---|---|---|
| gemessen | **nichts.** Weder ein Referenzclient noch ein SQL Server hat Spaltenmetadaten oder Zeilen aus dieser Abbildung gesehen; die Messung in [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) gilt für den Spike, nicht für diesen Quelltext | — |
| Handvektor | Integer Min und Max in fester und N-Form, Bit, NULL-Marken, leerer String gegen NULL, Text mit Surrogatpaar, Decimal 999,99 und 10^38 - 1 als 16 Byte, Datum 2000-01-01, 2024-01-01, 2024-03-10 und 9999-12-31, Zeit 12:00 | `ValueMappingTest` (`@spec SPEC-003`); die Rechnung steht im KDoc, die Verifikation rechnete mit Python nach |
| Selbstkonsistenz | erwartete TYPE_INFO-Bytes des Mindestkatalogs und Vektor 23:59:59,9999999 (übernommen aus den Orakeln der Encoder-Tests); Roundtrip der Zeilen durch `TokenEncoder` und Test-Decoder; Fuzz mit festem Startwert 20_261_030 (30 000 Zeilen, Orakel mit `CharsetEncoder` und `setScale` ohne Aufruf der Abbildung, zufällige Spaltenlisten bis Precision 60) | `ColumnMappingTest`, `ValueMappingTest`, `MappingFuzzTest`; Orakel und Encoder teilen Annahmen über das Layout, ein gemeinsamer Fehler bliebe unentdeckt |
| Selbstkonsistenz (Verhalten) | Spaltenliste ganz oder gar nicht; Zeile atomar und beim ersten Fehler abgebrochen; Fehlertexte ohne Zeilenwerte je Fehlerpfad; Zonen-Unabhängigkeit von Date, Time und DateTime unter Berlin und New York; Rechenzeit bei extremem Exponent und großer Mantisse begrenzt | `ColumnMappingTest`, `ValueMappingTest`, `DecimalValuesTest`; Mutanten M1 bis M5, L1 bis L12 und Z1 bis Z4 der Nachverifikation, davon 18 von 21 rot, die drei überlebenden laut Fix-Runde `48e7154` inzwischen rot (nicht nachgeprüft) |
| Festlegung ohne Messung | Kollation `SQL_Latin1_General_CP1_CI_AS`; Scale 7 für Zeit und Datum mit Zeit (das Modell trägt keinen Scale, 0 bis 6 nur im Codec); Unicode-Text bis 4000 UTF-16-Einheiten; DECIMALN statt NUMERICN; Spaltenzahl 65534 und Namenslänge 255 (Grenzen des Codecs) | KDoc von `ColumnMapping`, `SPEC-003`; `ColumnMappingTest` pinnt die Werte, belegt aber keine Passung zu einem Client |
| Festlegung ohne Messung | JVM-Klassen je `SqlType` (`Int`, `Long`, `Boolean`, `String`, `BigDecimal`, `LocalDate`, `LocalTime`, `LocalDateTime`); NULL in nicht nullbarer Spalte ist ein Fehler; leere und doppelte Spaltennamen sind gültig; Jahr 1 bis 9999 | KDoc von `ValueMapping`, `ColumnMapping`; Tests pinnen die Wahl; die endgültige Zeilendarstellung entscheidet `slice-adr-backend-grenzen` |
| Festlegung (Nutzerentscheidung 2026-10-30) | Werte ohne Zone ohne Umrechnung, Werte mit Zone abgewiesen; Genauigkeit größer als die Scale wird abgewiesen statt gerundet; Precision über 38 meldet `TDS_UNSUPPORTED_TYPE`, das Modell bleibt | §6, `SPEC-002`, `SPEC-003`; Tests je Punkt |
| Festlegung, vom Nutzer unbestätigt | Lesart: nachlaufende Nullen zählen nicht (1,500 passt in Scale 2); Mantisse über 1000 Stellen wird auch dann abgewiesen, wenn der Wert nach dem Kürzen passen würde; 100-ns-Raster der Zeit | `DecimalMapping` (KDoc), `SPEC-003`, Tests „Decimal wird auf den Scale der Spalte skaliert“ und zur 1000/1001-Grenze; Register `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` |
| nicht gemessen | Annahme der Spaltenmetadaten und Werte durch den Referenzclient; Kollation am Produkt; ein Metadaten-Token ohne Zeilen (das Zusammensetzen gehört dem Sequenzierer, ein Test mit null Zeilen fehlt); das Verhalten bei Zeitzonen im Betrieb | Träger: `slice-qa-kompatibilitaet` (Ende-zu-Ende mit .NET-Client, SELECT und NULL; die Kollation trägt kein Slice), `slice-backend-sequenzierer` (Zusammensetzen); Register `BEO-TDS/messung-am-echten-sql-server-fehlt` |

**Offene Punkte der Closure mit Träger** (gegen den Inhalt der Dateien geprüft; „Nutzerentscheidung“ ist kein Träger):

| Offener Punkt | Träger | Stand |
|---|---|---|
| endgültige Zeilendarstellung (`Row`, `QueryParameter`, `OutputParameter`) | `slice-adr-backend-grenzen` (`open/`) | führt S1 (Grenze „kleines Ergebnis“ und Zeilendarstellung); `SPEC-002` führt sie unter *Offen* |
| Annahme durch den Client (Ende-zu-Ende) | `slice-qa-kompatibilitaet` (`open/`) | Ziel: Szenarien mit echtem .NET-Client als Gate; `slice-qa-reproduzierbarkeit` liegt in `done/` und trägt es nicht |
| Kollation am Produkt, Annahme durch einen echten SQL Server | Register `BEO-TDS/messung-am-echten-sql-server-fehlt` | Beobachtung, offen; kein Slice trägt sie |
| Bestätigung der Decimal-Lesart und der 1000-Stellen-Grenze (Nutzer) | Register `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` | Beobachtung, offen; kein Slice trägt sie |

- **Gegenstand:** entfällt (der Slice lieferte).
- **Steering-Loop-Eintrag:** Kein `liegt in`: mit diesem Slice ist keine Regel, kein Sensor und keine Gate-Zeile verkörpert worden; gezählt wird, nicht verkörpert, und keine unverkörperte Beobachtung erreicht mit diesem Slice 3×. Vier Feststellungen. (1) **Gelernt: Eine Grenzprüfung steht vor jeder Operation, deren Aufwand mit dem Wert wächst.** `setScale` im Encoder und `stripTrailingZeros` in der Abbildung rechneten vor der Prüfung; der Aufwand wuchs mit Exponent und Mantissenlänge, und die KDoc der Abbildung behauptete das Gegenteil für den Exponenten. Behoben im Encoder (Prüfung über die Stellenzahl vor `setScale`) und in der Abbildung (Grenze vor dem Kürzen); die Zeitgrenzen in `DecimalValuesTest` und im Test zur riesigen Mantisse halten es in `make test`. Eine Regel ist es noch nicht (1×): Register `BEO-TDS/normalisierung-vor-grenzpruefung-bindet-rechenzeit` (neu). (2) **Mutanten überlebten, weil die Eingabe nicht variierte:** die Fehlertexte wurden an vier Pfaden geprüft, nicht an allen, und der Zonentest lief unter der Zone des Containers; ein Detail-Leck und eine Zonenumrechnung blieben grün (Review F-2, F-3; Verifikation L1 bis L3, Z1; Nachverifikation L9, Z3, M3). Das ist die Klasse `BEO-TDS/strukturtest-ohne-variation-der-eingabe`, hier das zweite Auftreten (nach `slice-auth-adapter-benutzerliste`); die Prüffrage „Welche Änderung am Produktionscode macht diesen Test rot, und wurde sie gesehen?“ bleibt Verifier-Handlung. (3) **Das Muster der Aussagen über den Beleg hinaus trat trotz der verkörperten Regel wieder auf** (Spec und KDoc nach der Entscheidung nicht nachgeführt, Abschnittsnummern ohne Quelle, Etikett Handvektor bei Encoder-Herkunft, KDoc breiter als die Messung zur Rechenzeit, Spec breiter als das Verhalten bei der 1000-Stellen-Grenze): ein weiteres Auftreten von `BEO-TDS/aussagen-ueber-messung-hinaus`, keine Schärfung; `AGENTS.md` §3.8 war der Maßstab. „Spec nach Entscheidung nicht nachgeführt“ trat auch bei `slice-backend-port-vertrag` auf (Review M1, Verifikation V1) und ist in dieser Klasse mitgezählt. (4) **Benannte Spec-Lücke:** `SPEC-003` schweigt zu NULL in einer nicht nullbaren Spalte und zu Spaltennamen (leer, doppelt, Länge, Unicode); der Slice setzt sie in KDoc und Tests (Festlegung ohne Messung). Die Lücke hat noch keine Zeile in einer versionierten Spec und ist darum nur benannt, nicht verkörpert. Außerdem offen und ehrlich geführt: ein Test setzt `TimeZone.setDefault` und ist nur bei sequenzieller Ausführung sicher, kein Gate hält diese Voraussetzung (Register `BEO-TDS/test-aendert-jvm-standardzone-ohne-sensor`, neu; kein neues Gate in diesem Slice).
- **Beobachtungs-Register (`../observations/`):** `evidence/slice-backend-typabbildung.md` ergänzt in `BEO-TDS/aussagen-ueber-messung-hinaus` (Review F-4 bis F-7, Verifikation V-1, V-4, V-6, Nachverifikation N-3; die MS-TDS-Abschnittsnummern in `Token.kt`, `TdsType.kt`, `TokenWriters.kt` und den Parsern stehen als Rest in der Evidence), `BEO-TDS/messung-am-echten-sql-server-fehlt` und `BEO-TDS/strukturtest-ohne-variation-der-eingabe`; drei neue Beobachtungen mit je einer Evidence-Datei: `BEO-TDS/normalisierung-vor-grenzpruefung-bindet-rechenzeit` (Review F-1, Verifikation V-4), `BEO-TDS/test-aendert-jvm-standardzone-ohne-sensor` (Nachverifikation Abschnitt 4) und `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` (Review F-6, F-9; Nachverifikation N-3). Kein neues Auftreten: `BEO-TDS/test-belegt-nur-den-fake` (die Tests laufen gegen den Produktionscode, das Orakel des Fuzz-Tests ruft die Abbildung nicht auf), `BEO-TDS/schranken-test-mit-ungeprueftem-zaehler` (die einseitige Grenze 1000 ohne 1001 ist ein anderer Mechanismus: es fehlte ein Fall, kein Zähler), `BEO-TDS/offene-punkte-ohne-traeger` (die offenen Punkte haben Träger, siehe Tabelle), `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` und die übrigen. Die Zähler folgen aus den Dateien; `aussagen-ueber-messung-hinaus` steht schon auf `verkörpert`, sein `state.md` bleibt unverändert.
- **Risiken aus §6:** alle neun tragen genau einen Ausgang (siehe §6): vier entfallen (Zeitzone und Rundung entschieden und umgesetzt, Modellgrenzen, Nullbarkeit und Namen, Doppelung), fünf weiter offen mit Register-Eintrag (Lesart der Decimal-Entscheidung, Kollation, Messung am Referenzclient, Zonentest, MS-TDS-Abschnittsnummern in anderen Dateien; Kollation und Messung gehören zu `BEO-TDS/messung-am-echten-sql-server-fehlt`). Kein Risiko ist eingetreten, das einen Carveout oder Folge-Slice verlangt.
- **Drei Paarungen:** Anker: kein Eintrag trägt `liegt in`, die Paarung entfällt. Folge-Slice: `slice-adr-backend-grenzen` und `slice-qa-kompatibilitaet` existieren als Dateien und führen die Punkte; `slice-backend-sequenzierer` wird mit diesem `→ done` startbar (seine §6-Nutzerentscheidungen stehen aus). Register: drei Evidence-Dateien in vorhandenen und drei in neuen Beobachtungen, siehe oben.

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung — dort die **zwei vorgelagerten
Schritte** (sie stehen in jedem Slice-Plan, unabhängig von Modus und
Slice-Typ) und die **vier Pflichtkriterien** (Konventionen-Dichte ·
Phase-Reife · Evidenz-/Diskrepanz-Risiko · Reconciliation-Aufwand), vier und
nicht mehr.

**Der Abschnitt selbst entfällt nie.** Die zwei vorgelagerten Prüfungen laufen
in **jedem** Slice-Plan — sie hängen weder am Modus noch am Slice-Typ. Bedingt
ist allein der Modus-Begründungsblock am Ende; deshalb nennt der Titel beide
Hälften.

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); `port`, `protocol` und `domain` sind keine eigenen Pfad-Cluster mit eigener Strukturregel (die Schichten-Importregeln in `config/detekt/detekt.yml` sind Architektur-, keine Modus-Grenzen). Keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, acht Beobachtungen, alle Sub-Area `*`). Treffer: `BEO-TDS/aussagen-ueber-messung-hinaus` (7×, die Regel steht in `AGENTS.md` §3.8; sie bindet KDoc, Vertrag und Spec-Nachführung dieses Slice, Gegenmittel in §2) und `BEO-TDS/messung-am-echten-sql-server-fehlt` (3×; der Mindestkatalog ist nur als Handvektor und am Spike-Testserver belegt, §6). Ohne Treffer: die übrigen sechs.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
