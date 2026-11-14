# Review-Report: slice-maven-veroeffentlichung — 2026-11-14

**Review-Art:** Code und Doku, gegen Plan, ADR und Hard Rules, nicht gegen die DoD (das ist die Frage des Verifiers).

**Gegenstand:** Diff `e92c4b3e..287866a4` im Zweig `worktree-agent-ad3bb353b929b3b87`: `edc7844c` (POM, Prüfung im Bau, Stage `publish`, `tools/publish.sh`, `tools/mk/publish.mk`, Selbsttests, `tools/publish-probe.sh`), `681a81b4` (Ausgabezeile des Probe-Skripts), `287866a4` (Messprotokoll, `releasing.md`, `SPEC-052`, `SPEC-059`).

**Skill:** `.harness/skills/reviewer.md` @ `287866a4` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-14

**Eingangs-Kontext:** Plan `slice-maven-veroeffentlichung` §1 bis §8, [ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md), `TDS-RB-05`, `TDS-QA-06`, `AGENTS.md` §3, Messprotokoll `docs/messungen/maven-paket-2026-11-14.md`.

## Selbst gefahren

Im Worktree, ohne Last, ein Gradle-Lauf. Kein Token, kein Netz zu GitHub, nichts veröffentlicht.

| Lauf | Ergebnis | Dauer |
|---|---|---|
| `make skript-selbsttest` | Exit 0 (aus dem Cache) | 1 s |
| `make docs-check` | Exit 0, 1445 Dateien, 0 Befunde | 3 s |
| `make flaeche` | Exit 0 | 1 s |
| `make stempel-eingaenge-pruefung` | Exit 0 | unter 1 s |
| `make kopplung-zahlen` | Exit 0 | 1 s |
| `make publish-vorschau` | Exit 0; die ausgegebene POM führt `kotlinx-coroutines-core-jvm` 1.10.1 und `kotlin-stdlib` 2.4.10 je mit `<scope>compile</scope>`, Lizenz MIT mit URL, `scm`, kein Jackson, kein Testserver | 11 s |

Gesamtlaufzeit rund 2 Minuten, überwiegend Lesen. Die Mutationen des Protokolls (M6) habe ich nicht wiederholt; ich habe sie an den Skripten gelesen und die Fälle nachgezählt (elf rote Fälle vor Docker, acht POM-Fälle, wie die Ausgabezeilen sagen).

## Prüfung der Schwerpunkte

