#!/usr/bin/env bash
# tools/lasttest.sh - Lasttest des Referenz-Workloads (TDS-QA-04, ADR-0006, ADR-0022; docs/plan/referenz-workload.md). Aufruf
# ueber `make lasttest`; BUILDER_FLAGS kommt aus der Umgebung. Kein Gate, nicht in `make gates`.
#
# Aufbauten (S6 und S7): A Backend allein (Kotlin-Prozess ohne Server und Draht, Strom bis zur Senke gezogen: eigenes
# Backend und Simulations-Backend), B Server mit eigenem Backend (nur Export; Massenladen nimmt ein eigenes Backend nicht an,
# der Lauf haelt den Fehler fest), C Server mit Simulations-Backend (Export und Import, das Backend zaehlt die Zeilen).
# B und C laufen je mit und ohne Senke (O7). Server und Client sind Container eines internen Netzes (kein Zugang nach
# aussen, nichts gemountet), Images aus Dockerfile und referenztest-dotnet/Dockerfile (Basis-Images dort per Digest).
#
# Umgebung (Vorgaben sind Setzungen, S2 bis S11): LASTTEST_ZEILEN=100000, LASTTEST_WDH=5 (Laeufe je Stufe nach einem Warmlauf),
# LASTTEST_SEED=42, LASTTEST_CPUS=2, LASTTEST_MEMORY=4g (Server und Aufbau A; --memory-swap gleich --memory),
# LASTTEST_CLIENT_MEMORY=2g (Client, gleiche CPUs), LASTTEST_XMX=256m (Heap des Serverprozesses), LASTTEST_AUFBAUTEN="A B C",
# LASTTEST_SENKEN="mit ohne", LASTTEST_MIN_FREI_MB=4096 (unter dieser verfuegbaren Menge des Hosts bricht der Lauf vor jedem Container ab, Exit 3;
# die Grenze gilt je Container, nicht fuer die Summe der Limits von Server und Client; gemessen sind nur Serverspitzen von 101 bis 178 MB,
# eine Spitze des Clients ist nicht gemessen),
# LASTTEST_LAST_MAX=12 (Last 1 Minute des Hosts, ueber der eine Messzeile "last-hoch" traegt), LASTTEST_SWAP_TOLERANZ_MB=16 (Wachstum des
# Swap des Hosts waehrend der Stufe, ab dem sie "swap-gewachsen" traegt).
#
# Sitzungen (TDS-QA-04, Slice der Staffel): LASTTEST_SITZUNGEN=1 (Zahl gleichzeitiger Sitzungen einer Stufe der Staffel; eine Stufe je Aufruf, weil
# die Zeilenzahl je Stufe wechselt, `docs/plan/referenz-workload.md` S2 und S4), LASTTEST_PHASEN="export import" (je Server nacheinander: `export` und
# `import` mit allen Sitzungen gemeinsam, `punkt-export` und `punkt-import` die Punktabfrage einer Sitzung, waehrend die uebrigen streamen oder
# laden; `punkt-import` nur in Aufbau C), LASTTEST_ANFRAGEN=1000 (Anfragen je Lauf der Punktabfrage). Aufbau A kennt nur eine Sitzung.
#
# Ausgabe: je Stufe eine Zeile `messung: ...` mit Datum, Aufbau, Senke, Richtung, Zeilen, Median, Minimum und Maximum der
# Millisekunden, Zeilen/s (Median), Bytes je Zeile (Zaehler der Netzwerkschnittstelle des Clients, mit IP- und TCP-Kopf),
# Streuung (ok oder zu-gross: Maximum mehr als das Doppelte des Minimums, dann ist die Stufe nicht belegt) und Last und
# Swap des Hosts vor und nach der Stufe. Der Kopf nennt Host, Docker, Commit, Image-IDs und Limits. Ein Lauf mit hoher Last
# oder wachsendem Swap des Hosts wird in jeder Messzeile (Aufbau A, B und C) vermerkt, nicht verworfen und wiederholt (Grenze, siehe Abschnitt 5 des
# Workload-Dokuments). Exit 0 nur, wenn jede Stufe vollstaendig und ohne Fehler lief; die Streuung ist kein Fehler.
set -u

