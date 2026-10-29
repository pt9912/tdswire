# Spezifikation — JVM TDS Server

**Bezug zum Lastenheft:** `spec/lastenheft.md`, Anforderungen `TDS-FA-01` bis `TDS-FA-11` sowie
`TDS-QA-*` und `TDS-RB-*`. Bei Widerspruch hat das Lastenheft Vorrang.  
**Rolle:** Technik-Stratum; technische Präzisierung ohne Erweiterung des Lastenhefts.

## 1. Algorithmen und Datenflüsse

### TDS-FA-01.a — TCP-Verbindung und PRELOGIN
**Eingabe:** TCP-Bytestrom des .NET-Clients. **Ausgabe:** ausgehandelte Verbindung oder
kontrollierte Ablehnung.

1. TCP-Verbindung annehmen und einen verbindungslokalen Paketdecoder initialisieren.
2. TDS-Paketheader (8 Byte) lesen, deklarierte Länge validieren, fragmentierte Nachrichten
   zusammensetzen.
3. PRELOGIN-Optionen anhand von Offsets und Längen prüfen; unbekannte Optionen nur nach definiertem
   Kompatibilitätsprofil behandeln.
4. Verschlüsselungsoptionen und TLS-Anforderungen des Referenzclients abgleichen; bei
   Unvereinbarkeit vor LOGIN abbrechen.
5. Nach erfolgreicher Verhandlung in den LOGIN7-Zustand wechseln.

**Komplexität:** O(n) je Nachricht. **Fehlermodi:** unvollständiger Header, ungültige Länge, zu
große Nachricht, ungültige PRELOGIN-Tabelle, TLS-Fehler, Timeout. Keine stillschweigende
Herabstufung einer geforderten Verschlüsselung.

### TDS-FA-02.a — LOGIN7 und Sitzung
**Eingabe:** LOGIN7-Nachricht. **Ausgabe:** Anmeldung oder Login-Fehler.

1. LOGIN7-Felder unter Beachtung ihrer Offsets und Zeichencodierung validieren.
2. Identität über konfigurierten Authentifizierungsadapter prüfen; Geheimnisse nicht protokollieren.
3. Bei Erfolg isolierte Session mit ID, Principal, Datenbankkontext und Abbruchstatus erzeugen.
4. Die für den Referenzclient erforderlichen Login-Antworttokens (insbesondere LOGINACK und DONE;
   erforderliche ENVCHANGE/INFO nach Profil) senden.
5. Bei Fehler Fehlerantwort senden und Verbindung kontrolliert beenden.

**Komplexität:** O(n) für Nachricht plus Authentifizierungsaufwand. **Fehlermodi:** ungültiges
Login, nicht unterstützte Authentifizierung, Ressourcenlimit (`SPEC-034`).

### TDS-FA-03.a — SQL_BATCH
**Eingabe:** zusammengesetzte SQL_BATCH-Nachricht. **Ausgabe:** Backend-Ergebnis als TDS-Tokenstrom.

1. Batchtext gemäß TDS-Unicodekodierung dekodieren.
2. Unterstützte Sitzungs-/Headerinformationen prüfen und Anfrage mit Kontext an Backend übergeben.
3. Ergebnis oder Fehler in eine geordnete Antwort überführen.
4. COLMETADATA, ROW/NBCROW (nur falls implementiert) und DONE-Familie gemäß Profil serialisieren.

**Komplexität:** O(n + Ergebnisgröße) ohne Backend-Rechenzeit. **Fehlermodi:** ungültige Kodierung,
nicht unterstütztes SQL, Backend-Timeout.

### TDS-FA-04.a — RPC und Parameter
**Eingabe:** TDS-RPC-Nachricht des Referenzclients. **Ausgabe:** parametrisiertes Backend-Statement
und TDS-Antwort.

1. Prozedurname bzw. Prozedur-ID und Parameterliste parsen.
2. Für `sp_executesql` SQL-Text, Parameterdeklarationen, Werte und NULL-Kennzeichnung extrahieren.
3. Werte typisiert und ohne SQL-Stringverkettung an den Backend-Vertrag übergeben.
4. Unbekannte Prozeduren und unzulässige Parameter mit definiertem Fehler beantworten.
5. Weitere RPC-Formen (`sp_prepexec`, `sp_prepare`, `sp_execute`, `sp_unprepare`) erst nach Nachweis
   des Bedarfs im Clientprofil ergänzen.

