# Welle welle-produktschnitt — Bibliothek als Produkt, Testserver nur zum Testen (Umbau nach ADR-0025) — Closure-Notiz

> **Zitier-Form** *(bleibt stehen — Norm, kein Ausfüll-Hinweis).* Dieses
> Artefakt friert ein; was es zitiert, bewegt sich weiter. Deshalb: **Kennung,
> nicht Adresse** — `slice-<Kennung>` statt seines Lifecycle-Pfads, `make <target>`
> statt eines Links auf die Sensor-Datei, eine Baseline-Stelle als
> `v<X.Y.Z>` · `regelwerk/<datei>.md` §<Abschnitt> statt als Link
> (Baseline-Regelwerk `grundlagen-harness-dateien.md` §harness/README.md als
> Einstiegspunkt — beim Ausfüllen mit dem adoptierten Tag schreiben).

**Welle:** welle-produktschnitt
**Abschluss:** 2026-11-07
**Verantwortlich:** pt9912

## Was wurde geliefert?

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 — *was gelernt wurde*: geliefert · was
funktionierte · was anders lief. Mit ID-Bezug, wo es einen gibt.

Alle vier Slices aus §4 der Welle-Datei liegen in `done/` (am Dateisystem geprüft, nicht aus den Slice-Notizen übernommen). Die Aussage der Welle steht an drei Belegen, die diese Closure am heutigen Stand von `main` neu gefahren hat (Abschnitt „Verifikation“): dem Übersetzungslauf des Moduls `testserver` (ein Zugriff auf `internal` bricht ihn), der Manifest-Prüfung im Bau (kein `Main-Class`-Eintrag, kein Jackson in `lib/`) und einem grünen `make e2e` in acht Stufen. Sie gilt für das Bibliotheks-Jar, das Gradle-Modul `testserver` und die Skripte, das `Dockerfile` und die Dokumente, die der Welle-Plan nennt. Sie gilt nicht für Java-Quellen oder Reflection im Modul `testserver` (akzeptiertes Negativ der [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md)), nicht für ein Betriebs-Image (es gibt keines) und nicht für ein fremdes Programm, das das Jar einbindet (ungemessen).

