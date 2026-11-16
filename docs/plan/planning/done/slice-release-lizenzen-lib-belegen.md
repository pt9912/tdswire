# Slice slice-release-lizenzen-lib-belegen: Lizenzen der Bibliotheken in `lib/` belegen und die Lizenzdateien im Repo ablegen (Beleg vor dem ersten Anhang)

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** ohne Welle — keine Closure-Bedingung, die von der DoD dieses Slice verschieden ist. Gewählt wie bei den Veröffentlichungs-Slices zu [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) und [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) (auch sie ohne Welle); [welle-referenzabgleich-release](../welle-referenzabgleich-release.md) führt diese Slices in ihrer Liste nicht, und [welle-qualitaet-grundlage](../welle-qualitaet-grundlage.md) trägt Qualitätsmessungen, kein Veröffentlichen. Offene Wellen und ihre Slice-Listen am 2026-11-16 gelesen.

**Bezug:** [TDS-RB-05](../../../../spec/lastenheft.md#tds-rb-05--lizenz) (Scope: Lizenz; Weitergabe fremder Binärdateien), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) (der Beleg nennt Quelle und Abrufdatum und ist wiederholbar lesbar), [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) (`Accepted` 2026-11-16, Entscheidung 3, Mess-Pflicht 8, Folgepflicht), [ADR-0018](../../adr/0018-laufzeit-artefakt.md) (§Offen: Lizenzhinweise der gelieferten Bibliotheken), [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) (Entscheidung 2: das Maven-Paket führt POM statt `lib/`).

**Berührte Spec-Stellen:** `SPEC-052` nur lesend (das Verzeichnis `lib` im `Class-Path`; die Spezifikation sagt zu Lizenzhinweisen der Bibliotheken nichts, am 2026-11-16 gelesen). Keine Spec-Änderung; [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) schärft keine Spec-Stelle.

**Verantwortlich:** pt9912.

**Autor:** Planner-Agent, Auftrag des Nutzers 2026-11-16 nach [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) (`Accepted` 2026-11-16, Folgepflicht). **Datum:** 2026-11-16.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Für jede Bibliothek, die im Verzeichnis `lib/` des gebauten Artefakts liegt, steht im Repo ein Beleg (welche Lizenz, aus welcher Quelle die Lizenzdatei stammt samt Abrufdatum, welche Anforderungen die Lizenz an die Weitergabe stellt), und die Lizenzdateien selbst liegen byte-gleich zur belegten Quelle im Repo, damit [slice-release-dateien-bauen](../done/slice-release-dateien-bauen.md) sie ins ZIP legen kann. Das ist die Bedingung aus [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) Entscheidung 3: **ohne diesen Beleg kein Anhang von `lib/`**. Der Beleg ist eine Lesung der Lizenzen, **keine Rechtsauskunft**; offene Fragen an den Nutzer stehen im Beleg einzeln benannt.

**Warum ein eigener Slice, vor dem Bau:** Der Beleg ist Lesen und Ablegen von Texten, kein Werkzeug; er hat einen anderen Eingang (Quellen der Bibliotheken, Netz zum Abruf) als der Bau (Dockerfile, Skripte) und kann anders ausgehen: nennt eine Lizenz eine Bedingung, die das ZIP nicht erfüllen kann, ist das ein Re-Evaluierungs-Trigger von [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) und eine Entscheidung des Nutzers — dann darf der Bau nicht schon fertig sein. Der Bau-Slice bräuchte sonst echte Lizenzdateien, die es noch nicht gibt, oder ein rotes Target auf echten Daten. Der Nutzer hat L1 gewählt und den Beleg vor dem Anhang zur Bedingung gemacht ([ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) Entscheidung 3); dieser Slice erfüllt die Bedingung.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Das ZIP, das Target `make release-dateien`, die Stage im `Dockerfile`, die Prüfung, dass die Lizenzdatei im Archiv fehlt — [slice-release-dateien-bauen](../done/slice-release-dateien-bauen.md). Grund: Bauen ist Werkzeug mit Selbsttest; dieser Slice liefert nur die Eingabe dafür (Texte und Beleg) und fasst weder `Dockerfile` noch `tools/` an.
- Eine Rechtsauskunft oder die Antwort auf die Rechtsfrage aus [ADR-0018](../../adr/0018-laufzeit-artefakt.md) §Offen. Grund: [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) Entscheidung 3 schließt es aus; der Beleg liest, er bewertet nicht. Die Fragen, die er offen lässt, gehen als Fragen an den Nutzer in den Bericht und in den Beleg.
- Lizenzhinweise für das **Maven-Paket** (POM führt MIT und die Abhängigkeiten, die Bibliotheken liegen nicht im Paket, [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md)). Grund: das Paket gibt keine fremden Binärdateien weiter; Gegenstand ist allein das ZIP mit `lib/`.
- Lizenzen von Testbibliotheken, des Testservers oder der Werkzeuge im Container. Grund: sie liegen weder in `lib/` noch im Jar.
- Eine Änderung von `LICENSE`, `README.md` oder `docs/user/`. Grund: der Slice behauptet nichts über das Projekt, nur über die gelieferten Bibliotheken; eine Namensnennung im README wäre eine Folge, die erst der Beleg verlangen könnte (dann eigener Punkt im Bericht).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (zwei):

- [x] **Beleg je Bibliothek in `lib/`, am gebauten Artefakt gemessen:** Die Liste von `lib/` stammt aus dem gebauten Artefakt (die `Class-Path`-Einträge des Manifests und die Dateien unter `build/libs/lib` im Container der Stage `build-jar`, am Lauf gelesen), nicht aus dem Lockfile allein; sie wird gegen `gradle.lockfile` gehalten (nach dem Lockfile erwartet, **nicht gebaut gelesen**: `kotlin-stdlib`, `kotlinx-coroutines-core-jvm`, `org.jetbrains:annotations`; jede Abweichung steht im Beleg). Je Eintrag: Name und Version der Datei, Lizenz (Name; eine SPDX-Kennung nur, wo die Quelle sie nennt), die Quelle der Lizenzdatei (im Jar selbst unter `META-INF/` mit Pfad, oder beim Projekt der Bibliothek mit URL) **mit Abrufdatum**, die Anforderungen der Lizenz an die Weitergabe (Lizenztext, Namensnennung, Hinweisdatei, Änderungsvermerk; wörtlich zitiert mit Fundstelle, kein Schluss aus dem Gedächtnis), und was nicht gelesen werden konnte. Ablage als Messprotokoll unter `docs/messungen/` (Datum, Host, Bedingung; [`AGENTS.md`](../../../../AGENTS.md) §5 Regel 7). Der Beleg beginnt mit dem Satz „Lesung, keine Rechtsauskunft“, nennt die Rechtsfrage aus [ADR-0018](../../adr/0018-laufzeit-artefakt.md) §Offen als bestehend und führt den Abschnitt **Offene Fragen an den Nutzer**; er sagt je Bibliothek, ob das ZIP die Anforderungen mit seinem Inhalt (Jar, `lib/`, `LICENSE`, die Lizenzdateien) erfüllen kann, und nennt jede Anforderung, die es nicht kann, als Befund.
- [x] **Lizenzdateien im Repo abgelegt:** je Bibliothek in `lib/` die Lizenzdatei(en) (und eine Hinweisdatei, wenn die Lizenz sie verlangt), byte-gleich zur belegten Quelle, mit SHA-256 im Beleg. Ablageort und Namensform wählt der Implementer; Vorgabe: eine Datei oder ein Verzeichnis je Bibliothek, der Name trägt Gruppe, Artefakt und Version der Datei in `lib/`, und der Ablageort steht im Beleg und in §7, weil [slice-release-dateien-bauen](../done/slice-release-dateien-bauen.md) ihn als Eingang der Stage liest (neue `COPY`-Quelle, Eintrag in `tools/stempel/*.eingaenge` dort). Ändert sich `lib/` (Lockfile-Wechsel), bricht der Bau-Slice an der fehlenden Datei; wie der Beleg dann fortgeschrieben wird, steht in einem Satz im Beleg.

Nachweis-Pflichten für beide Punkte:

- [x] `make gates` grün; für die Doku-Teile `make docs-check`, `make flaeche`, `make kopplung-zahlen` ([`harness/README.md`](../../../../harness/README.md) §Minimal agent workflow, Zeile „Doku oder Spec“). Der Slice berührt weder `src/` noch `testserver/` noch Werkzeug; `make clean-build` und `make e2e` sind nicht erforderlich, `make spec-abdeckung` entfällt (keine `SPEC-*`-Kennung neu oder entfernt).
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow ([`AGENTS.md`](../../../../AGENTS.md) §6), kein Self-Review (Modul 8). Der Review prüft vor allem, ob jede Aussage des Belegs auf eine gelesene Fundstelle zeigt ([`AGENTS.md`](../../../../AGENTS.md) §3.8) und ob die Ablage byte-gleich ist (Hash). Enthält die Nachbesserung Code oder Skripte, folgt ein zweiter Review in frischem Kontext (§3, Ablauf).
- [x] Doku-Update, soweit die Beschreibung des Werkzeugs oder des Ablaufs sich ändert: im Register [`BEO-TDS/lizenzhinweise-der-bibliotheken-rechtsfrage`](../observations/BEO-TDS/lizenzhinweise-der-bibliotheken-rechtsfrage/observation.md) der Stand (Beleg liegt, Rechtsfrage offen, Träger dieser Slice); `releasing.md` und `harness/README.md` bleiben unverändert, bis [slice-release-dateien-bauen](../done/slice-release-dateien-bauen.md) den Zustand „gebaut“ herstellt (die Aussage in `releasing.md` Schritt 7, das Artefakt führe keine Hinweise zu den Bibliotheken, bleibt bis dahin wahr).
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben; **kein Zaehler wird gesetzt**. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `docs/messungen/` (neues Messprotokoll zu den Lizenzen von `lib/`) | neu | erster Punkt der DoD; Datum, Host, Bedingung, Quellen mit Abrufdatum, offene Fragen |
| Ablage der Lizenzdateien (Ort wählt der Implementer, siehe DoD) | neu | zweiter Punkt der DoD; Eingang des Bau-Slice |
| `docs/plan/planning/observations/BEO-TDS/lizenzhinweise-der-bibliotheken-rechtsfrage/` | update | `state.md` und eine Datei in `evidence/`; kein Zähler |
| `docs/plan/planning/done/slice-release-lizenzen-lib-belegen.md` (dieser Slice, §7) | update | der Ablageort der Lizenzdateien steht in §7 (DoD zweiter Punkt); sonst keine Änderung am Plan |
| `docs/plan/planning/observations/BEO-TDS/lizenzhinweise-der-bibliotheken-rechtsfrage/observation.md` | update | die Aufzählung der gelieferten Bibliotheken nennt die dritte (annotations), nachgezogen nach Review F-5; Verifikation F-1 |
| `docs/reviews/` (Review, Re-Review, Verifikation zu diesem Slice) | neu | Rollen-Berichte, keine Lieferung des Slice |

**Ablauf:** (1) Implementer liest `lib/` am gebauten Artefakt (ein Lauf der Stage `build-jar`, Ausgabe gesichert; kein Eingriff in `make build`), liest die Lizenzdateien in den Jars und beim Projekt und legt Beleg und Dateien ab; er rät keine Lizenz aus dem Namen. (2) Review in frischem Kontext gegen die DoD. (3) Verifier prüft gegen die DoD, öffnet jede Quelle erneut und vergleicht die Hashes, fährt `make gates` auf dem End-Stand. (4) Nichts wird veröffentlicht, kein Tag gesetzt, nichts gepusht.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): (1) [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) ist `Accepted` (erfüllt, 2026-11-16, in `main` gemergt). (2) Ein Verantwortlicher ist eingetragen. (3) Keine weitere Bedingung: der Slice schreibt nur ins Repo und liest Quellen im Netz.

