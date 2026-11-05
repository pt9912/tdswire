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

**M4. Der Server, mit Rohbytes nachgestellt (Stand vor der Obergrenze, Commit `3de8b98`).** `TlsHandshakeNegativeTest` (Server als Prozess mit `REQUIRED`, Gegenstelle eine JDK-`SSLEngine`, TLS 1.3, Handshake in Paketen `0x12`): Mit `Finished` im Paket `0x12` und LOGIN7 im selben Schreibvorgang dahinter gab der Server `LoginAck`. Erreichte das `Finished` den Draht nie und folgte LOGIN7 als roher Record, trennte der Server ohne Antwort, das Ereignis `TlsHandshakeFailed` nannte `PROTOCOL_VIOLATION`; das ist derselbe Draht wie in M2. Die Mutation, die den zweiten Test rot färbte: in `NetInbound` die Meldung `Pulled.Violation("Paketheader ...")` durch `Pulled.EndOfStream` ersetzen (gesehen: `FAILED`, weil das Ereignis dann `CONNECTION_ABORTED` nannte). Beide Tests sind mit der Obergrenze entfallen, weil der 7.4-Pfad kein TLS 1.3 mehr anbietet; sie stehen im Commit `3de8b98`.

**M5. Vergleich mit dem .NET-Referenzclient.** Die Spezifikation hält fest (`Microsoft.Data.SqlClient` 7.1.1, .NET 10): TLS 1.3 mit `TLS_AES_256_GCM_SHA384` gelingt im TDS-7.4-Pfad gegen diesen Server ([Sicherheitsprofil](../../spec/spezifikation.md#gemessen-am-sicherheitsprofil)). Am selben Tag: `make kompatibilitaet KATEGORIE=Anmeldung` gegen den Kompatibilitätsserver (`REQUIRED`, Schlüsselspeicher `gueltig.p12`) endete grün, 19 Tests, darunter `Anmeldung_mit_den_Standardeinstellungen_des_Clients_gelingt` und `Tds74_Mandatory_prueft_den_Namen_im_Zertifikat` (TDS 7.4 im Mantel); Laufzeit 84 s (60 s Bau, 24 s Tests) bei Last bis 41. Die ausgehandelte TLS-Version dieses Laufs ist nicht ausgegeben und hier nicht behauptet; sie steht in der Spezifikation.

## Was gemessen ist, und was nicht

- **Gemessen:** der Draht des Fehlers (M2), dass die Ausnahme nur TLS 1.3 betrifft (M3), dass der Server ein regelgerecht gerahmtes TLS 1.3 annimmt (M4, M5), und die Stelle, an der der Server trennt (kein `READ:` im Server-Log nach seinem Flug; `PROTOCOL_VIOLATION` im nachgestellten Fall).
- **Nicht gemessen:** warum der Treiber das `Finished` nicht schreibt. Der Quelltext von mssql-jdbc ist nicht gelesen; die Erklärung, der Treiber puffere die Handshake-Bytes des Clients und gebe sie nur frei, wenn danach gelesen wird (unter TLS 1.2 liest der Client nach seinem `Finished` noch das des Servers, unter TLS 1.3 nicht), ist eine Vermutung, die zu M1 bis M3 passt, aber nicht belegt ist.
- **Nicht gemessen:** das Verhalten eines echten SQL Servers; der Server nimmt TLS 1.3 im TDS-7.4-Mantel nach der Spezifikation an, ein Microsoft-Server wählt nach unserer Kenntnis dort TLS 1.2 (nicht gemessen), so dass der Fehler dem Treiber dort nicht auffallen müsste.
- **Nicht gemessen:** andere Treiberversionen, andere JDK-Versionen, `encrypt=strict`.
- Der Rechner war ausgelastet (Last bis 41); die Abläufe sind nicht zeitkritisch, ein Zeitwert wird nicht behauptet.

## Nach der Entscheidung des Nutzers (2026-11-05): Obergrenze TLS 1.2 im 7.4-Pfad

**Quelltext-Befund (nicht gemessen, nicht verifiziert).** Laut Koordinator: In `IOBuffer.java` von mssql-jdbc (Zweig `main`, nicht der Tag 13.4.0) puffert `SSLHandshakeOutputStream` die Handshake-Bytes des Clients und sendet sie erst, wenn der Handschlag liest; unter TLS 1.3 liest der Client nach seinem `Finished` nichts mehr. Das passt zu M1 bis M3, ist hier aber nicht gelesen und bezieht sich nicht auf den gemessenen Stand.

**Entscheidung.** Der 7.4-Pfad bietet höchstens TLS 1.2 an, der 8.0-Pfad bleibt bei TLS 1.3 (Begründung nach Microsoft Learn laut Suchergebnis, nicht selbst am Server gemessen: ein echter SQL Server verhandelt im TDS-7.x-Pfad höchstens TLS 1.2).

**M7. d-migrate im Treiber-Standard nach der Änderung.** Image `tdswire-testserver:tls`, Image-ID `sha256:17a31de36ee6a38ad8d44098d47f8ec092f9d8cb2ca42bdf261061511788471c`, Server wie oben (`REQUIRED`, Port 14335), Last 18 bis 22: `schema reverse` mit `encrypt=true&trustServerCertificate=true`, ohne `sslProtocol` und ohne JVM-Option, endet mit Exit 4 und `Failed to initialize pool: Unsupported SQL.` Die Anmeldung gelingt also; der Fehler ist Hürde 2 (`SELECT 1`) des Parallel-Slice.

**M8. .NET-Referenzclient nach der Änderung: rot.** `make kompatibilitaet KATEGORIE=Anmeldung`: 19 Tests, 2 bestanden (beide TDS 8.0 `Encrypt=Strict`), 17 rot, alle mit `SqlException ... error:0A0000F4:SSL routines::unexpected message` im Handschlag des 7.4-Pfads. Mitschnitt (socat zwischen Client und Server): Der Server schreibt nach dem Client-`Finished` ein Paket `0x12` mit ChangeCipherSpec und `Finished` (59 Byte), der Client antwortet mit einem Alert (Paket `0x12`, Record-Typ `0x15`) und bricht ab. Server-Trace (`-Djavax.net.debug=ssl,handshake`): TLS 1.2, `TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384`, ServerHello trägt die Erweiterung `session_ticket` (leer), danach kommt aber kein NewSessionTicket, nur ChangeCipherSpec und `Finished`. Erklärung (zu M8 passend, nicht im JDK-Quelltext gelesen): Der Server kündigt Session-Tickets an und sendet wegen `-Djdk.tls.server.newSessionTicketCount=0` keines; ein Client, der ein Ticket erwartet, nennt das „unexpected message“. Gegenprobe: Mit `-Djdk.tls.server.enableSessionTicketExtension=false` im Server-Prozess (Kompatibilitätsserver, sonst unverändert) besteht `Anmeldung_mit_den_Standardeinstellungen_des_Clients_gelingt` unter TLS 1.2 im 7.4-Pfad. Das ist eine Messung eines Tests, nicht des ganzen Laufs. Ein Versuch, den Kompatibilitätsserver ganz ohne `newSessionTicketCount` zu starten, scheiterte am Start (die Prüfung des Flags und der Prozess des Testservers), ist also keine Messung dieser Alternative.

M8 widerlegt die Annahme der Spezifikation und der früheren Verifikation, TLS 1.2 im 7.4-Pfad sei mit `Microsoft.Data.SqlClient` gemessen: Der Verifikationsbericht vom 2026-11-01 nennt TLS 1.2 am Referenzclient „nicht belegt“, und diese Messung zeigt es rot. Offen ist die Wahl: das Flag `enableSessionTicketExtension=false` zusätzlich zur Pflicht (Prozess des Einbettenden), oder `newSessionTicketCount` fallen lassen (nach der Obergrenze nur noch für den 8.0-Pfad relevant, dort nicht gemessen).

## Folgerung für den Slice (vor der Entscheidung des Nutzers)

Eine Behebung in der Bibliothek ist nicht möglich, ohne den Handschlag zu schwächen: Ein Server, der LOGIN7-Records ohne das `Finished` des Clients annähme, nähme eine Verbindung an, deren Handschlag der Client nicht bestätigt hat. Das senkte [TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit) und braucht nach `AGENTS.md` §3.6 eine ADR. Ob der Standard des Treibers (`encrypt=true` unter TLS 1.3) vom Testserver verlangt wird oder ob der Parameter `sslProtocol=TLSv1.2` an der Verbindungszeichenfolge des Clients der dokumentierte Weg ist, ist eine Entscheidung des Nutzers über die Grenze der Zusage aus [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate).

## Matrix der JVM-Flags des Serverprozesses (2026-11-05, nach der Obergrenze TLS 1.2)

Gemessen, nicht entschieden. Der Server läuft in vier Varianten der Systemeigenschaften des Serverprozesses; der Stand ist der Commit `78dd86b` (7.4-Pfad höchstens TLS 1.2, 8.0-Pfad TLS 1.3), lokal auf einem Wegwerf-Zweig `messung-tls-flags` verändert (nicht auf dem Zweig des Slice). Die Änderungen: nur die Startzeile (`jvmArgs` der Task `kompatibilitaetsServer`, `ENTRYPOINT` der Stage `testserver`); für V2 und V3 zusätzlich die Prüfung des Flags `newSessionTicketCount` in `Bootstrap` ausgeschaltet **und** die Startprobe (`TlsStartProbe`, Ergebnis in `TdsListener.start` ignoriert), weil der Server ohne `newSessionTicketCount=0` sonst nicht startet (siehe „Befund Startprobe“).

| Variante | JVM-Flags des Serverprozesses |
|---|---|
| V0 | `-Djdk.tls.server.newSessionTicketCount=0` (Stand `78dd86b`) |
| V1 | V0 und `-Djdk.tls.server.enableSessionTicketExtension=false` |
| V2 | keines von beiden (Prüfung und Startprobe lokal ausgeschaltet) |
| V3 | nur `-Djdk.tls.server.enableSessionTicketExtension=false` (Prüfung und Startprobe lokal ausgeschaltet) |

Messungen: **a** = `make kompatibilitaet KATEGORIE=Anmeldung`, .NET-Referenzclient (`Microsoft.Data.SqlClient` 7.1.1), 19 Tests, davon 17 im TDS-7.4-Pfad (TLS 1.2); **b** = die zwei Tests `Tds80_Strict_*` derselben Läufe (TDS 8.0, TLS 1.3); **c** = d-migrate 1.12.0 (Image-Digest `sha256:308f27753036c9cc423f716b9fc4c9e68016543a8003ecd2f8282e05a2ee6491`, mssql-jdbc 13.4.0) mit `encrypt=true&trustServerCertificate=true` gegen `make testserver-up` (`REQUIRED`, Port 14335, Image `tdswire-testserver:tls`), `schema reverse`.

| Variante | a: 7.4-Pfad, TLS 1.2 (.NET) | b: 8.0-Pfad, Strict (.NET) | c: 7.4-Pfad (mssql-jdbc) | Last beim Start des Laufs (a/b; c) | Zeit a/b; c |
|---|---|---|---|---|---|
| V0 | **rot**, 2 von 19 bestanden, 17 rot, alle mit `error:0A0000F4:SSL routines::unexpected message` | grün, 2 von 2 | grün: Anmeldung gelingt, Exit 4 `Failed to initialize pool: Unsupported SQL.` | 9,3 und 8,8 (Wiederholung); erster Lauf 13,0 und 16,0 | 16 s und 7 s (Wiederholung, Cache); erster Lauf 59 s und 41 s |
| V1 | grün, 19 von 19 | grün, 2 von 2 | grün, Exit 4 `Unsupported SQL.` | 14,7 und 13,1 | 113 s; 44 s |
| V2 | grün, 19 von 19 | grün, 2 von 2 | grün, Exit 4 `Unsupported SQL.` | 9,9 und 16,5 | 68 s; 60 s |
| V3 | grün, 19 von 19 | grün, 2 von 2 | grün, Exit 4 `Unsupported SQL.` | 22,2 und 22,9 | 65 s; 68 s |

Die Last ist die einminütige Lastzahl des Rechners (20 Kerne, fremde Läufe); die Zeiten sind Wanduhr einer Einzelmessung je Zelle, keine Leistungsaussage. V0 ist zweimal gemessen (identisch: 2 von 19 grün, 17 rot); V1 einmal, V2 und V3 je einmal mit dem Bau der lokalen Änderung. Die Zahl „19 von 19“ der Zeile V1 stammt aus dem ersten Lauf und wurde nicht wiederholt. Die Zusatzprüfungen des Skripts `tools/kompatibilitaetstest.sh`, die zur Kategorie `Szenario` gehören (Pooling, SET-Texte, Prepare), sind in `Anmeldung` rot, wenn Tests der Kategorie rot sind (V0), und grün sonst; sie sind hier keine eigene Messung.

**Befund Startprobe.** Ohne `newSessionTicketCount=0` (V2, V3 unverändert gestartet) bricht der Start des Serverprozesses ab: `tdswire: Start gescheitert: TLS-Konfiguration: PAIR (Handschlag CONNECTION_ABORTED / Gegenstelle null)`. Das ist die Startprobe der Bibliothek, ein Handschlag im Speicher im 8.0-Pfad mit TLS 1.3. Gemessen ist der Abbruch (V2 und V3 je einmal ohne Lockerung, V1 und V0 starten); die Ursache im Handschlag ist nicht untersucht (naheliegend: das NewSessionTicket des Servers nach dem Handschlag, das die Gegenstelle der Probe nicht liest; nicht belegt). Mit der Lockerung der Probe starten V2 und V3, und echte Clients melden sich an (Tabelle). Eine Lösung ohne das Flag braucht also eine Änderung der Startprobe.

**Zusatzmessung 8.0-Pfad ohne Flag (V2), rohe TLS-1.3-Verbindung.** `openssl s_client -tls1_3 -alpn tds/8.0 -msg` (OpenSSL 3.0.13 im Image `tdswire-testkeys:tls`), 4 s Lesen nach dem Handschlag, Server aus dem Image von V2 (Startzeile ohne Flags): TLS 1.3, `TLS_AES_256_GCM_SHA384`, ALPN `tds/8.0`; nach dem Handschlag kommt ein `NewSessionTicket` (Handshake, 995 Byte; `Post-Handshake New Session Ticket arrived`), zwei Zeilen dazu im Mitschnitt; die Verbindung blieb offen, kein Alert. Derselbe Server mit `JAVA_TOOL_OPTIONS=-Djdk.tls.server.newSessionTicketCount=0`: kein Ticket (0 Zeilen). Die zwei `Tds80_Strict_*`-Tests des .NET-Clients bestehen in V2 und V3 (mit Ticket im 8.0-Pfad). Der 8.0-Pfad hat also im Draht kein Rahmenproblem durch das Ticket: der Strom ist roh TLS ab dem ersten Byte, ein Ticket ist ein TLS-Record wie jeder andere.

**Ursprung des Flags `newSessionTicketCount=0`.** `git log -S'newSessionTicketCount'`: erste Erwähnung im Commit `64993d7` (Zwischenstand der Messung von `slice-adr-transport`, 2026-10-29), übernommen in [ADR-0003](../plan/adr/0003-transportframework.md) (Commit `ac17f83`) und in die Spezifikation (Abschnitt „JVM-Flag“, mit der Umsetzung der Entscheidung entfallen). Pfad: **TDS 7.4** (`Encrypt=true`, Handschlag in Paketen `0x12`) **mit TLS 1.3**; Client: `Microsoft.Data.SqlClient` 7.1.1 auf .NET 10 gegen drei Prototypen (Netty, NIO, Coroutines); Befund laut Zeilen der Messung: Das NewSessionTicket des JDK-Servers landet noch im Handschlag in einem Paket `0x12`, der Client liest danach rohes TLS und bricht mit „Unrecognized record version“ ab; „mit erzwungenem TLS 1.2 tritt der Fehler nicht auf“. Die Zahlen stammen laut Slice aus dem Bericht eines Spike-Agenten und sind nicht wiederholt. Der 8.0-Pfad kommt in dieser Begründung nicht vor; der Test `TlsTicketFlagTest` belegt den 8.0-Pfad nur **mit** Flag (JDK-Gegenstelle). Seit der Obergrenze TLS 1.2 im 7.4-Pfad tritt der beschriebene Abbruch dort nicht mehr auf (mit Ticket-Erweiterung in V2 bis V3 grün gemessen), im 8.0-Pfad gilt er nach der Zusatzmessung nicht für die Rahmung; er gilt für die Startprobe der Bibliothek (Befund oben).

**Wirkung der beiden Flags.** `newSessionTicketCount=0` unterdrückt das NewSessionTicket (TLS 1.3 und, wie V0 zeigt, auch das von TLS 1.2), lässt aber im ServerHello von TLS 1.2 die Erweiterung `session_ticket` stehen; V0 ist deshalb rot für den .NET-Client. `enableSessionTicketExtension=false` nimmt die Ankündigung zurück (V1 und V3 grün); ob V3 im 8.0-Pfad TLS-1.3-Tickets sendet, ist nicht gemessen (die Strict-Tests laufen grün).

**Vorbehalte.** Einzelmessungen je Zelle (V0 zweimal), Last 9 bis 28, ein Host. Gemessen ist der Referenzclient `Microsoft.Data.SqlClient` 7.1.1 und d-migrate 1.12.0 (mssql-jdbc 13.4.0 JDK 21) in den Tests und Aufrufen oben; andere Clients, Treiber und das Verhalten nach Wiederaufnahme einer Sitzung (Ticket benutzt) sind nicht gemessen. Die Lockerungen von Prüfung und Startprobe in V2 und V3 sind lokale Änderungen des Messzweigs; die Bibliothek mit gelockerter Probe ist nicht getestet (kein `make test`, kein `make gates`). Der Test `TlsTicketFlagTest` und der Bootstrap-Test des Flags sind für V2 und V3 nicht gelaufen. Zu c: Exit 4 `Unsupported SQL` ist die erwartete Hürde 2 der Sonde, die Anmeldung gelingt.

## Umsetzung der Entscheidung (ADR-0032): Ursache der Startprobe und Messungen danach

Stand: Zweig `slice-tls-d-migrate` nach dem Commit der Umsetzung (Server ohne JVM-Option, Obergrenze TLS 1.2 im 7.4-Pfad). Ein Host (Linux 6.8, 20 Kerne), Einzelmessungen, Last 6 bis 10 (einminütige Lastzahl, fremde Läufe); die Zeiten sind Wanduhr, keine Leistungsaussage.

**Ursache des Abbruchs der Startprobe ohne Flag (Eingriff, kein Trace der einzelnen Schreiboperation).** Mit entferntem Flag in der Test-JVM scheiterte `TlsStartProbeTest` im TLS-1.3-Fall mit `PAIR (Handschlag CONNECTION_ABORTED:Kanal IO_FAILURE / Gegenstelle null)`: Die Gegenstelle der Probe meldete ihren Handschlag als gelungen (`null`), der Handschlag der Schleife scheiterte beim Schreiben (`IO_FAILURE` des Kanals). Erklärung, die dazu passt: Die Gegenstelle ist mit ihrem `Finished` fertig und die Probe schloss ihr Ende sofort; der TLS-1.3-Server des JDK meldet `FINISHED` erst, nachdem er sein NewSessionTicket geschrieben hat, und dieser Schreibvorgang traf das geschlossene Ende. Belege: (a) Die Probe besteht, wenn das Ende der Gegenstelle bis zum Urteil der Schleife offen bleibt, ohne dass die Gegenstelle das Ticket liest (Test `TlsStartProbeTest > probe passes with TLS 1_3 although the server writes a session ticket after the handshake`). (b) Nimmt man die Änderung zurück (Ende sofort schließen), werden `TlsStartProbeTest` (drei Fälle mit TLS 1.3), `BootstrapTest` (drei Fälle mit TLS-Start), `ServerSmokeTest > Ein Prozess ohne JVM-Option startet mit TLS`, `Tds8WiringTest`, `Tds8ProcessTest` und `SecurityConfigNegativeTest` rot (ein Lauf). (c) `TlsSessionTicketTest` zeigt am Draht, dass der Server im 8.0-Pfad nach dem Handschlag-Flug einen vierten Record (Typ `0x17`) schreibt, wenn die Test-JVM keine Option zur Ticket-Zahl trägt. Nicht belegt: dass dieser vierte Record der Schreibvorgang war, der scheiterte; (a) und (b) sind Eingriffe, die zur Erklärung passen.

**Gewählte Form der Probe.** Von den zwei Wegen der ADR (i weiterlesen, ii bis `FINISHED` prüfen) gilt (ii) mit einer Auflage: Die Gegenstelle liest das Ticket nicht; ihr Ende bleibt offen, bis die Schleife des Servers ihr Urteil hat, und schließt sofort, wenn die Gegenstelle selbst scheitert. Das Urteil ist das Ergebnis des Handschlags der Schleife (`SPEC-043` bleibt: ein vollständiger Handschlag mit den konfigurierten Versionen und Suiten, Diagnose `CERTIFICATE`, `PRIVATE_KEY`, `PAIR`); es hängt nicht von der Reihenfolge des Tickets ab und braucht weder Schlaf noch Zeitgrenze. Gegen (i): Weiterlesen müsste bis zu einem Ende des Stroms lesen, das erst das Urteil der Schleife erzeugt, oder die Zahl der Tickets kennen; beides ist mehr Zustand ohne Gewinn. Die Zeitgrenze der Probe (`DEFAULT_TIMEOUT`, 10 s) bleibt als Netz für eine Probe, die nicht endet; im gelungenen Lauf wirkt sie nicht. Annahme: Das Ticket (im Draht-Befund oben 995 Byte) passt in den Speicherkanal der Probe (64 KiB je Richtung); ein größerer Flug blockierte den Schreiber, die Probe endete dann an der Zeitgrenze als `PAIR`. Das ist nicht gemessen.

**Gegenproben der Tests (je ein Lauf von `make test`).**

| Änderung am geprüften Code | Erwartung | Gesehen |
|---|---|---|
| Ende der Gegenstelle der Probe sofort nach ihrem Handschlag schließen | Probe und Start mit TLS 1.3 rot | rot: `TlsStartProbeTest` (3 Fälle), `BootstrapTest` (3), `ServerSmokeTest` (Start mit TLS), `Tds8WiringTest`, `Tds8ProcessTest`, `SecurityConfigNegativeTest` |
| Test-JVM mit der Option zur Ticket-Zahl 0 starten (zweiter Lauf, zusammen mit der nächsten Zeile) | `TlsSessionTicketTest` rot (kein vierter Record) | rot |
| Obergrenze im 7.4-Pfad entfernen (`TlsAcceptor.protocolsFor` gibt alle Versionen zurück; im selben Lauf wie die Zeile davor) | `TlsHandshakeNegativeTest > Ein reiner TLS-1_3-Client scheitert im TDS-7_4-Pfad` und `TlsLoopbackTest > both versions offered negotiate by path and the path decides about ALPN` rot | rot, dazu `TelemetryRedactionTest > Die Kennung steht gekuerzt und nur im Ereignis der Annahme` |

**Messungen nach der Umsetzung.** Alle mit dem Stand des Zweigs, Server ohne JVM-Option (Startzeile `java -XX:+ExitOnOutOfMemoryError -cp … MainKt`, im Prozess gelesen mit `/proc/1/cmdline`).

| Messung | Ergebnis |
|---|---|
| `make gates FORCE=1` | grün (Exit 0), einschließlich `make test` und der Selbsttests |
| `make kompatibilitaet KATEGORIE=Anmeldung` (.NET, `Microsoft.Data.SqlClient` 7.1.1) | 19 von 19 grün, davon 2 `Tds80_Strict_*` (TDS 8.0, TLS 1.3) und 17 im 7.4-Pfad (TLS 1.2); Last 8,0 zu Beginn, 49 s (34 s Bau, 15 s Tests). M8 war 2 von 19. |
| `make kompatibilitaet` (Szenario) | 13 von 13 grün, 12 s |
| `make kompatibilitaet KATEGORIE=Luecken` / `Synchronitaet` | 7 von 7 / 4 von 4 grün, 13 s / 11 s |
| `make simulations-integration` | 4 von 4 grün, 41 s |
| `make smoke` | grün, einschließlich `ServerSmokeTest > Ein Prozess ohne JVM-Option startet mit TLS` |
| d-migrate 1.12.0 (Image-Digest wie oben) mit `encrypt=true&trustServerCertificate=true`, ohne `sslProtocol` und ohne JVM-Option, gegen `make testserver-up` (`REQUIRED`, `tdswire-tls`, Port 14351, Image `tdswire-testserver:tls`, ID `sha256:8c5eb1aee468279281633b02fd44724be876722c8c0fbf7cf99ea151aa2431b0`, Schlüsselspeicher `gueltig.p12`, pbkdf2-Benutzerliste mit 600000 Iterationen), Last 6,5 | Anmeldung gelingt; `schema reverse` endet mit Exit 4 und `Failed to initialize pool: Unsupported SQL.` (Hürde 2 des Parallel-Slice) |

Nicht gemessen bleibt, was die ADR als nicht gemessen führt: Wiederaufnahme mit dem Ticket, andere Treiber und Treiberversionen, ECDSA-Zertifikate, ein echter SQL Server. Die Messungen dieses Abschnitts ersetzen die Matrix V0 bis V3 nicht, sie messen den gebauten Stand (V2 ohne lokale Lockerung).
