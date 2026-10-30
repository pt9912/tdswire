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
   zusammensetzen. Die Größengrenzen (`SPEC-007`, vor abgeschlossenem Login `SPEC-046`) gelten
   während des Zusammensetzens: übersteigt die Summe der Paketlängen die Grenze, wird abgebrochen,
   bevor die Nachricht vollständig gepuffert ist.
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
   Meldung und State der Ablehnung sind für eine unbekannte Kennung und ein falsches Passwort
   gleich; der Grund steht nur im Server-Log (`SPEC-012`). Gemessen ist nur die Gleichheit der
   Antwortbytes, am Spike-Testserver gemessen und nicht am Produkt; für das Produkt ist die Gleichheit eine Anforderung. Als Anforderung an den Produktivcode gilt: Die Einstiegsimplementierung vergleicht in
   konstanter Zeit und führt bei unbekannter Kennung einen Scheinvergleich aus; das ist weder gebaut
   noch gemessen, ein Test muss es belegen. Der Adapter hat die Schnittstelle
   aus `contracts/auth-adapter.md` (`SPEC-051`); die Einstiegsimplementierung ist eine feste
   Benutzerliste aus der Konfiguration, SSPI/Kerberos und Entra-ID werden am LOGIN7 erkannt und abgelehnt.
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
5. `sp_prepexec` und `sp_execute` (Prepared-Statements mit sitzungsbezogenem Handle) sowie den RPC per
   Prozedurname (`CommandType.StoredProcedure`) behandeln; der Katalog steht in `SPEC-030`.
6. Weitere RPC-Formen (`sp_prepare`, `sp_unprepare`, `sp_cursor*`) erst nach Nachweis des Bedarfs im
   Clientprofil ergänzen.

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
5. Eine Ausnahme, die keine erwartete Ablehnung des Backends oder der Protokollschicht ist
   (`Failure` mit Code), erreicht den Client nur als `TDS_INTERNAL_ERROR` mit festem Text, nie mit
   dem Text der Ausnahme; dieser kann Konfigurationswerte enthalten und geht ins Log, ohne
   Parameterwerte (`SPEC-048`).
6. Nach ERROR und DONE mit Fehlerstatus bleibt die Verbindung offen, wenn die Severity (Class) höchstens
   19 ist. Ab Severity 20, nach `TDS_INTERNAL_ERROR` (`SPEC-048`) und nach `TDS_SHUTDOWN` (`SPEC-049`)
   schließt der Server die Verbindung nach ERROR und DONE. Ist der Strom nicht mehr sicher abzuschließen,
   schließt der Server die Verbindung ohne ERROR und DONE (`SPEC-031`); ERROR und DONE gibt es nur,
   solange der Strom synchron ist. Nach Cancel oder Timeout bleibt sie offen, wenn DONE
   mit ATTENTION-Status gesendet wurde (`SPEC-029`). Ausnahme: Nach einem abgelehnten Login (`SPEC-012`)
   schließt der Server die Verbindung, ein weiteres LOGIN7 wird nicht angenommen. Eigene Fehlercodes:
   Anfragefehler (zum Beispiel `TDS_UNSUPPORTED_SQL`, `TDS_BACKEND_ERROR`) tragen Severity 16, die
   Verbindung bleibt offen; `TDS_PROTOCOL_ERROR` und `TDS_INTERNAL_ERROR` tragen Severity 20, der Server
   schließt. Die Zuordnung jedes Codes legt das Fehler-Mapping fest und steht in §4 (Zuordnung des Fehler-Mappings; die Einzelzuordnung von `TDS_UNSUPPORTED_TYPE`, `TDS_SIM_UNKNOWN_OBJECT` und `TDS_TIMEOUT` ist offen und steht unter der Grundregel); `TDS_UNSUPPORTED_RPC` und `TDS_SIM_INVALID_VALUE` gehören zur Anfragefehler-Gruppe (Setzung, Voreinstellung 2026-10-30, nicht gemessen). `TDS_TIMEOUT` bleibt bis zur Entscheidung, ob Cancel und Timeout als Fehler zählen, außerhalb der Gruppen; für ihn und für jeden weiteren eigenen Code, der zu keiner Gruppe gehört, gilt bis zur Zuordnung, dass der Server schließt (im Zweifel schließen). Das Verbindungsende nach abgelehntem
   Login und die Severity-Werte der eigenen Codes sind Setzungen, nicht an Referenzclients gemessen (am
   echten Server wurde nur die Fehlerantwort des Logins einmal beobachtet). Noch festzulegen: ob Cancel
   und Timeout als Fehler zählen.

**Komplexität:** O(Meldungslänge). **Fehlermodi:** Transport bereits geschlossen, Encoderfehler.

### TDS-FA-07.a — ATTENTION und Cleanup
**Eingabe:** ATTENTION, Client-Disconnect, Timeout oder Shutdown. **Ausgabe:** Abbruchsignal und
freigegebene Ressourcen.

1. Verbindungs-/Sitzungsabbruch an laufende Backendoperation weiterreichen.
2. Bereits erzeugte Ergebnisressourcen und Session-Kontext schließen.
3. Soweit protokollkonform möglich, ATTENTION bestätigen; keine weitere Antwort einer abgebrochenen
   Abfrage mit der nächsten vermischen.
4. Shutdown: der Server beendet sich geordnet nach `SPEC-044`. Der Listener schließt zuerst, eine
   laufende Anfrage darf bis `SHUTDOWN_GRACE` zu Ende laufen, danach wird sie wie bei ATTENTION
   abgebrochen, und jede Verbindung endet.

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
2. Fehlerhafte sicherheitsrelevante Konfiguration führt zu Startabbruch. Die TLS-Konfiguration
   prüft eine Startprobe vor dem Binden des Ports (`SPEC-043`).
3. Sitzungs-, Anfrage- und Fehlerereignisse mit Korrelations-IDs erfassen. Passwörter werden nie
   geloggt, auch nicht gekürzt oder verschleiert; Parameterwerte standardmäßig nicht. Die
   Anmeldekennung erscheint in keiner Antwort an den Client und im Log nur in einem Feld, das die
   Spezifikation dafür festlegt (bisher keines).

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
   Parametermetadaten erzeugen; dazu zählt RETURNVALUE mit dem Handle von `sp_prepexec` (Ordinal 0,
   `INTN` 4; so beantwortete es der Testserver, die ordinale Zählung eines echten SQL Servers ist nicht
   gemessen und gegen MS-TDS zu prüfen) und je ein RETURNVALUE je OUTPUT-Parameter.
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
   Jeden Wert in den Typ seiner Spalte überführen (streng und verlustfrei, `SPEC-035`); was die
   Spalte nicht halten kann, endet mit `TDS_SIM_INVALID_VALUE`.
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
passenden laufenden Anfragezustand zulässig. Der Zustand ist ein expliziter Automat: eine Aufzählung der
Zustände und eine reine Übergangsfunktion (Zustand, Ereignisart) → (Folgezustand, Aktion). Ungültige
Übergänge werden abgelehnt; die Antwort darauf (Verbindung ohne Antwort schließen oder ein Fehler), die
Reichweite von ATTENTION vor dem Login und der Zuschnitt der Aufzählung (eine Aufzählung oder getrennt
nach Verbindungs- und Anfragezustand) sind noch festzulegen.

Anfragezustand (nur innerhalb von `READY`): `IDLE → RUNNING → STREAMING → COMPLETED → IDLE`. Bei
ATTENTION führen `RUNNING` und `STREAMING` über `CANCELLING` nach `COMPLETED`. Der Response
Sequencer hält den Anfragezustand und entscheidet jeden Übergang atomar je Sitzung: Übergang und das
zugehörige Schreiben laufen unter einer Sperre je Sitzung. Der Transport
meldet ein erkanntes ATTENTION-Paket an ihn, der Dispatch nimmt kein ATTENTION an. Bei
gleichzeitigem DONE und ATTENTION gewinnt der erste Übergang. Trifft ATTENTION nach `COMPLETED` oder
in `IDLE` ein, bleibt das bereits gesendete Ergebnis gültig, und der Server beantwortet das
ATTENTION mit der vom Profil geforderten Bestätigung, damit der Client nicht auf sie wartet (Setzung;
am echten Server ist nur ATTENTION nach vollständig gesendeter Antwort gemessen, im Leerlauf ohne
laufende Anfrage nicht). Der Abschluss einer Anfrage wird genau einmal gesendet.

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
Das Ergebnis eines Aufrufs ist der asynchrone Strom `events`; ein Backend mit kleinem Ergebnis darf ihn
aus einer fertigen Liste erzeugen. Der Response Sequencer hält ein `ResultEnd` bzw. `UpdateCount` ein
Ereignis lang zurück, bis das nächste Ereignis oder das Ende des Stroms bekannt ist, und setzt daraus das
Bit `DONE_MORE`; das Backend kennt das Bit nicht. Entwurf des Vertrags: `contracts/query-backend.md`.

