# Slice slice-qa-vergleichslauf-d-messungen-bulk-merge: Vergleichslauf D, Messungen am echten SQL Server zu Massenladen (INSERT BULK), MERGE und den Katalogantworten von d-migrate ohne Träger

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-testserver-manuell

**Bezug:** [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md)

**Berührte Spec-Stellen:** SPEC-036 (`INSERT BULK`, `SET FMTONLY`, Form des `MERGE`, Katalogabfragen und DDL-Formen; dort heute Setzungen ohne Messung) · SPEC-035 (Katalog der Spalten: Typen der Katalogspalten, Typnamen und Maße von `bigint`, `bit`, `time`, `datetime2`, Sonderwert `maxLength` -1, Fehler der Strukturabweisung) · Protokollteil Pakettypen und Dispatch (Paket 0x07) · SPEC-045 (Ausnahmeliste, soweit Zeilen entstehen) · SPEC-060 (nur der Messvermerk der Obergrenze, falls die Zusatzmessung sie bestätigt oder widerlegt) · SPEC-030 (nur der Messvermerk zum Flag-Feld der Ausgabewerte, A-20). Die Spezifikation ändern die Bau-Slices [slice-schreibpfad-bulk-load](../in-progress/slice-schreibpfad-bulk-load.md) und [slice-schreibpfad-merge-output](slice-schreibpfad-merge-output.md) und, für den Katalog, ein Bau-Slice nach dem Befund (Folge-Slice [slice-katalog-indizes-defaults-fremdschluessel](slice-katalog-indizes-defaults-fremdschluessel.md) für Indizes, Standardwerte, Prüfbedingungen, Fremdschlüssel), nicht dieser Slice.

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912. **Datum:** 2026-11-06.

---

## 1. Ziel und Abgrenzung


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Die Antworten des echten SQL Servers (2022 und 2025, Digest-Pin) auf das Massenladen (Paket BULK_LOAD 0x07 nach `INSERT BULK`) und auf `MERGE ... OUTPUT $action` mit Parametern sind als Einzelmessung je Instanz im Messprotokoll festgehalten (Tokenfolge, Statusbits, Zähler, Fehlerfälle), damit die Setzungen der beiden Schreib-Slices einen Maßstab haben statt einer Annahme. Dasselbe gilt für die Katalogantworten, die der geschlossene Katalog-Slice [slice-katalog-und-ddl-zustand](../done/slice-katalog-und-ddl-zustand.md) gegen den Testserver, nicht gegen einen echten Server belegt hat: die Typen der Katalogspalten, die Setzungen für `bigint`, `bit`, `time` und `datetime2`, der Sonderwert `maxLength` -1 und die Fehlernummern der Strukturabweisung. Nutzerentscheid vom 2026-11-06; Maßstab ist der Grundsatz „Spezifikation ist Zielbild, der echte SQL Server ist die Referenz“.

**Herkunft.** `BEO-TDS/messung-am-echten-sql-server-fehlt` (Antworten auf Massenladen, MERGE und Katalog ohne Träger) und die Verifikation V-1 von [slice-katalog-und-ddl-zustand](../done/slice-katalog-und-ddl-zustand.md): die Katalogmessung hatte keinen Träger (Kreis zwischen dem Messprotokoll des Katalog-Slice, diesem Slice und dem Register); **dieser Slice trägt sie** (Nutzerentscheid 2026-11-06, „deine Empfehlungen“). Läufe nur lokal; die EULA für das gepinnte Image hat der Nutzer bestätigt (2026-11-06).

**Schnitt und Name (Nutzerentscheid 2026-11-06, „deine Empfehlungen“):** Der Name des Slice bleibt, und der Schnitt bleibt bei drei Liefer-Punkten.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Der Bau der Katalogsichten für Indizes, Standardwerte, Prüfbedingungen und Fremdschlüssel — [slice-katalog-indizes-defaults-fremdschluessel](slice-katalog-indizes-defaults-fremdschluessel.md); dieser Slice misst die Antworten, die der Katalog-Slice schon gebaut hat (Spalten, Schlüssel, Strukturänderung), und liefert dem Folge-Slice die Messung der übrigen Sichten als Eingang, soweit sie dieselbe Messreihe trägt.
- Eine Änderung des Katalogs im Server dieses Repos nach dem Befund — der Befund ist Eingang eines Bau-Slice; ein Widerspruch zum Zielbild geht als Auftrag an den Nutzer (Spezifikation ist Zielbild).
- Der Bau von Massenladen und MERGE im Server dieses Repos — [slice-schreibpfad-bulk-load](../in-progress/slice-schreibpfad-bulk-load.md) und [slice-schreibpfad-merge-output](slice-schreibpfad-merge-output.md); dieser Slice misst, er ändert weder Quelltext von `src/` noch die Spezifikation.
- Ein Gate oder eine Aufnahme in `make gates` — der Vergleichslauf D bleibt lokal, ohne CI, mit EULA-Entscheidung des Nutzers ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md): kein Gate); die Messdateien sind Belege, kein Sensor.
- Last, Durchsatz und große Mengen — [slice-qa-performance](slice-qa-performance.md); die Messung trägt Form und Zähler, nicht Zeit.
- Eine Aussage über andere Clients als den Referenzclient — der Referenzclient (`Microsoft.Data.SqlClient`) ist nicht d-migrate; die Messung trägt die Antwort auf den Text und die Pakete, die sie sendet.