- `slice-spec-nachzug-produktschnitt`: Spezifikation auf den Produktschnitt nachgezogen (`SPEC-052`, `SPEC-005`, `SPEC-010`, `SPEC-023`, `SPEC-035`, `SPEC-040`, `SPEC-059`), mit der Öffentlichkeit von `SimulationBackend` und `SimCatalog` ([ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md)); 14 Zielzustand-Stellen gegen den gebauten Stand verifiziert; [TDS-RB-01](../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose).
- `slice-testserver-herausloesen`: Testserver aus der Bibliothek herausgelöst, `simulationFile`, Lader, YAML-Leser und Jackson verlassen `src/main`, die Test-Einstiege laufen über `TdsServer.start`; Jackson-Sperre `tools/jackson-sperre.sh`; die Skript-Prüfungen des Bibliotheks-Logs herausgenommen und gekennzeichnet; [TDS-RB-01](../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose).
- `slice-jar-ohne-main-und-image`: Bibliotheks-Jar ohne `Main-Class`, Manifest-Prüfung `tools/manifest-pruefung.sh` mit Selbsttest in `build-jar`, Stages `runtime` und `build` samt Pins entfernt, `make laufzeittest` fährt den Testserver-Container; [TDS-RB-01](../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit).
- `slice-explicit-api-umbau`: `explicitApi()` strict für `src/main`, Fläche auf die Namen von [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) Entscheidung 3 mit [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md) verkleinert (damals 61 öffentliche Klassen, vom Verifier aus den `public` gezählt); [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-RB-04](../../../../spec/lastenheft.md#tds-rb-04--externes-backend).

**Seit den Slice-Closures überholt (Stand `main` zu dieser Closure; die Slices selbst bleiben unverändert).** Die Welle hat einen Schnitt gebaut, auf dem spätere Slices weitergebaut haben; der Wortlaut der Aussagen unten folgt dem heutigen Stand:

- Das Log der Bibliothek ist gebaut ([ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md)): Senke und Parameter `telemetry` von `TdsServer.start`, JSON-Adapter des Testservers, Prozesstests; die Zustandsereignisse folgten in `slice-betrieb-zustandsereignisse`. Die Welle-Datei sagt in §1 und §3, bis `slice-betrieb-log-adapter-testserver` in `done/` liege, lese kein E2E-Lauf Zeilen der Bibliothek; der Slice liegt in `done/`, und die Skripte prüfen wieder Ereignisse (Abschnitt „Verlust beim Log“).
- Der Testserver ist ein Gradle-Modul `testserver` ([ADR-0034](../../adr/0034-testserver-als-gradle-modul.md), `slice-testserver-gradle-modul`), kein Source-Set; die Welle-Datei nennt das Modul bereits.
- Die öffentliche Fläche trägt einen Sensor, den `slice-explicit-api-umbau` noch als fehlend führte: `make flaeche` hält die Typnamen der Liste in `SPEC-059` gegen die Deklarationen (`slice-oeffentliche-flaeche-verkleinern-und-belegen`). Er vergleicht keine Mitglieder.
- Stufe 8 von `make e2e` (Referenzclient gegen ein eigenes Backend, `slice-e2e-backend-ausserhalb-modul`) ist neu; der Wortlaut „Stufen 1 bis 3 und 5 bis 7“ in §1 der Welle-Datei zählt sie nicht, die Schlussfolgerung (Stufen gegen den Serverprozess laufen gegen den Testserver) trägt weiter.
- Die Nutzer-Doku ist seit dem Stand `81800657` aufgabenbezogen neu (`docs/user/`, Einbettungstest `AnleitungTest` im Source-Set `integrator`); sie beschreibt den Produktschnitt als Ist-Zustand.
- `slice-betrieb-metriken-tracing` (Metriken und Tracing) liegt auf `main` in `open/`; die Aussagen dieser Welle berührt er nicht.

## Was hat funktioniert?

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3.

- Die Reihenfolge Spezifikation, Herauslösen, Jar und Fläche trug: Kein Slice musste nach §4 zurückgeführt werden; die Spezifikation stand vor dem Bau und die Zielzustand-Vermerke (14 Stellen) wurden mit dem Bau abgetragen.
- Das *Mehr* der Welle ist maschinell wiederholbar: Manifest-Prüfung mit Selbsttest, Mutation am Modul `testserver`, E2E-Durchgang. Alle drei konnte diese Closure ohne Rückgriff auf die Slice-Notizen neu fahren (Abschnitt „Verifikation“).
- Die Prüfung der Modulgrenze als Übersetzung (nicht assoziiertes Modul, `tools/modulgrenze-sperre.sh` gegen die Build-Datei) fing den Rückfall „`internal` öffnen“ in der Mutation dieser Closure am Compiler, ohne Zutun eines Tests.
- Der Review der Slices fand die zweiten Fundstellen vor der Closure (`slice-jar-ohne-main-und-image` F-5, `slice-explicit-api-umbau` Dockerfile-Kommentar); die Suche des Closure-Kriteriums (4) fand am heutigen Stand keine mehr.

## Was ging anders als geplant?

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 — jede Zeile moeglichst mit der Konsequenz,
die daraus schon gezogen wurde (Folge-Slice, Spec-Version).

- **Der Ort des Testservers wechselte** vom Source-Set `testserver` zum Gradle-Modul ([ADR-0034](../../adr/0034-testserver-als-gradle-modul.md)). Konsequenz: `slice-testserver-gradle-modul` (`done/`), Welle-Wortlaut nachgezogen; Mutation und Sperre der Modulgrenze am Modul gemessen.
- **Der Beleg „`integratorTest` läuft grün ohne Jackson im Klassenpfad“ ist durch Lock und Klassenpfad belegt, nicht durch einen Test, der fiele.** `slice-testserver-herausloesen` hat es als Abweichung (d) benannt; diese Closure bestätigt es (Abschnitt „Aussagen“, Zeile 3). Konsequenz: Beobachtung `BEO-TDS/jackson-abhaengigkeit-fuer-embedder-nicht-gemessen` bleibt offen, 2×.
- **Das Kriterium (1) nennt `runtime.MainKt`; der Einstieg des Testservers heißt `io.github.pt9912.tdswire.testserver.MainKt`.** Der Wortlaut wurde im Review von `slice-jar-ohne-main-und-image` (F-2) mit dem Nutzer geklärt. Diese Closure sucht beide Namen und wertet die Treffer (Abschnitt „Verifikation“).
- **Der E2E-Durchgang wuchs von sieben auf acht Stufen** (Stufe 8, eigenes Backend), nachdem die Welle ihn „in acht Stufen“ im Kriterium nannte; die Zahl im Kriterium war schon vorweggenommen. Die Zelle `make e2e` in `harness/README.md` nennt weiter „Stufen 1 bis 7“ mit den Messungen vom 2026-11-01; sie ist als Messung datiert und richtig, aber nicht auf acht Stufen nachgemessen (Nutzerpunkt aus `slice-jar-ohne-main-und-image`, unverändert).
- **Aussagen breiter als der Beleg** traten in `slice-testserver-herausloesen` (M-1, L-5) und `slice-explicit-api-umbau` (erste Fassung von `SPEC-059`) wieder auf, obwohl `AGENTS.md` §3.8 galt (Klasse `BEO-TDS/aussagen-ueber-messung-hinaus`, verkörpert; keine weitere Schärfung beschlossen).

## Trigger-Audit der Welle (Schritt 2)

Regeln dieser Sektion: `close-welle` Schritt 2 — je Klasse eine belegte Feststellung.

- **Carveouts:** 0 offen. `docs/plan/carveouts/` führt nur `done/` (CO-001 bis CO-004); kein Slice dieser Welle hat einen Carveout eröffnet oder verlängert.
- **Bootstrap-aware Gates:** 0 offen. Die Sensors-Tabelle in `harness/README.md` führt kein Gate mit Stufe oder Reife; die Zeilen mit `bootstrap-aware` sind Vorlagenreste. Neue Gates der Welle (`manifest-pruefung` in `build-jar`, Jackson-Sperre in `detekt`, Modulgrenze-Sperre in `detekt`) haben keine Reifestufe.
- **Entscheidungen (Re-Evaluierungs-Trigger der ADRs der Welle):**

| ADR | Trigger | Befund |
|---|---|---|
| [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) | ein Betreiber verlangt Produkt-`main` mit Image; Veröffentlichung in einem Artefakt-Repository; Testserver-Szenario braucht Modulgrenze (Option C); E2E-Befund nur mit `internal` oder Bibliotheks-Log; Testserver- und Embedder-Pfad laufen auseinander | Modulgrenze: **eingetreten, bearbeitet** durch [ADR-0034](../../adr/0034-testserver-als-gradle-modul.md); Log: durch [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) gebaut; Produkt-`main`, Veröffentlichung, Auseinanderlaufen: nicht eingetreten (Kenntnisstand dieser Closure; Pfad-Vergleich ist kein Sensor) |
| [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md) | weiterer Typ aus `simulation` nötig; Kompatibilitätslauf verzichtet auf den Aufzeichner; Embedder verlangt `SimCatalog.find` | nicht eingetreten: das Modul `testserver` übersetzt gegen die Fläche, `RecordingBackend` steht in `KompatibilitaetsServer` |
| [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) | Veröffentlichung, Jackson-Konflikt, Version 1.0, Embedder-Adapter für Log oder Telemetrie | Adapter-Wunsch **eingetreten, bearbeitet** durch [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) (Parameter `telemetry`); sonst nicht eingetreten |
| [ADR-0034](../../adr/0034-testserver-als-gradle-modul.md) | M1 rot; M4 Zyklus; Gate-Zeit wächst über das Maß des Nutzers; Veröffentlichung | M1 heute grün im Sinne der Aussage (Mutation rot gemessen, Abschnitt „Aussagen“ Zeile 4); M4 nicht neu gefahren; Gate-Zeit nicht gemessen (kein Maß des Nutzers genannt) |

Keine Entscheidung steht mit eingetretener, unbearbeiteter Bedingung da.

## Aussagen der Welle: gemessen und Festlegung

Regeln dieser Sektion: Closure-Kriterium §3 der Welle-Datei, vierter Punkt. *Gemessen* heißt: ein Lauf dieser Closure am heutigen Stand von `main` (2026-11-07), der besteht oder erwartungsgemäß rot ist; Einzelmessungen, ein Host, Last durch andere Agenten. *Festlegung* heißt: Setzung, gelesen und nicht gemessen, oder eine Messung der Slices, die diese Closure nicht neu gefahren hat. Die Angaben zu Mutationen der Slices stammen aus deren Notizen und sind als solche gekennzeichnet.

| # | Aussage | gemessen (Lauf dieser Closure) | Festlegung / Grenze / Stand heute |
|---|---|---|---|
| 1 | `tdswire.jar` hat keine `Main-Class`, `Class-Path` nennt nur `lib/<Name>` mit vorhandener Datei, das Jar heißt `tdswire.jar` | Stage `build-jar` ohne Cache (`--no-cache-filter build-jar`): `manifest-pruefung: ok (keine Main-Class, kein Testserver, kein Jackson, 3 Class-Path-Eintraege in lib/)`; Selbsttest `tools/manifest-selbsttest.sh` (13 Fehlerfälle, ein gutes Jar) in `make skript-selbsttest`, grün | Prüfung ist namensbasiert: `snakeyaml` oder `gson` in `lib/` erkennt sie nicht; der Start des Jars ist nicht geprüft (`SPEC-052`); `Class-Path` ist Komfort für Einbettende (Setzung); ein fremdes Programm, das das Jar so einbindet, ist ungemessen |
| 2 | `lib/` und `src/main` führen kein Jackson | Manifest-Prüfung (Name `jackson` in `lib/`) wie Zeile 1; `tools/jackson-sperre.sh` über `src/main` in der Stage `detekt`, grün in `make gates`; `gradle.lockfile`: 0 Zeilen mit Jackson, `testserver/gradle.lockfile`: 5 | Textprüfung (`com.fasterxml` in Kotlin und Java): andere YAML-Bibliotheken und eine Abhängigkeitszeile in `*.gradle.kts` erkennt die Sperre nicht, den Rückfall in `implementation` hält das Lock von Gradle; die Zählung im Lock ist von Hand (ein `grep -c`), kein Gate hält sie |
| 3 | Der Einbettungstest `integrator` läuft ohne Jackson im Klassenpfad | `integratorTest` grün (Stage `test`, ohne Cache); `build.gradle.kts` deklariert für `integrator` weder `integratorImplementation` noch `integratorRuntimeOnly` mit Jackson; `gradle.lockfile` ohne Jackson; Rückfall-Mutation (`integratorRuntimeOnly(libs.jackson.core)`) rot am Lock | **Kein Test fällt, wenn Jackson im Klassenpfad von `integrator` läge**: gehalten wird die Aussage nur vom Lock (Fitness Function der ADR: „nur Absicht“); das Lock bricht, solange niemand `make deps-lock` neu schreibt Beobachtung `BEO-TDS/jackson-abhaengigkeit-fuer-embedder-nicht-gemessen`, offen |
| 4 | Das Modul `testserver` bindet die Bibliothek nur über die öffentliche Fläche; ein Zugriff auf `internal` bricht die Übersetzung | Mutation dieser Closure: Datei im Modul mit `import …simulation.SimTemporal` (`internal object`) und `internal fun mutationM1() = SimTemporal`, Stage `compile`: `:testserver:compileKotlin FAILED`, `Cannot access 'object SimTemporal : Any': it is internal in file.` (zwei Meldungen); Datei danach entfernt, Arbeitsbaum sauber. `tools/modulgrenze-sperre.sh` in `make detekt-selbsttest`, grün | Gilt für Kotlin-Quellen: Java-Quellen und Reflection umgehen die Regel (akzeptiertes Negativ der ADR, `BEO-TDS/java-quelle-umgeht-kotlin-regel`, 2×, offen); ein Name, eine Mutation; die Messungen M2 bis M7 des Messprotokolls zum Gradle-Modul sind nicht neu gefahren |
| 5 | `explicitApi` strict übersetzt `src/main` ohne Diagnose; das nicht assoziierte Source-Set `integrator` und das Modul `testserver` übersetzen gegen die Fläche | `explicitApi()` in `build.gradle.kts`; Stage `compile` (`gradle classes`, Hauptmodul und Modul `testserver`) und `compileIntegratorKotlin` in der Stage `test` ohne Diagnose; `integratorOhneAssoziation` hängt an `compileIntegratorKotlin` | Die Zählung der Slice-Notiz (763 Diagnosen vor dem Umbau, 61 öffentliche Klassen danach) stammt vom 2026-11-01 und ist nicht neu gezählt; die Fläche ist seither um die Namen der Telemetrie gewachsen ([ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md)) |
| 6 | Die Liste der öffentlichen Typen in `SPEC-059` ist die Fläche | `make flaeche` (Teil von `make gates`, Selbsttest mit 14 Fällen), grün | `make flaeche` vergleicht Typnamen, keine Mitglieder: ein Nicht-Listen-Member `public` (Mutation A von `slice-explicit-api-umbau`, `SimCatalog.find`) bleibt grün; Mutation heute nicht neu gefahren. `Principal` steht in der Liste, ist aber nur über einen nicht öffentlichen Adapter erreichbar (Nutzerpunkt aus `slice-explicit-api-umbau`, offen) |
| 7 | Die Stufen von `make e2e` laufen gegen den Testserver; es gibt acht | `make e2e`: Exit 0, `e2e: gruen (8 Stufen, 415 s)`; Stufen 87, 97, 67, 61, 22, 22, 38, 21 s (Last durch andere Agenten, Einzelmessung); Stufe 2 (`make laufzeittest`) fährt den Testserver-Container | Zeiten sind Einzelmessungen: `harness/README.md` nennt für Stufen 1 bis 7 103 s warm und 339 s bei hoher Last (2026-11-01), nicht auf acht Stufen nachgezogen; der E2E-Durchgang ist kein Gate ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)) |
| 8 | Kein Stufentext, Skript und `Dockerfile` nennt `runtime.MainKt`, die Stage `runtime` oder die Stage `build` | Suche heute (Abschnitt „Verifikation“): kein Treffer für `runtime.MainKt`; das `Dockerfile` hat keine Stage `runtime` oder `build` (Liste der `FROM`-Zeilen); die Ausgabe des E2E-Laufs nennt `runtime.MainKt` nicht (0 Treffer) | Es bleibt `io.github.pt9912.tdswire.testserver.MainKt` (Einstieg des Testservers, `Dockerfile`, Testdaten in `tools/manifest-selbsttest.sh`); die Suche ist Handarbeit, kein Gate |
| 9 | Keine zweite Fundstelle der abgelösten Aussagen (`simulationFile`, `SIMULATION_DESCRIPTION_FILE` als Eingang der Bibliothek, „Jar mit Startklasse“, „Laufzeit-Image“) | Suche heute über `spec/`, `harness/README.md`, `README.md`, `tools/`, `Dockerfile`, `build.gradle.kts`: `simulationFile` 0 Treffer; `SIMULATION_DESCRIPTION_FILE` nur in Aussagen über den Testserver (`Dockerfile` im Stage `integrationsserver`, `tools/testserver.sh`, `SPEC-035`) und in zwei Zeilen des Änderungsverlaufs der Spezifikation; „Jar mit Startklasse“ und „Laufzeit-Image“ nur in Zeilen des Änderungsverlaufs (2026-10-31) | Die Zeilen des Änderungsverlaufs sind Chronik und bleiben; gesucht ist die Kernaussage, die Suche über Begriffe ist keine Gleichheitsprüfung (Klasse `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`, Reviewer-Prüfpunkt, kein Sensor) |
| 10 | Spezifikation, Skripte, `Dockerfile` und `harness/README.md` sagen dasselbe | `make kopplung-zahlen` (Heap und Text von `gross` in Gradle, Kotlin, .NET, `SPEC-040`, README; acht Mutationen im Selbsttest), `make docs-check` (1187 Dateien, 0 Befunde) in `make gates FORCE=1`, grün | Gleichheit der Prosa ist Lesen, kein Sensor; `docs-check` prüft Verweise, nicht Aussagen |
| 11 | Es gibt kein Betriebs-Image | `Dockerfile` ohne Stage `runtime` und `build`; `make laufzeittest` prüft nur den Testserver-Container | Die Prüfungen des Produkt-Images (Nutzer, Lizenz in `/app`, Dateiliste, `JAVA_TOOL_OPTIONS`) entfielen ohne Ersatz ([ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md)) |

