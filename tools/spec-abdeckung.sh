#!/bin/sh
# spec-abdeckung.sh — erzeugt die Spezifikations-Abdeckungsmatrix (TDS-QA-05, TDS-QA-06).
#
# Eingaenge (Umgebung, Vorgaben in Klammern):
#   SPEC_DATEI    (spec/spezifikation.md)    Festlegungen: `### SPEC-<NNN> — <Titel>` und Tabellenzeilen `| `SPEC-<NNN>` | <Titel> | ...`
#   TEST_QUELLE   (src/test)                 Kotlin-Tests mit dem KDoc-Block-Tag `@spec SPEC-<NNN> ...`
#   BERICHTE      (build/test-results/test)  JUnit-XML-Berichte des Gradle-Testlaufs (TEST-*.xml)
#   ZIEL          (docs/plan/spec-abdeckung.md)  die zu schreibende Matrix
#   WAISEN_DATEI  (leer)                     wenn gesetzt: dorthin die Zahl der Festlegungen ohne Nachweis
#   STRENG        (0)                        1: Exit 1, sobald eine Festlegung ohne Nachweis ist
#
# Ein Nachweis ist ein Test, dessen Tag die Kennung nennt UND dessen Klasse und Methode im Bericht
# ohne Fehler und ohne Uebersprung stehen. Ein getaggter Test ohne bestandenes Ergebnis steht in
# eigenem Abschnitt der Matrix und zaehlt nicht. Exit 2: Eingang fehlt oder leer.
# Grenze: der Tag sagt, welche Festlegung der Autor meint; ob die Assertions sie inhaltlich
# belegen, urteilt der Leser. Gate-Nachweise (ein Make-Target statt eines Tests) kennt der Erzeuger nicht.
#
# Aufgerufen von der Dockerfile-Stage `spec-abdeckung` und von tools/spec-abdeckung-selbsttest.sh;
# braucht sh, awk, sed, find, xargs, sort, cmp, mktemp.
set -eu

spec=${SPEC_DATEI:-spec/spezifikation.md}
quelle=${TEST_QUELLE:-src/test}
berichte=${BERICHTE:-build/test-results/test}
ziel=${ZIEL:-docs/plan/spec-abdeckung.md}
streng=${STRENG:-0}

fehler() { echo "spec-abdeckung: $*" >&2; exit 2; }

[ -f "$spec" ] || fehler "Spezifikation fehlt: $spec"
[ -d "$quelle" ] || fehler "Testquelle fehlt: $quelle"
[ -d "$berichte" ] || fehler "keine Testberichte unter $berichte — der Testlauf hat keine geschrieben"
berichtsdateien=$(find "$berichte" -name 'TEST-*.xml' | sort)
[ -n "$berichtsdateien" ] || fehler "keine TEST-*.xml unter $berichte"

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

# S: Festlegungen der Spezifikation: Kennung, Titel, Klasse. Eine Festlegung ist eine Ueberschrift
# `### SPEC-<NNN> — <Titel>` oder eine Tabellenzeile, deren erste Zelle `SPEC-<NNN>` und deren zweite
# der Titel ist. Die Klasse ist `gate` in dem `## `-Abschnitt, dessen Ueberschrift GATE_ABSCHNITT
# (Vorgabe: Werkzeuge) trifft, sonst `test`; eine Kennung zaehlt beim ersten Auftreten.
awk -v gate="${GATE_ABSCHNITT:-Werkzeuge}" '
  /^## / { klasse = ($0 ~ gate) ? "gate" : "test" }
  /^### SPEC-[0-9]+ — / {
    s = $0; sub(/^### /, "", s); id = s; sub(/ — .*$/, "", id); t = s; sub(/^[^ ]+ — /, "", t)
    if (!(id in da)) { da[id] = 1; printf "S\t%s\t%s\t%s\n", id, t, klasse }
    next
  }
  /^\| `SPEC-[0-9]+` \| / {
    n = split($0, z, " \\| "); id = z[1]; sub(/^\| `/, "", id); sub(/`$/, "", id)
    t = z[2]; sub(/ \|$/, "", t)
    if (!(id in da)) { da[id] = 1; printf "S\t%s\t%s\t%s\n", id, t, klasse }
  }
' "$spec" > "$tmp/s.tsv"
[ -s "$tmp/s.tsv" ] || fehler "keine SPEC-Festlegungen in $spec"

