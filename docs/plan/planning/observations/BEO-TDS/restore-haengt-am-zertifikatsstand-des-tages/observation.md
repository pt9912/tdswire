# Der Restore hängt am Zertifikatsstand des Tages: ein Wechsel macht ihn rot, ohne dass sich eine Datei im Repo ändert

**Sub-Area:** `*` (Build und Gates, Lieferkette)

`referenztest-dotnet/nuget.config` bindet den Restore an Fingerabdrücke von Zertifikaten, die dem Repo nicht gehören ([ADR-0035](../../../../adr/0035-dotnet-restore-signaturliste-und-audit-verzicht.md) Annahme (a)). Wechselt nuget.org sein Repository-Zertifikat oder ein Autor das seine, kann `make referenztest-dotnet`, `make referenztest-dotnet-lock` oder ein Frischlauf von `make gates` rot werden; Stempel und Cache verdecken das bis zum nächsten Frischlauf. Gemessen ist das nicht ([Messprotokoll](../../../../../messungen/dotnet-signaturpruefung-2026-11-06.md) §Rotation); gemessen ist nur, dass zwei bereits abgelaufene Autorenzertifikate den Restore nicht rot machen. Kein Wächter, kein Träger-Slice.
