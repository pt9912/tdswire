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

**Verantwortlich:** pt9912

**Autor:** Planner-Agent, Auftrag des Nutzers 2026-11-16 nach [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) (`Accepted` 2026-11-16, Folgepflicht). **Datum:** 2026-11-16.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** `.github/workflows/veroeffentlichung.yml` führt nach den beiden Paketen einen Job `release`, der aus den Dateien von `make release-dateien` ([slice-release-dateien-bauen](../done/slice-release-dateien-bauen.md)) ein GitHub Release mit fester Notiz anlegt; ein Eingangsskript prüft vorher Tag, Dateien, Prüfsummen und dass zum Tag noch kein Release besteht, und bricht sonst ohne Überschreiben. Belegt ist das ohne Wirkung nach außen: gegen einen `gh`-Stub in Wegwerf-Repos und mit `make workflow-lint`, nie gegen GitHub, wie [ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) es für den ersten Workflow getan hat.

**Warum ein eigener Slice, getrennt vom Bau und von der ersten Lieferung:** Der Bau ([slice-release-dateien-bauen](../done/slice-release-dateien-bauen.md)) hat keine Außenwirkung und kein Recht; dieser Slice führt das **neue Recht `contents: write`** ein und ist der Ort, an dem der Review die Punkte prüft, die der Linter nicht prüft (Pins, Reihenfolge `needs`, Bedingung, Rechte und Token-Fenster je Job). Die erste Lieferung ([slice-release-im-tag-lauf-messen](../open/slice-release-im-tag-lauf-messen.md)) legt ein Release auf der öffentlichen Seite an und verbraucht eine Version auf zwei Registries; was dabei rot werden kann, soll vorher rot geworden sein.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Tag, Release-Commit, Push, Dispatch-Lauf, Tag-Lauf, jedes echte `gh release create`, Messprotokoll, Mess-Pflichten 1 bis 4, 6 und 7 — [slice-release-im-tag-lauf-messen](../open/slice-release-im-tag-lauf-messen.md). Grund: sie setzen die Außenwirkung voraus, die dieser Slice nie ausführt; ein Agent hält kein Schreib-Token und pusht keinen Tag.
- Die nachträglichen Releases `v0.1.0` und `v0.1.1` — [slice-release-nachtraeglich-bestandstags](../open/slice-release-nachtraeglich-bestandstags.md). Grund: [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) Entscheidung 5 verlegt sie nach die erste Lieferung des neuen Formats und unter ausdrückliche Bestätigung des Nutzers. Dieser Slice liefert nur den **Modus ohne Dateien** des Eingangsskripts, den [slice-release-nachtraeglich-bestandstags](../open/slice-release-nachtraeglich-bestandstags.md) aufruft (die Notiz-Vorlage trägt die Variante, [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) Entscheidung 5), und belegt ihn am Stub.
- Der Bau der Dateien, die Prüfungen am Archiv, die Lizenzdateien — [slice-release-dateien-bauen](../done/slice-release-dateien-bauen.md) und [slice-release-lizenzen-lib-belegen](../done/slice-release-lizenzen-lib-belegen.md). Grund: dieser Slice ruft `make release-dateien` nur auf.
- Ein neues Gate: `make release-dateien` und das Eingangsskript stehen in keiner Gate-Liste, der Workflow ist kein Eingang von `make gates` ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md), [ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Entscheidung 3); der Selbsttest wird erweitert, keine Schwelle gesenkt ([`AGENTS.md`](../../../../AGENTS.md) §3.6).
- Ein persönliches Token, OIDC, eine zusätzliche Action ohne Commit-SHA, `--generate-notes`, `--clobber`, Löschen eines Releases: [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) nennt sie nicht gewählt. Der Annahme, dass `GITHUB_TOKEN` mit `contents: write` ein Release anlegen darf, steht [slice-release-im-tag-lauf-messen](../open/slice-release-im-tag-lauf-messen.md) gegenüber; kippt sie, ist es ein Re-Evaluierungs-Trigger von [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md).
- Tag-Schutz `v*` und die Einstellung „Workflow permissions“ des Repositories: Handlung des Nutzers ohne Sensor ([`releasing.md`](../../../maintainer/releasing.md) §Tag-Schutz); der Slice beschreibt das neue Recht dort, setzt aber nichts.
- `docs/user/`: Aussagen wie „die Anhänge sind anonym ladbar“ sind ungemessen (Mess-Pflicht 1), also bleibt die Nutzer-Doku unverändert, bis [slice-release-im-tag-lauf-messen](../open/slice-release-im-tag-lauf-messen.md) den Beleg hat (Setzung dieses Plans, wie bei [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) Entscheidung 5).

**Zielbild-Prüfung** (Maßstab: das Zielbild führt, Code folgt; „Zielbild anpassen“ nur mit Entscheidung des Nutzers):

