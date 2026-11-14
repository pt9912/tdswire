# Slice slice-release-entscheidungen: Entscheidungen des Nutzers zu Versionsschema, Tag, Veröffentlichung und Wächter-Form des Releases

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.

**Welle:** welle-referenzabgleich-release

**Bezug:** [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-RB-05](../../../../spec/lastenheft.md#tds-rb-05--lizenz), [ADR-0010](../../adr/0010-detekt-pre-release.md), [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md), [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md)

**Berührte Spec-Stellen:** SPEC-041 · SPEC-042

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912. **Datum:** 2026-11-06.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar.

**Ziel:** Die Entscheidungen des Nutzers, die [slice-release-doku-prozess](../done/slice-release-doku-prozess.md) in `docs/maintainer/releasing.md` als offen führt, sind getroffen und in der Anleitung als Schritte eingetragen. Der Slice liefert Entscheidungen, keine Erfindungen: Was der Nutzer nicht wählt, bleibt in der Anleitung offen.

**Anschluss (2026-11-14, Verweis, keine Entscheidung dieses Slice):** [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) steht auf `Accepted` und hat Versionsschema, Tag-Form, Registry und Signierung entschieden; offen für diesen Slice bleiben Software-Stückliste, der Weg, Jar und `lib/` ohne Registry aus dem Bau herauszugeben, `make e2e` im Release und die Form des Wächters für [ADR-0010](../../adr/0010-detekt-pre-release.md). Welche Punkte dieses Slice damit erledigt sind, entscheidet sein Plan beim Start; die Abgrenzung nennt [slice-maven-veroeffentlichung](../done/slice-maven-veroeffentlichung.md) §1.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Lizenzhinweise zu den Bibliotheken in `lib/` und Dateirechte der Benutzerliste — Rechtsfrage und offene Punkte von [ADR-0018](../../adr/0018-laufzeit-artefakt.md) §Offen (`Proposed`), eigener Träger.
- Der Wechsel von detekt auf eine 2.x-Stabilversion — eigener Slice mit neuer ADR nach Eintritt des Triggers ([ADR-0010](../../adr/0010-detekt-pre-release.md) bleibt `Accepted`, `AGENTS.md` §3.5).
- Ein Sensor für die Release-Liste des Kurs-Repos — `harness/conventions.md` führt ihn als *kommt nicht mit*.
- Ein Produkt-Image oder ein Registry-Weg gegen [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) — Änderung des Produktschnitts wäre eine eigene ADR.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**.

Liefer-Punkte (drei):

- [ ] **Versionsschema und Tag:** Der Nutzer wählt Zählweise, Beginn, Umgang mit `-SNAPSHOT` und ob und in welcher Form ein Tag gesetzt wird; die Entscheidung steht in `docs/maintainer/releasing.md` (Schritte „Version“ und „Tag und Vermerk“) und, wo sie den Quelltext berührt, in `build.gradle.kts`.
- [ ] **Veröffentlichung und E2E im Release:** Der Nutzer entscheidet über Registry, Signierung, Software-Stückliste, über den Weg, Jar und `lib/` aus dem Bau herauszugeben, und ob `make e2e` ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)) Teil eines Releases ist; eine Entscheidung mit Wirkung auf den Produktschnitt oder auf ein Gate ist eine ADR (`AGENTS.md` §3.6), die übrigen stehen in `releasing.md`.
- [ ] **Form des Wächters für [ADR-0010](../../adr/0010-detekt-pre-release.md):** Der Nutzer wählt, ob es beim Schritt in `releasing.md` bleibt oder ob zusätzlich ein Werkzeug-Target außerhalb von `make gates` die Versionsliste in Docker abfragt und meldet; entsteht es, trägt `harness/README.md` §Sensors seine Zeile.

Nachweis-Pflichten für alle drei Punkte:

- [ ] `make gates` grün.
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
| `docs/maintainer/releasing.md` | update | Offene Entscheidungen als Schritte eintragen |
| `build.gradle.kts` | update | nur falls die Versionsentscheidung den Quelltext berührt |
| `docs/plan/adr/` | neu | nur für Entscheidungen mit Wirkung auf Produktschnitt oder Gate |
| `tools/mk/`, `harness/README.md` | update | nur falls der Nutzer das Werkzeug-Target für den Wächter wählt (kein Gate) |

## 4. Trigger

**Start** (`open` → `next`): [slice-release-doku-prozess](../done/slice-release-doku-prozess.md) liegt in `done/` (die Anleitung führt die Punkte als offen), und der Nutzer hat die Entscheidungen getroffen oder ihren Zeitpunkt benannt; ein Verantwortlicher ist eingetragen.

**Rückführungen:**

- `in-progress` → `next` (zu groß): mehr als drei Liefer-Punkte; Schnittvorschlag: Veröffentlichung (Punkt 2) als eigener Slice mit ADR.
- `in-progress` → `open` (blockiert): Der Nutzer entscheidet einen Punkt nicht; der Punkt bleibt in `releasing.md` offen und der Slice liefert die übrigen.

## 5. Closure-Trigger

Alle Punkte der DoD erledigt, `make gates` grün, Review-Report liegt vor, Closure-Notiz mit Lerneintrag geschrieben.

## 6. Risiken und offene Punkte

- Ein Release-Schritt wird als entschieden eingetragen, ohne dass der Nutzer ihn gewählt hat (Setzung statt Entscheidung); `AGENTS.md` §3.8 gilt für die Anleitung. — **Ausgang:** offen bis zur Closure.
- Eine Veröffentlichung in einer Registry widerspräche [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md), sofern sie ein Produkt-Image meint; sie braucht dann eine ADR. — **Ausgang:** offen bis zur Closure.

## 7. Closure-Notiz

Wird bei der Closure gefüllt.

## 8. Sub-Area-Prüfungen und Modus-Begründung

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration führt `*` als einzige Sub-Area.

**Vorgelagert — offene Beobachtungen sichten:** nicht gesichtet beim Anlegen; der Implementer sichtet das Register (`../observations/BEO-TDS/`) beim Start.

Alle berührten Sub-Areas GF.
