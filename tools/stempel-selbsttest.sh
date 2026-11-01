#!/usr/bin/env bash
# tools/stempel-selbsttest.sh - belegt jeden Fehlerpfad von tools/stempel.sh einzeln (ADR-0024 Entscheidung 4 und 5,
# Folgepflicht 1 und 3a, TDS-QA-06). Aufrufer: tools/mk/stempel.mk (Target stempel-selbsttest).
#
# Zusage: Auf Wegwerf-Repos im temporaeren Verzeichnis endet `pruefe` mit Exit 0 nur bei exakt passender Klasse, exaktem
# Hash und einer Note an einem Vorfahren von HEAD, dessen Eingangs-Hash derselbe ist; flacher Klon, schmutzige Eingangs-
# pfade (versioniert, unversioniert, ignoriert), geaenderte Datei in den Eingaengen, unlesbare oder fehlende Note,
# Abfragefehler, fehlender Pfad oder fehlendes git, kein Vorfahr und eine Note mit falschem Hash enden mit Exit 1 und einer
# Grund-Zeile. `schreibe` schreibt nichts bei geaendertem Hash, schmutzigen Pfaden oder ungueltigem Argument (auch einem
# leeren Hash); bei belegter Sperre, einem Schreibfehler von git und einer nach dem Schreiben fehlenden Zeile ist der
# Stempel ungesetzt, mit Warnung und Exit 0. Unter gleichzeitigem Schreiben aus zwei Arbeitsbaeumen scheitert keiner, bleibt
# keiner ungesetzt und geht kein gemeldeter Stempel verloren. `einrichten` ist idempotent. Amend, Rebase und Squash tragen
# die Note nur mit notes.rewriteRef, und auch dann entscheidet der Hash. Der Selbsttest beruehrt weder refs/notes/gates noch
# die Konfiguration des Repos, in dem er laeuft (am Ende verglichen); Git-Konfiguration und Identitaet kommen aus dem
# temporaeren Verzeichnis.
# Aufbau: Gruppen unabhaengiger Faelle laufen als Hintergrundprozesse, jede in eigenen Wegwerf-Repos; ausgegeben werden nur
# rote Faelle und eine Summe. Die Faelle der Datei `tools/stempel-abbild-selbsttest.sh` (Abbild des echten Arbeitsbaums mit den
# echten Listen) sind ein eigener Lauf.
# Host-Lauf mit bash und git (Setzung): die Host-Abhaengigkeiten des Repos sind bash, git und docker. Die Stage `basis`
# (gradle:8.14-jdk21) fuehrt git (am 2026-11-01 mit `ls /usr/bin` geprueft); ein Lauf in Docker ist nicht gebaut, seine Laufzeit
# nicht gemessen. Dazu kommen bash ab 4.3 und GNU-Werkzeuge (sha256sum, sed -i, find -mmin, touch -t, date +%s%N);
# getestet nur unter Linux.
# Messung zu gleichzeitigem Schreiben (Einzelmessung 2026-11-01, Git 2.43.0, ein Host): 60 `git notes append` aus zwei
# Arbeitsbaeumen auf dieselbe Note gaben 60-mal Exit 0, in der Note blieben 39 Zeilen. Daher die Sperre im Skript.
# Laufzeit: Einzelmessung 2026-11-01, Lastmittel und Dauer im Kopf von tools/mk/stempel.mk; kalt (leerer Dateicache) nicht
# gemessen, das braucht Root. Der Selbsttest nutzt keinen Docker-Cache.
set -u
export LC_ALL=C
cd "$(git rev-parse --show-toplevel 2>/dev/null)" || { echo "stempel-selbsttest: kein Git-Arbeitsbaum" >&2; exit 1; }
SKRIPT=$(cd "$(dirname "${STEMPEL_SKRIPT:-tools/stempel.sh}")" && pwd)/$(basename "${STEMPEL_SKRIPT:-tools/stempel.sh}")
[ -f "$SKRIPT" ] || { echo "stempel-selbsttest: $SKRIPT fehlt" >&2; exit 1; }
BASH_BIN=$(command -v bash)
REAL_GIT=$(command -v git)
ECHT=$(pwd)

schnappschuss() { # Zustand des Repos, in dem der Selbsttest laeuft: Notes-Refs und lokale Konfiguration
	{ git -C "$ECHT" for-each-ref refs/notes; git -C "$ECHT" config --local --list; } 2>&1 | sha256sum | cut -d' ' -f1
}
VORHER=$(schnappschuss)