Reihenfolge der Reihe (die Abhängigkeiten stehen in jedem Slice in §4): **[slice-release-lizenzen-lib-belegen](slice-release-lizenzen-lib-belegen.md) → [slice-release-dateien-bauen](../done/slice-release-dateien-bauen.md) → [slice-release-job-im-workflow-bauen](../in-progress/slice-release-job-im-workflow-bauen.md) → [slice-release-im-tag-lauf-messen](../open/slice-release-im-tag-lauf-messen.md) → [slice-release-nachtraeglich-bestandstags](../open/slice-release-nachtraeglich-bestandstags.md)**. Dieser Slice ist der erste, weil [slice-release-dateien-bauen](../done/slice-release-dateien-bauen.md) seine Dateien als Eingang liest.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß): `lib/` führt deutlich mehr Bibliotheken als drei, oder eine Bibliothek führt mehrere Lizenzen; der Beleg wird zerlegt, bevor [slice-release-dateien-bauen](../done/slice-release-dateien-bauen.md) beginnt.
- `in-progress` → `open` (blockiert): der Beleg zeigt eine Anforderung, die das ZIP nicht erfüllen kann (zum Beispiel eine Hinweisdatei, die nicht in `lib/` oder neben `LICENSE` liegen kann), oder eine Lizenzdatei lässt sich nicht belegen. Das ist der Re-Evaluierungs-Trigger von [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) und eine **Entscheidung des Nutzers** (Archivinhalt, L2 oder L3); der Slice entscheidet sie nicht und liefert den Befund. Der Bau-Slice beginnt dann nicht.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Beobachtbar: (1) Das Messprotokoll liegt unter `docs/messungen/` mit einem Eintrag je Datei in `lib/` des gebauten Artefakts, jede Aussage mit Fundstelle oder als „nicht gelesen“ gekennzeichnet; die Lizenzdateien liegen im Repo, ihre SHA-256 im Beleg stimmen (der Review und der Verifier haben die Quellen erneut geöffnet). (2) Alle Punkte der DoD erledigt, `make gates` grün, Closure-Notiz mit Lerneintrag geschrieben. Die Closure-Notiz nennt den Ablageort der Lizenzdateien, die Liste von `lib/` mit Versionen und die **offenen Fragen an den Nutzer**, damit [slice-release-dateien-bauen](../done/slice-release-dateien-bauen.md) und [slice-release-im-tag-lauf-messen](../open/slice-release-im-tag-lauf-messen.md) sie aufnehmen.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Der Beleg liest Lizenztexte; ob die Weitergabe im ZIP rechtlich genügt, sagt er nicht ([ADR-0018](../../adr/0018-laufzeit-artefakt.md) §Offen, [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) Entscheidung 3). — **Ausgang:** weiter offen → [`BEO-TDS/lizenzhinweise-der-bibliotheken-rechtsfrage`](../observations/BEO-TDS/lizenzhinweise-der-bibliotheken-rechtsfrage/observation.md), Träger der Nutzer (offene Fragen 1 und 2d im Beleg).
- Eine Bibliothek in `lib/` führt keine Lizenzdatei im Jar, und beim Projekt ist die Datei nicht zu einer Version zuzuordnen (Quelle und Version stimmen nicht überein). — **Ausgang:** eingetreten für den ersten Teil (kein Jar führt eine Lizenzdatei, Befund 1 im Beleg); der zweite Teil ist nicht eingetreten, die Dateien liegen am Tag der Version (Quelle je Datei im Beleg).
- Die Liste von `lib/` weicht vom Lockfile ab (ein Eintrag mehr oder weniger als erwartet). — **Ausgang:** entfallen: dieselben drei Dateien, keine Abweichung (Beleg, vom Verifier am Docker-Bau wiederholt).
- Der Beleg altert still: ein Lockfile-Wechsel ändert `lib/`, und der Beleg bleibt stehen. — **Ausgang:** weiter offen, Träger [slice-release-dateien-bauen](../done/slice-release-dateien-bauen.md): der Sensor ist die Prüfung im Bau, nicht dieser Slice; sie fängt eine zu wenig abgelegte NOTICE nur mit der Sollmenge der sieben Dateien (Verifikation F-2, im Plan dort aufgenommen).
- Abruf bei einem Projekt scheitert (Netz, Umzug der Quelle). — **Ausgang:** entfallen: jeder Abruf gelang (ein erster Fehlversuch der NOTICE von kotlinx-coroutines lag am Suchpfad, nicht am Netz); keine Bibliothek steht als „nicht belegt“ da.

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