**Komplexität:** O(n) für RPC-Nachricht. **Fehlermodi:** unbekannte RPC-Form, Typinkonsistenz,
ungültige Parameterlänge.

### TDS-FA-05.a — Ergebniskodierung
**Eingabe:** typisiertes Backend-Resultset. **Ausgabe:** gültige TDS-Tokenfolge.

1. Spaltentypen über eine explizite Mapping-Tabelle in TDS-Typen abbilden.
2. COLMETADATA vor der ersten Datenzeile ausgeben; bei leerem Ergebnis dennoch Metadaten liefern.
3. Zeilen feldweise mit korrekten Längen, NULL-Repräsentationen und Unicode-Kodierung schreiben.
4. DONE-Token mit konsistentem Status und, soweit vorhanden, Zeilenzahl abschließen.
5. Nicht abbildbare Werte vor Ausgabe einer inkonsistenten Zeile abfangen.

**Komplexität:** O(Bytes des Resultsets). **Fehlermodi:** Überlauf, unbekannter Typ, ungültiger
Unicode, abgebrochener Stream.

### TDS-FA-06.a — Fehlerabbildung
**Eingabe:** Parser-, Authentifizierungs-, Backend- oder Transportfehler. **Ausgabe:**
ERROR-/DONE-Token oder Verbindungsabbruch, falls Protokollzustand keine Antwort mehr zulässt.

1. Fehler in stabile interne Kategorien klassifizieren.
2. Fehlernummer, Severity, State und Meldung gemäß vereinbartem Mapping serialisieren.
3. Nach ERROR einen passenden Abschlussstatus ausgeben, sofern der Kanal intakt ist.
4. Fehler nicht als erfolgreiches leeres Resultset tarnen.

**Komplexität:** O(Meldungslänge). **Fehlermodi:** Transport bereits geschlossen, Encoderfehler.

### TDS-FA-07.a — ATTENTION und Cleanup
**Eingabe:** ATTENTION, Client-Disconnect, Timeout oder Shutdown. **Ausgabe:** Abbruchsignal und
freigegebene Ressourcen.

1. Verbindungs-/Sitzungsabbruch an laufende Backendoperation weiterreichen.
2. Bereits erzeugte Ergebnisressourcen und Session-Kontext schließen.
3. Soweit protokollkonform möglich, ATTENTION bestätigen; keine weitere Antwort einer abgebrochenen
   Abfrage mit der nächsten vermischen.

**Komplexität:** O(Anzahl sitzungseigener Ressourcen). **Fehlermodi:** nicht abbrechbares Backend;
dann nach `SPEC-033` Verbindung schließen.

### TDS-FA-08.a — Backend-Vertrag
**Eingabe:** QueryRequest mit Session-Kontext, SQL und typisierten Parametern. **Ausgabe:**
Ergebnisstream oder typisierter Fehler.

1. Protokollschicht erzeugt QueryRequest unabhängig von TDS-Bytes.
2. Backend führt Anfrage aus und liefert Metadaten vor den Zeilen.
3. Backpressure wird bis zum Transport weitergereicht; Abbruch und Deadline bleiben erhalten.

**Komplexität:** abhängig von Backend und Ergebnismenge. **Fehlermodi:** Backend nicht erreichbar,
Typkonflikt, Abbruch.

### TDS-FA-09.a — Konfiguration und Diagnose
**Eingabe:** validierte Konfiguration. **Ausgabe:** gestarteter Listener und strukturierte
Betriebsdaten.

1. Bind-Adresse, Port, TLS-Modus, Authentifizierungsadapter, Größenlimits und Timeouts beim Start
   validieren.
2. Fehlerhafte sicherheitsrelevante Konfiguration führt zu Startabbruch.
3. Sitzungs-, Anfrage- und Fehlerereignisse mit Korrelations-IDs erfassen, ohne Passwörter oder
   Parameterwerte standardmäßig zu loggen.

**Komplexität:** O(Anzahl Konfigurationswerte). **Fehlermodi:** ungültiges Zertifikat, Port belegt,
fehlende Pflichtoption.

### TDS-FA-10.a — Mehrteilige Antworten und RPC-Ausgabewerte
**Eingabe:** geordnete Backend-Ereignisse und optional RPC-Ausgabeparameter. **Ausgabe:** geordneter
TDS-Tokenstrom.

1. Ergebnisse in Reihenfolge serialisieren: Metadaten, Zeilen, Ergebnisabschluss; weitere Ergebnisse
   folgen erst nach dem jeweiligen Abschluss.
