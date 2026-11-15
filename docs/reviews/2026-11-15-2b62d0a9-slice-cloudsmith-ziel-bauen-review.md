# Review-Report: slice-cloudsmith-ziel-bauen — 2026-11-15

**Review-Art:** Code, Skripte, Workflow und Doku gegen Plan §1 bis §3 und DoD-Punkt 2 (Workflow-Prüfpunkte), [ADR-0049](../plan/adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md), [ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md), [ADR-0048](../plan/adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) und Hard Rules (`AGENTS.md` §3.1, §3.7, §3.8)

**Gegenstand:** Commit 2b62d0a9, Basis `main` 0507456d (10 Dateien, +479/-110): `.github/workflows/veroeffentlichung.yml`, `build.gradle.kts`, `Dockerfile` (Kommentar), `tools/publish.sh`, `tools/publish-selbsttest.sh`, `tools/publish-probe.sh`, `tools/mk/publish.mk`, `tools/mk/skript-selbsttest.mk`, `harness/README.md`, `docs/maintainer/releasing.md`.

**Skill:** `.harness/skills/reviewer.md` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-15

**Eingangs-Kontext:** Slice `slice-cloudsmith-ziel-bauen` (§1 bis §3, §6); [TDS-RB-05](../../spec/lastenheft.md#tds-rb-05--lizenz), [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit); [ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md), [ADR-0048](../plan/adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md), [ADR-0049](../plan/adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md); `AGENTS.md` §3; Vergleichsdatei (nur gelesen, außerhalb des Repos): `sdk-kotlin-release.yml` des Schwesterprojekts.

**Nicht gefahren:** `tools/publish-probe.sh` (alle Modi), `make gates`, `make e2e`, `make clean-build`. Die Belege des Implementers zum Dummy-Lauf (Name der Cloudsmith-Aufgabe, angekommene Dateien) sind nicht wiederholt; wo ein Satz darauf ruht, steht es unten.

---

## Workflow-Prüfpunkte (DoD Punkt 2, einzeln)

| # | Prüfpunkt | Ergebnis | Beleg (Datei gelesen) |
|---|---|---|---|
| W-1 | SHA-Pins der Actions | in Ordnung | Einzige Action `actions/checkout@d23441a48e516b6c34aea4fa41551a30e30af803` in allen vier Jobs, identisch zu `gates.yml` und `e2e.yml` (`grep uses:`); keine neue Action, kein Tag-Pin. |
| W-2 | `gates` vor beiden Veröffentlichungs-Jobs | in Ordnung | `publish` und `publish-cloudsmith` tragen `needs: gates`; `vorschau` ebenso. |
| W-3 | keine `needs` untereinander, kein `continue-on-error` | in Ordnung | Beide Jobs ohne `needs` auf den anderen; `continue-on-error` kommt in der Datei nicht vor ([ADR-0049](../plan/adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) Entscheidung 2a). |
| W-4 | Bedingung nur bei Tag-Push | in Ordnung | Beide Jobs `if: github.event_name == 'push' && github.ref_type == 'tag'`, wörtlich gleich; `vorschau` nur bei `workflow_dispatch`. Der Dispatch kann nicht veröffentlichen. |
| W-5 | Ancestor-Prüfung | in Ordnung | Schritt „Tag-Commit liegt in origin/main“ mit `git merge-base --is-ancestor HEAD origin/main` in beiden Jobs gleich, vor dem Schritt mit Secret. |
| W-6 | `permissions` | in Ordnung | Kopf `permissions: {}`; `packages: write` nur in `publish`; `publish-cloudsmith` `contents: read` ohne `packages: write`; `gates` und `vorschau` `contents: read`. |
| W-7 | Secret-Fenster | in Ordnung | `CLOUDSMITH_USERNAME`/`CLOUDSMITH_API_KEY` stehen nur im `env:` des Schritts `make publish-cloudsmith`; `GITHUB_TOKEN` nur im `env:` des Schritts `make publish`; der Job `publish` nennt kein `CLOUDSMITH_*`, der Cloudsmith-Job kein `GITHUB_TOKEN`. Kein Secret in `run:`-Text, in `with:` oder als Argument. |
| W-8 | Secret in Logs/Argumenten (Werkzeug) | in Ordnung | `publish.sh` liest Werte per `printenv` in Variablen, gibt `-e NAME` an `docker run`, die Erfolgsmeldung nennt Ziel und URL, nie Wert; kein `set -x`. Der Selbsttest sucht den Platzhalter in Ausgabe und docker-Aufruf (Fälle `gut`, `c-gut`, `c-standard`, `standard`). |
| W-9 | Vergleich mit Schwesterprojekt | gleiche Bauart | Ein Job je Ziel, keine `needs` untereinander, `contents: read` im Cloudsmith-Job, Secrets nur im Schritt-`env:`, Einzel-Aufgabe statt `publish`. Abweichung (kein Befund): hier ein gemeinsamer `gates`-Job und `make`-Aufrufe statt direkter `docker`-Schritte; die Ancestor-Prüfung hat das Schwesterprojekt nicht. |

`make workflow-lint` Exit 0.

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| F-1 | MEDIUM | Die Namen der Zugangsdaten-Umgebung (`CLOUDSMITH_USERNAME`, `CLOUDSMITH_API_KEY`) stehen in `build.gradle.kts` (`System.getenv`), in `tools/publish.sh` (`benutzer_var`, `geheim_var`) und im Workflow-`env:`; der Selbsttest liest sie nicht gegeneinander. Gefahrene Mutationen an einer Kopie von `build.gradle.kts`: `getenv("CLOUDSMITH_USERNAME")` → `getenv("CLOUDSMITH_USER")` und `getenv("CLOUDSMITH_API_KEY")` → `getenv("GITHUB_TOKEN")`: `tools/publish-selbsttest.sh` bleibt Exit 0 (grün). Ebenso grün: der Name der Property `tdswireCloudsmithUrl` verändert. Rot dagegen (gemessen): Standard-URL, `name = "cloudsmith"`, Zeile `version = `. Failure-Szenario: der einzige echte Lauf scheitert mit 401 am Cloudsmith, nachdem `0.1.1` auf GitHub Packages schon verbraucht ist ([ADR-0049](../plan/adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) Teilfehler). Nur `publish-probe.sh ziele`/`registry` (kein Gate) würde es zeigen; der Kopplungskommentar nennt nur die URL. | Reviewer-Skill („Zahl, Feldmenge oder Zuordnung steht an einem zweiten Ort ohne Sensor“), `AGENTS.md` §3.7 (Kopplung) | `build.gradle.kts` · `password = System.getenv("CLOUDSMITH_API_KEY")`; `tools/publish.sh` · `benutzer_var=CLOUDSMITH_USERNAME; geheim_var=CLOUDSMITH_API_KEY`; `tools/publish-selbsttest.sh` · `Aufgabe ${t%%:*}: publish.sh nennt sie nicht` | ja (Mutation, gefahren) | Zuordnung an zweitem Ort ohne Sensor |
| F-2 | MEDIUM | Die Marker-Frage des Implementers trifft zu: Das neue Probe-Skript schreibt, die POM trage den Marker `published-with-gradle-metadata` und Gradle lese die `.module` „sonst“ auch ohne `gradleMetadata()`; erst `ignoreGradleMetadataRedirection()` schalte das ab. Der frühere Konsument (`mavenLocal { metadataSources { if (!nurPom) gradleMetadata(); mavenPom(); artifact() } }`, Stand `main`) ruft `ignoreGradleMetadataRedirection()` nicht. Wenn die Aussage des neuen Kommentars stimmt, hat die Spalte „Konsument nur über die POM“ in `docs/messungen/maven-paket-2026-11-14.md` (M1.1, M1.2, M2, M3 und der Satz „einmal ohne sie (nur POM)“) die `.module` trotzdem gelesen; dann belegt sie „nur POM“ nicht. Die Aussage des neuen Kommentars selbst hat im Diff keine Messzeile (für `mavenLocal` ohnehin nicht). Bewertet, nicht behoben, wie beauftragt. Die Wirkung auf die Entscheidung zu M1 (Scope `api`) ist gering: M1.2 ist in beiden Spalten rot, M1.1 in beiden grün. | `AGENTS.md` §3.8; Reviewer-Skill („Aussage breiter als ihr Beleg“) | `docs/messungen/maven-paket-2026-11-14.md` · `Konsument nur über die POM`; `tools/publish-probe.sh` · `Ohne .module: die POM der Version traegt den Marker` | ja (Lauf des Konsumenten mit `ignoreGradleMetadataRedirection()` an `mavenLocal`) | Aussage breiter als Beleg (Messspalte unterscheidet nicht) |
| F-3 | MEDIUM | Fortschreibung lässt die zweite Fundstelle stehen: `releasing.md` Schritt 8 sagt weiter „der Job `publish` bricht in der Prüfung `git merge-base --is-ancestor HEAD origin/main`“; der Diff berührt Schritt 8 nicht, obwohl `publish-cloudsmith` dieselbe Prüfung trägt und Schritt 9 und der Tag-Schutz fortgeschrieben sind. | Reviewer-Skill („Fortschreibung lässt die zweite Fundstelle stehen“) | `docs/maintainer/releasing.md` · `der Job `publish` bricht in der Prüfung` | ja (Grep) | zweite Fundstelle nicht nachgezogen |
| F-4 | LOW | Der Kopf von `tools/publish-probe.sh` nennt den Modus `konsument` weiter „mit Gradle-Metadaten und nur ueber die POM“, ohne die Grenze aus F-2. | `AGENTS.md` §3.8 | `tools/publish-probe.sh` · `mit Gradle-Metadaten und nur ueber die POM` | nein | Aussage breiter als Beleg |
| F-5 | LOW | Die Abschlussmeldung des Selbsttests nennt für Cloudsmith „neun rote Faelle“; im Skript stehen acht rote `c-`-Fälle (`c-snapshot`, `c-tag-fehlt`, `c-tag-alt`, `c-baum`, `c-schluessel-fehlt`, `c-benutzer-fehlt`, `c-fremdes-ziel`, `c-platzhalter-leer`), dazu zwei Kreuzfälle und zwei Aufruffehler, die die Meldung gesondert nennt. | `AGENTS.md` §3.8 | `tools/publish-selbsttest.sh` · `neun rote Faelle, zwei Zugangsdaten des anderen Ziels` | ja (Zählung) | Zahl in Meldung ohne Sensor |
| F-6 | LOW | Plan-Tabelle (Slice §3) nennt `tools/mk/skript-selbsttest.mk` nicht; der Diff ändert dort die Hilfezeile. | Reviewer-Skill („Plan-Tabelle nennt die berührten Dateien nicht“) | `tools/mk/skript-selbsttest.mk` · `Pruefungen von tools/publish.sh je Ziel vor Docker` | ja (Diff gegen Tabelle) | Plan-Tabelle unvollständig |
| F-7 | INFO | Zusagen „kein `continue-on-error`“, „kein `needs` untereinander“, „Dispatch überspringt `publish-cloudsmith`“ sind nur am Text prüfbar; der Kopf nennt das selbst („gelesen, nicht gemessen“). Die erste Messung ist der Tag-Lauf von `slice-cloudsmith-veroeffentlichung-0-1-1`. | `AGENTS.md` §3.8 | `.github/workflows/veroeffentlichung.yml` · `gelesen, nicht gemessen` | nein | — |
| F-8 | INFO | Die Task `publish` führt jetzt beide Repositories aus und braucht beide Zugangsdaten (Plan §6 Risiko 1). Im Repo ruft nichts mehr `gradle publish` (Grep über `*.sh`, `*.mk`, `Dockerfile`, `*.yml`, `*.kts`, `*.md`: kein Treffer). Ob Gradle fehlende Umgebungswerte eines nicht ausgeführten Repositories toleriert, hat dieser Review nicht gemessen; der Risiko-Ausgang liegt bei Implementer und Verifier. | Slice §6 | `build.gradle.kts` · `name = "cloudsmith"` | nein | — |

## Antworten auf die gezielten Punkte

1. **Kopplung `build.gradle.kts` ↔ `publish.sh` ↔ Selbsttest:** gelesen und mutiert (Skript `bash tools/publish-selbsttest.sh` gegen Kopie). Gehalten (rot): Standardziele beider Anbieter, Repository-Namen (`registry`, `cloudsmith`) gegen die Aufgabennamen, Versionszeile. Nicht gehalten (grün trotz Mutation): Namen der Umgebung und der URL-Property in `build.gradle.kts` (F-1). `isAllowInsecureProtocol = true` statt `startsWith("http://")` bleibt ebenfalls grün; das ist die im Kopf von `publish.sh` schon genannte Grenze und kein neuer Befund.
2. **`publish.mk` trägt den Aufruf mechanisch:** `publish-cloudsmith` ruft `bash tools/publish.sh --ziel cloudsmith` mit `BUILDER_FLAGS`; `.PHONY` ergänzt; weder `GATE_CHECKS`, `GATE_KLASSE_*`, `GATE_STAGE` noch `e2e.mk` nennen es (Selbsttest-Gegenprobe `mk3` an einer Kopie, grün). Zeile des Targets in `harness/README.md` steht. In Ordnung.
3. **Aussagen über Beleg hinaus:** Workflow, `releasing.md` Schritt 9, Tag-Schutz und `harness/README.md` sind im Zustand „gebaut, nicht gelaufen“ geschrieben; der Satz „die zweite im Lauf gegen einen Dummy-Server gelesen“ (Name der Cloudsmith-Aufgabe) ruht auf einem Lauf des Implementers, den dieser Review nicht wiederholt hat. `docs/user/` unverändert, wie [ADR-0049](../plan/adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) Entscheidung 5 es verlangt. Befunde: F-2, F-4, F-5.
4. **Marker `published-with-gradle-metadata`:** siehe F-2; an der `.module`-Erzeugung ist nichts geändert (Plan §1 hält das ausdrücklich draußen).
5. **Secrets in Argumenten oder Logs:** kein Befund (W-8).

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `.github/workflows/veroeffentlichung.yml` | geprüft, ohne Befund (W-1 bis W-9) |
| `tools/publish.sh` (Zielregel, Platzhalter-Regel, Namensübergabe an `docker run`, Fehlerzweige vor Docker) | geprüft, ohne Befund; Selbsttest Exit 0 am Stand |
| `tools/mk/publish.mk`, `tools/mk/skript-selbsttest.mk` | geprüft, ohne Befund außer F-6 |
| `Dockerfile` | geprüft, ohne Befund (nur Kommentarzeilen; `make stempel-eingaenge-pruefung` Exit 0) |
| `harness/README.md` | geprüft, ohne Befund (Zeilen `make skript-selbsttest`, `make publish`, `make publish-cloudsmith`, Workflow stimmen mit den Dateien überein) |
| `docs/maintainer/releasing.md` Kopf, Schritt 9, Tag-Schutz | geprüft, ohne Befund; Schritt 8 siehe F-3 |
| `docs/user/`, `spec/`, `src/`, `testserver/` | geprüft, ohne Befund (nicht im Diff) |
| Suppression, Host-statt-Docker, Schichtregel, Accepted-ADR geändert | geprüft, ohne Befund ([ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md)/0048/0049 unverändert; kein Gradle auf dem Host) |
| Kommentar-Klassen (HIGH-Liste) in den Kommentaren des Diffs | geprüft, ohne Befund |

## Gates

`make docs-check` Exit 0 (1478 Dateien, 0 Befunde, auf dem Stand 2b62d0a9, vor Anlegen dieses Reports). `make workflow-lint` Exit 0. `make stempel-eingaenge-pruefung` Exit 0. `bash tools/publish-selbsttest.sh` Exit 0 am Stand. Acht Mutationen an einer Kopie von `build.gradle.kts` (Ergebnis in F-1). Nicht gefahren: `make gates`, `make flaeche`, `make kopplung-zahlen`, `make clean-build`, `make e2e`, `tools/publish-probe.sh`.

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 3 |
| LOW | 3 |
| INFO | 2 |

**Finding-Klassen dieses Laufs:** Zuordnung an zweitem Ort ohne Sensor · Aussage breiter als Beleg · zweite Fundstelle nicht nachgezogen · Zahl in Meldung ohne Sensor · Plan-Tabelle unvollständig

## Verdikt

**Merge-blockierend:** ja, nach dem Skill wegen F-1 bis F-3 (MEDIUM; Ausgang je Finding entscheidet der Implementer; F-2 betrifft ein Messprotokoll außerhalb des Diffs und braucht die Entscheidung Nachmessen oder Einschränken). Kein HIGH; die Workflow-Prüfpunkte der DoD (W-1 bis W-9) sind ohne Befund. Eine Behebung, die Skripte oder Build-Dateien ändert (F-1), löst nach Slice §3 Ablauf (3) einen Re-Review in frischem Kontext aus.