Geschlossen 2026-11-16 (Planner, Slice-Closure ohne Welle). Geprüfter Stand: Zweig `slice-release-lizenzen-lib-belegen`, HEAD `456df041` (Basis `main` `74eb8ffe`). Prüfung: Verifikationsbericht [2026-11-16-456df041](../../../reviews/2026-11-16-456df041-slice-release-lizenzen-lib-belegen-verifikation.md), Review [2026-11-16-7559de60](../../../reviews/2026-11-16-7559de60-slice-release-lizenzen-lib-belegen-review.md) (ein MEDIUM, vier LOW, zwei INFO) und Re-Review [2026-11-16-be26c4af](../../../reviews/2026-11-16-be26c4af-slice-release-lizenzen-lib-belegen-re-review.md) (zwei LOW, zwei INFO), Beleg [Messprotokoll](../../../messungen/lizenzen-lib-2026-11-16.md).

Ablageort der Lizenzdateien (Eingang für [slice-release-dateien-bauen](../done/slice-release-dateien-bauen.md)): `lizenzen/lib/<Gruppe>-<Artefakt>-<Version>/`, Beleg: [Messprotokoll](../../../messungen/lizenzen-lib-2026-11-16.md). Sieben Dateien in drei Verzeichnissen:

| Verzeichnis unter `lizenzen/lib/` | Dateien |
|---|---|
| `org.jetbrains.kotlin-kotlin-stdlib-2.4.10` | `LICENSE.txt`, `NOTICE.txt`, `third_party/boost_LICENSE.txt`, `third_party/threetenbp_license.txt` |
| `org.jetbrains.kotlinx-kotlinx-coroutines-core-jvm-1.10.1` | `LICENSE.txt`, `NOTICE.txt` |
| `org.jetbrains-annotations-23.0.0` | `LICENSE.txt` |

