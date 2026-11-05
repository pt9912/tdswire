#!/usr/bin/env bash
# tools/testserver.sh - Testserver von Hand starten und stoppen (TDS-RB-01, TDS-QA-06, ADR-0025; Dockerfile-Stage
# `testserver`). Aufruf ueber `make testserver-up|-down|-logs|-status`; BUILDER_FLAGS kommt aus der Umgebung.
#
# Zusage: `up` startet den Container im Hintergrund, veroeffentlicht den TDS-Port auf TESTSERVER_HOST (Voreinstellung
# Loopback) und kehrt zurueck, sobald das Log `tdswire: gestartet, Port 1433` zeigt; `down` entfernt den Container. Das Image ist
# ein lokales Test-Image ohne Registry (ADR-0025 Entscheidung 4). Nicht belegt: Annahme durch einen Client ausser dem
# Rohbytes-Client des Selbsttests, TLS im Handlauf.
# Eingaenge (Umgebung): TESTSERVER_NAME, TESTSERVER_IMAGE, TESTSERVER_HOST, TESTSERVER_PORT, TESTSERVER_BAUEN (0 = Image
# nicht bauen), TESTSERVER_VERSCHLUESSELUNG (TEST_UNENCRYPTED | REQUIRED), TESTSERVER_BENUTZER_DATEI,
# TESTSERVER_BESCHREIBUNG, TESTSERVER_TLS_SPEICHER, TESTSERVER_TLS_PASSWORT_DATEI, TESTSERVER_FOLGEN (1 = Log verfolgen).
# Kopplung: tools/testserver-selbsttest.sh mutiert die Zeilen mit dem Vermerk `zweig:<name>`.
set -u

NAME="${TESTSERVER_NAME:-tdswire-testserver}"
IMAGE="${TESTSERVER_IMAGE:-tdswire-testserver:lokal}"
HOST="${TESTSERVER_HOST:-127.0.0.1}"
PORT="${TESTSERVER_PORT:-1433}"
VERSCHLUESSELUNG="${TESTSERVER_VERSCHLUESSELUNG:-TEST_UNENCRYPTED}"
INTERN_PORT=1433

vorhanden() { docker inspect "$NAME" >/dev/null 2>&1; }

