#!/bin/sh
# detekt-selbsttest.sh — Selbsttest der Import- und Aufrufregeln in config/detekt/detekt.yml und des
# Suppression-Verbots (tools/suppression-check.sh).
#
#   generate     erzeugt aus detekt.yml je ForbiddenImport-Regel eine Datei Forbidden_<regel>.kt unter
#                build/selbsttest/: ein Import je verbotenem Wert (ohne den Wert '*'), im Package, auf das
#                das `includes` der Regel zeigt.
#   check        wertet den Bericht der Gradle-Task detektSelbsttest aus. Gruen ist der Test, wenn
#                (1) die Packages der Regeln die Verzeichnisse der Testdaten sind (ohne runtime), in beide
#                    Richtungen, und jeder Package-Wert einer Regel dort vorkommt,
#                (2) jede Regel genau so viele Funde hat, wie sie verbotene Imports hat: die erzeugten
#                    (Forbidden_<regel>.kt) und bei einer Erlaubnisliste die handgeschriebenen
#                    Fremdbibliotheken (Fremd.kt) - eine Regel ohne Fund meldet nichts,
#                (3) die erlaubten Imports in Allowed.kt keinen Fund ausloesen,
#                (4) bei einer Verbotsliste (Regel = Package) verbotene, erlaubte (Allowed.kt) und das
#                    eigene Package zusammen alle Verzeichnisse der Testdaten ergeben,
#                (5) bei einer Erlaubnisliste die erlaubten Projekt-Packages genau die Imports von Allowed.kt
#                    plus das eigene Package sind, und sie ein Fremd.kt hat.
#                Eine Regel `<package>-technik` ist eine zusaetzliche Verbotsliste ueber Technik unter den
#                erlaubten Globs; ihre verbotenen Imports stehen handgeschrieben in Technik.kt und muessen
#                genau die Werte der Regel sein (ein Eintrag zu wenig oder zu viel faellt auf), jeder
#                davon wird gemeldet; fuer sie gilt zudem (3).
#   aufrufe      wertet den Bericht der Task detektSelbsttestAufrufe aus: jeder Eintrag von
#                ForbiddenMethodCall in detekt.yml hat in Aufruf.kt jedes Kern-Packages einen Fund, die
#                Markierungen `// erwartet: <Methode>` in Aufruf.kt sind genau diese Eintraege, und
#                Erlaubt.kt hat keinen Fund.
#   suppression  prueft an erzeugten Dateien, dass tools/suppression-check.sh jede Form von @Suppress
#                meldet, die das Skript nennt, und erlaubte Namen nicht.
#
# Aufgerufen von der Dockerfile-Stage `detekt-selbsttest`; braucht sh, awk, sed, grep, sort, cut, ls, tr
# und mktemp. Grenze: geprueft ist, dass die Regeln greifen und nichts treffen, was Allowed.kt und
# Erlaubt.kt als erlaubt nennen; die Wahrheit dieser Dateien gegenueber spec/architecture.md prueft ein
# Mensch.
set -eu

yml=config/detekt/detekt.yml
td=config/detekt/selbsttest/io/github/pt9912/tdswire
td_aufrufe=config/detekt/selbsttest-aufrufe/io/github/pt9912/tdswire
out=build/selbsttest
report=build/reports/detekt-selbsttest/detekt.xml
report_aufrufe=build/reports/detekt-selbsttest/aufrufe.xml

fail() { echo "detekt-selbsttest: FEHLER - $*" >&2; exit 1; }

