# Slice slice-fehlertext-unbekannte-tabelle-angleichen: Fehlertext der unbekannten Tabelle: „Invalid object name '<Name>'.“ statt „Unknown object.“ (A-3)

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** ohne Welle (keine Closure-Bedingung außerhalb der DoD dieses Slice; Träger der Lücke A-3 der Ausnahmeliste des Referenzabgleichs, angelegt nach Nutzerentscheidung vom 2026-11-17: „ja slices anlegen“ auf die Frage der Closure von [welle-referenzabgleich-release](../done/welle-referenzabgleich-release.md)).

**Bezug:** [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) (Negative: definierter SQL-Fehler), [TDS-FA-06](../../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände) (Fehler und Abschlusszustände), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0007](../../adr/0007-fehler-und-attention.md) (Fehler und Attention), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md)

**Berührte Spec-Stellen:** Abschnitt 4 der Spezifikation „Fehler-Codes und Logging-Felder“, Zeile `TDS_SIM_UNKNOWN_OBJECT` ([Abschnitt 4](../../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder)) · [SPEC-045](../../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig) (Ausnahmeliste).

**Verantwortlich:** pt9912

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-17.

---

## 1. Ziel und Abgrenzung

**Ziel:** Die Antwort des Servers auf eine unbekannte Tabelle trägt den Text der Referenz („Invalid object name '<Name>'.“) und die Felder des ERROR-Tokens, die die Ausnahmeregel mitführt — oder A-3 gilt nach Entscheidung des Nutzers als *so gewollt*.

**Ist-Stand** (gelesen am 2026-11-17; was nicht als gemessen steht, ist aus Datei oder Code gelesen):

- **Stand der Angleichung:** Nummer 208, Class 16, State 1 und die offene Verbindung sind gebaut ([slice-unbekannte-tabelle-class-16-angleichen](../done/slice-unbekannte-tabelle-class-16-angleichen.md)). Es bleibt der feste Text „Unknown object.“ ohne den Namen der Tabelle (`ErrorCode.kt`, `TDS_SIM_UNKNOWN_OBJECT`, „der Text ist Festlegung“).
- **Die Regeln** ([Messprotokoll des Referenzabgleichs](../../../messungen/referenzabgleich.md), A-3): Live-Schritt `fehler-vor-zeilen` (Referenz „Invalid object name 'gibtesnicht'.“, Server „Unknown object.“) und die Mitschnitt-Regel `fehler-vor-zeilen/01/fehler`. Die Mitschnitt-Regel führt wortgleich zwei weitere Unterschiede: Servername im ERROR-Token „vorhanden“ (Referenz) gegen „leer“ (Server) und Zeilennummer 1 gegen 0. Sie gehören zur Regel; ob der Server Servername und Zeilennummer setzen kann oder soll, ist nicht geprüft.
- **Mittel im Code (gelesen):** `ErrorRule` trägt einen festen Text je Code; `QueryEvent.Failure` hat `code` und `message`, der Text der Ausnahme eines Backends erreicht den Client nie (Abschnitt 4 der Spezifikation: `TDS_BACKEND_ERROR`, A-6 *so gewollt*). Für `TDS_SIM_UNKNOWN_OBJECT` erzeugt die Bibliothek selbst den Fehler (`StatementPlanner.kt`), der Name der Tabelle steht dort zur Verfügung; ein Text mit Namen wäre ein parametrisierter Text.
- **Messbasis:** Text und Nummer stammen aus Messung R05 von [ADR-0007](../../adr/0007-fehler-und-attention.md) und M-15 (`MERGE` gegen eine unbekannte Tabelle) und aus dem Schritt `fehler-vor-zeilen`; gemessen ist **ein** Fall (Name `gibtesnicht` ohne Schema, in `SELECT`). Wie der echte Server Schema, Datenbank, Namen mit Hochkomma oder Bezeichner über 128 Zeichen im Text nennt, ist nicht gemessen.
- **Abgrenzung zu A-6:** der Fehler der Zeilenquelle nach Zeilen (`TDS_BACKEND_ERROR`) ist *so gewollt* (Text der Ausnahme erreicht den Client nie); A-3 ist ein Fehler, den die Bibliothek selbst erzeugt, und kein Backend-Text.

**Entschieden (Nutzer, 2026-11-18): Option A, Code angleichen.** Die Frage unten war „Code angleichen“ oder „Zielbild anpassen“; die Spezifikation ist das Zielbild.

**Frage an den Nutzer — „Code angleichen“ oder „Zielbild anpassen“ (entschieden, siehe oben).** Die Spezifikation ist das Zielbild, eine Abweichung des Servers ist Schuld; ob sie hier durch Bau behoben wird oder ob Spezifikation und Ausnahmeliste dem Ist-Stand folgen, entscheidet der Nutzer, nicht der Planner. Der Slice startete nach der Antwort.

