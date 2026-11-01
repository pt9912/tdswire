# Slice slice-kompatibilitaetsmatrix-gate: Erzeugte Kompatibilitätsmatrix als Gate der Klasse tests

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-qualitaet-grundlage (wie [slice-kompatibilitaetsmatrix-erzeugen](slice-kompatibilitaetsmatrix-erzeugen.md)).

**Bezug:** [TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [ADR-0023](../../adr/0023-gate-klassen-und-stempel.md), [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md).

**Berührte Spec-Stellen:** —

**Verantwortlich:** — bis zur Priorisierung.
**Autor:** pt9912 (Planner-Rolle). **Datum:** 2026-11-01.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** `make gates` wird rot, wenn die erzeugte Sicht (die Kompatibilitätsmatrix) nicht gleich der Ausgabe des Erzeugers aus den committeten Eingängen ist (Spec, Tags, Berichte, E2E-Ergebnisdatei) oder der Erzeuger eine Waise meldet; so lässt sich die Datei nicht von Hand ändern. Die Ergebnisdatei darf veraltet sein, die Sicht nennt ihren Stand und ihr Datum; das Gate prüft nur „committet gleich erzeugt“ und braucht keinen Docker-Lauf des E2E-Teils. Das Gate gehört in die Stempelklasse `tests` ([ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md)), liest die vorhandenen Berichte der Stage `test` (abgeleitete Stage, kein zweiter Testlauf) und lässt die Gate-Zeit von `make gates` nicht steigen.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Erzeuger, Selbsttest, Ergebnisdatei — [slice-kompatibilitaetsmatrix-erzeugen](slice-kompatibilitaetsmatrix-erzeugen.md); Spec-Nachzug, Tags, Umstellung — [slice-kompatibilitaetsmatrix-migration](slice-kompatibilitaetsmatrix-migration.md); hier wird benutzt, was dort liegt.
- Eine Zuordnung zu einer anderen Klasse als `tests` oder ein Gate außerhalb der Stempel — nicht zulässig ([ADR-0023](../../adr/0023-gate-klassen-und-stempel.md)); eine Lockerung eines bestehenden Gates ist ausgeschlossen.
- Bestehen der .NET-Referenzclient-Tests — `make kompatibilitaet` ist kein Gate ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)).

**Keine Mindestzahl.** Ein Slice mit *einem* echten Ausschluss ist besser als
einer mit vier erfundenen; die vier Klassen sind ein Suchraster, keine
Ausfüll-Liste. Suchreihenfolge: Was übernimmt ein **Folge-Slice** (mit
Kennung — und die Kennung muss den Punkt auch annehmen)? Was bleibt als
**Bestand** bewusst stehen (mit Begründung)? Was wäre ein **anderer Vorgang**?
Welche **Schicht** rührt der Slice nicht an?

Was hier steht, ist die Grenze, an der ein wachsender Slice sich messen lässt:
Wer später etwas mitnimmt, das hier ausgeschlossen war, hat den Plan
**geändert**, nicht nur ergänzt.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [ ] **Gate:** ein Target (Name nach `make help`-Konvention, z. B. `kompatibilitaetsmatrix-pruefung`) erzeugt die Matrix aus den Berichten der Stage `test` in eine Datei außerhalb des Repos und vergleicht mit `cmp` gegen den committeten Stand, Exit 1 bei Abweichung oder Waise mit Hinweis `make kompatibilitaetsmatrix`; Aufnahme in `GATE_CHECKS` und Zuordnung zur Klasse `tests` samt Eingangsliste des Stempels (die Klasse darf bei Änderung von Spec, Tests, Matrix oder Erzeuger nicht überspringen; die E2E-Ergebnisdatei steht nicht in der Liste, sonst änderte ihr Commit den Hash).
- [ ] **Gate-Zeit gemessen, nicht geschätzt:** Einzelmessung `make gates` vor und nach der Aufnahme, warm und kalt, für die Fälle ohne Beleg und mit Beleg der Klasse `tests` (Messprotokoll unter `docs/messungen/`); die Zeit steigt nicht über die Messunsicherheit hinaus, sonst ist das Gate nicht aufgenommen. Selbsttest-Fall: die von Hand geänderte Matrix macht das Gate rot.
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update: `harness/README.md` (Zeile des Gates mit Bindung auf [ADR-0023](../../adr/0023-gate-klassen-und-stempel.md) und [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md)).
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
| `tools/mk/kompatibilitaetsmatrix.mk`, `Dockerfile` | update | Gate-Target und abgeleitete Stage über die Berichte der Stage `test` |
| Eingangslisten der Stempel, Zuordnung der Klassen | update | Eingänge der Klasse `tests` um Erzeuger und Matrix erweitern (Verfahren aus slice-stempel-migration) |
| `harness/README.md` | update | Zeile des Gates |

