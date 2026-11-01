#!/usr/bin/env bash
# tools/vergleichslauf-d.sh - Vergleichslauf D: der .NET-Referenzclient gegen einen echten SQL Server im Container
# (ADR-0008 Option D, SPEC-045, TDS-QA-01). Aufruf ueber `make vergleichslauf-d [LAUF=<Name>]`; BUILDER_FLAGS kommt
# aus der Umgebung (leer ohne BUILDER). Kein Gate und keine Stufe von `make e2e`: der Lauf spricht den Server dieses
# Repos nicht an (ADR-0022).
#
# Laeufe (Name = Image-Stufe, Kategorie, Einstellung `network.forcestrict`):
#   2022-anmeldung | 2022-tds8 | 2025-anmeldung | 2025-tds8 | 2025-tds8-forcestrict | alle (Voreinstellung)
# Je Lauf: Image der Referenzinstanz bauen (Dockerfile-Stufe `referenzinstanz-<jahr>` in referenztest-dotnet, Digest
# dort gepinnt) und Client-Image (Stufe `vergleich`); zwei Container in einem eigenen internen Netzwerk (kein Zugang
# nach aussen, kein veroeffentlichter Port, nichts wird gemountet; Namen mit PID). Zertifikat und Schluessel (CN und SAN
# localhost) entstehen je Aufruf in einem Wegwerf-Container aus dem Image der Referenzinstanz und gehen per `docker cp`
# mit der Datei mssql.conf in den Server-Container; das Zertifikat ist beim Client der Vertrauensanker (SSL_CERT_FILE).
# Das SA-Kennwort entsteht je Aufruf zufaellig, steht nur in den Umgebungen der beiden Container und wird nicht ausgegeben.
# Warten auf die Zeile 'Recovery is complete' im Log des Servers (Bereitschaftsschleife): nach 'ready for client
# connections' laufen noch Upgrade-Schritte der Systemdatenbanken, und die Anmeldung von sa scheitert in dieser Zeit
# mit State 7 (gemessen 2026-11-01, Image 2022). Die Tests der Kategorie laufen im Client-Container;
# die Zeilen `messung:` des Berichts sind die Werte, die die Ausnahmeliste (docs/plan/referenzabgleich-ausnahmen.md) zitiert.
# Kategorie VergleichTds8: zusaetzlich eine Sonde (`openssl s_client` aus dem Image der Referenzinstanz im Netzwerk):
# der TLS-Handschlag allein, ohne Client-Bibliothek und ohne Pruefung des Zertifikats; sie trennt "der Server beantwortet
# TLS vor jedem TDS-Paket" von "der Client lehnt das Zertifikat ab". Die Erwartungen je Lauf (Client und Sonde) stehen
# in `parameter` und sind Messungen vom 2026-11-01; ein anderes Ergebnis macht den Lauf rot.
#
# EULA: der Lauf bricht ab (Exit 2), wenn VERGLEICH_ACCEPT_EULA nicht `Y` ist; die Variable setzt allein
# tools/mk/vergleichslauf-d.mk (Entscheidung des Nutzers pt9912, 2026-11-01). Der Lauf bricht ebenso ab, wenn CI oder
# GITHUB_ACTIONS gesetzt sind: ein Lauf in der CI und in `make gates` braucht eine neue Entscheidung des Nutzers.
# `trap` raeumt bei Ende, SIGINT und SIGTERM Container, Netzwerk und Images dieses Aufrufs ab; bei SIGKILL bleiben sie stehen.
# Zusage: die Tests der Kategorie bestehen gegen die Referenzinstanz mit dem gepinnten Digest; nicht belegt steht im
# Kopf der Testklassen in referenztest-dotnet/Vergleich.
set -u

LAUFNAME="${1:-alle}"
ALLE="2022-anmeldung 2022-tds8 2025-anmeldung 2025-tds8 2025-tds8-forcestrict"

