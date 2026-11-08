# Slice slice-katalogspalten-smallint-tinyint-angleichen: Katalogspalten `max_length` (`smallint`), `precision` und `scale` (`tinyint`) und `is_nullable` (`BITN`) an die Messung angleichen: öffentliche Mitglieder `SqlType.Int16` und `SqlType.UInt8` (M-16, M-17, M-18)

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-testserver-manuell

**Bezug:** [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md), [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md), [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), [ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md)

**Berührte Spec-Stellen:** SPEC-003 (Mindestkatalog von `SqlType` wächst um `Int16` und `UInt8`) · SPEC-035 (Katalog der Spalten: Drahttypen der Antwortspalten; die „bekannte Abweichung `Int32`“ entfällt) · SPEC-059 (Liste der öffentlichen Namen: `SqlType` mit `Int16` und `UInt8`; Änderungsprotokoll: inkompatibel für ein erschöpfendes `when` über `SqlType`). **Die Spezifikation ändert dieser Slice** (Zielbild: Wortlaut folgt dem Messwert).

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-08

---

## 1. Ziel und Abgrenzung


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Der Server dieses Repos antwortet auf die Katalogabfrage der Spalten von d-migrate 1.12.0 mit den Spaltentypen des echten SQL Servers (2022 und 2025) nach den Zeilen M-16 bis M-18 des [Messprotokoll](../../../messungen/referenzabgleich.md#messreihe-zu-massenladen-merge-katalog-und-strukturänderung-messungen-vom-2026-11-07): `max_length` als `smallint` (2 Byte), `precision` und `scale` als `tinyint`, `is_nullable` als `bit` nullbar (`BITN`), `is_identity` als `bit` nicht nullbar. Dazu gibt es nach [ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) (Option A, Nutzerentscheid 2026-11-08) die öffentlichen Mitglieder `SqlType.Int16` (Draht: `INTN` mit 2 Byte) und `SqlType.UInt8` (Draht: `INTN` mit 1 Byte); die Antwortspalten der Katalogformen benutzen sie wie jede andere Spalte, es entsteht kein zweiter Mechanismus neben `ResultColumn`.

**Herkunft.** Aus dem Zuschnitt des Slice [slice-katalog-und-struktur-an-messung-angleichen](../done/slice-katalog-und-struktur-an-messung-angleichen.md) vom 2026-11-08 (dessen früherer Liefer-Punkt „Katalogspalten“), den Zeilen M-16, M-17, M-18 des [Messprotokoll](../../../messungen/referenzabgleich.md#messreihe-zu-massenladen-merge-katalog-und-strukturänderung-messungen-vom-2026-11-07) und dem Register [`BEO-TDS/katalog-strukturabweisung-und-aenderungszaehler-weichen-vom-echten-server-ab`](../observations/BEO-TDS/katalog-strukturabweisung-und-aenderungszaehler-weichen-vom-echten-server-ab/state.md). Nutzerentscheid 2026-11-07 (Katalogspalten `smallint`, `tinyint`, `bit` nullbar) und 2026-11-08 ([ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) `Accepted`, Option A). Die Spezifikation ist Zielbild, der echte SQL Server die Referenz.

**Reihenfolge und Fläche.** Dieser Slice ändert **jede Stelle, die `SqlType` auswertet** ([ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) Entscheidung 5: Typmodell, Typabbildung der Spalten, Wertschreiber, Ausgabewerte, Werte der Simulation, Katalog, Massenladen, Planer, Bestand im Testserver, Tests, Einbettungstest). Er berührt damit viele Dateien und kollidiert mit **jedem** anderen Zweig, der diese Dateien ändert. Deshalb startet er allein (WIP 1 für Bau-Slices an `SqlType`), nach [slice-katalog-und-struktur-an-messung-angleichen](../done/slice-katalog-und-struktur-an-messung-angleichen.md) und nach den parallelen Zweigen zu [slice-bulk-load-feste-typen-und-referenzclient](../done/slice-bulk-load-feste-typen-und-referenzclient.md), [slice-schreibpfad-merge-output](../done/slice-schreibpfad-merge-output.md) und [slice-sp-unprepare-ohne-obergrenze](../done/slice-sp-unprepare-ohne-obergrenze.md), und **vor** [slice-antwort-tokens-zielbild-luecken-bauen](../next/slice-antwort-tokens-zielbild-luecken-bauen.md) und [slice-ddl-abschluss-und-transaktionszustand-angleichen](slice-ddl-abschluss-und-transaktionszustand-angleichen.md) (siehe §4).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Die weiteren Typen von M-16: `nvarchar(256)` (`type_name`), `nvarchar(max)` (`default_definition`) und `sql_variant` (`seed_value`, `increment_value`) — benannte Grenze nach [ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) Entscheidung 7; [slice-katalog-indizes-defaults-fremdschluessel](slice-katalog-indizes-defaults-fremdschluessel.md) braucht sie und entscheidet dort, ob `SqlType` weiter wächst oder die Spalten eine andere Form bekommen.
- Die Erzeuger von `SqlType` aus dem Draht (`RpcParameters`, `BulkLoadReader`): `INTN` mit 1 oder 2 Byte als Parameter bleibt ein Typfehler (Setzung nach [ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) Entscheidung 5, kein Lastenheft-Punkt verlangt es).
- Die Typnamen `smallint` und `tinyint` im Bestand des Testservers: der Leser der Tabellenbeschreibung nimmt sie nicht auf (Setzung nach [ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) Entscheidung 6, wie `float`).
- Strukturabweisung und `CREATE TABLE` ohne Schlüssel — [slice-katalog-und-struktur-an-messung-angleichen](../done/slice-katalog-und-struktur-an-messung-angleichen.md); Abschluss der Strukturänderung und Transaktionstexte — [slice-ddl-abschluss-und-transaktionszustand-angleichen](slice-ddl-abschluss-und-transaktionszustand-angleichen.md); Anweisungsart am Port — [slice-antwort-tokens-zielbild-luecken-bauen](../next/slice-antwort-tokens-zielbild-luecken-bauen.md).
- Eine Aussage über andere Clients als den Referenzclient (`AGENTS.md` §3.8).

## 2. Definition of Done


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **Öffentliche Mitglieder und alle Auswerter** ([TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) Entscheidung 1, 3, 4, 5): `SqlType.Int16` und `SqlType.UInt8` (Setzung der Namen), JVM-Klasse der Werte `Int`, Bereichsprüfung bei der Abbildung (`Int16` −32768 bis 32767, `UInt8` 0 bis 255; ein Wert außerhalb ist ein Fehler des Backends, kein Abschneiden); jede Stelle, die `SqlType` auswertet, kennt die neuen Fälle (die Suche nach Auswertern mit `==`, die der Übersetzer nicht fängt, ist Teil des Baus, Stand der Suche in [ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) Entscheidung 5 ist nicht erschöpfend); `SPEC-059` nennt beide Namen in der Liste, Änderungsprotokoll mit dem Vermerk „inkompatibel für ein Kotlin-`when` ohne `else` über `SqlType`“, `SPEC-003` nimmt sie in den Mindestkatalog auf; der Einbettungstest `integrator` meldet je Typ eine Spalte und zieht sein erschöpfendes `when` nach; `make flaeche` grün; Tests der Grenzwerte (`UInt8`: 0, 255, 256, −1; `Int16`: −32768, 32767, ±1 darüber).
- [ ] **Katalogspalten** ([TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)): In der Antwort der Katalogabfragen von d-migrate 1.12.0 sind `max_length` `smallint`, `precision` und `scale` `tinyint`, `is_nullable` `BITN`, `is_identity` `bit` nicht nullbar (M-16); `SPEC-035` nennt den Typ der Spalte statt der „bekannten Abweichung `Int32`“; je Spalte ein Test gegen das COLMETADATA (Typ, Länge, Nullbarkeit); die Maße von `bigint`, `bit`, `time`, `datetime2` und der Sonderwert −1 (M-17, M-18) bleiben unverändert (die Messung stützt sie); Zeile M-16 der Zuordnung im Messprotokoll ist auf „angeglichen“ gezogen.
- [ ] **Katalogmaße von `smallint` und `tinyint`** ([TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), `AGENTS.md` §3.8; [ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) Entscheidung 2): Die Maße, die der Katalog für eine Spalte dieser Typen meldet (heute nur Entwurf: `smallint` Länge 2, Precision 5, Scale 0; `tinyint` Länge 1, Precision 3, Scale 0), sind in diesem Repo nicht gemessen. **Der Nutzer entscheidet vor dem Bau:** (a) Nachmessung mit dem Referenzclient gegen den echten Server (2022 und 2025, lokal, EULA bestätigt; neue Zeile der Messreihe im [Messprotokoll](../../../messungen/referenzabgleich.md#messreihe-zu-massenladen-merge-katalog-und-strukturänderung-messungen-vom-2026-11-07), Testfall in `referenztest-dotnet`, Ablage der Rohbytes), dann folgt der Bau dem Messwert; oder (b) Setzung, im Wortlaut der Spezifikation als „Setzung, nicht gemessen“ gekennzeichnet. Wegen der Setzung zum Bestand (kein `smallint`/`tinyint` in Tabellen des Testservers, s. o.) ist der Wert heute nur über eigene Backends erreichbar.

Nachweis-Pflichten für alle Punkte:

- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Haken der Liefer-Punkte setzt der Planner nach der Verifikation, nicht der Implementer (`BEO-TDS/haken-vor-verifikation-gesetzt`).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

## 3. Plan (vor Code)


Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/domain/` (`SqlType`) | update | zwei neue öffentliche Mitglieder |
| `src/main/kotlin/io/github/pt9912/tdswire/` (Typabbildung `TypeInfoWriter`, `ColumnMapping`; Wertschreiber `ValueWriter`, `ValueMapping`; `RpcOutputs`) | update | Draht: `INTN` mit 1 und 2 Byte, Bereichsprüfung |
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/` (`SimValues`, `CatalogAnswers.typeOf`, `CatalogForms`, `BulkLoads`, Planer) | update | Auswerter, Antwortspalten des Katalogs |
| `testserver/kotlin/` (`TableStore`, Leser der Tabellenbeschreibung) | update | Zeilenprüfung für `Int16`/`UInt8` als Auswerter; Typnamen `smallint`/`tinyint` bleiben nicht aufgenommen |
| `src/integrator/` (Einbettungstest) | update | Beleg der Namen, erschöpfendes `when` |
| `spec/spezifikation.md` (`SPEC-003`, `SPEC-035`, `SPEC-059`) | update | Zielbild, Liste der Fläche, Änderungsprotokoll |
| `referenztest-dotnet/` und `docs/messungen/referenzabgleich.md` | update (falls Nachmessung) | Maße von `smallint`/`tinyint` |
| Tests (Kotlin, `src/test`) | neu / update | Grenzwerte, COLMETADATA je Spalte |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf (die Suche nach Auswertern gehört dazu).

## 4. Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) `Accepted` (erfüllt, 2026-11-08); [slice-qa-vergleichslauf-d-messungen-bulk-merge](../done/slice-qa-vergleichslauf-d-messungen-bulk-merge.md) in `done/` (erfüllt); [slice-katalog-und-ddl-zustand](../done/slice-katalog-und-ddl-zustand.md) in `done/` (erfüllt); [slice-katalog-und-struktur-an-messung-angleichen](../done/slice-katalog-und-struktur-an-messung-angleichen.md) in `done/` (gleiche Dateien im Katalog und im Testserver; Setzung des Planners); die parallelen Zweige zu [slice-bulk-load-feste-typen-und-referenzclient](../done/slice-bulk-load-feste-typen-und-referenzclient.md), [slice-schreibpfad-merge-output](../done/slice-schreibpfad-merge-output.md) und [slice-sp-unprepare-ohne-obergrenze](../done/slice-sp-unprepare-ohne-obergrenze.md) sind in `main` (Setzung des Planners wegen der Fläche: dieser Slice berührt die Auswerter, die diese Zweige ändern; kein inhaltlicher Zwang); kein anderer Bau-Slice in `in-progress/` (WIP 1); ein Verantwortlicher ist eingetragen; der Nutzer hat über den dritten Liefer-Punkt (Nachmessung oder Setzung) entschieden.

**Reihenfolge der drei Bau-Slices (Setzung des Planners):** [slice-katalog-und-struktur-an-messung-angleichen](../done/slice-katalog-und-struktur-an-messung-angleichen.md) → dieser Slice → [slice-antwort-tokens-zielbild-luecken-bauen](../next/slice-antwort-tokens-zielbild-luecken-bauen.md) → [slice-ddl-abschluss-und-transaktionszustand-angleichen](slice-ddl-abschluss-und-transaktionszustand-angleichen.md) → [slice-katalog-indizes-defaults-fremdschluessel](slice-katalog-indizes-defaults-fremdschluessel.md). Begründung: dieser Slice ist die breiteste Änderung und kollidiert mit allen Zweigen; er geht früh und allein, damit [slice-antwort-tokens-zielbild-luecken-bauen](../next/slice-antwort-tokens-zielbild-luecken-bauen.md), [slice-ddl-abschluss-und-transaktionszustand-angleichen](slice-ddl-abschluss-und-transaktionszustand-angleichen.md) und [slice-katalog-indizes-defaults-fremdschluessel](slice-katalog-indizes-defaults-fremdschluessel.md) auf dem neuen `SqlType` aufsetzen. [slice-katalog-indizes-defaults-fremdschluessel](slice-katalog-indizes-defaults-fremdschluessel.md) startet nach diesem Slice (die Typen der Spalten wirken in jede Sichtantwort).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): die Suche nach Auswertern findet Stellen, die ein eigenes Verhalten brauchen (nicht nur einen weiteren Fall); Schnittvorschlag: Mitglieder und Auswerter | Katalogspalten | Maße. Die Rückführung „Fläche wächst“ entfällt, sie ist durch [ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) entschieden.
- `in-progress` → `open` (blockiert): ein Lauf mit d-migrate (`schema reverse`, `compare`, `schema migrate --execute`) wird durch `smallint`/`tinyint` statt `int` rot oder ändert sein Ergebnis; der Nutzer entscheidet dann über den Weg (Rückfall C von [ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) für die betroffene Spalte).

## 5. Closure-Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle drei Liefer-Punkte der DoD erledigt, `make gates` grün (auch `make flaeche`), der Review-Report liegt vor, ein Lauf mit d-migrate (`schema reverse`, `compare`, `schema migrate --execute`) nach dem Bau ist unverändert gegenüber einem Lauf unmittelbar davor (oder die Abweichung als Befund benannt, [ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) Konsequenzen), die Zeile M-16 des [Messprotokoll](../../../messungen/referenzabgleich.md#messreihe-zu-massenladen-merge-katalog-und-strukturänderung-messungen-vom-2026-11-07) ist von „widerspricht“ auf „angeglichen“ gezogen und, falls nachgemessen, trägt das Messprotokoll die neue Zeile, die Closure-Notiz trägt den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **Breite Änderung der Auswerter.** Eine Stelle, die `SqlType` nur mit `==` prüft, fängt der Übersetzer nicht; eine übersehene Stelle bricht still ein Verhalten. Gegenmittel: die Suche im ersten Lauf, Tests je Auswerter. — **Ausgang:** offen bis zur Closure.
- **Dateikonflikte mit allen Zweigen** an den Auswertern (Reihenfolge in §4 ist eine Setzung). — **Ausgang:** offen bis zur Closure.
- **Auswirkung auf den Beleg am fremden Client.** d-migrate liest `max_length` und `precision` über JDBC; ob `smallint` und `tinyint` statt `int` etwas ändert, ist nicht gemessen ([ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) Kontext). [slice-abnahme-d-migrate](slice-abnahme-d-migrate.md) prüft es am Ende erneut. — **Ausgang:** offen bis zur Closure.
- **Inkompatible Änderung für Kotlin-`when` ohne `else`** vor der ersten Veröffentlichung ([ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) Entscheidung 4, Setzung: trifft keinen Dritten). — **Ausgang:** offen bis zur Closure.
- **Katalogmaße ungemessen**, bis der Nutzer (a) oder (b) wählt. — **Ausgang:** offen bis zur Closure.

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

Wird bei der Closure gefüllt.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); eine eigene Sub-Area besteht nicht.

**Vorgelagert — offene Beobachtungen sichten:** [`BEO-TDS/katalog-strukturabweisung-und-aenderungszaehler-weichen-vom-echten-server-ab`](../observations/BEO-TDS/katalog-strukturabweisung-und-aenderungszaehler-weichen-vom-echten-server-ab/state.md), [`BEO-TDS/spec-setzung-widerspricht-messung-am-echten-server`](../observations/BEO-TDS/spec-setzung-widerspricht-messung-am-echten-server/state.md), [`BEO-TDS/messung-am-echten-sql-server-fehlt`](../observations/BEO-TDS/messung-am-echten-sql-server-fehlt/state.md), [`BEO-TDS/oeffentliche-flaeche-mitglieder-ohne-sensor`](../observations/BEO-TDS/oeffentliche-flaeche-mitglieder-ohne-sensor/state.md).

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
