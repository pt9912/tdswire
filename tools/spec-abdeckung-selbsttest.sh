#!/bin/sh
# spec-abdeckung-selbsttest.sh — belegt, dass tools/spec-abdeckung.sh nur bestandene Tests zaehlt
# und dass ein umbenannter, roter (failure und error), nicht gelaufener oder auf eine unbekannte
# Festlegung getaggter Test auffaellt. Er pinnt auch die Tag-Konvention: E2E-Art, mehrere @spec-Zeilen,
# Einzeilen-KDoc (nicht gelesen), `suspend fun` (Tag haftet an der naechsten passenden `fun`), das
# Verwerfen eines Tags durch einen neuen KDoc-Block, doppelte Berichte und die Gate-Klasse. Arbeitet an einer erzeugten Mini-Spezifikation mit Mini-Tests und Mini-Berichten.
#
# Aufgerufen von der Dockerfile-Stage `spec-abdeckung-selbsttest`; braucht sh, awk, sed, grep, mktemp.
set -eu

erzeuger=$(dirname "$0")/spec-abdeckung.sh
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/q/a/modul" "$tmp/q/a/smoke" "$tmp/q/a/e2e" "$tmp/b"

cat > "$tmp/spec.md" <<'FIX'
### SPEC-001 — Gruen belegt
### SPEC-002 — Nur rot getestet
### SPEC-003 — Ohne Test
### SPEC-004 — Test umbenannt
### SPEC-005 — Nur uebersprungen
### SPEC-006 — Smoke belegt
### SPEC-009 — Error im Bericht
### SPEC-010 — E2E belegt
### SPEC-011 — Einzeilen-KDoc
### SPEC-012 — Mehrzeiliger Tag A
### SPEC-013 — Mehrzeiliger Tag B
### SPEC-014 — Tag an suspend fun
### SPEC-015 — Doppelter Bericht
### SPEC-016 — Verworfener Tag
## 3. Konstanten
| `SPEC-007` | `LIMIT` | 5 | Tabellenzeile ohne Test |
## 7. Festlegungen der Harness-Werkzeuge
| `SPEC-008` | Static-Analysis-Gate (`make detekt`) | Gate ohne getaggten Test |
| `SPEC-017` | Fuzz-Tests | Test-Anforderung im Werkzeug-Abschnitt; Gate: `make test` |
| `SPEC-018` | Referenztest-Gate | Gate noch einzurichten |
FIX

cat > "$tmp/q/a/modul/FixTest.kt" <<'FIX'
package fix.modul

class FixTest {
    /**
     * Orakel.
     *
     * @spec SPEC-001
     */
    @Test
    fun `gruener Test`() {}

    /**
     * @spec SPEC-002 SPEC-001
     */
    @Test
    fun rotTest() {}

    /**
     * @spec SPEC-004
     */
    @Test
    fun `neuer Name`() {}

    /**
     * @spec SPEC-005
     */
    @Test
    fun uebersprungen() {}

    /**
     * @spec SPEC-099
     */
    @Test
    fun `Tag auf unbekannte Kennung`() {}
}
FIX

cat > "$tmp/q/a/modul/ZusatzTest.kt" <<'FIX'
package fix.modul

class ZusatzTest {
    /**
     * @spec SPEC-009
     */
    @Test
    fun fehlerTest() {}

    /** @spec SPEC-011 */
    @Test
    fun einzeilig() {}

    /**
     * @spec SPEC-012
     * @spec SPEC-013
     */
    @Test
    fun `mehrzeilig`() {}

    /**
     * @spec SPEC-014
     */
    @Test
    suspend fun haftetNichtHier() {}

    @Test
    fun trifftDenTag() {}

    /**
     * @spec SPEC-015
     */
    @Test
    fun doppelt() {}

    /**
     * @spec SPEC-016
     */

    /**
     * Neuer Block ohne Tag.
     */
    @Test
    fun verwaist() {}
}
FIX

cat > "$tmp/q/a/e2e/EndeTest.kt" <<'FIX'
package fix.e2e

class EndeTest {
    /**
     * @spec SPEC-010
     */
    @Test
    fun ende() {}
}
FIX

cat > "$tmp/q/a/smoke/RauchTest.kt" <<'FIX'
package fix.smoke

class RauchTest {
    /**
     * @spec SPEC-006
     */
    @Test
    fun rauch() {}
}
FIX

