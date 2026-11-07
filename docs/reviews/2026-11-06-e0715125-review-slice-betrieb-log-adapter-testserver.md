# Review-Report: slice-betrieb-log-adapter-testserver (HEAD e0715125) — 2026-11-06

**Review-Art:** Code (gegen Plan, ADR und Hard Rules; nicht gegen die DoD, das ist die Frage des Verifiers).

**Gegenstand:** Diff `652d5667..e0715125` (Commits `59cd4575`, `c0fd8370`, `e0715125`; dazu die `slice-mv`-Commits `58da8dc2` bis `2e194f08`), Zweig `worktree-agent-adb51ccd8692652a8`. Gemergt wird nicht.

**Skill:** `.harness/skills/reviewer.md` @ e0715125 · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-06

**Eingangs-Kontext:**

- Slice `slice-betrieb-log-adapter-testserver` (Plan, DoD, Rückführungen, Risiken)
- [ADR-0020](../plan/adr/0020-logformat-und-observability-schnittstelle.md) (`Accepted`: Frage 1 Format als Adapter, Frage 7 Warteschlange und Prozessende), [ADR-0034](../plan/adr/0034-testserver-als-gradle-modul.md) (Modul `testserver`), [ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md), [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md)
- `TDS-FA-09`, `TDS-QA-03`, `TDS-QA-02`; `SPEC-052`
- `AGENTS.md` §3.1 bis §3.8

## Gesamturteil

0 HIGH, 2 MEDIUM, 4 LOW, 4 INFO. Der Adapter ist in der Sache korrekt: Warteschlange, Überlauf, `dropped`, `close` mit Frist, hängender Kanal und Escaping tragen ihre Tests, und die Sicherheitsmutation (Kennung in einer Ablehnung) wird vom Prozesstest erkannt. Die zwei MEDIUM: die Frist der letzten Zeile (`LAST_LINE_GRACE`) trägt kein Test (Mutant überlebt), und die Plan-Tabelle und der Kopf „Berührte Spec-Stellen“ nennen nicht alles, was der Diff berührt. `make gates`, `make gates FORCE=1`, `make test`, `make docs-check`, `make flaeche`, `make detekt-selbsttest`, `make skript-selbsttest` und `make e2e` (8 Stufen, 403 s) sind grün.

## Selbst gefahren

| Lauf | Ergebnis |
|---|---|
| `make test` | Exit 0 (2 min 43 s bei Last 20 bis 24 des Hosts) |
| `make gates` | Exit 0; Stempel `tests` nicht geschrieben, Ursache siehe F-9 |
| `make gates FORCE=1` | Exit 0; wieder „Stempel nicht geschrieben“ (F-9) |
| `make docs-check`, `make flaeche`, `make detekt-selbsttest`, `make skript-selbsttest` | je Exit 0 |
| `make e2e` | Exit 0, acht Stufen grün, 403 s; Stufe 2 nennt `login_accepted` mit `principal` alice, Stufe 6 die Ereignisse `login_rejected` und `request_failed` |
| `grep -rn slice-betrieb-log-adapter-testserver tools/` (Abnahmeschritt aus §6 des Slice) | keine Fundstelle: keine Kennzeichnung der Herausnahme übrig |
| Merge-Probe `git merge-tree HEAD 7bb31eb0` | Konflikte nur in `spec/spezifikation.md` (Änderungsprotokoll, beide Seiten hängen an), `docs/plan/spec-abdeckung.md` (erzeugt) und einer Linkzeile in `done/slice-oeffentliche-flaeche-verkleinern-und-belegen.md`; `Main.kt`, `LogAdapter.kt`, `TelemetryJson.kt` konfliktfrei, `main` hat seit der Basis weder `domain/telemetry` noch `Main.kt` berührt (das `when` über `EventKind` bleibt vollständig) |

## Mutationen (selbst gefahren, Kopie des Baums, Stage mit gezielten Tests, jeder Lauf ein frischer Docker-Build, daher keine UP-TO-DATE-Falle)

