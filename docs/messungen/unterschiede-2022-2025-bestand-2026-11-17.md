# Unterschiede zwischen SQL Server 2022 und 2025: Bestandsaufnahme der vorhandenen Messungen (2026-11-17)

Messprotokoll zu [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md) und [ADR-0028](../plan/adr/0028-referenzinstanz-fuer-tds-8-0.md) (`AGENTS.md` §5 Regel 7). Es hält fest, **was die vorhandenen Messungen des Repos über Unterschiede der beiden Referenzinstanzen sagen**, nicht, ob eine Anforderung belegt ist. **Es misst nichts Neues:** alle Zeilen stammen aus Dateien, die am 2026-11-17 im Repo liegen (Stand `84b8b561`); es lief kein Bau, kein Messlauf und kein Docker gegen SQL-Server-Instanzen. Eigene Arbeit dieser Datei ist das Lesen und Gegenüberstellen; wo sie Bytes der Ablagen selbst dekodiert hat, steht das an der Zeile.

Anlass: Der Nutzer fragte am 2026-11-17, ob die Bibliothek einstellbar nachahmen kann, ob sie 2022 oder 2025 ist (Profil), und ob TDS 8.0 über 2025 als Referenz laufen kann („nicht wichtig, ob 2022 TDS 8 kann; wir nehmen 2025“). Diese Datei beantwortet die Fragen nicht, sie liefert den Bestand dafür.

## Quellen und Instanzen

Die Instanzen sind in [referenzabgleich](referenzabgleich.md) (Zeilen 5 bis 12) beschrieben; alle Aussagen gelten für diese Stände und sind Einzelmessungen eines Hosts (Bedingungen dort).

| Stufe | Server laut Log | Image-Digest |
|---|---|---|
| `referenzinstanz-2022` | SQL Server 2022 (RTM-CU27-GDR) 16.0.4305.5, Ubuntu 22.04 | `sha256:cf8f0ab2…` |
| `referenzinstanz-2025` | SQL Server 2025 (RTM-CU10) 17.0.5035.5, Ubuntu 24.04 | `sha256:245f6b1c…` |

Gelesene Quellen: `docs/messungen/referenzabgleich.md` (ganz), `docs/messungen/zeilenform-nbcrow-2026-11-17.md`, `docs/messungen/prelogin-serverversion-2026-11-05.md`, die Ablagen unter `referenztest-dotnet/Mitschnitt/` (`aufnahmen/{2022,2025}`, `messung/{2022,2025}`, `messung/bulk-merge-katalog/{2022,2025}`, `messung/jdbc-d-migrate`), `referenztest-dotnet/Vergleich/{mitschnitt-ausnahmen.tsv,live-ausnahmen.tsv}`, die Belegtests `MessungBelegTests.cs` und `MessungBulkBelegTests.cs`, [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0028](../plan/adr/0028-referenzinstanz-fuer-tds-8-0.md), [ADR-0053](../plan/adr/0053-lesart-zu-tds-8-0-in-adr-0007-und-adr-0008-folgt-adr-0028.md) (`Proposed`), die [Kompatibilitätsmatrix](../plan/kompatibilitaetsmatrix.md), `spec/spezifikation.md` und `src/main`.

**Methode des Vergleichs der Ablagen (lesend, `diff` auf den vorhandenen Dateien).** Je Szenario liegen zwei Dateien mit gleicher Aufnahmemethode (TLS-beendender Proxy, `Encrypt=Mandatory`, TLS 1.2). Zeilen `nachricht c|s …` tragen die Bytes je Nachricht. Ergebnis des Abgleichs der Dateien beider Instanzen:

| Ablage | Dateien | Zeilen, die sich unterscheiden |
|---|---|---|
| `messung/<Instanz>/` (22 + 4 Szenarien der Messreihe am Draht) | 26 | 24 Dateien: genau 10 Zeilen im `diff` (je Seite 5, siehe unten); `sitzung-crc0`: 12 (eine weitere Zeile je Seite, die Antwort auf `Cancel()` nach 50 Zeilen, in der 2025 mehr Zeilen vor dem DONE trägt: 17424 gegen 16776 Hexzeichen); `sitzung-crc1`: 14 (diese Zeile und die Folgeabfrage mit SESSIONSTATE) |
| `messung/bulk-merge-katalog/<Instanz>/` | 23 | 22 Dateien genau 10 Zeilen im `diff`; `katalog-struktur`: 18 (Transaktionsdeskriptor, siehe ENVCHANGE) |
| `aufnahmen/<Instanz>/` (Mitschnitt-Lauf, ohne Anmeldung) | 17 | 12 Dateien 4 (nur Kopfzeilen), 5 Dateien 8 (die Szenarien `cancel-*` und `timeout-wartend`, SESSIONSTATE) |

Die 5 Zeilen je Seite sind: die zwei Kopfzeilen `instanz` und `image`, die PRELOGIN-Anfrage des Clients (Zufallswert), die PRELOGIN-Antwort des Servers und die Anmeldeantwort mit LOGINACK und FEATUREEXTACK; die Auswertung der Felder (Tokenfolge, Statusbits, Werte) ist in den 24 und 22 Dateien gleich (das stützt die Belegtests `Ausser_SESSIONSTATE_nach_Cancel_sind_alle_Felder_beider_Instanzen_gleich_bis_auf_Version_und_Zufallswerte` in `MessungBelegTests.cs`, Zeile 345 ff., und die Aussage „in allen Austauschen außer der Anmeldung gleich“ in [referenzabgleich](referenzabgleich.md) Zeile 163). Die Zeilenzahlen sind eine Zählung dieser Datei, kein Messwert der Läufe.

## 1. Gegenüberstellung

