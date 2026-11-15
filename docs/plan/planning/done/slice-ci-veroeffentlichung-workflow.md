# Slice slice-ci-veroeffentlichung-workflow: Workflow zum Veröffentlichen der Bibliothek bei Tag-Push, Nachzug der Release-Anleitung, Messung per Dispatch-Lauf

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** ohne Welle — keine Closure-Bedingung, die von der DoD dieses Slice verschieden ist.

**Bezug:** [TDS-RB-05](../../../../spec/lastenheft.md#tds-rb-05--lizenz), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) (Scope), [ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) (`Accepted` 2026-11-14, Folgepflicht 1), [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) (Entscheidung 6 ohne die Wahl 6a), [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md) (Muster: Lauf mit Außenwirkung außerhalb der Gate-Kette)

**Berührte Spec-Stellen:** —

**Verantwortlich:** pt9912 (Anspruch auf `main`, Implementer im Worktree).

**Autor:** Planner-Agent, Auftrag des Nutzers 2026-11-14. **Datum:** 2026-11-14.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar.

**Ziel:** Ein Tag-Push `v*` auf einen Commit von `main` führt in der CI `make gates` und danach `make publish` aus, ohne dass ein persönliches Token gebraucht wird; die Release-Anleitung nennt diesen Weg, und der Workflow ist durch einen Dispatch-Lauf auf GitHub (Bau der Stage `publish`, ohne Veröffentlichung) gemessen ([ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Entscheidungen 1, 2 und 4).

**Warum ein eigener Slice vor der ersten Veröffentlichung:** Der Workflow hat Schreibrecht (`packages: write`) und ist ohne Wirkung nach außen prüfbar, soweit der Dispatch-Lauf trägt; die Veröffentlichung selbst ist nicht rücknehmbar und gehört in [slice-maven-erste-veroeffentlichung](../done/slice-maven-erste-veroeffentlichung.md). Dieser Slice **veröffentlicht nichts**, durch keine Rolle.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- **Die erste echte Veröffentlichung**, der Tag `v<Version>`, der Release-Commit und die Probe eines Konsumenten gegen die Registry — [slice-maven-erste-veroeffentlichung](../done/slice-maven-erste-veroeffentlichung.md). Die Handlung wirkt nach außen und verbraucht eine Version ([ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Entscheidung 4: Probe-Veröffentlichungen auf Test-Tags sind ausgeschlossen).
- **Änderungen an `tools/publish.sh`, an der Stage `publish`, an `tools/mk/publish.mk` und an `.github/workflows/gates.yml` und `e2e.yml`** — [ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) lässt sie unverändert (Entscheidung 1: das Skript bleibt der einzige Eingang; offene Frage 2 entschieden: keine Sperre außerhalb der CI; offene Frage 1 entschieden: Job `gates` im neuen Workflow statt `workflow_call`). Ein Befund an ihnen ist ein Rückweg, kein Nebenbei.
- **Ein Gate oder ein Sensor für den Workflow** — der Workflow ist kein Gate ([ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Entscheidung 3); `make publish` kommt in keine Gate-Liste, und `make workflow-lint` wird keines. Die Reihenfolge `gates` vor `publish`, die SHA-Pins, `permissions` und das Fehlen von `--url` prüft nur der Review (Textabgleich); ein Sensor dafür ist nicht Teil dieses Slice.
- **Das Setzen des Tag-Schutzes `v*` und der Workflow-Berechtigungen des Repositories** — Einstellungen bei GitHub, die der Nutzer setzt und die kein Gate prüft ([ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Konsequenzen). Dieser Slice liefert nur die Anleitung dazu.
- **Eine Umgebung mit Pflicht-Freigabe, Maven Central, Signierung, ein zweites veröffentlichtes Modul** — von der ADR verworfen oder an einen Trigger gebunden; keiner ist eingetreten.
- **Die Nutzer-Anleitung für Konsumenten (`read:packages`) und `tools/publish-probe.sh`** — bleiben bei [slice-maven-erste-veroeffentlichung](../done/slice-maven-erste-veroeffentlichung.md) (Nutzerentscheidung 2026-11-14).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**.

Liefer-Punkte (drei):

- [x] **Workflow-Datei:** `.github/workflows/veroeffentlichung.yml` nach [ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Entscheidung 2 und 4: Trigger `push: tags: ['v*']` und `workflow_dispatch` (kein Branch-Push, kein `pull_request`); Kopf `permissions: {}`; Job `gates` (`contents: read`, derselbe Checkout wie `gates.yml`: `fetch-depth: 0`, `persist-credentials: false`, derselbe SHA-Pin, `make gates`); Job `publish` (`needs: gates`, `contents: read` und `packages: write`, nur dort; läuft nur beim Tag-Push, Bedingung in §3; bricht, wenn der Tag-Commit nicht in `origin/main` liegt, `git merge-base --is-ancestor`; `GITHUB_TOKEN` aus `secrets.GITHUB_TOKEN` nur im `env:` des einen Schritts `make publish`; kein `--url`, kein Wert als Argument, kein `--info`/`--debug`); Job `vorschau` (nur bei `workflow_dispatch`, `make publish-vorschau`, kein Token); `concurrency` je Tag mit `cancel-in-progress: false`; jede Action per Commit-SHA; im Kopf der Datei der Zeiger auf `gates.yml` (Kopplung der wiederholten Schritte). `make workflow-lint` grün, Textabgleich im Review.
- [x] **Nachzug von Anleitung und Sensor-Index:** [`releasing.md`](../../../maintainer/releasing.md) Schritt 1 (die Setzung „Lauf von `gates.yml` auf dem Release-Stand“ bleibt der Nachweis, der Job `gates` im Workflow ist die Absicherung), Schritt 8 (der Tag-Push löst die Veröffentlichung aus; Tag nur auf einem Commit von `main`) und Schritt 9 (die CI ist der Weg, `make publish` mit persönlichem Token nicht mehr; der Satz „belegt gegen einen Dummy-Server, nicht gegen GitHub“ bleibt, bis [slice-maven-erste-veroeffentlichung](../done/slice-maven-erste-veroeffentlichung.md) schließt), samt einer **Anleitung zum Tag-Schutz `v*`**, den der Nutzer **vor dem ersten Tag** im Repository setzt (der Wortlaut folgt der GitHub-Dokumentation, am Tag des Schreibens abgerufen, sonst als ungeprüft gekennzeichnet, `AGENTS.md` §3.8); eine Zeile in [`harness/README.md`](../../../../harness/README.md) §Sensors (Werkzeuge-Tabelle: Workflow, kein Gate, Bindung [ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md)) und die Nennung im Absatz „Aktueller Lauf-Status“.
- [x] **Messprotokoll des Dispatch-Laufs:** ein Lauf von `workflow_dispatch` auf GitHub (Jobs `gates` und `vorschau`, `publish` übersprungen) ist gemessen, im Messprotokoll unter `docs/messungen/` (`AGENTS.md` §5 Regel 7) mit Datum, Lauf-Kennung, Ausgang je Job, Dauer von `gates` und `vorschau`, Runner-Image und -Version und POM-Ausgabe; kein Token, kein Netz zu GitHub Packages. Protokoll: [veroeffentlichung-dispatch-2026-11-15.md](../../../messungen/veroeffentlichung-dispatch-2026-11-15.md) (Lauf 43619591947). Die Docker-Version des Runners ist **bewusst entfallen**: Entscheidung des Nutzers am 2026-11-15 (Zielbild angepasst, Code nicht angeglichen), weil das Laufprotokoll sie nicht ausgibt, Runner-Image und -Version belegt sind und die Docker-Version keine Aussage des Slice ändert; ein Workflow-Edit samt zweitem Lauf stünde in keinem Verhältnis. Sie ist kein offener Punkt und keine Schuld. Das Protokoll benennt **ausdrücklich, was der Lauf nicht belegt** ([ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Entscheidung 4): dass `GITHUB_TOKEN` mit `packages: write` schreiben darf, die Zuordnung des Pakets zum Repository, den Klon eines Tag-Push für `tools/publish.sh` und die Maskierung im Log.

**Wer den dritten Punkt nicht liefern kann:** Der Implementer kann den Lauf **nicht** fahren (er hat keinen Zugriff auf GitHub Actions und keinen Push). Ablauf: Der Implementer liefert Punkt 1 und 2 und lässt sie reviewen; der Nutzer mergt nach `main` (`workflow_dispatch` erscheint für eine Datei erst auf dem Standardzweig) und löst den Lauf aus (Oberfläche von GitHub Actions oder `gh workflow run`); der Nutzer gibt die Lauf-Kennung (und, wenn er will, das Log) weiter; erst dann wird das Messprotokoll geschrieben. Bis dahin steht der Slice in `in-progress/` mit offenem drittem Punkt und schließt nicht.

Nachweis-Pflichten für alle drei Punkte:

- [x] `make workflow-lint` grün, `make skript-selbsttest` grün (die Datei unter `.github/workflows/` ist Eingang der Klasse `werkzeug`), `make gates` grün; bei Doku `make docs-check`, `make flaeche`, `make kopplung-zahlen` ([`harness/README.md`](../../../../harness/README.md) §Minimal agent workflow). Kein `make e2e`, kein `make clean-build`.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben; **kein Zaehler wird gesetzt**. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `.github/workflows/veroeffentlichung.yml` | neu | Workflow nach [ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Entscheidung 2 und 4 |
| `docs/maintainer/releasing.md` | update | Schritte 1, 8, 9, Anleitung Tag-Schutz; der Kopf-Absatz „eine Version ist nicht veröffentlicht“ bleibt wahr und bleibt stehen |
| `harness/README.md` | update | Zeile in §Sensors (Werkzeuge) und Lauf-Status |
| `docs/messungen/` (Messprotokoll) | neu, nach dem Lauf | Dispatch-Lauf mit Grenzen |
| `tools/stempel/werkzeug.eingaenge`, `tools/skript-selbsttest*` | unverändert erwartet | `.github/workflows` ist schon Eingang der Klasse `werkzeug`; der Befund „Liste muss wachsen“ wäre ein Rückweg |

**Bedingung des Jobs `publish` (Abweichung vom Wortlaut der ADR, Absicht des Planners, vom Review zu prüfen):** [ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Entscheidung 2 nennt `if: github.ref_type == 'tag'`. Wird `workflow_dispatch` auf einem Tag-Ref ausgelöst, ist `ref_type` ebenfalls `tag`, und `publish` liefe. Der Plan verlangt deshalb `github.event_name == 'push' && github.ref_type == 'tag'`: ein Dispatch veröffentlicht nie, wie Entscheidung 4 es sagt. Das ist die engere Fassung derselben Aussage, keine Lockerung.

**Nachbesserung nach dem Review:** Die LOW-Findings 1 bis 3 des Reviews zu 0144fb8a sind als reine Text- und Kommentaränderung behoben (Workflow-Kommentare, `releasing.md` Schritt 8 und 9), ohne Verhaltensänderung. Ein zweiter Review dazu fand nicht statt.

**Reihenfolge mit den Nachbarn in `releasing.md`:** dieser Slice zuerst; [slice-release-entscheidungen](../done/slice-release-entscheidungen.md) beginnt erst, wenn dieser Slice nicht mehr in `in-progress/` liegt (gleiche Datei, getrennte Abschnitte), [slice-maven-erste-veroeffentlichung](../done/slice-maven-erste-veroeffentlichung.md) beginnt nach seiner Closure.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): (1) [ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) steht auf `Accepted` (erfüllt, 2026-11-14). (2) Ein Verantwortlicher ist eingetragen. Der Start verlangt **keine** Bestätigung einer Veröffentlichung: dieser Slice veröffentlicht nichts.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß): der Befund am Dispatch-Lauf verlangt eine Änderung an `tools/publish.sh`, an der Stage `publish` oder an `gates.yml`; sie wird ein eigener Slice, dieser liefert den Workflow.
- `in-progress` → `open` (blockiert): der Nutzer führt den Dispatch-Lauf nicht aus oder der Lauf scheitert an einer Einstellung des Repositories, die nur der Nutzer ändert; der Stand der Punkte 1 und 2 bleibt im Zweig.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag.

Beobachtbar: (1) Der Dispatch-Lauf von `veroeffentlichung.yml` auf `main` ist auf GitHub abgeschlossen: Jobs `gates` und `vorschau` `success`, `publish` übersprungen; das Messprotokoll nennt die Lauf-Kennung. (2) Alle Punkte der DoD erledigt, `make gates` grün, Review-Report liegt vor, Closure-Notiz mit Lerneintrag geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang.

- **Rechte von `GITHUB_TOKEN`:** ob `permissions: packages: write` die erste Veröffentlichung eines noch nicht bestehenden Pakets zulässt, ist nach Dokumentationsstand gelesen, nicht gemessen; der Dispatch-Lauf belegt es nicht. — **Ausgang:** weiter offen; Träger der Messung: [slice-maven-erste-veroeffentlichung](../done/slice-maven-erste-veroeffentlichung.md).
- **Zuordnung des Pakets zum Repository** und **Maskierung des Tokens im Log** sind ungemessen; beide hängen an einer echten Veröffentlichung. — **Ausgang:** weiter offen; derselbe Träger.
- **Klon eines Tag-Push:** `tools/publish.sh` verlangt den Tag auf `HEAD` und einen sauberen Baum; dass der Runner beides im Klon eines Tag-Push liefert (losgelöster `HEAD`, Tag lokal, `origin/main` vorhanden bei `fetch-depth: 0`), ist ungemessen und vom Dispatch-Lauf nicht erreichbar (kein Tag-Ref). — **Ausgang:** weiter offen; derselbe Träger.
- **Doppelter Lauf von `make gates`** bei jedem Tag (in `gates.yml` und im Job `gates`): akzeptiertes Negativ der ADR; die Laufzeit auf dem Runner wird mit dem Dispatch-Lauf erstmals für diesen Job gemessen. — **Ausgang:** eingetreten, als akzeptiertes Negativ: `gates` brauchte im Dispatch-Lauf rund 13 min 9 s (Einzelmessung, [Protokoll](../../../messungen/veroeffentlichung-dispatch-2026-11-15.md)); die Verdopplung je Tag bleibt, die ADR sieht sie vor.
- **Der Dispatch-Lauf hängt am Nutzer** (Merge nach `main`, Auslösen): der Slice kann ohne ihn nicht schließen, und der Implementer kann den Lauf nicht ersetzen. — **Ausgang:** eingetreten und aufgelöst: der Nutzer hat gemergt und den Lauf 43619591947 am 2026-11-14 ausgelöst.
- **Tag-Schutz nicht gesetzt:** wer `v*` auf `main` pushen darf, löst eine Veröffentlichung aus; die Einstellung liegt bei GitHub und ist von keinem Sensor geprüft (akzeptiertes Negativ der ADR). Dieser Slice liefert die Anleitung, nicht den Zustand. — **Ausgang:** Anleitung geliefert; der Zustand ist laut Angabe des Nutzers im Auftrag der Closure gesetzt (von mir nicht geprüft, von keinem Gate geprüft). Weiter offen bleibt die Bestätigung im Start von [slice-maven-erste-veroeffentlichung](../done/slice-maven-erste-veroeffentlichung.md).
- **Wortlaut der Anleitung zum Tag-Schutz** gegen die Oberfläche von GitHub: ungeprüft, solange der Autor sie nicht abgerufen hat (`AGENTS.md` §3.8). — **Ausgang:** weiter offen: `releasing.md` kennzeichnet den Wortlaut als am Repository ungemessen, der Verifier konnte den Abruf nicht gegenprüfen; der Nutzer, der den Schutz setzt, gleicht ihn an der Oberfläche ab.

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register (vorhandene `BEO-<KUERZEL>/<slug>` **zitieren**) ·
`grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln.

Geschlossen 2026-11-15 (Planner, Slice-Closure ohne Welle). Gemergter Stand: `5058c91b` auf `main` (Merge des Zweigs, `gates` auf `main` `success`, Lauf 43616198230 nach Wiederholung, siehe Register); Messung: Dispatch-Lauf 43619591947 auf diesem Stand.

**Geliefert.** (1) `.github/workflows/veroeffentlichung.yml` nach [ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md): Trigger Tag-Push `v*` und Dispatch, Kopf `permissions: {}`, Jobs `gates`, `publish` (nur bei Push auf Tag, nach `gates`, Ancestor-Prüfung vor dem Token, `packages: write` nur dort) und `vorschau` (nur Dispatch, ohne Token), `concurrency` je Tag. (2) `releasing.md` Schritte 1, 8, 9 und Abschnitt Tag-Schutz `v*`, Zeile und Lauf-Status in `harness/README.md`. (3) Messprotokoll [veroeffentlichung-dispatch-2026-11-15.md](../../../messungen/veroeffentlichung-dispatch-2026-11-15.md): Lauf 43619591947, `gates` und `vorschau` `success`, `publish` `skipped`, `gates` rund 13 min 9 s, `vorschau` rund 1 min 42 s, POM ausgegeben, kein Token im Protokoll, kein Zugriff auf GitHub Packages. Nichts wurde in eine Registry geschrieben, kein Tag gesetzt, kein Token verwendet.

**Belege.** [Review](../../../reviews/2026-11-14-0144fb8a-slice-ci-veroeffentlichung-workflow-review.md) (`0144fb8a`): 0 HIGH, 0 MEDIUM, 3 LOW, 3 INFO; die LOW sind in `53f38738` als reine Text- und Kommentaränderung behoben. [Verifikation](../../../reviews/2026-11-14-53f38738-slice-ci-veroeffentlichung-workflow-verifikation.md) (`53f38738`): konform, DoD-Punkt 3 erwartungsgemäß offen, 1 LOW (V-LOW: `GITHUB_ACTOR` in `releasing.md` Schritt 9 als Tatsache, behoben in `82ad0fca`), 3 INFO. Kein `make clean-build`, kein `make e2e` (Doku und Workflow, keine Änderung unter `src/` oder `testserver/`; [`harness/README.md`](../../../../harness/README.md) §Minimal agent workflow). Die Closure fuhr `make docs-check`, `make flaeche`, `make kopplung-zahlen` und `make gates` (Ergebnisse im Bericht der Closure).

**Was anders lief.**
- **Docker-Version des Runners: Anforderung entfallen, Zielbild angepasst.** DoD Punkt 3 verlangte sie im Messprotokoll; das Laufprotokoll gibt sie nicht aus. Entscheidung des Nutzers am 2026-11-15, ausdrücklich: die Anforderung wird aus der DoD gestrichen, der Workflow nicht geändert. Begründung: Runner-Image und -Version sind belegt, die Docker-Version ändert keine Aussage des Slice, ein Workflow-Edit samt zweitem Lauf stünde in keinem Verhältnis. DoD-Text und Protokoll führen sie als bewusst entfallen (Protokoll Punkt 9), nicht als offene Schuld. [ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) (`Accepted`, unverändert) nennt „Docker-Version“ in ihrer Liste des Ungemessenen; das ist eine Aussage über den Stand der Messung, keine Folgepflicht dieses Slice.
- **Review-Bericht und `make docs-check`:** der Report trug 11 Kennungen ohne Link, der Review meldete Exit 0 für den Stand vor dem Report; der Verifier reproduzierte Exit 2. Behoben in `53f38738` (der Implementer änderte dafür einen Report-Text; Befunde und Urteil unberührt, Verifier INFO).
- **Verhalten auf dem Runner bleibt gelesen:** der Dispatch-Lauf kann Tag-Klon, Schreibrecht, Maskierung und Zuordnung nicht zeigen; das stand im Plan und im Protokoll.

**Ungemessen, übernommen.** Die Liste 1 bis 8 und 10 aus dem Protokoll („Was dieser Lauf nicht belegt“: Schreibrecht von `GITHUB_TOKEN` auch für das erste Paket, Zuordnung zum Repository, Maskierung eines echten Tokens, Klon eines Tag-Push, Ancestor-Prüfung im Job `publish` und ihr Bruch bei einem Tag außerhalb von `main`, Tag-Schutz mit Bypass-Berechtigten, `GITHUB_ACTOR` auf dem Runner, Einstellung „Workflow permissions“ des Repositories, `concurrency` bei zwei Läufen desselben Tags) bleibt unverändert und geht an [slice-maven-erste-veroeffentlichung](../done/slice-maven-erste-veroeffentlichung.md), dessen erste echte Veröffentlichung sie misst. Punkt 9 (Docker-Version) entfällt, er geht nirgendwohin.

**Register.** Fortgeschrieben, kein Zähler gesetzt, Zählung folgt aus den Dateien:
- `BEO-TDS/wanduhr-tests-flackern-unter-last`: Datei `evidence/slice-ci-veroeffentlichung-workflow.md`, 13 Dateien; `ListenerAbortTest.kt:112` rot im CI-Lauf 43616198230 auf dem gehosteten Runner (erster Auftritt dort), Wiederholung grün, Test außerhalb des Diffs. Der Eintrag ist schon `verkörpert` (`implement-slice` Schritt 22a und Reviewer-Skill, Anker `seit welle-betrieb-diagnose`); der Ausgang „behoben“ ist **nicht** gesetzt, der Planner ändert ihn nicht. Hinweis an den Nutzer: der Auftritt auf einem Runner ohne künstliche Last stützt die Lesart „Ordnungswette“ statt „Last des Hosts“ und betrifft eine Methode, von der nicht geprüft ist, ob der Träger [slice-listener-abort-test-ordnungswette](../done/slice-listener-abort-test-ordnungswette.md) sie erfasst hat; ob ein Folge-Slice nötig ist, entscheidet der Nutzer.
- `BEO-TDS/folgepflicht-in-dod-ohne-messweg`: Datei `evidence/slice-ci-veroeffentlichung-workflow.md` (Docker-Version als DoD-Größe ohne Messweg), 2 Dateien, offen.
- `BEO-TDS/aussagen-ueber-messung-hinaus`: Datei `evidence/slice-ci-veroeffentlichung-workflow.md` (Review LOW 1 bis 3, V-LOW), schon verkörpert in `AGENTS.md` §3.8.
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten`: Datei `evidence/slice-ci-veroeffentlichung-workflow.md` (Review-Report ohne Links, `docs-check`-Aussage veraltet); Ausgang `gestrichen` unberührt.
- Gesichtet, kein Auftreten: `nachweis-von-der-falschen-seite-der-grenze` (der Plan hat die Grenze vorab genannt, der Lauf zeigt sie im Protokoll), `skript-pruefung-ohne-rot-gegenprobe` (kein Skript geändert), `einmal-rot-ohne-gesicherte-ausgabe` (der Dispatch-Lauf war nicht rot).

**Offene Nutzerentscheidungen (nicht vom Planner entschieden).**
1. Ob die Methode `ListenerAbortTest … ruft den Abbruch nach der Frist …` einen Wartepunkt auf den Abbruch bekommt (Folge-Slice) oder der Auftritt im Register bleibt.
2. Ob der Tag-Schutz `v*` vor dem ersten Tag gegen die Oberfläche von GitHub abgeglichen wird (Wortlaut der Anleitung ungeprüft).

**Lerneintrag (Steering Loop).**

- **Benannte Planungslücke, kein neuer Sensor:** eine DoD-Größe braucht ihren Messweg im Plan, sonst bleibt der Punkt offen, obwohl der Lauf da ist. Die Docker-Version stand in der DoD und im Protokoll-Soll, das Laufprotokoll gibt sie nicht aus; der Plan hatte den Erzeuger nicht geprüft. Gezählt in `BEO-TDS/folgepflicht-in-dod-ohne-messweg` (2×, unter der Schwelle), nicht verkörpert. Das Zielbild wurde angepasst, nicht der Code (Nutzerentscheidung 2026-11-15).
- **Bestätigt, keine neue Regel:** Aussagen breiter als der Beleg treten dort auf, wo Verhalten auf dem Runner nur gelesen ist (vier Fundstellen, `AGENTS.md` §3.8 hat sie gefunden). Neu an diesem Slice: dieselbe Zusage stand an zwei Orten (Workflow-Kommentar, `releasing.md`), der Review behob einen, der Verifier fand den zweiten; die Regel „zahl-an-mehreren-orten“ gilt für Aussagen ebenso. Kein Sensor.
- **Grenze der Aussage in einem Review-Report:** eine Exit-Meldung eines Gates in einem Bericht unter `docs/` gilt für den Stand vor dem Bericht, weil der Bericht selbst Eingang von `make docs-check` ist. Das Gate fängt es (der Bericht wäre `make gates` rot gewesen); die Meldung im Bericht trägt es nicht. Gezählt in `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten`, nicht verkörpert.
- **Der Schnitt hat getragen:** Workflow und Dispatch-Messung vor der ersten echten Veröffentlichung zu trennen ließ den Workflow ohne Wirkung nach außen prüfen; was er nicht zeigen kann, steht benannt im Folge-Slice. Folge-Slice-Paarung: `done/slice-maven-erste-veroeffentlichung.md`.
- Spec-Lücke: keine neue. Verkörpert wurde mit diesem Slice keine Regel, daher kein `liegt in` und kein Herkunfts-Anker.

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung.

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration führt `*` als einzige Sub-Area (Greenfield).

**Vorgelagert — offene Beobachtungen sichten:** Register (`../observations/BEO-TDS/`, Stand des gemergten `main` vom 2026-11-14) nach Treffern durchgegangen: `nachweis-von-der-falschen-seite-der-grenze` (2×; der Dispatch-Lauf belegt den Bau auf dem Runner, nicht das Schreiben — die Grenze steht in §2 Punkt 3), `skript-pruefung-ohne-rot-gegenprobe` (1×; für den Workflow gibt es keinen Selbsttest, nur Lint und Review — in §1 benannt) und `einmal-rot-ohne-gesicherte-ausgabe` (2×; ein roter Dispatch-Lauf wird vor dem Wiederholen mit Lauf-Kennung und Ausgabe gesichert). Keine weiteren Treffer gelesen.

Alle berührten Sub-Areas GF.