cat > "$tmp/b/TEST-fix.modul.FixTest.xml" <<'FIX'
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="fix.modul.FixTest" tests="4" skipped="1" failures="1" errors="0">
  <testcase name="gruener Test()" classname="fix.modul.FixTest" time="0.001"/>
  <testcase name="rotTest()" classname="fix.modul.FixTest" time="0.001">
    <failure message="x" type="AssertionError">trace</failure>
  </testcase>
  <testcase name="Tag auf unbekannte Kennung()" classname="fix.modul.FixTest" time="0.001"/>
  <testcase name="uebersprungen()" classname="fix.modul.FixTest" time="0.001">
    <skipped/>
  </testcase>
</testsuite>
FIX

cat > "$tmp/b/TEST-fix.smoke.RauchTest.xml" <<'FIX'
<testsuite name="fix.smoke.RauchTest" tests="1" skipped="0" failures="0" errors="0">
  <testcase name="rauch()" classname="fix.smoke.RauchTest" time="0.001"/>
</testsuite>
FIX

cat > "$tmp/b/TEST-fix.modul.ZusatzTest.xml" <<'FIX'
<testsuite name="fix.modul.ZusatzTest" tests="5" skipped="0" failures="0" errors="1">
  <testcase name="fehlerTest()" classname="fix.modul.ZusatzTest" time="0.001">
    <error message="x" type="IllegalStateException">trace</error>
  </testcase>
  <testcase name="einzeilig()" classname="fix.modul.ZusatzTest" time="0.001"/>
  <testcase name="mehrzeilig()" classname="fix.modul.ZusatzTest" time="0.001"/>
  <testcase name="trifftDenTag()" classname="fix.modul.ZusatzTest" time="0.001"/>
  <testcase name="verwaist()" classname="fix.modul.ZusatzTest" time="0.001"/>
</testsuite>
FIX

# Derselbe Testfall in drei Berichten (bestanden, rot, bestanden): das Ergebnis haengt nicht von der Dateireihenfolge ab.
for lauf_nr in A B C; do
  { echo '<testsuite name="fix.modul.ZusatzTest" tests="1" skipped="0" failures="0" errors="0">'
    if [ "$lauf_nr" = B ]; then
      echo '  <testcase name="doppelt()" classname="fix.modul.ZusatzTest" time="0.001">'
      echo '    <failure message="x" type="AssertionError">trace</failure>'
      echo '  </testcase>'
    else
      echo '  <testcase name="doppelt()" classname="fix.modul.ZusatzTest" time="0.001"/>'
    fi
    echo '</testsuite>'; } > "$tmp/b/TEST-fix.modul.Doppelt$lauf_nr.xml"
done

cat > "$tmp/b/TEST-fix.e2e.EndeTest.xml" <<'FIX'
<testsuite name="fix.e2e.EndeTest" tests="1" skipped="0" failures="0" errors="0">
  <testcase name="ende()" classname="fix.e2e.EndeTest" time="0.001"/>
</testsuite>
FIX

lauf() {
  SPEC_DATEI="$tmp/spec.md" TEST_QUELLE="$tmp/q" BERICHTE="$tmp/b" ZIEL="$tmp/m.md" "$@" sh "$erzeuger" > "$tmp/aus.txt" 2>&1
}

erwarte() { # erwarte <Muster> <Meldung>
  grep -q -- "$1" "$tmp/m.md" || { echo "spec-abdeckung-selbsttest: FEHLT: $2" >&2; cat "$tmp/m.md" >&2; exit 1; }
}
verbiete() { # verbiete <Muster> <Meldung>
  if grep -q -- "$1" "$tmp/m.md"; then echo "spec-abdeckung-selbsttest: ZUVIEL: $2" >&2; cat "$tmp/m.md" >&2; exit 1; fi
}

