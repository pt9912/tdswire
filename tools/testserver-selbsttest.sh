#!/usr/bin/env bash
# tools/testserver-selbsttest.sh - Selbsttest von tools/testserver.sh (TDS-RB-01, TDS-QA-06, ADR-0025). Aufruf ueber
# `make testserver-selbsttest`; kein Gate (ADR-0022). Ablauf: up, Rohbytes-Login vom Host-Netz aus, zweiter up, down, down ohne
# Container, up bei belegtem Port; danach je Zweig eine Mutation des Skripts, die den Selbsttest rot faerbt.
# Zusage: Start, Login ueber den veroeffentlichten Port und Stop; danach kein Container und der Port frei. Nicht belegt: TLS.
set -u

LAUF="tdswire-st-$$"
PORT=$((20000 + $$ % 20000))
IMAGE="tdswire-testserver:$LAUF"
CLIENT_IMAGE="tdswire-laufzeit-client:$LAUF"
SCRATCH="$(mktemp -d)"
FEHLER=0
export TESTSERVER_NAME="$LAUF" TESTSERVER_IMAGE="$IMAGE" TESTSERVER_PORT="$PORT"

aufraeumen() {
  docker rm -f "$LAUF" "$LAUF-besetzt" >/dev/null 2>&1
  docker rmi -f "$IMAGE" "$CLIENT_IMAGE" >/dev/null 2>&1
  rm -rf "$SCRATCH"
}
trap aufraeumen EXIT

pruefe() { # pruefe <Beschreibung> <Befehl...>
  local text="$1"
  shift
  if "$@"; then echo "testserver-selbsttest: ok   $text"; else echo "testserver-selbsttest: FEHLER $text"; FEHLER=1; fi
}
keiner() { ! docker inspect "$LAUF" >/dev/null 2>&1; }
port_frei() { ! (exec 3<>"/dev/tcp/127.0.0.1/$PORT") 2>/dev/null; }

start=$(date +%s)
# shellcheck disable=SC2086
docker build ${BUILDER_FLAGS:-} --load --target laufzeit-client -t "$CLIENT_IMAGE" . || exit 1

# --- Ablauf mit dem echten Skript ----------------------------------------------------------------------
bash tools/testserver.sh up >"$SCRATCH/up" 2>&1
pruefe "up endet mit 0 und nennt Testwerte ohne Verschluesselung" \
  sh -c "[ $? -eq 0 ] && grep -q 'Testwerte, keine Verschluesselung' '$SCRATCH/up' && grep -q 'alice' '$SCRATCH/up'"
cat "$SCRATCH/up"
pruefe "status meldet laeuft" bash tools/testserver.sh status
docker run --rm --network host -e "TDSWIRE_ZIEL=127.0.0.1:$PORT" "$CLIENT_IMAGE" >"$SCRATCH/client" 2>&1
pruefe "Rohbytes-Login vom Host-Netz gegen den veroeffentlichten Port" grep -q 'laufzeit-client: Login ok' "$SCRATCH/client"
bash tools/testserver.sh up >"$SCRATCH/zweiter" 2>&1
pruefe "zweiter up bricht ab und nennt den Container" sh -c "[ $? -ne 0 ] && grep -q 'existiert schon' '$SCRATCH/zweiter'"
bash tools/testserver.sh down >/dev/null 2>&1
pruefe "down entfernt den Container" keiner
pruefe "Port nach down frei" port_frei
pruefe "down ohne Container ist ein Erfolg" bash tools/testserver.sh down
export TESTSERVER_BAUEN=0
docker run -d --name "$LAUF-besetzt" -p "127.0.0.1:$PORT:1433" --entrypoint sleep "$IMAGE" 120 >/dev/null
bash tools/testserver.sh up >"$SCRATCH/belegt" 2>&1
code=$?
pruefe "up bei belegtem Port bricht ab und hinterlaesst keinen Container" sh -c "[ $code -ne 0  ] && ! docker inspect '$LAUF' >/dev/null 2>&1"
docker rm -f "$LAUF-besetzt" >/dev/null 2>&1

# --- Mutationen: jede faerbt genau die Pruefung ihres Zweigs rot -----------------------------------------
mutiere() { # mutiere <Zweig> <sed-Ausdruck> <Pruefung (Funktion)>
  local zweig="$1" ausdruck="$2" pruefung="$3" ziel="$SCRATCH/mutant-$1.sh"
  sed "/zweig:$zweig/ $ausdruck" tools/testserver.sh >"$ziel"
  if cmp -s tools/testserver.sh "$ziel"; then echo "testserver-selbsttest: FEHLER Mutation $zweig aendert nichts"; FEHLER=1; return; fi
  if "$pruefung" "$ziel"; then echo "testserver-selbsttest: FEHLER Mutation $zweig bleibt gruen"; FEHLER=1
  else echo "testserver-selbsttest: ok   Mutation $zweig faerbt rot"; fi
}
zweiter_up_ok() { # gruen, wenn up bei vorhandenem Container mit 'existiert schon' abbricht
  docker create --name "$LAUF" "$IMAGE" >/dev/null
  bash "$1" up >"$SCRATCH/m" 2>&1
  local r=$?
  docker rm -f "$LAUF" >/dev/null 2>&1
  [ $r -ne 0 ] && grep -q 'existiert schon' "$SCRATCH/m"
}
down_leer_ok() { bash "$1" down >/dev/null 2>&1; }
port_belegt_ok() { # gruen, wenn up bei belegtem Port abbricht und keinen Container hinterlaesst
  docker run -d --name "$LAUF-besetzt" -p "127.0.0.1:$PORT:1433" --entrypoint sleep "$IMAGE" 120 >/dev/null
  bash "$1" up >/dev/null 2>&1
  local r=$?
  docker rm -f "$LAUF-besetzt" >/dev/null 2>&1
  local rest=1
  docker inspect "$LAUF" >/dev/null 2>&1 || rest=0
  docker rm -f "$LAUF" >/dev/null 2>&1
  [ $r -ne 0 ] && [ $rest -eq 0 ]
}
pruefe "Grundlage: das unveraenderte Skript besteht zweiter up" zweiter_up_ok tools/testserver.sh
pruefe "Grundlage: das unveraenderte Skript besteht down ohne Container" down_leer_ok tools/testserver.sh
pruefe "Grundlage: das unveraenderte Skript besteht belegten Port" port_belegt_ok tools/testserver.sh
mutiere zweiter-up 's/exit 1/exit 0/; s/existiert schon/laeuft/' zweiter_up_ok
mutiere down-ohne-container 's/exit 0/exit 1/' down_leer_ok
mutiere port-belegt 's/docker rm -f "$NAME" >\/dev\/null 2>&1/true/' port_belegt_ok

echo "testserver-selbsttest: Laufzeit $(( $(date +%s) - start )) s (Builds eingeschlossen)"
[ "$FEHLER" -eq 0 ] || { echo "testserver-selbsttest: ROT"; exit 1; }
echo "testserver-selbsttest: gruen"
