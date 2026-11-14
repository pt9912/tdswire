# Slice slice-release-entscheidungen: Entscheidungen des Nutzers zu E2E im Release, Wächter-Form, Software-Stückliste und Jar ohne Registry in der Release-Anleitung

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.

**Welle:** welle-referenzabgleich-release

**Bezug:** [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-RB-05](../../../../spec/lastenheft.md#tds-rb-05--lizenz), [ADR-0010](../../adr/0010-detekt-pre-release.md), [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md), [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md), [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md)

**Berührte Spec-Stellen:** SPEC-041 · SPEC-042

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912; verkleinert vom Planner-Agent nach den Entscheidungen des Nutzers 2026-11-14. **Datum:** 2026-11-06.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar.

**Ziel:** Die zwei Sätze, die [`releasing.md`](../../../maintainer/releasing.md) als „Offene Entscheidungen“ führt, sind nach den Entscheidungen des Nutzers vom 2026-11-14 („Deine Empfehlung“) als Schritte beziehungsweise als benannte Zurückstellung eingetragen. Der Slice liefert Entscheidungen, keine Erfindungen: Was der Nutzer nicht gewählt hat, bleibt in der Anleitung offen.

**Entschieden (Nutzer, 2026-11-14):** (a) `make e2e` ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)) ist ein **Schritt der Anleitung**, kein Gate; (b) der Wächter für [ADR-0010](../../adr/0010-detekt-pre-release.md) bleibt der Schritt in `releasing.md`, **kein neues Target**; (c) Software-Stückliste und ein Weg für Jar und `lib/` ohne Registry werden **nicht jetzt** gebaut oder entschieden und **nach der ersten Veröffentlichung neu bewertet**.

**Schon entschieden und nicht mehr Gegenstand (Anschluss, Verweis, keine Entscheidung dieses Slice):** Versionsschema, Tag-Form, Registry und Signierung — [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) (Eintrag in `releasing.md` Schritte 5, 6, 8, 9 durch [slice-maven-veroeffentlichung](../done/slice-maven-veroeffentlichung.md)); der Weg der Veröffentlichung (CI bei Tag-Push) — [ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) mit [slice-ci-veroeffentlichung-workflow](../open/slice-ci-veroeffentlichung-workflow.md). Die frühere Abgrenzung von [slice-maven-veroeffentlichung](../done/slice-maven-veroeffentlichung.md) §1 zu diesem Slice gilt mit dieser Verkleinerung als aufgelöst.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Lizenzhinweise zu den Bibliotheken in `lib/` und Dateirechte der Benutzerliste — Rechtsfrage und offene Punkte von [ADR-0018](../../adr/0018-laufzeit-artefakt.md) §Offen (`Proposed`), eigener Träger.
- Der Wechsel von detekt auf eine 2.x-Stabilversion — eigener Slice mit neuer ADR nach Eintritt des Triggers ([ADR-0010](../../adr/0010-detekt-pre-release.md) bleibt `Accepted`, `AGENTS.md` §3.5).
- Ein Sensor für die Release-Liste des Kurs-Repos — `harness/conventions.md` führt ihn als *kommt nicht mit*.
- Ein Produkt-Image gegen [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) — Änderung des Produktschnitts wäre eine eigene ADR.
- Bau einer Software-Stückliste und eines Weges, Jar und `lib/` ohne Registry herauszugeben — nicht jetzt (Entscheidung c); der Slice hält nur die Zurückstellung und den Trigger der Neubewertung in der Anleitung fest. Ein Bau wäre ein eigener Slice nach der Neubewertung.
- Ein Werkzeug-Target für den Wächter von [ADR-0010](../../adr/0010-detekt-pre-release.md) — entschieden: keines (Entscheidung b).
- Änderungen an Schritt 1, 8 und 9 und am Kopf von `releasing.md` — [slice-ci-veroeffentlichung-workflow](../open/slice-ci-veroeffentlichung-workflow.md).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**.

Liefer-Punkte (zwei):

