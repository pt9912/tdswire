#!/bin/bash
# publish-selbsttest.sh — belegt in einem Wegwerf-Repo, dass tools/publish.sh vor jedem Docker-Aufruf endet, wenn die
# Version auf -SNAPSHOT steht oder nicht die Form <zahl>.<zahl>.<zahl> hat, fehlt, der Tag v<Version> fehlt oder nicht auf
# HEAD liegt, der Baum unsauber ist (geaenderte und unversionierte Datei) oder GITHUB_ACTOR und GITHUB_TOKEN leer oder
# nicht gesetzt sind, ein anderes Ziel als GitHub Packages mit einem Token ohne Platzhalter-Beginn gewaehlt wird, dass der gute Fall docker mit dem Ziel aufruft und den Wert des Tokens nirgends nennt, dass ein
# falscher Aufruf mit Exit 2 endet, und dass `publish` in keiner Gate-Liste und nicht in tools/mk/e2e.mk steht
# (TDS-RB-05, TDS-QA-06, ADR-0047 Entscheidungen 3 und 6).
# Je Ziel (--ziel github|cloudsmith, ADR-0049 Entscheidung 3): dieselben Faelle mit den Zugangsdaten des Ziels, die Einzel-Task
# im run-Aufruf, nur die Umgebung des gewaehlten Ziels im run-Aufruf, das Standardziel nimmt einen Schluessel ohne
# Platzhalter, jedes andere Ziel nicht; ein unbekanntes Ziel endet mit Exit 2; `publish-cloudsmith` steht in keiner Gate-Liste.
# Grenze: docker ist ein Stub; belegt ist die Reihenfolge der Pruefungen und die Form des Aufrufs, nicht das Veroeffentlichen.
# Die Abwesenheitspruefung liest Make-Dateien als Text und kennt weder Includes noch Variablen.
#
# Aufgerufen von der Dockerfile-Stage `skript-selbsttest` (Skripte unter tools/, tools/mk, harness/mk, Makefile, repo.mk).
# Braucht bash, git, mktemp, grep, sed.
set -eu

hier=$(cd "$(dirname "$0")" && pwd)
skript="$hier/publish.sh"
wurzel_repo=$(cd "$hier/.." && pwd)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

fehler=0
pruefe() { echo "publish-selbsttest: FEHLER - $1" >&2; fehler=1; }

mkdir -p "$tmp/bin"
cat > "$tmp/bin/docker" <<'STUB'
#!/bin/sh
echo "docker $*" >> "$DOCKER_LOG"
STUB
chmod +x "$tmp/bin/docker"

platzhalter="PLATZHALTER-TOKEN-4711"
BEN_VAR=GITHUB_ACTOR; GEH_VAR=GITHUB_TOKEN

# neues <name> <version-zeile> <tag: ja|nein|alt>: Repo mit einem Commit, optional dem Tag v<version> auf HEAD oder einem Commit davor.
neues() {
  name="$1"; zeile="$2"; tag="$3"
  r="$tmp/$name"
  mkdir -p "$r"
  git -C "$r" init -q
  git -C "$r" config user.name selbsttest
  git -C "$r" config user.email selbsttest@invalid
  git -C "$r" config commit.gpgsign false
  git -C "$r" config tag.gpgsign false
  printf '%s\n' "$zeile" > "$r/build.gradle.kts"
  git -C "$r" add build.gradle.kts
  git -C "$r" commit -q -m eins
  v=$(printf '%s\n' "$zeile" | sed -n 's/^version = "\(.*\)"$/\1/p')
  case "$tag" in
    ja) git -C "$r" tag "v$v" ;;
    annotiert) git -C "$r" tag -a "v$v" -m "Release $v" ;;
    alt) git -C "$r" tag "v$v"; echo x > "$r/zwei.txt"; git -C "$r" add zwei.txt; git -C "$r" commit -q -m zwei ;;
  esac
}

