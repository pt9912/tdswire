#!/usr/bin/env bash
# tools/stempel-abbild-selbsttest.sh - die echten Eingangslisten gegen ein Abbild des Arbeitsbaums (ADR-0024 Entscheidung 10,
# TDS-QA-06). Aufrufer: tools/mk/stempel.mk (Target stempel-abbild).
#
# Zusage: In einem Wegwerf-Repo mit dem Inhalt dieses Arbeitsbaums (versioniert und unversioniert ohne Ignoriertes, Symlinks
# als Symlinks) und den echten Listen aendert die Aenderung einer Datei unter jedem Eintrag einer Liste den Eingangs-Hash
# ihrer Klasse, `pruefe` laeuft dann; der Image-Pin in d-check.mk betrifft `werkzeug` und `stempel`, nicht `tests`; ein anderer Basis-Image-
# Pin im Dockerfile betrifft beide. Das belegt die Kopplung "Datei im Eintrag -> Hash", nicht die Vollstaendigkeit der
# Listen (die prueft tools/stempel-eingaenge-pruefung.sh). Der Lauf kopiert den Arbeitsbaum und ist deshalb ein eigenes
# Target: noetig nach Aenderungen an tools/stempel*, tools/stempel/*.eingaenge oder dem Dockerfile, nicht bei jedem Gate-Lauf.
# Er beruehrt weder refs/notes/gates noch die Konfiguration dieses Repos. Voraussetzung: bash ab 4.3, git, GNU-Werkzeuge
# (cp -P --parents, xargs -0, grep -z, sed 0,/re/); getestet nur unter Linux.
# Laufzeit: Einzelmessung 2026-11-01, Lastmittel und Dauer im Kopf von tools/mk/stempel.mk.
set -u
export LC_ALL=C
cd "$(git rev-parse --show-toplevel 2>/dev/null)" || { echo "stempel-abbild: kein Git-Arbeitsbaum" >&2; exit 1; }
SKRIPT=$(cd "$(dirname "${STEMPEL_SKRIPT:-tools/stempel.sh}")" && pwd)/$(basename "${STEMPEL_SKRIPT:-tools/stempel.sh}")
[ -f "$SKRIPT" ] || { echo "stempel-abbild: $SKRIPT fehlt" >&2; exit 1; }
BASH_BIN=$(command -v bash)
ECHT=$(pwd)
schnappschuss() { { git -C "$ECHT" for-each-ref refs/notes; git -C "$ECHT" config --local --list; } 2>&1 | sha256sum | cut -d' ' -f1; }
VORHER=$(schnappschuss)
T=$(mktemp -d "${TMPDIR:-/tmp}/stempel-abbild.XXXXXX") || exit 1
trap 'rm -rf "$T"' EXIT
unset GIT_DIR GIT_WORK_TREE GIT_INDEX_FILE GIT_OBJECT_DIRECTORY GIT_COMMON_DIR GIT_NOTES_REF GIT_NOTES_REWRITE_REF
mkdir -p "$T/home"
printf '[init]\n\tdefaultBranch = main\n' > "$T/gitconfig"
export HOME="$T/home" GIT_CONFIG_GLOBAL="$T/gitconfig" GIT_CONFIG_NOSYSTEM=1 GIT_TERMINAL_PROMPT=0
export GIT_AUTHOR_NAME=Test GIT_AUTHOR_EMAIL=test@example.invalid GIT_COMMITTER_NAME=Test GIT_COMMITTER_EMAIL=test@example.invalid

LAEUFE=0
FEHLER=0
OUT=
ERR=
RC=
ERRF=$T/err
rot() { printf 'ROT   %s\n' "$*"; FEHLER=$((FEHLER + 1)); }
g() { git -C "$T/$1" "${@:2}"; }
S() { local d=$1; shift; OUT=$(cd "$T/$d" && "$BASH_BIN" "$SKRIPT" "$@" 2> "$ERRF" < /dev/null); RC=$?; ERR=$(cat "$ERRF"); }
erwarte() { # erwarte <name> <verz> <rc> <muster|-> <args>...
	local name=$1 d=$2 rc=$3 muster=$4
	shift 4
	S "$d" "$@"
	LAEUFE=$((LAEUFE + 1))
	if [ "$RC" != "$rc" ]; then rot "$name: Exit $RC statt $rc ($ERR)"
	elif [ "$muster" != - ] && ! printf '%s' "$ERR" | grep -q -F -- "$muster"; then rot "$name: stderr nennt \"$muster\" nicht: $ERR"; fi
}

