# Review-Report: slice-cloudsmith-ziel-bauen (zweiter Re-Review der Nachbesserung) — 2026-11-15

**Review-Art:** Code (Re-Review nach Reviewer-Skill „Nachbesserung mit Code ohne zweiten Review“): nur die Nachbesserung, Diff `9a9a681d..f25a9e9f`, gegen die Findings N-1 bis N-4 des Reports `docs/reviews/2026-11-15-9a9a681d-slice-cloudsmith-ziel-bauen-re-review.md` (Branch `worktree-agent-ad27d50069e3391c2`), [ADR-0049](../plan/adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md), [ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md) und Hard Rules (`AGENTS.md` §3.7, §3.8)

**Gegenstand:** Commit f25a9e9f: `tools/publish-selbsttest.sh` (+38), `tools/publish-probe.sh`, `docs/messungen/maven-paket-2026-11-14.md` und der Report des vorigen Re-Reviews. `.github/workflows/veroeffentlichung.yml`, `build.gradle.kts`, `tools/publish.sh` und `tools/mk/publish.mk` sind zwischen 9a9a681d und f25a9e9f unverändert (Diff über diese vier Pfade: 0 Zeilen, gefahren).

**Skill:** `.harness/skills/reviewer.md` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-15

**Eingangs-Kontext:** Report des vorigen Re-Reviews; [TDS-RB-05](../../spec/lastenheft.md#tds-rb-05--lizenz), [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit); [ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md), [ADR-0048](../plan/adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md), [ADR-0049](../plan/adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md); `AGENTS.md` §3.

**Nicht gefahren:** `make gates`, `make e2e`, `make clean-build`, `tools/publish-probe.sh` (alle Modi: Docker-Bau, Gradle-Lauf), der Selbsttest als Ganzes. Die Umgebung dieses Laufs wies jeden Aufruf von `bash tools/publish-selbsttest.sh` ab, weil das Skript Wegwerf-Repos mit `git` anlegt. Gefahren wurde stattdessen ein Auszug (siehe unten), der die Zeilen 26–27 (`job_block`, `schritt_block`) und 250–266 (Workflow-Prüfung) wörtlich übernimmt, gegen Kopien des Workflows, und die Make-Prüfung (Zeilen `mk_c`, `mk_g`, drei `case`) gegen Kopien von `tools/mk/publish.mk`. Was das Gesamtskript außerhalb dieser Zeilen tut, ist hier nicht gefahren.

---

## Behebung der Findings des vorigen Re-Reviews

| ID (alt) | Urteil | Beleg |
|---|---|---|
| N-1 | behoben für Make-Target, Schritt, Job, Secret und `packages`/`contents` der beiden Veröffentlichungs-Jobs; Rest als N2-1 (LOW) | `job_block` endet an der nächsten Zeile mit zwei Leerzeichen und `[a-z#]`, `schritt_block` am nächsten Schritt mit sechs Leerzeichen und `- `; die Secrets-Zeile wird mit `grep -qx` im Schrittblock gelesen, nicht mehr in der Datei. Mutationen siehe Tabelle: alle vier Fälle aus N-1 (M-8 bis M-11) sind rot. |
| N-2 | behoben | Der Kopf von `tools/publish-probe.sh` führt kein Ergebnis mehr („die Ergebnisse stehen in der Ausgabe der Läufe, nicht in diesem Kommentar“); die Art `pom-ohne-schutz` macht die Registry-Aussage wiederholbar. Rest als N2-3 (INFO). |
| N-3 | behoben im Messprotokoll; Rest im Skript als N2-2 (LOW) | Die vier Stellen der alten Lesart stehen mit Bedingung („ohne `gradleMetadata()`, liest die `.module` trotzdem“) in Aufbau-Absatz, Tabellenkopf, M3-Überschrift, M3-Text und „Nicht gemessen“. Diff der Tabellenzeilen M1.1 bis M1.2 und aller Zahlen: unverändert (nur Wortlaut der Kopfzeile). |
| N-4 | behoben | Die Zeile „Ausgabe des Modus: `Could not parse module metadata tdswire-0.1.0-SNAPSHOT.module`“ im Messprotokoll entspricht dem, was `grep … \| sed 's#/root/.m2/[^ ]*/##'` aus einer Gradle-Meldung mit dem Pfad macht; der Lauf des Modus ist hier nicht wiederholt (siehe N2-3). |

## Mutationen am Workflow-Teil des Selbsttests (Auszug, je Mutation eine Kopie des Workflows)

Grundzustand: grün. Rot heißt: mindestens eine Meldung `FEHLER`.

| Nr. | Mutation | Ergebnis |
|---|---|---|
| M-8 | Schritt `make publish-cloudsmith` ruft `run: make publish` | rot („ruft nicht make publish-cloudsmith“) |
| M-9 | Schritt `make publish` ruft `make publish-cloudsmith` | rot |
| M-10 | `GITHUB_TOKEN: ${{ secrets.OTHER }}` | rot |
| M-11 | `CLOUDSMITH_API_KEY` im `env:` des Schritts im Job `publish` | rot („der Job publish nennt CLOUDSMITH_*“) |
| M-12 | `packages: write` im Job `publish-cloudsmith` | rot |
| M-13 | `contents: write` statt `read` im Job `publish-cloudsmith` | rot |
| M-14 | `GITHUB_TOKEN` im Schritt des Jobs `publish-cloudsmith` | rot |
| E-1 | `CLOUDSMITH_API_KEY` im `env:` des anderen Schritts (Tag-Commit) desselben Cloudsmith-Jobs, der Schritt `make publish-cloudsmith` bleibt vollständig | rot („ausserhalb des Schritts“) |
| E-2 | Schritt `make publish-cloudsmith` umbenannt | rot („Job … oder sein Schritt … fehlt“) |
| E-3 | Schritt `make publish` umbenannt | rot |
| E-4 | Kommentarzeile `# CLOUDSMITH_API_KEY: …` im `env:` und falscher Wert | rot (der Wert, nicht der Kommentar, wird gelesen) |
| E-5 | `run: make publish-cloudsmith && make publish` | rot (`-x`, ganze Zeile) |
| E-6 | `env:` als Flow-Mapping mit Anführungszeichen | rot (Form nicht erkannt, sichere Richtung) |
| E-7 | YAML-Anker `&u` im Secrets-Wert | rot (sichere Richtung) |
| E-8 | Jobname `publish: &x` | rot (sichere Richtung) |
| E-9 | `GITHUB_TOKEN` zusätzlich im Schritt „Tag-Commit liegt in origin/main“ des Jobs `publish` | **grün** (N2-1) |
| E-10 | `CLOUDSMITH_API_KEY` im Schritt des Jobs `vorschau` | **grün** (N2-1) |
| E-11 | `CLOUDSMITH_API_KEY` im Schritt des Jobs `gates` | **grün** (N2-1) |
| E-12 | `env:` auf Workflow-Ebene mit `CLOUDSMITH_API_KEY` | **grün** (N2-1) |
| E-13 | `id-token: write` zusätzlich im Job `publish-cloudsmith` | **grün** (N2-1, nur `packages` und `contents` gebunden) |
| E-14 | zweiter Schritt `make publish` im Job `publish-cloudsmith` | grün (ohne Wirkung auf die Zusage: der Job hat kein `packages: write`) |
| E-15 | Kommentarzeile mit zwei Leerzeichen Einrückung innerhalb des Cloudsmith-Jobs | rot mit irreführender Meldung („Job … fehlt“); sichere Richtung (N2-4) |
| E-16 | Kommentarzeile `# CLOUDSMITH_API_KEY …` zwischen zwei Schritten des Cloudsmith-Jobs | rot („ausserhalb des Schritts“); sichere Richtung (N2-4) |

Make-Prüfung, je Mutation eine Kopie von `tools/mk/publish.mk`: `publish-cloudsmith` ruft `--ziel github` (rot), `--ziel cloudsmith` gestrichen (rot), `publish` ruft `--ziel cloudsmith` (rot), Kommentarzeile zwischen Target und Rezept (rot, sichere Richtung); Grundzustand grün.

Die Parser lesen nicht falsch-grün für die Zuordnung, die N-1 benannte: Job-Grenze und Schritt-Grenze folgen den Einrückungen, und ein Name in einem Kommentar oder einem anderen Schritt trägt keine Zeile in den gelesenen Block. Falsch-rot tritt nur bei Formen auf, die die Datei nicht verwendet (Flow-Mapping, Anker, Zwei-Leerzeichen-Kommentar im Job).

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| N2-1 | LOW | Der Kommentar vor der Workflow-Prüfung sagt „Rechte und Secrets je Job“ und die Meldung „Secrets und Rechte je Ziel gegen den Workflow“. Gebunden sind nur die Jobs `publish` und `publish-cloudsmith`, und `GITHUB_TOKEN` ist nur als Pflicht im Schritt gebunden, nicht als Ausschluss in den anderen Schritten desselben Jobs (die Cloudsmith-Werte sind es, per Zählung). Die Mutationen E-9 bis E-13 bleiben grün: Cloudsmith-Secret im Job `gates` oder `vorschau` oder auf Workflow-Ebene, `GITHUB_TOKEN` in einem zweiten Schritt, ein weiteres Recht neben `contents` und `packages` im Cloudsmith-Job. Failure-Szenario: ein Cloudsmith-Schlüssel steht im Job `gates`, der `make gates` ausführt; der Selbsttest bleibt grün, die Zusage „nur im env: des Schritts“ ([ADR-0049](../plan/adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) Entscheidung 3a) hält allein der Review. Die Grenze steht weder im Kopf des Selbsttests noch im Kommentar. | `AGENTS.md` §3.8 (Kommentar breiter als der Beleg), Reviewer-Skill („Zuordnung … ohne Sensor“) | `tools/publish-selbsttest.sh` · `Rechte und Secrets je Job` | ja (E-9 bis E-13) | Sensor bindet enger als sein Kommentar |
| N2-2 | LOW | Die Ausgabe-Beschriftungen der Messung, deren Spalte das Messprotokoll jetzt einschränkt, lauten im Skript weiter `lauf "nur POM" -PnurPom` und `lauf "nur POM, Bibliothek ohne transitive Abhaengigkeiten, …"`. Der Konsument liest die `.module` dabei trotzdem (Modus `marker`). Wer den Lauf wiederholt, liest „nur POM“ ohne die Bedingung, die das Protokoll an derselben Spalte nennt. | Reviewer-Skill („Zusage ohne ihre gemessene Bedingung im selben Satz“) | `tools/publish-probe.sh` · `lauf "nur POM" -PnurPom` | ja (Grep) | Bedingung in anderem Absatz |
| N2-3 | INFO | (a) Der Satz „`gradleMetadata()` wegzulassen reicht nicht“ steht im Registry-Konsumenten als Aussage; das Messprotokoll führt keine Registry-Zeile, der Kopf des Skripts verweist auf die Ausgabe der Läufe. Die Art `pom-ohne-schutz` macht die Aussage wiederholbar; diesen Lauf habe ich nicht gefahren. (b) Der Modus `marker` gibt nach der Änderung nur noch die Zeile `Could not parse module metadata` aus; ein roter Erstlauf aus anderem Grund zeigt dann nur `Exit N`, wie der erwartete rote Lauf. Der zweite Lauf (grün mit `ignoreGradleMetadataRedirection()`) trennt beides, weil dieselbe Konfiguration sonst nicht grün würde. (c) Der Kopf sagt „mit dem Pfad der `.module`“, die Ausgabe nennt nach dem `sed` nur den Dateinamen. | `AGENTS.md` §3.8 | `tools/publish-probe.sh` · `gradleMetadata() wegzulassen reicht nicht` | nein (Lauf nötig) | Messaussage ohne Protokollzeile |
| N2-4 | INFO | Die Blockgrenze von `job_block` endet an jeder Zeile mit zwei Leerzeichen und `[a-z#]`: ein Kommentar auf Jobebene im Job (zwei Leerzeichen) lässt den Test mit „Job … fehlt“ rot werden; ein Kommentar im Job, der `CLOUDSMITH_` oder `GITHUB_TOKEN` nennt, außerhalb des Schritts, ebenso. Sichere Richtung, die Meldung nennt aber nicht die Ursache. | Maintainability | `tools/publish-selbsttest.sh` · `job_block() { awk` | ja (E-15, E-16) | Parser bindet an Form |

## Prüfung auf neue Mängel der Nachbesserung

- **Zahlen unverändert (N-3):** Diff von `docs/messungen/maven-paket-2026-11-14.md`: Änderungen nur an Kopfzeile, Absatztext, M3-Überschrift und -Text und dem Punkt „Nicht gemessen“; keine Zahl, kein Exit, keine Tabellenzeile M1.1 bis M1.x berührt. Die Bedingung steht an allen Stellen des Protokolls, die „nur POM“ nennen (Grep über `docs/`, `spec/`, `tools/`, `harness/` nach `nur POM`, `nur über die POM`, `ohne .module`); Treffer in `docs/plan/planning/done/slice-maven-erste-veroeffentlichung.md` (Zeile zur `.module`-Prüfung) sagen nichts über die Spalte. Rest im Skript: N2-2.
- **Modus-Kopf (N-2/N-4):** Der Kopf trägt kein Messergebnis; die Aussage „Gradle liest die `.module` aus `mavenLocal` ohne `gradleMetadata()`“ und der Wortlaut stehen als Einzellauf mit Datum im Messprotokoll und im Kommentar am Modus `marker`. Der Wortlaut ist von mir nicht nachgemessen (siehe N2-3).
- **Art `pom-ohne-schutz`:** die Konsumenten-Konfiguration (`if (!nurPom) gradleMetadata() else if (!ohneSchutz) ignoreGradleMetadataRedirection()`) ist syntaktisch gültiges Kotlin; die Art `pom` bleibt, was sie war (`-PnurPom`); die Schleife in `registry` und die Auswertung am Dummy ändern sich nur um einen Durchlauf. Nicht gefahren.
- **Kommentare (`AGENTS.md` §3.7):** die neuen Kommentare im Selbsttest (Kopplung: `job_block`/`schritt_block` an die Einrückungen der Datei; Zuordnung Ziel, Make-Target, Job, Schritt) und im Probe-Skript sind Indikativ, tragen Kopplung oder Grenze; kein Konjunktiv über verworfene Alternativen, keine Chronik. Ausnahme der Reichweite: N2-1.
- **Host statt Docker:** keine neuen Aufrufe; `publish-selbsttest.sh` bleibt in der Stage `skript-selbsttest` (Eingänge `tools/mk`, `build.gradle.kts` und `.github/workflows` liegen im `COPY`).
- **Bash-Konstrukte:** `<<<` in einem Skript mit `#!/bin/bash`, vom Dockerfile mit `bash` aufgerufen; kein Befund.
- **Secrets:** der neue Code liest keine Zugangsdaten; kein Wert eines Tokens in Diff oder Report.
- **Haken vor der Verifikation:** kein `- [x]` im Diff.

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `.github/workflows/veroeffentlichung.yml`, `build.gradle.kts`, `tools/publish.sh`, `tools/mk/publish.mk` | geprüft, ohne Befund (Diff `9a9a681d..f25a9e9f` leer, 0 Zeilen) |
| `docs/messungen/maven-paket-2026-11-14.md` | geprüft, ohne Befund (Zahlen unverändert, Bedingung an den Stellen) |
| `tools/publish-selbsttest.sh` | geprüft; Befunde N2-1, N2-4; Zuordnung aus N-1 gebunden |
| `tools/publish-probe.sh` | geprüft; Befunde N2-2, N2-3 |
| `docs/user/`, `spec/`, `src/`, `testserver/`, `Dockerfile`, `harness/README.md` | geprüft, ohne Befund (nicht im Diff) |
| Suppression, Host-statt-Docker, Schichtregel, Accepted-ADR geändert | geprüft, ohne Befund |
| HIGH und MEDIUM | keine; kein Rollen-Widerspruch, kein Konflikt-Pfad nötig |

## Gates

`make docs-check` Exit 0 (1479 Dateien, 0 Befunde, mit diesem Report). Gefahren: Auszug der Workflow-Prüfung gegen 23 Mutationen, Make-Prüfung gegen 5. Nicht gefahren: `make gates`, `make flaeche`, `make kopplung-zahlen`, `make skript-selbsttest`, `tools/publish-probe.sh`.

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 0 |
| LOW | 2 |
| INFO | 2 |

## Fazit

N-1 bis N-4 sind in ihrem Wortlaut behoben; die Blockparser binden Make-Target, Schritt, Job, Secret und Rechte der beiden Veröffentlichungs-Jobs an die Stelle und lesen für die Formen der Datei weder falsch-grün noch falsch-rot. Offen sind zwei LOW (die Reichweite des Sensors gegenüber seinem Kommentar, ein Etikett im Probe-Skript) und zwei INFO. Kein Finding ab MEDIUM: ein weiterer Review-Kreis ist nicht ausgelöst.
