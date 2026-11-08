# Verifikationsbericht: slice-listener-annahmeschleife-bei-erschoepften-dateien (HEAD 07cf015b)

* Rolle: Verifier (Modul 11), frischer Kontext. Gegenstand: Slice `slice-listener-annahmeschleife-bei-erschoepften-dateien` (`in-progress/`, Zweig `worktree-agent-a73e777d8804566f6`, HEAD `07cf015b`; Slice-Anteil gegen `61551df2`, Basis älter als `main` `66fee6c1`). Vorgänger: Review `2026-11-08-5400b4e3-review-slice-listener-annahmeschleife-bei-erschoepften-dateien.md` (0 HIGH/MEDIUM, F-1 und F-2 LOW, F-3 bis F-7 INFO); F-1 und F-2 behoben in `07cf015b`.
* Maßstab: DoD §2 und Plan §3 des Slice, [ADR-0043](../plan/adr/0043-annahmeschleife-bei-erschoepftem-dateilimit.md) (auf `main` `Accepted`, Option A; im Zweig noch `Proposed`, der Zweig ändert die Datei nicht, die Merge-Probe liefert `Accepted`), [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-FA-09](../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), `spec/spezifikation.md` Abschnitt 4 (`ACCEPT_FAILED`), `AGENTS.md` §3.7 und §3.8.
* Verifiziert wird die Zusage, nicht der Diff; Belege sind eigene Läufe. Am Produktivcode nichts geändert. Mutationen liefen in Wegwerf-Kopien (`git archive HEAD`, Scratchpad `verifier/m1`, `m2`, `m3`), nicht im Arbeitsbaum.

## Eigene Läufe (Belege)

Host: Lastmittel 8 bis 277 durch fremde Läufe, Einzelmessungen, kein ruhiger Host.

| Lauf | Ergebnis |
|---|---|
| Merge-Probe `git merge-tree --write-tree HEAD main` (`main` = `66fee6c1`) | sauber, keine Konflikte |
| `make clean-build` (einmal, auf `07cf015b`) | Exit 0, 13 min 57 s; `:test` BUILD SUCCESSFUL in 6 min 34 s, 1230 `PASSED`, 0 `FAILED`; darin `TelemetryWiringTest > Ein gescheitertes Annehmen ergibt ACCEPT_FAILED und der Server nimmt danach wieder an()` PASSED, `TelemetryRedactionTest > Der Text der Ausnahme des Betriebssystems erreicht ACCEPT_FAILED nicht()` PASSED, alle fünf `ListenerAbortTest` PASSED (kein Flake dieses Laufs) |
| `make gates FORCE=1` | Exit 0, 32 s. **Cache-Falle:** Docker-Stufen aus dem Layer-Cache des vorigen `clean-build`; der Lauf belegt die Verdrahtung, den Frischbeleg liefert `clean-build` |
| `make flaeche` | Exit 0 (Selbsttest und Prüfung der Stage) |
| `make docs-check` | Exit 0, 1256 Dateien, 0 Befunde (vor Anlage dieses Berichts) |
| `make matrix-zitate` | Exit 0, 1230 bestandene Testfälle, 309 Zitate, 0 ohne PASSED (10 min 44 s) |
| `make e2e`, Lauf 1 | Exit 2: Stufen 1 bis 6 grün, **Stufe 7 rot**: `SynchronitaetTests.Cancel_und_Command_Timeout_kurz_hintereinander_lassen_die_Folgeabfrage_nur_ihre_Antwort_erhalten` (`Expected: 2, Actual: 0`, Runde 3 `Execution Timeout Expired` statt Cancel), Lastmittel zu dem Zeitpunkt 182 |
| `make e2e`, Lauf 2 (Wiederholung) | Exit 0, 8 Stufen grün, 153 s (Stufe 7: 37 s), Lastmittel 28 bis 67 |
| Mutationen M1 bis M3 (unten) | alle rot, mit der neuen Meldung |

## Einordnung des roten e2e-Laufs

