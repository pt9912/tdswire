# Review-Bericht: slice-betrieb-zustandsereignisse (HEAD b7bd6203)

* Rolle: Reviewer (Modul 10), frischer Kontext. Gegenstand: Diff `32fcc71b..HEAD` (Commits `23ea4a07`, `94118d92`, `b7bd6203`; davor die Lifecycle-Commits des Planners), Zweig `worktree-agent-a0206a0d2b6e5d78e`. Maßstab: der Slice (Plan, DoD, Rückführungen), [TDS-FA-09](../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit), [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität), [ADR-0020](../plan/adr/0020-logformat-und-observability-schnittstelle.md) (`Accepted`), [ADR-0021](../plan/adr/0021-oeffentlicher-integrations-einstieg.md), [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) (Nr. 4 und 5), `SPEC-005`, `SPEC-006`, `SPEC-007`, `SPEC-046`, `SPEC-052`, `SPEC-059`, Abschnitt 4 der Spezifikation, `AGENTS.md` §3.1 bis §3.8. Nicht geprüft: die DoD (Verifier).
* Selbst gefahren: `make gates` (Exit 0, Stempel gültig, Gruppen übersprungen), `make gates FORCE=1` (Exit 0; die Stage `test` kam dabei aus dem Layer-Cache, siehe F-1), `make e2e` (8 Stufen grün, 223 s; Stufe 1 meldet „1 s“, also aus dem Cache), `make matrix-zitate` (**Exit 2**: `TelemetryEmbeddingTest > eineSenkeEmpfaengtDieEreignisseUndDieStandardausgabeBleibtLeer()` FAILED, Last des Hosts 40 bis 42), danach `docker build --target test --no-cache-filter test .` (BUILD SUCCESSFUL, 4 min 8 s, derselbe Test PASSED), `make smoke` frisch (`--no-cache-filter smoke`) dreimal grün (je Test unter 2 s, Stage 1 min 46 s bis 2 min 5 s) und einmal unter hoher Last (Last 40 und mehr, 40 Rechenschleifen zusätzlich) grün (2 min 56 s). `make flaeche` und `make docs-check` liefen als Teil von `make gates` grün. Die Konflikte mit `main` (`f0886534`) und dem Zweig der Nutzer-Doku sind unter „Konflikte“ bewertet.
* Der Arbeitsbaum des Zweigs ist unverändert (alle Mutationen in einer Kopie unter dem Scratchpad, Gradle mit `--continue`).

## Gesamturteil

0 HIGH, 7 MEDIUM, 3 LOW, 5 INFO. Die Mechanik ist in der Sache tragfähig: Reihenfolge der Ereignisse am Server, Abbildung der Marken, Schlüsselspeicher nur als `SET`/`UNSET`, `MAX_MESSAGE_BYTES` wirkt nach dem Login und ist geprüft. Die Mutationen sind rot. Offen sind ein flackernder Gate-Test (F-1), ein reproduzierter Wettlauf von zwei `stop()`-Aufrufen gegen die Zusage der Reihenfolge (F-2), zweite Fundstellen in Nutzer-Doku und Spezifikation (F-3, F-4), die Redaktion von `settings`, die auf einer Funktion und einem Test ruht statt auf der Bauart (F-5), ein Prozesstest-Kommentar, der mehr behauptet als er belegt (F-6), und der Kopf „Berührte Spec-Stellen“ (F-7). Keine Haken in der DoD gesetzt (Negativbefund unten).

## Mutationen und Gegenproben (selbst gefahren)

Kopie des Baums im Scratchpad, Stage `smoke` mit angepasstem Test-Filter, Gradle `--continue`; Wirkung jeweils gemessen.

