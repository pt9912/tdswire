# ADR-0048: Veröffentlichen der Bibliothek in der CI bei Tag-Push mit `GITHUB_TOKEN` statt mit einem persönlichen Token (Option 6c)

**Status:** Proposed

**Datum:** 2026-11-14

**Autor:** Architect (Entwurf); Entscheidung: pt9912 (Nutzer) steht aus

**Entscheidung des Nutzers (Quelle):** 2026-11-14, im Auftrag an den Architect: „6c, Architect soll die ADR vorbereiten“. Begründung des Nutzers: Veröffentlichen gehört üblicherweise in die CI; ein lokales *Personal Access Token (classic)* mit `write:packages` ist das größere Risiko. **Die Richtung hat der Nutzer entschieden; die Ausgestaltung unten ist Entwurf, unbestätigt, bis er diese ADR annimmt.**

**Bezug:** [TDS-RB-05](../../../spec/lastenheft.md#tds-rb-05--lizenz), [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [ADR-0047](0047-veroeffentlichung-ueber-github-packages.md), [ADR-0022](0022-e2e-laeufe-nicht-in-den-gates.md), [ADR-0024](0024-lokale-stempel-zwei-klassen.md), [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md), [AGENTS.md](../../../AGENTS.md) §3.1, §3.5, §3.6, §3.8

**Schärft:** — (keine Spec-Stelle; das Veröffentlichen ist ein Werkzeug außerhalb der Gates, siehe Entscheidung)

**Supersedes (teilweise), nur wörtlich benannt, wirksam erst mit dem Status `Accepted`; die Datei von [ADR-0047](0047-veroeffentlichung-ueber-github-packages.md) bleibt unverändert und `Accepted` ([AGENTS.md](../../../AGENTS.md) §3.5):**

- [ADR-0047](0047-veroeffentlichung-ueber-github-packages.md) Entscheidung 6, **ausschließlich** (a) die Wahl der Option 6a, also das *Personal Access Token (classic)* mit `write:packages`, das der Maintainer in der Umgebung seines Aufrufs von `make publish` hält, als Quelle von `GITHUB_ACTOR` und `GITHUB_TOKEN`, und (b) den Satz „Empfehlung **6a**; 6c als Folge, wenn Veröffentlichungen regelmäßig werden (Trigger)“. Für den Weg der Veröffentlichung gilt stattdessen Entscheidung 1 dieser ADR.
- **Nicht ersetzt** (gilt unverändert weiter): in [ADR-0047](0047-veroeffentlichung-ueber-github-packages.md) Entscheidung 6 die Randbedingungen (kein Token in Layer, Datei, Commit, Log), die Weitergabe der Werte nur *namentlich* an den Container, die Stage `publish`, die beiden Targets `make publish-vorschau` und `make publish` samt Eingangsskript `tools/publish.sh` und dessen Selbsttest, ihre Lage außerhalb von `make gates` und `make e2e` und der Beleg gegen ein Ziel mit Platzhalter-Token. Ersetzt wird nur, woher das Token kommt und wer den Lauf auslöst. Ebenfalls unberührt: alle anderen Entscheidungen (1 bis 5, 7, 8) von [ADR-0047](0047-veroeffentlichung-ueber-github-packages.md), insbesondere das Konsumenten-Token mit `read:packages` (Entscheidung 5).

**Regeln:** Baseline-Regelwerk `modul-04-adrs.md` §Ziel-Form: ADR (MADR).

---

## Kontext

**Bewusste Vorverlegung des Zielbilds, vom Nutzer entschieden.** [ADR-0047](0047-veroeffentlichung-ueber-github-packages.md) nennt unter „Re-Evaluierungs-Trigger“ ausdrücklich „regelmäßige Veröffentlichungen, die ein persönliches Token unzumutbar machen (dann 6c)“. Dieser Trigger ist **nicht eingetreten**: es ist keine Version veröffentlicht, kein Tag gesetzt. Der Nutzer zieht Option 6c trotzdem jetzt vor, mit seiner Begründung (Risiko des lokalen Tokens). Die ADR hält das fest, statt den Trigger als eingetreten auszugeben.

**Was Beleg ist und was nicht** ([AGENTS.md](../../../AGENTS.md) §3.8). *Gelesen* im Repo am 2026-11-14 (nichts ausgeführt): `tools/publish.sh`, `tools/mk/publish.mk`, `.github/workflows/gates.yml` und `e2e.yml`, die Stage `publish` im `Dockerfile`, [`releasing.md`](../../maintainer/releasing.md) Schritte 1, 8 und 9, der Plan der Plan der ersten Veröffentlichung (`docs/plan/planning/open/`, Titel „Erste Veröffentlichung der Bibliothek“). *Aus dem Repo belegt:* `gates.yml` läuft auf `ubuntu-24.04` mit Docker und `make`, die Stages bauen dort (sieben Läufe auf `main` `success`, [`harness/README.md`](../../../harness/README.md) §Sensors, Stand dort). **Nur gelesen, nicht gemessen:** dass `GITHUB_TOKEN` mit `permissions: packages: write` ein Paket des Repositories des Workflows veröffentlichen darf (GitHub-Dokumentation zur [Maven-Registry](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry), im Auftrag genannt, hier nicht neu abgerufen); dass der Runner `GITHUB_ACTOR` selbst setzt, `GITHUB_TOKEN` aber nur über `env:` aus `secrets.GITHUB_TOKEN` in den Schritt gelangt (Setzung nach Dokumentationsstand, ungemessen). **Ungemessen:** Bau der Stage `publish` auf dem Runner (Dauer, Cache, Docker-Version), Verhalten von `tools/publish.sh` im Klon eines Tag-Push (`HEAD` losgelöst auf dem Tag-Commit, Tag lokal vorhanden), Zuordnung des Pakets zum Repository beim ersten Veröffentlichen, Maskierung des Tokens im Log eines echten Laufs.

**Ist-Zustand.** `tools/publish.sh` prüft Version, Tag auf `HEAD`, sauberen Baum, gesetzte Werte `GITHUB_ACTOR` und `GITHUB_TOKEN` und ruft erst dann Docker; Ziel ist ohne `--url` nur GitHub Packages, ein anderes Ziel nimmt nur ein Token mit dem Präfix `PLATZHALTER-`. Dass `make gates` auf dem Release-Commit grün war, prüft es nicht (Setzung von [ADR-0047](0047-veroeffentlichung-ueber-github-packages.md) und [`releasing.md`](../../maintainer/releasing.md) Schritt 1). `gates.yml` hat `on: push` ohne Filter (läuft damit auch bei einem Tag-Push, Lesart der Datei, nicht gemessen), `permissions: contents: read`, keine Secrets; Actions sind per Commit-SHA gepinnt.

**Annahme, die kippt:** dass `GITHUB_TOKEN` die erste Veröffentlichung eines noch nicht bestehenden Pakets zulässt und es dem Repository zuordnet. Kippt sie, bleibt 6a als Rückfall (siehe Entscheidung 1, Grenzen).

## Entscheidung

Vorgeschlagen (nicht entschieden, bis der Nutzer annimmt): **Option 6c-1.** Je Entscheidung eine Tabelle; **fett** ist die Empfehlung, unbestätigt.

### 1. Weg der Veröffentlichung

| Option | Pro | Contra |
|---|---|---|
| **6c-1 — Workflow `.github/workflows/veroeffentlichung.yml` bei Tag-Push `v*`; Job `gates` (`make gates`), danach Job `publish` (`needs: gates`), der `make publish` ruft; `GITHUB_TOKEN` aus `secrets.GITHUB_TOKEN` nur im Schritt des Aufrufs; `tools/publish.sh` und die Stage `publish` unverändert** | kein persönliches Token auf einem Rechner; `tools/publish.sh` bleibt der einzige Eingang (Version, Tag, Baum, Präfixprüfung `PLATZHALTER-`), er ist belegt (Selbsttest) und bleibt Docker-only ([AGENTS.md](../../../AGENTS.md) §3.1); kleinste Änderung: eine neue Workflow-Datei | neuer Workflow mit Schreibrecht (`gates.yml` und `e2e.yml` haben keines); `make gates` läuft bei einem Tag-Push zweimal (in `gates.yml` und im Job `gates`), eine Version kostet damit doppelte Laufzeit |
| 6c-2 — Workflow mit eigenem Gradle-Aufruf oder eigener Action auf dem Runner, ohne `make publish` | kein zweiter Docker-Bau in der Stage `publish` | bricht Docker-only ([AGENTS.md](../../../AGENTS.md) §3.1: Gradle läuft nur im Container); verdoppelt die Prüfungen von `tools/publish.sh` in YAML, die der Selbsttest nicht deckt; eine Third-Party-Action vergrößert die Lieferkette |
| 6a — Maintainer veröffentlicht mit persönlichem Token, wie [ADR-0047](0047-veroeffentlichung-ueber-github-packages.md) | gebaut und belegt gegen einen Dummy-Server; kein Workflow, kein Schreibrecht in der CI | genau das Risiko, das der Nutzer vermeiden will: ein langlebiger Schlüssel mit `write:packages` auf dem Rechner; ein Tag-Push löst nichts aus, die Reihenfolge „Gates grün, dann veröffentlichen“ hängt am Maintainer |

Empfehlung **6c-1**. **Grenzen:** (a) 6a bleibt technisch möglich, weil `tools/publish.sh` unverändert ist; der dokumentierte Weg ist nach `Accepted` 6c-1, und ein Aufruf von `make publish` mit einem persönlichen Token ist kein Verstoß, aber nicht mehr der Weg ([`releasing.md`](../../maintainer/releasing.md) Schritt 9 zieht der Folge-Slice nach; ob das Skript einen Aufruf außerhalb der CI sperren soll, ist offene Frage 2). (b) Scheitert die erste Veröffentlichung in der CI an Rechten, ist 6a der Rückfall; ein Teilbestand in der Registry verbraucht die Version.

### 2. Form des Workflows (Setzungen, nicht gemessen)

- **Trigger:** `on: push: tags: ['v*']` und `workflow_dispatch`. Der Dispatch-Lauf ist der Vorschau-Lauf (Entscheidung 4) und veröffentlicht nie: der Job `publish` hat `if: github.ref_type == 'tag'`. Kein `pull_request`, kein Branch-Push.
- **Reihenfolge:** Job `gates` führt `make gates` mit demselben Checkout wie `gates.yml` (`fetch-depth: 0`, `persist-credentials: false`, derselbe SHA-Pin). Der Job `publish` hat `needs: gates`. Grund für die Wiederholung statt eines Bezugs auf den Lauf von `gates.yml`: ein Workflow kann den Lauf eines anderen nur über `workflow_run` oder eine API-Abfrage abwarten, und beide hängen an Zeitpunkt und Zuordnung zum Tag-Commit (ungemessen); der eigene Job ist die Abkürzung, die trägt. Die Wiederholung ist Kopplung: ändert sich der Aufruf in `gates.yml`, steht im Kopf der neuen Datei der Zeiger darauf. `workflow_call` an `gates.yml` (ein Aufruf statt einer Kopie) ist die Alternative, ändert aber `gates.yml` und ist ungemessen; sie ist hier nicht gewählt.
- **Tag auf `main`:** Der Job `publish` bricht, wenn der Tag-Commit nicht in `origin/main` enthalten ist (`git merge-base --is-ancestor`, der Klon ist vollständig). Setzung: ein Tag auf einem fremden Zweig veröffentlicht nichts.
- **`permissions` je Job minimal:** Workflow-Kopf `permissions: {}`; Job `gates` `contents: read`; Job `publish` `contents: read` und `packages: write`, sonst nichts. Kein anderes Secret als `secrets.GITHUB_TOKEN`.
- **Zugangsdaten:** `GITHUB_TOKEN` steht nur im `env:` des einen Schritts `make publish`, nicht im Job. `GITHUB_ACTOR` setzt der Runner (ungemessen). Das Skript reicht beide nur namentlich an den Container; der Workflow übergibt nie `--url` und keinen Wert als Argument, und der Gradle-Lauf trägt kein `--info` oder `--debug`.
- **Pins und Lint:** Jede Action per Commit-SHA wie in `gates.yml` (der `checkout` ist schon gepinnt, mehr Actions braucht der Entwurf nicht). `make workflow-lint` prüft Syntax und Ausdrücke, **nicht** die SHA-Pins und **nicht**, dass der Workflow `make gates` vor `make publish` ruft ([`harness/README.md`](../../../harness/README.md) §Sensors); beides prüft nur der Review. `make workflow-lint` ist kein Gate und wird hier keines.
- **Keine Wiederholung:** `concurrency` je Tag mit `cancel-in-progress: false`; der Workflow hat keinen Wiederholungsschritt. Ein erneuter Lauf („Re-run“) für eine veröffentlichte Version ist nicht vorgesehen; ob die Registry eine zweite Veröffentlichung derselben Version ablehnt, ist ungemessen ([ADR-0047](0047-veroeffentlichung-ueber-github-packages.md) Entscheidung 3). Die Setzung „eine Version wird nie neu veröffentlicht“ gilt unverändert; Korrektur ist eine neue Version.

### 3. Einordnung gegenüber ADR-0022 und ADR-0024 (kein Gate)

Der Workflow ist **kein Gate**: `make publish` steht in keiner Liste `GATE_CHECKS`, `GATE_KLASSE_*` oder `GATE_STAGE`, der Workflow ist kein Eingang von `make gates`. Das folgt dem Muster von [ADR-0022](0022-e2e-laeufe-nicht-in-den-gates.md) (Läufe mit Außenwirkung und eigener Laufzeit stehen außerhalb der Kette, dort mit eigenem Workflow `e2e.yml`) und der Begründung von [ADR-0047](0047-veroeffentlichung-ueber-github-packages.md) Entscheidung 6 (schreibt nach außen, nicht wiederholbar). Die Klassen und Stempel von [ADR-0024](0024-lokale-stempel-zwei-klassen.md) ordnen Gates ein und gelten hier nicht; die CI kennt die lokalen Stempel nicht und der Job `gates` läuft voll. Keine Schwelle sinkt, keine Regel wird gelockert; [AGENTS.md](../../../AGENTS.md) §3.6 ist nicht berührt. Dass die Datei unter `.github/workflows/` steht, macht sie zum Eingang der Klasse `werkzeug` (`skript-selbsttest`; `tools/stempel/werkzeug.eingaenge`): die Klasse läuft nach Anlage der Datei einmal neu.

### 4. Vorschau-Lauf statt Probe-Veröffentlichung

Eine Probe-Veröffentlichung auf einem Fork- oder Test-Tag ist **ausgeschlossen**: sie verbraucht eine Version, die nie neu veröffentlicht wird. Stattdessen: Der Lauf per `workflow_dispatch` führt Job `gates` und einen Job `vorschau` mit `make publish-vorschau` aus (Bau der Stage `publish` auf dem Runner, `publishToMavenLocal`, POM-Ausgabe; kein Token, kein Netz zu GitHub Packages), und er überspringt `publish`.

**Was dieser Lauf belegt:** der Workflow ist syntaktisch gültig und läuft; Docker und die Stage `publish` bauen auf dem Runner, mit Dauer; die POM entsteht dort. **Was er nicht belegt (Grenze, benannt):** dass `GITHUB_TOKEN` mit `packages: write` schreiben darf; die Zuordnung des Pakets zum Repository; den Klon eines Tag-Push für `tools/publish.sh` (Tag lokal, `HEAD` auf dem Tag-Commit); die Maskierung im Log. Diese vier sind erst mit der ersten echten Veröffentlichung gemessen. Es gibt keinen Probelauf des Schreibens, der keine Version verbraucht (`gradle publish` hat keinen Trockenlauf; **akzeptiertes Negativ, einmalig:** das Risiko liegt auf der ersten Version `0.1.0`, deren Teilbestand eine neue Version `0.1.1` erzwänge; der Schaden ist ein verbrauchter Versionszähler, kein Datenverlust).

## Verglichene Alternativen

Die Wahl als Ganzes ist in Entscheidung 1 als Tabelle geführt (6c-1, 6c-2, 6a; „nichts tun“ ist 6a). Weitere verworfene Formen:

| Option | Pro | Contra |
|---|---|---|
| Veröffentlichen bei Push auf `main` oder per Hand-Dispatch ohne Tag | kein Tag nötig | `tools/publish.sh` verlangt den Tag `v<Version>` auf `HEAD`; die Version ist ohne Tag nicht an einen Commit gebunden |
| Umgebung `veröffentlichung` mit Pflicht-Freigabe (`environment` mit Reviewer) | eine zweite, menschliche Bestätigung vor `publish` | ob die Funktion für dieses Repository verfügbar ist, ist ungeprüft; der Tag-Push durch den Nutzer ist bereits dessen Bestätigung (offene Frage 3) |

## Konsequenzen

- Positiv: kein langlebiges Schreib-Token auf dem Rechner; „Gates grün, dann veröffentlichen“ steht im Workflow statt in einer Setzung der Anleitung; `tools/publish.sh` und sein Selbsttest bleiben unverändert tragend.
- Negativ: ein Workflow mit `packages: write`; doppelter Lauf von `make gates` bei jedem Tag; jeder, der einen Tag `v*` auf `main` pushen kann, löst eine Veröffentlichung aus (Schutz der Tags ist eine Repository-Einstellung außerhalb des Repos, kein Gate, nicht prüfbar: **akzeptiertes Negativ, Grund:** die Einstellung liegt bei GitHub, ein Sensor dafür bestünde aus einem Netz-Abruf, den dieses Repo nicht führt; der Nutzer setzt sie, siehe Folgepflicht).
- **Trigger-Hinweis an die Prozess-Quellen:** [ADR-0047](0047-veroeffentlichung-ueber-github-packages.md) bleibt in seiner Datei unverändert (`Accepted`); sein Trigger „regelmäßige Veröffentlichungen“ gilt durch diese ADR als vom Nutzer vorweggenommen, nicht als eingetreten. Der Index trägt den Vermerk am Eintrag von ADR-0047 nach der Annahme (nicht in dessen Datei).
- **Folgepflicht nach `Accepted` (zwei Folge-Slices, Planner; die Pläne schreibt diese ADR nicht um):**
  1. *Workflow-Slice* (neu): `.github/workflows/veroeffentlichung.yml` nach Entscheidung 2 und 4; `make workflow-lint` grün; Prüfung per Textabgleich im Review (Reihenfolge `gates` vor `publish`, `permissions`, keine `--url`); Nachzug von [`releasing.md`](../../maintainer/releasing.md) Schritt 1 (Setzung „Lauf von `gates.yml` auf dem Release-Stand“ bleibt Nachweis, der Job `gates` im Workflow ist die Absicherung), Schritt 8 (Tag-Push löst aus) und Schritt 9 (CI ist der Weg, `make publish` mit persönlichem Token nicht mehr); Zeile in [`harness/README.md`](../../../harness/README.md) (Workflow und Bindung an diese ADR); einmaliger Lauf per `workflow_dispatch` mit Messung nach Entscheidung 4, Messprotokoll in `docs/messungen/`.
  2. *Anpassung des Plans der ersten Veröffentlichung (`docs/plan/planning/open/`, Titel „Erste Veröffentlichung der Bibliothek“)* (Inhalt, vom Planner umzusetzen): Start-Trigger „stellt das Token (`write:packages`) bereit“ entfällt; die Bestätigung der Handlung ist der Tag-Push des Nutzers (Wortlaut im Plan-Kopf bleibt Pflicht); der Ablauf „führt der Nutzer `make publish` selbst aus“ wird „der Nutzer pusht den Tag, der Workflow läuft“; der Punkt „ein Workflow bei Tag-Push (6c)“ verlässt die Liste „Ausdrücklich NICHT“; das Messprotokoll stammt aus dem Actions-Lauf (Datum, Lauf-Kennung, Ausgang, kein Token); die Risiken Token-Scope und Zuordnung des Pakets werden zu „Rechte von `GITHUB_TOKEN`“ und bleiben bis zur Closure offen. Vorbedingung: der Workflow-Slice liegt in `done/`. Die Konsumentenseite (`read:packages`) bleibt, wie sie ist.
- Vom Nutzer außerhalb des Repos zu setzen und von keinem Gate geprüft: Schutz von Tags `v*` (nur Maintainer dürfen pushen) und die Einstellung der Workflow-Berechtigungen des Repositories, soweit sie `permissions` der Datei überstimmen.

## Fitness Function (falls maschinell prüfbar)

| Tooling | Regel | Make-Target |
|---|---|---|
| `actionlint` im Container | `veroeffentlichung.yml` ist syntaktisch gültig | `make workflow-lint` (**kein Gate**, [`harness/README.md`](../../../harness/README.md) §Sensors) |
| `tools/publish.sh` mit Selbsttest (unverändert) | Version, Tag, Baum, Token und Präfixprüfung brechen vor Docker | `make skript-selbsttest` (Klasse `werkzeug`, [ADR-0024](0024-lokale-stempel-zwei-klassen.md)) |
| Review | `needs: gates` vor `publish`; `packages: write` nur im Job `publish`; Actions per SHA; kein `--url` im Workflow; `make publish` in keiner Gate-Liste | nur Absicht, im Review (kein Sensor, siehe Entscheidung 2) |

## Re-Evaluierungs-Trigger

Die erste echte Veröffentlichung weist `GITHUB_TOKEN` ab oder ordnet das Paket nicht dem Repository zu (dann 6a als Rückfall oder neue ADR); eine Änderung der Berechtigungsregeln für `GITHUB_TOKEN` oder GitHub Packages; ein zweites veröffentlichtes Modul (dann [ADR-0047](0047-veroeffentlichung-ueber-github-packages.md) Entscheidung 1b und eigener Lauf je Modul); der Entschluss für Maven Central (Signierung braucht ein Secret, dann eigene Entscheidung); der Befund, dass die doppelte Laufzeit von `make gates` stört (dann `workflow_call` oder `tags-ignore` in `gates.yml`, mit eigener Begründung).

## Offene Fragen an den Nutzer

1. Gate-Wiederholung: Job `gates` im neuen Workflow (empfohlen, `gates.yml` bleibt unverändert, doppelter Lauf bei Tag-Push) oder `workflow_call` an `gates.yml` (kein Duplikat der Schritte, aber `gates.yml` ändert sich)?
2. Soll `tools/publish.sh` einen Aufruf außerhalb der CI sperren (etwa nur bei `GITHUB_ACTIONS=true`)? Empfohlen: nein (ein Rückfall 6a bleibt offen, kein neuer Zwang); die Alternative macht 6a nach der Annahme unmöglich ohne neue ADR.
3. Reicht der Tag-Push als ausdrückliche Bestätigung der Veröffentlichung, oder soll eine Umgebung mit Pflicht-Freigabe davor? Empfohlen: Tag-Push genügt.
4. Wer setzt den Schutz der Tags `v*` im Repository, und wann (vor dem ersten Tag)?

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-11-14 | Proposed – Entscheidung des Nutzers für die Richtung 6c (Auftrag an den Architect); Ausgestaltung Entwurf, Empfehlungen unbestätigt; ersetzt nach Annahme nur Entscheidung 6 (Wahl 6a) von [ADR-0047](0047-veroeffentlichung-ueber-github-packages.md) | — |

Nach `Accepted` wird diese Datei **nicht mehr inhaltlich überschrieben**.
Spätere Korrekturen oder Schärfungen entstehen als neue ADR mit
`Supersedes ADR-NNNN` (Baseline-Regelwerk `modul-04-adrs.md`
§Hard Rule für Accepted-ADRs).