Der Diff berührt weder `referenztest-dotnet/`, `testserver/` noch `tools/` (`git diff 61551df2 HEAD --stat` zeigt nur `src/main` (3 Dateien), `src/test` (2), `spec/`, `docs/`). Der Test ist ein Wanduhr-Test (Cancel 300 ms vor dem Command-Timeout von 1 s, Rennen von Cancel und Timeout) und steht in der Evidence von `BEO-TDS/wanduhr-tests-flackern-unter-last` (`evidence/slice-schreibpfad-merge-output.md`). Lauf 2 grün, und der frühere Verifier fand 9 von 9 grün; die Ursache ist unter Last 182 plausibel, aber nicht gemessen: **Einordnung: bekannter Last-Flake, kein Befund dieses Slice, kein Beleg gegen ihn.** Der Auftritt ist als weitere Evidence des Registers nachzutragen (Planner, Abschnitt Bedingungen).

Zum Risiko §6 „Last-Flake des Kind-JVM-Tests“: In keinem meiner Läufe (clean-build bei wechselnder Last, drei Mutationsbauten bei Last bis 277, Baseline darin) ist ein Kind-JVM-Test oder `ListenerAbortTest` rot geworden, wo er nicht rot sein sollte. Der vom Implementer gesehene rote `ListenerAbortTest … CANCEL_TIMEOUT` unter Last 19 bis 24 ist ein Wanduhr-Test derselben Beobachtung, nicht im Diff (die Dateien `ListenerAbort*` sind unverändert); im Frischlauf grün. Der Ausgang für §6: **weiter offen** im Sinne der Beobachtung (nicht ausgeschlossen, bei einem roten Auftritt unter Last 182 an anderer Stelle gesehen), am Kind-JVM-Test selbst **nicht eingetreten** (Vorschlag, Wortlaut Planner).

## Mutationen (selbst gefahren, Rotbeleg der neuen Testmeldung)

Wegwerf-Kopien von `07cf015b`, `docker build --target test` mit `--tests TelemetryWiringTest --tests TelemetryRedactionTest`, drei Bauten gleichzeitig.

| Nr. | Mutation | Ergebnis |
|---|---|---|
| M1 | Aufruf `prepareAcceptFailure()` in `Bootstrap.events()` entfernt | rot (3 min): `TelemetryRedactionTest` „Limit 128: kein ACCEPT_FAILED“; `TelemetryWiringTest`: `kein ACCEPT_FAILED, fehlt: [Exception in thread "DefaultDispatcher-worker-3" java.lang.NoClassDefFoundError: io/github/pt9912/tdswire/domain/telemetry/AcceptFailed]; …` |
| M2 | `check(Accept.Failed(name).failure == name)` aus `prepareExhaustion` entfernt | rot (3 min): beide Klassen „kein ACCEPT_FAILED“; Meldung `fehlt: [Exception in thread "tdswire-listener-io-2" java.lang.NoClassDefFoundError: io/github/pt9912/tdswire/transport/listener/TdsListener$Accept$Failed]` |
| M3 | `ChannelResult.Failed`/`ChannelError` und `ChannelResult.Ok` aus `prepareExhaustion` entfernt | rot (6 min 48 s): nur `TelemetryWiringTest`, `der Server nimmt nach dem Schließen nicht mehr an: […]`; die Ausgabe darin enthält `NoClassDefFoundError: io/github/pt9912/tdswire/transport/channel/ChannelResult$Ok`. `TelemetryRedactionTest` bleibt grün (liest nur `ACCEPT_FAILED`), wie im KDoc „je einzeln gesehen“ |

Die neue Meldung `fehlt: [...]` nennt in M1 und M2 die fehlende Klasse im Kopf der Assertion. Sie gilt nur für die Assertion „kein ACCEPT_FAILED“; bei der Assertion „nimmt nicht mehr an“ (M3) steht die Klasse nur in der angehängten Ausgabe, und diese Meldung ist ein einziger Text von rund 400 KB (ungekürzte `child.output`, anders als die auf `DIAGNOSTIC_CHARS` gekürzte Form darüber): V-1.

## DoD-Punkte

