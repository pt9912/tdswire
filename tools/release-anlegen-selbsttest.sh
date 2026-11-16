#!/bin/bash
# release-anlegen-selbsttest.sh — belegt in Wegwerf-Repos mit einem gh-Stub, dass tools/release-anlegen.sh vor `gh release create`
# bricht (Version -SNAPSHOT oder ohne Form, Tag nicht v<Version>, Tag fehlt oder nicht auf HEAD, Datei fehlt, zusaetzliche Datei,
# SHA256SUMS passt nicht zu einer Datei, SHA256SUMS nennt das Jar nicht, Release zum Tag besteht, gh release list schlaegt fehl, unersetzte
# Stelle in der Notiz; ohne Dateien: anderer Tag als v0.1.0 und v0.1.1, Tag fehlt) und dass der Gutfall `gh release create` einmal
# mit den drei Dateien und --verify-tag ruft, ohne --clobber, ohne Loeschen, mit Pruefsummen in der Notiz und ohne den Wert des Tokens in
# Argument, Notiz und Ausgabe; der Modus ohne Dateien ruft `create` ohne Anhang (TDS-RB-05, TDS-QA-06, ADR-0050, ADR-0024).
# Mutationen: je Marke `# P:<name>` des Skripts wird die Pruefung an einer Kopie ausgeschaltet; der zugehoerige Fall muss rot werden
# (und der Gutfall, wenn --verify-tag fehlt oder --clobber dazukommt). Ein Fall, der bei der Mutation gruen bleibt, macht diesen Test rot.
# Workflow als Text (veroeffentlichung.yml): `contents: write` steht genau einmal und im Job `release`; `needs` des Jobs nennt beide
# Paket-Jobs; dieselbe Bedingung wie `publish`; kein `continue-on-error`; Checkout mit persist-credentials false; die Schritte
# Tag-Commit, Bau und Pruefsummen ohne Secret; das Token steht nur im Schritt `gh release create`; kein `packages: write` und kein
# Cloudsmith-Secret im Job; jede `uses:`-Zeile traegt eine 40-stellige Commit-SHA; `vorschau` baut mit VORSCHAU=1 ohne Secret und ohne
# gh, `release` ohne VORSCHAU; `release-dateien` und das Skript stehen in keiner Gate-Liste und nicht in tools/mk/e2e.mk. Je vier
# Aenderungen an Kopien des Workflows (needs, Recht im falschen Job, Token im falschen Schritt, continue-on-error) muessen die
# Kernpruefungen ausloesen.
# Grenze: gh ist ein Stub; belegt sind Reihenfolge der Pruefungen und Form des Aufrufs, nicht GitHub. Pins gegen echte Actions,
# die Wirkung der Bedingung und der Rechte und die Reihenfolge der Jobs zur Laufzeit sieht der Test nicht (Text, kein Lauf); die
# Abwesenheitspruefung liest Make-Dateien als Text und kennt weder Includes noch Variablen.
#
# Aufgerufen von der Dockerfile-Stage `skript-selbsttest`. Braucht bash, git, mktemp, grep, sed, awk, sha256sum.
set -eu

hier=$(cd "$(dirname "$0")" && pwd)
wurzel_repo=$(cd "$hier/.." && pwd)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

fehler=0
pruefe() { echo "release-anlegen-selbsttest: FEHLER - $1" >&2; fehler=1; }

mkdir -p "$tmp/bin"
cat > "$tmp/bin/gh" <<'STUB'
#!/bin/sh
echo "gh $*" >> "$GH_LOG"
case "$1 $2" in
  "release list") [ "${GH_LIST_EXIT:-0}" -eq 0 ] || exit "$GH_LIST_EXIT"; [ ! -f "$GH_LISTE" ] || cat "$GH_LISTE" ;;
  "release create")
    while [ "$#" -gt 0 ]; do [ "$1" != "--notes-file" ] || { cp "$2" "$GH_NOTIZ"; break; }; shift; done ;;
esac
STUB
chmod +x "$tmp/bin/gh"

token="PLATZHALTER-TOKEN-4711"
: > "$tmp/leer"

