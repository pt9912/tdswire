# Signaturprüfung des .NET-Restore: Messprotokoll vom 2026-11-06

Messprotokoll zu [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) und [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md) (`AGENTS.md` §5 Regel 7). Es hält fest, was gemessen wurde, nicht, ob eine Anforderung belegt ist.

## Bedingungen

| Angabe | Wert |
|---|---|
| Datum | 2026-11-06 |
| Host | Linux 6.8, Docker, Netz zu `api.nuget.org` |
| Image | `mcr.microsoft.com/dotnet/sdk:10.0.403@sha256:de4fe90a98845d75910b9769662465c84de1a934665c431279d1382b2bddc02b` |
| Eingang | `referenztest-dotnet/packages.lock.json` (36 Pakete), `referenztest-dotnet/nuget.config` |
| Umfang | je Zeile ein Lauf, ein Host, ein Tag (Einzelmessung) |

## Signierer der 36 Pakete

Gelesen mit `dotnet nuget verify` und dem Schalter `all` je `.nupkg` des Restore. Jedes der 36 Pakete trägt eine Autorensignatur und eine Repository-Gegensignatur.

| Art | Antragsteller | SHA-256 des Zertifikats (Anfang) | Pakete |
|---|---|---|---|
| Autor | Microsoft Corporation | `566A3188…` | 20 |
| Autor | Microsoft Corporation | `9A1B131B…` | 7 |
| Autor | Microsoft Corporation | `AA12DA22…` | 1 |
| Autor | xUnit.net (.NET Foundation) | `B8F6A977…` | 1 |
| Autor | xUnit.net (.NET Foundation) | `0D766240…` | 7 |
| Repository | NuGet.org Repository by Microsoft | `1F4B311D…` | 34 |
| Repository | NuGet.org Repository by Microsoft | `0E5F38F5…` | 1 |
| Repository | NuGet.org Repository by Microsoft | `5A2901D6…` | 1 |

Das Repository-Zertifikat `1F4B311D…` trägt 34 von 36 Paketen. Die Pakete mit den beiden anderen Repository-Zertifikaten sind `Microsoft.SqlServer.Server 1.0.0` (`5A2901D6…`) und `xunit.abstractions 2.0.3` (`0E5F38F5…`). Zwei Autorenzertifikate sind abgelaufen: `AA12DA22…` (gültig bis 2023-10-05) und `B8F6A977…` (bis 2021-06-03); die Pakete mit ihnen bestehen den grünen Restore. Die Ursache (etwa der Zeitstempel der Signatur) ist nicht gemessen. Beide Daten und Zuordnungen stammen aus der Nachmessung im Review `docs/reviews/2026-11-06-d08d891a-slice-qa-dotnet-lieferkette-haerten.md`.

## Läufe von `dotnet restore` (frischer Container, Netz)

| Konfiguration | Ergebnis |
|---|---|
| `require`, alle fünf Autoren- und alle drei Repository-Zertifikate (die Datei im Repo) | Exit 0; Stage `test` mit `docker build --no-cache-filter restore` grün |
| `require`, keine Liste | Exit 1, `NU3034` „no trusted signers were specified“ |
| `require`, ohne xUnit-Autoren | Exit 0 |
| `require`, ohne Repository-Eintrag | Exit 0 |
| `require`, ohne xUnit-Autoren **und** ohne Repository-Eintrag | Exit 1, `NU3034` „signed but not by a trusted signer“ für acht Pakete: `xunit`, `xunit.abstractions`, `xunit.analyzers`, `xunit.assert`, `xunit.core`, `xunit.extensibility.core`, `xunit.extensibility.execution`, `xunit.runner.visualstudio` |
| `require`, nur das Repository-Zertifikat `1F4B311D…`, keine Autoren (Konfiguration von [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md)) | Exit 1, genau zwei Pakete: `Microsoft.SqlServer.Server 1.0.0` und `xunit.abstractions 2.0.3` (Nachmessung im Review) |
| ohne `signatureValidationMode`, mit Liste | Exit 0 (die Liste allein erzwingt nichts; der Wert ohne Eintrag ist damit nicht als `require` belegt) |

**Folgerung (aus den Zeilen):** Die Einträge wirken als ODER. Ein Repository-Eintrag ohne `owners` lässt jedes Paket zu, das nuget.org gegengezeichnet hat; solange er steht, binden die Autorenzertifikate nichts, und umgekehrt tragen die Autorenzertifikate allein alle 36 Pakete. Die Aussage von [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md), das Repository-Zertifikat allein habe Pakete scheitern lassen, erklärt die Zeile mit nur `1F4B311D…`: zwei Pakete tragen ein anderes Repository-Zertifikat. Ein `owners`-Eintrag am Repository, der Pakete an Veröffentlicher bindet, ist nicht gemessen.

**Rotation (Folgerung, nicht gemessen).** Wechselt nuget.org sein Repository-Zertifikat oder ein Autor das seine, kann der Restore ohne Änderung im Repo rot werden (`make referenztest-dotnet`, `make referenztest-dotnet-lock`, ein Frischlauf von `make gates`). Ein Eintrag der Liste muss dann ergänzt werden. Dass abgelaufene Zertifikate (oben) den Restore nicht rot machen, zeigt nur, dass Ablauf allein nicht genügt, nicht, wie sich ein Wechsel verhält.

## Nicht gemessen

- Ursache, warum abgelaufene Autorenzertifikate den Restore bestehen, und das Verhalten bei einem Wechsel eines Zertifikats.
- Verhalten bei einem neuen Paket oder einer neuen Version mit anderem Zertifikat (solange der Repository-Eintrag steht, wäre der Restore nach den Zeilen grün; nicht gemessen).
- `owners` am Repository-Eintrag.
- Lauf auf CI oder einem anderen Host.
