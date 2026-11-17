#!/usr/bin/env bash
# tools/rueckstau-selbsttest.sh - belegt tools/rueckstau.sh vor jedem Docker-Aufruf (TDS-QA-04): die Pruefung von RUECKSTAU_MIN_FREI_MB
# (Exit 2), der Speicherwaechter (Exit 3 unter der Grenze, kein Abbruch genau auf der Grenze), die Pruefung der Zellennamen (Exit 2 bei
# einem unbekannten, alle bekannten Namen kommen durch) und die Summe der Fremdlast (`fremdlast_summe`, Proben ueber 300 %); elf Mutationen
# des Skripts muessen je rot werden. Docker ersetzt ein Stub im PATH, /proc eine Nachbildung (RUECKSTAU_PROC). Die Datei `fall` der Nachbildung
# (der Stub schreibt seine Aufrufe nach `aufrufe`) laesst den Stub bauen und starten und den Speicher an einer Stelle sinken: bei `network` (der Waechter vor dem Server greift) oder beim Start
# des Servers (der Waechter vor der Fremdlast-Probe greift); ohne die Datei scheitert der Stub-Bau, und das Skript endet vor `zelle()`. Aufgerufen von der
# Dockerfile-Stage `skript-selbsttest`; braucht bash, awk, sed, grep, mktemp. Grenze: belegt den Anlauf des Skripts, keinen Lauf gegen Docker.
set -u
tool=$(cd "$(dirname "$0")" && pwd)/rueckstau.sh
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
fehler=0

mkdir -p "$tmp/bin" "$tmp/proc-knapp" "$tmp/proc-genau" "$tmp/proc-reichlich" "$tmp/proc-fall"
cat >"$tmp/bin/docker" <<'STUB'
#!/bin/sh
# Stub: ohne die Datei `fall` in RUECKSTAU_PROC scheitert `build`; mit ihr gelingen build, network, run -d und logs, und der Speicher
# sinkt bei `network` (fall: vor-server) oder beim Start des Servers (fall: vor-client); `run` scheitert sonst (Client, andere Faelle).
fall=$(cat "$RUECKSTAU_PROC/fall" 2>/dev/null)
echo "$*" >>"$RUECKSTAU_PROC/aufrufe"
senke() { printf 'MemTotal: 33000000 kB\nMemAvailable: 1000 kB\nSwapTotal: 100 kB\nSwapFree: 50 kB\n' >"$RUECKSTAU_PROC/meminfo"; }
case "$1" in
build) [ -n "$fall" ] || exit 1 ;;
network) [ "$2" = create ] && [ "$fall" = vor-server ] && senke ;;
run) [ "$2" = -d ] || exit 1
	case "$fall" in vor-*) ;; *) exit 1 ;; esac
	[ "$fall" = vor-client ] && senke ;;
logs) echo "tdswire: gestartet, Port 1433" ;;
inspect) echo true ;;
esac
exit 0
STUB
chmod +x "$tmp/bin/docker"
nachbildung() { # nachbildung <Verzeichnis> <MemAvailable in kB>
	printf 'MemTotal: 33000000 kB\nMemAvailable: %s kB\nSwapTotal: 100 kB\nSwapFree: 50 kB\n' "$2" >"$1/meminfo"
	echo "0.10 0.10 0.10 1/1 1" >"$1/loadavg"
	echo "cpu  1 1 1 1 1 1 1 0" >"$1/stat"
}
nachbildung "$tmp/proc-knapp" 1000
nachbildung "$tmp/proc-genau" 4194304
nachbildung "$tmp/proc-reichlich" 30000000

