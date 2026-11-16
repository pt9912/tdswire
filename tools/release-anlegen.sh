#!/bin/bash
# release-anlegen.sh — Eingangsskript des Jobs `release` in .github/workflows/veroeffentlichung.yml: prueft Tag, Dateien,
# Pruefsummen und dass zum Tag noch kein Release besteht, fuellt die Notiz aus tools/release-notiz.vorlage und ruft erst danach
# `gh release create` (TDS-RB-05, TDS-QA-06, ADR-0050 Entscheidungen 1, 4 und 5; Muster tools/publish.sh).
#
# Aufruf: release-anlegen.sh --tag v<Version> --dateien <verzeichnis>
#         release-anlegen.sh --tag v0.1.0|v0.1.1 --ohne-dateien
# Mit Dateien: <verzeichnis> ist die Ausgabe von `make release-dateien` und fuehrt genau tdswire-<Version>.zip, tdswire.jar und
# SHA256SUMS. Ohne Dateien (ADR-0050 Entscheidung 5): nur fuer die Bestandstags v0.1.0 und v0.1.1, Notiz ohne Dateizeilen, kein Anhang.
# Umgebung: GH_TOKEN (nur fuer gh; der Wert steht in keiner Ausgabe und in keinem Argument); RELEASE_WURZEL (Wurzel des Repos,
# Voreinstellung: das Verzeichnis ueber tools/); die Vorlage liegt neben dem Skript.
# Exit 1 bei einem Befund vor `gh release create`, 2 bei falschem Aufruf. Geprueft wird vor jedem Aufruf von `gh`, in dieser Folge:
# mit Dateien: Version in build.gradle.kts hat die Form <zahl>.<zahl>.<zahl> (auch -SNAPSHOT bricht), der Tag ist v<Version>, besteht
# und liegt auf HEAD, das Verzeichnis fuehrt genau die drei Dateien, `sha256sum -c SHA256SUMS` ist gruen und SHA256SUMS nennt ZIP und
# Jar; ohne Dateien: der Tag ist v0.1.0 oder v0.1.1 und besteht. Danach fragt `gh release list`, ob zum Tag ein Release besteht (besteht
# eines oder schlaegt die Abfrage fehl, Abbruch; nie --clobber, nie ein Loeschen), die Notiz darf keine unersetzte Stelle @NAME@ behalten,
# und ein einziger Aufruf `gh release create <Tag> <Dateien> --verify-tag --notes-file` legt Release und Anhaenge an.
# Marken `# P:<name>` am Zeilenende benennen die Pruefungen, an denen tools/release-anlegen-selbsttest.sh Mutationen setzt.
# Grenzen: der Selbsttest belegt den Ablauf an einem gh-Stub, nicht GitHub (Recht contents: write, Anlegen, Anhaenge, Abbruchverhalten,
# gh auf dem Runner sind ungemessen); Pins, Reihenfolge der Jobs, Bedingung und Rechte des Workflows prueft der Review; ob `gh release
# list` bei mehr als 1000 Releases alle nennt, ist nicht gemessen. Dass `make gates` gruen war, prueft das Skript nicht.
# Braucht bash, git, gh, sha256sum, awk, grep, sed.
set -eu

wurzel="${RELEASE_WURZEL:-$(cd "$(dirname "$0")/.." && pwd)}"
vorlage="$(cd "$(dirname "$0")" && pwd)/release-notiz.vorlage"
aufruf="Aufruf: $0 --tag v<Version> (--dateien <verzeichnis> | --ohne-dateien)"
tag=""; dir=""; ohne=0

while [ "$#" -gt 0 ]; do
  case "$1" in
    --tag) [ "$#" -ge 2 ] || { echo "$aufruf" >&2; exit 2; }; tag="$2"; shift 2 ;;
    --dateien) [ "$#" -ge 2 ] || { echo "$aufruf" >&2; exit 2; }; dir="$2"; shift 2 ;;
    --ohne-dateien) ohne=1; shift ;;
    *) echo "$aufruf" >&2; exit 2 ;;
  esac
done
[ -n "$tag" ] || { echo "$aufruf" >&2; exit 2; }
if [ "$ohne" -eq 1 ]; then [ -z "$dir" ] || { echo "$aufruf" >&2; exit 2; }; else [ -n "$dir" ] || { echo "$aufruf" >&2; exit 2; }; fi

fehler() { echo "release-anlegen: FEHLER - $1" >&2; exit 1; }

