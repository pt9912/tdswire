# Slice slice-wanduhr-tests-lastunabhaengig: Zwei lastabhängige Tests messen ohne Wanduhr und ohne Leerlauf-Schleife

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-qualitaet-grundlage

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

**Ziel:** `TableStoreTest > gleichzeitiger Zugriff verliert nichts` und `ListenerAbortTest > der Listener ruft den Abbruch nach der Frist und wartet CANCEL_TIMEOUT ab` messen ihre Zusage ohne Abhängigkeit von der Last des Hosts (begründet, nicht gemessen; die Schutzgrenze von 60 s reißt unter ausreichend hoher Last auch): der erste mit begrenzter, an die Anweisungen der Schreiber gebundener Last der Leser, der zweite mit einem Warten auf das Ereignis statt auf eine Wanduhr-Zahl von Abfragen; beide tragen ihre Zusage weiter, und die Mutation je Zusage färbt sie rot.

**Ist-Zustand (Handmessung im Docker-Rig, Gradle im Image der Stage `deps` mit gemounteten Quellen; der Erzeuger liegt nicht im Repo, die Zahlen gelten als Größenordnung):**

- `TableStoreTest > gleichzeitiger Zugriff verliert nichts`: ein Leser ruft `catalog()` ohne Pause in einer Schleife bis zum Ende der Schreiber. `catalog()` und `replace` teilen die Sperre des Bestands (`synchronized`, nicht fair); der Leser hält sie fast ununterbrochen. Ein Thread-Dump nach 720 s zeigte den Leser mit 690 s CPU, die vier Schreiber mit je 6 bis 8 s; der Lauf wurde nach 792 s abgebrochen (Last 5 bis 8). Die Laufzeit hängt damit an der Verteilung der Sperre, nicht an der Arbeit.
- `ListenerAbortTest … nach der Frist`: die Fristen des Listeners laufen in gesteuerter Zeit (`ManualDelayer`); die Wanduhr steckt in `eventually` (1000 Abfragen mit `delay(5)`, je Abfrage mindestens 5 ms, unter Last mehr) und in `withTimeout(3_000)` nach `releaseAll`. Unter Last steht „nicht eingetreten: Beenden wartet auf 7s“ im Register (`BEO-TDS/wanduhr-tests-flackern-unter-last`); die Spezifikation setzt keine Wanduhr-Zahl für diese Tests (`SPEC-033`, `SPEC-047` nennen Fristen des Produkts, die der Test in gesteuerter Zeit einstellt).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Eine Änderung an `TableStore`, `SimulationBackend` oder `TdsListener` — die Zusagen der Sperren und des Beendens bleiben; nur die Messung der Tests ändert sich. Fairness der Sperre wäre eine Änderung des Produkts ohne Anforderung.
- Die übrigen Wanduhr-Tests der Beobachtung (`TelemetryJsonTest … principal`, `AuthTimingTest`, `TelemetryEmbeddingTest`) — sie sind Kandidaten, kein Beleg (Stand der Beobachtung), und haben einen anderen Mechanismus (Reihenfolge von Antwort und Ereignis); `BEO-TDS/test-erwartet-reihenfolge-eines-wettlaufs` führt sie.
- Die Schutzgrenze anderer Listener-Testklassen (`shortTimeout` in `ListenerShutdownTest`, `ListenerLifecycleTest`, `ListenerPartialMessageTest`) — kein roter Lauf dieser Klassen im Register; `eventually` (gemeinsam) bleibt unverändert (rund fünf Sekunden; `HandshakeWiringTest` nutzt die Grenze als Beleg für `LOGIN_TIMEOUT=1`), die Schutzgrenze nach Uhr gibt es nur als eigene Variante `eventuallyGuarded` für `ListenerAbortTest`; die Zahlen jener Klassen bleiben.
- Der Ausgang der Beobachtung (Verkörperung, Zähler) — der Planner schreibt das Register nach der Verifikation.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (zwei):

