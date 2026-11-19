# Slice slice-kompatibilitaetsserver-an-referenz-angleichen: Kompatibilitätsserver und Testschema an die Referenz angleichen: Zeilenzahl von `UPDATE`/`DELETE` und Nullbarkeit von `laufend.Id` (A-9, A-14 ohne `kaputt.Text`)

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** ohne Welle (keine Closure-Bedingung außerhalb der DoD dieses Slice; Träger der Lücke A-9 und des Teils `laufend.Id` von A-14 der Ausnahmeliste des Referenzabgleichs, `kaputt.Text` liegt bei [slice-simulation-spaltenlaenge-in-auswahl](../open/slice-simulation-spaltenlaenge-in-auswahl.md), angelegt nach Nutzerentscheidung vom 2026-11-17: „ja slices anlegen“ auf die Frage der Closure von [welle-referenzabgleich-release](../done/welle-referenzabgleich-release.md)).

**Bezug:** [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) (Scope: Änderungszähler der Schreibformen), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate) (Testserver mit veränderlichem Bestand), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md)

**Berührte Spec-Stellen:** [SPEC-035](../../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) (Zahl der betroffenen Zeilen) · [SPEC-045](../../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig) (Schema der Referenzinstanz und Tabellenbeschreibung „gemeinsam geändert“, Ausnahmeliste).

**Verantwortlich:** pt9912

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-17.

---

## 1. Ziel und Abgrenzung

**Ziel:** Der Kompatibilitätsserver (`testserver/`, Fixture des Referenzabgleichs) und das Schema der Referenzinstanz (`testschema.sql`) beschreiben dieselben Zeilenzahlen und die gleiche Nullbarkeit von `laufend.Id`, so dass die Regel A-9 (Live) und die Regel `cancel-streaming/01/colmetadata` (A-14, Mitschnitt) entfallen. `kaputt.Text` (Länge 10) braucht die Länge im Ergebnis der Bibliothek und liegt bei [slice-simulation-spaltenlaenge-in-auswahl](../open/slice-simulation-spaltenlaenge-in-auswahl.md) (Nutzerentscheidung 2026-11-19: Bibliothek ändern).

**Ist-Stand** (gelesen am 2026-11-17; was nicht als gemessen steht, ist aus Datei oder Code gelesen):

- **A-9:** Schritte `aendern` (`UPDATE person SET Age = 40 WHERE Id = 1`) und `loeschen` (`DELETE FROM person WHERE Id = 3`): die Referenz meldet 1 und 1, der Server 2 und 3. Ursache (gelesen): `KompatibilitaetsServer.kt` setzt `UPDATED_ROWS = 2` und `DELETED_ROWS = 3` als feste Rückgabe der Callbacks `onUpdate` und `onDelete`; die Zahl stammt nicht aus der Anweisung. Das ist ein Wert der Fixture im Modul `testserver`, nicht der Bibliothek ([SPEC-035](../../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends): der Callback liefert die Zahl).
- **Kopplung:** `referenztest-dotnet/Kompatibilitaet/SzenarienTests.cs` (Zeile 20 nennt die Kopplung an `KompatibilitaetsServer.kt`) trägt `Geaendert = 2`, `Geloescht = 3`; der Test `Einfuegen_Aendern_und_Loeschen_im_Batch_melden_die_Zeilenzahlen_der_Callbacks` prüft die drei Zahlen 1, 2, 3 eines Batches. Aus den Zahlen folgt (Lesart, nicht belegt), dass sie die drei Anweisungen unterscheidbar machen; ändert die Fixture die Zahlen auf 1, verliert dieser Test das Mittel, die Zählung je Anweisung zu unterscheiden.
- **A-14:** Szenarien `cancel-streaming` und `fehler-nach-zeilen`: das Schema der Referenz führt `laufend` und `kaputt` als **Ansichten** (`Id` nullbar, `kaputt.Text` `nvarchar(10)`), der Server als Tabellen mit `Id` nicht nullbar und `Text` mit der Voreinstellung 4000 (Länge 8000 in COLMETADATA). Zwei Regeln in `mitschnitt-ausnahmen.tsv` benennen A-14: `cancel-streaming/01/colmetadata` und `fehler-nach-zeilen/01/colmetadata` (gezählt am 2026-11-17). Kein Verhalten des Servers, ein Pflegerückstand: [SPEC-045](../../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig) verlangt die gemeinsame Änderung beider Seiten.
- **Mittel auf Seiten des Servers (gelesen):** `SimColumn` trägt `nullable` und `maxLength`; `KompatibilitaetsServer.kt` kann `laufend.Id` nullbar beschreiben, ohne die Bibliothek zu ändern; `kaputt.Text` mit `maxLength = 10` ändert die Länge im COLMETADATA einer Auswahl nicht (Befund unten). Auf Seiten der Referenz hieße die Angleichung, die Ansichten in `testschema.sql` zu ändern; die Aufnahmen der betroffenen Szenarien (beide Instanzen) wären neu zu fahren.
- **Register:** A-14 hat keinen eigenen Eintrag; der Rückstand hängt an `BEO-TDS/live-lauf-ausserhalb-der-gates-haelt-kopplungen-nicht` (Kopplung von Schema der Referenz und Tabellen des Servers, kein Sensor).