# lauf <name> <soll-exit> <muster-der-meldung> <actor> <token> [argumente...]: Aufruf mit Stub-docker; Exit, Meldung und
# docker-Aufrufe werden gelesen. Bei einem Exit ungleich 0 darf docker nicht gerufen sein.
lauf() {
  name="$1"; soll="$2"; muster="$3"; actor="$4"; token="$5"; shift 5
  : > "$tmp/$name.docker"
  ist=0
  env -u GITHUB_ACTOR -u GITHUB_TOKEN -u CLOUDSMITH_USERNAME -u CLOUDSMITH_API_KEY \
    ${actor:+"${BEN_VAR:-GITHUB_ACTOR}=$actor"} ${token:+"${GEH_VAR:-GITHUB_TOKEN}=$token"} \
    PATH="$tmp/bin:$PATH" DOCKER_LOG="$tmp/$name.docker" PUBLISH_WURZEL="$tmp/$name" \
    bash "$skript" "$@" > "$tmp/$name.aus" 2>&1 || ist=$?
  [ "$ist" -eq "$soll" ] || pruefe "$name: Exit $ist statt $soll ($(tr '\n' ' ' < "$tmp/$name.aus"))"
  if [ -n "$muster" ] && ! grep -q "$muster" "$tmp/$name.aus"; then
    pruefe "$name: die Meldung nennt nicht [$muster] ($(tr '\n' ' ' < "$tmp/$name.aus"))"
  fi
  if [ "$soll" -ne 0 ] && [ -s "$tmp/$name.docker" ]; then
    pruefe "$name: docker wurde trotz Exit $soll gerufen ($(tr '\n' ' ' < "$tmp/$name.docker"))"
  fi
}

# Der gute Fall: docker wird zweimal gerufen (build, run), der Aufruf nennt Ziel und die Namen der Umgebung.
neues gut 'version = "0.1.0"' ja
lauf gut 0 "Version 0.1.0" aktor "$platzhalter" --url http://registry.invalid:8080/repo
[ "$(wc -l < "$tmp/gut.docker")" -eq 2 ] || pruefe "gut: docker nicht genau zweimal gerufen"
grep -q '^docker build .*--target publish' "$tmp/gut.docker" || pruefe "gut: kein docker build der Stage publish"
grep -q '^docker run --rm -e GITHUB_ACTOR -e GITHUB_TOKEN tdswire-publish:lokal gradle .* publishMavenPublicationToRegistryRepository -PtdswireRegistryUrl=http://registry.invalid:8080/repo$' "$tmp/gut.docker" \
  || pruefe "gut: der run-Aufruf hat nicht die erwartete Form ($(grep '^docker run' "$tmp/gut.docker"))"
if grep -q "$platzhalter" "$tmp/gut.docker" "$tmp/gut.aus"; then pruefe "gut: der Wert des Tokens steht in der Ausgabe oder im docker-Aufruf"; fi

# Ohne --url geht der Aufruf an GitHub Packages.
neues standard 'version = "0.2.0"' ja
lauf standard 0 "" aktor "ghp_echtes-token-form"
if grep -q "ghp_echtes-token-form" "$tmp/standard.docker" "$tmp/standard.aus"; then pruefe "standard: der Wert des Tokens steht in der Ausgabe oder im docker-Aufruf"; fi
grep -q 'publishMavenPublicationToRegistryRepository -PtdswireRegistryUrl=https://maven.pkg.github.com/pt9912/tdswire$' "$tmp/standard.docker" \
  || pruefe "standard: ohne --url ist das Ziel nicht GitHub Packages"

# Ein annotierter Tag (eigenes Tag-Objekt) gilt wie ein leichter, wenn er auf HEAD zeigt.
neues annotiert 'version = "0.3.0"' annotiert
lauf annotiert 0 "Version 0.3.0" aktor "$platzhalter"

# Docker-Netz fuer Belegulaeufe: wird an run gereicht.
: > "$tmp/gut.docker"
PUBLISH_DOCKER_NETZ=probe PATH="$tmp/bin:$PATH" DOCKER_LOG="$tmp/gut.docker" PUBLISH_WURZEL="$tmp/gut" \
  GITHUB_ACTOR=aktor GITHUB_TOKEN="$platzhalter" bash "$skript" --url http://registry.invalid:8080/repo > /dev/null 2>&1 \
  || pruefe "netz: Exit ungleich 0"
