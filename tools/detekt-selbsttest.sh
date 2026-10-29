#!/bin/sh
# detekt-selbsttest.sh — Selbsttest der Importregeln in config/detekt/detekt.yml und des
# Suppression-Verbots (tools/suppression-check.sh).
#
#   generate     erzeugt aus detekt.yml je Regel ein Forbidden.kt unter build/selbsttest/: ein
#                Import je verbotenem Package-Wert, im Package, auf das das `includes` zeigt.
#   check        wertet den Bericht der Gradle-Task detektSelbsttest aus. Gruen ist der Test, wenn
#                (1) jede Regel mindestens einen Fund hat (Forbidden.kt oder Fremd.kt),
#                (2) jeder verbotene Import genau einmal gemeldet ist: die erzeugten (Forbidden.kt)
#                    und die handgeschriebenen Fremdbibliotheken des Kerns (Fremd.kt),
#                (3) die erlaubten Imports in Allowed.kt keinen Fund ausloesen,
#                (4) die Packages der Regeln die Verzeichnisse der Testdaten sind (ohne runtime),
#                    in beide Richtungen, und jeder Package-Wert einer Regel dort vorkommt,
#                (5) bei einer Verbotsliste verbotene, erlaubte (Allowed.kt) und das eigene Package
#                    zusammen alle Verzeichnisse der Testdaten ergeben, und
#                (6) jede Regel mit Erlaubnisliste ein Fremd.kt hat.
#   suppression  prueft an erzeugten Dateien, dass tools/suppression-check.sh jede Form von
#                @Suppress meldet und erlaubte Namen nicht.
#
# Aufgerufen von der Dockerfile-Stage `detekt-selbsttest`; braucht nur sh, awk, sed und grep.
# Grenze: geprueft ist, dass die Regeln greifen und nichts treffen, was Allowed.kt als erlaubt nennt;
# die Wahrheit von Allowed.kt gegenueber spec/architecture.md prueft ein Mensch.
set -eu

yml=config/detekt/detekt.yml
td=config/detekt/selbsttest/io/github/pt9912/tdswire
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
  n=$(cat "$out"/io/github/pt9912/tdswire/*/Forbidden.kt | grep -c '^import ' || true)
  echo "detekt-selbsttest: $n verbotene Importe in $out erzeugt"
}

# Eine Zeile je Regel: Package|Modus (deny oder allow)|verbotene Packages|erlaubte Packages
regeltabelle() {
  awk '
    function flush() { if (pkg != "") print pkg "|" mode "|" forb "|" allow; pkg = "" }
    function paket(zeile,   v) { v = zeile; sub(/.*tdswire\./, "", v); sub(/\..*/, "", v); return v }
    /^  ForbiddenImport\/[a-z]+:/ { flush(); mode = "deny"; forb = ""; allow = ""; inallowed = 0; next }
    /includes:/ { if (match($0, /tdswire\/[a-z]+\/\*\*/)) pkg = substr($0, RSTART + 8, RLENGTH - 11); next }
    /^    allowedImports:/ { inallowed = 1; next }
    /^    [a-zA-Z]/ { inallowed = 0 }
    /^      - value: .\*./ { mode = "allow"; next }
    /^      - value: .io\.github/ { forb = forb " " paket($0); next }
    inallowed && /^      - .io\.github\.pt9912\.tdswire\./ { allow = allow " " paket($0) }
    END { flush() }
  ' "$yml"
}

