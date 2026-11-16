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

**Ziel:** Für jede Bibliothek, die im Verzeichnis `lib/` des gebauten Artefakts liegt, steht im Repo ein Beleg (welche Lizenz, aus welcher Quelle die Lizenzdatei stammt samt Abrufdatum, welche Anforderungen die Lizenz an die Weitergabe stellt), und die Lizenzdateien selbst liegen byte-gleich zur belegten Quelle im Repo, damit [slice-release-dateien-bauen](../open/slice-release-dateien-bauen.md) sie ins ZIP legen kann. Das ist die Bedingung aus [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) Entscheidung 3: **ohne diesen Beleg kein Anhang von `lib/`**. Der Beleg ist eine Lesung der Lizenzen, **keine Rechtsauskunft**; offene Fragen an den Nutzer stehen im Beleg einzeln benannt.

**Warum ein eigener Slice, vor dem Bau:** Der Beleg ist Lesen und Ablegen von Texten, kein Werkzeug; er hat einen anderen Eingang (Quellen der Bibliotheken, Netz zum Abruf) als der Bau (Dockerfile, Skripte) und kann anders ausgehen: nennt eine Lizenz eine Bedingung, die das ZIP nicht erfüllen kann, ist das ein Re-Evaluierungs-Trigger von [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) und eine Entscheidung des Nutzers — dann darf der Bau nicht schon fertig sein. Der Bau-Slice bräuchte sonst echte Lizenzdateien, die es noch nicht gibt, oder ein rotes Target auf echten Daten. Der Nutzer hat L1 gewählt und den Beleg vor dem Anhang zur Bedingung gemacht ([ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) Entscheidung 3); dieser Slice erfüllt die Bedingung.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Das ZIP, das Target `make release-dateien`, die Stage im `Dockerfile`, die Prüfung, dass die Lizenzdatei im Archiv fehlt — [slice-release-dateien-bauen](../open/slice-release-dateien-bauen.md). Grund: Bauen ist Werkzeug mit Selbsttest; dieser Slice liefert nur die Eingabe dafür (Texte und Beleg) und fasst weder `Dockerfile` noch `tools/` an.
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

- [ ] **Beleg je Bibliothek in `lib/`, am gebauten Artefakt gemessen:** Die Liste von `lib/` stammt aus dem gebauten Artefakt (die `Class-Path`-Einträge des Manifests und die Dateien unter `build/libs/lib` im Container der Stage `build-jar`, am Lauf gelesen), nicht aus dem Lockfile allein; sie wird gegen `gradle.lockfile` gehalten (nach dem Lockfile erwartet, **nicht gebaut gelesen**: `kotlin-stdlib`, `kotlinx-coroutines-core-jvm`, `org.jetbrains:annotations`; jede Abweichung steht im Beleg). Je Eintrag: Name und Version der Datei, Lizenz (Name; eine SPDX-Kennung nur, wo die Quelle sie nennt), die Quelle der Lizenzdatei (im Jar selbst unter `META-INF/` mit Pfad, oder beim Projekt der Bibliothek mit URL) **mit Abrufdatum**, die Anforderungen der Lizenz an die Weitergabe (Lizenztext, Namensnennung, Hinweisdatei, Änderungsvermerk; wörtlich zitiert mit Fundstelle, kein Schluss aus dem Gedächtnis), und was nicht gelesen werden konnte. Ablage als Messprotokoll unter `docs/messungen/` (Datum, Host, Bedingung; [`AGENTS.md`](../../../../AGENTS.md) §5 Regel 7). Der Beleg beginnt mit dem Satz „Lesung, keine Rechtsauskunft“, nennt die Rechtsfrage aus [ADR-0018](../../adr/0018-laufzeit-artefakt.md) §Offen als bestehend und führt den Abschnitt **Offene Fragen an den Nutzer**; er sagt je Bibliothek, ob das ZIP die Anforderungen mit seinem Inhalt (Jar, `lib/`, `LICENSE`, die Lizenzdateien) erfüllen kann, und nennt jede Anforderung, die es nicht kann, als Befund.
- [ ] **Lizenzdateien im Repo abgelegt:** je Bibliothek in `lib/` die Lizenzdatei(en) (und eine Hinweisdatei, wenn die Lizenz sie verlangt), byte-gleich zur belegten Quelle, mit SHA-256 im Beleg. Ablageort und Namensform wählt der Implementer; Vorgabe: eine Datei oder ein Verzeichnis je Bibliothek, der Name trägt Gruppe, Artefakt und Version der Datei in `lib/`, und der Ablageort steht im Beleg und in §7, weil [slice-release-dateien-bauen](../open/slice-release-dateien-bauen.md) ihn als Eingang der Stage liest (neue `COPY`-Quelle, Eintrag in `tools/stempel/*.eingaenge` dort). Ändert sich `lib/` (Lockfile-Wechsel), bricht der Bau-Slice an der fehlenden Datei; wie der Beleg dann fortgeschrieben wird, steht in einem Satz im Beleg.

