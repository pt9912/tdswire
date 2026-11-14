# Review-Report 2: slice-maven-veroeffentlichung, Nachbesserung — 2026-11-14

**Review-Art:** Zweiter, enger Review (Nachbesserung mit Code), gegen Plan, ADR und Hard Rules, nicht gegen die DoD.

**Gegenstand:** `git diff 82b7eca2..20078de0` im Zweig `worktree-agent-ad3bb353b929b3b87`: `7b0add15` (Ziel des Tokens, annotierter Tag, Kopplung), `20078de0` (Doku auf den Beleg, Abweichung vom Plan, Host-Lauf). Vorgänger: [Report 1](2026-11-14-287866a4-slice-maven-veroeffentlichung-review.md) (F-1 bis F-6).

**Skill:** `.harness/skills/reviewer.md` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-14

**Eingangs-Kontext:** Plan `slice-maven-veroeffentlichung`, [ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md), `TDS-RB-05`, `TDS-QA-06`, `AGENTS.md` §3, Report 1.

## Selbst gefahren

Kein Token, keine Registry, kein Gradle-Lauf, keine Last.

| Lauf | Ergebnis | Dauer |
|---|---|---|
| `make docs-check` | Exit 0, 1446 Dateien, 0 Befunde | 2 s |
| `make skript-selbsttest` | Exit 0; Stages aus dem Cache (Eingaben gleich dem Stand, in dem sie liefen) | 1 s |
| `make stempel-eingaenge-pruefung` | Exit 0 | unter 1 s |
| `make flaeche` | Exit 0 | unter 1 s |

Die neuen Mutationszeilen des Messprotokolls (sechs) habe ich nicht wiederholt; ich habe die Fälle gelesen und gedanklich gegen die Mutationen gehalten (siehe je Finding). `make publish-vorschau` nicht gefahren: der Diff ändert weder `publish.mk` noch die Stage `publish`; gelesen: der Pfad hängt an `publishToMavenLocal` ohne Token (Report 1, Punkt 22, unverändert).

## Stand der Findings aus Report 1

| ID | Stand | Beleg |
|---|---|---|
| F-1 | behoben (mit LOW-Rest, siehe G-1) | `tools/publish.sh:56-63` Bindung: anderes Ziel als `standard_url` verlangt `PLATZHALTER-?*`, sonst `fehler` vor Docker; Fälle `fremdes-ziel`, `fremdes-ziel-https`, `platzhalter-leer` (`tools/publish-selbsttest.sh`, Prüfung steht vor `docker run`); der Fall `standard` nimmt jetzt ein echtes Token und prüft, dass sein Wert nicht in Ausgabe und Docker-Aufruf steht |
| F-2 | behoben | Fall `annotiert` mit `git tag -a` (`publish-selbsttest.sh`); Streichen von `^{commit}` ließe `rev-parse` die Tag-Objekt-Kennung liefern, die nicht `HEAD` ist, der Fall würde rot (am Text hergeleitet, Messprotokoll nennt die Mutation) |
| F-3 | teilweise | Standardziel: Sensor in `publish-selbsttest.sh` (`standard_url` gegen `getOrElse`), Kommentar „Kopplung:“ in `publish.sh:22`; Versionszeile: Sensor und Kommentar im Kopf von `publish.sh`; Koordinaten: `manifest-selbsttest.sh` koppelt `groupId`, Lizenzname und Lizenz-URL, siehe G-2 |
| F-4 | behoben | Messprotokoll sagt `docker inspect`, `/proc` nicht gelesen; `SPEC-059` trägt die Bedingung (Gradle-Konsument mit Kotlin; Java und Maven nicht gemessen) im selben Satz |
| F-5 | behoben | Plan §3 „Abweichung bei der Umsetzung“; belegt: `tools/stempel/werkzeug.eingaenge` führt `build.gradle.kts`, `tests.eingaenge` ebenso; `make stempel-eingaenge-pruefung` grün |
| F-6 | behoben (Wortlaut ehrlich) | Messprotokoll §„Vorgehen“ benennt den Host-Lauf als Fehler im Vorgehen, mit Verweis auf `AGENTS.md` §3.1 |

## Prüfung der Schwerpunkte