| Stelle | Code angleichen | Zielbild anpassen | Entscheid |
|---|---|---|---|
| [`releasing.md`](../../../maintainer/releasing.md) Schritt 8 und 9, §Veröffentlichen in der CI (Jobs, Rechte, Teilfehler), §Tag-Schutz (neues Recht `contents: write`), §Offene Entscheidungen (der Satz „Weg für Jar und `lib/` ohne Registry zurückgestellt“ ist durch [slice-release-dateien-bauen](../done/slice-release-dateien-bauen.md) schon ersetzt: dort steht nur noch die Software-Stückliste als zurückgestellt, und „Nicht Teil dieses Ablaufs“ nennt das Anlegen des GitHub Release als „nicht gebaut“; hier nur noch auf „gebaut, nicht gelaufen“ heben) | Sätze nachziehen, im Zustand „gebaut, nicht gelaufen“; die Teilfehler-Anleitung für `release` steht als Absicht, nicht als Messung | — | Nachzug, nichts zu entscheiden |
| [`harness/README.md`](../../../../harness/README.md) §Sensors: Zeile `.github/workflows/veroeffentlichung.yml` (Job `release`, nie gelaufen), Zeile `make skript-selbsttest`, neue Zeile für das Eingangsskript, falls es ein Make-Target bekommt | Zeilen nachziehen, Bindung [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) | — | Nachzug, nichts zu entscheiden |
| [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) (Veröffentlichen braucht Netz und Schlüssel, ist nicht wiederholbar) | Veröffentlichen bleibt Werkzeug außerhalb der Gates, Bau in Docker | — | unverändert |

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [x] **Eingangsskript und feste Notiz:** Ein Skript (Name und Ort wählt der Implementer, Muster `tools/publish.sh`) prüft vor jedem Aufruf von `gh`: der Tag ist `v<Version aus build.gradle.kts>`, die drei Dateien liegen im angegebenen Verzeichnis, `sha256sum -c SHA256SUMS` ist grün, und zum Tag besteht noch kein Release (Abfrage vor dem Anlegen; besteht eines, Abbruch mit klarer Meldung ohne Überschreiben, kein `--clobber`, kein Löschen). Dann füllt es die Notiz aus einer Vorlagedatei im Repo (Version, Tag, Commit, Koordinaten `io.github.pt9912:tdswire`, Links auf beide Pakete aus den Messprotokollen, nicht aus dem Gedächtnis, Prüfsummen von ZIP und Jar, der Satz „die Prüfsumme belegt Unverändertheit, nicht Herkunft“, Lizenz MIT mit Verweis auf `LICENSE`, ein Zeiger auf die Einbettungs-Anleitung; **keine** Aussage über „anonym ladbar“, Gleichheit mit dem Paket-Jar oder die Lizenzen von `lib/`; wo ein Linkziel nicht belegt ist, steht der Koordinatentext ohne Link) und ruft `gh release create <Tag> <Dateien…> --verify-tag` **in einem Aufruf**, das Token nur im `env:` dieses einen Aufrufs. Das Skript hat einen **Modus ohne Dateien** (Notiz ohne Dateizeilen mit dem Satz, dass für diese Version keine Dateien anliegen und die Version in GitHub Packages, für `0.1.1` auch in Cloudsmith, steht; kein Anhang), den [slice-release-nachtraeglich-bestandstags](../open/slice-release-nachtraeglich-bestandstags.md) aufruft.
- [x] **Job `release` im Workflow:** neben `publish` und `publish-cloudsmith` führt `veroeffentlichung.yml` einen Job `release` mit `needs: [publish, publish-cloudsmith]`, derselben Bedingung (nur Tag-Push, `github.ref_type == 'tag'`), `permissions: contents: write` **nur dort** (kein `packages: write`, kein Cloudsmith-Secret), kein `continue-on-error`, derselbe Schritt „Tag-Commit liegt in origin/main“, Checkout mit `persist-credentials: false`, Bau und Prüfsummen in Schritten **ohne** Token im `env:`, das Token nur im Schritt mit `gh release create`; keine neue Action ohne Commit-SHA (nach Möglichkeit keine neue Action). Der Job `vorschau` (Dispatch) fährt zusätzlich `make release-dateien VORSCHAU=1` (Vorschau-Modus, nur dort; der Job `release` ruft `make release-dateien` ohne den Modus) ohne Token und ohne `gh` ([ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) Entscheidung 6); der Dispatch legt nie ein Release an. Der Kopfkommentar der Datei nennt Zusage und Grenze. `make workflow-lint` ist grün; der Review hat die Punkte geprüft, die der Linter nicht prüft (Pins, Reihenfolge `gates` vor beiden Paket-Jobs und beide vor `release`, Bedingung, Rechte je Job, Token-Fenster), und sein Report nennt sie einzeln.
- [x] **Selbsttest in `make skript-selbsttest`** (Klasse `werkzeug`, [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md)): in Wegwerf-Repos mit Stub für `gh` (und `docker`, falls das Skript es ruft) bricht das Skript **vor** `gh`, wenn der Tag nicht `v<Version>` ist, eine Datei fehlt, `SHA256SUMS` nicht zu den Dateien passt, die Version auf `-SNAPSHOT` steht oder ein Release zum Tag schon besteht (dann **kein** zweiter `create`-Aufruf, kein `--clobber`, kein `delete` im Stub-Protokoll); der Gutfall ruft `gh release create` **einmal** mit allen drei Dateien und `--verify-tag`, die Notiz enthält die Prüfsummen und keine unersetzte Platzhalterstelle, ein Vorschau-ZIP (`*-vorschau.zip`) im Ausgabeverzeichnis lässt den Job `release` brechen, ohne dass er etwas anhängt (der Job `release` ruft `make release-dateien` **ohne** `VORSCHAU=1`), der Wert des Tokens steht in keiner Ausgabe und in keinem Argument; der Modus ohne Dateien ruft `create` ohne Anhang. Der Selbsttest liest den Workflow als Text und belegt: `contents: write` steht nur im Job `release`, `needs` des Jobs nennt beide Paket-Jobs, das Token steht nur im Schritt `gh release create`, `release-dateien` und das Eingangsskript stehen in keiner Gate-Liste und nicht in `tools/mk/e2e.mk`. Die Grenze (Pins, Bedingung, Reihenfolge nur im Review; der Stub ist nicht GitHub) steht im Kopf des Skripts, mit einer Mutation je Zweig, damit der Test selbst nicht grün bleibt, wenn eine Prüfung ausfällt.

