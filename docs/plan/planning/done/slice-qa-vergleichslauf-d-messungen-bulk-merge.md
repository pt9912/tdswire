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

**Berührte Spec-Stellen:** SPEC-036 (`INSERT BULK`, `SET FMTONLY`, Form des `MERGE`, Katalogabfragen und DDL-Formen; dort heute Setzungen ohne Messung) · SPEC-035 (Katalog der Spalten: Typen der Katalogspalten, Typnamen und Maße von `bigint`, `bit`, `time`, `datetime2`, Sonderwert `maxLength` -1, Fehler der Strukturabweisung) · Protokollteil Pakettypen und Dispatch (Paket 0x07) · SPEC-045 (Ausnahmeliste, soweit Zeilen entstehen) · SPEC-060 (nur der Messvermerk der Obergrenze, falls die Zusatzmessung sie bestätigt oder widerlegt) · SPEC-030 (nur der Messvermerk zum Flag-Feld der Ausgabewerte, A-20). Die Spezifikation ändern die Bau-Slices [slice-schreibpfad-bulk-load](../done/slice-schreibpfad-bulk-load.md) und [slice-schreibpfad-merge-output](../done/slice-schreibpfad-merge-output.md) und, für den Katalog, ein Bau-Slice nach dem Befund (Folge-Slice [slice-katalog-indizes-defaults-fremdschluessel](../in-progress/slice-katalog-indizes-defaults-fremdschluessel.md) für Indizes, Standardwerte, Prüfbedingungen, Fremdschlüssel), nicht dieser Slice.

**Verantwortlich:** Implementer-Agent, Auftrag des Nutzers 2026-11-07.

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

- Der Bau der Katalogsichten für Indizes, Standardwerte, Prüfbedingungen und Fremdschlüssel — [slice-katalog-indizes-defaults-fremdschluessel](../in-progress/slice-katalog-indizes-defaults-fremdschluessel.md); dieser Slice misst die Antworten, die der Katalog-Slice schon gebaut hat (Spalten, Schlüssel, Strukturänderung), und liefert dem Folge-Slice die Messung der übrigen Sichten als Eingang, soweit sie dieselbe Messreihe trägt.
- Eine Änderung des Katalogs im Server dieses Repos nach dem Befund — der Befund ist Eingang eines Bau-Slice; ein Widerspruch zum Zielbild geht als Auftrag an den Nutzer (Spezifikation ist Zielbild).
- Der Bau von Massenladen und MERGE im Server dieses Repos — [slice-schreibpfad-bulk-load](../done/slice-schreibpfad-bulk-load.md) und [slice-schreibpfad-merge-output](../done/slice-schreibpfad-merge-output.md); dieser Slice misst, er ändert weder Quelltext von `src/` noch die Spezifikation.
- Ein Gate oder eine Aufnahme in `make gates` — der Vergleichslauf D bleibt lokal, ohne CI, mit EULA-Entscheidung des Nutzers ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md): kein Gate); die Messdateien sind Belege, kein Sensor.
- Last, Durchsatz und große Mengen — [slice-qa-performance](../open/slice-qa-performance.md); die Messung trägt Form und Zähler, nicht Zeit.
- Eine Aussage über andere Clients als den Referenzclient — der Referenzclient (`Microsoft.Data.SqlClient`) ist nicht d-migrate; die Messung trägt die Antwort auf den Text und die Pakete, die sie sendet.

