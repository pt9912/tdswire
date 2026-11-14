# Slice slice-maven-erste-veroeffentlichung: Erste Veröffentlichung der Bibliothek in GitHub Packages durch den Workflow bei Tag-Push, Konsum aus der Registry gemessen

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** ohne Welle — keine Closure-Bedingung, die von der DoD dieses Slice verschieden ist.

**Bezug:** [TDS-RB-01](../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache) (Zielbild-Prüfung: Konsum aus der Registry), [TDS-RB-05](../../../../spec/lastenheft.md#tds-rb-05--lizenz), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) (Scope), [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) (`Accepted` 2026-11-14, Folgepflicht „erste echte Veröffentlichung mit Messprotokoll“; der Weg „von Hand“ ist durch [ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) ersetzt), [ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) (`Accepted` 2026-11-14, Folgepflicht 2: Veröffentlichen in der CI bei Tag-Push)

**Berührte Spec-Stellen:** SPEC-052 · SPEC-059 (nur, wenn die Messung eine Aussage widerlegt; der Nachzug des Schemas liegt bei [slice-maven-veroeffentlichung](../done/slice-maven-veroeffentlichung.md))

**Verantwortlich:** — bis zur Priorisierung.

**Bestätigung des Nutzers:** steht aus. Der Wortlaut (Version, Zeitpunkt, Tag-Push als Handlung) wird hier eingetragen, bevor der Slice startet; eine Annahme der ADRs ist keine Bestätigung der Handlung.

**Autor:** Planner-Agent, Auftrag des Nutzers 2026-11-14; angepasst 2026-11-14 nach [ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md). **Datum:** 2026-11-14.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar.

**Ziel:** Die erste Version der Bibliothek (nach dem Schema `0.y.z` von [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) Entscheidung 3a, erwartet `0.1.0`) liegt in GitHub Packages, und der Konsum aus der Registry ist gemessen, einschließlich der Anleitung für Konsumenten mit `read:packages`.

**Warum ein eigener Slice:** Die Handlung schreibt nach außen und ist nicht rücknehmbar; sie ist **vom Nutzer ausdrücklich zu bestätigen**, und die Bestätigung ist der **Tag-Push des Nutzers** ([ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Entscheidung 1 und offene Frage 3). Die Bestätigung ist die Start-Bedingung (§4), nicht ein Liefer-Punkt: Die DoD nennt nur das Lieferbare (`/plan-welle` Schritt 6b, Frage 2). Die Vorbereitung (POM, Prüfung im Bau, `make publish-vorschau`, Eingangsskript) liefert [slice-maven-veroeffentlichung](../done/slice-maven-veroeffentlichung.md), den Workflow samt Dispatch-Messung [slice-ci-veroeffentlichung-workflow](../in-progress/slice-ci-veroeffentlichung-workflow.md), beide ohne Wirkung nach außen.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Werkzeug, POM-Prüfung und Eingangsskript — [slice-maven-veroeffentlichung](../done/slice-maven-veroeffentlichung.md); der Workflow `.github/workflows/veroeffentlichung.yml` — [slice-ci-veroeffentlichung-workflow](../in-progress/slice-ci-veroeffentlichung-workflow.md). Dieser Slice **benutzt** sie und ändert sie nicht (ein Befund an ihnen ist ein Rückweg dorthin, kein Nebenbei).
- Ein Aufruf von `make publish` von Hand mit persönlichem Token — der dokumentierte Weg ist die CI; die Option 6a ist nur der Rückfall nach [ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Entscheidung 1 (Grenzen) und braucht, wenn die CI abgewiesen wird, die Rückführung nach `open/` und eine Entscheidung des Nutzers.
- Das Setzen des Tag-Schutzes `v*` — Einstellung des Nutzers bei GitHub; die Anleitung liefert [slice-ci-veroeffentlichung-workflow](../in-progress/slice-ci-veroeffentlichung-workflow.md), hier ist der Schutz Start-Bedingung (§4).
- Ein zweites Veröffentlichen derselben Version oder ein Löschen — nach [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) Entscheidung 3a wird eine veröffentlichte Version nie neu veröffentlicht; Korrektur ist eine neue Version, und die ist ein eigener Vorgang mit eigener Bestätigung.
- Signierung, Maven Central, ein GitHub Release mit Dateien (Optionen 4b, 5b, 5c) — Trigger stehen in den ADRs; keiner ist eingetreten. (Der Workflow bei Tag-Push, Option 6c, ist mit [ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) entschieden und nicht mehr ausgeschlossen; dieser Slice **fährt** ihn.)
- Ein Java- oder Maven-Konsument — von der ADR als akzeptiertes Negativ geführt; der Probe-Konsument ist Kotlin und Gradle.
- Aus der Closure von [slice-maven-veroeffentlichung](../done/slice-maven-veroeffentlichung.md) übernommen (Verifikation V-4, Review F-7): die `.module`-Datei hat keine Prüfung auf Jackson und Testserver (im Bau wird nur die POM gelesen); ob dieser Slice sie nach der ersten Veröffentlichung am Registry-Artefakt liest, entscheidet sein Plan bei der Priorisierung, Java- und Maven-Konsumenten bleiben ungemessen.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**.

Liefer-Punkte (drei):

- [ ] **Veröffentlicht:** Die Version liegt in GitHub Packages unter `io.github.pt9912:tdswire` (Jar und POM), das Paket ist dem Repository `pt9912/tdswire` zugeordnet; der Release-Commit (Version ohne `-SNAPSHOT`, in `main`) trägt den Tag `v<Version>`, den der Nutzer pusht, danach steht `build.gradle.kts` wieder auf `-SNAPSHOT`. Das Messprotokoll stammt aus dem Lauf von `veroeffentlichung.yml` bei Tag-Push: Datum, Lauf-Kennung, Ausgang je Job, Dauer, ohne Token; es nennt, was der Lauf **belegt** (Schreibrecht von `GITHUB_TOKEN`, Zuordnung des Pakets, Klon eines Tag-Push für `tools/publish.sh`, Maskierung im Log) und was ungemessen bleibt.
- [ ] **Konsum aus der Registry gemessen:** Der Probe-Konsument aus [slice-maven-veroeffentlichung](../done/slice-maven-veroeffentlichung.md) bindet die Bibliothek über die Registry mit einem Token (`read:packages`), übersetzt und läuft; das Messprotokoll nennt, was dabei gebraucht wurde (Setup des Konsumenten, Token) und was ungemessen bleibt. Die Zielbild-Prüfung zu [TDS-RB-01](../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache) („Konsum aus der Registry“) ist damit geliefert; das Lastenheft bleibt unverändert.
- [ ] **Nutzer-Doku und Release-Anleitung:** `docs/user/einbettung-und-betrieb.md` führt das Setup eines Konsumenten (Registry-URL, Zugangsdaten mit `read:packages`, Koordinaten) im Ist-Zustand; alle Dateien unter `docs/user/` sind komplett gegen den Stand nach dem Slice gelesen (`AGENTS.md` §5 Regel 5); `docs/maintainer/releasing.md` führt Tag und Vermerk als Schritte, die gelaufen sind, und Schritt 9 ohne den Vorbehalt „gegen einen Dummy-Server, nicht gegen GitHub“.

Nachweis-Pflichten für alle drei Punkte:

- [ ] `make gates` grün; bei Doku und Spec `make docs-check`, `make flaeche`, `make kopplung-zahlen` ([`harness/README.md`](../../../../harness/README.md) §Minimal agent workflow).
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
| `build.gradle.kts` | update (zwei Commits) | Release-Commit setzt die Version ohne `-SNAPSHOT`; der Folge-Commit setzt `-SNAPSHOT` zurück ([ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) Entscheidung 3a) |
| `docs/messungen/` (Messprotokoll) | neu | Actions-Lauf bei Tag-Push, Konsum aus der Registry |
| `tools/publish-probe.sh` | unverändert | Probe-Konsument; bleibt bis zur Closure dieses Slice (Nutzerentscheidung 2026-11-14); ob er danach entfällt, steht in der Closure-Notiz |
| `docs/user/einbettung-und-betrieb.md` und übrige Dateien unter `docs/user/` | update | Setup des Konsumenten im Ist-Zustand |
| `docs/maintainer/releasing.md` | update | Schritte Version, Tag, Vermerk als gelaufen |

**Ablauf:** (1) Der Implementer setzt die Version im Release-Commit ohne `-SNAPSHOT`; der Commit gelangt nach `main` (der Workflow bricht, wenn der Tag-Commit nicht in `origin/main` liegt). (2) **Der Nutzer pusht den Tag `v<Version>`; der Workflow läuft** (Job `gates`, danach `publish`). Kein Agent führt `make publish` aus, hält ein Schreib-Token oder pusht den Tag; es gibt kein Token außer `GITHUB_TOKEN` des Runners. (3) Der Nutzer gibt die Lauf-Kennung weiter; Messprotokoll und Nachzüge folgen aus dem Lauf. (4) Folge-Commit setzt `-SNAPSHOT` zurück. Ein erneuter Lauf („Re-run“) für dieselbe Version ist nicht vorgesehen ([ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Entscheidung 2). Der Konsum aus der Registry (Punkt 2) wird mit einem Token des Nutzers mit `read:packages` gemessen; dieses Token ist ein Lese-Token und gehört nicht in eine Datei, ein Image oder eine Commit-Nachricht.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): (1) [slice-maven-veroeffentlichung](../done/slice-maven-veroeffentlichung.md) und [slice-ci-veroeffentlichung-workflow](../in-progress/slice-ci-veroeffentlichung-workflow.md) liegen in `done/`. (2) **Der Nutzer bestätigt ausdrücklich** die Veröffentlichung nach Version und Zeitpunkt (Wortlaut der Bestätigung steht im Plan-Kopf; eine Annahme der ADRs ist keine Bestätigung der Handlung; die Handlung selbst ist sein Tag-Push, ein Token ist nicht bereitzustellen) und **hat den Tag-Schutz `v*` im Repository gesetzt** (Angabe des Nutzers, von keinem Gate geprüft). (3) Ein Verantwortlicher ist eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß): der Konsum aus der Registry verlangt eine zweite Bauart (Maven, Java); sie wird ein eigener Slice, dieser liefert den Kotlin-Konsumenten.
- `in-progress` → `open` (blockiert): die Registry weist das Paket ab (etwa 422 wegen der `artifactId`, fehlende Rechte von `GITHUB_TOKEN` für ein noch nicht bestehendes Paket, ein Fehler des Klons im Workflow) oder der Nutzer zieht die Bestätigung zurück; ein Teilbestand in der Registry ist in §6 benannt. Bei einer Abweisung der CI ist der Rückfall 6a oder eine neue ADR ein Entscheid des Nutzers ([ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Re-Evaluierungs-Trigger), nicht dieses Slice.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag.

Beobachtbar: (1) Der Lauf von `veroeffentlichung.yml` bei Tag-Push ist `success` (das Messprotokoll nennt die Lauf-Kennung), die Version ist aus der Registry mit einem Token auflösbar (das Messprotokoll zeigt den Probe-Konsumenten gegen die Registry), der Tag `v<Version>` steht auf dem Release-Commit. (2) Alle Punkte der DoD erledigt, `make gates` grün, Review-Report liegt vor, Closure-Notiz mit Lerneintrag geschrieben. Die Closure-Notiz nennt außerdem, ob `tools/publish-probe.sh` bleibt oder entfällt, und hält fest, dass Software-Stückliste und der Weg für Jar und `lib/` ohne Registry nach der ersten Veröffentlichung neu zu bewerten sind (Auftrag aus [`releasing.md`](../../../maintainer/releasing.md), [slice-release-entscheidungen](../open/slice-release-entscheidungen.md)).

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang.

- Die Veröffentlichung gelingt teilweise (Jar liegt, POM fehlt, oder umgekehrt); die Version ist damit verbraucht, und eine Korrektur ist eine neue Version. — **Ausgang:** offen bis zur Closure.
- Die Verbindung des Pakets mit dem Repository (Zuordnung durch den Workflow) und seine Sichtbarkeit sind nicht geprüft ([ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) Entscheidung 1: Zuordnung über `distributionManagement`/URL nach der gelesenen Seite, nicht gemessen); der Konsum durch einen anderen Nutzer als den Maintainer ist ungemessen. — **Ausgang:** offen bis zur Closure.
- **Rechte von `GITHUB_TOKEN`:** ob `permissions: packages: write` die erste Veröffentlichung eines noch nicht bestehenden Pakets zulässt, ist nach Dokumentationsstand gelesen, nicht gemessen ([ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md), Annahme, die kippt); ebenso der Klon eines Tag-Push für `tools/publish.sh` (losgelöster `HEAD`, Tag lokal) und die Maskierung im Log. — **Ausgang:** offen bis zur Closure.
- **Tag-Schutz `v*` nicht gesetzt oder nicht wirksam:** wer den Tag pusht, löst die Veröffentlichung aus; die Einstellung ist von keinem Gate prüfbar (akzeptiertes Negativ der ADR). — **Ausgang:** offen bis zur Closure.
- Konsumenten ohne Konto oder Token können die Bibliothek nicht binden (Hauptkosten der Entscheidung 5a); das Setup steht in der Anleitung, die Alternative 5b ist ungeprüft. — **Ausgang:** offen bis zur Closure.

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register (vorhandene `BEO-<KUERZEL>/<slug>` **zitieren**) ·
`grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln.

Wird bei der Closure gefüllt.

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung.

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration führt `*` als einzige Sub-Area (Greenfield).

**Vorgelagert — offene Beobachtungen sichten:** Register (`../observations/BEO-TDS/`, Stand des gemergten `main` vom 2026-11-14) durchgegangen; Treffer: `nachweis-von-der-falschen-seite-der-grenze` (2×; dieser Slice liefert die Gegenseite zu den Belegen gegen das lokale Repository aus [slice-maven-veroeffentlichung](../done/slice-maven-veroeffentlichung.md)), `lizenzhinweise-der-bibliotheken-rechtsfrage` (offen, nicht Gegenstand) und `einmal-rot-ohne-gesicherte-ausgabe` (2×: die Ausgabe eines roten Laufs wird vor dem Neustart gesichert, eine Registry-Ablehnung ist nicht wiederholbar ohne neue Version).

Alle berührten Sub-Areas GF.
