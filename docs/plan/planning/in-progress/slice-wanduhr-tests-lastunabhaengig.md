# Slice slice-wanduhr-tests-lastunabhaengig: Zwei lastabhängige Tests messen ohne Wanduhr und ohne Leerlauf-Schleife

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** ohne Welle

**Bezug:** [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) (Tests in Docker reproduzierbar, auch unter Last des Hosts), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität) (die Tests belegen parallele Zugriffe und das geordnete Beenden). Kein ADR.

**Berührte Spec-Stellen:** — (kein Verhalten des Produkts ändert sich; die Tests tragen `SPEC-035`, `SPEC-033`, `SPEC-044` unverändert).

**Verantwortlich:** Implementer-Agent, Auftrag des Nutzers 2026-11-08.

**Autor:** pt9912 (Entwurf in der Implementer-Rolle auf Auftrag). **Datum:** 2026-11-08.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** `TableStoreTest > gleichzeitiger Zugriff verliert nichts` und `ListenerAbortTest > der Listener ruft den Abbruch nach der Frist und wartet CANCEL_TIMEOUT ab` messen ihre Zusage ohne Abhängigkeit von der Last des Hosts: der erste mit begrenzter, an die Schritte der Schreiber gebundener Last der Leser, der zweite mit einem Warten auf das Ereignis statt auf eine Wanduhr-Zahl von Abfragen; beide tragen ihre Zusage weiter, und die Mutation je Zusage färbt sie rot.

**Ist-Zustand (Handmessung im Docker-Rig, Gradle im Image der Stage `deps` mit gemounteten Quellen; der Erzeuger liegt nicht im Repo, die Zahlen gelten als Größenordnung):**

- `TableStoreTest > gleichzeitiger Zugriff verliert nichts`: ein Leser ruft `catalog()` ohne Pause in einer Schleife bis zum Ende der Schreiber. `catalog()` und `replace` teilen die Sperre des Bestands (`synchronized`, nicht fair); der Leser hält sie fast ununterbrochen. Ein Thread-Dump nach 720 s zeigte den Leser mit 690 s CPU, die vier Schreiber mit je 6 bis 8 s; der Lauf wurde nach 792 s abgebrochen (Last 5 bis 8). Die Laufzeit hängt damit an der Verteilung der Sperre, nicht an der Arbeit.
- `ListenerAbortTest … nach der Frist`: die Fristen des Listeners laufen in gesteuerter Zeit (`ManualDelayer`); die Wanduhr steckt in `eventually` (1000 Abfragen mit `delay(5)`, je Abfrage mindestens 5 ms, unter Last mehr) und in `withTimeout(3_000)` nach `releaseAll`. Unter Last steht „nicht eingetreten: Beenden wartet auf 7s“ im Register (`BEO-TDS/wanduhr-tests-flackern-unter-last`); die Spezifikation setzt keine Wanduhr-Zahl für diese Tests (`SPEC-033`, `SPEC-047` nennen Fristen des Produkts, die der Test in gesteuerter Zeit einstellt).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Eine Änderung an `TableStore`, `SimulationBackend` oder `TdsListener` — die Zusagen der Sperren und des Beendens bleiben; nur die Messung der Tests ändert sich. Fairness der Sperre wäre eine Änderung des Produkts ohne Anforderung.
- Die übrigen Wanduhr-Tests der Beobachtung (`TelemetryJsonTest … principal`, `AuthTimingTest`, `TelemetryEmbeddingTest`) — sie sind Kandidaten, kein Beleg (Stand der Beobachtung), und haben einen anderen Mechanismus (Reihenfolge von Antwort und Ereignis); `BEO-TDS/test-erwartet-reihenfolge-eines-wettlaufs` führt sie.
- Die Schutzgrenze anderer Listener-Testklassen (`shortTimeout` in `ListenerShutdownTest`, `ListenerLifecycleTest`, `ListenerPartialMessageTest`) — kein roter Lauf dieser Klassen im Register; `eventually` (gemeinsam) bekommt die Schutzgrenze nach Uhr statt nach Abfragen, die Zahlen jener Klassen bleiben.
- Der Ausgang der Beobachtung (Verkörperung, Zähler) — der Planner schreibt das Register nach der Verifikation.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (zwei):