T=$(mktemp -d "${TMPDIR:-/tmp}/stempel-selbsttest.XXXXXX") || exit 1
trap 'rm -rf "$T"' EXIT
unset GIT_DIR GIT_WORK_TREE GIT_INDEX_FILE GIT_OBJECT_DIRECTORY GIT_COMMON_DIR GIT_NOTES_REF GIT_NOTES_REWRITE_REF GIT_NOTES_REWRITE_MODE
unset GIT_ALTERNATE_OBJECT_DIRECTORIES GIT_NAMESPACE GIT_CEILING_DIRECTORIES GIT_DISCOVERY_ACROSS_FILESYSTEM
mkdir -p "$T/home"
printf '[init]\n\tdefaultBranch = main\n[advice]\n\tdetachedHead = false\n' > "$T/gitconfig"
export HOME="$T/home" GIT_CONFIG_GLOBAL="$T/gitconfig" GIT_CONFIG_NOSYSTEM=1 GIT_TERMINAL_PROMPT=0
export GIT_AUTHOR_NAME=Test GIT_AUTHOR_EMAIL=test@example.invalid GIT_COMMITTER_NAME=Test GIT_COMMITTER_EMAIL=test@example.invalid

LAEUFE=0
FEHLER=0
OUT=
ERR=
RC=
ERRF=$T/err
SENV=(STEMPEL_X=1)
rot() { printf 'ROT   %s\n' "$*"; FEHLER=$((FEHLER + 1)); }
g() { git -C "$T/$1" "${@:2}"; }
S() { # S <verz> <args>...: Skript im Repo-Verzeichnis ausfuehren, OUT/ERR/RC setzen
	local d=$1
	shift
	OUT=$(cd "$T/$d" && env "${SENV[@]}" "$BASH_BIN" "$SKRIPT" "$@" 2> "$ERRF" < /dev/null)
	RC=$?
	ERR=$(cat "$ERRF")
}
# erwarte <name> <verz> <rc> <muster|-> <args>...: Exit-Code, bei 1 genau eine Grund-Zeile, Muster darin
erwarte() {
	local name=$1 d=$2 rc=$3 muster=$4 zeilen
	shift 4
	S "$d" "$@"
	LAEUFE=$((LAEUFE + 1))
	zeilen=$(printf '%s' "$ERR" | grep -c '')
	if [ "$RC" != "$rc" ]; then
		rot "$name: Exit $RC statt $rc ($ERR)"
	elif [ "$rc" = 1 ] && [ "$zeilen" != 1 ]; then
		rot "$name: $zeilen Zeilen auf stderr statt einer: $ERR"
	elif [ "$rc" = 0 ] && [ "${1:-}" = pruefe ] && [ -n "$ERR" ]; then
		rot "$name: Exit 0, aber stderr nicht leer: $ERR"
	elif [ "$muster" != - ] && ! printf '%s' "$ERR" | grep -q -F -- "$muster"; then
		rot "$name: stderr nennt \"$muster\" nicht: $ERR"
	fi
}
ist() { # ist <name> <befehl>...: der Befehl endet mit 0
	local name=$1
	shift
	LAEUFE=$((LAEUFE + 1))
	"$@" || rot "$name"
}
gleich() { [ "$1" = "$2" ]; }
hashform() { [[ $1 =~ ^[0-9a-f]{64}$ ]]; }
leer() { [ -z "$1" ]; }

# --- Vorlage: ein Repo mit beiden Listen, dem Skript und Dateien in und ausserhalb der Eingaenge --------------------------
V="$T/vorlage"
mkdir -p "$V/tools/stempel" "$V/docs" "$V/src" "$V/other"
git -C "$V" init -q --template=
cp "$SKRIPT" "$V/tools/stempel.sh"
printf '# Fixture\ndocs\ntools\nMakefile\n' > "$V/tools/stempel/werkzeug.eingaenge"
printf 'src\nDockerfile\ntools/stempel.sh\ntools/stempel\n' > "$V/tools/stempel/tests.eingaenge"
printf 'a\n' > "$V/docs/a.md"
printf 'class A\n' > "$V/src/A.kt"
printf 'FROM scratch\n' > "$V/Dockerfile"
printf 'all:\n' > "$V/Makefile"
printf 'x\n' > "$V/other/x.txt"
printf 'y\n' > "$V/other/y.txt"
printf '*.ign\n' > "$V/.gitignore"
git -C "$V" add -A
git -C "$V" commit -q -m c1
# Ein Wegwerf-Repo kostet eine Kopie von rund vier Millisekunden (Einzelmessung 2026-11-01, 50 Kopien 189 ms; ein
# `git reset --hard` mit `git clean -fdx` brauchte 4,6 ms): die Zeit steckt in den Skriptaufrufen, nicht im Anlegen.
neu() { rm -rf "${T:?}/$1"; cp -a "$V" "$T/$1"; }
notiz() { # notiz <verz> <inhalt>: die Note an HEAD wird genau dieser Inhalt (kein stripspace)
	g "$1" notes --ref=refs/notes/gates add -f -C "$(printf '%s\n' "$2" | g "$1" hash-object -w --stdin)" HEAD 2> /dev/null
}
bearbeite() { # bearbeite <verz> <datei> <inhalt>: schreiben, committen
	mkdir -p "$(dirname "$T/$1/$2")"
	printf '%s\n' "$3" >> "$T/$1/$2"
	g "$1" add -A -- "$2"
	g "$1" commit -q -m "aendere $2"
}
gestempelt() { # gestempelt <verz>: frisches Repo mit beiden Stempeln an HEAD
	neu "$1"
	S "$1" schreibe tests
	S "$1" schreibe werkzeug
}
hash_von() { S "$1" pruefe "$2"; printf '%s' "$OUT"; }