# parameter <Lauf>: setzt STUFE, KATEGORIE, FORCESTRICT, ERWARTE_STRICT, ERWARTE_SONDE und ERWARTE_MANDATORY (gelingt|scheitert|-).
parameter() {
  case "$1" in
    2022-anmeldung) STUFE=2022 KATEGORIE=VergleichAnmeldung FORCESTRICT=0 ERWARTE_STRICT=- ERWARTE_SONDE=- ERWARTE_MANDATORY=- ;;
    2022-tds8) STUFE=2022 KATEGORIE=VergleichTds8 FORCESTRICT=0 ERWARTE_STRICT=scheitert ERWARTE_SONDE=scheitert ERWARTE_MANDATORY=gelingt ;;
    2025-anmeldung) STUFE=2025 KATEGORIE=VergleichAnmeldung FORCESTRICT=0 ERWARTE_STRICT=- ERWARTE_SONDE=- ERWARTE_MANDATORY=- ;;
    2025-tds8) STUFE=2025 KATEGORIE=VergleichTds8 FORCESTRICT=0 ERWARTE_STRICT=gelingt ERWARTE_SONDE=gelingt ERWARTE_MANDATORY=gelingt ;;
    2025-tds8-forcestrict) STUFE=2025 KATEGORIE=VergleichTds8 FORCESTRICT=1 ERWARTE_STRICT=gelingt ERWARTE_SONDE=gelingt ERWARTE_MANDATORY=scheitert ;;
    *) return 1 ;;
  esac
}

if [ "$LAUFNAME" = alle ]; then
  LISTE="$ALLE"
elif parameter "$LAUFNAME"; then
  LISTE="$LAUFNAME"
else
  echo "vergleichslauf-d: LAUF muss alle oder einer von [$ALLE] sein (war '$LAUFNAME')" >&2
  exit 2
fi
if [ -n "${CI:-}${GITHUB_ACTIONS:-}" ]; then
  echo "vergleichslauf-d: Abbruch - der Lauf ist lokal (Entscheidung des Nutzers pt9912, 2026-11-01); in der CI braucht er eine neue Entscheidung" >&2
  exit 2
fi
if [ "${VERGLEICH_ACCEPT_EULA:-}" != "Y" ]; then
  echo "vergleichslauf-d: Abbruch - VERGLEICH_ACCEPT_EULA ist nicht Y; die Zustimmung setzt tools/mk/vergleichslauf-d.mk (Entscheidung des Nutzers pt9912, 2026-11-01)" >&2
  exit 2
fi

LAUF="tdswire-vgl-$$"
NETZ=""
SERVER=""
CLIENT=""
SERVER_IMAGE=""
CLIENT_IMAGE="tdswire-vergleich-client:$LAUF"
SCRATCH="$(mktemp -d)"
FEHLER=0

aufraeumen() {
  [ -z "$CLIENT" ] || docker rm -f "$CLIENT" >/dev/null 2>&1
  [ -z "$SERVER" ] || docker rm -f "$SERVER" >/dev/null 2>&1
  [ -z "$NETZ" ] || docker network rm "$NETZ" >/dev/null 2>&1
  [ -z "$SERVER_IMAGE" ] || docker rmi -f "$SERVER_IMAGE" >/dev/null 2>&1
  docker rmi -f "$CLIENT_IMAGE" >/dev/null 2>&1
  rm -rf "$SCRATCH"
}
trap aufraeumen EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

pruefe() { # pruefe <Beschreibung> <Befehl...>
  local beschreibung="$1"
  shift
  if "$@"; then echo "vergleichslauf-d: ok   $beschreibung"; else echo "vergleichslauf-d: FEHLER $beschreibung"; FEHLER=1; fi
}

# Schluessel und Zertifikat entstehen in einem Wegwerf-Container aus dem Image der Referenzinstanz, liegen in $SCRATCH und gelten fuer diesen Aufruf.
zertifikat_erzeugen() { # zertifikat_erzeugen <Image>
  docker run --rm --entrypoint bash "$1" -c \
    'openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj /CN=localhost -addext subjectAltName=DNS:localhost,DNS:referenz -addext extendedKeyUsage=serverAuth -addext keyUsage=digitalSignature,keyEncipherment,keyCertSign -keyout /tmp/k.pem -out /tmp/c.pem >/dev/null 2>&1 && cat /tmp/c.pem && echo @@@@ && cat /tmp/k.pem' \
    >"$SCRATCH/pem" || return 1
  sed -n '1,/^@@@@$/{/^@@@@$/d;p}' "$SCRATCH/pem" >"$SCRATCH/server.pem"
  sed -n '/^@@@@$/,${/^@@@@$/d;p}' "$SCRATCH/pem" >"$SCRATCH/server.key"
  chmod 644 "$SCRATCH/server.pem" "$SCRATCH/server.key"
  grep -q 'BEGIN CERTIFICATE' "$SCRATCH/server.pem" && grep -q 'PRIVATE KEY' "$SCRATCH/server.key"
}