## 2. Definition of Done


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **Messreihe Schreibpfad: Massenladen und MERGE** ([TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate)): (a) Massenladen: `SqlBulkCopy` gegen beide Instanzen am Draht aufgenommen (Muster: `referenztest-dotnet/Vergleich/MessungWireTests.cs`, Mitschnitt unter `referenztest-dotnet/Mitschnitt/messung/{2022,2025}/`, redigiert): Quelle mit mehreren Typen und NULL, Quelle über mehrere Nachrichten, leere Quelle, Verstoß gegen Typ oder Länge; je Szenario Tokenfolge, Statusbits und Zähler der Antwort und die Ausnahme des Clients. (b) MERGE: `MERGE ... OUTPUT $action` mit Parametern am Draht aufgenommen, mit der Form, die [slice-schreibpfad-merge-output](slice-schreibpfad-merge-output.md) festlegt: Treffer, kein Treffer, mehrere Zeilen, Fehlerfall; Tokenfolge, Ergebnisspalte `$action`, Zähler und Abschluss. Zeilen im Messprotokoll mit der nächsten freien Nummer.
- [ ] **Messreihe Katalog und Strukturänderung** ([TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend); Träger der Verifikation V-1 von [slice-katalog-und-ddl-zustand](../done/slice-katalog-und-ddl-zustand.md)): am echten SQL Server (2022 und 2025, Digest-Pin) gemessen, was der Katalog-Slice gegen den Testserver nur als Setzung führt: (a) die Typen der Katalogspalten `max_length`, `precision`, `scale` (am Referenzclient laut M-1 `smallint` und `tinyint`) für die Abfragen von d-migrate 1.12.0, die der Mitschnitt in `DMigrateWire.kt` führt; (b) Typnamen, Byte, Precision und Skala von `bigint`, `bit`, `time`, `datetime2` in `sys.columns` und `sys.types`; (c) `max_length` -1 für `NVARCHAR(MAX)`; (d) Fehlernummern, Class und State der Strukturabweisung (`CREATE TABLE` einer vorhandenen Tabelle, `ADD` einer vorhandenen Spalte, `CREATE TABLE` ohne Schlüssel, `ALTER TABLE` einer unbekannten Tabelle); (e) die Antworten der Katalogabfragen für Indizes, Standardwerte, Prüfbedingungen und Fremdschlüssel einer Tabelle mit je einem Fall als Eingang des Folge-Slice [slice-katalog-indizes-defaults-fremdschluessel](slice-katalog-indizes-defaults-fremdschluessel.md); (f) der Änderungszähler von `CREATE TABLE` und `ALTER TABLE ... ADD` (der Bau setzt 0). Zeilen im Messprotokoll mit der nächsten freien Nummer, getrennt vom Messprotokoll `katalog-und-ddl-zustand-2026-11-06` (Test-Server) und mit Verweis darauf.
- [ ] **Zuordnung und Zusatzmessung** ([TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)): Je Befund steht im Messprotokoll, ob er eine Setzung der Spezifikation (`SPEC-035`, `SPEC-036`) stützt oder ihr widerspricht (Konflikt mit dem Zielbild, Träger: der Bau-Slice, beim Katalog [slice-katalog-indizes-defaults-fremdschluessel](slice-katalog-indizes-defaults-fremdschluessel.md)); die Bau-Slices tragen den Zeiger auf diese Zeilen; das Register `BEO-TDS/messung-am-echten-sql-server-fehlt` ist fortgeschrieben. Grenze „ein Fall je Instanz“ steht bei jeder Zeile. **Zusatzmessung aus [slice-handle-freigabe-und-grenze-bauen](../done/slice-handle-freigabe-und-grenze-bauen.md)** (Auflösungs-Trigger der Setzung in `SPEC-060`, Register `BEO-TDS/obergrenze-gemeldete-nummern-je-batch-weicht-vom-echten-server-ab`): `EXEC sp_unprepare <n>` mit sehr vielen unbekannten Nummern in einem Batch (Größenordnung 150 und mehr, gestuft) gegen beide Instanzen; Zeile im Messprotokoll neben M-7: meldet der echte Server jede Nummer, oder begrenzt er, und ab welcher Zahl? Ergebnis stützt die Setzung (100) oder widerspricht ihr; im Widerspruch ist der Folgeweg eine Angleichung der Spezifikation und des Bau-Pfads, der Auftrag dazu geht an den Nutzer. **Zusatzmessung aus [slice-antwort-tokens-an-messung-angleichen](../done/slice-antwort-tokens-an-messung-angleichen.md)** (Ausnahme A-20 des Messprotokolls, Register `BEO-TDS/zielbild-luecken-der-antwort-tokens`): das Flag-Feld des RETURNVALUE eines NULL-Ausgabewerts und der übrigen Typen (`bigint`, `bit`, `decimal`, `nvarchar`, `date`, `datetime2`) bei `sp_executesql` mit OUTPUT-Parametern gegen beide Instanzen; gemessen ist heute nur `INTN` 4 mit Wert (22 von 22 Treffern je Instanz `0x0000`). Das Ergebnis stützt oder widerspricht der Regel „nicht nullbar, `0x0000`“; im Widerspruch geht der Auftrag zum Bau an den Nutzer. **Zweiter Zusatz aus demselben Slice (Nutzerentscheid 2026-11-07, Zählung der Liefer-Punkte unverändert):** die Befehlskennung `curCmd` der DONEINPROC-Abschlüsse von `EXEC sp_unprepare <n>` (Batch ohne Ergebnis, mit Handle aus `sp_prepexec`) gegen beide Instanzen; der Wert steht heute nicht im Messprotokoll (der Server dieses Repos schreibt 0). Der Bau der Zielbild-Lücken der Antwort-Tokens (Flag-Feld A-20, `curCmd` des Update-Zählers, `curCmd` nach Fristablauf, `curCmd` von `EXEC sp_unprepare`) hat keinen Bau-Slice; Re-Evaluierungs-Trigger: nach der A-20-Messung in diesem Slice wird der Bau-Slice angelegt.

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
| `referenztest-dotnet/Vergleich/` (Messszenarien Massenladen und MERGE, Muster `MessungWireTests.cs`) | neu / update | Aufnahme der Szenarien am Draht, nur lokal |
| `referenztest-dotnet/Mitschnitt/messung/{2022,2025}/` | neu | redigierte Rohbytes je Szenario und Instanz |
| `referenztest-dotnet/Vergleich/` (Messszenarien Katalog und Strukturänderung: Abfragen aus `src/test/kotlin/.../simulation/DMigrateWire.kt`, `CREATE TABLE`, `ALTER TABLE ... ADD`) | neu | Aufnahme der Katalogantworten und der Strukturabweisung am Draht, nur lokal |
| `docs/messungen/referenzabgleich.md` | update | Messzeilen, Zuordnung zu Setzung oder Konflikt ([TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)) |
| `docs/plan/planning/observations/BEO-TDS/messung-am-echten-sql-server-fehlt/` | update | Stand und Evidence |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-katalog-und-ddl-zustand](../done/slice-katalog-und-ddl-zustand.md) liegt in `done/` (erfüllt mit der Closure des Katalog-Slice; Setzung des Planners: die Szenarien legen Tabellen über DDL an und lesen deren Form wie der Katalog-Slice; der Katalog-Slice liefert die Abfragen in `DMigrateWire.kt`, die dieser Slice gegen den echten Server wiederholt), die EULA für das gepinnte Image ist vom Nutzer bestätigt (2026-11-06), und ein Verantwortlicher ist eingetragen. Die beiden Schreib-Slices starten nicht an diesem Slice; liegt er vor ihrem Bau in `done/`, folgen sie der Messung, sonst bleibt ihre Antwort Setzung und wird nachträglich verglichen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte oder die Aufnahme von Paket 0x07 braucht Werkzeugarbeit; Schnittvorschlag: Massenladen | MERGE | Katalog (der Katalog-Teil bleibt Träger der Verifikation V-1 und geht nicht verloren, er wechselt höchstens in einen eigenen Slice, der dann den Eintrag im Register übernimmt).
- `in-progress` → `open` (blockiert): das gepinnte Image startet nicht oder die EULA-Bestätigung entfällt; oder der Aufnahme-Proxy trägt Paket 0x07 nicht (dann Entscheid des Nutzers über die Lesart).

