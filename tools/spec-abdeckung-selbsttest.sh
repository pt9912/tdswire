#!/bin/sh
# spec-abdeckung-selbsttest.sh — belegt, dass tools/spec-abdeckung.sh nur bestandene Tests zaehlt
# und dass ein umbenannter, roter, nicht gelaufener oder auf eine unbekannte Festlegung getaggter
# Test auffaellt. Arbeitet an einer erzeugten Mini-Spezifikation mit Mini-Tests und Mini-Berichten.
#
# Aufgerufen von der Dockerfile-Stage `spec-abdeckung-selbsttest`; braucht sh, awk, sed, grep, mktemp.
set -eu

erzeuger=$(dirname "$0")/spec-abdeckung.sh
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/q/a/modul" "$tmp/q/a/smoke" "$tmp/b"

cat > "$tmp/spec.md" <<'FIX'
### SPEC-001 — Gruen belegt
### SPEC-002 — Nur rot getestet
### SPEC-003 — Ohne Test
### SPEC-004 — Test umbenannt
### SPEC-005 — Nur uebersprungen
### SPEC-006 — Smoke belegt
## 3. Konstanten
| `SPEC-007` | `LIMIT` | 5 | Tabellenzeile ohne Test |
## 7. Festlegungen der Harness-Werkzeuge
| `SPEC-008` | Unit-Test-Gate | `make test` | Gate ohne getaggten Test |
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
erwarte '^| `SPEC-001` | Gruen belegt | 1 | 0 | 0 | 1 | FixTest | nachgewiesen |$' "SPEC-001 durch den gruenen Test belegt, der rote mit SPEC-001 steht unter Nicht bestanden"
erwarte '^| `SPEC-002` .*| 0 | 0 | 0 | 1 | — | OHNE NACHWEIS |$' "ein roter Test belegt nichts"
erwarte '^| `SPEC-003` .*| OHNE NACHWEIS |$' "eine Festlegung ohne Test ist sichtbar"
erwarte '^| `SPEC-004` .*| 0 | 0 | 0 | 1 | — | OHNE NACHWEIS |$' "ein Test ohne Ergebnis im Bericht (umbenannt) belegt nichts"
erwarte '^| `SPEC-004` | `fix.modul.FixTest.neuer Name` | nicht gelaufen' "der umbenannte Test steht als nicht gelaufen"
erwarte '^| `SPEC-002` | `fix.modul.FixTest.rotTest` | rot |$' "der rote Test steht als rot"
erwarte '^| `SPEC-005` | `fix.modul.FixTest.uebersprungen` | übersprungen |$' "ein uebersprungener Test steht als uebersprungen"
erwarte '^| `SPEC-006` .*| 0 | 1 | 0 | 0 | RauchTest | nachgewiesen |$' "die Art folgt dem Verzeichnis smoke/"
erwarte '^| `SPEC-099` | `fix.modul.FixTest.Tag auf unbekannte Kennung` |$' "ein Tag auf eine unbekannte Kennung steht im eigenen Abschnitt"
erwarte 'Stand: 2 von 8 Festlegungen .* 1 Gate-Festlegungen ungemessen, 5 ohne Nachweis' "die Zaehlung trennt Waisen und Gate-Festlegungen"
erwarte '^| `SPEC-007` | `LIMIT` | 0 .*| OHNE NACHWEIS |$' "eine Tabellenzeile ist eine Festlegung"
erwarte '^| `SPEC-008` | Unit-Test-Gate .*| Gate-Festlegung, ungemessen |$' "eine Gate-Festlegung ist weder Nachweis noch Waise"
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
