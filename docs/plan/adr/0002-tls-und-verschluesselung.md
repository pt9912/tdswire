# ADR-0002: TLS-Strategie und Verschlüsselungsmodi

**Status:** Accepted

**Datum:** 2026-10-29

**Autor:** pt9912

**Bezug:** [TDS-FA-01](../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung), [TDS-QA-03](../../../spec/lastenheft.md#tds-qa-03--sicherheit)

**Schärft:** [SPEC-043](../../../spec/spezifikation.md#spec-043--startprüfung-tls-konfiguration-und-port), [TDS-FA-09.a](../../../spec/spezifikation.md#tds-fa-09a--konfiguration-und-diagnose), [SPEC-028](../../../spec/spezifikation.md#spec-028--prelogin-tls-und-login7-zustands--und-antwortvertrag), [SPEC-010](../../../spec/spezifikation.md#3-defaults-und-konstanten), [SPEC-023](../../../spec/spezifikation.md#6-externe-verträge), [ARC-002](../../../spec/architecture.md#1-komponenten-übersicht), [ARC-013](../../../spec/architecture.md#3-externe-abhängigkeiten), [TDS-FA-01.a](../../../spec/spezifikation.md#tds-fa-01a--tcp-verbindung-und-prelogin)

---

## Kontext

TDS PRELOGIN handelt Verschlüsselung aus. Moderne .NET-Clients können Verschlüsselung standardmäßig verlangen; eine ungenaue TLS-Einbettung verhindert den Login.

Gemessen mit `Microsoft.Data.SqlClient` 7.1.1 auf .NET 10 gegen einen Testserver mit Zertifikat `CN=localhost`:

- TDS 7.4, `Encrypt=true`, Server antwortet ENCRYPT_ON: Der Handshake in TDS-Paketen vom Typ `0x12` gelingt (TLS 1.3, `TLS_AES_256_GCM_SHA384`); LOGIN7 kommt danach verschlüsselt (`TDSVersion=0x74000004`).
- Antwortet der Server ENCRYPT_REQ, gelingt der Handshake mit `Encrypt=true` wie mit `Encrypt=false`; LOGIN7 kommt verschlüsselt.
- Antwortet der Server ENCRYPT_NOT_SUP, sendet ein Client mit `Encrypt=false` LOGIN7 unverschlüsselt; ein Client mit `Encrypt=true` bricht ab, es folgt kein LOGIN7.
- Antwortet der Server ENCRYPT_OFF, beginnt der Client den TLS-Handshake trotzdem; das Login ist auch dann TLS-geschützt.
- Ein nicht vertrautes Zertifikat scheitert beim Client mit `UntrustedRoot`, in TDS 7.4 mit `TrustServerCertificate=false` und bei `Encrypt=Strict` (auch mit `TrustServerCertificate=true`). In TDS 7.4 mit `TrustServerCertificate=true` gelingt der Handshake auch mit nicht vertrautem Zertifikat.
- `Encrypt=Strict`: TLS 1.3 mit `TLS_AES_256_GCM_SHA384`, ALPN `tds/8.0`, danach PRELOGIN und LOGIN7 mit `TDSVersion=0x08000000`; bei falschem Namen bricht der Client nach dem Handshake ab.
- Nicht geklärt: `HostNameInCertificate` mit abweichendem Namen wurde im TDS-7.4-Pfad nicht beanstandet; ENCRYPT_ON auf einen Client mit `Encrypt=false` scheiterte serverseitig im Testserver.

## Entscheidung

Wir wählen **Option B**: profilabhängige Modi mit dem Standard `REQUIRED` (`SPEC-010`).

- **Standard:** TLS ist verlangt. Im klassischen TDS-7.4-Pfad antwortet der Server in PRELOGIN mit ENCRYPT_REQ (oder ENCRYPT_ON) und kapselt den Handshake in TDS-Pakete (`SPEC-028`); im TDS-8.0-Pfad (`Encrypt=Strict`) läuft TLS ab dem ersten Byte mit ALPN `tds/8.0`. Beide Pfade gehören zum Clientprofil ([ADR-0001](0001-tds-version-und-clientprofil.md)).
- **Abschwächung:** Ein unverschlüsselter Testmodus (PRELOGIN ENCRYPT_NOT_SUP, nur TDS 7.4) entsteht nur durch ausdrückliche Konfiguration, die im Log sichtbar ist (`SPEC-010`). Einen Rückfall ohne diese Konfiguration gibt es nicht (`SPEC-043`).
- **Zertifikate in Tests:** Der Server nutzt ein Testzertifikat, dem der Referenzclient vertraut (Trust-Store des Testcontainers). `TrustServerCertificate=true` genügt nur im TDS-7.4-Pfad; `Encrypt=Strict` ignoriert es.
- **Nachweis:** Ein Spike mit einem Testserver und dem Referenzclient hat Handshake, Zertifikatsprüfung und das Verhalten bei abweichenden Server-Antworten gemessen (siehe Kontext). Das Sicherheitsprofil `contracts/security-profile.md` hält Modi und Zertifikatsvorgaben fest; Mindest-Protokollversion und zugelassene Suiten sind offen.

## Verglichene Alternativen

| Option                            | Pro                               | Contra                                                 |
| --------------------------------- | --------------------------------- | ------------------------------------------------------ |
| A — TLS obligatorisch             | Sicherer Betriebsdefault          | Zertifikatsbetrieb und TDS-TLS-Einbettung erforderlich |
| **B — profilabhängige Modi**      | Test- und Betriebsprofile möglich; Standard `REQUIRED` entspricht `SPEC-010` | Mehr Zustände und Konfigurationsrisiken                |
| C — TLS nur auf Client-Anforderung (Server erzwingt nichts) | Größte Clientkompatibilität | Stilles Downgrade möglich; widerspricht `SPEC-028` |
| 0 — keine Festlegung vor dem ersten TLS-Spike | Keine Vorfestlegung auf einen Modus | Blockiert `SPEC-010` und die TLS-Zustände in `SPEC-028` |

## Konsequenzen

- **Positiv:** Der Verschlüsselungsmodus je Betriebsprofil ist festgelegt und prüfbar.
- **Negativ / Risiko:** Zertifikatsbetrieb und die TLS-Kapselung im TDS-Transport erhöhen den Aufwand.
- **Folgepflicht:** `SPEC-010`, `SPEC-023` und `SPEC-028` auf den gewählten Modus festlegen und das Security-Profil `contracts/security-profile.md` anlegen. Tests im TDS-8.0-Pfad brauchen ein vom Client vertrautes Zertifikat.
- **Offen:** Mindest-TLS-Version und zugelassene Suiten (`SPEC-023`) sind noch nicht gemessen. Die Freigabe des TLS-Modus für den Produktivbetrieb bleibt eine Entscheidung der Rolle Betrieb (Lastenheft, Offene Entscheidungen); diese ADR legt nur Standard und Abschwächung fest.

## Fitness Function (falls maschinell prüfbar)

| Tooling                                     | Regel                                                                                                                                 | Make-Target                         |
| ------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------- |
| JVM-/Protokolltests und .NET-Referenzclient | Eine Verbindung mit verlangter Verschlüsselung gelingt; wird die geforderte Verschlüsselung nicht erfüllt, bricht der Server vor LOGIN7 ab; kein stilles Downgrade. Noch kein bestandenes Gate. | — (Gate noch einzurichten) |

## Re-Evaluierungs-Trigger

Änderung des Client-Verschlüsselungsdefaults oder neue Sicherheitsrichtlinie.

## Geschichte

| Datum      | Ereignis                      | Verweis |
| ---------- | ----------------------------- | ------- |
| 2026-10-29 | Proposed – Entscheidung offen | —       |
| 2026-10-29 | Bezug auf `SPEC-043` und `TDS-FA-09.a` ergänzt; Entscheidung weiter offen | [SPEC-043](../../../spec/spezifikation.md#spec-043--startprüfung-tls-konfiguration-und-port) |
| 2026-10-29 | Accepted – Option B; Messung mit .NET 10 und `Microsoft.Data.SqlClient` 7.1.1; Messaussagen vor Abschluss des Slice nachgemessen und begrenzt | —       |
