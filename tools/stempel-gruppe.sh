#!/usr/bin/env bash
# tools/stempel-gruppe.sh <klasse> <gate>... - Gruppenschritt einer Gate-Klasse (ADR-0024 Entscheidung 1 und 5, ADR-0023,
# TDS-QA-06). Aufrufer: die Targets gruppe-werkzeug und gruppe-tests in tools/mk/stempel.mk; Selbsttest:
# tools/stempel-gruppe-selbsttest.sh.
#
#   1. `tools/stempel.sh pruefe <klasse>`: Exit 0 ueberspringt die Klasse, jeder andere Ausgang laesst sie laufen.
#      FORCE=1 (Umgebung) ueberspringt die Pruefung; den Hash liefert `pruefe` auch dann auf stdout.
#   2. Alle Gates der Klasse laufen mit `$MAKE <gate>...` (Standard make); das erste rote Gate beendet den Schritt mit
#      Exit 1 und schreibt nichts.
#   3. Nach gruenem Lauf schreibt `tools/stempel.sh schreibe <klasse> <hash>` mit dem Hash aus Schritt 1. Scheitert das
#      Schreiben (schmutziger Eingangsbaum, Hash hat sich geaendert), steht eine Zeile auf stderr und der Schritt bleibt
#      gruen: der naechste Lauf laeuft dann, das Gate faellt nicht.
# TROCKEN=1 (Umgebung, von `make -n`, `-t` und `-q`): meldet die Gates und endet mit Exit 0, ohne zu pruefen, zu rufen oder zu schreiben.
# KEIN_STEMPEL=1 (Umgebung, von `make -i`): die Gates laufen, der Stempel wird nicht geschrieben.
# Ein Aufruf ohne Gate oder mit unbekannter Klasse endet mit Exit 2.
set -u
klasse=${1:-}
case $klasse in werkzeug | tests) ;; *) printf 'stempel-gruppe: Klasse werkzeug oder tests erwartet, nicht %q\n' "$klasse" >&2; exit 2 ;; esac
shift
[ $# -gt 0 ] || { printf 'stempel-gruppe: Klasse %s ohne Gate\n' "$klasse" >&2; exit 2; }

if [ -n "${TROCKEN:-}" ]; then
	printf 'stempel-gruppe: Trockenlauf, %s: %s\n' "$klasse" "$*"
	exit 0
fi

hash=$(bash tools/stempel.sh pruefe "$klasse")
rc=$?
if [ "$rc" -eq 0 ] && [ "${FORCE:-}" != 1 ]; then
	printf 'stempel-gruppe: %s uebersprungen, Beleg fuer %s; FORCE=1 erzwingt den Lauf\n' "$klasse" "$hash"
	exit 0
fi

${MAKE:-make} "$@" || exit 1

if [ -n "${KEIN_STEMPEL:-}" ]; then
	printf 'stempel-gruppe: %s gelaufen, Stempel nicht geschrieben (make -i meldet rote Gates als gruen)\n' "$klasse" >&2
	exit 0
fi

if [[ $hash =~ ^[0-9a-f]{64}$ ]]; then
	bash tools/stempel.sh schreibe "$klasse" "$hash" || printf 'stempel-gruppe: %s gruen, Stempel nicht geschrieben\n' "$klasse" >&2
else
	printf 'stempel-gruppe: %s gruen, kein Eingangs-Hash, Stempel nicht geschrieben\n' "$klasse" >&2
fi
exit 0
