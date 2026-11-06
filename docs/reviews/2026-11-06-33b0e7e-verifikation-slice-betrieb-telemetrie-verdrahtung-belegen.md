# Verifikationsbericht: slice-betrieb-telemetrie-verdrahtung-belegen (HEAD 33b0e7e)

* Rolle: Verifier (Modul 11), frischer Kontext. Gegenstand: Slice `slice-betrieb-telemetrie-verdrahtung-belegen` (`in-progress/`, Zweig `worktree-agent-a735f10cebdeaf861`, HEAD `33b0e7e1`; Slice-Anteil `7bb31eb0..HEAD`, Basis `652d5667`). Verträge: der Slice (DoD, Plan-Tabelle, Risiken), [ADR-0020](../plan/adr/0020-logformat-und-observability-schnittstelle.md) (Frage 2 und 7), [ADR-0024](../plan/adr/0024-lokale-stempel-zwei-klassen.md) und [ADR-0030](../plan/adr/0030-dritter-stempel-fuer-die-selbsttests-der-stempel.md) (Stempel), [TDS-FA-09](../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit), [TDS-QA-05](../../spec/lastenheft.md#tds-qa-05--wartbarkeit), Spezifikation Abschnitt 4 und `SPEC-048`, der Review-Report `2026-11-06-84edd7e-review-slice-betrieb-telemetrie-verdrahtung-belegen` (F-1 bis F-9) und die Nutzerentscheide dieser Runde.
* Bedingung der Läufe: Host mit Last 16 bis 22 (`uptime`), andere Agenten bauen parallel; Docker-Cache der Stages `test` und `detekt` teils warm. Wo ein Lauf aus dem Cache kam, steht es dabei.
* Nicht geprüft: `make e2e`, `make referenztest-dotnet` (kein Gate-Beleg dieses Slice, kein Diff dort); `make smoke` (kein Diff am Testserver).

## Gesamturteil

**Closure-fähig: nein, solange V-1, V-2 und V-3 offen sind; alle drei sind Nachzüge in Dokumenten und Haken beziehungsweise ein Beleg, keine Änderung am Produktivcode.** Die drei Liefer-Punkte sind in der Sache geliefert: die Verdrahtung von `ACCEPT_FAILED` und `CONNECTION_FAILED` ist über den Server belegt (Mutationen M9, M10 und zwei Leck-Mutationen machen je zwei Tests rot), jedes der acht Packages hat einen Rot-Fall der Ausgabe-Sperre (Verengung des `includes`, entferntes `Logger.log` und ein neues Package ohne Rot-Fall machen `make detekt-selbsttest` rot), und die Lücke von `Logger.log` ist geschlossen. Alle Gate-Läufe sind grün, die neuen Tests sind in einer Schleife unter Last stabil. Die Änderung am Stempel-Werkzeug ist eine Verschärfung (V-5, LOW). Im Slice im verifizierten HEAD fehlen dagegen die Nutzerentscheide dieser Runde (V-1), und der Haken `@spec SPEC-<NNN>` steht zu Unrecht (V-2).

## Selbst gefahrene Sensoren

| Sensor | Ergebnis | Hinweis |
|---|---|---|
| `make docs-check` | 1039 Dateien, 0 Befunde (vor diesem Bericht) | |
| `make flaeche` | Exit 0 | Stage aus dem Cache (kein Diff an der öffentlichen Fläche) |
| `make gate-zuordnung` | 16 Gates, 4 Klassen je 4; Selbsttest 33 Fälle | Stage `quellpakete` stört die Zuordnung nicht |
| `make stempel-eingaenge-pruefung` | ok | |
| `make stempel-eingaenge` | 95 Fälle wie erwartet | |
| `make stempel-gruppe` | 67 Fälle wie erwartet | Fall „src/main geändert“ ruft werkzeug und tests |
| `make stempel-abbild` | 100 Fälle wie erwartet | jeder Eintrag der echten Liste (auch `src/main`) ändert den Hash |
| `make stempel-selbsttest` | 118 Fälle wie erwartet | |
| `make gates FORCE=1` | Exit 0, 64 s | fast alles aus dem Layer-Cache, **kein Frischbeleg der Tests** (Cache-Falle) |
| `make clean-build` (`--no-cache`: build-jar, test, detekt) | Exit 0, 8 min 39 s | 1007 Testfälle `PASSED`, 0 `FAILED`; `TelemetryWiringTest` 3/3, `TelemetryRedactionTest` alle grün; Last 16 bis 22 |
| `make test` | Exit 0, 1,2 s | **aus dem Cache**, wiederholt nichts; ein „mehrfach“ über `make test` wäre ein Scheinbeleg |
| Wiederholung unter Last: Stage `test` einer Kopie, achtmal `cleanTest :test` nur für `TelemetryWiringTest` und `TelemetryRedactionTest` | 8 von 8 grün (24 und 56 `PASSED`) | die Zeitschranken (40 s, 60 s) und das Limit von 128 und 160 Dateien hielten bei Last 16 bis 22 |
| `make detekt-selbsttest` | Exit 0 | Cache; deshalb zusätzlich `docker build --no-cache --target detekt-selbsttest`: Exit 0, 132 Aufruf-Fälle (12 Kern, 120 Ausgabe-Sperre: 15 Einträge mal 8 Packages), 0 Fehlmeldungen |
| `make skript-selbsttest` | Exit 0 | Cache |

## Mutationen (je eigene Kopie von `HEAD`, Stage `test` mit Lauf nur der zwei Klassen, oder Stage `detekt-selbsttest`)

| Mutation | Wirkung |
|---|---|
| M9 `ListenerEvent.Closed` liefert kein Ereignis | rot: `TelemetryWiringTest` (Handler mit Ausnahme), `TelemetryRedactionTest` (Text der Ausnahme eines Handlers) |
| M10 `ListenerEvent.AcceptFailed` liefert kein Ereignis | rot: `TelemetryWiringTest` (gescheitertes Annehmen), `TelemetryRedactionTest` (Text der Ausnahme des Betriebssystems) |
| Leck 1: `CONNECTION_FAILED.exceptionType` = Typname + Text der Ausnahme | rot: dieselben zwei Tests wie M9 |
| Leck 2: `ACCEPT_FAILED.exceptionType` = Typname + Text der Ausnahme | rot: dieselben zwei Tests wie M10 |
| `includes` der Ausgabe-Sperre auf `runtime`, `protocol`, `transport` verengt | `detekt-selbsttest` rot: „meldet kotlin.io.println in config/Aufruf.kt 0-mal“ |
| Eintrag `java.util.logging.Logger.log` aus `detekt.yml` entfernt | `detekt-selbsttest` rot: Markierungen in `Aufruf.kt` gegen Einträge |
| neues Package unter `src/main` ohne Rot-Fall | `detekt-selbsttest` rot: „Package(s) von src/main ohne Rot-Fall … neu“ |
| Flake-Fix zurückgenommen (`eventually("LOGIN_ACCEPTED")` entfernt), zwölf Läufe von `TelemetryRedactionTest` bei Last 16 | 12 von 12 grün: der Fehler ist nicht reproduziert (V-3) |
| wie vorige, dazu 400 ms Verzögerung vor `LoginAccepted` im Produktcode (Kopie) | grün, mit und ohne Fix: `server.stop()` wartet die Verbindung ab, der Fehler ist auch so nicht reproduziert (V-3) |

Nicht gefahren: die Mutation „eine Überladung von `Logger.log` fällt aus den Aufrufen“ (der Zähler je Methode in `tools/detekt-selbsttest.sh` ist gelesen, nicht mutiert).

## DoD-Punkte

Die Haken stehen im verifizierten HEAD bei den drei Liefer-Punkten und bei vier Nachweis-Pflichten (`@spec`, Aussagen, `make gates`, Doku-Update) als `[x]`, gesetzt vom Implementer vor Review und Verifikation (Review F-3, unverändert; V-2). Das Urteil hier ersetzt keinen Haken: der Planner setzt sie nach diesem Bericht.

| # | DoD-Punkt | Urteil | Beleg |
|---|---|---|---|
| 1 | Verdrahtung `ACCEPT_FAILED`, `CONNECTION_FAILED` über den Server; Felder und Grenze „keine `session_id`, Profil `unknown`“; Mutationen M9 und M10 rot; Redaktionstest je Art mit verschiedenen Eingabewerten | erfüllt | `TelemetryWiringTest` prüft Ebene, Typname, Verbindung 1, `sessionId` null, Profil `unknown`, daneben `LOGIN_ACCEPTED` mit Sitzung und bekanntem Profil, mit zwei verschiedenen Ausnahmen; M9 und M10 gefahren (oben); Redaktionstests mit Marken in Benutzername, Kennwort und Ausnahmetext, Leck-Mutationen rot. Der Auslöser von `CONNECTION_FAILED` ist ein `Error` aus der Senke (Nutzerentscheid, V-1); der von `ACCEPT_FAILED` das Limit offener Dateien einer Kind-JVM (nur mit vorgeladenen Klassen, V-6) |
| 2 | Rot-Fall je Package (`domain`, `port`, `config`, `observability`, `simulation`); Verengung des `includes` macht `detekt-selbsttest` rot; Erlaubt-Fall | erfüllt | acht Packages mit `AusgabeAufruf`, 120 Fälle in einem Frischlauf; Verengung rot; die Erlaubt-Fälle von `domain` und `port` tragen ihr `println` (unverändert, `Erlaubt.kt` im Kern-Satz). Grenze: `observability` hat keinen Ordner unter `src/main` (V-6) |
| 3 | `Logger.log` gesperrt oder Grenze benannt; `Thread.dumpStack`, `printStackTrace(PrintStream)` gemessen | erfüllt | `log`, `logp`, `logrb` und `Thread.dumpStack` in `detekt.yml`, je Rot-Fall mit einer `erwartet`-Zeile je Aufruf (fünf Überladungen von `log`); `printStackTrace(PrintStream)` und `(PrintWriter)` melden unter `kotlin.Throwable.printStackTrace`; die Grenze (`config`, `fine`, `finer`, `finest`, `entering`, `exiting`, `throwing`, Logger einer Fremdbibliothek) steht im Kommentar der Regel; Nutzerentscheid: keine weitere Sperre |
| N1 | jeder neue Test trägt `@spec SPEC-<NNN>`; Tests laufen in `make test` und sind grün | **teils** | grün (siehe oben); `TelemetryWiringTest` · „Eine Verbindung ohne Ausnahme des Handlers ergibt kein CONNECTION_FAILED“ trägt nur `@spec` [TDS-FA-09](../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), kein `SPEC-<NNN>` (V-2) |
| N2 | jede Aussage an einem Test oder als Festlegung gekennzeichnet | erfüllt | Test-KDoc und Spezifikation nennen die Bedingungen (vorgeladene Klassen, 128 Dateien als Setzung, `Error` aus der Senke als Auslöser); Mutation `AcceptChild` ohne `preloadClasses()` im Review gefahren, hier nicht wiederholt |
| N3 | `make gates` grün | erfüllt | Exit 0 (`FORCE=1`, Cache), dazu Frischläufe der Tests und von `detekt-selbsttest` |
| N4 | Doku-Update Abschnitt 4 | erfüllt | Satz zu `CONNECTION_FAILED`: „gebaut … belegt in `TelemetryWiringTest`“; zusätzlich ein Satz, welche Felder am Server und nicht über die öffentliche Fläche belegt sind; `SPEC-059` unverändert |
| N5 | Flake `TelemetryRedactionTest` stabilisiert, mit reproduziertem Fehler (Nutzerentscheid, Zeile liegt nur auf `main`) | **nicht belegt** | Fix steht (Wartebedingung vor `server.stop()`), der Fehler ist weder im Slice noch bei mir reproduziert (V-3) |
| — | Review durchgeführt | Report liegt vor; Fortsetzung nach den Nachzügen als Verifikation (dieser Bericht) | die Nachzüge `77c9a718`, `c7082ca4`, `33b0e7e1` hatten keinen Re-Review; gelesen und gefahren wurden sie hier |
| — | Closure-Notiz, Register, Risiken-Ausgänge, drei Paarungen | offen (zu Recht `[ ]`) | §7 „Wird bei der Closure gefüllt“; §6 trägt zwei Risiken „offen bis zur Closure“ |

## Review-Findings: Stand im HEAD

| ID | Urteil | Beleg |
|---|---|---|
| F-1 (Bedingung „vorgeladen“) | behoben | KDoc des Tests und Satz der Spezifikation nennen sie |
| F-2 (EMFILE ohne Träger) | teils | Register-Eintrag und §6 liegen im Zweig; der Folge-Slice liegt auf `main`, der Zustand des Registers im Zweig sagt noch „kein Folge-Slice angelegt“ (V-1) |
| F-3 (Haken vor Verifikation) | **offen** | sieben `[x]` bei Abgabe (V-2) |
| F-4 (Plan-Tabelle, `SPEC-048`) | teils | Kopf nennt `SPEC-048`; die Zeile zur Umbenennung zählt falsch (V-4) |
| F-5 (Rang-Zeiger) | behoben | KDoc von `AcceptChild` nennt das Register |
| F-6 (Rot-Fälle folgen den Testdaten, nicht `src/main`) | behoben, mit Kosten (V-5) | Stage `quellpakete`, Vergleich mit den Packages von `src/main`; Mutation „neues Package“ rot |
| F-7, F-8 | zur Kenntnis | `Error`-Naht im Test-KDoc und in der Spezifikation; Stabilisierung siehe V-3 |
| F-9 (Umgebung, Last) | belegt, Träger fehlt | 8 von 8 Läufe grün, Clean-Build grün; die Abhängigkeit von `sh` und `ulimit` steht nirgends dauerhaft (V-6) |

## Randbedingungen

| Bedingung | Urteil | Beleg |
|---|---|---|
| Kein Produktivcode im Diff | ja | `git diff 7bb31eb0..HEAD --stat -- src/main` leer; Änderungen nur in `src/test`, `config/detekt`, `tools`, `Dockerfile`, `spec`, `docs` |
| Gates nicht gelockert (`AGENTS.md` §3.6) | ja | `detekt.yml`: vier Einträge neu, keiner entfernt; `includes` unverändert; `tools/detekt-selbsttest.sh` prüft mehr (Zähler je Methode, Packages gegen `src/main`) |
| Suppression-Verbot | ja | kein `@Suppress` im Diff; `make detekt` im Clean-Build grün |
| Docker-only (§3.1) | ja | alle Läufe über `make` und `docker build`; die Schleifen und Mutationen als `docker build` auf Kopien |
| §3.8 | ja, eine Anmerkung | Aussagen tragen ihre Bedingung (Spezifikation, Test-KDoc, Kommentar der Sperre); „alle Überladungen“ ist am Rot-Fall mit fünf `log`-Aufrufen und zwei `printStackTrace`-Formen belegt |
| §3.7 | ja | Kommentare im Indikativ; `detekt.yml` und `tools/detekt-selbsttest.sh` nennen Grenze und Zeiger, keine Chronik |
| `Accepted`-ADR unberührt | ja | kein ADR im Diff; die Eingangsliste weicht vom Wortlaut der Entscheidung 1 von [ADR-0024](../plan/adr/0024-lokale-stempel-zwei-klassen.md) ab (V-5) |

## Die Änderung am Stempel-Werkzeug (`c7082ca4`, `77c9a718`)

* **Verschärfung, keine Lockerung.** `src/main` kommt in die Eingänge der Klasse `werkzeug` hinzu (`tools/stempel/werkzeug.eingaenge`), weil die neue Stage `quellpakete` es kopiert; die Regel von [ADR-0024](../plan/adr/0024-lokale-stempel-zwei-klassen.md) („jede Quelle eines `COPY` einer Stage der Klasse ist von einem Listeneintrag überdeckt“) verlangt den Eintrag, `make stempel-eingaenge-pruefung` ist ohne ihn rot. Es fällt kein Eintrag weg; ein Stempel gilt in weniger Fällen als vorher (§3.6 gewahrt, kein ADR nötig). Ohne den Eintrag wäre das Gegenteil eingetreten: ein neues Package unter `src/main` ließe einen passenden Stempel stehen, und `detekt-selbsttest` würde übersprungen (falsch grün, die gefährliche Richtung nach [ADR-0024](../plan/adr/0024-lokale-stempel-zwei-klassen.md)).
* **Kosten: ja, beabsichtigt, dokumentiert nur im Quelltext.** Jede Änderung unter `src/main` macht den Stempel der Klasse `werkzeug` ungültig; `make gates` fährt dann `baseline-verify`, `detekt-selbsttest`, `skript-selbsttest` und `kopplung-zahlen` mit (Fall „src/main geändert“ in `make stempel-gruppe`: `w t -`; früher nur `t`). Gemessen an einer Kopie mit Kommentarzeile in `src/main`: Stage `quellpakete` läuft neu (0,5 s), alle vier Schritte von `detekt-selbsttest` kommen aus dem Cache, weil der Inhalt der Liste gleich bleibt; die Läufe mit Cache kosten Sekunden, `make gates FORCE=1` insgesamt 64 s.
* **Selbsttests angepasst, nicht abgeschwächt.** `tools/stempel-eingaenge-selbsttest.sh`: der Fall „Fragment der Klasse werkzeug außerhalb der Einträge“ wandert von `src/main/neu.mk` nach `src/test/neu.mk` (der alte Pfad ist nun überdeckt; der Fall der Klasse `stempel` behält `src/main` und ist weiter rot); `tools/stempel-gruppe-selbsttest.sh`: aus einem Fall „src geändert“ werden zwei, `src/test` (nur `tests`) und `src/main` (`werkzeug` und `tests`). Beide Selbsttests laufen grün (95 und 67 Fälle), `make stempel-abbild` hält den Eintrag am Abbild (100 Fälle).
* **Kein stiller Weg, `detekt-selbsttest` zu überspringen.** Ein Überspringen setzt einen passenden Stempel voraus, und der enthält `src/main`; ohne Stempel (CI, `FORCE=1`, `make detekt-selbsttest` allein) läuft er immer. Der Selbsttest bricht, wenn die Liste `build/quellpakete` fehlt, wenn die Packages der Rot-Fälle von denen der Testdaten der Importregeln abweichen oder wenn ein Package von `src/main` keinen Rot-Fall hat (Mutation „neues Package“). Eine Restlücke, die nicht gemessen ist: die Liste nimmt nur Ordner direkt unter `io/github/pt9912/tdswire`; Quelltext an anderer Stelle von `src/main` fiele nicht auf (die Sperre selbst hat `includes` über den ganzen Baum).

## Plan-Tabelle gegen Diff

* Jede Zeile der Tabelle in Slice §3 findet ihre Dateien im Diff (Tests, Rot-Fälle, `detekt.yml`, Spezifikation, `tools/detekt-selbsttest.sh`, `AcceptChild.kt`, Umbenennung, Flake-Stabilisierung, `Dockerfile` und `werkzeug.eingaenge`).
* Die Zeile zur Umbenennung sagt „je drei neue Aufrufe der erweiterten Sperre“. Der Diff zeigt je Datei von `runtime`, `protocol` und `transport` **sechs neue Funktionen mit zehn Aufrufen** (`printStackTrace` mit `PrintStream` und mit `PrintWriter`, `Thread.dumpStack`, fünfmal `Logger.log`, `Logger.logp`, `Logger.logrb`), in der Summe 30 Aufrufe in den drei Dateien (V-4). Die fünf neuen Dateien (`domain`, `port`, `config`, `observability`, `simulation`) tragen alle 15 Einträge der Sperre.
* Nicht in der Tabelle, aber im Diff: `tools/stempel-eingaenge-selbsttest.sh`, `tools/stempel-gruppe-selbsttest.sh`, das Register `BEO-TDS/listener-annahmeschleife-steht-bei-erschoepftem-dateilimit-still` (drei Dateien), Link-Nachzug in Roadmap, Welle, zwei Slices in `done/` und einem Review-Report (nur Verweise auf den neuen Ort).

## Findings

| ID | Kategorie | Befund | Pfad | Klasse |
|---|---|---|---|---|
| V-1 | MEDIUM | Die Nutzerentscheide dieser Runde stehen im verifizierten HEAD nicht im Slice: §1 sagt noch, eine Zusage über `Error` aus der Senke „braucht eine Entscheidung des Nutzers“; der Entscheid zu den übrigen `Logger`-Methoden und die DoD-Zeile zum Flake fehlen. Sie liegen nur in der Fassung von `main` unter `open/` (Rename-Konflikt beim Merge, dazu Konflikte in Roadmap, Welle und einem Slice in `done/`). Der Zustand des Registers im Zweig („kein Folge-Slice angelegt, entscheidet der Nutzer“) widerspricht dem Folge-Slice auf `main` (`AGENTS.md` §3.7 Zustandsfelder). | Slice §1 und §2; `observations/BEO-TDS/listener-annahmeschleife-steht-bei-erschoepftem-dateilimit-still/state.md` | Entscheid nicht im Artefakt |
| V-2 | MEDIUM | Haken: drei Liefer-Punkte und vier Nachweis-Punkte stehen als `[x]` vor der Verifikation (Review F-3, unbehoben); davon ist „jeder neue Test trägt `@spec SPEC-<NNN>`“ falsch: ein neuer Test trägt nur `@spec` [TDS-FA-09](../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose). Der Reviewer-Skill legt das Setzen der Haken in die Hand des Planners. | Slice §2; `TelemetryWiringTest.kt` · „Eine Verbindung ohne Ausnahme des Handlers …“ | Haken vor der Verifikation gesetzt |
| V-3 | MEDIUM | Der Flake-Fix von `TelemetryRedactionTest` trägt keinen reproduzierten Fehler, den der Nutzerentscheid und das Register `BEO-TDS/flake-fix-ohne-reproduzierten-fehler` verlangen: ohne den Fix zwölf von zwölf Läufen grün (Last 16), auch mit 400 ms Verzögerung vor `LoginAccepted` im Produktcode. Die Begründung (`driver.run` sendet die Antwort, danach folgt `emit`) liegt im Code (`ConnectionWiring.kt`), die Mutation „Fix zurücknehmen“ überlebt. Der Slice nennt weder Lauf noch Meldung des beobachteten Fehlers. | Slice §3 (Zeile Stabilisierung); `TelemetryRedactionTest.kt` · `eventually("LOGIN_ACCEPTED")` | Flake-Fix ohne reproduzierten Fehler |
| V-4 | LOW | Plan-Tabelle gegen Diff: „je drei neue Aufrufe“ stimmt nicht (sechs Funktionen, zehn Aufrufe je Datei); zwei geänderte Skripte und das Register stehen nicht in der Tabelle. | Slice §3, Zeile Umbenennung | Plan-Tabelle nennt die berührten Dateien nicht |
| V-5 | LOW | Die Änderung der Eingänge von `werkzeug` ist eine Verschärfung, ihre Kosten (jeder `src/main`-Edit fährt die Klasse `werkzeug` mit) und ihr Grund stehen im Dockerfile, in `werkzeug.eingaenge` und im Kopf von `stempel-gruppe-selbsttest.sh`, nicht in einem Träger über dem Code: die Entscheidung 1 von [ADR-0024](../plan/adr/0024-lokale-stempel-zwei-klassen.md) (`Accepted`, unveränderlich) und die Zeilen von `make detekt-selbsttest` und `make gates` in `harness/README.md` nennen `src/main` nicht. | `harness/README.md` §Sensors; `docs/plan/adr/0024-…` Entscheidung 1 | Wirkung ohne Träger |
| V-6 | INFO | Grenzen und ihre Träger: (a) EMFILE, Register und Spezifikationssatz tragen sie, der Folge-Slice liegt auf `main` (V-1); (b) `Error`-Naht: Test-KDoc und Spezifikation, der Nutzerentscheid nur im Slice; (c) `observability` ohne Ordner: Kommentar in `tools/detekt-selbsttest.sh`; (d) Logger-Grenze: Kommentar in `detekt.yml`; (e) **Abhängigkeit von `sh`, `ulimit -n` (128 und 160) und den Zeitschranken (40 s, 60 s) steht nur im Quelltext von `AcceptChild`, in keinem Risiko des Slice und in keiner Doku**; nicht gemessen sind weitere Ausgabewege (`System.Logger`, `PrintStream.append`), die die Grenze der Sperre nur allgemein („nur die genannten Aufrufe“) deckt. | `AcceptChild.kt`; Slice §6 | Grenze ohne Träger (nur e) |

## Bedingungen vor der Closure

1. **V-1:** Nach dem Merge von `main` trägt der Slice in `done/` die Nutzerentscheide wörtlich (§1: `Error` als Auslöser genügt als Grenze, weitere `Logger`-Methoden ohne Sperre; DoD: Flake), und der Zustand des Registers nennt den Folge-Slice `slice-listener-annahmeschleife-bei-erschoepften-dateien`; die Konflikte in Roadmap, Welle und `done/` werden dabei aufgelöst, nicht übernommen.
2. **V-2:** der Test „Eine Verbindung ohne Ausnahme …“ bekommt `@spec SPEC-048` (oder die Zeile wird mit Begründung eingeschränkt); die Haken setzt der Planner nach diesem Bericht, nicht der Implementer.
3. **V-3:** entweder ein reproduzierter Fehler (Lauf, Meldung) im Slice und im Register, oder §7 und das Register nennen den Fix ausdrücklich „aus der Reihenfolge begründet, Wirkung nicht gemessen“ (Mutation überlebt); die DoD-Zeile ist dann mit dieser Einschränkung zu haken, nicht mit „reproduziert“.
4. Die Closure-Notiz in §7 trägt die Zuordnung *Test | Festlegung* und den Steering-Loop-Lerneintrag; beide Risiken in §6 bekommen einen Ausgang (Rot-Fall belegt die Konfiguration des `includes`, nicht jeden künftigen Aufruf; Test ohne Naht im Produktcode ist gelungen).
5. V-4 bis V-6 nachziehen oder in §7 als Grenze benennen (V-5: ein Satz in `harness/README.md` §Sensors; V-6e: Risiko in §6 mit Ausgang).

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `src/main` | unverändert, geprüft ohne Befund |
| `config/detekt/detekt.yml` | nur Verschärfung, geprüft ohne Befund |
| `spec/spezifikation.md` Abschnitt 4 | Sätze stimmen mit den Tests überein (vorgeladene Klassen, `Error` aus der Senke), ohne Befund |
| `tools/detekt-selbsttest.sh` | drei Mutationen rot, Frischlauf grün, ohne Befund |
| Stempel-Werkzeug | Verschärfung, vier Selbsttests grün, nur V-5 |
| Roadmap und Welle (Zweig) | Zustandsfelder zeigen den Slice unter `in-progress/`, Verweise lösen auf (`make docs-check` grün); nach dem Merge Konflikte (V-1) |

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 3 |
| LOW | 2 |
| INFO | 1 |

**Verdikt:** Closure nein, bis V-1 bis V-3 behoben oder als Grenze benannt sind; keine Änderung am Produktivcode nötig.
