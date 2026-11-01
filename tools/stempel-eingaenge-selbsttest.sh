#!/usr/bin/env bash
# tools/stempel-eingaenge-selbsttest.sh - belegt an Mutationen, dass tools/stempel-eingaenge-pruefung.sh greift
# (ADR-0024 Entscheidung 10, TDS-QA-06). Aufrufer: tools/mk/stempel.mk (Target stempel-eingaenge).
#
# Zusage: Die Pruefung ist auf dem echten Bestand gruen. Eine COPY-Zeile ohne Listeneintrag in einer Stage jeder Klasse, in
# einer vererbenden Vorgaenger-Stage, in einer Stage, aus der eine Stage der Klasse mit `COPY --from=` holt, im Dockerfile
# von referenztest-dotnet und hinter der letzten Stage, jede nicht erkannte Form von COPY, ADD, RUN --mount und FROM, ein
# entfernter Listeneintrag, ein Link- oder Make-Eingang ohne Eintrag und eine Scan-Wurzel von .d-check.yml ohne Eintrag machen
# sie rot; die Kontrollfaelle (COPY ausserhalb der Klassen, COPY --from ohne Quellen, Quelle unter einem Eintrag, Link im
# Codeblock) bleiben gruen. Ohne diese Rot-Faelle gilt der Selbsttest als nicht gelaufen: er endet dann mit Exit 1.
# Aufbau: Jeder Fall laeuft in einem kleinen Wegwerf-Repo (Kopien des Dockerfile, der .d-check.yml und der echten Listen, ein
# paar Dateien), nicht im ganzen Repo; die Ableitung gegen das echte Repo fuehrt das Target mit der Pruefung selbst aus. Gruppen
# unabhaengiger Faelle laufen als Hintergrundprozesse; ausgegeben werden nur rote Faelle und eine Summe. Er schreibt nur in ein
# temporaeres Verzeichnis.
# Voraussetzung: bash ab 4.3, git und GNU-Werkzeuge (sed, awk, grep, sort); getestet nur unter Linux.
# Laufzeit: Einzelmessung 2026-11-01, Lastmittel und Dauer im Kopf von tools/mk/stempel.mk; kalt (leerer Dateicache) nicht
# gemessen, das braucht Root.
set -u
export LC_ALL=C
cd "$(git rev-parse --show-toplevel 2>/dev/null)" || { echo "stempel-eingaenge-selbsttest: kein Git-Arbeitsbaum" >&2; exit 1; }
ECHT=$(pwd)
PRUEFUNG=${STEMPEL_PRUEFUNG:-tools/stempel-eingaenge-pruefung.sh}
case $PRUEFUNG in /*) PRUEFUNG_ABS=$PRUEFUNG ;; *) PRUEFUNG_ABS=$ECHT/$PRUEFUNG ;; esac
T=$(mktemp -d "${TMPDIR:-/tmp}/stempel-eingaenge.XXXXXX") || exit 1
trap 'rm -rf "$T"' EXIT
export GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_NOSYSTEM=1 GIT_AUTHOR_NAME=Test GIT_AUTHOR_EMAIL=test@example.invalid GIT_COMMITTER_NAME=Test GIT_COMMITTER_EMAIL=test@example.invalid
LAEUFE=0
FEHLER=0
FIXDIR=
DOCKERFILE_ECHT=Dockerfile
DOTNET_ECHT=referenztest-dotnet/Dockerfile

rot() { printf 'ROT   %s\n' "$*"; FEHLER=$((FEHLER + 1)); }

# fixture <verz>: kleines Repo mit den Eingaengen der Pruefung; Link-Ziele liegen unter src und docs.
fixture() {
	local f="$T/$1"
	mkdir -p "$f/tools/stempel" "$f/referenztest-dotnet" "$f/docs" "$f/src/main/kotlin/io/github/pt9912/tdswire/domain"
	cp "$ECHT/Dockerfile" "$f/Dockerfile"
	cp "$ECHT/referenztest-dotnet/Dockerfile" "$f/referenztest-dotnet/Dockerfile"
	cp "$ECHT/.d-check.yml" "$f/.d-check.yml"
	cp "$ECHT"/tools/stempel/*.eingaenge "$f/tools/stempel/"
	mkdir -p "$f/.claude/agents" "$f/.harness/skills"
	printf '# Fixture\n\n[Skill](../.harness/skills/reviewer.md)\n' > "$f/docs/a.md"
	printf '# R\n' > "$f/README.md"
	printf '# A\n' > "$f/AGENTS.md"
	printf '# P\n' > "$f/.claude/agents/p.md"
	printf '# S\n' > "$f/.harness/skills/reviewer.md"
	printf 'class Andere\n' > "$f/src/main/Andere.kt"
	printf 'class SqlType\n' > "$f/src/main/kotlin/io/github/pt9912/tdswire/domain/SqlType.kt"
	git -C "$f" init -q --template=
	git -C "$f" add -A
	FIXDIR=$f
}

# lauf <name> <erwartet 0|1> <muster|-> [VAR=wert]...: Pruefung im Fixture mit Umgebung starten, Exit-Code und Muster pruefen.
lauf() {
	local name=$1 erwartet=$2 muster=$3 aus rc
	shift 3
	aus=$(cd "$FIXDIR" && env "${@:-STEMPEL_X=1}" timeout 20 bash "$PRUEFUNG_ABS" 2>&1 < /dev/null)
	rc=$?
	LAEUFE=$((LAEUFE + 1))
	if [ "$rc" -ne "$erwartet" ]; then
		rot "$name: Exit $rc, erwartet $erwartet ($(printf '%s' "$aus" | head -n 2 | tr '\n' ' '))"
	elif [ "$erwartet" = 0 ] && [ "$(printf '%s\n' "$aus" | grep -c '')" != 1 ]; then
		rot "$name: Exit 0, aber die Ausgabe ist nicht genau eine Zeile ($(printf '%s' "$aus" | head -n 2 | tr '\n' ' '))"
	elif [ "$muster" != - ] && ! printf '%s' "$aus" | grep -q -F -- "$muster"; then
		rot "$name: Ausgabe nennt \"$muster\" nicht ($(printf '%s' "$aus" | head -n 2 | tr '\n' ' '))"
	fi
}

# docker_mit <name> <stage> <zeile>: Kopie des Dockerfile mit <zeile> hinter der FROM-Zeile der Stage -> $T/<name>.
docker_mit() {
	printf '%s\n' "$3" > "$T/einfuegung.$1"
	sed "/^FROM .* AS $2\$/r $T/einfuegung.$1" "$ECHT/$DOCKERFILE_ECHT" > "$T/$1"
	cmp -s "$ECHT/$DOCKERFILE_ECHT" "$T/$1" && rot "$1: Stage $2 nicht gefunden, Mutation nicht eingefuegt"
}

# listen_ohne <verz> <klasse> <eintrag>: Kopie der Listen ohne den Eintrag.
listen_ohne() {
	mkdir -p "$T/$1"
	cp "$ECHT"/tools/stempel/werkzeug.eingaenge "$ECHT"/tools/stempel/tests.eingaenge "$T/$1/"
	grep -v -x -F -- "$3" "$ECHT/tools/stempel/$2.eingaenge" > "$T/$1/$2.eingaenge"
	cmp -s "$ECHT/tools/stempel/$2.eingaenge" "$T/$1/$2.eingaenge" && rot "$1: Eintrag $3 steht nicht in $2"
}

# mit_datei <name> <erwartet> <muster|-> <datei> <inhalt> [VAR=wert]...: Datei im Fixture anlegen, Pruefung, Datei entfernen
mit_datei() {
	local name=$1 erwartet=$2 muster=$3 datei=$4 inhalt=$5
	shift 5
	mkdir -p "$FIXDIR/$(dirname "$datei")"
	printf '%s\n' "$inhalt" > "$FIXDIR/$datei"
	lauf "$name" "$erwartet" "$muster" "$@"
	rm -f "$FIXDIR/$datei"
}

# --- Gruppe 1: COPY-Quellen im Dockerfile -------------------------------------------------------------------------------------
h_docker() {
	fixture h1
	lauf "Bestand gruen" 0 "ok"
	local stage
	for stage in test compile detekt build-jar smoke deps basis; do
		docker_mit "m-$stage" "$stage" "COPY neu.txt neu.txt"
		lauf "tests: COPY neu.txt in Stage $stage" 1 "kein Eintrag deckt 'neu.txt'" "STEMPEL_DOCKERFILE=$T/m-$stage"
	done
	for stage in detekt-selbsttest skript-selbsttest kopplung-zahlen; do
		docker_mit "m-$stage" "$stage" "COPY neu.txt neu.txt"
		lauf "werkzeug: COPY neu.txt in Stage $stage" 1 "werkzeug.eingaenge: kein Eintrag deckt 'neu.txt'" "STEMPEL_DOCKERFILE=$T/m-$stage"
	done
	docker_mit m-kette kopplung-zahlen $'COPY a.txt \\\n  # Kommentar in der Fortsetzung\n  b.txt dest/'
	lauf "Fortsetzungszeile mit Kommentar: zwei Quellen" 1 "'b.txt'" "STEMPEL_DOCKERFILE=$T/m-kette"
	# COPY --from=<stage> holt die Quellen der Quell-Stage mit
	docker_mit m-quelle testkeys "COPY neu.txt neu.txt"
	lauf "COPY --from=testkeys: Quelle in der Quell-Stage" 1 "kein Eintrag deckt 'neu.txt'" "STEMPEL_DOCKERFILE=$T/m-quelle"
	# zyklisches COPY --from (Tiefengrenze 10, Befund) und unbekannte Stage
	docker_mit zy1 compile "COPY --from=test /a /a"
	printf '%s\n' 'COPY --from=compile /b /b' > "$T/einfuegung.zy"
	sed '/^FROM deps AS test$/r '"$T/einfuegung.zy" "$T/zy1" > "$T/m-zyklus"
	lauf "zyklisches COPY --from ist ein Befund" 1 "bildet einen Zyklus" "STEMPEL_DOCKERFILE=$T/m-zyklus"
	docker_mit k-unbekannt test "COPY --from=gibt-es-nicht /a /a"
	lauf "Kontrolle: COPY --from einer unbekannten Stage (externes Image)" 0 - "STEMPEL_DOCKERFILE=$T/k-unbekannt"
	# Kontrollfaelle
	docker_mit k-ausserhalb spec-abdeckung "COPY neu.txt neu.txt"
	lauf "Kontrolle: COPY in einer Stage ausserhalb der Klassen" 0 - "STEMPEL_DOCKERFILE=$T/k-ausserhalb"
	docker_mit k-from test "COPY --from=testkeys /neu.txt /neu.txt"
	lauf "Kontrolle: COPY --from= einer Stage ohne Quellen" 0 - "STEMPEL_DOCKERFILE=$T/k-from"
	docker_mit k-unter test "COPY src/main/neu.kt neu.kt"
	lauf "Kontrolle: Quelle unter dem Eintrag src" 0 - "STEMPEL_DOCKERFILE=$T/k-unter"
	docker_mit k-glob kopplung-zahlen "COPY tools/*.sh tools/"
	lauf "Kontrolle: Platzhalter unter dem Eintrag tools" 0 - "STEMPEL_DOCKERFILE=$T/k-glob"
	docker_mit m-glob test "COPY tools/*.sh tools/"
	lauf "tests: Platzhalter unter tools ohne Eintrag" 1 "kein Eintrag deckt 'tools/*.sh'" "STEMPEL_DOCKERFILE=$T/m-glob"
	docker_mit k-cache test "RUN --mount=type=cache,target=/x true"
	lauf "Kontrolle: RUN --mount=type=cache ist kein Eingang" 0 - "STEMPEL_DOCKERFILE=$T/k-cache"
	# nicht erkannte Formen sind Befunde
	local i=0 name zeile muster
	while IFS='|' read -r name zeile muster; do
		i=$((i + 1))
		docker_mit "f$i" test "$zeile"
		lauf "Befund: $name" 1 "$muster" "STEMPEL_DOCKERFILE=$T/f$i"
	done << 'EOF'
COPY .|COPY . .|COPY . holt den ganzen Kontext
Option --chmod|COPY --chmod=755 src x|nicht erkannte Option --chmod=755
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
'..' in der Quelle|COPY src/../docs/x.md x.md|in der Quelle src/../docs/x.md
EOF
	# COPY . bleibt Befund, auch wenn die Liste `.` fuehrt.
	docker_mit m-punkt test "COPY . ."
	mkdir -p "$T/l-punkt"
	cp "$ECHT"/tools/stempel/*.eingaenge "$T/l-punkt/"
	printf '.\n' >> "$T/l-punkt/tests.eingaenge"
	lauf "COPY . mit Eintrag . bleibt Befund" 1 "COPY . holt den ganzen Kontext" "STEMPEL_DOCKERFILE=$T/m-punkt" "STEMPEL_LISTEN=$T/l-punkt"
	# Stages
	{ cat "$ECHT/$DOCKERFILE_ECHT"; printf '\nFROM scratch AS neu\n'; } > "$T/m-letzte"
	lauf "andere letzte Stage als kompatibilitaetsserver" 1 "letzte Stage" "STEMPEL_DOCKERFILE=$T/m-letzte"
	sed 's/^FROM deps AS /FROM DEPS AS /' "$ECHT/$DOCKERFILE_ECHT" > "$T/k-gross"
	lauf "Kontrolle: FROM DEPS (Grossschreibung) vererbt weiter" 0 - "STEMPEL_DOCKERFILE=$T/k-gross"
	printf '%s\n' 'COPY neu.txt neu.txt' > "$T/einfuegung.gross"
	sed '/^FROM basis AS deps$/r '"$T/einfuegung.gross" "$T/k-gross" > "$T/m-gross"
	lauf "FROM DEPS vererbt: COPY in deps wird ueber compile gesehen" 1 "kein Eintrag deckt 'neu.txt'" "STEMPEL_DOCKERFILE=$T/m-gross"
	sed 's/^FROM deps AS test$/FROM --platform=linux\/amd64 deps AS test/; s/^FROM deps AS compile$/FROM --platform=linux\/amd64 deps AS compile/' "$ECHT/$DOCKERFILE_ECHT" > "$T/k-plattform"
	lauf "Kontrolle: FROM --platform= vererbt weiter" 0 - "STEMPEL_DOCKERFILE=$T/k-plattform"
	sed '/^FROM basis AS deps$/r '"$T/einfuegung.gross" "$T/k-plattform" > "$T/m-plattform"
	lauf "FROM --platform=: COPY in deps wird gesehen" 1 "kein Eintrag deckt 'neu.txt'" "STEMPEL_DOCKERFILE=$T/m-plattform"
	sed 's/^FROM deps AS compile$/ARG B=deps\nFROM ${B} AS compile/' "$ECHT/$DOCKERFILE_ECHT" > "$T/m-variable"
	lauf "FROM mit Variable ist ein Befund" 1 "FROM mit Variable" "STEMPEL_DOCKERFILE=$T/m-variable"
	sed 's/^FROM deps AS smoke$/FROM deps AS rauch/' "$ECHT/$DOCKERFILE_ECHT" > "$T/m-umbenannt"
	lauf "Stage smoke umbenannt" 1 "Stage 'smoke' steht nicht im Dockerfile" "STEMPEL_DOCKERFILE=$T/m-umbenannt"
	# referenztest-dotnet
	listen_ohne l-dotnet tests referenztest-dotnet
	lauf "tests: Eintrag referenztest-dotnet entfernt" 1 "Stage test von $DOTNET_ECHT" "STEMPEL_LISTEN=$T/l-dotnet"
	sed '/^FROM restore AS test$/a COPY Neu Neu' "$ECHT/$DOTNET_ECHT" > "$T/m-dotnet"
	lauf "Kontrolle: COPY Neu im dotnet-Dockerfile liegt unter referenztest-dotnet" 0 - "STEMPEL_DOTNET_DOCKERFILE=$T/m-dotnet"
	lauf "dotnet-Kontext ausserhalb der Eintraege" 1 "Stage test von" "STEMPEL_DOTNET_DOCKERFILE=$T/m-dotnet" "STEMPEL_DOTNET_KONTEXT=fremd"
}

# --- Gruppe 2: ein Listeneintrag nach dem anderen entfernt ----------------------------------------------------------------------
h_eintraege() {
	fixture h2
	local n=0 paar
	for paar in tests:src tests:LICENSE tests:config tests:tools/stempel.sh tests:tools/stempel tests:Dockerfile tests:.dockerignore \
		tests:tools/mk/referenztest-dotnet.mk tests:Makefile tests:repo.mk tests:tools/mk/builder.mk tests:tools/stempel-liste.sh tests:tools/stempel-gruppe.sh tests:tools/mk/stempel.mk \
		werkzeug:README.md werkzeug:AGENTS.md werkzeug:.claude/agents werkzeug:docs werkzeug:harness werkzeug:tools \
		werkzeug:Makefile werkzeug:.harness/baseline werkzeug:.harness/skills werkzeug:.d-check.yml werkzeug:d-check.mk \
		werkzeug:config werkzeug:gradle/libs.versions.toml werkzeug:referenztest-dotnet/Dockerfile \
		werkzeug:src/testserver/kotlin/io/github/pt9912/tdswire/testserver/IntegrationServer.kt tests:tools/jackson-sperre.sh \
		stempel:tools/stempel-gruppe.sh stempel:tools/gate-zuordnung.sh stempel:tools/mk stempel:harness/mk stempel:d-check.mk stempel:Dockerfile stempel:.d-check.yml stempel:tools/stempel stempel:Makefile stempel:repo.mk; do
		n=$((n + 1))
		listen_ohne "l$n" "${paar%%:*}" "${paar#*:}"
		lauf "${paar%%:*}.eingaenge ohne ${paar#*:}" 1 "${paar%%:*}.eingaenge: kein Eintrag deckt" "STEMPEL_LISTEN=$T/l$n"
	done
	mkdir -p "$T/l-glob" "$T/l-fehlt"
	cp "$ECHT"/tools/stempel/*.eingaenge "$T/l-glob/"
	printf 'src/*\n' >> "$T/l-glob/tests.eingaenge"
	lauf "Eintrag mit Platzhalter" 1 "kein literaler Pfad" "STEMPEL_LISTEN=$T/l-glob"
	cp "$ECHT"/tools/stempel/werkzeug.eingaenge "$T/l-fehlt/"
	lauf "Liste fehlt" 1 "Eingangsliste fehlt" "STEMPEL_LISTEN=$T/l-fehlt"
}

# --- Gruppe 3: Dateien im Repo (Links, Make-Dateien, Markdown) und .d-check.yml ------------------------------------------------------
h_dateien() {
	fixture h3
	local andere=src/main/Andere.kt
	mit_datei "Link auf eine Quelldatei ausserhalb der Eintraege" 1 "kein Eintrag deckt '$andere' (Link in docs/tmp-link.md)" docs/tmp-link.md "[x](../$andere)"
	mit_datei "Link mit Anker" 1 "kein Eintrag deckt '$andere'" docs/tmp-link.md "[x](../$andere#abschnitt)"
	mit_datei "Kontrolle: Link auf eine Datei, die als Eintrag steht" 0 - docs/tmp-link.md "[x](../src/main/kotlin/io/github/pt9912/tdswire/domain/SqlType.kt)"
	mit_datei "Kontrolle: derselbe Link im Codeblock" 0 - docs/tmp-link.md $'```\n[x](../'"$andere"$')\n```'
	mit_datei "Kontrolle: Link auf eine nicht vorhandene Datei" 0 - docs/tmp-link.md "[x](../src/main/gibt-es-nicht.kt)"
	mit_datei "Kontrolle: Link auf eine URL" 0 - docs/tmp-link.md "[x](https://example.invalid/a.kt)"
	mit_datei "Kontrolle: Text mit ](ziel) ohne oeffnende Klammer" 0 - docs/tmp-link.md "„x](../$andere)“"
	mit_datei "Link verlaesst das Repo" 1 "verlaesst das Repo" docs/tmp-link.md "[x](../../a/b.kt)"
	mit_datei "Neue Make-Datei im Wurzelverzeichnis" 1 "kein Eintrag deckt 'extra.mk'" extra.mk "x:"
	mit_datei "Neues Fragment, das ein Gate der Klasse tests einhaengt" 1 "tests.eingaenge: kein Eintrag deckt 'tools/mk/neu.mk' (Make-Fragment der Klasse tests)" tools/mk/neu.mk $'neu:\n\t@false\nGATE_CHECKS += neu\nGATE_KLASSE_tests += neu'
	mit_datei "Kontrolle: Fragment der Klasse werkzeug unter tools/mk" 0 - tools/mk/neu.mk $'neu:\n\t@false\nGATE_CHECKS += neu\nGATE_KLASSE_werkzeug += neu'
	mit_datei "Neue Make-Datei unter src" 1 "kein Eintrag deckt 'src/main/neu.mk'" src/main/neu.mk "x:"
	mit_datei "Neue Markdown-Datei unter src" 1 "kein Eintrag deckt 'src/main/neu.md'" src/main/neu.md "# x"
	mit_datei "Kontrolle: Markdown unter .tmp ist durch scan.ignore ausgenommen" 0 - .tmp/neu.md "# x"
	sed 's|".tmp/\*\*", ||' "$ECHT/.d-check.yml" > "$T/d-ohne-tmp"
	cmp -s "$ECHT/.d-check.yml" "$T/d-ohne-tmp" && rot "d-ohne-tmp: Mutation nicht eingefuegt"
	mit_datei ".d-check.yml: scan.ignore ohne .tmp/** -> Markdown unter .tmp braucht einen Eintrag" 1 "kein Eintrag deckt '.tmp/neu.md'" .tmp/neu.md "# x" "STEMPEL_DCHECK=$T/d-ohne-tmp"
	sed 's|^  roots: \["\."\]|  roots: [".", "fremd"]|' "$ECHT/.d-check.yml" > "$T/d-wurzel"
	lauf "Kontrolle: weitere Wurzel ohne Markdown aendert nichts" 0 - "STEMPEL_DCHECK=$T/d-wurzel"
	sed 's|^  roots: \[spec, docs, harness\]|  roots: [spec, docs, harness, contracts]|' "$ECHT/.d-check.yml" > "$T/d-code"
	lauf ".d-check.yml: codepaths.roots um contracts erweitert" 1 "'contracts' (codepaths.roots)" "STEMPEL_DCHECK=$T/d-code"
	sed 's|^  roots: \["\."\]|  roots:\n    - "."|' "$ECHT/.d-check.yml" > "$T/d-form"
	lauf ".d-check.yml: scan.roots als Blockliste" 1 "nicht in der erkannten Form" "STEMPEL_DCHECK=$T/d-form"
}

GRUPPEN=(h_docker h_eintraege h_dateien)
for gr in "${GRUPPEN[@]}"; do
	(
		"$gr"
		echo "#ZAHLEN $LAEUFE $FEHLER"
	) > "$T/aus.$gr" 2>&1 &
done
# die Ableitung gegen das echte Repo, einmal
aus=$(bash "$PRUEFUNG_ABS" 2>&1 < /dev/null)
rc=$?
LAEUFE=$((LAEUFE + 1))
[ "$rc" -eq 0 ] || rot "echtes Repo: Exit $rc ($(printf '%s' "$aus" | head -n 2 | tr '\n' ' '))"
wait
for gr in "${GRUPPEN[@]}"; do
	grep -v '^#ZAHLEN' "$T/aus.$gr"
	z=$(grep '^#ZAHLEN' "$T/aus.$gr")
	if [ -z "$z" ]; then rot "Gruppe $gr ohne Ergebnis"; else set -- $z; LAEUFE=$(($LAEUFE + $2)); FEHLER=$(($FEHLER + $3)); fi
done

if [ "$LAEUFE" -lt 80 ]; then
	rot "nur $LAEUFE Faelle: der Selbsttest gilt als nicht gelaufen"
fi
if [ "$FEHLER" -ne 0 ]; then
	printf 'stempel-eingaenge-selbsttest: %s von %s Faellen rot\n' "$FEHLER" "$LAEUFE" >&2
	exit 1
fi
printf 'stempel-eingaenge-selbsttest: %s Faelle wie erwartet\n' "$LAEUFE"
