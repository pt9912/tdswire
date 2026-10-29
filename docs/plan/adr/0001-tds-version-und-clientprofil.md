# ADR-0001: TDS-Version und .NET-Kompatibilitätsprofil

**Status:** Proposed

**Datum:** 2026-10-29

**Autor:** pt9912

**Bezug:** [TDS-FA-01](../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung), [TDS-QA-01](../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-RB-02](../../../spec/lastenheft.md#tds-rb-02--externer-client), [TDS-RB-03](../../../spec/lastenheft.md#tds-rb-03--protokollstandard)

**Schärft:** [SPEC-020](../../../spec/spezifikation.md#6-externe-verträge), [SPEC-021](../../../spec/spezifikation.md#6-externe-verträge), [SPEC-004](../../../spec/spezifikation.md#spec-004--testprofil), [ARC-010](../../../spec/architecture.md#3-externe-abhängigkeiten), [ARC-011](../../../spec/architecture.md#3-externe-abhängigkeiten), [TDS-FA-01.a](../../../spec/spezifikation.md#tds-fa-01a--tcp-verbindung-und-prelogin)

---

## Kontext

Der .NET-SqlClient bestimmt durch Version, Verbindungsoptionen und tatsächlich gesendete Nachrichten den zu unterstützenden TDS-Funktionsumfang. Ohne ein reproduzierbares Profil sind Konformitätsaussagen nicht prüfbar.

Diese ADR legt fest, **was** das Profil enthält (Protokollversion, Clientpaket und -version, Verbindungsoptionen); die Testmatrix und ihre Ausführung in der CI entscheidet ADR-0008.

## Entscheidung

**Offen — noch keine Option gewählt.** Diese ADR dokumentiert die zu treffende Entscheidung, nicht bereits einen Beschluss. Vor `Accepted` sind Clientpaket, Version, TDS-Handshake und Testfälle in einem versionierten Clientprofil nachzuweisen; erst dann ist eine Option zu wählen.

## Verglichene Alternativen

| Option                                                   | Pro                                | Contra                                                |
| -------------------------------------------------------- | ---------------------------------- | ----------------------------------------------------- |
| A — festes TDS-7.4-Profil                                | Begrenzter Umfang und klare Tests  | Kann mit dem gewählten Clientprofil inkompatibel sein |
| B — ausgehandeltes Profil für definierte Clientversionen | Zielgerichtete Kompatibilität      | Mehr Testmatrix und Negotiation-Aufwand               |
| C — möglichst breite TDS-Versionsabdeckung               | Mehr Clients potenziell erreichbar | Hoher Aufwand und schwer abgrenzbar                   |
| 0 — keine Festlegung vor dem ersten Handshake-Spike | Keine Vorfestlegung auf eine TDS-Version | Blockiert `SPEC-020`, `SPEC-021` und das Clientprofil |

## Konsequenzen

- **Positiv nach Entscheidung:** Das Clientprofil ist versioniert; Kompatibilitätsaussagen beziehen sich auf eine benannte Clientversion.
- **Negativ / Risiko:** Clients außerhalb des Profils sind nicht zugesagt; jede neue Clientversion braucht eine Neubewertung.
- **Folgepflicht:** `SPEC-020`, `SPEC-021` und `SPEC-004` auf die gewählte Variante festlegen und das Clientprofil anlegen.
- **Offen:** TDS-Variante, Clientpaket und Version.

## Fitness Function (falls maschinell prüfbar)

| Tooling                                     | Regel                                                                                                                                 | Make-Target                         |
| ------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------- |
| JVM-/Protokolltests und .NET-Referenzclient | Der .NET-Referenzclient öffnet im festgelegten Profil eine Verbindung; ein Client außerhalb des Profils wird kontrolliert abgewiesen. Noch kein bestandenes Gate. | — (Gate noch einzurichten) |

## Re-Evaluierungs-Trigger

Ein neuer unterstützter .NET-Client oder ein fehlgeschlagener Handshake im verbindlichen Profil.

## Geschichte

| Datum      | Ereignis                      | Verweis |
| ---------- | ----------------------------- | ------- |
| 2026-10-29 | Proposed – Entscheidung offen | —       |
