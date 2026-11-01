# Verifikation: slice-qa-anmeldedrosselung (Commit 7897412)

* Rolle: Verifier (Modul 11), frischer Kontext; Frage „Bauen wir es richtig?“ gegen Plan und DoD. Basis `main`, Branch `worktree-agent-a00ebde4018fb495a`, HEAD `7897412`; Review [f4ec33b](2026-10-31-f4ec33b-slice-qa-anmeldedrosselung.md) und Fix-Runde (`26cc536`, `500b883`, `7897412`) sind durch; nicht gemergt.
* Prüfgrundlage: Plan und DoD [`slice-qa-anmeldedrosselung`](../plan/planning/done/slice-qa-anmeldedrosselung.md), [ADR-0016](../plan/adr/0016-anmeldedrosselung.md) (Accepted, Option B), [ADR-0009](../plan/adr/0009-authentifizierungsmodus.md), [ADR-0011](../plan/adr/0011-pruefwertformat.md), [CO-003](../plan/carveouts/done/CO-003-anmeldedrosselung-fehlt.md), SPEC-008/012/023/051/053 und die Nutzerentscheidungen vom 2026-11-01 (SPEC-053 als Setzung ohne Lastenheft-Anker; fehlende Adresse bleibt ungedrosselt). Der Implementer-Bericht ist Eingabe, nicht Beleg.
* Herkunft: Der Verifier-Agent durfte die Datei nicht anlegen; der Inhalt ist sein Bericht, vom Aufrufer verdichtet abgelegt.
* Eigene Läufe (Docker-only): `make gates` im Worktree Exit 0 (baseline-verify 54 Dateien, d-check 537 Dateien 0 Befunde, detekt, build, kopplung-zahlen, skript-selbsttest; referenztest-dotnet übersprungen, Eingänge unverändert; die Stage `test` kam aus dem Cache). Dafür zwei frische Testläufe mit `docker build --no-cache-filter test --target test`: beide Exit 0, je 808 PASSED, 0 FAILED; alle 23 neuen Testfälle (6 Treiber, 14 `LoginThrottleTest`, 1 Timing, 2 Wiring) und die 6 Remote-Adress-Tests PASSED. Drei Mutationen in Kopien des HEAD; Worktree unberührt (`git status` und `git diff` leer).
* Grenze: kein Referenzclient und kein `make e2e`; der Rohbytes-Client gegen echte Sockets ist kein Referenzclient (SPEC-053 sagt das selbst).

## Gesamturteil

**Closure-fähig: ja, unter Bedingungen.** Keine DoD-Verletzung im Code, kein HIGH. Die Liefer-Punkte 1 und 2 sind im Code bestätigt, `make gates` ist grün, drei Mutationen werden von genau den zuständigen Tests gefangen. Die Bedingungen betreffen Closure-Handlungen und Wortlaut-Korrekturen im Plan: (1) Wortlaut von DoD-Punkt 1 und §4 korrigieren (F-1), (2) die Closure-Handlungen (Review-Punkt, Doku, CO-003-Auflösung, Closure-Notiz, Register, Risiko-Ausgänge), (3) empfohlen die KDoc des Zeittests ergänzen (F-3).

## DoD Punkt für Punkt

* **1, Entscheidung als ADR `Accepted`: bestätigt, Wortlaut stale.** [ADR-0016](../plan/adr/0016-anmeldedrosselung.md) trägt `Status: Accepted`; der Punkt ist `[x]`, sein Text sagt aber noch „liegt als `Proposed` vor“ (F-1).
* **2, Umsetzung im Anmeldepfad mit Tests, Wirkung gebremst und nicht dauerhaft blockiert, Messung der Wartezeit: bestätigt.** Einzelprüfungen unten; Echtzeit-Messung in `LoginThrottleTimingTest`.
* **3, `make gates` grün: bestätigt.**
* **4, Review mit Report: nicht Teil dieser Bestätigung** (Report `2026-10-31-f4ec33b-…` liegt vor; das Kästchen setzt die Closure). **5, Doku-Update und CO-003 aufgelöst: offen (Closure);** SPEC-053, SPEC-008, SPEC-012 sind nachgezogen, CO-003 ist noch Aktiv. **6 bis 9, Closure-Notiz, Register, Risiko-Ausgänge, Paarungen: offen (Closure);** §7 steht auf „Wird bei der Closure gefüllt.“

### Einzelprüfungen zu Punkt 2 (selbst gegen Code und Test)

