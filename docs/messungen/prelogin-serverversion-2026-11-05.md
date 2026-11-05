# Anmeldung mit mssql-jdbc nach der VERSION-Setzung: Messprotokoll vom 2026-11-05

Messprotokoll zu [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität) und [TDS-FA-01](../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung) (`AGENTS.md` §5 Regel 7), Träger: [slice-prelogin-serverversion](../plan/planning/done/slice-prelogin-serverversion.md). Einzelmessung je Zeile, ein Host (Linux, Docker, `--network host`), Datum 2026-11-05; Last (`uptime`, Lastmittel 1/5/15 min): Lauf 0.0.0.0 gemessen 11:19 Ortszeit `7,67 7,68 9,85`; Lauf 16.0.1000.6 Last nicht erfasst (Host zur selben Zeit unter wechselnder Last, zuvor 5,6 bis 9,9 beobachtet).

Gleicher Client und gleicher Befehl in beiden Läufen; nur der Testserver-Stand (und der Port) unterscheidet sich.

| Gegenstand | Lauf A: VERSION `0.0.0.0` | Lauf B: VERSION `16.0.1000.6` (Setzung) |
|---|---|---|
| Testserver-Stand | Commit ea528ad, `make testserver-up`, Port 24862, Image `tdswire-testserver:prelogin-alt` | Commit fbd2a44, `make testserver-up`, Port 24861, Image `tdswire-testserver:prelogin` |
| Client | d-migrate 1.12.0 (mssql-jdbc 13.4.0 laut Sonde), Image `ghcr.io/pt9912/d-migrate@sha256:308f27753036c9cc423f716b9fc4c9e68016543a8003ecd2f8282e05a2ee6491` | gleich |
| Befehl | `schema reverse --source "mssql://alice:secret@127.0.0.1:<Port>/master?encrypt=false" --output <Datei>`, `docker run --rm --network host` | gleich |
| Exit | 4 | 4 |
| Ausgabe | `WARNING: ... SQLServerConnection prelogin ... Server major version:0 is not supported by this driver.` und `[ERROR] Connection or metadata error: Failed to initialize pool: SQL Server version 0 is not supported by this driver.` | `[ERROR] Connection or metadata error: Failed to initialize pool: Unsupported SQL.` |

Lesart, soweit die Ausgabe sie trägt: Lauf A scheitert mit der Treibermeldung zur Server-Hauptversion (`SQLServerConnection prelogin`, `Server major version:0`), also bei der Auswertung der PRELOGIN-Antwort; Lauf B nennt diese Meldung nicht mehr und scheitert mit einem Fehler des Servers (`Unsupported SQL.`). Dass in Lauf B PRELOGIN und LOGIN7 angenommen sind, folgt aus dem Wechsel der Meldung, nicht aus einem Mitschnitt; Exit 4 allein belegt es nicht. Nicht untersucht: welche Anweisung `Unsupported SQL.` auslöste (Vermutung: der Verbindungstest `SELECT 1`, Hürde 2 der [Sonde](d-migrate-sonde-2026-11-05.md)); sie gehört in [slice-d-migrate-verbindung-und-export](../plan/planning/open/slice-d-migrate-verbindung-und-export.md) und wurde nicht behoben. Nicht gemessen: der .NET-Referenzclient mit `16.0.1000.6` gesondert (die Referenztests prüfen `ServerVersion` nur auf nicht leer und laufen im Gate grün).
