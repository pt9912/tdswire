# Slice slice-qa-performance-grenzwerte: Performance: Grenzwerte im Lastenheft festlegen

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-qualitaet-grundlage

**Bezug:** [TDS-QA-04](../../../../spec/lastenheft.md#tds-qa-04--performance)

**Berührte Spec-Stellen:** `SPEC-034` (`MAX_CONNECTIONS`, wo die Staffel eine Zahl ändert) · Abschnitt 5 der Spezifikation (Metriken, wo die Messung eine Aussage ändert)

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912. **Datum:** 2026-11-15.

---

## 1. Ziel und Abgrenzung


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Die Grenzwerte für Durchsatz, p95-Latenz und gleichzeitige Sitzungen stehen mit der Umgebung, für die sie gelten, in [TDS-QA-04](../../../../spec/lastenheft.md#tds-qa-04--performance) im Lastenheft, abgeleitet aus den Messzeilen von [slice-qa-performance-durchsatz-sitzungen](../in-progress/slice-qa-performance-durchsatz-sitzungen.md), [slice-qa-performance-rueckstau](../in-progress/slice-qa-performance-rueckstau.md) und [slice-qa-performance-heap-abschluss](slice-qa-performance-heap-abschluss.md) und vom Nutzer bestätigt.

**Übernimmt:** aus dem DoD-Punkt „Grenzwerte im Lastenheft festgelegt“ von [slice-qa-performance](../done/slice-qa-performance.md) (Fassung vor der Zerlegung vom 2026-11-15).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Messungen — liegen bei [slice-qa-performance-durchsatz-sitzungen](../in-progress/slice-qa-performance-durchsatz-sitzungen.md), [slice-qa-performance-rueckstau](../in-progress/slice-qa-performance-rueckstau.md) und [slice-qa-performance-heap-abschluss](slice-qa-performance-heap-abschluss.md); dieser Slice misst nichts und ändert kein Messprotokoll.
- Optimierung bei einem verfehlten Grenzwert — ein Slice je Befund (Welle §6). Ob ein Wert „Code angleichen“ oder „Zielbild anpassen“ heißt, entscheidet der Nutzer mit dem Wert, nicht dieser Slice.
- Eine Gate-Schwelle in `make gates` — der Lasttest bleibt außerhalb der Gates ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)); ein Gate aus dem Wächter-Wert wäre eine eigene Entscheidung.

## 2. Definition of Done


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [ ] **Lastenheft:** `spec/lastenheft.md` [TDS-QA-04](../../../../spec/lastenheft.md#tds-qa-04--performance) nennt Grenzwerte (Zeilen/s je Richtung, p95 der Punktabfrage, gleichzeitige Sitzungen) mit Referenzumgebung (Limits, Image, Methode) und Verweis auf das Messprotokoll; die Zeile „Grenzwerte für Durchsatz, p95-Latenz, Sitzungen und Lastprofil“ der Abnahmeunterlagen trägt den Stand. Form (ein oder zwei Werte je Messpunkt) nach der Antwort zu O5 im Workload.
- [ ] **Spec-Nachzug:** Aussagen der Spezifikation, die die Messungen berühren (Rückstau, `MAX_CONNECTIONS`, Metriken, „nicht gemessen“-Sätze), tragen den Beleg oder sind auf ihn zurückgeschnitten (`AGENTS.md` §3.8); `make doc-complete` und `make flaeche` laufen.
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update: `docs/plan/referenz-workload.md` auf „abgeschlossen“ gestellt, `harness/README.md` §Sensors, falls die Messung ein Target trägt; `docs/user/` nur, wenn sich Verhalten oder Konfiguration ändern.
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
| `spec/lastenheft.md` [TDS-QA-04](../../../../spec/lastenheft.md#tds-qa-04--performance) | update | Grenzwerte nach der Messung eintragen (Änderung dort, nicht in der Planung) |
| `spec/spezifikation.md` | update | Nachzug der Aussagen zu Rückstau, Sitzungen, Metriken auf den Beleg |
| `docs/plan/referenz-workload.md` | update | Stand „freigegeben“ und Verweis auf das Lastenheft |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-qa-performance-durchsatz-sitzungen](../in-progress/slice-qa-performance-durchsatz-sitzungen.md), [slice-qa-performance-rueckstau](../in-progress/slice-qa-performance-rueckstau.md) und [slice-qa-performance-heap-abschluss](slice-qa-performance-heap-abschluss.md) in `done/` **und** der Nutzer hat die aus den Messzeilen abgeleiteten Zahlen bestätigt (Entscheidung und Lieferung getrennt: der Slice liefert den Eintrag, nicht die Entscheidung).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert — Carveout?): Der Nutzer bestätigt die abgeleiteten Zahlen nicht oder verlangt andere Ziele als die Messung trägt; dann entscheidet er zwischen „Code angleichen“ (Optimierungs-Slice) und „Zielbild anpassen“ (Ziel ändern), und dieser Slice wartet auf die Entscheidung.

## 5. Closure-Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle Punkte der DoD in §2 erledigt, `make gates` grün, das Lastenheft nennt die Grenzwerte, alle Slices der Performance-Messung liegen in `done/` und die Closure-Notiz mit Steering-Loop-Lerneintrag ist geschrieben.

## 6. Risiken und offene Punkte


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Die Grenzwerte gelten für eine Umgebung und einen Messtag auf einem Host unter Last; ohne Nennung der Umgebung wären sie eine Behauptung (`AGENTS.md` §3.8). — **Ausgang:** offen bis zur Closure.
- Eine Messreihe fehlt oder ist „nicht belegt“ (Streuung); dann trägt das Lastenheft für diesen Punkt keinen Wert, sondern die Benennung der Lücke. — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` gilt für das ganze Repo (`*`, Greenfield); der Slice berührt `*`, eine feinere Sub-Area ist nicht deklariert.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen: `BEO-TDS/spec-nachzug-nach-messung-offen` und `BEO-TDS/aussagen-ueber-messung-hinaus` betreffen die Form des Nachzugs; die Zahl der Dateien unter `evidence/` ist der Zähler, hier nicht gesetzt.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
