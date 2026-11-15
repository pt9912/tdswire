# Review-Report: slice-cloudsmith-ziel-bauen (Re-Review der Nachbesserung) — 2026-11-15

**Review-Art:** Code (Re-Review nach Reviewer-Skill „Nachbesserung mit Code ohne zweiten Review“): nur die Nachbesserung, Diff `2b62d0a9..9a9a681d`, gegen den Report `2026-11-15-2b62d0a9-slice-cloudsmith-ziel-bauen-review.md` (F-1 bis F-6), [ADR-0049](../plan/adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md), [ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md) und Hard Rules (`AGENTS.md` §3.7, §3.8)

**Gegenstand:** Commit 9a9a681d (6 Dateien, +168/-7): `tools/publish-selbsttest.sh`, `tools/publish-probe.sh`, `docs/messungen/maven-paket-2026-11-14.md`, `docs/maintainer/releasing.md`, `docs/plan/planning/in-progress/slice-cloudsmith-ziel-bauen.md`, der Report des ersten Reviews. Die Dateien `.github/workflows/veroeffentlichung.yml`, `build.gradle.kts`, `tools/publish.sh`, `tools/mk/publish.mk`, `Dockerfile` und `harness/README.md` sind zwischen 2b62d0a9 und 9a9a681d unverändert (leerer Diff); der Workflow ist damit gegenüber dem ersten Review unverändert.

**Skill:** `.harness/skills/reviewer.md` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-15

