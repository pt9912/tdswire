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

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** Planner-Agent, Auftrag des Nutzers 2026-11-14. **Datum:** 2026-11-14.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar.

**Ziel:** Ein Tag-Push `v*` auf einen Commit von `main` führt in der CI `make gates` und danach `make publish` aus, ohne dass ein persönliches Token gebraucht wird; die Release-Anleitung nennt diesen Weg, und der Workflow ist durch einen Dispatch-Lauf auf GitHub (Bau der Stage `publish`, ohne Veröffentlichung) gemessen ([ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Entscheidungen 1, 2 und 4).

**Warum ein eigener Slice vor der ersten Veröffentlichung:** Der Workflow hat Schreibrecht (`packages: write`) und ist ohne Wirkung nach außen prüfbar, soweit der Dispatch-Lauf trägt; die Veröffentlichung selbst ist nicht rücknehmbar und gehört in [slice-maven-erste-veroeffentlichung](../open/slice-maven-erste-veroeffentlichung.md). Dieser Slice **veröffentlicht nichts**, durch keine Rolle.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- **Die erste echte Veröffentlichung**, der Tag `v<Version>`, der Release-Commit und die Probe eines Konsumenten gegen die Registry — [slice-maven-erste-veroeffentlichung](../open/slice-maven-erste-veroeffentlichung.md). Die Handlung wirkt nach außen und verbraucht eine Version ([ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Entscheidung 4: Probe-Veröffentlichungen auf Test-Tags sind ausgeschlossen).
- **Änderungen an `tools/publish.sh`, an der Stage `publish`, an `tools/mk/publish.mk` und an `.github/workflows/gates.yml` und `e2e.yml`** — [ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) lässt sie unverändert (Entscheidung 1: das Skript bleibt der einzige Eingang; offene Frage 2 entschieden: keine Sperre außerhalb der CI; offene Frage 1 entschieden: Job `gates` im neuen Workflow statt `workflow_call`). Ein Befund an ihnen ist ein Rückweg, kein Nebenbei.
- **Ein Gate oder ein Sensor für den Workflow** — der Workflow ist kein Gate ([ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Entscheidung 3); `make publish` kommt in keine Gate-Liste, und `make workflow-lint` wird keines. Die Reihenfolge `gates` vor `publish`, die SHA-Pins, `permissions` und das Fehlen von `--url` prüft nur der Review (Textabgleich); ein Sensor dafür ist nicht Teil dieses Slice.
- **Das Setzen des Tag-Schutzes `v*` und der Workflow-Berechtigungen des Repositories** — Einstellungen bei GitHub, die der Nutzer setzt und die kein Gate prüft ([ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Konsequenzen). Dieser Slice liefert nur die Anleitung dazu.
- **Eine Umgebung mit Pflicht-Freigabe, Maven Central, Signierung, ein zweites veröffentlichtes Modul** — von der ADR verworfen oder an einen Trigger gebunden; keiner ist eingetreten.
- **Die Nutzer-Anleitung für Konsumenten (`read:packages`) und `tools/publish-probe.sh`** — bleiben bei [slice-maven-erste-veroeffentlichung](../open/slice-maven-erste-veroeffentlichung.md) (Nutzerentscheidung 2026-11-14).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**.

Liefer-Punkte (drei):

- [ ] **Workflow-Datei:** `.github/workflows/veroeffentlichung.yml` nach [ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Entscheidung 2 und 4: Trigger `push: tags: ['v*']` und `workflow_dispatch` (kein Branch-Push, kein `pull_request`); Kopf `permissions: {}`; Job `gates` (`contents: read`, derselbe Checkout wie `gates.yml`: `fetch-depth: 0`, `persist-credentials: false`, derselbe SHA-Pin, `make gates`); Job `publish` (`needs: gates`, `contents: read` und `packages: write`, nur dort; läuft nur beim Tag-Push, Bedingung in §3; bricht, wenn der Tag-Commit nicht in `origin/main` liegt, `git merge-base --is-ancestor`; `GITHUB_TOKEN` aus `secrets.GITHUB_TOKEN` nur im `env:` des einen Schritts `make publish`; kein `--url`, kein Wert als Argument, kein `--info`/`--debug`); Job `vorschau` (nur bei `workflow_dispatch`, `make publish-vorschau`, kein Token); `concurrency` je Tag mit `cancel-in-progress: false`; jede Action per Commit-SHA; im Kopf der Datei der Zeiger auf `gates.yml` (Kopplung der wiederholten Schritte). `make workflow-lint` grün, Textabgleich im Review.
- [ ] **Nachzug von Anleitung und Sensor-Index:** [`releasing.md`](../../../maintainer/releasing.md) Schritt 1 (die Setzung „Lauf von `gates.yml` auf dem Release-Stand“ bleibt der Nachweis, der Job `gates` im Workflow ist die Absicherung), Schritt 8 (der Tag-Push löst die Veröffentlichung aus; Tag nur auf einem Commit von `main`) und Schritt 9 (die CI ist der Weg, `make publish` mit persönlichem Token nicht mehr; der Satz „belegt gegen einen Dummy-Server, nicht gegen GitHub“ bleibt, bis [slice-maven-erste-veroeffentlichung](../open/slice-maven-erste-veroeffentlichung.md) schließt), samt einer **Anleitung zum Tag-Schutz `v*`**, den der Nutzer **vor dem ersten Tag** im Repository setzt (der Wortlaut folgt der GitHub-Dokumentation, am Tag des Schreibens abgerufen, sonst als ungeprüft gekennzeichnet, `AGENTS.md` §3.8); eine Zeile in [`harness/README.md`](../../../../harness/README.md) §Sensors (Werkzeuge-Tabelle: Workflow, kein Gate, Bindung [ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md)) und die Nennung im Absatz „Aktueller Lauf-Status“.
- [ ] **Messprotokoll des Dispatch-Laufs:** ein Lauf von `workflow_dispatch` auf GitHub (Jobs `gates` und `vorschau`, `publish` übersprungen) ist gemessen, im Messprotokoll unter `docs/messungen/` (`AGENTS.md` §5 Regel 7) mit Datum, Lauf-Kennung, Ausgang je Job, Dauer von `gates` und `vorschau`, Docker-Version des Runners und POM-Ausgabe; kein Token, kein Netz zu GitHub Packages. Das Protokoll benennt **ausdrücklich, was der Lauf nicht belegt** ([ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Entscheidung 4): dass `GITHUB_TOKEN` mit `packages: write` schreiben darf, die Zuordnung des Pakets zum Repository, den Klon eines Tag-Push für `tools/publish.sh` und die Maskierung im Log.

**Wer den dritten Punkt nicht liefern kann:** Der Implementer kann den Lauf **nicht** fahren (er hat keinen Zugriff auf GitHub Actions und keinen Push). Ablauf: Der Implementer liefert Punkt 1 und 2 und lässt sie reviewen; der Nutzer mergt nach `main` (`workflow_dispatch` erscheint für eine Datei erst auf dem Standardzweig) und löst den Lauf aus (Oberfläche von GitHub Actions oder `gh workflow run`); der Nutzer gibt die Lauf-Kennung (und, wenn er will, das Log) weiter; erst dann wird das Messprotokoll geschrieben. Bis dahin steht der Slice in `in-progress/` mit offenem drittem Punkt und schließt nicht.

Nachweis-Pflichten für alle drei Punkte:

- [ ] `make workflow-lint` grün, `make skript-selbsttest` grün (die Datei unter `.github/workflows/` ist Eingang der Klasse `werkzeug`), `make gates` grün; bei Doku `make docs-check`, `make flaeche`, `make kopplung-zahlen` ([`harness/README.md`](../../../../harness/README.md) §Minimal agent workflow). Kein `make e2e`, kein `make clean-build`.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben; **kein Zaehler wird gesetzt**. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

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

**Reihenfolge mit den Nachbarn in `releasing.md`:** dieser Slice zuerst; [slice-release-entscheidungen](../open/slice-release-entscheidungen.md) beginnt erst, wenn dieser Slice nicht mehr in `in-progress/` liegt (gleiche Datei, getrennte Abschnitte), [slice-maven-erste-veroeffentlichung](../open/slice-maven-erste-veroeffentlichung.md) beginnt nach seiner Closure.

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

- **Rechte von `GITHUB_TOKEN`:** ob `permissions: packages: write` die erste Veröffentlichung eines noch nicht bestehenden Pakets zulässt, ist nach Dokumentationsstand gelesen, nicht gemessen; der Dispatch-Lauf belegt es nicht. — **Ausgang:** offen bis zur Closure (Träger der Messung: [slice-maven-erste-veroeffentlichung](../open/slice-maven-erste-veroeffentlichung.md)).
- **Zuordnung des Pakets zum Repository** und **Maskierung des Tokens im Log** sind ungemessen; beide hängen an einer echten Veröffentlichung. — **Ausgang:** offen bis zur Closure (derselbe Träger).
- **Klon eines Tag-Push:** `tools/publish.sh` verlangt den Tag auf `HEAD` und einen sauberen Baum; dass der Runner beides im Klon eines Tag-Push liefert (losgelöster `HEAD`, Tag lokal, `origin/main` vorhanden bei `fetch-depth: 0`), ist ungemessen und vom Dispatch-Lauf nicht erreichbar (kein Tag-Ref). — **Ausgang:** offen bis zur Closure (derselbe Träger).
- **Doppelter Lauf von `make gates`** bei jedem Tag (in `gates.yml` und im Job `gates`): akzeptiertes Negativ der ADR; die Laufzeit auf dem Runner wird mit dem Dispatch-Lauf erstmals für diesen Job gemessen. — **Ausgang:** offen bis zur Closure.
- **Der Dispatch-Lauf hängt am Nutzer** (Merge nach `main`, Auslösen): der Slice kann ohne ihn nicht schließen, und der Implementer kann den Lauf nicht ersetzen. — **Ausgang:** offen bis zur Closure.
- **Tag-Schutz nicht gesetzt:** wer `v*` auf `main` pushen darf, löst eine Veröffentlichung aus; die Einstellung liegt bei GitHub und ist von keinem Sensor geprüft (akzeptiertes Negativ der ADR). Dieser Slice liefert die Anleitung, nicht den Zustand. — **Ausgang:** offen bis zur Closure; der Nutzer bestätigt den Schutz vor dem ersten Tag im Start von [slice-maven-erste-veroeffentlichung](../open/slice-maven-erste-veroeffentlichung.md).
- **Wortlaut der Anleitung zum Tag-Schutz** gegen die Oberfläche von GitHub: ungeprüft, solange der Autor sie nicht abgerufen hat (`AGENTS.md` §3.8). — **Ausgang:** offen bis zur Closure.

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register (vorhandene `BEO-<KUERZEL>/<slug>` **zitieren**) ·
`grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln.

Wird bei der Closure gefüllt.

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung.

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration führt `*` als einzige Sub-Area (Greenfield).

**Vorgelagert — offene Beobachtungen sichten:** Register (`../observations/BEO-TDS/`, Stand des gemergten `main` vom 2026-11-14) nach Treffern durchgegangen: `nachweis-von-der-falschen-seite-der-grenze` (2×; der Dispatch-Lauf belegt den Bau auf dem Runner, nicht das Schreiben — die Grenze steht in §2 Punkt 3), `skript-pruefung-ohne-rot-gegenprobe` (1×; für den Workflow gibt es keinen Selbsttest, nur Lint und Review — in §1 benannt) und `einmal-rot-ohne-gesicherte-ausgabe` (2×; ein roter Dispatch-Lauf wird vor dem Wiederholen mit Lauf-Kennung und Ausgabe gesichert). Keine weiteren Treffer gelesen.

Alle berührten Sub-Areas GF.
