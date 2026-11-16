#!/bin/sh
# release-selbsttest.sh — belegt, dass tools/release-dateien.sh und tools/release-pruefung.sh jeden Befund am Archiv melden
# und ein gutes Archiv annehmen (TDS-RB-05, TDS-QA-06, ADR-0050 Entscheidungen 2 und 3, Mess-Pflichten 5 und 8, ADR-0024).
#
# Gutfall: ein synthetisches tdswire.jar (Class-Path auf die drei Namen der Bibliotheken, META-INF/LICENSE gleich LICENSE),
# leere lib-Jars und das echte Verzeichnis lizenzen/lib des Repos; der Lauf liefert drei Dateien. Rote Faelle, jeweils mit der
# erwarteten Meldung und ohne Ausgabe-Verzeichnis: Jackson in lib/, Main-Class im Manifest, andere LICENSE im Jar, Class-Path-Eintrag
# ohne Datei, LICENSE.txt entfernt, NOTICE.txt von kotlinx-coroutines entfernt (LICENSE.txt bleibt), Datei unter third_party
# entfernt, NOTICE.txt leer, neue Bibliothek ohne Verzeichnis unter lizenzen/lib, Version -SNAPSHOT, Version ohne Form, fehlende
# Versionszeile, nicht leere Ausgabe. Am fertigen Archiv (Verzeichnis kopiert, ZIP entpackt, veraendert, neu gepackt, Summen neu
# gerechnet, dann tools/release-pruefung.sh): Jackson in lib/, NOTICE entfernt, andere LICENSE im ZIP, einzelnes Jar anders als
# das Jar im ZIP, unlesbares ZIP; ohne neu gerechnete Summen: SHA256SUMS passt nicht zu einer Datei, eine Zeile fehlt, eine
# zusaetzliche Datei in der Ausgabe. Die Pruefungen lesen den Archivinhalt, nicht den Baum davor.
# Dazu: das echte lizenzen/lib fuehrt genau die sieben Dateien der Sollmenge; die Stage `release-dateien` des Dockerfile
# ruft die Skripte auf; das Make-Fragment exportiert per --output type=local und mountet nichts; `release-dateien` steht in
# keiner Gate-Liste und nicht in tools/mk/e2e.mk (je eine Gegenprobe an einer Kopie).
# Grenze: der Lauf der Stage selbst (Export, Version aus dem echten build.gradle.kts, Docker) ist nicht Gegenstand; ihn belegt
# `make release-dateien`. Ob die Lizenzdateien rechtlich genuegen, sieht der Test nicht.
#
# Aufgerufen von der Dockerfile-Stage `skript-selbsttest`; braucht das JDK-Werkzeug `jar`, sh, unzip, sha256sum, mktemp, grep, sed, find.
set -eu

wurzel=$(cd "$(dirname "$0")/.." && pwd)
dateien="$wurzel/tools/release-dateien.sh"
pruefung="$wurzel/tools/release-pruefung.sh"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

fehler=0
pruefe() { echo "release-selbsttest: FEHLER - $1" >&2; fehler=1; }

echtes_lizenzen="$wurzel/lizenzen/lib"
[ -d "$echtes_lizenzen" ] || { pruefe "lizenzen/lib liegt nicht neben tools/ (die Stage skript-selbsttest kopiert es)"; exit 1; }

# Das echte lizenzen/lib fuehrt genau die sieben Dateien der Sollmenge (die Gutfaelle unten nehmen es als Eingang).
anzahl=$(find "$echtes_lizenzen" -type f | wc -l)
[ "$anzahl" -eq 7 ] || pruefe "lizenzen/lib fuehrt $anzahl Dateien statt sieben (Sollmenge in tools/release-pruefung.sh nachziehen)"