**Liste von `lib/`** (am gebauten Artefakt gelesen, Manifest `Class-Path` und `build/libs/lib`; vom Verifier am Docker-Bau mit denselben SHA-256 wiederholt): `kotlin-stdlib-2.4.10.jar`, `kotlinx-coroutines-core-jvm-1.10.1.jar`, `annotations-23.0.0.jar`; gleich den drei Koordinaten des Lockfiles, keine Abweichung. Jede der drei ist Apache License 2.0 (POM-Angabe); keines der drei Jars führt eine Lizenz- oder Hinweisdatei, die Dateien stammen von den Tags der Projekte (Quelle und Abrufdatum je Datei im Beleg).

**Geliefert.** (1) Der Beleg liegt als Messprotokoll mit Lizenz, Quelle samt Abrufdatum, wörtlich zitierten Anforderungen von Abschnitt 4 der Lizenz und „Nicht gelesen“ je Bibliothek; er beginnt mit „Lesung, keine Rechtsauskunft“. (2) Die sieben Lizenz- und Hinweisdateien liegen byte-gleich zur Quelle im Repo; Review und Verifier haben die Quellen erneut abgerufen und die Hashes verglichen. (3) Drei Befunde im Beleg: kein Jar führt eine Lizenzdatei; die Standardbibliothek führt Dritt-Code (BSD, Boost, GWT und Guava unter Apache 2); die gelesenen Anforderungen verlangen nichts, was das ZIP nach seinem geplanten Inhalt nicht tragen kann (Lesung, keine Auskunft, ob das genügt). Der Rückführungs-Fall „blockiert“ ([ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) Re-Evaluierungs-Trigger) ist damit nicht eingetreten; der Bau-Slice darf beginnen, soweit der Nutzer die Fragen unten nicht anders beantwortet.