2. DONE/DONEPROC/DONEINPROC passend zum Nachrichtenkontext auswählen; `DONE_MORE`, `DONE_ERROR` und
   `DONE_COUNT` nur gemäß tatsächlichem Zustand setzen.
3. Bei RPC die im Clientprofil benötigten RETURNSTATUS- und RETURNVALUE-Tokens mit korrekten
   Parametermetadaten erzeugen.
4. Nach dem ersten gesendeten Byte niemals eine alternative vollständige Antwort beginnen; Fehler
   als zulässige Folgetokens senden oder Verbindung schließen.

**Fehlermodi:** unzulässige Ereignisfolge, Typfehler, nicht abschließbarer Stream. **Komplexität:**
O(übertragene Bytes).

### TDS-FA-11.a — Simulations-Backend
**Eingabe:** `QueryRequest` (`SPEC-002`) mit SQL-Text, typisierten Parametern und Session-Kontext.
**Ausgabe:** `QueryExecution` mit geordneten Ereignissen (Metadaten, Zeilen, Abschluss oder
Änderungszähler) oder typisierter Fehler. Das Simulations-Backend ist eine Implementierung von
`QueryBackend`; der Protokollkern kennt es nicht und läuft ohne es.

1. Anweisungstext normalisieren: Leerraum und Kommentare außerhalb von Literalen und Bezeichnern
   überlesen, ein einzelnes abschließendes `;` entfernen. Mehrere Anweisungen in einem Text gelten
   als nicht erkannt, solange das Clientprofil sie nicht verlangt.
2. Die Anweisung anhand ihres Textes genau einer Form aus `SPEC-036` zuordnen. Das geschieht mit
   einem Zeichenscanner, der Literale, Bezeichner und Kommentare erkennt, nicht mit einer
   Auswertung von SQL. Ohne Zuordnung endet die Anfrage mit `TDS_UNSUPPORTED_SQL`, bevor eine
   Zeilenquelle gelesen oder ein Callback gerufen wird.
3. Tabelle (Schema, Name) und alle genannten Spalten gegen die Tabellenbeschreibungen (`SPEC-035`)
   prüfen. Unbekanntes endet mit `TDS_SIM_UNKNOWN_OBJECT`, ebenfalls vor dem Lesen. Ohne Schema gilt
   `dbo`.
4. Parameter (`@name`) aus `QueryRequest.parameters` als typisierte Werte einsetzen, nie als Text.
   Jeden Wert in den Typ seiner Spalte überführen; was die Spalte nicht halten kann, endet mit
   `TDS_SIM_INVALID_VALUE`.
5. Auswahl: `ResultStart` mit den Spalten der Auswahlliste (bei `*` alle, in der Reihenfolge der
   Beschreibung) senden, auch wenn keine Zeile passt. Danach Zeilen einzeln aus der Zeilenquelle
   lesen, die Bedingung auf jede Zeile anwenden, passende als `Row` senden und mit `ResultEnd` und
   der Zeilenzahl abschließen. Das Backend wendet die Bedingung immer selbst an; ein Vorfilter der
   Zeilenquelle ist nur eine Optimierung. Gelesen wird nur so schnell, wie der Transport Zeilen
   abnimmt (Backpressure, `TDS-FA-08.a`); das Resultset wird nicht vollständig gehalten.
6. Einfügen, Ändern, Löschen: Werte nach Schritt 4 prüfen, den Callback der Tabelle rufen und dessen
   Zahl als `UpdateCount` senden. Hat die Tabelle für die Anweisung keinen Callback, endet sie mit
   `TDS_UNSUPPORTED_SQL`. Das Backend speichert nichts.
7. Fehler der Zeilenquelle oder eines Callbacks vor `ResultStart` sind `Failure` ohne Metadaten
   (`TDS_BACKEND_ERROR`). Nach `ResultStart` gilt `SPEC-031`. Cancel und Disconnect beenden das
   Lesen und schließen die Zeilenquelle (`SPEC-029`).

**Komplexität:** O(gelesene Zeilen) je Auswahl, O(n) für die Erkennung des Textes. **Fehlermodi:**
nicht erkannte Anweisung, unbekannte Tabelle oder Spalte, nicht überführbarer Wert, Fehler der
Zeilenquelle, Abbruch.

## 2. Datenstrukturen und Schemas

