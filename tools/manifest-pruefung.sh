#!/bin/sh
# manifest-pruefung.sh — Manifest des Bibliotheks-Jars und POM des Maven-Pakets ohne Container pruefen (TDS-RB-01,
# TDS-RB-05, TDS-QA-06, ADR-0025, ADR-0047).
#
# Aufruf: manifest-pruefung.sh <libs-verzeichnis> <pom-datei>   (Dockerfile, Stage `build-jar`: build/libs und
# build/publications/maven/pom-default.xml)
# Exit 1 bei einem Befund, 2 bei falschem Aufruf. Geprueft wird: das Jar heisst tdswire.jar, sein
# Manifest nennt keine Main-Class (die Bibliothek ist das Produkt, es gibt keine Startklasse), das Jar fuehrt keine
# Klasse des Testservers, und der Class-Path ist nicht leer und besteht nur aus Eintraegen `lib/<name>` ohne `..`, zu
# denen eine Datei in <libs-verzeichnis>/lib liegt; unter lib/ liegt kein Jackson (ADR-0025 Entscheidung 2).
# Die POM ist vorhanden, nennt kein Jackson und keinen Testserver (weder als Abhaengigkeit noch sonst im Text) und fuehrt
# im Block licenses eine Lizenz mit Namen MIT und einer http(s)-URL (ADR-0047 Entscheidung 2).
# Grenze: geprueft wird das Manifest und die Namen im Jar und in lib/, nicht der Start und nicht der Inhalt der
# Dateien; andere Bibliotheken der Testserver-Seite als Jackson erkennt die Pruefung nicht; die POM wird als Text gelesen
# (Namen und Zeilen, kein XML-Parser), geprueft wird nicht, ob ihre Abhaengigkeiten mit dem Class-Path uebereinstimmen.
set -eu

[ "$#" -eq 2 ] || { echo "Aufruf: $0 <libs-verzeichnis> <pom-datei>" >&2; exit 2; }
libs="$1"
pom="$2"
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

# POM: Text lesen, eine Zeile je Element (Gradle schreibt sie so).
if [ ! -f "$pom" ]; then
  melde "die POM $pom liegt nicht"
else
  if grep -qi 'jackson' "$pom"; then melde "die POM nennt Jackson"; fi
  if grep -qi 'testserver' "$pom"; then melde "die POM nennt den Testserver"; fi
  lizenzen=$(awk '/<licenses>/ { drin = 1 } drin { print } /<\/licenses>/ { drin = 0 }' "$pom")
  printf '%s\n' "$lizenzen" | grep -Eq '<name>MIT( License)?</name>' || melde "die POM nennt keine Lizenz MIT im Block licenses"
  printf '%s\n' "$lizenzen" | grep -Eq '<url>https?://[^<]+</url>' || melde "die Lizenz in der POM nennt keine URL"
fi

[ "$fehler" -eq 0 ] || exit 1
echo "manifest-pruefung: ok (keine Main-Class, kein Testserver, kein Jackson, $(printf '%s\n' "$cp_zeile" | wc -w) Class-Path-Eintraege in lib/, POM ohne Jackson und Testserver mit Lizenz MIT)"
