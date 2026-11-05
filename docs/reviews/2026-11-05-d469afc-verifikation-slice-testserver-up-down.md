# Verifikationsbericht: slice-testserver-up-down (HEAD d469afc)

* Rolle: Verifier (Modul 11), frischer Kontext. Gegenstand: [slice-testserver-up-down](../plan/planning/done/slice-testserver-up-down.md) (HEAD `d469afc`, Umsetzung `7c1bef2` und `d469afc`) gegen DoD §2, Plan §3, [ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md), [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md), [TDS-RB-01](../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) und `AGENTS.md` §3. Eingang: Review-Report [2026-11-05-7c1bef2-slice-testserver-up-down](2026-11-05-7c1bef2-slice-testserver-up-down.md) (0 HIGH, 4 MEDIUM).
* Selbst gefahren (Arbeitsbaum sauber, HEAD `d469afc`): `make testserver-up` mit `TESTSERVER_PORT=24317`, `testserver-status`, zweiter `up`, `testserver-logs`, `testserver-down`, zweiter `down`, Portprobe, `make help`, `make stempel-eingaenge-pruefung`, `make docs-check` (720 Dateien, 0 Befunde).
* Nicht selbst gefahren: `make gates` und `make testserver-selbsttest` (159 s). Beide sind als grün berichtet; ich habe die Belege nicht reproduziert. Ich habe die 9 Mutationen im Selbsttest-Skript gelesen, nicht erneut ausgeführt. Der Lauf von `make gates` auf `main` ist ohnehin Closure-Pflicht.

## Gesamturteil

**Closure-fähig: ja, mit Bedingungen B-1 bis B-3.** Kein HIGH, 0 MEDIUM, 3 LOW, 2 INFO. Die drei Liefer-Punkte und die Zeile in `harness/README.md` sind am Code und am Handlauf bestätigt. Offen sind nur die Pflichten, die der Plan selbst für die Closure vorsieht.

Bedingungen:

* B-1: `make gates` und `make testserver-selbsttest` auf `main` in der Closure-Notiz mit Ausgabe belegen (nur gemeldet, hier nicht reproduziert).
* B-2: Jedes Risiko aus §6 erhält einen Ausgang. Die ersten drei (Frage zu [ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md), Loopback/Anmeldung, Image-Name) sind durch Nutzerentscheidung laut Review-Eingang entschieden (Loopback voreingestellt, wählbar mit Warnung; `tdswire-testserver:lokal`). Der Ausgang dieser Frage (Architect-Prüfung) ist mir aus dem Repo nicht belegt. Er gehört in die Notiz.
* B-3: Der Closure-Trigger aus §5 (Lauf in frischem Kontext mit den Aufrufen der Kurzhilfe, mit Last) ist mit diesem Bericht nur teilweise gedeckt. Mein Lauf war auf `main` und in frischem Kontext, aber ohne gemessene Last. Die Ausgabe gehört in die Closure-Notiz.

## DoD-Punkte (§2)

* **Liefer-Punkt 1, Targets: bestätigt.**
  * Start im Hintergrund: `docker create` und `docker start`. Der Container lief danach, `docker port` zeigt `1433/tcp -> 127.0.0.1:24317`.
  * Voreinstellung Loopback, Port und Image-Tag per Variable: `TESTSERVER_HOST` (Voreinstellung `127.0.0.1`), `TESTSERVER_PORT`, `TESTSERVER_IMAGE`. Gefahren: Port 24317 gesetzt, gebunden wie gesetzt.
  * Rückkehr bei Bereitschaft: Schleife auf die Logzeile `tdswire: gestartet, Port 1433` (die Zeile steht in `src/testserver/.../Main.kt`). Mit gebautem Image kehrte der Lauf nach 3,7 s zurück. Der Nicht-bereit-Pfad (`TESTSERVER_WARTEN`) räumt auf und meldet; im Selbsttest durch eine kaputte Beschreibungsdatei belegt.
  * Zweiter `up`: Meldung „der Container tdswire-testserver existiert schon; zuerst 'make testserver-down'“, Exit 1 (gefahren; make meldet „Fehler 1“).
  * `down` ohne Container: „kein Container …, nichts zu tun“, Exit 0 (gefahren). `down` mit Container: gestoppt und entfernt, danach kein Container (`docker ps -a` leer) und Port abgelehnt (gefahren).
  * `logs` zeigt das Log; `status` meldet „läuft (… 127.0.0.1:24317)“ mit Exit 0, danach „nicht gestartet“.
