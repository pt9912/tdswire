#!/usr/bin/env bash
# tools/rueckstau.sh - Rueckstau-Messung (TDS-QA-04, ADR-0006, ADR-0022). Aufruf ueber `make rueckstau`; BUILDER_FLAGS kommt aus der
# Umgebung. Kein Gate, nicht in `make gates`, nicht in `make e2e`.
#
# Jede Zelle ist ein Lauf mit frischem Serverprozess (Dockerfile-Stage `lasttestserver`, Hauptklasse RueckstauServerKt) und einem Szenario
# des Referenzclients (referenztest-dotnet/Rueckstau/RueckstauTests.cs: Leser, Blockierend, Abbruch, Import). Server und Client sind
# Container eines internen Docker-Netzes (kein Zugang nach aussen); Limits wie der Lasttest (Server --cpus 2 --memory 4g, -Xmx256m, Client
# --cpus 2 --memory 2g). Zellen (RUECKSTAU_ZELLEN, durch Leerzeichen getrennt, Vorgabe alle):
#   leser-eigen-{1,8,64,256,256halb}  leser-sim-{1,8,64,256}   langsamer Leser (ein langsamer, bei 256halb 128 langsame Sitzungen), QUERY_TIMEOUT 3600 s;
#   leser-frist   ein langsamer Leser haelt den Strom laenger als QUERY_TIMEOUT (3 s), der Lauf haelt das Ende des Stroms fest (nicht in der Vorgabe)
#   blk-{suspendierend,unterbrechbar,nicht-unterbrechbar}-{8,64,256}, blk-direkt-8   Backend wartet je Zeile 10 ms
#   abbruch-{suspendierend,unterbrechbar,nicht-unterbrechbar}[-ohnelast]   Cancel bei wartendem Backend, CANCEL_TIMEOUT 2 s, 8 Sitzungen lesen daneben
#   imp-{1-schnell,1-callback,8-callback,8-callback-direkt,8-callback-unterbrechbar,1-sender}   Massenladen gegen das Simulations-Backend
# Je Zelle: Zeile `zelle:` (Zustand des Hosts vorher und nachher, Fremdlast), die Zeilen `rueckstau:` des Clients, der Kopf des Servers.
# Fremdlast: alle 10 s die CPU des Hosts (/proc/stat) abzueglich der CPU der beiden Container (docker stats); `proben` und `ueber300`
# (Proben mit mehr als 300 % fremder CPU) und `max` stehen in der Zeile `zelle:`. Der Lauf wartet nicht auf einen ruhigen Host.
# Umgebung: RUECKSTAU_MIN_FREI_MB=4096 (unter dieser verfuegbaren Menge bricht der Lauf vor jedem Container ab, Exit 3), RUECKSTAU_ZELLEN.
set -u
cd "$(dirname "$0")/.." || exit 1

min_frei="${RUECKSTAU_MIN_FREI_MB:-4096}"
[[ $min_frei =~ ^(0|[1-9][0-9]{0,8})$ ]] || { echo "rueckstau: RUECKSTAU_MIN_FREI_MB muss eine nichtnegative ganze Zahl sein, nicht '$min_frei'" >&2; exit 2; }
alle="leser-eigen-1 leser-eigen-8 leser-eigen-64 leser-eigen-256 leser-eigen-256halb leser-sim-1 leser-sim-8 leser-sim-64 leser-sim-256 blk-suspendierend-64 blk-unterbrechbar-8 blk-unterbrechbar-64 blk-unterbrechbar-256 blk-nicht-unterbrechbar-64 blk-nicht-unterbrechbar-256 blk-direkt-8 abbruch-unterbrechbar abbruch-nicht-unterbrechbar imp-1-schnell imp-1-callback imp-8-callback imp-8-callback-direkt imp-8-callback-unterbrechbar imp-1-sender"
zellen="${RUECKSTAU_ZELLEN:-$alle}"
klasse="io.github.pt9912.tdswire.testserver"
proc="${RUECKSTAU_PROC:-/proc}"

lauf="tdswire-rs-$$"
netz="$lauf-netz"
server="$lauf-server"
server_image="tdswire-rueckstauserver:$lauf"
client_image="tdswire-rueckstauclient:$lauf"
scratch="$(mktemp -d)"
fehler=0

aufraeumen() {
	docker rm -f "$server" >/dev/null 2>&1
	docker network rm "$netz" >/dev/null 2>&1
	docker rmi -f "$server_image" "$client_image" >/dev/null 2>&1
	rm -rf "$scratch"
}
trap aufraeumen EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