### SPEC-001 — Zustandsautomat einer Verbindung
`CONNECTED → PRELOGIN → TLS_NEGOTIATION (falls erforderlich) → LOGIN7 → READY → CLOSED`. Profile mit
vorgelagertem TLS (TDS 8.0, Strict-Encryption) beginnen mit
`CONNECTED → TLS_NEGOTIATION → PRELOGIN`. Nur `READY` akzeptiert SQL_BATCH/RPC; ATTENTION ist im
passenden laufenden Anfragezustand zulässig. Ungültige Übergänge werden abgelehnt.

Anfragezustand (nur innerhalb von `READY`): `IDLE → RUNNING → STREAMING → COMPLETED → IDLE`. Bei
ATTENTION führen `RUNNING` und `STREAMING` über `CANCELLING` nach `COMPLETED`. Der Response
Sequencer hält den Anfragezustand und entscheidet jeden Übergang atomar je Sitzung; der Transport
meldet ein erkanntes ATTENTION-Paket an ihn, der Dispatch nimmt kein ATTENTION an. Bei
gleichzeitigem DONE und ATTENTION gewinnt der erste Übergang. Trifft ATTENTION nach `COMPLETED` oder
in `IDLE` ein, bleibt das bereits gesendete Ergebnis gültig, und der Server beantwortet das
ATTENTION mit der vom Profil geforderten Bestätigung, damit der Client nicht auf sie wartet. Der
Abschluss einer Anfrage wird genau einmal gesendet.

### SPEC-002 — Interner Query-Vertrag
```kotlin
interface QueryBackend {
    suspend fun execute(request: QueryRequest): QueryExecution
    suspend fun cancel(sessionId: String, requestId: String)
}
data class QueryRequest(
    val sessionId: String,
    val requestId: String,
    val sql: String,
    val parameters: List<QueryParameter>,
    val deadlineEpochMillis: Long?
)
data class QueryParameter(
    val name: String, val type: SqlType, val value: Any?,
    val direction: ParameterDirection = ParameterDirection.INPUT
)
enum class ParameterDirection { INPUT, OUTPUT, INPUT_OUTPUT }
interface QueryExecution {
    val events: kotlinx.coroutines.flow.Flow<QueryEvent>
    suspend fun close()
}
sealed interface QueryEvent {
    data class ResultStart(val columns: List<ResultColumn>) : QueryEvent
    data class Row(val values: List<Any?>) : QueryEvent
    data class ResultEnd(val affectedRows: Long?) : QueryEvent
    data class UpdateCount(val affectedRows: Long) : QueryEvent
    data class ReturnStatus(val value: Int) : QueryEvent
    data class OutputParameter(val name: String, val type: SqlType, val value: Any?) : QueryEvent
    data class Failure(val code: String, val message: String) : QueryEvent
}
data class ResultColumn(val name: String, val type: SqlType, val nullable: Boolean)
```
Dies ist eine **Vertragsskizze**, kein bereits implementierter Code. Konkrete Typen, Fehler und
Lebensdauerregeln werden vor der Implementierung festgelegt.

### SPEC-003 — Typmapping (initialer Mindestkatalog)
| Backendtyp | TDS-Zieltyp | Grenzfälle |
|---|---|---|
| Integer 32/64 Bit | INT4 / INT8 | Min/Max |
| Boolean | BIT | NULL |
| Unicode-Text | NVARCHAR | leer, Unicode, Längenlimit |
| Decimal | DECIMALN / NUMERICN | Precision/Scale, Überlauf |
| Datum/Zeit | DATE / TIME / DATETIME2, nach Profil | Precision, Zeitzonenannahmen |

Konkrete Token-/Typkennungen, Kollation und Präzision sind anhand MS-TDS und des Referenzclients zu
verifizieren.

### SPEC-004 — Testprofil
Ein versioniertes `client-profile` dokumentiert: .NET-Laufzeit, NuGet-Paket und Version, Connection
String (ohne Secrets), Encrypt/TrustServerCertificate, Authentifizierung, verwendete
SQL-/RPC-Operationen und erwartete Typen. Ohne dieses Profil ist die Kompatibilitätsabnahme nicht
abgeschlossen. **Offene Freigabepunkte:** konkretes Paket (`Microsoft.Data.SqlClient` oder
`System.Data.SqlClient`) und Version, .NET-Version, `Encrypt`/`TrustServerCertificate`,
SQL-Authentifizierung, TDS-Variante, unterstützte RPCs, MARS ja/nein, Transaktionen ja/nein,
Verbindungs-Pooling (einschließlich RESETCONNECTION-Statusbit der Paketheader) und Lastprofil.
Verantwortliche Rolle und Trigger der Festlegungen stehen im
Lastenheft (Abschnitt Offene Entscheidungen). Bis zur Entscheidung gilt: kein Anspruch auf
Produktionskompatibilität. Das Referenzprojekt muss Connection.Open, SELECT mit/ohne Parameter,
NULL/Unicode/Decimal/Datum, leere und mehrteilige Resultsets, Fehler vor/nach Zeilen,
OUTPUT/RETURNSTATUS (falls freigegeben), Cancel/Timeout und Folgeabfrage nach Cancel prüfen.
Paketmitschnitte sind nur redigiert und ohne Secrets abzulegen.

