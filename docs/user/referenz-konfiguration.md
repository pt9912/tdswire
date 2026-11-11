# Referenz: Konfiguration, Fehlercodes und Grenzen

Diese Seite ist Nachschlagewerk zu den Anleitungen [Bibliothek einbetten und betreiben](einbettung-und-betrieb.md) und [Testserver von Hand](testserver-manuell.md). Sie führt Felder, Voreinstellungen, Fehlercodes, Antwortformen und Grenzen als knappe Tabellen. Verbindlich ist die [Spezifikation](../../spec/spezifikation.md); bei Widerspruch gilt sie. Ereignisarten: [Referenz der Ereignisse](referenz-ereignisse.md).

## Konfigurationsfelder

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
| `requests.maxPreparedHandles` | `MAX_PREPARED_HANDLES` | 1000 | 1 bis 100000 | `SPEC-061` |
| `requests.maxMessageBytes` | `MAX_MESSAGE_BYTES` | 16 MiB | 65535 bis 268435456 Byte, Summe der Paketlängen einer Nachricht nach dem Login | `SPEC-007` |
| `requests.metricsIntervalSeconds` | `METRICS_INTERVAL_SECONDS` | 60 s | 0 bis 86400, 0 = keine Schnappschüsse der Metriken; wirkt nur mit Senke | `SPEC-062` |