check() {
  [ -f "$report" ] || fail "kein Bericht unter $report - lief detektSelbsttest?"
  funde=$(mktemp)
  tabelle=$(mktemp)
  # Zeile je Fund: Dateiname, Regel
  awk '
    /<file name=/ { match($0, /name="[^"]*"/); f = substr($0, RSTART + 6, RLENGTH - 7); sub(/.*\//, "", f); next }
    /source="detekt\.ForbiddenImport\// { match($0, /ForbiddenImport\/[a-z]+/); print f, substr($0, RSTART, RLENGTH) }
  ' "$report" > "$funde"
  regeltabelle > "$tabelle"
  [ -s "$tabelle" ] || fail "keine ForbiddenImport-Regel mit includes in $yml"

  bekannt=$(ls "$td" | sort -u)
  pakete_regeln=$(cut -d'|' -f1 "$tabelle" | sort -u)
  pakete_testdaten=$(printf '%s\n' "$bekannt" | grep -vx runtime)
  [ "$pakete_regeln" = "$pakete_testdaten" ] || fail "Packages der Regeln ($(echo $pakete_regeln)) und der Testdaten unter config/detekt/selbsttest ohne runtime ($(echo $pakete_testdaten)) stimmen nicht ueberein"

  anzahl=0
  while IFS='|' read -r pkg modus verboten erlaubt; do
    anzahl=$((anzahl + 1))
    for p in $verboten $erlaubt; do
      printf '%s\n' "$bekannt" | grep -qx "$p" || fail "Regel $pkg nennt das Package '$p', das es unter config/detekt/selbsttest nicht gibt"
    done
    [ -f "$td/$pkg/Allowed.kt" ] || fail "Regel $pkg hat kein Allowed.kt unter config/detekt/selbsttest"
    if [ "$modus" = allow ]; then
      [ -f "$td/$pkg/Fremd.kt" ] || fail "Regel $pkg hat eine Erlaubnisliste und braucht ein Fremd.kt unter config/detekt/selbsttest"
    else
      importiert=$(sed -n 's/^import io\.github\.pt9912\.tdswire\.\([a-z]*\)\..*/\1/p' "$td/$pkg/Allowed.kt")
      vereinigung=$(printf '%s\n' $verboten $importiert "$pkg" | sort -u)
      [ "$vereinigung" = "$bekannt" ] || fail "Regel $pkg: verbotene ($(echo $verboten)), erlaubte ($(echo $importiert)) und das eigene Package ergeben ($(echo $vereinigung)), die Testdaten kennen ($(echo $bekannt)) - ein Wert fehlt in detekt.yml oder in Allowed.kt"
    fi
    n=$(grep -cE "^(Forbidden|Fremd)\.kt ForbiddenImport/$pkg\$" "$funde" || true)
    [ "$n" -ge 1 ] || fail "Regel ForbiddenImport/$pkg meldet keinen verbotenen Import - includes oder Package passen nicht"
  done < "$tabelle"

  erwartet_werte=$(grep -c "^      - value: 'io\.github" "$yml" || true)
  erwartet_fremd=$(cat "$td"/*/Fremd.kt 2>/dev/null | grep -c '^import ' || true)
  erwartet=$((erwartet_werte + erwartet_fremd))
  gemeldet=$(grep -cE '^(Forbidden|Fremd)\.kt ' "$funde" || true)
  [ "$gemeldet" -eq "$erwartet" ] || fail "$gemeldet von $erwartet verbotenen Importen gemeldet ($erwartet_werte aus detekt.yml, $erwartet_fremd aus Fremd.kt) - ein Wert greift nicht"

  falsch=$(grep -c '^Allowed\.kt ' "$funde" || true)
  [ "$falsch" -eq 0 ] || fail "$falsch erlaubte Importe gemeldet: $(grep '^Allowed\.kt ' "$funde" | tr '\n' ';')"

  echo "detekt-selbsttest: OK - $anzahl Regeln, $gemeldet von $erwartet verbotenen Importen gemeldet, 0 Fehlmeldungen bei erlaubten"
}

suppression() {
  skript=tools/suppression-check.sh
  d=build/suppression-test
  mkdir -p "$d/verboten" "$d/erlaubt"
  printf '@file:Suppress("X")\npackage a\n' > "$d/verboten/FileSuppress.kt"
  printf 'package a\n\n@Suppress("X")\nclass A\n' > "$d/verboten/Suppress.kt"
  printf 'package a\n\n@SuppressWarnings("X")\nclass B\n' > "$d/verboten/Warnings.kt"
  printf 'package a\n\n@kotlin.Suppress("X")\nclass C\n' > "$d/verboten/Qualified.kt"
  printf 'package a\n\nval unsuppressedCount = 0\n// Suppress ohne At-Zeichen\n' > "$d/erlaubt/Names.kt"
  n=0
  for f in "$d"/verboten/*.kt; do
    n=$((n + 1))
    if sh "$skript" "$f" > /dev/null 2>&1; then fail "$skript meldet $f nicht"; fi
  done
  sh "$skript" "$d/erlaubt" > /dev/null || fail "$skript meldet einen erlaubten Namen"
  echo "detekt-selbsttest: OK - $n Formen von @Suppress gemeldet, erlaubte Namen nicht"
}

case "${1:-}" in
  generate) generate ;;
  check) check ;;
  suppression) suppression ;;
  *) echo "Aufruf: $0 generate|check|suppression" >&2; exit 2 ;;
esac
