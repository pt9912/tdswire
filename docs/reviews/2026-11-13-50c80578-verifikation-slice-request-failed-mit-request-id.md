# Verifikationsbericht: slice-request-failed-mit-request-id (HEAD 50c80578)

* Rolle: Verifier (Modul 11), frischer Kontext. Gegenstand: [slice-request-failed-mit-request-id](../plan/planning/in-progress/slice-request-failed-mit-request-id.md). Maßstab: DoD §2, Plan §3 samt Fortschreibung, [TDS-FA-09](../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit), [ADR-0020](../plan/adr/0020-logformat-und-observability-schnittstelle.md), [ADR-0021](../plan/adr/0021-oeffentlicher-integrations-einstieg.md).
* Stand: Zweig `worktree-agent-ad732f1155c96c6cd`, HEAD `50c80578`, Basis `main` `25122359`; Arbeitsbaum am Ende sauber, am Produktivcode nichts geändert (Mutationen zurückgesetzt). Haken in der DoD: keine gesetzt, und ich setze keine.
* Eingang: Reviews [2026-11-13-3029d9c8-review-slice-request-failed-mit-request-id.md](2026-11-13-3029d9c8-review-slice-request-failed-mit-request-id.md) und [2026-11-13-646bc0ad-review-2-slice-request-failed-mit-request-id.md](2026-11-13-646bc0ad-review-2-slice-request-failed-mit-request-id.md); Commit `50c80578` hatte keinen eigenen Review und ist hier gezielt geprüft.

## Urteil

**Konform, ohne HIGH und MEDIUM.** Alle drei Liefer-Punkte sind belegt; die Kernzusage (`REQUEST_FAILED` trägt die Kennung der Anfrage) ist an drei Mutationen aus dem richtigen Grund rot. Alle Findings beider Reviews sind aufgehoben oder benannt. Ein LOW (V-1, Wortlaut „über DEBUG“) und drei INFO stehen unten. Offen bleiben die Aufgaben des Planners: Haken, Closure-Notiz (Zuordnung *Test | Festlegung*, Ausgänge der Risiken in §6, Steering-Loop), Register.

## Eigene Läufe (Belege)

Hinweis: `make gates`, `make clean-build` und `make e2e` liefen im Vordergrund auf dem End-Stand HEAD `50c80578`; der Lauf des Verifiers brach danach durch einen API-Fehler ab und wurde fortgesetzt, die drei Läufe wurden nicht wiederholt (Belege: Logs und Zeitmarken im Scratchpad).

| Lauf | Ergebnis |
|---|---|
| `make gates` | Exit 0, 19 s; Docker-Cache (gleicher Baum wie beim Implementer), Klassen `werkzeug` und `tests` liefen (kein Stempel), `stempel-gruppe` über den Stempel übersprungen |
| `make clean-build` | Exit 0, 584 s (`--no-cache`); 1401 Zeilen `PASSED`, 0 `FAILED`; darunter `RequestFailedRequestIdTest` (beide), `AbandonedFaultRequestIdTest`, `TelemetryJsonTest > Die Zeile request_failed traegt request_id` |
| `make e2e` | Exit 0, 8 Stufen grün, 370 s |
| `make docs-check` | Exit 0, 1400 Dateien, 0 Befunde, 5 s (vor dem Commit dieses Berichts) |
| Mutationen (c) | je Mutation ein Docker-Lauf der Stage `test` (Kopie des Dockerfile im Scratchpad, Filter auf `RequestFailedRequestIdTest`, `AbandonedFaultRequestIdTest`, `TelemetryEventTest`, `DispatchFailureTest`), 61 bis 63 s, einer zugleich |

Gesamtlaufzeit rund 35 Minuten; größte Posten: `make clean-build` 584 s, `make e2e` 370 s, drei Mutationsläufe je rund 62 s, Lesen von Diff, Spezifikation und `docs/user/`. Keine künstliche Last; ein Gradle-Lauf zugleich (parallel liefen Läufe anderer Sitzungen, die ich nicht beeinflusse).

## DoD je Punkt

