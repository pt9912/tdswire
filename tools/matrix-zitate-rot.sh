#!/bin/sh
# matrix-zitate-rot.sh (TDS-QA-05) — nennt, warum der Bau der Stage `test` rot ist: die roten Testfaelle (Zeilen
# `Klasse > Testfall() FAILED`, wie Gradle sie mit --progress=plain schreibt) und die Zusammenfassung
# `N tests completed, M failed`. Traegt der Bericht keine solche Zeile (Uebersetzungsfehler, Docker), stehen die letzten
# 40 Zeilen auf stderr. Aufruf: matrix-zitate-rot.sh <Bericht>. Exit 0 immer; Exit 2: Bericht nicht lesbar.
# Grenze: ein Test, der ohne Zeile `FAILED` endet (Abbruch der JVM), erscheint nur in den letzten 40 Zeilen.
set -eu

[ "$#" -eq 1 ] || { echo "Aufruf: $0 <Bericht>" >&2; exit 2; }
[ -r "$1" ] || { echo "matrix-zitate: Bericht nicht lesbar: $1" >&2; exit 2; }

rot=$(sed 's/^#[0-9]* [0-9.]* //' "$1" | grep -E '\(\) FAILED$|[0-9]+ tests completed, [0-9]+ failed' | sort -u || true)
if [ -n "$rot" ]; then
  echo "matrix-zitate: rote Testfaelle der Stage test:" >&2
  echo "$rot" >&2
else
  tail -n 40 "$1" >&2
fi
