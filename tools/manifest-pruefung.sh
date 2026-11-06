#!/bin/sh
# manifest-pruefung.sh — Manifest des Bibliotheks-Jars ohne Container pruefen (TDS-RB-01, TDS-QA-06, ADR-0025).
#
# Aufruf: manifest-pruefung.sh <libs-verzeichnis>   (Dockerfile, Stage `build-jar`: build/libs)
# Exit 1 bei einem Befund, 2 bei falschem Aufruf. Geprueft wird: das Jar heisst tdswire.jar, sein
# Manifest nennt keine Main-Class (die Bibliothek ist das Produkt, es gibt keine Startklasse), das Jar fuehrt keine
# Klasse des Testservers, und der Class-Path ist nicht leer und besteht nur aus Eintraegen `lib/<name>` ohne `..`, zu
# denen eine Datei in <libs-verzeichnis>/lib liegt; unter lib/ liegt kein Jackson (ADR-0025 Entscheidung 2).
# Grenze: geprueft wird das Manifest und die Namen im Jar und in lib/, nicht der Start und nicht der Inhalt der
# Dateien; andere Bibliotheken der Testserver-Seite als Jackson erkennt die Pruefung nicht.
set -eu

[ "$#" -eq 1 ] || { echo "Aufruf: $0 <libs-verzeichnis>" >&2; exit 2; }
libs="$1"
jar="$libs/tdswire.jar"

fehler=0
melde() { echo "manifest-pruefung: FEHLER - $1" >&2; fehler=1; }

[ -f "$jar" ] || { echo "manifest-pruefung: FEHLER - $jar liegt nicht (erwartet wird der Jar-Name tdswire.jar)" >&2; exit 1; }

# Manifest-Zeilen sind auf 72 Bytes umbrochen; eine Fortsetzung beginnt mit einem Leerzeichen.
manifest=$(unzip -p "$jar" META-INF/MANIFEST.MF | tr -d '\r' | awk '/^ / { sub(/^ /, ""); buf = buf $0; next } { if (NR > 1) print buf; buf = $0 } END { print buf }')

# Main-Class ohne Beachtung der Schreibweise des Schluessels: auch ein leerer Eintrag ist ein Befund.
if printf '%s\n' "$manifest" | grep -qi '^Main-Class:'; then
  melde "Manifest nennt eine Main-Class ($(printf '%s\n' "$manifest" | sed -n 's/^[Mm]ain-[Cc]lass: *//p')); die Bibliothek hat keine Startklasse"
fi

# Das Gradle-Modul `testserver` liegt nicht im Jar der Bibliothek.
if unzip -Z1 "$jar" | grep -q '^io/github/pt9912/tdswire/testserver/'; then
  melde "das Jar fuehrt Klassen des Testservers (io/github/pt9912/tdswire/testserver/)"
fi
if [ -d "$libs/lib" ] && ls "$libs/lib" | grep -qi '^jackson'; then
  melde "lib/ fuehrt Jackson: $(ls "$libs/lib" | grep -i '^jackson' | tr '\n' ' ')"
fi

cp_zeile=$(printf '%s\n' "$manifest" | sed -n 's/^Class-Path: //p')
[ -n "$cp_zeile" ] || melde "Class-Path fehlt oder ist leer"
for eintrag in $cp_zeile; do
  case "$eintrag" in
    *..*) melde "Class-Path-Eintrag [$eintrag] enthaelt .." ;;
    lib/?*) [ -f "$libs/$eintrag" ] || melde "Class-Path-Eintrag [$eintrag] hat keine Datei in $libs/lib" ;;
    *) melde "Class-Path-Eintrag [$eintrag] hat nicht die Form lib/<name>" ;;
  esac
done

[ "$fehler" -eq 0 ] || exit 1
echo "manifest-pruefung: ok (keine Main-Class, kein Testserver, kein Jackson, $(printf '%s\n' "$cp_zeile" | wc -w) Class-Path-Eintraege in lib/)"