### Der Verlust beim Log und sein Träger

[ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) (Konsequenzen) benennt drei Prüfungen der E2E-Skripte als verloren: (a) das Log des Servers nennt keinen Handlerfehler, (b) die Prüfung auf `secret|Geheimnis` im Log, (c) das Log nennt den Grund einer abgelehnten Anmeldung (`UNSUPPORTED_PASSWORD_CHANGE`). `slice-testserver-herausloesen` nahm die Prüfungen aus den Skripten und kennzeichnete sie mit der Kennung des Ersatz-Slice; den Ersatz trug `slice-betrieb-log-adapter-testserver`. Der Befund heute:

- **Träger:** `slice-betrieb-log-adapter-testserver` liegt in `done/` (Dateisystem). Die Kennzeichnungen „Entfallen, Ersatz in slice-betrieb-log-adapter-testserver“ stehen in keinem Skript mehr (`grep` über `tools/*.sh`: 0 Treffer).
- **Ersatz am Prozess des Testservers (gemessen: `make e2e`, Stufen 4 bis 7 grün):** (a) `kompatibilitaetstest.sh` prüft, dass das Log kein `connection_failed` und kein `adapter_failed` nennt, und eine Positivprobe (mindestens ein `login_accepted`), damit die Negativprüfung nicht auf leerem Log grün ist; (b) dieselbe Datei prüft `secret|Geheimnis` in der Ausgabe des Testservers (Ereigniszeilen und Backend); (c) in Kategorie `Luecken` prüft sie `login_rejected` mit `reason` `UNSUPPORTED_PASSWORD_CHANGE` und `UNSUPPORTED_FEDERATED_AUTHENTICATION` sowie `request_failed` mit `TDS_PROTOCOL_ERROR` und `TDS_UNSUPPORTED_TYPE`; in `Anmeldung` zählt sie `TDS_PREPARE_LIMIT` genau einmal. Das Muster der Zeilen hält `tools/log-muster-selbsttest.sh` in `make skript-selbsttest`, grün.
- **Festlegung:** Die Wortlaute sind die JSON-Ereignisse der [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md), nicht die Textzeilen der alten Skripte; die Prüfung (b) deckt die Ausgabe des Testservers, nicht ein Log der Bibliothek, das ein Einbettender schreibt. Dass jede der drei Prüfungen den Bruch wirklich meldet, ist die Mutation des Adapter-Slice (dessen Closure-Notiz), hier nicht neu gefahren.

