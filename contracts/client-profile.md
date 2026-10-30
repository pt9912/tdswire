# Clientprofil

Versioniertes Profil des Referenzclients für die Kompatibilitätsabnahme (`SPEC-004`, `SPEC-021`).

| Eigenschaft | Wert |
|---|---|
| .NET | .NET 10, SDK 10.0.403 |
| Paket | `Microsoft.Data.SqlClient` 7.1.1 (exakt) |
| Protokoll | TDS 7.4 und TDS 8.0 |
| Nicht im Profil | `System.Data.SqlClient`, andere Paket- und Laufzeitversionen |

## Verbindungsmodi (ohne Secrets)

| Modus | Connection-String-Optionen | Protokollverhalten |
|---|---|---|
| TDS 7.4, ohne TLS (Testmodus nur durch ausdrückliche Konfiguration, siehe `contracts/security-profile.md`) | `Encrypt=false` | PRELOGIN-Option ENCRYPTION `00`, LOGIN7 `TDSVersion=0x74000004`, Paketgröße 8000 |
| TDS 7.4, mit TLS | `Encrypt=true;TrustServerCertificate=true` | ENCRYPTION `01`, der TLS-Handshake läuft in TDS-Paketen vom Typ `0x12`, danach kommt LOGIN7 verschlüsselt |
| TDS 8.0 | `Encrypt=Strict;HostNameInCertificate=<Name>` | TLS 1.3 ab dem ersten Byte, ALPN `tds/8.0`, danach PRELOGIN und LOGIN7 `TDSVersion=0x08000000`; `TrustServerCertificate` gilt nicht, das Zertifikat muss dem Client vertrauenswürdig sein |

## Beobachtete Werte

- PRELOGIN-Option VERSION: `07010001A066`.
- PRELOGIN-Option FEDAUTHREQUIRED: `01`.
- LOGIN7 AppName und CltIntName: `Core Microsoft SqlClient Data Provider`.

## RPC-Katalog

Der Referenzclient sendete in den gemessenen Szenarien (Messgrenzen: Testserver, ohne echten SQL Server; TDS 7.4 unverschlüsselt, kein TDS 8.0, kein TLS; `Pooling=false`; eine Verbindung je Szenario; ein Lauf): Batch (`0x01`) für Text ohne Parameter; `sp_executesql` (ProcID 10) für Text mit Parametern; `sp_prepexec` (ProcID 13) und `sp_execute` (ProcID 12) bei `Prepare()` mit Parametern; RPC mit Prozedurnamen für `CommandType.StoredProcedure`. `sp_prepare`, `sp_unprepare` und `sp_cursor*` erschienen in diesen Szenarien nicht; unter Pooling ist das nicht gemessen. `ExecuteReader(KeyInfo|SingleRow)` sendet `sp_executesql` mit `SET FMTONLY OFF; SET NO_BROWSETABLE ON;` im Text (Behandlung offen); Transaktionen sind Pakettyp `0x0E`, kein RPC (offen). Der Katalog ist als Entwurf in `SPEC-030` festgelegt und noch nicht freigegeben.

## Offen

`Encrypt`/`TrustServerCertificate`-Details, SQL-Authentifizierung, unterstützte RPCs (Freigabe des Entwurfs oben), MARS, Transaktionen, Verbindungs-Pooling und Lastprofil stehen in `SPEC-004` als offene Freigabepunkte.
