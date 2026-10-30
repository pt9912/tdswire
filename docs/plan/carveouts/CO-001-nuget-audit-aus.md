# CO-001: NuGet-Audit des .NET-Referenztests ist ausgeschaltet

**Status:** Aktiv.

**Datum angelegt:** 2026-10-30. **Letzte Prüfung:** 2026-10-30.

**Betroffenes Gate:** `make referenztest-dotnet`

**Geltungsbereich:** `referenztest-dotnet/TdsWire.Referenztests.csproj` (Eigenschaft `NuGetAudit`)

**Folge-Slice:** [`slice-qa-dotnet-lieferkette-haerten.md`](../planning/open/slice-qa-dotnet-lieferkette-haerten.md)

Regeln: Baseline-Regelwerk `modul-07-carveouts.md` §Ziel-Form: Carveout — ein
Carveout braucht immer einen Auflösungs-Trigger **und** einen Folge-Slice.

---

## Begründung

NuGet-Audit fragt beim Restore online nach Schwachstellen. Gemessen ([ADR-0008](../adr/0008-dotnet-kompatibilitaetstests.md), Spike): `Microsoft.Data.SqlClient` 7.1.1 zieht `System.Security.Cryptography.Pkcs` 9.0.18 nach, für das zum Messzeitpunkt `NU1902` und `NU1903` gemeldet wurden; mit `-warnaserror` bricht der Build. Ein Ergebnis, das vom Tag der Abfrage abhängt, ist nicht reproduzierbar ([TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)); ein grünes `make gates` von heute wäre morgen rot, ohne dass sich eine Datei im Repo geändert hat (Folgerung, nicht gemessen).

Entscheidung des Nutzers (2026-10-30): `NuGetAudit=false` befristet, dazu ein eigenes, ausdrücklich nicht reproduzierbares Werkzeug ohne Gate-Wirkung, `make referenztest-dotnet-audit`. Es meldet bekannte Schwachstellen am Tag des Laufs; gemessen am 2026-10-30 meldete es `System.Security.Cryptography.Pkcs` 9.0.18, Schweregrad High, als transitive Abhängigkeit.

Das Werkzeug ersetzt die Prüfung nicht: niemand ruft es auf, wenn es nicht Teil eines Ablaufs ist. Ob ein dauerhafter Verzicht auf den Audit im Gate eine ADR braucht (`AGENTS.md` §3.6, Gate-Lockerung), ist offen und vom Folge-Slice zu entscheiden.

## Auflösungs-Trigger

Der Carveout ist aufgelöst, wenn der Folge-Slice `slice-qa-dotnet-lieferkette-haerten` in `done/` liegt und eines davon zutrifft: (a) `NuGetAudit` steht in `referenztest-dotnet/TdsWire.Referenztests.csproj` nicht mehr auf `false`, oder (b) eine ADR mit Status `Accepted` hält den dauerhaften Verzicht fest und nennt das Werkzeug `make referenztest-dotnet-audit` als Ersatz.

## Geltungs-Konfiguration

| Datei | Zeile/Section | Wert |
|---|---|---|
| `referenztest-dotnet/TdsWire.Referenztests.csproj` | `<NuGetAudit>` | `false`, Kommentar nennt `CO-001` |
| `tools/mk/referenztest-dotnet.mk` | Ziel `referenztest-dotnet-audit` | Werkzeug ohne Gate-Wirkung |

## Verifikation (nach Auflösung)

- [ ] Gate ist für den Geltungsbereich aktiviert (Gate-Konfiguration aktualisiert) oder der Verzicht ist per ADR entschieden.
- [ ] `make gates` grün ohne Ausnahme.
- [ ] Datei wird nach `docs/plan/carveouts/done/` bewegt (reiner `git mv`). <!-- d-check:ignore (done/ entsteht erst bei erster Carveout-Auflösung) -->
- [ ] Folge-Slice geschlossen oder explizit dokumentiert.

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-10-30 | Angelegt | [slice-qa-dotnet-referenztest-geruest](../planning/in-progress/slice-qa-dotnet-referenztest-geruest.md) |