| Mutation | Wirkung gemessen |
|---|---|
| `TdsServer.stop`: `SERVER_STOPPING` nach `listener.shutdown()` statt davor | rot: `ZustandsereignisseTest > Beenden meldet Beginn, Ende der Verbindung und Ende, und stop kehrt nach dem Ereignis zurueck` (1 von 9 Tests der Auswahl). `Ein Lauf meldet Start, Verbindung, …` bleibt grün (die Verbindung ist dort vor `stop` beendet), `TelemetryEmbeddingTest` lief in dieser Auswahl nicht |
| `ListenerEvent.Closed` ergibt kein Zustandsereignis (`CONNECTION_CLOSED` nie gemeldet) | rot: 4 von 16 (`Jedes Ende einer Verbindung hat einen Grund gleichen Namens`, `Ein Lauf meldet Start, …`, `Beenden meldet Beginn, …`, `Eine Verbindung ueber dem Limit …`) |
| `REFUSED_AT_SHUTDOWN` wird auf die Marke `REFUSED_AT_LIMIT` abgebildet | rot: `Jedes Ende einer Verbindung hat einen Grund gleichen Namens` (1 von 46); nur dieser Test hält die Abbildung, am Server entsteht die Marke in keinem Test |
| `effectiveSettings`: Wert von `TLS_KEYSTORE_FILE` ist der Inhalt des Speichers (`String(tls.keystore)`) | rot: `ZustandsereignisseTest > Die Einstellungen enthalten weder Schluesselinhalt noch Passwort noch Benutzerliste` (1 von 24); `TelemetryRedactionTest`, `BootstrapTest` und `SecurityConfigNegativeTest` bleiben grün — den Inhalt hält nur der eine Test, der die Funktion direkt aufruft |
| zwei `stop()`-Aufrufe nebenläufig, die Senke hält den ersten bei `SERVER_STOPPING` an, eine Verbindung mit halber Nachricht ist offen (Wegwerf-Test in der Kopie) | `SERVER_STOPPED` steht in der Senke, bevor `CONNECTION_CLOSED` gemeldet ist; der erste Aufruf kehrt zurück, während die Verbindung noch besteht: `[CONFIG_EFFECTIVE, SERVER_STARTED, SECURITY_WEAKENED, SECURITY_WEAKENED, CONNECTION_OPENED, SERVER_STOPPING, SERVER_STOPPED]` (F-2) |
| `TelemetryEmbeddingTest.eineSenke…` 60 Mal in einer Schleife (Kopie) | in 1 von 60 Läufen steht `CONNECTION_CLOSED` vor `SERVER_STOPPING` (F-1) |

## Findings

### F-1 MEDIUM — Einbettungstest erwartet die Reihenfolge eines Wettlaufs

* kategorie: MEDIUM
* quelle: Maintainability; `AGENTS.md` §3.8 (der Test behauptet eine Reihenfolge, die der Server nicht zusagt)
* pfad: `src/integrator/kotlin/io/github/pt9912/tdswire/integrator/TelemetryEmbeddingTest.kt` · „EventKind.SERVER_STOPPING,\n                EventKind.CONNECTION_CLOSED,\n                EventKind.SERVER_STOPPED,“ (Hilfsfunktion `serve`: `WireClient(…).use { … }` schließt den Client, danach `server.stop()`)
* befund: `serve` schließt die Verbindung vom Client aus und ruft danach `stop()` auf, ohne auf `CONNECTION_CLOSED` zu warten; ob der Server das Ende vor oder nach dem Beginn des Beendens sieht, ist ein Wettlauf (Marke `HANDLER_RETURNED` oder `SHUTDOWN`). Gemessen: 1 von 60 Läufen in einer Schleife in derselben Stage; `make matrix-zitate` war einmal rot an genau diesem Test (frischer Lauf, Last 40 bis 42), der Folgelauf der Stage grün. Der Test stand in `make gates` grün, weil die Stage `test` bei unverändertem Kontext aus dem Cache kommt.
* verifizierbar: ja (`docker build --target test --no-cache-filter test .` mehrfach, oder der Test in einer Schleife)
* klasse: Test erwartet die Reihenfolge eines Wettlaufs

