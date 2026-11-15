# Verifikation: slice-maven-erste-veroeffentlichung — 2026-11-15

**Rolle:** Verifier (Frage: bauen wir es richtig, gegen Plan und DoD). **Gegenstand:** Stand 8c73cdb5 (`main`, Worktree `worktree-agent-a9809cfbbc3c5a003` per `git merge --ff-only main` auf `main` gebracht; der Worktree stand vorher auf 31d55bd9 und kannte weder den Neuschnitt noch [ADR-0049](../plan/adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md)). **Eingang:** Slice `docs/plan/planning/done/slice-maven-erste-veroeffentlichung.md` (verkleinert auf einen Liefer-Punkt), [ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md), [ADR-0048](../plan/adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md), [ADR-0049](../plan/adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md), Messprotokolle [Tag-Lauf](../messungen/veroeffentlichung-tag-lauf-2026-11-15.md) und [Dispatch](../messungen/veroeffentlichung-dispatch-2026-11-15.md). **Bezug:** [TDS-RB-05](../../spec/lastenheft.md#tds-rb-05--lizenz), [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit). **Modell:** claude-sonnet-5-5.

## Urteil

**Der Liefer-Punkt „Veröffentlicht“ ist belegt; das Messprotokoll trägt seine Aussagen, mit zwei kleinen Ungenauigkeiten (INFO).** Keine HIGH, keine MEDIUM. 1 LOW (Nachweis-Pflichten offen, erwartet vor Closure), 3 INFO. Nichts veröffentlicht, nichts gepusht, keine Last erzeugt; die Abfragen waren lesend.

Der Slice ändert in diesem Stand nur Planungsdokumente und Messprotokolle; `src/`, `testserver/` und `.github/` sind gegenüber `v0.1.0` unverändert (`git diff --stat v0.1.0 HEAD -- .github tools/publish.sh` leer). Für diese Änderungsart sieht `harness/README.md` §Minimal agent workflow `make docs-check`, `make flaeche`, `make kopplung-zahlen` vor; `make clean-build` und `make e2e` sind nicht erforderlich und nicht gefahren.

## Belege, selbst erhoben (Abfragen 2026-11-15, nur lesend)

| Aussage des Slice oder Protokolls | Eigener Beleg | Urteil |
|---|---|---|
| Lauf 43699125460, Tag-Push, `success` | `gh run view 43699125460 --json …`: `conclusion` `success`, `event` `push`, `headBranch` `v0.1.0`, `headSha` `b0109953…`, `createdAt` 09:24:43Z, `updatedAt` 09:39:58Z | belegt |
| Ausgang je Job | `gates` success 09:24:45 bis 09:37:58, `publish` success 09:37:59 bis 09:39:58, `vorschau` skipped (0 Schritte); `publish`-Schritte: checkout, „Tag-Commit liegt in origin/main“, `make publish`, alle success | belegt, Zeiten stimmen mit dem Protokoll |
| Paket in der Pakete-API | `gh api /users/pt9912/packages/maven/io.github.pt9912.tdswire`: Typ `maven`, `visibility` `public`, `version_count` 1, `created_at` 09:39:40Z, `repository.full_name` `pt9912/tdswire`, `private` false | belegt (Zuordnung und Sichtbarkeit) |
| Version `0.1.0` | `…/versions`: genau eine Version `0.1.0`, `license` `MIT`, `created_at` 09:39:40Z | belegt |
| Tag auf dem Release-Commit | `gh api repos/pt9912/tdswire/git/ref/tags/v0.1.0`: `object.type` `commit`, `sha` `b0109953…`; lokal `git cat-file -t v0.1.0` = `commit` (leichtgewichtig), `git rev-parse 'v0.1.0^{commit}'` = `b0109953…` | belegt |
| Release-Commit trägt Version ohne `-SNAPSHOT`, liegt in `main` | `git show v0.1.0:build.gradle.kts`: `version = "0.1.0"`; `git merge-base --is-ancestor b0109953 main` Exit 0 | belegt |
| Danach `-SNAPSHOT` zurück | `build.gradle.kts` auf HEAD: `version = "0.1.0-SNAPSHOT"`; Commit `c6523593`, in `origin/main` (31d55bd9) | belegt |
| Workflow wie von [ADR-0048](../plan/adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Entscheidung 2 gesetzt | `git show v0.1.0:.github/workflows/veroeffentlichung.yml`: `permissions: {}` im Kopf, `gates` `contents: read`, `publish` `needs: gates`, `if` push und `ref_type == 'tag'`, `contents: read` und `packages: write`, Prüfung `merge-base --is-ancestor`, `GITHUB_TOKEN` nur im `env:` des Schritts `make publish`, `concurrency` je Ref mit `cancel-in-progress: false`, kein `--url`; Actions per SHA gepinnt | belegt (Textabgleich) |
| Protokollzeilen (Rechte, Klon, Skriptzeile, Maskierung) | Log des Laufs selbst geladen (`gh api …/runs/43699125460/logs`): `Packages: write` unter „GITHUB_TOKEN Permissions“ (09:38:00); `[new tag] v0.1.0`, `rev-parse refs/tags/v0.1.0^{commit}`, `detached HEAD`; `publish: Version 0.1.0, Tag v0.1.0 auf HEAD, Baum sauber, Zugangsdaten gesetzt; Ziel https://maven.pkg.github.com/pt9912/tdswire`; `GITHUB_TOKEN: ***`; Suche nach `ghs_`, `ghp_`, `github_pat`, `Bearer` ohne Treffer; `BUILD SUCCESSFUL in 22s` (Gradle-Publish); 1402 `PASSED` im Job `gates` (die Zählung über meinen doppelt entpackten Abzug ergab 2804 = 2 × 1402) | belegt, im Rahmen der genannten Muster |

## DoD (Slice §2, ein Liefer-Punkt)

| Teilaussage | Urteil | Beleg |
|---|---|---|
| Version liegt in GitHub Packages unter `io.github.pt9912:tdswire`, Paket und Version in der Pakete-API gesehen | **konform** | Abfragen oben. Der Slice schränkt selbst ein: die Dateien der Version sind nicht Gegenstand; das Protokoll nennt die Dateiliste „ungemessen“, und die Abfrage der Version liefert tatsächlich keine Dateien. Aussage und Beleg decken sich. |
| Zuordnung zu `pt9912/tdswire` | **konform** | Feld `repository` |
| Release-Commit mit Tag `v<Version>`, den der Nutzer pusht; danach `-SNAPSHOT` | **konform** | Tabelle oben; dass der Nutzer pushte, steht im Protokoll (Auslösung „durch den Tag-Push des Nutzers `pt9912`“) und in der Bestätigung im Plan-Kopf; die Lauf-Daten tragen `push` des Tags. Wer den Push ausführte, ist am Lauf (`triggering_actor`) nicht von mir abgefragt worden. |
| Messprotokoll: Datum, Lauf-Kennung, Ausgang je Job, Dauer, ohne Token | **konform** | Datei liegt; alle fünf Angaben vorhanden; keine Tokenmuster im Protokolltext (`grep` auf die genannten Präfixe in der Datei selbst nicht nötig, sie nennt nur `***`) |
| Protokoll nennt, was der Lauf belegt (Schreibrecht, Zuordnung, Klon, Maskierung) und was ungemessen bleibt | **konform** | Abschnitt „Was das Protokoll zeigt“ und „Was dieser Lauf nicht belegt“; die vier Punkte stimmen mit meinen Belegen überein. Die Aussage zu Maskierung ist mit der Grenze „nur die genannten Muster“ versehen (§3.8 eingehalten). |

## Gegen die ADRs

| Quelle | Prüfung | Urteil |
|---|---|---|
| [ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md) Entscheidung 3a | Version ohne `-SNAPSHOT` im Release-Commit, nie neu veröffentlicht: eine Version, ein Lauf, kein Re-Run | konform |
| [ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md) Folgepflicht „erste echte Veröffentlichung mit Messprotokoll“ | Messprotokoll liegt; der Weg „von Hand“ ist durch [ADR-0048](../plan/adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) ersetzt, der Slice sagt das im Bezug | konform |
| [ADR-0048](../plan/adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Entscheidung 1 und 2 | Veröffentlichung nur über den Workflow bei Tag-Push, kein Token außer `GITHUB_TOKEN`, Reihenfolge `gates` vor `publish`, Tag in `origin/main` | konform (Workflow-Text, Lauf-Schritte) |
| [ADR-0048](../plan/adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Entscheidung 4 | Die vier Aussagen, die der Dispatch-Lauf offen ließ, sind im Tag-Lauf gemessen (Protokoll Abschnitt „Was die Liste des Ungemessenen … jetzt trägt“: Punkte 1 bis 4 belegt); Punkte 5, 6, 8, 10 bleiben ausdrücklich ungemessen | konform |
| [ADR-0048](../plan/adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Folgepflicht 2 (Anpassung des Plans) | Bestätigung als Tag-Push im Plan-Kopf, Ablauf „Nutzer pusht, Workflow läuft“, 6c aus der Liste „NICHT“ genommen | konform |
| [ADR-0049](../plan/adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) Entscheidung 4 | `0.1.0` liegt nur in GitHub Packages und wird nicht neu veröffentlicht; der Slice führt kein Cloudsmith ein und nennt den Konsum, die Dateiliste und die Nutzer-Doku als Gegenstand von `slice-cloudsmith-veroeffentlichung-0-1-1` (Datei liegt in `open/`) | konform |
| [ADR-0049](../plan/adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) Folgepflicht 3 (Nachzug des laufenden Plans) | Titel, Risiko „Konsumenten ohne Konto oder Token“ und der Nutzer-Doku-Punkt sind im Neuschnitt nachgezogen | konform |

Abgrenzung zum Neuschnitt geprüft: Die entfernten Liefer-Punkte (Konsum, Nutzer-Doku und `releasing.md`) stehen in §1 „Ausdrücklich NICHT“ mit Verweis, in §2 steht nur noch das, was das Protokoll belegt. Das Zielbild des Plans („Konsum mit `read:packages`-Token“) ist als angepasst benannt, das Lastenheft bleibt unberührt.

## Befunde

- **LOW-1 — Nachweis-Pflichten des Slice (§2) sind noch offen.** Review-Report zu diesem Slice fehlt unter `docs/reviews/` (nur Reports zu `slice-maven-veroeffentlichung`), Closure-Notiz (§7: „Wird bei der Closure gefüllt“), Register-Fortschreibung, Ausgang der fünf Risiken (§6, alle „offen bis zur Closure“) und die Paarungen sind nicht getan; `make gates` des Slice-Endstands ist von mir nicht gefahren (kein `src/`-Anteil, Beleg-Stempel). Das ist vor dem Verschieben nach `done/` zu erledigen, kein Mangel des Liefer-Punkts. Bei der Closure zu beachten: die Risiken 1 (Teilbestand), 3 (Rechte, Klon, Maskierung) sind nach Protokoll teils belegt, teils (Dateiliste) an `slice-cloudsmith-veroeffentlichung-0-1-1` übergeben; Risiko 4 (Tag-Schutz) ist nur mit Bypass gemessen (Protokoll Punkt 6), „nicht wirksam ohne Bypass“ bleibt ungemessen. Der Ausgang je Risiko muss das so sagen, nicht „entfallen“.
- **INFO-1 — Zeitangabe im Protokoll.** Der Schritt „Aufgabe `publishMavenPublicationToRegistryRepository` ab 09:39:39“ stützt sich auf das Log, dessen Kopfzeile dieser Aufgabe um 09:39:56 steht; 09:39:39 ist die Zeit der davor laufenden Aufgaben (`generatePomFile…`, 09:39:39). Das Paket entsteht 09:39:40 (API), der Beginn liegt also zwischen 09:39:39 und 09:39:40; die Aussage „liegt in der Aufgabe“ stimmt, die Anfangszeit ist eine obere Schranke, keine Messung.
- **INFO-2 — Zwei Runner-Image-Versionen im selben Lauf.** Das Protokoll nennt sie selbst; kein Mangel, aber für die Reproduzierbarkeit ([TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)) bleibt das Runner-Image ungepinnt (Kontext, schon in [ADR-0048](../plan/adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) als Setzung geführt).
- **INFO-3 — Reihenfolge der Spec-Nachzüge.** Der Slice führt `SPEC-052` und `SPEC-059` als „nur, wenn die Messung eine Aussage widerlegt“; die Messung widerlegt keine, die Spec-Dateien sind unberührt. Konform, vermerkt, damit die Closure die Nichtberührung nennt.

## Sensoren (selbst gefahren auf 8c73cdb5)

| Sensor | Exit | Beleg |
|---|---|---|
| `gh run view`, `gh api` (Paket, Versionen, Tag-Ref, Lauf-Logs) | 0 | lesend; Log-Abzug im Scratchpad, nicht im Repo |
| `make docs-check` | siehe Schluss | direkter Exit-Code |

## Schluss

`make docs-check` nach Anlegen dieses Berichts: Ergebnis im Handback an den Aufrufer (Exit direkt gemessen). `make flaeche` und `make kopplung-zahlen` sind für diesen Bericht nicht gefahren, da der Bericht weder `spec/` noch `src/` berührt und der Auftrag nur `docs-check` verlangt.
