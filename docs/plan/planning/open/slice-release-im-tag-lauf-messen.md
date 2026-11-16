# Slice slice-release-im-tag-lauf-messen: Erster Tag-Lauf mit Release (Version wählt der Nutzer): Dateien auf der Release-Seite und die Mess-Pflichten 1 bis 4, 6 und 7 der Entscheidung zum Release gemessen

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** ohne Welle — keine Closure-Bedingung, die von der DoD dieses Slice verschieden ist. Gewählt wie bei den Veröffentlichungs-Slices zu [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) und [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) (auch sie ohne Welle); [welle-referenzabgleich-release](../welle-referenzabgleich-release.md) führt diese Slices in ihrer Liste nicht, und [welle-qualitaet-grundlage](../welle-qualitaet-grundlage.md) trägt Qualitätsmessungen, kein Veröffentlichen. Offene Wellen und ihre Slice-Listen am 2026-11-16 gelesen.

**Bezug:** [TDS-RB-05](../../../../spec/lastenheft.md#tds-rb-05--lizenz) (Scope), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) (Scope: Messprotokoll, Nachvollziehbarkeit des Laufs), [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) (`Accepted` 2026-11-16, Entscheidungen 1 bis 3, 6 und 7, Folgepflicht: Mess-Pflichten und Messprotokoll), [ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) (Entscheidung 1: der Tag-Push des Nutzers ist die Handlung), [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) (Teilfehler und Re-run der Paket-Jobs), [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) (Entscheidung 3: eine Version wird nie neu veröffentlicht), [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md) (nicht in den Gates).

**Berührte Spec-Stellen:** — (`spec/` nennt weder Veröffentlichung noch Release); `SPEC-052` und `SPEC-059` nur, wenn die Messung eine Aussage widerlegt.

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

**Ziel:** Auf dem ersten Tag-Lauf, der den Job `release` führt, entsteht ein GitHub Release mit `tdswire-<Version>.zip`, `tdswire.jar` und `SHA256SUMS`, und die Mess-Pflichten 1 bis 4, 6 und 7 der [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) sind gemessen und im Messprotokoll festgehalten; die Sätze in [`releasing.md`](../../../maintainer/releasing.md) und [`harness/README.md`](../../../../harness/README.md) stehen danach im Zustand „gelaufen“, soweit der Lauf sie belegt.

**Wahl der Version — Sache des Nutzers.** Dieser Slice **fällt mit der Veröffentlichung einer künftigen Version zusammen**: ein Tag-Lauf mit Release braucht einen neuen Tag `v<Version>`, und eine veröffentlichte Version wird nie neu veröffentlicht ([ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) Entscheidung 3, [`releasing.md`](../../../maintainer/releasing.md) Schritt 5); `v0.1.0` und `v0.1.1` bestehen. Ein eigener „Veröffentlichungs-Slice für Version X“ besteht in `open/` nicht (am 2026-11-16 gelesen), und dieser Plan erfindet keinen: dieser Slice **ist** der Träger des Release-Commits, des Tags und des Laufs, mit dem Muster von `slice-cloudsmith-veroeffentlichung-0-1-1`. Welche Version (Schema `0.y.z` nach [`releasing.md`](../../../maintainer/releasing.md) Schritt 5) und wann, setzt der Nutzer beim Start; der Slice nennt sie in §4 erst dann. Der Lauf verbraucht diese Version auf **zwei Registries** (nicht rücknehmbar); das ist der Preis der Messung, den [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) Entscheidung 6 als akzeptiertes Negativ nennt (ein Probe-Release auf der öffentlichen Seite wäre Außenwirkung; der Dispatch-Lauf vorab belegt alles außer dem Anlegen).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Bau der Stage, des Skripts, des Jobs und der Selbsttests — [slice-release-dateien-bauen](slice-release-dateien-bauen.md) und [slice-release-job-im-workflow-bauen](slice-release-job-im-workflow-bauen.md). Grund: sie sind vorher gebaut und ohne Außenwirkung belegt; hier läuft das Gebaute einmal echt. Ergibt der Lauf einen Fehler in ihnen, ist es eine Nachbesserung mit Re-Review oder ein neuer Slice, kein stiller Eingriff.
- Die nachträglichen Releases `v0.1.0` und `v0.1.1` — [slice-release-nachtraeglich-bestandstags](slice-release-nachtraeglich-bestandstags.md). Grund: [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) Entscheidung 5 setzt sie nach die erste Lieferung des neuen Formats und unter ausdrückliche Bestätigung.
- Inhalt der Version (welche Änderungen sie trägt). Grund: Sache des Nutzers; der Slice schreibt höchstens den Release-Commit (Version ohne `-SNAPSHOT`, [`releasing.md`](../../../maintainer/releasing.md) Schritt 5) und den Vermerk (Schritt 8).
- Anlegen von Tag-Schutz, Secrets, Repository-Einstellungen: Handlung des Nutzers. Grund: ein Agent verändert keine Einstellungen am Repository (kein Agent setzt Tag oder pusht).
- Signierung, Software-Stückliste, Maven Central: [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) Entscheidung 4; keiner der Re-Evaluierungs-Trigger ist eingetreten.
- Eine Änderung der Notiz-Vorlage nach dem Befund (zum Beispiel ein Satz zur Gleichheit der Jars bei positiver Messung 4): neuer Slice oder Nachbesserung, nach Entscheidung des Nutzers. Grund: die Vorlage sagt bis zur Messung nichts darüber ([ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) Entscheidung 4).
- Mess-Pflicht 8 (Lizenzen) und Mess-Pflicht 5 (Mutationen am Archiv): [slice-release-lizenzen-lib-belegen](../in-progress/slice-release-lizenzen-lib-belegen.md) und [slice-release-dateien-bauen](slice-release-dateien-bauen.md) (lokal belegt). Grund: sie hängen nicht an einem Lauf auf GitHub; das Protokoll dieses Slice verweist auf deren Belege.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **Dispatch-Lauf ohne Tag, vorab:** Der Nutzer löst `workflow_dispatch` auf `main` aus; die Jobs `gates` und `vorschau` laufen grün, `vorschau` baut `make release-dateien` ohne Token und ohne `gh`, die Jobs `publish`, `publish-cloudsmith` und `release` laufen nicht (Lauf-Kennung, Dauer je Job, Dateiliste und Prüfsummen aus dem Protokoll; Mess-Pflicht 5 und Teil von 7 auf der CI). Es entsteht keine Version und kein Release.
- [ ] **Tag-Lauf mit Release:** Release-Commit mit Version `<V>` ohne `-SNAPSHOT` (Version setzt der Nutzer, Schritte 1 bis 8 von [`releasing.md`](../../../maintainer/releasing.md) mit Vermerk), Tag `v<V>` auf diesem Commit durch den Nutzer; der Lauf führt `gates`, `publish`, `publish-cloudsmith` und `release`, alle grün (Lauf-Kennung, Versuche, Dauer je Job); die Release-Seite zeigt Titel `<V>`, die feste Notiz aus der Vorlage, `tdswire-<V>.zip`, `tdswire.jar` und `SHA256SUMS`; die Prüfsummen der Notiz stimmen mit denen der heruntergeladenen Dateien überein. Die Ausgabe jedes Versuchs wird gesichert, bevor ein Job wiederholt wird.
- [ ] **Messprotokoll der Mess-Pflichten** unter `docs/messungen/` (Datum, Lauf-Kennung, Ausgang, kein Token; [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) Entscheidung 7), je Punkt mit Messweg aus §3: **1** anonymer Abruf der Anhänge (Statuscode ohne Anmeldung je Datei); **2** das Recht `contents: write` legt Release und Anhänge für den vorhandenen Tag an (Lauf grün), der Wert der Einstellung „Workflow permissions“ des Repositories steht im Protokoll (vom Nutzer gelesen); **3 (zweite Hälfte)** ein Re-run des Jobs `release` bei bestehendem Release bricht in der Vorabprüfung ohne zu überschreiben; **4** `sha256` von `tdswire.jar` des Releases gegen das Jar des Pakets bei Cloudsmith (anonymer Abruf): gleich oder verschieden, gemessen; **6 (Einstellung)** ob das Repository unveränderliche Releases führt (vom Nutzer gelesen); **7** Dauer des Jobs `release` und Wirkung auf den Gesamtlauf aus den Job-Zeiten. **Nicht aus einem grünen Lauf zu messen:** Mess-Pflicht 3 (erste Hälfte: Re-run eines roten Pakets lässt `release` nachlaufen) und 6 (Abbruch bei `gh release create` hinterlässt ein Release ohne Anhänge). Sie stehen im Protokoll als „nicht gemessen“, sofern der Nutzer keinen Messweg freigibt (Probe-Repository oder anderer Aufbau, Entscheidung des Nutzers); keine Zusage in Anleitung oder Notiz, solange sie offen sind.

Nachweis-Pflichten für alle drei Punkte:

- [ ] `make gates` grün; für die Doku-Teile `make docs-check`, `make flaeche`, `make kopplung-zahlen` ([`harness/README.md`](../../../../harness/README.md) §Minimal agent workflow, Zeile „Doku oder Spec“). Zusätzlich `make gates` auf dem Release-Commit grün, `make e2e` als Schritt 2 von [`releasing.md`](../../../maintainer/releasing.md) (Ergebnis und Dauer im Vermerk); `make clean-build` ist hier nicht erforderlich.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow ([`AGENTS.md`](../../../../AGENTS.md) §6), kein Self-Review (Modul 8). Der Review prüft das Messprotokoll: jede Zahl und jeder Statuscode mit Fundstelle (Lauf-Kennung, Befehl), keine Aussage über das Gemessene hinaus. Enthält die Nachbesserung Code oder Skripte, folgt ein zweiter Review in frischem Kontext (§3, Ablauf).
- [ ] Doku-Update, soweit die Beschreibung des Werkzeugs oder des Ablaufs sich ändert: [`releasing.md`](../../../maintainer/releasing.md) und [`harness/README.md`](../../../../harness/README.md) §Sensors: die Sätze aus [slice-release-dateien-bauen](slice-release-dateien-bauen.md) und [slice-release-job-im-workflow-bauen](slice-release-job-im-workflow-bauen.md) im Zustand „gebaut, nicht gelaufen“ werden auf „gelaufen“ gehoben, soweit das Messprotokoll sie belegt, und nur so weit; die Liste „Ungemessen“ in [`releasing.md`](../../../maintainer/releasing.md) nennt, was offen blieb (Teilfehler-Hälfte von Mess-Pflicht 3, Abbruch beim Anlegen, falls kein Messweg freigegeben); die Notiz-Vorlage bleibt unverändert, auch bei positiven Befunden (siehe Abgrenzung). `docs/user/` ändert sich nur um eine Aussage, die Mess-Pflicht 1 oder 4 belegt; sonst bleibt sie unverändert.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben; **kein Zaehler wird gesetzt**. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

**Prüfung der Liefer-Punkte gegen Bauart und Werkzeug** (`/plan-welle` Schritt 6b): Frage 1 — die Messungen sind Läufe gegen GitHub, `curl` gegen die öffentliche Seite und Abfragen mit `gh api` (lesend); der Selbsttest sieht nichts davon. Der Messweg je Mess-Pflicht: **1** `curl -sI -L` ohne Header gegen die Download-URL jedes Anhangs, Statuscode und Zielhost; **2** das Ergebnis des Jobs `release`, dazu der Wert der Einstellung „Workflow permissions“ in den Einstellungen des Repositories, gelesen vom Nutzer (Weg und Name der Einstellung sind **nicht gelesen**; ein Agent sieht den Wert nicht ohne Admin-Recht); **3** Re-run des Jobs `release` nach dem grünen Lauf, durch den Nutzer (Oberfläche oder `gh run rerun`, die Aufrufform wählt der Nutzer, hier **nicht gelesen**); **4** `sha256sum` des Release-Jars und des anonym bei Cloudsmith abgerufenen Jars; **6** die Einstellung „immutable releases“ (Name und Ort der Einstellung sind **nicht gelesen**, der Nutzer nennt den Wert); **7** die Job-Zeiten je Versuch (Form wie im [Messprotokoll 0.1.1](../../../messungen/veroeffentlichung-0-1-1-2026-11-15.md): `gh api repos/pt9912/tdswire/actions/runs/<Lauf>/attempts/<n>/jobs`). Frage 2 — der Tag-Push, die Einstellungen und das Token liegen beim Nutzer: sie sind Start-Trigger und benannte Nutzer-Handlungen ([ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Entscheidung 1), nicht Lieferinhalt eines Agenten; steht der Nutzer nicht zur Verfügung, gilt die Rückführung in §4.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `build.gradle.kts` (Version), Release-Vermerk | update | Release-Commit nach [`releasing.md`](../../../maintainer/releasing.md) Schritt 5 und 8; Version setzt der Nutzer; der Folge-Commit mit `-SNAPSHOT` nach dessen Wahl |
| `docs/messungen/` (neues Messprotokoll zum Tag-Lauf mit Release) | neu | dritter Punkt; ein Dispatch-Protokoll darf im selben Dokument stehen |
| `docs/maintainer/releasing.md`, `harness/README.md` (§Sensors) | update | Sätze auf „gelaufen“ heben, soweit belegt (Doku-Update der DoD) |
| `docs/user/einbettung-und-betrieb.md` | update, nur bei Befund 1 oder 4 | eine Aussage über den Weg ohne Registry, nur so weit der Beleg trägt |

**Ablauf:** (1) Implementer bereitet den Release-Commit vor (Schritte 1 bis 8 von [`releasing.md`](../../../maintainer/releasing.md), `make gates`, `make e2e`, Vermerk); der Nutzer löst den Dispatch-Lauf aus, danach setzt er Tag und Push; der Implementer sichert die Ausgabe jedes Laufs und misst. (2) Review in frischem Kontext gegen das Messprotokoll. (3) Verifier prüft gegen die DoD, öffnet die Lauf-Kennungen erneut und wiederholt die lesenden Messungen (`curl`, Hash). (4) Der Implementer setzt keinen Tag, pusht nicht und löst keinen Lauf aus.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): (1) [slice-release-job-im-workflow-bauen](slice-release-job-im-workflow-bauen.md) liegt in `done/` (und damit [slice-release-lizenzen-lib-belegen](../in-progress/slice-release-lizenzen-lib-belegen.md) und [slice-release-dateien-bauen](slice-release-dateien-bauen.md)). (2) [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) ist `Accepted` (erfüllt, 2026-11-16, in `main` gemergt). (3) Der Nutzer hat **Version und Zeitpunkt** der Veröffentlichung ausdrücklich bestätigt; die Bestätigung steht im Kopf dieses Slice, wie bei `slice-cloudsmith-veroeffentlichung-0-1-1`. Das ist die Wahl der Version, **nicht der Planner**. (4) Der Nutzer hat den Stand von `main` mit dem Workflow gepusht (Handlung des Nutzers; ohne sie läuft nichts), ausdrücklich angewiesen, Dispatch und Tag-Push zu setzen (**kein Agent setzt Tag oder pusht**), und die Voraussetzungen aus [`releasing.md`](../../../maintainer/releasing.md) erfüllt (Tag-Schutz `v*` gesetzt und die Gegenprobe ohne Bypass durch den Nutzer gelaufen und in `releasing.md` eingetragen — Entscheidung des Nutzers 2026-11-16, weil `contents: write` das Gewicht des Schutzes erhöht; die Secrets für Cloudsmith und der Tag-Schutz stehen wie bei der letzten Veröffentlichung; ein Agent sieht beides nicht). (4a) Entscheidungen des Nutzers 2026-11-16: kein Probe-Repository, die Mess-Pflichten 3 und 6 bleiben ungemessen und stehen so in der Anleitung; die Werte der Einstellungen „Workflow permissions“ und „unveränderliche Releases“ liest der Nutzer und trägt sie ins Messprotokoll ein. (5) Der Nutzer hat den Beleg aus [slice-release-lizenzen-lib-belegen](../in-progress/slice-release-lizenzen-lib-belegen.md) gelesen und die dort benannten offenen Fragen beantwortet oder bewusst offen gelassen (der erste Anhang von `lib/` gibt fremde Binärdateien weiter, [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) Entscheidung 3). (6) Ein Verantwortlicher ist eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß): die Messung zerfällt, weil die Mess-Pflichten 3 und 6 einen eigenen Aufbau (Probe-Repository) verlangen; sie werden ein eigener Slice, dieser behält Punkt 1 und 2 und die übrigen Messungen.
- `in-progress` → `open` (blockiert): der Nutzer setzt den Tag nicht; der Dispatch-Lauf ist rot und die Ursache liegt im Workflow (dann Nachbesserung oder Rückführung nach [slice-release-job-im-workflow-bauen](slice-release-job-im-workflow-bauen.md)); der Job `release` ist rot, weil `GITHUB_TOKEN` das Recht nicht hat (**Re-Evaluierungs-Trigger von [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md)**: persönliches Token oder neue ADR; die Version ist dann auf beiden Registries verbraucht, das Release lässt sich von Hand nachholen); Mess-Pflicht 1 zeigt, dass die Anhänge nicht anonym ladbar sind (dann entfällt der Zweck „ohne Registry“, neue Entscheidung des Nutzers).

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Beobachtbar: (1) Das Messprotokoll liegt unter `docs/messungen/` mit Lauf-Kennungen und Statuscodes je Punkt der DoD, ohne Token; der Verifier hat die lesenden Messungen wiederholt. (2) Die Release-Seite der Version zeigt die drei Anhänge, die Prüfsummen stimmen. (3) Alle Punkte der DoD erledigt, `make gates` grün, Closure-Notiz mit Lerneintrag geschrieben. Die Closure-Notiz nennt außerdem das Ergebnis je Mess-Pflicht (gemessen, nicht gemessen, mit Grund), die Sätze, die sie auf „gelaufen“ gehoben hat, und was ungemessen blieb, damit [slice-release-nachtraeglich-bestandstags](slice-release-nachtraeglich-bestandstags.md) und die Doku nur das Gemessene tragen.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **Eine Version wird auf zwei Registries verbraucht, bevor das Anlegen des Releases gemessen ist:** schlägt `release` fehl, ist das Release von Hand nachzuholen ([ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) Entscheidung 6, akzeptiertes Negativ). — **Ausgang:** offen bis zur Closure.
- Mess-Pflicht 3 (erste Hälfte) und 6 (Abbruch) lassen sich an einem grünen Lauf nicht messen; ohne Messweg bleiben sie ungemessen und stehen so in der Anleitung (Register: `folgepflicht-in-dod-ohne-messweg` vermieden, indem die DoD nur zusagt, was messbar ist). — **Ausgang:** offen bis zur Closure; erwartet „weiter offen“, falls der Nutzer keinen Messweg freigibt.
- Die Einstellungen „Workflow permissions“ und „unveränderliche Releases“ liest nur der Nutzer; sein Wert steht im Protokoll ohne Gewähr gegen spätere Änderung. — **Ausgang:** offen bis zur Closure.
- Das Anlegen mit Anhängen kann bei einem Teilfehler ein Release ohne Anhänge hinterlassen (ungemessen, [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) Entscheidung 1); der Nutzer entscheidet über den Teilbestand wie bei den Paketen. — **Ausgang:** offen bis zur Closure.
- Ein Befund kann eine Setzung der ADR widerlegen (Jars verschieden, Anhänge nicht anonym ladbar, Recht fehlt): dann Re-Evaluierungs-Trigger von [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md), kein stiller Eingriff in die `Accepted`-ADR ([`AGENTS.md`](../../../../AGENTS.md) §3.5). — **Ausgang:** offen bis zur Closure.
- Die Ausgabe eines roten Versuchs geht verloren, wenn der Job vor dem Sichern wiederholt wird (Register: `einmal-rot-ohne-gesicherte-ausgabe`, 2×). — **Ausgang:** offen bis zur Closure; Gegenmaßnahme ist der Ablauf in §3 und der Dispatch-Lauf vorab.

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

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen; Treffer: `BEO-TDS/einmal-rot-ohne-gesicherte-ausgabe` (2×; Grund für das Sichern der Ausgabe jedes Versuchs vor einem Re-run und für den Dispatch-Lauf vorab), `BEO-TDS/nachweis-von-der-falschen-seite-der-grenze` (2×; dieser Slice ist die Gegenseite des Stubs aus [slice-release-job-im-workflow-bauen](slice-release-job-im-workflow-bauen.md)), `BEO-TDS/dod-nennt-nutzerentscheidung-als-lieferinhalt` und `BEO-TDS/dod-verlangt-messung-mit-geheimnis-des-nutzers` (Tag-Push, Einstellungen und Token liegen beim Nutzer: der Slice schneidet sie als Start-Trigger und als Nutzer-Handlung mit benanntem Träger und Rückführung, nicht als Lieferinhalt eines Agenten), `BEO-TDS/folgepflicht-in-dod-ohne-messweg` (je Mess-Pflicht steht der Messweg in §3), [`BEO-TDS/lizenzhinweise-der-bibliotheken-rechtsfrage`](../observations/BEO-TDS/lizenzhinweise-der-bibliotheken-rechtsfrage/observation.md) (Beleg aus [slice-release-lizenzen-lib-belegen](../in-progress/slice-release-lizenzen-lib-belegen.md), Rechtsfrage offen). Kein Zähler erreicht mit diesem Slice 3×, soweit er am Plan abzusehen ist.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