**Offene Frage an den Nutzer — „Code angleichen“ oder „Zielbild anpassen“.** Die Spezifikation ist das Zielbild, eine Abweichung des Servers ist Schuld; ob sie hier durch Bau behoben wird oder ob Spezifikation und Ausnahmeliste dem Ist-Stand folgen, entscheidet der Nutzer, nicht der Planner. Der Slice startete mit der Antwort des Nutzers (siehe unten).

- **A-9, Option A — Code angleichen:** die Fixture zählt die betroffenen Zeilen aus dem Zustand der Tabelle (Beschreibung `person` als veränderlicher Bestand, im Muster von [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate)) oder setzt die Konstanten auf 1 und zieht `SzenarienTests.cs` nach. **Option B — Zielbild anpassen:** A-9 wird *so gewollt* („die Fixture meldet feste, voneinander verschiedene Zahlen, damit der Test die Anweisungen eines Batches trennt“; eine Aussage über den Zweck der Wahl 2 und 3 ist aus den Dateien nicht belegt und braucht die Bestätigung des Nutzers). **Option C — Szenario anpassen:** das Szenario der Referenz so schreiben, dass die Referenz 2 und 3 Zeilen betrifft (etwa über `Age`); die Wirkung auf die Folgeschritte des Live-Szenarios ist ungeprüft.
- **A-14, Option A — Code angleichen:** `laufend.Id` nullbar (in der Fixture), `kaputt.Text` mit `maxLength = 10` (wirkt nicht ohne Änderung der Bibliothek, Befund unten). **Option B — Zielbild anpassen:** die Ansichten der Referenz so ändern, dass sie die Tabellen des Servers beschreiben (neue Aufnahmen der Szenarien `cancel-streaming` und `fehler-nach-zeilen`, beide Instanzen, EULA, Messsperre). **Option C:** A-14 als *so gewollt* führen — der Planner sieht dafür keinen Grund, weil die Regel ein Pflegerückstand ist.
- **Empfehlung des Planners (Setzung, keine Entscheidung):** A-14 Option A (kleinste Änderung, keine neue Aufnahme); für `laufend.Id` ohne Bibliothek, für `kaputt.Text` nicht (Befund unten). A-9 Option A mit Zählung aus dem Zustand, wenn der Nutzer den Test der drei Anweisungen anders trennen will; sonst Option C. A-9 und `laufend.Id` berühren nur Fixture, Schema der Referenz und Szenario, nicht `src/main`.

**Entscheidung des Nutzers (2026-11-18): Option A, Code angleichen** (die Spezifikation ist das Zielbild, eine Abweichung des Servers ist Schuld). **A-14:** `laufend.Id` nullbar, `kaputt.Text` mit `maxLength = 10` in der Fixture, ohne Bibliotheksänderung (für `kaputt.Text` nicht ausführbar, Befund unten; übergeben an [slice-simulation-spaltenlaenge-in-auswahl](../open/slice-simulation-spaltenlaenge-in-auswahl.md)). **A-9:** Zählung aus dem Zustand der Tabelle; gelesen ist: die Callbacks `onUpdate` und `onDelete` von `person` zählen die Zeilen des unveränderten Bestands, auf die die Bedingung zutrifft (`RowRules.matches`), der Bestand ändert sich nicht, die Tests bleiben unabhängig von ihrer Reihenfolge; eine Testtrennung (Option C) war dafür nicht nötig. Die Anweisungen eines Batches trennt der Test jetzt über Bedingungen mit verschiedener Trefferzahl (`Age = 36`: 2, `Id IN (1, 2, 3)`: 3, `Id = 99`: 0).

