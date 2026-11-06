#!/bin/sh
# tools/log-muster-selbsttest.sh - belegt an Beispielzeilen, dass jedes Muster von tools/log-muster.sh gruen und rot werden
# kann, und an Mutationen der Muster, dass jede Aenderung mindestens einen Fall rot faerbt (TDS-FA-09, TDS-QA-03, TDS-QA-06,
# ADR-0020). Aufgerufen von der Dockerfile-Stage `skript-selbsttest`; braucht sh, mktemp, grep, sed.
# Grenze: die Beispielzeilen sind von Hand geschrieben und nicht aus einem Lauf des Testservers; dass der Adapter genau diese
# Form schreibt, haelt TestserverLogProcessTest in make test, nicht dieser Test.
set -eu

lib=$(dirname "$0")/log-muster.sh
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
fehler=0
meldung() { echo "log-muster-selbsttest: FEHLER - $1" >&2; fehler=1; }

printf '%s\n' \
  '{"ts":"t","level":"INFO","event":"login_accepted","principal":"alice"}' \
  '{"ts":"t","level":"WARN","event":"login_rejected","reason":"UNSUPPORTED_PASSWORD_CHANGE"}' \
  '{"ts":"t","level":"ERROR","event":"request_failed","error_code":"TDS_PREPARE_LIMIT","cause":"handle-limit"}' \
  '{"ts":"t","level":"DEBUG","event":"simulation_backend_ready","tables":5}' \
  'tdswire: gestartet, Port 1433' > "$tmp/gut"
: > "$tmp/leer"
printf '%s\n' '{"event":"login_rejected","principal":"alice"}' > "$tmp/alice_in_ablehnung"
printf '%s\n' '{"event":"simulation_backend_ready","tables":4}' > "$tmp/vier_tabellen"
printf '%s\n' '{"event":"connection_failed"}' > "$tmp/verbindungsfehler"
printf '%s\n' '{"event":"adapter_failed"}' > "$tmp/adapterfehler"
printf '%s\n' 'Kennwort secret im Text' > "$tmp/kennwort"
printf '%s\n' '{"event":"request_failed","error_code":"TDS_PREPARE_LIMIT","cause":"anderes"}' > "$tmp/andere_ursache"

# fall <Name> <erwartet: 0 = Muster meldet wahr, 1 = falsch> <Logdatei> <Funktion> [Argumente]; Rueckgabe 0, wenn wie erwartet
fall() {
  name="$1"; soll="$2"; logdatei="$3"; shift 3
  ist=0
  "$@" < "$logdatei" > /dev/null || ist=1
  [ "$ist" -eq "$soll" ] || { meldung "$name (Muster meldet $ist statt $soll)"; return 1; }
}

