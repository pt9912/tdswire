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
   konstanter Zeit und führt bei unbekannter Kennung einen Scheinvergleich aus; gebaut und
   strukturell belegt; die Zeit ist für Listen mit gleicher Iterationszahl gemessen, für gemischte Listen
   ist die Zeitgleichheit nicht gegeben (Abschnitt „Kotlin-Signatur des Authentifizierungsadapters“). Der Adapter hat die Schnittstelle
   aus dem Vertrag zu `SPEC-051`; die Einstiegsimplementierung ist eine feste
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
   dem Text der Ausnahme; dieser kann Konfigurationswerte enthalten und geht nicht ins Log: das Log
   trägt Code und Typnamen der Ausnahme (`SPEC-048`, Abschnitt 4).
6. Nach ERROR und DONE mit Fehlerstatus bleibt die Verbindung offen, wenn die Severity (Class) höchstens
   19 ist. Ab Severity 20, nach `TDS_INTERNAL_ERROR` (`SPEC-048`) und nach `TDS_SHUTDOWN` (`SPEC-049`)
   schließt der Server die Verbindung nach ERROR und DONE. Ist der Strom nicht mehr sicher abzuschließen,
   schließt der Server die Verbindung ohne ERROR und DONE (`SPEC-031`); ERROR und DONE gibt es nur,
   solange der Strom synchron ist. Nach einem Cancel bleibt sie offen, wenn DONE
   mit ATTENTION-Status gesendet wurde (`SPEC-029`); ein Cancel ist kein Fehler und trägt kein ERROR. Nach dem
   Ablauf der Frist einer Anfrage (`TDS_TIMEOUT`, `SPEC-016`) bleibt sie offen, wenn ERROR und DONE mit Fehlerstatus
   vollständig gesendet sind. Ausnahme: Nach einem abgelehnten Login (`SPEC-012`)
   schließt der Server die Verbindung, ein weiteres LOGIN7 wird nicht angenommen. Eigene Fehlercodes:
   Anfragefehler (zum Beispiel `TDS_UNSUPPORTED_SQL`, `TDS_BACKEND_ERROR`) tragen Severity 16, die
   Verbindung bleibt offen; `TDS_PROTOCOL_ERROR` und `TDS_INTERNAL_ERROR` tragen Severity 20, der Server
   schließt. Die Zuordnung jedes Codes legt das Fehler-Mapping fest und steht in §4 (Zuordnung des Fehler-Mappings; die Einzelzuordnung von `TDS_UNSUPPORTED_TYPE` und `TDS_SIM_UNKNOWN_OBJECT` ist offen und steht unter der Grundregel); `TDS_UNSUPPORTED_RPC` und `TDS_SIM_INVALID_VALUE` gehören zur Anfragefehler-Gruppe (Setzung, Voreinstellung 2026-10-30, nicht gemessen), `TDS_TIMEOUT` ebenfalls (Setzung des Nutzers vom 2026-11-01, nicht gemessen). Für jeden weiteren eigenen Code, der zu keiner Gruppe gehört, gilt bis zur Zuordnung, dass der Server schließt (im Zweifel schließen). Das Verbindungsende nach abgelehntem
   Login und die Severity-Werte der eigenen Codes sind Setzungen, nicht an Referenzclients gemessen (am
   echten Server wurde nur die Fehlerantwort des Logins einmal beobachtet).

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
3. Sitzungs- und Fehlerereignisse mit `connection_id` und `session_id` als Ereignisse an die Senke der
   Bibliothek geben (die Diagnoseausgabe des Lastenhefts; Felder und Zugang in den Abschnitten 4 und 5
   dieser Spezifikation; gebaut); ohne übergebene Senke schreibt die Bibliothek nichts (gebaut).
   **Zielzustand, nicht gebaut:** Ereignisse mit `request_id` und Anfrage-Ereignisse. Passwörter werden nie geloggt, auch nicht gekürzt oder
   verschleiert; Parameterwerte nie. Die Anmeldekennung erscheint in keiner Antwort an den Client
   und im Log nur im Feld `principal` des Ereignisses „Anmeldung angenommen“ (Abschnitt 4).

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
   `INTN` 4; so beantwortete es der Testserver; an einem echten SQL Server gemessen: Ordinal 0 für das Handle von
   `sp_prepexec`, Ordinal 2 für einen OUTPUT-Parameter an dritter Stelle von `sp_executesql`, je ein Fall;
   die Zählung darüber hinaus ist nicht gemessen und gegen MS-TDS zu prüfen) und je ein RETURNVALUE je OUTPUT-Parameter.
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
laufenden Anfragezustand zulässig und wird in `READY` bestätigt (unten). Der Zustand ist ein expliziter Automat: eine Aufzählung der
Zustände und eine reine Übergangsfunktion (Zustand, Ereignisart) → (Folgezustand, Aktion). Ungültige
Übergänge werden abgelehnt: die Verbindung schließt ohne Antwort (Festlegung des Baus, nicht gemessen; ob
außerhalb des Handshakes stattdessen ein Fehler gesendet wird, ist offen). ATTENTION in PRELOGIN,
TLS-Aushandlung und LOGIN7 schließt die Verbindung ohne Antwort und wird weder beantwortet noch ignoriert
(Setzung des Nutzers vom 2026-11-01; der Testserver des Spike schloss auf rohe Pakete in 11 von 11 Fällen ohne
Antwortbytes, an einem echten Server ist es nicht gemessen). Der Zuschnitt der Aufzählung ist flach
(Setzung des Nutzers, nicht gemessen für den Anfragezustand): eine Aufzählung führt Verbindungs- und
Anfragezustände, `READY` ist der Anfragezustand `IDLE`.

Anfragezustand (nur nach dem Login): `IDLE → RUNNING → STREAMING → COMPLETED → IDLE`. Bei ATTENTION
oder beim Ablauf der Frist einer Anfrage (`SPEC-009`) führen `RUNNING` und `STREAMING` über `CANCELLING` nach
`COMPLETED`; `CANCELLING` ist ein Zustand der Aufzählung (flacher Zuschnitt). Der Response
Sequencer kennt den Anfragezustand nicht; der Sitzungsrahmen hält ihn und entscheidet jeden Übergang
atomar je Sitzung: Übergang und das zugehörige Schreiben laufen unter einer Sperre je Sitzung. Der Dispatch
meldet dem Rahmen den Beginn des ersten Ergebnisses (`RUNNING → STREAMING`) und das Ende der Antwort; das
Ende der Antwort sendet sie und führt in einem Übergang nach `IDLE` oder, wenn die Verbindungsfolge des
Ausgangs schließt, nach `CLOSED` (`COMPLETED` ist kein eigener Zustand der Aufzählung; Festlegung, nicht
gemessen). Eine Anfragenachricht (SQL_BATCH, RPC) in `RUNNING` oder `STREAMING` ist ein unzulässiger
Übergang. Die Anfrageschleife liest während einer Anfrage mit und meldet ein erkanntes ATTENTION-Paket an den
Sitzungsrahmen; bei gleichzeitigem DONE und ATTENTION oder bei Frist und ATTENTION gewinnt der erste
Übergang, und ab `CANCELLING` schreibt die reguläre Antwort nicht mehr.

ATTENTION in `RUNNING` und `STREAMING` löst genau einen Cancel aus und wird mit genau einem `DONE` mit
ATTENTION-Status beantwortet (`SPEC-029`). Trifft ATTENTION nach `COMPLETED` oder in `IDLE` ein, bleibt das
bereits gesendete Ergebnis gültig, und der Server beantwortet das ATTENTION mit derselben Bestätigung, ohne
Cancel, damit der Client nicht auf sie wartet (Setzung; am echten Server ist nur ATTENTION nach vollständig
gesendeter Antwort gemessen, im Leerlauf ohne laufende Anfrage nicht). Offen: die Antwort auf unzulässige
Übergänge außerhalb des Handshakes und ATTENTION in jedem Anfragezustand über diese Bestätigung hinaus.

RESETCONNECTION: Trägt das erste Paket einer Anfrage-Nachricht (SQL_BATCH, RPC, Transaction Manager) in `IDLE`
das Statusbit `RESETCONNECTION` (`0x08`), setzt der Sitzungsrahmen den Sitzungszustand zurück, mindestens die Handle-Tabelle
(`SPEC-030`): im Übergang, der die Anfrage annimmt, unter der Sperre der Sitzung und vor der Verarbeitung dieser
Anfrage. Die Anfrage mit dem Bit wird danach wie jede andere beantwortet (ein Transaction Manager bleibt unbeantwortet
und die Verbindung schließt wie ohne das Bit). Das Zurücksetzen ist eine Aktion am
Übergang, kein Zustand der Aufzählung. Wer weiteren Sitzungszustand einführt (heute führt der Server nur die
Handles), setzt ihn an dieser Stelle zurück. Das Bit in einem anderen Zustand als `IDLE` wertet der Server nicht
aus: eine Anfrage dort ist ein unzulässiger Übergang, an ATTENTION und an den Nachrichten des Handshakes bleibt
das Bit ohne Wirkung; das Statusbit `0x10` (RESETCONNECTIONSKIPTRAN) wertet der Server nicht aus und es wirkt
nicht wie `0x08`, weil der Server keine Transaktion führt (Festlegung, nicht gemessen). Gemessen am Referenzclient (`Microsoft.Data.SqlClient` 7.1.1,
TDS 7.4, `Pooling=true`, eine physische Verbindung): über mehrere Open-Phasen gelingen mehr als 1000 vorbereitete
Befehle (das 1001. einer Open-Phase endet weiter mit `SPEC-058`); das Bit ist damit über seine Wirkung beobachtet,
am Draht nicht gesehen. Die Antwort auf die Anfrage mit dem Bit trägt kein ENVCHANGE: der Referenzclient nahm sie so an;
ob und wie die Antwort eines echten Servers mit ENVCHANGE beginnt, ist nicht gemessen.

Der Abschluss einer Anfrage wird genau einmal gesendet.

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
Bit `DONE_MORE`; das Backend kennt das Bit nicht. Entwurf des Vertrags: `SPEC-022`.

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

**Zeilendarstellung und Klassen der Werte (Nutzerentscheidung vom 2026-10-31):** `Row.values` ist eine
`List<Any?>`. Die JVM-Klasse des Werts je Spaltenart: `Int32` als `Int`, `Int64` als `Long`, `Bool` als
`Boolean`, `UnicodeText` als `String`, `Decimal` als `java.math.BigDecimal`, `Date` als
`java.time.LocalDate`, `Time` als `java.time.LocalTime`, `DateTime` als `java.time.LocalDateTime`, NULL als
`null`. Die Klassen gelten auch für `QueryParameter.value` und `OutputParameter.value` (Setzung,
Voreinstellung 2026-10-30, nicht gemessen); `OutputParameter` lehnt nur die Antwort auf einen
Batch ab (`SPEC-031`); in der Antwort auf einen RPC wird er abgebildet. Die Liste hat so viele Werte wie
das offene Ergebnis Spalten; das Backend verändert sie nach der Übergabe nicht und verwendet sie nicht wieder (Setzung, Voreinstellung 2026-10-30, nicht
gemessen). Die Heap-Grenze einer Verbindung bestimmt der Strom mit dem Rückstau der Senke, nicht die
Darstellung.

**Frist (Nutzerentscheidung vom 2026-10-31):** `deadlineEpochMillis` ist für das Backend ein Hinweis; die
Frist setzt der Protokollkern durch (Einzelheiten im Vertrag zu `SPEC-022`, Quelle `QUERY_TIMEOUT`
in `SPEC-009`).

**Offen:**
- Die Unterscheidung statement-
  und batch-abbrechender Fehler im Strom, die `Failure` nicht trägt; Einzelheiten und Zuständigkeit im
  Vertrag zu `SPEC-022`.

Lebensdauerregeln und alles, was über diesen Stand hinausgeht, bleiben Entwurf des Vertrags
zu `SPEC-022`.

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
Verbindungs-Pooling unter TDS 8.0 (unter TDS 7.4 ist das RESETCONNECTION-Statusbit der Paketheader am Referenzclient über seine Wirkung gemessen, `SPEC-001`) und Lastprofil.
Die unterstützten RPCs gibt das Clientprofil erst frei, wenn die Messgrenzen aus `SPEC-030` geklärt sind
(TDS 8.0, Pooling, echter Server, `SET`-Text bei SchemaOnly und KeyInfo, Transaktionen).
Die Authentifizierung ist SQL-Authentifizierung (Kennung und Passwort im LOGIN7, `SPEC-051`).
Form C von `SPEC-036` füllt kein Anweisungsprofil des Referenzclients: ihre Texte stammen vom Prüfclient d-migrate
(`TDS-FA-12`), der nicht Teil dieses Profils ist; für den Referenzclient ist keiner gemessen.
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
regulärer Zeilen und beantwortet das ATTENTION mit genau einem `DONE` mit ATTENTION-Status, ohne ERROR-Token
und ohne zweite Nachricht (Setzung des Nutzers vom 2026-11-01; am Testserver des Spike genügte diese Form dem
Referenzclient und die Folgeabfrage gelang, ohne die Bestätigung hing der Client bis zum Timeout; an einem
Server dieses Repos und an anderen Clients ist sie nicht gemessen). Ein Cancel ist kein Fehler: es gibt kein
ERROR, auch nicht bei einem Cancel, den der Client durch seinen Command-Timeout auslöst. Die Sitzung nimmt erst
nach vollständigem Abbruchabschluss wieder eine neue Anfrage an (Anfragezustand `IDLE`). Bei Race zwischen
DONE und ATTENTION oder zwischen Frist und ATTENTION gilt die Regel der Anfragezustandsmaschine in `SPEC-001`:
der erste Übergang gewinnt, doppelte DONE-Tokens sind verboten. War die Antwort beim Gewinn des Übergangs schon
vollständig gesendet, wird das ATTENTION als eigene Nachricht bestätigt, ohne Cancel; ebenso ein ATTENTION nach dem
Abschluss eines Timeouts und jedes weitere ATTENTION: ein wiederholtes ATTENTION löst keinen zweiten Cancel aus, und jedes wird einzeln
bestätigt (Setzung, nicht gemessen; ob ein Client das erwartet, zeigt erst das Cancel-Szenario am Referenzclient).
Der Abbruch unterbricht kein Paket im Schreiben: er wirkt, sobald der Schreibvorgang, der gerade läuft, zurückgekehrt ist
(Festlegung, nicht gemessen). Grenze: ein Schreibvorgang, der am Rückstau hängt (der Client liest nicht mehr), hält die
Sitzungssperre; bis zu seiner Rückkehr wirken ATTENTION, die Frist der Anfrage und `CANCEL_TIMEOUT` nicht. Wenn ein bereits gesendeter Stream nicht mehr sauber abgeschlossen werden kann,
wird die Verbindung ohne ERROR und DONE geschlossen (`SPEC-031`). Beachtet das Backend den Cancel nicht,
schließt die Verbindung nach `CANCEL_TIMEOUT` (`SPEC-033`) ohne weitere Bytes; nur dieser ignorierte Cancel
schließt, nicht der Cancel selbst. Nach gesendetem DONE mit ATTENTION-Status bleibt die Verbindung offen, außer das
geordnete Beenden (`SPEC-044`) hat begonnen; dann endet sie. Meldet das Lesen während einer Anfrage das Ende der
Gegenstelle oder einen Fehler (Disconnect: Schließen mit oder ohne RST), gilt das in `RUNNING`, `STREAMING` und
`CANCELLING`: die Sitzung schließt, die Backendoperation bekommt genau einmal Cancel, ihr Strom wird geschlossen, und
es wird nichts mehr auf die Verbindung geschrieben, auch keine Bestätigung. Ein Ende der Gegenstelle zählt dabei als
Disconnect, auch wenn der Client nur seine Senderichtung geschlossen hätte (Festlegung, nicht gemessen; kein Client
des Profils tut das). Tests prüfen `SqlCommand.Cancel()`,
`CancellationToken` und unmittelbar anschließende
neue Befehle.

### SPEC-030 — RPC-Varianten und Parameter-Metadaten

RPC-Nachrichten müssen Prozedurname/-ID, Optionsflags, Parameternamen, Statusflags (inklusive
OUTPUT), TYPE_INFO, Längenpräfixe, NULL-Sentinels, Kollation sowie Precision/Scale typabhängig
korrekt dekodieren. Für `sp_executesql` werden SQL-Text und Parameterdefinitionen getrennt vom
Wertetransport gehalten. Der Katalog unterstützter RPCs ist der Mindestkatalog, den die Messung des Referenzclients trägt
(`SPEC-021`); die Messgrenzen stehen unter der Tabelle. Die Zeilen "Pflichtverhalten"
zum Fehlerfall (unbekanntes Handle, unbekannte Form) sind Festlegungen, nicht Messwerte:

| RPC | Erkennung | Pflichtverhalten |
|---|---|---|
| `sp_executesql` | ProcID 10 | Parameter 0 SQL-Text, Parameter 1 Deklarationsliste, optional (beide ohne Namen); danach Wertparameter, benannt oder ohne Namen (unten), INPUT, NULL und OUTPUT/InputOutput (Statusbyte `0x01`) |
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
eine Verbindung je Szenario, ein Lauf. An einem Microsoft-SQL-Server (Referenzclient über einen mitschneidenden Proxy, TDS 7.4,
`Encrypt=false`, 17 Szenarien) gemessen sind Tokenreihenfolge, Statusbits der DONE-Familie und je ein Ordinal der RETURNVALUE;
darüber hinaus sind sie gegen MS-TDS zu prüfen. **Offen:** die Behandlung der `SET`-Anweisungen im Batchtext von
`ExecuteReader(SchemaOnly)` (`SET FMTONLY`) und im Text von
`ExecuteReader(KeyInfo|SingleRow)` (`SET FMTONLY`, `SET NO_BROWSETABLE`) und die Transaktionen
(Pakettyp `0x0E`, kein RPC, nicht im Katalog).

Text ohne Deklarationsliste und Wertparameter ohne Namen. Gemessen am 2026-11-05 mit d-migrate 1.12.0 (Treiber
mssql-jdbc 13.4.0.jre11) gegen den Testserver: Für eine Anweisung ohne Parameter sendet der Treiber `sp_executesql` mit
genau einem Parameter, dem Text, ohne Deklarationsliste (`SELECT * FROM [kunden] WHERE 1 = 0`). Für eine Anweisung mit
Parameter sendet er den Text, die Deklarationsliste (`@P0 bigint`) und den Wert als `INTN` 8 mit Namenslänge 0, also
ohne Namen. Die Bibliothek nimmt beide Formen an (`INVALID_RPC_FORM` bei weniger als einem Parameter). Festlegung (Setzung, nicht gemessen): Der Text an Position 0 ist Pflicht und ein NVARCHAR ohne NULL; die
Deklarationsliste ist optional und fehlt als leere Liste, sie wird nicht gedeutet. Ein Wertparameter ohne Namen von
`sp_executesql`, `sp_prepexec` und `sp_execute` heißt `@P<i>` mit `i` als Stelle unter den Wertparametern, gezählt ab 0
ohne Text, Deklarationsliste und Handle; die Namen, die der Treiber in der Deklarationsliste nennt (`@P0`, `@P1`, ...),
sind dieselben. Ein so gebildeter Name, der einen gesendeten Namen doppelt (ohne Beachtung der Schreibweise), ergibt
`SPEC-013`. Beim RPC per Prozedurname bleibt ein Parameter ohne Namen `SPEC-013`, weil der Name im Aufruftext steht.
Belegt ist die Form der Nachricht an zwei aufgezeichneten Nutzlasten (ein Treiber, eine Version); für andere Treiber
und Versionen gilt die Festlegung nicht als gemessen.

Handle-Verwaltung (Festlegungen, nicht gemessen; Tokenfolge, Ordinal und Name des Handle-RETURNVALUE sind
Handvektoren, die Zahl 1000 ist eine Setzung, Voreinstellung, und nicht konfigurierbar):

- Eine Sitzung hält höchstens 1000 Handles gleichzeitig. Das `sp_prepexec`, das das 1001. Handle vergäbe,
  ergibt `SPEC-058` (ERROR und DONE mit Fehlerstatus), ohne dass das Backend gerufen wird; die Verbindung
  bleibt offen, die vorhandenen Handles bleiben gültig.
- `sp_prepexec` vergibt ein neues Handle: ein Zähler je Sitzung ab 1, aufsteigend, ohne Wiederverwendung außer nach `RESETCONNECTION` (dort beginnt er bei 1 neu). Der
  Wert im OUTPUT-Parameter der Nachricht (−1 beim Erstaufruf, ein altes Handle bei einem erneuten Prepare)
  bleibt unberücksichtigt; ein altes Handle bleibt bis zum Sitzungsende oder bis zu `RESETCONNECTION` gültig. Der Server merkt den
  SQL-Text (nicht die Deklarationsliste; die Größe des Textes ist nicht begrenzt), ruft das Backend mit dem
  Text und den Wertparametern der Nachricht und beantwortet nach der Form RPC. Das Handle steht als
  RETURNVALUE mit dem Namen `@handle` (`INTN` 4, nullbar) vor dem ersten Ausgabewert des Backends, sonst am
  Ende des Stroms, und hat damit das Ordinal 0; ein Ausgabewert des Backends zählt ab 1. Endet die Antwort
  mit einem Fehler, bevor das Handle im Strom steht (Fehler vor dem ersten Ausgabewert), entsteht kein Handle
  und der Wert wird nicht wieder vergeben; steht das Handle schon im Strom (Fehler nach einem Ausgabewert),
  bleibt es gültig.
- `sp_execute` ruft das Backend mit dem gemerkten Text und den Wertparametern der Nachricht und beantwortet
  nach der Form RPC ohne Handle-RETURNVALUE. Ein Handle, das die Sitzung nicht hält (nie vergeben, −1, 0), ergibt
  `SPEC-057`, ohne das Backend zu rufen; die Verbindung bleibt offen. Ein ungültiger Parameter wird vor der
  Suche des Handles abgelehnt (`SPEC-013`).
- `sp_unprepare` und `sp_prepare` sind nicht im Katalog (`SPEC-013`) und geben kein Handle frei.
- Die Handles enden mit der Sitzung; zwei Sitzungen sehen die Handles der jeweils anderen nicht. Der Zugriff
  auf die Tabelle einer Sitzung ist gegen gleichzeitigen Zugriff geschützt.
