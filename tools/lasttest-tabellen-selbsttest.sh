#!/usr/bin/env bash
# tools/lasttest-tabellen-selbsttest.sh - belegt tools/lasttest-tabellen.sh an einer kleinen Rohzeilen-Datei (TDS-QA-04): Median, Minimum und
# Maximum wie in der Zeile `messung:`, Zeilen/s je Sitzung, Mikrosekunden als Millisekunden, die Wahl des Versuchs (erster mit hoechstens
# einer Probe ueber 300 %, sonst der letzte, "gestoert" und "k. A."), Sortierung nach Sitzungen, Filter nach Stufenname, Spans mit
# "(Last-Sitzungen)" in den Punktphasen, Fehler bei fehlender Datei und ohne Zelle; sechs Mutationen des Werkzeugs muessen je rot werden.
# Aufgerufen von der Dockerfile-Stage `skript-selbsttest`; braucht bash, awk, sed, grep, cmp, mktemp. Grenze: belegt die Tabellen am Muster,
# nicht die Messung.
set -u
tool=$(dirname "$0")/lasttest-tabellen.sh
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
fehler=0
pruefe() { echo "lasttest-tabellen-selbsttest: FEHLER - $1" >&2; fehler=1; }

roh="$tmp/roh.txt"
{
	echo "# Kopf"
	echo "## stufe10-B-mit-export-v1"
	echo "messung: 2026-11-17T00:00:00Z aufbau B senke mit richtung export zeilen 100 sitzungen 10 wdh 5 median_ms 2000 min_ms 1900 max_ms 2100 zeilen_je_s 5000 bytes_je_zeile 330 streuung ok erste_zeile_ms_median 9 erste_zeile_ms_min 8 erste_zeile_ms_max 12 vorher [last 1.00/1.00/1.00 swap_mb 1 frei_mb 9] nachher [last 2.50/1.00/1.00 swap_mb 1 frei_mb 9]"
	echo "Probe: 4 Proben, 0 über 300 %, Höchstwert 120 %"
	echo "## stufe2-B-mit-export-v1"
	echo "messung: 2026-11-17T00:00:00Z aufbau B senke mit richtung export zeilen 100 sitzungen 2 wdh 5 median_ms 1000 min_ms 900 max_ms 1100 zeilen_je_s 4000 bytes_je_zeile 329 streuung ok erste_zeile_ms_median 5 erste_zeile_ms_min 4 erste_zeile_ms_max 6 vorher [last 5.00/1.00/1.00 swap_mb 1 frei_mb 9] nachher [last 6.00/1.00/1.00 swap_mb 1 frei_mb 9] last-hoch"
	echo "Probe: 4 Proben, 3 über 300 %, Höchstwert 800 %"
	echo "## stufe2-B-mit-export-v2"
	echo "messung: 2026-11-17T00:00:00Z aufbau B senke mit richtung export zeilen 100 sitzungen 2 wdh 5 median_ms 1200 min_ms 1100 max_ms 1300 zeilen_je_s 3334 bytes_je_zeile 329 streuung ok erste_zeile_ms_median 5 erste_zeile_ms_min 4 erste_zeile_ms_max 6 vorher [last 1.50/1.00/1.00 swap_mb 1 frei_mb 9] nachher [last 1.60/1.00/1.00 swap_mb 1 frei_mb 9]"
	echo "Probe: 4 Proben, 1 über 300 %, Höchstwert 350 %"
	echo "## stufe2-C-mit-punkt-export-v1"
	echo "messung: 2026-11-17T00:00:00Z aufbau C senke mit richtung punkt-export zeilen 100 sitzungen 2 wdh 5 anfragen 1000 p50_us_median 1500 p50_us_min 1400 p50_us_max 1600 p95_us_median 2500 p95_us_min 2000 p95_us_max 3000 p99_us_median 9000 p99_us_min 8000 p99_us_max 9900 streuung_p95 ok vorher [last 1.00/1.00/1.00 swap_mb 1 frei_mb 9] nachher [last 1.10/1.00/1.00 swap_mb 1 frei_mb 9]"
	echo "messung-span: aufbau C senke mit phase punkt-export span tds.query art rpc n=6000 median=1 min=0 max=20"
	echo "messung-span: aufbau C senke mit phase punkt-export span tds.query art sql_batch n=1 median=900 min=900 max=900"
	echo "Probe: nicht zugehörig, nicht gewertet"
	echo "## stufe3-C-ohne-import-v1"
	echo "messung: 2026-11-17T00:00:00Z aufbau C senke ohne richtung import zeilen 100 sitzungen 3 wdh 5 median_ms 500 min_ms 400 max_ms 600 zeilen_je_s 600 bytes_je_zeile 336 streuung ok vorher [last 1.00/1.00/1.00 swap_mb 1 frei_mb 9] nachher [last 1.00/1.00/1.00 swap_mb 1 frei_mb 9]"
	echo "Probe: 4 Proben, 0 über 300 %, Höchstwert 100 %"
	echo "## Lauf 1, anderer Abschnitt"
	echo "messung: 2026-11-17T00:00:00Z aufbau B senke mit richtung export zeilen 100 sitzungen 99 wdh 5 median_ms 1"
} > "$roh"

