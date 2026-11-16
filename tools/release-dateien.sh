#!/bin/sh
# release-dateien.sh — baut die Release-Dateien aus dem Ergebnis der Stage `build-jar` (TDS-RB-05, TDS-QA-06, ADR-0050
# Entscheidungen 2 und 3; Dockerfile-Stage `release-dateien`, Target `make release-dateien`).
#
# Aufruf: release-dateien.sh <libs-verzeichnis> <lizenzen-verzeichnis> <LICENSE-datei> <build.gradle.kts> <ausgabe>
# (Stage: build/libs, lizenzen/lib, LICENSE, build.gradle.kts, build/release-dateien). Die Version steht in build.gradle.kts als
# Zeile `version = "..."` (so liest auch tools/publish.sh sie); sie hat die Form <zahl>.<zahl>.<zahl> und endet nicht auf
# -SNAPSHOT. Die Ausgabe fuehrt tdswire-<version>.zip (tdswire.jar, lib/, LICENSE, lizenzen/lib/ mit den Lizenz- und
# Hinweisdateien), das einzelne tdswire.jar und SHA256SUMS (Format sha256sum, beide Dateien). Das ZIP entsteht mit dem
# JDK-Werkzeug `jar` (ohne Manifest); die Stage hat kein `zip`, und ein Nachladen braeche Docker-only.
# Erst nach gruener tools/release-pruefung.sh am entpackten ZIP kommen die Dateien in <ausgabe>; bei einem Befund entsteht dort
# nichts. <ausgabe> darf nicht oder nur leer bestehen. Exit 1 bei einem Befund, 2 bei falschem Aufruf.
# Grenze: ohne Netz, ohne Token, nichts nach aussen; die Bytes des ZIP sind nicht reproduzierbar (Zeitstempel der Eintraege),
# zugesagt sind die Pruefsummen der Dateien dieses Laufs.
set -eu

[ "$#" -eq 5 ] || { echo "Aufruf: $0 <libs-verzeichnis> <lizenzen-verzeichnis> <LICENSE-datei> <build.gradle.kts> <ausgabe>" >&2; exit 2; }
libs="$1"; lizenzen="$2"; lizenz="$3"; gradle="$4"; aus="$5"
skript=$(dirname "$0")
fehler() { echo "release-dateien: FEHLER - $1" >&2; exit 1; }

[ -f "$gradle" ] || fehler "$gradle liegt nicht"
version=$(sed -n 's/^version = "\(.*\)"$/\1/p' "$gradle")
[ -n "$version" ] || fehler "$gradle nennt keine Version"
case "$version" in
  *-SNAPSHOT) fehler "die Version $version endet auf -SNAPSHOT; Release-Dateien gibt es nur fuer eine Release-Version" ;;
esac
printf '%s\n' "$version" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$' \
  || fehler "die Version $version hat nicht die Form <zahl>.<zahl>.<zahl>"

if [ -e "$aus" ]; then
  [ -d "$aus" ] && [ -z "$(ls -A "$aus")" ] || fehler "$aus besteht und ist nicht leer"
fi
[ -f "$libs/tdswire.jar" ] || fehler "$libs/tdswire.jar liegt nicht"
[ -d "$libs/lib" ] || fehler "$libs/lib liegt nicht"
[ -d "$lizenzen" ] || fehler "$lizenzen liegt nicht"
[ -f "$lizenz" ] || fehler "$lizenz liegt nicht"

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
archiv="$tmp/archiv"
fertig="$tmp/fertig"
mkdir -p "$archiv/lizenzen" "$fertig"
cp "$libs/tdswire.jar" "$archiv/tdswire.jar"
cp -R "$libs/lib" "$archiv/lib"
cp "$lizenz" "$archiv/LICENSE"
cp -R "$lizenzen" "$archiv/lizenzen/lib"

zip="tdswire-$version.zip"
jar --create --no-manifest --file "$fertig/$zip" -C "$archiv" .
cp "$libs/tdswire.jar" "$fertig/tdswire.jar"
(cd "$fertig" && sha256sum "$zip" tdswire.jar > SHA256SUMS)

sh "$skript/release-pruefung.sh" "$fertig" "$lizenz" "$version"

mkdir -p "$aus"
cp "$fertig/$zip" "$fertig/tdswire.jar" "$fertig/SHA256SUMS" "$aus/"
echo "release-dateien: Version $version, $(ls "$aus" | wc -l) Dateien in $aus"