| # | DoD-Punkt | Urteil | Beleg |
|---|---|---|---|
| 1 | Annahmeschleife bei erschöpftem Limit: Kind-JVM-Test **ohne** Vorladen liest `ACCEPT_FAILED`, danach nimmt der Server wieder an; Mutation macht rot | **erfüllt** | `AcceptChild.kt`: `preloadClasses()` und sein Aufruf entfernt; `TelemetryWiringTest` (128 Dateien: `ACCEPT_FAILED` mit `ExceptionType=java.io.IOException`, `serves(port)` nach dem Schließen) und `TelemetryRedactionTest` (128 und 160) grün im Frischlauf; M1, M2, M3 rot. Weg: [ADR-0043](../plan/adr/0043-annahmeschleife-bei-erschoepftem-dateilimit.md) Option A (Vorbereitung beim Start), ohne Rückfall auf B. Die Begründung des Wegs in der Closure-Notiz steht aus (N4) |
| 2 | Spezifikation und Test-Naht: Satz zu `ACCEPT_FAILED` nennt die gemessene Bedingung; Vorladen weiter getragen → KDoc | **erfüllt** | Abschnitt 4 nennt Kind-JVM aus Klassenverzeichnissen, ohne Vorladen, 128 und 160, den Start-Weg, Wiederannehmen bei 128, den Lesepfad nur an einer Stelle, Nicht-Gemessenes (andere Umgebungen, Jar, andere Callbacks, Senke des Embedders). Das Vorladen entfällt, daher kein KDoc „was der Test nicht belegt“ nötig; `AcceptChild`-KDoc sagt „lädt keine Klassen vor“ und grenzt die Senke dieser Datei ab |
| N1 | `make gates` grün | **erfüllt** | `FORCE=1` Exit 0 (aus dem Cache); Frischlauf `make clean-build` Exit 0, 1230 `PASSED` |
| N2 | Review liegt vor | **erfüllt** | Report `2026-11-08-5400b4e3-review-…`; F-1, F-2 in `07cf015b` behoben (unten), F-7 beim Planner. Die Behebung hat keinen zweiten Review; sie ist klein, ich habe sie hier nachvollzogen und mutiert |
| N3 | Doku-Update bei Vertrag | **erfüllt** | Abschnitt 4 der Spezifikation; `docs/user/` (Reviewer: keine Fundstelle widerspricht) |
| N4 | Closure-Notiz mit Steering-Loop-Lerneintrag | offen, erwartet | §7 „Wird bei der Closure gefüllt“ |
| N5 | Register fortgeschrieben | offen, erwartet | im Zweig nicht berührt; siehe Bedingungen |
| N6 | Risiken §6 mit Ausgang | offen, erwartet | beide Risiken „offen bis zur Closure“; Vorschläge unten |
| N7 | Drei Paarungen | offen bis Welle-Closure | |
| — | **Haken** | **nicht gesetzt (richtig)** | `grep '\[x\]'` im Slice: 0 Treffer, auch im Diff gegen `61551df2` keiner; ich setze keine. Die Haken setzt der Planner |

## Aussage gegen Beleg (§3.8) im Spec-Satz

| Aussage | Beleg | Urteil |
|---|---|---|
| „Kind-JVM aus Klassenverzeichnissen, ohne Vorladen“ | `AcceptChild` nutzt den Klassenpfad der Test-JVM; kein `preloadClasses` mehr | gedeckt |
| „Limit von 128 und 160 Dateien“ (`ACCEPT_FAILED`) | `TelemetryRedactionTest` Zeile 330 `listOf(128, 160)`, `TelemetryWiringTest` `DESCRIPTORS = 128`; beide grün | gedeckt |
| „Wiederannehmen (128 Dateien)“ | `assertTrue(serves(port))` nur im Wiring-Test mit 128; 160 trägt es nicht und der Satz sagt es auch nicht | gedeckt |
| Start bereitet Fehlerpfad, Weg bis zum Ereignis und `ChannelResult`/`ChannelError`/`ChannelFailure` vor | M1, M2, M3 (Reviewer M4a, M4b zusätzlich); `ChannelFailure` wird in `prepareExhaustion` über `IO_FAILURE` berührt, einzeln nicht mutiert (V-2) | gedeckt, `ChannelFailure` nur im Verbund |
| „Lesepfad nur an dieser Stelle belegt, nicht mit laufender Anfrage, Schreiben oder Framing“ | der Test hält idle Verbindungen, der Client schließt; kein Test mit Anfrage unter erschöpftem Limit | Aussage ist eine Einschränkung, gedeckt |
| „Nicht gemessen: andere Umgebungen, Jar, andere Callbacks, Senke des Embedders“ | [ADR-0043](../plan/adr/0043-annahmeschleife-bei-erschoepftem-dateilimit.md) und Slice §1 | gedeckt |

