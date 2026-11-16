# Slice slice-release-job-im-workflow-bauen: Job `release` im Workflow, Eingangsskript und feste Notiz bauen, ohne Wirkung nach außen belegt

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** ohne Welle — keine Closure-Bedingung, die von der DoD dieses Slice verschieden ist. Gewählt wie bei den Veröffentlichungs-Slices zu [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) und [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) (auch sie ohne Welle); [welle-referenzabgleich-release](../welle-referenzabgleich-release.md) führt diese Slices in ihrer Liste nicht, und [welle-qualitaet-grundlage](../welle-qualitaet-grundlage.md) trägt Qualitätsmessungen, kein Veröffentlichen. Offene Wellen und ihre Slice-Listen am 2026-11-16 gelesen.

**Bezug:** [TDS-RB-05](../../../../spec/lastenheft.md#tds-rb-05--lizenz) (Scope), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) (Scope: Veröffentlichen ist Werkzeug außerhalb der Gates, Bau in Docker), [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) (`Accepted` 2026-11-16, Entscheidungen 1, 4 und 6, Folgepflicht), [ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) (Ablauf bei Tag-Push, Rechte je Job, erweitert), [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) (Entscheidungen 2a und 3a: Rechte-Minimierung und Secret-Fenster bleiben unberührt), [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) (Alternative 5b: ausgeführt), [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md) (nicht in den Gates), [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md) (Selbsttest der Klasse `werkzeug`).

**Berührte Spec-Stellen:** — (`spec/` nennt weder Workflow noch Release; [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) schärft keine Spec-Stelle, am 2026-11-16 gelesen).

**Verantwortlich:** —

**Autor:** Planner-Agent, Auftrag des Nutzers 2026-11-16 nach [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) (`Accepted` 2026-11-16, Folgepflicht). **Datum:** 2026-11-16.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** `.github/workflows/veroeffentlichung.yml` führt nach den beiden Paketen einen Job `release`, der aus den Dateien von `make release-dateien` ([slice-release-dateien-bauen](slice-release-dateien-bauen.md)) ein GitHub Release mit fester Notiz anlegt; ein Eingangsskript prüft vorher Tag, Dateien, Prüfsummen und dass zum Tag noch kein Release besteht, und bricht sonst ohne Überschreiben. Belegt ist das ohne Wirkung nach außen: gegen einen `gh`-Stub in Wegwerf-Repos und mit `make workflow-lint`, nie gegen GitHub, wie [ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) es für den ersten Workflow getan hat.

**Warum ein eigener Slice, getrennt vom Bau und von der ersten Lieferung:** Der Bau ([slice-release-dateien-bauen](slice-release-dateien-bauen.md)) hat keine Außenwirkung und kein Recht; dieser Slice führt das **neue Recht `contents: write`** ein und ist der Ort, an dem der Review die Punkte prüft, die der Linter nicht prüft (Pins, Reihenfolge `needs`, Bedingung, Rechte und Token-Fenster je Job). Die erste Lieferung ([slice-release-im-tag-lauf-messen](slice-release-im-tag-lauf-messen.md)) legt ein Release auf der öffentlichen Seite an und verbraucht eine Version auf zwei Registries; was dabei rot werden kann, soll vorher rot geworden sein.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Tag, Release-Commit, Push, Dispatch-Lauf, Tag-Lauf, jedes echte `gh release create`, Messprotokoll, Mess-Pflichten 1 bis 4, 6 und 7 — [slice-release-im-tag-lauf-messen](slice-release-im-tag-lauf-messen.md). Grund: sie setzen die Außenwirkung voraus, die dieser Slice nie ausführt; ein Agent hält kein Schreib-Token und pusht keinen Tag.
- Die nachträglichen Releases `v0.1.0` und `v0.1.1` — [slice-release-nachtraeglich-bestandstags](slice-release-nachtraeglich-bestandstags.md). Grund: [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) Entscheidung 5 verlegt sie nach die erste Lieferung des neuen Formats und unter ausdrückliche Bestätigung des Nutzers. Dieser Slice liefert nur den **Modus ohne Dateien** des Eingangsskripts, den [slice-release-nachtraeglich-bestandstags](slice-release-nachtraeglich-bestandstags.md) aufruft (die Notiz-Vorlage trägt die Variante, [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) Entscheidung 5), und belegt ihn am Stub.
- Der Bau der Dateien, die Prüfungen am Archiv, die Lizenzdateien — [slice-release-dateien-bauen](slice-release-dateien-bauen.md) und [slice-release-lizenzen-lib-belegen](../done/slice-release-lizenzen-lib-belegen.md). Grund: dieser Slice ruft `make release-dateien` nur auf.
- Ein neues Gate: `make release-dateien` und das Eingangsskript stehen in keiner Gate-Liste, der Workflow ist kein Eingang von `make gates` ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md), [ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Entscheidung 3); der Selbsttest wird erweitert, keine Schwelle gesenkt ([`AGENTS.md`](../../../../AGENTS.md) §3.6).
- Ein persönliches Token, OIDC, eine zusätzliche Action ohne Commit-SHA, `--generate-notes`, `--clobber`, Löschen eines Releases: [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) nennt sie nicht gewählt. Der Annahme, dass `GITHUB_TOKEN` mit `contents: write` ein Release anlegen darf, steht [slice-release-im-tag-lauf-messen](slice-release-im-tag-lauf-messen.md) gegenüber; kippt sie, ist es ein Re-Evaluierungs-Trigger von [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md).
- Tag-Schutz `v*` und die Einstellung „Workflow permissions“ des Repositories: Handlung des Nutzers ohne Sensor ([`releasing.md`](../../../maintainer/releasing.md) §Tag-Schutz); der Slice beschreibt das neue Recht dort, setzt aber nichts.
- `docs/user/`: Aussagen wie „die Anhänge sind anonym ladbar“ sind ungemessen (Mess-Pflicht 1), also bleibt die Nutzer-Doku unverändert, bis [slice-release-im-tag-lauf-messen](slice-release-im-tag-lauf-messen.md) den Beleg hat (Setzung dieses Plans, wie bei [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) Entscheidung 5).

