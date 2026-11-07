# Referenz: Ereignisse und Log

Diese Seite ist Nachschlagewerk zu [Ereignisse lesen](einbettung-und-betrieb.md#5-ereignisse-lesen-senke) und [Das Log lesen](testserver-manuell.md#4-das-log-lesen). Die Bibliothek schreibt nirgends hin; sie übergibt Ereignisse an die `TelemetrySink` des Integrators ([TDS-FA-09](../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose)). Maßgeblich sind `EventKind` und `TelemetryEvent` im Package `domain.telemetry` und [Abschnitt 4 der Spezifikation](../../spec/spezifikation.md#4-fehler-codes-und-logging-felder).

## Felder jedes Ereignisses

| Feld | Inhalt |
|---|---|
| `kind` | die Art (`EventKind`); die Menge ist je Stand geschlossen |
| `at` | Zeitpunkt (UTC) aus dem Zeitgeber der Bibliothek |
| `level` | `ERROR`, `WARN`, `INFO`, `DEBUG`, je Art fest |
| `connectionId` | Zähler des Listeners, ab 1 je Server; fehlt bei Ereignissen ohne Verbindung |
| `sessionId` | zufällige UUID der Sitzung; fehlt vor der Annahme der Anmeldung |
| `clientProfile` | `unknown`, `tds-8.0:strict` oder `tds-7.4:<modus>` mit `<modus>` aus `off`, `on`, `not_supported`, `required`; stammt aus dem ausgehandelten Protokoll, nie aus Angaben des Clients; bei TDS 7.4 vor der Annahme der Anmeldung `unknown` |

Die übrigen Felder gehören zur Art und sind `null`, wo die Art sie nicht trägt: `port`, `tables`, `reason`, `key`, `issueKind`, `part`, `form`, `address`, `exceptionType`, `principal`, `failure`, `errorCode`, `cause`. Passwort, Prüfwert, Parameterwert, SQL-Text, Pfad und Ausnahmetext haben kein Feld; eine Ausnahme erscheint nur als Typname, jede Zeichenkette ist auf 256 UTF-16-Einheiten gekürzt (Tests `TelemetryEventTest`, `TelemetryRedactionTest`). Die Wertemengen von `part`, `issueKind`, `failure` und `errorCode` sind Namen interner Konstanten ohne Zusage.

## Ereignisarten

| Art | Ebene | Felder | Bedeutung |
|---|---|---|---|
| `SERVER_STARTED` | INFO | `port` | der Server läuft, `port` ist der gebundene Port |
| `SERVER_STOPPED` | INFO | | der Server ist beendet; genau ein Ereignis je Server |
| `START_REFUSED` | ERROR | `reason` (`BACKEND_AND_SIMULATION`, `TABLE_NAME_DUPLICATE`) | der Start bricht vor dem Binden ab, ohne dass Konfiguration oder TLS-Material die Ursache nennen |
| `CONFIG_INVALID` | ERROR | `key`, `issueKind` | ein Konfigurationsfehler beim Start (Schlüssel und Art, nie der Wert); je Fehler ein Ereignis |
| `TLS_START_REJECTED` | ERROR | `part`, `form` | Startprobe oder Laden der TLS-Konfiguration gescheitert (`SPEC-043`) |
| `BIND_FAILED` | ERROR | `address`, `port`, `exceptionType` | Binden gescheitert (`exceptionType` ist `null`, wenn der Port belegt ist) |
| `SECURITY_WEAKENED` | WARN | `reason` (`ENCRYPTION_TEST_UNENCRYPTED`, `PLAIN_TEST_MODE`) | eine Abschwächung der Sicherheit ist aktiv |
| `SIMULATION_BACKEND_READY` | DEBUG | `tables` | das Simulations-Backend der Bibliothek ist verdrahtet; `tables` ist die Zahl der Tabellen |
| `ACCEPT_FAILED` | ERROR | `exceptionType` | Annehmen scheiterte, der Listener läuft weiter |
| `CONNECTION_FAILED` | ERROR | `exceptionType` | der Handler einer Verbindung warf eine Ausnahme |
| `LOGIN_ACCEPTED` | INFO | `principal` | Anmeldung angenommen; `principal` ist die Kennung aus dem Authentifizierungsadapter (bei der mitgelieferten Liste aus der Liste des Betreibers; dass die Kennung eines eigenen Adapters frei von Geheimnissen ist, sagt die Bibliothek nicht zu) |
| `LOGIN_REJECTED` | WARN | `reason` (`UNKNOWN_USER`, `WRONG_PASSWORD`, `INPUT_TOO_LONG`, `UNSUPPORTED_INTEGRATED_SECURITY`, `UNSUPPORTED_FEDERATED_AUTHENTICATION`, `UNSUPPORTED_PASSWORD_CHANGE`, `ADAPTER_FAILURE`) | Anmeldung abgelehnt; ein Feld für die Kennung fehlt |
| `ADAPTER_FAILED` | ERROR | `exceptionType` | der Authentifizierungsadapter warf eine Ausnahme |
| `ENCRYPTION_NOT_OFFERED` | WARN | | der Client bietet im PRELOGIN keine Verschlüsselung an, die Richtlinie verlangt sie |
| `TLS_FIRST_NOT_OFFERED` | WARN | `reason` (`NO_TLS_MATERIAL`, `POLICY_NOT_REQUIRED`) | der Client beginnt mit TLS, der Server bietet TLS ab dem ersten Byte nicht an |
| `TLS_HANDSHAKE_FAILED` | WARN | `failure` | der TLS-Handschlag ist gescheitert (Art, nie der Text) |
| `REQUEST_FAILED` | WARN | `errorCode`, `cause` | eine Anfrage wurde mit dem Fehler `errorCode` (`TDS_*`) beantwortet; `cause` ist der Typname der Ausnahme oder eine feste Marke, sonst `null` |

Ein Start, der abbricht, erscheint mit Senke als `CONFIG_INVALID`, `TLS_START_REJECTED`, `BIND_FAILED` oder `START_REFUSED`. Geprüft ist die Freiheit von Pfaden und Ausnahmetext für jeden Abbruchgrund (`BootstrapTest`, `SecurityConfigNegativeTest`, `SimulationWiringTest`, `TelemetryRedactionTest`); nicht jede Form der TLS-Diagnose ist einzeln mit Eingaben belegt. Die Bibliothek erzeugt die Ereignisse; für Kotlin-Aufrufer sind die Unterklassen und ihre Konstruktoren `internal`, aus Java sind sie im Bytecode aufrufbar (die Grenze von `internal`).

## Log des Testservers

Der Testserver (kein Produkt, `SPEC-052`) übergibt `TdsServer.start` einen eigenen Adapter und schreibt je Ereignis eine JSON-Zeile auf die Standardausgabe. Echte Zeilen aus einem Handlauf (`make testserver-logs`, 2026-11-07; Kürzungen mit `…`):

```text
{"ts":"2026-11-07T06:18:28.182461264Z","level":"INFO","event":"server_started","client_profile":"unknown","port":1433}
{"ts":"2026-11-07T06:18:28.193062997Z","level":"WARN","event":"security_weakened","client_profile":"unknown","reason":"ENCRYPTION_TEST_UNENCRYPTED"}
{"ts":"2026-11-07T06:19:58.180722001Z","level":"INFO","event":"login_accepted","connection_id":9,"session_id":"1923efcd-…","client_profile":"tds-7.4:not_supported","principal":"alice"}
```

- `ts` (UTC, ISO-8601), `level`, `event` (der Name der Art in Kleinbuchstaben), dann die Felder der Art, die nicht `null` sind, in der Schreibweise mit Unterstrich (`connection_id`, `session_id`, `client_profile`, `reason`, `principal`, `error_code`, …).
- Die Zeile besteht nur aus ASCII-Zeichen; Sonderzeichen sind mit `jackson-core` escaped.
- `LOG_LEVEL` (`ERROR`, `WARN`, `INFO`, `DEBUG`, Voreinstellung `INFO`) wählt, ab welcher Ebene geschrieben wird; ein anderer Wert, auch ein kleingeschriebener, bricht den Start mit Exit-Code 1 ab. Am Prozess `main` des Testservers entsteht kein Ereignis der Ebene `DEBUG`: das Backend ist eine eigene Quelle über dem `TableStore`, und `simulation_backend_ready`, die einzige Art der Ebene `DEBUG`, entsteht nur bei der Simulation der Bibliothek; `LOG_LEVEL=DEBUG` ändert dort nichts gegenüber `INFO` (gemessen: der Lauf mit `DEBUG` in `TestserverLogProcessTest` zeigt kein Ereignis der Ebene `DEBUG`; die Gleichheit mit `INFO` folgt aus der Ebene der Arten).
- Die Zeilen laufen über eine Warteschlange von 1024 Zeilen mit einem Schreiber; ist sie voll, wird die neue Zeile verworfen, und die Zahl steht im Feld `dropped` der nächsten geschriebenen Zeile. Vor dem erfolgreichen Start und für den Grund eines abgebrochenen Starts schreibt der Testserver ohne Warteschlange. Beim Beenden leert er die Warteschlange mit einer Frist von 2 Sekunden; was dann bleibt, meldet eine letzte Zeile `log_dropped` mit `dropped`, soweit der Kanal annimmt.
- Daneben stehen die Zeilen `tdswire: gestartet, Port <port>` und `tdswire: gestoppt` (Bereitschaft, kein Ereignis) und bei einem abgebrochenen Start der Grund als Zeile `tdswire: …`.
- Gemessen sind Escaping, Ebene, Überlauf und Prozessende in den Tests (`TelemetryJsonTest`, `LogAdapterTest`, `LogStartTest`, `TestserverLogProcessTest`); Durchsatz, Zeit und Speicher des Generators sind nicht gemessen. Ein Einbettender ist an diese Zeile nicht gebunden.
