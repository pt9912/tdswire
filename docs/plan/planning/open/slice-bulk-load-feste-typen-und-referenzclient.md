# Slice slice-bulk-load-feste-typen-und-referenzclient: Der Testserver nimmt das Massenladen des Referenzclients (`SqlBulkCopy`) an: feste Typen der Nutzlast lesen, den Katalog-Batch bedienen, im Referenztest belegen

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-testserver-manuell

**Bezug:** [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-RB-03](../../../../spec/lastenheft.md#tds-rb-03--protokollstandard), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md)

**Berührte Spec-Stellen:** SPEC-036 (Pakettypen: gelesene Typen der Nutzlast des Massenladens, Form der Anfragen des Katalogs) · SPEC-035 (Überführung der Werte fester Typen) · SPEC-040 (Referenztest mit `SqlBulkCopy`) · SPEC-059 (`make flaeche` bleibt grün; der Slice fügt keinen öffentlichen Namen hinzu, sonst Rückführung).

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-07.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Der Testserver nimmt das Massenladen von `SqlBulkCopy` (`Microsoft.Data.SqlClient`, der Referenzclient) an, wie es der echte SQL Server tut (Zielbild: der Server ist die Referenz). Heute ist es nicht so: [slice-schreibpfad-bulk-load](../in-progress/slice-schreibpfad-bulk-load.md) bildet die **Antworten** nach der Messung M-8 bis M-12 ab, liest aber die **Anfrage** des Referenzclients nicht (Verifikation V-1).

**Herkunft.** Die Verifikation von [slice-schreibpfad-bulk-load](../in-progress/slice-schreibpfad-bulk-load.md) (V-1) stellte fest: (1) alle 10 aufgezeichneten Nachrichten 0x07 von `SqlBulkCopy` (Messreihe M-8 bis M-12, `referenztest-dotnet/Mitschnitt/messung/bulk-merge-katalog/{2022,2025}/`: `bulk-typen-null`, 3 × `bulk-mehrere-nachrichten`, `bulk-verstoss-schluessel`, je Instanz) weist der Leser mit `INVALID_TYPE` ab, weil der Referenzclient nicht nullbare Spalten als feste Typen sendet (die erste Spalte `Id` als BIGINT fest, Typkennung `0x7f`, Flags `0x0008`); der Leser kennt nur die nullbaren Formen (`INTN`, `BITN` …), die mssql-jdbc sendet; (2) `SqlBulkCopy` fragt zuerst mit einem Katalog-Batch (`SELECT @@TRANCOUNT; DECLARE @Object_ID INT = OBJECT_ID('[<Tabelle>]'); …`), den der Testserver nicht kennt: der Referenzclient erreicht das Massenladen am Testserver nicht. Mehr gemessen ist nicht: welche weiteren festen Typen der Referenzclient für andere Spaltentypen sendet, ist außer `0x7f` nicht aufgezeichnet.

**Liefern:**

- Den Leser um die festen Typen erweitern, **soweit eine Messnachricht sie sendet** (heute belegt: BIGINT `0x7f`). Weitere feste Typen (INT `0x38`, SMALLINT `0x34`, TINYINT `0x30`, BIT `0x32`, FLOAT `0x3e`, REAL `0x3b`, MONEY …) kommen erst, wenn eine neue Messnachricht des Referenzclients sie zeigt; bis dahin sind sie Handvektoren nach der Beschreibung von `MS-TDS` und als solche gekennzeichnet (AGENTS.md §3.8), nicht erfunden.
- Den Katalog-Batch von `SqlBulkCopy` bedienen: Form aus den Messdateien (Anfrage `SELECT @@TRANCOUNT; DECLARE @Object_ID …` und die Antwort des echten Servers, beide Instanzen), im Planer als Form des Katalogs.
- Beleg: ein `SqlBulkCopy`-Szenario im Referenztest (`referenztest-dotnet`) gegen den Testserver, das Zeilen lädt und liest, und der Fehlerfall (Schlüsselverstoß, 2627) in der Form von M-12.
- Die 10 Messnachrichten bleiben als **byte-genauer Negativ-Vektor** (heute: der Leser weist sie ab) bis zur Lieferung; mit ihr werden sie zum positiven Vektor (Test liest die Bytes der Messdatei, nicht nur Token-Felder).

**Aufnahme aus der Verifikation (V-6).** `BulkDispatch.answer` setzt bei einer Nachricht mit IGNORE-Bit `awaiting` nicht zurück; ein folgendes ATTENTION in `READY` wird dann mit `ERROR|ATTENTION` statt der gewöhnlichen Bestätigung beantwortet. Kleiner Fehler, im Slice mitzubehandeln (Zustand zurücksetzen, Test für die Kombination), falls dieser Slice vorher startet; sonst bleibt er im Register [`BEO-TDS/massenladen-ignore-bit-laesst-zustand-stehen`](../observations/BEO-TDS/massenladen-ignore-bit-laesst-zustand-stehen/state.md) offen.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Die Atomarität und der Code je Fehlerursache — [slice-adr-massenladen-atomar-an-messung](slice-adr-massenladen-atomar-an-messung.md) (Entscheidung durch den Nutzer; dieser Slice übernimmt, was sie entscheidet, nicht mehr).
- Messungen mit mssql-jdbc — Folgepflicht 2 von [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), Träger [slice-schreibpfad-merge-output](slice-schreibpfad-merge-output.md).
- PLP-Typen (MAX) und NBCROW — kein Lauf des Referenzclients im Messbestand sendet sie; erst mit einer Messnachricht.
- Neue öffentliche Namen — die Fläche ist [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 4 festgelegt; würde der Slice einen brauchen, geht er zurück zur Zerlegung (§4).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (3):

- [ ] **Leser, feste Typen:** Der Leser der Nutzlast nimmt die festen Typen an, die eine Messnachricht des Referenzclients sendet (BIGINT `0x7f`), und überführt sie wie ihre nullbaren Gegenstücke; die 10 Messnachrichten aus `referenztest-dotnet/Mitschnitt/messung/bulk-merge-katalog/{2022,2025}/` ergeben Zeilen, byte-genau aus den Messdateien gelesen ([TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)); weitere feste Typen nur mit Messnachricht oder als gekennzeichneter Handvektor; Negativfälle (abgeschnitten, unbekannter Typ) enden wie bisher definiert ([TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität)).
- [ ] **Katalog-Batch:** Der Testserver beantwortet den Katalog-Batch von `SqlBulkCopy` in der Form des echten Servers (Messdateien beider Instanzen); die Form steht in `SPEC-036`.
- [ ] **Beleg am Referenzclient:** Ein `SqlBulkCopy`-Szenario im Referenztest gegen den Testserver lädt Zeilen (Zähler, Lesen danach) und endet beim Schlüsselverstoß mit 2627; `make referenztest-dotnet` grün; die Aussage „gemessen mit `SqlBulkCopy`“ in Spezifikation, Matrix und Nutzer-Doku verliert die Grenze „die Nutzlast wird nicht gelesen“.

Nachweis-Pflichten für alle Punkte:

- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Haken der Liefer-Punkte setzt der Planner nach der Verifikation, nicht der Implementer (`BEO-TDS/haken-vor-verifikation-gesetzt`).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
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
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/bulk/BulkLoadReader.kt`, `BulkValueReader.kt` | update | feste Typen der Nachricht 0x07 |
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/CatalogForms.kt`, `CatalogAnswers.kt`, `StatementPlanner.kt` | update | Form des Katalog-Batchs von `SqlBulkCopy` |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/bulk/` | update | Messnachrichten byte-genau als Vektor (Eingang: `referenztest-dotnet/Mitschnitt/messung/bulk-merge-katalog/`) |
| `referenztest-dotnet/` | update | `SqlBulkCopy`-Szenario gegen den Testserver |
| `spec/spezifikation.md` (`SPEC-036`), `docs/plan/kompatibilitaetsmatrix.md`, `docs/user/testserver-manuell.md` | update | die Grenze „Nutzlast des Referenzclients wird nicht gelesen“ entfällt |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-schreibpfad-bulk-load](../in-progress/slice-schreibpfad-bulk-load.md) liegt in `done/` (erfüllt mit der Closure dieses Vorgängers) und ein Verantwortlicher ist eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): **neue öffentliche Fläche** wird nötig (ein Typ, ein Parameter), oder mehr als drei Liefer-Punkte; Schnittvorschlag: Leser | Katalog-Batch | Referenztest.
- `in-progress` → `open` (blockiert): die Entscheidung zu [slice-adr-massenladen-atomar-an-messung](slice-adr-massenladen-atomar-an-messung.md) ändert die Form der Fehlerantwort, die der Referenztest belegt.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

DoD vollständig, `make gates` grün, das Szenario mit `SqlBulkCopy` gegen den Testserver ist als Lauf belegt, Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Der Referenzclient sendet für andere Spaltentypen weitere feste Typen, die in den Messnachrichten fehlen; sie sind dann ohne Messung zu lesen (Handvektor). — **Ausgang:** offen bis zur Closure.
- Der Katalog-Batch ist lang und kommt in Teilen (Variablen, dynamischer Text); eine Form, die nur die gemessene Frage trägt, kann bei anderen Tabellen brechen. — **Ausgang:** offen bis zur Closure.

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

- **Was hat funktioniert:** —
- **Was ging anders als geplant:** —
- **Steering-Loop-Eintrag:** —
- **Beobachtungs-Register (`../observations/`):** —
- **Folge-Slices:** —
- **Risiken aus §6:** —

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung.

**Vorgelagert — Sub-Area-Wahl prüfen:** `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); eine eigene Sub-Area besteht nicht.

**Vorgelagert — offene Beobachtungen sichten:** [`BEO-TDS/messung-am-echten-sql-server-fehlt`](../observations/BEO-TDS/messung-am-echten-sql-server-fehlt/state.md), [`BEO-TDS/aussagen-ueber-messung-hinaus`](../observations/BEO-TDS/aussagen-ueber-messung-hinaus/state.md) (Anfrage nicht gelesen, Antwort gemessen), [`BEO-TDS/massenladen-ignore-bit-laesst-zustand-stehen`](../observations/BEO-TDS/massenladen-ignore-bit-laesst-zustand-stehen/state.md).

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
