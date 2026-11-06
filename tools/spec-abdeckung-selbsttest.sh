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
  SPEC_DATEI="$tmp/spec.md" TEST_QUELLE="$tmp/q" BERICHTE="$tmp/b" CS_QUELLE= CS_BERICHTE= ZIEL="$tmp/m.md" "$@" sh "${erzeuger_lauf:-$erzeuger}" > "$tmp/aus.txt" 2>&1
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

# Zweites Verzeichnis von Quelle und Bericht (src/integrator): ein bestandener Test dort belegt eine Festlegung.
mkdir -p "$tmp/q2/b" "$tmp/b2"
cat > "$tmp/q2/b/EinbettungTest.kt" <<'FIX'
package fix.einbettung

class EinbettungTest {
    /**
     * @spec SPEC-003
     */
    @Test
    fun `von aussen`() {}
}
FIX
cat > "$tmp/b2/TEST-fix.einbettung.EinbettungTest.xml" <<'FIX'
<testsuite name="fix.einbettung.EinbettungTest" tests="1" skipped="0" failures="0" errors="0">
  <testcase name="von aussen()" classname="fix.einbettung.EinbettungTest" time="0.001"/>
</testsuite>
FIX
lauf env TEST_QUELLE="$tmp/q $tmp/q2" BERICHTE="$tmp/b $tmp/b2"
erwarte '^| `SPEC-003` .*| 1 | 0 | 0 | 0 | EinbettungTest | mindestens ein bestandener getaggter Test |$' "ein Test im zweiten Quellverzeichnis mit Bericht im zweiten Berichtsverzeichnis belegt"
lauf env TEST_QUELLE="$tmp/q $tmp/q2" BERICHTE="$tmp/b"
erwarte '^| `SPEC-003` .*| OHNE NACHWEIS |$' "ohne den Bericht des zweiten Verzeichnisses zaehlt der Test dort nicht"
echo "spec-abdeckung-selbsttest: zwei Verzeichnisse gruen"

# C#-Tests: Mini-Bestand mit Kommentar-Tags und einem TRX-Bericht (Aufbau wie ihn `dotnet test --logger trx` schreibt:
# `UnitTestResult` mit Test-Id und Ergebnis, `UnitTest`/`TestMethod` mit Klasse und Methode unter derselben Id).
mkdir -p "$tmp/cs/Mini" "$tmp/cs/Smoke" "$tmp/csb" "$tmp/k2" "$tmp/k2b"
cat > "$tmp/spec-cs.md" <<'FIX'
### SPEC-001 — Gruen
### SPEC-002 — Rot
### SPEC-003 — Uebersprungen
### SPEC-004 — Nicht im Bericht
### SPEC-005 — Mehrere Kennungen A
### SPEC-006 — Mehrere Kennungen B
### SPEC-007 — Mehrere Zeilen
### SPEC-008 — Tag ohne Methode
### SPEC-009 — Theorie mit rotem Fall
### SPEC-010 — Kotlin und C#
### SPEC-011 — Smoke
### SPEC-012 — Nur im Mini-Test ohne Tag
### SPEC-013 — Tag ueber Feld mit Zuweisung
FIX

cat > "$tmp/cs/Mini/SeiteATests.cs" <<'FIX'
namespace Fix.Mini
{
    public class SeiteATests
    {
        // @spec SPEC-001
        [Fact]
        public void Gruen() { }

        // @spec SPEC-002
        [Fact]
        public void Rot() { }

        // @spec SPEC-003
        [Fact]
        public void Uebersprungen() { }

        // @spec SPEC-004
        [Fact]
        public void NichtImBericht() { }

        // @spec SPEC-099
        [Fact]
        public void UnbekannteKennung() { }

        // @spec SPEC-005 SPEC-006
        // @spec SPEC-007
        // Zwischenkommentar

        [Trait("Kategorie", "X")]
        [Theory]
        [InlineData("a")]
        [InlineData("b")]
        public async Task Mehrere(string wert) { }

        // @spec SPEC-008
        private int feld = 1;

        [Fact]
        public void OhneTag() { }

        // @spec SPEC-013
        private readonly SeiteATests _feld = new SeiteATests();

        [Fact]
        public void NachFeld() { }

        // @spec SPEC-009
        [Theory]
        [InlineData(1)]
        [InlineData(2)]
        public void TheorieMitRot(int x) { }
    }
}
FIX

cat > "$tmp/cs/Mini/DateiTests.cs" <<'FIX'
using Xunit;

