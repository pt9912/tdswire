# TLS mit mssql-jdbc gegen den Testserver (`REQUIRED`): Messprotokoll vom 2026-11-05

Messprotokoll zu [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate) und [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität) (`AGENTS.md` §5 Regel 7). Es hält fest, was gemessen wurde, nicht, ob eine Anforderung belegt ist. Gegenstand ist Hürde 13 der [Sonde](d-migrate-sonde-2026-11-05.md): d-migrate mit `encrypt=true` gegen den Testserver mit `ENCRYPTION_POLICY=REQUIRED` endet nach LOGIN7 ohne Antwort.

## Bedingungen

| Angabe | Wert |
|---|---|
| Datum | 2026-11-05 |
| Host | Linux 6.8, 20 Kerne; Last 4 bis 41 (fremde Läufe, dazu `make kompatibilitaet` und `make test` desselben Rechners); Einzelmessungen, je Fall ein Lauf |
| Stand des Servers | Commit `6e91aec` (PRELOGIN-Version gesetzt), Image `tdswire-testserver:tls` aus `make testserver-up`, Image-ID `sha256:70aa61a11e02c9a3be425d2f521e3e5d059b876d2af02d094d721bcd5045feca`, JDK des Servers Temurin 21.0.12.1 |
| Client | d-migrate 1.12.0, Image `ghcr.io/pt9912/d-migrate@sha256:308f27753036c9cc423f716b9fc4c9e68016543a8003ecd2f8282e05a2ee6491`, JDK des Clients Temurin 21.0.12.1, Treiber `mssql-jdbc-13.4.0.jre11` (Dateiname im Image) |
| Server-Start | `make testserver-up` mit `TESTSERVER_NAME=tdswire-tls`, `TESTSERVER_IMAGE=tdswire-testserver:tls`, `TESTSERVER_PORT=14335`, `TESTSERVER_VERSCHLUESSELUNG=REQUIRED`, Schlüsselspeicher `gueltig.p12` aus der Stage `testkeys` des Dockerfiles, Benutzerliste `alice` mit `{pbkdf2-sha256}`-Prüfwert (600000 Iterationen, Kennwort `secret`, ein Testwert) |
| Client-Aufruf | `schema reverse --source mssql://alice:secret@127.0.0.1:<Port>/manuell?encrypt=true&trustServerCertificate=true --output rev.yaml`, `docker run --network host`; JDK-Optionen über `JAVA_TOOL_OPTIONS` |
| Zweiter Server | gleiches Image, von Hand als Container mit `JAVA_TOOL_OPTIONS=-Djavax.net.debug=ssl,handshake,record` gestartet (Port 14336), sonst gleiche Umgebung wie `make testserver-up` |
| Draht | `alpine/socat -x -v` als Zwischenstück zwischen Client und zweitem Server (Port 14337 nach 14336) |

## Ergebnis in einem Satz

Dem Server fehlt nichts: Er nimmt TLS 1.3 im Mantel von TDS 7.4 an, wenn der Client sein `Finished` in einem Paket vom Typ `0x12` sendet. **mssql-jdbc 13.4.0 (JDK 21) sendet unter TLS 1.3 sein `Finished` gar nicht an den Server** und schickt LOGIN7 gleich als rohen TLS-Record (Typ `0x17`, ohne TDS-Kopf). Der Server erwartet im Handschlag ein Paket `0x12`, liest `0x17` und trennt ohne Antwort. Die Ursache liegt im Treiber, nicht im Server (Grenze der Aussage: unten).

## Messungen

**M1. Fehlerbild reproduziert.** Aufruf wie oben mit `-Djavax.net.debug=ssl,handshake`: Exit 4, Meldung `Failed to initialize pool: SQL Server did not return a response. The connection has been closed.` Client-Log: ClientHello (TLS 1.3, 417 Byte), ServerHello, `Negotiated protocol version: TLSv1.3`, `TLS_AES_256_GCM_SHA384`, EncryptedExtensions, Certificate, CertificateVerify, Finished des Servers verarbeitet, `Produced client Finished handshake message`, `WRITE: TLSv1.3 handshake, length = 52`, `WRITE: TLSv1.3 application_data, length = 485` (LOGIN7), dann `close inbound of SSLSocket`.

**M2. Draht (socat) unter TLS 1.3.** Folge der Client-Bytes (aus `from=`/`to=` des Mitschnitts):

| Bytes | Inhalt |
|---|---|
| 0 bis 66 | PRELOGIN (`12 01 00 43 ...`) |
| 67 bis 496 | ein Paket `0x12` mit dem ClientHello (`12 01 01 ae 00 00 01 00 16 03 03 01 a1 01 ...`), 430 Byte |
| 497 bis 1019 | **ein roher TLS-Record** `17 03 03 02 06 ...` (Typ `0x17`, Länge 518), 523 Byte, **ohne Kopf `0x12`** |

Die Gesamtzahl der Client-Bytes ist 1020 (67 + 430 + 523). Das `Finished` hat 90 Byte als Record (`Raw write` des JDK mit `-Djavax.net.debug=ssl:record:plaintext:packet`: `17 03 03 00 55 ...`), sein Paket mit Kopf wären 98 Byte; es steht nirgends im Mitschnitt. Der 523-Byte-Record ist der LOGIN7-Record (`Raw write` des JDK: `17 03 03 02 06 ...`, im selben Lauf gemessen). Der Server antwortet auf das ClientHello mit einem Paket `0x12` von 1281 Byte (ServerHello, ChangeCipherSpec, EncryptedExtensions bis Finished) und liest danach nichts mehr, das Server-Log trägt nach dem Schreiben dieser drei Records (`WRITE: TLSv1.3 handshake, length = 122`, `change_cipher_spec, length = 1`, `handshake, length = 1102`) keine Zeile `READ:` mehr.