**Gebauter Stand (Setzung vom 2026-10-30, nicht gemessen):** Die Typen und Schnittstellen stehen als
Kotlin-Quelltext. Die Modelle `QueryRequest`, `QueryParameter`, `ParameterDirection`, `QueryEvent`,
`ResultColumn` und `SqlType` liegen im Package `domain`, die Schnittstellen `QueryBackend` und
`QueryExecution` mit dem `Flow` im Package `port`. `execute()` wirft für Fehler des Backends keine
Ausnahme: ein Fehler beim Start ist ein `Failure` im Strom `events`; die Abbruchausnahme der Coroutine
bleibt davon unberührt.

`SqlType` ist das Typmodell des Backends und umfasst den Mindestkatalog von `SPEC-003`: `Int32`,
`Int64`, `Bool`, `UnicodeText`, `Decimal(precision, scale)` mit `precision >= 1` und
`0 <= scale <= precision`, `Date`, `Time` und `DateTime` (ohne Zeitzone). Breiten unter 32 Bit,
Gleitkomma, Geld, GUID und Binärtypen gehören nicht dazu (Setzung vom 2026-10-30, nicht gemessen).

**Precision über 38 (Nutzerentscheidung vom 2026-10-30, nicht gemessen):** das Modell von `Decimal` hat
keine Obergrenze, die Typkennung kennt Precision 1 bis 38 (`SPEC-003`). Die Typabbildung weist eine
Precision über 38 vor COLMETADATA mit `TDS_UNSUPPORTED_TYPE` ab; das Modell bleibt unverändert.

**Offen:**
- Die JVM-Typen der Werte in `OutputParameter` und die endgültige Zeilendarstellung (`Row`,
  `QueryParameter`); die Typabbildung setzt für Zeilenwerte eine Zwischenfestlegung (`SPEC-003`).
- Die Unterscheidung statement-
  und batch-abbrechender Fehler im Strom, die `Failure` nicht trägt; die Grenze für ein „kleines“
  Ergebnis; die Behandlung von `OutOfMemoryError`; ein blockierendes Backend hinter dem `Flow`; die
  Semantik der Frist `deadlineEpochMillis` (wer sie durchsetzt, was bei Ablauf geschieht). Nicht
  gemessen; Einzelheiten und Zuständigkeit im Vertrag `contracts/query-backend.md`.

Lebensdauerregeln und alles, was über diesen Stand hinausgeht, bleiben Entwurf des Vertrags
`contracts/query-backend.md`.

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

**Typabbildung (Setzungen vom 2026-10-30, nicht gemessen):**
- Datum und Zeit sind ohne Zone: `LocalDate`, `LocalTime` und `LocalDateTime` werden wie geliefert
  abgebildet, ohne Zeitzonenumrechnung; Werte mit Zone (`Instant`, `OffsetDateTime`, `ZonedDateTime`)
  sind nicht im Mindestkatalog und enden in `TDS_UNSUPPORTED_TYPE`. `Time` und `DateTime` haben Scale 7
  (100 ns); ein feinerer Wert wird abgewiesen, nicht gerundet. Das Jahr liegt zwischen 1 und 9999.
- Unicode-Text ist `NVARCHAR(4000)` mit der Kollation `SQL_Latin1_General_CP1_CI_AS`, gezählt in
  UTF-16-Einheiten; ein unpaares Surrogat wird abgewiesen. Der leere String ist ein Wert, nicht NULL.
- Decimal wird nie gerundet: ein Wert mit mehr Nachkommastellen als der Scale der Spalte oder mit mehr
  Stellen als die Precision erlaubt wird abgewiesen. Lesart und Grenze sind Setzungen vom 2026-10-30,
  nicht gemessen und vom Nutzer nicht bestätigt: nachlaufende Nullen zählen nicht (1,500 passt in
  Scale 2, 1,234 nicht), aber eine Mantisse mit mehr als 1000 Stellen wird auch dann abgewiesen, wenn der
  Wert nach dem Kürzen passen würde. Eine Precision über 38 scheitert in der Typabbildung mit
  `TDS_UNSUPPORTED_TYPE` (`SPEC-002`).
- Genauigkeit der Zeittypen, Kollation und Längenlimit sind damit gesetzt; am Referenzclient und am
  echten SQL Server ist keiner dieser Werte gemessen.

**RPC-Parametertypen (Eingabe, vom Referenzclient beobachtet):** `tinyint`/`smallint`/`int`/`bigint`
als `INTN` (Länge 1/2/4/8); `float`/`real` als `FLTN`; `money`/`smallmoney` als `MONEYN`; `bit` als
`BITN`; `decimal` als `NUMERICN` (Precision/Scale am Parameter, Vorzeichenbyte plus 16 Byte);
`datetime`/`smalldatetime` als `DATETIMN`; `date`, `time`, `datetime2`, `datetimeoffset` als
`DATEN`/`TIMEN`/`DATETIME2N`/`DATETIMEOFFSETN` (Scale 7); `uniqueidentifier` als `GUID`;
`char`/`varchar` als `BIGCHAR`/`BIGVARCHAR` mit Collation; `nchar`/`nvarchar` als `NCHAR`/`NVARCHAR`
mit Collation (`maxlen` = 2 × Zeichenzahl der Deklaration); `binary`/`varbinary` als
`BIGBINARY`/`BIGVARBINARY`; `xml` als `XML` ohne Schema. MAX-Typen (`nvarchar(max)`, `varchar(max)`,
`varbinary(max)`, `xml`) tragen Länge `0xFFFF` und PLP. NULL ist der Sentinel des Typs (Länge 0 bei
`INTN`, `NUMERICN`, `GUID`, `DATETIME2N`; `0xFFFF` bei `NVARCHAR`, `BIGVARBINARY`); ein leerer String
hat Länge 0 und ist nicht NULL. Nicht gemessen: `text`, `ntext`, `image`, `sql_variant`, UDT, `json`,
Table-Valued-Parameter und OUTPUT für `varchar(max)`, `xml` und Datumstypen.

### SPEC-004 — Testprofil
Ein versioniertes `client-profile` dokumentiert: .NET-Laufzeit, NuGet-Paket und Version, Connection
String (ohne Secrets), Encrypt/TrustServerCertificate, Authentifizierung, verwendete
SQL-/RPC-Operationen und erwartete Typen. Ohne dieses Profil ist die Kompatibilitätsabnahme nicht
abgeschlossen. Festgelegt sind Paket (`Microsoft.Data.SqlClient` 7.1.1), .NET-Version (10) und
TDS-Variante (7.4 und 8.0). **Offene Freigabepunkte:** `Encrypt`/`TrustServerCertificate`,
unterstützte RPCs (Entwurf in `SPEC-030`), MARS ja/nein, Transaktionen ja/nein (Pakettyp `0x0E`, kein RPC),
Verbindungs-Pooling (einschließlich RESETCONNECTION-Statusbit der Paketheader) und Lastprofil.
Die unterstützten RPCs gibt das Clientprofil erst frei, wenn die Messgrenzen aus `SPEC-030` geklärt sind
(TDS 8.0, Pooling, echter Server, `SET`-Text bei SchemaOnly und KeyInfo, Transaktionen).
Die Authentifizierung ist SQL-Authentifizierung (Kennung und Passwort im LOGIN7, `contracts/auth-adapter.md`).
Verantwortliche Rolle und Trigger der Festlegungen stehen im
Lastenheft (Abschnitt Offene Entscheidungen). Bis zur Entscheidung der offenen Freigabepunkte gilt: kein Anspruch auf
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
Im TDS-8.0-Profil (`Encrypt=Strict`) läuft TLS vor jedem PRELOGIN ab dem ersten Byte, mit ALPN `tds/8.0`;
PRELOGIN und LOGIN7 folgen im Tunnel. Verlangt die Konfiguration TLS (`SPEC-010`), antwortet der Server
im TDS-7.x-Pfad mit ENCRYPT_REQ oder ENCRYPT_ON; ENCRYPT_NOT_SUP sendet er nur im ausdrücklich
konfigurierten unverschlüsselten Testmodus.

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
ohne ERROR und DONE geschlossen (`SPEC-031`). Nach gesendetem DONE mit ATTENTION-Status bleibt die Verbindung offen, außer das geordnete Beenden (`SPEC-044`) hat begonnen; dann endet sie. Tests prüfen `SqlCommand.Cancel()`, `CancellationToken` und unmittelbar anschließende
neue Befehle.