namespace Fix.Datei;

public sealed class DateiTests
{
    // @spec SPEC-010
    [Fact]
    public void Gleiche_Kennung_wie_Kotlin() { }
}
FIX

cat > "$tmp/cs/Smoke/RauchTests.cs" <<'FIX'
namespace Fix.Rauch;

public class RauchTests
{
    // @spec SPEC-011
    [Fact]
    public void Rauch() { }
}
FIX

# Ein Verzeichnis bin/ wird nicht gelesen: ein getaggter Test dort zaehlt nicht und steht in keinem Abschnitt.
mkdir -p "$tmp/cs/bin"
cat > "$tmp/cs/bin/Erzeugt.cs" <<'FIX'
namespace Fix.Bin;

public class ErzeugtTests
{
    // @spec SPEC-012
    [Fact]
    public void Erzeugt() { }
}
FIX

cat > "$tmp/csb/bericht.trx" <<'FIX'
<?xml version="1.0" encoding="utf-8"?>
<TestRun id="x" name="fix" xmlns="http://microsoft.com/schemas/VisualStudio/TeamTest/2010">
  <Results>
    <UnitTestResult executionId="e1" testId="t-gruen" testName="Fix.Mini.SeiteATests.Gruen" outcome="Passed" />
    <UnitTestResult executionId="e2" testId="t-rot" testName="Fix.Mini.SeiteATests.Rot" outcome="Failed">
      <Output>
        <ErrorInfo><Message>x</Message></ErrorInfo>
      </Output>
    </UnitTestResult>
    <UnitTestResult executionId="e3" testId="t-skip" testName="Fix.Mini.SeiteATests.Uebersprungen" outcome="NotExecuted" />
    <UnitTestResult executionId="e4" testId="t-unb" testName="Fix.Mini.SeiteATests.UnbekannteKennung" outcome="Passed" />
    <UnitTestResult executionId="e5" testId="t-mehr-a" testName="Fix.Mini.SeiteATests.Mehrere(wert: &quot;a&quot;)" outcome="Passed" />
    <UnitTestResult executionId="e6" testId="t-mehr-b" testName="Fix.Mini.SeiteATests.Mehrere(wert: &quot;b&quot;)" outcome="Passed" />
    <UnitTestResult executionId="e7" testId="t-ohne" testName="Fix.Mini.SeiteATests.OhneTag" outcome="Passed" />
    <UnitTestResult executionId="e8" testId="t-th-1" testName="Fix.Mini.SeiteATests.TheorieMitRot(x: 1)" outcome="Passed" />
    <UnitTestResult executionId="e9" testId="t-th-2" testName="Fix.Mini.SeiteATests.TheorieMitRot(x: 2)" outcome="Failed" />
    <UnitTestResult executionId="e13" testId="t-nachfeld" testName="Fix.Mini.SeiteATests.NachFeld" outcome="Passed" />
    <UnitTestResult executionId="e10" testId="t-datei" testName="Fix.Datei.DateiTests.Gleiche_Kennung_wie_Kotlin" outcome="Passed" />
    <UnitTestResult executionId="e11" testId="t-rauch" testName="Fix.Rauch.RauchTests.Rauch" outcome="Passed" />
    <UnitTestResult executionId="e12" testId="t-erzeugt" testName="Fix.Bin.ErzeugtTests.Erzeugt" outcome="Passed" />
  </Results>
  <TestDefinitions>
    <UnitTest name="Fix.Mini.SeiteATests.Gruen" id="t-gruen">
      <Execution id="e1" />
      <TestMethod className="Fix.Mini.SeiteATests" name="Gruen" />
    </UnitTest>
    <UnitTest name="Fix.Mini.SeiteATests.Rot" id="t-rot">
      <TestMethod className="Fix.Mini.SeiteATests" name="Rot" />
    </UnitTest>
    <UnitTest name="Fix.Mini.SeiteATests.Uebersprungen" id="t-skip">
      <TestMethod className="Fix.Mini.SeiteATests" name="Uebersprungen" />
    </UnitTest>
    <UnitTest name="Fix.Mini.SeiteATests.UnbekannteKennung" id="t-unb">
      <TestMethod className="Fix.Mini.SeiteATests" name="UnbekannteKennung" />
    </UnitTest>
    <UnitTest name="Fix.Mini.SeiteATests.Mehrere(wert: &quot;a&quot;)" id="t-mehr-a">
      <TestMethod className="Fix.Mini.SeiteATests" name="Mehrere" />
    </UnitTest>
    <UnitTest name="Fix.Mini.SeiteATests.Mehrere(wert: &quot;b&quot;)" id="t-mehr-b">
      <TestMethod className="Fix.Mini.SeiteATests" name="Mehrere" />
    </UnitTest>
    <UnitTest name="Fix.Mini.SeiteATests.OhneTag" id="t-ohne">
      <TestMethod className="Fix.Mini.SeiteATests" name="OhneTag" />
    </UnitTest>
    <UnitTest name="Fix.Mini.SeiteATests.NachFeld" id="t-nachfeld">
      <TestMethod className="Fix.Mini.SeiteATests" name="NachFeld" />
    </UnitTest>
    <UnitTest name="Fix.Mini.SeiteATests.TheorieMitRot(x: 1)" id="t-th-1">
      <TestMethod className="Fix.Mini.SeiteATests" name="TheorieMitRot" />
    </UnitTest>
    <UnitTest name="Fix.Mini.SeiteATests.TheorieMitRot(x: 2)" id="t-th-2">
      <TestMethod className="Fix.Mini.SeiteATests" name="TheorieMitRot" />
    </UnitTest>
    <UnitTest name="Fix.Datei.DateiTests.Gleiche_Kennung_wie_Kotlin" id="t-datei">
      <TestMethod className="Fix.Datei.DateiTests" name="Gleiche_Kennung_wie_Kotlin" />
    </UnitTest>
    <UnitTest name="Fix.Rauch.RauchTests.Rauch" id="t-rauch">
      <TestMethod className="Fix.Rauch.RauchTests" name="Rauch" />
    </UnitTest>
    <UnitTest name="Fix.Bin.ErzeugtTests.Erzeugt" id="t-erzeugt">
      <TestMethod className="Fix.Bin.ErzeugtTests" name="Erzeugt" />
    </UnitTest>
  </TestDefinitions>
