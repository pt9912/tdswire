# Slice slice-adr-testserver-bestand-und-katalog: Entscheidung: Callback-Lösung für Schreiben, Strukturänderung und Massenladen mit Katalog-Anbieter (Spike), Einschaltung, FLOAT, öffentliche Fläche, PRELOGIN-Wert, „abschließend“ und Reihenfolge

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

**Ziel:** Die Lösung, mit der die Bibliothek Schreiben, Strukturänderung und Massenladen an den Integrator weitergibt, ist an einem Wegwerf-Prototyp mit d-migrate gegen den Testserver geprüft und liegt als Entscheidung des Nutzers in einer neuen ADR (nächste freie Nummer laut ADR-Index) vor; dazu die übrigen offenen Punkte: Einschaltung, FLOAT, öffentliche Fläche (Kante zu [slice-oeffentliche-flaeche-verkleinern-und-belegen](slice-oeffentliche-flaeche-verkleinern-und-belegen.md)), PRELOGIN-Wert und Clientprofil, „abschließend“ in SPEC-036 und die Reihenfolge der Folge-Slices.

**Quelle und Vorbehalt.** Auslöser ist die Sonde vom 2026-11-05 ([d-migrate-sonde-2026-11-05](../../../messungen/d-migrate-sonde-2026-11-05.md)). Ihr Vorbehalt gilt hier: alles, was dort „lief“, lief gegen erfundene Katalogantworten; belegt ist, was d-migrate sendet und dass Treiber und d-migrate die TDS-Antworten der Bibliothek verarbeiten. Aufwandsgrößen (S/M/L) sind Schätzungen der Sonde, nicht gemessen.

**Entschieden (Nutzer, 2026-11-05, wörtlich):** „Der Test-Server ist zum Testen der API da. Die Funktionalität ist in der Lib.“ und „Die konkreten Tabellen zum Lesen/Schreiben werden im Testserver angelegt und der API mitgeteilt.“ Daraus folgt, und das ist nicht mehr Gegenstand der ADR, sondern ihr Kontext:

- **Bibliothek = Funktionalität, speichert nichts:** Der Katalog (`sys.*`, `INFORMATION_SCHEMA`) wird aus den mitgeteilten Tabellen (`SimTable`/`SimCatalog` über `BackendSource.Simulation`) beantwortet; die Bibliothek versteht die Anweisungsformen (SET, `SELECT 1`, `WHERE 1 = 0`, `INSERT`, `INSERT BULK`/BULK_LOAD 0x07, `MERGE ... OUTPUT $action`, DDL) und gibt Schreiben und Strukturänderungen an den Integrator weiter.
- **Testserver = Integrator:** legt die konkreten Tabellen an (Beschreibungsdatei oder Code), hält die Zeilen im Arbeitsspeicher, teilt der API Tabellen mit Zeilenquelle und Callbacks mit und implementiert die Callbacks (Schreiben, Strukturänderung, Massenladen).

**Lastenheft: von der Nutzerentscheidung getragen, Wortlaut vom Nutzer am 2026-11-05 freigegeben.** Stellen vor der Änderung (Fassung 0.6.1, wörtlich), die wahr bleiben:

- [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), Beschreibung: „Es wertet kein SQL aus und speichert keine Daten: Was gespeichert wird, entscheidet der Integrator.“ Out-of-Scope: „…Persistenz, Transaktionssemantik über die Weitergabe an den Integrator hinaus und SQL-Server-Systemkataloge, soweit sie der Referenzclient nach Clientprofil nicht selbst abfragt.“
- Abschnitt 5: „Keine integrierte SQL-Engine oder persistente Datenbank; das mitgelieferte Simulations-Backend ([TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend)) erkennt nur abgegrenzte Anweisungsformen und speichert nichts.“ — unverändert.
- [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), Out-of-Scope „Persistenzschicht“ — unverändert.

Was sich ändert (Fassung 0.7.0 in Commit `090092d` auf dem Zweig `worktree-agent-a73a7bf5646d52362`): [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) erhält die Fähigkeiten der Bibliothek (Katalogabfragen für mitgeteilte Tabellen, Weitergabe von Strukturänderung und Massenladen, Akzeptanzkriterium im Happy Path); der Ausschluss der Systemkataloge nimmt diese Abfragen aus (Abschnitt 5 ebenso). Neu ist [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate) für den Testserver als Integrator mit Bestand im Arbeitsspeicher und d-migrate als Prüfclient. **Die Verteilung** (Fähigkeiten der Bibliothek in [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), Zusage des Testservers in [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate)) ist eine Wahl des Planners: Die Funktionen gehören zum Simulations-Backend, und der Testserver ist ein eigener, nicht verbindlicher Gegenstand. **Der Wortlaut ist vom Nutzer am 2026-11-05 freigegeben („Ja, Wortlaut passt, Weg A aufnehmen.“); `090092d` darf auf `main`.**