**Zielbild-Prüfung** (Maßstab: das Zielbild führt, Code folgt; „Zielbild anpassen“ nur mit Entscheidung des Nutzers):

| Stelle | Code angleichen | Zielbild anpassen | Entscheid |
|---|---|---|---|
| [`releasing.md`](../../../maintainer/releasing.md) Schritt 8 und 9, §Veröffentlichen in der CI (Jobs, Rechte, Teilfehler), §Tag-Schutz (neues Recht `contents: write`), §Offene Entscheidungen („Weg für Jar und `lib/` ohne Registry zurückgestellt“) | Sätze nachziehen, im Zustand „gebaut, nicht gelaufen“; die Teilfehler-Anleitung für `release` steht als Absicht, nicht als Messung | — | Nachzug, nichts zu entscheiden |
| [`harness/README.md`](../../../../harness/README.md) §Sensors: Zeile `.github/workflows/veroeffentlichung.yml` (Job `release`, nie gelaufen), Zeile `make skript-selbsttest`, neue Zeile für das Eingangsskript, falls es ein Make-Target bekommt | Zeilen nachziehen, Bindung [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) | — | Nachzug, nichts zu entscheiden |
| [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) (Veröffentlichen braucht Netz und Schlüssel, ist nicht wiederholbar) | Veröffentlichen bleibt Werkzeug außerhalb der Gates, Bau in Docker | — | unverändert |

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **Eingangsskript und feste Notiz:** Ein Skript (Name und Ort wählt der Implementer, Muster `tools/publish.sh`) prüft vor jedem Aufruf von `gh`: der Tag ist `v<Version aus build.gradle.kts>`, die drei Dateien liegen im angegebenen Verzeichnis, `sha256sum -c SHA256SUMS` ist grün, und zum Tag besteht noch kein Release (Abfrage vor dem Anlegen; besteht eines, Abbruch mit klarer Meldung ohne Überschreiben, kein `--clobber`, kein Löschen). Dann füllt es die Notiz aus einer Vorlagedatei im Repo (Version, Tag, Commit, Koordinaten `io.github.pt9912:tdswire`, Links auf beide Pakete aus den Messprotokollen, nicht aus dem Gedächtnis, Prüfsummen von ZIP und Jar, der Satz „die Prüfsumme belegt Unverändertheit, nicht Herkunft“, Lizenz MIT mit Verweis auf `LICENSE`, ein Zeiger auf die Einbettungs-Anleitung; **keine** Aussage über „anonym ladbar“, Gleichheit mit dem Paket-Jar oder die Lizenzen von `lib/`; wo ein Linkziel nicht belegt ist, steht der Koordinatentext ohne Link) und ruft `gh release create <Tag> <Dateien…> --verify-tag` **in einem Aufruf**, das Token nur im `env:` dieses einen Aufrufs. Das Skript hat einen **Modus ohne Dateien** (Notiz ohne Dateizeilen mit dem Satz, dass für diese Version keine Dateien anliegen und die Version in GitHub Packages, für `0.1.1` auch in Cloudsmith, steht; kein Anhang), den [slice-release-nachtraeglich-bestandstags](slice-release-nachtraeglich-bestandstags.md) aufruft.
- [ ] **Job `release` im Workflow:** neben `publish` und `publish-cloudsmith` führt `veroeffentlichung.yml` einen Job `release` mit `needs: [publish, publish-cloudsmith]`, derselben Bedingung (nur Tag-Push, `github.ref_type == 'tag'`), `permissions: contents: write` **nur dort** (kein `packages: write`, kein Cloudsmith-Secret), kein `continue-on-error`, derselbe Schritt „Tag-Commit liegt in origin/main“, Checkout mit `persist-credentials: false`, Bau und Prüfsummen in Schritten **ohne** Token im `env:`, das Token nur im Schritt mit `gh release create`; keine neue Action ohne Commit-SHA (nach Möglichkeit keine neue Action). Der Job `vorschau` (Dispatch) fährt zusätzlich `make release-dateien` ohne Token und ohne `gh` ([ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) Entscheidung 6); der Dispatch legt nie ein Release an. Der Kopfkommentar der Datei nennt Zusage und Grenze. `make workflow-lint` ist grün; der Review hat die Punkte geprüft, die der Linter nicht prüft (Pins, Reihenfolge `gates` vor beiden Paket-Jobs und beide vor `release`, Bedingung, Rechte je Job, Token-Fenster), und sein Report nennt sie einzeln.
- [ ] **Selbsttest in `make skript-selbsttest`** (Klasse `werkzeug`, [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md)): in Wegwerf-Repos mit Stub für `gh` (und `docker`, falls das Skript es ruft) bricht das Skript **vor** `gh`, wenn der Tag nicht `v<Version>` ist, eine Datei fehlt, `SHA256SUMS` nicht zu den Dateien passt, die Version auf `-SNAPSHOT` steht oder ein Release zum Tag schon besteht (dann **kein** zweiter `create`-Aufruf, kein `--clobber`, kein `delete` im Stub-Protokoll); der Gutfall ruft `gh release create` **einmal** mit allen drei Dateien und `--verify-tag`, die Notiz enthält die Prüfsummen und keine unersetzte Platzhalterstelle, der Wert des Tokens steht in keiner Ausgabe und in keinem Argument; der Modus ohne Dateien ruft `create` ohne Anhang. Der Selbsttest liest den Workflow als Text und belegt: `contents: write` steht nur im Job `release`, `needs` des Jobs nennt beide Paket-Jobs, das Token steht nur im Schritt `gh release create`, `release-dateien` und das Eingangsskript stehen in keiner Gate-Liste und nicht in `tools/mk/e2e.mk`. Die Grenze (Pins, Bedingung, Reihenfolge nur im Review; der Stub ist nicht GitHub) steht im Kopf des Skripts, mit einer Mutation je Zweig, damit der Test selbst nicht grün bleibt, wenn eine Prüfung ausfällt.

