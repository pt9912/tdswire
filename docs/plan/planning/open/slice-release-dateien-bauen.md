# Slice slice-release-dateien-bauen: Release-Dateien (ZIP mit Jar, `lib/` und Lizenzdateien, einzelnes Jar, `SHA256SUMS`) in Docker bauen und am entpackten Archiv prüfen, ohne Wirkung nach außen

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** ohne Welle — keine Closure-Bedingung, die von der DoD dieses Slice verschieden ist. Gewählt wie bei den Veröffentlichungs-Slices zu [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) und [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) (auch sie ohne Welle); [welle-referenzabgleich-release](../welle-referenzabgleich-release.md) führt diese Slices in ihrer Liste nicht, und [welle-qualitaet-grundlage](../welle-qualitaet-grundlage.md) trägt Qualitätsmessungen, kein Veröffentlichen. Offene Wellen und ihre Slice-Listen am 2026-11-16 gelesen.

**Bezug:** [TDS-RB-05](../../../../spec/lastenheft.md#tds-rb-05--lizenz) (Scope), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) (Scope: Bau in Docker, kein `build/` auf dem Host), [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) (`Accepted` 2026-11-16, Entscheidungen 2 und 6, Mess-Pflichten 5 und 8, Folgepflicht), [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) (Produktschnitt: Jar ohne `Main-Class`), [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md) (nicht in den Gates), [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md) (Selbsttest der Klasse `werkzeug`), [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) (Entscheidung 2: Jar und POM aus dem Bau).

**Berührte Spec-Stellen:** `SPEC-052` nur lesend (Manifest, `lib/`, Lizenz im Jar; die Spezifikation führt die Release-Form nicht, [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) schärft keine Spec-Stelle, am 2026-11-16 gelesen). Keine Spec-Änderung geplant.

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

**Ziel:** `make release-dateien` baut in Docker aus dem Stand des Baums ein Verzeichnis mit `tdswire-<Version>.zip` (`tdswire.jar`, `lib/` samt den Lizenzdateien der Bibliotheken, `LICENSE`), dem einzelnen `tdswire.jar` und `SHA256SUMS`, und bricht, wenn eine Prüfung am **entpackten Archiv** rot ist oder zu einer Datei in `lib/` die Lizenzdatei fehlt. Ohne Wirkung nach außen: kein Token, kein `gh`, kein Netz außer dem Bau selbst; `make build` bleibt unverändert und schreibt weiter nichts auf den Host.

**Warum ein eigener Slice, getrennt vom Workflow und von der Veröffentlichung:** Der Bau ist wiederholbar und lokal belegbar, die Veröffentlichung nicht (Register: `einmal-rot-ohne-gesicherte-ausgabe`, 2×). Was am einzigen echten Tag-Lauf rot werden kann (ZIP-Inhalt, Prüfung, Prüfsummen), soll vorher an Mutationen rot geworden sein. Der Slice hat anderen Eingang als der Workflow (`Dockerfile`, `tools/`, Gradle-Ausgabe statt `.github/`) und ist ohne Workflow lieferbar. Dieselbe Zerlegung wie bei [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) (Ziel bauen, dann veröffentlichen).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Der Job `release`, `gh release create`, die Notiz-Vorlage, die Wiederholbarkeits-Prüfung, die Zeile `veroeffentlichung.yml` — [slice-release-job-im-workflow-bauen](slice-release-job-im-workflow-bauen.md). Grund: das ist die Außenwirkung und das Recht `contents: write`; dieser Slice ruft weder GitHub noch ein Token.
- Der Beleg der Lizenzen und die Lizenzdateien selbst — [slice-release-lizenzen-lib-belegen](../in-progress/slice-release-lizenzen-lib-belegen.md) (**Voraussetzung**, Start-Trigger). Grund: Lesen und Belegen von Lizenztexten ist kein Werkzeug und hat einen eigenen Ausgang; dieser Slice **liest** die Dateien nur.
- Der Dispatch-Lauf von `vorschau` mit `make release-dateien` — [slice-release-job-im-workflow-bauen](slice-release-job-im-workflow-bauen.md) trägt die Zeile im Workflow, [slice-release-im-tag-lauf-messen](slice-release-im-tag-lauf-messen.md) den Lauf. Grund: Workflow-Änderung.
- Gleichheit des Jars im ZIP mit dem Jar im Maven-Paket (Mess-Pflicht 4) und anonymer Abruf der Anhänge (Mess-Pflicht 1) — [slice-release-im-tag-lauf-messen](slice-release-im-tag-lauf-messen.md). Grund: beides braucht ein Release und ein Paket auf den echten Zielen. Dieser Slice behauptet keine Gleichheit mit dem Paket-Jar.
- Reproduzierbare Bytes des ZIP (feste Zeitstempel, feste Reihenfolge). Grund: [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) fordert sie nicht; die Notiz nennt die Prüfsummen der **angehängten** Dateien, nicht eines zweiten Baus. Ob der Implementer sie ohnehin herstellt, steht in §7.
- Signatur, Software-Stückliste, `sources`- und `javadoc`-Jar: [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) Entscheidung 4, nicht gewählt.
- Eine Änderung der Stage `build-jar`, von `make build` oder `build.gradle.kts`. Grund: [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) Entscheidung 2 (2a) lässt `make build` unverändert; die neue Stage erbt von `build-jar`.

**Zielbild-Prüfung** (Maßstab: das Zielbild führt, Code folgt; „Zielbild anpassen“ nur mit Entscheidung des Nutzers). Aus [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) gelesen und auf diesen Slice zugeschnitten:

| Stelle | Code angleichen | Zielbild anpassen | Entscheid |
|---|---|---|---|
| [`releasing.md`](../../../maintainer/releasing.md) Schritt 6 („ein Weg für Jar und `lib/` ohne Registry besteht nicht“, `make build` schreibt das Jar nicht auf den Host) | Satz nachziehen: der Weg ist `make release-dateien` (Zustand „gebaut, nicht in der CI gelaufen“); die Aussage zu `make build` bleibt wahr | — | Nachzug, nichts zu entscheiden |
| [`releasing.md`](../../../maintainer/releasing.md) Schritt 7 („Hinweise zu den Lizenzen der Bibliotheken führt das Artefakt nicht“) | Satz nachziehen: das ZIP führt die Lizenzdateien nach dem Beleg aus [slice-release-lizenzen-lib-belegen](../in-progress/slice-release-lizenzen-lib-belegen.md); die Rechtsfrage bleibt offen, die Anleitung behauptet dazu nichts | — | Nachzug, nichts zu entscheiden |
| [`harness/README.md`](../../../../harness/README.md) §Sensors: neue Zeile `make release-dateien`; Zeile `make skript-selbsttest` um die neuen Prüfungen | Zeilen ergänzen, Bindung [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md), Klasse `werkzeug` | — | Nachzug, nichts zu entscheiden |
| Gates ([`AGENTS.md`](../../../../AGENTS.md) §3.6) | `make skript-selbsttest` wird erweitert, keine Schwelle sinkt; das Target steht in keiner Gate-Liste ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)) | — | nichts zu tun |

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **Stage und Target liefern die Dateien:** Eine neue Stage im `Dockerfile` (`FROM build-jar`) und das Target `make release-dateien` (Muster `lock-export`: Export per `--output type=local,dest=<Verzeichnis>`, nichts gemountet, kein `build/` auf dem Host) liefern `tdswire-<Version>.zip` mit `tdswire.jar`, `lib/` (die Jars aus dem Bau, dazu die Lizenzdateien aus [slice-release-lizenzen-lib-belegen](../in-progress/slice-release-lizenzen-lib-belegen.md) im Archiv) und `LICENSE`, das einzelne `tdswire.jar` und `SHA256SUMS` (Format `sha256sum`, jede Anhangsdatei außer sich selbst, im Container mit `sha256sum -c` geprüft). Die Version liest der Bau aus `build.gradle.kts` (Zeile `version = "..."`, wie `tools/publish.sh`); er bricht bei `-SNAPSHOT` oder einer Version, die nicht `<zahl>.<zahl>.<zahl>` ist, und der Dateiname des ZIP trägt sie. Das Target steht in keiner Liste `GATE_CHECKS`, `GATE_KLASSE_*`, `GATE_STAGE` und nicht in `tools/mk/e2e.mk`; neue `COPY`-Quellen stehen in `tools/stempel/*.eingaenge` (`make stempel-eingaenge-pruefung` ist grün). `make build` ist unverändert und schreibt weiter nichts auf den Host.
- [ ] **Prüfungen am entpackten Archiv:** Der Container entpackt das fertige ZIP in ein leeres Verzeichnis und prüft **dort** (nicht am Baum davor): `tools/manifest-pruefung.sh` (keine `Main-Class`, `Class-Path` nur gegen vorhandene Einträge `lib/<Name>`, kein Jackson in `lib/`, kein Testserver im Jar; das Skript verlangt heute eine POM als zweites Argument — ob es einen Modus ohne POM bekommt oder die POM der Stage mitgegeben wird, entscheidet der Implementer; eine Änderung am Skript trägt ihren Selbsttest in `tools/manifest-selbsttest.sh`), `META-INF/LICENSE` im Jar gleich `LICENSE` (und `LICENSE` im ZIP gleich der Datei im Repo), und die **Sollmenge der Lizenz- und Hinweisdateien** (aus dem Beleg [lizenzen-lib-2026-11-16](../../../messungen/lizenzen-lib-2026-11-16.md), Ablage `lizenzen/lib/`): alle **sieben** Dateien liegen im Archiv, nicht nur eine je Jar in `lib/` — `org.jetbrains.kotlin-kotlin-stdlib-2.4.10/{LICENSE.txt, NOTICE.txt, third_party/boost_LICENSE.txt, third_party/threetenbp_license.txt}`, `org.jetbrains.kotlinx-kotlinx-coroutines-core-jvm-1.10.1/{LICENSE.txt, NOTICE.txt}`, `org.jetbrains-annotations-23.0.0/LICENSE.txt` (die Form im Archiv, Verzeichnis oder eine Datei, ist offene Frage 2c des Belegs und Entscheidung des Nutzers). Fehlt eine, auch nur eine Hinweisdatei (NOTICE), **bricht der Lauf**, es entsteht kein Verzeichnis mit stillem Teilinhalt ([ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) Entscheidung 3). Grund: eine Prüfung „je Jar eine Lizenzdatei“ meldet eine fehlende NOTICE von kotlinx-coroutines nicht, die LICENSE wäre vorhanden; im Repo besteht sonst kein Sensor für zu wenig abgelegte Dateien unter `lizenzen/lib/` (Verifikation F-2 zu [slice-release-lizenzen-lib-belegen](../in-progress/slice-release-lizenzen-lib-belegen.md)). Ändert sich `lib/`, bricht dieselbe Prüfung an der neuen Bibliothek ohne Sollzeile.
- [ ] **Selbsttest mit Mutationen in `make skript-selbsttest`** (Klasse `werkzeug`, [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md)): je eine Mutation des Eingangs macht den Lauf rot — Jackson in `lib/`, `Main-Class` im Manifest, eine andere `LICENSE` im Jar, ein `Class-Path`-Eintrag ohne Datei, eine Lizenzdatei entfernt, **eine NOTICE entfernt** (die von kotlinx-coroutines, die LICENSE bleibt: rot), `SHA256SUMS` passt nicht zu einer Datei (Mess-Pflichten 5 und 8, soweit lokal messbar); ein Gutfall bleibt grün. Der Test liest den Archivinhalt, nicht den Baum davor. Wie mutiert wird (Mini-Archiv im Selbsttest oder gezielte Veränderung in einer Wegwerf-Stage), legt der Implementer fest; die Grenze steht im Kopf des Skripts.

