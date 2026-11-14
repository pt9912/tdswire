# Verifikation: slice-ci-veroeffentlichung-workflow — 2026-11-14

**Rolle:** Verifier (Frage: bauen wir es richtig, gegen Plan und DoD). **Gegenstand:** Stand 53f38738, Basis d7e558aa, Branch `impl-ci-veroeffentlichung-workflow`. **Eingang:** Slice `docs/plan/planning/in-progress/slice-ci-veroeffentlichung-workflow.md`, [ADR-0048](../plan/adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md), [ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md), Review-Report `2026-11-14-0144fb8a-slice-ci-veroeffentlichung-workflow-review.md`, `.github/workflows/veroeffentlichung.yml`, `docs/maintainer/releasing.md`. **Bezug:** [TDS-RB-05](../../spec/lastenheft.md#tds-rb-05--lizenz), [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit). **Modell:** claude-sonnet-5-5.

## Urteil

**Konform für die DoD-Punkte 1 und 2 und die Nachweis-Pflichten, soweit sie in dieser Rolle liegen; DoD-Punkt 3 bewusst offen (braucht den Nutzer), der Slice bleibt in `in-progress/`.** Keine HIGH, keine MEDIUM. 1 LOW, 3 INFO.

Geändert sind nur Workflow-Datei und Doku (kein `src/`, kein `testserver/`): nach `harness/README.md` §Minimal agent workflow kein `make clean-build`, kein `make e2e`. Beides nicht gefahren.

## Sensoren (selbst gefahren auf 53f38738, Arbeitsbaum sauber)

| Sensor | Exit | Laufzeit | Beleg |
|---|---|---|---|
| `make workflow-lint` | 0 | 2 s | drei Dateien geprüft, darunter `veroeffentlichung.yml` |
| `make skript-selbsttest` | 0 | 2 s | Ausgabe ohne Klartext (Stage aus dem Cache); Exit direkt gelesen |
| `make docs-check` | 0 | 2 s | 1458 Dateien, 0 Befunde (direkter Exit-Code, nicht über Pipe) |
| `make flaeche` | 0 | 1 s | |
| `make kopplung-zahlen` | 0 | 1 s | |
| `make gates` | 0 | 10 s | Gruppen `werkzeug`, `tests`, `stempel` übersprungen wegen Beleg; `gate-zuordnung` 33 Fälle, `stempel-eingaenge` ok |

**Stempel `werkzeug` auf 53f38738: ja.** Die Note in `refs/notes/gates` an 53f38738 trägt den Hash `bd9e3cab...`, `make gates` meldete „werkzeug uebersprungen, Beleg fuer bd9e3cab...“. Der Beleg gilt nur für diesen Eingangs-Hash (Workflow-Datei ist Eingang); die Stempel `tests` und `stempel` gelten ebenfalls. Weil die Gruppen übersprungen wurden, ist das Grün von `make gates` für `werkzeug` ein Beleg-Grün, kein frischer Lauf; die Selbsttests lief ich mit `make skript-selbsttest` direkt (Exit 0, Cache). Kein `FORCE=1` gefahren. Gesamtlaufzeit aller Sensoren etwa 20 s, größter Posten `make gates` (10 s); kein Docker-Bau von Gradle-Stages.

## Rot-Gegenprobe (Wegwerf-Kopie unter dem Scratchpad, `git archive HEAD`, Arbeitsbaum unberührt)

Je Mutation an `veroeffentlichung.yml` `make workflow-lint` in der Kopie:

| Mutation | Zusage | `workflow-lint` | Sieht er es? |
|---|---|---|---|
| `if:` von `publish` gelockert auf `github.ref_type == 'tag'` (Dispatch auf Tag-Ref würde veröffentlichen) | Dispatch veröffentlicht nie | Exit 0 | **nein** |
| `needs: gates` aus `publish` entfernt | `gates` vor `publish` | Exit 0 | **nein** |
| `packages: write` in den Job `gates` verschoben | Schreibrecht nur in `publish` | Exit 0 | **nein** |
| SHA-Pin durch `@v6` ersetzt | Actions per Commit-SHA | Exit 0 | **nein** |
| Ancestor-Prüfung umgekehrt (`origin/main HEAD`) | Tag-Commit liegt in `main` | Exit 0 | **nein** |
| `needs: nichtda` (Syntax-/Referenzfehler) | Datei gültig | Exit 2 | ja |

`make workflow-lint` sieht nur Syntax und Ausdrücke, wie [ADR-0048](../plan/adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Entscheidung 2 und die Zeile in `harness/README.md` sagen. Die fünf semantischen Zusagen trägt allein der Review (Textabgleich). Das ist kein Mangel des Diffs, aber der Stand: kein Sensor schützt diese Zusagen bei einer späteren Änderung der Datei. Die Mutationen sind einzeln und von Hand gesetzt; die Kopie ist nicht im Repo.

## DoD (Slice §2)

| Punkt | Urteil | Beleg |
|---|---|---|
| 1 Workflow-Datei | erfüllt | Trigger nur `push: tags ['v*']` und `workflow_dispatch`; Kopf `permissions: {}`; `gates` (`contents: read`, `fetch-depth: 0`, `persist-credentials: false`, derselbe SHA `d23441a4...` wie `gates.yml`, `make gates`); `publish` (`needs: gates`, `contents: read`, `packages: write` nur dort, Bedingung `event_name == 'push' && ref_type == 'tag'`, Ancestor-Schritt vor dem Token, `GITHUB_TOKEN` nur im `env:` des Schritts `make publish`, kein `--url`, kein `--info`/`--debug`); `vorschau` (nur Dispatch, `needs: gates`, `make publish-vorschau`, kein Token); `concurrency` je `github.ref`, `cancel-in-progress: false`; Zeiger auf `gates.yml` im Kopf. Lint grün (gemessen). Reihenfolge, Rechte, Pins: Textabgleich durch mich, ohne Abweichung. |
| 2 Anleitung und Sensor-Index | erfüllt | `releasing.md` Schritte 1, 8, 9 und Abschnitt Tag-Schutz `v*` (Quelle und Abrufdatum genannt, als am Repository ungemessen gekennzeichnet); `harness/README.md` Zeile in der Werkzeuge-Tabelle (kein Gate, [ADR-0048](../plan/adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md)) und Nennung im Lauf-Status; Anker `#tag-schutz-v` löst auf (`docs-check` grün). Den Abruf der GitHub-Dokumentation vom 2026-11-14 konnte ich nicht gegenprüfen (kein Netz in dieser Rolle); die Selbstkennzeichnung nach §3.8 ist vorhanden. |
| 3 Messprotokoll des Dispatch-Laufs | **offen, erwartungsgemäß** | Kein Haken gesetzt, kein Protokoll unter `docs/messungen/`. Der Slice liegt in `in-progress/`; §2 („Wer den dritten Punkt nicht liefern kann“) nennt den Ablauf: Implementer liefert 1 und 2, Review, Nutzer mergt nach `main` (Dispatch erscheint erst auf dem Standardzweig), Nutzer löst den Lauf aus und gibt die Lauf-Kennung weiter, erst dann das Protokoll; §5 macht den Lauf zum Closure-Trigger. Bestätigt. |

Nachweis-Pflichten: Sensoren grün (siehe oben); Review-Report liegt vor; Closure-Notiz (§7 „Wird bei der Closure gefüllt“), Register-Fortschreibung und Ausgang der Risiken (§6, alle „offen bis zur Closure“) sind noch nicht fällig und nicht erledigt, passend zum offenen Punkt 3. Doku-Update erfolgt.

## Konformität zu ADR-0048 (Entscheidungen)

1. Weg: Workflow bei Tag-Push, `gates` dann `publish`, `tools/publish.sh`, Stage `publish`, `publish.mk` und `gates.yml` unverändert (`git diff d7e558aa` berührt keine davon): konform.
2. Form: Trigger, `needs`, Ancestor-Prüfung (`git merge-base --is-ancestor HEAD origin/main`, Richtung richtig, bricht mit Exit 1), Rechte je Job, Token nur im `env:` des einen Schritts, keine Wiederholung (kein Retry-Schritt, `cancel-in-progress: false`), Pins: konform. Die Bedingung von `publish` ist enger als der ADR-Wortlaut (`event_name == 'push'` zusätzlich); im Slice §3 als Abweichung mit Begründung benannt, keine Lockerung. Ein Dispatch auf Tag-Ref veröffentlicht damit nicht (gelesen, siehe Grenze).
3. Kein Gate: `make publish` steht in keiner Gate-Liste (`make gates` und `gate-zuordnung` grün, Zuordnung 16 Gates unverändert): konform.
4. Vorschau: `vorschau` nur bei Dispatch, ohne Token. Konform; die Grenzen (Token-Recht, Zuordnung, Klon eines Tag-Push, Maskierung) stehen im Workflow-Kopf und in `releasing.md` Schritt 9.

## Die drei LOW des Reviews

| LOW | Behoben? |
|---|---|
| 1 Kommentare „Klon ist vollständig“, „GITHUB_ACTOR setzt der Runner“ im Indikativ | **ja** im Workflow: jetzt „gelesen, nicht gemessen“, mit fail-closed-Hinweis |
| 2 `releasing.md` „Ein Tag auf einem anderen Commit veröffentlicht nichts“ | **ja**: Schritt 8 „nach dem Text des Workflows“, „gelesen, nie gelaufen“; Schritt 9 nennt das Verhalten in der Liste des Ungemessenen |
| 3 Concurrency-Kommentar (zweiter Lauf wartet, dritter kann verdrängen) | **ja**: Bedingung im selben Satz, als aus dem Gedächtnis, nicht abgerufen gekennzeichnet |

Reine Text- und Kommentaränderung (`git diff 0144fb8a 53f38738` am Workflow: nur Kommentarzeilen; Verhalten gleich). Ein zweiter Review dazu fand nicht statt (im Slice §3 offen gesagt).

## Befunde

| Schwere | Befund | Beleg |
|---|---|---|
| LOW | `releasing.md` Schritt 9: „`GITHUB_ACTOR` setzt der Runner“ steht weiter als Tatsache, im Workflow ist derselbe Punkt (LOW 1) auf „gelesen, nicht gemessen“ gesetzt, in `releasing.md` nicht; die Liste des Ungemessenen am Schritt-Ende nennt `GITHUB_ACTOR` nicht. Aussage breiter als der Beleg (AGENTS.md §3.8). | `docs/maintainer/releasing.md` Schritt 9 |
| INFO | Verlässlichkeit des Review-Berichts, siehe unten (Reproduktion). | Lauf an 439dbec7 |
| INFO | Der Review-Report wurde im Commit 53f38738 vom Implementer geändert (nur Links der Kennungen, kein Befund und kein Urteil geändert; `git diff --word-diff 439dbec7 53f38738`). Das ist sachlich folgenlos, ändert aber ein Reviewer-Artefakt; die Zeile „`make docs-check` Exit 0 (1457 Dateien)“ im Report blieb stehen. | `docs/reviews/...0144fb8a...review.md` |
| INFO | Die Zusagen Reihenfolge, Rechte, Bedingung, Pins und Ancestor-Richtung hat nach der Gegenprobe kein Sensor; nur der Review. Kein Mangel des Diffs ([ADR-0048](../plan/adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Entscheidung 2); jede künftige Änderung der Datei trägt dasselbe Risiko. Die Ancestor-Prüfung steht zudem in der Datei, die der Tag-Push selbst mitbringt (Hinweis INFO 1 des Reviews); tragend ist der Tag-Schutz, den der Nutzer setzt. | Gegenprobe oben |

## Verlässlichkeit des Review-Berichts: `docs-check` rot gemeldet, Review sagt Exit 0

Der Implementer meldete 11 Befunde `id-unlinked` im Review-Report vor seinem Eingriff; der Review nannte `make docs-check` Exit 0.

**Reproduziert:** `git archive 439dbec7` (Commit des Review-Reports, vor dem Eingriff) in eine Wegwerf-Kopie, `make docs-check`: **Exit 2, 1458 Dateien, 11 Befunde `id-unlinked`**, alle in der Review-Datei (Zeilen 3, 9, 21, 24, 31, 36). Auf 53f38738 (Kennungen verlinkt): Exit 0, 1458 Dateien, 0 Befunde. Der Befund des Implementers stimmt.

**Ursache, soweit belegbar:** Der Review nannte „1457 Dateien“, der Stand mit dem Report hat 1458. Der Review-Lauf von `docs-check` fand also vor dem Anlegen des Reports statt und konnte den Report selbst nicht erfassen. Die Aussage „Exit 0“ war für den geprüften Gegenstand (0144fb8a) richtig, trug aber nicht für den Stand, den der Report-Commit erzeugte. Warum der Review nach dem Schreiben nicht erneut fuhr, ist nicht belegbar; die Datei nennt es nicht. Ein Befund zur Verlässlichkeit: ein Review-Bericht, der unter `docs/reviews/` liegt, ist selbst Eingang von `docs-check` und macht dessen Zahl „Exit 0“ stale, sobald er geschrieben ist. Die Lücke ist ein Ablauf-Befund, kein Urteil über den Inhalt des Reviews: dessen inhaltliche Befunde habe ich nachgeprüft (Text, Rechte, Bedingung, Pins, Ancestor-Richtung) und bestätigt gefunden. Der Review-Report wäre ohne den Eingriff den Gates rot geblieben (`make gates` fährt `docs-check`); dieser Bericht ist nach demselben Muster vor dem Commit mit `make docs-check` geprüft (siehe unten).

## Geprüft, ohne Befund

- `AGENTS.md` §3.1 (kein Host-Gradle): nur `make`/`git`. §3.5: keine ADR geändert. §3.6: keine Schwelle gesenkt. §3.7/§3.8 außer dem LOW: Kommentare im Workflow tragen Zustand und Grenzen.
- Keine Berührung von `src/`, `testserver/`, `tools/`, `spec/`.

## Nicht geprüft / Grenzen

Verhalten auf dem Runner (Klon eines Tag-Push, `origin/main`, `GITHUB_ACTOR`, Token-Recht, Maskierung, Concurrency-Verhalten) ist nur gelesen. GitHub-Doku nicht abgerufen. Kein `make clean-build`, kein `make e2e` (nicht erforderlich). Keine Last erzeugt, kein Token, keine Registry, kein Push.