**Befund des Implementers zur Entscheidung A-14 (Stand 2026-11-19, gelesen in `SimulationBackend.kt` und `SimModel.kt`):** `SimColumn.maxLength` erreicht das COLMETADATA einer Auswahl nicht; `SimulationBackend` baut `ResultColumn(name, type, nullable)` ohne die Länge, und die KDoc von `SimColumn` sagt, die Länge ändere die Metadaten eines Ergebnisses nicht. `kaputt.Text` mit `maxLength = 10` in der Fixture ändert die Länge 8000 in COLMETADATA daher nicht. Gebaut ist der Teil, der ohne Bibliotheksänderung geht: `laufend.Id` nullbar (die Regel `cancel-streaming/01/colmetadata` entfällt). Offen bleibt `kaputt.Text` (Regel `fehler-nach-zeilen/01/colmetadata`, A-14 auf diese Länge verengt); Nutzerentscheidung 2026-11-19: die Bibliothek wird geändert (Länge im Ergebnis), Träger ist [slice-simulation-spaltenlaenge-in-auswahl](../open/slice-simulation-spaltenlaenge-in-auswahl.md). Verworfen ist Option B (Ansicht `kaputt` in `testschema.sql`, neue Aufnahmen beider Instanzen).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Der Testserver mit veränderlichem Bestand als Produkt ([TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate)) — seine Zählung der betroffenen Zeilen ist ein anderer Gegenstand; hier geht es um die feste Fixture `KompatibilitaetsServer`. Das Register nennt die Zählung aus dem Zustand als Alternative (`BEO-TDS/callback-zeilenzahl-ist-fixture-konstante`); ob sie die Fixture trägt, entscheidet §1.
- Ein Sensor, der die Kopplung von Schema der Referenz und Tabellen des Servers hält — `BEO-TDS/live-lauf-ausserhalb-der-gates-haelt-kopplungen-nicht` führt ihn als „denkbar und nicht vorgeschlagen“; die Aufnahme des Live-Laufs in `make gates` ist abgelehnt (Nutzerentscheidung 2026-11-06). Ein Sensor ist Regel- oder Gate-Entscheidung des Nutzers.
- Die Antwort auf `SchemaOnly` und `KeyInfo` (A-8), die die Spaltenbeschreibung ebenfalls prüft — [slice-simulation-schemaonly-keyinfo](../open/slice-simulation-schemaonly-keyinfo.md); erst mit ihr zeigt der Live-Lauf Schema und Tabellenbeschreibung gegeneinander.
- Änderungen der Bibliothek (`src/main`) — A-9 und `laufend.Id` sind keine Verhalten der Bibliothek; `kaputt.Text` braucht dagegen die Länge im Ergebnis der Bibliothek (Befund oben) und liegt bei [slice-simulation-spaltenlaenge-in-auswahl](../open/slice-simulation-spaltenlaenge-in-auswahl.md) (Nutzerentscheidung 2026-11-19).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit.

