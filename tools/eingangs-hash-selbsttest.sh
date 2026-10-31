#!/usr/bin/env bash
# tools/eingangs-hash-selbsttest.sh - belegt ohne Docker, dass tools/eingangs-hash.sh auf Inhalt reagiert und auf
# mtime nicht (TDS-QA-06). Aufgerufen von der Dockerfile-Stage `skript-selbsttest`.
set -euo pipefail
hash=$(cd "$(dirname "$0")" && pwd)/eingangs-hash.sh
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
fehler=0
pruefe() { echo "eingangs-hash-selbsttest: FEHLER - $1" >&2; fehler=1; }
h() { (cd "$tmp" && bash "$hash" "$@"); }

mkdir -p "$tmp/d/sub" "$tmp/d/bin" "$tmp/d/obj"
echo a > "$tmp/d/a.txt"; echo b > "$tmp/d/sub/b.txt"; echo x > "$tmp/ein.mk"
basis=$(h d ein.mk)

touch -d '2001-01-01' "$tmp/d/a.txt"
[ "$(h d ein.mk)" = "$basis" ] || pruefe "touch aendert den Hash"
[ "$(h ein.mk d)" = "$basis" ] || pruefe "die Reihenfolge der Pfade aendert den Hash"
echo junk > "$tmp/d/bin/x.dll"; echo junk > "$tmp/d/obj/y"
[ "$(h d ein.mk)" = "$basis" ] || pruefe "bin/ und obj/ zaehlen"

echo a2 > "$tmp/d/a.txt"
[ "$(h d ein.mk)" != "$basis" ] || pruefe "Inhaltsaenderung in d/a.txt bleibt unbemerkt"
echo a > "$tmp/d/a.txt"
echo b2 > "$tmp/d/sub/b.txt"
[ "$(h d ein.mk)" != "$basis" ] || pruefe "Inhaltsaenderung in d/sub/b.txt bleibt unbemerkt"
echo b > "$tmp/d/sub/b.txt"
echo y > "$tmp/ein.mk"
[ "$(h d ein.mk)" != "$basis" ] || pruefe "Inhaltsaenderung in ein.mk bleibt unbemerkt"
echo x > "$tmp/ein.mk"
echo n > "$tmp/d/neu.txt"
[ "$(h d ein.mk)" != "$basis" ] || pruefe "neue Datei bleibt unbemerkt"
rm "$tmp/d/neu.txt"
mv "$tmp/d/a.txt" "$tmp/d/c.txt"
[ "$(h d ein.mk)" != "$basis" ] || pruefe "Umbenennung bleibt unbemerkt"
mv "$tmp/d/c.txt" "$tmp/d/a.txt"
rm "$tmp/d/sub/b.txt"
[ "$(h d ein.mk)" != "$basis" ] || pruefe "geloeschte Datei bleibt unbemerkt"
echo b > "$tmp/d/sub/b.txt"
[ "$(h d ein.mk)" = "$basis" ] || pruefe "wiederhergestellter Stand hat anderen Hash"
exit "$fehler"
