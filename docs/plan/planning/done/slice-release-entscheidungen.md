# Slice slice-release-entscheidungen: Entscheidungen des Nutzers zu E2E im Release, Wächter-Form, Software-Stückliste und Jar ohne Registry in der Release-Anleitung

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.

**Welle:** welle-referenzabgleich-release

**Bezug:** [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-RB-05](../../../../spec/lastenheft.md#tds-rb-05--lizenz), [ADR-0010](../../adr/0010-detekt-pre-release.md), [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md), [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md), [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md)

**Berührte Spec-Stellen:** SPEC-041 · SPEC-042

**Verantwortlich:** pt9912.

**Autor:** pt9912; verkleinert vom Planner-Agent nach den Entscheidungen des Nutzers 2026-11-14. **Datum:** 2026-11-06.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar.

**Ziel:** Die zwei Sätze, die [`releasing.md`](../../../maintainer/releasing.md) als „Offene Entscheidungen“ führt, sind nach den Entscheidungen des Nutzers vom 2026-11-14 („Deine Empfehlung“) als Schritte beziehungsweise als benannte Zurückstellung eingetragen. Der Slice liefert Entscheidungen, keine Erfindungen: Was der Nutzer nicht gewählt hat, bleibt in der Anleitung offen.

**Entschieden (Nutzer, 2026-11-14):** (a) `make e2e` ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)) ist ein **Schritt der Anleitung**, kein Gate; (b) der Wächter für [ADR-0010](../../adr/0010-detekt-pre-release.md) bleibt der Schritt in `releasing.md`, **kein neues Target**; (c) Software-Stückliste und ein Weg für Jar und `lib/` ohne Registry werden **nicht jetzt** gebaut oder entschieden und **nach der ersten Veröffentlichung neu bewertet**.

**Schon entschieden und nicht mehr Gegenstand (Anschluss, Verweis, keine Entscheidung dieses Slice):** Versionsschema, Tag-Form, Registry und Signierung — [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) (Eintrag in `releasing.md` Schritte 5, 6, 8, 9 durch [slice-maven-veroeffentlichung](../done/slice-maven-veroeffentlichung.md)); der Weg der Veröffentlichung (CI bei Tag-Push) — [ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) mit [slice-ci-veroeffentlichung-workflow](../done/slice-ci-veroeffentlichung-workflow.md). Die frühere Abgrenzung von [slice-maven-veroeffentlichung](../done/slice-maven-veroeffentlichung.md) §1 zu diesem Slice gilt mit dieser Verkleinerung als aufgelöst.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Lizenzhinweise zu den Bibliotheken in `lib/` und Dateirechte der Benutzerliste — Rechtsfrage und offene Punkte von [ADR-0018](../../adr/0018-laufzeit-artefakt.md) §Offen (`Proposed`), eigener Träger.
- Der Wechsel von detekt auf eine 2.x-Stabilversion — eigener Slice mit neuer ADR nach Eintritt des Triggers ([ADR-0010](../../adr/0010-detekt-pre-release.md) bleibt `Accepted`, `AGENTS.md` §3.5).
- Ein Sensor für die Release-Liste des Kurs-Repos — `harness/conventions.md` führt ihn als *kommt nicht mit*.
- Ein Produkt-Image gegen [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) — Änderung des Produktschnitts wäre eine eigene ADR.
- Bau einer Software-Stückliste und eines Weges, Jar und `lib/` ohne Registry herauszugeben — nicht jetzt (Entscheidung c); der Slice hält nur die Zurückstellung und den Trigger der Neubewertung in der Anleitung fest. Ein Bau wäre ein eigener Slice nach der Neubewertung.
- Ein Werkzeug-Target für den Wächter von [ADR-0010](../../adr/0010-detekt-pre-release.md) — entschieden: keines (Entscheidung b).
- Änderungen an Schritt 1, 8 und 9 und am Kopf von `releasing.md` — [slice-ci-veroeffentlichung-workflow](../done/slice-ci-veroeffentlichung-workflow.md). Einzige Ausnahme: die Dauer von `make e2e` im Release-Vermerk von Schritt 8, damit Schritt 2 und Schritt 8 gleich lauten (Nutzerentscheidung 2026-11-15, „Dauer in Schritt 8 aufnehmen“; Entscheidung (a) nennt „Ergebnis und Dauer“).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**.

Liefer-Punkte (zwei):

