# Slice slice-abnahme-d-migrate: Abnahme: Validator-Lauf mit d-migrate über alle Funktionen des Solls gegen den Testserver

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-testserver-manuell

**Bezug:** [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-RB-01](../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md)

**Berührte Spec-Stellen:** — (Lauf und Beleg; Spec-Lücken, die der Lauf findet, gehen als benannte Lücke in die Closure-Notiz)

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-05.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert; jeder Ausschluss mit Begründung.

**Ziel:** Ein Validator-Agent in frischem Kontext ohne Planwissen führt nach der Anleitung `docs/user/testserver-manuell.md` alle Funktionen des Solls mit d-migrate 1.12.0 gegen den Testserver aus und belegt jede mit der Ausgabe: Anmeldung (unverschlüsselt und mit `encrypt=true`), `data export`, `schema reverse`, `schema compare`, `schema migrate` einschließlich `--execute`, `data import` und `data transfer` im Standardpfad und mit `--on-conflict skip|update`. Das ist das „Mehr“ der Welle [welle-testserver-manuell](../welle-testserver-manuell.md) gegenüber den Slice-DoDs.

**Quelle und Vorbehalt.** Auslöser ist die Sonde vom 2026-11-05 ([d-migrate-sonde-2026-11-05](../../../messungen/d-migrate-sonde-2026-11-05.md)). Ihr Vorbehalt gilt hier: alles, was dort „lief“, lief gegen erfundene Katalogantworten; belegt ist, was d-migrate sendet und dass Treiber und d-migrate die TDS-Antworten der Bibliothek verarbeiten. Aufwandsgrößen (S/M/L) sind Schätzungen der Sonde, nicht gemessen. Dieser Slice ist der Beleg, den die Sonde nicht liefern konnte: gegen die Bibliothek mit dem Testserver als Integrator (Katalog aus den mitgeteilten Tabellen, Bestand im Arbeitsspeicher des Testservers), nicht gegen vorbereitete Antworten.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- `data profile`: Aggregate, `CAST`, `TOP`, `GROUP BY` schließt [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) aus; der Validator führt es höchstens als Negativfall aus (Erwartung: definierter SQL-Fehler, Exit ungleich 0), nicht als Soll-Funktion.
- Behebung gefundener Fehler im Slice: Der Validator meldet; jede Behebung ist ein Folge-Slice oder ein Rücklauf in den betroffenen Slice (der Rest der Welle bleibt offen, bis alles funktioniert).
- Der Vergleich gegen einen echten SQL Server: bleibt der Vergleichslauf D (EULA-Entscheidung des Nutzers).
- Aufnahme des Laufs in `make gates`: ein Lauf mit fremdem Image gehört nach [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)-Muster nicht in die Gates; ob ein `make`-Ziel entsteht, ist eine Entscheidung dieses Slice, kein Zwang.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit.

Liefer-Punkte (3):

- [ ] **Lauf durch den Validator:** Ein Agent des Typs `validator` in frischem Kontext führt die Funktionen des Solls aus (Rechner, Last, Image-Digest von d-migrate und Testserver, Befehle, Exit-Codes, Auszüge der Ausgabe); jede Abweichung von der Anleitung ist ein Fund.
- [ ] **Beleg je Funktion:** eine Zeile je Funktion in einem Messprotokoll unter `docs/messungen/` mit Exit-Code und dem Zustand danach (Export nach Import, Struktur nach `--execute`); nicht gelaufene Funktionen stehen als nicht gelaufen, nicht als grün.
- [ ] **Anleitung nachgezogen:** `docs/user/testserver-manuell.md` beschreibt den Ist-Zustand (keine Chronik): die Grenze zu JDBC und die Aussage „Tabellen aus der Datei sind leer, Einfügen wird abgewiesen“ entsprechen dem Stand nach der Welle; sie nennt, wie der Bestand des Testservers zustande kommt und was er nicht tut (keine Dauerhaftigkeit).

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
| `docs/messungen/` — Messprotokoll des Validator-Laufs <!-- d-check:ignore (geplant: neue Datei) --> | neu | Beleg je Funktion |
| `docs/user/testserver-manuell.md` | update | Ist-Zustand nach der Welle |
| optional `tools/` und `tools/mk/` — Skript und Ziel für den Lauf | neu | nur wenn der Lauf wiederholbar sein soll; nicht in `make gates` |