F-1 (Aussage breiter als vorbereitete Klassen) ist in `07cf015b` behoben: der Satz nennt jetzt die Ergebniswerte des Lesens statt „den Lesepfad der Verbindungen“. Keine Fundstelle des alten Wortlauts mehr (`grep "Lesepfad der Verbindungen"` über `spec/`, `docs/`, `src/`: ohne Treffer). F-2 behoben: `ListenerEvent.AcceptFailed` steht nur noch in `prepareAcceptFailure`; M-Lauf M1 zeigt, dass diese Zeile Träger ist (rot beim Entfernen des Aufrufs), die Doppelung ist weg.

## Plan-vs-Code-Diff (§3)

| Plan-Zeile | Diff | Urteil |
|---|---|---|
| `TdsListener.kt`: `prepareExhaustion` baut Werte der `failed`-Klassen und `ChannelResult` vor dem ersten Annehmen | `prepareExhaustion()` in `launched` vor `launchAcceptLoop()`, +21 Zeilen | deckt sich |
| `TelemetryEvents.kt`, `Bootstrap.kt`: `prepareAcceptFailure` einmal, Aufruf vor dem Start des Listeners | `prepareAcceptFailure()` (internal), Aufruf in `events()` (läuft vor Bindung und Start) | deckt sich |
| `AcceptChild.kt`: Kind-JVM ohne Vorladen | `preloadClasses` entfernt, KDoc angepasst | deckt sich |
| `TelemetryWiringTest.kt`: Test des Wiederannehmens ohne Vorladen | Test bleibt, KDoc ersetzt, Meldung mit `missingClasses` | deckt sich |
| `spec/spezifikation.md` Abschnitt 4 | ein Absatz, zwei Fassungen (`5400b4e3`, `07cf015b`) | deckt sich |

Nicht in der Tabelle, im Diff: `docs/plan/planning/in-progress/roadmap.md` (Planungsdokument, Zustand „In Arbeit“), `docs/reviews/…` (Review), der Slice selbst (die Plan-Tabelle wurde vom Implementer um die zweite Zeile ergänzt und auf Weg A festgelegt; der Slice-Text erlaubt es: „Weg: Architect“). Keine Datei ohne Zeile, keine Zeile ohne Datei. Keine neue öffentliche Fläche: beide neuen Funktionen sind `internal`, `make flaeche` Exit 0. Keine `@Suppress`, kein Host-Gradle (Gates grün).

**Zustandsfeld im Plan:** Slice §3, Schlusssatz („entstehen mit dem Zweig `worktree-agent-a735f10cebdeaf861` und liegen erst nach dessen Merge auf `main`“) beschreibt einen vergangenen Zustand (Review F-7, Planner), unverändert.

## Lifecycle, Roadmap, Register