„Quelle“ nennt Datei und Zeile (Zeilennummern von `docs/messungen/referenzabgleich.md`, wenn nur eine Kennung steht) oder die Messkennung. Spalte „Stand“: **gleich** (beide gemessen, Befund gleich), **verschieden** (beide gemessen, Befund verschieden), **nicht verglichen** (an einer Instanz oder an keiner gemessen).

### 1.1 Verbindung, TLS, TDS 8.0, Version

| Aspekt | 2022 | 2025 | Quelle | Stand |
|---|---|---|---|---|
| `Encrypt=Strict` (TDS 8.0) mit selbst erzeugtem Zertifikat über `tlscert` und `tlskey` | scheitert bei vier Zertifikatsvarianten: Client `Number 0, Class 20, State 0` („SSL Provider, error: 31 - Encryption(ssl/tls) handshake failed“); Sonde `openssl s_client`: „unexpected eof while reading“; Log: „A valid TLS certificate is not configured to accept strict (TDS 8.0 and above) connections. The connection has been closed.“ | gelingt ohne Zusatzeinstellung; Sonde schließt den Handschlag ab (`TLSv1.3`, `TLS_AES_256_GCM_SHA384`) | B-3 (Zeile 99), B-4 (Zeile 100), B-6 (Zeile 102), Lauf `2022-tds8`, `2025-tds8`; [ADR-0028](../plan/adr/0028-referenzinstanz-fuer-tds-8-0.md) | verschieden (Ursache an 2022 nicht geklärt, ob Tag oder Konfiguration; Zeile 106) |
| `Encrypt=Mandatory` (TDS 7.4) | gelingt | gelingt | B-3, B-4 | gleich |
| Erlaubte TLS-Versionen laut Log des Servers | 1.0 bis 1.2 | 1.0 bis 1.3 | B-3, B-4 | verschieden (Log, nicht Wirkung am Client) |
| TLS-Version, die der **Referenzclient** aushandelt | nicht beobachtet | nicht beobachtet (die Sonde OpenSSL zeigt 1.3, das ist die der Sonde) | B-4; „Grenzen der Messung“ Zeile 245 | nicht verglichen |
| Messreihen mit Proxy: TLS-Version | 1.2 auf beiden Strecken gesetzt | 1.2 gesetzt | Zeile 112 | nicht verglichen (festgelegt, nicht ausgehandelt) |
| `network.forcestrict` in `mssql-conf list` des Images | nicht vorhanden (ebenso `network.subjectalternativename`) | vorhanden (beide) | Zeile 106 (Image), Herstellerdokumentation „Applies to: SQL Server 2025 (17.x) and later“, abgerufen 2026-11-01 | verschieden |
| Wirkung von `network.forcestrict = 1` | nicht gemessen (Einstellung im Image nicht vorhanden) | `Strict` gelingt, `Mandatory` scheitert (`Number 0, Class 20`, „error occurred during the pre-login handshake“); Log: „configured to accept only strict (TDS 8.0 and above) connections“; `Strict` hängt für `2025-latest` nicht an der Einstellung | B-5 (Zeile 101), Lauf `2025-tds8-forcestrict`; `ReferenzTds8Tests.cs` (`Mandatory_verhaelt_sich_wie_erwartet`) | nicht verglichen (nur 2025) |
| TDS 8.0 mit Standardzertifikat des Servers ohne `tlscert` | nicht gemessen | nicht gemessen | Zeile 245 | nicht verglichen |
| Pooling, Fehler- und ATTENTION-Framing unter TDS 8.0 | nicht gemessen | nicht gemessen | [ADR-0053](../plan/adr/0053-lesart-zu-tds-8-0-in-adr-0007-und-adr-0008-folgt-adr-0028.md) (Kontext), Zeile 138, 245 | nicht verglichen |
| Versionstext im Log des Laufs | „Microsoft SQL Server 2022 (RTM-CU27-GDR) (KB5126934) - 16.0.4305.5 (X64)“ (Kopf jeder Ablage) | „Microsoft SQL Server 2025 (RTM-CU10) (KB5125471) - 17.0.5035.5 (X64)“ | Zeile 2 jeder `.messung`-Datei; Zeilen 9 und 10 | verschieden |
| `ServerVersion` am Client | `16.00.4305` | `17.00.5035` | B-1 (Zeile 97) | verschieden |
| Versionstext im Live-Lauf | nur „vorhanden“ verglichen | nur „vorhanden“ | Zeile 40; SPEC-045 (Normierung) | nicht verglichen |
| `SERVERPROPERTY`, `@@VERSION` | nicht als Antwort gemessen (siehe Abschnitt 3: `SERVERPROPERTY('EngineEdition')` steht nur im Text einer Probe, den der Server erkennt) | nicht gemessen | `grep` auf Messprotokoll und Ablagen: keine Antwortzeile | nicht verglichen |

### 1.2 PRELOGIN und Anmeldung