- [x] **A-9:** nach Entscheidung des Nutzers meldet die Fixture (oder das Szenario) für `UPDATE` und `DELETE` dieselben Zahlen wie die Referenz; die zwei Live-Regeln (`aendern`, `loeschen`) entfallen; `SzenarienTests.cs` und `KompatibilitaetsServer.kt` bleiben gekoppelt und gleich.
- [x] **A-14 (`laufend.Id`):** `laufend.Id` ist in der Fixture nullbar wie in der Referenz; die Mitschnitt-Regel `cancel-streaming/01/colmetadata` entfällt. `kaputt.Text` und die Regel `fehler-nach-zeilen/01/colmetadata` liegen bei [slice-simulation-spaltenlaenge-in-auswahl](../open/slice-simulation-spaltenlaenge-in-auswahl.md).
- [x] **Nachweis:** Mitschnitt-Lauf (`LAUF=mitschnitt`, nur der Server) und Live-Lauf (`LAUF=live-2022`, `live-2025`, Referenzinstanz) sind gegen die verkleinerten Listen grün gefahren, oder „nicht gefahren“ steht mit Grund in §7; `make kompatibilitaet` (Nicht-Gate) auf dem End-Stand im Verifier-Lauf, weil `SzenarienTests.cs` berührt ist.
- [x] `make gates` Exit 0, dazu je Art der Änderung nach `harness/README.md` §Minimal agent workflow: engste Testklasse und Mutation je Zusage über enge Testklassen (`testserver/`), `make test`, `make referenztest-dotnet` (Selbsttest der Liste), `make docs-check`, `make flaeche`, `make kopplung-zahlen` (Doku/Spec), `make spec-abdeckung` einmal (Tests oder `@spec`-Tags) (`src/` oder `testserver/`: Verifier fährt `make clean-build` und `make e2e` je einmal auf dem End-Stand; Implementer und Reviewer nicht). Die Läufe `make vergleichslauf-d` sind Messung und Nachweis, keine Gates ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)).
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor (`.harness/skills/reviewer.md`), kein Self-Review.
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt: [SPEC-045](../../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig), [Messprotokoll des Referenzabgleichs](../../../messungen/referenzabgleich.md) (A-9, A-14), `live-ausnahmen.tsv`, `mitschnitt-ausnahmen.tsv`; `referenztest-dotnet/Vergleich/testschema.sql` und `KompatibilitaetsServer.kt` tragen die Kopplung in ihrem Kopf.
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben: `BEO-TDS/callback-zeilenzahl-ist-fixture-konstante` (Ausgang), `BEO-TDS/live-lauf-ausserhalb-der-gates-haelt-kopplungen-nicht` (A-14); keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — Pfad-Kandidaten für §8; geprüft ist das Vorhandensein der Dateien am 2026-11-17, nicht ihre Eignung.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `testserver/kotlin/io/github/pt9912/tdswire/testserver/KompatibilitaetsServer.kt` | update | Callbacks `onUpdate`/`onDelete` (zählen die Zeilen des Bestands; die Konstanten `UPDATED_ROWS`, `DELETED_ROWS` entfallen), `laufend.Id` nullbar |
| `referenztest-dotnet/Kompatibilitaet/SzenarienTests.cs` | update | Kopplung `Geaendert`, `Geloescht` an die Zählung; Test der drei Zeilenzahlen und neuer Test mit verschiedener Trefferzahl |
| `testserver/test/kotlin/io/github/pt9912/tdswire/testserver/KompatibilitaetsTablesTest.kt` | neu | Beleg der Zählung von `onUpdate`/`onDelete` und von `laufend.Id` nullbar ohne Schlüssel |
| `referenztest-dotnet/Vergleich/testschema.sql`, `referenztest-dotnet/Vergleich/LiveSzenarioTests.cs` | unberührt (Option B/C nicht gewählt; Option A ändert nur den Server) | Ansichten `laufend`, `kaputt`, Schritte `aendern`, `loeschen`; Neuaufnahme der Szenarien bei Änderung der Ansichten |
| `referenztest-dotnet/Vergleich/live-ausnahmen.tsv`, `referenztest-dotnet/Vergleich/mitschnitt-ausnahmen.tsv` | update | Regeln `aendern`, `loeschen` und `cancel-streaming/01/colmetadata` entfallen; `fehler-nach-zeilen/01/colmetadata` bleibt bis [slice-simulation-spaltenlaenge-in-auswahl](../open/slice-simulation-spaltenlaenge-in-auswahl.md) |
| `docs/messungen/referenzabgleich.md` | update | Tabelle der Ausnahmen A-9, A-14 |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf (Testdateien mit Kennung der Akzeptanzkriterien).

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`open` → `next` → `in-progress`): Nutzerzeichen, ein Verantwortlicher (`Verantwortlich:` ist bis dahin `—`) und die Antwort auf die Fragen in §1 (A-9 und A-14 getrennt entscheidbar). Der Live-Lauf als Nachweis braucht die gepinnten Referenzinstanzen (EULA bestätigt 2026-11-01 und 2026-11-17), läuft **nicht parallel** zu Last-Messslices, und die Messsperre `/tmp/claude-1000/-Development-tdswire/messlock` gilt für Messläufe; neue Aufnahmen (nur bei Option B für A-14) ebenso.

**Rückführungen — vorab benennen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert): die Referenzinstanzen sind nicht beziehbar (Pull, Platz, geänderter EULA-Text); der Mitschnitt-Teil von A-14 (`laufend.Id`, Option A) bleibt ohne Referenzinstanz prüfbar.

## 5. Closure-Trigger

Alle Punkte der DoD in §2 erledigt, `make gates` und `make docs-check` Exit 0, die Regeln der Ausnahmeliste, die dieser Slice trägt, sind entfallen oder mit der Entscheidung des Nutzers in ihrer Art geändert (nicht still stehen gelassen), und die Closure-Notiz mit Steering-Loop-Lerneintrag ist geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang.

