#!/bin/sh
# matrix-zitate.sh (TDS-QA-05) — haelt die Zitate `Klasse > Testfall` einer Matrix-Datei gegen einen Gradle-Testbericht
# (Zeilen `Klasse > Testfall() PASSED`, wie die Stage `test` sie mit --progress=plain schreibt).
# Aufruf: matrix-zitate.sh <Matrix.md> <Testbericht>. Exit 0: jedes Zitat steht als PASSED im Bericht;
# Exit 1: mindestens ein Zitat nicht (die Liste steht auf stdout); Exit 2: Aufruf oder Eingang unbrauchbar.
# Zitat-Form: Inline-Code `<Klasse>Test > <Testfall>`. Ein Zitat in anderer Form (z. B. mit Zeilenumbruch
# im Code-Span) wird nicht gelesen; die Zahl der gelesenen Zitate steht in der Ausgabe.
set -eu

[ "$#" -eq 2 ] || { echo "Aufruf: $0 <Matrix.md> <Testbericht>" >&2; exit 2; }
matrix=$1
bericht=$2
[ -r "$matrix" ] || { echo "matrix-zitate: Matrix nicht lesbar: $matrix" >&2; exit 2; }
[ -r "$bericht" ] || { echo "matrix-zitate: Testbericht nicht lesbar: $bericht" >&2; exit 2; }

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT INT TERM

sed 's/^#[0-9]* [0-9.]* //' "$bericht" | grep -E '\(\) PASSED$' | sed 's/() PASSED$//' | sort -u > "$tmp/bestanden" || true
grep -o '`[A-Za-z0-9]*Test > [^`]*`' "$matrix" | tr -d '`' | sort -u > "$tmp/zitiert" || true

bestanden=$(wc -l < "$tmp/bestanden")
zitiert=$(wc -l < "$tmp/zitiert")
[ "$bestanden" -gt 0 ] || { echo "matrix-zitate: der Bericht enthaelt keinen bestandenen Testfall" >&2; exit 2; }
[ "$zitiert" -gt 0 ] || { echo "matrix-zitate: die Matrix enthaelt kein Zitat der Form \`Klasse > Testfall\`" >&2; exit 2; }

comm -13 "$tmp/bestanden" "$tmp/zitiert" > "$tmp/fehlt"
fehlt=$(wc -l < "$tmp/fehlt")
echo "matrix-zitate: $bestanden bestandene Testfaelle im Bericht, $zitiert verschiedene Zitate, $fehlt ohne PASSED"
if [ "$fehlt" -gt 0 ]; then
  echo "--- zitiert, nicht bestanden:"
  cat "$tmp/fehlt"
  exit 1
fi