zeilenpruefung() { # zeilenpruefung <Skript> <Stufenname...>: Ausgabe nach $tmp/aus, Exit 0 nur, wenn alle erwarteten Zeilen da sind
	local skript="$1" aus="$tmp/aus"
	shift
	bash "$skript" "$roh" "$@" > "$aus" 2> /dev/null || return 1
	grep -qF '| 2 | B | mit | 1200 | 1100 | 1300 | 3334 | 1667 | 5 (4–6) | 329 | ok | 1.50 / 1.60 | - | 1 / 350 % | 2 von 2 |' "$aus" \
		&& grep -qF '| 10 | B | mit | 2000 | 1900 | 2100 | 5000 | 500 | 9 (8–12) | 330 | ok | 1.00 / 2.50 | - | 0 / 120 % | 1 von 1 |' "$aus" \
		&& grep -qF '| 2 | C | mit | 1.50 (1.40–1.60) | 2.50 (2.00–3.00) | 9.00 (8.00–9.90) | ok | 1.00 / 1.10 | - | k. A. | 1 von 1, gestört |' "$aus" \
		&& grep -qF '| 3 | C | ohne | 500 | 400 | 600 | 600 | 200 | 336 | ok | 1.00 / 1.00 | - | 0 / 100 % | 1 von 1 |' "$aus" \
		&& grep -qF '| 2 | C | punkt-export | tds.query | sql_batch (Last-Sitzungen) | 1 | 900 | 900 | 900 |' "$aus" \
		&& grep -qF '| 2 | C | punkt-export | tds.query | rpc | 6000 | 1 | 0 | 20 |' "$aus" \
		&& ! grep -q '^| 99 ' "$aus" \
		&& [ "$(grep -n '^| 2 | B | mit | 1200' "$aus" | cut -d: -f1)" -lt "$(grep -n '^| 10 | B | mit | 2000' "$aus" | cut -d: -f1)" ]
}
zeilenpruefung "$tool" stufe10 stufe2 stufe3 || pruefe "Tabellen am Muster"
# Filter: ohne den Namen stufe10 fehlt dessen Zeile
bash "$tool" "$roh" stufe2 stufe3 > "$tmp/aus" 2> /dev/null && ! grep -q '^| 10 ' "$tmp/aus" || pruefe "Filter nach Stufenname"
# Fehler
bash "$tool" "$tmp/gibt-es-nicht" > /dev/null 2>&1 && pruefe "fehlende Datei wurde angenommen"
echo "# nichts" > "$tmp/leer.txt"
bash "$tool" "$tmp/leer.txt" > /dev/null 2>&1 && pruefe "Datei ohne Zelle wurde angenommen"

mutiere() { # mutiere <Name> <sed-Ausdruck>: das veraenderte Werkzeug muss die Pruefung am Muster verfehlen
	sed "$2" "$tool" > "$tmp/$1.sh"
	if cmp -s "$tool" "$tmp/$1.sh"; then pruefe "Mutation $1 aenderte nichts"; return; fi
	if zeilenpruefung "$tmp/$1.sh" stufe10 stufe2 stufe3; then pruefe "Mutation $1 blieb gruen"; fi
}
mutiere ms-ohne-1000 's|sprintf("%.2f", us / 1000)|sprintf("%.2f", us / 100)|'
mutiere je-sitzung-ohne-teilung 's|int(feld(l, "zeilen_je_s") / st)|int(feld(l, "zeilen_je_s"))|'
mutiere versuch-erster 's|probe_m\[idx\] >= 0 \&\& probe_m\[idx\] < 2 \&\& (idx in zeile)|(idx in zeile)|'
mutiere gestoert-schwelle 's|probe_m\[idx\] < 2|probe_m[idx] < 4|g'
mutiere ohne-last-sitzungen 's| (Last-Sitzungen)||'
mutiere ohne-sortierung 's|^	sortiere_sitz()$|	|'

[ "$fehler" -eq 0 ] || exit 1
echo "lasttest-tabellen-selbsttest: ok (Tabellen am Muster, Filter, zwei Eingabefehler, 6 Mutationen rot)"