### SPEC-028 — PRELOGIN, TLS und LOGIN7: Zustands- und Antwortvertrag

Die TDS-Paketierung (8-Byte-Header, Pakettyp, Status, Big-Endian-Länge, Packet-ID) ist von der
TLS-Record-Verarbeitung getrennt. Der Decoder setzt Nachrichten über mehrere Pakete bis zum
EOM-Status zusammen; Paketgrenzen sind keine Nachrichten- oder TLS-Record-Grenzen. PRELOGIN-Optionen
werden anhand ihrer Offset-/Längen-Tabelle einschließlich Terminator geprüft. Die ENCRYPTION-Option
wird nach dem ausgehandelten Clientprofil behandelt; kein stilles Downgrade. Für klassische
TDS-7.x-Profile werden TLS-Handshake-Daten in der PRELOGIN/TDS-Transportphase entsprechend MS-TDS
gekapselt; ein bloßes TLS-Upgrade auf dem rohen TCP-Stream ist nicht allgemein ausreichend. Der
Übergang in verschlüsselte LOGIN7-/Nutzdaten erfolgt ausschließlich nach erfolgreicher Aushandlung.

Erfolgreiches LOGIN7 erfordert eine durch Referenztests bestätigte Folge zulässiger Tokens
einschließlich LOGINACK, relevanter ENVCHANGE-Ereignisse (z. B. Datenbank/Packet Size, soweit
ausgehandelt) und abschließendem DONE. Loginfehler erzeugen einen zulässigen ERROR-Abschluss und
keine READY-Sitzung. Genaue Felder, Reihenfolge, TDS-Version und Login-Feature-Extensions werden im
versionierten Clientprofil durch Paketmitschnitt und Integrationstest fixiert. Nicht unterstützte
Features werden nicht als unterstützt beworben.

### SPEC-029 — ATTENTION und Wiederverwendung der Verbindung

ATTENTION wird als eigenes TDS-Paket erkannt, auch wenn eine Anfrage gerade streamt. Der Server
signalisiert Cancel genau einmal an die laufende Backendoperation, stoppt das Schreiben weiterer
regulärer Zeilen und sendet die vom ausgehandelten TDS-Profil geforderte Abschlussbestätigung
(einschließlich DONE mit ATTENTION-Status, soweit protokollkonform). Die Sitzung nimmt erst nach
vollständigem Abbruchabschluss wieder eine neue Anfrage an (Anfragezustand `IDLE`). Bei Race
zwischen DONE und ATTENTION gilt die Regel der Anfragezustandsmaschine in `SPEC-001`; doppelte
DONE-Tokens sind verboten. Wenn ein
bereits gesendeter Stream nicht mehr sauber abgeschlossen werden kann, wird die Verbindung
geschlossen. Tests prüfen `SqlCommand.Cancel()`, `CancellationToken` und unmittelbar anschließende
neue Befehle.

### SPEC-030 — RPC-Varianten und Parameter-Metadaten

RPC-Nachrichten müssen Prozedurname/-ID, Optionsflags, Parameternamen, Statusflags (inklusive
OUTPUT), TYPE_INFO, Längenpräfixe, NULL-Sentinels, Kollation sowie Precision/Scale typabhängig
korrekt dekodieren. Für `sp_executesql` werden SQL-Text und Parameterdefinitionen getrennt vom
Wertetransport gehalten. `sp_prepare`, `sp_prepexec`, `sp_execute` und `sp_unprepare` sind **nicht
automatisch implementiert**; der Referenzclienttest entscheidet, welche davon erforderlich sind.
Unbekannte RPCs ergeben eine explizite Fehlermeldung. RETURNSTATUS/RETURNVALUE und die DONE-Familie
sind für freigegebene RPCs durch Tests nachzuweisen.

### SPEC-031 — Streaming-Fehler und Atomarität

