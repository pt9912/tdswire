#!/usr/bin/env bash
# tools/testserver-selbsttest.sh - Selbsttest von tools/testserver.sh (TDS-RB-01, TDS-QA-06, ADR-0025). Aufruf ueber
# `make testserver-selbsttest`; kein Gate (ADR-0022). Ablauf: up, Bindung an Loopback, Rohbytes-Login vom Host-Netz aus, zweiter
# up, down, down ohne Container, Port belegt, Eingabefehler, ungueltige Beschreibung, Warnung bei fremdem Host, eigene Dateien;
# danach je Zweig eine Mutation des Skripts, die genau die Pruefung dieses Zweigs rot faerbt.
# Zusage: Start, Login ueber den veroeffentlichten Port und Stop; danach kein Container und der Port frei; die Zweige mit
# Vermerk `zweig:` im Skript sind je durch eine Mutation belegt. Nicht belegt: TLS, der Abbruch von up durch SIGINT, die
# Fehlerpfade des `docker cp`, ein Login mit der eigenen Benutzerliste.
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
meldet() { grep -q "$1" "$2"; } # meldet <Muster> <Datei>
loopback() { docker port "$LAUF" 1433/tcp | grep -q '^127\.0\.0\.1:'; }

# --- Pruefungen je Zweig; Argument 1 ist das Skript (das echte oder eine Mutation) --------------------------
zweiter_up_ok() { # gruen, wenn up bei vorhandenem Container abbricht und 'existiert schon' meldet
  docker create --name "$LAUF" "$IMAGE" >/dev/null
  bash "$1" up >"$SCRATCH/m" 2>&1
  local r=$?
  docker rm -f "$LAUF" >/dev/null 2>&1
  [ $r -ne 0 ]
}
zweiter_up_meldung_ok() {
  docker create --name "$LAUF" "$IMAGE" >/dev/null
  bash "$1" up >"$SCRATCH/m" 2>&1
  docker rm -f "$LAUF" >/dev/null 2>&1
  grep -q 'existiert schon' "$SCRATCH/m"
}
down_leer_ok() { bash "$1" down >/dev/null 2>&1; }
port_belegt() { # port_belegt <Skript>: up bei belegtem Port; Ausgabe in $SCRATCH/m, Code in $PB_CODE, Rest in $PB_REST
  docker run -d --name "$LAUF-besetzt" -p "127.0.0.1:$PORT:1433" --entrypoint sleep "$IMAGE" 120 >/dev/null
  bash "$1" up >"$SCRATCH/m" 2>&1
  PB_CODE=$?
  docker rm -f "$LAUF-besetzt" >/dev/null 2>&1
  PB_REST=1
  docker inspect "$LAUF" >/dev/null 2>&1 || PB_REST=0
  docker rm -f "$LAUF" >/dev/null 2>&1
}
port_belegt_aufraeumen_ok() { port_belegt "$1"; [ $PB_CODE -ne 0 ] && [ $PB_REST -eq 0 ]; }
port_belegt_meldung_ok() { port_belegt "$1"; meldet 'belegt' "$SCRATCH/m"; }
kaputte_beschreibung() { printf 'schema_format: "9"\n' >"$SCRATCH/kaputt.yaml"; }
nicht_bereit() { # nicht_bereit <Skript>: Beschreibungsdatei, die der Server abweist; Code in $NB_CODE, Rest in $NB_REST
  kaputte_beschreibung
  TESTSERVER_BESCHREIBUNG="$SCRATCH/kaputt.yaml" TESTSERVER_WARTEN=30 bash "$1" up >"$SCRATCH/m" 2>&1
  NB_CODE=$?
  NB_REST=1
  docker inspect "$LAUF" >/dev/null 2>&1 || NB_REST=0
  docker rm -f "$LAUF" >/dev/null 2>&1
}
nicht_bereit_aufraeumen_ok() { nicht_bereit "$1"; [ $NB_CODE -ne 0 ] && [ $NB_REST -eq 0 ]; }
nicht_bereit_meldung_ok() { nicht_bereit "$1"; meldet 'nicht bereit' "$SCRATCH/m"; }
loopback_ok() { # gruen, wenn die Voreinstellung an Loopback bindet
  env -u TESTSERVER_HOST bash "$1" up >/dev/null 2>&1
  loopback
  local r=$?
  bash tools/testserver.sh down >/dev/null 2>&1
  return $r
}
warnung_ok() { # gruen, wenn ein Host ausser Loopback gewarnt wird
  TESTSERVER_HOST=0.0.0.0 bash "$1" up >"$SCRATCH/m" 2>&1
  bash tools/testserver.sh down >/dev/null 2>&1
  meldet 'WARNUNG' "$SCRATCH/m"
}