- `RESETCONNECTION` (`SPEC-001`) leert die Tabelle: jedes Handle aus der Zeit davor ist unbekannt (`SPEC-057`), der
  Zähler beginnt wieder bei 1, und die Grenze von 1000 gilt neu. Ein Handle, das ein Client aus der Zeit davor hält,
  trifft nach einem neuen `sp_prepexec` mit gleichem Wert den neuen Text. Der Zähler ab 1 nach dem Zurücksetzen ist
  eine Setzung, nicht gemessen; ob der Referenzclient seine Handles beim Zurücksetzen selbst verwirft, ist nicht
  gemessen.


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
sind ein Fall von `SPEC-048`; das gilt für jedes `Throwable` aus Aufruf und Strom des Backends außer der
Abbruchausnahme der Coroutine, also auch für ein `Error` (gebauter Stand des Sequenzierers). Ein
`OutOfMemoryError` beim Erzeugen oder Senden der Antwort einer Anfrage (Backend, Encoder, Senke) wird
gefangen und ist ebenfalls ein Fall von `SPEC-048` (Nutzerentscheidung vom 2026-10-31): ERROR und DONE mit
Fehlerstatus, solange der Strom synchron ist, danach schließt die Verbindung; sonst schließt sie ohne ERROR
und DONE. Die Zusage der Negative aus `TDS-FA-06` gilt hierfür als erfüllt (Abnahme-Entscheidung des Nutzers vom 2026-10-31, am 2026-11-06 bestätigt: „deine empfehlungen“; nicht gemessen). Aus Encoder und Senke werden andere `Error` und andere Ausnahmen nicht gefangen, ebenso nicht die
Abbruchausnahme der Coroutine (Setzung, Voreinstellung 2026-10-30, nicht gemessen). Ein Fehler beendet den
Lauf; weitere Ereignisse werden nicht gelesen.
Return Status und Ausgabeparameter werden in der Antwort auf einen Batch mit einem Fehler der Typabbildung
(`SPEC-014`) abgelehnt (in der Antwort auf einen RPC werden sie abgebildet, siehe „RPC im Backend-Vertrag“); mit der Zuordnung in §4 ist das `TDS_UNSUPPORTED_TYPE` (Class 20), und die
Verbindung schließt nach der Antwort, ebenso bei jedem anderen Typfehler (die Einzelzuordnung des
Codes ist offen). Die Befehlskennung (`curCmd`) der DONE-Tokens ist 0; woher sie kommt, ist
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
Die Skizze zeigt die Form der Typen, wie sie im Package `simulation` implementiert und durch Tests
festgelegt sind; die Überführung der Werte, das Lesen der Zeilenquelle und die Weitergabe von Einfügen,
Ändern und Löschen an die Callbacks sind gebaut. Festgelegt ist die Bedeutung:

- `next()` liefert die Werte einer Zeile in der Reihenfolge von `SimQuery.columns`, `null` am Ende.
  Jeder Aufruf darf suspendieren; die Zeilenquelle liest nie mehr voraus, als der Aufrufer abnimmt.
  `next()` blockiert den Thread des Aufrufers nicht (Pflicht nach `SPEC-022`, Nutzerentscheidung vom
  2026-10-31); die Werte einer Zeile tragen die Klassen aus `SPEC-002`.
- `close()` wird genau einmal gerufen, nach dem Ende, einem Fehler oder einem Abbruch.
- `FilterTerm.In` bildet `=` (ein Wert) und `IN (…)` ab. Alle Terme eines `RowFilter` gelten
  gemeinsam (UND). Dieselbe Spalte darf in mehreren Termen stehen; ein Widerspruch trifft keine
  Zeile. `NULL` in einem Vergleich oder einer Werteliste trifft nie (dreiwertige Logik,
  `ANSI_NULLS ON`, vorläufig und an der Referenzinstanz zu messen, `SPEC-045`); `IsNull` ist der
  einzige Weg zu NULL-Zeilen. `IS [NOT] NULL` ist als erkannte Bedingung durch das Lastenheft
  (Version 0.5.0, `TDS-FA-11`) gedeckt.
- `onUpdate` und `onDelete` bekommen `where = null` für eine Anweisung ohne `WHERE`, also für alle
  Zeilen. Die Zahl, die ein Callback liefert, ist die Zahl der betroffenen Zeilen.
- **Weitergabe von Einfügen, Ändern und Löschen** (`TDS-FA-11.a` Schritt 6; Setzung, nicht am Server gemessen):
  Der Callback bekommt die Werte der Anweisung, nach Schritt 4 in den Spaltentyp überführt, unter den Namen der
  Beschreibung und in der Reihenfolge des Textes; eine Spalte, die der Text nicht nennt, fehlt in der Abbildung
  (kein Standardwert). Zuerst werden die Werte geprüft, dann der Callback gewählt: ein ungültiger Wert endet mit
  `TDS_SIM_INVALID_VALUE` auch an einer Tabelle ohne den Callback, und dann wird kein Callback gerufen. Jede Anweisung
  ruft ihren Callback genau einmal, beim Sammeln der Ausführung, solange ein einziger Sammler sie sammelt (ein zweiter
  Sammler derselben Ausführung ruft ihn ein zweites Mal); schließt oder bricht jemand die Ausführung vorher ab,
  bleibt er ungerufen. Ein Fehler des Callbacks und eine negative Zahl sind `TDS_BACKEND_ERROR` mit fester Meldung, die
  den Text der Ausnahme nie nennt. Das Backend wiederholt einen Callback nicht.
- `SimQuery.filter` ist ein Hinweis zum Vorfiltern (Schritt 5 in `TDS-FA-11.a`), kein Ersatz für die
  Prüfung durch das Backend. Das Backend übergibt alle Spalten der Tabelle in der Reihenfolge der Beschreibung
  als `SimQuery.columns` und nennt im Filter die Spaltennamen der Beschreibung mit den in den Spaltentyp
  überführten Werten (NULL in einer Liste bleibt erhalten); ohne `WHERE` ist der Filter `null` (Setzung,
  nicht am Server gemessen).
- `primaryKey` nennt eine oder mehrere Spalten der Tabelle; sie müssen vorhanden und nicht nullbar
  sein. Der Schlüssel ist nur Teil der Beschreibung: er wirkt weder auf das Resultset noch auf
  Callbacks, das Backend speichert nichts und prüft keine Eindeutigkeit. Als Metadatum zum Client wird
  er nicht gesendet (Setzung, nicht am Server gemessen).
- **Validierung** (gilt für die Kotlin-Schnittstelle und die Datei): Tabellen- und Spaltennamen sind
  ohne Beachtung der Groß-/Kleinschreibung eindeutig; jede Tabelle hat mindestens eine Spalte und
  einen Schlüssel; eine Dezimalspalte hat höchstens Precision 38; der Katalog kopiert die übergebene
  Tabellenliste, eine Tabelle behält ihre Listen unkopiert und der Aufrufer ändert sie nach der Übergabe
  nicht (Setzung, nicht am Server gemessen). Der Vergleich der Namen ist der von `SPEC-036`.
- **Überführung eines Werts in den Spaltentyp** (`TDS-FA-11.a` Schritt 4; Literal und Parameter;
  Setzung, nicht am Server gemessen): streng und verlustfrei. Gleiche Typfamilie; Datum, Zeit und
  Zeitpunkt nur im ISO-Format (Zeitpunkt nur mit `T`); keine Rundung und keine Überführung zwischen
  Zahl und Text; ein Parameter wird nur verlustfrei erweitert (Int32 zu Int64 zu Decimal). Überlauf
  (Int32, Int64, Stellen der Dezimalzahl) ist immer `TDS_SIM_INVALID_VALUE`. Dezimalwerte werden mit
  `compareTo` verglichen (1.0 gleich 1.00), Textwerte ohne Beachtung der Groß-/Kleinschreibung
  (Setzung, wie die Bezeichner). Texte begrenzt das Backend selbst nicht; ein Text von mehr als 4000
  UTF-16-Einheiten, den eine Zeilenquelle liefert, weist die Typabbildung mit `TDS_UNSUPPORTED_TYPE` ab
  (Limit: `SPEC-003`; Code: `SPEC-014` und Typabbildung; Setzung, nicht gemessen).
  Weitere Festlegungen der Überführung (Setzung, Voreinstellung, nicht gemessen): eine Zahl gilt, wenn sie
  ohne Rest in den Zielwert passt (`1.0` und `100` in einer Ganzzahlspalte, `3.500` in `Decimal(10,2)`); die
  Zahlen `0` und `1` sind in einer Bool-Spalte falsch und wahr, jede andere Zahl ist ungültig; ein Text geht nur
  in eine Textspalte oder, im ISO-Format, in eine Zeitspalte; ein Dezimalparameter wird wie ein Literal gegen
  Precision und Skala der Spalte gehalten; NULL als Parameter gilt für jeden Spaltentyp; ein Parameter, den die
  Anfrage nicht trägt, ist `TDS_SIM_INVALID_VALUE`; die Richtung eines Parameters bleibt unbeachtet. „ISO-Format“
  heißt der Formatraum der ISO-Parser der Laufzeit: bei der Zeit sind Sekunden optional und Bruchteile bis zu
  Nanosekunden gelten, beim Datum gilt auch ein Jahr mit Vorzeichen und mehr als vier Stellen, beim Zeitpunkt
  ist das `T` Pflicht; Zeitzonen gelten nicht.
  Alle Werte der Bedingung werden vor dem Öffnen der Zeilenquelle überführt.
- **Fehler der Zeilenquelle** (`TDS-FA-11.a` Schritt 7; Setzung, nicht am Server gemessen): ein Fehler beim
  Öffnen oder Lesen der Zeilenquelle ist `TDS_BACKEND_ERROR` mit fester Meldung, die den Text der Ausnahme nie
  nennt; eine Zeile mit anderer Zahl von Werten als Spalten zählt als Fehler der Zeilenquelle; ein Fehler von
  `close()` bleibt ohne Folge. Ein `Error` der Laufzeit (etwa `OutOfMemoryError`) und die Abbruchausnahme der
  Coroutine fängt das Backend nicht. Cancel und `close` der Ausführung beenden das Lesen vor der nächsten
  Zeile; ein `next()`, das selbst hängt, endet nur mit dem Abbruch des Sammlers.
- **Namen im Metadatum des Ergebnisses** (Setzung, nicht am Server gemessen): Es sind die Namen der
  Beschreibung, nicht die Schreibweise im Text (`SELECT AGE` liefert `Age`).
- **Vergleich in der Bedingung** (Setzung, nicht am Server gemessen): Eine Zelle anderer JVM-Klasse als der
  Wert ist ungleich (`1` gegen `1L`), ohne Fehler.