- [x] **`TableStoreTest`** ([TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität)): die Last der Leser ist begrenzt (je Anweisung eines Schreibers werden `READERS` Lesevorgänge freigegeben; welcher Leser sie nimmt, bestimmt die Planung; kein Leerlauf-Drehen; jeder gelesene Katalog wird auf Stimmigkeit geprüft), der Test endet in der Größenordnung einer Sekunde statt in Minuten (Rig, drei Läufe unter Last, vorher/nachher gemessen); die Mutation „`catalog()` und `replace` ohne Sperre“ und die Mutation „Strukturänderung ohne `structureLock`“ färben ihn rot (je drei Läufe).
- [x] **`ListenerAbortTest` und `ListenerSupport`** ([TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität)): das Warten auf eine Frist des Listeners hängt am Ereignis (`ManualDelayer.awaitPending`), die Schutzgrenzen gegen einen hängenden Test sind Uhr-Grenzen von 60 s (`eventuallyGuarded`, `hangGuard`) und keine Aussage über die Dauer; `eventually` und damit die übrigen rund 90 Aufrufe (u. a. `HandshakeWiringTest`) bleiben unverändert; die Mutationen „der Abbruch wird nicht gerufen“ und „keine Wartezeit `CANCEL_TIMEOUT`“ färben die Klasse rot.

Nachweis-Pflichten für alle Punkte:

- [x] `make gates` grün.
- [x] `make flaeche`, `make docs-check`, `make spec-abdeckung` einmal (Tests geändert) gelaufen.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Haken der Liefer-Punkte setzt der Planner nach der Verifikation, nicht der Implementer (`BEO-TDS/haken-vor-verifikation-gesetzt`).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist — keiner berührt; `docs/user/` bleibt unverändert.
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben (Auftrag des Planners; Evidence nicht vom Implementer).
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `testserver/test/kotlin/io/github/pt9912/tdswire/testserver/TableStoreTest.kt` | update | zwei Leser, die `catalog()` je Marke eines gemeinsamen Semaphors rufen (je Anweisung eines Schreibers werden `READERS` Marken freigegeben, die Verteilung auf die Leser bestimmt die Planung); jeder Leser prüft den gelesenen Katalog (`kunden` vorhanden, Namen eindeutig); Ende der Leser über eine letzte Marke; Vierer-Schreiber mit je 150 Anfragen und die Endprüfung bleiben; KDoc nennt Last und Wächter — nach [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) (Laufzeit) und [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität) (parallele Zugriffe) |
| `src/test/kotlin/io/github/pt9912/tdswire/transport/listener/ListenerSupport.kt` | update | `ManualDelayer.awaitPending` wartet auf eine Änderung der wartenden Fristen; neue Variante `eventuallyGuarded` mit Uhr-Grenze nach Wahl des Aufrufers (Schutz gegen Hängen); `eventually` bleibt unverändert — nach [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) |
| `src/test/kotlin/io/github/pt9912/tdswire/transport/listener/ListenerAbortTest.kt` | update | `awaitWaiting` über `awaitPending`, `eventually` wird `eventuallyGuarded(…, hangGuard)`; `shortTimeout` (3 s) wird zur Schutzgrenze `hangGuard` (60 s); Zusagen und Erwartungen der Tests bleiben — nach [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität) (geordnetes Beenden, `SPEC-044`, `SPEC-033`) |
| `docs/plan/spec-abdeckung.md` | unverändert | `make spec-abdeckung` läuft einmal (Tests geändert); die Datei ändert sich nur, wenn der Lauf eine andere Abdeckung ergibt (Lauf des Implementers: unverändert) |

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