1. **Token (F-1).** Der Wert geht weiter nur als `-e GITHUB_ACTOR -e GITHUB_TOKEN` an `docker run` (Zeilen unterhalb der Bindung unverändert); die neue Prüfung gibt den Wert nicht aus, ihre Meldung nennt nur das Standardziel; kein `set -x`. Die Bindung steht nach den Prüfungen von Version, Tag, Baum und leeren Zugangsdaten und vor `docker`; jeder rote Fall prüft, dass der Stub nicht gerufen wurde. Ein Ziel mit anderer Schreibweise des Standardziels (zum Beispiel abschließender `/`) gilt als anderes Ziel und verlangt den Platzhalter: sicher. `make publish` reicht kein `--url`, `make publish-vorschau` ist von der Änderung nicht berührt (kein Token, kein Netz zu GitHub, `publishToMavenLocal` im Container).
2. **Sensor F-2/F-3 wirksam?** Ja für den annotierten Tag, das Standardziel und die Versionszeile (je ein roter Fall, im Messprotokoll mit Mutation genannt). Der Selbsttest liest `build.gradle.kts` über `$wurzel_repo`; fehlt die Datei, wird rot gemeldet statt still übergangen. Die Stage `skript-selbsttest` kopiert sie jetzt (`Dockerfile`, `COPY build.gradle.kts build.gradle.kts`); die Datei steht in `werkzeug.eingaenge`, `stempel-eingaenge-pruefung` grün.
3. **Aussagen gegen Belege (§3.8).** Neu: `releasing.md` Schritt 9 „ein echtes Token geht nie an ein anderes Ziel“ (G-1). Messprotokoll M4 „Gesehen ist das am Selbsttest mit Docker-Stub, nicht an einem Lauf gegen ein fremdes Ziel“: Bedingung im selben Satz, ohne Befund.
4. **Docker-only.** Kein neuer Host-Aufruf im Diff; `make skript-selbsttest` läuft in Docker.
5. **Kennungen und Anker.** `make docs-check` 0 Befunde; die neuen Verweise im Messprotokoll (`../plan/adr/0047-…`) lösen auf; der Plan trägt die Abweichung ohne neuen Pfad. Keine `- [x]` im Diff.
6. **Kommentare (§3.7).** Neue Kommentare in `publish.sh`, `manifest-selbsttest.sh` und `publish-selbsttest.sh` tragen Zusage, Kopplung oder Grenze im Indikativ; ohne Befund.

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| G-1 | LOW | `docs/maintainer/releasing.md` Schritt 9 und der Kopf von `publish.sh` sagen „ein echtes Token geht nie an ein anderes Ziel“. Beleg ist eine Präfixprüfung (`PLATZHALTER-?*`) in `tools/publish.sh`; sie gilt nur auf dem Weg über das Eingangsskript (ein direkter `gradle publish -PtdswireRegistryUrl=…` im Container, wo `isAllowInsecureProtocol` für `http://` gilt, oder ein von Hand `PLATZHALTER-` genanntes echtes Token umgehen sie) und ist am Docker-Stub gesehen, nicht an einem fremden Ziel. „nie“ trägt weiter als der Beleg. Einschränken auf „über `tools/publish.sh`“ und „Präfix“. | `AGENTS.md` §3.8 | `docs/maintainer/releasing.md` · `ein echtes Token geht nie an ein anderes Ziel`; `tools/publish.sh` · `ein echtes Token geht nie an ein anderes Ziel` | ja | Aussage breiter als der Beleg |
| G-2 | LOW | Die Kopplung der guten POM an `build.gradle.kts` deckt `groupId`, Lizenzname und Lizenz-URL; `artifactId`, Projekt-URL und die Koordinaten in `SPEC-052` (Fundstellen aus F-3 in Report 1) haben weiter keinen Sensor und keinen Kopplungskommentar. Report 1 nannte Koordinaten, Lizenzname und Projekt-URL gemeinsam; `20078de0` benennt nicht, warum der Rest bleibt. | Maintainability, `AGENTS.md` §3.7 Klasse Kopplung | `tools/manifest-selbsttest.sh` · `Zweite Fundstelle: Koordinaten, Lizenzname und Lizenz-URL` (Kommentar sagt „Koordinaten“, geprüft ist nur `group`) | ja | zweite Fundstelle teilweise ohne Sensor |
| G-3 | INFO | Die sechs neuen Mutationszeilen im Messprotokoll stehen unter der Aussage „Jede Mutation wurde … eingebaut“; ich habe sie nicht wiederholt. Gelesen sind sie plausibel (Entfernen der Bindung, Muster `*`, Streichen von `^{commit}`, geänderte Konstanten). Der Verifier kann eine davon einmal fahren. | Skill, MEDIUM „Mutationsbeschreibung nennt die gedachte statt der gemessenen Wirkung“ | `docs/messungen/maven-paket-2026-11-14.md` · Mutationstabelle | ja | nicht nachgefahren |
| G-4 | INFO | `isAllowInsecureProtocol = registryUrl.startsWith("http://")` in `build.gradle.kts` bleibt; die Bindung schützt den Weg über `publish.sh`, nicht die Gradle-Datei selbst. Kein Befund gegen den Plan, die Grenze sollte bei G-1 mitgenannt werden. | [ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md) Entscheidung 6 | `build.gradle.kts` · `isAllowInsecureProtocol` | nein | Grenze |

HIGH: keine. MEDIUM: keine.

## Negativbefund (geprüft, ohne Befund)

- `tools/publish.sh`: Reihenfolge der Prüfungen unverändert, neue Prüfung vor Docker, kein Wert des Tokens in Ausgabe oder Argument; ohne Befund.
- `tools/publish-selbsttest.sh`: Fälle haben je Meldung und „Docker nicht gerufen“; Zahlen der Ausgabezeile (elf, drei fremdes Ziel, drei Aufruffehler) stimmen mit den Fällen überein; ohne Befund.
- `Dockerfile`: eine zusätzliche `COPY`-Zeile in `skript-selbsttest`; kein Token, kein `ARG`; ohne Befund.
- `spec/spezifikation.md` (`SPEC-059`): Bedingung im Satz; Grep nach der alten Lesart („mit `implementation` findet ein Konsument `Flow` … nicht“) findet keine weitere Stelle; ohne Befund.
- Plan: Abweichung benannt; Haken unverändert; ohne Befund.

**Laufzeit:** rund 5 Minuten, überwiegend Lesen; größter Zeitposten Diff-Lektüre, Sensoren zusammen unter 10 s (Docker-Cache).
