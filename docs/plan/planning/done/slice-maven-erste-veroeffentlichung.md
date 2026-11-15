# Slice slice-maven-erste-veroeffentlichung: Erste Veröffentlichung der Bibliothek in GitHub Packages durch den Workflow bei Tag-Push

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** ohne Welle — keine Closure-Bedingung, die von der DoD dieses Slice verschieden ist.

**Bezug:** [TDS-RB-05](../../../../spec/lastenheft.md#tds-rb-05--lizenz), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) (Scope), [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) (`Accepted` 2026-11-14, Folgepflicht „erste echte Veröffentlichung mit Messprotokoll“; der Weg „von Hand“ ist durch [ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) ersetzt), [ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) (`Accepted` 2026-11-14, Folgepflicht 2: Veröffentlichen in der CI bei Tag-Push), [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) (`Accepted` 2026-11-15: zweites Ziel Cloudsmith; ersetzt die alleinige Wahl 5a von [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) und schneidet diesen Plan neu, siehe §1)

**Berührte Spec-Stellen:** SPEC-052 · SPEC-059 (nur, wenn die Messung eine Aussage widerlegt; der Nachzug des Schemas liegt bei [slice-maven-veroeffentlichung](../done/slice-maven-veroeffentlichung.md))

**Verantwortlich:** pt9912.

**Bestätigung des Nutzers:** 2026-11-15, Wortlaut: „v0.1.0 -- Zeitpunkt: so bald als möglich“. Version `0.1.0`; die Handlung ist der Tag-Push `v0.1.0` durch den Nutzer, sobald der Release-Commit in `main` und auf `origin` liegt. Der Tag-Schutz `v*` ist per API angelegt (Antwort der API gesehen); eine Gegenprobe an der GitHub-Oberfläche durch den Nutzer ist nicht eingetragen.

**Autor:** Planner-Agent, Auftrag des Nutzers 2026-11-14; angepasst 2026-11-14 nach [ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md); neu geschnitten 2026-11-15 nach [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) (Planner-Agent, Auftrag des Nutzers). **Datum:** 2026-11-14.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar.

**Ziel:** Die erste Version der Bibliothek (nach dem Schema `0.y.z` von [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) Entscheidung 3a, `0.1.0`) liegt in GitHub Packages, veröffentlicht vom Workflow bei Tag-Push und mit einem Messprotokoll des Laufs.

**Neuschnitt 2026-11-15 (nach [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md), Planner-Agent, Auftrag des Nutzers):** Der Plan sah als Ziel den Konsum aus der Registry mit einem `read:packages`-Token als einzigen Weg der Konsumenten vor. Die Entscheidung 5a von [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) ist als alleiniger Weg ersetzt: erster Weg ist der anonyme über Cloudsmith, GitHub Packages mit Token der zweite. Der Lauf von `0.1.0` ist erfolgt ([Messprotokoll des Tag-Laufs](../../../messungen/veroeffentlichung-tag-lauf-2026-11-15.md)). Was in diesem Plan noch offen war, liegt jetzt bei [slice-cloudsmith-veroeffentlichung-0-1-1](../open/slice-cloudsmith-veroeffentlichung-0-1-1.md): der Konsum aus der Registry (bisheriger Liefer-Punkt 2), die Dateiliste der Version in der Registry (im Protokoll ungemessen, bisher Teil von Liefer-Punkt 1) und die Nutzer-Doku samt Release-Anleitung (bisheriger Liefer-Punkt 3). Die Liefer-Punkte 2 und 3 sind aus der DoD entfernt, Punkt 1 trägt nur noch, was das Protokoll belegt. **Zielbild-Prüfung:** das Plan-Ziel „Konsum mit `read:packages`-Token“ ist das **angepasste** Zielbild (kein Lastenheft-Satz berührt); der Konsum mit Token bleibt als zweiter Weg Gegenstand von [slice-cloudsmith-veroeffentlichung-0-1-1](../open/slice-cloudsmith-veroeffentlichung-0-1-1.md).

**Warum ein eigener Slice:** Die Handlung schreibt nach außen und ist nicht rücknehmbar; sie war **vom Nutzer ausdrücklich zu bestätigen**, und die Bestätigung ist der **Tag-Push des Nutzers** ([ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Entscheidung 1 und offene Frage 3). Die Bestätigung ist die Start-Bedingung (§4), nicht ein Liefer-Punkt: Die DoD nennt nur das Lieferbare (`/plan-welle` Schritt 6b, Frage 2). Die Vorbereitung (POM, Prüfung im Bau, `make publish-vorschau`, Eingangsskript) liefert [slice-maven-veroeffentlichung](../done/slice-maven-veroeffentlichung.md), den Workflow samt Dispatch-Messung [slice-ci-veroeffentlichung-workflow](../done/slice-ci-veroeffentlichung-workflow.md), beide ohne Wirkung nach außen.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Werkzeug, POM-Prüfung und Eingangsskript — [slice-maven-veroeffentlichung](../done/slice-maven-veroeffentlichung.md); der Workflow `.github/workflows/veroeffentlichung.yml` — [slice-ci-veroeffentlichung-workflow](../done/slice-ci-veroeffentlichung-workflow.md). Dieser Slice **benutzt** sie und ändert sie nicht (ein Befund an ihnen ist ein Rückweg dorthin, kein Nebenbei).
- Ein Aufruf von `make publish` von Hand mit persönlichem Token — der dokumentierte Weg ist die CI; die Option 6a ist nur der Rückfall nach [ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Entscheidung 1 (Grenzen) und braucht, wenn die CI abgewiesen wird, die Rückführung nach `open/` und eine Entscheidung des Nutzers.
- Das Setzen des Tag-Schutzes `v*` — Einstellung des Nutzers bei GitHub; die Anleitung liefert [slice-ci-veroeffentlichung-workflow](../done/slice-ci-veroeffentlichung-workflow.md), hier ist der Schutz Start-Bedingung (§4).
- Ein zweites Veröffentlichen derselben Version oder ein Löschen — nach [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) Entscheidung 3a wird eine veröffentlichte Version nie neu veröffentlicht; Korrektur ist eine neue Version, und die ist ein eigener Vorgang mit eigener Bestätigung.
- Signierung, Maven Central, ein GitHub Release mit Dateien (Optionen 4b, 5b, 5c) — Trigger stehen in den ADRs; keiner ist eingetreten. (Der Workflow bei Tag-Push, Option 6c, ist mit [ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) entschieden und nicht mehr ausgeschlossen; dieser Slice **fährt** ihn.)
- Ein Java- oder Maven-Konsument — von der ADR als akzeptiertes Negativ geführt; der Probe-Konsument ist Kotlin und Gradle.
- Der Konsum aus der Registry (anonym über Cloudsmith, mit Token über GitHub Packages), die Dateiliste der Version in der Registry und die Nutzer-Doku zum Setup eines Konsumenten — [slice-cloudsmith-veroeffentlichung-0-1-1](../open/slice-cloudsmith-veroeffentlichung-0-1-1.md) (nimmt die Punkte an, DoD dort). Grund: [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) ändert den Weg der Konsumenten; eine Anleitung für `read:packages` allein wäre schon vor ihrer Lieferung überholt, und die Probe braucht das Werkzeug aus [slice-cloudsmith-ziel-bauen](../open/slice-cloudsmith-ziel-bauen.md).
- Aus der Closure von [slice-maven-veroeffentlichung](../done/slice-maven-veroeffentlichung.md) übernommen (Verifikation V-4, Review F-7): die `.module`-Datei hat keine Prüfung auf Jackson und Testserver (im Bau wird nur die POM gelesen); ob die `.module` am Registry-Artefakt gelesen wird, entscheidet nach dem Neuschnitt der Plan von [slice-cloudsmith-veroeffentlichung-0-1-1](../open/slice-cloudsmith-veroeffentlichung-0-1-1.md) (Konsum mit und ohne `.module` gegen Cloudsmith); Java- und Maven-Konsumenten bleiben ungemessen.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**.

Liefer-Punkte (einer; der Neuschnitt hat zwei an [slice-cloudsmith-veroeffentlichung-0-1-1](../open/slice-cloudsmith-veroeffentlichung-0-1-1.md) gegeben):

- [x] **Veröffentlicht:** Die Version liegt in GitHub Packages unter `io.github.pt9912:tdswire` (Paket und Version in der Pakete-API gesehen; die Dateien der Version sind Gegenstand von [slice-cloudsmith-veroeffentlichung-0-1-1](../open/slice-cloudsmith-veroeffentlichung-0-1-1.md)), das Paket ist dem Repository `pt9912/tdswire` zugeordnet; der Release-Commit (Version ohne `-SNAPSHOT`, in `main`) trägt den Tag `v<Version>`, den der Nutzer pusht, danach steht `build.gradle.kts` wieder auf `-SNAPSHOT`. Das Messprotokoll stammt aus dem Lauf von `veroeffentlichung.yml` bei Tag-Push: Datum, Lauf-Kennung, Ausgang je Job, Dauer, ohne Token; es nennt, was der Lauf **belegt** (Schreibrecht von `GITHUB_TOKEN`, Zuordnung des Pakets, Klon eines Tag-Push für `tools/publish.sh`, Maskierung im Log) und was ungemessen bleibt.

Nachweis-Pflichten für den Punkt:

- [x] `make gates` grün; bei Doku und Spec `make docs-check`, `make flaeche`, `make kopplung-zahlen` ([`harness/README.md`](../../../../harness/README.md) §Minimal agent workflow).
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`; **Lücke: kein Review-Report zu diesem Slice, der Verifikationsbericht ist die Prüfung**, siehe §7) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist (nach dem Neuschnitt: die Nutzer-Doku und `releasing.md` liegen bei [slice-cloudsmith-veroeffentlichung-0-1-1](../open/slice-cloudsmith-veroeffentlichung-0-1-1.md)).
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben; **kein Zaehler wird gesetzt**. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `build.gradle.kts` | update (zwei Commits) | Release-Commit setzt die Version ohne `-SNAPSHOT`; der Folge-Commit setzt `-SNAPSHOT` zurück ([ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) Entscheidung 3a) |
| `docs/messungen/` (Messprotokoll) | neu | Actions-Lauf bei Tag-Push, Konsum aus der Registry |
| `tools/publish-probe.sh` | unverändert | Probe-Konsument; bleibt bis zur Closure dieses Slice (Nutzerentscheidung 2026-11-14); [slice-cloudsmith-ziel-bauen](../open/slice-cloudsmith-ziel-bauen.md) erweitert ihn für beide Ziele, deshalb entfällt er mit dieser Closure nicht, und die Entscheidung über seinen Verbleib geht an die Closure von [slice-cloudsmith-veroeffentlichung-0-1-1](../open/slice-cloudsmith-veroeffentlichung-0-1-1.md) (Vorschlag des Planners 2026-11-15, Rückfrage an den Nutzer offen) |

**Ablauf:** (1) Der Implementer setzt die Version im Release-Commit ohne `-SNAPSHOT`; der Commit gelangt nach `main` (der Workflow bricht, wenn der Tag-Commit nicht in `origin/main` liegt). (2) **Der Nutzer pusht den Tag `v<Version>`; der Workflow läuft** (Job `gates`, danach `publish`). Kein Agent führt `make publish` aus, hält ein Schreib-Token oder pusht den Tag; es gibt kein Token außer `GITHUB_TOKEN` des Runners. (3) Der Nutzer gibt die Lauf-Kennung weiter; Messprotokoll und Nachzüge folgen aus dem Lauf. (4) Folge-Commit setzt `-SNAPSHOT` zurück. Ein erneuter Lauf („Re-run“) für dieselbe Version ist nicht vorgesehen ([ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Entscheidung 2). Der Konsum aus der Registry ist nicht mehr Teil dieses Plans (Neuschnitt, [slice-cloudsmith-veroeffentlichung-0-1-1](../open/slice-cloudsmith-veroeffentlichung-0-1-1.md)).

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): (1) [slice-maven-veroeffentlichung](../done/slice-maven-veroeffentlichung.md), [slice-ci-veroeffentlichung-workflow](../done/slice-ci-veroeffentlichung-workflow.md) und [slice-release-entscheidungen](../done/slice-release-entscheidungen.md) liegen in `done/` (erfüllt, 2026-11-15; der Wortlaut zu E2E, Wächter und zurückgestellter Stückliste in `releasing.md` steht damit, die `releasing.md`-Reihenfolge der Slices ist abgearbeitet; die Liste des Ungemessenen aus dem [Messprotokoll](../../../messungen/veroeffentlichung-dispatch-2026-11-15.md) ohne die entfallene Docker-Version geht an diesen Slice). Verbleibend: (2) **Der Nutzer bestätigt ausdrücklich** die Veröffentlichung nach Version und Zeitpunkt (Wortlaut der Bestätigung steht im Plan-Kopf; eine Annahme der ADRs ist keine Bestätigung der Handlung; die Handlung selbst ist sein Tag-Push, ein Token ist nicht bereitzustellen) und **hat den Tag-Schutz `v*` im Repository gesetzt** (Angabe des Nutzers im Auftrag der Closure von [slice-ci-veroeffentlichung-workflow](../done/slice-ci-veroeffentlichung-workflow.md) 2026-11-15, von keinem Gate und vom Planner nicht geprüft; die Bestätigung gehört in den Plan-Kopf). (3) Ein Verantwortlicher ist eingetragen. (Die `read:packages`-Anleitung war als Liefer-Punkt 3 vorgesehen und liegt nach dem Neuschnitt bei [slice-cloudsmith-veroeffentlichung-0-1-1](../open/slice-cloudsmith-veroeffentlichung-0-1-1.md).)

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß): entfallen mit dem Neuschnitt; der Konsum aus der Registry liegt bei [slice-cloudsmith-veroeffentlichung-0-1-1](../open/slice-cloudsmith-veroeffentlichung-0-1-1.md).
- `in-progress` → `open` (blockiert): die Registry weist das Paket ab (etwa 422 wegen der `artifactId`, fehlende Rechte von `GITHUB_TOKEN` für ein noch nicht bestehendes Paket, ein Fehler des Klons im Workflow) oder der Nutzer zieht die Bestätigung zurück; ein Teilbestand in der Registry ist in §6 benannt. Bei einer Abweisung der CI ist der Rückfall 6a oder eine neue ADR ein Entscheid des Nutzers ([ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Re-Evaluierungs-Trigger), nicht dieses Slice.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag.

Beobachtbar: (1) Der Lauf von `veroeffentlichung.yml` bei Tag-Push ist `success` (das Messprotokoll nennt die Lauf-Kennung), Paket und Version stehen in der Pakete-API, der Tag `v<Version>` steht auf dem Release-Commit. Der Konsum aus der Registry ist kein Kriterium dieses Slice mehr ([slice-cloudsmith-veroeffentlichung-0-1-1](../open/slice-cloudsmith-veroeffentlichung-0-1-1.md)). (2) Alle Punkte der DoD erledigt, `make gates` grün, Review-Report liegt vor, Closure-Notiz mit Lerneintrag geschrieben. Die Closure-Notiz nennt außerdem den Stand zu `tools/publish-probe.sh` (er bleibt über diese Closure hinaus, siehe §3) und hält fest, dass Software-Stückliste und der Weg für Jar und `lib/` ohne Registry nach der ersten Veröffentlichung neu zu bewerten sind (Auftrag aus [`releasing.md`](../../../maintainer/releasing.md), [slice-release-entscheidungen](../done/slice-release-entscheidungen.md)).

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang.

- Die Veröffentlichung gelingt teilweise (Jar liegt, POM fehlt, oder umgekehrt); die Version ist damit verbraucht, und eine Korrektur ist eine neue Version. Die Dateiliste der Version in der Registry ist im Messprotokoll ungemessen; sie misst [slice-cloudsmith-veroeffentlichung-0-1-1](../open/slice-cloudsmith-veroeffentlichung-0-1-1.md) (zweiter Punkt). — **Ausgang:** weiter offen, Träger [slice-cloudsmith-veroeffentlichung-0-1-1](../open/slice-cloudsmith-veroeffentlichung-0-1-1.md) (Dateiliste, Konsum). Kein Teilbestand beobachtet: der Lauf endete `success`, Paket und Version stehen in der Pakete-API; ob Jar, POM und `.module` in der Registry liegen, ist ungemessen.
- Die Verbindung des Pakets mit dem Repository (Zuordnung durch den Workflow) und seine Sichtbarkeit sind nicht geprüft ([ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) Entscheidung 1: Zuordnung über `distributionManagement`/URL nach der gelesenen Seite, nicht gemessen); der Konsum durch einen anderen Nutzer als den Maintainer ist ungemessen; Zuordnung und Sichtbarkeit sind im Messprotokoll belegt, der Konsum liegt bei [slice-cloudsmith-veroeffentlichung-0-1-1](../open/slice-cloudsmith-veroeffentlichung-0-1-1.md). — **Ausgang:** teils eingetreten (als belegt), teils weiter offen. Zuordnung (`repository` `pt9912/tdswire`) und Sichtbarkeit (`public`) sind im Messprotokoll belegt und vom Verifier selbst abgefragt; der Konsum durch einen anderen Nutzer als den Maintainer bleibt ungemessen, Träger [slice-cloudsmith-veroeffentlichung-0-1-1](../open/slice-cloudsmith-veroeffentlichung-0-1-1.md).
- **Rechte von `GITHUB_TOKEN`:** ob `permissions: packages: write` die erste Veröffentlichung eines noch nicht bestehenden Pakets zulässt, ist nach Dokumentationsstand gelesen, nicht gemessen ([ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md), Annahme, die kippt); ebenso der Klon eines Tag-Push für `tools/publish.sh` (losgelöster `HEAD`, Tag lokal) und die Maskierung im Log. — **Ausgang:** entfallen. Gemessen im Tag-Lauf 43699125460: `Packages: write` für das erste Paket, Klon eines Tag-Push mit `tools/publish.sh` (losgelöster `HEAD`, Tag lokal), `GITHUB_TOKEN: ***` im Log (Muster-Suche, nur die genannten Präfixe). Die Annahme der ADR trug.
- **Tag-Schutz `v*` nicht gesetzt oder nicht wirksam:** wer den Tag pusht, löst die Veröffentlichung aus; die Einstellung ist von keinem Gate prüfbar (akzeptiertes Negativ der ADR). — **Ausgang:** weiter offen, Träger [slice-cloudsmith-veroeffentlichung-0-1-1](../open/slice-cloudsmith-veroeffentlichung-0-1-1.md). Nur mit Bypass gemessen (der Push eines Berechtigten gelang, Ausgabe des Nutzers); die Abweisung ohne Bypass und die Wirksamkeit des Schutzes sind ungemessen, und die Gegenprobe an der Oberfläche ist nicht eingetragen. Nicht „entfallen“.
- Konsumenten ohne Konto oder Token können die Bibliothek nicht binden (Hauptkosten der Entscheidung 5a); mit [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) ist der anonyme Weg über Cloudsmith als Zusatz entschieden, gebaut in [slice-cloudsmith-ziel-bauen](../open/slice-cloudsmith-ziel-bauen.md) und gemessen in [slice-cloudsmith-veroeffentlichung-0-1-1](../open/slice-cloudsmith-veroeffentlichung-0-1-1.md). — **Ausgang:** weiter offen, Träger [slice-cloudsmith-veroeffentlichung-0-1-1](../open/slice-cloudsmith-veroeffentlichung-0-1-1.md) (Konsum anonym über Cloudsmith und mit Token, Anleitung, Nutzer-Doku); [slice-cloudsmith-ziel-bauen](../open/slice-cloudsmith-ziel-bauen.md) liefert das Werkzeug.

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register (vorhandene `BEO-<KUERZEL>/<slug>` **zitieren**) ·
`grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln.

Geschlossen 2026-11-15 (Planner, Slice-Closure ohne Welle). Gemergter Stand: `8c73cdb5` auf `main`; Prüfung: Verifikationsbericht [2026-11-15-8c73cdb5](../../../reviews/2026-11-15-8c73cdb5-slice-maven-erste-veroeffentlichung-verifikation.md).

**Geliefert.** Der einzige Liefer-Punkt nach dem Neuschnitt: `0.1.0` liegt in GitHub Packages unter `io.github.pt9912:tdswire` (Paket und Version in der Pakete-API, Zuordnung `pt9912/tdswire`, `public`), veröffentlicht vom Workflow bei Tag-Push `v0.1.0` (Lauf 43699125460, `success`, `gates` rund 13 min 13 s, `publish` rund 1 min 59 s, `vorschau` übersprungen), Release-Commit `b0109953`, danach `-SNAPSHOT` zurück (`c6523593`). Messprotokoll: [veroeffentlichung-tag-lauf-2026-11-15](../../../messungen/veroeffentlichung-tag-lauf-2026-11-15.md). Von diesem Slice nicht berührt: SPEC-052 und SPEC-059 (die Messung widerlegt keine Aussage; Verifikation INFO-3); `src/`, `testserver/`, `.github/` unverändert gegenüber `v0.1.0`.

**Belege und die Lücke.** Die Verifikation (`8c73cdb5`): keine HIGH, keine MEDIUM, 1 LOW (Nachweis-Pflichten vor Closure, hiermit erledigt), 3 INFO; sie erhob Lauf, Jobs, Paket-API, Tag-Ref, Workflow-Text und Laufprotokoll selbst. **Ein separater Review-Report zu diesem Slice liegt nicht vor** (nur Reports zu den Vorgänger-Slices). Der Slice ändert nur Planung und Messprotokolle, `src/` ist unberührt; die Prüfung stützt sich allein auf den Verifier-Bericht, ein Review gegen Plan und ADR ist nicht gelaufen. Das ist die Lücke, sie ist hier benannt und nicht durch einen erfundenen Report gefüllt; der Haken der Review-Pflicht in §2 trägt den Verifikationsbericht als Ersatz, nicht als Review. Sensoren der Closure: `make docs-check`, `make flaeche`, `make kopplung-zahlen`, `make gates` (Ergebnisse im Bericht der Closure); `make clean-build` und `make e2e` nicht erforderlich (Doku, [`harness/README.md`](../../../../harness/README.md) §Minimal agent workflow).

**Was anders lief.** (1) Der Plan wurde am Tag des Laufs nach [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) neu geschnitten: Konsum, Dateiliste und Nutzer-Doku gingen an [slice-cloudsmith-veroeffentlichung-0-1-1](../open/slice-cloudsmith-veroeffentlichung-0-1-1.md) (Zielbild angepasst, Lastenheft unberührt). (2) INFO-1 der Verifikation: die Anfangszeit der Gradle-Aufgabe im Messprotokoll war eine obere Schranke, im Text eine Messung; im Protokoll korrigiert. (3) INFO-2: zwei Runner-Image-Versionen im selben Lauf, das Runner-Image bleibt ungepinnt ([TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), von [ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) als Setzung geführt); keine Behebung, kein Slice.

**Risiken (Ausgänge stehen in §6).** Weiter offen mit Träger [slice-cloudsmith-veroeffentlichung-0-1-1](../open/slice-cloudsmith-veroeffentlichung-0-1-1.md): Teilbestand/Dateiliste (1), Konsum durch Dritte (2), **Tag-Schutz (4, nur mit Bypass gemessen, nicht „entfallen“)**, Konsum ohne Token (5). Entfallen: Rechte, Klon, Maskierung (3). Ungemessen und übernommen aus dem Protokoll: Bruch der Prüfung `origin/main` (Punkt 5), Abweisung ohne Bypass (6), Einstellung „Workflow permissions“ (8), `concurrency` (10), `GITHUB_ACTOR`-Wert (7).

**`tools/publish-probe.sh`:** bleibt über diese Closure hinaus (Nutzerentscheidung 2026-11-14 bis zur Closure; [slice-cloudsmith-ziel-bauen](../open/slice-cloudsmith-ziel-bauen.md) erweitert ihn); die Entscheidung über den Verbleib geht an die Closure von [slice-cloudsmith-veroeffentlichung-0-1-1](../open/slice-cloudsmith-veroeffentlichung-0-1-1.md), Rückfrage an den Nutzer offen. **Software-Stückliste und Weg für Jar und `lib/` ohne Registry** ([`releasing.md`](../../../maintainer/releasing.md), [slice-release-entscheidungen](../done/slice-release-entscheidungen.md)): nach der ersten Veröffentlichung neu zu bewerten; nicht geschehen, Träger ebenfalls [slice-cloudsmith-veroeffentlichung-0-1-1](../open/slice-cloudsmith-veroeffentlichung-0-1-1.md) (dessen Closure-Trigger nennt die Neubewertung).

**Register.** Fortgeschrieben, kein Zähler gesetzt, Zählung folgt aus den Dateien: `BEO-TDS/aussagen-ueber-messung-hinaus` um die Datei `evidence/slice-maven-erste-veroeffentlichung.md` ergänzt (INFO-1, schon verkörpert in `AGENTS.md` §3.8, hier ein weiteres Auftreten). Gesichtet, kein Auftreten: `nachweis-von-der-falschen-seite-der-grenze` (der Lauf zeigt die Gegenseite zum lokalen Repository: Schreiben und Zuordnung an der echten Registry belegt; der Konsum von außen bleibt beim Folge-Slice), `einmal-rot-ohne-gesicherte-ausgabe` (der Tag-Lauf war nicht rot), `lizenzhinweise-der-bibliotheken-rechtsfrage` (nicht Gegenstand).

**Offene Nutzerentscheidungen (nicht vom Planner entschieden).** (1) Gegenprobe des Tag-Schutzes `v*` an der Oberfläche und ohne Bypass (Risiko 4). (2) Ob ein Review-Report nachträglich gewollt ist oder die Verifikation genügt.

**Lerneintrag (Steering Loop).**

- **Benannte Spec-/Planungslücke, kein neuer Sensor:** Ein Ausgang „entfallen“ für ein Risiko, das nur mit Bypass gemessen ist, wäre breiter als der Beleg (`AGENTS.md` §3.8); der Ausgang heißt „weiter offen“ mit Träger. Der Verifier hat dies vor der Closure benannt; gezählt wird nichts neu, die Regel trägt (`BEO-TDS/aussagen-ueber-messung-hinaus`, verkörpert).
- **Bestätigt, keine neue Regel:** Eine Zeit aus dem Log ist die des Zeilenkopfs, nicht des Beginns einer Gradle-Aufgabe; Zeitangaben aus Laufprotokollen nennen ihre Schranke. Auftreten im Register, schon verkörpert.
- **Der Neuschnitt am Lauftag hat getragen:** der Liefer-Punkt, den das Protokoll belegt, schloss ohne das, was erst der Folge-Slice messen kann; was fehlt, steht mit Träger in §6. Folge-Slice-Paarung: `open/slice-cloudsmith-veroeffentlichung-0-1-1.md` (in dessen Start-Trigger (2) ist diese Closure die Bedingung).
- Verkörpert wurde mit diesem Slice keine Regel, daher kein `liegt in` und kein Herkunfts-Anker; Spec-Lücke: keine neue.

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung.

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration führt `*` als einzige Sub-Area (Greenfield).

**Vorgelagert — offene Beobachtungen sichten:** Register (`../observations/BEO-TDS/`, Stand des gemergten `main` vom 2026-11-14) durchgegangen; Treffer: `nachweis-von-der-falschen-seite-der-grenze` (2×; dieser Slice liefert die Gegenseite zu den Belegen gegen das lokale Repository aus [slice-maven-veroeffentlichung](../done/slice-maven-veroeffentlichung.md)), `lizenzhinweise-der-bibliotheken-rechtsfrage` (offen, nicht Gegenstand) und `einmal-rot-ohne-gesicherte-ausgabe` (2×: die Ausgabe eines roten Laufs wird vor dem Neustart gesichert, eine Registry-Ablehnung ist nicht wiederholbar ohne neue Version).

Alle berührten Sub-Areas GF.