Nachweis-Pflichten für alle drei Punkte:

- [ ] `make gates` grün; für die Doku-Teile `make docs-check`, `make flaeche`, `make kopplung-zahlen` ([`harness/README.md`](../../../../harness/README.md) §Minimal agent workflow, Zeile „Doku oder Spec“). `make release-dateien` selbst läuft einmal auf dem End-Stand; Ausgabe, Gesamtlaufzeit und die größten Zeitposten stehen im Bericht des Implementers ([`AGENTS.md`](../../../../AGENTS.md) §6). Die Tabelle der Pflicht-Sensoren führt für Werkzeug und Build-Datei keine eigene Zeile (§6); `make clean-build` und `make e2e` sehen sie nur für `src/` und `testserver/` vor, beides ist hier nicht erforderlich.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow ([`AGENTS.md`](../../../../AGENTS.md) §6), kein Self-Review (Modul 8). Der Review prüft zusätzlich, was kein Sensor prüft: dass der Export nichts auf den Host mountet, dass die Prüfungen am **entpackten** Archiv laufen und dass kein Token und kein Netz zu einem Dienst im Ablauf vorkommt. Enthält die Nachbesserung Code oder Skripte, folgt ein zweiter Review in frischem Kontext (§3, Ablauf).
- [ ] Doku-Update, soweit die Beschreibung des Werkzeugs oder des Ablaufs sich ändert: in [`harness/README.md`](../../../../harness/README.md) §Sensors die Zeile `make release-dateien` und die Zeile `make skript-selbsttest`; in [`releasing.md`](../../../maintainer/releasing.md) Schritt 6 und 7 (siehe Zielbild-Prüfung); die Kopfkommentare der neuen Stage, des neuen Make-Fragments und der Prüfskripte. Die Aussagen stehen im Zustand „gebaut, nicht in der CI gelaufen“ ([`AGENTS.md`](../../../../AGENTS.md) §3.8); `docs/user/` bleibt unverändert.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben; **kein Zaehler wird gesetzt**. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