abbild() { # abbild <verz>: Arbeitsbaum kopieren, committen, beide Stempel setzen
	mkdir "$T/$1"
	(cd "$ECHT" && git ls-files --cached --others --exclude-standard -z | grep -z -v '^\.claude/worktrees/' | xargs -0 cp -P --parents -t "$T/$1" 2> /dev/null)
	git -C "$T/$1" init -q --template=
	git -C "$T/$1" add -A
	git -C "$T/$1" commit -q -m abbild
	erwarte "Abbild $1: schreibe tests" "$1" 0 - schreibe tests
	erwarte "Abbild $1: schreibe werkzeug" "$1" 0 - schreibe werkzeug
	erwarte "Abbild $1: schreibe stempel" "$1" 0 - schreibe stempel
	erwarte "Abbild $1: pruefe tests" "$1" 0 - pruefe tests
	erwarte "Abbild $1: pruefe werkzeug" "$1" 0 - pruefe werkzeug
	erwarte "Abbild $1: pruefe stempel" "$1" 0 - pruefe stempel
}
aendere_eintrag() { # aendere_eintrag <verz> <eintrag>: eine Datei unter dem Eintrag aendern; ohne Datei: Symlink umbiegen
	local d=$1 e=$2 f
	f=$(g "$d" ls-files -s -- "$e" | grep -v '^120000' | head -n 1 | cut -f2)
	if [ -n "$f" ]; then
		printf '#x\n' >> "$T/$d/$f"
	else
		f=$(g "$d" ls-files -- "$e" | head -n 1)
		[ -n "$f" ] || return 1
		ln -sfn "$(readlink "$T/$d/$f")x" "$T/$d/$f"
	fi
	g "$d" add -A
	g "$d" commit -q -m "aendere $f"
}
je_eintrag() { # je_eintrag <klasse>
	local klasse=$1 d=abbild-$1 eintrag
	abbild "$d"
	while IFS= read -r eintrag; do
		eintrag=${eintrag%%#*}
		eintrag=$(printf '%s' "$eintrag" | tr -d '[:space:]')
		[ -n "$eintrag" ] || continue
		LAEUFE=$((LAEUFE + 1))
		if ! aendere_eintrag "$d" "$eintrag"; then rot "$klasse: unter dem Eintrag $eintrag liegt keine Datei"; continue; fi
		S "$d" pruefe "$klasse"
		if [ "$RC" != 1 ] || ! printf '%s' "$ERR" | grep -q "kein Beleg"; then rot "$klasse: Aenderung unter $eintrag laeuft nicht (Exit $RC: $ERR)"; fi
		g "$d" reset -q --hard HEAD~1
	done < "$ECHT/tools/stempel/$klasse.eingaenge"
}
pins() {
	local d=abbild-pins
	abbild "$d"
	sed -i 's/^DCHECK_DIGEST ?= sha256:\(.\)/DCHECK_DIGEST ?= sha256:0\1/' "$T/$d/d-check.mk"
	g "$d" commit -q -a -m "Pin geaendert"
	erwarte "werkzeug: Pin in d-check.mk geaendert -> Lauf" "$d" 1 "kein Beleg" pruefe werkzeug
	erwarte "tests: Pin in d-check.mk betrifft die Klasse nicht -> Ueberspringen" "$d" 0 - pruefe tests
	erwarte "stempel: Pin in d-check.mk geaendert -> Lauf" "$d" 1 "kein Beleg" pruefe stempel
	g "$d" reset -q --hard HEAD~1
	sed -i '0,/^FROM gradle:[^ ]*/s//&0/' "$T/$d/Dockerfile"
	g "$d" commit -q -a -m "Basis-Image geaendert"
	erwarte "tests: Basis-Image-Pin im Dockerfile geaendert -> Lauf" "$d" 1 "kein Beleg" pruefe tests
	erwarte "werkzeug: Basis-Image-Pin im Dockerfile geaendert -> Lauf" "$d" 1 "kein Beleg" pruefe werkzeug
	erwarte "stempel: Basis-Image-Pin im Dockerfile geaendert -> Lauf" "$d" 1 "kein Beleg" pruefe stempel
}

# Mindestzahl der Faelle je Gruppe: eine Gruppe, die nichts ausfuehrt, faellt auf (je Listeneintrag ein Fall plus die vier Abbild-Faelle).
declare -A MIN=([je_tests]=10 [je_werkzeug]=10 [je_stempel]=10 [pins]=10)
GRUPPEN=(je_tests je_werkzeug je_stempel pins)
for gr in "${GRUPPEN[@]}"; do
	(
		ERRF="$T/err.$BASHPID"
		case $gr in
			je_tests) je_eintrag tests ;;
			je_werkzeug) je_eintrag werkzeug ;;
			je_stempel) je_eintrag stempel ;;
			pins) pins ;;
		esac
		echo "#ZAHLEN $LAEUFE $FEHLER"
	) > "$T/aus.$gr" 2>&1 &
done
wait
for gr in "${GRUPPEN[@]}"; do
	grep -v '^#ZAHLEN' "$T/aus.$gr"
	z=$(grep '^#ZAHLEN' "$T/aus.$gr")
	if [ -z "$z" ]; then rot "Gruppe $gr ohne Ergebnis"; else set -- $z; [ "$2" -ge "${MIN[$gr]}" ] || rot "Gruppe $gr: $2 Faelle, mindestens ${MIN[$gr]} erwartet"; LAEUFE=$(($LAEUFE + $2)); FEHLER=$(($FEHLER + $3)); fi
done
[ "$(schnappschuss)" = "$VORHER" ] || rot "das Repo des Selbsttests wurde beruehrt (Notes-Refs, lokale Konfiguration)"
if [ "$LAEUFE" -lt 40 ]; then rot "nur $LAEUFE Faelle: der Selbsttest gilt als nicht gelaufen"; fi
if [ "$FEHLER" -ne 0 ]; then
	printf 'stempel-abbild: %s von %s Faellen rot\n' "$FEHLER" "$LAEUFE" >&2
	exit 1
fi
printf 'stempel-abbild: %s Faelle wie erwartet\n' "$LAEUFE"