## 5. Closure-Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle Liefer-Punkte der DoD erledigt (auch die Katalogmessung), `make gates` grün (der Messlauf selbst ist kein Gate), der Review-Report liegt vor, und die Closure-Notiz trägt je Befund die Zuordnung *Messung | Festlegung*, die Grenzen (zwei Instanzen, Digests, Client, TDS-Modus, Host) und den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **Paket 0x07 im Aufnahmewerkzeug.** Ob Proxy und Parser der Messdateien das Massenladen tragen, ist nicht erprobt. — **Ausgang:** offen bis zur Closure.
- **Referenzclient gegen d-migrate.** `Microsoft.Data.SqlClient` sendet nicht den Text und die Pakete von d-migrate; die Messung trägt nur den Referenzclient (`AGENTS.md` §3.8). — **Ausgang:** offen bis zur Closure.
- **Einzelmessung je Instanz.** Ein Fall je Szenario und Instanz, keine Verteilung; Abweichungen zwischen 2022 und 2025 sind Befund, nicht Rauschen. — **Ausgang:** offen bis zur Closure.
- **Abhängigkeit vom Katalog-Slice ist eine Setzung des Planners.** — **Ausgang:** offen bis zur Closure.
- **Katalogmessung braucht eigenen Messcode.** Der Referenzclient (`Microsoft.Data.SqlClient`) stellt die Katalogabfragen von d-migrate nicht von selbst; sie werden mit dem Wortlaut aus `DMigrateWire.kt` als Text gesendet. Gemessen wird die Antwort auf den Text, nicht das Verhalten von d-migrate oder mssql-jdbc (`AGENTS.md` §3.8). — **Ausgang:** offen bis zur Closure.
- **Zielbild-Abweichungen des Katalog-Slice (Verifikation V-1) hängen an diesem Slice.** Entfällt der Lauf (EULA), bleiben `max_length` als `Int32`, die Setzungen für `bigint`, `bit`, `time`, `datetime2` und die Fehlerarten der Strukturabweisung ohne Messung, benannt in `SPEC-035`; das Register `BEO-TDS/messung-am-echten-sql-server-fehlt` bleibt dann der Träger. — **Ausgang:** offen bis zur Closure.
- **Obergrenze der gemeldeten Nummern je Batch ist ohne Messung.** Die Zusatzmessung in Liefer-Punkt 3 hängt an derselben Instanz-Freigabe (EULA, Nutzer) wie die Messreihen; entfällt der Lauf, bleibt die Setzung in `SPEC-060` mit ihrem Auflösungs-Trigger stehen und das Register `BEO-TDS/obergrenze-gemeldete-nummern-je-batch-weicht-vom-echten-server-ab` offen. — **Ausgang:** offen bis zur Closure.
- **Flag-Feld der RETURNVALUE nur für `INTN` 4 mit Wert gemessen (Zusatz aus Liefer-Punkt 3).** Entfällt der Lauf (EULA), bleibt A-20 des Messprotokolls ohne Messung für NULL und die übrigen Typen und das Register `BEO-TDS/zielbild-luecken-der-antwort-tokens` offen. — **Ausgang:** offen bis zur Closure.
- **`curCmd` von `EXEC sp_unprepare` ungemessen (zweiter Zusatz aus Liefer-Punkt 3).** Entfällt der Lauf (EULA), bleibt die Lücke ohne Messwert und das Register `BEO-TDS/zielbild-luecken-der-antwort-tokens` offen. — **Ausgang:** offen bis zur Closure.

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