# --- Gruppen ------------------------------------------------------------------------------------------------------------------
g_grund() {
	neu a
	erwarte "ohne Notes: Lauf" a 1 "keine Notes" pruefe tests
	ist "ohne Notes: stdout traegt den Hash" hashform "$OUT"
	local H=$OUT
	erwarte "schreibe tests" a 0 - schreibe tests
	erwarte "pruefe nach schreibe: Ueberspringen" a 0 - pruefe tests
	ist "pruefe: stdout ist derselbe Hash" gleich "$OUT" "$H"
	ist "Note traegt genau die Zeile" gleich "$(g a notes --ref=refs/notes/gates show HEAD)" "stempel tests $H gruen"
	erwarte "andere Klasse ohne Stempel: Lauf" a 1 "kein Beleg" pruefe werkzeug
	erwarte "schreibe werkzeug (zweite Zeile am selben Commit)" a 0 - schreibe werkzeug
	erwarte "pruefe werkzeug" a 0 - pruefe werkzeug
	erwarte "pruefe tests nach zweiter Zeile" a 0 - pruefe tests
	ist "zwei Zeilen in der Note" gleich "$(g a notes --ref=refs/notes/gates show HEAD | grep -c '^stempel ')" 2
	erwarte "schreibe zum zweiten Mal: nichts doppelt" a 0 "liegt schon" schreibe tests
	ist "weiterhin zwei Zeilen" gleich "$(g a notes --ref=refs/notes/gates show HEAD | grep -c '^stempel ')" 2
	# Aufrufvertrag
	erwarte "unbekannte Klasse" a 1 "unbekannte Klasse" pruefe fremd
	erwarte "schreibe ohne Klasse" a 1 "unbekannte Klasse" schreibe
	erwarte "unbekannter Befehl" a 1 "Aufruf" tue
	erwarte "kein Befehl" a 1 "Aufruf"
	erwarte "schreibe mit Hash ohne Hex" a 1 "kein SHA-256" schreibe tests XYZ
	neu m5
	erwarte "schreibe mit leerem Hash-Argument" m5 1 "kein SHA-256" schreibe tests ""
	ist "schreibe mit leerem Hash-Argument: keine Note" leer "$(g m5 notes --ref=refs/notes/gates list)"
	erwarte "schreibe mit drei Argumenten" m5 1 "zu viele Argumente" schreibe tests "$(hash_von m5 tests)" extra
	# Exaktheit: Klasse und Hash. Alle Fastfaelle stehen in einer Note; jeder allein waere ein Treffer, wenn seine
	# Pruefung (exakte Zeile, Klasse, Hash) fehlte.
	neu b
	H=$(hash_von b tests)
	local H2="${H%?}$([ "${H: -1}" = 0 ] && echo 1 || echo 0)"
	notiz b "$(printf '%s\n' \
		"stempel tests $H2 gruen" \
		"stempel tests $H gruen " \
		" stempel tests $H gruen" \
		"xstempel tests $H gruen" \
		"stempel tests $H gruen2" \
		"stempel tests $H rot" \
		"stempel werkzeug $H gruen" \
		"stempel tests2 $H gruen" \
		"# stempel tests $H gruen" \
		"stempel tests ${H:0:32} gruen")"
	erwarte "zehn Fastfaelle in einer Note gelten nicht" b 1 "kein Beleg" pruefe tests
	notiz b "vorher
