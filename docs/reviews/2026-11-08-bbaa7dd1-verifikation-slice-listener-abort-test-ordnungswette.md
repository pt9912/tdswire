# Verifikationsbericht: slice-listener-abort-test-ordnungswette (HEAD bbaa7dd1)

* Rolle: Verifier (Modul 11), frischer Kontext. Gegenstand: Slice [slice-listener-abort-test-ordnungswette](../plan/planning/done/slice-listener-abort-test-ordnungswette.md) (`in-progress/`, Zweig `worktree-agent-af57b948a5ce60873`, HEAD `bbaa7dd1`, Commits `51d4aa93`/`fd8ef4b9` Move, `6d8a2a64` Test und Plan, `bbaa7dd1` Roadmap). Frage: Bauen wir es richtig, gegen Plan und DoD.
* Maßstab: Slice §2 (DoD), §3 (Plan), §6 (Risiken); [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität); `AGENTS.md` §3.1 bis §3.8. Kein ADR, keine Spec-Änderung zugesagt.
* Eingang: DoD-Bestätigung des Implementers samt Belegen. Ein eigener Review fand nicht statt (Entscheid des Orchestrators: Slice unter 100 Zeilen, nur Testcode); ich habe den Diff deshalb zusätzlich kritisch wie ein Reviewer gelesen (Findings unten, Schema HIGH/MEDIUM/LOW/INFO). Das ersetzt den Review-Report der DoD-Pflicht nicht: der Planner entscheidet, ob der Entscheid des Orchestrators diese Pflichtzeile ersetzt.
* Am Code nichts geändert, keine Haken gesetzt.
* Merge-Probe `git merge-tree --write-tree main HEAD` gegen `main` `142bdb20` (= Merge-Basis): Exit 0, sauber.

## Eigene Läufe (Belege)

Host: Lastmittel um 16 (fremde Läufe, keine künstliche Last). Läufe im Vordergrund. Logs und Skripte: Scratchpad `/tmp/claude-1000/-Development-tdswire/3768ddc4-cff6-4152-b0be-815e9ed5f5cb/scratchpad/lav/` (nicht im Repo).

| Lauf | Ergebnis |
|---|---|
| `make gates FORCE=1` | Exit 0 in 38 s. **Cache-Falle:** 88 Docker-Schritte `CACHED`, auch `test 6/6`; der Lauf belegt die Verdrahtung der Gates (`gate-zuordnung`: 16 Gates, Selbsttest 33 Fälle), nicht die Frische der Tests. Die Frische für die Klasse trägt mein eigener Lauf der Klasse im Rig (nächste Zeile) |
| `ListenerAbortTest` allein im Rig (Image aus `--target deps`, Quellen einer Kopie gemountet, `--offline`, `--user` mit eigener uid) | Exit 0, `BUILD SUCCESSFUL`, 5 von 5 `PASSED` (Ausgangsstand, 1 min 8 s) |
| `make flaeche` | Exit 0 |
| `make docs-check` | Exit 0, 1313 Dateien, 0 Befunde (vor Anlage dieses Berichts; nach Anlage siehe Commit-Nachricht) |
| `make clean-build`, `make e2e`, `make matrix-zitate`, `make spec-abdeckung` | nicht gefahren: nur Testcode geändert (Tabelle `harness/README.md`). Matrix: der Diff berührt `docs/plan/spec-abdeckung.md` nicht; Testnamen und Anzahl der Methoden unverändert (`ListenerAbortTest` steht dort weiter), Plan §3 sagt „ändert sich nur, wenn der Lauf eine andere Abdeckung ergibt“: kein Widerspruch |

## 1. Mutationen (Rig, Klasse allein, je ein Lauf)

| # | Mutation | Ergebnis |
|---|---|---|
| A | `open.forEach { it.expireGrace() }` in `TdsListener.awaitConnections` entfernt | Zieltest **rot** (`TimeoutCancellationException: Timed out waiting for 20000 ms`, Stelle `ListenerAbortTest.kt:81`, also im neuen Wartepunkt); drei Schwestertests ebenfalls rot, `ein Handler, der den Abbruch ignoriert` grün |
| B | `times.delayer.delay(times.cancelTimeout)` entfernt | Zieltest **rot** (Timeout in `awaitWaiting(delayer, cancelTimeout)`); außerdem `ruft den Abbruch…` und `ein Handler, der den Abbruch ignoriert…` rot |
| C | `repeat(100_000) { yield() }` vor `aborts.incrementAndGet()` im Test-Handler, **mit** Wartepunkt | 5 von 5 **grün** |
| D | wie C, Zeile `awaitAbortRan(handler)` entfernt | Zieltest **rot**: `expected: <1> but was: <0>` (`ListenerAbortTest.kt:137`) |
| E | zusätzlich (DoD-Wortlaut „`delay` vor `action()`“): `scope.launch { kotlinx.coroutines.delay(300); action() }` in `ManagedConnection.expireGrace`, **mit** Wartepunkt | Zieltest **grün** |
| F | wie E, ohne Wartepunkt | Zieltest **rot**: `expected: <1> but was: <0>` |

