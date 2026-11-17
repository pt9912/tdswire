# Performance: Rückstau am Socket, bei blockierendem Backend und beim Simulations-Backend, 2026-11-17

Messprotokoll zu [TDS-QA-04](../../spec/lastenheft.md#tds-qa-04--performance) und [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md) (`AGENTS.md` §5 Regel 7), Träger: [slice-qa-performance-rueckstau](../plan/planning/in-progress/slice-qa-performance-rueckstau.md). **Es ist eine Einzelmessung eines Tages auf einem Host und kein Grenzwert.** Erzeuger aller Zahlen: `tools/rueckstau.sh` (Make-Ziel `make rueckstau`) mit dem Serverprozess `testserver/kotlin/io/github/pt9912/tdswire/testserver/RueckstauServer.kt` und dem Client `referenztest-dotnet/Rueckstau/RueckstauTests.cs`, alle im Repo. Der Lauf aller Zellen außer `leser-frist` steht auf Commit `4eecf536`, die Zelle `leser-frist` auf Commit `769de2cc` (Unterschied der beiden: die Behandlung von `RS_ERWARTE_FRIST` im Client und eine Zeile im Skriptkopf). Rohzeilen: [performance-rueckstau-2026-11-17-rohzeilen.txt](performance-rueckstau-2026-11-17-rohzeilen.txt); jede Zahl unten steht dort in einer Zeile `rueckstau:` oder `zelle:`. **Nicht im Repo** liegen nur die Ausgabedateien der Läufe (die Rohzeilen sind ihr gefilterter Auszug) und die Wegwerf-Auswertung der Tabellen aus den Rohzeilen; die Tabellen sind per Hand aus den Zeilen übernommen, die Rohzeilen sind der Beleg.

## Bedingungen

| Angabe | Wert |
|---|---|
| Datum, Host | 2026-11-17, Lauf von 09:08 bis 09:18 UTC (Zelle `leser-frist` 09:23 UTC), Linux 6.8.0-147, 20 Kerne, 31 GiB, Docker 29.9.3; Swap des Hosts rund 12,5 GiB belegt (fremde Prozesse; `memory.swap.max` des Serverprozesses 0, in jeder Serverzeile) |
| Fremdlast | Last 1 min zwischen 1,9 und 4,3 im Lauf, 6,6 bei der Zelle `leser-frist`; der Host war nicht leer: ein Verifier fuhr parallel für einen anderen Slice. Je Zelle nennt `zelle:` die Proben (alle 10 s, CPU des Hosts abzüglich der CPU der beiden Container) mit mehr als 300 % fremder CPU; in drei Zellen gab es je eine solche Probe (`leser-eigen-8` 311 %, `leser-eigen-64` 340 %, `leser-sim-8` 302 %), in den übrigen keine (Höchstwert 270 %). Diese drei Zellen gelten nur als Größenordnung. Die Last 1 min vor und nach jeder Zelle steht in den Rohzeilen |
| Speicher | verfügbar zu Beginn und am Ende des Laufs rund 15,8 GiB (Zeilen `Kopf zustand`); Wächter (Abbruch unter 4096 MiB) hat nicht ausgelöst |
| Limits | Server: `--cpus 2`, `--memory 4g`, `--memory-swap 4g`, `-Xmx256m` (die JVM sieht 2 Prozessoren); Client: `--cpus 2`, `--memory 2g`, `--memory-swap 2g`; interne Docker-Netze ohne Zugang nach außen; `QUERY_TIMEOUT` 3600 s in den Leser-Zellen (Vorgabe sonst), `CANCEL_TIMEOUT` 2 s in den Abbruch-Zellen (Vorgabe 5 s) |
| Client | `Microsoft.Data.SqlClient` 7.1.1, TDS 7.4 ohne Verschlüsselung; jede Sitzung ein eigener Thread (`start_max_ms` in den Zeilen nennt den spätesten Start gegen den ersten: höchstens 38 ms) |
| Senke der Telemetrie | das Log des Testservers in der Vorgabe-Ebene des Prozesses (`LOG_LEVEL` nicht gesetzt); kein Vergleich mit und ohne |
| Ein Lauf je Zelle | je Zelle ein frischer Serverprozess und genau ein Durchlauf des Szenarios; **keine Wiederholungen, keine Streuung**: jede Zahl ist ein Einzelwert |

## Aufbau

- **Zählendes Backend** (Aufbau B, eigenes Backend): `RueckstauBackend` liefert `SELECT * FROM ref_zeilen` aus `ReferenzZeilen` und zählt jede Zeile, **bevor** sie den Strom verlässt (die Zeile „in Arbeit“ zählt mit). Eine Anfrage trägt Zeilenzahl, Art und Dauer des Wartens in einem Kommentar hinter dem Text (`/*zeilen=…,modus=…,block=…,jede=…*/`). `SELECT * FROM rueckstau_stand` liefert die Zähler (erzeugt, importiert, offene, beendete, abgebrochene Ströme, Threads und Spitze seit dem letzten Lesen, benutzter Heap; `_gc` ruft vorher die Speicherbereinigung).
- **Simulations-Backend** (Aufbau C): dieselbe Tabelle `ref_zeilen` mit einer zählenden Zeilenquelle und einem Stapel-Callback, der nach `RUECKSTAU_CALLBACK_MODUS` und `RUECKSTAU_CALLBACK_MS` wartet und die Zeilen zählt; dieselben Zähler als Tabellen `rueckstau_stand`.
- **Arten des Wartens** (Backend und Callback): `suspendierend` (`delay`), `unterbrechbar` (Thread eines festen Pools mit 64 Threads, `runInterruptible`), `nicht-unterbrechbar` (Thread des Pools, schläft bis zum Ende, ignoriert die Unterbrechung) und `direkt` (`Thread.sleep` im Thread des Aufrufers: ein Verstoß gegen „Suspendierend“ in „Pflichten des Backends“, hier nur zur Messung der Folge). Der Pool ist ein Aufbau dieser Messung, kein Adapter des Servers; kein echter Treiber.
- **Vorlauf** (Zeilen, die das Backend dem langsamsten Leser vorausläuft) = Zeilen, die das Backend nach dem Start des Szenarios dem Strom übergeben hat, minus die Zeilen der vollständig gelesenen Sitzungen, minus die eine Zeile, die der langsame Leser gelesen hat. Der langsame Leser liest **eine** Zeile und hält den Strom an, bis alle übrigen Sitzungen fertig sind und 1,5 s vergangen sind; der Zähler wird dann zweimal im Abstand von 1,5 s gelesen (`plateau_zuwachs`: Unterschied beider Werte). Mit mehr als einem langsamen Leser ist der Wert der Mittelwert je Leser (der Zähler ist über alle Sitzungen eines Prozesses summiert).
- **Zeile in Byte**: die Umrechnung nutzt 329 Byte je Zeile auf dem Draht (Größenordnung aus der Probe des Referenz-Workloads, Abschnitt 3.1 in `docs/plan/referenz-workload.md`; in diesem Lauf nicht gemessen).

## Ergebnisse

### Socket, mehrere Verbindungen: langsamer Leser (Stufe 256 der Staffel)

Eine Sitzung liest eine Zeile und hält an; die übrigen schnellen Sitzungen lesen je 100 000 Zeilen (Stufen 1, 8, 64) beziehungsweise 20 000 (Stufe 256 und halb) bis zum Ende. „Basis“ sind dieselben schnellen Sitzungen allein, vorher im selben Prozess; Zeilen/s sind gesamt über alle schnellen Sitzungen. Der Lauf „Basis“ kam zuerst: der Unterschied zu „mit langsamem Leser“ enthält damit auch den Anlauf der JVM und ist kein Maß für einen Einfluss.

| Zelle | Aufbau | Sitzungen | langsam | Vorlauf je langsamem Leser (Zeilen) | Zuwachs nach 1,5 s | Basis Zeilen/s | mit langsamem Leser Zeilen/s | Heap benutzt (Stichprobe) / nach GC MB | cgroup-Spitze MiB | Last 1 min vor → nach | Fremdlast (Proben, davon > 300 %, Höchstwert) |
|---|---|---|---|---|---|---|---|---|---|---|---|
| leser-eigen-1 | eigen | 1 | 1 | 1499 | 0 | – | – | 21 / 3 | 93,5 | 2,11 → 2,40 | 0, 0, 0 % |
| leser-eigen-8 | eigen | 8 | 1 | 1499 | 0 | 167 418 | 209 609 | 23 / 3 | 102,8 | 2,40 → 2,37 | 1, 1, 311 % |
| leser-eigen-64 | eigen | 64 | 1 | 663 | 0 | 218 003 | 225 616 | 26 / 4 | 114,0 | 2,37 → 2,74 | 5, 1, 340 % |
| leser-eigen-256 | eigen | 256 | 1 | 663 | 0 | 220 381 | 227 807 | 71 / 6 | 147,9 | 2,74 → 4,09 | 4, 0, 270 % |
| leser-eigen-256halb | eigen | 256 | 128 | 657 (Mittel) | 0 | 210 857 | 223 778 | 56 / 6 | 134,8 | 4,09 → 4,28 | 3, 0, 128 % |
| leser-sim-1 | Simulation | 1 | 1 | 1499 | 0 | – | – | 25 / 3 | 100,9 | 4,28 → 4,01 | 0, 0, 0 % |
| leser-sim-8 | Simulation | 8 | 1 | 1032 | 0 | 155 839 | 195 851 | 7 / 3 | 105,0 | 4,01 → 3,29 | 1, 1, 302 % |
| leser-sim-64 | Simulation | 64 | 1 | 614 | 0 | 213 271 | 227 359 | 19 / 4 | 108,8 | 3,29 → 2,14 | 5, 0, 263 % |
| leser-sim-256 | Simulation | 256 | 1 | 663 | 0 | 211 310 | 221 817 | 69 / 7 | 151,9 | 2,14 → 1,92 | 4, 0, 130 % |

Jede Zelle: alle Sitzungen erreichten alle Zeilen (letzte Zeile gleich dem Erzeuger), der langsame Leser las nach dem Anhalten bis zum Ende, `offen_ende` 0 (kein Strom blieb im Backend offen), der Server lief nach der Zelle weiter. Bei `leser-eigen-256halb` hielten 128 Ströme gleichzeitig offen (`offen 128` zur Zeit der Probe), `threads` 14.

Lesart (Bedingung: dieser Host, diese Docker-Netze, ein Durchlauf je Zelle): Das Backend läuft einem Leser, der nicht mehr liest, **614 bis 1499 Zeilen** voraus (rund 0,2 bis 0,5 MB) und bleibt dort stehen (Zuwachs 0 in allen neun Zellen); die Zahl hängt nicht erkennbar von der Zahl der Verbindungen bis 256 ab und nicht vom Aufbau (eigenes und Simulations-Backend liegen im selben Bereich). Dass sie bei 1 und 8 Sitzungen höher liegt (1499 und 1032) als bei 64 und 256 (614 bis 663), ist beobachtet; die Ursache ist nicht untersucht (denkbar: Puffer des Betriebssystems unter Last; nicht geprüft). Die übrigen Sitzungen liefen mit dem langsamen Leser nicht langsamer als die Basis (Aussage nur über Zeilen/s dieser Läufe; die Basis kam zuerst). Der Heap des Serverprozesses lag nach der Speicherbereinigung bei 3 bis 7 MB; die Stichprobe des benutzten Heaps (ohne Bereinigung) lag bei höchstens 71 MB.

**Neben der Einzelmessung** (eine Maschine, Loopback, 32 MiB, 500 ms; `SocketChannelLoopbackTest`): dort ist belegt, dass ein Writer 32 MiB gegen eine Gegenstelle, die nicht liest, nach 500 ms nicht abgeschlossen hat; ein Vorlauf in Zeilen ist dort nicht gemessen. Die hier gemessenen 0,2 bis 0,5 MB sind mit „weniger als 32 MiB“ vereinbar; ein Vergleich Zahl gegen Zahl besteht nicht, weil die frühere Messung keine Zahl für den Vorlauf nennt.

**Docker-Netz gegen Loopback** (gelesen in jedem Serverlauf, Zeile `netz`): im Container hat `eth0` die MTU 1500, `lo` 65536; `tcp_rmem` 4096 131072 6291456 und `tcp_wmem` 4096 16384 4194304 (Kernel-Werte des Hosts, im Container sichtbar). Das heißt: der Verkehr zwischen den Containern läuft über eine Schnittstelle mit der kleinen MTU eines Ethernet-Segments statt über Loopback mit der großen. Nicht gemessen und als Annahme zu lesen: dass der Weg über virtuelle Schnittstellen und eine Brücke des Hosts führt und keine physische Netzkarte berührt, und dass die Laufzeit zwischen den Containern unter der eines Netzes zwischen Hosts liegt. Ein Netz zwischen Hosts (andere Latenz, Verluste, andere Puffer) ist nicht gemessen; „nicht anders genug für Netz“ heißt hier: der Rückstau ist an dem Weg gemessen, den dieser Host bietet.

### Blockierendes Backend

Das eigene Backend wartet vor **jeder** Zeile 10 ms (100 Zeilen je Sitzung, erwartet je Sitzung 1000 ms); N Sitzungen gleichzeitig; ein Nachbar (eigene Sitzung) fragt alle 50 ms die Zähler ab. Der Pool hat 64 Threads. Die Basis der Threads des Serverprozesses ist 13. Zeiten in Millisekunden, Nachbar in Mikrosekunden (Median / 95. Rang / Höchstwert über n Anfragen; bei n unter 20 ist „95. Rang“ der Höchstwert).

| Zelle | Art | Sitzungen | Gesamt ms | Sitzung (Median) ms | Spitze der Threads | Nachbar ohne Last (n; p50 / p95 / max µs) | Nachbar mit Last (n; p50 / p95 / max µs) | Last 1 min vor → nach | Fremdlast (Proben, > 300 %, Höchstwert) |
|---|---|---|---|---|---|---|---|---|---|
| blk-suspendierend-64 | suspendierend | 64 | 1080 | 1044 | 13 | 20; 1814 / 2252 / 64 014 | 21; 1320 / 1712 / 1823 | 1,92 → 2,22 | 0, 0, 0 % |
| blk-unterbrechbar-8 | unterbrechbar | 8 | 1086 | 1078 | 77 | 38; 2022 / 2315 / 44 900 | 21; 1547 / 1994 / 2010 | 2,28 → 2,08 | 0, 0, 0 % |
| blk-unterbrechbar-64 | unterbrechbar | 64 | 1078 | 1057 | 77 | 20; 1874 / 2752 / 59 603 | 21; 1173 / 1563 / 2334 | 2,08 → 4,27 | 0, 0, 0 % |
| blk-unterbrechbar-256 | unterbrechbar | 256 | 4116 | 4041 | 78 | 38; 1744 / 2399 / 55 208 | 80; 958 / 2052 / 26 531 | 4,17 → 3,68 | 0, 0, 0 % |
| blk-nicht-unterbrechbar-64 | nicht unterbrechbar | 64 | 1080 | 1042 | 77 | 19; 1880 / 54 338 / 54 338 | 21; 1352 / 1702 / 1992 | 3,68 → 3,42 | 0, 0, 0 % |
| blk-nicht-unterbrechbar-256 | nicht unterbrechbar | 256 | 4101 | 4012 | 77 | 25; 1725 / 4462 / 63 941 | 80; 976 / 1464 / 13 354 | 3,38 → 3,57 | 0, 0, 0 % |
| blk-direkt-8 | direkt | 8 | 4159 | 4121 | 14 | 38; 1969 / 2447 / 44 007 | 3; 1 777 738 / 2 229 764 / 2 229 764 | 3,57 → 3,32 | 0, 0, 0 % |

Lesart (Bedingung: ein Backend, das hinter einem Pool von 64 Threads wartet, 10 ms je Zeile, dieser Host):

- Die Threads des Serverprozesses sind durch den Pool begrenzt: Spitze 77 bis 78 bei 64 bis 256 Sitzungen (13 + 64, einmal 78); bei 256 Sitzungen bleibt die Zahl gleich. Die Spitze belegt die **Obergrenze**, nicht, dass 64 Threads gleichzeitig schliefen; dafür steht die Dauer: bei 64 Sitzungen 1,08 s (Erwartung 1,0 s), bei 256 Sitzungen 4,1 s (Erwartung 256 · 1 s / 64 = 4 s). Mit 256 Sitzungen warten die Sitzungen also auf den Pool (Sitzung im Median 4,0 s), nicht auf das Backend.
- Unterbrechbar und nicht unterbrechbar unterscheiden sich im Durchlauf ohne Abbruch nicht (1078/1080 ms bei 64, 4116/4101 ms bei 256).
- Der Nachbar sieht mit Last Antwortzeiten, die nicht über denen ohne Last liegen (Median 1,0 bis 1,5 ms gegen 1,7 bis 2,0 ms). Warum sie mit Last etwas niedriger liegen, ist nicht untersucht (denkbar: die erste Anfrage nach dem Anmelden enthält Anlaufzeit; das erklärt auch die Höchstwerte von 44 bis 64 ms ohne Last; nicht geprüft). Bei `direkt` wartete der Nachbar im Median 1,8 s: die zwei Dispatcher-Threads der JVM mit 2 Prozessoren waren mit den Sitzungen belegt (Gesamtdauer 4,16 s gegen 8 · 1 s / 2 = 4 s Erwartung bei zwei Threads); ein Backend, das im Thread des Aufrufers schläft, hält damit die anderen Sitzungen dieses Prozesses an (n = 3 Anfragen in 4 s).

### Abbruch nach `CANCEL_TIMEOUT`

Das eigene Backend wartet vor der dritten, sechsten, … Zeile 10 s; der Client ruft `Cancel` 1,5 s nach dem Start der Anfrage; `CANCEL_TIMEOUT` ist 2 s; 8 weitere Sitzungen lesen daneben je 100 000 Zeilen, der Nachbar fragt die Zähler ab.

| Zelle | Art | Cancel bis Ende (ms) | Ende beim Client | gelesen | offene Ströme direkt danach / nach 11,5 s | Threads danach | Nachbar (n; p50 / p95 / max µs) | Last 1 min vor → nach | Fremdlast (Proben, > 300 %, Höchstwert) |
|---|---|---|---|---|---|---|---|---|---|
| abbruch-unterbrechbar | unterbrechbar | 9 | `Operation cancelled by user` | 2 | 0 / 0 | 14 | 101; 1750 / 16 987 / 43 521 | 3,32 → 3,15 | 1, 0, 249 % |
| abbruch-nicht-unterbrechbar | nicht unterbrechbar | 2018 | `transport-level error … (provider: TCP Provider, error: 0 - Success)` (Verbindung geschlossen) | 0 | 1 / 0 | 14 | 101; 1698 / 38 875 / 47 764 | 3,15 → 3,02 | 1, 0, 253 % |

Lesart (Bedingung: Pool, 10 s Wartezeit, `CANCEL_TIMEOUT` 2 s, diese 8 Last-Sitzungen):

- Ein unterbrechbarer Aufruf endet mit dem Abbruch in 9 ms.
- Ein nicht unterbrechbarer Aufruf hält den Abbruch bis `CANCEL_TIMEOUT` auf (2018 ms nach `Cancel`, Frist 2000 ms), danach schließt der Server die Verbindung; der Strom blieb im Backend offen (`offen` 1 direkt danach) und war nach dem Ende der Wartezeit geschlossen (`offen` 0, `abgebrochen_delta` 1 in den Rohzeilen). Der Thread des Pools war bis zur Rückkehr belegt; die Zahl der Threads blieb 14, weil der Pool seine Threads vor der Zelle nicht zurückgibt (die Zahl unterscheidet beide Arten nicht).
- Der Nachbar sah in beiden Abbruch-Zellen höhere 95. Ränge (17 und 39 ms) als in den Zellen ohne Abbruch (2 ms). Ob das am Abbruch oder an den 8 gleichzeitig lesenden Sitzungen liegt, ist in diesem Lauf nicht getrennt (keine Zelle ohne Last im Lauf des Commits `4eecf536`). Die 8 lesenden Sitzungen erreichten in beiden Zellen alle Zeilen.

### Simulations-Backend: Massenladen

N Sitzungen laden je 100 000 beziehungsweise 20 000 Zeilen mit `SqlBulkCopy` (`BatchSize` 1000). Alle 100 ms vergleicht der Client die Zeilen, die `SqlBulkCopy` aus dem Leser gezogen hat, mit den Zeilen, die der Stapel-Callback des Servers verarbeitet hat (Vorlauf des Senders). Der Callback wartet je Stapel 50 ms.

| Zelle | Sitzungen | Zeilen je Sitzung | Callback | Gesamt ms | größter Vorlauf des Senders (Zeilen, Proben) | Nachbar (n; p50 / p95 / max µs) | Spitze der Threads | Heap nach GC MB | Last 1 min vor → nach | Fremdlast |
|---|---|---|---|---|---|---|---|---|---|---|
| imp-1-schnell | 1 | 100 000 | ohne Warten | 817 | keine Probe (0) | 6; 1242 / 2612 / 2612 | 13 | 4 | 3,02 → 2,94 | 0, 0, 0 % |
| imp-1-callback | 1 | 100 000 | suspendierend 50 ms | 5909 | 1001 (43) | 105; 1446 / 2152 / 5682 | 13 | 4 | 2,94 → 2,72 | 0, 0, 0 % |
| imp-8-callback | 8 | 100 000 | suspendierend 50 ms | 6228 | 8008 (54) | 118; 958 / 8395 / 47 688 | 14 | 11 | 2,72 → 2,48 | 0, 0, 0 % |
| imp-8-callback-direkt | 8 | 20 000 | direkt 50 ms | 4623 | 7008 (27) | 73; 3404 / 21 625 / 58 922 | 13 | 11 | 2,48 → 2,33 | 0, 0, 0 % |
| imp-8-callback-unterbrechbar | 8 | 20 000 | unterbrechbar 50 ms (Pool) | 1481 | keine Probe (0) | 19; 1321 / 7775 / 7775 | 77 | 11 | 2,33 → 2,18 | 0, 0, 0 % |
| imp-1-sender | 1 | 5000 | ohne Warten; Sender 1 ms je Zeile | 5591 | 996 (40) | 98; 1699 / 3036 / 5974 | 13 | 4 | 2,18 → 2,16 | 0, 0, 0 % |

In jeder Zelle erreichte der Callback alle Zeilen (`importiert_delta` gleich `erwartet`). „Keine Probe“: die Zelle endete, bevor die Probe-Sitzung ihre erste Anfrage beantwortet bekam; für diese Zellen steht kein Vorlauf in der Messzeile.

Lesart (Bedingung: `SqlBulkCopy` mit `BatchSize` 1000 gegen das Simulations-Backend, dieser Host): Der Sender läuft dem Callback höchstens um einen Stapel je Sitzung voraus (1001 bei einer Sitzung, 8008 und 7008 bei acht: acht Stapel, ein Stapel je Sitzung plus Probeunschärfe), auch bei langsamem Callback (50 ms je Stapel) und bei langsamem Sender (996 bei 1 ms je Zeile). Der Grund liegt im Muster des Clients: jeder Stapel ist eine Nachricht, deren Antwort der Client abwartet, bevor er den nächsten sendet; das ist **keine** Aussage über einen Puffer des Servers. Der Heap blieb nach der Speicherbereinigung bei 4 bis 11 MB. Ein Callback, der im Thread des Aufrufers schläft (`direkt`), ließ den Nachbarn 3,4 ms im Median, 21,6 ms im 95. Rang warten (Zelle mit 8 Sitzungen und 20 000 Zeilen), im Gegensatz zum Export mit `direkt` (1,8 s): die Sitzungen geben dem Server zwischen den Stapeln Luft; das ist beobachtet, die Ursache ist nicht untersucht.

### Frist und langsamer Leser

Zelle `leser-frist` (Commit `769de2cc`, Last 1 min 6,6 → 5,9; keine Probe fremder CPU: die Zelle dauerte unter 10 s): `QUERY_TIMEOUT` 3 s; der Leser liest eine Zeile und hält 6 s an. Nach dem Weiterlesen (12 ms) lieferte der Client noch 1500 Zeilen, dann den Fehler `Timeout.` (`TDS_TIMEOUT`); der Strom stand danach als abgebrochen im Zähler (`abgebrochen_delta` 1, `offen_ende` 0).

Lesart (Bedingung: ein Leser, der länger als `QUERY_TIMEOUT` angehalten ist): Die Frist wirkt erst, wenn der Schreibvorgang am Rückstau zurückkehrt (so steht es in `SPEC-009`); ein Leser, der länger anhält als `QUERY_TIMEOUT`, bekommt die Zeilen aus den Puffern und danach `TDS_TIMEOUT`. In den ersten Läufen dieser Messung (Vorläufe unten) traf das die Stufen 64 und 256 halb, weil die schnellen Sitzungen länger als 30 s brauchten.

## Vorläufe, die nicht in die Zahlen eingehen

Drei Läufe aller Zellen und mehrere Läufe einzelner Zellen gingen dem Lauf auf Commit `4eecf536` voraus; ihre Ausgaben liegen nicht im Repo, ihre Befunde betreffen den Messaufbau:

1. Erster Lauf: drei Leser-Zellen (64, 256 halb, Simulation 64) endeten mit `Timeout.` des Lesers: das `QUERY_TIMEOUT` von 30 s war kleiner als die Dauer der schnellen Sitzungen. Behoben durch `QUERY_TIMEOUT` 3600 s in den Leser-Zellen; der Befund steht als Zelle `leser-frist`.
2. Abbruch-Zellen ohne Wirkung: das Ende kam erst nach 30 s mit `Timeout.` (Frist des Servers), gelesen 8 Zeilen statt 2. Vermutet (nicht einzeln geprüft): `Cancel` kam, bevor die Anfrage lief, weil der Thread-Pool des Clients unter Last verzögert startete. Behoben: der Client wartet, bis der Server den Strom als offen zählt, und `ThreadPool.SetMinThreads` ist gesetzt; danach endete derselbe Aufbau in 9 ms.
3. Zweiter und dritter Lauf: der Thread-Pool des Clients startete die Sitzungen gestaffelt (bei 256 Sitzungen bis zu 38 s nach der ersten, `start_max_ms`): die Gleichzeitigkeit der Stufe 256 war nicht gegeben, die Sitzungsdauern bei Stufe 256 und die Dauer von „weiterlesen“ bei 128 langsamen Lesern (148 s) waren ein Artefakt des Clients. Behoben: je Sitzung ein eigener Thread (`start_max_ms` ≤ 38 ms). Die Zahlen dieser Läufe stehen nirgends.

## Wächter und rot gesehene Gegenbeispiele

Die Bausteine des Aufbaus haben Tests in `testserver/test/kotlin/io/github/pt9912/tdswire/testserver/RueckstauTest.kt` (laufen in `make test`). Je Zusage eine Änderung am geprüften Code, die den Test rot machte (einmal gesehen, Lauf von `make test`):

| Zusage | Mutation | roter Test |
|---|---|---|
| Das Backend zählt nur die gezogenen Zeilen | im Strom `addAndGet(2)` statt `incrementAndGet()` | `das Backend zaehlt nur die Zeilen, die der Strom gezogen hat` |
| Ein Abbruch zählt als abgebrochen | Ende und Abbruch beide als beendet gezählt | derselbe Test |
| `nicht-unterbrechbar` hält den Abbruch auf | schlafe unterbrechbar (`runInterruptible`) | `ein nicht unterbrechbarer Aufruf haelt den Abbruch auf` |
| `unterbrechbar` endet mit dem Abbruch | ohne `runInterruptible` | `ein unterbrechbarer Aufruf endet mit dem Abbruch` |

**Keine rot gesehene Mutation** gibt es für den Test `der direkte Aufruf blockiert den Aufrufer`, für `die Angaben der Anfrage werden gelesen`, `die Tabelle der Zaehler liefert eine Zeile` und `die Tabellen des Aufbaus C zaehlen Quelle und Import`, und für die Zusagen des .NET-Clients (jede Sitzung erreicht alle Zeilen; Szenarien): sie laufen nur über die Prozessgrenze mit `make rueckstau`, das kein Gate ist, und eine Mutation des Servers dort wurde nicht gefahren.

## Nicht gemessen

Ein Netz zwischen Hosts; ein echter Treiber hinter dem Backend (der Pool ist eine Setzung dieser Messung); andere Clients als `Microsoft.Data.SqlClient` 7.1.1; TLS und TDS 8.0; Wiederholungen und Streuung (je Zelle ein Lauf); andere JVM-Versionen und andere Prozessorzahlen (die JVM sah 2 Prozessoren); Wirkung einer blockierenden Senke der Telemetrie; ein Schreibvorgang, der die Sitzungssperre am Rückstau hält und damit Frist und Abbruch aufschiebt (`SPEC-009`, `SPEC-033`): in den Abbruch-Zellen hing kein Schreibvorgang am Rückstau, und der Fall wurde nicht gesucht; blockierendes Lesen der Zeilenquelle des Simulations-Backends (nur das eigene Backend wartete beim Lesen, beim Simulations-Backend nur der Stapel-Callback beim Schreiben); der Vorlauf des Simulations-Backends in der Staffel mit mehreren langsamen Lesern.
