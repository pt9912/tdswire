#!/usr/bin/env bash
# tools/rueckstau-tabellen-selbsttest.sh - belegt tools/rueckstau-tabellen.sh an einer kleinen Rohzeilen-Datei (TDS-QA-04): je Tabelle eine
# Zeile (Leser mit Mittel bei mehreren langsamen Lesern, Blockierend, Abbruch, Import mit und ohne Probe, Frist), Tausendertrenner, Komma,
# Speicher in GiB (MiB durch 1024), cgroup-Spitze in MiB, Last vor und nach, Fremdlast, die Bedingungen; Fehler bei fehlender Datei und ohne
# Zelle; sieben Mutationen des Werkzeugs muessen je rot werden. Aufgerufen von der Dockerfile-Stage `skript-selbsttest`; braucht bash, awk,
# sed, grep, cmp, mktemp. Grenze: belegt die Tabellen am Muster, nicht die Messung.
set -u
tool=$(cd "$(dirname "$0")" && pwd)/rueckstau-tabellen.sh
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
fehler=0

cat >"$tmp/roh.txt" <<'ROH'
# Kopf
rueckstau: Kopf 2026-11-17T09:08:11Z host kerne 20
rueckstau: server aufbau=eigen zeilen=100000 seed=42 pool=64 callback=suspendierend/0ms heap_max=268435456
rueckstau: leser n 4 langsam 2 zeilen 100000 basis_ms 4000 schnell_ms 3000 basis_zeilen_je_s 12345 schnell_zeilen_je_s 23456 vorlauf_summe 3000 vorlauf_je_langsam 1500 plateau_zuwachs 0 heap_benutzt_mb 20 heap_nach_gc_mb 3
rueckstau: server peak 104857600 Bytes (cgroup), laeuft true
zelle: leser-eigen-4 2026-11-17T09:08:25Z vorher [last 1.50/2.00/3.00 swap_mb 12288 frei_mb 15360 container_fremd 2] nachher [last 2.50/2.00/3.00 swap_mb 12288 frei_mb 16384 container_fremd 2] fremdlast proben 5 ueber300 2 max 400%
rueckstau: server aufbau=eigen zeilen=100000 seed=42 pool=64 callback=suspendierend/0ms heap_max=268435456
rueckstau: blockierend modus nicht-unterbrechbar ohne_last nachbar n 20 fehler 0 p50_us 1800 p95_us 2200 max_us 64000
rueckstau: blockierend modus nicht-unterbrechbar mit_last nachbar n 21 fehler 0 p50_us 1300 p95_us 1700 max_us 1800
rueckstau: blockierend modus nicht-unterbrechbar n 64 zeilen 100 block_ms 10 jede 1 gesamt_ms 12000 sitzung_median_ms 1040 sitzung_max_ms 1100 start_max_ms 9 erwartet_je_sitzung_ms 1000 spitze_threads 77
zelle: blk-nicht-unterbrechbar-64 2026-11-17T09:14:53Z vorher [last 1.00/2.00/3.00 swap_mb 12000 frei_mb 15000 container_fremd 2] nachher [last 1.20/2.00/3.00 swap_mb 12000 frei_mb 15000 container_fremd 2] fremdlast proben 0 ueber300 0 max 0%
rueckstau: server aufbau=eigen zeilen=100000 seed=42 pool=64 callback=suspendierend/0ms heap_max=268435456
rueckstau: abbruch gelesen_vor_cancel 0
rueckstau: abbruch modus unterbrechbar mit_last nachbar n 101 fehler 0 p50_us 1750 p95_us 16987 max_us 43521
rueckstau: abbruch modus unterbrechbar block_ms 10000 jede 3 cancel_nach_ms 1500 cancel_bis_ende_ms 9 gelesen 2 ende 'SqlException: Operation cancelled by user.' last_sitzungen 8 direkt_danach: threads 14 offen 1; nach_block: threads 13 offen 0 abgebrochen_delta 1
zelle: abbruch-unterbrechbar 2026-11-17T09:16:32Z vorher [last 3.00/3.00/3.00 swap_mb 12000 frei_mb 15000 container_fremd 2] nachher [last 3.10/3.00/3.00 swap_mb 12000 frei_mb 15000 container_fremd 2] fremdlast proben 1 ueber300 0 max 249%
rueckstau: server aufbau=sim zeilen=100000 seed=42 pool=64 callback=suspendierend/50ms heap_max=268435456
rueckstau: import mit_last nachbar n 105 fehler 0 p50_us 1446 p95_us 2152 max_us 5682
rueckstau: import n 8 zeilen 100000 sender_ms_je_zeile 1 gesamt_ms 5909 importiert_delta 800000 max_vorlauf_zeilen 8008 proben 43 spitze_threads 14 heap_nach_gc_mb 11
zelle: imp-8-callback 2026-11-17T09:17:21Z vorher [last 2.00/3.00/3.00 swap_mb 12000 frei_mb 15000 container_fremd 2] nachher [last 2.10/3.00/3.00 swap_mb 12000 frei_mb 15000 container_fremd 2] fremdlast proben 0 ueber300 0 max 0%
rueckstau: server aufbau=sim zeilen=100000 seed=42 pool=64 callback=suspendierend/0ms heap_max=268435456
rueckstau: import mit_last nachbar n 6 fehler 0 p50_us 1242 p95_us 2612 max_us 2612
rueckstau: import n 1 zeilen 100000 sender_ms_je_zeile 0 gesamt_ms 817 importiert_delta 100000 max_vorlauf_zeilen 0 proben 0 spitze_threads 13 heap_nach_gc_mb 4
zelle: imp-1-schnell 2026-11-17T09:17:09Z vorher [last 3.02/2.00/3.00 swap_mb 12000 frei_mb 15000 container_fremd 2] nachher [last 2.94/3.00/3.00 swap_mb 12000 frei_mb 15000 container_fremd 2] fremdlast proben 0 ueber300 0 max 0%
rueckstau: server aufbau=eigen zeilen=100000 seed=42 pool=64 callback=suspendierend/0ms heap_max=268435456
rueckstau: leser n 1 langsam 1 zeilen 100000 basis_ms -1 basis_zeilen_je_s -1 schnell_zeilen_je_s -1 vorlauf_je_langsam 1499 plateau_zuwachs 0 offen_ende 0 abgebrochen_delta 1 weiterlesen_ms 9 frist_ende 'Timeout. nach 1500 Zeilen' gesamt_ms 6030
zelle: leser-frist 2026-11-17T09:23:21Z vorher [last 6.57/5.09/4.04 swap_mb 12288 frei_mb 15867 container_fremd 2] nachher [last 5.94/5.01/4.02 swap_mb 12288 frei_mb 15871 container_fremd 2] fremdlast proben 0 ueber300 0 max 0%
ROH
cat >"$tmp/erwartet.txt" <<'SOLL'
### Bedingungen aus den Zeilen zelle:
| Zellen | 6 |
| Last 1 min vor und nach den Zellen | 1,00 bis 6,57 |
| Verfügbarer Speicher vor und nach den Zellen (GiB) | 14,6 bis 16,0 |
| Swap des Hosts belegt (GiB) | 11,7 bis 12,0 |
| Zellen mit Proben über 300 % fremder CPU | leser-eigen-4 (2 von 5, 400 %) |
| leser-eigen-4 | eigen | 4 | 2 | 1500 (Mittel) | 0 | 12 345 | 23 456 | 20 / 3 | 100,0 | 1,50 → 2,50 | 5, 2, 400 % |
| blk-nicht-unterbrechbar-64 | nicht-unterbrechbar | 64 | 12 000 | 1040 | 9 | 77 | 20; 1800 / 2200 / 64 000 | 21; 1300 / 1700 / 1800 | 1,00 → 1,20 | 0, 0, 0 % |
| abbruch-unterbrechbar | unterbrechbar | 9 | `SqlException: Operation cancelled by user.` | 2 | 1 / 0 | 14 | 101; 1750 / 16 987 / 43 521 | 3,00 → 3,10 | 1, 0, 249 % |
| imp-8-callback | 8 | 100 000 | suspendierend 50ms | 1 | 5909 | 8008 (43) | 105; 1446 / 2152 / 5682 | 14 | 11 | 2,00 → 2,10 | 0, 0, 0 % |
| imp-1-schnell | 1 | 100 000 | ohne Warten | 0 | 817 | keine Probe (0) | 6; 1242 / 2612 / 2612 | 13 | 4 | 3,02 → 2,94 | 0, 0, 0 % |
| leser-frist | 9 | `Timeout. nach 1500 Zeilen` | 1 | 0 | 6,57 → 5,94 | 0, 0, 0 % |
SOLL