Nachweis-Pflichten für alle drei Punkte:

- [x] `make gates` grün; für die Doku-Teile `make docs-check`, `make flaeche`, `make kopplung-zahlen` ([`harness/README.md`](../../../../harness/README.md) §Minimal agent workflow, Zeile „Doku oder Spec“). Zusätzlich `make workflow-lint` (von [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) Fitness Function genannt, kein Gate; die Tabelle der Pflicht-Sensoren führt keine Zeile für Werkzeug, Workflow und Build-Datei, siehe §6). `make clean-build` und `make e2e` sind nicht erforderlich.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow ([`AGENTS.md`](../../../../AGENTS.md) §6), kein Self-Review (Modul 8). Der Review enthält die Prüfpunkte des Workflows einzeln (zweiter Punkt der DoD) und prüft, dass keine Aussage in Notiz, Kommentar oder Anleitung über das Gemessene hinausgeht ([`AGENTS.md`](../../../../AGENTS.md) §3.8). Enthält die Nachbesserung Code oder Skripte, folgt ein zweiter Review in frischem Kontext (§3, Ablauf).
- [x] Doku-Update, soweit die Beschreibung des Werkzeugs oder des Ablaufs sich ändert: [`releasing.md`](../../../maintainer/releasing.md) (Schritt 8 und 9, §Veröffentlichen in der CI, §Tag-Schutz mit dem Recht `contents: write` und dem Restrisiko aus [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md), §Offene Entscheidungen, die Liste „Ungemessen“), [`harness/README.md`](../../../../harness/README.md) §Sensors (Zeilen `veroeffentlichung.yml`, `make skript-selbsttest`, gegebenenfalls das Eingangsskript), die Kopfkommentare von `veroeffentlichung.yml` und des Skripts. Anleitung, keine Chronik ([`AGENTS.md`](../../../../AGENTS.md) §5 Regel 5); die Aussagen stehen im Zustand „gebaut, nicht gelaufen“.
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben; **kein Zaehler wird gesetzt**. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

**Prüfung der Liefer-Punkte gegen Bauart und Werkzeug** (`/plan-welle` Schritt 6b): Frage 1 — der Selbsttest sieht den Ablauf des Skripts am Stub und den Workflow als Text; GitHub (Recht, Anlegen, Anhänge, Verhalten bei Abbruch) sieht er nicht, dafür steht [slice-release-im-tag-lauf-messen](../open/slice-release-im-tag-lauf-messen.md). Das Eingangsskript läuft auf dem Runner mit `bash`, `git` und `gh`; dass `gh` auf dem Runner-Image vorhanden ist, ist nach Dokumentationsstand angenommen, **nicht gemessen** ([ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) Entscheidung 1), und ein Fehlen zeigt erst der Tag-Lauf. Ob das Skript ein Make-Target bekommt, entscheidet der Implementer (jedes Target hat seine Zeile in [`harness/README.md`](../../../../harness/README.md)). Frage 2 — kein Punkt nennt eine Entscheidung des Nutzers als Lieferinhalt.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `.github/workflows/veroeffentlichung.yml` | update | Job `release`, Schritt in `vorschau` (ruft `make release-dateien VORSCHAU=1`: der Modus erlaubt `-SNAPSHOT` auf `main` und nennt das ZIP `tdswire-<Version>-vorschau.zip`; der Job `release` ruft ihn nie), Kopfkommentar um das dritte Ziel und die Grenze des Linters |
| Eingangsskript (Name und Ort wählt der Implementer) | neu | Prüfungen vor `gh`, Wiederholbarkeit, Notiz, ein Aufruf `gh release create`, Modus ohne Dateien |
| Notiz-Vorlage (Ort wählt der Implementer; liegt nicht dort, wo das Doku-Gate Platzhalter als Links liest) | neu | feste Notiz nach [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) Entscheidung 4 mit Variante ohne Dateizeilen |
| `tools/publish-selbsttest.sh` oder neuer Selbsttest, `tools/mk/skript-selbsttest.mk`, `Dockerfile` (Stage `skript-selbsttest`) | update | dritter Punkt; neue Dateien in die `COPY`-Zeilen der Stage |
| `tools/stempel/*.eingaenge` | update, falls nötig | neue `COPY`-Quelle gehört in die Listen; `make stempel-eingaenge-pruefung` bricht sonst |
| `docs/maintainer/releasing.md`, `harness/README.md` (§Sensors) | update | Beschreibungen nachziehen (Doku-Update der DoD) |

