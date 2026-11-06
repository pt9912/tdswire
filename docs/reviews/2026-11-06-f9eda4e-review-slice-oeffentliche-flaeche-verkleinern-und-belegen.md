# Review-Bericht: slice-oeffentliche-flaeche-verkleinern-und-belegen (HEAD f9eda4ef)

* Rolle: Reviewer (Modul 10), frischer Kontext. Gegenstand: Diff `6b28cd69..HEAD` (Commits `7387fedd`, `502bafb7`, `dc7bd467`, `f9eda4ef`, Merge `5247c043`), Zweig `worktree-agent-ab7612a126af616f7`. Maßstab: Slice (Plan), [ADR-0021](../plan/adr/0021-oeffentlicher-integrations-einstieg.md), [ADR-0026](../plan/adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md), [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), `SPEC-059`, `SPEC-035`, `SPEC-043`, `SPEC-047`, [TDS-QA-05](../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [TDS-RB-04](../../spec/lastenheft.md#tds-rb-04--externes-backend), `AGENTS.md` §3.1 bis §3.8. Keine Prüfung gegen die DoD (Verifier).
* Selbst gefahren: `make flaeche` (Stage aus dem Cache, Selbsttest und Lauf grün: 61 Namen), `make test` (grün, Cache), `make gates` (Exit 0; `gate-zuordnung`: 16 Gates, 4 `immer`; Selbsttest 33 Fälle; die Gruppen `werkzeug`, `tests`, `stempel` wurden bei passendem Stempel übersprungen, `docs-check` 943 Dateien, 0 Befunde). Typzahl nachgerechnet (Befehl Z1 des Messprotokolls): Basis 83, HEAD 61. Mutationen selbst gefahren: siehe Abschnitt Mutationen.

## Gesamturteil

0 HIGH, 2 MEDIUM, 3 LOW, 3 INFO. Der Umbau ist in der Sache korrekt und trägt seine Aussagen, soweit er sie belegt; die zwei MEDIUM sind eine zweite Fundstelle in der Nutzer-Doku und eine Spezifikationszusage über Feldbelegung, die nur ein Teil der Tests hält.

## Mutationen (selbst gefahren)

| Mutation | Wirkung gemessen |
|---|---|
| `Principal` wieder `public` (Kopie des Baums, Skript `tools/flaeche-pruefung.sh`) | Exit 1: „oeffentliche Deklaration ohne Eintrag in der Liste: Principal“ |
| neue `public class ExtraEvent` in `TelemetryEvent.kt` | Exit 1, gleiche Meldung für `ExtraEvent` |
| `ServerStopped` mit `EventKind.SERVER_STARTED` (zwei Klassen, dieselbe Art), `make test` | rot: `TelemetryEmbeddingTest > eineSenkeEmpfaengtDieEreignisse…` (erwartet `…SERVER_STOPPED`, war `SERVER_STARTED`); der Task `integratorTest` brach den Lauf ab, der neue Unit-Test `Jede Art der Aufzaehlung gehoert zu genau einer Klasse` kam in diesem Lauf nicht mehr zur Ausführung und ist durch diese Mutation nicht belegt |
| `passwordOf` im Testserver liefert `PasswordChars(CharArray(0))`, `make test` | rot: `ServerSmokeTest > Ein Prozess ohne JVM-Option startet mit TLS` und `Tds8ProcessTest > Serverprozess nimmt TLS ab dem ersten Byte an` (2 von 900); `ConfigUsersFileTest` bleibt grün |

Arbeitsbaum nach den Mutationen zurückgesetzt (`git status` sauber).

## Findings

### F-1 MEDIUM — Nutzer-Doku nennt die entfallene Klasse `EncryptionWeakened`

* kategorie: MEDIUM
* quelle: Maintainability; Reviewer-Skill „Fortschreibung lässt die zweite Fundstelle stehen“; `AGENTS.md` §3.8
* pfad: `docs/user/einbettung-und-betrieb.md` · „mit einer Senke erscheint sie als Ereignis `EncryptionWeakened` (`SPEC-010`)“
* befund: Die Klasse ist nicht mehr öffentlich und die Art heißt `SECURITY_WEAKENED` mit `reason` `ENCRYPTION_TEST_UNENCRYPTED`; der Abschnitt „Log und Startdiagnose“ derselben Datei ist nachgezogen, diese Zeile nicht. Ein Einbettender sucht einen Namen, den es von außen nicht gibt. In derselben Datei nennt die Zeile mit „die Prüfung in `ConfigValidation.kt`“ eine Datei einer jetzt `internal` Klasse (siehe F-4).
* verifizierbar: ja (Grep über `docs/user`; `make docs-check` fängt es nicht, weil kein Link)
* klasse: Fortschreibung lässt die zweite Fundstelle stehen

### F-2 MEDIUM — Zusage der Felder je Art breiter als der Beleg

* kategorie: MEDIUM
* quelle: `AGENTS.md` §3.8; `SPEC-059` Abschnitt 4 (Ereignisarten mit Feldern „in Klammern“)
* pfad: `spec/spezifikation.md` · „Konstanten von `EventKind`, Felder in Klammern“
* befund: Abschnitt 4 legt für jede der 17 Arten fest, welche Felder gefüllt sind (z. B. `part` und `form` nur bei `TLS_START_REJECTED`, `tables` nur bei `SIMULATION_BACKEND_READY`, `failure` bei `TLS_HANDSHAKE_FAILED`). Gehalten wird das nur für Teile: `TelemetryEventTest` prüft Art, Klasse, Ebene und Kürzung, der Einbettungstest belegt `port`, `principal`, `reason` und `issueKind` über die öffentliche Fläche; `TelemetryRedactionTest` prüft Typ und erlaubte Namen je Klasse, nicht, dass ein Feld in der in der Spezifikation genannten Art gefüllt ist. Ein Feld, das von einer Art auf eine andere wandert (z. B. `cause` bei `CONNECTION_FAILED`), wird von keiner Prüfung der öffentlichen Fläche und vom Sensor nicht gesehen (Grenze „Mitglieder“ ist im Kopf des Sensors benannt). Die Spezifikation sagt das für Mitglieder nicht ausdrücklich bei Abschnitt 4 (nur im Satz „Belegt ist davon …“ der Zeile Zusage).
* verifizierbar: nein (kein Gate, das es bestätigt; per Mutation zu prüfen, hier nicht gefahren)
* klasse: Aussage breiter als ihr Beleg

### F-3 LOW — Test seam `ConfigLoader.passwordText` im Produktcode des Testservers

* kategorie: LOW
* quelle: Maintainability; `BEO-TDS/test-naht-im-produktivcode` (Muster, Beobachtung im Register)
* pfad: `testserver/kotlin/io/github/pt9912/tdswire/testserver/ConfigLoader.kt` · „internal fun passwordText“
* befund: Die Funktion ist `internal`, damit `ConfigUsersFileTest` den Text ohne `PasswordChars.reveal` lesen kann. Der Test prüft damit die Hilfsfunktion mit selbst gebauten Bytes, nicht den Weg `load` → `passwordOf` → `PasswordChars`. Die Belegkraft sinkt auf „die Hilfsfunktion entfernt das Zeilenende“; ein leeres `PasswordChars` aus dem Lader macht `ConfigUsersFileTest` nicht rot (selbst gefahren), rot werden `ServerSmokeTest` (TLS) und `Tds8ProcessTest`. Die Schwächung ist im Slice benannt („schwächere Aussage“) und durch die Prozesstests aufgefangen; die Aussage des Tests-KDoc („ein Zeilenende am Ende gehört nicht zum Kennwort“) bleibt wahr.
* verifizierbar: ja (Mutation oben)
* klasse: Test prüft die Hilfsfunktion statt des Weges

### F-4 LOW — Nutzer-Doku nennt Datei `ConfigValidation.kt` einer nicht mehr öffentlichen Klasse

* kategorie: LOW
* quelle: Maintainability; `SPEC-059` (Fläche)
* pfad: `docs/user/einbettung-und-betrieb.md` · „die Prüfung in `ConfigValidation.kt`“
* befund: Die Doku wendet sich an Einbettende, die die Klasse nicht mehr sehen; die Aussage „Prüfung im Start, Befunde in `Aborted.issues`“ steht in `SPEC-059`.
* verifizierbar: ja
* klasse: Fortschreibung lässt die zweite Fundstelle stehen

### F-5 LOW — Offene Slices tragen die Namen der entfallenen Klassen

* kategorie: LOW (Plan-Befund)
* quelle: Maintainability; Reviewer-Skill „zweite Fundstelle“ (hier nicht MEDIUM, weil die internen Klassen mit gleichem Namen weiterbestehen und die Slices eigene Verdrahtungstests auf ihnen planen)
* pfad: `docs/plan/planning/done/slice-betrieb-telemetrie-verdrahtung-belegen.md` · „Verdrahtung von `AcceptFailed` und `ConnectionFailed`“ und „`SPEC-059` ändert sich nicht“; `docs/plan/planning/open/slice-betrieb-metriken-tracing.md` · „ob sie auch als Feld in `RequestFailed`“; `docs/plan/planning/welle-betrieb-diagnose.md` · Zeile des Verdrahtungs-Slice
* befund: Die Namen gelten nur noch als `internal`-Klassen oder als `EventKind`-Konstanten; ein neues Feld (`request_id`) ist jetzt ein Mitglied der einen öffentlichen Klasse `TelemetryEvent` und ändert `SPEC-059` (Abschnitt 4 und die Zeile „Zusage“, die die Felder von `TelemetryEvent` nennt), was der Verdrahtungs-Slice mit „ändert sich nicht“ bereits für sich nicht berührt. Der Slice listet diese Slices nicht als nachzuziehen; die Schnitt-Tabelle (§3) nennt den Nachzug nicht.
* verifizierbar: ja (Grep)
* klasse: Fortschreibung lässt die zweite Fundstelle stehen

### F-6 LOW — Sensor: Klasse `immer` für Eingänge, die in Quelle und Spezifikation liegen

* kategorie: LOW
* quelle: [ADR-0024](../plan/adr/0024-lokale-stempel-zwei-klassen.md), [ADR-0030](../plan/adr/0030-dritter-stempel-fuer-die-selbsttests-der-stempel.md)
* pfad: `tools/mk/flaeche.mk` · „GATE_KLASSE_immer += flaeche“
* befund: Die Begründung („Eingang steht nicht vollständig in der Eingangsliste einer Klasse mit Stempel; kostet bei unverändertem Eingang nur den Layer-Cache“) trägt: Stage ohne Gradle, jeder Lauf ist ein `docker build` mit Layer-Cache (selbst gefahren: Stage `CACHED`, Lauf im Sekundenbereich). `make gate-zuordnung` bestätigt 16 Gates, 4 `immer`, Selbsttest 33 Fälle. Als LOW vermerkt: der Preis ist bei jeder Quelländerung ein Neubau der Stage mit Kopie dreier Quellbäume; ein Stempel der Klasse `tests` wäre mit den Eingängen `src/main`, `src/integrator`, `testserver` möglich gewesen, der Slice benennt diesen Gegengrund nicht. Eine Entscheidung, kein Fehler.
* verifizierbar: ja
* klasse: Gate-Klasse ohne Gegenprobe der Alternative

### F-7 INFO — `SECURITY_WEAKENED` mit `reason`: Abweichung von der bestätigten Vorlage

* kategorie: INFO
* quelle: Nutzerentscheid (Option B, „ohne Feld“ in der Vorlage); `SPEC-059`
* pfad: `docs/plan/planning/done/slice-oeffentliche-flaeche-verkleinern-und-belegen.md` · „Das ist eine Ergänzung gegenüber „ohne Feld“ in der Vorlage und steht zur Bestätigung aus.“
* befund: Die Begründung trägt (zwei aktive Abschwächungen wären ohne `reason` zwei gleiche Ereignisse; der Einbettungstest `TelemetryEmbeddingTest` belegt zwei Ereignisse mit den beiden Gründen). Die Spezifikation (Abschnitt 4, Änderungsprotokoll) führt `reason` bereits als Festlegung und kennzeichnet nur im Satz „Dass beide Abschwächungen eine Art sind … ist eine Setzung“; die ausstehende Nutzerbestätigung steht nur im Slice. Vor der Closure zu klären; keine Aktion des Reviewers.
* verifizierbar: nein
* klasse: Abweichung von der Vorlage ohne Bestätigung

### F-8 INFO — Kosten von Option B, Typsicherheit

* kategorie: INFO
* quelle: Slice §3 „Telemetrie, Option B“, `SPEC-059`
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/domain/telemetry/TelemetryEvent.kt` · „public open val reason: String? get() = null“
* befund: Die je Art typisierten Felder (`StartRefusal`, `LoginRejectionReason`, `TlsFirstRefusal`, `WeakeningReason`) sind Text; `reason` trägt vier Wertemengen; Zugriffe liefern `null` für Arten ohne das Feld; ein `when` über `kind` ist vollständig (Enum, ohne `else`), prüft aber nicht, welche Felder die Art füllt. Die Kosten sind im Slice und in Abschnitt 4 der Spezifikation benannt („Preis dieses Zuschnitts“), die Grenze des Sensors (Mitglieder) im Kopf des Sensors. Redaktion: `TelemetryRedactionTest` begrenzt auf der Basisklasse die Textfelder auf die Vereinigung der Namen und je Unterklasse auf die eigenen Zugriffe (Brücken und synthetische Methoden ausgenommen); keine Zugangsdaten in Ereignissen gefunden (`principal` nur bei `LOGIN_ACCEPTED`, kein Feld für Passwort, Prüfwert, Pfad, Ausnahmetext).
* verifizierbar: ja (`TelemetryRedactionTest`)
* klasse: Kosten einer Entscheidung benannt

### F-9 INFO — Sensor: Grenzen und Schwäche

* kategorie: INFO
* quelle: `AGENTS.md` §3.8
* pfad: `tools/flaeche-pruefung.sh` · „Namenssuche ist kein Aufrufbeleg“
* befund: Die Grenzen im Kopf sind vollständig und stimmen mit dem Messprotokoll überein: Typnamen statt Mitglieder ([ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md): `onInsertBatch`, `maxLength`, zweiter Konstruktor sind noch nicht gebaut und werden vom Sensor nicht gesehen), Namenstreffer in totem Aufruf oder Zeichenkette zählt als Beleg, `internal` im Bytecode öffentlich, Deklaration mit Umbruch nach `public` unsichtbar. Der Selbsttest hat 14 Fälle (a1 bis a5, b1, b2, c1, g0 bis g3, e1, e2), jeder Zweig hat eine Mutation (beide Richtungen selbst gefahren, siehe oben); die Eingangslisten des Dockerfile-Stage `flaeche` nennen die Quellen und beide Skripte. Die Grenze „Beleg in einem toten Aufruf“ wird im Einbettungstest nicht zusätzlich geprüft; die Namen aus `FlaecheEmbeddingTest` werden in wirkenden Aufrufen genutzt und im Test mit Werten geprüft.
* verifizierbar: ja
* klasse: Grenze benannt

## Antworten auf die gezielten Fragen

1. Nachgerechnet: 83 → 61 (Befehl Z1, beide Stände). `make flaeche`, Selbsttest, `make test`, `make gates` grün (Stempel/Cache siehe oben). Mutationen: siehe Tabelle; „zwei Klassen, dieselbe Art“ macht den Einbettungstest rot, der neue Unit-Test blieb in diesem Lauf unausgeführt; `Principal` public macht den Sensor rot.
2. Umbau: Basisklasse mit 13 nullbaren Zugriffen, Unterklassen `internal`, `kind` fest je Klasse (`TelemetryEventTest`: genau eine Klasse je Art), Redaktion gehalten. `SECURITY_WEAKENED` mit `reason`: begründet, Bestätigung offen (F-7). Typverlust: benannt (F-8).
3. Sensor: Gate-Klasse `immer` begründet tragfähig (F-6); Grenzen benannt (F-9).
4. `ConfigUsersFileTest`: Verlust an Belegkraft real, aber durch Prozesstests aufgefangen (F-3). Der Testserver nutzt nur öffentliche Namen (kompiliert gegen die Fläche; `ConfigValidation` und `reveal` sind für das Modul nicht mehr übersetzbar).
5. Siehe F-2.
6. Zweite Fundstellen: F-1, F-4, F-5. Spezifikation, `docs/user`, Matrix, `README.md`, Register: sonst keine Treffer außer Zeitdokumenten (Änderungsprotokoll, Roadmap-Zeile, Messprotokoll, Register-Beobachtung `oeffentliche-flaeche-der-telemetrie-ohne-entscheidung`, die den Ausgangszustand beschreibt). `slice-betrieb-*`: siehe F-5.
7. Tote Links: `make docs-check` 0 Befunde; README-Zellen der neuen Zeile 16, 193 und 141 Zeichen (≤ 200); Kommentare im Diff beschreiben den Ist-Zustand (kein Treffer auf „früher“, „wäre“).

## Geprüft, ohne Befund

* `src/main/kotlin/.../domain/telemetry/` — außer F-8: Konstruktoren, Kürzung (`bounded`), Ebene je Art, KDoc.
* `src/main/kotlin/.../runtime/` (`Bootstrap.kt`, `TdsServer.kt`) — Abbildung `SecurityWeakened`, keine Verhaltensänderung.
* `src/main/kotlin/.../config/`, `config/auth/`, `domain/Principal.kt` — nur Sichtbarkeit und KDoc.
* `src/integrator/` — Einbettungstests belegen die Namen über die öffentliche Fläche.
* `src/test/` — Anpassung an die neue Klasse, kein Verhaltensverlust gefunden außer F-3.
* `testserver/` — nur öffentliche Namen; F-3.
* `tools/flaeche-*.sh`, `tools/mk/flaeche.mk`, `Dockerfile` (Stage `flaeche`) — F-6, F-9; Host statt Docker: nein (Aufruf im Docker-Build; die Skripte liefen für die Mutationen auf einer Kopie im Scratchpad, nur lesend).
* `harness/README.md` — Zeile des Sensors mit Klasse und Bindung.
* `spec/spezifikation.md` — F-2, F-7; keine Erweiterung des Lastenhefts, kein Verstoß gegen §3.4.
* Suppression (`@Suppress`), Schichtregeln, Accepted-ADR-Änderung — keine im Diff.
