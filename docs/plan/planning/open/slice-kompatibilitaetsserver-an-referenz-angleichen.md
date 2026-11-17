# Slice slice-kompatibilitaetsserver-an-referenz-angleichen: Kompatibilitätsserver und Testschema an die Referenz angleichen: Zeilenzahl von `UPDATE`/`DELETE` und Spaltenbeschreibung von `laufend`/`kaputt` (A-9, A-14)

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** ohne Welle (keine Closure-Bedingung außerhalb der DoD dieses Slice; Träger der Lücken A-9 und A-14 der Ausnahmeliste des Referenzabgleichs, angelegt nach Nutzerentscheidung vom 2026-11-17: „ja slices anlegen“ auf die Frage der Closure von [welle-referenzabgleich-release](../done/welle-referenzabgleich-release.md)).

**Bezug:** [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) (Scope: Änderungszähler der Schreibformen), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate) (Testserver mit veränderlichem Bestand), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md)

**Berührte Spec-Stellen:** [SPEC-035](../../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) (Zahl der betroffenen Zeilen) · [SPEC-045](../../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig) (Schema der Referenzinstanz und Tabellenbeschreibung „gemeinsam geändert“, Ausnahmeliste).

**Verantwortlich:** —

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-17.

---

## 1. Ziel und Abgrenzung

**Ziel:** Der Kompatibilitätsserver (`testserver/`, Fixture des Referenzabgleichs) und das Schema der Referenzinstanz (`testschema.sql`) beschreiben dieselben Zeilenzahlen und Spaltenbeschreibungen, so dass die Regeln A-9 (Live) und A-14 (Mitschnitt) entfallen — oder sie gelten nach Entscheidung des Nutzers als *so gewollt*.

**Ist-Stand** (gelesen am 2026-11-17; was nicht als gemessen steht, ist aus Datei oder Code gelesen):

- **A-9:** Schritte `aendern` (`UPDATE person SET Age = 40 WHERE Id = 1`) und `loeschen` (`DELETE FROM person WHERE Id = 3`): die Referenz meldet 1 und 1, der Server 2 und 3. Ursache (gelesen): `KompatibilitaetsServer.kt` setzt `UPDATED_ROWS = 2` und `DELETED_ROWS = 3` als feste Rückgabe der Callbacks `onUpdate` und `onDelete`; die Zahl stammt nicht aus der Anweisung. Das ist ein Wert der Fixture im Modul `testserver`, nicht der Bibliothek ([SPEC-035](../../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends): der Callback liefert die Zahl).
- **Kopplung:** `referenztest-dotnet/Kompatibilitaet/SzenarienTests.cs` (Zeile 20 nennt die Kopplung an `KompatibilitaetsServer.kt`) trägt `Geaendert = 2`, `Geloescht = 3`; der Test `Einfuegen_Aendern_und_Loeschen_im_Batch_melden_die_Zeilenzahlen_der_Callbacks` prüft die drei Zahlen 1, 2, 3 eines Batches. Aus den Zahlen folgt (Lesart, nicht belegt), dass sie die drei Anweisungen unterscheidbar machen; ändert die Fixture die Zahlen auf 1, verliert dieser Test das Mittel, die Zählung je Anweisung zu unterscheiden.
- **A-14:** Szenarien `cancel-streaming` und `fehler-nach-zeilen`: das Schema der Referenz führt `laufend` und `kaputt` als **Ansichten** (`Id` nullbar, `kaputt.Text` `nvarchar(10)`), der Server als Tabellen mit `Id` nicht nullbar und `Text` mit der Voreinstellung 4000 (Länge 8000 in COLMETADATA). Zwei Regeln in `mitschnitt-ausnahmen.tsv` benennen A-14: `cancel-streaming/01/colmetadata` und `fehler-nach-zeilen/01/colmetadata` (gezählt am 2026-11-17). Kein Verhalten des Servers, ein Pflegerückstand: [SPEC-045](../../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig) verlangt die gemeinsame Änderung beider Seiten.
- **Mittel auf Seiten des Servers (gelesen):** `SimColumn` trägt `nullable` und `maxLength`; `KompatibilitaetsServer.kt` kann `laufend.Id` nullbar und `kaputt.Text` mit `maxLength = 10` beschreiben, ohne die Bibliothek zu ändern. Auf Seiten der Referenz hieße die Angleichung, die Ansichten in `testschema.sql` zu ändern; die Aufnahmen der betroffenen Szenarien (beide Instanzen) wären neu zu fahren.
- **Register:** A-14 hat keinen eigenen Eintrag; der Rückstand hängt an `BEO-TDS/live-lauf-ausserhalb-der-gates-haelt-kopplungen-nicht` (Kopplung von Schema der Referenz und Tabellen des Servers, kein Sensor).

