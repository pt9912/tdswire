# Slice slice-e2e-backend-ausserhalb-modul: E2E-Stufe für ein eigenes Backend und seinen Fehlpfad gegen den Serverprozess

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.

**Welle:** welle-qualitaet-grundlage

**Bezug:** [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-RB-04](../../../../spec/lastenheft.md#tds-rb-04--externes-backend) (Scope); [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) (öffentlicher Integrations-Einstieg), [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md) (E2E-Läufe nicht in den Gates)

**Berührte Spec-Stellen:** —

**Verantwortlich:** —

**Autor:** pt9912. **Datum:** 2026-11-05.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice.

**Ziel:** Eine E2E-Stufe belegt für [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung) und [TDS-RB-04](../../../../spec/lastenheft.md#tds-rb-04--externes-backend), was die Sicht heute in keiner Stufe deklariert: ein eigenes In-Memory-Backend (eine eigene `QueryBackend`-Implementierung, die nicht an das Simulations-Backend delegiert) wird über die öffentliche Fläche angebunden und im Serverprozess von einem Client erreicht, und ein eigenes Backend, dessen `execute` scheitert oder das nicht erreichbar ist, ergibt einen definierten Fehler, wobei der Server für weitere Verbindungen verfügbar bleibt; die Stufe steht in `tools/e2e.sh` und in der erzeugten E2E-Abdeckungs-Sicht.

**Bestand und Abgrenzung gegen Stufe 4:** Stufe 4 von `make e2e` (und 5 bis 7) läuft gegen `kompatibilitaetsserver` im Modul `testserver` (bindet das Hauptmodul als Projekt-Abhängigkeit, nur öffentliche Fläche, `BackendSource.Custom`), mit dem Referenzclient über die Prozessgrenze. Der dort angebundene `RecordingBackend` zeichnet auf und delegiert an das Simulations-Backend; Stufe 4 deklariert FA-08 und RB-04 nicht. Backend-Fehler bei offener Verbindung liefern die Stufen 3, 4 und 7 (50004, `TDS_BACKEND_ERROR`, Folgeabfrage gelingt), aber über die Simulation; es fehlt ein eigenes `QueryBackend`, dessen `execute` scheitert oder das nicht erreichbar ist, mit der Prüfung, dass der Server verfügbar bleibt. Die neue Stufe wiederholt Stufe 4 nicht: sie wiederholt weder die Szenarien noch den Aufzeichner, sondern belegt das Backend, das nicht die Simulation ist, und den Fehlpfad eines solchen Backends. Zeigt die Umsetzung, dass eine Ergänzung der Kennungen an Stufe 4 mit einem zusätzlichen Fall derselben Kategorie genügt, ist das die zu wählende Form (keine doppelte Stufe, Änderung von §2 und `tools/e2e.sh` dann mit Begründung in §7).

**Träger-Slice:** [slice-integrations-einstieg](../done/slice-integrations-einstieg.md) (liegt in `done/` und führt keinen E2E-Punkt); Zeiger und Befund stehen in [slice-e2e-geruest](../done/slice-e2e-geruest.md) §3 und §7.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Ein Verbraucher außerhalb des Gradle-Projekts gegen das gebaute Jar — `internal` ist im Bytecode öffentlich, der Nachweis der Source-Set-Grenze gilt für Kotlin-Quellen ([ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md)); ein Lauf außerhalb des Projekts wäre ein eigener Zuschnitt und wird hier nur aufgenommen, wenn die Umsetzung ihn billig trägt.
- Änderungen am öffentlichen Einstieg selbst — er ist durch [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) und den Träger-Slice geliefert; dieser Slice belegt ihn nur über die Prozessgrenze.
- Weitere Backend-Verträge über die Anforderungen [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung) und [TDS-RB-04](../../../../spec/lastenheft.md#tds-rb-04--externes-backend) hinaus — der Schnitt ist eine Stufe, kein Vertragsausbau.
- Die Entscheidung, ob `make e2e` ein Gate wird — bleibt bei [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — ≤ 3 Liefer-Punkte.

- [ ] **Backend und Lauf:** Ein eigenes Backend (nicht die Simulation, nur öffentliche Fläche, im Modul `testserver` oder einem eigenen ohne Assoziation) wird im Serverprozess angebunden; ein Referenzclient erhält Antworten aus ihm (leeres und gefülltes Resultset) und für ein fehlerhaftes oder nicht erreichbares Backend einen definierten Fehler, der Server bleibt verfügbar; der Lauf ist ein `make`-Target oder eine Kategorie von `make kompatibilitaet`.
- [ ] **Stufe und Sicht:** `tools/e2e.sh` führt das Target als Stufe mit Deklaration `e2e_abdeckung` (Kennungen [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-RB-04](../../../../spec/lastenheft.md#tds-rb-04--externes-backend); Kurzbeschreibung mit „Nicht belegt: …“); `make e2e-abdeckung` schreibt die Sicht; Zeile in `harness/README.md` §Sensors.
- [ ] **Laufzeit:** Die Zeit der neuen Stufe ist gemessen (Einzelmessung mit Datum, Last und Cache-Zustand, §7).
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben oder „keine Beobachtung angefallen“ in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area?

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| Eigenes Backend und Server-`main` im Modul `testserver` (Ort legt der Implementer fest) | neu | nur öffentliche Fläche; belegt [TDS-RB-04](../../../../spec/lastenheft.md#tds-rb-04--externes-backend); neben `KompatibilitaetsServer.kt`, nicht darin |
| `tools/e2e.sh` | update | neue Stufe mit Deklaration; Aufruf als `make <Target>` |
| `tools/mk/` (Fragment des Targets) | neu | Target der Stufe; Zeile in `harness/README.md` §Sensors |
| `docs/plan/e2e-abdeckung.md` | update (erzeugt) | `make e2e-abdeckung`; nicht von Hand |

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-e2e-geruest](../done/slice-e2e-geruest.md) liegt in `done/`; `make gates` ist auf `main` grün.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert — Carveout?): Die öffentliche Fläche reicht für den Prüfling nicht; dann ist das ein Befund zu [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md), nicht in diesem Slice zu lösen.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln.

Alle Punkte der DoD in §2 erledigt, `make e2e` grün mit der neuen Stufe, `make gates` grün, Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst.

- Die Stufe deklariert mehr, als ihr Lauf belegt (Kennung breiter als der Test) — Gegenmittel: „Nicht belegt“ in der Kurzbeschreibung, Abgleich im Review. — **Ausgang:** offen bis zur Closure.
- Die Stufe dupliziert Stufe 4 oder nutzt unbemerkt `internal`-Zugriff (Source-Set mit Assoziation) — Gegenmittel: die Abgrenzung in §1, ein Source-Set ohne Assoziation (`integratorOhneAssoziation`-artige Prüfung). — **Ausgang:** offen bis zur Closure.
- Laufzeit des Durchgangs wächst um die neue Stufe ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)); die Messung steht in §7 des Träger-Gerüsts. — **Ausgang:** offen bis zur Closure.

## 7. Closure-Notiz

Wird bei der Closure gefüllt.

## 8. Sub-Area-Prüfungen und Modus-Begründung

**Vorgelagert — Sub-Area-Wahl prüfen:** Der Slice berührt das ganze Repo (`*`, Kürzel `TDS`); eine feinere Sub-Area ist nicht deklariert.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen; berührt: `BEO-TDS/test-belegt-nur-den-fake` (Prüfling gegen den Fake statt gegen den Prozess) und `BEO-TDS/aussage-ueber-schichtgrenze-ohne-test`.

Alle berührten Sub-Areas GF.