### F-2 MEDIUM — Zusage „`SERVER_STOPPED` nach dem Ende jeder Verbindung“ gilt nicht bei zwei nebenläufigen `stop()`

* kategorie: MEDIUM
* quelle: [ADR-0020](../plan/adr/0020-logformat-und-observability-schnittstelle.md) Frage 7; `SPEC-059`; Reviewer-Skill „Zusage ohne ihre gemessene Bedingung im selben Satz“
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/runtime/TdsServer.kt` · „val first = stopped.compareAndSet(false, true)“
* befund: Der Aufruf, der das Flag setzt, gibt `SERVER_STOPPING` erst danach an die Senke; ein zweiter Aufruf in diesem Fenster führt `listener.shutdown()` aus, der erste findet es bereits begonnen (`beginShutdown()` ergibt `null`), kehrt sofort zurück und gibt `SERVER_STOPPED` ab, obwohl Verbindungen noch bestehen (gemessen, Tabelle oben); die Reihenfolge `SERVER_STOPPING` vor dem Schließen des Listeners gilt dort ebenso nicht. KDoc von `stop` und `EventKind.SERVER_STOPPED` sagen es ohne Bedingung; die Bedingung („Aufruf von mehreren Threads … nicht festgelegt“) steht nur in der Tabelle `TdsServer` des Abschnitts 5 der Spezifikation. Das Fenster ist so groß wie die Senke blockiert (die Senke darf nach [ADR-0020](../plan/adr/0020-logformat-und-observability-schnittstelle.md) blockieren).
* verifizierbar: ja (Test mit angehaltener Senke und zwei Aufrufen; kein Test des Diffs deckt die Nebenläufigkeit)
* klasse: Zusage ohne ihre Bedingung im selben Satz

### F-3 MEDIUM — Nutzer-Doku: „Nicht einstellbar … `MAX_MESSAGE_BYTES`“ widerspricht der neuen Tabellenzeile

* kategorie: MEDIUM
* quelle: Reviewer-Skill „Fortschreibung lässt die zweite Fundstelle stehen“; `SPEC-007`
* pfad: `docs/user/einbettung-und-betrieb.md` · „**Nicht einstellbar.** `MAX_TDS_PACKET_BYTES` und `MAX_MESSAGE_BYTES` sind Konstanten der Spezifikation und kein Feld der Konfiguration.“
* befund: Dieselbe Datei führt zwei Absätze darüber die Zeile `requests.maxMessageBytes` | `MAX_MESSAGE_BYTES` mit Bereich 65535 bis 268435456; die alte Lesart steht unverändert da. Die Datei wird im Zweig der Nutzer-Doku (`worktree-agent-a9764e63162fbe637`) neu geschrieben und führt dort `MAX_MESSAGE_BYTES` nicht mehr; der Merge der beiden Zweige hat in dieser Datei einen Inhaltskonflikt, und die Änderungen dieses Slice (Zeile des Schlüssels, Ereignisarten, Felder) müssen in die neue Fassung getragen werden.
* verifizierbar: ja (Grep `MAX_MESSAGE_BYTES` in `docs/user`; `make docs-check` fängt es nicht)
* klasse: Fortschreibung lässt die zweite Fundstelle stehen

### F-4 MEDIUM — Spezifikation Abschnitt 4: „kein Freitextfeld“, „die übrigen als Text“ und die Aufzählung der Textfelder nennen `settings` und die Adresse von `SERVER_STARTED` nicht

* kategorie: MEDIUM
* quelle: Reviewer-Skill „Fortschreibung lässt die zweite Fundstelle stehen“; `AGENTS.md` §3.8
* pfad: `spec/spezifikation.md` · „typisiert sind\n  sie nach dem Feld (`port`, `tables` als Zahl, die übrigen als Text)“ und „Zeichenketten\n  stehen in `principal`, in der Adresse des Bindefehlers, im Typnamen einer Ausnahme“
* befund: Das Feld `settings` ist eine Abbildung von Text auf Text (weder Zahl noch Text), und `address` kommt bei `SERVER_STARTED` hinzu; die Aufzählung der Zeichenketten-Felder in „Redaktion durch Bauart“ und die Aussage „kein Freitextfeld“ im selben Abschnitt sind nicht nachgezogen, die Änderungsliste des Implementers nennt sie nicht. Die Tabelle `TdsServer` des Abschnitts 5 (`stop` … „Aufruf von mehreren Threads … nicht festgelegt“) sagt zu den neuen Ereignissen nichts.
* verifizierbar: ja (Grep der Aussagen in Abschnitt 4)
* klasse: Fortschreibung lässt die zweite Fundstelle stehen

### F-5 MEDIUM — Redaktion von `settings` ruht auf einer Funktion und einem Test statt auf der Bauart; der Redaktionstest wurde dafür gelockert

* kategorie: MEDIUM
* quelle: [ADR-0020](../plan/adr/0020-logformat-und-observability-schnittstelle.md) („Was die Bauart trägt“: für Pfad, Prüfwert und Passwort hat das Modell kein Feld; eine geschlossene Menge mit typisierten Feldern, kein Freitextfeld); [TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit)
* pfad: `src/test/kotlin/io/github/pt9912/tdswire/runtime/TelemetryRedactionTest.kt` · „type in ALLOWED_TYPES || type.isEnum || (type == Map::class.java && name == \"settings\")“ und `src/main/kotlin/io/github/pt9912/tdswire/domain/telemetry/TelemetryEvent.kt` · „public open val settings: Map<String, String>?“
* befund: `ConfigEffective` nimmt jede Abbildung von Text auf Text an; dass kein Pfad, Passwort oder Prüfwert darin steht, hält allein `effectiveSettings` und der Test `Die Einstellungen enthalten weder …` (Mutation „Inhalt des Speichers als Wert“: nur dieser eine Test wird rot). Der Redaktionstest, der Typen und Textfelder je Art prüft, erlaubt den Typ `Map` namentlich für `settings` und prüft dessen Inhalt nicht; die Adresse von `SERVER_STARTED` ist in der Liste der Textfelder ergänzt. Ob das noch „Redaktion durch Bauart“ ist oder eine Abweichung von [ADR-0020](../plan/adr/0020-logformat-und-observability-schnittstelle.md) (Modell mit typisierten Feldern), entscheidet der Architect; bei Lesart „ADR-Verstoß“ wäre die Einstufung HIGH, hier MEDIUM, weil Schlüsselspeicher, Passwort und Benutzerliste in der Bibliothek keinen Pfad kennen und kein Weg in die Abbildung führt, der gemessen wurde.
* verifizierbar: ja (Mutation wie in der Tabelle; `make test`)
* klasse: Redaktion durch Bauart durch Konvention ersetzt

### F-6 MEDIUM — Prozesstest: „das Ende des Prozesses belegt, dass kein Thread des Servers weiterläuft“

* kategorie: MEDIUM
* quelle: `AGENTS.md` §3.8; Slice DoD („danach laufen weder Listener noch Threads“); Beobachtung `BEO-TDS/schranken-test-mit-ungeprueftem-zaehler` (im Plan §8 als berührt genannt)
* pfad: `src/test/kotlin/io/github/pt9912/tdswire/runtime/ZustandsereignisseProzessTest.kt` · „Das Ende des\n     * Prozesses belegt, dass kein Thread des Servers weiterläuft“
* befund: Derselbe Kommentar sagt, der Prozess ende über `exitProcess`; ein Prozess, der über `exitProcess` endet, endet auch mit laufenden Threads. Belegt ist der freie Port und der Exit-Code, nicht das Ende der Threads (kein Zähler der Threads mit Untergrenze am Prozess); die Threads des Listeners belegen die Tests des Listeners in-process.
* verifizierbar: ja (ein nicht beendeter Nicht-Daemon-Thread im Startabbruch würde den Test nicht röten)
* klasse: Aussage breiter als ihr Beleg

### F-7 MEDIUM — Kopf „Berührte Spec-Stellen“ nennt die berührten Stellen nicht

* kategorie: MEDIUM
* quelle: Reviewer-Skill „Plan-Tabelle nennt die berührten Dateien nicht“
* pfad: `docs/plan/planning/in-progress/slice-betrieb-zustandsereignisse.md` · „**Berührte Spec-Stellen:** SPEC-005 · SPEC-009 · SPEC-010 · SPEC-033 · SPEC-034 · SPEC-043 · SPEC-044 · SPEC-047“
* befund: Der Diff ändert `SPEC-006`, `SPEC-007`, `SPEC-052` (Zeile des Testservers), `SPEC-059` (Prüfung der Konfiguration) und Abschnitt 4 (Ereignismodell, Redaktion); `SPEC-006`, `SPEC-007`, `SPEC-052` und `SPEC-059` stehen im Kopf nicht, `SPEC-046` und `SPEC-061` als Teil der Prüfzeile ebenso nicht. Die Plan-Tabelle in §3 ist gegen den Diff gelesen: alle berührten Dateien stehen dort, einzeln oder unter „Anpassungen bestehender Tests“ (`ConfigValidationTest`, `TelemetryEventTest`, `SecurityConfigNegativeTest`, `EnvironmentConfig.kt` nur dort); die Konfigurationstabelle und die Zuordnung *Test | Festlegung* stehen laut Slice in der Closure-Notiz, die §7 noch nicht trägt (Aufgabe des Planners).
* verifizierbar: ja (Abgleich der `SPEC-`-IDs im Diff mit dem Kopf)
* klasse: Plan nennt die berührten Stellen nicht

### F-8 LOW — Abgebrochenes `stop()` meldet kein `SERVER_STOPPED`; die Art sagt „genau ein Ereignis je Server“

* kategorie: LOW
* quelle: `AGENTS.md` §3.8; [ADR-0020](../plan/adr/0020-logformat-und-observability-schnittstelle.md) Frage 7
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/domain/telemetry/TelemetryEvent.kt` · „Der Server ist beendet; genau ein Ereignis je Server, nach dem Ende jeder Verbindung.“
* befund: `TdsServer.stop` dokumentiert, dass das Ereignis zum Ende bei abgebrochenem Aufruf entfällt (der Listener räumt trotzdem auf, ein späterer `stop()` meldet nichts mehr, weil das Flag gesetzt ist); die KDoc der Art und der Abschnitt 4 sagen „genau ein Ereignis“ ohne diese Bedingung, und kein Test fährt den Abbruch.
* verifizierbar: ja (Test mit abgebrochener Coroutine)
* klasse: Zusage ohne ihre Bedingung im selben Satz