# eingang <name>: legt libs/, lizenzen/, LICENSE und build.gradle.kts an. Veraenderbar ueber VERSION, MANIFEST_EXTRA, JAR_LIZENZ,
# LIBS (Namen der Jars in lib/) und CLASS_PATH (Eintraege des Manifests).
VERSION=1.2.3
MANIFEST_EXTRA=
JAR_LIZENZ=
LIBS="kotlin-stdlib-2.4.10.jar kotlinx-coroutines-core-jvm-1.10.1.jar annotations-23.0.0.jar"
CLASS_PATH="lib/kotlin-stdlib-2.4.10.jar lib/kotlinx-coroutines-core-jvm-1.10.1.jar lib/annotations-23.0.0.jar"
eingang() {
  d="$tmp/$1"
  mkdir -p "$d/libs/lib" "$d/inhalt/META-INF"
  printf 'MIT License, Testtext\n' > "$d/LICENSE"
  if [ -n "$JAR_LIZENZ" ]; then printf '%s\n' "$JAR_LIZENZ" > "$d/inhalt/META-INF/LICENSE"; else cp "$d/LICENSE" "$d/inhalt/META-INF/LICENSE"; fi
  { printf 'Class-Path: %s\n' "$CLASS_PATH"; [ -z "$MANIFEST_EXTRA" ] || printf '%s\n' "$MANIFEST_EXTRA"; } > "$d/mf"
  jar --create --file "$d/libs/tdswire.jar" --manifest "$d/mf" -C "$d/inhalt" . > /dev/null
  for j in $LIBS; do : > "$d/libs/lib/$j"; done
  cp -R "$echtes_lizenzen" "$d/lizenzen"
  if [ -n "$VERSION" ]; then printf 'group = "x"\nversion = "%s"\n' "$VERSION" > "$d/build.gradle.kts"; else printf 'group = "x"\n' > "$d/build.gradle.kts"; fi
}
zuruecksetzen() {
  VERSION=1.2.3; MANIFEST_EXTRA=; JAR_LIZENZ=
  LIBS="kotlin-stdlib-2.4.10.jar kotlinx-coroutines-core-jvm-1.10.1.jar annotations-23.0.0.jar"
  CLASS_PATH="lib/kotlin-stdlib-2.4.10.jar lib/kotlinx-coroutines-core-jvm-1.10.1.jar lib/annotations-23.0.0.jar"
}

# lauf <name> <exit> <muster>: ruft release-dateien.sh auf den Eingang; bei Exit 1 steht <muster> in der Ausgabe, und die Ausgabe
# entsteht nicht.
lauf() {
  name="$1"; soll="$2"; muster="$3"; d="$tmp/$name"
  ist=0
  sh "$dateien" "$d/libs" "$d/lizenzen" "$d/LICENSE" "$d/build.gradle.kts" "$d/aus" > "$d/ausgabe.txt" 2>&1 || ist=$?
  [ "$ist" -eq "$soll" ] || pruefe "$name: Exit $ist statt $soll ($(tr '\n' ' ' < "$d/ausgabe.txt"))"
  if [ "$soll" -eq 1 ]; then
    grep -qF -- "$muster" "$d/ausgabe.txt" || pruefe "$name: die Meldung nennt [$muster] nicht ($(tr '\n' ' ' < "$d/ausgabe.txt"))"
    [ "$name" = ausgabe-belegt ] || [ ! -e "$d/aus" ] || pruefe "$name: die Ausgabe entstand trotz Befund"
  fi
}

# Gutfall.
zuruecksetzen; eingang gut; lauf gut 0 ""
for datei in tdswire-1.2.3.zip tdswire.jar SHA256SUMS; do
  [ -f "$tmp/gut/aus/$datei" ] || pruefe "gut: $datei liegt nicht in der Ausgabe"
done
(cd "$tmp/gut/aus" && sha256sum -c SHA256SUMS > /dev/null 2>&1) || pruefe "gut: sha256sum -c ist rot"
unzip -Z1 "$tmp/gut/aus/tdswire-1.2.3.zip" | grep -qx 'lizenzen/lib/org.jetbrains.kotlinx-kotlinx-coroutines-core-jvm-1.10.1/NOTICE.txt' || pruefe "gut: das ZIP fuehrt die NOTICE von kotlinx-coroutines nicht"
unzip -Z1 "$tmp/gut/aus/tdswire-1.2.3.zip" | grep -qx 'META-INF/MANIFEST.MF' && pruefe "gut: das ZIP fuehrt ein eigenes Manifest"