- [x] **E2E im Release und Wächter:** `docs/maintainer/releasing.md` Schritt 2 führt `make e2e` als Schritt des Releases (kein Gate, Ergebnis und Dauer im Release-Vermerk, Schritt 8) statt „nicht entschieden“; der Abschnitt „Wächter für [ADR-0010](../../adr/0010-detekt-pre-release.md)“ führt als Entscheidung, dass es beim Schritt bleibt und kein Werkzeug-Target entsteht (der Satz „ob es entsteht, entscheidet der Nutzer“ entfällt); die Zeile „Ob `make e2e` Teil eines Releases ist“ und die Zeile zum Wächter-Target verlassen „Offene Entscheidungen“. Kein neues Target, keine Zeile in `harness/README.md` §Sensors.
- [x] **Stückliste und Jar ohne Registry zurückgestellt:** `releasing.md` führt Software-Stückliste und den Weg für Jar und `lib/` ohne Registry als **nicht jetzt entschieden, nach der ersten Veröffentlichung neu zu bewerten** (Schritt 6 „offen“ und der Schlusssatz unter den Schritten sowie „Offene Entscheidungen“ tragen denselben Wortlaut; der Nutzer wählt nichts, was nicht gewählt ist). „Offene Entscheidungen“ führt danach nur, was der Nutzer wirklich offen lässt (Lizenzhinweise zu `lib/` und Dateirechte der Benutzerliste).

Nachweis-Pflichten für beide Punkte:

- [x] `make gates` grün; `make docs-check`, `make flaeche`, `make kopplung-zahlen` ([`harness/README.md`](../../../../harness/README.md) §Minimal agent workflow, Art „Doku oder Spec“).
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben; **kein Zaehler wird gesetzt**.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `docs/maintainer/releasing.md` | update | Schritt 2, Abschnitt Wächter, Schritt 6 (Satz zu Jar und `lib/`), „Nicht Teil dieses Ablaufs“ und „Offene Entscheidungen“; Schritte 1, 8, 9 und der Kopf gehören [slice-ci-veroeffentlichung-workflow](../done/slice-ci-veroeffentlichung-workflow.md) |

**Reihenfolge:** erst [slice-ci-veroeffentlichung-workflow](../done/slice-ci-veroeffentlichung-workflow.md), dann dieser Slice, beide ändern `releasing.md` (getrennte Abschnitte, aber eine Datei); [slice-maven-erste-veroeffentlichung](../done/slice-maven-erste-veroeffentlichung.md) berührt `releasing.md` ebenfalls und folgt nach beiden.

## 4. Trigger

**Start** (`open` → `next`): [slice-release-doku-prozess](../done/slice-release-doku-prozess.md) liegt in `done/` (erfüllt), die Entscheidungen des Nutzers liegen vor (2026-11-14, oben), und [slice-ci-veroeffentlichung-workflow](../done/slice-ci-veroeffentlichung-workflow.md) liegt nicht in `in-progress/` (gleiche Datei); ein Verantwortlicher ist eingetragen.

**Rückführungen:**

- `in-progress` → `open` (blockiert): der Nutzer zieht eine der Entscheidungen vom 2026-11-14 zurück; der Punkt bleibt in `releasing.md` offen und der Slice liefert den anderen.

## 5. Closure-Trigger

Alle Punkte der DoD erledigt, `make gates` grün, Review-Report liegt vor, Closure-Notiz mit Lerneintrag geschrieben.

## 6. Risiken und offene Punkte