**Was anders lief als geplant.** Der erste Stand des Belegs sagte für kotlinx-coroutines „keine NOTICE“, nachdem nur die Wurzel des Tags abgefragt war; das Verzeichnis `license/` führt `license/NOTICE.txt` (Review F-1, MEDIUM). Die Datei ist nachträglich abgelegt (Anzahl der Dateien von sechs auf sieben) und der Beleg berichtigt. Der Plan nannte in §3 weder den eigenen Slice (§7) noch die `observation.md` des Registers (Review F-4, Verifikation F-1); §3 ist nachgezogen.

**Offene Fragen an den Nutzer.** Stand: offen, Träger: Nutzer. Eingang für [slice-release-im-tag-lauf-messen](../open/slice-release-im-tag-lauf-messen.md) (Start-Bedingung 5: der Nutzer hat den Beleg gelesen und die Fragen beantwortet oder bewusst offen gelassen) und für [slice-release-dateien-bauen](../done/slice-release-dateien-bauen.md) (Inhalt des ZIP). Der Wortlaut steht im Beleg, Abschnitt „Offene Fragen an den Nutzer“; hier die Kurzform mit einem **Vorschlag des Planners** je Frage (kein Beleg, keine Entscheidung; die Rechtsfragen beantwortet kein Agent):