**Offene Frage an den Nutzer — „Code angleichen“ oder „Zielbild anpassen“.** Die Spezifikation ist das Zielbild, eine Abweichung des Servers ist Schuld; ob sie hier durch Bau behoben wird oder ob Spezifikation und Ausnahmeliste dem Ist-Stand folgen, entscheidet der Nutzer, nicht der Planner. Der Slice startet nicht, bevor die Frage beantwortet ist.

- **A-9, Option A — Code angleichen:** die Fixture zählt die betroffenen Zeilen aus dem Zustand der Tabelle (Beschreibung `person` als veränderlicher Bestand, im Muster von [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate)) oder setzt die Konstanten auf 1 und zieht `SzenarienTests.cs` nach. **Option B — Zielbild anpassen:** A-9 wird *so gewollt* („die Fixture meldet feste, voneinander verschiedene Zahlen, damit der Test die Anweisungen eines Batches trennt“; eine Aussage über den Zweck der Wahl 2 und 3 ist aus den Dateien nicht belegt und braucht die Bestätigung des Nutzers). **Option C — Szenario anpassen:** das Szenario der Referenz so schreiben, dass die Referenz 2 und 3 Zeilen betrifft (etwa über `Age`); die Wirkung auf die Folgeschritte des Live-Szenarios ist ungeprüft.
- **A-14, Option A — Code angleichen:** `laufend.Id` nullbar, `kaputt.Text` mit `maxLength = 10` in der Fixture. **Option B — Zielbild anpassen:** die Ansichten der Referenz so ändern, dass sie die Tabellen des Servers beschreiben (neue Aufnahmen der Szenarien `cancel-streaming` und `fehler-nach-zeilen`, beide Instanzen, EULA, Messsperre). **Option C:** A-14 als *so gewollt* führen — der Planner sieht dafür keinen Grund, weil die Regel ein Pflegerückstand ist.
- **Empfehlung des Planners (Setzung, keine Entscheidung):** A-14 Option A (kleinste Änderung, keine neue Aufnahme, kein Verhalten der Bibliothek betroffen). A-9 Option A mit Zählung aus dem Zustand, wenn der Nutzer den Test der drei Anweisungen anders trennen will; sonst Option C. Alle Optionen berühren nur Fixture, Schema der Referenz und Szenario, nicht `src/main`.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Der Testserver mit veränderlichem Bestand als Produkt ([TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate)) — seine Zählung der betroffenen Zeilen ist ein anderer Gegenstand; hier geht es um die feste Fixture `KompatibilitaetsServer`. Das Register nennt die Zählung aus dem Zustand als Alternative (`BEO-TDS/callback-zeilenzahl-ist-fixture-konstante`); ob sie die Fixture trägt, entscheidet §1.
- Ein Sensor, der die Kopplung von Schema der Referenz und Tabellen des Servers hält — `BEO-TDS/live-lauf-ausserhalb-der-gates-haelt-kopplungen-nicht` führt ihn als „denkbar und nicht vorgeschlagen“; die Aufnahme des Live-Laufs in `make gates` ist abgelehnt (Nutzerentscheidung 2026-11-06). Ein Sensor ist Regel- oder Gate-Entscheidung des Nutzers.
- Die Antwort auf `SchemaOnly` und `KeyInfo` (A-8), die die Spaltenbeschreibung ebenfalls prüft — [slice-simulation-schemaonly-keyinfo](../open/slice-simulation-schemaonly-keyinfo.md); erst mit ihr zeigt der Live-Lauf Schema und Tabellenbeschreibung gegeneinander.
- Änderungen der Bibliothek (`src/main`) — keine der beiden Lücken ist ein Verhalten der Bibliothek, wie die Lesung der Dateien zeigt; ändert die Wahl des Nutzers das, ist es ein Folge-Slice.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit.

