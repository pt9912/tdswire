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

## Login-Antwort des Servers (Festlegung)

Was der Server auf PRELOGIN und LOGIN7 sendet, ist eine Festlegung ohne Messung am Produkt; sie sagt nicht, dass der Referenzclient sie annimmt. Gemessen am Referenzclient gegen den Spike-Testserver (TDS 7.4, ohne Pool) sind nur: LOGINACK mit DONE genügt dem Client beim `Open`, die Collation-ENVCHANGE braucht der RPC mit Zeichenparametern, und die Ablehnung braucht genau ein ERROR mit DONE ([ADR-0009](../docs/plan/adr/0009-authentifizierungsmodus.md), [ADR-0005](../docs/plan/adr/0005-rpc-und-prepared-statements.md)).

| Gegenstand | Festlegung des Servers (Package `protocol/session`) |
|---|---|
| PRELOGIN-Antwort | Pakettyp `0x04`; Optionen VERSION (`0.0.0.0`), ENCRYPTION, INSTOPT (leer), MARS (aus); kein FEDAUTHREQUIRED. ENCRYPTION ist ENCRYPT_REQ bei `REQUIRED`, ENCRYPT_NOT_SUP im ausdrücklich konfigurierten Testmodus |
| Annahme | Pakettyp `0x04`; ENVCHANGE Datenbank, Packet Size, SQL-Collation, dann LOGINACK (Interface 1, TDS-Version `0x74000004`, bei TDS 8.0 `0x08000000`) und DONE ohne Statusbits |
| Ablehnung | genau ein ERROR (18456, State 1, Class 14, Text "Login failed." ohne Kennung), dann DONE mit dem Fehlerbit; danach schließt der Server |
| Datenbank und Packet Size | die Datenbank aus LOGIN7, sonst `master`; die verlangte Packet Size, wenn der Paket-Codec sie schreiben kann (größer als der Paketkopf von 8 Byte und höchstens `MAX_TDS_PACKET_BYTES`), sonst 4096. Die Packet Size wird per ENVCHANGE angekündigt und steht in der Sitzung; **angewandt wird sie nicht**: der Paket-Schreiber des Transports schreibt mit der Größe, mit der er gebaut wird. Ein Client, der weniger als diese Größe verlangt, kann Pakete über seiner Größe sehen. Wer die Größe anwendet, ist nicht festgelegt |

## RPC-Katalog

Der Referenzclient sendete in den gemessenen Szenarien (Messgrenzen: Testserver, ohne echten SQL Server; TDS 7.4 unverschlüsselt, kein TDS 8.0, kein TLS; `Pooling=false`; eine Verbindung je Szenario; ein Lauf): Batch (`0x01`) für Text ohne Parameter; `sp_executesql` (ProcID 10) für Text mit Parametern; `sp_prepexec` (ProcID 13) und `sp_execute` (ProcID 12) bei `Prepare()` mit Parametern; RPC mit Prozedurnamen für `CommandType.StoredProcedure`. `sp_prepare`, `sp_unprepare` und `sp_cursor*` erschienen in diesen Szenarien nicht; unter Pooling ist das nicht gemessen. `ExecuteReader(SchemaOnly)` sendet einen Batch mit `SET FMTONLY OFF; SET FMTONLY ON;` im Text (Behandlung offen). `ExecuteReader(KeyInfo|SingleRow)` sendet `sp_executesql` mit `SET FMTONLY OFF; SET NO_BROWSETABLE ON;` im Text (Behandlung offen); Transaktionen sind Pakettyp `0x0E`, kein RPC (offen). Der Katalog ist als Entwurf in `SPEC-030` festgelegt und noch nicht freigegeben.

## Offen

`Encrypt`/`TrustServerCertificate`-Details, unterstützte RPCs (Freigabe des Entwurfs oben), MARS, Transaktionen, Verbindungs-Pooling und Lastprofil stehen in `SPEC-004` als offene Freigabepunkte. Die Authentifizierung ist dort festgelegt (SQL-Authentifizierung, [`auth-adapter.md`](auth-adapter.md)) und kein offener Freigabepunkt.
