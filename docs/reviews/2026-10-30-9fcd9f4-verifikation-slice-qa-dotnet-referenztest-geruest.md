# Verifikationsbericht: slice-qa-dotnet-referenztest-geruest am Stand 9fcd9f4 — 2026-10-30

**Rolle:** Verifier (Frage: „Bauen wir es richtig?“ gegen Plan, DoD und Entscheidungen). Der Bericht repariert nichts und ändert keine Datei außer sich selbst.

**Gegenstand:** [slice-qa-dotnet-referenztest-geruest](../plan/planning/in-progress/slice-qa-dotnet-referenztest-geruest.md) (Welle welle-qualitaet-grundlage), Merge `9fcd9f4`. Bezug: [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0001](../plan/adr/0001-tds-version-und-clientprofil.md).

**Stand-Hinweis:** `main` steht inzwischen auf `67a3810`. `git diff 9fcd9f4 HEAD` ist für `referenztest-dotnet/`, `tools/mk/`, `harness/`, `docs/plan/carveouts/`, beide Slice-Dateien, `Makefile` und `repo.mk` leer; alle Läufe unten sind deshalb für `9fcd9f4` gültig. Mutationen und `make gates` liefen in einem frischen Klon im Scratchpad (`.../scratchpad/w`); der Arbeitsbaum des Repos blieb unverändert (`git status`: nur das bereits vorhandene `?? .claude/worktrees/`).

**Behauptung ≠ Bestätigung:** Der Slice trägt keine Closure-Notiz, keine Häkchen und keine Messwerte (§7: „Wird bei der Closure gefüllt“). Die Messwerte und Sensor-Behauptungen kamen nur im Auftrag; jede wurde selbst gefahren.

## 1. Selbst gefahrene Läufe