- Die Zahl 2 und 3 trennt im Test die Anweisungen eines Batches; eine Änderung der Konstanten schwächt diesen Test. — **Ausgang:** **eingetreten und aufgelöst:** die Konstanten sind entfallen; der neue Test `Aendern_und_Loeschen_melden_die_Zahl_der_Zeilen_auf_die_die_Bedingung_zutrifft` trennt die Anweisungen über Bedingungen mit verschiedener Trefferzahl (2, 0, 3). Der Test `…_im_Batch_…` trennt sie nicht mehr (alle drei Zahlen 1; Review F-9, Verifikation F-V4, kein Befund gegen die DoD). Dass die Referenz bei 0, 2 und 3 Treffern ebenso zählt, ist nicht gemessen (Verifikation F-V1, siehe §7).
- Die Kopplung zwischen Fixture, `SzenarienTests.cs` und `testschema.sql` ist ohne Sensor; ein vergessener Nachzug fällt erst im Live-Lauf auf (`BEO-TDS/live-lauf-ausserhalb-der-gates-haelt-kopplungen-nicht`). — **Ausgang:** **weiter offen, Träger Nutzer:** der Nachzug dieses Slice gelang, weil Live- und Mitschnitt-Lauf liefen (Verifier, beide Instanzen); ein Sensor, der die Kopplung ohne diesen Lauf hält, ist nicht gebaut und bleibt Entscheidung des Nutzers. Die Kopplung steht im Kopf der Dateien; neu kommt hinzu, dass der Kotlin-Test `KompatibilitaetsTablesTest` Alter 36 und drei Zeilen an einem vierten Ort führt (Review F-5, Kommentar ergänzt).
- Der Live-Lauf ist zuletzt am 2026-11-06 grün gefahren und seitdem nicht wiederholt ([Results-Notiz von welle-referenzabgleich-release](../done/welle-referenzabgleich-release-results.md)); Seit-dem-Änderungen (Class 16, A-10, A-12, A-19, A-20 entfallen, Mehrfach-RPC) sind in ihm nicht belegt. — **Ausgang:** **entfallen:** der Live-Lauf auf `07eec886` (Verifier, 2026-11-19, `live-2022` und `live-2025`, je 25 Schritte) endet mit Exit 0; eine Abweichung an anderer Stelle als den gelisteten Regeln ist nicht aufgetreten.
- Größe: drei Liefer-Punkte. — **Ausgang:** **entfallen:** drei Liefer-Punkte geliefert, keine Rückführung nach §4. Der Rest von A-14 (`kaputt.Text`) ging nicht aus Größe, sondern wegen des Befundes zu `SimColumn.maxLength` an einen Folge-Slice (Nutzerentscheidung 2026-11-19).
- Neu eingetreten: der Plan nannte als Mittel „`kaputt.Text` mit `maxLength = 10` in der Fixture, ohne die Bibliothek zu ändern“ (§1, gelesen); der Implementer fand beim Bau, dass das Feld das COLMETADATA einer Auswahl nicht erreicht. — **Ausgang:** **eingetreten und aufgelöst:** Befund in §1, Entscheidung des Nutzers 2026-11-19, Träger [slice-simulation-spaltenlaenge-in-auswahl](../open/slice-simulation-spaltenlaenge-in-auswahl.md); als Beobachtung geführt (§7).

## 7. Closure-Notiz

Geschlossen 2026-11-19 (Planner, Slice-Closure ohne Welle). Geprüfter Stand: Zweig `slice-kompatibilitaetsserver-an-referenz-angleichen`, Spitze `9130bfc6` (Umsetzung `a659f3f8`, Review-Report `0814b9bc`, Nachbesserung `0bdd8db4`, Folge-Slice angelegt `1d5193a5`, Slice eingeengt `07eec886`, Verifikationsbericht); danach nur Doku (diese Closure). Prüfung: [Review](../../../reviews/2026-11-19-slice-kompatibilitaetsserver-an-referenz-angleichen.md) (kein HIGH; F-1 bis F-3 MEDIUM, in `0bdd8db4` behoben; F-4, F-5 LOW, in `0bdd8db4` behoben; F-6 bis F-9 INFO), [Verifikation](../../../reviews/2026-11-19-slice-kompatibilitaetsserver-an-referenz-angleichen-verifikation.md) (eingeengte DoD erfüllt; F-V1 bis F-V4 INFO).

