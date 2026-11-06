# Slice slice-handle-freigabe-und-grenze-bauen: Antwortform von `sp_prepexec`, Freigabe der Prepared-Handles als Batch, Zählung und einstellbare Grenze je Sitzung (baut ADR-0036)

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-testserver-manuell

**Bezug:** [TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md), [ADR-0017](../../adr/0017-prepared-handles-grenzwert.md), [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md), [ADR-0004](../../adr/0004-session-zustandsautomat.md), [ADR-0014](../../adr/0014-fehlerfolge-rpc-antwort.md)

**Berührte Spec-Stellen:** SPEC-030 (Antwort auf `sp_prepexec`, Handle-Verwaltung, Zählung, Voreinstellung 1000, Parameter 0, RPC-Tabelle) · SPEC-001 (Batch-Form der Freigabe im Sitzungsrahmen) · SPEC-058 (Grenze als Voreinstellung) · SPEC-034 und §3 (Eingang der Grenze, Speicher je Sitzung) · SPEC-059 (falls die Grenze ein öffentlicher Name wird) · Abschnitt „RPC-Katalog des Referenzclients“; unverändert: SPEC-013, SPEC-036, SPEC-040, SPEC-057, Nicht-Ziel 6; vollständige Liste in der Tabelle „Liste der Spec-Änderungen“ von [ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md)

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-06.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert; jeder Ausschluss mit Begründung.

**Ziel:** Die Bibliothek baut die Entscheidung von [ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md) (`Accepted`, Option D mit B): `sp_prepexec` antwortet mit einem RETURNSTATUS vor dem RETURNVALUE `@handle`, der SQL-Batch `EXEC sp_unprepare <n>[;EXEC sp_unprepare <n>…]` gibt Handles frei, gezählt werden die gleichzeitig gehaltenen Handles, und die Grenze (Voreinstellung 1000) ist einstellbar; die Spezifikation führt die neue Lesart.

**Ausgangslage (Messung, nicht Entscheidung).** Der gebaute Testserver sendet bei `sp_prepexec` kein RETURNSTATUS (Reihe h6 des [Messprotokolls](../../../messungen/handle-grenze-optionen-2026-11-06.md)); mssql-jdbc 13.4.0 übernimmt das Handle dann nicht: je Ausführung ein `sp_prepexec`, nie ein `sp_execute`, nie ein `sp_unprepare` (h2, h3). Mit RETURNSTATUS (Eingriff im Durchlauf, Reihen t) benutzt ein Anweisungsobjekt sein Handle (t1) und der Treiber gibt geschlossene Anweisungen als SQL-Batch frei (t3, t5), den der Testserver heute mit „Unsupported SQL.“ beantwortet (t6). Gemessen ist das am Treiber allein, nicht an d-migrate und nicht an einem echten SQL Server.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Der RPC `sp_unprepare` (ProcID 15) und `sp_prepare`: bleiben `TDS_UNSUPPORTED_RPC` (`SPEC-013`); der Nutzer hat den RPC-Teil gestrichen, kein Treiber der Messung sendet ihn ([ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md) Entscheidung 2, Offen 5).
- Der Lauf von d-migrate mit `--on-conflict skip` über mehr als 1001 Zeilen je Sitzung mit Zählung der Handles — [slice-schreibpfad-merge-output](slice-schreibpfad-merge-output.md) (Folgepflicht 3 der ADR; ob d-migrate seine Anweisungen schließt, ist nicht gemessen).
- Antworten eines echten SQL Servers (RETURNSTATUS auf `sp_prepexec`, `sp_unprepare` mit unbekanntem Handle, Grenze und Speicher je Handle) — [slice-qa-vergleichslauf-d-messungen](slice-qa-vergleichslauf-d-messungen.md) und die EULA-Entscheidung des Nutzers (`BEO-TDS/messung-am-echten-sql-server-fehlt`).
- Pooling, TDS 8.0 und andere Treiberversionen oder Clients (Offen 4 und 5 der ADR) — nicht gemessen, kein Träger in dieser Welle (`BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`).
- Verdrängung (Option E) und Überlauf ohne Handle (Option F): in der ADR nicht gewählt; Entscheidung 2 von [ADR-0017](../../adr/0017-prepared-handles-grenzwert.md) (definierter Fehler bei Überschreitung) bleibt.
- Die Form des `MERGE` und die Zerlegung — [slice-schreibpfad-merge-output](slice-schreibpfad-merge-output.md) und [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md).
- Ein Name für den Eingang der Grenze vor der Abstimmung: Offen 7 der ADR; der Slice legt keinen öffentlichen Namen an, bevor Name und Ort in der Spezifikation mit [slice-oeffentliche-flaeche-verkleinern-und-belegen](../in-progress/slice-oeffentliche-flaeche-verkleinern-und-belegen.md) abgestimmt sind.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit.