| DoD-Punkt | Stand | Beleg |
|---|---|---|
| 1 Kennung im Ereignis | erfüllt | `RequestLoop.answer` setzt `errors.requestId` vor der Antwort, alle `ErrorLogEntry` entstehen über `ErrorMapper.record` (Grep: kein anderer Aufruf von `log.record`/`ErrorLogEntry(` in `src/main`), `TelemetryEvents.toEvent` reicht an `RequestFailed` (gekürzt). `RequestFailedRequestIdTest`: Backend-Fehler, abgewiesener RPC, unbekanntes Handle, Parser, Frist; Gleichheit mit dem Ende von `tds.query` (und `backend.execute`), fünf verschiedene Kennungen, Erfolg ohne Eintrag. Kennung `null` ohne Anfrage: Modell kennt den Fall (`TelemetryEventTest`), der Server erzeugt ihn nicht (Befund aus dem Code, so gekennzeichnet). Mutationen (a), (c) rot |
| 2 Fläche und Adapter | erfüllt | Kein neuer öffentlicher Name (unten); KDoc, Spezifikation Abschnitt 1, 4 und Änderungsprotokoll nachgezogen; `TelemetryJsonTest > Die Zeile request_failed traegt request_id` (Kennung gleich der des Spans, `login_accepted` ohne Feld); `TelemetryRedactionTest` führt `requestId` in der Typliste ohne Lockerung |
| 3 Nutzer-Doku | erfüllt, ein LOW | alle fünf Dateien gelesen (V-1); Tabelle der Arten, Aufgabe 5 samt Beispielcode (identisch zu `AnleitungTest`, Zeile `request_id`), Aufgabe 10 samt Grenzen; keine erfundene Beispielzeile von `request_failed` |
| Nachweis: `@spec`-Tags, Tests in `make test` grün | erfüllt | Tags SPEC-018/048/009/033/052 und `TDS-FA-09` an den neuen Tests; Matrix `docs/plan/spec-abdeckung.md` führt sie (Commit `646bc0ad` nach dem letzten Testcommit `b786b860`; `50c80578` ändert keinen Test) |
| `make gates` grün | erfüllt | s. o. |
| Review, Report | erfüllt | zwei Reports unter `docs/reviews/` |
| Closure-Notiz, Register, Ausgänge §6, Paarungen | offen (Planner) | §7 „Wird bei der Closure gefüllt“, §6 ohne Ausgang |

## Mutationen (selbst gefahren, eine je Lauf, zurückgenommen)

| Mutation | Ergebnis |
|---|---|
| (a) `TelemetryEvents.toEvent`: `RequestFailed(at, context, code.name, cause)` ohne Kennung | rot: `RequestFailedRequestIdTest > REQUEST_FAILED traegt die Kennung …` (erwartet vier Kennungen, war `[null, null, null, null]`) und `> Der Ablauf der Frist traegt …` (`null`); 63 s |
| (b) `RequestLoop.answer`: `finally` löscht die Kennung auch bei `abandoned` (`errors.requestId = null`) | rot: `AbandonedFaultRequestIdTest > Der spaete Fehler nach abandoned …` (erwartet `requestId=r1`, war `null`); 61 s |
| (c) Kennung der Vorgängerin bleibt stehen (Löschen im `finally` entfernt, Setzen nur wenn `null`) | rot: `DispatchFailureTest > Die Fehler gehen durch den uebergebenen ErrorMapper` (`erwartet null nach der Anfrage, war r1`) und `RequestFailedRequestIdTest > REQUEST_FAILED traegt …` (Kennung der Vorgängerin); 63 s |

`git status` nach jeder Mutation leer. Alle drei sind aus dem richtigen Grund rot (Ausfall der Zusage, nicht des Aufbaus).

## Commit 50c80578 gezielt am Code

Entfernt: `abandoned = end is RunEnd.Abandoned` in `RequestLoop.answerRelease`; sonst KDoc und Wortlaut. Am Code nachgeprüft:

* `UnprepareBatch.answer` (UnprepareBatch.kt) ruft nur `errors.releaseAnswer(...)` und wertet das Ergebnis aus; kein `fault`-, `unexpected`- oder `record`-Aufruf danach.
* `ErrorMapper.releaseAnswer` (ErrorAnswers.kt) schreibt den Eintrag mit `record(code, cause)` als erste Anweisung der Funktion, vor der Schleife und vor jedem `sink.accept`; das Log-Lambda in `ConnectionWiring` bildet das Ereignis aus dem übergebenen Eintrag sofort (`entry.toEvent`), liest `requestId` also nicht später aus dem Feld. Der Körper `UnprepareBatch.answer` läuft in `detached.async`; ein vor dem Start abgebrochener Lauf führt den Körper nicht aus, ein gestarteter hat `record` synchron hinter sich. Der abgelöste Lauf kann `errors` danach nicht mehr rufen.
* Beobachtbare Verhaltensänderung: keine am Server. Mit Entfernung bleibt `abandoned` in diesem Pfad `false` (zurückgesetzt in `answer`), das `finally` löscht die Kennung; ein Eintrag entsteht nach dem Aufgeben nicht mehr. Das deckt sich mit Mutation M-C des zweiten Reviews (alle Tests grün mit und ohne die Zeile). Der Fall „Freigabe wird aufgegeben“ ist selbst nicht durch einen Test belegt; Spezifikation und Doku beschränken die Zusage jetzt auf „belegt für Anfragen an das Backend“ und behaupten ihn nicht.
* R2-2 behoben: Wortlaut „nur für die Antwort einer Anfrage“ statt „während einer Anfrage“ in Spezifikation, `referenz-ereignisse.md`, KDoc `ErrorLogEntry`, `ErrorMapper.requestId`, `TelemetryEvent.requestId`.

## Plan-vs-Code-Diff (§3)

| Plan | Code | Stand |
|---|---|---|
| `ErrorLogEntry.requestId`, `ErrorMapper.record` | `requestId: String? = null` (`internal`); Erweiterung `ErrorMapper.record` einzige Bildestelle | wie geplant |
| Kennung als Parameter an den Aufrufen | **Abweichung:** Zustandsfeld `ErrorMapper.requestId` (`internal`, `@Volatile`), gesetzt in `answer`, gelöscht im `finally` außer bei `abandoned` | in der Fortschreibung vor dem Code benannt und begründet (13 Aufrufe in 8 Dateien, gezählt vom Reviewer); trägt, Rand in R2-4/V-4 |
| `RequestFailed.requestId` gekürzt | ja, mit Voreinstellung `null` | wie geplant |
| `TelemetryEvents.toEvent` | reicht die Kennung | wie geplant |
| `RequestLoop` setzt und löscht | ja; Nacharbeit F-1: bei `RunEnd.Abandoned` bleibt sie stehen (`abandoned` in `execute`) | Nacharbeit-Absatz stimmt mit dem Code; Verbindungsende nach dem Aufgeben am Code gelesen (`RunConclusion` → `live.abandon`) |
| `TelemetryJson.kt` unverändert | unverändert | wie geplant |
| Tests (`ZustandsereignisseTest` oder neu …) | neu `RequestFailedRequestIdTest`, `AbandonedFaultRequestIdTest`; angepasst `TelemetryEventTest`, `TelemetryRedactionTest`, `TelemetryFelderEmbeddingTest`, `AnleitungTest`, `TelemetryJsonTest`/`LoginProbe`, fünf Dispatch-Tests | wie fortgeschrieben |
| Spezifikation Abschnitt 1, 4, `docs/user/`, Matrix | ja (Abschnitt 5 unverändert; dort kein Satz zum Ereignis `REQUEST_FAILED`) | erfüllt |

## Öffentliche Fläche von Hand (SPEC-059, Java-Lesart)

`git diff main...HEAD -- src/main` berührt öffentliche Deklarationen nur in `TelemetryEvent.kt`: (1) KDoc an `EventKind.REQUEST_FAILED`, an `TelemetryEvent` und an `requestId`; (2) `internal class RequestFailed` bekommt einen Konstruktorparameter. `TelemetryEvent.requestId` (`public open val … get() = null`) und der `protected`-Konstruktor sind unverändert; `RequestFailed` ist `internal` (aus Java im Bytecode erreichbar, nach `SPEC-059` kein Teil der Fläche). Kein neuer Typname, kein neuer Zugriff, keine geänderte Signatur. `src/integrator` ändert nur Tests. `make flaeche` lief im `make gates` grün.

## Review-Findings gegen den Endstand

