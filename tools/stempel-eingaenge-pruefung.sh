#!/usr/bin/env bash
# tools/stempel-eingaenge-pruefung.sh - jede Eingangsquelle der Gates einer Klasse steht in der Eingangsliste ihres
# Stempels (ADR-0024 Entscheidung 10, TDS-QA-06). Aufrufer: tools/mk/stempel.mk; Selbsttest mit Mutationen:
# tools/stempel-eingaenge-selbsttest.sh.
#
# Zusage: Exit 0 nur, wenn
#  - jede COPY/ADD-Quelle der Stages test, compile, detekt, build-jar und smoke samt Vorgaenger-Stages (bei
#    `COPY --from=<stage>` zaehlen die Quellen der Quell-Stage fuer die holende Stage) von Dockerfile und der Stage test von
#    referenztest-dotnet/Dockerfile von einem Eintrag in tests.eingaenge ueberdeckt
#    ist und kompatibilitaetsserver die letzte Stage ist (`docker build .` baut die letzte),
#  - jede Quelle der Stages detekt-selbsttest, skript-selbsttest, kopplung-zahlen samt Vorgaengern, jede Markdown-Datei,
#    die d-check nach scan.roots und scan.ignore aus .d-check.yml liest, jedes vorhandene Ziel eines Markdown-Links in diesen
#    Dateien (Codebloecke und Symlinks ausgenommen), jede Make-Datei des Repos und jede Wurzel aus codepaths.roots von einem
#    Eintrag in werkzeug.eingaenge ueberdeckt ist,
#  - die festen Eingaenge (Dockerfile, .dockerignore, Makefile, repo.mk, builder.mk, Skript, Liste-Lib und beide Listen in
#    beiden Listen; .d-check.yml, d-check.mk (Image-Pin), .harness/baseline, harness, tools und referenztest-dotnet/Dockerfile
#    in werkzeug; die Fragmente der Test-Gates in tests) ueberdeckt sind und jede genannte Stage im Dockerfile steht.
# Ueberdeckt: ein Eintrag nennt denselben Pfad oder ein Elternverzeichnis (`.` deckt alles); eine Quelle mit Platzhalter
# ist ueberdeckt, wenn ein Eintrag ein Elternverzeichnis ihres Textes nennt (`tools/*.sh` unter `tools`).
# Befund statt stillem Ignorieren: `COPY .`, `..` in einer Quelle, ein Zyklus aus `COPY --from=`, Heredoc, JSON-Form, Variable (auch in FROM),
# Anfuehrungszeichen, URL, absolute Quelle, andere Optionen als --chown/--link/--from, `RUN --mount` ohne from= (bind,
# secret, ssh), eine andere letzte Stage als kompatibilitaetsserver und eine nicht erkannte Form von scan/codepaths in .d-check.yml.
# Stage-Namen gelten ohne Beachtung der Gross-/Kleinschreibung.
# Grenze: gezeigt wird "kein COPY, Link, Make-Fragment oder fester Eingang ausserhalb der Eintraege", nicht "kein versteckter
# Eingang"; was ein Gate zur Laufzeit holt (Netz, Basis-Image, Paketmanager) oder ausserhalb von Docker liest, prueft das
# nicht - Digest-Pins und Locks stehen als Eintraege da, weil sie Quellen sind. Links: nur Inline-Links `[text](ziel)`
# (keine Referenz-Definitionen, keine HTML-Anker); ein nicht vorhandenes Ziel braucht keinen Eintrag (sein Anlegen
# aendert nur ein ignore-refs-Ergebnis nicht). Die Zuordnung der Gates zu Stages und die festen Eingaenge stehen von Hand
# in diesem Skript. Die Syntax der Listen (ein literaler Pfad je Zeile) ist die von tools/stempel-liste.sh.
# Umgebung (nur fuer den Selbsttest): STEMPEL_DOCKERFILE, STEMPEL_DOTNET_DOCKERFILE, STEMPEL_DOTNET_KONTEXT,
# STEMPEL_DCHECK, STEMPEL_LISTEN.
# Voraussetzung: bash ab 4.3 (declare -A, mapfile, local -n), git, awk, sort, grep (GNU-Werkzeuge, Linux).
# Laufzeit: Kopf von tools/mk/stempel.mk.
set -u
export LC_ALL=C
SKRIPTVERZ=${BASH_SOURCE[0]}
case $SKRIPTVERZ in */*) SKRIPTVERZ=${SKRIPTVERZ%/*} ;; *) SKRIPTVERZ=. ;; esac
WARUM=
# shellcheck source=tools/stempel-liste.sh
. "$SKRIPTVERZ/stempel-liste.sh" || { echo "stempel-eingaenge: tools/stempel-liste.sh nicht lesbar" >&2; exit 1; }
cd "$(git rev-parse --show-toplevel 2>/dev/null)" || { echo "stempel-eingaenge: kein Git-Arbeitsbaum" >&2; exit 1; }

DOCKERFILE=${STEMPEL_DOCKERFILE:-Dockerfile}
DOTNET_DOCKERFILE=${STEMPEL_DOTNET_DOCKERFILE:-referenztest-dotnet/Dockerfile}
DOTNET_KONTEXT=${STEMPEL_DOTNET_KONTEXT:-referenztest-dotnet}
DCHECK=${STEMPEL_DCHECK:-.d-check.yml}
LISTEN=${STEMPEL_LISTEN:-tools/stempel}

# Zuordnung der Stages zu den Klassen: Make-Fragmente und Dockerfile nennen sie (tools/mk/test.mk, build.mk,
# detekt-selbsttest.mk, skript-selbsttest.mk, kopplung-zahlen.mk, referenztest-dotnet.mk).
STAGES_TESTS="test compile detekt build-jar smoke"
STAGES_TESTS_DOTNET="test"
STAGES_WERKZEUG="detekt-selbsttest skript-selbsttest kopplung-zahlen"
# Die letzte Stage des Dockerfile ist das Ziel von `docker build .`; `make build` baut `build-jar` (tools/mk/build.mk).
LETZTE_STAGE=kompatibilitaetsserver
FEST_BEIDE="Dockerfile .dockerignore Makefile repo.mk tools/mk/builder.mk tools/stempel.sh tools/stempel-liste.sh tools/stempel/werkzeug.eingaenge tools/stempel/tests.eingaenge"
FEST_WERKZEUG=".d-check.yml d-check.mk .harness/baseline harness tools referenztest-dotnet/Dockerfile"
FEST_TESTS="tools/mk/test.mk tools/mk/build.mk tools/mk/referenztest-dotnet.mk"

declare -A PARENT QUELLEN KENNT LETZTE VONREF
BEFUNDE=()
befund() { BEFUNDE+=("$*"); }

# lese_dockerfile <datei> <tag> <praefix>: PARENT/QUELLEN/KENNT je "<tag>:<stage>"; Befunde fuer nicht erkannte Formen.
lese_dockerfile() {
	local datei=$1 tag=$2 praefix=$3 art a b c
	[ -f "$datei" ] || { befund "Dockerfile fehlt: $datei"; return; }
	while IFS=$'\t' read -r art a b c; do
		case $art in
			S) KENNT["$tag:$a"]=1; PARENT["$tag:$a"]=$b ;;
			Q) QUELLEN["$tag:$a"]+="${praefix}${b}"$'\n' ;;
			F) VONREF["$tag:$a"]+="$b " ;;
			L) LETZTE["$tag"]=$a ;;
			B) befund "$datei: $a ($b)" ;;
		esac
	done < <(awk '
	function melde(was, grund) { printf "B\t%s\t%s\t\n", was, grund }
	function verarbeite(buf,    n, f, i, m, j, kw, img, name, von, src, flag) {
		n = split(buf, f, /[ \t]+/)
		kw = toupper(f[1])
		if (kw == "FROM") {
			i = 2
			while (i <= n && f[i] ~ /^--/) i++
			img = tolower(f[i])
			if (img ~ /\$/) melde(buf, "FROM mit Variable: Basis-Image oder Vorgaenger-Stage nicht aufloesbar")
			name = (toupper(f[i + 1]) == "AS") ? tolower(f[i + 2]) : sprintf("%d", stufen)
			stufen++
			cur = name
			printf "S\t%s\t%s\t\n", name, (img in bekannt) ? img : ""
			bekannt[name] = 1
			return
		}
		if (kw == "ONBUILD" && toupper(f[2]) ~ /^(COPY|ADD)$/) { melde(buf, "ONBUILD mit COPY oder ADD wird nicht ausgewertet"); return }
		if (kw == "RUN") {
			for (i = 2; i <= n && f[i] ~ /^--/; i++)
				if (f[i] ~ /^--mount=/ && f[i] !~ /from=/ && (f[i] ~ /type=(bind|secret|ssh)/ || f[i] !~ /type=/))
					melde(buf, "RUN --mount liest den Kontext oder das Geheimnis des Hosts")
			if (buf ~ /<</) melde(buf, "Heredoc wird nicht ausgewertet")
			return
		}
		if (kw != "COPY" && kw != "ADD") return
		if (cur == "") { melde(buf, kw " vor dem ersten FROM"); return }
		if (buf ~ /<</) { melde(buf, "Heredoc wird nicht ausgewertet"); return }
		von = 0
		for (i = 2; i <= n && f[i] ~ /^--/; i++) {
			if (f[i] ~ /^--from=/) { von = 1; vonname = tolower(substr(f[i], 8)) }
			else if (f[i] !~ /^--chown=/ && f[i] != "--link") { melde(buf, "nicht erkannte Option " f[i]); return }
		}
		if (f[i] ~ /^\[/) { melde(buf, "JSON-Form wird nicht ausgewertet"); return }
		if (n - i + 1 < 2) { melde(buf, "weniger als zwei Argumente"); return }
		if (von) { printf "F\t%s\t%s\t\n", cur, vonname; return }
		for (j = i; j < n; j++) {
			src = f[j]
			if (src ~ /\$/) { melde(buf, "Variable in der Quelle " src); continue }
			if (src ~ /["\047]/) { melde(buf, "Anfuehrungszeichen in der Quelle " src); continue }
			if (src ~ /:\/\// || src ~ /^git@/) { melde(buf, "URL als Quelle " src); continue }
			if (src ~ /^\//) { melde(buf, "absolute Quelle " src); continue }
			while (src ~ /^\.\//) sub(/^\.\//, "", src)
			sub(/\/+$/, "", src)
				if (src ~ /(^|\/)\.\.(\/|$)/) { melde(buf, "\047..\047 in der Quelle " src); continue }
			if (src == "" || src == ".") { melde(buf, "COPY . holt den ganzen Kontext (Befund, nicht erlaubt)"); continue }
			printf "Q\t%s\t%s\t\n", cur, src
		}
	}
	{
		t = $0
		sub(/^[ \t]+/, "", t)
		if (t ~ /^#/ || t == "") next
		if (t ~ /\\[ \t]*$/) { sub(/\\[ \t]*$/, "", t); buf = buf t " "; next }
		verarbeite(buf t)
		buf = ""
	}
	END { if (buf != "") verarbeite(buf); printf "L\t%s\t\t\n", cur }
	' "$datei")
}

# stage_quellen <tag> <stage> [tiefe]: Quellen der Stage, ihrer Vorgaenger und der Stages, aus denen sie mit
# `COPY --from=<stage>` Dateien holt (deren Eingaenge sind ihre), eine je Zeile.
stage_quellen() {
	local s=$2 tiefe=${3:-0} n=0 r
	[ "$tiefe" -lt 10 ] || { printf "!zyklus\n"; return 0; }
	while [ -n "$s" ] && [ -n "${KENNT["$1:$s"]:-}" ] && [ $n -lt 50 ]; do
		printf '%s' "${QUELLEN["$1:$s"]:-}"
		for r in ${VONREF["$1:$s"]:-}; do
			stage_quellen "$1" "$r" $((tiefe + 1))
		done
		s=${PARENT["$1:$s"]:-}
		n=$((n + 1))
	done
}

# lade_eintraege <klasse>: E_<klasse> aus der Liste (Syntax: tools/stempel-liste.sh, dieselbe wie in tools/stempel.sh).
lade_eintraege() {
	stempel_liste_lesen "$LISTEN/$1.eingaenge" "E_$1" || befund "$WARUM"
}

# ueberdeckt <klasse> <pfad>: ein Eintrag der Klasse nennt den Pfad, ein Elternverzeichnis oder `.`.
ueberdeckt() {
	local -n eintraege="E_$1"
	local p=$2 e
	for e in "${eintraege[@]}"; do
		[ "$e" = . ] && return 0
		[ "$p" = "$e" ] && return 0
		[[ $p == "$e"/* ]] && return 0
	done
	return 1
}

pruefe_quellen() { # <klasse> <herkunft> <quelle>...
	local klasse=$1 herkunft=$2 q
	shift 2
	for q in "$@"; do
		[ -n "$q" ] || continue
		if [ "$q" = "!zyklus" ]; then befund "COPY --from bildet einen Zyklus ($herkunft)"; continue; fi
		ueberdeckt "$klasse" "$q" || befund "$klasse.eingaenge: kein Eintrag deckt '$q' ($herkunft)"
	done
}

# Flow-Sequenz `[a, "b"]` aus einer Zeile von .d-check.yml in Zeilen zerlegen; andere Form: Befund.
d_check_lesen() { # <schluessel-ausgabe: scanroot|scanignore|coderoot>
	awk -v art="$1" '
	/^[A-Za-z]/ { top = $1; sub(/:.*/, "", top) }
	{ z = $0 }
	(top == "scan" && z ~ /^[ \t]+roots:/ && art == "scanroot") || (top == "scan" && z ~ /^[ \t]+ignore:/ && art == "scanignore") || (top == "codepaths" && z ~ /^[ \t]+roots:/ && art == "coderoot") {
		sub(/^[^:]*:[ \t]*/, "", z)
		if (z !~ /^\[.*\][ \t]*$/) { print "!" z; next }
		sub(/^\[/, "", z); sub(/\][ \t]*$/, "", z)
		n = split(z, t, ",")
		for (i = 1; i <= n; i++) { x = t[i]; gsub(/^[ \t"\047]+|[ \t"\047]+$/, "", x); if (x != "") print x }
		gefunden = 1
	}
	END { if (!gefunden) print "!" }
	' "$DCHECK"
}