## 2. Definition of Done


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [x] **Messreihe Schreibpfad: Massenladen und MERGE** ([TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate)): (a) Massenladen: `SqlBulkCopy` gegen beide Instanzen am Draht aufgenommen (Muster: `referenztest-dotnet/Vergleich/MessungWireTests.cs`, Mitschnitt unter `referenztest-dotnet/Mitschnitt/messung/{2022,2025}/`, redigiert): Quelle mit mehreren Typen und NULL, Quelle über mehrere Nachrichten, leere Quelle, Verstoß gegen Typ oder Länge; je Szenario Tokenfolge, Statusbits und Zähler der Antwort und die Ausnahme des Clients. (b) MERGE: `MERGE ... OUTPUT $action` mit Parametern am Draht aufgenommen, mit der Form, die [slice-schreibpfad-merge-output](../done/slice-schreibpfad-merge-output.md) festlegt: Treffer, kein Treffer, mehrere Zeilen, Fehlerfall; Tokenfolge, Ergebnisspalte `$action`, Zähler und Abschluss. Zeilen im Messprotokoll mit der nächsten freien Nummer.
- [x] **Messreihe Katalog und Strukturänderung** ([TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend); Träger der Verifikation V-1 von [slice-katalog-und-ddl-zustand](../done/slice-katalog-und-ddl-zustand.md)): am echten SQL Server (2022 und 2025, Digest-Pin) gemessen, was der Katalog-Slice gegen den Testserver nur als Setzung führt: (a) die Typen der Katalogspalten `max_length`, `precision`, `scale` (am Referenzclient laut M-1 `smallint` und `tinyint`) für die Abfragen von d-migrate 1.12.0, die der Mitschnitt in `DMigrateWire.kt` führt; (b) Typnamen, Byte, Precision und Skala von `bigint`, `bit`, `time`, `datetime2` in `sys.columns` und `sys.types`; (c) `max_length` -1 für `NVARCHAR(MAX)`; (d) Fehlernummern, Class und State der Strukturabweisung (`CREATE TABLE` einer vorhandenen Tabelle, `ADD` einer vorhandenen Spalte, `CREATE TABLE` ohne Schlüssel, `ALTER TABLE` einer unbekannten Tabelle); (e) die Antworten der Katalogabfragen für Indizes, Standardwerte, Prüfbedingungen und Fremdschlüssel einer Tabelle mit je einem Fall als Eingang des Folge-Slice [slice-katalog-indizes-defaults-fremdschluessel](../in-progress/slice-katalog-indizes-defaults-fremdschluessel.md); (f) der Änderungszähler von `CREATE TABLE` und `ALTER TABLE ... ADD` (der Bau setzt 0). Zeilen im Messprotokoll mit der nächsten freien Nummer, getrennt vom Messprotokoll `katalog-und-ddl-zustand-2026-11-06` (Test-Server) und mit Verweis darauf.
- [x] **Zuordnung und Zusatzmessung** ([TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)): Je Befund steht im Messprotokoll, ob er eine Setzung der Spezifikation (`SPEC-035`, `SPEC-036`) stützt oder ihr widerspricht (Konflikt mit dem Zielbild, Träger: der Bau-Slice, beim Katalog [slice-katalog-indizes-defaults-fremdschluessel](../in-progress/slice-katalog-indizes-defaults-fremdschluessel.md)); die Bau-Slices tragen den Zeiger auf diese Zeilen ([slice-schreibpfad-merge-output](../done/slice-schreibpfad-merge-output.md) und [slice-katalog-indizes-defaults-fremdschluessel](../in-progress/slice-katalog-indizes-defaults-fremdschluessel.md) in diesem Zweig; **Abweichung vom Plan-Wortlaut:** der Zeiger in [slice-schreibpfad-bulk-load](../done/slice-schreibpfad-bulk-load.md) liegt in einem anderen Zweig und ist Übergabe an dessen Implementer, getragen im Stand des Registers `BEO-TDS/messung-am-echten-sql-server-fehlt`); das Register `BEO-TDS/messung-am-echten-sql-server-fehlt` ist fortgeschrieben. Grenze „ein Fall je Instanz“ steht bei jeder Zeile. **Zusatzmessung aus [slice-handle-freigabe-und-grenze-bauen](../done/slice-handle-freigabe-und-grenze-bauen.md)** (Auflösungs-Trigger der Setzung in `SPEC-060`, Register `BEO-TDS/obergrenze-gemeldete-nummern-je-batch-weicht-vom-echten-server-ab`): `EXEC sp_unprepare <n>` mit sehr vielen unbekannten Nummern in einem Batch (Größenordnung 150 und mehr, gestuft) gegen beide Instanzen; Zeile im Messprotokoll neben M-7: meldet der echte Server jede Nummer, oder begrenzt er, und ab welcher Zahl? Ergebnis stützt die Setzung (100) oder widerspricht ihr; im Widerspruch ist der Folgeweg eine Angleichung der Spezifikation und des Bau-Pfads, der Auftrag dazu geht an den Nutzer. **Zusatzmessung aus [slice-antwort-tokens-an-messung-angleichen](../done/slice-antwort-tokens-an-messung-angleichen.md)** (Ausnahme A-20 des Messprotokolls, Register `BEO-TDS/zielbild-luecken-der-antwort-tokens`): das Flag-Feld des RETURNVALUE eines NULL-Ausgabewerts und der übrigen Typen (`bigint`, `bit`, `decimal`, `nvarchar`, `date`, `datetime2`) bei `sp_executesql` mit OUTPUT-Parametern gegen beide Instanzen; gemessen ist heute nur `INTN` 4 mit Wert (22 von 22 Treffern je Instanz `0x0000`). Das Ergebnis stützt oder widerspricht der Regel „nicht nullbar, `0x0000`“; im Widerspruch geht der Auftrag zum Bau an den Nutzer. **Zweiter Zusatz aus demselben Slice (Nutzerentscheid 2026-11-07, Zählung der Liefer-Punkte unverändert):** die Befehlskennung `curCmd` der DONEINPROC-Abschlüsse von `EXEC sp_unprepare <n>` (Batch ohne Ergebnis, mit Handle aus `sp_prepexec`) gegen beide Instanzen; der Wert steht heute nicht im Messprotokoll (der Server dieses Repos schreibt 0). Der Bau der Zielbild-Lücken der Antwort-Tokens (Flag-Feld A-20, `curCmd` des Update-Zählers, `curCmd` nach Fristablauf, `curCmd` von `EXEC sp_unprepare`) hat seinen Bau-Slice: [slice-antwort-tokens-zielbild-luecken-bauen](../done/slice-antwort-tokens-zielbild-luecken-bauen.md) (Trigger „nach der A-20-Messung“ eingetreten, Nutzerentscheid 2026-11-07; Umplanung im Drift-Log der Roadmap).

Nachweis-Pflichten für alle Punkte:

- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8). Die Nachbesserung `bd508880` und `4d73a9d2` hat keinen zweiten Review (siehe §7).
- [x] Haken der Liefer-Punkte setzt der Planner nach der Verifikation, nicht der Implementer (`BEO-TDS/haken-vor-verifikation-gesetzt`).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

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

Ergänzt im ersten Lauf (Implementer-Agent, 2026-11-07; Abgleich des Ist-Zustands gegen den Plan vor dem Editieren: `MessungWireTests.cs` zählt 22 Szenarien und `MessungBelegTests` prüft genau 22 Dateien je Stufe, deshalb eine eigene Kategorie und eine eigene Ablage statt Szenarien in der vorhandenen Klasse):

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `referenztest-dotnet/Vergleich/MessungBulkTests.cs` (Kategorie `MessungBulk`, 16 Szenarien) | neu | Erzeuger der Messreihe in allen drei Liefer-Punkten; Muster `MessungWireTests.cs`, gleiche Bausteine (`TlsAufnahme`, `MessDatei`) |
| `referenztest-dotnet/Vergleich/DMigrateTexte.cs` | neu | Wortlaut der Texte aus `DMigrateWire.kt` für den Referenzclient (die Stage `vergleich` kopiert nur `Mitschnitt` und `Vergleich`) |
| `referenztest-dotnet/Mitschnitt/messung/bulk-merge-katalog/{2022,2025}/` | neu | 16 redigierte Messdateien je Instanz, getrennt von der Ablage der Messreihe am Draht |
| `referenztest-dotnet/Mitschnitt/MessungBulkBelegTests.cs` | neu | Belege der Messprotokollzeilen M-8 bis M-24 ohne Referenzinstanz (`make referenztest-dotnet`) |
| `referenztest-dotnet/Mitschnitt/TokenLeser.cs`, `MitschnittEinheitTests.cs` | update, neu | Flag-Feld der RETURNVALUE in den Einzelheiten der Messung (nicht im Vergleich), Einheitstest dazu |
| `tools/vergleichslauf-d.sh`, `tools/mk/vergleichslauf-d.mk` | update | Läufe `bulk`, `bulk-2022`, `bulk-2025` (Kategorie `MessungBulk`, Ablage `messung/bulk-merge-katalog/<Stufe>/`) |
| `spec/spezifikation.md` (`SPEC-030`, `SPEC-035`, `SPEC-036`, `SPEC-060`) | update | Messvermerke; Setzungen bleiben, Konflikte stehen im Messprotokoll als Entscheidungsvorlage |
| `docs/plan/planning/observations/BEO-TDS/{messung-am-echten-sql-server-fehlt,obergrenze-gemeldete-nummern-je-batch-weicht-vom-echten-server-ab,zielbild-luecken-der-antwort-tokens}/state.md` | update | Stand der drei Einträge |
| `docs/plan/spec-abdeckung.md` | update (erzeugt) | Tests geändert, `make spec-abdeckung` |

