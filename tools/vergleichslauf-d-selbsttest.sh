#!/usr/bin/env bash
# vergleichslauf-d-selbsttest.sh - belegt, dass tools/vergleichslauf-d.sh vor jedem Docker-Aufruf abbricht (Exit 2),
# wenn die EULA-Variable nicht `Y` ist, wenn CI oder GITHUB_ACTIONS gesetzt sind und wenn der Name des Laufs unbekannt
# ist; dass ein gueltiger Aufruf bis zum ersten Docker-Aufruf kommt; dass der Vergleich des Live-Laufs (`--vergleich`) nur
# bei Gleichheit oder genau der gelisteten Abweichung gruen ist; und dass kein Gate, kein E2E-Skript und kein
# Workflow das Target nennen und keine Datei ausser dem Make-Fragment die EULA setzt (ADR-0008, ADR-0022, Entscheidung des
# Nutzers pt9912 vom 2026-11-01). Docker ist ein Ersatz, der seine Aufrufe protokolliert und scheitert; es laeuft kein
# Docker. Aufgerufen von der Dockerfile-Stage `skript-selbsttest`; braucht bash, grep, mktemp, find.
# Grenze der Deklaration: die Stage enthaelt nur die kopierten Dateien (tools/e2e.sh, tools/mk, harness/mk, Makefile,
# repo.mk, .github/workflows), nicht das ganze Repo. Die Suche nach der EULA-Variable laeuft nur dort; ein neues Skript
# an anderer Stelle mit eigener EULA-Zeile sieht der Test nicht. Die Gate-Kette prueft er an `GATE_CHECKS += ...` mit dem
# Namen auf derselben Zeile; `gates: vergleichslauf-d` als Voraussetzung oder ein Aufruf per `$(MAKE)` faellt durch.
set -eu

wurzel=$(cd "$(dirname "$0")/.." && pwd)
lauf="$wurzel/tools/vergleichslauf-d.sh"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
mkdir "$tmp/bin"
cat > "$tmp/bin/docker" <<'FIX'
#!/bin/sh
echo "$*" >> "$FAKE_LOG"
exit 1
FIX
chmod +x "$tmp/bin/docker"

fehler=0
pruefe() { echo "vergleichslauf-d-selbsttest: FEHLER - $1" >&2; fehler=1; }

# aufruf <Name> <Variablen...> -- <Argument>: setzt Status, Log und Fehlerausgabe in $tmp.
aufruf() {
  : > "$tmp/log"
  status=0
  env -i PATH="$tmp/bin:$PATH" HOME="$tmp" TMPDIR="$tmp" FAKE_LOG="$tmp/log" "$@" bash "$lauf" "$ARG" > "$tmp/aus" 2> "$tmp/fehl" || status=$?
}

ARG=alle
aufruf
[ "$status" -eq 2 ] || pruefe "ohne EULA-Variable: Exit $status statt 2"
[ ! -s "$tmp/log" ] || pruefe "ohne EULA-Variable: Docker wurde gerufen [$(tr '\n' ' ' < "$tmp/log")]"
grep -q 'VERGLEICH_ACCEPT_EULA' "$tmp/fehl" || pruefe "ohne EULA-Variable: die Meldung nennt die Variable nicht"

aufruf VERGLEICH_ACCEPT_EULA=N
[ "$status" -eq 2 ] || pruefe "EULA-Variable N: Exit $status statt 2"
[ ! -s "$tmp/log" ] || pruefe "EULA-Variable N: Docker wurde gerufen"

aufruf VERGLEICH_ACCEPT_EULA=Y CI=true
[ "$status" -eq 2 ] || pruefe "CI gesetzt: Exit $status statt 2"
[ ! -s "$tmp/log" ] || pruefe "CI gesetzt: Docker wurde gerufen"
grep -q 'CI' "$tmp/fehl" || pruefe "CI gesetzt: die Meldung nennt die CI nicht"

