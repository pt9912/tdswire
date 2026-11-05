# Slice slice-d-migrate-verbindung-und-export: d-migrate meldet sich an und exportiert: SELECT 1, SET-Anweisungen, sp_executesql mit einem Parameter und ohne Parameternamen, WHERE 1 = 0

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-testserver-manuell

**Bezug:** [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-FA-03](../../../../spec/lastenheft.md#tds-fa-03--einfache-sql-batches), [TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md), [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md)

**Berührte Spec-Stellen:** SPEC-030 (RPC-Varianten und Parameter-Metadaten) · SPEC-036 (Formen, Form C) · SPEC-004 (Testprofil, füllt Form C) · [TDS-FA-11.a](../../../../spec/spezifikation.md#tds-fa-11a--simulations-backend) (Simulations-Backend)

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-05.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert; jeder Ausschluss mit Begründung.

**Ziel:** Der Pool-Verbindungstest und die SET-Anweisungen von d-migrate werden beantwortet, `sp_executesql` mit einer Parameterliste ohne Deklaration und mit unbenannten Wertparametern wird angenommen, und `SELECT * FROM <Tabelle> WHERE 1 = 0` liefert Metadaten ohne Zeile; damit läuft die Anmeldung und `data export` einer Tabelle des Simulations-Backends durch (Hürden 2 bis 5 und 8 der Sonde).

**Quelle und Vorbehalt.** Auslöser ist die Sonde vom 2026-11-05 ([d-migrate-sonde-2026-11-05](../../../messungen/d-migrate-sonde-2026-11-05.md)). Ihr Vorbehalt gilt hier: alles, was dort „lief“, lief gegen erfundene Katalogantworten; belegt ist, was d-migrate sendet und dass Treiber und d-migrate die TDS-Antworten der Bibliothek verarbeiten. Aufwandsgrößen (S/M/L) sind Schätzungen der Sonde, nicht gemessen. Gemessen (Sonde) sind die abgelehnten Texte und Pakete; nicht gemessen ist, dass die Antworten, die dieser Slice festlegt, genügen: dafür steht Liefer-Punkt 3.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- PRELOGIN-Version (Hürde 1) — [slice-prelogin-serverversion](../done/slice-prelogin-serverversion.md); ohne sie kommt die Anmeldung nicht bis hierher, der Slice setzt sie voraus.
- Katalogabfragen auf `sys.*` und `INFORMATION_SCHEMA` (Hürde 6) und Zustand nach DDL (Hürde 7) — [slice-katalog-und-ddl-zustand](../open/slice-katalog-und-ddl-zustand.md); sie hängen an der Entscheidung in [slice-adr-testserver-bestand-und-katalog](../open/slice-adr-testserver-bestand-und-katalog.md), der Ort des Katalogs ist offen.
- Schreiben in Tabellen (Hürden 9 und 10) — [slice-schreibpfad-bulk-load](../open/slice-schreibpfad-bulk-load.md) und [slice-schreibpfad-merge-output](../open/slice-schreibpfad-merge-output.md); hier nur Lesen.
- Zeilen im Export: Tabellen aus der Beschreibungsdatei haben keine Zeilen (SPEC-035, Setzung, `docs/user/testserver-manuell.md`); der Beleg dieses Slice ist ein Export ohne Zeilen. Zeilen belegt der Schreibpfad und die Abnahme.
- TLS mit dem Standard des Treibers (Hürde 13) — [slice-tls-d-migrate](../next/slice-tls-d-migrate.md); die Belege dieses Slice laufen mit `encrypt=false` gegen `TEST_UNENCRYPTED`.
- Aggregate, `TOP`, `GROUP BY`, `ORDER BY` (`data profile`, Hürde 11): schließt [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) aus und steht nicht im Soll von [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit.

Liefer-Punkte (3):

- [ ] **Protokoll:** `sp_executesql` mit einem Parameter (ohne Deklarationsliste) und Wertparameter ohne Namen (positional) werden angenommen; die Parameter kommen im Backend unter einem festgelegten Namen an (Setzung, die Sonde ergänzte `@P<i>`; ob das der Name ist, legt der Slice fest). SPEC-030 trägt die Festlegung; Handvektoren stammen aus einer erneuten Aufzeichnung mit mssql-jdbc oder d-migrate, mit Quelle im Test (`AGENTS.md` §3.8), nicht aus der Erinnerung an die Sonde.
- [ ] **Anweisungsformen:** Form C (SPEC-004/SPEC-036) nennt `SELECT 1` und die SET-Anweisungen, die d-migrate nach dem Verbindungsaufbau sendet (`SET LOCK_TIMEOUT`, `SET ANSI_NULLS`, `SET ANSI_PADDING` und weitere, `set implicit_transactions on|off`), jeweils mit festgelegtem Ergebnis; eine Anweisung ohne Ergebnis endet als Änderungszähler 0 (die Sonde maß: `ResultEnd` ohne `ResultStart` ergibt Fehler 50007). Dazu `WHERE 1 = 0` als Bedingung, die keine Zeile trifft, mit Spaltenmetadaten (Boundary von [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend)). Ob nur der Text `1 = 0` oder ein Konstantenterm erkannt wird, ist eine Setzung des Slice (SPEC-036 schließt Ausdrücke aus).
- [ ] **Beleg am fremden Client:** ein Lauf von d-migrate 1.12.0 gegen `make testserver-up` (`encrypt=false`): Anmeldung, `data export` einer Tabelle der Beispieldatei (Ergebnis ohne Zeilen); die Ausgabe (Exit-Code, Auszug, Rechner, Last, Image-Digest) steht im Slice oder in einem Messprotokoll unter `docs/messungen/`. Was danach scheitert, wird gemeldet und dem Folge-Slice zugeordnet, nicht hier behoben.

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
| `spec/spezifikation.md` SPEC-030, SPEC-036, SPEC-004 | update | Festlegung der Parameternamen, Form C, Konstantenbedingung; Setzung gekennzeichnet |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/rpc/RpcCatalog.kt`, `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/RpcParameters.kt` | update | Sonde: `executeSql` verlangt mindestens zwei Parameter, `checkName` weist leere Namen ab (Messung im Messprotokoll, Hürden 4 und 5) |
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/` (Parser, Planer, Backend) | update | Form C und `1 = 0`; Antwort ohne Ergebnis als Änderungszähler |
| Tests unter `src/test/kotlin/` und `.NET`-Referenztests | neu / update | Happy/Boundary/Negative je neuer Form nach [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend); der Referenzclient darf nicht rot werden ([TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)) |

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-prelogin-serverversion](../done/slice-prelogin-serverversion.md) liegt in `done/`; die Welle [welle-testserver-manuell](../welle-testserver-manuell.md) läuft; ein Verantwortlicher ist eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte; Schnittvorschlag: Protokoll (Hürden 4, 5) und Anweisungsformen (2, 3, 8) trennen.
- `in-progress` → `open` (blockiert): die Entscheidung in [slice-adr-testserver-bestand-und-katalog](../open/slice-adr-testserver-bestand-und-katalog.md) ändert den Zuschnitt der öffentlichen Fläche.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln.

DoD vollständig, `make gates` grün, die Ausgabe des d-migrate-Laufs liegt vor, Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — jedes Risiko bekommt genau einen Ausgang.

- Die Formen für d-migrate stehen in der Bibliothek (Nutzerentscheidung 2026-11-05: die Funktionalität ist in der Lib) und weiten das Simulations-Backend über den Referenzclient hinaus; gedeckt ist das vom Prüfclient d-migrate in [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), dessen Wortlaut der Nutzer am 2026-11-05 freigegeben hat. — **Ausgang:** offen bis zur Closure.
- Die Handvektoren der Pakete stammen aus einem Treiber, der nicht Teil des Clientprofils ist ([TDS-RB-02](../../../../spec/lastenheft.md#tds-rb-02--externer-client)); die Aussage gilt für mssql-jdbc 13.4.0, nicht für andere Versionen. — **Ausgang:** offen bis zur Closure.
- Eine Änderung an `RpcParameters` kann den .NET-Referenzclient berühren (anderer Parameterpfad); nicht gemessen. — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (Namensliste der Eröffnung, Texte nicht einzeln gelesen). Treffer: [festlegung-gilt-nur-fuer-das-clientprofil](../observations/BEO-TDS/festlegung-gilt-nur-fuer-das-clientprofil/observation.md) (1×). Der Implementer sichtet das Register bei Start erneut.

Alle berührten Sub-Areas GF.