alle="leser-eigen-1 leser-eigen-8 leser-eigen-64 leser-eigen-256 leser-eigen-256halb leser-sim-1 leser-sim-8 leser-sim-64 leser-sim-256 leser-frist blk-suspendierend-64 blk-unterbrechbar-8 blk-unterbrechbar-64 blk-unterbrechbar-256 blk-nicht-unterbrechbar-64 blk-nicht-unterbrechbar-256 blk-direkt-8 abbruch-suspendierend abbruch-unterbrechbar abbruch-nicht-unterbrechbar abbruch-nicht-unterbrechbar-ohnelast imp-1-schnell imp-1-callback imp-8-callback imp-8-callback-direkt imp-8-callback-unterbrechbar imp-1-sender"

# erwarte <Meldung> <Exit erwartet> <Skript> [VAR=Wert ...]: Lauf mit Stub-Docker; zaehlt Abweichungen in $abweichungen
erwarte() {
	local meldung="$1" soll="$2" skript="$3" ist
	shift 3
	env PATH="$tmp/bin:$PATH" "$@" bash "$skript" >"$tmp/aus" 2>&1
	ist=$?
	if [ "$ist" -ne "$soll" ]; then
		echo "rueckstau-selbsttest: ABWEICHUNG - $meldung: Exit $ist statt $soll" >&2
		abweichungen=$((abweichungen + 1))
	fi
}

pruefe_skript() { # pruefe_skript <Skript>: setzt $abweichungen
	local skript="$1" wert summe
	abweichungen=0
	for wert in abc -1 01 1.5 1234567890; do
		erwarte "RUECKSTAU_MIN_FREI_MB=$wert" 2 "$skript" RUECKSTAU_PROC="$tmp/proc-reichlich" RUECKSTAU_MIN_FREI_MB="$wert" RUECKSTAU_ZELLEN=leser-frist
	done
	erwarte "unbekannte Zelle" 2 "$skript" RUECKSTAU_PROC="$tmp/proc-reichlich" RUECKSTAU_ZELLEN="leser-eigen-1 nope"
	erwarte "Zelle mit Zusatz" 2 "$skript" RUECKSTAU_PROC="$tmp/proc-reichlich" RUECKSTAU_ZELLEN="leser-eigen-1x"
	erwarte "knapper Speicher" 3 "$skript" RUECKSTAU_PROC="$tmp/proc-knapp" RUECKSTAU_ZELLEN=leser-frist
	grep -q 'ABBRUCH - verfuegbarer Speicher' "$tmp/aus" || { echo "rueckstau-selbsttest: ABWEICHUNG - keine Meldung des Waechters" >&2; abweichungen=$((abweichungen + 1)); }
	erwarte "Speicher genau auf der Grenze" 1 "$skript" RUECKSTAU_PROC="$tmp/proc-genau" RUECKSTAU_ZELLEN=leser-frist
	erwarte "Grenze gesenkt" 1 "$skript" RUECKSTAU_PROC="$tmp/proc-knapp" RUECKSTAU_MIN_FREI_MB=0 RUECKSTAU_ZELLEN=leser-frist
	erwarte "alle bekannten Zellen" 1 "$skript" RUECKSTAU_PROC="$tmp/proc-reichlich" RUECKSTAU_ZELLEN="$alle"
	for fall in vor-server vor-client; do
		nachbildung "$tmp/proc-fall" 30000000
		echo "$fall" >"$tmp/proc-fall/fall"
		rm -f "$tmp/proc-fall/aufrufe"
		erwarte "Waechter in zelle() $fall" 3 "$skript" RUECKSTAU_PROC="$tmp/proc-fall" RUECKSTAU_ZELLEN=leser-frist
		grep -q 'ABBRUCH - verfuegbarer Speicher' "$tmp/aus" || { echo "rueckstau-selbsttest: ABWEICHUNG - keine Meldung des Waechters in zelle() ($fall)" >&2; abweichungen=$((abweichungen + 1)); }
		# Der Waechter vor dem Server greift, bevor der Server laeuft: `run -d` ruft das Skript dann nicht.
		if [ "$fall" = vor-server ] && grep -q '^run -d' "$tmp/proc-fall/aufrufe"; then
			echo "rueckstau-selbsttest: ABWEICHUNG - der Server startet trotz knappem Speicher" >&2
			abweichungen=$((abweichungen + 1))
		fi
	done
	# Jeder Name des Musters hat einen Zweig: mit gelingendem Bau meldet das Skript sonst "unbekannte Zelle" zur Laufzeit.
	nachbildung "$tmp/proc-fall" 30000000
	echo ohne-abbruch >"$tmp/proc-fall/fall"
	erwarte "alle Zellen mit Zweig" 1 "$skript" RUECKSTAU_PROC="$tmp/proc-fall" RUECKSTAU_ZELLEN="$alle"
	if grep -q 'unbekannte Zelle' "$tmp/aus"; then
		echo "rueckstau-selbsttest: ABWEICHUNG - eine Zelle des Musters hat keinen Zweig: $(grep 'unbekannte Zelle' "$tmp/aus" | head -1)" >&2
		abweichungen=$((abweichungen + 1))
	fi
	printf '1 100 50 50\n2 400 100 300\n3 500 150 350\n4 900 100 800\n' >"$tmp/probe"
	# shellcheck disable=SC1090
	summe=$(RUECKSTAU_PROC="$tmp/proc-reichlich" bash -c 'source "$1"; fremdlast_summe "$2"' _ "$skript" "$tmp/probe" 2>&1)
	[ "$summe" = "proben 4 ueber300 2 max 800%" ] || { echo "rueckstau-selbsttest: ABWEICHUNG - Fremdlast: '$summe'" >&2; abweichungen=$((abweichungen + 1)); }
}