**Ablauf:** (1) Implementer baut die drei Punkte; der Befund an Stub und Linter ist Bericht, keine Schätzung. (2) Review in frischem Kontext mit den Workflow-Prüfpunkten (zweiter Punkt der DoD). (3) Enthält die Behebung Code, Skripte oder Workflow, folgt ein Re-Review in frischem Kontext; sonst der Vermerk „Nachbesserung ohne zweiten Review“ in §7. (4) Verifier prüft gegen die DoD, fährt `make gates` und `make workflow-lint` auf dem End-Stand. (5) Nichts wird veröffentlicht, kein Tag gesetzt, nichts gepusht, kein `gh` gegen GitHub.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): (1) [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) ist `Accepted` (erfüllt, 2026-11-16, in `main` gemergt). (2) [slice-release-dateien-bauen](../done/slice-release-dateien-bauen.md) liegt in `done/` (das Target `make release-dateien` und seine Ausgabeform sind belegt). (3) Ein Verantwortlicher ist eingetragen. (4) Kein Handeln des Nutzers nötig; dass das Recht `contents: write` am Repository wirkt, braucht dieser Slice nicht (Mess-Pflicht 2 liegt bei [slice-release-im-tag-lauf-messen](../open/slice-release-im-tag-lauf-messen.md)).

