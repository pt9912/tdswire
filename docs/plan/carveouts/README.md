# Carveouts — tdswire

Aktive Carveouts mit Auflösungs-Trigger. Aufgelöste Carveouts wandern nach
`done/` (reiner `git mv`). **Derivativ:** Quelle der Wahrheit sind die
Carveout-Dateien; bei jedem neuen oder aufgelösten Carveout mitziehen.

## Aktive Carveouts

| ID | Titel | Gate | Trigger | Folge-Slice |
|---|---|---|---|---|
| [CO-001](CO-001-nuget-audit-aus.md) | NuGet-Audit des .NET-Referenztests ausgeschaltet | `make referenztest-dotnet` | `NuGetAudit` nicht mehr `false` oder ADR zum Verzicht | [slice-qa-dotnet-lieferkette-haerten](../planning/open/slice-qa-dotnet-lieferkette-haerten.md) |
| [CO-002](CO-002-nuget-signaturpruefung-fehlt.md) | Keine Signaturprüfung der NuGet-Pakete | `make referenztest-dotnet` | `trustedSigners` mit `require` grün oder ADR zum Verzicht | [slice-qa-dotnet-lieferkette-haerten](../planning/open/slice-qa-dotnet-lieferkette-haerten.md) |

## Aufgelöste Carveouts

(noch keine)

## Konventionen

- Jeder aktive Carveout braucht: Trigger, Folge-Slice, letzten Prüf-Termin.
- Bei Welle-Closure: Carveout-Audit zwingend — welche gültig, welche aufgelöst?
- Siehe Baseline-Regelwerk `modul-07-carveouts.md`.
