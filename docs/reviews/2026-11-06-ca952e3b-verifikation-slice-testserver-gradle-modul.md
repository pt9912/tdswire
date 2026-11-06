# Verifikation: slice-testserver-gradle-modul — 2026-11-06

**Rolle:** Verifier (Modul 11), "Bauen wir es richtig?" gegen DoD (§2 des Slice) und Entscheidungen ([ADR-0034](../plan/adr/0034-testserver-als-gradle-modul.md) `Accepted`). Kein Review, keine Reparatur, keine Entscheidung des Nutzers gesetzt.

**Gegenstand:** Zweig `slice-testserver-gradle-modul`, HEAD `ca952e3b` (Basis `cd0e2368` von `main`; Merge `a572c5de`). Bezug: [TDS-RB-01](../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-QA-05](../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [ADR-0034](../plan/adr/0034-testserver-als-gradle-modul.md). Review davor: [2026-11-06-661740c6-slice-testserver-gradle-modul.md](2026-11-06-661740c6-slice-testserver-gradle-modul.md).

## Eigene Sensor-Läufe (am Stand `ca952e3b`, sauberer Worktree)

| Lauf | Ergebnis |
|---|---|
| `make docs-check` | 844 Dateien, 0 Befunde, Exit 0 |
| `make gates FORCE=1` | Exit 0. **Cache-Falle G1:** alle Docker-Schichten `CACHED`; der Lauf belegt Stempel, Selbsttests auf dem Host (stempel-selbsttest 118, stempel-eingaenge 95, stempel-abbild 99, stempel-gruppe 64, gate-zuordnung-selbsttest 33 Fälle) und Doku, nicht frische Gradle-Arbeit |
| `make clean-build` (Frischbeleg, `--no-cache`: Jar, Tests, detekt) | Exit 0, 8 min 40 s; `BUILD SUCCESSFUL` je Stage, darunter 933 Zeilen `PASSED` (gleich M4.2 des Protokolls) |
| `make e2e` | Exit 0, "gruen (7 Stufen, 305 s)": Stufe 1 44 s, 2 99 s, 3 51 s, 4 44 s, 5 15 s, 6 15 s, 7 37 s; die Stufen bauten Testserver-Stages neu (`BUILD SUCCESSFUL in 25 s` bis `50 s`) |
| `make testserver-selbsttest` | Exit 0, "gruen", 70 s, alle Mutationen rot |
| M1 (Kopie von `HEAD` im Scratchpad, `docker build --no-cache --target compile`): Datei im Modul mit `import …simulation.SqlLexemes` und `internal fun mutationM1() = SqlLexemes` | **rot**: `:testserver:compileKotlin FAILED`, `Cannot access 'object SqlLexemes : Any': it is internal in file.` (3:44, 5:29); gleich Protokoll M1 |
| M2 positiv (`docker build --no-cache --target testserver`, Datei `/src/testserver/build/testserver-klassenpfad.txt`) | Klassenpfad: drei Verzeichnisse des Moduls, dann `/src/build/libs/tdswire.jar` (kein Klassenverzeichnis der Bibliothek), dann coroutines, stdlib, Jackson; das Jar trägt `META-INF/MANIFEST.MF` (kein `Main-Class`) und `META-INF/LICENSE` |
| M2 negativ (Kopie, `implementation(files(<Wurzel>))` neben `project(":")`) | **rot**: `Task :testserver:testserverKlassenpfad FAILED`, "nennt Verzeichnisse ausserhalb des Moduls: [/src]" |
| Statisch: `grep -ci jackson` | `gradle.lockfile` 0, `testserver/gradle.lockfile` 5 |
| `git diff cd0e2368..HEAD` auf `docs/plan/adr/`, `config/` (nur 1 Kommentarzeile `detekt.yml`), `src/` (nur Kommentar in `ServerProcess.kt`; sonst Move) | keine ADR geändert (§3.5), keine Regel gelockert (§3.6), `@Suppress` nicht hinzugekommen |

Nicht gefahren: `make deps-lock` (braucht Netz; M3 des Protokolls und des Reviews, dort Zählung 97/91/97 Koordinaten, nicht von mir wiederholt, nur die Jackson-Zählung statisch), Kaltlauf `make build`/`make smoke` einzeln (liefen in `clean-build` und `e2e` Stufe 1), `make kopplung-zahlen` und `make stempel-eingaenge-pruefung` einzeln (sie laufen in `make gates`, dort grün).

## DoD (§2)

| # | Punkt | Befund | Haken im Slice |
|---|---|---|---|
| 1 | Modul, Bindung, Umzug | Erfüllt. `include(":testserver")`; `testserver/build.gradle.kts` mit `implementation(project(":"))`; Umzug `76a2e07c` ist ein reiner Move (26 Renames, 0 Zeilen, danach Inhalt in eigenen Commits, §3.3); Tasks `test`, `testserverKlassenpfad`, `integrationsServer` (`maxHeapSize = "48m"`), `kompatibilitaetsServer` im Modul; Wurzel-`test` bekommt die Klassenpfad-Datei. M1 und M2 selbst gefahren (siehe oben), M4 (kein Zyklus): `clean-build`, `e2e` Stufe 1 grün. `testserverOhneAssoziation` und `src/testserver` nur noch in Zeitdokumenten/ADRs | `[x]`, vom Befund getragen |
| 2 | Lock, Dockerfile, detekt, Stempel-Eingänge | Erfüllt, soweit gefahren. Eigene `testserver/gradle.lockfile` (Jackson nur dort), `abhaengigkeitenLaden` liest beide Projekte, `DEPS_TASKS` ohne Modulnamen, `build-jar` mit `:assemble`, Stages kopieren `testserver`; detekt-Konfiguration unverändert (nur ein Kommentar); `tests.eingaenge` +`testserver` +`tools/modulgrenze-sperre.sh`, `werkzeug.eingaenge` +Build-Datei +Sperre, ein Pfad umbenannt, nichts entfernt; Pfade der Skripte stimmen (E2E grün). M3 (`make deps-lock`) nicht wiederholt | `[x]` |
| 3 | Spec, Doku, Messung | Erfüllt. `SPEC-040`, `SPEC-042`, `SPEC-052`, `SPEC-059` und Änderungsprotokoll nennen den Stand; `harness/README.md`, `docs/user/testserver-manuell.md`, Messprotokoll `docs/messungen/testserver-gradle-modul-2026-11-06.md` vorhanden (M7 mit "Last nicht erklärt" als Grenze). `docs-check` 0 Befunde | `[x]` |
| N | `make gates`, `make e2e` (7 Stufen), `make testserver-selbsttest` | Erfüllt (siehe Läufe; für `gates` mit dem Vorbehalt G1, ergänzt durch `clean-build`) | `[x]` |
| — | Review, Closure-Notiz, Register, Risiko-Ausgänge, Paarungen | Offen, wie im Slice: `[ ]`; das ist Sache von Planner/Closure, nicht gesetzt | `[ ]` (richtig, keine Haken vorab) |

## Entscheidungs-Konformität gegen ADR-0034

| Entscheidung | Befund |
|---|---|
| 1 Modul `:testserver`, Wurzel bleibt Bibliothek, `integrator`/`test` bleiben Source-Sets | konform |
| 2 `implementation(project(":"))`, Jar `tdswire.jar` im Klassenpfad | konform, gemessen (M2 positiv, negativ) |
| 3 Modulgrenze ersetzt `testserverOhneAssoziation` nach M1; Sperre für `friend`/`associate` | konform: M1 rot (selbst), Sensor entfernt, `tools/modulgrenze-sperre.sh` in Stage `detekt` und im Selbsttest (Modus `modulgrenze`); `integratorOhneAssoziation` unverändert. Grenze der Sperre (nur Text einer Datei) im Skriptkopf benannt |
| 4 Ort `testserver/`, Layout der Verzeichnisse | konform |
| 5 Lock/Prüfsummen, `deps-lock`, `DEPS_TASKS`, `abhaengigkeitenLaden` | konform (Jackson-Zählung 0/5 statisch bestätigt; `make deps-lock` nicht wiederholt); Lock-Aussage in `SPEC-059` trägt jetzt "einmalige Zählung am 2026-11-06, kein Gate hält sie" |
| 6 detekt | konform (`detekt.yml` ohne Regeländerung) |
| 7 Dockerfile | konform; Pfad im Abbild `/src/testserver/...` (Dockerfile, `tools/testserver.sh`) stimmt mit dem Stage-Layout |
| 8 Wurzel-`test` über auflösbare Konfiguration | **Abweichung**, siehe Nutzerpunkt (b) |
| 9 `integrator` bleibt Source-Set | konform |
| 10 Stempel-Eingänge | konform |

## Review-Findings F-1 bis F-12 am Text

| ID | Stand am Text |
|---|---|
| F-1 | Behoben: Kommentar `detekt.yml` (nur Kommentar), Köpfe der Dockerfile-Stages, Welle-Ziel und DoD-Punkt 3, Ausschluss der Welle sagen "Modul". Grep über `spec/`, `harness/README.md`, `README.md`, `tools/`, `Dockerfile`, `*.kts`, `testserver/`, `docs/user`, offene Pläne: keine Fundstelle "Source-Set `testserver`" mehr; ausgenommen bleiben `welle-produktschnitt.md` Z. 56 (Titel eines erledigten Slice), `done/`, `docs/messungen/`, `docs/reviews/`, angenommene ADRs |
| F-2 | Bestand unverändert, siehe Nutzerpunkt (a) |
| F-3 | Kommentar in `build.gradle.kts` nennt jetzt den Ist-Zustand mit Beleg M3.3; Abweichung im Slice §6 und im Protokoll benannt; Entscheidung offen, siehe (b) |
| F-4, F-5, F-10 | Behoben laut `ca952e3b` (Messprotokoll: M7 `smoke`, Tabellenkopf M2, Stufe-7-Folgerung); Messprotokoll nicht Zeile für Zeile neu gelesen |
| F-6 | Behoben (Kommentar "Die Verzeichnisse des Moduls") |
| F-7 | Behoben als Vermerk laut Commit (Messprotokoll); `explicitApi()` im Modul nicht gesetzt, `detekt.yml` unverändert |
| F-12 | Behoben: Datum und "kein Gate hält sie" in `SPEC-059` |
| F-8, F-9, F-11 | INFO, unverändert (Rest-Lücke der Sperre benannt; Zweig `fremd` von mir erneut rot gesehen; `.dockerignore`-Muster ohne `**/` nicht gefahren) |

## Offene Nutzerpunkte (nicht entschieden)

- **(a) F-2, [ADR-0020](../plan/adr/0020-logformat-und-observability-schnittstelle.md) (Z. 188) und [ADR-0026](../plan/adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md) (Z. 66):** beide `Accepted` führen weiter die Fitness-Function-Zeile "Source-Set `testserver`"; die Dateien sind im Diff unverändert (§3.5). Der Nutzerentscheid vom 2026-11-06 ("deine empfehlungen": keine Folge-ADR, benannte Grenze in Register/Slice) ist **im Slice nicht so geführt**: §6 des Slice steht auf "weiter offen (Entscheidung des Nutzers)" und nennt keine Entscheidung; im Diff gibt es keine Änderung unter `docs/plan/planning/observations/` und keine Grenzen-Zeile im Messprotokoll zu diesen beiden ADRs (grep 0 Treffer außerhalb des Slice §6). Der Nachzug (Ausgang in §6, Eintrag der Grenze im Register oder in §7) steht aus; er liegt beim Planner.
- **(b) F-3, Abweichung von [ADR-0034](../plan/adr/0034-testserver-als-gradle-modul.md) Entscheidung 8:** gebaut ist die Datei der Modul-Task (`testserverKlassenpfad`) statt einer auflösbaren Konfiguration `project(":testserver")`. Gemessen ist die Dateilösung (M3.3, M4, plus meine Läufe); die Konfigurationslösung ist nicht gemessen. Im Slice §6 als "weiter offen (ADR-Anpassung oder Hinnahme)" geführt, im Protokoll benannt. Offen: Hinnahme oder ADR-Anpassung (eine neue ADR; die angenommene bleibt unverändert).

## Verbleibende Befunde

- V-1 (INFO): `make gates FORCE=1` ist wegen vollständigem Layer-Cache kein Frischbeleg für Tests und detekt; der Frischbeleg ist `make clean-build` (933 `PASSED`). Der `make e2e`-Lauf baute die Testserver-Stages teilweise neu, vollständig kalt war er nicht.
- V-2 (INFO): M3 (`make deps-lock` ohne Versionsänderung) von mir nicht wiederholt (Netz); Jackson-Zählung statisch bestätigt, Prüfsummen-Diff nicht neu erklärt.
- V-3 (INFO): Slice §6 führt weiter Risiken mit Ausgang "offen bis zur Closure" (M1, M4, M3, M5, M6 sind durch Messung und meine Läufe belegt, aber nicht abgeschlossen); das Setzen der Ausgänge ist Closure-Sache.
- Keine DoD-Verletzung, keine Aussage über dem Beleg gefunden, kein Gate gelockert, keine Suppression, keine angenommene ADR geändert.

## Verdikt

**DoD-konform** für die drei Liefer-Punkte und die Nachweis-Pflicht (`gates`, `e2e` 7 Stufen, `testserver-selbsttest` selbst grün; M1 und M2 selbst wiederholt), mit den zwei offenen Nutzerpunkten (a) und (b), die Closure und ADR-Stand betreffen, nicht die DoD-Punkte selbst. Übergabe an den Planner: Punkte (a) und (b) beim Nutzer klären und in §6/§7 des Slice führen, Review-/Closure-/Register-Haken setzen; ich habe nichts außer diesem Report geändert.