Nachweis-Pflichten für alle drei Punkte:

- [ ] `make gates` grün; für die Doku-Teile `make docs-check`, `make flaeche`, `make kopplung-zahlen` ([`harness/README.md`](../../../../harness/README.md) §Minimal agent workflow, Zeile „Doku oder Spec“). Zusätzlich `make workflow-lint` (von [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) Fitness Function genannt, kein Gate; die Tabelle der Pflicht-Sensoren führt keine Zeile für Werkzeug, Workflow und Build-Datei, siehe §6). `make clean-build` und `make e2e` sind nicht erforderlich.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow ([`AGENTS.md`](../../../../AGENTS.md) §6), kein Self-Review (Modul 8). Der Review enthält die Prüfpunkte des Workflows einzeln (zweiter Punkt der DoD) und prüft, dass keine Aussage in Notiz, Kommentar oder Anleitung über das Gemessene hinausgeht ([`AGENTS.md`](../../../../AGENTS.md) §3.8). Enthält die Nachbesserung Code oder Skripte, folgt ein zweiter Review in frischem Kontext (§3, Ablauf).
- [ ] Doku-Update, soweit die Beschreibung des Werkzeugs oder des Ablaufs sich ändert: [`releasing.md`](../../../maintainer/releasing.md) (Schritt 8 und 9, §Veröffentlichen in der CI, §Tag-Schutz mit dem Recht `contents: write` und dem Restrisiko aus [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md), §Offene Entscheidungen, die Liste „Ungemessen“), [`harness/README.md`](../../../../harness/README.md) §Sensors (Zeilen `veroeffentlichung.yml`, `make skript-selbsttest`, gegebenenfalls das Eingangsskript), die Kopfkommentare von `veroeffentlichung.yml` und des Skripts. Anleitung, keine Chronik ([`AGENTS.md`](../../../../AGENTS.md) §5 Regel 5); die Aussagen stehen im Zustand „gebaut, nicht gelaufen“.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben; **kein Zaehler wird gesetzt**. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