### F-9 LOW — Voreinstellung 16 MiB steht zweimal

* kategorie: LOW
* quelle: Maintainability; KDoc von `ConfigDefaults` („Die Konstruktoren der Einstellungen nutzen dieselben Werte“)
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/config/ServerConfig.kt` · „const val MAX_MESSAGE_BYTES = 16 * 1024 * 1024“ neben `src/main/kotlin/io/github/pt9912/tdswire/transport/framing/FramingLimits.kt` · „const val MAX_MESSAGE_BYTES = 16 * 1024 * 1024“
* befund: Zwei unabhängige Konstanten tragen denselben Wert; die Voreinstellung des Servers (`ConfigDefaults`) und die von `FramingLimits()` können auseinanderlaufen, und kein Test hält sie gleich (der Test der Voreinstellung nennt das Literal 16_777_216).
* verifizierbar: ja (Mutation eines Wertes)
* klasse: Voreinstellung doppelt gehalten

### F-10 LOW — Reihenfolge der Ereignisse von `Bootstrap`: Annahme beginnt vor `CONFIG_EFFECTIVE`

* kategorie: LOW
* quelle: Spezifikation Abschnitt 4 („genau ein Ereignis je Start, vor `SERVER_STARTED`“), `AGENTS.md` §3.8
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/runtime/Bootstrap.kt` · „telemetry.emit { ConfigEffective(it, config.effectiveSettings()) }“
* befund: `running(listener)` gibt `CONFIG_EFFECTIVE` und `SERVER_STARTED` ab, nachdem der Listener schon bindet und annimmt; ein Client mit festem Port kann `CONNECTION_OPENED` vor beiden auslösen. Nicht gemessen; die Zusage der Spezifikation nennt nur „vor `SERVER_STARTED`“, die Prozess- und Bibliothekstests verbinden erst nach der Bereitschaft.
* verifizierbar: nein (nicht gefahren)
* klasse: Reihenfolge der Ereignisse im Fenster zwischen Bindung und Meldung

