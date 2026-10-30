# Verifikation slice-qa-reproduzierbarkeit (DoD- und Entscheidungs-Konformität)

**Rolle:** Verifier (Modul 11) · **Stand:** HEAD `cff52a9`, main · **Datum:** 2026-10-30
**Gegenstand:** [slice-qa-reproduzierbarkeit](../plan/planning/done/slice-qa-reproduzierbarkeit.md), Welle [welle-qualitaet-grundlage](../plan/planning/welle-qualitaet-grundlage.md)
**Bezug:** [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0010](../plan/adr/0010-detekt-pre-release.md)
**Adressat:** Planner. Der Verifier repariert nichts und entscheidet den Schnitt nicht.

## 1. Urteil

**Der Slice ist so nicht schließbar.** Die beiden Teile, die dieser Slice liefert (CI-Workflow, Clean-Build samt
Stage `deps`), sind bestätigt. DoD-Punkt 3 (Vergleichslauf D) ist offen und hat keinen Träger außer diesem Slice;
er ist ohne Referenzclient-Szenarien nicht umsetzbar, und drei andere Planungs-Artefakte warten auf ihn.
Die Closure-Pflichten (Review-Report, Closure-Notiz, Risiko-Ausgang, Register) sind nicht erledigt; die Slice-Datei
trägt noch keine Häkchen und §7 ist leer.

Vorschlag zum Schnitt in §6 (Entscheidung beim Planner).

## 2. DoD Punkt für Punkt

Der Slice hat drei Liefer-Punkte (Grenze ≤ 3 eingehalten) und danach die Nachweis-Pflichten.

| # | DoD-Punkt | Urteil | Beleg |
|---|---|---|---|
| 1 | CI-Workflow führt `make gates` in Docker aus und ist grün | **bestätigt** | §3.1 |
| 2 | Clean-Build ohne Host-Toolchain, einschließlich .NET-Restore | **bestätigt mit Einschränkung** | §3.2, §3.3; der .NET-Restore ist durch `make clean-build` nicht gedeckt, nur durch den CI-Lauf (M-2) |
| 2a | NuGet-Audit und `trustedSigners` | **offen, Träger vorhanden:** [slice-qa-dotnet-lieferkette-haerten](../plan/planning/open/slice-qa-dotnet-lieferkette-haerten.md) | [CO-001](../plan/carveouts/CO-001-nuget-audit-aus.md) und [CO-002](../plan/carveouts/CO-002-nuget-signaturpruefung-fehlt.md) nennen ihn als Folge-Slice; der Slice liegt in `open/`, seine DoD hat zwei Liefer-Punkte (Signaturprüfung, Schwachstellenprüfung). Der gesperrte Restore liegt bei [slice-qa-dotnet-referenztest-geruest](../plan/planning/done/slice-qa-dotnet-referenztest-geruest.md) (`done/`). |
| 3 | Vergleichslauf D als eigenes Make-Target, Ausnahmeliste, Messungen, Anmelde-Szenario, Codec-Antwortseite | **offen, kein weiterer Träger** | Kein Target, keine Ausnahmeliste, keine Messung im Repo (`make help`, `tools/mk/` geprüft). Das Nicht-Anlegen eines Stubs ist richtig und deckt sich mit `harness/README.md` §Nicht behauptet. Die Wire-Messung (Tokenreihenfolge, DONE-Statusbits, RETURNVALUE-Zählung) liegt bei [slice-referenzabgleich-mitschnitt](../plan/planning/open/slice-referenzabgleich-mitschnitt.md); hier bleibt, was der Client beobachtet. |
| 4 | `make gates` grün | **bestätigt für CI** (Lauf auf `bda6cd6`), lokal nicht von mir gefahren | §3.1; HEAD `cff52a9` ist nicht gepusht (19 Commits vor `origin/main`), auf HEAD gibt es daher keinen CI-Lauf. Die Workflow-Datei ist auf `bda6cd6` und HEAD byte-gleich (`git show bda6cd6:.github/workflows/gates.yml \| diff`). |
| 5 | Review-Report unter `docs/reviews/` | **nicht bestätigt** | Kein Report zu diesem Slice in `docs/reviews/` |
| 6 | Doku-Update | **teilweise**; siehe M-1 (Lauf-Status in `harness/README.md` veraltet) | |
| 7 | Closure-Notiz mit Steering-Loop-Lerneintrag | **nicht bestätigt** | §7 der Slice-Datei: „Wird bei der Closure gefüllt." |
| 8 | Beobachtungs-Register fortgeschrieben | **nicht bestätigt** | `BEO-TDS/messung-am-echten-sql-server-fehlt` steht auf *geplant*, `BEO-TDS/vergleichslauf-d-ungeklaert` auf *offen*, beide mit diesem Slice als Träger; keine neue Evidenz zu diesem Slice |
| 9 | Jedes Risiko aus §6 trägt einen Ausgang | **offen** | Einziges Risiko: „offen bis zur Closure" |
| 10 | Die drei Paarungen | **nicht prüfbar vor Closure** | |