## Ausgeschlossene Gegenstände (§6 der Welle-Datei) und ihr Träger

Regeln dieser Sektion: Closure-Kriterium §3 der Welle-Datei — jeder ausgeschlossene Gegenstand ist einem Träger zugeordnet, der als Datei existiert, oder trägt den Grund, warum nicht.

| Gegenstand (§6) | Träger (Datei) | Stand zu dieser Closure |
|---|---|---|
| Log der Bibliothek, Log-Adapter des Testservers | `slice-betrieb-log-schnittstelle`, `slice-betrieb-log-adapter-testserver` (beide `done/`), Welle `welle-betrieb-diagnose.md` (flach in `planning/`) | gebaut; Metriken und Tracing: `slice-betrieb-metriken-tracing` (`open/`) |
| Spezifikation zum Log (Abschnitte 4 und 5, `TDS-FA-09.a`) | `slice-spec-nachzug-telemetrie` (`done/`) | geschlossen |
| Änderungen an der Fläche über die Namen der ADRs hinaus | kein Träger nötig: braucht der Testserver einen weiteren Typ, entsteht eine Folge-ADR; keiner ist entstanden | Trigger von [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md) nicht eingetreten |
| Produkt-`main`, Betriebs-Image, Veröffentlichung im Artefakt-Repository | `slice-release-entscheidungen` (`open/`) für Registry-Fragen gegen [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md); jede Änderung des Produktschnitts wäre eine eigene ADR | offen, ohne Auslöser |
| Sensor gegen Java-Quellen oder Reflection im Modul `testserver` | kein Träger. Grund: akzeptiertes Negativ der ADR; Register `BEO-TDS/java-quelle-umgeht-kotlin-regel` (2×) | unverändert |
| Format, Teilmenge, Typabbildung der Tabellenbeschreibung ([ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md)) | kein Träger nötig: die Welle verschob den Leser, `SimDescriptionReaderTest` (26 Tests) läuft in `:testserver:test` | unverändert |
| Lizenzhinweise der Bibliotheken in `lib/`, Protokollverhalten | `slice-release-doku-prozess` (`done/`); Register `BEO-TDS/lizenzhinweise-der-bibliotheken-rechtsfrage` | Rechtsfrage offen beim Nutzer |
| Neue Anforderungen oder Änderungen am Lastenheft | — | keine entstanden |

