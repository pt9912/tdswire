# Verifikationsbericht: slice-maven-veroeffentlichung — 2026-11-14

**Rolle:** Verifier („Bauen wir es richtig?", gegen Plan, DoD und [ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md)). **Modell:** claude-sonnet-5-5.

**Gegenstand:** Zweig `worktree-agent-ad3bb353b929b3b87`, Stand `fbce5d7d` (Basis `e92c4b3e`), Plan `docs/plan/planning/in-progress/slice-maven-veroeffentlichung.md`, Reports [1](2026-11-14-287866a4-slice-maven-veroeffentlichung-review.md) und [2](2026-11-14-20078de0-slice-maven-veroeffentlichung-review-2.md), Messprotokoll [`maven-paket-2026-11-14.md`](../messungen/maven-paket-2026-11-14.md), [`releasing.md`](../maintainer/releasing.md).

## Ergebnis

**Konform, mit Befunden ohne Blockade (0 HIGH, 0 MEDIUM, 2 LOW, 2 INFO).** Beide Pflichtläufe sind auf dem End-Stand grün, `make publish-vorschau` liefert die erwartete POM, eine nachgefahrene Mutation wird rot. Kein Token, keine Registry, kein Push, keine künstliche Last; nichts wurde nach GitHub geschrieben.

## Selbst gefahren (Docker, Vordergrund, Last 4 bis 6, ein Gradle-Lauf zur Zeit)

| Lauf | Exit | Dauer | Beleg |
|---|---|---|---|
| `make clean-build` | 0 | 10 min 54 s | `manifest-pruefung: ok (keine Main-Class, kein Testserver, kein Jackson, 3 Class-Path-Eintraege in lib/, POM ohne Jackson und Testserver mit Lizenz MIT)` in `build-jar`; Tests PASSED, kein Testfall FAILED (acht Treffer auf „FAILED" sind Namen grüner Tests); detekt grün. Größte Posten: Stage `test` mit Gradle 3 min 38 s und `build-jar` 3 min 19 s (`--no-cache`) |
| `make e2e` | 0 | 6 min 34 s (Skript: 394 s) | acht Stufen grün: 1 `smoke` 84 s, 2 `laufzeittest` 111 s, 3 `simulations-integration` 58 s, 4 `Szenario` 52 s, 5 `Anmeldung` 18 s, 6 `Luecken` 18 s, 7 `Synchronitaet` 37 s, 8 `eigenes-backend` 16 s. Größter Posten Stufe 2 |
| `make publish-vorschau` | 0 | 11 s | Ausgabe: `.jar`, `.module`, `.pom`, `maven-metadata-local.xml`; POM mit `io.github.pt9912:tdswire:0.1.0-SNAPSHOT`, Lizenz „MIT License" mit URL, `url`, `scm`, Abhängigkeiten nur `kotlinx-coroutines-core-jvm` 1.10.1 und `kotlin-stdlib` 2.4.10 (je `compile`); kein Jackson, kein Testserver |
| `make skript-selbsttest` (Basis) | 0 | Cache | |
| Mutation: Suffix `^{commit}` in `tools/publish.sh` entfernt (Review F-2, Zeile „annotiert" des Messprotokolls), danach `make skript-selbsttest` | 2 (Build-Schritt Exit 1) | etwa 20 s | rot: `publish-selbsttest: FEHLER - annotiert: Exit 1 statt 0 (… der Tag v0.3.0 liegt nicht auf HEAD …)` und `annotiert: die Meldung nennt nicht [Version 0.3.0]`; Mutation per `git checkout` zurückgenommen, Baum sauber |

Gesamtlaufzeit des Verifiers rund 25 Minuten, davon 18 Minuten die beiden Pflichtläufe. Auf dem Host lief nur `make`, `git`, `sed`, `grep`; die Mutation geschah im Arbeitsbaum und der Selbsttest in Docker.

## DoD-Zeilen mit Beleg

| DoD | Stand | Beleg |
|---|---|---|
| 1 POM und Prüfung im Bau: `maven-publish` im Wurzelprojekt, POM mit Koordinaten, MIT, URL, `scm`; `:testserver` ohne `maven-publish` | erfüllt | `build.gradle.kts` (`publishing {}`), `testserver/build.gradle.kts` nicht im Diff; ausgegebene POM oben |
| 1 Scope von coroutines gemessen und entschieden | erfüllt | M1.1/M1.2 (`api` gegen `implementation`, rot/grün), Entscheid `api`; `build.gradle.kts` trägt `api` |
| 1 Stage `build-jar` erzeugt POM ohne Netz, `manifest-pruefung.sh` weist Jackson, Testserver, fehlende MIT ab; je Regel eine Mutation im Selbsttest | erfüllt | `RUN gradle --offline :generatePomFileForMavenPublication` und Prüfzeile im `clean-build`-Log; Fälle `pom-jackson`, `pom-testserver`, `pom-fehlt`, `pom-mit-nur-im-kommentar`, `pom-lizenz-url-ohne-schema` (Messprotokoll M6; im Selbsttest grün) |
| 1 Sperre und Prüfsummen unverändert | erfüllt | `gradle.lockfile`, `settings-gradle.lockfile`, `testserver/gradle.lockfile`, `gradle/verification-metadata.xml` nicht im Diff `e92c4b3e..fbce5d7d`; `make clean-build` bei aktiver Sperre grün |
| 2 Stage `publish` ohne Token; `publish-vorschau`; `publish` mit `-e GITHUB_ACTOR -e GITHUB_TOKEN`; kein Gate, nicht in `make e2e` | erfüllt | `Dockerfile` (`FROM compile AS publish`, kein `ARG`), `tools/mk/publish.mk` (kein `GATE_CHECKS`), `tools/publish.sh` (nur namentlich); Abwesenheit hält `publish-selbsttest.sh` mit zwei Gegenproben an einer Kopie |
| 2 Eingangsskript bricht vor Docker bei `-SNAPSHOT`, Tag nicht auf `HEAD`, Baum unsauber, Token leer; Selbsttest in `make skript-selbsttest` | erfüllt | Selbsttest grün; Mutation „annotiert" nachgefahren (rot) |
| 2 Zeilen in `harness/README.md`; Eingangslisten | erfüllt, mit Abweichung | beide Targets in der Werkzeug-Tabelle (Bindung [ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md)); `tools/stempel/*.eingaenge` nicht geändert, im Plan §3 als Abweichung benannt (Review F-5); `make stempel-eingaenge-pruefung` laut Report 2 grün, von mir nicht wiederholt |
| 3 Messprotokoll (a) bis (d), `releasing.md`, `SPEC-052`, `SPEC-059`, `docs/user/` | erfüllt | M2 (a), M3 (b), M4 (c, d), `releasing.md` Schritte 5, 6, 8, 9; `docs/user/` nicht im Diff, Grep nach Maven, Registry, GitHub Packages findet nur „keine Registry" zum Test-Image |
| Rote Gegenfälle je Zusage | erfüllt | M6, 19 Zeilen; eine nachgefahren |
| `make gates` grün; Review-Report liegt vor | Review: erfüllt; `make gates` nicht von mir gefahren (Implementer-Beleg, außerhalb der Verifier-Tabelle) | Reports 1 und 2 |
| Haken, Closure-Notiz, Register, Risiko-Ausgänge, Paarungen | offen, erwartet | Plan §2 und §7 stehen auf „wird bei der Closure gefüllt"; keine `- [x]` im Diff. Das ist der Closure-Schritt, kein Mangel dieses Stands |

## ADR-0047-Konformität

| Entscheidung | Stand | Beleg |
|---|---|---|
| 1a Koordinaten `io.github.pt9912:tdswire` | konform | POM |
| 2a dasselbe Jar mit `Class-Path`, POM führt die Abhängigkeiten, `lib/` bleibt Handweg | konform | M2.1 bis M2.3; Manifest-Prüfung `3 Class-Path-Eintraege` |
| 2 POM ohne Jackson und Testserver, Mutation im Selbsttest | konform | `clean-build`-Log, M6 |
| 3a `0.y.z`, `-SNAPSHOT` bleibt im Quelltext, Tag `v<Version>`, kein `-SNAPSHOT` veröffentlicht | konform | `version = "0.1.0-SNAPSHOT"`, `publish.sh` |
| 4a keine Signierung, kein `sources`/`javadoc` | konform | nichts davon in `build.gradle.kts`, Ausgabe von `publish-vorschau` ohne `.asc` und ohne zusätzliche Jars |
| 5a GitHub Packages | konform | Standardziel `https://maven.pkg.github.com/pt9912/tdswire` |
| 6a Token nur namentlich, Stage ohne Token, `publish` kein Gate | konform | `publish.sh`, `Dockerfile`, `publish.mk`; Messung M4 (siehe Befund V-1) |
| 8a kein Binärkompatibilitätswerkzeug | konform | keines vorhanden |

## Review-Findings im Stand

| Finding | Stand | Beleg |
|---|---|---|
| F-1 Ziel des Tokens | behoben | `publish.sh` bindet ein anderes Ziel an `PLATZHALTER-?*`; Fälle `fremdes-ziel`, `fremdes-ziel-https`; die Mutation „Bindung entfernt" steht im Protokoll (von mir nicht nachgefahren) |
| F-2 annotierter Tag | behoben | von mir nachgefahren: rot ohne `^{commit}` |
| F-3 zweite Fundstellen | teilweise, benannt | Standardziel und Versionszeile mit Sensor; `artifactId`, Projekt-URL, `SPEC-052`-Koordinaten ohne Sensor, als offene Grenze im Plan geführt (G-2) |
| F-4 Aussagen breiter als Beleg | behoben | Messprotokoll („`/proc` nicht gelesen"), `SPEC-059` mit Bedingung im Satz |
| F-5 Plan-Abweichung | behoben | Plan §3 „Abweichung bei der Umsetzung" |
| F-6 Host-Lauf | behoben (Wortlaut) | Messprotokoll §Vorgehen |
| G-1 „nie" breiter als Beleg | behoben | `releasing.md` Schritt 9 und Kopf von `publish.sh` nennen Weg und Präfixprüfung als Grenze (`fbce5d7d`) |
| G-2 Kommentar „Koordinaten" | behoben | Kommentar in `manifest-selbsttest.sh` sagt `group`; Rest als offene Grenze im Plan |
| G-3 Mutation nicht nachgefahren | behoben durch diesen Bericht | eine der sechs Zeilen (`annotiert`) gefahren, rot |
| G-4 `isAllowInsecureProtocol` | als Grenze genannt | `publish.sh` und `releasing.md` nennen sie |

## Befunde

| ID | Schwere | Befund | Quelle |
|---|---|---|---|
| V-1 | LOW | Das Messprotokoll gibt als Stand „Commit `edc7844c`" an. M4 (Platzhalter-Beleg, 21 Anfragen, 0 Treffer) lief vor `7b0add15`, das den Pfad von `publish.sh` um die Bindung des Ziels ändert; ein Lauf `publish-probe.sh token` auf dem End-Stand ist nicht belegt. Die Probe nutzt einen Platzhalter und damit den Zweig, der die Bindung passiert; das ist aus dem Skript gelesen, nicht gemessen. Die Mutationszeilen M6 sind dagegen nach den Änderungen gelaufen. Die Aussage von M4 trägt für `edc7844c`, für den End-Stand ist sie hergeleitet. Einschränken („gemessen auf `edc7844c`") oder `publish-probe.sh token` einmal nachfahren (Platzhalter, internes Docker-Netz, kein Gradle-Konkurrenzlauf nötig). Ich habe es nicht gefahren, weil die Verifier-Tabelle es nicht verlangt | `AGENTS.md` §3.8; Messprotokoll Kopf „Stand" |
| V-2 | LOW | Fünf der sechs neuen Mutationszeilen (Bindung entfernt, Muster `*`, Standardziel, Lizenz-URL, Versionszeile) habe ich nicht wiederholt; sie stützen sich auf die Angabe des Protokolls und auf die gelesenen Fälle. Eine ist unabhängig bestätigt, ein Widerspruch zeigte sich nicht | Review-Finding G-3 |
| V-3 | INFO | `make gates` und `make stempel-eingaenge-pruefung` habe ich nicht selbst gefahren (nach `harness/README.md` nicht Verifier-Pflicht); der Beleg ist Report 2 (stempel-eingaenge, docs-check, flaeche grün) | `harness/README.md` §Minimal agent workflow |
| V-4 | INFO | Die `.module`-Datei hat keine Prüfung auf Jackson oder Testserver (Review F-7); die Protokoll-Zeile „Nicht gemessen" nennt es. Der Konsum aus der Registry und Java/Maven-Konsumenten bleiben ungemessen; beides ist Gegenstand von slice-maven-erste-veroeffentlichung | Protokoll §Nicht gemessen |

Kein Befund zu: Jackson oder Testserver im Jar (Manifest-Prüfung grün im frischen Bau, POM ohne beide), Docker-only (keine Host-Ausführung außer `make` und `git`), Lizenz und `groupId` der POM, Token (kein echtes Token verwendet), `:testserver` ohne `maven-publish`.

## Für den Planner

Closure-Voraussetzungen aus dieser Verifikation erfüllt: `make clean-build` und `make e2e` je einmal auf dem End-Stand grün (Plan §5 Kriterium 1), `make publish-vorschau` läuft und gibt die POM aus (Kriterium 2). Offen für die Closure bleiben die Haken der DoD, §7, Register und die Ausgänge der Risiken in §6, dazu die Entscheidung zu V-1.
