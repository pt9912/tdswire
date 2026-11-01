#!/usr/bin/env bash
# tools/stempel-gruppe-selbsttest.sh - Zusammenspiel von tools/stempel-gruppe.sh, tools/stempel.sh und den echten Listen
# (ADR-0024 Entscheidung 1, 5 und 8; TDS-QA-06). Aufrufer: tools/mk/stempel.mk (Target stempel-gruppe).
#
# Zusage: In einem Wegwerf-Repo mit dem Inhalt dieses Arbeitsbaums (Abbild wie tools/stempel-abbild-selbsttest.sh) und einem
# erfundenen `make`, das nur die Namen der gerufenen Gates aufzeichnet:
#   - ein erster Lauf beider Klassen ruft alle ihre Gates und schreibt beide Stempel; derselbe Eingang ein zweites Mal
#     ruft keines;
#   - eine Aenderung unter docs/ ruft die Gates von werkzeug, nicht die von tests; eine Aenderung unter src/ umgekehrt; eine
#     Aenderung am Dockerfile beide;
#   - ein rotes Gate beendet den Schritt mit Exit 1 und schreibt keinen Stempel; der naechste Lauf ruft die Gates wieder;
#   - ein Commit waehrend des Laufs (anderer Eingangs-Hash) schreibt keinen Stempel;
#   - FORCE=1 ruft die Gates trotz Stempel; ein schmutziger Eingangsbaum ruft sie und schreibt nichts, ohne den Schritt rot zu
#     machen; ein Trockenlauf (TROCKEN=1) ruft nichts und schreibt nichts; ein Aufruf ohne Gate oder mit unbekannter Klasse endet mit Exit 2.
# Das belegt die Verdrahtung der Gruppenschritte, nicht die Gates selbst. Er beruehrt weder refs/notes/gates noch die
# Konfiguration dieses Repos. Voraussetzung: wie tools/stempel-abbild-selbsttest.sh.
set -u
export LC_ALL=C
cd "$(git rev-parse --show-toplevel 2>/dev/null)" || { echo "stempel-gruppe-selbsttest: kein Git-Arbeitsbaum" >&2; exit 1; }
ECHT=$(pwd)
BASH_BIN=$(command -v bash)
T=$(mktemp -d "${TMPDIR:-/tmp}/stempel-gruppe.XXXXXX") || exit 1
trap 'rm -rf "$T"' EXIT
unset GIT_DIR GIT_WORK_TREE GIT_INDEX_FILE GIT_OBJECT_DIRECTORY GIT_COMMON_DIR GIT_NOTES_REF GIT_NOTES_REWRITE_REF
mkdir -p "$T/home"
printf '[init]\n\tdefaultBranch = main\n' > "$T/gitconfig"
export HOME="$T/home" GIT_CONFIG_GLOBAL="$T/gitconfig" GIT_CONFIG_NOSYSTEM=1 GIT_TERMINAL_PROMPT=0
export GIT_AUTHOR_NAME=Test GIT_AUTHOR_EMAIL=test@example.invalid GIT_COMMITTER_NAME=Test GIT_COMMITTER_EMAIL=test@example.invalid

# Das erfundene make: schreibt seine Argumente in $PROTOKOLL; ist $FAKE_ROT eines der Argumente, endet es mit Exit 2.
FAKE=$T/fake-make
cat > "$FAKE" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$PROTOKOLL"
if [ -n "${FAKE_COMMIT:-}" ]; then printf '\n# waehrend des Laufs\n' >> Dockerfile; git add -A; git commit -q -m waehrend; fi
for a in "$@"; do [ "$a" = "${FAKE_ROT:-}" ] && exit 2; done
exit 0
EOF
chmod +x "$FAKE"
export PROTOKOLL=$T/protokoll MAKE=$FAKE

W="gw1 gw2"
TS="gt1 gt2"
LAEUFE=0
FEHLER=0
rot() { printf 'ROT   %s\n' "$*"; FEHLER=$((FEHLER + 1)); }
g() { git -C "$R" "$@"; }
schritt() { # schritt <klasse> <rc> <gerufen|-> <name> [VAR=wert]...: Gruppenschritt laufen lassen
	local klasse=$1 soll=$2 gerufen=$3 name=$4 gates rc ist
	shift 4
	if [ "$klasse" = werkzeug ]; then gates=$W; else gates=$TS; fi
	: > "$PROTOKOLL"
	# shellcheck disable=SC2086
	(cd "$R" && env "$@" "$BASH_BIN" tools/stempel-gruppe.sh "$klasse" $gates > "$T/out" 2> "$T/err" < /dev/null)
	rc=$?
	ist=$(tr '\n' ' ' < "$PROTOKOLL" | sed 's/ $//')
	LAEUFE=$((LAEUFE + 1))
	[ "$rc" = "$soll" ] || rot "$name: Exit $rc statt $soll ($(head -c 200 "$T/err"))"
	if [ "$gerufen" = - ]; then [ -z "$ist" ] || rot "$name: Gates gerufen ($ist), erwartet keine"
	else [ "$ist" = "$gerufen" ] || rot "$name: gerufen '$ist' statt '$gerufen'"; fi
}
alle() { # alle <name> <w|-> <t|->: beide Klassen in der Reihenfolge werkzeug, tests
	schritt werkzeug 0 "$([ "$2" = w ] && echo "$W" || echo -)" "$1 werkzeug" X=1
	schritt tests 0 "$([ "$3" = t ] && echo "$TS" || echo -)" "$1 tests" X=1
}
neu_commit() { g add -A; g commit -q -m "$1"; }

