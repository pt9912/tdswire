#!/usr/bin/env bash
# tools/kompatibilitaetstest.sh - Kompatibilitaetslauf: der .NET-Referenzclient gegen den Serverprozess mit
# Simulations-Backend (ADR-0019, ADR-0008, TDS-QA-01). Aufruf ueber `make kompatibilitaet KATEGORIE=<Name>`;
# BUILDER_FLAGS kommt aus der Umgebung (leer ohne BUILDER). Die Kategorie waehlt die Tests des Referenzclients
# (`[Trait("Kategorie", ...)]` in referenztest-dotnet/Kompatibilitaet): Szenario, Anmeldung, Luecken oder Synchronitaet.
#
# Ablauf: Server-Image (Dockerfile-Stage `kompatibilitaetsserver`: JVM, Richtlinie REQUIRED, Schluesselspeicher aus
# `testkeys`) und Client-Image (referenztest-dotnet, Stage `kompatibilitaet`) bauen; zwei Container in einem
# eigenen internen Netzwerk (kein Zugang nach aussen, kein veroeffentlichter Port, nichts wird gemountet; Namen mit
# PID). Warten auf die Startzeile des Servers (Bereitschaftsschleife). Das Zertifikat des Servers kommt per
# `docker cp` zum Client und ist dort der Vertrauensanker (SSL_CERT_FILE). Danach laufen die Tests der Kategorie;
# der Lauf prueft, dass der Server danach laeuft, keinen Handlerfehler und kein Kennwort geloggt hat, und gibt das
# Log des Servers aus (Zeilen `kompatibilitaet: anfrage` zeigen den Text, den der Referenzclient stellt).
# `trap` raeumt bei Ende, SIGINT und SIGTERM Container, Netzwerk und Images ab; bei SIGKILL bleiben sie stehen.
# Zusage: die Tests der Kategorie bestehen gegen den Serverprozess; nicht belegt steht im Kopf der Testklassen.
# Der Lauf ist eine Stufe von `make e2e` und kein Gate (ADR-0022).
set -u

KATEGORIE="${1:-}"
case "$KATEGORIE" in
  Szenario | Anmeldung | Luecken | Synchronitaet) ;;
  *) echo "kompatibilitaetstest: KATEGORIE muss Szenario, Anmeldung, Luecken oder Synchronitaet sein (war '$KATEGORIE')" >&2; exit 2 ;;
esac

LAUF="tdswire-kp-$$"
NETZ="$LAUF-netz"
SERVER="$LAUF-server"
CLIENT="$LAUF-client"
SERVER_IMAGE="tdswire-kompatibilitaetsserver:$LAUF"
CLIENT_IMAGE="tdswire-kompatibilitaetsclient:$LAUF"
SCRATCH="$(mktemp -d)"
FEHLER=0

aufraeumen() {
  docker rm -f "$CLIENT" "$SERVER" >/dev/null 2>&1
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
  if "$@"; then echo "kompatibilitaetstest: ok   $beschreibung"; else echo "kompatibilitaetstest: FEHLER $beschreibung"; FEHLER=1; fi
}

start=$(date +%s)
# shellcheck disable=SC2086
docker build ${BUILDER_FLAGS:-} --load --target kompatibilitaetsserver -t "$SERVER_IMAGE" . || exit 1
# shellcheck disable=SC2086
docker build ${BUILDER_FLAGS:-} --load --target kompatibilitaet -t "$CLIENT_IMAGE" referenztest-dotnet || exit 1
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
[ "$FEHLER" -eq 0 ] || { docker logs "$SERVER" 2>&1 | tail -20; echo "kompatibilitaetstest: ROT"; exit 1; }

docker cp "$SERVER:/home/gradle/server.pem" "$SCRATCH/server.pem" >/dev/null || exit 1
docker create --name "$CLIENT" --network "$NETZ" -e "TDSWIRE_ZIEL=$SERVER:1433" -e SSL_CERT_FILE=/tmp/server.pem \
  "$CLIENT_IMAGE" dotnet test --no-build --nologo --filter "Kategorie=$KATEGORIE" \
  --logger "console;verbosity=detailed" >/dev/null || exit 1
docker cp "$SCRATCH/server.pem" "$CLIENT:/tmp/server.pem" >/dev/null || exit 1
docker start -a "$CLIENT" >"$SCRATCH/client" 2>&1
client_ok=$?
cat "$SCRATCH/client"
fertig=$(date +%s)

