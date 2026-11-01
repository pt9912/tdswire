#!/bin/sh
# manifest-selbsttest.sh — belegt an synthetischen Jars, dass tools/manifest-pruefung.sh jeden seiner Faelle
# abweist (Jar-Name, vorhandene Main-Class, Klasse des Testservers im Jar, Jackson in lib/ (auch gross geschrieben), Class-Path leer, fehlend,
# Eintrag ohne Datei, Eintrag ohne Form lib/<name>, Eintrag mit `..`) und ein gutes Jar ohne Main-Class annimmt
# (TDS-RB-01, TDS-QA-06, ADR-0022, ADR-0025). Er belegt auch, dass die
# Stage `build-jar` des Dockerfile die Pruefung aufruft: ohne diese Zeile wuerde der Bau sie nie ausfuehren.
# Grenze: der Aufruf im echten Bau ist die Textzeile, nicht ein Lauf gegen das echte Jar.
#
# Aufgerufen von der Dockerfile-Stage `skript-selbsttest` (Dockerfile liegt dort neben tools/); braucht das
# JDK-Werkzeug `jar`, sh, mktemp, grep, awk.
set -eu

pruefung=$(dirname "$0")/manifest-pruefung.sh
dockerfile=$(dirname "$0")/../Dockerfile
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

fehler=0
pruefe() { echo "manifest-selbsttest: FEHLER - $1" >&2; fehler=1; }

# bau <name> <jar-name> <manifest-zeilen> <lib-dateien...>: legt $tmp/<name>/<jar-name> und $tmp/<name>/lib/<datei> an.
bau() {
  name="$1"; jarname="$2"; zeilen="$3"; shift 3
  mkdir -p "$tmp/$name/lib" "$tmp/inhalt"
  rm -rf "$tmp/inhalt"; mkdir -p "$tmp/inhalt"
  : > "$tmp/inhalt/leer.txt"
  if [ -n "${EXTRA:-}" ]; then mkdir -p "$tmp/inhalt/$(dirname "$EXTRA")"; : > "$tmp/inhalt/$EXTRA"; fi
  for datei in "$@"; do : > "$tmp/$name/lib/$datei"; done
  printf '%s\n' "$zeilen" > "$tmp/$name.mf"
  jar --create --file "$tmp/$name/$jarname" --manifest "$tmp/$name.mf" -C "$tmp/inhalt" . > /dev/null
}

# erwarte <name> <exit> <jar-name> <manifest-zeilen> <lib-dateien...>
erwarte() {
  name="$1"; soll="$2"; jarname="$3"; zeilen="$4"; shift 4
  bau "$name" "$jarname" "$zeilen" "$@"
  ist=0
  sh "$pruefung" "$tmp/$name" > "$tmp/$name.aus" 2>&1 || ist=$?
  [ "$ist" -eq "$soll" ] || pruefe "$name: Exit $ist statt $soll ($(tr '\n' ' ' < "$tmp/$name.aus"))"
}

main="Main-Class: beispiel.Anwendung"

erwarte gut 0 tdswire.jar "Class-Path: lib/a.jar lib/b.jar" a.jar b.jar
erwarte main-vorhanden 1 tdswire.jar "$main
Class-Path: lib/a.jar" a.jar
erwarte main-anderer-name 1 tdswire.jar "Main-Class: x.MainKt
Class-Path: lib/a.jar" a.jar
erwarte main-leer 1 tdswire.jar "Main-Class: 
Class-Path: lib/a.jar" a.jar
erwarte main-klein 1 tdswire.jar "main-class: x.M
Class-Path: lib/a.jar" a.jar
EXTRA=io/github/pt9912/tdswire/testserver/MainKt.class
erwarte testserver-im-jar 1 tdswire.jar "Class-Path: lib/a.jar" a.jar
EXTRA=
erwarte jackson-in-lib 1 tdswire.jar "Class-Path: lib/a.jar lib/jackson-core-2.17.0.jar" a.jar jackson-core-2.17.0.jar
erwarte jackson-gross 1 tdswire.jar "Class-Path: lib/a.jar lib/Jackson-core-2.17.0.jar" a.jar Jackson-core-2.17.0.jar
erwarte jar-name 1 anders.jar "Class-Path: lib/a.jar" a.jar
erwarte cp-fehlt 1 tdswire.jar "Created-By: test" a.jar
erwarte cp-leer 1 tdswire.jar "Class-Path: " a.jar
erwarte cp-ohne-datei 1 tdswire.jar "Class-Path: lib/a.jar lib/fehlt.jar" a.jar
erwarte cp-falsche-form 1 tdswire.jar "Class-Path: /abs/a.jar" a.jar
erwarte cp-punkt-punkt 1 tdswire.jar "Class-Path: lib/../tdswire.jar" a.jar

# Ohne Argument: Exit 2.
ist=0
sh "$pruefung" > /dev/null 2>&1 || ist=$?
[ "$ist" -eq 2 ] || pruefe "ohne Argument: Exit $ist statt 2"

# Die Stage build-jar ruft die Pruefung auf.
aufruf=$(awk '/^FROM /{stage=$0} stage ~ / AS build-jar$/ && /^RUN sh tools\/manifest-pruefung\.sh build\/libs$/ {n++} END {print n+0}' "$dockerfile")
[ "$aufruf" -eq 1 ] || pruefe "das Dockerfile ruft die Pruefung in der Stage build-jar $aufruf mal auf statt einmal"

[ "$fehler" -eq 0 ] || exit 1
echo "manifest-selbsttest: ok (ein gutes Jar ohne Main-Class, dreizehn Fehlerfaelle, Aufruf ohne Argument, Aufruf im Dockerfile)"