grep -q '^docker run --rm --network probe -e GITHUB_ACTOR' "$tmp/gut.docker" || pruefe "netz: run ohne --network probe"

# Rote Faelle, je einer je Pruefung; jeder ist ausser der einen Ursache gut.
neues snapshot 'version = "0.1.0-SNAPSHOT"' ja
lauf snapshot 1 "endet auf -SNAPSHOT" aktor "$platzhalter"
neues form-zusatz 'version = "0.1.0-rc1"' ja
lauf form-zusatz 1 "nicht die Form" aktor "$platzhalter"
neues form-zwei 'version = "1.0"' ja
lauf form-zwei 1 "nicht die Form" aktor "$platzhalter"
neues ohne-version '// keine Version' nein
lauf ohne-version 1 "nennt keine Version" aktor "$platzhalter"
neues tag-fehlt 'version = "0.1.0"' nein
lauf tag-fehlt 1 "Tag v0.1.0 besteht nicht" aktor "$platzhalter"
neues tag-alt 'version = "0.1.0"' alt
lauf tag-alt 1 "nicht auf HEAD" aktor "$platzhalter"
neues baum-geaendert 'version = "0.1.0"' ja
echo '// geaendert' >> "$tmp/baum-geaendert/build.gradle.kts"
lauf baum-geaendert 1 "nicht sauber" aktor "$platzhalter"
neues baum-unversioniert 'version = "0.1.0"' ja
echo x > "$tmp/baum-unversioniert/neu.txt"
lauf baum-unversioniert 1 "nicht sauber" aktor "$platzhalter"
neues token-fehlt 'version = "0.1.0"' ja
lauf token-fehlt 1 "GITHUB_TOKEN ist leer" aktor ""
neues token-leer 'version = "0.1.0"' ja
: > "$tmp/token-leer.docker"
ist=0
GITHUB_ACTOR=aktor GITHUB_TOKEN= PATH="$tmp/bin:$PATH" DOCKER_LOG="$tmp/token-leer.docker" PUBLISH_WURZEL="$tmp/token-leer" \
  bash "$skript" > "$tmp/token-leer.aus" 2>&1 || ist=$?
[ "$ist" -eq 1 ] || pruefe "token-leer: Exit $ist statt 1"
grep -q "GITHUB_TOKEN ist leer" "$tmp/token-leer.aus" || pruefe "token-leer: Meldung fehlt"
[ ! -s "$tmp/token-leer.docker" ] || pruefe "token-leer: docker wurde gerufen"
neues actor-fehlt 'version = "0.1.0"' ja
lauf actor-fehlt 1 "GITHUB_ACTOR ist leer" "" "$platzhalter"

# Ein echtes Token geht nicht an ein anderes Ziel: --url mit anderem Ziel und einem Token ohne Platzhalter bricht vor Docker.
neues fremdes-ziel 'version = "0.1.0"' ja
lauf fremdes-ziel 1 "dafuer gilt nur ein Platzhalter" aktor "ghp_echtes-token-form" --url http://anderer-host.invalid/repo
neues fremdes-ziel-https 'version = "0.1.0"' ja
lauf fremdes-ziel-https 1 "dafuer gilt nur ein Platzhalter" aktor "ghp_echtes-token-form" --url https://anderer-host.invalid/repo
neues platzhalter-leer 'version = "0.1.0"' ja
lauf platzhalter-leer 1 "dafuer gilt nur ein Platzhalter" aktor "PLATZHALTER-" --url http://anderer-host.invalid/repo

# Falscher Aufruf: Exit 2, kein docker.
lauf aufruf-unbekannt 2 "Aufruf" aktor "$platzhalter" --unbekannt
lauf aufruf-ohne-wert 2 "Aufruf" aktor "$platzhalter" --url
lauf aufruf-kein-http 2 "keine http" aktor "$platzhalter" --url ftp://registry.invalid/repo