# neues <name> <versionzeile> <tags: Liste von Tag-Namen auf dem ersten Commit> [alt]: Repo mit einem Commit; mit "alt" liegt ein
# zweiter Commit darueber, die Tags bleiben auf dem ersten.
neues() {
  r="$tmp/$1"
  mkdir -p "$r"
  git -C "$r" init -q
  git -C "$r" config user.name selbsttest
  git -C "$r" config user.email selbsttest@invalid
  git -C "$r" config commit.gpgsign false
  git -C "$r" config tag.gpgsign false
  printf '%s\n' "$2" > "$r/build.gradle.kts"
  git -C "$r" add build.gradle.kts
  git -C "$r" commit -q -m eins
  for t in $3; do git -C "$r" tag "$t"; done
  if [ "${4:-}" = alt ]; then echo x > "$r/zwei.txt"; git -C "$r" add zwei.txt; git -C "$r" commit -q -m zwei; fi
}

# dateien <verzeichnis> <version>: die drei Dateien der Ausgabe von make release-dateien, mit passenden Pruefsummen.
dateien() {
  mkdir -p "$1"
  printf 'zip-inhalt %s\n' "$2" > "$1/tdswire-$2.zip"
  printf 'jar-inhalt %s\n' "$2" > "$1/tdswire.jar"
  (cd "$1" && sha256sum "tdswire-$2.zip" tdswire.jar > SHA256SUMS)
}

# skript_kopie <ziel>: Skript und Vorlage in ein eigenes Verzeichnis kopieren (Mutationen und Vorlagen-Faelle aendern nur die Kopie).
skript_kopie() { mkdir -p "$1"; cp "$hier/release-anlegen.sh" "$hier/release-notiz.vorlage" "$1/"; }

# lauf <skript> <repo> <fallname> <args...>: Ausgang in $tmp/<fallname>.aus, Stub-Protokoll in .log, Notiz in .notiz, Exit in $code.
lauf() {
  local sk="$1" repo="$2" fall="$3"
  shift 3
  : > "$tmp/$fall.log"
  set +e
  PATH="$tmp/bin:$PATH" GH_LOG="$tmp/$fall.log" GH_NOTIZ="$tmp/$fall.notiz" GH_LISTE="${LISTE:-$tmp/leer}" GH_LIST_EXIT="${LISTE_EXIT:-0}" \
    GH_TOKEN="$token" RELEASE_WURZEL="$repo" bash "$sk" "$@" > "$tmp/$fall.aus" 2>&1
  code=$?
  set -e
  return 0
}

# rot_ohne_create <fall> <exit> <teil>: Exit und Meldungsteil stimmen, kein create im Protokoll.
rot_ohne_create() {
  [ "$code" -eq "$2" ] || return 1
  grep -q "$3" "$tmp/$1.aus" || return 1
  ! grep -q 'release create' "$tmp/$1.log" || return 1
}