* **Liefer-Punkt 2, Konfiguration: bestätigt.**
  * Benutzerliste: Voreinstellung `alice` / `secret` im Testmodus. Eigene Datei über `TESTSERVER_BENUTZER_DATEI`.
  * Verschlüsselungsrichtlinie: `TESTSERVER_VERSCHLUESSELUNG` mit `TEST_UNENCRYPTED` oder `REQUIRED`. `REQUIRED` verlangt Schlüsselspeicher und Kennwortdatei, sonst Abbruch (im Selbsttest durch die vier Eingabefehler belegt).
  * Eigene Beschreibungsdatei über `TESTSERVER_BESCHREIBUNG`. Die Beispielbeschreibung `src/testserver/resources/manuell/sim.yaml` liegt bei: Tabellen `kunden` und `bestellungen`. Die Stage `testserver` kopiert `src`, der Server lädt die Datei im Handlauf (gefahren: Login-fähiger Start).
  * Ausgabe von `up`: Host und Port, „Benutzer alice, Kennwort secret“, „Testwerte, keine Verschluesselung (TDS 7.4 im Klartext)“ (gefahren, wörtlich so). Die DoD fordert „Testwerte, keine Verschlüsselung“; der Text steht ohne Umlaut, das ist durch den Skriptstil gedeckt.
* **Liefer-Punkt 3, Selbsttest: bestätigt nach Lesen, Lauf nicht reproduziert.**
  * Ablauf im Skript: `up`, Bindung an Loopback, Rohbytes-Login mit dem Image `laufzeit-client` über `--network host` gegen den veröffentlichten Port, `down`, danach `keiner` (Container) und `port_frei`. Der Teil „kein Netzwerk“ der DoD ist nicht eigens geprüft; der Container hat nur das Standardnetz von Docker, es entsteht kein eigenes (F-3).
  * Mutationen: neun, zu jedem DoD-Zweig mindestens eine: `zweiter-up` (Abbruch, Meldung), `down-ohne-container`, `port-belegt` (Aufräumen, Meldung; der Zweig selbst wird per `if false && docker start` entfernt), dazu `nicht-bereit` (Aufräumen, Meldung), `loopback` und `warnung`. Vor jeder Mutation prüft die Grundlage das unveränderte Skript, und eine Mutation, die den Text nicht ändert, färbt den Selbsttest rot (`aendert nichts`).
  * Der Selbsttest läuft mit eigenem Namen, Port und Image-Tag (`tdswire-st-$$`), so streitet er nicht mit einem Handlauf.
