#!/bin/sh
# flaeche-pruefung.sh - Sensor der oeffentlichen Flaeche der Bibliothek (TDS-QA-05, TDS-RB-04; Liste: SPEC-059).
#
# Aufruf: flaeche-pruefung.sh [wurzel]   (Voreinstellung: das aktuelle Verzeichnis). Exit 0, wenn die Zusage stimmt;
# Exit 1 mit je einer Zeile auf stderr je Befund; Exit 2 bei falschem Aufruf, fehlender Datei oder nicht lesbarer Liste.
#
# Er vergleicht drei Mengen von TYPNAMEN (Klassen, Schnittstellen, Objekte, Aufzaehlungen, auch geschachtelte Faelle):
#   LISTE    die Namen in `spec/spezifikation.md`, Zeile `SPEC-059` "Zusage", zwischen den Texten "Die Namen nach Package"
#            und "Signaturen von"; ein Name ist ein Zeichenketten-Stueck in Backticks, das mit einem Grossbuchstaben
#            beginnt und einen Kleinbuchstaben enthaelt (`NONE` und Aufzaehlungskonstanten zaehlen nicht), bis zum ersten
#            Zeichen, das kein Buchstabe oder keine Ziffer ist.
#   OEFFENTLICH  die Deklarationen `public ... class|interface|object <Name>` unter `src/main/kotlin` (`explicitApi` Modus
#            `strict` verlangt das Wort `public` an jeder oeffentlichen Deklaration).
#   BELEGT   ein Name mit einem Treffer in einer Nicht-Kommentarzeile (Zeile ohne fuehrendes `*`, `//`, `/*`) unter
#            `src/integrator` oder `testserver/kotlin`. Ein geschachtelter Name (Fall einer Schnittstelle oder Klasse)
#            zaehlt nur als `Eltern.Name` (auch in einem `import`), ein Name auf oberster Ebene als Wort.
# Befunde: (a) ein Name der LISTE ohne Beleg ("Name der Liste ohne Beleg"), (b) eine OEFFENTLICHE Deklaration ohne Eintrag in
# der LISTE, (c) ein Name der LISTE ohne oeffentliche Deklaration. Der Selbsttest tools/flaeche-selbsttest.sh macht jeden
# Zweig mit einer Mutation rot und belegt Faelle, die gruen bleiben muessen.
#
# Grenzen (jede ist eine Aussage ueber den Sensor, nicht ueber die Flaeche):
# - Typnamen. Mitglieder (Felder, Konstanten, Funktionen, Konstruktoren) vergleicht er nicht: `SimTable.onInsertBatch`,
#   `SimColumn.maxLength`, die Felder von `TelemetryEvent` oder die Konstanten von `EventKind` kann eine neue oeffentliche
#   Deklaration ohne Eintrag und ohne Beleg hinzufuegen, ohne dass er anschlaegt.
# - Namenssuche ist kein Aufrufbeleg: ein Name in einer Zeichenkette, in einem Kommentar am Zeilenende oder in einem toten
#   Aufruf zaehlt als Beleg. Ein `import` zaehlt (der Uebersetzer und ktlint weisen einen ungenutzten `import` ab).
# - Er liest Kotlin-Quellen. Im Bytecode ist `internal` oeffentlich; was Java oder Reflection erreicht, sieht er nicht.
# - Eine Deklaration, die nicht mit `public` und den Modifikatoren sealed, data, fun, enum, abstract, open, value,
#   annotation, inner, const auf einer Zeile beginnt (etwa ein Zeilenumbruch nach `public`), sieht er nicht.
# - Die Eltern eines geschachtelten Namens liest er an der Einrueckung; eine Deklaration in einem Block, der keine
#   Typdeklaration ist, zaehlt wie eine auf oberster Ebene.
# Aufgerufen in der Dockerfile-Stage `flaeche` (Target `flaeche`); Selbsttest: tools/flaeche-selbsttest.sh.
set -eu

[ "$#" -le 1 ] || { echo "Aufruf: $0 [wurzel]" >&2; exit 2; }
wurzel=${1:-.}
spec=$wurzel/spec/spezifikation.md
haupt=$wurzel/src/main/kotlin
belege="$wurzel/src/integrator $wurzel/testserver/kotlin"

[ -f "$spec" ] || { echo "flaeche-pruefung: FEHLER - Datei fehlt: $spec" >&2; exit 2; }
for d in $haupt $belege; do
  [ -d "$d" ] || { echo "flaeche-pruefung: FEHLER - Verzeichnis fehlt: $d" >&2; exit 2; }
done

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

