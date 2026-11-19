# Slice slice-simulation-spaltenlaenge-in-auswahl: Simulations-Backend: `SimColumn.maxLength` erreicht das COLMETADATA einer Auswahl (A-14, `kaputt.Text`)

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** ohne Welle (keine Closure-Bedingung außerhalb der DoD dieses Slice; Träger des Rests von A-14 der Ausnahmeliste des Referenzabgleichs, übergeben von [slice-kompatibilitaetsserver-an-referenz-angleichen](../done/slice-kompatibilitaetsserver-an-referenz-angleichen.md)).

**Bezug:** [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) (Scope: Tabellenbeschreibung des Simulations-Backends), [TDS-FA-05](../../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen) (Spaltenbeschreibung eines Ergebnisses), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) (Entscheidung zu `SimColumn.maxLength`, Accepted, unverändert)

**Berührte Spec-Stellen:** [SPEC-035](../../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) (Aussage „ändert die Metadaten eines Ergebnisses nicht“) · [SPEC-003](../../../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog) (`ResultColumn.maxLength`) · [SPEC-045](../../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig) (Ausnahmeliste).

**Verantwortlich:** pt9912

**Autor:** Planner. **Datum:** 2026-11-19.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten.

**Ziel:** Das COLMETADATA einer Auswahl des Simulations-Backends meldet für eine Textspalte die Länge der Tabellenbeschreibung (`SimColumn.maxLength`), so dass `kaputt.Text` mit `maxLength = 10` im Kompatibilitätsserver wie in der Referenz `nvarchar(10)` (Länge 20) ergibt und die Mitschnitt-Regel `fehler-nach-zeilen/01/colmetadata` entfällt.

**Entscheidung des Nutzers (2026-11-19): Code angleichen, die Bibliothek wird geändert.** `SimColumn.maxLength` wird ins COLMETADATA der Auswahl durchgereicht (die Spezifikation ist das Zielbild, eine Abweichung des Servers ist Schuld; Fortsetzung der Entscheidung vom 2026-11-18 in [slice-kompatibilitaetsserver-an-referenz-angleichen](../done/slice-kompatibilitaetsserver-an-referenz-angleichen.md) §1).

**Ist-Stand** (gelesen am 2026-11-19; was nicht als gemessen steht, ist aus Datei oder Code gelesen):

- `SimulationBackend.kt` baut in zwei Stellen (Zeilen 248 und 257, Fall „Bedingung nie wahr“ und Normalfall) `ResultColumn(it.name, it.type, it.nullable)` ohne Länge; `ResultColumn.maxLength` hat dort den Vorgabewert 4000 (Länge 8000 im COLMETADATA, `ColumnMapping.kt` bildet `UnicodeText` auf `NVARCHAR(<ResultColumn.maxLength>)` ab).
- Die KDoc von `SimColumn` und [SPEC-035](../../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) sagen ausdrücklich, die Länge „ändere die Metadaten eines Ergebnisses nicht“; diese Aussage ist die Setzung, die der Slice umkehrt. Sie steht in Spezifikation und KDoc, nicht in einer ADR (Befund zur ADR-Frage unten).
- Wo `SimColumn.maxLength` bereits wirkt: Katalogantworten (`CatalogAnswers.kt`), `MERGE` (`SimulationMerges.kt`, Text über der Länge ist 2628) und die Probe des Massenladens (`BulkCopyForms.kt` liest `maxLength`, `UNBOUNDED_TEXT` gilt dort als 4000).
- `SimColumn.maxLength` hat den Wertebereich 1 bis 4000 oder -1 (`nvarchar(max)`); `ResultColumn.maxLength` erlaubt nur 1 bis 4000. Die Abbildung von -1 ist offen (§6).
- Fixture: `kaputt` in `KompatibilitaetsServer.kt` führt `Text` ohne Länge (Vorgabe 4000); der Mitschnitt vergleicht gegen die Aufnahme der Referenz (`nvarchar(10)`, Länge 20). Die Regel `fehler-nach-zeilen/01/colmetadata` in `referenztest-dotnet/Vergleich/mitschnitt-ausnahmen.tsv` benennt diese Lücke; die zweite Regel zu A-14 (`cancel-streaming/01/colmetadata`, `laufend.Id` nullbar) ist in [slice-kompatibilitaetsserver-an-referenz-angleichen](../done/slice-kompatibilitaetsserver-an-referenz-angleichen.md) erledigt.

**Befund zur ADR-Frage (Planner, gelesen, keine Entscheidung):** Eine Folge-ADR ist nach Lage der Texte **nicht** nötig; dies prüft der Architect vor dem Start.