Vor Ausgabe von COLMETADATA wird die komplette Metadatenstruktur und Typabbildbarkeit validiert.
Nach COLMETADATA dürfen nur im TDS-Profil zulässige Zeilen-, Fehler- und Abschlusstokens folgen. Ein
Fehler nach teilweise übertragenen Zeilen wird als ERROR plus Abschluss mit Fehlerstatus gemeldet,
wenn der Strom synchron bleibt; andernfalls wird die Verbindung geschlossen. Niemals nach
Teilantwort einen zweiten vollständigen Resultset-Anfang vortäuschen. Backpressure begrenzt Puffer;
Cancel und Disconnect schließen den Backendstream.

### SPEC-035 — Tabellenbeschreibung und Zeilenquelle des Simulations-Backends
```kotlin
data class SimTable(
    val schema: String = "dbo",
    val name: String,
    val columns: List<SimColumn>,
    val rows: suspend (SimQuery) -> RowSource,
    val onInsert: (suspend (values: Map<String, Any?>) -> Long)? = null,
    val onUpdate: (suspend (set: Map<String, Any?>, where: RowFilter?) -> Long)? = null,
    val onDelete: (suspend (where: RowFilter?) -> Long)? = null
)
data class SimColumn(val name: String, val type: SqlType, val nullable: Boolean = true)
data class SimQuery(val columns: List<SimColumn>, val filter: RowFilter?)
data class RowFilter(val terms: List<FilterTerm>)
sealed interface FilterTerm {
    data class In(val column: String, val values: List<Any?>) : FilterTerm
    data class IsNull(val column: String, val negated: Boolean) : FilterTerm
}
fun interface RowSource {
    suspend fun next(): List<Any?>?
    fun close() {}
}
```
Dies ist eine **Vertragsskizze**, kein bereits implementierter Code; Typen und Lebensdauerregeln
werden vor der Implementierung festgelegt. Festgelegt ist die Bedeutung:

- `next()` liefert die Werte einer Zeile in der Reihenfolge von `SimQuery.columns`, `null` am Ende.
  Jeder Aufruf darf suspendieren; die Zeilenquelle liest nie mehr voraus, als der Aufrufer abnimmt.
- `close()` wird genau einmal gerufen, nach dem Ende, einem Fehler oder einem Abbruch.
- `FilterTerm.In` bildet `=` (ein Wert) und `IN (…)` ab. Alle Terme eines `RowFilter` gelten
  gemeinsam (UND). Dieselbe Spalte darf in mehreren Termen stehen; ein Widerspruch trifft keine
  Zeile. `NULL` in einem Vergleich oder einer Werteliste trifft nie (dreiwertige Logik,
  `ANSI_NULLS ON`); `IsNull` ist der einzige Weg zu NULL-Zeilen.
- `onUpdate` und `onDelete` bekommen `where = null` für eine Anweisung ohne `WHERE`, also für alle
  Zeilen. Die Zahl, die ein Callback liefert, ist die Zahl der betroffenen Zeilen.
- `SimQuery.filter` ist ein Hinweis zum Vorfiltern (Schritt 5 in `TDS-FA-11.a`), kein Ersatz für die
  Prüfung durch das Backend.

### SPEC-036 — Erkannte Anweisungsformen des Simulations-Backends (vorläufig)

| Form | Muster | Antwort |
|---|---|---|
| S1 | `SELECT <Spaltenliste \| *> FROM [<Schema>.]<Tabelle> [WHERE <Term> {AND <Term>}]` | Resultset |
| W1 | `INSERT INTO [<Schema>.]<Tabelle> (<Spalten>) VALUES (<Werte>)` | Änderungszähler |
| W2 | `UPDATE [<Schema>.]<Tabelle> SET <Spalte> = <Wert> {, <Spalte> = <Wert>} [WHERE <Term> {AND <Term>}]` | Änderungszähler |
| W3 | `DELETE FROM [<Schema>.]<Tabelle> [WHERE <Term> {AND <Term>}]` | Änderungszähler |
| C | genau die Texte, die das Clientprofil dem Referenzclient zuschreibt (z. B. ein Verbindungstest, falls das Profil ihn nennt) | im Profil festgelegtes Ergebnis |

- **Term:** `<Spalte> = <Wert>`, `<Spalte> IN (<Wert> {, <Wert>})` oder `<Spalte> IS [NOT] NULL`.
- **Wert:** Zahl, `'…'`, `N'…'`, `NULL` oder `@<Parametername>`.
- **Bezeichner:** unquotiert, `[…]` oder `"…"`; Tabellen- und Spaltennamen werden ohne Beachtung der
  Groß-/Kleinschreibung verglichen (vorläufig, wie die übliche Server-Collation).