generate() {
  [ -f "$yml" ] || fail "$yml fehlt"
  mkdir -p "$out"
  awk -v out="$out" '
    /^  ForbiddenImport\/[a-z-]+:/ { regel = $1; sub(/:$/, "", regel); sub(/.*\//, "", regel); inimport = 1; file = ""; skip = 0; next }
    /^  [A-Za-z]/ { inimport = 0 }
    inimport && /includes:/ {
      if (match($0, /tdswire\/[a-z]+\/\*\*/)) {
        pkg = substr($0, RSTART + 8, RLENGTH - 11)
        if (regel != pkg) { skip = 1; file = ""; next }
        dir = out "/io/github/pt9912/tdswire/" pkg
        system("mkdir -p " dir)
        file = dir "/Forbidden_" regel ".kt"
        files[file] = 1
        print "package io.github.pt9912.tdswire." pkg "\n" > file
      } else {
        print "detekt-selbsttest: FEHLER - " regel ": includes nennt kein Package der Form tdswire/<paket>/** (Kleinbuchstaben)" > "/dev/stderr"
        exit 1
      }
      next
    }
    inimport && /^      - value: / {
      if (skip) next
      if (file == "") { print "detekt-selbsttest: FEHLER - " regel ": Wert vor includes" > "/dev/stderr"; exit 1 }
      match($0, /\x27[^\x27]*\x27/)
      v = substr($0, RSTART + 1, RLENGTH - 2)
      if (v == "*") next
      sub(/\*$/, "Frame", v)
      print "import " v > file
    }
    END { for (f in files) { print "\ninternal object Forbidden" >> f; close(f) } }
  ' "$yml"
  n=$(cat "$out"/io/github/pt9912/tdswire/*/Forbidden_*.kt | grep -c '^import ' || true)
  echo "detekt-selbsttest: $n verbotene Importe in $out erzeugt"
}

# Eine Zeile je ForbiddenImport-Regel: Name|Package|Modus (deny oder allow)|verbotene Projekt-Packages|
# erlaubte Projekt-Packages|Zahl der verbotenen Werte (ohne '*')
regeltabelle() {
  awk '
    function flush() { if (name != "") print name "|" pkg "|" mode "|" forb "|" allow "|" n; name = "" }
    function paket(zeile,   v) { v = zeile; sub(/.*tdswire\./, "", v); sub(/\..*/, "", v); return v }
    /^  ForbiddenImport\/[a-z-]+:/ {
      flush(); name = $1; sub(/:$/, "", name); sub(/.*\//, "", name)
      pkg = ""; mode = "deny"; forb = ""; allow = ""; n = 0; inallowed = 0; inimport = 1; next
    }
    /^  [A-Za-z]/ { flush(); inimport = 0 }
    inimport && /includes:/ { if (match($0, /tdswire\/[a-z]+\/\*\*/)) pkg = substr($0, RSTART + 8, RLENGTH - 11); next }
    inimport && /^    allowedImports:/ { inallowed = 1; next }
    inimport && /^    [a-zA-Z]/ { inallowed = 0 }
    inimport && /^      - value: .\*./ { mode = "allow"; next }
    inimport && /^      - value: / { n++; if ($0 ~ /io\.github\.pt9912\.tdswire\./) forb = forb " " paket($0); next }
    inimport && inallowed && /^      - .io\.github\.pt9912\.tdswire\./ { allow = allow " " paket($0) }
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
    /source="detekt\.ForbiddenImport\// { match($0, /ForbiddenImport\/[a-z-]+/); print f, substr($0, RSTART, RLENGTH) }
  ' "$report" > "$funde"
  regeltabelle > "$tabelle"
  [ -s "$tabelle" ] || fail "keine ForbiddenImport-Regel mit includes in $yml"

  bekannt=$(ls "$td" | sort -u)
  pakete_regeln=$(cut -d'|' -f2 "$tabelle" | sort -u)
  pakete_testdaten=$(printf '%s\n' "$bekannt" | grep -vx runtime)
  [ "$pakete_regeln" = "$pakete_testdaten" ] || fail "Packages der Regeln ($(echo $pakete_regeln)) und der Testdaten unter config/detekt/selbsttest ohne runtime ($(echo $pakete_testdaten)) stimmen nicht ueberein"

  anzahl=0
  gemeldet=0
  while IFS='|' read -r name pkg modus verboten erlaubt n; do
    anzahl=$((anzahl + 1))
    for p in $verboten $erlaubt; do
      printf '%s\n' "$bekannt" | grep -qx "$p" || fail "Regel $name nennt das Package '$p', das es unter config/detekt/selbsttest nicht gibt"
    done
    [ -f "$td/$pkg/Allowed.kt" ] || fail "Regel $name hat kein Allowed.kt unter config/detekt/selbsttest"
    erwartet_fremd=0
    technik=0
    if [ "$name" != "$pkg" ]; then
      technik=1
      [ -f "$td/$pkg/Technik.kt" ] || fail "Regel $name braucht ein Technik.kt unter config/detekt/selbsttest"
      soll_technik=$(sed -n 's/^import //p' "$td/$pkg/Technik.kt" | sort -u)
      ist_technik=$(awk -v r="$name" '
        /^  ForbiddenImport\/[a-z-]+:/ { cur = $1; sub(/:$/, "", cur); sub(/.*\//, "", cur); next }
        /^  [A-Za-z]/ { cur = "" }
        cur == r && /^      - value: / { match($0, /\x27[^\x27]*\x27/); v = substr($0, RSTART + 1, RLENGTH - 2); sub(/\*$/, "Frame", v); print v }
      ' "$yml" | sort -u)
      [ "$soll_technik" = "$ist_technik" ] || fail "Regel $name: die Werte in detekt.yml ($(echo $ist_technik)) und die Imports von Technik.kt ($(echo $soll_technik)) sind verschieden - ein Eintrag fehlt oder ist zu viel"
    fi
    if [ "$technik" -eq 0 ]; then
      importiert=$(sed -n 's/^import io\.github\.pt9912\.tdswire\.\([a-z]*\)\..*/\1/p' "$td/$pkg/Allowed.kt")
      if [ "$modus" = allow ]; then
        [ -f "$td/$pkg/Fremd.kt" ] || fail "Regel $name hat eine Erlaubnisliste und braucht ein Fremd.kt unter config/detekt/selbsttest"
        erwartet_fremd=$(grep -c '^import ' "$td/$pkg/Fremd.kt" || true)
        soll=$(printf '%s\n' $importiert "$pkg" | sort -u)
        ist=$(printf '%s\n' $erlaubt | sort -u)
        [ "$soll" = "$ist" ] || fail "Regel $name: erlaubte Projekt-Packages ($(echo $ist)) und Imports von Allowed.kt plus das eigene Package ($(echo $soll)) sind verschieden - eine Lockerung oder ein fehlender Eintrag"
      else
        vereinigung=$(printf '%s\n' $verboten $importiert "$pkg" | sort -u)
        [ "$vereinigung" = "$bekannt" ] || fail "Regel $name: verbotene ($(echo $verboten)), erlaubte ($(echo $importiert)) und das eigene Package ergeben ($(echo $vereinigung)), die Testdaten kennen ($(echo $bekannt)) - ein Wert fehlt in detekt.yml oder in Allowed.kt"
      fi
    fi
    if [ "$technik" -eq 1 ]; then
      erwartet=$(grep -c '^import ' "$td/$pkg/Technik.kt" || true)
    else
      erwartet=$((n + erwartet_fremd))
    fi
    [ "$erwartet" -ge 1 ] || fail "Regel $name hat keinen verbotenen Wert"
    ist_funde=$(grep -cE "^(Forbidden_[^ ]*\.kt|Fremd\.kt|Technik\.kt) ForbiddenImport/$name\$" "$funde" || true)
    [ "$ist_funde" -eq "$erwartet" ] || fail "Regel ForbiddenImport/$name meldet $ist_funde von $erwartet verbotenen Importen ($n aus detekt.yml, $erwartet_fremd aus Fremd.kt) - includes, Package oder ein Wert greifen nicht"
    gemeldet=$((gemeldet + ist_funde))
  done < "$tabelle"

  falsch=$(grep -c '^Allowed\.kt ' "$funde" || true)
  [ "$falsch" -eq 0 ] || fail "$falsch erlaubte Importe gemeldet: $(grep '^Allowed\.kt ' "$funde" | tr '\n' ';')"

  echo "detekt-selbsttest: OK - $anzahl Importregeln, $gemeldet verbotene Importe gemeldet, 0 Fehlmeldungen bei erlaubten"
}

aufrufe() {
  [ -f "$report_aufrufe" ] || fail "kein Bericht unter $report_aufrufe - lief detektSelbsttestAufrufe?"
  funde=$(mktemp)
  # Zeile je Fund: Package, Dateiname, Methode
  awk '
    /<file name=/ { match($0, /name="[^"]*"/); f = substr($0, RSTART + 6, RLENGTH - 7); p = f; sub(/.*tdswire\//, "", p); sub(/\/.*/, "", p); sub(/.*\//, "", f); next }
    /source="detekt\.ForbiddenMethodCall"/ {
      match($0, /method `[^`]*`/)
      m = substr($0, RSTART + 8, RLENGTH - 9)
      gsub(/&lt;/, "<", m); gsub(/&gt;/, ">", m); gsub(/&amp;/, "\\&", m)
      print p, f, m
    }
  ' "$report_aufrufe" > "$funde"
  methoden=$(awk '/^  ForbiddenMethodCall:/ { i = 1; next } /^  [A-Za-z]/ { i = 0 } i && /^      - value: / { match($0, /\x27[^\x27]*\x27/); print substr($0, RSTART + 1, RLENGTH - 2) }' "$yml")
  [ -n "$methoden" ] || fail "kein Eintrag unter ForbiddenMethodCall in $yml"
  n=0
  for pkg in domain port; do
    marker=$(sed -n 's#^ *// erwartet: ##p' "$td_aufrufe/$pkg/Aufruf.kt" | sort -u)
    soll=$(printf '%s\n' $methoden | sort -u)
    [ "$marker" = "$soll" ] || fail "$pkg/Aufruf.kt erwartet ($(echo $marker)), detekt.yml fuehrt ($(echo $soll)) - ein Eintrag fehlt oder ist zu viel"
    for m in $methoden; do
      n=$((n + 1))
      grep -qF "$pkg Aufruf.kt $m" "$funde" || fail "ForbiddenMethodCall meldet '$m' in $pkg/Aufruf.kt nicht - der Eintrag greift nicht, oder die Beispiele rufen ihn nicht auf; gemeldet sind: $(grep "^$pkg Aufruf.kt " "$funde" | cut -d' ' -f3- | tr '\n' ';')"
    done
  done
  falsch=$(grep -c ' Erlaubt\.kt ' "$funde" || true)
  [ "$falsch" -eq 0 ] || fail "$falsch erlaubte Aufrufe gemeldet: $(grep ' Erlaubt\.kt ' "$funde" | tr '\n' ';')"
  echo "detekt-selbsttest: OK - $n Aufruf-Faelle gemeldet (je Eintrag in domain und port), 0 Fehlmeldungen bei erlaubten"
}

suppression() {
  skript=tools/suppression-check.sh
  d=build/suppression-test
  mkdir -p "$d/verboten" "$d/erlaubt"
  printf '@file:Suppress("X")\npackage a\n' > "$d/verboten/FileSuppress.kt"
  printf 'package a\n\n@Suppress("X")\nclass A\n' > "$d/verboten/Suppress.kt"
  printf 'package a\n\n@SuppressWarnings("X")\nclass B\n' > "$d/verboten/Warnings.kt"
  printf 'package a\n\n@kotlin.Suppress("X")\nclass C\n' > "$d/verboten/Qualified.kt"
  printf 'package a\n\n@get:Suppress("X")\nval d = 0\n' > "$d/verboten/Get.kt"
  printf 'package a\n\nclass E(@field:Suppress("X") val e: Int)\n' > "$d/verboten/Field.kt"
  printf '@file:[Suppress("X")]\npackage a\n' > "$d/verboten/FileList.kt"
  printf 'package a\n\n@[Suppress("X")]\nclass F\n' > "$d/verboten/List.kt"
  printf 'package a\n\n@[Deprecated("y") Suppress("X")]\nclass G\n' > "$d/verboten/ListLate.kt"
  printf 'package a\n\n@java.lang.SuppressWarnings("X")\nclass H\n' > "$d/verboten/Java.kt"
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
  aufrufe) aufrufe ;;
  suppression) suppression ;;
  *) echo "Aufruf: $0 generate|check|aufrufe|suppression" >&2; exit 2 ;;
esac