# T: Tag je Test: Kennung, Klasse (voll qualifiziert), Methode, Art.
# Der Tag gehoert zur naechsten `fun` nach seinem KDoc-Block; ein neuer Block verwirft einen
# offenen Tag. Die Art folgt dem Verzeichnis: smoke/ und e2e/, sonst Modul-Test.
find "$quelle" -name '*.kt' | sort | xargs awk '
  FNR == 1 { pkg = ""; cls = ""; pend = "" }
  /^package / { pkg = $2; next }
  /^(internal |private |public |open )*class [A-Za-z0-9_]+/ {
    s = $0; sub(/^(internal |private |public |open )*class /, "", s); sub(/[^A-Za-z0-9_].*$/, "", s)
    cls = s; pend = ""; next
  }
  /\/\*\*/ { pend = "" }
  /^[ \t]*\*[ \t]*@spec[ \t]/ {
    s = $0; sub(/^[ \t]*\*[ \t]*@spec[ \t]+/, "", s); sub(/[ \t]+$/, "", s); pend = pend " " s; next
  }
  /^[ \t]*(private |internal )?fun / && pend != "" {
    s = $0; sub(/^[ \t]*(private |internal )?fun /, "", s)
    if (substr(s, 1, 1) == "`") { s = substr(s, 2); sub(/`.*$/, "", s) } else { sub(/[^A-Za-z0-9_].*$/, "", s) }
    art = "Modul-Test"
    if (FILENAME ~ /\/smoke\//) art = "Smoke-Test"
    if (FILENAME ~ /\/e2e\//) art = "E2E-Test"
    n = split(pend, ids, /[ \t]+/)
    for (i = 1; i <= n; i++) if (ids[i] != "") printf "T\t%s\t%s.%s\t%s\t%s\n", ids[i], pkg, cls, s, art
    pend = ""
  }
' > "$tmp/t.tsv"

# R: Ergebnis je Testfall der Berichte: Klasse, Methode, pass | fail | skip.
# Gradle haengt `()` an den Methodennamen; ein Testfall ohne Kindelement ist bestanden.
# Das Wort-Splitting der Dateiliste ist beabsichtigt (Pfade ohne Leerzeichen).
# shellcheck disable=SC2086
awk '
  function entity(s) {
    gsub(/&lt;/, "<", s); gsub(/&gt;/, ">", s); gsub(/&quot;/, "\"", s); gsub(/&apos;/, "\047", s); gsub(/&amp;/, "\\&", s)
    return s
  }
  function attr(zeile, name,   s) {
    if (!match(zeile, " " name "=\"[^\"]*\"")) return ""
    s = substr(zeile, RSTART + length(name) + 3, RLENGTH - length(name) - 4)
    return entity(s)
  }
  function flush() { if (name != "") printf "R\t%s\t%s\t%s\n", cls, name, erg; name = "" }
  /<testcase / {
    flush(); name = attr($0, "name"); cls = attr($0, "classname"); sub(/\(\)$/, "", name); erg = "pass"
    if ($0 ~ /\/>[ \t]*$/) flush()
    next
  }
  name != "" && /<(failure|error)/ { erg = "fail" }
  name != "" && /<skipped/ { if (erg == "pass") erg = "skip" }
  /<\/testcase>/ { flush() }
' $berichtsdateien > "$tmp/r.tsv"
[ -s "$tmp/r.tsv" ] || fehler "keine Testfaelle in den Berichten unter $berichte"

cat "$tmp/s.tsv" "$tmp/t.tsv" "$tmp/r.tsv" | awk -F '\t' -v spec="$spec" '
  function zelle(s) { gsub(/\|/, "\\|", s); return s }
  function rang(e) { return e == "fail" ? 3 : (e == "skip" ? 2 : (e == "pass" ? 1 : 0)) }
  $1 == "S" { ns++; sid[ns] = $2; titel[$2] = $3; klasse[$2] = $4; bekannt[$2] = 1; next }
  $1 == "T" { nt++; tid[nt] = $2; tkl[nt] = $3; tme[nt] = $4; tart[nt] = $5; next }
  $1 == "R" { k = $2 SUBSEP $3; if (rang($4) > rang(erg[k])) erg[k] = $4; next }
  END {
    for (i = 1; i <= nt; i++) {
      e = erg[tkl[i] SUBSEP tme[i]]
      if (e == "pass") {
        pass[tid[i], tart[i]]++; gesamt[tid[i]]++
        k = tkl[i]; sub(/^.*\./, "", k)
        if (!((tid[i], k) in gesehen)) { gesehen[tid[i], k] = 1; klliste[tid[i]] = klliste[tid[i]] (klliste[tid[i]] == "" ? "" : ", ") k }
      } else {
        verfehlt[tid[i]]++
        if (bekannt[tid[i]]) {
          nv++
          vtext[nv] = sprintf("| `%s` | `%s` | %s |", tid[i], zelle(tkl[i] "." tme[i]), (e == "" ? "nicht gelaufen (kein Ergebnis im Bericht)" : (e == "fail" ? "rot" : "übersprungen")))
        }
      }
      if (!bekannt[tid[i]]) { nu++; utext[nu] = sprintf("| `%s` | `%s` |", tid[i], zelle(tkl[i] "." tme[i])) }
    }
    waisen = 0; gates = 0
    for (j = 1; j <= ns; j++) if (gesamt[sid[j]] == 0) { if (klasse[sid[j]] == "gate") gates++; else waisen++ }
    print "# Abdeckung der Festlegungen der Spezifikation durch Tests"
    print ""
    print "Erzeugt von `make spec-abdeckung` aus den `@spec`-Tags im KDoc der Testfunktionen unter `src/test`"
    print "und den JUnit-Berichten des Testlaufs, der diese Matrix erzeugt hat. Die Festlegungen sind die"
    print "`SPEC-<NNN>`-Überschriften und die Tabellenzeilen mit `SPEC-<NNN>` in der ersten Zelle von"
    print "[`" spec "`](../../" spec ")."
    print ""
    print "Ein **Nachweis** ist ein Test, dessen Tag die Kennung nennt und der im Bericht desselben Laufs"
    print "bestanden steht (Klasse und Methodenname stimmen überein). Ein getaggter Test, der nicht gelaufen,"
    print "rot oder übersprungen ist, zählt nicht und steht im Abschnitt „Getaggt, aber ohne bestandenes"
    print "Ergebnis“. Ein umbenannter Test verliert seinen Nachweis, bis sein Name wieder zu einem Testfall"
    print "im Bericht passt. **Grenze:** der Tag nennt die Festlegung, die der Autor meint; ob die"
    print "Assertions sie inhaltlich belegen, urteilt der Leser. Ein Gate als Nachweis (ein Make-Target statt"
    print "eines Tests) kennt der Erzeuger nicht: eine Festlegung aus dem Abschnitt der Harness-Werkzeuge ohne"
    print "bestandenen Test steht als „Gate-Festlegung, ungemessen“ und ist weder Nachweis noch Waise. Die Art"
    print "folgt dem Verzeichnis des Tests: `smoke/` Smoke-Test, `e2e/` E2E-Test, sonst Modul-Test."
    print ""
    printf "Stand: %d von %d Festlegungen mit mindestens einem bestandenen Test, %d Gate-Festlegungen ungemessen, %d ohne Nachweis.\n", ns - waisen - gates, ns, gates, waisen
    print ""
    print "## Matrix"
    print ""
    print "| Festlegung | Titel | Modul-Test | Smoke-Test | E2E-Test | Nicht bestanden | Belegt durch | Urteil |"
    print "| --- | --- | --- | --- | --- | --- | --- | --- |"
    for (j = 1; j <= ns; j++) {
      s = sid[j]
      printf "| `%s` | %s | %d | %d | %d | %d | %s | %s |\n", s, zelle(titel[s]), pass[s, "Modul-Test"], pass[s, "Smoke-Test"], pass[s, "E2E-Test"], verfehlt[s], (klliste[s] == "" ? "—" : zelle(klliste[s])), (gesamt[s] > 0 ? "nachgewiesen" : (klasse[s] == "gate" ? "Gate-Festlegung, ungemessen" : "OHNE NACHWEIS"))
    }
    print ""
    print "## Getaggt, aber ohne bestandenes Ergebnis"
    print ""
    if (nv == 0) print "Keiner."
    else {
      print "| Festlegung | Test | Grund |"
      print "| --- | --- | --- |"
      for (i = 1; i <= nv; i++) print vtext[i]
    }
    print ""
    print "## Tags auf unbekannte Festlegungen"
    print ""
    if (nu == 0) print "Keine."
    else {
      print "| Tag | Test |"
      print "| --- | --- |"
      for (i = 1; i <= nu; i++) print utext[i]
    }
  }
' > "$tmp/matrix.md"

mkdir -p "$(dirname "$ziel")"
if [ -f "$ziel" ] && cmp -s "$tmp/matrix.md" "$ziel"; then
  echo "spec-abdeckung: $ziel unveraendert"
else
  cp "$tmp/matrix.md" "$ziel"
  echo "spec-abdeckung: $ziel geschrieben"
fi

waisen=$(grep -c 'OHNE NACHWEIS' "$tmp/matrix.md" || true)
echo "spec-abdeckung: $waisen Festlegung(en) ohne Nachweis"
if [ -n "${WAISEN_DATEI:-}" ]; then
  echo "$waisen" > "$WAISEN_DATEI"
fi
if [ "$streng" = 1 ] && [ "$waisen" -gt 0 ]; then
  echo "spec-abdeckung: STRENG=1 und Festlegungen ohne Nachweis" >&2
  exit 1
fi