# Rote Faelle am Eingang: die Pruefung am entpackten ZIP meldet sie.
zuruecksetzen; LIBS="$LIBS jackson-core-2.17.0.jar"; CLASS_PATH="$CLASS_PATH lib/jackson-core-2.17.0.jar"
eingang jackson; lauf jackson 1 "lib/ fuehrt Jackson"
zuruecksetzen; MANIFEST_EXTRA="Main-Class: beispiel.Anwendung"
eingang main; lauf main 1 "Main-Class"
zuruecksetzen; JAR_LIZENZ="Apache License"
eingang andere-lizenz; lauf andere-lizenz 1 "META-INF/LICENSE im Jar ist nicht gleich"
zuruecksetzen; CLASS_PATH="$CLASS_PATH lib/fehlt.jar"
eingang cp-ohne-datei; lauf cp-ohne-datei 1 "lib/fehlt.jar"
zuruecksetzen; eingang lizenz-fehlt
rm "$tmp/lizenz-fehlt/lizenzen/org.jetbrains.kotlin-kotlin-stdlib-2.4.10/LICENSE.txt"
lauf lizenz-fehlt 1 "kotlin-stdlib-2.4.10/LICENSE.txt fehlt"
zuruecksetzen; eingang notice-fehlt
rm "$tmp/notice-fehlt/lizenzen/org.jetbrains.kotlinx-kotlinx-coroutines-core-jvm-1.10.1/NOTICE.txt"
lauf notice-fehlt 1 "kotlinx-coroutines-core-jvm-1.10.1/NOTICE.txt fehlt"
[ -f "$tmp/notice-fehlt/lizenzen/org.jetbrains.kotlinx-kotlinx-coroutines-core-jvm-1.10.1/LICENSE.txt" ] || pruefe "notice-fehlt: die LICENSE.txt der Bibliothek soll in diesem Fall liegen"
zuruecksetzen; eingang drittlizenz-fehlt
rm "$tmp/drittlizenz-fehlt/lizenzen/org.jetbrains.kotlin-kotlin-stdlib-2.4.10/third_party/boost_LICENSE.txt"
lauf drittlizenz-fehlt 1 "third_party/boost_LICENSE.txt fehlt"
zuruecksetzen; eingang notice-leer
: > "$tmp/notice-leer/lizenzen/org.jetbrains.kotlin-kotlin-stdlib-2.4.10/NOTICE.txt"
lauf notice-leer 1 "kotlin-stdlib-2.4.10/NOTICE.txt fehlt im ZIP oder ist leer"
zuruecksetzen; LIBS="$LIBS neu-1.0.jar"; CLASS_PATH="$CLASS_PATH lib/neu-1.0.jar"
eingang neue-bibliothek; lauf neue-bibliothek 1 "lib/neu-1.0.jar liegt keine LICENSE.txt"
zuruecksetzen; VERSION=1.2.3-SNAPSHOT
eingang snapshot; lauf snapshot 1 "endet auf -SNAPSHOT"
zuruecksetzen; VERSION=1.2
eingang version-form; lauf version-form 1 "nicht die Form"
zuruecksetzen; VERSION=
eingang version-fehlt; lauf version-fehlt 1 "nennt keine Version"
zuruecksetzen; eingang ausgabe-belegt
mkdir "$tmp/ausgabe-belegt/aus"; : > "$tmp/ausgabe-belegt/aus/alt"
lauf ausgabe-belegt 1 "nicht leer"