**Eingangs-Kontext:** Report des ersten Reviews (Commit 2b62d0a9); Slice `slice-cloudsmith-ziel-bauen` §3; [TDS-RB-05](../../spec/lastenheft.md#tds-rb-05--lizenz), [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit); [ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md), [ADR-0048](../plan/adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md), [ADR-0049](../plan/adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md); `AGENTS.md` §3.

**Nicht gefahren:** `tools/publish-probe.sh` (alle Modi, also auch `marker`; Docker-Bau und Gradle-Lauf), `make gates`, `make e2e`, `make clean-build`. Die Messung des Modus `marker` ist gelesen, nicht wiederholt (siehe F-2 unten).

---

## Behebung der Findings des ersten Reviews

| ID (alt) | Urteil | Beleg |
|---|---|---|
| F-1 | behoben für die benannten Zuordnungen; eine Nachbarlücke bleibt (N-1) | Der Selbsttest liest jetzt die Umgebungsnamen (Benutzer, Schlüssel) und die URL-Property je Ziel aus `publish.sh` (`benutzer_var`, `geheim_var`, `gradle_property`) gegen den Repository-Block von `build.gradle.kts`, und die Namen `CLOUDSMITH_*` gegen den Workflow (`<NAME>: ${{ secrets.<NAME> }}`). Gefahren an einer Kopie (Skript je Mutation, Exit 1 = rot): siehe Tabelle Mutationen. Alle sieben Mutationen aus dem Umfeld von F-1 sind rot; der Selbsttest bleibt am unveränderten Stand grün (Exit 0). |
| F-2 | eingeschränkt, nicht nachgemessen an der Registry-Seite; Aussage trägt für `mavenLocal` (siehe F-2 unten, N-2, N-3) | Messprotokoll bekommt einen Absatz „Grenze der Spalte“; der Modus `marker` liegt im Skript; der Kopf des Skripts und der Kommentar im Registry-Konsumenten sind angepasst. |
| F-3 | behoben | `releasing.md` Schritt 8 nennt „die Jobs `publish` und `publish-cloudsmith`“. Grep über `docs/maintainer`, `docs/user`, `harness/README.md`, `tools`, Workflow nach „Job `publish`“: weitere Treffer stehen nur in zwei Messprotokollen vom 2026-11-15 (Dispatch- und Tag-Lauf) und beschreiben den damaligen Lauf, in dem es nur diesen Job gab; sie bleiben mit Grund (Zeitdokument). |
| F-4 | behoben | Kopf von `tools/publish-probe.sh` nennt „mit gradleMetadata() und ohne es in den Quellen; die `.module` liest Gradle auch dann, siehe `marker`“. |
| F-5 | behoben | Die Meldung nennt „acht rote Faelle“; gezählt im Skript: `c-snapshot`, `c-tag-fehlt`, `c-tag-alt`, `c-baum`, `c-schluessel-fehlt`, `c-benutzer-fehlt`, `c-fremdes-ziel`, `c-platzhalter-leer` = 8. |
| F-6 | behoben | Slice §3 führt `tools/mk/skript-selbsttest.mk` mit der Wirkung „Hilfezeile“. |

### Mutationen am Selbsttest (Kopie von `tools/`, `build.gradle.kts`, Workflow; je eine Mutation, danach Rückkopie)

| Nr. | Mutation | Ergebnis |
|---|---|---|
| M-1 | `build.gradle.kts`: `getenv("CLOUDSMITH_USERNAME")` → `getenv("CLOUDSMITH_USER")` | rot (Exit 1, „Benutzer-Umgebung“) |
| M-2 | `build.gradle.kts`: `getenv("CLOUDSMITH_API_KEY")` → `getenv("GITHUB_TOKEN")` | rot („Schluessel-Umgebung“) |
| M-3 | `build.gradle.kts`: Property `tdswireCloudsmithUrl` → `tdswireCloudsmithUrlX` | rot („URL-Property“) |
| M-4 | `build.gradle.kts`: `getenv("GITHUB_ACTOR")` → `getenv("GITHUB_USER")` | rot („Benutzer-Umgebung“) |
| M-5 | Workflow: `CLOUDSMITH_API_KEY: ${{ secrets.CLOUDSMITH_TOKEN }}` | rot („Workflow: … CLOUDSMITH_API_KEY“) |
| M-6 | Workflow: Zeile `CLOUDSMITH_USERNAME:` gelöscht | rot („Workflow: … CLOUDSMITH_USERNAME“) |
| M-7 | `tools/publish.sh`: `geheim_var=CLOUDSMITH_API_KEY` → `CLOUDSMITH_KEY` | rot (zwölf Meldungen, darunter „Schluessel-Umgebung“ und „Workflow“) |
| M-8 (eigene Wahl) | `tools/mk/publish.mk`: `--ziel cloudsmith` → `--ziel github` | **grün** (Exit 0) |
| M-9 (eigene Wahl) | Workflow: Schritt des Jobs `publish-cloudsmith` `run: make publish-cloudsmith` → `run: make publish` | **grün** (Exit 0) |
| M-10 (eigene Wahl) | Workflow: `GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}` → `${{ secrets.GH_PAT }}` | **grün** (Exit 0) |
| M-11 (eigene Wahl) | Workflow: die zwei `CLOUDSMITH_*`-Zeilen aus dem Schritt `make publish-cloudsmith` entfernt und mit gleichem Wortlaut in den Schritt `make publish` (Job `publish`, GitHub) gesetzt | **grün** (Exit 0) |

`make skript-selbsttest` Exit 0 (Stage `skript-selbsttest` im Docker-Bau; die Stage kopiert `build.gradle.kts` und `.github/workflows`, die neuen Prüfungen finden ihre Eingänge). `bash tools/publish-selbsttest.sh` am Stand Exit 0.

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| N-1 | MEDIUM | Der neue Sensor bindet nur die Namen an die Stelle, nicht die Stelle an das Ziel: M-8 (das Make-Target `publish-cloudsmith` ruft `--ziel github`), M-9 (der Cloudsmith-Job ruft `make publish`), M-10 (Name des GitHub-Secrets) und M-11 (die Cloudsmith-Secrets stehen im Schritt des GitHub-Jobs statt im Cloudsmith-Schritt) lassen den Selbsttest grün. Die Workflow-Prüfung ist ein `grep` über die ganze Datei; die Meldung „der Schritt make publish-cloudsmith setzt … nicht“ sagt mehr, als die Prüfung bindet. Failure-Szenario: der einzige echte Lauf veröffentlicht in M-8/M-9 ein zweites Mal nach GitHub Packages (Duplikat, Fehler) und nie nach Cloudsmith, in M-11 stehen die Cloudsmith-Secrets im Job mit `packages: write` und der Cloudsmith-Schritt läuft ohne Zugangsdaten; die Version ist danach auf GitHub verbraucht ([ADR-0049](../plan/adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) Teilfehler). F-1 in seinem Wortlaut (Umgebungs- und Property-Namen) ist behoben; dies ist die Nachbarzuordnung derselben Klasse, vom Implementer nicht benannt, in der Closure-Notiz nicht als Grenze geführt. | Reviewer-Skill („Zahl, Feldmenge oder Zuordnung steht an einem zweiten Ort ohne Sensor“), `AGENTS.md` §3.8 (Meldung breiter als der Beleg) | `tools/publish-selbsttest.sh` · `der Schritt make publish-cloudsmith setzt $n nicht aus secrets.$n` | ja (Mutationen M-8 bis M-11, gefahren) | Zuordnung an zweitem Ort ohne Sensor |
| N-2 | LOW | Die Aussage „gemessen (Dummy-Server, Anfragen am Server) fragt Gradle die `.module` ohne diese Zeile auch ohne gradleMetadata() an“ (Kopf von `tools/publish-probe.sh`, Registry-Konsument) beschreibt einen Lauf des früheren Zustands von `registry`; im Skript gibt es keinen Modus, der den Registry-Konsumenten ohne `ignoreGradleMetadataRedirection()` fährt, und kein Messprotokoll führt ihn. Der Kommentar „(am Dummy-Server gemessen)“ im Registry-Konsumenten gilt ebenso. Der Satz ist als Einzellauf gekennzeichnet und sagt, der Grund (Marker in der POM) sei nicht gemessen; die Messung ist aber im Repo nicht wiederholbar. | `AGENTS.md` §3.8 | `tools/publish-probe.sh` · `gradleMetadata() wegzulassen reicht nicht` | nein (Lauf nötig; kein Modus) | Messaussage ohne Erzeuger im Repo |
| N-3 | LOW | In `docs/messungen/maven-paket-2026-11-14.md` stehen außerhalb des neuen Absatzes „Grenze der Spalte“ weiter die alten Lesarten: der Aufbau-Absatz („einmal nur über die POM“), die Tabellenüberschrift „Konsument nur über die POM“, M3 („einmal ohne sie (nur POM)“) und der Punkt „Nicht gemessen“ zur `.module` („M1.1 liest sie“). Die Grenze steht in einem eigenen Absatz oberhalb der Tabelle, nicht an den Stellen. | Reviewer-Skill („Zusage ohne ihre gemessene Bedingung im selben Satz“, hier LOW, weil der Absatz im selben Dokument steht und die Spalte ausdrücklich einschränkt) | `docs/messungen/maven-paket-2026-11-14.md` · `einmal ohne sie (nur POM)` | ja (Grep) | Bedingung in anderem Absatz |
| N-4 | INFO | Die Messung des Modus `marker` ist im Skript nachvollziehbar gebaut: `.module` der veröffentlichten Version durch Müll ersetzt; Konsument mit `mavenLocal`, `mavenPom()`, `artifact()` und ohne `gradleMetadata()` (gleiche Quellen wie `konsument` mit `-PnurPom`); zweiter Lauf mit `ignoreGradleMetadataRedirection()`; Scope `api` (Standard von `build.gradle.kts`), Version `0.1.0-SNAPSHOT` wie die Zeile `version =`. Rot im ersten und grün im zweiten Lauf trägt die Aussage „Gradle liest die `.module` aus `mavenLocal` ohne gradleMetadata()“ als Einzellauf. Der Wortlaut „Could not parse module metadata“ im Messprotokoll steht in keinem Artefakt des Repos; das Skript gibt nur die ersten vier passenden Zeilen aus und wertet am Exit aus. Dieser Review hat den Modus nicht gefahren. | `AGENTS.md` §3.8 | `tools/publish-probe.sh` · `mavenLocal ohne gradleMetadata(), .module durch Muell ersetzt` | ja (Lauf des Modus `marker`) | — |

## Bewertung des Hinweises in `docs/messungen/maven-paket-2026-11-14.md`

Der Absatz „Grenze der Spalte ‚nur über die POM‘“ ist die richtige Form von F-2: Er schränkt ein („ohne `gradleMetadata()` in den Quellen“, nicht „ohne `.module`“), nennt Datum, Skriptmodus und Einzellauf und lässt die Zahlen der Tabelle, wie gemessen. Die Aussage trägt nur so weit wie der Beleg: sie gilt für `mavenLocal` und diesen Konsumenten (Gradle, Kotlin 2.4.10), nennt den Grund (Marker in der POM) im Messprotokoll nicht und behauptet nichts über die Registry. Die Wirkung auf die Entscheidung zu M1 (Scope `api`) bleibt gering: in der Spalte „nur POM“ wurde die `.module` mitgelesen, aber M1.2 ist in beiden Spalten rot und M1.1 in beiden grün, und die POM trägt denselben Eintrag. Offene Punkte: die alten Lesarten an den Folgestellen (N-3) und der nicht wiederholbare Teil für die Registry (N-2). Eine Messzeile mit der Fehlermeldung im Wortlaut fehlt im Repo (N-4).

## Prüfung auf neue Mängel der Nachbesserung

- **Selbsttest zu eng gekoppelt?** Die Extraktion liest feste Formen (`geheim_var=…` am Zeilenende, `gradle_property=… ;;`, `username = System.getenv`, `gradleProperty("…")` im Block nach `name = "registry|cloudsmith"`). Ändert sich die Form, endet der Test mit „unterscheiden sich [leer]“, nicht grün (Prüfung auf nichtleer vorhanden). Das ist gewollt und kein Befund.
- **Zu weit?** Nein, im Gegenteil: siehe N-1 (zu lose an die Stelle gebunden).
- **Kommentare (`AGENTS.md` §3.7):** Die neuen Kommentare im Selbsttest und im Probe-Skript sind Indikativ und tragen Kopplung/Grenze. Ausnahmen: der Kommentar der Namensprüfung sagt „die Namen der Umgebung“ im Workflow, geprüft werden nur die `CLOUDSMITH_*` (N-1); die Messaussage im Probe-Kopf siehe N-2. Kein Konjunktiv über verworfene Alternativen, keine Chronik („früher stand hier“) im Diff.
- **Workflow unverändert gegenüber dem ersten Review:** ja (leerer Diff `2b62d0a9..9a9a681d` für `.github/workflows/veroeffentlichung.yml`); die Prüfpunkte W-1 bis W-9 des ersten Reports gelten unverändert. `make workflow-lint` Exit 0.
- **Haken vor der Verifikation:** der Diff im Slice §3 ist eine Tabellenzeile; kein `- [x]`.
- **Secrets:** der neue Code liest keine Zugangsdaten; der Modus `marker` braucht keine.

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `.github/workflows/veroeffentlichung.yml` | geprüft, ohne Befund (unverändert, W-1 bis W-9 des ersten Reports bestehen) |
| `tools/publish.sh`, `build.gradle.kts`, `tools/mk/publish.mk`, `Dockerfile`, `harness/README.md` | geprüft, ohne Befund (kein Diff in der Nachbesserung) |
| `tools/publish-selbsttest.sh` (Kosmetik, Meldung „acht rote Faelle“) | geprüft, ohne Befund außer N-1 |
| `docs/maintainer/releasing.md` | geprüft, ohne Befund (Schritt 8 nachgezogen; Treffer in Messprotokollen mit Grund) |
| `docs/plan/planning/in-progress/slice-cloudsmith-ziel-bauen.md` | geprüft, ohne Befund (Tabellenzeile ergänzt, keine Haken) |
| `docs/user/`, `spec/`, `src/`, `testserver/` | geprüft, ohne Befund (nicht im Diff) |
| Suppression, Host-statt-Docker, Schichtregel, Accepted-ADR geändert | geprüft, ohne Befund |

## Gates

`make docs-check` Exit 0 (1480 Dateien, 0 Befunde, mit diesem Report). `make skript-selbsttest` Exit 0. `make workflow-lint` Exit 0. `bash tools/publish-selbsttest.sh` Exit 0 am Stand; elf Mutationen an einer Kopie (Tabelle oben). Nicht gefahren: `make gates`, `make flaeche`, `make kopplung-zahlen`, `make clean-build`, `make e2e`, `tools/publish-probe.sh`.

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 1 |
| LOW | 2 |
| INFO | 1 |

**Finding-Klassen dieses Laufs:** Zuordnung an zweitem Ort ohne Sensor · Messaussage ohne Erzeuger im Repo · Bedingung in anderem Absatz

## Verdikt

F-1 bis F-6 des ersten Reviews sind in ihrem Wortlaut behoben; F-2 ist als Einschränkung behoben (Nachmessen für die Registry steht aus, N-2). Kein HIGH, kein Rollen-Widerspruch. N-1 (MEDIUM) ist die Nachbarlücke zu F-1: die Zuordnung Make-Target, Job-Schritt und Secret zum Ziel ist nicht gebunden; ob der Implementer sie schließt oder als Grenze in Kopf des Selbsttests und Closure-Notiz benennt, entscheidet er. Eine Änderung an Skripten löst erneut den Re-Review aus.
