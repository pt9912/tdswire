#!/bin/sh
# flaeche-selbsttest.sh - Selbsttest des Sensors tools/flaeche-pruefung.sh (TDS-QA-05, TDS-RB-04, SPEC-059).
#
# Aufruf: flaeche-selbsttest.sh   Exit 0, wenn jeder Fall das erwartete Ergebnis hat; sonst Exit 1.
# Er baut in einem Wegwerf-Verzeichnis ein kleines Repo (Liste, Hauptmodul, Einbettungstest, Testserver) und ruft den Sensor
# auf Abwandlungen davon. Je Zweig des Sensors gibt es eine Mutation, die rot werden muss, und Faelle, die gruen oder rot
# bleiben muessen, damit ein Sensor, der immer anschlaegt oder nie, auffaellt:
#   Zweig (a) Name der Liste ohne Beleg:        Fall a1 (Verwendung entfernt), a2 (nur im Kommentar), a3 (nur in den Tests
#                                               des Testservers), a4 (geschachtelter Name ohne Eltern), a5 (fremde Eltern)
#   Zweig (b) Deklaration ohne Eintrag:         Fall b1 (neuer oeffentlicher Typ), b2 (neuer oeffentlicher Fall)
#   Zweig (c) Name ohne oeffentliche Deklaration: Fall c1 (Typ wird internal)
#   gruen bleibt:                               Fall g0 (Ausgangslage), g1 (zusaetzlicher interner Typ), g2 (Beleg nur im
#                                               Testserver), g3 (voll qualifizierte Verwendung mit Paketnamen)
#   Aufruffehler (Exit 2):                      Fall e1 (Zeile der Liste fehlt), e2 (Verzeichnis fehlt)
# Grenze: der Test belegt die Zweige an einem Mini-Repo, nicht am echten Bestand; ob die echte Liste stimmt, sagt der Lauf
# des Sensors selbst.
set -eu

pruefung=$(cd "$(dirname "$0")" && pwd)/flaeche-pruefung.sh
[ -f "$pruefung" ] || { echo "flaeche-selbsttest: FEHLER - $pruefung fehlt" >&2; exit 2; }

arbeit=$(mktemp -d)
trap 'rm -rf "$arbeit"' EXIT
fehler=0

# Die Ausgangslage: Liste mit Server, Mode (Fast, Slow), Event (Row); alle von aussen verwendet.
baue() {
  d=$1
  mkdir -p "$d/spec" "$d/src/main/kotlin/a" "$d/src/integrator/kotlin" "$d/testserver/kotlin" "$d/testserver/test/kotlin"
  printf '%s\n' \
    '| Teil | Inhalt |' \
    '| Zusage | Maßstab. Die Namen nach Package: `runtime`: `Server`, `Mode` (`Fast`, `Slow`); `domain`: `Event` (`Row`), `NONE`, `accept`. Für die Signaturen von `Server` gilt dasselbe; `Hidden` ist nicht öffentlich. |' \
    > "$d/spec/spezifikation.md"
  printf '%s\n' \
    'package a' \
    '' \
    'public class Server' \
    '' \
    'public sealed interface Mode {' \
    '    public data object Fast : Mode' \
    '' \
    '    public data object Slow : Mode' \
    '}' \
    '' \
    'public sealed interface Event {' \
    '    public data class Row(val v: Int) : Event' \
    '}' \
    '' \
    'internal class Hidden' \
    '' \
    'public fun helper(): Int = 1' \
    > "$d/src/main/kotlin/a/Main.kt"
  printf '%s\n' \
    'package t' \
    '' \
    'import a.Event' \
    'import a.Mode' \
    'import a.Server' \
    '' \
    'class T {' \
    '    val s = Server()' \
    '    val f = Mode.Fast' \
    '    val w = Mode.Slow' \
    '    val r = Event.Row(1)' \
    '}' \
    > "$d/src/integrator/kotlin/T.kt"
  printf '%s\n' 'package ts' > "$d/testserver/kotlin/Leer.kt"
}

# erwarte <name> <exitcode> <muster> <verzeichnis>: der Sensor endet mit <exitcode>, und stderr enthaelt <muster> (nicht, wenn leer).
erwarte() {
  name=$1
  soll=$2
  muster=$3
  d=$4
  ist=0
  sh "$pruefung" "$d" > "$arbeit/aus.txt" 2> "$arbeit/fehler.txt" || ist=$?
  if [ "$ist" -ne "$soll" ]; then
    echo "flaeche-selbsttest: FEHLER - $name: Exit $ist, erwartet $soll" >&2
    cat "$arbeit/fehler.txt" >&2
    fehler=1
    return
  fi
  if [ -n "$muster" ] && ! grep -q -e "$muster" "$arbeit/fehler.txt"; then
    echo "flaeche-selbsttest: FEHLER - $name: stderr enthaelt nicht '$muster'" >&2
    cat "$arbeit/fehler.txt" >&2
    fehler=1
    return
  fi
  echo "flaeche-selbsttest: ok - $name"
}

neu() {
  d=$arbeit/$1
  rm -rf "$d"
  baue "$d"
  printf '%s' "$d"
}

# g0 Ausgangslage: gruen
d=$(neu g0)
erwarte 'g0 Ausgangslage gruen' 0 '' "$d"

# g1 ein zusaetzlicher interner Typ stoert nicht
d=$(neu g1)
printf '%s\n' 'internal class Extra' 'private class Extra2' >> "$d/src/main/kotlin/a/Main.kt"
erwarte 'g1 interner Typ ohne Eintrag bleibt gruen' 0 '' "$d"

