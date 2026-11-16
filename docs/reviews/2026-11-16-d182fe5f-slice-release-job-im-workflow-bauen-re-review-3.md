# Re-Review-Report 3: slice-release-job-im-workflow-bauen — 2026-11-16

**Review-Art:** dritter Re-Review der Nachbesserung, in frischem Kontext; nur die Commits nach dem Report-Stand `b8a2085c`, gegen Plan, ADR und Hard Rules; nicht gegen die DoD.

**Gegenstand:** `git diff b8a2085c..d182fe5f` (ein Commit `d182fe5f`; `tools/release-anlegen-selbsttest.sh`, Abschnitt „Abwesenheit“ und Kopf; dazu der Report des zweiten Re-Reviews). Vorgänger: [Review `461aa00b`](2026-11-16-461aa00b-slice-release-job-im-workflow-bauen-review.md), [Re-Review `a1b762f7`](2026-11-16-a1b762f7-slice-release-job-im-workflow-bauen-re-review.md), [Re-Review 2 `b8a2085c`](2026-11-16-b8a2085c-slice-release-job-im-workflow-bauen-re-review-2.md) (S-1, S-2, S-3 LOW).

**Skill:** `.harness/skills/reviewer.md` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-16

**Eingangs-Kontext:** Slice [slice-release-job-im-workflow-bauen](../plan/planning/in-progress/slice-release-job-im-workflow-bauen.md); [ADR-0050](../plan/adr/0050-github-release-im-tag-lauf.md), [ADR-0024](../plan/adr/0024-lokale-stempel-zwei-klassen.md); [TDS-RB-05](../../spec/lastenheft.md#tds-rb-05--lizenz), [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit); `AGENTS.md` 3.7, 3.8.

**Selbst gefahren (nur enge Läufe, nichts nach außen):** `docker build --no-cache --target skript-selbsttest .` (Exit 0, `release-anlegen-selbsttest: gruen`, frisch); `make workflow-lint` (Exit 0); `bash tools/release-anlegen-selbsttest.sh` im Scratchpad (grün); acht Mutationen einer Kopie des Selbsttests (unten); `make docs-check` nach diesem Report. Kein `clean-build`, kein `e2e`, keine Last, kein `gh`.

## Zustand der Findings des zweiten Re-Reviews

| Finding | Stand | Beleg |
|---|---|---|
| S-1 LOW, `gegen-e2e.mk` nie rot | **behoben** | `e2e_befund` ist eine Funktion; die echte Prüfung (`! e2e_befund "$wurzel_repo/tools/mk/e2e.mk"`) und die Probe an der Kopie rufen sie beide. Gemessen: `e2e_befund` auf ein Muster `XXX` umgestellt, dann meldet der Selbsttest „die Pruefung auf e2e.mk wird an einer Kopie nicht rot“. |
| S-2 LOW, Lücke nur durch Klammer benannt | **behoben** | `gate_befund` wird von der Schleife über die echten Dateien und von vier Proben an Kopien von `release-dateien.mk` gerufen (`GATE_CHECKS += release-anlegen`, `GATE_KLASSE_werkzeug += release-dateien`, `GATE_STAGE += release-dateien=release-dateien`, `GATE_CHECKS := x release-dateien`). Der Kopf nennt Bindung und Nicht-Gebundenes (siehe T-1). |
| S-3 LOW, README-Zelle | unverändert, Hinweis, nicht Gegenstand dieses Diffs | — |

## Gemessene Mutationen (Kopie des Selbsttests, Rest des Repos unverändert)

| Mutation | Ergebnis |
|---|---|
| `gate_befund` sucht `XXX` | rot, genau die vier Gate-Proben melden „nicht rot“ |
| `e2e_befund` sucht `XXX` | rot, genau die e2e-Probe meldet |
| Alternative `GATE_KLASSE_[a-z]+` aus `gate_zeilen` entfernt | rot, nur die Probe `GATE_KLASSE_werkzeug` |
| Alternative `GATE_CHECKS` entfernt | rot, die Proben `GATE_CHECKS += …` und `GATE_CHECKS := …` |
| Alternative `GATE_STAGE` entfernt | rot, nur die Probe `GATE_STAGE` |
| Operator `(\+|:)?=` auf `(\+)?=` | rot, nur die Probe `GATE_CHECKS := …` |
| Muster `release-anlegen` aus `gate_befund` gestrichen | rot, nur die Probe `GATE_CHECKS += release-anlegen` |
| Aufruf `pruefe` in der Schleife über die echten Dateien entfernt | **grün** (siehe T-1) |

Jede der vier Gate-Proben und die e2e-Probe greift an der benannten Stelle; keine Probe ist an `grep` allein gebunden. Fehlalarme gegen die echten Dateien: keine (Basislauf grün; `release-dateien.mk` selbst nennt `release-dateien` nur in Kommentaren und Targetzeilen, nicht in einer Gate-Zeile). `set -e` und `|| true` in `gate_zeilen`, `!` mit `|| pruefe` in der e2e-Prüfung: keine Maskierung; die Funktionen stehen in `if` bzw. `||`-Position, ihr Exit bleibt der des letzten `grep`. In den echten Make-Dateien gibt es keine Gate-Zeile mit Zeilenfortsetzung, `?=` oder `::=` (Grep).

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| T-1 | INFO | Der Kopf nennt als nicht gebunden Dateien außerhalb der Verzeichnisse und Gate-Listen über Variable oder Include. Nicht genannt: die Schleife über die echten Dateien selbst (Glob, `pruefe`-Aufruf; Entfernen des `pruefe` lässt den Selbsttest grün, gemessen) sowie Gate-Zeilen mit Zeilenfortsetzung, `?=` oder `::=` (vom Muster nicht erfasst; im Repo nicht vorhanden). Die Zusage „hat Gegenproben … je eine Zeile in …“ ist wahr, die Reichweite „in keiner Gate-Liste“ am Repo trägt die Schleife, ohne dass ein Test sie belegt. Kein Fehlschlag-Szenario im jetzigen Bestand. | AGENTS.md 3.8 | `tools/release-anlegen-selbsttest.sh` · `Nicht gebunden: Dateien ausserhalb von tools/mk` | ja | Aufzählung des nicht Gebundenen unvollständig |

## Stimmen die Aussagen des Kopfes?

- „Gegenproben an Kopien, die dieselben Funktionen gate_befund und e2e_befund lesen wie die echten Dateien“: wahr (nachgelesen, mutiert).
- „je eine Zeile in GATE_CHECKS, GATE_KLASSE_*, GATE_STAGE und in e2e.mk“: wahr (vier Gate-Proben, davon zwei in `GATE_CHECKS`: `+=` und `:=`; eine e2e-Probe).
- „Nicht gebunden: Dateien ausserhalb von tools/mk, harness/mk, Makefile und repo.mk sowie eine Gate-Liste über Variable oder Include“: wahr, aber unvollständig (T-1).
- Grenze-Satz „liest Make-Dateien als Text und kennt weder Includes noch Variablen“: wahr.

## Fortschreibung (zweite Fundstelle)

Grep nach der alten Lesart „Gegenprobe nur für GATE_CHECKS“ und „für tools/mk/e2e.mk keine“ in `tools/`, `docs/`, `harness/`, `.github/`: kein Treffer außerhalb der älteren Reports (Zeitdokumente, bleiben). Der Zeiger in `tools/mk/release-dateien.mk` („tools/release-selbsttest.sh liest die Make-Dateien darauf“) betrifft den älteren Slice und die dort vorhandene Prüfung; unverändert wahr.

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `tools/release-anlegen-selbsttest.sh` (Kopf, Abschnitt Abwesenheit) | geprüft; T-1 INFO |
| `docs/reviews/…-re-review-2.md` | Zeitdokument, geprüft, ohne Befund |
| `.github/workflows/`, `harness/README.md`, `Dockerfile`, `spec/`, `docs/user/`, `src/`, `testserver/` | im Diff nicht berührt |
| Suppression, Schichtregeln, Accepted-ADR, Host statt Docker | nicht berührt |

## Summary

Kein HIGH, kein MEDIUM, kein LOW. S-1 und S-2 sind behoben und gemessen: Funktionen und Proben sind an dieselbe Prüfung gebunden, jede Entschärfung der Prüfung macht genau die benannte Probe rot. Ein INFO (T-1) zur Vollständigkeit der Aufzählung des nicht Gebundenen. `make docs-check` Exit 0 (siehe unten).