**Prüfung der Liefer-Punkte gegen Bauart und Werkzeug** (`/plan-welle` Schritt 6b): Frage 1 — der Selbsttest sieht den Ablauf des Skripts am Stub und den Workflow als Text; GitHub (Recht, Anlegen, Anhänge, Verhalten bei Abbruch) sieht er nicht, dafür steht [slice-release-im-tag-lauf-messen](slice-release-im-tag-lauf-messen.md). Das Eingangsskript läuft auf dem Runner mit `bash`, `git` und `gh`; dass `gh` auf dem Runner-Image vorhanden ist, ist nach Dokumentationsstand angenommen, **nicht gemessen** ([ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) Entscheidung 1), und ein Fehlen zeigt erst der Tag-Lauf. Ob das Skript ein Make-Target bekommt, entscheidet der Implementer (jedes Target hat seine Zeile in [`harness/README.md`](../../../../harness/README.md)). Frage 2 — kein Punkt nennt eine Entscheidung des Nutzers als Lieferinhalt.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `.github/workflows/veroeffentlichung.yml` | update | Job `release`, Schritt in `vorschau`, Kopfkommentar um das dritte Ziel und die Grenze des Linters |
| Eingangsskript (Name und Ort wählt der Implementer) | neu | Prüfungen vor `gh`, Wiederholbarkeit, Notiz, ein Aufruf `gh release create`, Modus ohne Dateien |
| Notiz-Vorlage (Ort wählt der Implementer; liegt nicht dort, wo das Doku-Gate Platzhalter als Links liest) | neu | feste Notiz nach [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) Entscheidung 4 mit Variante ohne Dateizeilen |
| `tools/publish-selbsttest.sh` oder neuer Selbsttest, `tools/mk/skript-selbsttest.mk`, `Dockerfile` (Stage `skript-selbsttest`) | update | dritter Punkt; neue Dateien in die `COPY`-Zeilen der Stage |
| `tools/stempel/*.eingaenge` | update, falls nötig | neue `COPY`-Quelle gehört in die Listen; `make stempel-eingaenge-pruefung` bricht sonst |
| `docs/maintainer/releasing.md`, `harness/README.md` (§Sensors) | update | Beschreibungen nachziehen (Doku-Update der DoD) |

