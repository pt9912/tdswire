# Slice slice-schreibpfad-merge-output: MERGE ... OUTPUT $action: Form in der Bibliothek, Konfliktbehandlung beim Integrator, data import und data transfer mit --on-conflict skip und update

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-testserver-manuell

**Bezug:** [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md), [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md)

**Berührte Spec-Stellen:** SPEC-036 (neue Form für `MERGE` mit VALUES-Zeile, Alias und `OUTPUT $action`; bisher „OUTPUT-Klauseln nicht erkannt“) · SPEC-030 (Parametertypen) · SPEC-035 (Callbacks) · SPEC-040

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-05.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert; jeder Ausschluss mit Begründung.

**Ziel:** Die Bibliothek erkennt das `MERGE` mit Parametern, gibt es nach der Entscheidung an den Integrator weiter und liefert dessen Ergebnis als Ergebniszeile `$action`; der Testserver führt die Konfliktbehandlung gegen seinen Bestand aus, sodass `data import` und `data transfer` von d-migrate mit `--on-conflict skip` und `update` durchlaufen und ein Export die Zeilen enthält (Hürde 10 der Sonde).

**Quelle und Vorbehalt.** Auslöser ist die Sonde vom 2026-11-05 ([d-migrate-sonde-2026-11-05](../../../messungen/d-migrate-sonde-2026-11-05.md)). Ihr Vorbehalt gilt hier: alles, was dort „lief“, lief gegen erfundene Katalogantworten; belegt ist, was d-migrate sendet und dass Treiber und d-migrate die TDS-Antworten der Bibliothek verarbeiten. Aufwandsgrößen (S/M/L) sind Schätzungen der Sonde, nicht gemessen. Gemessen ist der Text und die Parametertypen (Int64, UnicodeText, Date, vom Binder angenommen); die Sonde beantwortete das MERGE mit einer festen Zeile, nichts wurde gespeichert. Der Abbruch-Pfad (`abort`) nimmt laut Sonde den Massenpfad; ein `INSERT` mit Parametern wurde nicht beobachtet. **Hülle:** Der Plan ist knapp; der Implementer erweitert §3 im ersten Lauf, nachdem die Entscheidung in [slice-adr-testserver-bestand-und-katalog](../done/slice-adr-testserver-bestand-und-katalog.md) vorliegt (Form der Weitergabe und Namen der Fläche hängen an ihr). **Aufteilung:** Die Funktion liegt in der Bibliothek (`src/main`, Packages `simulation`, `protocol`); der Testserver (`testserver`) ist Integrator: Bestand, Callbacks, Beispieltabellen. Jeder Liefer-Punkt unten nennt seinen Anteil.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Der Standardpfad mit Paket 0x07 — [slice-schreibpfad-bulk-load](slice-schreibpfad-bulk-load.md).
- Allgemeines `MERGE` (mehrere Quellen, mehrere WHEN-Zweige, `DELETE`-Zweig, beliebige `OUTPUT`-Spalten): erkannt wird nur die Gestalt, die d-migrate sendet; alles andere bleibt `TDS_UNSUPPORTED_SQL`.
- Katalog und DDL — [slice-katalog-und-ddl-zustand](../in-progress/slice-katalog-und-ddl-zustand.md).
- Konfliktsemantik jenseits von Schlüsselvergleich (Bedingungen, Trigger); die Bibliothek speichert und vergleicht nichts selbst ([TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend)).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit.

Liefer-Punkte (3):