# Rote Faelle am fertigen Archiv.
# archiv <name>: kopiert die Ausgabe des Gutfalls nach $tmp/<name>/aus.
archiv() { mkdir -p "$tmp/$1"; cp -R "$tmp/gut/aus" "$tmp/$1/aus"; }
# summen <name>: rechnet SHA256SUMS neu.
summen() { (cd "$tmp/$1/aus" && sha256sum tdswire-1.2.3.zip tdswire.jar > SHA256SUMS); }
# entpackt <name>: entpackt das ZIP nach $tmp/<name>/x; neu_gepackt <name> packt es wieder und rechnet die Summen.
entpackt() { mkdir -p "$tmp/$1/x"; unzip -q "$tmp/$1/aus/tdswire-1.2.3.zip" -d "$tmp/$1/x"; }
neu_gepackt() { rm "$tmp/$1/aus/tdswire-1.2.3.zip"; jar --create --no-manifest --file "$tmp/$1/aus/tdswire-1.2.3.zip" -C "$tmp/$1/x" .; summen "$1"; }
# archivlauf <name> <muster>: tools/release-pruefung.sh gegen die Ausgabe; erwartet Exit 1 und <muster>.
archivlauf() {
  ist=0
  sh "$pruefung" "$tmp/$1/aus" "$tmp/gut/LICENSE" 1.2.3 > "$tmp/$1/ausgabe.txt" 2>&1 || ist=$?
  [ "$ist" -eq 1 ] || pruefe "$1: Exit $ist statt 1 ($(tr '\n' ' ' < "$tmp/$1/ausgabe.txt"))"
  grep -qF -- "$2" "$tmp/$1/ausgabe.txt" || pruefe "$1: die Meldung nennt [$2] nicht ($(tr '\n' ' ' < "$tmp/$1/ausgabe.txt"))"
}
ist=0
sh "$pruefung" "$tmp/gut/aus" "$tmp/gut/LICENSE" 1.2.3 > /dev/null 2>&1 || ist=$?
[ "$ist" -eq 0 ] || pruefe "Gegenprobe: tools/release-pruefung.sh am Gutfall: Exit $ist statt 0"

archiv summe-falsch
sed -i '1s/^0/1/;t;1s/^./0/' "$tmp/summe-falsch/aus/SHA256SUMS"
archivlauf summe-falsch "sha256sum -c SHA256SUMS ist rot"

archiv summe-fehlt
sed -i '/tdswire.jar$/d' "$tmp/summe-fehlt/aus/SHA256SUMS"
archivlauf summe-fehlt "SHA256SUMS nennt"

archiv zusatzdatei
: > "$tmp/zusatzdatei/aus/extra.txt"
archivlauf zusatzdatei "extra.txt"

archiv jar-einzeln-anders
printf 'anders' >> "$tmp/jar-einzeln-anders/aus/tdswire.jar"; summen jar-einzeln-anders
archivlauf jar-einzeln-anders "tdswire.jar im ZIP ist nicht gleich dem einzelnen"

archiv zip-lizenz-anders
entpackt zip-lizenz-anders; printf 'anders\n' > "$tmp/zip-lizenz-anders/x/LICENSE"; neu_gepackt zip-lizenz-anders
archivlauf zip-lizenz-anders "LICENSE im ZIP ist nicht gleich"

archiv zip-notice-fehlt
entpackt zip-notice-fehlt; rm "$tmp/zip-notice-fehlt/x/lizenzen/lib/org.jetbrains.kotlinx-kotlinx-coroutines-core-jvm-1.10.1/NOTICE.txt"; neu_gepackt zip-notice-fehlt
archivlauf zip-notice-fehlt "kotlinx-coroutines-core-jvm-1.10.1/NOTICE.txt fehlt"

archiv zip-jackson
entpackt zip-jackson; : > "$tmp/zip-jackson/x/lib/jackson-core-2.17.0.jar"; neu_gepackt zip-jackson
archivlauf zip-jackson "lib/ fuehrt Jackson"

archiv zip-kaputt
printf 'kein zip' > "$tmp/zip-kaputt/aus/tdswire-1.2.3.zip"; summen zip-kaputt
archivlauf zip-kaputt "laesst sich nicht entpacken"

# Aufruffehler.
ist=0; sh "$dateien" > /dev/null 2>&1 || ist=$?
[ "$ist" -eq 2 ] || pruefe "release-dateien.sh ohne Argument: Exit $ist statt 2"
ist=0; sh "$pruefung" > /dev/null 2>&1 || ist=$?
[ "$ist" -eq 2 ] || pruefe "release-pruefung.sh ohne Argument: Exit $ist statt 2"

