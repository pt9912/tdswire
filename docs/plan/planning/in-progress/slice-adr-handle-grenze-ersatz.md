# Slice slice-adr-handle-grenze-ersatz: Entscheidung: neue ADR ersetzt Grenzwert und Konfigurierbarkeit der Prepared-Handles (Entscheidung 1 und 4 von ADR-0017)

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-testserver-manuell

**Bezug:** [TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [ADR-0017](../../adr/0017-prepared-handles-grenzwert.md), [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md), [ADR-0004](../../adr/0004-session-zustandsautomat.md), [ADR-0014](../../adr/0014-fehlerfolge-rpc-antwort.md)

**Berührte Spec-Stellen:** SPEC-030 (Anzahl der Handles je Sitzung, Setzung 1000; Antwortform, Freigabe) · SPEC-034 (`MAX_CONNECTIONS`, Speicher je Sitzung, Eingang der Grenze) · SPEC-040 (Läufe `--on-conflict skip|update` gegen den Testserver; unverändert) · SPEC-001 · SPEC-058 · SPEC-059 (falls die Grenze ein öffentlicher Name wird); vollständige Liste in §3

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-06.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert; jeder Ausschluss mit Begründung.

**Ziel:** Eine neue ADR liegt als Entscheidung des Nutzers vor, die Entscheidung 1 (Grenzwert 1000 je Sitzung) und Entscheidung 4 (Zahl fest, nicht einstellbar) von [ADR-0017](../../adr/0017-prepared-handles-grenzwert.md) wörtlich ersetzt, weil der Pfad `MERGE ... OUTPUT $action` von d-migrate die Grenze nach 1001 Anweisungen je Sitzung erreicht und der Client kein `sp_unprepare` sendet.

**Auslöser (Messung, nicht Entscheidung).** Der Spike von [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) (Messprotokoll [callback-loesung-spike-2026-11-05](../../../messungen/callback-loesung-spike-2026-11-05.md), Zeilen a6 und a7): Exit 5 nach 1001 `MERGE` je Sitzung; in 2002 Anweisungen kam kein `sp_unprepare` an; gemessen mit einem Prototyp, nicht gegen einen echten SQL Server (`BEO-TDS/spike-prototyp-nicht-im-repo-messung-nicht-wiederholbar`). Der Nutzer hat am 2026-11-06 entschieden, dass die Ablösung eine eigene ADR und ein eigener Slice vor dem MERGE-Slice ist.

**Zu entscheiden (Optionen, keine Vorentscheidung des Planners):** ob und wie die Grenze für diesen Pfad weicht — andere oder konfigurierbare Zahl, Freigabe über `sp_unprepare` (heute definierter Fehler laut SPEC-030), Wiederverwendung eines Handles für denselben Anweisungstext, oder Zerlegung des Imports so, dass die Grenze nicht berührt wird. Die ADR stellt die Optionen mit Messzeile oder als Setzung gekennzeichnet und führt die Offen-Liste mit Trägern.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Code für Handle-Tabelle, Grenze oder `sp_unprepare` — [slice-schreibpfad-merge-output](../open/slice-schreibpfad-merge-output.md) baut, was die ADR entscheidet; dieser Slice entscheidet nur und baut nicht.
- Die Zerlegung von `MERGE`, die Zeilenquelle nach Schlüssel und die Weitergabe an den Integrator — durch [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) entschieden und unberührt.
- Änderungen an [ADR-0017](../../adr/0017-prepared-handles-grenzwert.md) selbst — die Datei bleibt `Accepted` und unverändert (`AGENTS.md` §3.5); die Entscheidungen 2 und 3 (definierter Fehler bei Überschreitung, Zugriff unter dem Sitzungsschutz) bleiben, soweit die neue ADR sie nicht wörtlich nennt.
- Die Gegenmessung am echten SQL Server (Zahl und Verhalten der Handles) — [slice-qa-vergleichslauf-d-messungen](../open/slice-qa-vergleichslauf-d-messungen.md) und die EULA-Entscheidung des Nutzers; hier bleibt die Zahl Setzung, wo sie nicht gemessen ist.
- Die Auswahl der Optionen durch den Planner — die Entscheidung gehört dem Nutzer.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit.

Liefer-Punkte (3):

- [ ] **Optionen mit Messung oder Setzung** *(Beleg: Tabelle in der ADR, Messzeilen im Messprotokoll [callback-loesung-spike-2026-11-05](../../../messungen/callback-loesung-spike-2026-11-05.md) oder einem neuen Protokoll unter `docs/messungen/`)*: je Option die Kosten (Speicher je Handle bei `MAX_CONNECTIONS`, Verhalten von d-migrate bei der Überschreitung und bei `sp_unprepare`, soweit mit dem Testserver messbar); nicht Gemessenes steht als Setzung, nicht als Befund.
- [x] **ADR mit Entscheidung des Nutzers** *(nächste freie Nummer laut ADR-Index; `Accepted` erst nach dem Entscheid des Nutzers; `Supersedes` nur wörtlich Entscheidung 1 und 4 von [ADR-0017](../../adr/0017-prepared-handles-grenzwert.md), Fitness Function und Index nachgezogen)*: jede Aussage trägt eine Messzeile oder ist als Setzung gekennzeichnet; die Folgen für `SPEC-030` und die Verträge sind benannt.
- [ ] **Liste der Spec-Änderungen und Anschluss** *(in §3 und im Anschluss von [slice-schreibpfad-merge-output](../open/slice-schreibpfad-merge-output.md))*: Änderungen an SPEC-030 und SPEC-034, die der MERGE-Slice schreibt; Start-Trigger und Risiko des MERGE-Slice an die Entscheidung angeschlossen. Die Spezifikation ändert dieser Slice nicht.

Nachweis-Pflichten für alle Punkte:

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
§Was ist eine Sub-Area? — Pfad-Kandidaten für §8; der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| [ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md) | neu (`Proposed`) | Entscheidung samt Optionen, Messzeilen und Offen-Liste mit Trägern |
| `docs/plan/adr/README.md` | update | ADR-Index (`AGENTS.md` §5 Regel 2 und 3) |
| [handle-grenze-optionen-2026-11-06](../../../messungen/handle-grenze-optionen-2026-11-06.md) | neu | Messzeilen der Optionen, soweit messbar |
| [slice-schreibpfad-merge-output](../open/slice-schreibpfad-merge-output.md) | update | Start-Trigger (die ADR `Accepted`), Anschluss, Risiko |
| `spec/spezifikation.md` | nicht in diesem Slice | SPEC-030 und SPEC-034 schreibt der MERGE-Slice nach `Accepted` |

**Spec-Änderungen, die der MERGE-Slice (oder ein vom Nutzer geschnittener Bau-Slice) nach `Accepted` schreibt** (entschieden: Fassung D mit B, Freigabe nur als Batch; Liste mit Einzelheiten in der Tabelle „Liste der Spec-Änderungen“ von [ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md)): SPEC-030 (Antwort auf `sp_prepexec` mit RETURNSTATUS, Freigabe über `sp_unprepare` als Batch (der RPC bleibt abgelehnt), Zählung, Voreinstellung 1000 statt „nicht konfigurierbar“, Erstaufruf 0 oder −1, RPC-Tabelle), SPEC-058 (Grenze als Voreinstellung), SPEC-034 und §3 (Eingang der Grenze, Speicher je Sitzung), SPEC-001 (Batch-Form im Sitzungsrahmen), SPEC-059 (falls öffentlicher Name; Name und Ort mit dem Slice zur öffentlichen Fläche abgestimmt), Abschnitt „RPC-Katalog des Referenzclients“, die Kompatibilitätsmatrix (`make matrix-zitate`) und die Kommentare und Tests in `src/` (Einzelliste in der ADR). SPEC-013, SPEC-036, SPEC-040 und Nicht-Ziel 6 ändern sich durch diese Entscheidung nicht; [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) bleibt unberührt. Der Bau-Slice vor dem MERGE-Slice wird vom Planner angelegt.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) steht auf `Accepted` (erfüllt am 2026-11-06) und ein Verantwortlicher ist eingetragen.