stempel tests $H gruen
nachher"
	erwarte "Kontrolle: die Zeile steht zwischen anderen" b 0 - pruefe tests
	notiz b "stempel tests $H gruen

stempel tests $H gruen"
	erwarte "mehrere gleiche Zeilen aendern nichts" b 0 - pruefe tests
}

g_hash() {
	gestempelt c1
	local H0 W0
	H0=$(hash_von c1 tests)
	W0=$(hash_von c1 werkzeug)
	bearbeite c1 other/x.txt z
	erwarte "Datei ausserhalb geaendert: tests uebersprungen" c1 0 - pruefe tests
	erwarte "Datei ausserhalb geaendert: werkzeug uebersprungen" c1 0 - pruefe werkzeug
	bearbeite c1 docs/a.md z
	erwarte "docs geaendert: werkzeug laeuft" c1 1 "kein Beleg" pruefe werkzeug
	erwarte "docs geaendert: tests uebersprungen" c1 0 - pruefe tests
	ist "Hash werkzeug hat sich geaendert" test "$(hash_von c1 werkzeug)" != "$W0"
	bearbeite c1 src/A.kt z
	erwarte "src geaendert: tests laeuft" c1 1 "kein Beleg" pruefe tests
	ist "Hash tests hat sich geaendert" test "$(hash_von c1 tests)" != "$H0"
	gestempelt c2
	bearbeite c2 tools/stempel/tests.eingaenge "# Kommentar"
	erwarte "Liste geaendert: tests laeuft (Liste ist Eingang)" c2 1 "kein Beleg" pruefe tests
	gestempelt c3
	bearbeite c3 tools/stempel.sh "# Aenderung"
	erwarte "Skript geaendert: tests laeuft" c3 1 "kein Beleg" pruefe tests
	erwarte "Skript geaendert: werkzeug laeuft" c3 1 "kein Beleg" pruefe werkzeug
	# schmutziger Eingangsbaum
	gestempelt d1
	printf 'z\n' >> "$T/d1/src/A.kt"
	erwarte "src unstaged geaendert: tests laeuft" d1 1 "nicht sauber" pruefe tests
	ist "stdout traegt den Hash auch bei Exit 1" hashform "$OUT"
	erwarte "src unstaged geaendert: werkzeug uebersprungen" d1 0 - pruefe werkzeug
	g d1 add src/A.kt
	erwarte "src staged geaendert: tests laeuft" d1 1 "nicht sauber" pruefe tests
	g d1 reset -q --hard
	printf 'neu\n' > "$T/d1/src/Neu.kt"
	erwarte "unversionierte Datei in src" d1 1 "nicht sauber" pruefe tests
	rm "$T/d1/src/Neu.kt"
	printf 'ign\n' > "$T/d1/src/Z.ign"
	erwarte "ignorierte Datei in src" d1 1 "nicht sauber" pruefe tests
	rm "$T/d1/src/Z.ign"
	rm "$T/d1/src/A.kt"
	erwarte "geloeschte Datei in src" d1 1 "nicht sauber" pruefe tests
	g d1 checkout -q -- src/A.kt
	erwarte "Kontrolle: wieder sauber" d1 0 - pruefe tests
	printf 'z\n' >> "$T/d1/docs/a.md"
	erwarte "docs geaendert: werkzeug laeuft" d1 1 "nicht sauber" pruefe werkzeug
	erwarte "docs geaendert: tests uebersprungen" d1 0 - pruefe tests
	g d1 checkout -q -- docs/a.md
	printf 'z\n' >> "$T/d1/other/x.txt"
	erwarte "Aenderung ausserhalb: tests uebersprungen" d1 0 - pruefe tests
	neu d7
	printf 'z\n' >> "$T/d7/src/A.kt"
	erwarte "schreibe bei schmutzigem Baum bricht ab" d7 1 "nicht sauber" schreibe tests
	ist "schreibe bei schmutzigem Baum: keine Note" leer "$(g d7 notes --ref=refs/notes/gates list)"
	# Hash aendert sich waehrend des Laufs
	neu e
	H0=$(hash_von e tests)
	bearbeite e src/A.kt z
	erwarte "schreibe mit altem Hash bricht ab" e 1 "geaendert" schreibe tests "$H0"
	ist "schreibe mit altem Hash: keine Note" leer "$(g e notes --ref=refs/notes/gates list)"
	H0=$(hash_von e tests)
	erwarte "Kontrolle: schreibe mit dem jetzigen Hash" e 0 - schreibe tests "$H0"
	erwarte "Kontrolle: danach Ueberspringen" e 0 - pruefe tests
}

