# Slice slice-maven-veroeffentlichung: Maven-Paket der Bibliothek vorbereiten, POM und Prüfung im Bau, Werkzeug zum Veröffentlichen

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** ohne Welle — es gibt keine Closure-Bedingung, die von der DoD dieses Slice verschieden ist (Baseline-Regelwerk `modul-06-roadmap.md` §Wann Arbeit eine Welle braucht). Die Nutzerentscheidungen zu Versionsschema und Registry, die [welle-referenzabgleich-release](../welle-referenzabgleich-release.md) in ihrem Closure-Kriterium nennt, liegen mit [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) vor; ob die Welle den Slice als Träger liest, entscheidet ihre Closure, nicht dieser Plan.

**Bezug:** [TDS-RB-01](../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-RB-05](../../../../spec/lastenheft.md#tds-rb-05--lizenz), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) (Scope), [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) (`Accepted` 2026-11-14, Auftrag „Folgepflicht nach `Accepted`“), [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md), [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md), [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md), [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md), [ADR-0034](../../adr/0034-testserver-als-gradle-modul.md)

**Berührte Spec-Stellen:** SPEC-052 (veröffentlichte Form neben Jar und `lib/`) · SPEC-059 (Zeilen „Abhängigkeiten“ und „Zusage“: Versionsschema `0.y.z`, POM-Form)

**Verantwortlich:** pt9912

**Autor:** Planner-Agent, Auftrag des Nutzers 2026-11-14. **Datum:** 2026-11-14.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Die Bibliothek ist als Maven-Paket veröffentlichungsfähig, und das Veröffentlichen ist als Werkzeug vorbereitet und belegt, **ohne dass etwas in die Registry geschrieben wird**: Der Bau erzeugt und prüft die POM, `make publish-vorschau` baut Jar und POM im Container in ein lokales Maven-Repository, und `make publish` bricht vor dem Netz, wenn Version, Tag, Baum oder Token nicht stimmen ([ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md), Entscheidungen 1a, 2a, 3a, 4a, 5a, 6a, 8a, vom Nutzer am 2026-11-14 angenommen).

**Abgrenzung zu [slice-release-entscheidungen](../open/slice-release-entscheidungen.md)** (dieser Plan entscheidet dessen Punkte nicht; er nennt, was [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) zu ihnen schon gesagt hat und was offen bleibt):

| Punkt in `slice-release-entscheidungen` | Stand nach [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) | Träger |
|---|---|---|
| Versionsschema, Beginn, Umgang mit `-SNAPSHOT`, Form des Tags | durch die ADR entschieden (Entscheidung 3a: `0.y.z`, Release-Commit setzt die Version ohne `-SNAPSHOT`, Tag `v<Version>` auf diesem Commit, nie ein `-SNAPSHOT` veröffentlicht); der Eintrag in `releasing.md` (Schritte „Version“ und „Tag und Vermerk“) steht noch aus | dieser Slice (Nachzug der Anleitung, Eingangsskript prüft die Form); ob `slice-release-entscheidungen` den Punkt damit als erledigt führt, entscheidet dessen Plan |
| Registry | durch die ADR entschieden (GitHub Packages, 5a) | dieser Slice (Werkzeug), [slice-maven-erste-veroeffentlichung](../open/slice-maven-erste-veroeffentlichung.md) (Handlung) |
| Signierung | durch die ADR entschieden (4a: keine) | — |
| Weg, Jar und `lib/` aus dem Bau herauszugeben | **offen für den Handweg ohne Registry** (die ADR lässt `lib/` und `docker cp` stehen, 2a); die Registry ist ein zweiter Weg zum Jar | `slice-release-entscheidungen` |
| Software-Stückliste | **offen** (die ADR nennt sie „nicht Gegenstand“) | `slice-release-entscheidungen` |
| Ist `make e2e` Teil eines Releases | **offen** (die ADR sagt nur: `make gates` vor `make publish` ist Setzung, das Target prüft es nicht) | `slice-release-entscheidungen` |
| Form des Wächters für [ADR-0010](../../adr/0010-detekt-pre-release.md) | **offen**, von der ADR nicht berührt | `slice-release-entscheidungen` |
| Risiko „Registry widerspräche [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md)“ | von [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) gelesen: kein ausschließender Satz in [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md), `releasing.md` war breiter als ihr Beleg | — |

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- **Die erste echte Veröffentlichung** (Schreiben in die Registry, Tag setzen), die Probe eines Konsumenten gegen die Registry und das Setup mit `read:packages` in der Nutzer-Anleitung — [slice-maven-erste-veroeffentlichung](../open/slice-maven-erste-veroeffentlichung.md). Begründung: Die Handlung wirkt nach außen und ist nicht rücknehmbar (eine veröffentlichte Version wird nie neu veröffentlicht, [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) Entscheidung 3a) und braucht die ausdrückliche Bestätigung des Nutzers. Ein DoD-Punkt, dessen Haken an dieser Bestätigung hängt, ließe den Slice offen in `in-progress/` stehen oder zwänge einen Haken ohne Beleg; der Plan trennt Entscheidung und Lieferung (`/plan-welle` Schritt 6b, Frage 2). Die Anleitung für Konsumenten wäre vor der ersten Veröffentlichung eine Aussage über einen Weg, der noch nicht trägt (`AGENTS.md` §5 Regel 5, §3.8). *Alternative, die der Plan verworfen hat:* ein vierter Liefer-Punkt „Veröffentlichung durch den Nutzer bestätigt“ in diesem Slice; er hätte die Grenze von drei Liefer-Punkten gebrochen und den Slice an eine Handlung außerhalb des Repos gebunden. *Zweite Alternative:* derselbe Schritt als ungezählter Nachweis am Ende dieses Slice; er hängt den Closure-Trigger an die Nutzerhandlung und verlöre die Eigenschaft, dass die Vorbereitung einzeln lieferbar ist.
- **Kein Lauf von `make publish` mit einem echten Token** in diesem Slice, durch keine Rolle. Belegläufe nutzen einen Platzhalter und ein Ziel, das nicht GitHub ist, oder `publishToMavenLocal` ([ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) Entscheidung 6, „Beleg im Folge-Slice“).
- **Die Änderung der Version im Quelltext:** `version` bleibt `0.1.0-SNAPSHOT`; der Release-Commit setzt sie in [slice-maven-erste-veroeffentlichung](../open/slice-maven-erste-veroeffentlichung.md).
- **Signierung, `sources`- und `javadoc`-Jar** — Entscheidung 4a; Trigger: Entscheid für Maven Central ([ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md), Re-Evaluierungs-Trigger).
- **Ein Werkzeug für Binärkompatibilität** — Entscheidung 8a; Trigger: Entwurf von `1.0`.
- **Ein Workflow bei Tag-Push mit `GITHUB_TOKEN`** — Option 6c; Trigger: regelmäßige Veröffentlichungen.
- **Veröffentlichung von `:testserver` und des Test-Images** — [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md), [ADR-0034](../../adr/0034-testserver-als-gradle-modul.md); das Modul bekommt kein `maven-publish`.
- **Änderung von `Class-Path` und `lib/`** (Optionen 2b, 2c) — Trigger: der Beleg, dass der `Class-Path` im Registry-Jar Konsumenten stört; dann eine neue ADR mit `Supersedes`, nicht dieser Slice.
- **Änderung des Lastenhefts** — die Zielbild-Prüfung der ADR empfiehlt „Code angleichen“; Lastenheft bleibt unverändert, die Lücke „Konsum aus der Registry“ ist in der ADR benannt und wird im Folge-Slice gemessen.
- **Lizenzhinweise zu den Bibliotheken** (Rechtsfrage, [ADR-0018](../../adr/0018-laufzeit-artefakt.md) §Offen) — die POM nennt die Lizenz der Bibliothek (MIT), keine Aussage über die ihrer Abhängigkeiten.

**Keine Mindestzahl.** Ein Slice mit *einem* echten Ausschluss ist besser als
einer mit vier erfundenen; die vier Klassen sind ein Suchraster, keine
Ausfüll-Liste.

Was hier steht, ist die Grenze, an der ein wachsender Slice sich messen lässt:
Wer später etwas mitnimmt, das hier ausgeschlossen war, hat den Plan
**geändert**, nicht nur ergänzt.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **POM und Prüfung im Bau:** Das Wurzelprojekt wendet `maven-publish` an; `publishing {}` führt `tdswire.jar` mit einer POM (Koordinaten `io.github.pt9912:tdswire`, Lizenz MIT mit URL, Projekt-URL, `scm`); das Modul `:testserver` bekommt kein `maven-publish`. Der Scope von `kotlinx-coroutines-core` (`api` oder `implementation`) ist gemessen (POM-Eintrag, Wirkung auf `gradle.lockfile`) und im Messprotokoll entschieden. Die Stage `build-jar` erzeugt die POM ohne Netz und ohne Token (`generatePomFileForMavenPublication`); `tools/manifest-pruefung.sh` liest sie und weist ab: eine Abhängigkeit mit `jackson`, eine auf `testserver`, eine fehlende MIT-Lizenzangabe; `tools/manifest-selbsttest.sh` weist je Regel eine Mutation ab (rot) und nimmt die gute POM an. `gradle.lockfile`, `settings-gradle.lockfile` und `gradle/verification-metadata.xml` bleiben unverändert, oder die Änderung ist gemessen und benannt ([ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) Kontext: Setzung „kein neuer Eintrag“).
- [ ] **Werkzeug zum Veröffentlichen:** Die Dockerfile-Stage `publish` (Jar, Gradle, Abhängigkeiten, **kein Token**); `make publish-vorschau` (`publishToMavenLocal` im Container, kein Token, kein Netz zu GitHub, gibt die POM aus) und `make publish` (Registry; Token nur namentlich als `-e GITHUB_ACTOR -e GITHUB_TOKEN`, nie als Argument, nichts davon in einem Layer oder im Log); beide **kein Gate**: nicht in `GATE_CHECKS`, nicht in `make e2e`. Das Eingangsskript von `make publish` bricht vor dem Netz bei: Version auf `-SNAPSHOT`, Tag `v<Version>` nicht auf `HEAD`, unsauberem Baum, leerem oder fehlendem `GITHUB_TOKEN`; sein Selbsttest läuft in `make skript-selbsttest` und weist je Prüfung einen roten Fall ab und nimmt den guten an. Die beiden Targets stehen in der Werkzeug-Tabelle von `harness/README.md` (Bindung [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md)); die Eingangslisten unter `tools/stempel/` tragen die neue Stage und das neue Make-Fragment (`make stempel-eingaenge-pruefung` grün).
- [ ] **Belege und Nachzug:** Ein Messprotokoll unter `docs/messungen/` (Datum, Host, Bedingung; der Erzeuger liegt im Repo, siehe §3) belegt: (a) den `Class-Path` mit fehlenden Einträgen im Jar des Registry-Wegs (Option 2a) — wie sich ein Konsument verhält, ohne dass `lib/` daneben liegt; (b) einen Probe-Konsumenten, der die Bibliothek nur über `publishToMavenLocal` und die POM bindet, übersetzt und läuft; (c) den Platzhalter-Token-Beleg: `docker history --no-trunc` des Images der Stage `publish` und das Log des Laufs enthalten den Platzhalter nicht, ein leeres Token bricht vor dem Netz; (d) das Gradle-Log eines Laufs mit Platzhalter-Token enthält ihn nicht (kein `--info`, kein `--debug`). `docs/maintainer/releasing.md` (Kopfsatz, Schritte 5, 6 und 8, „Nicht Teil dieses Ablaufs“, „Offene Entscheidungen“), `SPEC-052` und `SPEC-059` (Versionsschema `0.y.z`, veröffentlichte Form mit Abhängigkeiten in der POM) sind auf den Stand gebracht; alle Dateien unter `docs/user/` sind komplett gegen den Stand nach dem Slice gelesen und enthalten keine Aussage über einen Registry-Weg, der noch keine Version trägt (`AGENTS.md` §5 Regel 5; das Setup mit `read:packages` kommt mit [slice-maven-erste-veroeffentlichung](../open/slice-maven-erste-veroeffentlichung.md)).

Nachweis-Pflichten für alle drei Punkte:

- [ ] Jede Zusage der drei Punkte hat einen roten Gegenfall: eine Mutation am Prüfer (POM-Prüfung, Eingangsskript), keine Zusage ohne ihn; wo eine Aussage nur einmal gemessen ist, steht das so im Messprotokoll (`AGENTS.md` §3.8).
- [ ] `make gates` grün; die Pflicht-Sensoren je Art der Änderung ([`harness/README.md`](../../../../harness/README.md) §Minimal agent workflow): die engsten (`make build`, `make skript-selbsttest`) und bei Doku und Spec `make docs-check`, `make flaeche`, `make kopplung-zahlen`; `make spec-abdeckung` entfällt (keine neue oder entfernte `SPEC-*`-Kennung, keine Tests oder Tags).
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist (hier: die Punkte oben).
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

Pfade und Namen sind Vorschläge des Planners aus [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md); der Implementer erweitert die Liste im ersten Lauf und misst den Ist-Stand, bevor er sie übernimmt.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `build.gradle.kts` | update | `maven-publish`, `publishing {}` mit POM-Metadaten (Lizenz, URL, `scm`), Scope von coroutines nach Messung; jede Änderung der Datei lässt die Stage `deps` neu laufen (Kopplung im `Dockerfile`) |
| `testserver/build.gradle.kts` | unverändert | kein `maven-publish` im Modul ([ADR-0034](../../adr/0034-testserver-als-gradle-modul.md)); belegt, falls `publishing` im Wurzelprojekt nicht in das Modul wirkt |
| `gradle.lockfile`, `settings-gradle.lockfile`, `gradle/verification-metadata.xml`, `tools/mk/build.mk` (`DEPS_TASKS`) | unverändert erwartet | Lösen die Tasks von `maven-publish` Konfigurationen auf, die Lock und Prüfsummen nicht kennen, folgt `make deps-lock` (braucht Netz) und die Änderung wird im Protokoll benannt |
| `Dockerfile` (`build-jar`) | update | erzeugt die POM und ruft die erweiterte Prüfung |
| `Dockerfile` (neue Stage `publish`) | neu | Image aus Jar, Gradle, Abhängigkeiten, ohne Token; `COPY`-Quellen in die Eingangslisten |
| `tools/manifest-pruefung.sh`, `tools/manifest-selbsttest.sh` | update | POM lesen: kein `jackson`, keine Abhängigkeit auf `testserver`, Lizenz MIT; Mutation je Regel (Zusage: [TDS-RB-05](../../../../spec/lastenheft.md#tds-rb-05--lizenz), [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) Entscheidung 2) |
| `tools/publish.sh`, `tools/publish-selbsttest.sh` | neu | Eingangsskript (Version, Tag, Baum, Token) und sein Selbsttest mit rotem Fall je Prüfung; ein Ziel für Belegläufe, das nicht GitHub ist, ist ein Parameter des Eingangsskripts, nicht der Default |
| `tools/mk/publish.mk` | neu | `publish-vorschau` und `publish`, kein `GATE_CHECKS`, nicht in `tools/mk/e2e.mk`; Eingabe `GITHUB_TOKEN` nur namentlich |
| `tools/mk/skript-selbsttest.mk`, `Dockerfile` (Stage `skript-selbsttest`) | update | Selbsttest des Eingangsskripts in `make skript-selbsttest` (Klasse `werkzeug`) |
| `tools/stempel/werkzeug.eingaenge`, `tools/stempel/tests.eingaenge`, `tools/stempel/stempel.eingaenge` | update | neue Skripte, Stage und Fragment als Eingang; `make stempel-eingaenge-pruefung` hält sie gegen die `COPY`-Quellen |
| `tools/publish-probe.sh` | neu | Erzeuger der Messung (b): Probe-Konsument gegen `publishToMavenLocal` und, im Folge-Slice, gegen die Registry; liegt im Repo, damit die Messung wiederholbar ist (`/plan-welle` Schritt 6d); kein Target, kein Gate |
| `harness/README.md` | update | Werkzeug-Tabelle: Zeilen für `make publish-vorschau` und `make publish` (kein Gate, Bindung [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md)), Zeile von `make skript-selbsttest` um das Eingangsskript |
| `docs/messungen/` (neues Messprotokoll) | neu | die Messungen (a) bis (d) und die Wahl des Scopes von coroutines |
| `docs/maintainer/releasing.md` | update | Kopfsatz „einen Registry-Weg gibt es nicht“, Schritte 5, 6 und 8, „Nicht Teil dieses Ablaufs“, „Offene Entscheidungen“ (entschiedene Punkte heraus, offene bleiben: Stückliste, `make e2e` im Release, Handweg, Wächter) |
| `spec/spezifikation.md` (`SPEC-052`, `SPEC-059`) | update | veröffentlichte Form neben Jar und `lib/`; Zeile „Abhängigkeiten“ und „Zusage“: Schema `0.y.z`; `make flaeche` bleibt bei der Zahl der Liste (kein neuer Typname) |
| `docs/user/` (alle Dateien) | geprüft, nur bei falscher Aussage ändern | `/plan-welle` Schritt 6e; das Setup mit `read:packages` steht erst im Folge-Slice |

**Ablauf (Rollen, `/implement-slice`):** Implementer baut und belegt; Reviewer; Verifier fährt `make clean-build` und `make e2e` je einmal auf dem End-Stand (`harness/README.md` §Minimal agent workflow). Enthält die Behebung der Review- oder Verifikationsbefunde Code (Quelltext, Skripte, Make-, Build-, Konfigurationsdateien), folgt ein Re-Review in frischem Kontext; reine Doku-Korrekturen brauchen ihn nicht; unterbleibt er, führt die Closure den Vermerk „Nachbesserung ohne zweiten Review“. Höchstens zwei Gradle-Agenten gleichzeitig; keine künstliche Last auf dem Host; Läufe im Vordergrund mit Gesamtlaufzeit und größten Zeitposten im Bericht (`AGENTS.md` §6).

**Abweichung bei der Umsetzung:** Die Eingangslisten `tools/stempel/*.eingaenge` sind nicht geändert; sie führen `tools`, `Dockerfile`, `harness/README.md` und `build.gradle.kts` als Ganzes und überdecken die neuen Dateien, `make stempel-eingaenge-pruefung` ist grün (Review F-5).

**Erzeuger der Messzahlen (`/plan-welle` Schritt 6d):** `tools/publish-probe.sh`, die Stage `publish` und das Eingangsskript liegen im Repo; die Messungen sind damit wiederholbar. Läuft der Probe-Konsument ersatzweise außerhalb des Repos (Scratchpad), trägt seine Zahl nur als Größenordnung, und das Messprotokoll sagt es.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`open` → `next`, dann `next` → `in-progress`): Der Nutzer hat den Slice priorisiert und einen Verantwortlichen eingetragen; [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) steht auf `Accepted` (seit 2026-11-14, erfüllt); kein anderer Slice in `in-progress/` ändert `build.gradle.kts`, das `Dockerfile` oder `tools/mk/` (WIP-Limit 1 je Rolleninhaber, und ein Merge-Konflikt in diesen Dateien wäre ein Konflikt im Code). Der Start verlangt **keine** Handlung an der Registry und kein Token.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): Die drei Liefer-Punkte sind nicht in einer Review-Sitzung prüfbar oder ein vierter wird nötig. Schnittvorschlag: Punkt 2 (Werkzeug zum Veröffentlichen) als eigener Slice nach Punkt 1; Punkt 3 (Belege und Nachzug) bleibt am Slice, der die Aussagen liefert.
- `in-progress` → `open` (blockiert — Carveout?): Die Messung (a) zeigt, dass der `Class-Path` mit fehlenden Einträgen einen Konsumenten stört; das ist der Trigger für Option 2b oder 2c in [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) und braucht eine neue ADR mit `Supersedes` des Halbsatzes von [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md). Ebenso, wenn `maven-publish` ein Plugin in Lock oder Prüfsummen zieht, das der Nutzer nicht gewollt hat.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Beobachtbar: (1) Alle Punkte der DoD sind erledigt, `make gates` ist grün, der Review-Report liegt vor, und der Verifier hat `make clean-build` und `make e2e` je einmal auf dem End-Stand gefahren. (2) `make publish-vorschau` läuft und gibt die POM aus; das Messprotokoll liegt unter `docs/messungen/`; **nichts wurde nach GitHub geschrieben und kein Tag gesetzt** (Beleg: das Protokoll nennt, gegen welches Ziel jeder Lauf ging). Dazu der Lerneintrag in §7 (geschärfte Regel, neuer Sensor oder benannte Spec-Lücke) und [slice-maven-erste-veroeffentlichung](../open/slice-maven-erste-veroeffentlichung.md) als Datei in `open/` (die Folge-Slice-Paarung).

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Die außenwirksame Handlung gerät in den Lauf: ein Agent erreicht ein echtes Token (Umgebung, Datei) und `make publish` schreibt eine Version, die nicht zurückzunehmen ist ([ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md): eine veröffentlichte Version wird nie neu veröffentlicht). Gegenmaßnahme im Plan: kein echtes Token in diesem Slice, Belegläufe nur mit Platzhalter gegen ein Ziel, das nicht GitHub ist; das Eingangsskript bricht bei `-SNAPSHOT`, und die Version bleibt `0.1.0-SNAPSHOT`. — **Ausgang:** offen bis zur Closure.
- Der Scope von `kotlinx-coroutines-core` ist ungemessen: als `implementation` landet er als Laufzeit-Abhängigkeit in der POM, ein Konsument, der gegen `Flow` übersetzt, braucht ihn zur Übersetzungszeit; `api` ändert die POM und eventuell die Zeile „Abhängigkeiten“ von `SPEC-059`. — **Ausgang:** offen bis zur Closure.
- Der `Class-Path` mit fehlenden Einträgen im Registry-Jar ist nur nach Lesart der Jar-Spezifikation wirkungslos (nicht gemessen, [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) Entscheidung 2a); die Messung (a) kann rot ausfallen. Sie läuft mit Probe-Konsumenten der eigenen Bauart (Kotlin, Gradle); ein Java- oder Maven-Konsument bleibt ungemessen (akzeptiertes Negativ der ADR). — **Ausgang:** offen bis zur Closure.
- `maven-publish` ist ein Plugin der Gradle-Distribution; ob Lock und Prüfsummen unverändert bleiben, ist Setzung der ADR und im Bau zu belegen (siehe DoD Punkt 1). — **Ausgang:** offen bis zur Closure.
- Der Platzhalter-Beleg findet nur den Platzhalter in seiner Form, nicht eine abgeleitete Form des Tokens (etwa Base64 im Header); die Zeile der Beobachtung `BEO-TDS/redaktion-sucht-nur-den-bekannten-wert` (verkörpert im Reviewer-Skill) gilt: der Review prüft, welche Formen das Protokoll durchsucht, und das Protokoll nennt die Grenze. Das Token ist im laufenden Container per `docker inspect` und `/proc` sichtbar (von der ADR akzeptiert, derselbe Host-Nutzer). — **Ausgang:** offen bis zur Closure.
- Belege gegen `publishToMavenLocal` oder ein anderes Ziel als GitHub tragen den Weg zu GitHub nicht (Beobachtung `BEO-TDS/nachweis-von-der-falschen-seite-der-grenze`); die Lücke ist die Zielbild-Prüfung zu [TDS-RB-01](../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache) („Konsum aus der Registry“), benannt in der ADR, im Folge-Slice zu messen. — **Ausgang:** offen bis zur Closure.
- Die neue Stage `publish` und das neue Fragment stehen in keiner Eingangsliste, ohne dass ein Gate rot wird (Beobachtung `BEO-TDS/eingangsliste-je-stage-von-hand`, 3×, offen); die DoD verlangt `make stempel-eingaenge-pruefung` grün, die Prüfung hält aber nur die `COPY`-Quellen und Make-Fragmente der Klassen. — **Ausgang:** offen bis zur Closure.
- `make publish` ist kein Gate und steht nicht in `GATE_CHECKS`; dass es dort nicht steht, hält kein Sensor (`make gate-zuordnung` prüft Gates, nicht die Abwesenheit; [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) Fitness Function: „nur Absicht, im Review“). Verwandt mit `BEO-TDS/zusage-ohne-sensor-in-der-ci-kette` (6×, offen, Entscheidung beim Nutzer). — **Ausgang:** offen bis zur Closure.
- Bis [slice-maven-erste-veroeffentlichung](../open/slice-maven-erste-veroeffentlichung.md) schließt, sagt `docs/maintainer/releasing.md` „vorbereitet, nicht veröffentlicht“ und die Nutzer-Anleitung nennt keinen Registry-Weg; ein Leser, der die Registry sucht, findet in diesem Zwischenstand keinen Weg. — **Ausgang:** offen bis zur Closure.

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register (vorhandene `BEO-<KUERZEL>/<slug>` **zitieren** statt neu
formulieren) · `grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln
(das Feld `liegt in` steht **nur**, wenn mit diesem Slice wirklich etwas verkörpert
wurde; Feld und Zielort auf **einer** Zeile).

Wird bei der Closure gefüllt.

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung — dort die **zwei vorgelagerten
Schritte** (sie stehen in jedem Slice-Plan, unabhängig von Modus und
Slice-Typ) und die **vier Pflichtkriterien** (Konventionen-Dichte ·
Phase-Reife · Evidenz-/Diskrepanz-Risiko · Reconciliation-Aufwand), vier und
nicht mehr.

**Der Abschnitt selbst entfällt nie.** Die zwei vorgelagerten Prüfungen laufen
in **jedem** Slice-Plan — sie hängen weder am Modus noch am Slice-Typ. Bedingt
ist allein der Modus-Begründungsblock am Ende; deshalb nennt der Titel beide
Hälften.

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` führt `*` als einzige Sub-Area (Kürzel `TDS`, Greenfield). Berührt sind Bau und Werkzeuge (`build.gradle.kts`, `Dockerfile`, `tools/`), Doku und Spec; sie liegen in dieser einen Sub-Area, eine Ausdifferenzierung hat keinen Beleg im Repo.

**Vorgelagert — offene Beobachtungen sichten:** Register (`../observations/BEO-TDS/`, Stand des gemergten `main` vom 2026-11-14) durchgegangen. Treffer, je mit Zähler-Stand laut `evidence/` und mit der Evidenz-Pflicht der Closure (ob dieser Slice eine weitere Datei schreibt; der Zähler wird nicht gesetzt):

- `skript-pruefung-ohne-rot-gegenprobe` (1×): Eingangsskript und POM-Prüfung sind Prüfer ohne Gegenprobe, wenn sie ohne roten Fall ausgeliefert werden; die DoD verlangt eine Mutation je Zusage. Fällt eine Prüfung ohne Gegenfall auf, ist das die zweite Datei.
- `jackson-abhaengigkeit-fuer-embedder-nicht-gemessen` (2×): Der Klassenpfad ohne Jackson ist für die Form `lib/` und das Lock belegt, für die POM nicht; der Slice liefert den POM-Anteil (Prüfung im Bau). Die Closure liest, ob die Beobachtung damit für die POM-Form gelöst ist; erscheint sie neu (etwa Jackson in der POM), erreicht sie 3× und braucht einen eigenen Slice.
- `eingangsliste-je-stage-von-hand` (3×, über der Schwelle, offen): die neue Stage `publish` und das Fragment sind Eingänge der Listen; steht eine Datei nicht in der Liste und die Prüfung meldet es nicht, ist das eine weitere Datei. Die Zeile der Beobachtung ist gegen den Ist-Stand zu lesen (`make stempel-eingaenge-pruefung` besteht), bevor sie in die Closure übernommen wird.
- `zusage-ohne-sensor-in-der-ci-kette` (6×, offen, Entscheidung beim Nutzer): die Abwesenheit von `make publish` in `GATE_CHECKS` hat keinen Sensor (siehe §6); eine weitere Datei nur, wenn die Closure sie als Zusage ohne Sensor liest.
- `nachweis-von-der-falschen-seite-der-grenze` (2×): Belege gegen das lokale Repository statt gegen die Registry (siehe §6).
- `redaktion-sucht-nur-den-bekannten-wert` (3×, verkörpert im Reviewer-Skill): der Platzhalter-Beleg; der Review wendet die Regel an.
- `spec-059-zusagen-vor-produktschnitt` (1×) und `zusage-container-teil-spec-052-ohne-test` (1×): der Nachzug von `SPEC-052` und `SPEC-059` liest diese Stellen mit; beide sind offen und werden hier nicht gelöst.
- `lizenzhinweise-der-bibliotheken-rechtsfrage`: die POM nennt nur die Lizenz der Bibliothek (siehe §1).
- `einmal-rot-ohne-gesicherte-ausgabe` (2×) und `folgepflicht-in-dod-ohne-messweg` (1×): jeder Messlauf sichert seine Ausgabe vor einem Neustart, und jede Messung der DoD hat ihren Weg im Plan (§3, Erzeuger der Messzahlen).

Alle berührten Sub-Areas GF.