alle_bestanden() {
  [ "$client_ok" -eq 0 ] && grep -q 'Test Run Successful' "$SCRATCH/client" &&
    grep -Eq 'Total tests: [1-9]' "$SCRATCH/client" && ! grep -Eq 'Failed: [1-9]' "$SCRATCH/client"
}
pruefe "Alle Tests der Kategorie $KATEGORIE bestanden ($(grep -Eo 'Total tests: [0-9]+' "$SCRATCH/client" | head -1))" alle_bestanden

laeuft() { [ "$(docker inspect --format '{{.State.Running}}' "$SERVER")" = "true" ]; }
pruefe "Server laeuft nach den Tests" laeuft
pruefe "Log des Servers nennt keinen Handlerfehler" sh -c "! docker logs '$SERVER' 2>&1 | grep -q 'Handlerfehler'"
pruefe "Log des Servers nennt weder Kennwort noch Text der Quellenausnahme" \
  sh -c "! docker logs '$SERVER' 2>&1 | grep -Eq 'secret|Geheimnis'"

if [ "$KATEGORIE" = Anmeldung ]; then
  # Der Client beobachtet nur, dass die zweite Nutzung der gepoolten Verbindung gelingt; ob sie dieselbe Sitzung am
  # Server ist, zeigt das Log (Marker Id = 9101 und Id = 9102 in AnmeldungTests).
  sitzung_von() { docker logs "$SERVER" 2>&1 | grep -F "$1" | head -1 | sed 's/.*session=\([0-9a-f]*\) .*/\1/'; }
  gleiche_sitzung() {
    local a b
    a="$(sitzung_von 'WHERE Id = 9101]')"
    b="$(sitzung_von 'WHERE Id = 9102]')"
    [ -n "$a" ] && [ "$a" = "$b" ]
  }
  pruefe "Pooling: beide Anfragen laufen auf derselben Sitzung des Servers" gleiche_sitzung
  # Die Texte, die der Referenzclient bei SchemaOnly und KeyInfo stellt (Form C von ADR-0012); das Log ist die Messung.
  set_texte() {
    docker logs "$SERVER" 2>&1 | grep -qF 'sql=[ SET FMTONLY OFF; SET FMTONLY ON;SELECT Id, Name FROM person SET FMTONLY OFF;]' &&
      docker logs "$SERVER" 2>&1 | grep -qF 'sql=[ SET FMTONLY OFF; SET NO_BROWSETABLE ON;SELECT Id, Name FROM person SET NO_BROWSETABLE OFF;]'
  }
  pruefe "Log nennt die SET-Texte von SchemaOnly und KeyInfo" set_texte
fi

if [ "$KATEGORIE" = Luecken ]; then
  # Der Client sieht in beiden Faellen nur 18456; den Grund der Ablehnung nennt allein das Log des Servers.
  grund() { docker logs "$SERVER" 2>&1 | grep -qF "Login: abgelehnt ($1)"; }
  pruefe "Log nennt als Grund des Kennwortwechsels UNSUPPORTED_PASSWORD_CHANGE" grund UNSUPPORTED_PASSWORD_CHANGE
  pruefe "Log nennt als Grund des Zugriffstokens UNSUPPORTED_FEDERATED_AUTHENTICATION" grund UNSUPPORTED_FEDERATED_AUTHENTICATION
  pruefe "Log nennt TDS_PROTOCOL_ERROR (mehrere RPCs) und TDS_UNSUPPORTED_TYPE (xml)" \
    sh -c "docker logs '$SERVER' 2>&1 | grep -qF 'tdswire: Fehler: TDS_PROTOCOL_ERROR' && docker logs '$SERVER' 2>&1 | grep -qF 'tdswire: Fehler: TDS_UNSUPPORTED_TYPE'"
fi

echo "kompatibilitaetstest: --- Log des Servers ---"
docker logs "$SERVER" 2>&1
echo "kompatibilitaetstest: --- Ende des Logs ---"

echo "kompatibilitaetstest: Laufzeit $((fertig - start)) s, davon Bau $((gebaut - start)) s und Tests $((fertig - gebaut)) s"
[ "$FEHLER" -eq 0 ] || { echo "kompatibilitaetstest: ROT"; exit 1; }
echo "kompatibilitaetstest: gruen"