Ergänzt in der Closure (Planner, 2026-11-07; Abgleich Plan-Tabelle gegen Diff nach den Behebungscommits `bd508880` und `4d73a9d2`, Verifikation V-6):

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `referenztest-dotnet/Mitschnitt/Messung.cs` (`Pumpe`, `PaketweiseSchreiben`) | update | Proxy schreibt jedes TDS-Paket in einem eigenen Record in beiden Richtungen (Fix des Abbruchs von `bulk-mehrere-nachrichten`) |
| `tools/vergleichslauf-d.sh` (Wiederholungslauf) | update | Exit 0 bei grünem Test ohne Ablage (V-3) |
| `docs/plan/planning/observations/BEO-TDS/{katalog-strukturabweisung-und-aenderungszaehler-weichen-vom-echten-server-ab,folge-adr-zu-adr-0033-massenladen-verstoss-und-teilbestand,messlauf-intermittierend-rot-ohne-rate-im-protokoll}/` | neu | Träger der Konflikte M-12, M-16, M-19, M-20; Flake des Messwerkzeugs |
| `docs/plan/planning/observations/BEO-TDS/*/evidence/slice-qa-vergleichslauf-d-messungen-bulk-merge.md` | neu | Belege der Closure in 11 Einträgen |
| `docs/messungen/katalog-und-ddl-zustand-2026-11-06.md` | update | Gegenmessung liegt vor (Nachbarprotokoll) |
| `docs/plan/planning/done/slice-schreibpfad-merge-output.md`, `…/slice-katalog-indizes-defaults-fremdschluessel.md`, `…/slice-abnahme-d-migrate.md` | update | Zeiger auf die Messzeilen, Messauftrag mit dem JDBC-Client, Abnahme-Bezug |
| `docs/plan/planning/open/slice-{katalog-und-struktur-an-messung-angleichen,sp-unprepare-ohne-obergrenze,adr-massenladen-atomar-an-messung,antwort-tokens-zielbild-luecken-bauen}.md` | neu | Bau-Slices und ADR-Slice nach den Nutzerentscheiden 2026-11-07 |
| `docs/plan/planning/welle-testserver-manuell.md`, `docs/plan/planning/in-progress/roadmap.md` | update | Slices der Welle, Drift-Log, Zustandsfelder |
| `spec/spezifikation.md` (`SPEC-060`) | update | Messvermerk statt „Setzung, nicht gemessen“, Nutzerentscheid als Beleg im Register |

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

- **Paket 0x07 im Aufnahmewerkzeug.** Ob Proxy und Parser der Messdateien das Massenladen tragen, ist nicht erprobt. — **Ausgang:** entfallen: getragen. Der Proxy trägt Paket 0x07; der Abbruch von `bulk-mehrere-nachrichten` (Records mit mehreren Paketen) ist durch `PaketweiseSchreiben` behoben (Mutation 18 von 20 rot, Fix 64 von 64 grün, Verifikation); Register `BEO-TDS/messlauf-intermittierend-rot-ohne-rate-im-protokoll`.
- **Referenzclient gegen d-migrate.** `Microsoft.Data.SqlClient` sendet nicht den Text und die Pakete von d-migrate; die Messung trägt nur den Referenzclient (`AGENTS.md` §3.8). — **Ausgang:** eingetreten, weiter offen: Gemessen ist der Referenzclient, nicht mssql-jdbc. Träger: Messauftrag mit dem JDBC-Client (MERGE-Wortlaut von d-migrate, INT-Spalten; Folgepflicht 2 von [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md)) in [slice-schreibpfad-merge-output](../done/slice-schreibpfad-merge-output.md) (Nutzerentscheid 2026-11-07), Register `BEO-TDS/messung-am-echten-sql-server-fehlt`.
- **Einzelmessung je Instanz.** Ein Fall je Szenario und Instanz, keine Verteilung; Abweichungen zwischen 2022 und 2025 sind Befund, nicht Rauschen. — **Ausgang:** eingetreten, Grenze genannt: ein Fall je Szenario und Instanz bei jeder Zeile; der eine serverseitige Verstoß (M-12) ist Allaussage-Gefahr (Verifikation V-4), die Erweiterung trägt [slice-adr-massenladen-atomar-an-messung](../done/slice-adr-massenladen-atomar-an-messung.md).
- **Abhängigkeit vom Katalog-Slice ist eine Setzung des Planners.** — **Ausgang:** entfallen: [slice-katalog-und-ddl-zustand](../done/slice-katalog-und-ddl-zustand.md) liegt in `done/`, die Szenarien liefen auf seinem Aufbau.
- **Katalogmessung braucht eigenen Messcode.** Der Referenzclient (`Microsoft.Data.SqlClient`) stellt die Katalogabfragen von d-migrate nicht von selbst; sie werden mit dem Wortlaut aus `DMigrateWire.kt` als Text gesendet. Gemessen wird die Antwort auf den Text, nicht das Verhalten von d-migrate oder mssql-jdbc (`AGENTS.md` §3.8). — **Ausgang:** eingetreten: die Texte stehen in `DMigrateTexte.cs` (Wortlaut gleich `DMigrateWire.kt`, maschinell geprüft, 24 Texte); die Kopplung hält kein Sensor, Träger `BEO-TDS/live-lauf-ausserhalb-der-gates-haelt-kopplungen-nicht` (V-5).
- **Zielbild-Abweichungen des Katalog-Slice (Verifikation V-1) hängen an diesem Slice.** Entfällt der Lauf (EULA), bleiben `max_length` als `Int32`, die Setzungen für `bigint`, `bit`, `time`, `datetime2` und die Fehlerarten der Strukturabweisung ohne Messung, benannt in `SPEC-035`; das Register `BEO-TDS/messung-am-echten-sql-server-fehlt` bleibt dann der Träger. — **Ausgang:** eingetreten, weiter offen: M-16, M-19, M-20 widersprechen dem Bau; Träger [slice-katalog-und-struktur-an-messung-angleichen](slice-katalog-und-struktur-an-messung-angleichen.md), Register `BEO-TDS/katalog-strukturabweisung-und-aenderungszaehler-weichen-vom-echten-server-ab`.
- **Obergrenze der gemeldeten Nummern je Batch ist ohne Messung.** Die Zusatzmessung in Liefer-Punkt 3 hängt an derselben Instanz-Freigabe (EULA, Nutzer) wie die Messreihen; entfällt der Lauf, bleibt die Setzung in `SPEC-060` mit ihrem Auflösungs-Trigger stehen und das Register `BEO-TDS/obergrenze-gemeldete-nummern-je-batch-weicht-vom-echten-server-ab` offen. — **Ausgang:** eingetreten, weiter offen: M-22 widerspricht der Setzung 100; Träger [slice-sp-unprepare-ohne-obergrenze](slice-sp-unprepare-ohne-obergrenze.md), Register `BEO-TDS/obergrenze-gemeldete-nummern-je-batch-weicht-vom-echten-server-ab`.
- **Flag-Feld der RETURNVALUE nur für `INTN` 4 mit Wert gemessen (Zusatz aus Liefer-Punkt 3).** Entfällt der Lauf (EULA), bleibt A-20 des Messprotokolls ohne Messung für NULL und die übrigen Typen und das Register `BEO-TDS/zielbild-luecken-der-antwort-tokens` offen. — **Ausgang:** entfallen: M-24 misst 10 Typen mit Wert, NULL und ohne Zuweisung, alle `0x0000`; der Bau der Angleichung liegt bei [slice-antwort-tokens-zielbild-luecken-bauen](../done/slice-antwort-tokens-zielbild-luecken-bauen.md), Register `BEO-TDS/zielbild-luecken-der-antwort-tokens`.
- **`curCmd` von `EXEC sp_unprepare` ungemessen (zweiter Zusatz aus Liefer-Punkt 3).** Entfällt der Lauf (EULA), bleibt die Lücke ohne Messwert und das Register `BEO-TDS/zielbild-luecken-der-antwort-tokens` offen. — **Ausgang:** entfallen: M-23 misst `DONEPROC` Befehl `0x00e0` (nicht `DONEINPROC`), dazu die Tokenfolge; der Bau liegt bei [slice-antwort-tokens-zielbild-luecken-bauen](../done/slice-antwort-tokens-zielbild-luecken-bauen.md).

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