start=$(date +%s)
# shellcheck disable=SC2086
docker build ${BUILDER_FLAGS:-} --load --target laufzeit-client -t "$CLIENT_IMAGE" . || exit 1

# --- Ablauf mit dem echten Skript ----------------------------------------------------------------------
bash tools/testserver.sh up >"$SCRATCH/up" 2>&1
code=$?
cat "$SCRATCH/up"
pruefe "up endet mit 0 und nennt Testwerte ohne Verschluesselung und den Benutzer" \
  sh -c "[ $code -eq 0 ] && grep -q 'Testwerte, keine Verschluesselung' '$SCRATCH/up' && grep -q 'alice' '$SCRATCH/up'"
pruefe "die Voreinstellung bindet den Port an 127.0.0.1" loopback
pruefe "up ohne fremden Host warnt nicht" sh -c "! grep -q WARNUNG '$SCRATCH/up'"
pruefe "status meldet laeuft" bash tools/testserver.sh status
docker run --rm --network host -e "TDSWIRE_ZIEL=127.0.0.1:$PORT" "$CLIENT_IMAGE" >"$SCRATCH/client" 2>&1
pruefe "Rohbytes-Login vom Host-Netz gegen den veroeffentlichten Port" meldet 'laufzeit-client: Login ok' "$SCRATCH/client"
bash tools/testserver.sh up >"$SCRATCH/zweiter" 2>&1
code=$?
pruefe "zweiter up bricht ab und nennt den Container" sh -c "[ $code -ne 0 ] && grep -q 'existiert schon' '$SCRATCH/zweiter'"
bash tools/testserver.sh down >/dev/null 2>&1
pruefe "down entfernt den Container" keiner
pruefe "Port nach down frei" port_frei
pruefe "nach down steht kein Netzwerk des Laufs" sh -c "! docker network ls --format '{{.Name}}' | grep -q '$LAUF'"
pruefe "down ohne Container ist ein Erfolg" bash tools/testserver.sh down

export TESTSERVER_BAUEN=0
pruefe "Eingabefehler: fehlende Benutzerliste" sh -c "! TESTSERVER_BENUTZER_DATEI=/nein bash tools/testserver.sh up >'$SCRATCH/e' 2>&1 && grep -q 'nicht gefunden' '$SCRATCH/e'"
pruefe "Eingabefehler: fehlende Beschreibungsdatei" sh -c "! TESTSERVER_BESCHREIBUNG=/nein bash tools/testserver.sh up >'$SCRATCH/e' 2>&1 && grep -q 'nicht gefunden' '$SCRATCH/e'"
pruefe "Eingabefehler: REQUIRED ohne Schluesselspeicher" sh -c "! TESTSERVER_VERSCHLUESSELUNG=REQUIRED bash tools/testserver.sh up >'$SCRATCH/e' 2>&1 && grep -q 'TESTSERVER_TLS_SPEICHER' '$SCRATCH/e'"
pruefe "Eingabefehler: unbekannte Verschluesselung" sh -c "! TESTSERVER_VERSCHLUESSELUNG=EGAL bash tools/testserver.sh up >'$SCRATCH/e' 2>&1 && grep -q 'TEST_UNENCRYPTED oder REQUIRED' '$SCRATCH/e'"
pruefe "nach den Eingabefehlern steht kein Container" keiner
printf 'bob\t{plain}geheim\n' >"$SCRATCH/eigene-users.txt"
TESTSERVER_BENUTZER_DATEI="$SCRATCH/eigene-users.txt" TESTSERVER_BESCHREIBUNG=testserver/resources/manuell/sim.yaml \
  bash tools/testserver.sh up >"$SCRATCH/eigene" 2>&1