**Prüfung der Liefer-Punkte gegen Bauart und Werkzeug** (`/plan-welle` Schritt 6b): Frage 1 — der Selbsttest sieht die Prüfungen am Archiv, wenn er ein Mini-Archiv baut oder verändert; die Stage selbst (Export, Version aus `build.gradle.kts`, `sha256sum -c` im Container) sieht nur der Lauf von `make release-dateien`, deshalb steht dessen Beleg im Bericht des Implementers und im Review-Report, nicht im Selbsttest. Ob das Image der Stage `zip` führt oder ein anderes Werkzeug für das ZIP nötig ist, ist **nicht gelesen**; die Wahl gehört dem Implementer und darf kein neues Netz im Lauf brauchen. Frage 2 — kein Punkt nennt eine Entscheidung des Nutzers als Lieferinhalt (die Entscheidungen N5 und N6 liegen in [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md)).

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `Dockerfile` | update | neue Stage `FROM build-jar` (Archiv, Prüfung, `SHA256SUMS`) und eine Export-Stage nach dem Muster `lock-export`; Stage `skript-selbsttest` um neue Skripte erweitert; Kopfkommentar der Stage nennt Zusage und Grenze |
| `tools/mk/` (neues Fragment oder `publish.mk`) | neu / update | Target `make release-dateien` mit Hilfezeile; in keiner Gate-Liste |
| `tools/manifest-pruefung.sh`, `tools/manifest-selbsttest.sh` | update, falls nötig | Modus ohne POM oder POM aus der Stage; Selbsttest mit Mutation je neuem Zweig (Klasse `werkzeug`) |
| Prüfskript der Stage (Name wählt der Implementer) | neu | entpackt, prüft die Sollmenge der sieben Lizenz- und Hinweisdateien (NOTICE eingeschlossen) und eine Lizenzdatei je Jar in `lib/`, `LICENSE`, `sha256sum -c`; Kopf nennt Zusage und Grenze |
| `tools/mk/skript-selbsttest.mk`, Selbsttest-Skript(e) | update | Hilfezeile; Mutationen am Archiv; Abwesenheit in den Gate-Listen |
| `tools/stempel/*.eingaenge` | update | neue `COPY`-Quellen (Lizenzdateien aus [slice-release-lizenzen-lib-belegen](../in-progress/slice-release-lizenzen-lib-belegen.md), neue Skripte); `make stempel-eingaenge-pruefung` bricht sonst |
| `harness/README.md` (§Sensors), `docs/maintainer/releasing.md` | update | Beschreibungen nachziehen (Doku-Update der DoD) |