g_beleg() {
	neu f
	g f checkout -q -b zweig-a
	bearbeite f other/x.txt a
	S f schreibe tests
	g f checkout -q main
	g f checkout -q -b zweig-b
	bearbeite f other/y.txt b
	erwarte "Stempel auf einem Zweig, der kein Vorfahr ist" f 1 "kein Beleg" pruefe tests
	g f merge -q --no-edit zweig-a
	erwarte "Kontrolle: nach dem Merge ist der Commit ein Vorfahr" f 0 - pruefe tests
	neu f2
	local C1 H2 H
	C1=$(g f2 rev-parse HEAD)
	bearbeite f2 src/A.kt z
	H2=$(hash_von f2 tests)
	g f2 notes --ref=refs/notes/gates add -m "stempel tests $H2 gruen" "$C1"
	erwarte "Note am Vorfahren traegt den Hash von HEAD, nicht den eigenen" f2 1 "kein Beleg" pruefe tests
	g f2 notes --ref=refs/notes/gates add -f -m "stempel tests $H2 gruen" HEAD 2> /dev/null
	erwarte "Kontrolle: dieselbe Zeile an HEAD" f2 0 - pruefe tests
	# flacher Klon
	neu g
	bearbeite g other/x.txt z
	H=$(hash_von g tests)
	git clone -q --depth 1 "file://$T/g" "$T/g-flach" 2> /dev/null
	git clone -q --no-local "file://$T/g" "$T/g-voll" 2> /dev/null
	ist "Klon ist flach" gleich "$(g g-flach rev-parse --is-shallow-repository)" true
	g g-flach notes --ref=refs/notes/gates add -m "stempel tests $H gruen" HEAD
	g g-voll notes --ref=refs/notes/gates add -m "stempel tests $H gruen" HEAD
	erwarte "flacher Klon trotz passender Note" g-flach 1 "flacher Klon" pruefe tests
	erwarte "Kontrolle: voller Klon mit derselben Note" g-voll 0 - pruefe tests
	# unlesbare Note, fehlendes Objekt, Abfragefehler
	neu h
	H=$(hash_von h tests)
	local ID
	ID=$(printf '\000\377\376müll\000' | g h hash-object -w --stdin)
	g h notes --ref=refs/notes/gates add -f -C "$ID" HEAD 2> /dev/null
	erwarte "unlesbare Note (Binaerdaten)" h 1 "kein Beleg" pruefe tests
	ID=$(printf '\000\377\n%s\n\000\n' "stempel tests $H gruen" | g h hash-object -w --stdin)
	g h notes --ref=refs/notes/gates add -f -C "$ID" HEAD 2> /dev/null
	erwarte "Binaerdaten neben der gueltigen Zeile aendern nichts" h 0 - pruefe tests
	g h notes --ref=refs/notes/gates add -f -m "stempel tests $H gruen" 2> /dev/null
	ID=$(g h notes --ref=refs/notes/gates list HEAD)
	rm "$T/h/.git/objects/${ID:0:2}/${ID:2}"
	erwarte "Objekt der Note fehlt" h 1 "kein Beleg" pruefe tests
	neu i
	mkdir -p "$T/i/.git/refs/notes"
	printf '1111111111111111111111111111111111111111\n' > "$T/i/.git/refs/notes/gates"
	erwarte "Notes-Ref zeigt auf ein fehlendes Objekt (Abfragefehler)" i 1 "Notes nicht lesbar" pruefe tests
	neu j
	mkdir -p "$T/leer"
	SENV=("PATH=$T/leer")
	erwarte "git nicht im PATH" j 1 "git fehlt" pruefe tests
	SENV=(STEMPEL_X=1)
	mkdir -p "$T/nichtrepo" "$T/ohnehead"
	git -C "$T/ohnehead" init -q
	erwarte "kein Git-Arbeitsbaum" nichtrepo 1 "git fehlt" pruefe tests
	erwarte "Repo ohne Commit" ohnehead 1 "HEAD nicht lesbar" pruefe tests
	# Listen
	neu k
	printf '%s\n' 'src' 'fehlt/ganz' >> "$T/k/tools/stempel/tests.eingaenge"
	g k commit -q -a -m liste
	erwarte "Eingangspfad fehlt in HEAD" k 1 "Eingangspfad fehlt" pruefe tests
	neu l
	printf 'src/*\n' >> "$T/l/tools/stempel/tests.eingaenge"
	erwarte "Platzhalter in der Liste" l 1 "kein literaler Pfad" pruefe tests
	neu m
	rm "$T/m/tools/stempel/tests.eingaenge"
	erwarte "Liste fehlt" m 1 "Eingangsliste fehlt" pruefe tests
	printf '# nur Kommentar\n\n' > "$T/m/tools/stempel/tests.eingaenge"
	erwarte "Liste ohne Eintrag" m 1 "Eingangsliste leer" pruefe tests
	printf 'src\nDockerfile\ntools/stempel.sh\ntools/stempel\n../docs\n' > "$T/m/tools/stempel/tests.eingaenge"
	erwarte "'..' in einem Listeneintrag" m 1 "ohne '..'" pruefe tests
	neu m4
	printf 'src\nDockerfile\ntools/stempel.sh\ntools/stempel\nother' > "$T/m4/tools/stempel/tests.eingaenge"
	g m4 commit -q -a -m "Liste ohne Zeilenende am Ende"
	erwarte "Liste ohne Zeilenende: schreibe" m4 0 - schreibe tests
	bearbeite m4 other/x.txt z
	erwarte "Liste ohne Zeilenende: der letzte Eintrag zaehlt (Aenderung in other laeuft)" m4 1 "kein Beleg" pruefe tests
}