| Mutation | Ergebnis |
|---|---|
| m1 `queue.offer(event)` durch `queue.put(event)` | rot: `LogAdapterTest` Überlauf, hängender Kanal, Bereitschaftszeile (3 von 8) |
| m2 `close` ohne Frist (`writer?.join()`) | rot: `LogAdapterTest` „Was nach der Frist bleibt…“ und „Ein haengender Kanal…“ |
| m3 `log.startWriter()` aus `Main.kt` entfernt | rot: `TestserverLogProcessTest` beide Tests mit nicht gelesenem Kanal |
| m4 `log.close()` vor `exitProcess` beim Abbruch entfernt | **grün (überlebt)**; äquivalent, siehe F-7 |
| m5 Sicherheit: `LoginRejected` trägt `principal` „mallory“ (Kennung einer Ablehnung im Log) | rot: `TestserverLogProcessTest > Alle Zeilen eines Laufs sind gueltig und nennen kein Geheimnis` |
| m6 `log.close()` aus dem Shutdown-Hook entfernt | rot, aber nur durch einen Test: „Ein Kanal, den niemand liest…“; der Test „Alle Zeilen eines Laufs…“ bleibt grün |
| m7 `last.join(grace)` in `writeLast` durch `last.join(1)` | **grün (überlebt)**, siehe F-1 |

Wanduhr-Tests (2 s, 250 ms, 5 s, 1 500 Anmeldungen, Pipe voll): `TestserverLogProcessTest` lief in diesem Review dreimal vollständig grün (`make test`, m4, m7) und in m3, m5, m6 mit grünen, von der Mutation nicht betroffenen Tests, bei Last 10 bis 24, ohne Flackern; so wenige Läufe belegen keine Stabilität.

## Findings

### F-1 MEDIUM — Frist der letzten Zeile ohne tragenden Test

* kategorie: MEDIUM
* quelle: Maintainability; Reviewer-Skill „Eigencode, dessen Entfernung keinen Test rot macht“; [ADR-0020](../plan/adr/0020-logformat-und-observability-schnittstelle.md) Frage 7 (3)
* pfad: `testserver/kotlin/io/github/pt9912/tdswire/testserver/LogAdapter.kt` · `last.join(grace.toMillis())`
* befund: Wird das Warten auf die Zeile in `writeLast` auf 1 ms gekürzt (m7), bleiben alle Tests von `LogAdapterTest` und `TestserverLogProcessTest` grün; der Test des hängenden Kanals misst nur die Frist von `close` (`elapsed >= 300 ms`), nicht die 250 ms von `writeLast`. Failure-Szenario: Ohne das Warten endet der Hook, bevor der Daemon-Thread `tdswire: gestoppt` oder `log_dropped` geschrieben hat; die JVM beendet sich, die Zeile fehlt, und `tools/laufzeittest.sh` (prüft `gestoppt`) wird zufällig rot.
* verifizierbar: ja (Mutation m7, `make test`)
* klasse: Wartebedingung im Eigencode ohne Test, dessen Entfernung rot macht

### F-2 MEDIUM — Plan-Tabelle und Kopf „Berührte Spec-Stellen“ unvollständig

* kategorie: MEDIUM
* quelle: Maintainability; Reviewer-Skill „Plan-Tabelle nennt die berührten Dateien nicht“; Slice §1 („ändert die Spezifikation nur bei einem Befund“)
* pfad: `docs/plan/planning/done/slice-betrieb-log-adapter-testserver.md` · „Berührte Spec-Stellen“ und die Tabelle in §3
* befund: Der Diff ändert `docs/plan/planning/in-progress/roadmap.md` (neuer Eintrag der Welle) und über `slice-mv` rund 20 Dateien (Slices, Wellen, Register) an Verweisen; keine steht in der Tabelle. `SPEC-052` ist in der Tabelle (Vermerk), fehlt aber im Kopf „Berührte Spec-Stellen“, und §1 sagt „kein stilles Ändern“ der Spezifikation, während §3 den Vermerk plant: der Slice widerspricht sich in der Frage, ob die Spezifikation geändert wird. Failure-Szenario: Der Negativbefund „keine Datei darüber hinaus“ ist nach dem Abgleich nicht haltbar; ein Leser des Kopfes erwartet keine Änderung an `SPEC-052`.
* verifizierbar: ja (Abgleich `git diff --stat 652d5667..HEAD` gegen §3)
* klasse: Plan-Tabelle nennt berührte Dateien nicht

