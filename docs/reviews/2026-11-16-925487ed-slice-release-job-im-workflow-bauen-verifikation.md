# Verifikationsbericht: slice-release-job-im-workflow-bauen — 2026-11-16

**Rolle:** Verifier (Modul 11), frischer Kontext; DoD- und Entscheidungs-Konformität, Plan-vs-Code-Diff. Kein Review, kein Fix, keine DoD-Haken, kein Schließen.

**Stand:** Branch `slice-release-job-im-workflow-bauen`, Spitze `925487ed`, Basis `c2c3209e`; Arbeitsbaum sauber.

**Gegenstand:** Slice [slice-release-job-im-workflow-bauen](../plan/planning/done/slice-release-job-im-workflow-bauen.md); [ADR-0050](../plan/adr/0050-github-release-im-tag-lauf.md), [ADR-0048](../plan/adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md), [ADR-0049](../plan/adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md), [ADR-0024](../plan/adr/0024-lokale-stempel-zwei-klassen.md); [TDS-RB-05](../../spec/lastenheft.md#tds-rb-05--lizenz), [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit). Reviews im Branch: [461aa00b](2026-11-16-461aa00b-slice-release-job-im-workflow-bauen-review.md), [a1b762f7](2026-11-16-a1b762f7-slice-release-job-im-workflow-bauen-re-review.md), [b8a2085c](2026-11-16-b8a2085c-slice-release-job-im-workflow-bauen-re-review-2.md), [d182fe5f](2026-11-16-d182fe5f-slice-release-job-im-workflow-bauen-re-review-3.md) (zuletzt kein HIGH, MEDIUM, LOW; ein INFO T-1).

## Selbst gefahrene Sensoren (End-Stand `925487ed`, Docker, nichts nach außen)

| Sensor | Ergebnis | Dauer |
|---|---|---|
| `docker build --no-cache --target skript-selbsttest .` | Exit 0; Log nennt `release-anlegen-selbsttest: gruen`, `release-selbsttest: ok`, `publish-selbsttest: ok` | 34 s |
| `make workflow-lint` | Exit 0 | 2 s |
| `make docs-check` | Exit 0 (vor diesem Bericht) | 3 s |
| `make flaeche`, `make kopplung-zahlen` | Exit 0 | 1 s, 3 s |
| `make clean-build` | Exit 0 | 654 s |
| `make e2e` | Exit 0, „gruen (9 Stufen, 453 s)“ | 453 s |
| `make gates` | Exit 0 in 9 s: die Gruppen `werkzeug`, `tests`, `stempel` übersprungen (Beleg für den Stand, `FORCE=1` erzwingt); deshalb zusätzlich `FORCE=1 make gates` | Exit 0, 36 s (Layer-Cache der eben gelaufenen Stages) |

Gesamt etwa 20 Minuten; größte Posten `make clean-build` (654 s) und `make e2e` (453 s). Speicher verfügbar durchgehend 8 bis 11 GB; keine künstliche Last.

**Aussagekraft von `make clean-build` und `make e2e` für diese Änderungsart:** gering. Der Diff berührt weder `src/` noch `testserver/` (Dateiliste: Workflow, `Dockerfile`, `releasing.md`, `harness/README.md`, drei Dateien unter `tools/`, vier Reports). Beide Läufe belegen nur, dass der Stand die Produktbau- und Testkette nicht stört; `make e2e` fährt weder `release-dateien` noch das Eingangsskript (Stufen 1 bis 9, `tools/mk/e2e.mk` nennt beide nicht; vom Selbsttest als Text gelesen). Die Belege für den Gegenstand sind `skript-selbsttest`, `workflow-lint` und der Text-Lesetest des Workflows. Der Slice-Plan erklärt beide Läufe selbst für nicht erforderlich; die Tabelle in `harness/README.md` verlangt sie vom Verifier nur bei Änderungen an `src/` oder `testserver/`, also hier nicht — gefahren auf Auftrag.

## Befund je DoD-Punkt

**Liefer-Punkt 1 — Eingangsskript und feste Notiz** (`tools/release-anlegen.sh`, `tools/release-notiz.vorlage`): **erfüllt**, soweit am Stub belegbar. Gelesen am Code:

- Vor jedem `gh`: Versionsform (auch `-SNAPSHOT` bricht), Tag `v<Version>`, Tag besteht und liegt auf `HEAD`, genau die drei Dateien, `sha256sum -c`, `SHA256SUMS` nennt ZIP und Jar. Erst danach `gh release list`; Treffer oder Fehlschlag der Abfrage bricht; weder `--clobber` noch ein Löschen im Skript.
- Ein Aufruf `gh release create <Tag> <zip> <jar> <SHA256SUMS> --verify-tag --notes-file`; Token nur über die Umgebung des Schritts, nicht als Argument.
- Notiz aus der Vorlage: Version, Tag, Commit, Koordinaten, beide Paket-Links, Prüfsummen, der Satz „Die Prüfsumme belegt Unverändertheit, nicht Herkunft“, Lizenz MIT mit `LICENSE`, Zeiger auf `docs/user/einbettung-und-betrieb.md` (Datei besteht). Keine Aussage zu „anonym ladbar“, Jar-Gleichheit oder Lizenzen von `lib/`. Die beiden Registry-URLs stehen in den Messprotokollen (`veroeffentlichung-tag-lauf-2026-11-15`, `veroeffentlichung-0-1-1-2026-11-15`).
- Modus ohne Dateien: nur `v0.1.0` und `v0.1.1`, Notiz ohne Dateizeilen mit dem Satz zu fehlenden Dateien, Cloudsmith nur für `v0.1.1`, `create` ohne Anhang.

**Liefer-Punkt 2 — Job `release` im Workflow:** **erfüllt**, soweit am Text und am Linter belegbar. Gelesen in `.github/workflows/veroeffentlichung.yml` (die Prüfpunkte, die der Linter nicht prüft, einzeln):

| Prüfpunkt | Befund |
|---|---|
| Pins | alle `uses:` tragen dieselbe 40-stellige SHA (`actions/checkout@d23441a4…`), keine neue Action. Gegen die echte Action nicht geprüft (siehe Ungemessen) |
| Reihenfolge | `gates` ← `publish`, `publish-cloudsmith` (je `needs: gates`) ← `release` (`needs: [publish, publish-cloudsmith]`) |
| Bedingung | `github.event_name == 'push' && github.ref_type == 'tag'`, wortgleich mit `publish` |
| Rechte | `contents: write` genau einmal, nur in `release`; `release` ohne `packages: write` und ohne Cloudsmith-Secret; Kopf `permissions: {}` |
| Token-Fenster | `GH_TOKEN` nur im `env:` des Schritts `gh release create`; Bau (`make release-dateien`), Prüfsummen und Tag-Commit-Schritt ohne `env:` |
| Weitere | kein `continue-on-error`; Checkout `persist-credentials: false`; Schritt „Tag-Commit liegt in origin/main“ wie in den anderen Jobs; `release` ruft `make release-dateien` ohne `VORSCHAU=1`; `vorschau` ruft `make release-dateien VORSCHAU=1` ohne Secret und ohne `gh`; Kopfkommentar nennt Zusage und Grenze („nie gelaufen“) |

`make workflow-lint` Exit 0.

**Liefer-Punkt 3 — Selbsttest in `make skript-selbsttest`:** **erfüllt**. Frisch ohne Cache grün. Gelesen: Fälle je Zweig (Tag, Datei fehlt, zusätzliche Datei, Prüfsumme, `-SNAPSHOT`, Release besteht ohne zweiten `create`, Abfrage schlägt fehl, Platzhalter, Modus ohne Dateien); das Vorschau-ZIP im Verzeichnis (`tdswire-1.2.3-vorschau.zip`) bricht mit „fuehrt nicht genau“ ohne `create`; je Marke `# P:<name>` eine Mutation, dazu `--verify-tag` entfernt und `--clobber` ergänzt; der Workflow als Text mit 21 Gegenproben an Kopien; die Abwesenheit in Gate-Listen und `e2e.mk` mit Gegenproben. Das Protokoll im Stub zeigt im Gutfall einen `create`. Die Einbindung ist vollständig: `Dockerfile` `COPY` der drei Dateien, Aufruf in der `&&`-Kette; `make stempel-eingaenge-pruefung` und `gate-zuordnung` grün in `make gates`. Ich habe keine eigene Mutation außerhalb des Selbsttests gesetzt; die Gegenproben sind die der Reviews (acht Mutationen am Selbsttest im dritten Re-Review gemessen) und die im grünen Lauf enthaltenen.

**Nachweis-Pflichten:**

| Pflicht | Befund |
|---|---|
| `make gates` grün | ja (siehe oben, auch `FORCE=1`) |
| `make docs-check`, `make flaeche`, `make kopplung-zahlen` | grün |
| `make workflow-lint` | grün |
| Review durchgeführt, Report liegt vor, zweiter Review bei Code | ja: ein Review und drei Re-Reviews in frischem Kontext, zuletzt ohne HIGH, MEDIUM, LOW |
| Doku-Update (`releasing.md` Schritt 8 und 9, Jobs und Rechte, Teilfehler, Tag-Schutz mit `contents: write`, Ungemessen; `harness/README.md` Zeilen; Kopfkommentare) | ausgeführt; `docs/user/` bleibt wie geplant unverändert. Zelle in `harness/README.md` zu `skript-selbsttest` s. Finding V-2 |
| Closure-Notiz mit Lerneintrag | **offen**: §7 trägt „Wird bei der Closure gefüllt“ (Planner) |
| Beobachtungs-Register fortgeschrieben | **offen** (Planner); der Plan benennt die Treffer in §8 |
| Jedes Risiko aus §6 mit Ausgang | **offen** (§6 trägt alle sechs „offen bis zur Closure“); Entwurf siehe unten |
| Drei Paarungen (Anker · Folge-Slice · Register) | **offen**; Folge-Slices `slice-release-im-tag-lauf-messen` und `slice-release-nachtraeglich-bestandstags` liegen in `open/` |

Die DoD-Haken im Slice sind unberührt (alle offen); sie setzt der Planner.

## Entscheidungs-Konformität (ADR-0050, ADR-0048, ADR-0049)

| Entscheidung | Befund |
|---|---|
| [ADR-0050](../plan/adr/0050-github-release-im-tag-lauf.md) Entscheidung 1 (Job `release`, Rechte, Token-Fenster, `persist-credentials: false`) | konform (Text) |
| Entscheidung 1, Teilfehler und Wiederholbarkeit | Vorabprüfung „Release besteht“ vorhanden; Verhalten bei „Re-run failed jobs“ nicht gemessen, in `releasing.md` so benannt |
| Entscheidung 4 (feste Notiz, keine Aussage über Ungemessenes) | konform; Zeiger auf `lib/`-Lizenzen oder Gleichheit fehlt bewusst |
| Entscheidung 5 (nachträglich, ohne Dateien) | Modus vorhanden, begrenzt auf `v0.1.0` und `v0.1.1`; das Anlegen selbst bleibt dem Folge-Slice |
| Entscheidung 6 (kein Gate, Vorschau im Dispatch) | konform: weder `release-dateien` noch das Skript in einer Gate-Liste oder `e2e.mk` (gelesen und vom Selbsttest geprüft); `vorschau` fährt `VORSCHAU=1` |
| [ADR-0048](../plan/adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) (Rechte je Job, kein Gate), [ADR-0049](../plan/adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) Entscheidungen 2a und 3a (Jobs unabhängig, Secrets nur im Schritt) | `publish` und `publish-cloudsmith` unverändert; kein `continue-on-error`; `release` ohne Cloudsmith-Secret |
| AGENTS §3.2 bis §3.8 | keine Verletzung gefunden; Kommentare tragen Zusage und Grenze; Aussagen über GitHub sind als „ungemessen“ gekennzeichnet |

## Plan-vs-Code-Diff

| Plan (§3) | Ist |
|---|---|
| `veroeffentlichung.yml` update | ja |
| Eingangsskript neu | `tools/release-anlegen.sh` |
| Notiz-Vorlage neu (nicht im Platzhalter-Linkbereich des Doku-Gates) | `tools/release-notiz.vorlage`, `docs-check` Exit 0 |
| Selbsttest, `skript-selbsttest.mk`, `Dockerfile` | neuer Selbsttest `tools/release-anlegen-selbsttest.sh` und `Dockerfile` ja; **`tools/mk/skript-selbsttest.mk` nicht geändert** (Kommentar und Beschreibung nennen die neuen Skripte nicht), s. V-1 |
| `tools/stempel/*.eingaenge` falls nötig | nicht geändert; `make stempel-eingaenge-pruefung` grün, also nicht nötig |
| `releasing.md`, `harness/README.md` | ja |

Nicht im Plan und im Diff: vier Review-Reports unter `docs/reviews/` (nach Ablauf Schritt 2 und 3 vorgesehen).

## Findings

| ID | Kat. | Befund | Pfad |
|---|---|---|---|
| V-1 | LOW | Der Kopf von `tools/mk/skript-selbsttest.mk` und die Beschreibung des Targets (`##`-Zeile, also `make help`) zählen die geprüften Skripte auf und nennen `tools/release-anlegen.sh` samt Workflow-Text nicht (auch `tools/release-selbsttest.sh` fehlt dort, schon vor diesem Slice). Der Plan führte die Datei als „update“. Der Selbsttest läuft; die Aufzählung ist unvollständig. | `tools/mk/skript-selbsttest.mk` Zeilen 1 bis 7 |
| V-2 | LOW | Die Zelle `make skript-selbsttest` in `harness/README.md` nennt `release-*.sh` und „Workflow-Text“, aber `test-lauf.sh` nur noch „Argumente“ (zuvor „Argumente, Modi“) und `publish.sh` ohne „je Ziel“: bei der Kürzung gingen zwei Aussagen verloren, die der Selbsttest weiter belegt (`test-lauf-selbsttest.sh` hält Modi, `publish-selbsttest.sh` je Ziel). Gleiche Zelle wie S-3 des zweiten Re-Reviews. | `harness/README.md` §Sensors |
| V-3 | INFO | T-1 des dritten Re-Reviews bleibt: die Schleife über die echten Dateien der Abwesenheitsprüfung hat keine Gegenprobe (Entfernen von `pruefe` bleibt grün), Zeilenfortsetzung, `?=` und `::=` erfasst das Muster nicht. Ich habe das nicht neu gemessen, nur gelesen: Schleife und Muster stehen so im Skript. Im Repo sind beide Formen nicht vorhanden. | `tools/release-anlegen-selbsttest.sh` Abschnitt Abwesenheit |
| V-4 | INFO | Closure-Notiz, Register, Risiko-Ausgänge und Paarungen fehlen (Planner-Handlung, kein Mangel des Bauens). Der Closure-Note-Skill prüft nur `done/`; hier gab es noch nichts zu prüfen (geprüft, ohne Befund: keine Slice in `done/` im Diff). | Slice §6, §7 |

Kein HIGH, kein MEDIUM.

## Risiken aus §6 — Vorschlag für die Ausgänge (setzt der Planner)

1. Stub belegt nicht GitHub: weiter offen, Träger [slice-release-im-tag-lauf-messen](../plan/planning/open/slice-release-im-tag-lauf-messen.md).
2. Workflow-Aussagen ohne Sensor: teilweise entfallen (Rechte, `needs`, Token-Ort, Pins als Form, Bedingung als Text liest der Selbsttest); weiter offen bleibt, was der Text nicht zeigt: Pins gegen die echte Action, Wirkung zur Laufzeit.
3. Restrisiko des neuen Rechts: weiter offen, beim Nutzer (Tag-Schutz `v*`).
4. Tabelle der Pflicht-Sensoren ohne Zeile für Werkzeug, Workflow, Build-Datei: weiter offen, Sache des Nutzers.
5. Hostabhängigkeit `gh`: weiter offen; lokal nur der Stub, auf dem Runner angenommen.
6. Linkziele der Pakete: entfallen für die zwei Registry-Adressen (Quelle: Messprotokolle); „anonym ladbar“ steht nicht in der Notiz.

## Ausdrücklich ungemessen (nicht Gegenstand dieses Slice, nicht belegt)

- `gh` auf dem Runner-Image vorhanden, und ob `gh` das Repository ohne `GH_REPO` aus dem Remote des Klons findet.
- Das Recht `contents: write` für `GITHUB_TOKEN` (und die Einstellung „Workflow permissions“ des Repositories).
- Das Anlegen eines Releases mit drei Anhängen in einem Aufruf, Verhalten bei Abbruch (Release ohne Anhänge?), unveränderliche Releases.
- „Re-run failed jobs“: läuft `release` im selben Lauf nach; Re-run des Jobs `release` bei bestehendem Release.
- Anonyme Ladbarkeit der Anhänge; Gleichheit von Release-Jar und Paket-Jar.
- Pins gegen die echten Actions (nur die Form der SHA ist geprüft, und dass sie dieselbe ist wie in `gates.yml`); Wirkung von Bedingung, Reihenfolge und Rechten zur Laufzeit.
- `make release-dateien VORSCHAU=1` im Dispatch-Lauf der CI.
- Laufzeit des Jobs `release`.
- Ob `gh release list --limit 1000` alle Releases nennt (im Kopf des Skripts als nicht gemessen vermerkt; kein Repo mit mehr als 1000 Releases in Sicht).

## Fragen an den Nutzer

1. **Host-Skript und `gh` gegen AGENTS §3.1:** `tools/release-anlegen.sh` läuft im Job auf dem Runner-Host (`bash`, `git`, `gh`), nicht in Docker, anders als `make publish`; lokal läuft nur der Selbsttest in Docker. Der Plan nennt das als Risiko 5. Ist die Hostausführung im CI-Job für ein Werkzeug außerhalb der Gates gewollt (§3.1 betrifft lokale Toolchain), oder soll das Skript in einen Container?
2. **`--ohne-dateien` nur für `v0.1.0` und `v0.1.1` fest verdrahtet:** richtig so ([ADR-0050](../plan/adr/0050-github-release-im-tag-lauf.md) Entscheidung 5), aber ein dritter Bestandstag bräuchte eine Änderung am Skript und am Selbsttest. Soll das so bleiben?
3. **`gh release list --limit 1000`:** reicht die Grenze, oder soll die Abfrage gezielt nach dem Tag fragen (`gh release view`), was die Grenze erübrigte, aber eine andere Fehlerunterscheidung (nicht gefunden gegen Fehler) braucht?

## Summary

Alle drei Liefer-Punkte sind am Code, am frischen Selbsttest und am Linter erfüllt; alle Sensoren Exit 0 (Selbsttest frisch ohne Cache, workflow-lint, docs-check, flaeche, kopplung-zahlen, clean-build, e2e, gates auch mit `FORCE=1`). `clean-build` und `e2e` haben für diese Änderung nur Nebenwirkungs-Aussagekraft (kein Eingriff in `src/` oder `testserver/`). Die Nachweis-Pflichten zur Closure (Notiz, Register, Risiko-Ausgänge, Paarungen) stehen aus. Findings: zwei LOW (V-1, V-2), zwei INFO. Nichts gegen GitHub belegt; was nur der echte Lauf zeigt, steht oben und bleibt beim Folge-Slice [slice-release-im-tag-lauf-messen](../plan/planning/open/slice-release-im-tag-lauf-messen.md).