**Beschreibung als Datei.** Zusätzlich zur Kotlin-Schnittstelle kann der Testserver Tabellen aus einer Datei
laden; die Bibliothek kennt weder die Datei noch ihr Format und nimmt nur `SimTable`-Werte (Verdrahtung unten).
Das Format ist das neutrale Schema-Modell von d-migrate in YAML, `schema_format` `1.0`; maßgeblich
ist die formale Beschreibung
[spec/schema.json von d-migrate](https://github.com/pt9912/d-migrate/blob/a33984910bc3c760ad2f7c9733f5da0f12e1abf7/spec/schema.json)
(geprüft gegen den Stand dieses Commits, 2026-10-30). Dieses Dokument kopiert das Format nicht; es legt
fest, was der Leser des Testservers daraus liest. Ändert sich das Format dort, ist der Belegstand zu erneuern.

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
  kein Anker an einer Abbildung oder Liste, kein Alias, kein Tag und kein Merge-Schlüssel (ein Anker an
  einem Skalar ohne Alias lädt und bleibt ohne Wirkung, gemessen am Adapter), nur Skalar-Schlüssel; höchstens 1 MiB, vor dem
  Parsen geprüft.
- **Eine Datei beschreibt nur das Schema:** eine aus ihr geladene Tabelle hat eine leere Zeilenquelle
  und keine Callbacks. Auswahl liefert Metadaten und ein leeres Ergebnis; Einfügen, Ändern und Löschen
  enden mit `TDS_UNSUPPORTED_SQL`. Zeilenquelle und Callbacks bleiben Code des Integrators (Setzung, nicht am Server gemessen).
- **Ladefehler:** Ein Fehler (unbekanntes oder doppeltes Feld, Typ ohne Entsprechung, doppelter Name,
  Schlüssel auf unbekannte Spalte, zu große oder unlesbare Datei) bricht den Start ab, bevor ein Port
  gebunden wird, ohne Teilladen. Die Meldung nennt den Pfad im Dokument, Zeile und Spalte und nie den
  Inhalt. Kein TDS-Fehlercode: beim Laden ist kein Client verbunden (Setzung, nicht am Server gemessen).
- **Weitere Festlegungen des Lesers** (Setzungen, nicht am Server und nicht an d-migrate gemessen):
  `tables` als leere Abbildung ist erlaubt; Namen von Tabellen und Spalten sind nicht leer; `decimal`
  verlangt `precision` und `scale`, `scale` höchstens `precision`; `ordinal` kommt bei keinen zwei Spalten
  gleich vor; Ganzzahlen müssen in 32 Bit passen; `schema_format` und die Pflichttexte werden als Text
  gelesen, mit oder ohne Anführungszeichen; ein Wahrheitswert oder eine Ganzzahl in Anführungszeichen wird
  abgewiesen. Der Pfad einer Ladefehler-Meldung besteht aus Schlüsseln des Dokuments, ein Abschnitt höchstens
  64 Codepunkte lang, Steuer-, Format-, Zeilen- und Absatztrennzeichen (Unicode-Kategorien Cc, Cf, Zl, Zp)
  und einzelne Ersatzzeichen als `?`; der Typname steht in der Meldung nur, wenn er einer der 21 Typen des
  Schemas ist.
- **Verdrahtung:** Die Kotlin-Schnittstelle übernimmt die Tabellen des Integrators mit Zeilenquellen und Callbacks
  über den Einstieg `TdsServer.start` mit `BackendSource.Simulation` (`SPEC-059`). Ein Name, der zweimal vorkommt
  (ohne Beachtung der Schreibweise), bricht den Start ab. Der Katalog hält eigene Kopien der Listen `columns` und
  `primaryKey` der Tabellen des Integrators: ändert er seine Listen nach der Übergabe, bleibt der Katalog
  unverändert. Ohne Tabellen läuft der Server ohne Simulations-Backend, beantwortet den Login und lässt eine
  Anfrage unbeantwortet.
- **Weg `Custom`:** Wer ein Backend mit Katalog selbst baut (`SimulationBackend(SimCatalog(tables))`) und als
  `BackendSource.Custom` übergibt (etwa um es mit einem eigenen `QueryBackend` zu umhüllen), ist ein zweiter Weg
  neben `Simulation`, mit demselben Verhalten des Backends. Dieser Weg durchläuft die Namensprüfung des Einstiegs und
  das Kopieren der inneren Listen nicht: `SimCatalog` meldet einen doppelten Namen (ohne Beachtung der Schreibweise)
  selbst mit `IllegalArgumentException` und kopiert nur die Liste der Tabellen, nicht deren `columns` und
  `primaryKey` (aus dem Quelltext gelesen, Setzung). Die Namen `SimulationBackend` und `SimCatalog` stehen unter der
  Zusage von `SPEC-059`.
- **Datei und Umgebungsvariable gehören dem Testserver:** Die Umgebungsvariable `SIMULATION_DESCRIPTION_FILE` nennt
  den Pfad der Datei (Name gesetzt, nicht gemessen). Der Wert ist nicht leer, die Datei lesbar und höchstens 1 MiB
  groß (gelesen wird höchstens ein Byte mehr, ein Byte mehr als 1 MiB ist ein Startfehler); sonst bricht der
  Testserver den Start ab, bevor ein Port gebunden wird, und die Diagnose nennt Schlüssel und Art, nie den Pfad. Der
  Testserver baut aus der Datei `SimTable`-Werte und übergibt sie wie ein Einbettender; die Bibliothek kennt weder
  Datei noch Pfad noch Größengrenze noch die YAML-Bibliothek. Der Leser der Beschreibung bekommt einen Baum aus
  Abbildungen, Listen und Skalaren mit Zeile und Spalte und kennt weder die Datei noch die YAML-Bibliothek (Setzung,
  nicht am Server gemessen). Gemessen am Adapter mit Jackson 2.21.7: ein Anker an einem Skalar, auf den kein Alias
  verweist, bleibt für die Strom-API unsichtbar und ohne Wirkung; ein Anker an einer Abbildung oder Liste, ein Alias
  und ein Tag sind ein Ladefehler; ein leerer Wert ohne Anführungszeichen ist ein leerer Text.

### SPEC-036 — Erkannte Anweisungsformen des Simulations-Backends (vorläufig)

| Form | Muster | Antwort |
|---|---|---|
| S1 | `SELECT <Spaltenliste \| *> FROM [<Schema>.]<Tabelle> [WHERE <Term> {AND <Term>}]` | Resultset |
| W1 | `INSERT INTO [<Schema>.]<Tabelle> (<Spalten>) VALUES (<Werte>)` | Änderungszähler |
| W2 | `UPDATE [<Schema>.]<Tabelle> SET <Spalte> = <Wert> {, <Spalte> = <Wert>} [WHERE <Term> {AND <Term>}]` | Änderungszähler |
| W3 | `DELETE FROM [<Schema>.]<Tabelle> [WHERE <Term> {AND <Term>}]` | Änderungszähler |
| C | `SELECT 1`, oder eine oder mehrere `SET`-Anweisungen aus der Liste unten, durch `;` getrennt | `SELECT 1`: Resultset mit einer Spalte ohne Namen (`Int32`, nicht NULL) und der Zeile 1; `SET`: Änderungszähler 0 |

- **Term:** `<Spalte> = <Wert>`, `<Spalte> IN (<Wert> {, <Wert>})` oder `<Spalte> IS [NOT] NULL`. Nur in S1 zusätzlich
  der Konstantenterm `<Ganzzahl> = <Ganzzahl>` (Ganzzahl: Ziffern mit optionalem führendem `-`): er gilt für jede Zeile,
  wenn beide Zahlen gleich sind, sonst für keine; `WHERE 1 = 0` liefert also die Spalten der Auswahl und keine Zeile,
  ohne dass die Zeilenquelle geöffnet wird, `WHERE 1 = 1` jede Zeile. Der Konstantenterm ist eine Setzung: gemessen ist
  nur der Text `1 = 0` (d-migrate 1.12.0, mssql-jdbc 13.4.0, 2026-11-05, `SELECT * FROM [kunden] WHERE 1 = 0` als
  Probe der Spaltenmetadaten). Das
  Lastenheft nimmt ihn ausdrücklich auf (`TDS-FA-11`); die Liste „Nicht erkannt“ gilt für ihn nicht.
  In `UPDATE` und `DELETE` ist er nicht erkannt: ein Filter ohne Spaltenterm wäre dort ein Filter ohne Vorfilter, den
  ein Callback als „alle Zeilen“ liest.
- **Wert:** Zahl, `'…'`, `N'…'`, `NULL` oder `@<Parametername>`.
- **Bezeichner:** unquotiert, `[…]` oder `"…"`; Tabellen- und Spaltennamen werden ohne Beachtung der
  Groß-/Kleinschreibung verglichen (vorläufig, wie die übliche Server-Collation). Festlegung ohne Messung:
  zwei Namen sind gleich, wenn ihre Codepunkte nach Großschreibung und anschließender Kleinschreibung
  übereinstimmen (die Regel von `String.equalsIgnoreCase`); danach sind `i` und `İ` sowie `s` und `ſ`
  gleich, und zwei Spalten, die sich nur so unterscheiden, sind doppelt. `"…"` als
  Bezeichner setzt `QUOTED_IDENTIFIER ON` voraus; beides ist an der Referenzinstanz zu messen
  (`SPEC-045`).
- **Nicht erkannt** (`TDS_UNSUPPORTED_SQL`; Verbund, Aggregat, Ausdruck, Unterabfrage und Sortierung
  schließt das Lastenheft wörtlich aus; für die übrigen Fälle gilt die Setzung, dass die Liste der Formen
  abschließend ist, eine Lesart des Lastenhefts, vom Nutzer nicht bestätigt):
  `JOIN` und weitere Relationen, Unterabfragen, `WITH`,
  Aggregate und `GROUP BY`, `DISTINCT`, `TOP`, `ORDER BY`, `OFFSET`/`FETCH`, Aliase für Tabellen und
  Spalten, Ausdrücke und Funktionen in Listen und Bedingungen, `OR`, `NOT`, `<`, `>`, `LIKE`, `BETWEEN`,
  `OUTPUT`-Klauseln, mehrere Anweisungen in einem Text und alles, was keine Form der Tabelle ist.
- **Form C** (Setzung nach der Aufzeichnung von d-migrate 1.12.0 mit mssql-jdbc 13.4.0.jre11, 2026-11-05, gegen den
  Testserver; für den Referenzclient ist keine Anweisung der Form gemessen): `SELECT 1` ist der Verbindungstest des
  Pools, `SET LOCK_TIMEOUT 30000` folgt jedem Verbindungsaufbau, und `data import` sendet in einem Text sieben
  `SET`-Anweisungen (`ANSI_NULLS ON`, `ANSI_PADDING ON`, `ANSI_WARNINGS ON`, `ARITHABORT ON`,
  `CONCAT_NULL_YIELDS_NULL ON`, `NUMERIC_ROUNDABORT OFF`, `QUOTED_IDENTIFIER ON`). Erkannt ist `SELECT 1` genau (kein anderer Wert, kein `FROM`, keine Spaltenliste) und jede
  Folge von `SET`-Anweisungen aus: `SET <Option> ON|OFF` mit `ANSI_NULLS`, `ANSI_PADDING`, `ANSI_WARNINGS`,
  `ARITHABORT`, `CONCAT_NULL_YIELDS_NULL`, `NUMERIC_ROUNDABORT`, `QUOTED_IDENTIFIER`, `IMPLICIT_TRANSACTIONS`, und
  `SET LOCK_TIMEOUT <Ganzzahl>`; die Liste ist eine Setzung (die ersten sieben und `LOCK_TIMEOUT` gemessen,
  `IMPLICIT_TRANSACTIONS` aus der Beobachtung von `set implicit_transactions on|off` beim Schreiben von Strukturen,
  hier nicht erneut gemessen). Die Anweisungen sind durch `;` getrennt (Leerraum und Zeilenumbruch dazwischen,
  ein abschließendes `;`); ein Text, der eine Anweisung außerhalb der Liste enthält, ist als Ganzes nicht erkannt
  (alles oder nichts). Die Antwort auf `SET` ist der Änderungszähler 0, nicht das Ende eines Ergebnisses ohne Anfang
  (`ResultEnd` ohne `ResultStart` beantwortet die Sitzung mit Fehler 50007, Class 20, und schließt die Verbindung;
  gemessen mit mssql-jdbc 13.4.0 am 2026-11-05). Die Wirkung der Optionen auf spätere Anweisungen ist nicht modelliert: `SET` ändert keinen Zustand der
  Sitzung. Form C nennt keine Tabelle; das Backend ruft weder Zeilenquelle noch Callback.
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
- **Weitere Festlegungen von Scanner und Zuordnung** (Setzungen, nicht am Server gemessen): `N'…'` gilt auch
  mit kleinem `n`; ein Zeilenkommentar endet an CR oder LF; Wörter bestehen aus Unicode-Buchstaben
  (auch außerhalb der Grundebene; der Text wird je Codepunkt gelesen), ASCII-Ziffern, `_`, `#` und `$`; die fünfzehn Wörter werden nur in ASCII-Schreibweise und ohne Beachtung
  der Groß-/Kleinschreibung erkannt, andere Wörter (auch `TOP` und `ORDER`) sind gewöhnliche Namen; ein
  Punkt in einer Zahl braucht Ziffern auf beiden Seiten; ein Parameter ist `@` und mindestens ein Wortzeichen;
  `INSERT` hat genau eine Wertezeile mit ebenso vielen Werten wie Spalten; eine Spalte steht in der
  Spaltenliste von `INSERT` und in `SET` höchstens einmal, sonst ist der Text nicht erkannt. Die Zuordnung zu
  einer Form kommt vor der Prüfung der Objekte: eine nicht erkannte Anweisung endet mit `TDS_UNSUPPORTED_SQL`,
  auch wenn ihre Tabelle unbekannt wäre. Die Meldung eines abgewiesenen Texts nennt nie Teile des Textes.

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
eigenen Diagnose, die Adresse und Port nennt: im `reason` von `StartOutcome.Aborted` und, mit übergebener
Senke, im Ereignis des Bindefehlers (Abschnitt 4). Nach jedem fehlgeschlagenen Start laufen keine Threads
und kein Listener des Servers weiter; ein neuer Versuch legt einen neuen Server an. Die Probe prüft
weder, ob ein Client dem Zertifikat vertraut, noch die TDS-Kapselung des Handschlags (`SPEC-028`).
Schreibt der TLS-1.3-Server nach dem Handschlag ein NewSessionTicket, besteht die Probe: ihr Urteil ist
das Ergebnis des Handschlags auf der Seite des Servers. Die Gegenstelle liest nach ihrem Handschlag weiter
und verwirft, was der Server noch schreibt, bis der Server seinen Kanal schließt; das Urteil hängt weder
von der Größe der Nachschrift noch von einer Wartezeit ab (Setzung). Nur eine Probe, die nicht endet,
bricht nach einer Frist als `PAIR` ab. Der Start verlangt keine JVM-Option.

### SPEC-044 — Geordnetes Beenden

Beginn ist die Shutdown-Anforderung mit der Frist `SHUTDOWN_GRACE` (`SPEC-047`; 0 = sofort).

1. Der Listener schließt als Erstes; neue Verbindungen werden nicht mehr angenommen, eine im selben
   Moment angenommene endet sofort.
2. Eine Verbindung ohne laufende Anfrage und ohne unvollständig empfangene Nachricht endet sofort.
3. Eine laufende Anfrage darf bis zu ihrem Abschluss laufen, höchstens bis zum Ablauf der Frist; ihr
   Abschluss wird regulär gesendet, nie ein halber Tokenstrom (`SPEC-031`). Danach beginnt keine
   weitere Anfrage derselben Verbindung: eine nach dem Beginn eingetroffene Anfrage wird mit
   `TDS_SHUTDOWN` beantwortet, danach endet die Verbindung. Von den übrigen Anweisungen eines
   Batches läuft nach dem Beginn nichts mehr. Herkunft des Schließens (Setzung des Nutzers vom 2026-11-01,
   nicht am echten Server gemessen): ERROR und DONE werden vollständig gesendet, erst danach wird geschlossen;
   das Ende der Antwort führt in einem Übergang nach `CLOSED`. Dieselbe Herkunft hat das Ende der Verbindung
   nach `DONE` mit ATTENTION-Status im Beenden.
   Nicht gebaut: „Von den übrigen Anweisungen eines Batches läuft nach dem Beginn nichts mehr“: der Server reicht den
   Text unverändert an das Backend und kennt keine Anweisungsgrenzen darin; ein Backend, das mehrere Anweisungen
   ausführt, erfährt vom Beginn nur über das Cancel nach der Frist.
4. Nach Ablauf der Frist wird jede noch laufende Anfrage wie bei ATTENTION abgebrochen (`SPEC-029`,
   Cancel an das Backend); gelingt das nicht innerhalb `CANCEL_TIMEOUT` (`SPEC-033`), wird die
   Verbindung getrennt.
5. Die Shutdown-Anforderung kehrt zurück, wenn alle Verbindungen beendet sind, spätestens
   `SHUTDOWN_GRACE` plus `CANCEL_TIMEOUT` nach dem Beginn. Eine zweite ist wirkungslos.

Eine Verbindung, die mitten in einer Nachricht kein EOM mehr sendet, bleibt bis zum Ablauf der Frist
offen und wird dann getrennt. Eine Verbindung ohne laufende Anfrage wird ohne Meldung getrennt,
solange das Clientprofil (`SPEC-004`) keine unaufgeforderte Server-Meldung außerhalb einer Antwort
belegt: TDS-Antworten folgen auf Anfragen.

Offene Frage an die Spezifikation (benannt am 2026-11-06; die Zusage von Nr. 3 bleibt unverändert):
Nr. 2 und Nr. 3 treffen eine Verbindung ohne laufende Anfrage gegensätzlich, wenn sie nach dem Beginn eine Anfrage
sendet. Gebaut ist Nr. 2 vor Nr. 3 gelesen: eine ruhende Verbindung endet beim Beginn sofort, `TDS_SHUTDOWN` erreicht
nur eine Verbindung mit laufender Anfrage oder unvollständiger Nachricht. Ob eine ruhende Verbindung für eine danach
eintreffende Anfrage offen bleibt, um `TDS_SHUTDOWN` zu senden, ist nicht entschieden. Ebenso nicht entschieden:
wann eine Verbindung mitten im Handshake endet (gebaut: wie eine ruhende, sofort).

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
SQL-Server-Image setzt `ACCEPT_EULA=Y` voraus; die Zustimmung des Repo-Inhabers gilt als Setzung (rechtlich ungeprüft), der Lauf setzt die Variable ausdrücklich. Gemessen ist nur eine Instanz der Version 2022: `Encrypt=Strict` (TDS 8.0) scheitert, und der Server-Log nennt TLS 1.0 bis 1.2; der Abgleich beider Pfade entfällt dort.

## 3. Defaults und Konstanten

| ID | Name | Wert | Begründung |
|---|---|---|---|
| `SPEC-005` | `LISTEN_PORT` (Eingang des Testservers) | 1433 (Voreinstellung von `ListenSettings`, konfigurierbar) | TDS-Standardport; Wertebereich 0 bis 65535, 0 wählt einen freien Port (Festlegung, nicht gemessen); dazu `LISTEN_HOST`, nicht leer, Voreinstellung `0.0.0.0` (Festlegung, nicht gemessen). Wertebereich und Voreinstellungen sind Aussagen über `ServerConfig` und ihre Prüfung (`ConfigValidation.check`); `LISTEN_PORT` und `LISTEN_HOST` sind die Umgebungsvariablen, aus denen der Testserver diese Werte liest, die Bibliothek liest keine Umgebung |
| `SPEC-006` | `MAX_TDS_PACKET_BYTES` | 65535, zusätzlich protokollkonforme Headerprüfung | Obergrenze des 16-Bit-Längenfelds |
| `SPEC-007` | `MAX_MESSAGE_BYTES` | 16 MiB (vorläufig) | Speicherbegrenzung, gilt während des Zusammensetzens (`TDS-FA-01.a`); mit Clientprofil abgleichen |
| `SPEC-008` | `LOGIN_TIMEOUT` (Eingang des Testservers) | 15 s (vorläufig; konfigurierbar in ganzen Sekunden von 1 bis 3600, Festlegung, nicht gemessen; Umgebungsvariable des Testservers, die Bibliothek liest keine Umgebung) | Hängende Logins begrenzen; die Frist gilt ab Verbindungsaufbau über PRELOGIN, TLS-Handschlag und LOGIN7, danach schließt der Server ohne Antwort; für eine angemeldete Verbindung gilt sie nicht; die Wartezeit der Drosselung wiederholter Anmeldeversuche (`SPEC-053`) zählt zur Frist |
| `SPEC-046` | `MAX_PRELOGIN_MESSAGE_BYTES` (Eingang des Testservers) | 65536 (vorläufig; konfigurierbar von 512 bis 65536, die obere Grenze ist die der PRELOGIN-Verhandlung, Festlegung, nicht gemessen; Umgebungsvariable des Testservers, die Bibliothek liest keine Umgebung) | Vor abgeschlossenem Login kein großer Puffer: PRELOGIN und LOGIN7 sind klein, eine größere Nachricht wird abgewiesen |
| `SPEC-047` | `SHUTDOWN_GRACE` | 10 s (vorläufig; 0 = sofort; konfigurierbar in ganzen Sekunden von 0 bis 3600, Festlegung, vom Nutzer bestätigt am 2026-11-06, nicht gemessen; Eingang der Bibliothek ist `ServerConfig.requests` (`RequestSettings.shutdownGraceSeconds`), Umgebungsvariable des Testservers). `TdsServer.stop` beginnt das geordnete Beenden mit dieser Frist | Frist für laufende Anfragen beim geordneten Beenden (`SPEC-044`); die Voreinstellung 10 s plus `CANCEL_TIMEOUT` 5 s übersteigt die Voreinstellung von `docker stop` (10 s bis SIGKILL; Voreinstellung von Docker, hier nicht gemessen) |
| `SPEC-009` | `QUERY_TIMEOUT` | 30 s (vorläufig; konfigurierbar in ganzen Sekunden von 1 bis 3600, Festlegung, nicht gemessen; Eingang der Bibliothek ist `ServerConfig.requests`, Umgebungsvariable des Testservers) | Backend-Timeout; Quelle der Frist einer Anfrage (`deadlineEpochMillis` = Beginn plus `QUERY_TIMEOUT`), die der Protokollkern durchsetzt (`SPEC-022`), solange kein Schreibvorgang der Antwort am Rückstau hängt (er hält die Sitzungssperre, die Frist wirkt erst nach seiner Rückkehr); Ablauf endet in `TDS_TIMEOUT` (`SPEC-016`) |
| `SPEC-010` | `ENCRYPTION_POLICY` | Standard `REQUIRED`; Abschwächung nur durch ausdrückliche, im Log sichtbare (wenn eine Senke übergeben ist) Konfiguration, je Clientprofil; genau `REQUIRED` oder `TEST_UNENCRYPTED`; `REQUIRED` verlangt Schlüsselspeicher (PKCS#12) und Passwort in `ServerConfig`, `TEST_UNENCRYPTED` lässt beide nicht zu (Festlegung, nicht gemessen); `ENCRYPTION_POLICY`, `TLS_KEYSTORE_FILE` und `TLS_KEYSTORE_PASSWORD_FILE` sind die Umgebungsvariablen, aus denen der Testserver Richtlinie, Speicherdatei und Passwortdatei liest, die Bibliothek liest keine Umgebung und keine Datei | Keine unbemerkte TLS-Herabstufung |
| `SPEC-033` | `CANCEL_TIMEOUT` | 5 s (vorläufig; konfigurierbar in ganzen Sekunden von 1 bis 3600, Festlegung, nicht gemessen; Umgebungsvariable des Testservers) | Frist für nicht abbrechbare Backendoperationen, danach Verbindung schließen; sie läuft ab dem Gewinn des Abbruchs (ATTENTION oder Frist der Anfrage), nicht vorher, und damit nicht, solange ein Schreibvorgang am Rückstau die Sitzungssperre hält |
| `SPEC-034` | `MAX_CONNECTIONS` | 1000 (vorläufig; konfigurierbar als ganze Zahl von 1 bis 100000, Festlegung, vom Nutzer bestätigt am 2026-11-06, nicht gemessen; Eingang der Bibliothek ist `ServerConfig.listen` (`ListenSettings.maxConnections`), Umgebungsvariable des Testservers). Der Listener nimmt höchstens so viele Verbindungen zugleich auf (gezählt im Verbindungsregister, ab der Annahme bis zum Ende des Handlers); eine weitere wird ohne Antwortbytes geschlossen, ohne dass der Prozess oder eine bestehende Verbindung leidet, und nach dem Ende einer Verbindung nimmt er wieder auf. Dass der Client einer abgewiesenen Verbindung ein Schließen ohne Antwort sieht (statt ERROR vor dem Login), ist Festlegung, vom Nutzer bestätigt am 2026-11-06, und am Referenzclient nicht gemessen | Ressourcenlimit für gleichzeitige Verbindungen |
| `SPEC-053` | Drosselung wiederholter Anmeldeversuche | je Quelladresse (die Adresse der Gegenstelle ohne Port; Kennung und Passwort gehen nicht in den Schlüssel ein): die ersten 5 Fehlversuche bleiben unverzögert, ab dem 6. wartet jede weitere Anmeldung dieser Adresse vor der Prüfung der Zugangsdaten 250 ms, verdoppelt je weiterem Versuch bis 5 s; der Zähler der Fehlversuche fällt 60 s nach dem letzten Fehlversuch der Adresse auf null und wird durch einen Erfolg nicht zurückgesetzt; gleichzeitig laufende Versuche einer Adresse zählen ab ihrem Beginn mit und werden bei Ende ohne Fehlversuch abgezogen; höchstens 10000 Adressen, bei Überlauf wird die Adresse verdrängt, deren Anlage oder letzter Fehlversuch am längsten zurückliegt, auch wenn nur ein Versuch läuft, und ihre Zähler gehen verloren; nicht konfigurierbar (alle Zahlen Setzung, nicht gemessen) | Setzung ohne Lastenheft-Anker, vom Nutzer bestätigt (das Lastenheft verlangt keine Drosselung). Raten gegen die Benutzerliste von einer Adresse bremsen, ohne Sperre und ohne Zustand je Kennung. Die Wartezeit hängt nur von der Adresse ab, ist ein Anhalten ohne Platz des Adapteraufrufs, gehört zu `LOGIN_TIMEOUT` (`SPEC-008`) und ist nicht an ihn gebunden: bei einer Frist unter der Wartezeit endet die Anmeldung ohne Antwort. Als Fehlversuch zählt die Ablehnung durch den Adapter, nicht eine Annahme, ein Adapterfehler oder ein Abbruch. Ein Kanal ohne bekannte Adresse der Gegenstelle wird nicht gedrosselt (Entscheidung des Nutzers). Ob die Drosselung ins Log schreibt und mit welcher Adressangabe, ist offen; heute schreibt sie nichts ins Log. Rund 12 Versuche je Minute (60 s / 5 s) gelten nur für eine Verbindung nach der anderen; bei gleichzeitigen Verbindungen wartet jede höchstens 5 s, und die Obergrenze gleichzeitiger Adapteraufrufe begrenzt die Rate, nicht die Staffel; ein Schwall gleichzeitiger Verbindungen ist nicht gemessen. Eine verteilte Suche über viele Adressen bleibt ungebremst; Clients hinter einer gemeinsamen Adresse teilen Zähler und Wartezeit |

Vorläufige Werte sind technische Entwurfsannahmen, keine aus dem Lastenheft abgeleiteten
Abnahmegrenzen.

## 4. Fehler-Codes und Logging-Felder

| ID | Interner Code | Bedingung | Aktion |
|---|---|---|---|
| `SPEC-011` | `TDS_PROTOCOL_ERROR` | Paket/Token ungültig, auch eine RPC-Nachricht, die der Parser abweist (Struktur- oder Kodierfehler: Format, Länge, Typkennung außerhalb der Typliste, TYPE_INFO, ALL_HEADERS; ein gültig kodierter, aber nicht unterstützter PLP-Typ gehört nicht dazu, siehe `SPEC-013`; Setzung der Nutzerentscheidung vom 2026-10-31, nicht gemessen) | Abbruch oder ERROR (Severity 20, Setzung), je nach Zustand; nach ERROR schließt der Server |
| `SPEC-012` | `TDS_LOGIN_FAILED` | Authentifizierung fehlgeschlagen | Login-Fehler, Session nicht erstellen; Meldung und State für unbekannte Kennung und falsches Passwort identisch; Nummer 18456, Class 14, einheitlicher State, feste Meldung ohne Kennung und genau ein ERROR vor DONE (am Referenzclient gemessen, am Spike-Testserver und nicht am Produkt: dieselben Antwortbytes für beide Fälle, keine Wiederholung; für das Produkt ist die Gleichheit eine Anforderung). Wiederholte Fehlversuche einer Quelladresse werden verzögert, die Antwort bleibt dieselbe (`SPEC-053`). Danach schließt der Server die Verbindung und nimmt kein weiteres LOGIN7 an (Setzung, nicht gemessen; am echten Server wurde nur die Fehlerantwort einmal beobachtet, das Verbindungsende nicht). An einem echten SQL Server wurde einmal beobachtet (vom Review bestätigt): falsches Kennwort und unbekannter Benutzer ergeben beide Nummer 18456, State 1, Class 14, die Meldung nennt den Benutzernamen; der interne State des Servers ist am Client nicht sichtbar. |
| `SPEC-013` | `TDS_UNSUPPORTED_RPC` | RPC nicht unterstützt: ProcID oder Prozedurname außerhalb des Katalogs (`SPEC-030`), darunter `sp_prepare` und `sp_unprepare`, ein Parameter außerhalb des Mindestkatalogs (`SPEC-003`), auch ein gültig kodierter PLP-Typ (`nvarchar(max)`; der Rest der Nachricht wird dann nicht gelesen) oder ein Wert außerhalb des Bereichs seines Typs; gilt nur für gültig kodierte Nachrichten, ein Struktur- oder Kodierfehler (zum Beispiel eine Typkennung außerhalb der Typliste, deren Wertlänge unbestimmt ist) ist `SPEC-011` (Trennung nach Ursache: Setzung der Nutzerentscheidung vom 2026-10-31, nicht gemessen) | ERROR und DONE mit Fehlerstatus; die Verbindung bleibt offen (Class 16, Zuordnung in diesem Abschnitt), das Backend wird nicht gerufen; das Log nennt den Ablehnungsgrund als feste Marke, nie einen Wert |
| `SPEC-014` | `TDS_UNSUPPORTED_TYPE` | Wert nicht serialisierbar | definierter Fehler, kein fehlerhaftes Resultset |
| `SPEC-015` | `TDS_BACKEND_ERROR` | Backend-Ausführung fehlgeschlagen | Fehlerabbildung in TDS |
| `SPEC-016` | `TDS_TIMEOUT` | Frist überschritten (die Frist einer Anfrage, `QUERY_TIMEOUT`, `SPEC-009`) | Cancel an das Backend, Cleanup, ERROR und DONE mit Fehlerstatus, Verbindung offen (Class 16); ist der Strom nicht mehr sicher abzuschließen, schließt die Verbindung ohne ERROR und DONE (`SPEC-031`) |
| `SPEC-037` | `TDS_UNSUPPORTED_SQL` | Anweisung oder Bedingung außerhalb von `SPEC-036`; Schreibzugriff ohne Callback | ERROR und DONE mit Fehlerstatus, Zeilenquelle nicht gelesen |
| `SPEC-038` | `TDS_SIM_UNKNOWN_OBJECT` | Tabelle oder Spalte ist nicht beschrieben | ERROR und DONE mit Fehlerstatus; vorgesehene SQL-Server-Nummern 208 (Objekt) und 207 (Spalte) |
| `SPEC-039` | `TDS_SIM_INVALID_VALUE` | Wert nicht in den Spaltentyp überführbar | ERROR und DONE mit Fehlerstatus; vorgesehene SQL-Server-Nummern 245 (Konvertierung) und 8115 (Überlauf), an der Referenzinstanz zu messen |
| `SPEC-048` | `TDS_INTERNAL_ERROR` | unerwartete Ausnahme im Server oder im Backend-Adapter, auch ein `OutOfMemoryError` beim Erzeugen oder Senden der Antwort (`SPEC-031`) | ERROR (Severity 20) und DONE mit Fehlerstatus, fester Text ohne den Text der Ausnahme, danach Verbindungsende; die Ausnahme geht nur als Typname ins Log, nie mit Text (Abschnitt 4). Gebaut: das Ereignis `RequestFailed` trägt den Code und den vollqualifizierten Typnamen der Ausnahme oder eine feste Marke, nie den Text der Ausnahme (Abschnitt 4; Tests: `TelemetryRedactionTest`, `RequestWiringTest`) |
| `SPEC-049` | `TDS_SHUTDOWN` | Anfrage nach Beginn des geordneten Beendens (`SPEC-044`) | ERROR und DONE mit Fehlerstatus, danach Verbindungsende; Herkunft des Schließens: ERROR und DONE werden vollständig gesendet, erst danach wird geschlossen (Setzung des Nutzers vom 2026-11-01, am Referenzclient wird „ERROR, DONE, dann Schließen“ gelesen, ein Herunterfahren am echten Server ist nicht gemessen); vorgesehen: die SQL-Server-Meldung für ein Herunterfahren, Nummer im Mapping nach `TDS-FA-06.a` festlegen |
| `SPEC-057` | `TDS_UNKNOWN_HANDLE` | `sp_execute` mit einem Handle, das die Sitzung nicht hält, auch nach `RESETCONNECTION` (`SPEC-030`) | ERROR und DONE mit Fehlerstatus; die Verbindung bleibt offen (Class 16), das Backend wird nicht gerufen; das Log nennt die feste Marke `unknown-handle` |
| `SPEC-058` | `TDS_PREPARE_LIMIT` | `sp_prepexec` bei erreichter Grenze von 1000 Handles der Sitzung (`SPEC-030`) | ERROR und DONE mit Fehlerstatus; die Verbindung bleibt offen (Class 16), das Backend wird nicht gerufen, die vorhandenen Handles bleiben gültig; das Log nennt die feste Marke `handle-limit` |

Die Nummern in der Aktionsspalte sind ein Vorschlag für das Mapping nach `TDS-FA-06.a`. Für
`TDS_UNSUPPORTED_SQL` gibt es keine SQL-Server-Entsprechung, denn dort ist die Anweisung gültig; die
Nummer kommt aus dem Benutzerbereich (ab 50001).

**Zuordnung des Fehler-Mappings** (`TDS-FA-06.a` Nr. 2 und 6; Träger:
`ErrorCode` im Package `protocol.error`). Kein Wert dieser Tabelle ist an einem echten SQL Server
gemessen, mit einer Ausnahme: Nummer 18456, State 1 und Class 14 von `TDS_LOGIN_FAILED` wurden an einem
echten SQL Server einmal beobachtet (`SPEC-012`); der Text dort nennt den Benutzernamen, der feste Text
dieser Tabelle nicht. Die Spalte *Herkunft* trennt: *Vorschlag* (Nummer aus der Aktionsspalte oben,
„an der Referenzinstanz zu messen“), *Setzung des Nutzers* (Gruppe und Class, 2026-10-30; `TDS_TIMEOUT` 2026-11-01),
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
| `TDS_TIMEOUT` | 50005 | 16 | offen | Gruppe und Class: Setzung des Nutzers 2026-11-01; Nummer: Festlegung |
| `TDS_UNSUPPORTED_SQL` | 50001 | 16 | offen | Class und Folge: Setzung des Nutzers; Nummer: Vorschlag (Benutzerbereich) |
| `TDS_SIM_UNKNOWN_OBJECT` | 208 | 20 | schließt | Nummer: Vorschlag (207 für Spalten nicht abgebildet); Folge: Grundregel; Class 20: Festlegung, Einzelzuordnung offen; der echte SQL Server sendet 208 mit Class 16 (Messung R05), hier schließt der Client bei Class 20 selbst |
| `TDS_SIM_INVALID_VALUE` | 245 | 16 | offen | Nummer: Vorschlag (8115 für Überlauf nicht abgebildet); Gruppe: Voreinstellung |
| `TDS_INTERNAL_ERROR` | 50007 | 20 | schließt | Class und Folge: Setzung des Nutzers; Nummer: Festlegung |
| `TDS_SHUTDOWN` | 50008 | 20 | schließt | Folge: Setzung des Nutzers (`SPEC-049`); Nummer und Class: Festlegung (die SQL-Server-Nummer für ein Herunterfahren ist nicht gemessen) |
| `TDS_UNKNOWN_HANDLE` | 50009 | 16 | offen | Gruppe: Setzung (Voreinstellung, nicht gemessen); Nummer: Festlegung |
| `TDS_PREPARE_LIMIT` | 50010 | 16 | offen | Gruppe: Setzung (Voreinstellung, nicht gemessen); Nummer: Festlegung |

Das DONE trägt für jeden Code das Statusbit `ERROR`, Befehlskennung 0 und Zeilenzahl 0; ob der
Referenzclient es so annimmt, ist nicht am Produkt gemessen. Am echten SQL Server trägt das DONE bei
Class 20 und 25 zusätzlich das Statusbit `SRVERR` und es folgen weitere ERROR-Token (Messung R10); das
Mapping sendet für jeden Code nur ERROR und DONE mit `ERROR`, diese Abweichung ist nicht am Produkt
gemessen. Ein Backend meldet als `Failure` nur `TDS_BACKEND_ERROR`, `TDS_UNSUPPORTED_SQL`,
`TDS_UNSUPPORTED_TYPE`, `TDS_SIM_UNKNOWN_OBJECT`, `TDS_SIM_INVALID_VALUE` und `TDS_TIMEOUT`
(Festlegung); jeder andere Code, auch ein anderer eigener wie `TDS_LOGIN_FAILED` oder `TDS_SHUTDOWN`,
erreicht den Client als `TDS_INTERNAL_ERROR`. Ist der Strom nicht synchron, entfällt die
Antwort und die Verbindung schließt (`SPEC-031`). Der Log-Eintrag einer unerwarteten Ausnahme trägt den
Code und den Typnamen der Ausnahme, nicht ihren Text (Zeile `SPEC-048`, Abschnitt 4). Offen: die
Einzelzuordnung der Codes unter *Grundregel*; die Unterscheidung statement- und batch-abbrechender
Fehler; die Messung aller Werte (`BEO-TDS/messung-am-echten-sql-server-fehlt`). Gebaut ist im
Anfragepfad das Ereignis `RequestFailed` mit Code und vollqualifiziertem Typnamen oder fester Marke, nie
der Text der Ausnahme; gebaut ist ebenso, dass kein Ereignis der Bibliothek beim Start den Text einer
Ausnahme trägt (Test: `TelemetryRedactionTest`). Zielzustand, nicht gebaut: dass auch der Testserver
beim Start keine Meldung einer Ausnahme schreibt (gebaut schreibt er die Meldung des Ladefehlers der
Tabellenbeschreibung mit Pfad, Zeile und Spalte, einem festen Grund und nie dem Inhalt; `SimLoadException`, `YamlTreeReader`).

Pflicht-Logfelder: `ts`, `level`, `connection_id`, `session_id` (falls vorhanden), `request_id`
(falls vorhanden), `event`, `duration_ms`, `error_code`, `client_profile`; dazu `principal`, nur im
Ereignis „Anmeldung angenommen“. Keine Zugangsdaten, keine Anmeldekennung abgelehnter Anmeldungen
und keine Parameterwerte im Log. Gebaut sind im Modell der Bibliothek (`SPEC-059`) `ts` (Feld `at`), `level`,
`connection_id` (`connectionId`), `session_id` (`sessionId`), `client_profile` (`clientProfile`), `error_code`
(`errorCode`, im Ereignis `RequestFailed`) und `principal`; `event` ist die Art des Ereignisses. **Zielzustand,
nicht gebaut:** `request_id` und `duration_ms`: keine gebaute Ereignisart trägt sie, sie gehören zu „Anfrage
beendet“.

Der Server schreibt kein Log; er gibt Ereignisse an die Senke, die `TdsServer.start` übergeben bekommt (Abschnitt 5,
`SPEC-059`). Ohne Senke entsteht kein Ereignis. Die folgenden Festlegungen sind Setzungen, nicht gemessen,
soweit nicht anders genannt; sie gelten für das Modell der Bibliothek und damit für jeden Adapter:

- **Ereignismodell:** eine geschlossene Menge von Ereignisarten mit typisierten Feldern, kein
  Freitextfeld. `level` (`ERROR`, `WARN`, `INFO`, `DEBUG`) ist je Ereignisart fest. `ts` ist der
  Zeitpunkt des Ereignisses in UTC aus dem Zeitgeber der Bibliothek; die Darstellung über UTC hinaus
  legt der Adapter fest (Voreinstellung ISO-8601, Setzung).
- **Ereignisarten (gebaut; die Menge ist je Stand geschlossen):** Start (`ServerStarted` mit Port) und Ende
  (`ServerStopped`) des Servers; Abbruch des Starts vor dem Binden (`StartRefused`: Backend und
  Tabellen zugleich, doppelter Tabellenname); ungültige Konfiguration je Fehler (`ConfigInvalid`: Schlüssel und Art,
  kein Wert); fehlgeschlagene TLS-Konfiguration (`TlsStartRejected`: Teil und Form); Bindefehler (`BindFailed`:
  Adresse, Port und der Typname der Ausnahme, bei belegtem Port keiner); aktive Abschwächung der Verschlüsselung
  (`EncryptionWeakened`, `SPEC-010`); aktiver Klartext-Testmodus der Anmeldung (`PlainTestModeActive`,
  `AUTH_PLAIN_TESTMODE`); Simulations-Backend verdrahtet (`SimulationBackendReady`); Anmeldung angenommen
  (`LoginAccepted`) und abgelehnt (`LoginRejected` mit Grund-Marke); Adapterfehler (`AdapterFailed`); der Client
  bietet keine Verschlüsselung an (`EncryptionNotOffered`); TLS ab dem ersten Byte nicht angeboten
  (`TlsFirstNotOffered`); TLS-Handschlag gescheitert (`TlsHandshakeFailed`); Ausnahme des Handlers einer Verbindung
  (`ConnectionFailed`); Annehmen gescheitert (`AcceptFailed`); Anfrage mit Fehler beantwortet (`RequestFailed`).
  Die Ebene ist je Art fest (Setzung): `ERROR` für die Abbrüche des Starts, `AcceptFailed`, `ConnectionFailed` und
  `AdapterFailed`; `WARN` für die Abschwächungen, `LoginRejected`, `EncryptionNotOffered`, `TlsFirstNotOffered`,
  `TlsHandshakeFailed` und `RequestFailed`; `INFO` für `ServerStarted`, `ServerStopped` und `LoginAccepted`; `DEBUG`
  für `SimulationBackendReady`. **Zielzustand, nicht gebaut:** Beginn und Ende des geordneten Beendens, Verbindung
  angenommen und beendet (mit der Art des Endes), Anfrage beendet (mit Art, Dauer und Ergebnis), Abbruch durch
  ATTENTION, Schnappschuss der Metriken und Ende eines Spans (Abschnitt 5).
- **`error_code`** ist ein `TDS_*`-Code aus diesem Abschnitt (gebaut im Ereignis `RequestFailed`, Feld `errorCode`,
  dazu `cause` als Typname der Ausnahme oder feste Marke des Anlasses). Eine Ausnahme erscheint nur als
  Typname, nie mit Text und nie mit Stapelspur.
- **`principal`** ist die Kennung aus dem Authentifizierungsadapter und steht nur im Ereignis
  „Anmeldung angenommen“. Ein Ereignis zu einer abgelehnten Anmeldung hat kein Feld für Kennung oder
  Eingabe; es trägt eine Grund-Marke und `connection_id`. Für die mitgelieferte Benutzerliste stammt
  die Kennung aus der Liste des Betreibers. Nicht zugesagt: dass die Kennung eines eigenen
  Authentifizierungsadapters frei von Geheimnissen ist; das verantwortet dessen Betreiber.
- **`client_profile`** wird aus dem ausgehandelten Protokoll abgeleitet. Gebaut sind die Werte `unknown`,
  `tds-8.0:strict` (TLS ab dem ersten Byte) und `tds-7.4:<modus>` mit dem ausgehandelten Verschlüsselungsmodus
  (`off`, `on`, `not_supported`, `required`). Ereignisse des Handshakes im Pfad TDS 7.4 tragen `unknown`, weil der
  Handler den ausgehandelten Modus erst mit dem Ausgang des Handshakes kennt; `LoginAccepted` und `RequestFailed`
  tragen ihn. `ConnectionFailed` trägt auch bei einem Fehler nach der Anmeldung keine `session_id` und das Profil
  `unknown` (Grenze, gebaut: der Listener kennt beides nicht). Angaben des Clients aus LOGIN7 gehen nicht ein.
- **Herkunft der IDs:** `connection_id` ist der Zähler des Listeners (ab 1 je Server; er wiederholt sich nach einem
  Neustart, Logs über Neustarts trennt `ts`); `session_id` ist eine zufällige UUID und steht nur in Ereignissen
  nach der Annahme der Anmeldung; `request_id` ist eine zufällige UUID (**Zielzustand, nicht gebaut:** kein
  Ereignis trägt sie). Mehrere Server in einer JVM mit einer gemeinsamen Senke unterscheidet das Ereignis nicht
  (Grenze, gebaut); wer eine Senke teilt, übergibt jedem Server eine Hülle, die den Server kennzeichnet.
- **Redaktion durch Bauart:** Passwort, Prüfwert, Parameterwert, SQL-Text, Pfad und Ausnahmetext
  haben im Modell kein Feld; es gibt keinen Schalter, der Parameterwerte zuließe. Zeichenketten
  stehen in `principal`, in der Adresse des Bindefehlers, im Typnamen einer Ausnahme oder der festen Marke des
  Anlasses (`cause`), im Schlüsselnamen und der Art der Konfiguration, in Teil und Form der TLS-Diagnose und in der
  Art des Handschlag-Fehlers; die Wertemengen von `part`, `kind`, `failure` und `errorCode` sind die Namen interner
  Konstanten und nicht zugesagt; das ist keine geschlossene Menge (ob weitere Felder, etwa die Quelladresse der
  Gegenstelle, hinzukommen, ist nicht festgelegt). Jedes Zeichenketten-Feld ist auf 256 UTF-16-Einheiten begrenzt
  (Setzung, darüber wird abgeschnitten, ein getrenntes Paar aus Ersatzzeichen entfällt ganz). Gebaut und geprüft:
  `TelemetryEventTest`, `TelemetryRedactionTest`. Nicht zugesagt: Escaping, Ziele und Aufbewahrung im Adapter eines
  Embedders.
- **Abbruch des Starts:** Das Ereignis zum Bindefehler nennt Adresse und Port (`SPEC-043`).
  `StartOutcome.Aborted.reason` nennt dieselbe Adresse und denselben Port; Adresse und Port sind
  Betriebsangaben, kein Geheimnis. `reason` enthält weder Pfade noch Ausnahmetext noch Schlüsselmaterial
  oder Kennwörter. Die „Diagnose, die Adresse und Port nennt“ (`SPEC-043`) steht damit auch ohne
  übergebene Senke im `reason`; das Ereignis ist ihre Form für die Senke (Festlegung). Gebaut ist
  `reason` als „Start gescheitert: “ plus die Meldung des Fehlers; beim belegten Port lautet sie „Port belegt:
  <Adresse>:<Port>“ (`ListenerConfig.kt`, `Bootstrap.kt`). Geprüft ist die Freiheit von Pfaden und Ausnahmetext für
  jeden Abbruchgrund (ungültige Konfiguration, Backend und Tabellen zugleich, doppelter Tabellenname,
  TLS-Konfiguration, belegter Port, Binden gescheitert), mit Eingaben, die Pfad, Schlüsselinhalt, Kennwort und
  Tabellenname trügen (`BootstrapTest`, `SecurityConfigNegativeTest`, `SimulationWiringTest`,
  `TelemetryRedactionTest`); nicht jede Form der TLS-Diagnose ist einzeln mit Eingaben belegt.

## 5. Metriken und Tracing-Felder

| ID | Span | Pflicht-Attribute | Quelle |
|---|---|---|---|
| `SPEC-017` | `tds.connection` | `connection.id`, `tds.phase`, `outcome` | Transport/Handshake |
| `SPEC-018` | `tds.query` | `session.id`, `request.id`, `tds.request_kind`, `outcome` | Anfrageverarbeitung |
| `SPEC-019` | `backend.execute` | `request.id`, `duration_ms`, `outcome` | Backend-Adapter |

Metriken: aktive Sessions, Login-Erfolgs-/Fehlerquote, Anfragen/s, Anfrage-Latenz p50/p95/p99,
aktive Backendoperationen, Abbrüche, Protokollfehler. Keine SQL-Texte als Metrik-Labels.

**Zugang zu Metriken und Spans (Festlegung, nicht gemessen; gebaut ist die Senke, Zielzustand, nicht gebaut sind
Schnappschuss und Span-Ende):**

- Der Schnappschuss der Metriken (Ereignis `metrics`) und das Ende eines Spans gehen als Ereignisse
  an die Senke der Bibliothek, dieselbe wie für das Log (Abschnitt 4); **Zielzustand, nicht gebaut:** keine
  gebaute Ereignisart ist ein Schnappschuss oder ein Span-Ende. Gebaut: die Senke ist der Parameter `telemetry` von
  `TdsServer.start` (Typ `TelemetrySink`, `SPEC-059`); ohne Senke (Voreinstellung `TelemetrySink.NONE`) erzeugt
  und schreibt die Bibliothek nichts (Tests: `TelemetryTest`, `EmbeddingTest`, `TelemetryEmbeddingTest`).
- Vertrag der Senke (gebaut, KDoc von `TelemetrySink`): Sie blockiert nicht und wirft nicht; die Bibliothek fängt
  eine `Exception` der Senke ab und verwirft sie (Test: `TelemetryTest`); bei einer `InterruptedException` bleibt das
  Flag der Unterbrechung gesetzt (Test: `TelemetryTest`). Eine `CancellationException` wird nicht gefangen. Dass
  auch jeder `Error` der Senke nicht gefangen wird, ist eine Festlegung dieses Stands, aus der Behandlung der
  Abbruchausnahme im Anfragepfad nur für die `CancellationException` abgeleitet. Belegt ist es an der Grenze der
  Meldestelle (`TelemetryTest`); für Meldestellen in der Infrastruktur des Listeners (`AcceptFailed`,
  `ConnectionFailed`) ist es nicht gemessen und nicht zugesagt. Die Senke läuft bei Ereignissen einer Verbindung auf
  dem Dispatcher der Verbindung (`Dispatchers.Default`, prozessweit); eine blockierende Senke belegt dort Threads,
  die auch andere Coroutinen des Prozesses nutzen (Grenze; die Bibliothek schützt davor nicht). Wer die Senke
  übergibt, besitzt und schließt sie; die Bibliothek schließt sie nicht.
- Der Abstand der Schnappschüsse ist ein Wert der `ServerConfig` in Sekunden (Voreinstellung 60,
  0 schaltet ihn aus; Setzung, nicht gemessen) und wird in `config` geprüft; der Wertebereich ist
  nicht festgelegt. Ob das Ereignis `metrics` die Verteilung als Perzentile (p50, p95, p99,
  Voreinstellung dieser Spezifikation) oder als Zähler je Klasse trägt, ist nicht festgelegt.
- Namen und Pflicht-Attribute der Spans (`SPEC-017` bis `SPEC-019`) bleiben. JMX, ein
  Text-Endpunkt und eine Telemetrie-Bibliothek sind nicht Teil der Zusage; sie wären Adapter hinter
  der Senke.

## 6. Externe Verträge

| ID | System | Version | Referenz |
|---|---|---|---|
| `SPEC-020` | Microsoft TDS | TDS 7.4 und TDS 8.0 (Strict-Encryption) | [MS-TDS](https://learn.microsoft.com/en-us/openspecs/windows_protocols/ms-tds/) |
| `SPEC-021` | .NET SQL-Client | `Microsoft.Data.SqlClient` 7.1.1 auf .NET 10 (SDK 10.0.403), im Container ausgeführt | [Clientprofil](#clientprofil-vertrag-zu-spec-021) |
| `SPEC-022` | Query-Backend | Projektvertrag v0: Typen und Schnittstellen als Quelltext (Setzung vom 2026-10-30, nicht gemessen); Pflichten und Grenzen nach Nutzerentscheidung vom 2026-10-31 (Messung im Spike, nicht am Produkt), Rest Entwurf | [Query-Backend](#query-backend-vertrag-zu-spec-022) |
| `SPEC-023` | TLS | TLS 1.3 und TLS 1.2 (Mindestversion 1.2), TLS 1.3 nur im TDS-8.0-Pfad, der TDS-7.4-Pfad höchstens TLS 1.2; Suiten TLS 1.3: `TLS_AES_256_GCM_SHA384`, `TLS_AES_128_GCM_SHA256`; TLS 1.2: ECDHE mit AES-GCM; Standard `REQUIRED` | [Sicherheitsprofil](#sicherheitsprofil-vertrag-zu-spec-023) |
| `SPEC-051` | Authentifizierungsadapter | Schnittstelle als Kotlin-Quelltext (Festlegung, nicht gemessen); Prüfwert der festen Benutzerliste mit Verfahrenskennung (`plain` nur im Testmodus, `pbkdf2-sha256`), eigene Datei nur für die Benutzerliste, Iterationen mit Boden, Standard und Obergrenze, strenger Parser; Dateirechte und Behandlung eines BOM offen (Stand 2026-10-30) | [Authentifizierungsadapter](#authentifizierungsadapter-vertrag-zu-spec-051) |
| `SPEC-059` | Einstieg des Integrators | Kotlin-Fassade `TdsServer.start(config, source, telemetry)` im Package `runtime` mit `BackendSource` und `StartOutcome` (Festlegung, nicht gemessen); Senke `TelemetrySink` für Log und Telemetrie, Voreinstellung keine; Prüfung der Konfiguration beim Start, Einmal-Konfiguration, Stop idempotent; Nachweis ohne Zugriff auf `internal`-Namen im Gate `make test`: der Einbettungstest `integrator` (Source-Set ohne Assoziation) und die Übersetzung des Moduls `testserver`, das die Bibliothek als Projekt-Abhängigkeit bindet (`SPEC-052`) | [Einstieg des Integrators](#einstieg-des-integrators-vertrag-zu-spec-059) |

### Clientprofil (Vertrag zu `SPEC-021`)

Versioniertes Profil des Referenzclients für die Kompatibilitätsabnahme (`SPEC-004`).

| Eigenschaft | Wert |
|---|---|
| .NET | .NET 10, SDK 10.0.403 |
| Paket | `Microsoft.Data.SqlClient` 7.1.1 (exakt) |
| Protokoll | TDS 7.4 und TDS 8.0 |
| Nicht im Profil | `System.Data.SqlClient`, andere Paket- und Laufzeitversionen |

#### Verbindungsmodi des Clientprofils (ohne Secrets)

| Modus | Connection-String-Optionen | Protokollverhalten |
|---|---|---|
| TDS 7.4, ohne TLS (Testmodus nur durch ausdrückliche Konfiguration, siehe `SPEC-023`) | `Encrypt=false` | PRELOGIN-Option ENCRYPTION `00`, LOGIN7 `TDSVersion=0x74000004`, Paketgröße 8000 |
| TDS 7.4, mit TLS | `Encrypt=true;TrustServerCertificate=true` | ENCRYPTION `01`, der TLS-Handshake läuft in TDS-Paketen vom Typ `0x12`, danach kommt LOGIN7 verschlüsselt |
| TDS 8.0 | `Encrypt=Strict;HostNameInCertificate=<Name>` | TLS 1.3 ab dem ersten Byte, ALPN `tds/8.0`, danach PRELOGIN und LOGIN7 `TDSVersion=0x08000000`; `TrustServerCertificate` gilt nicht, das Zertifikat muss dem Client vertrauenswürdig sein |

#### Beobachtete Werte des Referenzclients

- PRELOGIN-Option VERSION: `07010001A066`.
- PRELOGIN-Option FEDAUTHREQUIRED: `01`.
- LOGIN7 AppName und CltIntName: `Core Microsoft SqlClient Data Provider`.

#### Login-Antwort des Servers (Festlegung)

Was der Server auf PRELOGIN und LOGIN7 sendet, ist eine Festlegung ohne Messung am Produkt; sie sagt nicht, dass der Referenzclient sie annimmt. Gemessen am Referenzclient gegen den Spike-Testserver (TDS 7.4, ohne Pool) sind nur: LOGINACK mit DONE genügt dem Client beim `Open`, die Collation-ENVCHANGE braucht der RPC mit Zeichenparametern, und die Ablehnung braucht genau ein ERROR mit DONE.

| Gegenstand | Festlegung des Servers (Package `protocol/session`) |
|---|---|
| PRELOGIN-Antwort | Pakettyp `0x04`; Optionen VERSION (`16.0.1000.6`, Setzung, siehe unter der Tabelle), ENCRYPTION, INSTOPT (leer), MARS (aus); kein FEDAUTHREQUIRED. ENCRYPTION ist ENCRYPT_REQ bei `REQUIRED`, ENCRYPT_NOT_SUP im ausdrücklich konfigurierten Testmodus |
| Annahme | Pakettyp `0x04`; ENVCHANGE Datenbank, Packet Size, SQL-Collation, dann LOGINACK (Interface 1, TDS-Version `0x74000004`, bei TDS 8.0 `0x08000000`) und DONE ohne Statusbits |
| Ablehnung | genau ein ERROR (18456, State 1, Class 14, Text "Login failed." ohne Kennung), dann DONE mit dem Fehlerbit; danach schließt der Server |
| Datenbank und Packet Size | die Datenbank aus LOGIN7, sonst `master`; die verlangte Packet Size, wenn der Paket-Codec sie schreiben kann (größer als der Paketkopf von 8 Byte und höchstens `MAX_TDS_PACKET_BYTES`), sonst 4096 (Setzung, entspricht `DEFAULT_PACKET_SIZE`, nicht gemessen). Die Packet Size wird per ENVCHANGE angekündigt und steht in der Sitzung; **angewandt wird sie ab dem Beginn der Anfrageschleife**: `RequestLoop.run` stellt sie vor der ersten Anfrage am Paket-Schreiber ein (`channel.usePacketSize(ready.session.packetSize)`), und die Antworten auf Anfragen haben Pakete dieser Größe (Test `StreamingDispatchTest`, Packet Size 512 und 1000); eine laufende Antwort behält ihre Größe. Die Nachrichten davor (die Antwort auf PRELOGIN, die Antwort auf LOGIN7 mit LOGINACK) schreibt der Paket-Schreiber mit der Größe, mit der er gebaut wird (4096); ein Client, der weniger verlangt, kann dort Pakete über seiner Größe sehen (nicht am Referenzclient gemessen) |

**VERSION der PRELOGIN-Antwort (Setzung, nicht gemessen).** `16.0.1000.6`; Hauptversion, Build und Unter-Build sind gesetzt, nicht an einem echten Server abgelesen, und die Byteordnung des Unter-Builds bleibt Festlegung. Grund: Der Wert ging in einer Sonde mit mssql-jdbc 13.4.0 (in d-migrate 1.12.0) bei der Anmeldung durch. Gemessen am 2026-11-05 (Einzelmessung, ein Host, Protokoll im Messprotokoll-Verzeichnis des Repos) lehnt derselbe Treiber gegen den Testserver mit VERSION `0.0.0.0` ab (`SQL Server version 0 is not supported by this driver`, Exit 4 von d-migrate) und nimmt `16.0.1000.6` an. Welche Hauptversion mssql-jdbc mindestens verlangt, ist nicht gemessen; dass der .NET-Referenzclient den Wert annimmt, ist nicht gesondert gemessen (die Referenztests prüfen `ServerVersion` nur auf nicht leer). Der Wert gehört nicht zum Clientprofil und sagt nichts über einen echten SQL Server.

#### RPC-Katalog des Referenzclients

Der Referenzclient sendete in den gemessenen Szenarien (Messgrenzen: Testserver, ohne echten SQL Server; TDS 7.4 unverschlüsselt, kein TDS 8.0, kein TLS; `Pooling=false`; eine Verbindung je Szenario; ein Lauf): Batch (`0x01`) für Text ohne Parameter; `sp_executesql` (ProcID 10) für Text mit Parametern; `sp_prepexec` (ProcID 13) und `sp_execute` (ProcID 12) bei `Prepare()` mit Parametern; RPC mit Prozedurnamen für `CommandType.StoredProcedure`. `sp_prepare`, `sp_unprepare` und `sp_cursor*` erschienen in diesen Szenarien nicht; unter Pooling ist das nicht gemessen. `ExecuteReader(SchemaOnly)` sendet einen Batch mit `SET FMTONLY OFF; SET FMTONLY ON;` im Text (Behandlung offen). `ExecuteReader(KeyInfo|SingleRow)` sendet `sp_executesql` mit `SET FMTONLY OFF; SET NO_BROWSETABLE ON;` im Text (Behandlung offen); Transaktionen sind Pakettyp `0x0E`, kein RPC (offen). Der Katalog ist als Entwurf in `SPEC-030` festgelegt und noch nicht freigegeben.

#### Antwortformen des Servers (Festlegung)

Die Formen, die der Server liefert, sind Setzungen ohne Messung am Referenzclient; an einem echten SQL Server sind Tokenfolge, Statusbits und Ordinale für 17 aufgenommene Szenarien gemessen (Abweichungen des Servers stehen in der Ausnahmeliste des Messprotokolls), sonst Handvektoren (`TDS-FA-10.a`, `SPEC-030`, `SPEC-031`).

| Form | Antwort |
|---|---|
| SQL_BATCH, ein oder mehrere Ergebnisse und Update-Zähler | COLMETADATA, ROW und DONE je Ergebnis, DONE mit `COUNT` je Update-Zähler, `MORE` an jedem Abschluss außer dem letzten; ein leeres Ergebnis hat COLMETADATA und einen Abschluss |
| RPC, auch mit Ergebnissen, Rückgabestatus und OUTPUT-Parametern | wie im Absatz „RPC im Backend-Vertrag“ des Backend-Vertrags: DONEINPROC je Abschluss, RETURNSTATUS, je OUTPUT- und InputOutput-Parameter ein RETURNVALUE, DONEPROC zum Schluss |
| Fehler im Strom | ERROR und DONE mit Fehlerstatus, solange der Strom synchron ist, sonst schließt der Server (`SPEC-031`) |

Nicht unterstützt und ohne Erfolgsantwort (definierter Fehler, nie ein unvollständiger Erfolgsstrom): eine Zeile ohne offenes Ergebnis, ein Ergebnisende ohne offenes Ergebnis, ein Ergebnisanfang oder Update-Zähler im offenen Ergebnis und ein Strom, der im offenen Ergebnis endet (`SPEC-048`, Verbindung schließt); RETURNSTATUS oder RETURNVALUE in der Antwort auf einen Batch sowie ein Typ außerhalb der Abbildung (`TDS_UNSUPPORTED_TYPE`, `SPEC-014`). Ebenfalls nicht unterstützt: Transaktionen (Pakettyp `0x0E`), MARS, Table-Valued-Parameter und weitere Antwortformen außerhalb des Clientprofils.

#### Offene Punkte des Clientprofils

`Encrypt`/`TrustServerCertificate`-Details, unterstützte RPCs (Freigabe des Entwurfs oben), MARS, Transaktionen, Verbindungs-Pooling und Lastprofil stehen in `SPEC-004` als offene Freigabepunkte. Die Authentifizierung ist dort festgelegt (SQL-Authentifizierung, `SPEC-051`) und kein offener Freigabepunkt.

### Query-Backend (Vertrag zu `SPEC-022`)

Vertrag zwischen Protokollkern und Backend (`TDS-FA-08.a`, `SPEC-002`). Der Protokollkern erzeugt einen `QueryRequest` ohne TDS-Bytes und liest das Ergebnis als Strom von Ereignissen; das Backend kennt weder Pakete noch Tokens. Die Typen und Schnittstellen stehen als Kotlin-Quelltext (Abschnitt „Signatur des Backend-Vertrags“); alles darüber hinaus ist ein **Entwurf**, soweit es nicht unten als Festlegung oder Nutzerentscheidung gekennzeichnet ist. Gemessen ist das Ergebnismodell an einem Generator-Backend mit drei Spalten, nicht der Kotlin-Vertrag.

#### Aufruf des Backends

| Teil | Inhalt |
|---|---|
| `execute(QueryRequest)` | suspendierend; liefert eine `QueryExecution` mit dem Strom `events` |
| `QueryRequest` | Sitzungs- und Anfragekennung, SQL-Text, typisierte Parameter, optionale Frist (`SPEC-002`) |
| `cancel(sessionId, requestId)` | suspendierend; beendet die laufende Operation |
| `QueryExecution.close()` | schließt Zeilenquelle und Ressourcen der Operation |

#### Signatur des Backend-Vertrags

Stand des Quelltexts: Setzung des Aufrufers (2026-10-30), nicht gemessen.

| Typ | Package | Anmerkung |
|---|---|---|
| `QueryRequest`, `QueryParameter`, `ParameterDirection`, `QueryEvent` (sieben Ereignisse), `ResultColumn`, `SqlType` | `domain` | reine Modelle; `domain` importiert keine Fremdbibliothek |
| `QueryBackend`, `QueryExecution` | `port` | tragen den `Flow`; `port` importiert `domain`, die Standardbibliothek und `kotlinx.coroutines` |

- **Fehler beim Start:** `execute()` wirft für Fehler des Backends keine Ausnahme; ein Fehler beim Start (zum Beispiel Backend nicht erreichbar) ist ein `Failure` im Strom `events`. Die Abbruchausnahme der Coroutine bleibt davon unberührt. Setzung, nicht gemessen.
- **`SqlType`** (Definition in `SPEC-002`): `Int32`, `Int64`, `Bool`, `UnicodeText`, `Decimal(precision, scale)` mit `precision >= 1` und `0 <= scale <= precision`, `Date`, `Time`, `DateTime` (das Modell trägt keine Zone) — der Mindestkatalog von `SPEC-003`. Breiten unter 32 Bit, Gleitkomma, Geld, GUID und Binärtypen gehören nicht dazu. Setzung, nicht gemessen. Eine Precision über 38 ist im Modell darstellbar und scheitert in der Typabbildung mit `TDS_UNSUPPORTED_TYPE`, bevor COLMETADATA geschrieben wird (Nutzerentscheidung vom 2026-10-30, nicht gemessen). Längenlimit von `UnicodeText` (4000 UTF-16-Einheiten), Scale der Zeittypen (7) und Zeitzonenannahme (Werte ohne Zone) sind Setzungen der Typabbildung (`SPEC-003`). JVM-Klassen der Werte je Spaltenart (Nutzerentscheidung vom 2026-10-31, Definition in `SPEC-002`): `Int32` als `Int`, `Int64` als `Long`, `Bool` als `Boolean`, `UnicodeText` als `String`, `Decimal` als `java.math.BigDecimal`, `Date` als `LocalDate`, `Time` als `LocalTime`, `DateTime` als `LocalDateTime`, NULL als `null`; sie gelten für `Row`, `QueryParameter` und `OutputParameter`.
- **Belegt am Test-Backend** (Test-Quelltext, suspendierend): Metadaten vor Zeilen, leeres Ergebnis mit Metadaten, `Failure` vor und nach `ResultStart`, Fehler beim Start als `Failure`, `close()` und `cancel()` erreichen die Zeilenquelle des Test-Backends. Das ist keine Aussage über blockierende oder echte Backends.

#### Ergebnis des Backend-Aufrufs

Das Ergebnis ist ein asynchroner Strom (`Flow`) von Ereignissen: `ResultStart` (Spalten), `Row`, `ResultEnd`, `UpdateCount`, `ReturnStatus`, `OutputParameter`, `Failure`. Ein Backend mit kleinem Ergebnis darf den Strom aus einer fertigen Liste erzeugen; der Vertrag setzt keine Grenze für „klein“ (Setzung, Voreinstellung 2026-10-30, vom Nutzer nicht ausdrücklich gewählt, nicht gemessen), siehe „Pflichten des Backends“. Das Backend setzt kein `DONE_MORE`: der Response Sequencer hält ein `ResultEnd` bzw. `UpdateCount` ein Ereignis lang zurück, bis das nächste Ereignis oder das Ende bekannt ist, und setzt daraus das Bit.

#### Pflichten des Backends

- Metadaten (`ResultStart`) kommen vor den Zeilen (`TDS-FA-08.a`).
- Gelesen wird nur so schnell, wie der Transport abnimmt (Backpressure); ein Backend läuft nicht beliebig voraus. **Gebauter Stand (Festlegung, am Test-Backend über Zähler belegt, nicht am Produkt gemessen):** Die Antwort geht Token für Token an den Paket-Schreiber der Sitzung (Packet Size aus dem Login). Nimmt der Kanal nichts ab, suspendiert der Lauf, und das Test-Backend liefert höchstens so viele Ereignisse voraus, wie in den vom Kanal angenommenen Paketen, dem einen Paket im Schreiben (ein volles Paket geht erst hinaus, wenn das nächste Byte vorliegt) und dem Ereignis in Arbeit Platz haben; der Speicher des Schreibers hängt nicht an der Größe des Ergebnisses: er besteht aus dem Puffer von einem Paket Nutzdaten, dem Paket im Schreiben samt den Kopien beim Paketbau und dem Stück des Aufrufers (ein Token); eine Byte-Zahl ist nicht gemessen. Fällt im Schreiben der Antwort ein Fehler (etwa Speichermangel), kann der Strom mitten in einem Token stehen: der Schreiber nimmt dann nichts mehr an, und die Verbindung schließt ohne ERROR und DONE. Die Aussage gilt für das Test-Backend an einem Draht ohne Netz, nicht für einen Socket, ein blockierendes Backend oder das Simulations-Backend.
- Endet das Lesen durch Abbruch der Coroutine der Anfrage (Disconnect) oder durch einen Fehler beim Schreiben der Antwort, ruft der Kern `cancel()` und `close()` genau einmal je Anfrage und liest kein weiteres Ereignis. Nach einer vollständigen oder mit ERROR und DONE beendeten Antwort ruft er nur `close()`. Festlegung, nicht gemessen; die Quelle des Cancel durch ATTENTION ist davon nicht berührt.
- Cancel und Disconnect beenden das Lesen und schließen die Zeilenquelle (`SPEC-029`, `SPEC-031`). Ein Backend, dessen Operation sich nicht unterbrechen lässt, hält die Sitzung bis `CANCEL_TIMEOUT` (`SPEC-033`); danach schließt der Server die Verbindung.
- **Suspendierend (Nutzerentscheidung vom 2026-10-31):** Ein Backend und jede Zeilenquelle (`SPEC-035`, `next()`) blockieren den Thread des Aufrufers nicht. Ein blockierender Treiber wird vom Integrator hinter einen eigenen, begrenzten Pool gelegt und unterbrechbar aufgerufen; der Server stellt keinen Adapter. Gemessen im Spike (fingiertes Backend, Blockieren als `Thread.sleep`, „nicht unterbrechbar“ als Setzung, Kotlin 2.1.20 und `kotlinx-coroutines-core` 1.10.1 statt der Versionen des Repos, 200 Ströme zu 5000 Zeilen): ein Backend, das je Zeile blockiert, brauchte 77 Threads statt 11 und das 8,2-fache der Wanddauer (3099 ms gegen 377 ms); die anderen Umhüllungen lagen bei 26 bis 78 Threads und 707 bis 3127 ms; der Abbruch eines nicht unterbrechbaren Aufrufs wartete den Rest der Blockade ab (2400 ms bei 3000 ms), bei einer Blockade über `CANCEL_TIMEOUT` schließt die Sitzung, und der Thread bleibt bis zur Rückkehr belegt. Die Zahlen gelten für den Spike, nicht für das Produkt oder einen echten Treiber. Empfehlung, keine Pflicht (Setzung, Voreinstellung 2026-10-30): blockweise statt je Zeile aufrufen (707 ms gegen 3099 ms im Spike). Ein Hilfsadapter im Server ist ein benannter Folgeschritt mit dem Auslöser „ein echtes Backend, dessen Treiber blockiert“.
- **Kleine Ergebnisse (Setzung, Voreinstellung 2026-10-30, vom Nutzer nicht ausdrücklich gewählt, nicht gemessen):** Ein Backend, das ein Ergebnis materialisiert, begrenzt es selbst. Der Server erzwingt keine Grenze; er schützt sich durch den Rückstau der Senke und die Behandlung des `OutOfMemoryError` (`SPEC-031`).
- **Frist (Nutzerentscheidung vom 2026-10-31):** Der Protokollkern setzt die Frist durch. Quelle ist `QUERY_TIMEOUT` (`SPEC-009`): er setzt `deadlineEpochMillis` auf „Beginn der Anfrage plus `QUERY_TIMEOUT`“ und umschließt die Sammlung des Stroms mit einem Zeitlimit. Bei Ablauf geht der Lauf den Weg des Cancel (Strom abbrechen, `close()`, zurückgehaltenen Abschluss verwerfen) und endet in `TDS_TIMEOUT` (`SPEC-016`; ERROR und DONE mit Fehlerstatus, solange der Strom synchron ist, sonst schließt die Verbindung, `SPEC-031`). Das Feld bleibt für kooperative Backends ein Hinweis; ein Backend darf es ignorieren. Einzelheiten (Setzung, Voreinstellung 2026-10-30, nicht gemessen): `null` heißt keine Frist; eine Frist in der Vergangenheit endet sofort in `TDS_TIMEOUT`; die Wanduhr wird einmal beim Start der Sammlung in eine Dauer umgerechnet; `CANCEL_TIMEOUT` (`SPEC-033`) bleibt unabhängig davon die Frist für den Abbruch selbst. Gemessen im Spike (Frist 500 ms): Überschreitung 0 bis 3 ms bei suspendierendem oder unterbrechbarem Backend, bis zum Ende des Aufrufs bei nicht unterbrechbarem (1500 bis 1513 ms bei 2000 ms Blockade; im Betrieb begrenzt durch `CANCEL_TIMEOUT`, `SPEC-033`); ein Backend, das die Frist ignoriert, lief bis zum Stromende (3042 ms). `TDS_TIMEOUT` ist ein Anfragefehler der Class 16 bei offener Verbindung (Setzung des Nutzers vom 2026-11-01, nicht gemessen; `SPEC-016`, §4). Läuft die Frist beim Beginn schon ab, wird das Backend nicht gerufen.
- **`OutOfMemoryError` (Nutzerentscheidung vom 2026-10-31):** Er wird im Anfragepfad gefangen und als `TDS_INTERNAL_ERROR` beantwortet (`SPEC-031`, `SPEC-048`); die Verbindung schließt nach der Antwort. Betreiberschalter, kein Server-Code: der Startparameter `-XX:+ExitOnOutOfMemoryError` beendet den Prozess beim ersten `OutOfMemoryError` (im Spike Exit-Code 3 nach rund 2 s). Akzeptiertes Negativ: im Spike war beim Fangen in vier von vier Läufen nach 20 s mindestens eine Coroutine nicht beendet, in dreien davon eine unbeteiligte kleine Verbindung (ob sie hängt oder später endet, ist nicht unterschieden); die Ursache ist nicht isoliert, die Häufigkeit im Produkt nicht gemessen; ob die Antwort im erschöpften Heap noch geschrieben werden kann, ist nicht gemessen.

#### Festlegungen des Backend-Vertrags

- Fehlersemantik im Strom (Setzung des Nutzers, nicht am Produkt gemessen): Solange der Strom synchron ist, folgen auf Teilzeilen ein `Failure` und damit ERROR und DONE; lässt er sich nicht mehr abschließen, schließt der Server die Verbindung ohne ERROR und DONE (`SPEC-031`).
- Festlegungen des Response Sequencers (Nutzer, 2026-10-30, nicht am Produkt gemessen; der Tokenstrom ist Handvektor und Selbstkonsistenz): ein `Failure` zählt in der `DONE_MORE`-Vorschau als nächstes Ereignis (ein zurückgehaltenes `ResultEnd` oder `UpdateCount` bekommt `DONE_MORE`, danach ERROR und DONE mit Fehlerstatus). Beleg nur für das Bit vor dem ERROR: der Referenzclient gegen einen echten SQL Server zeigt `DONE[MORE|COUNT]` vor dem ERROR eines batch-abbrechenden Fehlers; am Produkt nicht gemessen. Für statement-abbrechende Fehler, bei denen der echte Server `DONE[MORE|ERR]` sendet, schreibt der Sequenzierer die Variante ohne `MORE`, weil `Failure` keine Klasse trägt); ein zurückgehaltener Abschluss wird bei Cancel oder Disconnect verworfen; ein Verstoß gegen die Ereignisreihenfolge ist ein Fall von `SPEC-048` (ERROR, DONE mit Fehlerstatus, Verbindung schließen); `ReturnStatus` und `OutputParameter` werden in der Antwort auf einen Batch mit einem Fehler der Typabbildung abgelehnt (in der Antwort auf einen RPC werden sie abgebildet, siehe „RPC im Backend-Vertrag“); mit der Zuordnung in §4 ist das `TDS_UNSUPPORTED_TYPE` (Class 20) und die Verbindung schließt nach der Antwort, ebenso bei jedem anderen Typfehler (die Einzelzuordnung des Codes ist offen). Der Ausgang des Sequenzierers trägt die Verbindungsfolge (Fehler offen oder schließt nach der Antwort, je nach Abbildung; Reihenfolgeverstoß und Ausnahme schließen; ein nicht abschließbarer Strom schließt ohne Antwort). Eine Ausnahme des Backends wird dem Fehler-Mapping mit der Ausnahme übergeben, damit sie ins Log geht; ihr Text erreicht den Client nie. Eine Abbruchausnahme läuft aus `execute()` wie aus dem Strom nach außen. Setzung des Implementers: ein `Failure` beendet den Lauf, spätere Ereignisse werden nicht gelesen; ein Strom ohne Ereignis endet in einem DONE ohne Bits; eine Ausnahme aus `execute()` oder aus dem Strom ist ein Fall von `SPEC-048`.

- RPC im Backend-Vertrag (Nutzerentscheidung vom 2026-10-31, nicht gemessen): Ein RPC erreicht das Backend als `QueryRequest` mit Text und Parameterliste, der Vertrag bleibt unverändert. `sp_executesql`: der SQL-Text der Nachricht unverändert als `sql`; die Deklarationsliste wird nicht gedeutet, Name, Typ und Wert jedes Wertparameters kommen aus der Nachricht, die Richtung aus dem Statusbyte (Bit 0 mit Wert `INPUT_OUTPUT`, Bit 0 mit NULL `OUTPUT`, sonst `INPUT`). RPC per Prozedurname: der Server baut aus Name und Parameternamen den Text `EXEC <name> @a = @a, @o = @o OUTPUT`; die Werte stehen nur in der Parameterliste, nie im Text. Der Name muss einer engen Grammatik folgen (ein bis vier Teile, je regulärer Bezeichner oder in eckigen Klammern, höchstens 128 Zeichen je Teil, kein Steuerzeichen), jeder Parametername `@` plus Bezeichnerzeichen (ohne Beachtung der Schreibweise eindeutig, `@@` am Anfang nur beim RPC per Prozedurname abgelehnt, weil der Name dort im Aufruftext auch rechts von `=` steht und `@@x` eine globale Funktion bezeichnen kann; bei `sp_executesql` steht der Name nicht im Text und `@@x` ist nicht abgelehnt; Setzung der Nutzerentscheidung vom 2026-10-31, nicht gemessen); sonst gilt der RPC als unbekannt (`SPEC-013`). Zählregel der Obergrenze 128 (Festlegung): ein regulärer Teil zählt mit allen Zeichen, ein Teil in Klammern ohne die Klammern (Escape `]]` zählt zwei Zeichen), ein Parametername mit dem `@`. Ein späteres Backend muss die Quotierung der Namen im Aufruftext selbst beachten: ein Teil in eckigen Klammern endet nur an einem einfachen `]` (`]]` ist ein Zeichen), danach folgen nur `.` oder das Ende; es darf den Aufruftext nicht an anderen Zeichen trennen. Ein Parameter ohne Namen beim RPC per Prozedurname (bei `sp_executesql`, `sp_prepexec` und `sp_execute` heißt er `@P<i>`, `SPEC-030`), mit anderem Statusbit als OUTPUT oder mit einem Typ ohne Entsprechung in `SqlType` (`INTN` mit 1 und 2 Byte, Gleitkomma, Geld, GUID, Binär, `varchar`, `DATETIMN`, `DATETIMEOFFSETN`) ergibt ebenfalls `SPEC-013`; ebenso ein Wert außerhalb des Bereichs seines Typs (Dezimalbetrag ab 10 hoch Precision, Vorzeichenbyte außer 0 und 1, Datum nach 9999-12-31, Zeit ab 24 Uhr; Festlegung ohne Messung). Ein PLP-Typ (`nvarchar(max)`, `varchar(max)`, `varbinary(max)`) ist gültig kodiert, aber nicht unterstützt und ergibt ebenfalls `SPEC-013` bei offener Verbindung (Setzung der Nutzerentscheidung vom 2026-10-31, nicht gemessen). Eine RPC-Nachricht, die der Parser als Struktur- oder Kodierfehler abweist (Format, Länge, Typkennung außerhalb der Typliste, TYPE_INFO, ALL_HEADERS), wird dagegen wie ein SQL_BATCH mit Parserfehler beantwortet: `SPEC-011` beziehungsweise die Kategorie des Parsers, die Verbindung schließt (Nutzerentscheidung vom 2026-10-31, nicht gemessen). Die JVM-Typen der Parameterwerte sind die der Zeilenwerte (`Int`, `Long`, `Boolean`, `String`, `BigDecimal`, `LocalDate`, `LocalTime`, `LocalDateTime`). Setzung des Implementers, nicht gemessen: `sp_prepexec` erreicht das Backend mit dem Text der Nachricht und den Wertparametern, `sp_execute` mit dem gemerkten Text und den Wertparametern der Nachricht (`SPEC-030`); per Name gesendet ergeben `sp_executesql`, `sp_prepexec`, `sp_execute`, `sp_prepare`, `sp_unprepare` und `sp_cursor*` ebenfalls `SPEC-013`. Die Antwort auf einen erfolgreichen RPC hat die Form RPC des Response Sequencers (Setzung des Implementers; Tokenfolge, Statusbits und das Ordinal sind an einem echten SQL Server für `sp_executesql` mit und ohne Ausgabewert und für `sp_prepexec` in je einem Lauf je Instanz (2022, 2025) gemessen, Fehler im RPC und ein Ergebnis hinter dem RETURNVALUE nicht): der Abschluss eines Ergebnisses oder Update-Zählers ist ein DONEINPROC (`MORE` auch vor dem DONEPROC, `COUNT` nur bei gemeldeter Zahl; der Testserver des Spikes schrieb den letzten Abschluss ohne `MORE`, die Abweichung ist eine Setzung ohne Messung), `ReturnStatus` des Backends wird RETURNSTATUS, je `OutputParameter` entsteht ein RETURNVALUE (Statusbyte 1, Name wie vom Backend genannt, Typ nullbar, Ordinal ab 0 in der Reihenfolge der Ereignisse und nicht als Position in der Aufrufliste, Setzung; ein echter SQL Server zeigt in einem Fall (OUTPUT-Parameter an dritter Stelle von `sp_executesql`) das Ordinal 2, NULL bleibt NULL), und den Strom schließt ein DONEPROC ohne Bits und ohne Zahl; die Tokens stehen in der Reihenfolge des Stroms; ein Ergebnis nach einem Ausgabewert ist nicht ausgeschlossen, ob der Client diese Folge annimmt, ist offen. Liefert das Backend keinen `ReturnStatus`, entsteht kein RETURNSTATUS; ohne `OutputParameter` entsteht kein RETURNVALUE. Ein zweiter `ReturnStatus` und ein `ReturnStatus` oder `OutputParameter` in einem offenen Ergebnis sind ein Verstoß gegen die Ereignisreihenfolge (`SPEC-048`); ein Ausgabewert, dessen Typ oder Wert die Abbildung nicht schreibt, ist ein Typfehler (`SPEC-014`). ERROR und der Abschluss mit Fehlerstatus sind auch in der RPC-Antwort ein DONE (Setzung ohne Messung; die Messzeile R02 der Fehlermessung zeigt am echten Server für einen Fehler im Prozedur-Kontext ein DONEPROC mit Fehlerstatus, die Fehlerabbildung entscheidet, ob die RPC-Antwort so schreibt). Die Antwort auf einen SQL_BATCH kennt weder RETURNSTATUS noch RETURNVALUE noch DONEPROC.

#### Offene Punkte des Backend-Vertrags

- Unterscheidung statement- und batch-abbrechender Fehler im Strom, die `Failure` nicht trägt: der Sequencer schreibt eine Variante (ERROR und DONE mit Fehlerstatus, Lauf beendet); die Verbindungsfolge nach Severity und die Abbildung von `Failure.code` bleiben Sache der Fehlerabbildung.
- Herkunft der Befehlskennung (`curCmd`) der DONE-Tokens: der Sequencer schreibt 0; der Port trägt keine Kennung (Spec-Lücke).
- Verzögerung durch den zurückgehaltenen Abschluss der `DONE_MORE`-Vorschau: nicht gemessen.

#### Gemessen am Backend-Vertrag

Gemessen sind: Heap, erste Zeile, Durchsatz, langsamer Leser, Fehler nach der ersten Zeile, Mehrfach-Ergebnisse, Cancel, Threads bei 200 Strömen (Flow-Messungen mit suspendierendem Backend, Blockade nur beim Iterator). Im Spike gemessen (Nachmessung vom 2026-10-30, Grenzen wie oben bei „Pflichten des Backends“; Einzel- bis Vierfachläufe auf einem ausgelasteten Rechner): Bytes je Zeile und Zeilen bis zum `OutOfMemoryError` bei 256 MiB Heap (`List<Any?>`: 173 Byte je schmale und 454 je breite Zeile, 1,54 Mio schmale Zeilen), der `OutOfMemoryError` ohne und mit Fangen (Hänger), ein blockierendes Backend hinter dem `Flow` (Threads, Wanddauer, Abbruch) und die Frist. Nicht gemessen: echte Query-Engine oder Treiber, breitere Zeilen, LOBs und PLP, OUTPUT-Parameter im Strom, Antworten eines echten SQL Servers, MARS, Transaktionen, andere Clients.

### Sicherheitsprofil (Vertrag zu `SPEC-023`)

TLS-Modi des Servers (`SPEC-010`, `SPEC-028`, `SPEC-043`) für das Clientprofil (`SPEC-021`).

| Modus | Konfiguration | PRELOGIN-Antwort | Verhalten |
|---|---|---|---|
| `REQUIRED` (Standard), TDS 7.4 | `ENCRYPTION_POLICY=REQUIRED` | ENCRYPT_REQ oder ENCRYPT_ON (beide gemessen) | TLS-Handshake in TDS-Paketen vom Typ `0x12`, danach verschlüsseltes LOGIN7 |
| `REQUIRED`, TDS 8.0 | `ENCRYPTION_POLICY=REQUIRED`, Client `Encrypt=Strict` | — (TLS vor PRELOGIN) | TLS 1.3 ab dem ersten Byte, ALPN `tds/8.0` |
| unverschlüsselter Testmodus | ausdrückliche Konfiguration, im Log sichtbar, wenn eine Senke übergeben ist | ENCRYPT_NOT_SUP | nur TDS 7.4; Clients mit `Encrypt=false` melden sich unverschlüsselt an, Clients mit `Encrypt=true` brechen ab |

#### Zertifikate des Sicherheitsprofils

- Der Client prüft die Kette: ein nicht vertrautes Zertifikat scheitert mit `UntrustedRoot`.
- `Encrypt=Strict` ignoriert `TrustServerCertificate`; das Zertifikat muss dem Client vertraut sein, der Name muss passen.
- `TrustServerCertificate=true` genügt im TDS-7.4-Pfad, auch bei einem nicht vertrauten Zertifikat.
- Tests nutzen ein Testzertifikat im Trust-Store des Testcontainers.

#### Protokollversionen und Suiten des Sicherheitsprofils

| Version | Suiten |
|---|---|
| TLS 1.3 | `TLS_AES_256_GCM_SHA384`, `TLS_AES_128_GCM_SHA256` |
| TLS 1.2 | `TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384`, `TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256` |

Mindestversion ist TLS 1.2. TLS 1.1 und älter, CBC-Suiten, RSA-Schlüsselaustausch ohne Forward Secrecy sowie 3DES und RC4 sind nicht zugelassen. Die Startprobe (`SPEC-043`) nutzt genau diese Konfiguration.

Die Obergrenze gilt je Pfad: Der TDS-7.4-Pfad (Handschlag in Paketen vom Typ `0x12`) bietet höchstens TLS 1.2 an, der TDS-8.0-Pfad (`Encrypt=Strict`) alle konfigurierten Versionen, also TLS 1.3. Sie ändert keinen Standard der Konfiguration (`TlsParameters`) und fügt keinen Schlüssel hinzu; bleibt im 7.4-Pfad keine Version übrig, scheitert der Handschlag. Der Server nimmt keine Anmeldung ohne das `Finished` des Clients an (Setzung).

Die Bibliothek verlangt keine JVM-Option und liest keine Systemeigenschaft. Der JDK-Server sendet im TLS-1.3-Pfad nach dem Handschlag sein NewSessionTicket; im 8.0-Pfad ist es ein TLS-Record wie jeder andere, der Strom ist roh TLS ab dem ersten Byte. Gemessen ist: Ein Server, dessen Ticket-Zahl 0 ist, kündigt im 7.4-Pfad unter TLS 1.2 die Session-Ticket-Erweiterung weiter an und sendet kein Ticket, und `Microsoft.Data.SqlClient` 7.1.1 bricht den Handschlag ab („unexpected message“; ein Host, ein Lauf je Zelle). Ein Einbettender setzt deshalb die Ticket-Zahl der JDK-Systemeigenschaften `jdk.tls.server.*` nicht auf 0. Ein Server ohne die Session-Ticket-Erweiterung war im 7.4-Pfad mit demselben Client grün; andere Werte der Ticket-Zahl sind nicht gemessen. Das ist keine Prüfung der Bibliothek, sondern eine Pflicht des Einbettenden, die der Server nicht erkennt.

#### Festlegungen der TLS-Schleife

Festlegungen dieses Profils, nicht am Referenzclient gemessen (die Tests laufen gegen eine JDK-`SSLEngine` im selben Prozess):

- Im TDS-8.0-Pfad weist der Server eine Gegenstelle ab, die `tds/8.0` nicht aushandelt (kein ALPN oder ein fremdes); es entsteht kein Kanal.
- Im TDS-7.4-Pfad verlangt der Server für Handshake-Bytes den Pakettyp `0x12`; ein anderer Typ, ein Header außerhalb des Katalogs oder eine Länge unter dem Header beendet den Handshake.
- Endet der Strom nach dem Handshake ohne `close_notify`, ist das ein Fehler, kein sauberes Ende.
- Fehlertexte der Schleife nennen den Ausnahmetyp oder eine feste Beschreibung, nie Zertifikats- oder Schlüsselinhalt.
- Ein KeyUpdate der Gegenstelle in TLS 1.3 beantwortet die Schleife (mit einer JDK-Gegenstelle getestet: der Server schreibt einen kurzen Record, ohne selbst Nutzdaten zu schreiben, und die Nachrichten laufen weiter; der Inhalt der Antwort ist nicht beobachtet); eine Neuverhandlung in TLS 1.2 durch die Gegenstelle beantwortet die Schleife (JDK-Voreinstellung; mit einer JDK-Gegenstelle dreimal hintereinander getestet: Nutzdaten laufen danach in beide Richtungen unversehrt, und die Marke des Servers erscheint nicht im Klartext in seinem Ausgang); eine Begrenzung der Neuverhandlungen gibt es nicht, die Rechenlast vieler Neuverhandlungen ist nicht gemessen.

#### Schlüsselmaterial des Servers

Setzung dieses Profils, nicht gemessen und nicht aus dem Referenzclient abgeleitet:

- Schlüssel und Zertifikatskette des Servers kommen aus einem PKCS#12-Speicher; ein PEM-Lader ist nicht Teil des Servers.
- Speicher- und Schlüsselkennwort sind derselbe Wert; der Server nimmt den ersten Schlüsseleintrag.
- Zugelassen sind RSA-Schlüssel ab 2048 Bit; andere Arten und kürzere Schlüssel weist der Start ab (`SPEC-043`).
- Die Startprobe prüft jedes Zertifikat der Kette auf Gültigkeit zum Startzeitpunkt, nicht Vertrauen oder Namen.
- Die Bibliothek nimmt Speicher und Kennwort als Inhalt in `ServerConfig` (`TlsKeyMaterial`); der Testserver nennt den Speicher mit `TLS_KEYSTORE_FILE` und das Kennwort als Datei mit `TLS_KEYSTORE_PASSWORD_FILE` (beides Umgebungsvariablen des Testservers, ein Zeilenende am Ende der Kennwortdatei wird entfernt); `ENCRYPTION_POLICY=REQUIRED` verlangt beide, `TEST_UNENCRYPTED` lässt sie nicht zu. Der Handler verdrahtet beide Pfade am selben Port, ohne Konfigurationsschalter, und wählt je Verbindung am ersten Byte (Setzung, nicht gemessen): das Byte `0x16` (TLS-Handshake-Record, kein TDS-Pakettyp) wählt den Pfad ab dem ersten Byte (TDS 8.0, ALPN `tds/8.0`, `CONNECTED → TLS_NEGOTIATION → PRELOGIN`), jedes andere Byte den Pfad TDS 7.4 (gekapselt in Paketen vom Typ `0x12`). Der Pfad ab dem ersten Byte besteht nur mit der Richtlinie `REQUIRED`; im Testmodus trennt der Server ein erstes Byte `0x16` ohne Antwort. Ein Klartext-Anfang hinter dem ersten Byte `0x16` und eine Gegenstelle ohne ALPN `tds/8.0` enden ohne TDS-Antwort (ob die TLS-Engine vorher einen Alert sendet, ist nicht beobachtet). Belegt ist der Pfad gegen eine JDK-Gegenstelle und Rohbytes, nicht gegen `Microsoft.Data.SqlClient`.

#### Gemessen am Sicherheitsprofil

TLS 1.3 mit `TLS_AES_256_GCM_SHA384` im TDS-8.0-Pfad (`Microsoft.Data.SqlClient` 7.1.1, .NET 10); der TDS-7.4-Pfad bietet kein TLS 1.3 an (unten). Der unverschlüsselte Testmodus ist nur mit dem Referenzclient und `Encrypt=false` gemessen.

Mit `Microsoft.Data.SqlClient` 7.1.1 gegen einen Testserver mit RSA-Zertifikat in beiden Pfaden (TDS 7.4 im Tunnel und `Encrypt=Strict`):

- nur TLS 1.2 mit `TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384` und `…_AES_128_GCM_SHA256`: Handshake und LOGIN7 gelingen, der Client wählt die 256-Bit-Suite, wenn beide angeboten sind;
- nur TLS 1.2 mit der 128-Bit-Suite: gelingt;
- nur TLS 1.3 mit `TLS_AES_128_GCM_SHA256`: gelingt im Pfad `Encrypt=Strict`;
- nur TLS 1.1: der Client lehnt ab („tlsv1 alert protocol version“).

Entscheidung des Nutzers (2026-11-05): Der TDS-7.4-Pfad bietet höchstens TLS 1.2 an; der TDS-8.0-Pfad (`Encrypt=Strict`) bleibt bei TLS 1.3. Begründung, nach Microsoft Learn (laut Suchergebnis, nicht selbst am Server gemessen): Ein echter SQL Server verhandelt im TDS-7.x-Pfad höchstens TLS 1.2, TLS 1.3 gibt es nur mit TDS 8.0. Gemessen (Rohbytes-Mitschnitt, ein Host, ein Lauf) mit `mssql-jdbc` 13.4.0 (JDK 21, `encrypt=true`) im 7.4-Pfad unter TLS 1.3: Der Treiber sendet sein `Finished` nicht an den Server und schickt LOGIN7 gleich als rohen TLS-Record ohne TDS-Kopf; der Server trennt und meldet `PROTOCOL_VIOLATION`. Mit der Obergrenze TLS 1.2 gelingt die Anmeldung. Die Obergrenze trägt der Test `TlsHandshakeNegativeTest`: ein reiner TLS-1.3-Client scheitert im 7.4-Pfad mit `HANDSHAKE_FAILED`.

Gemessen an dem Stand, der keine JVM-Option verlangt (ein Host, ein Lauf je Aufruf): `Microsoft.Data.SqlClient` 7.1.1 besteht die Kategorie der Anmelde-Tests des Referenzclients mit 19 von 19 (17 im 7.4-Pfad unter TLS 1.2, 2 mit `Encrypt=Strict`), und `mssql-jdbc` 13.4.0 mit `encrypt=true` meldet sich im Treiber-Standard an. Nicht gemessen: die Wiederaufnahme einer Sitzung mit dem Ticket in beiden Pfaden, andere Treiber und Treiberversionen, ein echter SQL Server.

Nicht gemessen: ECDSA-Zertifikate, `TLS_CHACHA20_POLY1305_SHA256`, andere Clients.

#### Offene Punkte des Sicherheitsprofils

Das Verhalten von `HostNameInCertificate` im TDS-7.4-Pfad; ENCRYPT_ON gegen einen Client mit `Encrypt=false`.

### Authentifizierungsadapter (Vertrag zu `SPEC-051`)

Vertrag zwischen Anmeldepfad und Benutzerverwaltung (`TDS-FA-02.a`, `SPEC-012`). Der Anmeldepfad entschleiert LOGIN7 und ruft den Adapter; der Adapter entscheidet allein über Annahme oder Ablehnung. Gemessen sind die Eingaben des Referenzclients (`Microsoft.Data.SqlClient` 7.1.1, .NET 10), nicht der Kotlin-Vertrag selbst; der Verschlüsselungsmodus als Eingabe bleibt ein Entwurf. Die Kotlin-Signatur steht im Abschnitt „Kotlin-Signatur des Authentifizierungsadapters“.

#### Eingabe des Authentifizierungsadapters

| Feld | Inhalt | Grenzen (gemessen) |
|---|---|---|
| Benutzername (in der Spezifikation und in den Messungen „Kennung“) | UTF-16, nach LOGIN7 `UserName` | 0 bis 128 UTF-16-Einheiten, auch leer; der Client wirft vor dem Verbindungsaufbau, wenn sie länger ist (128 und 129 nur mit ASCII gemessen) |
| Passwort | UTF-16, entschleiert (`Password`, je Byte XOR `0xA5`, dann Nibble-Tausch) | 0 bis 128 UTF-16-Einheiten, auch leer (128 und 129 nur mit ASCII gemessen; Unicode, Sonderzeichen und Surrogatpaar nur mit kürzeren Passwörtern, das mit Nicht-BMP-Zeichen hat 15 Einheiten). Entwurf: ein Zeichenfeld, das der Anmeldepfad nach dem Aufruf überschreibt; erfasst ist nur dieses Feld, nicht der Paket- oder TLS-Puffer |
| Verbindungskontext | Datenbank, Anwendungsname, Rechnername, Intent | Werte aus LOGIN7; der Rechnername ist vom Client frei gesetzt und kein Beleg einer Herkunft |
| Verschlüsselungsmodus | Entwurf: der ausgehandelte Modus aus PRELOGIN, nicht aus LOGIN7 | nicht gemessen |
| Authentifizierungsmerkmale | `fIntSecurity` (SSPI-Block), FeatureExt-Option `0x02`, `fChangePassword` mit neuem Passwort | am LOGIN7 erkennbar |

Nicht unterstützte Modi (SSPI/Kerberos, Entra-ID-Token) erkennt der Anmeldepfad an den Merkmalen und lehnt sie ab, ohne den Adapter zu rufen. Ein Passwortwechsel ist in der ersten Version nicht Teil des Adaptervertrags; er wird abgelehnt.

#### Ausgabe des Authentifizierungsadapters

| Wert | Bedeutung |
|---|---|
| `Accepted(principal)` | Anmeldung gelungen; der Principal geht in die Session |
| `Rejected(reason)` | abgelehnt; `reason` dient nur dem Server-Log |

Der Adapter liefert keinen Text und keine Nummer für den Client. Die Antwort an den Client ist ein festes Muster des Anmeldepfads (ERROR 18456, einheitlicher State, Class 14, feste Meldung ohne Benutzernamen, danach DONE mit Fehlerstatus; `SPEC-012`). Danach schließt der Server die Verbindung; ein weiteres LOGIN7 wird nicht angenommen (Setzung, nicht gemessen: am echten SQL Server wurde nur die Fehlerantwort einmal beobachtet). Ein Ausnahmefall im Adapter wird wie `Rejected` beantwortet und als interner Fehler geloggt (`SPEC-048`).

#### Pflichten des Authentifizierungsadapters

- Das Passwort erscheint in keinem Log und in keiner Fehlermeldung, auch nicht gekürzt oder verschleiert. Der Benutzername erscheint in keiner Antwort an den Client und im Log nur im Feld `principal` des Ereignisses „Anmeldung angenommen“ (gebaut, Abschnitt 4; `TDS-FA-09.a`); abgelehnte Anmeldungen tragen kein Feld mit der Kennung. `Rejected(reason)` trägt den Grund ohne Passwort und ohne Benutzernamen.
- Anforderung an den Produktivcode der Einstiegsimplementierung: Der Passwortvergleich läuft in konstanter Zeit über die Länge hinaus; bei unbekannter Kennung läuft ein Scheinvergleich. Gebaut und strukturell belegt (Abschnitt „Kotlin-Signatur des Authentifizierungsadapters“, Bauart des Vergleichs); Gemessen (Test mit echter Ableitung, Median von 15 Aufrufen, im Docker-Image des Repos; Dauer je Aufruf bei 210 000 Iterationen auf zwei Hosts 42 bis 64 ms, die Spanne gilt nur für diese Hosts): Bei einer Liste mit gleicher Iterationszahl brauchen bekannter Benutzername mit falschem Passwort, unbekannter Benutzername und bekannter Benutzername mit richtigem Passwort innerhalb einer Toleranz von 25 % gleich lang. Bei einer gemischten Liste (niedrigere Iterationszahl oder `plain` neben `pbkdf2-sha256`) trennt die Zeit bekannte von unbekannten Benutzernamen; das ist eine Grenze, keine Zusage. Ein Adapter mit eigener Benutzerverwaltung unterliegt derselben Anforderung, der Vertrag kann sie nicht erzwingen.
- Die Antwort an den Client ist für unbekannten Benutzernamen und falsches Passwort in den Antwortbytes gleich (gemessen am Spike-Testserver, nicht am Produkt); das ist Sache des festen Antwortmusters des Anmeldepfads, nicht des Adapters.
- Ein Adapter blockiert den Anmeldepfad nicht länger als `LOGIN_TIMEOUT` (`SPEC-008`).

#### Einstiegsimplementierung des Authentifizierungsadapters

Feste Benutzerliste aus der Konfiguration (Benutzername, Passwortprüfwert). Die Liste wird beim Start validiert (`TDS-FA-09.a`); eine fehlerhafte Liste bricht den Start ab. Für Test und kleine Betriebe gedacht.

Prüfwertformat und Konfigurationsweg (vom Nutzer entschieden, 2026-10-30; was unter „Offen“ steht, ist nicht entschieden):

- **Verfahrenskennung:** Jeder Eintrag trägt vor dem Prüfwert eine Verfahrenskennung, `{kennung}Wert`. Ein Eintrag ohne Verfahrenskennung und eine unbekannte Verfahrenskennung brechen den Start ab (Setzung, nicht gemessen). Die Verfahrenskennung ist case-sensitiv und klein: `plain`, `pbkdf2-sha256`; `{PBKDF2-SHA256}` wird abgewiesen.
- **Verfahren:** `plain` nur bei ausdrücklich eingeschaltetem Testmodus, sonst Startabbruch vor dem Binden des Ports; ein leerer `plain`-Wert ist zulässig (nur im Testmodus). `pbkdf2-sha256` (`PBKDF2WithHmacSHA256` des JDK, keine Fremdbibliothek), Wert `Salz$Iterationen$Hash`; Salz und Hash sind Standard-Base64 mit verlangtem Padding.
- **Iterationszahl (Setzung):** Boden 210 000, Standard 600 000, Obergrenze 10 000 000; der Betreiber unterschreitet den Boden nicht. Gemessen (nur in einem Spike-Image auf einem Host, kein Produktivwert) ist die Dauer je Ableitung: 54 ms bei 210 000, 125 ms bei 600 000, 2,1 s bei 10 000 000. Die Werte für Boden, Standard und Obergrenze sind gesetzt, nicht gemessen. Die Obergrenze schützt nicht vor gleichzeitiger Last: 64 Threads mit je 3 Ableitungen (192 Ableitungen) mit 600 000 und `--cpus=1` erreichten im Spike als längste Dauer einer Ableitung 23,6 s; das ist die Dauer der letzten Ableitung unter dieser Last, nicht die Wartezeit einer einzelnen Anmeldung. Die Iterationen bestehen nur aus ASCII-Ziffern (keine Vorzeichen, keine anderen Ziffernschriften).
- **Passwort-Randfälle:** Die Ableitung nimmt die UTF-8-Bytes des vom Client gelieferten Passworts (UTF-16 nach UTF-8 wie im JDK). Ein alleinstehendes Hochsurrogat ergibt denselben Hash wie `?` (gemessen); das ist eine bekannte Eigenschaft. Es gibt keine Unicode-Normalisierung (NFC und NFD ergeben verschiedene Hashes, gemessen). Ein leeres Passwort ist zulässig und wird abgeleitet.
- **Konfigurationsweg:** eigene Datei nur für die Benutzerliste, eigenes Zeilenformat, eigener Parser. Die Bibliothek nimmt die geprüfte Liste (`UserList` in `AuthSettings`); Datei und Umgebungsvariablen sind Eingänge des Testservers: Der Pfad wird in der Umgebungsvariable `AUTH_USERS_FILE` übergeben, wie der Schalter `AUTH_PLAIN_TESTMODE` (Setzung (Voreinstellung 2026-10-30, nicht gemessen)); gemessen ist nur die Übergabe eines Werts per Umgebungsvariable, nicht die eines Pfades. Eine Zeile je Eintrag, `Benutzername<TAB>Prüfwert`, UTF-8, kein Escaping, Aufteilung am ersten TAB. Benutzernamen mit TAB, LF, CR oder alleinstehendem Hochsurrogat sind nicht darstellbar und ein Ladefehler. Ein doppelter Benutzername bricht den Start ab.
- **Testmodus-Schalter:** `AUTH_PLAIN_TESTMODE` (Umgebungsvariable des Testservers), gilt nur bei genau `true`; eigener Schalter nur für `plain`; der Start nennt den Modus an die Senke (Ereignis des aktiven Klartext-Testmodus, Abschnitt 4; gebaut).
- **Fehlerwerte:** je Abweichung genau eine Fehlerart mit Listenplatz, ohne Prüfwert und ohne Passwort. Der Listenplatz ist die Zeilennummer der Datei, 1-basiert (Setzung (Voreinstellung 2026-10-30, nicht gemessen)); gemessen ist im Spike die Position nach Sortierung, nicht die Zeilennummer.
- **Entschieden** (Nutzer, 2026-10-30; Setzungen, nicht gemessen): derselbe Iterationsboden im Testmodus, keine Kommentare und keine Leerzeilen, CRLF nicht toleriert, Hash genau 32 Byte und Salz mindestens 16 Byte ohne Obergrenze, Ziffernfolge der Iterationen 1 bis 8 Stellen ohne führende Null.
- **Offen** (vom Nutzer nicht entschieden, nicht gemessen): Dateirechte und Übergabe der Datei im Betrieb; weder der Lader noch der Start prüfen sie.
- **Lesen der Datei** (Lader des Testservers; Setzung, nicht gemessen): Die Datei wird als UTF-8 gelesen, ungültiges UTF-8 ist ein Startfehler (`NOT_UTF8`). Ein UTF-8-BOM am Anfang wird abgewiesen (`BYTE_ORDER_MARK`), nicht entfernt; er würde Teil des ersten Benutzernamens. Die Zeilenenden werden nicht normalisiert, ein CR erreicht den Parser und ist dort ein Fehler. `AUTH_PLAIN_TESTMODE` schaltet nur bei genau `true` ein; fehlt der Schlüssel oder ist er leer oder `false`, ist `plain` verboten, jeder andere Wert (auch `TRUE`) ist ein Startfehler und schaltet nie ein. Die Diagnose nennt Schlüssel, Art und bei der Liste Fehlerart und Zeile, nie Pfad, Wert, Benutzername oder Prüfwert.

#### Kotlin-Signatur des Authentifizierungsadapters

Package `io.github.pt9912.tdswire.config.auth` (Schicht `config`); der Principal liegt in `io.github.pt9912.tdswire.domain`. Die Signatur ist eine Festlegung ohne Messung am Referenzclient oder an einem echten SQL Server:

```kotlin
interface AuthAdapter { fun authenticate(request: AuthRequest): AuthResult }

class AuthRequest(
    val userName: String, val password: PasswordChars, val context: ConnectionContext,
    val encryption: NegotiatedEncryption, val features: AuthFeatures,
)
sealed interface AuthResult {
    class Accepted(val principal: Principal) : AuthResult
    data class Rejected(val reason: RejectReason) : AuthResult   // UNKNOWN_USER, WRONG_PASSWORD, INPUT_TOO_LONG
}
```

- Der Aufruf **blockiert** und ist nicht `suspend`. Der Aufrufer hält den Transport-Thread frei und `LOGIN_TIMEOUT` ein (`SPEC-008`); eine Messung zur Wirkung einer blockierenden Ableitung auf den Transport liegt nicht vor.
- `PasswordChars` hält die UTF-16-Einheiten als Kopie; `reveal()` gibt eine Kopie, `wipe()` überschreibt. Das schützt nur dieses Feld, nicht den Paket-, Lese- oder TLS-Puffer und nicht jede Kopie der JVM. Der Anmeldepfad überschreibt sein Feld nach dem Aufruf und beim Abbilden aus einem anderen Typ (`SecretChars`) auch das Zwischenfeld. Der Adapter überschreibt das an die Ableitung übergebene Zeichenfeld, die abgeleiteten Bytes und den UTF-8-Kandidaten des `plain`-Pfads (je mit Test); `PBEKeySpec.clearPassword()` und die Nullung des UTF-8-Zwischenpuffers haben keinen Test, und das `SecretKey`-Objekt der JDK-Ableitung wird nicht überschrieben (aus dem Code gelesen, nicht gemessen).
- Keine Textdarstellung von `AuthRequest`, `PasswordChars`, `ConnectionContext`, `Principal`, `Accepted`, der Benutzerliste und ihrer Einträge nennt Passwort, Benutzernamen oder Prüfwert.
- Eingaben über 128 UTF-16-Einheiten (Benutzername oder Passwort) ergeben `Rejected(INPUT_TOO_LONG)`, ohne Ausnahme und ohne Ableitung.
- `AuthFeatures` wertet die Einstiegsimplementierung nicht aus; der Anmeldepfad lehnt die nicht unterstützten Modi vor dem Adapter ab.

Validierung der Liste: `UserList.parse(text, plainTestMode)` liefert `Valid` oder `Invalid(UserListError(kind, line))`; `line` ist die 1-basierte Zeilennummer der Datei, bei einer Liste ohne Zeile `null`. Das Lesen der Datei, `AUTH_USERS_FILE` und das Auswerten von `AUTH_PLAIN_TESTMODE` (nur der Wert `true` schaltet ein, `UserList.plainTestModeFrom`) gehören dem Testserver. Die Fehlerarten sind `UserListErrorKind`.

Entschieden (Nutzer, 2026-10-30), Setzungen, nicht gemessen, jeweils im KDoc von `UserList` und `Pbkdf2Limits` gekennzeichnet:

- Der Iterationsboden 210 000 gilt auch im Testmodus.
- Keine Kommentare: eine Zeile mit `#` ist ein gewöhnlicher Eintrag. Eine leere Zeile ist ein Fehler; nur das Zeilenende am Dateiende ist erlaubt.
- Zeilenende ist LF; ein CR in einer Zeile (auch CRLF) ist ein Fehler.
- Der Hash hat genau 32 Byte, das Salz mindestens 16 Byte.
- Die Iterationen sind eine Ziffernfolge von 1 bis 8 Stellen ohne führende Null.
- Der erste Fehler in Dateireihenfolge bricht die Prüfung ab.
- Benutzernamen werden zeichengenau verglichen (keine Groß-/Kleinschreibung-Faltung, keine Normalisierung).
- Dateirechte und Übergabe der Datei sind weder geprüft noch entschieden.

Bauart des Vergleichs (Anforderung, strukturell belegt, Zeit gemessen, siehe unten): Der Vergleich untersucht jede Position bis zur längeren Länge und bricht nicht beim ersten Unterschied ab. Bei unbekanntem Benutzernamen läuft dieselbe Ableitung wie für die Attrappe der Liste gegen deren Prüfwert (Scheinvergleich); das Ergebnis wird verworfen. Attrappe ist der `pbkdf2`-Eintrag mit der höchsten Iterationszahl (bei Gleichstand der erste in Dateireihenfolge), Salz- und Hashlänge wie dort; hat die Liste nur `plain`-Einträge, ist es der erste Eintrag und es läuft keine Ableitung, wie für jeden bekannten Benutzer. Verbleibende Grenze bei gemischten Listen: Ein bekannter Benutzer mit niedrigerer Iterationszahl oder mit `plain`-Prüfwert durchläuft einen anderen Rechenschritt als die Attrappe. Gemessen (Test mit echter Ableitung, Median von 15 Aufrufen, im Docker-Image des Repos; Dauer je Aufruf bei 210 000 Iterationen auf zwei Hosts 42 bis 64 ms, die Spanne gilt nur für diese Hosts): Bei einer Liste mit gleicher Iterationszahl brauchen bekannter Benutzername mit falschem Passwort, unbekannter Benutzername und bekannter Benutzername mit richtigem Passwort innerhalb einer Toleranz von 25 % gleich lang. Bei einer gemischten Liste (niedrigere Iterationszahl oder `plain` neben `pbkdf2-sha256`) trennt die Zeit bekannte von unbekannten Benutzernamen; das ist eine Grenze, keine Zusage.

#### Gemessen am Authentifizierungsadapter

Gemessen sind: LOGIN7-Felder, Entschleierung, Ablehnungsbytes, erkennbare Modi. Zu Prüfwert und Konfigurationsweg gemessen mit JDK 21 (Eclipse Adoptium 21.0.12.1) in einem Spike-Image auf einem Host (Intel Core i9-13900H): Ableitungsdauer je Iterationszahl, Kodierung des Passworts (wie UTF-8), Parser-Fälle, Sichtbarkeit des Prüfwerts je Weg; die Werte gelten nur für dieses Image und diese Hardware. Nicht gemessen: Dateirechte im Betrieb, Wirkung einer blockierenden Ableitung auf den Transport, Windows-SSPI (NTLM), Verschlüsselungsmodus als Adaptereingabe, TDS 8.0 mit SQL-Authentifizierung, LOGIN7 über mehrere Pakete, Pooling mit `ResetConnection`, andere Clients.

### Einstieg des Integrators (Vertrag zu `SPEC-059`)

Der Weg, auf dem ein Integrator den Server mit einem eigenen Backend oder eigenen Zeilenquellen aus seinem Programm startet und beendet (`TDS-FA-08.a`, `TDS-RB-04`, `TDS-FA-11.a`). Alles in diesem Abschnitt ist eine Festlegung, soweit nicht ausdrücklich ein Test genannt ist; gemessen ist nichts an einem fremden Programm, nur der Einbettungstest dieses Repos.

| Teil | Inhalt |
|---|---|
| `TdsServer.start(config, source, telemetry)` | suspendierend, Package `runtime`; liefert `StartOutcome`: `Running(server)` oder `Aborted(reason, issues)`. Der Start wirft keine Ausnahme für Konfigurations-, Tabellen-, TLS- oder Bindefehler. `start` nimmt als dritten Parameter `telemetry: TelemetrySink` entgegen, Voreinstellung `TelemetrySink.NONE` (Abschnitt 5). |
| `TdsServer` | `port` (der gebundene Port) und `suspend stop()`. `stop` beginnt das geordnete Beenden (`SPEC-044`) und kehrt zurück, wenn alle Verbindungen beendet sind. `stop` ist idempotent: der zweite Aufruf endet ohne Wirkung (Test: Einbettungstest, nacheinander aus einem Thread). Aufruf von mehreren Threads und `stop` während `start` sind nicht festgelegt. |
| `BackendSource` | `Custom(backend)`: ein `QueryBackend`, auch ein selbst gebautes `SimulationBackend(SimCatalog(tables))` (`SPEC-035`); `Simulation(tables)`: `SimTable`-Werte mit Zeilenquelle und Callbacks (`SPEC-035`); `None`: wie `Simulation` mit leerer Liste. `Custom` mit Tabellen ist nicht darstellbar. Die Bibliothek kennt keine Datei der Tabellenbeschreibung; den Weg über die Datei geht der Testserver (`SPEC-035`). |
| `StartOutcome.Aborted` | `reason` ist ein Text ohne Pfad, Ausnahmetext, Schlüsselmaterial und Kennwort; beim Bindefehler nennt er Adresse und Port; `issues` nennt bei ungültiger Konfiguration jeden Fehler mit Schlüssel und Art (`ConfigIssue`), sonst ist die Liste leer. Festlegung: `ConfigIssue` ist als Typ der Signatur Teil der Zusage und die Art (`IssueKind`) ebenso; der Wortlaut des Schlüssels (ein Text, der das Konfigurationsfeld nennt) ist keine Zusage, ein Einbettender verlässt sich nicht auf bestimmte Namen. Der gebaute Stand führt dafür die Namen der Umgebungsvariablen (`SPEC-005`, `SPEC-010`). Ein Abbruch bindet keinen Port und startet keinen Thread des Servers. |
| Prüfung | Der Start prüft die Konfiguration, gleich auf welchem Weg sie entstand (`ConfigValidation.check`): `REQUIRED` verlangt Schlüsselspeicher und Passwort, `TEST_UNENCRYPTED` lässt beide nicht zu (`SPEC-010`); Port 0 bis 65535 (`SPEC-005`), Anmeldefrist 1 bis 3600 s (`SPEC-008`), Frist einer Anfrage und Frist des Abbruchs je 1 bis 3600 s (`SPEC-009`, `SPEC-033`), Frist des geordneten Beendens 0 bis 3600 s (`SPEC-047`), Zahl der Verbindungen 1 bis 100000 (`SPEC-034`), Größe der PRELOGIN-Nachricht 512 bis 65536 Byte (`SPEC-046`), Bind-Adresse nicht leer. Die Voreinstellungen von `ListenSettings` (`MAX_CONNECTIONS` 1000), `LoginSettings`, `RequestSettings` (`ServerConfig.requests`: `QUERY_TIMEOUT` 30 s, `CANCEL_TIMEOUT` 5 s, `SHUTDOWN_GRACE` 10 s) und der Verschlüsselung sind die der Spezifikation; ein Einbettender ohne Angabe bekommt also nach 30 s `TDS_TIMEOUT` für eine Anfrage. Umgebung und Dateien liest nicht die Bibliothek, sondern der Testserver; er baut eine `ServerConfig` und ruft `TdsServer.start` wie ein Einbettender (`SPEC-052`). |
| Einmal-Konfiguration | Eine `ServerConfig` gilt für einen Start: sobald der Start das TLS-Material lädt, löscht er das Passwort im Schlüsselmaterial; bricht er vorher ab (Prüfung), bleibt es lesbar (Festlegung, getestet für Prüfung und Bindefehler). Wer neu startet, baut eine neue `ServerConfig`. |
| Nicht erreichbar | Der Authentifizierungsadapter lässt sich über den Einstieg nicht ersetzen. Der Einstieg schreibt nirgends hin; Log und Telemetrie gehen an die übergebene Senke (Abschnitt 5). |
| Abhängigkeiten | Jar und `lib/` liegen auf dem Klassenpfad des Einbettenden (`SPEC-052`); `lib/` führt keine YAML-Bibliothek, die Bibliothek braucht keine (Festlegung). Gemessen: `gradle.lockfile` der Bibliothek führt kein Jackson, die Sperre des Moduls `testserver/gradle.lockfile` führt es (einmalige Zählung am 2026-11-06, kein Gate hält sie), und der Einbettungstest (`make test`) startet und bedient den Server ohne Jackson im Klassenpfad. |
| Zusage | Bis zur ersten Version `1.0` ändert sich diese Fläche nur durch eine Änderung dieser Spezifikation (Setzung); ab `1.0` gilt semantische Versionierung. Gemeint ist: öffentlich ist genau diese Fläche, alles andere in der Bibliothek ist `internal`. Belegt ist davon nur, dass `explicitApi` (Modus `strict`) im Hauptmodul für jede Deklaration eine ausdrückliche Sichtbarkeit verlangt; es gilt nicht für das Source-Set `integrator` und nicht für das Modul `testserver`. Dass nur die Namen dieser Liste öffentlich sind, hält kein Sensor (Grenze, keine Zusage über die Gleichheit der Menge). Die Namen nach Package: `runtime`: `TdsServer`, `BackendSource` (`Custom`, `Simulation`, `None`), `StartOutcome` (`Running`, `Aborted`); `config`: `ConfigValidation` (Vorab-Prüfung einer `ServerConfig`), `ServerConfig`, `ListenSettings`, `LoginSettings`, `RequestSettings`, `EncryptionSetting`, `TlsKeyMaterial`, `AuthSettings`, `ConfigIssue`, `IssueKind`; `config.auth`: `PasswordChars` (Konstruktor, `reveal`), `UserList` (`parse`, `plainTestModeFrom`), `UserListResult` (`Valid`, `Invalid`), `UserListError`, `UserListErrorKind`; `port`: `QueryBackend`, `QueryExecution`; `domain`: `QueryRequest`, `QueryParameter`, `ParameterDirection`, `QueryEvent` mit seinen Fällen, `ResultColumn`, `SqlType` mit seinen Fällen, `Principal`; `domain.telemetry`: `TelemetrySink` (`accept`, `NONE`), `TelemetryEvent` (`at`, `level`, `connectionId`, `sessionId`, `clientProfile`) mit den Fällen `ServerStarted`, `ServerStopped`, `StartRefused`, `ConfigInvalid`, `TlsStartRejected`, `BindFailed`, `EncryptionWeakened`, `PlainTestModeActive`, `SimulationBackendReady`, `AcceptFailed`, `ConnectionFailed`, `LoginAccepted`, `LoginRejected`, `AdapterFailed`, `EncryptionNotOffered`, `TlsFirstNotOffered`, `TlsHandshakeFailed` und `RequestFailed`, `EventLevel`, `StartRefusal`, `LoginRejectionReason`, `TlsFirstRefusal` (die Typnamen sind festgelegt; ob die Felder der Fälle und die Konstanten der Aufzählungen Teil der Zusage sind, ist offen und eine Nutzerentscheidung; ein Sammler für Tests gehört nicht zur Fläche, jedes Source-Set bringt ihren eigenen mit); `simulation`: `SimTable`, `SimColumn`, `SimQuery`, `RowFilter`, `FilterTerm`, `RowSource`, `SimulationBackend(catalog: SimCatalog)` und `SimCatalog(tables: List<SimTable>)` mit `tables`. Für die Signaturen von `SimulationBackend` und `SimCatalog` (Konstruktoren, `tables`) gilt dieselbe Zusage: Änderung nur durch eine Änderung dieser Spezifikation, im Änderungsprotokoll genannt; das Verhalten des Backends legt `SPEC-035` fest, die Zusage der Namen erweitert es nicht. `SimCatalog.find` und `SimTable.column` sind `internal` (Festlegung: Aufrufer ist der Planer des Hauptmoduls). `Principal` steht in der Liste, ist heute aber nur über den nicht öffentlichen Authentifizierungsadapter erreichbar; ein Einbettender kann es über den Einstieg weder erzeugen noch lesen (Hinweis, keine Fähigkeit des Einstiegs). Authentifizierungsadapter (`AuthAdapter` und seine Typen) und Konfigurationsschlüssel (`ConfigKeys`) gehören nicht zur Fläche. Weitere Namen sind keine Zusage. |
| Nachweis | Ein Test in einem eigenen Source-Set, das gegen die Klassen des Hauptmoduls übersetzt und keinen `internal`-Namen sieht, startet den Server mit einem In-Memory-Backend und mit einer Zeilenquelle des Integrators und beantwortet Anfragen mit Rohbytes (`java.net`, kein Referenzclient); er läuft im selben Prozess in `make test`. Grenze: `internal` ist im Bytecode öffentlich, der Nachweis gilt für Kotlin-Quellen. |

## 7. Festlegungen der Harness-Werkzeuge

| ID | Werkzeug | Festlegung |
|---|---|---|
| `SPEC-024` | Unit-Test-Gate | Unit-Tests für Decoder, Zustandsautomat, Encoder und Fehlerabbildung müssen erfolgreich sein; Gate: `make test` |
| `SPEC-025` | Integrationstest (E2E-Durchgang) | Containerisierter Testserver + echter .NET-Referenzclient (`Microsoft.Data.SqlClient` 7.1.1, .NET 10) prüfen Login, SQL_BATCH, RPC, Typen, Fehler, Cancel; ohne lokale .NET-Installation und ohne Mount, Basis-Images per Digest, Pakete per Lock und Hash; Lauf: `make e2e` (Stufen 3 bis 7, kein Gate) |
| `SPEC-026` | `make gates` | Aggregator: führt alle eingerichteten Gates aus; neue Gates für Formatierung, statische Prüfung und Tests werden in ihn aufgenommen |
| `SPEC-027` | Fuzz-/Property-Tests | Defekte Paketlängen, Offsets, Fragmentierung und Typwerte dürfen keinen ungefangenen Prozessabsturz verursachen |
| `SPEC-040` | Simulations-Tests (`make simulations-integration` im E2E-Durchgang) | Unit-Tests der Anweisungserkennung (jede Form aus `SPEC-036` positiv und negativ, Grenzen bei Literalen, Bezeichnern und Kommentaren), Unit-Tests des Lesers der Tabellenbeschreibung (jedes gelesene Feld, jeder Ladefehler mit Pfad, Zeile und Spalte, ohne Inhalt in der Meldung) und Integrationstest mit dem .NET-Referenzclient (`make simulations-integration`, Stufe des E2E-Durchgangs `make e2e`, nicht von `make gates`): Auswahl mit Bedingung, leeres Ergebnis, Zeilenquelle über der Speichergrenze (Angabe des Testservers, Task `integrationsServer` in `testserver/build.gradle.kts`: Serverprozess mit 48 MiB Heap, Text von 64 000 000 Zeichen; `make kopplung-zahlen` hält die Zahlen in Gradle, Kotlin, .NET, hier und im README gleich), Fehler der Zeilenquelle mit Folgeabfrage und Cancel einer laufenden Anfrage (`SqlCommand.Cancel()` gegen eine Zeilenquelle ohne Ende, nach 50 gelesenen Zeilen) mit Folgeabfrage auf derselben Verbindung, die nur ihre Antwort erhält; der Lauf geht gegen den Testserver (`SPEC-052`) und belegt das Verhalten des Referenzclients gegen die Bibliothek, die er bindet, in TDS 7.4 ohne Verschlüsselung und ohne Pool, nicht gegen einen echten SQL Server. `CancellationToken`, Command-Timeout und der Ablauf von `QUERY_TIMEOUT` am Referenzclient gehören nicht zu diesem Lauf, sondern zu Stufe 7 (`make kompatibilitaet KATEGORIE=Synchronitaet`) |
| `SPEC-041` | Static-Analysis-Gate (`make detekt`, `make detekt-selbsttest`) | detekt in Docker, Konfiguration auf Standardbasis mit dem ktlint-Regelsatz (prüft, schreibt nichts um), `warningsAsErrors` und den Importregeln der Schichten, im Kern als Erlaubnisliste ohne Fremdbibliothek; dazu Technik-Regeln für Kern und Port, die Prozess, Dateisystem und Ähnliches über `ForbiddenMethodCall` sperren und deshalb nur über `detektMain` und `detektTest` wirken (Typauflösung); keine Baseline; jeder Befund bricht den Lauf. Tests des Kerns bleiben frei von Fremdbibliotheken (`kotlin.test` statt JUnit), weil die Kernregeln auch für Test-Quellen gelten. Die Importregeln lesen nur `import`-Zeilen: ein voll qualifizierter Name im Code und ein `typealias` darauf umgehen sie; das Gate belegt die Einhaltung der Importgrenze, nicht des Classpaths. `@Suppress` und `@SuppressWarnings` im Quelltext brechen den Lauf vor detekt (Textprüfung des Suppression-Gates). Ein Selbsttest belegt, dass jede Importregel ihre verbotenen Importe meldet, die erlaubten Testdaten nicht und das Suppression-Verbot jede Form meldet; er läuft nur bei geänderten Eingängen |
| `SPEC-042` | Build-Gate (`make build`) | Compile und Jar in Docker, ohne Tests (die Stage ruft `:assemble` des Wurzelprojekts, der Bibliothek); Basis-Images per Digest gepinnt, Abhängigkeitsversionen per Lock (je Gradle-Projekt eine Datei: `gradle.lockfile` der Bibliothek und `testserver/gradle.lockfile` des Moduls `testserver`) und Prüfsummen (Gradle-Verifikationsdatei) fixiert |
| `SPEC-052` | Bibliotheks-Artefakt und Lizenz (E2E-Durchgang: `make laufzeittest` mit dem Testserver; der Tag `@spec` nennt nur den Lizenztext) | Das Artefakt ist ein Bibliotheks-Jar `tdswire.jar` **ohne `Main-Class`** im Manifest; die Bibliothek ist das Produkt (`TDS-RB-01`), und es gibt weder eine Startklasse noch ein Betriebs-Image als Produkt. Die Bibliotheken, die die Bibliothek braucht, liegen im Verzeichnis `lib` daneben und stehen im Class-Path des Manifests (Komfort für Einbettende, Setzung). Die Lizenz ist die MIT-Lizenz (Datei `LICENSE` im Wurzelverzeichnis, Rechteinhaber `pt9912`, Jahr 2026, `TDS-RB-05`); sie liegt im Jar unter `META-INF/LICENSE`, und der Bau des Jars bricht, wenn sie dort fehlt oder vom Inhalt der Datei abweicht (läuft in `make build`). Im Bau (`make build`) prüft eine Textprüfung ohne Container, dass das Manifest des Jars keine `Main-Class` nennt, das Jar keine Klasse des Testservers und `lib` kein Jackson führt, der Class-Path nur Einträge `lib/<Name>` mit vorhandener Datei in `lib` führt und das Jar `tdswire.jar` heißt (nicht geprüft: der Start des Jars). Die Lizenzdatei prüft außerdem ein Unit-Test (MIT-Text, Rechteinhaber, Rechteeinräumung, Haftungsausschluss). **Testserver:** Ein ausführbarer Serverprozess, der nur der Prüfung der Bibliothek dient (E2E- und Kompatibilitätsläufe, kein Produkt), bindet die Bibliothek ausschließlich über `TdsServer.start` und ihre öffentliche Fläche (`SPEC-059`). Er ist ein eigenes Gradle-Modul `testserver` (Verzeichnis `testserver/` mit eigener `build.gradle.kts` und eigener `gradle.lockfile`), das die Bibliothek als Projekt-Abhängigkeit `project(":")` bindet; Jackson steht nur in seiner Sperre (einmalige Zählung am 2026-11-06, kein Gate hält sie). Gemessen (Messprotokoll vom 2026-11-06, M1 und M2): Ein Zugriff auf einen `internal`-Namen aus Kotlin-Quellen des Moduls bricht dessen Übersetzung, und der Laufzeit-Klassenpfad des Moduls trägt das Jar `tdswire.jar` (mit Manifest und Lizenz), nicht das Klassenverzeichnis; nicht gemessen sind Java-Quellen und Reflection (`internal` bleibt im Bytecode öffentlich). Er liest Konfiguration und Benutzerliste zur Laufzeit aus der Umgebung und aus Dateien (Eingänge des Testservers, `SPEC-005`, `SPEC-010`, `SPEC-051`), nie aus dem Abbild (Setzung),. **Log und Metriken des Testservers** (Zielzustand, nicht gebaut; Setzung, nicht gemessen, auch die Zahlen): Er übergibt `TdsServer.start` eine Senke mit einem eigenen Adapter (Abschnitte 4 und 5) und liest dafür `LOG_LEVEL` (`ERROR`, `WARN`, `INFO`, `DEBUG`; Voreinstellung `INFO`; ein anderer Wert bricht den Start des Testservers ab) und `METRICS_INTERVAL_SECONDS` (Quelle des Abstands der Schnappschüsse in der `ServerConfig`); beide Schlüssel sind Eingänge des Testservers, nicht der Bibliothek. Der Adapter schreibt je Ereignis eine JSON-Zeile auf die Standardausgabe mit den Feldern `ts`, `level`, `event` und denen des Ereignisses und filtert nach `LOG_LEVEL`. Er hält eine begrenzte Warteschlange (1024 Zeilen); bei Überlauf wird die neue Zeile verworfen und gezählt, die Zahl steht im Feld `dropped` der nächsten geschriebenen Zeile. Ereignisse vor dem erfolgreichen Start und die Diagnose eines abgebrochenen Starts schreibt er synchron und ohne Warteschlange; vor dem Prozessende leert er die Warteschlange mit einer Frist von 2 Sekunden, der Rest geht verloren und wird in einer letzten Zeile mit `dropped` gemeldet, soweit der Kanal noch annimmt. Das sind Aussagen über den Testserver, kein Vertrag der Bibliothek. Die Bereitschaftszeile bleibt Sache des Testservers und ist kein Ereignis des Modells. Sein Container-Abbild entsteht aus dem Klassenpfad des Moduls `testserver` (Setzung); ein Abbild als Betriebsweg gibt es nicht. `make laufzeittest` startet den Testserver als Container in einem internen Netzwerk, lässt einen Rohbytes-Client aus einem zweiten Container PRELOGIN und LOGIN7 sprechen und beendet den Container mit `docker stop`; die Ausgabe des Testservers nennt nie ein Passwort und einen Benutzer nur im Feld `principal` des Ereignisses „Anmeldung angenommen“ (Abschnitt 4). Gemessen am Server-Container vor dem Umbau, der den Server zum Testserver macht (2026-10-31): `docker stop` (SIGTERM) endet mit Exit-Code 143. Prüfungen, die ein Produkt-Image voraussetzen (Nutzer 65532, Inhalt von `/app`, Lizenz im Abbild, `JAVA_TOOL_OPTIONS`), gibt es nicht, weil es kein Produkt-Image gibt; die Lizenz im Jar prüft der Bau. Nicht belegt: Annahme durch `Microsoft.Data.SqlClient`, TLS im Container, Last, der Inhalt der Jars und der Basis-Schichten. `make laufzeittest` ist Stufe des E2E-Durchgangs `make e2e` und steht nicht in `make gates`. |
| `SPEC-050` | Referenzabgleich (vorläufig, E2E-Durchgang) | Live-Lauf und Mitschnitt-Lauf nach `SPEC-045` gegen eine SQL-Server-Referenzinstanz in Docker (eigener Container im eigenen Netzwerk, ohne Mount); Abweichungen nur über die Ausnahmeliste; ergänzt die Läufe von `SPEC-025` und `SPEC-032`, ersetzt sie nicht; Lauf des E2E-Durchgangs, noch einzurichten |
| `SPEC-032` | Kompatibilitätstest (E2E-Durchgang) | Echter .NET-SqlClient prüft LOGINACK/ENVCHANGE/DONE, TLS-Profil (TDS 7.4 und TDS 8.0), RPC-Parameter, Streaming-Fehler, ATTENTION und Wiederverwendung; ein Szenario ist rot, sobald der Client es nicht besteht; Lauf: `make e2e` (Stufen 4 bis 7, ebenso `make kompatibilitaet`, kein Gate) |

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
| 2026-10-30 | Verträge zu `SPEC-021`, `SPEC-022`, `SPEC-023` und `SPEC-051` in Abschnitt 6 übernommen; Spec-Lücke zu `SqlType` im Backend-Vertrag entfällt (`SPEC-002` definiert ihn); Zeilenformat der Benutzerliste in `SPEC-051` auf den Stand des Vertrags angeglichen |
| 2026-10-30 | Simulations-Backend nachgeführt (`SPEC-035`, `SPEC-036`, `SPEC-040`, `SPEC-045`): Ignorierliste um sieben Spaltenfelder, `tables` als Pflicht, Längengrenze der Typabbildung, Setzungsvermerke, Lesart der Liste „Nicht erkannt“ als Setzung, Tests des Lesers, gemeinsame Quelle der Schemata |
| 2026-10-30 | Anfragezustand im Verbindungsautomaten festgelegt: flacher Zuschnitt, Sitzungsrahmen statt Response Sequencer hält den Zustand, Ende der Antwort als ein Übergang (`SPEC-001`) |
| 2026-10-31 | Grenzen des Backend-Vertrags nach Nutzerentscheidungen: Klassen der Werte je Spaltenart, Pflicht „suspendierend“ für Backend und Zeilenquelle, Frist durch den Protokollkern mit `QUERY_TIMEOUT` als Quelle, `OutOfMemoryError` im Anfragepfad gefangen (`SPEC-002`, `SPEC-009`, `SPEC-016`, `SPEC-022`, `SPEC-031`, `SPEC-035`, `SPEC-048`); keine Grenze für „kleine“ Ergebnisse als Setzung; Messungen nur im Spike, nicht am Produkt |
| 2026-10-31 | Simulations-Backend, weitere Festlegungen von Scanner, Zuordnung und Leser der Tabellenbeschreibung nachgetragen (`SPEC-035`, `SPEC-036`) |
| 2026-10-31 | RPC-Kodierungsfehler schließen mit Protokollfehler (`SPEC-011`), nicht gebaute Formen bleiben `SPEC-013` bei offener Verbindung; Zählregel der Obergrenzen, Wertbereich, Quotierung im Backend-Vertrag, `@@` abgelehnt (Nutzerentscheidung; `TDS-FA-04.a`, `SPEC-011`, `SPEC-013`) |
| 2026-10-31 | RPC: `@@x` als Parametername nur beim RPC per Prozedurname abgelehnt, gültig kodierte PLP-Typen offen als `SPEC-013` statt Schließen, Strukturfehler schließen weiter (Nutzerentscheidung, nicht gemessen; `TDS-FA-04.a`, `SPEC-011`, `SPEC-013`) |
| 2026-10-31 | RPC im Backend-Vertrag: `sp_executesql` und RPC per Prozedurname als Text mit Parameterliste (Nutzerentscheidung), Ablehnungsgründe unter `SPEC-013`; kein Wert im Aufruftext (`TDS-FA-04.a`, `SPEC-013`, `SPEC-030`) |
| 2026-10-31 | Simulations-Backend, Fix-Runde der Prüfung: Wörter je Codepunkt, Namensvergleich und Pfadbereinigung als Festlegung, Grenzen des Modells (`SPEC-035`, `SPEC-036`) |
| 2026-10-31 | Simulations-Backend, Auswahl gebaut: Überführung von Literal und Parameter mit ihren Einzelfestlegungen, Vorfilter und Spalten der Zeilenquelle, Fehler der Zeilenquelle, Lesen auf Abnahme (`SPEC-035`, `TDS-FA-11.a`); Einfügen, Ändern, Löschen noch `TDS_UNSUPPORTED_SQL` |
| 2026-10-31 | Laufzeit-Artefakt und Lizenz festgelegt: Jar mit Startklasse, Laufzeit-Image ohne Root-Rechte, Lizenz in Jar und Image, Laufzeittest im E2E-Durchgang (`SPEC-052`, `TDS-RB-01`, `TDS-RB-05`) |
| 2026-10-31 | Drosselung wiederholter Anmeldeversuche je Quelladresse festgelegt: Staffel, Fenster, Tabellengrenze als Setzung ohne Lastenheft-Anker, nicht konfigurierbar (`SPEC-053`, `SPEC-012`) |
| 2026-10-31 | Prepared-Statements festgelegt: Handle-Verwaltung je Sitzung, Grenze von 1000 Handles als Setzung, Handle als RETURNVALUE, Fehler für unbekanntes Handle und erreichte Grenze (`TDS-FA-04.a`, `SPEC-030`, `SPEC-057`, `SPEC-058`) |
| 2026-10-31 | Simulations-Backend, Änderungen und Verdrahtung gebaut: Weitergabe von Einfügen, Ändern und Löschen an die Callbacks mit Reihenfolge der Prüfung und Fehlerabbildung, Katalog aus Datei und Tabellen des Integrators, Umgebungsvariable `SIMULATION_DESCRIPTION_FILE`, Größengrenze und Startabbruch (`SPEC-035`, `TDS-FA-11.a`) |
| 2026-10-31 | Einstieg des Integrators festgelegt: Fassade `TdsServer.start` mit Backend-Quelle, Prüfung der Konfiguration beim Start, Einmal-Konfiguration, Zusage der Fläche, Nachweis ohne `internal` (`SPEC-059`, `SPEC-035`, `TDS-FA-08.a`) |
| 2026-11-01 | Bibliothek als Produkt nachgezogen: Die Bibliothek ist das Produkt (Bibliotheks-Jar ohne Startklasse), Umgebungsvariablen, Dateien und die Tabellenbeschreibung als Datei gehören dem Testserver, die Läufe des E2E-Durchgangs gehen gegen den Testserver, `SimulationBackend` und `SimCatalog` sind verbindliche Namen mit Signaturzusage bis `1.0`, der Zwischenstand-Satz der Zusage entfällt, `ConfigKeys` ist nicht mehr zugesagt (die Schlüssel in `ConfigIssue` sind Text ohne zugesagten Wortlaut); was am Code noch nicht gilt, steht als Zielzustand an der Stelle (`SPEC-052`, `SPEC-040`, `SPEC-025`, `SPEC-005`, `SPEC-010`, `SPEC-023`, `SPEC-035`, `SPEC-051`, `SPEC-059`, `TDS-RB-01`, `TDS-QA-06`, `TDS-FA-08`, `TDS-FA-09`, `TDS-FA-11`) |
| 2026-11-01 | Testserver gebaut: Umgebung, Dateien, Tabellenbeschreibung und Jackson liegen im Testserver (eigenes Source-Set), `ServerConfig` führt keine Datei der Tabellenbeschreibung mehr; `make e2e` lief mit den sieben Stufen grün (Einzelmessung 2026-11-01, 312 s); die Zielzustand-Vermerke zu `SPEC-005`, `SPEC-010`, `SPEC-035`, `SPEC-040`, `SPEC-051` und `SPEC-059` entfallen, `SPEC-052` bleibt Zielzustand; `LOGIN_TIMEOUT` und `MAX_PRELOGIN_MESSAGE_BYTES` sind als Eingang des Testservers ausgezeichnet |
| 2026-11-01 | Log und Telemetrie festgelegt: Pflicht-Logfelder um `ts`, `level` und `principal` (nur bei angenommener Anmeldung) ergänzt, Quelle von `client_profile` und Herkunft der IDs, Parameterwerte nie, Redaktion durch Bauart, Zugang zu Metriken und Spans über die Senke der Bibliothek, `LOG_LEVEL`, `METRICS_INTERVAL_SECONDS` und das Format der JSON-Zeile als Aussagen über den Testserver, Adresse des Bindefehlers nur im Ereignis (`TDS-FA-09.a`, Abschnitte 4 und 5, `SPEC-052`) |
| 2026-11-01 | Öffentliche Fläche der Bibliothek festgelegt und verkleinert: `explicitApi` (Modus `strict`) ist eingeschaltet, `SPEC-059` nennt die endgültige Liste der öffentlichen Namen, alles andere ist `internal` (darunter `SimCatalog.find`, `SimTable.column`, die Typen der Authentifizierungsadapter und `ConfigKeys`); `SHUTDOWN_GRACE` und `MAX_CONNECTIONS` sind als Eingang des gebauten Stands nicht ausgezeichnet und stehen als Zielzustand, `QUERY_TIMEOUT` nennt `ServerConfig.requests` als Eingang (`SPEC-059`, `SPEC-047`, `SPEC-034`, `SPEC-009`) |
| 2026-11-01 | RESETCONNECTION festgelegt und gebaut: Das Statusbit im ersten Paket einer Anfrage in `IDLE` leert unter der Sperre der Sitzung vor der Verarbeitung die Handle-Tabelle, Zähler und Grenze beginnen neu (Setzung, nicht gemessen); am Referenzclient mit Pooling über seine Wirkung gemessen, die Antwort ohne ENVCHANGE nimmt er an (`SPEC-001`, `SPEC-030`, `SPEC-057`, `SPEC-004`) |
| 2026-11-01 | Bibliotheks-Jar ohne `Main-Class` gebaut: Das Manifest nennt keine Startklasse, die Prüfung im Bau meldet eine vorhandene `Main-Class`, eine Klasse des Testservers im Jar und Jackson in `lib/` als Befund, die Stages `runtime` und `build` (Distroless) entfallen, `make laufzeittest` prüft den Testserver-Container; die Prüfungen am Produkt-Image entfallen ohne Ersatz; der Zielzustand-Vermerk von `SPEC-052` zur Startklasse und zum Laufzeit-Image entfällt (`SPEC-052`, `TDS-RB-01`, `TDS-QA-06`) |
| 2026-11-01 | Log-Schnittstelle gebaut: Ereignismodell und Senke `TelemetrySink` im Package `domain.telemetry`, Parameter `telemetry` von `TdsServer.start` (Voreinstellung keine Senke), die Log-Anschlüsse der Bibliothek geben ihre Ereignisse an die Senke, `ServerLog` entfällt, die Bibliothek schreibt nirgends hin; die Zielzustand-Vermerke zu Modell, Senke, `principal`, `client_profile` und Redaktion entfallen, `request_id`, `duration_ms`, Schnappschuss und Span-Ende bleiben Zielzustand (`SPEC-059`, `SPEC-048`, `SPEC-043`, `TDS-FA-09.a`) |
| 2026-11-05 | Anmeldung und Export des Prüfclients d-migrate festgelegt: `sp_executesql` mit nur dem Text und Wertparameter ohne Namen als `@P<i>` (`SPEC-030`), Form C mit `SELECT 1` und `SET`-Anweisungen und der Konstantenterm `1 = 0` in S1 (`SPEC-036`), Verweis im Testprofil (`SPEC-004`); `TDS-FA-11`, `TDS-FA-12` |
| 2026-11-05 | Der TDS-7.4-Pfad bietet höchstens TLS 1.2 an, TDS 8.0 bleibt bei TLS 1.3 (Entscheidung des Nutzers); Messung mit `mssql-jdbc` 13.4.0 und die offene Messung mit `Microsoft.Data.SqlClient` im Sicherheitsprofil; kein Default ändert sich (`SPEC-023`, `SPEC-010`) |
| 2026-11-05 | Der Start verlangt keine JVM-Option mehr: Abschnitt „JVM-Flag“ des Sicherheitsprofils, die Prüfung im Einstieg, der Abbruchgrund „JVM-Flag fehlt“ von `StartRefused` und die Zeile „JVM-Flag“ der Festlegungen des Einstiegs entfallen, die Startprobe besteht mit dem NewSessionTicket des TLS-1.3-Servers, die offene Messung mit `Microsoft.Data.SqlClient` ist beantwortet (`SPEC-023`, `SPEC-043`, `SPEC-059`) |
| 2026-11-06 | Disconnect, geordnetes Beenden und `MAX_CONNECTIONS` gebaut, mit einer Ausnahme (nicht gebaut: Anweisungsgrenzen eines Batches nach dem Beginn, `SPEC-044` Nr. 3): Ende der Gegenstelle in jedem Anfragezustand schließt die Sitzung und bricht die Backendoperation einmal ab (`SPEC-029`); `TdsServer.stop` ist das geordnete Beenden mit `SHUTDOWN_GRACE` aus `ServerConfig.requests`, eine Verbindung mit unvollständiger Nachricht oder laufender Anfrage bleibt bis zum Abschluss oder Fristablauf offen, jede danach vollständig werdende Nachricht erhält `TDS_SHUTDOWN`, Abbruch wie bei ATTENTION nach der Frist, Trennen nach `CANCEL_TIMEOUT`; Herkunft des Schließens nach ERROR und DONE gekennzeichnet (`SPEC-044`, `SPEC-049`, `SPEC-047`); `MAX_CONNECTIONS` als Grenze des Listeners mit Eingang (`SPEC-034`, `SPEC-059`) |
| 2026-11-06 | Nutzer bestätigt die Festlegungen der Wertebereiche (`MAX_CONNECTIONS` 1 bis 100000, `SHUTDOWN_GRACE` 0 bis 3600) und der Abweisung ohne Antwortbytes (`SPEC-034`, `SPEC-047`); `SPEC-044` führt die Spannung zwischen Nr. 2 und Nr. 3 (ruhende Verbindung endet beim Beginn, `TDS_SHUTDOWN` erreicht sie nicht) und den Handshake als offene Frage, ohne die Zusage abzuschwächen |
| 2026-11-06 | Der Testserver ist ein eigenes Gradle-Modul `testserver` (Verzeichnis `testserver/`, vorher ein Verzeichnis unter `src`), das die Bibliothek als Projekt-Abhängigkeit bindet: eigene Sperre `testserver/gradle.lockfile` (Jackson nur dort), Nachweis „nur öffentliche Fläche“ durch die Übersetzung des Moduls statt durch einen Sensor am Source-Set, Sperre eines Eintrags `friend` oder `associate` in seiner Build-Datei (`SPEC-052`, `SPEC-040`, `SPEC-042`, `SPEC-059`) |
| 2026-11-06 | Nachzug der Spezifikation hinter den gebauten Stand nach einem Review der Spezifikation (Grundsatz des Nutzers: Spezifikation ist Zielbild; kein Zielbild verengt): Packet Size (Zeile „Datenbank und Packet Size“): „angewandt wird sie nicht“ entfällt, belegt ist die Anwendung ab Beginn der Anfrageschleife (`RequestLoop.kt`, `channel.usePacketSize(ready.session.packetSize)`; Test `StreamingDispatchTest` mit 512 und 1000), die Antworten vor der Schleife behalten 4096 (Lesen des Codes, nicht am Referenzclient gemessen); die Voreinstellung 4096 ist als Setzung gekennzeichnet (`TlsChannel.DEFAULT_PACKET_SIZE`, `LoginResponses.DEFAULT_PACKET_SIZE`); der Zielzustand-Vermerk zum Feld `principal` im Abschnitt zur Kennung im Anmeldepfad entfällt (gebaut: `LoginAccepted.principal` in `TelemetryEvent.kt`, Änderung vom 2026-11-01); `SPEC-025` und `SPEC-032` nennen `make e2e` statt „noch einzurichten“ (die Targets `e2e` und `kompatibilitaet` bestehen, Einzelmessung vom 2026-11-01: 103 s warm für `e2e`, in diesem Nachzug nicht wiederholt; `SPEC-050` bleibt Zielzustand); der Vermerk zur Meldung des Testservers beim Start nennt den Beleg des gebauten Stands (KDoc von `SimLoadException`); die Nutzerentscheidung vom 2026-10-31 zu `TDS-FA-06` (nicht synchroner Strom) ist am 2026-11-06 bestätigt („deine empfehlungen“), der Wortlaut bleibt (`SPEC-031`, `SPEC-048`, `TDS-FA-06`) |
