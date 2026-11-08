# Slice slice-listener-abort-test-ordnungswette: `ListenerAbortTest` wartet auf den asynchron gestarteten Abbruch, bevor er den Zähler liest

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-qualitaet-grundlage

**Bezug:** [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) (Tests in Docker reproduzierbar, auch unter Last des Hosts), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität) (der Test belegt das geordnete Beenden und den Abbruch nach `CANCEL_TIMEOUT`). Kein ADR.

**Berührte Spec-Stellen:** — (kein Verhalten des Produkts ändert sich; der Test trägt `SPEC-044` und `SPEC-033` unverändert).

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912 (Entwurf in der Planner-Rolle, Closure von [slice-sp-execute-unbekanntes-handle-bauen](../done/slice-sp-execute-unbekanntes-handle-bauen.md)). **Datum:** 2026-11-08.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** `ListenerAbortTest > der Listener trennt nach CANCEL_TIMEOUT, wenn der Abbruch die Verbindung nicht beendet` liest den Zähler `handler.aborts` erst, nachdem der asynchron gestartete Abbruch gelaufen ist; ein Abbruch, der nie oder verzögert gestartet wird, macht den Test rot, und unter Last bleibt er grün.

**Ist-Zustand (aus dem Code gelesen, nicht gemessen; [Review](../../../reviews/2026-11-08-ba72fe81-review-slice-sp-execute-unbekanntes-handle-bauen.md) F-8):** `ManagedConnection.expireGrace` startet den angemeldeten Abbruch mit `scope.launch { action() }`. Der Test kehrt von `awaitWaiting(delayer, cancelTimeout)` zurück, sobald die Ablauf-Coroutine des Listeners auf `CANCEL_TIMEOUT` wartet, und liest danach `assertEquals(1, handler.aborts.get())` ohne Wartepunkt auf den Lauf der Abbruch-Coroutine. Unter Last kann der Zähler noch 0 sein. Die Fristen laufen in gesteuerter Zeit (`ManualDelayer`), die Wanduhr ist nicht beteiligt. Die Schwestertests der Klasse lesen den Zähler erst nach `shutdown.await()` oder gar nicht. Träger der Beobachtung: [`BEO-TDS/wanduhr-tests-flackern-unter-last`](../observations/BEO-TDS/wanduhr-tests-flackern-unter-last/state.md) (Evidence `slice-sp-execute-unbekanntes-handle-bauen.md`).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Eine Änderung an `ManagedConnection`, `TdsListener` oder am Ablauf des Beendens — die Zusage (Abbruch wird gerufen, `CANCEL_TIMEOUT` wird abgewartet, danach Trennung) bleibt; geändert wird nur, worauf der Test wartet.
- Die übrigen Wanduhr-Tests der Beobachtung (`TelemetryJsonTest`, `AuthTimingTest`, `TelemetryEmbeddingTest`, `ServerSmokeTest`) — andere Mechanismen oder nur Kandidaten ohne roten Lauf; `BEO-TDS/test-erwartet-reihenfolge-eines-wettlaufs` führt die Klasse der Reihenfolge.
- Der Ausgang der Beobachtung (Verkörperung, „behoben“) — bleibt beim Nutzer; ein grüner Lauf unter Last beweist die Ordnung nicht, die Mutation belegt nur, dass der Wartepunkt trägt.
- Die Methode `… ruft den Abbruch nach der Frist …` — von [slice-wanduhr-tests-lastunabhaengig](../done/slice-wanduhr-tests-lastunabhaengig.md) umgebaut und liest den Zähler nach `shutdown.await()`.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (einer):

- [ ] **`ListenerAbortTest`** ([TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität)): der Test wartet auf den asynchron gestarteten Abbruch (Ereignis, nicht Wanduhr-Zahl; z. B. der Handler meldet den Beginn des Abbruchs, bevor der Zähler gelesen wird), bevor er `handler.aborts` prüft; die Mutation „`expireGrace()` entfernt“ und die Mutation „Abbruch verzögert gestartet (`delay` vor `action()`)“ machen den Test rot, der Zählerstand 0 wird nicht mehr als Wettlauf gelesen. Beleg: `make test`, Mutationen einzeln gefahren und im Bericht benannt.

