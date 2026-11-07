#!/usr/bin/env bash
# tools/vergleichslauf-d.sh - Vergleichslauf D: der .NET-Referenzclient gegen einen echten SQL Server im Container
# (ADR-0008 Option D, SPEC-045, TDS-QA-01). Aufruf ueber `make vergleichslauf-d [LAUF=<Name>]`; BUILDER_FLAGS kommt
# aus der Umgebung (leer ohne BUILDER). Kein Gate und keine Stufe von `make e2e` (ADR-0022). Nur die Laeufe `live-*`
# starten zusaetzlich den Server dieses Repos (Stufe `kompatibilitaetsserver`); alle anderen sprechen nur die Referenzinstanz an.
#
# Laeufe (Name = Image-Stufe, Kategorie, Einstellung `network.forcestrict`):
#   2022-anmeldung | 2022-tds8 | 2025-anmeldung | 2025-tds8 | 2025-tds8-forcestrict | alle (Voreinstellung)
#   live-2022 | live-2025 | live (beide): Live-Lauf nach SPEC-045, siehe `livelauf`; messung-2022 | messung-2025: Gegenmessungen
#   an der Referenzinstanz (referenztest-dotnet/Vergleich/ReferenzMessungTests.cs, Kategorie VergleichMessung), kein Urteil.
#   aufnahme-2022 | aufnahme-2025 | aufnahme (beide): Aufnahme der Mitschnitte (SPEC-045) an der Referenzinstanz ueber den
#   mitschneidenden Proxy im Client-Container; die redigierten Dateien ersetzen referenztest-dotnet/Mitschnitt/aufnahmen/<stufe>/
#   im Arbeitsbaum (der Lauf schreibt dorthin, committet nichts). mitschnitt-2022 | mitschnitt-2025 | mitschnitt (beide):
#   Abspielen der abgelegten Mitschnitte gegen einen frisch gestarteten Server dieses Repos (Stage `kompatibilitaetsserver`,
#   ENCRYPTION_POLICY=TEST_UNENCRYPTED) und feldweiser Vergleich mit referenztest-dotnet/Vergleich/mitschnitt-ausnahmen.tsv;
#   das Abspielen startet keine Referenzinstanz.
#   wire-2022 | wire-2025 | wire (beide): Messreihe am Draht (SPEC-045, Kategorie MessungWire, referenztest-dotnet/Vergleich/
#   MessungWireTests.cs): Fehler im RPC, Ordinal der RETURNVALUE, curCmd, RESETCONNECTION, Anmeldung und SESSIONSTATE ueber den
#   TLS-beendenden Proxy; die redigierten Dateien ersetzen referenztest-dotnet/Mitschnitt/messung/<stufe>/ im Arbeitsbaum (der
#   Lauf schreibt dorthin, committet nichts). Die Zeilen `messung:` der Ausgabe sind die Werte, die das Messprotokoll zitiert.
#   bulk-2022 | bulk-2025 | bulk (beide): Messreihe zu Massenladen, MERGE, Katalog, Strukturaenderung und den Zusatzmessungen (Kategorie
#   MessungBulk, referenztest-dotnet/Vergleich/MessungBulkTests.cs) mit derselben Methode wie `wire`; die Dateien ersetzen
#   referenztest-dotnet/Mitschnitt/messung/bulk-merge-katalog/<stufe>/ im Arbeitsbaum (die Ablage von `wire` bleibt unberuehrt).
#   MESSUNG_NUR=<Szenarien> waehlt eine Teilmenge ohne Ablage; mit MESSUNG_WIEDERHOLEN=<N> faehrt jedes gewaehlte Szenario N-mal ueber
#   einen frisch aufgebauten Bestand und nennt die Rate roter Laeufe (MESSUNG_DIREKT=1: der Client verbindet sich ohne Proxy).
# Je Lauf: Image der Referenzinstanz bauen (Dockerfile-Stufe `referenzinstanz-<jahr>` in referenztest-dotnet, Digest
# dort gepinnt) und Client-Image (Stufe `vergleich`); zwei Container in einem eigenen internen Netzwerk (kein Zugang
# nach aussen, kein veroeffentlichter Port, nichts wird gemountet; Namen mit PID). Zertifikat und Schluessel (CN und SAN
# localhost) entstehen je Aufruf in einem Wegwerf-Container aus dem Image der Referenzinstanz und gehen per `docker cp`
# mit der Datei mssql.conf in den Server-Container; das Zertifikat ist beim Client der Vertrauensanker (SSL_CERT_FILE).
# Das SA-Kennwort entsteht je Aufruf zufaellig und wird nicht ausgegeben. Es steht in den Umgebungen der beiden Container
# (`docker inspect` zeigt es lokal), in der Argumentliste der Docker-CLI-Aufrufe `docker create -e ...` (lokal in der
# Prozessliste sichtbar, solange sie laufen) und im Speicher des Skripts; ein Wegwerf-Wert fuer einen Container, der mit dem
# Aufruf endet. Die Ausgabe des Clients wird vor der Ausgabe auf das Kennwort geprueft; bei einem Treffer wird sie nicht
# gedruckt. Der freie Platz fuer die Images (rund 4,8 GB, siehe tools/mk/vergleichslauf-d.mk) wird nicht vorab geprueft.
# Warten auf die Zeile 'Recovery is complete' im Log des Servers (Bereitschaftsschleife): nach 'ready for client
# connections' laufen noch Upgrade-Schritte der Systemdatenbanken, und die Anmeldung von sa scheitert in dieser Zeit
# mit State 7 (gemessen 2026-11-01, Image 2022). Die Tests der Kategorie laufen im Client-Container;
# die Zeilen `messung:` des Berichts sind die Werte, die die Ausnahmeliste (docs/messungen/referenzabgleich.md) zitiert.
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

