#!/bin/sh
# manifest-pruefung.sh — Manifest des Laufzeit-Jars ohne Container pruefen (TDS-RB-01, TDS-QA-06).
#
# Aufruf: manifest-pruefung.sh <libs-verzeichnis>   (Dockerfile, Stage `build-jar`: build/libs)
# Exit 1 bei einem Befund, 2 bei falschem Aufruf. Geprueft wird: das Jar heisst tdswire.jar, sein
# Manifest nennt als Main-Class die Startklasse, und der Class-Path ist nicht leer und besteht nur aus
# Eintraegen `lib/<name>`, zu denen eine Datei in <libs-verzeichnis>/lib liegt. Grenze: geprueft wird das
# Manifest, nicht der Start; Entrypoint und Basis-Image der Stage `runtime` pruefen allein die
# E2E-Stufen (make laufzeittest), und ein vorhandener Eintrag sagt nichts ueber den Inhalt der Datei.
set -eu

[ "$#" -eq 1 ] || { echo "Aufruf: $0 <libs-verzeichnis>" >&2; exit 2; }
libs="$1"
jar="$libs/tdswire.jar"
startklasse="io.github.pt9912.tdswire.runtime.MainKt"

fehler=0
melde() { echo "manifest-pruefung: FEHLER - $1" >&2; fehler=1; }

[ -f "$jar" ] || { echo "manifest-pruefung: FEHLER - $jar liegt nicht (erwartet wird der Jar-Name tdswire.jar)" >&2; exit 1; }

# Manifest-Zeilen sind auf 72 Bytes umbrochen; eine Fortsetzung beginnt mit einem Leerzeichen.
manifest=$(unzip -p "$jar" META-INF/MANIFEST.MF | tr -d '\r' | awk '/^ / { sub(/^ /, ""); buf = buf $0; next } { if (NR > 1) print buf; buf = $0 } END { print buf }')

main=$(printf '%s\n' "$manifest" | sed -n 's/^Main-Class: //p')
[ "$main" = "$startklasse" ] || melde "Main-Class ist [$main], erwartet [$startklasse]"

cp_zeile=$(printf '%s\n' "$manifest" | sed -n 's/^Class-Path: //p')
[ -n "$cp_zeile" ] || melde "Class-Path fehlt oder ist leer"
for eintrag in $cp_zeile; do
  case "$eintrag" in
    lib/?*) [ -f "$libs/$eintrag" ] || melde "Class-Path-Eintrag [$eintrag] hat keine Datei in $libs/lib" ;;
    *) melde "Class-Path-Eintrag [$eintrag] hat nicht die Form lib/<name>" ;;
  esac
done

[ "$fehler" -eq 0 ] || exit 1
echo "manifest-pruefung: ok (Main-Class, $(printf '%s\n' "$cp_zeile" | wc -w) Class-Path-Eintraege in lib/)"