Nachweis-Pflichten für alle Punkte:

- [ ] `make gates` grün.
- [ ] `make flaeche`, `make docs-check`, `make spec-abdeckung` einmal (Test geändert) gelaufen.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Haken der Liefer-Punkte setzt der Planner nach der Verifikation, nicht der Implementer (`BEO-TDS/haken-vor-verifikation-gesetzt`).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist — keiner berührt.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben (`BEO-TDS/wanduhr-tests-flackern-unter-last`, Evidence-Datei dieses Vorgangs).
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo mit Wellen von der nächsten Welle-Closure.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/test/kotlin/io/github/pt9912/tdswire/transport/listener/ListenerAbortTest.kt` | update | Methode `… trennt nach CANCEL_TIMEOUT, wenn der Abbruch die Verbindung nicht beendet`: Wartepunkt auf den Beginn des Abbruchs vor `assertEquals(1, handler.aborts.get())`; Zusagen und Erwartungen sonst unverändert — nach [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität) (`SPEC-044`, `SPEC-033`) |
| `src/test/kotlin/io/github/pt9912/tdswire/transport/listener/ListenerSupport.kt` | update, falls der Wartepunkt dort liegt | Hilfsfunktion oder Signal des `AbortingHandler`, falls der Wartepunkt ein Ereignis des Handlers ist; `eventually` und die übrigen Aufrufe bleiben unverändert — nach [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) |
| `docs/plan/spec-abdeckung.md` | update (erzeugt) | `make spec-abdeckung` einmal (Test geändert); die Datei ändert sich nur, wenn der Lauf eine andere Abdeckung ergibt |
| `docs/plan/planning/observations/BEO-TDS/wanduhr-tests-flackern-unter-last/` | update | Evidence-Datei dieses Vorgangs, Nachtrag im Stand |
| `docs/plan/planning/welle-qualitaet-grundlage.md`, `docs/plan/planning/in-progress/roadmap.md` | update | Zeile der Slice-Tabelle und Stand der Wellenzeile |

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): keine Start-Bedingung außer einem eingetragenen Verantwortlichen; der Slice teilt keine Datei mit einem offenen Slice.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): der Wartepunkt verlangt eine Änderung am Produktcode (`ManagedConnection`), etwa ein Ereignis des Abbruchs.
- `in-progress` → `open` (blockiert): nicht erwartet.

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

- **Die Ursache ist aus dem Code gelesen, nicht gemessen.** Wenn der Wettlauf nicht die Ursache der roten Läufe ist, bleibt der Test nach dem Umbau rot unter Last. — **Ausgang:** offen bis zur Closure; ein roter Lauf nach dem Umbau geht als Evidence an `BEO-TDS/wanduhr-tests-flackern-unter-last` und widerlegt die Hypothese.
- **Die Mutation „Abbruch verzögert“ kann einen Wanduhr-Wert brauchen.** Eine Verzögerung in gesteuerter Zeit löst der `ManualDelayer` nicht von selbst. — **Ausgang:** offen bis zur Closure.

## 7. Closure-Notiz

Wird bei der Closure gefüllt.

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung.

**Vorgelagert — Sub-Area-Wahl prüfen:** `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); der Slice berührt einen Test der Transportschicht, eine eigene Sub-Area besteht nicht.

**Vorgelagert — offene Beobachtungen sichten:** [`BEO-TDS/wanduhr-tests-flackern-unter-last`](../observations/BEO-TDS/wanduhr-tests-flackern-unter-last/state.md) (Gegenstand), `BEO-TDS/test-erwartet-reihenfolge-eines-wettlaufs` (Klasse der Reihenfolge).

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