- [ ] **`TableStoreTest`** ([TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität)): die Last der Leser ist begrenzt (jeder Leser ruft `catalog()` höchstens einmal je Schritt der Schreiber, kein Leerlauf-Drehen), der Test endet in der Größenordnung einer Sekunde statt in Minuten (Rig, drei Läufe unter Last, vorher/nachher gemessen); die Mutation „`catalog()` und `replace` ohne Sperre“ und die Mutation „Strukturänderung ohne `structureLock`“ färben ihn rot (je drei Läufe).
- [ ] **`ListenerAbortTest` und `ListenerSupport`** ([TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität)): das Warten auf eine Frist des Listeners hängt am Ereignis (`ManualDelayer.awaitPending`), die Schutzgrenzen gegen einen hängenden Test sind Uhr-Grenzen von 60 s und keine Aussage über die Dauer; die Mutationen „der Abbruch wird nicht gerufen“ und „keine Wartezeit `CANCEL_TIMEOUT`“ färben die Klasse rot.

Nachweis-Pflichten für alle Punkte:

- [ ] `make gates` grün.
- [ ] `make flaeche`, `make docs-check`, `make spec-abdeckung` einmal (Tests geändert) gelaufen.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Haken der Liefer-Punkte setzt der Planner nach der Verifikation, nicht der Implementer (`BEO-TDS/haken-vor-verifikation-gesetzt`).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist — keiner berührt; `docs/user/` bleibt unverändert.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben (Auftrag des Planners; Evidence nicht vom Implementer).
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `testserver/test/kotlin/io/github/pt9912/tdswire/testserver/TableStoreTest.kt` | update | zwei Leser, die je Schritt eines Schreibers genau einen Aufruf von `catalog()` machen (Semaphore, vom Schreiber nach jeder Anweisung freigegeben); Ende der Leser über eine letzte Marke; Vierer-Schreiber mit je 150 Anfragen und die Endprüfung bleiben; KDoc nennt Last und Wächter — nach [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) (Laufzeit) und [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität) (parallele Zugriffe) |
| `src/test/kotlin/io/github/pt9912/tdswire/transport/listener/ListenerSupport.kt` | update | `ManualDelayer.awaitPending` wartet auf eine Änderung der wartenden Fristen; `eventually` zählt eine Uhr-Grenze von 60 s statt 1000 Abfragen (Schutz gegen Hängen) — nach [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) |
| `src/test/kotlin/io/github/pt9912/tdswire/transport/listener/ListenerAbortTest.kt` | update | `awaitWaiting` über `awaitPending`; `shortTimeout` (3 s) wird zur Schutzgrenze `hangGuard` (60 s); Zusagen und Erwartungen der Tests bleiben — nach [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität) (geordnetes Beenden, `SPEC-044`, `SPEC-033`) |
| `docs/plan/spec-abdeckung.md` | erzeugt | `make spec-abdeckung`, einmal (Tests geändert) |

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): Auftrag des Nutzers 2026-11-08 (Laufzeit der Gates und des Frischbaus).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): die Änderung braucht eine Anpassung des Produktcodes, um die Zusage zu tragen.
- `in-progress` → `open` (blockiert — Carveout?): nicht erwartet.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

DoD vollständig (vom Verifier bestätigt), Review konform, Closure-Notiz mit Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Die Leser rufen `catalog()` seltener als vorher; die Mutation ohne Sperre könnte unentdeckt bleiben, weil der Wettlauf zufallsabhängig ist (ein grüner Lauf beweist die Sperre nicht). — **Ausgang:** offen bis zur Messung; Ziel ist je Mutation rot in jedem von drei Läufen (Bericht des Implementers).
- Der Flake von `ListenerAbortTest` wurde im Rig nicht reproduziert (drei grüne Läufe unter Last 15 bis 19 vor der Änderung); die Wirkung der Änderung ist damit begründet, nicht gemessen. — **Ausgang:** weiter offen: → `BEO-TDS/wanduhr-tests-flackern-unter-last` im Register (Zähler und Ausgang beim Planner und beim Nutzer).
- Die Schutzgrenze von 60 s verlängert die Zeit bis zum Rot, wenn ein Test hängt (statt 3 bis 5 s). — **Ausgang:** offen bis zur Messung (Mutation „keine Wartezeit“ nennt die Zeit bis zum Rot im Bericht).

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register (vorhandene `BEO-<KUERZEL>/<slug>` **zitieren** statt neu
formulieren — sonst zählt das Register zwei Namen getrennt) ·
`grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln.

*Wird vom Planner nach der Verifikation geschrieben.*

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung.

**Vorgelagert — Sub-Area-Wahl prüfen:** eine Sub-Area (`*`, Tests); keine Ausdifferenzierung nötig.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen; Treffer: `BEO-TDS/wanduhr-tests-flackern-unter-last` (Zähler 7×, Ausgang beim Nutzer), `BEO-TDS/wanduhr-test-nur-wenige-laeufe-gesehen` (Risiko, kein Auftreten).

Alle berührten Sub-Areas GF.
