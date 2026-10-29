#!/bin/sh
# detekt-selbsttest.sh — Selbsttest der Importregeln in config/detekt/detekt.yml.
#
#   generate   erzeugt aus detekt.yml je Regel ein Forbidden.kt unter build/selbsttest/: ein
#              Import je verbotenem Wert, im Package, auf das das `includes` der Regel zeigt.
#   check      wertet den Bericht der Gradle-Task detektSelbsttest aus. Der Test ist gruen,
#              wenn (1) jede Regel mindestens einen Fund hat, (2) jeder erzeugte verbotene Import
#              genau einmal gemeldet ist und (3) die erlaubten Importe in
#              config/detekt/selbsttest/**/Allowed.kt keinen Fund ausloesen.
#
# Aufgerufen von der Dockerfile-Stage `detekt-selbsttest`; braucht nur sh, awk und grep.
# Grenze: geprueft ist, dass die Regeln greifen und nichts Erlaubtes treffen, das Allowed.kt
# nennt - nicht, dass die Tabelle in spec/architecture.md vollstaendig in detekt.yml steht.
set -eu

yml=config/detekt/detekt.yml
out=build/selbsttest
report=build/reports/detekt-selbsttest/detekt.xml

fail() { echo "detekt-selbsttest: FEHLER - $*" >&2; exit 1; }

generate() {
  [ -f "$yml" ] || fail "$yml fehlt"
  mkdir -p "$out"
  awk -v out="$out" '
    /^  ForbiddenImport\/[a-z]+:/ { regel = $1; sub(/:$/, "", regel); file = "" }
    /includes:/ {
      if (match($0, /tdswire\/[a-z]+\/\*\*/)) {
        pkg = substr($0, RSTART + 8, RLENGTH - 11)
        dir = out "/io/github/pt9912/tdswire/" pkg
        system("mkdir -p " dir)
        file = dir "/Forbidden.kt"
        files[file] = 1
        print "package io.github.pt9912.tdswire." pkg "\n" > file
      } else {
        print "detekt-selbsttest: FEHLER - " regel ": includes nennt kein Package der Form tdswire/<paket>/** (Kleinbuchstaben)" > "/dev/stderr"
        exit 1
      }
    }
    /^      - value: .io\.github/ {
      if (file == "") { print "detekt-selbsttest: FEHLER - " regel ": Wert vor includes" > "/dev/stderr"; exit 1 }
      match($0, /\x27[^\x27]*\x27/)
      v = substr($0, RSTART + 1, RLENGTH - 2)
      sub(/\*$/, "Frame", v)
      print "import " v > file
    }
    END { for (f in files) { print "\ninternal object Forbidden" >> f; close(f) } }
  ' "$yml"
  n=$(cat "$out"/io/github/pt9912/tdswire/*/Forbidden.kt | grep -c '^import ')
  echo "detekt-selbsttest: $n verbotene Importe in $out erzeugt"
}

check() {
  [ -f "$report" ] || fail "kein Bericht unter $report - lief detektSelbsttest?"
  funde=$(mktemp)
  # Zeile je Fund: Dateiname, Regel
  awk '
    /<file name=/ { match($0, /name="[^"]*"/); f = substr($0, RSTART + 6, RLENGTH - 7); sub(/.*\//, "", f); next }
    /source="detekt\.ForbiddenImport\// { match($0, /ForbiddenImport\/[a-z]+/); print f, substr($0, RSTART, RLENGTH) }
  ' "$report" > "$funde"

  erwartet=$(grep -c "^      - value: 'io\.github" "$yml")
  regeln=$(grep -oE '^  ForbiddenImport/[a-z]+' "$yml" | sed 's/^ *//' | sort -u)
  [ -n "$regeln" ] || fail "keine ForbiddenImport-Regel in $yml"

  anzahl=0
  for r in $regeln; do
    anzahl=$((anzahl + 1))
    n=$(grep -c "^Forbidden.kt $r\$" "$funde" || true)
    [ "$n" -ge 1 ] || fail "Regel $r meldet keinen verbotenen Import - includes oder Package passen nicht"
  done

  # Die Packages der Regeln muessen die Verzeichnisse der handgeschriebenen Testdaten sein, in
  # beide Richtungen: so faengt ein Tippfehler im includes (die erzeugten Testdaten folgen ihm)
  # und ein neues Package ohne Regel. runtime ist der Composition Root und hat keine Regel.
  pakete_regeln=$(grep -oE "includes: \['\*\*/io/github/pt9912/tdswire/[a-z]+/" "$yml" | sed 's#.*/tdswire/##; s#/$##' | sort -u)
  pakete_testdaten=$(ls config/detekt/selbsttest/io/github/pt9912/tdswire | grep -vx runtime | sort -u)
  [ "$pakete_regeln" = "$pakete_testdaten" ] || fail "Packages der Regeln ($(echo $pakete_regeln)) und der Testdaten unter config/detekt/selbsttest ohne runtime ($(echo $pakete_testdaten)) stimmen nicht ueberein"

  # Jeder verbotene Wert muss auf ein Package der Testdaten zeigen (runtime eingeschlossen): ein
  # Tippfehler im Wert wuerde sonst bestehen, denn der erzeugte Import folgt ihm.
  bekannt=$(ls config/detekt/selbsttest/io/github/pt9912/tdswire | sort -u)
  for v in $(grep "^      - value: 'io\.github" "$yml" | sed "s#.*tdswire\.\([a-z]*\)\..*#\1#" | sort -u); do
    echo "$bekannt" | grep -qx "$v" || fail "ein verbotener Wert nennt das Package '$v', das es unter config/detekt/selbsttest nicht gibt"
  done

  gemeldet=$(grep -c '^Forbidden.kt ' "$funde" || true)
  [ "$gemeldet" -eq "$erwartet" ] || fail "$gemeldet von $erwartet verbotenen Importen gemeldet - ein Wert in detekt.yml greift nicht"

  falsch=$(grep -c '^Allowed.kt ' "$funde" || true)
  [ "$falsch" -eq 0 ] || fail "$falsch erlaubte Importe gemeldet: $(grep '^Allowed.kt ' "$funde" | tr '\n' ';')"

  echo "detekt-selbsttest: OK - $anzahl Regeln, $gemeldet von $erwartet verbotenen Importen gemeldet, 0 Fehlmeldungen bei erlaubten"
}

case "${1:-}" in
  generate) generate ;;
  check) check ;;
  *) echo "Aufruf: $0 generate|check" >&2; exit 2 ;;
esac