* Staffel 5 frei, dann 250 ms, verdoppelt bis 5 s: bestätigt (`LoginThrottle.waitFor`, Test mit 12 Stufen; `LoginThrottleDriverTest`: sechste wartet 250 ms, die ersten fünf nicht).
* Fenster 60 s ab letztem Fehlversuch: bestätigt (`expire()`, `settle(failed)` schreibt `lastFailure`; Tests zu Fensterende, Verschiebung und Wiring nach Ablauf).
* Erfolg setzt Fehlversuche nicht zurück: bestätigt (`settle(false)` ändert `failures` nicht; eigener Test).
* Schlüssel Adresse ohne Port und Zone: bestätigt (`hostAddress.substringBefore('%')`; Test mit Zonenkennung laut Review).
* Warten VOR dem Adapteraufruf und ohne `AuthPool`-Platz: bestätigt (`HandshakeDriver.authenticate`: `enter`, `waitTurn`, dann `executor.start`; Tests: Adapteraufrufe zum Warte-Zeitpunkt = 5; Pool mit 1 Platz, Fremdaufruf läuft durch).
* Wartezeit und Antwortbytes für bekannte und unbekannte Kennung gleich: bestätigt (Wartezeit nur aus `failures + running` der Adresse; Test vergleicht je 8 Versuche; Mutation M3 gefangen).
* Passwort-Wipe bei Abbruch im Warten: bestätigt (`finally` mit `if (!waited) { request.password.wipe(); attempt.settle(false) }`; Test prüft Passwort während des Wartens lesbar, danach alle Zeichen null, Adapteraufrufe 0, Tabelle leer; Mutation M1 gefangen).
* Speicherobergrenze 10 000 mit Verdrängung: bestätigt (`admit()` entfernt `table.keys.first()`; vier Tests, darunter Eintrag mit nur laufenden Versuchen).
* Fehlversuch nur bei `AuthResult.Rejected`: bestätigt (Test zu Annahme, Adapterfehler, fehlender Adresse; Abbruch zählt nicht; Mutation M2 gefangen).
* Laufende Versuche (Mechanik 5): bestätigt (Wartezeit aus `failures + running`, `running` wird in `settle` immer abgezogen; Test mit 8 Threads).
* `remoteAddress` im Transport: bestätigt (`SuspendingChannel` Voreinstellung `null`; `SocketChannelAdapter` liest sie bei der Erzeugung und fängt `IOException`; Peeked, Switchable, Tls und `ManagedConnection` geben sie weiter; `ConnectionWiring` übergibt sie an `driver.run`; `RemoteAddressTest`, `TlsRemoteAddressTest`, `ListenerRemoteAddressTest`).
* Wiring gegen echte Sockets: bestätigt (`LoginThrottleWiringTest` auf `127.0.0.1`: fünf Fehlversuche ohne Wartezeit, der sechste wartet 250 ms, ein siebter mit richtigem Passwort wartet 500 ms und wird angenommen, nach 60 s ist die Adresse frei; gesteuert sind nur Uhr und Pause).
* Fail-open ohne Adresse (Nutzerentscheidung): bestätigt (`enter(null)` gibt einen `Attempt(null, …)` ohne Wartezeit und Zähler; Plan §3 und SPEC-053 benennen die Entscheidung).
* SPEC-053 als gekennzeichnete Setzung (Nutzerentscheidung): bestätigt („Setzung ohne Lastenheft-Anker, vom Nutzer bestätigt“, „alle Zahlen Setzung, nicht gemessen“, „rund 12 Versuche je Minute nur für eine Verbindung nach der anderen“, „ein Schwall … nicht gemessen“, „heute schreibt sie nichts ins Log“); Lastenheft nicht angefasst; SPEC-008 und SPEC-012 verweisen auf SPEC-053.

## Mutationen und Zeit-Tests

* M1 (`request.password.wipe()` im Abbruchzweig entfernt): Exit 1, `LoginThrottleDriverTest > Ein abgebrochenes Warten ruft den Adapter nicht und ueberschreibt das Passwort` FAILED, sonst nichts.
* M2 (`failed = true` statt `is AuthResult.Rejected`): Exit 1, `LoginThrottleDriverTest > Annahme, Adapterfehler und fehlende Adresse zaehlen nicht` FAILED.
* M3 (Wartezeit entfällt für die Kennung `alice`): Exit 1, `… fuer bekannte und unbekannte Kennung gleich` FAILED, außerdem beide `LoginThrottleWiringTest`-Fälle.
* `LoginThrottleTimingTest` (Echtzeit, Sollwerte 200, 400 und 400 ms, Fenster 1 s) lief in beiden frischen Testläufen und im Zwischenlauf der Mutationen ohne Auffälligkeit bei Systemlast `load average` um 16 (20 Kerne); der Fall dauert etwa 2 bis 2,1 s. Die gemessenen Einzelwerte druckt der Test nicht, eine numerische Streuung ist daher nicht angebbar, nur „kein Rot in 2 Läufen plus 3 Mutationsläufen“. Restrisiko: ein JVM-Stillstand über 200 ms bei einem freien Versuch; nicht beobachtet, nicht ausgeschlossen.

## Findings

Kein HIGH, kein MEDIUM.