- [ ] **A-9:** nach Entscheidung des Nutzers meldet die Fixture (oder das Szenario) für `UPDATE` und `DELETE` dieselben Zahlen wie die Referenz; die zwei Live-Regeln (`aendern`, `loeschen`) entfallen; `SzenarienTests.cs` und `KompatibilitaetsServer.kt` bleiben gekoppelt und gleich.
- [ ] **A-14:** die Spaltenbeschreibung von `laufend` und `kaputt` ist auf beiden Seiten gleich (Option A: Fixture, Option B: Ansichten und neue Aufnahmen); die zwei Mitschnitt-Regeln `…/colmetadata` entfallen.
- [ ] **Nachweis:** Mitschnitt-Lauf (`LAUF=mitschnitt`, nur der Server) und Live-Lauf (`LAUF=live-2022`, `live-2025`, Referenzinstanz) sind gegen die verkleinerten Listen grün gefahren, oder „nicht gefahren“ steht mit Grund in §7; `make kompatibilitaet` (Nicht-Gate) auf dem End-Stand im Verifier-Lauf, weil `SzenarienTests.cs` berührt ist.
- [ ] `make gates` Exit 0, dazu je Art der Änderung nach `harness/README.md` §Minimal agent workflow: engste Testklasse und Mutation je Zusage über enge Testklassen (`testserver/`), `make test`, `make referenztest-dotnet` (Selbsttest der Liste), `make docs-check`, `make flaeche`, `make kopplung-zahlen` (Doku/Spec), `make spec-abdeckung` einmal (Tests oder `@spec`-Tags) (`src/` oder `testserver/`: Verifier fährt `make clean-build` und `make e2e` je einmal auf dem End-Stand; Implementer und Reviewer nicht). Die Läufe `make vergleichslauf-d` sind Messung und Nachweis, keine Gates ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)).
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor (`.harness/skills/reviewer.md`), kein Self-Review.
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt: [SPEC-045](../../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig), [Messprotokoll des Referenzabgleichs](../../../messungen/referenzabgleich.md) (A-9, A-14), `live-ausnahmen.tsv`, `mitschnitt-ausnahmen.tsv`; `referenztest-dotnet/Vergleich/testschema.sql` und `KompatibilitaetsServer.kt` tragen die Kopplung in ihrem Kopf.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben: `BEO-TDS/callback-zeilenzahl-ist-fixture-konstante` (Ausgang), `BEO-TDS/live-lauf-ausserhalb-der-gates-haelt-kopplungen-nicht` (A-14); keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — Pfad-Kandidaten für §8; geprüft ist das Vorhandensein der Dateien am 2026-11-17, nicht ihre Eignung.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `testserver/kotlin/io/github/pt9912/tdswire/testserver/KompatibilitaetsServer.kt` | update | Callbacks `onUpdate`/`onDelete` (Konstanten `UPDATED_ROWS`, `DELETED_ROWS`), Spalten von `laufend` und `kaputt` |
| `referenztest-dotnet/Kompatibilitaet/SzenarienTests.cs` | update, falls Zahlen sich ändern | Kopplung `Geaendert`, `Geloescht` an die Konstanten; Test der drei Zeilenzahlen eines Batches |
| `referenztest-dotnet/Vergleich/testschema.sql`, `referenztest-dotnet/Vergleich/LiveSzenarioTests.cs` | update, falls Option B/C | Ansichten `laufend`, `kaputt`, Schritte `aendern`, `loeschen`; Neuaufnahme der Szenarien bei Änderung der Ansichten |
| `referenztest-dotnet/Vergleich/live-ausnahmen.tsv`, `referenztest-dotnet/Vergleich/mitschnitt-ausnahmen.tsv` | update | Regeln `aendern`, `loeschen` und `…/colmetadata` (A-14) entfallen |
| `docs/messungen/referenzabgleich.md` | update | Tabelle der Ausnahmen A-9, A-14 |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf (Testdateien mit Kennung der Akzeptanzkriterien).

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`open` → `next` → `in-progress`): Nutzerzeichen, ein Verantwortlicher (`Verantwortlich:` ist bis dahin `—`) und die Antwort auf die Fragen in §1 (A-9 und A-14 getrennt entscheidbar). Der Live-Lauf als Nachweis braucht die gepinnten Referenzinstanzen (EULA bestätigt 2026-11-01 und 2026-11-17), läuft **nicht parallel** zu Last-Messslices, und die Messsperre `/tmp/claude-1000/-Development-tdswire/messlock` gilt für Messläufe; neue Aufnahmen (nur bei Option B für A-14) ebenso.

