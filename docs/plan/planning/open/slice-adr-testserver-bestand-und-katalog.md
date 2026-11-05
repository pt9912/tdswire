# Slice slice-adr-testserver-bestand-und-katalog: Entscheidung: Weitergabe von Schreiben, Strukturänderung und Massenladen an den Integrator, Katalog nach Änderungen, FLOAT, Einschaltung, öffentliche Fläche, PRELOGIN-Wert, „abschließend“ und Reihenfolge

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

**Berührte Spec-Stellen:** SPEC-035 (Callbacks `onInsert`/`onUpdate`/`onDelete`, Setzung „Dateitabellen sind leer, Einfügen wird abgewiesen“) · SPEC-036 (Formen, Katalog, MERGE, „abschließend“) · SPEC-059 (öffentliche Fläche) · SPEC-040 (Läufe gegen den Testserver) · [TDS-FA-11.a](../../../../spec/spezifikation.md#tds-fa-11a--simulations-backend) · [TDS-FA-08.a](../../../../spec/spezifikation.md#tds-fa-08a--backend-vertrag) (Backend-Vertrag) · Tabelle „Login-Antwort des Servers (Festlegung)“ (PRELOGIN-Version) · Protokollteil Pakettypen (Katalog der Pakete, 0x07 nicht enthalten)

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-05.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert; jeder Ausschluss mit Begründung.

**Ziel:** Die Architektur-Entscheidungen, die nach der Ortsentscheidung des Nutzers offen sind, liegen als Entscheidung des Nutzers in einer neuen ADR (nächste freie Nummer laut ADR-Index) vor: (1) die Form der Weitergabe von Schreiben, Strukturänderung und Massenladen an den Integrator (Callbacks an `SimTable` oder Erweiterung des Backend-Vertrags `QueryBackend`), (2) wie sich der Katalog nach einer Strukturänderung ändert (die Bibliothek liest die Tabellenliste vom Integrator neu, oder der Integrator meldet die Änderung), (3) das Massenladen (Paket 0x07), (4) ob der Typ FLOAT aufgenommen wird, (5) Einschaltung und Konfiguration, (6) die Auswirkung auf die öffentliche Fläche (jeder neue öffentliche Name; Kante und Reihenfolge zu [slice-oeffentliche-flaeche-verkleinern-und-belegen](slice-oeffentliche-flaeche-verkleinern-und-belegen.md)), (7) der Wert der PRELOGIN-Version und die Frage nach dem Clientprofil, (8) der Umgang mit „abschließend“ in SPEC-036, (9) die Reihenfolge der Folge-Slices.

**Quelle und Vorbehalt.** Auslöser ist die Sonde vom 2026-11-05 ([d-migrate-sonde-2026-11-05](../../../messungen/d-migrate-sonde-2026-11-05.md)). Ihr Vorbehalt gilt hier: alles, was dort „lief“, lief gegen erfundene Katalogantworten; belegt ist, was d-migrate sendet und dass Treiber und d-migrate die TDS-Antworten der Bibliothek verarbeiten. Aufwandsgrößen (S/M/L) sind Schätzungen der Sonde, nicht gemessen.

**Entschieden (Nutzer, 2026-11-05, wörtlich):** „Der Test-Server ist zum Testen der API da. Die Funktionalität ist in der Lib.“ und „Die konkreten Tabellen zum Lesen/Schreiben werden im Testserver angelegt und der API mitgeteilt.“ Daraus folgt, und das ist nicht mehr Gegenstand der ADR, sondern ihr Kontext:

- **Bibliothek = Funktionalität, speichert nichts:** Der Katalog (`sys.*`, `INFORMATION_SCHEMA`) wird aus den mitgeteilten Tabellen (`SimTable`/`SimCatalog` über `BackendSource.Simulation`) beantwortet; die Bibliothek versteht die Anweisungsformen (SET, `SELECT 1`, `WHERE 1 = 0`, `INSERT`, `INSERT BULK`/BULK_LOAD 0x07, `MERGE ... OUTPUT $action`, DDL) und gibt Schreiben und Strukturänderungen an den Integrator weiter.
- **Testserver = Integrator:** legt die konkreten Tabellen an (Beschreibungsdatei oder Code), hält die Zeilen im Arbeitsspeicher, teilt der API Tabellen mit Zeilenquelle und Callbacks mit und implementiert die Callbacks (Schreiben, Strukturänderung, Massenladen).

**Lastenheft: von der Nutzerentscheidung getragen, Wortlaut noch nicht gesichtet.** Stellen vor der Änderung (Fassung 0.6.1, wörtlich), die wahr bleiben:

- [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), Beschreibung: „Es wertet kein SQL aus und speichert keine Daten: Was gespeichert wird, entscheidet der Integrator.“ Out-of-Scope: „…Persistenz, Transaktionssemantik über die Weitergabe an den Integrator hinaus und SQL-Server-Systemkataloge, soweit sie der Referenzclient nach Clientprofil nicht selbst abfragt.“
- Abschnitt 5: „Keine integrierte SQL-Engine oder persistente Datenbank; das mitgelieferte Simulations-Backend ([TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend)) erkennt nur abgegrenzte Anweisungsformen und speichert nichts.“ — unverändert.
- [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), Out-of-Scope „Persistenzschicht“ — unverändert.

Was sich ändert (Fassung 0.7.0 in Commit `090092d` auf dem Zweig `worktree-agent-a73a7bf5646d52362`): [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) erhält die Fähigkeiten der Bibliothek (Katalogabfragen für mitgeteilte Tabellen, Weitergabe von Strukturänderung und Massenladen, Akzeptanzkriterium im Happy Path); der Ausschluss der Systemkataloge nimmt diese Abfragen aus (Abschnitt 5 ebenso). Neu ist [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate) für den Testserver als Integrator mit Bestand im Arbeitsspeicher und d-migrate als Prüfclient. **Die Verteilung** (Fähigkeiten der Bibliothek in [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), Zusage des Testservers in [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate)) ist eine Wahl des Planners: Die Funktionen gehören zum Simulations-Backend, und der Testserver ist ein eigener, nicht verbindlicher Gegenstand. **Offen: die Sichtprüfung des Wortlauts durch den Nutzer vor dem Merge auf `main`.**

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Code für Katalog, Weitergabe, Massenladen, MERGE oder Testserver-Bestand — [slice-katalog-und-ddl-zustand](slice-katalog-und-ddl-zustand.md), [slice-schreibpfad-bulk-load](slice-schreibpfad-bulk-load.md), [slice-schreibpfad-merge-output](slice-schreibpfad-merge-output.md); dieser Slice entscheidet und baut nicht. Spike-Code liegt im Scratchpad und nie im Repo.
- Das Schneiden der öffentlichen Fläche selbst — [slice-oeffentliche-flaeche-verkleinern-und-belegen](slice-oeffentliche-flaeche-verkleinern-und-belegen.md) (Kriterium je Name aus der Nutzerentscheidung „nur die Klassen, die man wirklich dafür benötigt“); die Entscheidung hier sagt, **welche neuen Namen** die Weitergabe der Fläche hinzufügt und in welcher Reihenfolge, nicht deren Schnitt.
- Die Verteilung von Funktion (Bibliothek) und Bestand (Testserver): vom Nutzer entschieden, hier nicht wieder offen.
- `data profile` (Aggregate, `CAST`, `TOP`, `GROUP BY`): ausgeschlossen durch [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) und nicht im Soll.
- Dauerhafte Speicherung über den Prozess hinaus und Transaktionssemantik mit Rücknahme über mehrere Anweisungen: Out-of-Scope von [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit.

Liefer-Punkte (3):

- [ ] **Optionen mit Beleg:** Zu jeder Frage (Plan unten) stehen zwei bis drei Optionen. Aussagen über Aufwand stützen sich auf die Sonde ([d-migrate-sonde-2026-11-05](../../../messungen/d-migrate-sonde-2026-11-05.md)) oder auf einen Spike im Scratchpad; Aufwand ohne Spike steht als Schätzung. Die Optionen nennen je Variante die neuen oder geänderten Namen der öffentlichen Fläche (SPEC-059, [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md)) und die Spec-Stellen, die sich ändern.
- [ ] **Entscheidung des Nutzers** zu den Fragen, festgehalten als neue ADR (nächste freie Nummer laut ADR-Index, Status `Accepted`, `Bezug` auf [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md), [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md), [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md), [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md), ohne `Supersedes` es sei denn, die Entscheidung überschreibt wörtlich; Index nachgezogen). Jede Aussage trägt eine Messzeile oder ist als Setzung des Nutzers gekennzeichnet. Der Wortlaut von [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend)/[TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate) ist vom Nutzer gesichtet oder nach seinen Änderungen angepasst, bevor die ADR `Accepted` wird.
- [ ] **Anschlussstellen** nachgezogen: Start-Trigger und Risiken der Folge-Slices [slice-katalog-und-ddl-zustand](slice-katalog-und-ddl-zustand.md), [slice-schreibpfad-bulk-load](slice-schreibpfad-bulk-load.md), [slice-schreibpfad-merge-output](slice-schreibpfad-merge-output.md), [slice-tls-d-migrate](slice-tls-d-migrate.md), [slice-abnahme-d-migrate](slice-abnahme-d-migrate.md); jede Spec-Stelle oben ist dem Folge-Slice zugeordnet, der sie schreibt (dieser Slice ändert die Spezifikation nur dort, wo die Entscheidung selbst die Festlegung ist, zum Beispiel der PRELOGIN-Wert).

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
| `spec/spezifikation.md` | nur soweit die Entscheidung die Festlegung ist | PRELOGIN-Wert, Einschaltung; alles andere schreiben die Folge-Slices |

**Die Fragen (Optionen zum Ausarbeiten, keine Vorentscheidung):**

1. **Form der Weitergabe.** (a) Callbacks an `SimTable` (bestehen für Einfügen, Ändern, Löschen: `onInsert`, `onUpdate`, `onDelete`, SPEC-035; würden um Massenladen, Konflikt-Einfügen und Strukturänderung wachsen); (b) Erweiterung des Backend-Vertrags `QueryBackend` ([ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)) um Ausführungsarten; (c) Abbildung auf die vorhandenen Callbacks (zum Beispiel Massenladen als Folge von `onInsert`). Jede Variante nennt ihre neuen öffentlichen Namen.
2. **Katalog nach einer Strukturänderung.** (a) Die Bibliothek liest die Tabellenliste bei jeder Katalogabfrage vom Integrator neu (zum Beispiel über einen Anbieter statt einer festen Liste: `SimCatalog` ist heute fest, [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md)); (b) der Integrator meldet die Änderung (Rückruf an die Bibliothek). Zu klären, wie eine DDL-Anweisung (`CREATE TABLE`, `ALTER TABLE ... ADD`) an den Integrator geht und was bei einem Fehler des Integrators mit dem Katalog geschieht.
3. **Massenladen (Paket 0x07).** Protokoll ([TDS-RB-03](../../../../spec/lastenheft.md#tds-rb-03--protokollstandard), [MS-TDS]), gehört in die Bibliothek: neuer Pakettyp im Dispatch, Dekodierung von COLMETADATA und ROW; Weitergabe nach Frage 1; Umgang mit `SET FMTONLY ON` und der Spaltenprobe vor dem Paket.
4. **FLOAT (Hürde 12).** Die Sonde sah ihn nur im Aggregat von `data profile` (nicht im Soll); gebraucht wird er, wenn eine Soll-Funktion eine Spalte dieses Typs trägt — nicht gemessen. Aufnehmen in `SqlType` (öffentlich) oder nicht.
5. **Einschaltung und Konfiguration.** Ob die neuen Fähigkeiten ohne Schalter gelten (sie ändern nichts, solange der Integrator keine Callbacks setzt: die Standard-Setzung „Einfügen wird abgewiesen“ bliebe) oder einen Schalter brauchen. Nicht gemessen.
6. **Öffentliche Fläche.** Jeder neue öffentliche Name aus 1 bis 5 mit Begründung nach dem Kriterium von [slice-oeffentliche-flaeche-verkleinern-und-belegen](slice-oeffentliche-flaeche-verkleinern-und-belegen.md); Katalog-Ableitung und Planer `internal`. Reihenfolge zu diesem Slice (Schnitt vorher oder nachher; dessen Plan sagt: vor einem Release).
7. **PRELOGIN-Wert und Clientprofil.** Wert (Setzung) für [slice-prelogin-serverversion](slice-prelogin-serverversion.md); ob d-migrate als Prüfclient eine ADR zum Clientprofil von [ADR-0001](../../adr/0001-tds-version-und-clientprofil.md) braucht.
8. **„Abschließend“ in SPEC-036.** Die Liste der Formen ist laut SPEC-036 eine unbestätigte Lesart; Katalogabfragen, MERGE und Massenladen weiten sie. Bestätigen oder die Lesart schärfen.
9. **Reihenfolge der Folge-Slices.** Vorschlag zum Prüfen: Weitergabe und Katalog vor Massenladen und MERGE; TLS unabhängig. Die Kanten stehen in [welle-testserver-manuell](../welle-testserver-manuell.md) §5.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): Die Welle [welle-testserver-manuell](../welle-testserver-manuell.md) läuft, der Nutzer hat den Wortlaut des Lastenhefts 0.7.0 gesichtet (Sichtprüfung vor dem Merge auf `main`; bei Änderungswünschen wird `090092d` überarbeitet) und ein Verantwortlicher ist eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte; Schnittvorschlag: Weitergabe und Katalog als eine Entscheidung, Massenladen und Fläche als zweite.
- `in-progress` → `open` (blockiert): die Entscheidung des Nutzers steht aus, oder der Nutzer ändert den Wortlaut von [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend)/[TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate) — der Slice bleibt in `open/`, bis beides vorliegt.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln.

Die DoD ist vollständig, die ADR steht im Index auf `Accepted`, `make gates` ist grün, ein Review-Report liegt vor, und die Closure-Notiz ordnet jeden offenen Punkt der ADR einem Träger zu und trägt den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — jedes Risiko bekommt genau einen Ausgang.

- Das Lastenheft 0.7.0 liegt vor der Sichtprüfung nicht auf `main`; die Entscheidung stützt sich auf einen Wortlaut, der sich ändern kann, besonders die Verteilung der Zusagen auf [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) und [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate). — **Ausgang:** offen bis zur Closure.
- Der Backend-Vertrag ([ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)) oder die Callbacks von `SimTable` wachsen für Massenladen und Strukturänderung; die öffentliche Fläche wird größer, während [slice-oeffentliche-flaeche-verkleinern-und-belegen](slice-oeffentliche-flaeche-verkleinern-und-belegen.md) sie verkleinern soll. Die Kante und ihre Reihenfolge benennt die Entscheidung. — **Ausgang:** offen bis zur Closure.
- d-migrate ist weder im Clientprofil von [ADR-0001](../../adr/0001-tds-version-und-clientprofil.md) noch eine Zusage für eine Version; das Lastenheft sagt „Version und Treiber legt die Spezifikation fest“, die Spezifikation führt das noch nicht (`BEO-TDS/festlegung-gilt-nur-fuer-das-clientprofil`). — **Ausgang:** offen bis zur Closure.
- SPEC-036 nennt die Liste der Formen „abschließend“ — eine Lesart des Lastenhefts, vom Nutzer nicht bestätigt (`BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt`); Katalogabfragen, MERGE und Massenladen weiten sie. — **Ausgang:** offen bis zur Closure.

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