ignoriert() { # <datei>: passt auf ein Muster aus scan.ignore (** wie *, ein fuehrendes **/ auch ohne Verzeichnis)
	local f=$1 pat p2
	for pat in "${SCAN_IGNORE[@]}"; do
		p2=${pat//\*\*/\*}
		# shellcheck disable=SC2053
		[[ $f == $p2 ]] && return 0
		# shellcheck disable=SC2053
		[[ $pat == \*\*/* && $f == ${p2#*/} ]] && return 0
	done
	return 1
}

# --- Eintraege und Dockerfiles lesen ---------------------------------------------------------------------------------
lade_eintraege werkzeug
lade_eintraege tests
lese_dockerfile "$DOCKERFILE" root ""
lese_dockerfile "$DOTNET_DOCKERFILE" dotnet "$DOTNET_KONTEXT/"

stage_pruefen() { # <tag> <stage>...
	local tag=$1 s
	shift
	for s in "$@"; do
		[ -n "${KENNT["$tag:$s"]:-}" ] || befund "Stage '$s' steht nicht im Dockerfile ($tag)"
	done
}
# shellcheck disable=SC2086
stage_pruefen root $STAGES_TESTS $STAGES_WERKZEUG
# shellcheck disable=SC2086
stage_pruefen dotnet $STAGES_TESTS_DOTNET
[ "${LETZTE[root]:-}" = "$LETZTE_STAGE" ] \
	|| befund "letzte Stage von $DOCKERFILE ist '${LETZTE[root]:-}', nicht '$LETZTE_STAGE' (docker build . baut die letzte)"

