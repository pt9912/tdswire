# Testserver von Hand starten und ausprobieren

Diese Anleitung führt durch die Aufgaben eines Menschen, der den Testserver der Bibliothek von Hand bedient: starten und stoppen, einen Client anmelden, mit d-migrate Struktur lesen und ändern, das Log lesen, den Server anders einstellen (eigene Benutzer, eigene Tabellen, Verschlüsselung). Jede Aufgabe hat Ziel, Voraussetzungen, Schritte, erwartetes Ergebnis und typische Fehler; die Ausgaben stammen aus Läufen am gebauten Stand (Datum bei jeder Aufgabe). Einstellungen, Beispiel-Tabellen und Grenzen stehen am Ende. Bezug: [TDS-FA-09](../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-05](../../spec/lastenheft.md#tds-qa-05--wartbarkeit).

Der Testserver ist **kein Produkt**: er ist ein Serverprozess zur Prüfung der Bibliothek, das Image `tdswire-testserver:lokal` ist ein lokales Test-Image ohne Registry ([ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md), Entscheidungen 2 und 4). Wer die Bibliothek in ein eigenes Programm einbettet, liest [Die Bibliothek einbetten und betreiben](einbettung-und-betrieb.md).

## Aufgaben

| Ich möchte … | Abschnitt |
|---|---|
| den Testserver starten, prüfen und stoppen | [1](#1-den-testserver-starten-prüfen-und-stoppen) |
| mich mit einem Client anmelden | [2](#2-mit-einem-client-anmelden) |
| mit d-migrate Struktur lesen und ändern | [3](#3-mit-d-migrate-struktur-lesen-und-ändern) |
| das Log lesen | [4](#4-das-log-lesen) |
| den Server anders einstellen (Host, Benutzer, Tabellen, Verschlüsselung) | [5](#5-den-server-anders-einstellen) |
| Zeilen in die Tabellen laden und wieder lesen | [6](#6-daten-laden-massenladen) |

## 1. Den Testserver starten, prüfen und stoppen

**Ziel.** Der Testserver läuft als Container auf `127.0.0.1:1433` und endet auf Befehl.

**Voraussetzungen.** Docker und GNU `make`, sonst nichts auf dem Host (Docker-only). Alle Befehle laufen im Wurzelverzeichnis des Repos. Port 1433 ist frei.

**Schritte.**

1. Starten:

   ```sh
   make testserver-up
   ```

2. Prüfen:

   ```sh
   make testserver-status
   ```

3. Stoppen:

   ```sh
   make testserver-down
   ```

**Erwartetes Ergebnis** (Lauf vom 2026-11-07, Image im Cache: 4,2 s; Einzelmessung, Last nicht erhoben):

```text
BUILDER_FLAGS="" bash tools/testserver.sh up
(Docker-Bau des Images: Zeilen `#<n> …`, bei vorhandenem Image aus dem Cache)
testserver-up: bereit auf 127.0.0.1:1433 (Container tdswire-testserver, Image tdswire-testserver:lokal)
testserver-up: Benutzer alice, Kennwort secret
testserver-up: Testwerte, keine Verschluesselung (TDS 7.4 im Klartext)
testserver-up: Stop mit 'make testserver-down', Log mit 'make testserver-logs'
bash tools/testserver.sh status
testserver-status: laeuft (tdswire-testserver, 127.0.0.1:1433)
bash tools/testserver.sh down
testserver-down: Container tdswire-testserver gestoppt und entfernt
```

`make testserver-up` baut das Image (der erste Start braucht Zeit), startet den Container im Hintergrund und kehrt zurück, sobald das Log `tdswire: gestartet, Port 1433` zeigt. `make testserver-status` endet mit Exit 0, wenn der Container läuft; sonst meldet es `testserver-status: nicht gestartet` und endet mit einem Fehler (Make-Exit 2). `make testserver-logs` zeigt das Log des Containers (`TESTSERVER_FOLGEN=1` verfolgt es). `make testserver-down` stoppt mit `docker stop` und entfernt den Container; ohne Container ist es ein Erfolg ohne Wirkung. Ohne laufende Anfrage endet der Server sofort; läuft eine Anfrage, wartet er bis `SHUTDOWN_GRACE` (Voreinstellung 10 s) plus `CANCEL_TIMEOUT` (5 s), und `docker stop` wartet nach Docker-Voreinstellung 10 s bis SIGKILL; dieser Vergleich ist nicht gemessen. Die Tabellen liegen im Arbeitsspeicher: ein Neustart des Containers stellt den Anfangsstand aus der Beschreibung wieder her (gemessen am 2026-11-07: `schema reverse` nach `schema migrate` und Neustart zeigt wieder nur `kunden` und `bestellungen`).

**Typische Fehler.**

| Beobachtung | Ursache | Abhilfe |
|---|---|---|
| `make testserver-up` bricht ab und nennt `make testserver-down` | es besteht schon ein Container | `make testserver-down` |
| `testserver-up: Start fehlgeschlagen (Port 14335 auf 127.0.0.1 belegt?)`, danach `Error response from daemon: … failed to bind host port 127.0.0.1:14335/tcp: address already in use` und Make-Fehler (Lauf vom 2026-11-07 mit belegtem Port 14335) | ein anderer Prozess nutzt den Port auf dem Host | einen freien Port wählen: `TESTSERVER_PORT=14333 make testserver-up` (der Container-Port ist immer 1433; die Variable wählt den Port auf dem Host) |
| `testserver-up: der Server wurde nicht bereit; Log:` mit Zeilen `tdswire: …` und Make-Fehler | die Wartezeit (60 s) reichte nicht, oder die Konfiguration ist ungültig (Beispiele in [Aufgabe 5](#5-den-server-anders-einstellen)) | die Zeilen `tdswire: …` lesen; `TESTSERVER_WARTEN=<Sekunden>` |
| der Container bleibt nach Abbruch von `make testserver-up` mit SIGINT stehen | der Abbruch räumt nicht auf | der nächste Aufruf meldet es; `make testserver-down` |

Die Targets sind kein Gate und laufen nicht in `make gates` ([ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md)); ihre Zweige prüft `make testserver-selbsttest`.

## 2. Mit einem Client anmelden

**Ziel.** Ein Client meldet sich mit `alice` und `secret` am laufenden Testserver an.

**Voraussetzungen.** Der Testserver läuft ([Aufgabe 1](#1-den-testserver-starten-prüfen-und-stoppen)).

**Schritte mit dem Rohbytes-Client des Repos.** Er spricht PRELOGIN und LOGIN7 mit selbst gebauten Bytes und meldet das Ergebnis; er ist **kein** Referenzclient und stellt keine Anfragen.

```sh
make testserver-up
docker build --load --target laufzeit-client -t tdswire-laufzeit-client:lokal .
docker run --rm --network host -e TDSWIRE_ZIEL=127.0.0.1:1433 tdswire-laufzeit-client:lokal
make testserver-down
```

**Erwartetes Ergebnis** (Lauf vom 2026-11-07, Exit 0): `laufzeit-client: Login ok`. Der Client liest `TDSWIRE_BENUTZER` und `TDSWIRE_PASSWORT` (Voreinstellung `alice`, `secret`); mit einem falschen Kennwort meldet er `laufzeit-client: Login fehlgeschlagen` und endet mit Exit 1 (gefahren am 2026-11-07, danach folgt eine Gradle-Meldung `BUILD FAILED`). Das Log zeigt die Anmeldung als Ereignis `login_accepted`, die Ablehnung als `login_rejected` mit `reason` ([Aufgabe 4](#4-das-log-lesen)).

**Schritte mit Microsoft.Data.SqlClient.** Die Zeichenfolge ist die des Integrationstests `referenztest-dotnet/Integration/SimulationBackendTests.cs`, der gegen einen Testserver mit `TEST_UNENCRYPTED` läuft (`make simulations-integration`):

```text
Server=tcp:127.0.0.1,1433;User ID=alice;Password=secret;Encrypt=false;Pooling=false;ConnectRetryCount=0;Connect Timeout=15;Command Timeout=120
```

`Encrypt=false` ist nötig, weil der Handlauf im Klartext läuft; mit anderem Port ändert sich nur die Portzahl. Beleg-Typ: der Integrationstest läuft am Referenzclient gegen einen Testserver desselben Aufbaus; dass ein Client mit dieser Zeichenfolge den **handgestarteten Container** erreicht, ist nicht gemessen. Weitere Clients (`sqlcmd`, JDBC-Treiber) sind hier nicht beschrieben.

**Typische Fehler.**

| Beobachtung | Ursache | Abhilfe |
|---|---|---|
| `Login fehlgeschlagen`, im Log `login_rejected` | falsches Kennwort oder unbekannter Benutzer (beide ergeben dieselbe Antwort) | `reason` im Log nennt den Grund |
| der Client verlangt Verschlüsselung | der Handlauf läuft im Klartext | `Encrypt=false`; oder `REQUIRED` ([Aufgabe 5](#5-den-server-anders-einstellen)) |

## 3. Mit d-migrate Struktur lesen und ändern

**Ziel.** d-migrate liest die Struktur des Testservers, vergleicht sie mit einem Soll und legt Tabellen und Spalten an.

**Voraussetzungen.** Der Testserver läuft mit den Voreinstellungen. Docker kann das Image `ghcr.io/pt9912/d-migrate` per Digest ziehen (d-migrate 1.12.0, mssql-jdbc 13.4.0). Das Verzeichnis `probe` im Wurzelverzeichnis des Repos (`mkdir -p probe`; `.gitignore` führt es) nimmt die Dateien der Schritte auf; es enthält keine Datei namens `soll.yaml`, `ist.yaml` oder `kunden.json`. Die Befehle verwenden den Digest `sha256:308f27753036c9cc423f716b9fc4c9e68016543a8003ecd2f8282e05a2ee6491`; die Verbindung ist `mssql://alice:secret@127.0.0.1:1433/manuell?encrypt=false`.

**Schritte.**

1. Die Struktur lesen:

   ```sh
   docker run --rm --network host --user "$(id -u):$(id -g)" -v "$PWD/probe:/work" \
     ghcr.io/pt9912/d-migrate@sha256:308f27753036c9cc423f716b9fc4c9e68016543a8003ecd2f8282e05a2ee6491 \
     schema reverse --source 'mssql://alice:secret@127.0.0.1:1433/manuell?encrypt=false' --output /work/ist.yaml
   ```

   Ergebnis (Lauf vom 2026-11-07, Exit 0): die Ausgabe

   ```text
   Schema written to /work/ist.yaml
   Report written to /work/ist.report.yaml
   ```

   und die Dateien `probe/ist.yaml` (Schema: `kunden` mit `Id` biginteger, `Name` text mit `max_length` 100, `Eintritt` date; `bestellungen` mit `Id`, `KundenId`, `Betrag` decimal 10,2; Schlüssel `Id`) und `probe/ist.report.yaml` (Bericht mit `notes: 0`, `warnings: 0`).
2. Das Soll schreiben (`/work` im Container ist `probe` auf dem Host): die Beschreibung der Beispiel-Tabellen, ergänzt um die Spalte `Email` in `kunden` und die neue Tabelle `produkte`. Die Datei `probe/soll.yaml`:

   ```yaml
   schema_format: "1.0"
   name: manuell
   version: "1"
   tables:
     kunden:
       columns:
         Id:
           type: biginteger
           required: true
         Name:
           type: text
           max_length: 100
         Eintritt:
           type: date
         Email:
           type: text
           max_length: 200
       primary_key: [Id]
     bestellungen:
       columns:
         Id:
           type: biginteger
           required: true
         KundenId:
           type: biginteger
           required: true
         Betrag:
           type: decimal
           precision: 10
           scale: 2
       primary_key: [Id]
     produkte:
       columns:
         Id:
           type: biginteger
           required: true
         Bezeichnung:
           type: text
           max_length: 50
       primary_key: [Id]
   ```

3. Vergleichen (Soll gegen den Server):

   ```sh
   docker run --rm --network host --user "$(id -u):$(id -g)" -v "$PWD/probe:/work" \
     ghcr.io/pt9912/d-migrate@sha256:308f27753036c9cc423f716b9fc4c9e68016543a8003ecd2f8282e05a2ee6491 \
     schema compare --source file:/work/soll.yaml --target 'db:mssql://alice:secret@127.0.0.1:1433/manuell?encrypt=false'
   ```

   Ergebnis (Exit 1): `Status: DIFFERENT`, `Tables removed: 1` (`produkte`), `Tables changed: 1` (`kunden`: `column Email`).
4. Das Soll anwenden:

   ```sh
   docker run --rm --network host --user "$(id -u):$(id -g)" -v "$PWD/probe:/work" \
     ghcr.io/pt9912/d-migrate@sha256:308f27753036c9cc423f716b9fc4c9e68016543a8003ecd2f8282e05a2ee6491 \
     schema migrate --source /work/soll.yaml --target 'db:mssql://alice:secret@127.0.0.1:1433/manuell?encrypt=false' --execute --report /work/migrate.yaml
   ```

   Ergebnis (Exit 0, ohne Ausgabe auf dem Terminal): der Bericht `migrate.yaml` hat `"status": "ok"`, `statementsAttempted` 2 und `transactionRolledBack` false; gesendet wurden `CREATE TABLE [produkte] (…)` und `ALTER TABLE [kunden] ADD [Email] NVARCHAR(200);`.
5. Erneut vergleichen (Schritt 3): `Status: IDENTICAL`, `No differences found.`, Exit 0.
6. Daten exportieren (die Tabellen haben keine Zeilen):

   ```sh
   docker run --rm --network host --user "$(id -u):$(id -g)" -v "$PWD/probe:/work" \
     ghcr.io/pt9912/d-migrate@sha256:308f27753036c9cc423f716b9fc4c9e68016543a8003ecd2f8282e05a2ee6491 \
     data export --source 'mssql://alice:secret@127.0.0.1:1433/manuell?encrypt=false' --tables kunden --format json --output /work/kunden.json
   ```

   Ergebnis (Exit 0): die letzten Zeilen lauten `Exported table 'kunden' | 0 rows | 1 chunks | 0.00 MB` und `Exported 1 table(s) (0 rows, 0.00 MB) in <Zeit> s`; die Datei `kunden.json` enthält `[]`, solange nichts geladen ist ([Aufgabe 6](#6-daten-laden-massenladen)).

**Erwartetes Ergebnis.** `schema reverse`, `schema compare`, `schema migrate --execute` und `data export --tables` enden wie oben. Beleg-Typ: Läufe vom 2026-11-07 auf einem Host, Einzelläufe; die Reihe vom 2026-11-06 steht im [Messprotokoll zu Katalog und Strukturänderung](../messungen/katalog-und-ddl-zustand-2026-11-06.md) (Exit 0 auch für `schema reverse --include-all`). Gemessen ist das Verhalten von d-migrate gegen den **Testserver**; die Typnamen und Maße des Katalogs für `bigint`, `bit`, `time` und `datetime2` sind Setzung nach dem Handbuch des Servers, gemessen ist, dass d-migrate sie liest. Für andere Befehle von d-migrate ist keine Aussage gemacht.

**Eigenschaften des Katalogs.**

- Der Server hält Tabellen im Arbeitsspeicher und ändert sie auf `CREATE TABLE` und `ALTER TABLE … ADD`; er beantwortet die Katalogabfragen für `schema reverse`, `schema compare` und `schema migrate` aus dem jeweils aktuellen Stand ([SPEC-036](../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig), Formen K und D). Der Katalog beschreibt die Struktur; Zeilen kommen über das Massenladen ([Aufgabe 6](#6-daten-laden-massenladen)).
- Keine Transaktion: bricht `schema migrate --execute` in der Mitte ab, bleibt die erste Änderung stehen, auch wenn d-migrate `FULL_ROLLBACK_CONFIRMED` meldet.
- Identität, Standardwerte, Fremdschlüssel, Indizes und Prüfbedingungen kennt der Katalog nicht; ein Soll mit dem Typ `identifier` zeigt bei `schema compare` einen Unterschied (`identifier -> biginteger`, Exit 1).
- Eine Spalte, die ein Client mit `NVARCHAR(MAX)` anlegt, zeigt der Katalog als unbegrenzt (`max_length` -1); eine Spalte aus der Beschreibung ohne `max_length` zeigt er als 4000 Zeichen, sodass ein Soll mit `text` ohne `max_length` gegen sie einen Unterschied meldet.
- Die Schlüssel der Beispiel-Tabellen sind `BIGINT`: ein INT-Schlüssel scheitert beim Massenladen im Treiber von d-migrate, bevor Zeilen den Server erreichen ([Messprotokoll zur Callback-Lösung](../messungen/callback-loesung-spike-2026-11-05.md), Messung b3).

**Typische Fehler.**

| Beobachtung | Ursache | Abhilfe |
|---|---|---|
| `Export failed: /work/<datei>` mit Exit 5 | die Ausgabedatei existiert schon und gehört einem anderen Benutzer (Lauf vom 2026-11-07) | einen neuen Dateinamen wählen |
| `Failed to read operand: Duplicate field 'columns'` | die Soll-Datei ist ungültiges YAML (etwa fehlender Zeilenumbruch beim Anhängen an `ist.yaml`) | die Datei prüfen |
| `--execute requires a DB target (db:<url>)` | bei `schema migrate` fehlt das Präfix `db:` | `--target 'db:mssql://…'` |
| Exit 5 „Post-execute compare detected drift“ bei `schema migrate` | Soll und Katalog weichen nach der Änderung ab (etwa `text` ohne `max_length`) | `max_length` im Soll angeben |
| Verbindung scheitert | der Container läuft nicht, oder `--network host` fehlt | `make testserver-status` |

## 4. Das Log lesen

**Ziel.** Sie sehen, was der Server tat: Konfiguration, Start, Verbindungen, Anmeldungen, abgewiesene Anmeldungen, Fehler, Beenden.

**Voraussetzungen.** Der Testserver läuft.

**Schritte.**

1. `make testserver-logs` (mit `TESTSERVER_FOLGEN=1` verfolgt es das Log).
2. Die Ebene wählen: `TESTSERVER_LOG_LEVEL=<ERROR|WARN|INFO|DEBUG> make testserver-up` (Voreinstellung `INFO`); der Wert wird als `LOG_LEVEL` an den Container gereicht.

**Erwartetes Ergebnis** (Lauf vom 2026-11-07, nach Anmeldungen mit d-migrate; Kürzungen mit `…`):

```text
{"ts":"2026-11-07T12:56:44.744134059Z","level":"INFO","event":"config_effective","client_profile":"unknown","login_timeout":15,"max_prelogin_message_bytes":65536,"encryption_policy":"TEST_UNENCRYPTED","tls_keystore_file":"UNSET","auth_plain_testmode":"true","query_timeout":30,"cancel_timeout":5,"shutdown_grace":10,"max_connections":1000,"max_prepared_handles":1000,"max_message_bytes":16777216}
{"ts":"2026-11-07T12:56:44.762102338Z","level":"INFO","event":"server_started","client_profile":"unknown","port":1433,"address":"0.0.0.0"}
{"ts":"2026-11-07T12:56:44.763086976Z","level":"WARN","event":"security_weakened","client_profile":"unknown","reason":"ENCRYPTION_TEST_UNENCRYPTED"}
{"ts":"2026-11-07T12:56:44.763668569Z","level":"WARN","event":"security_weakened","client_profile":"unknown","reason":"PLAIN_TEST_MODE"}
tdswire: gestartet, Port 1433
{"ts":"2026-11-07T12:56:53.946171844Z","level":"INFO","event":"connection_opened","connection_id":9,"client_profile":"unknown"}
{"ts":"2026-11-07T12:56:53.948987426Z","level":"WARN","event":"encryption_not_offered","connection_id":9,"client_profile":"unknown"}
{"ts":"2026-11-07T12:56:53.952348400Z","level":"INFO","event":"login_accepted","connection_id":9,"session_id":"40a466a5-…","client_profile":"tds-7.4:not_supported","principal":"alice"}
{"ts":"2026-11-07T12:56:54.089799174Z","level":"INFO","event":"connection_closed","connection_id":9,"client_profile":"unknown","reason":"HANDLER_RETURNED"}
```

Beim Stoppen mit `docker stop` folgen (gefahren am 2026-11-07):

```text
{"ts":"2026-11-07T13:00:27.486222870Z","level":"INFO","event":"server_stopping","client_profile":"unknown"}
{"ts":"2026-11-07T13:00:27.502125111Z","level":"INFO","event":"server_stopped","client_profile":"unknown"}
tdswire: gestoppt
```

Lesart der Zeilen:

- `config_effective` steht vorn und nennt die wirksamen Einstellungen (ein Feld je Schlüssel; der Schlüsselspeicher nur als `SET` oder `UNSET`, nie als Pfad); `server_started` nennt Adresse (im Container `0.0.0.0`) und Port.
- `security_weakened` erscheint zweimal, weil der Handlauf im Klartext (`ENCRYPTION_TEST_UNENCRYPTED`) und mit Testkennwort (`PLAIN_TEST_MODE`) läuft.
- `connection_opened` und `connection_closed` rahmen jede Verbindung; `reason` nennt die Art des Endes (`HANDLER_RETURNED`: der Handler ist zurückgekehrt, auch nach dem Trennen durch den Client). `connection_closed` trägt keine Sitzung und das Profil `unknown`.
- `encryption_not_offered` ist eine Warnung: der Client hat im PRELOGIN keine Verschlüsselung angeboten; die Verbindung läuft im Klartext weiter, die Anmeldung folgt in der nächsten Zeile (`login_accepted`, `principal` ist der Benutzer).
- Eine abgelehnte Anmeldung erscheint als `login_rejected` mit `reason`, etwa `WRONG_PASSWORD` (gefahren am 2026-11-07 mit dem Rohbytes-Client und falschem Kennwort); die Gründe stehen in der [Anleitung zur Fehlersuche](einbettung-und-betrieb.md#6-eine-abgelehnte-anmeldung-suchen).

Format, Felder und alle Ereignisarten: [Referenz der Ereignisse](referenz-ereignisse.md). Mit `LOG_LEVEL=DEBUG` erscheint zusätzlich je Verbindung eine Zeile `span_ended` (gemessen am 2026-11-07 im Handlauf: `"event":"span_ended",…,"span":"tds.connection","outcome":"ok",…,"phase":"session"`, und am Prozess von `TestserverLogProcessTest`); alle 60 Sekunden folgt eine Zeile `metrics` (Ebene `INFO`, auch ohne `DEBUG`; Beispiel in der [Referenz](referenz-ereignisse.md#log-des-testservers)). `make testserver-selbsttest` prüft die Weitergabe der Ebene nicht.

**Wenn der Start scheitert.** `make testserver-up` meldet `testserver-up: der Server wurde nicht bereit; Log:` mit den Zeilen des Containers und einem Make-Fehler (Exit 2), oder bricht schon vorher ab. Die Diagnose des Prozesses nennt den Grund ohne Pfad und Inhalt (gefahren am 2026-11-07):

| Ursache | Ausgabe |
|---|---|
| der Host-Port ist belegt | `testserver-up: Start fehlgeschlagen (Port 14335 auf 127.0.0.1 belegt?)` und die Docker-Meldung `failed to bind host port 127.0.0.1:14335/tcp: address already in use` (der Container bindet im Container immer 1433; hat der Prozess selbst einen belegten Port, nennt er `Port belegt: <Adresse>:<Port>` und das Ereignis `bind_failed`, am Prozess gemessen von `ZustandsereignisseProzessTest`) |
| das Zertifikat des Schlüsselspeichers ist abgelaufen | `{"level":"ERROR","event":"tls_start_rejected",…,"part":"CERTIFICATE","form":"Kette jetzt nicht gueltig"}` und `tdswire: Start gescheitert: TLS-Konfiguration: CERTIFICATE (Kette jetzt nicht gueltig)` |
| die Speicherdatei fehlt | das Skript bricht vor dem Start ab: `lstat <Pfad>/fehlt.p12: no such file or directory` und Make-Fehler |
| die Konfiguration ist unsicher (`REQUIRED` mit der Voreinstellungsliste, die `{plain}` enthält) | `tdswire: Konfiguration ungueltig: AUTH_USERS_FILE: USER_LIST_INVALID (PLAIN_NOT_ALLOWED, Zeile 1)` |
| `LOG_LEVEL` ist ungültig | `tdswire: Konfiguration ungueltig: LOG_LEVEL: INVALID_VALUE` |

**Typische Fehler.**

| Beobachtung | Ursache | Abhilfe |
|---|---|---|
| nur `config_effective`, `server_started`, `security_weakened` und `tdswire: …`-Zeilen | der Container ist neu und noch ohne Verbindung | einen Client anmelden |
| `testserver-up: der Server wurde nicht bereit; Log:` mit einer Zeile aus der Tabelle oben und Make-Fehler (`make: *** … Fehler 1`, Exit 2) | der Start scheiterte | die Zeile `tdswire: …` lesen und die Einstellung korrigieren |

## 5. Den Server anders einstellen

**Ziel.** Der Testserver läuft mit anderem Host oder Port, eigenen Benutzern, eigenen Tabellen oder mit Verschlüsselung.

**Voraussetzungen.** Kein Container läuft (`make testserver-down`). Die Variablen stehen im Kopf von `tools/testserver.sh`, ihre Prüfungen und Meldungen im Rumpf von `up`. Die Befehle laufen im Wurzelverzeichnis des Repos; Dateien liegen im Arbeitsverzeichnis.

**Schritte.** Variablen vor `make testserver-up` setzen:

| Einstellung | Voreinstellung | Ändern mit |
|---|---|---|
| Host | `127.0.0.1` (Loopback: nur dieser Rechner erreicht den Server) | `TESTSERVER_HOST=<Adresse>` |
| Port | `1433` | `TESTSERVER_PORT=<Port>` |
| Benutzer, Kennwort | `alice`, `secret` | `TESTSERVER_BENUTZER_DATEI=<Datei>` |
| Verschlüsselung | `TEST_UNENCRYPTED`: TDS 7.4 im Klartext, nur im Testmodus | `TESTSERVER_VERSCHLUESSELUNG=REQUIRED` mit Schlüsselspeicher |
| Tabellen | die zwei Beispiel-Tabellen unten | `TESTSERVER_BESCHREIBUNG=<Datei>` |
| Container, Image | `tdswire-testserver`, `tdswire-testserver:lokal` | `TESTSERVER_NAME=`, `TESTSERVER_IMAGE=` |
| Image bauen | ja | `TESTSERVER_BAUEN=0` baut nicht (Image muss vorhanden sein) |
| Log-Ebene | `INFO` | `TESTSERVER_LOG_LEVEL=DEBUG` (`ERROR`, `WARN`, `INFO`, `DEBUG`) |
| Wartezeit bis zur Bereitschaft | 60 Sekunden | `TESTSERVER_WARTEN=<Sekunden>` |
| Abstand der Schnappschüsse der Metriken | 60 Sekunden (`METRICS_INTERVAL_SECONDS`) | nicht über `make` einstellbar; am Prozess `METRICS_INTERVAL_SECONDS=<Sekunden>` (0 = aus) |

Die folgenden Abschnitte fahren je eine Einstellung durch (Läufe vom 2026-11-07, ein Host).

### Eigene Benutzer

1. Die Liste schreiben (eine Zeile je Benutzer, `Benutzername<TAB>Prüfwert`, UTF-8; `{plain}Kennwort` gilt nur im Testmodus, den das Skript nur mit `TEST_UNENCRYPTED` einschaltet):

   ```sh
   mkdir -p probe
   printf 'bob\t{plain}geheim\n' > probe/benutzer.txt
   ```

2. Starten und anmelden:

   ```sh
   TESTSERVER_BENUTZER_DATEI=probe/benutzer.txt make testserver-up
   docker run --rm --network host -e TDSWIRE_ZIEL=127.0.0.1:1433 -e TDSWIRE_BENUTZER=bob -e TDSWIRE_PASSWORT=geheim tdswire-laufzeit-client:lokal
   ```

   (Das Image `tdswire-laufzeit-client:lokal` baut [Aufgabe 2](#2-mit-einem-client-anmelden).) Ergebnis: `testserver-up: Benutzerliste aus probe/benutzer.txt` und `laufzeit-client: Login ok`; mit `-e TDSWIRE_PASSWORT=falsch` `laufzeit-client: Login fehlgeschlagen`. Das Log zeigt `login_accepted` mit `"principal":"bob"` und `login_rejected` mit `"reason":"WRONG_PASSWORD"`.

### Eigene Tabellen

1. Eine Beschreibung `probe/tabellen.yaml` schreiben (das Schema-Modell von d-migrate in YAML, [SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends)); sie legt den Anfangsstand fest. Beispiel mit einer Tabelle:

   ```yaml
   schema_format: "1.0"
   name: eigene
   version: "1"
   tables:
     artikel:
       columns:
         Nr:
           type: biginteger
           required: true
         Bezeichnung:
           type: text
           max_length: 80
       primary_key: [Nr]
   ```

2. Starten: `TESTSERVER_BESCHREIBUNG=probe/tabellen.yaml make testserver-up`. Der Katalog führt danach `artikel` statt `kunden` und `bestellungen` (`schema reverse` wie in [Aufgabe 3](#3-mit-d-migrate-struktur-lesen-und-ändern)).
3. Ist die Beschreibung ungültig, bricht der Start ab, bevor ein Port gebunden wird. Ersetzt man in `probe/tabellen.yaml` `type: biginteger` durch `type: uuid`, ergibt das, gefahren:

   ```text
   testserver-up: der Server wurde nicht bereit; Log:
   tdswire: Beschreibung der Tabellen ungueltig: tables.artikel.columns.Nr.type (Zeile 8, Spalte 15): Typ uuid ohne Entsprechung
   make: *** [tools/mk/testserver.mk:8: testserver-up] Fehler 1
   ```

   Eine Datei über 1 MiB ergibt `tdswire: Konfiguration ungueltig: SIMULATION_DESCRIPTION_FILE: TOO_LARGE` (gefahren mit einer Datei von 1100000 Byte). Die Meldung nennt je nach Fehler den Pfad im Dokument mit Zeile und Spalte oder den Schlüssel der Einstellung mit der Art; den Inhalt der Datei nennt sie nie.

### Verschlüsselung `REQUIRED`

`REQUIRED` verlangt einen Schlüsselspeicher im PKCS#12-Format, eine Datei mit seinem Kennwort und eine Benutzerliste mit `pbkdf2`-Prüfwerten (`{plain}` ist ohne Testmodus nicht zugelassen).

1. Einen Speicher erzeugen (ein selbst erzeugtes Zertifikat taugt für Tests) und das Kennwort in eine Datei legen:

   ```sh
   mkdir -p probe
   printf '%s' 'speicher-geheim' > probe/speicher-kennwort.txt
   docker run --rm --user "$(id -u):$(id -g)" -v "$PWD/probe:/work" -w /work \
     gradle:8.14-jdk21@sha256:b3453e88792ea90b526f5d04fa2961bba183ae57ded4ad3437a49fb4e8381c06 \
     keytool -genkeypair -alias server -keyalg RSA -keysize 2048 -validity 365 -storetype PKCS12 \
       -keystore server.p12 -storepass speicher-geheim -dname "CN=localhost" -ext "san=dns:localhost,ip:127.0.0.1"
   ```

   Ergebnis: die Datei `probe/server.p12`.

2. Einen Prüfwert erzeugen. Die Datei `probe/Pruefwert.java`:

   ```java
   import java.security.SecureRandom;
   import java.util.Base64;
   import javax.crypto.SecretKeyFactory;
   import javax.crypto.spec.PBEKeySpec;

   public class Pruefwert {
       public static void main(String[] args) throws Exception {
           int iterationen = 600000;
           byte[] salz = new byte[16];
           new SecureRandom().nextBytes(salz);
           PBEKeySpec spec = new PBEKeySpec(args[0].toCharArray(), salz, iterationen, 32 * 8);
           byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
           Base64.Encoder base64 = Base64.getEncoder();
           System.out.println("{pbkdf2-sha256}" + base64.encodeToString(salz) + "$" + iterationen + "$" + base64.encodeToString(hash));
       }
   }
   ```

   Der Aufruf im JDK-Image schreibt die Liste (Ergebnis: eine Zeile `bob<TAB>{pbkdf2-sha256}…$600000$…`):

   ```sh
   WERT=$(docker run --rm -v "$PWD/probe:/work" -w /work \
     gradle:8.14-jdk21@sha256:b3453e88792ea90b526f5d04fa2961bba183ae57ded4ad3437a49fb4e8381c06 java Pruefwert.java geheim)
   printf 'bob\t%s\n' "$WERT" > probe/benutzer-pbkdf2.txt
   ```

3. Starten:

   ```sh
   TESTSERVER_VERSCHLUESSELUNG=REQUIRED TESTSERVER_TLS_SPEICHER=probe/server.p12 \
     TESTSERVER_TLS_PASSWORT_DATEI=probe/speicher-kennwort.txt TESTSERVER_BENUTZER_DATEI=probe/benutzer-pbkdf2.txt make testserver-up
   ```

   Ergebnis: `testserver-up: Verschluesselung REQUIRED mit dem Schluesselspeicher aus TESTSERVER_TLS_SPEICHER`; das Skript legt Speicher, Passwortdatei und Liste mit `docker cp` in den Container. Ohne `TESTSERVER_TLS_SPEICHER` und `TESTSERVER_TLS_PASSWORT_DATEI` bricht es ab: `testserver-up: REQUIRED verlangt TESTSERVER_TLS_SPEICHER und TESTSERVER_TLS_PASSWORT_DATEI`.
4. Mit TLS anmelden, hier mit d-migrate (Bild und Digest wie in [Aufgabe 3](#3-mit-d-migrate-struktur-lesen-und-ändern)):

   ```sh
   docker run --rm --network host --user "$(id -u):$(id -g)" -v "$PWD/probe:/work" \
     ghcr.io/pt9912/d-migrate@sha256:308f27753036c9cc423f716b9fc4c9e68016543a8003ecd2f8282e05a2ee6491 \
     schema reverse --source 'mssql://bob:geheim@localhost:1433/manuell?encrypt=true&trustServerCertificate=true' --output /work/tls-ist.yaml
   ```

   Ergebnis (Exit 0): `Schema written to /work/tls-ist.yaml`; das Log zeigt `login_accepted` mit `"client_profile":"tds-7.4:required"` und `"principal":"bob"`.

Der Testserver bietet im TDS-7.4-Pfad höchstens TLS 1.2 an, der TDS-8.0-Pfad (`Encrypt=Strict`) TLS 1.3 (gemessen am 2026-11-05, [Messprotokoll](../messungen/tls-d-migrate-2026-11-05.md)); die Anmelde-Tests des Referenzclients `Microsoft.Data.SqlClient` 7.1.1 liefen dort mit 19 von 19 grün (ein Host, ein Lauf). Das Skript schaltet `AUTH_PLAIN_TESTMODE` mit `REQUIRED` nicht ein: ohne `TESTSERVER_BENUTZER_DATEI` mit `pbkdf2`-Prüfwerten endet der Start mit „der Server wurde nicht bereit“. Dateirechte der mit `docker cp` kopierten Dateien setzt das Skript nicht; eine Aussage dazu macht diese Anleitung nicht.

### Anderer Host

`TESTSERVER_HOST=0.0.0.0 make testserver-up` macht den Server im Netz erreichbar; `make testserver-up` warnt dann mit `WARNUNG: <Host> ist nicht Loopback; der Server ist im Netz erreichbar` (der Zweig ist im Selbsttest belegt). Wer das tut, setzt Klartext und Testkennwort dem Netz aus; ein Zugriff von einem anderen Rechner ist nicht gemessen.

**Typische Fehler.**

| Beobachtung | Ursache | Abhilfe |
|---|---|---|
| Abbruch mit `REQUIRED verlangt TESTSERVER_TLS_SPEICHER und TESTSERVER_TLS_PASSWORT_DATEI` | Speicher oder Passwortdatei fehlt | beide Variablen setzen |
| „der Server wurde nicht bereit“ bei `REQUIRED` | die Benutzerliste enthält `{plain}` oder fehlt | `TESTSERVER_BENUTZER_DATEI` mit pbkdf2-Werten |
| „der Server wurde nicht bereit“ mit `Beschreibung der Tabellen ungueltig` | die Beschreibung ist ungültig | Pfad, Zeile und Spalte der Meldung lesen |
| der Client bricht den TLS-Handschlag ab | der Client vertraut dem selbst erzeugten Zertifikat nicht | `trustServerCertificate=true` (nur Tests) oder ein vertrauenswürdiges Zertifikat |

## 6. Daten laden (Massenladen)

**Ziel.** Zeilen aus einer JSON-Datei kommen mit `data import` von d-migrate in den Arbeitsspeicher des Testservers, und `data export` liefert sie zurück.

**Voraussetzungen.** Der Testserver läuft mit den Voreinstellungen ([Aufgabe 1](#1-den-testserver-starten-prüfen-und-stoppen)); die Tabelle `kunden` ist leer. Docker, das Verzeichnis `probe` und der Digest von d-migrate wie in [Aufgabe 3](#3-mit-d-migrate-struktur-lesen-und-ändern).

**Schritte.**

1. Die Zeilen schreiben, `probe/kunden-ein.json`:

   ```sh
   mkdir -p probe
   printf '[{"Id":1,"Name":"Ada","Eintritt":"2020-01-31"},{"Id":2,"Name":"Bob","Eintritt":null}]\n' > probe/kunden-ein.json
   ```

2. Laden:

   ```sh
   docker run --rm --network host --user "$(id -u):$(id -g)" -v "$PWD/probe:/work" \
     ghcr.io/pt9912/d-migrate@sha256:308f27753036c9cc423f716b9fc4c9e68016543a8003ecd2f8282e05a2ee6491 \
     data import --target 'mssql://alice:secret@127.0.0.1:1433/manuell?encrypt=false' --source /work/kunden-ein.json --table kunden
   ```

   Ergebnis (Lauf vom 2026-11-07, Exit 0): die Zeilen `Importing table 'kunden' | chunk 1 | 2 rows processed, 2 inserted` und `Imported 1 table(s) (2 inserted) in <Zeit> s`.
3. Lesen:

   ```sh
   docker run --rm --network host --user "$(id -u):$(id -g)" -v "$PWD/probe:/work" \
     ghcr.io/pt9912/d-migrate@sha256:308f27753036c9cc423f716b9fc4c9e68016543a8003ecd2f8282e05a2ee6491 \
     data export --source 'mssql://alice:secret@127.0.0.1:1433/manuell?encrypt=false' --tables kunden --format json --output /work/kunden-aus.json
   ```

   Ergebnis (Exit 0): `Exported table 'kunden' | 2 rows | 1 chunks | 0.00 MB`; `probe/kunden-aus.json` enthält die beiden Zeilen (`{"Id": 1,"Name": "Ada","Eintritt": "2020-01-31"}` und `{"Id": 2,"Name": "Bob","Eintritt": null}`).
4. Dieselbe Datei noch einmal laden (Schritt 2): der Schlüssel `Id` kommt doppelt vor. Ergebnis: `Error: Import failed: Violation of PRIMARY KEY constraint. Cannot insert duplicate key.`, Exit 5; der Export zeigt unverändert die beiden Zeilen.

**Erwartetes Ergebnis.** Nach Schritt 3 liefert der Export die geladenen Zeilen; ein Neustart des Containers (`make testserver-down`, `make testserver-up`) leert den Bestand, der Export liefert wieder `[]` (gefahren am 2026-11-07). Beleg-Typ: Einzelläufe am Testserver mit d-migrate 1.12.0, ein Host; die Messungen des Massenladens stehen im [Messprotokoll](../messungen/bulk-load-2026-11-07.md).

**Eigenschaften** (Setzungen, soweit nicht anders gesagt nicht am echten SQL Server gemessen; [SPEC-036](../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) Form B, [SPEC-001](../../spec/spezifikation.md#spec-001--zustandsautomat-einer-verbindung)):

- `data import` und `data transfer` laden im Standardpfad (ohne `--on-conflict`) über das Massenladen von mssql-jdbc (`INSERT BULK`, Pakettyp `0x07`). `INSERT`, `UPDATE` und `DELETE` als SQL-Text enden an den Tabellen des Testservers mit `TDS_UNSUPPORTED_SQL`.
- Eine Nachricht des Treibers (Voreinstellung 10 000 Zeilen, `--chunk-size`) gilt als Ganzes: sie wird ganz oder gar nicht übernommen. Ein doppelter Schlüssel, ein NULL im Schlüssel oder ein Wert, der nicht zum Typ der Spalte passt, weist die ganze Nachricht ab. Der Server antwortet wie der echte SQL Server auf einen Schlüsselverstoß (Nummer 2627, Class 14; gemessen am Referenzclient mit `SqlBulkCopy`, [Messprotokoll](../messungen/referenzabgleich.md), M-12); der Text ist gekürzt, die Zuordnung jedes Fehlers des Einfügens zu 2627 ist eine Setzung. d-migrate endet mit Exit 5. Nachrichten davor bleiben im Bestand: es gibt keine Transaktion über mehrere Nachrichten.
- Grenzen (Setzungen): eine Nachricht ist höchstens 16 MiB groß (eine größere schließt die Verbindung), hat höchstens 1024 Spalten und höchstens 262 144 Zellen (Spalten mal Zeilen; eine größere schließt die Verbindung mit einem Protokollfehler), und der Bestand nimmt höchstens 2 097 152 Zellen über alle Tabellen auf; eine Nachricht darüber weist der Server wie jeden Fehler des Einfügens als 2627 mit dem Text „Violation of PRIMARY KEY constraint“ ab (der Text nennt eine Ursache, die es nicht war), statt dass der Heap endet.
- Der Bestand liegt im Heap des Containers. Bei kleinerem Heap, als die Zellen brauchen, endet der Prozess mit `OutOfMemoryError`; gemessen sind 4 bis 120 Byte je Zelle für die Zeilen einer Nachricht, der Bestand selbst ist nicht gemessen. Bei 10 000 Zeilen je Nachricht gilt die Zellengrenze je Nachricht für Tabellen bis 26 Spalten; mit mehr Spalten hilft `--chunk-size`.
- `INT`-Spalten und ganzzahlig geschriebene `DECIMAL`-Werte scheitern im Treiber von d-migrate, bevor der Server sie sieht; die Beispiel-Tabellen nutzen deshalb `BIGINT`.
- Der Referenzclient lädt anders: `SqlBulkCopy` (`Microsoft.Data.SqlClient`) beginnt mit einem Katalog-Batch, den der Server nicht kennt, und sendet nicht nullbare Spalten als feste Typen (zum Beispiel BIGINT), die der Server in der Nutzlast nicht liest; `SqlBulkCopy` erreicht den Testserver daher nicht. Die Form der Antworten des Servers auf das Massenladen ist an `SqlBulkCopy` am echten SQL Server gemessen (M-8 bis M-12), nicht an einem Lauf von `SqlBulkCopy` am Testserver.

**Typische Fehler.**

| Beobachtung | Ursache | Abhilfe |
|---|---|---|
| `Import failed: Violation of PRIMARY KEY constraint. Cannot insert duplicate key.` (Exit 5) | doppelter Schlüssel, NULL im Schlüssel, ein unpassender Wert, oder die Zellengrenze des Bestands ist erreicht (der Text nennt dann eine falsche Ursache) | die Daten prüfen; kleinere Mengen laden; Neustart leert den Bestand |
| die Verbindung wird beim Laden geschlossen | die Nachricht ist größer als 16 MiB, hat mehr als 1024 Spalten oder mehr als 262 144 Zellen | `--chunk-size` kleiner wählen |
| Prozess endet mit `OutOfMemoryError` | der Heap des Containers trägt die Zeilen nicht | weniger Zeilen laden |
| Fehler im Treiber vor dem Server (`INT`, ganzzahliger `DECIMAL`) | Spaltentyp der Beschreibung | `BIGINT` verwenden |

## Referenz: Beispiel-Tabellen und Anfragen

Die Beschreibung `testserver/resources/manuell/sim.yaml` gilt, solange `TESTSERVER_BESCHREIBUNG` fehlt:

| Tabelle | Spalte | Typ in der Beschreibung | Nullbar |
|---|---|---|---|
| `kunden` | `Id` | `biginteger` (Int64), `required` | nein (Schlüssel) |
| | `Name` | `text`, `max_length` 100 | ja |
| | `Eintritt` | `date` | ja |
| `bestellungen` | `Id` | `biginteger` (Int64), `required` | nein (Schlüssel) |
| | `KundenId` | `biginteger` (Int64), `required` | nein |
| | `Betrag` | `decimal`, Precision 10, Skala 2 | ja |

Beide Tabellen haben beim Start keine Zeilen. Zeilen kommen über das Massenladen in den Arbeitsspeicher ([Aufgabe 6](#6-daten-laden-massenladen), auch für Tabellen, die ein Client mit `CREATE TABLE` angelegt hat); `INSERT`, `UPDATE` und `DELETE` als SQL-Text enden mit `TDS_UNSUPPORTED_SQL`. Ein Neustart des Containers leert die Zeilen. Eine Zeilenquelle mit eigenen Daten und Callbacks braucht ein Einbettender im Code ([Anleitung](einbettung-und-betrieb.md#3-tabellen-vorgeben-simulations-backend)).

Erkannt werden nach [SPEC-036](../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) `SELECT <Spalten | *> FROM <Tabelle> [WHERE …]`, `INSERT`, `UPDATE`, `DELETE` einfacher Form, `SELECT 1`, `SET`-Anweisungen aus der Liste, die Transaktionstexte von d-migrate, die Katalogabfragen von d-migrate, `CREATE TABLE`/`ALTER TABLE … ADD` und `INSERT BULK` samt Nachricht `0x07` (Form B). Verbund, Aggregat, `ORDER BY`, `TOP`, `OR`, `LIKE`, Unterabfragen und mehrere Anweisungen in einem Text enden mit `TDS_UNSUPPORTED_SQL`; dass die Liste abschließend ist, ist eine Setzung der Spezifikation (Lesart des Lastenhefts, vom Nutzer nicht bestätigt), nicht am Server gemessen. Für die Beispiel-Tabellen gilt nach der Spezifikation:

```sql
SELECT Id, Name FROM kunden                     -- zwei Spalten, keine Zeile
SELECT * FROM bestellungen WHERE KundenId = 1   -- drei Spalten, keine Zeile
INSERT INTO kunden (Id, Name) VALUES (1, N'A')  -- TDS_UNSUPPORTED_SQL (Zeilen laden: Aufgabe 6)
```

Belegt ist das so weit: der Test `Leeres_Ergebnis_hat_Metadaten_und_keine_Zeile` (`make simulations-integration`, Microsoft.Data.SqlClient) liest `SELECT Id, Name FROM leer` aus einer Dateitabelle des Integrationslaufs (zwei Spalten, keine Zeile), und der Test `ohne Callback endet die Anweisung mit TDS_UNSUPPORTED_SQL` (`make test`) belegt die Abweisung von `INSERT` auf Backend-Ebene. Beides gegen den handgestarteten Container mit diesen Tabellen ist nicht gemessen.

## Referenz: Grenzen

- Kein Produkt: kein Betrieb, keine Registry, keine Zusage für andere Rechner oder Netze ([ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md)).
- Daten nur über das Massenladen ([Aufgabe 6](#6-daten-laden-massenladen)): `INSERT`, `UPDATE` und `DELETE` als SQL-Text werden abgewiesen, ein Neustart leert den Bestand.
- Keine Windows-Authentifizierung, kein Kerberos, kein Entra ID ([Lastenheft](../../spec/lastenheft.md)); angemeldet wird mit Benutzer und Kennwort aus der Liste.
- Keine Lastaussage: unter Last, mit vielen Verbindungen oder langer Laufzeit ist der Container nicht gemessen.
- Die Testwerte (`alice`/`secret`, Klartext) gehören nicht zu Daten, die Schutz brauchen; ein Host außer Loopback macht den Server im Netz erreichbar.
- Cancel einer laufenden Anfrage mit Folgeabfrage ist am Referenzclient im Integrationslauf (`make simulations-integration`, TDS 7.4 ohne Verschlüsselung und ohne Pool) belegt; im Handlauf nicht gemessen.
- Gegenstand der Messungen: d-migrate 1.12.0 (Image `ghcr.io/pt9912/d-migrate`, Digest `sha256:308f27753036c9cc423f716b9fc4c9e68016543a8003ecd2f8282e05a2ee6491`, mssql-jdbc 13.4.0) meldet sich mit `encrypt=false` an, der Verbindungstest des Pools läuft, und `data export --tables kunden` (auch `bestellungen`; JSON, CSV, YAML) endet mit Exit 0, bei leeren Tabellen mit einem Ergebnis ohne Zeilen ([Messprotokoll](../messungen/d-migrate-verbindung-und-export-2026-11-05.md), Läufe einzeln, Last 14 bis 21). `data import` im Standardpfad lädt über das Massenladen ([Aufgabe 6](#6-daten-laden-massenladen)); für `data profile` und andere Befehle ist keine Aussage gemacht.
- Nicht gemessen: Abfragen gegen die Beispiel-Tabellen durch irgendeinen Client am handgestarteten Container; Zugriff von einem anderen Rechner; die Annahme des Zertifikats durch andere Clients als d-migrate 1.12.0 und `Microsoft.Data.SqlClient` 7.1.1.
