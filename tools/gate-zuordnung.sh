#!/usr/bin/env bash
# tools/gate-zuordnung.sh <gates> <werkzeug> <tests> [<gate=stage>... [<datei>]] - jedes Target in GATE_CHECKS gehoert zu genau
# einer der zwei Klassen `werkzeug` und `tests` (ADR-0024 Entscheidung 1 und 3, Fitness Function; TDS-QA-06). Die ersten drei
# Argumente sind leerzeichengetrennte Listen: alle Gates, die Gates der Klasse werkzeug, die Gates der Klasse tests.
# Optional das vierte Argument: Paare `gate=stage` der Gates, die eine Docker-Stage bauen (`referenztest-dotnet=dotnet:test`
# fuer die Stage des .NET-Dockerfiles), und als fuenftes die Datei mit den Stage-Listen der Klassen (Standard:
# tools/stempel-eingaenge-pruefung.sh, Zeilen STAGES_TESTS, STAGES_TESTS_DOTNET, STAGES_WERKZEUG). Die Stage eines Gates muss in
# der Stage-Liste seiner Klasse stehen: dort hat die Eingangsliste der Klasse ihre COPY-Quellen geprueft; ein Gate in der
# anderen Klasse liesse seinen Eingang aus dem Hash seiner Klasse heraus.
# Grenze: ein Gate, das eine Stage baut und kein Paar nennt, bleibt ungeprueft.
# Exit 0, wenn die Zuordnung stimmt; Exit 1 mit je einer Zeile auf stderr bei: Gate ohne Klasse, Gate in beiden Klassen,
# Klasseneintrag, der kein Gate ist, Stage nicht in der Stage-Liste der Klasse des Gates. Aufrufer: Target gate-zuordnung in
# tools/mk/stempel.mk; Selbsttest: tools/gate-zuordnung-selbsttest.sh.
set -u
{ [ $# -ge 3 ] && [ $# -le 5 ]; } || { echo 'gate-zuordnung: drei Listen erwartet (Gates, werkzeug, tests), optional Paare und Stage-Datei' >&2; exit 2; }
read -r -a alle <<< "$1"
read -r -a w <<< "$2"
read -r -a t <<< "$3"
read -r -a paare <<< "${4:-}"
stagedatei=${5:-tools/stempel-eingaenge-pruefung.sh}
bef=0
in_liste() { local x=$1 e; shift; for e in "$@"; do [ "$e" = "$x" ] && return 0; done; return 1; }
for g in ${alle[@]+"${alle[@]}"}; do
	iw=0; it=0
	in_liste "$g" ${w[@]+"${w[@]}"} && iw=1
	in_liste "$g" ${t[@]+"${t[@]}"} && it=1
	[ $((iw + it)) -eq 1 ] || { echo "gate-zuordnung: $g steht in $((iw + it)) Klassen, verlangt ist genau eine" >&2; bef=1; }
done
for g in ${w[@]+"${w[@]}"}; do in_liste "$g" ${alle[@]+"${alle[@]}"} || { echo "gate-zuordnung: $g (werkzeug) ist kein Gate in GATE_CHECKS" >&2; bef=1; }; done
for g in ${t[@]+"${t[@]}"}; do in_liste "$g" ${alle[@]+"${alle[@]}"} || { echo "gate-zuordnung: $g (tests) ist kein Gate in GATE_CHECKS" >&2; bef=1; }; done

if [ ${#paare[@]} -gt 0 ]; then
	stages() { sed -n "s/^$1=\"\\(.*\\)\"\$/\\1/p" "$stagedatei" | head -n 1; }
	st_tests=$(stages STAGES_TESTS); st_dotnet=$(stages STAGES_TESTS_DOTNET); st_werkzeug=$(stages STAGES_WERKZEUG)
	{ [ -n "$st_tests" ] && [ -n "$st_dotnet" ] && [ -n "$st_werkzeug" ]; } || { echo "gate-zuordnung: Stage-Listen in $stagedatei nicht lesbar" >&2; exit 1; }
	lt=" $st_tests"
	for s in $st_dotnet; do lt+=" dotnet:$s"; done
	lt+=" "
	lw=" $st_werkzeug "
	for p in "${paare[@]}"; do
		g=${p%%=*}; s=${p#*=}
		if in_liste "$g" ${w[@]+"${w[@]}"}; then
			[[ $lw == *" $s "* ]] || { echo "gate-zuordnung: $g (werkzeug) baut Stage $s, die nicht in der Stage-Liste von werkzeug steht" >&2; bef=1; }
		elif in_liste "$g" ${t[@]+"${t[@]}"}; then
			[[ $lt == *" $s "* ]] || { echo "gate-zuordnung: $g (tests) baut Stage $s, die nicht in der Stage-Liste von tests steht" >&2; bef=1; }
		fi
	done
fi
[ "$bef" -eq 0 ] || exit 1
echo "gate-zuordnung: ${#alle[@]} Gates, ${#w[@]} werkzeug, ${#t[@]} tests, jedes in genau einer Klasse; ${#paare[@]} Stage-Paare"