- [ ] **E2E im Release und Wächter:** `docs/maintainer/releasing.md` Schritt 2 führt `make e2e` als Schritt des Releases (kein Gate, Ergebnis und Dauer im Release-Vermerk, Schritt 8) statt „nicht entschieden“; der Abschnitt „Wächter für [ADR-0010](../../adr/0010-detekt-pre-release.md)“ führt als Entscheidung, dass es beim Schritt bleibt und kein Werkzeug-Target entsteht (der Satz „ob es entsteht, entscheidet der Nutzer“ entfällt); die Zeile „Ob `make e2e` Teil eines Releases ist“ und die Zeile zum Wächter-Target verlassen „Offene Entscheidungen“. Kein neues Target, keine Zeile in `harness/README.md` §Sensors.
- [ ] **Stückliste und Jar ohne Registry zurückgestellt:** `releasing.md` führt Software-Stückliste und den Weg für Jar und `lib/` ohne Registry als **nicht jetzt entschieden, nach der ersten Veröffentlichung neu zu bewerten** (Schritt 6 „offen“ und der Schlusssatz unter den Schritten sowie „Offene Entscheidungen“ tragen denselben Wortlaut; der Nutzer wählt nichts, was nicht gewählt ist). „Offene Entscheidungen“ führt danach nur, was der Nutzer wirklich offen lässt (Lizenzhinweise zu `lib/` und Dateirechte der Benutzerliste).

Nachweis-Pflichten für beide Punkte:

- [ ] `make gates` grün; `make docs-check`, `make flaeche`, `make kopplung-zahlen` ([`harness/README.md`](../../../../harness/README.md) §Minimal agent workflow, Art „Doku oder Spec“).
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — kein Self-Review (Modul 8).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben; **kein Zaehler wird gesetzt**.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `docs/maintainer/releasing.md` | update | Schritt 2, Abschnitt Wächter, Schritt 6 (Satz zu Jar und `lib/`), „Nicht Teil dieses Ablaufs“ und „Offene Entscheidungen“; Schritte 1, 8, 9 und der Kopf gehören [slice-ci-veroeffentlichung-workflow](../open/slice-ci-veroeffentlichung-workflow.md) |

**Reihenfolge:** erst [slice-ci-veroeffentlichung-workflow](../open/slice-ci-veroeffentlichung-workflow.md), dann dieser Slice, beide ändern `releasing.md` (getrennte Abschnitte, aber eine Datei); [slice-maven-erste-veroeffentlichung](../open/slice-maven-erste-veroeffentlichung.md) berührt `releasing.md` ebenfalls und folgt nach beiden.

## 4. Trigger

**Start** (`open` → `next`): [slice-release-doku-prozess](../done/slice-release-doku-prozess.md) liegt in `done/` (erfüllt), die Entscheidungen des Nutzers liegen vor (2026-11-14, oben), und [slice-ci-veroeffentlichung-workflow](../open/slice-ci-veroeffentlichung-workflow.md) liegt nicht in `in-progress/` (gleiche Datei); ein Verantwortlicher ist eingetragen.

**Rückführungen:**

- `in-progress` → `open` (blockiert): der Nutzer zieht eine der Entscheidungen vom 2026-11-14 zurück; der Punkt bleibt in `releasing.md` offen und der Slice liefert den anderen.

## 5. Closure-Trigger

Alle Punkte der DoD erledigt, `make gates` grün, Review-Report liegt vor, Closure-Notiz mit Lerneintrag geschrieben.

## 6. Risiken und offene Punkte

- Ein Release-Schritt wird als entschieden eingetragen, ohne dass der Nutzer ihn gewählt hat (Setzung statt Entscheidung); `AGENTS.md` §3.8 gilt für die Anleitung. Die drei Entscheidungen stehen oben mit Datum; mehr wird nicht eingetragen. — **Ausgang:** offen bis zur Closure.
- „Nach der ersten Veröffentlichung neu bewerten“ hat keinen Auslöser außer der Closure von [slice-maven-erste-veroeffentlichung](../open/slice-maven-erste-veroeffentlichung.md), deren Notiz den Auftrag nennt; kein Sensor erinnert daran. — **Ausgang:** offen bis zur Closure.

## 7. Closure-Notiz

Wird bei der Closure gefüllt.

## 8. Sub-Area-Prüfungen und Modus-Begründung

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration führt `*` als einzige Sub-Area.

**Vorgelagert — offene Beobachtungen sichten:** nicht gesichtet beim Anlegen; der Implementer sichtet das Register (`../observations/BEO-TDS/`) beim Start.

Alle berührten Sub-Areas GF.
