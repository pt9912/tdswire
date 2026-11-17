#!/usr/bin/env bash
# tools/lasttest-selbsttest.sh - belegt, dass tools/lasttest-statistik.sh Median, Minimum, Maximum und die Streuungsregel
# (Maximum mehr als das Doppelte des Minimums) richtig rechnet, Eingabefehler abweist, und dass drei Mutationen des Skripts
# je rot werden (Median ohne Sortierung, Streuung ohne Faktor 2, Maximum als erster Wert) und dass tools/lasttest.sh falsche Zahlenwerte der Umgebung vor
# jedem Docker-Aufruf mit Exit 2 abweist (docker-Stub; eine Mutation ohne die Pruefung wird rot). Ein Handlauf von tools/lasttest.sh
# gegen einen docker-Stub und eine Nachbildung von meminfo und loadavg (LASTTEST_PROC) haelt (a) die Auswertung der Zeilen von
# Aufbau A (Spalte der Millisekunden), (b) den Vermerk last-hoch und swap-gewachsen in allen drei Zeilen von Aufbau A (und sein
# Fehlen bei ruhigem Host), (c) den Fehler, wenn der Import in Aufbau B nicht abgewiesen wird und (d) vier Sitzungen in Aufbau C
# (Zeilen/s aller Sitzungen, Zeit der ersten Zeile, Perzentile der Punktabfrage ohne den Warmlauf, Dauer je Schicht nach Anfrageart aus dem
# Log, Zaehlung der Importe je Sitzung, Vermerke an den Zeilen der Punktabfrage), (e) die Streuung des p95, (f) die Zaehlung der Laeufe der
# Punktabfrage und (g) das Fehlen von punkt-import in Aufbau B; je Fall mindestens eine Mutation, die rot wird.
# TDS-QA-04, TDS-QA-06. Grenze: der Stub belegt die Auswertung der Skriptzeilen, nicht den Wortlaut der echten Ausgabe des Containers.
# Aufgerufen von der Dockerfile-Stage `skript-selbsttest`; braucht bash, sh, sort, sed, cmp, mktemp, grep, awk (die Auswertung der
# Zeilen von Aufbau A uebt awk; in der Stage liegt mawk, gemessen im Verifikationsbericht vom 2026-11-16).
set -u
statistik=$(dirname "$0")/lasttest-statistik.sh
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
fehler=0
pruefe() { echo "lasttest-selbsttest: FEHLER - $1" >&2; fehler=1; }

erwarte() { # erwarte <Skript> <Beschreibung> <erwartete Ausgabe> <Eingabe...>
	local skript="$1" beschr="$2" soll="$3" ist
	shift 3
	ist=$(printf '%s\n' "$@" | bash "$skript" 2>/dev/null) || ist="EXIT"
	[ "$ist" = "$soll" ] || pruefe "$beschr: '$ist' statt '$soll'"
}

faelle() { # faelle <Skript> <Praefix>
	erwarte "$1" "$2 ungerade, unsortiert" "n=5 median=30 min=10 max=50 streuung=zu-gross" 50 10 30 20 40
	erwarte "$1" "$2 gerade" "n=4 median=25 min=20 max=30 streuung=ok" 30 20 25 25
	erwarte "$1" "$2 genau das Doppelte" "n=3 median=15 min=10 max=20 streuung=ok" 20 10 15
	erwarte "$1" "$2 ueber dem Doppelten" "n=3 median=15 min=10 max=21 streuung=zu-gross" 21 10 15
	erwarte "$1" "$2 ein Wert" "n=1 median=7 min=7 max=7 streuung=ok" 7
	erwarte "$1" "$2 Median nach Sortierung (zweistellig gegen Text)" "n=3 median=9 min=2 max=100 streuung=zu-gross" 100 9 2
}
faelle "$statistik" "Skript"

for eingabe in "" "abc" "1.5" "-3"; do
	if printf '%s' "$eingabe" | bash "$statistik" > /dev/null 2>&1; then pruefe "Eingabe '$eingabe' wurde angenommen"; fi
done
if printf '1\n\n3\n' | bash "$statistik" > /dev/null 2>&1; then pruefe "leere Zeile wurde angenommen"; fi