- **Nicht erkannt** (`TDS_UNSUPPORTED_SQL`): `JOIN` und weitere Relationen, Unterabfragen, `WITH`,
  Aggregate und `GROUP BY`, `DISTINCT`, `TOP`, `ORDER BY`, `OFFSET`/`FETCH`, Aliase für Tabellen und
  Spalten, Ausdrücke und Funktionen in Listen und Bedingungen, `OR`, `NOT`, `<`, `>`, `LIKE`, `BETWEEN`,
  `OUTPUT`-Klauseln, mehrere Anweisungen in einem Text und alles, was keine Form der Tabelle ist.
- Form C wird erst mit dem Clientprofil (`SPEC-004`) gefüllt; bis dahin ist sie leer.

Der Katalog ist ein Entwurf. Verbindlich wird er mit der Festlegung, die das Lastenheft unter
„Offene Entscheidungen“ führt.

## 3. Defaults und Konstanten

| ID | Name | Wert | Begründung |
|---|---|---|---|
| `SPEC-005` | `LISTEN_PORT` | 1433 (konfigurierbar) | TDS-Standardport |
| `SPEC-006` | `MAX_TDS_PACKET_BYTES` | 65535, zusätzlich protokollkonforme Headerprüfung | Obergrenze des 16-Bit-Längenfelds |
| `SPEC-007` | `MAX_MESSAGE_BYTES` | 16 MiB (vorläufig) | Speicherbegrenzung; mit Clientprofil abgleichen |
| `SPEC-008` | `LOGIN_TIMEOUT` | 15 s (vorläufig) | Hängende Logins begrenzen |
| `SPEC-009` | `QUERY_TIMEOUT` | 30 s (vorläufig, konfigurierbar) | Backend-Timeout |
| `SPEC-010` | `ENCRYPTION_POLICY` | Standard `REQUIRED`; Abschwächung nur durch ausdrückliche, im Log sichtbare Konfiguration, je Clientprofil | Keine unbemerkte TLS-Herabstufung |
| `SPEC-033` | `CANCEL_TIMEOUT` | 5 s (vorläufig, konfigurierbar) | Frist für nicht abbrechbare Backendoperationen, danach Verbindung schließen |
| `SPEC-034` | `MAX_CONNECTIONS` | 1000 (vorläufig, konfigurierbar) | Ressourcenlimit für gleichzeitige Verbindungen |

Vorläufige Werte sind technische Entwurfsannahmen, keine aus dem Lastenheft abgeleiteten
Abnahmegrenzen.

## 4. Fehler-Codes und Logging-Felder

| ID | Interner Code | Bedingung | Aktion |
|---|---|---|---|
| `SPEC-011` | `TDS_PROTOCOL_ERROR` | Paket/Token ungültig | Abbruch oder ERROR, je nach Zustand |
| `SPEC-012` | `TDS_LOGIN_FAILED` | Authentifizierung fehlgeschlagen | Login-Fehler, Session nicht erstellen |
| `SPEC-013` | `TDS_UNSUPPORTED_RPC` | RPC nicht unterstützt | ERROR und DONE mit Fehlerstatus |
| `SPEC-014` | `TDS_UNSUPPORTED_TYPE` | Wert nicht serialisierbar | definierter Fehler, kein fehlerhaftes Resultset |
| `SPEC-015` | `TDS_BACKEND_ERROR` | Backend-Ausführung fehlgeschlagen | Fehlerabbildung in TDS |
| `SPEC-016` | `TDS_TIMEOUT` | Frist überschritten | Cancel, Cleanup, Fehler/Disconnect |
| `SPEC-037` | `TDS_UNSUPPORTED_SQL` | Anweisung oder Bedingung außerhalb von `SPEC-036`; Schreibzugriff ohne Callback | ERROR und DONE mit Fehlerstatus, Zeilenquelle nicht gelesen |
| `SPEC-038` | `TDS_SIM_UNKNOWN_OBJECT` | Tabelle oder Spalte ist nicht beschrieben | ERROR und DONE mit Fehlerstatus; vorgesehene SQL-Server-Nummern 208 (Objekt) und 207 (Spalte) |
| `SPEC-039` | `TDS_SIM_INVALID_VALUE` | Wert nicht in den Spaltentyp überführbar | ERROR und DONE mit Fehlerstatus; vorgesehene SQL-Server-Nummer 245 |

