# ADR-0056: Die Länge einer Textspalte der Tabellenbeschreibung erreicht das COLMETADATA einer Auswahl (ersetzt teilweise ADR-0012 Entscheidung 7)

**Status:** Proposed (Empfehlung Option C, Entscheidung beim Nutzer)

**Datum:** 2026-11-19

**Autor:** Architect (im Auftrag von pt9912)

**Bezug:** [TDS-FA-11](../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-05](../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen), [TDS-QA-01](../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0008](0008-dotnet-kompatibilitaetstests.md), [ADR-0012](0012-simulations-backend-beschreibung-und-formen.md), [ADR-0033](0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), [Messprotokoll des Referenzabgleichs](../../messungen/referenzabgleich.md) (Ausnahme A-14, Messung M-18)

**Schärft:** [SPEC-035](../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) (Aussage zu `SimColumn.maxLength` und den Metadaten eines Ergebnisses), [SPEC-003](../../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog) (Unicode-Text, `ResultColumn.maxLength`), [SPEC-045](../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig) (Ausnahmeliste). Diese ADR ändert `spec/` nicht; der Nachzug steht unter Konsequenzen.

**Supersedes (teilweise), nur wörtlich benannt, wirksam erst mit dem Status `Accepted`; die Datei von [ADR-0012](0012-simulations-backend-beschreibung-und-formen.md) bleibt unverändert und `Accepted` ([AGENTS.md](../../../AGENTS.md) §3.5):**

- [ADR-0012](0012-simulations-backend-beschreibung-und-formen.md) Entscheidung 7, **nur** der Halbsatz „und nicht an den Client melden“ und der Satz „Der Client sieht `NVARCHAR(4000)`; die Abweichung gegenüber `NVARCHAR(n)` der Referenzinstanz steht auf der Ausnahmeliste von SPEC-045 (Eintrag 1 der Liste unter „Folgen“)“, **soweit** sie das COLMETADATA einer Auswahl des Simulations-Backends (Formen S1 und S2) betreffen. Es bleiben: „lesen und auf eine positive Ganzzahl prüfen“ und „nicht durchsetzen“ im Sinn der Entscheidung 2 unten (das Backend prüft die Zeilen der Zeilenquelle nicht).
- [ADR-0012](0012-simulations-backend-beschreibung-und-formen.md), Abschnitt „Folgen“, Ausnahmeliste von SPEC-045, Eintrag (1), **nur** die Angabe „Backend jeweils `NVARCHAR(4000)`“, soweit sie das COLMETADATA einer Auswahl betrifft. Es bleibt: das Backend weist einen Text über 4000 UTF-16-Einheiten ab.

**Regeln:** Baseline-Regelwerk `modul-04-adrs.md` §Ziel-Form: ADR (MADR).

---

## Kontext