Quellen danach zurückgesetzt (`diff -r` gegen das Worktree: gleich). Die Mutationen sind je einmal gefahren; „grün mit Wartepunkt, rot ohne“ trägt der Zusammenhang C/D und E/F, nicht eine Wiederholung. Mutation E und F brauchten eine Wanduhr von 300 ms im Produktcode der Mutation, nicht im Test.

## 2. Prüfung der vier Fragen

**(1) Trägt der Wartepunkt die Zusage weiter? Ja.** Der Diff fügt nur `awaitAborted()` (liest das vorhandene `aborted`-Deferred, das der Handler nach `aborts.incrementAndGet()` vollendet) und den Aufruf `awaitAbortRan(handler)` vor `assertEquals(1, handler.aborts.get())` hinzu. Keine Erwartung ist gelockert oder entfernt: 1 Abbruch, Beenden noch aktiv vor `CANCEL_TIMEOUT`, `activeConnections == 1`, kein `Closed`, danach `SHUTDOWN_GRACE_EXPIRED` genau einmal, Peer getrennt. A und B färben den Test weiter rot, D und F zeigen den Wartepunkt als tragend.

**(2) Hängt `awaitAbortRan` an einer Wanduhr, die unter Last reißen kann?** Der Wartepunkt selbst nicht: `withTimeoutOrNull(hangGuard)` um ein Ereignis (`CompletableDeferred.await`), `eventually` und `eventuallyGuarded` unverändert, `ListenerSupport.kt` nicht angefasst. Aber die Aussage „Schutzgrenze nur dort“ ist enger zu lesen, siehe F-1: `blocking` kappt den ganzen Testkörper bei 20 s, die 60 s sind nie wirksam. Neue Wanduhr-Belastung entsteht dadurch nicht (die 20 s galten vorher für den ganzen Körper schon).

**(3) Nachbarn.** `ListenerAbortTest`: Test 1 liest den Zähler erst nach `shutdown.await()` (der Handler zählt vor `aborted.complete` und kehrt dann zurück, das Ende folgt), die Prüfung `0` vor der Frist ist ein Negativ vor dem Auslöser; Test 3 liest keinen Zähler (`release.isCompleted`, `activeConnections` nach `shutdown.await()`, `delayer.pending` nach Abbruch der Ablauf-Coroutine); Test 4 und 5 lesen Zustand erst nach einem Ereignis (`awaitWaiting`, `shutdown.await()`), `shutdownBegun` ist vor dem ersten `delay` gesetzt (`beginShutdown`). Kein Fund desselben Musters. `ListenerPartialMessageTest`: Zustand (`activeConnections`, `closed()`) wird nach `eventually(... delayer.pending ...)` gelesen, dem Ereignis vor dem Auslöser, `endsOf(...)` nach `shutdown.await()`: kein Fund des Musters, aber INFO-1.

**(4) Mutationen:** siehe §1, alle wie zugesagt.

## 3. DoD und Plan-vs-Code