### SPEC-030 — RPC-Varianten und Parameter-Metadaten

RPC-Nachrichten müssen Prozedurname/-ID, Optionsflags, Parameternamen, Statusflags (inklusive
OUTPUT), TYPE_INFO, Längenpräfixe, NULL-Sentinels, Kollation sowie Precision/Scale typabhängig
korrekt dekodieren. Für `sp_executesql` werden SQL-Text und Parameterdefinitionen getrennt vom
Wertetransport gehalten. Der Katalog unterstützter RPCs ist der Mindestkatalog, den die Messung des Referenzclients trägt
(`contracts/client-profile.md`); die Messgrenzen stehen unter der Tabelle. Die Zeilen "Pflichtverhalten"
zum Fehlerfall (unbekanntes Handle, unbekannte Form) sind Festlegungen, nicht Messwerte:

| RPC | Erkennung | Pflichtverhalten |
|---|---|---|
| `sp_executesql` | ProcID 10 | Parameter 0 SQL-Text, Parameter 1 Deklarationsliste (beide ohne Namen); danach benannte Parameter, INPUT, NULL und OUTPUT/InputOutput (Statusbyte `0x01`) |
| `sp_prepexec` | ProcID 13 | Parameter 0 OUTPUT-Handle (`INTN` 4, bei Erstaufruf −1), Parameter 1 Deklarationsliste, Parameter 2 SQL-Text; Antwort liefert das Handle als RETURNVALUE |
| `sp_execute` | ProcID 12 | Parameter 0 Handle (`INTN` 4), danach nur die Nutzerparameter; unbekanntes Handle ergibt einen definierten Fehler |
| RPC per Prozedurname | Name als String (`NameLenProcID` = Zeichenzahl), `OptionFlags=0x0000` | Name wie in `CommandText` (auch `[dbo].[p]`); der ReturnValue-Parameter wird nicht gesendet, RETURNSTATUS trägt ihn; nicht gesetzte Parameter fehlen in der Nachricht |

Prepare-Handles gehören zur Sitzung und enden mit ihr; der Client sendete in den gemessenen
Szenarien (ohne Pool) kein `sp_unprepare`, daher ist die Anzahl je Sitzung zu begrenzen (Festlegung); unter
Pooling ist das nicht gemessen, ein gepoolter Client kann `sp_unprepare` senden, das der Katalog mit einem
Fehler beantwortet. Ohne RETURNSTATUS und
RETURNVALUE bleiben ReturnValue, OUTPUT-Parameter und Prepare-Handle beim Client leer bzw. −1; die
Tokens sind für diese Formen Pflicht und durch Tests nachzuweisen. Die Collation aus der
Login-Antwort (ENVCHANGE Typ 7) ist Voraussetzung für jeden RPC mit Zeichenparametern. Alle anderen RPCs,
auch `sp_prepare`, `sp_unprepare` und `sp_cursor*`, ergeben eine explizite Fehlermeldung ohne
Verbindungsabbruch. **Messgrenzen:** Gemessen ist nur das Verhalten des Referenzclients gegen einen Testserver, nicht
die Antwort eines Microsoft-SQL-Servers; TDS 7.4 unverschlüsselt (kein TDS 8.0, kein TLS), `Pooling=false`,
eine Verbindung je Szenario, ein Lauf. Tokenreihenfolge, ordinale Zählung der RETURNVALUE und
DONE-Statusbits sind gegen MS-TDS zu prüfen. **Offen:** die Behandlung der `SET`-Anweisungen im Batchtext von
`ExecuteReader(SchemaOnly)` (`SET FMTONLY`) und im Text von
`ExecuteReader(KeyInfo|SingleRow)` (`SET FMTONLY`, `SET NO_BROWSETABLE`) und die Transaktionen
(Pakettyp `0x0E`, kein RPC, nicht im Katalog).

### SPEC-031 — Streaming-Fehler und Atomarität

Vor Ausgabe von COLMETADATA wird die komplette Metadatenstruktur und Typabbildbarkeit validiert.
Nach COLMETADATA dürfen nur im TDS-Profil zulässige Zeilen-, Fehler- und Abschlusstokens folgen. Ein
Fehler nach teilweise übertragenen Zeilen wird als ERROR plus Abschluss mit Fehlerstatus gemeldet,
wenn der Strom synchron bleibt; andernfalls wird die Verbindung ohne ERROR und Abschluss geschlossen. Niemals nach
Teilantwort einen zweiten vollständigen Resultset-Anfang vortäuschen. Backpressure begrenzt Puffer;
Cancel und Disconnect schließen den Backendstream.

Festlegungen des Response Sequencers (Setzungen des Nutzers und des Implementers, nicht gemessen,
Handvektor statt Messung am echten Server): „synchron“ heißt, dass der Transport jedes bisher
erzeugte Token vollständig angenommen hat; sonst wird nichts mehr geschrieben und die Verbindung ohne
ERROR und DONE geschlossen. Ein Fehler im Strom zählt für `DONE_MORE` als nächstes Ereignis: ein
zurückgehaltenes DONE bekommt `DONE_MORE`, danach folgen ERROR und DONE mit Fehlerstatus. Belegt
ist nur das Bit vor dem ERROR: ein Referenzclient gegen einen echten SQL Server zeigt `DONE_MORE` vor
dem ERROR eines batch-abbrechenden Fehlers; am Produkt ist es nicht gemessen, und bei einem
statement-abbrechenden Fehler (echter Server: `DONE_MORE` auch am Abschluss mit Fehlerstatus)
schreibt der Sequenzierer die Variante ohne `DONE_MORE`. Bei Cancel
oder Disconnect wird ein zurückgehaltenes DONE verworfen. Ein Verstoß des Backends gegen die
Ereignisreihenfolge (Zeile oder Ergebnisende ohne offenes Ergebnis, Ergebnisanfang oder
Update-Count im offenen Ergebnis, Strom endet im offenen Ergebnis) und eine Ausnahme des Backends
sind ein Fall von `SPEC-048`. Ein Fehler beendet den Lauf; weitere Ereignisse werden nicht gelesen.
Return Status und Ausgabeparameter werden bis zu ihrer Abbildung mit einem Fehler der Typabbildung
(`SPEC-014`) abgelehnt. Die Befehlskennung (`curCmd`) der DONE-Tokens ist 0; woher sie kommt, ist
nicht festgelegt (Spec-Lücke).

