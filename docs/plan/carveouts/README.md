# Carveouts — tdswire

Aktive Carveouts mit Auflösungs-Trigger. Aufgelöste Carveouts wandern nach
`done/` (reiner `git mv`). **Derivativ:** Quelle der Wahrheit sind die
Carveout-Dateien; bei jedem neuen oder aufgelösten Carveout mitziehen.

## Aktive Carveouts

| ID | Titel | Gate | Trigger | Folge-Slice |
|---|---|---|---|---|
| – | keiner aktiv | | | |

## Aufgelöste Carveouts

| ID | Titel | Aufgelöst | Folge-Slice |
|---|---|---|---|
| [CO-001](done/CO-001-nuget-audit-aus.md) | NuGet-Audit des .NET-Referenztests ausgeschaltet | 2026-11-06, [ADR-0035](../adr/0035-dotnet-restore-signaturliste-und-audit-verzicht.md) `Accepted` (dauerhafter Verzicht, Ersatz `make referenztest-dotnet-audit`) und Folge-Slice in `done/` | [slice-qa-dotnet-lieferkette-haerten](../planning/done/slice-qa-dotnet-lieferkette-haerten.md) |
| [CO-002](done/CO-002-nuget-signaturpruefung-fehlt.md) | Keine Signaturprüfung der NuGet-Pakete | 2026-11-06, `trustedSigners` mit `require` grün ([ADR-0035](../adr/0035-dotnet-restore-signaturliste-und-audit-verzicht.md)) und Folge-Slice in `done/` | [slice-qa-dotnet-lieferkette-haerten](../planning/done/slice-qa-dotnet-lieferkette-haerten.md) |
| [CO-003](done/CO-003-anmeldedrosselung-fehlt.md) | Keine Drosselung wiederholter Anmeldeversuche | 2026-11-01, [ADR-0016](../adr/0016-anmeldedrosselung.md) `Accepted` und Folge-Slice in `done/` | [slice-qa-anmeldedrosselung](../planning/done/slice-qa-anmeldedrosselung.md) |
| [CO-004](done/CO-004-cancel-szenario-spec-040-fehlt.md) | Cancel-Szenario des Integrationslaufs nach SPEC-040 fehlt | 2026-11-06, Folge-Slice in `done/` und Cancel-Szenario in `make simulations-integration` bestanden | [slice-cancel-referenzclient-szenario](../planning/done/slice-cancel-referenzclient-szenario.md) |

## Konventionen

- Jeder aktive Carveout braucht: Trigger, Folge-Slice, letzten Prüf-Termin.
- Bei Welle-Closure: Carveout-Audit zwingend — welche gültig, welche aufgelöst?
- Siehe Baseline-Regelwerk `modul-07-carveouts.md`.