**Gegenstand.** Der Kompatibilitätsserver meldet für `UPDATE` und `DELETE` auf `person` die Zahl der Zeilen, auf die die Bedingung zutrifft (`onUpdate`, `onDelete` zählen mit `RowRules.matches` über den unveränderten Bestand; die Konstanten `UPDATED_ROWS` und `DELETED_ROWS` sind entfallen), und `laufend.Id` ist nullbar ohne Schlüssel. Entscheidung des Nutzers 2026-11-18: Option A, Code angleichen. Entfallen sind die Live-Regeln `aendern` und `loeschen` (A-9) und die Mitschnitt-Regel `cancel-streaming/01/colmetadata` (A-14, Teil `laufend.Id`). **Nicht geliefert:** `kaputt.Text` (Länge 10, Regel `fehler-nach-zeilen/01/colmetadata`): `SimColumn.maxLength` erreicht das COLMETADATA einer Auswahl nicht, die Fixture allein ändert die Länge 8000 nicht; Nutzerentscheidung 2026-11-19: Bibliothek ändern, Träger [slice-simulation-spaltenlaenge-in-auswahl](../open/slice-simulation-spaltenlaenge-in-auswahl.md) (`open/`, `Verantwortlich: —`). Der Wortlaut der DoD ist an diese Einengung angepasst (Commit `07eec886`, vor der Verifikation); die Verifikation maß die eingeengte DoD.

**Beleg je DoD-Punkt (Bestätigung durch den Verifier).**

- Punkt 1, A-9: erfüllt. Live-Lauf `live-2022` und `live-2025` auf `07eec886`: Schritte `aendern` und `loeschen` je Referenz und Server `ExecuteNonQuery 1`, „gleich“ ohne Regel; die zwei Regeln sind aus `live-ausnahmen.tsv` entfallen.
- Punkt 2, A-14 `laufend.Id`: erfüllt. Mitschnitt-Lauf `mitschnitt-2022` und `-2025` (je 324 Schritte): `cancel-streaming/01/colmetadata` „gleich“ ohne Regel; `fehler-nach-zeilen/01/colmetadata` bleibt als Art *Lücke* mit dem Grund `kaputt.Text` (Länge 20 gegen 8000) und ist genau diese Regel.
- Punkt 3, Nachweis: erfüllt. `make kompatibilitaet` (Verifier, Exit 0, Kategorie `Szenario` 14 Tests, darunter beide geänderten `SzenarienTests`, 15 s), Mitschnitt-Lauf 30 s, Live-Lauf 88 s.
- Sensoren der Art: `make clean-build` Exit 0 (580 s), `make e2e` Exit 0 (9 Stufen, 414 s), beide auf dem End-Stand `07eec886`. `make gates`, `make spec-abdeckung`, `make test` und die Mutationen sind Pflicht des Implementers; sein Bericht steht in keiner Datei des Repos, der Verifier fuhr sie nicht (Zuständigkeit nach `harness/README.md` §Minimal agent workflow). `make docs-check` und `make gates` auf dem Stand dieser Closure stehen in der Übergabe des Planners.

**Mutationsbeleg des Implementers.** Gefahren und belegt (Bericht des Implementers an den Auftraggeber dieser Closure, nicht als Datei im Repo): `onDelete` auf die Konstante 3 gesetzt macht den Test `onUpdate und onDelete von person melden die Zahl der zutreffenden Zeilen` rot. **Nur gelesen, nicht gefahren:** die drei Mutationen des Reviews (`onUpdate` auf die Konstante 2; `matching` ohne Filterauswertung; `laufend.Id` mit `nullable = false` oder `primaryKey`); ihre Wirkung ist aus dem Code abgeleitet (Review, Kopf des Reports).

**Zuordnung je Aussage.**

- *Gemessen* (echter Server, Einzelmessung 2026-11-06, Zeile A-9 des Messprotokolls, am 2026-11-19 im Live-Lauf je Instanz wiederholt): die Referenz meldet für `UPDATE person SET Age = 40 WHERE Id = 1` und `DELETE FROM person WHERE Id = 3` je eine betroffene Zeile.
- *Selbstkonsistenz:* die Zahlen 1, 1, 0, 3, 2, 3, 1 des Kotlin-Tests folgen aus den Daten von `person` (drei Zeilen, Alter 36, NULL, 36); die Trefferzahlen 2, 0, 3 des .NET-Tests sind am Server geprüft, an der Referenz nicht (F-V1).
- *Wiedergabe:* `laufend.Id` nullbar ist gegen die Aufnahme beider Instanzen belegt, nicht gegen eine frische Abfrage der Referenz (F-V2); die Aufnahme ist die Referenz des Mitschnitt-Laufs.
- *Gelesen, nicht gemessen:* `SimColumn.maxLength` erreicht das COLMETADATA einer Auswahl nicht ([SPEC-035](../../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends), `SimulationBackend.kt` baut `ResultColumn(name, type, nullable)`).