[ADR-0012](0012-simulations-backend-beschreibung-und-formen.md) Entscheidung 7 (N4a A) liest `max_length` einer Spalte der Beschreibung, setzt es nicht durch und meldet es **nicht an den Client**; der Client sieht `NVARCHAR(4000)`. Die Alternative „`UnicodeText` um eine Länge erweitern“ nennt die ADR als „eigene Folge-Entscheidung“ (Offen 2). [ADR-0033](0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 2 führte `SimColumn.maxLength` ein, **damit der Katalog die Länge meldet**; sie sagt nichts über das Ergebnis einer Auswahl, und [SPEC-035](../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) hielt die Aussage von Entscheidung 7 für das Ergebnis fest („ändert die Metadaten eines Ergebnisses nicht“). Die Spezifikation steht im Rang über der ADR, kann eine angenommene ADR-Aussage aber nicht stillschweigend umkehren: ein Leser der ADR liest dann weiter „der Client sieht `NVARCHAR(4000)`“ (Accepted-ADRs überschreibt niemand, [AGENTS.md](../../../AGENTS.md) §3.5; die Klärung ist eine Folge-ADR).

Der Nutzer hat am 2026-11-18 und 2026-11-19 entschieden: **Code angleichen**, `SimColumn.maxLength` wird ins COLMETADATA einer Auswahl durchgereicht (Referenzabgleich, Ausnahme A-14: die Referenz meldet für `kaputt.Text` `nvarchar(10)` mit Länge 20, der Server dieses Repos `nvarchar(4000)` mit Länge 8000). Die Entscheidung des Nutzers deckt das Durchreichen; zwei Folgefragen hat sie nicht beantwortet und diese ADR setzt sie.

**Was gemessen ist** ([Messprotokoll](../../messungen/referenzabgleich.md), Einzelmessungen: je Instanz eine Aufnahme, ein Host, `Microsoft.Data.SqlClient` 7.1.1): A-14, die Referenz meldet an einer Textspalte einer Ansicht `nvarchar(10)` (Länge 20); M-18, der Katalog meldet für `nvarchar(max)` `max_length` -1; M-15 und M-26, `$action` ist `nvarchar(10)` (Länge 10 am `ResultColumn`). **Gelesen im Code, nicht gemessen:** `ResultColumn.maxLength` erlaubt 1 bis 4000; ein Text einer `Row` über dieser Länge endet in einem Fehler der Antwort statt im Ergebnis (`ResponseMergeTest`, Fehlernummer nicht gemessen); `BulkCopyForms.kt` bildet -1 für die Probe des Massenladens auf 4000 ab.

**Was nicht gemessen ist und hier nicht behauptet wird:** wie der echte Server das COLMETADATA einer Auswahl einer `nvarchar(max)`-Spalte schreibt (Typ, Längenfeld, Form des Wertes); ob und wie er einen Text über der Länge einer Spalte in einem Ergebnis meldet. Der zweite Fall ist an einer Tabellenspalte nicht erreichbar, weil der Server ihn schon beim Schreiben abweist (M-15: 2628 beim `MERGE`; M-11: der Client weist beim Massenladen ab); er ist nur über einen Ausdruck erreichbar, und die Form S1 liest nur Spalten.

## Entscheidung

1. **Die Länge erreicht das COLMETADATA.** Das Simulations-Backend setzt in jedem `ResultStart` einer Auswahl (Formen S1 und S2, auch im Fall „Bedingung nie wahr“) `ResultColumn.maxLength` aus `SimColumn.maxLength`. Ohne Angabe an der Spalte gilt weiter 4000. Die Länge im COLMETADATA ist `2 * maxLength` Byte wie bisher in der Abbildung von `ResultColumn.maxLength`.
2. **Zeilen über der Länge: unverändert Sache der Bibliothek (Setzung).** Das Backend prüft die Zeilen der Zeilenquelle nicht gegen `SimColumn.maxLength` (SPEC-035 bleibt). Die Regel von `ResultColumn.maxLength` gilt unverändert: ein Text einer `Row` über der Länge endet in einem Fehler der Antwort statt im Ergebnis. **Neu ist die Grenze**: sie ist die Länge der Beschreibung, nicht mehr 4000. Eine Zeilenquelle, die über der Länge ihrer eigenen Beschreibung liefert, ist ein Fehler des Integrators und wird sichtbar; sie lieferte bisher bis 4000 Zeichen. Am echten Server ist der Fall an einer Tabellenspalte nicht erreichbar (siehe Kontext); die Wirkung ist Setzung, nicht gemessen.
3. **`maxLength` -1 (`nvarchar(max)`) wird im Ergebnis als 4000 gemeldet (Setzung).** `ResultColumn.maxLength` bildet 1 bis 4000 ab; die Form `nvarchar(max)` im COLMETADATA ist nicht gebaut und nicht gemessen. Die Abbildung auf 4000 ist dieselbe wie in der Probe des Massenladens (`BulkCopyForms.kt`) und ändert das Verhalten gegenüber heute nicht (ein Text über 4000 endet schon heute in einem Fehler der Antwort). Sie trifft einen echten `nvarchar(max)` im Ergebnis nicht und ist die **bekannte Grenze** dieser Entscheidung.
4. **Die Fläche wächst nicht.** `SimColumn.maxLength` (ADR-0033 Nr. 4) und `ResultColumn.maxLength` (Änderung vom 2026-11-08) sind öffentlich; es kommt weder ein Typname noch ein Mitglied hinzu (`make flaeche` vergleicht Typnamen).

## Verglichene Alternativen

| Option | Pro | Contra |
|---|---|---|
| A — nichts tun, A-14 bleibt Ausnahme | kein Bau; ADR-0012 Nr. 7 gilt unverändert | widerspricht der Entscheidung des Nutzers (Code angleichen); die Abweichung zur Referenz bleibt |
| B — Schema der Referenz angleichen (Ansicht `kaputt` mit `nvarchar(4000)`, neue Aufnahmen) | Bibliothek bleibt; nur Fixture und Aufnahmen | vom Nutzer abgelehnt; die Abweichung bliebe für jeden Integrator mit kürzerer Beschreibung |
| **C — Länge durchreichen, -1 als 4000, Zeilen über der Länge enden in der Bibliothek im Fehler (gewählt, Empfehlung)** | kleinster Bau (zwei Stellen in `SelectExecution.kt`), keine neue Fläche, keine neue Codec-Form; -1 verhält sich wie heute | ein Integrator mit Zeilen über der eigenen Beschreibung bekommt Fehler statt Ergebnis; ein echter `nvarchar(max)` bleibt im Ergebnis `nvarchar(4000)` |
| D — wie C, aber -1 als `nvarchar(max)` im COLMETADATA | trifft den echten Server in diesem Punkt | ändert Port und Protokoll (neue Typform, PLP-Werte); nicht gemessen, kein Fixture führt sie; größerer Bau als der Anlass |
| E — wie C, aber das Backend prüft die Zeilen vorab und meldet eine eigene Fehlernummer | Fehler benennt Tabelle und Spalte (Setzung) | neue Zusage und neuer Code ohne Messung; der echte Server hat den Fall an einer Spalte nicht |

Empfehlung: **Option C**. Die Wahl liegt beim Nutzer.

## Konsequenzen

- Positiv: A-14 lässt sich durch Bau schließen (`kaputt.Text` mit `maxLength = 10`, die Regel `fehler-nach-zeilen/01/colmetadata` entfällt); die Aussage „der Client sieht `NVARCHAR(4000)`“ gilt nur noch für Spalten ohne Länge.
- Negativ: Ein Integrator, dessen Zeilenquelle über der Länge seiner Beschreibung liefert (etwa eine Datei mit `max_length` kleiner als die Daten), bekommt Fehler statt Ergebnis (Entscheidung 2).
- **Akzeptiertes Negativ:** Die Abbildung von -1 auf 4000 weicht für `nvarchar(max)` im Ergebnis vom echten Server ab. Kein Fixture und keine Aufnahme führt eine solche Spalte in einer Auswahl; deshalb steht keine Zeile auf der Ausnahmeliste. Zeigt ein Mitschnitt die Abweichung, ist es ein neuer Eintrag der Liste (Auslöser unten).
- Folgepflicht nach Annahme (Implementer, Doku, im Slice zu A-14): `SimColumn`-KDoc, SPEC-035 (Aussage „ändert die Metadaten eines Ergebnisses nicht“ ersetzen; Entscheidungen 2 und 3 als Setzung kennzeichnen), SPEC-003 (Unicode-Text im Ergebnis der Auswahl), die Zeile ADR-0012 im ADR-Index („teilweise ersetzt durch ADR-0056“), das Änderungslog, die Beschreibung von `maxLength` in `docs/user/einbettung-und-betrieb.md` und die Zeile A-14 des Messprotokolls. Die zweite Fundstelle der Aussage steht in SPEC-035 (Absatz der Datei-Abbildung, „das Metadatum eines Ergebnisses bleibt `NVARCHAR(4000)`“) und im Eintrag (1) der Ausnahmeliste von ADR-0012; beide sind mitzuziehen.
- Zu messen, um Setzungen zu belegen (kein Messlauf in dieser ADR): (a) das COLMETADATA einer Auswahl einer `nvarchar(max)`-Spalte am Referenzclient (Mitschnitt, beide Instanzen) belegt oder widerlegt Entscheidung 3; (b) der Fall von Entscheidung 2 ist an einer Tabellenspalte nicht erreichbar; eine Messung über einen Ausdruck würde die Form S1 nicht betreffen.

## Fitness Function (falls maschinell prüfbar)

| Tooling | Regel | Make-Target |
|---|---|---|
| Tests am Simulations-Backend | `SimColumn.maxLength` erscheint als `ResultColumn.maxLength` der Auswahl (S1, S2, Bedingung nie wahr); Vorgabe 4000 bleibt; -1 ergibt 4000; eine Zeile über der Länge endet in einem Fehler der Antwort, eine Zeile genau der Länge im Ergebnis | `make test` |
| Mitschnitt-Lauf (kein Gate) | die Regel `fehler-nach-zeilen/01/colmetadata` entfällt, der Lauf ist gegen die verkleinerte Liste grün | `make vergleichslauf-d LAUF=mitschnitt` |

## Re-Evaluierungs-Trigger

Ein Mitschnitt oder eine Messung des COLMETADATA einer `nvarchar(max)`-Spalte im Ergebnis liegt vor (Entscheidung 3, Option D); ein Embedder oder ein Lauf meldet Zeilen über der Länge der Beschreibung als Hindernis (Entscheidung 2, Option E); der Nutzer verwirft die Entscheidung „Code angleichen“ zu A-14.

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-11-19 | Proposed (Empfehlung Option C, Entscheidung beim Nutzer); Auslöser: die Prüfung des Slice zu A-14 fand die Aussage von ADR-0012 Entscheidung 7, die der Slice umkehrt | Auftrag des Orchestrators vom 2026-11-19 |

Nach `Accepted` wird diese Datei **nicht mehr inhaltlich überschrieben**.
Spätere Korrekturen oder Schärfungen entstehen als neue ADR mit
`Supersedes ADR-NNNN` (Baseline-Regelwerk `modul-04-adrs.md`
§Hard Rule für Accepted-ADRs).