pruefe() { # pruefe <Skript>: Zahl der erwarteten Zeilen, die in der Ausgabe fehlen, in $abweichungen
	local skript="$1" zeile
	abweichungen=0
	bash "$skript" "$tmp/roh.txt" >"$tmp/aus" 2>/dev/null || abweichungen=$((abweichungen + 1))
	while IFS= read -r zeile; do
		grep -qxF -- "$zeile" "$tmp/aus" || abweichungen=$((abweichungen + 1))
	done <"$tmp/erwartet.txt"
}

pruefe "$tool"
[ "$abweichungen" -eq 0 ] || { echo "rueckstau-tabellen-selbsttest: FEHLER - $abweichungen Abweichungen vom Erwarteten" >&2; fehler=1; }
bash "$tool" "$tmp/fehlt" >/dev/null 2>&1; [ $? -eq 1 ] || { echo "rueckstau-tabellen-selbsttest: FEHLER - Datei fehlt: kein Exit 1" >&2; fehler=1; }
echo "# nur Kommentar" >"$tmp/leer.txt"
bash "$tool" "$tmp/leer.txt" >/dev/null 2>&1; [ $? -eq 1 ] || { echo "rueckstau-tabellen-selbsttest: FEHLER - keine Zelle: kein Exit 1" >&2; fehler=1; }