**Was hat funktioniert.** Das Risiko „die Zahlen 2 und 3 trennen die Anweisungen“ stand vorab im Plan (§6) und fiel beim Bau nicht aus: die Trennung zog in einen eigenen Test mit verschiedener Trefferzahl um, statt zu verschwinden. Die Zählung aus der Bedingung braucht keinen veränderlichen Bestand und keine Reihenfolge der Tests. Der Implementer meldete den Befund zu `kaputt.Text` beim Bau statt die Zusage still zu verengen; die Nutzerentscheidung kam vor der Closure und der Folge-Slice lag vor der Verifikation.

**Was ging anders als geplant.** (a) §1 nannte `kaputt.Text` mit `maxLength = 10` in der Fixture als ausführbar ohne Bibliotheksänderung („Mittel auf Seiten des Servers (gelesen)“); gelesen war, dass das Feld existiert, nicht, dass es das COLMETADATA erreicht. Der Slice wurde auf A-9 und `laufend.Id` eingeengt. (b) Review F-1: fünf Stellen in §1, §3 und §4 sagten nach dem Befund weiter das Gegenteil (zweite Fundstelle); F-2: der Ergebnis-Absatz im Messprotokoll mischte Messstand 2026-11-06 und heutige Liste; F-3: die Plan-Tabelle nannte die neue Testdatei nicht. Alle drei sind in `0bdd8db4` behoben.

**Nachbesserung ohne zweiten Review.** Commit `0bdd8db4` ändert Testcode und Tags (F-4: wertlose Zusicherung entfernt, F-5: Kopplungskommentar im Kotlin-Test, F-7: `@spec SPEC-035` und `SPEC-045` samt `docs/plan/spec-abdeckung.md`) neben Text und Doku. Ein zweiter Review lief nicht. Stattdessen geprüft: `make clean-build` (Tests und detekt frisch), `make e2e` und `make kompatibilitaet` des Verifiers auf dem End-Stand `07eec886`, der `0bdd8db4` enthält. Nicht geprüft: die Mutationen auf dem Stand nach der Nachbesserung.

**Steering-Loop-Eintrag.** *Geschärfte Regel: nicht verkörpert, gezählt.* Neue Klasse `BEO-TDS/plan-nennt-mittel-ohne-weg-vom-feld-zum-draht` (1×, unter der Schwelle): ein Plan nennt ein Mittel („ohne die Bibliothek zu ändern“) nach dem Lesen der Fixture und des Feldes, ohne das Feld bis zur Ausgabe am Draht zu verfolgen; der Bau widerlegt es. Lerneffekt, noch nicht verkörpert: eine Plan-Aussage über die Ausführbarkeit eines Mittels nennt den Weg vom Feld bis zur Ausgabe oder steht als „nicht bis zum Draht verfolgt“. Verwandt, eigener Mechanismus: `BEO-TDS/plan-nimmt-compilerzwang-an-when-mit-else-an` (dort: ein Zwang, der nicht besteht). Kein `liegt in`: keine Regel und kein Sensor wurde verkörpert. *Wiederauftreten verkörperter Klassen,* je als Evidenz geführt, Retirement-Check „die Klasse ist wiedergekehrt, die Regel bleibt“: `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` (F-1), `BEO-TDS/plan-tabelle-nennt-neue-datei-nicht` (F-3), `BEO-TDS/zahl-an-mehreren-orten-ohne-sensor` (F-5), `BEO-TDS/nachbesserung-ohne-zweiten-review` (Vermerk oben). Der Reviewer fing F-1, F-3 und F-5; ob der Implementer Schritt 20c (Grep der Kernaussage) fuhr, steht in keinem Bericht. *Benannte Lücken des Belegs, offen, Träger Nutzer:* (1) F-V1: die Zählung der Referenz bei 0, 2 und 3 betroffenen Zeilen ist nicht gemessen; [SPEC-035](../../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) sagt „Zahl der betroffenen Zeilen“, belegt ist nur der Fall 1 (A-9); (2) F-V3: von den benannten Mutationen ist nur die Konstante in `onDelete` als gefahren belegt (siehe oben), und dieser Beleg steht in keiner Datei. Beide stehen in diesem Absatz, nicht in einer versionierten Spec, daher ohne `LH-*`-Kennung.

