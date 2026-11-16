# Re-Review-Report: slice-release-job-im-workflow-bauen — 2026-11-16

**Review-Art:** Re-Review der Nachbesserung, in frischem Kontext; nur die Commits nach dem Report-Stand, gegen Plan, ADR und Hard Rules; nicht gegen die DoD.

**Gegenstand:** `git diff 461aa00b..a1b762f7` (ein Commit, 6 Dateien: `.github/workflows/veroeffentlichung.yml`, `docs/maintainer/releasing.md`, `harness/README.md`, `tools/release-anlegen-selbsttest.sh`, `tools/release-notiz.vorlage`, der erste Report). Vorgänger: [Review `461aa00b`](2026-11-16-461aa00b-slice-release-job-im-workflow-bauen-review.md) (F-1 MEDIUM, F-2 bis F-4 LOW, I-3).

**Skill:** `.harness/skills/reviewer.md` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-16

**Eingangs-Kontext:** Slice [slice-release-job-im-workflow-bauen](../plan/planning/in-progress/slice-release-job-im-workflow-bauen.md); [ADR-0050](../plan/adr/0050-github-release-im-tag-lauf.md), [ADR-0048](../plan/adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md), [ADR-0049](../plan/adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md), [ADR-0024](../plan/adr/0024-lokale-stempel-zwei-klassen.md); [TDS-RB-05](../../spec/lastenheft.md#tds-rb-05--lizenz), [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit); `AGENTS.md` 3.7, 3.8.

**Selbst gefahren (nur enge Läufe, nichts nach außen):** `bash tools/release-anlegen-selbsttest.sh` auf dem Host (`gruen`, 17 s); dasselbe Skript an einer Kopie im Scratchpad, mit einer Ausgabe je Gegenprobe (welche Prüfungen die Kopie rot macht) und vier zusätzlichen Mutationen für die Prüfungen ohne Gegenprobe; `make workflow-lint` (grün); `make skript-selbsttest` (Exit 0, **aus dem Cache**, `RUN` nicht frisch in Docker gefahren); `make docs-check` (Exit 0, 1546 Dateien, 0 Befunde, vor diesem Report). Kein `clean-build`, kein `e2e`, keine Last, kein `gh`.

## Zustand der ersten Findings

| Finding | Stand | Beleg |
|---|---|---|
| F-1 MEDIUM, Gegenproben liefen an einer zweiten Fassung | **behoben** | `wf_befunde` ist weg; `wf_pruefungen` ist eine Funktion, die der echte Workflow (`echt=$(wf_pruefungen "$wf")`) und jede Kopie (`gegenprobe`) lesen. 18 Gegenproben, jede mit Prüfung des echten Workflows als Grundlage. Neue Lücke: R-1. |
| F-2 LOW, README-Zelle | **teilweise** | Zelle hat 199 Zeichen mit den Randleerzeichen (197 ohne), Grenze 200 gehalten. Gegenüber `c2c3209e` fehlen weiter `Abbruch` (`e2e.sh`) und `Werte` (`lasttest*.sh`): R-3. |
| F-3 LOW, Plural in der Vorlage | **behoben** | Neuer Satz „die Pakete dieser Version stehen oben“ ist für `v0.1.0` (GitHub Packages) und `v0.1.1` (zusätzlich Cloudsmith) wahr; INFO R-5. |
| F-4 LOW, Kopfkommentar | **behoben** | Der Kopf nennt `make release-dateien VORSCHAU=1` im Dispatch-Lauf; der Job `vorschau` führt den Schritt aus (gelesen). INFO R-6. |
| I-3 INFO, `gh` ohne `GH_REPO` ungemessen | **übernommen** | `releasing.md` nennt es unter „Ungemessen“. |

## Prüfung von `wf_pruefungen`

- **Eine Funktion, zwei Leser:** ja. Der echte Workflow und jede Kopie gehen durch dieselbe Funktion; die Gegenprobe prüft, dass der Name der Prüfung in der Ausgabe steht.
- **Maskiert eine `&&`-Kette spätere rote Fälle?** Nein. Die einzige `&&`-Kette (`continue-on-error`) steht in einem Befehl für sich und läuft bei fehlendem Treffer weiter; alle anderen Prüfungen sind `… || rot_` oder `! … || rot_` und unabhängig, die Funktion läuft bis `echo "$out"` durch. Ein roter Fall stoppt die folgenden nicht (an den Kopien sichtbar: `job-release` meldet zehn Prüfungen zugleich).
- **Löst jede Gegenprobe genau die benannte Prüfung aus?** Die benannte immer, **genau** sie nicht in allen Fällen. Gemessene Ausgabe je Gegenprobe: `recht-im-job` → `recht-einmal recht-im-job`; `vorschau-nur-in-vorschau` → `bau-ohne-vorschau vorschau-nur-in-vorschau`; `token-nur-im-anlegen` → `token-nur-im-anlegen token-zweimal`; `token-zweimal` → `token-zweimal vorschau-ohne-secret`; `token-im-anlegen` → `token-im-anlegen token-zweimal`; `job-release` → zehn Prüfungen. Die übrigen elf lösen allein ihren Namen aus. Der Test prüft nur Enthaltensein (R-2).
- **Fehlschlag ohne Wirkung der Mutation:** Eine Mutation, deren `sed`/`awk` nichts ändert, lässt die Prüfung grün und die Gegenprobe rot; der Test fängt das ohne eigenen `cmp`.

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| R-1 | MEDIUM | Der Kopf sagt „Jede dieser Pruefungen hat eine Gegenprobe“; für `tag-commit`, `cloudsmith-secret` und `bau-ohne-vorschau` gibt es keine (die Lücke steht nur im Bericht des Implementers, nicht in Kopf oder Grenze der Datei). `bau-ohne-vorschau` wird nur mittelbar rot, wenn die Mutation `vorschau-nur-in-vorschau` den Aufruf ändert; ein Entfernen des Schritts zeigt keine Mutation. Fehlschlag-Szenario: die `grep`-Zeile von `tag-commit` oder `cloudsmith-secret` wird mit falschem Muster geändert, der echte Workflow bleibt grün, kein Test fällt. Gemessen an meinen eigenen Mutationen funktionieren die drei Prüfungen (Schritt ersetzt → `tag-commit`; Aufruf umbenannt → `bau-ohne-vorschau`; `CLOUDSMITH` im Job → `cloudsmith-secret`), die Aussage trägt der Test aber nicht. | AGENTS.md 3.8, Skill „Aussage breiter als ihr Beleg“ | `tools/release-anlegen-selbsttest.sh` · `dieser Pruefungen hat eine Gegenprobe` | ja (Streichen der drei Prüfzeilen lässt den Selbsttest grün) | Zusage „jede Prüfung“ über die Zahl der Gegenproben |
| R-2 | LOW | Der Kopf sagt, eine Änderung „macht genau sie rot“; die Gegenprobe prüft nur, dass der Name unter den roten steht, und fünf Gegenproben machen eine zweite Prüfung rot (Liste oben). Zusage breiter als der Test; die Wirkung (die benannte Prüfung wird rot) ist belegt. | AGENTS.md 3.8 | `tools/release-anlegen-selbsttest.sh` · `macht genau sie rot (dieselbe Funktion` | ja | Zusage „genau“ über Enthaltensein |
| R-3 | LOW | Die Zelle `make skript-selbsttest` führt weiter weniger als vor dem Slice: `e2e.sh: Abbruch` wurde `e2e.sh`, `lasttest*.sh: Werte, Statistik` wurde `lasttest*.sh: Statistik`. Der erste Review ließ die gekürzten Angaben wiederherstellen; die Grenze 200 und der Bedarf für `Workflow-Text` bleiben ein Zielkonflikt, der in der Zelle zu Lasten zweier alter Angaben gelöst ist. | Maintainability, Skill „Wiederholung eines Musters“ (zweiter Review, gleiche Zelle) | `harness/README.md` · `` `e2e.sh`; `lasttest*.sh`: Statistik; `` | ja (Diff der Zelle gegen `c2c3209e`) | Zelle gekürzt statt verlagert |
| R-4 | INFO | Die Prüfung des Selbsttests ist am Host gelaufen; `make skript-selbsttest` kam aus dem Cache. Der Verifier fährt die Stage frisch (Rolle: Verifier). | Maintainability | `Dockerfile` · Stage `skript-selbsttest` | ja | Lauf aus dem Cache |
| R-5 | INFO | Der neue Satz der Vorlage ist an `v0.1.0` und `v0.1.1` gelesen wahr. Der Selbsttest sucht nur den ersten Satzteil („Für diese Version liegen keine Dateien an“), der zweite Teil ist nicht gebunden; „Pakete“ steht für `v0.1.0` weiter im Plural bei einer Zeile (Überschrift „Pakete dieser Version:“ ebenso). Ohne erwartete Aktion. | AGENTS.md 3.8 | `tools/release-notiz.vorlage` · `die Pakete dieser Version stehen oben` | ja | Notiz-Wortlaut |
| R-6 | INFO | Der Kopfkommentar nennt den Dispatch-Schritt `make release-dateien VORSCHAU=1` als Tatsache; der Dispatch-Lauf 43619591947 lief vor diesem Schritt, die Klammer „(gelesen, nicht gemessen)“ steht erst hinter dem zweiten Satzteil. Die Zusage ist die Definition des Workflows, nicht eine Messung; ob der Leser die Klammer auf beide Teile bezieht, ist offen. | AGENTS.md 3.8 | `.github/workflows/veroeffentlichung.yml` · `fuehrt `make release-dateien VORSCHAU=1` aus` | nein | Messklammer |

## Fortschreibung (zweite Fundstelle)

Grep über `docs/maintainer`, `docs/user`, `harness/README.md` und den Workflow-Kopf nach der alten Lesart „Dispatch baut nur `publish-vorschau`“: `harness/README.md` Zeile `veroeffentlichung.yml` nennt `publish-vorschau`, `release-dateien` im Dispatch (aus dem ersten Diff); `releasing.md` nennt den Schritt. Kein widersprechender Treffer.

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `tools/release-anlegen-selbsttest.sh` (Abschnitt 3) | geprüft, Befunde R-1, R-2; Funktion, `&&`-Kette, Mutationsqualität, Abwesenheitsprüfung gelesen und gefahren |
| `.github/workflows/veroeffentlichung.yml` | geprüft, R-6; Jobs, Rechte, Schritte unverändert gegenüber dem ersten Review |
| `tools/release-notiz.vorlage` | geprüft, R-5; keine Aussage „anonym“, „bytegleich“ |
| `docs/maintainer/releasing.md` | geprüft, ohne Befund; neuer Punkt unter „Ungemessen“ ist so gesetzt, wie es ist |
| `harness/README.md` | geprüft, R-3; Zelle ≤ 200 |
| `tools/release-anlegen.sh`, `Dockerfile`, `spec/`, `docs/user/`, `src/` | nicht berührt |
| Suppression, Schichtregeln, Accepted-ADR | nicht berührt |

## Summary

Kein HIGH. F-1 ist in der Sache behoben: eine Funktion, zwei Leser, die benannte Prüfung wird in jeder Gegenprobe rot, `&&` maskiert nichts. Offen: ein MEDIUM (R-1, der Kopf sagt „jede Prüfung“, drei haben keine Gegenprobe, die Lücke ist nur im Bericht benannt, nicht in der Datei), zwei LOW (R-2, R-3), drei INFO. Die drei Prüfungen ohne Gegenprobe wirken, wie meine Mutationen zeigen; es fehlt die Bindung im Test und der ehrliche Kopf. Die Nachbesserung ändert Skript und Workflow-Kommentar; dieser Report ist der Re-Review dafür. `make docs-check` Exit 0.
