# Die Bibliothek einbetten und betreiben

Diese Anleitung führt durch die Aufgaben eines Integrators und eines Betreibers der JVM-Bibliothek `tdswire`: einbinden, ein Backend anschließen, Tabellen vorgeben, Strukturänderungen zulassen, Ereignisse lesen, den Server begrenzt betreiben und beenden. Jede Aufgabe hat Ziel, Voraussetzungen, Schritte, erwartetes Ergebnis und typische Fehler. Schlüssel, Ereignisarten, Fehlercodes und Grenzen stehen in der [Referenz der Konfiguration](referenz-konfiguration.md) und der [Referenz der Ereignisse](referenz-ereignisse.md). Den Testserver von Hand bedienen: [Testserver von Hand](testserver-manuell.md). Bezug: [TDS-FA-08](../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-FA-09](../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-QA-05](../../spec/lastenheft.md#tds-qa-05--wartbarkeit).

Das Produkt ist die JVM-Bibliothek. Sie hat keine ausführbare Startklasse und kein Produkt-Image ([ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md)); der Testserver ist ein Prüfwerkzeug, kein Betriebsweg. Den Prozess baut der Integrator aus der Bibliothek. Sie schreibt kein Log und liest weder Umgebung noch Datei; Ereignisse gehen an die Senke des Integrators. Angemeldet wird mit Benutzer und Kennwort aus einer Liste; Windows-Authentifizierung, Kerberos und Entra ID sind ausgeschlossen ([Lastenheft](../../spec/lastenheft.md)).

Die Codebeispiele sind Auszüge aus `AnleitungTest` im Source-Set `integrator` (`src/integrator/kotlin/io/github/pt9912/tdswire/integrator/AnleitungTest.kt`). Der Test übersetzt sie gegen die öffentliche Fläche und fährt sie mit `make test` gegen einen Rohbytes-Client, der kein Referenzclient ist; belegt ist, dass die Beispiele übersetzen und die beschriebene Antwort liefern.

## Aufgaben

| Ich möchte … | Abschnitt |
|---|---|
| die Bibliothek in meine Anwendung einbinden und den Server starten und stoppen | [1](#1-den-server-in-die-anwendung-einbinden) |
| mein eigenes Backend anschließen | [2](#2-ein-eigenes-backend-anschließen) |
| Tabellen mit eigenen Zeilen vorgeben (Simulations-Backend) | [3](#3-tabellen-vorgeben-simulations-backend) |
| Tabellen zur Laufzeit anlegen und ändern lassen | [4](#4-strukturänderungen-zulassen-veränderlicher-bestand) |
| Ereignisse des Servers in mein Log bekommen | [5](#5-ereignisse-lesen-senke) |
| den Server begrenzen, betreiben und geordnet beenden | [6](#6-den-server-begrenzen-betreiben-und-beenden) |

## 1. Den Server in die Anwendung einbinden

**Ziel.** Ein Server der Bibliothek läuft im Prozess der Anwendung, nimmt Anmeldungen an und endet auf Aufruf.

**Voraussetzungen.** Docker und GNU `make` zum Bauen (kein lokales JDK, [AGENTS.md](../../AGENTS.md) §3.1); Kotlin mit Coroutines in der Anwendung.

**Schritte.**

1. Das Jar bauen: `make build`. Es entsteht `tdswire.jar` ohne `Main-Class`; die Bibliotheken, die es braucht, liegen in `lib/` daneben und stehen im `Class-Path` des Manifests. Jar und `lib/` gehören auf den Klassenpfad der Anwendung (`SPEC-052`, Festlegung). Jackson ist nicht im Klassenpfad nötig; der Einbettungstest startet und bedient den Server ohne Jackson.
2. Die Konfiguration bauen. Das Beispiel nimmt Loopback und Klartext (TDS 7.4, nur für Tests) und liest die Benutzerliste aus dem Text einer Datei, die die Anwendung selbst liest:

   ```kotlin
   internal fun anleitungKonfiguration(port: Int, benutzerliste: String): ServerConfig {
       val users = when (val parsed = UserList.parse(benutzerliste, plainTestMode = true)) {
           is UserListResult.Valid -> parsed.list
           is UserListResult.Invalid -> error("Benutzerliste ungültig: ${parsed.error}")
       }
       return ServerConfig(
           listen = ListenSettings("127.0.0.1", port),
           encryption = EncryptionSetting.TEST_UNENCRYPTED,
           auth = AuthSettings(users, plainTestMode = true),
       )
   }
   ```

   Eine Zeile der Benutzerliste ist `Benutzername<TAB>Prüfwert`, etwa `alice<TAB>{plain}secret`; `{plain}` gilt nur im Testmodus, sonst `{pbkdf2-sha256}Salz$Iterationen$Hash`. Die Voreinstellung der Verschlüsselung ist `REQUIRED` mit Schlüsselspeicher (`TlsKeyMaterial`); `TEST_UNENCRYPTED` ist eine ausdrückliche Abschwächung.
3. Starten und das Ergebnis prüfen. `start` ist suspendierend:

   ```kotlin
   val outcome = TdsServer.start(config, source)
   when (outcome) {
       is StartOutcome.Running -> { /* outcome.server.port ist der gebundene Port */ }
       is StartOutcome.Aborted -> { /* outcome.reason, outcome.issues */ }
   }
   ```

   `source` ist eine `BackendSource`: `Custom(backend)` ([Abschnitt 2](#2-ein-eigenes-backend-anschließen)), `Simulation(tables)` ([Abschnitt 3](#3-tabellen-vorgeben-simulations-backend)) oder `None` (wie `Simulation` mit leerer Liste). `Custom` zusammen mit Tabellen gibt es nicht; ein doppelter Tabellenname bricht den Start ab. Als dritten Parameter nimmt `start` die Senke `TelemetrySink` ([Abschnitt 5](#5-ereignisse-lesen-senke)).
4. Beenden: `outcome.server.stop()` (suspendierend, idempotent).

**Erwartetes Ergebnis.** `StartOutcome.Running`; ein Client kann sich mit Benutzer und Kennwort der Liste an `server.port` anmelden (`Port 0` wählt einen freien Port). Nach `stop()` nimmt der Port keine Verbindung mehr an. Eine `ServerConfig` gilt für einen Start: der Start löscht das Passwort im Schlüsselmaterial, sobald er es lädt; wer neu startet, baut eine neue Konfiguration.

**Typische Fehler.**

| Beobachtung | Ursache | Abhilfe |
|---|---|---|
| `StartOutcome.Aborted`, `issues` nennt `<Schlüssel>: OUT_OF_RANGE`, `MISSING`, `CONFLICT` | Wert außerhalb des Bereichs, `REQUIRED` ohne Schlüsselmaterial, `TEST_UNENCRYPTED` mit Schlüsselmaterial | Konfiguration korrigieren ([Referenz](referenz-konfiguration.md#konfigurationsfelder)); der Wortlaut des Schlüssels ist keine Zusage |
| `Aborted.reason` ist `Port belegt: <Adresse>:<Port>` | der Port ist vergeben | anderen Port wählen |
| der Fehler wird nicht bemerkt | `start` wirft für Konfigurations-, Tabellen-, TLS- und Bindefehler keine Ausnahme | das Ergebnis immer prüfen |

`Aborted.reason` nennt den Grund ohne Pfad, Ausnahmetext, Schlüsselmaterial und Kennwort; ein Start, der abbricht, bindet keinen Port. Das Beispiel `anleitungBefunde` (im Test) gibt `outcome.issues.map { it.message }` aus: für Port 70000 steht dort ein Befund mit der Art `OUT_OF_RANGE`.

**Öffentliche Fläche.** Öffentlich ist, was [`SPEC-059`](../../spec/spezifikation.md#einstieg-des-integrators-vertrag-zu-spec-059) aufzählt; alles andere ist `internal`. `explicitApi` (Modus `strict`) verlangt im Hauptmodul für jede Deklaration eine ausdrückliche Sichtbarkeit, und `make flaeche` hält die Typnamen der Liste gegen die öffentlichen Typdeklarationen und ihre Verwendung im Einbettungstest und im Testserver. Felder, Konstanten und Funktionen vergleicht er nicht; `internal` ist im Bytecode öffentlich, ein Java-Aufrufer sieht es. Maßgeblich ist die Liste der Spezifikation, nicht das, was der Compiler zeigt. Alles in `SPEC-059` ist Festlegung, soweit kein Test genannt ist; belegt sind die Einstiege durch `EmbeddingTest` und `FlaecheEmbeddingTest`.

## 2. Ein eigenes Backend anschließen

**Ziel.** Die Anfragen der Clients beantwortet Code der Anwendung.

**Voraussetzungen.** Ein laufender Server nach [Abschnitt 1](#1-den-server-in-die-anwendung-einbinden).

**Schritte.**

1. `QueryBackend` implementieren (Package `port`): `execute(QueryRequest)` liefert eine `QueryExecution` mit einem `Flow` von `QueryEvent`, `cancel(sessionId, requestId)` beendet die laufende Operation. Das Backend kennt weder Pakete noch Tokens. Das Beispiel beantwortet jede Anfrage mit zwei Zeilen:

   ```kotlin
   internal class AnleitungBackend : QueryBackend {
       private val spalte = ResultColumn("Text", SqlType.UnicodeText, nullable = false)

       override suspend fun execute(request: QueryRequest): QueryExecution {
           val ereignisse: List<QueryEvent> = listOf(
               QueryEvent.ResultStart(listOf(spalte)),
               QueryEvent.Row(listOf("eins")),
               QueryEvent.Row(listOf("zwei")),
               QueryEvent.ResultEnd(2),
           )
           return object : QueryExecution {
               override val events: Flow<QueryEvent> = ereignisse.asFlow()

               override suspend fun close() = Unit
           }
       }

       override suspend fun cancel(sessionId: String, requestId: String) = Unit
   }
   ```

2. Mit `BackendSource.Custom(AnleitungBackend())` an `TdsServer.start` übergeben.

**Erwartetes Ergebnis.** Ein Batch (`SELECT irgendwas`) liefert zwei Zeilen (Test `einEigenesBackendBeantwortetDieAnfrage`).

**Regeln des Anschlusses.** Der Vertrag steht im [Query-Backend-Vertrag](../../spec/spezifikation.md#query-backend-vertrag-zu-spec-022) (`SPEC-022`; der Kotlin-Quelltext ist Setzung, gemessen ist das Ergebnismodell an einem Generator-Backend mit drei Spalten):

- Ein Fehler beim Start ist ein `Failure` im Strom, keine Ausnahme von `execute`. `Failure.scope` (`FailureScope`) sagt, ob nur die Anweisung abbricht (`STATEMENT`: der Strom darf mit der Folgeanweisung weitergehen) oder der Batch (`BATCH`, die Voreinstellung: die Antwort endet am Fehler).
- Ein `OutputParameter` nennt den Parameter über seinen Namen aus `QueryRequest.parameters`; der Server schreibt das RETURNVALUE mit der Position des Parameters in der Aufrufliste des RPC als Ordinal. `RETURNSTATUS` (0, wenn das Backend keinen `ReturnStatus` meldet) und RETURNVALUE stehen am Ende der Antwort. Ein Name, den die Anfrage nicht nennt, bekommt die Position hinter dem letzten Parameter.
- Die Werte einer Zeile haben die Klassen aus `SPEC-002` (`Int`, `Long`, `Boolean`, `String`, `BigDecimal`, `LocalDate`, `LocalTime`, `LocalDateTime`, `null`).
- `deadlineEpochMillis` ist ein Hinweis; die Frist `QUERY_TIMEOUT` setzt der Protokollkern durch und endet in `TDS_TIMEOUT`.
- Backend und Zeilenquellen sind suspendierend und blockieren den Thread des Aufrufers nicht. Ein blockierender Treiber gehört hinter einen eigenen, begrenzten Pool und wird unterbrechbar aufgerufen; der Server stellt dafür keinen Adapter. Blockweise statt je Zeile aufzurufen ist empfohlen (Setzung). Zahlen eines Spikes (fingiertes Backend, `Thread.sleep` als Blockade, `-Xmx256m`, ausgelasteter Rechner; [ADR-0013](../plan/adr/0013-backend-grenzen.md)): je Zeile blockierend 77 Threads und 3099 ms, blockweise 707 ms bei 200 Strömen zu 5000 Zeilen; das ist kein Verhalten des Produkts oder eines echten Treibers.
- Der Server erzwingt keine Grenze für Ergebnisse; ein Backend, das ein Ergebnis materialisiert, begrenzt es selbst (Setzung, nicht gemessen). Der Server liest nur so schnell, wie der Transport abnimmt; das gilt für das Test-Backend an einem Draht ohne Netz, nicht für einen Socket, ein blockierendes Backend oder das Simulations-Backend.
- Welche Anweisungen und Typen der Server annimmt, steht in der [Kompatibilitätsmatrix](../plan/kompatibilitaetsmatrix.md).

**Typische Fehler.**

| Beobachtung | Ursache | Abhilfe |
|---|---|---|
| der Client sieht `TDS_INTERNAL_ERROR` und die Verbindung schließt | das Backend warf eine Ausnahme, verletzte die Ereignisreihenfolge (Zeile ohne `ResultStart`, Strom endet im offenen Ergebnis) oder meldete einen Code außerhalb der erlaubten Menge | Ausnahmen als `Failure` melden; erlaubt sind `TDS_BACKEND_ERROR`, `TDS_UNSUPPORTED_SQL`, `TDS_UNSUPPORTED_TYPE`, `TDS_SIM_UNKNOWN_OBJECT`, `TDS_SIM_INVALID_VALUE`, `TDS_TIMEOUT` ([Fehlercodes](referenz-konfiguration.md#fehlercodes-und-antwortformen)) |
| der Server hängt nach einem Abbruch | das Backend beachtet den Abbruch seiner Coroutine nicht; sein Aufruf bleibt bis zur Rückkehr belegt, die Sitzung schließt nach `CANCEL_TIMEOUT` | suspendierend und unterbrechbar arbeiten |

## 3. Tabellen vorgeben (Simulations-Backend)

**Ziel.** Clients lesen und ändern Tabellen, die die Anwendung als Beschreibung samt Zeilenquelle und Callbacks liefert.

**Voraussetzungen.** Ein laufender Server nach [Abschnitt 1](#1-den-server-in-die-anwendung-einbinden).

**Schritte.**

1. Eine `SimTable` beschreiben: `schema` (Voreinstellung `dbo`), `name`, `columns` (`SimColumn`: Name, `SqlType`, nullbar, `maxLength` für Textspalten, Voreinstellung 4000, -1 für `NVARCHAR(MAX)`), `primaryKey`, die Zeilenquelle `rows` und optional die Callbacks `onInsert`, `onUpdate`, `onDelete` ([Schnittstelle](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends)):

   ```kotlin
   internal fun anleitungTabellen(): BackendSource {
       val spalten = listOf(SimColumn("Id", SqlType.Int64, nullable = false), SimColumn("Name", SqlType.UnicodeText))
       val zeilen = listOf(mapOf("Id" to 1L, "Name" to "Ada"))
       val kunden = SimTable(
           name = "kunden",
           columns = spalten,
           primaryKey = listOf("Id"),
           rows = { query ->
               val offen = zeilen.iterator()
               RowSource { if (offen.hasNext()) offen.next().let { z -> query.columns.map { z[it.name] } } else null }
           },
       )
       return BackendSource.Simulation(listOf(kunden))
   }
   ```

2. Die Zeilenquelle: `rows` bekommt eine `SimQuery` (alle Spalten der Tabelle in der Reihenfolge der Beschreibung, ein Filter als Hinweis zum Vorfiltern) und liefert eine `RowSource`. `next()` liefert die Werte einer Zeile in der Reihenfolge von `SimQuery.columns` oder `null` am Ende, suspendiert und blockiert nicht; `close()` wird genau einmal gerufen, nach dem Ende, einem Fehler oder einem Abbruch.
3. Die Callbacks: `onInsert(values)`, `onUpdate(set, where)` und `onDelete(where)` bekommen die Werte der Anweisung in den Spaltentyp überführt und liefern die Zahl der betroffenen Zeilen; `where = null` heißt alle Zeilen. Jede Anweisung ruft ihren Callback genau einmal.
4. Mit `BackendSource.Simulation(tables)` an `TdsServer.start` übergeben (oder `SimulationBackend(SimCatalog(tables))` als `Custom`).

**Erwartetes Ergebnis.** `SELECT Name FROM kunden` liefert eine Zeile (Test `dasEinbettungsbeispielStartetBedientUndStoppt`). Der Referenzclient liest Auswahl und leere Ergebnisse gegen das Simulations-Backend (`make simulations-integration`, `make kompatibilitaet`); der Beleg-Typ ist der Lauf am Referenzclient gegen den Server dieses Repos, nicht gegen einen echten SQL Server als Gegenstelle der Tabellen.

**Was das Backend erkennt.** Eine feste Liste von Anweisungsformen ([`SPEC-036`](../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig)): `SELECT` mit einfacher Bedingung (`=`, `IN`, `IS [NOT] NULL`, mit `AND`), `INSERT`, `UPDATE`, `DELETE` einfacher Form, `SELECT 1`, `SET`-Anweisungen aus der Liste der Spezifikation, die Transaktionstexte von d-migrate (`SET IMPLICIT_TRANSACTIONS ON|OFF`, `IF @@TRANCOUNT > 0 COMMIT TRAN` und `… ROLLBACK TRAN`), die Katalogabfragen von d-migrate 1.12.0 und `CREATE TABLE` sowie `ALTER TABLE … ADD`. Die Transaktionstexte beantwortet das Backend mit dem Änderungszähler 0; die Bibliothek hat keine Transaktion und nimmt nichts zurück. Alles andere endet mit `TDS_UNSUPPORTED_SQL`; dass die Liste abschließend ist, ist eine Setzung. Die Liste steht nur in der Spezifikation.

**Eigenschaften.**

- `primaryKey` beschreibt nur; er wirkt weder auf das Resultset noch auf die Callbacks und prüft keine Eindeutigkeit. Das Backend speichert nichts.
- Namen sind ohne Beachtung der Groß-/Kleinschreibung eindeutig; jede Tabelle hat mindestens eine Spalte und einen Schlüssel, eine Dezimalspalte höchstens Precision 38. Ein Verstoß ist beim Bau der `SimTable` eine `IllegalArgumentException`.
- Ohne Callback endet Einfügen, Ändern und Löschen mit `TDS_UNSUPPORTED_SQL`.
- Ein Fehler beim Öffnen oder Lesen der Quelle ist `TDS_BACKEND_ERROR` mit fester Meldung, die den Text der Ausnahme nie nennt.
- Die Beschreibung der Tabellen als Datei (YAML im Schema-Modell von d-migrate) liest nur der Testserver; die Bibliothek nimmt `SimTable`-Werte. Wer Tabellen aus einer Datei bauen will, liest sie selbst.

**Typische Fehler.**

| Beobachtung | Ursache | Abhilfe |
|---|---|---|
| `TDS_UNSUPPORTED_SQL` bei `INSERT` | die Tabelle hat keinen `onInsert` | Callback setzen |
| `TDS_UNSUPPORTED_SQL` bei `ORDER BY`, `JOIN`, `OR`, Aggregat | außerhalb der erkannten Formen | die Anweisung vereinfachen oder ein eigenes Backend ([Abschnitt 2](#2-ein-eigenes-backend-anschließen)) |
| `TDS_SIM_INVALID_VALUE` | ein Wert passt nicht verlustfrei in den Spaltentyp | Wert oder Typ ändern |
| `TDS_SIM_UNKNOWN_OBJECT` | Tabelle oder Spalte ist nicht beschrieben | Beschreibung prüfen |
| der Start bricht ab | ein Tabellenname kommt doppelt vor | Namen eindeutig machen |

## 4. Strukturänderungen zulassen (veränderlicher Bestand)

**Ziel.** Clients legen mit `CREATE TABLE` Tabellen an und erweitern sie mit `ALTER TABLE … ADD`; Katalogabfragen (etwa von d-migrate) sehen den jeweils aktuellen Stand.

**Voraussetzungen.** Ein `SimulationBackend` als `BackendSource.Custom`; der Bestand liegt bei der Anwendung.

**Schritte.**

1. Das Backend mit einem Katalog-Anbieter und dem Strukturänderungs-Callback bauen: `SimulationBackend(catalog = { … }, onStructure = { schema, name, spalten, schluessel -> … })`. Der Anbieter liefert bei jeder Anfrage die aktuelle Tabellenliste; das Backend hält keinen Stand. `onStructure` bekommt die Tabelle im Zustand nach der Anweisung (Schema, Name, alle Spalten in der Reihenfolge, Schlüssel):

   ```kotlin
   internal fun anleitungVeraenderlich(): SimulationBackend {
       val bestand = CopyOnWriteArrayList<SimTable>()
       return SimulationBackend(
           catalog = { SimCatalog(bestand.toList()) },
           onStructure = { schema, name, spalten, schluessel ->
               bestand.removeAll { it.schema.equals(schema, ignoreCase = true) && it.name.equals(name, ignoreCase = true) }
               bestand += SimTable(
                   schema = schema,
                   name = name,
                   columns = spalten,
                   primaryKey = schluessel,
                   rows = { RowSource { null } },
               )
           },
       )
   }
   ```

2. Mit `BackendSource.Custom(anleitungVeraenderlich())` starten.

**Erwartetes Ergebnis.** Nach `CREATE TABLE dbo.neu (Id BIGINT NOT NULL, Name NVARCHAR(50) NULL, CONSTRAINT [pk_neu] PRIMARY KEY ([Id]))` liefert `SELECT Id, Name FROM neu` ein Ergebnis ohne Zeile (Test `dieStrukturaenderungLegtEineTabelleAn`). Der Änderungszähler der Strukturanweisung ist 0.

**Eigenschaften.**

- Erkannt sind Spalten der Form `<Name> <Typ> [NOT NULL | NULL]` mit `INT`, `BIGINT`, `BIT`, `DATE`, `TIME`, `DATETIME2`, `NVARCHAR(<n>)` (`n` von 1 bis 4000) oder `NVARCHAR(MAX)` (`maxLength` -1) und `DECIMAL(<p>, <s>)` (Precision bis 38). Eine Schlüsselspalte ist nach der Anweisung nicht nullbar. Ein anderer Typ oder eine weitere Klausel (`DEFAULT`, `IDENTITY`, `UNIQUE`, `WITH`) macht den Text nicht erkannt (`TDS_UNSUPPORTED_SQL`).
- Ohne `onStructure`, und mit dem Konstruktor `SimulationBackend(SimCatalog(tables))` mit festem Katalog, endet jede Strukturänderung mit `TDS_UNSUPPORTED_SQL`.
- Prüfen und Ändern sind je Backend atomar: gleichzeitige Strukturänderungen laufen nacheinander, die zweite sieht die erste (`CREATE TABLE` derselben Tabelle ist dann abgewiesen). Die Sperre gilt für dieses Backend; ein Bestand, den ein anderer Schreiber ändert, bekommt sie nicht. Die Sperre ist ein nicht reentranter `Mutex` (aus dem Quelltext gelesen, nicht gemessen): ein Callback, der dasselbe Backend aufruft und die Antwort abwartet, hängt, und ein hängender Callback blockiert alle weiteren Strukturänderungen dieses Backends.
- Keine Transaktion (Zusage): mehrere Callbacks sind keine Transaktion; bricht eine Folge in der Mitte ab, bleibt die erste Änderung beim Integrator stehen. `ROLLBACK TRAN` ist der Änderungszähler 0. d-migrate meldet nach einem Fehler des Callbacks `FULL_ROLLBACK_CONFIRMED`, obwohl die erste Änderung bleibt (Einzelmessung 2026-11-05, [Spike](../messungen/callback-loesung-spike-2026-11-05.md)).
- Der Katalog zeigt Typ, Länge und Nullbarkeit der Spalten und den Schlüssel; Identität, Standardwerte, Fremdschlüssel, Indizes und Prüfbedingungen kennt er nicht, ihre Abfragen antworten leer. Ein Soll mit dem Typ `identifier` zeigt bei `schema compare` einen Unterschied (Exit 1).
- Belegt am Prüfclient d-migrate 1.12.0 gegen den Testserver ([Messprotokoll](../messungen/katalog-und-ddl-zustand-2026-11-06.md), 2026-11-06, ein Host): `schema reverse`, `schema compare` und `schema migrate --execute` enden mit Exit 0. Die Typnamen und Maße des Katalogs für `bigint`, `bit`, `time` und `datetime2` sind Setzung nach dem Handbuch des Servers; gemessen am echten SQL Server (2022 und 2025, [Messprotokoll zum Referenzabgleich](../messungen/referenzabgleich.md), M-1) sind die Spaltentypen der Katalogsichten, die d-migrate liest.

**Typische Fehler.**

| Beobachtung | Ursache | Abhilfe |
|---|---|---|
| `TDS_UNSUPPORTED_SQL` bei `CREATE TABLE` | kein `onStructure`, fester Katalog, nicht erkannter Typ oder Klausel, Tabelle ohne Schlüssel oder schon vorhanden | Konstruktor mit Anbieter und Callback; Typen nach der Liste |
| `schema migrate` endet mit Exit 5 („Post-execute compare detected drift“) | der Anbieter liefert nach der Änderung den alten Stand | den Bestand im Callback ändern, bevor er zurückkehrt |
| `schema compare` zeigt `text -> text(4000)` | `NVARCHAR(MAX)` ist als 4000 beschrieben | `maxLength = -1` setzen |

## 5. Ereignisse lesen (Senke)

**Ziel.** Ereignisse des Servers (Start, Anmeldungen, Fehler, Sicherheitswarnungen) erscheinen im Log der Anwendung.

**Voraussetzungen.** Ein Server nach [Abschnitt 1](#1-den-server-in-die-anwendung-einbinden).

**Schritte.**

1. Eine `TelemetrySink` implementieren und als dritten Parameter an `TdsServer.start` übergeben (Voreinstellung `TelemetrySink.NONE`: dann entsteht kein Ereignis). Das Beispiel sammelt in eine Liste:

   ```kotlin
   val ereignisse = CopyOnWriteArrayList<TelemetryEvent>()
   val outcome = TdsServer.start(config, anleitungTabellen(), TelemetrySink { ereignisse += it })
   ```

2. Die Ereignisse formatieren und ausgeben, wie die Anwendung es braucht. Die Art steht in `kind` (`EventKind`); Felder und Ebenen: [Referenz der Ereignisse](referenz-ereignisse.md).
3. Die Senke schließt, wer sie übergeben hat.

**Erwartetes Ergebnis.** Nach Start, Anmeldung und `stop()` enthält die Liste `SERVER_STARTED`, `LOGIN_ACCEPTED` und `SERVER_STOPPED` (Test `dasEinbettungsbeispielStartetBedientUndStoppt`).

**Vertrag der Senke** (KDoc von `TelemetrySink`, Tests `TelemetryTest`):

- `accept` blockiert nicht und wirft nicht. Eine blockierende Senke hält den Anfragepfad an und belegt Threads von `Dispatchers.Default`, die andere Coroutinen des Prozesses mitnutzen; davor schützt die Bibliothek nicht.
- Wirft `accept` eine `Exception`, fängt die Bibliothek sie ab und verwirft sie. Eine `CancellationException` wird nicht gefangen; dass auch ein `Error` nicht gefangen wird, ist eine Festlegung.
- Teilen mehrere Server in einer JVM eine Senke, unterscheidet das Ereignis sie nicht: `connection_id` zählt je Server.
- Passwort, Prüfwert, Parameterwert, SQL-Text, Pfad und Ausnahmetext haben im Modell kein Feld; eine Ausnahme erscheint nur als Typname, jede Zeichenkette ist auf 256 UTF-16-Einheiten gekürzt (Tests `TelemetryEventTest`, `TelemetryRedactionTest`).

Für den Testserver gibt es einen fertigen JSON-Adapter; er gehört dem Testserver, nicht der Bibliothek ([Referenz](referenz-ereignisse.md#log-des-testservers), [Anleitung](testserver-manuell.md#4-das-log-lesen)).

**Typische Fehler.**

| Beobachtung | Ursache | Abhilfe |
|---|---|---|
| der Anfragepfad stockt | die Senke blockiert | in der Senke nur übergeben (Warteschlange), nicht schreiben |
| ein `when` über `EventKind` bricht bei der Übersetzung | die Menge der Arten ist je Stand geschlossen, eine neue Art bricht ein vollständiges `when` | ein `else`-Zweig |

## 6. Den Server begrenzen, betreiben und beenden

**Ziel.** Der Server nimmt höchstens eine festgelegte Last an, schützt sich vor Überlauf und endet geordnet.

**Voraussetzungen.** Ein Server nach [Abschnitt 1](#1-den-server-in-die-anwendung-einbinden); die Felder stehen in der [Referenz](referenz-konfiguration.md#konfigurationsfelder).

**Schritte.**

1. Grenzen setzen: `listen.maxConnections` (Voreinstellung 1000), `requests.queryTimeoutSeconds` (30 s), `requests.maxPreparedHandles` (1000), `login.timeoutSeconds` (15 s). Der Listener nimmt höchstens `maxConnections` Verbindungen zugleich auf; eine weitere wird ohne Antwortbytes geschlossen, und nach dem Ende einer Verbindung nimmt er wieder auf.
2. Das Beenden wählen: `requests.shutdownGraceSeconds` (Voreinstellung 10 s, 0 = sofort) und `requests.cancelTimeoutSeconds` (5 s). `stop()` ist das geordnete Beenden ([`SPEC-044`](../../spec/spezifikation.md#spec-044--geordnetes-beenden)):
   - der Listener schließt zuerst;
   - eine Verbindung ohne laufende Anfrage und ohne unvollständig empfangene Nachricht endet sofort;
   - eine Verbindung mit unvollständiger Nachricht bleibt bis zum Ablauf der Frist offen und wird dann getrennt;
   - eine laufende Anfrage darf bis `SHUTDOWN_GRACE` zu Ende laufen, ihre Antwort wird vollständig gesendet, danach endet die Verbindung;
   - jede Anfrage, die nach dem Beginn vollständig wird, erhält `TDS_SHUTDOWN` (ERROR und DONE mit Fehlerstatus), danach endet die Verbindung;
   - nach Ablauf der Frist wird die laufende Anfrage wie bei ATTENTION abgebrochen (genau ein `cancel()` am Backend, die Antwort endet mit `DONE[ATTN]`); beendet das Backend den Abbruch nicht innerhalb von `CANCEL_TIMEOUT`, wird getrennt;
   - `stop()` kehrt zurück, wenn alle Verbindungen beendet sind, spätestens `SHUTDOWN_GRACE` plus `CANCEL_TIMEOUT` nach dem Beginn; ein zweiter Aufruf endet ohne Wirkung.
3. Im Container: die Voreinstellung von `SHUTDOWN_GRACE` plus `CANCEL_TIMEOUT` (15 s) ist länger als die Voreinstellung von `docker stop` (10 s bis SIGKILL; Voreinstellung von Docker, nicht gemessen); `SHUTDOWN_GRACE` dann kleiner setzen.
4. Speicher: die JVM der Anwendung mit `-XX:+ExitOnOutOfMemoryError` starten und von einem Supervisor neu starten lassen, wenn hängende Verbindungen nach einem `OutOfMemoryError` nicht hinnehmbar sind. Ein `OutOfMemoryError` im Anfragepfad wird gefangen und als `TDS_INTERNAL_ERROR` beantwortet, die Verbindung schließt nach der Antwort. Die Bibliothek setzt den Schalter nicht, und einen Supervisor oder Betriebs-Container liefert das Repo nicht ([ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md)).

**Erwartetes Ergebnis.** Verbindungen über der Grenze werden geschlossen, Anfragen über der Frist enden mit `TDS_TIMEOUT` (Verbindung offen), und `stop()` kehrt innerhalb von `SHUTDOWN_GRACE` plus `CANCEL_TIMEOUT` zurück. Endet die Gegenstelle während einer Anfrage (Schließen oder RST), wird die Anfrage am Backend abgebrochen, ihr Strom geschlossen und nichts mehr geschrieben.

**Beleg-Typ.** Die Reihenfolge „ERROR, DONE, dann Schließen“ beim Beenden ist Setzung und am echten SQL Server nicht gemessen. Die Abweisung ohne Antwortbytes und die Wertebereiche von `MAX_CONNECTIONS` und `SHUTDOWN_GRACE` sind Festlegungen (vom Nutzer bestätigt am 2026-11-06). Was der Referenzclient bei einer abgewiesenen Verbindung sieht, ist nicht gemessen. Der Speicher und die Zahlen des Spikes (173 Byte je schmale und 454 Byte je breite Zeile im Heap, 1,54 Mio schmale Zeilen bis zum `OutOfMemoryError` bei 256 MiB; im Spike war nach dem Fangen in vier von vier Läufen nach 20 s mindestens eine Coroutine nicht beendet, mit dem Schalter endete die JVM nach rund 2 s mit Exit-Code 3) sind Messungen eines Spikes mit fingiertem Backend ([ADR-0013](../plan/adr/0013-backend-grenzen.md)), nicht Verhalten des Produkts. Dateirechte und Besitzer der Benutzerliste im Betrieb sind nicht entschieden und nicht gemessen; diese Anleitung empfiehlt keine.

**Typische Fehler.**

| Beobachtung | Ursache | Abhilfe |
|---|---|---|
| `stop()` dauert bis zur Frist | eine Anfrage läuft oder eine Nachricht ist unvollständig | `SHUTDOWN_GRACE` kleiner setzen |
| Verbindung wird sofort geschlossen | `MAX_CONNECTIONS` erreicht | Grenze prüfen oder Last verteilen |
| `TDS_PREPARE_LIMIT` | die Sitzung hält `maxPreparedHandles` Handles | Handles freigeben ([Referenz](referenz-konfiguration.md#prepared-handles)) oder Grenze erhöhen |
| Anfrage endet nach 30 s mit `TDS_TIMEOUT` | Voreinstellung von `queryTimeoutSeconds` | Wert erhöhen |

## Weiterführend

- [Referenz der Konfiguration](referenz-konfiguration.md): Felder, Fehlercodes, Antwortformen, Prepared-Handles, Grenzen.
- [Referenz der Ereignisse](referenz-ereignisse.md): Ereignisarten, Felder, Log des Testservers.
- [Testserver von Hand](testserver-manuell.md), [Kompatibilitätsmatrix](../plan/kompatibilitaetsmatrix.md).