| Aspekt | 2022 | 2025 | Quelle | Stand |
|---|---|---|---|---|
| PRELOGIN: Verschlüsselung | Client fragt `ENCRYPT_ON`, Server antwortet `ENCRYPT_ON` | gleich | W-8 (Zeile 129) | gleich |
| PRELOGIN-Antwort, Option VERSION (Bytes) | `10 00 10 d1 00 00` | `11 00 13 ab 00 00` | **eigene Dekodierung** der Zeile 10 von `messung/{2022,2025}/anmeldung-crc0.messung` (und jeder weiteren Ablage); gelesen als Haupt `0x10`=16, Neben 0, Build `0x10d1`=4305 bzw. Haupt `0x11`=17, Build `0x13ab`=5035, Unterbuild 0; die Belegtests und das Messprotokoll werten das Feld nicht aus (Zeile 130: „bis auf Versionen und Zufallswerte“) | verschieden (Bytes lesen sich wie die Versionen der Instanzen; die Zuordnung ist Lesart) |
| LOGINACK: TDS-Version | `74 00 00 04` (7.4) | `74 00 00 04` | W-8 (Zeile 129) | gleich |
| LOGINACK: Programmversion (letzte vier Bytes des Tokens) | `10 00 10 d1` | `11 00 13 ab` | **eigene Dekodierung** der Zeile 12 beider `anmeldung-crc0.messung`; dieselben Werte wie in der PRELOGIN-Antwort; kein Feld, das ein Messprotokoll ausweist | verschieden (Lesart wie oben) |
| Tokenfolge der Anmeldeantwort | `ENVCHANGE(1) INFO(5701) ENVCHANGE(7) ENVCHANGE(2) INFO(5703) LOGINACK ENVCHANGE(4) FEATUREEXTACK DONE`, DONE `0x0000` | gleich | W-8; `MessungBelegTests.cs` Zeilen 295 bis 311 | gleich |
| ENVCHANGE der Anmeldung | Datenbank `master` → `tdswire_live`, Sprache `us_english`, Paketgröße `4096` → `8000` | gleich | W-8 | gleich |
| LOGIN7 (Client): Merkmalsliste | `0x04`, `0x05`, `0x09`, `0x0a`, `0x0b`, `0x0d`, `0x0e`, `0x0f`, `0x10`; mit `ConnectRetryCount=1` zusätzlich `0x01` | gleich (Client sendet unabhängig von der Instanz) | W-8 | gleich |
| FEATUREEXTACK (Server) ohne `ConnectRetryCount` | `0x04`, `0x09`, `0x0a`, `0x0b` | `0x04`, `0x09`, `0x0a`, `0x0b` **und `0x0d`, `0x0e`** | W-8 (Zeile 129); `MessungBelegTests.cs` Zeilen 306 bis 308 (`Assert.Equal(stufe == "2025", …)`); Aufnahme mssql-jdbc: `[0x4 0x9 0xa 0xb 0x1]` (58 Antworten) gegenüber `[0x4 0x9 0xa 0xb 0xe 0xd 0x1]` (47 Antworten) in `jdbc-d-migrate/{2022,2025}.aufnahme.txt` (Zählung dieser Datei) | verschieden; was `0x0d` und `0x0e` bedeuten, steht im Repo nicht |
| FEATUREEXTACK mit `ConnectRetryCount=1` | zusätzlich `0x01` vorn mit Daten (`0x0009`) | gleich | W-8; `MessungBelegTests.cs` Zeile 302 | gleich |
| Fehlerantwort der Anmeldung (falsches Kennwort, unbekannter Benutzer) | Client: `Number 18456, Class 14, State 1`, „Login failed for user '<Name>'.“ | gleich | A-1 (Zeile 20) | gleich |
| Interner State im Log des Servers (8 bei falschem Kennwort, 5 bei unbekanntem Benutzer) | State 8 und 5 | State 8 und 5 | B-2 (Zeile 98) | gleich |
| `State 7` („An error occurred while evaluating the password“), solange die Systemdatenbanken noch aktualisiert werden | in vier Läufen gesehen, keiner abgelegt, Instanz nicht genannt | siehe links | B-2 | nicht verglichen |
| `PacketSize` | 8000 | 8000 | Zeile 40 | gleich |

### 1.3 Antworten, Tokenfolgen, Statusbits

