#!/usr/bin/env bash
# tools/gate-zuordnung-selbsttest.sh - Faelle fuer tools/gate-zuordnung.sh (ADR-0024 Entscheidung 3). Jeder Fall ruft das
# Skript mit erfundenen Listen und prueft den Exit-Code und, bei Exit 1, den Namen des Gates in der Meldung. Ein Fall mit
# erwartetem Exit 1 ist die Mutation: das Skript muss rot werden, sonst ist der Selbsttest rot.
set -u
cd "$(dirname "$0")/.." || exit 2
n=0; fehl=0
fall() { # <beschreibung> <erwarteter exit> <muster oder -> <gates> <werkzeug> <tests>
	local text=$1 soll=$2 muster=$3 aus rc
	aus=$(bash tools/gate-zuordnung.sh "$4" "$5" "$6" ${7+"$7"} ${8+"$8"} 2>&1); rc=$?
	n=$((n + 1))
	if [ "$rc" -ne "$soll" ]; then echo "FALL ROT: $text (Exit $rc, erwartet $soll): $aus"; fehl=$((fehl + 1)); return; fi
	if [ "$muster" != - ] && ! printf '%s' "$aus" | grep -q -F -- "$muster"; then echo "FALL ROT: $text (Meldung ohne '$muster'): $aus"; fehl=$((fehl + 1)); fi
}
fall "alles zugeordnet" 0 - "a b c" "a b" "c"
fall "leere Klasse tests" 0 - "a b" "a b" ""
fall "Gate ohne Klasse" 1 "c steht in 0 Klassen" "a b c" "a" "b"
fall "Gate in beiden Klassen" 1 "b steht in 2 Klassen" "a b" "a b" "b"
fall "Eintrag der Klasse ist kein Gate (werkzeug)" 1 "x (werkzeug)" "a" "a x" ""
fall "Eintrag der Klasse ist kein Gate (tests)" 1 "y (tests)" "a" "a" "y"
fall "Namensteil zaehlt nicht: test und detekt-selbsttest" 1 "test steht in 0 Klassen" "test detekt-selbsttest" "detekt-selbsttest" ""
T=$(mktemp -d "${TMPDIR:-/tmp}/gate-zuordnung.XXXXXX") || exit 2
trap 'rm -rf "$T"' EXIT
printf 'STAGES_TESTS="test detekt"\nSTAGES_TESTS_DOTNET="test"\nSTAGES_WERKZEUG="skript"\n' > "$T/stages"
fall "Stage in der Liste der eigenen Klasse" 0 - "a b c" "b" "a c" "a=test b=skript c=dotnet:test" "$T/stages"
fall "tests-Gate baut Stage von werkzeug (falsche Klasse)" 1 "a (tests) baut Stage skript" "a b" "b" "a" "a=skript" "$T/stages"
fall "werkzeug-Gate baut Stage von tests (falsche Klasse)" 1 "b (werkzeug) baut Stage test" "a b" "b" "a" "b=test" "$T/stages"
fall "Stage in keiner Liste" 1 "a (tests) baut Stage unbekannt" "a" "" "a" "a=unbekannt" "$T/stages"
fall "Stage fehlt in der Liste" 1 "a (tests) baut Stage compile" "a" "" "a" "a=compile" "$T/stages"
fall "Stage-Datei ohne Listen" 1 "nicht lesbar" "a" "" "a" "a=test" "$T/leer"
bash tools/gate-zuordnung.sh a a > /dev/null 2>&1; rc=$?; n=$((n + 1)); [ "$rc" -eq 2 ] || { echo "FALL ROT: Aufruf mit zwei Argumenten (Exit $rc, erwartet 2)"; fehl=$((fehl + 1)); }
if [ "$fehl" -ne 0 ]; then echo "gate-zuordnung-selbsttest: $fehl von $n Faellen rot"; exit 1; fi
[ "$n" -ge 14 ] || { echo "gate-zuordnung-selbsttest: nur $n Faelle gelaufen"; exit 1; }
echo "gate-zuordnung-selbsttest: $n Faelle wie erwartet"