**Ablauf:** (1) Implementer baut die drei Punkte; der Befund an Stub und Linter ist Bericht, keine Schätzung. (2) Review in frischem Kontext mit den Workflow-Prüfpunkten (zweiter Punkt der DoD). (3) Enthält die Behebung Code, Skripte oder Workflow, folgt ein Re-Review in frischem Kontext; sonst der Vermerk „Nachbesserung ohne zweiten Review“ in §7. (4) Verifier prüft gegen die DoD, fährt `make gates` und `make workflow-lint` auf dem End-Stand. (5) Nichts wird veröffentlicht, kein Tag gesetzt, nichts gepusht, kein `gh` gegen GitHub.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): (1) [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) ist `Accepted` (erfüllt, 2026-11-16, in `main` gemergt). (2) [slice-release-dateien-bauen](slice-release-dateien-bauen.md) liegt in `done/` (das Target `make release-dateien` und seine Ausgabeform sind belegt). (3) Ein Verantwortlicher ist eingetragen. (4) Kein Handeln des Nutzers nötig; dass das Recht `contents: write` am Repository wirkt, braucht dieser Slice nicht (Mess-Pflicht 2 liegt bei [slice-release-im-tag-lauf-messen](slice-release-im-tag-lauf-messen.md)).

Reihenfolge: [slice-release-lizenzen-lib-belegen](../done/slice-release-lizenzen-lib-belegen.md) → [slice-release-dateien-bauen](slice-release-dateien-bauen.md) → **[slice-release-job-im-workflow-bauen](slice-release-job-im-workflow-bauen.md)** → [slice-release-im-tag-lauf-messen](slice-release-im-tag-lauf-messen.md) → [slice-release-nachtraeglich-bestandstags](slice-release-nachtraeglich-bestandstags.md); [slice-release-im-tag-lauf-messen](slice-release-im-tag-lauf-messen.md) beginnt nicht vor diesem Slice in `done/`.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß): das Eingangsskript verlangt einen Umbau über die Zusage hinaus (zum Beispiel ein eigenes Bauwerkzeug für die Notiz), oder der Selbsttest des Workflows als Text wächst über die Prüfungen des zweiten Jobs hinaus; der dritte Punkt wird ein eigener Slice, bevor [slice-release-im-tag-lauf-messen](slice-release-im-tag-lauf-messen.md) beginnt.
- `in-progress` → `open` (blockiert): die Ausgabeform von [slice-release-dateien-bauen](slice-release-dateien-bauen.md) trägt das Skript nicht (zum Beispiel fehlt etwas in `SHA256SUMS`) und [slice-release-dateien-bauen](slice-release-dateien-bauen.md) ist nachzuziehen; der Nutzer zieht das Release zurück (Re-Evaluierungs-Trigger von [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md)).

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Beobachtbar: (1) `make skript-selbsttest` (in `make gates`) und `make workflow-lint` sind grün; die Mutationen je Zweig sind rot, einzeln im Bericht und im Review-Report genannt; das Protokoll des `gh`-Stubs zeigt im Gutfall genau einen `create`-Aufruf. (2) Der Review-Report mit den einzeln genannten Workflow-Prüfpunkten liegt vor. (3) Alle Punkte der DoD erledigt, `make gates` grün, Closure-Notiz mit Lerneintrag geschrieben. Die Closure-Notiz nennt außerdem, welche Sätze in [`harness/README.md`](../../../../harness/README.md) und [`releasing.md`](../../../maintainer/releasing.md) im Zustand „gebaut, nicht gelaufen“ stehen, welche Annahmen ungemessen sind (`gh` auf dem Runner, Recht, Verhalten bei Teilfehler und Re-run), und den Ort der Notiz-Vorlage, damit [slice-release-im-tag-lauf-messen](slice-release-im-tag-lauf-messen.md) und [slice-release-nachtraeglich-bestandstags](slice-release-nachtraeglich-bestandstags.md) sie lesen.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **Der Stub belegt den Ablauf, nicht GitHub:** Recht `contents: write`, die Einstellung „Workflow permissions“, das Anlegen eines Releases mit Anhängen, Verhalten bei Abbruch, `gh` auf dem Runner und Re-run-Verhalten sind nur am echten Lauf zu messen (Register: `nachweis-von-der-falschen-seite-der-grenze`, 2×; die Gegenseite ist [slice-release-im-tag-lauf-messen](slice-release-im-tag-lauf-messen.md)). — **Ausgang:** offen bis zur Closure; erwartet „weiter offen, Träger [slice-release-im-tag-lauf-messen](slice-release-im-tag-lauf-messen.md)“.
- **Workflow-Aussagen ohne Sensor:** Pins, Reihenfolge, Bedingung und Rechte je Job prüft nur der Review; der Selbsttest liest einen Teil als Text (DoD dritter Punkt), der Linter nichts davon. — **Ausgang:** offen bis zur Closure.
- **Restrisiko des neuen Rechts:** wer den Workflow auf einem Tag ändern und den Tag nach `main` pushen kann, kann Releases anlegen oder ändern ([ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) Entscheidung 1); die tragende Kontrolle ist der Tag-Schutz `v*`, Handlung des Nutzers ohne Sensor, Gegenprobe ohne Bypass offen ([`releasing.md`](../../../maintainer/releasing.md)). — **Ausgang:** offen bis zur Closure; erwartet „weiter offen, beim Nutzer“.
- **Die Tabelle der Pflicht-Sensoren hat keine Zeile für Werkzeug, Workflow und Build-Datei:** der Plan nimmt `make gates`, die Zeile „Doku oder Spec“ für die Doku-Teile und `make workflow-lint` nach [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) Fitness Function; ob die Tabelle eine eigene Zeile bekommt, ist Sache des Nutzers. — **Ausgang:** offen bis zur Closure.
- Ein Release-Skript hat eine Hostabhängigkeit über `bash`, `git`, `docker` hinaus (`gh`, auf dem Runner angenommen); lokal läuft nur der Selbsttest am Stub. — **Ausgang:** offen bis zur Closure.
- Linkziele der Pakete in der Notiz: Quelle sind die Messprotokolle; ein nicht belegtes Ziel steht ohne Link. — **Ausgang:** offen bis zur Closure.

