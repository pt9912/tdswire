# Review slice-qa-dotnet-referenztest-geruest (9fcd9f4)

* Gegenstand: Diff `dc208ef..9fcd9f4` (`referenztest-dotnet/`, `tools/mk/referenztest-dotnet.mk`, `harness/README.md`, Carveouts, Folge-Slice)
* Plan: [slice-qa-dotnet-referenztest-geruest](../plan/planning/done/slice-qa-dotnet-referenztest-geruest.md), Welle [welle-qualitaet-grundlage](../plan/planning/welle-qualitaet-grundlage.md)
* Grundlage: [ADR-0008](../plan/adr/README.md), [ADR-0001](../plan/adr/README.md), [TDS-QA-01](../../spec/lastenheft.md), [TDS-QA-06](../../spec/lastenheft.md), [CO-001](../plan/carveouts/CO-001-nuget-audit-aus.md), [CO-002](../plan/carveouts/CO-002-nuget-signaturpruefung-fehlt.md), `AGENTS.md` §3.1–3.8, Nutzerentscheidungen F1–F4
* Sensor: `make referenztest-dotnet` (gecacht 1,0 s; Test-Stage mit `--no-cache-filter test` neu ausgeführt: 1/1 bestanden, 11,9 s), `make referenztest-dotnet-audit` (5,8 s, meldet `System.Security.Cryptography.Pkcs` 9.0.18, High und Moderate, Exit 0), `make gates` grün (Exit 0, 8,8 s, warm). Digest-Auflösung per `docker buildx imagetools inspect`. Nicht ausgeführt: `make referenztest-dotnet-lock` (schreibt eine Datei im Arbeitsbaum), die Selbstproben mit verfälschtem `contentHash` und geänderter Version, ein Kaltlauf.

Ergebnis: kein HIGH, ein MEDIUM, drei LOW, drei INFO. Die Carveouts erfüllen die Carveout-Form; die Pins sind belegt.

## Findings

### F1 · MEDIUM · Smoke-Test-KDoc sagt "ohne Netz" für den ganzen Ablauf, der Restore läuft mit Netz
* quelle: Hard Rule 3.8
* pfad: `referenztest-dotnet/Smoke/KeinServerSmokeTests.cs` · "Restore, Build und Test laufen in Docker ohne Mount und ohne Netz"
* befund: Ohne Netz läuft nur die Stage `test` (`RUN --network=none dotnet test --no-restore`); die Stage `restore` holt die Pakete von `api.nuget.org` (Dockerfile, [ADR-0008](../plan/adr/README.md) §Netzbedarf). Failure-Szenario: ein Wartender liest die KDoc als "Restore offline", sperrt auf einem Runner die Registry und sieht den Restore mit `NU1301` brechen. Die Zeile in `harness/README.md` ("gesperrter Restore, dann Build und Smoke-Test ohne Netz") und das Dockerfile sind korrekt; nur die KDoc ist breiter.
* verifizierbar: nein
* klasse: Aussage breiter als Beleg (Kommentar)

### F2 · LOW · `nuget.config` schließt aus fehlendem `signatureValidationMode` auf "allein Lock und contentHash"
* quelle: Hard Rule 3.8, [CO-002](../plan/carveouts/CO-002-nuget-signaturpruefung-fehlt.md)
* pfad: `referenztest-dotnet/nuget.config` · "die Integritaet nach dem ersten Lock tragen allein packages.lock.json und contentHash"
* befund: [CO-002](../plan/carveouts/CO-002-nuget-signaturpruefung-fehlt.md) hält selbst fest, dass der Wert von `signatureValidationMode` ohne Eintrag nicht gemessen ist; ob NuGet beim Restore dennoch Signaturen auswertet, ist damit offen. Die Config-Aussage "allein" trägt weiter als diese Messzeile.
* verifizierbar: nein
* klasse: Aussage breiter als Beleg (Konfigurationskommentar)

### F3 · LOW · `README.md` der Carveouts nennt als Trigger eine Kurzform, die vom Carveout abweicht
* quelle: Maintainability, [CO-001](../plan/carveouts/CO-001-nuget-audit-aus.md), [CO-002](../plan/carveouts/CO-002-nuget-signaturpruefung-fehlt.md)
* pfad: `docs/plan/carveouts/README.md` · "`NuGetAudit` nicht mehr `false` oder ADR zum Verzicht"
* befund: Im Carveout gilt der Trigger erst, wenn der Folge-Slice in `done/` liegt und eine der Bedingungen zutrifft; der Index lässt die Bedingung "Folge-Slice in `done/`" weg. Index ist derivativ; ein Leser des Index löst einen Carveout früher auf, als der Carveout erlaubt.
* verifizierbar: nein
* klasse: Index weicht von Quelle ab

### F4 · LOW · Smoke-Test: Fehlerklasse "Verbindung abgelehnt" als einziger Ausgang angenommen
* quelle: [TDS-QA-06](../../spec/lastenheft.md), Hard Rule 3.8
* pfad: `Smoke/KeinServerSmokeTests.cs` · "Assert.Equal(20, ausnahme.Class)"
* befund: Gemessen ist ein Lauf der Stage (702 ms, Number 0, Class 20); der Kommentar belegt für die fünf weiteren Versuche nur den Ausnahmetyp. Bei stark belastetem Runner kann der Verbindungsversuch statt der Ablehnung in den `Connect Timeout=1` laufen (andere `Number`/`Class`, Dauer zudem inklusive JIT und Erstaufruf von SqlClient); der Test wäre dann rot ohne Fehler im Gerüst. Port-Race (Listener stoppt, Port wird frei) ist im Build-Container mit einem Testprozess praktisch ausgeschlossen. Der Wert 5 s ist als Setzung gekennzeichnet; das ist korrekt.
* verifizierbar: nein (nur über Lasttest)
* klasse: Zeit-/Lastabhängige Assertion auf Einzelmessung

