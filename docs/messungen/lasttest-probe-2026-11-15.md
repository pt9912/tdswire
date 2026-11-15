# Lasttest des Referenz-Workloads: Probe-Messzeilen vom 2026-11-15

Messprotokoll zu [TDS-QA-04](../../spec/lastenheft.md#tds-qa-04--performance) (`AGENTS.md` §5 Regel 7), Träger: [slice-qa-performance](../plan/planning/in-progress/slice-qa-performance.md). Es ist eine **Einzelmessung eines Tages auf einem Host** und belegt nur, dass das Werkzeug `make lasttest` seine Aufbauten trennt und läuft. **Es ist kein Grenzwert und keine Messreihe**: die Messungen der Folge-Slices ([Referenz-Workload](../plan/referenz-workload.md) Abschnitt 7) stehen aus.

## Bedingungen

| Angabe | Wert |
|---|---|
| Datum, Host | 2026-11-15 19:27 bis 19:29 UTC, Linux 6.8.0-147, 20 Kerne, 31 GiB, Docker 29.9.3; Last (1/5/15 min) vor dem Lauf 5,79/5,37/5,07, nach dem Lauf 6,45/5,62/5,18, durch fremde Läufe |
| Speicher des Hosts | verfügbar vor dem Lauf 11 070 MiB, nach dem Lauf 11 551 MiB; Swap des Hosts belegt vor dem Lauf 14 449 MiB, nach dem Lauf 14 865 MiB (fremde Prozesse; der Container hat `memory.swap.max` 0, gelesen in jedem Serverlauf) |
| Stand | Commit `321fd7b1` plus die ungeprüften Änderungen des Slice (Arbeitsbaum geändert); Aufruf `make lasttest`, Vorgaben des Werkzeugs |
| Limits | Server und Aufbau A: `--cpus 2`, `--memory 4g`, `--memory-swap 4g`, Serverprozess `-Xmx256m`; Client: `--cpus 2`, `--memory 2g`, `--memory-swap 2g` (Setzungen, Referenz-Workload S8) |
| Images | Server `sha256:c48f8542cfaa538e5c7a0b03030f42325d38fd1feec3962ffb4c9b9d5a2bc138`, Client `sha256:ee7c3d863ada6f05ae92c99f3d0b970934181210d668e11ca1019fe0cea7400f` (lokal gebaut; die Basis-Images stehen im `Dockerfile` und in `referenztest-dotnet/Dockerfile` per Digest) |
| Workload | Tabelle `ref_zeilen`, 100 000 Zeilen, Seed 42, 5 Läufe nach einem Warmlauf, Export mit `SqlDataReader`, Import mit `SqlBulkCopy` (BatchSize 1000), TDS 7.4 ohne Verschlüsselung, eine Sitzung |
| Erzeuger der Zahlen | `testserver/kotlin/io/github/pt9912/tdswire/testserver/ReferenzZeilen.kt` und `referenztest-dotnet/Lasttest/ReferenzZeilen.cs`; Ausgabe: `tools/lasttest.sh` (Kopf des Skripts nennt die Felder) |

## Messzeilen

Millisekunden je Lauf über alle 100 000 Zeilen; Bytes je Zeile sind der Zähler der Netzwerkschnittstelle des Client-Containers (mit IP- und TCP-Kopf) geteilt durch die Zeilenzahl, nicht die Länge der TDS-Pakete. Die Streuung war in jeder Zeile `ok` (Maximum höchstens das Doppelte des Minimums).

| Aufbau | Senke | Richtung | Median ms | Min ms | Max ms | Zeilen/s (Median) | Bytes je Zeile |
|---|---|---|---|---|---|---|---|
| A Backend allein, eigenes Backend | | Export | 118 | 108 | 172 | 847 457 | kein Draht |
| A Backend allein, Simulations-Backend | | Export | 123 | 121 | 127 | 813 008 | kein Draht |
| B eigenes Backend | mit | Export | 1025 | 1001 | 1066 | 97 560 | 328 |
| B eigenes Backend | ohne | Export | 1183 | 1103 | 1368 | 84 530 | 330 |
| C Simulations-Backend | mit | Export | 1125 | 1011 | 1160 | 88 888 | 329 |
| C Simulations-Backend | mit | Import | 571 | 492 | 581 | 175 131 | 337 |
| C Simulations-Backend | ohne | Export | 1131 | 1114 | 1338 | 88 417 | 330 |
| C Simulations-Backend | ohne | Import | 563 | 528 | 694 | 177 619 | 337 |

Der Lauf trug an den Zeilen von B ohne Senke und von C den Vermerk `swap-gewachsen` (Swap des Hosts um mehr als 16 MiB größer als vor der Stufe; der Swap gehört dem Host, nicht dem Container).

## Was die Zeilen zeigen und was nicht

- **Die Trennung greift:** die Zeilen von A (Strom bis zur Senke im selben Prozess) liegen um den Faktor 8 bis 9 über denen von B (derselbe Erzeuger über Server und Draht zum Referenzclient). Der Unterschied B gegen A ist in dieser Messung der Aufwand für Codec, Sitzung, Transport und den Client; er ist **nicht** der Anteil der Protokollschicht allein, weil der Referenzclient in der Zeit mitliest (Setzung S6, ungemessen: ein Leser, der schneller wäre als der Server, würde den Anteil verschieben).
- **Backend gegen Protokollschicht:** C gegen B liegt im Median innerhalb der Streuung der Läufe (1125 gegen 1025 ms mit Senke, 1131 gegen 1183 ms ohne). Aus dieser Probe folgt kein Anteil des Simulations-Backends.
- **Senke:** der Unterschied mit gegen ohne Senke geht in B in die eine, in C in keine eindeutige Richtung; die Probe belegt keinen Einfluss der Telemetrie.
- **Import gegen Export:** der Import ist in dieser Probe etwa doppelt so schnell wie der Export derselben Zeilen; Ursache nicht untersucht.
- **Massenladen gegen ein eigenes Backend (Aufbau B):** der Client endet mit `InvalidOperationException` und der Meldung `Cannot access destination table 'ref_zeilen'.` (kein SQL-Fehler mit Nummer). Ein eigenes Backend trägt die Fähigkeit zum Massenladen nicht (Quelltext `BulkLoading.kt`, [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 5); die Probe der Zieltabelle schlägt dort fehl.
- **Speicher:** die Spitze des Serverprozesses (cgroup `memory.peak`) lag in den Läufen von B und C zwischen 101 und 134 MB bei `-Xmx256m`; für die Heap-Aussagen der Folge-Slices reicht sie nicht (cgroup-Spitze, nicht Live-Größe nach GC).

## Größere Probe (Laufzeit der Hauptstufe)

Zur Frage der Laufzeit (Risiko in §6 des Slice): `make lasttest` mit 1 000 000 Zeilen und 5 Läufen, Aufbau C und B je mit Senke, am selben Tag (19:23 bis 19:26 UTC, Last 4,3 bis 5,7): Aufbau C Export 10 468 ms (Min 10 403, Max 10 595), Import 4 790 ms (4 552 bis 5 217), Aufbau B Export 10 249 ms (10 007 bis 10 716). Die Laufzeit des Werkzeugs war 173 s für beide Aufbauten (Bau 7 s), also rund 91 s für Aufbau C mit einer Senke und 64 s für Aufbau B. Spitze des Serverprozesses (cgroup) 178 MB bei C, 106 MB bei B, bei `-Xmx256m`. Die Zeilen dieses Laufs stehen hier nur als Zusammenfassung; die Rohzeilen sind nicht abgelegt.

## Nicht belegt

Ein Grenzwert; die Streuung über mehrere Tage und Hosts; mehrere Sitzungen; p95 je Anfrage; Rückstau mit langsamem Leser; Heap als Live-Größe nach GC; ein anderer Client als `Microsoft.Data.SqlClient` 7.1.1; TLS; der Einfluss der Senke bei hoher Last; ob `--memory-swap` den Container vom Swap des Hosts trennt über die Aussage `memory.swap.max 0` hinaus (der Swap des Containers wurde nicht gemessen, nur sein Limit gelesen).