Reihenfolge: [slice-release-lizenzen-lib-belegen](../done/slice-release-lizenzen-lib-belegen.md) → [slice-release-dateien-bauen](../done/slice-release-dateien-bauen.md) → **[slice-release-job-im-workflow-bauen](slice-release-job-im-workflow-bauen.md)** → [slice-release-im-tag-lauf-messen](../open/slice-release-im-tag-lauf-messen.md) → [slice-release-nachtraeglich-bestandstags](../open/slice-release-nachtraeglich-bestandstags.md); [slice-release-im-tag-lauf-messen](../open/slice-release-im-tag-lauf-messen.md) beginnt nicht vor diesem Slice in `done/`.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß): das Eingangsskript verlangt einen Umbau über die Zusage hinaus (zum Beispiel ein eigenes Bauwerkzeug für die Notiz), oder der Selbsttest des Workflows als Text wächst über die Prüfungen des zweiten Jobs hinaus; der dritte Punkt wird ein eigener Slice, bevor [slice-release-im-tag-lauf-messen](../open/slice-release-im-tag-lauf-messen.md) beginnt.
- `in-progress` → `open` (blockiert): die Ausgabeform von [slice-release-dateien-bauen](../done/slice-release-dateien-bauen.md) trägt das Skript nicht (zum Beispiel fehlt etwas in `SHA256SUMS`) und [slice-release-dateien-bauen](../done/slice-release-dateien-bauen.md) ist nachzuziehen; der Nutzer zieht das Release zurück (Re-Evaluierungs-Trigger von [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md)).

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Beobachtbar: (1) `make skript-selbsttest` (in `make gates`) und `make workflow-lint` sind grün; die Mutationen je Zweig sind rot, einzeln im Bericht und im Review-Report genannt; das Protokoll des `gh`-Stubs zeigt im Gutfall genau einen `create`-Aufruf. (2) Der Review-Report mit den einzeln genannten Workflow-Prüfpunkten liegt vor. (3) Alle Punkte der DoD erledigt, `make gates` grün, Closure-Notiz mit Lerneintrag geschrieben. Die Closure-Notiz nennt außerdem, welche Sätze in [`harness/README.md`](../../../../harness/README.md) und [`releasing.md`](../../../maintainer/releasing.md) im Zustand „gebaut, nicht gelaufen“ stehen, welche Annahmen ungemessen sind (`gh` auf dem Runner, Recht, Verhalten bei Teilfehler und Re-run), und den Ort der Notiz-Vorlage, damit [slice-release-im-tag-lauf-messen](../open/slice-release-im-tag-lauf-messen.md) und [slice-release-nachtraeglich-bestandstags](../open/slice-release-nachtraeglich-bestandstags.md) sie lesen.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **Der Stub belegt den Ablauf, nicht GitHub:** Recht `contents: write`, die Einstellung „Workflow permissions“, das Anlegen eines Releases mit Anhängen, Verhalten bei Abbruch, `gh` auf dem Runner und Re-run-Verhalten sind nur am echten Lauf zu messen (Register: `nachweis-von-der-falschen-seite-der-grenze`, 2×; die Gegenseite ist [slice-release-im-tag-lauf-messen](../open/slice-release-im-tag-lauf-messen.md)). — **Ausgang:** weiter offen, Träger [slice-release-im-tag-lauf-messen](../open/slice-release-im-tag-lauf-messen.md) (gh auf dem Runner, Recht `contents: write`, Anlegen mit drei Anhängen, Abbruch, Re-run, anonyme Ladbarkeit; Verifikationsbericht §Ausdrücklich ungemessen).
- **Workflow-Aussagen ohne Sensor:** Pins, Reihenfolge, Bedingung und Rechte je Job prüft nur der Review; der Selbsttest liest einen Teil als Text (DoD dritter Punkt), der Linter nichts davon. — **Ausgang:** teilweise entfallen, Rest weiter offen: Rechte, `needs`, Token-Ort, Bedingung und die Form der Pins liest der Selbsttest als Text (21 Gegenproben an Kopien, im dritten Re-Review nachgemessen); offen bleiben die Pins gegen die echte Action, Bedingung, Reihenfolge und Rechte zur Laufzeit (Träger [slice-release-im-tag-lauf-messen](../open/slice-release-im-tag-lauf-messen.md)) und die Schleife über die echten Dateien der Abwesenheitsprüfung (T-1).
- **Restrisiko des neuen Rechts:** wer den Workflow auf einem Tag ändern und den Tag nach `main` pushen kann, kann Releases anlegen oder ändern ([ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) Entscheidung 1); die tragende Kontrolle ist der Tag-Schutz `v*`, Handlung des Nutzers ohne Sensor, Gegenprobe ohne Bypass offen ([`releasing.md`](../../../maintainer/releasing.md)). — **Ausgang:** weiter offen, Träger Nutzer (Tag-Schutz `v*`; Start-Bedingung 4 von [slice-release-im-tag-lauf-messen](../open/slice-release-im-tag-lauf-messen.md)).
- **Die Tabelle der Pflicht-Sensoren hat keine Zeile für Werkzeug, Workflow und Build-Datei:** der Plan nimmt `make gates`, die Zeile „Doku oder Spec“ für die Doku-Teile und `make workflow-lint` nach [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) Fitness Function; ob die Tabelle eine eigene Zeile bekommt, ist Sache des Nutzers. — **Ausgang:** weiter offen, Träger Nutzer; der Verifier fuhr dieselbe Menge wie der Plan, die Tabelle ist unverändert.
- Ein Release-Skript hat eine Hostabhängigkeit über `bash`, `git`, `docker` hinaus (`gh`, auf dem Runner angenommen); lokal läuft nur der Selbsttest am Stub. — **Ausgang:** weiter offen: `gh` auf dem Runner ist angenommen (Träger [slice-release-im-tag-lauf-messen](../open/slice-release-im-tag-lauf-messen.md)); ob die Ausführung des Skripts auf dem Runner-Host gegen [`AGENTS.md`](../../../../AGENTS.md) §3.1 zulässig ist, entscheidet der Nutzer (§7, Frage 1).
- Linkziele der Pakete in der Notiz: Quelle sind die Messprotokolle; ein nicht belegtes Ziel steht ohne Link. — **Ausgang:** entfallen: die zwei Registry-Adressen stehen in den Messprotokollen ([0.1.0](../../../messungen/veroeffentlichung-tag-lauf-2026-11-15.md), [0.1.1](../../../messungen/veroeffentlichung-0-1-1-2026-11-15.md)); „anonym ladbar“ steht nicht in der Notiz.

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

Geschlossen 2026-11-16 (Planner, Slice-Closure ohne Welle). Geprüfter Stand: Zweig `slice-release-job-im-workflow-bauen`, HEAD `ce59ee31` (Basis `main` `c2c3209e`). Prüfung: Verifikationsbericht [2026-11-16-925487ed](../../../reviews/2026-11-16-925487ed-slice-release-job-im-workflow-bauen-verifikation.md) (kein HIGH, kein MEDIUM, zwei LOW, zwei INFO), Review [2026-11-16-461aa00b](../../../reviews/2026-11-16-461aa00b-slice-release-job-im-workflow-bauen-review.md) (ein MEDIUM, drei LOW, vier INFO) und drei Re-Reviews in frischem Kontext: [a1b762f7](../../../reviews/2026-11-16-a1b762f7-slice-release-job-im-workflow-bauen-re-review.md) (ein MEDIUM, zwei LOW, drei INFO), [b8a2085c](../../../reviews/2026-11-16-b8a2085c-slice-release-job-im-workflow-bauen-re-review-2.md) (drei LOW, ein INFO), [d182fe5f](../../../reviews/2026-11-16-d182fe5f-slice-release-job-im-workflow-bauen-re-review-3.md) (kein HIGH, MEDIUM, LOW; ein INFO T-1). Jede Nachbesserung mit Skripten oder Workflow ist von einem Re-Review gelesen; der Vermerk „Nachbesserung ohne zweiten Review“ entfällt. Nach dem dritten Re-Review ändert kein Commit mehr Skripte oder Workflow.

