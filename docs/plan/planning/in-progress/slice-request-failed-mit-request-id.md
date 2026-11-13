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

**Ziel:** Das Ereignis `REQUEST_FAILED` trägt die Kennung der Anfrage (`requestId`, dieselbe wie im Ende der Spans `tds.query` und `backend.execute`), sodass ein Fehler (`errorCode`) einer Anfrage ohne Schluss über Verbindung, Sitzung und Reihenfolge zugeordnet werden kann, auch bei einer Ebene über `DEBUG`, auf der das Ende der Spans fehlt.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Ein Ereignis „Anfrage beendet“ (Art, Dauer, Ergebnis) — bleibt Zielzustand der Spezifikation ohne Slice (Nutzerentscheid 2026-11-07); Art, Dauer und Ergebnis tragen heute die Enden der Spans, ein zweites Ereignis dafür wäre eine neue Art und braucht eigenen Anlass (Register `BEO-TDS/request-failed-ohne-request-id`, Re-Evaluierungs-Trigger dort).
- `request_id` in weiteren Ereignissen (Anmeldung, Verbindung): sie gehören keiner Anfrage.
- Ein eigenes Ereignis zum Abbruch durch ATTENTION — bleibt Zielzustand ohne Slice.
- Die Bündelung der Einstellungen in Typen — [slice-einstellungen-in-typen-buendeln](../done/slice-einstellungen-in-typen-buendeln.md).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**.

Liefer-Punkte (drei):

- [ ] **Kennung im Ereignis:** `REQUEST_FAILED` trägt `requestId` für jede Antwort mit Fehlercode, die zu einer Anfrage einer angemeldeten Sitzung gehört; die Kennung ist dieselbe wie im Ende von `tds.query` derselben Anfrage (Test am Server über Sammler: Fehler, abgewiesener RPC, Timeout; Gegenprobe: Mutation „andere Kennung“ rot). Eine Antwort, die zu keiner Anfrage gehört, trägt `null`.
- [ ] **Fläche und Adapter:** Das Feld `requestId` am Ereignis ist bereits ein öffentlicher Zugriff von `TelemetryEvent` (kein neuer Typname, `make flaeche` bleibt bei der Zahl der Liste von `SPEC-059`); KDoc und Spezifikation (Abschnitt 4 „Herkunft der IDs“, Abschnitt 1, Zielzustand-Sätze) werden auf den Stand gebracht; der JSON-Adapter des Testservers schreibt `request_id` in die Zeile von `request_failed` (Test `TelemetryJsonTest`); die Redaktionstests bleiben ohne Lockerung.
- [ ] **Nutzer-Doku:** alle Dateien unter `docs/user/` komplett gegen den Stand nach dem Slice gelesen und auf den Ist-Zustand gebracht (`AGENTS.md` §5 Regel 5): Tabelle der Ereignisarten, Grenzen in Aufgabe 10, Beispielzeilen, Beispielcode im Einbettungstest.

Nachweis-Pflichten für alle drei Punkte:

- [ ] Jeder Test trägt im KDoc über der Testfunktion den Block-Tag `@spec SPEC-<NNN>`; die Tests laufen in `make test` und sind grün.
- [ ] Jede Aussage steht an einem Test oder ist als Festlegung gekennzeichnet; die Zuordnung *Test | Festlegung* steht in der Closure-Notiz.
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

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

**Nacharbeit nach dem Review (F-1, 2026-11-13).** Der Test `AbandonedFaultRequestIdTest` fuhr den Pfad „Backend ignoriert den Abbruch (`RunEnd.Abandoned`) und kehrt nach `CANCEL_TIMEOUT` mit einer Ausnahme zurück“ und fand `requestId = null` im `REQUEST_FAILED`: `answer` hatte die Kennung im `finally` schon gelöscht, der späte Fehler läuft in der abgelösten Coroutine des Laufs. Behoben in der `RequestLoop`: bei `RunEnd.Abandoned` bleibt die Kennung im `ErrorMapper` stehen. Das ist tragfähig, weil das Aufgeben die Verbindung schließt (`live.abandon`) und die Schleife keine weitere Anfrage beantwortet; ein späterer Fehler kann also nur zu der aufgegebenen Anfrage gehören. Verworfen: die Kennung am Lauf statt am Mapper zu führen (`ErrorMapping` und `ResponseSequencer` bekämen die Kennung als Parameter jeder Funktion, eine Änderung an mehreren Signaturen für denselben Wert, solange eine Schleife nur eine Anfrage zugleich beantwortet). Die implizite Kopplung zwischen Schleife und Mapper (F-5 des Reviews) bleibt und ist hier mit ihrem Rand begründet. F-4: die Frist-Anfrage läuft in einem eigenen Test, die fünf übrigen Anfragen laufen mit der Voreinstellung von 30 s.

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

- Nicht jede Antwort mit Fehlercode gehört zu einer Anfrage (Fehler der Anmeldung, des Framings vor der ersten Nachricht); dort bleibt `requestId` `null`, und ein Auswertender darf `null` nicht als Fehler lesen. — **Ausgang:** offen bis zur Closure.
- `ErrorMapper` hat drei Aufrufstellen mit je anderem Kontext (Zeile 164 meldet für mehrere Nachrichten, `repeat(messages.size)`): welche Kennung dort gilt, ist im Bau zu klären; der Test deckt jede Stelle. — **Ausgang:** offen bis zur Closure.

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register · `grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln.

Wird bei der Closure gefüllt.

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
