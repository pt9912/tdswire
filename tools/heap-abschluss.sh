#!/usr/bin/env bash
# tools/heap-abschluss.sh - Heap- und Abschluss-Messung (TDS-QA-04, ADR-0006, ADR-0022). Aufruf ueber `make heap-abschluss`; BUILDER_FLAGS kommt
# aus der Umgebung. Kein Gate, nicht in `make gates`, nicht in `make e2e`.
#
# Jede Zelle ist ein Lauf mit frischem Serverprozess (Dockerfile-Stage `lasttestserver`, Hauptklasse HeapAbschlussServerKt). Server und
# Client sind Container eines internen Docker-Netzes (kein Zugang nach aussen); Limits wie der Lasttest (Server --cpus 2 --memory 4g,
# -Xmx256m, Client --cpus 2 --memory 2g). Zellen (HEAP_ZELLEN, durch Leerzeichen getrennt, Vorgabe alle):
#   heap-{eigen,sim}-{1,8,16}   N Sitzungen des Referenzclients (referenztest-dotnet/Heap/HeapTests.cs) lesen gleichzeitig je HEAP_ZEILEN Zeilen
#                               (Vorgabe 10 000 000) von ref_zeilen; eigen: eigenes Backend (Aufbau B), sim: Simulations-Backend (Aufbau C);
#                               der Server nimmt alle 500 ms eine Stichprobe des Live-Heaps nach einer Speicherbereinigung, HEAP_WDH
#                               Wiederholungen (Vorgabe 1) im selben Serverprozess, QUERY_TIMEOUT 3600 s
#   abschluss                   Rohbytes-Client (AbschlussClientKt, ein JVM-Container) gegen das eigene Backend mit verzoegertem Abschluss:
#                               Formen ende und folge, Verzoegerungen ABSCHLUSS_VERZOEGERUNG_MS (Vorgabe 0,10,100,1000), ABSCHLUSS_WDH
#                               Wiederholungen (Vorgabe 5) nach einem Warmlauf, ABSCHLUSS_ZEILEN (Vorgabe 1000)
# Je Zelle: Zeile `zelle:` (Zustand des Hosts vorher und nachher, Fremdlast), die Zeilen `heap:` oder `abschluss:` des Clients, der Kopf des Servers.
# Fremdlast: alle 10 s die CPU des Hosts (/proc/stat) abzueglich der CPU der beiden Container (docker stats); `proben` und `ueber300`
# (Proben mit mehr als 300 % fremder CPU) und `max` stehen in der Zeile `zelle:`. Der Lauf wartet nicht auf einen ruhigen Host.
# Umgebung: HEAP_MIN_FREI_MB=4096 (unter dieser verfuegbaren Menge bricht der Lauf vor jedem Container ab, Exit 3), HEAP_ZELLEN, HEAP_ZEILEN, HEAP_WDH.
set -u
cd "$(dirname "$0")/.." || exit 1

min_frei="${HEAP_MIN_FREI_MB:-4096}"
[[ $min_frei =~ ^(0|[1-9][0-9]{0,8})$ ]] || { echo "heap-abschluss: HEAP_MIN_FREI_MB muss eine nichtnegative ganze Zahl sein, nicht '$min_frei'" >&2; exit 2; }
zeilen="${HEAP_ZEILEN:-10000000}"
[[ $zeilen =~ ^[1-9][0-9]{0,9}$ ]] || { echo "heap-abschluss: HEAP_ZEILEN muss eine positive ganze Zahl sein, nicht '$zeilen'" >&2; exit 2; }
wdh="${HEAP_WDH:-1}"
[[ $wdh =~ ^[1-9][0-9]{0,2}$ ]] || { echo "heap-abschluss: HEAP_WDH muss eine positive ganze Zahl sein, nicht '$wdh'" >&2; exit 2; }
alle="abschluss heap-eigen-1 heap-sim-1 heap-eigen-8 heap-sim-8 heap-eigen-16 heap-sim-16"
zellen="${HEAP_ZELLEN:-$alle}"
klasse="io.github.pt9912.tdswire.testserver"
proc="${HEAP_PROC:-/proc}" # nur der Selbsttest (tools/heap-abschluss-selbsttest.sh) setzt es: eine Nachbildung von meminfo, loadavg und stat

lauf="tdswire-hs-$$"
netz="$lauf-netz"
server="$lauf-server"
server_image="tdswire-heapserver:$lauf"
client_image="tdswire-heapclient:$lauf"
fehler=0