**Geliefert, im Zustand „gebaut, nicht gelaufen“** (Eingang für [slice-release-im-tag-lauf-messen](../open/slice-release-im-tag-lauf-messen.md), das die Zeilen auf „gelaufen“ hebt, soweit sein Messprotokoll sie belegt):

- Job `release` in `.github/workflows/veroeffentlichung.yml` (`needs: [publish, publish-cloudsmith]`, Bedingung wortgleich mit `publish`, `contents: write` nur dort, `GH_TOKEN` nur im Schritt `gh release create`, Bau und Prüfsummen ohne Token, ohne `VORSCHAU=1`); der Job `vorschau` fährt zusätzlich `make release-dateien VORSCHAU=1` ohne Token und ohne `gh`.
- Eingangsskript `tools/release-anlegen.sh` (Prüfungen vor `gh`, Abfrage `gh release list` vor dem Anlegen, ein Aufruf `gh release create … --verify-tag`, Modus `--ohne-dateien` nur für `v0.1.0` und `v0.1.1`) und **Notiz-Vorlage `tools/release-notiz.vorlage`** (mit Variante ohne Dateizeilen; Ort für [slice-release-im-tag-lauf-messen](../open/slice-release-im-tag-lauf-messen.md) und [slice-release-nachtraeglich-bestandstags](../open/slice-release-nachtraeglich-bestandstags.md)); Selbsttest `tools/release-anlegen-selbsttest.sh` (Teil der Stage `skript-selbsttest`, Klasse `werkzeug`).
- Sätze im Zustand „gebaut, nicht gelaufen“: [`harness/README.md`](../../../../harness/README.md) §Sensors (Zeilen `.github/workflows/veroeffentlichung.yml` und `make skript-selbsttest`); [`releasing.md`](../../../maintainer/releasing.md) Schritt 8 und 9, §Veröffentlichen in der CI, §Tag-Schutz (Recht `contents: write`, Restrisiko), §Offene Entscheidungen, Liste „Ungemessen“. `docs/user/` bleibt wie geplant unverändert.
- Am Lauf (Verifier, End-Stand `925487ed`, nichts nach außen): `docker build --no-cache --target skript-selbsttest` Exit 0 (34 s, `release-anlegen-selbsttest: gruen`); `make workflow-lint`, `make docs-check`, `make flaeche`, `make kopplung-zahlen` je Exit 0; `make gates` Exit 0 (Gruppen per Stempel übersprungen) und `FORCE=1 make gates` Exit 0 (36 s); `make clean-build` Exit 0 (654 s) und `make e2e` Exit 0 (9 Stufen, 453 s) auf Auftrag, ohne Aussagekraft für den Gegenstand (kein Eingriff in `src/` oder `testserver/`). Der Selbsttest führt 21 Gegenproben am Workflow-Text und eine Mutation je Marke `# P:<name>` des Skripts, dazu `--verify-tag` entfernt und `--clobber` ergänzt; das Stub-Protokoll zeigt im Gutfall genau einen `create`-Aufruf.

**Was anders lief als geplant.** Der erste Selbsttest ließ die Mutationen des Workflow-Teils gegen eine zweite Fassung der Prüfungen laufen (Review F-1, MEDIUM); der Re-Review fand drei Prüfungen ohne Gegenprobe und einen Kopf, der „jede“ sagte (R-1, MEDIUM); der zweite eine Probe, die nie rot werden konnte (S-1). Erst der dritte Re-Review fand keinen Befund oberhalb INFO. Der Plan führte `tools/mk/skript-selbsttest.mk` als „update“; die Datei blieb unverändert (V-1).

**Offen, Träger: Nutzer** (nicht entschieden, Stand: offen; Empfehlungen sind Vorschläge des Planners):

1. Hostausführung von `tools/release-anlegen.sh` im Job (`bash`, `git`, `gh` auf dem Runner, nicht in Docker wie `make publish`) gegen [`AGENTS.md`](../../../../AGENTS.md) §3.1. Der Review (I-1) liest §3.1 als Verbot lokaler `gradle`, `java`, Kotlin; der Verifier legt die Frage vor. Vorschlag: zulässig, Präzedenz `tools/publish.sh`; bei Verneinung braucht das Skript einen Container.
2. `--ohne-dateien` nur für `v0.1.0` und `v0.1.1` (Entscheidung 5 von [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md)): ein dritter Bestandstag verlangt eine Änderung am Skript und am Selbsttest; für weitere Tags eine Folge-ADR mit `Schärft:` auf Entscheidung 5 (die Setzung steht im Skript, in der ADR und im Folge-Slice, ein Sensor nur im Skript, I-2). Vorschlag: so lassen, bis ein Fall besteht.
3. `gh release list --limit 1000` gegen `gh release view`: die Abfrage trennt „besteht“ von „Abfrage schlug fehl“ (Fall `liste`), die Grenze 1000 ist im Kopf des Skripts als ungemessen vermerkt, ohne Wirkung für dieses Repo (I-3). Vorschlag: so lassen.

