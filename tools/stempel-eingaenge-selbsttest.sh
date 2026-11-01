#!/usr/bin/env bash
# tools/stempel-eingaenge-selbsttest.sh - belegt an Mutationen, dass tools/stempel-eingaenge-pruefung.sh greift
# (ADR-0024 Entscheidung 10, TDS-QA-06). Aufrufer: tools/mk/stempel.mk (Target stempel-eingaenge).
#
# Zusage: Die Pruefung ist auf dem echten Bestand gruen. Eine COPY-Zeile ohne Listeneintrag in einer Stage jeder
# Klasse, in einer vererbenden Vorgaenger-Stage, im Dockerfile von referenztest-dotnet, jede nicht erkannte Form von
# COPY, ADD und RUN --mount, ein entfernter Listeneintrag und eine Scan-Wurzel von .d-check.yml ohne Eintrag machen sie
# rot; die Kontrollfaelle (COPY ausserhalb der Klassen, COPY --from, Quelle unter einem Eintrag) bleiben gruen. Ohne
# diese Rot-Faelle gilt der Selbsttest als nicht gelaufen: er endet dann mit Exit 1. Er schreibt nur in ein temporaeres
# Verzeichnis.
# Voraussetzung: bash ab 4.3, git und GNU-Werkzeuge (sed, cp -P --parents, xargs -0, grep -z); getestet nur unter Linux.
# Laufzeit (Einzelmessung 2026-11-01, ein Lauf bei Lastmittel 8 bis 16; kein Beleg fuer andere Hosts): Selbsttest mit 91 Faellen
# 27,3 s, die Pruefung allein 0,26 s. Ein Lauf mit leerem Dateicache ist nicht gemessen (braucht Root); der
# Lauf nutzt keinen Docker-Cache, warm und kalt unterscheiden sich hier nur durch den Dateicache des Hosts.
set -u
export LC_ALL=C
cd "$(git rev-parse --show-toplevel 2>/dev/null)" || { echo "stempel-eingaenge-selbsttest: kein Git-Arbeitsbaum" >&2; exit 1; }
ECHT=$(pwd)
PRUEFUNG=${STEMPEL_PRUEFUNG:-tools/stempel-eingaenge-pruefung.sh}
case $PRUEFUNG in /*) PRUEFUNG_ABS=$PRUEFUNG ;; *) PRUEFUNG_ABS=$ECHT/$PRUEFUNG ;; esac
T=$(mktemp -d "${TMPDIR:-/tmp}/stempel-eingaenge.XXXXXX") || exit 1
trap 'rm -rf "$T"' EXIT
FEHLER=0
LAEUFE=0

# lauf <name> <erwartet 0|1> <muster|-> [VAR=wert]...: Pruefung mit Umgebung starten, Exit-Code und Muster in der Ausgabe pruefen.
lauf() {
	local name=$1 erwartet=$2 muster=$3 aus rc
	shift 3
	aus=$(env "$@" bash "$PRUEFUNG" 2>&1 < /dev/null)
	rc=$?
	LAEUFE=$((LAEUFE + 1))
	if [ "$rc" -ne "$erwartet" ]; then
		printf 'ROT   %s: Exit %s, erwartet %s\n%s\n' "$name" "$rc" "$erwartet" "$aus" >&2
		FEHLER=$((FEHLER + 1))
	elif [ "$muster" != - ] && ! printf '%s' "$aus" | grep -q -F -- "$muster"; then
		printf 'ROT   %s: Ausgabe nennt "%s" nicht\n%s\n' "$name" "$muster" "$aus" >&2
		FEHLER=$((FEHLER + 1))
	else
		printf 'ok    %s (Exit %s)\n' "$name" "$rc"
	fi
}

# docker_mit <name> <stage> <zeile>: Kopie des Dockerfile mit <zeile> hinter der FROM-Zeile der Stage -> $T/<name>.
docker_mit() {
	printf '%s\n' "$3" > "$T/einfuegung"
	sed "/^FROM .* AS $2\$/r $T/einfuegung" "$DOCKERFILE_ECHT" > "$T/$1"
	cmp -s "$DOCKERFILE_ECHT" "$T/$1" && { printf 'ROT   %s: Stage %s nicht gefunden, Mutation nicht eingefuegt\n' "$1" "$2" >&2; FEHLER=$((FEHLER + 1)); }
}

# listen_ohne <verz> <klasse> <eintrag>: Kopie der Listen ohne den Eintrag.
listen_ohne() {
	mkdir -p "$T/$1"
	cp tools/stempel/werkzeug.eingaenge tools/stempel/tests.eingaenge "$T/$1/"
	grep -v -x -F -- "$3" "tools/stempel/$2.eingaenge" > "$T/$1/$2.eingaenge"
	cmp -s "tools/stempel/$2.eingaenge" "$T/$1/$2.eingaenge" && { printf 'ROT   %s: Eintrag %s steht nicht in %s\n' "$1" "$3" "$2" >&2; FEHLER=$((FEHLER + 1)); }
}

DOCKERFILE_ECHT=Dockerfile
DOTNET_ECHT=referenztest-dotnet/Dockerfile

lauf "Bestand gruen" 0 "ok" STEMPEL_X=1

# --- COPY ohne Listeneintrag: je Stage der Klassen, samt Vererbung ------------------------------------------------------
for stage in test compile detekt build-jar smoke build deps basis; do
	docker_mit "m-$stage" "$stage" "COPY neu.txt neu.txt"
	lauf "tests: COPY neu.txt in Stage $stage" 1 "kein Eintrag deckt 'neu.txt'" "STEMPEL_DOCKERFILE=$T/m-$stage"
done
for stage in detekt-selbsttest skript-selbsttest kopplung-zahlen; do
	docker_mit "m-$stage" "$stage" "COPY neu.txt neu.txt"
	lauf "werkzeug: COPY neu.txt in Stage $stage" 1 "werkzeug.eingaenge: kein Eintrag deckt 'neu.txt'" "STEMPEL_DOCKERFILE=$T/m-$stage"
done
docker_mit m-kette kopplung-zahlen $'COPY a.txt \\\n  # Kommentar in der Fortsetzung\n  b.txt dest/'
lauf "Fortsetzungszeile mit Kommentar: zwei Quellen" 1 "'b.txt'" "STEMPEL_DOCKERFILE=$T/m-kette"

# --- Kontrollfaelle: bleiben gruen --------------------------------------------------------------------------------------
docker_mit k-ausserhalb spec-abdeckung "COPY neu.txt neu.txt"
lauf "Kontrolle: COPY in einer Stage ausserhalb der Klassen" 0 - "STEMPEL_DOCKERFILE=$T/k-ausserhalb"
docker_mit k-from test "COPY --from=testkeys /neu.txt /neu.txt"
lauf "Kontrolle: COPY --from= ist kein Kontext-Eingang" 0 - "STEMPEL_DOCKERFILE=$T/k-from"
docker_mit k-unter test "COPY src/main/neu.kt neu.kt"
lauf "Kontrolle: Quelle unter dem Eintrag src" 0 - "STEMPEL_DOCKERFILE=$T/k-unter"
docker_mit k-glob kopplung-zahlen "COPY tools/*.sh tools/"
lauf "Kontrolle: Platzhalter unter dem Eintrag tools" 0 - "STEMPEL_DOCKERFILE=$T/k-glob"
docker_mit m-glob test "COPY tools/*.sh tools/"
lauf "tests: Platzhalter unter tools ohne Eintrag" 1 "kein Eintrag deckt 'tools/*.sh'" "STEMPEL_DOCKERFILE=$T/m-glob"

# --- nicht erkannte Formen sind Befunde -----------------------------------------------------------------------------------
i=0
while IFS='|' read -r name zeile muster; do
	i=$((i + 1))
	docker_mit "f$i" test "$zeile"
	lauf "Befund: $name" 1 "$muster" "STEMPEL_DOCKERFILE=$T/f$i"
done << 'EOF'
COPY .|COPY . .|COPY . holt den ganzen Kontext
COPY ./|COPY ./ x/|COPY . holt den ganzen Kontext
Option --chmod|COPY --chmod=755 src x|nicht erkannte Option --chmod=755
Option --parents|COPY --parents src x|nicht erkannte Option --parents
Heredoc COPY|COPY <<EOF /x|Heredoc
Heredoc RUN|RUN cat <<EOF|Heredoc
JSON-Form|COPY ["src", "x"]|JSON-Form
Variable|COPY $QUELLE x|Variable in der Quelle
Anfuehrungszeichen|COPY "src" x|Anfuehrungszeichen
URL mit ADD|ADD https://example.invalid/a.tgz /x|URL als Quelle
absolute Quelle|COPY /etc/hosts /x|absolute Quelle
RUN --mount bind|RUN --mount=type=bind,source=src,target=/x true|RUN --mount
RUN --mount ohne type|RUN --mount=target=/x true|RUN --mount
RUN --mount secret|RUN --mount=type=secret,id=a true|RUN --mount
ONBUILD COPY|ONBUILD COPY x y|ONBUILD
EOF
docker_mit k-cache test "RUN --mount=type=cache,target=/x true"
lauf "Kontrolle: RUN --mount=type=cache ist kein Eingang" 0 - "STEMPEL_DOCKERFILE=$T/k-cache"
# COPY . bleibt Befund, auch wenn die Liste `.` fuehrt.
docker_mit m-punkt test "COPY . ."
mkdir -p "$T/l-punkt"
cp tools/stempel/*.eingaenge "$T/l-punkt/"
printf '.\n' >> "$T/l-punkt/tests.eingaenge"
lauf "COPY . mit Eintrag . bleibt Befund" 1 "COPY . holt den ganzen Kontext" "STEMPEL_DOCKERFILE=$T/m-punkt" "STEMPEL_LISTEN=$T/l-punkt"

# --- letzte Stage, FROM-Formen, '..' -----------------------------------------------------------------------------------------
{ cat "$DOCKERFILE_ECHT"; printf '\nFROM scratch AS neu\n'; } > "$T/m-letzte"
lauf "andere letzte Stage als build" 1 "letzte Stage" "STEMPEL_DOCKERFILE=$T/m-letzte"
sed 's/^FROM deps AS /FROM DEPS AS /' "$DOCKERFILE_ECHT" > "$T/k-gross"
lauf "Kontrolle: FROM DEPS (Grossschreibung) vererbt weiter" 0 - "STEMPEL_DOCKERFILE=$T/k-gross"
sed '/^FROM deps AS /!b;s//FROM DEPS AS /' "$DOCKERFILE_ECHT" > "$T/k-gross2"
printf '%s\n' 'COPY neu.txt neu.txt' > "$T/einfuegung"
sed '/^FROM basis AS deps$/r '"$T/einfuegung" "$T/k-gross2" > "$T/m-gross"
lauf "FROM DEPS vererbt: COPY in deps wird ueber compile gesehen" 1 "kein Eintrag deckt 'neu.txt'" "STEMPEL_DOCKERFILE=$T/m-gross"
sed 's/^FROM deps AS test$/FROM --platform=linux\/amd64 deps AS test/' "$DOCKERFILE_ECHT" > "$T/k-plattform"
lauf "Kontrolle: FROM --platform= vererbt weiter" 0 - "STEMPEL_DOCKERFILE=$T/k-plattform"
sed '/^FROM basis AS deps$/r '"$T/einfuegung" "$T/k-plattform" > "$T/m-plattform"
sed 's/^FROM deps AS compile$/FROM --platform=linux\/amd64 deps AS compile/' "$T/m-plattform" > "$T/m-plattform2"
lauf "FROM --platform=: COPY in deps wird ueber compile gesehen" 1 "kein Eintrag deckt 'neu.txt'" "STEMPEL_DOCKERFILE=$T/m-plattform2"
sed 's/^FROM deps AS compile$/ARG B=deps\nFROM ${B} AS compile/' "$DOCKERFILE_ECHT" > "$T/m-variable"
lauf "FROM mit Variable ist ein Befund" 1 "FROM mit Variable" "STEMPEL_DOCKERFILE=$T/m-variable"
docker_mit m-punkte test "COPY src/../docs/x.md x.md"
lauf "'..' in der Quelle ist ein Befund" 1 "'..' in der Quelle" "STEMPEL_DOCKERFILE=$T/m-punkte"

# --- Stage fehlt ---------------------------------------------------------------------------------------------------------
sed 's/^FROM deps AS smoke$/FROM deps AS rauch/' "$DOCKERFILE_ECHT" > "$T/m-umbenannt"
lauf "Stage smoke umbenannt" 1 "Stage 'smoke' steht nicht im Dockerfile" "STEMPEL_DOCKERFILE=$T/m-umbenannt"

# --- referenztest-dotnet ---------------------------------------------------------------------------------------------------
listen_ohne l-dotnet tests referenztest-dotnet
lauf "tests: Eintrag referenztest-dotnet entfernt" 1 "Stage test von $DOTNET_ECHT" "STEMPEL_LISTEN=$T/l-dotnet"
sed '/^FROM restore AS test$/a COPY Neu Neu' "$DOTNET_ECHT" > "$T/m-dotnet"
lauf "Kontrolle: COPY Neu im dotnet-Dockerfile liegt unter referenztest-dotnet" 0 - "STEMPEL_DOTNET_DOCKERFILE=$T/m-dotnet"
lauf "dotnet-Kontext ausserhalb der Eintraege" 1 "Stage test von" "STEMPEL_DOTNET_DOCKERFILE=$T/m-dotnet" "STEMPEL_DOTNET_KONTEXT=fremd"

# --- Eintrag entfernt ------------------------------------------------------------------------------------------------------
n=0
for paar in tests:src tests:LICENSE tests:config tests:tools/stempel.sh tests:tools/stempel tests:Dockerfile tests:.dockerignore \
	tests:tools/mk/referenztest-dotnet.mk werkzeug:README.md werkzeug:AGENTS.md werkzeug:.claude/agents werkzeug:docs \
	werkzeug:harness werkzeug:tools werkzeug:Makefile werkzeug:.harness/baseline werkzeug:.d-check.yml werkzeug:config \
	werkzeug:gradle/libs.versions.toml werkzeug:src/test/kotlin/io/github/pt9912/tdswire/runtime/IntegrationServer.kt \
	werkzeug:d-check.mk werkzeug:referenztest-dotnet/Dockerfile werkzeug:.harness/skills \
	werkzeug:src/main/kotlin/io/github/pt9912/tdswire/domain/SqlType.kt \
	tests:Makefile tests:repo.mk tests:tools/mk/builder.mk tests:tools/stempel-liste.sh; do
	n=$((n + 1))
	listen_ohne "l$n" "${paar%%:*}" "${paar#*:}"
	lauf "${paar%%:*}.eingaenge ohne ${paar#*:}" 1 "${paar%%:*}.eingaenge: kein Eintrag deckt" "STEMPEL_LISTEN=$T/l$n"
done
mkdir -p "$T/l-glob"
cp tools/stempel/*.eingaenge "$T/l-glob/"
printf 'src/*\n' >> "$T/l-glob/tests.eingaenge"
lauf "Eintrag mit Platzhalter" 1 "kein literaler Pfad" "STEMPEL_LISTEN=$T/l-glob"
mkdir -p "$T/l-fehlt"
cp tools/stempel/werkzeug.eingaenge "$T/l-fehlt/"
lauf "Liste fehlt" 1 "Eingangsliste fehlt" "STEMPEL_LISTEN=$T/l-fehlt"

# --- .d-check.yml ------------------------------------------------------------------------------------------------------------
sed 's|^  roots: \["\."\]|  roots: [".", "fremd"]|' .d-check.yml > "$T/d-wurzel"
lauf "Kontrolle: weitere Wurzel ohne Markdown aendert nichts" 0 - "STEMPEL_DCHECK=$T/d-wurzel"
sed 's|^  roots: \[spec, docs, harness\]|  roots: [spec, docs, harness, contracts]|' .d-check.yml > "$T/d-code"
lauf ".d-check.yml: codepaths.roots um contracts erweitert" 1 "'contracts' (codepaths.roots)" "STEMPEL_DCHECK=$T/d-code"
sed 's|^  roots: \["\."\]|  roots:\n    - "."|' .d-check.yml > "$T/d-form"
lauf ".d-check.yml: scan.roots als Blockliste" 1 "nicht in der erkannten Form" "STEMPEL_DCHECK=$T/d-form"

# --- Links, Make-Dateien und Symlinks in einem Abbild des Arbeitsbaums --------------------------------------------------------------
mkdir "$T/abbild"
(git ls-files --cached --others --exclude-standard -z | grep -z -v '^\.claude/worktrees/' | xargs -0 cp -P --parents -t "$T/abbild" 2> /dev/null)
export GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_NOSYSTEM=1 GIT_AUTHOR_NAME=Test GIT_AUTHOR_EMAIL=test@example.invalid GIT_COMMITTER_NAME=Test GIT_COMMITTER_EMAIL=test@example.invalid
git -C "$T/abbild" init -q
git -C "$T/abbild" add -A
git -C "$T/abbild" commit -q -m abbild
# abbild_lauf <name> <erwartet> <muster|-> <datei> <inhalt>: Datei schreiben, Pruefung im Abbild, Zustand zuruecksetzen
abbild_lauf() {
	local name=$1 erwartet=$2 muster=$3 datei=$4 inhalt=$5 aus rc
	mkdir -p "$T/abbild/$(dirname "$datei")"
	printf '%s\n' "$inhalt" > "$T/abbild/$datei"
	aus=$(cd "$T/abbild" && env "${ABBILD_ENV[@]}" bash "$PRUEFUNG_ABS" 2>&1 < /dev/null)
	rc=$?
	git -C "$T/abbild" clean -fdq
	git -C "$T/abbild" checkout -q -- .
	LAEUFE=$((LAEUFE + 1))
	if [ "$rc" -ne "$erwartet" ]; then
		printf 'ROT   %s: Exit %s, erwartet %s\n%s\n' "$name" "$rc" "$erwartet" "$aus" >&2
		FEHLER=$((FEHLER + 1))
	elif [ "$muster" != - ] && ! printf '%s' "$aus" | grep -q -F -- "$muster"; then
		printf 'ROT   %s: Ausgabe nennt "%s" nicht\n%s\n' "$name" "$muster" "$aus" >&2
		FEHLER=$((FEHLER + 1))
	else
		printf 'ok    %s (Exit %s)\n' "$name" "$rc"
	fi
}
ABBILD_ENV=(STEMPEL_X=1)
ANDERE=$(git -C "$T/abbild" ls-files -- src/main | grep -v 'domain/SqlType.kt' | head -n 1)
[ -n "$ANDERE" ] || { echo "ROT   keine Quelldatei fuer den Linktest gefunden" >&2; FEHLER=$((FEHLER + 1)); }
abbild_lauf "Abbild: Bestand gruen" 0 "ok" "docs/leer.txt" "x"
abbild_lauf "Link auf eine Quelldatei ausserhalb der Eintraege" 1 "kein Eintrag deckt '$ANDERE' (Link in docs/tmp-link.md)" "docs/tmp-link.md" "[x](../$ANDERE)"
abbild_lauf "Link mit Anker auf eine Quelldatei ausserhalb der Eintraege" 1 "kein Eintrag deckt '$ANDERE'" "docs/tmp-link.md" "[x](../$ANDERE#abschnitt)"
abbild_lauf "Kontrolle: derselbe Link im Codeblock" 0 - "docs/tmp-link.md" $'```\n[x](../'"$ANDERE"$')\n```'
abbild_lauf "Kontrolle: Link auf eine nicht vorhandene Datei" 0 - "docs/tmp-link.md" "[x](../src/main/gibt-es-nicht.kt)"
abbild_lauf "Kontrolle: Link auf eine URL" 0 - "docs/tmp-link.md" "[x](https://example.invalid/a.kt)"
abbild_lauf "Kontrolle: Text mit ](ziel) ohne oeffnende Klammer" 0 - "docs/tmp-link.md" "„x](../$ANDERE)“"
abbild_lauf "Link verlaesst das Repo" 1 "verlaesst das Repo" "docs/tmp-link.md" "[x](../../a/b.kt)"
abbild_lauf "Kontrolle: Markdown unter .tmp ist durch scan.ignore ausgenommen" 0 - ".tmp/neu.md" "# x"
sed 's|".tmp/\*\*", ||' .d-check.yml > "$T/d-ohne-tmp"
cmp -s .d-check.yml "$T/d-ohne-tmp" && { echo "ROT   d-ohne-tmp: Mutation nicht eingefuegt" >&2; FEHLER=$((FEHLER + 1)); }
ABBILD_ENV=("STEMPEL_DCHECK=$T/d-ohne-tmp")
abbild_lauf ".d-check.yml: scan.ignore ohne .tmp/** -> Markdown unter .tmp braucht einen Eintrag" 1 "kein Eintrag deckt '.tmp/neu.md'" ".tmp/neu.md" "# x"
ABBILD_ENV=(STEMPEL_X=1)
abbild_lauf "Neue Make-Datei im Wurzelverzeichnis" 1 "kein Eintrag deckt 'extra.mk'" "extra.mk" "x:"
abbild_lauf "Neue Make-Datei unter src" 1 "kein Eintrag deckt 'src/main/neu.mk'" "src/main/neu.mk" "x:"
abbild_lauf "Neue Markdown-Datei unter src" 1 "kein Eintrag deckt 'src/main/neu.md'" "src/main/neu.md" "# x"

if [ "$LAEUFE" -lt 55 ]; then
	printf 'ROT   nur %s Laeufe: der Selbsttest gilt als nicht gelaufen\n' "$LAEUFE" >&2
	FEHLER=$((FEHLER + 1))
fi
if [ "$FEHLER" -ne 0 ]; then
	printf 'stempel-eingaenge-selbsttest: %s von %s Faellen rot\n' "$FEHLER" "$LAEUFE" >&2
	exit 1
fi
printf 'stempel-eingaenge-selbsttest: %s Faelle wie erwartet\n' "$LAEUFE"