# faelle <skript>: fuehrt alle Faelle gegen das Skript aus und setzt `rot` auf die Namen der roten Faelle.
faelle() {
  sk="$1"; rot=" "
  rm -rf "$tmp/f"; mkdir -p "$tmp/f"
  LISTE=""; LISTE_EXIT=""
  nok() { rot="$rot$1 "; }

  # Gutfall: Tag auf HEAD, drei Dateien, Summen passen, kein Release.
  neues f/gut 'version = "1.2.3"' v1.2.3; dateien "$tmp/f/gut-d" 1.2.3
  lauf "$sk" "$tmp/f/gut" gut --tag v1.2.3 --dateien "$tmp/f/gut-d"
  g=1
  [ "$code" -eq 0 ] || g=0
  [ "$(grep -c 'release create' "$tmp/gut.log")" -eq 1 ] || g=0
  grep -q "release create v1.2.3 $tmp/f/gut-d/tdswire-1.2.3.zip $tmp/f/gut-d/tdswire.jar $tmp/f/gut-d/SHA256SUMS " "$tmp/gut.log" || g=0
  grep -q -- '--verify-tag' "$tmp/gut.log" || g=0
  ! grep -q -E -- '--clobber|delete|--draft' "$tmp/gut.log" || g=0
  zsum=$(awk '$2 == "tdswire-1.2.3.zip" {print $1}' "$tmp/f/gut-d/SHA256SUMS"); jsum=$(awk '$2 == "tdswire.jar" {print $1}' "$tmp/f/gut-d/SHA256SUMS")
  grep -q "^$zsum  tdswire-1.2.3.zip$" "$tmp/gut.notiz" || g=0
  grep -q "^$jsum  tdswire.jar$" "$tmp/gut.notiz" || g=0
  ! grep -q -E '@[A-Z_]+@' "$tmp/gut.notiz" || g=0
  grep -q 'Die Prüfsumme belegt Unverändertheit, nicht Herkunft.' "$tmp/gut.notiz" || g=0
  grep -q 'io.github.pt9912:tdswire:1.2.3' "$tmp/gut.notiz" || g=0
  ! grep -q -i -E 'anonym|bytegleich|gleich dem Paket' "$tmp/gut.notiz" || g=0
  ! grep -q -F "$token" "$tmp/gut.aus" "$tmp/gut.log" "$tmp/gut.notiz" || g=0
  [ "$g" -eq 1 ] || nok gut

  # Version -SNAPSHOT und Version ohne Form
  neues f/snap 'version = "1.2.3-SNAPSHOT"' v1.2.3-SNAPSHOT; dateien "$tmp/f/snap-d" 1.2.3-SNAPSHOT
  lauf "$sk" "$tmp/f/snap" snap --tag v1.2.3-SNAPSHOT --dateien "$tmp/f/snap-d"
  rot_ohne_create snap 1 'Form' || nok version-form
  neues f/form 'version = "1.2"' v1.2; dateien "$tmp/f/form-d" 1.2
  lauf "$sk" "$tmp/f/form" form --tag v1.2 --dateien "$tmp/f/form-d"
  rot_ohne_create form 1 'Form' || nok version-form

  # Tag nicht v<Version>, Tag fehlt, Tag nicht auf HEAD
  neues f/tagv 'version = "1.2.3"' "v1.2.3 v1.2.4"; dateien "$tmp/f/tagv-d" 1.2.3
  lauf "$sk" "$tmp/f/tagv" tagv --tag v1.2.4 --dateien "$tmp/f/tagv-d"
  rot_ohne_create tagv 1 'ist nicht v1.2.3' || nok tag-version
  neues f/tagf 'version = "1.2.3"' ""; dateien "$tmp/f/tagf-d" 1.2.3
  lauf "$sk" "$tmp/f/tagf" tagf --tag v1.2.3 --dateien "$tmp/f/tagf-d"
  rot_ohne_create tagf 1 'besteht nicht' || nok tag-besteht
  neues f/tagh 'version = "1.2.3"' v1.2.3 alt; dateien "$tmp/f/tagh-d" 1.2.3
  lauf "$sk" "$tmp/f/tagh" tagh --tag v1.2.3 --dateien "$tmp/f/tagh-d"
  rot_ohne_create tagh 1 'nicht auf HEAD' || nok tag-head

  # Dateien: je eine fehlt, eine zusaetzliche, Summe passt nicht, Jar fehlt in SHA256SUMS
  neues f/fehlt 'version = "1.2.3"' v1.2.3
  for x in tdswire-1.2.3.zip tdswire.jar SHA256SUMS; do
    dateien "$tmp/f/fehlt-d" 1.2.3; rm "$tmp/f/fehlt-d/$x"
    lauf "$sk" "$tmp/f/fehlt" fehlt --tag v1.2.3 --dateien "$tmp/f/fehlt-d"
    rot_ohne_create fehlt 1 "$x fehlt" || nok dateien
    rm -rf "$tmp/f/fehlt-d"
  done
  neues f/extra 'version = "1.2.3"' v1.2.3; dateien "$tmp/f/extra-d" 1.2.3; echo v > "$tmp/f/extra-d/tdswire-1.2.3-vorschau.zip"
  lauf "$sk" "$tmp/f/extra" extra --tag v1.2.3 --dateien "$tmp/f/extra-d"
  rot_ohne_create extra 1 'fuehrt nicht genau' || nok nur-diese
  neues f/summe 'version = "1.2.3"' v1.2.3; dateien "$tmp/f/summe-d" 1.2.3; echo anders > "$tmp/f/summe-d/tdswire.jar"
  lauf "$sk" "$tmp/f/summe" summe --tag v1.2.3 --dateien "$tmp/f/summe-d"
  rot_ohne_create summe 1 'SHA256SUMS ist rot' || nok summen
  neues f/zeile 'version = "1.2.3"' v1.2.3; dateien "$tmp/f/zeile-d" 1.2.3; sed -i '/tdswire.jar$/d' "$tmp/f/zeile-d/SHA256SUMS"
  lauf "$sk" "$tmp/f/zeile" zeile --tag v1.2.3 --dateien "$tmp/f/zeile-d"
  rot_ohne_create zeile 1 'nennt nicht' || nok summen-zeilen

  # Release zum Tag besteht: kein create, kein Loeschen; gh release list schlaegt fehl: kein create
  neues f/besteht 'version = "1.2.3"' v1.2.3; dateien "$tmp/f/besteht-d" 1.2.3
  printf 'v1.2.2\nv1.2.3\n' > "$tmp/f/liste"
  LISTE="$tmp/f/liste" lauf "$sk" "$tmp/f/besteht" besteht --tag v1.2.3 --dateien "$tmp/f/besteht-d"
  rot_ohne_create besteht 1 'besteht schon ein Release' || nok besteht
  ! grep -q -E -- 'delete|--clobber' "$tmp/besteht.log" || nok besteht
  grep -q 'release list' "$tmp/besteht.log" || nok besteht
  LISTE_EXIT=3 lauf "$sk" "$tmp/f/besteht" liste --tag v1.2.3 --dateien "$tmp/f/besteht-d"
  rot_ohne_create liste 1 'schlug fehl' || nok liste

  # Vorlage mit unersetzter Stelle
  mkdir -p "$tmp/f/pl"; cp "$sk" "$tmp/f/pl/release-anlegen.sh"; cp "$hier/release-notiz.vorlage" "$tmp/f/pl/"
  printf '@UNBEKANNT@\n' >> "$tmp/f/pl/release-notiz.vorlage"
  lauf "$tmp/f/pl/release-anlegen.sh" "$tmp/f/gut" platz --tag v1.2.3 --dateien "$tmp/f/gut-d"
  rot_ohne_create platz 1 'unersetzte Stelle' || nok platzhalter

  # ohne Dateien: v0.1.0 (nur GitHub Packages), v0.1.1 (auch Cloudsmith), anderer Tag, Tag fehlt, Release besteht, falscher Aufruf
  neues f/ohne 'version = "0.3.0-SNAPSHOT"' "v0.1.0 v0.1.1 v0.2.0" alt
  for t in v0.1.0 v0.1.1; do
    lauf "$sk" "$tmp/f/ohne" "o$t" --tag "$t" --ohne-dateien
    g=1
    [ "$code" -eq 0 ] || g=0
    [ "$(grep -c 'release create' "$tmp/o$t.log")" -eq 1 ] || g=0
    grep -q "release create $t --verify-tag --title ${t#v} --notes-file" "$tmp/o$t.log" || g=0
    ! grep -q -E 'release create [^ ]+ [^-]' "$tmp/o$t.log" || g=0
    grep -q 'Für diese Version liegen keine Dateien an' "$tmp/o$t.notiz" || g=0
    ! grep -q -E 'SHA256|tdswire.jar|Dateien dieses Release' "$tmp/o$t.notiz" || g=0
    ! grep -q -E '@[A-Z_]+@' "$tmp/o$t.notiz" || g=0
    grep -q "^Version \`${t#v}\`, Tag \`$t\`, Commit \`$(git -C "$tmp/f/ohne" rev-parse "$t^{commit}")\`" "$tmp/o$t.notiz" || g=0
    if [ "$t" = v0.1.1 ]; then grep -q 'Cloudsmith' "$tmp/o$t.notiz" || g=0; else ! grep -q 'Cloudsmith' "$tmp/o$t.notiz" || g=0; fi
    ! grep -q -F "$token" "$tmp/o$t.aus" "$tmp/o$t.log" "$tmp/o$t.notiz" || g=0
    [ "$g" -eq 1 ] || nok ohne-gut
  done
  lauf "$sk" "$tmp/f/ohne" oanders --tag v0.2.0 --ohne-dateien
  rot_ohne_create oanders 1 'nur fuer v0.1.0 und v0.1.1' || nok ohne-bestand
  neues f/ohne2 'version = "0.3.0-SNAPSHOT"' ""
  lauf "$sk" "$tmp/f/ohne2" otag --tag v0.1.0 --ohne-dateien
  rot_ohne_create otag 1 'besteht nicht' || nok ohne-tag
  printf 'v0.1.0\n' > "$tmp/f/liste0"
  LISTE="$tmp/f/liste0" lauf "$sk" "$tmp/f/ohne" obesteht --tag v0.1.0 --ohne-dateien
  rot_ohne_create obesteht 1 'besteht schon ein Release' || nok besteht
  lauf "$sk" "$tmp/f/ohne" oaufruf --tag v0.1.0 --ohne-dateien --dateien "$tmp/f/gut-d"
  rot_ohne_create oaufruf 2 'Aufruf' || nok aufruf
  lauf "$sk" "$tmp/f/ohne" aufruf2 --dateien "$tmp/f/gut-d"
  rot_ohne_create aufruf2 2 'Aufruf' || nok aufruf
  lauf "$sk" "$tmp/f/ohne" aufruf3 --tag v0.1.0 --unbekannt
  rot_ohne_create aufruf3 2 'Aufruf' || nok aufruf
}

