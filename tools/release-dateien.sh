#!/bin/sh
# release-dateien.sh — baut die Release-Dateien aus dem Ergebnis der Stage `build-jar` (TDS-RB-05, TDS-QA-06, ADR-0050
# Entscheidungen 2 und 3; Dockerfile-Stage `release-dateien`, Target `make release-dateien`).
#
# Aufruf: release-dateien.sh [--vorschau] <libs-verzeichnis> <lizenzen-verzeichnis> <LICENSE-datei> <build.gradle.kts> <ausgabe>
#         release-dateien.sh [--vorschau] --nur-version <build.gradle.kts>   (nur die Versionspruefung, ohne Docker; das
# Make-Target ruft sie vor `docker build`)
# (Stage: build/libs, lizenzen/lib, LICENSE, build.gradle.kts, build/release-dateien). Die Version steht in build.gradle.kts als
# genau eine Zeile `version = "..."` (so liest auch tools/publish.sh sie); sie hat die Form <zahl>.<zahl>.<zahl> und endet nicht
# auf -SNAPSHOT. Die Ausgabe fuehrt tdswire-<version>.zip (tdswire.jar, lib/, LICENSE, lizenzen/lib/ mit den Lizenz- und
# Hinweisdateien), das einzelne tdswire.jar und SHA256SUMS (Format sha256sum, beide Dateien). Das ZIP entsteht mit dem
# JDK-Werkzeug `jar` (ohne Manifest); die Stage hat kein `zip`, und ein Nachladen braeche Docker-only.
# --vorschau (ADR-0050 Entscheidung 6, Nutzerentscheidung 2026-11-16): erlaubt auch eine Version <zahl>.<zahl>.<zahl>-SNAPSHOT und
# nennt das ZIP immer tdswire-<version>-vorschau.zip, auch bei einer Release-Version; alle Pruefungen am entpackten ZIP bleiben.
# Nur der Dispatch-Lauf der CI nutzt den Modus, ein Release nie.
# Die Lizenz- und Hinweisdateien unter lizenzen/lib/ sind ein Vorschlag des Implementers; die offenen Fragen 2a bis 2c des Belegs
# docs/messungen/lizenzen-lib-2026-11-16.md sind unbeantwortet, der Nutzer entscheidet vor dem Messslice.
# Erst nach gruener tools/release-pruefung.sh am entpackten ZIP kommen die Dateien in <ausgabe>; bei einem Befund entsteht dort
# nichts. <ausgabe> darf nicht oder nur leer bestehen. Exit 1 bei einem Befund, 2 bei falschem Aufruf.
# Grenze: ohne Netz, ohne Token, nichts nach aussen; ob zwei Laeufe dasselbe ZIP liefern, ist nicht zugesagt und nicht gemessen.
set -eu

aufruf="Aufruf: $0 [--vorschau] <libs-verzeichnis> <lizenzen-verzeichnis> <LICENSE-datei> <build.gradle.kts> <ausgabe> | [--vorschau] --nur-version <build.gradle.kts>"
vorschau=0
nur_version=0
while [ "$#" -gt 0 ]; do
  case "$1" in
    --vorschau) vorschau=1; shift ;;
    --nur-version) nur_version=1; shift ;;
    *) break ;;
  esac
done
if [ "$nur_version" -eq 1 ]; then
  [ "$#" -eq 1 ] || { echo "$aufruf" >&2; exit 2; }
  gradle="$1"
else
  [ "$#" -eq 5 ] || { echo "$aufruf" >&2; exit 2; }
  libs="$1"; lizenzen="$2"; lizenz="$3"; gradle="$4"; aus="$5"
fi
skript=$(dirname "$0")
fehler() { echo "release-dateien: FEHLER - $1" >&2; exit 1; }

[ -f "$gradle" ] || fehler "$gradle liegt nicht"
versionen=$(sed -n 's/^version = "\(.*\)"$/\1/p' "$gradle")
[ -n "$versionen" ] || fehler "$gradle nennt keine Version"
[ "$(printf '%s\n' "$versionen" | wc -l)" -eq 1 ] || fehler "$gradle nennt mehr als eine Zeile version"
version="$versionen"
if [ "$vorschau" -eq 1 ]; then
  printf '%s\n' "$version" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+(-SNAPSHOT)?$' \
    || fehler "die Version $version hat nicht die Form <zahl>.<zahl>.<zahl> oder <zahl>.<zahl>.<zahl>-SNAPSHOT"
  marke=-vorschau
else
  case "$version" in
    *-SNAPSHOT) fehler "die Version $version endet auf -SNAPSHOT; Release-Dateien gibt es nur fuer eine Release-Version (Vorschau: --vorschau)" ;;
  esac
  printf '%s\n' "$version" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$' \
    || fehler "die Version $version hat nicht die Form <zahl>.<zahl>.<zahl>"
  marke=
fi
if [ "$nur_version" -eq 1 ]; then
  echo "release-dateien: Version $version ok${marke:+ (Vorschau)}"
  exit 0
fi

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

zip="tdswire-$version$marke.zip"
jar --create --no-manifest --file "$fertig/$zip" -C "$archiv" .
cp "$libs/tdswire.jar" "$fertig/tdswire.jar"
(cd "$fertig" && sha256sum "$zip" tdswire.jar > SHA256SUMS)

sh "$skript/release-pruefung.sh" ${marke:+--vorschau} "$fertig" "$lizenz" "$version"

mkdir -p "$aus"
cp "$fertig/$zip" "$fertig/tdswire.jar" "$fertig/SHA256SUMS" "$aus/"
echo "release-dateien: Version $version${marke:+ (VORSCHAU, kein Release)}, $(ls "$aus" | wc -l) Dateien in $aus"