## 3. Eigene Läufe

### 3.1 CI-Workflow `.github/workflows/gates.yml`

- Datei existiert, führt als einzigen Schritt `make gates`; `runs-on: ubuntu-24.04` (exakt), `permissions: contents: read`,
  `fetch-depth: 0` (nötig für `tools/harness/history-range-guard.sh`, das einen Shallow-Klon abweist), `persist-credentials: false`.
- `actions/checkout@d23441a48e516b6c34aea4fa41551a30e30af803`: per `gh api repos/actions/checkout/git/ref/tags/v6.1.0`
  aufgelöst, SHA stimmt mit dem Kommentar „v6.1.0" überein.
- `gh run list`: fünf Läufe des Workflows `gates`, alle `success`, alle `push` auf `main`:

| Run-ID | Commit | Laufzeit (createdAt bis updatedAt) | enthält Stage `deps` |
|---|---|---|---|
| 41048175295 | `bda6cd6` | 11:27:07 bis 11:32:22 (5 m 15 s) | ja |
| 41040960552 | `f42a18f` | 10:28:04 bis 10:32:42 (4 m 38 s) | nein (vor `34393a5`) |
| 41032109029 | `025bb11` | 09:15:30 bis 09:20:11 (4 m 41 s) | nein |
| 41028544823 | `30baefa` | 08:45:01 bis 08:49:26 (4 m 25 s) | nein |
| 41023977695 | `ed291de` | 08:04:09 bis 08:07:55 (3 m 46 s) | nein |

- Log von 41048175295 (selbst gelesen): `make gates` läuft; `baseline-verify` und d-check; `docker build --target detekt`
  mit `[deps 4/4] RUN gradle --no-daemon abhaengigkeitenLaden` **nicht cached** (41 s, 28 Konfigurationszeilen
  `abhaengigkeitenLaden: ... (n Dateien)`); danach `build`, `detekt-selbsttest`, das .NET-Gate
  (`docker build --target test ... referenztest-dotnet`: `[restore 2/2] RUN dotnet restore` frisch ausgeführt, dann
  `RUN --network=none dotnet test --no-restore`: 1 von 1 bestanden), `test` (BUILD SUCCESSFUL in 1 m 1 s). Job-Ende grün.
- `make workflow-lint`: Exit 0, 0,8 s.

### 3.2 `make clean-build` (ein Lauf, `--no-cache`)

Exit 0, **7 m 49,94 s (469,9 s)** wall clock. Der Implementer nannte 356 s. Abweichung +32 %; auf dem Host liefen parallel andere
Docker-Läufe (Implementer), die Zahl ist deshalb eine Obergrenze und kein Widerspruch. Die drei Teilbuilds liefen alle mit
frischem `deps` (48 s, 48 s, 40 s), Gradle-Läufe BUILD SUCCESSFUL (Jar, Test 2 m, detekt 2 m 38 s). Die in die Zusage
„Clean-Build in x Sekunden" zu übernehmende Zahl ist damit **470 s, nicht 356 s**, falls sie irgendwo steht (im Repo habe ich sie nicht gefunden).