frei_mb() { awk '/^MemAvailable:/ {print int($2 / 1024)}' "$proc/meminfo"; }
swap_mb() { awk '/^SwapTotal:/ {t=$2} /^SwapFree:/ {f=$2} END {print int((t - f) / 1024)}' "$proc/meminfo"; }
zustand() { echo "last $(awk '{print $1"/"$2"/"$3}' "$proc/loadavg") swap_mb $(swap_mb) frei_mb $(frei_mb) container_fremd $(( $(docker ps -q | wc -l) - $(docker ps -q --filter "name=$lauf" | wc -l) ))"; }

speicherwacht() {
	local frei
	frei="$(frei_mb)"
	if [ "$frei" -lt "$min_frei" ]; then
		echo "rueckstau: ABBRUCH - verfuegbarer Speicher des Hosts $frei MiB unter $min_frei MiB" >&2
		exit 3
	fi
}

# CPU des Hosts in Prozent eines Kerns (Summe aller Kerne) zwischen zwei Aufrufen: cpu_probe <Zustandsdatei>
cpu_host() {
	awk '/^cpu / {idle = $5 + $6; total = 0; for (i = 2; i <= NF; i++) total += $i; print idle, total}' "$proc/stat"
}

# Fremdlast-Probe im Hintergrund: alle 10 s eine Zeile "t host_pct eigen_pct fremd_pct" in <Datei>, bis <Datei>.stop existiert.
fremdlast() {
	local datei="$1" a b idle0 total0 idle1 total1 host eigen
	a="$(cpu_host)"
	while [ ! -e "$datei.stop" ]; do
		sleep 10
		[ ! -e "$datei.stop" ] || break
		eigen="$(docker stats --no-stream --format '{{.CPUPerc}}' "$server" "$lauf-client" 2>/dev/null | tr -d '%' | awk '{s += $1} END {printf "%.0f", s}')"
		b="$(cpu_host)"
		read -r idle0 total0 <<<"$a"
		read -r idle1 total1 <<<"$b"
		host="$(awk -v i0="$idle0" -v t0="$total0" -v i1="$idle1" -v t1="$total1" 'BEGIN {d = t1 - t0; if (d > 0) printf "%.0f", (1 - (i1 - i0) / d) * 100 * '"$(nproc)"'; else print 0}')"
		echo "$(date +%s) $host ${eigen:-0} $((host - ${eigen:-0}))" >>"$datei"
		a="$b"
	done
}

fremdlast_summe() {
	awk '{n++; if ($4 > 300) k++; if ($4 > m) m = $4} END {printf "proben %d ueber300 %d max %d%%", n, k, m}' "$1" 2>/dev/null || echo "proben 0"
}