**Offene Findings** (offen, nicht erledigt):

- V-1 (LOW): `tools/mk/skript-selbsttest.mk` Zeilen 1 bis 7 (Kopf und `##`-Beschreibung, also `make help`) nennen weder `tools/release-anlegen.sh` noch den Workflow-Text (auch `release-selbsttest.sh` fehlt dort seit `slice-release-dateien-bauen`); der Plan führte die Datei als „update“.
- V-2 (LOW): Zelle `make skript-selbsttest` in [`harness/README.md`](../../../../harness/README.md) §Sensors ohne „Modi“ (`test-lauf.sh`) und ohne „je Ziel“ (`publish.sh`); dritte Kürzung derselben Zelle (Review F-2, Re-Review R-3, S-3), Grenze 200 Zeichen. Entscheidung beim Nutzer oder Planner; `BEO-TDS/kuerzung-einer-tabellenzelle-verliert-angaben`.
- T-1 / V-3 (INFO): die Schleife über die echten Dateien der Abwesenheitsprüfung in `tools/release-anlegen-selbsttest.sh` hat keine Gegenprobe (Entfernen von `pruefe` bleibt grün); Zeilenfortsetzung, `?=` und `::=` erfasst das Muster nicht, im Repo nicht vorhanden. Der Kopf des Selbsttests nennt es nicht vollständig.

**Ungemessen, Träger [slice-release-im-tag-lauf-messen](../open/slice-release-im-tag-lauf-messen.md)** (Verifikationsbericht §Ausdrücklich ungemessen): `gh` auf dem Runner-Image und ob es das Repository ohne `GH_REPO` findet; das Recht `contents: write` und die Einstellung „Workflow permissions“; das Anlegen mit drei Anhängen in einem Aufruf und das Verhalten bei Abbruch (Release ohne Anhänge?); „Re-run failed jobs“ und Re-run des Jobs `release` bei bestehendem Release; anonyme Ladbarkeit der Anhänge und Gleichheit von Release-Jar und Paket-Jar; Pins gegen die echten Actions, Bedingung, Reihenfolge und Rechte zur Laufzeit; `make release-dateien VORSCHAU=1` im Dispatch-Lauf; Laufzeit des Jobs `release`; ob `--limit 1000` alle Releases nennt.

**Folge-Slices und Start-Bedingungen.** [slice-release-im-tag-lauf-messen](../open/slice-release-im-tag-lauf-messen.md): Start-Bedingung 1 (dieser Slice in `done/`) ist mit dieser Schließung erfüllt, Bedingung 2 war es; offen sind 3 bis 6 (Version und Zeitpunkt, Push und Tag-Schutz, Beleg der Lizenzen gelesen, Verantwortlicher), alle beim Nutzer. Der Slice ist damit **nicht ohne den Nutzer startbar**; sein Text nennt diese Bedingungen schon, seine Datei bleibt unverändert. [slice-release-nachtraeglich-bestandstags](../open/slice-release-nachtraeglich-bestandstags.md): Start-Bedingung 1 verlangt [slice-release-im-tag-lauf-messen](../open/slice-release-im-tag-lauf-messen.md) in `done/`; sie bleibt unerfüllt, die Datei bleibt unverändert. Beide Dateien liegen in `open/`.

**Risiken (Ausgänge in §6).** Weiter offen: Stub gegen GitHub (Träger [slice-release-im-tag-lauf-messen](../open/slice-release-im-tag-lauf-messen.md)), Restrisiko des Rechts `contents: write` (Träger Nutzer, Tag-Schutz), Tabelle der Pflicht-Sensoren (Träger Nutzer), Hostabhängigkeit `gh` (angenommen; §3.1 beim Nutzer). Teilweise entfallen: Workflow-Aussagen ohne Sensor (Rest siehe §6). Entfallen: Linkziele der Pakete.