Die Nummern in der Aktionsspalte sind ein Vorschlag für das Mapping nach `TDS-FA-06.a`. Für
`TDS_UNSUPPORTED_SQL` gibt es keine SQL-Server-Entsprechung, denn dort ist die Anweisung gültig; die
Nummer kommt aus dem Benutzerbereich (ab 50001).

Pflicht-Logfelder: `connection_id`, `session_id` (falls vorhanden), `request_id` (falls vorhanden),
`event`, `duration_ms`, `error_code`, `client_profile`. Keine Zugangsdaten oder Parameterwerte im
Standardlogging.

## 5. Metriken und Tracing-Felder

| ID | Span | Pflicht-Attribute | Quelle |
|---|---|---|---|
| `SPEC-017` | `tds.connection` | `connection.id`, `tds.phase`, `outcome` | Transport/Handshake |
| `SPEC-018` | `tds.query` | `session.id`, `request.id`, `tds.request_kind`, `outcome` | Anfrageverarbeitung |
| `SPEC-019` | `backend.execute` | `request.id`, `duration_ms`, `outcome` | Backend-Adapter |

Metriken: aktive Sessions, Login-Erfolgs-/Fehlerquote, Anfragen/s, Anfrage-Latenz p50/p95/p99,
aktive Backendoperationen, Abbrüche, Protokollfehler. Keine SQL-Texte als Metrik-Labels.

## 6. Externe Verträge

| ID | System | Version | Vertrag-Datei / Referenz |
|---|---|---|---|
| `SPEC-020` | Microsoft TDS | TDS 7.x; TDS 8.0 nur bei Profil mit Strict-Encryption; konkrete Variante offen | [MS-TDS](https://learn.microsoft.com/en-us/openspecs/windows_protocols/ms-tds/) |
| `SPEC-021` | .NET SQL-Client | `Microsoft.Data.SqlClient` oder `System.Data.SqlClient`, Version offen | `contracts/client-profile.md` (anzulegen) |
| `SPEC-022` | Query-Backend | Projektvertrag v0 (Entwurf) | `contracts/query-backend.md` (anzulegen) |
| `SPEC-023` | TLS | durch Clientprofil und Betriebsrichtlinie bestimmt | `contracts/security-profile.md` (anzulegen) |

Die genannten Vertragsdateien sind **geplante Artefakte**, keine bereits vorhandenen Dateien.

## 7. Festlegungen der Harness-Werkzeuge

| ID | Werkzeug | Festlegung |
|---|---|---|
| `SPEC-024` | Unit-Test-Gate | Unit-Tests für Decoder, Zustandsautomat, Encoder und Fehlerabbildung müssen erfolgreich sein; Gate noch einzurichten |
| `SPEC-025` | Integrationstest-Gate | Containerisierter Server + echter .NET-Referenzclient prüfen Login, SQL_BATCH, RPC, Typen, Fehler, Cancel; Gate noch einzurichten |
| `SPEC-026` | `make gates` | Aggregator: führt alle eingerichteten Gates aus; neue Gates für Formatierung, statische Prüfung und Tests werden in ihn aufgenommen |
| `SPEC-027` | Fuzz-/Property-Tests | Defekte Paketlängen, Offsets, Fragmentierung und Typwerte dürfen keinen ungefangenen Prozessabsturz verursachen |
| `SPEC-040` | Simulations-Test-Gate | Unit-Tests der Anweisungserkennung (jede Form aus `SPEC-036` positiv und negativ, Grenzen bei Literalen, Bezeichnern und Kommentaren) und Integrationstest mit dem .NET-Referenzclient: Auswahl, leeres Ergebnis, Zeilenquelle über der Speichergrenze, Fehler der Zeilenquelle, Cancel; Gate noch einzurichten |
| `SPEC-032` | Kompatibilitätstest-Gate | Echter .NET-SqlClient prüft LOGINACK/ENVCHANGE/DONE, TLS-Profil, RPC-Parameter, Streaming-Fehler, ATTENTION und Wiederverwendung; Gate noch einzurichten |

## 8. Historie

| Datum | Änderung |
|---|---|
| 2026-10-29 | Überarbeitung: TLS-Kapselung, Login-Token, RPC, Streaming, ATTENTION, Mehrfachergebnisse und Abnahmetests präzisiert |
| 2026-10-29 | `TDS-FA-11.a` ergänzt: Simulations-Backend mit Ablauf, Tabellenbeschreibung, Anweisungsformen, Fehlercodes und Test-Gate (`SPEC-035` bis `SPEC-040`) |