**Beobachtungs-Register (`../observations/`).** `BEO-TDS/callback-zeilenzahl-ist-fixture-konstante`: Ausgang `gestrichen` in `state.md` (die Konstanten sind entfallen, der Callback zählt aus der Bedingung; A-9 hat den Live-Lauf als Sensor). `BEO-TDS/live-lauf-ausserhalb-der-gates-haelt-kopplungen-nicht`: bleibt `offen` (3×, über der Schwelle, Entscheidung beim Nutzer); `state.md` nennt den Stand von A-14 (`laufend.Id` gebaut, `kaputt.Text` bei [slice-simulation-spaltenlaenge-in-auswahl](../open/slice-simulation-spaltenlaenge-in-auswahl.md)); **keine neue Evidenz:** der Slice zeigt keinen neuen Fall eines unbemerkten Kopplungsbruchs, der Nachzug fiel durch den Lauf. `BEO-TDS/mitschnitt-luecken-ohne-traeger`: Evidenz `slice-kompatibilitaetsserver-an-referenz-angleichen.md` und `state.md` fortgeschrieben (A-14 teilweise gebaut). Neu: `BEO-TDS/plan-nennt-mittel-ohne-weg-vom-feld-zum-draht` (1×). Wiederauftreten: je eine Evidenz bei den vier genannten verkörperten Klassen.

**Folge-Slices.** [slice-simulation-spaltenlaenge-in-auswahl](../open/slice-simulation-spaltenlaenge-in-auswahl.md) (`open/`, angelegt in `1d5193a5`, `Verantwortlich: —`): `SimColumn.maxLength` ins COLMETADATA der Auswahl; mit ihm entfällt die Regel `fehler-nach-zeilen/01/colmetadata`. Die Frage, ob eine Folge-ADR nötig ist, steht dort (Befund: keine erwartet, Urteil beim Architect).

**Offen, Träger Nutzer (nicht entschieden, nicht erledigt).**

1. **Referenz-Zählung bei 0, 2 und 3 Treffern (F-V1):** messen (Einzelmessung je Instanz, zusätzliche Schritte im Live-Lauf oder ein Messprotokoll-Eintrag), oder bleibt die Aussage auf Fall 1 beschränkt?
2. **Mutationen (F-V3):** die drei nur gelesenen Mutationen nachfahren, oder als Grenze annehmen?
3. **Sensor für die Kopplung von Schema der Referenz und Tabellen des Servers:** weiter ohne Träger (`BEO-TDS/live-lauf-ausserhalb-der-gates-haelt-kopplungen-nicht`); ein Sensor ist Regel- oder Gate-Entscheidung des Nutzers.
4. **Test `…_im_Batch_…` (Review F-9, Verifikation F-V4):** der Name sagt „im Batch“, die drei Erwartungen sind alle 1; umbenennen oder lassen (INFO).

**Risiken (Ausgänge in §6).** Eingetreten und aufgelöst: Trennung der Anweisungen im Test, `maxLength` erreicht das COLMETADATA nicht. Weiter offen, Träger Nutzer: Kopplung ohne Sensor. Entfallen: Live-Lauf lange nicht gefahren, Größe.

**Gates.** `make docs-check` und `make gates` Exit 0 auf dem Stand dieser Closure stehen in der Übergabe des Planners, nicht in dieser Notiz.

**Herkunft:** Lücken A-9 und A-14 der Ausnahmeliste, [Messprotokoll des Referenzabgleichs](../../../messungen/referenzabgleich.md); Register `BEO-TDS/callback-zeilenzahl-ist-fixture-konstante` (Träger-Vorschlag: Zählung aus dem Zustand oder Konstanten auf 1, „kleiner eigener Slice“) und `BEO-TDS/live-lauf-ausserhalb-der-gates-haelt-kopplungen-nicht` (Kopplung für A-14); [Results-Notiz von welle-referenzabgleich-release](../done/welle-referenzabgleich-release-results.md). Zusammengelegt, weil beide dieselbe Datei (`KompatibilitaetsServer.kt`), dasselbe Szenario-Gerüst und denselben Nachweis (Live- und Mitschnitt-Lauf) tragen und keine die Bibliothek ändert. Nutzerentscheidung 2026-11-17: „ja slices anlegen“ (Frage der Closure von `welle-referenzabgleich-release`, Punkt: die 14 Lücken-Nummern ohne Träger-Slice).

## 8. Sub-Area-Prüfungen und Modus-Begründung

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register (`../observations/BEO-TDS/`) gegen die Verzeichnisliste gelesen; Treffer: `callback-zeilenzahl-ist-fixture-konstante` (offen), `live-lauf-ausserhalb-der-gates-haelt-kopplungen-nicht` (offen, über der Schwelle). Zähler-Stände stehen in den Dateien des Registers, nicht hier.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