* **F-1 (LOW, Planner), Plan §2 Punkt 1 und §4:** Punkt 1 ist `[x]`, sagt aber „liegt als `Proposed` vor … die Wahl bestätigt der Nutzer“; §4 Rückführung nennt noch „[ADR-0016](../plan/adr/0016-anmeldedrosselung.md) bleibt `Proposed`“. Beides beschreibt einen Zustand, den es nicht mehr gibt (3.7, Zustandsfelder).
* **F-2 (LOW, 3.7), `LoginThrottleTimingTest` KDoc „ein Stillstand der JVM von 200 ms wäre nötig, um sie zu reißen“:** Konjunktiv; Indikativ wäre „Die Obergrenze für freie Versuche reißt nur ein Stillstand der JVM über 200 ms“.
* **F-3 (LOW, 3.8), `LoginThrottleTimingTest` KDoc „Messzeile“:** Sie trägt drei Läufe; hinzu kommen zwei frische Läufe plus drei Mutationsläufe bei `load average` um 16, alle ohne Rot. Die Aussage bleibt wahr; empfohlen vor Closure die Zahl der Läufe und die Last nennen oder die gemessenen Werte in die Meldung schreiben.
* **F-4 (INFO):** `make spec-abdeckung` hat in `docs/plan/spec-abdeckung.md` auch ältere Drift auf `main` bereinigt (SPEC-025/032/050 tragen jetzt die Titel der Spec und „OHNE NACHWEIS“); Stand von „32 von 52, 7 ungemessen, 13 ohne Nachweis“ auf „33 von 53, 4 ungemessen, 16 ohne Nachweis“. Merge-Konfliktpotenzial mit anderen Branches, die die Matrix regenerieren.
* **F-5 (INFO), Grenzen, nicht Mängel:** IPv6-Quelle mit vielen Adressen (etwa ein /64-Präfix) umgeht die Drosselung und kann bei 10 000 Adressen fremde Einträge verdrängen ([ADR-0016](../plan/adr/0016-anmeldedrosselung.md) §Offen, SPEC-053: „verteilte Suche bleibt ungebremst“); hinter einem Proxy gilt die Proxy-Adresse für alle Clients; die Drosselung schreibt nichts ins Log; bei gleichzeitigen Verbindungen begrenzt der `AuthPool` die Rate, nicht die Staffel; das Passwort liegt während der Wartezeit bis zu 5 s im Speicher.
* **F-6 (INFO), `AuthPool.throttle`:** Ein `HandshakeDriver` ohne ausdrücklichen Pool erzeugt einen eigenen `AuthPool` mit eigener Tabelle; die KDoc benennt das, im Prozess gibt es einen Pool. Ein gesonderter Test, dass `Bootstrap` den einen Pool an alle Verbindungen gibt, wurde nicht gesucht.

## Offene Closure-Aufgaben (nur gemeldet)

* Risiken aus §6, je ein Ausgang: Fremdsperre je Benutzername: entfallen ([ADR-0016](../plan/adr/0016-anmeldedrosselung.md) Option B, der Schlüssel enthält keinen Namen, es gibt keine Sperre, nur eine Verzögerung mit Deckel 5 s). Clients hinter gemeinsamer Adresse: weiter offen, nicht gemessen, mit Träger (Beobachtung oder Folge-Slice); Re-Evaluierungs-Trigger steht in der ADR.
* [CO-003](../plan/carveouts/done/CO-003-anmeldedrosselung-fehlt.md) auflösen (Trigger (a) erfüllt, sobald der Slice in `done/` liegt): Tabelle „Geltungs-Konfiguration“ nachführen (sie behauptet, die KDoc von `AuthExecutor` „nennt `CO-003`“, was der Branch entfernt hat; Wiederkehr von `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`), Verifikations-Kästchen abhaken, reiner `git mv` nach `docs/plan/carveouts/done/` und Zeile in `docs/plan/carveouts/README.md` nach „Aufgelöste Carveouts“; der Slice nach `done/` erst nach Inhalt (Closure-Notiz), dann reiner `git mv` (3.3).
* Register: `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` trägt die Drosselung als offenen Punkt; eine weitere Evidence-Datei (`evidence/slice-qa-anmeldedrosselung.md`) oder der Vermerk „keine Beobachtung“ in §7 ist fällig.
* Steering-Loop-Lerneintrag und Paarungen in §7; mögliche Lerneinträge: Konjunktiv-Funde im Review, „Kommentar in Fundstelle bleibt stehen“ (Wiederkehr), die Spec-Zeile mit gemessener Bedingung („nur sequentiell“).
* Merge-Hinweis: `spec/spezifikation.md` (SPEC-008, SPEC-012, SPEC-053, Historie), Roadmap-Liste „In Arbeit“, `docs/plan/spec-abdeckung.md`, `docs/plan/carveouts/README.md`, CO-003 und Verweis-Nachzüge können mit parallelen Branches kollidieren.

## Geprüft, ohne Befund

Keine Gate-Lockerung (kein Diff an `config/`, `Dockerfile`, `build.gradle.kts`, `tools/`); kein `@Suppress`, detekt grün; keine Accepted-ADR geändert; Lastenheft unverändert; `FailingCloseChannel.getRemoteAddress` gibt `null` statt Ausnahme (Test-Fake, vom Konstruktor-Test am geschlossenen Kanal gedeckt); `settle` idempotent (`AtomicBoolean`), alle Zustandsänderungen unter einem Lock, Entfernung eines Eintrags nur bei `running == 0 && failures == 0`, Tabelle wächst nie über 10 000.