aufruf VERGLEICH_ACCEPT_EULA=Y GITHUB_ACTIONS=true
[ "$status" -eq 2 ] || pruefe "GITHUB_ACTIONS gesetzt: Exit $status statt 2"
[ ! -s "$tmp/log" ] || pruefe "GITHUB_ACTIONS gesetzt: Docker wurde gerufen"

ARG=unbekannt
aufruf VERGLEICH_ACCEPT_EULA=Y
[ "$status" -eq 2 ] || pruefe "unbekannter Lauf: Exit $status statt 2"
[ ! -s "$tmp/log" ] || pruefe "unbekannter Lauf: Docker wurde gerufen"
grep -q 'LAUF muss' "$tmp/fehl" || pruefe "unbekannter Lauf: die Meldung nennt die Namen nicht"

# Gueltiger Aufruf: kommt bis zum ersten Docker-Aufruf (Bau des Client-Images), der im Ersatz scheitert.
ARG=2022-anmeldung
aufruf VERGLEICH_ACCEPT_EULA=Y
[ "$status" -eq 1 ] || pruefe "gueltiger Aufruf: Exit $status statt 1 (Docker-Ersatz scheitert)"
head -1 "$tmp/log" | grep -q 'build.*--target vergleich' || pruefe "gueltiger Aufruf: erster Docker-Aufruf ist [$(head -1 "$tmp/log")]"

# Vergleich des Live-Laufs (`--vergleich`, SPEC-045): gruen genau bei Gleichheit oder bei der gelisteten Abweichung; jede andere
# Abweichung ist rot. Docker und EULA-Variable sind dafuer nicht noetig; die Dateien entstehen hier.
vergleich() { # vergleich <Name> <erwarteter Exit> <Referenz> <Server> <Ausnahmen> [<Stufe>]
  local name="$1" erwartet="$2" ist=0
  printf '%b' "$3" > "$tmp/v-ref"
  printf '%b' "$4" > "$tmp/v-srv"
  printf '%b' "$5" > "$tmp/v-aus"
  env -i PATH="$PATH" HOME="$tmp" bash "$lauf" --vergleich "$tmp/v-ref" "$tmp/v-srv" "$tmp/v-aus" ${6:+"$6"} > "$tmp/v-aus.log" 2>&1 || ist=$?
  [ "$ist" -eq "$erwartet" ] || pruefe "Vergleich $name: Exit $ist statt $erwartet [$(tr '\n' '|' < "$tmp/v-aus.log")]"
}
R='a\tgleich\nb\tNummer 208, Class 16\nc\tzwei Zeilen\n'
S='a\tgleich\nb\tNummer 208, Class 20\nc\tzwei Zeilen\n'
REGEL='b\tNummer 208, Class 16\tNummer 208, Class 20\tso gewollt\tGrund\tSPEC-1\n'
vergleich "gleich ohne Regel" 0 'a\tx\n' 'a\tx\n' ''
vergleich "gelistete Abweichung" 0 "$R" "$S" "$REGEL"
vergleich "kleinere Abweichung (Server weicht weniger ab)" 1 "$R" 'a\tgleich\nb\tNummer 208, Class 16\nc\tzwei Zeilen\n' "$REGEL"
vergleich "groessere Abweichung (Server weicht anders ab)" 1 "$R" 'a\tgleich\nb\tNummer 208, Class 21\nc\tzwei Zeilen\n' "$REGEL"
vergleich "groessere Abweichung (Referenz weicht anders ab)" 1 'a\tgleich\nb\tNummer 207, Class 16\nc\tzwei Zeilen\n' "$S" "$REGEL"
vergleich "nicht gelistete Abweichung" 1 "$R" 'a\tgleich\nb\tNummer 208, Class 20\nc\tdrei Zeilen\n' "$REGEL"
vergleich "Abweichung ohne jede Regel" 1 "$R" "$S" ''
vergleich "Regel ohne Abweichung" 1 'a\tx\n' 'a\tx\n' 'a\tx\ty\tso gewollt\tG\tV\n'
vergleich "Regel ohne Schritt" 1 'a\tx\n' 'a\tx\n' "$REGEL"
vergleich "doppelte Regel" 1 "$R" "$S" "$REGEL$REGEL"
vergleich "zwei Regeln, die zweite trifft" 0 "$R" "$S" 'b\tNummer 1\tNummer 2\tLuecke\tG\tV\n'"$REGEL"
vergleich "zwei Regeln, keine trifft" 1 "$R" 'a\tgleich\nb\tNummer 208, Class 22\nc\tzwei Zeilen\n' 'b\tNummer 1\tNummer 2\tLuecke\tG\tV\n'"$REGEL"
vergleich "unbekannte Art" 1 "$R" "$S" 'b\tNummer 208, Class 16\tNummer 208, Class 20\tegal\tGrund\tSPEC-1\n'
vergleich "Regel ohne Grund" 1 "$R" "$S" 'b\tNummer 208, Class 16\tNummer 208, Class 20\tLuecke\n'
vergleich "Schritt fehlt auf dem Server" 1 'a\tx\nb\ty\n' 'a\tx\n' ''
vergleich "Schritt fehlt bei der Referenz" 1 'a\tx\n' 'a\tx\nb\ty\n' ''
vergleich "Regel der eigenen Stufe" 0 "$R" "$S" 'b@2022\tNummer 208, Class 16\tNummer 208, Class 20\tLuecke\tG\tV\n' 2022
vergleich "Regel einer anderen Stufe gilt nicht" 1 "$R" "$S" 'b@2025\tNummer 208, Class 16\tNummer 208, Class 20\tLuecke\tG\tV\n' 2022
vergleich "Kommentar und Leerzeile zaehlen nicht" 0 "$R" "$S" "# Kopf\n\n$REGEL"
: > "$tmp/leer"
status=0
env -i PATH="$PATH" HOME="$tmp" bash "$lauf" --vergleich "$tmp/leer" "$tmp/gibtesnicht" "$tmp/leer" > /dev/null 2>&1 || status=$?
[ "$status" -eq 2 ] || pruefe "Vergleich mit fehlender Datei: Exit $status statt 2"

