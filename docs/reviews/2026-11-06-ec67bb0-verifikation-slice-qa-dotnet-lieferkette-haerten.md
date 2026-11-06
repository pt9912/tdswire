# Verifikationsbericht: slice-qa-dotnet-lieferkette-haerten (HEAD ec67bb05)

* Rolle: Verifier (Modul 11), frischer Kontext. Gegenstand: [slice-qa-dotnet-lieferkette-haerten](../plan/planning/in-progress/slice-qa-dotnet-lieferkette-haerten.md) (Zweig `slice-qa-dotnet-lieferkette-haerten`, `git diff 1c9a85c9..HEAD`; Commits `d08d891a` Umsetzung, `f2ba2cc4` Review, `ec67bb05` Nachbesserung und ADR) gegen DoD §2, Plan §3, [ADR-0035](../plan/adr/0035-dotnet-restore-signaturliste-und-audit-verzicht.md), [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md), [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [CO-001](../plan/carveouts/CO-001-nuget-audit-aus.md), [CO-002](../plan/carveouts/CO-002-nuget-signaturpruefung-fehlt.md) und `AGENTS.md` §3. Eingang: Review-Report [2026-11-06-d08d891a-slice-qa-dotnet-lieferkette-haerten](2026-11-06-d08d891a-slice-qa-dotnet-lieferkette-haerten.md) (0 HIGH, 1 MEDIUM, 3 LOW, 2 INFO).
* Selbst gefahren (Arbeitsbaum sauber, HEAD `ec67bb05`): `make docs-check` (775 Dateien, 0 Befunde); `make referenztest-dotnet` (Exit 0, aber aus dem Cache); deshalb zusätzlich `docker build --no-cache-filter restore --target test referenztest-dotnet` (frischer `dotnet restore` mit `require` und Liste, Exit 0, Tests grün); Rotlauf an einer Kopie im Scratchpad (Arbeitsbaum unverändert): `nuget.config` ohne xUnit-Autoren und ohne Repository-Eintrag, Exit 1, `NU3034` „signed but not by a trusted signer“ für genau acht Pakete (`xunit`, `xunit.abstractions`, `xunit.analyzers`, `xunit.assert`, `xunit.core`, `xunit.extensibility.core`, `xunit.extensibility.execution`, `xunit.runner.visualstudio`); `make stempel-eingaenge-pruefung` (ok).
* Nicht selbst gefahren: `make gates` (der Implementer-Beleg dazu lag mir nicht vor; die DoD-Zeile ist offen, siehe unten), die übrigen Rotläufe des Messprotokolls (keine Liste; ohne `signatureValidationMode`; nur `1F4B311D…`), `dotnet nuget verify`-Zählung der Zertifikate (vom Review nachgemessen, hier nur gelesen), `make referenztest-dotnet-audit`.
* Nicht prüfbar von hier: Der Wortlaut der Nutzerentscheidung vom 2026-11-06 („deine empfehlungen“) steht nur in [ADR-0035](../plan/adr/0035-dotnet-restore-signaturliste-und-audit-verzicht.md) §Kontext; ein Beleg außerhalb dieser Quelle liegt mir nicht vor. Ich behandle ihn als vom Auftraggeber berichtet.

## Gesamturteil

**Die Inhalte der Liefer-Punkte 1 und 2 sind am Code und am Lauf bestätigt; der Slice ist noch nicht closure-fähig, weil die Closure-Pflichten selbst offen sind (V-3) und die DoD-Formulierung von Liefer-Punkt 1 der Planner entscheiden muss (V-1).** Kein HIGH, 1 MEDIUM (V-1), 2 LOW, 2 INFO. Kein Gate gelockert; die Gate-Lockerung nach `AGENTS.md` §3.6 ist per [ADR-0035](../plan/adr/0035-dotnet-restore-signaturliste-und-audit-verzicht.md) gefasst.

## DoD-Punkte (§2)

* **Liefer-Punkt 1, Signaturprüfung: bestätigt in der Sache, mit Auslegungsvorbehalt (V-1).**
  * `signatureValidationMode=require` und `trustedSigners` (3 Microsoft-, 2 xUnit-Autorenzertifikate, 3 Repository-Zertifikate, SHA-256, `allowUntrustedRoot=false`) stehen in `referenztest-dotnet/nuget.config` (Diff gelesen). `<clear />` und Quellen-Mapping unverändert.
  * Grüner Lauf: selbst gefahren, Stage `restore` frisch (kein Cache), dann `test` grün.
  * Roter Lauf: selbst gefahren, wie oben; er tritt **nur** ein, wenn Autoren- und Repository-Eintrag zugleich fehlen. Die Einträge wirken als ODER (Messprotokoll Zeilen „ohne xUnit-Autoren: Exit 0“ und „ohne Repository-Eintrag: Exit 0“; vom Review nachgemessen).
  * [CO-002](../plan/carveouts/CO-002-nuget-signaturpruefung-fehlt.md): aufgelöst wird er laut seinem Trigger (a) erst, wenn der Slice in `done/` liegt. Er liegt unverändert in `carveouts/` (Diff ohne Änderung an `docs/plan/carveouts/`): korrekt NICHT verschoben.
* **Liefer-Punkt 2, Schwachstellenprüfung: bestätigt.**
  * `NuGetAudit=false` bleibt in `referenztest-dotnet/TdsWire.Referenztests.csproj` Zeile 22 (nicht im Diff geändert). `make referenztest-dotnet-audit` bleibt das manuelle Werkzeug (Tabelle „Werkzeuge“ in `harness/README.md`, unverändert, „kein Gate“).
  * [ADR-0035](../plan/adr/0035-dotnet-restore-signaturliste-und-audit-verzicht.md): Status `Accepted`, Datum 2026-11-06, Nutzerentscheidung mit Wortlaut im Kontext; Entscheidung 2 nennt `make referenztest-dotnet-audit` als Ersatz, wie [CO-001](../plan/carveouts/CO-001-nuget-audit-aus.md) Trigger (b) es verlangt.
  * Gate-Lockerung §3.6: Entscheidung 3 hält fest, dass kein bestehendes Gate gesenkt wird und die Schwachstellenprüfung nicht Teil von `make gates` ist und es wegen [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) nicht werden soll. Das ist die ADR, die der Slice verlangt; Lockerung im Sinn von §3.6 findet im Diff nicht statt (keine Schwelle, keine Detekt-Regel, keine Stufe berührt).
  * [CO-001](../plan/carveouts/CO-001-nuget-audit-aus.md): unverändert in `carveouts/`, korrekt NICHT verschoben.
* **[ADR-0035](../plan/adr/0035-dotnet-restore-signaturliste-und-audit-verzicht.md) formal: bestätigt.** `Supersedes (teilweise)` nennt wörtlich den Satz „Mit dem Repository-Zertifikat … ist nicht gebaut.“ (stimmt mit [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md) Zeile 43 überein) und die Offenen Punkte (1) und (2) „soweit sie offen sagen“; [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md) selbst ist im Diff unverändert (`AGENTS.md` §3.5 gewahrt). Index `docs/plan/adr/README.md` hat die Zeile mit Status `Accepted`. Das Nummernloch 0033/0034 entsteht, weil diese ADRs auf `main` liegen (Basis des Zweigs davor); ein Zählkonflikt besteht nicht, solange kein anderer Zweig 0035 vergibt (beim Merge zu prüfen, V-4).
* **`make gates` grün: offen.** Kein Gate-Beleg des Implementers im Eingang; ich habe `make gates` nicht gefahren. Das Verschieben der Zeile `referenztest-dotnet` unter den Stempel bleibt damit unbelegt (Teilbelege: `docs-check`, `referenztest-dotnet`, `stempel-eingaenge-pruefung` grün).
* **Review durchgeführt:** Report liegt vor (nur Liefer-Punkt 1, HEAD `d08d891a`). Die Nachbesserung `ec67bb05` und die [ADR-0035](../plan/adr/0035-dotnet-restore-signaturliste-und-audit-verzicht.md) sind nicht erneut gereviewt (V-3).
* **Doku-Update `harness/README.md`:** Zeile „Nicht behauptet“ und Zeile `make referenztest-dotnet` geändert; Aussagen stimmen mit dem Messprotokoll (der Rotfall ist „einmal von Hand gemessen“). Der Satz „greift im Restore“ trägt: selbst gefahren.
* **Beobachtungs-Register:** `BEO-TDS/signaturliste-ohne-selbsttest/` (observation, state, evidence) angelegt; Stand „offen, 1×“, kein Zähler gesetzt, keine Verkörperung behauptet. Konsistent mit dem Nutzerentscheid, den Selbsttest nicht im Slice zu bauen.
* **Closure-Notiz (§7), Risiko-Ausgang (§6), done/-Verschiebung, Verschiebung beider Carveouts:** nicht erledigt; §7 steht auf „Wird bei der Closure gefüllt.“, das Risiko in §6 auf „offen bis zur Closure“. Das ist für den Stand vor der Closure erwartbar, aber die DoD ist damit nicht abgehakt (Haken stehen alle auf `[ ]`).

## Review-Findings F-1 bis F-6 am Text (Stichprobe aller sechs)

| Finding | Behoben? | Beleg |
|---|---|---|
| F-1 MEDIUM, Kommentar behauptet Bruch bei neuem Paket; ODER nicht benannt | ja | `nuget.config`: „Die Eintraege wirken als ODER …“, „fuer ein neues Paket mit anderem Zertifikat ist das nicht gemessen“; Messprotokoll: „Folgerung … ODER“, „doppelt prüfen“ entfällt; „Nicht gemessen“ nennt das neue Paket mit „wäre grün“ |
| F-2 LOW, drei statt acht Pakete | ja | Messzeile nennt acht Pakete; mein Rotlauf bestätigt die Zahl und die Namen |
| F-3 LOW, Erklärung ohne Messzeile | ja | eigene Zeile „nur das Repository-Zertifikat `1F4B311D…`“ (zwei Pakete) im Messprotokoll; Messung stammt aus dem Review, nicht von mir wiederholt |
| F-4 LOW, Zeiger auf den Slice | ja | Kommentar zeigt auf `docs/messungen/dotnet-signaturpruefung-2026-11-06.md` |
| F-5 INFO, [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md)-Widerspruch ohne Ablöser | ja | [ADR-0035](../plan/adr/0035-dotnet-restore-signaturliste-und-audit-verzicht.md) `Supersedes (teilweise)`, wörtlich |
| F-6 INFO, Rotation | ja | Messprotokoll „Rotation (Folgerung, nicht gemessen)“, [ADR-0035](../plan/adr/0035-dotnet-restore-signaturliste-und-audit-verzicht.md) Konsequenzen negativ und Annahme (a); abgelaufene Zertifikate `AA12DA22…` (bis 2023-10-05) und `B8F6A977…` (bis 2021-06-03) mit Datum, Ursache ausdrücklich nicht gemessen |

## Konformität der Aussagen (`AGENTS.md` §3.8) und Entscheidungen

* Die Aussagen in [ADR-0035](../plan/adr/0035-dotnet-restore-signaturliste-und-audit-verzicht.md), `nuget.config` und Messprotokoll tragen höchstens so weit wie die Zeilen; Folgerungen sind als „Folgerung, nicht gemessen“ markiert (Rotation, `-warnaserror`-Bruch, `owners`). Die Zeilen, die ich nachgefahren habe (Grünlauf, Rotlauf mit acht Paketen), stimmen.
* Zertifikatsrotation ist benannt ([ADR-0035](../plan/adr/0035-dotnet-restore-signaturliste-und-audit-verzicht.md) Konsequenzen und „Nicht gemessen“; Messprotokoll; `nuget.config` verweist auf das Protokoll). Offen bleibt, wer eine rote Rotation behebt; das Protokoll sagt nur „ein Eintrag muss ergänzt werden“.

## Findings

* **V-1 (MEDIUM) DoD-Formulierung von Liefer-Punkt 1 trägt der Rotfall nicht wörtlich.** Die DoD verlangt „einen roten [Lauf] bei einem Paket, dessen Signierer nicht in der Liste steht“. Gemessen ist ein roter Lauf nur, wenn **beide** Eintragsarten fehlen (Autor und nuget.org-Repository); ein Paket mit unbekanntem Autor, aber nuget.org-Gegensignatur bleibt grün, solange der Repository-Eintrag steht, und das ist der Regelfall. Der Rot-Beleg erfüllt die DoD also nur in der engen Lesart („Signierer“ = jeder Signierer, den die Liste führt), nicht in der naheliegenden („unbekannter Autor lässt den Restore brechen“). Der Slice und [ADR-0035](../plan/adr/0035-dotnet-restore-signaturliste-und-audit-verzicht.md) sagen das ehrlich (Alternativen-Tabelle, Konsequenzen, Kommentar); die Abweichung von der DoD-Absicht ist aber nirgends als solche entschieden. Der Planner/Nutzer hat zu entscheiden: (i) die enge Lesart gilt, die Closure-Notiz sagt es in einem Satz; oder (ii) ein `owners`-Eintrag am Repository bzw. Wegfall des Repository-Eintrags macht die Autoren bindend (neuer Messaufwand, nicht im Slice). Mit (i) ist Liefer-Punkt 1 erfüllbar; ohne Entscheidung bleibt die Zusage der DoD breiter als der Beleg.
* **V-2 (LOW) Der rote Fall hat keinen Sensor.** Gemessen von Hand einmal (Messprotokoll) und von mir einmal wiederholt; `make gates` sieht ihn nicht. Das trägt die Beobachtung `signaturliste-ohne-selbsttest` und `harness/README.md` („Nicht behauptet“); keine Behauptung im Repo geht darüber hinaus. Nur zur Kenntnis, solange der Nutzer den Selbsttest verwiesen hat.
* **V-3 (LOW) Review-Abdeckung.** Der Review betrifft Liefer-Punkt 1 auf `d08d891a`. [ADR-0035](../plan/adr/0035-dotnet-restore-signaturliste-und-audit-verzicht.md), der Index, das Register und die Nachbesserung `ec67bb05` sind neu seither und nicht gereviewt. Die DoD „Review durchgeführt“ ist damit nur für Teile belegt; ein kurzer Re-Review der Fix-Stellen (wie bei früheren Slices) schließt das.
* **V-4 (INFO) ADR-Nummer.** [ADR-0035](../plan/adr/0035-dotnet-restore-signaturliste-und-audit-verzicht.md) überspringt 0033 und 0034, die auf `main` liegen; vor dem Merge den ADR-Index gegen `main` abgleichen (Konfliktfläche: `docs/plan/adr/README.md`).
* **V-5 (INFO) Zustandsabhängigkeit.** Der Grünlauf braucht `api.nuget.org`; er hängt an Netz und am Zertifikatsstand des Tages ([ADR-0035](../plan/adr/0035-dotnet-restore-signaturliste-und-audit-verzicht.md) Annahme (a), „Nicht gemessen: CI“). Der Cache hält `make referenztest-dotnet` grün, ohne die Signaturprüfung neu zu fahren; wer sie belegen will, ruft `docker build --no-cache-filter restore --target test referenztest-dotnet`. Dies gehört in die Closure-Notiz als Belegform.

## Bedingungen für die Closure

* B-1: Planner/Nutzer entscheidet V-1 (Lesart der DoD) und hält sie in der Closure-Notiz fest.
* B-2: `make gates` auf dem Zweig mit Ausgabe in der Closure-Notiz belegen; die Cache-Falle (V-5) benennen.
* B-3: Re-Review der Nachbesserung `ec67bb05` und der [ADR-0035](../plan/adr/0035-dotnet-restore-signaturliste-und-audit-verzicht.md) (V-3).
* B-4: Closure-Notiz mit Steering-Loop-Lerneintrag; Ausgang für das Risiko in §6 ([ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md)-Befund: Aufwand der Liste gemessen, ein Lauf je Zeile, Ausgang „eingetreten/entfallen“ vom Implementer); Beobachtungs-Register in §7 zitieren.
* B-5: Erst nach dem Slice-Move nach `done/` [CO-001](../plan/carveouts/CO-001-nuget-audit-aus.md) und [CO-002](../plan/carveouts/CO-002-nuget-signaturpruefung-fehlt.md) per `git mv` nach `carveouts/done/` (Trigger beider Carveouts), danach die Verweise in `harness/README.md` (Bindung-Spalte) nachziehen.

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `referenztest-dotnet/nuget.config` | geprüft, gefahren, kein Befund über V-1 hinaus |
| `referenztest-dotnet/TdsWire.Referenztests.csproj` | geprüft (`NuGetAudit=false`, unverändert), ohne Befund |
| `tools/mk/referenztest-dotnet.mk` | Kommentar geprüft, ohne Befund |
| `docs/plan/adr/` (0035, Index, 0008 unverändert) | geprüft, ohne Befund außer V-4 |
| `docs/plan/carveouts/` | unverändert, gewollt |
| Gate-Lockerung | keine im Diff gefunden |
| `docs-check`, `stempel-eingaenge-pruefung` | grün |
