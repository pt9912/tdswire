#!/usr/bin/env bash
# tools/simulationstest.sh - Integrationslauf des Simulations-Backends mit dem .NET-Referenzclient (SPEC-040,
# ADR-0012, ADR-0008). Aufruf ueber `make simulations-integration`; BUILDER_FLAGS kommt aus der Umgebung (leer
# ohne BUILDER).
#
# Ablauf: Testserver-Image (Dockerfile-Stage `integrationsserver`, JVM mit begrenztem Heap, Tabellen von
# IntegrationServer.kt, Beschreibung aus testserver/resources/integration/sim.yaml) und Client-Image
# (referenztest-dotnet, Stage `integration`) bauen; beide Container laufen in einem eigenen internen Netzwerk
# (kein Zugang nach aussen, nichts wird gemountet). Der Lauf prueft, dass der Text der Tabelle `gross` groesser ist
# als der Heap des Servers, fuehrt die Tests des Referenzclients aus und prueft, dass der Server danach laeuft und
# kein Kennwort ausgegeben hat. Der Testserver laeuft mit LOG_LEVEL=DEBUG und schreibt je Ereignis der Bibliothek eine
# JSON-Zeile (ADR-0020); der Lauf liest das Ereignis `simulation_backend_ready` mit der Zahl der Tabellen und prueft, dass
# kein Ereignis `connection_failed` oder `adapter_failed` (Ausnahme im Handler oder im Authentifizierungsadapter) steht.
# Zusage: Auswahl mit Bedingung, leeres Ergebnis, Zeilenquelle ueber dem Heap, Fehler der Zeilenquelle mit
# Folgeabfrage und Cancel einer laufenden Anfrage (Quelle `laufend` ohne Ende) mit Folgeabfrage am Referenzclient
# (Microsoft.Data.SqlClient), TDS 7.4 ohne Verschluesselung und ohne Pool; die Quelle von `laufend` wird nach dem
# Cancel genau einmal geschlossen (eine Zeile des Testservers je Schliessen); dazu das Massenladen mit `SqlBulkCopy` in die
# Tabelle `bulk_ziel` (Zeilen und Zaehler, mehrere Nachrichten, Schluesselverstoss 2627). Nicht belegt: echter SQL Server, TDS 8.0,
# Last. Der Lauf ist Stufe 3 von `make e2e` und kein Gate.
set -u
. "$(dirname "$0")/log-muster.sh" || { echo "log-muster.sh nicht lesbar" >&2; exit 1; }

LAUF="tdswire-sim-$$"
NETZ="$LAUF-netz"
SERVER="$LAUF-server"
SERVER_IMAGE="tdswire-integrationsserver:$LAUF"
CLIENT_IMAGE="tdswire-integrationsclient:$LAUF"
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
  local beschreibung="$1"
  shift
  if "$@"; then echo "simulationstest: ok   $beschreibung"; else echo "simulationstest: FEHLER $beschreibung"; FEHLER=1; fi
}

start=$(date +%s)
# shellcheck disable=SC2086
docker build ${BUILDER_FLAGS:-} --load --target integrationsserver -t "$SERVER_IMAGE" . || exit 1
# shellcheck disable=SC2086
docker build ${BUILDER_FLAGS:-} --load --target integration -t "$CLIENT_IMAGE" referenztest-dotnet || exit 1
gebaut=$(date +%s)

docker network create --internal "$NETZ" >/dev/null || exit 1
docker run -d --name "$SERVER" --network "$NETZ" -e LOG_LEVEL=DEBUG "$SERVER_IMAGE" >/dev/null || exit 1

gestartet() {
  local i
  for i in $(seq 1 120); do
    docker logs "$SERVER" 2>&1 | grep -q 'tdswire: gestartet, Port 1433' && return 0
    [ "$(docker inspect --format '{{.State.Running}}' "$SERVER")" = "true" ] || return 1
    sleep 1
  done
  return 1
}
pruefe "Server im Container meldet 'gestartet, Port 1433'" gestartet
# Die 6 Tabellen sind die fuenf von `IntegrationTables.all()` (IntegrationServer.kt: person, gross, kaputt, laufend, bulk_ziel) und `leer` aus
# testserver/resources/integration/sim.yaml (Kopplung: ein Wechsel der Tabellenzahl dort aendert diese Zahl).
simulations_backend_verdrahtet() {
  docker logs "$SERVER" 2>&1 | log_nennt_ereignis simulation_backend_ready '"tables":6'
}
pruefe "Log nennt das Ereignis simulation_backend_ready mit 6 Tabellen" simulations_backend_verdrahtet

# Der Text der Tabelle `gross` muss groesser sein als der Heap: sonst belegt der Lauf nicht, dass die Zeilen
# einzeln gelesen werden.
kopf="$(docker logs "$SERVER" 2>&1 | grep -m1 '^integrationsserver: Heap max ')"
heap="$(printf '%s\n' "$kopf" | sed -n 's/.*Heap max \([0-9][0-9]*\) Bytes.*/\1/p')"
text="$(printf '%s\n' "$kopf" | sed -n 's/.*gross \([0-9][0-9]*\) Zeichen.*/\1/p')"
echo "simulationstest: $kopf"
text_groesser_als_heap() { [ -n "$heap" ] && [ -n "$text" ] && [ "$text" -gt "$heap" ]; }
pruefe "Text von gross ($text Zeichen) ist groesser als der Heap ($heap Bytes)" text_groesser_als_heap

docker run --rm --network "$NETZ" -e "TDSWIRE_ZIEL=$SERVER:1433" "$CLIENT_IMAGE" >"$SCRATCH/client" 2>&1
client_ok=$?
cat "$SCRATCH/client"
pruefe "Alle 8 Tests des Referenzclients bestanden" \
  sh -c "[ $client_ok -eq 0 ] && grep -q 'Test Run Successful' '$SCRATCH/client' && grep -q 'Total tests: 8' '$SCRATCH/client' && grep -q 'Passed: 8' '$SCRATCH/client'"
fertig=$(date +%s)

quelle_einmal_geschlossen() { [ "$(docker logs "$SERVER" 2>&1 | grep -cx 'integrationsserver: Quelle laufend geschlossen')" = 1 ]; }
pruefe "Die Quelle von laufend ist nach dem Cancel genau einmal geschlossen" quelle_einmal_geschlossen

laeuft() { [ "$(docker inspect --format '{{.State.Running}}' "$SERVER")" = "true" ]; }
pruefe "Server laeuft nach den Tests" laeuft
kein_handlerfehler() { docker logs "$SERVER" 2>&1 | log_ohne_ereignis 'connection_failed|adapter_failed'; }
kein_kennwort() { docker logs "$SERVER" 2>&1 | log_ohne_text 'secret|Geheimnis'; }
pruefe "Log nennt keinen Handlerfehler und keinen Fehler des Authentifizierungsadapters" kein_handlerfehler
pruefe "Ausgabe des Testservers (Ereigniszeilen und Backend) nennt weder Kennwort noch Text der Quellenausnahme" kein_kennwort

echo "simulationstest: Laufzeit $((fertig - start)) s, davon Bau $((gebaut - start)) s und Tests $((fertig - gebaut)) s"
[ "$FEHLER" -eq 0 ] || { echo "simulationstest: ROT"; exit 1; }
echo "simulationstest: gruen"