### I-1 INFO — Stufe 1 des E2E „1 s“

Die Stufe läuft über `make smoke` und kam beim Review aus dem Layer-Cache; die frische Dauer der Stage liegt bei 1 min 46 s bis 2 min 5 s (davon ein Teil Kompilierung), je Test der neuen Klasse unter 2 s. Vier frische Läufe der Stage waren grün (einer unter Last 40 und mehr, 2 min 56 s). Das Muster „Cache verdeckt Wiederholung“ ist als `BEO-TDS/test-stage-cache-verdeckt-wiederholung` geführt; F-1 ist ein weiterer Beleg dafür. Die Einordnung in Stufe 1 statt einer neunten Stufe stimmt mit `tools/e2e.sh`, `docs/plan/e2e-abdeckung.md` und `harness/README.md` überein (`make e2e-selbsttest` lief als Teil von `make gates` grün).

### I-2 INFO — Marken nur an der Abbildung geprüft

`HANDLER_FAILED`, `LOGIN_TIMEOUT`, `SHUTDOWN_GRACE_EXPIRED` und `REFUSED_AT_SHUTDOWN` sind am Server nicht belegt, nur ihre Abbildung (`Jedes Ende einer Verbindung hat einen Grund gleichen Namens`, Mutation gefahren: rot). Die Spezifikation sagt es so („am Server die Marken `HANDLER_RETURNED`, `SHUTDOWN` und `REFUSED_AT_LIMIT`“). Das Entstehen am Listener belegen `ListenerShutdownTest` und `ListenerLimitTest` (mit einem abweisenden Register). `REFUSED_AT_SHUTDOWN` ist ein Zustand im Wettlauf von Annahme und Beginn des Beendens; am Server ohne Hilfsmittel nicht reproduzierbar, die Marke ist damit eine Zusage ohne Entstehungstest am Server, aber mit Entstehungstest am Listener, und als solche gekennzeichnet.

