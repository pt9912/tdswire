#!/usr/bin/env bash
# tools/lasttest-statistik.sh - die Statistik einer Messreihe des Lasttests (TDS-QA-04, docs/plan/referenz-workload.md
# Abschnitt 5, S11): liest Zahlen (eine je Zeile, ganze Zahlen) von der Standardeingabe und schreibt eine Zeile
#   n=<Anzahl> median=<Wert> min=<Wert> max=<Wert> streuung=ok|zu-gross
# Der Median einer geraden Anzahl ist der abgerundete Mittelwert der zwei mittleren Werte. `streuung=zu-gross` heisst
# Maximum mehr als das Doppelte des Minimums (Setzung S11): die Stufe ist dann "nicht belegt", nicht gemittelt. Eine leere
# Eingabe oder eine Zeile, die keine nichtnegative ganze Zahl ist, endet mit Exit 1 ohne Ausgabe. Gehalten von
# tools/lasttest-selbsttest.sh (Stage `skript-selbsttest`); Aufrufer ist tools/lasttest.sh.
set -u
werte=()
while IFS= read -r zeile; do
	[[ $zeile =~ ^[0-9]+$ ]] || { echo "lasttest-statistik: keine ganze Zahl: '$zeile'" >&2; exit 1; }
	werte+=("$zeile")
done
[ "${#werte[@]}" -gt 0 ] || { echo "lasttest-statistik: keine Werte" >&2; exit 1; }
mapfile -t sortiert < <(printf '%s\n' "${werte[@]}" | sort -n)
n=${#sortiert[@]}
min=${sortiert[0]}
max=${sortiert[$((n - 1))]}
if [ $((n % 2)) -eq 1 ]; then
	median=${sortiert[$((n / 2))]}
else
	median=$(((sortiert[n / 2 - 1] + sortiert[n / 2]) / 2))
fi
streuung=ok
[ "$max" -le $((2 * min)) ] || streuung=zu-gross
echo "n=$n median=$median min=$min max=$max streuung=$streuung"
