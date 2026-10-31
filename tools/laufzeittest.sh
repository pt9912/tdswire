#!/usr/bin/env bash
# tools/laufzeittest.sh - Laufzeittest des Container-Artefakts (TDS-RB-01, Dockerfile-Stagen `runtime`
# und `laufzeit-client`). Aufruf ueber `make laufzeittest`; BUILDER_FLAGS und BUILDER_LOAD kommen aus
# der Umgebung (leer ohne BUILDER).
#
# Ablauf: beide Images bauen; Inhalt des Server-Images pruefen (Nutzer, Lizenz, Dateinamen und
# Namen der Umgebung ohne Benutzerliste, Schluesselspeicher, Passwort; kein Inhaltsscan); Server-Container in einem eigenen internen Netzwerk anlegen, die Testkonfiguration per
# `docker cp` hineinlegen (nichts wird gemountet), starten; ein Rohbytes-Client in einem zweiten Container
# meldet sich an; `docker stop` beendet den Prozess (SIGTERM, Exit-Code 143). Zusage: Start, Login und Stop des
# Containers mit Rohbytes; nicht belegt: Annahme durch Microsoft.Data.SqlClient, TLS, Last.
set -u

LAUF="tdswire-lz-$$"
NETZ="$LAUF-netz"
SERVER="$LAUF-server"
SERVER_IMAGE="tdswire-laufzeit:$LAUF"
CLIENT_IMAGE="tdswire-laufzeit-client:$LAUF"
OPTIONEN="-XX:+ExitOnOutOfMemoryError"
SCRATCH="$(mktemp -d)"
FEHLER=0
lager=""

aufraeumen() {
  [ -z "$lager" ] || docker rm -f "$lager" >/dev/null 2>&1
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
docker build ${BUILDER_FLAGS:-} --load --target runtime -t "$SERVER_IMAGE" . || exit 1
# shellcheck disable=SC2086
docker build ${BUILDER_FLAGS:-} --load --target laufzeit-client -t "$CLIENT_IMAGE" . || exit 1

# --- Inhalt des Images --------------------------------------------------------------------------
benutzer="$(docker image inspect --format '{{.Config.User}}' "$SERVER_IMAGE")"
nicht_root() { [ -n "$benutzer" ] && [ "${benutzer%%:*}" != "root" ] && [ "${benutzer%%:*}" != "0" ]; }
pruefe "Image-Nutzer ohne Root-Rechte (Nutzer '$benutzer')" nicht_root

lager="$(docker create "$SERVER_IMAGE")"
docker cp "$lager:/app/LICENSE" "$SCRATCH/LICENSE" >/dev/null 2>&1
pruefe "Image fuehrt /app/LICENSE und sie gleicht der LICENSE der Wurzel" cmp -s "$SCRATCH/LICENSE" LICENSE
docker export "$lager" | tar -t >"$SCRATCH/inhalt" 2>/dev/null
docker rm "$lager" >/dev/null && lager=""
inhalt_gelesen() { grep -qx 'app/tdswire.jar' "$SCRATCH/inhalt"; }
pruefe "Dateiliste des Images ist gelesen (enthaelt app/tdswire.jar)" inhalt_gelesen
# Gelesen werden die Pfade unterhalb der Verzeichnisse, die ein Betreiber oder dieses Dockerfile befuellt;
# die Dateien der Basis-Schichten (JDK-Konfiguration, Zertifikate) liegen ausserhalb und sind nicht geprueft.
ohne_geheimnis() {
  ! grep -E '^(app|home|tmp|srv|opt|root|data|config)/' "$SCRATCH/inhalt" \
    | grep -Ei '(^|/)(users?([.-][^/]*)?|benutzer[^/]*|passwords?[^/]*|secrets?[^/]*|[^/]*\.properties|.*\.(p12|pfx|jks|pem|key))$' \
    && ! docker image inspect --format '{{json .Config.Env}}' "$SERVER_IMAGE" | grep -Eiq 'PASSWORD|SECRET|AUTH_USERS|KEYSTORE'
}
pruefe "Dateinamen der Image-Liste und Namen der Umgebung nennen keine Benutzerliste, keinen Schluesselspeicher, kein Passwort" ohne_geheimnis
app_nur_artefakt() {
  ! grep -E '^app/' "$SCRATCH/inhalt" | grep -Ev '^app/($|LICENSE$|tdswire\.jar$|lib/($|[^/]+\.jar$))'
}
pruefe "/app enthaelt nur Jar, Bibliotheken und Lizenz" app_nur_artefakt

# --- Start, Login, Stop -----------------------------------------------------------------------------
docker network create --internal "$NETZ" >/dev/null || exit 1
printf 'alice\t{plain}secret\n' >"$SCRATCH/users.txt"
# Die Benutzerliste kommt per `docker cp` vor dem Start in den Schreibbereich des Containers.
docker create --name "$SERVER" --network "$NETZ" \
  -e LISTEN_HOST=0.0.0.0 -e LISTEN_PORT=1433 -e ENCRYPTION_POLICY=TEST_UNENCRYPTED \
  -e AUTH_PLAIN_TESTMODE=true -e AUTH_USERS_FILE=/app/users.txt \
  -e "JAVA_TOOL_OPTIONS=$OPTIONEN" "$SERVER_IMAGE" >/dev/null || exit 1
docker cp "$SCRATCH/users.txt" "$SERVER:/app/users.txt" >/dev/null || exit 1
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
pruefe "Server im Container meldet 'gestartet, Port 1433'" gestartet
pruefe "JAVA_TOOL_OPTIONS erreicht die JVM ($OPTIONEN)" \
  sh -c "docker logs '$SERVER' 2>&1 | grep -q 'Picked up JAVA_TOOL_OPTIONS: $OPTIONEN'"

docker run --rm --network "$NETZ" -e "TDSWIRE_ZIEL=$SERVER:1433" "$CLIENT_IMAGE" >"$SCRATCH/client" 2>&1
client_ok=$?
pruefe "Rohbytes-Client im zweiten Container meldet sich an (PRELOGIN, LOGIN7, LOGINACK)" \
  sh -c "[ $client_ok -eq 0 ] && grep -q 'laufzeit-client: Login ok' '$SCRATCH/client'"
[ "$client_ok" -eq 0 ] || cat "$SCRATCH/client"

docker stop "$SERVER" >/dev/null
ende="$(docker inspect --format '{{.State.ExitCode}}' "$SERVER")"
# 143 = 128 + SIGTERM: die JVM endet nach dem Shutdown-Hook mit diesem Code (gemessen am 2026-10-31).
pruefe "docker stop beendet den Prozess durch SIGTERM, Exit-Code 143 (gemessen: $ende)" test "$ende" = "143"
pruefe "Ausgabe des Servers nennt 'gestoppt'" sh -c "docker logs '$SERVER' 2>&1 | grep -q 'tdswire: gestoppt'"
pruefe "Ausgabe des Servers nennt weder Benutzer noch Passwort" \
  sh -c "! docker logs '$SERVER' 2>&1 | grep -Eq 'alice|secret'"

echo "laufzeittest: Laufzeit $(( $(date +%s) - start )) s (Builds eingeschlossen)"
[ "$FEHLER" -eq 0 ] || { echo "laufzeittest: ROT"; exit 1; }
echo "laufzeittest: gruen"
