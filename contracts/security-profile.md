# Sicherheitsprofil

TLS-Modi des Servers (`SPEC-010`, `SPEC-023`, `SPEC-028`, `SPEC-043`) für das Clientprofil in `contracts/client-profile.md`.

| Modus | Konfiguration | PRELOGIN-Antwort | Verhalten |
|---|---|---|---|
| `REQUIRED` (Standard), TDS 7.4 | `ENCRYPTION_POLICY=REQUIRED` | ENCRYPT_REQ oder ENCRYPT_ON (beide gemessen) | TLS-Handshake in TDS-Paketen vom Typ `0x12`, danach verschlüsseltes LOGIN7 |
| `REQUIRED`, TDS 8.0 | `ENCRYPTION_POLICY=REQUIRED`, Client `Encrypt=Strict` | — (TLS vor PRELOGIN) | TLS 1.3 ab dem ersten Byte, ALPN `tds/8.0` |
| unverschlüsselter Testmodus | ausdrückliche Konfiguration, im Log sichtbar | ENCRYPT_NOT_SUP | nur TDS 7.4; Clients mit `Encrypt=false` melden sich unverschlüsselt an, Clients mit `Encrypt=true` brechen ab |

## Zertifikate

- Der Client prüft die Kette: ein nicht vertrautes Zertifikat scheitert mit `UntrustedRoot`.
- `Encrypt=Strict` ignoriert `TrustServerCertificate`; das Zertifikat muss dem Client vertraut sein, der Name muss passen.
- `TrustServerCertificate=true` genügt im TDS-7.4-Pfad, auch bei einem nicht vertrauten Zertifikat.
- Tests nutzen ein Testzertifikat im Trust-Store des Testcontainers.

## Gemessen

TLS 1.3 mit `TLS_AES_256_GCM_SHA384` in beiden Pfaden (`Microsoft.Data.SqlClient` 7.1.1, .NET 10). Der unverschlüsselte Testmodus ist nur mit dem Referenzclient und `Encrypt=false` gemessen.

## Offen

Mindest-TLS-Version und zugelassene Suiten; das Verhalten von `HostNameInCertificate` im TDS-7.4-Pfad; ENCRYPT_ON gegen einen Client mit `Encrypt=false`.
