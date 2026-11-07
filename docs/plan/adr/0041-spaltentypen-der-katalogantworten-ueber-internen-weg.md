# ADR-0041: Spaltentypen der Katalogantworten, die `SqlType` nicht ausdrückt (`smallint`, `tinyint`), entstehen über einen internen Weg der Bibliothek

**Status:** Proposed

**Datum:** 2026-11-07

**Autor:** Architect (Entwurf); Entscheidung: pt9912 (Nutzer), ausstehend

**Bezug:** [TDS-FA-11](../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-QA-05](../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [ADR-0021](0021-oeffentlicher-integrations-einstieg.md), [ADR-0026](0026-oeffentlichkeit-simulations-backend-und-katalog.md), [ADR-0033](0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md)

**Schärft:** [SPEC-035](../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) (Katalog der Spalten: Typen der Antwortspalten), [SPEC-003](../../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog) (Abgrenzung: der Mindestkatalog von `SqlType` bleibt, Antwortspalten der Katalogformen liegen außerhalb) und bestätigt die Liste der öffentlichen Namen in [SPEC-059](../../../spec/spezifikation.md#einstieg-des-integrators-vertrag-zu-spec-059) (unverändert).

**Kein `Supersedes`.** [ADR-0033](0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) bleibt unverändert und `Accepted` ([AGENTS.md](../../../AGENTS.md) §3.5). Der Satz „Die Typen im Katalog folgen `SqlType`“ in Entscheidung 2 steht dort bei den Typnamen und Maßen der **Werte** (Messzeile c1: `integer`, `nvarchar` mit 4000, `date`, `Decimal(10,2)`), nicht bei den Drahttypen der Antwortspalten; in dieser Lesart widerspricht ihm diese ADR nicht. Teilt der Nutzer die Lesart nicht, ist diese ADR beim Annehmen ein teilweises `Supersedes` genau dieses Satzes (offene Frage unten). Entscheidung 9 („`SqlType` bleibt wie es ist“) und Entscheidung 4 (keine neuen Typnamen ohne Eintrag in der Liste) bestätigt diese ADR.

**Regeln:** Baseline-Regelwerk `modul-04-adrs.md` §Ziel-Form: ADR (MADR).

---

## Kontext

**Messung.** Die Messreihe vom 2026-11-07 ([M-16 bis M-18](../../messungen/referenzabgleich.md#messreihe-zu-massenladen-merge-katalog-und-strukturänderung-messungen-vom-2026-11-07), Referenzclient gegen SQL Server 2022 und 2025, die 17 Texte von d-migrate 1.12.0) zeigt die Antwortspalten der Katalogabfrage der Spalten: `max_length` ist `smallint` (2 Byte), `precision` und `scale` sind `tinyint`, `is_nullable` ist `bit` nullbar (`BITN`), `is_identity` `bit` nicht nullbar. Der Nutzer hat am 2026-11-07 entschieden, dass der Bau dem echten Server folgt (Spezifikation ist Zielbild, der echte Server die Referenz).

**Was heute trägt und was nicht.** `ResultColumn.type` ist `SqlType` ([SPEC-003](../../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog)): `Int32`, `Int64`, `Bool`, `UnicodeText`, `Decimal`, `Date`, `Time`, `DateTime`. Daraus folgt:

| Antwortspalte (M-16) | Ausdrückbar mit `SqlType` | Folge |
|---|---|---|
| `is_nullable` (`BITN`), `is_identity` (`BIT`) | ja: `Bool` mit `nullable = true` bzw. `false` | kein Gegenstand dieser Entscheidung |
| `max_length` (`smallint`), `precision`, `scale` (`tinyint`) | **nein** (nur `Int32`, heute gebaut) | Gegenstand |
| `type_name` `nvarchar(256)`, `default_definition` `nvarchar(max)`, `seed_value`/`increment_value` `sql_variant` | nein (`UnicodeText` ist fest `NVARCHAR(4000)`, `sql_variant` fehlt) | nicht Gegenstand des Auftrags vom 2026-11-07, aber derselbe Mangel; der Weg dieser Entscheidung soll sie tragen können |

**Wirkung auf Clients: nicht gemessen.** Alle Läufe von d-migrate gegen den Testserver (Spike und Katalog-Messung, [Messprotokoll Spike](../../messungen/callback-loesung-spike-2026-11-05.md), [Messprotokoll Katalog und DDL-Zustand](../../messungen/katalog-und-ddl-zustand-2026-11-06.md)) liefen mit `int` statt `smallint`/`tinyint` bis Exit 0 und `IDENTICAL`. Belegt ist nur das; ob ein Client mit strenger Typprüfung die `int`-Werte anders liest, ist nicht gemessen (so steht es im zweiten Protokoll). Der Quelltext von d-migrate liegt nicht im Repo; ob es die Werte mit Zahl-Gettern liest (die ein JDBC-Treiber umwandelt) oder die Spaltentypen auswertet, ist **Annahme, nicht gemessen** ([AGENTS.md](../../../AGENTS.md) §3.8). Wer die Angleichung hier rechtfertigt, rechtfertigt sie mit dem Zielbild (Nutzergrundsatz), nicht mit einem belegten Fehler eines Clients.

**Randbedingung.** [ADR-0033](0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 9 hält `SqlType` fest und verlangt für Erweiterungen eine Folge-ADR; Nr. 4 und [ADR-0026](0026-oeffentlichkeit-simulations-backend-und-katalog.md) Entscheidung 4 verlangen, dass der Testserver nur gegen die öffentliche Fläche übersetzt und ein weiterer nötiger öffentlicher Name ein Befund ist. Der Bau-Slice zur Angleichung des Katalogs hat die Rückführung „Fläche wächst“ vorab benannt. Die Katalogformen (`CatalogForm`) und ihre Antwortspalten sind `internal`; öffentlich ist nur, was `ResultStart` mit `ResultColumn` über den Port trägt.

**Annahme, die kippt:** Dass sich die Spaltenbeschreibung des Katalogs von der des Ports trennen lässt, ohne den Sequenzierer zu verbiegen. Der Codec kennt `INTN` mit 1, 2, 4 und 8 Byte bereits (`TdsType.IntNType`); nicht gelesen ist, wie der Wertschreiber die schmalen Größen aus der Spaltenbeschreibung bedient. Das ist der Aufwand der Option B und vom Bau zu belegen.

## Entscheidung

Wir wählen **Option B**: Antwortspalten der Katalogformen, die `SqlType` nicht ausdrückt, beschreibt die Bibliothek auf einem **nur intern sichtbaren Weg** vom Simulations-Backend zum Sequenzierer (Spaltenbeschreibung auf Ebene des Drahttyps). `SqlType`, `ResultColumn`, `QueryEvent`, `QueryBackend` und `QueryExecution` bleiben unverändert, es entsteht kein öffentlicher Name.

1. **Umfang.** Gebaut werden die gemessenen Typen von M-16: `smallint` (`INTN`, 2 Byte) für `max_length`, `tinyint` (`INTN`, 1 Byte) für `precision` und `scale`; `is_nullable` und `is_identity` bleiben `Bool` (nullbar bzw. nicht). Die weiteren Typen von M-16 (`nvarchar(256)`, `nvarchar(max)`, `sql_variant`) gehören zu den Sichten für Indizes, Standardwerte und Fremdschlüssel; sie benutzen denselben Weg, ohne dass dafür eine weitere Entscheidung nötig ist, solange er intern bleibt.
2. **Grenzen des Weges (Zusage der Bauart).** (a) Keine neue Unterklasse eines öffentlichen `sealed`-Typs (`SqlType`, `QueryEvent`): ob ein Kotlin-`when` eines Integrators dadurch nicht mehr erschöpfend wäre, ist nicht geprüft; der Bau darf eine Unterklasse nur wählen, wenn eine Übersetzungsprobe im Source-Set `integrator` (ohne `internal`) das Gegenteil zeigt. (b) Eigene Backends (`BackendSource.Custom`) sehen nichts davon. (c) Werte dieser Spalten entstehen in der Bibliothek aus Zahlen der Beschreibung; ihr Bereich (`tinyint` 0 bis 255, `smallint` −32768 bis 32767) wird geprüft, ein Wert außerhalb ist ein Fehler der Bibliothek, kein stilles Abschneiden. Die Katalogwerte liegen durch Konstruktion im Bereich (Längen bis 8000 und −1, Precision und Scale bis 38); die Grenzen sind getestet.
3. **Zusage in der Spezifikation.** `SPEC-035` nennt den Drahttyp der Antwortspalten als Zusage der Antwort (Wortlaut folgt dem Messwert; die „bekannte Abweichung `Int32`“ entfällt); `SPEC-003` grenzt ab: der Mindestkatalog betrifft Backends und Parameter, nicht die Antwortspalten der Katalogformen.
4. **Weg bei Nutzung durch Dritte.** Braucht ein Einbettender `smallint` oder `tinyint` für eigene Ergebnisse oder Ausgabewerte, ist das eine eigene Entscheidung (öffentliche Mitglieder von `SqlType`, Option A); sie ist durch diese ADR nicht vorweggenommen und nicht verbaut.

## Verglichene Alternativen

| Option | Pro | Contra |
|---|---|---|
| A — neue öffentliche Mitglieder `SqlType.Int16` und `SqlType.UInt8` (Namen Setzung) | ein einziger Weg für Katalog, Backends und Ausgabewerte; kein zweiter Mechanismus; Java sieht nur zwei weitere verschachtelte Klassen | öffentliche Fläche wächst (SPEC-059, `make flaeche`, [ADR-0033](0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 4: „nur, was man dafür braucht“ — der Einbettende braucht sie nicht); [ADR-0033](0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 9 verlangt dafür ohnehin eine ADR; ein Kotlin-`when` über `SqlType` in fremdem Code bricht beim Übersetzen (bei `sealed`, nicht geprüft aber zu erwarten); jede Stelle der Bibliothek, die `SqlType` auswertet (Typabbildung, Wertabbildung, Parameter, Massenladen), muss die zwei Fälle behandeln oder ablehnen; `SPEC-003` wächst über den Mindestkatalog; jedes eigene Backend muss sie kennen |
| **B — interner Spaltenweg (gewählt)** | keine öffentliche Änderung, `make flaeche` und der Einbettungstest bleiben grün; [ADR-0033](0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 9 und Nr. 4 bleiben wörtlich wahr; derselbe Weg trägt die weiteren Typen von M-16 und der Sichten (Mehrfachnutzen); Wirkung beschränkt sich auf die Bibliothek | ein zweiter Mechanismus neben `ResultColumn` (Wartbarkeit: er braucht einen Test, der ihn an den Katalogformen festhält); Aufwand im Sequenzierer und im Wertschreiber (nicht gelesen); unterscheidet Antworten des Simulations-Backends von denen eigener Backends |
| C — Zustand lassen: `max_length`, `precision`, `scale` bleiben `Int32`, als benannte Grenze | kein Aufwand; belegt ist, dass d-migrate damit bis Exit 0 läuft; keine Fläche, kein Mechanismus | widerspricht dem Zielbild und dem Nutzerentscheid vom 2026-11-07; die Antwortspalte unterscheidet sich am Draht vom echten Server (Referenzclient sieht `int`); die Grenze wächst mit jeder weiteren Sicht |
| D — Teilmenge: nur `is_nullable` als `BITN` (trägt heute), den Rest als Grenze | billig; löst, was `SqlType` schon kann | verschiebt die eigentliche Frage; das Ergebnis ist C für drei der vier Spalten; die Teilmenge ist in B enthalten und braucht keine eigene Entscheidung |

**Bewertung nach den Kriterien.** Sicherheit/[TDS-QA-07](../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität): A und B ändern nur Bytes der Antwortspalten; kein Einfluss auf Synchronität, solange die Bereichsprüfung greift (Entscheidung 2c); B hat keine Eingabe von außen. Öffentliche Fläche: A +2 Namen, B und C 0. Java-Lesart: A neutral, B und C ohne Wirkung. Wartbarkeit: A einfach, aber breite Änderung; B zweiter Mechanismus, klein gehalten durch Beschränkung auf die Katalogformen. Zielbild: A und B erreichen es für die Katalogspalten, C nicht. Aufwand: C null, D klein, B mittel, A groß (alle Auswerter von `SqlType`). Kopplung: B schafft den Weg, den die Entscheidung zur Anweisungsart ([ADR-0042](0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md)) ebenfalls braucht; wer von beiden Slices zuerst baut, legt ihn an. Relevanz für d-migrate: für A, B und C nicht belegt (siehe Kontext).

## Konsequenzen

- Positiv: Zielbild für die Katalogspalten ohne neue öffentliche Fläche; die Zusagen von [ADR-0033](0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 4 und Nr. 9 bleiben unberührt; der Weg trägt die Sichten der Folge-Arbeit.
- Negativ: ein zweiter Mechanismus in der Bibliothek; die Wirkung auf d-migrate bleibt unbekannt, bis ein Lauf (`schema reverse`, `compare`, `schema migrate --execute`) nach dem Bau sie zeigt. Fällt dieser Lauf rot aus, ist das ein Befund über die Annahme in „Kontext“, nicht über diese Entscheidung allein.
- Folgepflicht Bau: der Bau-Slice zur Angleichung des Katalogs setzt den Punkt „Katalogspalten“ nach Entscheidung 1 bis 3 um; die Rückführung „Fläche wächst“ entfällt für diesen Punkt (nur dann, wenn `make flaeche` unverändert bleibt). Der Bau-Slice zu den Zielbild-Lücken der Antwort-Tokens ist von dieser ADR nicht berührt.
- Folgepflicht Spezifikation: `SPEC-035` und `SPEC-003` wie in Entscheidung 3; erst nach `Accepted`.

## Fitness Function (falls maschinell prüfbar)

| Tooling | Regel | Make-Target |
|---|---|---|
| Typnamen-Sensor der Fläche | die Liste der öffentlichen Typen in `SPEC-059` bleibt gleich (kein neuer Name) | `make flaeche` |
| Einbettungstest im Source-Set `integrator` | übersetzt und läuft unverändert gegen die öffentliche Fläche (ein Kotlin-`when` über `SqlType` und `QueryEvent` bleibt erschöpfend) | `make test` |
| Tests der Katalogformen | COLMETADATA der Antwort: Typ, Länge, Nullbarkeit je Spalte gegen die Zeilen M-16; Grenzwerte von `tinyint` und `smallint` | `make test` |

## Re-Evaluierungs-Trigger

Ein Einbettender oder ein Backend braucht `smallint`/`tinyint` in eigenen Ergebnissen (dann Option A als Folge-ADR); der Trigger von [ADR-0033](0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 9 (FLOAT) tritt ein und eine gemeinsame Erweiterung von `SqlType` ist ohnehin fällig; ein Lauf mit d-migrate oder dem Referenzclient zeigt, dass der Drahttyp der Antwortspalten etwas ändert; der Bau des internen Weges kostet mehr als eine Erweiterung von `SqlType` (dann zurück zu A oder C).

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-11-07 | Proposed (Entwurf des Architects) | [Messprotokoll](../../messungen/referenzabgleich.md#messreihe-zu-massenladen-merge-katalog-und-strukturänderung-messungen-vom-2026-11-07), Zeilen M-16 bis M-18 |

Nach `Accepted` wird diese Datei **nicht mehr inhaltlich überschrieben**.
Spätere Korrekturen oder Schärfungen entstehen als neue ADR mit
`Supersedes ADR-NNNN` (Baseline-Regelwerk `modul-04-adrs.md`
§Hard Rule für Accepted-ADRs).
