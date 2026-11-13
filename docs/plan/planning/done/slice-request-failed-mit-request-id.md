# Slice slice-request-failed-mit-request-id: Anfrage-Kennung im Ereignis REQUEST_FAILED

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-betrieb-diagnose

**Bezug:** [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit), [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) (`Accepted`, Ereignisse an die Senke), [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) (öffentliche Fläche)

**Berührte Spec-Stellen:** SPEC-017 · SPEC-018 · SPEC-048 · SPEC-052 · SPEC-059 · spezifikation.md §4 (Herkunft der IDs, Zielzustand `request_id`) · spezifikation.md §1 (Diagnoseausgabe)

**Verantwortlich:** pt9912

**Autor:** Planner-Agent, Auftrag des Nutzers 2026-11-07. **Datum:** 2026-11-07.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar.

**Ziel:** Das Ereignis `REQUEST_FAILED` trägt die Kennung der Anfrage (`requestId`, dieselbe wie im Ende der Spans `tds.query` und `backend.execute`), sodass ein Fehler (`errorCode`) einer Anfrage ohne Schluss über Verbindung, Sitzung und Reihenfolge zugeordnet werden kann, auch dann, wenn der Adapter `DEBUG` nicht zulässt (etwa `LOG_LEVEL=INFO`) und das Ende der Spans deshalb fehlt.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Ein Ereignis „Anfrage beendet“ (Art, Dauer, Ergebnis) — bleibt Zielzustand der Spezifikation ohne Slice (Nutzerentscheid 2026-11-07); Art, Dauer und Ergebnis tragen heute die Enden der Spans, ein zweites Ereignis dafür wäre eine neue Art und braucht eigenen Anlass (Register `BEO-TDS/request-failed-ohne-request-id`, Re-Evaluierungs-Trigger dort).
- `request_id` in weiteren Ereignissen (Anmeldung, Verbindung): sie gehören keiner Anfrage.
- Ein eigenes Ereignis zum Abbruch durch ATTENTION — bleibt Zielzustand ohne Slice.
- Die Bündelung der Einstellungen in Typen — [slice-einstellungen-in-typen-buendeln](../done/slice-einstellungen-in-typen-buendeln.md).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**.

Liefer-Punkte (drei):

- [x] **Kennung im Ereignis:** `REQUEST_FAILED` trägt `requestId` für jede Antwort mit Fehlercode, die zu einer Anfrage einer angemeldeten Sitzung gehört; die Kennung ist dieselbe wie im Ende von `tds.query` derselben Anfrage (Test am Server über Sammler: Fehler, abgewiesener RPC, Timeout; Gegenprobe: Mutation „andere Kennung“ rot). Eine Antwort, die zu keiner Anfrage gehört, trägt `null`.
- [x] **Fläche und Adapter:** Das Feld `requestId` am Ereignis ist bereits ein öffentlicher Zugriff von `TelemetryEvent` (kein neuer Typname, `make flaeche` bleibt bei der Zahl der Liste von `SPEC-059`); KDoc und Spezifikation (Abschnitt 4 „Herkunft der IDs“, Abschnitt 1, Zielzustand-Sätze) werden auf den Stand gebracht; der JSON-Adapter des Testservers schreibt `request_id` in die Zeile von `request_failed` (Test `TelemetryJsonTest`); die Redaktionstests bleiben ohne Lockerung.
- [x] **Nutzer-Doku:** alle Dateien unter `docs/user/` komplett gegen den Stand nach dem Slice gelesen und auf den Ist-Zustand gebracht (`AGENTS.md` §5 Regel 5): Tabelle der Ereignisarten, Grenzen in Aufgabe 10, Beispielzeilen, Beispielcode im Einbettungstest.

Nachweis-Pflichten für alle drei Punkte:

- [x] Jeder Test trägt im KDoc über der Testfunktion den Block-Tag `@spec SPEC-<NNN>`; die Tests laufen in `make test` und sind grün.
- [x] Jede Aussage steht an einem Test oder ist als Festlegung gekennzeichnet; die Zuordnung *Test | Festlegung* steht in der Closure-Notiz.
- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/error/ErrorMapper.kt`, `.../ErrorAnswers.kt` | update | `ErrorLogEntry` bekommt `requestId`; der `ErrorMapper` hält die Kennung der laufenden Anfrage in einem Feld, und alle Einträge entstehen über `ErrorMapper.record` (statt die Kennung an den drei Aufrufen von `log.record` weiterzureichen; Begründung in der Fortschreibung unten); `ErrorLogEntry` ist `internal` |
| `src/main/kotlin/io/github/pt9912/tdswire/domain/telemetry/TelemetryEvent.kt` | update | `RequestFailed` überschreibt `requestId` (gekürzt wie bei `SpanEnded`); KDoc von `requestId` nennt beide Arten |
| `src/main/kotlin/io/github/pt9912/tdswire/runtime/TelemetryEvents.kt` | update | `ErrorLogEntry.toEvent` reicht die Kennung an `RequestFailed` |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/RequestLoop.kt` | update | setzt die Kennung der Anfrage im `ErrorMapper` in `answer` und löscht sie danach, außer die Anfrage ist aufgegeben (`RunEnd.Abandoned`); keine Entscheidung (ARC-009) |
| `testserver/kotlin/io/github/pt9912/tdswire/testserver/TelemetryJson.kt` | unverändert | die Feldtabelle führt `request_id` schon; belegt durch `TelemetryJsonTest` |
| `src/test/kotlin/` (`ZustandsereignisseTest` oder neuer Test am Server, `TelemetryRedactionTest`, `TelemetryEventTest`), `src/integrator/` (`TelemetryFelderEmbeddingTest`), `testserver/test/` (`TelemetryJsonTest`) | update, neu | Kennung gleich der des Spans; Typliste der Redaktion; Zeile des Adapters; `@spec`-Tag SPEC-018 |
| `spec/spezifikation.md` (Abschnitt 1, 4), `docs/user/` (alle Dateien), `docs/plan/spec-abdeckung.md` | update | Zielzustand-Sätze zu `request_id` in `REQUEST_FAILED`; Nutzer-Doku; Matrix mit `make spec-abdeckung` |

**Fortschreibung des Plans vor dem Code (Implementer, 2026-11-13).** Gemessen am Stand von `main` und gegen den Plan oben:

- `ErrorMapper` wird je Verbindung gebaut (`ConnectionWiring.errorMapper`) und nur von der `RequestLoop` gerufen; die drei Aufrufstellen aus §6 sind nur ein Teil der Stellen, die einen `ErrorLogEntry` schreiben (auch `ErrorAnswers.kt`, `AbortedAnswer`, `RunConclusion`, `BulkCopyProbe` über `errors`). Statt die Kennung durch jede Signatur zu reichen, trägt `ErrorMapper` die Kennung der laufenden Anfrage in einem Feld (`ErrorMapper.requestId`, `internal`, `@Volatile`); die `RequestLoop` setzt es in `answer` vor der Antwort und löscht es im `finally`. Alle Einträge entstehen über eine Stelle (`ErrorMapper.record`). Das löst das zweite Risiko aus §6: die Stelle, die für mehrere Nachrichten meldet (`repeat(messages.size)` im Plan), gibt es im Stand von `main` nicht mehr als eigene Aufrufstelle; jede Antwort gehört der einen Anfrage, die gerade läuft.
- `ErrorLogEntry` bekommt `requestId: String? = null` (`internal`, kein öffentlicher Name); `RequestFailed` bekommt `requestId` als letzten Konstruktorparameter mit Voreinstellung `null` (gekürzt wie bei `SpanEnded`).
- `TelemetryJson.kt` braucht keine Änderung: die Tabelle der Felder führt `requestId` schon (`request_id`) und gibt es für jede Art aus, die es füllt; belegt wird das mit einem neuen Test in `TelemetryJsonTest` (ein Rohbytes-Client `LoginProbe.batch`).
- Tests: neu `RequestFailedRequestIdTest` (Server am Socket: Backend-Fehler, abgewiesener RPC, Freigabe eines unbekannten Handles, Frist, Parser), `TelemetryJsonTest`, `TelemetryEventTest`, `TelemetryRedactionTest` (Feldliste), `TelemetryFelderEmbeddingTest` (nur öffentliche Fläche); die Erwartungen von fünf Dispatch-Tests an `ErrorLogEntry` tragen jetzt die Kennung (`r1`, ... aus dem Zähler des Testaufbaus).
- Nutzer-Doku: `docs/user/referenz-ereignisse.md` und `docs/user/einbettung-und-betrieb.md` (Aufgabe 5 samt Beispielcode in `AnleitungTest`, Aufgabe 10 samt Grenzen); die übrigen Dateien unter `docs/user/` nennen `REQUEST_FAILED` und `requestId` nicht.