### SPEC-035 — Tabellenbeschreibung und Zeilenquelle des Simulations-Backends
```kotlin
data class SimTable(
    val schema: String = "dbo",
    val name: String,
    val columns: List<SimColumn>,
    val primaryKey: List<String>,
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
  `ANSI_NULLS ON`, vorläufig und an der Referenzinstanz zu messen, `SPEC-045`); `IsNull` ist der
  einzige Weg zu NULL-Zeilen. `IS [NOT] NULL` ist eine Setzung des Nutzers (2026-10-30): das
  Lastenheft nennt es nicht wörtlich.
- `onUpdate` und `onDelete` bekommen `where = null` für eine Anweisung ohne `WHERE`, also für alle
  Zeilen. Die Zahl, die ein Callback liefert, ist die Zahl der betroffenen Zeilen.
- `SimQuery.filter` ist ein Hinweis zum Vorfiltern (Schritt 5 in `TDS-FA-11.a`), kein Ersatz für die
  Prüfung durch das Backend.
- `primaryKey` nennt eine oder mehrere Spalten der Tabelle; sie müssen vorhanden und nicht nullbar
  sein. Der Schlüssel ist nur Teil der Beschreibung: er wirkt weder auf das Resultset noch auf
  Callbacks, das Backend speichert nichts und prüft keine Eindeutigkeit. Als Metadatum zum Client wird
  er nicht gesendet (Setzung, nicht am Server gemessen).
- **Validierung** (gilt für die Kotlin-Schnittstelle und die Datei): Tabellen- und Spaltennamen sind
  ohne Beachtung der Groß-/Kleinschreibung eindeutig; jede Tabelle hat mindestens eine Spalte und
  einen Schlüssel (Setzung, nicht am Server gemessen).
- **Überführung eines Werts in den Spaltentyp** (`TDS-FA-11.a` Schritt 4; Literal und Parameter;
  Setzung, nicht am Server gemessen): streng und verlustfrei. Gleiche Typfamilie; Datum, Zeit und
  Zeitpunkt nur im ISO-Format (Zeitpunkt nur mit `T`); keine Rundung und keine Überführung zwischen
  Zahl und Text; ein Parameter wird nur verlustfrei erweitert (Int32 zu Int64 zu Decimal). Überlauf
  (Int32, Int64, Stellen der Dezimalzahl) ist immer `TDS_SIM_INVALID_VALUE`. Dezimalwerte werden mit
  `compareTo` verglichen (1.0 gleich 1.00), Textwerte ohne Beachtung der Groß-/Kleinschreibung
  (Setzung, wie die Bezeichner). Texte begrenzt das Backend selbst nicht; ein Text von mehr als 4000
  UTF-16-Einheiten, den eine Zeilenquelle liefert, weist die Typabbildung mit `TDS_UNSUPPORTED_TYPE` ab
  (Limit: `SPEC-003`; Code: `SPEC-014` und Typabbildung; Setzung, nicht gemessen).

**Beschreibung als Datei.** Zusätzlich zur Kotlin-Schnittstelle kann der Server Tabellen aus einer Datei
laden. Das Format ist das neutrale Schema-Modell von d-migrate in YAML, `schema_format` `1.0`; maßgeblich
ist die formale Beschreibung
[spec/schema.json von d-migrate](https://github.com/pt9912/d-migrate/blob/a33984910bc3c760ad2f7c9733f5da0f12e1abf7/spec/schema.json)
(geprüft gegen den Stand dieses Commits, 2026-10-30). Dieses Dokument kopiert das Format nicht; es legt
fest, was der Leser daraus liest. Ändert sich das Format dort, ist der Belegstand zu erneuern.

- **Gelesen:** `schema_format` (muss `1.0` sein), `name` und `version` (Pflicht, nicht ausgewertet),
  `tables`; je Tabelle `columns` und `primary_key` (beide Pflicht, jeder Eintrag des Schlüssels eine
  vorhandene Spalte); je Spalte `type` (Pflicht), `required`, `max_length`, `precision`, `scale`,
  `timezone`, `ordinal`. `tables` ist Pflicht, strenger als das Schema, das nur `schema_format`, `name`
  und `version` verlangt (Setzung, nicht gemessen). Sind alle Spalten einer Tabelle mit `ordinal` versehen, gilt diese Reihenfolge,
  sonst die im Dokument; nur bei einem Teil ist die Datei abzuweisen (Setzung nach der Kurzreferenz von
  d-migrate, an d-migrate nicht gemessen).
- **Ignoriert** (im Schema bekannt, ohne Wirkung): `description`, `encoding`, `locale`, `custom_types`,
  `procedures`, `functions`, `views`, `triggers`, `sequences` in der Datei; `description`, `indices`,
  `constraints`, `partitioning`, `metadata` in der Tabelle; `unique`, `default`, `auto_increment`,
  `generation`, `references` in der Spalte, dazu die typgebundenen Spaltenfelder `length`,
  `float_precision`, `values`, `ref_type`, `element_type`, `geometry_type` und `srid` (im Schema bekannt;
  Setzung, nicht gemessen: ignoriert, die Typen, zu denen sie gehören, weist die Typabbildung ab). Nicht
  erzwungen werden `UNIQUE`, `DEFAULT`, `CHECK`, Fremdschlüssel, Indizes und Identität.
  **Abgewiesen:** jedes Feld, das weder gelesen noch ignoriert wird.
- **Nullbar** ist eine Spalte, die weder `required: true` trägt noch im Schlüssel steht noch den Typ
  `identifier` hat. Der Tabellenname ist der Schlüssel unter `tables`, wörtlich (ein Punkt bleibt Teil
  des Namens), das Schema ist `dbo` (Setzung, nicht am Server gemessen).
- **Typabbildung:** `identifier` auf `Int32` (nicht nullbar), `integer` auf `Int32`, `biginteger` auf
  `Int64`, `boolean` auf `Bool`, `text` und `email` auf `UnicodeText`, `decimal` auf
  `Decimal(precision, scale)` bis Precision 38, `date` auf `Date`, `time` auf `Time`, `datetime` ohne
  `timezone: true` auf `DateTime`. `max_length` wird auf eine positive Ganzzahl geprüft, aber weder
  durchgesetzt noch gemeldet (der Client sieht `NVARCHAR(4000)`, `SPEC-003`). Jeder andere Typ
  (`smallint`, `char`, `float`, `uuid`, `binary`, `xml`, `json`, `array`, `enum`, `geometry`,
  `fulltext`, `datetime` mit `timezone: true`) und eine Precision über 38 weisen die Datei ab.
- **Skalare und Dokument** (Setzung, nicht gemessen): `required` nur `true` oder `false`; Ganzzahlen
  nur in kanonischer Schreibweise (`010`, `0x10` und `yes` werden abgewiesen). Ein Dokument, UTF-8,
  keine Anker, Aliase, Tags und Merge-Schlüssel, nur Skalar-Schlüssel; höchstens 1 MiB, vor dem
  Parsen geprüft.
- **Eine Datei beschreibt nur das Schema:** eine aus ihr geladene Tabelle hat eine leere Zeilenquelle
  und keine Callbacks. Auswahl liefert Metadaten und ein leeres Ergebnis; Einfügen, Ändern und Löschen
  enden mit `TDS_UNSUPPORTED_SQL`. Zeilenquelle und Callbacks bleiben Code des Integrators (Setzung, nicht am Server gemessen).
- **Ladefehler:** Ein Fehler (unbekanntes oder doppeltes Feld, Typ ohne Entsprechung, doppelter Name,
  Schlüssel auf unbekannte Spalte, zu große oder unlesbare Datei) bricht den Start ab, bevor ein Port
  gebunden wird, ohne Teilladen. Die Meldung nennt den Pfad im Dokument, Zeile und Spalte und nie den
  Inhalt. Kein TDS-Fehlercode: beim Laden ist kein Client verbunden (Setzung, nicht am Server gemessen).
- **Verdrahtung:** Die Kotlin-Schnittstelle und eine Umgebungsvariable mit dem Dateipfad (Name nicht
  festgelegt). Die Datei liest `runtime`; das Package `simulation` bekommt einen Baum aus Abbildungen,
  Listen und Skalaren mit Zeile und Spalte und kennt weder die Datei noch die YAML-Bibliothek (Setzung,
  nicht am Server gemessen).

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
  Groß-/Kleinschreibung verglichen (vorläufig, wie die übliche Server-Collation). `"…"` als
  Bezeichner setzt `QUOTED_IDENTIFIER ON` voraus; beides ist an der Referenzinstanz zu messen
  (`SPEC-045`).
- **Nicht erkannt** (`TDS_UNSUPPORTED_SQL`; Verbund, Aggregat, Ausdruck, Unterabfrage und Sortierung
  schließt das Lastenheft wörtlich aus; für die übrigen Fälle gilt die Setzung, dass die Liste der Formen
  abschließend ist, eine Lesart des Lastenhefts, vom Nutzer nicht bestätigt):
  `JOIN` und weitere Relationen, Unterabfragen, `WITH`,
  Aggregate und `GROUP BY`, `DISTINCT`, `TOP`, `ORDER BY`, `OFFSET`/`FETCH`, Aliase für Tabellen und
  Spalten, Ausdrücke und Funktionen in Listen und Bedingungen, `OR`, `NOT`, `<`, `>`, `LIKE`, `BETWEEN`,
  `OUTPUT`-Klauseln, mehrere Anweisungen in einem Text und alles, was keine Form der Tabelle ist.
- Form C wird erst mit dem Clientprofil (`SPEC-004`) gefüllt; bis dahin ist sie leer.
- **Scanner** (Setzungen, nicht am Server gemessen): Leerraum sind Leerzeichen, Tab, CR und LF.
  Blockkommentare dürfen sich schachteln; ein offenes Literal oder ein offener Kommentar ist nicht
  erkannt. `N'…'` gilt nur mit `N` unmittelbar vor dem Hochkomma; ein verdoppeltes Hochkomma steht für
  ein Hochkomma, `]]` in `[…]` und `""` in `"…"` ebenso. Zahlen: Ziffern mit optionalem Dezimalpunkt
  und führendem `-`; Exponent, Hexzahl und `+` sind nicht erkannt, ebenso eine Zahl, an die sich
  Buchstaben anschließen. Unquotierte Bezeichner bestehen aus Buchstaben, Ziffern, `_`, `#` und `$`;
  `SELECT`, `FROM`, `WHERE`, `AND`, `OR`, `NOT`, `IN`, `IS`, `NULL`, `INSERT`, `INTO`, `VALUES`,
  `UPDATE`, `SET` und `DELETE` sind nur quotiert Bezeichner; dreiteilige Namen sind nicht erkannt.
  `INSERT` ohne Spaltenliste, `DELETE` ohne `FROM`, `<>`, `!=` und `NOT IN` sind nicht erkannt. Eine
  Spalte darf in Liste und Bedingungen mehrfach stehen. Datum, Zeit und Zeitpunkt als Literal nur im
  ISO-Format als `'…'`; Parameternamen werden ohne Beachtung der Groß-/Kleinschreibung verglichen. Eine
  Obergrenze für die Länge des Anweisungstextes ist nicht festgelegt.