### F-3 LOW — KDoc nennt einen Test, den es nicht gibt

* kategorie: LOW
* quelle: `AGENTS.md` §3.7 (Kommentar-Klasse Kopplung), §3.8
* pfad: `testserver/kotlin/io/github/pt9912/tdswire/testserver/TelemetryJson.kt` · „`TelemetryEventSchemaTest` hält sie gleich“
* befund: `TelemetryEventSchemaTest` existiert nirgends im Repo; den Zweck trägt `TelemetryJsonTest` (`Die Zeile deckt jeden Zugriff des Ereignisses`). Die Kopplung zeigt ins Leere.
* verifizierbar: ja (`grep -rn TelemetryEventSchemaTest`)
* klasse: Kopplungs-Kommentar mit nicht auflösbarem Ziel

### F-4 LOW — `LOG_LEVEL` am manuellen Testserver nicht einstellbar und nicht dokumentiert

* kategorie: LOW
* quelle: Slice §3 (Zeile `docs/user/testserver-manuell.md`: „Log des Testservers (Format, `LOG_LEVEL`, …)“); `AGENTS.md` §3.8
* pfad: `docs/user/testserver-manuell.md` · „daneben stehen JSON-Zeilen der Ereignisse“; `tools/testserver.sh` · `env_args=(-e LISTEN_HOST=0.0.0.0`
* befund: Die Datei erhielt nur einen Klammerzusatz an einer Beispielzeile; Format, `LOG_LEVEL` und Verhalten bei Überlauf stehen dort nicht, und `make testserver-up` reicht `LOG_LEVEL` nicht an den Container durch (die Liste der `-e` kennt ihn nicht). Wer das Log des manuellen Testservers auf `DEBUG` stellen will, findet keinen Weg.
* verifizierbar: ja (Grep)
* klasse: Plan-Zusage zur zweiten Fundstelle nur zum Teil eingelöst

### F-5 LOW — Negativprüfungen ohne Positivprobe im selben Lauf, aber mit Kennung `TDS-FA-09`

* kategorie: LOW
* quelle: Maintainability; `AGENTS.md` §5 Regel 6 (ein Beleg ist ein bestandener Lauf, nicht ein Verweis)
* pfad: `tools/e2e.sh` · „das Log des Testservers nennt keinen Handlerfehler und kein Kennwort“ (Stufen 4, 5, 7, 8)
* befund: In `kompatibilitaetstest.sh` (Szenario, Anmeldung, Synchronitaet) und `eigenes-backend-test.sh` sind die neuen Prüfungen reine `! grep`; sie bleiben auch bei leerem Log grün. Die Stufen tragen dennoch `TDS-FA-09` (Zustandsereignisse protokolliert), obwohl nur Stufe 3 (`simulation_backend_ready`) und Stufe 6 (`login_rejected`, `request_failed`) ein Ereignis positiv lesen.
* verifizierbar: ja (Skripte lesen; `docs/plan/e2e-abdeckung.md`)
* klasse: Negativprüfung ohne Positivprobe

### F-6 LOW — Neue `grep`-Prüfungen der Skripte ohne Rot-Gegenprobe im Selbsttest

* kategorie: LOW
* quelle: Slice §6 (Abnahmeschritt: kein Sensor hält die Skript-Prüfungen); `TDS-QA-06`
* pfad: `tools/kompatibilitaetstest.sh` · `anfrage_fehler()`; `tools/laufzeittest.sh` · `benutzer_nur_im_principal()`
* befund: `make skript-selbsttest` deckt nur `e2e.sh`, `manifest-pruefung.sh` und `vergleichslauf-d.sh`; keine der neuen Muster hat einen Rot-Fall. Eine Handprobe mit synthetischen Zeilen (Kennung auch in `login_rejected`) macht `benutzer_nur_im_principal` rot und `anmeldung_als_ereignis` grün, wie gedacht; ein Sensor ist das nicht.
* verifizierbar: nein (kein Gate-Lauf; Handprobe)
* klasse: Skript-Prüfung ohne Rot-Fall im Selbsttest