if [ "$ohne" -eq 1 ]; then
  [ "$tag" = v0.1.0 ] || [ "$tag" = v0.1.1 ] || fehler "ohne Dateien nur fuer v0.1.0 und v0.1.1, nicht fuer $tag" # P:ohne-bestand
  version="${tag#v}"
  commit=$(git -C "$wurzel" rev-parse -q --verify "refs/tags/$tag^{commit}") || fehler "der Tag $tag besteht nicht" # P:ohne-tag
  abschnitte=" OHNE "
  [ "$tag" != v0.1.1 ] || abschnitte=" OHNE CLOUDSMITH "
  zip=""; sha_zip=""; sha_jar=""
else
  version=$(sed -n 's/^version = "\(.*\)"$/\1/p' "$wurzel/build.gradle.kts")
  [ -n "$version" ] || fehler "build.gradle.kts nennt keine Version"
  printf '%s\n' "$version" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$' || fehler "die Version $version hat nicht die Form <zahl>.<zahl>.<zahl> (auch -SNAPSHOT wird nicht angelegt)" # P:version-form
  [ "$tag" = "v$version" ] || fehler "der Tag $tag ist nicht v$version" # P:tag-version
  commit=$(git -C "$wurzel" rev-parse -q --verify "refs/tags/$tag^{commit}") || fehler "der Tag $tag besteht nicht" # P:tag-besteht
  [ "$commit" = "$(git -C "$wurzel" rev-parse HEAD)" ] || fehler "der Tag $tag liegt nicht auf HEAD" # P:tag-head
  zip="tdswire-$version.zip"
  for f in "$zip" tdswire.jar SHA256SUMS; do
    [ -f "$dir/$f" ] || fehler "die Datei $f fehlt in $dir" # P:dateien
  done
  [ "$(ls -A "$dir" | LC_ALL=C sort | tr '\n' ' ')" = "$(printf '%s\n' SHA256SUMS "$zip" tdswire.jar | LC_ALL=C sort | tr '\n' ' ')" ] || fehler "$dir fuehrt nicht genau $zip, tdswire.jar und SHA256SUMS" # P:nur-diese
  (cd "$dir" && sha256sum -c SHA256SUMS >/dev/null 2>&1) || fehler "sha256sum -c SHA256SUMS ist rot" # P:summen
  sha_zip=$(awk -v n="$zip" '$2 == n {print $1}' "$dir/SHA256SUMS")
  sha_jar=$(awk '$2 == "tdswire.jar" {print $1}' "$dir/SHA256SUMS")
  { [ -n "$sha_zip" ] && [ -n "$sha_jar" ]; } || fehler "SHA256SUMS nennt nicht $zip und tdswire.jar" # P:summen-zeilen
  abschnitte=" MIT CLOUDSMITH "
fi

# Ab hier fragt das Skript gh; die Abfrage steht vor dem Anlegen und gibt ihren Befund ohne Token-Wert aus.
liste=$(gh release list --limit 1000 --json tagName --jq '.[].tagName') || fehler "gh release list schlug fehl; ob zum Tag ein Release besteht, ist unbekannt" # P:liste
printf '%s\n' "$liste" | grep -Fxq "$tag" && fehler "zum Tag $tag besteht schon ein Release; nichts wird ueberschrieben oder geloescht" # P:besteht

notiz=$(mktemp)
trap 'rm -f "$notiz"' EXIT
awk -v aktiv="$abschnitte" '
  /^@@[A-Z]+@@$/ { n = substr($0, 3, length($0) - 4); if (n == "ENDE") { an = 1 } else { an = (index(aktiv, " " n " ") > 0) }; next }
  an == 0 { next }
  { print }
  BEGIN { an = 1 }' "$vorlage" \
  | sed -e "s|@VERSION@|$version|g" -e "s|@TAG@|$tag|g" -e "s|@COMMIT@|$commit|g" -e "s|@ZIP@|$zip|g" \
        -e "s|@SHA_ZIP@|$sha_zip|g" -e "s|@SHA_JAR@|$sha_jar|g" > "$notiz"
! grep -Eq '@[A-Z_]+@' "$notiz" || fehler "die Notiz behaelt eine unersetzte Stelle der Form @NAME@" # P:platzhalter

echo "release-anlegen: Tag $tag, Version $version, Commit $commit; kein Release zum Tag; $([ "$ohne" -eq 1 ] && echo "ohne Dateien" || echo "Dateien in $dir")"
if [ "$ohne" -eq 1 ]; then
  gh release create "$tag" --verify-tag --title "$version" --notes-file "$notiz"
else
  gh release create "$tag" "$dir/$zip" "$dir/tdswire.jar" "$dir/SHA256SUMS" --verify-tag --title "$version" --notes-file "$notiz"
fi
