# Slice slice-qa-performance-rueckstau: Performance: Rückstau am Socket, bei blockierendem Backend und beim Simulations-Backend

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-qualitaet-grundlage

**Bezug:** [TDS-QA-04](../../../../spec/lastenheft.md#tds-qa-04--performance), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)

**Berührte Spec-Stellen:** `SPEC-022` · `SPEC-035` (Rückstau-Zusage in `spec/spezifikation.md`, Abschnitt „Pflichten des Backends“: „gilt für das Test-Backend an einem Draht ohne Netz, nicht für einen Socket, ein blockierendes Backend oder das Simulations-Backend“)

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

**Ziel:** Der Rückstau ist im Serverprozess über einen Socket mit mehreren Verbindungen, mit einem blockierenden Backend und mit dem Simulations-Backend gemessen; jeder der drei Punkte trägt eine Messzeile oder ist mit Begründung als nicht lieferbar benannt (`BEO-TDS/rueckstau-zusage-nur-test-backend-traeger-unvollstaendig`, `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`).

**Übernimmt:** aus dem DoD-Punkt „Lasttest“ von [slice-qa-performance](../done/slice-qa-performance.md) (Fassung vor der Zerlegung vom 2026-11-15) die Punkte Socket, blockierendes Backend und Simulations-Backend; die Träger-Zuweisung der beiden Beobachtungen bleibt bei diesem Gegenstand.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Heap beim Strom und Latenz des zurückgehaltenen Abschlusses — liegen bei [slice-qa-performance-heap-abschluss](../open/slice-qa-performance-heap-abschluss.md).
- Durchsatz und p95 der Staffel — liegen bei [slice-qa-performance-durchsatz-sitzungen](../in-progress/slice-qa-performance-durchsatz-sitzungen.md); hier zählt, wie weit das Backend dem Leser vorausläuft und was ein langsamer Leser den übrigen Sitzungen nimmt.
- Wirkung einer blockierenden Senke der Telemetrie auf den Anfragepfad — ein anderer Gegenstand (Senke, nicht Backend); `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` führt sie weiter, sie bleibt dort offen, bis ein Slice sie übernimmt.
- Ein Schreibvorgang, der am Rückstau die Sitzungssperre hält und damit Frist und Abbruch aufschiebt (`SPEC-009`, `SPEC-033`): er wird beobachtet, wo er in den Läufen auftritt, die Behebung ist kein Teil — eine Behebung wäre „Code angleichen“ oder „Zielbild anpassen“ und ein eigener Slice.

## 2. Definition of Done


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [ ] **Socket, mehrere Verbindungen:** am Serverprozess (Aufbau B und C aus dem [Referenz-Workload](../../referenz-workload.md)) mit der Stufe 256 der Staffel: Zeilen, die das Backend dem langsamsten Leser vorausläuft, und Verhalten der übrigen Sitzungen; die Zahl ist neben die Einzelmessung (eine Maschine, Loopback, 32 MiB, 500 ms) gestellt, und das Protokoll sagt, was am Docker-Netz auf demselben Host anders ist als Loopback (nicht anders genug für „Netz“).
- [ ] **Blockierendes Backend:** ein Backend, dessen Lesen oder Schreiben wartet (unterbrechbar und nicht unterbrechbar), unter Last: Threads, Dauer, Abbruch nach `CANCEL_TIMEOUT`, Wirkung auf andere Sitzungen — eine Messzeile oder „nicht lieferbar“ mit Begründung.
- [ ] **Simulations-Backend:** Rückstau der Zeilenquelle und des Massenladens bei langsamem Leser beziehungsweise Sender — eine Messzeile oder „nicht lieferbar“ mit Begründung; die Aussage in `spec/spezifikation.md`, Abschnitt „Pflichten des Backends“ („gilt nicht für“) ist auf den Beleg zurückgeschnitten oder erweitert.
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update: `spec/spezifikation.md` (Rückstau-Aussage), Messprotokoll unter `docs/messungen/`, `docs/user/` nur bei geändertem Verhalten.
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
| Szenarien des Werkzeugs aus [slice-qa-performance](../done/slice-qa-performance.md) (langsamer Leser, blockierendes Backend, Stufe 256) | update | Messung nach [TDS-QA-04](../../../../spec/lastenheft.md#tds-qa-04--performance) und [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) |
| Messende Backends (blockierend, zählend) im Testserver-Modul | neu | Aufbau mit Zähler, Erzeuger im Repo |
| `docs/messungen/performance-rueckstau-<datum>.md` | neu | Messprotokoll, Einzelmessung (`AGENTS.md` §5 Regel 7) |
| `spec/spezifikation.md`, Abschnitt „Pflichten des Backends“ | update | Aussage trägt höchstens so weit wie der Beleg (`AGENTS.md` §3.8) |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-qa-performance](../done/slice-qa-performance.md) in `done/` (Werkzeug und Aufbauten). Die Reihenfolge nach [slice-qa-performance-durchsatz-sitzungen](../in-progress/slice-qa-performance-durchsatz-sitzungen.md) ist eine Setzung, damit die Streuung des Hosts zuerst an der einfacheren Messung bekannt ist; formal genügt der Start nach [slice-qa-performance](../done/slice-qa-performance.md).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert — Carveout?): Ein Aufbau (blockierendes Backend, Simulations-Backend am Serverprozess) ist nur mit einer Änderung der öffentlichen Fläche herstellbar (`SPEC-059`); der Punkt steht dann als „nicht lieferbar“ mit Begründung in der Messzeile, und die Fläche ist ein eigener Vorgang.

## 5. Closure-Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle Punkte der DoD in §2 erledigt, `make gates` grün, Messprotokoll liegt unter `docs/messungen/`, die Register-Einträge `BEO-TDS/rueckstau-zusage-nur-test-backend-traeger-unvollstaendig` und `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` tragen den Ausgang dieses Slice im `Stand:`, und die Closure-Notiz mit Steering-Loop-Lerneintrag ist geschrieben.

## 6. Risiken und offene Punkte


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Der Rückstau am Socket hängt von den Puffern der Umgebung ab (`BEO-TDS/annahme-ueber-puffer-der-umgebung`); die Zahl gilt für diesen Host und dieses Docker-Netz, nicht für ein Netz zwischen Hosts. — **Ausgang:** offen bis zur Closure.
- Ein blockierendes Backend aus Treiberaufrufen verhält sich anders als der Wartezustand des Messaufbaus (der Spike nahm Interrupts als Annahme, [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)). — **Ausgang:** offen bis zur Closure.
- Die Messung kann eine Zusage der Spezifikation widerlegen; dann entsteht ein Nachzug (Code angleichen oder Zielbild anpassen), nicht in diesem Slice. — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen: `BEO-TDS/rueckstau-zusage-nur-test-backend-traeger-unvollstaendig` (1 Datei, Träger dieser Slice), `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` (11 Dateien: die Schwelle von 3× ist überschritten, die Lücke trägt diese Zerlegung), `BEO-TDS/annahme-ueber-puffer-der-umgebung` (2 Dateien, Risiko oben); den Zähler bildet die Zahl der Dateien unter `evidence/`, hier nicht gesetzt.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