# LISTE
grep '^| Zusage |' "$spec" > "$tmp/zusage.txt" || true
[ "$(wc -l < "$tmp/zusage.txt")" -eq 1 ] || {
  echo "flaeche-pruefung: FEHLER - die Zeile '| Zusage |' steht nicht genau einmal in $spec" >&2
  exit 2
}
case $(cat "$tmp/zusage.txt") in
  *"Die Namen nach Package"*"Signaturen von"*) ;;
  *)
    echo "flaeche-pruefung: FEHLER - die Zeile 'Zusage' nennt nicht 'Die Namen nach Package' vor 'Signaturen von'" >&2
    exit 2
    ;;
esac
sed 's/.*Die Namen nach Package//; s/Signaturen von.*//' "$tmp/zusage.txt" |
  grep -oE '`[^`]+`' | tr -d '`' | sed -E 's/^([A-Za-z0-9]*).*/\1/' |
  grep -E '^[A-Z][A-Za-z0-9]*[a-z][A-Za-z0-9]*$' | sort -u > "$tmp/liste.txt"
[ -s "$tmp/liste.txt" ] || { echo "flaeche-pruefung: FEHLER - die Liste der Namen ist leer" >&2; exit 2; }

# OEFFENTLICH: Zeilen "Name<TAB>Eltern" (Eltern leer auf oberster Ebene), Eltern nach der Einrueckung.
find "$haupt" -name '*.kt' | sort | while IFS= read -r datei; do
  awk '
    function einrueckung(zeile,   n) { n = match(zeile, /[^ ]/); return n - 1 }
    {
      zeile = $0
      if (zeile !~ /^ *(public |internal |private |protected )?((sealed|data|fun|enum|abstract|open|value|annotation|inner|const) )*(class|interface|object) [A-Za-z0-9]+/) next
      n = einrueckung(zeile)
      eltern = ""
      for (m = n - 1; m >= 0; m--) if (m in stapel) { eltern = stapel[m]; break }
      text = zeile
      sub(/^ */, "", text)
      sichtbar = (text ~ /^public /)
      sub(/^(public|internal|private|protected) /, "", text)
      while (text ~ /^(sealed|data|fun|enum|abstract|open|value|annotation|inner|const) /) sub(/^[a-z]+ /, "", text)
      sub(/^(class|interface|object) /, "", text)
      name = text
      sub(/[^A-Za-z0-9].*/, "", name)
      for (k in stapel) if (k + 0 >= n) delete stapel[k]
      stapel[n] = name
      if (sichtbar) printf "%s\t%s\n", name, eltern
    }
  ' "$datei"
done | sort -u > "$tmp/oeffentlich.txt"

cut -f1 "$tmp/oeffentlich.txt" | sort -u > "$tmp/oeffentlich-namen.txt"

# Quelltext der Belege ohne Kommentarzeilen
find $belege -name '*.kt' | sort | while IFS= read -r datei; do
  grep -v -E '^[[:space:]]*(\*|//|/\*)' "$datei" || true
done > "$tmp/belege.txt"

befund=0

while IFS= read -r name; do
  grep -qx "$name" "$tmp/oeffentlich-namen.txt" || {
    echo "flaeche-pruefung: Name der Liste ohne oeffentliche Deklaration: $name" >&2
    befund=1
  }
done < "$tmp/liste.txt"

while IFS= read -r name; do
  grep -qx "$name" "$tmp/liste.txt" || {
    echo "flaeche-pruefung: oeffentliche Deklaration ohne Eintrag in der Liste: $name" >&2
    befund=1
  }
done < "$tmp/oeffentlich-namen.txt"

while IFS= read -r name; do
  grep -qx "$name" "$tmp/oeffentlich-namen.txt" || continue
  gefunden=0
  while IFS="$(printf '\t')" read -r n eltern; do
    [ "$n" = "$name" ] || continue
    if [ -n "$eltern" ]; then
      if grep -qE "(^|[^A-Za-z0-9_])$eltern\\.$name([^A-Za-z0-9_]|\$)" "$tmp/belege.txt"; then gefunden=1; fi
    else
      if grep -qw "$name" "$tmp/belege.txt"; then gefunden=1; fi
    fi
  done < "$tmp/oeffentlich.txt"
  [ "$gefunden" -eq 1 ] || {
    echo "flaeche-pruefung: Name der Liste ohne Beleg ueber die oeffentliche Flaeche: $name" >&2
    befund=1
  }
done < "$tmp/liste.txt"

if [ "$befund" -ne 0 ]; then
  echo "flaeche-pruefung: FEHLER - die Flaeche weicht von SPEC-059 ab" >&2
  exit 1
fi
echo "flaeche-pruefung: OK - $(wc -l < "$tmp/liste.txt") Namen der Liste, alle oeffentlich deklariert und ueber src/integrator oder testserver/kotlin belegt"