| Lauf | Ergebnis |
|---|---|
| `make referenztest-dotnet` im Repo | Exit 0, alle Stages `CACHED`, **1,03 s** |
| `docker build --no-cache --target test referenztest-dotnet` (Klon, Basis-Image lokal vorhanden) | Exit 0, **17,6 s**: Restore 5,4 s (4,4 s Paketabruf), Test-Stage 3,6 s (1 Test bestanden, 794 ms), Export 7,2 s |
| Image-Größe (`docker image inspect`, Stage `test`) | **1,61 GB** |
| `make referenztest-dotnet-lock` im Klon | Exit 0, `git status` danach leer: das Lock wird byte-gleich neu erzeugt (Stand 2026-10-30) |
| `make referenztest-dotnet-audit` im Klon | Exit 0; meldet `System.Security.Cryptography.Pkcs` 9.0.18 transitiv, **High** [GHSA-846f-f764-76mx](https://github.com/advisories/GHSA-846f-f764-76mx) und zusätzlich **Moderate** `GHSA-2fmv-rv2c-59x7`; Quelle nur nuget.org; Stage lief (`--no-cache-filter audit`), 5,7 s |
| `make gates` im Klon | Exit 0, 7,1 s; `GATE_CHECKS := baseline-verify docs-check detekt build detekt-selbsttest referenztest-dotnet test`, `record-gates` hängt an allen; `d-check: 171 Datei(en) geprüft, 0 Befund(e)`. Die Stage `test` von `referenztest-dotnet` und das Gradle-`test` kamen aus dem Cache (kein erneutes Ausführen) |
| `docker buildx imagetools inspect mcr.microsoft.com/dotnet/sdk:10.0.403` | Index-Digest `sha256:de4fe90a98845d75910b9769662465c84de1a934665c431279d1382b2bddc02b` = Pin im Dockerfile; Plattformen linux/amd64, arm/v7, arm64 (gefahren nur amd64) |
| Frontend-Pin | `# syntax=docker/dockerfile:1@sha256:4edf897a…` in `referenztest-dotnet/Dockerfile` und im Wurzel-`Dockerfile` identisch |

## 2. Mutationsliste (Klon, jeweils zurückgesetzt)

| # | Mutation | Ergebnis |
|---|---|---|
| M1 | SqlClient `7.1.1` → `7.1.0` in der `.csproj` | rot, `NU1004` („package reference … version has changed“), Exit 1 im Docker-Build, bestätigt |
| M2 | erster `contentHash` (SqlClient) verfälscht | rot, `NU1403` („content hash validation failed for Microsoft.Data.SqlClient.7.1.1“), bestätigt |
| M3 | `Newtonsoft.Json 13.0.3` ohne Lock-Eintrag hinzugefügt | rot, `NU1004` („package references have changed … Lock file’s package references: …“); der Restore löst nicht still auf, bestätigt |
| M4 | `packages.lock.json` gelöscht | rot (Exit 2), aber durch den `COPY` der Stage `restore`, nicht durch einen NU-Code; kein stiller Neuaufbau |
| M5 | `contentHash` eines **transitiven** Pakets (Pkcs 9.0.18) verfälscht | rot, `NU1403` für Pkcs; die Aussage „auch der transitiven“ der `.csproj` ist belegt |
| M6 | Smoke-Test absichtlich rot (`Assert.Equal(21, Class)`) | `make referenztest-dotnet` Exit 2, „Failed: 1“; Selbstprobe bestätigt |
| M7 | Netzzugriff (`HttpClient` auf `api.nuget.org`) im Test, `--network=none` im Dockerfile | rot („Resource temporarily unavailable“); **Gegenprobe** ohne `--network=none`: grün (2 bestanden). Die Option ist wirksam |

Eine erste Fassung von M5 und M7 traf wegen meiner Mutation (falscher Lock-Eintrag, blockierender Aufruf gegen Analyzer `xUnit1031`) nicht den Zweck; beide wurden korrigiert wiederholt, die Ergebnisse oben sind die der korrigierten Läufe.

## 3. Smoke-Test: Fehlerbild, Dauer, Flakiness

Fehlerbild (Mutation: `Assert.Fail` mit Text): `SqlException`, `Number 0`, `Class 20`, Text „A network-related or instance-specific error … (provider: TCP Provider, error: 35 – An internal exception was caught)“, innere Ausnahme `SocketException: Success`.

Läufe der fertigen Test-Image (`docker run --network none … dotnet test --no-build`), Dauer des Tests:

| Bedingung | Läufe | Dauer | rot |
|---|---|---|---|
| ohne Last | 8 | 771 bis 822 ms | 0 |
| parallel `make test` | 8 | 769 bis 788 ms | 0 — **Einschränkung:** `make test` kam komplett aus dem Cache, das war keine echte Last |
| CPU-Last: 40 Busy-Loop-Container auf 20 Kernen | 6 | 935 ms bis ca. 1 s (Anzeige auf ganze Sekunden gerundet) | 0 |

Bewertung der 5-s-Grenze: Messbereich unter Last höchstens etwa 1 s bei `Connect Timeout=1`; Abstand zur Grenze etwa Faktor 5. Die Dauer liegt bei etwa 0,8 × Timeout, nicht bei Millisekunden: der Fehlerpfad ist also nicht die sofortige Ablehnung, sondern die Verbindungsversuche laufen bis kurz vor den Timeout (Folgerung aus Dauer und Text, nicht bewiesen). Die Assertion auf `Number 0` und `Class 20` hielt in allen 22 Läufen. Mehr als diese Messzeilen (unter dieser Last, ein Host, amd64) trägt die Aussage „nicht flaky“ nicht.

## 4. DoD Punkt für Punkt

| DoD-Punkt | Urteil | Beleg |
|---|---|---|
| LP1 baut in Docker ohne Host-.NET und ohne Mount | **bestätigt** | Build-Kontext `referenztest-dotnet/`, nur `COPY`; `--output` nur für `lock-export`; Läufe oben |
| LP1 Digest-Pins (Frontend, SDK), SDK-Digest gegen Tag geprüft | **bestätigt** | Abschnitt 1; der in [ADR-0008](../plan/adr/README.md) gekürzte Digest `de4fe90a…` ist aufgelöst und gleich dem Index-Digest. Plattform amd64; arm64 nur im Index vorhanden, nicht gefahren |
| LP1 `nuget.config` mit `<clear />`, einer Quelle, `packageSourceMapping` | **bestätigt** | Datei gelesen; der Audit-Lauf nennt als einzige Quelle nuget.org |
| LP1 Lock mit `RestoreLockedMode`, in Stage erzeugt, per `--output` heraus | **bestätigt** | `lock`/`lock-export`; Neuerzeugung ergibt kein Diff |
| LP1 Audit und `trustedSigners` nach F1/F2 umgesetzt, Ausstieg trägt Carveout und Trigger | **bestätigt** | `NuGetAudit=false` mit Verweis `CO-001`; `trustedSigners` fehlt mit Verweis `CO-002`; Abschnitt 5 |
| LP1 roter Lauf bei Version und bei `contentHash` | **bestätigt** | M1 `NU1004`, M2 `NU1403` (am neuen Projekt nachgemessen, [ADR-0008](../plan/adr/README.md)-Codes stimmen) |
| LP2 Fragment unter `tools/mk/`, Test- und Lock-Target, je eine Zeile in `harness/README.md` | **bestätigt** | `tools/mk/referenztest-dotnet.mk` (drei Targets), drei Zeilen in §Sensors; zusätzlich Zeile „Nicht behauptet“ nachgezogen |
| LP2 Smoke ohne Server, Fehlerbild gemessen, belegt nur Gerüst | **bestätigt** | Abschnitt 3; Test enthält keine Server-Annahme |
| LP2 absichtlich roter Test macht Target rot | **bestätigt** | M6 |
| LP2 `GATE_CHECKS`-Aufnahme | **bestätigt** | Abschnitt 1 |
| LP2 Messung des Gerüsts in der Closure-Notiz | **nicht bestätigt (offen)** | §7 leer; die Messwerte stehen nirgends im Repo. Nachmessung: Kaltbau 17,6 s (Auftrag nennt 23 s; nicht reproduziert, Basis-Image lag lokal, Abweichung ungeklärt), gecacht 1,03 s (bestätigt), Image 1,61 GB (bestätigt), Smoke etwa 0,8 × Timeout (bestätigt) |
| LP3 Szenarienliste (S1 bis S18), Verweis aus slice-qa-kompatibilitaet, kein Server-Szenario im Projekt | **teilweise bestätigt** | Liste vorhanden; [slice-qa-kompatibilitaet](../plan/planning/open/slice-qa-kompatibilitaet.md) verweist in DoD und Start-Trigger darauf; das Projekt enthält genau eine Test-Methode ohne Server. Der inhaltliche Abgleich der Zeilen mit den „Offen“-Abschnitten von [ADR-0005](../plan/adr/README.md) und [ADR-0009](../plan/adr/README.md) wurde **nicht** gefahren |
| `make gates` grün | **bestätigt** (Klon, Stand gleich) | Abschnitt 1 |
| Review durchgeführt, Report liegt vor | **bestätigt** | [2026-10-30-9fcd9f4-slice-qa-dotnet-referenztest-geruest](2026-10-30-9fcd9f4-slice-qa-dotnet-referenztest-geruest.md); seine Findings F1 bis F3 sind im Baum unverändert offen (Abschnitt 6) |
| Doku-Update `harness/README.md` | **bestätigt** | Diff gelesen; `contracts/client-profile.md` nicht berührt, kein Anlass |
| Closure-Notiz mit Lerneintrag | **nicht bestätigt (offen)** | §7 „Wird bei der Closure gefüllt“ |
| Beobachtungs-Register fortgeschrieben | **nicht bestätigt (offen)** | kein neuer Eintrag, keine „keine Beobachtung“-Antwort in §7 |
| Risiken aus §6 mit Ausgang | **nicht bestätigt (offen)** | alle sieben stehen auf „offen bis zur Closure“ |
| Drei Paarungen | **teilweise** | Anker ↔ Folge-Slice: [CO-001](../plan/carveouts/CO-001-nuget-audit-aus.md) und [CO-002](../plan/carveouts/CO-002-nuget-signaturpruefung-fehlt.md) nennen [slice-qa-dotnet-lieferkette-haerten](../plan/planning/open/slice-qa-dotnet-lieferkette-haerten.md), und dieser nennt beide zurück; Register offen |

Die offenen Punkte sind Closure-Arbeit, keine Verletzung: Der Slice liegt in `in-progress/` und darf noch nicht nach `done/`.

## 5. Carveouts, Folge-Slice, Hard Rule 3.6

* **Form:** Beide Carveouts tragen Status, Datum, betroffenes Gate, Geltungsbereich, Folge-Slice, Begründung, Auflösungs-Trigger, Konfigurationstabelle, Verifikation und Geschichte; der Index führt beide.
* **Trigger beobachtbar:** CO-001: Folge-Slice in `done/` **und** `NuGetAudit` nicht mehr `false` oder eine `Accepted`-ADR, die das Werkzeug als Ersatz nennt. CO-002: Folge-Slice in `done/` und `nuget.config` führt `require` plus `trustedSigners` mit grünem Lauf, oder `Accepted`-ADR. Alle Bedingungen sind an Dateien und Verzeichnissen prüfbar.
* **Hard Rule 3.6:** Es wird keine bestehende Schwelle gesenkt; das Gate entsteht neu, und die zwei fehlenden Prüfungen sind befristete Ausnahmen mit Trigger und Folge-Slice. Der offene Punkt „braucht ein dauerhafter Verzicht eine ADR“ ist im Folge-Slice benannt und nicht vorweggenommen. Konform.
* **Bindung in `harness/README.md`:** `CO-001`, `CO-002` an der Gate-Zeile sagt „dieses Gate hat eine befristete Lücke“ und ist inhaltlich zutreffend (die Lücken liegen genau in diesem Gate). Die beiden Werkzeug-Zeilen binden dieselben Carveouts mit „kein Gate“. Sinnvoll; die Bindung ist nicht verlinkt (INFO I-1).
* **Audit-Befund:** bestätigt (Pkcs 9.0.18, High, GHSA-846f-f764-76mx); der Befund hängt am Abfragetag, hier derselbe Tag wie die Messung des Carveouts.

## 6. Out-of-Scope (§1)

Eingehalten. Kein Test setzt einen TDS-Server voraus, es gibt keinen Vergleichslauf D, keine EULA-Prüfung, keinen CI-Workflow und keine Zwei-Container-Variante in den 13 Dateien des Merges.

## 7. Findings

**HIGH:** keine.

**MEDIUM**

* **M-1 · KDoc des Smoke-Tests sagt „ohne Netz“ für den ganzen Ablauf (Hard Rule 3.8).** `Smoke/KeinServerSmokeTests.cs`: „Restore, Build und Test laufen in Docker ohne Mount und ohne Netz“. Selbst belegt: der Restore holt Pakete (4,4 s Abruf im Kaltlauf), nur die Stage `test` läuft mit `--network=none` (M7). Gleiche Feststellung wie F1 im Review; im Baum nicht behoben. Die Aussage ist breiter als der Beleg.

**LOW**

* **L-1 · `nuget.config`: „allein packages.lock.json und contentHash“ (Hard Rule 3.8).** [CO-002](../plan/carveouts/CO-002-nuget-signaturpruefung-fehlt.md) hält selbst fest, der Wert von `signatureValidationMode` ohne Eintrag sei nicht gemessen; `dotnet nuget config get signatureValidationMode` meldet nur „Key not found“, das ist keine Messung der Wirkung. „Allein“ ist breiter als die Messzeile (Review F2, offen).
* **L-2 · Index `docs/plan/carveouts/README.md` kürzt den Trigger** und lässt „Folge-Slice in `done/`“ weg (Review F3, offen).
* **L-3 · [CO-001](../plan/carveouts/CO-001-nuget-audit-aus.md) nennt nur „Schweregrad High“.** Der Lauf vom selben Tag meldet für Pkcs zwei Advisories (High und Moderate). Nicht falsch, aber unvollständig; auch die Zeile „Exit 0 auch bei Fund“ in der README ist bestätigt.
* **L-4 · Folge-Slice führt `SPEC-050` als berührte Spec-Stelle.** `SPEC-050` ist das Referenzabgleich-Gate (Vergleich mit SQL-Server-Referenzinstanz), das der Slice ausdrücklich nicht berührt; das Lock-und-Hash-Versprechen steht in `SPEC-025` (Folgerung aus dem Spec-Text, nicht von der Slice-Autorschaft bestätigt).
* **L-5 · Fehlerbild des Smoke-Tests nur als Klasse 20 / Number 0 belegt.** Die Annahme „Verbindung abgelehnt“ aus dem Review (F4) trifft nach Dauer (etwa 0,8 × Timeout) und Text nicht zu; der Test ist trotzdem in 22 Läufen stabil. Eine Verschiebung des Fehlerpfads durch eine neue SqlClient-Version würde den Test rot machen, ohne dass das Gerüst defekt wäre; das ist das Risiko, das `RestoreLockedMode` auf die Version begrenzt.
* **L-6 · Die Messwerte stehen nicht im Repo** (Kaltbau, gecacht, Image, Smoke-Dauer, Plattform). Closure-Notiz ausstehend; der Kaltwert des Auftrags (23 s) ließ sich mit 17,6 s nicht reproduzieren.

**INFO**

* **I-1** Die Bindungen `CO-001`/`CO-002` in `harness/README.md` sind unverlinkt; das Doku-Gate verlangt das nur für ADR-Kennungen.
* **I-2** Ein grüner `make gates` führt den Smoke-Test nur aus, wenn sich Eingänge der Stage ändern (Cache); wiederholte Gate-Läufe belegen nicht erneut, dass er stabil ist. Das ist das akzeptierte Risiko des Slice (§6, zweiter Punkt) und noch ohne Ausgang.
* **I-3** Das Lock führt 36 Pakete (32 transitiv); [ADR-0008](../plan/adr/README.md) nannte für den Spike 37 mit unbekanntem Test-Framework. Wahl xunit 2.9.3 und Microsoft.NET.Test.Sdk 18.11.0 ist im Slice noch nicht in einer Closure-Notiz festgehalten.

## 8. Nicht gefahren

Abgleich der Szenarienliste S1 bis S18 mit den „Offen“-Abschnitten von [ADR-0005](../plan/adr/README.md) und [ADR-0009](../plan/adr/README.md); arm64-Lauf; Kaltbau mit frischem Pull des Basis-Images; eine Messung des Defaults von `signatureValidationMode`.