g_schreiben() {
	# einrichten
	neu n
	erwarte "einrichten" n 0 - einrichten
	ist "notes.rewriteRef gesetzt" gleich "$(g n config --get-all notes.rewriteRef)" refs/notes/gates
	local VOR
	VOR=$(cat "$T/n/.git/config")
	erwarte "einrichten zum zweiten Mal" n 0 - einrichten
	ist "zweiter Aufruf aendert die Konfiguration nicht" gleich "$(cat "$T/n/.git/config")" "$VOR"
	neu n2
	g n2 config notes.rewriteRef refs/notes/commits
	S n2 einrichten
	ist "fremder Wert bleibt, unserer kommt dazu" gleich "$(g n2 config --get-all notes.rewriteRef | sort | tr '\n' ' ')" "refs/notes/commits refs/notes/gates "
	neu n3
	S n3 schreibe tests
	ist "schreibe richtet ein, wenn der Schluessel fehlt" gleich "$(g n3 config --get-all notes.rewriteRef)" refs/notes/gates
	# Schreibfehler: git haelt die Sperre des Refs
	neu sf1
	mkdir -p "$T/sf1/.git/refs/notes"
	: > "$T/sf1/.git/refs/notes/gates.lock"
	erwarte "Schreibfehler von git: Warnung, Exit 0" sf1 0 "Schreiben schlug fehl" schreibe tests
	rm "$T/sf1/.git/refs/notes/gates.lock"
	erwarte "Schreibfehler: danach Lauf" sf1 1 "kein Beleg" pruefe tests
	# Nachlesen: git meldet Erfolg, schreibt aber nichts
	neu sf2
	mkdir -p "$T/fake"
	printf '#!/usr/bin/env bash\nfor a in "$@"; do [ "$a" = append ] && exit 0; done\nexec "%s" "$@"\n' "$REAL_GIT" > "$T/fake/git"
	chmod +x "$T/fake/git"
	SENV=("PATH=$T/fake:$PATH")
	erwarte "Zeile fehlt nach dem Schreiben: Warnung, Exit 0" sf2 0 "Zeile fehlt nach dem Schreiben" schreibe tests
	SENV=(STEMPEL_X=1)
	erwarte "Zeile fehlt nach dem Schreiben: danach Lauf" sf2 1 "kein Beleg" pruefe tests
}