**Optional: Ansatz als Liste, wenn eine Zeile pro Datei nicht trägt** — z. B.
eine Schnittstellenänderung über viele gleichrangige Dateien mit derselben
Begründung, oder ein Ansatz, der sich nicht auf eine Datei herunterbrechen
lässt. Ergänzt die Tabelle, ersetzt sie nicht:

- Ist die Gate-Zeit nach der Messung höher, bleibt das Target ein Werkzeug (kein Gate) und der Nutzer entscheidet; die Schwelle wird nicht gesenkt.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-stempel-migration](../done/slice-stempel-migration.md) und [slice-kompatibilitaetsmatrix-erzeugen](slice-kompatibilitaetsmatrix-erzeugen.md) und [slice-kompatibilitaetsmatrix-migration](slice-kompatibilitaetsmatrix-migration.md) liegen in `done/`.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): die Messung braucht mehr als eine Sitzung.
- `in-progress` → `open` (blockiert — Carveout?): die Klasse `tests` ist nicht verdrahtet oder der Erzeuger liefert nicht byte-gleich.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

DoD vollständig, `make gates` grün, Messprotokoll der Gate-Zeit liegt vor, Review-Report liegt vor, Closure-Notiz mit Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **Gate-Zeit steigt** (Nutzerziel: `make gates` performanter) — Kosten nicht gemessen; Bedingung in der DoD. — **Ausgang:** weiter offen bis zur Messung; der Ausgang steht bei der Closure.
- **Gate rot ohne Fehler im Produkt** (Quelle gepflegt, Datei nicht neu erzeugt): beabsichtigt, die Meldung nennt `make kompatibilitaetsmatrix`. — **Ausgang:** weiter offen: → BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor im Register (7×).

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

**Vorgelagert — Sub-Area-Wahl prüfen:** nur `*` (Greenfield, Kürzel `TDS`); berührt sind `tools/`, `tools/mk`, `Dockerfile`, `harness/README.md`.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, 2026-11-01): `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor` (7×, offen, über der Schwelle), `BEO-TDS/zusage-ohne-sensor-in-der-ci-kette` (4×, offen); sonst keine Treffer.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

<!-- Block für jede berührte Sub-Area duplizieren. Format identisch
mit dem im Baseline-Regelwerk §Ziel-Form: Sub-Area-Modus-Begründung
abgedruckten Block. -->

### Sub-Area: <Name>

- **Modus:** GF | BF | Hybrid
- **Konventionen-Dichte:** <Beleg aus `harness/conventions.md`,
  Adaptions-Block oder Code>
- **Phase-Reife:** Phase 0–5 <Begründung gegen die Phase × Modus-Matrix>
- **Evidenz-/Diskrepanz-Risiko:** <bei BF/Hybrid: was kann die
  Inventur sichtbar machen? bei GF: meist niedrig>
- **Reconciliation-Aufwand:** <Slice-Schätzung;
  Graduation-/Folge-Slice-Trigger>