# --- Klasse tests ----------------------------------------------------------------------------------------------------
for s in $STAGES_TESTS; do
	mapfile -t q < <(stage_quellen root "$s")
	pruefe_quellen tests "Stage $s" "${q[@]+"${q[@]}"}"
done
for s in $STAGES_TESTS_DOTNET; do
	mapfile -t q < <(stage_quellen dotnet "$s")
	pruefe_quellen tests "Stage $s von $DOTNET_DOCKERFILE" "${q[@]+"${q[@]}"}"
done
# shellcheck disable=SC2086
pruefe_quellen tests "fester Eingang" $FEST_BEIDE $FEST_TESTS

# --- Klasse werkzeug -------------------------------------------------------------------------------------------------
for s in $STAGES_WERKZEUG; do
	mapfile -t q < <(stage_quellen root "$s")
	pruefe_quellen werkzeug "Stage $s" "${q[@]+"${q[@]}"}"
done
# shellcheck disable=SC2086
pruefe_quellen werkzeug "fester Eingang" $FEST_BEIDE $FEST_WERKZEUG

mapfile -t SCAN_ROOTS < <(d_check_lesen scanroot)
mapfile -t SCAN_IGNORE < <(d_check_lesen scanignore)
mapfile -t CODE_ROOTS < <(d_check_lesen coderoot)
for x in "${SCAN_ROOTS[@]}" "${SCAN_IGNORE[@]}" "${CODE_ROOTS[@]}"; do
	[ "$x" != "${x#!}" ] && befund "$DCHECK: scan.roots, scan.ignore oder codepaths.roots nicht in der erkannten Form (eine Zeile mit [..]): '${x#!}'"
