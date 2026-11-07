#!/bin/sh
# test-lauf.sh - der eine Aufruf von `dotnet test` fuer die Stages `test` und `test-bericht` (TDS-QA-05, TDS-QA-06).
# Aufruf: sh test-lauf.sh test | bericht. Beide Modi fuehren `dotnet test $DOTNET_TEST_ARGS` aus (Variable der Basis
# `test-quellen` im Dockerfile); der Modus `bericht` fuegt nur den TRX-Logger und das Ergebnisverzeichnis /bericht hinzu.
#   test     rote Tests und Bau-Fehler brechen den Lauf (Exit ungleich 0); meldet `dotnet test` "No test matches" oder
#            "No test is available" (ein Filter ohne Treffer, kein Test im Bau), endet der Lauf mit Exit 3, auch wenn
#            `dotnet test` selbst 0 liefert.
#   bericht  rote Tests brechen nicht ab, der Bericht bleibt erhalten; scheitert schon der Bau, entsteht /bericht nicht
#            und der Export `test-bericht-export` bricht.
# Jedes andere Argument bricht mit Exit 2 ab: ein Flag (etwa --filter) am Skriptaufruf, das nur eine Stage verengt, ist hier
# nicht moeglich. Grenze: eine RUN-Zeile, die dieses Skript nicht ruft, sieht es nicht. Die Abweisung und die Modi belegt
# tools/test-lauf-selbsttest.sh (Stage `skript-selbsttest`); Grenze dort: der Stub belegt die feste Locale, nicht den Wortlaut des echten `dotnet`.
set -u
# Die Locale ist fest: die Meldungen von `dotnet test`, die der Modus `test` liest, sind die englischen (mit LANG oder LC_ALL
# auf de_DE.UTF-8 schreibt dotnet "Kein Test entspricht ...", gemessen 2026-11-07).
LC_ALL=C.UTF-8 LANG=C.UTF-8 LANGUAGE=en
export LC_ALL LANG LANGUAGE
if [ "$#" -ne 1 ]; then
  echo "test-lauf.sh: genau ein Argument: test | bericht" >&2
  exit 2
fi
case "$1" in
  test)
    ausgabe=$(mktemp) || exit 1
    trap 'rm -f "$ausgabe"' EXIT
    # DOTNET_TEST_ARGS ist absichtlich ungequotet: die Variable traegt mehrere Argumente.
    dotnet test $DOTNET_TEST_ARGS > "$ausgabe" 2>&1
    rc=$?
    cat "$ausgabe"
    if [ "$rc" -eq 0 ] && grep -Eq 'No test matches|No test is available' "$ausgabe"; then
      echo "test-lauf.sh: dotnet test hat keinen Test ausgefuehrt; ein Gate ohne Tests ist rot" >&2
      exit 3
    fi
    exit "$rc"
    ;;
  bericht)
    dotnet test $DOTNET_TEST_ARGS --logger "trx;LogFileName=bericht.trx" --results-directory /bericht \
      || echo "test-bericht: der Testlauf ist rot; rote Tests zaehlen nicht als Nachweis"
    ;;
  *)
    echo "test-lauf.sh: unbekannter Modus '$1' (test | bericht)" >&2
    exit 2
    ;;
esac
