#!/bin/sh
# spec-abdeckung.sh — erzeugt die Spezifikations-Abdeckungsmatrix (TDS-QA-05, TDS-QA-06).
#
# Eingaenge (Umgebung, Vorgaben in Klammern):
#   SPEC_DATEI    (spec/spezifikation.md)    Festlegungen: `### SPEC-<NNN> — <Titel>` und Tabellenzeilen `| `SPEC-<NNN>` | <Titel> | ...`
#   TEST_QUELLE   (src/test src/integrator testserver)  Verzeichnisse (durch Leerzeichen getrennt) mit Kotlin-Tests mit dem KDoc-Block-Tag `@spec SPEC-<NNN> [SPEC-<NNN> ...]`
#   BERICHTE      (build/test-results/test build/test-results/integratorTest testserver/build/test-results/test)  Verzeichnisse mit JUnit-XML-Berichten des Gradle-Testlaufs (TEST-*.xml)
#   CS_QUELLE     (referenztest-dotnet)      Verzeichnisse (durch Leerzeichen getrennt) mit C#-Tests mit dem Kommentar-Tag `// @spec SPEC-<NNN> [SPEC-<NNN> ...]`;
#                                            gesetzt und leer: keine C#-Tests lesen (dann ist auch kein Bericht noetig); bin/ und obj/ werden nicht gelesen
#   CS_BERICHTE   (build/dotnet-bericht)     Verzeichnisse mit TRX-Berichten (*.trx) des Laufs `dotnet test --logger trx` des .NET-Geruests
#   ZIEL          (docs/plan/spec-abdeckung.md)  die zu schreibende Matrix
#   WAISEN_DATEI  (leer)                     wenn gesetzt: dorthin die Zahl der Festlegungen ohne Nachweis
#   STRENG        (0)                        1: Exit 1, sobald eine Festlegung ohne Nachweis ist
#
# Konvention des Tags: eine KDoc-Zeile `* @spec SPEC-<NNN> [SPEC-<NNN> ...]` ueber der Testfunktion,
# Kennungen durch Leerzeichen getrennt (ein Komma macht die Kennung unbekannt). Mehrere `@spec`-Zeilen
# untereinander im selben Block sind erlaubt und addieren sich; eine Folgezeile ohne eigenes `@spec`
# gehoert nicht zum Tag. Der Tag haftet an der naechsten `fun` (Modifikatoren nur `private`/`internal`;
# bei `suspend fun` oder `override fun` haftet er an der naechsten passenden `fun`). Ein Einzeilen-KDoc
# `/** @spec ... */` wird nicht gelesen. Ein neuer KDoc-Block oder eine Klasse verwirft einen offenen Tag.
#
# Konvention des C#-Tags: eine Kommentarzeile `// @spec SPEC-<NNN> [SPEC-<NNN> ...]` ueber der Testmethode, Kennungen durch Leerzeichen
# getrennt; mehrere `// @spec`-Zeilen untereinander addieren sich. Zwischen Tag und Methode duerfen Leerzeilen, andere Kommentarzeilen und
# Attributzeilen stehen (`[Fact]`, `[Theory]`, `[InlineData(...)]`, `[Trait(...)]`; jedes Attribut in einer Zeile). Der Tag haftet an der
# ersten Zeile danach, die eine Methode mit Typ, Namen und `(` ist (ohne `=` vor der ersten `(`: eine Feld- oder Eigenschaftszeile mit
# Zuweisung ist keine Methode); jede andere Zeile (Feld, Klasse, Namensraum, ein Attribut ueber mehrere
# Zeilen) verwirft ihn. Der Testfall heisst `<Namensraum>.<Klasse>.<Methode>`: Namensraum der Zeile `namespace` (Datei- oder Blockform),
# Klasse die zuletzt gelesene `class`; geschachtelte Klassen (TRX: `Aussen+Innen`) sind nicht gelesen. Ein `[Theory]` zaehlt als bestanden,
# wenn alle seine Faelle bestanden sind (schlechtestes Ergebnis). Der Bericht der C#-Tests ist TRX (eingebauter Logger von `dotnet test`,
# kein Paket): `Passed` ist bestanden, `NotExecuted` uebersprungen, jedes andere Ergebnis rot. Ein getaggter C#-Test ohne Testfall im Bericht
# steht als „nicht gelaufen“; so stehen die zwei Tests des Abspielens (`LAUF=mitschnitt`, nur in `make vergleichslauf-d`), weil der Bericht
# nur die Stage `test-bericht` des .NET-Geruests kennt. Diese Stage teilt Quellen und Argumente von `dotnet test` mit der Stage `test`
# (Basis `test-quellen`, Variable DOTNET_TEST_ARGS in referenztest-dotnet/Dockerfile) und fuegt nur den TRX-Logger hinzu.
# Grenze: der C#-Tag haftet an der naechsten Methodenzeile, ob sie die gemeinte Testmethode ist, sieht der Erzeuger nicht; ein Tag ueber
# einer anderen vorhandenen und bestandenen Methode zaehlt fuer diese. Nur ein fehlender Name faellt als „nicht gelaufen“ auf.
# Die Art eines C#-Tests ist Smoke-Test unter `/Smoke/`, sonst Modul-Test.
#
# Ein Nachweis ist ein Test, dessen Tag die Kennung nennt UND dessen Klasse und Methode im Bericht
# ohne Fehler und ohne Uebersprung stehen; das Urteil lautet „mindestens ein bestandener getaggter
# Test“. Ein getaggter Test ohne bestandenes Ergebnis steht in eigenem Abschnitt der Matrix und zaehlt
# nicht. Kommt derselbe Testfall in mehreren Berichten vor, zaehlt das schlechteste Ergebnis.
# Exit 1: STRENG=1 und Festlegungen ohne Nachweis (`make` meldet dann Exit 2). Exit 2: Eingang fehlt oder leer.
# Gate-Klasse: eine Festlegung ist Gate-Festlegung (ungemessen, keine Waise), wenn die Titelzelle ein
# `make`-Target nennt oder die Zeile „Gate noch einzurichten“ traegt; Test-Anforderungen sind es nicht.
# Nennt irgendein Test die Kennung einer Gate-Festlegung, steht sie als Nachweis da, und die Klasse
# ist aus der Matrix nicht mehr zu sehen.
# Grenzen: der Tag sagt, welche Festlegung der Autor meint; ob die Assertions sie inhaltlich belegen,
# urteilt der Leser. Gate-Nachweise (ein Make-Target statt eines Tests) kennt der Erzeuger nicht.
# Eine Zeile `<failure` oder `<error` in der Ausgabe eines Testfalls (system-out) faerbt ihn rot.
# Die Namenszusage gilt gegen den Bericht: ein getaggter Test ohne gleichnamigen Testfall im Bericht
# steht als „nicht gelaufen“; ein frischer Lauf kennt den alten Namen nicht mehr.
#
# Der Erzeuger sieht dem TRX-Bericht sein Alter nicht an: `make spec-abdeckung` fuehrt die C#-Tests bei jedem Aufruf neu aus
# (`--no-cache-filter test-bericht`), damit der Layer-Cache keinen alten Lauf in die Matrix traegt.
#
# Aufgerufen von der Dockerfile-Stage `spec-abdeckung` und von tools/spec-abdeckung-selbsttest.sh;
# braucht sh, awk, sed, find, xargs, sort, cmp, mktemp.
set -eu