aufraeumen() {
	docker rm -f "$server" "$lauf-client" >/dev/null 2>&1
	docker network rm "$netz" >/dev/null 2>&1
	docker rmi -f "$server_image" "$client_image" >/dev/null 2>&1
	rm -rf "$scratch"
}

frei_mb() { awk '/^MemAvailable:/ {print int($2 / 1024)}' "$proc/meminfo"; }
swap_mb() { awk '/^SwapTotal:/ {t=$2} /^SwapFree:/ {f=$2} END {print int((t - f) / 1024)}' "$proc/meminfo"; }
zustand() { echo "last $(awk '{print $1"/"$2"/"$3}' "$proc/loadavg") swap_mb $(swap_mb) frei_mb $(frei_mb) container_fremd $(( $(docker ps -q | wc -l) - $(docker ps -q --filter "name=$lauf" | wc -l) ))"; }

speicherwacht() {
	local frei
	frei="$(frei_mb)"
	if [ "$frei" -lt "$min_frei" ]; then
		echo "heap-abschluss: ABBRUCH - verfuegbarer Speicher des Hosts $frei MiB unter $min_frei MiB" >&2
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

# zelle <Name> <Server-Umgebung> <Client-Art: dotnet|jvm> <Filter oder Hauptklasse> "<Client-Umgebung>"
zelle() {
	local name="$1" senv="$2" art="$3" ziel="$4" cenv="$5" vorher nachher gestartet probe args=() cargs=() e
	speicherwacht
	vorher="$(zustand)"
	for e in $senv; do args+=(-e "$e"); done
	for e in $cenv; do cargs+=(-e "$e"); done
	docker run -d --name "$server" --network "$netz" --cpus 2 --memory 4g --memory-swap 4g -e LASTTEST_XMX=256m "${args[@]}" "$server_image" "$klasse.HeapAbschlussServerKt" >/dev/null || { fehler=1; return; }
	gestartet=0
	for _ in $(seq 1 120); do
		if docker logs "$server" 2>&1 | grep -q 'tdswire: gestartet, Port 1433'; then gestartet=1; break; fi
		[ "$(docker inspect --format '{{.State.Running}}' "$server")" = "true" ] || break
		sleep 1
	done
	if [ "$gestartet" -ne 1 ]; then
		docker logs "$server" 2>&1 | tail -20
		echo "heap-abschluss: FEHLER Server der Zelle $name nicht gestartet" >&2
		fehler=1
		docker rm -f "$server" >/dev/null 2>&1
		return
	fi
	echo "$(docker logs "$server" 2>&1 | grep -m1 '^heapabschluss: server') swap_max $(docker exec "$server" cat /sys/fs/cgroup/memory.swap.max 2>/dev/null || echo unbekannt)"
	probe="$scratch/probe-$name"
	rm -f "$probe" "$probe.stop"
	: >"$probe"
	speicherwacht
	fremdlast "$probe" &
	local probe_pid=$!
	if [ "$art" = dotnet ]; then
		docker run --rm --name "$lauf-client" --network "$netz" --cpus 2 --memory 2g --memory-swap 2g -e "TDSWIRE_ZIEL=$server:1433" "${cargs[@]}" \
			"$client_image" dotnet test --no-build --nologo --logger "console;verbosity=detailed" --filter "FullyQualifiedName~$ziel" >"$scratch/client" 2>&1
	else
		docker run --rm --name "$lauf-client" --network "$netz" --cpus 2 --memory 2g --memory-swap 2g -e LASTTEST_XMX=128m -e "ABSCHLUSS_ZIEL=$server:1433" "${cargs[@]}" \
			"$server_image" "$klasse.$ziel" >"$scratch/client" 2>&1
	fi
	local client_ok=$?
	touch "$probe.stop"
	wait "$probe_pid" 2>/dev/null
	nachher="$(zustand)"
	tr -d '\r' <"$scratch/client" | grep -E -A6 '^ *(heap|abschluss): |Passed!|Failed!|Failed |Error Message' | grep -vE '^--$|Stack Trace' | sed 's/^ *//' | head -200
	[ "$client_ok" -eq 0 ] || { echo "heap-abschluss: FEHLER Client der Zelle $name (Exit $client_ok)" >&2; fehler=1; }
	echo "heapabschluss: server peak $(docker exec "$server" cat /sys/fs/cgroup/memory.peak 2>/dev/null || echo unbekannt) Bytes (cgroup), laeuft $(docker inspect --format '{{.State.Running}}' "$server")"
	echo "zelle: $name $(date -u +%Y-%m-%dT%H:%M:%SZ) vorher [$vorher] nachher [$nachher] fremdlast $(fremdlast_summe "$probe")"
	docker rm -f "$server" >/dev/null 2>&1
}

# Mit `source` geladen (tools/heap-abschluss-selbsttest.sh) endet das Skript hier: nur die Funktionen stehen dann zur Verfuegung.
[ "${BASH_SOURCE[0]}" = "$0" ] || return 0

# Zellennamen vor dem Bau pruefen (Exit 2); dieselben Namen wie die Auswahl unten.
# Kopplung: dieselben Namen wie der Skriptkopf und die Zweige von `case "$z"` unten. tools/heap-abschluss-selbsttest.sh belegt, dass jeder Name
# seiner Liste `alle` das Muster passiert und einen Zweig hat; ein Name im Muster, der nicht in `alle` steht, ist nicht belegt (ein Name ohne
# Zweig endete sonst erst nach dem Bau der Images in "unbekannte Zelle").
zellen_muster='^(abschluss|heap-(eigen|sim)-(1|8|16))$'
heap_zellen=0
for z in $zellen; do
	[[ $z =~ $zellen_muster ]] || { echo "heap-abschluss: unbekannte Zelle '$z'" >&2; exit 2; }
	[ "$z" = abschluss ] || heap_zellen=1
done
scratch="$(mktemp -d)"
trap aufraeumen EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

start=$(date +%s)
speicherwacht
echo "heap-abschluss: Kopf $(date -u +%Y-%m-%dT%H:%M:%SZ) host kerne $(nproc) ram_mb $(awk '/^MemTotal:/ {print int($2 / 1024)}' "$proc/meminfo") kernel $(uname -r) docker $(docker version --format '{{.Server.Version}}' 2>/dev/null) commit $(git rev-parse --short HEAD 2>/dev/null || echo unbekannt)$([ -n "$(git status --porcelain 2>/dev/null)" ] && echo "+geaendert")"
echo "heap-abschluss: Kopf server cpus 2 memory 4g memory-swap 4g xmx 256m client cpus 2 memory 2g zeilen $zeilen wdh $wdh zellen [$zellen]"
echo "heap-abschluss: Kopf zustand vorher: $(zustand)"
# shellcheck disable=SC2086
docker build ${BUILDER_FLAGS:-} --load --target lasttestserver -t "$server_image" . || exit 1
if [ "$heap_zellen" -eq 1 ]; then
	# shellcheck disable=SC2086
	docker build ${BUILDER_FLAGS:-} --load --target heap -t "$client_image" referenztest-dotnet || exit 1
fi
gebaut=$(date +%s)
echo "heap-abschluss: Kopf images server $(docker image inspect --format '{{.Id}}' "$server_image") client $(docker image inspect --format '{{.Id}}' "$client_image" 2>/dev/null || echo nicht-gebaut)"
docker network create --internal "$netz" >/dev/null || exit 1

# Kopplung: die Zweige entsprechen `zellen_muster` oben und dem Skriptkopf.
for z in $zellen; do
	case "$z" in
	abschluss) zelle "$z" "HEAPABSCHLUSS_AUFBAU=eigen" jvm AbschlussClientKt "ABSCHLUSS_WDH=${ABSCHLUSS_WDH:-5} ABSCHLUSS_ZEILEN=${ABSCHLUSS_ZEILEN:-1000} ABSCHLUSS_VERZOEGERUNG_MS=${ABSCHLUSS_VERZOEGERUNG_MS:-0,10,100,1000}" ;;
	heap-*)
		aufbau="${z#heap-}"
		aufbau="${aufbau%-*}"
		n="${z##*-}"
		zelle "$z" "HEAPABSCHLUSS_AUFBAU=$aufbau HEAPABSCHLUSS_ZEILEN=$zeilen QUERY_TIMEOUT=3600" dotnet HeapTests.Strom "HS_N=$n HS_ZEILEN=$zeilen HS_WDH=$wdh"
		;;
	*)
		echo "heap-abschluss: unbekannte Zelle '$z'" >&2
		fehler=1
		;;
	esac
done

fertig=$(date +%s)
echo "heap-abschluss: Kopf zustand nachher: $(zustand)"
echo "heap-abschluss: Laufzeit $((fertig - start)) s, davon Bau $((gebaut - start)) s und Messung $((fertig - gebaut)) s"
[ "$fehler" -eq 0 ] || { echo "heap-abschluss: ROT"; exit 1; }
echo "heap-abschluss: gruen"