Der Katalog der Formen, Terme und Werte ist festgelegt. Vorläufig bleiben die Aussagen, die an der
Referenzinstanz zu messen sind (Vergleich von Bezeichnern und Werten, `QUOTED_IDENTIFIER`, Kommentare,
Überführung); die Überschrift behält das Wort „vorläufig“ für sie.

### SPEC-043 — Startprüfung: TLS-Konfiguration und Port

Vor dem Binden des Ports führt der Server mit der konfigurierten TLS-Konfiguration einen
vollständigen Handschlag im Speicher aus, gegen eine Gegenstelle, die nur prüft, dass jedes
Zertifikat der Kette jetzt gültig ist. Wem ein Client vertraut, ist seine Sache; ein abgelaufenes
oder noch nicht gültiges Zertifikat scheitert aber an jedem prüfenden Client. Die Probe nutzt genau
die konfigurierten Protokollversionen und Suiten (`SPEC-023`), nicht die Voreinstellung der
Laufzeit. Scheitert sie, bricht der Start ab, bevor ein Port gebunden ist, und die Diagnose nennt den
Teil:

- `CERTIFICATE`: Kette unlesbar oder jetzt nicht gültig,
- `PRIVATE_KEY`: Schlüssel unlesbar, verschlüsselt oder in einer nicht zugelassenen Form,
- `PAIR`: der Schlüssel gehört nicht zum Zertifikat, oder der Handschlag scheitert aus anderem Grund.

Meldung und Log nennen die gefundene Form, nie einen Dateipfad und nie den Inhalt von Zertifikat
oder Schlüssel, sodass sich die Diagnose protokollieren und dem Konfigurationsschlüssel zuordnen
lässt. Es gibt keinen Rückfall auf eine unverschlüsselte Verbindung; eine Abschwächung folgt nur aus
der ausdrücklichen Konfiguration nach `SPEC-010`. Ein belegter Port beendet den Start mit einer
eigenen Diagnose, die Adresse und Port nennt. Nach jedem fehlgeschlagenen Start laufen keine Threads
und kein Listener des Servers weiter; ein neuer Versuch legt einen neuen Server an. Die Probe prüft
weder, ob ein Client dem Zertifikat vertraut, noch die TDS-Kapselung des Handschlags (`SPEC-028`).

### SPEC-044 — Geordnetes Beenden

Beginn ist die Shutdown-Anforderung mit der Frist `SHUTDOWN_GRACE` (`SPEC-047`; 0 = sofort).

1. Der Listener schließt als Erstes; neue Verbindungen werden nicht mehr angenommen, eine im selben
   Moment angenommene endet sofort.
2. Eine Verbindung ohne laufende Anfrage und ohne unvollständig empfangene Nachricht endet sofort.
3. Eine laufende Anfrage darf bis zu ihrem Abschluss laufen, höchstens bis zum Ablauf der Frist; ihr
   Abschluss wird regulär gesendet, nie ein halber Tokenstrom (`SPEC-031`). Danach beginnt keine
   weitere Anfrage derselben Verbindung: eine nach dem Beginn eingetroffene Anfrage wird mit
   `TDS_SHUTDOWN` beantwortet, danach endet die Verbindung. Von den übrigen Anweisungen eines
   Batches läuft nach dem Beginn nichts mehr.
4. Nach Ablauf der Frist wird jede noch laufende Anfrage wie bei ATTENTION abgebrochen (`SPEC-029`,
   Cancel an das Backend); gelingt das nicht innerhalb `CANCEL_TIMEOUT` (`SPEC-033`), wird die
   Verbindung getrennt.
5. Die Shutdown-Anforderung kehrt zurück, wenn alle Verbindungen beendet sind, spätestens
   `SHUTDOWN_GRACE` plus `CANCEL_TIMEOUT` nach dem Beginn. Eine zweite ist wirkungslos.

Eine Verbindung, die mitten in einer Nachricht kein EOM mehr sendet, bleibt bis zum Ablauf der Frist
offen und wird dann getrennt. Eine Verbindung ohne laufende Anfrage wird ohne Meldung getrennt,
solange das Clientprofil (`SPEC-004`) keine unaufgeforderte Server-Meldung außerhalb einer Antwort
belegt: TDS-Antworten folgen auf Anfragen.

### SPEC-045 — Referenzabgleich: Ablauf und Ausnahmeliste (vorläufig)

Erwartungen an das Verhalten des Servers werden an einer echten SQL-Server-Instanz gemessen, nicht
angenommen. Dieselben Szenarien des Referenzclients laufen gegen die Referenzinstanz (Container, mit
einem Testschema ohne echte Daten) und gegen den Server mit dem Simulations-Backend, das dasselbe
Schema beschreibt (`TDS-FA-11.a`). Es gibt zwei Läufe:

- **Live-Lauf:** Der Referenzclient führt ein Szenario nacheinander gegen beide aus und gibt je
  Schritt eine Zeile aus. Das Szenario umfasst Anmeldung, SELECT mit und ohne Parameter, NULL,
  Unicode, Decimal, Datum, leere und mehrteilige Ergebnisse, Fehler vor und nach Zeilen,
  OUTPUT/RETURNSTATUS (falls freigegeben) sowie Cancel mit Folgeabfrage. Grün ist der Lauf, wenn
  beide Ausgaben gleich sind oder genau der dokumentierten Abweichung entsprechen; eine kleinere
  oder größere Abweichung macht ihn rot.
- **Mitschnitt-Lauf:** Redigierte Paketmitschnitte des Referenzclients gegen die Referenzinstanz
  (`SPEC-004`) werden gegen einen frisch gestarteten Server abgespielt und feldweise verglichen:
  Nachrichten- und Tokenfolge, COLMETADATA (Typ, Länge, Precision/Scale, Nullbarkeit), Zeilenwerte
  und -zahl, DONE-Status und Zählung, ERROR (Nummer, State, Klasse, Text) und ENVCHANGE.