Alle Bereiche und Voreinstellungen sind Festlegungen, nicht gemessen ([Abschnitt Defaults](../../spec/spezifikation.md#3-defaults-und-konstanten)); die Voreinstellungen stehen in `ServerConfig.kt`. Der Testserver liest zusätzlich `LOG_LEVEL` und `SIMULATION_DESCRIPTION_FILE` ([Log](referenz-ereignisse.md#log-des-testservers)). Die Namen der Umgebungsvariablen des Testservers pinnt `ConfigLoaderTest`; die Schlüsseltabelle und die Konfiguration im Code halten sich nur durch Lesen, kein Test vergleicht die Liste mit den Feldern von `ServerConfig`.

**Nicht einstellbar.** `MAX_TDS_PACKET_BYTES` (65535, `SPEC-006`) ist die Protokollgrenze des 16-Bit-Längenfelds und keine Einstellung; `MAX_MESSAGE_BYTES` ist einstellbar (Tabelle). Die Drosselung wiederholter Anmeldeversuche (`SPEC-053`) ist nicht konfigurierbar.

**Verschlüsselung.** `REQUIRED` verlangt Schlüsselmaterial. `TEST_UNENCRYPTED` (TDS 7.4 im Klartext) ist eine ausdrückliche Abschwächung für Tests und erscheint mit Senke als Ereignis `SECURITY_WEAKENED` mit `reason = ENCRYPTION_TEST_UNENCRYPTED`. Welche TLS-Version der Server je Pfad anbietet, steht in `SPEC-023` (TDS 7.4 höchstens TLS 1.2, TDS 8.0 TLS 1.3). Wer die Bibliothek einbettet, setzt die Ticket-Zahl der JDK-Systemeigenschaften `jdk.tls.server.*` nicht auf 0: gemessen ist, dass `Microsoft.Data.SqlClient` im 7.4-Pfad den Handschlag abbricht („unexpected message“), wenn der Server die Session-Ticket-Erweiterung ankündigt und wegen Ticket-Zahl 0 kein Ticket sendet; ohne die Erweiterung war der Handschlag grün, andere Werte sind nicht gemessen ([Messprotokoll](../messungen/tls-d-migrate-2026-11-05.md)).

## Benutzerliste

Eine Zeile je Benutzer, `Benutzername<TAB>Prüfwert`, UTF-8; der Prüfwert trägt eine Verfahrenskennung: `{pbkdf2-sha256}Salz$Iterationen$Hash` (Salz und Hash Standard-Base64 mit Padding) oder `{plain}Kennwort` nur im Testmodus. Format und Fehlerarten: [Authentifizierungsadapter](../../spec/spezifikation.md#authentifizierungsadapter-vertrag-zu-spec-051) (Setzungen, nicht gemessen). `UserList.parse(text, plainTestMode)` liefert ein `UserListResult`: `Valid` trägt die `UserList` (Feld `list`), die `AuthSettings(users, plainTestMode)` entgegennimmt, `Invalid` einen `UserListError` mit Art und Zeile. Belegt ist, dass eine root-eigene, weltlesbare Datei, per `docker cp` in einen Container gelegt, im Testserver gelesen wird; Die Bibliothek setzt keine Dateirechte; sie liest nur den Text, den die Anwendung übergibt, und diese Referenz macht zur Datei im Betrieb keine Aussage.

## Verbindungen

Der Listener nimmt höchstens `MAX_CONNECTIONS` Verbindungen zugleich auf; eine weitere wird ohne Antwortbytes geschlossen. Endet die Gegenstelle während einer Anfrage, wird die Anfrage am Backend abgebrochen und nichts mehr geschrieben. Abbruch einer laufenden Anfrage (Cancel) mit Folgeabfrage ist am Referenzclient im Integrationslauf belegt (`make simulations-integration` und `make kompatibilitaet KATEGORIE=Synchronitaet`, TDS 7.4); nicht belegt sind Abbruch im Leerlauf am Draht und mehrere Abbrüche je Anfrage ([Kompatibilitätsmatrix](../plan/kompatibilitaetsmatrix.md), Zeile 12). Am echten SQL Server (2022 und 2025, Referenzclient) gemessen: `Cancel()` an einem beendeten und an einem nicht gestarteten Befehl wirft keine Ausnahme und lässt die Verbindung offen ([Messprotokoll](../messungen/referenzabgleich.md), M-5).

## Prepared-Handles

Eine Sitzung hält höchstens `MAX_PREPARED_HANDLES` Handles zugleich (`SPEC-061`). Das `sp_prepexec`, das ein Handle darüber vergäbe, endet mit `TDS_PREPARE_LIMIT` (`SPEC-058`); die Verbindung bleibt offen, die vorhandenen Handles bleiben gültig. Gezählt werden die gleichzeitig gehaltenen Handles. Freigegeben wird durch den SQL-Batch `EXEC sp_unprepare <n>`; mssql-jdbc 13.4.0 sendet ihn, sobald die Antwort auf `sp_prepexec` ein RETURNSTATUS vor dem Handle trägt. Der RPC `sp_unprepare` gibt nichts frei und endet mit `TDS_UNSUPPORTED_RPC`. `sp_execute` mit einem Handle, das die Sitzung nicht hält (nie vergeben, freigegeben, aus der Zeit vor `RESETCONNECTION`), ergibt ebenfalls den Fehler 8179 (Class 16, State 4, Text „Could not find prepared statement with handle <n>.“), gefolgt von RETURNSTATUS 8179 und DONEPROC mit `ERROR`, ohne das Backend zu rufen und bei offener Verbindung (`SPEC-057`); Nummer, Class, State, Text für Handle 999999 und offene Verbindung sind am Referenzclient gemessen (2022 und 2025, [Messprotokoll](../messungen/referenzabgleich.md), M-6 und M-7), RETURNSTATUS, die Tokenfolge und der Fall nach `RESETCONNECTION` sind Setzung. Ein Handle, das die Sitzung nicht hält, ergibt bei der Freigabe den Fehler 8179 (Class 16, State 8, Text „Could not find prepared statement with handle <n>.“) wie am echten SQL Server (gemessen am Referenzclient, 2022 und 2025, [Messprotokoll](../messungen/referenzabgleich.md), M-4); der Server meldet jede solche Nummer eines Batches mit einem Fehler, ohne Obergrenze, wie der echte Server (Messprotokoll, M-22; `SPEC-060`). Die Antwort besteht je Nummer aus RETURNSTATUS und DONEPROC mit der Befehlskennung `0x00e0` und ohne DONE (Messprotokoll, M-23): gehalten ergibt RETURNSTATUS 0, nicht gehalten ERROR 8179, RETURNSTATUS 8179 und DONEPROC mit `ERROR`; jedes DONEPROC außer dem der letzten Anweisung trägt `MORE`. Die Zahl der Nummern begrenzt `MAX_MESSAGE_BYTES`; die Antwort (131 Byte für eine einstellige Nummer, bei siebenstelligen rund 143 Byte) geht Token für Token auf den Draht und steht nicht im Speicher, und das Log trägt einen Eintrag je Antwort. Gemessen (Einzelmessung, Serverprozess mit 256 MiB Heap, [Messprotokoll](../messungen/unprepare-antwort-speicher-2026-11-07.md)): zwei gleichzeitige Nachrichten von 16 MiB mit lauter unbekannten Nummern erhalten die vollständige Antwort. Bei vier gleichzeitigen Nachrichten dieser Größe endete in Einzelmessungen (zum Teil einer Messfassung außerhalb des Repos, zum Teil Proben ohne Test im Repo) der Prozess mit `-XX:+ExitOnOutOfMemoryError`, der Option des Image des Testservers, und alle Sitzungen gingen verloren; ohne die Option schloss eine von vier Verbindungen ohne Antwort. Ein Batch aus Leerraum gleicher Größe scheiterte ebenso (die Anfragen belegen den Heap). `RESETCONNECTION` leert die Tabelle der Handles. Der Speicher je Sitzung wächst mit Grenze mal Textgröße und ist nicht gemessen; wer die Grenze erhöht, trägt ihn.

## Fehlercodes und Antwortformen

Jeder Fehler des Servers erreicht den Client als ERROR (feste englische Meldung; State 1, außer bei Nummern mit dem State des echten SQL Servers, zum Beispiel 8179: State 8 bei der Freigabe, State 4 bei `sp_execute`) und einen Abschluss mit Fehlerstatus; der Text einer Backend-Meldung oder Ausnahme erreicht den Client nie. Zuordnung nach [Abschnitt 4 der Spezifikation](../../spec/spezifikation.md#4-fehler-codes-und-logging-felder); am echten SQL Server beobachtet sind die Nummern 18456, 8179, 2627, 2628, 2714, 2705, 4902 und 208 (dieses mit Class 16), die übrigen Nummern sind Festlegungen:

| Code | Nummer | Class | Folge |
|---|---|---|---|
| `TDS_UNSUPPORTED_SQL` | 50001 | 16 | Verbindung offen |
| `TDS_UNSUPPORTED_RPC` | 50002 | 16 | offen |
| `TDS_UNSUPPORTED_TYPE` | 50003 | 20 | schließt |
| `TDS_BACKEND_ERROR` | 50004 | 16 | offen |
| `TDS_TIMEOUT` | 50005 | 16 | offen |
| `TDS_PROTOCOL_ERROR` | 50006 | 20 | schließt |
| `TDS_INTERNAL_ERROR` | 50007 | 20 | schließt |
| `TDS_SHUTDOWN` | 50008 | 20 | schließt |
| `TDS_PREPARE_LIMIT` | 50010 | 16 | offen |
| `TDS_SIM_UNKNOWN_OBJECT` | 208 | 16 | offen |
| `TDS_SIM_INVALID_VALUE` | 245 | 16 | offen |
| `TDS_SIM_KEY_VIOLATION` | 2627 | 14 | offen |
| `TDS_SIM_STRING_TRUNCATED` | 2628 | 16 | offen |
| `TDS_SIM_OBJECT_EXISTS` | 2714 | 16 | offen |
| `TDS_SIM_COLUMN_EXISTS` | 2705 | 16 | offen |
| `TDS_SIM_OBJECT_NOT_FOUND` | 4902 | 16 | offen |
| `TDS_SIM_UNKNOWN_PREPARED_HANDLE` | 8179 | 16 | offen |
| `TDS_LOGIN_FAILED` | 18456 | 14 | schließt |

Die Antworten auf das Massenladen und die Nachrichten des Referenzclients sind an `SqlBulkCopy` am echten SQL Server 2022 und 2025 gemessen (Katalog-Batch, auch mit Schema und Sonderzeichen im Namen der Tabelle, Form von `INSERT BULK` und der Nachricht, Schlüsselverstoß; [Messprotokoll](../messungen/referenzabgleich.md), M-8 bis M-12 und M-33 bis M-36); gemessen ist nur der Schlüsselverstoß, die Zuordnung jedes Fehlers des Einfügens zu 2627 ist Setzung, ebenso beim Fehler von `onInsert` eines `MERGE`.

Die Anmeldung mit falschem Kennwort und mit unbekanntem Benutzer ergibt dieselbe Antwort (Nummer 18456, Class 14, State 1, Text `Login failed.` ohne Kennung). Am echten SQL Server (2022 und 2025) nennt der Text den Benutzernamen; diese Abweichung ist gewollt ([Messprotokoll](../messungen/referenzabgleich.md), A-1). `TDS_SIM_UNKNOWN_OBJECT` sendet wie der echte Server Class 16 und lässt die Verbindung offen; der Text „Unknown object.“ weicht vom Text des echten Servers („Invalid object name '<Name>'.“) ab (A-3).

**Antwortformen** (Tokenfolgen gemessen am Referenzclient gegen den echten SQL Server 2022 und 2025, Einzelmessung je Instanz, und als Handvektoren in Tests abgelegt; [Messprotokoll](../messungen/referenzabgleich.md), Abschnitt „Mitschnitt-Lauf“, Messaussagen 1 bis 4):

| Anfrage | Antwort |
|---|---|
| SQL-Batch mit Ergebnis | `COLMETADATA ROW* DONE` (Statusbit `COUNT`) |
| RPC (`sp_executesql`, `sp_prepexec`) mit Ergebnis | `COLMETADATA ROW* DONEINPROC RETURNSTATUS [RETURNVALUE] DONEPROC` |
| RPC ohne Ausgabewert | `RETURNSTATUS 0` vor `DONEPROC` (der Wert 0, wenn das Backend keinen `ReturnStatus` meldet) |
| `sp_prepexec` | `RETURNSTATUS 0` vor dem `RETURNVALUE` mit dem Handle (Ordinal 0) |
| Fehler in der Anfrage | `ERROR` und `DONE` mit Fehlerstatus (im RPC `DONEPROC`); der Abschluss nach einem Fehler trägt die Befehlskennung nach der Form des Abschlusses (`SPEC-031`) |
| Änderung (`INSERT`, `UPDATE`, `DELETE`) | `DONE` mit `COUNT`, der Zeilenzahl und der Befehlskennung der Art, die das Backend im `UpdateCount` meldet: `0x00c3`, `0x00c5`, `0x00c4` (im RPC `DONEINPROC` mit `MORE\|COUNT`); ohne Angabe der Art steht die Kennung 0 (`SPEC-031`) |
| `CREATE TABLE`, `ALTER TABLE … ADD` (Batch) | `DONE` ohne `COUNT` und ohne Zeilenzahl, Befehl `0x00c6` bzw. `0x00d8`, wenn das Backend die Art `CREATE_TABLE` bzw. `ALTER_TABLE` meldet (das Simulations-Backend tut es); im RPC und ohne Angabe der Art steht die Kennung 0 mit `COUNT` (`SPEC-031`) |
| Transaktionstexte (Batch) | `set implicit_transactions on`: `DONE` ohne `COUNT`, Befehl `0x00b9`; `set implicit_transactions off IF @@TRANCOUNT > 0 COMMIT TRAN`: `DONE MORE` `0x00ba`, dann `DONE` `0x00c0`; `IF @@TRANCOUNT > 0 COMMIT TRAN`: `DONE` `0x00c0`; `set implicit_transactions off` allein: Änderungszähler 0 ohne Art (nicht gemessen) (`SPEC-031`, `SPEC-036`) |
| `CREATE TABLE` bei `set implicit_transactions on` | `ENVCHANGE` Typ 8 mit dem Deskriptor der Transaktion vor dem `DONE`; der bedingte `COMMIT` bei offener Transaktion: `DONE MORE` `0x00c0`, `ENVCHANGE` Typ 9, `DONE` `0x00d5`. Die Bibliothek hat keine Transaktion: `ROLLBACK TRAN` nimmt nichts zurück und schreibt kein `ENVCHANGE`; `ENVCHANGE` Typ 8 bei anderen Anweisungen als `CREATE TABLE` und im RPC schreibt der Server nicht, der echte SQL Server schon (`SPEC-031`) |
| Freigabe `EXEC sp_unprepare <n>` | je Nummer `RETURNSTATUS 0` und `DONEPROC` mit Befehl `0x00e0`, bei einer nicht gehaltenen Nummer `ERROR` 8179, `RETURNSTATUS 8179` und `DONEPROC` mit `ERROR`; kein `DONE` (`SPEC-060`) |
| `sp_execute` mit einem Handle, das die Sitzung nicht hält | `ERROR` 8179 (Class 16, State 4), `RETURNSTATUS 8179` und `DONEPROC` mit `ERROR`; kein `DONE` (`SPEC-057`) |
| erste Anfrage nach `RESETCONNECTION` | beginnt mit `ENVCHANGE` Typ 18 (leerer neuer und alter Wert), gleich welche Antwort folgt (`SPEC-001`) |
| `INSERT BULK` (Massenladen, Form B) | `DONE` mit Befehl `0x00fd`, ohne Zähler und ohne Statusbits; danach erwartet die Sitzung die Nachricht `0x07` |
| Probe der Zieltabelle von `SqlBulkCopy` (vor `INSERT BULK`) | vier Ergebnisse wie am echten Server: `@@TRANCOUNT`, die Spalten der Tabelle ohne Zeile, je Spalte die Kollation, die leere Alias-Tabelle; dazwischen `DONE`, `DONEINPROC` und `DONEPROC` der Anweisungen des Batches; die Tabelle steht in der Probe mit Schema (`dbo.t`) oder ohne (dann `dbo`), ein `]` im Namen als `]]`, ein `'` als `''`; ein Name mit drei Teilen (Datenbank, Schema, Tabelle) ist nicht erkannt, eine nicht beschriebene Tabelle oder ein unbekanntes Schema ergibt `ERROR` 208 (Class 16), die Verbindung bleibt offen (`SPEC-036`) |
| Nachricht `0x07` mit den Zeilen | `DONE` mit `COUNT` (Zahl der geladenen Zeilen) und Befehl `0x00f0` |
| Fehler beim Laden (Schlüsselverstoß) | `ERROR` 2627 (Class 14, State 1, gekürzter Text), `INFO` 3621 „The statement has been terminated.“ und `DONE` mit `ERROR`, Befehl `0x00f0`, ohne Zähler; die ganze Nachricht ist abgewiesen |
| `MERGE … OUTPUT $action` | `COLMETADATA` mit der Spalte `$action` (`nvarchar(10)`, nicht nullbar), je Aktion eine `ROW` mit `INSERT` oder `UPDATE`, `DONEINPROC` `MORE\|COUNT` mit Befehl `0x0117` und der Zahl der Zeilen (ohne Aktion keine `ROW`, Zähler 0), `RETURNSTATUS`, `DONEPROC` (`SPEC-031`, `SPEC-036`) |
| Fehler im Ergebnis eines `MERGE` | `COLMETADATA`, `ERROR` (2628 bei einem Text über der Länge seiner Spalte, 2627 bei einem Fehler von `onInsert`, sonst 50004), `INFO` 3621, `DONEINPROC` `MORE\|ERROR` mit Befehl `0x0117` ohne Zähler, `RETURNSTATUS` mit der Nummer, `DONEPROC` ohne Fehlerbit; das `INFO` ist für 2628 und 2627 gemessen, für andere Fehler gesetzt. Eine unbekannte Tabelle endet vor dem Ergebnis mit `ERROR` 208 (Class 16), `RETURNSTATUS 208` und `DONEPROC` mit `ERROR`, die Verbindung bleibt offen (`SPEC-036`) |
| Abbruch (ATTENTION) | `DONE` mit `ATTN`; der Server antwortet mit einer Nachricht, der echte Server im Stream mit zwei |

Der Fehlerabschluss laut Festlegung (`SPEC-031`): ein Fehler nach teilweise übertragenen Zeilen wird als ERROR plus Abschluss mit Fehlerstatus gemeldet, wenn der Strom synchron bleibt („synchron“ heißt: der Transport hat jedes bisher erzeugte Token vollständig angenommen); andernfalls schließt die Verbindung ohne ERROR und Abschluss. `FailureScope.STATEMENT` lässt die Folgeanweisung des Batches laufen (das DONE des Fehlers trägt dann `MORE|ERROR`, gemessen am echten Server für Division durch null), `FailureScope.BATCH` beendet den Lauf (`ERROR` ohne `MORE`). Welche Fehler welcher Klasse angehören, bestimmt das Backend; die Fehler des Servers selbst sind batch-abbrechend (Setzung). Ordinal der RETURNVALUE ist die Position des Parameters in der Aufrufliste (gemessen: der dritte Parameter von `sp_executesql` hat Ordinal 2, ein Fall je Instanz); das Flag-Feld jedes RETURNVALUE ist `0x0000`. Nach Ablauf von `QUERY_TIMEOUT` trägt das DONE der Fehlerantwort im Batch die Befehlskennung `0x00c1`, wenn ein Ergebnis begonnen und nicht abgeschlossen ist (aus dem Fehler im Ergebnis übertragen, die Frist ist nicht gemessen), sonst `0x00fd`.

## Grenzen

- Gemessen ist das Verhalten des Referenzclients `Microsoft.Data.SqlClient` 7.1.1 gegen den Server dieses Repos in den Läufen dieses Repos und gegen SQL Server 2022 und 2025 im Referenzabgleich ([Messprotokoll](../messungen/referenzabgleich.md)); nicht gemessen sind Verhalten unter Last, mit vielen Verbindungen oder langer Laufzeit und andere Clients als der Referenzclient und d-migrate 1.12.0.
- Der Referenzabgleich nennt die Abweichungen des Servers von der Referenz mit Pfad und Grund (A-Nummern im Protokoll); „gleich wie die Referenz“ gilt nur für die dort aufgenommenen Szenarien.
- Lizenzhinweise zu den Bibliotheken in `lib/` sind eine Rechtsfrage; [TDS-RB-05](../../spec/lastenheft.md#tds-rb-05--lizenz) verlangt die MIT-Lizenz des Projekts, das Jar führt sie unter `META-INF/LICENSE`.
- Keine Windows-Authentifizierung, kein Kerberos, kein Entra ID.
