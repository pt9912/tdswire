# ADR-0042: Anweisungsart und Zustandswechsel im Abschluss einer Antwort setzt nur die Bibliothek, der Port bleibt unverändert

**Status:** Proposed

**Datum:** 2026-11-07

**Autor:** Architect (Entwurf); Entscheidung: pt9912 (Nutzer), ausstehend

**Bezug:** [TDS-FA-10](../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), [TDS-FA-11](../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-QA-07](../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität), [ADR-0021](0021-oeffentlicher-integrations-einstieg.md), [ADR-0026](0026-oeffentlichkeit-simulations-backend-und-katalog.md), [ADR-0033](0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), [ADR-0036](0036-handle-freigabe-und-grenze-je-sitzung.md), [ADR-0038](0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md), [ADR-0041](0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md)

**Schärft:** [SPEC-031](../../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität) (Absatz „Befehlskennung (`curCmd`)“: Abschluss eines Änderungszählers), [SPEC-035](../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) (Abschluss von `CREATE TABLE` und `ALTER TABLE … ADD`, Transaktionstexte), [SPEC-036](../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) (Form C), [SPEC-030](../../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten) (Abschluss nach Änderung im RPC) und bestätigt die Liste in [SPEC-059](../../../spec/spezifikation.md#einstieg-des-integrators-vertrag-zu-spec-059) (unverändert).

**Supersedes (teilweise), nur wörtlich benannt; die Datei des Vorgängers bleibt unverändert und `Accepted` ([AGENTS.md](../../../AGENTS.md) §3.5):** [ADR-0033](0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Entscheidung 3, **nur** die Klammer „(Setzung: mit dem Änderungszähler 0; gemessen ist der Zähler 0 nur für `ROLLBACK TRAN`, d1)“, soweit sie den Abschluss von `CREATE TABLE` und `ALTER TABLE … ADD` auf den Änderungszähler 0 festlegt; es gilt Entscheidung 4 dieser ADR. Es bleiben: der übrige Wortlaut von Entscheidung 3 (Erkennen, Callback, Fehler des Callbacks, Abweisung ohne Callback) und Entscheidung 6 (auch der Satz zu `ROLLBACK TRAN`: nicht gemessen, bleibt Setzung mit Zähler 0).

**Regeln:** Baseline-Regelwerk `modul-04-adrs.md` §Ziel-Form: ADR (MADR).

---

## Kontext

**Messung** ([Messreihe vom 2026-11-07](../../messungen/referenzabgleich.md#messreihe-zu-massenladen-merge-katalog-und-strukturänderung-messungen-vom-2026-11-07), Referenzclient, SQL Server 2022 und 2025). M-20: `CREATE TABLE` schließt mit `DONE` ohne `COUNT`, Befehl `0x00c6`; `ALTER TABLE … ADD` mit `0x00d8`; `set implicit_transactions on` mit `0x00b9`, bei `CREATE TABLE` danach `ENVCHANGE` Typ 8; `IF @@TRANCOUNT > 0 COMMIT TRAN` als `DONE MORE` `0x00c0`, `ENVCHANGE` Typ 9, `DONE` `0x00d5`. W-6 der [Messreihe am Draht](../../messungen/referenzabgleich.md#messreihe-am-draht-messungen-vom-2026-11-06): INSERT `0x00c3`, DELETE `0x00c4`, UPDATE `0x00c5`. Der Nutzer hat am 2026-11-07 entschieden, dass der Bau dem echten Server folgt.

**Ist.** `QueryEvent.UpdateCount(affectedRows)` ist der Abschluss jeder Anweisung ohne Ergebnis; der Sequenzierer schreibt dazu `DONE` mit `COUNT` und der Befehlskennung 0, „weil der Port die Art der Anweisung nicht trägt“ ([SPEC-031](../../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität)). Das Simulations-Backend kennt die Art dagegen schon: es hat die Form erkannt (W1 bis W3, D, C, `MERGE`, `INSERT BULK`). Sitzungszustand führt `RequestLoop` heute nur für die Prepared-Handles (frei mit der Verbindung, [ADR-0036](0036-handle-freigabe-und-grenze-je-sitzung.md)); `QueryBackend` hat keinen Hook für das Ende einer Sitzung außer `cancel`. [ADR-0033](0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 6 sagt: keine Transaktion; die Bibliothek nimmt nichts zurück.

**Was die Frage betrifft und was nicht.** Vom Auftrag der beiden Bau-Slices hängen an dieser Entscheidung nur: (a) `curCmd` nach INSERT, UPDATE, DELETE, (b) Abschluss von `CREATE TABLE`/`ALTER TABLE … ADD` ohne `COUNT` mit eigener Kennung, (c) `ENVCHANGE` Typ 8 und 9. **Nicht** betroffen, weil Protokoll oder Sequenzierer sie allein bestimmen: das Flag-Feld der Ausgabewerte, `curCmd` `0x00c1` nach Fristablauf bei offenem Ergebnis, Tokenfolge und `0x00e0` von `EXEC sp_unprepare` (das erkennt `UnprepareBatch` im Protokoll).

**Wirkung auf d-migrate: nicht gemessen** ([AGENTS.md](../../../AGENTS.md) §3.8). Alle Läufe gegen den Testserver (Spike, Katalog-Messung) endeten mit den heutigen Abschlüssen (Zähler 0, kein `ENVCHANGE`, `curCmd` 0) bei Exit 0; der Treiber (mssql-jdbc 13.4.0) zeigt der Anwendung nach meiner Kenntnis kein `curCmd`, das ist hier **Annahme**. Die Abweichungen und was sie bei einem Client ändern könnten:

| Abweichung | verändert d-migrate? | Stand |
|---|---|---|
| `curCmd` 0 statt `0x00c3`/`0x00c4`/`0x00c5`/`0x00c6`/`0x00d8` | kein Hinweis | nicht gemessen; Zielbild ([TDS-QA-01](../../../spec/lastenheft.md#tds-qa-01--kompatibilität)) |
| Zähler 0 mit `COUNT` statt `DONE` ohne `COUNT` bei DDL | möglich nur über `getUpdateCount`/`executeUpdate`; kein Hinweis | nicht gemessen |
| `ENVCHANGE` 8/9 fehlt | **einzige Abweichung mit Zustand im Client**: ein Client kann aus Typ 8 einen Transaktionsdeskriptor übernehmen und ihn in späteren Anfragen zurücksenden; das Senden ist also die Änderung mit dem **größeren Risiko**, nicht das Weglassen | nicht gemessen, weder die Reaktion von mssql-jdbc noch die des Referenzclients auf einen Deskriptor des Testservers |
| `CREATE TABLE` ohne Schlüssel abgewiesen (anderer Liefer-Punkt desselben Bau-Slices) | **ja**: `CREATE TABLE` ohne Schlüssel weist der Server heute ab, ein Soll mit schlüssellosen Tabellen kann `schema migrate --execute` damit nicht anlegen (Wirkung am Client nicht gemessen) | Folge der Spezifikation (`SPEC-035` Validierung verlangt einen Schlüssel); keine Fläche, keine Entscheidung dieser ADR |

**Randbedingung.** [ADR-0033](0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 4 (jede neue öffentliche Fläche steht in einer Liste; neuer Name = Befund) und die Nutzerentscheidung vom 2026-11-05 („nur die Klassen, die man dafür braucht“). Ein Embedder braucht die Anweisungsart für seine Aufgabe nicht; der Bedarf ist ein Zielbild-Bedarf der Bibliothek.

## Entscheidung

Wir wählen **Option C**: Anweisungsart und Zustandswechsel setzt nur die Bibliothek, aus der Form, die das Simulations-Backend ohnehin erkannt hat. `QueryEvent`, `QueryBackend`, `QueryExecution` und die Callbacks von `SimTable` bleiben unverändert, es entsteht kein öffentlicher Name.

1. **Weg.** Das Simulations-Backend übergibt dem Sequenzierer die Anweisungsart (INSERT, UPDATE, DELETE, `CREATE TABLE`, `ALTER TABLE … ADD`, die Transaktionstexte der Form C) auf einem nur intern sichtbaren Weg (derselbe Gedanke wie in [ADR-0041](0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) Entscheidung 1; wer von beiden Bauten zuerst kommt, legt ihn an). Der Sequenzierer schreibt daraus Befehlskennung und das Bit `COUNT`: Änderungen mit `COUNT` und `0x00c3`/`0x00c5`/`0x00c4`, `CREATE TABLE` und `ALTER TABLE … ADD` als `DONE` ohne `COUNT` mit `0x00c6` bzw. `0x00d8`, die Transaktionstexte mit den gemessenen Folgen von M-20. Keine Unterklasse eines öffentlichen `sealed`-Typs, es sei denn, eine Übersetzungsprobe im Source-Set `integrator` zeigt, dass ein Kotlin-`when` über `QueryEvent` erschöpfend bleibt (nicht geprüft).
2. **Kein Text-Klassifizierer im Protokoll.** Das Protokoll liest den SQL-Text eines Batches oder `sp_executesql` nicht, um eine Art abzuleiten (Option B). Ausnahme bleibt die bestehende Form `EXEC sp_unprepare`.
3. **Eigene Backends** (`BackendSource.Custom`) bleiben bei dem heutigen Abschluss: `UpdateCount` mit `COUNT`, Befehlskennung 0. Das ist eine **benannte Grenze** (Setzung), die `SPEC-031` und `SPEC-059` nennen; die Zusage „der Server folgt dem echten Server“ gilt für die Antworten des Simulations-Backends und die Teile, die das Protokoll allein bestimmt.
4. **Zustand je Sitzung** (`implicit_transactions` an/aus, Transaktion offen) hält die Verbindung wie die Prepared-Handles, nicht das Backend: das Backend meldet nur das Ereignis (Anweisungsart), die Verbindung schreibt daraus `ENVCHANGE` 8 und 9. Gemessen ist Typ 8 nur bei `CREATE TABLE` nach `set implicit_transactions on` und Typ 9 nur beim `COMMIT` dieses Zustands (M-20); jede weitere Kombination (Typ 8 bei INSERT/UPDATE/DELETE, `ROLLBACK`, Ende der Sitzung) ist Setzung und wird nicht als Zusage geschrieben. Die Bytes des Deskriptors liefert ein Handvektor aus der Ablage der Messung ([AGENTS.md](../../../AGENTS.md) §3.8), nicht der Entwurf.
5. **Zusage „keine Transaktion“ bleibt.** `ENVCHANGE` 8 meldet dem Client einen Zustand, den die Bibliothek nicht zurücknimmt ([ADR-0033](0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 6); die Spezifikation sagt das bei der Zusage zu `ENVCHANGE` ausdrücklich.
6. **Reihenfolge im Bau.** Die Teile ohne Frage am Port (Flag-Feld, `0x00c1`, `sp_unprepare`) können gebaut werden, bevor diese ADR angenommen ist. Innerhalb der Teile mit Frage endet `ENVCHANGE` (Entscheidung 4) zuletzt und wird erst als Zusage geschrieben, wenn ein Lauf mit d-migrate (`schema migrate --execute`, `schema compare`) nach dem Bau dieselben Ergebnisse zeigt wie vorher; ändert er sie, bleibt `ENVCHANGE` ungebaut und ist eine benannte Grenze (Option D für diesen Teil).

## Verglichene Alternativen

| Option | Pro | Contra |
|---|---|---|
| A — öffentlich: Anweisungsart als Feld oder Ereignis am Port (z. B. `UpdateCount` mit Art, ein Aufzählungstyp, ein Zustands-Ereignis) | jedes Backend kann das Zielbild erreichen; nichts ist implizit; Antworten sind im Test aus Sicht des Ports prüfbar | mindestens ein neuer Typname plus Änderung von `UpdateCount` (Konstruktor, `copy`, `componentN`, für Java `@JvmOverloads` nötig) und für `ENVCHANGE` weitere Ereignisse; [ADR-0033](0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 4 und SPEC-059, `make flaeche` berührt; der Einbettende braucht es nicht („nur, was man dafür braucht“); jedes eigene Backend bekäme die Pflicht, die Art richtig zu melden (falsche Art = falscher Zustand beim Client, [TDS-QA-07](../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität)) |
| B — Text-Klassifizierer plus Sitzungszustand im Protokoll (wie `UnprepareBatch`) | wirkt für alle Backends gleich; kein Weg vom Backend zum Sequenzierer | das Protokoll müsste SQL lesen (Scanner liegt im Package des Simulations-Backends; Schichtregeln von `make detekt`; sonst Doppelung); `sp_executesql` verpackt den Text in Parametern; eine Fehlklassifizierung bei eigenen Backends (z. B. `EXEC` einer Prozedur mit Zähler) schreibt falsche Kennung oder meldet einen Zustand, den der Client dann führt ([TDS-QA-07](../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität)); die Bibliothek nimmt für eigene Backends eine Semantik an, die sie nicht kennt; zweimal Erkennung derselben Formen (Backend und Protokoll) |
| **C — intern, nur die Bibliothek setzt die Art (gewählt)** | keine Fläche, kein Text-Lesen im Protokoll; die Art kommt aus der Erkennung, die das Backend ohnehin hat; Zustand lebt und stirbt mit der Verbindung; trägt beide Bau-Slices mit einem Mechanismus (gemeinsam mit [ADR-0041](0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md)) | eigene Backends bleiben bei Kennung 0 (Grenze); zweiter Weg neben dem Port, der gepflegt und getestet werden muss; `ENVCHANGE` simuliert eine Transaktion, die es nicht gibt (Entscheidung 5) |
| D — Teilmenge, Rest als Grenze: nur was d-migrate liest, hier: nichts von `curCmd` | kein Aufwand, kein Risiko durch `ENVCHANGE`; belegt ist, dass d-migrate mit den heutigen Abschlüssen bis Exit 0 läuft | widerspricht dem Zielbild und dem Nutzerentscheid vom 2026-11-07 für `curCmd` und DDL-Abschluss; als **Rückfallstufe für `ENVCHANGE`** ist D in Entscheidung 6 enthalten |

**Bewertung nach den Kriterien.** Sicherheit/[TDS-QA-07](../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität): C setzt die Art nur dort, wo die Bibliothek sie selbst erkannt hat, ohne Eingabe eines Dritten; die Verbindung führt den Zustand, es gibt kein Verwaisen; B und A öffnen den Weg für falsche Art von außen oder aus Fehlklassifizierung. Neu ist das Risiko durch `ENVCHANGE` (Kontext, Tabelle). Öffentliche Fläche: A viele Namen, B/C/D keine. Java-Lesart: A trifft `UpdateCount` binär, sonst neutral. Wartbarkeit: C hält Erkennung an einer Stelle, B zweimal, A verteilt die Pflicht auf jedes Backend. Zielbild: A/B/C erreichen es für das Simulations-Backend, nur A/B auch für eigene Backends (B nur geraten). Aufwand: D null, C mittel (Weg, Zustand der Verbindung, Skripte je Form), B groß, A groß. Kopplung der Slices: beide hängen an dem einen Weg; der Bau zu den Zielbild-Lücken der Antwort-Tokens verliert nur den Punkt (a) an diese Entscheidung, der Bau zur Angleichung des Katalogs die Punkte (b) und (c).

## Konsequenzen

- Positiv: Zielbild für `curCmd`, DDL-Abschluss und Transaktionstexte beim Simulations-Backend ohne neuen öffentlichen Namen; `make flaeche` und der Einbettungstest bleiben unverändert; ein Mechanismus für beide Bau-Slices.
- Negativ: Eigene Backends sehen die Angleichung nicht (Grenze, in der Spezifikation benannt); `ENVCHANGE` 8 behauptet gegenüber dem Client eine Transaktion, die die Bibliothek nicht hat (verstärkt, was d-migrate schon in seinen Bericht schreibt, [ADR-0033](0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Konsequenzen); die Reaktion des Treibers auf den Deskriptor ist unbekannt.
- Folgepflicht Bau: der Bau zu den Zielbild-Lücken der Antwort-Tokens setzt Punkt (a) um (Tests je Anweisungsart); der Bau zur Angleichung des Katalogs setzt (b) und (c) um; die Rückführungen „Fläche wächst“ beider Slices entfallen für diese Punkte, solange `make flaeche` unverändert bleibt. Ein Test sendet nach `ENVCHANGE` 8 eine Anfrage mit dem zurückgesendeten Deskriptor im Kopf der Nachricht und erwartet eine normale Antwort ([TDS-QA-07](../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität)).
- Folgepflicht Spezifikation: `SPEC-031`, `SPEC-035`, `SPEC-036`, `SPEC-030` wie im Feld „Schärft“; erst nach `Accepted`.

## Fitness Function (falls maschinell prüfbar)

| Tooling | Regel | Make-Target |
|---|---|---|
| Typnamen-Sensor der Fläche | Liste in `SPEC-059` unverändert | `make flaeche` |
| Tests des Sequenzierers und der Verbindung | je Anweisungsart Kennung und `COUNT`-Bit gegen W-6 und M-20; `ENVCHANGE` 8/9 nur in den gemessenen Fällen; ein eigenes Backend schreibt Kennung 0 | `make test` |
| Einbettungstest im Source-Set `integrator` | unverändert übersetzbar, bedient eine Anfrage mit zurückgesendetem Deskriptor | `make test` |
| Referenzclient gegen den Serverprozess (kein Gate) | Antworten auf `CREATE TABLE`, `ALTER TABLE … ADD` und die Transaktionstexte mit dem Referenzclient | `make kompatibilitaet` |

## Re-Evaluierungs-Trigger

Ein Einbettender verlangt die Art oder den Zustand für ein eigenes Backend (Option A als Folge-ADR); ein Lauf mit d-migrate oder dem Referenzclient zeigt, dass `curCmd`, `DONE` ohne `COUNT` oder `ENVCHANGE` das Verhalten ändert (zum Guten: Priorität; zum Schlechten: Rückfall D für den betroffenen Teil); die Messung zeigt `ENVCHANGE` 8 bei weiteren Anweisungen oder `ROLLBACK`; [ADR-0033](0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 6 („keine Transaktion“) wird aufgegeben.

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-11-07 | Proposed (Entwurf des Architects) | [Messprotokoll](../../messungen/referenzabgleich.md#messreihe-zu-massenladen-merge-katalog-und-strukturänderung-messungen-vom-2026-11-07), Zeilen M-20, M-23; [Messreihe am Draht](../../messungen/referenzabgleich.md#messreihe-am-draht-messungen-vom-2026-11-06), W-6 |

Nach `Accepted` wird diese Datei **nicht mehr inhaltlich überschrieben**.
Spätere Korrekturen oder Schärfungen entstehen als neue ADR mit
`Supersedes ADR-NNNN` (Baseline-Regelwerk `modul-04-adrs.md`
§Hard Rule für Accepted-ADRs).