* Lifecycle: Slice liegt in `in-progress/`, Verweise nachgezogen (vier `slice-mv`-Commits, reine Moves); Trigger Start („`slice-betrieb-telemetrie-verdrahtung-belegen` in `done/`“) erfüllt. Verantwortlicher im Slice noch „— bis zur Priorisierung“ (Trigger Start nennt „ein Verantwortlicher ist eingetragen“): V-3.
* Roadmap: „Offene Wellen“ und Zeile `welle-betrieb-diagnose` nennen den Slice unter `in-progress/` mit auflösbarem Anker, ohne Chronik. Der Ruhe-Marker (Zeile 13) folgt dem Verzeichnis; Merge gegen `main` ohne Konflikt.
* Register: `BEO-TDS/listener-annahmeschleife-steht-bei-erschoepftem-dateilimit-still` steht auf „offen — 1×, Folge-Slice … (`open/`, nicht gestartet)“, das stimmt nach dem Start nicht mehr und ist bei der Closure fortzuschreiben (Stand „verkörpert“ mit Anker auf [ADR-0043](../plan/adr/0043-annahmeschleife-bei-erschoepftem-dateilimit.md) und die Tests; die Verkörperung ist `prepareAcceptFailure`/`prepareExhaustion`, kein `liegt in`-Ziel mit `seit slice-…`-Anker im Produktcode: Planner entscheidet, ob ein Anker nötig ist). `BEO-TDS/test-naht-im-produktivcode` (Stand „offen“, zwei Evidence-Dateien): mit diesem Slice eine dritte → die 3×-Schwelle ist erreicht, die Frage der Verkörperung stellt sich dem Planner; der Slice ist hier ein anderer Fall (Vorbereitung hat Produktnutzen, F-3), das ist vom Planner zu bewerten, nicht von mir.

## Risiken §6 (Vorschläge für die Ausgänge)

1. Test an `sh`, `ulimit -n` und Zeitschranken 40 s/60 s, unter Last ungemessen: **Vorschlag: eingetreten als Grenze nicht festgestellt, weiter offen.** Belegt grün unter Last 8 bis 277 (Frischlauf, drei Mutationsbauten mit Baseline, e2e Wiederholung); rot waren zwei andere Wanduhr-Tests (`ListenerAbortTest`, vom Implementer; `Cancel_und_Command_Timeout…`, von mir), beide in `BEO-TDS/wanduhr-tests-flackern-unter-last`.
2. Naht im Produktcode (`BEO-TDS/test-naht-im-produktivcode`): **Vorschlag: eingetreten**: `prepareAcceptFailure` und `prepareExhaustion` stehen im Produktcode; sie sind als akzeptiertes Negativ in [ADR-0043](../plan/adr/0043-annahmeschleife-bei-erschoepftem-dateilimit.md) benannt, haben Produktnutzen (Verfügbarkeit nach Ressourcenmangel) und keine neue Fläche.

## Findings

### V-1 INFO — Meldung „nimmt nicht mehr an“ ungekürzt (rund 400 KB)

* quelle: `AGENTS.md` §3.7 (Lesbarkeit für den, der die Stelle ändert); Rotbeleg M3
* pfad: `src/test/kotlin/io/github/pt9912/tdswire/runtime/TelemetryWiringTest.kt` · `assertTrue(serves(port), "der Server nimmt nach dem Schließen nicht mehr an: ${child.output}")`
* befund: Die zweite Meldung hängt `child.output` ungekürzt an (im roten M3-Lauf eine Zeile von rund 400 KB), die erste kürzt auf `DIAGNOSTIC_CHARS` und nennt `missingClasses`. Wer M3 liest, findet die Klasse nur durch Suchen. Kein Schaden für Grün; Wirkung im Rot.
* klasse: Diagnose-Meldung ungleich zwischen zwei Assertions

### V-2 INFO — `ChannelFailure` nur im Verbund mit `ChannelResult` belegt

* pfad: `TdsListener.kt` · `prepareExhaustion`
* befund: Die Spec nennt `ChannelFailure` als vorbereitet. Die Mutationen entfernen die Gruppe (`ChannelResult.Failed`, `ChannelError`, `ChannelFailure.IO_FAILURE`) zusammen (M3) oder `Ok` allein (Reviewer M4a); `ChannelFailure` einzeln habe ich nicht mutiert, und der Test belegt ihn vermutlich erst, wenn der Lesepfad `Failed` erzeugt. Die Aussage ist nicht breiter als die Gruppe („Ergebniswerte des Lesens“), aber eine einzelne Nennung in Klammern. Wortlaut „je einzeln gesehen“ im KDoc nennt Gruppen, nicht Klassen.
* klasse: Feinheit der Aussage über die Klassenliste