einzellauf() { # einzellauf <Name>
  local name="$1" start t_bereit t_fertig kennwort groesse
  parameter "$name" || return 1
  NETZ="$LAUF-$name-netz"
  SERVER="$LAUF-$name-server"
  CLIENT="$LAUF-$name-client"
  SERVER_IMAGE="tdswire-referenz-$STUFE:$LAUF"
  echo "vergleichslauf-d: --- Lauf $name (Stufe $STUFE, Kategorie $KATEGORIE, forcestrict $FORCESTRICT) ---"
  start=$SECONDS
  # shellcheck disable=SC2086
  docker build ${BUILDER_FLAGS:-} --load --target "referenzinstanz-$STUFE" -t "$SERVER_IMAGE" referenztest-dotnet || return 1
  groesse=$(docker image inspect --format '{{.Size}}' "$SERVER_IMAGE")
  echo "vergleichslauf-d: messung: Image der Referenzinstanz $STUFE auf der Platte $((groesse / 1000000)) MB (Digest im Dockerfile gepinnt)"
  [ -s "$SCRATCH/server.pem" ] || zertifikat_erzeugen "$SERVER_IMAGE" || return 1

  kennwort="Tdswire-Ref-1-$(LC_ALL=C tr -dc "a-z0-9" </dev/urandom | head -c 16)"
  {
    echo "[network]"
    echo "tlscert = /var/opt/mssql/tdswire-server.pem"
    echo "tlskey = /var/opt/mssql/tdswire-server.key"
    [ "$FORCESTRICT" = 0 ] || echo "forcestrict = 1"
  } >"$SCRATCH/mssql.conf"
  chmod 666 "$SCRATCH/mssql.conf"

  docker network create --internal "$NETZ" >/dev/null || return 1
  docker create --name "$SERVER" --network "$NETZ" --network-alias referenz -e "ACCEPT_EULA=$VERGLEICH_ACCEPT_EULA" -e MSSQL_PID=Developer \
    -e "MSSQL_SA_PASSWORD=$kennwort" "$SERVER_IMAGE" >/dev/null || return 1
  docker cp "$SCRATCH/server.pem" "$SERVER:/var/opt/mssql/tdswire-server.pem" >/dev/null || return 1
  docker cp "$SCRATCH/server.key" "$SERVER:/var/opt/mssql/tdswire-server.key" >/dev/null || return 1
  docker cp "$SCRATCH/mssql.conf" "$SERVER:/var/opt/mssql/mssql.conf" >/dev/null || return 1
  docker start "$SERVER" >/dev/null || return 1

  bereit() {
    local i
    for i in $(seq 1 180); do
      docker logs "$SERVER" 2>&1 | grep -q 'Recovery is complete' && return 0
      [ "$(docker inspect --format '{{.State.Running}}' "$SERVER")" = "true" ] || return 1
      sleep 1
    done
    return 1
  }
  pruefe "Server der Referenzinstanz meldet 'Recovery is complete'" bereit
  [ "$FEHLER" -eq 0 ] || { docker logs "$SERVER" 2>&1 | tail -30; return 1; }
  t_bereit=$((SECONDS - start))
  echo "vergleichslauf-d: messung: Start der Referenzinstanz $STUFE bis 'Recovery is complete' (mit Bau des Images) $t_bereit s; Speicher des Servers $(docker stats --no-stream --format '{{.MemUsage}}' "$SERVER")"
  docker logs "$SERVER" 2>&1 | grep -m1 -E 'Microsoft SQL Server' | sed 's/^/vergleichslauf-d: server: /'

  if [ "$ERWARTE_SONDE" != "-" ]; then
    local sonde urteil
    sonde=$(docker run --rm --network "$NETZ" --entrypoint bash "$SERVER_IMAGE" -c \
      "printf '' | timeout 20 openssl s_client -connect referenz:1433 -servername referenz -brief 2>&1 | head -12")
    if printf '%s\n' "$sonde" | grep -q 'CONNECTION ESTABLISHED'; then urteil=gelingt; else urteil=scheitert; fi
    printf '%s\n' "$sonde" | sed 's/^/vergleichslauf-d: sonde: /'
    echo "vergleichslauf-d: messung: Sonde (TLS-Handschlag vor jedem TDS-Paket, Image $STUFE, forcestrict $FORCESTRICT): $urteil"
    pruefe "Sonde: TLS-Handschlag $ERWARTE_SONDE (gemessen: $urteil)" test "$urteil" = "$ERWARTE_SONDE"
  fi

  docker create --name "$CLIENT" --network "$NETZ" -e "TDSWIRE_ZIEL=referenz:1433" -e "TDSWIRE_REFERENZ_KENNWORT=$kennwort" \
    -e "TDSWIRE_ERWARTE_STRICT=$ERWARTE_STRICT" -e "TDSWIRE_ERWARTE_MANDATORY=$ERWARTE_MANDATORY" -e SSL_CERT_FILE=/tmp/server.pem \
    "$CLIENT_IMAGE" dotnet test --no-build --nologo --filter "Kategorie=$KATEGORIE" \
    --logger "console;verbosity=detailed" >/dev/null || return 1
  docker cp "$SCRATCH/server.pem" "$CLIENT:/tmp/server.pem" >/dev/null || return 1
  docker start -a "$CLIENT" >"$SCRATCH/client" 2>&1
  client_ok=$?
  cat "$SCRATCH/client"
  t_fertig=$((SECONDS - start))

  alle_bestanden() {
    [ "$client_ok" -eq 0 ] && grep -q 'Test Run Successful' "$SCRATCH/client" &&
      grep -Eq 'Total tests: [1-9]' "$SCRATCH/client" && ! grep -Eq 'Failed: [1-9]' "$SCRATCH/client"
  }
  pruefe "Alle Tests der Kategorie $KATEGORIE bestanden ($(grep -Eo 'Total tests: [0-9]+' "$SCRATCH/client" | head -1))" alle_bestanden
  laeuft() { [ "$(docker inspect --format '{{.State.Running}}' "$SERVER")" = "true" ]; }
  pruefe "Server der Referenzinstanz laeuft nach den Tests" laeuft
  if [ "$KATEGORIE" = VergleichAnmeldung ]; then
    # Der Client sieht fuer beide Faelle State 1; den internen State nennt allein das Log des Servers.
    interner_state() { docker logs "$SERVER" 2>&1 | grep -A1 "Error: 18456, Severity: 14, State: $1\." | grep -qF "$2"; }
    pruefe "Log: falsches Kennwort ist intern State 8 'Password did not match'" \
      interner_state 8 "Login failed for user 'sa'. Reason: Password did not match"
    pruefe "Log: unbekannter Benutzer ist intern State 5 'Could not find a login'" \
      interner_state 5 "Login failed for user 'tdswire_unbekannt'. Reason: Could not find a login"
  fi
  if [ "$FEHLER" -ne 0 ]; then
    echo "vergleichslauf-d: --- Log der Referenzinstanz (eine Pruefung ist rot) ---"
    docker logs "$SERVER" 2>&1 | cut -c1-220
  fi
  pruefe "Ausgabe des Clients nennt das Kennwort des Servers nicht" sh -c "! grep -qF '$kennwort' '$SCRATCH/client'"
  echo "vergleichslauf-d: --- TLS-Zeilen im Log der Referenzinstanz ---"
  docker logs "$SERVER" 2>&1 | grep -i -E 'tls|strict|encrypt|certificate' | head -20
  echo "vergleichslauf-d: messung: Lauf $name $t_fertig s (mit Bau und Start $t_bereit s)"

  docker rm -f "$CLIENT" "$SERVER" >/dev/null 2>&1
  docker network rm "$NETZ" >/dev/null 2>&1
  docker rmi -f "$SERVER_IMAGE" >/dev/null 2>&1
  CLIENT="" SERVER="" NETZ="" SERVER_IMAGE=""
  return 0
}

gesamt=$SECONDS
# shellcheck disable=SC2086
docker build ${BUILDER_FLAGS:-} --load --target vergleich -t "$CLIENT_IMAGE" referenztest-dotnet || exit 1
echo "vergleichslauf-d: messung: Bau des Client-Images $((SECONDS - gesamt)) s"
for lauf in $LISTE; do
  einzellauf "$lauf" || FEHLER=1
  [ "$FEHLER" -eq 0 ] || break
done
echo "vergleichslauf-d: messung: Laufzeit gesamt $((SECONDS - gesamt)) s"
[ "$FEHLER" -eq 0 ] || { echo "vergleichslauf-d: ROT"; exit 1; }
echo "vergleichslauf-d: gruen"