up() {
  if vorhanden; then # zweig:zweiter-up
    echo "testserver-up: der Container $NAME existiert schon; zuerst 'make testserver-down'" >&2; exit 1 # zweig:zweiter-up
  fi # zweig:zweiter-up
  if [ "$VERSCHLUESSELUNG" = "REQUIRED" ]; then
    if [ -z "${TESTSERVER_TLS_SPEICHER:-}" ] || [ -z "${TESTSERVER_TLS_PASSWORT_DATEI:-}" ]; then
      echo "testserver-up: REQUIRED verlangt TESTSERVER_TLS_SPEICHER und TESTSERVER_TLS_PASSWORT_DATEI" >&2; exit 1
    fi
  elif [ "$VERSCHLUESSELUNG" != "TEST_UNENCRYPTED" ]; then
    echo "testserver-up: TESTSERVER_VERSCHLUESSELUNG ist TEST_UNENCRYPTED oder REQUIRED" >&2; exit 1
  fi
  if [ -n "${TESTSERVER_BENUTZER_DATEI:-}" ] && [ ! -f "$TESTSERVER_BENUTZER_DATEI" ]; then
    echo "testserver-up: Benutzerliste $TESTSERVER_BENUTZER_DATEI nicht gefunden" >&2; exit 1
  fi
  if [ -n "${TESTSERVER_BESCHREIBUNG:-}" ] && [ ! -f "$TESTSERVER_BESCHREIBUNG" ]; then
    echo "testserver-up: Beschreibungsdatei $TESTSERVER_BESCHREIBUNG nicht gefunden" >&2; exit 1
  fi

  if [ "${TESTSERVER_BAUEN:-1}" != "0" ]; then
    # shellcheck disable=SC2086
    docker build ${BUILDER_FLAGS:-} --load --target testserver -t "$IMAGE" . || exit 1
  fi

  scratch="$(mktemp -d)"
  trap 'rm -rf "$scratch"' EXIT
  benutzer="${TESTSERVER_BENUTZER_DATEI:-}"
  if [ -z "$benutzer" ]; then
    benutzer="$scratch/users.txt"
    printf 'alice\t{plain}secret\n' >"$benutzer"
  fi
  env_args=(-e LISTEN_HOST=0.0.0.0 -e "LISTEN_PORT=$INTERN_PORT" -e "ENCRYPTION_POLICY=$VERSCHLUESSELUNG"
    -e AUTH_USERS_FILE=/home/gradle/users.txt)
  [ "$VERSCHLUESSELUNG" = "TEST_UNENCRYPTED" ] && env_args+=(-e AUTH_PLAIN_TESTMODE=true)
  if [ -n "${TESTSERVER_BESCHREIBUNG:-}" ]; then
    env_args+=(-e SIMULATION_DESCRIPTION_FILE=/home/gradle/sim.yaml)
  else
    env_args+=(-e SIMULATION_DESCRIPTION_FILE=/src/src/testserver/resources/manuell/sim.yaml)
  fi
  if [ "$VERSCHLUESSELUNG" = "REQUIRED" ]; then
    env_args+=(-e TLS_KEYSTORE_FILE=/home/gradle/keystore.p12 -e TLS_KEYSTORE_PASSWORD_FILE=/home/gradle/keystore.pw)
  fi

  # Die Dateien kommen per `docker cp` vor dem Start in den Schreibbereich des Containers (nichts wird gemountet).
  docker create --name "$NAME" -p "$HOST:$PORT:$INTERN_PORT" "${env_args[@]}" "$IMAGE" >/dev/null || exit 1
  docker cp "$benutzer" "$NAME:/home/gradle/users.txt" >/dev/null || { docker rm -f "$NAME" >/dev/null 2>&1; exit 1; }
  if [ -n "${TESTSERVER_BESCHREIBUNG:-}" ]; then
    docker cp "$TESTSERVER_BESCHREIBUNG" "$NAME:/home/gradle/sim.yaml" >/dev/null || { docker rm -f "$NAME" >/dev/null 2>&1; exit 1; }
  fi
  if [ "$VERSCHLUESSELUNG" = "REQUIRED" ]; then
    docker cp "$TESTSERVER_TLS_SPEICHER" "$NAME:/home/gradle/keystore.p12" >/dev/null || { docker rm -f "$NAME" >/dev/null 2>&1; exit 1; }
    docker cp "$TESTSERVER_TLS_PASSWORT_DATEI" "$NAME:/home/gradle/keystore.pw" >/dev/null || { docker rm -f "$NAME" >/dev/null 2>&1; exit 1; }
  fi

  if ! docker start "$NAME" >"$scratch/start" 2>&1; then # zweig:port-belegt
    echo "testserver-up: Start fehlgeschlagen (Port $PORT auf $HOST belegt?)" >&2; cat "$scratch/start" >&2 # zweig:port-belegt
    docker rm -f "$NAME" >/dev/null 2>&1 # zweig:port-belegt
    exit 1 # zweig:port-belegt
  fi # zweig:port-belegt

  for _ in $(seq 1 60); do
    if docker logs "$NAME" 2>&1 | grep -q "tdswire: gestartet, Port $INTERN_PORT"; then bereit=1; break; fi
    if [ "$(docker inspect --format '{{.State.Running}}' "$NAME")" != "true" ]; then break; fi
    sleep 1
  done
  if [ "${bereit:-0}" != "1" ]; then
    echo "testserver-up: der Server wurde nicht bereit; Log:" >&2
    docker logs "$NAME" >&2
    docker rm -f "$NAME" >/dev/null 2>&1
    exit 1
  fi

  echo "testserver-up: bereit auf $HOST:$PORT (Container $NAME, Image $IMAGE)"
  if [ -z "${TESTSERVER_BENUTZER_DATEI:-}" ]; then
    echo "testserver-up: Benutzer alice, Kennwort secret"
  else
    echo "testserver-up: Benutzerliste aus $TESTSERVER_BENUTZER_DATEI"
  fi
  if [ "$VERSCHLUESSELUNG" = "TEST_UNENCRYPTED" ]; then
    echo "testserver-up: Testwerte, keine Verschluesselung (TDS 7.4 im Klartext)"
  else
    echo "testserver-up: Verschluesselung REQUIRED mit dem Schluesselspeicher aus TESTSERVER_TLS_SPEICHER"
  fi
  case "$HOST" in
    127.0.0.1 | localhost | ::1) ;;
    *) echo "testserver-up: WARNUNG: $HOST ist nicht Loopback; der Server ist im Netz erreichbar" ;;
  esac
  echo "testserver-up: Stop mit 'make testserver-down', Log mit 'make testserver-logs'"
}

down() {
  if ! vorhanden; then # zweig:down-ohne-container
    echo "testserver-down: kein Container $NAME, nichts zu tun" # zweig:down-ohne-container
    exit 0 # zweig:down-ohne-container
  fi # zweig:down-ohne-container
  docker stop "$NAME" >/dev/null 2>&1
  docker rm -f "$NAME" >/dev/null || exit 1
  echo "testserver-down: Container $NAME gestoppt und entfernt"
}

case "${1:-}" in
  up) up ;;
  down) down ;;
  logs)
    vorhanden || { echo "testserver-logs: kein Container $NAME" >&2; exit 1; }
    if [ "${TESTSERVER_FOLGEN:-0}" = "1" ]; then docker logs -f "$NAME"; else docker logs "$NAME"; fi
    ;;
  status)
    if [ "$(docker inspect --format '{{.State.Running}}' "$NAME" 2>/dev/null)" = "true" ]; then
      echo "testserver-status: laeuft ($NAME, $(docker port "$NAME" "$INTERN_PORT/tcp" | head -1))"
    else
      echo "testserver-status: nicht gestartet"; exit 1
    fi
    ;;
  *) echo "Aufruf: tools/testserver.sh up|down|logs|status" >&2; exit 2 ;;
esac