## 7. Closure-Notiz


Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register (vorhandene `BEO-<KUERZEL>/<slug>` **zitieren** statt neu
formulieren — sonst zählt das Register zwei Namen getrennt) ·
`grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln (das
Feld `liegt in` steht **nur**, wenn mit diesem Slice wirklich etwas verkörpert
wurde; Feld und Zielort auf **einer** Zeile, Sektionsangabe innerhalb der
Backticks). Ging der Gegenstand an einen anderen Slice oder entfiel er, trägt
diese Sektion die Zeile `Gegenstand:` mit Kennung oder Grund und jedes Risiko
aus §6 seinen Ausgang; die Liefer-Punkte der DoD bleiben leer
(`modul-05-planning-harness.md` §Ein Slice, dessen Gegenstand ein anderer
übernimmt).

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` gilt für das ganze Repo (`*`, Greenfield); der Slice berührt `*`, eine feinere Sub-Area ist nicht deklariert.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen; Treffer: `BEO-TDS/zusage-ohne-sensor-in-der-ci-kette` (Pins, Reihenfolge, Bedingung und Rechte je Job prüft nur der Review; der Slice nennt die Prüfpunkte und die Grenze des Selbsttests), `BEO-TDS/nachweis-von-der-falschen-seite-der-grenze` (2×; der `gh`-Stub belegt den Ablauf, nicht GitHub; die Gegenseite trägt [slice-release-im-tag-lauf-messen](slice-release-im-tag-lauf-messen.md)), `BEO-TDS/einmal-rot-ohne-gesicherte-ausgabe` (2×; der einzige echte Lauf ist [slice-release-im-tag-lauf-messen](slice-release-im-tag-lauf-messen.md), vorher läuft alles, was sich lokal belegen lässt), `BEO-TDS/aussagen-ueber-messung-hinaus` (verkörpert in [`AGENTS.md`](../../../../AGENTS.md) §3.8; die Notiz und die Kommentare tragen nur Gemessenes). Kein Zähler erreicht mit diesem Slice 3×, soweit er am Plan abzusehen ist.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