# zelle <Name> <Szenario-Fact> "<Server-Umgebung>" "<Client-Umgebung>"
zelle() {
	local name="$1" fact="$2" senv="$3" cenv="$4" vorher nachher gestartet probe args=() cargs=() e
	speicherwacht
	vorher="$(zustand)"
	for e in $senv; do args+=(-e "$e"); done
	for e in $cenv; do cargs+=(-e "$e"); done
	docker run -d --name "$server" --network "$netz" --cpus 2 --memory 4g --memory-swap 4g -e LASTTEST_XMX=256m "${args[@]}" "$server_image" "$klasse.RueckstauServerKt" >/dev/null || { fehler=1; return; }
	gestartet=0
	for _ in $(seq 1 120); do
		if docker logs "$server" 2>&1 | grep -q 'tdswire: gestartet, Port 1433'; then gestartet=1; break; fi
		[ "$(docker inspect --format '{{.State.Running}}' "$server")" = "true" ] || break
		sleep 1
	done
	if [ "$gestartet" -ne 1 ]; then
		docker logs "$server" 2>&1 | tail -20
		echo "rueckstau: FEHLER Server der Zelle $name nicht gestartet" >&2
		fehler=1
		docker rm -f "$server" >/dev/null 2>&1
		return
	fi
	echo "$(docker logs "$server" 2>&1 | grep -m1 '^rueckstau: server') swap_max $(docker exec "$server" cat /sys/fs/cgroup/memory.swap.max 2>/dev/null || echo unbekannt)"
	docker exec "$server" sh -c 'echo "rueckstau: netz eth0_mtu $(cat /sys/class/net/eth0/mtu) lo_mtu $(cat /sys/class/net/lo/mtu) tcp_rmem [$(cat /proc/sys/net/ipv4/tcp_rmem)] tcp_wmem [$(cat /proc/sys/net/ipv4/tcp_wmem)]"' 2>/dev/null
	probe="$scratch/probe-$name"
	rm -f "$probe" "$probe.stop"
	: >"$probe"
	speicherwacht
	fremdlast "$probe" &
	local probe_pid=$!
	docker run --rm --name "$lauf-client" --network "$netz" --cpus 2 --memory 2g --memory-swap 2g -e "TDSWIRE_ZIEL=$server:1433" "${cargs[@]}" \
		"$client_image" dotnet test --no-build --nologo --logger "console;verbosity=detailed" --filter "FullyQualifiedName~RueckstauTests.$fact" >"$scratch/client" 2>&1
	local client_ok=$?
	touch "$probe.stop"
	wait "$probe_pid" 2>/dev/null
	nachher="$(zustand)"
	tr -d '\r' <"$scratch/client" | grep -E -A6 '^ *rueckstau: |Passed!|Failed!|Failed |Error Message' | grep -vE '^--$|Stack Trace' | sed 's/^ *//' | head -80
	[ "$client_ok" -eq 0 ] || { echo "rueckstau: FEHLER Client der Zelle $name (Exit $client_ok)" >&2; fehler=1; }
	echo "rueckstau: server peak $(docker exec "$server" cat /sys/fs/cgroup/memory.peak 2>/dev/null || echo unbekannt) Bytes (cgroup), laeuft $(docker inspect --format '{{.State.Running}}' "$server")"
	echo "zelle: $name $(date -u +%Y-%m-%dT%H:%M:%SZ) vorher [$vorher] nachher [$nachher] fremdlast $(fremdlast_summe "$probe")"
	docker rm -f "$server" >/dev/null 2>&1
}

start=$(date +%s)
speicherwacht
echo "rueckstau: Kopf $(date -u +%Y-%m-%dT%H:%M:%SZ) host kerne $(nproc) ram_mb $(awk '/^MemTotal:/ {print int($2 / 1024)}' "$proc/meminfo") kernel $(uname -r) docker $(docker version --format '{{.Server.Version}}' 2>/dev/null) commit $(git rev-parse --short HEAD 2>/dev/null || echo unbekannt)$([ -n "$(git status --porcelain 2>/dev/null)" ] && echo "+geaendert")"
echo "rueckstau: Kopf server cpus 2 memory 4g memory-swap 4g xmx 256m client cpus 2 memory 2g zellen [$zellen]"
echo "rueckstau: Kopf zustand vorher: $(zustand)"
# shellcheck disable=SC2086
docker build ${BUILDER_FLAGS:-} --load --target lasttestserver -t "$server_image" . || exit 1
# shellcheck disable=SC2086
docker build ${BUILDER_FLAGS:-} --load --target rueckstau -t "$client_image" referenztest-dotnet || exit 1
gebaut=$(date +%s)
echo "rueckstau: Kopf images server $(docker image inspect --format '{{.Id}}' "$server_image") client $(docker image inspect --format '{{.Id}}' "$client_image")"
docker network create --internal "$netz" >/dev/null || exit 1

