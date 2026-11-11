# ADR-0046: Öffentliche Fläche der Katalog-Elemente: vier Typen (`SimUniqueKey`, `SimForeignKey`, `SimIndex`, `SimCheck`), fünf Felder und der Callback `onStructure` mit einer `SimTable`

**Status:** Accepted (vom Nutzer entschieden)

**Datum:** 2026-11-11

**Autor:** Architect (Entwurf); Entscheidung: pt9912 (Nutzer)

**Entscheidung des Nutzers (Quelle):** 2026-11-11, per Auswahl: „Behalten, per Folge-ADR festhalten“ (Option A); die Fläche ist gebaut, die Entscheidung folgt dem Bau.

**Bezug:** [TDS-FA-11](../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-05](../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [ADR-0021](0021-oeffentlicher-integrations-einstieg.md), [ADR-0026](0026-oeffentlichkeit-simulations-backend-und-katalog.md), [ADR-0033](0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), [ADR-0041](0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md), [ADR-0042](0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md)

**Schärft:** [SPEC-035](../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) (Beschreibung der Tabelle: Eindeutigkeiten, Fremdschlüssel, Indizes, Prüfbedingungen, Standardwert der Spalte; Callback der Strukturänderung), [SPEC-036](../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) (Anweisungsformen, die diese Elemente anlegen und entfernen; Antworten der Katalogabfragen) und [SPEC-059](../../../spec/spezifikation.md#einstieg-des-integrators-vertrag-zu-spec-059) (die Liste der öffentlichen Namen).

**Supersedes (teilweise), nur wörtlich benannt:** [ADR-0033](0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Entscheidung 4, **ausschließlich** der Satz „Es entsteht kein neuer Typname.“ sowie die dortige Aufzählung der neuen öffentlichen Namen als abschließende Liste (Fassung F: Parameter des Strukturänderungs-Callbacks `schema`, `name`, `columns`, `primaryKey`). Alles Übrige in ADR-0033 bleibt unberührt. ADR-0033 und [ADR-0041](0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) bleiben unverändert und `Accepted` ([AGENTS.md](../../../AGENTS.md) §3.5). ADR-0041 Entscheidung 7 wird nicht berührt: `SqlType` wächst hier nicht.

**Regeln:** Baseline-Regelwerk `modul-04-adrs.md` §Ziel-Form: ADR (MADR).

---

## Kontext

**Anlass.** Der Bau zu den Katalogsichten für Indizes, Standardwerte, Fremdschlüssel, Prüfbedingungen und Eindeutigkeiten hat die öffentliche Fläche erweitert, ohne dass eine Entscheidung vorlag, obwohl der zugehörige Plan sie vor dem Bau verlangte. Der Review vom 2026-11-09 nennt das als HIGH (F-1: Fläche wächst ohne Entscheid) und überlässt die Auflösung dem Nutzer. Der Nutzer hat am 2026-11-11 entschieden, die gebaute Fläche zu behalten und sie in dieser Folge-ADR festzuhalten. Eine Entscheidung nach dem Bau ist kein Normalfall; sie ist die ehrliche Form, wenn der Bau der Entscheidung vorausging, und sie geschieht hier ohne Änderung an ADR-0033.

**Warum die Fläche gebraucht wird.** Das Katalog-Backend muss Eindeutigkeiten, Fremdschlüssel, Indizes, Prüfbedingungen und Standardwerte kennen, damit der Prüfclient d-migrate (`schema reverse`, `schema compare`, `schema migrate`) gegen den Testserver verläuft wie gegen den echten Server ([TDS-FA-12](../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate); Zielbild: der echte Server ist die Referenz). Die Beschreibung der Tabelle ([SPEC-035](../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends)) trug bis dahin Spalten und Primärschlüssel; die Zusage „alles andere antwortet leer“ widerspricht der Messung M-21 ([Messprotokoll](../../messungen/referenzabgleich.md)).

**Gemessen** (M-21, Rohbytes beider Instanzen, 2022 und 2025, je ein Fall): die Antwortformen der sechs Abfragen (Typen, Nullbarkeit, Spaltenfolge), die Zeilen des einen Falls je Element, `key_ordinal` und `constraint_column_id` ab 1, die Form von `default_definition` (`((1))`, `(N'neu')`), die Form der Prüfbedingung für `Menge > 0` (`([Menge]>(0))`), dass die Eindeutigkeit auch in der Indexabfrage erscheint (`is_unique` 1) und der Primärschlüssel nicht. Den Gebrauch durch d-migrate belegt der Lauf `schema migrate --execute` mit anschließendem `schema compare` `IDENTICAL` gegen den Testserver (Messprotokoll zum Bau).

**Setzungen, nicht gemessen** ([AGENTS.md](../../../AGENTS.md) §3.8): mehrspaltige Elemente (nur Schlüssel mit einer Spalte gemessen); die Form des Servers für mehrere Vergleiche in einer Prüfbedingung, für Klammern und Texte; die Sortierung der Namen ohne Beachtung der Schreibweise (der Server ordnet nach der Collation); die Fehlernummern der neuen Abweisungen; die Abschlusskennungen von `CREATE INDEX` und `ADD CONSTRAINT`. Diese Punkte sind im Bau als Setzung gekennzeichnet; die ADR entscheidet sie nicht und erhebt sie nicht zum Beleg.

**Was gebaut ist** (Fläche):

- vier neue öffentliche Typen: `SimUniqueKey`, `SimForeignKey`, `SimIndex`, `SimCheck`;
- fünf neue öffentliche Felder: `SimColumn.defaultDefinition`, `SimTable.uniqueKeys`, `SimTable.foreignKeys`, `SimTable.indexes`, `SimTable.checks` (Standardwert leer bzw. `null`, bestehende Aufrufe der Konstruktoren übersetzen weiter);
- eine **inkompatible** Änderung: der Callback `onStructure` des zweiten Konstruktors von `SimulationBackend` ist `(SimTable) -> Unit` statt `(schema, name, columns, primaryKey) -> Unit`.

## Entscheidung

Wir wählen **Option A**: Die gebaute Fläche bleibt wie gebaut: die vier Typen `SimUniqueKey`, `SimForeignKey`, `SimIndex`, `SimCheck`, die fünf Felder `SimColumn.defaultDefinition`, `SimTable.uniqueKeys`, `SimTable.foreignKeys`, `SimTable.indexes`, `SimTable.checks` und die Signatur `onStructure: (suspend (SimTable) -> Unit)?`.

1. **Neue Typnamen.** Die vier Typnamen gehören in die Liste von [SPEC-059](../../../spec/spezifikation.md#einstieg-des-integrators-vertrag-zu-spec-059) (sie stehen dort und sind durch `make flaeche` belegt). ADR-0033 Entscheidung 4 sagte „Es entsteht kein neuer Typname“ für die damalige Menge; dieser Satz wird durch diese ADR für die Katalog-Elemente abgelöst. Die Namen folgen der Konvention `Sim*` (`SimTable`, `SimColumn`, `SimQuery`, `SimCatalog`).
2. **Beschreibung der Tabelle.** `SimTable` trägt die vier Listen, `SimColumn` den Standardwert als den Text, den der Katalog als `default_definition` meldet (mit den Klammern des Servers, M-21). Die Form der Elemente ist die, die die Katalogabfragen brauchen; Einzelheiten (Pflichtfelder, Prüfungen im Konstruktor) stehen in `SPEC-035`.
3. **Callback.** `onStructure` bekommt die Tabelle im Zustand nach der Anweisung als eine `SimTable`. Das ist der Zuschnitt, der die Elemente ohne weitere Parameter trägt; vier Parameter plus vier Listen wären nicht mehr lesbar. Der Callback bleibt ein Funktionstyp (Fassung F von ADR-0033 Nr. 4), nur mit dem Typ der Tabelle als Parameter.
4. **Inkompatibilität.** Jeder Aufrufer des zweiten Konstruktors mit einem Lambda aus vier Parametern bricht beim Übersetzen. Im Repo betrifft das nur `TableStore` (Testserver); er ist mitgezogen. Nach [ADR-0021](0021-oeffentlicher-integrations-einstieg.md) gilt die Zusage vor der ersten Veröffentlichung des Jars eng; bis dahin trifft der Bruch keinen Dritten, der sich auf sie verlassen hat (Setzung, wie in ADR-0041 Nr. 4). Die Spezifikation benennt die Änderung im Änderungsprotokoll von `SPEC-059`.
5. **Namen und Typen sind Setzung.** Die Namen und der Zuschnitt sind Setzungen des Baus (der echte Server legt keine Namen der öffentlichen Fläche fest). Fremdschlüssel-Aktionen sind `String` (`NO_ACTION`, `CASCADE`, `SET_NULL`, `SET_DEFAULT`), um keinen fünften Typnamen einzuführen; das ist so gesetzt und hier nicht erneut entschieden.

**Begründung.** Zielbild: die Spezifikation ist das Zielbild, die Sichten des echten Servers sind die Referenz, und der Backend-Anbieter muss die Elemente liefern können, weil die Bibliothek die Abfragen sonst nicht beantworten kann. Ein einziger Weg über `SimTable` und `SimColumn` für Katalog, Strukturänderung und Beschreibung; kein zweiter Mechanismus neben der Beschreibung.

## Verglichene Alternativen

| Option | Pro | Contra |
|---|---|---|
| **A — Fläche wie gebaut behalten, per Folge-ADR festhalten (gewählt, Entscheid 2026-11-11)** | kein Rückbau; ein Weg für Beschreibung, Katalog und Callback; die Messung M-21 und der Lauf mit d-migrate stützen die Antwortformen; der Review hat Namen und Zuschnitt als dem Muster `Sim*` folgend und als kleinsten Zuschnitt für vier Abfragen mit eigener Zeilenform beurteilt | die Fläche wächst um vier Typen, fünf Felder und eine inkompatible Signatur; ein Entscheid nach dem Bau (der Plan verlangte ihn vorher); `make flaeche` fängt nur Typnamen (siehe Konsequenzen) |
| B — kleinerer Zuschnitt: `SimUniqueKey` als Flag an `SimIndex` (verworfen) | ein Typname weniger | Grund des Reviews: die Abfrage der Eindeutigkeiten und der Namensraum der Bedingungen unterscheiden Eindeutigkeit und Index; ein Flag vermischt beide, und die Antwort der Eindeutigkeiten (eigene Form, M-21) müsste aus einem Index-Flag abgeleitet werden. Der Gewinn ist ein Name |
| C — nur intern im Testserver, nicht in der Bibliothek (verworfen) | keine öffentliche Änderung | der Katalog-Anbieter ist die Bibliothek (ADR-0033, ADR-0026): sie beantwortet die Abfragen aus der Beschreibung des Integrators; liegt die Fläche nur im Testserver, kann ein eigenes Backend die Elemente nicht liefern. ADR-0026 Entscheidung 4 verlangt zudem, dass der Testserver nur gegen die öffentliche Fläche übersetzt |
| D — Rückbau auf den Stand von ADR-0033 (nicht gewählt) | wörtlich ADR-0033 | verwirft gemessene und gebaute Zielbild-Arbeit; die Sichten antworten wieder leer |

## Konsequenzen

- Positiv: `schema reverse`, `compare` und `migrate` von d-migrate laufen gegen den Testserver mit Elementen; eigene Backends können sie melden; die Fläche ist entschieden statt stillschweigend gewachsen; ADR-0033 und ADR-0041 bleiben unverändert.
- Negativ: größere öffentliche Fläche; der Bruch am Callback `onStructure` für Aufrufer mit vier Parametern (im Repo nur `TableStore`, mitgezogen).
- **Lücke im Sensor, benannt:** `make flaeche` vergleicht nur die Typnamen der Liste in `SPEC-059` mit den Deklarationen und ihrer Verwendung; es vergleicht **keine Mitglieder und keine Signatur**. Die fünf Felder und die Signatur von `onStructure` fängt allein der Einbettungstest `integrator` (er übersetzt gegen die neue Form). Akzeptiertes Negativ: die Fläche ist klein, der Einbettungstest bricht bei jeder Abweichung beim Übersetzen, und ein Mitglieder-Vergleich wäre ein eigenes Werkzeug mit eigenem Träger, das für diese Lücke nicht lohnt. Wer ihn will, trifft eine eigene Entscheidung.
- **Akzeptiertes Negativ der Setzungen** (Kontext): mehrspaltige Elemente, die Server-Form mehrerer Vergleiche, die Namenssortierung, die Fehlernummern und die Abschlusskennungen sind nicht gemessen. Sie sind im Bau gekennzeichnete Setzungen ohne Wirkung auf die gemessenen Fälle; diese ADR erhebt sie nicht zum Beleg und verlangt keinen eigenen Träger. Fällt ein Lauf gegen den echten Server anders aus, ist das ein Befund zur Setzung, nicht zu dieser Entscheidung.
- **Offene Punkte aus dem Review, die diese ADR nicht entscheidet** (nur genannt): (a) F-6: Namen von Bedingungen gelten im Bau je Tabelle (Setzung), am echten Server nach seiner Dokumentation je Schema (nicht gemessen); d-migrate vergibt Namen ohne Tabelle, zwei Tabellen im selben Schema können dort kollidieren, wo der Testserver beide annimmt. (b) `SimForeignKey.onDelete` heißt wie der Callback `SimTable.onDelete`, mit anderer Bedeutung; eine Umbenennung wäre eine eigene Entscheidung. (c) Fremdschlüssel-Aktionen sind `String` statt eines Typs; nur in Nr. 5 als Setzung festgehalten. Träger dieser Punkte bleibt der Review-Befund; diese ADR schafft keinen neuen.
- Folgepflicht Spezifikation: `SPEC-035`, `SPEC-036` und `SPEC-059` stehen im Feld „Schärft“ und nennen die Fläche bereits. Die Spezifikation nennt keine ADR-Kennung; der Bezug läuft über das Feld „Schärft“ dieser ADR.

## Fitness Function (falls maschinell prüfbar)

| Tooling | Regel | Make-Target |
|---|---|---|
| Typnamen-Sensor der Fläche | die vier Typnamen stehen in der Liste von `SPEC-059`, jede öffentliche Deklaration hat einen Eintrag, jeder Eintrag einen Beleg; **Mitglieder und Signaturen fängt er nicht** | `make flaeche` |
| Einbettungstest im Source-Set `integrator` | übersetzt gegen die öffentliche Fläche: Felder von `SimTable` und `SimColumn`, `onStructure` mit einer `SimTable` | `make test` |

## Re-Evaluierungs-Trigger

Ein Lauf gegen den echten Server zeigt, dass eine Setzung (Kontext) abweicht und die Form der Elemente ändert; Namen von Bedingungen je Schema werden gemessen und verlangen eine andere Eindeutigkeit (F-6); ein Integrator meldet den Bruch von `onStructure` als Hindernis; die erste Veröffentlichung des Jars steht an (dann ist die Inkompatibilität nicht mehr billig); die Fläche wächst weiter und ein Mitglieder-Vergleich lohnt.

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-11-09 | Befund F-1 im Review: Fläche wächst ohne Entscheid | Review vom 2026-11-09 zum Bau der Katalog-Elemente |
| 2026-11-11 | Accepted (Entscheidung des Nutzers per Auswahl: „Behalten, per Folge-ADR festhalten“, Option A) | Nutzerentscheid vom 2026-11-11 |

Nach `Accepted` wird diese Datei **nicht mehr inhaltlich überschrieben**.
Spätere Korrekturen oder Schärfungen entstehen als neue ADR mit
`Supersedes ADR-NNNN` (Baseline-Regelwerk `modul-04-adrs.md`
§Hard Rule für Accepted-ADRs).