| Punkt | Befund |
|---|---|
| Liefer-Punkt `ListenerAbortTest` (Wartepunkt auf Ereignis, Mutationen) | **erfüllt am Code**: Wartepunkt am Ereignis des Handlers; „`expireGrace()` entfernt“ rot (A), „verzögert gestartet“ rot ohne und grün mit Wartepunkt (D/F gegen C/E). Der Beleg „im Bericht benannt“ liegt mit diesem Bericht vor |
| `make gates` grün | bestätigt (Cache-Einschränkung oben) |
| `make flaeche`, `make docs-check` | bestätigt grün. `make spec-abdeckung`: nicht von mir gefahren (Auftrag); Erzeugnis `spec-abdeckung.md` fehlt im Diff und braucht es nicht |
| Review, Report unter `docs/reviews/` | offen: kein Review-Report; dieser Bericht ist die Verifikation mit Lesen des Diffs. Entscheid beim Planner |
| Haken | keine gesetzt (Slice §2: alle `[ ]`), konform zu `BEO-TDS/haken-vor-verifikation-gesetzt`; setzt der Planner |
| Closure-Notiz §7, Register, Risiken §6, Paarungen | nicht Sache des Implementers; §7 „Wird bei der Closure gefüllt“ |
| Plan §3 gegen Diff | `ListenerAbortTest.kt` update: deckungsgleich. `ListenerSupport.kt` unverändert: stimmt. `spec-abdeckung.md`: nicht im Diff, nach Plan zulässig. Register: nicht angefasst, nach Plan beim Planner. Roadmap: Zustandsfeld im Diff (`Offene Wellen` nennt Welle und Slice, Wellenzeile nennt `in-progress/`), Slice-Zeile der Welle-Tabelle ohne Zustand. Keine Abweichung. Mit dem Move zogen `fd8ef4b9` die Verweise in `done/slice-sp-execute-unbekanntes-handle-bauen.md`, Evidence-Datei und `state.md` nach (reine Pfadänderungen, je 1 bis 3 Zeilen) |
| Commit-Folge (`AGENTS.md` §3.3) | Move rein (`51d4aa93`, Rename ohne Inhalt), Verweise nachgezogen (`fd8ef4b9`), Inhalt danach. Konform |

Risiken §6: (a) „Ursache aus dem Code gelesen“ bleibt offen: ein Lauf unter Last nach dem Umbau ist hier nicht gefahren (und, auf Auftrag, keine künstliche Last); der Planner führt den Ausgang „weiter offen“ bis zu einem Lauf. (b) „Mutation kann einen Wanduhr-Wert brauchen“: eine Mutation im Handler mit `yield` (C/D) kam ohne Wanduhr aus; in `ManagedConnection` (E/F) braucht die Verzögerung eine Wanduhr von 300 ms im Mutationscode. Vorschlag zum Ausgang: eingetreten für E/F, entfallen für C/D, der Test selbst bleibt wanduhrfrei.

## 4. Hard Rules `AGENTS.md` §3.1 bis §3.8

* §3.1 Docker-only: Implementer-Läufe über `make`; meine Läufe in Docker (Rig-Container, nicht als root). Keine Host-Toolchain.
* §3.2 Suppression: kein `@Suppress` im Diff.
* §3.3 Move: siehe oben, konform.
* §3.4 Architektur: `spec/architecture.md` nicht berührt.
* §3.5 ADR: keine berührt.
* §3.6 Gates: keine Schwelle gesenkt, keine Konfiguration berührt.
* §3.7 Kommentare: KDoc von `awaitAborted` und `awaitAbortRan` beschreiben den Zustand (Zusage/Kopplung/Grenze); ein Rest in F-2.
* §3.8 Aussage ≤ Beleg: Slice §1 „aus dem Code gelesen, nicht gemessen“, §6 Risiko 1 offen, KDoc „aus dem Code gelesen, nicht gemessen“ — kein „behoben“. Ein Rest in F-2.

## 5. Findings

Negativbefund (der Pflicht nach): **HIGH: 0. MEDIUM: 0.** Geprüft und ohne Befund: Schwächung der Zusage (nein), neue Wanduhr im Wartepunkt (nein), dasselbe Muster in den vier Schwestertests und in `ListenerPartialMessageTest` (nein), Basisfehler bei Merge (keiner), gelockertes Gate (nein), Suppression (nein).