for z in $zellen; do
	case "$z" in
	leser-eigen-1) zelle "$z" Leser "RUECKSTAU_AUFBAU=eigen QUERY_TIMEOUT=3600" "RS_N=1 RS_LANGSAM=1 RS_ZEILEN=100000" ;;
	leser-eigen-8) zelle "$z" Leser "RUECKSTAU_AUFBAU=eigen QUERY_TIMEOUT=3600" "RS_N=8 RS_LANGSAM=1 RS_ZEILEN=100000" ;;
	leser-eigen-64) zelle "$z" Leser "RUECKSTAU_AUFBAU=eigen QUERY_TIMEOUT=3600" "RS_N=64 RS_LANGSAM=1 RS_ZEILEN=100000" ;;
	leser-eigen-256) zelle "$z" Leser "RUECKSTAU_AUFBAU=eigen QUERY_TIMEOUT=3600" "RS_N=256 RS_LANGSAM=1 RS_ZEILEN=20000" ;;
	leser-eigen-256halb) zelle "$z" Leser "RUECKSTAU_AUFBAU=eigen QUERY_TIMEOUT=3600" "RS_N=256 RS_LANGSAM=128 RS_ZEILEN=20000" ;;
	leser-frist) zelle "$z" Leser "RUECKSTAU_AUFBAU=eigen QUERY_TIMEOUT=3" "RS_N=1 RS_LANGSAM=1 RS_ZEILEN=100000 RS_NACH_MS=3000 RS_ERWARTE_FRIST=1" ;;
	leser-sim-1) zelle "$z" Leser "RUECKSTAU_AUFBAU=sim RUECKSTAU_ZEILEN=100000 QUERY_TIMEOUT=3600" "RS_N=1 RS_LANGSAM=1 RS_ZEILEN=100000" ;;
	leser-sim-8) zelle "$z" Leser "RUECKSTAU_AUFBAU=sim RUECKSTAU_ZEILEN=100000 QUERY_TIMEOUT=3600" "RS_N=8 RS_LANGSAM=1 RS_ZEILEN=100000" ;;
	leser-sim-64) zelle "$z" Leser "RUECKSTAU_AUFBAU=sim RUECKSTAU_ZEILEN=100000 QUERY_TIMEOUT=3600" "RS_N=64 RS_LANGSAM=1 RS_ZEILEN=100000" ;;
	leser-sim-256) zelle "$z" Leser "RUECKSTAU_AUFBAU=sim RUECKSTAU_ZEILEN=20000 QUERY_TIMEOUT=3600" "RS_N=256 RS_LANGSAM=1 RS_ZEILEN=20000" ;;
	blk-*)
		modus="${z#blk-}"
		modus="${modus%-*}"
		n="${z##*-}"
		zelle "$z" Blockierend "RUECKSTAU_AUFBAU=eigen RUECKSTAU_POOL=64" "RS_N=$n RS_MODUS=$modus RS_ZEILEN=100 RS_BLOCK_MS=10 RS_JEDE=1"
		;;
	abbruch-*)
		last=8
		modus="${z#abbruch-}"
		case "$modus" in *-ohnelast) last=0; modus="${modus%-ohnelast}" ;; esac
		zelle "$z" Abbruch "RUECKSTAU_AUFBAU=eigen RUECKSTAU_POOL=64 CANCEL_TIMEOUT=2" "RS_MODUS=$modus RS_BLOCK_MS=10000 RS_JEDE=3 RS_ABBRUCH_NACH_MS=1500 RS_LAST=$last RS_ZEILEN=100000"
		;;
	imp-1-schnell) zelle "$z" Import "RUECKSTAU_AUFBAU=sim" "RS_N=1 RS_ZEILEN=100000" ;;
	imp-1-callback) zelle "$z" Import "RUECKSTAU_AUFBAU=sim RUECKSTAU_CALLBACK_MODUS=suspendierend RUECKSTAU_CALLBACK_MS=50" "RS_N=1 RS_ZEILEN=100000" ;;
	imp-8-callback) zelle "$z" Import "RUECKSTAU_AUFBAU=sim RUECKSTAU_CALLBACK_MODUS=suspendierend RUECKSTAU_CALLBACK_MS=50" "RS_N=8 RS_ZEILEN=100000" ;;
	imp-8-callback-direkt) zelle "$z" Import "RUECKSTAU_AUFBAU=sim RUECKSTAU_CALLBACK_MODUS=direkt RUECKSTAU_CALLBACK_MS=50" "RS_N=8 RS_ZEILEN=20000" ;;
	imp-8-callback-unterbrechbar) zelle "$z" Import "RUECKSTAU_AUFBAU=sim RUECKSTAU_POOL=64 RUECKSTAU_CALLBACK_MODUS=unterbrechbar RUECKSTAU_CALLBACK_MS=50" "RS_N=8 RS_ZEILEN=20000" ;;
	imp-1-sender) zelle "$z" Import "RUECKSTAU_AUFBAU=sim" "RS_N=1 RS_ZEILEN=5000 RS_SENDER_MS=1" ;;
	*)
		echo "rueckstau: unbekannte Zelle '$z'" >&2
		fehler=1
		;;
	esac
done

fertig=$(date +%s)
echo "rueckstau: Kopf zustand nachher: $(zustand)"
echo "rueckstau: Laufzeit $((fertig - start)) s, davon Bau $((gebaut - start)) s und Messung $((fertig - gebaut)) s"
[ "$fehler" -eq 0 ] || { echo "rueckstau: ROT"; exit 1; }
echo "rueckstau: gruen"
