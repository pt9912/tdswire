#!/usr/bin/env bash
# tools/eingangs-hash.sh <Pfad>... - SHA-256 ueber Pfad und Inhalt aller Dateien unter den Pfaden (TDS-QA-06).
# Zusage: gleicher Inhalt und gleiche Pfade ergeben denselben Hash, unabhaengig von mtime, Aufrufreihenfolge und
# Verzeichnis-Lesereihenfolge; eine geaenderte, hinzugekommene, geloeschte oder umbenannte Datei aendert ihn.
# bin/ und obj/ (Build-Ausgabe) zaehlen nicht. Aufrufer: tools/mk/referenztest-dotnet.mk; Selbsttest:
# tools/eingangs-hash-selbsttest.sh.
set -euo pipefail
export LC_ALL=C
find "$@" \( -name bin -o -name obj \) -prune -o -type f -print0 \
	| sort -z \
	| while IFS= read -r -d '' f; do
		printf '%s\n' "$f"
		sha256sum < "$f"
	done | sha256sum | awk '{print $1}'