Liefer-Punkte (3):

- [ ] **Antwortform, Freigabe und Zählung** *(Bibliothek)*: `sp_prepexec` antwortet mit RETURNSTATUS (Wert 0) vor RETURNVALUE `@handle` (Handvektor); der Batch `EXEC sp_unprepare <n>` mit weiteren `;EXEC sp_unprepare <n>` gibt im Sitzungsrahmen frei, ohne das Backend zu rufen, mit Erfolg ohne Zeilen, ein unbekanntes Handle ist kein Fehler, eine freigegebene Nummer wird nicht wieder vergeben; gezählt werden vergebene minus freigegebene. Tests laut Fitness Function von [ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md), darunter die Negativfälle (gemischter Batch geht unverändert an das Backend; andere Schreibweise; nichtnumerisches und negatives Argument; Länge über der Grenze der Nachricht; der RPC `sp_unprepare` bleibt `TDS_UNSUPPORTED_RPC` und gibt nichts frei), zwei Sitzungen zählen getrennt, Gegenprobe ohne Entfernen aus der Tabelle. Beleg am Treiber: ein Anweisungsobjekt mit 200 Ausführungen ergibt 1 `sp_prepexec` und 198 `sp_execute`, 1100 neue Anweisungen mit Erstes-Mal-Prepare brechen nicht an der Grenze ab (Wiederholbarkeit legt der Slice fest; Messprotokoll unter `docs/messungen/`, Klasse „Client gegen gebautes Produkt“).
- [ ] **Einstellbare Grenze** *(Bibliothek und Testserver)*: Die Grenze ist ein Eingang mit der Voreinstellung 1000; Name, Ort, Bereich und Umgebungsvariable des Testservers stehen in der Spezifikation, abgestimmt mit [slice-oeffentliche-flaeche-verkleinern-und-belegen](../in-progress/slice-oeffentliche-flaeche-verkleinern-und-belegen.md) (neuer öffentlicher Name oder bestehende Konfiguration, `SPEC-059`); die Anfrage, die über die eingestellte Grenze vergäbe, ergibt weiter `SPEC-058`; ungültige Werte (null, negativ, nichtnumerisch) haben einen festgelegten Ausgang.
- [ ] **Spezifikation, Matrix und Kommentare** *(Beleg: `make docs-check`, `make matrix-zitate`, `make gates`)*: die Spec-Änderungen laut Tabelle „Liste der Spec-Änderungen“ von [ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md) sind geschrieben (SPEC-030, SPEC-001, SPEC-058, SPEC-034 und §3, SPEC-059 falls nötig, RPC-Katalog des Referenzclients), jede Zusage trägt eine Messzeile oder ist als Setzung gekennzeichnet; die Kompatibilitätsmatrix ist nachgezogen und jedes Zitat `Klasse > Testfall` bleibt PASSED; Kommentare und Tests der alten Lesart in `src/` sind je Stelle geprüft und nachgezogen (Grep nach `sp_unprepare`, „nicht konfigurierbar“, „−1 beim Erstaufruf“).

Nachweis-Pflichten für alle Punkte:

- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist (SPEC-059, Nutzer-Doku zum Testserver bei neuer Umgebungsvariable).
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben oder „keine Beobachtung angefallen“ in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — Pfad-Kandidaten für §8; der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/` (Handle-Tabelle `PreparedHandles.kt`, Antwort des RPC, Sitzungsrahmen `RequestLoop.kt`) | update | RETURNSTATUS, Batch-Freigabe vor dem Backend, Zählung, Grenze als Eingang |
| Konfiguration der Bibliothek und Start des Testservers (Ort nach der Abstimmung mit dem Slice zur öffentlichen Fläche; Testserver nach dem Modul-Umbau unter `testserver/kotlin/`) | update | Eingang der Grenze, Umgebungsvariable des Testservers |
| Tests: `PreparedHandlesDispatchTest` und Nachbarn für Batch, Antwortform, Grenze | update / neu | Happy, Boundary, Negative nach [TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc) und [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität); `RpcParameters.kt`, `RpcProcedureName.kt`, `RpcDispatchTest`, `RpcBinderTest`, `RpcCatalogFormsTest`, `RequestSequenceTest` betreffen den RPC und werden je Stelle geprüft |
| `spec/spezifikation.md` | update | Liste der Spec-Änderungen der ADR |
| `docs/plan/kompatibilitaetsmatrix.md` | update | Zeilen `sp_prepexec`, Handle-Verwaltung, Lücke 13; `make matrix-zitate` |
| `docs/messungen/` — neues Messprotokoll | neu | Beleg am Treiber gegen den gebauten Stand |

**Anschluss:** Die Spec-Änderungen und ihre Einzelstellen stehen in [ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md) („Liste der Spec-Änderungen“) und in §3 von [slice-adr-handle-grenze-ersatz](../done/slice-adr-handle-grenze-ersatz.md). Der Implementer erweitert die Liste im ersten Lauf und schlägt die Reihenfolge der Commits vor (Spec getrennt vom Code).

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md) steht auf `Accepted` (erfüllt am 2026-11-06), [slice-testserver-gradle-modul](../in-progress/slice-testserver-gradle-modul.md) liegt in `done/` (der Pfad des Testservers wandert nach `testserver/`; der Eingang der Grenze schreibt dort) und [slice-oeffentliche-flaeche-verkleinern-und-belegen](../in-progress/slice-oeffentliche-flaeche-verkleinern-und-belegen.md) liegt in `done/` (Schnitt der Fläche steht, Name und Ort des Eingangs sind abstimmbar) und ein Verantwortlicher ist eingetragen.

**Kante:** Dieser Slice steht **vor** [slice-schreibpfad-merge-output](slice-schreibpfad-merge-output.md) (Nutzerentscheidung vom 2026-11-06, Folgepflicht 2 von [ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md)); der MERGE-Slice trägt die Kante als Start-Trigger. Zu [slice-katalog-und-ddl-zustand](slice-katalog-und-ddl-zustand.md) und [slice-schreibpfad-bulk-load](slice-schreibpfad-bulk-load.md) besteht keine Kante.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte; Schnittvorschlag: Antwortform und Freigabe | Grenze und Spezifikation.
- `in-progress` → `open` (blockiert): der Name des Eingangs der Grenze ist mit dem Slice zur öffentlichen Fläche nicht abgestimmt, oder eine Messung am gebauten Stand widerlegt eine Setzung der ADR (Re-Evaluierungs-Trigger der ADR; dann Entscheidung des Nutzers).

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln.

Die DoD ist vollständig, `make gates` ist grün, `make matrix-zitate` ist grün, ein Review-Report liegt vor, die Messzeilen des Treiber-Belegs stehen im Messprotokoll, und die Closure-Notiz trägt den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — jedes Risiko bekommt genau einen Ausgang.

- Die Wirkung des RETURNSTATUS ist am Treiber mit einem Eingriff im Durchlauf gemessen (Reihen t), nicht am gebauten Server; der gebaute Server kann abweichen (andere Paketlänge, andere Reihenfolge). — **Ausgang:** offen bis zur Closure.
- Die Grammatik des Batches ist aus zwei Mitschnitten gelesen (t5); das Ende des Textes ist nicht im Mitschnitt, Schreibweisen und Längen sind Setzung. — **Ausgang:** offen bis zur Closure.
- Ob d-migrate seine Anweisungen schließt, ist nicht gemessen; schließt es sie nicht, bleibt die einstellbare Grenze der einzige Schutz für den MERGE-Pfad (`BEO-TDS/messung-am-echten-sql-server-fehlt`, Träger für den Lauf: [slice-schreibpfad-merge-output](slice-schreibpfad-merge-output.md)). — **Ausgang:** offen bis zur Closure.
- Der Eingang der Grenze kann ein neuer öffentlicher Name werden; [slice-oeffentliche-flaeche-verkleinern-und-belegen](../in-progress/slice-oeffentliche-flaeche-verkleinern-und-belegen.md) trägt den Eintrag „Möglicher neuer Name aus der Handle-Grenze“. — **Ausgang:** offen bis zur Closure.
- Speicher je Sitzung (Grenze × Textgröße) ist nicht gemessen; `SPEC-034` kennt im gebauten Stand kein Limit für `MAX_CONNECTIONS`, die Zusage von [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität) stützt sich auf die Grenze je Sitzung. — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (Namensliste, Texte nicht einzeln gelesen). Treffer: `BEO-TDS/messung-am-echten-sql-server-fehlt`; `BEO-TDS/spike-prototyp-nicht-im-repo-messung-nicht-wiederholbar` (Messwerkzeug im Scratchpad: Wiederholbarkeit des Belegs festlegen); `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`; `BEO-TDS/testserver-sendet-bei-sp-prepexec-kein-returnstatus` (Ausgangslage). Zählerstände liest der Implementer bei Start.

Alle berührten Sub-Areas GF.