Closure am 2026-11-07 (Planner). Zuerst der Stand der Rollen, danach die Entscheide und die Closure-Teile.

**Belege der Rollen.** Umsetzung `a865d2da` und `2c6dd2d4`, Nachbesserung `bd508880` (Proxy der Messreihe, Fehlersuche mit Wiederholungen, Belegtests) und `4d73a9d2` (Messprotokoll, Register, Messvermerke, Zeiger). Review: [2026-11-07-2c6dd2d4-review-slice-qa-vergleichslauf-d-messungen-bulk-merge](../../../reviews/2026-11-07-2c6dd2d4-review-slice-qa-vergleichslauf-d-messungen-bulk-merge.md) (1 HIGH, 6 MEDIUM, 1 LOW, 3 INFO). Verifikation: [2026-11-07-4d73a9d2-verifikation-slice-qa-vergleichslauf-d-messungen-bulk-merge](../../../reviews/2026-11-07-4d73a9d2-verifikation-slice-qa-vergleichslauf-d-messungen-bulk-merge.md) (0 HIGH, 3 MEDIUM, 4 LOW, 4 INFO): Messdaten, Instanz-Gleichheit, Redaktion und Mutationen tragen, die Zeilen M-8 bis M-24 sind in eigenen Läufen auf beiden Instanzen reproduziert; „nicht closure-fähig“ bis zu den Bedingungen 1 bis 4, nicht wegen der Messwerte. **Nachbesserung ohne zweiten Review:** die Commits `bd508880` und `4d73a9d2` entstanden nach dem Review; der Verifier las und fuhr sie (64 von 64 Läufe grün nach dem Fix, Mutation 18 von 20 rot), ein Re-Review mit dem Reviewer-Skill hat nicht stattgefunden. Das ist eine Grenze dieser Closure (Nutzerentscheid-Vorschlag, hier als Vorschlag geführt, Entscheid beim Nutzer; Register `BEO-TDS/nachbesserung-ohne-zweiten-review`, jetzt 4 Belege).

