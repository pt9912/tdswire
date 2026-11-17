#!/usr/bin/env bash
# tools/heap-abschluss-tabellen-selbsttest.sh - belegt tools/heap-abschluss-tabellen.sh an einer kleinen Rohzeilen-Datei (TDS-QA-04): je Lauf eine
# Zeile der Heap-Tabelle (KiB in MiB, Sekunden, Tausendertrenner, Stichproben, cgroup-Spitze in MiB, Last vor und nach, Fremdlast), eine Zeile je
# Form und Verzoegerung der Abschluss-Tabelle (Median, Minimum, Maximum ueber die Wiederholungen ohne den Warmlauf `wdh 0`, gleiches Paket von
# letzter Zeile und DONE gezaehlt), die Bedingungen; Fehler bei fehlender Datei und ohne Zelle; dreizehn Mutationen des Werkzeugs muessen je rot werden.
# Aufgerufen von der Dockerfile-Stage `skript-selbsttest`; braucht bash, awk, sed, grep, cmp, mktemp. Grenze: belegt die Tabellen am Muster, nicht die Messung.
set -u
tool=$(cd "$(dirname "$0")" && pwd)/heap-abschluss-tabellen.sh
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
fehler=0

cat >"$tmp/roh.txt" <<'ROH'
heapabschluss: server aufbau=eigen zeilen=100 seed=42 intervall_ms=500 heap_max=268435456 prozessoren=2 gc=G1 swap_max 0
heap: lauf n 1 zeilen 10000000 wdh 1 von 2 dauer_ms 95000 zeilen_je_s_gesamt 105263 proben_offen 190 proben_leer 3 min_kb 4096 median_kb 6144 p95_kb 9216 max_kb 12288 leerlauf_max_kb 3072 gc_ms 2500
heap: lauf n 1 zeilen 10000000 wdh 2 von 2 dauer_ms 96000 zeilen_je_s_gesamt 104166 proben_offen 191 proben_leer 1 min_kb 4000 median_kb 6000 p95_kb 9000 max_kb 12000 leerlauf_max_kb 3000 gc_ms 2400
heapabschluss: server peak 157286400 Bytes (cgroup), laeuft true
zelle: heap-eigen-1 2026-11-17T14:05:09Z vorher [last 3.00/3.00/3.00 swap_mb 12288 frei_mb 15360 container_fremd 2] nachher [last 4.00/3.00/3.00 swap_mb 12288 frei_mb 15000 container_fremd 2] fremdlast proben 19 ueber300 0 max 200%
abschluss: kopf ziel=x:1433 zeilen=1000 wdh=3 packet_size=4096
abschluss: lauf form ende verzoegerung_ms 100 wdh 0 zeilen 1000 ergebnisse 1 more_bit false pakete 5 paket_nutzbytes 4088 zeile_und_done_im_selben_paket true zeilen_vor_dem_letzten_paket 860 zeilen_im_letzten_paket 140 vorletztes_paket_bis_done_us 30000 erste_zeile_us 900 zeile_bis_done_client_us 7 zeile_emit_bis_zeile_gesehen_us 100500 zeile_emit_bis_done_gesehen_us 100600 naechstes_bis_done_gesehen_us 400 anfrage_bis_done_us 101000
abschluss: lauf form ende verzoegerung_ms 100 wdh 1 zeilen 1000 ergebnisse 1 more_bit false pakete 5 paket_nutzbytes 4088 zeile_und_done_im_selben_paket true zeilen_vor_dem_letzten_paket 860 zeilen_im_letzten_paket 140 vorletztes_paket_bis_done_us 10000 erste_zeile_us 1000 zeile_bis_done_client_us 10 zeile_emit_bis_zeile_gesehen_us 100400 zeile_emit_bis_done_gesehen_us 100500 naechstes_bis_done_gesehen_us 300 anfrage_bis_done_us 100900
abschluss: lauf form ende verzoegerung_ms 100 wdh 2 zeilen 1000 ergebnisse 1 more_bit false pakete 5 paket_nutzbytes 4088 zeile_und_done_im_selben_paket true zeilen_vor_dem_letzten_paket 860 zeilen_im_letzten_paket 140 vorletztes_paket_bis_done_us 20000 erste_zeile_us 1100 zeile_bis_done_client_us 30 zeile_emit_bis_zeile_gesehen_us 100200 zeile_emit_bis_done_gesehen_us 100300 naechstes_bis_done_gesehen_us 200 anfrage_bis_done_us 100800
abschluss: lauf form ende verzoegerung_ms 100 wdh 3 zeilen 1000 ergebnisse 1 more_bit false pakete 5 paket_nutzbytes 4088 zeile_und_done_im_selben_paket false zeilen_vor_dem_letzten_paket 860 zeilen_im_letzten_paket 140 vorletztes_paket_bis_done_us 15000 erste_zeile_us 1200 zeile_bis_done_client_us 20 zeile_emit_bis_zeile_gesehen_us 100300 zeile_emit_bis_done_gesehen_us 100400 naechstes_bis_done_gesehen_us 250 anfrage_bis_done_us 100950
abschluss: lauf form folge verzoegerung_ms 100 wdh 1 zeilen 1000 ergebnisse 2 more_bit true pakete 5 paket_nutzbytes 4088 zeile_und_done_im_selben_paket true zeilen_vor_dem_letzten_paket 960 zeilen_im_letzten_paket 40 vorletztes_paket_bis_done_us 100500 erste_zeile_us 900 zeile_bis_done_client_us 0 zeile_emit_bis_zeile_gesehen_us 101000 zeile_emit_bis_done_gesehen_us 101000 naechstes_bis_done_gesehen_us 600 anfrage_bis_done_us 102000
zelle: abschluss 2026-11-17T14:20:00Z vorher [last 2.00/3.00/3.00 swap_mb 12000 frei_mb 15000 container_fremd 2] nachher [last 2.50/3.00/3.00 swap_mb 12000 frei_mb 15000 container_fremd 2] fremdlast proben 1 ueber300 1 max 350%
ROH
cat >"$tmp/erwartet.txt" <<'SOLL'
### Bedingungen aus den Zeilen zelle:
| Zellen | 2 |
| Zellen mit Proben über 300 % fremder CPU | abschluss (1 von 1, 350 %) |
| Last 1 min vor und nach den Zellen | 2,00 bis 4,00 |
| Verfügbarer Speicher vor und nach den Zellen (GiB) | 14,6 bis 15,0 |
| Swap des Hosts belegt (GiB) | 11,7 bis 12,0 |
| heap-eigen-1 (14:05) | eigen | 1 | 10 000 000 | 1 von 2 | 95,0 | 105 263 | 190 | 4,0 | 6,0 | 9,0 | 12,0 | 3,0 | 2,5 | 150,0 | 3,00 → 4,00 | 19, 0, 200 % |
| heap-eigen-1 (14:05) | eigen | 1 | 10 000 000 | 2 von 2 | 96,0 | 104 166 | 191 | 3,9 | 5,9 | 8,8 | 11,7 | 2,9 | 2,4 | 150,0 | 3,00 → 4,00 | 19, 0, 200 % |
| ende | 100 | 3 | 1000 | 5 | 4088 | 2 von 3 | 140 (140 bis 140) | false | 20 (10 bis 30) | 100 300 (100 200 bis 100 400) | 100 400 (100 300 bis 100 500) | 250 (200 bis 300) | 15 000 (10 000 bis 20 000) | 100 900 (100 800 bis 100 950) | 2,00 → 2,50 | 1, 1, 350 % |
| folge | 100 | 1 | 1000 | 5 | 4088 | 1 von 1 | 40 (40 bis 40) | true | 0 (0 bis 0) | 101 000 (101 000 bis 101 000) | 101 000 (101 000 bis 101 000) | 600 (600 bis 600) | 100 500 (100 500 bis 100 500) | 102 000 (102 000 bis 102 000) | 2,00 → 2,50 | 1, 1, 350 % |
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
[ "$abweichungen" -eq 0 ] || { echo "heap-abschluss-tabellen-selbsttest: FEHLER - $abweichungen Abweichungen vom Erwarteten" >&2; fehler=1; }
bash "$tool" "$tmp/fehlt" >/dev/null 2>&1; [ $? -eq 1 ] || { echo "heap-abschluss-tabellen-selbsttest: FEHLER - Datei fehlt: kein Exit 1" >&2; fehler=1; }
echo "# nur Kommentar" >"$tmp/leer.txt"
bash "$tool" "$tmp/leer.txt" >/dev/null 2>&1; [ $? -eq 1 ] || { echo "heap-abschluss-tabellen-selbsttest: FEHLER - keine Zelle: kein Exit 1" >&2; fehler=1; }