pruefe_skript "$tool"
[ "$abweichungen" -eq 0 ] || fehler=1
echo "rueckstau-selbsttest: Skript - $abweichungen Abweichungen"

mutiere() { # mutiere <Name> <sed-Ausdruck>: das Skript muss rot werden
	mkdir -p "$tmp/m/tools"
	sed "$2" "$tool" >"$tmp/m/tools/rueckstau.sh"
	if cmp -s "$tool" "$tmp/m/tools/rueckstau.sh"; then
		echo "rueckstau-selbsttest: FEHLER - Mutation $1 aendert nichts" >&2
		fehler=1
		return
	fi
	pruefe_skript "$tmp/m/tools/rueckstau.sh" 2>/dev/null
	if [ "$abweichungen" -eq 0 ]; then
		echo "rueckstau-selbsttest: FEHLER - Mutation $1 bleibt gruen" >&2
		fehler=1
	else
		echo "rueckstau-selbsttest: ok   Mutation $1 faerbt $abweichungen Pruefungen rot"
	fi
}
mutiere waechter-le 's/-lt "\$min_frei"/-le "$min_frei"/'
mutiere waechter-ohne-abbruch 's/^\t\texit 3$/\t\ttrue/'
mutiere zahl-fuehrende-null 's/\^(0|\[1-9\]\[0-9\]{0,8})\$/^([0-9]{1,9})$/'
mutiere zellen-ungeprueft 's/\[\[ \$z =~ \$zellen_muster \]\] || {.*; }/true/'
mutiere zellen-ohne-blk 's/|blk-(suspendierend|unterbrechbar|nicht-unterbrechbar|direkt)-\[0-9\]+//'
mutiere fremdlast-ab-300 's/\$4 > 300/$4 >= 300/'
mutiere waechter-zelle-beide-gestrichen 's/^\tspeicherwacht$/\t:/'
mutiere waechter-zelle-erster-gestrichen '0,/^\tspeicherwacht$/s//\t:/'
mutiere waechter-zelle-zweiter-gestrichen ':a;N;$!ba;s/\(\n\tspeicherwacht\n\(.*\)\)\n\tspeicherwacht\n/\1\n\t:\n/'
mutiere zweig-blk-fehlt 's/^\tblk-\*)/\tblkx-*)/'
mutiere zweig-imp-fehlt 's/^\timp-1-sender) /\timpx-1-sender) /'

[ "$fehler" -eq 0 ] || exit 1
echo "rueckstau-selbsttest: gruen"
