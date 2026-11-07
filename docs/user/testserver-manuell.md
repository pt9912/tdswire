# Testserver von Hand starten und ausprobieren

Diese Anleitung führt durch die Aufgaben eines Menschen, der den Testserver der Bibliothek von Hand bedient: starten und stoppen, einen Client anmelden, mit d-migrate Struktur lesen und ändern, das Log lesen, den Server anders einstellen. Jede Aufgabe hat Ziel, Voraussetzungen, Schritte, erwartetes Ergebnis und typische Fehler; die Ausgaben stammen aus Läufen am gebauten Stand (Datum und Bedingung bei jeder Aufgabe). Einstellungen, Beispiel-Tabellen und Grenzen stehen am Ende. Bezug: [TDS-FA-09](../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-05](../../spec/lastenheft.md#tds-qa-05--wartbarkeit).

Der Testserver ist **kein Produkt**: er ist ein Serverprozess zur Prüfung der Bibliothek, das Image `tdswire-testserver:lokal` ist ein lokales Test-Image ohne Registry ([ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md), Entscheidungen 2 und 4). Wer die Bibliothek in ein eigenes Programm einbettet, liest [Die Bibliothek einbetten und betreiben](einbettung-und-betrieb.md).

## Aufgaben

| Ich möchte … | Abschnitt |
|---|---|
| den Testserver starten, prüfen und stoppen | [1](#1-den-testserver-starten-prüfen-und-stoppen) |
| mich mit einem Client anmelden | [2](#2-mit-einem-client-anmelden) |
| mit d-migrate Struktur lesen und ändern | [3](#3-mit-d-migrate-struktur-lesen-und-ändern) |
| das Log lesen | [4](#4-das-log-lesen) |
| den Server anders einstellen (Host, Tabellen, Benutzer, Verschlüsselung) | [5](#5-den-server-anders-einstellen) |

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
testserver-up: bereit auf 127.0.0.1:1433 (Container tdswire-testserver, Image tdswire-testserver:lokal)
testserver-up: Benutzer alice, Kennwort secret
testserver-up: Testwerte, keine Verschluesselung (TDS 7.4 im Klartext)
testserver-up: Stop mit 'make testserver-down', Log mit 'make testserver-logs'
testserver-status: laeuft (tdswire-testserver, 127.0.0.1:1433)
testserver-down: Container tdswire-testserver gestoppt und entfernt
```

`make testserver-up` baut das Image (der erste Start braucht Zeit), startet den Container im Hintergrund und kehrt zurück, sobald das Log `tdswire: gestartet, Port 1433` zeigt. `make testserver-status` endet mit Exit 0, wenn der Container läuft; sonst meldet es `testserver-status: nicht gestartet` und endet mit einem Fehler (Make-Exit 2). `make testserver-logs` zeigt das Log des Containers (`TESTSERVER_FOLGEN=1` verfolgt es). `make testserver-down` stoppt mit `docker stop` und entfernt den Container; ohne Container ist es ein Erfolg ohne Wirkung. Ohne laufende Anfrage endet der Server sofort; läuft eine Anfrage, wartet er bis `SHUTDOWN_GRACE` (Voreinstellung 10 s) plus `CANCEL_TIMEOUT` (5 s), und `docker stop` wartet nach Docker-Voreinstellung 10 s bis SIGKILL; dieser Vergleich ist nicht gemessen. Die Tabellen liegen im Arbeitsspeicher: ein Neustart des Containers stellt den Anfangsstand aus der Beschreibung wieder her (gemessen am 2026-11-07: `schema reverse` nach `schema migrate` und Neustart zeigt wieder nur `kunden` und `bestellungen`).

**Typische Fehler.**

| Beobachtung | Ursache | Abhilfe |
|---|---|---|
| `make testserver-up` bricht ab und nennt `make testserver-down` | es besteht schon ein Container | `make testserver-down` |
| Port belegt | ein anderer Prozess nutzt 1433 | `TESTSERVER_PORT=14333 make testserver-up` (der Container-Port ist immer 1433; die Variable wählt den Port auf dem Host) |
| „der Server wurde nicht bereit“ | die Wartezeit (60 s) reichte nicht, oder die Konfiguration ist ungültig (etwa `REQUIRED` mit der Voreinstellungsliste, [Abschnitt 5](#5-den-server-anders-einstellen)) | `make testserver-logs`; `TESTSERVER_WARTEN=<Sekunden>` |
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

**Erwartetes Ergebnis** (Lauf vom 2026-11-07, Exit 0): `laufzeit-client: Login ok`. Der Client liest `TDSWIRE_BENUTZER` und `TDSWIRE_PASSWORT` (Voreinstellung `alice`, `secret`); mit `-e TDSWIRE_PASSWORT=falsch` meldet er `laufzeit-client: Login fehlgeschlagen` und endet mit Exit 1 (Lauf vom 2026-11-05, danach folgt eine Gradle-Meldung `BUILD FAILED`). Das Log zeigt die Anmeldung als Ereignis `login_accepted` ([Aufgabe 4](#4-das-log-lesen)).

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

**Voraussetzungen.** Der Testserver läuft mit den Voreinstellungen. Docker kann das Image `ghcr.io/pt9912/d-migrate` per Digest ziehen (d-migrate 1.12.0, mssql-jdbc 13.4.0). Das Arbeitsverzeichnis ist beschreibbar.

**Schritte.** Die Befehle bilden `docker run` mit dem gepinnten Digest; die Verbindung ist `mssql://alice:secret@127.0.0.1:1433/manuell?encrypt=false`:

1. Die Struktur lesen:

   ```sh
   docker run --rm --network host --user "$(id -u):$(id -g)" -v "$PWD:/work" \
     ghcr.io/pt9912/d-migrate@sha256:308f27753036c9cc423f716b9fc4c9e68016543a8003ecd2f8282e05a2ee6491 \
     schema reverse --source 'mssql://alice:secret@127.0.0.1:1433/manuell?encrypt=false' --output /work/ist.yaml
   ```

   Ergebnis (Lauf vom 2026-11-07, Exit 0): `Schema written to /work/ist.yaml`; die Datei beschreibt `kunden` (`Id` biginteger, `Name` text mit `max_length` 100, `Eintritt` date) und `bestellungen` (`Id`, `KundenId`, `Betrag` decimal 10,2) mit Schlüssel `Id`.
2. Eine Soll-Datei schreiben: `ist.yaml` kopieren, in `kunden` die Spalte `Email` (`type: text`, `max_length: 200`, `ordinal: 4`) ergänzen und unter `tables` eine Tabelle `produkte` (`Id` biginteger `required: true`, `Bezeichnung` text `max_length` 50, `primary_key: [Id]`) hinzufügen.
3. Vergleichen (Soll gegen den Server):

   ```sh
   docker run --rm --network host --user "$(id -u):$(id -g)" -v "$PWD:/work" \
     ghcr.io/pt9912/d-migrate@sha256:308f27753036c9cc423f716b9fc4c9e68016543a8003ecd2f8282e05a2ee6491 \
     schema compare --source file:/work/soll.yaml --target 'db:mssql://alice:secret@127.0.0.1:1433/manuell?encrypt=false'
   ```

   Ergebnis (Exit 1): `Status: DIFFERENT`, `Tables removed: 1` (`produkte`), `Tables changed: 1` (`kunden`: `column Email`).
4. Das Soll anwenden:

   ```sh
   docker run --rm --network host --user "$(id -u):$(id -g)" -v "$PWD:/work" \
     ghcr.io/pt9912/d-migrate@sha256:308f27753036c9cc423f716b9fc4c9e68016543a8003ecd2f8282e05a2ee6491 \
     schema migrate --source /work/soll.yaml --target 'db:mssql://alice:secret@127.0.0.1:1433/manuell?encrypt=false' --execute --report /work/migrate.yaml
   ```

   Ergebnis (Exit 0, ohne Ausgabe auf dem Terminal): der Bericht hat `"status": "ok"`, `statementsAttempted` 2 und `transactionRolledBack` false; gesendet wurden `CREATE TABLE [produkte] (…)` und `ALTER TABLE [kunden] ADD [Email] NVARCHAR(200);`.
5. Erneut vergleichen (Schritt 3): `Status: IDENTICAL`, `No differences found.`, Exit 0.
6. Daten exportieren (die Tabellen haben keine Zeilen):

   ```sh
   docker run --rm --network host --user "$(id -u):$(id -g)" -v "$PWD:/work" \
     ghcr.io/pt9912/d-migrate@sha256:308f27753036c9cc423f716b9fc4c9e68016543a8003ecd2f8282e05a2ee6491 \
     data export --source 'mssql://alice:secret@127.0.0.1:1433/manuell?encrypt=false' --tables kunden --format json --output /work/kunden-neu.json
   ```

   Ergebnis (Exit 0): `Exported 1 table(s) (0 rows, 0.00 MB)`, die Datei enthält `[]`.

**Erwartetes Ergebnis.** `schema reverse`, `schema compare`, `schema migrate --execute` und `data export --tables` enden wie oben. Beleg-Typ: Läufe vom 2026-11-07 auf einem Host, Einzelläufe; die Reihe vom 2026-11-06 steht im [Messprotokoll zu Katalog und Strukturänderung](../messungen/katalog-und-ddl-zustand-2026-11-06.md) (Exit 0 auch für `schema reverse --include-all`). Gemessen ist das Verhalten von d-migrate gegen den **Testserver**; die Typnamen und Maße des Katalogs für `bigint`, `bit`, `time` und `datetime2` sind Setzung nach dem Handbuch des Servers, gemessen ist, dass d-migrate sie liest. Für andere Befehle von d-migrate ist keine Aussage gemacht.

**Eigenschaften des Katalogs.**

- Der Server hält Tabellen im Arbeitsspeicher und ändert sie auf `CREATE TABLE` und `ALTER TABLE … ADD`; er beantwortet die Katalogabfragen für `schema reverse`, `schema compare` und `schema migrate` aus dem jeweils aktuellen Stand ([SPEC-036](../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig), Formen K und D). Gespeichert wird die Struktur, keine Zeilen.
- Keine Transaktion: bricht `schema migrate --execute` in der Mitte ab, bleibt die erste Änderung stehen, auch wenn d-migrate `FULL_ROLLBACK_CONFIRMED` meldet.
- Identität, Standardwerte, Fremdschlüssel, Indizes und Prüfbedingungen kennt der Katalog nicht; ein Soll mit dem Typ `identifier` zeigt bei `schema compare` einen Unterschied (`identifier -> biginteger`, Exit 1).
- Eine Spalte, die ein Client mit `NVARCHAR(MAX)` anlegt, zeigt der Katalog als unbegrenzt (`max_length` -1); eine Spalte aus der Beschreibung ohne `max_length` zeigt er als 4000 Zeichen, sodass ein Soll mit `text` ohne `max_length` gegen sie einen Unterschied meldet.
- Die Schlüssel der Beispiel-Tabellen sind `BIGINT`: ein INT-Schlüssel scheitert beim Massenladen im Treiber von d-migrate, bevor Zeilen den Server erreichen ([Messprotokoll des Spikes](../messungen/callback-loesung-spike-2026-11-05.md), Messung b3).

**Typische Fehler.**

| Beobachtung | Ursache | Abhilfe |
|---|---|---|
| `Export failed: /work/<datei>` mit Exit 5 | die Ausgabedatei existiert schon und gehört einem anderen Benutzer (Lauf vom 2026-11-07) | einen neuen Dateinamen wählen |
| `Failed to read operand: Duplicate field 'columns'` | die Soll-Datei ist ungültiges YAML (etwa fehlender Zeilenumbruch beim Anhängen) | die Datei prüfen |
| `--execute requires a DB target (db:<url>)` | bei `schema migrate` fehlt das Präfix `db:` | `--target 'db:mssql://…'` |
| Exit 5 „Post-execute compare detected drift“ bei `schema migrate` | Soll und Katalog weichen nach der Änderung ab (etwa `text` ohne `max_length`) | `max_length` im Soll angeben |
| Verbindung scheitert | der Container läuft nicht, oder `--network host` fehlt | `make testserver-status` |

## 4. Das Log lesen

**Ziel.** Sie sehen, was der Server tat: Start, Anmeldungen, abgewiesene Anmeldungen, Fehler.

**Voraussetzungen.** Der Testserver läuft.

**Schritte.**

1. `make testserver-logs` (mit `TESTSERVER_FOLGEN=1` verfolgt es das Log).
2. Die Ebene wählen: `TESTSERVER_LOG_LEVEL=<ERROR|WARN|INFO|DEBUG> make testserver-up` (Voreinstellung `INFO`); der Wert wird als `LOG_LEVEL` an den Container gereicht, ein anderer Wert bricht den Start des Servers ab.

**Erwartetes Ergebnis** (Lauf vom 2026-11-07, nach einem Rohbytes-Login und den Läufen von d-migrate; Kürzungen mit `…`):

```text
{"ts":"2026-11-07T06:18:28.182461264Z","level":"INFO","event":"server_started","client_profile":"unknown","port":1433}
{"ts":"2026-11-07T06:18:28.193062997Z","level":"WARN","event":"security_weakened","client_profile":"unknown","reason":"ENCRYPTION_TEST_UNENCRYPTED"}
{"ts":"2026-11-07T06:18:28.193653233Z","level":"WARN","event":"security_weakened","client_profile":"unknown","reason":"PLAIN_TEST_MODE"}
tdswire: gestartet, Port 1433
{"ts":"2026-11-07T06:19:58.344569286Z","level":"WARN","event":"encryption_not_offered","connection_id":10,"client_profile":"unknown"}
{"ts":"2026-11-07T06:19:58.345779651Z","level":"INFO","event":"login_accepted","connection_id":10,"session_id":"8f335eae-…","client_profile":"tds-7.4:not_supported","principal":"alice"}
```

Die Warnungen `security_weakened` erscheinen, weil der Handlauf im Klartext mit Testkennwort läuft. Neben den Zeilen `tdswire: …` steht eine JSON-Zeile je Ereignis; Format, Felder und alle Ereignisarten: [Referenz der Ereignisse](referenz-ereignisse.md). Mit `LOG_LEVEL=DEBUG` erscheint im Handlauf kein weiteres Ereignis (gemessen am 2026-11-06 einmal von Hand: der Container trägt `LOG_LEVEL=DEBUG` laut `docker inspect`, am Prozess gemessen von `TestserverLogProcessTest`); `make testserver-selbsttest` prüft die Weitergabe der Ebene nicht.

**Typische Fehler.**

| Beobachtung | Ursache | Abhilfe |
|---|---|---|
| nur `tdswire: …`-Zeilen | der Container ist neu und noch ohne Ereignis | einen Client anmelden |
| Start bricht mit Exit 1 ab | `LOG_LEVEL` ist kein gültiger Wert (auch kleingeschrieben) | `ERROR`, `WARN`, `INFO` oder `DEBUG` |

## 5. Den Server anders einstellen

**Ziel.** Der Testserver läuft mit anderem Host, Port, anderen Tabellen, Benutzern oder mit Verschlüsselung.

**Voraussetzungen.** Kein Container läuft (`make testserver-down`). Die Variablen stehen im Kopf von `tools/testserver.sh`, ihre Prüfungen und Meldungen im Rumpf von `up`.

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

- **Anderer Host.** `TESTSERVER_HOST=0.0.0.0 make testserver-up` macht den Server im Netz erreichbar; `make testserver-up` warnt dann mit `WARNUNG: <Host> ist nicht Loopback; der Server ist im Netz erreichbar` (der Zweig ist im Selbsttest belegt). Wer das tut, setzt Klartext und Testkennwort dem Netz aus; ein Zugriff von einem anderen Rechner ist nicht gemessen.
- **Benutzerliste.** Eine Zeile je Benutzer, `Benutzername<TAB>Prüfwert`, UTF-8. `{plain}Kennwort` gilt nur im Testmodus, den das Skript nur mit `TEST_UNENCRYPTED` einschaltet (`AUTH_PLAIN_TESTMODE=true`); das zweite Verfahren ist `{pbkdf2-sha256}Salz$Iterationen$Hash` (Format in der [Referenz](referenz-konfiguration.md#konfigurationsfelder)). Der Start mit einer eigenen Liste endet im Selbsttest mit Exit 0 und der Meldung `Benutzerliste aus …`; ein Login mit einem Benutzer dieser Liste an einem laufenden Container ist nicht gemessen.
- **Tabellen.** Die Beschreibung ist das Schema-Modell von d-migrate in YAML ([SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends)); sie legt den Anfangsstand fest. Ohne Datei gilt `testserver/resources/manuell/sim.yaml`.
- **Verschlüsselung `REQUIRED`.** Zusätzlich nötig sind `TESTSERVER_TLS_SPEICHER` (PKCS#12-Datei) und `TESTSERVER_TLS_PASSWORT_DATEI` (Datei mit dem Kennwort des Speichers); fehlt eines, bricht `make testserver-up` mit einer Meldung ab (belegt im Selbsttest). Das Skript legt beide Dateien mit `docker cp` in den Container. Das Skript schaltet `AUTH_PLAIN_TESTMODE` mit `REQUIRED` nicht ein: `TESTSERVER_BENUTZER_DATEI` mit Prüfwerten `{pbkdf2-sha256}…` ist Pflicht, sonst endet der Start mit „der Server wurde nicht bereit“. Ein Zertifikat erzeugt diese Anleitung nicht. Gefahren am 2026-11-05 (ein Host, [Messprotokoll](../messungen/tls-d-migrate-2026-11-05.md)): Start mit `REQUIRED`, Schlüsselspeicher `gueltig.p12` und pbkdf2-Benutzerliste (600000 Iterationen) gelingt; die Anmelde-Tests des Referenzclients `Microsoft.Data.SqlClient` 7.1.1 liefen am 2026-11-05 mit 19 von 19 grün (ein Host, ein Lauf). Der Testserver bietet im TDS-7.4-Pfad höchstens TLS 1.2 an, der TDS-8.0-Pfad (`Encrypt=Strict`) TLS 1.3; d-migrate 1.12.0 meldet sich mit `encrypt=true` im Treiber-Standard an (mssql-jdbc 13.4.0 sendet unter TLS 1.3 im 7.4-Mantel sein `Finished` nicht). Nicht gemessen sind die Dateirechte der kopierten Dateien und die Annahme durch andere Clients.

**Erwartetes Ergebnis.** `make testserver-up` meldet Host, Port und Benutzer in seiner Ausgabe; die Voreinstellung der Bibliothek ist `REQUIRED` (`SPEC-010`), der Testserver setzt für den Handlauf `TEST_UNENCRYPTED` und sagt es mit `Testwerte, keine Verschluesselung`.

**Typische Fehler.**

| Beobachtung | Ursache | Abhilfe |
|---|---|---|
| Abbruch mit einer Meldung zu `REQUIRED` | Speicher oder Passwortdatei fehlt | beide Variablen setzen |
| „der Server wurde nicht bereit“ | `REQUIRED` mit der Voreinstellungsliste (`plain` ist nur im Testmodus zulässig) | `TESTSERVER_BENUTZER_DATEI` mit pbkdf2-Werten |
| Start bricht ab, Log nennt Schlüssel und Art | die Beschreibung ist ungültig (unbekannter Typ, doppelter Name, Datei über 1 MiB) | die Meldung nennt den Pfad im Dokument, Zeile und Spalte, nie den Inhalt ([SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends), Setzung) |

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

Beide Tabellen haben keine Zeilen: eine aus einer Datei geladene Tabelle liefert Metadaten und ein leeres Ergebnis; Einfügen, Ändern und Löschen enden mit `TDS_UNSUPPORTED_SQL` (auch an Tabellen, die ein Client mit `CREATE TABLE` angelegt hat). Der Testserver gibt ein Schema her, keine Daten; Daten braucht eine Zeilenquelle im Code eines Einbettenden ([Anleitung](einbettung-und-betrieb.md#3-tabellen-vorgeben-simulations-backend)).

Erkannt werden nach [SPEC-036](../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) `SELECT <Spalten | *> FROM <Tabelle> [WHERE …]`, `INSERT`, `UPDATE`, `DELETE` einfacher Form, `SELECT 1`, `SET`-Anweisungen aus der Liste, die Transaktionstexte von d-migrate, die Katalogabfragen von d-migrate und `CREATE TABLE`/`ALTER TABLE … ADD`. Verbund, Aggregat, `ORDER BY`, `TOP`, `OR`, `LIKE`, Unterabfragen und mehrere Anweisungen in einem Text enden mit `TDS_UNSUPPORTED_SQL`; dass die Liste abschließend ist, ist eine Setzung der Spezifikation (Lesart des Lastenhefts, vom Nutzer nicht bestätigt), nicht am Server gemessen. Für die Beispiel-Tabellen gilt nach der Spezifikation:

```sql
SELECT Id, Name FROM kunden                     -- zwei Spalten, keine Zeile
SELECT * FROM bestellungen WHERE KundenId = 1   -- drei Spalten, keine Zeile
INSERT INTO kunden (Id, Name) VALUES (1, N'A')  -- TDS_UNSUPPORTED_SQL
```

Belegt ist das so weit: der Test `Leeres_Ergebnis_hat_Metadaten_und_keine_Zeile` (`make simulations-integration`, Microsoft.Data.SqlClient) liest `SELECT Id, Name FROM leer` aus einer Dateitabelle des Integrationslaufs (zwei Spalten, keine Zeile), und der Test `ohne Callback endet die Anweisung mit TDS_UNSUPPORTED_SQL` (`make test`) belegt die Abweisung von `INSERT` auf Backend-Ebene. Beides gegen den handgestarteten Container mit diesen Tabellen ist nicht gemessen.

## Referenz: Grenzen

- Kein Produkt: kein Betrieb, keine Registry, keine Zusage für andere Rechner oder Netze ([ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md)).
- Keine Daten: die Tabellen sind leer.
- Keine Windows-Authentifizierung, kein Kerberos, kein Entra ID ([Lastenheft](../../spec/lastenheft.md)); angemeldet wird mit Benutzer und Kennwort aus der Liste.
- Keine Lastaussage: unter Last, mit vielen Verbindungen oder langer Laufzeit ist der Container nicht gemessen.
- Die Testwerte (`alice`/`secret`, Klartext) gehören nicht zu Daten, die Schutz brauchen; ein Host außer Loopback macht den Server im Netz erreichbar.
- Cancel einer laufenden Anfrage mit Folgeabfrage ist am Referenzclient im Integrationslauf (`make simulations-integration`, TDS 7.4 ohne Verschlüsselung und ohne Pool) belegt; im Handlauf nicht gemessen.
- Gegenstand der Messungen: d-migrate 1.12.0 (Image `ghcr.io/pt9912/d-migrate`, Digest `sha256:308f27753036c9cc423f716b9fc4c9e68016543a8003ecd2f8282e05a2ee6491`, mssql-jdbc 13.4.0) meldet sich mit `encrypt=false` an, der Verbindungstest des Pools läuft, und `data export --tables kunden` (auch `bestellungen`; JSON, CSV, YAML) endet mit Exit 0 und einem Ergebnis ohne Zeilen ([Messprotokoll](../messungen/d-migrate-verbindung-und-export-2026-11-05.md), Läufe einzeln, Last 14 bis 21).
- Nicht gemessen: Dateirechte nach `docker cp` (Benutzerliste, Beschreibung, Schlüsselspeicher) im Container; Abfragen gegen die Beispiel-Tabellen durch irgendeinen Client am handgestarteten Container; Zugriff von einem anderen Rechner.