# g2 der Beleg darf im Testserver liegen statt im Einbettungstest
d=$(neu g2)
printf '%s\n' 'package ts' 'import a.Server' 'val s = Server()' > "$d/testserver/kotlin/Leer.kt"
printf '%s\n' 'package t' 'import a.Event' 'import a.Mode' 'val x = Mode.Fast' 'val y = Mode.Slow' 'val z = Event.Row(1)' > "$d/src/integrator/kotlin/T.kt"
erwarte 'g2 Beleg im Testserver bleibt gruen' 0 '' "$d"

# g3 ein Fall mit Eltern, im Quelltext mit vollem Pfad geschrieben, zaehlt (Eltern.Name auch hinter Paketnamen)
d=$(neu g3)
printf '%s\n' 'package t' 'val s = a.Server()' 'val f = a.Mode.Fast' 'val w = a.Mode.Slow' 'val r = a.Event.Row(1)' > "$d/src/integrator/kotlin/T.kt"
erwarte 'g3 voll qualifizierte Verwendung bleibt gruen' 0 '' "$d"

# a1 Verwendung eines Falls entfernt
d=$(neu a1)
printf '%s\n' 'package t' 'import a.Event' 'import a.Mode' 'import a.Server' 'val s = Server()' 'val f = Mode.Fast' 'val r = Event.Row(1)' > "$d/src/integrator/kotlin/T.kt"
erwarte 'a1 Name der Liste ohne Verwendung ist rot' 1 'ohne Beleg ueber die oeffentliche Flaeche: Slow' "$d"

# a2 die Verwendung steht nur in einer Kommentarzeile
d=$(neu a2)
printf '%s\n' 'package t' 'import a.Event' 'import a.Mode' '// val s = Server()' '/* val s = Server() */' '* Server' 'val f = Mode.Fast' 'val w = Mode.Slow' 'val r = Event.Row(1)' > "$d/src/integrator/kotlin/T.kt"
erwarte 'a2 Verwendung nur im Kommentar ist rot' 1 'ohne Beleg ueber die oeffentliche Flaeche: Server' "$d"

# a3 die Verwendung steht nur in den Tests des Testservers
d=$(neu a3)
printf '%s\n' 'package ts' 'val s = Server()' > "$d/testserver/test/kotlin/X.kt"
printf '%s\n' 'package t' 'import a.Event' 'import a.Mode' 'val f = Mode.Fast' 'val w = Mode.Slow' 'val r = Event.Row(1)' > "$d/src/integrator/kotlin/T.kt"
erwarte 'a3 Verwendung nur in den Tests des Testservers ist rot' 1 'ohne Beleg ueber die oeffentliche Flaeche: Server' "$d"

# a4 ein geschachtelter Name zaehlt nur mit seinen Eltern
d=$(neu a4)
printf '%s\n' 'package t' 'import a.Event' 'import a.Server' 'val s = Server()' 'val f = Fast' 'val w = Slow' 'val r = Event.Row(1)' > "$d/src/integrator/kotlin/T.kt"
erwarte 'a4 geschachtelter Name ohne Eltern ist rot' 1 'ohne Beleg ueber die oeffentliche Flaeche: Fast' "$d"

# a5 gleicher Name unter fremden Eltern zaehlt nicht
d=$(neu a5)
printf '%s\n' 'package t' 'import a.Event' 'import a.Mode' 'import a.Server' 'val s = Server()' 'val f = Mode.Fast' 'val w = Other.Slow' 'val r = Event.Row(1)' > "$d/src/integrator/kotlin/T.kt"
erwarte 'a5 gleicher Name unter fremden Eltern ist rot' 1 'ohne Beleg ueber die oeffentliche Flaeche: Slow' "$d"

# b1 neuer oeffentlicher Typ ohne Eintrag
d=$(neu b1)
printf '%s\n' 'public class Extra' >> "$d/src/main/kotlin/a/Main.kt"
erwarte 'b1 neuer oeffentlicher Typ ohne Eintrag ist rot' 1 'ohne Eintrag in der Liste: Extra' "$d"

# b2 neuer oeffentlicher Fall ohne Eintrag
d=$(neu b2)
printf '%s\n' 'public sealed interface Extra2 {' '    public data object Case : Extra2' '}' >> "$d/src/main/kotlin/a/Main.kt"
erwarte 'b2 neuer oeffentlicher Fall ohne Eintrag ist rot' 1 'ohne Eintrag in der Liste: Case' "$d"

# c1 ein Name der Liste ist nicht mehr oeffentlich
d=$(neu c1)
sed -i 's/^public class Server$/internal class Server/' "$d/src/main/kotlin/a/Main.kt"
erwarte 'c1 Name der Liste ohne oeffentliche Deklaration ist rot' 1 'ohne oeffentliche Deklaration: Server' "$d"

# e1 die Zeile der Liste fehlt
d=$(neu e1)
printf '%s\n' '| Teil | Inhalt |' > "$d/spec/spezifikation.md"
erwarte 'e1 Zeile der Liste fehlt ist ein Aufruffehler' 2 'nicht genau einmal' "$d"

# e2 ein Verzeichnis fehlt
d=$(neu e2)
rm -rf "$d/testserver"
erwarte 'e2 fehlendes Verzeichnis ist ein Aufruffehler' 2 'Verzeichnis fehlt' "$d"

if [ "$fehler" -ne 0 ]; then
  echo "flaeche-selbsttest: FEHLER - mindestens ein Fall hat nicht das erwartete Ergebnis" >&2
  exit 1
fi
echo "flaeche-selbsttest: OK - alle Faelle haben das erwartete Ergebnis"
