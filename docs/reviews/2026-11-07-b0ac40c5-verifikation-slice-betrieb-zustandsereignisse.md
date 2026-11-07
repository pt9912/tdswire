# Verifikationsbericht: slice-betrieb-zustandsereignisse (HEAD b0ac40c5)

* Rolle: Verifier (Modul 11), frischer Kontext. Gegenstand: Slice `slice-betrieb-zustandsereignisse` (`in-progress/`, Zweig `worktree-agent-a0206a0d2b6e5d78e`, HEAD `b0ac40c5`, Basis `32fcc71b`) gegen die DoD §2, die Plan-Tabelle §3, [TDS-FA-09](../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit), [ADR-0020](../plan/adr/0020-logformat-und-observability-schnittstelle.md), [ADR-0040](../plan/adr/0040-wirksame-konfiguration-im-ereignis-aus-typisiertem-wertobjekt.md), [ADR-0021](../plan/adr/0021-oeffentlicher-integrations-einstieg.md), [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 4 und den Review-Report `2026-11-07-b7bd620-review-slice-betrieb-zustandsereignisse.md` (F-1 bis F-10, I-1 bis I-5). Implementer-Commits des Slice: `23ea4a07`, `94118d92`, `b7bd6203`, Behebung `c007588c`, `dd3662fb`, `ff513800`, `3466329d`, `54a8d095`, `b0ac40c5`; `main` einschließlich [ADR-0040](../plan/adr/0040-wirksame-konfiguration-im-ereignis-aus-typisiertem-wertobjekt.md) (`591a1ab3`) ist eingemergt (`47caf891`).
* Maßstab des Nutzers: „Spec ist Zielbild“; Abweichungen werden sichtbar mit Träger geführt. Verifiziert wird die Zusage (DoD, Plan), nicht der Diff; Belege sind eigene Läufe, nicht die Behauptung des Implementers. Nichts am Produktivcode geändert: alle Mutationen und Schleifen liefen in Kopien unter dem Scratchpad (`git archive HEAD`); `git status` des Arbeitsbaums war nach jedem Lauf leer.
* Die Nachbesserung `b7bd6203..b0ac40c5` hatte keinen zweiten Review; sie ist hier wie ein Review bewertet (Abschnitt „Review der Nachbesserung“), das ersetzt keinen Re-Review durch einen eigenen Kontext, den die Rückführung vorsieht.

## Eigene Läufe (Belege)

Host: Lastmittel 16 bis 33 durch andere Läufe während aller Messungen (kein ruhiger Host; Einzelmessungen). Alle Läufe am Arbeitsbaum bei HEAD `b0ac40c5`.

| Lauf | Ergebnis |
|---|---|
| `make gates FORCE=1` | Exit 0 (zweimal gefahren, Exit-Code des zweiten erfasst). Die Stage `test` kam dabei aus dem Layer-Cache (`#22 CACHED` im Log): der Lauf ist **kein** Beleg für die Tests (Cache-Falle G1), nur für die übrigen Gates und die Stempel |
| `make clean-build` (Gradle ohne Cache, G1) | Exit 0; Stage `test` frisch: 1107 Testfälle `PASSED`, 0 `FAILED` (alle drei Stages `build-jar`, `test`, `detekt` ohne Cache, Exit 0) |
| `make flaeche` | Exit 0 (Stage lief aus dem Cache; Text der Liste in `SPEC-059` unverändert, kein neuer Typname) |
| `make docs-check` | Exit 0, 1136 Dateien, 0 Befunde (vor diesem Bericht) |
| `make matrix-zitate` | Exit 0, 1107 bestandene Testfälle im Bericht, 290 Zitate, 0 ohne `PASSED`, 0 nicht gelesene Zitat-Formen (frische Stage `test`) |
| `make spec-abdeckung` | Exit 0; Gradle-Stufe frisch (Schritt `[spec-abdeckung 8/12] RUN gradle … test integratorTest` mit Ausgabe, nicht gecacht, 1107 `PASSED`); `docs/plan/spec-abdeckung.md` danach **unverändert** (`git status` leer), „9 Festlegung(en) ohne Nachweis“, keine Waise |
| `make e2e` | Exit 0, 8 Stufen, 363 s. Stufe 1 meldet „2 s“: aus dem Cache (Muster `BEO-TDS/test-stage-cache-verdeckt-wiederholung`). `docs/plan/e2e-abdeckung.md` nach dem Lauf unverändert |
| Stufe 1 frisch: `docker build --no-cache-filter smoke --target smoke --progress=plain .` | Exit 0, 3 min 46 s unter Last; 6 Tests `ServerSmokeTest` und 5 Tests `ZustandsereignisseProzessTest` `PASSED` |
| Schleife F-1: `TelemetryEmbeddingTest` in `integratorTest`, `cleanIntegratorTest integratorTest` je Lauf, Daemon, `--offline` | Fassung `b0ac40c5`: **130 von 130 grün** (30 + 100); Kontrolle mit der Fassung des Reviews (`b7bd6203`, ohne Latch): **rot in Lauf 6** (bei Last um 27); die Schleife erkennt den Wettlauf also |
| Schleifen gleicher Klasse im Modul `testserver` | `LogStartTest`, `TelemetryJsonTest`, `LogAdapterTest`: 40 von 40 grün |
| Schleifen `ZustandsereignisseTest` (30), `TelemetryRedactionTest` (20), `ListenerAbortTest` (20), `AuthTimingTest` (20) einzeln | alle grün (Last 17 bis 27) |
| Schleife der vier Klassen zusammen in einer JVM, mit `-q` | **Lauf 5 rot**, Läufe 1 bis 4 grün, Schleife brach ab (26 Tests, 1 rot; der Name des Tests ging mit `-q` verloren); Wiederholung mit sichtbarem Ergebnis (`> /tmp/out.txt`, bei Rot `grep FAILED`): **40 von 40 grün** (Last 16 bis 33). Der eine rote Lauf ist nicht reproduziert und nicht zugeordnet |
| Prozess-Tests `TestserverLogProcessTest` und `ZustandsereignisseProzessTest` in Schleife (12) | 12 von 12 grün |
| Mutationen und Sondermessung | siehe die Abschnitte „Mutationen“ und „Reentranz“ |

## Gesamturteil

**Closure-fähig: nein, solange die Bedingungen 1 bis 4 offen sind** (Closure-Notiz mit Konfigurationstabelle und Zuordnung, Kopf und Plan-Tabelle, die Entscheidung zu `MAX_TDS_PACKET_BYTES`, Haken). Die Zusagen des Slice sind im Code und in den Tests belegt: die vier neuen Arten, die Marken, die Geheimnisfreiheit der wirksamen Konfiguration (jetzt aus der Bauart, [ADR-0040](../plan/adr/0040-wirksame-konfiguration-im-ereignis-aus-typisiertem-wertobjekt.md)), `MAX_MESSAGE_BYTES` mit Bereichsprüfung und Wirkung nach dem Login, die Prozesstests der Startdiagnose. Alle zehn Findings des Reviews sind behoben oder mit einer benannten Grenze versehen; die Nachbesserung selbst ist ohne HIGH, hat aber eine neue gemessene Kante (V-3) und eine nicht zugeordnete rote Messung unter Last (V-7). 0 HIGH, 2 MEDIUM, 5 LOW, 4 INFO.

## DoD-Punkte

| # | DoD-Punkt | Urteil | Beleg |
|---|---|---|---|
| 1 | Zustandsereignisse: wirksame Konfiguration ohne Geheimnisse, Listener gebunden, Beginn und Ende des Beendens, Login angenommen und abgelehnt, Verbindung geöffnet und geschlossen mit Grund, Abschwächung, Anfragefehler mit `error_code`; Sammler und Zeilen des Prozesses; `stop()` kehrt nach dem Ereignis zum Ende zurück | erfüllt, Grenzen benannt | `ZustandsereignisseTest` (Folge und Ebenen, Marken, Beenden mit Sammler bei Rückkehr, zwei nebenläufige `stop`, abgebrochener `stop`), `TelemetryFelderEmbeddingTest` und `TelemetryEmbeddingTest` über die öffentliche Fläche, `TelemetryJsonTest` und `LogAdapterTest` am Adapter, `ZustandsereignisseProzessTest` (Zeilen des Prozesses, frisch grün). Grenzen: Marken `HANDLER_FAILED`, `LOGIN_TIMEOUT`, `SHUTDOWN_GRACE_EXPIRED`, `REFUSED_AT_SHUTDOWN` am Server nicht erzeugt, nur ihre Abbildung (I-2 des Reviews, so in der Spezifikation); `CONNECTION_CLOSED` trägt keine Sitzung (Listener kennt sie nicht, benannt); Reihenfolge `CONFIG_EFFECTIVE` und `SERVER_STARTED` gegen ein frühes `CONNECTION_OPENED` bei festem Port ist nicht gemessen, als Grenze in KDoc und Spezifikation benannt (F-10, V-8) |
| 2 | Startdiagnose am Prozess: belegter Port (Adresse und Port, Exit-Code ungleich null), unbrauchbarer Speicher (Teil ohne Pfad und Inhalt), unsichere Konfiguration abgewiesen, danach weder Listener noch Threads; E2E-Stufe | erfüllt, mit Einschränkung | Fünf Tests `ZustandsereignisseProzessTest`, frisch grün. „Weder Listener noch Threads“: am Prozess belegt sind freier Port und Exit-Code (der Kommentar sagt es jetzt so, F-6 behoben); die Threads belegen `BootstrapTest` (`listenerThreads`, im Prozess des Tests) und `ListenerAbortTest`. E2E: Einordnung in Stufe 1 (`make smoke`) statt einer neunten Stufe, Deklaration `tools/e2e.sh` mit `TDS-FA-09`, `docs/plan/e2e-abdeckung.md` nach `make e2e` unverändert, Dockerfile-Stage `smoke` führt beide Klassen; benannte Setzung des Plans §3, so deklariert |
| 3 | Konfigurationsabdeckung: Tabelle Konstante, Schlüssel, Umgebungsname, Träger, Test in der Closure-Notiz | **teilweise** | Die Tabelle ist nicht geschrieben (§7 „Wird bei der Closure gefüllt“, Sache des Planners); ich habe sie unten nachgeprüft (Abschnitt „Konfigurationstabelle“). Eine Zeile weicht vom Wortlaut der DoD ab: `MAX_TDS_PACKET_BYTES` hat keinen Schlüssel (V-2) |
| N1 | Tests tragen `@spec SPEC-<NNN>`, laufen in `make test`, grün | erfüllt | `make spec-abdeckung` unverändert, 0 Waisen; Tags: `SPEC-005`, `007`, `008`, `009`, `010`, `012`, `033`, `034`, `043`, `044`, `046`, `047`, `048`, `051`, `052`, `059`, `061`; Passung des Tags `SPEC-051` am Schlüsseltest: V-5 |
| N2 | Jede Aussage an einem Test oder als Festlegung; Zuordnung in der Closure-Notiz | offen, erwartet | Closure-Notiz leer; die Festlegungen stehen als solche in der Spezifikation (`SPEC-007` Bereich, Abschnitt 4 „Setzung“ der Marken und Ebenen) |
| N3 | `make gates` grün | erfüllt, mit Cache-Vorbehalt | `make gates FORCE=1` Exit 0 (Stage `test` aus dem Cache); frisch belegt durch `make clean-build` (Exit 0, 1107 grün) |
| N4 | Review durchgeführt, Report liegt vor | erfüllt, Nachbesserung ohne zweiten Review | `docs/reviews/2026-11-07-b7bd620-review-…`; `b7bd6203..b0ac40c5` hier nachgeprüft |
| N5 | Doku-Update bei Vertrag | erfüllt, mit Nachzügen in Nachbarzweigen | Spezifikation (Abschnitt 3, 4, 5, Drift-Log), `docs/user/einbettung-und-betrieb.md`, `harness/README.md`; Nachzug in den Nutzer-Doku-Zweig siehe Abschnitt „Reihenfolge“ |
| N6 | Closure-Notiz mit Lerneintrag | offen, erwartet | §7 leer |
| N7 | Beobachtungs-Register fortgeschrieben | offen, erwartet | im Diff des Slice keine Änderung unter `observations/`; die Finding-Klassen des Reviews (siehe Abschnitt Bedingungen) gehören in die Closure |
| N8 | Jedes Risiko aus §6 trägt einen Ausgang | offen, erwartet | alle sieben „offen bis zur Closure“; Vorschlag der Ausgänge im Abschnitt „Risiken“ |
| N9 | Drei Paarungen | offen bis Welle-Closure | Anker entsteht nur, wenn die Closure etwas verkörpert |
| N10 | Haken | **keiner gesetzt, richtig** | `grep -c '\- \[x\]'` auf dem Slice: 0; kein `[x]` im Diff des Slice. Die Haken setzt der Planner nach Deckung durch diesen Bericht |

## Plan-vs-Code-Diff (§3)

Gelesen ist der Diff der Slice-Commits (nicht der von `main` eingemergten Dateien).

| Zeile des Plans | Diff | Urteil |
|---|---|---|
| `domain/telemetry/TelemetryEvent.kt`: vier Arten, `address`, `settings`, keine neuen öffentlichen Typen | ja; `EffectiveSettings.kt` (neu, `internal`) kommt mit [ADR-0040](../plan/adr/0040-wirksame-konfiguration-im-ereignis-aus-typisiertem-wertobjekt.md) hinzu, `make flaeche` grün | ja; die neue Datei steht nicht in der Tabelle (V-1) |
| `runtime/` (`Bootstrap`, `TdsServer`, `TelemetryEvents`, `ConnectionWiring`) | ja | ja |
| `config/` (`ServerConfig`, `ConfigValidation`), `transport/messaging/TransportHandshakeChannel.kt` | ja, zusätzlich `transport/framing/FramingLimits.kt` (F-9: eine Quelle für 16 MiB) | `FramingLimits.kt` nicht in der Tabelle (V-1); neue Importkante `transport` nach `config` (V-4) |
| `testserver/kotlin` (`TelemetryJson`, `ConfigLoader`, `EnvKeys`) | ja | ja |
| `src/test/.../runtime/` (`ZustandsereignisseTest`, `ZustandsereignisseProzessTest`, `MessageLimitWiringTest`) und Anpassungen | ja; angepasst `BootstrapTest`, `ConfigValidationTest`, `TelemetryEventTest`, `SecurityConfigNegativeTest`, `LoginThrottleWiringTest`, `TelemetryRedactionTest`, `TestserverLogProcessTest`, `EnvironmentConfig.kt`, `RuntimeSupport.kt` | ja (unter „Anpassungen bestehender Tests“); die `@spec`-Tags des Plans (005, 007, 010, 043, 044, 052, 059) sind belegt, zusätzlich 008, 009, 012, 033, 034, 046, 047, 048, 051, 061 |
| `src/integrator/`, `testserver/test/` | ja (`TelemetryFelderEmbeddingTest`, `TelemetryEmbeddingTest`, `TelemetryJsonTest`, `ConfigLoaderTest`, `LogStartTest`, `LogAdapterTest`, `LogTestSupport`) | ja |
| `Dockerfile` (Stage `smoke`), `tools/e2e.sh`, `tools/mk/smoke.mk`, `harness/README.md` | ja | ja; Zelle `make smoke` misst weiter genau 200 Zeichen (I-5 des Reviews unverändert; die nächste Ergänzung bricht `cell-max-chars`) |
| `spec/spezifikation.md`, `docs/user/einbettung-und-betrieb.md`, `docs/plan/e2e-abdeckung.md` | ja | ja; zusätzlich `docs/plan/spec-abdeckung.md` (erzeugt, nach `make spec-abdeckung` unverändert) |
| nicht im Plan | `docs/plan/adr/0040-…` und Index-Zeile (Architect, Nutzerentscheid), `docs/reviews/…b7bd620…` (Reviewer) | begründet, keine Abweichung des Implementers |
| Kopf „Berührte Spec-Stellen“ | nennt `SPEC-005`, `006`, `007`, `009`, `010`, `033`, `034`, `043`, `044`, `046`, `047`, `052`, `059`, `061`, `ARC-002`, `008`, `009` | unvollständig (V-1): getaggt oder berührt sind außerdem `SPEC-008`, `012`, `048`, `051`; berührt sind `ARC-001` (Modell) und `ARC-003` (`FramingLimits`, `TransportHandshakeChannel`), `ARC-009` wird nicht berührt (kein Eingriff in `observability`) |

## Review-Findings F-1 bis F-10: Nachvollzug am HEAD

| ID | Urteil | Beleg |
|---|---|---|
| F-1 (Einbettungstest erwartet Wettlauf) | behoben, eigene 130 Läufe | `TelemetryEmbeddingTest` wartet per `CountDownLatch` auf `CONNECTION_CLOSED` (Wartezeit 20 s) und erwartet `CONNECTION_CLOSED` vor `SERVER_STOPPING`; Schleife siehe oben, Kontrolle mit alter Fassung rot in Lauf 6. Die gleiche Klasse in `testserver/test`: `LogStartTest` (Prüfwort `wertprobe-xq9` ohne Ziffernfolge, die als Zeitstempel oder Port vorkommen kann), `TelemetryJsonTest` und `LogAdapterTest` (`RealEvents.collect(…, settle)` wartet auf die Anmeldungsereignisse): 40 von 40 grün. Weitere Stellen „Client schließen, Server sofort beenden, Ereignis erwarten“ (alle Aufrufer von `stop()` in `src/test`, `src/integrator`, `testserver/test` gelesen): `ZustandsereignisseTest` wartet mit `eventually`, `TelemetryFelderEmbeddingTest` und `TelemetryJsonTest` (Zeile `connection_closed`) beenden mit **offenen** Clients (Grund `SHUTDOWN`, deterministisch), `TestserverLogProcessTest` zählt Ereignisse nach `SIGTERM` ohne vorheriges Warten auf `login_accepted` (die Anmeldeantwort geht vor dem Ereignis hinaus; Fenster Mikro- bis Millisekunden, 12 Läufe und alle Gate-Läufe grün; nicht abgesichert, V-7) |
| F-2 (`stop()` nebenläufig) | behoben, Mutation rot, eine Kante neu (V-3) | `Mutex` um Beginn, `listener.shutdown()` und Ende; Test `Zwei nebenlaeufige stop warten aufeinander …`; Mutation „ohne Sperre“ (`stopLock.withLock {` durch `run {` ersetzt): genau dieser Test rot; Kontrolle des Tests mit zwei Threads ohne Standard-Dispatcher (`ff513800`) stabil in 30 Läufen. `stop` aus dem Handler heraus: unverändert gegenüber vorher (das Beenden wartet auf den Handler bis zur Frist). `stop` aus der Senke: blockiert dauerhaft, gemessen (V-3) |
| F-3 (Nutzer-Doku „Nicht einstellbar“) | behoben im Zweig | Satz nennt `MAX_TDS_PACKET_BYTES` als Konstante und `MAX_MESSAGE_BYTES` als einstellbar; Nachzug in die neue Fassung des anderen Zweigs: Abschnitt „Reihenfolge“ |
| F-4 (Spezifikation Abschnitt 4) | behoben | „Ereignismodell“ nennt `settings` als Abbildung aus dem typisierten Wertobjekt; Aufzählung der Textfelder nennt Bind-Adresse und `settings`; Zeile `TdsServer` des Abschnitts 5 nennt die Sperre und den abgebrochenen Aufruf. Offen, gering: `TDS-FA-09.a` Nr. 3 sagt weiter „Sitzungs- und Fehlerereignisse“, die Zustandsereignisse tragen Abschnitt 4 und [ADR-0040](../plan/adr/0040-wirksame-konfiguration-im-ereignis-aus-typisiertem-wertobjekt.md) (INFO V-9) |
| F-5 (Redaktion von `settings`) | behoben nach [ADR-0040](../plan/adr/0040-wirksame-konfiguration-im-ereignis-aus-typisiertem-wertobjekt.md) Option D | siehe Abschnitt „Option D“ |
| F-6 (Prozesstest-Kommentar) | behoben | Kommentar sagt, was belegt ist (Port, Exit-Code) und was nicht (Threads am Prozess) |
| F-7 (Kopf „Berührte Spec-Stellen“) | **teilweise** | Kopf ergänzt um `006`, `007`, `052`, `059`, `046`, `061`; es fehlen weiter `008`, `012`, `048`, `051` (getaggt) und zwei `ARC` (V-1) |
| F-8 (abgebrochenes `stop`) | behoben | KDoc von `stop` und `SERVER_STOPPED` („höchstens ein Ereignis“, nächster Aufruf gibt es ab), Zeile `TdsServer` der Spezifikation, Test `Ein abgebrochener stop meldet das Ende erst mit dem naechsten Aufruf`. Flackern: Test wartet 500 ms (`delay`) und prüft `first.isCancelled`; in 30 Läufen `ZustandsereignisseTest` unter Last 17 bis 27 nie rot. Die Art `SERVER_STOPPING` sagt noch „genau ein Ereignis je Server“ ohne die Bedingung „wenn `stop` gerufen wird“ (INFO V-10) |
| F-9 (16 MiB doppelt) | behoben | `FramingLimits.MAX_MESSAGE_BYTES = ConfigDefaults.MAX_MESSAGE_BYTES`; eine Quelle; Preis: neue Importkante (V-4) |
| F-10 (Reihenfolge bei festem Port) | Grenze statt Messung | KDoc der Art `CONFIG_EFFECTIVE` und Spezifikation nennen „Reihenfolge dort nicht gemessen“; kein Test, kein Träger außer der Aussage selbst. Das genügt `AGENTS.md` §3.8 (Aussage kleiner als Beleg); ein Test hätte das Fenster zwischen Binden und Meldung herstellen müssen |
| I-1 bis I-5 | I-1 unverändert (Stufe 1 aus dem Cache, frisch gemessen: oben), I-2 unverändert, I-3 siehe Abschnitt „Öffentliche Fläche“, I-4 unverändert, I-5 unverändert (200 Zeichen) | |

## Option D ([ADR-0040](../plan/adr/0040-wirksame-konfiguration-im-ereignis-aus-typisiertem-wertobjekt.md)) und Gegenproben

Die fünf Folgen der ADR sind umgesetzt: (1) `EffectiveSettings` ist `internal`, Felder `Int` und `Boolean` und zwei Aufzählungen (`EncryptionMode`, `KeystoreState` mit `SET` und `UNSET`); (2) `ServerConfig.effectiveSettings()` liefert den Typ, `asMap()` ist die einzige Ableitung (Suche in `src/main` und `testserver/kotlin`: ein Aufrufer, `ConfigEffective`), und `ConfigEffective` ist an genau einer Stelle gebaut (`Bootstrap.running`); (3) `ConfigEffective` nimmt `EffectiveSettings`, die Abbildung ist unveränderlich, die Kürzung entfällt; (4) Reflexionstest `Die wirksame Konfiguration besteht nur aus Zahlen, Aufzaehlungen und Schaltern` (Felder nur `int`, `long`, `boolean` oder Aufzählung; Konstruktor von `ConfigEffective` ohne `String` und `Map`) und der Marken-Test bleiben; (5) Abschnitt 4 trägt den Satz.

**Eine Abweichung vom Wortlaut der ADR:** Folge 2 sagt „Schlüssel aus `ConfigKeys`“. `domain` darf `ConfigKeys` nicht importieren (`ARC-001`), die Schlüssel stehen deshalb als Literale in `EffectiveSettings.asMap()`; ein Test (`Die Schluessel der wirksamen Konfiguration sind die Namen der Konfiguration`) hält die Reihenfolge und Gleichheit mit `ConfigKeys`. Die KDoc sagt es. Die ADR ist `Accepted` und bleibt unverändert; die Closure-Notiz soll die Abweichung nennen (V-6).

Mutationen (Kopien des Baums, Stage mit Auswahl `ZustandsereignisseTest`, `TelemetryRedactionTest`, `BootstrapTest`, `SecurityConfigNegativeTest`, Gradle `--continue`):

| Mutation | Wirkung gemessen |
|---|---|
| `TdsServer.stop` ohne Sperre (`withLock` durch `run` ersetzt; beim Implementer „M14“) | rot: `ZustandsereignisseTest > Zwei nebenlaeufige stop warten aufeinander und kehren nach dem Ende zurueck` (1 Test) |
| Schlüssel in `asMap()` umbenannt (`LOGIN_TIMEOUT` zu `LOGIN_TIMEOUTX`) | rot: `Die Schluessel der wirksamen Konfiguration …` und `Die wirksame Konfiguration nennt jede Einstellung und keinen Pfad` (2 Tests) |
| „Inhalt des Speichers als Wert“ (`keystore = if (tls != null) String(tls.keystore) else KeystoreState.UNSET`) | **Übersetzungsfehler**: `TelemetryEvents.kt:99:16 Argument type mismatch: actual type is 'Comparable<String & KeystoreState> & Serializable', but 'KeystoreState' was expected` — so erwartet von der ADR |
| Kontrolle des Tests von F-1: Fassung `b7bd6203` des Einbettungstests, Schleife | rot in Lauf 6 |

## Reentranz: `stop` aus der Senke (gemessen)

Sondermessung in einer Kopie (Test im Source-Set `integrator`, öffentliche Fläche): eine Senke ruft bei `SERVER_STOPPING` bzw. `SERVER_STOPPED` `runBlocking { withTimeoutOrNull(3000) { server.stop() } }`. Ergebnis beider Fälle: `BLOCKIERT (Zeitgrenze 3 s)`. Ursache: `stop` hält den nicht wiedereintrittsfähigen `Mutex` auch während der Abgabe der Ereignisse an die Senke; ein Aufruf aus der Senke wartet auf sich selbst. Ohne Zeitgrenze wäre es ein Dauerstillstand. Gegenüber der Fassung `b7bd6203` (Flag per `AtomicBoolean`) ist das eine neue Kante (nicht gefahren, aus dem Code gelesen: der geschachtelte Aufruf kehrte dort nach `listener.shutdown()` zurück). Der Vertrag der Senke verbietet Blockieren in `accept` („blockiert nicht und wirft nicht“), `runBlocking` ist ein Verstoß der Senke; aber weder KDoc von `stop` noch von `TelemetrySink` nennen den Aufruf von `stop` aus der Senke (V-3).

## Konfigurationstabelle (nachgeprüft, für die Closure-Notiz)

Konstante der Spezifikation (Abschnitt 3) zu Schlüssel (Feld der `ServerConfig`, Name in `ConfigKeys`), Umgebungsname des Testservers (`EnvKeys`, gleich dem Schlüssel), Prüfung (`ConfigValidation`) und Test.

| Konstante | Schlüssel (Feld) | Umgebungsname | Bereich | Test (Prüfung, Lader, Wirkung) |
|---|---|---|---|---|
| `LISTEN_PORT` (`SPEC-005`) | `listen.port` | `LISTEN_PORT` | 0 bis 65535 | `ConfigValidationTest`, `ConfigLoaderTest` (Bereichsgrenzen), `ZustandsereignisseProzessTest` (belegter Port) |
| `MAX_TDS_PACKET_BYTES` (`SPEC-006`) | **keiner** (Protokollkonstante, 65535; Festlegung in `SPEC-006`) | — | — | Konstante in `FramingLimits`, Aushandlung begrenzt auf sie (`ConnectionWiring`, `LoginResponses`); V-2 |
| `MAX_MESSAGE_BYTES` (`SPEC-007`) | `requests.maxMessageBytes` | `MAX_MESSAGE_BYTES` | 65535 bis 268435456 (Festlegung) | `ConfigValidationTest` (Grenzen), `ConfigLoaderTest` (70000 übernommen, Grenzen 65535 und 268435456), `MessageLimitWiringTest` (Wirkung nach dem Login), `TelemetryFelderEmbeddingTest`, `TelemetryJsonTest` (Ausgabe) |
| `LOGIN_TIMEOUT` (`SPEC-008`) | `login.timeoutSeconds` | `LOGIN_TIMEOUT` | 1 bis 3600 | `ConfigValidationTest`, `ConfigLoaderTest`, `HandshakeWiringTest` |
| `MAX_PRELOGIN_MESSAGE_BYTES` (`SPEC-046`) | `login.maxPreloginMessageBytes` | `MAX_PRELOGIN_MESSAGE_BYTES` | 512 bis 65536 | `ConfigValidationTest`, `ConfigLoaderTest`, `PreloginNegotiationTest`, `MessageLimitWiringTest` |
| `SHUTDOWN_GRACE` (`SPEC-047`) | `requests.shutdownGraceSeconds` | `SHUTDOWN_GRACE` | 0 bis 3600 | `ConfigValidationTest`, `ConfigLoaderTest`, `ShutdownWiringTest` |
| `QUERY_TIMEOUT` (`SPEC-009`) | `requests.queryTimeoutSeconds` | `QUERY_TIMEOUT` | 1 bis 3600 | `ConfigValidationTest`, `ConfigLoaderTest`, `QueryTimeoutTest` (Wirkung) |
| `ENCRYPTION_POLICY` (`SPEC-010`) | `encryption` | `ENCRYPTION_POLICY` | `REQUIRED`, `TEST_UNENCRYPTED` | `ConfigLoaderTest`, `SecurityConfigNegativeTest`, `ZustandsereignisseProzessTest` (unsichere Konfiguration) |
| `CANCEL_TIMEOUT` (`SPEC-033`) | `requests.cancelTimeoutSeconds` | `CANCEL_TIMEOUT` | 1 bis 3600 | `ConfigValidationTest`, `ConfigLoaderTest`, `CancelTimeoutTest` |
| `MAX_CONNECTIONS` (`SPEC-034`) | `listen.maxConnections` | `MAX_CONNECTIONS` | 1 bis 100000 | `ConfigValidationTest`, `ConfigLoaderTest`, `ListenerLimitTest`, `ZustandsereignisseTest` (Marke `REFUSED_AT_LIMIT`) |

Die Ausgänge der Risiken zu `QUERY_TIMEOUT` und `MAX_CONNECTIONS` aus §6 sind damit belegt: beide Schlüssel bestehen und wirken (kein Schlüssel „ohne Wirkung“).

## Öffentliche Fläche: vereinbar mit [ADR-0020](../plan/adr/0020-logformat-und-observability-schnittstelle.md), [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 4 und `SPEC-059`?

Neu und öffentlich sind Mitglieder, kein Typname: vier Konstanten von `EventKind`, `TelemetryEvent.settings` und `TelemetryEvent.address` bei `SERVER_STARTED` (das Feld bestand, die Art setzt es neu), `RequestSettings.maxMessageBytes` (fünfter Konstruktorparameter, Voreinstellung).

* [ADR-0020](../plan/adr/0020-logformat-und-observability-schnittstelle.md): Die Menge der Arten ist dort ein Entwurf, „der Bau legt die endgültige Menge fest“; die vier Arten stehen im Entwurf (Beginn und Ende des Beendens, Verbindung angenommen und beendet) beziehungsweise sind durch [TDS-FA-09](../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose) („relevante Zustandsereignisse“) und die DoD gedeckt (wirksame Konfiguration). Keine Änderung der ADR nötig.
* [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 4: Die Liste dort gilt dem Folgeweg des Massenladens („Jeder neue öffentliche Name steht in dieser Liste“ zählt die Namen **dieser** Entscheidung auf; der Re-Evaluierungs-Trigger der ADR nennt „über die Liste in Nr. 4 hinaus einen öffentlichen Namen“ für ihre Folge-Slices). Die neuen Mitglieder gehören nicht zu ihr. [ADR-0040](../plan/adr/0040-wirksame-konfiguration-im-ereignis-aus-typisiertem-wertobjekt.md) trägt die gleiche Lesart („keine neue Zeile in der Liste von `SPEC-059`“ für Typnamen) und schließt für `settings` einen neuen Namen aus.
* `SPEC-059`: „Bis `1.0` ändert sich diese Fläche nur durch eine Änderung dieser Spezifikation“. Diese Bedingung ist erfüllt: die Spezifikation führt die Mitglieder (Abschnitt 4 Ereignismodell, Ebenen, `SPEC-007`, Zeile „Prüfung“ des Abschnitts 5, Drift-Log 2026-11-07). `make flaeche` vergleicht nur Typnamen und ist grün; Mitglieder hält kein Sensor.
* Quellkompatibilität: ein vollständiges `when` über `EventKind` bricht beim Einbettenden mit der Übersetzung; Abschnitt 4 sagt es („jede neue Art bricht es“), vor `1.0` gewollt. Der Adapter des Testservers ist nachgezogen.

**Urteil:** Eine ADR- oder Spezifikations-Ergänzung ist **nicht Bedingung der Closure**; die Spezifikation trägt die Setzung bereits. Offen als Entscheidungsvorlage für den Architect, nicht für diesen Slice: ob die Fläche ihre Mitglieder gate-geprüft führen soll (heute nur Typnamen), und ob Mitglieder wie `RequestSettings.maxMessageBytes` als positionaler Parameter zusätzlich eine Reihenfolge-Zusage tragen (siehe Reihenfolge: zwei Zweige hängen je einen Parameter an die gleiche Stelle).

## Review der Nachbesserung `b7bd6203..b0ac40c5` (Findings mit Schwere)

Gelesen sind `c007588c`, `dd3662fb`, `ff513800`, `3466329d`, `54a8d095`, `b0ac40c5` gegen Plan, [ADR-0020](../plan/adr/0020-logformat-und-observability-schnittstelle.md), [ADR-0040](../plan/adr/0040-wirksame-konfiguration-im-ereignis-aus-typisiertem-wertobjekt.md), `AGENTS.md` §3.1 bis §3.8. Suppression-Verbot, Docker-only, Schichtregeln (detekt grün in `make gates` und `make clean-build`), keine Gate-Lockerung, keine geänderte `Accepted`-ADR: ohne Befund.

## Findings

Schwere wie im Reviewer-Skill: HIGH, MEDIUM, LOW, INFO.

### V-1 MEDIUM — Kopf „Berührte Spec-Stellen“ und Plan-Tabelle §3 weiter unvollständig

* quelle: Reviewer-Skill „Plan-Tabelle nennt die berührten Dateien nicht“; F-7 des Reviews
* pfad: `docs/plan/planning/done/slice-betrieb-zustandsereignisse.md` · „**Berührte Spec-Stellen:** SPEC-005 · SPEC-006 · SPEC-007 · SPEC-009 …“
* befund: Der Kopf fehlt `SPEC-008`, `SPEC-012`, `SPEC-048`, `SPEC-051` (alle als `@spec` in neuen oder geänderten Tests; `SPEC-012` auch in der DoD) und `ARC-001`, `ARC-003`; die Plan-Tabelle nennt `domain/telemetry/EffectiveSettings.kt` (neu) und `transport/framing/FramingLimits.kt` nicht. Der letzte Satz unter der Tabelle („Die Liste ist im Lauf des Implementers auf den Bau ausgeweitet; sie ist die Plan-Tabelle, die Zuordnung … stehen in der Closure-Notiz“) ist nicht lesbar.
* verifizierbar: ja (Abgleich der `@spec`-Tags und Dateien im Diff mit Kopf und Tabelle)
* klasse: Plan nennt die berührten Stellen nicht (zweiter Lauf in Folge)

### V-2 MEDIUM — DoD verlangt einen Schlüssel für `MAX_TDS_PACKET_BYTES`, der Slice führt keinen

* quelle: Slice §1 („jede Konstante … hat einen Konfigurationsschlüssel mit Validierung“), §2 Punkt 3 (die Liste nennt `MAX_TDS_PACKET_BYTES`; „fehlt … der Schlüssel, legt dieser Slice ihn an“) gegen §3 Zeile 3 („`MAX_TDS_PACKET_BYTES` bleibt Protokollkonstante“); `SPEC-006`; „Spec ist Zielbild“
* pfad: `spec/spezifikation.md` · „Protokollkonstante, kein Konfigurationsschlüssel (Festlegung: das Längenfeld hat 16 Bit …“
* befund: Der Slice widerspricht sich selbst (Ziel und DoD gegen Plan §3). Die Umsetzung folgt §3 und trägt die Festlegung in `SPEC-006`, im Nutzer-Doku-Satz und in der Tabelle oben; die Begründung („ein einstellbarer Wert unter 65535 verlangte, die Aushandlung zu begrenzen, und wäre neues Verhalten“) ist eine Festlegung, nicht gemessen, und als solche gekennzeichnet. Ob die Abweichung vom Wortlaut der DoD gewollt ist, steht beim Planner und beim Nutzer, nicht beim Implementer. Entscheidungsvorlage: (A) Abweichung bestätigen, DoD-Satz in der Closure-Notiz als „kein Schlüssel, Grund `SPEC-006`“ führen; (B) Schlüssel mit Bereich 512 bis 65535 bauen, was die Aushandlung der Paketgröße (`LoginResponses`) begrenzt — neues Verhalten, eigener Slice. Empfehlung A, weil [TDS-FA-09](../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose) die Konstante nicht nennt und der Schlüssel ohne Wirkung läse (§6 des Slice, [ADR-0020](../plan/adr/0020-logformat-und-observability-schnittstelle.md)-Gedanke).
* verifizierbar: ja (Grep `MAX_TDS_PACKET_BYTES` in `src/main`: Konstante, keine Konfiguration)
* klasse: DoD-Wortlaut gegen Plan-Tabelle widersprüchlich

### V-3 LOW — `stop` aus der Senke blockiert dauerhaft (neue Kante der Sperre)

* quelle: [ADR-0020](../plan/adr/0020-logformat-und-observability-schnittstelle.md) Frage 7; `AGENTS.md` §3.8; Reviewer-Skill „Zusage ohne ihre Bedingung im selben Satz“
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/runtime/TdsServer.kt` · „stopLock.withLock {“ (die Abgabe von `SERVER_STOPPING` und `SERVER_STOPPED` liegt innerhalb)
* befund: Gemessen (Abschnitt „Reentranz“): ein `stop()` aus der Senke bei `SERVER_STOPPING` oder `SERVER_STOPPED` kehrt nicht zurück. KDoc von `stop` („darf nebenläufig gerufen werden“) und der Vertrag der Senke nennen den Aufruf aus der Senke nicht. Kein Verstoß gegen den Vertrag der Senke (sie darf nicht blockieren), aber eine Falle für „beim Ende des Servers aufräumen“.
* verifizierbar: ja (Kopie mit dem Test aus dem Abschnitt, nicht im Repo)
* klasse: Zusage ohne ihre Bedingung im selben Satz

### V-4 LOW — Neue Importkante `transport.framing` nach `config` (F-9)

* quelle: Komponententabelle der Sicht (Zeile `ARC-003`: Transport darf Domain, Security-Verträge, Observability- und Protocol-Schnittstellen importieren); Reviewer-Skill Schichten
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/transport/framing/FramingLimits.kt` · „import io.github.pt9912.tdswire.config.ConfigDefaults“
* befund: Kein anderer Import von `config` im Package `transport`; `ConfigDefaults` ist keine Security-Verträge-Zeile. `make detekt` lässt die Kante zu (`ForbiddenImport/transport` sperrt `port`, `simulation`, `runtime`, `observability`), `protocol.dispatch.PreparedHandles` importiert `ConfigDefaults` bereits (Vorbild). Die Sicht und der Sensor sind uneins; wo die Antwort liegt, entscheidet der Architect (Kante in die Zeile aufnehmen oder die Konstante in `domain` führen).
* verifizierbar: ja (Grep `tdswire.config` in `transport/`)
* klasse: Schichtkante außerhalb der Tabelle der Sicht

### V-5 LOW — Tag `@spec SPEC-051` am Schlüsseltest passt nicht zum Inhalt von `SPEC-051`

* quelle: Konvention nach `slice-qa-spec-abdeckung` (der Tag nennt die Zeile, die der Test belegt); `AGENTS.md` §3.8
* pfad: `src/test/kotlin/io/github/pt9912/tdswire/runtime/ZustandsereignisseTest.kt` · „fun `Die Schluessel der wirksamen Konfiguration sind die Namen der Konfiguration`“ mit „@spec SPEC-051“
* befund: `SPEC-051` ist der Authentifizierungsadapter; der Test prüft die Schlüsselnamen der Abbildung. Die Matrix zählt damit einen Test für `SPEC-051`, der ihn nicht belegt. Der Tag `SPEC-048` an den Redaktionstests hat Vorbild im Bestand (Redaktion), der Tag `SPEC-051` nicht.
* verifizierbar: ja (Lesen)
* klasse: Tag und belegte Zeile passen nicht

### V-6 LOW — [ADR-0040](../plan/adr/0040-wirksame-konfiguration-im-ereignis-aus-typisiertem-wertobjekt.md) Folge 2 („Schlüssel aus `ConfigKeys`“): die Schlüssel stehen zweimal

* quelle: [ADR-0040](../plan/adr/0040-wirksame-konfiguration-im-ereignis-aus-typisiertem-wertobjekt.md) Entscheidung, Folge 2; `ARC-001`
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/domain/telemetry/EffectiveSettings.kt` · „"LOGIN_TIMEOUT" to loginTimeoutSeconds.toString()“
* befund: Die Schlüssel sind Literale (`domain` darf `config` nicht kennen); ein Test hält die Gleichheit der heutigen Menge, die KDoc nennt es. Die Abweichung vom Wortlaut der ADR steht nirgends in der Planung; sie wird in die Closure-Notiz gehören. Eine neue Einstellung braucht drei Stellen (Feld, Literal, `ConfigKeys`-Konstante) und den Test, der nur die Reihenfolge der bestehenden elf hält: ein zwölfter Schlüssel ohne Eintrag im Test bleibt dort unbemerkt, bis jemand die Liste erweitert (siehe Reihenfolge, Zweig der Metriken).
* verifizierbar: ja
* klasse: Abweichung vom Wortlaut einer ADR nicht geführt

### V-7 LOW — Ein roter Test in der kombinierten Schleife, nicht zugeordnet

* quelle: Auftrag („unter Last kann ein Wanduhr-Test rot sein — wiederhole und ordne ein“); Beobachtung `BEO-TDS/test-stage-cache-verdeckt-wiederholung`
* pfad: Schleife der Klassen `ZustandsereignisseTest`, `TelemetryRedactionTest`, `ListenerAbortTest`, `AuthTimingTest` in einer JVM (`:test`, `:cleanTest` je Lauf)
* befund: Lauf 5 rot, Läufe 1 bis 4 grün (26 Tests, 1 rot, Last 25 bis 33); wegen `-q` ist der Name verloren. Einzelschleifen: 30, 20, 20, 20 Läufe grün. Die Wiederholung mit sichtbaren Testnamen blieb in 40 von 40 Läufen grün; der rote Lauf ist weder reproduziert noch einem Test zugeordnet (1 rot in 5 Läufen, danach 0 rot in 40 im selben Aufbau). Bekannt lastabhängig sind außerhalb des Diffs `ListenerAbortTest` und `AuthTimingTest`; dass ein Test des Diffs (`ZustandsereignisseTest`, `TelemetryRedactionTest`) beteiligt war, ist nicht ausgeschlossen. Dazu das Fenster in `TestserverLogProcessTest` (Anmeldeantwort geht vor dem Ereignis `login_accepted` hinaus, `SIGTERM` folgt ohne Warten auf die Zeile; Vorbild vor dem Slice, aber die Zahl der Ereignisse im Test ist mit diesem Slice gewachsen).
* verifizierbar: ja (Schleife mit sichtbaren Testnamen)
* klasse: Gate-Test unter Last nicht zugeordnet

### V-8 INFO — Reihenfolge bei festem Port bleibt Grenze (F-10)

`CONFIG_EFFECTIVE` und `SERVER_STARTED` kommen nach dem Binden; ein früher Client kann `CONNECTION_OPENED` davor auslösen. KDoc und Spezifikation nennen es „nicht gemessen“. Kein Test, keine Messung; zulässig nach `AGENTS.md` §3.8, solange der Satz nicht breiter wird.

### V-9 INFO — `TDS-FA-09.a` Nr. 3 nennt die Zustandsereignisse nicht

Die Zeile sagt „Sitzungs- und Fehlerereignisse“; Abschnitt 4 und [ADR-0040](../plan/adr/0040-wirksame-konfiguration-im-ereignis-aus-typisiertem-wertobjekt.md) (`Schärft`) tragen die Zustandsereignisse. Die Rangfolge (Spezifikation über ADR) wäre mit einem Halbsatz in Nr. 3 gewahrt; kein Widerspruch.

### V-10 INFO — KDoc von `SERVER_STOPPING`: „genau ein Ereignis je Server“

Gilt nur, wenn `stop` gerufen wird (kein Ereignis für einen nie beendeten Server). Gleiche Klasse wie F-8, geringer.

### V-11 INFO — `config_effective` schreibt Schalter als Zeichenkette

`auth_plain_testmode` steht als `"true"`, Zahlen als Zahl: Festlegung der Zeile des Adapters („ein Wert aus Ziffern steht als Zahl, jeder andere als Zeichenkette“), im KDoc und im Test benannt; ein Leser mit JSON-Typen sieht keinen Boolean.

## Mutationen und Gegenproben des Reviews, soweit der Behebung zugeordnet

Siehe die Tabellen oben. Nicht erneut gefahren (unverändert seit dem Review und durch die Tests des Reviews belegt): Abbildung der Marken `REFUSED_AT_SHUTDOWN` und die Mutation von `SERVER_STOPPING` hinter `listener.shutdown()`; beide Tests (`Jedes Ende einer Verbindung hat einen Grund gleichen Namens`, `Beenden meldet Beginn, …`) sind im Lauf `make clean-build` grün.

## Reihenfolge und Merge (Übergabepunkte)

Die Zweige ändern dieselben Dateien. `git merge-tree` (nur gelesen) mit diesem HEAD meldet für `worktree-agent-a905952da1a804fdb` (Metriken und Tracing, `slice-betrieb-metriken-tracing`) 26 Inhaltskonflikte (12 in Planung, Spezifikation, Nutzer-Doku und `spec-abdeckung.md`, 14 im Code und in Tests; der Auftrag nennt 24, hier ist die Zahl der Meldungen `KONFLIKT` gezählt) (Planung, Spezifikation, Nutzer-Doku, `spec-abdeckung.md` und im Code: `TelemetryEvent.kt`, `TdsServer.kt`, `ServerConfig.kt`, `ConfigValidation.kt`, `ConfigLoader.kt`, `EnvKeys.kt`, `TelemetryJson.kt`, `TelemetryEmbeddingTest.kt`, `TelemetryFelderEmbeddingTest.kt`, `ConfigValidationTest.kt`, `ConfigLoaderTest.kt`, `EnvironmentConfig.kt`, `TelemetryRedactionTest.kt`, `TestserverLogProcessTest.kt`); `Bootstrap.kt` und `ConnectionWiring.kt` mergen ohne Konflikt, tragen aber Semantik. Mit `worktree-agent-a9764e63162fbe637` (Nutzer-Doku) konfligiert nur `docs/user/einbettung-und-betrieb.md`.

**Empfohlene Reihenfolge:** dieser Zweig zuerst (er enthält [ADR-0040](../plan/adr/0040-wirksame-konfiguration-im-ereignis-aus-typisiertem-wertobjekt.md), die Sperre und die Typen, und liegt auf dem neueren `main`), danach die Metriken, zuletzt die Nutzer-Doku. Nach jedem Merge `make gates FORCE=1`, `make clean-build` und `make spec-abdeckung` (die Matrix wird erzeugt, nie von Hand gemergt), `make e2e-selbsttest` ist Teil von `make gates`.

### Übergabepunkte für den Merge der Metriken (nach diesem Zweig)

1. **`RequestSettings`: zwei Zweige hängen je einen `Int` als fünften Parameter an** (`maxMessageBytes` hier, `metricsIntervalSeconds` dort). Beide Parameter haben den Typ `Int`: ein positionaler Aufruf `RequestSettings(a, b, c, d, x)` übersetzt in beiden Reihenfolgen. Festzulegen: `maxMessageBytes` bleibt der fünfte, `metricsIntervalSeconds` wird der sechste; alle positionalen Aufrufe auf benannte Argumente umstellen (`ConfigLoader.kt` in `testserver`: dort steht je Zweig ein positionaler Aufruf `RequestSettings(queryTimeout, cancelTimeout, grace, handles, …)`). Die Fehlerlage ist sonst still: ein vertauschter Wert fällt nur durch die Bereichsprüfung auf (60 ist unter 65535; 16777216 über 86400), also nur bei Standardwerten.
2. **`EffectiveSettings` muss `METRICS_INTERVAL_SECONDS` aufnehmen:** Feld `metricsIntervalSeconds: Int`, Literal `"METRICS_INTERVAL_SECONDS"` in `asMap()` (V-6: kein Import von `ConfigKeys`), Zuordnung in `ServerConfig.effectiveSettings()`; im Test `Die Schluessel der wirksamen Konfiguration …` die Liste um den zwölften Schlüssel erweitern, in `Die Einstellungen enthalten weder …` `assertEquals(11, settings.size, …)` auf 12, in `TelemetryEventTest` die 11 positionalen Zahlen des Konstruktors von `EffectiveSettings` (alle `Int`) um die zwölfte erweitern oder auf benannte Argumente umstellen. `TelemetryJson` schreibt `config_effective` je Schlüssel generisch (`key.lowercase()`), das Feld `metrics_interval_seconds` entsteht ohne Änderung; `TelemetryJsonTest` prüft einzelne Felder, nicht die Menge.
3. **`TdsServer`:** Der Zweig der Metriken ändert den Konstruktor (`snapshots: Job?`) und ruft `snapshots?.cancelAndJoin()` vor `listener.shutdown()`. In der Zusammenführung gehört `cancelAndJoin` **in** den `withLock`-Block, nach der Abgabe von `SERVER_STOPPING` und vor `listener.shutdown()`; `Bootstrap.running` baut `TdsServer(listener, telemetry, snapshots())`. Der Satz der Metriken („nach der Rückkehr entsteht kein weiterer Schnappschuss“) und der Satz dieses Zweigs („nebenläufig gerufen, der zweite wartet“) gehören beide in die KDoc. Der Test `Zwei nebenlaeufige stop …` und der des abgebrochenen `stop` bleiben die Sensoren; ein abgebrochener `stop` nach `cancelAndJoin` darf das Ereignis zum Ende weiter nur mit dem nächsten Aufruf abgeben.
4. **Exakte Ereignisfolgen brechen:** Mit der Senke entstehen `SPAN_ENDED` (Ebene `DEBUG`, je Verbindung, Anfrage und Backend-Aufruf, `tds.connection` endet im Handler, also voraussichtlich **vor** dem `CONNECTION_CLOSED` des Listeners; nicht gefahren) und `METRICS`. Betroffen sind alle Aussagen über die vollständige Folge: `ZustandsereignisseTest` (Folge des Laufs und `atReturn` der Rückkehr von `stop`), `TelemetryEmbeddingTest` (Liste der Arten), `TestserverLogProcessTest` (die sortierte Liste der Ebene `DEBUG`), `TelemetryJsonTest`, `LogStartTest` (Menge `states`). Festzulegen: filtern auf die Arten der Zustandsereignisse oder die Liste um `SPAN_ENDED` erweitern; die Zusage „Beginn, Ende der Verbindung, Ende“ bleibt eine Zusage über die Teilfolge der Zustandsarten, nicht über die ganze Folge.
5. **`EventKind` und `TelemetryEvent`:** beide Zweige hängen Konstanten hinter `REQUEST_FAILED` und Mitglieder ans Ende der Klasse (textueller Konflikt, kein semantischer). Der `when` in `TelemetryJson` und die Liste `covered` müssen danach alle zehn neuen Arten und alle neuen Felder (`settings`; `span`, `outcome`, `requestId`, `durationMs`, `phase`, `requestKind`, `metrics`) kennen; der Redaktionstest erlaubt den Typ `Map` namentlich für `settings` und muss für `metrics` (`Map<String, Long>`) ergänzt werden, ohne den Reflexionstest von `EffectiveSettings` zu lockern.
6. **`ConnectionWiring`:** der Zweig der Metriken zerlegt `handle` in `connect` und `entry`; die zwei Zeilen dieses Zweigs (`sessionLimits`, vierter Parameter von `TransportHandshakeChannel`) mergen ohne Konflikt. Sensor: `MessageLimitWiringTest` muss nach dem Merge grün sein (die Grenze erreicht den Kanal).
7. **Spezifikation:** beide Zweige schreiben die Aufzählungen in Abschnitt 4 (Ereignismodell, Ebenen: `METRICS` und `SPAN_ENDED` kommen zu den Listen von `INFO` und `DEBUG`) und das Drift-Log; `spec-abdeckung.md` neu erzeugen. `harness/README.md`: die Zelle `make smoke` steht bei 200 Zeichen, nichts anhängen.
8. **Planung:** `roadmap.md`, `welle-betrieb-diagnose.md` und die Slice-Dateien sind Sache des Planners (je Zweig Verweise auf den Ort in `in-progress/`).

### Übergabepunkte für den Merge der Nutzer-Doku (nach den beiden anderen)

Die neue Fassung teilt die Nutzer-Doku auf: `einbettung-und-betrieb.md` (Aufgaben), `referenz-konfiguration.md`, `referenz-ereignisse.md`, `testserver-manuell.md`, `README.md`. Die Stellen dieses Zweigs sind dorthin zu tragen:

1. **Tabellenzeile `MAX_MESSAGE_BYTES`** (`requests.maxMessageBytes` | `MAX_MESSAGE_BYTES` | 16 MiB | 65535 bis 268435456 Byte, Summe der Paketlängen einer Nachricht nach dem Login | `SPEC-007`) in die Tabelle „Konfigurationsfelder“ von `referenz-konfiguration.md`.
2. **Satz „Nicht einstellbar“:** `referenz-konfiguration.md` Zeile 26 sagt heute, `MAX_TDS_PACKET_BYTES` und `MAX_MESSAGE_BYTES` seien Konstanten ohne Feld — nach diesem Zweig falsch; der Satz dieses Zweigs lautet: `MAX_TDS_PACKET_BYTES` (65535, `SPEC-006`) ist die Protokollgrenze des 16-Bit-Längenfelds und keine Einstellung; `MAX_MESSAGE_BYTES` ist einstellbar.
3. **`referenz-ereignisse.md`:** in die Tabelle der Arten die vier neuen (`CONFIG_EFFECTIVE`: `settings`, `SERVER_STOPPING`, `CONNECTION_OPENED`, `CONNECTION_CLOSED`: `reason` mit den sieben Marken, ohne Sitzung, Profil `unknown`), bei `SERVER_STARTED` die Felder `address` und `port`, bei `SERVER_STOPPED` „höchstens ein Ereignis, nach dem Ende jeder Verbindung“ statt „genau ein Ereignis je Server“, in die Feldliste `settings` (Schlüssel und Wert, nie Pfad). Der Absatz „Felder“ (Redaktion: `settings` nur Zahlen, Namen, Schalter, für den Schlüsselspeicher `SET` oder `UNSET`).
4. **Beispielzeilen** („echte Zeilen aus einem Handlauf, 2026-11-07“ in `referenz-ereignisse.md` und `testserver-manuell.md`): `server_started` trägt jetzt `address`, davor steht `config_effective` (ein Feld je Einstellung, Kleinbuchstaben); die Zeilen sind nach dem Merge nicht mehr „echt“ und sind neu aufzunehmen (`make testserver-up`, `make testserver-logs`) oder mit dem Datum des Stands zu kennzeichnen; die Zeile der Diagnosetabelle „nur `tdswire: …`-Zeilen und `server_started`“ nennt dann auch `config_effective`.
5. **Startdiagnose:** der Absatz dieses Zweigs (Ereignis `CONFIG_EFFECTIVE` bei Erfolg, `BIND_FAILED` mit Adresse und Port bei belegtem Port, `StartOutcome.Aborted.reason`) entspricht in der neuen Fassung der Fehlertabelle von Aufgabe 1 (Zeile `Aborted.reason` ist `Port belegt: <Adresse>:<Port>`) und Aufgabe 5; dort nachtragen, dass mit Senke `CONFIG_EFFECTIVE` und `SERVER_STARTED` bei Erfolg erscheinen.
6. **Liste „Nicht gebaut“:** der Satz „Nicht gebaut: …, Ereignisse zu Verbindung angenommen/beendet und geordnetem Beenden“ ist nach diesem Zweig falsch; nur „Anfrage beendet“ (und je nach Reihenfolge Metriken, Spans) bleibt.
7. **Sensor:** `AnleitungTest` (Zweig der Nutzer-Doku) prüft die Ereignisse mit `containsAll` und baut `RequestSettings` mit benannten Argumenten; er bricht durch diesen Zweig nicht. `make docs-check` fängt die Widersprüche **nicht** (gleiche Lage wie F-3 des Reviews): der Nachzug ist von Hand zu prüfen, mit Grep auf `MAX_MESSAGE_BYTES`, `SERVER_STOPPED` und `Nicht gebaut` in `docs/user/`.

## Risiken aus §6 (Vorschlag der Ausgänge)

* Überschneidung mit `slice-betrieb-log-adapter-testserver`: entfallen; der Slice kürzt hier (`TestserverLogProcessTest` rechnet die Ereigniszahlen um, keine Prüfung entfernt).
* Überschneidung mit den Tests von `slice-bootstrap-konfiguration` und `slice-transport-listener-lebenszyklus`: entfallen; keine Prüfung dort gekürzt.
* `QUERY_TIMEOUT` ohne Träger der Semantik: entfallen; Schlüssel besteht und wirkt (`QueryTimeoutTest`).
* `MAX_CONNECTIONS`: entfallen; Schlüssel und Durchsetzung bestehen (`ListenerLimitTest`, Marke `REFUSED_AT_LIMIT` im Test des Slice).
* „Verständlich“ nicht messbar: eingetreten und beurteilt; die Diagnose nennt Adresse und Port beziehungsweise Teil ohne Pfad (Prozesstests), die Beurteilung trägt der Review.
* Portwahl im Container: entfallen; der Prozesstest belegt den Port selbst; 12 Wiederholungen und ein frischer Lauf der Stage grün.
* Rohbytes-Client statt Referenzclient: weiter offen, benannt (E2E-Deklaration sagt „kein Referenzclient“).

## Bedingungen vor der Closure

1. **Closure-Notiz (Planner):** Konfigurationstabelle (oben), Zuordnung *Test | Festlegung* je Aussage, Steering-Loop-Lerneintrag, Risiko-Ausgänge, Finding-Klassen des Reviews (Test erwartet die Reihenfolge eines Wettlaufs · Zusage ohne ihre Bedingung (2×, jetzt V-3) · Fortschreibung lässt die zweite Fundstelle stehen (2×) · Redaktion durch Bauart durch Konvention ersetzt · Aussage breiter als ihr Beleg · Plan nennt die berührten Stellen nicht · Voreinstellung doppelt gehalten · Reihenfolge im Fenster zwischen Bindung und Meldung) und dieses Berichts (Plan nennt die Stellen nicht, ein zweites Mal; DoD gegen Plan widersprüchlich; Schichtkante außerhalb der Sicht; Tag passt nicht zur Zeile).
2. **V-1** (Kopf und Plan-Tabelle), Docs-only.
3. **V-2** (Entscheid zu `MAX_TDS_PACKET_BYTES`, Planner mit dem Nutzer; Vorlage oben).
4. **Haken** setzt der Planner nach Deckung durch diesen Bericht (aktuell keiner gesetzt, richtig).
5. **V-7:** entweder die rote Messung einem Test zuordnen (Schleife mit sichtbaren Namen) oder als weiter offen mit Träger führen; ohne Zuordnung ist „Gate unter Last rot“ nicht ausgeschlossen.
6. Nicht Bedingung, empfohlen: V-3 (ein Satz in der KDoc von `stop`), V-4 und V-6 (Entscheid des Architect, wohin die Kante und die Schlüsselliste gehören), V-5 (Tag).

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 2 |
| LOW | 5 |
| INFO | 4 |

**Finding-Klassen dieses Laufs:** Plan nennt die berührten Stellen nicht · DoD-Wortlaut gegen Plan-Tabelle widersprüchlich · Zusage ohne ihre Bedingung im selben Satz · Schichtkante außerhalb der Tabelle der Sicht · Tag und belegte Zeile passen nicht · Abweichung vom Wortlaut einer ADR nicht geführt · Gate-Test unter Last nicht zugeordnet
