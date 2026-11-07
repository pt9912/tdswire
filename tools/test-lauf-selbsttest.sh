#!/bin/sh
# tools/test-lauf-selbsttest.sh - belegt gegen einen `dotnet`-Stub im PATH, dass referenztest-dotnet/test-lauf.sh die Argumente
# abweist (Exit 2), im Modus `test` ohne Logger mit DOTNET_TEST_ARGS aufruft und bei rotem Test oder ohne ausgefuehrten Test
# Exit ungleich 0 liefert, im Modus `bericht` Logger und Ergebnisverzeichnis anfuegt und bei rotem Test Exit 0 behaelt, und an
# Mutationen des Skripts, dass jede Aenderung mindestens einen Fall rot faerbt (TDS-QA-05, TDS-QA-06, ADR-0024).
# Aufgerufen von der Dockerfile-Stage `skript-selbsttest`; braucht sh, mktemp, grep, sed, cat.
# Grenze: der Stub kennt nur Argumente, Ausgabe und Exit; dass das echte `dotnet test` bei einem Filter ohne Treffer die geprueften
# Meldungen schreibt, ist am echten Lauf einmal von Hand gemessen und nicht Gegenstand dieses Tests.
set -u

skript=${1:-referenztest-dotnet/test-lauf.sh}
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
mkdir "$tmp/bin"
cat > "$tmp/bin/dotnet" <<'STUB'
#!/bin/sh
printf '%s\n' "$*" >> "$STUB_LOG"
printf '%s\n' "$LC_ALL $LANG $LANGUAGE" > "$STUB_LOG.locale"
[ -n "${STUB_AUSGABE:-}" ] && printf '%s\n' "$STUB_AUSGABE"
exit "${STUB_RC:-0}"
STUB
chmod +x "$tmp/bin/dotnet"

# lauf <Skript> <Argumente...>: setzt STUB_RC und STUB_AUSGABE aus der Umgebung des Aufrufers; Rueckgabe: Exit des Skripts, Log in $tmp/log
lauf() {
  s="$1"; shift
  : > "$tmp/log"
  PATH="$tmp/bin:$PATH" STUB_LOG="$tmp/log" DOTNET_TEST_ARGS="--no-restore --nologo" sh "$s" "$@" > "$tmp/aus" 2> "$tmp/fehl"
}

