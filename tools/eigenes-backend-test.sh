#!/usr/bin/env bash
# tools/eigenes-backend-test.sh - Lauf mit eigenem In-Memory-Backend: der .NET-Referenzclient gegen den Serverprozess,
# dessen Backend eine eigene `QueryBackend`-Implementierung ist (TDS-FA-08, TDS-RB-04, ADR-0034). Aufruf ueber
# `make eigenes-backend`; BUILDER_FLAGS kommt aus der Umgebung (leer ohne BUILDER).
#
# Ablauf: Server-Image (Dockerfile-Stage `eigenesbackendserver`: testserver/.../EigenesBackendServer.kt, nur oeffentliche
# Flaeche der Bibliothek, kein Simulations-Backend) und Client-Image (referenztest-dotnet, Stage `eigenes-backend`)
# bauen; beide Container laufen in einem eigenen internen Netzwerk (kein Zugang nach aussen, nichts wird gemountet).
# Die Tests des Referenzclients (Kategorie EigenesBackend) lesen ein gefuelltes und ein leeres Ergebnis und die
# Fehlerpfade des Backends (Fehler am Start, Fehler nach Zeilen, `execute` wirft). Danach prueft der Lauf am Log des
# Servers, dass jede Anfrage das eigene Backend erreicht hat (Zeilen `eigenes-backend: anfrage <Tabelle>`, gezaehlt je
# Tabelle), dass der Server laeuft und dass weder Kennwort noch Text der Ausnahme oder des Fehlers in den Zeilen des Backends stehen.
# Zusage: die vier Tests bestehen gegen den Serverprozess; nicht belegt steht im Kopf von
# referenztest-dotnet/EigenesBackend/EigenesBackendTests.cs. Der Lauf ist Stufe 8 von `make e2e` und kein Gate (ADR-0022).
set -u

LAUF="tdswire-eb-$$"
NETZ="$LAUF-netz"
SERVER="$LAUF-server"
SERVER_IMAGE="tdswire-eigenesbackendserver:$LAUF"
CLIENT_IMAGE="tdswire-eigenesbackendclient:$LAUF"
SCRATCH="$(mktemp -d)"
FEHLER=0

aufraeumen() {
  docker rm -f "$SERVER" >/dev/null 2>&1
  docker network rm "$NETZ" >/dev/null 2>&1
  docker rmi -f "$SERVER_IMAGE" "$CLIENT_IMAGE" >/dev/null 2>&1
  rm -rf "$SCRATCH"
}
trap aufraeumen EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

pruefe() { # pruefe <Beschreibung> <Befehl...>
  local beschreibung="$1"
  shift
  if "$@"; then echo "eigenes-backend: ok   $beschreibung"; else echo "eigenes-backend: FEHLER $beschreibung"; FEHLER=1; fi
}

start=$(date +%s)
# shellcheck disable=SC2086
docker build ${BUILDER_FLAGS:-} --load --target eigenesbackendserver -t "$SERVER_IMAGE" . || exit 1
# shellcheck disable=SC2086
docker build ${BUILDER_FLAGS:-} --load --target eigenes-backend -t "$CLIENT_IMAGE" referenztest-dotnet || exit 1
gebaut=$(date +%s)

docker network create --internal "$NETZ" >/dev/null || exit 1
docker run -d --name "$SERVER" --network "$NETZ" "$SERVER_IMAGE" >/dev/null || exit 1

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
[ "$FEHLER" -eq 0 ] || { docker logs "$SERVER" 2>&1 | tail -20; echo "eigenes-backend: ROT"; exit 1; }

docker run --rm --network "$NETZ" -e "TDSWIRE_ZIEL=$SERVER:1433" "$CLIENT_IMAGE" >"$SCRATCH/client" 2>&1
client_ok=$?
cat "$SCRATCH/client"
fertig=$(date +%s)

pruefe "Alle 4 Tests des Referenzclients bestanden" \
  sh -c "[ $client_ok -eq 0 ] && grep -q 'Test Run Successful' '$SCRATCH/client' && grep -q 'Total tests: 4' '$SCRATCH/client' && grep -q 'Passed: 4' '$SCRATCH/client'"

# Jede Anfrage der Tests erreicht das eigene Backend; die Simulation kennt keine dieser Tabellen. Erwartet (Kopplung:
# EigenesBackendTests.cs): eigen 6 (gefuellt 1, Folgeabfrage nach fehler 1, nach fehler_nach_zeilen 1, drei neue Verbindungen
# nach wirft 3), leer 1, fehler 1, fehler_nach_zeilen 1, wirft 1.
anfragen_an() { docker logs "$SERVER" 2>&1 | grep -cx "eigenes-backend: anfrage $1"; }
anfragen_je_tabelle() { # anfragen_je_tabelle <Tabelle> <erwartete Zahl>
  local ist
  ist="$(anfragen_an "$1")"
  echo "eigenes-backend: Anfragen an $1: $ist (erwartet $2)"
  [ "$ist" = "$2" ]
}
pruefe "Backend: 6 Anfragen an eigen" anfragen_je_tabelle eigen 6
pruefe "Backend: 1 Anfrage an leer" anfragen_je_tabelle leer 1
pruefe "Backend: 1 Anfrage an fehler" anfragen_je_tabelle fehler 1
pruefe "Backend: 1 Anfrage an fehler_nach_zeilen" anfragen_je_tabelle fehler_nach_zeilen 1
pruefe "Backend: 1 Anfrage an wirft" anfragen_je_tabelle wirft 1

laeuft() { [ "$(docker inspect --format '{{.State.Running}}' "$SERVER")" = "true" ]; }
pruefe "Server laeuft nach den Tests" laeuft
# Grenze: TdsServer.start laeuft ohne Senke, die Bibliothek schreibt kein Log; geprueft ist nur, dass das Backend des Testservers
# selbst nichts davon schreibt. Dass die Bibliothek nichts ausgibt, belegt dieser Punkt nicht (der Client-Teil prueft die Meldung).
pruefe "Ausgabe des Testservers (nur Zeilen des Backends) nennt weder Kennwort noch Text der Ausnahme oder des Fehlers" \
  sh -c "! docker logs '$SERVER' 2>&1 | grep -Eq 'secret|Geheimnis|Text des Backends'"

echo "eigenes-backend: --- Log des Servers ---"
docker logs "$SERVER" 2>&1
echo "eigenes-backend: --- Ende des Logs ---"

echo "eigenes-backend: Laufzeit $((fertig - start)) s, davon Bau $((gebaut - start)) s und Tests $((fertig - gebaut)) s"
[ "$FEHLER" -eq 0 ] || { echo "eigenes-backend: ROT"; exit 1; }
echo "eigenes-backend: gruen"