1. **Aussagen gegen Belege (§3.8).** `SPEC-059` „Abhängigkeiten“ und `SPEC-052` stimmen mit der gelaufenen POM überein (Scope `compile`, `kotlin-stdlib`). Zwei Stellen sind breiter als ihr Beleg: siehe F-3 und F-4.
2. **Token.** Der Wert geht nur als `-e GITHUB_ACTOR -e GITHUB_TOKEN` an `docker run` (`tools/publish.sh`, Selbsttest `gut` liest die Form des Aufrufs und sucht den Platzhalter im Stub-Aufruf und in der Ausgabe); die Stage `publish` kennt kein `ARG` und kein Token; im Skript steht kein `set -x`; das Skript gibt nur das Ziel aus. Das Protokoll durchsucht drei Formen (Klartext, Base64 des Tokens, Base64 von `Benutzer:Token`) in vier Quellen und fährt eine Positivprobe (M4.4); die Grenze steht im Protokoll und im Probe-Skript. Mutation „Wert im `docker run`“ ist im Protokoll belegt. Ohne Befund bis auf F-1.
3. **Eingangsskript.** Reihenfolge der Prüfungen, Form `<zahl>.<zahl>.<zahl>`, Tag auf `HEAD`, Baum inklusive unversionierter Dateien, leeres Token: je ein roter Fall und die Prüfung, dass Docker nicht gerufen wurde. `make publish` reicht kein `--url` weiter. Befunde: F-1 (Ziel), F-2 (annotierter Tag).
4. **POM-Prüfung.** Jeder Zweig der Prüfung hat einen Fall (fehlt, Jackson, Testserver, ohne Lizenz, andere Lizenz, ohne URL, URL ohne Schema, MIT nur im Kommentar). Die Prüfung liest Text, die Grenze steht im Kopf. Die Prüfung gilt der POM, nicht der `.module`-Datei; das Protokoll nennt es unter „Nicht gemessen“. Ohne Befund; zur zweiten Fundstelle siehe F-5.
5. **Abwesenheit in Gate-Listen und `e2e.mk`.** `publish` und `publish-vorschau` stehen in keiner Gate-Liste und nicht in `e2e.mk` (am Text gelesen). Der Selbsttest prüft das mit zwei Gegenproben an einer Kopie; Text, keine Auswertung von Make, Grenze steht im Kopf. Ohne Befund; die Grenze (`BEO-TDS/zusage-ohne-sensor-in-der-ci-kette`) gilt weiter.
6. **Gradle-Metadaten, Scope, Sperre.** Neben der POM entsteht `.module` (Protokoll M2, M5.3). `api` für coroutines ist gemessen (M1.1 gegen M1.2). `gradle.lockfile`, `settings-gradle.lockfile`, `testserver/gradle.lockfile` und `gradle/verification-metadata.xml` stehen nicht im Diff; M5.2 nennt den Lauf von `make deps-lock`. `DEPS_TASKS` ist um zwei Tasks erweitert. `make flaeche` grün, die Zahl der Liste ändert sich nicht; der Eintrag im Änderungsprotokoll von `SPEC-059` nennt die Änderung. Ohne Befund.
7. **Doku-Gate, Ruheort, Zahlen.** `make docs-check` 0 Befunde. Der Plan liegt in `in-progress/`; die neuen Dateien verlinken keinen Pfad, der erst im Ruheort gilt. Zahlen im Selbsttest-Kopf (elf, drei, acht, dreizehn) stimmen mit den Fällen im Skript überein.
8. **Host-Lauf.** Siehe F-6.
9. **Schnitt des Slice.** Eingehalten: `version` bleibt `0.1.0-SNAPSHOT`, `make publish` bricht dort vor Docker; ein Schreiben in die Registry braucht Token und Tag auf `HEAD`; das Protokoll nennt für jeden Lauf das Ziel (lokales Repository, interner Docker-Netz-Dummy). `:testserver` bekommt kein `maven-publish`; die 21 Anfragen von M4.2 betreffen nur `tdswire`-Dateien.

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| F-1 | MEDIUM | Das Eingangsskript nimmt mit `--url` jede `http(s)`-Adresse an und gibt das echte Token dann an `gradle publish` weiter; für `http://` setzt `build.gradle.kts` `isAllowInsecureProtocol = true`. Failure-Szenario: ein Maintainer ruft `bash tools/publish.sh --url http://anderer-host/...` auf einem Release-Commit mit gesetztem Token auf (Tippfehler, kopierter Befehl) und sendet das Token im Klartext an einen fremden Host; keine Prüfung hält `--url` und ein echtes Token auseinander, und `make publish` selbst reicht kein `--url` weiter (der Weg ist nur direkt erreichbar). Die Ausgabezeile nennt das Ziel, bricht aber nicht ab. | [ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md) Entscheidung 6 (Token nirgends außer im Lauf), Sicherheits-Anti-Pattern | `tools/publish.sh` · `--url) [ "$#" -ge 2 ]` und `build.gradle.kts` · `isAllowInsecureProtocol = registryUrl.startsWith("http://")` | nein (der Selbsttest nimmt `--url http://registry.invalid:8080/repo` als guten Fall an) | Ziel des Tokens frei wählbar |
| F-2 | MEDIUM | `tag_commit=$(git ... rev-parse -q --verify "refs/tags/v$version^{commit}")` ist der Zweig, der einen annotierten Tag (den `releasing.md` Schritt 8 mit „Beschreibung des Tags“ nahelegt) auf den Commit auflöst; der Selbsttest legt nur leichte Tags an (`git tag "v$v"`, kein `tag -a`), die Entfernung von `^{commit}` lässt jeden Test grün. Failure-Szenario: ein späterer Umbau streicht das Suffix, und ein Release mit annotiertem Tag bricht mit „liegt nicht auf HEAD“; der Zweig ist nur im Kopf belegt. | Maintainability (Eigencode ohne Test) | `tools/publish.sh` · `refs/tags/v$version^{commit}`; `tools/publish-selbsttest.sh` (kein `tag -a`) | ja (Fall mit annotiertem Tag) | Eigencode ohne tragenden Test |
| F-3 | MEDIUM | Zwei Orte nennen dieselbe Zuordnung ohne Sensor oder Kopplungskommentar: (a) das Standardziel `https://maven.pkg.github.com/pt9912/tdswire` steht in `build.gradle.kts` (`getOrElse`) und in `tools/publish.sh` (`url=`), und `publish.sh` übergibt es immer, sodass der Wert in `build.gradle.kts` nur ein direkter `gradle publish` im Container erreicht; (b) `tools/publish.sh` liest die Version mit `sed -n 's/^version = "\(.*\)"$/\1/p'` aus `build.gradle.kts`, die Zeilenform steht dort in keinem Kommentar, und eine Umstellung der Zeile (Katalog, `val`) liefert „nennt keine Version“ (sicher, aber ohne Hinweis auf die Ursache). Dazu stehen Koordinaten, Lizenzname und Projekt-URL in `build.gradle.kts`, in `SPEC-052` und im Test-POM `pom_gut` von `manifest-selbsttest.sh` (eine Kopie der Gradle-Ausgabe von Hand). | Maintainability (Zahl oder Zuordnung an einem zweiten Ort ohne Sensor), `AGENTS.md` §3.7 Klasse Kopplung | `tools/publish.sh` · `url="https://maven.pkg.github.com/pt9912/tdswire"`; `build.gradle.kts` · `.getOrElse("https://maven.pkg.github.com/pt9912/tdswire")` | ja | zweite Fundstelle ohne Sensor |
| F-4 | LOW | Das Messprotokoll sagt, das Token sei „in `/proc` sichtbar“ und belegt es mit M4.4; M4.4 zeigt nur `docker inspect`, `/proc` ist nicht gelesen. Außerdem sagt `SPEC-059` „ein Konsument findet `Flow` bei der Übersetzung nicht“ ohne die Bedingung des Protokolls (Gradle-Konsument mit Kotlin; Java und Maven ungemessen) im selben Satz; das Protokoll nennt sie, die Spezifikation nicht. | `AGENTS.md` §3.8 | `docs/messungen/maven-paket-2026-11-14.md` · `in `/proc` sichtbar`; `spec/spezifikation.md` · `findet ein Konsument `Flow` bei der Übersetzung nicht` | nein | Aussage breiter als der Beleg |
| F-5 | LOW | Die Plan-Tabelle §3 führt Änderungen an `tools/stempel/*.eingaenge` auf; der Diff berührt sie nicht. Die Listen führen `tools`, `Dockerfile`, `harness/README.md` und `build.gradle.kts` als Verzeichnis oder Datei, sodass die neuen Dateien überdeckt sind (`make stempel-eingaenge-pruefung` grün); die Abweichung vom Plan ist nirgends benannt. | Plan §3 und DoD Punkt 2 | `docs/plan/planning/done/slice-maven-veroeffentlichung.md` · `tools/stempel/werkzeug.eingaenge` | ja | Plan und Diff weichen ohne Vermerk ab |
| F-6 | LOW | Der Implementer hat nach eigenem Hinweis `sh tools/manifest-selbsttest.sh` auf dem Host gefahren; der Host führt `jar` und `java` (`/usr/bin/jar`), das Skript braucht `jar`. Der Diff ruft den Selbsttest nicht auf dem Host auf, `make skript-selbsttest` läuft in Docker; der Lauf ist ein Abweichen von `AGENTS.md` §3.1 im Vorgehen, nicht im Artefakt. | `AGENTS.md` §3.1 | `tools/manifest-selbsttest.sh` · `jar --create` | nein | Host-Lauf eines Docker-Werkzeugs |
| F-7 | INFO | Die Prüfung der POM gilt nur der POM-Datei; die `.module`-Datei, die Gradle-Konsumenten vorziehen, hat keine Prüfung auf Jackson oder Testserver. Das Protokoll nennt es unter „Nicht gemessen“. Verantwortlich wäre der Verifier oder ein Folge-Slice. | `TDS-RB-05`, [ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md) | `docs/messungen/maven-paket-2026-11-14.md` · `Die `.module`-Datei` | ja | Prüfung deckt nur eine der zwei Metadaten-Dateien |
| F-8 | INFO | Der Selbsttest der Abwesenheit liest Gate-Listen als Text mit einem Muster für `GATE_CHECKS`, `GATE_KLASSE_*`, `GATE_STAGE`; eine Zuweisung über Variablen oder fortgesetzte Zeilen sieht er nicht. Grenze steht im Kopf des Skripts; Beobachtung `BEO-TDS/zusage-ohne-sensor-in-der-ci-kette` gilt. | [ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md) Fitness Function | `tools/publish-selbsttest.sh` · `Die Abwesenheitspruefung liest Make-Dateien als Text` | nein | Sensor nur Text |

