#!/usr/bin/env bash
# tools/integrationszahlen-selbsttest.sh - belegt, dass tools/integrationszahlen-pruefung.sh jede Abweichung der
# Zahlen des Integrationslaufs abweist (TDS-FA-11, TDS-QA-06): die unveraenderten Dateien sind gruen; je ein Ort
# geaendert ist rot - Heap in build.gradle.kts, BIG_ROWS, GrosseZeichen im .NET-Test, Heap und Zeichenzahl in
# SPEC-040, Heap in der README, je eine fehlende Zeile. Aufgerufen von der Dockerfile-Stage `kopplung-zahlen`.
set -euo pipefail
dir=$(cd "$(dirname "$0")" && pwd)
pruefung="$dir/integrationszahlen-pruefung.sh"
wurzel=$(cd "$dir/.." && pwd)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
fehler=0
pruefe() { echo "integrationszahlen-selbsttest: FEHLER - $1" >&2; fehler=1; }

kt=src/test/kotlin/io/github/pt9912/tdswire/runtime/IntegrationServer.kt
cs=referenztest-dotnet/Integration/SimulationBackendTests.cs
dateien="build.gradle.kts $kt $cs spec/spezifikation.md harness/README.md"
frisch() { rm -rf "$tmp/w"; for d in $dateien; do mkdir -p "$tmp/w/$(dirname "$d")"; cp "$wurzel/$d" "$tmp/w/$d"; done; }
# rot <name> <datei> <sed-ausdruck>: wendet die Aenderung an (sie muss die Datei veraendern) und erwartet Ausgang != 0.
rot() {
  frisch
  cp "$tmp/w/$2" "$tmp/vorher"
  sed -i "$3" "$tmp/w/$2"
  if cmp -s "$tmp/vorher" "$tmp/w/$2"; then pruefe "$1: die Aenderung traf nichts"; return; fi
  if bash "$pruefung" "$tmp/w" > /dev/null 2>&1; then pruefe "$1: nicht rot"; fi
}

frisch
bash "$pruefung" "$tmp/w" > /dev/null 2>&1 || pruefe "unveraenderte Dateien sind nicht gruen"

rot "Heap in build.gradle.kts" build.gradle.kts 's/maxHeapSize = "48m"/maxHeapSize = "40m"/'
rot "BIG_ROWS" "$kt" 's/BIG_ROWS = 500_000/BIG_ROWS = 400_000/'
rot "GrosseZeichen im .NET-Test" "$cs" 's/GrosseZeichen = 128/GrosseZeichen = 129/'
rot "Heap in SPEC-040" spec/spezifikation.md '/^| `SPEC-040`/s/Serverprozess mit 48 MiB/Serverprozess mit 40 MiB/'
rot "Zeichenzahl in SPEC-040" spec/spezifikation.md '/^| `SPEC-040`/s/Text von 64 000 000 Zeichen/Text von 63 000 000 Zeichen/'
rot "Heap in der README" harness/README.md '/^| `make simulations-integration`/s/Heap (48 MiB)/Heap (40 MiB)/'
rot "Zeile SPEC-040 fehlt" spec/spezifikation.md 's/^| `SPEC-040`/| `SPEC-0400`/'
rot "Zeile der README fehlt" harness/README.md 's/^| `make simulations-integration`/| `make simulations-integration-x`/'

if [ "$fehler" = 0 ]; then echo "integrationszahlen-selbsttest: ok"; fi
exit "$fehler"