**Register** (`../observations/`). Fortgeschrieben, kein Zähler gesetzt (je `evidence/slice-release-job-im-workflow-bauen.md`): [`BEO-TDS/skript-pruefung-ohne-rot-gegenprobe`](../observations/BEO-TDS/skript-pruefung-ohne-rot-gegenprobe/observation.md) (Review F-1, Re-Review R-1, S-1, T-1; jetzt 2×, offen, unter der Schwelle), [`BEO-TDS/kuerzung-einer-tabellenzelle-verliert-angaben`](../observations/BEO-TDS/kuerzung-einer-tabellenzelle-verliert-angaben/observation.md) (F-2, R-3, S-3, V-2; jetzt 2×, offen), [`BEO-TDS/zusage-ohne-sensor-in-der-ci-kette`](../observations/BEO-TDS/zusage-ohne-sensor-in-der-ci-kette/observation.md) (Pins, Bedingung und Reihenfolge zur Laufzeit; 9×, Stand `offen`, Entscheidung beim Nutzer), [`BEO-TDS/aussagen-ueber-messung-hinaus`](../observations/BEO-TDS/aussagen-ueber-messung-hinaus/observation.md) (R-1, R-2, F-3, R-6; verkörpert, Wiederauftreten im `state.md` vermerkt, die Regel bleibt). Die in §8 genannten `BEO-TDS/nachweis-von-der-falschen-seite-der-grenze` und `BEO-TDS/einmal-rot-ohne-gesicherte-ausgabe` bekommen keine Datei: der Slice trägt keinen neuen Fund dieser Klassen, die Gegenseite ist als Folge-Slice benannt (Stand 2×). `BEO-TDS/nachbesserung-ohne-zweiten-review` ist nicht wiedergekehrt: die Regel wirkte, alle Nachbesserungen sind von Re-Reviews gelesen. **Lese-Schritt:** 44 Einträge führen drei oder mehr Dateien in `evidence/` (24 `verkörpert`, 1 `gestrichen`, 12 `offen`, 2 `geplant`, je 1 `teilweise`, `gebaut`, `geliefert`, 2 ohne Stand-Wort; gezählt vor und nach dieser Closure, unverändert). Diese Closure ändert keinen Stand; die über der Schwelle liegenden und nicht verkörperten Einträge tragen ihre Entscheidung beim Nutzer oder in den Welle-Closures. Mit diesem Slice erreicht kein Eintrag erstmals 3×.

**Lerneintrag (Steering Loop).**

- **Neuer Sensor, gebaut:** `tools/release-anlegen-selbsttest.sh` (Klasse `werkzeug`, in `make skript-selbsttest`): eine Mutation je Marke des Eingangsskripts, der Workflow als Text mit 21 Gegenproben und die Abwesenheit von `release-dateien` und `release-anlegen` in Gate-Listen und `tools/mk/e2e.mk`. Er deckt die Zusagen dieses Slice, nicht die Klasse: kein `liegt in`, `BEO-TDS/zusage-ohne-sensor-in-der-ci-kette` bleibt `offen`.
- **Geschärft, keine neue Regel:** (i) Eine Gegenprobe läuft gegen dieselbe Funktion, die den echten Bestand liest, nicht gegen eine zweite Fassung der Prüfung; eine Probe, die dasselbe Muster an einer selbst angehängten Zeile sucht, kann nie rot werden (Review F-1, Re-Review 2 S-1; `BEO-TDS/skript-pruefung-ohne-rot-gegenprobe`, 2×). (ii) Ein Kopf, der „jede“ oder „genau“ sagt, zählt nach (R-1, R-2; `BEO-TDS/aussagen-ueber-messung-hinaus`, verkörpert in [`AGENTS.md`](../../../../AGENTS.md) §3.8). (iii) Drei Re-Reviews waren nötig, weil jede Nachbesserung am Selbsttest eine neue ungebundene Stelle eröffnete; die Kette endete erst, als der Befund INFO war (T-1).
- **Benannte Spec-Lücke:** keine neue; die Tabelle der Pflicht-Sensoren in [`harness/README.md`](../../../../harness/README.md) §Minimal agent workflow hat weiter keine Zeile für Werkzeug, Workflow und Build-Datei (Träger Nutzer, §6).
- **Grenze:** Der Slice belegt Ablauf und Workflow-Text am Stub und am Linter, nie GitHub; der Selbsttest sieht nur die Mutationen, die er führt.
- **Drei Paarungen:** Anker: kein Eintrag trägt `liegt in`, also nichts zu paaren. Folge-Slice: [slice-release-im-tag-lauf-messen](../open/slice-release-im-tag-lauf-messen.md) und [slice-release-nachtraeglich-bestandstags](../open/slice-release-nachtraeglich-bestandstags.md) liegen als Datei in `open/`. Register: die vier genannten `BEO-TDS/<slug>` existieren als Verzeichnisse mit nicht leerem `evidence/`, je mit der Datei dieses Slice.

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

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen; Treffer: `BEO-TDS/zusage-ohne-sensor-in-der-ci-kette` (Pins, Reihenfolge, Bedingung und Rechte je Job prüft nur der Review; der Slice nennt die Prüfpunkte und die Grenze des Selbsttests), `BEO-TDS/nachweis-von-der-falschen-seite-der-grenze` (2×; der `gh`-Stub belegt den Ablauf, nicht GitHub; die Gegenseite trägt [slice-release-im-tag-lauf-messen](../open/slice-release-im-tag-lauf-messen.md)), `BEO-TDS/einmal-rot-ohne-gesicherte-ausgabe` (2×; der einzige echte Lauf ist [slice-release-im-tag-lauf-messen](../open/slice-release-im-tag-lauf-messen.md), vorher läuft alles, was sich lokal belegen lässt), `BEO-TDS/aussagen-ueber-messung-hinaus` (verkörpert in [`AGENTS.md`](../../../../AGENTS.md) §3.8; die Notiz und die Kommentare tragen nur Gemessenes). Kein Zähler erreicht mit diesem Slice 3×, soweit er am Plan abzusehen ist.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
