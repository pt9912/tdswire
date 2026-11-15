#!/bin/bash
# publish.sh — Eingangsskript von `make publish` und `make publish-cloudsmith`: prueft Version, Tag, Baum und Zugangsdaten
# und ruft erst danach Docker (TDS-RB-05, TDS-QA-06, ADR-0047 Entscheidungen 3 und 6, ADR-0049 Entscheidung 3;
# Dockerfile-Stage `publish`).
#
# Aufruf: publish.sh [--ziel github|cloudsmith] [--url <registry-url>]
# Ziele (ADR-0049 Entscheidung 3): `github` (Voreinstellung, GitHub Packages, https://maven.pkg.github.com/pt9912/tdswire,
# Zugangsdaten GITHUB_ACTOR und GITHUB_TOKEN, Task publishMavenPublicationToRegistryRepository) und `cloudsmith`
# (https://maven.cloudsmith.io/pt9912/tdswire/, Zugangsdaten CLOUDSMITH_USERNAME und CLOUDSMITH_API_KEY, Task
# publishMavenPublicationToCloudsmithRepository). Je Ziel wird nur dessen Umgebung verlangt und an `docker run` gegeben, und
# je Ziel laeuft die Einzel-Task, weil `publish` beide Repositories ausfuehrt.
# Ohne --url geht der Lauf an das Standardziel des gewaehlten Ziels. --url ist der Weg der Belegulaeufe auf ein anderes Ziel;
# alle Pruefungen gelten auch dann, und das Token (bei cloudsmith der Schluessel) muss ein Platzhalter sein (Beginn
# PLATZHALTER-). Grenze: das gilt nur fuer den Weg ueber dieses Skript (Praefixpruefung, am Docker-Stub gesehen); ein
# direkter `gradle publish` im Container (build.gradle.kts erlaubt http:// ueber isAllowInsecureProtocol) und ein von Hand
# so genanntes echtes Token umgehen sie.
# Umgebung: je Ziel die oben genannten Zugangsdaten (Pflicht; nur der Name wird an `docker run` gegeben, nie der Wert);
# BUILDER_FLAGS (wie die anderen Make-Targets); PUBLISH_WURZEL (Wurzel des Repos, Voreinstellung: das Verzeichnis ueber
# tools/); PUBLISH_DOCKER_NETZ (Docker-Netz des Laufs, nur fuer Belegulaeufe).
# Exit 1 bei einem Befund vor Docker, 2 bei falschem Aufruf. Geprueft wird in dieser Folge: die Version in
# build.gradle.kts hat die Form <zahl>.<zahl>.<zahl> und endet nicht auf -SNAPSHOT; der Tag v<Version> liegt auf HEAD; der
# Arbeitsbaum ist sauber (auch ohne unversionierte Dateien); die Zugangsdaten des Ziels sind nicht leer; bei --url mit
# einem anderen Ziel als dem Standard beginnt das Token mit PLATZHALTER-.
# Die Version steht in build.gradle.kts als Zeile `version = "..."` (so liest das Skript sie).
# Grenzen: dass `make gates` auf diesem Stand gruen war, prueft das Skript nicht (Setzung von ADR-0047); der Wert des
# Tokens wird nicht auf seine Gueltigkeit geprueft; der Tag wird auf HEAD gelesen, nicht gegen das Remote.
set -eu

wurzel="${PUBLISH_WURZEL:-$(cd "$(dirname "$0")/.." && pwd)}"
aufruf="Aufruf: $0 [--ziel github|cloudsmith] [--url <registry-url>]"
ziel=github
url=""

while [ "$#" -gt 0 ]; do
  case "$1" in
    --url) [ "$#" -ge 2 ] || { echo "$aufruf" >&2; exit 2; }; url="$2"; shift 2 ;;
    --ziel) [ "$#" -ge 2 ] || { echo "$aufruf" >&2; exit 2; }; ziel="$2"; shift 2 ;;
    *) echo "$aufruf" >&2; exit 2 ;;
  esac
done

# Kopplung: die Standardziele stehen ebenso als Voreinstellung in build.gradle.kts (tdswireRegistryUrl,
# tdswireCloudsmithUrl); tools/publish-selbsttest.sh liest beide Stellen.
case "$ziel" in
  github)
    standard_url="https://maven.pkg.github.com/pt9912/tdswire"
    benutzer_var=GITHUB_ACTOR; geheim_var=GITHUB_TOKEN
    gradle_task=publishMavenPublicationToRegistryRepository; gradle_property=tdswireRegistryUrl ;;
  cloudsmith)
    standard_url="https://maven.cloudsmith.io/pt9912/tdswire/"
    benutzer_var=CLOUDSMITH_USERNAME; geheim_var=CLOUDSMITH_API_KEY
    gradle_task=publishMavenPublicationToCloudsmithRepository; gradle_property=tdswireCloudsmithUrl ;;
  *) echo "publish: FEHLER - --ziel nennt weder github noch cloudsmith" >&2; exit 2 ;;
esac
[ -n "$url" ] || url="$standard_url"
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

benutzer_wert=$(printenv "$benutzer_var" || true)
geheim_wert=$(printenv "$geheim_var" || true)
[ -n "$benutzer_wert" ] || fehler "$benutzer_var ist leer oder nicht gesetzt"
[ -n "$geheim_wert" ] || fehler "$geheim_var ist leer oder nicht gesetzt"

# Ein anderes Ziel als das Standardziel nimmt nur einen Platzhalter (Beginn PLATZHALTER-, wie ihn tools/publish-probe.sh
# erzeugt); ein echtes Token oder ein echter Schluessel geht nur an das feste Standardziel des Anbieters.
if [ "$url" != "$standard_url" ]; then
  case "$geheim_wert" in
    PLATZHALTER-?*) ;;
    *) fehler "--url nennt ein anderes Ziel als $standard_url; dafuer gilt nur ein Platzhalter (Beginn PLATZHALTER-) in $geheim_var" ;;
  esac
fi

echo "publish: Version $version, Tag v$version auf HEAD, Baum sauber, Zugangsdaten gesetzt; Ziel $ziel, $url"
image=tdswire-publish:lokal
# shellcheck disable=SC2086
docker build ${BUILDER_FLAGS:-} --target publish -t "$image" --progress=plain "$wurzel"
# Ohne --offline: Gradle verweigert im Offline-Modus das Hochladen. Die Abhaengigkeiten liegen in der Stage.
# Die Werte gehen nur namentlich an den Container (-e NAME), nie als Argument.
docker run --rm ${PUBLISH_DOCKER_NETZ:+--network "$PUBLISH_DOCKER_NETZ"} -e "$benutzer_var" -e "$geheim_var" "$image" \
  gradle --no-daemon "$gradle_task" "-P$gradle_property=$url"
