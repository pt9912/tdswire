# Lasttest des Referenz-Workloads: Probe-Messzeilen vom 2026-11-15

Messprotokoll zu [TDS-QA-04](../../spec/lastenheft.md#tds-qa-04--performance) (`AGENTS.md` §5 Regel 7), Träger: [slice-qa-performance](../plan/planning/done/slice-qa-performance.md). Es ist eine **Einzelmessung eines Tages auf einem Host** und belegt nur, dass das Werkzeug `make lasttest` seine Aufbauten trennt und läuft. **Es ist kein Grenzwert und keine Messreihe**: die Messungen der Folge-Slices ([Referenz-Workload](../plan/referenz-workload.md) Abschnitt 7) stehen aus.

## Bedingungen

| Angabe | Wert |
|---|---|
| Datum, Host | 2026-11-15 19:27 bis 19:29 UTC, Linux 6.8.0-147, 20 Kerne, 31 GiB, Docker 29.9.3; Last (1/5/15 min) vor dem Lauf 5,79/5,37/5,07, nach dem Lauf 6,45/5,62/5,18, durch fremde Läufe |
| Speicher des Hosts | verfügbar vor dem Lauf 11 070 MiB, nach dem Lauf 11 551 MiB; Swap des Hosts belegt vor dem Lauf 14 449 MiB, nach dem Lauf 14 865 MiB (fremde Prozesse; der Container hat `memory.swap.max` 0, gelesen in jedem Serverlauf) |
| Laufzeit | `make lasttest` 63 s (Bau 3 s, Messung 60 s), Werkzeugstand ohne den Import in Aufbau A; Ausgabe des Werkzeugs am Ende des Laufs |
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

- **Die Trennung greift:** die Zeilen von A (Strom bis zur Senke im selben Prozess) liegen um das 8,7- bis 10-Fache über denen von B (1025/118 bis 1183/118 bei dem eigenen Backend, 1125/123 bei dem Simulations-Backend) (derselbe Erzeuger über Server und Draht zum Referenzclient). Der Unterschied B gegen A ist in dieser Messung der Aufwand für Codec, Sitzung, Transport und den Client; er ist **nicht** der Anteil der Protokollschicht allein, weil der Referenzclient in der Zeit mitliest (Setzung S6, ungemessen: ein Leser, der schneller wäre als der Server, würde den Anteil verschieben).
- **Backend gegen Protokollschicht:** C gegen B liegt im Median innerhalb der Streuung der Läufe (1125 gegen 1025 ms mit Senke, 1131 gegen 1183 ms ohne). Aus dieser Probe folgt kein Anteil des Simulations-Backends.
- **Senke:** der Unterschied mit gegen ohne Senke kehrt in B zwischen den zwei Läufen um (Nachtrag unten), in C ist er mit höchstens 3 Prozent kleiner als die Streuung der Läufe; die Probe belegt keinen Einfluss der Telemetrie.
- **Import gegen Export:** der Import ist in dieser Probe etwa doppelt so schnell wie der Export derselben Zeilen; Ursache nicht untersucht.
- **Massenladen gegen ein eigenes Backend (Aufbau B), Folge:** der Import wird deshalb über den Callback getrennt (Nachtrag unten). Die Messung selbst: der Client endet mit `InvalidOperationException` und der Meldung `Cannot access destination table 'ref_zeilen'.` (kein SQL-Fehler mit Nummer). Ein eigenes Backend trägt die Fähigkeit zum Massenladen nicht (Quelltext `BulkLoading.kt`, [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 5); die Probe der Zieltabelle schlägt dort fehl.
- **Speicher:** die Spitze des Serverprozesses (cgroup `memory.peak`) lag in den Läufen von B und C zwischen 101 und 134 MB bei `-Xmx256m`; für die Heap-Aussagen der Folge-Slices reicht sie nicht (cgroup-Spitze, nicht Live-Größe nach GC).

## Nachtrag 2026-11-16: Aufbau A für den Import

Der Aufbau A trägt seit diesem Nachtrag auch den Import: der Stapel-Callback von `ref_zeilen` wird in Stapeln zu 1000 Zeilen direkt gerufen (kein Draht, kein Client, keine Senke), damit der Import von C getrennt von der Protokollschicht gelesen werden kann. Zweiter Lauf von `make lasttest` mit denselben Vorgaben (100 000 Zeilen, 5 Läufe nach einem Warmlauf, Limits wie oben), 2026-11-16 04:31 bis 04:32 UTC, Last 8,2/5,7/3,4 bis 7,1/5,8/3,6, Swap des Hosts 13 974 MiB unverändert, verfügbar vor 10 927 MiB, nach 11 111 MiB; Laufzeit 92 s (Bau 36 s, Messung 56 s). Der Lauf trug keinen Vermerk `last-hoch` oder `swap-gewachsen`; die Streuung war in jeder Zeile `ok`.

| Aufbau | Senke | Richtung | Median ms | Min ms | Max ms | Zeilen/s (Median) |
|---|---|---|---|---|---|---|
| A, eigenes Backend | | Export | 114 | 108 | 117 | 877 192 |
| A, Simulations-Backend | | Export | 129 | 120 | 165 | 775 193 |
| A, Simulations-Backend (Callback) | | Import | 125 | 124 | 130 | 800 000 |
| B eigenes Backend | mit | Export | 1035 | 1000 | 1144 | 96 618 |
| B eigenes Backend | ohne | Export | 1008 | 962 | 1089 | 99 206 |
| C Simulations-Backend | mit | Export | 1028 | 958 | 1447 | 97 276 |
| C Simulations-Backend | mit | Import | 478 | 447 | 619 | 209 205 |
| C Simulations-Backend | ohne | Export | 1046 | 989 | 1114 | 95 602 |
| C Simulations-Backend | ohne | Import | 464 | 422 | 577 | 215 517 |

Lesart, nur so weit die Zeilen tragen: C-Import liegt im Median bei rund dem Vierfachen von A-Import (478 gegen 125 ms mit Senke, 464 gegen 125 ms ohne). Die Differenz von rund 340 bis 350 ms je 100 000 Zeilen enthält den Aufwand von Codec, Sitzung, Transport und Referenzclient für den Import und den Unterschied im Bau der Zeilen (siehe unten); sie trennt weder den Client noch den Zeilenbau ab. Der Callback ist in A und C derselbe, A-Import baut die Zeilen aber selbst als Maps, der Server baut sie nach dem Dekodieren; ob beide Schritte gleich teuer sind, ist ungemessen. Mit und ohne Senke kehrt der Unterschied bei B-Export zwischen den zwei Läufen um (2026-11-15 ohne Senke langsamer, 2026-11-16 schneller); bei C-Export und C-Import hat er in beiden Läufen dasselbe Vorzeichen (ohne Senke langsamer bzw. schneller), liegt aber unter der Streuung der Läufe. Ein Einfluss der Senke ist weiter nicht belegt. Zeilen und Zeilen/s der Tabelle oben vom 2026-11-15 und dieser Tabelle sind zwei Einzelmessungen an zwei Tagen; ihr Unterschied (zum Beispiel C-Import 571 gegen 478 ms) zeigt die Streuung zwischen den Läufen, keine Änderung des Servers.

## Größere Probe (Laufzeit der Hauptstufe)

Zur Frage der Laufzeit (Risiko in §6 des Slice): `make lasttest` mit 1 000 000 Zeilen und 5 Läufen, Aufbau C und B je mit Senke, am selben Tag (19:23 bis 19:26 UTC, Last 4,3 bis 5,7): Aufbau C Export 10 468 ms (Min 10 403, Max 10 595), Import 4 790 ms (4 552 bis 5 217), Aufbau B Export 10 249 ms (10 007 bis 10 716). Die Laufzeit des Werkzeugs war 173 s für beide Aufbauten (Bau 7 s), also rund 91 s für Aufbau C mit einer Senke und 64 s für Aufbau B. Spitze des Serverprozesses (cgroup) 178 MB bei C, 106 MB bei B, bei `-Xmx256m`. Die Zeilen dieses Laufs stehen hier nur als Zusammenfassung; die Rohzeilen sind nicht abgelegt.

## Nicht belegt

Rot gesehen mit Sensor im Repo (`tools/lasttest-selbsttest.sh`, `make skript-selbsttest`, Mutation je Fall gegen einen docker-Stub und eine Nachbildung von meminfo und loadavg): die Statistik, die Prüfung der Umgebungswerte, die Auswertung der Zeilen von Aufbau A (falsche Spalte der Millisekunden), der Vermerk `last-hoch` und `swap-gewachsen` in allen drei Zeilen von Aufbau A (und sein Fehlen bei ruhigem Host) und der Fehler „Import in B nicht abgewiesen“. Der Stub belegt die Auswertung der Skriptzeilen, nicht den Wortlaut der echten Ausgabe des Containers. Rot gesehen ohne Sensor im Repo (einmal von Hand, im Arbeitsbaum zurückgenommen): die Zählprüfung der Importe im Lauf von B und C, der Zeilenvergleich im Client und vier der fünf Fälle von `LasttestTest` (Mutationen des Verifiers; der Fall zum Import am Callback rot vom Implementer). Nicht rot gesehen: der Vermerk in den Zeilen von B und C (nur Lesen des gemeinsamen `vermerke`, das A-Fälle mitprüfen) und die Kopplung der Messzeilen von B und C über die Tokensuche (`lauf`, `ms`, `rx_bytes`, `tx_bytes`).

Ein Grenzwert; die Streuung über mehrere Tage und Hosts; mehrere Sitzungen; p95 je Anfrage; Rückstau mit langsamem Leser; Heap als Live-Größe nach GC; ein anderer Client als `Microsoft.Data.SqlClient` 7.1.1; TLS; der Einfluss der Senke bei hoher Last; ob `--memory-swap` den Container vom Swap des Hosts trennt über die Aussage `memory.swap.max 0` hinaus (der Swap des Containers wurde nicht gemessen, nur sein Limit gelesen).