Nachweis-Pflichten für beide Punkte:

- [ ] `make gates` grün; für die Doku-Teile `make docs-check`, `make flaeche`, `make kopplung-zahlen` ([`harness/README.md`](../../../../harness/README.md) §Minimal agent workflow, Zeile „Doku oder Spec“). Der Slice berührt weder `src/` noch `testserver/` noch Werkzeug; `make clean-build` und `make e2e` sind nicht erforderlich, `make spec-abdeckung` entfällt (keine `SPEC-*`-Kennung neu oder entfernt).
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow ([`AGENTS.md`](../../../../AGENTS.md) §6), kein Self-Review (Modul 8). Der Review prüft vor allem, ob jede Aussage des Belegs auf eine gelesene Fundstelle zeigt ([`AGENTS.md`](../../../../AGENTS.md) §3.8) und ob die Ablage byte-gleich ist (Hash). Enthält die Nachbesserung Code oder Skripte, folgt ein zweiter Review in frischem Kontext (§3, Ablauf).
- [ ] Doku-Update, soweit die Beschreibung des Werkzeugs oder des Ablaufs sich ändert: im Register [`BEO-TDS/lizenzhinweise-der-bibliotheken-rechtsfrage`](../observations/BEO-TDS/lizenzhinweise-der-bibliotheken-rechtsfrage/observation.md) der Stand (Beleg liegt, Rechtsfrage offen, Träger dieser Slice); `releasing.md` und `harness/README.md` bleiben unverändert, bis [slice-release-dateien-bauen](../open/slice-release-dateien-bauen.md) den Zustand „gebaut“ herstellt (die Aussage in `releasing.md` Schritt 7, das Artefakt führe keine Hinweise zu den Bibliotheken, bleibt bis dahin wahr).
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben; **kein Zaehler wird gesetzt**. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

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

**Ablauf:** (1) Implementer liest `lib/` am gebauten Artefakt (ein Lauf der Stage `build-jar`, Ausgabe gesichert; kein Eingriff in `make build`), liest die Lizenzdateien in den Jars und beim Projekt und legt Beleg und Dateien ab; er rät keine Lizenz aus dem Namen. (2) Review in frischem Kontext gegen die DoD. (3) Verifier prüft gegen die DoD, öffnet jede Quelle erneut und vergleicht die Hashes, fährt `make gates` auf dem End-Stand. (4) Nichts wird veröffentlicht, kein Tag gesetzt, nichts gepusht.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): (1) [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) ist `Accepted` (erfüllt, 2026-11-16, in `main` gemergt). (2) Ein Verantwortlicher ist eingetragen. (3) Keine weitere Bedingung: der Slice schreibt nur ins Repo und liest Quellen im Netz.

