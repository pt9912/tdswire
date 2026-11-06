# Review-Report: slice-betrieb-telemetrie-verdrahtung-belegen — 2026-11-06

**Review-Art:** Code (gegen Plan, ADR-0020, Hard Rules)

**Gegenstand:** Diff `652d5667..84edd7eb` (Zweig `worktree-agent-a735f10cebdeaf861`)

**Skill:** `.harness/skills/reviewer.md` @ 84edd7eb · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-06

**Eingangs-Kontext:**

- Slice `slice-betrieb-telemetrie-verdrahtung-belegen` (Plan §3, DoD, Rückführungen §4, Risiken §6)
- ADR-0020 (Frage 2 und 7), `TDS-FA-09`, `TDS-QA-03`, `TDS-QA-05`
- `AGENTS.md` §3.1 bis §3.8

**Selbst gefahrene Sensoren:** `make gates` (Exit 0; Stempel bestanden), `make gates FORCE=1` (Exit 0, 44 s; Stages aus dem Layer-Cache, also kein Frischlauf der Tests), Mutationen je in eigener Kopie des Baums mit `docker build --target test` (Gradle läuft alle Tests, 905) bzw. `--target detekt-selbsttest`.

| Mutation (gefahren) | Wirkung |
|---|---|
| M9 `ListenerEvent.Closed` liefert kein Ereignis | 3 rot: `TelemetryWiringTest` (CONNECTION_FAILED), `TelemetryRedactionTest` (Handlerfehler), dazu `ListenerAbortTest` |
| M10 `ListenerEvent.AcceptFailed` liefert kein Ereignis | 2 rot: `TelemetryWiringTest` (ACCEPT_FAILED), `TelemetryRedactionTest` (Betriebssystem) |
| Leck: `CONNECTION_FAILED.exceptionType` = Typname + `message` | 4 rot, darunter beide neuen Tests |
| Leck: `ACCEPT_FAILED.exceptionType` = Typname + `message` | 2 rot (Text „Too many open files“ erkannt) |
| `AcceptChild` ohne `preloadClasses()` | 2 rot: kein `ACCEPT_FAILED`; Kind-JVM meldet `NoClassDefFoundError` im `failed`-Handler von `TdsListener.acceptOne` |
| `includes` der Ausgabe-Sperre auf `{runtime,protocol,transport}` | `detekt-selbsttest` rot (`config/Aufruf.kt`, 0 Funde) |
| Eintrag `Logger.log` aus `detekt.yml` entfernt | `detekt-selbsttest` rot (Markierungen gegen Einträge) |
| Eintrag `Thread.dumpStack` entfernt | `detekt-selbsttest` rot |

## Findings