### V-3 INFO — Verantwortlicher nicht eingetragen

* quelle: Slice §4 Start-Trigger („ein Verantwortlicher ist eingetragen“), Kopf des Slice „Verantwortlich: — bis zur Priorisierung.“
* befund: Der Start-Trigger verlangt einen Verantwortlichen; der Kopf trägt keinen. Formales Zustandsfeld, Sache des Planners (Eintrag oder Trigger-Anpassung bei der Closure).
* klasse: Zustandsfeld vs Trigger

### V-4 INFO — [ADR-0043](../plan/adr/0043-annahmeschleife-bei-erschoepftem-dateilimit.md) im Zweig `Proposed`

* befund: Im Zweig steht [ADR-0043](../plan/adr/0043-annahmeschleife-bei-erschoepftem-dateilimit.md) auf `Proposed`; auf `main` ist sie `Accepted` (und im ADR-Index). Die Merge-Probe liefert `Accepted`; die Closure darf sich nicht auf die Zweigfassung stützen. Kein Fehler des Zweigs.
* klasse: Basis älter als `main`

Kein HIGH, kein MEDIUM, kein LOW.

## Gesamturteil

**Closure-fähig: ja, unter den Bedingungen 1 bis 5.** Beide Liefer-Punkte sind in eigenen Läufen belegt: Frischlauf aller Tests grün, Gates grün, die drei Mutationen röten die Tests mit der neuen Meldung, die Spec-Aussage trägt nicht weiter als der Beleg, es gibt keine neue öffentliche Fläche, der Plan deckt den Diff. Der rote e2e-Lauf 1 ist ein bekannter Wanduhr-Flake (Stufe 7, nicht im Diff), Wiederholung grün.

## Bedingungen für die Planner-Closure

1. **Closure-Notiz in §7** mit Zuordnung *Test | Festlegung* (Test: Kind-JVM-Test ohne Vorladen, M1 bis M3; Festlegung: Weg A nach [ADR-0043](../plan/adr/0043-annahmeschleife-bei-erschoepftem-dateilimit.md) mit Grund, Erweiterung um Domänenereignis und `ChannelResult` im Wortlaut der ADR „bestimmt der Test“), den Findings-Klassen des Reviews und dieses Berichts, und dem Steering-Loop-Lerneintrag; ein `liegt in` nur, wenn etwas verkörpert wurde (dann mit Anker `seit slice-…`).
2. **Haken** der Liefer-Punkte 1 und 2 und der Nachweis-Pflichten setzt der Planner nach Prüfung dieses Berichts (keine sind gesetzt).
3. **Ausgang beider Risiken in §6** (Vorschläge oben).
4. **Register fortschreiben:** `listener-annahmeschleife-steht-bei-erschoepftem-dateilimit-still` (Stand und Folge-Slice-Zeile veraltet), `test-naht-im-produktivcode` (dritte Evidence → Schwelle), `wanduhr-tests-flackern-unter-last` (Auftritt von `Cancel_und_Command_Timeout…` in Stufe 7 bei Last 182, Wiederholung grün, Beleg: dieser Bericht).
5. **Planner-Fassungsfeld:** Slice §3 Schlusssatz (Review F-7) und Verantwortlicher (V-3); nach `git mv` nach `done/` die Pfade nach Ruheort-Regel; Merge gegen `main` ist sauber, der Roadmap-Ruhe-Marker (Zeile „Offene Wellen“: `Nichts in Arbeit.` auf `main`) ist bei der Closure auf den Zustand nach `done/` zu setzen.

Gesamtlaufzeit dieser Verifikation rund 1 h 10 min; größte Posten: `make clean-build` 14 min, `make matrix-zitate` 10 min 44 s, die drei Mutationsbauten (M3 6 min 48 s, parallel zu M2) etwa 10 min zusammen, `make e2e` 7 min + 2,5 min Wiederholung, Warten auf Last.