</TestRun>
FIX

cat > "$tmp/k2/KotlinSeiteTest.kt" <<'FIX'
package fix.kt

class KotlinSeiteTest {
    /**
     * @spec SPEC-010
     */
    @Test
    fun `kotlin gruen`() {}
}
FIX
cat > "$tmp/k2b/TEST-fix.kt.KotlinSeiteTest.xml" <<'FIX'
<testsuite name="fix.kt.KotlinSeiteTest" tests="1" skipped="0" failures="0" errors="0">
  <testcase name="kotlin gruen()" classname="fix.kt.KotlinSeiteTest" time="0.001"/>
</testsuite>
FIX

lauf_cs() {
  lauf env SPEC_DATEI="$tmp/spec-cs.md" TEST_QUELLE="$tmp/k2" BERICHTE="$tmp/k2b" CS_QUELLE="$tmp/cs" CS_BERICHTE="$tmp/csb" "$@"
}

faelle_cs() {
  lauf_cs
  erwarte '^| `SPEC-001` | Gruen | 1 | 0 | 0 | 0 | SeiteATests | mindestens ein bestandener getaggter Test |$' "ein bestandener C#-Test zaehlt"
  erwarte '^| `SPEC-002` .*| 0 | 0 | 0 | 1 | — | OHNE NACHWEIS |$' "ein roter C#-Test belegt nichts"
  erwarte '^| `SPEC-002` | `Fix.Mini.SeiteATests.Rot` | rot |$' "der rote C#-Test steht als rot"
  erwarte '^| `SPEC-003` | `Fix.Mini.SeiteATests.Uebersprungen` | übersprungen |$' "ein uebersprungener C#-Test steht als uebersprungen"
  erwarte '^| `SPEC-004` | `Fix.Mini.SeiteATests.NichtImBericht` | nicht gelaufen' "ein C#-Test ohne Testfall im Bericht steht als nicht gelaufen"
  erwarte '^| `SPEC-004` .*| OHNE NACHWEIS |$' "ein nicht gelaufener C#-Test belegt nichts"
  erwarte '^| `SPEC-099` | `Fix.Mini.SeiteATests.UnbekannteKennung` |$' "ein C#-Tag auf eine unbekannte Kennung steht im eigenen Abschnitt"
  erwarte '^| `SPEC-005` .*| 1 | 0 | 0 | 0 | SeiteATests | mindestens ein bestandener getaggter Test |$' "mehrere Kennungen in einer Zeile: erste, trotz Attributen und Theorie"
  erwarte '^| `SPEC-006` .*| 1 | 0 | 0 | 0 | SeiteATests | mindestens ein bestandener getaggter Test |$' "mehrere Kennungen in einer Zeile: zweite"
  erwarte '^| `SPEC-007` .*| 1 | 0 | 0 | 0 | SeiteATests | mindestens ein bestandener getaggter Test |$' "mehrere Tag-Zeilen addieren sich"
  erwarte '^| `SPEC-008` .*| 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |$' "ein Tag vor einer Zeile ohne Methode wird verworfen"
  erwarte '^| `SPEC-009` .*| 0 | 0 | 0 | 1 | — | OHNE NACHWEIS |$' "eine Theorie mit einem roten Fall ist rot"
  erwarte '^| `SPEC-010` .*| 2 | 0 | 0 | 0 | KotlinSeiteTest, DateiTests | mindestens ein bestandener getaggter Test |$' "Kotlin- und C#-Nachweis derselben Kennung addieren sich (Dateiform des Namensraums)"
  erwarte '^| `SPEC-011` .*| 0 | 1 | 0 | 0 | RauchTests | mindestens ein bestandener getaggter Test |$' "die Art folgt dem Verzeichnis Smoke/"
  erwarte '^| `SPEC-013` .*| 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |$' "ein Tag ueber einer Feldzeile mit Zuweisung haftet nicht am Konstruktoraufruf"
  verbiete '`SPEC-013` | `Fix' "das Feld steht in keinem Abschnitt als Test"
  verbiete 'SPEC-012` | `Fix' "unter bin/ wird nichts gelesen"
  erwarte '^| `SPEC-012` .*| OHNE NACHWEIS |$' "ein Test unter bin/ belegt nichts"
}

