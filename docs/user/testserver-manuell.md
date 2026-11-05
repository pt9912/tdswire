# Testserver von Hand starten und ausprobieren

Diese Anleitung beschreibt, wie ein Mensch den Testserver mit `make testserver-up` startet, einen eigenen Client anmeldet und mit `make testserver-down` stoppt. Sie sagt bei jeder Aussage über das Verhalten des Servers, ob sie gemessen ist; was nicht gemessen ist, steht in [Grenzen und nicht Belegtes](#grenzen-und-nicht-belegtes).

Der Testserver ist **kein Produkt**: Er ist ein Serverprozess zur Prüfung der Bibliothek, das Image `tdswire-testserver:lokal` ist ein lokales Test-Image ohne Registry ([ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md), Entscheidungen 2 und 4). Bezug: [TDS-FA-09](../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-05](../../spec/lastenheft.md#tds-qa-05--wartbarkeit).

## Voraussetzungen

Docker und GNU `make`, sonst nichts auf dem Host (Docker-only). Alle Befehle laufen im Wurzelverzeichnis des Repos. Der erste Start baut das Image und braucht Zeit; mit gebautem Image im Cache kehrte `make testserver-up` am 2026-11-05 nach 3,6 s zurück (Einzelmessung, Last des Rechners nicht erhoben).

## Befehle

| Befehl | Wirkung |
|---|---|
| `make testserver-up` | baut das Image, startet den Container im Hintergrund, kehrt zurück, sobald das Log `tdswire: gestartet, Port 1433` zeigt |
| `make testserver-status` | `laeuft (Container, Host:Port)` mit Exit 0, sonst `nicht gestartet` und ein Fehler |
| `make testserver-logs` | Log des Containers; mit `TESTSERVER_FOLGEN=1` verfolgt es das Log |
| `make testserver-down` | stoppt und entfernt den Container; ohne Container ein Erfolg ohne Wirkung |

Ein zweites `make testserver-up` bei bestehendem Container bricht ab und nennt `make testserver-down`. Die Targets sind kein Gate und laufen nicht in `make gates` ([ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md)); ihre Zweige prüft `make testserver-selbsttest`.

## Host, Port, Benutzer, Verschlüsselung

| Einstellung | Voreinstellung | Ändern mit |
|---|---|---|
| Host | `127.0.0.1` (Loopback: nur dieser Rechner erreicht den Server) | `TESTSERVER_HOST=<Adresse>` |
| Port | `1433` | `TESTSERVER_PORT=<Port>` |
| Benutzer, Kennwort | `alice`, `secret` | `TESTSERVER_BENUTZER_DATEI=<Datei>` |
| Verschlüsselung | `TEST_UNENCRYPTED`: TDS 7.4 im Klartext, nur im Testmodus | `TESTSERVER_VERSCHLUESSELUNG=REQUIRED` mit Schlüsselspeicher |
| Tabellen | die zwei Beispiel-Tabellen unten | `TESTSERVER_BESCHREIBUNG=<Datei>` |
| Container, Image | `tdswire-testserver`, `tdswire-testserver:lokal` | `TESTSERVER_NAME=`, `TESTSERVER_IMAGE=` |
| Image bauen | ja | `TESTSERVER_BAUEN=0` baut nicht (Image muss vorhanden sein) |
| Wartezeit bis zur Bereitschaft | 60 Sekunden | `TESTSERVER_WARTEN=<Sekunden>` |

Die Variablen stehen im Kopf von `tools/testserver.sh`, ihre Prüfungen und Meldungen im Rumpf von `up`. Die Voreinstellung der Bibliothek ist `REQUIRED` (`SPEC-010`, [Abschnitt Defaults](../../spec/spezifikation.md#3-defaults-und-konstanten)); der Testserver setzt für den Handlauf `TEST_UNENCRYPTED`, und `make testserver-up` sagt es in seiner Ausgabe: `Testwerte, keine Verschluesselung`. Das Kennwort `secret` ist ein Testwert.

**Anderer Host.** `TESTSERVER_HOST=0.0.0.0 make testserver-up` macht den Server im Netz erreichbar. `make testserver-up` warnt dann mit `WARNUNG: <Host> ist nicht Loopback; der Server ist im Netz erreichbar`; der Zweig ist im Selbsttest belegt. Ein Zugriff von einem anderen Rechner ist nicht gemessen. Wer das tut, setzt Klartext und Testkennwort dem Netz aus.

**Benutzerliste.** Eine Zeile je Benutzer, `Benutzername<TAB>Prüfwert`, UTF-8; der Prüfwert trägt eine Verfahrenskennung. `{plain}Kennwort` gilt nur im Testmodus, den das Skript nur mit `TEST_UNENCRYPTED` einschaltet (`AUTH_PLAIN_TESTMODE=true`). Das zweite Verfahren ist `{pbkdf2-sha256}Salz$Iterationen$Hash` (Salz und Hash Standard-Base64 mit Padding). Zeilenformat und Fehlerarten: `spec/spezifikation.md`, Abschnitt „Authentifizierungsadapter“ (Prüfwertformat; Setzungen, nicht gemessen). Der Start mit einer eigenen Liste endet im Selbsttest mit Exit 0 und der Meldung `Benutzerliste aus …`; ein Login mit einem Benutzer dieser Liste ist **nicht gemessen** (siehe Grenzen).

## Beispiel-Tabellen

Die Beschreibung `src/testserver/resources/manuell/sim.yaml` gilt, solange `TESTSERVER_BESCHREIBUNG` fehlt. Format: das Schema-Modell von d-migrate in YAML, gelesen nach [SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends).

| Tabelle | Spalte | Typ in der Beschreibung | Nullbar |
|---|---|---|---|
| `kunden` | `Id` | `identifier` (Int32) | nein (Schlüssel) |
| | `Name` | `text` | ja |
| | `Eintritt` | `date` | ja |
| `bestellungen` | `Id` | `identifier` (Int32) | nein (Schlüssel) |
| | `KundenId` | `integer` (Int32), `required` | nein |
| | `Betrag` | `decimal`, Precision 10, Skala 2 | ja |

Beide Tabellen haben **keine Zeilen**. Nach [SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) liefert eine aus einer Datei geladene Tabelle Metadaten und ein leeres Ergebnis; Einfügen, Ändern und Löschen enden mit `TDS_UNSUPPORTED_SQL`. Der Testserver gibt also nur ein Schema her, keine Daten.

## Anfragen

Nach [SPEC-036](../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) (vorläufig, nicht am Server gemessen) werden diese Formen erkannt: `SELECT <Spalten | *> FROM <Tabelle> [WHERE …]` sowie `INSERT`, `UPDATE`, `DELETE` einfacher Form. Dass alles andere (Verbund, Aggregat, `ORDER BY`, `TOP`, `OR`, `LIKE`, Unterabfragen, mehrere Anweisungen) mit `TDS_UNSUPPORTED_SQL` endet, ist eine Setzung der Spezifikation (die Liste der Formen gilt als abschließend; Lesart des Lastenhefts, vom Nutzer nicht bestätigt) und nicht am Server gemessen.

Für die Beispiel-Tabellen gilt nach der Spezifikation:

```sql
SELECT Id, Name FROM kunden                     -- zwei Spalten, keine Zeile
SELECT * FROM bestellungen WHERE KundenId = 1   -- drei Spalten, keine Zeile
INSERT INTO kunden (Id, Name) VALUES (1, N'A')  -- TDS_UNSUPPORTED_SQL
```

Belegt ist das so weit: Der Test `Leeres_Ergebnis_hat_Metadaten_und_keine_Zeile` (`make simulations-integration`, Microsoft.Data.SqlClient) liest `SELECT Id, Name FROM leer` aus einer Dateitabelle des Integrationslaufs: zwei Spalten, keine Zeile. Der Test `ohne Callback endet die Anweisung mit TDS_UNSUPPORTED_SQL` (`make test`) belegt die Abweisung von `INSERT` auf Backend-Ebene. Beides gegen den **handgestarteten Container mit diesen Tabellen** ist nicht gemessen.

## Beispiel 1: Rohbytes-Client des Repos (gemessen)

Der Rohbytes-Client spricht PRELOGIN und LOGIN7 mit selbst gebauten Bytes und meldet das Ergebnis. Er ist **kein** Referenzclient und stellt keine Anfragen: Belegt ist die Antwort des Servers auf diese Bytes.

```sh
TESTSERVER_PORT=14333 make testserver-up
docker build --load --target laufzeit-client -t tdswire-laufzeit-client:lokal .
docker run --rm --network host -e TDSWIRE_ZIEL=127.0.0.1:14333 tdswire-laufzeit-client:lokal
make testserver-down
```

Zusammenstellung der Ausgaben mehrerer Aufrufe (`up`, `status`, `logs`, zwei Clientläufe, `down`) vom 2026-11-05 mit Port 14333 (frei), nicht ein einzelner Lauf; Kommentare in Klammern:

```text
testserver-up: bereit auf 127.0.0.1:14333 (Container tdswire-testserver, Image tdswire-testserver:lokal)
testserver-up: Benutzer alice, Kennwort secret
testserver-up: Testwerte, keine Verschluesselung (TDS 7.4 im Klartext)
testserver-up: Stop mit 'make testserver-down', Log mit 'make testserver-logs'

testserver-status: laeuft (tdswire-testserver, 127.0.0.1:14333)
tdswire: gestartet, Port 1433                       (make testserver-logs)
laufzeit-client: Login ok                           (Exit 0)
laufzeit-client: Login fehlgeschlagen               (mit -e TDSWIRE_PASSWORT=falsch, Exit 1)
testserver-down: Container tdswire-testserver gestoppt und entfernt
```

Der Client liest `TDSWIRE_BENUTZER` und `TDSWIRE_PASSWORT` (Voreinstellung `alice`, `secret`). Der Container-Port ist immer 1433; `TESTSERVER_PORT` wählt nur den Port auf dem Host. Ein anderer Client als dieser Rohbytes-Client war bei der Erstellung der Anleitung nicht vorhanden und lief nicht gegen den handgestarteten Container.

## Beispiel 2: Microsoft.Data.SqlClient (Zeichenfolge, nicht gegen den Handlauf gemessen)

Die Zeichenfolge ist die des Integrationstests `referenztest-dotnet/Integration/SimulationBackendTests.cs`, der gegen einen Testserver mit `TEST_UNENCRYPTED` läuft (`make simulations-integration`):

```text
Server=tcp:127.0.0.1,1433;User ID=alice;Password=secret;Encrypt=false;Pooling=false;ConnectRetryCount=0;Connect Timeout=15;Command Timeout=120
```

`Encrypt=false` ist nötig, weil der Handlauf im Klartext läuft. `Command Timeout=120` stammt aus dem Test (große Ergebnisse); `Pooling=false` und `ConnectRetryCount=0` ebenso. Mit anderem Port ändert sich nur die Portzahl. Dass ein Client mit dieser Zeichenfolge den **handgestarteten Container** erreicht, ist nicht gemessen. Weitere Clients (`sqlcmd`, JDBC-Treiber) sind nicht geprüft und hier nicht beschrieben; sie bringen eigene Lizenz und Installation mit.

## Verschlüsselung `REQUIRED`

`TESTSERVER_VERSCHLUESSELUNG=REQUIRED` verlangt zusätzlich `TESTSERVER_TLS_SPEICHER` (PKCS#12-Datei) und `TESTSERVER_TLS_PASSWORT_DATEI` (Datei mit dem Kennwort des Speichers); fehlt eines, bricht `make testserver-up` mit einer Meldung ab (belegt im Selbsttest). Das Skript legt beide Dateien mit `docker cp` in den Container. **Benutzerliste bei `REQUIRED`:** Das Skript schaltet `AUTH_PLAIN_TESTMODE` mit `REQUIRED` nicht ein; die Voreinstellungsliste (`alice`, `{plain}secret`) wäre dann nach der Spezifikation ein Startabbruch (`plain` nur im Testmodus), `make testserver-up` endete mit „der Server wurde nicht bereit“. Nötig ist `TESTSERVER_BENUTZER_DATEI` mit Prüfwerten `{pbkdf2-sha256}Salz$Iterationen$Hash` statt `{plain}`. Das ist aus Skript und Spezifikation abgeleitet, im Handlauf nicht gefahren; das Skript bricht bei `REQUIRED` ohne Benutzerliste nicht früh ab. **Nicht gemessen:** ein Start im Handlauf mit `REQUIRED`, die Dateirechte der kopierten Dateien und die Annahme durch einen Client. Der TLS-Pfad ist in der Spezifikation gegen eine JDK-Gegenstelle und Rohbytes belegt, nicht gegen Microsoft.Data.SqlClient. Ein Zertifikat erzeugt diese Anleitung nicht.

## Grenzen und nicht Belegtes

Was der Testserver nicht kann:

- Kein Produkt: kein Betrieb, keine Registry, keine Zusage für andere Rechner oder Netze ([ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md)).
- Keine Daten: die Tabellen aus der Datei sind leer, Einfügen, Ändern und Löschen werden abgewiesen. Daten braucht eine Zeilenquelle im Code eines Einbettenden.
- Nur die Anfrageformen von [SPEC-036](../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig); dass die Liste abschließend ist, ist eine Setzung (nicht gemessen).
- Keine Windows-Authentifizierung, kein Kerberos, kein Entra ID (Out-of-Scope im [Lastenheft](../../spec/lastenheft.md)); angemeldet wird mit Benutzer und Kennwort aus der Liste.
- Keine Lastaussage: unter Last, mit vielen Verbindungen oder langer Laufzeit ist der Container nicht gemessen.
- Abbruch einer laufenden Anfrage (Cancel) mit Folgeabfrage am Referenzclient: im Integrationslauf nicht belegt ([CO-004](../plan/carveouts/CO-004-cancel-szenario-spec-040-fehlt.md)); im Handlauf nicht gemessen.
- Die Testwerte (`alice`/`secret`, Klartext) dürfen nicht für Daten genutzt werden, die Schutz brauchen. Ein Host außer Loopback macht den Server im Netz erreichbar.

Nicht belegt (Stand: Handlauf am 2026-11-05):

- TLS im Handlauf (`REQUIRED` mit Schlüsselspeicher und Passwortdatei).
- Ein Login mit einer eigenen Benutzerliste an einem laufenden Container.
- Die Dateirechte nach `docker cp` (Benutzerliste, Beschreibung, Schlüsselspeicher) im Container.
- Abfragen gegen die Beispiel-Tabellen durch irgendeinen Client; Microsoft.Data.SqlClient gegen den handgestarteten Container; Zugriff von einem anderen Rechner.
- Fehlerpfade von `docker cp` und der Abbruch von `make testserver-up` durch SIGINT (der Container bleibt dann stehen, der nächste Aufruf meldet es).
- Der Nachvollzug dieser Anleitung durch eine zweite Person von einem frischen Klon aus (Closure-Trigger der Welle [welle-testserver-manuell](../plan/planning/welle-testserver-manuell.md)).