g_sperre() {
	neu o
	g o worktree add -q --detach "$T/o2"
	local SP="$T/o/.git/gates-stempel.lock" ZEIT0 WARTE
	mkdir "$SP"
	printf '%s\n' "$$" > "$SP/pid"
	SENV=(STEMPEL_SPERRE_VERSUCHE=5)
	ZEIT0=$(date +%s%N)
	erwarte "Sperre belegt: Stempel ungesetzt, Exit 0" o2 0 "Sperre belegt" schreibe tests
	WARTE=$((($(date +%s%N) - ZEIT0) / 1000000))
	SENV=(STEMPEL_X=1)
	ist "Sperre belegt: wartet die fuenf Versuche ab ($WARTE ms)" test "$WARTE" -ge 400 -a "$WARTE" -lt 10000
	ist "Standard der Sperre: 20 Versuche (im Skript)" grep -q 'STEMPEL_SPERRE_VERSUCHE:-20' "$SKRIPT"
	ist "Standard der Sperre: 0,1 s je Versuch (im Skript)" grep -q 'sleep 0.1$' "$SKRIPT"
	erwarte "Sperre belegt: danach Lauf" o2 1 "kein Beleg" pruefe tests
	ist "Sperre belegt: keine Note" leer "$(g o notes --ref=refs/notes/gates list)"
	rm -rf "$SP"
	erwarte "Sperre frei: schreibe aus dem zweiten Arbeitsbaum" o2 0 - schreibe tests
	erwarte "Note aus Arbeitsbaum 2 gilt im Hauptbaum" o 0 - pruefe tests
	ist "Sperre nach dem Lauf entfernt" test ! -e "$SP"
	( : ) & local TOT=$!
	wait "$TOT"
	mkdir "$SP"
	printf '%s\n' "$TOT" > "$SP/pid"
	erwarte "Sperre eines beendeten Prozesses wird uebernommen" o2 0 - schreibe werkzeug
	erwarte "  ... und der Stempel steht" o 0 - pruefe werkzeug
	neu o3
	g o3 worktree add -q --detach "$T/o4"
	mkdir "$T/o3/.git/gates-stempel.lock"
	touch -t 200001010000 "$T/o3/.git/gates-stempel.lock"
	erwarte "Alte Sperre ohne pid wird uebernommen" o4 0 - schreibe tests
	erwarte "  ... und der Stempel steht" o3 0 - pruefe tests
	neu o5
	g o5 worktree add -q --detach "$T/o6"
	mkdir "$T/o5/.git/gates-stempel.lock"
	printf '%s\n' "$$" > "$T/o5/.git/gates-stempel.lock/pid"
	touch -t 200001010000 "$T/o5/.git/gates-stempel.lock"
	erwarte "Alte Sperre mit lebender pid (wiederverwendet) wird uebernommen" o6 0 - schreibe tests
	erwarte "  ... und der Stempel steht" o5 0 - pruefe tests
}

# Zwei Arbeitsbaeume schreiben je zehn Stempel an eigene Commits; jeder als gesetzt gemeldete Stempel steht am Ende in der
# Note. Der Fall zeigt die Sperre nur wahrscheinlich (ohne Sperre: Einzelmessung 4 von 6 Laeufen rot bei je 40 Stempeln);
# die Sperre selbst belegt g_sperre deterministisch.
g_gleichzeitig() {
	neu p
	g p worktree add -q --detach "$T/p2"
	stress() { # stress <verz> <kennung>
		local d=$1 k=$2 n c
		for n in $(seq 1 10); do
			printf '%s %s\n' "$k" "$n" >> "$T/$d/other/x.txt"
			g "$d" add -A
			g "$d" commit -q -m "$k $n"
			c=$(g "$d" rev-parse HEAD)
			S "$d" schreibe tests
			if [ "$RC" != 0 ]; then echo "FEHLER $c"; elif printf '%s' "$ERR" | grep -q "nicht gesetzt"; then echo "UNGESETZT $c"; else echo "GESETZT $c"; fi
		done > "$T/stress-$k"
	}
	( ERRF="$T/err-a"; stress p a ) &
	local P1=$!
	( ERRF="$T/err-b"; stress p2 b ) &
	local P2=$!
	wait "$P1" "$P2"
	local GESETZT UNGESETZT FEHLSCHLAG VERLOREN=0 art c
	GESETZT=$(cat "$T"/stress-a "$T"/stress-b | grep -c '^GESETZT')
	UNGESETZT=$(cat "$T"/stress-a "$T"/stress-b | grep -c '^UNGESETZT')
	FEHLSCHLAG=$(cat "$T"/stress-a "$T"/stress-b | grep -c '^FEHLER')
	ist "gleichzeitig: kein Aufruf scheitert" gleich "$FEHLSCHLAG" 0
	ist "gleichzeitig: keiner der Schreiber wartet vergeblich ($UNGESETZT ungesetzt)" gleich "$UNGESETZT" 0
	ist "gleichzeitig: mindestens ein Stempel gesetzt" test "$GESETZT" -gt 0
	while read -r art c; do
		[ "$art" = GESETZT ] || continue
		g p notes --ref=refs/notes/gates show "$c" 2> /dev/null | grep -q -x "stempel tests .* gruen" || VERLOREN=$((VERLOREN + 1))
	done < <(cat "$T"/stress-a "$T"/stress-b)
	ist "gleichzeitig: kein als gesetzt gemeldeter Stempel ging verloren" gleich "$VERLOREN" 0
}