# Vergleich der Ausgaben des Live-Laufs (SPEC-045): `bash tools/vergleichslauf-d.sh --vergleich <Referenz> <Server> <Ausnahmen> [<Stufe>]`.
# Es braucht weder Docker noch die EULA-Variable und liest nur die drei Dateien. Referenz und Server: eine Zeile je Schritt,
# `Kennung TAB Ergebnis`. Ausnahmen: `Kennung TAB Ergebnis der Referenz TAB Ergebnis des Servers TAB Art TAB Grund TAB Verweis`,
# Art `so gewollt` oder `Luecke`; Zeilen mit `#` am Anfang und leere Zeilen zaehlen nicht. Exit 0 genau dann, wenn
# beide Dateien dieselben Kennungen fuehren und jeder Schritt gleich ist oder genau der gelisteten Abweichung entspricht
# (beide Ergebnisse wortgleich); jede andere Abweichung, eine Regel ohne Abweichung, eine Regel ohne Schritt, eine doppelte
# Regel und eine unbekannte Art machen den Vergleich rot (Exit 1). Ein Schritt darf mehrere Regeln mit verschiedenen
# Ergebnispaaren haben (eine Instanz, die je Lauf verschieden antwortet); jedes Paar steht wortgleich da. Eine Regel `Kennung@<Stufe>` gilt nur fuer die Referenzinstanz
# dieser Stufe (2022 oder 2025), eine Regel ohne `@` fuer jede; Regeln anderer Stufen zaehlen nicht. Eingabefehler (Datei fehlt): Exit 2.
vergleichen() { # vergleichen <Referenz> <Server> <Ausnahmen> [<Stufe 2022|2025>]
  local datei rot=0 kennung ref srv art grund verweis zeile
  local -A werte_ref werte_srv regel_hat regel_art
  local -a kennungen=()
  local sep=$'\x1f' schluessel
  for datei in "$1" "$2" "$3"; do
    [ -f "$datei" ] || { echo "vergleichslauf-d: Datei fehlt: $datei" >&2; return 2; }
  done
  while IFS=$'\t' read -r kennung ref; do
    [ -n "$kennung" ] || continue
    [ -z "${werte_ref[$kennung]+x}" ] || { echo "vergleichslauf-d: live: FEHLER Schritt $kennung doppelt in der Ausgabe der Referenz"; rot=1; }
    werte_ref[$kennung]="$ref"
    kennungen+=("$kennung")
  done <"$1"
  while IFS=$'\t' read -r kennung srv; do
    [ -n "$kennung" ] || continue
    [ -z "${werte_srv[$kennung]+x}" ] || { echo "vergleichslauf-d: live: FEHLER Schritt $kennung doppelt in der Ausgabe des Servers"; rot=1; }
    werte_srv[$kennung]="$srv"
    [ -n "${werte_ref[$kennung]+x}" ] || kennungen+=("$kennung")
  done <"$2"
  while IFS=$'\t' read -r kennung ref srv art grund verweis; do
    case "$kennung" in '' | '#'*) continue ;; esac
    case "$kennung" in
      *@*)
        [ "${kennung#*@}" = "${4:-}" ] || continue
        kennung="${kennung%@*}"
        ;;
    esac
    schluessel="$kennung$sep$ref$sep$srv"
    [ -z "${regel_art[$schluessel]+x}" ] || { echo "vergleichslauf-d: live: FEHLER Regel $kennung doppelt (gleiche Ergebnisse)"; rot=1; }
    case "$art" in
      'so gewollt' | 'Luecke') ;;
      *) echo "vergleichslauf-d: live: FEHLER Regel $kennung: Art '$art' ist weder 'so gewollt' noch 'Luecke'"; rot=1 ;;
    esac
    [ -n "$grund" ] && [ -n "$verweis" ] || { echo "vergleichslauf-d: live: FEHLER Regel $kennung: Grund oder Verweis fehlt"; rot=1; }
    regel_art[$schluessel]="$art"
    regel_hat[$kennung]=1
    [ -n "${werte_ref[$kennung]+x}${werte_srv[$kennung]+x}" ] || { echo "vergleichslauf-d: live: FEHLER Regel $kennung gehoert zu keinem Schritt"; rot=1; }
  done <"$3"
  for kennung in "${kennungen[@]}"; do
    if [ -z "${werte_ref[$kennung]+x}" ] || [ -z "${werte_srv[$kennung]+x}" ]; then
      echo "vergleichslauf-d: live: FEHLER Schritt $kennung fehlt in der Ausgabe der $([ -z "${werte_ref[$kennung]+x}" ] && echo Referenz || echo Servers)"
      rot=1
      continue
    fi
    ref="${werte_ref[$kennung]}"
    srv="${werte_srv[$kennung]}"
    if [ "$ref" = "$srv" ]; then
      if [ -n "${regel_hat[$kennung]+x}" ]; then
        echo "vergleichslauf-d: live: FEHLER Schritt $kennung ist gleich, die Ausnahme dazu entfaellt"
        rot=1
      else
        echo "vergleichslauf-d: live: ok   gleich       $kennung"
      fi
    elif [ -n "${regel_art[$kennung$sep$ref$sep$srv]+x}" ]; then
      echo "vergleichslauf-d: live: ok   Ausnahme (${regel_art[$kennung$sep$ref$sep$srv]}) $kennung"
    else
      if [ -n "${regel_hat[$kennung]+x}" ]; then
        echo "vergleichslauf-d: live: FEHLER Schritt $kennung weicht anders ab als gelistet"
      else
        echo "vergleichslauf-d: live: FEHLER Schritt $kennung weicht ab und steht nicht in der Ausnahmeliste"
      fi
      echo "vergleichslauf-d: live:   Referenz: $ref"
      echo "vergleichslauf-d: live:   Server:   $srv"
      rot=1
    fi
  done
  zeile="${#kennungen[@]} Schritte"
  if [ "$rot" -eq 0 ]; then echo "vergleichslauf-d: live: gruen ($zeile)"; else echo "vergleichslauf-d: live: ROT ($zeile)"; fi
  return "$rot"
}
if [ "${1:-}" = "--vergleich" ]; then
  [ "$#" -ge 4 ] && [ "$#" -le 5 ] || { echo "vergleichslauf-d: --vergleich <Referenz> <Server> <Ausnahmen> [<Stufe>]" >&2; exit 2; }
  vergleichen "$2" "$3" "$4" "${5:-}"
  exit $?