zeilen="${LASTTEST_ZEILEN:-100000}"
wdh="${LASTTEST_WDH:-5}"
seed="${LASTTEST_SEED:-42}"
cpus="${LASTTEST_CPUS:-2}"
speicher="${LASTTEST_MEMORY:-4g}"
client_speicher="${LASTTEST_CLIENT_MEMORY:-2g}"
xmx="${LASTTEST_XMX:-256m}"
aufbauten="${LASTTEST_AUFBAUTEN:-A B C}"
senken="${LASTTEST_SENKEN:-mit ohne}"
min_frei="${LASTTEST_MIN_FREI_MB:-4096}"
last_max="${LASTTEST_LAST_MAX:-12}"
swap_toleranz="${LASTTEST_SWAP_TOLERANZ_MB:-16}"
sitzungen="${LASTTEST_SITZUNGEN:-1}"
phasen="${LASTTEST_PHASEN:-export import}"
anfragen="${LASTTEST_ANFRAGEN:-1000}"
proc="${LASTTEST_PROC:-/proc}" # nur der Selbsttest (tools/lasttest-selbsttest.sh) setzt es: eine Nachbildung von meminfo und loadavg
statistik="$(dirname "$0")/lasttest-statistik.sh"
klasse="io.github.pt9912.tdswire.testserver"