- [ ] **Bibliothek, Form und Weitergabe:** SPEC-036 legt die Form des MERGE fest, das d-migrate sendet (Erkennung am Text, Zuordnung der Parameter); die Weitergabe an den Integrator, die Ergebnismenge `$action` und der Änderungszähler sind festgelegt (Setzung, nicht am echten SQL Server gemessen); Tests je Zweig und für NULL-Parameter und Typgrenzen ([TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc) Boundary).
- [ ] **Testserver:** Der Testserver führt Einfügen, wenn der Schlüssel fehlt, bei `skip` unverändert, bei `update` ändern, gegen seinen Bestand aus und meldet die Aktion zurück.
- [ ] **Beleg am fremden Client:** `data import` und `data transfer` mit `--on-conflict skip` und `--on-conflict update` gegen den Testserver enden mit Exit 0; ein `data export` danach zeigt die erwarteten Zeilen; Ausgabe mit Rechner, Last und Image-Digest.

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
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/` (Scanner, Parser, Planer) | update | neue Form; SPEC-036 schließt `OUTPUT`-Klauseln heute aus |
| Weitergabe (Callback oder Vertrag nach der Entscheidung) | update | Konflikt-Einfügen und Aktion als Ergebnis |
| `testserver/kotlin/` (Bestand, Callback) | update | Integrator-Anteil: Konfliktbehandlung |
| `spec/spezifikation.md` SPEC-036 | update | Form M, Setzungen gekennzeichnet |
| Tests | neu | Happy/Boundary/Negative nach [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate) |

**Anschluss an die Entscheidung** ([ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), `Accepted` am 2026-11-06, Messungen: [Messprotokoll des Spikes](../../../messungen/callback-loesung-spike-2026-11-05.md); Liste in §3 von [slice-adr-testserver-bestand-und-katalog](../done/slice-adr-testserver-bestand-und-katalog.md)). Spec-Änderungen dieses Slice: `SPEC-036` (Form `MERGE … OUTPUT $action` für `skip` und `update`, „`OUTPUT`-Klauseln“ verlassen die Liste „Nicht erkannt“; der vollständige Wortlaut ist aus einem erneuten Mitschnitt zu übernehmen, der Spike hat ihn nur teilweise im Log), `SPEC-035` (Zerlegung in Lesen über die Zeilenquelle und höchstens einen Callback; der Vorfilter bleibt ein Hinweis, die Bibliothek prüft den Schlüssel; Verengung von Int64 auf Int32 im Wertebereich), `SPEC-030` (`DECIMALN` mit kürzerem Wert als der Typ, `DATETIMN` mit 8 Byte als Zeitpunkt, Zeit als `DATETIMN`), `SPEC-040` (`--on-conflict skip|update`). Entschieden (ADR `Accepted`, 2026-11-06): `MERGE` und die Lesart „abschließend“ sind bestätigt; die Handle-Grenze ersetzt eine eigene neue ADR (Folge-Slice vor diesem Slice, ersetzt Entscheidung 1 und 4 von [ADR-0017](../../adr/0017-prepared-handles-grenzwert.md)), nicht dieser Slice. Start-Trigger unverändert (§4), zusätzlich: die Folge-ADR zur Handle-Grenze ist `Accepted` ([ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md), `Accepted` am 2026-11-06; Antwortform, Freigabe und Eingang der Grenze baut [slice-handle-freigabe-und-grenze-bauen](../in-progress/slice-handle-freigabe-und-grenze-bauen.md) vor diesem Slice). Messung zur Grenze: [handle-grenze-optionen-2026-11-06](../../../messungen/handle-grenze-optionen-2026-11-06.md) — mssql-jdbc sendet ohne RETURNSTATUS in der `sp_prepexec`-Antwort ein `sp_prepexec` je Ausführung und nie `sp_unprepare`; mit ihm sendet es `sp_unprepare` als SQL-Batch. Spec-Liste in [slice-adr-handle-grenze-ersatz](../done/slice-adr-handle-grenze-ersatz.md) §3; der Bau-Slice [slice-handle-freigabe-und-grenze-bauen](../in-progress/slice-handle-freigabe-und-grenze-bauen.md) ist ein weiterer Start-Trigger dieses Slice. Aus dem Spike: ohne die drei Änderungen am RPC-Parameter lehnt die Bibliothek den MERGE von d-migrate ab („Unsupported RPC.“ bzw. „Protocol error.“, a7); die Handle-Grenze 1000 aus [ADR-0017](../../adr/0017-prepared-handles-grenzwert.md) bricht den Import nach rund 1000 Zeilen je Sitzung (a6, Entscheid der ADR vom 2026-11-06); `MERGE` nennt das Lastenheft nicht wörtlich (Entscheid der ADR vom 2026-11-06).

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-adr-testserver-bestand-und-katalog](../done/slice-adr-testserver-bestand-und-katalog.md) liegt in `done/` (Weitergabe, Katalog nach Änderungen und Fläche entschieden) und [slice-katalog-und-ddl-zustand](../in-progress/slice-katalog-und-ddl-zustand.md) liegt in `done/` (Bestand des Testservers besteht) und [slice-adr-handle-grenze-ersatz](../done/slice-adr-handle-grenze-ersatz.md) liegt in `done/` (die ADR, die Entscheidung 1 und 4 von [ADR-0017](../../adr/0017-prepared-handles-grenzwert.md) ersetzt; ohne sie bricht der Import nach 1001 `MERGE` je Sitzung ab, Messzeile a6 des Spikes) und [slice-handle-freigabe-und-grenze-bauen](../in-progress/slice-handle-freigabe-und-grenze-bauen.md) liegt in `done/` (baut Antwortform, Freigabe und einstellbare Grenze aus [ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md); ohne ihn bricht der Import nach 1001 `MERGE` je Sitzung ab); damit stehen [slice-testserver-gradle-modul](../done/slice-testserver-gradle-modul.md) und [slice-oeffentliche-flaeche-verkleinern-und-belegen](../done/slice-oeffentliche-flaeche-verkleinern-und-belegen.md) vor diesem Slice (Kette über den Katalog-Slice). Reihenfolge vom Nutzer am 2026-11-06 entschieden ([ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md)).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte; Schnittvorschlag: Anteil der Bibliothek und Anteil des Testservers als zwei Slices.
- `in-progress` → `open` (blockiert): die Entscheidung in [slice-adr-testserver-bestand-und-katalog](../done/slice-adr-testserver-bestand-und-katalog.md) trägt den Fall nicht oder wird überstimmt.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln.

DoD vollständig, `make gates` grün, die Ausgabe der d-migrate-Läufe liegt vor, Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — jedes Risiko bekommt genau einen Ausgang.

- Aufwand der Sonde: M bis L; die Erkennung von `MERGE` weitet die Liste der Formen, die SPEC-036 „abschließend“ nennt (nicht vom Nutzer bestätigt). — **Ausgang:** offen bis zur Closure.
- Die Gestalt des MERGE hängt an d-migrate 1.12.0 und seiner Spaltenliste; eine andere Version kann einen anderen Text senden (nicht gemessen). — **Ausgang:** offen bis zur Closure.
- Da die Bibliothek nichts speichert, muss die Weitergabe die Konfliktsemantik (Schlüsselvergleich, Aktion) tragen; ob dafür ein eigener Callback nötig ist oder eine Folge aus Lesen und Einfügen genügt, entscheidet [slice-adr-testserver-bestand-und-katalog](../done/slice-adr-testserver-bestand-und-katalog.md). — **Ausgang:** offen bis zur Closure.
- Ob d-migrate beim `MERGE` seine Anweisungsobjekte schließt oder wiederverwendet, ist nicht gemessen; davon hängt ab, ob die Freigabe aus [ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md) die Grenze für diesen Pfad entschärft (nur der Treiber ist gemessen). — **Ausgang:** offen bis zur Closure.
- Der Client wählt den Typ seiner Parameter selbst: d-migrate 1.12.0 sendet Ganzzahlwerte als `bigint`, die strenge Überführung ([SPEC-035](../../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends)) weitet nur `Int32` auf `Int64`; ein Filter `Id = 7` gegen eine `Int32`-Spalte endete im Export mit „Invalid value“ (Beobachtung [parametertyp-des-clients-passt-nicht-zur-spalte](../observations/BEO-TDS/parametertyp-des-clients-passt-nicht-zur-spalte/observation.md), Ursache aus dem Code gelesen, nicht gemessen). Der Slice prüft die Parameter seines Pfades gegen die Spaltentypen. — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (Namensliste der Eröffnung, Texte nicht einzeln gelesen); kein Treffer ausgemacht. Der Implementer sichtet das Register bei Start erneut.

Alle berührten Sub-Areas GF.
