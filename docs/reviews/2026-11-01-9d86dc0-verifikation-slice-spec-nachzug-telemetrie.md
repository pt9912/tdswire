# Verifikationsbericht: slice-spec-nachzug-telemetrie

* Rolle: Verifier (Modul 11), frischer Kontext. Branch `worktree-agent-a8241fa30f4d7b6a2`, HEAD `9d86dc0`. Diff gegen `0db1db4`: nur `spec/spezifikation.md` (+81/-18) und Dokumente unter `docs/`; `spec/architecture.md`, `spec/lastenheft.md`, ADRs, `src/` und `tools/` unverändert. Plan/DoD: `docs/plan/planning/done/slice-spec-nachzug-telemetrie.md` §2; Entscheidungen [ADR-0020](../plan/adr/0020-logformat-und-observability-schnittstelle.md), Lastenheft 0.6.1; Review [2026-11-01-02db795-slice-spec-nachzug-telemetrie.md](2026-11-01-02db795-slice-spec-nachzug-telemetrie.md). Verdichtet vom Aufrufer abgelegt; Findings vollständig.

## Gesamturteil: closure-fähig mit Bedingungen

Die drei Liefer-Punkte sind inhaltlich erfüllt. Bedingung: F-1 (falscher Marker zum gebauten Stand) beheben. Kein HIGH.

## Sensoren (selbst gefahren)

* `make docs-check`: 648 Dateien, 0 Befunde (1,2 s Einzelmessung). `make gates`: Exit 0, 10 s (Einzelmessung; 53 Docker-Schritte aus dem Cache, `referenztest-dotnet` übersprungen; ein spec-only-Diff ändert keine Test-Eingänge). `make spec-abdeckung`: nicht gefahren (Closure-Aufgabe).
* **Gegenprobe 1:** `[ADR-0020](../plan/adr/0020-logformat-und-observability-schnittstelle.md)` in den Satz „Pflicht-Logfelder“ eingefügt: `make docs-check` rot (`id-unlinked`, `matrix-forbidden`); zurückgesetzt, `cmp` gegen die Kopie. Die Regel „keine ADR-Bezüge in spec/“ trägt das Gate.
* **Gegenprobe 2:** Skript sucht Zeilen mit Senke, `principal`, `LOG_LEVEL`, `METRICS_INTERVAL`, `ts`, `level`, `client_profile` oder Ereignis ohne „Zielzustand“ im selben Absatz; auf einer Testkopie ohne Marker an `TDS-FA-09.a` Nr. 3 meldete es genau die Zeilen 151, 153, 155. Die Zeilen 911 bis 975 stehen unter Blockmarkern („Zielzustand, nicht gebaut“, „Setzung, nicht gemessen“); je Absatz oder Block, nicht je Spiegelstrich: tragfähig, nur zeilenweise-heuristisch prüfbar.

## DoD

* **1. Pflicht-Logfelder: bestätigt (mit F-1).** `ts` (UTC), `level`; `principal` nur im Ereignis „Anmeldung angenommen“; `client_profile` aus dem ausgehandelten Protokoll (`tds-7.4`/`tds-8.0`, vor der Aushandlung `unknown`); `connection_id` Zähler des Listeners, `session_id`/`request_id` UUID; Parameterwerte „nie“; Ereignismodell geschlossen ohne Freitextfeld; Zeichenketten 256 (Setzung); „bisher keines“ nirgends mehr.
* **2. Zugang zu Metriken und Spans: bestätigt.** Senke als Parameter von `start`; ohne Senke schreibt die Bibliothek nichts (Lastenheft 0.6.1); Senken-Vertrag; Abstand 60 s, 0 schaltet aus, Prüfung in `config` (Setzung); SPEC-017 bis 019 bleiben; kein SQL-Text als Label; JMX, Text-Endpunkt, Telemetrie-Bibliothek nicht zugesagt; Perzentile (Offen (d)) „nicht festgelegt“.
* **3. Eingänge des Testservers (SPEC-052): bestätigt.** `LOG_LEVEL` (`ERROR`, `WARN`, `INFO`, `DEBUG`; Voreinstellung `INFO`; anderer Wert bricht den Start ab), `METRICS_INTERVAL_SECONDS`; JSON-Zeile mit `ts`, `level`, `event`, Warteschlange 1024, Verwerfen und `dropped`, Diagnose des Startabbruchs synchron, Frist 2 s; Bereitschaftszeile kein Ereignis; alles „Setzung, nicht gemessen“ und „kein Vertrag der Bibliothek“.
* Nachweis-Pflichten der DoD (Tests mit `@spec`, Closure-Notiz, Register, Risiko-Ausgänge): keine Haken gesetzt, Closure-Aufgaben.

