# Verifikation: slice-cloudsmith-ziel-bauen — 2026-11-15

**Rolle:** Verifier (Modul 8/11) — „Bauen wir es richtig?“ gegen DoD und Entscheidungen, nicht gegen den Diff und nicht gegen den Bedarf.

**Stand:** Commit `85820481` (Branch `worktree-agent-ad27d50069e3391c2`; Commits `2b62d0a9`, `9a9a681d`, `f25a9e9f`, `85820481`), per `git merge --ff-only` auf den Worktree dieses Laufs gehoben (Basis `0507456d`). Kein `src/`, kein `testserver/` im Diff; deshalb kein `make clean-build`, kein `make e2e` (Tabelle in `harness/README.md` §Minimal agent workflow, [slice](../plan/planning/done/slice-cloudsmith-ziel-bauen.md) §3 Ablauf 4).

**Gegenstand:** [slice-cloudsmith-ziel-bauen](../plan/planning/done/slice-cloudsmith-ziel-bauen.md) §2 (drei Liefer-Punkte, sieben Nachweis-Pflichten), gegen [ADR-0049](../plan/adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) Entscheidungen 2a und 3a, [ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md) Entscheidungen 3 und 6, [ADR-0048](../plan/adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md); Bezug [TDS-RB-05](../../spec/lastenheft.md#tds-rb-05--lizenz), [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit).

**Eingang:** die drei Review-Reports `2026-11-15-2b62d0a9-…-review.md`, `…-9a9a681d-…-re-review.md`, `…-f25a9e9f-…-re-review-2.md` (Stand: kein HIGH/MEDIUM; offen LOW N2-1, N2-2, INFO N2-3, N2-4). Ein Bericht des Implementers mit eigenen Sensor-Belegen liegt dieser Verifikation nicht vor; die Belege unten sind deshalb eigene Läufe, nicht Prüfungen einer Behauptung.

**Datum:** 2026-11-15 · **Modell:** claude-sonnet-5-5

---

## 1. Eigene Läufe

| Lauf | Ergebnis |
|---|---|
| `make gates` | Exit 0, 9 s. Die drei Gruppen `werkzeug`, `tests`, `stempel` wurden **übersprungen** (`stempel-gruppe: … uebersprungen, Beleg fuer <Hash>`): der Beleg gilt für den Hash dieses Baums und stammt nicht aus diesem Lauf. `gate-zuordnung` (16 Gates, Selbsttest 33 Fälle), `stempel-eingaenge`, `docs-check` (`klasse immer`) liefen. |
| `make skript-selbsttest` | Exit 0 (Docker-Stage `CACHED`, gleiche Eingänge). Danach unter drei Mutationen frisch gelaufen (Abschnitt 2); im Grundzustand der Mutation 3 `publish-selbsttest: ok (… Ziel github … Ziel cloudsmith …)`. |
| `make workflow-lint` | Exit 0 (`e2e.yml`, `gates.yml`, `veroeffentlichung.yml`). |
| `make docs-check` | Exit 0, 1481 Dateien, 0 Befunde (vor dem Anlegen dieses Berichts). |
| `make flaeche` | Exit 0. |
| `make kopplung-zahlen` | Exit 0. |
| `bash tools/publish-probe.sh ziele` | Exit 0 (gelaufen, siehe unten). |
| `bash tools/publish-probe.sh registry cloudsmith anonym` | Exit 0 (gelaufen, siehe unten). |
| `bash tools/publish-probe.sh registry cloudsmith zugang` | Exit 0 (gelaufen, siehe unten). |

Nicht gefahren: `make clean-build`, `make e2e` (Änderungsart ohne `src/`/`testserver/`), `tools/publish-probe.sh url …` (Netz nach außen), `make publish*`, jede Veröffentlichung. Kein Secret gelesen, keine künstliche Last. Der Arbeitsbaum war nach den Mutationen wieder sauber (`git checkout --`).

## 2. Eigene Mutationen (am Arbeitsbaum, je Lauf `make skript-selbsttest`, danach zurückgesetzt)

| Nr. | Mutation | Erwartung | Ergebnis |
|---|---|---|---|
| V-1 | `tools/publish.sh`: Zweig `PLATZHALTER-?*) ;;` der Zielregel zu `*) ;;` (echtes Schlüsselmaterial an ein fremdes Ziel) | rot | **rot**, Exit 2: `fremdes-ziel`, `fremdes-ziel-https`: „Exit 0 statt 1“, „docker wurde trotz Exit 1 gerufen“ |
| V-2 | Workflow: `packages: write` im Job `publish-cloudsmith` | rot | **rot**, Exit 2: „der Job publish-cloudsmith hat ein Recht packages“ |
| V-3 | Workflow: `CLOUDSMITH_API_KEY: ${{ secrets.… }}` im `env:` des Schritts `make gates` im Job `gates` | durch den Selbsttest nicht gebunden (N2-1) | **grün**, Exit 0 — N2-1 am eigenen Lauf bestätigt; die Zusage „nur im `env:` des Schritts `make publish-cloudsmith`“ ([ADR-0049](../plan/adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) Entscheidung 3a) hält an dieser Stelle allein der Review. `make workflow-lint` ist für V-3 nicht gefahren (er prüft die Zuordnung ohnehin nicht, Kopf des Workflows). |

V-1 und V-2 belegen, dass der Selbsttest die Zielregel für Schlüsselmaterial und das Recht des Cloudsmith-Jobs trägt; V-3 belegt die Grenze, die der Report des zweiten Re-Reviews nennt.

## 3. DoD Zeile für Zeile

### Liefer-Punkt 1 — Zweites Ziel im Werkzeug, ohne Wirkung nach außen belegt: **erfüllt**

| Teilzusage | Beleg (eigene Messung/Lesung) |
|---|---|
| `build.gradle.kts`: zweites Repository `cloudsmith`, Standardziel `https://maven.cloudsmith.io/pt9912/tdswire/`, überschreibbar (`tdswireCloudsmithUrl`), Zugangsdaten nur aus `CLOUDSMITH_USERNAME`/`CLOUDSMITH_API_KEY` | `git diff 0507456d..HEAD -- build.gradle.kts` gelesen: so umgesetzt, `isAllowInsecureProtocol` nur bei `http://`. |
| `tools/publish.sh`: Zielschalter, je Ziel nur dessen Zugangsdaten, namentlich an `docker run`, Einzel-Aufgabe statt `publish`, Schlüsselmaterial nur an das Standardziel | `publish.sh` gelesen (`--ziel github|cloudsmith`, `-e "$benutzer_var" -e "$geheim_var"`, `gradle "$gradle_task"`, Platzhalterregel bei `url != standard_url`); V-1 rot. |
| neues Make-Ziel `publish-cloudsmith`; `make publish` bleibt GitHub Packages; beide in keiner Gate-Liste | `tools/mk/publish.mk` gelesen; `make gates` mit `gate-zuordnung` grün; der Selbsttest liest die Gate-Listen (Report 2b62d0a9, Gegenprobe). |
| `make skript-selbsttest` deckt je Ziel die Brüche vor Docker und die Zielregel | grün; V-1 rot; Ausgabe nennt „Ziel github … Ziel cloudsmith: guter Fall, Standardziel, Docker-Netz, acht rot…“. |
| `tools/publish-probe.sh`: Einzel-Aufgabe läuft gegen den Dummy, Aufgabenname **am Lauf belegt**, Platzhalter in keiner Ausgabe, angekommene Dateien aufgelistet | `probe ziele` selbst gefahren: `> Task :publishMavenPublicationToCloudsmithRepository` (und `…ToRegistryRepository` für github) im Gradle-Lauf gelesen; je Ziel 20 Dateien mit 201 (Jar, `.module`, POM, Prüfsummen, `maven-metadata.xml`); 18 Suchen nach dem Platzhalter (Klartext, zwei Base64-Formen, sechs Quellen): 0 Treffer, Positivprobe (`docker inspect` des Dummy) 1 Treffer. Leerer Schlüssel bricht je Ziel vor dem Netz (Exit 1, Meldung nennt `GITHUB_TOKEN` bzw. `CLOUDSMITH_API_KEY`). |

Der Aufgabenname ist damit belegt (nicht mehr abgeleitet): `publishMavenPublicationToCloudsmithRepository`. Das Risiko „`publish` führt beide Repositories aus“ ist durch die Einzel-Aufgabe je Ziel umgangen, nicht gemessen (kein Lauf von `gradle publish` mit nur einem Satz Zugangsdaten); der Ausgang gehört in die Closure (Abschnitt 6).

### Liefer-Punkt 2 — Zweiter Job im Workflow: **erfüllt, soweit Lesung und Selbsttest tragen**

Eigene Lesung von `.github/workflows/veroeffentlichung.yml` am Stand `85820481`:

| Punkt | Befund |
|---|---|
| `needs: gates` in `publish` und `publish-cloudsmith`; keine `needs` zwischen beiden | ja; `vorschau` ebenfalls `needs: gates` |
| kein `continue-on-error` | keines in der Datei |
| Bedingung nur Tag-Push | beide Jobs wörtlich `github.event_name == 'push' && github.ref_type == 'tag'`; `vorschau` nur `workflow_dispatch` |
| Schritt „Tag-Commit liegt in origin/main“ | in beiden Jobs gleich |
| Rechte | `publish`: `contents: read`, `packages: write`; `publish-cloudsmith`: nur `contents: read`; Kopf `permissions: {}` |
| Secret-Fenster | `CLOUDSMITH_USERNAME`/`CLOUDSMITH_API_KEY` nur im `env:` des Schritts `make publish-cloudsmith`; `publish` bekommt sie nie (`GITHUB_TOKEN` nur im Schritt `make publish`) |
| Pins | alle `uses:` mit Commit-SHA `d23441a48e516b6c34aea4fa41551a30e30af803` (derselbe wie in `gates.yml`); keine neue Action |
| `vorschau`/Dispatch veröffentlichen nie | `vorschau` führt nur `make publish-vorschau` aus |
| `make workflow-lint` | grün |

Der Review-Report nennt die Punkte einzeln (Reports 2b62d0a9 und die zwei Re-Reviews); die Lesung dieses Verifiers stimmt damit überein. Mutationen V-2 (rot) und V-3 (grün) zeigen, was der Selbsttest bindet und was nicht (N2-1).

### Liefer-Punkt 3 — Probe-Konsument gegen Registry per URL: **erfüllt für den Dummy, nicht für ein echtes Cloudsmith**

| Teilzusage | Beleg |
|---|---|
| Modus `registry <ziel> <anonym|zugang>`: der Bau-Lauf schreibt Dateien, ein Auslieferer im Docker-Netz ohne Zugang nach außen liefert sie aus, ein Konsument löst auf, übersetzt, läuft, mit `.module` und ohne | `probe registry cloudsmith anonym` und `… zugang` selbst gefahren: je drei Konsumenten-Läufe (`metadaten`, `pom`, `pom-ohne-schutz`) mit Exit 0 und `PROBE-OK`. |
| anonym | Lese-Anfragen `auth=fehlt -> 200` (Jar, `.module`, POM). |
| mit Zugangsdaten | Anfragen ohne Auth `-> 401`, danach `auth=ok -> 200`; der Dummy trennt die Fälle, das Skript hat also den Zugang gesendet. |
| `.module` mit und ohne | Lauf `pom` fragt `.pom` (keine `.module`), Lauf `metadaten` fragt `.module`; Lauf `pom-ohne-schutz` zeigt: ohne `ignoreGradleMetadataRedirection()` fragt Gradle die `.module` trotzdem an. Das stützt die Einschränkung des Messprotokolls („nur POM“ nur mit Bedingung). |
| Modus ist Erzeuger der Messzahlen für den Folge-Slice | `ziele`, `registry`, `url` im Kopf als Erzeuger genannt; `url` nicht gefahren (Netz). |

## 4. Nachweis-Pflichten

| Pflicht | Stand |
|---|---|
| `make gates` grün, `make docs-check`, `make flaeche`, `make kopplung-zahlen`, `make workflow-lint` | erfüllt (Abschnitt 1). Einschränkung: die Gruppen `werkzeug`, `tests`, `stempel` von `make gates` liefen nicht neu, sondern per Beleg (gleicher Baum-Hash); den Frischlauf der Klasse `werkzeug` ersetzt hier die Selbsttest-Läufe V-1 bis V-3 und der Grundlauf. |
| Review in frischem Kontext, Report unter `docs/reviews/`, bei Code ein zweiter Review | erfüllt: drei Reports, der letzte ein zweiter Re-Review (Code: Selbsttest, Probe-Skript). Der Re-Review hat den Selbsttest als Ganzes nicht gefahren (Umgebung wies es ab); dieser Verifier hat ihn gefahren. |
| Doku-Update (`harness/README.md` §Sensors, `releasing.md` Schritt 8/9, Tag-Schutz, Kopf; Kopfkommentare `veroeffentlichung.yml`, `publish.sh`, `publish.mk`) im Zustand „gebaut, nicht gelaufen“ | erfüllt nach Lesung: Zeilen `make publish`, `make publish-cloudsmith` („am Dummy-Server belegt, nie gegen Cloudsmith gelaufen“), Workflow („nie gelaufen“), `skript-selbsttest` („je Ziel“); `releasing.md` Schritt 9 „gebaut und nie gelaufen“, Tag-Schutz um die zwei Secrets; `docs/user/` unverändert (Entscheidung 5). Nicht jeder Satz geprüft; eine Stichprobe gegen die Zusagen fand keinen Satz, der „gelaufen“ behauptet. |
| Closure-Notiz mit Steering-Loop-Lerneintrag | **offen**, §7 des Slice steht auf „Wird bei der Closure gefüllt.“ — Aufgabe der Closure, kein Befund dieses Verifiers. |
| Beobachtungs-Register fortgeschrieben, kein Zähler gesetzt | **offen** (Closure). |
| Jedes Risiko aus §6 trägt einen Ausgang | **offen** (Closure); Vorschläge in Abschnitt 6. |
| Drei Paarungen (Anker · Folge-Slice · Register) | **offen** (Closure). |
| keine `- [x]` vor der Verifikation | bestätigt: der Diff setzt keinen Haken; der Planner setzt sie nach diesem Bericht. |

## 5. Plan-vs-Code-Diff

| Plan (§3 Tabelle) | Code | Urteil |
|---|---|---|
| `build.gradle.kts` update | +15 Zeilen, Repository `cloudsmith` | wie geplant |
| `tools/publish.sh` update | Zielschalter, Einzel-Aufgabe, Platzhalterregel je Ziel | wie geplant |
| `tools/mk/publish.mk` update | `publish-cloudsmith` | wie geplant |
| `tools/mk/skript-selbsttest.mk` update | Hilfezeile | wie geplant (der Plan führte die Zeile erst im Nachtrag) |
| `tools/publish-selbsttest.sh` update | je Ziel Brüche, Schlüssel nur am Standardziel, Gate-Listen, Namens-, Job- und Schritt-Bindung des Workflows | über den Plan hinaus: Workflow-Zuordnung je Job (aus dem Review N-1); die Zusage des Plans („der Workflow ist vom Selbsttest nicht zu sehen“, §3 Frage 1) ist damit teilweise überholt, der Plan ist an der Stelle zu aktualisieren (Closure) |
| `tools/publish-probe.sh` update | Modi `ziele`, `registry`, `url`, `marker` | `marker` (Frage, ob `mavenLocal` die `.module` liest) und die Art `pom-ohne-schutz` gehen über den Plan hinaus, tragen den dritten Punkt und die Marker-Messung; keine Abweichung gegen eine Zusage |
| `.github/workflows/veroeffentlichung.yml` update | Job `publish-cloudsmith`, Kopfkommentar | wie geplant |
| `harness/README.md`, `releasing.md` update | nachgezogen | wie geplant |
| `Dockerfile`, `tools/stempel/*.eingaenge` „nur falls nötig“ | Dockerfile: Kommentarzeilen; `*.eingaenge` unverändert; `stempel-eingaenge` grün | wie geplant |
| nicht im Plan, im Diff | `docs/messungen/maven-paket-2026-11-14.md` (Wortlaut der Bedingung „nur POM“, Zahlen unverändert laut Re-Review 2); Slice-Datei (+1 Zeile Plan-Tabelle); drei Review-Reports | Messprotokoll-Änderung folgt aus Review F-2/N-3, vertretbar |

Ausdrücklich-NICHT-Liste des Slice (§1): kein Release, kein Tag, keine `.module`-Änderung (`manifest-pruefung.sh`, `GenerateModuleMetadata` unberührt), kein neues Gate (V: `gate-zuordnung`), kein echtes Schlüsselmaterial, keine Lastenheft-Änderung, `docs/user/` unverändert: eingehalten (Diffstat gelesen).

Entscheidungs-Konformität: [ADR-0049](../plan/adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) Entscheidung 2a (zwei Jobs, `needs: gates`, kein `needs` untereinander, kein `continue-on-error`) und 3a (Secrets nur im `env:` des Schritts, `contents: read`, Einzel-Aufgabe je Ziel, Standardziel fest, Platzhalter sonst) erfüllt nach Lesung und V-1/V-2; Entscheidung 3 „Ausschluss von `-SNAPSHOT`“ im Selbsttest je Ziel gebunden. [ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md) Entscheidung 6 (Docker-Stage `publish`, Zugangsdaten nur zur Laufzeit): unverändert. [ADR-0048](../plan/adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Ablauf: erweitert, `vorschau` bleibt.

## 6. Was ungemessen bleibt — und ob es für die DoD trägt

**Ungemessen (Zustand dieses Stands):**

1. **Tag-Lauf von `publish-cloudsmith`.** Der Job ist nie gelaufen; ob er beim Tag-Push läuft und beim Dispatch übersprungen wird, ist gelesen (Bedingung), nicht gemessen. Der Dispatch-Lauf 43619591947 kannte den Job nicht.
2. **Pins, Reihenfolge `gates` vor beiden Jobs, Bedingung, Secret-Fenster, Rechte je Job.** Nur Review und Lesung tragen sie; der Selbsttest bindet davon Make-Target, Schritt, Job, Secrets des Cloudsmith-Schritts und `packages`/`contents` beider Jobs, nicht das Secret-Fenster außerhalb der beiden Jobs (V-3), nicht die Pins, nicht `needs`, nicht die Bedingung.
3. **Echtes Cloudsmith.** Nach Angabe des Auftrags nur ein 404 des leeren Repositories (Modus `url`); dieser Verifier hat ihn nicht wiederholt. Ein 404 trennt „leer“ nicht von „nicht öffentlich“ (Kopf des Skripts). Ungemessen: Sammelfenster, asynchrone Verarbeitung, Annahme der `.module`, Form des Benutzernamens, Rechte des Schlüssels, Verhalten bei erneutem Upload.
4. **Start-Bedingung 4.(3) des Slice** („Repository als ‚Open source‘ bestätigt“) steht im Plan als „nicht erfüllt“; der Bau braucht sie nicht, die Veröffentlichung schon.
5. **`gradle publish` mit nur einem Satz Zugangsdaten** (§6 Risiko 1): nicht gemessen, nur umgangen.
6. **Frischlauf der Gate-Klassen** in `make gates`: per Hash-Beleg übersprungen (Abschnitt 1).

**Trägt das für die DoD dieses Slice?** Ja. Die DoD fordert ausdrücklich Belege „ohne Wirkung nach außen“ (Dummy-Server, Platzhalter) und für den Workflow „`make workflow-lint` grün und Review der Punkte, die der Linter nicht prüft“. Punkt 1 und 3 sind am Dummy gemessen und von mir wiederholt, Punkt 2 ist per Review und eigener Lesung getragen, nicht per Sensor, und so steht es in der DoD. Die Punkte 1 bis 3 der Ungemessen-Liste sind genau die Gegenseite, die der Plan dem Folge-Slice [slice-cloudsmith-veroeffentlichung-0-1-1](../plan/planning/in-progress/slice-cloudsmith-veroeffentlichung-0-1-1.md) zuweist (Risiko „Der Dummy belegt den Ablauf, nicht Cloudsmith“, Register `nachweis-von-der-falschen-seite-der-grenze`). Was **nicht** trägt, ist jede Aussage über Cloudsmith oder über den Tag-Lauf; die Doku sagt „gebaut, nicht gelaufen“ und bleibt damit gedeckt (`AGENTS.md` §3.8).

Offene Closure-Pflichten (§7, Register, Risiko-Ausgänge, Paarungen) sind keine DoD-Verletzung der Bauarbeit, sondern der nächste Schritt des Planners.

## 7. Offene Findings der Reviews — Zuordnung (Vorschlag an den Planner)

| ID | Schwere | Vorschlag | Begründung |
|---|---|---|---|
| N2-1 | LOW | **Closure-Risiko, Träger [slice-cloudsmith-veroeffentlichung-0-1-1](../plan/planning/in-progress/slice-cloudsmith-veroeffentlichung-0-1-1.md)** (Ausgang: „weiter offen“, mit Tag-Lauf als Beleg der Zuordnung) — und eine **Einzeilen-Nachbesserung des Kommentars** im Selbsttest, bevor der Slice nach `done/` geht: „Rechte und Secrets je Job“ auf „Secrets und Rechte der Jobs `publish` und `publish-cloudsmith`“ einschränken (§3.8). | Die Lücke selbst (V-3 grün) ist von der DoD gedeckt: der Slice sagt, dass diese Punkte der Review prüft. Der zu breite **Kommentar** ist dagegen ein vermeidbarer §3.8-Verstoß; ihn einzuengen ist billiger als ihn zu tragen. Den Sensor zu erweitern (Cloudsmith-Name in keinem Schritt außerhalb des Cloudsmith-Jobs, Datei-weit) wäre eine kleine Änderung, ist aber kein DoD-Muss; wenn der Planner sie will, gehört sie vor `done/` in diesen Slice, nicht in den Folge-Slice, weil sie Werkzeug des Selbsttests ist und der Folge-Slice ein einmaliger echter Lauf ist. |
| N2-2 | LOW | **Nachbesserung in diesem Slice** (zwei Beschriftungen in `tools/publish-probe.sh`: `lauf "nur POM" -PnurPom` und die zweite „nur POM, …“-Zeile um „(`.module` wird trotzdem gelesen)“ ergänzen), nicht Closure-Risiko. | Der Folge-Slice ruft diese Läufe für seine Messzahlen auf; eine Beschriftung ohne Bedingung dort würde die Aussage `nur POM` ohne ihre Bedingung in ein Messprotokoll tragen. Billig, vor dem Folge-Slice zu beheben. Hinweis: die Änderung am Skript ist „Code oder Skripte“; der Slice verlangt dann „Nachbesserung ohne zweiten Review“-Vermerk in §7 oder einen Re-Review (der Planner entscheidet; eine reine Beschriftung ist keine Verhaltensänderung). |
| N2-3 | INFO | Closure: im Lerneintrag nennen („Messaussage am Kopf nur mit Protokollzeile“). Dieser Verifier hat den Lauf `pom-ohne-schutz` gefahren: Gradle fragt die `.module` an (Abschnitt 3, Punkt 3), (a) damit am eigenen Lauf bestätigt. Nicht nachgemessen: der Wortlaut „Could not parse module metadata“ des Modus `marker`. | |
| N2-4 | INFO | Closure: als bekannte Eigenschaft des Parsers notieren (sichere Richtung, Meldung nennt die Ursache nicht). | |

## 8. Findings dieses Verifiers

| ID | Kategorie | Befund |
|---|---|---|
| V-A | LOW (Beleg) | Die Belege des Implementers zu `ziele`/`registry` stehen in keinem Artefakt im Repo: weder das Messprotokoll noch ein Report führt die Zahlen (Aufgabenname, Dateiliste, 18 Suchen, Konsumenten-Läufe). Der Slice §5 sagt, die Ausgaben stünden „im Bericht des Implementers und im Review-Report“; die Reports nennen den Lauf als **nicht gefahren**. Die Zahlen sind jetzt von diesem Verifier gemessen (Abschnitt 3). Vorschlag: die Closure-Notiz nennt den Aufgabennamen (verlangt von §5) und verweist auf diesen Bericht, oder ein Messprotokoll des Dummy-Laufs entsteht im Folge-Slice. |
| V-B | INFO | `make gates` hat die drei Stempel-Gruppen per Beleg übersprungen; die Frische der Klasse `werkzeug` ist an V-1 bis V-3 und am Grundlauf belegt, nicht an einem `FORCE=1`-Lauf. |
| V-C | INFO | §3 Frage 1 des Slice („der Workflow ist weder vom Selbsttest noch von `make workflow-lint` im Ablauf zu sehen“) ist durch die Nachbesserung teilweise überholt (der Selbsttest liest den Workflow); die Closure-Notiz sollte den Satz in der Zusage der Sensor-Reichweite korrigieren. |

Kein HIGH, kein MEDIUM.

## 9. Fazit

Die drei Liefer-Punkte sind erfüllt, soweit die DoD sie ohne Wirkung nach außen verlangt; die eigenen Läufe (`make gates`, `skript-selbsttest`, `workflow-lint`, `docs-check`, `flaeche`, `kopplung-zahlen`, drei Probe-Läufe, drei Mutationen) bestätigen den Stand `85820481`. Die Bauarbeit ist verifiziert; der Slice ist nicht `done/`, solange §7 (Closure-Notiz mit Lerneintrag), Register, Risiko-Ausgänge, Paarungen und die DoD-Haken fehlen — das ist der Planner. Vor `done/` empfohlen: N2-2 und der Kommentar aus N2-1 als Nachbesserung, der Rest als Closure-Risiko mit Träger `slice-cloudsmith-veroeffentlichung-0-1-1`.