**Richtung (Nutzer, 2026-11-05, auf die Frage Callbacks an `SimTable` oder Erweiterung von `QueryBackend`: „Deine Empfehlung“).** Gewählt ist damit die Empfehlung des Planners, **eine Setzung, vom Nutzer als Richtung gewählt; Einzelheiten unter Vorbehalt des Spikes (Liefer-Punkt 1), nicht gemessen:**

- `QueryBackend` bleibt unverändert (dünner, allgemeiner Port); die Funktion liegt im Simulations-Backend.
- `INSERT`, `UPDATE`, `DELETE` laufen über die bestehenden Callbacks von `SimTable` (`onInsert`, `onUpdate`, `onDelete`, SPEC-035).
- `MERGE ... OUTPUT $action` zerlegt die Bibliothek selbst in Lesen über die Zeilenquelle plus die bestehenden Einfüge- und Änderungs-Callbacks. **Prüfpunkt, nicht gemessen:** dass die Zeilenquelle nach Schlüssel lesen kann (`SimQuery.filter` ist laut SPEC-035 ein Hinweis zum Vorfiltern, kein Ersatz).
- **Massenladen, Weg A (Nutzer, 2026-11-05: „Weg A aufnehmen“):** Die Bibliothek decodiert die Pakete vom Typ 0x07 (Spaltenbeschreibung, Zeilen), ordnet Spalten zu, wandelt Typen, verbraucht mit Gegendruck nur so schnell, wie der bestehende Einfüge-Callback von `SimTable` abnimmt, zählt Zeilen und meldet am Ende die Zahl. Bei einem Fehler in einer Zeile endet das Massenladen mit dem Fehler des Callbacks; vorher eingefügte Zeilen bleiben (keine Transaktionssemantik, die Zusage nennt es). Ein Stapel- oder Strom-Callback kommt erst nach Messung. Protokollteil: Sitzungszustand „Massenladen erwartet“ nach der Anweisung `INSERT BULK`, davor `SET FMTONLY` und die `sys.columns`-Abfrage laut Sonde.
- Strukturänderungen (DDL) brauchen einen Strukturänderungs-Callback auf Katalog-Ebene und einen **Katalog-Anbieter statt eines festen `SimCatalog`**: Die Bibliothek erfragt bei jeder Katalogabfrage den aktuellen Katalog und hält keinen Zustand. Voraussichtlich **zwei neue öffentliche Namen** (Setzung, nicht gemessen).
- Mehrere Callbacks einer Anweisung sind **keine Transaktion**; das deckt „Transaktionssemantik über die Weitergabe an den Integrator hinaus“ im Out-of-Scope von [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), und die Zusage nennt es.

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