- Keine neue öffentliche Fläche: `ResultColumn.maxLength` ist öffentlich seit der Änderung vom 2026-11-08 (Änderungslog der Spezifikation, `SPEC-059`); `SimColumn.maxLength` seit [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md). `make flaeche` vergleicht Typnamen, keine Mitglieder; es ändert sich weder Typ noch Signatur.
- [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 2 (`Accepted`, unveränderlich) begründet das Feld mit „damit der Katalog die Länge der Beschreibung meldet“ und sagt nichts darüber, ob die Länge im Ergebnis erscheint; der Ausschluss der Ergebnis-Metadaten steht nur in SPEC-035 und in der KDoc. Die Spezifikation ist fortschreibbar (Rang 2), die Änderung ist eine Spec-Änderung mit Eintrag im Änderungslog, keine Überschreibung einer ADR.
- Zu prüfen bleibt, ob die Abbildung von -1 auf 4000 und die Wirkung auf Zeilen über der Länge (siehe §6) eine Entscheidung tragen, die eine ADR braucht; sagt der Architect ja, ist die ADR ein Lieferpunkt vor dem Code (neue ADR, kein Überschreiben von [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md)).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Die Prüfung der Zeilen der Zeilenquelle gegen `SimColumn.maxLength` — SPEC-035 nimmt sie aus der Prüfung aus; was mit einem Text über der Länge im Ergebnis geschieht, ist ein eigener Befund (§6), den der Slice nur belegen, nicht neu setzen soll.
- Option B für A-14 (Ansicht `kaputt` in `testschema.sql`, neue Aufnahmen beider Instanzen) — vom Nutzer abgelehnt (Code angleichen); nicht nötig.
- Die Antwort auf `SchemaOnly` und `KeyInfo` (A-8) — [slice-simulation-schemaonly-keyinfo](../open/slice-simulation-schemaonly-keyinfo.md); anderer Gegenstand.
- `laufend.Id` nullbar und A-9 — erledigt in [slice-kompatibilitaetsserver-an-referenz-angleichen](../done/slice-kompatibilitaetsserver-an-referenz-angleichen.md).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit.

- [ ] **Bibliothek:** `SimulationBackend` übergibt `SimColumn.maxLength` an `ResultColumn` (beide Stellen); die Abbildung von -1 ist festgelegt und belegt; `SimColumn`-KDoc, SPEC-035, SPEC-003 und das Änderungslog der Spezifikation sagen dasselbe; Tests je Zusage (Länge im COLMETADATA einer Auswahl, Vorgabe 4000 bleibt, -1, Bedingung nie wahr).
- [ ] **Fixture und Ausnahmeliste:** `kaputt.Text` hat `maxLength = 10` in `KompatibilitaetsServer.kt`; die Regel `fehler-nach-zeilen/01/colmetadata` in `mitschnitt-ausnahmen.tsv` entfällt; A-14 im [Messprotokoll](../../../messungen/referenzabgleich.md) ist geschlossen.
- [ ] **Nachweis:** Mitschnitt-Lauf (`LAUF=mitschnitt`) gegen die verkleinerte Liste grün; `make kompatibilitaet` (Nicht-Gate) auf dem End-Stand im Verifier-Lauf.
- [ ] `make gates` Exit 0, dazu je Art der Änderung nach `harness/README.md` §Minimal agent workflow: engste Testklasse und Mutation je Zusage über enge Testklassen (`src/`, `testserver/`), `make test`, `make referenztest-dotnet` (Selbsttest der Liste), `make docs-check`, `make flaeche`, `make kopplung-zahlen` (Doku/Spec), `make spec-abdeckung` einmal (Tests oder `@spec`-Tags). Verifier fährt `make clean-build` und `make e2e` je einmal auf dem End-Stand; Implementer und Reviewer nicht.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor (`.harness/skills/reviewer.md`), kein Self-Review.
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt: SPEC-035, SPEC-003, Änderungslog, [Messprotokoll des Referenzabgleichs](../../../messungen/referenzabgleich.md), `docs/user/`, soweit die Länge im Ergebnis dort beschrieben ist.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben: `BEO-TDS/live-lauf-ausserhalb-der-gates-haelt-kopplungen-nicht` (A-14, Rest); keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — Pfad-Kandidaten für §8; geprüft ist das Vorhandensein der Dateien am 2026-11-19, nicht ihre Eignung.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/SimulationBackend.kt` | update | beide `ResultColumn(...)` (Zeilen 248, 257) tragen `maxLength` aus `SimColumn`; Abbildung von -1 |
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/SimModel.kt` | update | KDoc von `SimColumn.maxLength`: die Länge gilt auch im Ergebnis |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/result/ColumnMapping.kt` | prüfen | bildet `ResultColumn.maxLength` auf `NVARCHAR` ab; erwartet keine Änderung |
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/BulkCopyForms.kt` | prüfen | liest `maxLength` bereits; Abgleich der Abbildung von -1 mit der Probe des Massenladens |
| `spec/spezifikation.md` (SPEC-035, SPEC-003, Änderungslog) | update | Aussage „ändert die Metadaten eines Ergebnisses nicht“ ersetzen; Abbildung von -1 als Setzung |
| `testserver/kotlin/io/github/pt9912/tdswire/testserver/KompatibilitaetsServer.kt` | update | `kaputt.Text` mit `maxLength = 10` |
| `referenztest-dotnet/Vergleich/mitschnitt-ausnahmen.tsv` | update | Regel `fehler-nach-zeilen/01/colmetadata` entfällt |
| `docs/messungen/referenzabgleich.md` | update | A-14 geschlossen |
| Tests unter `src/test/kotlin/…/simulation/` | neu / update | Länge im Ergebnis, Vorgabe 4000, -1, Bedingung nie wahr; Testdatei nennt die Anforderungs-Kennung |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`open` → `next` → `in-progress`): Nutzerzeichen und ein Verantwortlicher (`Verantwortlich:` ist bis dahin `—`; den Anspruch macht der Orchestrator); die Entscheidung „Code angleichen“ liegt vom 2026-11-19 vor. Vor dem Code urteilt der Architect über die ADR-Frage (§1) und die Fragen in §6.

