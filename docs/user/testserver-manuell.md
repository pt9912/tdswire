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

Ein zweites `make testserver-up` bei bestehendem Container bricht ab und nennt `make testserver-down`. Die Targets sind kein Gate und laufen nicht in `make gates` ([ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md)); ihre Zweige prüft `make testserver-selbsttest`. `make testserver-down` stoppt den Container mit `docker stop`; ohne laufende Anfrage endet der Server sofort. Läuft eine Anfrage, wartet er bis `SHUTDOWN_GRACE` (Voreinstellung 10 s) plus `CANCEL_TIMEOUT` (5 s), `docker stop` wartet nach Docker-Voreinstellung 10 s bis SIGKILL; dieser Vergleich ist nicht gemessen.

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
| Log-Ebene | `INFO` (Voreinstellung des Testservers) | `TESTSERVER_LOG_LEVEL=DEBUG` (`ERROR`, `WARN`, `INFO`, `DEBUG`); ein anderer Wert bricht den Start des Servers ab |
| Wartezeit bis zur Bereitschaft | 60 Sekunden | `TESTSERVER_WARTEN=<Sekunden>` |

Die Variablen stehen im Kopf von `tools/testserver.sh`, ihre Prüfungen und Meldungen im Rumpf von `up`. Die Voreinstellung der Bibliothek ist `REQUIRED` (`SPEC-010`, [Abschnitt Defaults](../../spec/spezifikation.md#3-defaults-und-konstanten)); der Testserver setzt für den Handlauf `TEST_UNENCRYPTED`, und `make testserver-up` sagt es in seiner Ausgabe: `Testwerte, keine Verschluesselung`. Das Kennwort `secret` ist ein Testwert.

**Log-Ebene.** `make testserver-logs` zeigt neben den Zeilen `tdswire: …` eine JSON-Zeile je Ereignis; `TESTSERVER_LOG_LEVEL` wird als `LOG_LEVEL` an den Container gereicht. Format, Ebenen, Überlauf und Prozessende beschreibt der Absatz „Log des Testservers“ in [Einbettung und Betrieb](einbettung-und-betrieb.md). Gemessen am 2026-11-06 (einmal von Hand): der Container trägt `LOG_LEVEL=DEBUG` (`docker inspect`); im Handlauf erscheint dabei kein weiteres Ereignis (das Backend ist eine eigene Quelle, die Art `simulation_backend_ready` der Ebene `DEBUG` entsteht nur bei der Simulation der Bibliothek; am Prozess gemessen von `TestserverLogProcessTest` in `make test`), und `make testserver-selbsttest` prüft die Weitergabe nicht.

**Anderer Host.** `TESTSERVER_HOST=0.0.0.0 make testserver-up` macht den Server im Netz erreichbar. `make testserver-up` warnt dann mit `WARNUNG: <Host> ist nicht Loopback; der Server ist im Netz erreichbar`; der Zweig ist im Selbsttest belegt. Ein Zugriff von einem anderen Rechner ist nicht gemessen. Wer das tut, setzt Klartext und Testkennwort dem Netz aus.

**Benutzerliste.** Eine Zeile je Benutzer, `Benutzername<TAB>Prüfwert`, UTF-8; der Prüfwert trägt eine Verfahrenskennung. `{plain}Kennwort` gilt nur im Testmodus, den das Skript nur mit `TEST_UNENCRYPTED` einschaltet (`AUTH_PLAIN_TESTMODE=true`). Das zweite Verfahren ist `{pbkdf2-sha256}Salz$Iterationen$Hash` (Salz und Hash Standard-Base64 mit Padding). Zeilenformat und Fehlerarten: `spec/spezifikation.md`, Abschnitt „Authentifizierungsadapter“ (Prüfwertformat; Setzungen, nicht gemessen). Der Start mit einer eigenen Liste endet im Selbsttest mit Exit 0 und der Meldung `Benutzerliste aus …`; ein Login mit einem Benutzer dieser Liste ist **nicht gemessen** (siehe Grenzen).

## Beispiel-Tabellen

Die Beschreibung `testserver/resources/manuell/sim.yaml` gilt, solange `TESTSERVER_BESCHREIBUNG` fehlt. Format: das Schema-Modell von d-migrate in YAML, gelesen nach [SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends).

| Tabelle | Spalte | Typ in der Beschreibung | Nullbar |
|---|---|---|---|
| `kunden` | `Id` | `biginteger` (Int64), `required` | nein (Schlüssel) |
| | `Name` | `text`, `max_length` 100 | ja |
| | `Eintritt` | `date` | ja |
| `bestellungen` | `Id` | `biginteger` (Int64), `required` | nein (Schlüssel) |
| | `KundenId` | `biginteger` (Int64), `required` | nein |
| | `Betrag` | `decimal`, Precision 10, Skala 2 | ja |

Die Schlüssel sind `BIGINT`: ein INT-Schlüssel scheitert beim Massenladen im Treiber von d-migrate, bevor Zeilen den Server erreichen ([Messprotokoll des Spikes](../messungen/callback-loesung-spike-2026-11-05.md), Messung b3).

Beide Tabellen haben beim Start **keine Zeilen**. Zeilen kommen über das Massenladen (Abschnitt „Daten laden“) in den Arbeitsspeicher des Testservers; `INSERT`, `UPDATE` und `DELETE` als SQL-Text enden mit `TDS_UNSUPPORTED_SQL` ([SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends)). Ein Neustart des Containers leert die Zeilen.

## Struktur ändern und lesen (Katalog)

Der Testserver hält seine Tabellen im Arbeitsspeicher und ändert sie auf `CREATE TABLE` und `ALTER TABLE … ADD`; die Tabellen aus der Beschreibung sind der Anfangsstand. Ein Neustart des Containers stellt diesen Anfangsstand wieder her. Die Katalogabfragen, die d-migrate 1.12.0 für `schema reverse`, `schema compare` und `schema migrate` stellt, beantwortet der Server aus dem jeweils aktuellen Stand ([SPEC-036](../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig), Formen K und D). Gemessen (2026-11-06, ein Host, [Messprotokoll](../messungen/katalog-und-ddl-zustand-2026-11-06.md)): `schema reverse` (auch `--include-all`), `schema compare` gegen das Ergebnis von `schema reverse` und gegen die Beschreibung, und `schema migrate --execute` mit einer neuen Tabelle und einer neuen Spalte enden mit Exit 0.

```sh
docker run --rm --network host --user "$(id -u):$(id -g)" -v "$PWD:/work" ghcr.io/pt9912/d-migrate@sha256:308f27753036c9cc423f716b9fc4c9e68016543a8003ecd2f8282e05a2ee6491 \
  schema reverse --source 'mssql://alice:secret@127.0.0.1:1433/manuell?encrypt=false' --output /work/ist.yaml
```

Grenzen: Gespeichert wird die Struktur; Zeilen kommen nur über das Massenladen (Abschnitt „Daten laden“). Die Bibliothek hat keine Transaktion: bricht `schema migrate --execute` in der Mitte ab, bleibt die erste Änderung stehen, auch wenn d-migrate `FULL_ROLLBACK_CONFIRMED` meldet. Identität, Standardwerte, Fremdschlüssel, Indizes und Prüfbedingungen kennt der Katalog nicht; ein Soll mit dem Typ `identifier` zeigt bei `schema compare` einen Unterschied (`identifier -> biginteger`, Exit 1). Eine Spalte, die ein Client mit `NVARCHAR(MAX)` anlegt, zeigt der Katalog als unbegrenzt (`max_length` -1); eine Spalte aus der Beschreibung ohne `max_length` zeigt er als 4000 Zeichen, sodass ein Soll mit `text` ohne `max_length` gegen sie einen Unterschied meldet.

## Daten laden (Massenladen)

`data import` und `data transfer` von d-migrate 1.12.0 laden im Standardpfad (ohne `--on-conflict`) über das Massenladen von mssql-jdbc (`INSERT BULK`, Pakettyp `0x07`). Der Testserver nimmt die Zeilen in den Arbeitsspeicher; ein anschließendes `data export` liefert sie ([SPEC-036](../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) Form B, [SPEC-001](../../spec/spezifikation.md#spec-001--zustandsautomat-einer-verbindung)).

```sh
docker run --rm --network host --user "$(id -u):$(id -g)" -v "$PWD:/work" ghcr.io/pt9912/d-migrate@sha256:308f27753036c9cc423f716b9fc4c9e68016543a8003ecd2f8282e05a2ee6491 \
  data import --target 'mssql://alice:secret@127.0.0.1:1433/manuell?encrypt=false' --source /work/kunden.json --table kunden
```

Grenzen (Setzungen, soweit nicht anders gesagt nicht am echten SQL Server gemessen; Messprotokoll des Massenladens in `docs/messungen/bulk-load-2026-11-07.md`):

- Eine Nachricht des Treibers (Voreinstellung 10 000 Zeilen, `--chunk-size`) gilt als Ganzes: sie wird ganz oder gar nicht in den Bestand übernommen. Ein doppelter Schlüssel, ein NULL im Schlüssel oder ein Wert, der nicht zum Typ der Spalte passt, weist die ganze Nachricht ab; der Server antwortet wie der echte SQL Server auf einen Schlüsselverstoß (Nummer 2627, Class 14; gemessen mit `SqlBulkCopy`, [Messprotokoll](../messungen/referenzabgleich.md), M-12), und d-migrate endet mit Exit 5 (der Text des Servers ist gekürzt; die Zuordnung jedes Fehlers des Einfügens zu 2627 ist eine Setzung). Nachrichten davor bleiben im Bestand, es gibt keine Transaktion über mehrere Nachrichten.
- Eine Nachricht ist höchstens 16 MiB groß (eine größere schließt die Verbindung) und liegt im Speicher des Servers; der Heap des Containers muss die Zeilen aller Nachrichten tragen. Der Server nimmt höchstens 1024 Spalten und 262 144 Zellen (Spalten mal Zeilen) je Nachricht an (Setzung; eine größere Nachricht schließt die Verbindung mit einem Protokollfehler), und der Bestand nimmt höchstens 2 097 152 Zellen über alle Tabellen auf (Setzung); eine Nachricht darüber weist er ab (`Backend error.`, Exit 5 von d-migrate), statt dass der Heap endet. Bei kleinerem Heap als diese Zellen brauchen, endet der Prozess trotzdem mit `OutOfMemoryError` (gemessen sind 4 bis 120 Byte je Zelle für die Zeilen einer Nachricht, [Messprotokoll](../messungen/bulk-load-2026-11-07.md); der Bestand selbst ist nicht gemessen); bei der Voreinstellung von 10 000 Zeilen je Nachricht gilt die erste Grenze für Tabellen bis 26 Spalten, mit mehr Spalten hilft `--chunk-size`.
- Tabellen mit `INT`-Spalten und ganzzahlig geschriebene `DECIMAL`-Werte scheitern im Treiber, bevor der Server sie sieht; die Beispiel-Tabellen nutzen deshalb `BIGINT`.

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
tdswire: gestartet, Port 1433                       (make testserver-logs; daneben stehen JSON-Zeilen der Ereignisse)
laufzeit-client: Login ok                           (Exit 0)
laufzeit-client: Login fehlgeschlagen               (mit -e TDSWIRE_PASSWORT=falsch, Exit 1; danach Gradle-Meldung `BUILD FAILED`)
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

`TESTSERVER_VERSCHLUESSELUNG=REQUIRED` verlangt zusätzlich `TESTSERVER_TLS_SPEICHER` (PKCS#12-Datei) und `TESTSERVER_TLS_PASSWORT_DATEI` (Datei mit dem Kennwort des Speichers); fehlt eines, bricht `make testserver-up` mit einer Meldung ab (belegt im Selbsttest). Das Skript legt beide Dateien mit `docker cp` in den Container. **Benutzerliste bei `REQUIRED`:** Das Skript schaltet `AUTH_PLAIN_TESTMODE` mit `REQUIRED` nicht ein; die Voreinstellungsliste (`alice`, `{plain}secret`) wäre dann nach der Spezifikation ein Startabbruch (`plain` nur im Testmodus), `make testserver-up` endete mit „der Server wurde nicht bereit“. Nötig ist `TESTSERVER_BENUTZER_DATEI` mit Prüfwerten `{pbkdf2-sha256}Salz$Iterationen$Hash` statt `{plain}`. Im Handlauf gefahren am 2026-11-05 (ein Host, [Messprotokoll](../messungen/tls-d-migrate-2026-11-05.md)): Start mit `REQUIRED`, Schlüsselspeicher `gueltig.p12` und pbkdf2-Benutzerliste (600000 Iterationen) gelingt; das Skript bricht bei `REQUIRED` ohne Benutzerliste nicht früh ab. **Nicht gemessen:** die Dateirechte der kopierten Dateien und die Annahme durch andere Clients als d-migrate 1.12.0 und `Microsoft.Data.SqlClient` 7.1.1 (dessen Anmelde-Tests des Referenzclients liefen am 2026-11-05 mit 19 von 19 grün, ein Host, ein Lauf). Ein Zertifikat erzeugt diese Anleitung nicht.

## Grenzen und nicht Belegtes

Was der Testserver nicht kann:

- Kein Produkt: kein Betrieb, keine Registry, keine Zusage für andere Rechner oder Netze ([ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md)).
- Daten nur über das Massenladen: `INSERT`, `UPDATE` und `DELETE` als SQL-Text werden abgewiesen, ein Neustart leert den Bestand. Weitere Zeilenquellen braucht ein Einbettender im Code.
- Nur die Anfrageformen von [SPEC-036](../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig); dass die Liste abschließend ist, ist eine Setzung (nicht gemessen).
- Keine Windows-Authentifizierung, kein Kerberos, kein Entra ID (Out-of-Scope im [Lastenheft](../../spec/lastenheft.md)); angemeldet wird mit Benutzer und Kennwort aus der Liste.
- Keine Lastaussage: unter Last, mit vielen Verbindungen oder langer Laufzeit ist der Container nicht gemessen.
- Abbruch einer laufenden Anfrage (Cancel) mit Folgeabfrage am Referenzclient: im Integrationslauf (`make simulations-integration`) am Referenzclient belegt, TDS 7.4 ohne Verschlüsselung und ohne Pool; im Handlauf nicht gemessen.
- Grenze zu JDBC (gemessen am 2026-11-05, ein Host, Läufe einzeln, Last 14 bis 21): d-migrate 1.12.0 (Image `ghcr.io/pt9912/d-migrate:latest`, Digest `sha256:308f27753036c9cc423f716b9fc4c9e68016543a8003ecd2f8282e05a2ee6491`, mssql-jdbc 13.4.0) meldet sich mit `encrypt=false` an, der Verbindungstest des Pools läuft, und `data export --tables kunden` (auch `bestellungen`; JSON, CSV, YAML) endet mit Exit 0 und einem Ergebnis ohne Zeilen. Es scheiterten damals, ohne dass hier etwas dagegen gebaut wäre: Abfragen auf `sys.*` und `INFORMATION_SCHEMA` (`schema reverse` läuft seit dem Katalog, siehe oben; `data export` ohne `--tables` ist nicht erneut gemessen), `data profile` (`data import` im Standardpfad läuft seit dem Massenladen, siehe „Daten laden“) und ein Filter, dessen Wert nicht verlustfrei in den Spaltentyp passt (`--filter "Id = 7"`: Meldung `Invalid value`; vermutete Ursache, aus dem Code gelesen: d-migrate sendet `bigint`, die Spalte war `Int32`; seit den `BIGINT`-Schlüsseln der Beispieltabellen nicht erneut gemessen; Textfilter liefen). Einzelheiten und Auszüge: [Messprotokoll](../messungen/d-migrate-verbindung-und-export-2026-11-05.md).
- TLS bei `REQUIRED` (gemessen am 2026-11-05, ein Host, ein Lauf; [Messprotokoll](../messungen/tls-d-migrate-2026-11-05.md)): Der Testserver bietet im TDS-7.4-Pfad höchstens TLS 1.2 an, der TDS-8.0-Pfad (`Encrypt=Strict`) TLS 1.3. d-migrate 1.12.0 meldet sich mit `encrypt=true` im Treiber-Standard an (mssql-jdbc 13.4.0 sendet unter TLS 1.3 im 7.4-Mantel sein `Finished` nicht); zum Zeitpunkt der Messung endete der Lauf danach mit „Unsupported SQL“ (der Verbindungstest `SELECT 1` war damals nicht gebaut; er läuft seit dem Slice zu Verbindung und Export, die Kombination mit `encrypt=true` ist nicht erneut gemessen). Der Testserver braucht keine JVM-Option; wer die Bibliothek einbettet, setzt die Ticket-Zahl der JDK-Systemeigenschaften `jdk.tls.server.*` nicht auf 0: Gemessen ist, dass `Microsoft.Data.SqlClient` im 7.4-Pfad den Handschlag abbricht („unexpected message“), wenn der Server die Session-Ticket-Erweiterung ankündigt und wegen Ticket-Zahl 0 kein Ticket sendet. Ohne die Erweiterung war der Handschlag grün; andere Werte der Ticket-Zahl sind nicht gemessen.
- Die Testwerte (`alice`/`secret`, Klartext) dürfen nicht für Daten genutzt werden, die Schutz brauchen. Ein Host außer Loopback macht den Server im Netz erreichbar.

Nicht belegt (Stand: Handlauf am 2026-11-05):

- TLS im Handlauf (`REQUIRED` mit Schlüsselspeicher und Passwortdatei).
- Ein Login mit einer eigenen Benutzerliste an einem laufenden Container.
- Die Dateirechte nach `docker cp` (Benutzerliste, Beschreibung, Schlüsselspeicher) im Container.
- Abfragen gegen die Beispiel-Tabellen durch irgendeinen Client; Microsoft.Data.SqlClient gegen den handgestarteten Container; Zugriff von einem anderen Rechner.
- Fehlerpfade von `docker cp` und der Abbruch von `make testserver-up` durch SIGINT (der Container bleibt dann stehen, der nächste Aufruf meldet es).
- Der Nachvollzug dieser Anleitung durch eine zweite Person von einem frischen Klon aus (Closure-Trigger der Welle [welle-testserver-manuell](../plan/planning/welle-testserver-manuell.md)).
