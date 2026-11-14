#!/bin/sh
# manifest-selbsttest.sh — belegt an synthetischen Jars, dass tools/manifest-pruefung.sh jeden seiner Faelle
# abweist (Jar-Name, vorhandene Main-Class, Klasse des Testservers im Jar, Jackson in lib/ (auch gross geschrieben), Class-Path leer, fehlend,
# Eintrag ohne Datei, Eintrag ohne Form lib/<name>, Eintrag mit `..`; POM fehlt, nennt Jackson, nennt den Testserver,
# fuehrt keine Lizenz, eine andere Lizenz als MIT oder eine Lizenz ohne URL) und ein gutes Jar mit guter POM annimmt
# (TDS-RB-01, TDS-RB-05, TDS-QA-06, ADR-0022, ADR-0025, ADR-0047). Er belegt auch, dass die
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

# Die gute POM hat die Form, die `generatePomFileForMavenPublication` schreibt; POM=<text> ersetzt sie, POM_FEHLT=1 laesst sie weg.
pom_gut='<?xml version="1.0" encoding="UTF-8"?>
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>io.github.pt9912</groupId>
  <artifactId>tdswire</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <licenses>
    <license>
      <name>MIT License</name>
      <url>https://opensource.org/license/mit</url>
    </license>
  </licenses>
  <dependencies>
    <dependency>
      <groupId>org.jetbrains.kotlinx</groupId>
      <artifactId>kotlinx-coroutines-core-jvm</artifactId>
      <version>1.10.1</version>
      <scope>compile</scope>
    </dependency>
  </dependencies>
</project>'
POM="$pom_gut"
POM_FEHLT=

# erwarte <name> <exit> <jar-name> <manifest-zeilen> <lib-dateien...>
erwarte() {
  name="$1"; soll="$2"; jarname="$3"; zeilen="$4"; shift 4
  bau "$name" "$jarname" "$zeilen" "$@"
  rm -f "$tmp/$name.pom"
  [ -n "$POM_FEHLT" ] || printf '%s\n' "$POM" > "$tmp/$name.pom"
  ist=0
  sh "$pruefung" "$tmp/$name" "$tmp/$name.pom" > "$tmp/$name.aus" 2>&1 || ist=$?
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

# POM-Faelle: das Jar ist gut, jeweils nur die POM nicht.
POM_FEHLT=1
erwarte pom-fehlt 1 tdswire.jar "Class-Path: lib/a.jar" a.jar
POM_FEHLT=
POM=$(printf '%s\n' "$pom_gut" | sed 's|</dependencies>|<dependency><groupId>com.fasterxml.jackson.core</groupId><artifactId>jackson-core</artifactId></dependency></dependencies>|')
erwarte pom-jackson 1 tdswire.jar "Class-Path: lib/a.jar" a.jar
POM=$(printf '%s\n' "$pom_gut" | sed 's|</dependencies>|<dependency><groupId>io.github.pt9912</groupId><artifactId>testserver</artifactId></dependency></dependencies>|')
erwarte pom-testserver 1 tdswire.jar "Class-Path: lib/a.jar" a.jar
POM=$(printf '%s\n' "$pom_gut" | sed '/<licenses>/,/<\/licenses>/d')
erwarte pom-ohne-lizenz 1 tdswire.jar "Class-Path: lib/a.jar" a.jar
POM=$(printf '%s\n' "$pom_gut" | sed 's|MIT License|Apache License 2.0|')
erwarte pom-andere-lizenz 1 tdswire.jar "Class-Path: lib/a.jar" a.jar
POM=$(printf '%s\n' "$pom_gut" | sed '/<url>https:\/\/opensource/d')
erwarte pom-lizenz-ohne-url 1 tdswire.jar "Class-Path: lib/a.jar" a.jar
POM=$(printf '%s\n' "$pom_gut" | sed 's|https://opensource.org|opensource.org|')
erwarte pom-lizenz-url-ohne-schema 1 tdswire.jar "Class-Path: lib/a.jar" a.jar
POM=$(printf '%s\n' "$pom_gut" | sed 's|<licenses>|<!-- MIT --><licenses>|; s|MIT License|BSD|')
erwarte pom-mit-nur-im-kommentar 1 tdswire.jar "Class-Path: lib/a.jar" a.jar
POM="$pom_gut"

# Zweite Fundstelle: Koordinaten, Lizenzname und Lizenz-URL der handkopierten guten POM stehen auch in build.gradle.kts.
bgk=$(dirname "$0")/../build.gradle.kts
if [ -f "$bgk" ]; then
  gruppe=$(sed -n 's/^group = "\(.*\)"$/\1/p' "$bgk")
  lname=$(sed -n 's/.*name\.set("\(MIT[^"]*\)").*/\1/p' "$bgk")
  lurl=$(sed -n 's/.*url\.set("\(https:\/\/opensource[^"]*\)").*/\1/p' "$bgk")
  for wert in "<groupId>$gruppe</groupId>" "<name>$lname</name>" "<url>$lurl</url>"; do
    { [ -n "$gruppe" ] && [ -n "$lname" ] && [ -n "$lurl" ] && printf '%s\n' "$pom_gut" | grep -qF "$wert"; } \
      || pruefe "die gute POM des Selbsttests und build.gradle.kts stimmen nicht ueberein ($wert)"
  done
else
  pruefe "build.gradle.kts liegt nicht neben tools/ (die Stage skript-selbsttest kopiert sie)"
fi

# Ohne Argument und mit nur einem Argument: Exit 2.
ist=0
sh "$pruefung" > /dev/null 2>&1 || ist=$?
[ "$ist" -eq 2 ] || pruefe "ohne Argument: Exit $ist statt 2"
ist=0
sh "$pruefung" "$tmp/gut" > /dev/null 2>&1 || ist=$?
[ "$ist" -eq 2 ] || pruefe "ohne POM-Argument: Exit $ist statt 2"

# Die Stage build-jar ruft die Pruefung auf.
aufruf=$(awk '/^FROM /{stage=$0} stage ~ / AS build-jar$/ && /^RUN sh tools\/manifest-pruefung\.sh build\/libs build\/publications\/maven\/pom-default\.xml$/ {n++} END {print n+0}' "$dockerfile")
[ "$aufruf" -eq 1 ] || pruefe "das Dockerfile ruft die Pruefung in der Stage build-jar $aufruf mal auf statt einmal"

[ "$fehler" -eq 0 ] || exit 1
echo "manifest-selbsttest: ok (ein gutes Jar mit guter POM, dreizehn Fehlerfaelle am Jar, acht an der POM, Aufruf ohne Argument und ohne POM, Aufruf im Dockerfile)"