R=$T/repo
mkdir "$R"
(cd "$ECHT" && git ls-files --cached --others --exclude-standard -z | grep -z -v '^\.claude/worktrees/' | xargs -0 cp -P --parents -t "$R" 2> /dev/null)
g init -q --template=
neu_commit abbild
alle "erster Lauf" w t
alle "zweiter Lauf, derselbe Eingang" - -
[ "$(g notes --ref=refs/notes/gates show HEAD 2> /dev/null | grep -c '^stempel ')" = 2 ] || rot "erster Lauf: nicht genau zwei Stempel an HEAD"
printf '\nx\n' >> "$R/docs/plan/adr/README.md" 2> /dev/null || printf 'x\n' >> "$R/docs/leer.md"
neu_commit docs
alle "docs geaendert" w -
printf '\n// x\n' >> "$(cd "$R" && git ls-files 'src/main/*.kt' | head -n 1 | sed "s|^|$R/|")"
neu_commit src
alle "src geaendert" - t
printf '\n# x\n' >> "$R/Dockerfile"
neu_commit dockerfile
alle "Dockerfile geaendert" w t
alle "Dockerfile, zweiter Lauf" - -

schritt tests 0 "$TS" "FORCE=1 trotz Stempel" FORCE=1
printf '\n# y\n' >> "$R/Dockerfile"
schritt werkzeug 0 "$W" "schmutziger Baum ruft" X=1
neu_commit dockerfile-2
# schmutzig: nichts geschrieben, der Schritt bleibt gruen
printf 'u\n' > "$R/src/unversioniert.txt"
schritt tests 0 "$TS" "unversionierte Datei in src" X=1
schritt tests 0 "$TS" "unversionierte Datei: nichts geschrieben" X=1
rm -f "$R/src/unversioniert.txt"
mkdir -p "$R/referenztest-dotnet/obj"
printf 'u\n' > "$R/referenztest-dotnet/obj/x"
schritt tests 0 "$TS" "obj/ unter referenztest-dotnet ruft" X=1
schritt tests 0 "$TS" "obj/: nichts geschrieben" X=1
rm -rf "$R/referenztest-dotnet/obj"
alle "nach dem Aufraeumen" w t
# rotes Gate: Exit 1, kein Stempel
printf '\n# z\n' >> "$R/Dockerfile"
neu_commit dockerfile-3
schritt tests 1 "$TS" "rotes Gate beendet den Schritt" FAKE_ROT=gt1
schritt tests 0 "$TS" "nach rotem Lauf kein Stempel, Gates laufen wieder" X=1
schritt tests 0 - "danach Stempel gesetzt" X=1
# Aenderung waehrend des Laufs: der uebergebene Hash passt nicht mehr, kein Stempel
printf '\n# w\n' >> "$R/Dockerfile"
neu_commit dockerfile-5
schritt tests 0 "$TS" "Commit waehrend des Laufs" FAKE_COMMIT=1
schritt tests 0 "$TS" "danach kein Stempel am neuen Stand" X=1
# Trockenlauf: keine Gates, kein Stempel (der Baum ist sauber, ohne TROCKEN wuerde geschrieben)
printf '\n# t\n' >> "$R/Dockerfile"
neu_commit dockerfile-4
schritt tests 0 - "Trockenlauf ruft nichts" TROCKEN=1
schritt tests 0 "$TS" "nach Trockenlauf kein Stempel, Gates laufen" X=1
# Aufruf ohne Gate und mit unbekannter Klasse
(cd "$R" && "$BASH_BIN" tools/stempel-gruppe.sh tests > /dev/null 2>&1); rc=$?; LAEUFE=$((LAEUFE + 1))
[ "$rc" = 2 ] || rot "Aufruf ohne Gate: Exit $rc statt 2"
(cd "$R" && "$BASH_BIN" tools/stempel-gruppe.sh doku gt1 > /dev/null 2>&1); rc=$?; LAEUFE=$((LAEUFE + 1))
[ "$rc" = 2 ] || rot "unbekannte Klasse: Exit $rc statt 2"

if [ "$FEHLER" -ne 0 ]; then echo "stempel-gruppe: $FEHLER von $LAEUFE Faellen rot"; exit 1; fi
[ "$LAEUFE" -ge 20 ] || { echo "stempel-gruppe: nur $LAEUFE Faelle gelaufen"; exit 1; }
echo "stempel-gruppe: $LAEUFE Faelle wie erwartet"