## Fundstellensuche (Trefferliste für die Closure-Notiz)

`bisher keines` 0, `Standardlogging` 0. `ins Log`: Z. 98 und 853 stimmen mit Abschnitt 4 überein; 835 (Drosselung, „heute schreibt sie nichts ins Log“) ohne Widerspruch; 1088 siehe I-1. `im Log`: 155, 905, 1111, 1197 konsistent; 832 siehe F-2. `Standardausgabe` 1290, `Serverprozess` 1287/1290, `LOG_LEVEL`/`METRICS_INTERVAL` 1290/1331: ok. `Korrelation`: Spec 150 neu formuliert; `architecture.md` 208 verträglich. `Start-Log` 1213 konsistent. `Log-Eintrag` 896: wahr, aber Marker falsch (F-1). `geloggt` 153/1193 ok. Keine ADR-, Slice-, Welle- oder Pfad-Wörter außerhalb `spec/` im Diff (nur das vorbestehende `BEO-TDS/messung-am-echten-sql-server-fehlt`, Z. 900, nicht im Diff).

## Selbstkonsistenz Spec gegen ADR-0020

Frage 1 bis 8 deckungsgleich (Modell, Senke, Vertrag, `LOG_LEVEL`, Bereitschaftszeile, Authentifizierungsadapter bleibt nicht erreichbar; `error_code` als `TDS_*`, Ausnahme nur als Typname, vier Zeichenketten-Felder und „keine geschlossene Menge“, 256 Zeichen; Frage 3 B, 4 A, 5 C samt IDs; Frage 6 A samt `metrics`, 60/0; Frage 7 B mit allen Zahlen und der Regel zum Prozessende; Frage 8 A). Offen (a) folgt Lastenheft 0.6.1; (b) nicht in der Spec; (c) Voreinstellung ISO-8601, Setzung; (d), (e), (f), (g) „nicht festgelegt“; (h) nicht Gegenstand. Bewusste Abweichung: `Aborted.reason` ohne Adresse und Port (ADR: „derselbe Inhalt wie das Abbruch-Ereignis“; Spec rangiert über der ADR; M-4). Von der Spec nicht übernommen (bewusst; Closure-Notiz): Escaping-Zusage des Testserver-Adapters, `TdsServer.stop()` kehrt erst nach dem Ende-Ereignis zurück, Schreiber-Thread startet erst mit dem laufenden Server, die Bereitschaftszeile läuft nie über die verwerfende Warteschlange, Prozessende nach der Frist auch bei hängendem Kanal.

## Marker „Zielzustand, nicht gebaut“ gegen den Code

Stimmig: „Der gebaute Stand schreibt eine Textzeile und kennt weder `ts`, `level` noch `principal`“ (`ServerLog.line(text)` schreibt `tdswire: <Text>`); die ACHTUNG-Zeilen in `Bootstrap.kt:178/181` für beide Modi; `start(config, source)` ohne Senke. Nicht stimmig: F-1.

## Findings