(faelle_cs) || { echo "spec-abdeckung-selbsttest: C#-Faelle rot" >&2; exit 1; }
echo "spec-abdeckung-selbsttest: C#-Faelle gruen"

# Ohne Bericht bricht der Erzeuger mit CS_QUELLE ab (Exit 2), statt C#-Tests still als nicht gelaufen zu fuehren.
mkdir -p "$tmp/csleer"
lauf_cs CS_BERICHTE="$tmp/csleer" && { echo "spec-abdeckung-selbsttest: ohne TRX-Bericht muss der Erzeuger Exit 2 liefern" >&2; exit 1; }
lauf_cs CS_BERICHTE="$tmp/gibt-es-nicht" && { echo "spec-abdeckung-selbsttest: ohne Berichtsverzeichnis muss der Erzeuger Exit 2 liefern" >&2; exit 1; }
echo "spec-abdeckung-selbsttest: C#-Bericht fehlt gruen"

# Mutationen des Erzeugers: jede muss die C#-Faelle rot machen. Eine Mutation, die den Erzeuger nicht aendert, ist ein Fehler des Selbsttests.
mutiere() { # mutiere <Name> <sed-Ausdruck>
  sed "$2" "$erzeuger" > "$tmp/mut-$1.sh"
  if cmp -s "$erzeuger" "$tmp/mut-$1.sh"; then echo "spec-abdeckung-selbsttest: Mutation $1 aendert den Erzeuger nicht" >&2; exit 1; fi
  if ( erzeuger_lauf="$tmp/mut-$1.sh"; faelle_cs ) > /dev/null 2>&1; then
    echo "spec-abdeckung-selbsttest: Mutation $1 bleibt unbemerkt (C#-Faelle gruen)" >&2; exit 1
  fi
  echo "spec-abdeckung-selbsttest: Mutation $1 rot gesehen"
}
mutiere cs-eingang-entfernt 's|^csquelle=.*|csquelle=|'
mutiere trx-ergebnis-immer-bestanden 's|^        e = (rerg\[i\].*|        e = "pass"|'
mutiere tag-haftet-ueber-andere-zeilen 's|^    { pend = "" }$||'
mutiere feldzeile-mit-zuweisung-ist-methode 's#s !~ /=/ && ##'
mutiere trx-ohne-methodenzuordnung 's|if (!(rid\[i\] in dkl)) continue|dkl[rid[i]] = "x"; dme[rid[i]] = "y"|'

# Leere Berichte: der Erzeuger bricht ab, statt eine leere Matrix als Befund auszugeben.
rm "$tmp/b"/*.xml
lauf env && { echo "spec-abdeckung-selbsttest: ohne Berichte muss der Erzeuger Exit 2 liefern" >&2; exit 1; }
echo "spec-abdeckung-selbsttest: gruen"
