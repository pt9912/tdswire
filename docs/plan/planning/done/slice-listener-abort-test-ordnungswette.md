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

- [x] **`ListenerAbortTest`** ([TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität)): der Test wartet auf den asynchron gestarteten Abbruch (Ereignis, nicht Wanduhr-Zahl; z. B. der Handler meldet den Beginn des Abbruchs, bevor der Zähler gelesen wird), bevor er `handler.aborts` prüft; die Mutation „`expireGrace()` entfernt“ und die Mutation „Abbruch verzögert gestartet (`delay` vor `action()`)“ machen den Test rot, der Zählerstand 0 wird nicht mehr als Wettlauf gelesen. Beleg: `make test`, Mutationen einzeln gefahren und im Bericht benannt.

Nachweis-Pflichten für alle Punkte:

- [x] `make gates` grün.
- [x] `make flaeche`, `make docs-check`, `make spec-abdeckung` einmal (Test geändert) gelaufen.
- [x] **Ersetzt durch Entscheid (kein Review-Report):** Es existiert kein Review-Report unter `docs/reviews/`; die Zeile „Review durchgeführt, Report liegt vor“ gilt hier durch den [Verifikationsbericht](../../../reviews/2026-11-08-bbaa7dd1-verifikation-slice-listener-abort-test-ordnungswette.md), der den Diff zusätzlich kritisch wie ein Reviewer las (0 HIGH, 0 MEDIUM, 2 LOW, 4 INFO), als erfüllt. Der Entscheid ist die Standardantwort des Orchestrators auf die Empfehlung „Slices unter 100 Zeilen reiner Testcode laufen ohne eigenen Reviewer“; der Nutzer hat ihn nicht ausdrücklich bestätigt und kann ihn umkehren (Review dann nachholen). Siehe §7.
- [x] Haken der Liefer-Punkte setzt der Planner nach der Verifikation, nicht der Implementer (`BEO-TDS/haken-vor-verifikation-gesetzt`).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist — keiner berührt.
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben (`BEO-TDS/wanduhr-tests-flackern-unter-last`, Evidence-Datei dieses Vorgangs).
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo mit Wellen von der nächsten Welle-Closure.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/test/kotlin/io/github/pt9912/tdswire/transport/listener/ListenerAbortTest.kt` | update | Methode `… trennt nach CANCEL_TIMEOUT, wenn der Abbruch die Verbindung nicht beendet`: Wartepunkt `awaitAbortRan` (Ereignis des Handlers, Schutzgrenze nur dort) vor `assertEquals(1, handler.aborts.get())`; die übrigen Methoden lesen den Zähler erst nach `shutdown.await()` oder gar nicht und bleiben; Zusagen und Erwartungen sonst unverändert — nach [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität) (`SPEC-044`, `SPEC-033`) |
| `src/test/kotlin/io/github/pt9912/tdswire/transport/listener/ListenerSupport.kt` | unverändert | Der Wartepunkt liegt im `AbortingHandler` der Testklasse (`awaitAborted`, Ereignis statt Abfrage); `eventually` und `eventuallyGuarded` bleiben unberührt — nach [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) |
| `docs/plan/spec-abdeckung.md` | update (erzeugt) | `make spec-abdeckung` einmal (Test geändert); die Datei ändert sich nur, wenn der Lauf eine andere Abdeckung ergibt |
| `docs/plan/planning/observations/BEO-TDS/wanduhr-tests-flackern-unter-last/` | bei der Closure (Planner) | Evidence-Datei und Nachtrag schreibt der Planner mit der Closure-Notiz, nicht der Implementer |
| `docs/plan/planning/in-progress/roadmap.md` | update | Zustandsfeld: der Slice steht in `in-progress/`; `welle-qualitaet-grundlage.md` bleibt (die Zeile der Slice-Tabelle trägt keinen Zustand). |

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

- **Die Ursache ist aus dem Code gelesen, nicht gemessen.** Wenn der Wettlauf nicht die Ursache der roten Läufe ist, bleibt der Test nach dem Umbau rot unter Last. — **Ausgang: weiter offen.** Ein Lauf unter natürlicher Last nach dem Umbau ohne Rot liegt nicht vor (der Verifier fuhr den Ausgangsstand der Klasse einmal bei Lastmittel um 16, 5 von 5 grün, und keine künstliche Last); bis dahin ist die Hypothese weder bestätigt noch widerlegt. Träger: `BEO-TDS/wanduhr-tests-flackern-unter-last`; ein roter Lauf nach dem Umbau geht dort als Evidence ein und widerlegt die Hypothese.
- **Die Mutation „Abbruch verzögert“ kann einen Wanduhr-Wert brauchen.** Eine Verzögerung in gesteuerter Zeit löst der `ManualDelayer` nicht von selbst. — **Ausgang: für die Mutation im Handler entfallen, für die in `ManagedConnection` eingetreten.** Die Mutation im Test-Handler (`repeat(100_000) { yield() }` vor dem Zählen, Verifikationsbericht §1 C/D) kam ohne Wanduhr aus; die Mutation `scope.launch { delay(300); action() }` in `ManagedConnection.expireGrace` (E/F) brauchte 300 ms Wanduhr im Mutationscode. Der Test selbst bleibt wanduhrfrei.

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register (vorhandene `BEO-<KUERZEL>/<slug>` **zitieren** statt neu
formulieren) · `grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln.

