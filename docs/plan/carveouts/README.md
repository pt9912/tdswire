# Carveouts — tdswire

Aktive Carveouts mit Auflösungs-Trigger. Aufgelöste Carveouts wandern nach
`done/` (reiner `git mv`). **Derivativ:** Quelle der Wahrheit sind die
Carveout-Dateien; bei jedem neuen oder aufgelösten Carveout mitziehen.

## Aktive Carveouts

| ID | Titel | Gate | Trigger | Folge-Slice |
|---|---|---|---|---|
| [CO-001](CO-001-nuget-audit-aus.md) | NuGet-Audit des .NET-Referenztests ausgeschaltet | `make referenztest-dotnet` | Folge-Slice in `done/` und `NuGetAudit` nicht mehr `false` oder ADR `Accepted` zum Verzicht (nennt `make referenztest-dotnet-audit` als Ersatz) | [slice-qa-dotnet-lieferkette-haerten](../planning/in-progress/slice-qa-dotnet-lieferkette-haerten.md) |
| [CO-002](CO-002-nuget-signaturpruefung-fehlt.md) | Keine Signaturprüfung der NuGet-Pakete | `make referenztest-dotnet` | Folge-Slice in `done/` und `trustedSigners` mit `require` grün oder ADR `Accepted` zum Verzicht | [slice-qa-dotnet-lieferkette-haerten](../planning/in-progress/slice-qa-dotnet-lieferkette-haerten.md) |
| [CO-004](CO-004-cancel-szenario-spec-040-fehlt.md) | Cancel-Szenario des Integrationslaufs nach SPEC-040 fehlt | `make simulations-integration` | Folge-Slice in `done/` und Cancel-Szenario mit Folgeabfrage am Referenzclient bestanden (Option A vom Nutzer bestätigt 2026-10-31) | [slice-cancel-referenzclient-szenario](../planning/next/slice-cancel-referenzclient-szenario.md) (Voraussetzung: [slice-attention-cancel-timeout](../planning/done/slice-attention-cancel-timeout.md)) |

## Aufgelöste Carveouts

| ID | Titel | Aufgelöst | Folge-Slice |
|---|---|---|---|
| [CO-003](done/CO-003-anmeldedrosselung-fehlt.md) | Keine Drosselung wiederholter Anmeldeversuche | 2026-11-01, [ADR-0016](../adr/0016-anmeldedrosselung.md) `Accepted` und Folge-Slice in `done/` | [slice-qa-anmeldedrosselung](../planning/done/slice-qa-anmeldedrosselung.md) |

## Konventionen

- Jeder aktive Carveout braucht: Trigger, Folge-Slice, letzten Prüf-Termin.
- Bei Welle-Closure: Carveout-Audit zwingend — welche gültig, welche aufgelöst?
- Siehe Baseline-Regelwerk `modul-07-carveouts.md`.
