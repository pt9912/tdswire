# Slice slice-qa-messung-anweisungsformen-grenzen: Grenzen von `OR`, `SELECT <Ganzzahl>` und `WAITFOR DELAY` am echten Server messen

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** ohne Welle (keine Closure-Bedingung außerhalb der DoD dieses Slice; Folge-Slice der Closure von [slice-simulation-anweisungsformen-erweitern](../done/slice-simulation-anweisungsformen-erweitern.md): die Grenzen der drei Formen sind dort als Setzung gebaut, nicht gemessen).

**Bezug:** [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität) (Scope: Abgleich mit dem echten Server), [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), [ADR-0058](../../adr/0058-or-in-der-auswahl-ganzzahlkonstante-und-waitfor-delay-sind-erkannt.md) (Entscheidungen 3 bis 5: die Grenzen sind dort Setzung), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)

**Berührte Spec-Stellen:** [SPEC-036](../../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) (Abschnitt „Term“, Form C mit den „Ungemessenen Setzungen“, Form Z) · [SPEC-045](../../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig) (Ausnahmeliste, falls eine Messung eine Regel verlangt) · [Messprotokoll des Referenzabgleichs](../../../messungen/referenzabgleich.md) (Abschnitt „Anweisungsformen erweitert“).

**Verantwortlich:** —

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-22.

---

## 1. Ziel und Abgrenzung

**Ziel:** Jede Grenze der drei Formen, die [slice-simulation-anweisungsformen-erweitern](../done/slice-simulation-anweisungsformen-erweitern.md) als Setzung gebaut hat und die sich am echten Server messen lässt, ist an beiden Instanzen (2022, 2025) mit dem Referenzclient gemessen; wo die Messung der Setzung widerspricht, entscheidet der Nutzer „Code angleichen“ oder „Zielbild anpassen“ (die Spezifikation ist das Zielbild, eine Abweichung ist Schuld).

**Ist-Stand** (aus [SPEC-036](../../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) und dem Messprotokoll, Stand 2026-11-22; was nicht als gemessen steht, ist Setzung):

- **Gemessen** (Läufe vom 2026-11-22, je ein Lauf je Instanz): `SELECT 2` und `SELECT Id FROM person WHERE Id = 1 OR Id = 2` mit Spaltentyp und Werten; `WAITFOR DELAY '00:00:10'` mit Abbruch durch ATTENTION.
- **Zu messen, lässt sich am echten Server messen:**
  - `OR`: `IN` und `IS [NOT] NULL` in der Folge, mehr als zwei Terme, `NULL` in `=` und `IN` der Folge (die Auswertung ist abgeleitet), Terme auf verschiedenen Spalten.
  - `SELECT <Ganzzahl>`: andere Werte als 2 (0, negative Werte, `2147483647`), `SELECT 01` und `SELECT -0`, `-2147483648` (nach Kenntnis in T-SQL kein `int`-Literal), `2147483648` (Typ der Konstante), `SET FMTONLY ON SELECT 2`.
  - `WAITFOR DELAY`: das reguläre Ende der Wartezeit (Antwort, Änderungszähler), der Bereich von `hh:mm:ss` (`00:00:00`, Stunden und Minuten über dem Bereich, Bruchteile), eine Variable als Frist.
- **Lässt sich nicht messen, bleibt Setzung:** `QUERY_TIMEOUT` kürzer als die Wartezeit endet mit `TDS_TIMEOUT`; der echte Server kennt keine Frist des Servers, die Referenz hat nur die Frist des Clients ([SPEC-036](../../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig), Form Z).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Neue Anweisungsformen (`NOT`, `<`, `>`, `LIKE`, `BETWEEN`, `<>`, `!=`, `NOT IN`, `OR` in `UPDATE` und `DELETE`): bleiben abgewiesen, [ADR-0058](../../adr/0058-or-in-der-auswahl-ganzzahlkonstante-und-waitfor-delay-sind-erkannt.md) Entscheidungen 1 und 3; eine neue Form braucht eine Entscheidung des Nutzers.
- Andere Clients als `Microsoft.Data.SqlClient` 7.1.1 (mssql-jdbc, d-migrate): nicht gemessen, kein Träger ([AGENTS.md](../../../../AGENTS.md) §3.8).
- Die Antwort von `SchemaOnly` und `KeyInfo` (`SET FMTONLY` als Text von `SchemaOnly`): [slice-simulation-schemaonly-keyinfo](../in-progress/slice-simulation-schemaonly-keyinfo.md); hier nur `SET FMTONLY ON SELECT 2` als Grenze der Form C.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit.

