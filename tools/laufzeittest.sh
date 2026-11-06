#!/usr/bin/env bash
# tools/laufzeittest.sh - Laufzeittest des Testserver-Containers (TDS-RB-01, TDS-QA-06; Dockerfile-Stagen
# `testserver` und `laufzeit-client`). Aufruf ueber `make laufzeittest`; BUILDER_FLAGS kommt aus
# der Umgebung (leer ohne BUILDER).
#
# Ablauf: beide Images bauen; Testserver-Container in einem eigenen internen Netzwerk anlegen, die Testkonfiguration
# per `docker cp` hineinlegen (nichts wird gemountet), starten; ein Rohbytes-Client in einem zweiten Container
# meldet sich an; `docker stop` beendet den Prozess (SIGTERM, Exit-Code 143). Zusage: Start, Login und Stop des
# Testserver-Containers mit Rohbytes; nicht belegt: Annahme durch Microsoft.Data.SqlClient, TLS, Last.
# Der Container ist kein Produkt-Image (ADR-0025 Entscheidung 4): Nutzer, Inhalt von /app, Lizenz im Image und
# JAVA_TOOL_OPTIONS sind keine Zusage dieses Laufs. Die Ausgabe des Testservers besteht aus den Zeilen `gestartet` und
# `gestoppt` und je einer JSON-Zeile je Ereignis der Bibliothek (ADR-0020); der Lauf prueft, dass die Anmeldung als
# Ereignis `login_accepted` mit der Kennung im Feld `principal` dasteht und dass nirgends sonst Benutzer oder Passwort stehen.
set -u

LAUF="tdswire-lz-$$"
NETZ="$LAUF-netz"
SERVER="$LAUF-server"
SERVER_IMAGE="tdswire-testserver:$LAUF"
CLIENT_IMAGE="tdswire-laufzeit-client:$LAUF"
SCRATCH="$(mktemp -d)"
FEHLER=0

aufraeumen() {
  docker rm -f "$SERVER" >/dev/null 2>&1
  docker network rm "$NETZ" >/dev/null 2>&1
  docker rmi -f "$SERVER_IMAGE" "$CLIENT_IMAGE" >/dev/null 2>&1
  rm -rf "$SCRATCH"
}
trap aufraeumen EXIT

pruefe() { # pruefe <Beschreibung> <Befehl...>
  local text="$1"
  shift
  if "$@"; then echo "laufzeittest: ok   $text"; else echo "laufzeittest: FEHLER $text"; FEHLER=1; fi
}

start=$(date +%s)
# shellcheck disable=SC2086
docker build ${BUILDER_FLAGS:-} --load --target testserver -t "$SERVER_IMAGE" . || exit 1
# shellcheck disable=SC2086
docker build ${BUILDER_FLAGS:-} --load --target laufzeit-client -t "$CLIENT_IMAGE" . || exit 1

# --- Start, Login, Stop -----------------------------------------------------------------------------
docker network create --internal "$NETZ" >/dev/null || exit 1
printf 'alice\t{plain}secret\n' >"$SCRATCH/users.txt"
# Die Benutzerliste kommt per `docker cp` vor dem Start in den Schreibbereich des Containers.
docker create --name "$SERVER" --network "$NETZ" \
  -e LISTEN_HOST=0.0.0.0 -e LISTEN_PORT=1433 -e ENCRYPTION_POLICY=TEST_UNENCRYPTED \
  -e AUTH_PLAIN_TESTMODE=true -e AUTH_USERS_FILE=/home/gradle/users.txt "$SERVER_IMAGE" >/dev/null || exit 1
docker cp "$SCRATCH/users.txt" "$SERVER:/home/gradle/users.txt" >/dev/null || exit 1
docker start "$SERVER" >/dev/null || exit 1

gestartet() {
  local i
  for i in $(seq 1 60); do
    docker logs "$SERVER" 2>&1 | grep -q 'tdswire: gestartet, Port 1433' && return 0
    [ "$(docker inspect --format '{{.State.Running}}' "$SERVER")" = "true" ] || return 1
    sleep 1
  done
  return 1
}
pruefe "Testserver im Container meldet 'gestartet, Port 1433'" gestartet

docker run --rm --network "$NETZ" -e "TDSWIRE_ZIEL=$SERVER:1433" "$CLIENT_IMAGE" >"$SCRATCH/client" 2>&1
client_ok=$?
pruefe "Rohbytes-Client im zweiten Container meldet sich an (PRELOGIN, LOGIN7, LOGINACK)" \
  sh -c "[ $client_ok -eq 0 ] && grep -q 'laufzeit-client: Login ok' '$SCRATCH/client'"
[ "$client_ok" -eq 0 ] || cat "$SCRATCH/client"

docker stop "$SERVER" >/dev/null
ende="$(docker inspect --format '{{.State.ExitCode}}' "$SERVER")"
# 143 = 128 + SIGTERM: die JVM endet nach dem Shutdown-Hook mit diesem Code (gemessen am 2026-10-31).
pruefe "docker stop beendet den Prozess durch SIGTERM, Exit-Code 143 (gemessen: $ende)" test "$ende" = "143"
pruefe "Ausgabe des Testservers nennt 'gestoppt'" sh -c "docker logs '$SERVER' 2>&1 | grep -q 'tdswire: gestoppt'"
anmeldung_als_ereignis() {
  docker logs "$SERVER" 2>&1 | grep -F '"event":"login_accepted"' | grep -qF '"principal":"alice"'
}
pruefe "Ausgabe des Testservers nennt die Anmeldung als Ereignis login_accepted mit principal alice" anmeldung_als_ereignis
benutzer_nur_im_principal() {
  ! docker logs "$SERVER" 2>&1 | grep -v '"event":"login_accepted"' | grep -q 'alice'
}
pruefe "Ausgabe des Testservers nennt den Benutzer nur im Ereignis login_accepted" benutzer_nur_im_principal
kein_passwort() { ! docker logs "$SERVER" 2>&1 | grep -q 'secret'; }
pruefe "Ausgabe des Testservers nennt das Passwort nicht" kein_passwort

echo "laufzeittest: Laufzeit $(( $(date +%s) - start )) s (Builds eingeschlossen)"
[ "$FEHLER" -eq 0 ] || { echo "laufzeittest: ROT"; exit 1; }
echo "laufzeittest: gruen"