Wird bei der Closure gefüllt.

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung — dort die **zwei vorgelagerten
Schritte** (sie stehen in jedem Slice-Plan, unabhängig von Modus und
Slice-Typ) und die **vier Pflichtkriterien** (Konventionen-Dichte ·
Phase-Reife · Evidenz-/Diskrepanz-Risiko · Reconciliation-Aufwand), vier und
nicht mehr.

**Der Abschnitt selbst entfällt nie.** Die zwei vorgelagerten Prüfungen laufen
in **jedem** Slice-Plan — sie hängen weder am Modus noch am Slice-Typ. Bedingt
ist allein der Modus-Begründungsblock am Ende; deshalb nennt der Titel beide
Hälften.

**Vorgelagert — Sub-Area-Wahl prüfen:** `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); der Slice berührt Messszenarien des Referenzclients, Messdateien und das Messprotokoll, eine eigene Sub-Area besteht nicht.

**Vorgelagert — offene Beobachtungen sichten:** `BEO-TDS/messung-am-echten-sql-server-fehlt` (Gegenstand dieses Slice, jetzt auch die Katalogantworten), `BEO-TDS/aussagen-ueber-messung-hinaus` (verkörpert), `BEO-TDS/mitschnitt-luecken-ohne-traeger` (Ausnahmeliste, soweit Zeilen entstehen).

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