* **F-1 (MEDIUM), falscher Marker: „der gebaute Stand schreibt den Text der Ausnahme ins Log“ (§3.8):** Zeile `SPEC-048` (Z. 853) und der Absatz Z. 896/898. Gegenbeleg: `ErrorMapper.unexpected(...)` übergibt `exception.javaClass.name` als Marke (`ErrorMapper.kt:80-82`, KDoc „im Log nur ihr Typname“); `ConnectionWiring.kt:124` schreibt `($cause)`, und `cause` ist dieser Typname oder eine feste Marke wie `unknown-failure-code`; `ErrorMapperTest.kt:264-272` (`IllegalStateException(secret)`, „der Eintrag nur den Typnamen“) belegt das. Herkunft: Der Vorbestand (Z. 895/896 in `0db1db4`) sagte richtig „Code und Typname, nicht ihren Text: enger als der Satz in SPEC-048“; die Fix-Runde ersetzte diese Wahrheit durch den falschen Marker, gestützt auf die Reviewer-Behauptung M-2 („der gebaute Code schreibt `($cause)` mit Text“), die selbst falsch war. Der Zielzustand „nur Typname, nie Text“ ist für den Anfragepfad schon gebaut; gegenüber dem Zielzustand fehlen nur Format und Felder. Nebenbefund: `Bootstrap.kt:130` (`Start gescheitert: ${started.failure.message}`), `Bootstrap.kt:192` und `SimulationSetup.kt:41` schreiben Fehlertext beim Start (Startpfad, nicht der Pfad von SPEC-048); soll der Marker bleiben, muss er genau diesen Startpfad meinen. Vorschlag: Marker an SPEC-048 und im Absatz 896 auf „der gebaute Stand schreibt Code und vollqualifizierten Typnamen als Textzeile“ ändern oder streichen.
* **F-2 (LOW), SPEC-010 „im Log sichtbar“ ohne Bedingung (Review L-2 nur zur Hälfte behoben):** Z. 832 „Abschwächung nur durch ausdrückliche, im Log sichtbare Konfiguration“ gegen die Tabellenzeile Z. 1111 „im Log sichtbar, wenn eine Senke übergeben ist“. Folgepflicht nach dem Umbau-Merge.
* **F-3 (LOW), SPEC-043 nennt die Diagnose unbedingt:** Z. 763 „Ein belegter Port beendet den Start mit einer eigenen Diagnose, die Adresse und Port nennt“ gegen Abschnitt 4 „Ohne übergebene Senke gibt es keine Diagnose mit Adresse und Port“. Dieselbe Klasse wie `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`.
* **I-1:** Z. 1088 („damit sie ins Log geht“), Z. 34/1191 („Server-Log“ ohne Senken-Bedingung) unkritisch, bei erneuter Fundstellensuche nach M-4 mitprüfen. **I-2:** Zahlen 1024 und 2 s hängen am Block-Kopf „Setzung, nicht gemessen, auch die Zahlen“ (SPEC-052), im selben Satz stehen sie nur bei 60 s und 256: vertretbar, knapp. **I-3:** Merge-Hinweise (Änderungsprotokoll) nicht erneut gefahren; der Merge von `main` (`9d86dc0`) war sauber.

## Offene Nutzerentscheidung (nur gemeldet)

**M-4:** Die Adresse des Bindefehlers steht nur im Ereignis, `StartOutcome.Aborted.reason` bleibt ein Text ohne Konfigurationswert; die Spec sagt selbst: „Ohne übergebene Senke gibt es keine Diagnose mit Adresse und Port“. `TDS-FA-09` verlangt eine verständliche Diagnose bei belegtem Port. Wirkung: F-3 und der Abstand zu [ADR-0020](../plan/adr/0020-logformat-und-observability-schnittstelle.md).

## Folgepflichten

1. SPEC-010 (Z. 832): Bedingung „wenn eine Senke übergeben ist“ nach dem Umbau-Merge. 2. `tools/laufzeittest.sh` Z. 105 (`! docker logs ... | grep -Eq 'alice|secret'`) nach dem Umbau-Merge: sobald der Testserver `principal` ausgibt, steht `alice` im Log; die Prüfung muss `secret` sperren und `alice` nur außerhalb des Feldes `principal` im Ereignis „Anmeldung angenommen“. 3. Hunk des SPEC-048-Absatzes (Z. 896 bis 898) beim Merge mit Umbau und Abbruchpfad: der falsche Marker (F-1) darf nicht festgeschrieben werden. 4. Trefferliste der Fundstellensuche und die Zuordnung Test|Festlegung je Aussage in die Closure-Notiz; `make spec-abdeckung`; `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` bekommt mit F-1 und F-3 weitere Evidenz.

## Risiken aus Plan §6 (nur gemeldet)

Spec früher als Code: offen bis zur Closure (F-1 wirkt gegen die Marker). „Bisher keines“ an zwei Stellen: eingetreten und ersetzt, dritte Fundstelle gefunden und auf eine Aussage gezogen, verbleibend F-1 und F-3. Lesart Happy Path (Offen (a)): Lastenheft 0.6.1 stützt sie, erwartet „entfallen“. Zahlen als Setzung: gekennzeichnet. Instanztrennung (f) und Quelladresse (e): offen. Zwei Träger für die Spec-Arbeit: eingetreten, Bestätigung durch den Nutzer steht aus. `Aborted.reason` gegen Prozessende: Ausgang hängt an M-4.