- Ein Release-Schritt wird als entschieden eingetragen, ohne dass der Nutzer ihn gewählt hat (Setzung statt Entscheidung); `AGENTS.md` §3.8 gilt für die Anleitung. Die drei Entscheidungen stehen oben mit Datum; mehr wird nicht eingetragen. — **Ausgang:** entfallen. Eingetragen ist nur, was die drei Entscheidungen tragen; Review und Verifikation fanden keine Setzung (F-3 „Release-Stand“ ist entfernt, Verifikation: nur Entschiedenes eingetragen).
- „Nach der ersten Veröffentlichung neu bewerten“ hat keinen Auslöser außer der Closure von [slice-maven-erste-veroeffentlichung](../done/slice-maven-erste-veroeffentlichung.md), deren Notiz den Auftrag nennt; kein Sensor erinnert daran. — **Ausgang:** weiter offen. Träger ist die Closure von [slice-maven-erste-veroeffentlichung](../done/slice-maven-erste-veroeffentlichung.md) (ihr §5 nennt den Auftrag); ein Sensor entsteht nicht.

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register (vorhandene `BEO-<KUERZEL>/<slug>` **zitieren**) ·
`grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln.

Geschlossen 2026-11-15 (Planner, Slice-Closure; die Welle [welle-referenzabgleich-release](../welle-referenzabgleich-release.md) bleibt offen). Stand: Zweig `worktree-agent-a231c5f47ad1cd322`, noch nicht in `main` gemergt; belegte Commits `8f7a75f0` (Umsetzung), `0c365e4e` (Review), `f145be91` (Nachbesserung F-1 bis F-4), `2c6dcfc9` (Dauer in Schritt 8), `ed95eb24` und `3efef309` (Verifikation).

**Geliefert.** Nur [`releasing.md`](../../../maintainer/releasing.md) ist geändert. (1) Schritt 2 führt `make e2e` ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)) als Schritt der Anleitung, kein Gate, Ergebnis und Dauer im Vermerk (Schritt 8); der Abschnitt Wächter für [ADR-0010](../../adr/0010-detekt-pre-release.md) führt die Entscheidung, dass es beim Schritt bleibt und kein Target entsteht; beide Zeilen verließen „Offene Entscheidungen“. (2) Software-Stückliste und der Weg für Jar und `lib/` ohne Registry stehen in Schritt 6, in „Nicht Teil dieses Ablaufs“ und in „Offene Entscheidungen“ mit demselben Kernwortlaut „nicht jetzt entschieden, nach der ersten Veröffentlichung neu zu bewerten“ (Nutzer, 2026-11-14). „Offene Entscheidungen“ führt als Liste nur noch Lizenzhinweise zu `lib/` und Dateirechte der Benutzerliste. Kein neues Target, keine Zeile in `harness/README.md`, kein Eintrag in einer `Accepted`-ADR.

**Belege.** [Review](../../../reviews/2026-11-15-8f7a75f0-slice-release-entscheidungen-review.md) (`8f7a75f0`): 0 HIGH, 1 MEDIUM, 3 LOW, 2 INFO. [Verifikation](../../../reviews/2026-11-15-2c6dcfc9-slice-release-entscheidungen-verifikation.md) (`2c6dcfc9`): konform für beide Liefer-Punkte, F-1 bis F-4 behoben, F-5 und F-6 sinnvoll belassen, 0 HIGH, 0 MEDIUM, 0 LOW, 3 INFO (V-1 bis V-3). Doku-Änderung: kein `make clean-build`, kein `make e2e` ([`harness/README.md`](../../../../harness/README.md) §Minimal agent workflow). Verifier und Closure fuhren `make docs-check`, `make flaeche`, `make kopplung-zahlen` und `make gates` (Ergebnisse im Bericht der Closure).

**Was anders lief.**
- **MEDIUM F-1, Zahl an zweitem Ort ohne Sensor:** die Umsetzung schrieb 103 s und 339 s aus der Zeile `make e2e` in `harness/README.md` §Sensors in Schritt 2 ab; kein Sensor hielt beide Orte gleich. Behoben in `f145be91` durch Verweis statt Wiederholung: die Zahlen stehen nur noch an einem Ort (Beleg: Verifier, Suche nach `103 s` und `339 s` in `releasing.md` ohne Treffer). Der Reviewer-Prüfpunkt hat den Fund getragen.
- **F-2, F-3, F-4** (Satz zu Stufe 8 enger als der Beleg; „Release-Stand“ als Zugabe zur Entscheidung; Wortlaut (c) an drei Stellen nicht gleich) behoben in `f145be91`.
- **Nachbesserung `f145be91`/`2c6dcfc9` ohne zweiten Review:** beide Commits ändern nur `releasing.md` (Doku). Nach der Regel `BEO-TDS/nachbesserung-ohne-zweiten-review` (Code, Skripte, Konfiguration) ist das keine Nachbesserung mit Code; der Verifier hat sie Zeile für Zeile gegen den Report gelesen. Vermerk, kein Register-Beleg.
- **V-1, Abgrenzung des Plans gegen die DoD:** Slice §1 schloss „Änderungen an Schritt 8“ aus, die DoD verlangte „Ergebnis und Dauer im Release-Vermerk, Schritt 8“; der Plan widersprach sich an dieser Stelle. Nutzerentscheidung 2026-11-15 („Dauer in Schritt 8 aufnehmen“, Code angleichen) in `2c6dcfc9`; §1 trägt jetzt die Ausnahme. Die Einsicht des Plans: Eine Abgrenzung nach Abschnitten einer gemeinsamen Datei muss gegen die DoD-Sätze gelesen werden, die dort etwas verlangen.
- **docs-check:** der Verifikationsbericht trug in `ed95eb24` eine Kennung ohne Link, `3efef309` entfernte sie; der Rotlauf des Review-Berichts steht in keinem Bericht und ist hier nicht belegt. Spitze grün.

**Ungemessen, offen.**
- Lizenzhinweise zu den Bibliotheken in `lib/` und Dateirechte der Benutzerliste: bewusst offen, Träger [ADR-0018](../../adr/0018-laufzeit-artefakt.md) §Offen (`Proposed`); Register `BEO-TDS/lizenzhinweise-der-bibliotheken-rechtsfrage` und `BEO-TDS/benutzerliste-dateirechte-im-betrieb-offen`. Nicht gemessen, nicht Teil dieses Slice.
- **Neubewertung von Software-Stückliste und Jar ohne Registry nach der ersten Veröffentlichung:** hat keinen Sensor und keinen Auslöser außer der Closure von [slice-maven-erste-veroeffentlichung](../done/slice-maven-erste-veroeffentlichung.md); deren §5 trägt den Auftrag. Wer diese Closure ohne den Auftrag schreibt, wird nicht erwischt (Grenze der Deklaration). Weiter offen (§6).
- [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) (`Accepted`) führt die Software-Stückliste weiter als „offen in `releasing.md`“; das stimmt nach diesem Slice nur noch als „zurückgestellt“ (Review F-5, Verifikation: richtig belassen, `AGENTS.md` §3.5).

**Register.** Fortgeschrieben, kein Zähler und kein Ausgang gesetzt, Zählung folgt aus den Dateien:
- `BEO-TDS/zahl-an-mehreren-orten-ohne-sensor`: Datei `evidence/slice-release-entscheidungen.md` (Review F-1). Der Eintrag ist schon `verkörpert` (Reviewer-Skill); die Dateien zählen danach 9 (der Stand-Text des Eintrags nennt 7, er ist überholt).
- Gesichtet, kein Beleg eingetragen: `nachbesserung-ohne-zweiten-review` (Nachbesserung nur Doku, außerhalb der Regel, siehe oben), `kennungen-ohne-link-in-neuen-dokumenten` (Stand `gestrichen`, gedeckt durch `make docs-check`; der eine belegte Fund (Verifikationsbericht, `3efef309`) betrifft eine Kennung ohne Link in einem Bericht; weil der Eintrag gestrichen ist, ist kein Beleg ergänzt, der Nutzer entscheidet, ob er wiederbelebt wird).

**Offene Nutzerentscheidungen.** Keine neuen. Hinweis: `zahl-an-mehreren-orten-ohne-sensor` steht über der Schwelle und ist verkörpert; der Planner ändert den Stand nicht.

**Lerneintrag (Steering Loop).**

- **Bestätigt, keine neue Regel:** eine Zahl, die aus einer Messzeile in eine Anleitung abgeschrieben wird, braucht den Verweis statt der Kopie; der Reviewer-Prüfpunkt „Zahl an zweitem Ort ohne Sensor“ hat sie gefunden, bevor ein Sensor nötig wurde. Gezählt im Register (Datei `evidence/slice-release-entscheidungen.md`), die Regel ist bereits verkörpert; kein `liegt in`.
- **Benannte Planungslücke, nicht verkörpert:** der Plan grenzte nach Abschnitten einer gemeinsamen Datei ab (Schritt 8 gehört dem Vorgänger) und verlangte in der DoD einen Satz, der genau dort steht. Der Widerspruch fiel erst in der Verifikation auf (V-1). Kein Register-Eintrag angelegt; ob einer entsteht, entscheidet der Nutzer.
- **Der Schnitt hat getragen:** Entscheidungen des Nutzers als eigener, kleiner Slice, der nur einträgt, was entschieden ist, und Zurückgestelltes mit Trigger und Träger benennt. Folge-Slice-Paarung: [slice-maven-erste-veroeffentlichung](../done/slice-maven-erste-veroeffentlichung.md) trägt die Neubewertung (§5, Zeile zur Closure-Notiz).
- Spec-Lücke: keine neue. Verkörpert wurde mit diesem Slice keine Regel, daher kein `liegt in` und kein Herkunfts-Anker.

## 8. Sub-Area-Prüfungen und Modus-Begründung

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration führt `*` als einzige Sub-Area.

**Vorgelagert — offene Beobachtungen sichten:** nicht gesichtet beim Anlegen; der Implementer sichtet das Register (`../observations/BEO-TDS/`) beim Start.

Alle berührten Sub-Areas GF.
