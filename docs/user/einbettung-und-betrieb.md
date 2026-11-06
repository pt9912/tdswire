# Die Bibliothek einbetten und betreiben

Diese Anleitung beschreibt, was ein Integrator und ein Betreiber vom gebauten Stand der Bibliothek wissen müssen: den Einstieg `TdsServer.start`, die Konfiguration, Log und Startdiagnose, das Anbinden eines Backends, das Simulations-Backend und den Betriebstext zu Speicher und blockierenden Quellen. Sie bildet den Ist-Zustand ab. Wo eine Aussage nicht gemessen ist, steht es dabei; was nicht gebaut ist, steht in [Grenzen und nicht Belegtes](#grenzen-und-nicht-belegtes). Bezug: [TDS-FA-08](../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-FA-09](../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-05](../../spec/lastenheft.md#tds-qa-05--wartbarkeit).

Das Produkt ist die JVM-Bibliothek. Es gibt kein ausführbares Server-Tool, kein Produkt-Image und keine Startklasse im Jar ([ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md)); der Testserver (`make testserver-up`, [Anleitung](testserver-manuell.md)) dient nur der Prüfung und ist kein Betriebsweg. Wer einen Prozess betreiben will, baut ihn aus der Bibliothek.

## Einbettung

`make build` baut das Bibliotheks-Jar `tdswire.jar` ohne `Main-Class`; die Bibliotheken, die es braucht, liegen im Verzeichnis `lib` daneben und stehen im `Class-Path` des Manifests. Jar und `lib/` gehören auf den Klassenpfad des Einbettenden (`SPEC-052`, Festlegung). Ein Einbettungstest dieses Repos (`make test`, Source-Set `integrator`) startet und bedient den Server ohne Jackson im Klassenpfad; an einem fremden Programm ist nichts gemessen.

Der Einstieg ist `TdsServer.start(config, source, telemetry)` im Package `io.github.pt9912.tdswire.runtime` ([Vertrag](../../spec/spezifikation.md#einstieg-des-integrators-vertrag-zu-spec-059)):

```kotlin
val outcome = TdsServer.start(config, source)        // suspendierend
when (outcome) {
    is StartOutcome.Running -> { /* outcome.server.port ist der gebundene Port */ }
    is StartOutcome.Aborted -> { /* outcome.reason, outcome.issues */ }
}
// später: outcome.server.stop()   (suspendierend, idempotent)
```

- `start` wirft für Konfigurations-, Tabellen-, TLS- und Bindefehler keine Ausnahme. Wer das Ergebnis nicht prüft, sieht den Abbruch nicht.
- `stop()` ist das geordnete Beenden ([`SPEC-044`](../../spec/spezifikation.md#spec-044--geordnetes-beenden)): der Listener schließt zuerst; eine Verbindung ohne laufende Anfrage und ohne unvollständig empfangene Nachricht endet sofort (ein Client, der danach sendet, sieht ein Schließen); eine Verbindung mit unvollständiger Nachricht bleibt bis zum Ablauf der Frist offen und wird dann getrennt; eine laufende Anfrage darf bis `SHUTDOWN_GRACE` zu Ende laufen, ihre Antwort wird vollständig gesendet, danach endet die Verbindung; eine Verbindung, die beim Beginn eine laufende Anfrage oder eine unvollständige Nachricht hatte, bleibt nach deren regulärem Ende offen, bis die Frist abläuft: jede Anfrage, die danach vollständig wird, erhält `TDS_SHUTDOWN` (ERROR und DONE mit Fehlerstatus) und danach endet die Verbindung. Nach Ablauf der Frist wird die laufende Anfrage wie bei ATTENTION abgebrochen (genau ein `cancel()` am Backend, die Antwort endet mit `DONE[ATTN]`); beendet das Backend den Abbruch nicht innerhalb von `CANCEL_TIMEOUT`, wird getrennt. `stop()` kehrt zurück, wenn alle Verbindungen beendet sind, spätestens `SHUTDOWN_GRACE` plus `CANCEL_TIMEOUT` nach dem Beginn (auch bei einem Backend, das den Abbruch seiner Coroutine nicht beachtet: der Listener gibt die Verbindung dann auf und wartet nicht auf den Aufruf); ein zweiter Aufruf endet ohne Wirkung. Die Reihenfolge „ERROR, DONE, dann Schließen“ ist Setzung und am echten Server nicht gemessen. Beachtet das Backend den Abbruch nicht, bleibt sein Aufruf bis zu seiner Rückkehr belegt. Nicht gebaut: dass von den übrigen Anweisungen eines Batches nach dem Beginn nichts mehr läuft (`SPEC-044` Nr. 3); der Server kennt keine Anweisungsgrenzen im Text.
- Eine `ServerConfig` gilt für einen Start: der Start löscht das Passwort im Schlüsselmaterial, sobald er es lädt. Wer neu startet, baut eine neue Konfiguration.
- Der Einstieg liest keine Umgebung und keine Datei, verlangt keine JVM-Option und schreibt nirgends hin. Den Authentifizierungsadapter lässt er nicht ersetzen: angemeldet wird gegen die mitgelieferte Benutzerliste.
- `BackendSource` wählt die Antworten: `Custom(backend)` mit einem eigenen `QueryBackend`, `Simulation(tables)` mit Tabellen und Zeilenquellen des Integrators, `None` (wie `Simulation` mit leerer Liste). `Custom` zusammen mit Tabellen gibt es nicht; ein doppelter Tabellenname bricht den Start ab.

Belegt ist das durch den Einbettungstest `EmbeddingTest` (`make test`: In-Memory-Backend, Zeilenquelle des Integrators, Abbruch bei ungültiger Konfiguration) und `FlaecheEmbeddingTest` (Parameter und Ausgabe eines Backends, Bedingung einer Tabelle als Filter, Fehler einer Benutzerliste). Alles in `SPEC-059` ist Festlegung, soweit nicht ein Test genannt ist; bis zur Version `1.0` ändert sich die öffentliche Fläche nur durch eine Änderung der Spezifikation (Setzung).

### Öffentliche Fläche

Öffentlich ist, was `SPEC-059` aufzählt; alles andere ist `internal`. Belegt ist nur, dass `explicitApi` (Modus `strict`) im Hauptmodul für jede Deklaration eine ausdrückliche Sichtbarkeit verlangt. Die Typnamen der Liste hält der Sensor `make flaeche` gegen die öffentlichen Typdeklarationen und gegen ihre Verwendung im Einbettungstest und im Testserver; Felder, Konstanten und Funktionen vergleicht er nicht, und `internal` ist im Bytecode öffentlich (Java-Aufrufer sehen es). Ein Integrator verlässt sich auf die Liste in der Spezifikation, nicht auf das, was der Compiler ihm zeigt.

## Konfiguration

Die Bibliothek liest keine Umgebung. Der Integrator baut eine `ServerConfig`; der Start prüft sie und liefert die Befunde als `StartOutcome.Aborted.issues`. Die Spalte „Umgebungsname“ nennt die Variable, aus der der **Testserver** den Wert liest; sie ist für die Bibliothek ohne Bedeutung.

| Feld der `ServerConfig` | Umgebungsname des Testservers | Voreinstellung | Bereich | Spezifikation |
|---|---|---|---|---|
| `listen.host` | `LISTEN_HOST` | `0.0.0.0` | nicht leer | `SPEC-005` |
| `listen.port` | `LISTEN_PORT` | 1433 | 0 bis 65535, 0 wählt einen freien Port | `SPEC-005` |
| `listen.maxConnections` | `MAX_CONNECTIONS` | 1000 | 1 bis 100000 | `SPEC-034` |
| `login.timeoutSeconds` | `LOGIN_TIMEOUT` | 15 s | 1 bis 3600 | `SPEC-008` |
| `login.maxPreloginMessageBytes` | `MAX_PRELOGIN_MESSAGE_BYTES` | 65536 | 512 bis 65536 | `SPEC-046` |
| `encryption` | `ENCRYPTION_POLICY` | `REQUIRED` | `REQUIRED` oder `TEST_UNENCRYPTED` | `SPEC-010` |
| `tls` (`TlsKeyMaterial`) | `TLS_KEYSTORE_FILE`, `TLS_KEYSTORE_PASSWORD_FILE` | keines | PKCS#12-Speicher und Passwort; bei `REQUIRED` Pflicht, bei `TEST_UNENCRYPTED` nicht zulässig | `SPEC-010` |
| `auth` (`AuthSettings`) | `AUTH_USERS_FILE`, `AUTH_PLAIN_TESTMODE` | keine Liste | Benutzerliste (`UserList` aus `UserList.parse`), Verfahren `plain` nur im Testmodus | `SPEC-051` |
| `requests.queryTimeoutSeconds` | `QUERY_TIMEOUT` | 30 s | 1 bis 3600 | `SPEC-009` |
| `requests.cancelTimeoutSeconds` | `CANCEL_TIMEOUT` | 5 s | 1 bis 3600 | `SPEC-033` |
| `requests.shutdownGraceSeconds` | `SHUTDOWN_GRACE` | 10 s | 0 bis 3600, 0 = sofort | `SPEC-047` |

Alle Bereiche und Voreinstellungen sind Festlegungen, nicht gemessen ([Abschnitt Defaults](../../spec/spezifikation.md#3-defaults-und-konstanten)); die Voreinstellungen stehen in `ServerConfig.kt`, die Prüfung läuft im Start (Befunde als `StartOutcome.Aborted.issues`). Ein Einbettender ohne Angabe bekommt für eine Anfrage nach 30 s `TDS_TIMEOUT`.

**Verschlüsselung.** Voreinstellung ist `REQUIRED` mit Schlüsselspeicher. `TEST_UNENCRYPTED` (TDS 7.4 im Klartext) ist eine ausdrückliche Abschwächung für Tests; mit einer Senke erscheint sie als Ereignis mit `kind = SECURITY_WEAKENED` und `reason = ENCRYPTION_TEST_UNENCRYPTED` (`SPEC-010`). Die Aussage, welche TLS-Version der Server je Pfad anbietet, steht in `SPEC-023` (TDS 7.4 höchstens TLS 1.2, TDS 8.0 TLS 1.3).

**Benutzerliste.** Eine Zeile je Benutzer, `Benutzername<TAB>Prüfwert`, UTF-8; der Prüfwert trägt eine Verfahrenskennung (`{pbkdf2-sha256}Salz$Iterationen$Hash`; `{plain}` nur im Testmodus). Format und Fehlerarten: [Authentifizierungsadapter](../../spec/spezifikation.md#authentifizierungsadapter-vertrag-zu-spec-051) (Setzungen, nicht gemessen). Der Integrator liest die Datei selbst und ruft `UserList.parse(text, plainTestMode)`; das Ergebnis ist ein `UserListResult`: `Valid` trägt die `UserList` (Feld `list`), die `AuthSettings(users, plainTestMode)` entgegennimmt, `Invalid` trägt einen `UserListError` mit Art und Zeile. Zur Datei auf dem Betriebsrechner: belegt ist nur, dass eine root-eigene, weltlesbare Datei, per `docker cp` in einen Container gelegt, im Testserver gelesen wird. **Dateirechte und Besitzer für den Betrieb sind nicht entschieden und nicht gemessen**; diese Anleitung empfiehlt keine.

**Verbindungen.** Der Listener nimmt höchstens `MAX_CONNECTIONS` Verbindungen zugleich auf; eine weitere wird ohne Antwortbytes geschlossen, und nach dem Ende einer Verbindung nimmt er wieder auf. Die Abweisung ohne Antwortbytes und die Wertebereiche von `MAX_CONNECTIONS` und `SHUTDOWN_GRACE` sind Festlegungen, vom Nutzer bestätigt am 2026-11-06. Was der Referenzclient bei einer abgewiesenen Verbindung sieht, ist nicht gemessen. Endet die Gegenstelle während einer Anfrage (Schließen oder Abbruch mit RST), wird die Anfrage am Backend abgebrochen, ihr Strom geschlossen und nichts mehr geschrieben. Die Voreinstellung von `SHUTDOWN_GRACE` plus `CANCEL_TIMEOUT` (15 s) ist länger als die Voreinstellung von `docker stop` (10 s bis SIGKILL; Voreinstellung von Docker, hier nicht gemessen); wer den Testserver im Container beendet, setzt `SHUTDOWN_GRACE` kleiner.

**Nicht einstellbar.** `MAX_TDS_PACKET_BYTES` und `MAX_MESSAGE_BYTES` sind Konstanten der Spezifikation und kein Feld der Konfiguration. Die Drosselung wiederholter Anmeldeversuche (`SPEC-053`) ist nicht konfigurierbar.

## Log und Startdiagnose

Die Bibliothek schreibt nirgends hin. `TdsServer.start` nimmt eine Senke `TelemetrySink` entgegen (Voreinstellung `TelemetrySink.NONE`: dann entsteht kein Ereignis); wer ein Log will, übergibt eine Senke und formatiert die Ereignisse selbst. Vertrag der Senke (KDoc von `TelemetrySink`, Tests `TelemetryTest`):

- `accept` blockiert nicht und wirft nicht. Eine blockierende Senke hält den Anfragepfad an und belegt Threads von `Dispatchers.Default`, die andere Coroutinen des Prozesses mitnutzen; davor schützt die Bibliothek nicht.
- Wirft `accept` eine `Exception`, fängt die Bibliothek sie ab und verwirft sie. Eine `CancellationException` wird nicht gefangen; dass auch ein `Error` nicht gefangen wird, ist eine Festlegung.
- Wer die Senke übergibt, schließt sie auch. Teilen mehrere Server in einer JVM eine Senke, unterscheidet das Ereignis sie nicht: `connection_id` zählt je Server.

**Felder.** Jedes Ereignis trägt `at` (UTC), `level` (`ERROR`, `WARN`, `INFO`, `DEBUG`, je Ereignisart fest), `connectionId` und `sessionId` (wo vorhanden) und `clientProfile` (`unknown`, `tds-8.0:strict`, `tds-7.4:<modus>`). Die Art des Ereignisses steht in `kind` (Aufzählung `EventKind`); die übrigen Felder gehören zur Art und sind `null`, wo die Art sie nicht trägt: `errorCode` und `cause` bei `REQUEST_FAILED`, `principal` nur bei `LOGIN_ACCEPTED`, `reason` bei den Arten mit fester Marke. Passwort, Prüfwert, Parameterwert, SQL-Text, Pfad und Ausnahmetext haben im Modell kein Feld; eine Ausnahme erscheint nur als Typname, jede Zeichenkette ist auf 256 UTF-16-Einheiten gekürzt (Tests `TelemetryEventTest`, `TelemetryRedactionTest`). Die gebauten Ereignisarten und ihre Ebenen stehen in [Abschnitt 4 der Spezifikation](../../spec/spezifikation.md#4-fehler-codes-und-logging-felder); die Menge ist je Stand geschlossen, und die Wertemengen von `part`, `issueKind`, `failure` und `errorCode` sind Namen interner Konstanten ohne Zusage.

**Startdiagnose.** Ein Start, der abbricht, bindet keinen Port und startet keinen Thread des Servers. `StartOutcome.Aborted.reason` nennt den Grund ohne Pfad, Ausnahmetext, Schlüsselmaterial und Kennwort; bei einem belegten Port lautet er `Port belegt: <Adresse>:<Port>`, sonst `Start gescheitert: ` plus die Meldung des Fehlers. `issues` nennt bei ungültiger Konfiguration jeden Fehler mit Schlüssel und Art (kein Wert); der Wortlaut des Schlüssels ist keine Zusage. Mit Senke erscheint dieselbe Diagnose als Ereignis (`CONFIG_INVALID`, `TLS_START_REJECTED`, `BIND_FAILED`, `START_REFUSED`). Geprüft ist die Freiheit von Pfaden und Ausnahmetext für jeden Abbruchgrund (`BootstrapTest`, `SecurityConfigNegativeTest`, `SimulationWiringTest`, `TelemetryRedactionTest`); nicht jede Form der TLS-Diagnose ist einzeln mit Eingaben belegt.

## Metriken und Spans

Gebaut ist nur die Senke. **Metriken-Schnappschüsse und Span-Enden sind nicht gebaut**: keine Ereignisart ist ein Schnappschuss oder ein Span-Ende, es gibt weder `METRICS_INTERVAL_SECONDS` noch einen Endpunkt. Die Namen der Spans (`tds.connection`, `tds.query`, `backend.execute`) und die Liste der Metriken stehen in [Abschnitt 5 der Spezifikation](../../spec/spezifikation.md#5-metriken-und-tracing-felder) als Zielzustand. Auch die Felder `request_id` und `duration_ms` und die Ereignisse „Anfrage beendet“, „Verbindung geöffnet und geschlossen“ und „Beginn und Ende des geordneten Beendens“ sind nicht gebaut.

## Ein Backend anbinden

Ein Backend implementiert `QueryBackend` (Package `port`): `execute(QueryRequest)` liefert eine `QueryExecution` mit einem `Flow` von `QueryEvent`; `cancel(sessionId, requestId)` beendet die laufende Operation. Das Backend kennt weder Pakete noch Tokens. Der Vertrag steht im [Query-Backend-Vertrag](../../spec/spezifikation.md#query-backend-vertrag-zu-spec-022) (`SPEC-022`, Kotlin-Quelltext als Setzung, gemessen ist das Ergebnismodell an einem Generator-Backend mit drei Spalten). Zugesagt für den Anschluss:

- Ein Fehler beim Start ist ein `Failure` im Strom, keine Ausnahme von `execute`.
- Die Werte einer Zeile haben die Klassen aus `SPEC-002` (`Int`, `Long`, `Boolean`, `String`, `BigDecimal`, `LocalDate`, `LocalTime`, `LocalDateTime`, `null`).
- Der Server liest nur so schnell, wie der Transport abnimmt; das gilt für das Test-Backend an einem Draht ohne Netz, nicht für einen Socket, ein blockierendes Backend oder das Simulations-Backend.
- Das Feld `deadlineEpochMillis` ist ein Hinweis; die Frist `QUERY_TIMEOUT` setzt der Protokollkern durch und endet in `TDS_TIMEOUT`.
- Welche Anweisungen und Typen der Server annimmt, steht in der [Kompatibilitätsmatrix](../plan/kompatibilitaetsmatrix.md); diese Anleitung kopiert sie nicht.

### Betriebstext zu Speicher und blockierenden Quellen

Die folgenden Aussagen sind Festlegungen und Setzungen aus [ADR-0013](../plan/adr/0013-backend-grenzen.md); **die Zahlen sind Messungen eines Spikes** (fingiertes Backend, `Thread.sleep` als Blockade, Kotlin 2.1.20 und `kotlinx-coroutines-core` 1.10.1, `-Xmx256m`, ausgelasteter Rechner), **nicht Verhalten des Produkts oder eines echten Treibers**.

**Pflicht für Integratoren.** Ein Backend und jede Zeilenquelle (`RowSource.next()`) sind suspendierend: sie blockieren den Thread des Aufrufers nicht. Ein blockierender Treiber gehört hinter einen eigenen, begrenzten Pool und wird unterbrechbar aufgerufen; der Server stellt dafür keinen Adapter. Empfohlen, keine Pflicht (Setzung): blockweise statt je Zeile aufrufen. Im Spike brauchte ein Backend, das je Zeile blockierte, 77 Threads statt 11 und 3099 ms statt 377 ms (200 Ströme zu 5000 Zeilen); blockweise Aufrufe lagen bei 707 ms. Der Abbruch eines nicht unterbrechbaren Aufrufs wartet im Spike den Rest der Blockade ab; ist sie länger als `CANCEL_TIMEOUT`, schließt die Sitzung, und der Thread bleibt bis zur Rückkehr belegt.

**Grenze für Ergebnisse.** Der Server erzwingt keine Grenze. Ein Backend, das ein Ergebnis materialisiert, begrenzt es selbst (Setzung, nicht gemessen). Der Server schützt sich durch den Rückstau der Senke; als Größenordnung maß der Spike 173 Byte je schmale und 454 Byte je breite Zeile im Heap und 1,54 Mio schmale Zeilen bis zum `OutOfMemoryError` bei 256 MiB.

**`OutOfMemoryError` und Betreiberschalter.** Ein `OutOfMemoryError` im Anfragepfad wird gefangen und als `TDS_INTERNAL_ERROR` beantwortet; die Verbindung schließt nach der Antwort. **Akzeptiertes Negativ:** im Spike war beim Fangen in vier von vier Läufen nach 20 s mindestens eine Coroutine nicht beendet, in dreien davon eine unbeteiligte kleine Verbindung; die Ursache ist nicht isoliert, die Häufigkeit im Produkt nicht gemessen, und ob die Antwort im erschöpften Heap noch geschrieben werden kann, ist nicht gemessen. Wer hängende Verbindungen nicht hinnimmt, startet die JVM des eigenen Prozesses mit `-XX:+ExitOnOutOfMemoryError`: sie endet beim ersten `OutOfMemoryError` (im Spike Exit-Code 3 nach rund 2 s) und wird von einem Supervisor neu gestartet. Die Bibliothek setzt den Schalter nicht und sieht ihn nicht vor; dass er das Fangen davor ersetzt, ist nicht gemessen. Einen Supervisor und einen Betriebs-Container liefert das Repo nicht ([ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md)).

## Simulations-Backend

`BackendSource.Simulation(tables)` (oder `SimulationBackend(SimCatalog(tables))` als `Custom`) beantwortet Anfragen aus Tabellenbeschreibungen. Eine `SimTable` trägt `schema` (Voreinstellung `dbo`), `name`, `columns` (`SimColumn`: Name, `SqlType`, nullbar), `primaryKey`, die Zeilenquelle `rows` und optional die Callbacks `onInsert`, `onUpdate`, `onDelete` ([Schnittstelle](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends)).

- `rows` bekommt eine `SimQuery` (Spalten in der Reihenfolge der Anfrage, ein Filter als Hinweis zum Vorfiltern) und liefert eine `RowSource`; `next()` liefert die Werte einer Zeile oder `null` am Ende, suspendiert und blockiert nicht; `close()` wird genau einmal gerufen.
- Der Schlüssel `primaryKey` beschreibt nur; er wirkt weder auf das Resultset noch auf die Callbacks und prüft keine Eindeutigkeit.
- Namen sind ohne Beachtung der Groß-/Kleinschreibung eindeutig; jede Tabelle hat mindestens eine Spalte und einen Schlüssel, eine Dezimalspalte höchstens Precision 38.
- Ohne Callback endet Einfügen, Ändern und Löschen mit `TDS_UNSUPPORTED_SQL`.
- Erkannt wird eine feste Liste von Anweisungsformen ([`SPEC-036`](../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig), vorläufig): `SELECT` mit einfacher Bedingung sowie `INSERT`, `UPDATE`, `DELETE` einfacher Form. Alles andere endet mit `TDS_UNSUPPORTED_SQL`; dass die Liste abschließend ist, ist eine Setzung. Die Liste wird hier nicht kopiert.

Die Beschreibung der Tabellen **als Datei** (YAML im Schema-Modell von d-migrate) liest nur der Testserver; die Bibliothek kennt weder die Datei noch das Format. Wer die Tabellen aus einer Datei bauen will, liest sie selbst und übergibt `SimTable`-Werte. Das Verhalten gegen den Referenzclient steht in der Kompatibilitätsmatrix und im [Messprotokoll zum Referenzabgleich](../messungen/referenzabgleich.md); ein echter SQL Server ist dabei nicht die Gegenstelle.

## Grenzen und nicht Belegtes

- Nicht gebaut: Metriken, Spans, `request_id`, `duration_ms`, Ereignisse zu Verbindung angenommen/beendet, Anfrage beendet und geordnetem Beenden. Ein Log liefert die Bibliothek nicht; der JSON-Adapter mit `LOG_LEVEL` ist nicht gebaut.
- Keine Windows-Authentifizierung, kein Kerberos, kein Entra ID ([Lastenheft](../../spec/lastenheft.md)); angemeldet wird mit Benutzer und Kennwort aus der Liste.
- Gemessen ist das Verhalten des Referenzclients `Microsoft.Data.SqlClient` gegen den Testserver in den Läufen dieses Repos, nicht gegen einen echten SQL Server, nicht unter Last, nicht mit vielen Verbindungen oder langer Laufzeit.
- Abbruch einer laufenden Anfrage (Cancel) mit Folgeabfrage ist am Referenzclient im Integrationslauf belegt (`make simulations-integration` und `make kompatibilitaet KATEGORIE=Synchronitaet`, TDS 7.4); nicht belegt sind Abbruch im Leerlauf und mehrere Abbrüche je Anfrage ([Kompatibilitätsmatrix](../plan/kompatibilitaetsmatrix.md), Zeile 12).
- Nicht belegt, keine Aussage dieser Anleitung: Dateirechte und Besitzer der Benutzerliste im Betrieb ([ADR-0018](../plan/adr/0018-laufzeit-artefakt.md), Abschnitt „Offen“); Lizenzhinweise zu den Bibliotheken in `lib/` (Rechtsfrage, [TDS-RB-05](../../spec/lastenheft.md#tds-rb-05--lizenz) verlangt die MIT-Lizenz des Projekts, das Jar führt sie unter `META-INF/LICENSE`); ob die Menge der öffentlichen Klassen gegen die Liste von `SPEC-059` von einem Sensor gehalten wird.
- Die Schlüsseltabelle oben und die Konfiguration im Code halten sich nur durch Lesen: kein Test vergleicht die dokumentierte Liste mit den Feldern von `ServerConfig`. Die Namen der Umgebungsvariablen des Testservers pinnt `ConfigLoaderTest`.
