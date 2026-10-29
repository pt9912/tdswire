# ADR-0001: TDS-Version und .NET-Kompatibilitätsprofil

**Status:** Accepted

**Datum:** 2026-10-29

**Autor:** pt9912

**Bezug:** [TDS-FA-01](../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung), [TDS-QA-01](../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-RB-02](../../../spec/lastenheft.md#tds-rb-02--externer-client), [TDS-RB-03](../../../spec/lastenheft.md#tds-rb-03--protokollstandard)

**Schärft:** [SPEC-020](../../../spec/spezifikation.md#6-externe-verträge), [SPEC-021](../../../spec/spezifikation.md#6-externe-verträge), [SPEC-004](../../../spec/spezifikation.md#spec-004--testprofil), [ARC-010](../../../spec/architecture.md#3-externe-abhängigkeiten), [ARC-011](../../../spec/architecture.md#3-externe-abhängigkeiten), [TDS-FA-01.a](../../../spec/spezifikation.md#tds-fa-01a--tcp-verbindung-und-prelogin)

---

## Kontext

Der .NET-SqlClient bestimmt durch Version, Verbindungsoptionen und tatsächlich gesendete Nachrichten den zu unterstützenden TDS-Funktionsumfang. Ohne ein reproduzierbares Profil sind Konformitätsaussagen nicht prüfbar.

Gemessen mit dem Referenzclient (.NET 10, `Microsoft.Data.SqlClient` 7.1.1): LOGIN7 trägt bei `Encrypt=false` `TDSVersion=0x74000004` und Paketgröße 8000; unter .NET 8 und mit `System.Data.SqlClient` 4.9.1 sind diese beiden Werte gleich, PRELOGIN-Option VERSION und AppName unterscheiden sich. Bei `Encrypt=true` bricht der Testserver ohne TLS-Unterstützung ab, es folgt kein LOGIN7; der Client beginnt den TLS-Handshake in Paketen vom Typ `0x12` (Nutzlast beginnt mit `160301`, der Abschluss des Handshakes ist nicht gemessen). Mit `Microsoft.Data.SqlClient` 7.1.1 auf .NET 10 gemessen: Bei `Encrypt=Strict` beginnt die Verbindung mit TLS 1.3 und ALPN `tds/8.0`, danach folgen PRELOGIN und LOGIN7 mit `TDSVersion=0x08000000`; `TrustServerCertificate` gilt dort nicht, das Serverzertifikat muss dem Client vertrauenswürdig sein.

Diese ADR legt fest, **was** das Profil enthält (Protokollversion, Clientpaket und -version, Verbindungsoptionen); die Testmatrix und ihre Ausführung in der CI entscheidet ADR-0008.

## Entscheidung

Wir wählen **Option B**: ein ausgehandeltes Profil für definierte Clientversionen. Das Profil umfasst:

- **Client:** `Microsoft.Data.SqlClient` 7.1.1 (exakt) auf .NET 10 (SDK 10.0.403). `System.Data.SqlClient` ist nicht Teil des Profils; der Compiler meldet `SqlConnection` dieses Pakets als veraltet (CS0618: „Use the Microsoft.Data.SqlClient package instead“), und es sendet bei `Encrypt=false` dieselbe TDS-Version.
- **Protokoll:** TDS 7.4 (LOGIN7 `TDSVersion=0x74000004`, klassischer Weg mit PRELOGIN und TLS in TDS-Paketen) und TDS 8.0 (`Encrypt=Strict`, TLS ab dem ersten Byte mit ALPN `tds/8.0`, LOGIN7 `TDSVersion=0x08000000`).
- **Nachweis:** Ein Spike mit einem Testserver und dem Referenzclient hat die Handshake-Abläufe beider Pfade gemessen (siehe Kontext). Die TLS-Strategie im Einzelnen (erlaubte Testmodi, Zertifikatsvorgaben) entscheidet [ADR-0002](0002-tls-und-verschluesselung.md).

## Verglichene Alternativen

| Option                                                   | Pro                                | Contra                                                |
| -------------------------------------------------------- | ---------------------------------- | ----------------------------------------------------- |
| A — festes TDS-7.4-Profil                                | Begrenzter Umfang und klare Tests  | Kann mit dem gewählten Clientprofil inkompatibel sein |
| **B — ausgehandeltes Profil für definierte Clientversionen** | Zielgerichtete Kompatibilität; beide gemessenen Pfade (TDS 7.4, TDS 8.0) sind abgedeckt | Mehr Testmatrix und Negotiation-Aufwand               |
| C — möglichst breite TDS-Versionsabdeckung               | Mehr Clients potenziell erreichbar | Hoher Aufwand und schwer abgrenzbar                   |
| 0 — keine Festlegung vor dem ersten Handshake-Spike | Keine Vorfestlegung auf eine TDS-Version | Blockiert `SPEC-020`, `SPEC-021` und das Clientprofil |

## Konsequenzen

- **Positiv:** Das Clientprofil ist versioniert; Kompatibilitätsaussagen beziehen sich auf eine benannte Clientversion.
- **Negativ / Risiko:** Clients außerhalb des Profils sind nicht zugesagt; jede neue Clientversion braucht eine Neubewertung.
- **Folgepflicht:** `SPEC-020`, `SPEC-021` und `SPEC-004` auf die gewählte Variante festlegen und das Clientprofil `contracts/client-profile.md` anlegen. Beide Protokollpfade brauchen Tests; TDS 8.0 setzt in jedem Test ein vertrauenswürdiges Serverzertifikat voraus.

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
| 2026-10-29 | Accepted – Option B; Messung mit .NET 10 und `Microsoft.Data.SqlClient` 7.1.1; Messaussagen vor Abschluss des Slice auf das Gemessene begrenzt | —       |