# 1. Das echte Skript: kein Fall rot.
faelle "$hier/release-anlegen.sh"
[ "$rot" = " " ] || pruefe "das Skript besteht diese Faelle nicht:$rot"

# 2. Je Marke eine Mutation: die Pruefung entfaellt, der Fall gleichen Namens muss rot werden.
marken=$(grep -o '# P:[a-z-]*$' "$hier/release-anlegen.sh" | sed 's/^# P://')
[ -n "$marken" ] || pruefe "tools/release-anlegen.sh traegt keine Marke # P:"
for m in $marken; do
  skript_kopie "$tmp/m-$m"
  sed -i "/# P:$m\$/s/fehler \"[^\"]*\" *# P:/true # P:/" "$tmp/m-$m/release-anlegen.sh"
  if cmp -s "$tmp/m-$m/release-anlegen.sh" "$hier/release-anlegen.sh"; then pruefe "die Mutation $m aendert das Skript nicht"; fi
  faelle "$tmp/m-$m/release-anlegen.sh"
  case "$rot" in *" $m "*) ;; *) pruefe "die Mutation $m (Pruefung entfernt) macht den Fall $m nicht rot (rot:$rot)" ;; esac
done
# Mutation ohne --verify-tag und Mutation mit --clobber: der Gutfall muss rot werden.
skript_kopie "$tmp/m-verify"
sed -i 's/ --verify-tag//g' "$tmp/m-verify/release-anlegen.sh"
faelle "$tmp/m-verify/release-anlegen.sh"
case "$rot" in *" gut "*) ;; *) pruefe "die Mutation ohne --verify-tag macht den Gutfall nicht rot" ;; esac
skript_kopie "$tmp/m-clobber"
sed -i 's/ --verify-tag / --verify-tag --clobber /g' "$tmp/m-clobber/release-anlegen.sh"
if cmp -s "$tmp/m-clobber/release-anlegen.sh" "$hier/release-anlegen.sh"; then pruefe "die Mutation mit --clobber aendert das Skript nicht"; fi
faelle "$tmp/m-clobber/release-anlegen.sh"
case "$rot" in *" gut "*) ;; *) pruefe "die Mutation mit --clobber macht den Gutfall nicht rot" ;; esac