# Mutationen: jede muss mindestens einen Fall rot machen.
mutiere() { # mutiere <Name> <sed-Ausdruck>
	sed "$2" "$statistik" > "$tmp/$1.sh"
	if cmp -s "$statistik" "$tmp/$1.sh"; then pruefe "Mutation $1 aenderte nichts"; return; fi
	local vorher=$fehler
	fehler=0
	faelle "$tmp/$1.sh" "Mutation $1" 2> "$tmp/$1.aus"
	if [ "$fehler" -eq 0 ]; then
		fehler=1
		echo "lasttest-selbsttest: FEHLER - Mutation $1 blieb gruen" >&2
	else
		fehler=$vorher
	fi
}
mutiere ohne-sortierung 's/| sort -n)/| cat)/'
mutiere ohne-faktor-2 's/\$((2 \* min))/$((min))/'
mutiere maximum-erster-wert 's/^max=.*/max=${sortiert[0]}/'

# Zahlenwerte der Umgebung von tools/lasttest.sh: Exit 2 vor dem ersten docker-Aufruf.
lasttest=$(dirname "$0")/lasttest.sh
mkdir "$tmp/bin"
printf '#!/bin/sh\necho gerufen >> "%s/docker-gerufen"\nexit 1\n' "$tmp" > "$tmp/bin/docker"
chmod +x "$tmp/bin/docker"
abweisung() { # abweisung <Skript> <Beschreibung> <VAR=Wert>
	rm -f "$tmp/docker-gerufen"
	local rc=0
	env PATH="$tmp/bin:$PATH" "$3" bash "$1" > /dev/null 2>&1 || rc=$?
	[ "$rc" -eq 2 ] || { echo "lasttest-selbsttest: FEHLER - $2: Exit $rc statt 2" >&2; return 1; }
	[ ! -e "$tmp/docker-gerufen" ] || { echo "lasttest-selbsttest: FEHLER - $2: docker wurde gerufen" >&2; return 1; }
}
abweisungen() { # abweisungen <Skript>: 0, wenn jede Angabe abgewiesen wird
	local ok=0
	abweisung "$1" "ZEILEN Text" "LASTTEST_ZEILEN=abc" || ok=1
	abweisung "$1" "ZEILEN null" "LASTTEST_ZEILEN=0" || ok=1
	abweisung "$1" "WDH null" "LASTTEST_WDH=0" || ok=1
	abweisung "$1" "CPUS mit Befehl" "LASTTEST_CPUS=2;x" || ok=1
	abweisung "$1" "SEED Text" "LASTTEST_SEED=abc" || ok=1
	abweisung "$1" "ZEILEN fuehrende Null" "LASTTEST_ZEILEN=08" || ok=1
	abweisung "$1" "ZEILEN zu lang" "LASTTEST_ZEILEN=12345678901234567890" || ok=1
	abweisung "$1" "SITZUNGEN null" "LASTTEST_SITZUNGEN=0" || ok=1
	abweisung "$1" "ANFRAGEN Text" "LASTTEST_ANFRAGEN=x" || ok=1
	abweisung "$1" "QUERY_TIMEOUT Text" "LASTTEST_QUERY_TIMEOUT=x" || ok=1
	abweisung "$1" "PHASEN unbekannt" "LASTTEST_PHASEN=export;x" || ok=1
	return $ok
}
abweisungen "$lasttest" || fehler=1
sed '/^# Zahlenwerte vor dem Bau/,/^lauf=/{/^lauf=/!d}' "$lasttest" > "$tmp/ohne-pruefung.sh"
if cmp -s "$lasttest" "$tmp/ohne-pruefung.sh"; then pruefe "Mutation ohne-pruefung aenderte nichts"; elif abweisungen "$tmp/ohne-pruefung.sh" 2> /dev/null; then pruefe "Mutation ohne-pruefung blieb gruen"; fi