**Nacharbeit nach dem Review (F-1, 2026-11-13).** Der Test `AbandonedFaultRequestIdTest` fuhr den Pfad „Backend ignoriert den Abbruch (`RunEnd.Abandoned`) und kehrt nach `CANCEL_TIMEOUT` mit einer Ausnahme zurück“ und fand `requestId = null` im `REQUEST_FAILED`: `answer` hatte die Kennung im `finally` schon gelöscht, der späte Fehler läuft in der abgelösten Coroutine des Laufs. Behoben in der `RequestLoop`: bei `RunEnd.Abandoned` bleibt die Kennung im `ErrorMapper` stehen. Das ist tragfähig, weil das Aufgeben die Verbindung schließt (`live.abandon`) und die Schleife keine weitere Anfrage beantwortet; ein späterer Fehler kann also nur zu der aufgegebenen Anfrage gehören. Verworfen: die Kennung am Lauf statt am Mapper zu führen (`ErrorMapping` und `ResponseSequencer` bekämen die Kennung als Parameter jeder Funktion, eine Änderung an den Signaturen von 13 Aufrufstellen von `errors.*` in 8 Dateien (`RequestLoop`, `RunConclusion`, `AbortedAnswer`, `UnprepareBatch`, `BulkCopyProbe`, `ResponseSequencer`, `ResponseRun`, `ConnectionWiring`) plus `sequencerMapping()` als gespeicherte Lambda, für denselben Wert, solange eine Schleife nur eine Anfrage zugleich beantwortet). Die implizite Kopplung zwischen Schleife und Mapper (F-5 des Reviews) bleibt und ist hier mit ihrem Rand begründet. Zweiter Review (R2-1): der Pfad `answerRelease` (Freigabe per `sp_unprepare`) braucht den Zusatz nicht und trägt ihn nicht: `ErrorMapper.releaseAnswer` schreibt den Eintrag als erste Anweisung, vor dem ersten Token und damit vor dem Aufgeben; der abgelöste Lauf ruft den Mapper danach nicht mehr (kein `fault` in `UnprepareBatch.answer`). Die Zeile `abandoned = ...` dort ist entfernt, die Zusage gilt für Anfragen an das Backend. F-4: die Frist-Anfrage läuft in einem eigenen Test, die fünf übrigen Anfragen laufen mit der Voreinstellung von 30 s.

**Zuschnitt der Fläche (vorab).** Es entsteht kein Typname und kein Zugriff: `TelemetryEvent.requestId` besteht. Braucht der Bau dennoch ein neues Mitglied der öffentlichen Fläche (zum Beispiel ein eigenes Feld statt `requestId`), gilt die Rückführung unten.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-betrieb-metriken-tracing](../done/slice-betrieb-metriken-tracing.md) liegt in `done/` (das Feld `requestId` und die Kennung der Anfrage bestehen), und ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (Entscheid des Architects nötig): der Bau braucht ein neues Mitglied der öffentlichen Fläche über das bestehende `requestId` hinaus (neuer Zugriff, Parameter oder Typname); dann entscheidet der Architect nach [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) und `SPEC-059`, bevor der Slice weiterläuft.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün (einschließlich `make test`), der Review-Report liegt vor, und die Closure-Notiz trägt die Zuordnung *Test | Festlegung* und den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang.