**Rückführungen — vorab benennen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert): die Referenzinstanzen sind nicht beziehbar (Pull, Platz, geänderter EULA-Text); der Mitschnitt-Teil von A-14 (Option A) bleibt ohne Referenzinstanz prüfbar.

## 5. Closure-Trigger

Alle Punkte der DoD in §2 erledigt, `make gates` und `make docs-check` Exit 0, die Regeln der Ausnahmeliste, die dieser Slice trägt, sind entfallen oder mit der Entscheidung des Nutzers in ihrer Art geändert (nicht still stehen gelassen), und die Closure-Notiz mit Steering-Loop-Lerneintrag ist geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang.

- Die Zahl 2 und 3 trennt im Test die Anweisungen eines Batches; eine Änderung der Konstanten schwächt diesen Test. — **Ausgang:** offen bis zur Closure; Entscheidung in §1 (Option A mit Zählung aus dem Zustand erhält die Trennung nicht ohne weiteres); Ersatz der Trennung im Test ist Teil von DoD 1.
- Die Kopplung zwischen Fixture, `SzenarienTests.cs` und `testschema.sql` ist ohne Sensor; ein vergessener Nachzug fällt erst im Live-Lauf auf (`BEO-TDS/live-lauf-ausserhalb-der-gates-haelt-kopplungen-nicht`). — **Ausgang:** offen bis zur Closure; der Slice trägt die Kopplung im Kopf der Dateien; ein Sensor ist Entscheidung des Nutzers und nicht Teil.
- Der Live-Lauf ist zuletzt am 2026-11-06 grün gefahren und seitdem nicht wiederholt ([Results-Notiz von welle-referenzabgleich-release](../done/welle-referenzabgleich-release-results.md)); Seit-dem-Änderungen (Class 16, A-10, A-12, A-19, A-20 entfallen, Mehrfach-RPC) sind in ihm nicht belegt. — **Ausgang:** offen bis zur Closure; der Nachweis in DoD 3 ist zugleich der erste Lauf nach diesen Änderungen; weicht er an anderer Stelle ab, steht der Befund in §7 und im Register.
- Größe: drei Liefer-Punkte. — **Ausgang:** offen bis zur Closure; Rückführung nach §4.

## 7. Closure-Notiz

Wird bei der Closure gefüllt.

**Herkunft:** Lücken A-9 und A-14 der Ausnahmeliste, [Messprotokoll des Referenzabgleichs](../../../messungen/referenzabgleich.md); Register `BEO-TDS/callback-zeilenzahl-ist-fixture-konstante` (Träger-Vorschlag: Zählung aus dem Zustand oder Konstanten auf 1, „kleiner eigener Slice“) und `BEO-TDS/live-lauf-ausserhalb-der-gates-haelt-kopplungen-nicht` (Kopplung für A-14); [Results-Notiz von welle-referenzabgleich-release](../done/welle-referenzabgleich-release-results.md). Zusammengelegt, weil beide dieselbe Datei (`KompatibilitaetsServer.kt`), dasselbe Szenario-Gerüst und denselben Nachweis (Live- und Mitschnitt-Lauf) tragen und keine die Bibliothek ändert. Nutzerentscheidung 2026-11-17: „ja slices anlegen“ (Frage der Closure von `welle-referenzabgleich-release`, Punkt: die 14 Lücken-Nummern ohne Träger-Slice).

## 8. Sub-Area-Prüfungen und Modus-Begründung

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register (`../observations/BEO-TDS/`) gegen die Verzeichnisliste gelesen; Treffer: `callback-zeilenzahl-ist-fixture-konstante` (offen), `live-lauf-ausserhalb-der-gates-haelt-kopplungen-nicht` (offen, über der Schwelle). Zähler-Stände stehen in den Dateien des Registers, nicht hier.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