# Handlaeufe von tools/lasttest.sh gegen einen docker-Stub (Aufbau A und B) und eine Nachbildung von /proc.
stub="$tmp/stub"
mkdir -p "$tmp/sbin" "$stub/proc"
cat > "$tmp/sbin/docker" <<'STUB'
#!/bin/sh
d="$STUB_DIR"
case "$1" in
	version) echo 29.0.0; exit 0 ;;
	image) echo sha256:stub; exit 0 ;;
	logs) echo "tdswire: gestartet, Port 1433"; echo "lasttest: server aufbau=B stub"; [ ! -f "$d/server-log" ] || cat "$d/server-log"; exit 0 ;;
	inspect) echo true; exit 0 ;;
	exec) echo 0; exit 0 ;;
	run)
		case "$*" in
		*LasttestAlleinKt*)
			cat "$d/a-ausgabe"
			[ ! -f "$d/meminfo-nach" ] || cp "$d/meminfo-nach" "$d/proc/meminfo"
			exit 0 ;;
		esac
		[ "$2" = "-d" ] && exit 0
		cat "$d/client-ausgabe"
		[ ! -f "$d/meminfo-nach" ] || cp "$d/meminfo-nach" "$d/proc/meminfo"
		[ ! -f "$d/server-log-zusatz" ] || cat "$d/server-log-zusatz" >> "$d/server-log"
		exit 0 ;;
	*) exit 0 ;;