# Kopplung: Gate-Kette, E2E-Skript, Workflows und die EULA-Variable. Fehlt eine Quelle, ist der Test rot statt leer.
for datei in tools/e2e.sh tools/mk/vergleichslauf-d.mk Makefile repo.mk .github/workflows/gates.yml .github/workflows/e2e.yml; do
  [ -f "$wurzel/$datei" ] || pruefe "Eingang fehlt: $datei"
done
if grep -rEq '^GATE_CHECKS[[:space:]]*\+=.*vergleichslauf' "$wurzel/tools/mk" "$wurzel/harness/mk" "$wurzel/repo.mk" "$wurzel/Makefile"; then
  pruefe "ein Fragment haengt vergleichslauf-d an GATE_CHECKS"
fi
grep -q 'vergleichslauf' "$wurzel/tools/e2e.sh" && pruefe "tools/e2e.sh nennt vergleichslauf"
grep -rq 'vergleichslauf' "$wurzel/.github/workflows" && pruefe "ein Workflow nennt vergleichslauf"
grep -q '^VERGLEICH_ACCEPT_EULA = Y$' "$wurzel/tools/mk/vergleichslauf-d.mk" || pruefe "tools/mk/vergleichslauf-d.mk setzt die EULA-Variable nicht"
for ort in tools/e2e.sh tools/mk harness/mk repo.mk Makefile .github/workflows; do
  treffer=$(grep -rl 'ACCEPT_EULA' "$wurzel/$ort" 2>/dev/null | grep -v 'tools/mk/vergleichslauf-d.mk' || true)
  [ -z "$treffer" ] || pruefe "ACCEPT_EULA steht ausserhalb des Fragments von Lauf D: $treffer"
done

[ "$fehler" -eq 0 ] || exit 1
echo "vergleichslauf-d-selbsttest: gruen"