done
mapfile -t MD < <(git ls-files --cached --others --exclude-standard -- '*.md' 2>/dev/null | sort -u)
[ ${#MD[@]} -gt 0 ] || befund "keine Markdown-Datei gefunden (git ls-files)"
SCAN_MD=()
for f in "${MD[@]}"; do
	[ -n "$f" ] || continue
	imbaum=0
	for r in "${SCAN_ROOTS[@]}"; do
		r=${r#./}
		[ "$r" = . ] && imbaum=1
		[[ $f == "$r"/* ]] && imbaum=1
	done
	[ $imbaum -eq 1 ] || continue
	ignoriert "$f" && continue
	ueberdeckt werkzeug "$f" || befund "werkzeug.eingaenge: kein Eintrag deckt '$f' (d-check liest sie nach scan.roots)"
	[ -L "$f" ] || SCAN_MD+=("$f")
done
# Ziele der Markdown-Links der gelesenen Dateien (ausserhalb von Codebloecken): auch ein Link auf eine Nicht-Markdown-Datei
# ist ein Eingang von docs-check.
if [ ${#SCAN_MD[@]} -gt 0 ]; then
	mapfile -t LINKS < <(awk '
	function norm(p,    n, a, i, m, r, out) {
		n = split(p, a, "/"); m = 0
		for (i = 1; i <= n; i++) {
			if (a[i] == "" || a[i] == ".") continue
			if (a[i] == "..") { if (m == 0) return "!"; m--; continue }
			r[++m] = a[i]
		}
		out = ""
		for (i = 1; i <= m; i++) out = out (i > 1 ? "/" : "") r[i]
		return (out == "") ? "." : out
	}
	FNR == 1 { fence = 0; dir = FILENAME; if (sub(/\/[^\/]*$/, "", dir) == 0) dir = "" }
	/^[ \t]*(```|~~~)/ { fence = !fence; next }
	fence { next }
	{
		z = $0
		while (match(z, /\[[^]]*\]\(<?[^) >]+/)) {
			t = substr(z, RSTART, RLENGTH)
			z = substr(z, RSTART + RLENGTH)
			sub(/^\[[^]]*\]\(<?/, "", t)
			if (t ~ /^([a-zA-Z][a-zA-Z0-9+.-]*:|#|\/\/)/) continue
			sub(/#.*/, "", t); sub(/\?.*/, "", t)
			if (t == "") continue
			p = (t ~ /^\//) ? substr(t, 2) : ((dir == "") ? t : dir "/" t)
			printf "%s\t%s\t%s\n", norm(p), FILENAME, t
		}
	}
	' "${SCAN_MD[@]}" | sort -u -t$'\t' -k1,1)
	for l in "${LINKS[@]}"; do
		IFS=$'\t' read -r ziel datei roh <<< "$l"
		if [ "$ziel" = '!' ]; then
			befund "Link '$roh' in $datei verlaesst das Repo"
		elif [ -e "$ziel" ] || [ -L "$ziel" ]; then
			ueberdeckt werkzeug "$ziel" || befund "werkzeug.eingaenge: kein Eintrag deckt '$ziel' (Link in $datei)"
		fi
	done
fi
# Make-Dateien: alle gehoeren zu docs-check (targets) oder zu den Rezepten der Gates.
mapfile -t MK < <(git ls-files --cached --others --exclude-standard -- '*.mk' 2>/dev/null | grep -v '^\.claude/worktrees/' | sort -u)
for f in "${MK[@]}"; do
	[ -n "$f" ] || continue
	ueberdeckt werkzeug "$f" || befund "werkzeug.eingaenge: kein Eintrag deckt '$f' (Make-Fragment)"
done
for r in "${CODE_ROOTS[@]}"; do
	[ "$r" = "${r#!}" ] || continue
	ueberdeckt werkzeug "$r" || befund "werkzeug.eingaenge: kein Eintrag deckt '$r' (codepaths.roots)"
done

if [ ${#BEFUNDE[@]} -gt 0 ]; then
	for b in "${BEFUNDE[@]}"; do printf 'stempel-eingaenge: %s\n' "$b" >&2; done
	printf 'stempel-eingaenge: %d Befund(e)\n' "${#BEFUNDE[@]}" >&2
	exit 1
fi
echo "stempel-eingaenge: ok - alle COPY-Quellen der Klassen tests und werkzeug, die Markdown-Dateien des Doku-Gates und die festen Eingaenge sind von Listeneintraegen ueberdeckt"