### 3.3 Stage `deps` (`abhaengigkeitenLaden`)

Alle Proben auf Kopien aus `git archive HEAD` im Scratchpad, nicht im Working Tree.

- Quelltext-Änderung (Kommentarzeile an `Principal.kt`), `docker build --target compile --progress=plain .`: `deps 1/4` bis `4/4`
  **CACHED**; nur `compile 1/2` (COPY src) und `compile 2/2` (`gradle classes`, 27 s) laufen; **keine** Download-Zeile.
  Dasselbe für `--target detekt` (89 s, 0 Treffer auf „download", deps CACHED) und `--target build-jar` (34 s, 0 Treffer).
  `--target test` war mit dieser Änderung bereits im Cache (1,3 s); für `test` ist die Probe daher nicht belastbar, `compile` und `detekt` tragen sie.
  `--network none` habe ich nicht benutzt (ändert den Cache-Schlüssel).
- Gegenprobe: eine Zeile an `gradle/libs.versions.toml` (Eingang der Stage) lässt `deps 2/4` und folgende neu laufen.

## 4. Mutationen

| # | Mutation (Kopie bzw. stdin, nichts im Working Tree) | Erwartung | Ergebnis |
|---|---|---|---|
| M1 | `runs-on:` zu `runs:` (stdin an actionlint) | rot | **rot**: „runs-on section is missing", „unexpected key runs" |
| M1b | dasselbe über `make workflow-lint WORKFLOWS=<Kopie>` | rot | **rot**, make-Exit 2 (`|| exit 1` greift) |
| M2 | `permissions:` zu `permission:` | rot | **rot** |
| M3 | Action-Pin `@<sha>` zu `@v6` (Tag statt Commit) | actionlint prüft Pins nicht | **grün, Exit 0**: bestätigt, die SHA-Pin-Pflicht hat keinen Sensor |
| M4 | `run: make gates` zu `run: make docs-check` | actionlint prüft das nicht | **grün, Exit 0**: kein Sensor hält fest, dass der Workflow `make gates` ruft |
| M5 | `make workflow-lint WORKFLOWS=" "` (leer) | rot | **rot**: „keine Datei unter .github/workflows/" |
| M6 | Version `coroutines` in `libs.versions.toml` und in `gradle.lockfile` auf `1.10.99` (nicht existent), `--target deps` | rot in der Task | **rot**: `Task :abhaengigkeitenLaden FAILED`, „Could not find kotlinx-coroutines-core:1.10.99" |
| M7 | ein Byte der SHA-256 von `kotlinx-coroutines-core-jvm-1.10.1.jar` in `verification-metadata.xml` geändert, `--target deps` | rot | **rot**: „Dependency verification failed ... One artifact failed verification" |
| M8 | Quelltext-Änderung vs. `deps` | `deps` bleibt CACHED | **bestätigt**, §3.3 |
| M9 | Eingangsdatei `libs.versions.toml` geändert | `deps` läuft neu | **bestätigt** |

Working Tree am Ende: `git status --short` zeigt nur das bereits vor dem Lauf vorhandene, ungetrackte `.claude/worktrees/`.
Diese Berichtsdatei kommt hinzu. Nicht committet, kein Push.

## 5. Weitere Prüfungen

- **harness/README.md gegen Makefile (5):** `workflow-lint` und `clean-build` stehen in der Tabelle *Werkzeuge* mit „kein Gate"
  und Bindung [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit). In `tools/mk/ci.mk` gibt es kein `GATE_CHECKS +=`; ein
  `grep GATE_CHECKS` über Makefile, `repo.mk`, `tools/mk/`, `harness/mk/` führt beide nicht. Stimmt.
- **Aussagen über Messung hinaus ([AGENTS.md](../../AGENTS.md) §3.8) (6):** Workflow-Kommentare (v6.1.0, Runner-Version, Credentials, Historie)
  sind belegt (§3.1). `ci.mk`: „Jede Workflow-Datei geht ueber stdin ... nichts wird gemountet" belegt. „--no-cache verwirft den Layer-Cache der
  drei Stages" ist ungenau (drei `docker build`-Aufrufe über mehr als drei Stages, `testkeys` eingeschlossen), harmlos (L-3).
  Die Slice-Datei macht keine Messaussage über das Ziel hinaus; §1 benennt D als hier verbleibend, ohne Zahlen. Eine Zusage ohne Beleg: siehe M-1
  (README sagt „nicht belegt", der Beleg existiert).
- **Out-of-Scope (7):** Die Wahl des Referenzclients ([ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md)) ist nicht angefasst. Keine
  Gate-Lockerung ([AGENTS.md](../../AGENTS.md) §3.6): `make gates` unverändert, `clean-build` und `workflow-lint` sind additiv.
- **Zusagen ohne Träger:** NuGet-Audit und `trustedSigners` haben Träger und Carveouts mit Trigger. Der Slice
  [slice-qa-dotnet-lieferkette-haerten](../plan/planning/open/slice-qa-dotnet-lieferkette-haerten.md) trägt „ohne Welle" und fehlt in der Slice-Tabelle von
  [welle-qualitaet-grundlage](../plan/planning/welle-qualitaet-grundlage.md); [welle-referenzabgleich-release](../plan/planning/welle-referenzabgleich-release.md) nennt ihn. Verweise sind vorhanden und lösen auf.
  Zusage von D ohne anderen Träger: siehe H-1.

## 6. Findings

### HIGH

**H-1 — DoD-Punkt 3 (Vergleichslauf D) ist offen, hat keinen anderen Träger und eine nicht deklarierte Abhängigkeit.**
D ist laut [slice-qa-kompatibilitaet](../plan/planning/open/slice-qa-kompatibilitaet.md) §1 dort ausdrücklich ausgeschlossen („Träger ist slice-qa-reproduzierbarkeit"),
laut [slice-referenzabgleich-live](../plan/planning/open/slice-referenzabgleich-live.md), [slice-referenzabgleich-mitschnitt](../plan/planning/open/slice-referenzabgleich-mitschnitt.md) und
[welle-referenzabgleich-release](../plan/planning/welle-referenzabgleich-release.md) die Voraussetzung ihrer Starts (Make-Target, Container, EULA, Messung, Image-Tag). Der Slice kann nur nach `done/`, wenn D geliefert ist;
D braucht Referenzclient-Szenarien (Anmelde-Szenario, Mehrfach-Resultset, RPC mit Ausgabewert), die erst `slice-qa-kompatibilitaet` schreibt. Die Rückführungen in §4 des Slice nennen nur „CI-Runner ohne Docker", nicht
„Szenarien des Referenzclients fehlen". Die Kette ist damit nicht aufgelöst: live wartet auf reproduzierbarkeit, reproduzierbarkeit auf Szenarien, die in `slice-qa-kompatibilitaet` liegen, und dieser Slice wiederum hat keinen Eintrag in §4, der das benennt.
Folge: „Alle Punkte der DoD erledigt" (§5) ist nicht erreichbar, solange der Slice in dieser Gestalt bleibt.

*Vorschlag (Entscheidung beim Planner), zwei Wege:*
(a) **Schnitt:** D, EULA-Prüfung, Messung, Anmelde-Szenario und Codec-Antwortseite in einen eigenen Slice (Arbeitstitel `slice-qa-vergleichslauf-d`, Start nach `slice-qa-kompatibilitaet`) verlagern; `slice-qa-reproduzierbarkeit` schließt dann mit DoD 1 und 2 (Closure-Pflichten noch offen); Verweise in live, mitschnitt, release-Welle, kompatibilitaet, den beiden `BEO-TDS`-Einträgen und den Carveout-Texten nachziehen.
(b) **Rückführung** `in-progress` → `open`, bis die Szenarien da sind, mit der Abhängigkeit in §4 benannt. Dann bliebe der fertige CI-Teil ungeschlossen liegen; (a) ist der kleinere Eingriff.

### MEDIUM

**M-1 — `harness/README.md` §Aktueller Lauf-Status ist veraltet.** Dort steht „ein Lauf auf dem Runner ist nicht belegt, ein Badge steht deshalb nicht hier". Seit Run 41048175295 (und vier früheren) ist er belegt;
der Plan §3 des Slice nennt „CI-Badge als Lauf-Status" als Änderung. Die Aussage unterschreitet den Beleg (kein §3.8-Verstoß nach oben), ist aber falsch geworden.

**M-2 — Der „Clean-Build einschließlich .NET-Restore" hängt allein am CI-Lauf.** `make clean-build` baut nur Jar, Test und detekt; das `referenztest-dotnet`-Gate (mit `dotnet restore`) ist nicht darin, und eine zweite lokale Probe fehlt.
Der frische Runner belegt den Restore (§3.1, nicht cached), das ist ein gültiger Beleg, aber die Zusage „`make clean-build` ist der Nachweis" (README: „für den Clean-Build-Nachweis") deckt .NET nicht. Entweder Wortlaut einschränken oder Target erweitern.

**M-3 — Closure-Pflichten sind nicht begonnen:** kein Review-Report, §6-Risiko ohne Ausgang, §7 leer, DoD-Häkchen fehlen. Kein Befund gegen Code, aber der Weg nach `done/` ist offen.

### LOW

- **L-1:** Kein Sensor für die SHA-Pin-Pflicht (M3) und für „der Workflow ruft `make gates`" (M4). `make workflow-lint` fängt Syntaxfehler, mehr nicht. Das Wort „Lint" in der README-Zeile ist ehrlich; ein Hinweis in der Zeile, was er *nicht* prüft, fehlt.
- **L-2:** HEAD ist 19 Commits vor `origin/main`; der grüne Beleg liegt auf `bda6cd6`. Für die Workflow-Datei unerheblich (byte-gleich), für „`make gates` grün auf HEAD" fehlt der CI-Beleg bis zum Push.
- **L-3:** `ci.mk` „drei Stages" ungenau (§5). Laufzeit 470 s statt 356 s (§3.2), lastabhängig.
- **L-4:** `--target test` konnte ich für die `deps`-Probe nicht belastbar machen (Layer lag im Cache); `compile`, `detekt` und `build-jar` tragen die Aussage.
- **L-5:** [slice-qa-dotnet-lieferkette-haerten](../plan/planning/open/slice-qa-dotnet-lieferkette-haerten.md) steht „ohne Welle" und in keiner Slice-Tabelle der Wellen-Pläne der Qualitäts-Welle; die Welle-Closure-Paarung (Folge-Slice) wird das sehen.

## 7. Zusammenfassung

- Bestätigt: DoD 1 (CI, 5 grüne Läufe, `deps` und .NET-Gate im Log von 41048175295), DoD 2 (Clean-Build 470 s, `deps` mit Quelltext-Änderung nicht neu, Mutationen M6/M7 rot), `workflow-lint` rot bei M1/M1b/M2/M5, grün bei M3/M4 (bekannte Lücke), Working Tree sauber.
- Offen mit Träger: NuGet-Audit und `trustedSigners` ([slice-qa-dotnet-lieferkette-haerten](../plan/planning/open/slice-qa-dotnet-lieferkette-haerten.md), [CO-001](../plan/carveouts/CO-001-nuget-audit-aus.md), [CO-002](../plan/carveouts/CO-002-nuget-signaturpruefung-fehlt.md)); Wire-Messung ([slice-referenzabgleich-mitschnitt](../plan/planning/open/slice-referenzabgleich-mitschnitt.md)).
- Offen ohne weiteren Träger: Vergleichslauf D (H-1). Closure daher nicht möglich; Schnitt (a) vorgeschlagen.