fi

LAUFNAME="${1:-alle}"
ALLE="2022-anmeldung 2022-tds8 2025-anmeldung 2025-tds8 2025-tds8-forcestrict"
LIVE="live-2022 live-2025"
MESSUNG="messung-2022 messung-2025"
AUFNAHME="aufnahme-2022 aufnahme-2025"
MITSCHNITT="mitschnitt-2022 mitschnitt-2025"
WIRE="wire-2022 wire-2025"
BULK="bulk-2022 bulk-2025"

# parameter <Lauf>: setzt STUFE, KATEGORIE, FORCESTRICT, ERWARTE_STRICT, ERWARTE_SONDE und ERWARTE_MANDATORY (gelingt|scheitert|-).
parameter() {
  case "$1" in
    2022-anmeldung) STUFE=2022 KATEGORIE=VergleichAnmeldung FORCESTRICT=0 ERWARTE_STRICT=- ERWARTE_SONDE=- ERWARTE_MANDATORY=- ;;
    2022-tds8) STUFE=2022 KATEGORIE=VergleichTds8 FORCESTRICT=0 ERWARTE_STRICT=scheitert ERWARTE_SONDE=scheitert ERWARTE_MANDATORY=gelingt ;;
    2025-anmeldung) STUFE=2025 KATEGORIE=VergleichAnmeldung FORCESTRICT=0 ERWARTE_STRICT=- ERWARTE_SONDE=- ERWARTE_MANDATORY=- ;;
    2025-tds8) STUFE=2025 KATEGORIE=VergleichTds8 FORCESTRICT=0 ERWARTE_STRICT=gelingt ERWARTE_SONDE=gelingt ERWARTE_MANDATORY=gelingt ;;
    2025-tds8-forcestrict) STUFE=2025 KATEGORIE=VergleichTds8 FORCESTRICT=1 ERWARTE_STRICT=gelingt ERWARTE_SONDE=gelingt ERWARTE_MANDATORY=scheitert ;;
    messung-2022) STUFE=2022 KATEGORIE=VergleichMessung FORCESTRICT=0 ERWARTE_STRICT=- ERWARTE_SONDE=- ERWARTE_MANDATORY=- ;;
    messung-2025) STUFE=2025 KATEGORIE=VergleichMessung FORCESTRICT=0 ERWARTE_STRICT=- ERWARTE_SONDE=- ERWARTE_MANDATORY=- ;;
    live-2022) STUFE=2022 KATEGORIE=Live FORCESTRICT=0 ERWARTE_STRICT=- ERWARTE_SONDE=- ERWARTE_MANDATORY=- ;;
    live-2025) STUFE=2025 KATEGORIE=Live FORCESTRICT=0 ERWARTE_STRICT=- ERWARTE_SONDE=- ERWARTE_MANDATORY=- ;;
    aufnahme-2022) STUFE=2022 KATEGORIE=MitschnittAufnahme FORCESTRICT=0 ERWARTE_STRICT=- ERWARTE_SONDE=- ERWARTE_MANDATORY=- ;;
    aufnahme-2025) STUFE=2025 KATEGORIE=MitschnittAufnahme FORCESTRICT=0 ERWARTE_STRICT=- ERWARTE_SONDE=- ERWARTE_MANDATORY=- ;;
    mitschnitt-2022) STUFE=2022 KATEGORIE=MitschnittAbspielen FORCESTRICT=0 ERWARTE_STRICT=- ERWARTE_SONDE=- ERWARTE_MANDATORY=- ;;
    mitschnitt-2025) STUFE=2025 KATEGORIE=MitschnittAbspielen FORCESTRICT=0 ERWARTE_STRICT=- ERWARTE_SONDE=- ERWARTE_MANDATORY=- ;;
    wire-2022) STUFE=2022 KATEGORIE=MessungWire FORCESTRICT=0 ERWARTE_STRICT=- ERWARTE_SONDE=- ERWARTE_MANDATORY=- ;;
    wire-2025) STUFE=2025 KATEGORIE=MessungWire FORCESTRICT=0 ERWARTE_STRICT=- ERWARTE_SONDE=- ERWARTE_MANDATORY=- ;;
    bulk-2022) STUFE=2022 KATEGORIE=MessungBulk FORCESTRICT=0 ERWARTE_STRICT=- ERWARTE_SONDE=- ERWARTE_MANDATORY=- ;;
    bulk-2025) STUFE=2025 KATEGORIE=MessungBulk FORCESTRICT=0 ERWARTE_STRICT=- ERWARTE_SONDE=- ERWARTE_MANDATORY=- ;;
    *) return 1 ;;
  esac
}

if [ "$LAUFNAME" = alle ]; then
  LISTE="$ALLE"
elif [ "$LAUFNAME" = live ]; then
  LISTE="$LIVE"
elif [ "$LAUFNAME" = aufnahme ]; then
  LISTE="$AUFNAHME"
elif [ "$LAUFNAME" = mitschnitt ]; then
  LISTE="$MITSCHNITT"
