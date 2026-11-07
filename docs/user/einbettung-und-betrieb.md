# Die Bibliothek einbetten und betreiben

Diese Anleitung führt durch die Aufgaben eines Integrators und eines Betreibers der JVM-Bibliothek `tdswire`: einbinden, ein Backend anschließen, Tabellen vorgeben, Strukturänderungen zulassen, Ereignisse lesen, eine abgelehnte Anmeldung suchen, Benutzerliste und Verschlüsselung einrichten, den Server begrenzen und beenden. Jede Aufgabe hat Ziel, Voraussetzungen, Schritte, erwartetes Ergebnis und typische Fehler. Schlüssel, Ereignisarten, Fehlercodes und Grenzen stehen in der [Referenz der Konfiguration](referenz-konfiguration.md) und der [Referenz der Ereignisse](referenz-ereignisse.md). Den Testserver von Hand bedienen: [Testserver von Hand](testserver-manuell.md). Bezug: [TDS-FA-08](../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-FA-09](../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-QA-05](../../spec/lastenheft.md#tds-qa-05--wartbarkeit).

Das Produkt ist die JVM-Bibliothek. Sie hat keine ausführbare Startklasse und kein Produkt-Image ([ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md)); der Testserver ist ein Prüfwerkzeug, kein Betriebsweg. Den Prozess baut der Integrator aus der Bibliothek. Sie schreibt kein Log und liest weder Umgebung noch Datei; Ereignisse gehen an die Senke des Integrators. Angemeldet wird mit Benutzer und Kennwort aus einer Liste; Windows-Authentifizierung, Kerberos und Entra ID sind ausgeschlossen ([Lastenheft](../../spec/lastenheft.md)).

Die Kotlin-Blöcke, die eine Funktion oder Klasse mit dem Namen `anleitung…` oder `Anleitung…` zeigen, stehen im Source-Set `integrator` in `AnleitungTest` (`src/integrator/kotlin/io/github/pt9912/tdswire/integrator/AnleitungTest.kt`), die Einrückung ausgenommen wörtlich; zehn Deklarationen sind es. Die übrigen Blöcke sind Aufruffragmente (Start, Senke, Rückgabewert von `start`), Shell-Befehle und Auszüge aus der Schnittstelle, die der Test in anderer Form aufruft. Der Test übersetzt die Beispiele gegen die öffentliche Fläche und fährt sie mit `make test` gegen einen Rohbytes-Client, der kein Referenzclient ist; belegt ist, dass die Beispiele übersetzen und die beschriebene Antwort liefern. Kein Gate hält Test und Doku gleich.

## Aufgaben

| Ich möchte … | Abschnitt |
|---|---|
| die Bibliothek in meine Anwendung einbinden und den Server starten und stoppen | [1](#1-den-server-in-die-anwendung-einbinden) |
| mein eigenes Backend anschließen | [2](#2-ein-eigenes-backend-anschließen) |
| Tabellen mit eigenen Zeilen vorgeben (Simulations-Backend) | [3](#3-tabellen-vorgeben-simulations-backend) |
| Tabellen zur Laufzeit anlegen und ändern lassen | [4](#4-strukturänderungen-zulassen-veränderlicher-bestand) |
| Ereignisse des Servers in mein Log bekommen | [5](#5-ereignisse-lesen-senke) |
| herausfinden, warum eine Anmeldung abgelehnt wird | [6](#6-eine-abgelehnte-anmeldung-suchen) |
| Benutzer einrichten und einen `pbkdf2`-Prüfwert erzeugen | [7](#7-benutzer-einrichten-und-einen-pbkdf2-prüfwert-erzeugen) |
| die Verschlüsselung (TLS) einschalten | [8](#8-tls-einschalten) |
| den Server begrenzen, betreiben und geordnet beenden | [9](#9-den-server-begrenzen-betreiben-und-beenden) |
| Metriken und Spans des Servers lesen | [10](#10-metriken-und-spans-lesen) |

## 1. Den Server in die Anwendung einbinden

**Ziel.** Ein Server der Bibliothek läuft im Prozess der Anwendung, nimmt Anmeldungen an und endet auf Aufruf.

**Voraussetzungen.** Docker und GNU `make` zum Bauen (kein lokales JDK, [AGENTS.md](../../AGENTS.md) §3.1); Kotlin mit Coroutines in der Anwendung.

**Schritte.**

1. Jar und `lib/` aus dem Bau holen. `make build` prüft das Jar (Lizenz, Manifest ohne `Main-Class`, `Class-Path` gegen `lib/`, kein Jackson), schreibt es aber nicht auf den Host; die Dateien liegen im Build-Image. Ein Weg aus dem Repo heraus ist kein Target; dieser Handweg mit Docker liefert sie (gefahren am 2026-11-07, Exit 0):

   ```sh
   docker build --target build-jar -t tdswire-build:lokal .
   docker create --name tdswire-jar tdswire-build:lokal
   docker cp tdswire-jar:/src/build/libs/. ./tdswire-libs
   docker rm tdswire-jar
   ```

   Ergebnis: im Verzeichnis `tdswire-libs` liegen `tdswire.jar` und das Unterverzeichnis `lib` mit `annotations-23.0.0.jar`, `kotlin-stdlib-2.4.10.jar` und `kotlinx-coroutines-core-jvm-1.10.1.jar`. Jar und `lib/` gehören nebeneinander auf den Klassenpfad der Anwendung: `lib/` steht im `Class-Path` des Manifests (`SPEC-052`, Festlegung). Jackson ist im Klassenpfad nicht nötig; der Einbettungstest startet und bedient den Server ohne Jackson. Dass ein fremdes Programm das Jar so einbindet, ist nicht gemessen.
2. Die Konfiguration bauen. Das Beispiel nimmt Loopback und Klartext (TDS 7.4, nur für Tests) und liest die Benutzerliste aus dem Text einer Datei, die die Anwendung selbst liest:

   ```kotlin
   import io.github.pt9912.tdswire.config.AuthSettings
   import io.github.pt9912.tdswire.config.EncryptionSetting
   import io.github.pt9912.tdswire.config.ListenSettings
   import io.github.pt9912.tdswire.config.ServerConfig
   import io.github.pt9912.tdswire.config.auth.UserList
   import io.github.pt9912.tdswire.config.auth.UserListResult

   /** Baut die Konfiguration: Loopback, Klartext (nur für Tests), Benutzerliste aus dem Text einer Datei. */
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

   Eine Zeile der Benutzerliste ist `Benutzername<TAB>Prüfwert`, etwa `alice<TAB>{plain}secret`. `{plain}` gilt nur im Testmodus; für den Betrieb [Aufgabe 7](#7-benutzer-einrichten-und-einen-pbkdf2-prüfwert-erzeugen). Die Voreinstellung der Verschlüsselung ist `REQUIRED` mit Schlüsselspeicher ([Aufgabe 8](#8-tls-einschalten)); `TEST_UNENCRYPTED` ist eine ausdrückliche Abschwächung.
3. Starten und das Ergebnis prüfen. `start` ist suspendierend; der Block gehört in eine suspendierende Funktion:

   ```kotlin
   import io.github.pt9912.tdswire.runtime.BackendSource
   import io.github.pt9912.tdswire.runtime.StartOutcome
   import io.github.pt9912.tdswire.runtime.TdsServer

   val config = anleitungKonfiguration(port = 0, benutzerliste = "alice\t{plain}secret\n")
   when (val outcome = TdsServer.start(config, BackendSource.None)) {
       is StartOutcome.Running -> println("Port ${outcome.server.port}")
       is StartOutcome.Aborted -> println(anleitungBefunde(outcome))
   }
   ```

   Die Befunde eines abgebrochenen Starts gibt diese Funktion aus (sie steht im Test):

   ```kotlin
   /** Gibt die Befunde eines abgebrochenen Starts als Text aus; ein laufender Server ergibt eine leere Liste. */
   internal fun anleitungBefunde(outcome: StartOutcome): List<String> = when (outcome) {
       is StartOutcome.Running -> emptyList()
       is StartOutcome.Aborted -> outcome.issues.map { it.message }
   }
   ```

   Das zweite Argument ist eine `BackendSource`: `Custom(backend)` ([Aufgabe 2](#2-ein-eigenes-backend-anschließen)), `Simulation(tables)` ([Aufgabe 3](#3-tabellen-vorgeben-simulations-backend)) oder `None` (wie `Simulation` mit leerer Liste: der Server beantwortet die Anmeldung und lässt eine Anfrage unbeantwortet). `Custom` zusammen mit Tabellen gibt es nicht; ein doppelter Tabellenname bricht den Start ab. Als dritten Parameter nimmt `start` die Senke `TelemetrySink` ([Aufgabe 5](#5-ereignisse-lesen-senke)).
4. Beenden: `outcome.server.stop()` (suspendierend, idempotent).

**Erwartetes Ergebnis.** `StartOutcome.Running`; ein Client kann sich mit Benutzer und Kennwort der Liste an `server.port` anmelden (Port 0 wählt einen freien Port). Nach `stop()` scheitert ein Verbindungsaufbau an den Port (Test `dieGrenzenGeltenUndNachStopNimmtDerPortNichtsMehrAn`). Eine `ServerConfig` gilt für einen Start: der Start löscht das Passwort im Schlüsselmaterial, sobald er es lädt; wer neu startet, baut eine neue Konfiguration. Mit Port 70000 endet der Start mit `StartOutcome.Aborted`; `anleitungBefunde` liefert dann einen Befund, der auf `: OUT_OF_RANGE` endet (Test `einAbgebrochenerStartMeldetDieBefunde`).

**Typische Fehler.**

| Beobachtung | Ursache | Abhilfe |
|---|---|---|
| `StartOutcome.Aborted`, ein Befund `<Schlüssel>: OUT_OF_RANGE`, `MISSING` oder `CONFLICT` | Wert außerhalb des Bereichs, `REQUIRED` ohne Schlüsselmaterial, `TEST_UNENCRYPTED` mit Schlüsselmaterial | Konfiguration korrigieren ([Referenz](referenz-konfiguration.md#konfigurationsfelder)); der Wortlaut des Schlüssels ist keine Zusage |
| `Aborted.reason` ist `Port belegt: <Adresse>:<Port>` | der Port ist vergeben | anderen Port wählen |
| der Fehler wird nicht bemerkt | `start` wirft für Konfigurations-, Tabellen-, TLS- und Bindefehler keine Ausnahme | das Ergebnis immer prüfen |

`Aborted.reason` nennt den Grund ohne Pfad, Ausnahmetext, Schlüsselmaterial und Kennwort; ein Start, der abbricht, bindet keinen Port und startet keinen Thread des Servers. Mit Senke erscheint dieselbe Diagnose als Ereignis. Am Serverprozess des Testservers gemessen (`ZustandsereignisseProzessTest`, Exit-Code 1 des Prozesses):

| Ursache | `Aborted.reason` / Ereignis |
|---|---|
| der Port ist belegt | `Port belegt: <Adresse>:<Port>`; Ereignis `BIND_FAILED` mit `address` und `port` |
| das Zertifikat im Schlüsselspeicher ist abgelaufen, oder die Datei ist kein PKCS#12-Speicher | Ereignis `TLS_START_REJECTED` mit `part` (etwa `CERTIFICATE`) und `form` (etwa `Kette jetzt nicht gueltig`), ohne Pfad und Inhalt |
| die Konfiguration ist unsicher oder ungültig (etwa `{plain}` ohne Testmodus, ein Wert außerhalb des Bereichs) | Ereignis `CONFIG_INVALID` mit `key` und `issueKind`, je Fehler eines |

Ein Start, der gelingt, meldet vor `SERVER_STARTED` genau ein Ereignis `CONFIG_EFFECTIVE` mit den wirksamen Einstellungen (nie Pfad, Prüfwert, Passwort oder Benutzerliste; der Schlüsselspeicher nur als `SET` oder `UNSET`). Die Reihenfolge beider Ereignisse bei festem Port und einem Client, der sofort verbindet, ist nicht gemessen.

**Öffentliche Fläche.** Öffentlich ist, was [`SPEC-059`](../../spec/spezifikation.md#einstieg-des-integrators-vertrag-zu-spec-059) aufzählt; alles andere ist `internal`. `explicitApi` (Modus `strict`) verlangt im Hauptmodul für jede Deklaration eine ausdrückliche Sichtbarkeit, und `make flaeche` hält die Typnamen der Liste gegen die öffentlichen Typdeklarationen und ihre Verwendung im Einbettungstest und im Testserver. Felder, Konstanten und Funktionen vergleicht er nicht; `internal` ist im Bytecode öffentlich, ein Java-Aufrufer sieht es. Maßgeblich ist die Liste der Spezifikation, nicht das, was der Compiler zeigt. Alles in `SPEC-059` ist Festlegung, soweit kein Test genannt ist; belegt sind die Einstiege durch `EmbeddingTest` und `FlaecheEmbeddingTest`.

## 2. Ein eigenes Backend anschließen

**Ziel.** Die Anfragen der Clients beantwortet Code der Anwendung.

**Voraussetzungen.** Ein laufender Server nach [Aufgabe 1](#1-den-server-in-die-anwendung-einbinden).

**Schritte.**

1. `QueryBackend` implementieren (Package `io.github.pt9912.tdswire.port`): `execute(QueryRequest)` liefert eine `QueryExecution` mit einem `Flow` von `QueryEvent`, `cancel(sessionId, requestId)` beendet die laufende Operation. Das Backend kennt weder Pakete noch Tokens. Das Beispiel beantwortet jede Anfrage mit zwei Zeilen:

   ```kotlin
   import io.github.pt9912.tdswire.domain.QueryEvent
   import io.github.pt9912.tdswire.domain.QueryRequest
   import io.github.pt9912.tdswire.domain.ResultColumn
   import io.github.pt9912.tdswire.domain.SqlType
   import io.github.pt9912.tdswire.port.QueryBackend
   import io.github.pt9912.tdswire.port.QueryExecution
   import kotlinx.coroutines.flow.Flow
   import kotlinx.coroutines.flow.asFlow

   /** Ein Backend, das jede Anfrage mit zwei Zeilen einer Textspalte beantwortet. */
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
- Backend und Zeilenquellen sind suspendierend und blockieren den Thread des Aufrufers nicht. Ein blockierender Treiber gehört hinter einen eigenen, begrenzten Pool und wird unterbrechbar aufgerufen; der Server stellt dafür keinen Adapter. Blockweise statt je Zeile aufzurufen ist empfohlen (Setzung). Die Grenzen und Messungen dazu stehen in [ADR-0013](../plan/adr/0013-backend-grenzen.md); es sind Entwurfsmessungen mit einem fingierten Backend, im Repo nicht wiederholbar, und keine Zusage des Produkts.
- Der Server erzwingt keine Grenze für Ergebnisse; ein Backend, das ein Ergebnis materialisiert, begrenzt es selbst. Der Server liest nur so schnell, wie der Transport abnimmt; das gilt für das Test-Backend an einem Draht ohne Netz, nicht für einen Socket, ein blockierendes Backend oder das Simulations-Backend.
- Welche Anweisungen und Typen der Server annimmt, steht in der [Kompatibilitätsmatrix](../plan/kompatibilitaetsmatrix.md).

**Typische Fehler.**

| Beobachtung | Ursache | Abhilfe |
|---|---|---|
| der Client sieht `TDS_INTERNAL_ERROR`, die Verbindung schließt | das Backend warf eine Ausnahme, verletzte die Ereignisreihenfolge (Zeile ohne `ResultStart`, Strom endet im offenen Ergebnis) oder meldete einen Code außerhalb der erlaubten Menge | Ausnahmen als `Failure` melden; erlaubt sind `TDS_BACKEND_ERROR`, `TDS_UNSUPPORTED_SQL`, `TDS_UNSUPPORTED_TYPE`, `TDS_SIM_UNKNOWN_OBJECT`, `TDS_SIM_INVALID_VALUE`, `TDS_SIM_KEY_VIOLATION`, `TDS_TIMEOUT` ([Fehlercodes](referenz-konfiguration.md#fehlercodes-und-antwortformen)) |
| der Client sieht `TDS_SIM_UNKNOWN_OBJECT` und die Verbindung schließt | der Code gehört zur Class 20 (schließt die Verbindung); der echte SQL Server lässt sie offen | die Tabelle der Fehlercodes in der Referenz lesen |
| die Sitzung schließt nach einem Abbruch | das Backend beachtet den Abbruch seiner Coroutine nicht und beendet ihn nicht innerhalb von `CANCEL_TIMEOUT`; sein Aufruf bleibt bis zur Rückkehr belegt | suspendierend und unterbrechbar arbeiten |

## 3. Tabellen vorgeben (Simulations-Backend)

**Ziel.** Clients lesen und ändern Tabellen, die die Anwendung als Beschreibung samt Zeilenquelle und Callbacks liefert.

**Voraussetzungen.** Ein laufender Server nach [Aufgabe 1](#1-den-server-in-die-anwendung-einbinden).

**Schritte.**

1. Eine `SimTable` beschreiben: `schema` (Voreinstellung `dbo`), `name`, `columns` (`SimColumn`: Name, `SqlType`, nullbar, `maxLength` für Textspalten, Voreinstellung 4000, -1 für `NVARCHAR(MAX)`), `primaryKey`, die Zeilenquelle `rows` und optional die Callbacks `onInsert`, `onUpdate`, `onDelete` ([Schnittstelle](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends)):

   ```kotlin
   import io.github.pt9912.tdswire.domain.SqlType
   import io.github.pt9912.tdswire.runtime.BackendSource
   import io.github.pt9912.tdswire.simulation.RowSource
   import io.github.pt9912.tdswire.simulation.SimColumn
   import io.github.pt9912.tdswire.simulation.SimTable

   /** Eine Tabelle `kunden` mit einer Zeile; die Zeilenquelle liefert die Spalten in der Reihenfolge der Anfrage. */
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
3. Die Callbacks: `onInsert(values)`, `onUpdate(set, where)` und `onDelete(where)` bekommen die Werte der Anweisung in den Spaltentyp überführt und liefern die Zahl der betroffenen Zeilen; `where = null` heißt alle Zeilen. Jede Anweisung ruft ihren Callback genau einmal. Fürs Massenladen (`INSERT BULK`, siehe unten) gibt es `onInsertBatch(rows)`: die Zeilen einer Nachricht als Liste von Abbildungen, ein Aufruf je Nachricht. Ein Auszug (Fragment, kein Block des Tests):

   ```kotlin
   SimTable(
       name = "kunden",
       columns = spalten,
       primaryKey = listOf("Id"),
       rows = { query -> /* RowSource wie oben */ },
       onInsertBatch = { zeilen -> speichere(zeilen); zeilen.size.toLong() },
   )
   ```

   `speichere` ist Code der Anwendung. Ohne `onInsertBatch` ruft die Bibliothek `onInsert` je Zeile in der Reihenfolge der Nachricht; die Zahl des Callbacks ist die Zahl der geladenen Zeilen. Eine Tabelle ohne beide Callbacks weist `INSERT BULK` mit `TDS_UNSUPPORTED_SQL` ab. Zuerst werden alle Werte der Nachricht geprüft, dann wird gerufen. Ein Fehler des Callbacks ist `TDS_SIM_KEY_VIOLATION` (Nummer 2627, Class 14); Zeilen, die ein Callback davor angenommen hat, bleiben: es gibt keine Transaktion, atomar ist nur, was die Anwendung im Stapel-Callback atomar macht.
4. Mit `BackendSource.Simulation(tables)` an `TdsServer.start` übergeben (oder `SimulationBackend(SimCatalog(tables))` als `Custom`).

**Erwartetes Ergebnis.** `SELECT Name FROM kunden` liefert eine Zeile (Test `dasEinbettungsbeispielStartetBedientUndStoppt`). Der Referenzclient liest Auswahl und leere Ergebnisse gegen das Simulations-Backend (`make simulations-integration`, `make kompatibilitaet`); der Beleg-Typ ist der Lauf am Referenzclient gegen den Server dieses Repos, nicht gegen einen echten SQL Server als Gegenstelle der Tabellen.

**Was das Backend erkennt.** Eine feste Liste von Anweisungsformen ([`SPEC-036`](../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig)): `SELECT` mit einfacher Bedingung (`=`, `IN`, `IS [NOT] NULL`, mit `AND`), `INSERT`, `UPDATE`, `DELETE` einfacher Form, `SELECT 1`, `SET`-Anweisungen aus der Liste der Spezifikation, die Transaktionstexte von d-migrate (`SET IMPLICIT_TRANSACTIONS ON|OFF`, `IF @@TRANCOUNT > 0 COMMIT TRAN` und `… ROLLBACK TRAN`), die Katalogabfragen von d-migrate 1.12.0, `CREATE TABLE` sowie `ALTER TABLE … ADD` und das Massenladen `INSERT BULK` mit der Nachricht `0x07` samt der Probe der Zieltabelle von `SqlBulkCopy` (Form B). Die Transaktionstexte beantwortet das Backend mit dem Änderungszähler 0; die Bibliothek hat keine Transaktion und nimmt nichts zurück. Alles andere endet mit `TDS_UNSUPPORTED_SQL`; dass die Liste abschließend ist, ist eine Setzung. Die Liste steht nur in der Spezifikation.

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
| `TDS_UNSUPPORTED_SQL` bei `ORDER BY`, `JOIN`, `OR`, Aggregat | außerhalb der erkannten Formen | die Anweisung vereinfachen oder ein eigenes Backend ([Aufgabe 2](#2-ein-eigenes-backend-anschließen)) |
| `TDS_SIM_INVALID_VALUE` | ein Wert passt nicht verlustfrei in den Spaltentyp | Wert oder Typ ändern |
| `TDS_SIM_UNKNOWN_OBJECT`, die Verbindung schließt | Tabelle oder Spalte ist nicht beschrieben | Beschreibung prüfen |
| der Start bricht ab | ein Tabellenname kommt doppelt vor | Namen eindeutig machen |

## 4. Strukturänderungen zulassen (veränderlicher Bestand)

**Ziel.** Clients legen mit `CREATE TABLE` Tabellen an und erweitern sie mit `ALTER TABLE … ADD`; Katalogabfragen (etwa von d-migrate) sehen den jeweils aktuellen Stand.

**Voraussetzungen.** Ein `SimulationBackend` als `BackendSource.Custom`; der Bestand liegt bei der Anwendung.

**Schritte.**

1. Das Backend mit einem Katalog-Anbieter und dem Strukturänderungs-Callback bauen: `SimulationBackend(catalog = { … }, onStructure = { schema, name, spalten, schluessel -> … })`. Der Anbieter liefert bei jeder Anfrage die aktuelle Tabellenliste; das Backend hält keinen Stand. `onStructure` bekommt die Tabelle im Zustand nach der Anweisung (Schema, Name, alle Spalten in der Reihenfolge, Schlüssel):

   ```kotlin
   import io.github.pt9912.tdswire.simulation.RowSource
   import io.github.pt9912.tdswire.simulation.SimCatalog
   import io.github.pt9912.tdswire.simulation.SimTable
   import io.github.pt9912.tdswire.simulation.SimulationBackend
   import java.util.concurrent.CopyOnWriteArrayList

   /** Das Backend liest den Bestand bei jeder Anfrage aus `bestand`; `CREATE TABLE` trägt die neue Tabelle ein. */
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

**Erwartetes Ergebnis.** Nach `CREATE TABLE dbo.neu (Id BIGINT NOT NULL, Name NVARCHAR(50) NULL, CONSTRAINT [pk_neu] PRIMARY KEY ([Id]))` liefert `SELECT Id, Name FROM neu` ein Ergebnis ohne Zeile (Test `dieStrukturaenderungLegtEineTabelleAn`). Der Änderungszähler der Strukturanweisung ist 0 (Setzung; gemessen ist der Zähler 0 nur für `ROLLBACK TRAN`).

**Eigenschaften.**

- Erkannt sind Spalten der Form `<Name> <Typ> [NOT NULL | NULL]` mit `INT`, `BIGINT`, `BIT`, `DATE`, `TIME`, `DATETIME2`, `NVARCHAR(<n>)` (`n` von 1 bis 4000) oder `NVARCHAR(MAX)` (`maxLength` -1) und `DECIMAL(<p>, <s>)` (Precision bis 38). Eine Schlüsselspalte ist nach der Anweisung nicht nullbar. Ein anderer Typ oder eine weitere Klausel (`DEFAULT`, `IDENTITY`, `UNIQUE`, `WITH`) macht den Text nicht erkannt (`TDS_UNSUPPORTED_SQL`).
- Ohne `onStructure`, und mit dem Konstruktor `SimulationBackend(SimCatalog(tables))` mit festem Katalog, endet jede Strukturänderung mit `TDS_UNSUPPORTED_SQL`.
- Prüfen und Ändern sind je Backend atomar: gleichzeitige Strukturänderungen laufen nacheinander, die zweite sieht die erste (`CREATE TABLE` derselben Tabelle ist dann abgewiesen). Die Sperre gilt für dieses Backend; ein Bestand, den ein anderer Schreiber ändert, bekommt sie nicht. Die Sperre ist ein nicht reentranter `Mutex` (aus dem Quelltext gelesen, nicht gemessen): ein Callback, der dasselbe Backend aufruft und die Antwort abwartet, hängt, und ein hängender Callback blockiert alle weiteren Strukturänderungen dieses Backends.
- Keine Transaktion (Zusage): mehrere Callbacks sind keine Transaktion; bricht eine Folge in der Mitte ab, bleibt die erste Änderung beim Integrator stehen. `ROLLBACK TRAN` ist der Änderungszähler 0. d-migrate meldet nach einem Fehler des Callbacks `FULL_ROLLBACK_CONFIRMED`, obwohl die erste Änderung bleibt (Einzelmessung 2026-11-05, [Messprotokoll](../messungen/callback-loesung-spike-2026-11-05.md)).
- Der Katalog zeigt Typ, Länge und Nullbarkeit der Spalten und den Schlüssel; Identität, Standardwerte, Fremdschlüssel, Indizes und Prüfbedingungen kennt er nicht, ihre Abfragen antworten leer. Ein Soll mit dem Typ `identifier` zeigt bei `schema compare` einen Unterschied (Exit 1).
- Belegt am Prüfclient d-migrate 1.12.0 gegen den Testserver ([Messprotokoll](../messungen/katalog-und-ddl-zustand-2026-11-06.md), 2026-11-06, ein Host): `schema reverse`, `schema compare` und `schema migrate --execute` enden mit Exit 0; die Schrittfolge steht in [Testserver, Aufgabe 3](testserver-manuell.md#3-mit-d-migrate-struktur-lesen-und-ändern). Die Typnamen und Maße des Katalogs für `bigint`, `bit`, `time` und `datetime2` sind Setzung nach dem Handbuch des Servers; gemessen am echten SQL Server (2022 und 2025, [Messprotokoll zum Referenzabgleich](../messungen/referenzabgleich.md), M-1) sind die Spaltentypen der Katalogsichten, die d-migrate liest.

**Typische Fehler.**

| Beobachtung | Ursache | Abhilfe |
|---|---|---|
| `TDS_UNSUPPORTED_SQL` bei `CREATE TABLE` | kein `onStructure`, fester Katalog, nicht erkannter Typ oder Klausel, Tabelle ohne Schlüssel oder schon vorhanden | Konstruktor mit Anbieter und Callback; Typen nach der Liste |
| `schema migrate` endet mit Exit 5 („Post-execute compare detected drift“) | der Anbieter liefert nach der Änderung den alten Stand | den Bestand im Callback ändern, bevor er zurückkehrt |
| `schema compare` zeigt `text -> text(4000)` | `NVARCHAR(MAX)` ist als 4000 beschrieben | `maxLength = -1` setzen |

## 5. Ereignisse lesen (Senke)

**Ziel.** Ereignisse des Servers (Start, Anmeldungen, Fehler, Sicherheitswarnungen) erscheinen im Log der Anwendung.

**Voraussetzungen.** Ein Server nach [Aufgabe 1](#1-den-server-in-die-anwendung-einbinden).

**Schritte.**

1. Eine Funktion schreiben, die ein Ereignis in eine Zeile fasst. Felder, die die Art nicht trägt, sind `null` und entfallen:

   ```kotlin
   import io.github.pt9912.tdswire.domain.telemetry.TelemetryEvent

   /** Eine Zeile je Ereignis; Felder, die die Art nicht trägt, entfallen. */
   internal fun anleitungZeile(event: TelemetryEvent): String = listOfNotNull(
       event.at.toString(),
       event.level.name,
       event.kind.name,
       event.connectionId?.let { "connection_id=$it" },
       event.reason?.let { "reason=$it" },
       event.errorCode?.let { "error_code=$it" },
       event.principal?.let { "principal=$it" },
   ).joinToString(" ")
   ```

2. Die Senke übergibt die Zeile an eine Warteschlange, ein eigener Thread schreibt sie in das Log der Anwendung. `accept` blockiert so nicht (Vertrag unten):

   ```kotlin
   import io.github.pt9912.tdswire.domain.telemetry.TelemetrySink
   import java.util.concurrent.LinkedBlockingQueue

   val zeilen = LinkedBlockingQueue<String>()
   val sink = TelemetrySink { zeilen.offer(anleitungZeile(it)) }
   val outcome = TdsServer.start(config, BackendSource.None, sink)
   ```

3. Die Senke schließt, wer sie übergeben hat. Ohne Senke (Voreinstellung `TelemetrySink.NONE`) entsteht kein Ereignis.

**Erwartetes Ergebnis.** Nach einer abgelehnten Anmeldung steht in der Warteschlange eine Zeile `<Zeitpunkt> WARN LOGIN_REJECTED connection_id=1 reason=WRONG_PASSWORD` (Test `eineAbgelehnteAnmeldungErscheintMitGrundInDerZeile`, geprüft auf `LOGIN_REJECTED`, `reason=WRONG_PASSWORD` und `connection_id=1`). Nach Start, Anmeldung und `stop()` liefert eine Senke, die alle Ereignisse sammelt, unter anderem `CONFIG_EFFECTIVE`, `SERVER_STARTED`, `LOGIN_ACCEPTED`, `SERVER_STOPPING` und `SERVER_STOPPED` (geprüft sind `SERVER_STARTED`, `LOGIN_ACCEPTED` und `SERVER_STOPPED`) (Test `dasEinbettungsbeispielStartetBedientUndStoppt`). Arten und Felder: [Referenz der Ereignisse](referenz-ereignisse.md).

**Vertrag der Senke** (KDoc von `TelemetrySink`, Tests `TelemetryTest`):

- `accept` blockiert nicht und wirft nicht. Eine blockierende Senke hält den Anfragepfad an und belegt Threads von `Dispatchers.Default`, die andere Coroutinen des Prozesses mitnutzen; davor schützt die Bibliothek nicht.
- `accept` ruft `TdsServer.stop` nicht: bei `SERVER_STOPPING` und `SERVER_STOPPED` kehrt ein solcher Aufruf nicht zurück (die Sperre von `stop` ist nicht wiedereintrittsfähig; gemessen mit einer Zeitgrenze von 3 s, kein Test im Repo).
- Wirft `accept` eine `Exception`, fängt die Bibliothek sie ab und verwirft sie. Eine `CancellationException` wird nicht gefangen; dass auch ein `Error` nicht gefangen wird, ist eine Festlegung.
- Das Ende eines Spans läuft auf dem Anfragepfad, der Schnappschuss der Metriken in einer eigenen Aufgabe; blockiert die Senke dort, hält das den Anfragepfad an, und `stop` wartet auf einen laufenden Schnappschuss ([Aufgabe 10](#10-metriken-und-spans-lesen)).
- Teilen mehrere Server in einer JVM eine Senke, unterscheidet das Ereignis sie nicht: `connection_id` zählt je Server.
- Passwort, Prüfwert, Parameterwert, SQL-Text, Pfad und Ausnahmetext haben im Modell kein Feld; eine Ausnahme erscheint nur als Typname, jede Zeichenkette ist auf 256 UTF-16-Einheiten gekürzt (Tests `TelemetryEventTest`, `TelemetryRedactionTest`).

Für den Testserver gibt es einen fertigen JSON-Adapter; er gehört dem Testserver, nicht der Bibliothek ([Referenz](referenz-ereignisse.md#log-des-testservers), [Anleitung](testserver-manuell.md#4-das-log-lesen)).

**Typische Fehler.**

| Beobachtung | Ursache | Abhilfe |
|---|---|---|
| der Anfragepfad stockt | die Senke blockiert | in der Senke nur übergeben (Warteschlange), nicht schreiben |
| ein `when` über `EventKind` bricht bei der Übersetzung | die Menge der Arten ist je Stand geschlossen, eine neue Art bricht ein vollständiges `when` | ein `else`-Zweig |

## 6. Eine abgelehnte Anmeldung suchen

**Ziel.** Sie finden den Grund, aus dem ein Client sich nicht anmelden kann.

**Voraussetzungen.** Eine Senke nach [Aufgabe 5](#5-ereignisse-lesen-senke). Der Client sieht nur die Fehlermeldung `Login failed.` mit Nummer 18456 (Class 14, State 1); sie ist für alle Gründe gleich und verrät nicht, ob Benutzer oder Kennwort falsch war ([Referenz](referenz-konfiguration.md#fehlercodes-und-antwortformen)). Den Grund nennt allein die Senke.

**Schritte.**

1. Den Anmeldeversuch wiederholen und in den Zeilen der Senke die Art `LOGIN_REJECTED` suchen. Das Feld `reason` nennt den Grund.
2. Den Grund nachschlagen:

   | `reason` | Ursache | Abhilfe |
   |---|---|---|
   | `UNKNOWN_USER` | der Benutzername steht nicht in der Liste | Zeile `Benutzername<TAB>Prüfwert` ergänzen ([Aufgabe 7](#7-benutzer-einrichten-und-einen-pbkdf2-prüfwert-erzeugen)) |
   | `WRONG_PASSWORD` | das Kennwort passt nicht zum Prüfwert | Kennwort des Clients prüfen oder den Prüfwert neu erzeugen |
   | `INPUT_TOO_LONG` | Benutzername oder Kennwort länger als 128 UTF-16-Einheiten, oder ein zu langer Datenbankname | kürzere Eingabe |
   | `UNSUPPORTED_INTEGRATED_SECURITY` | der Client verlangt Windows-Authentifizierung | auf Anmeldung mit Benutzer und Kennwort umstellen (etwa `User ID` und `Password` in der Zeichenfolge) |
   | `UNSUPPORTED_FEDERATED_AUTHENTICATION` | der Client verlangt Entra ID | wie oben |
   | `UNSUPPORTED_PASSWORD_CHANGE` | der Client verlangt eine Kennwortänderung | ohne Kennwortänderung anmelden |
   | `ADAPTER_FAILURE` | der Authentifizierungsadapter scheiterte; das Ereignis `ADAPTER_FAILED` nennt den Typnamen der Ausnahme | den Adapter prüfen |

3. Findet sich kein `LOGIN_REJECTED`, die Ereignisse zur Verbindung (`connection_id`) davor lesen: `ENCRYPTION_NOT_OFFERED` (der Client bietet im PRELOGIN keine Verschlüsselung an, die Richtlinie verlangt sie), `TLS_FIRST_NOT_OFFERED` (der Client beginnt mit TLS, der Server bietet es nicht an; `reason` `NO_TLS_MATERIAL` oder `POLICY_NOT_REQUIRED`), `TLS_HANDSHAKE_FAILED` (der Handschlag scheiterte; `failure` nennt die Art).

**Erwartetes Ergebnis.** Die Zeile `LOGIN_REJECTED … reason=WRONG_PASSWORD` für ein falsches Kennwort (Test `eineAbgelehnteAnmeldungErscheintMitGrundInDerZeile`; am Testserver: `{"level":"WARN","event":"login_rejected","connection_id":2,"client_profile":"unknown","reason":"WRONG_PASSWORD"}`, Lauf vom 2026-11-07).

**Typische Fehler.**

| Beobachtung | Ursache | Abhilfe |
|---|---|---|
| die Senke bleibt leer | `TelemetrySink.NONE` (Voreinstellung) | Senke übergeben |
| der Client meldet 18456, die Senke `LOGIN_ACCEPTED` für einen anderen Benutzer | der Client nutzt andere Zugangsdaten als angenommen | Zeichenfolge des Clients prüfen |

## 7. Benutzer einrichten und einen pbkdf2-Prüfwert erzeugen

**Ziel.** Die Benutzerliste enthält Prüfwerte statt Klartextkennwörter; der Betrieb mit `REQUIRED` ist damit möglich.

**Voraussetzungen.** Die Anwendung läuft auf einer JVM (das Beispiel nutzt `PBKDF2WithHmacSHA256` des JDK). Das Verfahren `{plain}` ist im Betrieb nicht zugelassen: `UserList.parse(text, plainTestMode = false)` weist es ab.

**Schritte.**

1. Den Prüfwert erzeugen. Er hat die Form `{pbkdf2-sha256}Salz$Iterationen$Hash` mit Salz (mindestens 16 Byte) und Hash (32 Byte) als Standard-Base64 mit Padding; die Iterationen liegen zwischen 210000 und 10000000 (das Beispiel nimmt 600000). PBKDF2 mit HMAC-SHA-256 des JDK:

   ```kotlin
   import java.security.SecureRandom
   import java.util.Base64
   import javax.crypto.SecretKeyFactory
   import javax.crypto.spec.PBEKeySpec

   private const val SALT_BYTES = 16
   private const val HASH_BYTES = 32
   private const val BITS_PER_BYTE = 8
   private const val ITERATIONEN = 600_000

   /** Der Prüfwert `{pbkdf2-sha256}Salz$Iterationen$Hash` für [kennwort]; Salz und Hash sind Standard-Base64. */
   internal fun anleitungPruefwert(kennwort: String): String {
       val salz = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
       val spec = PBEKeySpec(kennwort.toCharArray(), salz, ITERATIONEN, HASH_BYTES * BITS_PER_BYTE)
       val hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
       spec.clearPassword()
       val base64 = Base64.getEncoder()
       val felder = listOf(base64.encodeToString(salz), ITERATIONEN.toString(), base64.encodeToString(hash))
       return "{pbkdf2-sha256}" + felder.joinToString("$")
   }
   ```

2. Die Zeile der Liste schreiben: `bob<TAB>` plus der Prüfwert, eine Zeile je Benutzer, UTF-8, Zeilenende `\n` (keine Byte-Order-Mark).
3. Die Liste lesen und die Einstellung bauen. Der Text der Datei geht an `UserList.parse`; ohne `{plain}` ist der Testmodus aus:

   ```kotlin
   import io.github.pt9912.tdswire.config.AuthSettings
   import io.github.pt9912.tdswire.config.auth.UserList
   import io.github.pt9912.tdswire.config.auth.UserListResult
   import kotlin.test.assertIs

   /** Die Benutzerliste aus dem Text einer Datei; [plain] gilt nur im Testmodus. */
   internal fun anleitungBenutzer(text: String, plain: Boolean): AuthSettings {
       val parsed = UserList.parse(text, plainTestMode = plain)
       return AuthSettings(assertIs<UserListResult.Valid>(parsed).list, plainTestMode = plain)
   }
   ```

   In der Anwendung ersetzt eine `when`-Verzweigung wie in [Aufgabe 1](#1-den-server-in-die-anwendung-einbinden) das `assertIs` des Tests; `Invalid` trägt einen `UserListError` mit Art und Zeile.

**Erwartetes Ergebnis.** Ein Client mit `bob` und dem Kennwort meldet sich an; mit falschem Kennwort wird die Anmeldung abgelehnt (Test `einPruefwertMitPbkdf2MeldetDenBenutzerAn`, Rohbytes-Client, `TEST_UNENCRYPTED`). Ob ein Client unter `REQUIRED` mit TLS und demselben Prüfwert anmeldet, ist am Testserver gefahren: [Testserver, Aufgabe 5](testserver-manuell.md#5-den-server-anders-einstellen).

**Typische Fehler.**

| Beobachtung | Ursache | Abhilfe |
|---|---|---|
| `UserListResult.Invalid` | Format verletzt (Verfahren fehlt oder unbekannt, Salz zu kurz, Iterationen außerhalb des Bereichs, Hash nicht 32 Byte, `{plain}` ohne Testmodus) | `UserListError.kind` und Zeile lesen; Format in der [Referenz](referenz-konfiguration.md#benutzerliste) |

Dateirechte und Besitzer der Benutzerliste setzt die Bibliothek nicht; sie liest nur den Text, den die Anwendung übergibt. Zur Datei im Betrieb macht diese Anleitung keine Aussage.

## 8. TLS einschalten

**Ziel.** Der Server verlangt Verschlüsselung (`REQUIRED`, die Voreinstellung) und weist Clients ohne sie ab.

**Voraussetzungen.** Ein PKCS#12-Speicher mit einem privaten Schlüssel und Zertifikat, sein Kennwort, und eine Benutzerliste mit `pbkdf2`-Prüfwerten ([Aufgabe 7](#7-benutzer-einrichten-und-einen-pbkdf2-prüfwert-erzeugen)).

**Schritte.**

1. Einen Speicher erzeugen (nur, wenn keiner vorliegt; ein selbst erzeugtes Zertifikat taugt für Tests). Das Werkzeug `keytool` des JDK läuft im gepinnten Gradle-Image des Repos (gefahren am 2026-11-07, Exit 0):

   ```sh
   docker run --rm --user "$(id -u):$(id -g)" -v "$PWD:/work" -w /work \
     gradle:8.14-jdk21@sha256:b3453e88792ea90b526f5d04fa2961bba183ae57ded4ad3437a49fb4e8381c06 \
     keytool -genkeypair -alias server -keyalg RSA -keysize 2048 -validity 365 -storetype PKCS12 \
       -keystore server.p12 -storepass speicher-geheim -dname "CN=localhost" -ext "san=dns:localhost,ip:127.0.0.1"
   ```

   Ergebnis: die Datei `server.p12` im Arbeitsverzeichnis. Das Kennwort `speicher-geheim` ist ein Testwert.
2. Die Konfiguration mit `REQUIRED` und dem Speicher bauen:

   ```kotlin
   import io.github.pt9912.tdswire.config.TlsKeyMaterial
   import io.github.pt9912.tdswire.config.auth.PasswordChars
   import java.nio.file.Files
   import java.nio.file.Path

   /** `REQUIRED` mit dem PKCS#12-Speicher aus [speicher] und seinem Kennwort. */
   internal fun anleitungTls(speicher: Path, kennwort: String, auth: AuthSettings, port: Int): ServerConfig = ServerConfig(
       listen = ListenSettings("127.0.0.1", port),
       encryption = EncryptionSetting.REQUIRED,
       tls = TlsKeyMaterial(Files.readAllBytes(speicher), PasswordChars(kennwort.toCharArray())),
       auth = auth,
   )
   ```

3. Starten wie in [Aufgabe 1](#1-den-server-in-die-anwendung-einbinden). Der Start führt vor dem Binden einen vollständigen TLS-Handschlag im Speicher aus (`SPEC-043`).

**Erwartetes Ergebnis.** `StartOutcome.Running` (Test `tlsMitEinemPkcs12SpeicherStartetMitRequired`: der Test erzeugt den Speicher mit dem `keytool` des JDK und startet mit `REQUIRED`). Am Testserver gefahren am 2026-11-07: ein TLS-Client (d-migrate 1.12.0 mit `encrypt=true&trustServerCertificate=true`) meldet `bob` an; das Ereignis `LOGIN_ACCEPTED` trägt `client_profile` `tds-7.4:required` ([Testserver, Aufgabe 5](testserver-manuell.md#5-den-server-anders-einstellen)). Mit `REQUIRED` entsteht kein Ereignis `SECURITY_WEAKENED`. Der Server bietet im TDS-7.4-Pfad höchstens TLS 1.2, im TDS-8.0-Pfad TLS 1.3 an (`SPEC-023`).

**Typische Fehler.**

| Beobachtung | Ursache | Abhilfe |
|---|---|---|
| `Aborted`, Befund `MISSING` | `REQUIRED` ohne `tls` | `TlsKeyMaterial` übergeben |
| `Aborted`, Befund `CONFLICT` | `TEST_UNENCRYPTED` zusammen mit `tls` | eines von beiden |
| `TLS_START_REJECTED` (Ereignis), `Aborted.reason` ohne Pfad | der Speicher ist unlesbar, das Kennwort falsch oder die Startprobe scheiterte | Speicher und Kennwort prüfen; `part` und `form` des Ereignisses nennen den Teil |
| ein Client bricht den Handschlag ab („unexpected message“) | die Ticket-Zahl der JDK-Systemeigenschaften `jdk.tls.server.*` ist auf 0 gesetzt | den Wert nicht auf 0 setzen ([Referenz](referenz-konfiguration.md#konfigurationsfelder)) |

## 9. Den Server begrenzen, betreiben und beenden

**Ziel.** Der Server nimmt höchstens eine festgelegte Last an, schützt sich vor Überlauf und endet geordnet.

**Voraussetzungen.** Ein Server nach [Aufgabe 1](#1-den-server-in-die-anwendung-einbinden); die Felder stehen in der [Referenz](referenz-konfiguration.md#konfigurationsfelder).

**Schritte.**

1. Grenzen und Fristen in der Konfiguration setzen:

   ```kotlin
   import io.github.pt9912.tdswire.config.LoginSettings
   import io.github.pt9912.tdswire.config.RequestSettings

   /** Höchstens 50 Verbindungen, Anmeldefrist 5 s, Anfragefrist 10 s, 200 Handles je Sitzung, geordnetes Beenden 3 s. */
   internal fun anleitungGrenzen(auth: AuthSettings): ServerConfig = ServerConfig(
       listen = ListenSettings("127.0.0.1", 0, maxConnections = 50),
       login = LoginSettings(timeoutSeconds = 5),
       encryption = EncryptionSetting.TEST_UNENCRYPTED,
       auth = auth,
       requests = RequestSettings(
           queryTimeoutSeconds = 10,
           cancelTimeoutSeconds = 2,
           shutdownGraceSeconds = 3,
           maxPreparedHandles = 200,
       ),
   )
   ```

   Der Listener nimmt höchstens `maxConnections` Verbindungen zugleich auf; eine weitere wird ohne Antwortbytes geschlossen, und nach dem Ende einer Verbindung nimmt er wieder auf. Eine Anfrage über `queryTimeoutSeconds` endet mit `TDS_TIMEOUT` (Verbindung offen). Eine Nachricht nach dem Login ist höchstens `requests.maxMessageBytes` groß (Voreinstellung 16 MiB, 65535 bis 268435456; die Summe der Paketlängen einer Nachricht); die Protokollgrenze eines Pakets (`MAX_TDS_PACKET_BYTES`, 65535) ist keine Einstellung. Eine Sitzung hält höchstens `maxPreparedHandles` Handles ([Referenz](referenz-konfiguration.md#prepared-handles)).
2. Beenden mit `server.stop()`. Das ist das geordnete Beenden ([`SPEC-044`](../../spec/spezifikation.md#spec-044--geordnetes-beenden)):
   - der Listener schließt zuerst;
   - eine Verbindung ohne laufende Anfrage und ohne unvollständig empfangene Nachricht endet sofort;
   - eine Verbindung mit unvollständiger Nachricht bleibt bis zum Ablauf der Frist offen und wird dann getrennt;
   - eine laufende Anfrage darf bis `shutdownGraceSeconds` zu Ende laufen, ihre Antwort wird vollständig gesendet, danach endet die Verbindung;
   - jede Anfrage, die nach dem Beginn vollständig wird, erhält `TDS_SHUTDOWN` (ERROR und DONE mit Fehlerstatus), danach endet die Verbindung;
   - nach Ablauf der Frist wird die laufende Anfrage wie bei ATTENTION abgebrochen (genau ein `cancel()` am Backend, die Antwort endet mit `DONE[ATTN]`); beendet das Backend den Abbruch nicht innerhalb von `cancelTimeoutSeconds`, wird getrennt;
   - mit Senke melden `SERVER_STOPPING` den Beginn, `CONNECTION_CLOSED` (mit `reason`, etwa `SHUTDOWN`) jede Verbindung und `SERVER_STOPPED` das Ende; `stop()` kehrt erst zurück, nachdem das Ereignis zum Ende an die Senke gegeben wurde; zwei nebenläufige Aufrufe warten aufeinander;
   - `stop()` kehrt zurück, wenn alle Verbindungen beendet sind, spätestens `shutdownGraceSeconds` plus `cancelTimeoutSeconds` nach dem Beginn; ein zweiter Aufruf endet ohne Wirkung. Beachtet das Backend den Abbruch nicht, bleibt sein Aufruf bis zu seiner Rückkehr belegt.
3. Im Container: die Voreinstellung von `SHUTDOWN_GRACE` plus `CANCEL_TIMEOUT` (15 s) ist länger als die Voreinstellung von `docker stop` (10 s bis SIGKILL; Voreinstellung von Docker, nicht gemessen); `shutdownGraceSeconds` dann kleiner setzen.
4. Speicher: die JVM der Anwendung mit `-XX:+ExitOnOutOfMemoryError` starten und von einem Supervisor neu starten lassen, wenn hängende Verbindungen nach einem `OutOfMemoryError` nicht hinnehmbar sind. Ein `OutOfMemoryError` im Anfragepfad wird gefangen und als `TDS_INTERNAL_ERROR` beantwortet, die Verbindung schließt nach der Antwort. Die Bibliothek setzt den Schalter nicht; einen Supervisor oder Betriebs-Container liefert das Repo nicht ([ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md)). Zur Messlage ([ADR-0013](../plan/adr/0013-backend-grenzen.md)): ob das Fangen allein hängende Verbindungen hinterlässt, und ob der Schalter es ersetzt, ist am Produkt nicht gemessen.

**Erwartetes Ergebnis.** Die Konfiguration startet (Test `dieGrenzenGeltenUndNachStopNimmtDerPortNichtsMehrAn`: Start, Anmeldung, `stop()`, danach scheitert der Verbindungsaufbau). Verbindungen über der Grenze werden geschlossen, Anfragen über der Frist enden mit `TDS_TIMEOUT`. Endet die Gegenstelle während einer Anfrage (Schließen oder RST), wird die Anfrage am Backend abgebrochen, ihr Strom geschlossen und nichts mehr geschrieben.

**Beleg-Typ.** Die Grenzen selbst sind Festlegungen; der Test belegt, dass die Konfiguration übersetzt, startet und `stop()` den Port schließt, nicht das Abweisen der 51. Verbindung. Die Reihenfolge „ERROR, DONE, dann Schließen“ beim Beenden ist Setzung und am echten SQL Server nicht gemessen. Die Abweisung ohne Antwortbytes und die Wertebereiche von `MAX_CONNECTIONS` und `SHUTDOWN_GRACE` sind Festlegungen (vom Nutzer bestätigt am 2026-11-06). Was der Referenzclient bei einer abgewiesenen Verbindung sieht, ist nicht gemessen.

**Typische Fehler.**

| Beobachtung | Ursache | Abhilfe |
|---|---|---|
| `stop()` dauert bis zur Frist | eine Anfrage läuft oder eine Nachricht ist unvollständig | `shutdownGraceSeconds` kleiner setzen |
| Verbindung wird sofort geschlossen | `maxConnections` erreicht | Grenze prüfen oder Last verteilen |
| `TDS_PREPARE_LIMIT` | die Sitzung hält `maxPreparedHandles` Handles | Handles freigeben ([Referenz](referenz-konfiguration.md#prepared-handles)) oder Grenze erhöhen |
| Anfrage endet nach 30 s mit `TDS_TIMEOUT` | Voreinstellung von `queryTimeoutSeconds` | Wert erhöhen |

## 10. Metriken und Spans lesen

**Ziel.** Der Server liefert Zähler und Latenz-Perzentile im festen Abstand und das Ende jedes Spans (`tds.connection`, `tds.query`, `backend.execute`) an die Senke. Es gibt weder einen Endpunkt noch JMX noch eine Telemetrie-Bibliothek: der Zugang ist die Senke.

**Voraussetzungen.** Ein Server mit Senke nach [Aufgabe 5](#5-ereignisse-lesen-senke). Ohne Senke entsteht nichts.

**Schritte.**

1. Den Abstand der Schnappschüsse in `ServerConfig.requests` setzen (Voreinstellung 60 s, 0 schaltet die Schnappschüsse aus, Bereich 0 bis 86400). `RequestSettings` hat mehrere Ganzzahl-Felder; die Felder benennen, nicht nach Position übergeben:

   ```kotlin
   requests = RequestSettings(metricsIntervalSeconds = 10)
   ```

2. In der Senke nach der Art unterscheiden: `EventKind.METRICS` trägt `metrics` (ganze Zahlen nach Schlüssel), `EventKind.SPAN_ENDED` trägt `span`, `outcome`, `durationMs`, `requestId`, `requestKind` und `phase`. Das Ende eines Spans hat die Ebene `DEBUG`, der Schnappschuss `INFO`.
3. Raten (Anfragen je Sekunde, Quote der Anmeldungen) aus zwei Schnappschüssen bilden: die Zähler `*_total` wachsen seit dem Start.

**Erwartetes Ergebnis** (Handlauf des Testservers mit `LOG_LEVEL=DEBUG`, Rohbytes-Client, 2026-11-07; der Client stellte keine Anfrage, deshalb fehlen Spans `tds.query`; siehe die Zeilen in der [Referenz](referenz-ereignisse.md#log-des-testservers)): nach der Anmeldung ein `span_ended` mit `span` = `tds.connection`, `outcome` = `ok`, `phase` = `session`, danach ein `metrics` mit `metrics.logins_succeeded_total` = 1 und `metrics.spans_open` = 0. `tds.query` und `backend.execute` tragen dieselbe `requestId` wie die `QueryRequest` des Backends (Test `MetricsTracingWiringTest`).

**Was die Werte bedeuten.**

- Die Schlüssel, die Werte von `outcome` und die Klassengrenzen der Perzentile stehen in der [Referenz](referenz-ereignisse.md#ereignisarten) und in [Abschnitt 5 der Spezifikation](../../spec/spezifikation.md#5-metriken-und-tracing-felder); sie sind Festlegungen, nicht gemessen.
- Ein Perzentil ist die Obergrenze der Klasse, in der sein Rang liegt, nicht die gemessene Dauer; es fehlt, bis die erste Anfrage endet.
- Kein Span und keine Metrik trägt SQL-Text, Anmeldekennung oder Parameterwert; die Schlüssel hängen nicht von den Anfragen ab (Test mit 200 verschiedenen Texten).

**Grenzen.**

- Eine Senke, die blockiert, hält den Anfragepfad an, auch am Ende eines Spans; `stop` wartet auf einen laufenden Schnappschuss und kehrt erst nach der Senke zurück.
- Wirft die Senke beim Schnappschuss einen `Error` oder eine `CancellationException`, endet der Zeitgeber, und es folgt bis zum nächsten Start kein `METRICS` mehr; Anfragen laufen weiter.
- Overhead und Kosten der Aggregation unter Last sind nicht gemessen. Ein Bulk-Load-Paket erscheint als `sql_batch` (Setzung); die Kennung der Anfrage trägt nur das Ende der Spans, nicht `REQUEST_FAILED`.

**Typische Fehler.**

| Beobachtung | Ursache | Abhilfe |
|---|---|---|
| kein `METRICS` | keine Senke, Abstand 0, oder die Senke warf einen `Error` | Senke übergeben, `metricsIntervalSeconds` prüfen |
| kein `SPAN_ENDED` im Log | Ebene unter `DEBUG` im Adapter | im Adapter `DEBUG` zulassen (Testserver: `LOG_LEVEL=DEBUG`) |
| `RequestSettings` startet mit falschem Wert | Ganzzahlen nach Position übergeben | benannte Argumente verwenden |

## Weiterführend

- [Referenz der Konfiguration](referenz-konfiguration.md): Felder, Fehlercodes, Antwortformen, Prepared-Handles, Grenzen.
- [Referenz der Ereignisse](referenz-ereignisse.md): Ereignisarten, Felder, Log des Testservers.
- [Testserver von Hand](testserver-manuell.md), [Kompatibilitätsmatrix](../plan/kompatibilitaetsmatrix.md).
