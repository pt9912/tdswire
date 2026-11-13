# Review-Report: slice-request-failed-mit-request-id — 2026-11-13

**Review-Art:** Code (gegen Plan, ADR und Hard Rules; nicht gegen die DoD).

**Gegenstand:** Diff `25122359..3029d9c8` im Zweig `worktree-agent-ad732f1155c96c6cd` (zwei Commits: `09c3e34c` Code, Tests, Spezifikation, Nutzer-Doku; `3029d9c8` nur `docs/plan/spec-abdeckung.md`, neu erzeugt; 21 Dateien).

**Skill:** `.harness/skills/reviewer.md` @ `3029d9c8` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-13

**Eingangs-Kontext:** Slice `slice-request-failed-mit-request-id` (§1, §3 samt der Fortschreibung des Plans durch den Implementer), [ADR-0020](../plan/adr/0020-logformat-und-observability-schnittstelle.md), [ADR-0021](../plan/adr/0021-oeffentlicher-integrations-einstieg.md), `TDS-FA-09`, `TDS-QA-03`, `SPEC-017`, `SPEC-018`, `SPEC-048`, `SPEC-052`, `SPEC-059`, `AGENTS.md` §3 (insbesondere §3.7 und §3.8), `harness/README.md`.

## Selbst gefahren

| Lauf | Ergebnis |
|---|---|
| Diff vollständig gelesen (Hauptcode, Tests, Testserver, Integrator, Spezifikation, `docs/user/`, Plan, `spec-abdeckung.md`); `RequestLoop.answer`, `AbortableRun`, `ResponseRun.fault` im Ganzen gelesen | siehe Befunde |
| `make test` auf dem End-Stand | grün (aus dem Layer-Cache des Implementers, 1 s; frisch nicht gefahren) |
| Mutation M-1: `errors.requestId = null` im `finally` von `RequestLoop.answer` entfernt, `make test` | rot, genau ein Test: `DispatchFailureTest > Die Fehler gehen durch den uebergebenen ErrorMapper` (`assertNull(errors.requestId)`); 177 s; Mutation zurückgenommen |
| Grep über `spec/`, `docs/`, `src/`, `testserver/`, `tools/`, `README.md` nach der alten Lesart („trägt die Art nicht“, „nicht `REQUEST_FAILED`“, „Zielzustand … `request_id`“) | keine Fundstelle außerhalb der Zeitdokumente (`done/`, `observations/`, `reviews/`) |
| Grep `docs/user/` nach „bisher“, „noch nicht“, „Zielzustand“, „seit Slice“ und nach `request_failed`/`error_code=` | keine Treffer zu den geänderten Stellen; keine erfundene Beispielzeile (der Zusatz in der Anleitung ist der Code aus `AnleitungTest`) |
| `make docs-check` | grün vor dem Commit dieses Reports |