elif [ "$LAUFNAME" = wire ]; then
  LISTE="$WIRE"
elif [ "$LAUFNAME" = bulk ]; then
  LISTE="$BULK"
elif parameter "$LAUFNAME"; then
  LISTE="$LAUFNAME"
else
  echo "vergleichslauf-d: LAUF muss alle, live, aufnahme, mitschnitt, wire, bulk oder einer von [$ALLE $LIVE $MESSUNG $AUFNAHME $MITSCHNITT $WIRE $BULK] sein (war '$LAUFNAME')" >&2
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

SIM_SERVER=""
SIM_IMAGE=""

aufraeumen() {
  [ -z "$CLIENT" ] || docker rm -f "$CLIENT" >/dev/null 2>&1
  [ -z "$SIM_SERVER" ] || docker rm -f "$SIM_SERVER" >/dev/null 2>&1
  [ -z "$SIM_IMAGE" ] || docker rmi -f "$SIM_IMAGE" >/dev/null 2>&1
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

# referenz_hochfahren <Name>: baut das Image der Referenzinstanz (STUFE), startet sie mit selbst erzeugtem Zertifikat im
# eigenen internen Netzwerk und wartet auf 'Recovery is complete'. Setzt NETZ, SERVER, SERVER_IMAGE, KENNWORT, START
# und T_BEREIT; die Container und das Image raeumt `aufraeumen` oder der Aufrufer ab.
referenz_hochfahren() {
  local name="$1" groesse
  NETZ="$LAUF-$name-netz"
  SERVER="$LAUF-$name-server"
  SERVER_IMAGE="tdswire-referenz-$STUFE:$LAUF"
  START=$SECONDS
  # shellcheck disable=SC2086
  docker build ${BUILDER_FLAGS:-} --load --target "referenzinstanz-$STUFE" -t "$SERVER_IMAGE" referenztest-dotnet || return 1
  groesse=$(docker image inspect --format '{{.Size}}' "$SERVER_IMAGE")
  echo "vergleichslauf-d: messung: Image der Referenzinstanz $STUFE auf der Platte $((groesse / 1000000)) MB (Digest im Dockerfile gepinnt)"
  [ -s "$SCRATCH/server.pem" ] || zertifikat_erzeugen "$SERVER_IMAGE" || return 1

  KENNWORT="Tdswire-Ref-1-$(LC_ALL=C tr -dc "a-z0-9" </dev/urandom | head -c 16)"
  {
    echo "[network]"
    echo "tlscert = /var/opt/mssql/tdswire-server.pem"
    echo "tlskey = /var/opt/mssql/tdswire-server.key"
    [ "$FORCESTRICT" = 0 ] || echo "forcestrict = 1"
  } >"$SCRATCH/mssql.conf"
  chmod 666 "$SCRATCH/mssql.conf"

  docker network create --internal "$NETZ" >/dev/null || return 1
  docker create --name "$SERVER" --network "$NETZ" --network-alias referenz -e "ACCEPT_EULA=$VERGLEICH_ACCEPT_EULA" -e MSSQL_PID=Developer \
    -e "MSSQL_SA_PASSWORD=$KENNWORT" "$SERVER_IMAGE" >/dev/null || return 1
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
  T_BEREIT=$((SECONDS - START))
  echo "vergleichslauf-d: messung: Start der Referenzinstanz $STUFE bis 'Recovery is complete' (mit Bau des Images) $T_BEREIT s; Speicher des Servers $(docker stats --no-stream --format '{{.MemUsage}}' "$SERVER")"
  docker logs "$SERVER" 2>&1 | grep -m1 -E 'Microsoft SQL Server' | sed 's/^/vergleichslauf-d: server: /'
  return 0
}

einzellauf() { # einzellauf <Name>
  local name="$1" t_bereit t_fertig kennwort start
  parameter "$name" || return 1
  CLIENT="$LAUF-$name-client"
  echo "vergleichslauf-d: --- Lauf $name (Stufe $STUFE, Kategorie $KATEGORIE, forcestrict $FORCESTRICT) ---"
  referenz_hochfahren "$name" || return 1
  kennwort="$KENNWORT"
  start=$START
  t_bereit=$T_BEREIT

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
  client_leck=0
  if grep -qF -e "$kennwort" "$SCRATCH/client"; then
    client_leck=1
    echo "vergleichslauf-d: Ausgabe des Clients nennt das Kennwort des Servers; sie wird nicht gedruckt"
  else
    cat "$SCRATCH/client"
  fi
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
  pruefe "Ausgabe des Clients nennt das Kennwort des Servers nicht" test "$client_leck" -eq 0
  echo "vergleichslauf-d: --- TLS-Zeilen im Log der Referenzinstanz ---"
  docker logs "$SERVER" 2>&1 | grep -i -E 'tls|strict|encrypt|certificate' | head -20
  echo "vergleichslauf-d: messung: Lauf $name $t_fertig s (mit Bau und Start $t_bereit s)"

  docker rm -f "$CLIENT" "$SERVER" >/dev/null 2>&1
  docker network rm "$NETZ" >/dev/null 2>&1
  docker rmi -f "$SERVER_IMAGE" >/dev/null 2>&1
  CLIENT="" SERVER="" NETZ="" SERVER_IMAGE=""
  return 0
}

# live_client <Anzeigename> <Ziel> <Benutzer> <Kennwort> <Aufbau 0|1> <Zertifikat> <Ausgabedatei>: fuehrt das Szenario der Kategorie
# Live (referenztest-dotnet/Vergleich/LiveSzenarioTests.cs) in einem Client-Container gegen ein Ziel aus und legt die
# Schrittzeilen in <Ausgabedatei> ab. Der Aufbau des Testschemas (1) gilt nur der Referenzinstanz.
live_client() {
  local anzeige="$1" ziel="$2" benutzer="$3" kennwort="$4" aufbau="$5" zertifikat="$6" ausgabe="$7" ok leck=0
  CLIENT="$LAUF-live-$anzeige-client"
  docker create --name "$CLIENT" --network "$NETZ" -e "TDSWIRE_ZIEL=$ziel" -e "TDSWIRE_LIVE_BENUTZER=$benutzer" \
    -e "TDSWIRE_LIVE_KENNWORT=$kennwort" -e "TDSWIRE_LIVE_AUFBAU=$aufbau" -e TDSWIRE_LIVE_DATENBANK="$([ "$aufbau" = 1 ] && echo tdswire_live)" \
    -e TDSWIRE_LIVE_AUSGABE=/tmp/live.txt -e SSL_CERT_FILE=/tmp/server.pem \
    "$CLIENT_IMAGE" dotnet test --no-build --nologo --filter "Kategorie=Live" --logger "console;verbosity=detailed" >/dev/null || return 1
  docker cp "$zertifikat" "$CLIENT:/tmp/server.pem" >/dev/null || return 1
  docker start -a "$CLIENT" >"$SCRATCH/client-$anzeige" 2>&1
  ok=$?
  if grep -qF -e "$kennwort" "$SCRATCH/client-$anzeige" && [ "$aufbau" = 1 ]; then
    leck=1
    echo "vergleichslauf-d: Ausgabe des Clients ($anzeige) nennt das Kennwort des Servers; sie wird nicht gedruckt"
  else
    cat "$SCRATCH/client-$anzeige"
  fi
  docker cp "$CLIENT:/tmp/live.txt" "$ausgabe" >/dev/null 2>&1
  docker rm -f "$CLIENT" >/dev/null 2>&1
  CLIENT=""
  pruefe "Live-Szenario gegen $anzeige: Client-Lauf bestanden, Ausgabedatei mit Schritten" \
    sh -c "[ $ok -eq 0 ] && [ -s '$ausgabe' ]"
  pruefe "Ausgabe des Clients ($anzeige) nennt das Kennwort des Servers nicht" test "$leck" -eq 0
}

# livelauf <Name>: der Live-Lauf nach SPEC-045. Dasselbe Szenario laeuft nacheinander gegen die Referenzinstanz (Stufe) und
# gegen den Serverprozess mit Simulations-Backend (Dockerfile-Stage `kompatibilitaetsserver`, dieselbe wie bei
# `make kompatibilitaet`), im selben internen Netzwerk; danach vergleicht `vergleichen` die beiden Ausgaben mit
# referenztest-dotnet/Vergleich/live-ausnahmen.tsv. Die Zeilen `live: referenz|server <Schritt>` sind die Messwerte, die
# die Ausnahmeliste (docs/messungen/referenzabgleich.md) zitiert.
livelauf() {
  local name="$1" t_fertig
  parameter "$name" || return 1
  echo "vergleichslauf-d: --- Lauf $name (Stufe $STUFE, Kategorie $KATEGORIE) ---"
  referenz_hochfahren "$name" || return 1
  docker logs "$SERVER" 2>&1 | grep -q 'Recovery is complete' || return 1
  live_client referenz referenz:1433 sa "$KENNWORT" 1 "$SCRATCH/server.pem" "$SCRATCH/live-referenz.txt" || return 1
  [ "$FEHLER" -eq 0 ] || return 1

  SIM_IMAGE="tdswire-live-server:$LAUF"
  SIM_SERVER="$LAUF-$name-sim"
  # shellcheck disable=SC2086
  docker build ${BUILDER_FLAGS:-} --load --target kompatibilitaetsserver -t "$SIM_IMAGE" . || return 1
  docker run -d --name "$SIM_SERVER" --network "$NETZ" --network-alias testserver "$SIM_IMAGE" >/dev/null || return 1
  gestartet() {
    local i
    for i in $(seq 1 120); do
      docker logs "$SIM_SERVER" 2>&1 | grep -q 'tdswire: gestartet, Port 1433' && return 0
      [ "$(docker inspect --format '{{.State.Running}}' "$SIM_SERVER")" = "true" ] || return 1
      sleep 1
    done
    return 1
  }
  pruefe "Server mit Simulations-Backend meldet 'gestartet, Port 1433'" gestartet
  [ "$FEHLER" -eq 0 ] || { docker logs "$SIM_SERVER" 2>&1 | tail -20; return 1; }
  docker cp "$SIM_SERVER:/home/gradle/server.pem" "$SCRATCH/sim.pem" >/dev/null || return 1
  live_client server testserver:1433 alice secret 0 "$SCRATCH/sim.pem" "$SCRATCH/live-server.txt" || return 1
  [ "$FEHLER" -eq 0 ] || return 1

  sed 's/^/vergleichslauf-d: live: referenz /' "$SCRATCH/live-referenz.txt"
  sed 's/^/vergleichslauf-d: live: server   /' "$SCRATCH/live-server.txt"
  pruefe "Live-Vergleich: gleich oder genau die gelistete Abweichung" \
    vergleichen "$SCRATCH/live-referenz.txt" "$SCRATCH/live-server.txt" referenztest-dotnet/Vergleich/live-ausnahmen.tsv "$STUFE"
  t_fertig=$((SECONDS - START))
  echo "vergleichslauf-d: messung: Lauf $name $t_fertig s (mit Bau und Start $T_BEREIT s)"

  docker rm -f "$SIM_SERVER" "$SERVER" >/dev/null 2>&1
  docker network rm "$NETZ" >/dev/null 2>&1
  docker rmi -f "$SERVER_IMAGE" "$SIM_IMAGE" >/dev/null 2>&1
  SERVER="" SIM_SERVER="" NETZ="" SERVER_IMAGE="" SIM_IMAGE=""
  return 0
}

# aufnahmelauf <Name>: die Aufnahme der Mitschnitte (SPEC-045). Die Referenzinstanz (Stufe) startet frisch; der Client-Container
# fuehrt die Szenarien der Kategorie MitschnittAufnahme (referenztest-dotnet/Vergleich/MitschnittLaufTests.cs) ueber den
# mitschneidenden Proxy in seinem Container aus und legt die Mitschnitte in /tmp/aufnahme ab. Danach liegen die Dateien in
# $SCRATCH, werden auf das Kennwort der Instanz geprueft (Klartextsuche; die Formen im Draht prueft der Test) und ersetzen
# referenztest-dotnet/Mitschnitt/aufnahmen/<Stufe>/ im Arbeitsbaum. Die Herkunft (Instanz laut Log, Image mit Digest laut
# Dockerfile, Hash des Testschemas) geht als Umgebung an den Test und steht im Kopf jeder Datei.
aufnahmelauf() {
  local name="$1" instanz image schema ok ziel_verzeichnis
  parameter "$name" || return 1
  CLIENT="$LAUF-$name-client"
  echo "vergleichslauf-d: --- Lauf $name (Stufe $STUFE, Kategorie $KATEGORIE) ---"
  referenz_hochfahren "$name" || return 1
  instanz=$(docker logs "$SERVER" 2>&1 | grep -m1 -E 'Microsoft SQL Server' | tr -d '\r' | sed -E 's/^[0-9: .-]+Server +//; s/ +$//')
  image=$(grep -E "AS referenzinstanz-$STUFE\$" referenztest-dotnet/Dockerfile | sed -E 's/^FROM ([^ ]+) AS .*/\1/')
  schema="sha256:$(sha256sum referenztest-dotnet/Vergleich/testschema.sql | cut -d' ' -f1)"
  docker create --name "$CLIENT" --network "$NETZ" -e "TDSWIRE_ZIEL=referenz:1433" -e "TDSWIRE_REFERENZ_KENNWORT=$KENNWORT" \
    -e SSL_CERT_FILE=/tmp/server.pem -e TDSWIRE_MITSCHNITT_AUSGABE=/tmp/aufnahme -e "TDSWIRE_HERKUNFT_INSTANZ=$instanz" \
    -e "TDSWIRE_HERKUNFT_IMAGE=$image" -e "TDSWIRE_HERKUNFT_SCHEMA=$schema" \
    "$CLIENT_IMAGE" dotnet test --no-build --nologo --filter "Kategorie=MitschnittAufnahme" --logger "console;verbosity=detailed" >/dev/null || return 1
  docker cp "$SCRATCH/server.pem" "$CLIENT:/tmp/server.pem" >/dev/null || return 1
  docker start -a "$CLIENT" >"$SCRATCH/client-$name" 2>&1
  ok=$?
  if grep -qF -e "$KENNWORT" "$SCRATCH/client-$name"; then
    echo "vergleichslauf-d: Ausgabe des Clients nennt das Kennwort des Servers; sie wird nicht gedruckt"
    FEHLER=1
  else
    cat "$SCRATCH/client-$name"
  fi
  docker cp "$CLIENT:/tmp/aufnahme/." "$SCRATCH/aufnahme-$STUFE" >/dev/null 2>&1
  docker rm -f "$CLIENT" >/dev/null 2>&1
  CLIENT=""
  pruefe "Aufnahme $name: Test bestanden, Mitschnitte vorhanden" sh -c "[ $ok -eq 0 ] && ls '$SCRATCH/aufnahme-$STUFE'/*.mitschnitt >/dev/null 2>&1"
  keine_kennwort_treffer() { ! grep -rqF -e "$KENNWORT" "$SCRATCH/aufnahme-$STUFE"; }
  pruefe "Aufnahme $name: das Kennwort steht in keiner Datei der Ablage" keine_kennwort_treffer
  [ "$FEHLER" -eq 0 ] || return 1
  ziel_verzeichnis="referenztest-dotnet/Mitschnitt/aufnahmen/$STUFE"
  mkdir -p "$ziel_verzeichnis" && rm -f "$ziel_verzeichnis"/*.mitschnitt && cp "$SCRATCH/aufnahme-$STUFE"/*.mitschnitt "$ziel_verzeichnis"/ || return 1
  echo "vergleichslauf-d: messung: Aufnahme $name: $(ls "$ziel_verzeichnis" | wc -l) Mitschnitte in $ziel_verzeichnis"
  docker rm -f "$SERVER" >/dev/null 2>&1
  docker network rm "$NETZ" >/dev/null 2>&1
  docker rmi -f "$SERVER_IMAGE" >/dev/null 2>&1
  SERVER="" NETZ="" SERVER_IMAGE=""
  return 0
}

# mitschnittlauf <Name>: das Abspielen der abgelegten Mitschnitte (SPEC-045) gegen einen frisch gestarteten Server dieses
# Repos im eigenen internen Netzwerk: Dockerfile-Stage `kompatibilitaetsserver`, im Klartext (ENCRYPTION_POLICY=TEST_UNENCRYPTED,
# ohne Schluesselspeicher; der Relais des Clients spielt die Anfragen im Klartext ab). Der Client-Container fuehrt die Kategorie
# MitschnittAbspielen aus und legt je Seite eine Datei `Kennung TAB Ergebnis` ab; `vergleichen` urteilt mit
# referenztest-dotnet/Vergleich/mitschnitt-ausnahmen.tsv. Eine Referenzinstanz startet nicht.
# Kopplung: der Lauf haengt an den ENV-Namen TLS_KEYSTORE_FILE, TLS_KEYSTORE_PASSWORD_FILE und ENCRYPTION_POLICY der Dockerfile-Stage
# `kompatibilitaetsserver` (Wurzel-Dockerfile, SPEC-010) und an deren Gradle-Task `kompatibilitaetsServer` (CMD); ein Umbenennen dort
# aendert diesen Lauf. Das Versagen ist laut: der Abspieler bricht ab, wenn die PRELOGIN-Antwort nicht ENCRYPT_NOT_SUP ist.
mitschnittlauf() {
  local name="$1" ok t_fertig
  parameter "$name" || return 1
  echo "vergleichslauf-d: --- Lauf $name (Stufe $STUFE, Kategorie $KATEGORIE) ---"
  START=$SECONDS
  NETZ="$LAUF-$name-netz"
  docker network create --internal "$NETZ" >/dev/null || return 1
  SIM_IMAGE="tdswire-live-server:$LAUF"
  SIM_SERVER="$LAUF-$name-sim"
  # shellcheck disable=SC2086
  docker build ${BUILDER_FLAGS:-} --load --target kompatibilitaetsserver -t "$SIM_IMAGE" . || return 1
  docker run -d --name "$SIM_SERVER" --network "$NETZ" --network-alias testserver --entrypoint env "$SIM_IMAGE" \
    -u TLS_KEYSTORE_FILE -u TLS_KEYSTORE_PASSWORD_FILE ENCRYPTION_POLICY=TEST_UNENCRYPTED \
    gradle --no-daemon --offline -q kompatibilitaetsServer >/dev/null || return 1
  gestartet() {
    local i
    for i in $(seq 1 120); do
      docker logs "$SIM_SERVER" 2>&1 | grep -q 'tdswire: gestartet, Port 1433' && return 0
      [ "$(docker inspect --format '{{.State.Running}}' "$SIM_SERVER")" = "true" ] || return 1
      sleep 1
    done
    return 1
  }
  pruefe "Server mit Simulations-Backend (Klartext) meldet 'gestartet, Port 1433'" gestartet
  [ "$FEHLER" -eq 0 ] || { docker logs "$SIM_SERVER" 2>&1 | tail -20; return 1; }
  CLIENT="$LAUF-$name-client"
  docker create --name "$CLIENT" --network "$NETZ" -e "TDSWIRE_ZIEL=testserver:1433" -e "TDSWIRE_STUFE=$STUFE" \
    -e TDSWIRE_LIVE_BENUTZER=alice -e TDSWIRE_LIVE_KENNWORT=secret -e TDSWIRE_MITSCHNITT_REFERENZ=/tmp/mref.txt \
    -e TDSWIRE_MITSCHNITT_SERVER=/tmp/msrv.txt \
    "$CLIENT_IMAGE" dotnet test --no-build --nologo --filter "Kategorie=MitschnittAbspielen" --logger "console;verbosity=detailed" >/dev/null || return 1
  docker start -a "$CLIENT" >"$SCRATCH/client-$name" 2>&1
  ok=$?
  cat "$SCRATCH/client-$name"
  docker cp "$CLIENT:/tmp/mref.txt" "$SCRATCH/mitschnitt-referenz.txt" >/dev/null 2>&1
  docker cp "$CLIENT:/tmp/msrv.txt" "$SCRATCH/mitschnitt-server.txt" >/dev/null 2>&1
  docker rm -f "$CLIENT" >/dev/null 2>&1
  CLIENT=""
  pruefe "Abspielen $name: Client-Lauf bestanden, beide Ausgabedateien vorhanden" \
    sh -c "[ $ok -eq 0 ] && [ -s '$SCRATCH/mitschnitt-referenz.txt' ] && [ -s '$SCRATCH/mitschnitt-server.txt' ]"
  [ "$FEHLER" -eq 0 ] || return 1
  sed 's/^/vergleichslauf-d: mitschnitt: referenz /' "$SCRATCH/mitschnitt-referenz.txt"
  sed 's/^/vergleichslauf-d: mitschnitt: server   /' "$SCRATCH/mitschnitt-server.txt"
  pruefe "Mitschnitt-Vergleich: gleich oder genau die gelistete Abweichung" \
    vergleichen "$SCRATCH/mitschnitt-referenz.txt" "$SCRATCH/mitschnitt-server.txt" referenztest-dotnet/Vergleich/mitschnitt-ausnahmen.tsv "$STUFE"
  t_fertig=$((SECONDS - START))
  echo "vergleichslauf-d: messung: Lauf $name $t_fertig s (mit Bau und Start des Servers)"
  docker rm -f "$SIM_SERVER" >/dev/null 2>&1
  docker network rm "$NETZ" >/dev/null 2>&1
  docker rmi -f "$SIM_IMAGE" >/dev/null 2>&1
  SIM_SERVER="" NETZ="" SIM_IMAGE=""
  return 0
}

# wirelauf <Name>: die Messreihe am Draht (SPEC-045). Die Referenzinstanz (Stufe) startet frisch; der Client-Container fuehrt die
# Szenarien der Kategorie MessungWire oder MessungBulk (referenztest-dotnet/Vergleich/MessungWireTests.cs, MessungBulkTests.cs) ueber den TLS-beendenden Proxy aus und
# legt die Messungen in /tmp/messung ab. Danach liegen die Dateien in $SCRATCH, werden auf das Kennwort der Instanz geprueft
# (Klartextsuche; die Formen im Draht prueft der Test) und ersetzen referenztest-dotnet/Mitschnitt/messung/<Stufe>/ im Arbeitsbaum.
# Anders als die Aufnahme enthalten sie PRELOGIN, LOGIN7 (redigiert) und die Antwort der Anmeldung; sie werden nicht abgespielt.
wirelauf() {
  local name="$1" instanz image schema ok ziel_verzeichnis
  parameter "$name" || return 1
  CLIENT="$LAUF-$name-client"
  echo "vergleichslauf-d: --- Lauf $name (Stufe $STUFE, Kategorie $KATEGORIE) ---"
  referenz_hochfahren "$name" || return 1
  instanz=$(docker logs "$SERVER" 2>&1 | grep -m1 -E 'Microsoft SQL Server' | tr -d '\r' | sed -E 's/^[0-9: .-]+Server +//; s/ +$//')
  image=$(grep -E "AS referenzinstanz-$STUFE\$" referenztest-dotnet/Dockerfile | sed -E 's/^FROM ([^ ]+) AS .*/\1/')
  schema="sha256:$(sha256sum referenztest-dotnet/Vergleich/testschema.sql | cut -d' ' -f1)"
  docker create --name "$CLIENT" --network "$NETZ" -e "TDSWIRE_ZIEL=referenz:1433" -e "TDSWIRE_REFERENZ_KENNWORT=$KENNWORT" \
    -e SSL_CERT_FILE=/tmp/server.pem -e TDSWIRE_MESSUNG_AUSGABE=/tmp/messung -e "TDSWIRE_MESSUNG_NUR=${MESSUNG_NUR:-}" -e "TDSWIRE_MESSUNG_WIEDERHOLEN=${MESSUNG_WIEDERHOLEN:-}" -e "TDSWIRE_MESSUNG_DIREKT=${MESSUNG_DIREKT:-}" -e "TDSWIRE_HERKUNFT_INSTANZ=$instanz" \
    -e "TDSWIRE_HERKUNFT_IMAGE=$image" -e "TDSWIRE_HERKUNFT_SCHEMA=$schema" \
    "$CLIENT_IMAGE" dotnet test --no-build --nologo --filter "Kategorie=$KATEGORIE" --logger "console;verbosity=detailed" >/dev/null || return 1
  docker cp "$SCRATCH/server.pem" "$CLIENT:/tmp/server.pem" >/dev/null || return 1
  docker start -a "$CLIENT" >"$SCRATCH/client-$name" 2>&1
  ok=$?
  if grep -qF -e "$KENNWORT" "$SCRATCH/client-$name"; then
    echo "vergleichslauf-d: Ausgabe des Clients nennt das Kennwort des Servers; sie wird nicht gedruckt"
    FEHLER=1
  else
    cat "$SCRATCH/client-$name"
  fi
  docker cp "$CLIENT:/tmp/messung/." "$SCRATCH/messung-$STUFE" >/dev/null 2>&1
  docker rm -f "$CLIENT" >/dev/null 2>&1
  CLIENT=""
  pruefe "Messreihe $name: Test bestanden, Messungen vorhanden" sh -c "[ $ok -eq 0 ] && ls '$SCRATCH/messung-$STUFE'/*.messung >/dev/null 2>&1"
  keine_kennwort_treffer() { ! grep -rqF -e "$KENNWORT" "$SCRATCH/messung-$STUFE"; }
  pruefe "Messreihe $name: Klartextsuche nach dem Kennwort in der Ablage ohne Treffer (Nutzlasten stehen als Hex; die tragende Prüfung ist MessDatei.Treffer im Test)" keine_kennwort_treffer
  [ "$FEHLER" -eq 0 ] || return 1
  ziel_verzeichnis="referenztest-dotnet/Mitschnitt/messung/$STUFE"
  [ "$KATEGORIE" != MessungBulk ] || ziel_verzeichnis="referenztest-dotnet/Mitschnitt/messung/bulk-merge-katalog/$STUFE"
  # MESSUNG_NUR (Namen der Szenarien, durch Komma getrennt) ist die Teilmenge fuer die Fehlersuche: sie ersetzt die Ablage nicht.
  if [ -n "${MESSUNG_NUR:-}" ]; then
    echo "vergleichslauf-d: messung: Teilmenge $MESSUNG_NUR, die Ablage im Arbeitsbaum bleibt unveraendert"
  else
    mkdir -p "$ziel_verzeichnis" && rm -f "$ziel_verzeichnis"/*.messung && cp "$SCRATCH/messung-$STUFE"/*.messung "$ziel_verzeichnis"/ || return 1
    echo "vergleichslauf-d: messung: Messreihe $name: $(ls "$ziel_verzeichnis" | wc -l) Messungen in $ziel_verzeichnis"
  fi
  docker rm -f "$SERVER" >/dev/null 2>&1
  docker network rm "$NETZ" >/dev/null 2>&1
  docker rmi -f "$SERVER_IMAGE" >/dev/null 2>&1
  SERVER="" NETZ="" SERVER_IMAGE=""
  return 0
}

gesamt=$SECONDS
# shellcheck disable=SC2086
docker build ${BUILDER_FLAGS:-} --load --target vergleich -t "$CLIENT_IMAGE" referenztest-dotnet || exit 1
echo "vergleichslauf-d: messung: Bau des Client-Images $((SECONDS - gesamt)) s"
for lauf in $LISTE; do
  case "$lauf" in
    live-*) livelauf "$lauf" || FEHLER=1 ;;
    aufnahme-*) aufnahmelauf "$lauf" || FEHLER=1 ;;
    mitschnitt-*) mitschnittlauf "$lauf" || FEHLER=1 ;;
    wire-* | bulk-*) wirelauf "$lauf" || FEHLER=1 ;;
    *) einzellauf "$lauf" || FEHLER=1 ;;
  esac
  [ "$FEHLER" -eq 0 ] || break
done
echo "vergleichslauf-d: messung: Laufzeit gesamt $((SECONDS - gesamt)) s"
[ "$FEHLER" -eq 0 ] || { echo "vergleichslauf-d: ROT"; exit 1; }
echo "vergleichslauf-d: gruen"