**Kante:** Dieser Slice steht **vor** [slice-schreibpfad-merge-output](../open/slice-schreibpfad-merge-output.md) (Nutzerentscheidung vom 2026-11-06, Folgepflicht 1 von [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md)); der MERGE-Slice trägt die Kante als eigenen Start-Trigger. Zu den Slices Katalog und Massenladen besteht keine Kante.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte; Schnittvorschlag: Messung der Optionen | ADR und Anschluss.
- `in-progress` → `open` (blockiert): die Entscheidung des Nutzers steht aus, oder die Messung verlangt einen Echt-Server-Lauf (EULA-Entscheidung des Nutzers).

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln.

Die DoD ist vollständig, die ADR steht im Index auf `Accepted`, `make gates` ist grün, ein Review-Report liegt vor, und die Closure-Notiz ordnet jeden offenen Punkt der ADR einem Träger zu und trägt den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — jedes Risiko bekommt genau einen Ausgang.

- Die Messung der Optionen stützt sich auf d-migrate gegen den Testserver, nicht gegen einen echten SQL Server; ob dieser Handles je Sitzung begrenzt und wie er die Überschreitung beantwortet, ist nicht gemessen (`BEO-TDS/messung-am-echten-sql-server-fehlt`). — **Ausgang:** offen bis zur Closure.
- `sp_unprepare` ist in SPEC-030 als abgelehnt festgelegt; die Entscheidung gibt nur die Batch-Form frei und lässt den Katalog der RPCs unverändert. — **Ausgang:** offen bis zur Closure.
- Zählt die ADR die Grenze anders (je Sitzung, je Anweisungstext), bleibt die Zusage von [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität) (Ressourcenverbrauch nach oben begrenzt) zu belegen. — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (Namensliste, Texte nicht einzeln gelesen). Treffer: [messung-am-echten-sql-server-fehlt](../observations/BEO-TDS/messung-am-echten-sql-server-fehlt/observation.md); [spike-prototyp-nicht-im-repo-messung-nicht-wiederholbar](../observations/BEO-TDS/spike-prototyp-nicht-im-repo-messung-nicht-wiederholbar/observation.md); [tds-8-0-und-pooling-nicht-gemessen](../observations/BEO-TDS/tds-8-0-und-pooling-nicht-gemessen/observation.md) (Handles unter Pooling). Zählerstände liest der Implementer bei Start.

Alle berührten Sub-Areas GF.
