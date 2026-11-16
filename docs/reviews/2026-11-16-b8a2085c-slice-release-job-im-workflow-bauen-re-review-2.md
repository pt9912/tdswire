# Re-Review-Report 2: slice-release-job-im-workflow-bauen — 2026-11-16

**Review-Art:** zweiter Re-Review der Nachbesserung, in frischem Kontext; nur die Commits nach dem Report-Stand `a1b762f7`, gegen Plan, ADR und Hard Rules; nicht gegen die DoD.

**Gegenstand:** `git diff a1b762f7..b8a2085c` (ein Commit `b8a2085c`; 3 Dateien: `.github/workflows/veroeffentlichung.yml`, `harness/README.md`, `tools/release-anlegen-selbsttest.sh`; dazu der Report des ersten Re-Reviews). Vorgänger: [Review `461aa00b`](2026-11-16-461aa00b-slice-release-job-im-workflow-bauen-review.md), [Re-Review `a1b762f7`](2026-11-16-a1b762f7-slice-release-job-im-workflow-bauen-re-review.md) (R-1 MEDIUM, R-2, R-3 LOW, R-4 bis R-6 INFO).

**Skill:** `.harness/skills/reviewer.md` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-16

**Eingangs-Kontext:** Slice [slice-release-job-im-workflow-bauen](../plan/planning/in-progress/slice-release-job-im-workflow-bauen.md); [ADR-0050](../plan/adr/0050-github-release-im-tag-lauf.md), [ADR-0048](../plan/adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md), [ADR-0049](../plan/adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md), [ADR-0024](../plan/adr/0024-lokale-stempel-zwei-klassen.md); [TDS-RB-05](../../spec/lastenheft.md#tds-rb-05--lizenz), [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit); `AGENTS.md` 3.7, 3.8.

**Selbst gefahren (nur enge Läufe, nichts nach außen):** `bash tools/release-anlegen-selbsttest.sh` (`gruen`, 18 s); `make --always-make skript-selbsttest` (Exit 0, aber alle Docker-Schichten `CACHED`) und deshalb `docker build --no-cache --target skript-selbsttest` (Exit 0, `release-anlegen-selbsttest: gruen`, 32 s, frisch); `make workflow-lint` (Exit 0); drei Mutationen am Selbsttest selbst (Kopie im Scratchpad, Rest des Repos verlinkt): je eine der Prüfzeilen `tag-commit`, `bau-ohne-vorschau`, `cloudsmith-secret` in `wf_pruefungen` entschärft; `make docs-check` nach diesem Report. Kein `clean-build`, kein `e2e`, keine Last, kein `gh`.

## Zustand der Findings des ersten Re-Reviews

| Finding | Stand | Beleg |
|---|---|---|
| R-1 MEDIUM, drei Prüfungen ohne Gegenprobe | **behoben** | Nachgezählt: `wf_pruefungen` setzt 21 Namen (`job-release`, `recht-einmal`, `recht-im-job`, `needs`, `bedingung-release`, `bedingung-publish`, `continue-on-error`, `persist-credentials`, `packages-write`, `cloudsmith-secret`, `tag-commit`, `bau-ohne-vorschau`, `vorschau-nur-in-vorschau`, `pruefsummen`, `token-im-anlegen`, `skript-aufruf`, `token-nur-im-anlegen`, `token-zweimal`, `vorschau-ohne-secret`, `vorschau-baut`, `sha-form`); `gegenprobe` wird mit denselben 21 Namen gerufen. Die drei neuen Mutationen ändern jeweils die benannte Stelle im Block `release` (der awk-Bereich endet vor `vorschau`, `publish` liegt davor und bleibt unberührt). Gebunden: Entschärfe ich eine der drei Prüfzeilen im Selbsttest, meldet er „die Gegenprobe <Name> macht die Pruefung nicht rot“ (alle drei gemessen). |
| R-2 LOW, „genau sie rot“ | **behoben** | Kopf nennt jetzt: „wird die benannte Pruefung rot; bei einigen wird zusaetzlich eine zweite rot (die Gegenprobe prueft nur den Namen der benannten)“; das deckt den Test (`case … *" $1 "*`). |
| R-3 LOW, README-Zelle | **teilweise** | `e2e.sh: Abbruch` und `lasttest*.sh: Werte, Statistik` stehen wieder; Zelle 197 Zeichen (nachgezählt, Grenze 200 gehalten). Dafür entfielen `(je Ziel)` an `publish.sh` und `Modi` an `test-lauf.sh`: S-3. |
| R-4 INFO, Cache | **behoben für diesen Lauf** | Stage mit `--no-cache` gefahren, grün. |
| R-5 INFO, Notiz-Satz | **behoben** | Der Fall sucht den ganzen Satz „…; die Pakete dieser Version stehen oben.“; der Selbsttest bleibt grün, der Satz steht in der Notiz. |
| R-6 INFO, Messklammer | **behoben** | Der Kopf trennt: der Dispatch-Lauf „fuehrt ausserdem `make release-dateien VORSCHAU=1` aus, ohne Token und ohne `gh` (in der CI nicht gelaufen)“; die Klammer steht am Schritt, nicht an der Stage. Ob der Lauf 43619591947 vor dem Schritt lag, ist damit wahrheitsgemäß abgedeckt. |

## Prüfung der neuen Gegenproben

- **`tag-commit`:** ersetzt die Zeile `git merge-base --is-ancestor HEAD origin/main` im Block `release` durch `true \`; `schritt_block 'Tag-Commit liegt in origin/main' | grep …` findet sie nicht mehr, die Prüfung wird rot. Das Muster der Prüfung steht an derselben Stelle wie in den Jobs `publish` und `publish-cloudsmith` (dort unberührt).
- **`bau-ohne-vorschau`:** ersetzt `run: make release-dateien` durch `run: true`; der Schritt heißt weiter `make release-dateien`, die Prüfung auf die `run:`-Zeile wird rot. Anders als `vorschau-nur-in-vorschau` (Aufruf mit `VORSCHAU=1`) belegt sie das Entfernen.
- **`cloudsmith-secret`:** fügt `CLOUDSMITH_API_KEY` im Block `release` ein; das YAML der Kopie ist nicht gültig (`env:` zweimal), die Prüfung liest Text und wird rot. Die Mutation belegt die Textprüfung, nicht den Workflow-Parser; für die Prüfung ist das die gewollte Grenze („Text, kein Lauf“, steht im Kopf).
- **Bindung:** gemessen (siehe oben), nicht nur gelesen.

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| S-1 | LOW | Die neue Zeile `grep -q -E 'release-dateien\|release-anlegen' "$tmp/gegen-e2e.mk" \|\| pruefe …` kann nie rot werden: sie sucht in einer Kopie, an die die Zeile `e2e: release-dateien` selbst angehängt wurde, mit demselben Muster wie die echte Prüfung. Sie liest die echte Prüfzeile (`! grep … "$wurzel_repo/tools/mk/e2e.mk"`) nicht; wird deren Muster falsch, bleibt alles grün. Sie sieht aus wie eine Gegenprobe, belegt aber nur `grep`. Der Kopf sagt dagegen richtig, für `tools/mk/e2e.mk` gebe es keine Gegenprobe; die Zeile im Code und der Kopf widersprechen sich. | AGENTS.md 3.7, 3.8; Skill „Eigencode, dessen Entfernung keinen Test rot macht“ | `tools/release-anlegen-selbsttest.sh` · `die Pruefung auf e2e.mk wird an einer Kopie nicht rot` | ja (Streichen der Zeile lässt den Selbsttest grün; Ändern des Musters der echten Prüfung ebenso) | Scheinbare Gegenprobe ohne Bindung an die Prüfung |
| S-2 | LOW | Der Kopf benennt die offene Lücke der Abwesenheitsprüfung nur teilweise: Die Gegenprobe belegt die Funktion `gate_zeilen` mit `GATE_CHECKS` (Muster `release-anlegen`); weder die Schleife über die Make-Dateien (Glob, `continue`, `pruefe`) noch die Alternativen `GATE_KLASSE_*` und `GATE_STAGE` des Musters von `gate_zeilen` noch das Muster `release-dateien` in einer Gate-Liste sind gebunden. „Hat eine Gegenprobe (GATE_CHECKS an einer Kopie …)“ ist wörtlich wahr, liest sich aber als Beleg für „in keiner Gate-Liste“. Der Implementer nennt die Lücke im Bericht, nicht in der Datei. | AGENTS.md 3.8 | `tools/release-anlegen-selbsttest.sh` · `hat eine Gegenprobe (GATE_CHECKS` | ja (Muster von `gate_zeilen` auf `GATE_CHECKS` kürzen: Selbsttest bleibt grün) | Zusage „Gate-Liste“ über eine Liste belegt |
| S-3 | LOW | Die README-Zelle `make skript-selbsttest` verlor für Platz `(je Ziel)` (`publish.sh`) und `Modi` (`test-lauf.sh`); beides prüft der Selbsttest weiter und das `##`-Kommentar des Targets nennt es („je Ziel“, „Argumente und Modi“). Dieselbe Zelle wurde damit zum dritten Mal gekürzt, um die Grenze von 200 zu halten; das Muster („Zelle gekürzt statt verlagert“) war in F-2 und R-3 LOW. Hier kein Fehlschlag-Szenario (die Angaben stehen am Target), deshalb nicht MEDIUM; der Architect kann das Muster als wiederholt werten. | Maintainability, Skill „Wiederholung eines Musters, das schon zweimal LOW war“ | `harness/README.md` · `` `publish.sh`; `vergleichslauf-d.sh` `` | ja (Diff der Zelle gegen `c2c3209e`) | Zelle gekürzt statt verlagert |
| S-4 | INFO | Die Gegenproben von `tag-commit`, `bau-ohne-vorschau`, `cloudsmith-secret` liefen am echten Workflow mit der Mutation allein; ob sie wie die fünf älteren zusätzlich eine zweite Prüfung rot machen, wurde nicht für alle ausgegeben. Der Kopf sagt „bei einigen“ und der Test prüft nur Enthaltensein; kein Befund. | Maintainability | `tools/release-anlegen-selbsttest.sh` · `bei einigen wird zusaetzlich eine zweite rot` | nein | — |

## Stimmen die Aussagen des neuen Kopfes?

- „Gegenproben je Pruefung der Funktion wf_pruefungen“ und „Ohne Gegenprobe ist keine der Pruefungen dieser Funktion“: 21 von 21, nachgezählt; wahr.
- „dieselbe Funktion liest den echten Workflow und die Kopien“: wahr (`echt=$(wf_pruefungen "$wf")`, `gegenprobe` ruft dieselbe Funktion).
- „die Gegenprobe prueft nur den Namen der benannten“: wahr.
- „Die Abwesenheitspruefung der Gate-Listen hat eine Gegenprobe (GATE_CHECKS …), die fuer tools/mk/e2e.mk keine“: wörtlich wahr; zur Reichweite S-1, S-2.
- Die Aufzählung der Prüfungen im ersten Kopfsatz („…kein Cloudsmith-Secret im Job; jede uses:-Zeile…“) deckt die 21 Namen; `vorschau-ohne-secret`, `token-zweimal` stehen dort sinngemäß (Token nur im Schritt, `vorschau` ohne Secret).

## Fortschreibung (zweite Fundstelle)

Grep über `.github/workflows/`, `harness/README.md`, `docs/maintainer/releasing.md`, `tools/` nach der alten Lesart „Dispatch führt nur die Stage `publish` und `release-dateien` aus“ und nach „genau sie rot“: kein widersprechender Treffer; der Workflow-Kopf und `releasing.md` nennen den Dispatch-Schritt gleich (`releasing.md` im Diff nicht berührt, Aussage dort unverändert wahr).

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `tools/release-anlegen-selbsttest.sh` | geprüft; S-1, S-2, S-4; Abschnitt 3 (`wf_pruefungen`, 21 Gegenproben) gelesen, drei Prüfzeilen mutiert, Selbsttest rot wie erwartet |
| `.github/workflows/veroeffentlichung.yml` | geprüft, ohne Befund; nur der Kopfkommentar geändert, Jobs, Rechte, Schritte unverändert |
| `harness/README.md` | geprüft; S-3; Zelle 197 Zeichen; Zeile der Datei sonst unverändert |
| `tools/release-anlegen.sh`, `tools/release-notiz.vorlage`, `Dockerfile`, `spec/`, `docs/user/`, `src/`, `testserver/` | im Diff nicht berührt |
| Suppression, Schichtregeln, Accepted-ADR, Host statt Docker | nicht berührt (der Selbsttest läuft in der Stage `skript-selbsttest`; der Host-Lauf ist ein Skript ohne Gradle/JDK) |

## Summary

Kein HIGH, kein MEDIUM. R-1 ist behoben und im Test gebunden (21 von 21 Prüfungen mit Gegenprobe, drei neue an der benannten Stelle, mutiert und rot gemessen); R-2, R-5, R-6, R-4 sind behoben; R-3 teilweise (Zelle in der Grenze, zwei andere Angaben entfielen: S-3). Neu: ein toter Check im Selbsttest, der wie eine Gegenprobe aussieht (S-1), und eine Lücke der Abwesenheitsprüfung, die der Kopf nur über die Klammer „GATE_CHECKS“ benennt (S-2). Die Nachbesserung ändert Skript und Workflow-Kommentar (Code-Fläche); dieser Report ist der Re-Review dafür. `make docs-check` Exit 0 (siehe unten).