esac
STUB
chmod +x "$tmp/sbin/docker"
{
	echo "lasttest: a zeilen=1000 seed=42 wdh=3 heap_max=1"
	for z in "erzeuger 900 110 120 130" "simulation 900 210 220 230" "simulation-import 900 310 320 330"; do
		set -- $z
		n=0
		name=$1
		shift
		for ms in "$@"; do echo "lasttest: a $name lauf $n zeilen 1000 ms $ms"; n=$((n + 1)); done
	done
} > "$stub/a-ausgabe"
client_ausgabe() { # client_ausgabe <mit|ohne Abweisungszeile>
	for n in 0 1 2 3; do echo "lasttest: export lauf $n zeilen 1000 ms 100 erste_zeile_ms 1 rx_bytes 328000"; done > "$stub/client-ausgabe"
	[ "$1" = ohne ] || echo "lasttest: import aufbau B abgewiesen: InvalidOperationException Number  Meldung 'x'" >> "$stub/client-ausgabe"
}
host() { # host <ruhig|heiss>: Nachbildung von meminfo und loadavg; heiss: Last 99 und der Swap waechst waehrend des Containers
	printf 'MemTotal: 32000000 kB\nMemAvailable: 20000000 kB\nSwapTotal: 16000000 kB\nSwapFree: 2000000 kB\n' > "$stub/proc/meminfo"
	rm -f "$stub/meminfo-nach"
	if [ "$1" = heiss ]; then
		echo "99.00 99.00 99.00 1/1 1" > "$stub/proc/loadavg"
		printf 'MemTotal: 32000000 kB\nMemAvailable: 20000000 kB\nSwapTotal: 16000000 kB\nSwapFree: 1000000 kB\n' > "$stub/meminfo-nach"
	else
		echo "1.00 1.00 1.00 1/1 1" > "$stub/proc/loadavg"
	fi
}
EXTRA=
handlauf() { # handlauf <Skript> <Aufbau>: Ausgabe nach $tmp/aus, Fehlerausgabe nach $tmp/fehl, Exit-Code auf der Standardausgabe
	local rc=0
	env PATH="$tmp/sbin:$PATH" STUB_DIR="$stub" LASTTEST_PROC="$stub/proc" LASTTEST_ZEILEN=1000 LASTTEST_WDH=3 \
		LASTTEST_AUFBAUTEN="$2" LASTTEST_SENKEN=mit $EXTRA bash "$1" > "$tmp/aus" 2> "$tmp/fehl" || rc=$?
	echo "$rc"
}
pruefung_a() { # die Mediane der drei A-Zeilen stehen in der richtigen Spalte
	host ruhig
	[ "$(handlauf "$1" A)" = 0 ] || return 1
	grep -q 'aufbau A backend erzeuger .*richtung export .*median_ms 120 ' "$tmp/aus" \
		&& grep -q 'aufbau A backend simulation .*richtung export .*median_ms 220 ' "$tmp/aus" \
		&& grep -q 'aufbau A backend simulation .*richtung import .*median_ms 320 ' "$tmp/aus"
}
pruefung_b() { # ruhiger Host: kein Vermerk; heisser Host: alle drei A-Zeilen tragen beide Vermerke
	host ruhig
	[ "$(handlauf "$1" A)" = 0 ] || return 1
	[ "$(grep -c '^messung: .* aufbau A ' "$tmp/aus")" = 3 ] || return 1
	! grep '^messung: .* aufbau A ' "$tmp/aus" | grep -q 'last-hoch\|swap-gewachsen' || return 1
	host heiss
	[ "$(handlauf "$1" A)" = 0 ] || return 1
	[ "$(grep -c '^messung: .* aufbau A .* last-hoch swap-gewachsen$' "$tmp/aus")" = 3 ]
}
pruefung_c() { # B: ohne Abweisungszeile Exit 1 und die Meldung, mit der Zeile Exit 0
	host ruhig
	client_ausgabe ohne
	[ "$(handlauf "$1" B)" = 1 ] && grep -q 'Import nicht abgewiesen' "$tmp/fehl" || return 1
	client_ausgabe mit
	[ "$(handlauf "$1" B)" = 0 ] && grep -q '^lasttest: gruen' "$tmp/aus"
}
sitzungs_ausgabe() { # Client- und Serverzeilen von vier Sitzungen in Aufbau C (1000 Zeilen, 3 Laeufe nach dem Warmlauf)
	rm -f "$stub/server-log"
	{
		for n in 0 1 2 3; do echo "lasttest: export lauf $n zeilen 1000 ms 100 erste_zeile_ms $((n + 5)) rx_bytes 1312000 sitzungen 4"; done
		for n in 0 1 2 3; do echo "lasttest: import lauf $n zeilen 1000 ms 200 tx_bytes 1348000 sitzungen 4"; done
		echo "lasttest: punkt lauf 0 anfragen 10 p50_us 999 p95_us 999 p99_us 999 min_us 1 max_us 999 sitzungen 4"
		echo "lasttest: punkt lauf 1 anfragen 10 p50_us 100 p95_us 300 p99_us 700 min_us 1 max_us 700 sitzungen 4"
		echo "lasttest: punkt lauf 2 anfragen 10 p50_us 120 p95_us 340 p99_us 800 min_us 1 max_us 800 sitzungen 4"
		echo "lasttest: punkt lauf 3 anfragen 10 p50_us 110 p95_us 320 p99_us 900 min_us 1 max_us 900 sitzungen 4"
	} > "$stub/client-ausgabe"
	{
		for n in $(seq 1 16); do echo "lasttest: import $((n * 1000))"; done
		for id in a b c; do
			echo "{\"event\":\"span_ended\",\"span\":\"backend.execute\",\"request_id\":\"$id\",\"duration_ms\":3}"
			echo "{\"event\":\"span_ended\",\"span\":\"tds.query\",\"request_id\":\"$id\",\"duration_ms\":9,\"request_kind\":\"rpc\"}"
		done
		echo "{\"event\":\"span_ended\",\"span\":\"backend.execute\",\"request_id\":\"d\",\"duration_ms\":70}"
		echo "{\"event\":\"span_ended\",\"span\":\"tds.query\",\"request_id\":\"d\",\"duration_ms\":80,\"request_kind\":\"sql_batch\"}"
	} > "$stub/server-log-zusatz"
}
pruefung_d() { # vier Sitzungen: Zeilen/s je Wanduhr aller Sitzungen, Zeit der ersten Zeile, Perzentile ohne Warmlauf, Spans nach Art, Zaehlpruefung je Sitzung
	host ruhig
	sitzungs_ausgabe
	EXTRA="LASTTEST_SITZUNGEN=4 LASTTEST_PHASEN=export\ import\ punkt-export\ punkt-import"
	local rc
	rc="$(env_handlauf "$1")"
	EXTRA=
	[ "$rc" = 0 ] || return 1
	grep -q 'aufbau C senke mit richtung export zeilen 1000 sitzungen 4 .* median_ms 100 .* zeilen_je_s 40000 bytes_je_zeile 328 .* erste_zeile_ms_median 7 erste_zeile_ms_min 6 erste_zeile_ms_max 8 ' "$tmp/aus" \
		&& grep -q 'aufbau C senke mit richtung import zeilen 1000 sitzungen 4 .* median_ms 200 .* zeilen_je_s 20000 bytes_je_zeile 337 ' "$tmp/aus" \
		&& grep -q 'richtung punkt-export .* anfragen 10 p50_us_median 110 p50_us_min 100 p50_us_max 120 p95_us_median 320 p95_us_min 300 p95_us_max 340 p99_us_median 800 p99_us_min 700 p99_us_max 900 ' "$tmp/aus" \
		&& grep -q 'richtung punkt-import ' "$tmp/aus" \
		&& grep -q 'phase punkt-export span tds.query art rpc n=3 median=9 min=9 max=9$' "$tmp/aus" \
		&& grep -q 'phase punkt-export span backend.execute art rpc n=3 median=3 min=3 max=3$' "$tmp/aus" \
		&& grep -q 'phase punkt-export span backend.execute art sql_batch n=1 median=70 min=70 max=70$' "$tmp/aus"
}
pruefung_d_heiss() { # heisser Host: auch die Zeilen der Punktabfrage tragen beide Vermerke
	host heiss
	sitzungs_ausgabe
	EXTRA="LASTTEST_SITZUNGEN=4 LASTTEST_PHASEN=punkt-export"
	local rc
	rc="$(env_handlauf "$1")"
	EXTRA=
	[ "$rc" = 0 ] || return 1
	grep -q '^messung: .* richtung punkt-export .* last-hoch swap-gewachsen$' "$tmp/aus"
}
env_handlauf() { # wie handlauf, mit LASTTEST_ZEILEN=1000 und den Angaben aus $EXTRA (Leerzeichen als \ )
	local rc=0
	eval "env PATH=\"$tmp/sbin:\$PATH\" STUB_DIR=\"$stub\" LASTTEST_PROC=\"$stub/proc\" LASTTEST_ZEILEN=1000 LASTTEST_WDH=3 LASTTEST_ANFRAGEN=10 LASTTEST_AUFBAUTEN=C LASTTEST_SENKEN=mit $EXTRA bash \"$1\" > \"$tmp/aus\" 2> \"$tmp/fehl\"" || rc=$?
	echo "$rc"
}
pruefung_e() { # Streuung der Punktabfrage: gleiche p95 gelten als ok, p95 mit mehr als dem Doppelten Unterschied als zu-gross
	host ruhig
	sitzungs_ausgabe
	EXTRA="LASTTEST_SITZUNGEN=4 LASTTEST_PHASEN=punkt-export"
	[ "$(env_handlauf "$1")" = 0 ] || { EXTRA=; return 1; }
	grep -q 'richtung punkt-export .* streuung_p95 ok vorher' "$tmp/aus" || { EXTRA=; return 1; }
	sed -i 's/p95_us 340 /p95_us 900 /' "$stub/client-ausgabe"
	[ "$(env_handlauf "$1")" = 0 ] || { EXTRA=; return 1; }
	EXTRA=
	grep -q 'richtung punkt-export .* streuung_p95 zu-gross vorher' "$tmp/aus"
}
pruefung_f() { # Laeufe der Punktabfrage: fehlt ein Lauf, endet der Lauf mit Exit 1 und der Meldung
	host ruhig
	sitzungs_ausgabe
	sed -i '/punkt lauf 3 /d' "$stub/client-ausgabe"
	EXTRA="LASTTEST_SITZUNGEN=4 LASTTEST_PHASEN=punkt-export"
	local rc
	rc="$(env_handlauf "$1")"
	EXTRA=
	[ "$rc" = 1 ] && grep -q 'statt 3 Läufe der Punktabfrage' "$tmp/fehl"
}
pruefung_g() { # Aufbau B hat keine Phase punkt-import: keine Zeile dafuer, Exit 0
	host ruhig
	sitzungs_ausgabe
	EXTRA="LASTTEST_SITZUNGEN=4 LASTTEST_AUFBAUTEN=B LASTTEST_PHASEN=punkt-import"
	local rc
	rc="$(env_handlauf "$1")"
	EXTRA=
	[ "$rc" = 1 ] && return 1
	! grep -q 'richtung punkt-import' "$tmp/aus"
}
kopie() { # kopie <Name> <sed-Programm> [Zeile]: veraendertes lasttest.sh samt Statistik in $tmp/m/<Name>; druckt den Pfad
	mkdir -p "$tmp/m/$1"
	cp "$(dirname "$lasttest")/lasttest-statistik.sh" "$tmp/m/$1/"
	sed "${3:-}$2" "$lasttest" > "$tmp/m/$1/lasttest.sh"
	if cmp -s "$lasttest" "$tmp/m/$1/lasttest.sh"; then echo "lasttest-selbsttest: FEHLER - Mutation $1 aenderte nichts" >&2; fehler=1; fi
	echo "$tmp/m/$1/lasttest.sh"
}
mutationen=0
rot() { # rot <Pruefung> <Skript> <Name>: die Pruefung muss am veraenderten Skript scheitern
	mutationen=$((mutationen + 1))
	if "$1" "$2" 2> /dev/null; then pruefe "Mutation $3 blieb gruen"; fi
}
for fall in a b c d d_heiss e f g; do "pruefung_$fall" "$lasttest" || pruefe "Handlauf $fall am unveraenderten Skript"; done
rot pruefung_a "$(kopie a-falsche-spalte 's/{print \$9}/{print $7}/g')" a-falsche-spalte
for n in $(grep -n 'zustand)\]$(vermerke' "$lasttest" | cut -d: -f1); do
	mutationen=$((mutationen + 1))
	m="$(kopie "b-ohne-vermerk-$n" 's/\[\$(zustand)\]\$(vermerke "\$vorher")"/[$(zustand)]"/' "${n}")"
	if pruefung_b "$m" 2> /dev/null && pruefung_d_heiss "$m" 2> /dev/null; then pruefe "Mutation b-ohne-vermerk-$n blieb gruen"; fi