# Ziel cloudsmith (ADR-0049): dieselben Pruefungen mit CLOUDSMITH_USERNAME und CLOUDSMITH_API_KEY; GITHUB_* ersetzt sie nicht.
BEN_VAR=CLOUDSMITH_USERNAME; GEH_VAR=CLOUDSMITH_API_KEY
neues c-gut 'version = "0.1.0"' ja
lauf c-gut 0 "Ziel cloudsmith" dienst "$platzhalter" --ziel cloudsmith --url http://registry.invalid:8080/repo
grep -q '^docker run --rm -e CLOUDSMITH_USERNAME -e CLOUDSMITH_API_KEY tdswire-publish:lokal gradle .* publishMavenPublicationToCloudsmithRepository -PtdswireCloudsmithUrl=http://registry.invalid:8080/repo$' "$tmp/c-gut.docker" \
  || pruefe "c-gut: der run-Aufruf hat nicht die erwartete Form ($(grep '^docker run' "$tmp/c-gut.docker"))"
if grep -q "GITHUB_" "$tmp/c-gut.docker"; then pruefe "c-gut: der run-Aufruf nennt eine Umgebung des GitHub-Ziels"; fi
if grep -q "$platzhalter" "$tmp/c-gut.docker" "$tmp/c-gut.aus"; then pruefe "c-gut: der Wert des Schluessels steht in der Ausgabe oder im docker-Aufruf"; fi
: > "$tmp/c-gut.docker"
PUBLISH_DOCKER_NETZ=probe PATH="$tmp/bin:$PATH" DOCKER_LOG="$tmp/c-gut.docker" PUBLISH_WURZEL="$tmp/c-gut" \
  CLOUDSMITH_USERNAME=dienst CLOUDSMITH_API_KEY="$platzhalter" bash "$skript" --ziel cloudsmith --url http://registry.invalid:8080/repo > /dev/null 2>&1 \
  || pruefe "c-netz: Exit ungleich 0"
grep -q '^docker run --rm --network probe -e CLOUDSMITH_USERNAME' "$tmp/c-gut.docker" || pruefe "c-netz: run ohne --network probe"

# Ohne --url: das Standardziel von Cloudsmith, der Schluessel muss kein Platzhalter sein und steht nirgends.
neues c-standard 'version = "0.2.0"' ja
lauf c-standard 0 "" dienst "cl_echter-schluessel-form" --ziel cloudsmith
if grep -q "cl_echter-schluessel-form" "$tmp/c-standard.docker" "$tmp/c-standard.aus"; then pruefe "c-standard: der Wert des Schluessels steht in der Ausgabe oder im docker-Aufruf"; fi
grep -q 'publishMavenPublicationToCloudsmithRepository -PtdswireCloudsmithUrl=https://maven.cloudsmith.io/pt9912/tdswire/$' "$tmp/c-standard.docker" \
  || pruefe "c-standard: ohne --url ist das Ziel nicht das Standardziel von Cloudsmith"

neues c-snapshot 'version = "0.1.0-SNAPSHOT"' ja
lauf c-snapshot 1 "endet auf -SNAPSHOT" dienst "$platzhalter" --ziel cloudsmith
neues c-tag-fehlt 'version = "0.1.0"' nein
lauf c-tag-fehlt 1 "Tag v0.1.0 besteht nicht" dienst "$platzhalter" --ziel cloudsmith
neues c-tag-alt 'version = "0.1.0"' alt
lauf c-tag-alt 1 "nicht auf HEAD" dienst "$platzhalter" --ziel cloudsmith
neues c-baum 'version = "0.1.0"' ja
echo x > "$tmp/c-baum/neu.txt"
lauf c-baum 1 "nicht sauber" dienst "$platzhalter" --ziel cloudsmith
neues c-schluessel-fehlt 'version = "0.1.0"' ja
lauf c-schluessel-fehlt 1 "CLOUDSMITH_API_KEY ist leer" dienst "" --ziel cloudsmith
neues c-benutzer-fehlt 'version = "0.1.0"' ja
lauf c-benutzer-fehlt 1 "CLOUDSMITH_USERNAME ist leer" "" "$platzhalter" --ziel cloudsmith
neues c-fremdes-ziel 'version = "0.1.0"' ja
lauf c-fremdes-ziel 1 "dafuer gilt nur ein Platzhalter" dienst "cl_echter-schluessel-form" --ziel cloudsmith --url http://anderer-host.invalid/repo
neues c-platzhalter-leer 'version = "0.1.0"' ja
lauf c-platzhalter-leer 1 "dafuer gilt nur ein Platzhalter" dienst "PLATZHALTER-" --ziel cloudsmith --url http://anderer-host.invalid/repo