- [ ] **Spike (Wegwerf, nicht gemergt, Code im Scratchpad):** Ein kleiner Prototyp der Callback-Lösung läuft mit d-migrate 1.12.0 gegen den Testserver und belegt die Prüfpunkte: (a) `MERGE`-Zerlegung in Lesen über die Zeilenquelle plus Einfüge-/Änderungs-Callbacks, mit der Prüfung, ob die Zeilenquelle nach Schlüssel lesen kann; (b) Massenladen nach Weg A mit vier Messpunkten: (i) Zeilenzahl und Inhalt nach `data import` und `data transfer` im Standardpfad stimmen (Export danach); (ii) Fehler mitten im Strom: was d-migrate erhält (Fehlernummer; eine Schlüsselverletzung 2627 nur, wenn gemessen); (iii) Typen, die d-migrate sendet (die Sonde sah nur int, nvarchar, date; Dezimal u. a. nicht gemessen); (iv) beschränkter Speicher bei großem Import (Heap-Grenze, Gegendruck); offen und nicht gemessen: ob d-migrate das Massenladen abschalten kann (kein Ausweg im Plan); (c) Katalog-Anbieter und Strukturänderungs-Callback: ein `schema reverse` nach `CREATE TABLE`/`ALTER TABLE` zeigt die geänderte Struktur; (d) Verhalten bei mehreren Callbacks einer Anweisung (keine Transaktion; Fehler in der Mitte). Messwerte und Ausgabe (Rechner, Last, Image-Digest) stehen als Tabelle im Slice und in der ADR.
- [ ] **ADR mit Entscheidung des Nutzers:** neue ADR (nächste freie Nummer laut ADR-Index; `Accepted` erst nach dem Entscheid des Nutzers, `Bezug` auf [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md), [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md), [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md), [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md), ohne `Supersedes` es sei denn, die Entscheidung überschreibt wörtlich; Index nachgezogen) zur Callback-Lösung (Spike-Befunde), zur Einschaltung, zu FLOAT, zur öffentlichen Fläche mit der Liste jedes neuen öffentlichen Namens, zum PRELOGIN-Wert und zu „abschließend“; jede Aussage trägt eine Messzeile oder ist als Setzung gekennzeichnet; die Zusage nennt „keine Transaktion“. Der Wortlaut von [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend)/[TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate) ist vom Nutzer am 2026-11-05 freigegeben.
- [ ] **Liste der Spec-Änderungen** und Anschlussstellen: je Folge-Slice ([slice-katalog-und-ddl-zustand](slice-katalog-und-ddl-zustand.md), [slice-schreibpfad-bulk-load](slice-schreibpfad-bulk-load.md), [slice-schreibpfad-merge-output](slice-schreibpfad-merge-output.md), [slice-tls-d-migrate](../next/slice-tls-d-migrate.md), [slice-abnahme-d-migrate](slice-abnahme-d-migrate.md)) die Änderungen an SPEC-035, SPEC-036, SPEC-059 und SPEC-040, die er schreibt, sowie Start-Trigger und Risiken nachgezogen. Dieser Slice ändert die Spezifikation selbst nur dort, wo die Entscheidung die Festlegung ist (PRELOGIN-Wert).

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
| Scratchpad — Wegwerf-Prototyp der Callback-Lösung <!-- d-check:ignore (nicht im Repo) --> | neu, nicht gemergt | Spike für die Prüfpunkte; Code liegt nie im Repo |
| Slices der Welle (Folge-Slices) | update | Start-Trigger und Risiken an die Entscheidung anschließen |
| `spec/spezifikation.md` | nur soweit die Entscheidung die Festlegung ist | PRELOGIN-Wert, Einschaltung; alles andere schreiben die Folge-Slices |

**Entschieden (Richtung, Setzung des Planners, vom Nutzer gewählt; unter Vorbehalt des Spikes):** Form der Weitergabe (bestehende Callbacks, `QueryBackend` unverändert), Katalog nach Strukturänderung (Katalog-Anbieter, Strukturänderungs-Callback), Massenladen als Abbildung auf den Einfüge-Callback. Das ist keine ADR-Entscheidung, bis der Spike es trägt und der Nutzer die ADR annimmt.

**Offene Fragen (Optionen zum Ausarbeiten, keine Vorentscheidung):**