### I-3 INFO — Öffentliche Fläche und Quellkompatibilität

`make flaeche` grün (kein neuer Typname). Neu an öffentlichen Typen: vier Konstanten von `EventKind`, `TelemetryEvent.settings`, `RequestSettings.maxMessageBytes`. [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 4 zählt neue öffentliche Namen unterhalb der Typebene (Konstruktor, Parameter, Feld) in der ADR auf; für diese Mitglieder trägt die Spezifikation die Setzung ([ADR-0021](../plan/adr/0021-oeffentlicher-integrations-einstieg.md) Entscheidung 3: ADR oder Spezifikation), eine ADR nennt sie nicht ([ADR-0020](../plan/adr/0020-logformat-und-observability-schnittstelle.md) legt die Menge der Arten dem Bau). Ein `when` des Integrators über `EventKind` ist nicht mehr erschöpfend; Abschnitt 4 sagt „jede neue Art bricht es bei der Übersetzung“, der Adapter des Testservers (`TelemetryJson`, `when` ohne `else`) und die Einbettungstests sind nachgezogen. Vor 1.0 ist das gewollt; ob die Aufnahme von Mitgliedern in die Liste der Fläche eine Entscheidung braucht, ist Sache des Architects.

### I-4 INFO — [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 5 („Obergrenze von 16 MiB je Nachricht 0x07“) und Speicher ([TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität))

Mit `MAX_MESSAGE_BYTES` ist 16 MiB die Voreinstellung; die Zusage der Grenze, die [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 5 dem Folge-Slice des Massenladens überträgt, muss „Voreinstellung“ nennen. Der Bereich bis 268435456 Byte je Nachricht und Verbindung ist als Festlegung benannt, ohne Aussage zum Heap-Bedarf bei der Zahl der Verbindungen (Voreinstellung 1000); `OutOfMemoryError` fängt der Anfragepfad nach `SPEC-031`. Vor dem Login gilt weiter `MAX_PRELOGIN_MESSAGE_BYTES` (in `SPEC-007` abgegrenzt, `MessageLimitWiringTest` hält die Wirkung nach dem Login). Zusammenspiel mit `MAX_CELLS` des anderen Zweigs nicht geprüft (nicht im Diff).

### I-5 INFO — Zeile `make smoke` in `harness/README.md`

Die Zelle „Tut was“ misst 200 Zeichen ohne die Randleerzeichen, genau die Schwelle von `cell-max-chars`; `make docs-check` ist grün. Die nächste Ergänzung der Zeile bricht das Gate.

## Konflikte (nur bewertet)

* Mit `main` (`f0886534`): `git merge-tree` meldet einen Inhaltskonflikt in `docs/plan/planning/welle-betrieb-diagnose.md` (zwei Zeilen: Verweis auf den Slice in `in-progress/`), die übrigen Planungsdateien mergen automatisch. Kein Konflikt im Code.
* Mit dem Zweig der Nutzer-Doku (`worktree-agent-a9764e63162fbe637`): Inhaltskonflikt in `docs/user/einbettung-und-betrieb.md`; `harness/README.md` mergt automatisch. Siehe F-3.

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `src/main/kotlin/…/domain/telemetry` (Modell, Marken, Kürzung) | geprüft, ohne Befund über F-4 und F-5 hinaus; neue Klassen internal, Ebenen INFO je Art |
| `src/main/kotlin/…/runtime` (`Bootstrap`, `TelemetryEvents`, `ConnectionWiring`) | geprüft, ohne Befund über F-10 hinaus; `ConnectionOpened` trägt keine Adresse der Gegenstelle, `SERVER_STARTED.address` ist die konfigurierte Bind-Adresse (kein Geheimnis, in Test und Spezifikation benannt) |
| `src/main/kotlin/…/config`, `transport/messaging` (`MAX_MESSAGE_BYTES`) | geprüft, ohne Befund über F-9 hinaus; Grenze wirkt nach dem Login, Bereich 65535 bis 268435456 als Festlegung benannt, Schlüssel in `ConfigDefaults`, Validierung, Lader, Tests |
| `testserver/kotlin`, `testserver/test` | geprüft, ohne Befund; `when` über `EventKind` vollständig, `config_effective` als ein Feld je Einstellung, kein Pfad |
| `src/test`, `src/integrator` (angepasste Tests) | geprüft: `BootstrapTest` entfernt die Bind-Adresse aus den verbotenen Wörtern (begründet, die Adresse steht in `SERVER_STARTED`; Pfad- und Speichernamen sind neu verboten), `LoginThrottleWiringTest` nimmt `ServerStarted` aus der Adressprüfung (die Gegenstelle bleibt geprüft), `TelemetryRedactionTest` siehe F-5, `TestserverLogProcessTest` rechnet die Ereigniszahlen um (keine Prüfung entfernt); Zusage der Marken siehe I-2 |
| Konfigurationsabdeckung (Konstante, Schlüssel, Umgebungsname, Test) | gegen Code, Spezifikation (Abschnitt 3) und Tests nachgeprüft: `LISTEN_PORT`, `MAX_MESSAGE_BYTES`, `LOGIN_TIMEOUT`, `MAX_PRELOGIN_MESSAGE_BYTES`, `SHUTDOWN_GRACE`, `QUERY_TIMEOUT`, `ENCRYPTION_POLICY`, `CANCEL_TIMEOUT`, `MAX_CONNECTIONS` haben Schlüssel, Prüfung und Test; `MAX_TDS_PACKET_BYTES` ist Konstante ohne Schlüssel mit Begründung in `SPEC-006`; `QUERY_TIMEOUT` wirkt (Verdrahtung in `ConnectionWiring`); ohne Befund |
| `Dockerfile`, `tools/e2e.sh`, `tools/mk/smoke.mk`, `harness/README.md` | geprüft, ohne Befund über I-1 und I-5 hinaus; Stage `smoke` führt beide Prozesstest-Klassen, Deklaration in `tools/e2e.sh` und `docs/plan/e2e-abdeckung.md` gleich |
| Suppression, Host-Aufrufe, Schichtregeln, `detekt.yml` | geprüft, ohne Befund (`make gates` grün; kein `@Suppress`, keine Lockerung) |
| DoD-Haken im Slice | geprüft, ohne Befund: kein Liefer-Punkt und keine Nachweis-Pflicht als `- [x]` gesetzt, kein Rollen-Text im Wortlaut |
| Accepted-ADR | geprüft, ohne Befund: [ADR-0020](../plan/adr/0020-logformat-und-observability-schnittstelle.md), [ADR-0021](../plan/adr/0021-oeffentlicher-integrations-einstieg.md), [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) unverändert |
| Kennungen als Link in neuen Dokumenten | geprüft, ohne Befund (`make docs-check` grün) |
| Vorherige Findings am Modul (Reviews zu Log-Schnittstelle, Adapter, Fläche) | herangezogen; das Muster „zweite Fundstelle stehen gelassen“ trat in diesem Lauf zweimal auf (F-3, F-4), im Vorlauf zu `slice-oeffentliche-flaeche-verkleinern-und-belegen` einmal |

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 7 |
| LOW | 3 |
| INFO | 5 |

**Finding-Klassen dieses Laufs:** Test erwartet die Reihenfolge eines Wettlaufs · Zusage ohne ihre Bedingung im selben Satz (2×) · Fortschreibung lässt die zweite Fundstelle stehen (2×) · Redaktion durch Bauart durch Konvention ersetzt · Aussage breiter als ihr Beleg · Plan nennt die berührten Stellen nicht · Voreinstellung doppelt gehalten · Reihenfolge der Ereignisse im Fenster zwischen Bindung und Meldung

## Verdikt

**Merge-blockierend:** ja — wegen F-1 (ein Gate-Test, der in `make matrix-zitate` rot wurde und in 1 von 60 Läufen rot ist), F-2 (reproduzierte Verletzung der Zusage der Reihenfolge) und F-5 (Entscheidung des Architects über die Form von `settings`). F-3, F-4, F-6 und F-7 sind Fortschreibungen ohne Codeänderung.

**Übergabe:** Findings gehen an den Implementer; F-5 und I-3 zusätzlich an den Architect (Lesart von [ADR-0020](../plan/adr/0020-logformat-und-observability-schnittstelle.md) und [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 4); die Finding-Klassen gehen in die Slice-Closure §7 und den Zähler. Der Report ersetzt keine Verifikation.