Reihenfolge der Reihe (die Abhängigkeiten stehen in jedem Slice in §4): **[slice-release-lizenzen-lib-belegen](slice-release-lizenzen-lib-belegen.md) → [slice-release-dateien-bauen](../open/slice-release-dateien-bauen.md) → [slice-release-job-im-workflow-bauen](../open/slice-release-job-im-workflow-bauen.md) → [slice-release-im-tag-lauf-messen](../open/slice-release-im-tag-lauf-messen.md) → [slice-release-nachtraeglich-bestandstags](../open/slice-release-nachtraeglich-bestandstags.md)**. Dieser Slice ist der erste, weil [slice-release-dateien-bauen](../open/slice-release-dateien-bauen.md) seine Dateien als Eingang liest.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß): `lib/` führt deutlich mehr Bibliotheken als drei, oder eine Bibliothek führt mehrere Lizenzen; der Beleg wird zerlegt, bevor [slice-release-dateien-bauen](../open/slice-release-dateien-bauen.md) beginnt.
- `in-progress` → `open` (blockiert): der Beleg zeigt eine Anforderung, die das ZIP nicht erfüllen kann (zum Beispiel eine Hinweisdatei, die nicht in `lib/` oder neben `LICENSE` liegen kann), oder eine Lizenzdatei lässt sich nicht belegen. Das ist der Re-Evaluierungs-Trigger von [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) und eine **Entscheidung des Nutzers** (Archivinhalt, L2 oder L3); der Slice entscheidet sie nicht und liefert den Befund. Der Bau-Slice beginnt dann nicht.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Beobachtbar: (1) Das Messprotokoll liegt unter `docs/messungen/` mit einem Eintrag je Datei in `lib/` des gebauten Artefakts, jede Aussage mit Fundstelle oder als „nicht gelesen“ gekennzeichnet; die Lizenzdateien liegen im Repo, ihre SHA-256 im Beleg stimmen (der Review und der Verifier haben die Quellen erneut geöffnet). (2) Alle Punkte der DoD erledigt, `make gates` grün, Closure-Notiz mit Lerneintrag geschrieben. Die Closure-Notiz nennt den Ablageort der Lizenzdateien, die Liste von `lib/` mit Versionen und die **offenen Fragen an den Nutzer**, damit [slice-release-dateien-bauen](../open/slice-release-dateien-bauen.md) und [slice-release-im-tag-lauf-messen](../open/slice-release-im-tag-lauf-messen.md) sie aufnehmen.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Der Beleg liest Lizenztexte; ob die Weitergabe im ZIP rechtlich genügt, sagt er nicht ([ADR-0018](../../adr/0018-laufzeit-artefakt.md) §Offen, [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) Entscheidung 3). — **Ausgang:** offen bis zur Closure; erwartet „weiter offen: → [`BEO-TDS/lizenzhinweise-der-bibliotheken-rechtsfrage`](../observations/BEO-TDS/lizenzhinweise-der-bibliotheken-rechtsfrage/observation.md)“.
- Eine Bibliothek in `lib/` führt keine Lizenzdatei im Jar, und beim Projekt ist die Datei nicht zu einer Version zuzuordnen (Quelle und Version stimmen nicht überein). — **Ausgang:** offen bis zur Closure.
- Die Liste von `lib/` weicht vom Lockfile ab (ein Eintrag mehr oder weniger als erwartet). — **Ausgang:** offen bis zur Closure.
- Der Beleg altert still: ein Lockfile-Wechsel ändert `lib/`, und der Beleg bleibt stehen. — **Ausgang:** offen bis zur Closure; der Sensor ist die fehlende Datei im Bau von [slice-release-dateien-bauen](../open/slice-release-dateien-bauen.md), nicht dieser Slice.
- Abruf bei einem Projekt scheitert (Netz, Umzug der Quelle). — **Ausgang:** offen bis zur Closure; dann steht die Bibliothek als „nicht belegt“ im Beleg, und `lib/` wird nicht angehängt ([ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) Entscheidung 3).

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

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen; Treffer: [`BEO-TDS/lizenzhinweise-der-bibliotheken-rechtsfrage`](../observations/BEO-TDS/lizenzhinweise-der-bibliotheken-rechtsfrage/observation.md) (offen, Träger bisher keiner: dieser Slice liefert den Beleg, nicht die Rechtsauskunft; Zähler wird nicht gesetzt), `BEO-TDS/aussagen-ueber-messung-hinaus` (verkörpert in [`AGENTS.md`](../../../../AGENTS.md) §3.8; der Beleg trägt nur, was gelesen ist). Kein Zähler erreicht mit diesem Slice 3×, soweit er am Plan abzusehen ist.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
