# Slice slice-release-nachtraeglich-bestandstags: Releases für die bestehenden Tags `v0.1.0` und `v0.1.1` nachträglich anlegen, ohne Anhänge und mit Hinweis

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** ohne Welle — keine Closure-Bedingung, die von der DoD dieses Slice verschieden ist. Gewählt wie bei den Veröffentlichungs-Slices zu [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) und [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) (auch sie ohne Welle); [welle-referenzabgleich-release](../welle-referenzabgleich-release.md) führt diese Slices in ihrer Liste nicht, und [welle-qualitaet-grundlage](../welle-qualitaet-grundlage.md) trägt Qualitätsmessungen, kein Veröffentlichen. Offene Wellen und ihre Slice-Listen am 2026-11-16 gelesen.

**Bezug:** [TDS-RB-05](../../../../spec/lastenheft.md#tds-rb-05--lizenz) (Scope), [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) (`Accepted` 2026-11-16, Entscheidung 5 und Aufgelöste Fragen 5 und 6: Setzung nach Empfehlung, Folgepflicht), [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) (Entscheidung 3: veröffentlichte Versionen bleiben unverändert), [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) (`0.1.1` auch bei Cloudsmith).

**Berührte Spec-Stellen:** — (`spec/` nennt weder Veröffentlichung noch Release).

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

**Ziel:** Die Release-Seite des Repositories zeigt zu den bestehenden Tags `v0.1.0` und `v0.1.1` je ein Release mit Titel `0.1.0` bzw. `0.1.1`, **ohne Anhänge**, mit der festen Notiz aus der Vorlage ([slice-release-job-im-workflow-bauen](../in-progress/slice-release-job-im-workflow-bauen.md)) in der Variante ohne Dateizeilen und dem Satz, dass für diese Version keine Dateien anliegen und die Version in GitHub Packages (für `0.1.1` auch in Cloudsmith) steht. Es entsteht keine Version, kein Tag und kein Paket; nur die Seite zu einer vorhandenen Version.

**Warum ohne Anhänge (Setzung der ADR, hier nicht neu entschieden):** Die Jars der alten Tags müssten neu gebaut werden; ob sie den damals veröffentlichten Paket-Jars gleichen, ist nicht gemessen (Mess-Pflicht 4 bei [slice-release-im-tag-lauf-messen](slice-release-im-tag-lauf-messen.md)), und ein neu gebautes Jar an einer alten Version wäre eine Behauptung ohne Beleg. Nachträgliche Anhänge gibt es nicht, auch wenn Mess-Pflicht 4 gleiche Bytes belegt ([ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) Entscheidung 5).

**Warum ein eigener Slice, nach der ersten Lieferung:** Form und Notiz sollen dem neuen Format entsprechen ([ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) Entscheidung 5, N3), und das Anlegen hat Außenwirkung auf der öffentlichen Seite und läuft unter ausdrücklicher Bestätigung des Nutzers; es ist eine Handlung des Nutzers oder des Maintainers, kein Workflow-Lauf. Das trennt es vom Lauf in [slice-release-im-tag-lauf-messen](slice-release-im-tag-lauf-messen.md), der eine Version verbraucht.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Anhänge, ZIP, `SHA256SUMS`, `lib/` für die alten Tags: Setzung der [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) Entscheidung 5, aus dem oben genannten Grund.
- Ein neuer Tag, eine neue Version, ein Paket, ein Release-Commit: das Anlegen ist `gh release create <Tag> --verify-tag` auf den vorhandenen Tag, `--verify-tag` verhindert, dass ein Tag neu entsteht. Grund: die Versionen sind veröffentlicht und unveränderlich ([ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) Entscheidung 3).
- Änderung der Notiz-Vorlage, des Skripts oder des Workflows: [slice-release-job-im-workflow-bauen](../in-progress/slice-release-job-im-workflow-bauen.md); ein Fehler dort ist eine Nachbesserung dort. Grund: dieser Slice wendet nur an.
- Messungen am Workflow (Mess-Pflichten 1 bis 4, 6, 7): [slice-release-im-tag-lauf-messen](slice-release-im-tag-lauf-messen.md). Grund: kein Workflow-Lauf; beobachtet wird allein die Seite.
- Löschen oder Ändern eines vorhandenen Releases: kein Weg des Plans (kein `--clobber`, kein `delete`). Grund: [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) Entscheidung 1.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (zwei):

- [ ] **Beide Releases angelegt:** je ein Release zu `v0.1.0` und `v0.1.1` über den Modus ohne Dateien des Eingangsskripts aus [slice-release-job-im-workflow-bauen](../in-progress/slice-release-job-im-workflow-bauen.md) (`gh release create <Tag> --verify-tag`, Titel `0.1.0` bzw. `0.1.1`), Notiz aus der Vorlage ohne Dateizeilen, mit dem Hinweis; Commit und Pakete der Notiz stammen aus dem Tag und den Messprotokollen ([Messprotokoll 0.1.1](../../../messungen/veroeffentlichung-0-1-1-2026-11-15.md), [Messprotokoll 0.1.0](../../../messungen/veroeffentlichung-tag-lauf-2026-11-15.md)), kein Link ohne belegtes Ziel. Der Aufruf ist die Handlung des Nutzers (oder, auf seine ausdrückliche Bestätigung, des Implementers); jede der beiden Seiten braucht die Bestätigung, bevor sie entsteht.
- [ ] **Beleg an der Seite, nicht am Aufruf:** `gh release view` (oder die API) zeigt je Release Tag, Titel, Notiz, **keine** Assets; die SHA beider Tags (`git rev-parse v0.1.0 v0.1.1` und am Remote) ist vor und nach dem Anlegen gleich; ein Messprotokoll unter `docs/messungen/` hält Datum, Aufrufe (ohne Token) und die Ausgaben fest.

Nachweis-Pflichten für beide Punkte:

- [ ] `make gates` grün; für die Doku-Teile `make docs-check`, `make flaeche`, `make kopplung-zahlen` ([`harness/README.md`](../../../../harness/README.md) §Minimal agent workflow, Zeile „Doku oder Spec“). Der Slice ändert nur Doku und Protokoll; `make clean-build` und `make e2e` sind nicht erforderlich.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow ([`AGENTS.md`](../../../../AGENTS.md) §6), kein Self-Review (Modul 8). Der Review prüft, dass keine Aussage der Notiz über das Gemessene hinausgeht und dass kein Tag bewegt wurde. Enthält die Nachbesserung Code oder Skripte, folgt ein zweiter Review in frischem Kontext (§3, Ablauf).
- [ ] Doku-Update, soweit die Beschreibung des Werkzeugs oder des Ablaufs sich ändert: [`releasing.md`](../../../maintainer/releasing.md) (Stand der Release-Seiten der beiden ersten Versionen als Zustandsaussage, Anleitung, keine Chronik, [`AGENTS.md`](../../../../AGENTS.md) §5 Regel 5) und, falls der Abschnitt „Offene Entscheidungen“ noch einen Satz zum Weg ohne Registry führt, dessen Nachzug; [`harness/README.md`](../../../../harness/README.md) nur, wenn eine Zeile den Zustand nennt.
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
| `docs/messungen/` (kurzes Messprotokoll zu den zwei Seiten) | neu | zweiter Punkt der DoD |
| `docs/maintainer/releasing.md` | update | Zustand der Release-Seiten, soweit die Anleitung ihn nennt |

**Ablauf:** (1) Implementer bereitet die beiden Aufrufe vor (Modus ohne Dateien, Probelauf am Stub ist schon in [slice-release-job-im-workflow-bauen](../in-progress/slice-release-job-im-workflow-bauen.md) belegt) und nennt dem Nutzer Tag, Titel, Notiz je Version zur Bestätigung. (2) Nach der Bestätigung legt der Nutzer die beiden Seiten selbst an (`gh release create`; Entscheidung des Nutzers 2026-11-16); die Ausgabe wird gesichert. (3) Review in frischem Kontext, danach Verifier: liest die Seiten über die API, vergleicht die Tag-SHA. (4) Das Skript, die Vorlage und der Workflow bleiben unverändert.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): (1) [slice-release-im-tag-lauf-messen](slice-release-im-tag-lauf-messen.md) liegt in `done/` — die erste Lieferung des neuen Formats mit Release ist belegt, die Notiz-Vorlage hat sich am echten Lauf bewährt ([ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) Entscheidung 5). (2) Der Nutzer hat ausdrücklich bestätigt, dass die zwei Seiten jetzt entstehen sollen (Außenwirkung auf der öffentlichen Seite). (3) Ein Verantwortlicher ist eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß): nicht zu erwarten (zwei Aufrufe, ein Protokoll); zeigt [slice-release-im-tag-lauf-messen](slice-release-im-tag-lauf-messen.md), dass die Vorlage zu ändern ist, wird das ein Slice vor diesem.
- `in-progress` → `open` (blockiert): die Bestätigung des Nutzers fehlt oder wird zurückgenommen; die Seite eines Tags besteht schon (dann kein Überschreiben, Entscheidung des Nutzers); `--verify-tag` weist den Tag ab.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Beobachtbar: (1) Beide Seiten sind angelegt, ohne Assets, Tag-SHAs unverändert (Ausgaben im Messprotokoll, vom Verifier erneut gelesen). (2) Alle Punkte der DoD erledigt, `make gates` grün, Closure-Notiz mit Lerneintrag geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **Außenwirkung auf der öffentlichen Seite ohne Rücknahme im Plan:** das Löschen eines Releases ist kein Weg des Plans; eine falsche Notiz wäre von Hand zu korrigieren (Entscheidung des Nutzers). — **Ausgang:** offen bis zur Closure.
- Die Notiz nennt Pakete und Links aus den Messprotokollen; ob die Links am Tag der Anlage auflösen, ist nicht zugesagt (GitHub Packages ist nur mit Token lesbar, nicht gemessen, [`releasing.md`](../../../maintainer/releasing.md)). — **Ausgang:** offen bis zur Closure; ein nicht belegtes Ziel steht ohne Link.
- Die Vorlage ist am echten Lauf von [slice-release-im-tag-lauf-messen](slice-release-im-tag-lauf-messen.md) bewährt, nicht an den beiden alten Versionen; das Jahr, der Commit und die Pakete der alten Versionen sind aus den Protokollen zu lesen, nicht aus dem Gedächtnis. — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen; Treffer: `BEO-TDS/aussagen-ueber-messung-hinaus` (verkörpert in [`AGENTS.md`](../../../../AGENTS.md) §3.8; die Notiz sagt nichts über Dateien, Gleichheit oder anonyme Ladbarkeit für die alten Versionen), `BEO-TDS/dod-nennt-nutzerentscheidung-als-lieferinhalt` (die Bestätigung des Nutzers ist Start-Trigger und Nutzer-Handlung, nicht Lieferinhalt eines Agenten). Kein Zähler erreicht mit diesem Slice 3×, soweit er am Plan abzusehen ist.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
