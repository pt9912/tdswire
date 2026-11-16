#!/usr/bin/env bash
# tools/lasttest-selbsttest.sh - belegt, dass tools/lasttest-statistik.sh Median, Minimum, Maximum und die Streuungsregel
# (Maximum mehr als das Doppelte des Minimums) richtig rechnet, Eingabefehler abweist, und dass drei Mutationen des Skripts
# je rot werden (Median ohne Sortierung, Streuung ohne Faktor 2, Maximum als erster Wert) und dass tools/lasttest.sh falsche Zahlenwerte der Umgebung vor
# jedem Docker-Aufruf mit Exit 2 abweist (docker-Stub; eine Mutation ohne die Pruefung wird rot). TDS-QA-04, TDS-QA-06.
# Aufgerufen von der Dockerfile-Stage `skript-selbsttest`; braucht bash, sort, sed, mktemp, grep, awk.
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
	return $ok
}
abweisungen "$lasttest" || fehler=1
sed '/^# Zahlenwerte vor dem Bau/,/^lauf=/{/^lauf=/!d}' "$lasttest" > "$tmp/ohne-pruefung.sh"
if cmp -s "$lasttest" "$tmp/ohne-pruefung.sh"; then pruefe "Mutation ohne-pruefung aenderte nichts"; elif abweisungen "$tmp/ohne-pruefung.sh" 2> /dev/null; then pruefe "Mutation ohne-pruefung blieb gruen"; fi

[ "$fehler" -eq 0 ] || exit 1
echo "lasttest-selbsttest: ok (6 Faelle, Eingabefehler, 3 Mutationen rot, fuenf abgewiesene Umgebungswerte, 1 Mutation rot)"
