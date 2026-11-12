# Slice slice-zweites-error-folgefehler-angleichen: Fehler 1781 und 1750 beim zweiten Standardwert und das zweite ERROR 3727 an den echten Server angleichen

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-testserver-manuell — die Welle gleicht den Testserver dem echten SQL Server an (Zielbild) und schließt erst, wenn alle ihre Slices in `done/` liegen; der Slice ist ein Mitglied der Gruppe „Angleichung an den echten SQL Server nach der Messung“, und sein Trigger „spätestens die Wellen-Closure“ ist genau diese Closure-Bedingung.

**Bezug:** [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md)

**Berührte Spec-Stellen:** SPEC-036 (Form D3, Form D5 Grenze 3727, Form D7 (b)) · SPEC-035 (Fehlernummern der Strukturabweisung) · SPEC-031 (Folge-Token nach einem Fehler, INFO 3621 nach 2628)

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912 (Entwurf in der Planner-Rolle, Closure von [slice-declare-df-standardwert-vorhandene-spalte](../done/slice-declare-df-standardwert-vorhandene-spalte.md), Entscheid des Nutzers 2026-11-12). **Datum:** 2026-11-12.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung**; die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks.

**Ziel:** Der Testserver antwortet auf zwei Strukturfehler mit der Folge von Fehlern des echten SQL Servers (Zielbild): (1) ein zweiter Standardwert an derselben Spalte (`ALTER TABLE … ADD CONSTRAINT … DEFAULT … FOR` ohne vorherigen Text mit `DECLARE @df`) endet mit 1781 und danach 1750 (beide Class 16) statt mit `TDS_SIM_OBJECT_EXISTS` (2714); (2) `ALTER TABLE … DROP CONSTRAINT` eines Namens ohne Treffer (3728) oder einer Bedingung einer anderen Tabelle (3733) sendet danach das zweite ERROR 3727. Beide Abweichungen sind am echten Server gemessen (Messprotokolle [standardwert-ersetzen-d-migrate-2026-11-12](../../../messungen/standardwert-ersetzen-d-migrate-2026-11-12.md) Zeile 04 und [drop-constraint-d-migrate-2026-11-11](../../../messungen/drop-constraint-d-migrate-2026-11-11.md) Zeilen 05, 08, 10, 13, 16, 19) und stehen als Punkt 15 (b) und Punkt 10 im Register [katalog-elemente-grenzen-der-angleichung](../observations/BEO-TDS/katalog-elemente-grenzen-der-angleichung/state.md). Entscheid des Nutzers 2026-11-12: Code angleichen, aber nicht im Slice, in dem die Abweichungen gefunden wurden. Der Mechanismus eines Folge-Tokens nach dem ERROR existiert (INFO 3621 nach 2628, `SPEC-031`).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Die Antwortfolge des Textes mit `DECLARE @df` (vier `DONE`, `RETURNSTATUS`, `DONEPROC`) — Entscheid des Nutzers 2026-11-12: Zielbild angepasst, Grenze in `SPEC-036` Form D7 (a); Trigger dort: ein Client, der die Zwischenabschlüsse auswertet.
- `ENVCHANGE` Typ 8 unter `IMPLICIT_TRANSACTIONS` — Entscheid des Nutzers 2026-11-12: gemeinsame Grenze der Transaktionszusage in `SPEC-031`.
- Namen von Standardwert und Schlüssel (Punkt 11 des Registers) und die Prüfung des Verweises 3725 (Punkt 12) — eigene Entscheide beziehungsweise nicht geplant.
- Fehlernummern, die am echten Server nicht gemessen sind — der Slice gleicht nur gemessene Folgen an (`AGENTS.md` §3.8).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit.

Liefer-Punkte (zwei):