1. Genügt die Beilage der Lizenzdateien für die Weitergabe der drei Jars, oder soll die Rechtsfrage aus [ADR-0018](../../adr/0018-laufzeit-artefakt.md) §Offen vorher durch Dritte beantwortet werden (L3 aus [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md))? Vorschlag: vor dem Start von `slice-release-im-tag-lauf-messen` entscheiden, weil der erste Anhang von `lib/` sie voraussetzt.
2. Dritt-Lizenzen und Hinweisdateien im ZIP: 2a BSD und Boost der Standardbibliothek ins ZIP; 2b die NOTICE des Kotlin-Projekts (Zuordnung zur Standardbibliothek nicht belegt) und die NOTICE von kotlinx-coroutines ins ZIP; 2c in welcher Form (eine Datei oder das Verzeichnis `lizenzen/lib` im Archiv); 2d ob die Boost-Ausnahme „machine-executable object code“ ein Jar umfasst. Vorschlag: 2a und 2b ja (die Dateien sind abgelegt, mitführen kostet nichts), 2c das Verzeichnis unverändert (Herkunft je Bibliothek bleibt sichtbar), 2d bleibt Rechtsfrage und hängt an Frage 1.
3. Die Lizenzdatei von annotations 23.0.0 nennt „Copyright 2000-2016“, die Quell-Dateien „2000-2020/2021“; die Datei liegt unverändert. Vorschlag: nicht nachfragen, die Datei bleibt byte-gleich zur Quelle (eine Änderung wäre ein Eingriff in den Text des Projekts).