Abweichungen stehen in einer Ausnahmeliste, jede mit Grund und in einer von zwei Arten: *so gewollt*
(der Server weicht bewusst ab und bleibt dabei) oder *Lücke* (ein Fund mit Verweis auf offene
Arbeit; wird er behoben, entfällt die Regel). Eine Regel ist so eng wie möglich: der Pfad der
Abweichung, dazu die Anweisung oder das Szenario. Werte, die der Server selbst vergibt (Objekt-IDs,
Sitzungs-IDs, Zeitstempel, Versionstext), werden nur auf „vorhanden / nicht vorhanden“ verglichen.

Grenzen: Ein Mitschnitt spielt die Anfragen des Clients ab, nicht den Client; fragt der Client wegen
einer anderen Antwort anders, zeigt das nur der Live-Lauf. Das Schema der Referenzinstanz und die
Tabellenbeschreibung des Simulations-Backends haben eine gemeinsame Quelle, die Datei nach `SPEC-035`;
wie das Skript der Referenzinstanz aus ihr entsteht, ist nicht festgelegt. Ein Schema, das nur im Code des
Integrators steht, wird getrennt gepflegt und muss mit dem der Referenzinstanz gemeinsam geändert werden. Mitschnitte enthalten nur Testdaten und keine Secrets. Das
SQL-Server-Image setzt `ACCEPT_EULA=Y` voraus; die Zustimmung des Repo-Inhabers ist in der ADR als Entscheidung festgehalten (rechtlich ungeprüft), der Lauf setzt die Variable ausdrücklich. Gemessen ist nur eine Instanz der Version 2022: `Encrypt=Strict` (TDS 8.0) scheitert, und der Server-Log nennt TLS 1.0 bis 1.2; der Abgleich beider Pfade entfällt dort.

## 3. Defaults und Konstanten

| ID | Name | Wert | Begründung |
|---|---|---|---|
| `SPEC-005` | `LISTEN_PORT` | 1433 (konfigurierbar) | TDS-Standardport |
| `SPEC-006` | `MAX_TDS_PACKET_BYTES` | 65535, zusätzlich protokollkonforme Headerprüfung | Obergrenze des 16-Bit-Längenfelds |
| `SPEC-007` | `MAX_MESSAGE_BYTES` | 16 MiB (vorläufig) | Speicherbegrenzung, gilt während des Zusammensetzens (`TDS-FA-01.a`); mit Clientprofil abgleichen |
| `SPEC-008` | `LOGIN_TIMEOUT` | 15 s (vorläufig) | Hängende Logins begrenzen; die Frist gilt ab Verbindungsaufbau über PRELOGIN, TLS-Handschlag und LOGIN7, danach schließt der Server ohne Antwort; für eine angemeldete Verbindung gilt sie nicht |
| `SPEC-046` | `MAX_PRELOGIN_MESSAGE_BYTES` | 65536 (vorläufig) | Vor abgeschlossenem Login kein großer Puffer: PRELOGIN und LOGIN7 sind klein, eine größere Nachricht wird abgewiesen |
| `SPEC-047` | `SHUTDOWN_GRACE` | 10 s (vorläufig, konfigurierbar; 0 = sofort) | Frist für laufende Anfragen beim geordneten Beenden (`SPEC-044`) |
| `SPEC-009` | `QUERY_TIMEOUT` | 30 s (vorläufig, konfigurierbar) | Backend-Timeout |
| `SPEC-010` | `ENCRYPTION_POLICY` | Standard `REQUIRED`; Abschwächung nur durch ausdrückliche, im Log sichtbare Konfiguration, je Clientprofil | Keine unbemerkte TLS-Herabstufung |
| `SPEC-033` | `CANCEL_TIMEOUT` | 5 s (vorläufig, konfigurierbar) | Frist für nicht abbrechbare Backendoperationen, danach Verbindung schließen |
| `SPEC-034` | `MAX_CONNECTIONS` | 1000 (vorläufig, konfigurierbar) | Ressourcenlimit für gleichzeitige Verbindungen |

Vorläufige Werte sind technische Entwurfsannahmen, keine aus dem Lastenheft abgeleiteten
Abnahmegrenzen.

## 4. Fehler-Codes und Logging-Felder

| ID | Interner Code | Bedingung | Aktion |
|---|---|---|---|
| `SPEC-011` | `TDS_PROTOCOL_ERROR` | Paket/Token ungültig | Abbruch oder ERROR (Severity 20, Setzung), je nach Zustand; nach ERROR schließt der Server |
| `SPEC-012` | `TDS_LOGIN_FAILED` | Authentifizierung fehlgeschlagen | Login-Fehler, Session nicht erstellen; Meldung und State für unbekannte Kennung und falsches Passwort identisch; Nummer 18456, Class 14, einheitlicher State, feste Meldung ohne Kennung und genau ein ERROR vor DONE (am Referenzclient gemessen, am Spike-Testserver und nicht am Produkt: dieselben Antwortbytes für beide Fälle, keine Wiederholung; für das Produkt ist die Gleichheit eine Anforderung). Danach schließt der Server die Verbindung und nimmt kein weiteres LOGIN7 an (Setzung, nicht gemessen; am echten Server wurde nur die Fehlerantwort einmal beobachtet, das Verbindungsende nicht). An einem echten SQL Server wurde einmal beobachtet (vom Review bestätigt): falsches Kennwort und unbekannter Benutzer ergeben beide Nummer 18456, State 1, Class 14, die Meldung nennt den Benutzernamen; der interne State des Servers ist am Client nicht sichtbar. |
| `SPEC-013` | `TDS_UNSUPPORTED_RPC` | RPC nicht unterstützt | ERROR und DONE mit Fehlerstatus |
| `SPEC-014` | `TDS_UNSUPPORTED_TYPE` | Wert nicht serialisierbar | definierter Fehler, kein fehlerhaftes Resultset |
| `SPEC-015` | `TDS_BACKEND_ERROR` | Backend-Ausführung fehlgeschlagen | Fehlerabbildung in TDS |
| `SPEC-016` | `TDS_TIMEOUT` | Frist überschritten | Cancel, Cleanup, Fehler/Disconnect |
| `SPEC-037` | `TDS_UNSUPPORTED_SQL` | Anweisung oder Bedingung außerhalb von `SPEC-036`; Schreibzugriff ohne Callback | ERROR und DONE mit Fehlerstatus, Zeilenquelle nicht gelesen |
| `SPEC-038` | `TDS_SIM_UNKNOWN_OBJECT` | Tabelle oder Spalte ist nicht beschrieben | ERROR und DONE mit Fehlerstatus; vorgesehene SQL-Server-Nummern 208 (Objekt) und 207 (Spalte) |
| `SPEC-039` | `TDS_SIM_INVALID_VALUE` | Wert nicht in den Spaltentyp überführbar | ERROR und DONE mit Fehlerstatus; vorgesehene SQL-Server-Nummern 245 (Konvertierung) und 8115 (Überlauf), an der Referenzinstanz zu messen |
| `SPEC-048` | `TDS_INTERNAL_ERROR` | unerwartete Ausnahme im Server oder im Backend-Adapter | ERROR (Severity 20) und DONE mit Fehlerstatus, fester Text ohne den Text der Ausnahme, danach Verbindungsende; die Ausnahme geht ins Log, ohne Parameterwerte |
| `SPEC-049` | `TDS_SHUTDOWN` | Anfrage nach Beginn des geordneten Beendens (`SPEC-044`) | ERROR und DONE mit Fehlerstatus, danach Verbindungsende; vorgesehen: die SQL-Server-Meldung für ein Herunterfahren, Nummer im Mapping nach `TDS-FA-06.a` festlegen |

Die Nummern in der Aktionsspalte sind ein Vorschlag für das Mapping nach `TDS-FA-06.a`. Für
`TDS_UNSUPPORTED_SQL` gibt es keine SQL-Server-Entsprechung, denn dort ist die Anweisung gültig; die
Nummer kommt aus dem Benutzerbereich (ab 50001).