1. **Trägt der Spike die Richtung?** Prüfpunkte: Zeilenquelle liest nach Schlüssel (MERGE-Zerlegung); Massenladen als Folge von `onInsert` genügt (Durchsatz, Fehler in der Mitte); Katalog-Anbieter und Strukturänderungs-Callback; Verhalten ohne Transaktion. Fällt ein Prüfpunkt, entsteht die Alternative (eigener Massen- oder Konflikt-Callback, oder Erweiterung von `QueryBackend`, [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)) in der ADR.
2. **FLOAT (Hürde 12).** Die Sonde sah ihn nur im Aggregat von `data profile` (nicht im Soll); gebraucht wird er, wenn eine Soll-Funktion eine Spalte dieses Typs trägt — nicht gemessen. Aufnehmen in `SqlType` (öffentlich) oder nicht.
3. **Einschaltung und Konfiguration.** Ob die neuen Fähigkeiten ohne Schalter gelten (sie ändern nichts, solange der Integrator keine Callbacks setzt: die Standard-Setzung „Einfügen wird abgewiesen“ bliebe) oder einen Schalter brauchen. Nicht gemessen.
4. **Öffentliche Fläche.** Voraussichtlich zwei neue Namen (Strukturänderungs-Callback, Katalog-Anbieter), jeder mit Begründung nach dem Kriterium von [slice-oeffentliche-flaeche-verkleinern-und-belegen](slice-oeffentliche-flaeche-verkleinern-und-belegen.md); Katalog-Ableitung und Planer `internal`. Reihenfolge zu diesem Slice (Schnitt vorher oder nachher; dessen Plan sagt: vor einem Release). Zu klären, wie der Anbieter `SimCatalog` (öffentlich nach [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md)) ersetzt oder ergänzt.
5. **PRELOGIN-Wert und Clientprofil.** Wert (Setzung) für [slice-prelogin-serverversion](../done/slice-prelogin-serverversion.md); ob d-migrate als Prüfclient eine ADR zum Clientprofil von [ADR-0001](../../adr/0001-tds-version-und-clientprofil.md) braucht.
6. **„Abschließend“ in SPEC-036.** Die Liste der Formen ist laut SPEC-036 eine unbestätigte Lesart; Katalogabfragen, MERGE und Massenladen weiten sie. Bestätigen oder die Lesart schärfen.
7. **Reihenfolge der Folge-Slices.** Vorschlag zum Prüfen: Callbacks und Katalog vor Massenladen und MERGE; TLS unabhängig. Die Kanten stehen in [welle-testserver-manuell](../welle-testserver-manuell.md) §5.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): Die Welle [welle-testserver-manuell](../welle-testserver-manuell.md) läuft, der Wortlaut des Lastenhefts 0.7.0 ist vom Nutzer am 2026-11-05 freigegeben (erfüllt) und ein Verantwortlicher ist eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte; Schnittvorschlag: Weitergabe und Katalog als eine Entscheidung, Massenladen und Fläche als zweite.
- `in-progress` → `open` (blockiert): die Entscheidung des Nutzers steht aus, oder der Spike verlangt eine Änderung am freigegebenen Wortlaut von [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend)/[TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate) (neue Nutzerentscheidung) — der Slice bleibt in `open/`, bis beides vorliegt.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln.

Die DoD ist vollständig, die ADR steht im Index auf `Accepted`, `make gates` ist grün, ein Review-Report liegt vor, und die Closure-Notiz ordnet jeden offenen Punkt der ADR einem Träger zu und trägt den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — jedes Risiko bekommt genau einen Ausgang.

- Der Wortlaut des Lastenhefts 0.7.0 ist freigegeben (2026-11-05); eine spätere Änderung wäre eine neue Nutzerentscheidung, besonders die Verteilung der Zusagen auf [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) und [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate). — **Ausgang:** offen bis zur Closure.
- Der Backend-Vertrag ([ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)) oder die Callbacks von `SimTable` wachsen für Massenladen und Strukturänderung; die öffentliche Fläche wird größer, während [slice-oeffentliche-flaeche-verkleinern-und-belegen](slice-oeffentliche-flaeche-verkleinern-und-belegen.md) sie verkleinern soll. Die Kante und ihre Reihenfolge benennt die Entscheidung. — **Ausgang:** offen bis zur Closure.
- d-migrate ist weder im Clientprofil von [ADR-0001](../../adr/0001-tds-version-und-clientprofil.md) noch eine Zusage für eine Version; das Lastenheft sagt „Version und Treiber legt die Spezifikation fest“, die Spezifikation führt das noch nicht (`BEO-TDS/festlegung-gilt-nur-fuer-das-clientprofil`). — **Ausgang:** offen bis zur Closure.
- SPEC-036 nennt die Liste der Formen „abschließend“ — eine Lesart des Lastenhefts, vom Nutzer nicht bestätigt (`BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt`); Katalogabfragen, MERGE und Massenladen weiten sie. — **Ausgang:** offen bis zur Closure.
- Die MERGE-Zerlegung setzt voraus, dass die Zeilenquelle nach Schlüssel lesen kann; das ist nicht gemessen. Trägt es nicht, fällt die Richtung „`QueryBackend` unverändert“ für diese Form. — **Ausgang:** offen bis zur Closure.
- Mehrere Callbacks einer Anweisung sind keine Transaktion: Ein Fehler in der Mitte hinterlässt einen Teilbestand beim Integrator. Gedeckt durch den Out-of-Scope „Transaktionssemantik über die Weitergabe hinaus“ von [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), aber für Client-Verhalten nicht gemessen. — **Ausgang:** offen bis zur Closure.

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