- **Option A — Code angleichen (gewählt):** der Server schreibt „Invalid object name '<Name>'.“ mit dem Namen aus der Anweisung (Schreibweise, Quoting und Grenzen nach Messung); Servername und Zeilennummer nach Messung und Entscheidung. Der Name stammt vom Client selbst; Länge, Zeichen und Hochkomma im Namen sind zu begrenzen und zu escapen (Kommentar der Regel A-6: der Text einer Ausnahme der Quelle soll nie zum Client).
- **Option B — Zielbild anpassen:** der feste Text „Unknown object.“ bleibt; A-3 wird *so gewollt* mit der Begründung des Nutzers (etwa: kein Eingabewert in Fehlertexten). Ein Client, der den Text parst, ist im Referenzabgleich nicht beobachtet (der Referenzclient meldet `Number 208`; das Verhalten ist gleich, nur der Text weicht ab).
- **Empfehlung des Planners (Setzung, keine Entscheidung):** Option A mit gemessenen Grenzen — die Spezifikation ist das Zielbild, und die Abweichung ist ein reiner Textunterschied bei sonst gleichem Verhalten. Die Messung vor dem Bau (DoD 1) klärt, was der echte Server bei Schema, Hochkomma und Länge schreibt; sie kann Option B als vernünftiger zeigen, wenn die Texte nicht festzulegen sind.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Unbekannte Spalte (Nummer 207) — das Mapping bildet sie nicht ab (`ErrorCode.kt`: „207 (Spalte) bildet das Mapping nicht ab“); keine Regel der Listen betrifft sie, ohne Szenario keine Zusage.
- Der Text von `TDS_BACKEND_ERROR` (A-6, *so gewollt*), `TDS_UNSUPPORTED_SQL` und anderer Fehler — fester Text als Festlegung ([ADR-0007](../../adr/0007-fehler-und-attention.md)); keine Regel der Listen.
- Der Servername im ERROR-Token für alle Fehler des Servers — Gegenstand nur, soweit die Regel `fehler-vor-zeilen/01/fehler` ihn mitführt; für A-6 (*so gewollt*) steht er unverändert.
- Der Client-Beleg für mssql-jdbc nach Class 16 — Träger war [slice-abnahme-d-migrate](../done/slice-abnahme-d-migrate.md) (Register `BEO-TDS/unbekannte-tabelle-class-20-statt-16-am-server`).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit.

- [ ] **Messung (klein):** Text und Felder des ERROR bei unbekannter Tabelle mit Namen, Schema, Hochkomma und langem Namen an 2022 und 2025 (Einzelmessung je Instanz, Messzeile im Messprotokoll), bevor ein Text festgelegt wird; ein nicht herstellbarer Fall steht als „nicht lieferbar“.
- [ ] **Angleichung (nach Entscheidung des Nutzers):** `TDS_SIM_UNKNOWN_OBJECT` meldet den Text mit Namen und die entschiedenen Felder; Tests (Happy, Hochkomma im Namen, langer Name, Schema); die Regeln `fehler-vor-zeilen` (Live) und `fehler-vor-zeilen/01/fehler` (Mitschnitt) entfallen — oder Art *so gewollt* mit Begründung.
- [ ] **Nachzug:** Abschnitt 4 der Spezifikation (Zeile `TDS_SIM_UNKNOWN_OBJECT`), [Messprotokoll des Referenzabgleichs](../../../messungen/referenzabgleich.md) (A-3), Register `BEO-TDS/unbekannte-tabelle-class-20-statt-16-am-server`.
- [ ] `make gates` Exit 0, dazu je Art der Änderung nach `harness/README.md` §Minimal agent workflow: engste Testklasse und Mutation je Zusage über enge Testklassen (`src/`), `make docs-check`, `make flaeche`, `make kopplung-zahlen` (Doku/Spec), `make spec-abdeckung` einmal (Tests oder `@spec`-Tags) (`src/` oder `testserver/`: Verifier fährt `make clean-build` und `make e2e` je einmal auf dem End-Stand; Implementer und Reviewer nicht). Die Läufe `make vergleichslauf-d` sind Messung und Nachweis, keine Gates ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)).
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor (`.harness/skills/reviewer.md`), kein Self-Review.
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt: Abschnitt 4 der Spezifikation (Zeile `TDS_SIM_UNKNOWN_OBJECT`), [SPEC-045](../../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig), [Messprotokoll des Referenzabgleichs](../../../messungen/referenzabgleich.md) (A-3), `live-ausnahmen.tsv`, `mitschnitt-ausnahmen.tsv`.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben: `BEO-TDS/unbekannte-tabelle-class-20-statt-16-am-server` (Ausgang nachführen); keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — Pfad-Kandidaten für §8; geprüft ist das Vorhandensein der Dateien am 2026-11-17, nicht ihre Eignung.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/error/ErrorCode.kt`, `ErrorMapper.kt` | update, falls Option A | fester Text je Code; neu: ein Text mit Namen (Parametrisierung des Textes) |
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/StatementPlanner.kt` | update, falls Option A | erzeugt den Fehler `UNKNOWN_OBJECT`; reicht den Namen der Tabelle weiter |
| `src/test/kotlin/io/github/pt9912/tdswire/simulation/SimulationBackendFixedTest.kt`, `SimulationBackendChangeTest.kt`, `SimulationBackendBulkTest.kt`, `BulkCopyFormsTest.kt` | update | vorhandene Tests der Abweisung (Treffer von `TDS_SIM_UNKNOWN_OBJECT` in `src/test`) auf den neuen Text; neue Fälle |
| `referenztest-dotnet/Vergleich/live-ausnahmen.tsv`, `referenztest-dotnet/Vergleich/mitschnitt-ausnahmen.tsv` | update | zwei Regeln A-3 entfallen oder wechseln die Art |
| `spec/spezifikation.md`, `docs/messungen/referenzabgleich.md` | update | Abschnitt 4 (Zeile `TDS_SIM_UNKNOWN_OBJECT`), Tabelle der Ausnahmen |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf (Testdateien mit Kennung der Akzeptanzkriterien).

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`open` → `next` → `in-progress`): Nutzerzeichen, ein Verantwortlicher (`Verantwortlich:` ist bis dahin `—`) und die Antwort auf die Frage in §1 für den Bau; die kleine Messung (DoD 1) ist davon unabhängig und kann vorher starten. Sie braucht die gepinnten Instanzen (EULA bestätigt 2026-11-01 und 2026-11-17), läuft **nicht parallel** zu Last-Messslices, und die Messsperre `/tmp/claude-1000/-Development-tdswire/messlock` gilt für Messläufe.