| Aspekt | 2022 | 2025 | Quelle | Stand |
|---|---|---|---|---|
| Live-Lauf, 25 Schritte (Ergebnisse des Referenzclients) | je Schritt dieselben gelisteten Paare | gleich; **eine Ausnahme: `cancel-mit-folgeabfrage`** lieferte an beiden Instanzen zwei verschiedene Texte (A-7), beide Instanzen zeigten beide Varianten | Zeilen 36, 38 | gleich (Text je Lauf nicht deterministisch) |
| Mitschnitt-Lauf, 17 Szenarien: Tokenfolge, COLMETADATA, Zeilen, DONE-Bits, `curCmd`, ERROR, INFO, ENVCHANGE, RETURNSTATUS/RETURNVALUE | gleich bis auf die Zeilen unten | gleich | Zeile 50 (324 Felder je Teillauf, davon 69 abweichend vom Server dieses Repos in 2022); `aufnahmen/*` (12 Dateien nur Kopfzeilen verschieden) | gleich |
| Tokenfolge bei `Cancel()` und Frist (Szenarien `cancel-streaming`, `cancel-wartend`, `timeout-wartend`, `cancel-kurz-vor-timeout`, `cancel-kurz-nach-timeout`) | enthält `SESSIONSTATE` vor dem `DONE` (Mitschnitt mit dem Client-Standard `ConnectRetryCount=1`) | kein `SESSIONSTATE` | A-18 (Zeile 71), Ergebnis 5 (Zeile 58); `mitschnitt-ausnahmen.tsv` | verschieden |
| SESSIONSTATE ohne Verhandlung (`ConnectRetryCount=0`) | keiner (10 Antworten) | keiner | W-9 (Zeile 130), `sitzung-crc0` | gleich |
| SESSIONSTATE mit Verhandlung nach `SET DATEFORMAT dmy` (Zustand `2:2=0x0702`) und `SET LANGUAGE German` (`2:2=0x0102`) | gesendet | gesendet | W-9; `MessungBelegTests.cs` (`Ohne_Verhandlung_sendet_keine_Instanz_SESSIONSTATE_mit_Verhandlung_beide_nach_SET`) | gleich |
| SESSIONSTATE nach `Cancel()` nach 50 Zeilen und in der Folgeabfrage | gesendet, Kennung 254 (Folge 3 nicht wiederherstellbar `254:4=0x03000000`, Folge 4 wiederherstellbar `254:4=0x00000000`) | **nicht gesendet** | W-9; `MessungBelegTests.cs` (`Nach_Cancel_und_in_der_Folgeabfrage_sendet_nur_die_Instanz_2022_SESSIONSTATE`, in der Datei bis auf `sitzung-crc1/06` und `/07` alle Felder gleich); Ablagen `sitzung-crc1.messung` Zeilen 28 | verschieden; Ursache nicht geklärt (Spec `spezifikation.md` Zeile 1947: „Lücke ohne Träger“); Bedeutung der Kennung 254 nicht festgestellt (Zeile 138) |
| SESSIONSTATE in der Aufnahme mssql-jdbc / d-migrate (19 Befehle) | 140 Vorkommen des Wortes in `2022.aufnahme.txt` bei 58 Verbindungen | 129 Vorkommen bei 47 Verbindungen | Zählung dieser Datei mit `grep -c` auf `jdbc-d-migrate/`; nicht ausgewertet, ob Vorkommen und Verbindungen zusammenhängen | nicht verglichen (Zahlen verschieden, Ursache nicht untersucht) |
| Zahl der Verbindungen und Zeilen der Aufnahme mssql-jdbc / d-migrate | 58 Verbindungen, 2169 Zeilen | 47 Verbindungen, 2026 Zeilen | `jdbc-d-migrate/*.aufnahme.txt`; Ausgaben und Exit-Codes der 19 Befehle gleich (18 mal Exit 0, 1 mal Exit 5, Ausgabezeilen `ausgabe:` bis auf Run-ID und Dauer gleich, nach Zählung dieser Datei) | verschieden in der Zahl der Verbindungen; Ursache nicht untersucht; Ergebnis der Befehle gleich |
| Statusbits der DONE-Familie | `COUNT`, `MORE\|COUNT`, `ERROR`, `MORE\|ERROR`, ohne Bits | gleich | Ergebnis 2 (Zeile 55); W-1 bis W-3, W-11, W-13 | gleich |
| `curCmd` (SELECT `0x00c1` … CREATE TABLE `0x00c6` …, DONEPROC `0x00e0`, `0x00fd`) | wie W-6 | gleich | W-6 (Zeile 127); M-20, M-23 | gleich |
| ENVCHANGE nach der Anmeldung in den 17 Szenarien | keine | keine | Ergebnis 4 (Zeile 57) | gleich |
| ENVCHANGE Typ 18 bei `RESETCONNECTION` | Typ 18, neue und alte Werte leer, erstes Token | gleich | W-7 (Zeile 128) | gleich |
| ENVCHANGE Typ 8 (Beginn der Transaktion) und Typ 9 | Deskriptor `01 00 00 00 35 00 00 00` (so in `katalog-struktur` der Ablage 2022) | Deskriptor `01 00 00 00 33 00 00 00` | `spec/spezifikation.md` Zeilen 833 bis 835 („Wert der Instanz 2022 (2025: `33` im fünften Byte)“); eigene Gegenlesung: die Hexfolge `e30b0008 08 0100000035000000 00` (2022) und `e30b0008 08 0100000033000000 00` (2025) steht in je einer Zeile der beiden Ablagen `katalog-struktur.messung`; Aufnahme mssql-jdbc: je Instanz 27 Typ 8 und 26 Typ 9 ([ddl-abschluss…](ddl-abschluss-und-transaktionszustand-d-migrate-2026-11-08.md) Zeile 70) | verschieden (Wert; die Art der Token gleich); der Server wählt ihn je Transaktion |
| Zeilenform ROW/NBCROW | je Zeile nach der Regel „NBCROW genau dann, wenn kürzer“ | **byte-gleich** in allen 17 Austauschen | M-37 bis M-41, [zeilenform-nbcrow](zeilenform-nbcrow-2026-11-17.md) | gleich |
| Fehlernummern und Texte der Strukturabweisung (2714/State 6, 2705/State 4, 4902, 1781/1750, 3728, 3733/3727, 3701, 3723) | gemessen | gleich | M-19; `spezifikation.md` Zeilen 1165 bis 1167, 1666 bis 1681 („2022 und 2025“) | gleich |
| Fehler `8179` (`sp_unprepare` State 8, `sp_execute` State 4), 208, 8134, 2627, 2628 | gemessen | gleich | M-4, M-6, M-7, M-12, M-15; Zeile 91 | gleich |
| ERROR-Token bei Abbruch durch `Cancel()` oder Frist | kein ERROR-Token; zwei Nachrichten `DONE[ERROR]`, `DONE[ATTN]` | gleich | Ergebnis 5 (Zeile 58), W-9 | gleich |
| ATTENTION des Clients (ein ATTENTION bei Frist und `Cancel()` kurz hintereinander; keines bei `Cancel()` im Leerlauf) | vier Aufnahmen, je eine | gleich | Ergebnis 5 | gleich (Einzelmessungen) |
| Mehrere RPCs in einer Nachricht (Trennbyte `0xFF`, DONEPROC `MORE\|0x80`, Ordinal je Aufruf, Fehler im zweiten von drei Aufrufen, RETURNSTATUS 2627) | W-10 bis W-13 | gleich | W-10 bis W-13 (Zeilen 150 bis 153), `MessungBelegTests.cs` Tests mit `[MemberData(nameof(Stufen))]` | gleich |
| Ordinal der RETURNVALUE = Position in der Aufrufliste | W-5 | gleich | W-5 (Zeile 126), M-24 | gleich |
| Fehler im RPC (RETURNSTATUS = Fehlernummer oder 0, -6 in Prozeduren) | W-1 bis W-3 | gleich | Zeilen 122 bis 124 | gleich |