**Nachbesserung ohne zweiten Review: kein Fall der Regel.** Die Nachbesserungen `cef5ef0e` und `be26c4af` (nach dem Review) änderten Messprotokoll, Register, Slice-Plan und eine Lizenz-Datendatei, `456df041` (nach dem Re-Review, N-1 bis N-4) nur das Messprotokoll und den Re-Review-Bericht; kein Quelltext, kein Skript, keine Make- oder Build-Datei. Die ersten beiden hat der Re-Review gelesen, `456df041` kein dritter Review, der Verifier hat den Stand gelesen und nach der Skill-Regel keinen zweiten Review verlangt (Verifikation, DoD-Zeile 4). [`BEO-TDS/nachbesserung-ohne-zweiten-review`](../observations/BEO-TDS/nachbesserung-ohne-zweiten-review/observation.md) wird deshalb nicht fortgeschrieben; die Regel gilt für Code, Skripte und Konfiguration.

**Risiken (Ausgänge in §6).** Weiter offen mit Träger: Rechtsfrage (Nutzer, Register), Altern des Belegs (Träger [slice-release-dateien-bauen](../done/slice-release-dateien-bauen.md), siehe Sensor-Lücke unten). Eingetreten: keine Lizenzdatei im Jar. Entfallen: Abweichung vom Lockfile, gescheiterter Abruf.

**Register** (`../observations/`). Fortgeschrieben, kein Zähler gesetzt: [`BEO-TDS/lizenzhinweise-der-bibliotheken-rechtsfrage`](../observations/BEO-TDS/lizenzhinweise-der-bibliotheken-rechtsfrage/observation.md) (Stand `offen`, Belegdatei `evidence/slice-release-lizenzen-lib-belegen.md`; zwei Dateien in `evidence/`, unter der Schwelle). Dieselben Klassen, hier als Auftreten verzeichnet (je `evidence/slice-release-lizenzen-lib-belegen.md`): `BEO-TDS/aussagen-ueber-messung-hinaus` (Review F-1, F-3; Re-Review N-1, N-2), `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` (Review F-5), `BEO-TDS/plan-tabelle-nennt-neue-datei-nicht` (Review F-4, Verifikation F-1), `BEO-TDS/zusage-ohne-sensor-in-der-ci-kette` (Verifikation F-2). **Lese-Schritt:** 44 Einträge führen drei oder mehr Dateien in `evidence/` (24 `verkörpert`, 1 `gestrichen`, 12 `offen`, 2 `geplant`, je 1 `teilweise`, `gebaut`, `geliefert`, 2 ohne Stand-Wort; gezählt aus den Verzeichnissen vor dieser Closure). Diese Closure ändert keinen Stand; die über der Schwelle liegenden und dort nicht verkörperten Einträge tragen ihre Entscheidung beim Nutzer oder in den Welle-Closures, nicht in diesem Slice. Mit diesem Slice erreicht kein Eintrag erstmals 3×. Wiederkehr nach Verkörperung (Retirement-Check, je in der `state.md` vermerkt): `fortschreibung-laesst-zweite-fundstelle-stehen`, `plan-tabelle-nennt-neue-datei-nicht`, `aussagen-ueber-messung-hinaus`; jedes Mal fing der Review die Klasse, die Regeln bleiben.

**Folge-Slices:** [slice-release-dateien-bauen](../done/slice-release-dateien-bauen.md) (Eingang: `lizenzen/lib/`, Sollmenge sieben Dateien) und [slice-release-im-tag-lauf-messen](../open/slice-release-im-tag-lauf-messen.md) (Start-Bedingung 5: die offenen Fragen). Beide liegen in `open/`; die Reihe steht in §4.

**Lerneintrag (Steering Loop).**