### F5 · INFO · Grüner Test-Stage-Lauf ist gecacht
* quelle: Maintainability, Slice §6
* pfad: `tools/mk/referenztest-dotnet.mk` · "docker build --target test --progress=plain"
* befund: Beobachtet: der zweite Lauf zeigt `CACHED` für den Testschritt; der Smoke-Test läuft nur bei geänderten Eingängen. "Ein roter Test macht `make gates` rot" in `harness/README.md` gilt für Läufe mit geändertem Cache-Schlüssel. Das Risiko ist im Slice §6 mit Ausgangsvorschlag benannt; der Ausgang gehört in die Closure-Notiz.
* verifizierbar: ja (zweiter Lauf)
* klasse: Cache verdeckt Testausführung

### F6 · INFO · Laufzeit, Platz, Netz in `make gates` und CI
* quelle: Maintainability, [TDS-QA-06](../../spec/lastenheft.md), Nutzerentscheidung F3
* pfad: `.github/workflows/gates.yml` · "run: make gates"
* befund: Gemessen ist warm: `make gates` 8,8 s, Stage `test` neu 11,9 s. Nicht gemessen sind Kaltlauf, Pull des SDK-Images (laut [ADR-0008](../plan/adr/README.md) 1,31 GB) und Plattenbedarf auf einem Runner. Der CI-Lauf braucht Netz zu `mcr.microsoft.com`, `docker.io` (Frontend) und `api.nuget.org`; GitHub-hosted Runner haben es, ein Lauf ist laut `harness/README.md` nicht belegt. Der Stop-Hook-Zyklus sieht bei warmem Cache keinen spürbaren Zuschlag, bei kaltem Cache einen Pull plus Restore. Die Werte sind laut Slice Teil der Closure-Notiz (F3).
* verifizierbar: ja (Kaltlauf)
* klasse: Gate-Kosten kalt nicht gemessen

### F7 · INFO · Carveout-Form erfüllt; Trigger beobachtbar, kein Wächter
* quelle: Hard Rule 3.6, `modul-07-carveouts.md`
* pfad: `docs/plan/carveouts/CO-001-nuget-audit-aus.md` · "Der Carveout ist aufgelöst, wenn der Folge-Slice"
* befund: Beide Carveouts tragen Begründung mit Messzeile, Folgerungen als solche gekennzeichnet, Trigger (Dateistand und Lifecycle-Ort, beobachtbar), Geltungs-Konfiguration und existierenden Folge-Slice in `open/` (Verantwortlicher "—", bis zur Priorisierung). Die Schwelle der Gates bleibt, Hard Rule 3.6 ist eingehalten. Kein Sensor prüft Trigger oder Frist; das deklariert `harness/conventions.md` für Modul 7 ("liegt bei, nicht verdrahtet"). Die `Letzte Prüfung` ist der Anlegetag.
* verifizierbar: nein
* klasse: Trigger ohne Wächter (deklariert)

## Geprüft, ohne Befund
* `referenztest-dotnet/Dockerfile`: Frontend-Digest `sha256:4edf897a…` löst gegen `docker/dockerfile:1` auf; SDK-Digest `sha256:de4fe90a…` ist der Index-Digest des Tags `10.0.403` (beide per `imagetools inspect` bestätigt, wie der Kommentar sagt); kein Mount; `RUN --network=none` im Testschritt; Restore-Stage mit drei Dateien; Lock per `--output` aus `scratch`-Stage; Kommentare tragen Zusage/Kopplung/Grenze.
* `referenztest-dotnet/TdsWire.Referenztests.csproj`: `RestoreLockedMode` außer bei `LockErzeugen=true`; kein `NoWarn`, kein `SuppressMessage`-Äquivalent; `NuGetAudit=false` trägt Verweis auf [CO-001](../plan/carveouts/CO-001-nuget-audit-aus.md); `TreatWarningsAsErrors` an.
* `referenztest-dotnet/nuget.config`, `packages.lock.json`, `.dockerignore`: `<clear />`, eine Quelle, `packageSourceMapping`; Lock führt `Pkcs` 9.0.18 wie in [CO-001](../plan/carveouts/CO-001-nuget-audit-aus.md) genannt.
* `tools/mk/referenztest-dotnet.mk` und `harness/README.md`: `GATE_CHECKS` nur für das Test-Target; Audit- und Lock-Target stehen als "kein Gate" mit Bindung an [CO-001](../plan/carveouts/CO-001-nuget-audit-aus.md)/[CO-002](../plan/carveouts/CO-002-nuget-signaturpruefung-fehlt.md); die Gate-Zeile bindet beide Carveouts zutreffend; Docker-only.
* `docs/plan/planning/open/slice-qa-dotnet-lieferkette-haerten.md`: nimmt beide Carveouts an, Liefer-Punkte sind prüfbar.
