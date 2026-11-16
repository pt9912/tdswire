#!/bin/sh
# release-pruefung.sh — prueft die Release-Dateien am ENTPACKTEN ZIP, nicht am Baum davor (TDS-RB-05, TDS-QA-06, ADR-0050
# Entscheidungen 2 und 3, Mess-Pflichten 5 und 8). Aufrufer: tools/release-dateien.sh (Dockerfile, Stage `release-dateien`).
#
# Aufruf: release-pruefung.sh [--vorschau] <ausgabe-verzeichnis> <LICENSE-datei> <version>
# Das Ausgabe-Verzeichnis fuehrt genau drei Dateien: tdswire-<version>.zip, tdswire.jar und SHA256SUMS; mit --vorschau heisst das
# ZIP tdswire-<version>-vorschau.zip (der Modus aendert sonst nichts an den Pruefungen). Exit 1 bei einem
# Befund, 2 bei falschem Aufruf. Geprueft wird:
#  - SHA256SUMS nennt genau tdswire-<version>.zip und tdswire.jar, und `sha256sum -c` ist gruen;
#  - das ZIP wird in ein leeres Verzeichnis entpackt, dort laeuft tools/manifest-pruefung.sh --ohne-pom (keine Main-Class,
#    Class-Path nur gegen vorhandene lib/<Name>, kein Jackson in lib/, kein Testserver im Jar);
#  - META-INF/LICENSE im Jar und LICENSE im ZIP sind gleich der Datei <LICENSE-datei>; tdswire.jar im ZIP ist gleich dem
#    einzelnen tdswire.jar;
#  - die Sollmenge der Lizenz- und Hinweisdateien (sieben, Liste unten) liegt im ZIP unter lizenzen/lib/ und ist nicht leer;
#  - zu jedem Jar in lib/ gibt es ein Verzeichnis lizenzen/lib/<Gruppe>-<Name ohne .jar> mit einer nicht leeren LICENSE.txt:
#    eine neue Bibliothek ohne Verzeichnis bricht den Lauf.
# Die Sollmenge ist ein Vorschlag: sie umfasst alle sieben Dateien der Ablage lizenzen/lib/ des Belegs
# docs/messungen/lizenzen-lib-2026-11-16.md; dessen offene Fragen 2a bis 2c sind unbeantwortet, die Entscheidung liegt beim Nutzer. Die Liste steht hier von Hand, und tools/release-selbsttest.sh haelt sie gegen das Verzeichnis lizenzen/lib des Repos.
# Grenze: geprueft werden Namen, Gleichheit und Nicht-Leere der Dateien, nicht ihr Inhalt und nicht, ob die Lizenz die
# Weitergabe so erlaubt (Rechtsfrage, ADR-0050 Entscheidung 3); andere Hinweisdateien als die der Sollmenge sieht die Pruefung
# nicht; ob zwei Laeufe dasselbe ZIP liefern, ist nicht zugesagt.
# Braucht sh, unzip, sha256sum, cmp, mktemp, ls, sort (die Stage erbt sie von `basis`).
set -eu

marke=
if [ "${1:-}" = "--vorschau" ]; then marke=-vorschau; shift; fi
[ "$#" -eq 3 ] || { echo "Aufruf: $0 [--vorschau] <ausgabe-verzeichnis> <LICENSE-datei> <version>" >&2; exit 2; }
aus="$1"
lizenz="$2"
version="$3"
skript=$(dirname "$0")
zip="tdswire-$version$marke.zip"

fehler=0
melde() { echo "release-pruefung: FEHLER - $1" >&2; fehler=1; }

[ -d "$aus" ] || { echo "release-pruefung: FEHLER - $aus ist kein Verzeichnis" >&2; exit 1; }
[ -f "$lizenz" ] || { echo "release-pruefung: FEHLER - $lizenz liegt nicht" >&2; exit 1; }

# Ein Vorschau-ZIP gehoert in keine Release-Ausgabe.
if [ -z "$marke" ] && ls "$aus" | grep -q -- '-vorschau\.zip$'; then
  echo "release-pruefung: FEHLER - $aus fuehrt ein Vorschau-ZIP ($(ls "$aus" | grep -- '-vorschau\.zip$' | tr '\n' ' ')), das keine Release-Ausgabe ist" >&2; exit 1
fi

