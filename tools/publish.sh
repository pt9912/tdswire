#!/bin/bash
# publish.sh — Eingangsskript von `make publish`: prueft Version, Tag, Baum und Token und ruft erst danach Docker
# (TDS-RB-05, TDS-QA-06, ADR-0047 Entscheidungen 3 und 6; Dockerfile-Stage `publish`).
#
# Aufruf: publish.sh [--url <registry-url>]
# Ohne --url geht der Lauf an GitHub Packages (https://maven.pkg.github.com/pt9912/tdswire). --url ist der Weg der
# Belegulaeufe auf ein anderes Ziel; alle Pruefungen gelten auch dann, und das Token muss ein Platzhalter sein (Beginn
# PLATZHALTER-): ein echtes Token geht nie an ein anderes Ziel.
# Umgebung: GITHUB_ACTOR und GITHUB_TOKEN (Pflicht; nur der Name wird an `docker run` gegeben, nie der Wert);
# BUILDER_FLAGS (wie die anderen Make-Targets); PUBLISH_WURZEL (Wurzel des Repos, Voreinstellung: das Verzeichnis ueber
# tools/); PUBLISH_DOCKER_NETZ (Docker-Netz des Laufs, nur fuer Belegulaeufe).
# Exit 1 bei einem Befund vor Docker, 2 bei falschem Aufruf. Geprueft wird in dieser Folge: die Version in
# build.gradle.kts hat die Form <zahl>.<zahl>.<zahl> und endet nicht auf -SNAPSHOT; der Tag v<Version> liegt auf HEAD; der
# Arbeitsbaum ist sauber (auch ohne unversionierte Dateien); GITHUB_ACTOR und GITHUB_TOKEN sind nicht leer; bei --url mit
# einem anderen Ziel als dem Standard beginnt das Token mit PLATZHALTER-.
# Die Version steht in build.gradle.kts als Zeile `version = "..."` (so liest das Skript sie).
# Grenzen: dass `make gates` auf diesem Stand gruen war, prueft das Skript nicht (Setzung von ADR-0047); der Wert des
# Tokens wird nicht auf seine Gueltigkeit geprueft; der Tag wird auf HEAD gelesen, nicht gegen das Remote.
set -eu

wurzel="${PUBLISH_WURZEL:-$(cd "$(dirname "$0")/.." && pwd)}"
# Kopplung: derselbe Wert steht als Voreinstellung von tdswireRegistryUrl in build.gradle.kts; tools/publish-selbsttest.sh liest beide.
standard_url="https://maven.pkg.github.com/pt9912/tdswire"
url="$standard_url"

while [ "$#" -gt 0 ]; do
  case "$1" in
    --url) [ "$#" -ge 2 ] || { echo "Aufruf: $0 [--url <registry-url>]" >&2; exit 2; }; url="$2"; shift 2 ;;
    *) echo "Aufruf: $0 [--url <registry-url>]" >&2; exit 2 ;;
  esac
done
case "$url" in
  http://?* | https://?*) ;;
  *) echo "publish: FEHLER - --url nennt keine http(s)-Adresse" >&2; exit 2 ;;
esac

fehler() { echo "publish: FEHLER - $1" >&2; exit 1; }

version=$(sed -n 's/^version = "\(.*\)"$/\1/p' "$wurzel/build.gradle.kts")
[ -n "$version" ] || fehler "build.gradle.kts nennt keine Version"
case "$version" in
  *-SNAPSHOT) fehler "die Version $version endet auf -SNAPSHOT; veroeffentlicht wird nur eine Release-Version" ;;
esac
printf '%s\n' "$version" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$' \
  || fehler "die Version $version hat nicht die Form <zahl>.<zahl>.<zahl>"

kopf=$(git -C "$wurzel" rev-parse HEAD) || fehler "$wurzel ist kein Repository mit einem Commit"
tag_commit=$(git -C "$wurzel" rev-parse -q --verify "refs/tags/v$version^{commit}") \
  || fehler "der Tag v$version besteht nicht"
[ "$tag_commit" = "$kopf" ] || fehler "der Tag v$version liegt nicht auf HEAD ($kopf)"
[ -z "$(git -C "$wurzel" status --porcelain)" ] || fehler "der Arbeitsbaum ist nicht sauber"

[ -n "${GITHUB_ACTOR:-}" ] || fehler "GITHUB_ACTOR ist leer oder nicht gesetzt"
[ -n "${GITHUB_TOKEN:-}" ] || fehler "GITHUB_TOKEN ist leer oder nicht gesetzt"

# Ein anderes Ziel als GitHub Packages nimmt nur ein Platzhalter-Token (Beginn PLATZHALTER-, wie ihn tools/publish-probe.sh
# erzeugt); ein echtes Token geht nur an das feste Standardziel.
if [ "$url" != "$standard_url" ]; then
  case "$GITHUB_TOKEN" in
    PLATZHALTER-?*) ;;
    *) fehler "--url nennt ein anderes Ziel als $standard_url; dafuer gilt nur ein Platzhalter-Token (Beginn PLATZHALTER-)" ;;
  esac
fi

echo "publish: Version $version, Tag v$version auf HEAD, Baum sauber, Zugangsdaten gesetzt; Ziel $url"
image=tdswire-publish:lokal
# shellcheck disable=SC2086
docker build ${BUILDER_FLAGS:-} --target publish -t "$image" --progress=plain "$wurzel"
# Ohne --offline: Gradle verweigert im Offline-Modus das Hochladen. Die Abhaengigkeiten liegen in der Stage.
# Die Werte gehen nur namentlich an den Container (-e NAME), nie als Argument.
docker run --rm ${PUBLISH_DOCKER_NETZ:+--network "$PUBLISH_DOCKER_NETZ"} -e GITHUB_ACTOR -e GITHUB_TOKEN "$image" \
  gradle --no-daemon publish "-PtdswireRegistryUrl=$url"