code=$?
pruefe "up mit eigener Benutzerliste und eigener Beschreibung endet mit 0 und nennt die Liste" \
  sh -c "[ $code -eq 0 ] && grep -q 'Benutzerliste aus' '$SCRATCH/eigene'"
bash tools/testserver.sh down >/dev/null 2>&1
pruefe "ungueltige Beschreibung: up bricht ab, meldet es, kein Container" nicht_bereit_aufraeumen_ok tools/testserver.sh
pruefe "ungueltige Beschreibung: Meldung" nicht_bereit_meldung_ok tools/testserver.sh
pruefe "Grundlage Warnung: Host ausser Loopback wird gewarnt" warnung_ok tools/testserver.sh

# --- Grundlagen und Mutationen ---------------------------------------------------------------------------
mutiere() { # mutiere <Name> <Zweig-Vermerk> <sed-Ausdruck> <Pruefung>
  local name="$1" zweig="$2" ausdruck="$3" pruefung="$4" ziel="$SCRATCH/mutant-$1.sh"
  sed "/zweig:$zweig/ $ausdruck" tools/testserver.sh >"$ziel"
  if cmp -s tools/testserver.sh "$ziel"; then echo "testserver-selbsttest: FEHLER Mutation $name aendert nichts"; FEHLER=1; return; fi
  if "$pruefung" "$ziel"; then echo "testserver-selbsttest: FEHLER Mutation $name bleibt gruen"; FEHLER=1
  else echo "testserver-selbsttest: ok   Mutation $name faerbt rot"; fi
}
for p in zweiter_up_ok zweiter_up_meldung_ok down_leer_ok port_belegt_aufraeumen_ok port_belegt_meldung_ok loopback_ok; do
  pruefe "Grundlage: das unveraenderte Skript besteht $p" "$p" tools/testserver.sh
done
mutiere zweiter-up-abbruch zweiter-up 's/exit 1/exit 0/' zweiter_up_ok
mutiere zweiter-up-meldung zweiter-up 's/existiert schon/laeuft/' zweiter_up_meldung_ok
mutiere down-ohne-container down-ohne-container 's/exit 0/exit 1/' down_leer_ok
mutiere port-belegt-aufraeumen port-belegt 's/docker rm -f "$NAME" >\/dev\/null 2>&1/true/' port_belegt_aufraeumen_ok
mutiere port-belegt-meldung port-belegt 's/if ! docker start/if false \&\& docker start/' port_belegt_meldung_ok
mutiere nicht-bereit-aufraeumen nicht-bereit 's/docker rm -f "$NAME" >\/dev\/null 2>&1/true/' nicht_bereit_aufraeumen_ok
mutiere nicht-bereit-meldung nicht-bereit 's/nicht bereit/fertig/' nicht_bereit_meldung_ok
mutiere loopback loopback 's/127\.0\.0\.1/0.0.0.0/' loopback_ok
mutiere warnung warnung 's/WARNUNG/INFO/' warnung_ok

echo "testserver-selbsttest: Laufzeit $(( $(date +%s) - start )) s (Builds eingeschlossen)"
[ "$FEHLER" -eq 0 ] || { echo "testserver-selbsttest: ROT"; exit 1; }
echo "testserver-selbsttest: gruen"
