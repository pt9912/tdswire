# Anmeldung mit mssql-jdbc nach der VERSION-Setzung: Messprotokoll vom 2026-11-05

Messprotokoll zu [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität) und [TDS-FA-01](../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung) (`AGENTS.md` §5 Regel 7), Träger: [slice-prelogin-serverversion](../plan/planning/in-progress/slice-prelogin-serverversion.md). Einzelmessung, ein Host (Linux, Docker), Datum 2026-11-05.

| Gegenstand | Wert |
|---|---|
| Client | d-migrate 1.12.0 (mssql-jdbc 13.4.0), Image `ghcr.io/pt9912/d-migrate@sha256:308f27753036c9cc423f716b9fc4c9e68016543a8003ecd2f8282e05a2ee6491`, `--network host` |
| Server | Testserver mit PRELOGIN-VERSION `16.0.1000.6` (Setzung), `make testserver-up` (Port 24861, Benutzer `alice`) |
| Aufruf | `schema reverse --source mssql://alice:***@127.0.0.1:24861/master?encrypt=false --output /tmp/rev.yaml` |
| Ergebnis | Exit 4, `Failed to initialize pool: Unsupported SQL.` |

Lesart: Die Meldung `SQL Server version 0 is not supported by this driver` kommt nicht mehr; PRELOGIN und LOGIN7 sind angenommen, der Treiber sendet Anweisungen (die Fehlermeldung kommt vom Server). Nicht untersucht: welche Anweisung `Unsupported SQL.` auslöste (Vermutung: der Verbindungstest `SELECT 1`, Hürde 2 der [Sonde](d-migrate-sonde-2026-11-05.md)); sie gehört in [slice-d-migrate-verbindung-und-export](../plan/planning/open/slice-d-migrate-verbindung-und-export.md) und wurde nicht behoben.