* **F-1 (LOW): Die Diagnosemeldung von `awaitAbortRan` ist nicht erreichbar, die 60 s gelten nicht.** `blocking` (`TransportSupport.kt`) kappt den Testkörper bei 20 s; `hangGuard` ist 60 s. Gemessen (Mutation A): der Lauf endet mit `TimeoutCancellationException: Timed out waiting for 20000 ms` an `ListenerAbortTest.kt:81`, nicht mit `AssertionError("nicht eingetreten: der Abbruch lief")`. Derselbe Befund gilt für das vorhandene `awaitWaiting`. Folge: ein nie gestarteter Abbruch ist rot (zugesagt), aber ohne die vorgesehene Meldung; das Wort „Schutzgrenze nur dort“ (Slice) trägt die 20 s der Hülle nicht. Kein Fehler des Slice allein; Auflösung wäre `hangGuard` unter die 20 s zu legen oder die Meldung wegzulassen. Der Planner entscheidet, ob es ein Slice braucht.
* **F-2 (LOW): Der KDoc von `awaitAbortRan` sagt „belegt ist nur, dass ein verzögert gestarteter Abbruch den Test ohne diesen Wartepunkt rot, mit ihm grün lässt“ ohne auflösbaren Anker** (§3.8, §3.7). Die Mutation steht in keinem Repo-Artefakt; der Beleg liegt in diesem Bericht (C bis F, von mir nachgefahren, die Aussage stimmt). Der Satz „Dass die Ordnungswette die Ursache der roten Läufe unter Last war“ ist Grenze-Klasse und ehrlich eingeschränkt, steht aber in der Vergangenheit über Läufe, die der Leser des Codes nicht sieht. Vorschlag: den Verweis auf den Slice oder dieses Register-Evidence statt Wortlaut „belegt“, oder den Satz auf „aus dem Code gelesen, nicht gemessen“ kürzen.
* **INFO-1: `ListenerPartialMessageTest` hat Wanduhr-Schranken von 3 s** (`shortTimeout = 3_000L`, `withTimeout(shortTimeout) { shutdown.await() }`, Zeilen 45, 69, 88) nach `releaseAll` bzw. ohne gesteuerte Zeit. Das ist nicht das Muster dieses Slice (Zählerstand ohne Wartepunkt), aber eine Wanduhr, die unter hoher Last reißen kann; außerhalb des Auftrags, Kandidat für `BEO-TDS/wanduhr-tests-flackern-unter-last`.
* **INFO-2: Der Wartepunkt hängt am ersten Abbruch.** Käme ein zweiter Aufruf von `abort` später, bliebe er für `assertEquals(1, …)` unsichtbar. Der Test belegte „genau einmal“ auch vorher nur zum Lesezeitpunkt; der Slice schwächt das nicht.
* **INFO-3: Schreibweise in der Closure-Notiz von `done/slice-sp-execute-unbekanntes-handle-bauen.md` Zeile „Folge-Slices“**: der Link zeigt nach `in-progress/`, der Satz sagt weiter „ist eine Datei in `open/`“. Zeitpunktaussage der Closure; kein Befund des Slice, und die Pfade sind aufgelöst (`make docs-check` grün).
* **INFO-4: Der Gate-Lauf ist ein Cache-Lauf** (siehe oben); die Frische der Klasse ist durch meinen Rig-Lauf des Ausgangsstands belegt (5 von 5 grün), einmal, bei Lastmittel um 16, nicht unter hoher Last wiederholt.

## 6. Bedingungen für die Planner-Closure

1. Entscheiden, ob der Entscheid des Orchestrators (kein eigener Reviewer) die DoD-Zeile „Review durchgeführt, Report liegt vor“ ersetzt; sonst Review nachholen. Der Diff hat 17 Zeilen Testcode.
2. F-1 und F-2 (beide LOW) entweder in einer kleinen Nachbesserung auflösen oder als bewusst belassen in §7 benennen; F-1 könnte einen Eintrag im Register zur Klasse der Wanduhr-Schranken bekommen (`BEO-TDS/wanduhr-tests-flackern-unter-last`, mit INFO-1).
3. Haken setzt der Planner nach diesem Bericht: Liefer-Punkt und die Nachweis-Zeilen `make gates`, `make flaeche`, `make docs-check` sind von mir bestätigt; `make spec-abdeckung` ist vom Implementer gefahren und von mir nicht wiederholt (Diff berührt `spec-abdeckung.md` nicht).
4. §6 Risiken mit Ausgang versehen: Risiko 1 bleibt „weiter offen“, solange kein Lauf unter Last nach dem Umbau vorliegt (der Ausgang der Beobachtung bleibt beim Nutzer, der Slice sagt „behoben“ nirgends); Risiko 2 siehe oben.
5. Closure-Notiz §7 mit Lerneintrag, Evidence-Datei zu `BEO-TDS/wanduhr-tests-flackern-unter-last`, Welle-Zeile; Roadmap-Zustandsfeld nach der Closure auf „Nichts in Arbeit“ zurück, wenn kein Slice in `in-progress/` bleibt. Die Pfade in den Verweisen auf den Slice folgen beim `git mv` nach `done/` (`slice-mv`).