### F-7 INFO — Überlebender Mutant m4 ist äquivalent; Fehlerzweige sind mit `StandardOutput` unerreichbar

* kategorie: INFO
* quelle: [ADR-0020](../plan/adr/0020-logformat-und-observability-schnittstelle.md) Frage 7 (2)
* pfad: `testserver/kotlin/io/github/pt9912/tdswire/testserver/Main.kt` · `log.close()` im Zweig `StartOutcome.Aborted`
* befund: Vor `startWriter` ist die Phase `BEFORE_START`; `close` schreibt dann nur `log_dropped`, wenn ein synchrones Schreiben eine `IOException` warf, und `System.out.println` wirft keine (`PrintStream` schluckt sie). Der Aufruf hat mit dem echten Kanal keine beobachtbare Wirkung; er folgt der Regel der ADR. Aus demselben Grund laufen die `catch (IOException)` in `deliver` und `writeLast` nur mit Test-Kanälen.
* verifizierbar: ja (m4)
* klasse: Aufruf ohne Wirkung am echten Kanal

### F-8 INFO — Randfälle der Verlust-Semantik, nicht gemeldet und nicht gemessen

* kategorie: INFO
* quelle: [ADR-0020](../plan/adr/0020-logformat-und-observability-schnittstelle.md) Frage 7 (3), `SPEC-052`
* pfad: `testserver/kotlin/io/github/pt9912/tdswire/testserver/LogAdapter.kt` · `drainTo(left)` und `Phase.CLOSED -> dropped.incrementAndGet()`
* befund: (a) Das Ereignis, das der Schreiber bei hängendem Kanal in der Hand hält, zählt weder in `left` noch in `dropped`; (b) ein Ereignis nach `close` wird gezählt, aber nie gemeldet; (c) ein `accept`, das die Phase vor `close` las und nach `drainTo` einreiht, geht ungezählt verloren; (d) schreibt ein zuvor hängender Kanal nachträglich, kann dieses eine Ereignis nach `tdswire: gestoppt` stehen. Der Erhaltungstest (3001) gilt für den Fall, dass der Leser innerhalb der Frist beginnt.
* verifizierbar: nein
* klasse: Verlust-Semantik an den Rändern der Frist

### F-9 INFO — Ursache von „Stempel nicht geschrieben“: ignoriertes Verzeichnis im Eingang

* kategorie: INFO
* quelle: [ADR-0024](../plan/adr/0024-lokale-stempel-zwei-klassen.md); `AGENTS.md` §3.1
* pfad: `testserver/build/` (ignoriert, Eigentümer root, angelegt 16:23 bis 16:50)
* befund: `tools/stempel.sh pruefe tests` meldet „Eingangspfade nicht sauber: !! testserver/build/classes/kotlin/main/META-INF/io.github.pt9912_testserver.kotlin_module“; der Eingang `testserver` verlangt `git status --ignored` ohne Treffer. Das Verzeichnis (und `build/` im Wurzelverzeichnis, root-eigen) stammt aus einem Lauf mit Mount des Arbeitsbaums im Container, nicht aus dem Diff; der Diff ändert weder Liste noch Skript. `make gates` ist grün, nur der Stempel fehlt, auch bei `FORCE=1`.
* verifizierbar: ja (`bash tools/stempel.sh pruefe tests`)
* klasse: Gebaute Artefakte im Eingangspfad des Stempels

### F-10 INFO — Namen der JSON-Felder und `log_dropped` stehen nur in Doku und Tests

