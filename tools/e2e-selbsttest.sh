#!/bin/sh
# e2e-selbsttest.sh — belegt, dass tools/e2e.sh an der ersten roten Stufe mit Exit 1 abbricht, die Stufe
# nennt und keine folgende Stufe startet, und dass acht gruene Stufen Exit 0 ergeben (ADR-0022,
# TDS-QA-06). Die Stufen sind ein Ersatz fuer `make` (E2E_MAKE), der seine Aufrufe protokolliert; es laeuft
# kein Docker. Aufgerufen von der Dockerfile-Stage `skript-selbsttest`; braucht bash, sh, mktemp, grep, tr.
set -eu

e2e=$(dirname "$0")/e2e.sh
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

cat > "$tmp/fake-make" <<'FIX'
#!/bin/sh
echo "$*" >> "$FAKE_LOG"
[ "$1" != "${FAKE_ROT:-}" ] && [ "$*" != "${FAKE_ROT_ARGS:-}" ]
FIX
chmod +x "$tmp/fake-make"

fehler=0
pruefe() { echo "e2e-selbsttest: FEHLER - $1" >&2; fehler=1; }
aufrufe() { tr '\n' ' ' < "$tmp/log"; }

# Rot in Stufe 2: Exit 1, die Meldung nennt Stufe 2, Stufe 3 bis 8 laufen nicht.
: > "$tmp/log"
status=0
E2E_MAKE="$tmp/fake-make" FAKE_LOG="$tmp/log" FAKE_ROT=laufzeittest bash "$e2e" > "$tmp/aus" 2> "$tmp/fehl" || status=$?
[ "$status" -eq 1 ] || pruefe "rote Stufe 2: Exit $status statt 1"
grep -q 'e2e: ROT - Stufe 2' "$tmp/fehl" || pruefe "rote Stufe 2: die Meldung nennt die Stufe nicht"
[ "$(aufrufe)" = "smoke laufzeittest " ] || pruefe "rote Stufe 2: aufgerufen wurde [$(aufrufe)]"
if grep -q '^e2e: gruen' "$tmp/aus"; then pruefe "rote Stufe 2: der Durchgang meldet gruen"; fi

# Rot in Stufe 1: nichts danach.
: > "$tmp/log"
status=0
E2E_MAKE="$tmp/fake-make" FAKE_LOG="$tmp/log" FAKE_ROT=smoke bash "$e2e" > /dev/null 2> "$tmp/fehl" || status=$?
[ "$status" -eq 1 ] || pruefe "rote Stufe 1: Exit $status statt 1"
[ "$(aufrufe)" = "smoke " ] || pruefe "rote Stufe 1: aufgerufen wurde [$(aufrufe)]"

# Rot in Stufe 6 (Kategorie Anmeldung): Exit 1, die Meldung nennt Stufe 6, Stufe 7 laeuft nicht; Stufe 5 lief mit ihrer Kategorie.
: > "$tmp/log"
status=0
E2E_MAKE="$tmp/fake-make" FAKE_LOG="$tmp/log" FAKE_ROT_ARGS="kompatibilitaet KATEGORIE=Anmeldung" bash "$e2e" > /dev/null 2> "$tmp/fehl" || status=$?
[ "$status" -eq 1 ] || pruefe "rote Stufe 6: Exit $status statt 1"
grep -q 'e2e: ROT - Stufe 6' "$tmp/fehl" || pruefe "rote Stufe 6: die Meldung nennt die Stufe nicht"
case "$(aufrufe)" in
  *"kompatibilitaet KATEGORIE=Szenario kompatibilitaet KATEGORIE=Anmeldung ") ;;
  *) pruefe "rote Stufe 6: aufgerufen wurde [$(aufrufe)]" ;;
esac

# Rot in Stufe 8 (Kategorie Synchronitaet): Exit 1, die Meldung nennt Stufe 8; Stufe 7 lief mit ihrer Kategorie.
: > "$tmp/log"
status=0
E2E_MAKE="$tmp/fake-make" FAKE_LOG="$tmp/log" FAKE_ROT_ARGS="kompatibilitaet KATEGORIE=Synchronitaet" bash "$e2e" > "$tmp/aus" 2> "$tmp/fehl" || status=$?
[ "$status" -eq 1 ] || pruefe "rote Stufe 8: Exit $status statt 1"
grep -q 'e2e: ROT - Stufe 8' "$tmp/fehl" || pruefe "rote Stufe 8: die Meldung nennt die Stufe nicht"
if grep -q '^e2e: gruen' "$tmp/aus"; then pruefe "rote Stufe 8: der Durchgang meldet gruen"; fi
case "$(aufrufe)" in
  *"kompatibilitaet KATEGORIE=Luecken kompatibilitaet KATEGORIE=Synchronitaet ") ;;
  *) pruefe "rote Stufe 8: aufgerufen wurde [$(aufrufe)]" ;;
esac

# Alles gruen: Exit 0, acht Stufen in der Reihenfolge.
: > "$tmp/log"
status=0
E2E_MAKE="$tmp/fake-make" FAKE_LOG="$tmp/log" bash "$e2e" > "$tmp/aus" 2> /dev/null || status=$?
[ "$status" -eq 0 ] || pruefe "alles gruen: Exit $status statt 0"
[ "$(aufrufe)" = "smoke laufzeittest simulations-integration referenztest-dotnet FORCE=1 kompatibilitaet KATEGORIE=Szenario kompatibilitaet KATEGORIE=Anmeldung kompatibilitaet KATEGORIE=Luecken kompatibilitaet KATEGORIE=Synchronitaet " ] || pruefe "alles gruen: aufgerufen wurde [$(aufrufe)]"
grep -q '^e2e: gruen (8 Stufen' "$tmp/aus" || pruefe "alles gruen: die Schlusszeile fehlt"

# Jede Deklaration einer Stufe traegt den Teil "Nicht belegt:" mit nichtleerem Rest.
# Die Zeilen der Stufen beginnen mit `e2e_abdeckung "` (die Funktionsdefinition mit `e2e_abdeckung()`).
zeilen=$(grep -c '^e2e_abdeckung "' "$e2e" || true)
[ "$zeilen" -eq 8 ] || pruefe "Deklarationen: $zeilen statt 8 Zeilen e2e_abdeckung"
mit=$(grep '^e2e_abdeckung "' "$e2e" | grep -c 'Nicht belegt: [^ "][^"]*" "' || true)
[ "$mit" -eq "$zeilen" ] || pruefe "Deklarationen: $((zeilen - mit)) von $zeilen Stufen ohne Teil 'Nicht belegt:' mit Rest"

[ "$fehler" -eq 0 ] || exit 1
echo "e2e-selbsttest: ok (Abbruch an Stufe 1, 2, 6 und 8, alle gruen, Teil Nicht belegt in 8 Stufen)"
