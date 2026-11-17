# Performance: Import in Aufbau B, C und A am Referenz-Workload, 2026-11-17

Messprotokoll zu [TDS-QA-04](../../spec/lastenheft.md#tds-qa-04--performance) (`AGENTS.md` §5 Regel 7), Träger: [slice-bulk-fuer-eigene-backends](../plan/planning/in-progress/slice-bulk-fuer-eigene-backends.md); Fortsetzung von [performance-durchsatz-2026-11-17](performance-durchsatz-2026-11-17.md), dessen Abschnitt „Stand der DoD-Zeile Import in Aufbau B“ auf dieses Protokoll zeigt. **Es ist eine Einzelmessung eines Tages auf einem Host, und kein Grenzwert.** Gemessen ist der Import (`SqlBulkCopy`, `BatchSize` 1000, Phase `import`) in Aufbau B (eigenes Backend mit `BulkLoadingBackend`, `ZeilenBackend`, zählendes `load` ohne Speicherung), in Aufbau C (Simulations-Backend, `onInsertBatch` zählt) und, für eine Sitzung, im Aufbau A (Stapel-Callback ohne Draht und Client). **C wurde in demselben Aufruf der Läufe und im selben Zustand des Hosts neu gemessen; die Zahlen von C im Protokoll [performance-durchsatz-2026-11-17](performance-durchsatz-2026-11-17.md) sind kein Vergleichswert** (sie liefen neben Läufen anderer Agenten). Erzeuger der Zahlen: `tools/lasttest.sh` mit dem Client unter `referenztest-dotnet/Lasttest/` (beide im Repo, Zustand von Commit `9f236f41`, Arbeitsbaum sauber: die Kopfzeilen der Rohzeilen nennen den Commit ohne `+geaendert`); Rohzeilen aller Zellen: [performance-import-b-2026-11-17-rohzeilen.txt](performance-import-b-2026-11-17-rohzeilen.txt); die Tabellen setzt `tools/lasttest-tabellen.sh` aus den Rohzeilen (`make lasttest-tabellen ROHZEILEN=docs/messungen/performance-import-b-2026-11-17-rohzeilen.txt STUFEN="stufe1 stufe8 stufe16 stufe64"`; die Tabelle „import“ unten und die Tabelle „Dauer je Schicht“ der Ausgabe stehen hier ohne Änderung). **Nicht im Repo** liegen die Hilfen zum Ablauf und zur Fremdlast: eine Schleife, die je Zelle `make lasttest` ruft (vor jeder Zelle Prüfung, dass kein Container `tdswire-rs-*` oder `tdswire-lt-*` und kein Lauf von `tools/lasttest.sh` fremd läuft) und eine Probe der CPU aller Prozesse alle 8 s (Host aus `/proc/stat` abzüglich der Container dieses Laufs aus `docker stats`). Die Spalte „Fremdlast“ stammt aus dieser Probe und **ist nicht reproduzierbar, nur Größenordnung**; ihre Wirkung auf die Zahlen ist nicht gemessen. Die Zeilen von `tools/lasttest.sh` sind mit denselben Aufrufen wiederholbar (Muster: `make lasttest LASTTEST_AUFBAUTEN=B LASTTEST_SENKEN=mit LASTTEST_PHASEN=import LASTTEST_SITZUNGEN=8 LASTTEST_ZEILEN=1000000 LASTTEST_WDH=5 LASTTEST_QUERY_TIMEOUT=3600`); die Zahlen sind es nicht.

## Bedingungen

| Angabe | Wert |
|---|---|
| Datum, Host | 2026-11-17 von 10:10 bis 10:31 UTC, Linux 6.8.0-147, 20 Kerne, 31 GiB, Docker 29.9.3; Swap des Hosts rund 12 GiB belegt (fremde Prozesse; `memory.swap.max` des Containers 0) |
| Fremdlast | Der Host war während der Läufe ruhiger als bei [performance-durchsatz-2026-11-17](performance-durchsatz-2026-11-17.md): Last 1 min in den Rohzeilen von 1,50 bis 8,63; es lief **kein** Lauf eines anderen Messslices (Prüfung vor jeder Zelle, siehe oben). Neben den Läufen liefen die Werkzeuge des Hosts (unter anderem `suricata`, ein Browser). Die Probe nennt je Zelle die Zahl der Proben (8 s) mit mehr als 300 % fremder CPU und den Höchstwert: je Zelle höchstens eine Probe über 300 % (Höchstwert 484 %), keine Zelle ist als „gestört“ gekennzeichnet; die Probe hat 2 bis 11 Proben je Zelle (nur der Teil, in dem ein Container dieses Laufs läuft) |
| Wiederholte Zellen | Die ersten Läufe der Zellen Stufe 1, B, mit Senke und Stufe 1, C, mit Senke (Abschnitte `verworfen-…` der Rohzeilen) zählten in der Probe den Bau der Images mit (Fehler des Hilfsskripts, nach den ersten beiden Zellen behoben); sie sind wiederholt, die Wiederholungen stehen in den Tabellen. Die verworfenen Läufe haben die Mediane 3185 ms (B, 3050 bis 3265) und 3511 ms (C, 3447 bis 3664) und stehen nur in den Rohzeilen |
| Limits | Server: `--cpus 2`, `--memory 4g`, `--memory-swap 4g`, `-Xmx256m`; Client: `--cpus 2`, `--memory 2g`, `--memory-swap 2g` (Workload S8); `QUERY_TIMEOUT` 3600 s für die Stufen 8, 16 und 64, Stufe 1 mit der Vorgabe des Servers |
| Methode | Je Zelle (Stufe, Aufbau, Senke) ein Aufruf von `make lasttest` mit frischem Server und der Phase `import`. Stufe N: N gleichzeitige Sitzungen, die gemeinsam freigegeben werden; 1 000 000 Zeilen je Sitzung bei den Stufen 1 und 8, 100 000 bei 16 und 64 (Workload S2); 5 Läufe nach einem Warmlauf (S11); Zeilen/s sind die Zeilen aller Sitzungen durch die Wanduhr von der gemeinsamen Freigabe bis zur letzten Sitzung (Median); die Reihenfolge je Stufe war B mit, C mit, B ohne, C ohne. Die Zählprüfung von `tools/lasttest.sh` (in C seit dem Vorgänger, in B gleich gebaut) zählt die Zeilen `lasttest: import <Summe>`, die das Backend schreibt, wenn sein Zähler über alle Sitzungen und Läufe ein Vielfaches der Zeilenzahl überschreitet, und vergleicht ihre Zahl mit Sitzungen × (Läufe + 1); sie belegt, dass die Summe aller geladenen Zeilen erreicht ist, nicht, dass jede Sitzung in jedem Lauf alle ihre Zeilen geladen hat. Referenzclient `Microsoft.Data.SqlClient` 7.1.1, TDS 7.4 ohne Verschlüsselung |

## Ergebnisse

### Import (Stufen 1, 8, 16, 64)

Millisekunden über die Zeilen je Sitzung; „Zeilen/s“ gesamt über alle Sitzungen und je Sitzung. Alle 16 Zellen tragen `Streuung ok` (Maximum höchstens das Doppelte des Minimums, S11) und keinen Vermerk `last-hoch` oder `swap-gewachsen`.

| Stufe | Aufbau | Senke | Median ms | Min ms | Max ms | Zeilen/s gesamt | je Sitzung | Byte/Zeile | Streuung | Last 1 min vor/nach | Vermerk | Fremdlast, Probe außerhalb des Repos (Proben > 300 %, Höchstwert) | Versuch |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | B | mit | 3172 | 2953 | 3307 | 315258 | 315258 | 336 | ok | 3.48 / 3.38 | - | 0 / 152 % | 1 von 1 |
| 1 | B | ohne | 2998 | 2947 | 3301 | 333555 | 333555 | 336 | ok | 2.96 / 3.14 | - | 0 / 118 % | 1 von 1 |
| 1 | C | mit | 3609 | 3296 | 3867 | 277085 | 277085 | 336 | ok | 3.35 / 3.24 | - | 0 / 165 % | 1 von 1 |
| 1 | C | ohne | 3560 | 3353 | 3669 | 280898 | 280898 | 336 | ok | 3.13 / 3.02 | - | 0 / 168 % | 1 von 1 |
| 8 | B | mit | 14573 | 14387 | 15284 | 548960 | 68620 | 336 | ok | 3.02 / 1.77 | - | 1 / 418 % | 1 von 1 |
| 8 | B | ohne | 14594 | 13982 | 14873 | 548170 | 68521 | 336 | ok | 2.05 / 1.54 | - | 0 / 174 % | 1 von 1 |
| 8 | C | mit | 15498 | 14283 | 15901 | 516195 | 64524 | 336 | ok | 1.90 / 1.80 | - | 1 / 475 % | 1 von 1 |
| 8 | C | ohne | 15074 | 14898 | 15941 | 530715 | 66339 | 336 | ok | 1.50 / 6.62 | - | 0 / 215 % | 1 von 1 |
| 16 | B | mit | 3203 | 2995 | 3509 | 499531 | 31220 | 337 | ok | 6.41 / 8.20 | - | 1 / 435 % | 1 von 1 |
| 16 | B | ohne | 3448 | 3053 | 3620 | 464037 | 29002 | 337 | ok | 7.28 / 5.50 | - | 0 / 179 % | 1 von 1 |
| 16 | C | mit | 3487 | 3116 | 3705 | 458847 | 28677 | 337 | ok | 7.41 / 7.48 | - | 1 / 484 % | 1 von 1 |
| 16 | C | ohne | 3197 | 3001 | 3474 | 500469 | 31279 | 337 | ok | 5.50 / 4.28 | - | 0 / 164 % | 1 von 1 |
| 64 | B | mit | 14872 | 14662 | 15246 | 430338 | 6724 | 337 | ok | 4.09 / 2.84 | - | 1 / 314 % | 1 von 1 |
| 64 | B | ohne | 14470 | 14299 | 15134 | 442294 | 6910 | 337 | ok | 7.00 / 2.99 | - | 0 / 205 % | 1 von 1 |
| 64 | C | mit | 14803 | 14458 | 15234 | 432344 | 6755 | 337 | ok | 3.13 / 8.63 | - | 1 / 373 % | 1 von 1 |
| 64 | C | ohne | 14509 | 14262 | 14943 | 441105 | 6892 | 337 | ok | 2.91 / 3.82 | - | 1 / 344 % | 1 von 1 |

### Import in Aufbau A (eine Sitzung, 1 000 000 Zeilen)

Aufbau A ruft den Stapel-Callback des Simulations-Backends in Stapeln zu 1000 Zeilen ohne Server, Draht und Client (Zeile `messung:` der Zelle `stufe1-A-ohne-import-v1`; Last 1 min vor und nach 3,54 und 3,17, Probe 2 Proben, 0 über 300 %, Höchstwert 162 %): Median 1184 ms (Minimum 1182, Maximum 1191), 844 594 Zeilen/s, `Streuung ok`. Die Zelle trägt auch die Zeilen des Exports in A (Erzeuger 1061 ms, Simulations-Backend 1172 ms); er ist nicht Gegenstand dieses Protokolls.

### Dauer je Schicht (Senke mit)

Die Spans `tds.query` und `backend.execute` je Nachricht des Imports (Art `sql_batch`, auch der `INSERT BULK` vor jeder Nachricht): Median 0 ms in allen Zellen bis auf eine (Stufe 64, C, `tds.query`, Median 1 ms); Maximum bei Stufe 1 höchstens 31 ms, bei Stufe 8 höchstens 70 ms, bei Stufe 16 höchstens 111 ms und bei Stufe 64 höchstens 115 ms (Auflösung 1 ms, abgerundet; Ausgabe von `make lasttest-tabellen`, Zeilen `messung-span:` der Rohzeilen). Die Spans sagen für den Import nichts über die Eigenzeit des Backends (Workload 3.6).

## Lesart (nur so weit die Zeilen tragen)

Rechnungen sind Verhältnisse der Mediane der Tabelle (B gegen C, in Prozent der Zeilen/s von C), keine Messwerte.

- **Aufbau B nimmt den Import an und zählt alle Zeilen:** in allen 16 Zellen erreicht die Summe der geladenen Zeilen den Sollwert (Zählprüfung von `tools/lasttest.sh`, Grenze siehe Methode; sie ist rot, wenn nicht), `SqlBulkCopy` stellt gegen B neben der Probe der Zieltabelle und `INSERT BULK` keine Anfrage, die `ZeilenBackend` beantworten müsste (keine Zelle endet mit einem Fehler des Clients; zusätzlich zählt `make eigenes-backend` keine Anfrage unbekannten Textes).
- **Eine Sitzung (Stufe 1):** B liegt über C: mit Senke 315 258 gegen 277 085 Zeilen/s (+13,8 %), ohne Senke 333 555 gegen 280 898 (+18,8 %). Die Bereiche Minimum bis Maximum überlappen mit Senke um 11 ms (B bis 3307 ms, C ab 3296 ms) und sind ohne Senke getrennt (B bis 3301 ms, C ab 3353 ms). Bei einer Sitzung ist B damit von C unterscheidbar, mit Senke knapp; das gilt für diese Messfolge: die Reihenfolge der Zellen je Stufe war fest (B mit, C mit, B ohne, C ohne), ein Gang des Hosts über die 21 Minuten geht in den Vergleich B gegen C ein, und die Probe der Fremdlast ist nicht reproduzierbar.
- **Acht Sitzungen (Stufe 8):** B über C um 6,35 % mit Senke (548 960 gegen 516 195) und 3,3 % ohne Senke (548 170 gegen 530 715); mit Senke überlappen die Bereiche, ohne Senke sind sie getrennt (B bis 14 873 ms, C ab 14 898 ms, Abstand 25 ms).
- **16 und 64 Sitzungen:** die Bereiche überlappen in allen vier Zellen, das Vorzeichen wechselt (Stufe 16: B +8,9 % mit Senke, −7,3 % ohne Senke; Stufe 64: −0,5 % mit Senke, +0,3 % ohne Senke). **B ist dort von C nicht unterscheidbar** (Negativ der Messung nach der Entscheidung für die Fläche, kein Rückbau).
- **Plateau:** ab 8 Sitzungen liegen die Zeilen/s gesamt zwischen 430 338 und 548 960; je Sitzung fallen sie mit der Zahl der Sitzungen (68 620 bei 8, 31 220 bei 16, 6 724 bei 64 in B mit Senke). Die Grenze kann Server oder Client sein (die CPU beider ist ungemessen, beide haben 2 CPUs); bei 16 und 64 Sitzungen liegt der Unterschied zwischen B und C innerhalb der Streuung der Läufe.
- **Gegen A-Import:** bei einer Sitzung braucht A-Import 1184 ms, B 3172 ms (mit Senke) und 2998 ms (ohne) und C 3609 ms (mit) und 3560 ms (ohne); was davon Codec, Sitzung, Transport und Client ist und was der Unterschied im Bau der Zeilen (Maps gegen `BulkRows`), trennt kein Aufbau: der Client ist in B und C nicht abgetrennt, und die Differenz C gegen B enthält die Maps des Simulations-Backends **und** die Überführung der Typen.
- **Senke:** mit gegen ohne Senke liegt bei B zwischen −5,5 % (Stufe 1) und +7,65 % (Stufe 16) und wechselt über die Stufen das Vorzeichen (+0,1 % bei Stufe 8, −2,7 % bei Stufe 64); bei C liegt mit Senke in allen vier Stufen unter ohne Senke (−1,4 %, −2,7 %, −8,3 %, −2,0 %; Zeilen/s je Stufe). Ein Einfluss der Telemetrie ist damit weder belegt noch ausgeschlossen.
- **Gegen das Protokoll vom Vormittag:** die C-Zellen dieses Protokolls liegen bei Stufe 1 mit Senke bei 277 085 Zeilen/s, im Protokoll [performance-durchsatz-2026-11-17](performance-durchsatz-2026-11-17.md) bei 240 963 (damals neben anderen Läufen); beide sind Einzelmessungen unter verschiedener Fremdlast und nicht zu vergleichen.

## Nicht belegt

- Ein Grenzwert, die Streuung über mehrere Tage und Hosts, ein ruhiger Host: die Zellen laufen auf einem Host mit Werkzeugen anderer Prozesse (Probe je Zelle oben, außerhalb des Repos).
- Warum B bei einer Sitzung schneller ist als C (die Zeilen sagen nur, dass er es ist); eine Aufteilung in Anteile von Codec, Transport, Client und Bau der Zeilen.
- `punkt-import` in Aufbau B (Punktabfrage unter Import-Last): nicht gemessen; `tools/lasttest.sh` überspringt die Zelle bis zur Entscheidung des Nutzers.
- Heap und Speicher des Servers beim Import (cgroup-Spitzen stehen in den Rohzeilen, 131 600 384 Bytes in der ersten Zelle, keine Aussage über die Obergrenze), eine Nachricht über 16 MiB (nur der Einbettungstest mit dem Kleinstwert von `RequestLimits.maxMessageBytes`), ein anderer Client als der Referenzclient, d-migrate gegen ein eigenes Backend, TLS und TDS 8.0.
- Die Erkennung der Texte von `INSERT BULK` und der Probe in `ZeilenBackend` (`BulkErkennung` im Testserver) ist für den Referenzclient geschrieben und an diesem gemessen; ihr Aufwand je Anfrage ist in den Zeilen enthalten und nicht getrennt.