# Amend, Rebase, Squash: mit und ohne notes.rewriteRef
g_umschreiben() { # g_umschreiben <mit|ohne>
	local modus=$1 q=q-$1 q2=q2-$1 r=r-$1 s=s-$1
	ohne_rewrite() { if [ "$modus" = ohne ]; then g "$1" config --unset-all notes.rewriteRef; fi; }
	neu "$q"
	S "$q" schreibe tests
	ohne_rewrite "$q"
	g "$q" commit -q --amend -m "neue Meldung"
	if [ "$modus" = mit ]; then erwarte "Amend mit rewriteRef: Note uebernommen, Hash gleich" "$q" 0 - pruefe tests
	else erwarte "Amend ohne rewriteRef: Note am alten Commit, Lauf" "$q" 1 "kein Beleg" pruefe tests; fi
	neu "$q2"
	S "$q2" schreibe tests
	ohne_rewrite "$q2"
	printf 'z\n' >> "$T/$q2/src/A.kt"
	g "$q2" add src/A.kt
	g "$q2" commit -q --amend --no-edit
	erwarte "Amend $modus rewriteRef mit geaendertem Eingang: Hash entscheidet, Lauf" "$q2" 1 "kein Beleg" pruefe tests
	neu "$r"
	g "$r" checkout -q -b feat
	bearbeite "$r" other/f.txt f
	S "$r" schreibe tests
	ohne_rewrite "$r"
	g "$r" checkout -q main
	bearbeite "$r" other/m.txt m
	g "$r" checkout -q feat
	g "$r" rebase -q main
	if [ "$modus" = mit ]; then erwarte "Rebase mit rewriteRef: Note uebernommen" "$r" 0 - pruefe tests
	else erwarte "Rebase ohne rewriteRef: Lauf" "$r" 1 "kein Beleg" pruefe tests; fi
	neu "$s"
	g "$s" checkout -q -b feat
	bearbeite "$s" other/f.txt f
	bearbeite "$s" other/g.txt g
	S "$s" schreibe tests
	ohne_rewrite "$s"
	g "$s" checkout -q main
	bearbeite "$s" other/m.txt m
	g "$s" checkout -q feat
	( cd "$T/$s" && GIT_SEQUENCE_EDITOR="sed -i '2s/^pick/squash/'" GIT_EDITOR=true git rebase -q -i main ) > /dev/null 2>&1
	ist "Squash $modus rewriteRef: zwei Commits zu einem" gleich "$(g "$s" rev-list --count main..feat)" 1
	if [ "$modus" = mit ]; then erwarte "Squash mit rewriteRef: Note uebernommen" "$s" 0 - pruefe tests
	else erwarte "Squash ohne rewriteRef: Lauf" "$s" 1 "kein Beleg" pruefe tests; fi
}
g_umschreiben_mit() { g_umschreiben mit; }
g_umschreiben_ohne() { g_umschreiben ohne; }

# --- Lauf: Gruppen parallel, ausgegeben werden rote Faelle und die Summe ---------------------------------------------------------
GRUPPEN=(g_grund g_hash g_beleg g_schreiben g_sperre g_gleichzeitig g_umschreiben_mit g_umschreiben_ohne)
for gr in "${GRUPPEN[@]}"; do
	(
		ERRF="$T/err.$BASHPID"
		"$gr"
		echo "#ZAHLEN $LAEUFE $FEHLER"
	) > "$T/aus.$gr" 2>&1 &
done
wait
for gr in "${GRUPPEN[@]}"; do
	grep -v '^#ZAHLEN' "$T/aus.$gr"
	z=$(grep '^#ZAHLEN' "$T/aus.$gr")
	if [ -z "$z" ]; then
		rot "Gruppe $gr ohne Ergebnis"
	else
		set -- $z
		LAEUFE=$(($LAEUFE + $2))
		FEHLER=$(($FEHLER + $3))
	fi
done

NACHHER=$(schnappschuss)
LAEUFE=$((LAEUFE + 1))
[ "$NACHHER" = "$VORHER" ] || rot "das Repo des Selbsttests wurde beruehrt (Notes-Refs, lokale Konfiguration)"
if [ "$LAEUFE" -lt 100 ]; then
	rot "nur $LAEUFE Faelle: der Selbsttest gilt als nicht gelaufen"
fi
if [ "$FEHLER" -ne 0 ]; then
	printf 'stempel-selbsttest: %s von %s Faellen rot\n' "$FEHLER" "$LAEUFE" >&2
	exit 1
fi
printf 'stempel-selbsttest: %s Faelle wie erwartet\n' "$LAEUFE"
