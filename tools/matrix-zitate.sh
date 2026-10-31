#!/bin/sh
# matrix-zitate.sh (TDS-QA-05) — haelt die Zitate `Klasse > Testfall` einer Matrix-Datei gegen einen Gradle-Testbericht
# (Zeilen `Klasse > Testfall() PASSED`, wie die Stage `test` sie mit --progress=plain schreibt).
# Aufruf: matrix-zitate.sh <Matrix.md> <Testbericht>. Exit 0: jedes Zitat steht als PASSED im Bericht;
# Exit 1: mindestens ein Zitat nicht (die Liste steht auf stdout); Exit 2: Aufruf oder Eingang unbrauchbar.
# Zitat-Form: Inline-Code `<Klasse>Test > <Testfall>` in einer Zeile. Jedes `<Klasse>Test >` ausserhalb dieser
# Form (ohne Backticks, Code-Span ueber den Zeilenumbruch) ist eine Fremdform: sie wird nicht gelesen, die Zeilen
# stehen auf stdout, die Zahl steht in der Zusammenfassung, und der Lauf endet mit Exit 1.
# Grenze: Der Waechter erkennt eine Fremdform nur, wenn der Klassenname auf `Test` endet; ein Zitat einer
# Klasse mit anderem Namensende liest und meldet er nicht.
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

# Zeilen mit mehr Vorkommen von `<Klasse>Test >` als gelesenen Zitaten der Zeile: Fremdform
awk '{
  alle = gsub(/[A-Za-z0-9]*Test >/, "&")
  gelesen = gsub(/`[A-Za-z0-9]*Test > [^`]*`/, "&")
  if (alle > gelesen) { print NR ": " $0; fremd += alle - gelesen }
} END { print fremd + 0 > "/dev/stderr" }' "$matrix" > "$tmp/fremd" 2> "$tmp/fremd-zahl"
fremd=$(cat "$tmp/fremd-zahl")

comm -13 "$tmp/bestanden" "$tmp/zitiert" > "$tmp/fehlt"
fehlt=$(wc -l < "$tmp/fehlt")
echo "matrix-zitate: $bestanden bestandene Testfaelle im Bericht, $zitiert verschiedene Zitate, $fehlt ohne PASSED, $fremd nicht gelesene Zitat-Formen"
status=0
if [ "$fehlt" -gt 0 ]; then
  echo "--- zitiert, nicht bestanden:"
  cat "$tmp/fehlt"
  status=1
fi
if [ "$fremd" -gt 0 ]; then
  echo "--- Zitat in Fremdform (nicht gelesen; Inline-Code in einer Zeile verwenden):"
  cut -c1-200 "$tmp/fremd"
  status=1
fi
exit "$status"
