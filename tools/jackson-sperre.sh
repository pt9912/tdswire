#!/bin/sh
# jackson-sperre.sh — Sperre gegen Jackson in der Bibliothek (ADR-0025 Entscheidung 2, TDS-RB-01).
#
# Aufruf: jackson-sperre.sh <verzeichnis>...   Exit 1 bei einem Treffer, 2 bei falschem Aufruf.
# Jeder Verweis auf `com.fasterxml` in *.kt und *.java unter den Verzeichnissen ist ein Treffer: ein Import,
# ein voll qualifizierter Name, auch in einem Kommentar. Die Bibliothek liest keine Datei und kein YAML; Jackson
# gehoert dem Testserver (Gradle-Modul `testserver`) und steht nicht im Klassenpfad der Bibliothek. Die Pruefung laeuft
# vor detekt in der Dockerfile-Stage `detekt` und nennt `src/main`; sie ist eine Textpruefung, weil die Importregeln in
# config/detekt/detekt.yml je Package der Schichten gelten (ihr Selbsttest vergleicht die Packages mit den Testdaten)
# und `runtime` keine Regel hat.
# Grenzen: geprueft ist der Text `com.fasterxml` in Kotlin und Java. Nicht erkannt sind andere Bibliotheken der Datei
# (`org.yaml.snakeyaml`, `com.google.gson`), eine Abhaengigkeitszeile in `*.gradle.kts` und ein Name, den Reflection aus
# Zeichenketten zusammensetzt. Den Rueckfall in `implementation` erkennt das Lock von Gradle (`Resolved ... is not part
# of the dependency lock state`), solange niemand `make deps-lock` neu schreibt; danach zeigt der Diff der `gradle.lockfile`
# Jackson in `compileClasspath`.
set -eu

[ "$#" -ge 1 ] || { echo "Aufruf: $0 <verzeichnis>..." >&2; exit 2; }

treffer=$(grep -rn 'com\.fasterxml' --include='*.kt' --include='*.java' "$@" || true)

if [ -n "$treffer" ]; then
  echo "jackson-sperre: FEHLER - Jackson gehoert dem Testserver, nicht der Bibliothek (ADR-0025):" >&2
  echo "$treffer" >&2
  exit 1
fi
echo "jackson-sperre: OK - kein Verweis auf com.fasterxml unter $*"
