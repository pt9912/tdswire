# Performance: Durchsatz, p95 und Sitzungen, Zwischenstand vom 2026-11-17

Messprotokoll zu [TDS-QA-04](../../spec/lastenheft.md#tds-qa-04--performance) (`AGENTS.md` §5 Regel 7), Träger: [slice-qa-performance-durchsatz-sitzungen](../plan/planning/in-progress/slice-qa-performance-durchsatz-sitzungen.md). **Es ist ein Zwischenstand und eine Einzelmessung eines Tages auf einem Host. Gemessen ist nur die Stufe 1 der Staffel (eine Sitzung), und sie zum Teil unter fremder Last. Die Stufen 8, 16 und 64 sind nicht gemessen** (siehe „Nicht belegt“). Es ist kein Grenzwert. Erzeuger der Zahlen: `tools/lasttest.sh` mit dem Client unter `referenztest-dotnet/Lasttest/`, beide im Repo (Commit `d5a751eb`); die Rohzeilen stehen in [performance-durchsatz-2026-11-17-rohzeilen.txt](performance-durchsatz-2026-11-17-rohzeilen.txt). Nicht im Repo liegen die Hilfen zur Beobachtung der Fremdlast (Probe der CPU aller Prozesse alle 10 s, Wartelauf auf einen ruhigen Host, Wiederholung je Zelle); ihre Angaben sind nur Größenordnung.

## Bedingungen

| Angabe | Wert |
|---|---|
| Host | Linux 6.8.0-147, 20 Kerne, 31 GiB, Docker 29.9.3; 13,6 bis 16,4 GiB Swap des Hosts belegt (fremde Prozesse; der Container hat `memory.swap.max` 0) |
| Fremde Last | Auf dem Host lief gleichzeitig ein anderer Agent mit Gradle- und Docker-Bauten (Java-Prozesse mit 200 bis 600 % CPU). Lauf 1: 17 von 41 Proben (10 s) mit mehr als 300 % fremder CPU, ab 04:59:28Z, also in allen Zellen außer der ersten; Last 1 min in den Zeilen von 3,5 bis 10,9. Die Zeilen von Lauf 1 sind **unter fremder Last** |
| Lauf 2 | Zellenweise (ein Aufruf je Aufbau, Senke, Phase), vor jeder Zelle Wartelauf auf einen ruhigen Host: zwei Zellen sauber (Zelle C mit `import`: 0 Proben, Zelle B ohne `punkt-export`: 1 Probe mit Fremdlast). Danach ruhte die Messung auf Anweisung (Verifier-Läufe auf dem Host) |
| Limits | Server: `--cpus 2`, `--memory 4g`, `--memory-swap 4g`, `-Xmx256m`; Client: `--cpus 2`, `--memory 2g`, `--memory-swap 2g` (unverändert, Workload S8) |
| Workload | `ref_zeilen`, 1 000 000 Zeilen, Seed 42, 5 Läufe nach einem Warmlauf, TDS 7.4 ohne Verschlüsselung; Punktabfrage 1000 Anfragen je Lauf, Perzentile als Rang (aufgerundet) der Zeit je Anfrage am Client |
| Stand | Lauf 1: Commit `e2b16d45` (Arbeitsbaum mit der benannten Client-Container-Änderung geändert), Lauf 2: `d5a751eb` bzw. `+geaendert` laut Kopf in den Rohzeilen |

## Stufe 1 (eine Sitzung), Lauf 1 unter fremder Last

Millisekunden über alle 1 000 000 Zeilen; Zeilen/s aus dem Median; erste Zeile 1 ms (Minimum 1, Maximum 4) in jeder Exportzeile.

| Aufbau | Senke | Richtung | Median ms | Min ms | Max ms | Zeilen/s | Byte/Zeile | Streuung |
|---|---|---|---|---|---|---|---|---|
| B | mit | Export | 9970 | 9509 | 10292 | 100 300 | 330 | ok |
| B | ohne | Export | 10077 | 9895 | 10374 | 99 235 | 330 | ok |
| C | mit | Export | 10619 | 10091 | 10870 | 94 170 | 329 | ok |
| C | ohne | Export | 10776 | 10551 | 10932 | 92 798 | 330 | ok |
| C | mit | Import | 5939 | 5566 | 6493 | 168 378 | 336 | ok |
| C | ohne | Import | 5249 | 5118 | 5270 | 190 512 | 336 | ok |

Punktabfrage, Mikrosekunden: Median (Minimum bis Maximum) der Perzentile über die 5 Läufe; Stufe 1 hat keine Last-Sitzung, die Zeile misst die Anfrage allein. Die Zeile „C mit Import“ trägt keine Last aus anderen Sitzungen, der Name der Phase ist hier nur die Last-Art der höheren Stufen.

| Aufbau | Senke | p50 | p95 | p99 |
|---|---|---|---|---|
| B | mit | 249 (226–310) | 348 (332–468) | 463 (406–628) |
| B | ohne | 213 (155–222) | 305 (250–381) | 392 (350–474) |
| C | mit | 293 (237–453) | 702 (409–1127) | 1542 (787–2380) |
| C | ohne | 268 (237–316) | 375 (341–650) | 523 (414–2368) |

(Die Phase `punkt-import` bei Stufe 1 ergab C mit p50 227, p95 351, p99 436 und C ohne p50 193, p95 315, p99 390; sie unterscheidet sich von `punkt-export` bei einer Sitzung nur durch Zufall und Fremdlast, siehe Rohzeilen.)

## Lauf 2, saubere Zellen

| Zelle | Ergebnis |
|---|---|
| C mit Import | Median 3982 ms (Min 3888, Max 4768), 251 130 Zeilen/s, 336 Byte/Zeile, Last 1 min 3,54 vor und 4,56 nach der Zelle |
| B ohne Punktabfrage | p50 270, p95 470, p99 890 µs (Mediane), Last 9,16 vor und 9,47 nach der Zelle (Fremdlast in einer Probe, Höchstwert 549 %) |

C mit Import liegt 33 bis 53 % über den Zeilen von Lauf 1 (3982 gegen 5939 und 5249 ms): die Zahlen von Lauf 1 sind unter Fremdlast und tragen nicht.

## Dauer je Schicht (Senke mit)

`tds.query` und `backend.execute` aus dem Log (`duration_ms`, ganze Millisekunden abgerundet, je Anfrageart über `request_id` zugeordnet). Export Stufe 1: je 6 Spans (Warmlauf und 5 Läufe) mit dem Median nahe der Wanduhr des Laufs (z. B. B mit Export `tds.query` 10 782 ms, `backend.execute` 10 781 ms im ersten Probelauf, Rohzeilen); die Differenz beider Spans liegt unter der Auflösung von 1 ms bis wenigen ms, weil `backend.execute` den Strom einschließt (Workload Abschnitt 3.6). Die Punktabfrage (6000 Spans) zeigt Median 0 ms, höchstens 35 ms (Auflösung 1 ms: nicht aussagekräftig unterhalb von 1 ms).

## Befund: Frist der Anfrage

Mit der Vorgabe `QUERY_TIMEOUT` von 30 s ([SPEC-009](../../spec/spezifikation.md#5-metriken-und-tracing-felder) gilt als Quelle der Frist, Tabelle der Konfiguration) endete bereits der erste Export der Stufe 8 (8 Sitzungen, je 1 000 000 Zeilen, Aufbau B) nach 30 002 bis 30 044 ms mit `TDS_TIMEOUT`: die acht Ströme teilen sich 2 CPUs, ein Strom dauert dann über 30 s (bei einer Sitzung 10 s). Das Werkzeug kennt deshalb `LASTTEST_QUERY_TIMEOUT` (Sekunden, an den Server als `QUERY_TIMEOUT`); die Stufen 8, 16 und 64 brauchen sie. Ob die Vorgabe von 30 s für einen Betreiber der Referenzlast passt, ist eine Frage an das Zielbild und steht bei [slice-qa-performance-grenzwerte](../plan/planning/open/slice-qa-performance-grenzwerte.md).

## Nicht belegt

- **Stufen 8, 16 und 64**: Export, Import, Punktabfrage unter Last und Dauer je Schicht sind nicht gemessen. Grund: der Host war während der ganzen Messzeit durch Bauten eines anderen Agenten belegt, und auf Anweisung ruht die Messung seit den Verifier-Läufen. Das Werkzeug und der Selbsttest liegen im Repo (`make skript-selbsttest`), die Stufen sind mit `LASTTEST_SITZUNGEN=<N> LASTTEST_ZEILEN=<Z> LASTTEST_QUERY_TIMEOUT=3600 make lasttest` bei ruhigem Host zu fahren (Z 1 000 000 für 1 und 8, sonst 100 000).
- Stufe 1: Export B mit und ohne Senke, Export C und Import C ohne Senke, die vier Punktzeilen und die Spans liegen nur unter Fremdlast vor; Wiederholung bei ruhigem Host steht aus. Ein Einfluss der Senke ist nicht belegt (Unterschiede liegen unter der Streuung und Fremdlast).
- **Import in Aufbau B**: die DoD nennt einen Import in ein zählendes Backend in Aufbau B; der freigegebene Workload sagt, ein eigenes Backend nimmt kein Massenladen an (Abschnitt 3.6). Der Import ist deshalb nur in Aufbau C gemessen; die Auflösung dieses Widerspruchs steht beim Nutzer.
- Ein Grenzwert, Rückstau, Heap, TLS, ein anderer Client, Streuung über mehrere Tage.
