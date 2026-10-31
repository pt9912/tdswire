#!/usr/bin/env bash
# tools/integrationszahlen-pruefung.sh [wurzel] - haelt die Zahlen des Integrationslaufs (SPEC-040, `make
# simulations-integration`) an allen Orten gleich (TDS-FA-11, TDS-QA-06): Heap (`maxHeapSize` der Task
# `integrationsServer` in build.gradle.kts), Zeilen und Zeichen von `gross` (BIG_ROWS, BIG_TEXT_CHARS in
# IntegrationServer.kt und die Konstanten GrosseZeilen, GrosseZeichen im .NET-Test), die Zeile SPEC-040 der
# Spezifikation (Heap und Zeichenzahl) und die Zeile des Ziels in harness/README.md (Heap). [wurzel] ist das
# Repo-Verzeichnis (Vorgabe: .). Ausgang 0 nur, wenn alle Orte stimmen; jeder Befund steht auf stderr.
# Grenze: gelesen wird der Text der Dateien, nicht ein Lauf; den Heap in tools/simulationstest.sh liest der Lauf
# selbst aus dem Log des Servers. Aufgerufen von der Dockerfile-Stage `kopplung-zahlen`.
set -euo pipefail
w="${1:-.}"
fehler=0
befund() { echo "integrationszahlen: FEHLER - $1" >&2; fehler=1; }

gradle="$w/build.gradle.kts"
server="$w/src/test/kotlin/io/github/pt9912/tdswire/runtime/IntegrationServer.kt"
dotnet="$w/referenztest-dotnet/Integration/SimulationBackendTests.cs"
spec="$w/spec/spezifikation.md"
readme="$w/harness/README.md"
for d in "$gradle" "$server" "$dotnet" "$spec" "$readme"; do
  [ -f "$d" ] || { echo "integrationszahlen: FEHLER - Datei fehlt: $d" >&2; exit 1; }
done

heap=$(sed -n 's/.*maxHeapSize *= *"\([0-9][0-9]*\)m".*/\1/p' "$gradle")
[ "$(printf '%s\n' "$heap" | grep -c .)" = 1 ] || { echo "integrationszahlen: FEHLER - maxHeapSize in build.gradle.kts nicht genau einmal" >&2; exit 1; }
zeilen=$(sed -n 's/^internal const val BIG_ROWS = \([0-9_]*\)$/\1/p' "$server" | tr -d _)
zeichen=$(sed -n 's/^internal const val BIG_TEXT_CHARS = \([0-9_]*\)$/\1/p' "$server" | tr -d _)
[ -n "$zeilen" ] && [ -n "$zeichen" ] || { echo "integrationszahlen: FEHLER - BIG_ROWS oder BIG_TEXT_CHARS fehlt in IntegrationServer.kt" >&2; exit 1; }

cs_zeilen=$(sed -n 's/^ *private const int GrosseZeilen = \([0-9_]*\);.*/\1/p' "$dotnet" | tr -d _)
cs_zeichen=$(sed -n 's/^ *private const int GrosseZeichen = \([0-9_]*\);.*/\1/p' "$dotnet" | tr -d _)
[ "$cs_zeilen" = "$zeilen" ] || befund "GrosseZeilen ($cs_zeilen) weicht von BIG_ROWS ($zeilen) ab"
[ "$cs_zeichen" = "$zeichen" ] || befund "GrosseZeichen ($cs_zeichen) weicht von BIG_TEXT_CHARS ($zeichen) ab"

gesamt=$((zeilen * zeichen))
# Tausendergruppen mit Leerzeichen, wie die Spezifikation sie schreibt (64 000 000).
text=$(printf '%s' "$gesamt" | rev | sed 's/\([0-9]\{3\}\)/\1 /g; s/ $//' | rev)

zeile_spec=$(grep -c '^| `SPEC-040`' "$spec" || true)
[ "$zeile_spec" = 1 ] || befund "Zeile SPEC-040 in der Spezifikation nicht genau einmal ($zeile_spec)"
grep '^| `SPEC-040`' "$spec" | grep -qF "Serverprozess mit $heap MiB Heap, Text von $text Zeichen" \
  || befund "SPEC-040 nennt nicht 'Serverprozess mit $heap MiB Heap, Text von $text Zeichen'"

zeile_readme=$(grep -c '^| `make simulations-integration`' "$readme" || true)
[ "$zeile_readme" = 1 ] || befund "Zeile von make simulations-integration in harness/README.md nicht genau einmal ($zeile_readme)"
grep '^| `make simulations-integration`' "$readme" | grep -qF "über dem Heap ($heap MiB)" \
  || befund "harness/README.md nennt nicht 'über dem Heap ($heap MiB)'"

if [ "$fehler" = 0 ]; then echo "integrationszahlen: ok (Heap $heap MiB, $zeilen Zeilen zu $zeichen Zeichen = $text)"; fi
exit "$fehler"
