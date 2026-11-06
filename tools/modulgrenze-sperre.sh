#!/bin/sh
# modulgrenze-sperre.sh - Sperre gegen das Oeffnen der Modulgrenze des Testservers (ADR-0034 Entscheidung 3, TDS-RB-01).
#
# Aufruf: modulgrenze-sperre.sh <datei>...   Exit 1 bei einem Treffer, 2 bei falschem Aufruf oder fehlender Datei.
# Jede Zeile, die `friend` oder `associate` enthaelt (ohne Beachtung der Gross-/Kleinschreibung), ist ein Treffer, auch in
# einem Kommentar: `friendPaths`, `-Xfriend-paths`, `associateWith`, `associateCompilations`. Mit einem solchen Eintrag
# uebersetzt das Modul `internal`-Namen der Bibliothek, und die Uebersetzung des Moduls belegt "nur oeffentliche Flaeche"
# nicht mehr. Aufgerufen in der Dockerfile-Stage `detekt` mit testserver/build.gradle.kts; Selbsttest:
# tools/detekt-selbsttest.sh (Modus modulgrenze).
# Grenzen: geprueft ist der Text der genannten Dateien. Nicht erkannt sind ein Eintrag in einer anderen Datei (Wurzel-
# Build-Datei, Init-Skript, `gradle.properties`), ein Name, den die Build-Datei aus Zeichenketten zusammensetzt, und ein
# anderer Weg, `internal` zu oeffnen. Die Bibliothek selbst prueft `integratorOhneAssoziation` in build.gradle.kts.
set -eu

[ "$#" -ge 1 ] || { echo "Aufruf: $0 <datei>..." >&2; exit 2; }
for d in "$@"; do
  [ -f "$d" ] || { echo "modulgrenze-sperre: FEHLER - Datei fehlt: $d" >&2; exit 2; }
done

treffer=$(grep -n -i -e 'friend' -e 'associate' "$@" || true)

if [ -n "$treffer" ]; then
  echo "modulgrenze-sperre: FEHLER - die Modulgrenze des Testservers bleibt zu (ADR-0034 Entscheidung 3):" >&2
  echo "$treffer" >&2
  exit 1
fi
echo "modulgrenze-sperre: OK - kein friend- und kein associate-Eintrag in $*"
