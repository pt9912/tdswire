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

**Entschieden am 2026-11-06 ([ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md), Option D mit B, Freigabe nur als Batch); die Frage lautete (Optionen, keine Vorentscheidung des Planners):** ob und wie die Grenze für diesen Pfad weicht — andere oder konfigurierbare Zahl, Freigabe über `sp_unprepare` (heute definierter Fehler laut SPEC-030), Wiederverwendung eines Handles für denselben Anweisungstext, oder Zerlegung des Imports so, dass die Grenze nicht berührt wird. Die ADR stellt die Optionen mit Messzeile oder als Setzung gekennzeichnet und führt die Offen-Liste mit Trägern.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Code für Handle-Tabelle, Grenze oder `sp_unprepare` — [slice-schreibpfad-merge-output](../in-progress/slice-schreibpfad-merge-output.md) baut, was die ADR entscheidet; dieser Slice entscheidet nur und baut nicht.
- Die Zerlegung von `MERGE`, die Zeilenquelle nach Schlüssel und die Weitergabe an den Integrator — durch [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) entschieden und unberührt.
- Änderungen an [ADR-0017](../../adr/0017-prepared-handles-grenzwert.md) selbst — die Datei bleibt `Accepted` und unverändert (`AGENTS.md` §3.5); die Entscheidungen 2 und 3 (definierter Fehler bei Überschreitung, Zugriff unter dem Sitzungsschutz) bleiben, soweit die neue ADR sie nicht wörtlich nennt.
- Die Gegenmessung am echten SQL Server (Zahl und Verhalten der Handles) — [slice-qa-vergleichslauf-d-messungen](../done/slice-qa-vergleichslauf-d-messungen.md) und die EULA-Entscheidung des Nutzers; hier bleibt die Zahl Setzung, wo sie nicht gemessen ist.
- Die Auswahl der Optionen durch den Planner — die Entscheidung gehört dem Nutzer.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit.

Liefer-Punkte (3):

- [x] **Optionen mit Messung oder Setzung** *(Beleg: Tabelle in der ADR, Messzeilen im Messprotokoll [callback-loesung-spike-2026-11-05](../../../messungen/callback-loesung-spike-2026-11-05.md) oder einem neuen Protokoll unter `docs/messungen/`)*: je Option die Kosten (Speicher je Handle bei `MAX_CONNECTIONS`, Verhalten von d-migrate bei der Überschreitung und bei `sp_unprepare`, soweit mit dem Testserver messbar); nicht Gemessenes steht als Setzung, nicht als Befund.
- [x] **ADR mit Entscheidung des Nutzers** *(nächste freie Nummer laut ADR-Index; `Accepted` erst nach dem Entscheid des Nutzers; `Supersedes` nur wörtlich Entscheidung 1 und 4 von [ADR-0017](../../adr/0017-prepared-handles-grenzwert.md), Fitness Function und Index nachgezogen)*: jede Aussage trägt eine Messzeile oder ist als Setzung gekennzeichnet; die Folgen für `SPEC-030` und die Verträge sind benannt.
- [x] **Liste der Spec-Änderungen und Anschluss** *(in §3 und im Anschluss von [slice-schreibpfad-merge-output](../in-progress/slice-schreibpfad-merge-output.md))*: Änderungen an SPEC-030 und SPEC-034, die der MERGE-Slice schreibt; Start-Trigger und Risiko des MERGE-Slice an die Entscheidung angeschlossen. Die Spezifikation ändert dieser Slice nicht.

Nachweis-Pflichten für alle Punkte:

- [x] `make gates` grün (Verifikation und Closure, beide selbst gefahren).
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist (kein Vertrag geändert: die Spezifikation bleibt unberührt, ADR-Index nachgezogen).
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben oder „keine Beobachtung angefallen“ in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — Pfad-Kandidaten für §8; der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| [ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md) | neu (`Accepted` am 2026-11-06) | Entscheidung samt Optionen, Messzeilen und Offen-Liste mit Trägern |
| `docs/plan/adr/README.md` | update | ADR-Index (`AGENTS.md` §5 Regel 2 und 3) |
| [handle-grenze-optionen-2026-11-06](../../../messungen/handle-grenze-optionen-2026-11-06.md) | neu | Messzeilen der Optionen, soweit messbar |
| [slice-schreibpfad-merge-output](../in-progress/slice-schreibpfad-merge-output.md) | update | Start-Trigger (die ADR `Accepted`), Anschluss, Risiko |
| `spec/spezifikation.md` | nicht in diesem Slice | SPEC-030 und SPEC-034 schreibt der MERGE-Slice nach `Accepted` |

**Spec-Änderungen, die der MERGE-Slice (oder ein vom Nutzer geschnittener Bau-Slice) nach `Accepted` schreibt** (entschieden: Fassung D mit B, Freigabe nur als Batch; Liste mit Einzelheiten in der Tabelle „Liste der Spec-Änderungen“ von [ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md)): SPEC-030 (Antwort auf `sp_prepexec` mit RETURNSTATUS, Freigabe über `sp_unprepare` als Batch (der RPC bleibt abgelehnt), Zählung, Voreinstellung 1000 statt „nicht konfigurierbar“, Erstaufruf 0 oder −1, RPC-Tabelle), SPEC-058 (Grenze als Voreinstellung), SPEC-034 und §3 (Eingang der Grenze, Speicher je Sitzung), SPEC-001 (Batch-Form im Sitzungsrahmen), SPEC-059 (falls öffentlicher Name; Name und Ort mit dem Slice zur öffentlichen Fläche abgestimmt), Abschnitt „RPC-Katalog des Referenzclients“, die Kompatibilitätsmatrix (`make matrix-zitate`) und die Kommentare und Tests in `src/` (Einzelliste in der ADR). SPEC-013, SPEC-036, SPEC-040 und Nicht-Ziel 6 ändern sich durch diese Entscheidung nicht; [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) bleibt unberührt. Der Bau-Slice vor dem MERGE-Slice ist [slice-handle-freigabe-und-grenze-bauen](../done/slice-handle-freigabe-und-grenze-bauen.md).

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) steht auf `Accepted` (erfüllt am 2026-11-06) und ein Verantwortlicher ist eingetragen.

**Kante:** Dieser Slice steht **vor** [slice-schreibpfad-merge-output](../in-progress/slice-schreibpfad-merge-output.md) (Nutzerentscheidung vom 2026-11-06, Folgepflicht 1 von [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md)); der MERGE-Slice trägt die Kante als eigenen Start-Trigger. Zu den Slices Katalog und Massenladen besteht keine Kante.

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