mutiere() { # mutiere <Name> <sed-Ausdruck>: das Werkzeug muss rot werden
	sed "$2" "$tool" >"$tmp/mutant.sh"
	if cmp -s "$tool" "$tmp/mutant.sh"; then
		echo "heap-abschluss-tabellen-selbsttest: FEHLER - Mutation $1 aendert nichts" >&2
		fehler=1
		return
	fi
	pruefe "$tmp/mutant.sh"
	if [ "$abweichungen" -eq 0 ]; then
		echo "heap-abschluss-tabellen-selbsttest: FEHLER - Mutation $1 bleibt gruen" >&2
		fehler=1
	else
		echo "heap-abschluss-tabellen-selbsttest: ok   Mutation $1 faerbt $abweichungen Zeilen rot"
	fi
}
mutiere kib-in-mb 's/feld(l, "median_kb") \/ 1024/feld(l, "median_kb") \/ 1000/'
mutiere median-statt-maximum 's/eins(feld(l, "median_kb") \/ 1024)/eins(feld(l, "max_kb") \/ 1024)/'
mutiere spitze-in-mb 's/zp\[i\] \/ 1048576/zp[i] \/ 1000000/'
mutiere median-untere-haelfte 's/tmp\[int(n \/ 2) + 1\]/tmp[int(n \/ 2)]/'
mutiere warmlauf-zaehlt 's/feld(l, "wdh") + 0 == 0) continue/feld(l, "wdh") + 0 == -1) continue/'
mutiere selbes-paket 's/feld(l, "zeile_und_done_im_selben_paket") == "true"/feld(l, "zeile_und_done_im_selben_paket") == "false"/'
mutiere vorletztes-paket-spalte 's/dreier(key "v", m, 1, 1)/dreier(key "a", m, 1, 1)/'
mutiere zeilen-im-letzten-paket-spalte 's/dreier(key "p", m, 1, 1)/dreier(key "n", m, 1, 1)/'
mutiere zellenzahl 's/"| Zellen | " n " |"/"| Zellen | " (n + 1) " |"/'
mutiere schwelle-ueber300 's/feld($0, "ueber300") + 0 > 0/feld($0, "ueber300") + 0 > 1/'
mutiere more-bit-fest 's/kmore\[key\] = feld(l, "more_bit")/kmore[key] = "false"/'
mutiere tausender-ab-1000 's/if (zahl < 10000)/if (zahl < 1000)/'
mutiere speicher-durch-1000 's/fmin \/ 1024/fmin \/ 1000/'

[ "$fehler" -eq 0 ] || exit 1
echo "heap-abschluss-tabellen-selbsttest: gruen"