# 3. Der Workflow als Text.
wf="$wurzel_repo/.github/workflows/veroeffentlichung.yml"
job_block() { awk -v j="  $2:" '$0 == j {b = 1; print; next} b && /^  [a-z#]/ {b = 0} b' "$1"; }
schritt_block() { awk -v n="      - name: $1" '$0 == n {b = 1; print; next} b && /^      - / {b = 0} b'; }
ausser_anlegen() { awk -v n='      - name: gh release create' '$0 == n {b = 1; next} b && /^      - / {b = 0} !b'; }
[ -f "$wf" ] || { pruefe "$wf fehlt"; exit 1; }
rel=$(job_block "$wf" release)
[ -n "$rel" ] || pruefe "der Workflow fuehrt keinen Job release"
[ "$(grep -v '^ *#' "$wf" | grep -c 'contents: write')" -eq 1 ] || pruefe "contents: write steht nicht genau einmal im Workflow"
printf '%s\n' "$rel" | grep -q '^      contents: write$' || pruefe "contents: write steht nicht im Job release"
printf '%s\n' "$rel" | grep -q '^    needs: \[publish, publish-cloudsmith\]$' || pruefe "needs des Jobs release nennt nicht genau publish und publish-cloudsmith"
bed="if: github.event_name == 'push' && github.ref_type == 'tag'"
printf '%s\n' "$rel" | grep -qF "    $bed" || pruefe "der Job release hat nicht die Bedingung des Tag-Push"
job_block "$wf" publish | grep -qF "    $bed" || pruefe "der Job publish hat nicht dieselbe Bedingung"
! grep -v '^ *#' "$wf" | grep -q 'continue-on-error' || pruefe "der Workflow nutzt continue-on-error"
printf '%s\n' "$rel" | grep -q 'persist-credentials: false' || pruefe "der Checkout des Jobs release setzt persist-credentials nicht auf false"
! printf '%s\n' "$rel" | grep -q 'packages: write' || pruefe "der Job release hat packages: write"
! printf '%s\n' "$rel" | grep -q 'CLOUDSMITH' || pruefe "der Job release nennt ein Cloudsmith-Secret"
printf '%s\n' "$rel" | schritt_block 'Tag-Commit liegt in origin/main' | grep -q 'git merge-base --is-ancestor HEAD origin/main' || pruefe "der Job release hat den Schritt Tag-Commit liegt in origin/main nicht"
printf '%s\n' "$rel" | schritt_block 'make release-dateien' | grep -q '^        run: make release-dateien$' || pruefe "der Job release ruft make release-dateien nicht auf"
! printf '%s\n' "$rel" | grep -q 'VORSCHAU' || pruefe "der Job release nennt VORSCHAU"
printf '%s\n' "$rel" | schritt_block 'Pruefsummen' | grep -q 'sha256sum -c SHA256SUMS' || pruefe "der Job release prueft die Pruefsummen nicht vor dem Anlegen"
anlegen=$(printf '%s\n' "$rel" | schritt_block 'gh release create')
printf '%s\n' "$anlegen" | grep -q 'GH_TOKEN: \${{ secrets.GITHUB_TOKEN }}' || pruefe "der Schritt gh release create fuehrt das Token nicht"
printf '%s\n' "$anlegen" | grep -q 'tools/release-anlegen.sh' || pruefe "der Schritt gh release create ruft tools/release-anlegen.sh nicht"
! printf '%s\n' "$rel" | ausser_anlegen | grep -q -E 'secrets\.|GH_TOKEN|GITHUB_TOKEN|env:' || pruefe "ein Token oder ein env: steht im Job release ausserhalb des Schritts gh release create"
[ "$(grep -v '^ *#' "$wf" | grep -c -E 'secrets\.GITHUB_TOKEN')" -eq 2 ] || pruefe "secrets.GITHUB_TOKEN steht nicht genau zweimal im Workflow (Jobs publish und release)"
! job_block "$wf" vorschau | grep -q -E 'secrets\.|GH_TOKEN|gh release|release-anlegen' || pruefe "der Job vorschau nennt ein Secret oder gh"
job_block "$wf" vorschau | grep -q '^        run: make release-dateien VORSCHAU=1$' || pruefe "der Job vorschau ruft make release-dateien VORSCHAU=1 nicht auf"
if grep -E '^\s+(- )?uses:' "$wf" | grep -v -E '@[0-9a-f]{40}$'; then pruefe "eine uses:-Zeile traegt keine 40-stellige Commit-SHA"; fi