### 1.4 Katalog, Typen, Bulk, MERGE

| Aspekt | 2022 | 2025 | Quelle | Stand |
|---|---|---|---|---|
| Katalogantworten für `schema reverse` (zehn Standardabfragen: `sys.tables`, `sys.columns`, `sys.indexes`, `sys.key_constraints`, `sys.foreign_keys`, `sys.schemas`, `INFORMATION_SCHEMA.TABLES`/`COLUMNS`, `SCHEMA_NAME()`, `DB_NAME()`; Spaltennamen, Typen, Zeilenzahlen) | gemessen | „Beide Instanzen lieferten in allen Zeilen dasselbe“ | M-1 (Zeile 79 und 83); `sys.schemas` 13 Zeilen | gleich (nur diese zehn Abfragen) |
| Katalogspalten der 17 Texte von d-migrate (`max_length` smallint, `precision`/`scale` tinyint, `is_nullable` BITN, `seed_value` sql_variant, …) | gemessen | gleich | M-16 bis M-18, M-21 (Zeilen 175 bis 180); `katalog-abfragen` und `katalog-struktur` in beiden Ablagen: Auswertung in allen Austauschen außer der Anmeldung gleich (Zeile 163) | gleich |
| Typnamen und Maße in `sys.columns`/`sys.types` (`time` 5/16/7, `datetime2` 8/27/7, `nvarchar(max)` -1, …) | gemessen | gleich | M-17, M-18 | gleich |
| `INFORMATION_SCHEMA.COLUMNS` mit weiteren Spalten, Sichten außerhalb der gemessenen | nicht gemessen | nicht gemessen | Zeile 83 („Abfragen von `schema reverse` selbst … nicht im Repo“) | nicht verglichen |
| Sichten für Sequenzen, Routinen, Sichten, Partitionen, Volltext | leer (kein Objekt angelegt) | leer | M-21 (Zeile 180) | nicht verglichen (kein Fall) |
| TYPE_INFO `decimal(10,2)`: `DECIMALN` mit Länge 17 | 17 | 17 | A-11 (Zeile 65), `mitschnitt-ausnahmen.tsv` | gleich |
| Massenladen `SqlBulkCopy`: Probe, `insert bulk`, Nachricht 7, `DONE` `0x00f0` mit Zähler, Status `0x04` im ersten Paket, leere Quelle, Verstoß (Client-seitig: ATTENTION; Schlüssel serverseitig: 2627, Class 14, Bestand unverändert) | M-8 bis M-12 | gleich | M-8 bis M-12 (Zeilen 167 bis 171); Zeile 163 | gleich |
| Massenladen: abgebrochene Verbindung bei zusammengefassten oder geteilten Paketen im Proxy | `bulk-mehrere-nachrichten` bei Weitergabe je Paket 0 von 40 rot | je Lesevorgang 14 von 40 rot (nur an 2025 gemessen); je Paket 0 von 40 | Zeile 163 | nicht verglichen (Weitergabe je Lesevorgang nur an 2025 gemessen; Mechanismus nicht festgestellt) |
| Namen mit Schema und Sonderzeichen im Massenladen (M-33 bis M-35), feste Typen in Nachricht 7 (M-36) | gemessen | gleich; Nachricht 7 byte-gleich (M-36) | Zeilen 184 bis 187 | gleich |
| `MERGE … OUTPUT $action`: Tokenfolge, `$action` nvarchar(10) nicht nullbar, Befehl `0x0117`, COLMETADATA ohne ROW, Fehlerfälle | M-13 bis M-15 | gleich | M-13 bis M-15, M-26 (Zeilen 172 bis 174, 234) | gleich |
| `sp_unprepare` mit vielen Nummern (bis 2000), `curCmd` | M-22, M-23 | gleich | Zeilen 181, 182 | gleich |
| Flag-Feld RETURNVALUE `0x0000` für zehn Typen | M-24 | gleich | Zeile 183 | gleich |
| `ON DELETE` (Fremdschlüssel) mit d-migrate | gemessen | **nicht gemessen** | [fremdschluessel-on-delete-d-migrate-2026-11-12](fremdschluessel-on-delete-d-migrate-2026-11-12.md) Zeile 61 („nur SQL Server 2022, nicht 2025“) | nicht verglichen |
| JSON-, Vektor- und weitere neue Typen von 2025 | nicht gemessen | nicht gemessen | keine Treffer in Messprotokollen, Ablagen und `spec/` | nicht verglichen |

### 1.5 Laufzeit und Betrieb der Referenzinstanzen

| Aspekt | 2022 | 2025 | Quelle | Stand |
|---|---|---|---|---|
| Bau und Start der Referenzinstanz im Live-Lauf | 13 bis 22 s | 13 bis 22 s, einmal 55 s | Zeile 36 (Einzelmessungen, Last 8 bis 9) | nicht verglichen (Einzelmessungen unter Last) |
| Betriebssystem des Images | Ubuntu 22.04 | Ubuntu 24.04 | Zeilen 9, 10 | verschieden |
| Image-Größe, Plattenbedarf | im [ADR-0028](../plan/adr/0028-referenzinstanz-fuer-tds-8-0.md) für das zweite Image „2,4 GB Platte, rund 636 MB Netz“ (Option A gegen B) | siehe links | [ADR-0028](../plan/adr/0028-referenzinstanz-fuer-tds-8-0.md) Alternativen | nicht verglichen |

## 2. Regeln der Ausnahmeliste, die nur eine Instanz betreffen