- Nicht jede Antwort mit Fehlercode gehört zu einer Anfrage (Fehler der Anmeldung, des Framings vor der ersten Nachricht); dort bleibt `requestId` `null`, und ein Auswertender darf `null` nicht als Fehler lesen. — **Ausgang:** eingetreten als F-1 des ersten Reviews (der späte Fehler eines Backends, das den Abbruch ignoriert, trug `null`, obwohl er zu einer Anfrage gehörte); im Slice behoben (`b786b860`, Kennung bleibt bei `RunEnd.Abandoned` stehen), Test `AbandonedFaultRequestIdTest` (Mutation „`finally` löscht auch bei `abandoned`“ rot, vom Verifier gefahren). Kein Folge-Slice nötig. Der Fall „keine Anfrage“ erzeugt der Server nicht (Befund aus dem Code, kein Test; siehe Restrisiken).
- `ErrorMapper` hat drei Aufrufstellen mit je anderem Kontext (Zeile 164 meldet für mehrere Nachrichten, `repeat(messages.size)`): welche Kennung dort gilt, ist im Bau zu klären; der Test deckt jede Stelle. — **Ausgang:** entfallen: die Stelle gibt es im Stand von `main` nicht mehr als eigene Aufrufstelle, jede Antwort gehört der einen Anfrage, die gerade läuft (Beleg: Fortschreibung des Plans in §3; alle Einträge entstehen über `ErrorMapper.record`, Grep des Reviewers und des Verifiers: kein anderer `ErrorLogEntry(`-Aufruf in `src/main`).
- Zustandsfeld statt Parameter (Abweichung vom Plan, F-5, R2-4, V-4): die Kennung hängt am Zustand von `ErrorMapper.requestId` und an der Invariante „Aufgeben schließt die Verbindung, danach folgt keine Anfrage“; fällt sie, bleibt eine spät fehlende oder falsche Kennung am Feld hängen. — **Ausgang:** weiter offen (Register `BEO-TDS/zustandsfeld-statt-parameter`, 1×; Trigger: eine offene Verbindung nach dem Aufgeben, dann braucht die Kennung eine andere Bindung, etwa am Lauf). Heute kein Fehlerpfad; KDoc `RequestLoop.abandoned` nennt die Kopplung, `AbandonedFaultRequestIdTest` hält sie für Anfragen an das Backend.

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register · `grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln.

Closure am 2026-11-13 (Planner).

**Belege der Rollen.** Umsetzung `09c3e34c` (Matrix `3029d9c8`). Review 1: [2026-11-13-3029d9c8-review-slice-request-failed-mit-request-id](../../../reviews/2026-11-13-3029d9c8-review-slice-request-failed-mit-request-id.md) (0 HIGH, 1 MEDIUM F-1, 1 LOW F-2, 3 INFO F-3 bis F-5). Nachbesserung F-1: `b786b860` (Matrix `646bc0ad`). Review 2: [2026-11-13-646bc0ad-review-2-slice-request-failed-mit-request-id](../../../reviews/2026-11-13-646bc0ad-review-2-slice-request-failed-mit-request-id.md) (0 HIGH, 1 MEDIUM R2-1, 1 LOW R2-2, 2 INFO R2-3, R2-4; F-1 aufgehoben). Nachbesserung R2-1 bis R2-3: `50c80578`. Verifikation: [2026-11-13-50c80578-verifikation-slice-request-failed-mit-request-id](../../../reviews/2026-11-13-50c80578-verifikation-slice-request-failed-mit-request-id.md) (konform, ohne HIGH und MEDIUM; 1 LOW V-1, 3 INFO V-2 bis V-4): `make gates` Exit 0, 19 s (Docker-Cache); `make clean-build` Exit 0, 584 s, 1401 Testfälle `PASSED`, 0 `FAILED`; `make e2e` Exit 0, 370 s, acht Stufen grün; drei Mutationen je rot (Kennung weglassen in `TelemetryEvents.toEvent`; Löschen auch bei `abandoned`; Kennung der Vorgängerin bleibt stehen). V-1 und V-2 behoben in `e29068bf` (nur Nutzer-Doku und Plan-Wortlaut).

**Nachbesserung ohne zweiten Review (benannt).** `50c80578` änderte Produktcode (die Zeile `abandoned = end is RunEnd.Abandoned` in `RequestLoop.answerRelease` entfällt) nach dem zweiten Review und bekam keinen dritten; der Verifier hat den Commit gezielt am Code geprüft (`UnprepareBatch.answer` ruft nach `releaseAnswer` weder `fault` noch `record`; `releaseAnswer` schreibt den Eintrag als erste Anweisung, vor dem ersten Token; keine beobachtbare Verhaltensänderung am Server) und deckt sich mit Mutation M-C des zweiten Reviews (alle Tests grün mit und ohne die Zeile). Dieser Slice hatte also einen zweiten Review-Lauf für `b786b860`, aber keinen für die Entfernung danach. `e29068bf` ändert nur Doku und Plan-Wortlaut.

**Benannte Abweichung vom Plan.** Die Kennung wird nicht als Parameter durch die Fehlerpfade gereicht, sondern als Zustandsfeld `ErrorMapper.requestId` (`internal`, `@Volatile`) geführt: gesetzt in `RequestLoop.answer` vor der Antwort, gelöscht im `finally`, außer die Anfrage ist aufgegeben. Grund: 13 Aufrufstellen von `errors.*` in 8 Dateien (`RequestLoop`, `RunConclusion`, `AbortedAnswer`, `UnprepareBatch`, `BulkCopyProbe`, `ResponseSequencer`, `ResponseRun`, `ConnectionWiring`) plus `sequencerMapping()` als gespeicherte Lambda, für denselben Wert, solange eine Schleife nur eine Anfrage zugleich beantwortet. Die Abweichung steht in der Fortschreibung des Plans in §3 vor dem Code. Preis: eine implizite Kopplung zwischen Schleife und Mapper (Risiko in §6).

**Fläche.** Kein neuer öffentlicher Name und kein neuer Zugriff: `TelemetryEvent.requestId` bestand; `RequestFailed` ist `internal`. `make flaeche` grün in `make gates`; die Java-Lesart hat der Verifier von Hand am Diff geprüft.

**Zuordnung Test | Festlegung.**

| Aussage | Art | Stelle |
|---|---|---|
| `REQUEST_FAILED` trägt die Kennung der Anfrage für alle getesteten Auslöser (Backend-Fehler, abgewiesener RPC, unbekanntes Handle, Parserfehler, Frist); gleich der Kennung im Ende von `tds.query` und `backend.execute`; fünf verschiedene Kennungen; Erfolg ohne Eintrag | **Test** (Mutationen: Kennung weglassen, Kennung der Vorgängerin bleibt stehen: rot) | `RequestFailedRequestIdTest` |
| Der späte Fehler eines Backends, das den Abbruch ignoriert, trägt die Kennung seiner Anfrage (Pfad `execute`) | **Test** (Mutation: `finally` löscht auch bei `abandoned`: rot) | `AbandonedFaultRequestIdTest` |
| Die Kennung ist nach der Anfrage gelöscht | **Test**, nur White-Box (am Socket nicht beobachtbar, F-3) | `DispatchFailureTest` |
| Das Modell kennt `requestId` = `null`, die Zeile der Senke trägt das Feld | **Test** | `TelemetryEventTest`, `TelemetryJsonTest`, `TelemetryRedactionTest`, `TelemetryFelderEmbeddingTest` |
| Beispielcode der Nutzer-Doku (Aufgabe 5) | **Test** | `AnleitungTest` |
| Der Server ruft sein Fehler-Mapping nur für die Antwort einer Anfrage, `null` tritt am Server also nicht auf | **Festlegung** als Befund aus dem Code, **kein Test** (Verifikation V-3 am Code nachgeprüft: `ErrorMapper` wird nur in `ConnectionWiring` gebaut und nur von `RequestLoop` innerhalb `answer` gerufen) | Spezifikation Abschnitt 4 |
| Der Freigabepfad (`sp_unprepare` per `answerRelease`) braucht den Zusatz für aufgegebene Läufe nicht | **nur durch Code-Lesung** (Review 2, Verifikation), **kein Test**; Spezifikation und Nutzer-Doku beschränken die Zusage auf Anfragen an das Backend | Plan §3 (Nacharbeit) |
| Ein Zustandsfeld statt Parameter trägt, solange das Aufgeben die Verbindung schließt | **Festlegung**, die Kopplung steht im KDoc von `RequestLoop.abandoned` | `RunConclusion`, `ErrorMapper.kt` |

**Restrisiken (benannt).**

- *Zustandsfeld mit zwei Zuständen* (`errors.requestId`, `abandoned`): hängt an der Invariante „Aufgeben schließt die Verbindung“ ([ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md)); Register `BEO-TDS/zustandsfeld-statt-parameter`.
- *Freigabepfad ohne Test.* Ein spät fehlender Eintrag nach einer aufgegebenen Freigabe ist nicht belegt; die Spezifikation behauptet ihn nicht.
- *Der Satz „der Server ruft das Fehler-Mapping nur für die Antwort einer Anfrage“* ruht auf Code-Lesung, kein Test hält ihn.
- *Löschen der Kennung nur White-Box* (F-3): kein Socket-Test kann es sehen.

**Nutzer-Doku komplett gelesen.** Alle Dateien unter `docs/user/` gegen den Stand nach dem Slice (Implementer, Review, Verifikation je für sich): `referenz-ereignisse.md` und `einbettung-und-betrieb.md` angepasst, die übrigen nennen `REQUEST_FAILED` und `requestId` nicht. V-2: die Zelle `REQUEST_FAILED` trägt nur den Ist-Zustand; die Forensik („Befund aus dem Code, kein Test dafür“) steht in der Spezifikation und hier.

**Gelernt (Lerneintrag).**

1. *Eine Fortschreibung nennt die Kernaussage an einer Stelle und lässt die alte an der zweiten stehen* (R2-2: „nur während einer Anfrage“ neben dem neuen Satz in Spezifikation, Nutzer-Doku und zwei KDoc; V-1: derselbe Satz in der Gegenrichtung „über/unter `DEBUG`“, neu im Slice selbst, der Bestand „unter“ war älter). Die Regel gilt (Reviewer-Skill, Schritt 20c in `.claude/commands/implement-slice.md`); sie griff bei Reviewer und Verifier, nicht beim Implementer. Gezählt in `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`. Beim V-1-Fix galt die Regel selbst: Grep über Spezifikation, Nutzer-Doku und Quelltext fand die Stelle im Plan (Ziel in §1) und sonst keine.
2. *Eigencode, dessen Entfernung keinen Test rot macht* (R2-1: die neue Zeile in `answerRelease`; aufgelöst durch Entfernung statt durch Test, weil der Fall nicht erzeugbar war). Die Regel gilt (Reviewer-Skill, `seit welle-transport-tls`) und griff im zweiten Review per Mutation; gezählt in `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test`.
3. *Nachbesserung ohne zweiten Review*: ein zweiter Review lief, die danach entfernte Zeile bekam keinen dritten. Die Regel gilt (`BEO-TDS/nachbesserung-ohne-zweiten-review`, verkörpert); der Vermerk oben ist ihre Erfüllung, die gezielte Prüfung des Verifiers ersetzt den dritten Review. Gezählt als Evidenz.
4. *Eine Zeile Produktcode, die als Folge eines Review-Befunds entfernt wird, ist der kleinste Fix, braucht aber dieselbe Prüfung wie ein Zusatz* — Beobachtung, keine neue Regel; die Begründung steht im Vermerk oben.
5. *Zustandsfeld statt Parameter* ist ein Entwurfspreis, kein Fehler: er ist 1× gesehen und als eigene Beobachtung angelegt, nicht als Regel.

- **Steering-Loop-Eintrag:** keine Regel verkörpert, kein Feld `liegt in` gesetzt; die Klassen 1 bis 3 sind gezählt und ihre Regeln gelten bereits, Klasse 5 ist unterhalb der Schwelle.
- **Beobachtungs-Register (`../observations/`):** `BEO-TDS/request-failed-ohne-request-id` auf *verkörpert* geführt (Zielort und Anker `seit slice-request-failed-mit-request-id`), `evidence/slice-request-failed-mit-request-id.md`; `evidence/slice-request-failed-mit-request-id.md` ergänzt in `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`, `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test`, `BEO-TDS/nachbesserung-ohne-zweiten-review` und `BEO-TDS/plan-tabelle-nennt-neue-datei-nicht` (F-2); neu angelegt `BEO-TDS/zustandsfeld-statt-parameter` (1×). Die Klasse „Eigencode, dessen Entfernung keinen Test rot macht“ hatte schon ein Verzeichnis (`zeile-oder-zweig-ohne-unterscheidenden-test`), deshalb keine neue Beobachtung.
- **Folge-Slices:** keine neuen. [slice-betrieb-prozesslauf-logzeilen-inspektion](../in-progress/slice-betrieb-prozesslauf-logzeilen-inspektion.md) bleibt in `open/`; sein Start-Trigger (dieser Slice in `done/`) ist mit der Closure erfüllt, der Start ist Sache des Nutzers.
- **Risiken aus §6:** alle drei tragen einen Ausgang (eingetreten, entfallen, weiter offen).
- **Gates:** siehe Commit-Bericht der Closure (`make docs-check`, `make flaeche`, `make kopplung-zahlen`, `make gates`); der frische Beleg der Tests ist die Verifikation (`make clean-build`, `make e2e`).

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung.

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); keine eigene Sub-Area.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`), nachgesehen am 2026-11-07:

- `BEO-TDS/request-failed-ohne-request-id` — Auslöser des Slice (Entscheid des Nutzers 2026-11-07: Option (b)).
- `BEO-TDS/oeffentliche-flaeche-mitglieder-ohne-sensor` — 2×, offen: Mitglieder der Fläche hält kein Sensor; dieser Slice fügt keines hinzu.
- `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` — verkörpert: Zielzustand-Sätze zu `request_id` in Abschnitt 1 und 4 der Spezifikation und in der Nutzer-Doku gemeinsam nachziehen.
- Ohne Treffer: die übrigen Beobachtungen.

Alle berührten Sub-Areas GF.