done
rot pruefung_b "$(kopie b-vermerk-immer 's/^\t\[ "\$(last_ganz)" -le "\$last_max" \] || v=" last-hoch"/\tv=" last-hoch"/')" b-vermerk-immer
rot pruefung_c "$(kopie c-ohne-pruefung 's/^\(\t*\)if grep -E .import aufbau B abgewiesen. .*; then :; else/\1if true; then :; else/')" c-ohne-pruefung

rot pruefung_d "$(kopie d-ohne-sitzungsfaktor 's/zeilen \* sitzungen \* 1000 \/ median/zeilen * 1000 \/ median/')" d-ohne-sitzungsfaktor
rot pruefung_d "$(kopie d-falsches-perzentil 's/for feld in p50_us p95_us p99_us; do/for feld in p50_us p50_us p99_us; do/')" d-falsches-perzentil
rot pruefung_d "$(kopie d-ohne-warmlauf '/-v b="\$feld"/s/ \&\& \$(i + 1) >= 1//')" d-ohne-warmlauf
rot pruefung_d "$(kopie d-span-ohne-art 's/if (id in art) print "backend.execute", art\[id\], dauer\[id\]/print "backend.execute", "rpc", dauer[id]/')" d-span-ohne-art
rot pruefung_d "$(kopie d-ohne-sitzungszaehlung 's/-eq \$((sitzungen \* (wdh + 1)))/-eq $((wdh + 1))/')" d-ohne-sitzungszaehlung
rot pruefung_e "$(kopie e-ohne-streuung-p95 's/^\t\t\[ "\$feld" != p95_us \] || p95streuung=.*$/\t\tp95streuung=ok/')" e-ohne-streuung-p95
rot pruefung_f "$(kopie f-ohne-laufzaehlung 's/^\t\[ "\$anzahl" -eq "\$wdh" \] || .*$/\t:/')" f-ohne-laufzaehlung
rot pruefung_g "$(kopie g-punkt-import-in-b 's/if \[ "\$phase" = punkt-import \] \&\& \[ "\$aufbau" = B \]; then continue; fi/:/;s/\[ "\$phase" = punkt-import \] \&\& \[ "\$aufbau" = B \] \&\& continue/:/')" g-punkt-import-in-b

[ "$fehler" -eq 0 ] || exit 1
echo "lasttest-selbsttest: ok (6 Faelle, Eingabefehler, 3 Mutationen rot, elf abgewiesene Umgebungswerte, 1 Mutation rot; Handlaeufe a bis g, $mutationen Mutationen rot)"