spec=${SPEC_DATEI:-spec/spezifikation.md}
quelle=${TEST_QUELLE:-src/test src/integrator testserver}
berichte=${BERICHTE:-build/test-results/test build/test-results/integratorTest testserver/build/test-results/test}
ziel=${ZIEL:-docs/plan/spec-abdeckung.md}
streng=${STRENG:-0}
csquelle=${CS_QUELLE-referenztest-dotnet}
csberichte=${CS_BERICHTE-build/dotnet-bericht}

fehler() { echo "spec-abdeckung: $*" >&2; exit 2; }

[ -f "$spec" ] || fehler "Spezifikation fehlt: $spec"
# Das Wort-Splitting der Verzeichnislisten ist beabsichtigt (Pfade ohne Leerzeichen).
for verzeichnis in $quelle; do [ -d "$verzeichnis" ] || fehler "Testquelle fehlt: $verzeichnis"; done
for verzeichnis in $berichte; do
  [ -d "$verzeichnis" ] || fehler "keine Testberichte unter $verzeichnis — der Testlauf hat keine geschrieben"
done
# shellcheck disable=SC2086
berichtsdateien=$(find $berichte -name 'TEST-*.xml' | sort)
[ -n "$berichtsdateien" ] || fehler "keine TEST-*.xml unter $berichte"

