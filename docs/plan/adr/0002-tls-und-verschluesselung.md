# ADR-0002: TLS-Strategie und Verschlüsselungsmodi

**Status:** Proposed

**Datum:** 2026-10-29

**Autor:** pt9912

**Bezug:** [TDS-FA-01](../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung), [TDS-QA-03](../../../spec/lastenheft.md#tds-qa-03--sicherheit)

**Schärft:** [SPEC-043](../../../spec/spezifikation.md#spec-043--startprüfung-tls-konfiguration-und-port), [TDS-FA-09.a](../../../spec/spezifikation.md#tds-fa-09a--konfiguration-und-diagnose), [SPEC-028](../../../spec/spezifikation.md#spec-028--prelogin-tls-und-login7-zustands--und-antwortvertrag), [SPEC-010](../../../spec/spezifikation.md#3-defaults-und-konstanten), [SPEC-023](../../../spec/spezifikation.md#6-externe-verträge), [ARC-002](../../../spec/architecture.md#1-komponenten-übersicht), [ARC-013](../../../spec/architecture.md#3-externe-abhängigkeiten), [TDS-FA-01.a](../../../spec/spezifikation.md#tds-fa-01a--tcp-verbindung-und-prelogin)

---

## Kontext

TDS PRELOGIN handelt Verschlüsselung aus. Moderne .NET-Clients können Verschlüsselung standardmäßig verlangen; eine ungenaue TLS-Einbettung verhindert den Login.

## Entscheidung

**Offen — noch keine Option gewählt.** Diese ADR dokumentiert die zu treffende Entscheidung, nicht bereits einen Beschluss. Vor `Accepted` sind mit echtem SqlClient die PRELOGIN/TLS-Sequenz, die Zertifikatsprüfung und das Downgrade-Verhalten zu testen und die Betriebsanforderungen zu dokumentieren.

## Verglichene Alternativen

| Option                            | Pro                               | Contra                                                 |
| --------------------------------- | --------------------------------- | ------------------------------------------------------ |
| A — TLS obligatorisch             | Sicherer Betriebsdefault          | Zertifikatsbetrieb und TDS-TLS-Einbettung erforderlich |
| B — profilabhängige Modi          | Test- und Betriebsprofile möglich | Mehr Zustände und Konfigurationsrisiken                |
| C — TLS nur auf Client-Anforderung (Server erzwingt nichts) | Größte Clientkompatibilität | Stilles Downgrade möglich; widerspricht `SPEC-028` |
| 0 — keine Festlegung vor dem ersten TLS-Spike | Keine Vorfestlegung auf einen Modus | Blockiert `SPEC-010` und die TLS-Zustände in `SPEC-028` |

## Konsequenzen

- **Positiv nach Entscheidung:** Der Verschlüsselungsmodus je Betriebsprofil ist festgelegt und prüfbar.
- **Negativ / Risiko:** Zertifikatsbetrieb und die TLS-Kapselung im TDS-Transport erhöhen den Aufwand.
- **Folgepflicht:** `SPEC-010`, `SPEC-023` und `SPEC-028` auf den gewählten Modus festlegen und das Security-Profil anlegen.
- **Offen:** Erlaubte Testmodi und Zertifikatsvorgaben.

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