**Rückführungen — vorab benennen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2, etwa wenn eine ADR nötig wird.
- `in-progress` → `open` (blockiert): die Abbildung von -1 oder die Wirkung auf Zeilen über der Länge ist ohne Entscheidung des Nutzers nicht setzbar.

## 5. Closure-Trigger

Alle Punkte der DoD in §2 erledigt, `make gates` und `make docs-check` Exit 0, die Regel `fehler-nach-zeilen/01/colmetadata` der Ausnahmeliste ist entfallen (nicht still stehen gelassen), und die Closure-Notiz mit Steering-Loop-Lerneintrag ist geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang.

- `SimColumn.maxLength` -1 (`nvarchar(max)`) ist als `ResultColumn.maxLength` nicht darstellbar (Bereich 1 bis 4000); die Abbildung auf 4000 ist eine Setzung, die einen echten `nvarchar(max)` im Ergebnis nicht trifft. — **Ausgang:** offen bis zur Closure; Architect setzt die Abbildung, SPEC-035 nennt sie als Setzung.
- Ein Text einer Zeile über `ResultColumn.maxLength` weist die Bibliothek ab (KDoc `QueryEvent.kt`), während SPEC-035 die Zeilen der Zeilenquelle nicht gegen `SimColumn.maxLength` prüft: mit durchgereichter Länge kann eine bisher gelieferte Zeile in der Antwort scheitern. — **Ausgang:** offen bis zur Closure; Test mit Zeile über der Länge, Verhalten wird festgestellt und in SPEC-035 gesagt; fällt es nicht mit „unverändert“ zusammen, ist es eine Entscheidung des Nutzers.
- Bestehende Tests und Einbettungstests, die eine Länge 4000 im Ergebnis voraussetzen, obwohl die Tabelle eine kürzere führt. — **Ausgang:** offen bis zur Closure; Suche in `src/test` und `src/integrator` zu Beginn.
- Ob eine Folge-ADR nötig ist (§1). — **Ausgang:** offen bis zur Closure; Urteil des Architects vor dem Code.
- Größe: drei Liefer-Punkte. — **Ausgang:** offen bis zur Closure; Rückführung nach §4.

## 7. Closure-Notiz

Wird bei der Closure gefüllt.

**Herkunft:** Rest von A-14 der Ausnahmeliste, [Messprotokoll des Referenzabgleichs](../../../messungen/referenzabgleich.md); Befund des Implementers in [slice-kompatibilitaetsserver-an-referenz-angleichen](../done/slice-kompatibilitaetsserver-an-referenz-angleichen.md) §1 (Stand 2026-11-19: `SimColumn.maxLength` erreicht das COLMETADATA einer Auswahl nicht); Nutzerentscheidung 2026-11-19: Bibliothek ändern, Code angleichen; Register `BEO-TDS/live-lauf-ausserhalb-der-gates-haelt-kopplungen-nicht`.

## 8. Sub-Area-Prüfungen und Modus-Begründung

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register (`../observations/BEO-TDS/`) gegen die Verzeichnisliste gelesen; Treffer: `live-lauf-ausserhalb-der-gates-haelt-kopplungen-nicht` (offen, über der Schwelle; Kopplung von Schema der Referenz und Tabellen des Servers). Zähler-Stand steht in der Datei des Registers, nicht hier.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