**Rückführungen — vorab benennen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert): die gepinnten Referenzinstanzen sind nicht beziehbar (Pull, Platz, geänderter EULA-Text).

## 5. Closure-Trigger

Alle Punkte der DoD in §2 erledigt, `make gates` und `make docs-check` Exit 0, die Regeln der Ausnahmeliste, die dieser Slice trägt, sind entfallen oder mit der Entscheidung des Nutzers in ihrer Art geändert (nicht still stehen gelassen), und die Closure-Notiz mit Steering-Loop-Lerneintrag ist geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang.

- Der Name der Tabelle im Fehlertext ist Eingabe des Clients (Länge, Hochkomma, Steuerzeichen); ein Text mit Namen ist eine Ausgabe fremder Eingabe. — **Ausgang:** offen bis zur Closure; Grenzen und Escaping nach Messung und Test; sonst Option B.
- Die Messbasis ist ein Fall (`gibtesnicht`); die Aussage „Text mit Namen“ trägt höchstens so weit ([AGENTS.md](../../../../AGENTS.md) §3.8). — **Ausgang:** offen bis zur Closure; DoD 1 erweitert die Basis auf die genannten Fälle; Rest bleibt „nicht gemessen“.
- Servername und Zeilennummer der Referenz sind Werte, die der echte Server selbst vergibt ([SPEC-045](../../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig): nur auf „vorhanden / nicht vorhanden“ verglichen); ein Gleichlauf des Wertes ist nicht Ziel. — **Ausgang:** offen bis zur Closure; die Regel wird auf „vorhanden“ und „Zeile ≥ 1“ so weit angeglichen, wie der Vergleich es zulässt; sonst bleibt eine verkleinerte Regel stehen.
- Vorhandene Tests und Clients, die den festen Text „Unknown object.“ als Wert erwarten (mehrere Tests in `src/test`, `TestserverLaufInspektionTest`). — **Ausgang:** offen bis zur Closure; die Tests werden mitgezogen; ein Client außerhalb des Repos ist nicht bekannt.
- Größe: drei Liefer-Punkte. — **Ausgang:** offen bis zur Closure; Rückführung nach §4.

## 7. Closure-Notiz

Wird bei der Closure gefüllt.

**Herkunft:** Lücke A-3 der Ausnahmeliste, [Messprotokoll des Referenzabgleichs](../../../messungen/referenzabgleich.md); Register `BEO-TDS/unbekannte-tabelle-class-20-statt-16-am-server` (Offen: der feste Text; „kein Träger“ in [Results-Notiz von welle-referenzabgleich-release](../done/welle-referenzabgleich-release-results.md)); [slice-unbekannte-tabelle-class-16-angleichen](../done/slice-unbekannte-tabelle-class-16-angleichen.md). Nicht im Gruppierungsvorschlag der Closure-Notiz (dort ohne Zeile), deshalb ein eigener Slice. Nutzerentscheidung 2026-11-17: „ja slices anlegen“ (Frage der Closure von `welle-referenzabgleich-release`, Punkt: die 14 Lücken-Nummern ohne Träger-Slice).

## 8. Sub-Area-Prüfungen und Modus-Begründung

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register (`../observations/BEO-TDS/`) gegen die Verzeichnisliste gelesen; Treffer: `unbekannte-tabelle-class-20-statt-16-am-server` (offen, 1×), `anweisungsformen-des-simulations-backends-enger-als-der-server` (gleiche Fehlerfamilie). Zähler-Stände stehen in den Dateien des Registers, nicht hier.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