- [ ] **Bau** ([TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend)): der zweite Standardwert an derselben Spalte endet mit 1781 und 1750 (Class 16, Verbindung offen), `DROP CONSTRAINT` mit 3728 oder 3733 sendet danach das zweite ERROR 3727; Nummern, Class und State aus den Ablagen der Messung (Handvektoren), Tests je Zusage und Mutationen je Zusage; ob der Folge-Fehler im RPC gilt, ist zu messen oder als Setzung zu kennzeichnen.
- [ ] **Fortschreibung und Beleg** ([TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)): `SPEC-035`, `SPEC-036` (Form D3, D5, D7 (b)), Nutzer-Doku und Register (Punkte 10 und 15 (b)) nachgezogen; `schema migrate --execute` von d-migrate bleibt gegen den Testserver bei Exit 0 und `IDENTICAL`.

Nachweis-Pflichten für alle Punkte:

- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Haken der Liefer-Punkte setzt der Planner nach der Verifikation, nicht der Implementer (`BEO-TDS/haken-vor-verifikation-gesetzt`).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben oder „keine Beobachtung angefallen“ in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — Pfad-Kandidaten für §8; der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/StructurePlanner.kt`, `SimulationChanges.kt` und die Fehlerregeln des Sequenzierers (Ort vom Implementer zu bestimmen) | update | Folge-Fehler nach dem ersten ERROR, Mechanismus wie INFO 3621 nach 2628 (`SPEC-031`); keine öffentliche Änderung erwartet |
| `src/test/kotlin/io/github/pt9912/tdswire/simulation/` (enge Testklassen der Formen D3, D5, D7) | update | Handvektoren der Ablagen `struktur-standardwert-ersetzen.messung` und `struktur-drop-constraint.messung`, Mutationen je Zusage |
| `spec/spezifikation.md` (`SPEC-035`, `SPEC-036`, Änderungsprotokoll), `docs/user/`, Register `BEO-TDS/katalog-elemente-grenzen-der-angleichung` | update | Zusage statt Grenze, Punkte 10 und 15 (b) |

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`open` → `next` → `in-progress`): die Abnahme [slice-abnahme-d-migrate](slice-abnahme-d-migrate.md) trifft einen der beiden Fälle, oder spätestens die Closure der Welle [welle-testserver-manuell](../welle-testserver-manuell.md) (die Welle schließt erst, wenn dieser Slice in `done/` liegt); ein Verantwortlicher ist eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): der Folge-Fehler verlangt einen neuen Mechanismus über den vorhandenen Folge-Token hinaus; Schnittvorschlag: 1781/1750 | 3727.
- `in-progress` → `open` (blockiert): der Nutzer entscheidet für einen der beiden Fälle, dass die Grenze das Zielbild ist; dann geht der Slice mit der Zeile `Gegenstand:` nach `done/`, der Satz in der Spezifikation wird als Entscheid des Nutzers gekennzeichnet.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln.

DoD vollständig, `make gates` grün, Review und Verifikation liegen vor, `make clean-build` und `make e2e` einmal durch den Verifier auf dem End-Stand (Änderung an `src/`), Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — jedes Risiko bekommt genau einen Ausgang.

- Der Folge-Fehler ist nur als Batch gemessen (Punkt 10), im RPC nicht; ein Client im RPC sähe eine Folge, die nicht gemessen ist. — **Ausgang:** offen bis zur Closure.
- Ein Client, der nur das erste ERROR kennt, sieht ein zusätzliches ERROR; d-migrate liest bisher das erste. — **Ausgang:** offen bis zur Closure.

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register; `grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln.

- **Was hat funktioniert:** —
- **Was ging anders als geplant:** —
- **Steering-Loop-Eintrag:** —
- **Beobachtungs-Register (`../observations/`):** —
- **Folge-Slices:** —
- **Risiken aus §6:** —

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung.

**Vorgelagert — Sub-Area-Wahl prüfen:** Eine Sub-Area (`*`, Kürzel `TDS`); keine Aufteilung nötig.

**Vorgelagert — offene Beobachtungen sichten:** `BEO-TDS/katalog-elemente-grenzen-der-angleichung` (Herkunft, Punkte 10 und 15 (b)).

Alle berührten Sub-Areas GF.