## Steering-Loop-Einträge

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 (hier stehen **nur** Beobachtungen, die im
Register 3× erreicht haben; jeder Eintrag nennt seine `BEO-<KUERZEL>/<slug>`) ·
`grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln (Feld
und Zielort auf **einer** Zeile, Sektionsangabe innerhalb der Backticks; die
**Spec-Lücke** trägt statt `liegt in` ihre `LH-*`-ID — das ist kein Versehen).

**Lese-Schritt des Beobachtungs-Registers.** Gelesen wurden alle Verzeichnisse von `BEO-TDS` mit mindestens drei Evidence-Dateien, in denen eine Datei einer der vier Slices dieser Welle liegt (Stand 2026-11-07, am Dateisystem gezählt: `evidence/<slice>.md`). Das sind vier Klassen. **Mit dieser Closure wird nichts neu verkörpert.** Der Grund: Zwei Klassen haben bereits ihren Ausgang, eine ist an die Entscheidung des Nutzers vom 2026-11-06 gebunden, und die vierte trägt keine Empfehlung im Register-`state.md` — sie geht als Frage 1 an den Nutzer, statt dass diese Closure eine Regel erfindet.

| Klasse (`BEO-TDS/…`) | Evidence | Beitrag dieser Welle | Ausgang dieser Closure |
|---|---|---|---|
| `aussagen-ueber-messung-hinaus` | 75 | `slice-testserver-herausloesen`, `slice-explicit-api-umbau` | schon verkörpert (`AGENTS.md` §3.8 und Reviewer-Skill, `seit welle-protokollcodec`); Anker am Ziel gefunden |
| `fortschreibung-laesst-zweite-fundstelle-stehen` | 28 | alle vier Slices | schon verkörpert (Reviewer-Skill, `seit slice-qa-dotnet-lieferkette-haerten`); Anker am Ziel gefunden. Das Kriterium (4) dieser Welle ist die Suche über beide Orte: heute ohne Fund (Zeile 9 der Tabelle) |
| `lesart-einer-nutzerentscheidung-unbestaetigt` | 16 | `slice-jar-ohne-main-und-image` (F-1, F-2) | Nutzerentscheid 2026-11-06 gilt: bei der Welle-Closure gelesen, ohne Regel, Sensor oder Träger-Slice; hier gelesen, unverändert |
| `zusage-ohne-sensor-in-der-ci-kette` | 6 | `slice-testserver-herausloesen` (F-4, m7), `slice-jar-ohne-main-und-image` (F-3), `slice-explicit-api-umbau` (Menge der öffentlichen Klassen) | gelesen, unverändert *offen*; `state.md` vermerkt „Entscheidung bei der Welle, die die CI-Kette ändert“, ohne Empfehlung; Frage 1 |

**Kein Eintrag über der Schwelle wurde in dieser Closure neu zur Regel.** Das ist das Ergebnis des Lese-Schritts, nicht sein Auslassen. Die Anker-Paarung entfällt für neue Einträge; geprüft ist sie für die zwei bestehenden Anker, auf die sich die Tabelle stützt (`AGENTS.md` §3.8 mit `seit welle-protokollcodec`, Reviewer-Skill mit `seit slice-qa-dotnet-lieferkette-haerten`).

**Lerneintrag der Welle (benannte Spec-Lücken, in der Spezifikation oder im Register geführt, kein `liegt in`):** Die Welle zeigt am heutigen Stand, dass ihre drei „maschinellen“ Belege unterschiedlich weit tragen. (1) *Jackson im Klassenpfad von `integrator`*: Der Beleg ist das Lock, kein Test ([TDS-RB-01](../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache) und `SPEC-059` „ohne Jackson im Klassenpfad“ stehen ohne Laufzeitprüfung; Register `BEO-TDS/jackson-abhaengigkeit-fuer-embedder-nicht-gemessen`, 2×, kein Träger-Slice). (2) *Die Menge der öffentlichen Mitglieder*: `make flaeche` hält Typnamen, nicht Mitglieder ([TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), `SPEC-059`). (3) *Der Container-Teil von `SPEC-052`* (`make laufzeittest` mit dem Testserver) trägt keinen `@spec`-getaggten Test, weil der E2E-Durchgang kein Gate ist ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)); die Matrix `docs/plan/spec-abdeckung.md` führt `SPEC-052` mit Tests der Lizenz und des Log-Prozesses, den Container-Teil nicht (Register `BEO-TDS/zusage-container-teil-spec-052-ohne-test`, 1×; gelesen am Stand der Matrix, nicht neu erzeugt). Keine der drei ist mit dieser Closure geschlossen; jede ist mit Ort und Grenze genannt.

**Weitere Register-Einträge dieser Welle unter der Schwelle** (gelesen bei der Sichtung der Eröffnung, hier nur der Ausgang): `BEO-TDS/jackson-abhaengigkeit-fuer-embedder-nicht-gemessen` (2×, offen; Kriterium (2), siehe oben), `BEO-TDS/zusage-container-teil-spec-052-ohne-test` (1×, offen; die vorgesehene Prüfung der Welle-Closure ist erfolgt, siehe Lerneintrag), `BEO-TDS/folge-adr-betriebsweg-nach-produktschnitt-ungeklaert` (1×, offen; erneut gesichtet, Frage 2), `BEO-TDS/java-quelle-umgeht-kotlin-regel` (2×, offen) und `BEO-TDS/move-commit-laesst-detekt-rot` (2×, offen) unverändert, `BEO-TDS/zwei-artefakt-stages-nebeneinander` (gestrichen mit `slice-jar-ohne-main-und-image`).

## Beobachtungs-Register (Zeiger)

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register — der Zähler wird **nicht** hier gepflegt; diese
Sektion ist ein Zeiger und trägt keine Daten.

Der Zähler steht in [`../observations/`](../observations/).
Was in dieser Welle **3×** erreicht hat und gelesen wurde, steht oben unter
*Steering-Loop-Einträge*.

## Folge-Slices

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 — **derivativ**: Diese Liste zeigt nur,
das Original ist die Slice-Datei. Jeder genannte Folge-Slice muss als Datei im
Planning-Lifecycle existieren; genannt ohne angelegt ist dieselbe Klasse wie
ein halluziniertes Gate.

Diese Closure legt keinen Slice an. Genannt, weil Gegenstand dieser Welle:

- `slice-betrieb-metriken-tracing` (`open/`) — Metriken und Tracing über die Senke; berührt die Aussagen der Welle nicht.
- `slice-release-entscheidungen` (`open/`) — Entscheidungen zum Release, darunter die Registry-Frage gegen [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md).

Für die Lücken des Lerneintrags besteht kein Träger-Slice (Fragen 1 bis 3).

## Verifikation

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 1 — keine Behauptung ohne nachprüfbaren
Anker (Hash, Lauf, Zahl). Einzelmessungen am 2026-11-07, ein Host (Last durch andere Agenten, Lastmittel zeitweise über 20), Basis `main` mit `81800657`.

- **Closure-Trigger am Repo geprüft:** Die vier Slices aus §4 liegen in `done/` (`ls`: `slice-spec-nachzug-produktschnitt`, `slice-testserver-herausloesen`, `slice-jar-ohne-main-und-image`, `slice-explicit-api-umbau`); `slice-betrieb-log-adapter-testserver` liegt in `done/`, `slice-betrieb-log-schnittstelle` ebenso.
- **Kriterium (1), Suche:** `grep -rn "runtime.MainKt" Dockerfile tools docs spec harness Makefile README.md` — der einzige Treffer außerhalb der Zeitdokumente ist die Welle-Datei selbst (Kriterium) und [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) (Beschreibung des abgelösten Zustands, `Accepted`); `grep` nach `MainKt` über `Dockerfile tools Makefile harness spec README.md docs/user docs/maintainer build.gradle.kts testserver settings.gradle.kts` findet `io.github.pt9912.tdswire.testserver.MainKt` (`Dockerfile` Zeile `ENTRYPOINT` des Stage `testserver`) und Testdaten in `tools/manifest-selbsttest.sh` (`x.MainKt`, `testserver/MainKt.class`). Das `Dockerfile` führt 21 Stages, keine heißt `runtime` oder `build` (`grep "^FROM"`); `runtime` kommt nur als Package-Name der Testklassen `ServerSmokeTest` und `ZustandsereignisseProzessTest` vor. E2E-Ausgabe: 0 Treffer auf `runtime.MainKt`.
- **Kriterium (1), Lauf:** `make e2e` am heutigen Stand: Exit 0, `e2e: gruen (8 Stufen, 415 s)`.
- **Kriterium (2), Bau:** `make build` Exit 0; Stage `build-jar` mit `--no-cache-filter build-jar` neu gebaut: Exit 0, `manifest-pruefung: ok (keine Main-Class, kein Testserver, kein Jackson, 3 Class-Path-Eintraege in lib/)`; `lib/` führt `annotations`, `kotlin-stdlib`, `kotlinx-coroutines-core-jvm` (Einträge des `Class-Path`, `docs/user/einbettung-und-betrieb.md` nennt dieselben drei).
- **Kriterium (2), `integratorTest`:** siehe Frischlauf der Stage `test`; Rückfall-Mutation „`integratorRuntimeOnly(libs.jackson.core)`“ in `build.gradle.kts`, Stage `test`: rot in `:abhaengigkeitenLaden` mit `Resolved 'com.fasterxml.jackson.core:jackson-core:2.21.7' which is not part of the dependency lock state`; die Zeile ist zurückgenommen, der Arbeitsbaum danach ohne Änderung. Die Mutation fällt am Lock von Gradle, nicht an einem Test.
- **Kriterium (3), Mutation:** siehe Tabelle Zeile 4; rot gesehen und zurückgenommen.
- **Kriterium (4), Suche:** siehe Tabelle Zeile 9.
- **Kriterium (5):** siehe Tabelle Zeilen 5 und 6.
- **`make gates FORCE=1`** (erzwingt die Klassen mit Stempel) am Stand vor dieser Closure: Exit 0; gelaufen sind `baseline-verify`, `detekt-selbsttest`, `kopplung-zahlen`, `skript-selbsttest`, `detekt`, `build-jar`, `referenztest-dotnet` (Stage `test` der .NET-Referenztests), `test`, `flaeche`, die vier Selbsttests der Stempel, `gate-zuordnung`, `docs-check` (1187 Dateien, 0 Befunde). Die Stage `test` kam in diesem Lauf aus dem Layer-Cache; der Frischbeleg der Tests ist der folgende Lauf.
- **Frischlauf der Stage `test` ohne Layer-Cache** (`docker build --no-cache-filter test --target test .`): Exit 0, `BUILD SUCCESSFUL in 3m 19s`, 1156 `PASSED`-Zeilen, 0 rote Zeilen eines Testfalls, kein Testfall übersprungen (die zwei `SKIPPED` im Log sind Gradle-Tasks `checkKotlinGradlePluginConfigurationErrors`), `integratorTest` mit `AnleitungTest` und den `*EmbeddingTest` grün. Ein erster Versuch am selben Stand blieb nach `TableStoreTest` im Modul `testserver` stehen und wurde nach 25 Minuten abgebrochen (Prozess der Test-JVM mit 100 % CPU, parallel zu Läufen anderer Agenten bei Lastmittel über 20; eine zweite Test-JVM desselben Moduls aus einem Build eines anderen Zweiges stand zur gleichen Zeit ebenso; Ursache nicht untersucht); `:testserver:test` allein, außerhalb des Build-Kontexts im Container der Stage `deps`, lief durch (`BUILD SUCCESSFUL in 1m 20s`), ebenso `TelemetryJsonTest` allein. Der Hänger ist ein Einzelereignis, kein Befund über den Stand; als Beobachtung nicht angelegt (1 Fall, Ursache offen).

**Gate-Läufe nach den Commits dieser Closure:** `make docs-check` nach dem Self-Close-Commit Exit 0 (1188 Dateien, 0 Befunde); nach dem Move und vor dem Link-Nachzug zwei Befunde `codepath-missing` (Pfade in Backticks in `slice-e2e-backend-ausserhalb-modul` und `slice-testserver-gradle-modul`) neben den Markdown-Links, wie vorgesehen nachgezogen; nach dem Link-Nachzug Exit 0, 0 Befunde. `make gates` danach: Exit 0; die Klassen `werkzeug`, `tests` und `stempel` wurden **durch ihren Stempel übersprungen** (die Commits dieser Closure ändern nur Dokumente unter `docs/plan/planning/`, kein Eingang der Klassen), die Klasse `immer` lief voll. Der Frischbeleg der Tests ist der Lauf der Stage `test` ohne Cache oben und `make gates FORCE=1` vor den Commits, nicht der Stempel.

## Offene Fragen an den Nutzer

Diese Closure trifft keine davon. Jede trägt einen Träger und eine Empfehlung.

1. **`zusage-ohne-sensor-in-der-ci-kette` (6×, offen; Belege auch aus dieser Welle).** Zusagen der Build-Kette ohne Wächter: Kennzeichnung „Entfallen, Ersatz in …“ in Skripten (m7, inzwischen durch den Ersatz abgelöst), Menge der öffentlichen Klassen, SHA-Pins der Actions, Aufruf `make gates` im Workflow. Das Register trägt keine Empfehlung („Entscheidung bei der Welle, die die CI-Kette ändert“). *Empfehlung:* nichts verkörpern, solange keine Welle die CI-Kette ändert; die einzelnen Lücken tragen eigene Träger (`make flaeche` für die Typnamen) oder sind benannt. Alternativ die Klasse streichen und die drei Fälle je als eigene Beobachtung führen.
2. **`folge-adr-betriebsweg-nach-produktschnitt-ungeklaert` (1×).** [ADR-0011](../../adr/0011-pruefwertformat.md) und [ADR-0002](../../adr/0002-tls-und-verschluesselung.md) sprechen von Datei und Schlüsselnamen; seit [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) gehören Umgebung und Dateien dem Testserver. Keine Zeile der Spezifikation widerspricht ihnen. *Empfehlung:* keine Folge-ADR; beide ADRs bleiben als Aussagen über den Testserver lesbar, die Spezifikation führt die Zuordnung in `SPEC-035` und `SPEC-051`. Der Nutzer entscheidet.
3. **Lücken des Lerneintrags** (Jackson im Klassenpfad von `integrator` ohne fallenden Test; Mitglieder der Fläche ohne Sensor; Container-Teil von `SPEC-052` ohne `@spec`-Test). *Empfehlung:* für (1) einen kleinen Test im Source-Set `integrator`, der die Klasse `com.fasterxml.jackson.core.JsonFactory` zur Laufzeit nicht findet, falls der Nutzer die Aussage der ADR über „Absicht“ hinaus halten will; für (2) und (3) die Grenze benannt lassen.
4. **Zelle `make e2e` in `harness/README.md`** nennt „Stufen 1 bis 7“ mit Messungen vom 2026-11-01 (103 s, 339 s). Heutige Einzelmessung: 8 Stufen, 415 s. *Empfehlung:* nachziehen, sobald die Zelle ohnehin angefasst wird (Grenze 200 Zeichen je Zelle); kein Fehler, nur veraltete Messung.
5. **Name `make laufzeittest`** (Nutzerpunkt aus `slice-jar-ohne-main-und-image`): der Gegenstand heißt Testserver-Container. *Empfehlung:* belassen, bis ein Anlass zum Umbenennen entsteht.
6. **`archive-welle` ist nicht gelaufen.** Die Zeitdokumente dieser Welle (vier Slices, Plan, Review-Berichte) bleiben flach in `done/`. Grund: auf anderen Zweigen laufen parallel Änderungen an Roadmap, Wellen und Spezifikation (Metriken, Merge, Test-Stage); das Werkzeug bewegt Dateien und bricht Verweise. *Empfehlung:* `make archive-welle WELLE=welle-produktschnitt` nach dem Merge dieser und der parallelen Closures.
