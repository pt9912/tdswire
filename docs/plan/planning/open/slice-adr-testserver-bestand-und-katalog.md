# Slice slice-adr-testserver-bestand-und-katalog: Entscheidung: Ort des Katalogs, Ort des veränderlichen Bestands, Massenladen (Paket 0x07) und Reihenfolge der Folge-Slices

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-testserver-manuell

**Bezug:** [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-RB-03](../../../../spec/lastenheft.md#tds-rb-03--protokollstandard), [TDS-RB-04](../../../../spec/lastenheft.md#tds-rb-04--externes-backend), [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md), [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md), [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md), [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md), [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md)

**Berührte Spec-Stellen:** SPEC-035 (Setzung „Dateitabellen sind leer, Einfügen wird abgewiesen“) · SPEC-036 (Formen, Katalog, MERGE) · SPEC-059 (öffentliche Fläche) · SPEC-040 (Läufe gegen den Testserver) · [TDS-FA-11.a](../../../../spec/spezifikation.md#tds-fa-11a--simulations-backend) · [TDS-FA-08.a](../../../../spec/spezifikation.md#tds-fa-08a--backend-vertrag) (Backend-Vertrag) · Protokollteil Pakettypen (Katalog der Pakete, 0x07 nicht enthalten)

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-05.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert; jeder Ausschluss mit Begründung.

**Ziel:** Die Architektur-Entscheidungen, auf denen die Folge-Slices ruhen, liegen als Entscheidung des Nutzers in einer neuen ADR (nächste freie Nummer laut ADR-Index) vor: (1) wo der Katalog (`sys.*`, `INFORMATION_SCHEMA`) lebt, (2) wo der veränderliche Bestand nach DDL und Schreiben lebt und was aus der Setzung „Dateitabellen sind leer, Einfügen wird abgewiesen“ wird, (3) wie das Massenladen (Paket 0x07) zur Bibliothek gehört und was es an der öffentlichen Fläche ändert, (4) die Reihenfolge der Folge-Slices.

**Quelle und Vorbehalt.** Auslöser ist die Sonde vom 2026-11-05 ([d-migrate-sonde-2026-11-05](../../../messungen/d-migrate-sonde-2026-11-05.md)). Ihr Vorbehalt gilt hier: alles, was dort „lief“, lief gegen erfundene Katalogantworten; belegt ist, was d-migrate sendet und dass Treiber und d-migrate die TDS-Antworten der Bibliothek verarbeiten. Aufwandsgrößen (S/M/L) sind Schätzungen der Sonde, nicht gemessen.

**Lastenheft: von der Nutzerentscheidung getragen, Wortlaut noch nicht gesichtet.** Das Lastenheft schloss die Zusagen aus, die [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate) jetzt verlangt. Die Stellen vor der Änderung (Fassung 0.6.1, wörtlich):

- [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), Out-of-Scope: „Entwicklung einer SQL-Engine, eines Query-Optimizers oder einer Persistenzschicht. Das mitgelieferte Simulations-Backend ([TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend)) ist keine SQL-Engine im Sinne dieses Ausschlusses.“
- [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), Beschreibung: „Es wertet kein SQL aus und speichert keine Daten: Was gespeichert wird, entscheidet der Integrator.“ Out-of-Scope: „Auswertung beliebigen SQLs (Verbunde, Aggregate, Ausdrücke, Unterabfragen, Sortierung), Persistenz, Transaktionssemantik über die Weitergabe an den Integrator hinaus und SQL-Server-Systemkataloge, soweit sie der Referenzclient nach Clientprofil nicht selbst abfragt.“
- Abschnitt 5, Globale Out-of-Scope-Punkte: „Keine vollständige T-SQL-Kompatibilität, keine SQL-Server-Systemkataloge oder Administrationsfunktionen, außer den Abfragen des Referenzclients, die das Clientprofil festlegt ([TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend)).“ und „Keine integrierte SQL-Engine oder persistente Datenbank; das mitgelieferte Simulations-Backend ([TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend)) erkennt nur abgegrenzte Anweisungsformen und speichert nichts.“
- [TDS-RB-02](../../../../spec/lastenheft.md#tds-rb-02--externer-client), Vorgabe: „Der verbindliche Client ist eine .NET-Anwendung mit einem vor Abnahme festgelegten SqlClient-Paket und Version; dessen tatsächliches Verbindungs- und Abfrageverhalten ist maßgeblich.“

**Nutzerentscheidung (2026-11-05, wörtlich):** „Ja, auch das Lastenheft entsprechend anpassen.“ (zur Frage, ob Schreiben im Testserver das Lastenheft ändert), davor: „Hürde 6 ja — 7, 9, 10 auch, das Schreiben muss gehen — ohne data profile“. Das Lastenheft ist deshalb in Fassung 0.7.0 vorbereitet (Commit `6dd6c33` auf dem Zweig `worktree-agent-a73a7bf5646d52362`): neu [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate) (Arbeitsspeicher-Bestand und Katalog für den **Testserver**, d-migrate als zweiter, nicht verbindlicher Prüfclient, Akzeptanzkriterien mit d-migrate-Läufen); Ausnahmeverweise in den Ausschlüssen von [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) und Abschnitt 5; Glossar; Historie. **Offen: die Sichtprüfung des Wortlauts durch den Nutzer vor dem Merge auf `main`.** Der Wortlaut sagt nicht, **wo** der Bestand lebt (Bibliothek oder Testserver); er sagt „Testserver“ als Verhalten des ausführbaren Prozesses, ohne die Bibliothek mit Datenhaltung zu belegen. Wählt die Entscheidung unten eine Datenhaltung in der Bibliothek, ist das Lastenheft ein zweites Mal zu ändern (Nutzersache, siehe Liefer-Punkt 2).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Code für Katalog, Bestand, Massenladen oder MERGE — [slice-katalog-und-ddl-zustand](slice-katalog-und-ddl-zustand.md), [slice-schreibpfad-bulk-load](slice-schreibpfad-bulk-load.md), [slice-schreibpfad-merge-output](slice-schreibpfad-merge-output.md); dieser Slice entscheidet und baut nicht. Spike-Code liegt im Scratchpad und nie im Repo.
- Das Schneiden der öffentlichen Fläche — [slice-oeffentliche-flaeche-verkleinern-und-belegen](slice-oeffentliche-flaeche-verkleinern-und-belegen.md) (Kriterium je Name aus der Nutzerentscheidung „nur die Klassen, die man wirklich dafür benötigt“); die Entscheidung hier sagt, **welche neuen Namen** eine Variante der Fläche hinzufügt, entscheidet aber nicht deren Schnitt.
- `data profile` (Aggregate, `CAST`, `TOP`, `GROUP BY`): ausgeschlossen durch [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) und nicht im Soll; die Entscheidung nimmt es nicht wieder auf.
- Dauerhafte Speicherung über den Prozess hinaus und Transaktionssemantik mit Rücknahme über mehrere Anweisungen: Out-of-Scope von [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate).
- Die Aufnahme von d-migrate oder JDBC in das Clientprofil von [ADR-0001](../../adr/0001-tds-version-und-clientprofil.md): das Lastenheft nennt d-migrate als nicht verbindlichen Prüfclient; ob das ein Profil-Eintrag ist, ist eine eigene Frage (Risiko unten).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit.

Liefer-Punkte (3):

- [ ] **Optionen mit Beleg:** Zu jeder der vier Fragen (§6) stehen zwei bis drei Optionen. Aussagen über Aufwand stützen sich auf die Sonde ([d-migrate-sonde-2026-11-05](../../../messungen/d-migrate-sonde-2026-11-05.md)) oder auf einen Spike im Scratchpad; Aufwand ohne Spike steht als Schätzung. Die Optionen nennen für jede Variante die neuen oder geänderten Namen der öffentlichen Fläche (SPEC-059, [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md)) und die Spec-Stellen, die sich ändern.
- [ ] **Entscheidung des Nutzers** zu den Fragen, festgehalten als neue ADR (nächste freie Nummer laut ADR-Index, Status `Accepted`, `Bezug` auf [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md), [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md), [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md), [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md), ohne `Supersedes` es sei denn, die Entscheidung überschreibt wörtlich; Index nachgezogen). Jede Aussage trägt eine Messzeile oder ist als Setzung des Nutzers gekennzeichnet. Fällt die Entscheidung auf Datenhaltung in der Bibliothek, ist die Änderung des Lastenhefts als Nutzerfrage gestellt und beantwortet, bevor die ADR `Accepted` wird.
- [ ] **Anschlussstellen** nachgezogen: Start-Trigger und Risiken der Folge-Slices [slice-katalog-und-ddl-zustand](slice-katalog-und-ddl-zustand.md), [slice-schreibpfad-bulk-load](slice-schreibpfad-bulk-load.md), [slice-schreibpfad-merge-output](slice-schreibpfad-merge-output.md), [slice-tls-d-migrate](slice-tls-d-migrate.md), [slice-abnahme-d-migrate](slice-abnahme-d-migrate.md); die Spec-Stellen oben sind je dem Folge-Slice zugeordnet, der sie schreibt (diese Slice ändert die Spezifikation nur dort, wo die Entscheidung selbst die Festlegung ist, zum Beispiel die Setzung in SPEC-035); die Antwort auf FLOAT (Hürde 12) steht fest.

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
| `docs/plan/adr/` — neue ADR <!-- d-check:ignore (geplant: neue Datei) --> | neu | Entscheidung samt Optionen, Messzeilen und Offen-Liste mit Trägern |
| `docs/plan/adr/README.md` | update | ADR-Index (`AGENTS.md` §5 Regel 2 und 3) |
| Slices der Welle (Folge-Slices) | update | Start-Trigger und Risiken an die Entscheidung anschließen |
| `spec/spezifikation.md` | nur soweit die Entscheidung die Festlegung ist | SPEC-035-Setzung; alles andere schreiben die Folge-Slices |

**Die vier Fragen (Optionen zum Ausarbeiten, keine Vorentscheidung):**

1. **Ort des Katalogs.** (a) Bibliothek: `SimulationBackend`/`SimCatalog` leitet `sys.*` aus der Beschreibung ab und wirkt für jeden Integrator; (b) Testserver: ein Custom-Backend (`BackendSource.Custom`) beantwortet die Katalogabfragen, die Bibliothek bleibt schmal (Vorschlag der Sonde, nicht gemessen); (c) geteilt: die Bibliothek stellt die Ableitung als wiederverwendbaren Baustein bereit, der Testserver verdrahtet sie. Beleg der Sonde: 26 bis 29 Anweisungen je `schema reverse`, mehr als zehn Spalten je Resultset (Hürde 6).
2. **Ort des veränderlichen Bestands.** (a) schreibbarer In-Memory-Speicher im Testserver (Custom-Backend, `onInsert`/`onUpdate`/`onDelete` bestehen als Weitergabe, SPEC-035); (b) in der Bibliothek, dann fällt die Setzung „Dateitabellen sind leer, Einfügen wird abgewiesen“ (SPEC-035/SPEC-036) für das Simulations-Backend, und das Lastenheft ist ein zweites Mal zu ändern (Persistenz-Ausschluss von [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend)); (c) Bibliothek bietet nur die Callback-Schnittstelle, der Testserver implementiert den Speicher.
3. **Massenladen (Paket 0x07).** Es ist Protokoll ([TDS-RB-03](../../../../spec/lastenheft.md#tds-rb-03--protokollstandard), [MS-TDS]) und gehört damit in die Bibliothek: neuer Pakettyp im Dispatch, Dekodierung von COLMETADATA und ROW, Weiterleitung an das Backend. Zu entscheiden: die Form der Weiterleitung (eine Ausführungsart „Massenladen“ im Backend-Vertrag [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) oder Abbildung auf Einfügen, Form W1 mit einer Zeile je Aufruf) und der Umgang mit `SET FMTONLY ON`. Berührt die öffentliche Fläche, sobald der Backend-Vertrag wächst.
4. **Reihenfolge.** Vorschlag zum Prüfen: Katalog und Bestand (Frage 1, 2) vor Massenladen und MERGE; TLS unabhängig. Die Kanten stehen in [welle-testserver-manuell](../welle-testserver-manuell.md) §5.

Dazu kommt **FLOAT (Hürde 12)**: Die Sonde sah ihn nur im Aggregat von `data profile` (nicht im Soll); gebraucht wird er, wenn eine Soll-Funktion eine Spalte dieses Typs trägt (zum Beispiel `schema reverse` einer Tabelle mit `float`-Spalte, `data export`/`import` einer solchen Spalte) — nicht gemessen. Die Entscheidung legt fest, ob `SqlType` um FLOAT wächst.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): Die Welle [welle-testserver-manuell](../welle-testserver-manuell.md) läuft, der Nutzer hat den Wortlaut des Lastenhefts 0.7.0 gesichtet (Sichtprüfung vor dem Merge auf `main`; bei Änderungswünschen wird der Commit `6dd6c33` überarbeitet) und ein Verantwortlicher ist eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte; Schnittvorschlag: Katalog und Bestand als eine Entscheidung, Massenladen als zweite.
- `in-progress` → `open` (blockiert): die Entscheidung des Nutzers steht aus, oder der Nutzer ändert den Wortlaut von [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate) — der Slice bleibt in `open/`, bis beides vorliegt.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln.

Die DoD ist vollständig, die ADR steht im Index auf `Accepted`, `make gates` ist grün, ein Review-Report liegt vor, und die Closure-Notiz ordnet jeden offenen Punkt der ADR einem Träger zu und trägt den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — jedes Risiko bekommt genau einen Ausgang.

- Das Lastenheft 0.7.0 liegt vor der Sichtprüfung nicht auf `main`; die Entscheidung stützt sich auf einen Wortlaut, der sich ändern kann. Der Wortlaut legt den Ort nicht fest, die Wahl der Bibliothek als Ort der Datenhaltung braucht eine zweite Änderung. — **Ausgang:** offen bis zur Closure.
- Der Backend-Vertrag ([ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)) wächst um das Massenladen; die öffentliche Fläche wird dadurch größer, während [slice-oeffentliche-flaeche-verkleinern-und-belegen](slice-oeffentliche-flaeche-verkleinern-und-belegen.md) sie verkleinern soll. Die Kante und ihre Reihenfolge benennt die Entscheidung. — **Ausgang:** offen bis zur Closure.
- d-migrate ist weder im Clientprofil von [ADR-0001](../../adr/0001-tds-version-und-clientprofil.md) noch eine Zusage für eine Version; das Lastenheft sagt „Version und Treiber legt die Spezifikation fest“, die Spezifikation führt das noch nicht. Ob das eine ADR braucht (siehe `BEO-TDS/festlegung-gilt-nur-fuer-das-clientprofil`), ist Teil der Entscheidung. — **Ausgang:** offen bis zur Closure.
- SPEC-036 nennt die Liste der Formen „abschließend“ — eine Lesart des Lastenhefts, vom Nutzer nicht bestätigt (`BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt`); die Katalogabfragen und MERGE weiten sie. — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (Namensliste der Eröffnung, Texte nicht einzeln gelesen). Treffer: [festlegung-gilt-nur-fuer-das-clientprofil](../observations/BEO-TDS/festlegung-gilt-nur-fuer-das-clientprofil/observation.md) (1×); [lesart-einer-nutzerentscheidung-unbestaetigt](../observations/BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt/observation.md); [oeffentliche-flaeche-der-telemetrie-ohne-entscheidung](../observations/BEO-TDS/oeffentliche-flaeche-der-telemetrie-ohne-entscheidung/observation.md) (berührt über die Fläche). Zählerstände liest der Implementer bei Start.

Alle berührten Sub-Areas GF.