- Die Leser rufen `catalog()` seltener als vorher; die Mutation ohne Sperre könnte unentdeckt bleiben, weil der Wettlauf zufallsabhängig ist (ein grüner Lauf beweist die Sperre nicht). — **Ausgang:** entfallen, gemessen: die Mutationen „`catalog()` ohne Sperre“ (M1b), „`replace` ohne Sperre“ (M1c), beide ohne Sperre (M1) und „`structureLock` entfernt“ (M2) färben den Test je 3 von 3 rot (Review, Rig); M1 vom Verifier am End-Stand erneut 3 von 3 rot. Grenze: ein grüner Lauf beweist die Sperre weiter nicht, der Wettlauf bleibt zufallsabhängig; gemessen bei Lastmittel 3 bis 12, nicht unter hoher Last.
- Der Flake von `ListenerAbortTest` wurde im Rig nicht reproduziert (drei grüne Läufe unter Last 15 bis 19 vor der Änderung); die Wirkung der Änderung ist damit begründet, nicht gemessen. — **Ausgang:** weiter offen: → [`BEO-TDS/wanduhr-tests-flackern-unter-last`](../observations/BEO-TDS/wanduhr-tests-flackern-unter-last/state.md) im Register; Review und Verifikation sahen den Flake nicht (5 von 5 `ListenerAbortTest` grün im frischen Bau), was ohne Reproduktion nichts belegt; der Ausgang „behoben“ ist nicht gesetzt und bleibt beim Nutzer.
- Die Schutzgrenze von 60 s verlängert die Zeit bis zum Rot, wenn ein Test hängt (statt 3 bis 5 s). — **Ausgang:** eingetreten und gemessen: bei „`expireGrace()` entfernt“ 67 s je Klassenlauf (vier rote Tests; Review 66 bis 68 s), bei „`delay(cancelTimeout)` entfernt“ 47 s, bei `catalog()` ohne Sperre 7 s; die 60 s bleiben eine Wanduhr-Zahl (Review F-6). Hingenommen: ein hängender Test kostet bis zu 60 s statt 3 bis 5 s.

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register (vorhandene `BEO-<KUERZEL>/<slug>` **zitieren** statt neu
formulieren — sonst zählt das Register zwei Namen getrennt) ·
`grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln.

- **Gegenstand:** `TableStoreTest > gleichzeitiger Zugriff verliert nichts` begrenzt die Last der Leser (je Anweisung eines Schreibers werden `READERS` Lesevorgänge freigegeben, jeder gelesene Katalog wird geprüft); `ListenerAbortTest` wartet am Ereignis (`ManualDelayer.awaitPending`) und trägt die Schutzgrenze `hangGuard` von 60 s über die neue Variante `eventuallyGuarded`; `eventually` ist wortgleich der Basis. Kein Produktcode, keine Spec, keine ADR berührt.
- **Review und Verifikation:** Review `0624d431` ([Report](../../../reviews/2026-11-08-0624d431-review-slice-wanduhr-tests-lastunabhaengig.md); 0 HIGH, 3 MEDIUM, 1 LOW, 3 INFO): F-1 bis F-4 behoben in `eec118d3`, F-5 hier entschieden. Verifikation `eec118d3` ([Bericht](../../../reviews/2026-11-08-eec118d3-verifikation-slice-wanduhr-tests-lastunabhaengig.md)): closure-fähig unter B1 bis B4, in dieser Closure erfüllt.
- **Nachbesserung ohne zweiten Review (Grenze):** `eec118d3` hatte keinen eigenen Review; der Verifier prüfte sie einzeln gegen F-1 bis F-5.
- **Gemessen** (Einzelmessungen 2026-11-08, Last durch fremde Läufe, keine künstliche): `make clean-build` Exit 0, 588 s statt 27 bis 34 min, 1269 Testfälle `PASSED`; `TableStoreTest > gleichzeitiger Zugriff verliert nichts` höchstens 1,6 s im frischen Bau (Rig 1,06 s) statt bis zu 24 min; vier Mutationen des Verifiers rot 3/3 (M1, H1, H2, A1), des Reviews M1b, M1c, M2, A1, A2 rot 3/3. Lastmittel 3 bis 11; eine Wiederholung unter hoher Last (15 bis 25) liegt nicht vor.
- **Was hat funktioniert:** (1) Der Review fuhr Mutationen auch an Tests, die der Slice nicht berührte, und fand so, dass `eventually` zwei Wächter in `HandshakeWiringTest` stilllegte (H1, H2 überlebten). (2) Die Mutationen färbten genau die Zusage-Tests; der Verifier wiederholte sie am End-Stand. (3) `eventually` blieb nach der Nachbesserung Zeichen für Zeichen die Basis (`git diff` ohne gelöschte Zeile).
- **Was ging anders als geplant:** (1) Der Plan verlängerte `eventually` selbst auf 60 s; erst der Review (F-1) zeigte, dass rund 90 Aufrufe die alte Grenze als Teil ihrer Aussage trugen. Die Nachbesserung teilte die Variante `eventuallyGuarded` ab. (2) Die Zusage „jeder Leser genau einmal“ war breiter als der Bau (F-3). (3) Ziel ohne Bedingung im selben Satz (F-4).
- **Entscheid zu F-5 / `make spec-abdeckung`:** die Zeile der DoD gilt erst mit dem Lauf der Closure auf dem gemergten Stand als erfüllt; Ergebnis: `make spec-abdeckung` auf dem gemergten Stand (`main` `e8757e09`) Exit 0, `docs/plan/spec-abdeckung.md` unverändert, kein roter Test; `make gates` Exit 0 (frisch, 8 min 29 s) und `make docs-check` Exit 0 (0 Befunde) auf demselben Stand; `make flaeche` lief im Verifikationsbericht (Exit 0) und als Gate in `make gates`.
- **Beobachtungs-Register (`../observations/`):** fortgeschrieben: [BEO-TDS/wanduhr-tests-flackern-unter-last](../observations/BEO-TDS/wanduhr-tests-flackern-unter-last/state.md) (Stand 7× unverändert, kein neues Auftreten; Evidence dieses Vorgangs: Laufzeit des `TableStoreTest` und Lage von `ListenerAbortTest`; Ausgang „behoben“ nicht gesetzt). Neu: [BEO-TDS/gemeinsamer-helfer-verlaengert-zeitaussage-anderer-tests](../observations/BEO-TDS/gemeinsamer-helfer-verlaengert-zeitaussage-anderer-tests/state.md) (1×).
- **Steering-Loop-Eintrag:** Kein Eintrag verkörpert, daher kein `liegt in`; der Eintrag ist gezählt, nicht verkörpert. Gelernt: *Ein gemeinsamer Test-Helfer, dessen Zeitgrenze für einen Test verlängert wird, verlängert sie still für jeden anderen Aufrufer*: `eventually` wurde auf 60 s gestellt und hätte bei rund 90 Aufrufen zwei Wächter in `HandshakeWiringTest` (Frist von `LOGIN_TIMEOUT=1` als Beleg gegen den Standard 15 s) stillgelegt; der Review fing es mit den Mutationen H1 und H2, nicht der Autor. Prüfpunkt bei jeder Änderung eines gemeinsamen Helfers: Aufrufer des Helfers greppen und fragen, ob einer die alte Grenze als Teil seiner Aussage nutzt; eine neue Variante statt Änderung des Bestehenden (1×, unter der Schwelle von 3×). Benannte Spec-Lücke: keine.
- **Folge-Slices:** keine; der Rest (Wanduhr-Kandidaten `TelemetryJsonTest … principal`, `AuthTimingTest`, `TelemetryEmbeddingTest`) ist als Kandidat im Register geführt, kein Slice angelegt.
- **Risiken aus §6:** alle 3 mit Ausgang eingetragen, gezählt in der Reihenfolge von §6 (entfallen, gemessen: 1; weiter offen mit Träger im Register: 2; eingetreten, gemessen und hingenommen: 3).
- **Drei Paarungen:** Das Repo führt Wellen; die Paarungen prüft die Closure von [welle-qualitaet-grundlage](../welle-qualitaet-grundlage.md). Hier geprüft: *Anker*: kein Anker gesetzt (kein `liegt in`). *Folge-Slice*: keiner genannt. *Register*: beide genannten Kennungen bestehen als Verzeichnis mit nicht leerem `evidence/`.

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung.

**Vorgelagert — Sub-Area-Wahl prüfen:** eine Sub-Area (`*`, Tests); keine Ausdifferenzierung nötig.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen; Treffer: `BEO-TDS/wanduhr-tests-flackern-unter-last` (Zähler 7×, Ausgang beim Nutzer), `BEO-TDS/wanduhr-test-nur-wenige-laeufe-gesehen` (Risiko, kein Auftreten).

Alle berührten Sub-Areas GF.
