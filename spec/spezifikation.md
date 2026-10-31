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
passenden laufenden Anfragezustand zulässig (Zielbild, Stand der Festlegung unten). Der Zustand ist ein expliziter Automat: eine Aufzählung der
Zustände und eine reine Übergangsfunktion (Zustand, Ereignisart) → (Folgezustand, Aktion). Ungültige
Übergänge werden abgelehnt; die Antwort darauf (Verbindung ohne Antwort schließen oder ein Fehler), die
Reichweite von ATTENTION vor dem Login sind noch festzulegen. Der Zuschnitt der Aufzählung ist flach
(Setzung des Nutzers, nicht gemessen für den Anfragezustand): eine Aufzählung führt Verbindungs- und
Anfragezustände, `READY` ist der Anfragezustand `IDLE`.

Anfragezustand (nur nach dem Login): `IDLE → RUNNING → STREAMING → COMPLETED → IDLE`. Vorgesehen: Bei
ATTENTION führen `RUNNING` und `STREAMING` über `CANCELLING` nach `COMPLETED`. Der Response
Sequencer kennt den Anfragezustand nicht; der Sitzungsrahmen hält ihn und entscheidet jeden Übergang
atomar je Sitzung: Übergang und das zugehörige Schreiben laufen unter einer Sperre je Sitzung. Der Dispatch
meldet dem Rahmen den Beginn des ersten Ergebnisses (`RUNNING → STREAMING`) und das Ende der Antwort; das
Ende der Antwort sendet sie und führt in einem Übergang nach `IDLE` oder, wenn die Verbindungsfolge des
Ausgangs schließt, nach `CLOSED` (`COMPLETED` ist kein eigener Zustand der Aufzählung; Festlegung, nicht
gemessen). Eine Anfragenachricht (SQL_BATCH, RPC) in `RUNNING` oder `STREAMING` ist ein unzulässiger
Übergang. Der Dispatch nimmt kein ATTENTION an. Zielbild, nicht gebaut (der nächste Absatz nennt den
Stand): Der Transport meldet ein erkanntes ATTENTION-Paket an den Sitzungsrahmen, und bei gleichzeitigem
DONE und ATTENTION gewinnt der erste Übergang.