**Zuordnung des Fehler-Mappings** (`TDS-FA-06.a` Nr. 2 und 6; Träger:
`ErrorCode` im Package `protocol.error`). Kein Wert dieser Tabelle ist an einem echten SQL Server
gemessen. Die Spalte *Herkunft* trennt: *Vorschlag* (Nummer aus der Aktionsspalte oben,
„an der Referenzinstanz zu messen“), *Setzung des Nutzers* (Gruppe und Class, 2026-10-30),
*Voreinstellung* (Gruppe nach der Voreinstellung vom 2026-10-30, nicht entschieden), *Festlegung* (vom Mapping gewählt, nicht
gemessen), *Grundregel* (nur die Folge: kein Gruppenmitglied, im Zweifel schließen; die Class 20 dieser Codes ist Festlegung). State ist für jeden Code 1, der
Text fest und englisch; der Text einer Backend-Meldung oder Ausnahme erreicht den Client nie.

| Code | Nummer | Class | Folge nach ERROR und DONE | Herkunft |
|---|---|---|---|---|
| `TDS_PROTOCOL_ERROR` | 50006 | 20 | schließt | Class und Folge: Setzung des Nutzers; Nummer: Festlegung |
| `TDS_LOGIN_FAILED` | 18456 | 14 | schließt | Nummer, Class: `SPEC-012`; Folge: Setzung; Bytes gleich der Antwort des Handshakes |
| `TDS_UNSUPPORTED_RPC` | 50002 | 16 | offen | Gruppe: Voreinstellung; Nummer: Festlegung |
| `TDS_UNSUPPORTED_TYPE` | 50003 | 20 | schließt | Folge: Grundregel; Class 20: Festlegung, Einzelzuordnung offen; Nummer: Festlegung |
| `TDS_BACKEND_ERROR` | 50004 | 16 | offen | Class und Folge: Setzung des Nutzers; Nummer: Festlegung |
| `TDS_TIMEOUT` | 50005 | 20 | schließt | Folge: Grundregel; Class 20: Festlegung; offen, ob Cancel/Timeout als Fehler gelten; Nummer: Festlegung |
| `TDS_UNSUPPORTED_SQL` | 50001 | 16 | offen | Class und Folge: Setzung des Nutzers; Nummer: Vorschlag (Benutzerbereich) |
| `TDS_SIM_UNKNOWN_OBJECT` | 208 | 20 | schließt | Nummer: Vorschlag (207 für Spalten nicht abgebildet); Folge: Grundregel; Class 20: Festlegung, Einzelzuordnung offen; der echte SQL Server sendet 208 mit Class 16 (Messung R05), hier schließt der Client bei Class 20 selbst |
| `TDS_SIM_INVALID_VALUE` | 245 | 16 | offen | Nummer: Vorschlag (8115 für Überlauf nicht abgebildet); Gruppe: Voreinstellung |
| `TDS_INTERNAL_ERROR` | 50007 | 20 | schließt | Class und Folge: Setzung des Nutzers; Nummer: Festlegung |
| `TDS_SHUTDOWN` | 50008 | 20 | schließt | Folge: Setzung des Nutzers (`SPEC-049`); Nummer und Class: Festlegung (die SQL-Server-Nummer für ein Herunterfahren ist nicht gemessen) |