**Anschluss an die Entscheidung** ([ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), `Accepted` am 2026-11-06, Messungen: [Messprotokoll des Spikes](../../../messungen/callback-loesung-spike-2026-11-05.md); Liste in §3 von [slice-adr-testserver-bestand-und-katalog](../done/slice-adr-testserver-bestand-und-katalog.md)). Spec-Änderungen dieses Slice: `SPEC-040` (alle Läufe mit d-migrate, Exit 0), in der Tabelle „Login-Antwort des Servers (Festlegung)“ der Ersatz des Satzes „Welche Hauptversion mssql-jdbc mindestens verlangt, ist nicht gemessen“ durch die Messung (Hauptversionen 9 bis 17 ohne 14 angenommen, 0 und 8 abgelehnt, [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 10; der Wert `16.0.1000.6` bleibt; Übernahme aus dem Entscheidungs-Slice, der die Spezifikation nicht geändert hat) und die Festlegung von Version und Treiber des Prüfclients (d-migrate 1.12.0, mssql-jdbc 13.4.0, Image-Digest), die [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate) der Spezifikation überlässt; ein Eintrag im Clientprofil braucht keine ADR (Entscheid 2026-11-06). Aus dem Spike: Der Standardpfad von `data import` ist für Tabellen mit INT-Spalte nicht zu belegen (b3); mit `--on-conflict skip` lief INT nur mit Verengung Int64 auf Int32, angepasstem RPC-Parser und unter der Handle-Grenze (a3, a6, a7); die Abnahme sagt das aus, statt es zu überspielen (Entscheid 2026-11-06: BIGINT-Schlüssel in den Beispieltabellen, Gegenmessung am echten SQL Server bleibt offen).

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): Alle anderen Slices der Welle [welle-testserver-manuell](../welle-testserver-manuell.md) für das Soll liegen in `done/`: [slice-prelogin-serverversion](../done/slice-prelogin-serverversion.md), [slice-d-migrate-verbindung-und-export](../done/slice-d-migrate-verbindung-und-export.md), [slice-katalog-und-ddl-zustand](../done/slice-katalog-und-ddl-zustand.md), [slice-schreibpfad-bulk-load](../done/slice-schreibpfad-bulk-load.md), [slice-schreibpfad-merge-output](../done/slice-schreibpfad-merge-output.md), [slice-tls-d-migrate](../done/slice-tls-d-migrate.md).

**Weitere Slices der Welle, Nutzerentscheid 2026-11-07 (Zuordnung des Planners, geprüft gegen das Soll):** [slice-katalog-und-struktur-an-messung-angleichen](slice-katalog-und-struktur-an-messung-angleichen.md), [slice-katalogspalten-smallint-tinyint-angleichen](slice-katalogspalten-smallint-tinyint-angleichen.md), [slice-ddl-abschluss-und-transaktionszustand-angleichen](slice-ddl-abschluss-und-transaktionszustand-angleichen.md) (die drei Teile des früheren Slice `slice-katalog-und-struktur-an-messung-angleichen`, Zuschnitt 2026-11-08 nach [ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) und [ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md)), [slice-sp-unprepare-ohne-obergrenze](slice-sp-unprepare-ohne-obergrenze.md) und [slice-antwort-tokens-zielbild-luecken-bauen](slice-antwort-tokens-zielbild-luecken-bauen.md) gleichen den Server dem echten SQL Server an (Zielbild); keine Funktion des Solls hängt an ihnen, sie sind **Grenze, keine Voraussetzung**; liegen sie vor der Abnahme, misst der Lauf sie mit (Regress der Funktionen `schema reverse`, `schema migrate --execute`), sonst gelten die Messvermerke der Spezifikation. [slice-adr-massenladen-atomar-an-messung](slice-adr-massenladen-atomar-an-messung.md) ist **Eingang** von [slice-schreibpfad-bulk-load](../done/slice-schreibpfad-bulk-load.md) (Verhalten bei Fehler im Massenladen) und damit mittelbar Voraussetzung dieser Abnahme für `data import` im Standardpfad.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): Der Lauf findet Fehler in mehreren Funktionen; die Behebungen wandern in die betroffenen Slices, die Abnahme beginnt von vorn.
- `in-progress` → `open` (blockiert): Ein Fund verlangt eine Entscheidung des Nutzers (zum Beispiel eine Funktion, die sich nicht belegen lässt).

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln.

Jede Funktion des Solls hat einen Beleg mit Ausgabe oder einen benannten, vom Nutzer entschiedenen Ausgang; `make gates` grün; Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben. Dieser Slice ist die Bedingung des Mehrs im Closure-Trigger der Welle.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — jedes Risiko bekommt genau einen Ausgang.

- Ein Validator ohne Planwissen findet Lücken der Anleitung, die der Planner nicht sieht; das ist die Absicht, kostet aber Läufe (Aufwand nicht gemessen; Setzung M). — **Ausgang:** offen bis zur Closure.
- Lauf hängt an einem Image mit beweglichem Tag (`ghcr.io/pt9912/d-migrate:latest`): der Digest wird im Protokoll genannt, die Verfügbarkeit des Images nicht gesichert. — **Ausgang:** offen bis zur Closure.
- FLOAT (Hürde 12): Braucht eine Funktion des Solls den Typ und hat die Entscheidung ihn nicht aufgenommen, scheitert die Abnahme an dieser Stelle. — **Ausgang:** offen bis zur Closure.

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