**M3. Gegenprobe TLS 1.2.** Mit `-Djdk.tls.client.protocols=TLSv1.2` und mit dem Treiber-Parameter `sslProtocol=TLSv1.2` in der URL (ohne JVM-Option): Der Client schickt nach dem Server-Flug von 1157 Byte ein Paket `0x12` mit ClientKeyExchange, ChangeCipherSpec und Finished (101 Byte, `12 01 00 65 ... 16 03 03 00 25 10 00 00 21 ...`), der Server antwortet mit seinem Finished in einem Paket `0x12` (59 Byte), die Anmeldung gelingt. Der Lauf endet mit Exit 4 und `Failed to initialize pool: Unsupported SQL.`: das ist Hürde 2 der Sonde (`SELECT 1`, [slice-d-migrate-verbindung-und-export](../plan/planning/in-progress/slice-d-migrate-verbindung-und-export.md)), nicht Gegenstand dieser Messung. Unter TLS 1.2 trägt der Treiber das Client-`Finished` also in einem Paket `0x12`; unter TLS 1.3 nicht.

**M4. Der Server, mit Rohbytes nachgestellt.** `TlsHandshakeNegativeTest` (Server als Prozess mit `REQUIRED`, Gegenstelle eine JDK-`SSLEngine`, TLS 1.3, Handshake in Paketen `0x12`): Mit `Finished` im Paket `0x12` und LOGIN7 im selben Schreibvorgang dahinter gibt der Server `LoginAck` (der Test `Ein TLS-1_3-Client mit Finished im Handschlagpaket wird angemeldet`). Erreicht das `Finished` den Draht nie (`dropLastFlight`) und folgt LOGIN7 als roher Record, trennt der Server ohne Antwort, das Ereignis `TlsHandshakeFailed` nennt `PROTOCOL_VIOLATION` (der Test `Ein TLS-1_3-Client ohne gesendetes Finished wird ohne Antwort getrennt`). Das ist derselbe Draht wie in M2. Die Mutation, die den zweiten Test rot färbt: in `NetInbound` die Meldung `Pulled.Violation("Paketheader ...")` durch `Pulled.EndOfStream` ersetzen (gesehen: der Test endet mit `FAILED`, weil das Ereignis dann `CONNECTION_ABORTED` nennt; ein zweiter Test der Schicht, `TlsNegativeTest`, färbt sich ebenfalls).

**M5. Vergleich mit dem .NET-Referenzclient.** Die Spezifikation hält fest (`Microsoft.Data.SqlClient` 7.1.1, .NET 10): TLS 1.3 mit `TLS_AES_256_GCM_SHA384` gelingt im TDS-7.4-Pfad gegen diesen Server ([Sicherheitsprofil](../../spec/spezifikation.md#gemessen-am-sicherheitsprofil)). Am selben Tag: `make kompatibilitaet KATEGORIE=Anmeldung` gegen den Kompatibilitätsserver (`REQUIRED`, Schlüsselspeicher `gueltig.p12`) endete grün, 19 Tests, darunter `Anmeldung_mit_den_Standardeinstellungen_des_Clients_gelingt` und `Tds74_Mandatory_prueft_den_Namen_im_Zertifikat` (TDS 7.4 im Mantel); Laufzeit 84 s (60 s Bau, 24 s Tests) bei Last bis 41. Die ausgehandelte TLS-Version dieses Laufs ist nicht ausgegeben und hier nicht behauptet; sie steht in der Spezifikation.

## Was gemessen ist, und was nicht

- **Gemessen:** der Draht des Fehlers (M2), dass die Ausnahme nur TLS 1.3 betrifft (M3), dass der Server ein regelgerecht gerahmtes TLS 1.3 annimmt (M4, M5), und die Stelle, an der der Server trennt (kein `READ:` im Server-Log nach seinem Flug; `PROTOCOL_VIOLATION` im nachgestellten Fall).
- **Nicht gemessen:** warum der Treiber das `Finished` nicht schreibt. Der Quelltext von mssql-jdbc ist nicht gelesen; die Erklärung, der Treiber puffere die Handshake-Bytes des Clients und gebe sie nur frei, wenn danach gelesen wird (unter TLS 1.2 liest der Client nach seinem `Finished` noch das des Servers, unter TLS 1.3 nicht), ist eine Vermutung, die zu M1 bis M3 passt, aber nicht belegt ist.
- **Nicht gemessen:** das Verhalten eines echten SQL Servers; der Server nimmt TLS 1.3 im TDS-7.4-Mantel nach der Spezifikation an, ein Microsoft-Server wählt nach unserer Kenntnis dort TLS 1.2 (nicht gemessen), so dass der Fehler dem Treiber dort nicht auffallen müsste.
- **Nicht gemessen:** andere Treiberversionen, andere JDK-Versionen, `encrypt=strict`.
- Der Rechner war ausgelastet (Last bis 41); die Abläufe sind nicht zeitkritisch, ein Zeitwert wird nicht behauptet.

## Folgerung für den Slice

Eine Behebung in der Bibliothek ist nicht möglich, ohne den Handschlag zu schwächen: Ein Server, der LOGIN7-Records ohne das `Finished` des Clients annähme, nähme eine Verbindung an, deren Handschlag der Client nicht bestätigt hat. Das senkte [TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit) und braucht nach `AGENTS.md` §3.6 eine ADR. Ob der Standard des Treibers (`encrypt=true` unter TLS 1.3) vom Testserver verlangt wird oder ob der Parameter `sslProtocol=TLSv1.2` an der Verbindungszeichenfolge des Clients der dokumentierte Weg ist, ist eine Entscheidung des Nutzers über die Grenze der Zusage aus [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate).
