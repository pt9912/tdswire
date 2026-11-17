# Performance: Durchsatz, p95 und gleichzeitige Sitzungen am Referenz-Workload, 2026-11-17

Messprotokoll zu [TDS-QA-04](../../spec/lastenheft.md#tds-qa-04--performance) (`AGENTS.md` §5 Regel 7), Träger: [slice-qa-performance-durchsatz-sitzungen](../plan/planning/in-progress/slice-qa-performance-durchsatz-sitzungen.md). **Es ist eine Einzelmessung eines Tages auf einem Host, durchgehend neben anderen Läufen auf demselben Host, und kein Grenzwert.** Jede Zelle trägt Last 1 min vor und nach dem Lauf (aus den Rohzeilen) und eine Probe der Fremdlast (Spalte „Fremdlast“, **Probe außerhalb des Repos, nicht reproduzierbar, nur Größenordnung**); Zellen mit gestörten Proben gelten nur als Größenordnung. Erzeuger der Zahlen: `tools/lasttest.sh` mit dem Client unter `referenztest-dotnet/Lasttest/` (beide im Repo, Zustand von Commit `d5a751eb`; das Messwerkzeug ist seit diesem Commit unverändert; später geändert wurden Protokoll, Selbsttest des Werkzeugs, Slice-Plan und ADR-Dateien); Rohzeilen aller Zellen: [performance-durchsatz-2026-11-17-rohzeilen.txt](performance-durchsatz-2026-11-17-rohzeilen.txt). **Nicht im Repo** liegen die Hilfen zur Fremdlast und zum Ablauf (Probe der CPU aller Prozesse alle 10 s, getrennt nach „eigen“ (Container dieses Laufs) und „fremd“; Schleife über die Zellen mit Wiederholung bei Abbruch durch den Speicherwächter), die Tabellen unten setzt `tools/lasttest-tabellen.sh` aus den Rohzeilen (`make lasttest-tabellen ROHZEILEN=docs/messungen/performance-durchsatz-2026-11-17-rohzeilen.txt STUFEN="stufe1b stufe8 stufe16 stufe64"`; der Selbsttest läuft in `make skript-selbsttest`; die Ausgabe ist gegen die Tabellen dieses Protokolls ohne Abweichung verglichen). **Grenze:** die Spalte „Fremdlast“, die Wahl des gewählten Versuchs über sie und die Kennzeichnung „gestört“ stammen aus der Probe außerhalb des Repos; das Werkzeug liest nur ihre Zusammenfassung aus den Rohzeilen. Die Rohzeilen tragen je Zelle die Zeilen `messung:`, `messung-span:` und `lasttest: punkt …` (Läufe der Punktabfrage; `last_zeilen` zählt nur Läufe der Last-Sitzungen, die in dieser Zeit abgeschlossen wurden, und ist bei Stufe 8 und gestreamten Millionenströmen 0, auch wenn Sitzungen liefen; es ist kein Maß der Last) sowie die Kopfzeile; die Probe der Fremdlast steht dort nur als Zusammenfassung je Zelle („Probe: …“).

## Bedingungen

| Angabe | Wert |
|---|---|
| Datum, Host | 2026-11-17 von 05:29 bis 07:12 UTC, Linux 6.8.0-147, 20 Kerne, 31 GiB, Docker 29.9.3; Swap des Hosts 12 018 bis 16 383 MiB belegt (fremde Prozesse; `memory.swap.max` des Containers 0) |
| Fremdlast | Gleichzeitig liefen Gradle- und Docker-Bauten anderer Agenten und, auf Anweisung des Nutzers, ein Verifier mit `make clean-build` und `make e2e`; es wurde **nicht** auf einen ruhigen Host gewartet. Die Spalte „Fremdlast“ nennt die Zahl der Proben (10 s) mit mehr als 300 % fremder CPU und den Höchstwert; ab 2 Proben heißt die Zelle „gestört“. Last 1 min vor und nach jeder Zelle steht in jeder Zeile; Last 1 min in den Rohzeilen von 2,69 bis 15,38 (der Vermerk `last-hoch`, Last über 12, wird nur auf die Last **nach** der Zelle vergeben und steht in einer Rohzeile) |
| Speicher | verfügbar in den Rohzeilen von 3816 MiB (Versuch 1 der Zelle Stufe 8, B, mit Senke, `punkt-export`, siehe unten) bis 15 619 MiB; der Wächter (Abbruch unter 4096 MiB, Exit 3) brach einige Zellen vor dem Start ab, sie wurden wiederholt; abgebrochene Läufe sind nicht in den Zahlen |
| Limits | Server: `--cpus 2`, `--memory 4g`, `--memory-swap 4g`, `-Xmx256m`; Client: `--cpus 2`, `--memory 2g`, `--memory-swap 2g` (unverändert, Workload S8); `QUERY_TIMEOUT` 3600 s für die Stufen 8, 16, 64 (`LASTTEST_QUERY_TIMEOUT`), Stufe 1 mit der Vorgabe des Servers |
| Methode | Je Zelle (Aufbau, Senke, Phase) ein Aufruf von `make lasttest` mit frischem Server. Stufe N: N gleichzeitige Sitzungen (Phasen `export`, `import`), bzw. eine Sitzung mit der Punktabfrage und N − 1 Sitzungen, die den Export streamen oder den Import laden (`punkt-export`, `punkt-import`; Stufe 1 ohne Last-Sitzung). 1 000 000 Zeilen je Sitzung bei den Stufen 1 und 8, 100 000 bei 16 und 64 (Workload S2); 5 Läufe nach einem Warmlauf; Punktabfrage 1000 Anfragen je Lauf, Perzentile als Rang (aufgerundet) der Zeit je Anfrage am Client; Zeilen/s sind die Zeilen aller Sitzungen durch die Wanduhr von der gemeinsamen Freigabe bis zur letzten Sitzung (Median); „erste Zeile“ ist der Median über die Sitzungen. Referenzclient `Microsoft.Data.SqlClient` 7.1.1, TDS 7.4 ohne Verschlüsselung |
| Stand | Stufe 1 (Wiederholung, `stufe1b`), 8, 16, 64: Commits `e483d17b`, `a39bd9e4` (Quelltext von `d5a751eb`); früherer Lauf von Stufe 1 siehe unten |

## Ergebnisse

Millisekunden über die Zeilen je Sitzung; „Zeilen/s“ gesamt über alle Sitzungen und je Sitzung; Punktabfrage in Millisekunden (Median der Perzentile der 5 Läufe, in Klammern Minimum bis Maximum). Stufe 1 ist die Wiederholung `stufe1b` (Fremdlast je Zelle in der Spalte, bis 224 % in der Probe, Last 1 min vor der Zelle 4,13 bis 10,15). Die Zeilen `sql_batch` unter „Dauer je Schicht“ in den Phasen `punkt-*` sind die abgebrochenen Ströme der Last-Sitzungen, nicht die Punktabfrage (`rpc`). **Zwei Zellen tragen `Streuung zu-gross`** (Stufe 16 C mit Senke `punkt-export`, Stufe 1 C mit Senke `punkt-import`): nach S11 sind sie nicht belegt, ihre Werte stehen hier und in der Lesart nur zur Ansicht.

**Versuch 1 der Zelle Stufe 8, B, mit Senke, `punkt-export`** lief vollständig (p50 2,43 ms, p95 5,45 ms, p99 19,6 ms, Vermerk `last-hoch swap-gewachsen`, verfügbarer Speicher danach 3816 MiB) und ist in den Rohzeilen (`stufe8-B-mit-punkt-export-v1`); seine Probe ist nicht zuzuordnen (die Datei trug Proben eines anderen Aufrufs). `tools/lasttest-tabellen.sh` wählt je Zelle den ersten Versuch mit höchstens einer Probe über 300 % und sonst den letzten; Versuch 1 gilt ohne zugehörige Probe nicht als sauber, darum zeigt die Tabelle Versuch 2 (Spalte „Versuch“: „2 von 2“, „gestört“ nur, wenn der gewählte Versuch keine saubere Probe hat). Die Wahl hängt damit an der Probe außerhalb des Repos. Beide Läufe sind Einzelmessungen unter Fremdlast; der Unterschied im p95 (Faktor 2) bei derselben Zelle zeigt die Streuung zwischen Läufen und ist ein Grund, die Zahlen nur als Größenordnung zu lesen.


### export

| Stufe | Aufbau | Senke | Median ms | Min ms | Max ms | Zeilen/s gesamt | je Sitzung | erste Zeile ms (min–max) | Byte/Zeile | Streuung | Last 1 min vor/nach | Vermerk | Fremdlast, Probe außerhalb des Repos (Proben > 300 %, Höchstwert) | Versuch |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | B | mit | 9536 | 9284 | 9676 | 104865 | 104865 | 1 (0–2) | 329 | ok | 10.15 / 6.01 | - | 0 / 174 % | 1 von 1 |
| 1 | B | ohne | 9359 | 9255 | 9681 | 106849 | 106849 | 1 (0–2) | 329 | ok | 5.29 / 4.48 | - | 0 / 126 % | 1 von 1 |
| 1 | C | mit | 9541 | 9281 | 9874 | 104810 | 104810 | 1 (1–2) | 329 | ok | 4.30 / 3.96 | - | 0 / 126 % | 1 von 1 |
| 1 | C | ohne | 9505 | 9337 | 9776 | 105207 | 105207 | 1 (1–3) | 329 | ok | 5.23 / 4.37 | - | 0 / 103 % | 1 von 1 |
| 8 | B | mit | 39167 | 38941 | 41940 | 204253 | 25531 | 2 (1–4) | 328 | ok | 13.93 / 11.39 | - | 17 / 949 % | 1 von 1, gestört |
| 8 | B | ohne | 40398 | 38783 | 41146 | 198029 | 24753 | 2 (2–28) | 329 | ok | 9.97 / 9.81 | swap-gewachsen | 13 / 630 % | 1 von 1, gestört |
| 8 | C | mit | 39246 | 36829 | 41120 | 203842 | 25480 | 2 (2–8) | 328 | ok | 11.41 / 8.39 | - | 12 / 838 % | 1 von 1, gestört |
| 8 | C | ohne | 37821 | 37106 | 38907 | 211522 | 26440 | 3 (2–4) | 329 | ok | 8.60 / 7.88 | - | 10 / 695 % | 1 von 1, gestört |
| 16 | B | mit | 7559 | 7529 | 7729 | 211668 | 13229 | 3 (2–6) | 329 | ok | 11.90 / 7.88 | - | 1 / 617 % | 1 von 1 |
| 16 | B | ohne | 7309 | 7093 | 7863 | 218908 | 13681 | 3 (2–25) | 328 | ok | 9.56 / 8.85 | - | 2 / 638 % | 1 von 1, gestört |
| 16 | C | mit | 7909 | 7584 | 8400 | 202301 | 12643 | 4 (3–7) | 329 | ok | 8.91 / 7.96 | - | 3 / 697 % | 1 von 1, gestört |
| 16 | C | ohne | 7435 | 7200 | 7921 | 215198 | 13449 | 6 (4–12) | 328 | ok | 7.78 / 7.43 | - | 0 / 175 % | 1 von 1 |
| 64 | B | mit | 27989 | 27708 | 30360 | 228661 | 3572 | 9 (6–10) | 328 | ok | 7.43 / 6.94 | - | 0 / 185 % | 1 von 1 |
| 64 | B | ohne | 29802 | 29351 | 31559 | 214750 | 3355 | 8 (5–13) | 328 | ok | 7.61 / 7.00 | - | 0 / 270 % | 1 von 1 |
| 64 | C | mit | 32346 | 30836 | 33188 | 197860 | 3091 | 13 (9–54) | 328 | ok | 7.16 / 8.79 | - | 12 / 853 % | 1 von 1, gestört |
| 64 | C | ohne | 28070 | 27976 | 28475 | 228001 | 3562 | 9 (6–44) | 328 | ok | 4.25 / 4.00 | - | 0 / 96 % | 1 von 1 |

### import

| Stufe | Aufbau | Senke | Median ms | Min ms | Max ms | Zeilen/s gesamt | je Sitzung | Byte/Zeile | Streuung | Last 1 min vor/nach | Vermerk | Fremdlast, Probe außerhalb des Repos (Proben > 300 %, Höchstwert) | Versuch |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | C | mit | 4150 | 3901 | 4352 | 240963 | 240963 | 336 | ok | 4.13 / 5.22 | - | 0 / 135 % | 1 von 1 |
| 1 | C | ohne | 3684 | 3414 | 4026 | 271444 | 271444 | 336 | ok | 4.34 / 4.08 | - | 0 / 97 % | 1 von 1 |
| 8 | C | mit | 15275 | 14682 | 15717 | 523731 | 65466 | 336 | ok | 8.03 / 12.90 | - | 1 / 464 % | 1 von 1 |
| 8 | C | ohne | 14752 | 14588 | 15702 | 542299 | 67787 | 336 | ok | 8.61 / 8.73 | - | 4 / 539 % | 1 von 1, gestört |
| 16 | C | mit | 3195 | 3100 | 3553 | 500782 | 31298 | 337 | ok | 8.35 / 10.68 | - | 1 / 696 % | 1 von 1 |
| 16 | C | ohne | 3203 | 3096 | 3663 | 499531 | 31220 | 337 | ok | 7.05 / 10.37 | - | 0 / 184 % | 1 von 1 |
| 64 | C | mit | 13634 | 13302 | 14194 | 469414 | 7334 | 337 | ok | 8.29 / 6.54 | - | 4 / 575 % | 1 von 1, gestört |
| 64 | C | ohne | 14668 | 14156 | 14995 | 436323 | 6817 | 337 | ok | 3.92 / 2.69 | - | 0 / 100 % | 1 von 1 |

### punkt-export

| Stufe | Aufbau | Senke | p50 ms (min–max) | p95 ms (min–max) | p99 ms (min–max) | Streuung p95 | Last 1 min vor/nach | Vermerk | Fremdlast, Probe außerhalb des Repos (Proben > 300 %, Höchstwert) | Versuch |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | B | mit | 0.22 (0.19–0.37) | 0.35 (0.32–0.57) | 0.45 (0.41–0.74) | ok | 5.85 / 5.70 | - | 0 / 161 % | 1 von 1 |
| 1 | B | ohne | 0.21 (0.17–0.34) | 0.33 (0.28–0.49) | 0.42 (0.34–0.65) | ok | 4.45 / 4.41 | - | 0 / 119 % | 1 von 1 |
| 1 | C | mit | 0.32 (0.26–0.48) | 0.47 (0.38–0.74) | 0.56 (0.43–1.25) | ok | 5.12 / 4.87 | - | 0 / 138 % | 1 von 1 |
| 1 | C | ohne | 0.28 (0.25–0.44) | 0.44 (0.38–0.64) | 0.54 (0.51–0.84) | ok | 5.02 / 5.10 | - | 0 / 167 % | 1 von 1 |
| 8 | B | mit | 1.71 (1.55–1.92) | 2.80 (2.45–3.54) | 25.04 (19.38–43.56) | ok | 10.92 / 10.14 | - | 0 / 157 % | 2 von 2 |
| 8 | B | ohne | 2.07 (1.78–2.37) | 4.12 (3.12–4.92) | 23.35 (16.07–32.73) | ok | 9.62 / 11.04 | - | 3 / 989 % | 1 von 1, gestört |
| 8 | C | mit | 1.88 (1.77–1.94) | 3.13 (2.99–3.21) | 29.79 (21.06–38.06) | ok | 7.81 / 8.36 | - | 9 / 1108 % | 1 von 1, gestört |
| 8 | C | ohne | 2.04 (1.78–2.27) | 3.45 (2.93–4.75) | 26.19 (22.97–32.04) | ok | 8.46 / 10.56 | - | 3 / 973 % | 1 von 1, gestört |
| 16 | B | mit | 3.47 (3.35–3.73) | 5.41 (4.85–6.73) | 29.65 (23.94–37.39) | ok | 8.46 / 9.30 | - | 2 / 506 % | 1 von 1, gestört |
| 16 | B | ohne | 3.37 (3.23–3.74) | 5.13 (4.67–7.92) | 27.09 (19.86–37.15) | ok | 8.85 / 9.07 | - | 2 / 558 % | 1 von 1, gestört |
| 16 | C | mit | 3.84 (3.62–3.92) | 7.39 (5.81–16.53) | 27.34 (26.32–38.01) | zu-gross | 10.18 / 8.42 | - | 2 / 523 % | 1 von 1, gestört |
| 16 | C | ohne | 3.35 (3.31–3.39) | 5.03 (4.88–5.74) | 23.51 (19.32–29.54) | ok | 9.96 / 8.98 | - | 1 / 643 % | 1 von 1 |
| 64 | B | mit | 12.95 (12.72–13.14) | 29.14 (27.33–31.66) | 33.21 (31.49–46.14) | ok | 6.87 / 7.70 | - | 3 / 679 % | 1 von 1, gestört |
| 64 | B | ohne | 12.84 (12.65–13.21) | 28.68 (28.00–29.83) | 31.80 (31.48–39.55) | ok | 7.24 / 7.55 | - | 0 / 238 % | 1 von 1 |
| 64 | C | mit | 12.91 (12.84–14.81) | 27.25 (26.72–33.36) | 32.31 (28.81–47.79) | ok | 6.12 / 5.68 | - | 5 / 687 % | 1 von 1, gestört |
| 64 | C | ohne | 12.64 (12.58–12.72) | 26.15 (24.95–27.58) | 29.45 (27.93–37.69) | ok | 3.04 / 3.83 | - | 0 / 96 % | 1 von 1 |

### punkt-import

| Stufe | Aufbau | Senke | p50 ms (min–max) | p95 ms (min–max) | p99 ms (min–max) | Streuung p95 | Last 1 min vor/nach | Vermerk | Fremdlast, Probe außerhalb des Repos (Proben > 300 %, Höchstwert) | Versuch |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | C | mit | 0.32 (0.27–0.52) | 0.46 (0.42–0.94) | 0.61 (0.51–1.87) | zu-gross | 5.06 / 5.38 | - | 0 / 224 % | 1 von 1 |
| 1 | C | ohne | 0.26 (0.20–0.33) | 0.37 (0.33–0.61) | 0.50 (0.40–0.84) | ok | 5.01 / 5.25 | - | 0 / 133 % | 1 von 1 |
| 8 | C | mit | 0.47 (0.39–0.71) | 2.24 (2.15–2.97) | 43.87 (37.49–47.78) | ok | 8.39 / 8.70 | - | 1 / 455 % | 1 von 1 |
| 8 | C | ohne | 0.39 (0.31–0.55) | 2.31 (2.03–2.87) | 35.13 (30.27–45.44) | ok | 10.54 / 11.14 | - | 2 / 837 % | 1 von 1, gestört |
| 16 | C | mit | 0.70 (0.63–0.98) | 3.45 (3.18–5.54) | 60.15 (59.86–62.26) | ok | 8.05 / 8.56 | - | 0 / 134 % | 1 von 1 |
| 16 | C | ohne | 0.58 (0.53–0.82) | 3.63 (2.90–4.14) | 65.09 (60.90–67.35) | ok | 8.82 / 7.64 | - | 0 / 172 % | 1 von 1 |
| 64 | C | mit | 10.88 (6.36–11.58) | 94.54 (92.63–95.39) | 102.38 (100.11–105.05) | ok | 5.03 / 5.34 | - | 1 / 316 % | 1 von 1 |
| 64 | C | ohne | 10.64 (10.61–11.63) | 95.04 (93.86–95.71) | 102.77 (102.19–104.55) | ok | 3.68 / 10.68 | - | 0 / 94 % | 1 von 1 |

### Dauer je Schicht (Senke mit)

| Stufe | Aufbau | Phase | Span | Art | n | Median ms | Min ms | Max ms |
|---|---|---|---|---|---|---|---|---|
| 1 | B | export | tds.query | sql_batch | 6 | 9549 | 9282 | 10125 |
| 1 | B | export | backend.execute | sql_batch | 6 | 9548 | 9282 | 10113 |
| 1 | B | punkt-export | tds.query | rpc | 6000 | 0 | 0 | 34 |
| 1 | B | punkt-export | backend.execute | rpc | 6000 | 0 | 0 | 31 |
| 1 | C | export | tds.query | sql_batch | 6 | 9629 | 9281 | 10064 |
| 1 | C | export | backend.execute | sql_batch | 6 | 9628 | 9280 | 10028 |
| 1 | C | import | tds.query | sql_batch | 12006 | 0 | 0 | 43 |
| 1 | C | import | backend.execute | sql_batch | 12000 | 0 | 0 | 9 |
| 1 | C | punkt-export | tds.query | rpc | 6000 | 0 | 0 | 48 |
| 1 | C | punkt-export | backend.execute | rpc | 6000 | 0 | 0 | 36 |
| 1 | C | punkt-import | tds.query | rpc | 6000 | 0 | 0 | 79 |
| 1 | C | punkt-import | backend.execute | rpc | 6000 | 0 | 0 | 59 |
| 8 | B | export | tds.query | sql_batch | 48 | 39610 | 38849 | 43018 |
| 8 | B | export | backend.execute | sql_batch | 48 | 39609 | 38848 | 43007 |
| 8 | B | punkt-export | tds.query | rpc | 6000 | 1 | 0 | 58 |
| 8 | B | punkt-export | tds.query | sql_batch (Last-Sitzungen) | 7 | 16255 | 16249 | 16295 |
| 8 | B | punkt-export | backend.execute | rpc | 6000 | 0 | 0 | 51 |
| 8 | B | punkt-export | backend.execute | sql_batch (Last-Sitzungen) | 7 | 16253 | 16247 | 16284 |
| 8 | C | export | tds.query | sql_batch | 48 | 39733 | 36759 | 41120 |
| 8 | C | export | backend.execute | sql_batch | 48 | 39724 | 36757 | 41119 |
| 8 | C | import | tds.query | sql_batch | 96048 | 0 | 0 | 89 |
| 8 | C | import | backend.execute | sql_batch | 96000 | 0 | 0 | 57 |
| 8 | C | punkt-export | tds.query | rpc | 6000 | 1 | 0 | 57 |
| 8 | C | punkt-export | tds.query | sql_batch (Last-Sitzungen) | 7 | 18130 | 18102 | 18177 |
| 8 | C | punkt-export | backend.execute | rpc | 6000 | 0 | 0 | 50 |
| 8 | C | punkt-export | backend.execute | sql_batch (Last-Sitzungen) | 7 | 18098 | 18091 | 18125 |
| 8 | C | punkt-import | tds.query | rpc | 6000 | 0 | 0 | 106 |
| 8 | C | punkt-import | tds.query | sql_batch (Last-Sitzungen) | 7095 | 1 | 0 | 107 |
| 8 | C | punkt-import | backend.execute | rpc | 6000 | 0 | 0 | 57 |
| 8 | C | punkt-import | backend.execute | sql_batch (Last-Sitzungen) | 7088 | 0 | 0 | 62 |
| 16 | B | export | tds.query | sql_batch | 96 | 7562 | 7490 | 8449 |
| 16 | B | export | backend.execute | sql_batch | 96 | 7559 | 7471 | 8441 |
| 16 | B | punkt-export | tds.query | rpc | 6000 | 2 | 0 | 54 |
| 16 | B | punkt-export | tds.query | sql_batch (Last-Sitzungen) | 60 | 8341 | 2428 | 9615 |
| 16 | B | punkt-export | backend.execute | rpc | 6000 | 0 | 0 | 38 |
| 16 | B | punkt-export | backend.execute | sql_batch (Last-Sitzungen) | 60 | 8338 | 2427 | 9605 |
| 16 | C | export | tds.query | sql_batch | 96 | 8006 | 7568 | 9850 |
| 16 | C | export | backend.execute | sql_batch | 96 | 8004 | 7547 | 9798 |
| 16 | C | import | tds.query | sql_batch | 19296 | 1 | 0 | 157 |
| 16 | C | import | backend.execute | sql_batch | 19200 | 0 | 0 | 65 |
| 16 | C | punkt-export | tds.query | rpc | 6000 | 2 | 0 | 56 |
| 16 | C | punkt-export | tds.query | sql_batch (Last-Sitzungen) | 60 | 8921 | 3012 | 10416 |
| 16 | C | punkt-export | backend.execute | rpc | 6000 | 0 | 0 | 47 |
| 16 | C | punkt-export | backend.execute | sql_batch (Last-Sitzungen) | 60 | 8919 | 3011 | 10383 |
| 16 | C | punkt-import | tds.query | rpc | 6000 | 0 | 0 | 61 |
| 16 | C | punkt-import | tds.query | sql_batch (Last-Sitzungen) | 14699 | 1 | 0 | 131 |
| 16 | C | punkt-import | backend.execute | rpc | 6000 | 0 | 0 | 57 |
| 16 | C | punkt-import | backend.execute | sql_batch (Last-Sitzungen) | 14624 | 0 | 0 | 58 |
| 64 | B | export | tds.query | sql_batch | 384 | 28004 | 27683 | 30356 |
| 64 | B | export | backend.execute | sql_batch | 384 | 27998 | 27667 | 30351 |
| 64 | B | punkt-export | tds.query | rpc | 6000 | 6 | 5 | 74 |
| 64 | B | punkt-export | tds.query | sql_batch (Last-Sitzungen) | 252 | 30080 | 1349 | 34436 |
| 64 | B | punkt-export | backend.execute | rpc | 6000 | 0 | 0 | 15 |
| 64 | B | punkt-export | backend.execute | sql_batch (Last-Sitzungen) | 252 | 30073 | 1347 | 34418 |
| 64 | C | export | tds.query | sql_batch | 384 | 31933 | 30809 | 33186 |
| 64 | C | export | backend.execute | sql_batch | 384 | 31901 | 30795 | 33184 |
| 64 | C | import | tds.query | sql_batch | 77184 | 1 | 0 | 120 |
| 64 | C | import | backend.execute | sql_batch | 76800 | 0 | 0 | 57 |
| 64 | C | punkt-export | tds.query | rpc | 6000 | 7 | 0 | 82 |
| 64 | C | punkt-export | tds.query | sql_batch (Last-Sitzungen) | 252 | 29886 | 1354 | 36584 |
| 64 | C | punkt-export | backend.execute | rpc | 6000 | 0 | 0 | 58 |
| 64 | C | punkt-export | backend.execute | sql_batch (Last-Sitzungen) | 252 | 29879 | 1349 | 36552 |
| 64 | C | punkt-import | tds.query | rpc | 6000 | 0 | 0 | 90 |
| 64 | C | punkt-import | tds.query | sql_batch (Last-Sitzungen) | 185225 | 1 | 0 | 115 |
| 64 | C | punkt-import | backend.execute | rpc | 6000 | 0 | 0 | 10 |
| 64 | C | punkt-import | backend.execute | sql_batch (Last-Sitzungen) | 184272 | 0 | 0 | 54 |

## Lesart (nur so weit die Zeilen tragen)

- **Export:** bei einer Sitzung rund 105 000 Zeilen/s; ab 8 Sitzungen liegen die Gesamtzeilen/s bei 198 000 bis 229 000 (je Sitzung 25 000 bei 8, 13 000 bei 16, 3 000 bis 3 600 bei 64): ein **Plateau der Gesamtzeilen/s ab 8 Sitzungen, dessen Grenze Server oder Client sein kann** (CPU von Server und Client sind ungemessen, beide haben 2 CPUs). Aufbau B und C liegen bei 1, 8 und 16 Sitzungen bis etwa 7 % beieinander; bei 64 Sitzungen nicht (C mit Senke 15,6 % über B, C ohne Senke 5,8 % unter B, mit Fremdlast in C mit Senke). Ein Anteil des Simulations-Backends folgt daraus nicht. Die Zeit der ersten Zeile wächst von 1 ms bei einer Sitzung auf 8 bis 13 ms bei 64.
- **Import (Aufbau C):** 241 000 bis 271 000 Zeilen/s bei einer Sitzung, 436 000 bis 542 000 bei 8 bis 64 Sitzungen gesamt (Höchstwert bei 8). Dieselbe Bedingung wie beim Export: die Grenze kann Server oder Client sein. Aufbau B für den Import fehlt (siehe unten).
- **Punktabfrage:** ohne Last 0,2 bis 0,3 ms (p50; die Zelle Stufe 1 C `punkt-import` ist `zu-gross`). Unter Export-Last bei 8 Sitzungen p50 1,7 bis 2,1 ms und p95 2,8 bis 4,1 ms, bei 16 Sitzungen p95 5 bis 7 ms (davon eine `zu-gross`-Zelle mit 7,39 ms), bei 64 Sitzungen p50 12,6 bis 13 ms und p95 26 bis 29 ms. Unter Import-Last bei 64 Sitzungen p50 10,6 ms und p95 rund 95 ms, bei 8 und 16 Sitzungen p95 2 bis 4 ms. Der p95 stammt aus der Messung am Client, nicht aus `METRICS` (Workload S9).
- **p99:** unter Export-Last liegt der p99 bei 8 bis 64 Sitzungen in einem Band von 23 bis 33 ms (bei 1 Sitzung unter 1 ms); unter Import-Last steigt er mit der Stufe (35 bis 44 ms bei 8, 60 bis 65 ms bei 16, 102 ms bei 64). Die Ursache des Bandes unter Export-Last ist nicht untersucht.
- **Senke:** Nachgerechnet aus den Rohzeilen (Export B, Export C, Import C je Stufe, 12 Paare mit und ohne Senke) liegt bei 7 von 12 Paaren der Median der einen Seite außerhalb von Minimum bis Maximum der anderen; die Unterschiede betragen −7,0 bis +15,2 % und wechseln über die Stufen das Vorzeichen, bei Stufe 1 (Export B, Export C, Import C) ist es gleich (mit Senke langsamer, +0,4 bis +12,6 %). Zwei dieser Paare ohne gestörte Proben in beiden Zellen: Stufe 1 Import C (+12,6 %) und Stufe 64 Export B (−6,1 %, mit Senke schneller). Ein Einfluss der Telemetrie ist damit weder belegt noch ausgeschlossen; die Kosten einer blockierenden Senke sind nicht gemessen.
- **Dauer je Schicht:** (nur mit Senke; ohne Senke entstehen keine Spans) `backend.execute` liegt beim Export um 1 bis 32 ms unter `tds.query`, bei Strömen von 7 bis 40 s; die Zeit außerhalb des Backend-Aufrufs ist gegenüber dem Strom klein. Der Strom wird im Backend-Span gezogen und enthält das Warten auf den Transport (Workload 3.6): der Span sagt nichts über die Eigenzeit des Backends. Punktabfragen (`rpc`): Median 0 bis 2 ms, bei 64 Sitzungen `tds.query` 6 bis 7 ms und `backend.execute` 0 ms; Auflösung 1 ms abgerundet. Die Zahl `n` der Export-Spans ist Sitzungen mal 6 Läufe.
- **Frist:** mit der Vorgabe `QUERY_TIMEOUT` von 30 s ([SPEC-009](../../spec/spezifikation.md#5-metriken-und-tracing-felder)) endete der erste Export der Stufe 8 nach 30 002 bis 30 044 ms (`tds.query` n = 8, Rohzeilen, letzter Abschnitt); der Client endete mit einem Fehler (Exit 1). **Dass der Fehlercode `TDS_TIMEOUT` war, steht in keiner Rohzeile**; es folgt aus der Dauer, die der Frist von 30 s entspricht (Schluss, nicht Beleg). Die Stufen 8, 16 und 64 laufen darum mit 3600 s, der Obergrenze von SPEC-009. Ob 30 s für die Referenzlast passt, ist eine Frage an die Grenzwerte ([slice-qa-performance-grenzwerte](../plan/planning/open/slice-qa-performance-grenzwerte.md)).

## Früherer Lauf von Stufe 1 (Fremdlast)

Der erste Lauf von Stufe 1 (alle Zellen in einem Aufruf, 04:58 bis 05:04 UTC) lief zu 17 von 41 Proben mit mehr als 300 % fremder CPU; seine Zeilen (Export 93 000 bis 100 000 Zeilen/s, Import 168 000 bis 190 000 Zeilen/s) liegen in den Rohzeilen und tragen nicht. Die Wiederholung `stufe1b` oben ist mit weniger Fremdlast gelaufen (Probe höchstens 224 % in der Zelle C, mit Senke, `punkt-import`, Last 1 min vor den Zellen 4,13 bis 10,15).

## Stand der DoD-Zeile Import in Aufbau B (Zustand, keine Entscheidung)

Der Import in ein zählendes **eigenes** Backend (Aufbau B) ist **nicht gemessen und offen**. Der Nutzer hat am 2026-11-17 „Code angleichen“ (Fläche bauen) und danach „DoD teilen, B später“ (der Import in Aufbau B verlässt diesen Slice) entschieden; [ADR-0052](../plan/adr/0052-oeffentliche-flaeche-fuer-massenladen-eigener-backends.md) (Option N1, angenommen) macht die Fähigkeit am Backend öffentlich und nennt einen eigenen Slice für den Bau der Fläche; dieser Slice baut sie nicht. Bis dahin steht der Import nur in Aufbau C. Der Import am Stapel-Callback ohne Draht (Aufbau A) ist in diesem Protokoll nicht gemessen (nur die Probe vom 2026-11-16, eine Sitzung, [Messprotokoll](lasttest-probe-2026-11-15.md)) und bleibt offen; die Nutzerentscheidung „DoD teilen, B später“ betrifft nur Aufbau B. Der Befund des Probelaufs, dass ein eigenes Backend `SqlBulkCopy` abweist, gilt bis zum Bau fort.

## Zellen nur in den Rohzeilen

Die Rohzeilen enthalten außerdem zwei Zellen aus dem zweiten Aufruf von Stufe 1 (`stufe1-B-ohne-punkt-export`, `stufe1-C-mit-import`, Fremdlast je Zelle in den Rohzeilen) und den Abbruch von Stufe 8 an der Frist; sie stehen in keiner Tabelle, die Wiederholung `stufe1b` ersetzt sie.

## Nicht belegt

- **Zwei Zellen mit `Streuung zu-gross`** (nach S11 nicht belegt): Stufe 16, C, mit Senke, `punkt-export` (p95 7,39 ms, Minimum bis Maximum 5,81 bis 16,53) und Stufe 1, C, mit Senke, `punkt-import`; **Versuch 1 der Zelle Stufe 8, B, mit Senke, `punkt-export`** (siehe oben) ist ein Lauf mit anderem Ergebnis derselben Zelle.
- Die C#-Seite des Werkzeugs (`referenztest-dotnet/Lasttest/`) läuft in keinem Gate (nach [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md)): die Prüfung, dass jede Punktabfrage genau die Zeile der Id liefert, hängt nur an `make lasttest` und an Stufe 9 von `make e2e`; der Selbsttest belegt die Auswertung der Skriptzeilen, nicht den C#-Teil. Dasselbe gilt für die neuen Pfade dieses Werkzeugs (Gruppe mit N Sitzungen, die Phasen `punkt-*`): Stufe 9 von `make e2e` fährt nur die Phasen `export` und `import` mit einer Sitzung; die Pfade sind allein durch die Messläufe vom 2026-11-17 belegt (156 Zeilen `lasttest: punkt lauf` in den Rohzeilen), ein Wächter gegen künftige Änderungen am Client fehlt.
- Ein Grenzwert; die Streuung über mehrere Tage und Hosts; ein ruhiger Host (die gestörten Zellen sind als solche gekennzeichnet; jede Zelle ist ein Lauf, Wiederholungen: Abbruch durch den Speicherwächter vor dem Start, und die Zelle Stufe 8, B, mit Senke, `punkt-export` mit zwei vollständigen Läufen, siehe oben).
- Import in Aufbau B (siehe oben); Rückstau, Heap als Live-Größe, Latenz des zurückgehaltenen Abschlusses (Folge-Slices); TLS, ein anderer Client, d-migrate als Gegenprobe.
- Die Trennung von Client-Aufwand und Protokollschicht: der Referenzclient liest und schreibt in der gemessenen Zeit mit, und sein CPU-Anteil (2 CPUs für bis zu 64 Sitzungen) ist ungemessen; bei 64 Sitzungen kann der Client, nicht der Server, die Grenze sein.
- Die Ursache des engen p99-Bandes.