ATTENTION in jedem Anfragezustand ist offen; die Kette über `CANCELLING` ist dessen Zielbild. Gebaut ist sie nicht: Bis zu dieser Festlegung behandelt der Automat jede Nachricht in
`RUNNING` und `STREAMING`, auch ATTENTION, als unzulässigen Übergang, und `CANCELLING` ist kein Zustand
der Aufzählung (Festlegung, nicht gemessen). Ebenso gilt bis dahin als Festlegung, nicht als Zusage:
ATTENTION in `READY` (`IDLE`) bleibt unbeantwortet, und die Verbindung schließt. Die folgende Bestätigung
ist das Zielbild: Trifft ATTENTION nach `COMPLETED` oder in `IDLE` ein, bleibt das bereits gesendete
Ergebnis gültig, und der Server beantwortet das ATTENTION mit der vom Profil geforderten Bestätigung,
damit der Client nicht auf sie wartet (Setzung; am echten Server ist nur ATTENTION nach vollständig
gesendeter Antwort gemessen, im Leerlauf ohne laufende Anfrage nicht).

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
Voreinstellung 2026-10-30, nicht gemessen); `OutputParameter` bleibt bis zur Abbildung abgelehnt
(`SPEC-031`). Die Liste hat so viele Werte wie das offene Ergebnis Spalten; das Backend verändert sie
nach der Übergabe nicht und verwendet sie nicht wieder (Setzung, Voreinstellung 2026-10-30, nicht
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
Verbindungs-Pooling (einschließlich RESETCONNECTION-Statusbit der Paketheader) und Lastprofil.
Die unterstützten RPCs gibt das Clientprofil erst frei, wenn die Messgrenzen aus `SPEC-030` geklärt sind
(TDS 8.0, Pooling, echter Server, `SET`-Text bei SchemaOnly und KeyInfo, Transaktionen).
Die Authentifizierung ist SQL-Authentifizierung (Kennung und Passwort im LOGIN7, `SPEC-051`).
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
(`SPEC-021`); die Messgrenzen stehen unter der Tabelle. Die Zeilen "Pflichtverhalten"
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
sind ein Fall von `SPEC-048`; das gilt für jedes `Throwable` aus Aufruf und Strom des Backends außer der
Abbruchausnahme der Coroutine, also auch für ein `Error` (gebauter Stand des Sequenzierers). Ein
`OutOfMemoryError` beim Erzeugen oder Senden der Antwort einer Anfrage (Backend, Encoder, Senke) wird
gefangen und ist ebenfalls ein Fall von `SPEC-048` (Nutzerentscheidung vom 2026-10-31): ERROR und DONE mit
Fehlerstatus, solange der Strom synchron ist, danach schließt die Verbindung; sonst schließt sie ohne ERROR
und DONE. Die Zusage der Negative aus `TDS-FA-06` gilt hierfür als erfüllt (Abnahme-Entscheidung des Nutzers vom 2026-10-31, nicht gemessen). Aus Encoder und Senke werden andere `Error` und andere Ausnahmen nicht gefangen, ebenso nicht die
Abbruchausnahme der Coroutine (Setzung, Voreinstellung 2026-10-30, nicht gemessen). Ein Fehler beendet den
Lauf; weitere Ereignisse werden nicht gelesen.
Return Status und Ausgabeparameter werden bis zu ihrer Abbildung mit einem Fehler der Typabbildung
(`SPEC-014`) abgelehnt; mit der Zuordnung in §4 ist das `TDS_UNSUPPORTED_TYPE` (Class 20), und die
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
festgelegt sind; die Überführung der Werte und die Zeilenquelle des Backends sind noch nicht gebaut.
Festgelegt ist die Bedeutung:

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
- `SimQuery.filter` ist ein Hinweis zum Vorfiltern (Schritt 5 in `TDS-FA-11.a`), kein Ersatz für die
  Prüfung durch das Backend.
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
- **Weitere Festlegungen des Lesers** (Setzungen, nicht am Server und nicht an d-migrate gemessen):
  `tables` als leere Abbildung ist erlaubt; Namen von Tabellen und Spalten sind nicht leer; `decimal`
  verlangt `precision` und `scale`, `scale` höchstens `precision`; `ordinal` kommt bei keinen zwei Spalten
  gleich vor; Ganzzahlen müssen in 32 Bit passen; `schema_format` und die Pflichttexte werden als Text
  gelesen, mit oder ohne Anführungszeichen; ein Wahrheitswert oder eine Ganzzahl in Anführungszeichen wird
  abgewiesen. Der Pfad einer Ladefehler-Meldung besteht aus Schlüsseln des Dokuments, ein Abschnitt höchstens
  64 Codepunkte lang, Steuer-, Format-, Zeilen- und Absatztrennzeichen (Unicode-Kategorien Cc, Cf, Zl, Zp)
  und einzelne Ersatzzeichen als `?`; der Typname steht in der Meldung nur, wenn er einer der 21 Typen des
  Schemas ist.
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
SQL-Server-Image setzt `ACCEPT_EULA=Y` voraus; die Zustimmung des Repo-Inhabers gilt als Setzung (rechtlich ungeprüft), der Lauf setzt die Variable ausdrücklich. Gemessen ist nur eine Instanz der Version 2022: `Encrypt=Strict` (TDS 8.0) scheitert, und der Server-Log nennt TLS 1.0 bis 1.2; der Abgleich beider Pfade entfällt dort.

## 3. Defaults und Konstanten

| ID | Name | Wert | Begründung |
|---|---|---|---|
| `SPEC-005` | `LISTEN_PORT` | 1433 (konfigurierbar) | TDS-Standardport; Wertebereich 0 bis 65535, 0 wählt einen freien Port (Festlegung, nicht gemessen); dazu `LISTEN_HOST`, nicht leer, Voreinstellung `0.0.0.0` (Festlegung, nicht gemessen) |
| `SPEC-006` | `MAX_TDS_PACKET_BYTES` | 65535, zusätzlich protokollkonforme Headerprüfung | Obergrenze des 16-Bit-Längenfelds |
| `SPEC-007` | `MAX_MESSAGE_BYTES` | 16 MiB (vorläufig) | Speicherbegrenzung, gilt während des Zusammensetzens (`TDS-FA-01.a`); mit Clientprofil abgleichen |
| `SPEC-008` | `LOGIN_TIMEOUT` | 15 s (vorläufig; konfigurierbar in ganzen Sekunden von 1 bis 3600, Festlegung, nicht gemessen) | Hängende Logins begrenzen; die Frist gilt ab Verbindungsaufbau über PRELOGIN, TLS-Handschlag und LOGIN7, danach schließt der Server ohne Antwort; für eine angemeldete Verbindung gilt sie nicht |
| `SPEC-046` | `MAX_PRELOGIN_MESSAGE_BYTES` | 65536 (vorläufig; konfigurierbar von 512 bis 65536, die obere Grenze ist die der PRELOGIN-Verhandlung, Festlegung, nicht gemessen) | Vor abgeschlossenem Login kein großer Puffer: PRELOGIN und LOGIN7 sind klein, eine größere Nachricht wird abgewiesen |
| `SPEC-047` | `SHUTDOWN_GRACE` | 10 s (vorläufig, konfigurierbar; 0 = sofort) | Frist für laufende Anfragen beim geordneten Beenden (`SPEC-044`) |
| `SPEC-009` | `QUERY_TIMEOUT` | 30 s (vorläufig, konfigurierbar) | Backend-Timeout; Quelle der Frist einer Anfrage (`deadlineEpochMillis` = Beginn plus `QUERY_TIMEOUT`), die der Protokollkern durchsetzt (`SPEC-022`); Ablauf endet in `TDS_TIMEOUT` (`SPEC-016`) |
| `SPEC-010` | `ENCRYPTION_POLICY` | Standard `REQUIRED`; Abschwächung nur durch ausdrückliche, im Log sichtbare Konfiguration, je Clientprofil; genau `REQUIRED` oder `TEST_UNENCRYPTED`; `REQUIRED` verlangt `TLS_KEYSTORE_FILE` (PKCS#12) und `TLS_KEYSTORE_PASSWORD_FILE`, `TEST_UNENCRYPTED` lässt beide nicht zu (Festlegung, nicht gemessen) | Keine unbemerkte TLS-Herabstufung |
| `SPEC-033` | `CANCEL_TIMEOUT` | 5 s (vorläufig, konfigurierbar) | Frist für nicht abbrechbare Backendoperationen, danach Verbindung schließen |
| `SPEC-034` | `MAX_CONNECTIONS` | 1000 (vorläufig, konfigurierbar) | Ressourcenlimit für gleichzeitige Verbindungen |

Vorläufige Werte sind technische Entwurfsannahmen, keine aus dem Lastenheft abgeleiteten
Abnahmegrenzen.

## 4. Fehler-Codes und Logging-Felder

| ID | Interner Code | Bedingung | Aktion |
|---|---|---|---|
| `SPEC-011` | `TDS_PROTOCOL_ERROR` | Paket/Token ungültig, auch eine RPC-Nachricht, die der Parser abweist (Struktur- oder Kodierfehler: Format, Länge, Typkennung außerhalb der Typliste, TYPE_INFO, ALL_HEADERS; ein gültig kodierter, aber nicht unterstützter PLP-Typ gehört nicht dazu, siehe `SPEC-013`; Setzung der Nutzerentscheidung vom 2026-10-31, nicht gemessen) | Abbruch oder ERROR (Severity 20, Setzung), je nach Zustand; nach ERROR schließt der Server |
| `SPEC-012` | `TDS_LOGIN_FAILED` | Authentifizierung fehlgeschlagen | Login-Fehler, Session nicht erstellen; Meldung und State für unbekannte Kennung und falsches Passwort identisch; Nummer 18456, Class 14, einheitlicher State, feste Meldung ohne Kennung und genau ein ERROR vor DONE (am Referenzclient gemessen, am Spike-Testserver und nicht am Produkt: dieselben Antwortbytes für beide Fälle, keine Wiederholung; für das Produkt ist die Gleichheit eine Anforderung). Danach schließt der Server die Verbindung und nimmt kein weiteres LOGIN7 an (Setzung, nicht gemessen; am echten Server wurde nur die Fehlerantwort einmal beobachtet, das Verbindungsende nicht). An einem echten SQL Server wurde einmal beobachtet (vom Review bestätigt): falsches Kennwort und unbekannter Benutzer ergeben beide Nummer 18456, State 1, Class 14, die Meldung nennt den Benutzernamen; der interne State des Servers ist am Client nicht sichtbar. |
| `SPEC-013` | `TDS_UNSUPPORTED_RPC` | RPC nicht unterstützt: ProcID oder Prozedurname außerhalb des Katalogs (`SPEC-030`), eine Katalogform, deren Bearbeitung nicht gebaut ist, ein Parameter außerhalb des Mindestkatalogs (`SPEC-003`), auch ein gültig kodierter PLP-Typ (`nvarchar(max)`; der Rest der Nachricht wird dann nicht gelesen) oder ein Wert außerhalb des Bereichs seines Typs; gilt nur für gültig kodierte Nachrichten, ein Struktur- oder Kodierfehler (zum Beispiel eine Typkennung außerhalb der Typliste, deren Wertlänge unbestimmt ist) ist `SPEC-011` (Trennung nach Ursache: Setzung der Nutzerentscheidung vom 2026-10-31, nicht gemessen) | ERROR und DONE mit Fehlerstatus; die Verbindung bleibt offen (Class 16, Zuordnung in diesem Abschnitt), das Backend wird nicht gerufen; das Log nennt den Ablehnungsgrund als feste Marke, nie einen Wert |
| `SPEC-014` | `TDS_UNSUPPORTED_TYPE` | Wert nicht serialisierbar | definierter Fehler, kein fehlerhaftes Resultset |
| `SPEC-015` | `TDS_BACKEND_ERROR` | Backend-Ausführung fehlgeschlagen | Fehlerabbildung in TDS |
| `SPEC-016` | `TDS_TIMEOUT` | Frist überschritten (die Frist einer Anfrage, `QUERY_TIMEOUT`, `SPEC-009`) | Cancel, Cleanup, Fehler/Disconnect |
| `SPEC-037` | `TDS_UNSUPPORTED_SQL` | Anweisung oder Bedingung außerhalb von `SPEC-036`; Schreibzugriff ohne Callback | ERROR und DONE mit Fehlerstatus, Zeilenquelle nicht gelesen |
| `SPEC-038` | `TDS_SIM_UNKNOWN_OBJECT` | Tabelle oder Spalte ist nicht beschrieben | ERROR und DONE mit Fehlerstatus; vorgesehene SQL-Server-Nummern 208 (Objekt) und 207 (Spalte) |
| `SPEC-039` | `TDS_SIM_INVALID_VALUE` | Wert nicht in den Spaltentyp überführbar | ERROR und DONE mit Fehlerstatus; vorgesehene SQL-Server-Nummern 245 (Konvertierung) und 8115 (Überlauf), an der Referenzinstanz zu messen |
| `SPEC-048` | `TDS_INTERNAL_ERROR` | unerwartete Ausnahme im Server oder im Backend-Adapter, auch ein `OutOfMemoryError` beim Erzeugen oder Senden der Antwort (`SPEC-031`) | ERROR (Severity 20) und DONE mit Fehlerstatus, fester Text ohne den Text der Ausnahme, danach Verbindungsende; die Ausnahme geht ins Log, ohne Parameterwerte |
| `SPEC-049` | `TDS_SHUTDOWN` | Anfrage nach Beginn des geordneten Beendens (`SPEC-044`) | ERROR und DONE mit Fehlerstatus, danach Verbindungsende; vorgesehen: die SQL-Server-Meldung für ein Herunterfahren, Nummer im Mapping nach `TDS-FA-06.a` festlegen |

Die Nummern in der Aktionsspalte sind ein Vorschlag für das Mapping nach `TDS-FA-06.a`. Für
`TDS_UNSUPPORTED_SQL` gibt es keine SQL-Server-Entsprechung, denn dort ist die Anweisung gültig; die
Nummer kommt aus dem Benutzerbereich (ab 50001).

**Zuordnung des Fehler-Mappings** (`TDS-FA-06.a` Nr. 2 und 6; Träger:
`ErrorCode` im Package `protocol.error`). Kein Wert dieser Tabelle ist an einem echten SQL Server
gemessen, mit einer Ausnahme: Nummer 18456, State 1 und Class 14 von `TDS_LOGIN_FAILED` wurden an einem
echten SQL Server einmal beobachtet (`SPEC-012`); der Text dort nennt den Benutzernamen, der feste Text
dieser Tabelle nicht. Die Spalte *Herkunft* trennt: *Vorschlag* (Nummer aus der Aktionsspalte oben,
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

| ID | System | Version | Referenz |
|---|---|---|---|
| `SPEC-020` | Microsoft TDS | TDS 7.4 und TDS 8.0 (Strict-Encryption) | [MS-TDS](https://learn.microsoft.com/en-us/openspecs/windows_protocols/ms-tds/) |
| `SPEC-021` | .NET SQL-Client | `Microsoft.Data.SqlClient` 7.1.1 auf .NET 10 (SDK 10.0.403), im Container ausgeführt | [Clientprofil](#clientprofil-vertrag-zu-spec-021) |
| `SPEC-022` | Query-Backend | Projektvertrag v0: Typen und Schnittstellen als Quelltext (Setzung vom 2026-10-30, nicht gemessen); Pflichten und Grenzen nach Nutzerentscheidung vom 2026-10-31 (Messung im Spike, nicht am Produkt), Rest Entwurf | [Query-Backend](#query-backend-vertrag-zu-spec-022) |
| `SPEC-023` | TLS | TLS 1.3 und TLS 1.2 (Mindestversion 1.2); Suiten TLS 1.3: `TLS_AES_256_GCM_SHA384`, `TLS_AES_128_GCM_SHA256`; TLS 1.2: ECDHE mit AES-GCM; Standard `REQUIRED` | [Sicherheitsprofil](#sicherheitsprofil-vertrag-zu-spec-023) |
| `SPEC-051` | Authentifizierungsadapter | Schnittstelle als Kotlin-Quelltext (Festlegung, nicht gemessen); Prüfwert der festen Benutzerliste mit Verfahrenskennung (`plain` nur im Testmodus, `pbkdf2-sha256`), eigene Datei nur für die Benutzerliste, Iterationen mit Boden, Standard und Obergrenze, strenger Parser; Dateirechte und Behandlung eines BOM offen (Stand 2026-10-30) | [Authentifizierungsadapter](#authentifizierungsadapter-vertrag-zu-spec-051) |

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
| PRELOGIN-Antwort | Pakettyp `0x04`; Optionen VERSION (`0.0.0.0`), ENCRYPTION, INSTOPT (leer), MARS (aus); kein FEDAUTHREQUIRED. ENCRYPTION ist ENCRYPT_REQ bei `REQUIRED`, ENCRYPT_NOT_SUP im ausdrücklich konfigurierten Testmodus |
| Annahme | Pakettyp `0x04`; ENVCHANGE Datenbank, Packet Size, SQL-Collation, dann LOGINACK (Interface 1, TDS-Version `0x74000004`, bei TDS 8.0 `0x08000000`) und DONE ohne Statusbits |
| Ablehnung | genau ein ERROR (18456, State 1, Class 14, Text "Login failed." ohne Kennung), dann DONE mit dem Fehlerbit; danach schließt der Server |
| Datenbank und Packet Size | die Datenbank aus LOGIN7, sonst `master`; die verlangte Packet Size, wenn der Paket-Codec sie schreiben kann (größer als der Paketkopf von 8 Byte und höchstens `MAX_TDS_PACKET_BYTES`), sonst 4096. Die Packet Size wird per ENVCHANGE angekündigt und steht in der Sitzung; **angewandt wird sie nicht**: der Paket-Schreiber des Transports schreibt mit der Größe, mit der er gebaut wird. Ein Client, der weniger als diese Größe verlangt, kann Pakete über seiner Größe sehen. Wer die Größe anwendet, ist nicht festgelegt |

#### RPC-Katalog des Referenzclients

Der Referenzclient sendete in den gemessenen Szenarien (Messgrenzen: Testserver, ohne echten SQL Server; TDS 7.4 unverschlüsselt, kein TDS 8.0, kein TLS; `Pooling=false`; eine Verbindung je Szenario; ein Lauf): Batch (`0x01`) für Text ohne Parameter; `sp_executesql` (ProcID 10) für Text mit Parametern; `sp_prepexec` (ProcID 13) und `sp_execute` (ProcID 12) bei `Prepare()` mit Parametern; RPC mit Prozedurnamen für `CommandType.StoredProcedure`. `sp_prepare`, `sp_unprepare` und `sp_cursor*` erschienen in diesen Szenarien nicht; unter Pooling ist das nicht gemessen. `ExecuteReader(SchemaOnly)` sendet einen Batch mit `SET FMTONLY OFF; SET FMTONLY ON;` im Text (Behandlung offen). `ExecuteReader(KeyInfo|SingleRow)` sendet `sp_executesql` mit `SET FMTONLY OFF; SET NO_BROWSETABLE ON;` im Text (Behandlung offen); Transaktionen sind Pakettyp `0x0E`, kein RPC (offen). Der Katalog ist als Entwurf in `SPEC-030` festgelegt und noch nicht freigegeben.

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
- **Frist (Nutzerentscheidung vom 2026-10-31):** Der Protokollkern setzt die Frist durch. Quelle ist `QUERY_TIMEOUT` (`SPEC-009`): er setzt `deadlineEpochMillis` auf „Beginn der Anfrage plus `QUERY_TIMEOUT`“ und umschließt die Sammlung des Stroms mit einem Zeitlimit. Bei Ablauf geht der Lauf den Weg des Cancel (Strom abbrechen, `close()`, zurückgehaltenen Abschluss verwerfen) und endet in `TDS_TIMEOUT` (`SPEC-016`; ERROR und DONE mit Fehlerstatus, solange der Strom synchron ist, sonst schließt die Verbindung, `SPEC-031`). Das Feld bleibt für kooperative Backends ein Hinweis; ein Backend darf es ignorieren. Einzelheiten (Setzung, Voreinstellung 2026-10-30, nicht gemessen): `null` heißt keine Frist; eine Frist in der Vergangenheit endet sofort in `TDS_TIMEOUT`; die Wanduhr wird einmal beim Start der Sammlung in eine Dauer umgerechnet; `CANCEL_TIMEOUT` (`SPEC-033`) bleibt unabhängig davon die Frist für den Abbruch selbst. Gemessen im Spike (Frist 500 ms): Überschreitung 0 bis 3 ms bei suspendierendem oder unterbrechbarem Backend, bis zum Ende des Aufrufs bei nicht unterbrechbarem (1500 bis 1513 ms bei 2000 ms Blockade; im Betrieb begrenzt durch `CANCEL_TIMEOUT`, `SPEC-033`); ein Backend, das die Frist ignoriert, lief bis zum Stromende (3042 ms). Class von `TDS_TIMEOUT` und Verbindungsfolge bleiben offen (`SPEC-016`, §4).
- **`OutOfMemoryError` (Nutzerentscheidung vom 2026-10-31):** Er wird im Anfragepfad gefangen und als `TDS_INTERNAL_ERROR` beantwortet (`SPEC-031`, `SPEC-048`); die Verbindung schließt nach der Antwort. Betreiberschalter, kein Server-Code: der Startparameter `-XX:+ExitOnOutOfMemoryError` beendet den Prozess beim ersten `OutOfMemoryError` (im Spike Exit-Code 3 nach rund 2 s). Akzeptiertes Negativ: im Spike war beim Fangen in vier von vier Läufen nach 20 s mindestens eine Coroutine nicht beendet, in dreien davon eine unbeteiligte kleine Verbindung (ob sie hängt oder später endet, ist nicht unterschieden); die Ursache ist nicht isoliert, die Häufigkeit im Produkt nicht gemessen; ob die Antwort im erschöpften Heap noch geschrieben werden kann, ist nicht gemessen.

#### Festlegungen des Backend-Vertrags

- Fehlersemantik im Strom (Setzung des Nutzers, nicht am Produkt gemessen): Solange der Strom synchron ist, folgen auf Teilzeilen ein `Failure` und damit ERROR und DONE; lässt er sich nicht mehr abschließen, schließt der Server die Verbindung ohne ERROR und DONE (`SPEC-031`).
- Festlegungen des Response Sequencers (Nutzer, 2026-10-30, nicht am Produkt gemessen; der Tokenstrom ist Handvektor und Selbstkonsistenz): ein `Failure` zählt in der `DONE_MORE`-Vorschau als nächstes Ereignis (ein zurückgehaltenes `ResultEnd` oder `UpdateCount` bekommt `DONE_MORE`, danach ERROR und DONE mit Fehlerstatus). Beleg nur für das Bit vor dem ERROR: der Referenzclient gegen einen echten SQL Server zeigt `DONE[MORE|COUNT]` vor dem ERROR eines batch-abbrechenden Fehlers; am Produkt nicht gemessen. Für statement-abbrechende Fehler, bei denen der echte Server `DONE[MORE|ERR]` sendet, schreibt der Sequenzierer die Variante ohne `MORE`, weil `Failure` keine Klasse trägt); ein zurückgehaltener Abschluss wird bei Cancel oder Disconnect verworfen; ein Verstoß gegen die Ereignisreihenfolge ist ein Fall von `SPEC-048` (ERROR, DONE mit Fehlerstatus, Verbindung schließen); `ReturnStatus` und `OutputParameter` werden bis zu ihrer Abbildung mit einem Fehler der Typabbildung abgelehnt; mit der Zuordnung in §4 ist das `TDS_UNSUPPORTED_TYPE` (Class 20) und die Verbindung schließt nach der Antwort, ebenso bei jedem anderen Typfehler (die Einzelzuordnung des Codes ist offen). Der Ausgang des Sequenzierers trägt die Verbindungsfolge (Fehler offen oder schließt nach der Antwort, je nach Abbildung; Reihenfolgeverstoß und Ausnahme schließen; ein nicht abschließbarer Strom schließt ohne Antwort). Eine Ausnahme des Backends wird dem Fehler-Mapping mit der Ausnahme übergeben, damit sie ins Log geht; ihr Text erreicht den Client nie. Eine Abbruchausnahme läuft aus `execute()` wie aus dem Strom nach außen. Setzung des Implementers: ein `Failure` beendet den Lauf, spätere Ereignisse werden nicht gelesen; ein Strom ohne Ereignis endet in einem DONE ohne Bits; eine Ausnahme aus `execute()` oder aus dem Strom ist ein Fall von `SPEC-048`.

- RPC im Backend-Vertrag (Nutzerentscheidung vom 2026-10-31, nicht gemessen): Ein RPC erreicht das Backend als `QueryRequest` mit Text und Parameterliste, der Vertrag bleibt unverändert. `sp_executesql`: der SQL-Text der Nachricht unverändert als `sql`; die Deklarationsliste wird nicht gedeutet, Name, Typ und Wert jedes Wertparameters kommen aus der Nachricht, die Richtung aus dem Statusbyte (Bit 0 mit Wert `INPUT_OUTPUT`, Bit 0 mit NULL `OUTPUT`, sonst `INPUT`). RPC per Prozedurname: der Server baut aus Name und Parameternamen den Text `EXEC <name> @a = @a, @o = @o OUTPUT`; die Werte stehen nur in der Parameterliste, nie im Text. Der Name muss einer engen Grammatik folgen (ein bis vier Teile, je regulärer Bezeichner oder in eckigen Klammern, höchstens 128 Zeichen je Teil, kein Steuerzeichen), jeder Parametername `@` plus Bezeichnerzeichen (ohne Beachtung der Schreibweise eindeutig, `@@` am Anfang nur beim RPC per Prozedurname abgelehnt, weil der Name dort im Aufruftext auch rechts von `=` steht und `@@x` eine globale Funktion bezeichnen kann; bei `sp_executesql` steht der Name nicht im Text und `@@x` ist nicht abgelehnt; Setzung der Nutzerentscheidung vom 2026-10-31, nicht gemessen); sonst gilt der RPC als unbekannt (`SPEC-013`). Zählregel der Obergrenze 128 (Festlegung): ein regulärer Teil zählt mit allen Zeichen, ein Teil in Klammern ohne die Klammern (Escape `]]` zählt zwei Zeichen), ein Parametername mit dem `@`. Ein späteres Backend muss die Quotierung der Namen im Aufruftext selbst beachten: ein Teil in eckigen Klammern endet nur an einem einfachen `]` (`]]` ist ein Zeichen), danach folgen nur `.` oder das Ende; es darf den Aufruftext nicht an anderen Zeichen trennen. Ein Parameter ohne Namen, mit anderem Statusbit als OUTPUT oder mit einem Typ ohne Entsprechung in `SqlType` (`INTN` mit 1 und 2 Byte, Gleitkomma, Geld, GUID, Binär, `varchar`, `DATETIMN`, `DATETIMEOFFSETN`) ergibt ebenfalls `SPEC-013`; ebenso ein Wert außerhalb des Bereichs seines Typs (Dezimalbetrag ab 10 hoch Precision, Vorzeichenbyte außer 0 und 1, Datum nach 9999-12-31, Zeit ab 24 Uhr; Festlegung ohne Messung). Ein PLP-Typ (`nvarchar(max)`, `varchar(max)`, `varbinary(max)`) ist gültig kodiert, aber nicht unterstützt und ergibt ebenfalls `SPEC-013` bei offener Verbindung (Setzung der Nutzerentscheidung vom 2026-10-31, nicht gemessen). Eine RPC-Nachricht, die der Parser als Struktur- oder Kodierfehler abweist (Format, Länge, Typkennung außerhalb der Typliste, TYPE_INFO, ALL_HEADERS), wird dagegen wie ein SQL_BATCH mit Parserfehler beantwortet: `SPEC-011` beziehungsweise die Kategorie des Parsers, die Verbindung schließt (Nutzerentscheidung vom 2026-10-31, nicht gemessen). Die JVM-Typen der Parameterwerte sind die der Zeilenwerte (`Int`, `Long`, `Boolean`, `String`, `BigDecimal`, `LocalDate`, `LocalTime`, `LocalDateTime`). Setzung des Implementers, nicht gemessen: `sp_prepexec` und `sp_execute` ergeben bis zur Bearbeitung der Handles `SPEC-013`; per Name gesendet ergeben `sp_executesql`, `sp_prepexec`, `sp_execute`, `sp_prepare`, `sp_unprepare` und `sp_cursor*` ebenfalls `SPEC-013`. Die Antwort auf einen erfolgreichen RPC besteht aus denselben Tokens wie die auf einen Batch (DONE); RETURNSTATUS, RETURNVALUE und DONEPROC sind noch nicht festgelegt.

#### Offene Punkte des Backend-Vertrags

- Unterscheidung statement- und batch-abbrechender Fehler im Strom, die `Failure` nicht trägt: der Sequencer schreibt eine Variante (ERROR und DONE mit Fehlerstatus, Lauf beendet); die Verbindungsfolge nach Severity und die Abbildung von `Failure.code` bleiben Sache der Fehlerabbildung.
- Herkunft der Befehlskennung (`curCmd`) der DONE-Tokens: der Sequencer schreibt 0; der Port trägt keine Kennung (Spec-Lücke).
- Verzögerung durch den zurückgehaltenen Abschluss der `DONE_MORE`-Vorschau: nicht gemessen.
- Class von `TDS_TIMEOUT` und Verbindungsfolge nach Ablauf der Frist (ob Cancel und Timeout als Fehler zählen, `SPEC-016`, §4).

#### Gemessen am Backend-Vertrag

Gemessen sind: Heap, erste Zeile, Durchsatz, langsamer Leser, Fehler nach der ersten Zeile, Mehrfach-Ergebnisse, Cancel, Threads bei 200 Strömen (Flow-Messungen mit suspendierendem Backend, Blockade nur beim Iterator). Im Spike gemessen (Nachmessung vom 2026-10-30, Grenzen wie oben bei „Pflichten des Backends“; Einzel- bis Vierfachläufe auf einem ausgelasteten Rechner): Bytes je Zeile und Zeilen bis zum `OutOfMemoryError` bei 256 MiB Heap (`List<Any?>`: 173 Byte je schmale und 454 je breite Zeile, 1,54 Mio schmale Zeilen), der `OutOfMemoryError` ohne und mit Fangen (Hänger), ein blockierendes Backend hinter dem `Flow` (Threads, Wanddauer, Abbruch) und die Frist. Nicht gemessen: echte Query-Engine oder Treiber, breitere Zeilen, LOBs und PLP, OUTPUT-Parameter im Strom, Antworten eines echten SQL Servers, MARS, Transaktionen, andere Clients.

### Sicherheitsprofil (Vertrag zu `SPEC-023`)

TLS-Modi des Servers (`SPEC-010`, `SPEC-028`, `SPEC-043`) für das Clientprofil (`SPEC-021`).

| Modus | Konfiguration | PRELOGIN-Antwort | Verhalten |
|---|---|---|---|
| `REQUIRED` (Standard), TDS 7.4 | `ENCRYPTION_POLICY=REQUIRED` | ENCRYPT_REQ oder ENCRYPT_ON (beide gemessen) | TLS-Handshake in TDS-Paketen vom Typ `0x12`, danach verschlüsseltes LOGIN7 |
| `REQUIRED`, TDS 8.0 | `ENCRYPTION_POLICY=REQUIRED`, Client `Encrypt=Strict` | — (TLS vor PRELOGIN) | TLS 1.3 ab dem ersten Byte, ALPN `tds/8.0` |
| unverschlüsselter Testmodus | ausdrückliche Konfiguration, im Log sichtbar | ENCRYPT_NOT_SUP | nur TDS 7.4; Clients mit `Encrypt=false` melden sich unverschlüsselt an, Clients mit `Encrypt=true` brechen ab |

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

#### Festlegungen der TLS-Schleife

Festlegungen dieses Profils, nicht am Referenzclient gemessen (die Tests laufen gegen eine JDK-`SSLEngine` im selben Prozess):

- Im TDS-8.0-Pfad weist der Server eine Gegenstelle ab, die `tds/8.0` nicht aushandelt (kein ALPN oder ein fremdes); es entsteht kein Kanal.
- Im TDS-7.4-Pfad verlangt der Server für Handshake-Bytes den Pakettyp `0x12`; ein anderer Typ, ein Header außerhalb des Katalogs oder eine Länge unter dem Header beendet den Handshake.
- Endet der Strom nach dem Handshake ohne `close_notify`, ist das ein Fehler, kein sauberes Ende.
- Fehlertexte der Schleife nennen den Ausnahmetyp oder eine feste Beschreibung, nie Zertifikats- oder Schlüsselinhalt.
- Ein KeyUpdate der Gegenstelle in TLS 1.3 beantwortet die Schleife (mit einer JDK-Gegenstelle getestet: der Server schreibt einen kurzen Record, ohne selbst Nutzdaten zu schreiben, und die Nachrichten laufen weiter; der Inhalt der Antwort ist nicht beobachtet); Eine Neuverhandlung in TLS 1.2 durch die Gegenstelle beantwortet die Schleife (JDK-Voreinstellung; mit einer JDK-Gegenstelle dreimal hintereinander getestet: Nutzdaten laufen danach in beide Richtungen unversehrt, nie im Klartext); eine Begrenzung der Neuverhandlungen gibt es nicht, die Rechenlast vieler Neuverhandlungen ist nicht gemessen.

#### Schlüsselmaterial des Servers

Setzung dieses Profils, nicht gemessen und nicht aus dem Referenzclient abgeleitet:

- Schlüssel und Zertifikatskette des Servers kommen aus einem PKCS#12-Speicher; ein PEM-Lader ist nicht Teil des Servers.
- Speicher- und Schlüsselkennwort sind derselbe Wert; der Server nimmt den ersten Schlüsseleintrag.
- Zugelassen sind RSA-Schlüssel ab 2048 Bit; andere Arten und kürzere Schlüssel weist der Start ab (`SPEC-043`).
- Die Startprobe prüft jedes Zertifikat der Kette auf Gültigkeit zum Startzeitpunkt, nicht Vertrauen oder Namen.
- Die Konfiguration nennt den Speicher mit `TLS_KEYSTORE_FILE` und das Kennwort als Datei mit `TLS_KEYSTORE_PASSWORD_FILE` (beides Umgebungsvariablen, ein Zeilenende am Ende der Kennwortdatei wird entfernt); `ENCRYPTION_POLICY=REQUIRED` verlangt beide, `TEST_UNENCRYPTED` lässt sie nicht zu. Der Handler verdrahtet den Pfad TDS 7.4 (gekapselt in Paketen vom Typ `0x12`); der Pfad ab dem ersten Byte (TDS 8.0) ist nicht verdrahtet.

#### JVM-Flag des Serverprozesses

Der Serverprozess läuft mit `-Djdk.tls.server.newSessionTicketCount=0`. Ohne das Flag sendet der JDK-Server bei TLS 1.3 ein NewSessionTicket noch im Handshake; im TDS-7.4-Pfad landet es in einem Paket vom Typ `0x12`, und der Client bricht ab („Unrecognized record version“). Das Flag gilt prozessweit, auch im TDS-8.0-Pfad; es ist beim Start des Serverprozesses zu setzen. Eine Lösung ohne das Flag ist nicht gebaut. `main` setzt das Flag nicht selbst: der Aufruf des Prozesses setzt es, und der Start bricht ab, wenn die Systemeigenschaft nicht genau `0` ist (Setzung). Geprüft ist der Wert der Eigenschaft beim Start vor dem ersten TLS-Aufruf, nicht die Wirkung auf dem Draht; sie belegt der Test mit Kind-JVM mit und ohne Flag.

#### Gemessen am Sicherheitsprofil

TLS 1.3 mit `TLS_AES_256_GCM_SHA384` in beiden Pfaden (`Microsoft.Data.SqlClient` 7.1.1, .NET 10). Der unverschlüsselte Testmodus ist nur mit dem Referenzclient und `Encrypt=false` gemessen.

Mit `Microsoft.Data.SqlClient` 7.1.1 gegen einen Testserver mit RSA-Zertifikat in beiden Pfaden (TDS 7.4 im Tunnel und `Encrypt=Strict`):

- nur TLS 1.2 mit `TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384` und `…_AES_128_GCM_SHA256`: Handshake und LOGIN7 gelingen, der Client wählt die 256-Bit-Suite, wenn beide angeboten sind;
- nur TLS 1.2 mit der 128-Bit-Suite: gelingt;
- nur TLS 1.3 mit `TLS_AES_128_GCM_SHA256`: gelingt;
- nur TLS 1.1: der Client lehnt ab („tlsv1 alert protocol version“).

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

- Das Passwort erscheint in keinem Log und in keiner Fehlermeldung, auch nicht gekürzt oder verschleiert. Der Benutzername erscheint in keiner Antwort an den Client und im Log nur in einem Feld, das die Spezifikation festlegt (bisher keines; `TDS-FA-09.a`). `Rejected(reason)` trägt den Grund ohne Passwort und ohne Benutzernamen.
- Anforderung an den Produktivcode der Einstiegsimplementierung: Der Passwortvergleich läuft in konstanter Zeit über die Länge hinaus; bei unbekannter Kennung läuft ein Scheinvergleich. Gebaut und strukturell belegt (Abschnitt „Kotlin-Signatur des Authentifizierungsadapters“, Bauart des Vergleichs), nicht gemessen. Ein Adapter mit eigener Benutzerverwaltung unterliegt derselben Anforderung, der Vertrag kann sie nicht erzwingen.
- Die Antwort an den Client ist für unbekannten Benutzernamen und falsches Passwort in den Antwortbytes gleich (gemessen am Spike-Testserver, nicht am Produkt); das ist Sache des festen Antwortmusters des Anmeldepfads, nicht des Adapters.
- Ein Adapter blockiert den Anmeldepfad nicht länger als `LOGIN_TIMEOUT` (`SPEC-008`).

#### Einstiegsimplementierung des Authentifizierungsadapters

Feste Benutzerliste aus der Konfiguration (Benutzername, Passwortprüfwert). Die Liste wird beim Start validiert (`TDS-FA-09.a`); eine fehlerhafte Liste bricht den Start ab. Für Test und kleine Betriebe gedacht.

Prüfwertformat und Konfigurationsweg (vom Nutzer entschieden, 2026-10-30; was unter „Offen“ steht, ist nicht entschieden):

- **Verfahrenskennung:** Jeder Eintrag trägt vor dem Prüfwert eine Verfahrenskennung, `{kennung}Wert`. Ein Eintrag ohne Verfahrenskennung und eine unbekannte Verfahrenskennung brechen den Start ab (Setzung, nicht gemessen). Die Verfahrenskennung ist case-sensitiv und klein: `plain`, `pbkdf2-sha256`; `{PBKDF2-SHA256}` wird abgewiesen.
- **Verfahren:** `plain` nur bei ausdrücklich eingeschaltetem Testmodus, sonst Startabbruch vor dem Binden des Ports; ein leerer `plain`-Wert ist zulässig (nur im Testmodus). `pbkdf2-sha256` (`PBKDF2WithHmacSHA256` des JDK, keine Fremdbibliothek), Wert `Salz$Iterationen$Hash`; Salz und Hash sind Standard-Base64 mit verlangtem Padding.
- **Iterationszahl (Setzung):** Boden 210 000, Standard 600 000, Obergrenze 10 000 000; der Betreiber unterschreitet den Boden nicht. Gemessen (nur in einem Spike-Image auf einem Host, kein Produktivwert) ist die Dauer je Ableitung: 54 ms bei 210 000, 125 ms bei 600 000, 2,1 s bei 10 000 000. Die Werte für Boden, Standard und Obergrenze sind gesetzt, nicht gemessen. Die Obergrenze schützt nicht vor gleichzeitiger Last: 64 Threads mit je 3 Ableitungen (192 Ableitungen) mit 600 000 und `--cpus=1` erreichten im Spike als längste Dauer einer Ableitung 23,6 s; das ist die Dauer der letzten Ableitung unter dieser Last, nicht die Wartezeit einer einzelnen Anmeldung. Die Iterationen bestehen nur aus ASCII-Ziffern (keine Vorzeichen, keine anderen Ziffernschriften).
- **Passwort-Randfälle:** Die Ableitung nimmt die UTF-8-Bytes des vom Client gelieferten Passworts (UTF-16 nach UTF-8 wie im JDK). Ein alleinstehendes Hochsurrogat ergibt denselben Hash wie `?` (gemessen); das ist eine bekannte Eigenschaft. Es gibt keine Unicode-Normalisierung (NFC und NFD ergeben verschiedene Hashes, gemessen). Ein leeres Passwort ist zulässig und wird abgeleitet.
- **Konfigurationsweg:** eigene Datei nur für die Benutzerliste, eigenes Zeilenformat, eigener Parser. Der Pfad wird in der Umgebungsvariable `AUTH_USERS_FILE` übergeben, wie der Schalter `AUTH_PLAIN_TESTMODE` (Setzung (Voreinstellung 2026-10-30, nicht gemessen)); gemessen ist nur die Übergabe eines Werts per Umgebungsvariable, nicht die eines Pfades. Eine Zeile je Eintrag, `Benutzername<TAB>Prüfwert`, UTF-8, kein Escaping, Aufteilung am ersten TAB. Benutzernamen mit TAB, LF, CR oder alleinstehendem Hochsurrogat sind nicht darstellbar und ein Ladefehler. Ein doppelter Benutzername bricht den Start ab.
- **Testmodus-Schalter:** `AUTH_PLAIN_TESTMODE`, gilt nur bei genau `true`; eigener Schalter nur für `plain`; der Start-Log nennt den Modus.
- **Fehlerwerte:** je Abweichung genau eine Fehlerart mit Listenplatz, ohne Prüfwert und ohne Passwort. Der Listenplatz ist die Zeilennummer der Datei, 1-basiert (Setzung (Voreinstellung 2026-10-30, nicht gemessen)); gemessen ist im Spike die Position nach Sortierung, nicht die Zeilennummer.
- **Entschieden** (Nutzer, 2026-10-30; Setzungen, nicht gemessen): derselbe Iterationsboden im Testmodus, keine Kommentare und keine Leerzeilen, CRLF nicht toleriert, Hash genau 32 Byte und Salz mindestens 16 Byte ohne Obergrenze, Ziffernfolge der Iterationen 1 bis 8 Stellen ohne führende Null.
- **Offen** (vom Nutzer nicht entschieden, nicht gemessen): Dateirechte und Übergabe der Datei im Betrieb; weder der Lader noch der Start prüfen sie.
- **Lesen der Datei** (Konfiguration, `ConfigFiles`; Setzung, nicht gemessen): Die Datei wird als UTF-8 gelesen, ungültiges UTF-8 ist ein Startfehler (`NOT_UTF8`). Ein UTF-8-BOM am Anfang wird abgewiesen (`BYTE_ORDER_MARK`), nicht entfernt; er würde Teil des ersten Benutzernamens. Die Zeilenenden werden nicht normalisiert, ein CR erreicht den Parser und ist dort ein Fehler. `AUTH_PLAIN_TESTMODE` schaltet nur bei genau `true` ein; fehlt der Schlüssel oder ist er leer oder `false`, ist `plain` verboten, jeder andere Wert (auch `TRUE`) ist ein Startfehler und schaltet nie ein. Die Diagnose nennt Schlüssel, Art und bei der Liste Fehlerart und Zeile, nie Pfad, Wert, Benutzername oder Prüfwert.

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

Validierung der Liste: `UserList.parse(text, plainTestMode)` liefert `Valid` oder `Invalid(UserListError(kind, line))`; `line` ist die 1-basierte Zeilennummer der Datei, bei einer Liste ohne Zeile `null`. Das Lesen der Datei, `AUTH_USERS_FILE` und das Auswerten von `AUTH_PLAIN_TESTMODE` (nur der Wert `true` schaltet ein, `UserList.plainTestModeFrom`) gehören der Konfiguration. Die Fehlerarten sind `UserListErrorKind`.

Entschieden (Nutzer, 2026-10-30), Setzungen, nicht gemessen, jeweils im KDoc von `UserList` und `Pbkdf2Limits` gekennzeichnet:

- Der Iterationsboden 210 000 gilt auch im Testmodus.
- Keine Kommentare: eine Zeile mit `#` ist ein gewöhnlicher Eintrag. Eine leere Zeile ist ein Fehler; nur das Zeilenende am Dateiende ist erlaubt.
- Zeilenende ist LF; ein CR in einer Zeile (auch CRLF) ist ein Fehler.
- Der Hash hat genau 32 Byte, das Salz mindestens 16 Byte.
- Die Iterationen sind eine Ziffernfolge von 1 bis 8 Stellen ohne führende Null.
- Der erste Fehler in Dateireihenfolge bricht die Prüfung ab.
- Benutzernamen werden zeichengenau verglichen (keine Groß-/Kleinschreibung-Faltung, keine Normalisierung).
- Dateirechte und Übergabe der Datei sind weder geprüft noch entschieden.

Bauart des Vergleichs (Anforderung, strukturell belegt, nicht gemessen): Der Vergleich untersucht jede Position bis zur längeren Länge und bricht nicht beim ersten Unterschied ab. Bei unbekanntem Benutzernamen läuft dieselbe Ableitung wie für die Attrappe der Liste gegen deren Prüfwert (Scheinvergleich); das Ergebnis wird verworfen. Attrappe ist der `pbkdf2`-Eintrag mit der höchsten Iterationszahl (bei Gleichstand der erste in Dateireihenfolge), Salz- und Hashlänge wie dort; hat die Liste nur `plain`-Einträge, ist es der erste Eintrag und es läuft keine Ableitung, wie für jeden bekannten Benutzer. Verbleibende Grenze bei gemischten Listen: Ein bekannter Benutzer mit niedrigerer Iterationszahl oder mit `plain`-Prüfwert durchläuft einen anderen Rechenschritt als die Attrappe. Eine Aussage über Zeit ist daraus nicht abzuleiten; nichts davon ist gemessen.

#### Gemessen am Authentifizierungsadapter

Gemessen sind: LOGIN7-Felder, Entschleierung, Ablehnungsbytes, erkennbare Modi. Zu Prüfwert und Konfigurationsweg gemessen mit JDK 21 (Eclipse Adoptium 21.0.12.1) in einem Spike-Image auf einem Host (Intel Core i9-13900H): Ableitungsdauer je Iterationszahl, Kodierung des Passworts (wie UTF-8), Parser-Fälle, Sichtbarkeit des Prüfwerts je Weg; die Werte gelten nur für dieses Image und diese Hardware. Nicht gemessen: Dateirechte im Betrieb, Wirkung einer blockierenden Ableitung auf den Transport, Zeitgleichheit von unbekannter Kennung und falschem Passwort, Windows-SSPI (NTLM), Verschlüsselungsmodus als Adaptereingabe, TDS 8.0 mit SQL-Authentifizierung, LOGIN7 über mehrere Pakete, Pooling mit `ResetConnection`, andere Clients.

## 7. Festlegungen der Harness-Werkzeuge

| ID | Werkzeug | Festlegung |
|---|---|---|
| `SPEC-024` | Unit-Test-Gate | Unit-Tests für Decoder, Zustandsautomat, Encoder und Fehlerabbildung müssen erfolgreich sein; Gate: `make test` |
| `SPEC-025` | Integrationstest-Gate | Containerisierter Server + echter .NET-Referenzclient (`Microsoft.Data.SqlClient` 7.1.1, .NET 10) prüfen Login, SQL_BATCH, RPC, Typen, Fehler, Cancel; ohne lokale .NET-Installation und ohne Mount, Basis-Images per Digest, Pakete per Lock und Hash; Gate noch einzurichten |
| `SPEC-026` | `make gates` | Aggregator: führt alle eingerichteten Gates aus; neue Gates für Formatierung, statische Prüfung und Tests werden in ihn aufgenommen |
| `SPEC-027` | Fuzz-/Property-Tests | Defekte Paketlängen, Offsets, Fragmentierung und Typwerte dürfen keinen ungefangenen Prozessabsturz verursachen |
| `SPEC-040` | Simulations-Test-Gate | Unit-Tests der Anweisungserkennung (jede Form aus `SPEC-036` positiv und negativ, Grenzen bei Literalen, Bezeichnern und Kommentaren), Unit-Tests des Lesers der Tabellenbeschreibung (jedes gelesene Feld, jeder Ladefehler mit Pfad, Zeile und Spalte, ohne Inhalt in der Meldung) und Integrationstest mit dem .NET-Referenzclient: Auswahl, leeres Ergebnis, Zeilenquelle über der Speichergrenze, Fehler der Zeilenquelle, Cancel; Gate noch einzurichten |
| `SPEC-041` | Static-Analysis-Gate (`make detekt`, `make detekt-selbsttest`) | detekt in Docker, Konfiguration auf Standardbasis mit dem ktlint-Regelsatz (prüft, schreibt nichts um), `warningsAsErrors` und den Importregeln der Schichten, im Kern als Erlaubnisliste ohne Fremdbibliothek; dazu Technik-Regeln für Kern und Port, die Prozess, Dateisystem und Ähnliches über `ForbiddenMethodCall` sperren und deshalb nur über `detektMain` und `detektTest` wirken (Typauflösung); keine Baseline; jeder Befund bricht den Lauf. Tests des Kerns bleiben frei von Fremdbibliotheken (`kotlin.test` statt JUnit), weil die Kernregeln auch für Test-Quellen gelten. Die Importregeln lesen nur `import`-Zeilen: ein voll qualifizierter Name im Code und ein `typealias` darauf umgehen sie; das Gate belegt die Einhaltung der Importgrenze, nicht des Classpaths. `@Suppress` und `@SuppressWarnings` im Quelltext brechen den Lauf vor detekt (Textprüfung des Suppression-Gates). Ein Selbsttest belegt, dass jede Importregel ihre verbotenen Importe meldet, die erlaubten Testdaten nicht und das Suppression-Verbot jede Form meldet; er läuft nur bei geänderten Eingängen |
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
| 2026-10-30 | Verträge zu `SPEC-021`, `SPEC-022`, `SPEC-023` und `SPEC-051` in Abschnitt 6 übernommen; Spec-Lücke zu `SqlType` im Backend-Vertrag entfällt (`SPEC-002` definiert ihn); Zeilenformat der Benutzerliste in `SPEC-051` auf den Stand des Vertrags angeglichen |
| 2026-10-30 | Simulations-Backend nachgeführt (`SPEC-035`, `SPEC-036`, `SPEC-040`, `SPEC-045`): Ignorierliste um sieben Spaltenfelder, `tables` als Pflicht, Längengrenze der Typabbildung, Setzungsvermerke, Lesart der Liste „Nicht erkannt“ als Setzung, Tests des Lesers, gemeinsame Quelle der Schemata |
| 2026-10-30 | Anfragezustand im Verbindungsautomaten festgelegt: flacher Zuschnitt, Sitzungsrahmen statt Response Sequencer hält den Zustand, Ende der Antwort als ein Übergang (`SPEC-001`) |
| 2026-10-31 | Grenzen des Backend-Vertrags nach Nutzerentscheidungen: Klassen der Werte je Spaltenart, Pflicht „suspendierend“ für Backend und Zeilenquelle, Frist durch den Protokollkern mit `QUERY_TIMEOUT` als Quelle, `OutOfMemoryError` im Anfragepfad gefangen (`SPEC-002`, `SPEC-009`, `SPEC-016`, `SPEC-022`, `SPEC-031`, `SPEC-035`, `SPEC-048`); keine Grenze für „kleine“ Ergebnisse als Setzung; Messungen nur im Spike, nicht am Produkt |
| 2026-10-31 | Simulations-Backend, weitere Festlegungen von Scanner, Zuordnung und Leser der Tabellenbeschreibung nachgetragen (`SPEC-035`, `SPEC-036`) |
| 2026-10-31 | RPC-Kodierungsfehler schließen mit Protokollfehler (`SPEC-011`), nicht gebaute Formen bleiben `SPEC-013` bei offener Verbindung; Zählregel der Obergrenzen, Wertbereich, Quotierung im Backend-Vertrag, `@@` abgelehnt (Nutzerentscheidung; `TDS-FA-04.a`, `SPEC-011`, `SPEC-013`) |
| 2026-10-31 | RPC: `@@x` als Parametername nur beim RPC per Prozedurname abgelehnt, gültig kodierte PLP-Typen offen als `SPEC-013` statt Schließen, Strukturfehler schließen weiter (Nutzerentscheidung, nicht gemessen; `TDS-FA-04.a`, `SPEC-011`, `SPEC-013`) |
| 2026-10-31 | RPC im Backend-Vertrag: `sp_executesql` und RPC per Prozedurname als Text mit Parameterliste (Nutzerentscheidung), Ablehnungsgründe unter `SPEC-013`; kein Wert im Aufruftext (`TDS-FA-04.a`, `SPEC-013`, `SPEC-030`) |
| 2026-10-31 | Simulations-Backend, Fix-Runde der Prüfung: Wörter je Codepunkt, Namensvergleich und Pfadbereinigung als Festlegung, Grenzen des Modells (`SPEC-035`, `SPEC-036`) |