- **Gegenstand:** `ListenerAbortTest > der Listener trennt nach CANCEL_TIMEOUT, wenn der Abbruch die Verbindung nicht beendet` wartet über `awaitAbortRan` (Ereignis des Handlers, `awaitAborted` liest das vorhandene `aborted`-Deferred) auf den asynchron gestarteten Abbruch, bevor er `handler.aborts` prüft. 17 Zeilen Testcode in `ListenerAbortTest.kt`; kein Produktcode, keine Spec, keine ADR berührt.
- **Verifikation** `bbaa7dd1` ([Bericht](../../../reviews/2026-11-08-bbaa7dd1-verifikation-slice-listener-abort-test-ordnungswette.md); 0 HIGH, 0 MEDIUM, 2 LOW, 4 INFO): Mutationen im Rig, je einmal: A (`expireGrace()` entfernt) rot, B (`cancelTimeout`-Frist entfernt) rot, D und F (Abbruch verzögert, ohne Wartepunkt) rot mit `expected: <1> but was: <0>`, C und E (verzögert, mit Wartepunkt) grün. Gemessen als Einzelmessung bei Lastmittel um 16 (fremde Läufe): `ListenerAbortTest` 5 von 5 `PASSED`, `make flaeche` Exit 0, `make docs-check` Exit 0, `make gates FORCE=1` Exit 0 (ein Cache-Lauf: die Frische der Tests trägt der Rig-Lauf, nicht das Gate).
- **Entscheid zur Review-Pflicht (kein Review-Report):** Es gibt keinen Review-Report. Die DoD-Zeile „Review durchgeführt, Report liegt vor“ ist durch den Verifikationsbericht ersetzt, weil der Orchestrator entschied, dass Slices unter 100 Zeilen reiner Testcode ohne eigenen Reviewer laufen und der Verifier den Diff dabei kritisch wie ein Reviewer liest. Das ist ein Entscheid des Orchestrators, vom Nutzer nicht ausdrücklich bestätigt und umkehrbar; kein Haken ohne Beleg, sondern ein benannter Ersatz. Grenze: Der Verifier las den Diff mit Wissen um den Plan; ein Reviewer mit frischem Kontext und eigenen Mutationen blieb aus.
- **`make spec-abdeckung`:** vom Implementer gefahren und unverändert übernommen (`docs/plan/spec-abdeckung.md` nicht im Diff; 6 Festlegungen ohne Nachweis, unverändert); vom Verifier und in dieser Closure nicht wiederholt.
- **Bewusst belassen (beide LOW, kein Code in der Closure geändert):**
  - *F-1:* Die Meldung `nicht eingetreten: der Abbruch lief` in `awaitAbortRan` ist nicht erreichbar: `blocking` kappt den Testkörper bei 20 s, `hangGuard` steht bei 60 s (gemessen in Mutation A: `TimeoutCancellationException: Timed out waiting for 20000 ms` an `ListenerAbortTest.kt:81`; gilt ebenso für das vorhandene `awaitWaiting`). Der nie gestartete Abbruch ist rot wie zugesagt, nur ohne die vorgesehene Meldung; neue Wanduhr entsteht nicht. Belassen, weil die Auflösung (`hangGuard` unter 20 s legen oder die Meldung streichen) auch `awaitWaiting` und `eventuallyGuarded` berührt und damit größer ist als dieser Slice. Anker: [Verifikationsbericht F-1](../../../reviews/2026-11-08-bbaa7dd1-verifikation-slice-listener-abort-test-ordnungswette.md); als Klasse „Wanduhr-Schranken“ ins Register getragen.
  - *F-2:* Der KDoc von `awaitAbortRan` sagt „belegt ist nur, dass ein verzögert gestarteter Abbruch den Test ohne diesen Wartepunkt rot, mit ihm grün lässt“; die Mutationen stehen in keinem Repo-Artefakt außer dem Verifikationsbericht (§1, C bis F). Belassen: die Aussage stimmt (vom Verifier nachgefahren), der Anker ist [Verifikationsbericht §1](../../../reviews/2026-11-08-bbaa7dd1-verifikation-slice-listener-abort-test-ordnungswette.md); der KDoc im Code nennt ihn nicht, weil ein Verweis aus `src/test` auf ein Zeitdokument unter `docs/reviews/` selbst driftet.