* **Liefer-Punkt `make gates` grün: berichtet, nicht reproduziert (B-1).** Das Doku-Gate (`make docs-check`) und `make stempel-eingaenge-pruefung` habe ich selbst gefahren, beide grün.
* **Zeile in `harness/README.md` und `make help`: bestätigt.** §Sensors (Werkzeuge) führt vier Zeilen: `testserver-up`, `testserver-down`, `testserver-logs` / `testserver-status`, `testserver-selbsttest`. `make help` zeigt alle fünf Targets mit Kurzhilfe. Die Zeilen sind „kein Gate“ und binden an [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md), [ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md), [TDS-RB-01](../../spec/lastenheft.md#tds-rb-01--implementierungssprache) beziehungsweise [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit). Die Kostenangabe des Selbsttests nennt Dauer, Cache-Zustand, Last und Datum.
* **Review-Report: Datei da, Haken im Plan offen.** Closure-Notiz, Register, Risiko-Ausgänge und Paarungen: erst bei der Closure (kein Befund).

## Plan-vs-Code-Diff (§3)

| Plan | Code | Urteil |
|---|---|---|
| `tools/mk/testserver.mk` neu | liegt, fünf Targets, `.PHONY`, kein `GATE_CHECKS` | gedeckt |
| `tools/testserver.sh` neu | liegt; Stage `testserver`, `docker cp` statt Mount, Bereitschaft, Meldungen | gedeckt |
| `Dockerfile` update (falls nötig) | unverändert (Stage `testserver` trägt den Handlauf) | gedeckt, „sonst unverändert“ |
| `src/testserver/resources/manuell/` neu | `sim.yaml` | gedeckt |
| `tools/testserver-selbsttest.sh` neu | liegt | gedeckt |
| `tools/stempel/*.eingaenge` update, soweit ein Gate die Dateien liest | nicht geändert; `make stempel-eingaenge-pruefung` grün | gedeckt, „sonst bewusst nicht“. Der Selbsttest ist kein Gate. |
| Nicht im Plan, im Diff | `harness/README.md`-Zeilen, Review-Report | von der DoD verlangt |

Keine Änderung an `src/main`, am Dockerfile oder an `GATE_CHECKS`: die Ausschlüsse aus §1 sind gehalten.

## Konformität zu Entscheidungen und Hard Rules

* [ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md): lokales Test-Image ohne Registry und ohne Produkt-Image; der Container startet den vorhandenen `main` des Testservers. Konform. Der Handlauf mit veröffentlichtem Port ist durch Entscheidung 4 nicht ausgeschlossen; der Architect-Ausgang liegt mir nicht vor (B-2).
* [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md): Kein Target in `GATE_CHECKS`, nicht in `make e2e` oder CI, Selbsttest als Werkzeug. Konform.
* Docker-only (§3.1): Alle Befehle laufen über `docker` und `make`. Konform.
* Suppression-Verbot (§3.2): Kein Kotlin berührt; im Skript steht nur `shellcheck disable=SC2086` (Shell, nicht von der Regel gemeint). Konform.
* §3.7 (Kommentare): die Köpfe beschreiben Zusage, Kopplung (`zweig:`-Vermerke) und Grenze; kein Konjunktiv über Verworfenes gefunden. Konform.
* §3.8 (Aussage trägt so weit wie Beleg): Der Kopf von `tools/testserver.sh` und `tools/testserver-selbsttest.sh` benennt, was nicht belegt ist (TLS, Client außer Rohbytes, Dateirechte, Fehlerpfade von `docker cp`, SIGINT, eigene Benutzerliste). Konform, mit F-1 als Ausnahme einer Formulierung.

## Umgang mit dem Review-Report: Stimmt er?

| Befund | Behauptung | Prüfung |
|---|---|---|
| F-1 (Loopback-Voreinstellung ohne Test) | im Selbsttest eingearbeitet | stimmt: `loopback`-Prüfung (`docker port`), Grundlage `loopback_ok`, Mutation `loopback` (127.0.0.1 → 0.0.0.0), Mutation `warnung`, Prüfung „up ohne fremden Host warnt nicht“ |
| F-2 (Aufräumzweige) | eingearbeitet | stimmt für den Pfad „nicht bereit“ (Aufräumen und Meldung, je eine Mutation). Die `docker cp`-Aufräumzweige (`|| { docker rm -f …; exit 1; }`) bleiben ohne Test; der Kopf nennt sie unter „Nicht belegt: Fehlerpfade des docker cp“. Der Plan verlangt sie nicht (F-2 im Review war über den Plan hinaus). Vertretbar. |
| F-3 (Konfigurationszweige) | eingearbeitet | stimmt: vier Eingabefehler-Prüfungen, `up` mit eigener Benutzerliste und eigener Beschreibung, ungültige Beschreibung; Kopf nennt als nicht belegt „ein Login mit der eigenen Benutzerliste“, TLS, Dateirechte |
| F-4 (Mutation `port-belegt` greift nicht am Zweig) | eingearbeitet | stimmt: zwei Mutationen, eine auf das `docker rm`, eine auf `if ! docker start` (`if false && docker start`), die Meldung „belegt“ wird geprüft |
| F-5 (zwei Änderungen in einer Mutation) | eingearbeitet | stimmt: `zweiter-up-abbruch` und `zweiter-up-meldung` getrennt |
| F-6 (`localhost`-Zweig) | entfernt | stimmt: das `case` nennt `127.0.0.1`, `::1`, `[::1]` |
| F-7 (Zeiger) | korrigiert | stimmt: der Kopf von `tools/testserver.sh` nennt Voreinstellung, Testwert `alice` / `secret`, Warnung und Variablen |
| F-8 (Kostenangabe) | korrigiert | stimmt: „159 s, Stages im Cache, Last 14 bis 15 (Einzelmessung 2026-11-05)“; Hosts nennt die Zeile nicht (`AGENTS.md` §5 Regel 7 verlangt Host), siehe F-2 unten |
| F-9 (SIGINT bleibt Grenze im Kopf) | dokumentiert | stimmt: Kopf von `tools/testserver.sh` nennt „ein Abbruch von `up` durch SIGINT (der Container bleibt dann stehen, der naechste `up` meldet es)“ |

## Findings

Kein HIGH, kein MEDIUM.

* **F-1 (LOW):** Die Zeile für `testserver-status` in `harness/README.md` und die Kurzhilfe sagen „Exit 0 ja, 1 nein“. Das gilt für `bash tools/testserver.sh status`. Über `make testserver-status` endet der Aufruf bei „nicht gestartet“ mit **Exit 2** (make-Fehler; gefahren: `status=2`). Der Selbsttest prüft den Exit-Code von `status` nur im laufenden Fall. Fix-Optionen: Aussage auf „ungleich 0“ ändern oder den Code im Selbsttest für den nicht-laufenden Fall prüfen (Entscheidung beim Planner).
* **F-2 (LOW):** Die Kostenzeile des Selbsttests in `harness/README.md` nennt Datum, Cache und Last, aber keinen Host (`AGENTS.md` §5 Regel 7: Datum, Host und Bedingung). Die Nachbarzeilen halten es ebenso; Formfrage.
* **F-3 (LOW):** Die DoD nennt „kein Netzwerk“ nach dem Selbsttest. Der Selbsttest prüft Container, Image-Aufräumen und Port, aber kein Netzwerk; `up` legt keines an (`docker create` ohne `--network`), so ist die Aussage wahr, aber nicht belegt. Entweder den Satz in der DoD auf „kein Container“ kürzen oder `docker network ls` in die Nachprüfung aufnehmen.
* **I-1 (INFO):** Die Ausgabe von `up` schreibt ohne Umlaute („Verschluesselung“); das entspricht dem Stil der Skripte, nicht der DoD-Schreibweise. Kein Befund.
* **I-2 (INFO):** `TESTSERVER_IMAGE` und `TESTSERVER_NAME` sind per Umgebung steuerbar, stehen aber nicht in der Kurzhilfe von `make help`; die DoD verlangt „Image-Tag über Make-Variablen“, und das trägt (make reicht Kommandozeilenvariablen als Umgebung weiter; Selbsttest nutzt es). Die Auffindbarkeit steht im Kopf von `tools/testserver.sh`.

## Verdikt

Die Umsetzung hält DoD §2 (Liefer-Punkte 1 bis 3, Zeile, `make help`), den Plan §3, [ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md), [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md) und die Hard Rules. Der Umgang mit dem Review-Report ist wie behauptet. F-9 bleibt als Grenze im Kopf. Die Behauptung „gates und Selbsttest grün“ ist hier nicht reproduziert und gehört mit Beleg in die Closure-Notiz (B-1). Closure-Notiz, Register, Risiko-Ausgänge und der Lauf auf `main` mit Last sind die offenen Closure-Pflichten, kein Befund.
