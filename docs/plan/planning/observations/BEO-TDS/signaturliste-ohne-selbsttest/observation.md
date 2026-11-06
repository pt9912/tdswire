# Die Signaturliste des .NET-Restore hat keinen Selbsttest: der rote Fall ist einmal von Hand gemessen

**Sub-Area:** `*` (Build und Gates, Lieferkette)

`referenztest-dotnet/nuget.config` führt `signatureValidationMode=require` und `trustedSigners` ([ADR-0035](../../../../adr/0035-dotnet-restore-signaturliste-und-audit-verzicht.md)). Dass der Restore ohne passenden Eintrag bricht, ist im [Messprotokoll](../../../../../messungen/dotnet-signaturpruefung-2026-11-06.md) einmal von Hand gemessen; kein Gate mutiert die Liste und sieht den Restore rot werden. Ein Selbsttest wäre ein neues Gate und gehört nicht in den Slice, in dem die Liste entstand (Entscheidung des Nutzers 2026-11-06).
