# ADR-0035: Signaturliste des .NET-Restore und dauerhafter Verzicht auf NuGet-Audit

**Status:** Accepted (Entscheidung des Nutzers 2026-11-06)

**Datum:** 2026-11-06

**Autor:** pt9912

**Bezug:** [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [ADR-0008](0008-dotnet-kompatibilitaetstests.md), [CO-001](../carveouts/CO-001-nuget-audit-aus.md), [CO-002](../carveouts/CO-002-nuget-signaturpruefung-fehlt.md), [Messprotokoll](../../messungen/dotnet-signaturpruefung-2026-11-06.md)

**Schärft:** [SPEC-025](../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge) (Pakete per Lock und Hash; hier zusätzlich Signaturprüfung).

**Supersedes (teilweise), nur wörtlich benannt; die Datei von [ADR-0008](0008-dotnet-kompatibilitaetstests.md) bleibt unverändert und `Accepted` ([AGENTS.md](../../../AGENTS.md) §3.5):**

- ADR-0008, Messung zur Signaturprüfung: **nur** der Satz „Mit dem Repository-Zertifikat von nuget.org als einzigem Eintrag scheiterten weiter Pakete mit „signed but not by a trusted signer“; eine funktionierende Liste ist nicht gebaut.“ (Stand: die Liste ist gebaut, siehe Entscheidung 1; die Messung zum Repository-Zertifikat allein bleibt wahr, siehe Kontext).
- ADR-0008, Offene Punkte: **nur** (1) „NuGet Audit“ und (2) „`trustedSigners`“, soweit sie „offen, nicht entschieden“ sagen. Beide sind mit dieser ADR entschieden.

Weitere Stellen von ADR-0008, die diese ADR überholt, ohne dass die Datei sie ändert (Zeiger von hier, §3.5): in den Messgrenzen die Nennung „eine funktionierende `trustedSigners`-Liste“ als nicht gemessen; in der Tabelle der Optionen die Zelle zu Option B („NuGet-Audit und `trustedSigners` offen“); in der Aufzählung „Offen:“ die Nennung von NuGet-Audit und `trustedSigners`; in der Geschichte die Zeile 2026-10-30 („NuGet-Audit und `trustedSigners` offen“). Der Index trägt bei ADR-0008 den Hinweis auf diese ADR.

---

## Kontext

**Entscheidung des Nutzers vom 2026-11-06.** Auf die Empfehlungen des Koordinators zur Härtung der Lieferkette des .NET-Restore antwortete der Nutzer „deine empfehlungen“: (1) Der Versuch mit `trustedSigners` gilt als gelungen, die Zeitbox ist erledigt. (2) Der Verzicht auf `NuGetAudit` bleibt dauerhaft, mit ADR; `make referenztest-dotnet-audit` bleibt manuelles Werkzeug. (3) Die Liste führt Autorenzertifikate wie gebaut, `owners` am Repository-Eintrag erst bei Bedarf. (4) Ein Selbsttest mit Mutation gehört nicht in diesen Slice, sondern als Beobachtung ins Register. (5) Ablauf und Rotation von Zertifikaten stehen in der Closure-Notiz und im Messprotokoll.

**Gemessen** ([Messprotokoll](../../messungen/dotnet-signaturpruefung-2026-11-06.md), ein Host, Einzelmessung je Zeile): Die 36 Pakete des Locks tragen fünf Autoren- und drei Repository-Zertifikate. Mit `signatureValidationMode=require` und allen acht Zertifikaten läuft der Restore grün. Ohne Liste scheitert er mit `NU3034`. Ohne xUnit-Autoren und ohne Repository-Eintrag scheitern acht Pakete mit „signed but not by a trusted signer“. Mit nur dem Repository-Zertifikat `1F4B311D…` scheitern genau zwei Pakete, deren Repository-Zertifikat ein anderes ist; das erklärt den Befund von ADR-0008. Gemessen ist: ohne xUnit-Autoren bleibt der Restore grün, ohne Repository-Eintrag ebenfalls, rot wird er erst, wenn beide fehlen. Daraus folgt (Folgerung, nicht eigens gemessen), dass die Einträge als ODER wirken: Der Repository-Eintrag ohne `owners` lässt jedes von nuget.org gegengezeichnete Paket zu, die Autorenzertifikate binden erst, wenn er fehlt.

**NuGet-Audit** ([CO-001](../carveouts/CO-001-nuget-audit-aus.md)): Das Ergebnis hängt am Abfragetag; gemessen meldete es am 2026-10-30 für `System.Security.Cryptography.Pkcs` 9.0.18 zwei Advisories. Mit `-warnaserror` bräche der Build an einem Tag, an dem sich keine Datei im Repo geändert hat (Folgerung, nicht gemessen).

**Annahmen, die kippen:** (a) dass die Zertifikate der Pakete im Lock zwischen zwei `make referenztest-dotnet-lock` stabil bleiben; (b) dass ein manuell gerufenes Werkzeug ausreicht, um bekannte Schwachstellen zu sehen.

## Entscheidung

1. **Signaturprüfung gebaut, enge Lesart (V-1).** Der Nutzer entschied am 2026-11-06 („i, deine empfehlung“): Autoren- und Repository-Eintrag wirken als ODER, der rote Lauf entsteht erst, wenn beide fehlen. Die Slice-Vorgabe „roter Lauf bei einem Paket, dessen Signierer nicht in der Liste steht“ gilt in dieser Lesart als erfüllt durch die Messzeile „ohne xUnit-Autoren und ohne Repository-Eintrag“. `referenztest-dotnet/nuget.config` führt `signatureValidationMode=require` und `trustedSigners` mit den Zertifikaten (SHA-256) aus dem Messprotokoll: fünf Autoren- und drei Repository-Zertifikate. Ein neues Paket mit nuget.org-Gegensignatur braucht keinen Eintrag und lässt den Restore grün (Folgerung, nicht gemessen); die Autorenzertifikate begrenzen erst, wenn der Repository-Eintrag entfällt. Ein Paket ohne Gegensignatur und mit unbekanntem Autor bricht den Restore. Das Lock hält Version und `contentHash` weiter fest. Ein `owners`-Eintrag am Repository-Eintrag kommt bei Bedarf, nicht jetzt.
2. **Dauerhafter Verzicht auf `NuGetAudit`.** `NuGetAudit` bleibt in `referenztest-dotnet/TdsWire.Referenztests.csproj` auf `false`. Ersatz ist `make referenztest-dotnet-audit`: manuell, kein Gate, am Abfragetag abhängig. Das ist der Auflösungs-Trigger (b) von CO-001.
3. **Kein Gate gesenkt (`AGENTS.md` §3.6).** Die Schwachstellenprüfung der .NET-Pakete war nie Teil von `make gates`; es sinkt weder eine Schwelle noch eine Strenge. Die ADR liegt vor, weil `NuGetAudit=false` den Audit beim Restore abschaltet und der dauerhafte Verzicht eine Entscheidung ist, kein PR-Kommentar. Setzung (nicht gemessen): Die Schwachstellenprüfung bleibt wegen [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) außerhalb von `make gates`.

## Verglichene Alternativen

| Option | Pro | Contra |
|---|---|---|
| `NuGetAudit` an, mit Ausnahmeliste je Advisory | Schwachstellen bleiben im Build sichtbar | Ergebnis hängt am Abfragetag; ein roter `make gates` ohne Änderung im Repo; die Liste müsste täglich gepflegt werden; verworfen |
| `NuGetAudit` an als Warnung ohne `-warnaserror` | Sichtbar, nicht rot | Warnungen im Build werden nicht gelesen; die Datenbank der Quelle bleibt eine Netzabhängigkeit des Restore; verworfen zugunsten des manuellen Werkzeugs |
| Signaturliste nur mit Repository-Zertifikaten | Eine Liste, kleiner; trägt nach dem ODER dieselbe Wirkung wie die gebaute Konfiguration | Konfiguration mit drei Repository-Zertifikaten allein nicht gemessen (gemessen: nur `1F4B311D…`, und Autoren allein); Gewählt wurde die Autoren- und Repository-Liste als Setzung nach der Vorgabe des Slice, nicht aus einer Messung |
| **Autoren- und Repository-Zertifikate, `require` (gebaut)** | Slice-Vorgabe; Restore wird rot, wenn für ein Paket weder ein Autoren- noch ein Repository-Eintrag passt (gemessen an den acht `xunit`-Paketen) | Wirkt als ODER, die Autoren binden solange nichts, wie der Repository-Eintrag steht; Zertifikatswechsel kann den Restore ohne Änderung im Repo rot machen |
| Verzicht auf die Signaturprüfung dauerhaft | Kein Pflegeaufwand | Der Versuch gelang; das Lock vertraut dem ersten Abruf, die Prüfung schließt diese Lücke teilweise; verworfen |

## Konsequenzen

- Positiv: CO-002 ist aufgelöst, sobald der Slice in `done/` liegt (Trigger (a)); CO-001 ebenso (Trigger (b)). Das Verschieben beider nach `carveouts/done/` ist Sache der Closure.
- Negativ: Wechselt ein Zertifikat, kann `make referenztest-dotnet` ohne Änderung im Repo rot werden (nicht gemessen, Folgerung); die Autoren binden solange nichts, wie der Repository-Eintrag steht. Bekannte Schwachstellen der .NET-Pakete sieht nur, wer `make referenztest-dotnet-audit` ruft.
- Ein Selbsttest, der den roten Fall mit einer Mutation der Liste hält, fehlt; der rote Fall ist einmal von Hand gemessen. Träger ist die Beobachtung `BEO-TDS/signaturliste-ohne-selbsttest`.

## Fitness Function (falls maschinell prüfbar)

| Tooling | Regel | Make-Target |
|---|---|---|
| NuGet-Restore in Docker | Restore mit `require` und der Liste grün | `make referenztest-dotnet` |
| manuell | bekannte Schwachstellen der .NET-Pakete am Tag des Laufs | `make referenztest-dotnet-audit` (kein Gate, [CO-001](../carveouts/CO-001-nuget-audit-aus.md)) |

## Nicht gemessen

- Verhalten bei Wechsel eines Zertifikats; Ursache, warum abgelaufene Autorenzertifikate den Restore bestehen.
- `owners` am Repository-Eintrag.
- Lauf der Signaturprüfung auf der CI oder einem anderen Host.