lauf env
erwarte '^| `SPEC-001` | Gruen belegt | 1 | 0 | 0 | 1 | FixTest | mindestens ein bestandener getaggter Test |$' "SPEC-001 durch den gruenen Test belegt, der rote mit SPEC-001 steht unter Nicht bestanden"
erwarte '^| `SPEC-002` .*| 0 | 0 | 0 | 1 | — | OHNE NACHWEIS |$' "ein roter Test belegt nichts"
erwarte '^| `SPEC-003` .*| OHNE NACHWEIS |$' "eine Festlegung ohne Test ist sichtbar"
erwarte '^| `SPEC-004` .*| 0 | 0 | 0 | 1 | — | OHNE NACHWEIS |$' "ein Test ohne Ergebnis im Bericht (umbenannt) belegt nichts"
erwarte '^| `SPEC-004` | `fix.modul.FixTest.neuer Name` | nicht gelaufen' "der umbenannte Test steht als nicht gelaufen"
erwarte '^| `SPEC-002` | `fix.modul.FixTest.rotTest` | rot |$' "der rote Test steht als rot"
erwarte '^| `SPEC-005` | `fix.modul.FixTest.uebersprungen` | übersprungen |$' "ein uebersprungener Test steht als uebersprungen"
erwarte '^| `SPEC-006` .*| 0 | 1 | 0 | 0 | RauchTest | mindestens ein bestandener getaggter Test |$' "die Art folgt dem Verzeichnis smoke/"
erwarte '^| `SPEC-099` | `fix.modul.FixTest.Tag auf unbekannte Kennung` |$' "ein Tag auf eine unbekannte Kennung steht im eigenen Abschnitt"
erwarte 'Stand: 6 von 18 Festlegungen .* 2 Gate-Festlegungen ungemessen, 10 ohne Nachweis' "die Zaehlung trennt Waisen und Gate-Festlegungen"
erwarte '^| `SPEC-007` | `LIMIT` | 0 .*| OHNE NACHWEIS |$' "eine Tabellenzeile ist eine Festlegung"
erwarte '^| `SPEC-008` | Static-Analysis-Gate (`make detekt`) .*| Gate-Festlegung, ungemessen |$' "ein make-Target in der Titelzelle macht eine Gate-Festlegung"
erwarte '^| `SPEC-018` .*| Gate-Festlegung, ungemessen |$' "„Gate noch einzurichten“ macht eine Gate-Festlegung"
erwarte '^| `SPEC-017` .*| OHNE NACHWEIS |$' "eine Test-Anforderung im Werkzeug-Abschnitt ist eine Waise, auch wenn ihr Text ein make-Target nennt"
erwarte '^| `SPEC-009` .*| 0 | 0 | 0 | 1 | — | OHNE NACHWEIS |$' "ein <error> im Bericht ist rot"
erwarte '^| `SPEC-009` | `fix.modul.ZusatzTest.fehlerTest` | rot |$' "der Test mit <error> steht als rot"
erwarte '^| `SPEC-010` .*| 0 | 0 | 1 | 0 | EndeTest | mindestens ein bestandener getaggter Test |$' "die Art folgt dem Verzeichnis e2e/"
erwarte '^| `SPEC-011` .*| 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |$' "ein Einzeilen-KDoc wird nicht gelesen"
erwarte '^| `SPEC-012` .*| 1 | 0 | 0 | 0 | ZusatzTest | mindestens ein bestandener getaggter Test |$' "mehrere @spec-Zeilen: erste Kennung"
erwarte '^| `SPEC-013` .*| 1 | 0 | 0 | 0 | ZusatzTest | mindestens ein bestandener getaggter Test |$' "mehrere @spec-Zeilen: zweite Kennung"
erwarte '^| `SPEC-014` .*| 1 | 0 | 0 | 0 | ZusatzTest | mindestens ein bestandener getaggter Test |$' "bei suspend fun haftet der Tag an der naechsten passenden fun (Ist-Verhalten, Grenze im Kopf des Erzeugers)"
erwarte '^| `SPEC-015` .*| 0 | 0 | 0 | 1 | — | OHNE NACHWEIS |$' "bei doppeltem Bericht zaehlt das schlechteste Ergebnis"
erwarte '^| `SPEC-016` .*| 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |$' "ein neuer KDoc-Block verwirft einen offenen Tag"
verbiete '`SPEC-016` | `fix' "der verworfene Tag steht in keinem Abschnitt"
echo "spec-abdeckung-selbsttest: Zaehlung gruen"

lauf env STRENG=1 && { echo "spec-abdeckung-selbsttest: STRENG=1 haette bei Waisen Exit 1 liefern muessen" >&2; exit 1; }
lauf env STRENG=0 || { echo "spec-abdeckung-selbsttest: ohne STRENG darf der Erzeuger nicht brechen" >&2; exit 1; }
echo "spec-abdeckung-selbsttest: Schalter gruen"

# Umbenennen im Quelltext: der Bericht kennt den alten Namen, der Nachweis verschwindet.
sed 's/`gruener Test`/`umbenannter Test`/' "$tmp/q/a/modul/FixTest.kt" > "$tmp/neu.kt"
cp "$tmp/neu.kt" "$tmp/q/a/modul/FixTest.kt"
lauf env
erwarte '^| `SPEC-001` .*| 0 | 0 | 0 | 2 | — | OHNE NACHWEIS |$' "ein umbenannter Test verliert den Nachweis"
echo "spec-abdeckung-selbsttest: Umbenennung gruen"

# Leere Berichte: der Erzeuger bricht ab, statt eine leere Matrix als Befund auszugeben.
rm "$tmp/b"/*.xml
lauf env && { echo "spec-abdeckung-selbsttest: ohne Berichte muss der Erzeuger Exit 2 liefern" >&2; exit 1; }
echo "spec-abdeckung-selbsttest: gruen"
