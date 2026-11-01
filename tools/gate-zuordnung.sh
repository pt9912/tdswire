#!/usr/bin/env bash
# tools/gate-zuordnung.sh <gates> <werkzeug> <tests> <immer> [<gate=stage>... [<datei>]] - jedes Target in GATE_CHECKS gehoert zu
# genau einer der Klassen `werkzeug`, `tests` und `immer` (ADR-0024 Entscheidung 1, 3 und 6, Fitness Function; TDS-QA-06). Die
# ersten vier Argumente sind leerzeichengetrennte Listen: alle Gates, die Gates der Klasse werkzeug, die der Klasse tests und die
# Gates ohne Stempel, die immer laufen (ihr Eingang steht nicht vollstaendig in einer Eingangsliste).
# Das fuenfte Argument nennt je Gate der Klassen werkzeug und tests ein Paar `gate=stage`: die Docker-Stage, die es baut
# (`referenztest-dotnet=dotnet:test` fuer das .NET-Dockerfile), oder `gate=-` fuer ein Gate ohne Docker-Stage. Die Stage muss in
# der Stage-Liste seiner Klasse stehen (Standard-Datei als sechstes Argument: tools/stempel-eingaenge-pruefung.sh, Zeilen
# STAGES_TESTS, STAGES_TESTS_DOTNET, STAGES_WERKZEUG): dort hat die Eingangsliste der Klasse ihre COPY-Quellen geprueft; ein Gate
# in der anderen Klasse liesse seinen Eingang aus dem Hash seiner Klasse heraus.
# Umgebung (optional, alle drei zusammen): GZ_ENDKETTE (die Kette, die make gates ausfuehrt), GZ_GRUPPE_W und GZ_GRUPPE_T (die
# Gates, die gruppe-werkzeug und gruppe-tests rufen). Dann muss jedes Gate der Klassen in seiner Gruppe stehen, die Gruppe in der
# Kette, und jedes Gate der Klasse immer in der Kette.
# Grenze: ein Gate, das eine Stage baut und mit `gate=-` angemeldet ist, bleibt ungeprueft; die Pruefung der Eingangslisten
# (tools/stempel-eingaenge-pruefung.sh) kennt seine Stage dann nicht.
# Exit 0, wenn die Zuordnung stimmt; Exit 1 mit je einer Zeile auf stderr je Befund. Aufrufer: Target gate-zuordnung in
# tools/mk/stempel.mk; Selbsttest: tools/gate-zuordnung-selbsttest.sh.
set -u
{ [ $# -ge 4 ] && [ $# -le 6 ]; } || { echo 'gate-zuordnung: vier Listen erwartet (Gates, werkzeug, tests, immer), optional Paare und Stage-Datei' >&2; exit 2; }
read -r -a alle <<< "$1"
read -r -a w <<< "$2"
read -r -a t <<< "$3"
read -r -a im <<< "$4"
read -r -a paare <<< "${5:-}"
stagedatei=${6:-tools/stempel-eingaenge-pruefung.sh}
bef=0
in_liste() { local x=$1 e; shift; for e in "$@"; do [ "$e" = "$x" ] && return 0; done; return 1; }
for g in ${alle[@]+"${alle[@]}"}; do
	n=0
	in_liste "$g" ${w[@]+"${w[@]}"} && n=$((n + 1))
	in_liste "$g" ${t[@]+"${t[@]}"} && n=$((n + 1))
	in_liste "$g" ${im[@]+"${im[@]}"} && n=$((n + 1))
	[ "$n" -eq 1 ] || { echo "gate-zuordnung: $g steht in $n Klassen, verlangt ist genau eine" >&2; bef=1; }
done
for k in w t im; do
	case $k in w) nm=werkzeug ;; t) nm=tests ;; im) nm=immer ;; esac
	declare -n liste=$k
	for g in ${liste[@]+"${liste[@]}"}; do
		in_liste "$g" ${alle[@]+"${alle[@]}"} || { echo "gate-zuordnung: $g ($nm) ist kein Gate in GATE_CHECKS" >&2; bef=1; }
	done
done

stages() { sed -n "s/^$1=\"\\(.*\\)\"\$/\\1/p" "$stagedatei" 2> /dev/null | head -n 1; }
st_tests=$(stages STAGES_TESTS); st_dotnet=$(stages STAGES_TESTS_DOTNET); st_werkzeug=$(stages STAGES_WERKZEUG)
if [ -n "$st_tests" ] && [ -n "$st_dotnet" ] && [ -n "$st_werkzeug" ]; then
	lt=" $st_tests"
	for s in $st_dotnet; do lt+=" dotnet:$s"; done
	lt+=" "
	lw=" $st_werkzeug "
	for g in ${w[@]+"${w[@]}"} ${t[@]+"${t[@]}"}; do
		s=
		for p in ${paare[@]+"${paare[@]}"}; do [ "${p%%=*}" = "$g" ] && s=${p#*=}; done
		[ -n "$s" ] || { echo "gate-zuordnung: $g hat kein Paar gate=stage (gate=- fuer ein Gate ohne Docker-Stage)" >&2; bef=1; continue; }
		[ "$s" = - ] && continue
		if in_liste "$g" ${w[@]+"${w[@]}"}; then
			[[ $lw == *" $s "* ]] || { echo "gate-zuordnung: $g (werkzeug) baut Stage $s, die nicht in der Stage-Liste von werkzeug steht" >&2; bef=1; }
		else
			[[ $lt == *" $s "* ]] || { echo "gate-zuordnung: $g (tests) baut Stage $s, die nicht in der Stage-Liste von tests steht" >&2; bef=1; }
		fi
	done
else
	echo "gate-zuordnung: Stage-Listen in $stagedatei nicht lesbar" >&2
	bef=1
fi

if [ -n "${GZ_ENDKETTE+x}" ]; then
	read -r -a kette <<< "$GZ_ENDKETTE"
	read -r -a gw <<< "${GZ_GRUPPE_W:-}"
	read -r -a gt <<< "${GZ_GRUPPE_T:-}"
	for g in ${w[@]+"${w[@]}"}; do in_liste "$g" ${gw[@]+"${gw[@]}"} || { echo "gate-zuordnung: $g (werkzeug) steht nicht in gruppe-werkzeug" >&2; bef=1; }; done
	for g in ${t[@]+"${t[@]}"}; do in_liste "$g" ${gt[@]+"${gt[@]}"} || { echo "gate-zuordnung: $g (tests) steht nicht in gruppe-tests" >&2; bef=1; }; done
	in_liste gruppe-werkzeug ${kette[@]+"${kette[@]}"} || { echo "gate-zuordnung: gruppe-werkzeug steht nicht in der Kette von make gates" >&2; bef=1; }
	in_liste gruppe-tests ${kette[@]+"${kette[@]}"} || { echo "gate-zuordnung: gruppe-tests steht nicht in der Kette von make gates" >&2; bef=1; }
	for g in ${im[@]+"${im[@]}"}; do in_liste "$g" ${kette[@]+"${kette[@]}"} || { echo "gate-zuordnung: $g (immer) steht nicht in der Kette von make gates" >&2; bef=1; }; done
fi
[ "$bef" -eq 0 ] || exit 1
echo "gate-zuordnung: ${#alle[@]} Gates, ${#w[@]} werkzeug, ${#t[@]} tests, ${#im[@]} immer; jedes in genau einer Klasse, jedes gestempelte mit Stage-Paar"