Quelle: `referenztest-dotnet/Vergleich/mitschnitt-ausnahmen.tsv` (Kennung `<Szenario>/<NN>/<Feld>@<Instanz>`). Die Datei führt **40 Regeln** (ohne Kommentarzeilen), davon **10 mit `@2022`**, **5 mit `@2025`**, 25 für beide Instanzen. Die Liste des Live-Laufs (`live-ausnahmen.tsv`) hat keine Regel mit `@2022` oder `@2025`; ihre zwei Regeln für `cancel-mit-folgeabfrage` führen die zwei Textvarianten und gelten für beide Instanzen (Zeile 38).

**Abweichung in der Quelle:** [referenzabgleich](referenzabgleich.md) Zeile 50 nennt „74 Regeln, 16 davon nur für eine Instanz“. Die Datei zählt heute 40 Regeln und 15 (10 plus 5) mit Instanz. Warum die Prosa 74 und 16 nennt, ist nicht untersucht (die Zahl stammt aus einem früheren Lauf oder einer anderen Zählung); diese Datei gibt die Zählung der TSV wieder.

| Kennung | Instanz | Referenz (Auszug) | Server dieses Repos | Regel | Grund laut Datei |
|---|---|---|---|---|---|
| `cancel-kurz-nach-timeout/01/tokenfolge` | 2022 | `SESSIONSTATE DONE \|\| DONE` | `ERROR DONE \|\| DONE` | A-15 | WAITFOR außerhalb der erkannten Formen; die Referenz sendet kein ERROR-Token, nur `DONE`; bei 2022 zusätzlich SESSIONSTATE |
| `cancel-kurz-nach-timeout/01/tokenfolge` | 2025 | `DONE \|\| DONE` | `ERROR DONE \|\| DONE` | A-15 | wie oben, ohne SESSIONSTATE |
| `cancel-kurz-nach-timeout/02/tokenfolge` | 2022 | `COLMETADATA ROW+ SESSIONSTATE DONE` | `COLMETADATA ROW+ DONE` | A-18 | 2022 sendet bei `ConnectRetryCount=1` SESSIONSTATE um den Abbruch (W-8, W-9) |
| `cancel-kurz-vor-timeout/01/tokenfolge` | 2022 | `SESSIONSTATE DONE \|\| DONE` | `ERROR DONE \|\| DONE` | A-15 | wie `cancel-kurz-nach-timeout/01` |
| `cancel-kurz-vor-timeout/01/tokenfolge` | 2025 | `DONE \|\| DONE` | `ERROR DONE \|\| DONE` | A-15 | wie oben |
| `cancel-kurz-vor-timeout/02/tokenfolge` | 2022 | `COLMETADATA ROW+ SESSIONSTATE DONE` | `COLMETADATA ROW+ DONE` | A-18 | wie oben |
| `cancel-streaming/01/tokenfolge` | 2022 | `COLMETADATA ROW+ SESSIONSTATE DONE \|\| DONE` | `COLMETADATA ROW+ DONE` | A-13 | zwei Nachrichten der Referenz gegen eine des Servers ([ADR-0027](../plan/adr/0027-abbruch-und-zuruecksetzen.md) baut die zweite nicht), dazu SESSIONSTATE bei 2022 |
| `cancel-streaming/01/tokenfolge` | 2025 | `COLMETADATA ROW+ DONE \|\| DONE` | `COLMETADATA ROW+ DONE` | A-13 | wie oben, ohne SESSIONSTATE |
| `cancel-streaming/02/tokenfolge` | 2022 | `COLMETADATA ROW+ SESSIONSTATE DONE` | `COLMETADATA ROW+ DONE` | A-18 | SESSIONSTATE in der Folgeabfrage nach dem Abbruch |
| `cancel-wartend/01/tokenfolge` | 2022 | `SESSIONSTATE DONE \|\| DONE` | `ERROR DONE \|\| DONE` | A-15 | wie `cancel-kurz-nach-timeout/01` |
| `cancel-wartend/01/tokenfolge` | 2025 | `DONE \|\| DONE` | `ERROR DONE \|\| DONE` | A-15 | wie oben |
| `cancel-wartend/02/tokenfolge` | 2022 | `COLMETADATA ROW+ SESSIONSTATE DONE` | `COLMETADATA ROW+ DONE` | A-18 | wie oben |
| `timeout-wartend/01/tokenfolge` | 2022 | `SESSIONSTATE DONE \|\| DONE` | `ERROR DONE \|\| DONE` | A-15 | wie oben |
| `timeout-wartend/01/tokenfolge` | 2025 | `DONE \|\| DONE` | `ERROR DONE \|\| DONE` | A-15 | wie oben |
| `timeout-wartend/02/tokenfolge` | 2022 | `COLMETADATA ROW+ SESSIONSTATE DONE` | `COLMETADATA ROW+ DONE` | A-18 | wie oben |

Alle 15 Regeln betreffen das Feld `tokenfolge` und denselben Unterschied: **SESSIONSTATE im Verlauf nach Abbruch oder Frist** nur bei 2022; die Instanz-Kennung `@…` steht dort, weil Referenz und Server ohne das Token gleich lang wären. Ein anderer Unterschied zwischen den Instanzen steht in der Ausnahmeliste nicht. Der Grund der A-18-Regeln ist in der Datei auf den Befund W-8/W-9 gezogen (Verhandlung der Sitzungswiederaufnahme ist Voraussetzung; der Unterschied liegt in den Austauschen um den Abbruch).

## 3. Was der Server dieser Bibliothek heute fest verdrahtet hat (Fundstellen, keine Bewertung)

Dateien unter `src/main/kotlin/io/github/pt9912/tdswire/`; `testserver/` und `src/integrator` führen keine Versionsangabe (Suche nach `16.0`, `17.0`, `@@VERSION`, `ProductVersion`, `Microsoft SQL Server`, `2022`, `2025` ohne Treffer außer zwei Datumswerten in Testdaten).