# Genau drei Dateien: keine fehlende, keine zusaetzliche.
ist=$(ls -A "$aus" | sort | tr '\n' ' ')
soll=$(printf '%s\n' "SHA256SUMS" "$zip" "tdswire.jar" | sort | tr '\n' ' ')
[ "$ist" = "$soll" ] || { echo "release-pruefung: FEHLER - $aus fuehrt [$ist] statt [$soll]" >&2; exit 1; }

# SHA256SUMS: Format `sha256sum`, jede Anhangsdatei ausser sich selbst, und die Summen stimmen.
namen=$(awk '{ n = $2; sub(/^\*/, "", n); print n }' "$aus/SHA256SUMS" | sort | tr '\n' ' ')
soll_namen=$(printf '%s\n' "$zip" "tdswire.jar" | sort | tr '\n' ' ')
[ "$namen" = "$soll_namen" ] || melde "SHA256SUMS nennt [$namen] statt [$soll_namen]"
(cd "$aus" && sha256sum -c SHA256SUMS > /dev/null 2>&1) || melde "sha256sum -c SHA256SUMS ist rot in $aus"

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
x="$tmp/entpackt"
mkdir "$x"
unzip -q "$aus/$zip" -d "$x" || { echo "release-pruefung: FEHLER - $zip laesst sich nicht entpacken" >&2; exit 1; }

# Manifest, Class-Path, Jackson, Testserver am entpackten Archiv.
if [ -f "$x/tdswire.jar" ]; then
  sh "$skript/manifest-pruefung.sh" --ohne-pom "$x" > "$tmp/manifest.aus" 2>&1 || { cat "$tmp/manifest.aus" >&2; melde "tools/manifest-pruefung.sh ist rot am entpackten ZIP"; }
else
  melde "das ZIP fuehrt kein tdswire.jar"
fi

# LICENSE im ZIP, META-INF/LICENSE im Jar, einzelnes Jar gleich dem Jar im ZIP.
if [ -f "$x/LICENSE" ]; then
  cmp -s "$x/LICENSE" "$lizenz" || melde "LICENSE im ZIP ist nicht gleich $lizenz"
else
  melde "das ZIP fuehrt keine LICENSE"
fi
if [ -f "$x/tdswire.jar" ]; then
  unzip -p "$x/tdswire.jar" META-INF/LICENSE 2> /dev/null | cmp -s - "$lizenz" || melde "META-INF/LICENSE im Jar ist nicht gleich $lizenz"
  cmp -s "$x/tdswire.jar" "$aus/tdswire.jar" || melde "tdswire.jar im ZIP ist nicht gleich dem einzelnen tdswire.jar"
fi

# Sollmenge der Lizenz- und Hinweisdateien: alle sieben, nicht leer.
sollmenge="
org.jetbrains.kotlin-kotlin-stdlib-2.4.10/LICENSE.txt
org.jetbrains.kotlin-kotlin-stdlib-2.4.10/NOTICE.txt
org.jetbrains.kotlin-kotlin-stdlib-2.4.10/third_party/boost_LICENSE.txt
org.jetbrains.kotlin-kotlin-stdlib-2.4.10/third_party/threetenbp_license.txt
org.jetbrains.kotlinx-kotlinx-coroutines-core-jvm-1.10.1/LICENSE.txt
org.jetbrains.kotlinx-kotlinx-coroutines-core-jvm-1.10.1/NOTICE.txt
org.jetbrains-annotations-23.0.0/LICENSE.txt"
for datei in $sollmenge; do
  [ -s "$x/lizenzen/lib/$datei" ] || melde "lizenzen/lib/$datei fehlt im ZIP oder ist leer"
done

# Je Jar in lib/ ein Verzeichnis mit LICENSE.txt: eine Bibliothek ohne Sollzeile bricht hier.
if [ -d "$x/lib" ]; then
  for jar in "$x"/lib/*.jar; do
    [ -f "$jar" ] || continue
    basis=$(basename "$jar" .jar)
    gefunden=0
    for verz in "$x"/lizenzen/lib/*-"$basis"; do
      [ -s "$verz/LICENSE.txt" ] && gefunden=1
    done
    [ "$gefunden" -eq 1 ] || melde "zu lib/$basis.jar liegt keine LICENSE.txt unter lizenzen/lib/<Gruppe>-$basis"
  done
else
  melde "das ZIP fuehrt kein lib/"
fi

[ "$fehler" -eq 0 ] || exit 1
echo "release-pruefung: ok ($zip entpackt: Manifest ohne Main-Class, kein Jackson, LICENSE gleich, $(printf '%s\n' "$sollmenge" | grep -c .) Lizenz- und Hinweisdateien, SHA256SUMS gruen)"
