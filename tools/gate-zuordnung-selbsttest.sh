#!/usr/bin/env bash
# tools/gate-zuordnung-selbsttest.sh - Faelle fuer tools/gate-zuordnung.sh (ADR-0024 Entscheidung 3 und 6, ADR-0030
# Entscheidung 5 und 6). Jeder Fall ruft das Skript mit erfundenen Listen und prueft den Exit-Code und, bei Exit 1, den Namen des
# Gates in der Meldung. Ein Fall mit erwartetem Exit 1 ist die Mutation: das Skript muss rot werden, sonst ist der Selbsttest
# rot. Die Pflichtmenge der vier Selbsttests ist in den Faellen mit GZ_PFLICHT ersetzt, ein Fall nutzt die echte.
set -u
cd "$(dirname "$0")/.." || exit 2
T=$(mktemp -d "${TMPDIR:-/tmp}/gate-zuordnung.XXXXXX") || exit 2
trap 'rm -rf "$T"' EXIT
printf 'STAGES_TESTS="test detekt"\nSTAGES_TESTS_DOTNET="test"\nSTAGES_WERKZEUG="skript"\n' > "$T/stages"
n=0; fehl=0
# fall <beschreibung> <exit> <muster|-> <gates> <werkzeug> <tests> <stempel> <immer> <paare> [<datei>] [VAR=wert]...
fall() {
	local text=$1 soll=$2 muster=$3 g=$4 w=$5 t=$6 s=$7 im=$8 p=$9 d=${10:-$T/stages} aus rc
	shift $(($# > 9 ? 10 : 9))
	aus=$(env GZ_PFLICHT=- "${@:-GZ_X=1}" bash tools/gate-zuordnung.sh "$g" "$w" "$t" "$s" "$im" "$p" "$d" 2>&1); rc=$?
	n=$((n + 1))
	if [ "$rc" -ne "$soll" ]; then echo "FALL ROT: $text (Exit $rc, erwartet $soll): $aus"; fehl=$((fehl + 1)); return; fi
	if [ "$muster" != - ] && ! printf '%s' "$aus" | grep -q -F -- "$muster"; then echo "FALL ROT: $text (Meldung ohne '$muster'): $aus"; fehl=$((fehl + 1)); fi
}
P="a=test b=skript c=dotnet:test"
fall "alles zugeordnet" 0 - "a b c" "b" "a c" "" "" "$P"
fall "Klasse immer braucht kein Paar" 0 - "a d" "" "a" "" "d" "a=test"
fall "Klasse stempel mit gate=-" 0 - "a z" "" "a" "z" "" "a=test z=-"
fall "leere Klasse tests" 0 - "b" "b" "" "" "" "b=skript"
fall "Gate ohne Klasse" 1 "x steht in 0 Klassen" "a x" "" "a" "" "" "a=test"
fall "Gate in werkzeug und tests" 1 "b steht in 2 Klassen" "b" "b" "b" "" "" "b=skript"
fall "Gate in tests und immer" 1 "a steht in 2 Klassen" "a" "" "a" "" "a" "a=test"
fall "Gate in tests und stempel" 1 "a steht in 2 Klassen" "a" "" "a" "a" "" "a=test"
fall "Eintrag der Klasse ist kein Gate (werkzeug)" 1 "x (werkzeug)" "a" "x" "a" "" "" "a=test x=skript"
fall "Eintrag der Klasse ist kein Gate (stempel)" 1 "z (stempel)" "a" "" "a" "z" "" "a=test z=-"
fall "Eintrag der Klasse ist kein Gate (immer)" 1 "y (immer)" "a" "" "a" "" "y" "a=test"
fall "Namensteil zaehlt nicht" 1 "test steht in 0 Klassen" "test detekt-selbsttest" "detekt-selbsttest" "" "" "" "detekt-selbsttest=skript"
fall "tests-Gate baut Stage von werkzeug (falsche Klasse)" 1 "a (tests) baut Stage skript" "a" "" "a" "" "" "a=skript"
fall "werkzeug-Gate baut Stage von tests (falsche Klasse)" 1 "b (werkzeug) baut Stage test" "b" "b" "" "" "" "b=test"
fall "stempel-Gate baut eine Docker-Stage" 1 "z (stempel) baut Stage test" "z" "" "" "z" "" "z=test"
fall "Stage in keiner Liste" 1 "a (tests) baut Stage unbekannt" "a" "" "a" "" "" "a=unbekannt"
fall "Stage compile fehlt in der Liste" 1 "a (tests) baut Stage compile" "a" "" "a" "" "" "a=compile"
fall "dotnet-Stage ohne Praefix zaehlt als test" 0 - "a" "" "a" "" "" "a=test"
fall "Gate ohne Paar" 1 "a hat kein Paar" "a" "" "a" "" "" ""
fall "Gate ohne Docker-Stage mit gate=-" 0 - "a" "a" "" "" "" "a=-"
fall "Stage-Datei ohne Listen" 1 "nicht lesbar" "a" "" "a" "" "" "a=test" "$T/leer"
K="GZ_ENDKETTE=gruppe-werkzeug gruppe-tests gruppe-stempel i"
fall "Kette: Gruppen und Gates stimmen" 0 - "a b i z" "b" "a" "z" "i" "a=test b=skript z=-" "$T/stages" "$K" GZ_GRUPPE_W=b GZ_GRUPPE_T=a GZ_GRUPPE_S=z
fall "Kette: tests-Gate fehlt in gruppe-tests" 1 "a (tests) steht nicht in gruppe-tests" "a b i z" "b" "a" "z" "i" "a=test b=skript z=-" "$T/stages" "$K" GZ_GRUPPE_W=b GZ_GRUPPE_T= GZ_GRUPPE_S=z
fall "Kette: stempel-Gate fehlt in gruppe-stempel" 1 "z (stempel) steht nicht in gruppe-stempel" "a b i z" "b" "a" "z" "i" "a=test b=skript z=-" "$T/stages" "$K" GZ_GRUPPE_W=b GZ_GRUPPE_T=a GZ_GRUPPE_S=
fall "Kette: gruppe-werkzeug fehlt" 1 "gruppe-werkzeug steht nicht in der Kette" "a b i z" "b" "a" "z" "i" "a=test b=skript z=-" "$T/stages" "GZ_ENDKETTE=gruppe-tests gruppe-stempel i" GZ_GRUPPE_W=b GZ_GRUPPE_T=a GZ_GRUPPE_S=z
fall "Kette: gruppe-stempel fehlt" 1 "gruppe-stempel steht nicht in der Kette" "a b i z" "b" "a" "z" "i" "a=test b=skript z=-" "$T/stages" "GZ_ENDKETTE=gruppe-werkzeug gruppe-tests i" GZ_GRUPPE_W=b GZ_GRUPPE_T=a GZ_GRUPPE_S=z
fall "Kette: immer-Gate fehlt" 1 "i (immer) steht nicht in der Kette" "a b i z" "b" "a" "z" "i" "a=test b=skript z=-" "$T/stages" "GZ_ENDKETTE=gruppe-werkzeug gruppe-tests gruppe-stempel" GZ_GRUPPE_W=b GZ_GRUPPE_T=a GZ_GRUPPE_S=z
# Pflichtmenge (ADR-0030 Entscheidung 6)
fall "Pflicht: alle in der Klasse stempel" 0 - "p q" "" "" "p q" "" "p=- q=-" "$T/stages" "GZ_PFLICHT=p q"
fall "Pflicht: eines fehlt in GATE_CHECKS" 1 "q (Pflichtmenge) steht nicht in GATE_CHECKS" "p" "" "" "p" "" "p=-" "$T/stages" "GZ_PFLICHT=p q"
fall "Pflicht: eines steht in einer anderen Klasse" 1 "q (Pflichtmenge) gehoert nicht zur Klasse stempel" "p q" "q" "" "p" "" "p=- q=-" "$T/stages" "GZ_PFLICHT=p q"
fall "Pflicht: eines steht in keiner Klasse" 1 "q steht in 0 Klassen" "p q" "" "" "p" "" "p=-" "$T/stages" "GZ_PFLICHT=p q"
aus=$(env -u GZ_PFLICHT bash tools/gate-zuordnung.sh "stempel-selbsttest stempel-eingaenge stempel-abbild" "" "" "stempel-selbsttest stempel-eingaenge stempel-abbild" "" "stempel-selbsttest=- stempel-eingaenge=- stempel-abbild=-" "$T/stages" 2>&1); rc=$?
n=$((n + 1))
{ [ "$rc" -eq 1 ] && printf '%s' "$aus" | grep -q "stempel-gruppe (Pflichtmenge) steht nicht in GATE_CHECKS"; } || { echo "FALL ROT: echte Pflichtmenge ohne stempel-gruppe (Exit $rc): $aus"; fehl=$((fehl + 1)); }
bash tools/gate-zuordnung.sh a a a > /dev/null 2>&1; rc=$?; n=$((n + 1)); [ "$rc" -eq 2 ] || { echo "FALL ROT: Aufruf mit drei Argumenten (Exit $rc, erwartet 2)"; fehl=$((fehl + 1)); }
if [ "$fehl" -ne 0 ]; then echo "gate-zuordnung-selbsttest: $fehl von $n Faellen rot"; exit 1; fi
[ "$n" -ge 30 ] || { echo "gate-zuordnung-selbsttest: nur $n Faelle gelaufen"; exit 1; }
echo "gate-zuordnung-selbsttest: $n Faelle wie erwartet"