# Die Zugangsdaten des anderen Ziels reichen nicht: GitHub-Werte ohne Cloudsmith-Werte brechen vor Docker, und umgekehrt.
neues kreuz-c 'version = "0.1.0"' ja
: > "$tmp/kreuz-c.docker"; ist=0
GITHUB_ACTOR=aktor GITHUB_TOKEN="$platzhalter" PATH="$tmp/bin:$PATH" DOCKER_LOG="$tmp/kreuz-c.docker" PUBLISH_WURZEL="$tmp/kreuz-c" \
  bash "$skript" --ziel cloudsmith > "$tmp/kreuz-c.aus" 2>&1 || ist=$?
[ "$ist" -eq 1 ] || pruefe "kreuz-c: Exit $ist statt 1"
grep -q "CLOUDSMITH_USERNAME ist leer" "$tmp/kreuz-c.aus" || pruefe "kreuz-c: Meldung fehlt"
[ ! -s "$tmp/kreuz-c.docker" ] || pruefe "kreuz-c: docker wurde gerufen"
neues kreuz-g 'version = "0.1.0"' ja
: > "$tmp/kreuz-g.docker"; ist=0
CLOUDSMITH_USERNAME=dienst CLOUDSMITH_API_KEY="$platzhalter" PATH="$tmp/bin:$PATH" DOCKER_LOG="$tmp/kreuz-g.docker" PUBLISH_WURZEL="$tmp/kreuz-g" \
  bash "$skript" > "$tmp/kreuz-g.aus" 2>&1 || ist=$?
[ "$ist" -eq 1 ] || pruefe "kreuz-g: Exit $ist statt 1"
grep -q "GITHUB_ACTOR ist leer" "$tmp/kreuz-g.aus" || pruefe "kreuz-g: Meldung fehlt"
[ ! -s "$tmp/kreuz-g.docker" ] || pruefe "kreuz-g: docker wurde gerufen"

# Unbekanntes Ziel und fehlender Wert: Exit 2, kein docker.
lauf ziel-unbekannt 2 "weder github noch cloudsmith" dienst "$platzhalter" --ziel maven-central
lauf ziel-ohne-wert 2 "Aufruf" dienst "$platzhalter" --ziel
BEN_VAR=GITHUB_ACTOR; GEH_VAR=GITHUB_TOKEN

# Zweite Fundstellen (Kopplung): das Standardziel in publish.sh gleich der Voreinstellung in build.gradle.kts, und die
# Zeile `version = "..."` in build.gradle.kts hat die Form, die publish.sh liest.
bgk="$wurzel_repo/build.gradle.kts"
if [ -f "$bgk" ]; then
  in_skript=$(sed -n 's/^ *standard_url="\(.*\)"$/\1/p' "$skript" | tr '\n' ' ')
  in_gradle=$(sed -n 's/.*getOrElse("\(https:[^"]*\)").*/\1/p' "$bgk" | tr '\n' ' ')
  { [ -n "$in_skript" ] && [ "$in_skript" = "$in_gradle" ]; } || pruefe "Standardziele: publish.sh [$in_skript] und build.gradle.kts [$in_gradle] unterscheiden sich (Reihenfolge github, cloudsmith)"
  for t in publishMavenPublicationToRegistryRepository:'name = "registry"' publishMavenPublicationToCloudsmithRepository:'name = "cloudsmith"'; do
    grep -q "${t%%:*}" "$skript" && grep -q "${t#*:}" "$bgk" || pruefe "Aufgabe ${t%%:*}: publish.sh nennt sie nicht oder build.gradle.kts hat das Repository nicht (${t#*:})"
  done
  [ -n "$(sed -n 's/^version = "\(.*\)"$/\1/p' "$bgk")" ] || pruefe "build.gradle.kts hat keine Zeile version = \"...\" in der Form, die publish.sh liest"
