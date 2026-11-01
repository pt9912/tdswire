#!/usr/bin/env bash
# tools/stempel.sh <pruefe|schreibe|einrichten> ... - lokale Stempel der zwei Gate-Klassen `werkzeug` und `tests`
# (ADR-0024, ADR-0023, TDS-QA-06). Aufrufer: noch keiner; Selbsttest: tools/stempel-selbsttest.sh.
# Voraussetzung: bash ab 4.3 (mapfile, declare -A, local -n), git und GNU-Werkzeuge (sha256sum, find -mmin, sort, grep -a).
#
#   pruefe <klasse>           Exit 0 NUR, wenn ein Vorfahr von HEAD (HEAD eingeschlossen) den Eingangs-Hash von HEAD hat
#                             und eine Note die Zeile `stempel <klasse> <hash> gruen` traegt. Jeder andere Fall (flacher
#                             Klon, Abfragefehler, Eingangspfade nicht sauber, kein Treffer, kein Vorfahr, fehlender
#                             Pfad, unlesbare Note, fehlendes git) endet mit Exit 1 und genau einer Grund-Zeile auf
#                             stderr; einen dritten Ausgang gibt es nicht. stdout traegt den Eingangs-Hash, sobald er
#                             berechnet ist, auch bei Exit 1.
#   schreibe <klasse> [hash]  haengt die Zeile per `git notes append` an die Note von HEAD in refs/notes/gates.
#                             Exit-Code-Vertrag:
#                             - Exit 1 = nichts geschrieben, Grund auf stderr: der Hash ist jetzt ein anderer als der
#                               uebergebene, die Eingangspfade sind nicht sauber, Klasse, Liste, Pfad oder Argument sind
#                               ungueltig. Ein zweites Argument, das nicht 64 Hex-Zeichen ist, auch das leere
#                               (`schreibe tests ""`), ist ein Fehler und kein Auslassen der Pruefung.
#                             - Exit 0 = die Zeile steht an HEAD - oder eine Warnung "nicht gesetzt" auf stderr nennt,
#                               dass die Sperre `<gemeinsames Git-Verzeichnis>/gates-stempel.lock` belegt war, das
#                               Schreiben scheiterte oder die Zeile danach fehlte. Der naechste Lauf laeuft dann; das
#                               Gate faellt nicht.
#                             Ein Aufrufer laesst die stderr-Zeile sichtbar, leitet nie aus dem Exit von `schreibe`
#                             ab, dass ein Stempel steht (das entscheidet allein `pruefe`), und fasst Exit 1 nicht mit
#                             einem blinden `|| true` zusammen: ob ein schmutziger Baum das Gate rot macht, ist Sache
#                             seiner Verdrahtung, ein ungueltiger Aufruf soll auffallen.
#                             Gleichzeitiges Schreiben: `git notes append` aus zwei Arbeitsbaeumen verlor in
#                             Einzelmessungen (Git 2.43.0, ein Host, vier Wiederholungen) 36 bis 47 von 60 Zeilen
#                             bzw. Notes, ohne zu scheitern. Der Verlust geht in Richtung Lauf (die Zeile fehlt), nicht
#                             in Richtung Ueberspringen; die Sperre serialisiert die Aufrufe dieses Skripts. Die Sperre
#                             wartet hoechstens zwei Sekunden; eine Sperre mit totem Prozess, ohne pid-Datei aelter als
#                             eine Minute oder aelter als zehn Minuten wird uebernommen. Grenze: uebernehmen zwei
#                             Wartende dieselbe tote Sperre gleichzeitig, koennen beide schreiben (Folge: hoechstens
#                             ein verlorener Stempel; nicht gefahren). Schreiber ausserhalb dieses Skripts sperrt sie nicht.
#   einrichten                setzt notes.rewriteRef fuer refs/notes/gates lokal im Klon, idempotent; `schreibe`
#                             ruft es, wenn der Schluessel fehlt. Notes werden nie gepusht oder geholt.
#
# Eingangs-Hash: SHA-256 ueber die Zeilen `<Pfad> <Git-Tree-ID>` aller Eintraege von tools/stempel/<klasse>.eingaenge
# (sortiert, ID aus `git rev-parse <Commit>:<Pfad>`). Ein Eintrag ist ein literaler Pfad ohne Platzhalter; ein Eintrag,
# der in HEAD fehlt, ist ein Fehler, kein leerer Wert. Sauber heisst: `git status --ignored` meldet in den Eintraegen
# nichts (versioniert, unversioniert, ignoriert); Aenderungen ausserhalb zaehlen nicht.
# Eingaenge: die Eintraege beider Listen; das Skript, tools/stempel-liste.sh und beide Listen stehen in der Liste beider
# Klassen (die Pruefung tools/stempel-eingaenge-pruefung.sh haelt das).
# Grenze: Die Note ist lokal und wird von jedem mit Schreibrecht auf das Git-Verzeichnis gesetzt; der Hash-Vergleich
# gegen den Vorfahren faengt eine Note am falschen Commit, nicht eine Note, die ein Mensch am richtigen setzt.
# Laufzeit des Selbsttests: Kopf von tools/stempel-selbsttest.sh.
set -u
export LC_ALL=C
export GIT_OPTIONAL_LOCKS=0
NOTES_REF=refs/notes/gates
WARUM=
HASH=
E=()
# shellcheck source=tools/stempel-liste.sh
SKRIPTVERZ=${BASH_SOURCE[0]}
case $SKRIPTVERZ in */*) SKRIPTVERZ=${SKRIPTVERZ%/*} ;; *) SKRIPTVERZ=. ;; esac
. "$SKRIPTVERZ/stempel-liste.sh" || { printf 'stempel: tools/stempel-liste.sh nicht lesbar\n' >&2; exit 1; }

grund() { printf 'stempel: %s\n' "$*" >&2; }

# klasse_pruefen <klasse>: nur die zwei Klassen der ADR-0024.
klasse_pruefen() {
	case ${1:-} in
		werkzeug | tests) return 0 ;;
		*) WARUM="unbekannte Klasse '${1:-}' (werkzeug oder tests)"; return 1 ;;
	esac
}

# lade_liste <klasse>: setzt E (sortiert, ohne Doppelte).
lade_liste() { stempel_liste_lesen "tools/stempel/$1.eingaenge" E; }

# eingangs_hash <commit>: setzt HASH aus E; ein Pfad, der im Commit fehlt, ist ein Fehler.
eingangs_hash() {
	local c=$1 e id zeilen=
	HASH=
	for e in "${E[@]}"; do
		if [ "$e" = . ]; then
			id=$(git rev-parse --verify -q "$c^{tree}" 2>/dev/null) || id=
		else
			id=$(git rev-parse --verify -q "$c:$e" 2>/dev/null) || id=
		fi
		[ -n "$id" ] || { WARUM="Eingangspfad fehlt oder git schlug fehl: $e"; return 1; }
		zeilen+="$e $id"$'\n'
	done
	HASH=$(printf '%s' "$zeilen" | sha256sum | cut -d' ' -f1)
	[[ $HASH =~ ^[0-9a-f]{64}$ ]] || { HASH=; WARUM="Hash nicht berechenbar (sha256sum fehlt?)"; return 1; }
}

# sauber: in den Eintraegen meldet git status (auch ignoriert, auch unversioniert) nichts.
sauber() {
	local out
	out=$(git status --porcelain --ignored --untracked-files=all -- "${E[@]}" 2>/dev/null) || { WARUM="git status schlug fehl"; return 1; }
	[ -z "$out" ] || { WARUM="Eingangspfade nicht sauber: $(printf '%s\n' "$out" | head -n 1)"; return 1; }
}

# note_traegt <commit> <zeile>: die Note des Commits enthaelt die Zeile exakt (alles andere in der Note zaehlt nicht).
note_traegt() {
	local obj
	obj=$(git notes --ref="$NOTES_REF" list "$1" 2>/dev/null) || return 1
	[ -n "$obj" ] || return 1
	git cat-file blob "$obj" 2>/dev/null | grep -a -x -F -q -- "$2"
}

# beleg_suchen <klasse> <hash>: ein Vorfahr von HEAD mit der Zeile in seiner Note und demselben Eingangs-Hash.
beleg_suchen() {
	local zeile="stempel $1 $2 gruen" liste note c
	liste=$(git notes --ref="$NOTES_REF" list 2>/dev/null) || { WARUM="Notes nicht lesbar"; return 1; }
	[ -n "$liste" ] || { WARUM="kein Beleg (keine Notes)"; return 1; }
	while read -r note c; do
		[ -n "$c" ] || continue
		git cat-file blob "$note" 2>/dev/null | grep -a -x -F -q -- "$zeile" || continue
		git merge-base --is-ancestor "$c" HEAD >/dev/null 2>&1 || continue
		eingangs_hash "$c" || continue
		[ "$HASH" = "$2" ] || continue
		return 0
	done <<< "$liste"
	WARUM="kein Beleg fuer $1 mit diesem Eingangs-Hash an einem Vorfahren"
	return 1
}

# sperre_holen: ein Verzeichnis im gemeinsamen Git-Verzeichnis, belegt hoechstens zwei Sekunden lang abgewartet (Kopf: Grund
# und Grenzen). Uebernommen wird eine Sperre mit totem Prozess, ohne pid-Datei aelter als eine Minute oder aelter als zehn
# Minuten (eine wiederverwendete pid haelt sie sonst dauerhaft).
sperre_holen() {
	local gd i p
	gd=$(cd "$(git rev-parse --git-common-dir 2>/dev/null)" 2>/dev/null && pwd) || { SPERRE="(Git-Verzeichnis)"; return 1; }
	SPERRE="$gd/gates-stempel.lock"
	for i in $(seq 1 20); do
		if mkdir "$SPERRE" 2>/dev/null; then
			printf '%s\n' "$$" > "$SPERRE/pid"
			trap 'rm -rf "$SPERRE"' EXIT
			return 0
		fi
		p=$(cat "$SPERRE/pid" 2>/dev/null)
		if { [ -n "$p" ] && ! kill -0 "$p" 2>/dev/null; } \
			|| { [ -z "$p" ] && [ -n "$(find "$SPERRE" -maxdepth 0 -mmin +1 2>/dev/null)" ]; } \
			|| [ -n "$(find "$SPERRE" -maxdepth 0 -mmin +10 2>/dev/null)" ]; then
			rm -rf "$SPERRE"
			continue
		fi
		sleep 0.1
	done
	return 1
}

cmd_pruefe() {
	local klasse=${1:-} hash
	klasse_pruefen "$klasse" || return 1
	[ "$(git rev-parse --is-shallow-repository 2>/dev/null)" = false ] || { WARUM="flacher Klon (oder Abfrage fehlgeschlagen)"; return 1; }
	git rev-parse --verify -q 'HEAD^{commit}' > /dev/null 2>&1 || { WARUM="HEAD nicht lesbar"; return 1; }
	lade_liste "$klasse" || return 1
	eingangs_hash HEAD || return 1
	hash=$HASH
	printf '%s\n' "$hash"
	sauber || return 1
	beleg_suchen "$klasse" "$hash" || return 1
	return 0
}

cmd_schreibe() {
	local klasse=${1:-} uebergeben=${2:-} head zeile aus
	klasse_pruefen "$klasse" || return 1
	if [ $# -ge 2 ]; then [[ $uebergeben =~ ^[0-9a-f]{64}$ ]] || { WARUM="uebergebener Hash ist kein SHA-256 in Hex (leer zaehlt nicht als ausgelassen)"; return 1; }; fi
	[ $# -le 2 ] || { WARUM="zu viele Argumente"; return 1; }
	head=$(git rev-parse --verify -q 'HEAD^{commit}' 2>/dev/null) || { WARUM="HEAD nicht lesbar"; return 1; }
	lade_liste "$klasse" || return 1
	eingangs_hash "$head" || return 1
	if [ $# -ge 2 ] && [ "$uebergeben" != "$HASH" ]; then
		WARUM="Eingangs-Hash hat sich seit dem Aufruf geaendert (uebergeben $uebergeben, jetzt $HASH)"
		return 1
	fi
	sauber || return 1
	zeile="stempel $klasse $HASH gruen"
	if ! git config --get-all notes.rewriteRef 2>/dev/null | grep -q -x -F -- "$NOTES_REF"; then
		cmd_einrichten || grund "schreibe $klasse: notes.rewriteRef nicht eingerichtet ($WARUM)"
	fi
	if ! sperre_holen; then
		grund "schreibe $klasse: Stempel nicht gesetzt, Sperre belegt ($SPERRE)"
		return 0
	fi
	if note_traegt "$head" "$zeile"; then
		grund "schreibe $klasse: Stempel liegt schon an $head"
		return 0
	fi
	if ! aus=$(git notes --ref="$NOTES_REF" append -m "$zeile" "$head" 2>&1); then
		grund "schreibe $klasse: Stempel nicht gesetzt, Schreiben schlug fehl: $(printf '%s' "$aus" | head -n 1)"
		return 0
	fi
	note_traegt "$head" "$zeile" || { grund "schreibe $klasse: Stempel nicht gesetzt, die Zeile fehlt nach dem Schreiben"; return 0; }
	return 0
}

cmd_einrichten() {
	if git config --get-all notes.rewriteRef 2>/dev/null | grep -q -x -F -- "$NOTES_REF"; then
		return 0
	fi
	git config --local --add notes.rewriteRef "$NOTES_REF" > /dev/null 2>&1 || { WARUM="git config schlug fehl"; return 1; }
	git config --get-all notes.rewriteRef 2>/dev/null | grep -q -x -F -- "$NOTES_REF" || { WARUM="notes.rewriteRef nach dem Setzen nicht lesbar"; return 1; }
	return 0
}

main() {
	local befehl=${1:-}
	[ $# -gt 0 ] && shift
	case $befehl in
		pruefe | schreibe | einrichten) ;;
		*) WARUM="Aufruf: stempel.sh pruefe <klasse> | schreibe <klasse> [hash] | einrichten"; return 1 ;;
	esac
	local top
	top=$(git rev-parse --show-toplevel 2>/dev/null) || { WARUM="kein Git-Arbeitsbaum oder git fehlt"; return 1; }
	cd "$top" || { WARUM="Wurzel des Arbeitsbaums nicht erreichbar"; return 1; }
	"cmd_$befehl" "$@"
}

main "$@"
rc=$?
if [ "$rc" -ne 0 ]; then
	grund "${1:-} ${2:-}: ${WARUM:-unbekannter Fehler}"
	exit 1
fi
exit 0
