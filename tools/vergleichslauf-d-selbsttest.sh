#!/usr/bin/env bash
# vergleichslauf-d-selbsttest.sh - belegt, dass tools/vergleichslauf-d.sh vor jedem Docker-Aufruf abbricht (Exit 2),
# wenn die EULA-Variable nicht `Y` ist, wenn CI oder GITHUB_ACTIONS gesetzt sind und wenn der Name des Laufs unbekannt
# ist; dass ein gueltiger Aufruf bis zum ersten Docker-Aufruf kommt; und dass kein Gate, kein E2E-Skript und kein
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