**Ablauf:** (1) Implementer baut die drei Punkte; der Befund am Lauf ist Bericht, keine Schätzung. (2) Review in frischem Kontext, Änderungsart-Sensoren aus der DoD. (3) Enthält die Behebung Code, Skripte oder Make-/Build-Dateien, folgt ein Re-Review in frischem Kontext; sonst der Vermerk „Nachbesserung ohne zweiten Review“ in §7. (4) Verifier prüft gegen die DoD, fährt `make release-dateien` und `make gates` auf dem End-Stand und entpackt das ZIP selbst. (5) Nichts wird veröffentlicht, kein Tag gesetzt, nichts gepusht.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): (1) [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) ist `Accepted` (erfüllt, 2026-11-16, in `main` gemergt). (2) [slice-release-lizenzen-lib-belegen](../in-progress/slice-release-lizenzen-lib-belegen.md) liegt in `done/`: der Beleg und die Lizenzdateien sind im Repo, und der Beleg nennt keine Anforderung, die das ZIP nicht erfüllen kann (sonst Entscheidung des Nutzers, der Slice beginnt nicht). (3) Ein Verantwortlicher ist eingetragen. (4) Der Nutzer hat nichts zu tun: der Slice schreibt nichts nach außen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß): die Prüfung am Archiv verlangt einen Umbau von `tools/manifest-pruefung.sh` über einen Modus hinaus, oder der Selbsttest braucht ein eigenes Werkzeug-Image; der dritte Punkt wird ein eigener Slice, bevor [slice-release-job-im-workflow-bauen](slice-release-job-im-workflow-bauen.md) beginnt.
- `in-progress` → `open` (blockiert): das Image der Stage führt kein Werkzeug für ein ZIP und ein Nachladen bräche die Docker-Zusage ([`AGENTS.md`](../../../../AGENTS.md) §3.1); eine Lizenzdatei aus [slice-release-lizenzen-lib-belegen](../in-progress/slice-release-lizenzen-lib-belegen.md) lässt sich nicht byte-gleich ins Archiv legen (Hinweispflicht der Lizenz), dann Entscheidung des Nutzers.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Beobachtbar: (1) `make release-dateien` läuft grün und liefert drei Dateien; das entpackte ZIP führt `tdswire.jar`, `lib/` mit je einer Lizenzdatei je Bibliothek und `LICENSE`; `sha256sum -c SHA256SUMS` ist grün (Ausgaben im Bericht und im Review-Report, vom Verifier selbst gefahren). (2) `make skript-selbsttest` (in `make gates`) ist grün, und jede der sieben Mutationen ist rot, einzeln im Bericht genannt. (3) Alle Punkte der DoD erledigt, `make gates` grün, Closure-Notiz mit Lerneintrag geschrieben. Die Closure-Notiz nennt außerdem die Namen von Stage, Target und Prüfskript, die Zeilen in [`harness/README.md`](../../../../harness/README.md) und [`releasing.md`](../../../maintainer/releasing.md) im Zustand „gebaut, nicht gelaufen“, damit [slice-release-im-tag-lauf-messen](slice-release-im-tag-lauf-messen.md) sie auf „gelaufen“ hebt.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **Die Tabelle der Pflicht-Sensoren hat keine Zeile für Werkzeug, Workflow und Build-Datei:** der Plan nimmt `make gates`, für die Doku-Teile die Zeile „Doku oder Spec“ und den Lauf des gelieferten Targets selbst (kein zusätzlicher Sensor, sondern der Gegenstand); ob die Tabelle eine eigene Zeile bekommt, ist Sache des Nutzers (derselbe offene Punkt wie in `slice-cloudsmith-ziel-bauen` §6). — **Ausgang:** offen bis zur Closure.
- Die Prüfung am Archiv sieht nur, was ihre Mutationen abdecken; andere Bibliotheken der Testserver-Seite als Jackson erkennt `tools/manifest-pruefung.sh` nicht (Grenze im Kopf des Skripts). — **Ausgang:** offen bis zur Closure.
- Ob das ZIP aus zwei Läufen byte-gleich ist, ist nicht zugesagt; die Prüfsummen der Notiz gelten der angehängten Datei. — **Ausgang:** offen bis zur Closure.
- Das Jar im ZIP stammt aus der Stage `build-jar` (`gradle :assemble`), das Jar im Paket aus der Stage `publish` (`gradle :jar`); ob beide gleich sind, ist ungemessen ([ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) Entscheidung 2). — **Ausgang:** offen bis zur Closure; erwartet „weiter offen, Träger [slice-release-im-tag-lauf-messen](slice-release-im-tag-lauf-messen.md) (Mess-Pflicht 4)“.
- Neue `COPY`-Quelle oder neues Fragment: `make stempel-eingaenge-pruefung` bricht, wenn eine Datei nicht in `tools/stempel/*.eingaenge` steht; Gate-Klasse und Stempel ändern sich mit dem `Dockerfile` und den Skripten. — **Ausgang:** offen bis zur Closure.
- Der Selbsttest ist der Sensor für die Zusage „bricht ohne Lizenzdatei“; fällt der Bau von `lib/` später anders aus (neue Bibliothek mit zwei Lizenzdateien), meldet er sich erst am echten Archiv. — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen; Treffer: `BEO-TDS/eingangsliste-je-stage-von-hand` (die neue Stage braucht Einträge in `tools/stempel/*.eingaenge`; `make stempel-eingaenge-pruefung` ist der Sensor), `BEO-TDS/zusage-ohne-sensor-in-der-ci-kette` (Zusagen des Baus brauchen einen Sensor: hier der Selbsttest mit Mutationen), `BEO-TDS/zwei-artefakt-stages-nebeneinander` (die neue Stage erbt von `build-jar` und führt keine zweite Definition des Artefakts), [`BEO-TDS/lizenzhinweise-der-bibliotheken-rechtsfrage`](../observations/BEO-TDS/lizenzhinweise-der-bibliotheken-rechtsfrage/observation.md) (der Bau liest die Dateien aus [slice-release-lizenzen-lib-belegen](../in-progress/slice-release-lizenzen-lib-belegen.md)). Kein Zähler erreicht mit diesem Slice 3×, soweit er am Plan abzusehen ist.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