# Abwesenheit: release-dateien und das Skript in keiner Gate-Liste, nicht in e2e.mk
gate_zeilen() { grep -E '^(GATE_CHECKS|GATE_KLASSE_[a-z]+|GATE_STAGE)[[:space:]]*(\+|:)?=' "$1" || true; }
for f in "$wurzel_repo"/tools/mk/*.mk "$wurzel_repo"/harness/mk/*.mk "$wurzel_repo/Makefile" "$wurzel_repo/repo.mk"; do
  [ -f "$f" ] || continue
  if gate_zeilen "$f" | grep -q -E 'release-dateien|release-anlegen'; then pruefe "$f fuehrt release-dateien oder release-anlegen in einer Gate-Liste"; fi
done
! grep -q -E 'release-dateien|release-anlegen' "$wurzel_repo/tools/mk/e2e.mk" || pruefe "tools/mk/e2e.mk nennt release-dateien oder release-anlegen"
cp "$wurzel_repo/tools/mk/release-dateien.mk" "$tmp/gegen.mk"; printf 'GATE_CHECKS += release-anlegen\n' >> "$tmp/gegen.mk"
gate_zeilen "$tmp/gegen.mk" | grep -q -E 'release-dateien|release-anlegen' || pruefe "die Abwesenheitspruefung wird an einer Kopie mit GATE_CHECKS nicht rot"

# Gegenproben des Workflow-Textes an Kopien: je eine Aenderung muss die Kernpruefungen ausloesen.
wf_befunde() { # <datei>: Zahl der Befunde der Kernpruefungen
  n=0; w="$1"
  [ "$(grep -v '^ *#' "$w" | grep -c 'contents: write')" -eq 1 ] || n=$((n+1))
  job_block "$w" release | grep -q '^      contents: write$' || n=$((n+1))
  job_block "$w" release | grep -q '^    needs: \[publish, publish-cloudsmith\]$' || n=$((n+1))
  ! grep -v '^ *#' "$w" | grep -q 'continue-on-error' || n=$((n+1))
  ! job_block "$w" release | ausser_anlegen | grep -q -E 'secrets\.|GH_TOKEN|env:' || n=$((n+1))
  echo "$n"
}
[ "$(wf_befunde "$wf")" = 0 ] || pruefe "die Kernpruefungen des Workflows sind am echten Workflow nicht gruen"
sed 's/^    needs: \[publish, publish-cloudsmith\]$/    needs: gates/' "$wf" > "$tmp/w1.yml"
[ "$(wf_befunde "$tmp/w1.yml")" != 0 ] || pruefe "needs: gates im Job release wird nicht erkannt"
awk '/^  vorschau:/ {v=1} v && /^      contents: read$/ {print "      contents: write"; next} {print}' "$wf" > "$tmp/w2.yml"
[ "$(wf_befunde "$tmp/w2.yml")" != 0 ] || pruefe "contents: write im Job vorschau wird nicht erkannt"
awk '/^  release:/ {r=1} r && /^        run: make release-dateien$/ {print "        env:"; print "          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}"} {print}' "$wf" > "$tmp/w3.yml"
[ "$(wf_befunde "$tmp/w3.yml")" != 0 ] || pruefe "das Token in einem Schritt vor gh release create wird nicht erkannt"
sed 's/^    runs-on: ubuntu-24.04$/    runs-on: ubuntu-24.04\n    continue-on-error: true/' "$wf" > "$tmp/w4.yml"
[ "$(wf_befunde "$tmp/w4.yml")" != 0 ] || pruefe "continue-on-error wird nicht erkannt"

if [ "$fehler" -ne 0 ]; then exit 1; fi
echo "release-anlegen-selbsttest: gruen"
