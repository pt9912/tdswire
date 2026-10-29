#!/bin/sh
# suppression-check.sh — Suppression-Verbot (AGENTS.md 3.2) fuer Kotlin-Quelltext.
#
# Aufruf: suppression-check.sh <verzeichnis>...   Exit 1 bei einem Treffer, 2 bei falschem Aufruf.
# Jedes @Suppress und @SuppressWarnings in *.kt und *.kts unter den Verzeichnissen ist ein Treffer, auch in
# einem Kommentar: mit Ziel (@file:, @get:, @field:, ...), qualifiziert (@kotlin.Suppress) und in einer
# Annotationsliste derselben Zeile (@[Suppress(...)]). detekt kann das nicht selbst melden: eine
# Unterdrueckung nimmt den Befund aus seinem Bericht. Darum ist das eine Textpruefung vor detekt
# (Dockerfile, Stage `detekt`). Grenzen: geprueft ist nur, was unter den Verzeichnissen liegt, die der
# Aufrufer nennt - die Stage nennt src; eine Annotationsliste, die ueber mehrere Zeilen geht, wird
# nicht erkannt. `\b` ist eine GNU-Erweiterung von grep (im gepinnten Image GNU grep).
set -eu

[ "$#" -ge 1 ] || { echo "Aufruf: $0 <verzeichnis>..." >&2; exit 2; }

treffer=$(grep -rnE '@([a-z]+:)?(\[([^]]*[[:space:]])?)?([a-z]+\.)*Suppress(Warnings)?\b' --include='*.kt' --include='*.kts' "$@" || true)

if [ -n "$treffer" ]; then
  echo "suppression-check: FEHLER - Unterdrueckungen sind verboten (AGENTS.md 3.2), der Befund wird im Code behoben:" >&2
  echo "$treffer" >&2
  exit 1
fi
echo "suppression-check: OK - keine Unterdrueckung unter $*"
