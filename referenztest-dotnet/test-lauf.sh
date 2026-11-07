#!/bin/sh
# test-lauf.sh - der eine Aufruf von `dotnet test` fuer die Stages `test` und `test-bericht` (TDS-QA-05, TDS-QA-06).
# Aufruf: sh test-lauf.sh test | bericht. Beide Modi fuehren `dotnet test $DOTNET_TEST_ARGS` aus (Variable der Basis
# `test-quellen` im Dockerfile); der Modus `bericht` fuegt nur den TRX-Logger und das Ergebnisverzeichnis /bericht hinzu.
#   test     rote Tests und Bau-Fehler brechen den Lauf (Exit ungleich 0).
#   bericht  rote Tests brechen nicht ab, der Bericht bleibt erhalten; scheitert schon der Bau, entsteht /bericht nicht
#            und der Export `test-bericht-export` bricht.
# Jedes andere Argument bricht mit Exit 2 ab: ein Flag (etwa --filter), das nur eine Stage verengt, ist hier nicht moeglich.
set -u
if [ "$#" -ne 1 ]; then
  echo "test-lauf.sh: genau ein Argument: test | bericht" >&2
  exit 2
fi
case "$1" in
  test)
    # DOTNET_TEST_ARGS ist absichtlich ungequotet: die Variable traegt mehrere Argumente.
    exec dotnet test $DOTNET_TEST_ARGS
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