mutiere() { # mutiere <Name> <sed-Ausdruck>: das Werkzeug muss rot werden
	sed "$2" "$tool" >"$tmp/mutant.sh"
	if cmp -s "$tool" "$tmp/mutant.sh"; then
		echo "rueckstau-tabellen-selbsttest: FEHLER - Mutation $1 aendert nichts" >&2
		fehler=1
		return
	fi
	pruefe "$tmp/mutant.sh"
	if [ "$abweichungen" -eq 0 ]; then
		echo "rueckstau-tabellen-selbsttest: FEHLER - Mutation $1 bleibt gruen" >&2
		fehler=1
	else
		echo "rueckstau-tabellen-selbsttest: ok   Mutation $1 faerbt $abweichungen Zeilen rot"
	fi
}
mutiere spitze-in-mb 's/zp\[i\] \/ 1048576/zp[i] \/ 1000000/'
mutiere speicher-durch-1000 's/fmin \/ 1024/fmin \/ 1000/'
mutiere vorlauf-summe 's/feld(l, "vorlauf_je_langsam")/feld(l, "vorlauf_summe")/'
mutiere tausender-ab-1000 's/if (zahl < 10000)/if (zahl < 1000)/'
mutiere last-nachher 's/return t\[1\] }/return t[2] }/;s/split(inh, t, "\/"); return t\[1\]/split(inh, t, "\/"); return t[2]/'
mutiere mittel-ab-2 's/feld(l, "langsam") + 0 > 1/feld(l, "langsam") + 0 > 2/'
mutiere callback-null 's/cc\[2\] == "0ms"/cc[2] == "1ms"/'

[ "$fehler" -eq 0 ] || exit 1
echo "rueckstau-tabellen-selbsttest: gruen"