## Negativbefund (geprüft, ohne Befund)

- `Dockerfile` (Stage `publish`, `build-jar`, `skript-selbsttest`): kein Token, kein `ARG`, `--offline` nur dort, wo nichts hochgeladen wird; ohne Befund.
- `build.gradle.kts`: `maven-publish` nur im Wurzelprojekt, `testserver/build.gradle.kts` unverändert; Suppression-Verbot nicht berührt; ohne Befund.
- `tools/mk/publish.mk`, `tools/mk/build.mk`, `tools/mk/skript-selbsttest.mk`: kein `GATE_CHECKS` für die Targets, Token in keiner Rezeptzeile, `.PHONY` gesetzt; ohne Befund.
- `tools/manifest-pruefung.sh`, `tools/manifest-selbsttest.sh`: ohne Befund (siehe F-7 für die Grenze).
- `tools/publish-probe.sh`: kein echtes Token (`GITHUB_TOKEN` der Umgebung wird nicht gelesen), Dummy im Netz `--internal`, Werte nur namentlich an Container; ohne Befund.
- `harness/README.md`: Zeilen der beiden Targets und von `make skript-selbsttest` vorhanden, Bindung [ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md); ohne Befund.
- `docs/maintainer/releasing.md`, `spec/spezifikation.md` (`SPEC-052`, `SPEC-059`, Änderungsprotokoll): gegen Messprotokoll und POM gelesen; Grep nach der alten Lesart („Registry-Weg gibt es nicht“, „Versionsschema … nicht entschieden“) findet keine Stelle außerhalb des Plans und der historischen ADRs; ohne weiteren Befund außer F-4.
- `docs/user/`: nicht im Diff, kein Treffer auf Registry oder Maven außer dem Hinweis „keine Registry“ zum Test-Image (`testserver-manuell.md`, gilt unverändert); ohne Befund.
- Gradle-Sperre und Prüfsummen: nicht im Diff; M5 nennt die Läufe; ohne Befund.
- Haken der DoD im Plan: keine `- [x]` im Diff; ohne Befund.
- Test-KDoc, Kommentare in Skripten: Indikativ, keine verworfene Alternative; ohne Befund.