# alle_faelle <Skript>: Rueckgabe = Zahl der roten Faelle (ueber $tmp/rot)
alle_faelle() {
  s="$1"; rot=0
  erwartet() { # <Name> <Bedingung als Exit-Vergleich: 0 = erfuellt>
    [ "$2" -eq 0 ] || { echo "test-lauf-selbsttest: FEHLER - $1" >&2; rot=$((rot + 1)); }
  }
  ist=0; lauf "$s" || ist=$?; erwartet "ohne Argument Exit 2 (war $ist)" $((ist == 2 ? 0 : 1))
  ist=0; lauf "$s" test --filter X || ist=$?; erwartet "zwei Argumente Exit 2 (war $ist)" $((ist == 2 ? 0 : 1))
  erwartet "zwei Argumente rufen dotnet nicht" $([ ! -s "$tmp/log" ]; echo $?)
  ist=0; lauf "$s" unbekannt || ist=$?; erwartet "unbekannter Modus Exit 2 (war $ist)" $((ist == 2 ? 0 : 1))
  ist=0; STUB_AUSGABE="Passed!" lauf "$s" test || ist=$?
  erwartet "Modus test gruen Exit 0 (war $ist)" $((ist == 0 ? 0 : 1))
  erwartet "Modus test ruft dotnet test mit den Argumenten, ohne Logger" $([ "$(cat "$tmp/log")" = "test --no-restore --nologo" ]; echo $?)
  ist=0; STUB_RC=1 lauf "$s" test || ist=$?
  erwartet "Modus test rot behaelt den Exit (war $ist)" $((ist == 1 ? 0 : 1))
  ist=0; STUB_AUSGABE="No test matches the given testcase filter" lauf "$s" test || ist=$?
  erwartet "Modus test ohne Treffer ist rot, Exit 3 (war $ist)" $((ist == 3 ? 0 : 1))
  ist=0; STUB_AUSGABE="No test is available in x.dll" lauf "$s" test || ist=$?
  erwartet "Modus test ohne verfuegbaren Test ist rot, Exit 3 (war $ist)" $((ist == 3 ? 0 : 1))
  ist=0; LC_ALL=de_DE.UTF-8 LANG=de_DE.UTF-8 LANGUAGE=de lauf "$s" test || ist=$?
  erwartet "dotnet laeuft mit fester Locale trotz de_DE in der Umgebung" $([ "$(cat "$tmp/log.locale")" = "C.UTF-8 C.UTF-8 en" ]; echo $?)
  ist=0; STUB_AUSGABE="No test matches the given testcase filter" lauf "$s" bericht || ist=$?
  erwartet "Modus bericht bricht ohne Treffer nicht ab (war $ist)" $((ist == 0 ? 0 : 1))
  ist=0; STUB_RC=1 lauf "$s" bericht || ist=$?
  erwartet "Modus bericht rot behaelt Exit 0 (war $ist)" $((ist == 0 ? 0 : 1))
  erwartet "Modus bericht meldet den roten Lauf" $(grep -q 'der Testlauf ist rot' "$tmp/aus"; echo $?)
  erwartet "Modus bericht haengt Logger und Ergebnisverzeichnis an" \
    $([ "$(cat "$tmp/log")" = 'test --no-restore --nologo --logger trx;LogFileName=bericht.trx --results-directory /bericht' ]; echo $?)
  echo "$rot" > "$tmp/rot"
}

alle_faelle "$skript"
if [ "$(cat "$tmp/rot")" -ne 0 ]; then
  echo "test-lauf-selbsttest: FEHLER - das Skript besteht seine Faelle nicht" >&2
  exit 1
fi

# Mutationen: jede Aenderung am Skript muss mindestens einen Fall rot faerben.
fehler=0
mutation() { # <Name> <sed-Ausdruck>
  sed "$2" "$skript" > "$tmp/mutant.sh"
  if cmp -s "$skript" "$tmp/mutant.sh"; then echo "test-lauf-selbsttest: FEHLER - Mutation '$1' aendert nichts" >&2; fehler=1; return; fi
  alle_faelle "$tmp/mutant.sh" 2> /dev/null
  [ "$(cat "$tmp/rot")" -gt 0 ] || { echo "test-lauf-selbsttest: FEHLER - Mutation '$1' bleibt gruen" >&2; fehler=1; }
}
mutation "Argumentzaehlung gestrichen" 's/"\$#" -ne 1/"$#" -ne 99/'
mutation "unbekannter Modus Exit 0" 's/^    exit 2$/    exit 0/'
mutation "Treffer-Pruefung gestrichen" 's/No test matches|No test is available/ZZZ/'
mutation "Exit des Modus test verschluckt" 's/^    exit "\$rc"$/    exit 0/'
mutation "Logger im Modus test" 's/dotnet test \$DOTNET_TEST_ARGS > /dotnet test $DOTNET_TEST_ARGS --logger x > /'
mutation "Modus bericht bricht bei Rot ab" 's/ || echo "test-bericht: [^"]*"//'
mutation "Ergebnisverzeichnis gestrichen" 's| --results-directory /bericht||'
mutation "feste Locale gestrichen" 's/^LC_ALL=C.UTF-8 .*$/:/'
mutation "Argumente der Variable gestrichen" 's/dotnet test \$DOTNET_TEST_ARGS > /dotnet test > /'
[ "$fehler" -eq 0 ] || exit 1
echo "test-lauf-selbsttest: ok - Faelle und 9 Mutationen wie erwartet"