Gesamtlaufzeit rund 25 Minuten; größter Posten: Mutation M-1 (177 s Gradle-Lauf). Kein `make clean-build`, kein `make e2e`, keine Last.

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| F-1 | MEDIUM | Die Nutzer-Doku sagt unbedingt, `requestId` sei `null`, „wenn die Antwort zu keiner Anfrage gehört; der Server meldet heute keinen solchen Fall“; die Spezifikation trägt dieselbe Aussage nur als „Befund aus dem Code und nicht durch einen Test belegt“. Ein Gegenbeispiel ist nicht ausgeschlossen: der Lauf der Anfrage ist eine `detached.async`-Coroutine; kehrt ein Backend, das den Abbruch nicht beachtet (`RunEnd.Abandoned`), nach `CANCEL_TIMEOUT` mit einer Ausnahme zurück, ruft `ResponseRun.fault` den `ErrorMapper` nach dem `finally`, das die Kennung gelöscht hat, und das `REQUEST_FAILED` trüge `null` für eine Antwort, die zu einer Anfrage gehörte. Pfad aus dem Code gelesen, nicht gefahren. Failure-Szenario: ein Auswertender verlässt sich auf die Doku („nie `null` im heutigen Server“) und ordnet dieses Ereignis keiner Anfrage zu. | `AGENTS.md` §3.8 | `docs/user/referenz-ereignisse.md` · „der Server meldet heute keinen solchen Fall“; `spec/spezifikation.md` · „ein Befund aus dem Code und nicht durch einen Test belegt“; `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/RequestLoop.kt` · `errors.requestId = null` | ja — Test mit Backend, das den Abbruch ignoriert und danach wirft (Vorbild `AbbruchWiringTest`, `CANCEL_TIMEOUT` klein), `REQUEST_FAILED` des späten Fehlers lesen | Aussage über „kein Fall ohne Anfrage“ breiter als ihr Beleg; Kennung im geteilten Feld, Lauf überlebt `answer` |
| F-2 | LOW | Die Plan-Tabelle in §3 nennt `ErrorAnswers.kt` nicht als geänderte Datei (die Fortschreibung nennt sie nur als Stelle, die Einträge schreibt) und listet `TelemetryJson.kt` als `update`, obwohl sie unverändert bleibt (die Fortschreibung begründet das); der Kopf „Berührte Spec-Stellen“ nennt `SPEC-048` und `SPEC-052` nicht, die als `@spec`-Tags der neuen Tests und in der Spezifikationsänderung stehen. | Maintainability (Reviewer-Skill, Eintrag „Plan-Tabelle nennt die berührten Dateien nicht“) | `docs/plan/planning/done/slice-request-failed-mit-request-id.md` · „Berührte Spec-Stellen“ und Tabelle in §3 | nein | Plan-Tabelle nennt die berührten Dateien nicht |
| F-3 | INFO | Das Löschen der Kennung im `finally` ist am Socket nicht beobachtbar (der Server schreibt außerhalb einer Anfrage keinen Fehler); nur der White-Box-Test `DispatchFailureTest` (`assertNull(errors.requestId)`) macht Mutation M-1 rot. Der Schutz gilt dem Zustand für künftige Aufrufer, nicht heutigem Verhalten. | Maintainability | `src/test/kotlin/io/github/pt9912/tdswire/protocol/dispatch/DispatchFailureTest.kt` · „Nach der Anfrage gilt keine Kennung mehr“ | ja — Mutation M-1 | Löschen der Kennung nur White-Box belegt |
| F-4 | INFO | `RequestFailedRequestIdTest` setzt `QUERY_TIMEOUT=1` (Untergrenze des Bereichs) für alle sechs Anfragen der Sitzung; nur „haengt“ soll ablaufen. Dauert eine der fünf anderen Anfragen unter Last länger als 1 s (erste Anfrage nach dem Login, kalte JVM), kommt `TDS_TIMEOUT` statt des erwarteten Codes und der Test wird rot. Kleiner geht die Frist nicht; ob es auftritt, ist nicht gemessen. | `TDS-QA-03` (nicht berührt; Hinweis zur Lastabhängigkeit) | `src/test/kotlin/io/github/pt9912/tdswire/runtime/RequestFailedRequestIdTest.kt` · `ConfigKeys.QUERY_TIMEOUT to "1"` | nein | Wanduhr-Frist im Test mit mehreren Anfragen |
| F-5 | INFO | Abweichung vom Plan (Kennung als `@Volatile`-Feld des `ErrorMapper`, gesetzt in `answer`, gelöscht im `finally`, statt durch jede Signatur gereicht): tragfähig. Ein `ErrorMapper` je Verbindung, die Schleife beantwortet eine Anfrage nach der anderen, alle Einträge gehen durch `ErrorMapper.record` (Grep: kein anderer `ErrorLogEntry(`-Aufruf in `src/main`), und die Fortschreibung des Plans benennt die Änderung vor dem Code. Offen bleibt der Preis: eine implizite Kopplung zwischen Schleife und Mapper (KDoc nennt sie) und der Rand aus F-1. `errors.requestId` wird vor dem `try` gesetzt; wirft `session.request` vorher, bleibt die Kennung stehen (die Schleife endet dann ohnehin). | [ADR-0020](../plan/adr/0020-logformat-und-observability-schnittstelle.md) | `src/main/kotlin/io/github/pt9912/tdswire/protocol/error/ErrorMapper.kt` · `internal var requestId: String? = null` | nein | Zustandsfeld statt Parameter |

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `src/main/.../domain/telemetry/TelemetryEvent.kt` (öffentliche Fläche, Java-Lesart) | geprüft, ohne Befund: `TelemetryEvent.requestId` bestand als öffentlicher Zugriff, `RequestFailed` ist `internal`, der neue Konstruktorparameter erzeugt keinen Namen und keinen Zugriff in `SPEC-059`; `make flaeche` vergleicht nur Typnamen, die Mitglieder wurden von Hand gelesen; KDoc nach §3.7 (Zusage, Indikativ) und §3.8 |
| `src/main/.../protocol/dispatch/RequestLoop.kt`, `protocol/error/ErrorMapper.kt`, `ErrorAnswers.kt`, `runtime/TelemetryEvents.kt` | geprüft, ohne Befund außer F-1 und F-5: jede Aufrufstelle (Backend-Fehler, Typfehler, Protokollfehler, RPC-Ablehnung, Handle, Freigabe, Frist, Beenden) läuft innerhalb `answer`; `@Volatile` genügt für die Sichtbarkeit, weil je Verbindung nur ein Strang Anfragen beantwortet; keine Kennung einer Vorgängerin (Setzen am Anfang, Löschen im `finally`) |
| Redaktion | geprüft, ohne Befund: die Kennung ist die UUID aus `requestIds()`, gekürzt auf 256 Einheiten wie bei `SpanEnded`; kein Wert aus SQL, Parametern oder Ausnahmetext; `TelemetryRedactionTest` führt `requestId` in der Typliste ohne Lockerung |
| `src/test/` (neu `RequestFailedRequestIdTest`, `TelemetryEventTest`, fünf Dispatch-Tests) | geprüft, ohne Befund außer F-3 und F-4: die Mutationen „keine Kennung“, „andere Kennung“, „Kennung der Vorgängerin“ röten den Test am Socket (Vergleich mit `tds.query` und `backend.execute` je Anfrage, Sechs verschiedene Kennungen, Gegenprobe Erfolg ohne Fehler); nicht gefahren, aus dem Test gelesen |
| `src/integrator/`, `testserver/test/` | geprüft, ohne Befund: der Einbettungstest bleibt bei der öffentlichen Fläche; `TelemetryJsonTest` belegt `request_id` in `request_failed` und seine Abwesenheit bei `login_accepted` (`TelemetryJson.kt` bleibt unverändert, weil die Tabelle `requestId` schon führt) |
| `spec/spezifikation.md` | geprüft, ohne Befund außer F-1: Abschnitt 1, 4 und Änderungsprotokoll gleichlautend; die untere Schicht erweitert das Lastenheft nicht (`TDS-FA-09` nennt keine `request_id` im Wortlaut); der Entfall des Zielzustands „`request_id` in den übrigen Ereignissen“ steht im Protokoll mit Entscheid des Nutzers; kein neuer öffentlicher Name |
| `docs/user/` (alle Dateien gelesen/gegrept) | geprüft, ohne Befund außer F-1: Tabelle der Arten, Aufgabe 5 mit Beispielcode, Aufgabe 10 mit Grenzen; kein Abwesenheits- oder Planungstext, keine erfundene Beispielzeile |
| `docs/plan/spec-abdeckung.md`, Haken im Slice | geprüft, ohne Befund: Matrix enthält den neuen Test unter `SPEC-018`, `SPEC-048`, `SPEC-052`, `TDS-FA-09`; die DoD trägt im Diff keinen gesetzten Haken |
| Hard Rules (`@Suppress`, Host statt Docker, Schichten, Accepted-ADR) | geprüft, ohne Befund |

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 1 |
| LOW | 1 |
| INFO | 3 |

**Finding-Klassen dieses Laufs:** Aussage über „kein Fall ohne Anfrage“ breiter als ihr Beleg · Plan-Tabelle nennt die berührten Dateien nicht · Löschen der Kennung nur White-Box belegt · Wanduhr-Frist im Test mit mehreren Anfragen · Zustandsfeld statt Parameter

## Verdikt

**Merge-blockierend:** ja, wegen F-1 (MEDIUM); die Einschränkung der Aussage in `docs/user/referenz-ereignisse.md` auf den Stand der Spezifikation oder ein Test des späten Fehlers nach `Abandoned` hebt das Finding auf. HIGH liegt nicht vor; die Kernlieferung (richtige Kennung in jedem getesteten `REQUEST_FAILED`, keine neue öffentliche Fläche, keine neuen Werte) hält.

**Übergabe:** Findings gehen an den Implementer; die Finding-Klassen gehen in die Slice-Closure §7 und von dort in den Zähler. Dieser Report ersetzt keine Verifikation; DoD- und Spezifikationskonformität prüft der Verifier.
