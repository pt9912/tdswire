# Slice slice-qa-performance-heap-abschluss: Performance: Heap beim Strom großer Ergebnisse und Latenz des zurückgehaltenen Abschlusses

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-qualitaet-grundlage

**Bezug:** [TDS-QA-04](../../../../spec/lastenheft.md#tds-qa-04--performance), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)

**Berührte Spec-Stellen:** `SPEC-002` · `SPEC-031` (Strom und Reihenfolge der Ereignisse; der zurückgehaltene Abschluss für `DONE_MORE`)

**Verantwortlich:** pt9912

**Autor:** pt9912. **Datum:** 2026-11-15.

---

## 1. Ziel und Abgrenzung


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Der Heap des Serverprozesses beim Strom großer Ergebnisse (10 Mio Zeilen aus dem [Referenz-Workload](../../referenz-workload.md)) und die Verzögerung durch den zurückgehaltenen Abschluss in Millisekunden sind am Serverprozess gemessen und an die Zahlen des Spike in [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) gestellt.

**Übernimmt:** aus dem DoD-Punkt „Lasttest“ von [slice-qa-performance](../done/slice-qa-performance.md) (Fassung vor der Zerlegung vom 2026-11-15) die Punkte Heap und Latenz des Abschlusses.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Rückstau bei langsamen Lesern und blockierenden Backends — liegt bei [slice-qa-performance-rueckstau](../done/slice-qa-performance-rueckstau.md); hier hat der Leser keine Pause.
- Durchsatz je Stufe der Staffel — liegt bei [slice-qa-performance-durchsatz-sitzungen](../done/slice-qa-performance-durchsatz-sitzungen.md); hier zählt die Heap-Linie, nicht die Zeilen/s.
- Die Grenze des Testserver-Bestands am Heap (`BEO-TDS/testserver-bestand-nicht-am-heap-begrenzt`) — der Bestand speichert, der Strom nicht; die Messung des Bestands ist ein anderer Gegenstand und bleibt im Register offen.
- Eine kompaktere Zeilendarstellung oder ein blockweise liefernder Iterator (im Spike von [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) als „nicht gebaut“ benannt) — Optimierung, ausgeschlossen aus der Welle (§6).

## 2. Definition of Done


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [ ] **Heap:** Live-Heap-Stichprobe im Serverprozess mit festem `-Xmx` beim Strom von 10 Mio Zeilen (eine Sitzung und mit der Staffel bis 16 Sitzungen), Aufbau B und C, mit Streuung; die Zeile sagt, dass es eine Stichprobe nach GC ist und keine Spitze (Aussage wie im Spike).
- [ ] **Abschluss:** Verzögerung des zurückgehaltenen Abschlusses (`DONE_MORE`-Vorschau) in ms, gemessen mit einem Rohbytes-Client am Serverprozess als Abstand zwischen der letzten Zeile eines Ergebnisses und seinem DONE, wenn das Backend das nächste Ereignis um eine einstellbare Zeit verzögert (0, 10, 100, 1000 ms; Setzung); eine Messzeile je Wert oder „nicht lieferbar“ mit Begründung.
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update: Messprotokoll unter `docs/messungen/`; der Satz „Die Vorschau kostet … einen zurückgehaltenen Abschluss, dessen Verzögerung nicht gemessen ist“ in [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) bleibt unverändert (die ADR ist `Accepted`, `AGENTS.md` §3.5); der Beleg steht im Protokoll, ein Nachzug in der Spezifikation nur, wo sie die Verzögerung nennt.
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
| Szenarien des Werkzeugs aus [slice-qa-performance](../done/slice-qa-performance.md) (Strom 10 Mio, Abstand Zeile/Abschluss) | update | Messung nach [TDS-QA-04](../../../../spec/lastenheft.md#tds-qa-04--performance) und [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) |
| Backend mit einstellbarer Verzögerung des nächsten Ereignisses | neu | Erzeuger im Repo, Aufbau B |
| `docs/messungen/performance-heap-abschluss-<datum>.md` | neu | Messprotokoll, Einzelmessung (`AGENTS.md` §5 Regel 7) |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-qa-performance](../done/slice-qa-performance.md) in `done/`; nach [slice-qa-performance-durchsatz-sitzungen](../done/slice-qa-performance-durchsatz-sitzungen.md) ist die Streuung des Hosts bekannt (Setzung der Reihenfolge). Die Reihenfolge zu [slice-qa-performance-rueckstau](../done/slice-qa-performance-rueckstau.md) ist offen; beide dürfen nacheinander laufen, das WIP-Limit gilt.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert — Carveout?): Der Heap des Serverprozesses ist am Container nicht von dem des Messwerkzeugs zu trennen (beide im selben Prozess), weil der Aufbau nur eine JVM trägt; dann steht der Punkt „Heap“ als nicht lieferbar mit Begründung.

## 5. Closure-Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle Punkte der DoD in §2 erledigt, `make gates` grün, Messprotokoll liegt unter `docs/messungen/`, die Register-Einträge zum Heap und zum zurückgehaltenen Abschluss tragen den Ausgang im `Stand:`, die Closure-Notiz mit Steering-Loop-Lerneintrag ist geschrieben.

## 6. Risiken und offene Punkte


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Der Live-Heap ist eine Stichprobe alle n ms nach GC (im Spike alle 500 ms); eine Spitze zwischen zwei Stichproben bleibt unsichtbar. — **Ausgang:** offen bis zur Closure.
- Die Verzögerung des Abschlusses hängt am Verhalten des Backends; ein Backend, das das nächste Ereignis sofort liefert, zeigt keine. Die Zahl gilt für das Messbackend, nicht für ein Produktivbackend. — **Ausgang:** offen bis zur Closure.
- Mit 10 Mio Zeilen (Zeilengröße auf dem Draht ungemessen) kann eine Stufe auf diesem Host länger laufen als ein Tag-Fenster der Last trägt. — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen: `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` (11 Dateien; Heap und zurückgehaltener Abschluss sind dort genannt) und `BEO-TDS/testserver-bestand-nicht-am-heap-begrenzt` (1 Datei, ausgeschlossen, siehe §1); den Zähler bildet die Zahl der Dateien unter `evidence/`, hier nicht gesetzt.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