Das DONE trägt für jeden Code das Statusbit `ERROR`, Befehlskennung 0 und Zeilenzahl 0; ob der
Referenzclient es so annimmt, ist nicht am Produkt gemessen. Am echten SQL Server trägt das DONE bei
Class 20 und 25 zusätzlich das Statusbit `SRVERR` und es folgen weitere ERROR-Token (Messung R10); das
Mapping sendet für jeden Code nur ERROR und DONE mit `ERROR`, diese Abweichung ist nicht am Produkt
gemessen. Ein Backend meldet als `Failure` nur `TDS_BACKEND_ERROR`, `TDS_UNSUPPORTED_SQL`,
`TDS_UNSUPPORTED_TYPE`, `TDS_SIM_UNKNOWN_OBJECT`, `TDS_SIM_INVALID_VALUE` und `TDS_TIMEOUT`
(Festlegung); jeder andere Code, auch ein anderer eigener wie `TDS_LOGIN_FAILED` oder `TDS_SHUTDOWN`,
erreicht den Client als `TDS_INTERNAL_ERROR`. Ist der Strom nicht synchron, entfällt die
Antwort und die Verbindung schließt (`SPEC-031`). Der Log-Eintrag einer unerwarteten Ausnahme trägt den
Code und den Typnamen der Ausnahme, nicht ihren Text: das ist enger als der Satz in `SPEC-048`, die
Ausnahme gehe ins Log. Offen: die Einzelzuordnung der Codes unter *Grundregel* und die Frage, ob Cancel
und Timeout als Fehler zählen; die Unterscheidung statement- und batch-abbrechender Fehler; die Messung
aller Werte (`BEO-TDS/messung-am-echten-sql-server-fehlt`).

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
| `SPEC-020` | Microsoft TDS | TDS 7.4 und TDS 8.0 (Strict-Encryption) | [MS-TDS](https://learn.microsoft.com/en-us/openspecs/windows_protocols/ms-tds/) |
| `SPEC-021` | .NET SQL-Client | `Microsoft.Data.SqlClient` 7.1.1 auf .NET 10 (SDK 10.0.403), im Container ausgeführt | `contracts/client-profile.md` |
| `SPEC-022` | Query-Backend | Projektvertrag v0: Typen und Schnittstellen als Quelltext (Setzung vom 2026-10-30, nicht gemessen), Rest Entwurf | `contracts/query-backend.md` |
| `SPEC-023` | TLS | TLS 1.3 und TLS 1.2 (Mindestversion 1.2); Suiten TLS 1.3: `TLS_AES_256_GCM_SHA384`, `TLS_AES_128_GCM_SHA256`; TLS 1.2: ECDHE mit AES-GCM; Standard `REQUIRED` | `contracts/security-profile.md` |
| `SPEC-051` | Authentifizierungsadapter | Entwurf der Schnittstelle; Prüfwert der festen Benutzerliste mit Verfahrenskennung (`plain` nur im Testmodus, `pbkdf2-sha256`), eigene Datei nur für die Benutzerliste, Iterationen mit Boden, Standard und Obergrenze, strenger Parser; Einzelheiten des Zeilenformats im Vertrag teils offen | `contracts/auth-adapter.md` |

## 7. Festlegungen der Harness-Werkzeuge

| ID | Werkzeug | Festlegung |
|---|---|---|
| `SPEC-024` | Unit-Test-Gate | Unit-Tests für Decoder, Zustandsautomat, Encoder und Fehlerabbildung müssen erfolgreich sein; Gate: `make test` |
| `SPEC-025` | Integrationstest-Gate | Containerisierter Server + echter .NET-Referenzclient (`Microsoft.Data.SqlClient` 7.1.1, .NET 10) prüfen Login, SQL_BATCH, RPC, Typen, Fehler, Cancel; ohne lokale .NET-Installation und ohne Mount, Basis-Images per Digest, Pakete per Lock und Hash; Gate noch einzurichten |
| `SPEC-026` | `make gates` | Aggregator: führt alle eingerichteten Gates aus; neue Gates für Formatierung, statische Prüfung und Tests werden in ihn aufgenommen |
| `SPEC-027` | Fuzz-/Property-Tests | Defekte Paketlängen, Offsets, Fragmentierung und Typwerte dürfen keinen ungefangenen Prozessabsturz verursachen |
| `SPEC-040` | Simulations-Test-Gate | Unit-Tests der Anweisungserkennung (jede Form aus `SPEC-036` positiv und negativ, Grenzen bei Literalen, Bezeichnern und Kommentaren), Unit-Tests des Lesers der Tabellenbeschreibung (jedes gelesene Feld, jeder Ladefehler mit Pfad, Zeile und Spalte, ohne Inhalt in der Meldung) und Integrationstest mit dem .NET-Referenzclient: Auswahl, leeres Ergebnis, Zeilenquelle über der Speichergrenze, Fehler der Zeilenquelle, Cancel; Gate noch einzurichten |
| `SPEC-041` | Static-Analysis-Gate (`make detekt`, `make detekt-selbsttest`) | detekt in Docker, Konfiguration auf Standardbasis (`config/detekt/detekt.yml`) mit dem ktlint-Regelsatz (prüft, schreibt nichts um), `warningsAsErrors` und den Importregeln der Schichten, im Kern als Erlaubnisliste ohne Fremdbibliothek; dazu Technik-Regeln für Kern und Port, die Prozess, Dateisystem und Ähnliches über `ForbiddenMethodCall` sperren und deshalb nur über `detektMain` und `detektTest` wirken (Typauflösung); keine Baseline; jeder Befund bricht den Lauf. Tests des Kerns bleiben frei von Fremdbibliotheken (`kotlin.test` statt JUnit), weil die Kernregeln auch für Test-Quellen gelten. Die Importregeln lesen nur `import`-Zeilen: ein voll qualifizierter Name im Code und ein `typealias` darauf umgehen sie; das Gate belegt die Einhaltung der Importgrenze, nicht des Classpaths. `@Suppress` und `@SuppressWarnings` im Quelltext brechen den Lauf vor detekt (Textprüfung `tools/suppression-check.sh`). Ein Selbsttest belegt, dass jede Importregel ihre verbotenen Importe meldet, die erlaubten Testdaten nicht und das Suppression-Verbot jede Form meldet; er läuft nur bei geänderten Eingängen |
| `SPEC-042` | Build-Gate (`make build`) | Compile und Jar in Docker, ohne Tests; Basis-Images per Digest gepinnt, Abhängigkeitsversionen per Lock und Prüfsummen fixiert |
| `SPEC-050` | Referenzabgleich-Gate (vorläufig) | Live-Lauf und Mitschnitt-Lauf nach `SPEC-045` gegen eine SQL-Server-Referenzinstanz in Docker (eigener Container im eigenen Netzwerk, ohne Mount); Abweichungen nur über die Ausnahmeliste; ergänzt die Läufe von `SPEC-025` und `SPEC-032`, ersetzt sie nicht; Gate noch einzurichten |
| `SPEC-032` | Kompatibilitätstest-Gate | Echter .NET-SqlClient prüft LOGINACK/ENVCHANGE/DONE, TLS-Profil (TDS 7.4 und TDS 8.0), RPC-Parameter, Streaming-Fehler, ATTENTION und Wiederverwendung; ein Szenario ist rot, sobald der Client es nicht besteht; Gate noch einzurichten |

## 8. Historie

| Datum | Änderung |
|---|---|
| 2026-10-29 | Überarbeitung: TLS-Kapselung, Login-Token, RPC, Streaming, ATTENTION, Mehrfachergebnisse und Abnahmetests präzisiert |
| 2026-10-29 | `TDS-FA-11.a` ergänzt: Simulations-Backend mit Ablauf, Tabellenbeschreibung, Anweisungsformen, Fehlercodes und Test-Gate (`SPEC-035` bis `SPEC-040`) |
| 2026-10-29 | Static-Analysis-Gate und Build-Gate festgelegt (`SPEC-041`, `SPEC-042`) |
| 2026-10-29 | Startprüfung, geordnetes Beenden, Referenzabgleich, Größengrenzen vor dem Login, Fehlerabbildung ohne Ausnahmetext und gleiche Ablehnung unbekannter Kennungen präzisiert (`SPEC-043` bis `SPEC-050`) |
| 2026-10-30 | Authentifizierungsadapter festgelegt: SQL-Authentifizierung über Schnittstelle, Einstiegsimplementierung mit fester Benutzerliste, feste Ablehnungsantwort (`SPEC-051`, `SPEC-012`, `TDS-FA-02.a`) |
| 2026-10-30 | Korrektur nach Review: Zeitgleichheit als Anforderung an den Produktivcode, Log-Pflicht für Passwort und Kennung (`TDS-FA-02.a`, `TDS-FA-09.a`) |
| 2026-10-30 | Testumgebung für den Referenzclient und den Referenzabgleich festgelegt (`SPEC-021`, `SPEC-025`, `SPEC-032`, `SPEC-045`, `SPEC-050`) |
| 2026-10-30 | Unit-Test-Gate an `make test` gebunden (`SPEC-024`) |
| 2026-10-30 | Prüfwertformat und Konfigurationsweg der Benutzerliste benannt (`SPEC-051`) |
| 2026-10-30 | Iterationszahlen, Passwort-Randfälle, Parserregeln und Zeilenformat der Benutzerliste in den Vertrag übernommen (`SPEC-051`) |
| 2026-10-30 | Nach Setzungen des Nutzers geschärft: Zustand als expliziter Automat mit Sperre je Sitzung, Zuschnitt der Aufzählung offen (`SPEC-001`); Ergebnis als Strom mit Vorschau für `DONE_MORE` (`SPEC-002`, `SPEC-022`); Verbindung bleibt offen nach DONE mit ATTENTION-Status, außer im geordneten Beenden (`SPEC-029`); Verbindungsende nach ERROR und DONE bei internem Fehler und Shutdown, `SPEC-049` verlangt jetzt zusätzlich DONE (`SPEC-048`, `SPEC-049`); Verbindung offen bis Severity 19 (`TDS-FA-06.a`). `SPEC-048`, `SPEC-049` und die Schwelle ab Severity 20 sind Setzungen des Nutzers, am echten Server nur für Severity 20 und 25 gemessen; Handshake-Ende, Severity eigener Codes und Cancel/Timeout als Fehler bleiben offen |
| 2026-10-30 | Nach weiteren Setzungen des Nutzers geschärft: Verbindungsende nach abgelehntem Login (`SPEC-012`), Severity 16 für Anfragefehler und 20 für `TDS_PROTOCOL_ERROR` und `TDS_INTERNAL_ERROR` (`TDS-FA-06.a`, `SPEC-011`, `SPEC-048`), nicht abschließbarer Strom ohne ERROR und DONE geschlossen (`SPEC-029`, `SPEC-031`); alles Setzung, nicht an Referenzclients gemessen; die Zuordnung jedes Codes legt das Fehler-Mapping fest, Cancel/Timeout als Fehler bleibt offen |
| 2026-10-30 | Nach Re-Review geschärft: eigene Codes ohne Einzelzuordnung schließen bis zur Zuordnung durch das Fehler-Mapping (`TDS-FA-06.a`); Messklammer in `SPEC-012` an die Antwortbytes gebunden, das Verbindungsende als Setzung getrennt (`SPEC-012`) |
| 2026-10-30 | Backend-Vertrag auf den gebauten Stand geschärft: `SqlType` definiert, Ort der Typen und Fehlerform beim Start als Setzung, Precision-Obergrenze und weitere Punkte als offen geführt (`SPEC-002`, `SPEC-022`, `TDS-FA-08.a`) |
| 2026-10-30 | Typabbildung nachgeführt: Precision über 38 scheitert in der Typabbildung, Zeit ohne Zone mit Scale 7, Unicode-Text `NVARCHAR(4000)` und Decimal ohne Rundung als Setzungen (`SPEC-002`, `SPEC-003`) |
| 2026-10-30 | Zuordnung des Fehler-Mappings je Code (Nummer, Class, State, Text, Verbindungsfolge) in §4 festgehalten, jede Zeile nach Herkunft gekennzeichnet; Einzelzuordnung der Codes ohne Gruppe offen (`TDS-FA-06.a`, `SPEC-011` bis `SPEC-016`, `SPEC-037` bis `SPEC-039`, `SPEC-048`, `SPEC-049`) |
| 2026-10-30 | Simulations-Backend festgelegt: Beschreibung zusätzlich als Datei im neutralen Schema-Modell von d-migrate, Typabbildung, strenge Überführung, Schlüssel nur beschreibend, Scanner-Regeln (`SPEC-035`, `SPEC-036`, `TDS-FA-11.a`); Vergleich von Bezeichnern und Werten sowie Überführung bleiben Setzungen, nicht am echten SQL Server gemessen |
| 2026-10-30 | Simulations-Backend nachgeführt (`SPEC-035`, `SPEC-036`, `SPEC-040`, `SPEC-045`): Ignorierliste um sieben Spaltenfelder, `tables` als Pflicht, Längengrenze der Typabbildung, Setzungsvermerke, Lesart der Liste „Nicht erkannt“ als Setzung, Tests des Lesers, gemeinsame Quelle der Schemata |