# alle_faelle <Bibliothek>: liest die Bibliothek ein und faehrt jeden Fall; Rueckgabe: Zahl der roten Faelle
alle_faelle() {
  . "$1"
  rot=0
  fall "Ereignis steht im Log" 0 "$tmp/gut" log_nennt_ereignis login_accepted || rot=$((rot + 1))
  fall "Leeres Log nennt kein Ereignis (Positivprobe)" 1 "$tmp/leer" log_nennt_ereignis login_accepted || rot=$((rot + 1))
  fall "Ereignis mit Fragment" 0 "$tmp/gut" log_nennt_ereignis login_accepted '"principal":"alice"' || rot=$((rot + 1))
  fall "Fragment einer anderen Zeile zaehlt nicht" 1 "$tmp/alice_in_ablehnung" log_nennt_ereignis login_accepted '"principal":"alice"' \
    || rot=$((rot + 1))
  fall "Ereignis mit Zahl der Tabellen" 0 "$tmp/gut" log_nennt_ereignis simulation_backend_ready '"tables":5' || rot=$((rot + 1))
  fall "Andere Zahl der Tabellen" 1 "$tmp/vier_tabellen" log_nennt_ereignis simulation_backend_ready '"tables":5' || rot=$((rot + 1))
  fall "Log ohne Fehlerereignis" 0 "$tmp/gut" log_ohne_ereignis 'connection_failed|adapter_failed' || rot=$((rot + 1))
  fall "connection_failed macht rot" 1 "$tmp/verbindungsfehler" log_ohne_ereignis 'connection_failed|adapter_failed' || rot=$((rot + 1))
  fall "adapter_failed macht rot" 1 "$tmp/adapterfehler" log_ohne_ereignis 'connection_failed|adapter_failed' || rot=$((rot + 1))
  fall "Log ohne Kennwort" 0 "$tmp/gut" log_ohne_text 'secret|Geheimnis' || rot=$((rot + 1))
  fall "Kennwort im Log macht rot" 1 "$tmp/kennwort" log_ohne_text 'secret|Geheimnis' || rot=$((rot + 1))
  fall "Benutzer nur im Ereignis login_accepted" 0 "$tmp/gut" log_text_nur_im_ereignis alice login_accepted || rot=$((rot + 1))
  fall "Benutzer in einer Ablehnung macht rot" 1 "$tmp/alice_in_ablehnung" log_text_nur_im_ereignis alice login_accepted \
    || rot=$((rot + 1))
  fall "Fehler mit Code und Ursache gezaehlt" 0 "$tmp/gut" log_zaehle_fehler TDS_PREPARE_LIMIT handle-limit || rot=$((rot + 1))
  fall "Andere Ursache zaehlt nicht" 1 "$tmp/andere_ursache" log_zaehle_fehler TDS_PREPARE_LIMIT handle-limit || rot=$((rot + 1))
  return "$rot"
}

rot=0
( alle_faelle "$lib" ) || rot=$?
[ "$rot" -eq 0 ] || fehler=1

# mutiere <Name> <sed-Ausdruck>: wendet die Aenderung auf eine Kopie an; sie muss die Kopie aendern und mindestens einen Fall rot faerben
mutiere() {
  name="$1"; ausdruck="$2"; ziel="$tmp/mutant-$name.sh"
  sed "$ausdruck" "$lib" > "$ziel"
  if [ "$(cat "$lib")" = "$(cat "$ziel")" ]; then meldung "Mutation $name aendert nichts"; return; fi
  stumm=0
  ( alle_faelle "$ziel" ) > /dev/null 2>&1 || stumm=$?
  if [ "$stumm" -eq 0 ]; then meldung "Mutation $name bleibt gruen"; else echo "log-muster-selbsttest: ok   Mutation $name faerbt $stumm Faelle rot"; fi
}
mutiere ereignis-ohne-fragment 's/| grep -qF -- "\${2:-.*$/| grep -q ./'
mutiere ereignis-ohne-zeilenfilter 's/grep -F "\\"event\\":\\"\$1\\"" | grep -qF/cat | grep -qF/'
mutiere ohne-ereignis-ohne-negation '/^log_ohne_ereignis/,/^}/ s/^  ! grep/  grep/'
mutiere ohne-ereignis-anderes-feld '/^log_ohne_ereignis/,/^}/ s/\\"event\\"/\\"evento\\"/'
mutiere ohne-text-ohne-negation '/^log_ohne_text/,/^}/ s/^  ! grep/  grep/'
mutiere text-nur-im-ereignis-ohne-filter '/^log_text_nur_im_ereignis/,/^}/ s/grep -v/grep/'
mutiere zaehle-fehler-andere-ursache 's/\\"cause\\"/\\"causx\\"/'
mutiere zaehle-fehler-anderer-code 's/\\"error_code\\"/\\"error_codx\\"/'

[ "$fehler" -eq 0 ] || { echo "log-muster-selbsttest: ROT" >&2; exit 1; }
echo "log-muster-selbsttest: gruen"