| Fundstelle | Festes | Kennzeichnung im Quelltext |
|---|---|---|
| `protocol/session/PreloginNegotiation.kt` Zeilen 87 bis 91 (`SERVER_MAJOR` 16, `SERVER_MINOR` 0, `SERVER_BUILD` 1000, `SERVER_SUB_BUILD` 6) und Zeile 34 (KDoc: „VERSION `16.0.1000.6` … sind Festlegung“) | VERSION der PRELOGIN-Antwort `16.0.1000.6` | „Setzung ohne Messung am echten Server“; Herkunft: ging in einer Sonde mit mssql-jdbc 13.4.0 durch ([prelogin-serverversion](prelogin-serverversion-2026-11-05.md): `0.0.0.0` scheiterte am Treiber mit „Server major version:0 is not supported“) |
| `protocol/prelogin/PreloginMessage.kt` Zeilen 36 bis 50 (`version(major, minor, build, subBuild)`) | Byteordnung des Unterbuilds Big-Endian | KDoc: „Festlegung ohne Orakel“ |
| `protocol/session/LoginResponses.kt` Zeile 41 (`PROGRAM_VERSION = 0x01000000`) und Zeile 40 (`PROGRAM_NAME = "tdswire"`), Zeile 63 (`progVersion`) | LOGINACK-Programmname und -version | KDoc Zeilen 25 bis 29: „Festlegung ohne Messung“ |
| `protocol/session/LoginResponses.kt` Zeilen 37, 38, 61 (`TDS_7_4 = 0x74000004`, `TDS_8_0 = 0x08000000`, Wahl nach `HandshakeOrder`) | TDS-Version in LOGINACK; 7.4 bei PRELOGIN zuerst, 8.0 bei TLS zuerst | KDoc nennt die TDS-Version je Reihenfolge als Festlegung; `HandshakeOrder` in `protocol/session/ConnectionState.kt` Zeile 60 |
| `protocol/session/LoginResponses.kt` Zeilen 52 bis 65 (`acceptance`) | Tokenfolge der Annahme: ENVCHANGE Datenbank, Packet Size, SQL-Collation (`LATIN1_GENERAL_CI_AS`), LOGINACK, DONE (`curCmd 0`, ohne Bits); kein FEATUREEXTACK, kein INFO 5701/5703, kein ENVCHANGE Sprache | KDoc: „Festlegung ohne Messung“ |
| `protocol/session/LoginResponses.kt` Zeile 36 (`DEFAULT_PACKET_SIZE = 4096`) | Standardpaketgröße | Setzung (Spec Zeile 1955: „Setzung, … nicht gemessen“) |
| `protocol/session/ConnectionState.kt` Zeilen 60 bis 67 (`HandshakeOrder`, `EncryptionPolicy`) | zwei Pfade (PRELOGIN zuerst, TLS zuerst) und zwei Richtlinien (`REQUIRED`, `TEST_UNENCRYPTED`); der Pfad folgt der Reihenfolge des Handschlags | Kopf von `PreloginNegotiation.kt` Zeilen 22 bis 36 |
| `transport/tls/TlsParameters.kt` Zeilen 21 bis 38; `transport/tls/TlsAcceptor.kt` Zeilen 46 bis 64 | erlaubte TLS-Versionen `TLSv1.3`, `TLSv1.2`; im TDS-7.4-Pfad höchstens 1.2; im TDS-8.0-Pfad ALPN `tds/8.0` | `spec/spezifikation.md` SPEC-023 (Zeilen 1916, 2061 bis 2066) |
| `simulation/BulkCopyForms.kt` Zeile 45 | Text einer Probe des Clients, der `SERVERPROPERTY('EngineEdition')` enthält; der Server erkennt ihn als Form, er beantwortet keine Abfrage von `SERVERPROPERTY` | KDoc der Form |
| Nicht gefunden in `src/main`: FEATUREEXTACK, SESSIONSTATE, `0x0d`/`0x0e` | der Server schreibt keine FEATUREEXTACK-Merkmale und kein SESSIONSTATE (Spec Zeile 1947: „vorerst nicht senden“, Nutzerentscheid 2026-11-06) | Spec |

## 4. Nicht gemessen

Im Repo **nicht** gemessen (auch nicht für eine Instanz; die Liste nennt Lücken, keine Befunde, und gibt kein Wissen aus eigener Kenntnis als Messung aus, `AGENTS.md` §3.8):