else
  pruefe "build.gradle.kts liegt nicht neben tools/ (die Stage skript-selbsttest kopiert sie)"
fi

# `publish`, `publish-vorschau` und `publish-cloudsmith` stehen in keiner Gate-Liste und nicht im E2E-Durchgang. abwesend <wurzel>: Befund-Zahl.
abwesend() {
  w="$1"; n=0
  for f in "$w"/tools/mk/*.mk "$w"/harness/mk/*.mk "$w/Makefile" "$w/repo.mk"; do
    [ -f "$f" ] || continue
    if grep -E '^(GATE_CHECKS|GATE_KLASSE_[a-z]+|GATE_STAGE)[[:space:]]*\+?=.*(^|[[:space:]=])publish' "$f" > /dev/null; then n=$((n + 1)); fi
  done
  if [ -f "$w/tools/mk/e2e.mk" ] && grep -q 'publish' "$w/tools/mk/e2e.mk"; then n=$((n + 1)); fi
  echo "$n"
}
[ "$(abwesend "$wurzel_repo")" -eq 0 ] || pruefe "ein Target publish steht in einer Gate-Liste oder in tools/mk/e2e.mk"
[ -f "$wurzel_repo/tools/mk/publish.mk" ] || pruefe "tools/mk/publish.mk fehlt"
# Gegenprobe der Abwesenheitspruefung an einer Kopie: je eine Zeile in einer Gate-Liste und in e2e.mk macht sie rot.
mkdir -p "$tmp/mk1/tools/mk" "$tmp/mk2/tools/mk" "$tmp/mk3/tools/mk"
cp "$wurzel_repo/tools/mk/publish.mk" "$tmp/mk1/tools/mk/"
cp "$wurzel_repo/tools/mk/publish.mk" "$tmp/mk2/tools/mk/"
cp "$wurzel_repo/tools/mk/publish.mk" "$tmp/mk3/tools/mk/"
echo 'GATE_KLASSE_werkzeug += publish-cloudsmith' >> "$tmp/mk3/tools/mk/publish.mk"
echo 'GATE_CHECKS += publish-vorschau' >> "$tmp/mk1/tools/mk/publish.mk"
echo 'e2e-stufe: publish' > "$tmp/mk2/tools/mk/e2e.mk"
[ "$(abwesend "$tmp/mk1")" -eq 1 ] || pruefe "Gegenprobe: GATE_CHECKS += publish-vorschau wird nicht gefunden"
[ "$(abwesend "$tmp/mk3")" -eq 1 ] || pruefe "Gegenprobe: GATE_KLASSE_werkzeug += publish-cloudsmith wird nicht gefunden"
[ "$(abwesend "$tmp/mk2")" -eq 1 ] || pruefe "Gegenprobe: publish in e2e.mk wird nicht gefunden"

[ "$fehler" -eq 0 ] || exit 1
echo "publish-selbsttest: ok (Ziel github: guter Fall und Standardziel, Docker-Netz, elf rote Faelle und drei Faelle mit fremdem Ziel vor Docker, annotierter Tag, drei Aufruffehler; Ziel cloudsmith: guter Fall, Standardziel, Docker-Netz, neun rote Faelle, zwei Zugangsdaten des anderen Ziels, zwei Aufruffehler; Standardziele, Aufgaben und Versionszeile gegen build.gradle.kts, Abwesenheit in Gate-Listen und e2e.mk mit drei Gegenproben)"