- **Neuer Sensor, geplant, nichts verkörpert (Verifikation F-2):** Im Repo meldet nichts, dass ein Verzeichnis unter `lizenzen/lib/` eine Datei weniger führt als der Beleg (`make gates` fasst `lizenzen/` nicht an); eine Prüfung „je Jar eine Lizenzdatei“ meldet eine fehlende NOTICE von kotlinx-coroutines nicht, die LICENSE wäre vorhanden. Der Träger ist der Plan von [slice-release-dateien-bauen](../done/slice-release-dateien-bauen.md): die Sollmenge der sieben Dateien und eine Mutation „NOTICE entfernt → rot“ stehen dort jetzt in DoD und Nachweis (Planpflege, kein Bau). Kein `liegt in`, weil keine Regel und kein Gate geschrieben ist; verkörpert ist der Sensor erst mit dem Bau. Auslöser: `BEO-TDS/zusage-ohne-sensor-in-der-ci-kette` (7×, `offen`, Entscheidung beim Nutzer).
- **Geschärft, keine neue Regel:** (i) Eine Verneinung („keine NOTICE“) trägt nur so weit wie der gelesene Suchraum; der erste Stand suchte an der Wurzel und übersah `license/` (`AGENTS.md` §3.8, verkörpert; die Klasse fing der Review, nicht der Implementer). (ii) Ein Tag ist nicht unveränderlich, und eine Streichung aus „Nicht belegt“ trägt nur so weit, wie gelesen wurde (Re-Review N-1, N-2; dieselbe Regel). (iii) Eine Fortschreibung sucht die Kernaussage im ganzen Repo: `state.md` war nachgezogen, `observation.md` nannte weiter zwei Bibliotheken (`BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`, Reviewer-Skill und Schritt 20c von `implement-slice`; der Re-Review fand nach der Behebung bei seinem Grep keine zweite Fundstelle mehr). (iv) Die Datei-Tabelle §3 nennt `observation.md` und den eigenen Slice nicht (`BEO-TDS/plan-tabelle-nennt-neue-datei-nicht`, verkörpert im Reviewer-Skill; Review und Verifikation fingen es).
- **Benannte Spec-Lücke:** Die Spezifikation sagt nichts zu Lizenzhinweisen der Bibliotheken ([`SPEC-052`](../../../../spec/spezifikation.md) nur lesend, am 2026-11-16 gelesen); [TDS-RB-05](../../../../spec/lastenheft.md#tds-rb-05--lizenz) verlangt die Lizenz des Projekts. Die Rechtsfrage ([ADR-0018](../../adr/0018-laufzeit-artefakt.md) §Offen) ist weder beantwortet noch in eine Spec-Stelle überführt; Träger: der Nutzer, Register `BEO-TDS/lizenzhinweise-der-bibliotheken-rechtsfrage`. Kein `liegt in`, nichts verkörpert, daher kein Herkunfts-Anker.
- **Grenze des Belegs:** Er altert still mit dem Lockfile: ein Wechsel von `lib/` ändert das Verzeichnis nicht; der Sensor ist erst die Prüfung im Bau von [slice-release-dateien-bauen](../done/slice-release-dateien-bauen.md) und fängt eine neue Bibliothek nur, wenn er die Sollmenge je Version prüft (im Plan dort aufgenommen). Nicht gelesen und im Beleg benannt: Unterverzeichnisse der Tags, Kopf-Kommentare jeder Quelldatei, die Klasse-zu-Quelle-Zuordnung.
- **Drei Paarungen:** Anker: kein Eintrag trägt `liegt in`, also nichts zu paaren. Folge-Slice: beide genannten Slices liegen als Datei in `open/`. Register: die fünf genannten `BEO-TDS/<slug>` existieren als Verzeichnisse mit nicht leerem `evidence/`.

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

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen; Treffer: [`BEO-TDS/lizenzhinweise-der-bibliotheken-rechtsfrage`](../observations/BEO-TDS/lizenzhinweise-der-bibliotheken-rechtsfrage/observation.md) (offen, Träger bisher keiner: dieser Slice liefert den Beleg, nicht die Rechtsauskunft; Zähler wird nicht gesetzt), `BEO-TDS/aussagen-ueber-messung-hinaus` (verkörpert in [`AGENTS.md`](../../../../AGENTS.md) §3.8; der Beleg trägt nur, was gelesen ist). Kein Zähler erreicht mit diesem Slice 3×, soweit er am Plan abzusehen ist.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