* kategorie: INFO (Hinweis an den Planner)
* quelle: `SPEC-052`, [ADR-0020](../plan/adr/0020-logformat-und-observability-schnittstelle.md)
* pfad: `docs/user/einbettung-und-betrieb.md` · „in der Schreibweise mit Unterstrich“
* befund: Die Spezifikation und die ADR sagen „Felder des Ereignisses“ und „letzte Zeile (`dropped`)“; die Schreibweise (`error_code`, `exception_type`, `issue_kind`) und der Ereignisname `log_dropped` sind Setzungen des Adapters, auf die die Skripte (`error_code`, `cause`, `reason`, `tables`) und Tests bauen. Die Zahl 5 in `"tables":5` (`tools/simulationstest.sh`) dupliziert die Tabellenzahl aus `IntegrationServer.kt` ohne Sensor.
* verifizierbar: nein
* klasse: Setzung ohne Heimat in der Spezifikation

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `LogAdapter.kt`: Warteschlange 1024, `offer` nicht blockierend, `dropped` in der nächsten Zeile, `startWriter` vs. `writeDirect`, Daemon-Thread, `close` mit Frist (m1, m2, m3 rot) | geprüft, ohne Befund außer F-1, F-8 |
| `TelemetryJson.kt`: ASCII-Escaping, DEL, Ersatzzeichenpaar, einzelnes Ersatzzeichen, U+2028, Feldnamen gegen `TelemetryEvent`; gültiges JSON je Zeile am Prozess (`FlatJson`, streng) | geprüft, ohne Befund außer F-3 |
| `LogLevelSetting.kt`, `Main.kt`: Filter nach Ordinal, ungültiger Wert bricht mit Exit 1 ab, nur Schlüssel und Art in der Meldung, Bereitschaftszeile unverändert und synchron, `gestoppt` zuletzt (Normalfall) | geprüft, ohne Befund |
| [TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit): Mutation m5 (Kennung in einer Ablehnung) wird erkannt; `principal` steht nur in `login_accepted`; Passwort, Prüfwert, Kennung der Ablehnung in keiner Zeile; TLS-Fehler am Prozess in `Tds8WiringTest` (`tls_handshake_failed`, Wörterliste) | geprüft, ohne Befund |
| Abhängigkeit `jackson-core`: nur `testserver`, Katalogeintrag mit `jackson`-Version, `gradle.lockfile` und Prüfsummen unverändert; der strenge Docker-Bau (Sperre) bestand in jedem Lauf; Jackson-Sperre und Modulgrenze-Sperre in `make detekt` grün; `lib` der Bibliothek ohne Jackson (`make build` im `gates`-Lauf) | geprüft, ohne Befund (Beleg trägt: eine fehlende Sperre bräche den Bau) |
| E2E-Skripte: Muster treffen die Felder (`event`, `reason`, `error_code`, `cause`, `tables`); keine Kennzeichnung der Herausnahme übrig; Ersatz für `Handlerfehler` prüft `connection_failed` und `adapter_failed` (statt eines Ereignisses „Adapterfehler“, gleiche Aussage); `make e2e` 8 Stufen grün | geprüft, ohne Befund außer F-5, F-6 |
| Spec: Vermerk in `SPEC-052` und Änderungsprotokoll fügen keine Zusage hinzu, die das Lastenheft nicht trägt; `LOG_LEVEL` als eigene Datei statt `EnvKeys` ist in §3 geplant und berührt `ConfigKeys` nicht | geprüft, ohne Befund außer F-2 |
| DoD-Haken: keine gesetzt (alle `[ ]`), kein Vorgriff auf die Verifikation; Closure-Notiz §7 steht „Wird bei der Closure gefüllt“ | geprüft, ohne Befund |
| Zweite Fundstellen: Grep nach „schreibt kein Log“, „Ein Log liefert die Bibliothek nicht“, „nicht gebaut“ in `docs/user`, `spec`, `docs/plan`: Aussagen über die Bibliothek bleiben wahr, `docs/user/einbettung-und-betrieb.md` (Grenzen) nachgezogen | geprüft, ohne Befund außer F-4 |
| Hard Rules §3.1 (Docker-only: alle Läufe über `make`), §3.2 (kein `@Suppress`; `make detekt` grün), §3.5 (keine ADR geändert), §3.6 (kein Gate gelockert) | geprüft, ohne Befund |
| `src/main`: nur KDoc in `TelemetrySink.kt`, keine Schichtregel berührt | geprüft, ohne Befund |
| Merge mit `main` (7bb31eb0) | geprüft: nur Textkonflikte in Doku und erzeugter Abdeckung (siehe oben) |

## Summary

0 HIGH, 2 MEDIUM, 4 LOW, 4 INFO. Zu bearbeiten vor der Verifikation: F-1 (Test für die Frist von `writeLast`) und F-2 (Plan-Tabelle und Kopf nachziehen). Die Verifikation gegen die DoD ist Aufgabe des Verifiers.