- **Bedeutung der FEATUREEXTACK-Merkmale `0x0d` und `0x0e`** (nur bei 2025 bestätigt); der Verfasser vermutet nach eigener Kenntnis von MS-TDS, dass sie zu neuen Datentypen von 2025 gehören (JSON, Vektor), **belegt ist das im Repo nicht**; Quelle wäre die Beschreibung von MS-TDS.
- Neue Typen von 2025 (JSON, Vektor und weitere): kein Szenario legt sie an; weder `COLMETADATA` noch Zeilen dazu; auch nicht, ob der Referenzclient 7.1.1 sie liest.
- Antworten auf `SELECT @@VERSION`, `SERVERPROPERTY('ProductVersion')`, `SERVERPROPERTY('EngineEdition')`, `sys.dm_*`; der Versionstext erscheint nur im Kopf der Ablagen und im Log.
- Weitere Unterschiede der Sichten `sys.*`, `INFORMATION_SCHEMA` außerhalb der zehn Standardabfragen und der 17 Texte von d-migrate 1.12.0 (zum Beispiel neue Spalten in Systemsichten von 2025, andere Reihenfolge der Zeilen, andere Zahl der Zeilen in `sys.schemas` bei anderem Aufbau).
- Fehlertexte und -nummern außerhalb der gelisteten (unbekannte Funktionen, Syntaxfehler, Konvertierungsfehler, Deadlock, Zeitüberschreitung auf dem Server).
- Verhalten unter `Encrypt=Strict` über das Annehmen der Verbindung hinaus (Fehler- und ATTENTION-Framing, Pooling, Katalog, Bulk); TLS-Version des Referenzclients; Standardzertifikat des Servers ohne `tlscert`; ob eine andere Konfiguration `Strict` an 2022 zulässt ([ADR-0053](../plan/adr/0053-lesart-zu-tds-8-0-in-adr-0007-und-adr-0008-folgt-adr-0028.md) Kontext, `Proposed`).
- Andere Clients als `Microsoft.Data.SqlClient` 7.1.1 und mssql-jdbc 13.4.0 (über d-migrate 1.12.0): zum Beispiel ob ein Treiber das Merkmal `0x0d`/`0x0e` oder die Hauptversion 17 annimmt. Die Aufnahme mssql-jdbc an 2025 lief ohne Abbruch an der Version (Exit-Codes gleich), geprüft ist das nur für diese 19 Befehle; ob der Treiber in der Aufnahme an 2025 eine Hauptversion 17 akzeptiert, folgt aus dem gleichen Ausgang, ist aber kein eigener Test.
- Die Ursache der SESSIONSTATE-Kennung 254 und ihres Fehlens bei 2025; die Ursache der unterschiedlichen Zahl der Verbindungen (58 gegen 47) in der Aufnahme mssql-jdbc.
- Mehrfachmessung: alle Zeilen sind Einzelmessungen; „2022 und 2025 gleich“ heißt gleich in je einer Aufnahme.
- `ON DELETE`/`ON UPDATE`-Fremdschlüssel an 2025; weitere Messreihen, die nur 2022 betrafen, sind nicht gesucht außer der genannten.
- Verhalten der Referenzinstanzen nach neuen Digests (`2022-latest` und `2025-latest` sind bewegliche Tags; der Digest hält nur den Stand).

## 5. Fragen an den Nutzer

Diese Datei empfiehlt nichts außer diesen Fragen; die Entscheidung liegt beim Nutzer.

**(a) Profil oder Schalter?** Braucht die Bibliothek ein Profil „2022“ oder „2025“ (die Bibliothek gibt sich als eine der beiden Versionen aus: PRELOGIN-VERSION, LOGINACK-Programmversion, FEATUREEXTACK-Merkmale, gegebenenfalls SESSIONSTATE nach Abbruch), oder reicht ein Schalter für die **akzeptierten Protokollversionen** (nur TDS 7.4, nur TDS 8.0, beide)? Heute bestimmt der Pfad des Handschlags die TDS-Version in LOGINACK (Abschnitt 3); die Version des Servers in PRELOGIN ist fest (`16.0.1000.6`, Setzung). Zu klären ist auch, ob die Bibliothek bei einem Profil „2025“ die Merkmale `0x0d` und `0x0e` bestätigen soll, ohne ihre Bedeutung zu implementieren (die Bedeutung ist im Repo nicht belegt, Abschnitt 4).

**(b) Welche Unterschiede aus Abschnitt 1 wären die Begründung für ein Profil?** Die gemessenen Unterschiede sind klein: Versionsbytes (PRELOGIN, LOGINACK), FEATUREEXTACK `0x0d`/`0x0e`, SESSIONSTATE (Kennung 254) nach Abbruch bei 2022, der Transaktionsdeskriptor (Wert), `forcestrict` und TLS-Versionen im Log, und `Encrypt=Strict` (nur 2025 angenommen). Alle gemessenen Antworten auf Anfragen (Tokenfolge, Statusbits, Typen, Katalog, Bulk, MERGE, Fehlernummern) sind gleich, bis auf das SESSIONSTATE nach Abbruch. Welche dieser Unterschiede soll ein Profil abbilden, damit es einen Kunden bedient, und welche reichen als Dokumentation? Belegt im Repo ist ein Client, der die Version der PRELOGIN-Antwort prüft: mssql-jdbc 13.4.0 lehnte `0.0.0.0` ab (Messprotokoll vom 2026-11-05); ein Client, der zwischen 16 und 17 unterscheidet, ist nicht belegt.

**(c) Neue öffentliche Fläche braucht vorab eine ADR.** Ein Profil oder ein Schalter wäre neue öffentliche Fläche (Konfiguration der Bibliothek); nach Vorgabe des Nutzers braucht sie vorab eine ADR. Soll der Planner die ADR vorbereiten, und zu welcher Frage: Profil, Schalter, oder zunächst nur die Referenz „2025 für TDS 8.0“ wie in [ADR-0028](../plan/adr/0028-referenzinstanz-fuer-tds-8-0.md) (schon `Accepted`)? Dazu steht [ADR-0053](../plan/adr/0053-lesart-zu-tds-8-0-in-adr-0007-und-adr-0008-folgt-adr-0028.md) im Status `Proposed` (Lesart zu TDS 8.0, ohne neue Fläche); soll sie vor einem Profil angenommen werden?

**(d) Gemessene Antworten für TDS 8.0 über 2025.** Der Nutzer sagt „wir nehmen 2025“: die Aufnahmen und Messreihen laufen mit `Encrypt=Mandatory` (TDS 7.4, TLS 1.2); für TDS 8.0 gibt es an 2025 nur das Annehmen der Verbindung (B-4, B-5). Soll TDS 8.0 an 2025 als Referenz für Tokenfolge und Framing gemessen werden (Mitschnitt unter TLS ohne Proxy ist mit dem heutigen Proxy nicht lesbar, Zeile 46)?
