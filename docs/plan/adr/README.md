# ADR-Index — Kotlin/JVM TDS-Server

| ID | Titel | Status | Bezug |
|---|---|---|---|
| [ADR-0001](0001-tds-version-und-clientprofil.md) | TDS-Version und .NET-Kompatibilitätsprofil | Accepted | [TDS-FA-01](../../../spec/lastenheft.md), [TDS-QA-01](../../../spec/lastenheft.md), [TDS-RB-02](../../../spec/lastenheft.md), [TDS-RB-03](../../../spec/lastenheft.md) |
| [ADR-0002](0002-tls-und-verschluesselung.md) | TLS-Strategie und Verschlüsselungsmodi | Accepted | [TDS-FA-01](../../../spec/lastenheft.md), [TDS-QA-03](../../../spec/lastenheft.md) |
| [ADR-0003](0003-transportframework.md) | Transportframework und Netzwerk-Laufzeit | Accepted | [TDS-FA-01](../../../spec/lastenheft.md), [TDS-QA-02](../../../spec/lastenheft.md), [TDS-RB-01](../../../spec/lastenheft.md) |
| [ADR-0004](0004-session-zustandsautomat.md) | Session-Zustandsautomat und Protokollverarbeitung | Accepted | [TDS-FA-01](../../../spec/lastenheft.md), [TDS-FA-02](../../../spec/lastenheft.md), [TDS-FA-07](../../../spec/lastenheft.md) |
| [ADR-0005](0005-rpc-und-prepared-statements.md) | RPC-Unterstützung und Prepared Statements | Accepted | [TDS-FA-04](../../../spec/lastenheft.md), [TDS-FA-10](../../../spec/lastenheft.md) |
| [ADR-0006](0006-backend-vertrag-und-streaming.md) | Backend-Schnittstelle und Resultset-Streaming | Accepted | [TDS-FA-05](../../../spec/lastenheft.md), [TDS-FA-08](../../../spec/lastenheft.md), [TDS-FA-10](../../../spec/lastenheft.md), [TDS-FA-11](../../../spec/lastenheft.md) |
| [ADR-0007](0007-fehler-und-attention.md) | Fehlerbehandlung und ATTENTION-Semantik | Accepted | [TDS-FA-06](../../../spec/lastenheft.md), [TDS-FA-07](../../../spec/lastenheft.md), [TDS-QA-07](../../../spec/lastenheft.md) |
| [ADR-0008](0008-dotnet-kompatibilitaetstests.md) | Referenzclient und .NET-Kompatibilitätstests | Accepted | [TDS-QA-01](../../../spec/lastenheft.md), [TDS-QA-06](../../../spec/lastenheft.md), [TDS-FA-09](../../../spec/lastenheft.md) |
| [ADR-0009](0009-authentifizierungsmodus.md) | Authentifizierungsmodus und Authentifizierungsadapter | Accepted | [TDS-FA-02](../../../spec/lastenheft.md#tds-fa-02--anmeldung-und-sitzungsverwaltung), [TDS-QA-03](../../../spec/lastenheft.md#tds-qa-03--sicherheit) |
| [ADR-0010](0010-detekt-pre-release.md) | detekt 2.0 als Gate-Werkzeug (Pre-Release) | Accepted | [TDS-QA-05](../../../spec/lastenheft.md), [TDS-QA-06](../../../spec/lastenheft.md) |
| [ADR-0011](0011-pruefwertformat.md) | Prüfwertformat und Konfigurationsweg der festen Benutzerliste | Accepted | [ADR-0009](0009-authentifizierungsmodus.md), [TDS-FA-02](../../../spec/lastenheft.md#tds-fa-02--anmeldung-und-sitzungsverwaltung), [TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-03](../../../spec/lastenheft.md#tds-qa-03--sicherheit) |

## Konventionen

- `Accepted`-ADRs bleiben inhaltlich unverändert; Änderungen über neue supersedierende ADRs.
- `Schärft` zeigt auf die betroffenen Spec-Stellen, nicht umgekehrt.
- Entscheidung, Fitness Function und Geschichte vor Annahme vervollständigen.
