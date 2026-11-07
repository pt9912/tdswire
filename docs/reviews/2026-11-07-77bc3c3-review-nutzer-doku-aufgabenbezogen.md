# Review-Bericht: Nutzer-Doku aufgabenbezogen und auf den Ist-Zustand (HEAD 77bc3c38)

* Rolle: Reviewer, frischer Kontext. Gegenstand: Diff `32fcc71b..77bc3c38` (Commits `0986aa23`, `84d543c7`, `9117d001`, `77bc3c38`), Zweig `worktree-agent-a9764e63162fbe637`. Maßstab: `AGENTS.md` §5 Regel 5 (Ist-Zustand) und §3.8, [TDS-FA-08](../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-FA-09](../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [ADR-0021](../plan/adr/0021-oeffentlicher-integrations-einstieg.md), [ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md), [ADR-0013](../plan/adr/0013-backend-grenzen.md), Reviewer-Skill. Es gibt keinen Slice-Plan zu diesem Diff; geprüft wurde gegen den Auftrag des Nutzers (Ist-Zustand, Aufgabenbezug).
* Selbst gefahren: `make docs-check` (Exit 0, 1111 Dateien, 0 Befunde), `make gates FORCE=1` (Exit 0, darin `flaeche`, `gate-zuordnung` 16 Gates / Selbsttest 33 Fälle, `stempel-eingaenge` ok, detekt), Frischbeleg der Stage `test` (`docker build --no-cache-filter test --target test`, Exit 0; `AnleitungTest`: 4 von 4 PASSED), `make testserver-up/-status/-logs/-down`, Rohbytes-Client (richtiges und falsches Kennwort, eigene Benutzerliste), d-migrate-Folge (`schema reverse`, `compare`, `migrate --execute`, `compare`, `data export`) mit dem Digest `sha256:308f2775…`, ungültige Beschreibung und Datei über 1 MiB, `TESTSERVER_LOG_LEVEL=debug`, `REQUIRED` ohne Speicher. Merge gegen `f0886534`: `git merge-tree` meldet keinen Konflikt; die 13 geänderten Dateien auf `main` liegen alle unter `docs/plan/planning/` und berühren keine Datei dieses Diffs.

## Gesamturteil

0 HIGH, 5 MEDIUM, 5 LOW, 4 INFO. Die Aussagen der fünf Dateien stimmen in der Sache mit Code, Spezifikation und Messprotokollen überein (Konfigurationstabelle, Fehlercodetabelle, Ereignisarten gegen `EventKind`, Testnamen, Messprotokoll-Zitate geprüft); das verbotene Vokabular kommt nicht vor. Die schwerwiegenden Mängel liegen im Aufgabenbezug: der erste Schritt der Einbettungs-Anleitung liefert auf dem Host kein Jar, mehrere Aufgaben sind Eigenschaftenkataloge ohne lauffähigen Aufruf, und eine Fehlerzeile gibt einen Satz der Spezifikation wieder, den der Server nicht erfüllt.

## Verbotene Muster (Grep über `docs/user/`)

Treffer null für `nicht gebaut`, `noch nicht`, `bisher`, `Zielzustand`, `seit Slice`, `früher`, `Slice`, `Welle`, `Proposed`, `BEO-`, `Register`, `Zielbild`, `Nutzerentscheid`, `geplant`, `künftig`, `später`. `vorläufig` steht nur im Anker-Fragment der Überschrift von `SPEC-036` (Linkziel, nicht Aussage der Doku). `soll` kommt nur als Fachwort (Soll-Datei, Soll-Schema von d-migrate) vor, `wird/werden` beschreibt durchweg Ist-Verhalten. Aufgefallen sind: „nicht entschieden“ (F-9) und der Begriff „Spike“ mit Einzelzahlen (F-4).

## Findings

### F-1 MEDIUM — Schritt 1 der Einbettung liefert auf dem Host kein Jar

* kategorie: MEDIUM
* quelle: `AGENTS.md` §3.8 (Aussage breiter als Beleg), `AGENTS.md` §5 Regel 5
* pfad: `docs/user/einbettung-und-betrieb.md` · „Das Jar bauen: `make build`. Es entsteht `tdswire.jar` ohne `Main-Class`“
* befund: `make build` ist `docker build --target build-jar` ohne `--output`; das Jar liegt nur in der Build-Stage. `docs/maintainer/releasing.md` sagt ausdrücklich „Das Target schreibt das Jar nicht auf den Host. Ein Weg, Jar und `lib/` aus dem Bau herauszugeben, besteht im Repo nicht“. Der Satz „Jar und `lib/` gehören auf den Klassenpfad der Anwendung“ folgt trotzdem ohne Hinweis, woher der Integrator beides bekommt; die Vorfassung trug immerhin „an einem fremden Programm ist nichts gemessen“.
* failure-szenario: ein Integrator folgt Schritt 1, sucht `tdswire.jar` und findet nichts; die Aufgabe „einbinden“ ist nicht ausführbar.
* verifizierbar: ja (`make build`, danach `ls tdswire.jar lib/`; `tools/mk/build.mk` und Dockerfile-Stage `build-jar`)
* klasse: Anleitungsschritt ohne erreichbares Ergebnis (Ausgabeweg des Artefakts fehlt)

### F-2 MEDIUM — Fehlerzeile zur Tabellenbeschreibung übernimmt einen Spezifikationssatz, den der Server nicht erfüllt

* kategorie: MEDIUM
* quelle: `AGENTS.md` §5 Regel 5 (Ist-Zustand), §3.8
* pfad: `docs/user/testserver-manuell.md` · „Start bricht ab, Log nennt Schlüssel und Art“
* befund: Die Zeile führt Beobachtung „Schlüssel und Art“ und Abhilfe „die Meldung nennt den Pfad im Dokument, Zeile und Spalte“ für dieselben Ursachen. Gefahren: unbekannter Typ gibt `tdswire: Beschreibung der Tabellen ungueltig: tables.t.columns.A.type (Zeile 8, Spalte 15): Typ unbekannt`; eine Datei über 1 MiB gibt `tdswire: Konfiguration ungueltig: SIMULATION_DESCRIPTION_FILE: TOO_LARGE`. Die Zeile mischt also die beiden Formen. `SPEC-035` widerspricht sich selbst („Die Meldung nennt den Pfad im Dokument, Zeile und Spalte“ gegenüber „die Diagnose nennt Schlüssel und Art, nie den Pfad“); der Widerspruch hat im Register einen Träger (`BEO-TDS/testserver-start-meldung-zielzustand-ohne-traeger`, `offen`, kein Slice), die Doku soll aber den Ist-Zustand zeigen, nicht den einen oder anderen Spezifikationssatz.
* verifizierbar: ja (`TESTSERVER_BESCHREIBUNG=<Datei> make testserver-up`)
* klasse: Doku gibt Spezifikationssatz wieder statt gemessenem Ist

### F-3 MEDIUM — Aufgaben ohne Schritte oder Beispiel (Themenkatalog statt Aufgabe)

* kategorie: MEDIUM
* quelle: Auftrag des Nutzers (Aufgabenbezug), `AGENTS.md` §5 Regel 5
* pfad: `docs/user/einbettung-und-betrieb.md` · „## 6. Den Server begrenzen, betreiben und beenden“; · „2. Die Ereignisse formatieren und ausgeben, wie die Anwendung es braucht“; `docs/user/testserver-manuell.md` · „## 5. Den Server anders einstellen“; · „2. Eine Soll-Datei schreiben“
* befund: (a) Aufgabe 6 hat kein Codebeispiel für `RequestSettings`/`ListenSettings`; ihre Schritte 1 bis 4 sind Feldlisten und Prosa. (b) Aufgabe 5 (Senke) hat für das Log der Anwendung keinen Code. (c) Testserver-Aufgabe 5 besteht aus Tabelle und Aufzählung; für eigene Benutzerliste, eigene Beschreibung und `REQUIRED` steht kein Befehl, obwohl `printf 'bob\t{plain}geheim\n' > users.txt; TESTSERVER_BENUTZER_DATEI=users.txt make testserver-up` gefahren Exit 0 und `Login ok` für `bob` ergibt (die Doku nennt den Login als „nicht gemessen“). (d) d-migrate-Schritt 2 (Soll-Datei) ist Prosa; die Datei ist nicht abdruckbar kopierbar, und das Anhängen an `ist.yaml` scheitert, weil die Datei ohne Zeilenende endet (die Fehlerzeile der Doku nennt es). Es fehlen die Aufgaben „TLS für die Bibliothek einschalten“ (`TlsKeyMaterial` aus einer PKCS#12-Datei) und „Prüfwert `{pbkdf2-sha256}` erzeugen“ (kein Werkzeug im Repo, keine Aussage in der Doku): die Voreinstellung `REQUIRED` ist damit für einen Betreiber nach der Doku nicht herstellbar. Die „Fehlersuche bei Login-Abweisung“ besteht aus einer Tabellenzeile.
* verifizierbar: nein (inhaltliche Prüfung); die gefahrenen Läufe sind oben genannt
* klasse: Themenkatalog statt Aufgabe

### F-4 MEDIUM — Zahlen eines Wegwerf-Spikes in der Nutzer-Doku

* kategorie: MEDIUM
* quelle: Reviewer-Skill (Messzahl aus einem Erzeuger, der nicht im Repo liegt), `AGENTS.md` §5 Regel 5 (keine Forensik)
* pfad: `docs/user/einbettung-und-betrieb.md` · „je Zeile blockierend 77 Threads und 3099 ms, blockweise 707 ms“; · „173 Byte je schmale und 454 Byte je breite Zeile im Heap, 1,54 Mio schmale Zeilen“
* befund: Die Zahlen stimmen mit `[ADR-0013](../plan/adr/0013-backend-grenzen.md)` (M1, M2, M6, M6/C, M7) überein. Der Erzeuger ist ein „Spike aus rund 600 Zeilen Kotlin im Scratchpad“ ([ADR-0013](../plan/adr/0013-backend-grenzen.md)), nicht im Repo; die Doku nennt „Spike“ und „fingiertes Backend“, aber nicht, dass sich die Zahlen im Repo nicht wiederholen lassen, und führt sie auf die Einheit genau. Für den Nutzer sind es Entwurfsmessungen, keine Eigenschaft, die er braucht.
* verifizierbar: nein (kein Gate, Erzeuger fehlt)
* klasse: Messzahl ohne wiederholbaren Erzeuger in der Nutzer-Doku

### F-5 MEDIUM — Harness-Nachzug: Anker löst auf eine Closure ohne Eintrag, Register zählt an der falschen Stelle

* kategorie: MEDIUM
* quelle: Reviewer-Skill (Fortschreibung lässt die zweite Fundstelle stehen), Baseline `v6.18.1` · `regelwerk/grundlagen-traceability.md` §Herkunfts-Anker
* pfad: `docs/plan/planning/observations/BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen/state.md` · „liegt in `.harness/skills/reviewer.md` (MEDIUM, Prüfpunkt „Nutzer-Doku nicht komplett gegen den Slice gelesen“)“
* befund: Alle drei Zielorte tragen `seit slice-katalog-und-ddl-zustand` (gefunden: `reviewer.md`, `plan-welle.md` 6e, `implement-slice.md` 20b). Die Closure-Notiz dieses Slice sagt in §7 „Nichts neu verkörpert; kein `liegt in`, gezählt wird“; der Anker führt damit auf eine Stelle, die die Regel nicht kennt, und die Anker-Paarung (von der Closure nach außen) prüft nichts. Das Beobachtungs-Register: die Regel „Nutzer-Doku ist Ist-Zustand, keine Planung“ hat die Beobachtung `planung-und-register-zeiger-in-nutzer-doku` (Stand weiter „offen — 1×, unter der Schwelle“, eine Evidenz-Datei); die `liegt in`-Zeilen stehen stattdessen unter einer anderen Beobachtung (`fortschreibung-laesst-zweite-fundstelle-stehen`), deren Stand-Zeile unverändert „verkörpert“ lautet. Verkörpert bei 1× steht außerhalb des Geltungsbereichs „nur Regeln, die die 3×-Schwelle erreicht haben“.
* failure-szenario: der Retirement-Check folgt dem Anker, findet in §7 keine Regel und kann die Herkunft nicht prüfen; die Zählung der einschlägigen Beobachtung bleibt bei 1×, obwohl dieser Lauf das nächste Auftreten (Nutzer-Rüge „nicht gebaut“ in der Doku) ist.
* verifizierbar: nein (kein Sensor für Register-Zustand)
* klasse: Herkunfts-Anker ohne Gegenstück in der Closure

### F-6 LOW — KDoc von `AnleitungTest` behauptet mehr, als der Abgleich trägt; kein Sensor zwischen Test und Doku

* kategorie: LOW
* quelle: `AGENTS.md` §3.8, §3.7
* pfad: `src/integrator/kotlin/io/github/pt9912/tdswire/integrator/AnleitungTest.kt` · „jede Funktion mit dem Präfix `anleitung` steht dort wörtlich“
* befund: `anleitungBefunde` steht nicht als Codeblock in der Doku (nur namentlich erwähnt); zwei der sechs Kotlin-Blöcke (`when (outcome)`-Fragment mit `source`, Senken-Fragment) sind Auszüge, keine Funktionen. Die vier Funktionen `anleitungKonfiguration`, `anleitungTabellen`, `AnleitungBackend`, `anleitungVeraenderlich` sind wörtlich gleich (per Skript verglichen, nach Einrückungsabzug). Mutation gefahren: im Test `Row(listOf("zwei"))` zu `"DREI"` geändert, `make docs-check` bleibt Exit 0, 0 Befunde (Änderung zurückgesetzt); die Gleichheit hält kein Gate, und der Test selbst bliebe auch grün (er zählt nur Zeilen).
* verifizierbar: ja (Mutation oben)
* klasse: Gleichheitszusage zwischen Test und Doku ohne Sensor

### F-7 LOW — Zeiger in `tools/log-muster.sh` auf die alte Fundstelle des Log-Formats

* kategorie: LOW
* quelle: Reviewer-Skill (Fortschreibung lässt die zweite Fundstelle stehen)
* pfad: `tools/log-muster.sh` · „docs/user/einbettung-und-betrieb.md; die Muster lesen es literal“
* befund: Das Format der Logzeile steht jetzt in `docs/user/referenz-ereignisse.md` („Log des Testservers“); der Kommentar zeigt auf die Anleitung. Die Datei steht nicht in der Änderungsliste.
* verifizierbar: ja (Grep)
* klasse: Zeiger auf umgezogene Fundstelle

### F-8 LOW — Erwartete Ausgaben weichen im Wortlaut vom gefahrenen Lauf ab

* kategorie: LOW
* quelle: `AGENTS.md` §3.8
* pfad: `docs/user/testserver-manuell.md` · „Ergebnis (Lauf vom 2026-11-07, Exit 0): `Schema written to /work/ist.yaml`“; · „Start bricht mit Exit 1 ab“
* befund: `schema reverse` gibt zusätzlich `Report written to /work/ist.report.yaml` aus und legt die Datei ab; `data export` endet auf `… (0 rows, 0.00 MB) in 0.16 s`. Bei ungültigem `LOG_LEVEL` sieht der Nutzer an `make testserver-up` „der Server wurde nicht bereit“ mit `tdswire: Konfiguration ungueltig: LOG_LEVEL: INVALID_VALUE` und Make-Fehler 1, nicht „Exit 1“ des Servers. Alle übrigen gefahrenen Ausgaben (up/status/logs/down, `Login ok`/`Login fehlgeschlagen`, compare DIFFERENT mit `Tables removed: 1` und `column Email`, migrate Exit 0 mit `statementsAttempted` 2, zweites compare IDENTICAL, Export `[]`) stimmen.
* verifizierbar: ja
* klasse: Erwartete Ausgabe unvollständig zitiert

### F-9 LOW — „nicht entschieden“ als Aussage der Nutzer-Doku

* kategorie: LOW
* quelle: `AGENTS.md` §5 Regel 5
* pfad: `docs/user/einbettung-und-betrieb.md` · „Dateirechte und Besitzer der Benutzerliste im Betrieb sind nicht entschieden und nicht gemessen; diese Anleitung empfiehlt keine“; ebenso `docs/user/referenz-konfiguration.md` · „für den Betrieb sind nicht entschieden und nicht gemessen“
* befund: Der Halbsatz „nicht entschieden“ nennt einen offenen Planungspunkt (Register `benutzerliste-dateirechte-im-betrieb-offen`); der Ist-Zustand wäre „die Bibliothek setzt keine Rechte; die Doku macht keine Aussage“.
* verifizierbar: nein
* klasse: Planungszustand als Aussage

### F-10 LOW — Codebeispiele ohne Pakete; Einstiegsseite nur aus der Wurzel verlinkt

* kategorie: LOW
* quelle: Auftrag des Nutzers („lauffähige Codebeispiele“), `AGENTS.md` §5
* pfad: `docs/user/einbettung-und-betrieb.md` · „internal fun anleitungKonfiguration“; `harness/README.md` · „Nutzer-Doku (Betrieb, Qualität)“
* befund: Kein Block nennt `import`-Zeilen; die Pakete (`runtime`, `config`, `config.auth`, `domain`, `simulation`) sind nur für `port` genannt, die Datei mit den Imports wird im Vorspann verlinkt. `docs/user/README.md` ist vom Wurzel-README verlinkt, nicht aber von `harness/README.md` Rang 6 und `AGENTS.md` §2 Rang 6, die weiter auf `einbettung-und-betrieb.md` zeigen.
* verifizierbar: nein
* klasse: Beispiel nicht aus der Doku allein übersetzbar

### F-11 INFO — Widerspruch zur Spezifikation „Class 20 bei `TDS_SIM_UNKNOWN_OBJECT`“

* kategorie: INFO
* quelle: `SPEC-038`, Messprotokoll Referenzabgleich A-3
* pfad: `docs/user/referenz-konfiguration.md` · „`TDS_SIM_UNKNOWN_OBJECT` sendet Class 20 und schließt die Verbindung, der echte Server sendet Class 16 und lässt sie offen (A-3)“
* befund: Die Doku gibt den Ist-Zustand und die Messung wieder und erfindet nichts; Träger sind die Register-Einträge `unbekannte-tabelle-class-20-statt-16-am-server`, `client-schliesst-bei-class-20-selbst`, `server-schliesst-bei-class-20-pipeline`. Die Fehlerzeile `TDS_SIM_UNKNOWN_OBJECT` in Aufgabe 3 der Einbettung nennt das Schließen der Verbindung nicht und verweist nicht auf die Tabelle.
* verifizierbar: nein
* klasse: Spezifikationsabweichung mit Träger, in der Doku korrekt abgebildet

### F-12 INFO — Beispiel zeigt `encryption_not_offered` ohne Erklärung

* kategorie: INFO
* quelle: Maintainability
* pfad: `docs/user/testserver-manuell.md` · `"event":"encryption_not_offered"`
* befund: Die Logzeile des Beispiels steht vor jeder angenommenen Anmeldung; der Text erklärt nur `security_weakened`. Ein Nutzer, der Fehler sucht, sieht eine WARN-Zeile ohne Deutung (die Referenz der Ereignisse erklärt sie).
* verifizierbar: ja
* klasse: Beispielzeile ohne Deutung

### F-13 INFO — Abdeckungsmatrix nicht neu erzeugt

* kategorie: INFO
* quelle: `AGENTS.md` §5 Regel 6
* pfad: `src/integrator/kotlin/io/github/pt9912/tdswire/integrator/AnleitungTest.kt` · `@spec SPEC-059`
* befund: Die vier neuen Tests tragen den Tag `SPEC-059`; `docs/plan/spec-abdeckung.md` ist erzeugt (`make spec-abdeckung`, kein Gate) und im Diff nicht fortgeschrieben. Zuständig: Verifier.
* verifizierbar: ja (`make spec-abdeckung`)
* klasse: Erzeugtes Dokument nicht nachgezogen

### F-14 INFO — Test: Quelle, Flackern, Schichten

* kategorie: INFO
* quelle: [ADR-0021](../plan/adr/0021-oeffentlicher-integrations-einstieg.md)
* pfad: `src/integrator/kotlin/io/github/pt9912/tdswire/integrator/AnleitungTest.kt` · `class AnleitungTest`
* befund: Der Test importiert nur Typen der Liste von `SPEC-059`; `make flaeche` und der Einbettungstest bleiben grün. Port 0, `stop()` im `finally`, Lese- und Verbindungsfrist des `WireClient` fest (5 s / 20 s), kein `sleep`, keine Zeitschranke im Test: kein Flackern erkennbar (vier Läufe grün, ein Frischlauf unter Last). Die Datei liegt im Source-Set `integrator`, das über `COPY src` in Stage `test` kommt; `gate-zuordnung` und `stempel-eingaenge` sind grün. Die Behauptung „nach `stop()` nimmt der Port keine Verbindung mehr an“ (Doku, Aufgabe 1) trägt `AnleitungTest` nicht; ein Test dafür wurde nicht gefunden.
* verifizierbar: ja
* klasse: —

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `docs/user/referenz-konfiguration.md` (Felder, Bereiche, Voreinstellungen, Fehlercodetabelle, Antwortformen) | Zahlen gegen `ServerConfig.kt`, `ConfigValidation.kt`, `ErrorCode.kt` und `SPEC-012`, `SPEC-060` geprüft, ohne Befund |
| `docs/user/referenz-ereignisse.md` (18 Arten, Ebenen, Felder, Gründe) | gegen `EventKind`, `TelemetryEvent.kt`, `StartRefusal`, `LoginRejectionReason`, `TlsFirstRefusal`, `WeakeningReason` geprüft, ohne Befund |
| Zitierte Tests und Messprotokolle (13 Testklassen, `Leeres_Ergebnis_…`, M-1/M-4/M-5, A-1/A-3) | vorhanden und passend, ohne Befund |
| `testserver-manuell.md` Aufgaben 1, 2, 4 und der Lauf mit d-migrate | selbst gefahren, Ausgaben bis auf F-8 gleich |
| Links und Verweise (`make docs-check`, Wurzel-README, Anker aus `docs/user/README.md`) | Exit 0, keine toten Anker, ohne Befund |
| `.claude/commands/*.md`, `.harness/skills/reviewer.md` (Wortlaut) | Anker `seit slice-katalog-und-ddl-zustand` an allen drei Zielorten vorhanden (zur Wirkung F-5) |
| Detekt, Schichtregeln, Suppression im neuen Test | `make gates` Exit 0, geprüft, ohne Befund |
