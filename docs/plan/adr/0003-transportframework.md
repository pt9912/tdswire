# ADR-0003: Transportframework und Netzwerk-Laufzeit

**Status:** Accepted

**Datum:** 2026-10-29

**Autor:** pt9912

**Bezug:** [TDS-FA-01](../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung), [TDS-QA-02](../../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-RB-01](../../../spec/lastenheft.md#tds-rb-01--implementierungssprache)

**Schärft:** [SPEC-046](../../../spec/spezifikation.md#3-defaults-und-konstanten), [ARC-003](../../../spec/architecture.md#1-komponenten-übersicht), [ARC-008](../../../spec/architecture.md#1-komponenten-übersicht), [ARC-013](../../../spec/architecture.md#3-externe-abhängigkeiten), [SPEC-027](../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge), [TDS-FA-01.a](../../../spec/spezifikation.md#tds-fa-01a--tcp-verbindung-und-prelogin)

---

## Kontext

Framing, Fragmentierung, Backpressure und TLS-Integration benötigen eine robuste JVM-I/O-Laufzeit. Die Wahl soll Protokollsemantik nicht mit Transport koppeln.

Gemessen mit drei Prototypen (Netty 4.1.118, Java NIO mit `SSLEngine`, Coroutines 1.10.1 mit `AsynchronousSocketChannel` und `SSLEngine`; JDK 21, `-Xmx256m`) und `Microsoft.Data.SqlClient` 7.1.1: Alle drei erreichen LOGIN7 im TDS-7.4-Pfad (`0x74000004`) und im TDS-8.0-Pfad (`0x08000000`); ein Test-Client sendete je 1000 byteweise und zufällig zerstückelte Nachrichten je Pfad, und der Server setzte sie korrekt zusammen (ob sie beim Server als kleine Lese-Stücke ankamen, ist nicht protokolliert); bei 100 defekten Verbindungen schloss der Server die erwarteten und der Prozess blieb am Leben. Bei 100 MiB an einen mit 1 MiB/s lesenden Client bleibt der Speicher in allen dreien begrenzt (Heap höchstens 10 MB), vorausgesetzt der Schreiber beachtet den Rückstau; bei Netty wuchs der Direct-Speicher ohne die `isWritable`-Prüfung auf 128 MB in 25 s. Bei 200 gleichzeitigen Verbindungen sind Speicher und Durchsatz nicht klar unterscheidbar (einzelner Lauf). Der Prototypumfang beträgt 120 (Netty), 157 (NIO) und 126 Zeilen (Coroutines); Netty bringt sieben Jars mit, NIO keines, Coroutines eines. Die Zahlen stammen aus einem Spike-Lauf; für Option C hat die Verifikation Fragmentierung, defekte Verbindungen, 100 MiB bei langsamem Leser und beide Pfade mit dem echten Client wiederholt (Durchsatz bei 200 Verbindungen etwa 8 % unter dem ersten Lauf), die Optionen A und B nicht. Nicht gemessen sind Latenz-Perzentile, Last über 200 Verbindungen, CPU, Live-Heap bei 200 Verbindungen, Wiederaufnahme und Neuverhandlung, Idle-Timeouts, Langzeitlauf, bei Netty der Fall mit TLS-Bytes im Lesepuffer nach PRELOGIN, die Fehlerart des Clients nach LOGIN7 bei Netty und der Aufwand bei vollständigem TDS (Tokens, ATTENTION, MARS).

## Entscheidung

Wir wählen **Option C**: Kotlin-Coroutines mit `java.nio.channels.AsynchronousSocketChannel` und einer eigenen `SSLEngine`-Schleife; `kotlinx-coroutines-core` ist die einzige zusätzliche Abhängigkeit des Transports.

- **Framing und Fragmentierung:** Der Transport setzt Nachrichten aus beliebig zerstückelten TDS-Paketen zusammen und lehnt eine Nachricht über der Größengrenze vor dem Login (`SPEC-046`) ab. Das ist die Anforderung an den Produktivcode; die Prototypen hielten sie ein, die Messung belegt aber nicht, dass der Puffer erst gar nicht gefüllt wird.
- **TLS:** Der Handshake im TDS-7.4-Pfad läuft in TDS-Paketen vom Typ `0x12` und wird am Ergebnis `FINISHED` der `SSLEngine` auf rohes TLS umgeschaltet (so bauten es die Prototypen mit `SSLEngine`; die Messung prüft den Umschaltpunkt nicht einzeln); der TDS-8.0-Pfad startet TLS ab dem ersten Byte (siehe [ADR-0002](0002-tls-und-verschluesselung.md)). Der Serverprozess läuft mit `-Djdk.tls.server.newSessionTicketCount=0`: Ohne das Flag sendet der JDK-Server bei TLS 1.3 ein NewSessionTicket noch im Handshake, es landet in einem `0x12`-Paket, und der Client bricht ab.
- **Backpressure:** Ergibt sich aus dem suspendierenden Schreiben. Anforderung an den Produktivcode: kein Schreibpfad puffert unbegrenzt; im Prototyp blieb der Speicher bei 100 MiB an einen langsamen Leser begrenzt.
- **Pufferstrategie:** Nachrichten werden bis zur Größengrenze zusammengesetzt, Ergebnisströme schrittweise geschrieben; das Schreiben suspendiert bei Rückstau. Das gilt als Entwurf und ist im Produktivcode zu liefern.
- **Auswahlgrund:** Die Messung trennt die drei Optionen nicht. Gewählt ist C wegen des kleinsten Eigencodes bei einer Abhängigkeit und wegen des Rückstaus im Sprachmodell (Entscheidung des Autors); Java NIO hätte keine Abhängigkeit, aber 157 Zeilen und Rückstau von Hand, Netty sieben Jars und eine Pflichtprüfung `isWritable`.
- **Nachweis:** Ein Spike mit je einem Prototyp je Option (Netty, Java NIO, Coroutines) und dem echten Referenzclient hat Framing, beide Protokollpfade, defekte Verbindungen, Backpressure und Mehrfachlast gemessen (siehe Kontext).

## Verglichene Alternativen

| Option                                       | Pro                                | Contra                                             |
| -------------------------------------------- | ---------------------------------- | -------------------------------------------------- |
| A — Netty                                    | Bewährte Pipeline und ByteBuf-I/O  | Komplexität bei Buffer-Lebensdauer                 |
| B — Java NIO                                 | Keine zusätzliche Frameworkbindung | Mehr eigener Netzwerkcode                          |
| **C — Kotlin-Coroutines mit Socket-Abstraktion** | Idiomatic Kotlin API; Backpressure durch suspendierendes Schreiben; 126 Zeilen im Prototyp, eine Abhängigkeit | Framing und die `SSLEngine`-Schleife selbst zu schreiben; Adapter für `AsynchronousSocketChannel` (ca. 12 Zeilen) |
| 0 — keine Festlegung vor dem ersten Transport-Spike | Keine Frameworkbindung vor dem Nachweis | Blockiert `ARC-003` und `ARC-013` |

## Konsequenzen

- **Positiv:** Framing, Backpressure und Pufferverwaltung sind an einer Stelle gelöst und prüfbar.
- **Negativ / Risiko:** Die Wahl bindet Buffer-Lebensdauer und Wartungsaufwand an das gewählte Framework.
- **Folgepflicht:** `ARC-003`, `ARC-008` und `ARC-013` bleiben technologieneutral, denn die Sicht trifft keine Bibliotheksentscheidung; die Laufzeit steht in dieser ADR. `SPEC-046` und `SPEC-027` bleiben unverändert (die Prototypen hielten die Grenze ein und bestanden die Fragmentierungsproben). Fuzz-Tests nach `SPEC-027` ergänzen; `kotlinx-coroutines-core` in `gradle/libs.versions.toml`, Lock und Prüfsummen aufnehmen, sobald der erste Transport-Code entsteht; das JVM-Flag steht im Sicherheitsprofil und ist im Start des Serverprozesses zu setzen.
- **Offen:** Der Aufwand bei vollständigem TDS (Tokens, ATTENTION, MARS) und das Verhalten unter höherer Last als 200 Verbindungen sind nicht gemessen.

## Fitness Function (falls maschinell prüfbar)

| Tooling                                     | Regel                                                                                                                                 | Make-Target                         |
| ------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------- |
| JVM-/Protokolltests und .NET-Referenzclient | Fragmentierungs- und Fuzz-Tests mit beliebigen Paketgrenzen verursachen keinen Prozessabsturz; der Speicherbedarf bleibt unter Last begrenzt. Noch kein bestandenes Gate. | — (Gate noch einzurichten) |

## Re-Evaluierungs-Trigger

Nachweisbare Leaks, unzureichende Backpressure oder neue JVM-Runtime-Anforderungen.

## Geschichte

| Datum      | Ereignis                      | Verweis |
| ---------- | ----------------------------- | ------- |
| 2026-10-29 | Proposed – Entscheidung offen | —       |
| 2026-10-29 | Bezug auf `SPEC-046` (Größengrenze vor dem Login) ergänzt; Entscheidung weiter offen | [SPEC-046](../../../spec/spezifikation.md#3-defaults-und-konstanten) |
| 2026-10-29 | Accepted – Option C; Spike mit drei Prototypen und dem Referenzclient; Messaussagen vor Abschluss des Slice begrenzt | —       |