# Zahlenwerte vor dem Bau pruefen (Exit 2): ganze Zahlen ohne fuehrende Nullen, hoechstens 9 Stellen (Seed 18, mit Minus erlaubt),
# ZEILEN und WDH mindestens 1.
for angabe in "LASTTEST_ZEILEN=$zeilen" "LASTTEST_WDH=$wdh" "LASTTEST_CPUS=$cpus" "LASTTEST_MIN_FREI_MB=$min_frei" \
	"LASTTEST_LAST_MAX=$last_max" "LASTTEST_SWAP_TOLERANZ_MB=$swap_toleranz" "LASTTEST_SITZUNGEN=$sitzungen" "LASTTEST_ANFRAGEN=$anfragen"; do
	[[ ${angabe#*=} =~ ^(0|[1-9][0-9]{0,8})$ ]] || { echo "lasttest: ${angabe%%=*} muss eine nichtnegative ganze Zahl ohne fuehrende Nullen mit hoechstens 9 Stellen sein, nicht '${angabe#*=}'" >&2; exit 2; }
done
[[ $seed =~ ^-?(0|[1-9][0-9]{0,17})$ ]] || { echo "lasttest: LASTTEST_SEED muss eine ganze Zahl sein, nicht '$seed'" >&2; exit 2; }
{ [ "$zeilen" -ge 1 ] && [ "$wdh" -ge 1 ] && [ "$sitzungen" -ge 1 ] && [ "$anfragen" -ge 1 ]; } || { echo "lasttest: LASTTEST_ZEILEN, LASTTEST_WDH, LASTTEST_SITZUNGEN und LASTTEST_ANFRAGEN muessen mindestens 1 sein" >&2; exit 2; }
for phase in $phasen; do
	case "$phase" in export | import | punkt-export | punkt-import) ;; *) echo "lasttest: LASTTEST_PHASEN kennt export, import, punkt-export, punkt-import, nicht '$phase'" >&2; exit 2 ;; esac
done

lauf="tdswire-lt-$$"
netz="$lauf-netz"
server="$lauf-server"
server_image="tdswire-lasttestserver:$lauf"
client_image="tdswire-lasttestclient:$lauf"
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
last1() { awk '{print $1}' "$proc/loadavg"; }
last_ganz() { awk '{print int($1)}' "$proc/loadavg"; }
zustand() { echo "last $(awk '{print $1"/"$2"/"$3}' "$proc/loadavg") swap_mb $(swap_mb) frei_mb $(frei_mb)"; }

# Vor jedem Container: genug Speicher auf dem Host, sonst Abbruch (Exit 3).
speicherwacht() {
	local frei
	frei="$(frei_mb)"
	if [ "$frei" -lt "$min_frei" ]; then
		echo "lasttest: ABBRUCH - verfuegbarer Speicher des Hosts $frei MiB unter $min_frei MiB" >&2
		exit 3
	fi
}


# vermerke <Zustand vorher>: " last-hoch" und " swap-gewachsen" fuer jede Messzeile (Stufen von B, C und Aufbau A).
vermerke() {
	local v=""
	[ "$(last_ganz)" -le "$last_max" ] || v=" last-hoch"
	[ "$(swap_mb)" -le $(($(echo "$1" | awk '{print $4}') + swap_toleranz)) ] || v="$v swap-gewachsen"
	echo "$v"
}

# statfeld <Ausgabe von lasttest-statistik.sh> <Name>: der Wert hinter <Name>=
statfeld() { echo "$1" | tr ' ' '\n' | sed -n "s/^$2=//p"; }

# Eine Stufe auswerten: stufe <Datei> <Muster> <Aufbau> <Senke> <Richtung> <Byte-Feldname> <Zustand vorher>
# Zeilen/s sind die Zeilen aller Sitzungen durch die Wanduhr des Laufs (`ms` reicht von der Freigabe bis zur letzten Sitzung).
stufe() {
	local datei="$1" muster="$2" aufbau="$3" senke="$4" richtung="$5" byte_feld="$6" vorher="$7"
	local ms zeilen_je_s bytes st nachher median min max streuung vermerk erste ersteFeld=""
	ms="$(grep -E "$muster" "$datei" | awk '{for (i = 1; i <= NF; i++) if ($i == "lauf" && $(i + 1) >= 1) { for (j = i; j <= NF; j++) if ($j == "ms") print $(j + 1) }}')"
	[ -n "$ms" ] || { echo "lasttest: FEHLER $aufbau $senke $richtung: keine Messläufe" >&2; fehler=1; return; }
	st="$(printf '%s\n' "$ms" | bash "$statistik")" || { fehler=1; return; }
	median="${st#*median=}"; median="${median%% *}"
	min="${st#*min=}"; min="${min%% *}"
	max="${st#*max=}"; max="${max%% *}"
	streuung="${st##*streuung=}"
	[ "$(printf '%s\n' "$ms" | wc -l)" -eq "$wdh" ] || { echo "lasttest: FEHLER $aufbau $senke $richtung: $(printf '%s\n' "$ms" | wc -l) statt $wdh Läufe" >&2; fehler=1; }
	zeilen_je_s=$((median > 0 ? zeilen * sitzungen * 1000 / median : 0))
	bytes="-"
	if [ -n "$byte_feld" ]; then
		bytes="$(grep -E "$muster" "$datei" | awk -v b="$byte_feld" '{for (i = 1; i <= NF; i++) if ($i == "lauf" && $(i + 1) >= 1) for (j = i; j <= NF; j++) if ($j == b) print $(j + 1)}' | sort -n | awk -v z="$((zeilen * sitzungen))" '{a[NR] = $1} END {if (NR) print int(a[int((NR + 1) / 2)] / z)}')"
	fi
	if [ "$richtung" = export ]; then
		erste="$(grep -E "$muster" "$datei" | awk '{for (i = 1; i <= NF; i++) if ($i == "lauf" && $(i + 1) >= 1) { for (j = i; j <= NF; j++) if ($j == "erste_zeile_ms") print $(j + 1) }}' | bash "$statistik")" \
			|| { echo "lasttest: FEHLER $aufbau $senke $richtung: keine Zeit der ersten Zeile" >&2; fehler=1; return; }
		ersteFeld=" erste_zeile_ms_median $(statfeld "$erste" median) erste_zeile_ms_min $(statfeld "$erste" min) erste_zeile_ms_max $(statfeld "$erste" max)"
	fi
	nachher="$(zustand)"
	vermerk="$(vermerke "$vorher")"
	echo "messung: $(date -u +%Y-%m-%dT%H:%M:%SZ) aufbau $aufbau senke $senke richtung $richtung zeilen $zeilen sitzungen $sitzungen wdh $wdh median_ms $median min_ms $min max_ms $max zeilen_je_s $zeilen_je_s bytes_je_zeile $bytes streuung $streuung$ersteFeld vorher [$vorher] nachher [$nachher]$vermerk"
}

# Die Punktabfrage auswerten: punktstufe <Datei> <Aufbau> <Senke> <Phase> <Zustand vorher>. Je Perzentil Median, Minimum und Maximum
# der Perzentile der Laeufe (Mikrosekunden); die Streuung steht fuer p95.
punktstufe() {
	local datei="$1" aufbau="$2" senke="$3" phase="$4" vorher="$5" feld zeile="" st p95streuung="" anzahl
	for feld in p50_us p95_us p99_us; do
		st="$(grep -E '^lasttest: punkt lauf' "$datei" | awk -v b="$feld" '{for (i = 1; i <= NF; i++) if ($i == "lauf" && $(i + 1) >= 1) for (j = i; j <= NF; j++) if ($j == b) print $(j + 1)}' | bash "$statistik")" \
			|| { echo "lasttest: FEHLER $aufbau $senke $phase: keine Läufe der Punktabfrage" >&2; fehler=1; return; }
		zeile="$zeile ${feld}_median $(statfeld "$st" median) ${feld}_min $(statfeld "$st" min) ${feld}_max $(statfeld "$st" max)"
		[ "$feld" != p95_us ] || p95streuung="$(statfeld "$st" streuung)"
	done
	anzahl="$(grep -E '^lasttest: punkt lauf' "$datei" | awk '{for (i = 1; i <= NF; i++) if ($i == "lauf" && $(i + 1) >= 1) print 1}' | wc -l)"
	[ "$anzahl" -eq "$wdh" ] || { echo "lasttest: FEHLER $aufbau $senke $phase: $anzahl statt $wdh Läufe der Punktabfrage" >&2; fehler=1; }
	echo "messung: $(date -u +%Y-%m-%dT%H:%M:%SZ) aufbau $aufbau senke $senke richtung $phase zeilen $zeilen sitzungen $sitzungen wdh $wdh anfragen $anfragen$zeile streuung_p95 $p95streuung vorher [$vorher] nachher [$(zustand)]$(vermerke "$vorher")"
}

# Dauer je Schicht aus dem Log des Servers (Senke mit, Ebene DEBUG): spanstufe <Logdatei> <Aufbau> <Phase>. Je Span (`tds.query`,
# `backend.execute`) und Anfrageart (`request_kind` des `tds.query` mit derselben `request_id`; ein `backend.execute` ohne
# zugehoeriges `tds.query` zaehlt nicht) Anzahl, Median, Minimum und Maximum von `duration_ms` (ganze Millisekunden, abgerundet).
spanstufe() {
	local datei="$1" aufbau="$2" phase="$3" span art werte st
	awk -F'"' 'function feld(zeile, name,   m) { if (match(zeile, "\"" name "\":\"?[^,}\"]*")) { m = substr(zeile, RSTART, RLENGTH); sub(/^[^:]*:"?/, "", m); return m } return "" }
		/"event":"span_ended"/ { span = feld($0, "span"); id = feld($0, "request_id"); ms = feld($0, "duration_ms")
			if (span == "tds.query") art[id] = feld($0, "request_kind")
			else if (span == "backend.execute") dauer[id] = ms
			if (span == "tds.query") q[id] = ms }
		END { for (id in q) print "tds.query", art[id], q[id]; for (id in dauer) if (id in art) print "backend.execute", art[id], dauer[id] }' "$datei" >"$scratch/spans"
	for span in tds.query backend.execute; do
		for art in $(awk -v s="$span" '$1 == s {print $2}' "$scratch/spans" | sort -u); do
			werte="$(awk -v s="$span" -v a="$art" '$1 == s && $2 == a {print $3}' "$scratch/spans")"
			st="$(printf '%s\n' "$werte" | bash "$statistik")" || continue
			echo "messung-span: aufbau $aufbau senke mit phase $phase span $span art $art ${st% streuung=*}"
		done
	done
}

limits=(--cpus "$cpus" --memory "$speicher" --memory-swap "$speicher")
client_limits=(--cpus "$cpus" --memory "$client_speicher" --memory-swap "$client_speicher")
umgebung=(-e "LASTTEST_ZEILEN=$zeilen" -e "LASTTEST_WDH=$wdh" -e "LASTTEST_SEED=$seed" -e "LASTTEST_SITZUNGEN=$sitzungen" -e "LASTTEST_ANFRAGEN=$anfragen")

start=$(date +%s)
speicherwacht
echo "lasttest: Kopf $(date -u +%Y-%m-%dT%H:%M:%SZ) host kerne $(nproc) ram_mb $(awk '/^MemTotal:/ {print int($2 / 1024)}' "$proc/meminfo") kernel $(uname -r) docker $(docker version --format '{{.Server.Version}}' 2>/dev/null) commit $(git rev-parse --short HEAD 2>/dev/null || echo unbekannt)$([ -n "$(git status --porcelain 2>/dev/null)" ] && echo "+geaendert")"
echo "lasttest: Kopf zeilen $zeilen wdh $wdh (nach einem Warmlauf) seed $seed aufbauten [$aufbauten] senken [$senken] server cpus $cpus memory $speicher memory-swap $speicher xmx $xmx client cpus $cpus memory $client_speicher"
echo "lasttest: Kopf zustand vorher: $(zustand)"

# shellcheck disable=SC2086
docker build ${BUILDER_FLAGS:-} --load --target lasttestserver -t "$server_image" . || exit 1
# shellcheck disable=SC2086
docker build ${BUILDER_FLAGS:-} --load --target lasttest -t "$client_image" referenztest-dotnet || exit 1
gebaut=$(date +%s)
echo "lasttest: Kopf images server $(docker image inspect --format '{{.Id}}' "$server_image") client $(docker image inspect --format '{{.Id}}' "$client_image")"
docker network create --internal "$netz" >/dev/null || exit 1

for aufbau in $aufbauten; do
	case "$aufbau" in
	A)
		speicherwacht
		vorher="$(zustand)"
		docker run --rm --name "$server" "${limits[@]}" -e "LASTTEST_XMX=$xmx" "${umgebung[@]}" "$server_image" "$klasse.LasttestAlleinKt" >"$scratch/a" 2>&1 || { cat "$scratch/a"; echo "lasttest: FEHLER Aufbau A" >&2; fehler=1; continue; }
		grep '^lasttest: a ' "$scratch/a" | head -1
		for backend in erzeuger simulation; do
			ms="$(grep "^lasttest: a $backend lauf " "$scratch/a" | awk '$5 >= 1 {print $9}')"
			st="$(printf '%s\n' "$ms" | bash "$statistik")" || { fehler=1; continue; }
			median="${st#*median=}"; median="${median%% *}"
			echo "messung: $(date -u +%Y-%m-%dT%H:%M:%SZ) aufbau A backend $backend senke - richtung export zeilen $zeilen wdh $wdh $(echo "$st" | sed 's/n=[0-9]* //; s/median=/median_ms /; s/min=/min_ms /; s/max=/max_ms /; s/streuung=/streuung /') zeilen_je_s $((median > 0 ? zeilen * 1000 / median : 0)) bytes_je_zeile - vorher [$vorher] nachher [$(zustand)]$(vermerke "$vorher")"
		done
		ms="$(grep "^lasttest: a simulation-import lauf " "$scratch/a" | awk '$5 >= 1 {print $9}')"
		if st="$(printf '%s\n' "$ms" | bash "$statistik")"; then
			median="${st#*median=}"; median="${median%% *}"
			echo "messung: $(date -u +%Y-%m-%dT%H:%M:%SZ) aufbau A backend simulation senke - richtung import zeilen $zeilen wdh $wdh $(echo "$st" | sed 's/n=[0-9]* //; s/median=/median_ms /; s/min=/min_ms /; s/max=/max_ms /; s/streuung=/streuung /') zeilen_je_s $((median > 0 ? zeilen * 1000 / median : 0)) bytes_je_zeile - vorher [$vorher] nachher [$(zustand)]$(vermerke "$vorher")"
		else
			fehler=1
		fi
		;;
	B | C)
		for senke in $senken; do
			speicherwacht
			vorher="$(zustand)"
			docker run -d --name "$server" --network "$netz" "${limits[@]}" -e "LASTTEST_XMX=$xmx" -e "LASTTEST_AUFBAU=$aufbau" -e "LASTTEST_SENKE=$senke" \
				-e LOG_LEVEL=DEBUG "${umgebung[@]}" "$server_image" >/dev/null || { fehler=1; continue; }
			gestartet=0
			for _ in $(seq 1 120); do
				if docker logs "$server" 2>&1 | grep -q 'tdswire: gestartet, Port 1433'; then gestartet=1; break; fi
				[ "$(docker inspect --format '{{.State.Running}}' "$server")" = "true" ] || break
				sleep 1
			done
			if [ "$gestartet" -ne 1 ]; then
				docker logs "$server" 2>&1 | tail -20
				echo "lasttest: FEHLER Server Aufbau $aufbau Senke $senke nicht gestartet" >&2
				fehler=1
				docker rm -f "$server" >/dev/null 2>&1
				continue
			fi
			echo "$(docker logs "$server" 2>&1 | grep -m1 '^lasttest: server') swap_max $(docker exec "$server" cat /sys/fs/cgroup/memory.swap.max 2>/dev/null || echo unbekannt) memory_max $(docker exec "$server" cat /sys/fs/cgroup/memory.max 2>/dev/null || echo unbekannt)"
			for phase in $phasen; do
				[ "$phase" = punkt-import ] && [ "$aufbau" = B ] && continue
				speicherwacht
				vorher="$(zustand)"
				logvor="$(docker logs "$server" 2>&1 | wc -l)"
				docker run --rm --network "$netz" "${client_limits[@]}" -e "TDSWIRE_ZIEL=$server:1433" -e "LASTTEST_AUFBAU=$aufbau" -e "LASTTEST_PHASE=$phase" "${umgebung[@]}" \
					"$client_image" >"$scratch/client" 2>&1
				client_ok=$?
				grep -E '^ *lasttest:|Passed!|Failed!|Failed |Error Message' "$scratch/client" | sed 's/^ *//' | head -40
				[ "$client_ok" -eq 0 ] || { echo "lasttest: FEHLER Client Aufbau $aufbau Senke $senke Phase $phase (Exit $client_ok)" >&2; fehler=1; }
				tr -d '\r' <"$scratch/client" | grep -E '^ *lasttest: ' | sed 's/^ *//' >"$scratch/zeilen"
				docker logs "$server" 2>&1 | tail -n +$((logvor + 1)) >"$scratch/serverlog"
				echo "lasttest: server peak $(docker exec "$server" cat /sys/fs/cgroup/memory.peak 2>/dev/null || echo unbekannt) Bytes (cgroup, bis Ende Phase $phase, Aufbau $aufbau Senke $senke)"
				case "$phase" in
				export)
					stufe "$scratch/zeilen" '^lasttest: export lauf' "$aufbau" "$senke" export rx_bytes "$vorher"
					;;
				import)
					if [ "$aufbau" = C ]; then
						stufe "$scratch/zeilen" '^lasttest: import lauf' "$aufbau" "$senke" import tx_bytes "$vorher"
						gezaehlt="$(grep -c '^lasttest: import ' "$scratch/serverlog")"
						[ "$gezaehlt" -eq $((sitzungen * (wdh + 1))) ] || { echo "lasttest: FEHLER Aufbau C Senke $senke: das Backend meldet $gezaehlt von $((sitzungen * (wdh + 1))) vollständigen Importen" >&2; fehler=1; }
					else
						if grep -E 'import aufbau B abgewiesen' "$scratch/zeilen" | head -1 | grep -q .; then :; else echo "lasttest: FEHLER Aufbau B: Import nicht abgewiesen" >&2; fehler=1; fi
					fi
					;;
				*)
					punktstufe "$scratch/zeilen" "$aufbau" "$senke" "$phase" "$vorher"
					;;
				esac
				[ "$senke" != mit ] || spanstufe "$scratch/serverlog" "$aufbau" "$phase"
			done
			running="$(docker inspect --format '{{.State.Running}}' "$server")"
			[ "$running" = true ] || { echo "lasttest: FEHLER Server läuft nach Aufbau $aufbau Senke $senke nicht mehr" >&2; fehler=1; }
			docker rm -f "$server" >/dev/null 2>&1
		done
		;;
	*)
		echo "lasttest: unbekannter Aufbau '$aufbau'" >&2
		fehler=1
		;;
	esac
done

fertig=$(date +%s)
echo "lasttest: Kopf zustand nachher: $(zustand)"
echo "lasttest: Laufzeit $((fertig - start)) s, davon Bau $((gebaut - start)) s und Messung $((fertig - gebaut)) s"
[ "$fehler" -eq 0 ] || { echo "lasttest: ROT"; exit 1; }
echo "lasttest: gruen"