- [ ] **Messung am echten Server:** je Zeile der Liste „Zu messen“ eine Messzeile an beiden Instanzen (Szenarien der Kategorie `MessungWire` oder neue Live-Schritte, Einzelmessung mit Datum, Host, Bedingung); ein Fall, der nicht herstellbar ist, steht als „nicht lieferbar“ mit Begründung. Die Messläufe sind Nachweis, kein Gate ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)).
- [ ] **Abgleich je Messzeile:** deckt die Messung die Setzung, steht sie in [SPEC-036](../../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) als gemessen; widerspricht sie, entscheidet der Nutzer „Code angleichen“ oder „Zielbild anpassen“, und der Slice führt es aus (Tests je Form, Ausnahmeliste, falls ein Schritt des Live-Laufs abweicht).
- [ ] **Nachzug:** [SPEC-036](../../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) (die „Ungemessenen Setzungen“ und die Sätze „Setzung, nicht gemessen“), [Messprotokoll des Referenzabgleichs](../../../messungen/referenzabgleich.md), [Kompatibilitätsmatrix](../../kompatibilitaetsmatrix.md), das KDoc von `SimulationBackendDisjunctionTest` (Satz „gemessen am Server ist nur der Erfolg von `Id = 1 OR Id = 2`“, durch den Lauf vom 2026-11-22 überholt) und das Änderungsprotokoll.
- [ ] `make gates` Exit 0, dazu je Art der Änderung nach `harness/README.md` §Minimal agent workflow: `make docs-check`, `make flaeche`, `make kopplung-zahlen` (Doku/Spec), `make spec-abdeckung` einmal (Tests oder `@spec`-Tags), bei Änderung unter `src/`: engste Testklasse und Mutation je Zusage (Verifier fährt `make clean-build` und `make e2e` je einmal auf dem End-Stand; Implementer und Reviewer nicht).
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor (`.harness/skills/reviewer.md`), kein Self-Review.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben: `BEO-TDS/setzungen-der-anweisungsformen-ungemessen` (Ausgang nachführen); keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — Pfad-Kandidaten für §8; geprüft ist das Vorhandensein der Dateien am 2026-11-22, nicht ihre Eignung.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `referenztest-dotnet/Vergleich/MessungWireTests.cs`, `referenztest-dotnet/Vergleich/LiveSzenarioTests.cs`, `referenztest-dotnet/Vergleich/live-ausnahmen.tsv` | update | Messszenarien je Zeile der Liste „Zu messen“; eine Regel nur, wenn ein Schritt abweicht und der Nutzer „so gewollt“ entscheidet |
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/StatementParser.kt`, `SimConditions.kt`, `FixedForms.kt`, `WaitForms.kt`, `WaitExecution.kt` | update, falls eine Messung der Setzung widerspricht und der Nutzer „Code angleichen“ entscheidet | Grenzen der Formen |
| `src/test/kotlin/io/github/pt9912/tdswire/simulation/StatementFormsTest.kt`, `FixedFormsTest.kt`, `WaitFormsTest.kt`, `SimulationBackendDisjunctionTest.kt` | update | Tests der geänderten Grenzen; KDoc von `SimulationBackendDisjunctionTest` |
| `spec/spezifikation.md`, `docs/messungen/referenzabgleich.md`, `docs/plan/kompatibilitaetsmatrix.md` | update | SPEC-036, Messprotokoll, Matrix |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf und gleicht sie am Ende gegen `git diff main...HEAD --stat` ab.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`open` → `next` → `in-progress`): Nutzerzeichen und ein Verantwortlicher (`Verantwortlich:` ist bis dahin `—`). Die Messung braucht die gepinnten Referenzinstanzen (EULA bestätigt 2026-11-01 und 2026-11-17), läuft **nicht parallel** zu Last-Messslices, und die Messsperre `/tmp/claude-1000/-Development-tdswire/messlock` gilt für Messläufe.

**Rückführungen — vorab benennen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2, oder die Messung ergibt mehr als einen Widerspruch zur Setzung, der Bau verlangt (Zerlegung nach Form).
- `in-progress` → `open` (blockiert): die gepinnten Referenzinstanzen sind nicht beziehbar.

## 5. Closure-Trigger

Alle Punkte der DoD in §2 erledigt, `make gates` und `make docs-check` Exit 0, jede Zeile der Liste „Zu messen“ ist gemessen oder als „nicht lieferbar“ begründet, und die Closure-Notiz mit Steering-Loop-Lerneintrag ist geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang.

- Eine Messung widerspricht einer Setzung (zum Beispiel akzeptiert der echte Server `-2147483648` als `int`, oder `WAITFOR` mit anderem Bereich); der Bau ändert dann eine erkannte Form. — **Ausgang:** offen bis zur Closure; die Entscheidung „Code angleichen“ oder „Zielbild anpassen“ liegt beim Nutzer, Rückführung nach §4.
- Die Messung eines Falls ist an beiden Instanzen verschieden (2022, 2025). — **Ausgang:** offen bis zur Closure; die Aussage trägt je Instanz, die Setzung des Servers steht im Messprotokoll mit der Instanz.
- Größe: drei Liefer-Punkte (Messung, Abgleich, Nachzug). — **Ausgang:** offen bis zur Closure; Rückführung nach §4.

## 7. Closure-Notiz

Wird bei der Closure gefüllt.

**Herkunft:** Closure von [slice-simulation-anweisungsformen-erweitern](../done/slice-simulation-anweisungsformen-erweitern.md) (2026-11-22): die „Ungemessenen Setzungen“ der Form C (Entscheidung des Nutzers 2026-11-22) und die Sätze „Setzung, nicht gemessen“ zu `OR` und `WAITFOR`; Register `BEO-TDS/setzungen-der-anweisungsformen-ungemessen`; [Verifikationsbericht](../../../reviews/2026-11-22-slice-simulation-anweisungsformen-erweitern-verifikation.md) (Frage 4: was der Lauf nicht trägt).

## 8. Sub-Area-Prüfungen und Modus-Begründung

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register (`../observations/BEO-TDS/`) gegen die Verzeichnisliste gelesen; Treffer: `setzungen-der-anweisungsformen-ungemessen` (offen), `anweisungsformen-des-simulations-backends-enger-als-der-server`, `messung-am-echten-sql-server-fehlt`, `schemaonly-keyinfo-fmtonly-ohne-traeger`. Zähler-Stände stehen in den Dateien des Registers, nicht hier.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