- **Gemessen und nicht gemessen:** Die Ursachen-Hypothese (Ordnungswette) ist nicht gemessen; kein Lauf unter hoher Last nach dem Umbau (Risiko 1 weiter offen). Der Slice sagt „behoben“ nirgends; der Ausgang der Beobachtung bleibt beim Nutzer.
- **Was hat funktioniert:** Die Paarung „Mutation ohne Wartepunkt rot, mit Wartepunkt grün“ (C/D, E/F) belegt, dass der Wartepunkt trägt, ohne Last erzeugen zu müssen; die Mutationen A und B zeigen, dass die Zusage nicht geschwächt ist.
- **Was ging anders als geplant:** (1) Die zugesagte Schutzgrenze „nur dort“ trägt wegen der 20 s der Hülle nicht (F-1). (2) Mutation „Abbruch verzögert“ brauchte im Produktcode 300 ms Wanduhr (Risiko 2).
- **Steering-Loop-Eintrag:** Kein Eintrag verkörpert, daher kein `liegt in`; der Eintrag ist gezählt, nicht verkörpert. Gelernt (unter der Schwelle): *Eine Schutzgrenze neben einer äußeren Hülle ist nur so weit wirksam wie die kleinere von beiden*: `hangGuard` (60 s) wurde als Grenze gesetzt, `blocking` (20 s) kappt vorher; die Meldung stand im Code und erreichte nie einen Leser. Die Aussage wurde erst durch die Mutation im Rig gefunden (Verifier), nicht durch Lesen. Die Klasse („Wanduhr-Schranken“) steht als Evidence im Register [`BEO-TDS/wanduhr-tests-flackern-unter-last`](../observations/BEO-TDS/wanduhr-tests-flackern-unter-last/state.md). Benannte Spec-Lücke: keine (kein Verhalten des Produkts berührt).
- **Beobachtungs-Register (`../observations/`):** fortgeschrieben: [BEO-TDS/wanduhr-tests-flackern-unter-last](../observations/BEO-TDS/wanduhr-tests-flackern-unter-last/state.md) (Stand unverändert 8×, Zähler und Ausgang „behoben“ nicht gesetzt, `observation.md` unverändert; Evidence dieses Vorgangs: Klasse „Wanduhr-Schranken“ aus F-1 und INFO-1, `ListenerPartialMessageTest` mit `shortTimeout` 3 s).
- **Folge-Slices:** keine; F-1 und die Klasse „Wanduhr-Schranken“ sind als Kandidat im Register geführt, kein Slice angelegt.
- **Risiken aus §6:** beide mit Ausgang eingetragen, gezählt in der Reihenfolge von §6 (weiter offen: 1; entfallen für die Mutation im Handler, eingetreten für die in `ManagedConnection`: 2).
- **Drei Paarungen:** Das Repo führt Wellen; die Paarungen prüft die Closure von [welle-qualitaet-grundlage](../welle-qualitaet-grundlage.md). Hier geprüft: *Anker*: kein Anker gesetzt (kein `liegt in`). *Folge-Slice*: keiner genannt. *Register*: `BEO-TDS/wanduhr-tests-flackern-unter-last` existiert mit nicht leerem `evidence/`.

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung.

**Vorgelagert — Sub-Area-Wahl prüfen:** `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); der Slice berührt einen Test der Transportschicht, eine eigene Sub-Area besteht nicht.

**Vorgelagert — offene Beobachtungen sichten:** [`BEO-TDS/wanduhr-tests-flackern-unter-last`](../observations/BEO-TDS/wanduhr-tests-flackern-unter-last/state.md) (Gegenstand), `BEO-TDS/test-erwartet-reihenfolge-eines-wettlaufs` (Klasse der Reihenfolge).

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