| # | kategorie | quelle | pfad | befund | verifizierbar | klasse |
|---|---|---|---|---|---|---|
| F-1 | MEDIUM | AGENTS.md §3.8 | `src/test/kotlin/io/github/pt9912/tdswire/runtime/TelemetryWiringTest.kt` · „Der Server nimmt nach dem Schließen der Verbindungen wieder an“; `spec/spezifikation.md` · „das Annehmen scheitert am Limit offener Dateien einer Kind-JVM“ | Test-KDoc und Spec nennen ACCEPT_FAILED und das Wiederannehmen ohne die gemessene Bedingung. Ohne `preloadClasses()` (gefahren) entsteht kein Ereignis: die JVM kann die Klasse `Accept.Failed` unter erschöpftem Limit nicht laden, der Test belegt ACCEPT_FAILED nur im Vorladen-Fall. | ja (Mutation oben) | Zusage ohne gemessene Bedingung im selben Satz |
| F-2 | MEDIUM | TDS-FA-09, Maintainability | `src/test/kotlin/io/github/pt9912/tdswire/runtime/AcceptChild.kt` · „`NoClassDefFoundError` (Beobachtung dieses Tests, siehe [AcceptChild])“ | Befund ohne Träger: bei erschöpftem Limit endet der `failed`-Callback von `TdsListener.acceptOne` mit `NoClassDefFoundError` (Thread `tdswire-listener-io-1`), die Continuation wird nicht fortgesetzt, die Annahmeschleife steht, kein Ereignis, keine Rückkehr. Weder Slice (§6, §3), Spec noch Register nennen das; nur ein Code-Kommentar, dessen Zeiger auf `[AcceptChild]` keine Beobachtung enthält. Ob das ein Produktfehler ist, entscheidet der Nutzer/Planner, es braucht aber einen Träger (Register oder Folge-Slice) und einen Risiko-Ausgang in §6. | ja (Mutation oben) | Beobachteter Produktbefund nur im Code-Kommentar |
| F-3 | MEDIUM | Reviewer-Skill (Haken vor Verifikation) | `docs/plan/planning/in-progress/slice-betrieb-telemetrie-verdrahtung-belegen.md` · „- [x] **Verdrahtung von `ACCEPT_FAILED` und `CONNECTION_FAILED`**“ | Die drei Liefer-Punkte und drei Nachweis-Punkte (`@spec`, Aussagen, `make gates`, Doku-Update) stehen als `- [x]`, bevor Review und Verifikation abgeschlossen sind; der Review-Haken ist korrekt offen. | ja | Haken vor der Verifikation gesetzt |
| F-4 | MEDIUM | Reviewer-Skill (Plan-Tabelle) | Slice · „**Berührte Spec-Stellen:** SPEC-059 · SPEC-041 · TDS-FA-09.a“ und Tabelle §3 | Die neuen Tests tragen `@spec SPEC-048` (Ausnahmetext nicht im Ereignis), `SPEC-048` fehlt im Kopf. Tabelle §3 führt die drei vorhandenen `Aufruf.kt` (runtime, protocol, transport: Umbenennung `Aufruf` in `AusgabeAufruf`, drei neue Aufrufe) als „neu“ nicht auf; die Stabilisierung `eventually("LOGIN_ACCEPTED")` in `TelemetryRedactionTest` steht nicht in „Umsetzung“. | ja (Diff-Abgleich) | Plan-Tabelle nennt berührte Stellen nicht |
| F-5 | LOW | AGENTS.md §3.7 | `AcceptChild.kt` · „(Beobachtung dieses Tests, siehe [AcceptChild])“ | Der Rang-Zeiger zeigt auf die umschließende Datei, deren KDoc die Beobachtung nicht trägt; der Leser findet nichts. | nein | Kommentar-Zeiger ohne Ziel |
| F-6 | LOW | Maintainability | `tools/detekt-selbsttest.sh` · `pakete_regeln=$(ls "$td" | sort | tr '\n' ' ')` | Die Packages der Rot-Fälle folgen den Testdaten der Importregeln, nicht `src/main`: `observability` hat einen Rot-Fall, aber kein Verzeichnis im Hauptmodul; ein neues Package unter `src/main` verlangt keinen Rot-Fall. Der Beleg „jedes Package des Hauptmoduls“ trägt damit nur für die Packages der Architekturtabelle (Grenze nicht benannt). | ja | Package-Ableitung aus Testdaten statt Quelle |
| F-7 | INFO | Maintainability | `TelemetryWiringTest.kt` · `ThrowsAfterLogin` | CONNECTION_FAILED entsteht im Test nur über einen `Error` aus der Senke (Backend und Adapter bilden jede `Exception` auf eine Antwort ab; ein anderer Auslöser ist nicht gefunden). Das ist eine Naht des Tests, kein Produktcode; die Grenze steht in Test-KDoc, Slice §1 und der Spec („der Handler endet mit einer Ausnahme der Klasse `Error` aus der Senke“). Die Formulierung des KDoc von `TelemetrySink` („Meldestellen in der Infrastruktur des Listeners: weder gemessen“) betrifft den `Error` bei der Meldung selbst und bleibt wahr; die Spur von `Error` aus der Meldestelle des Handlers ist mit diesem Test gemessen. Kein Finding, ehrlich begrenzt. | nein | — |
| F-8 | INFO | Maintainability | `TelemetryRedactionTest.kt` · `eventually("LOGIN_ACCEPTED")` | Stabilisierung außerhalb des Auftrags; zulässig (nur Test, schließt einen echten Wettlauf: `loginAndSend` wartet nicht auf das Ereignis), vor `server.stop()`. In F-4 als Planungslücke vermerkt. | nein | — |
| F-9 | INFO | Verifier | `AcceptChild.kt`, `TelemetryWiringTest.kt` | Umgebungsabhängigkeit: `sh`, `ulimit -n 128/160`, `IOException` als Typname, Zeitschranken 40 s und 60 s. In drei Läufen (M9, Leck 1, Leck 2) grün, Dauer 5 bis 50 s; bei Last nicht gemessen. Für die DoD-Frage der Stabilität ist der Verifier zuständig. | nein | — |

## Geprüft, ohne Befund

- `config/detekt/detekt.yml`: nur Verschärfung (vier neue Einträge, kein entfernter Wert; `git diff` zählt 0 gelöschte `value`-Zeilen), §3.6 gewahrt; Kommentar im Indikativ, nennt die Grenze (nicht gesperrte Stufenmethoden).
- `config/detekt/selbsttest-aufrufe/`: je Package ein Rot-Fall, Zählung gegen `// erwartet:` greift (Mutationen oben); `Aufruf` zu `AusgabeAufruf` vermeidet die Namenskollision mit dem Kern-Objekt im selben Source-Set.
- `tools/detekt-selbsttest.sh`: POSIX-sh in Docker-Stage `detekt-selbsttest`, kein Gradle/JDK auf dem Host (§3.1 gewahrt).
- Suppression-Verbot: kein `@Suppress`, `noqa`, `nolint` im Diff.
- `TelemetryRedactionTest`: Marken in Benutzername, Kennwort und Ausnahmetext; Leck-Mutationen werden erkannt (TDS-QA-03). Der Fall ACCEPT_FAILED trägt keine Eingabe außer dem Text der Betriebssystem-Ausnahme und sagt das im KDoc.
- `spec/spezifikation.md` Abschnitt 4: Sätze „am Server belegt“ und „belegt in `TelemetryWiringTest`“ stimmen mit den Tests überein; weitere Fundstellen (`docs/user`, `harness/README.md`, Matrix) nennen die alte Lücke von `Logger.log` nicht.
- `docs/plan/planning/` (Roadmap, Welle, done-Slices): nur Link-Nachzug auf `in-progress/`.
- Parallele Zweige: der Log-Adapter nutzt dieselben Ereignisarten; keine Änderung an `EventKind` oder Feldern, keine Kopplung berührt.
- Kein Test ersetzt für `make clean-build` einen Frischlauf in diesem Review; das Ergebnis des Implementers ist nicht nachvollzogen (Cache-Stages).
