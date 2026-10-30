# CO-002: Keine Signaturprüfung der NuGet-Pakete im .NET-Referenztest

**Status:** Aktiv.

**Datum angelegt:** 2026-10-30. **Letzte Prüfung:** 2026-10-30.

**Betroffenes Gate:** `make referenztest-dotnet`

**Geltungsbereich:** `referenztest-dotnet/nuget.config` (`trustedSigners` und `signatureValidationMode` fehlen)

**Folge-Slice:** [`slice-qa-dotnet-lieferkette-haerten.md`](../planning/open/slice-qa-dotnet-lieferkette-haerten.md)

Regeln: Baseline-Regelwerk `modul-07-carveouts.md` §Ziel-Form: Carveout — ein
Carveout braucht immer einen Auflösungs-Trigger **und** einen Folge-Slice.

---

## Begründung

Gemessen ([ADR-0008](../adr/0008-dotnet-kompatibilitaetstests.md), Spike): `signatureValidationMode=require` greift unter Linux; ohne `trustedSigners` scheitert der Restore mit `NU3034`; mit dem Repository-Zertifikat von nuget.org als einzigem Eintrag scheiterten weiter Pakete („signed but not by a trusted signer“). Eine funktionierende Liste ist nicht gebaut, ihr Aufwand nicht gemessen.

Entscheidung des Nutzers (2026-10-30): jetzt nur Lock-Datei (`packages.lock.json`, `RestoreLockedMode`) und `contentHash`; die Signaturprüfung folgt als Arbeit des Folge-Slice.

**Grenze dessen, was der Lock belegt.** Das erste Lock vertraut nuget.org beim Schreiben: `make referenztest-dotnet-lock` löst die Pakete über `https://api.nuget.org/v3/index.json` auf und hält ihre `contentHash`-Werte fest. Ein Paket, das schon beim ersten Schreiben manipuliert war, trägt seinen manipulierten Hash im Lock. Belegt ist nur die Richtung danach: ein verfälschter `contentHash` bricht den Restore mit `NU1403`, eine geänderte Version in der `.csproj` mit `NU1004` (am Projekt dieses Repos nachgemessen, je ein Lauf). Welchen Wert `signatureValidationMode` ohne Eintrag in der `nuget.config` annimmt, ist nicht gemessen.

## Auflösungs-Trigger

Der Carveout ist aufgelöst, wenn der Folge-Slice `slice-qa-dotnet-lieferkette-haerten` in `done/` liegt und eines davon zutrifft: (a) `referenztest-dotnet/nuget.config` führt `signatureValidationMode` mit `require` und eine `trustedSigners`-Liste, und `make referenztest-dotnet` läuft grün damit, oder (b) eine ADR mit Status `Accepted` hält den dauerhaften Verzicht auf die Signaturprüfung fest.

## Geltungs-Konfiguration

| Datei | Zeile/Section | Wert |
|---|---|---|
| `referenztest-dotnet/nuget.config` | `trustedSigners`, `config/signatureValidationMode` | fehlen (Kommentar in der Datei nennt `CO-002`) |
| `referenztest-dotnet/packages.lock.json` | `contentHash` je Paket | einzige Integritätsprüfung nach dem ersten Schreiben |

## Verifikation (nach Auflösung)

- [ ] Gate ist für den Geltungsbereich aktiviert (Gate-Konfiguration aktualisiert) oder der Verzicht ist per ADR entschieden.
- [ ] `make gates` grün ohne Ausnahme.
- [ ] Datei wird nach `docs/plan/carveouts/done/` bewegt (reiner `git mv`). <!-- d-check:ignore (done/ entsteht erst bei erster Carveout-Auflösung) -->
- [ ] Folge-Slice geschlossen oder explizit dokumentiert.

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-10-30 | Angelegt | [slice-qa-dotnet-referenztest-geruest](../planning/in-progress/slice-qa-dotnet-referenztest-geruest.md) |