- Die Messung der Optionen stützt sich auf d-migrate gegen den Testserver, nicht gegen einen echten SQL Server; ob dieser Handles je Sitzung begrenzt und wie er die Überschreitung beantwortet, ist nicht gemessen (`BEO-TDS/messung-am-echten-sql-server-fehlt`). — **Ausgang:** weiter offen; gemessen ist mssql-jdbc 13.4.0 gegen den gebauten Testserver, nicht d-migrate und nicht der echte Server; Träger [slice-schreibpfad-merge-output](../in-progress/slice-schreibpfad-merge-output.md) (Lauf von d-migrate) und [slice-qa-vergleichslauf-d-messungen](../done/slice-qa-vergleichslauf-d-messungen.md) (echter Server).
- `sp_unprepare` ist in SPEC-030 als abgelehnt festgelegt; die Entscheidung gibt nur die Batch-Form frei und lässt den Katalog der RPCs unverändert. — **Ausgang:** entfallen; [ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md) streicht den RPC-Teil, der RPC `sp_unprepare` bleibt abgelehnt, [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) und `SPEC-013` bleiben unberührt (Verifikation V: kein Rest einer Katalog-Aufnahme).
- Zählt die ADR die Grenze anders (je Sitzung, je Anweisungstext), bleibt die Zusage von [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität) (Ressourcenverbrauch nach oben begrenzt) zu belegen. — **Ausgang:** weiter offen; die ADR zählt die gleichzeitig gehaltenen Handles je Sitzung und macht die Grenze einstellbar, der Speicher je Handle ist nicht gemessen; Träger [slice-handle-freigabe-und-grenze-bauen](../done/slice-handle-freigabe-und-grenze-bauen.md) (Risiko Speicher je Sitzung).

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register; `grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln.

- **Was hat funktioniert:** Die Messung vor der Wahl änderte das Bild: das Muster des Spikes zeigte sich beim Treiber allein, und ein RETURNSTATUS drehte sein Verhalten (Reihen h, t, u des [Messprotokolls](../../../messungen/handle-grenze-optionen-2026-11-06.md)); ohne sie wäre Option A (Zahl anheben) der einzige belegte Weg geblieben. Der Review vor `Accepted` (F-1 bis F-10) holte die Fehler am Text, bevor der Nutzer entschied: die zweiten Fundstellen der Spec-Liste, die Katalogregel von [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) (F-2, durch Streichung des RPC-Teils aufgelöst), Aussagen breiter als die Messung (F-3, F-7). Die Verifikation fand den Rest im Plan des Slice (V-1).
- **Was ging anders als geplant:** (a) Der Nutzer entschied „deine empfehlungen“ mit einer Änderung: der RPC `sp_unprepare` entfällt, nur der Batch bleibt; damit entfiel das `Supersedes` zu [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md), und der Bau-Slice ist ein eigener Slice statt Teil des MERGE-Slice. (b) Die Messung lief gegen den gebauten Testserver mit einem Eingriff im Durchlauf, nicht gegen einen Prototyp wie im Spike; das Wegwerf-Werkzeug liegt im Scratchpad. (c) `Accepted` kam vor der Verifikation; der Plan des Slice führte die ADR deshalb noch als `Proposed` und die Haken offen (V-1, bei der Closure behoben, mit Grep über die Nachbar-Slices: keine weitere Fundstelle).
- **Steering-Loop-Eintrag:** Nichts verkörpert; kein `liegt in`, gezählt wird. Drei Feststellungen. (1) **Finding-Klassen des Reviews** (F-1 bis F-10): zweite Fundstelle bei einer Fortschreibung (F-1, `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`, verkörpert im Reviewer-Skill, hier wieder gefunden vom Review und von der Verifikation V-1); Aussage breiter als der Beleg (F-3, F-7, `BEO-TDS/aussagen-ueber-messung-hinaus`, verkörpert in `AGENTS.md` §3.8); neue ADR weitet die Liste einer angenommenen ADR (F-2, `BEO-TDS/neue-adr-weitet-liste-einer-accepted-adr-ohne-supersedes`, 2×); Beleglage der Optionen ungleich (F-8, F-10: kein Register-Eintrag, in der ADR behoben). (2) **Verifikation V-1** ist die dritte Fundstelle der Klasse `BEO-TDS/slice-plan-veraltet-nach-gelieferter-adr` (jetzt 3×, Schwelle erreicht): `Accepted` wechselte, der Slice-Plan führte Status und Haken weiter als offen. Vorschlag an den Nutzer, nicht umgesetzt: Planner-Schritt „Anschluss-Blöcke und Zustandsfelder der Nachbar-Slices beim Wechsel einer ADR auf `Accepted` nachziehen“ in `.claude/commands/plan-welle.md`. (3) **Messklasse „Client gegen Prototyp“** (`BEO-TDS/spike-prototyp-nicht-im-repo-messung-nicht-wiederholbar`) trat ein zweites Mal auf: das Messwerkzeug liegt im Scratchpad, die Zeilen sind nachrechenbar, nicht wiederholbar; der Bau-Slice legt die Wiederholbarkeit seines Treiber-Belegs fest. Neue Beobachtung: der Testserver sendet bei `sp_prepexec` kein RETURNSTATUS, mssql-jdbc übernimmt das Handle dann nicht (`BEO-TDS/testserver-sendet-bei-sp-prepexec-kein-returnstatus`, Träger der Bau-Slice). **Benannte Spec-Lücke:** `SPEC-030` führt weder Freigabe über den Batch noch einstellbare Grenze noch die Antwortform mit RETURNSTATUS; die Liste steht in [ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md), der Träger ist [slice-handle-freigabe-und-grenze-bauen](../done/slice-handle-freigabe-und-grenze-bauen.md).
- **Beobachtungs-Register (`../observations/`):** weitere Datei in `evidence/` bei `fortschreibung-laesst-zweite-fundstelle-stehen`, `aussagen-ueber-messung-hinaus`, `slice-plan-veraltet-nach-gelieferter-adr`, `neue-adr-weitet-liste-einer-accepted-adr-ohne-supersedes`, `spike-prototyp-nicht-im-repo-messung-nicht-wiederholbar`, `messung-am-echten-sql-server-fehlt`; neu: `testserver-sendet-bei-sp-prepexec-kein-returnstatus` und `nutzerentscheid-ohne-fundort` (V-2: der Entscheid „deine empfehlungen“ steht in der ADR als Zitat mit Datum, ohne Fundort; der Verifier konnte ihn nicht gegen das Original prüfen).
- **Folge-Slices:** [slice-handle-freigabe-und-grenze-bauen](../done/slice-handle-freigabe-und-grenze-bauen.md) (Antwortform, Freigabe, Zählung, einstellbare Grenze, Spec-Liste; Start nach Modul-Umbau und Schnitt der Fläche, vor dem MERGE-Slice); [slice-schreibpfad-merge-output](../in-progress/slice-schreibpfad-merge-output.md) (Start-Trigger um den Bau-Slice ergänzt, trägt den Lauf von d-migrate mit Zählung); [slice-qa-vergleichslauf-d-messungen](../done/slice-qa-vergleichslauf-d-messungen.md) (echter Server).
- **Risiken aus §6:** Ausgänge in §6: ein Risiko entfallen (RPC-Katalog), zwei weiter offen mit Träger (Messung am echten Server und an d-migrate; Speicher je Sitzung).
- **Nachtrag Steering-Loop-Eintrag (2026-11-06, Entscheidung des Nutzers „deine empfehlungen“):** Die Klasse [BEO-TDS/slice-plan-veraltet-nach-gelieferter-adr](../observations/BEO-TDS/slice-plan-veraltet-nach-gelieferter-adr/observation.md) (3×) ist verkörpert, damit ist Feststellung (2) des Eintrags darunter entschieden. Regel: Planner-Schritt 6a, beim Wechsel einer ADR auf `Accepted` die Zustandsfelder der Nachbar-Slices per Grep über das ganze Repo nachziehen, die ADR bleibt unberührt; Slice-Beleg: V-1. Die Messklasse „Client gegen gebautes Produkt“ bleibt bei der Zählung von `spike-prototyp-nicht-im-repo-messung-nicht-wiederholbar`. V-2 (Fundort des Nutzerentscheids in der ADR) bleibt benannte Grenze im Register `nutzerentscheid-ohne-fundort`. liegt in `.claude/commands/plan-welle.md`

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung.

**Vorgelagert — Sub-Area-Wahl prüfen:** Eine Sub-Area (`*`, Kürzel `TDS`); keine Aufteilung nötig.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (Namensliste, Texte nicht einzeln gelesen). Treffer: [messung-am-echten-sql-server-fehlt](../observations/BEO-TDS/messung-am-echten-sql-server-fehlt/observation.md); [spike-prototyp-nicht-im-repo-messung-nicht-wiederholbar](../observations/BEO-TDS/spike-prototyp-nicht-im-repo-messung-nicht-wiederholbar/observation.md); [tds-8-0-und-pooling-nicht-gemessen](../observations/BEO-TDS/tds-8-0-und-pooling-nicht-gemessen/observation.md) (Handles unter Pooling). Zählerstände liest der Implementer bei Start.

Alle berührten Sub-Areas GF.
