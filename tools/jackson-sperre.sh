#!/bin/sh
# jackson-sperre.sh — Sperre gegen Jackson in der Bibliothek (ADR-0025 Entscheidung 2, TDS-RB-01).
#
# Aufruf: jackson-sperre.sh <verzeichnis>...   Exit 1 bei einem Treffer, 2 bei falschem Aufruf.
# Jeder Verweis auf `com.fasterxml` in *.kt und *.java unter den Verzeichnissen ist ein Treffer: ein Import,
# ein voll qualifizierter Name, auch in einem Kommentar. Die Bibliothek liest keine Datei und kein YAML; Jackson
# gehoert dem Testserver (Source-Set `testserver`) und steht nicht im Klassenpfad der Bibliothek. Eine neue Sperre ist
# keine Lockerung (AGENTS.md 3.6). Darum steht die Pruefung vor detekt in der Dockerfile-Stage `detekt`, sie nennt
# `src/main`. Warum kein detekt-Eintrag: die Importregeln in config/detekt/detekt.yml gelten je Package der Schichten
# (der Selbsttest vergleicht ihre Packages mit den Testdaten), und `runtime` hat keine Regel. Grenze: geprueft ist
# Text; Reflection auf den Namen aus einer Zeichenkette, die ihn zusammensetzt, bleibt unerkannt.
set -eu

[ "$#" -ge 1 ] || { echo "Aufruf: $0 <verzeichnis>..." >&2; exit 2; }

treffer=$(grep -rn 'com\.fasterxml' --include='*.kt' --include='*.java' "$@" || true)

if [ -n "$treffer" ]; then
  echo "jackson-sperre: FEHLER - Jackson gehoert dem Testserver, nicht der Bibliothek (ADR-0025):" >&2
  echo "$treffer" >&2
  exit 1
fi
echo "jackson-sperre: OK - kein Verweis auf com.fasterxml unter $*"
