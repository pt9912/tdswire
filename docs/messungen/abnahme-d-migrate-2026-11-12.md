# Abnahme mit d-migrate nach der Anleitung des Testservers: Messprotokoll vom 2026-11-12

Messprotokoll zu [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) und [TDS-RB-01](../../spec/lastenheft.md#tds-rb-01--implementierungssprache) (`AGENTS.md` §5 Regel 7), Träger: [slice-abnahme-d-migrate](../plan/planning/in-progress/slice-abnahme-d-migrate.md). Es hält fest, was gemessen wurde, nicht, ob eine Anforderung belegt ist. Gelesen wurde allein die Anleitung [Testserver von Hand starten und ausprobieren](../user/testserver-manuell.md) (Stand vor diesem Lauf); Spezifikation, ADRs und Register wurden vor dem Lauf nicht gelesen (Validator mit frischem Kontext). Das Register [katalog-elemente-grenzen-der-angleichung](../plan/planning/observations/BEO-TDS/katalog-elemente-grenzen-der-angleichung/observation.md) wurde erst nach dem Lauf gelesen; die Zuordnung steht unten.

## Bedingungen

| Angabe | Wert |
|---|---|
| Datum, Rechner | 2026-11-12, `legion7i16`, Intel Core i9-13900H, 20 Kerne, 31 GiB, Linux 6.8.0-147, Docker 29.9.3 |
| Last | Load Average (1/5/15 Minuten) laut `uptime`: 3,05 / 3,08 / 4,17 beim Start (11:17), 4,50 / 3,85 / 3,95 gegen Ende (11:29), 3,90 / 3,81 / 3,93 beim Abbau (11:30); natürliche Last, keine künstliche; Speicher: 14 bis 15 GiB verfügbar, Swap 10 von 16 GiB belegt (vorher, nicht durch den Lauf) |
| Testserver | Arbeitsbaum des Worktrees auf `acd70a20`, Image lokal gebaut (`tdswire-testserver:val-abn`, Image-ID `sha256:8906932bcf88d86563e5cb01f39802e00b7b34cebbd7aa9d0761b7c328b3ffc4`), Container `val-abn` auf `127.0.0.1:14577`, ein zweiter `val-abn2` auf 14578 (Ziel von `data transfer`, gleiches Image, `TESTSERVER_BAUEN=0`) |
| Client | d-migrate 1.12.0, `ghcr.io/pt9912/d-migrate@sha256:308f27753036c9cc423f716b9fc4c9e68016543a8003ecd2f8282e05a2ee6491` (ID gleich), mssql-jdbc 13.4.0; Aufruf wie in der Anleitung (`--network host`, `--user "$(id -u):$(id -g)"`, `-v probe:/work`) |
| Abweichung von der Anleitung | Port 14577 statt 1433 (Variable `TESTSERVER_PORT`, Name `TESTSERVER_NAME`, Image `TESTSERVER_IMAGE`), damit kein anderer Lauf geteilt wird; die Verbindungszeichenfolgen tragen den Port |
| Ablage | Befehle und Ausgaben liegen im Scratchpad des Laufs (90 Dateien, nicht im Repo); die Soll-Dateien sind aus den Texten der Anleitung abgeschrieben, die Zusatzfälle mit `sed` aus `soll-elemente.yaml` abgeleitet, kumulativ in der Reihenfolge der Tabelle D |

Zeiten: `make testserver-up` 36 s beim ersten Bau, danach 3 bis 4 s; jeder d-migrate-Befehl 1,0 bis 2,5 s (Container-Start eingeschlossen). Einzelmessungen unter Last 3 bis 5.

## A. Die Funktionen

Die Spalte „Zustand danach“ nennt, was nach dem Befehl gelesen wurde.

| Funktion | Befehl (Kurzform) | Exit | Zustand danach |
|---|---|---|---|
| Anmeldung unverschlüsselt | `schema reverse --source mssql://alice:secret@127.0.0.1:14577/manuell?encrypt=false` | 0 | `ist.yaml` mit `kunden`, `bestellungen`; Log `login_accepted`, `tds-7.4:not_supported` |
| Anmeldung `encrypt=true` | `schema reverse` mit `bob:geheim@localhost`, `encrypt=true&trustServerCertificate=true`, Server `REQUIRED` | 0 | Schema geschrieben; Log `login_accepted`, `tds-7.4:required`; ohne `trustServerCertificate`: Exit 4, `PKIX path building failed`, Log `tls_handshake_failed` |
| falsches Kennwort | `schema reverse` mit `alice:falsch` | 4 | `Login failed.`; kein Schema geschrieben |
| `schema reverse` | wie oben, `--output /work/ist.yaml` | 0 | Dateien `ist.yaml` und `ist.report.yaml` wie in der Anleitung |
| `schema compare` | `--source file:/work/soll.yaml --target db:…` | 1, danach 0 | zuerst `DIFFERENT` (`produkte` fehlt, `kunden`: `column Email`), nach `migrate` `IDENTICAL` |
| `schema migrate --execute` | `--source /work/soll.yaml --target db:… --execute --report` | 0 | Bericht `status ok`, `statementsAttempted` 2, `transactionRolledBack` false; `compare` danach `IDENTICAL` |
| `data export` (leer) | `--tables kunden --format json` | 0 | `kunden.json` enthält `[]`; `0 rows` |
| `data import` (Standardpfad) | `--source kunden-ein.json --table kunden` | 5, nach Neustart 0 | ohne Neustart nach `migrate`: Fund 1; nach Neustart `2 inserted`, Export liefert beide Zeilen |
| `data import` doppelter Schlüssel | dieselbe Datei noch einmal | 5 | `Violation of PRIMARY KEY constraint. Cannot insert duplicate key.`; Export unverändert zwei Zeilen |
| `data import --on-conflict skip` | Zeilen Id 2 (geändert) und 3 (neu) | 0 | `1 inserted, 1 skipped`; Id 2 unverändert, Id 3 neu |
| `data import --on-conflict update` | dieselbe Datei | 0 | `0 inserted, 2 updated`; Id 2 geändert, Id 3 aus der Datei |
| `data transfer` (Standardpfad) | `--source` Server 1 `--target` Server 2 `--tables kunden` | 3 ohne gleiche Struktur, dann 0 | ohne gleiche Struktur `Preflight: Column 'kunden.Email' missing in target` (Fund 2); nach `schema migrate` auf Server 2 `Transferred: kunden`, Export am Ziel zeigt alle Zeilen |
| `data transfer --on-conflict skip` | Quelle hat Id 3 geändert, Id 4 neu | 0 | Ziel: Id 4 neu, Id 3 unverändert |
| `data transfer --on-conflict update` | wie zuvor | 0 | Ziel: Id 3 geändert |
| `data transfer` mit `DECIMAL` | `--tables bestellungen`, Standardpfad und `--on-conflict update` | 0, 0 | Ziel: `Betrag` `"12.50"`, `"3.25"` |
| `data profile` (Negativfall) | `--tables kunden` | 5 | `[ERROR] Profiling failed: Failed to list tables: Unsupported SQL.`; Log `request_failed`, `TDS_UNSUPPORTED_SQL` |
| Bestand aus der Datei | `schema reverse` und `data export` nach Start | 0 | `kunden`, `bestellungen`, leer |
| Neustart setzt zurück | `make testserver-down`, `make testserver-up`, danach `schema reverse` und `data export` | 0 | keine Spalte `Email`, `kunden.json` ist `[]` (vorher 4 Zeilen und `Email`) |
| `make testserver-up` | mit Name, Port, Image gesetzt | 0 | `bereit auf 127.0.0.1:14577`; unter Last 3 bis 5: 36 s (Bau), 3 bis 4 s (Cache) |
| `make testserver-status` | laufend / nach `down` | 0 / 2 | `laeuft (val-abn, 127.0.0.1:14577)` / `nicht gestartet`, Make-Fehler 1, Exit 2 |
| `make testserver-logs` | | 0 | Ereignisse `connection_opened`, `encryption_not_offered`, `login_accepted`, `connection_closed`, `request_failed` |
| `make testserver-down` | zweimal | 0, 0 | `gestoppt und entfernt`; ohne Container `kein Container val-abn, nichts zu tun` |

## B. Aufgabe 7: Elemente nach der Anleitung

| Schritt | Exit | Beobachtung |
|---|---|---|
| 2 `compare` mit `soll-elemente.yaml` | 1 | `DIFFERENT`, `Tables removed: 2` (`auftraege`, `lieferanten`) |
| 3 `migrate --execute` | 0 | `ok`, `statementsAttempted` 3, `transactionRolledBack` false |
| 4 `compare` | 0 | `IDENTICAL` |
| 5 `schema reverse` | 0 | `auftraege`: `default: 1`, `default: neu`, Index `ix_auftraege_status`, Prüfbedingung `'"Menge">(0)'`; der Fremdschlüssel steht als Eintrag in `constraints` (`type: foreign_key`, `references.table`, `on_delete: cascade`), nicht als `references` an der Spalte |
| 6 `soll-elemente2.yaml` nach Neustart | 1, 0, 0 | `compare` nennt Index, Fremdschlüssel, Prüfbedingung, Eindeutigkeit; `migrate` `ok` mit 4 Anweisungen; `compare` danach `IDENTICAL` |
| 7 zweiter `migrate` mit `soll-elemente.yaml` | 0 | `ok`, 2 Anweisungen (`DROP CONSTRAINT IF EXISTS [ck_auftraege_menge]`, `ADD CONSTRAINT … CHECK (Menge > 0)`), `compare` danach `IDENTICAL` |

## C. Aufgabe 5: Einstellungen

| Fall | Ergebnis |
|---|---|
| `REQUIRED` mit Speicher, Passwortdatei und `pbkdf2`-Liste | `bereit`, Exit 0; `schema reverse`, `schema compare` (Exit 1), `schema migrate --execute` (Exit 0), `data import` (Exit 0) und `data export` (Exit 0) über TLS; `encrypt=false` oder `encrypt=true` ohne Vertrauen: Exit 4 |
| eigene Benutzer `bob` | Rohbytes-Client `Login ok` (Exit 0); `alice` danach `Login fehlgeschlagen` |
| Rohbytes-Client, `alice` | `laufzeit-client: Login ok`, Exit 0; mit falschem Kennwort Exit 1 und Gradle-Meldung `BUILD FAILED` wie in der Anleitung |
| eigene Tabellen (`artikel`) | `schema reverse` zeigt `artikel` mit `default: ohne` und Index `ix_artikel_bez` statt `kunden` und `bestellungen` |
| Beschreibung mit `type: uuid` | `der Server wurde nicht bereit`, Zeile `Beschreibung der Tabellen ungueltig: tables.artikel.columns.Nr.type (Zeile 8, Spalte 15): Typ uuid ohne Entsprechung`, Make-Exit 2; kein Container bleibt stehen |
| nicht gelaufen | belegter Port, Host `0.0.0.0`, Datei über 1 MiB, `LOG_LEVEL=DEBUG`, Zugriff von einem anderen Rechner, Microsoft.Data.SqlClient am Handlauf |

## D. Zusatzfälle (Soll gegen den laufenden Testserver, kumulativ)

Jeder Fall: `schema migrate --execute`, danach `schema compare`. Die Ausgangslage ist der Stand der Anleitung (`soll-elemente.yaml` angewendet).

| Fall | Änderung im Soll | migrate | compare | gesendet (Auszug) |
|---|---|---|---|---|
| Index ändern | `columns: [Status]` zu `[Status, Menge]` | 0 | 0, `IDENTICAL` | `DROP INDEX IF EXISTS [ix_auftraege_status] ON [auftraege]`, `CREATE INDEX … ([Status], [Menge])` |
| Index umbenennen | Name `ix_auftraege_status2` | 0 | 0 | `DROP INDEX IF EXISTS [ix_auftraege_status]`, `CREATE INDEX [ix_auftraege_status2]` |
| Index hinzufügen | `ix_auftraege_lief` auf `LieferantId` | 0 | 0 | `CREATE INDEX [ix_auftraege_lief]` |
| Index entfernen | beide Indizes weg | 0 | 0 | zwei `DROP INDEX IF EXISTS` |
| Standardwert geändert | `default: 1` zu `2` | 0 | 0 | Text mit `DECLARE @df …`, `ADD CONSTRAINT [df_auftraege_Menge] DEFAULT 2` |
| Standardwert neu | `kunden.Name` `default: unbekannt` | 0 | 0 | Text mit `DECLARE @df …`, `ADD CONSTRAINT [df_kunden_Name] DEFAULT N'unbekannt'` |
| Standardwert weggenommen | `default: neu` gestrichen | 0 | 0 | nur der Text mit `DECLARE @df …` |
| Prüfbedingung ändern | `Menge > 1` | 0 | 0 | `DROP CONSTRAINT IF EXISTS`, `ADD CONSTRAINT … CHECK (Menge > 1)` |
| Prüfbedingung entfernen | Block `constraints` gestrichen | 0 | 0 | `DROP CONSTRAINT IF EXISTS [ck_auftraege_menge]` |
| Fremdschlüssel ändern | `on_delete: cascade` zu `restrict` | 5 | 1, `DIFFERENT` | `DROP CONSTRAINT IF EXISTS [fk_auftraege_LieferantId]`, `ADD CONSTRAINT [_fk_LieferantId] … ON DELETE NO ACTION`; Fund 3 |
| Fremdschlüssel entfernen | `references` gestrichen | 0 | 0 | `DROP CONSTRAINT IF EXISTS [_fk_LieferantId]` |
| Fremdschlüssel neu | `references … cascade` | 0 | 0 | `ADD CONSTRAINT [_fk_LieferantId] … ON DELETE CASCADE` |
| Fremdschlüssel ändern | `cascade` zu `no_action` | 5 | 1 | wie oben; Unterschied nur `on_delete=no_action` gegen keinen Wert; Fund 3 |
| Prüfbedingung neu | `ck_auftraege_menge2` | 5 | 1 | Anweisung gelingt; Exit 5 nur wegen des Fremdschlüssels davor |
| Eindeutigkeit entfernen | `unique: true` an `lieferanten.Name` gestrichen | 5 | 1 | `DROP CONSTRAINT IF EXISTS [uq_lieferanten_Name]` gelingt; Exit 5 nur wegen des Fremdschlüssels |
| Eindeutigkeit neu | `unique: true` an `kunden.Name`, `lieferanten.Name` | 5 | 1 | `ADD CONSTRAINT [_unique_Name] UNIQUE` an beiden gelingt; Exit 5 nur wegen des Fremdschlüssels |
| Fremdschlüssel ohne `on_delete` | `on_delete` gestrichen | 0 | 0 | konvergiert, `IDENTICAL` |
| eindeutiger Index neu | `indices: unique: true` an `bestellungen.KundenId` | 5 | 1 | `CREATE UNIQUE INDEX [ux_best_kunde]` gelingt; der Vergleich liest `constraint _unique_KundenId` gegen `index ux_best_kunde`; Exit 5 `Post-execute compare detected drift` |
| eindeutigen Index entfernen | `indices` gestrichen | 5 | 1 | `ALTER TABLE [bestellungen] DROP CONSTRAINT IF EXISTS [_unique_KundenId]`; der Index bleibt; Exit 5 |

Die Zeilen „Prüfbedingung neu“ bis „Eindeutigkeit neu“ sind in der Wirkung getrennt gelesen: der Vergleich nennt in allen drei Fällen nur den Fremdschlüssel als Unterschied.

## E. Funde

Nummern: F1 bis F4, getrennt nach (a) Fehler im Server, (b) Lücke oder Fehler der Anleitung, (c) Verhalten gegen den echten SQL Server abweichend, aber Setzung der Spezifikation, (d) Verhalten des Clients (d-migrate): Server, Testserver und echter SQL Server verhalten sich gleich.

| Nr | Art | Befund | Befehl, Exit, Auszug | Erwartung nach der Anleitung |
|---|---|---|---|---|
| F1 | (b) | Aufgabe 6 setzt die Beispiel-Tabellen im Anfangsstand voraus; wer Aufgabe 3 bis Schritt 4 gefahren hat, hat die Spalte `Email` in `kunden`, und `data import` mit der Datei der Anleitung endet mit Exit 5 | `data import --source /work/kunden-ein.json --table kunden`, Exit 5, `Error: Import failed: Source and destination schemas do not match.` | Aufgabe 6 Schritt 2: Exit 0, `2 inserted`; die Anleitung sagt „Server läuft mit den Voreinstellungen, `kunden` leer“ und nennt keinen Neustart nach Aufgabe 3 |
| F2 | (b) | `data transfer` kommt in der Anleitung nur in Eigenschaften vor, ohne Schritte: nötig sind zwei Server (zwei Ports, zwei Container) und gleiche Struktur; sonst bricht der Vorabtest ab | `data transfer --source <Server 1> --target <Server 2> --tables kunden`, Exit 3, `Error […]: Preflight: Column 'kunden.Email' missing in target` | Aufgabe 6 „`data transfer` mit `--on-conflict` läuft ebenso“; Exit 3 und die Voraussetzung stehen nirgends |
| F3 | (d) | Ein Fremdschlüssel mit `on_delete: restrict` oder `no_action` im Soll endet bei `schema migrate --execute` mit Exit 5, danach `DIFFERENT`: d-migrate sendet `ON DELETE NO ACTION`, liest den Fremdschlüssel ohne `on_delete` zurück; der Name wird dabei `_fk_<Spalte>` statt des Namens der ersten Anlage. Der echte SQL Server 2022 liefert dasselbe (Exit 5): d-migrate liest `NO_ACTION` ohne `on_delete`, keine Abweichung des Testservers, siehe [Messprotokoll zu F3](fremdschluessel-on-delete-d-migrate-2026-11-12.md) | `schema migrate --execute`, Exit 5, `Post-execute compare detected drift`; `compare`: `constraint _fk_LieferantId (… on_delete=no_action) -> _fk_LieferantId (…)` | Aufgabe 7 nennt als Grenze nur den eindeutigen Index; ein Soll mit `restrict` oder `no_action` ist nicht erwähnt |
| F4 | (b) | Kleinigkeiten: die Verbindungszeichenfolgen der Anleitung tragen fest Port 1433, obwohl Aufgabe 1 für einen belegten Port `TESTSERVER_PORT` nennt; der Fremdschlüssel erscheint in `ist-elemente.yaml` als Eintrag in `constraints`, nicht als `references` an der Spalte | alle Befehle der Aufgaben 3, 6, 7 | Hinweis, den Port in `--source`/`--target` mitzuführen |

Funde der Art (a): keine; F3 ist Art (d). Der Server hat in keinem Lauf eine Zusage der Anleitung verfehlt.

## F. Zuordnung zum Register (nach dem Lauf gelesen)

Register: `docs/plan/planning/observations/BEO-TDS/katalog-elemente-grenzen-der-angleichung/`, Punkte 9 bis 15.

| Punkt | Getroffen | Beleg dieses Laufs |
|---|---|---|
| 9 `DROP INDEX [IF EXISTS]` | ja, wie beschrieben | Index ändern, umbenennen, entfernen: Exit 0, `IDENTICAL` |
| 10 zweites ERROR 3727 | nein | kein `DROP CONSTRAINT` ohne Treffer mit Auswertung der Folgefehler; d-migrate sendet `IF EXISTS` |
| 11 Name von Standardwert und Schlüssel | nein | d-migrate sendet den Text mit `DECLARE @df …`, nie `DROP CONSTRAINT <df-Name>` |
| 12 Verweis (3725) | nein | kein Schlüssel einer referenzierten Tabelle entfernt |
| 13 Antwort auf `DROP INDEX` im Einzelnen | nur Teil (a) in der Wirkung | `CREATE INDEX` und `DROP INDEX` ohne `COUNT` werden von d-migrate angenommen |
| 14 Entfernen eines eindeutigen Index | **ja** | `DROP CONSTRAINT IF EXISTS [_unique_KundenId]`, Index bleibt, Exit 5 `POST_EXECUTE_DRIFT`; dazu das Anlegen: der Vergleich liest den eindeutigen Index als Eindeutigkeit (Exit 5, Punkt 3 und Grenze der Anleitung) |
| 15 Grenzen der Form D7 | (a) berührt, (b) nein | Standardwert neu, geändert, weggenommen: Exit 0, `IDENTICAL`; zweiter Standardwert an derselben Spalte nicht gefahren (b), Transaktionszusage (c) nicht gefahren |
| nicht im Register | F3 | Fremdschlüssel mit `restrict` oder `no_action` |

## Grenzen dieses Protokolls

Einzelläufe auf einem Host; kein Lauf dieses Protokolls gegen einen echten SQL Server (F3 ist in der Nachmessung [fremdschluessel-on-delete-d-migrate-2026-11-12](fremdschluessel-on-delete-d-migrate-2026-11-12.md) zugeordnet); die Inhalte der Ausgabe sind Auszüge, die vollständigen Ausgaben liegen nur im Scratchpad des Laufs. Aufgabe 2 Schritt „Microsoft.Data.SqlClient“, Aufgabe 4 (Ebenen des Logs außer `INFO`), Host `0.0.0.0`, belegter Port und Dateien über 1 MiB sind nicht gelaufen.