trxdateien=
if [ -n "$csquelle" ]; then
  for verzeichnis in $csquelle; do [ -d "$verzeichnis" ] || fehler "C#-Testquelle fehlt: $verzeichnis"; done
  for verzeichnis in $csberichte; do
    [ -d "$verzeichnis" ] || fehler "kein C#-Testbericht unter $verzeichnis — der Testlauf hat keinen geschrieben"
  done
  # shellcheck disable=SC2086
  trxdateien=$(find $csberichte -name '*.trx' | sort)
  [ -n "$trxdateien" ] || fehler "keine *.trx unter $csberichte"
fi

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

# S: Festlegungen der Spezifikation: Kennung, Titel, Klasse. Eine Festlegung ist eine Ueberschrift
# `### SPEC-<NNN> — <Titel>` oder eine Tabellenzeile, deren erste Zelle `SPEC-<NNN>` und deren zweite
# der Titel ist. Die Klasse ist `gate` bei einer Tabellenzeile, deren Titelzelle `make ` nennt oder die
# „Gate noch einzurichten“ traegt, sonst `test`; eine Kennung zaehlt beim ersten Auftreten.
awk '
  /^### SPEC-[0-9]+ — / {
    s = $0; sub(/^### /, "", s); id = s; sub(/ — .*$/, "", id); t = s; sub(/^[^ ]+ — /, "", t)
    if (!(id in da)) { da[id] = 1; printf "S\t%s\t%s\ttest\n", id, t }
    next
  }
  /^\| `SPEC-[0-9]+` \| / {
    n = split($0, z, " \\| "); id = z[1]; sub(/^\| `/, "", id); sub(/`$/, "", id)
    t = z[2]; sub(/ \|$/, "", t)
    klasse = (t ~ /`make / || $0 ~ /Gate noch einzurichten/) ? "gate" : "test"
    if (!(id in da)) { da[id] = 1; printf "S\t%s\t%s\t%s\n", id, t, klasse }
  }
' "$spec" > "$tmp/s.tsv"
[ -s "$tmp/s.tsv" ] || fehler "keine SPEC-Festlegungen in $spec"

# T: Tag je Test: Kennung, Klasse (voll qualifiziert), Methode, Art.
# Der Tag gehoert zur naechsten `fun` nach seinem KDoc-Block; ein neuer Block verwirft einen
# offenen Tag. Die Art folgt dem Verzeichnis: smoke/ und e2e/, sonst Modul-Test.
# shellcheck disable=SC2086
find $quelle -name '*.kt' | sort | xargs awk '
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

# T (C#): dieselben Spalten. Der Tag steht in `// @spec`-Zeilen und haftet an der ersten Methodenzeile danach; Attribut-, Kommentar- und
# Leerzeilen dazwischen halten ihn, jede andere Zeile verwirft ihn.
if [ -n "$csquelle" ]; then
  # shellcheck disable=SC2086
  find $csquelle \( -name bin -o -name obj \) -prune -o -name '*.cs' -print | sort | xargs -r awk '
    FNR == 1 { ns = ""; cls = ""; pend = "" }
    /^[ \t]*namespace[ \t]/ {
      s = $0; sub(/^[ \t]*namespace[ \t]+/, "", s); sub(/[ \t]*[;{].*$/, "", s); sub(/[ \t]+$/, "", s)
      ns = s; pend = ""; next
    }
    /^[ \t]*((public|internal|private|protected|sealed|static|abstract|partial)[ \t]+)*class[ \t]+[A-Za-z0-9_]+/ {
      s = $0; sub(/^[ \t]*((public|internal|private|protected|sealed|static|abstract|partial)[ \t]+)*class[ \t]+/, "", s); sub(/[^A-Za-z0-9_].*$/, "", s)
      cls = s; pend = ""; next
    }
    /^[ \t]*\/\/[ \t]*@spec[ \t]/ {
      s = $0; sub(/^[ \t]*\/\/[ \t]*@spec[ \t]+/, "", s); sub(/[ \t]+$/, "", s); pend = pend " " s; next
    }
    /^[ \t]*$/ { next }
    /^[ \t]*\[.*\][ \t]*$/ { next }
    /^[ \t]*\/\// { next }
    pend == "" { next }
    /\(/ {
      s = $0; sub(/\(.*$/, "", s); sub(/<[^>]*>[ \t]*$/, "", s); sub(/[ \t]+$/, "", s)
      if (s !~ /=/ && s ~ /[]A-Za-z0-9_>?][ \t]+[A-Za-z0-9_]+$/ && s !~ /^[ \t]*(if|for|foreach|while|switch|using|return|new|else|throw|await|var)[ \t(]/) {
        sub(/^.*[ \t]/, "", s)
        art = "Modul-Test"
        if (FILENAME ~ /\/Smoke\//) art = "Smoke-Test"
        n = split(pend, ids, /[ \t]+/)
        for (i = 1; i <= n; i++) if (ids[i] != "") printf "T\t%s\t%s.%s\t%s\t%s\n", ids[i], ns, cls, s, art
      }
    }
    { pend = "" }
  ' >> "$tmp/t.tsv"
fi

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

# R (C#): Ergebnis je Testfall der TRX-Berichte. `UnitTestResult` nennt Test-Id und Ergebnis, Klasse und Methode stehen im Block
# `UnitTest`/`TestMethod` unter derselben Id (ein `[Theory]` hat mehrere Ids mit derselben Klasse und Methode).
if [ -n "$csquelle" ]; then
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
    /<UnitTestResult / { nr++; rid[nr] = attr($0, "testId"); rerg[nr] = attr($0, "outcome") }
    /<UnitTest / { aktuell = attr($0, "id") }
    /<TestMethod / && aktuell != "" { dkl[aktuell] = attr($0, "className"); dme[aktuell] = attr($0, "name") }
    END {
      for (i = 1; i <= nr; i++) {
        if (!(rid[i] in dkl)) continue
        e = (rerg[i] == "Passed") ? "pass" : (rerg[i] == "NotExecuted" ? "skip" : "fail")
        printf "R\t%s\t%s\t%s\n", dkl[rid[i]], dme[rid[i]], e
      }
    }
  ' $trxdateien > "$tmp/r-cs.tsv"
  [ -s "$tmp/r-cs.tsv" ] || fehler "keine Testfaelle in den TRX-Berichten unter $csberichte"
  cat "$tmp/r-cs.tsv" >> "$tmp/r.tsv"
fi

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
    print "Erzeugt von `make spec-abdeckung` aus den `@spec`-Tags im KDoc der Testfunktionen unter `src/test`, `src/integrator` und `testserver`,"
    print "aus den `// @spec`-Kommentaren der C#-Tests unter `referenztest-dotnet`"
    print "und den Berichten des Testlaufs, der diese Matrix erzeugt hat (JUnit-XML des Gradle-Laufs, TRX der Stage `test-bericht` des .NET-Gerüsts). Die Festlegungen sind die"
    print "`SPEC-<NNN>`-Überschriften und die Tabellenzeilen mit `SPEC-<NNN>` in der ersten Zelle von"
    print "[`" spec "`](../../" spec ")."
    print ""
    print "Die Matrix belegt den Stand **jenes Testlaufs**, nicht eines Commits: sie nennt keinen Commit-Hash,"
    print "damit der Erzeuger deterministisch bleibt. Wer ihren Stand braucht, erzeugt sie neu."
    print ""
    print "**Konvention des Tags:** eine KDoc-Zeile `* @spec SPEC-<NNN> [SPEC-<NNN> ...]` über der Testfunktion,"
    print "Kennungen durch Leerzeichen getrennt (ein Komma macht die Kennung unbekannt). Mehrere `@spec`-Zeilen"
    print "untereinander im selben Block sind erlaubt und addieren sich. Der Tag haftet an der nächsten `fun`"
    print "(Modifikatoren nur `private`/`internal`; bei `suspend fun` oder `override fun` haftet er an der"
    print "nächsten passenden `fun`); ein Einzeilen-KDoc `/** @spec ... */` wird nicht gelesen; ein neuer"
    print "KDoc-Block oder eine Klasse verwirft einen offenen Tag."
    print ""
    print "**Konvention des C#-Tags:** eine Kommentarzeile `// @spec SPEC-<NNN> [SPEC-<NNN> ...]` über der Testmethode; Attributzeilen"
    print "(`[Fact]`, `[Theory]`, `[InlineData(...)]`), Leer- und Kommentarzeilen dazwischen halten ihn, jede andere Zeile verwirft ihn."
    print "Der Bericht der C#-Tests ist der der Stage `test-bericht` des .NET-Gerüsts (Quellen und Argumente wie die Stage `test`); ein getaggter C#-Test, den sie nicht"
    print "ausführt (die zwei Tests des Abspielens, die nur in `make vergleichslauf-d` laufen), steht als „nicht gelaufen“."
    print ""
    print "Ein **Nachweis** ist ein Test, dessen Tag die Kennung nennt und der im Bericht desselben Laufs"
    print "bestanden steht (Klasse und Methodenname stimmen überein); das Urteil lautet „mindestens ein"
    print "bestandener getaggter Test“. Ein getaggter Test, der nicht gelaufen, rot oder übersprungen ist,"
    print "zählt nicht und steht im Abschnitt „Getaggt, aber ohne bestandenes Ergebnis“. Ein getaggter Test,"
    print "zu dem der Bericht keinen gleichnamigen Testfall führt, steht als „nicht gelaufen“; belegbar ist"
    print "das nur gegen einen Bericht, der den Namen nicht kennt. Kommt ein Testfall in mehreren Berichten"
    print "vor, zählt das schlechteste Ergebnis. **Grenze:** der Tag nennt die Festlegung, die der Autor"
    print "meint; ob die Assertions sie inhaltlich belegen, urteilt der Leser. Eine Zeile `<failure` oder"
    print "`<error` in der Ausgabe eines Testfalls färbt ihn rot."
    print ""
    print "Ein Gate als Nachweis (ein Make-Target statt eines Tests) kennt der Erzeuger nicht: eine Festlegung,"
    print "deren Titelzelle ein `make`-Target nennt oder die „Gate noch einzurichten“ trägt, steht ohne"
    print "bestandenen Test als „Gate-Festlegung, ungemessen“ und ist weder Nachweis noch Waise. Nennt"
    print "irgendein Test ihre Kennung, steht sie als Nachweis da und die Klasse ist nicht mehr zu sehen."
    print "Die Art folgt dem Verzeichnis des Tests: `smoke/` (C#: `Smoke/`) Smoke-Test, `e2e/` E2E-Test, sonst Modul-Test."
    print ""
    printf "Stand: %d von %d Festlegungen mit mindestens einem bestandenen Test, %d Gate-Festlegungen ungemessen, %d ohne Nachweis.\n", ns - waisen - gates, ns, gates, waisen
    print ""
    print "## Matrix"
    print ""
    print "| Festlegung | Titel | Modul-Test | Smoke-Test | E2E-Test | Nicht bestanden | Belegt durch | Urteil |"
    print "| --- | --- | --- | --- | --- | --- | --- | --- |"
    for (j = 1; j <= ns; j++) {
      s = sid[j]
      printf "| `%s` | %s | %d | %d | %d | %d | %s | %s |\n", s, zelle(titel[s]), pass[s, "Modul-Test"], pass[s, "Smoke-Test"], pass[s, "E2E-Test"], verfehlt[s], (klliste[s] == "" ? "—" : zelle(klliste[s])), (gesamt[s] > 0 ? "mindestens ein bestandener getaggter Test" : (klasse[s] == "gate" ? "Gate-Festlegung, ungemessen" : "OHNE NACHWEIS"))
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
