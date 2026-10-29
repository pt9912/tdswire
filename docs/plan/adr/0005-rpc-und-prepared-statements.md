# ADR-0005: RPC-Unterstützung und Prepared Statements

**Status:** Proposed

**Datum:** 2026-10-29

**Autor:** pt9912

**Bezug:** [TDS-FA-04](../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-FA-10](../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte)

**Schärft:** [SPEC-030](../../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten), [SPEC-003](../../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog), [ARC-005](../../../spec/architecture.md#1-komponenten-übersicht), [ARC-007](../../../spec/architecture.md#1-komponenten-übersicht), [TDS-FA-04.a](../../../spec/spezifikation.md#tds-fa-04a--rpc-und-parameter), [TDS-FA-10.a](../../../spec/spezifikation.md#tds-fa-10a--mehrteilige-antworten-und-rpc-ausgabewerte)

---

## Kontext

Parametrisierte .NET-Kommandos nutzen RPC. Der tatsächlich erforderliche Satz an Prozeduren und Tokenantworten ist client- und API-abhängig.

## Entscheidung

**Offen — noch keine Option gewählt.** Diese ADR dokumentiert die zu treffende Entscheidung, nicht bereits einen Beschluss. Vor `Accepted` sind SqlCommand-Szenarien mit Parametern, Nullwerten, OUTPUT und Prepared-API gegen einen Referenz-SQL-Server zu beobachten; danach ist der Umfang festzulegen.

## Verglichene Alternativen

| Option                                       | Pro                        | Contra                                                 |
| -------------------------------------------- | -------------------------- | ------------------------------------------------------ |
| A — nur sp_executesql                        | Kleiner Anfangsumfang      | Prepared- und Output-Szenarien eventuell nicht nutzbar |
| B — clientprofilbasierter RPC-Mindestkatalog | Bedarfsgerecht und testbar | Erfordert Mitschnitte und Testfälle                    |
| C — umfassende RPC-Kompatibilität            | Breite API-Abdeckung       | Sehr großer Implementierungs- und Testaufwand          |
| 0 — keine Festlegung vor den Mitschnitten | Keine Vorfestlegung auf einen RPC-Umfang | Blockiert `SPEC-030` und `SPEC-003` |

## Konsequenzen

- **Positiv nach Entscheidung:** Der RPC-Umfang ist an das Clientprofil gebunden und für jede Form getestet.
- **Negativ / Risiko:** Ein vom Client später genutzter RPC-Typ ist nicht unterstützt, bis er nachgezogen wird.
- **Folgepflicht:** `SPEC-030` und `SPEC-003` auf den gewählten RPC-Katalog festlegen; Mitschnitte redigiert ablegen.
- **Offen:** Unterstützte RPC-Formen und Prepared-Statement-Umfang.

## Fitness Function (falls maschinell prüfbar)

| Tooling                                     | Regel                                                                                                                                 | Make-Target                         |
| ------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------- |
| JVM-/Protokolltests und .NET-Referenzclient | Eine parametrisierte Abfrage mit NULL und OUTPUT-Parameter liefert die erwarteten Werte; eine unbekannte RPC-Form ergibt einen definierten Fehler. Noch kein bestandenes Gate. | — (Gate noch einzurichten) |

## Re-Evaluierungs-Trigger

Neuer .NET-Aufruftyp erzeugt bisher nicht unterstützte RPC-Nachricht.

## Geschichte

| Datum      | Ereignis                      | Verweis |
| ---------- | ----------------------------- | ------- |
| 2026-10-29 | Proposed – Entscheidung offen | —       |