**Nutzerentscheide vom 2026-11-07 (per Rückfrage an den Nutzer, Beleg in den Stand-Zellen der Register-Einträge).** (1) [slice-katalog-und-struktur-an-messung-angleichen](slice-katalog-und-struktur-an-messung-angleichen.md): Katalogspalten `max_length` smallint, `precision` und `scale` tinyint, `is_nullable` bit nullbar; Strukturabweisung 2714, 2705, 4902; `CREATE TABLE` ohne Schlüssel erlaubt; Änderungszähler `DONE` ohne `COUNT` (Befehl `0x00c6`, `0x00d8`), `implicit_transactions` mit ENVCHANGE Typ 8 und 9 (Register `BEO-TDS/katalog-strukturabweisung-und-aenderungszaehler-weichen-vom-echten-server-ab`). (2) [slice-sp-unprepare-ohne-obergrenze](slice-sp-unprepare-ohne-obergrenze.md): Obergrenze 100 aufheben, `SPEC-060` an den Messwert angleichen (Teil des Slice), Schutz nach [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität) anders tragen (Register `BEO-TDS/obergrenze-gemeldete-nummern-je-batch-weicht-vom-echten-server-ab`). (3) [slice-adr-massenladen-atomar-an-messung](../done/slice-adr-massenladen-atomar-an-messung.md): Folge-ADR (Architect, `Proposed`, `Supersedes (teilweise)` [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 5 (iii) und Nr. 6) (Register `BEO-TDS/folge-adr-zu-adr-0033-massenladen-verstoss-und-teilbestand`). (4) [slice-antwort-tokens-zielbild-luecken-bauen](../done/slice-antwort-tokens-zielbild-luecken-bauen.md): Flag-Feld `0x0000`, `curCmd` des Update-Zählers, von `EXEC sp_unprepare` und nach Fristablauf, Tokenfolge von `sp_unprepare` (Register `BEO-TDS/zielbild-luecken-der-antwort-tokens`; Umplanung im Drift-Log der Roadmap). (5) MERGE-Wortlaut von d-migrate und INT-Spalten (Folgepflicht 2 von [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md)) mit dem JDBC-Client nachmessen: Messauftrag in [slice-schreibpfad-merge-output](../done/slice-schreibpfad-merge-output.md) (Zählung der Liefer-Punkte bleibt 3, Zerlegung dort benannt). Alle vier neuen Slices haben Start-Trigger, Rückführungen und den Bezug zu [slice-abnahme-d-migrate](../open/slice-abnahme-d-migrate.md) (drei sind Grenze, der ADR-Slice ist Eingang des Massenladens und damit mittelbar Voraussetzung); die Welle ist `welle-testserver-manuell`, womit sie Bedingung ihres Closure-Triggers sind.

**Entscheide des Planners zu den Bedingungen und Findings der Verifikation.**

| Finding | Entscheid | Ausgang im Text |
|---|---|---|
| V-1 (MEDIUM) Zeiger in den Bau-Slices | Sätze im Zustand vor der Messung gezogen („gemessen, Zeilen M-13 bis M-15“ bzw. M-16 bis M-21), in [slice-schreibpfad-merge-output](../done/slice-schreibpfad-merge-output.md) als „Abweichung vom Plan-Wortlaut“; [slice-schreibpfad-bulk-load](../done/slice-schreibpfad-bulk-load.md) nicht angefasst (anderer Zweig, `worktree-agent-a4845d02c1beaf126`): **Übergabe** an dessen Implementer, getragen im Stand von `BEO-TDS/messung-am-echten-sql-server-fehlt` (Gegenstände unten) | DoD Punkt 3 (Abweichung benannt), die zwei Slices, Register |
| V-2 (MEDIUM) Träger der sieben Konflikte, Umplanung | Bau-Slice angelegt (Nutzerentscheide 1 bis 4); Umplanung im Drift-Log der Roadmap; §2 nennt den Bau-Slice | vier Slices in `open/`, Roadmap, Welle, Register |
| V-3 (MEDIUM) Exit 2 trotz „0 rot“ | Exit-Logik korrigiert: bei `MESSUNG_WIEDERHOLEN` > 0 zählt allein der Test (keine Ablage, keine Prüfung „Messungen vorhanden“); Mutation (alter Stand) Exit 2 und `ROT`, Fix Exit 0 (`MESSUNG_NUR=bulk-leer MESSUNG_WIEDERHOLEN=3`, Instanz 2022); der Wiederholungspfad von `vergleichslauf-d.sh` hat im `make skript-selbsttest` keinen Fall (er braucht Docker und die EULA-Variable), das ist Grenze | `tools/vergleichslauf-d.sh`, Messprotokoll („endet mit Exit 0“) |
| V-4 (LOW) `SPEC-060` Klammer, „vorgelegt“, M-12 Allaussage | „Setzung, nicht gemessen“ durch „Setzung; der Messwert widerspricht ihr“ ersetzt; „dem Nutzer vorgelegt“ durch den Nutzerentscheid 2026-11-07 mit Beleg im Register ersetzt; M-12 als „ein gemessener Fall“ gekennzeichnet, Erweiterung bei [slice-adr-massenladen-atomar-an-messung](../done/slice-adr-massenladen-atomar-an-messung.md) | `spec/spezifikation.md` `SPEC-060`, Zuordnung im [Messprotokoll](../../../messungen/referenzabgleich.md#messreihe-zu-massenladen-merge-katalog-und-strukturänderung-messungen-vom-2026-11-07) |
| V-5 (LOW) Kopplung `DMigrateTexte.cs` und `DMigrateWire.kt` | Träger: Register `BEO-TDS/live-lauf-ausserhalb-der-gates-haelt-kopplungen-nicht` (dritte Kopplung, Beleg dieser Closure); kein Sensor angelegt | Register |
| V-6 (LOW) Plan §3 | nachgeführt (zweite Tabelle) | §3 |
| V-7 (LOW) Proxy-Fix in beiden Richtungen | im [Messprotokoll](../../../messungen/referenzabgleich.md#messreihe-zu-massenladen-merge-katalog-und-strukturänderung-messungen-vom-2026-11-07) genannt: beide Richtungen, unvollständiges Paket am Stromende geht verloren, keine Aussage über die Segmentierung der Serverantwort | Messprotokoll, Absatz „Aufteilung der Records im Proxy“ |
| V-8 (INFO) `observation.md` überholt | `observation.md` bleibt (unveränderlich); der Stand sagt, dass die Texte zu „nicht gemessen“ überholt sind | `state.md` von `obergrenze-…` und `zielbild-luecken-…` |
| V-9, V-10 (INFO) | als Grenzen unten geführt | dieser Abschnitt |
| V-11 (INFO) Risiken, Closure-Notiz offen | Ausgänge in §6, diese Notiz | §6, §7 |

**Übergabe an den Implementer von `slice-schreibpfad-bulk-load` (Zweig `worktree-agent-a4845d02c1beaf126`, nicht gemergt, hier nicht angefasst).** Beim Merge der beiden Zweige entstehen Konflikte in Roadmap, Welle-Datei und Slice-Dateien (beide Seiten erhalten). Der zweite Durchgang des Zweigs zieht aus [Messprotokoll](../../../messungen/referenzabgleich.md#messreihe-zu-massenladen-merge-katalog-und-strukturänderung-messungen-vom-2026-11-07) nach: (1) `INSERT BULK` als SQL_BATCH antwortet mit `DONE` Befehl `0x00fd`, ohne `COUNT` (M-8); (2) die Nachricht 7 antwortet mit `DONE` Status `COUNT`, Befehl `0x00f0`, Zeilenzahl der Nachricht (M-8, M-9: 1000, 1000, 500); (3) Atomarität bei 2627: in dem einen gemessenen Fall bleibt keine Zeile der Nachricht (M-12, `DONE` `ERROR` Befehl `0x00f0` ohne Zähler, danach `DONE` mit `ATTN`), Entscheid über die Folge-ADR abwarten ([slice-adr-massenladen-atomar-an-messung](../done/slice-adr-massenladen-atomar-an-messung.md)); (4) bei einer Nachricht über mehrere Pakete trägt das erste Paket das Statusbit `0x04` (M-9); (5) bei Verstößen im Client sendet der Referenzclient ein ATTENTION statt der Nachricht 7 (M-11); (6) eine leere Quelle sendet nichts (M-10). Der Zeiger im Slice selbst fehlt dort noch.

**Ergebnis je Messpunkt (§5).** Alle Aussagen sind Einzelmessungen: je Szenario und Instanz ein Fall (2022: 16.0.4305.5, 2025: 17.0.5035.5, Digest-Pin), ein Host (Last 10 bis 12, in der Verifikation 24 bis 33), `Microsoft.Data.SqlClient` 7.1.1, TDS 7.4 mit `Encrypt=Mandatory`, Proxy mit TLS-Beendigung. Nicht mssql-jdbc von d-migrate.

| Messpunkt | Ergebnis | Zuordnung zur Spezifikation (Details im [Messprotokoll](../../../messungen/referenzabgleich.md#messreihe-zu-massenladen-merge-katalog-und-strukturänderung-messungen-vom-2026-11-07)) |
|---|---|---|
| Massenladen: Ablauf, `DONE` `0x00fd` und `0x00f0`, Zähler, Status `0x04`, leere Quelle, Verstoß im Client (ATTENTION) | gemessen (M-8 bis M-11) | ohne Setzung in der Spezifikation; Maßstab des Bau-Slice [slice-schreibpfad-bulk-load](../done/slice-schreibpfad-bulk-load.md) |
| Schlüsselverstoß im Massenladen (2627) | gemessen, **ein Fall** (M-12) | widerspricht [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 5 (iii) und Nr. 6 für diesen Fall; Träger der Erweiterung und der ADR: [slice-adr-massenladen-atomar-an-messung](../done/slice-adr-massenladen-atomar-an-messung.md) |
| `MERGE … OUTPUT $action` mit Parametern: Tokenfolge, `$action` nvarchar(10) nicht nullbar, Zähler, Befehl `0x0117`, Fehler 2628, 2627, 208 | gemessen (M-13 bis M-15), Klauseln hinter `USING (VALUES …)` sind Setzung des Messcodes | ohne Setzung in der Spezifikation; Maßstab von [slice-schreibpfad-merge-output](../done/slice-schreibpfad-merge-output.md) |
| Typen der Katalogspalten, Fehler der Strukturabweisung, `CREATE TABLE` ohne Schlüssel, Änderungszähler | gemessen (M-16, M-19, M-20) | widerspricht `SPEC-035`/`SPEC-036` (Setzung); Träger [slice-katalog-und-struktur-an-messung-angleichen](slice-katalog-und-struktur-an-messung-angleichen.md) |
| Typnamen und Maße, `max_length` -1, `ADD` mehrerer Spalten | gemessen (M-17, M-18, M-19) | stützt `SPEC-035`/`SPEC-036` |
| Antworten der Sichten für Indizes, Standardwerte, Prüfbedingungen, Fremdschlüssel | gemessen, je ein Fall (M-21) | Eingang von [slice-katalog-indizes-defaults-fremdschluessel](../in-progress/slice-katalog-indizes-defaults-fremdschluessel.md) |
| `sp_unprepare` mit vielen unbekannten Nummern | gemessen (M-22): jede Nummer wird gemeldet, bis 2000 am Client | widerspricht `SPEC-060` (Obergrenze 100); Träger [slice-sp-unprepare-ohne-obergrenze](slice-sp-unprepare-ohne-obergrenze.md) |
| `curCmd` und Tokenfolge von `EXEC sp_unprepare`; Flag-Feld der Ausgabewerte | gemessen (M-23: `DONEPROC` `0x00e0`, kein `DONE`; M-24: `0x0000` für 10 Typen) | widerspricht dem Bau; stützt die Regel des echten Servers in `SPEC-030`; Träger [slice-antwort-tokens-zielbild-luecken-bauen](../done/slice-antwort-tokens-zielbild-luecken-bauen.md) |

**Zuordnung Messung | Handvektor | Festlegung je Aussage (`BEO-TDS/aussagen-ueber-messung-hinaus`).**

| Aussage | Art | Stelle |
|---|---|---|
| Tokenfolgen, Statusbits, Zähler, Befehlskennungen, Fehlernummern (Nummer, Class, State), Typen und Maße der Katalogspalten, Flag-Feld, Zahl der gemeldeten Nummern | Messung (Einzelmessung je Instanz, ein Host, ein Client) | M-8 bis M-24; Dateien unter `referenztest-dotnet/Mitschnitt/messung/bulk-merge-katalog/`; `MessungBulkBelegTests` |
| Wortlaut der 17 Katalogtexte und 7 Strukturtexte | Handvektor (Wortlaut aus `DMigrateWire.kt`, dort aus dem Mitschnitt von d-migrate 1.12.0), maschinell gleich geprüft | `DMigrateTexte.cs`, Review |
| Klauseln hinter `USING (VALUES …)` und Spaltenliste des `MERGE`, Quelle der `SqlBulkCopy`-Szenarien (`DataTable`), Aufbauskript | Festlegung des Messcodes | Test `MessungBulkTests`, Messprotokoll (Grenzen dieser Reihe) |
| Ursache des Abbruchs bei Records mit mehreren Paketen („der Server beendet“) | **Hypothese**; belegt ist der Zusammenhang mit dem Schnitt der Records (18 von 20 rot mit Mutation, 64 von 64 grün mit Fix, ohne Proxy 0 von 40) | Messprotokoll, „Aufteilung der Records im Proxy“ |
| Messung gilt für den Referenzclient, nicht für mssql-jdbc | Festlegung der Methode (Grenze) | Messprotokoll, Grenzen dieser Reihe |
| „keine Zeile der Nachricht bleibt“ als Regel des Servers | **nicht belegt** (ein Fall); Erweiterung beauftragt | M-12, [slice-adr-massenladen-atomar-an-messung](../done/slice-adr-massenladen-atomar-an-messung.md) |
| Redaktion der Ablage (Rechnername, Benutzer, Kennwort) | Festlegung, durch Stichprobe in 32 Dateien belegt (Review) | Review, `MessDatei.Treffer` im Test |
| Bau-Slices, Reihenfolge, Zuordnung zu Welle und Abnahme | Nutzerentscheide 2026-11-07 und Setzungen des Planners (Reihenfolge, Abnahme-Zuordnung) | die vier Slices, Welle-Datei |

**Steering-Loop-Eintrag (benannte Spec-Lücken; nichts verkörpert, daher ohne Zielort-Feld).** (a) *Sieben Zielbild-Konflikte der Spezifikation und des Baus gegen den echten Server* (M-12, M-16, M-19, M-20, M-22, M-23, M-24): benannt im [Messprotokoll](../../../messungen/referenzabgleich.md#messreihe-zu-massenladen-merge-katalog-und-strukturänderung-messungen-vom-2026-11-07) und mit Messvermerk in `SPEC-030`, `SPEC-035`, `SPEC-036`, `SPEC-060`, mit Träger je Konflikt (vier Slices in `open/`). (b) *Mssql-jdbc gegen Referenzclient* (Folgepflicht 2 von [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md)): Messauftrag in [slice-schreibpfad-merge-output](../done/slice-schreibpfad-merge-output.md). (c) *Serverseitiger Verstoß gegen Typ oder Länge* im Massenladen ist mit `SqlBulkCopy` und `DataTable` nicht herstellbar, das IGNORE-Bit des Pakets 7 ist nicht gemessen: Lücke ohne Träger, benannt in den Grenzen dieser Reihe. (d) *Ursache des Verbindungsabbruchs im Server bei Records mit mehreren Paketen:* nicht festgestellt. Der Wiederholungslauf `MESSUNG_WIEDERHOLEN` (Rate roter Läufe, Exit 0 bei grünem Test) ist ein geänderter Sensor des Messwerkzeugs; er trägt noch keinen Herkunfts-Anker (3×-Schwelle nicht erreicht, Klasse `BEO-TDS/messlauf-intermittierend-rot-ohne-rate-im-protokoll` steht bei 1×).

**Lerneintrag (Finding-Klassen, nach Klasse im Register gezählt, nichts verkörpert).** Review F-1 bis F-11 und Verifikation V-1 bis V-11 fallen in wenige Klassen: (1) *Flake im Messwerkzeug (Proxy)* und das Protokoll ohne Rate (F-2, V-3): erst Wiederholungen mit Mutation machten Ursache und Fix belegbar — `BEO-TDS/messlauf-intermittierend-rot-ohne-rate-im-protokoll` (neu, 1×). (2) *Zustandszelle erzählt Chronik* (F-1: drei `Stand`-Zellen des Registers mit „Bisheriger Wortlaut“, zwei widersprüchliche Zustände nebeneinander) — `BEO-TDS/kommentar-nennt-abwesenden-oder-unwirklichen-zustand` (jetzt 8× nach Dateien). (3) *Aussage ohne Messgrenze im selben Satz* (F-7, V-4: Allaussagen über 10 Typen, drei Fälle, ein Fall) — `BEO-TDS/aussagen-ueber-messung-hinaus`. (4) *Träger zeigt auf einen nicht existierenden Slice* (F-5, V-2: sieben Konflikte mit „Bau-Slice nach Nutzerentscheid“ ohne Datei; ein versprochener Bau-Slice nach eingetretenem Trigger ohne Datei) — `BEO-TDS/mitschnitt-luecken-ohne-traeger` (jetzt 3×). (5) *Fortschreibung lässt die zweite Fundstelle stehen* (F-3, F-4, F-9, V-1, V-8) — `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`. (6) *Plan-Tabelle gegen Diff* (V-6) — `BEO-TDS/plan-tabelle-nennt-neue-datei-nicht`. (7) *Kopplung ohne Sensor* (V-5: `DMigrateTexte.cs` gegen `DMigrateWire.kt`) — `BEO-TDS/live-lauf-ausserhalb-der-gates-haelt-kopplungen-nicht` (jetzt 3×). (8) *Nachbesserung ohne zweiten Review* (Bedingung 4) — `BEO-TDS/nachbesserung-ohne-zweiten-review` (jetzt 4×). (9) *Spezifikation und Bau gegen den echten Server* (sieben Konflikte) — `BEO-TDS/spec-setzung-widerspricht-messung-am-echten-server`. Zum Messen mit einem Proxy: ein Proxy, der Records zusammenfasst, kann den Server zum Reset der Verbindung bringen, und die Messreihe muss die Rate roter Läufe nennen, nicht den einen grünen Lauf. Zur Zählung: vier Klassen (`mitschnitt-luecken-ohne-traeger`, `live-lauf-ausserhalb-der-gates-haelt-kopplungen-nicht`, `nachbesserung-ohne-zweiten-review`, `kommentar-nennt-abwesenden-oder-unwirklichen-zustand`) liegen über der Schwelle; der Lese-Schritt gehört in die Closure der Welle, diese Closure ändert weder Regel noch Gate.

**Gate-Läufe und Belege (Planner, im Worktree des Zweigs, Stand nach den Änderungen dieser Closure).** `make gates`: Exit 0; die Klasse `werkzeug` lief frisch (`skript-selbsttest` mit dem geänderten `tools/vergleichslauf-d.sh`, `detekt-selbsttest`, `kopplung-zahlen`, `flaeche`), die Klassen `tests` und `stempel` übersprangen mit Beleg an einem Vorfahren (Eingänge unverändert); `d-check`: 1134 Dateien, 0 Befunde; `make docs-check` vor jedem Commit Exit 0. `make referenztest-dotnet`: Exit 0, `Passed: 126, Failed: 0`. `make spec-abdeckung`: Exit 0, `docs/plan/spec-abdeckung.md` unverändert (9 Festlegungen ohne Nachweis). `make vergleichslauf-d LAUF=bulk-2022 MESSUNG_NUR=bulk-leer MESSUNG_WIEDERHOLEN=3`: Exit 0, `wiederholung: 3 Laeufe, 0 rot`; derselbe Aufruf mit dem Stand vor der Korrektur (Mutation, `MESSUNG_WIEDERHOLEN=2`): Exit 2, `FEHLER … Messungen vorhanden`, `ROT`. Nicht erneut gefahren: die Messreihe `bulk-2022`/`bulk-2025` im Ganzen (die Verifikation hat sie auf `4d73a9d2` gefahren; seither ändern sich nur Dokumente und die Exit-Logik des Wiederholungslaufs), `make clean-build`, `make matrix-zitate` (Verifikation: Exit 0).

- **Was hat funktioniert:** Die Messreihe ist wiederholbar im Repo (`make vergleichslauf-d LAUF=bulk`, 56 s und 68 s je Instanz); Byte-Stichproben, Instanz-Gleichheit, Redaktion und Mutationen tragen; die Wiederholungen mit Mutation machten den Fix des Proxys belegbar.
- **Was ging anders als geplant:** Der Proxy brauchte einen Fix (Record je Paket), obwohl der Plan nur Szenarien nannte; sieben Konflikte statt der erwarteten wenigen; der Bau-Slice der Zielbild-Lücken entstand nach der Messung (Umplanung); der Zeiger in `slice-schreibpfad-bulk-load` ist Übergabe, weil der Slice in einem anderen Zweig liegt (Abweichung vom Plan-Wortlaut von Liefer-Punkt 3); die Nachbesserung hat keinen zweiten Review.
- **Beobachtungs-Register (`../observations/`):** `evidence/slice-qa-vergleichslauf-d-messungen-bulk-merge.md` ergänzt in `BEO-TDS/aussagen-ueber-messung-hinaus`, `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`, `BEO-TDS/plan-tabelle-nennt-neue-datei-nicht`, `BEO-TDS/nachbesserung-ohne-zweiten-review`, `BEO-TDS/mitschnitt-luecken-ohne-traeger`, `BEO-TDS/live-lauf-ausserhalb-der-gates-haelt-kopplungen-nicht`, `BEO-TDS/kommentar-nennt-abwesenden-oder-unwirklichen-zustand`, `BEO-TDS/spec-setzung-widerspricht-messung-am-echten-server`, `BEO-TDS/obergrenze-gemeldete-nummern-je-batch-weicht-vom-echten-server-ab`, `BEO-TDS/zielbild-luecken-der-antwort-tokens` und im neuen Eintrag `BEO-TDS/messlauf-intermittierend-rot-ohne-rate-im-protokoll`; Stände fortgeschrieben in `BEO-TDS/messung-am-echten-sql-server-fehlt`, `BEO-TDS/katalog-strukturabweisung-und-aenderungszaehler-weichen-vom-echten-server-ab`, `BEO-TDS/folge-adr-zu-adr-0033-massenladen-verstoss-und-teilbestand`. Zähler folgen aus den Dateien.
- **Folge-Slices:** [slice-katalog-und-struktur-an-messung-angleichen](slice-katalog-und-struktur-an-messung-angleichen.md), [slice-sp-unprepare-ohne-obergrenze](slice-sp-unprepare-ohne-obergrenze.md), [slice-adr-massenladen-atomar-an-messung](../done/slice-adr-massenladen-atomar-an-messung.md), [slice-antwort-tokens-zielbild-luecken-bauen](../done/slice-antwort-tokens-zielbild-luecken-bauen.md); Zeiger und Messauftrag in [slice-schreibpfad-merge-output](../done/slice-schreibpfad-merge-output.md); [slice-katalog-indizes-defaults-fremdschluessel](../in-progress/slice-katalog-indizes-defaults-fremdschluessel.md) (Eingang M-21); [slice-schreibpfad-bulk-load](../done/slice-schreibpfad-bulk-load.md) (Zeiger durch Übergabe).
- **Offene Nutzerpunkte:** (1) die Nachbesserung `bd508880`/`4d73a9d2` als „Nachbesserung ohne zweiten Review“ führen (Vorschlag) oder den Re-Review nachholen; (2) `BEO-TDS/nachbesserung-ohne-zweiten-review` (4×), `BEO-TDS/mitschnitt-luecken-ohne-traeger` (3×), `BEO-TDS/live-lauf-ausserhalb-der-gates-haelt-kopplungen-nicht` (3×) und `BEO-TDS/kommentar-nennt-abwesenden-oder-unwirklichen-zustand` (8×): Regel, Sensor, Träger oder Streichung bei der Closure der Welle; (3) die Kopplung `DMigrateTexte.cs` und `DMigrateWire.kt`: Sensor gewünscht?; (4) Reihenfolge der vier neuen Slices zueinander und zu den Schreib-Slices (Setzungen des Planners); (5) Grenzen: Raten unter Last (V-10), Ablage mit Zufallsanteil (V-9, F-10: 32 Dateien ändern sich bei jedem Lauf in der PRELOGIN-Zeile des Clients), Einzelmessungen auf einem Host.
- **Risiken aus §6:** jedes mit genau einem Ausgang, siehe §6.
- **Drei Paarungen:** geprüft nach dem Move (2026-11-07): *Anker* — kein Steering-Loop-Eintrag trägt ein Zielort-Feld, nichts zu paaren; *Folge-Slice* — die neun genannten Slices ([slice-katalog-und-struktur-an-messung-angleichen](slice-katalog-und-struktur-an-messung-angleichen.md), [slice-sp-unprepare-ohne-obergrenze](slice-sp-unprepare-ohne-obergrenze.md), [slice-adr-massenladen-atomar-an-messung](../done/slice-adr-massenladen-atomar-an-messung.md), [slice-antwort-tokens-zielbild-luecken-bauen](../done/slice-antwort-tokens-zielbild-luecken-bauen.md), [slice-schreibpfad-merge-output](../done/slice-schreibpfad-merge-output.md), [slice-katalog-indizes-defaults-fremdschluessel](../in-progress/slice-katalog-indizes-defaults-fremdschluessel.md), [slice-schreibpfad-bulk-load](../done/slice-schreibpfad-bulk-load.md), [slice-abnahme-d-migrate](../open/slice-abnahme-d-migrate.md), [slice-katalog-und-ddl-zustand](../done/slice-katalog-und-ddl-zustand.md)) existieren als Dateien; *Register* — alle 15 in §6 und §7 genannten `BEO-TDS/<slug>` sind Verzeichnisse mit nicht leerem `evidence/` (maschinell geprüft). Rot: keine.

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