| ID | Stand am Endstand |
|---|---|
| F-1 (MEDIUM, früher später Fehler `null`) | aufgehoben: `AbandonedFaultRequestIdTest`, Mutation (b) rot |
| F-2 (Plan-Tabelle, Spec-Stellen) | aufgehoben: Plan nennt `ErrorAnswers.kt`, `TelemetryJson.kt` unverändert, Kopf nennt SPEC-048/052 |
| F-3 (Löschen nur White-Box) | INFO, bleibt: nur `DispatchFailureTest` hält es, Mutation (c) rot |
| F-4 (Frist-Test mit fünf Anfragen) | aufgehoben: Frist-Anfrage eigener Test mit Latch, fünf übrige mit 30 s |
| F-5 (Zustandsfeld statt Parameter) | INFO, benannte Abweichung, Rand beschrieben |
| R2-1 (MEDIUM, Eigencode in `answerRelease`) | aufgehoben durch Entfernung, am Code nachgeprüft (oben) |
| R2-2 (Wortlaut) | aufgehoben (oben); V-1 ist eine neue, andere Stelle |
| R2-3 (Zahl der Signaturen) | aufgehoben: „13 Aufrufstellen in 8 Dateien“ im Plan |
| R2-4 (Invariante Aufgeben schließt) | INFO, bleibt: KDoc `RequestLoop.abandoned` nennt die Kopplung, `AbandonedFaultRequestIdTest` hält sie |

## Findings des Verifiers

| ID | Kategorie | Befund | Pfad |
|---|---|---|---|
| V-1 | LOW | Das neue „auch bei einer Ebene über `DEBUG`“ / „bei `LOG_LEVEL` über `DEBUG`“ widerspricht dem Bestand derselben Seite („Ebene unter `DEBUG` im Adapter“, Typische Fehler, Aufgabe 10) in der Leserichtung; eine Ebene „über“ DEBUG ist INFO bis ERROR, gemeint ist die Ebene, auf der das Ende der Spans nicht ankommt. Der Wortlaut stammt aus diesem Slice (`git log -S`: Commit `09c3e34c`), der Bestand „unter“ ist älter. Vorschlag: „wenn der Adapter nur Ebenen ab `INFO` annimmt, wo das Ende der Spans fehlt“. | `docs/user/einbettung-und-betrieb.md` Aufgabe 10 Schritt 2; `docs/user/referenz-ereignisse.md` Zeile `REQUEST_FAILED` |
| V-2 | INFO | Die Zelle `REQUEST_FAILED` in `referenz-ereignisse.md` ist lang und trägt Forensik („Befund aus dem Code, kein Test dafür“, Verweis auf den Testnamen des Grenzfalls); `AGENTS.md` §5 Regel 5 verlangt Ist-Zustand ohne Chronik. Die Aussage ist nicht breiter als der Beleg (§3.8); Kürzung nach Ermessen des Planners. | `docs/user/referenz-ereignisse.md` |
| V-3 | INFO | Die Aussage „der Server ruft sein Fehler-Mapping nur für die Antwort einer Anfrage“ hat keinen Test; ich habe sie am Code nachgeprüft: `ErrorMapper` wird nur in `ConnectionWiring` gebaut und nur von `RequestLoop` (innerhalb `answer`) und der von ihm abgeleiteten Abbildung gerufen. Bleibt als gekennzeichneter Befund aus dem Code. | `spec/spezifikation.md` Abschnitt 4 |
| V-4 | INFO | Zustandsfeld mit zwei Zuständen (`errors.requestId`, `abandoned`) und der Invariante „Aufgeben schließt die Verbindung“; fällt sie, braucht die Kennung eine andere Bindung. KDoc nennt die Kopplung; der Test hält sie. Kein Fehler des Stands. | `RequestLoop.kt`, `ErrorMapper.kt` |

Eine DoD-Verletzung oder Spec-Lücke, die Tests und Review nicht sähen, fand ich nicht.

## Register (Vorschläge, nicht geschrieben)

* `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`: weitere Evidenz (R2-2: Wortlaut „während einer Anfrage“ neben dem neuen Satz; V-1 ähnelt der Klasse als Gegenrichtung „über/unter“). Zählt zum bestehenden Eintrag.
* Neu, 1×: „Eigencode, dessen Entfernung keinen Test rot macht“ (R2-1; per Entfernung statt per Test aufgelöst) — Beobachtung vorschlagen, falls die Klasse in einem weiteren Slice wiederkehrt.
* Neu, 1×: „Zustandsfeld statt Parameter“ (F-5, R2-4, V-4): implizite Kopplung zwischen Schleife und Mapper, Trigger: eine offene Verbindung nach dem Aufgeben.
* `BEO-TDS/request-failed-ohne-request-id`: Stand auf umgesetzt führen, Evidenz dieses Slice.
