# Sicherheitsprofil

TLS-Modi des Servers (`SPEC-010`, `SPEC-023`, `SPEC-028`, `SPEC-043`) für das Clientprofil in `contracts/client-profile.md`.

| Modus | Konfiguration | PRELOGIN-Antwort | Verhalten |
|---|---|---|---|
| `REQUIRED` (Standard), TDS 7.4 | `ENCRYPTION_POLICY=REQUIRED` | ENCRYPT_REQ oder ENCRYPT_ON (beide gemessen) | TLS-Handshake in TDS-Paketen vom Typ `0x12`, danach verschlüsseltes LOGIN7 |
| `REQUIRED`, TDS 8.0 | `ENCRYPTION_POLICY=REQUIRED`, Client `Encrypt=Strict` | — (TLS vor PRELOGIN) | TLS 1.3 ab dem ersten Byte, ALPN `tds/8.0` |
| unverschlüsselter Testmodus | ausdrückliche Konfiguration, im Log sichtbar | ENCRYPT_NOT_SUP | nur TDS 7.4; Clients mit `Encrypt=false` melden sich unverschlüsselt an, Clients mit `Encrypt=true` brechen ab |

## Zertifikate

- Der Client prüft die Kette: ein nicht vertrautes Zertifikat scheitert mit `UntrustedRoot`.
- `Encrypt=Strict` ignoriert `TrustServerCertificate`; das Zertifikat muss dem Client vertraut sein, der Name muss passen.
- `TrustServerCertificate=true` genügt im TDS-7.4-Pfad, auch bei einem nicht vertrauten Zertifikat.
- Tests nutzen ein Testzertifikat im Trust-Store des Testcontainers.

## Protokollversionen und Suiten

| Version | Suiten |
|---|---|
| TLS 1.3 | `TLS_AES_256_GCM_SHA384`, `TLS_AES_128_GCM_SHA256` |
| TLS 1.2 | `TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384`, `TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256` |

Mindestversion ist TLS 1.2. TLS 1.1 und älter, CBC-Suiten, RSA-Schlüsselaustausch ohne Forward Secrecy sowie 3DES und RC4 sind nicht zugelassen. Die Startprobe (`SPEC-043`) nutzt genau diese Konfiguration.

## Festlegungen der TLS-Schleife

Festlegungen dieses Profils, nicht am Referenzclient gemessen (die Tests laufen gegen eine JDK-`SSLEngine` im selben Prozess):

- Im TDS-8.0-Pfad weist der Server eine Gegenstelle ab, die `tds/8.0` nicht aushandelt (kein ALPN oder ein fremdes); es entsteht kein Kanal.
- Im TDS-7.4-Pfad verlangt der Server für Handshake-Bytes den Pakettyp `0x12`; ein anderer Typ, ein Header außerhalb des Katalogs oder eine Länge unter dem Header beendet den Handshake.
- Endet der Strom nach dem Handshake ohne `close_notify`, ist das ein Fehler, kein sauberes Ende.
- Fehlertexte der Schleife nennen den Ausnahmetyp oder eine feste Beschreibung, nie Zertifikats- oder Schlüsselinhalt.
- Ein KeyUpdate der Gegenstelle in TLS 1.3 beantwortet die Schleife (mit einer JDK-Gegenstelle getestet: der Server schreibt einen kurzen Record, ohne selbst Nutzdaten zu schreiben, und die Nachrichten laufen weiter; der Inhalt der Antwort ist nicht beobachtet); Neuverhandlung in TLS 1.2 ist nicht Gegenstand und nicht getestet.

## Schlüsselmaterial

Setzung dieses Profils, nicht gemessen und nicht aus dem Referenzclient abgeleitet:

- Schlüssel und Zertifikatskette des Servers kommen aus einem PKCS#12-Speicher; ein PEM-Lader ist nicht Teil des Servers.
- Speicher- und Schlüsselkennwort sind derselbe Wert; der Server nimmt den ersten Schlüsseleintrag.
- Zugelassen sind RSA-Schlüssel ab 2048 Bit; andere Arten und kürzere Schlüssel weist der Start ab (`SPEC-043`).
- Die Startprobe prüft jedes Zertifikat der Kette auf Gültigkeit zum Startzeitpunkt, nicht Vertrauen oder Namen.
- Die Konfiguration nennt den Speicher mit `TLS_KEYSTORE_FILE` und das Kennwort als Datei mit `TLS_KEYSTORE_PASSWORD_FILE` (beides Umgebungsvariablen, ein Zeilenende am Ende der Kennwortdatei wird entfernt); `ENCRYPTION_POLICY=REQUIRED` verlangt beide, `TEST_UNENCRYPTED` lässt sie nicht zu. Der Handler verdrahtet den Pfad TDS 7.4 (gekapselt in Paketen vom Typ `0x12`); der Pfad ab dem ersten Byte (TDS 8.0) ist nicht verdrahtet.

## JVM-Flag

Der Serverprozess läuft mit `-Djdk.tls.server.newSessionTicketCount=0`. Ohne das Flag sendet der JDK-Server bei TLS 1.3 ein NewSessionTicket noch im Handshake; im TDS-7.4-Pfad landet es in einem Paket vom Typ `0x12`, und der Client bricht ab („Unrecognized record version“). Das Flag gilt prozessweit, auch im TDS-8.0-Pfad; es ist beim Start des Serverprozesses zu setzen. Eine Lösung ohne das Flag ist nicht gebaut. `main` setzt das Flag nicht selbst: der Aufruf des Prozesses setzt es, und der Start bricht ab, wenn die Systemeigenschaft nicht genau `0` ist (Setzung). Geprüft ist der Wert der Eigenschaft beim Start vor dem ersten TLS-Aufruf, nicht die Wirkung auf dem Draht; sie belegt der Test mit Kind-JVM mit und ohne Flag.

## Gemessen

TLS 1.3 mit `TLS_AES_256_GCM_SHA384` in beiden Pfaden (`Microsoft.Data.SqlClient` 7.1.1, .NET 10). Der unverschlüsselte Testmodus ist nur mit dem Referenzclient und `Encrypt=false` gemessen.

Mit `Microsoft.Data.SqlClient` 7.1.1 gegen einen Testserver mit RSA-Zertifikat in beiden Pfaden (TDS 7.4 im Tunnel und `Encrypt=Strict`):

- nur TLS 1.2 mit `TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384` und `…_AES_128_GCM_SHA256`: Handshake und LOGIN7 gelingen, der Client wählt die 256-Bit-Suite, wenn beide angeboten sind;
- nur TLS 1.2 mit der 128-Bit-Suite: gelingt;
- nur TLS 1.3 mit `TLS_AES_128_GCM_SHA256`: gelingt;
- nur TLS 1.1: der Client lehnt ab („tlsv1 alert protocol version“).

Nicht gemessen: ECDSA-Zertifikate, `TLS_CHACHA20_POLY1305_SHA256`, andere Clients.

## Offen

Das Verhalten von `HostNameInCertificate` im TDS-7.4-Pfad; ENCRYPT_ON gegen einen Client mit `Encrypt=false`.