# Dockerfile: die Stage ruft das Eingangsskript, der Export steht in einer eigenen Stage von scratch.
dockerfile="$wurzel/Dockerfile"
aufruf=$(awk '/^FROM /{stage=$0} stage ~ / AS release-dateien$/ && /^RUN sh tools\/release-dateien\.sh build\/libs lizenzen\/lib LICENSE build\.gradle\.kts build\/release-dateien$/ {n++} END {print n+0}' "$dockerfile")
[ "$aufruf" -eq 1 ] || pruefe "das Dockerfile ruft tools/release-dateien.sh in der Stage release-dateien $aufruf mal auf statt einmal"
grep -q '^FROM build-jar AS release-dateien$' "$dockerfile" || pruefe "die Stage release-dateien erbt nicht von build-jar"
grep -q '^FROM scratch AS release-dateien-export$' "$dockerfile" || pruefe "die Stage release-dateien-export fehlt oder erbt nicht von scratch"

# Make-Fragment: Export per --output type=local, nichts gemountet, kein docker run oder cp.
mk="$wurzel/tools/mk/release-dateien.mk"
fragment_fehler() {
  grep -q -- '--output type=local,dest=' "$1" || echo "kein --output type=local,dest="
  grep -q -- '--target release-dateien-export' "$1" || echo "nicht --target release-dateien-export"
  if grep -Eq -- '(^|[[:space:]])(-v|--volume|--mount)([[:space:]=]|$)|docker (run|cp|create)' "$1"; then echo "mountet oder kopiert aus einem Container"; fi
}
[ -f "$mk" ] || pruefe "tools/mk/release-dateien.mk liegt nicht"
[ -z "$(fragment_fehler "$mk")" ] || pruefe "tools/mk/release-dateien.mk: $(fragment_fehler "$mk")"
sed 's|--output type=local,dest=|--output type=tar,dest=|' "$mk" > "$tmp/mk-ohne-output"
[ -n "$(fragment_fehler "$tmp/mk-ohne-output")" ] || pruefe "Gegenprobe: ein Export ohne type=local wird nicht gefunden"
sed 's|docker build|docker run -v $(PWD):/x docker build|' "$mk" > "$tmp/mk-mount"
[ -n "$(fragment_fehler "$tmp/mk-mount")" ] || pruefe "Gegenprobe: ein Mount wird nicht gefunden"

# Abwesenheit in den Gate-Listen und in e2e.mk.
abwesend() {
  w="$1"; n=0
  for f in "$w"/tools/mk/*.mk "$w"/harness/mk/*.mk "$w/Makefile" "$w/repo.mk"; do
    [ -f "$f" ] || continue
    if grep -E '^(GATE_CHECKS|GATE_KLASSE_[a-z]+|GATE_STAGE)[[:space:]]*\+?=.*(^|[[:space:]=])release-dateien' "$f" > /dev/null; then n=$((n + 1)); fi
  done
  if [ -f "$w/tools/mk/e2e.mk" ] && grep -q 'release-dateien' "$w/tools/mk/e2e.mk"; then n=$((n + 1)); fi
  echo "$n"
}
[ "$(abwesend "$wurzel")" -eq 0 ] || pruefe "ein Target release-dateien steht in einer Gate-Liste oder in tools/mk/e2e.mk"
for fall in checks stage e2e; do
  mkdir -p "$tmp/g-$fall/tools/mk"
  case "$fall" in
    checks) echo 'GATE_CHECKS += release-dateien' > "$tmp/g-$fall/tools/mk/x.mk" ;;
    stage) echo 'GATE_STAGE += release-dateien=release-dateien' > "$tmp/g-$fall/tools/mk/x.mk" ;;
    e2e) echo 'e2e-stufe: release-dateien' > "$tmp/g-$fall/tools/mk/e2e.mk" ;;
  esac
  [ "$(abwesend "$tmp/g-$fall")" -eq 1 ] || pruefe "Gegenprobe: $fall wird nicht gefunden"
done

[ "$fehler" -eq 0 ] || exit 1
echo "release-selbsttest: ok (ein Gutfall mit drei Dateien, dreizehn rote Faelle am Eingang, acht am fertigen Archiv, zwei Aufruffehler, Sollmenge gegen lizenzen/lib, Stage und Export im Dockerfile, Make-Fragment ohne Mount mit zwei Gegenproben, Abwesenheit in Gate-Listen und e2e.mk mit drei Gegenproben)"
