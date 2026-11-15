# Review-Report: slice-cloudsmith-veroeffentlichung-0-1-1, Commits d804eee0 und f456e8a2 — 2026-11-15

**Review-Art:** Code (Doku und Messprotokoll) gegen Plan, ADR und Hard Rules.

**Gegenstand:** Commits `d804eee0` (Messprotokoll 0.1.1, Nutzer-Doku, `releasing.md`) und `f456e8a2` (Version auf `0.1.1-SNAPSHOT`), Zweig `worktree-agent-ae8ee070bc730ad4b`, Basis `main` `9c7439f5`. Berührt: `docs/messungen/veroeffentlichung-0-1-1-2026-11-15.md` (neu), `docs/user/einbettung-und-betrieb.md`, `docs/user/README.md`, `docs/maintainer/releasing.md`, `build.gradle.kts`.

**Skill:** `.harness/skills/reviewer.md` @ Stand `9c7439f5`. **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-15

**Eingangs-Kontext:**

- `slice-cloudsmith-veroeffentlichung-0-1-1` (DoD, §3 Ablauf, §6)
- [ADR-0049](../plan/adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md), [ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md), [ADR-0048](../plan/adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md)
- [TDS-RB-05](../../spec/lastenheft.md#tds-rb-05--lizenz), [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)
- `AGENTS.md` §3.7, §3.8, §5 Regel 5 und Regel 7
- Logs der fünf Versuche und Antworten der APIs im Scratchpad des Implementers (`m11/`), stichprobenweise gelesen; keine Secret-Werte ausgegeben

Nicht geprüft (Verifier-Frage): Erfüllung der DoD-Haken, `make gates`.

---

## Findings

| # | kategorie | quelle | pfad · Anker | befund | verifizierbar | klasse |
|---|---|---|---|---|---|---|
| 1 | MEDIUM | `AGENTS.md` §3.8, Reviewer-Skill „Fortschreibung lässt die zweite Fundstelle stehen“ | `harness/README.md` · `am Dummy-Server belegt, nie gegen Cloudsmith gelaufen` (Zeile `make publish-cloudsmith`) und `(nie gelaufen)` (Zeile `.github/workflows/veroeffentlichung.yml`) | Beide Zeilen des Einstiegs sagen „nie gelaufen“; der Diff hebt dieselbe Aussage in `releasing.md` auf „gelaufen“ (Lauf 43734101990), und `harness/README.md` steht weder im Diff noch in der Plan-Tabelle (Slice §3). Ein Agent, der den Einstieg liest, hält das Cloudsmith-Ziel für ungelaufen. Dieselbe Stelle, Absatz „Aktueller Lauf-Status“ (`kein Tag-Lauf`), war schon seit `0.1.0` veraltet. | ja (Grep `nie gelaufen` über `harness/`) | Fortschreibung-zweite-Fundstelle |
| 2 | MEDIUM | `AGENTS.md` §3.7, §3.8 | `.github/workflows/veroeffentlichung.yml` · `das Schreiben nach Cloudsmith erst die erste echte Veroeffentlichung dorthin` | Kopfkommentar sagt, erst die erste echte Veröffentlichung nach Cloudsmith belege das Schreiben; sie ist erfolgt (Lauf 43734101990, Versuch 5). Der Kommentar ist damit eine Zusage über einen Zustand, der nicht mehr gilt, und steht nicht in der Änderungsliste. Nicht als HIGH („Kommentar trägt keine Klasse“) gewertet, weil er bis zum Lauf wahr war; der Grund ist Nachzug, nicht Fehlformulierung. Nicht geändert (Auftrag). | nein | Fortschreibung-zweite-Fundstelle |
| 3 | MEDIUM | `AGENTS.md` §3.8 | `docs/user/einbettung-und-betrieb.md` · `Gemessen ist: ein Kotlin-Konsument mit diesem Eintrag` | „Mit diesem Eintrag“ meint das Snippet (`maven { url; content { includeGroup } }`, ohne `metadataSources`). Die Probe lief mit `metadataSources { gradleMetadata(); mavenPom(); artifact() }` und `isAllowInsecureProtocol = true`, der zweite Lauf mit `ignoreGradleMetadataRedirection()` (`tools/publish-probe.sh`, Art `url`). Das Snippet mit Gradle-Voreinstellung ist nicht gefahren; das Protokoll nennt ungemessen, ob Gradle die `.module` überhaupt abrief. Die Aussage ist breiter als der Beleg. | nein (Lauf der Probe mit genau dem Snippet fehlt) | Aussage-breiter-als-Beleg |
| 4 | MEDIUM | Reviewer-Skill „Messzahl aus einem Erzeuger, der nicht im Repo liegt“ | `docs/messungen/veroeffentlichung-0-1-1-2026-11-15.md` · `Gesucht in den Protokollen der fünf Versuche` | Die Maskierungs-Anzahlen (je Protokoll eine Zeile, Treffer 0, drei verschiedene 40-Hex-Werte) kommen aus einem Wegwerf-Skript im Scratchpad (`mask.py`); das Protokoll nennt es nicht und sagt nicht, dass es nicht im Repo liegt. Ich habe die Zählungen mit eigenem Skript an `cs1` bis `cs5` und `pub5` wiederholt: sie stimmen. Dasselbe gilt für die `curl`-Tabelle (Skript im Scratchpad); die Werte sind an der öffentlichen Registry wiederholbar, ich habe fünf davon wiederholt. Die Logs selbst liegen nur im Scratchpad und in GitHub (Aufbewahrungsfrist). | ja | Erzeuger-nicht-im-Repo |
| 5 | MEDIUM | `AGENTS.md` §3.8 | `docs/messungen/veroeffentlichung-0-1-1-2026-11-15.md` · `der erste anonyme Abruf dieser Messung liegt Stunden später` | Die Dateien des Scratchpads tragen für `curl.sh`, `cs-api.json` und `probe.start` die Zeit 17:25 bis 17:26 UTC, also rund sechs Minuten nach `sync_finished_at` 17:19:19, nicht Stunden. Die Kernaussage (kein Abruf im Fenster 17:17:58 bis 17:19:19, der erste 200 ungemessen) bleibt tragfähig; die Zeitangabe ist es nicht. Gleichzeitig bleibt der anonyme 200 ein bekannter Messwert erst ab 17:25, nicht ab dem Upload. | ja (Dateizeiten, Lauf der Probe) | Aussage-breiter-als-Beleg |
| 6 | LOW | `AGENTS.md` §3.7 (Zustandsangabe, kein Doppel-Stand) | `docs/maintainer/releasing.md` · `nach der ersten Veröffentlichung neu zu bewerten` (Schritt 6 und Absatz „Nicht Teil dieses Ablaufs“) | Nur der Absatz „Zurückgestellt“ wurde auf „Veröffentlichung ist erfolgt, Neubewertung fällig“ gehoben. Schritt 6 und der Absatz „Nicht Teil dieses Ablaufs“ sagen weiter „nicht jetzt entschieden, nach der ersten Veröffentlichung neu zu bewerten“, im selben Dokument also zwei Stände derselben Aussage. | ja (Grep) | Fortschreibung-zweite-Fundstelle |
| 7 | LOW | `AGENTS.md` §5 Regel 5 | `docs/user/einbettung-und-betrieb.md` · `Cloudsmith ist ein Dienst eines Drittanbieters` und `mehr sagt diese Anleitung dazu nicht` | Die Nutzer-Doku enthält einen Satz über die Anleitung selbst und einen Weg-B-Vorbehalt in Konjunktivform („bleibt die Auslieferung aus, ist Weg B der Ausweg“). Das ist Ist-Zustand, kein Chronik- oder Planungstext; aufgeführt, weil die Wörter der Skill-Liste („nicht gemessen“, „nicht gemessen: Maven und Java“) vorkommen. Sie stehen als Grenze, nicht als Zukunft; der Stil entspricht dem Bestand (`Dass ein fremdes Programm das Jar so einbindet, ist nicht gemessen`). | ja (Grep) | Nutzer-Doku-Abwesendes |
| 8 | INFO | Verifier | `docs/messungen/veroeffentlichung-0-1-1-2026-11-15.md` · `Welche Form des Benutzernamens richtig ist, steht hier nicht` | Der Plan nennt die Form des Benutzernamens als zu belegenden Punkt der DoD; Protokoll und `releasing.md` benennen sie ausdrücklich nicht. Die Aussage ist ehrlich eingeschränkt (Ursache nur nach Angabe des Nutzers); ob die DoD damit erfüllt ist, entscheidet der Verifier. | nein | DoD-Abgleich |
| 9 | INFO | Verifier | Protokoll · `Mit Token über GitHub Packages (ungemessen)` | Der zweite Weg der DoD (Konsum mit Token) ist nicht gemessen, das Protokoll sagt es und gibt den Aufruf für den Nutzer an. Nutzer-Doku und `releasing.md` behaupten ihn nicht als gemessen (geprüft). DoD-Haken und Closure bleiben dem Verifier und dem Planner. | nein | DoD-Abgleich |
| 10 | INFO | Maintainability | Protokoll · `dass es „Re-run failed jobs“ war und nicht „Re-run all jobs“, folgt aus dem Verhalten` | Die Art der Wiederholung ist aus den Job-Zeiten geschlossen (`gates` und `publish` unverändert in den Versuchen 2 bis 5, geprüft) und so gekennzeichnet; `releasing.md` nennt das Verhalten „gemessen“ und die Schaltfläche „Re-run failed jobs“ als Weg. Die Verknüpfung Schaltfläche zu Verhalten ist Schluss, nicht Beobachtung. | nein | Schluss-statt-Beobachtung |
| 11 | INFO | Maintainability | Protokoll · `Gesamtprotokoll des Laufs (erster Versuch und Versuch 2)` | Die Suche im Gesamtprotokoll liegt nicht im Scratchpad vor und war nicht nachzulesen; die sechs Einzelprotokolle (`cs1` bis `cs5`, `pub5`) habe ich nachgelesen. | nein | nicht nachprüfbarer Beleg |
| 12 | INFO | Maintainability | `build.gradle.kts` · `version = "0.1.1-SNAPSHOT"` | Siehe Prüfpunkt 4: konsistent mit [ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md) Entscheidung 3a und dem Vorbild `c6523593` (`0.1.0-SNAPSHOT` nach `0.1.0`). Maven sortiert `0.1.1-SNAPSHOT` vor `0.1.1`; das ist die Folge der Setzung, kein Befund. | ja | — |

---

## Prüfpunkte der Anfrage

### (1) Belege des Messprotokolls

Nachgelesen und bestätigt (Scratchpad `m11/`, nur Zeiten, Zahlen und Namen gelesen):

- Fünf Versuche, Auslöser `pt9912`, Start- und Job-Zeiten der Tabelle: gleich `at1` bis `at5` und `aj1` bis `aj5`. `gates` und `publish` tragen in den Versuchen 2 bis 5 unverändert Start und Ende des ersten (16:11:53 bis 16:21:33, 16:21:35 bis 16:24:20); `vorschau` `skipped`.
- 401 am ersten PUT (Jar) in den Protokollen der Versuche 1 bis 4, je genau eine Zeile; keine im fünften. `BUILD FAILED in 7s, 9 s, 6 s, 6 s`, im fünften `BUILD SUCCESSFUL in 15s`; Zeiten des grünen Versuchs (17:16:12, Image 17:17:37, POM-Task 17:17:45, Upload-Task 17:17:55, Ende 17:17:58) stimmen.
- Der Schritt `merge-base --is-ancestor` steht in `cs1`, `cs5` und `pub5` und ist durchlaufen (`releasing.md` Schritt 8, „der positive Fall ist in beiden Jobs gelaufen“: gedeckt).
- Cloudsmith-API: ein Paket, `tdswire-0.1.1.jar` (primär), `.pom` 1643, `.module` 2858 Bytes, Summe 1789289 = `size`; `uploaded_at` 17:17:46.68, `sync_finished_at` 17:19:19.09: **92 s** (gerechnet 92,4 s) stimmt. Pakete-API von GitHub: `0.1.1` 16:23:51 und 16:23:52, `0.1.0` 09:39:40 und 09:39:41: gleich `ghv.json`; genau eine Anlage von `0.1.1`.
- Probe `url`: zwei Läufe `Exit 0, PROBE-OK`, 17:25:10 bis 17:26:04 (54 s) laut `probe.start`.
- Maskierung: eigene Zählung je Protokoll (`CLOUDSMITH_USERNAME: ***` eins, `CLOUDSMITH_API_KEY: ***` eins, `GITHUB_TOKEN: ***` eins im `publish`-Protokoll, Treffer für `ghs_`, `ghp_`, `github_pat`, `Bearer`, `X-Api-Key`, `api_key=` null, Wort „authorization“ eins als `AUTHORIZATION: basic ***` an der Zeile des Checkout, drei verschiedene 40-Hex-Werte, kein `CLOUDSMITH_…:` mit anderem Wert als `***`): stimmt mit dem Protokoll. Dass die Maskierung bei fehlgeschlagenem Aufruf hält, ist an den roten Versuchen gesehen (die Fehlerzeile nennt nur die URL).
- Wiederholung der anonymen Abrufe heute (17:39 UTC): `maven-metadata.xml` 200 / 330, POM 200 / 1643, `.module` 200 / 2858, Jar 200 / 1784788, `0.1.0`-POM 404; GitHub Packages anonym 401. Gleich der Tabelle.
- Reihenfolge der Secret-Änderungen gegen Versuchszeiten: Versuch 1 und 2 vor beiden, 3 und 4 zwischen, 5 danach (`16:50:46`, `17:15:53` gegen Start 16:51:04, 17:01:33, 17:16:05): schlüssig; die Ursache ist als Angabe des Nutzers gekennzeichnet.

Abweichung: Finding 5. Herkunft der Zahlen: Finding 4.

### (2) Nutzer-Doku Weg A

- URL `https://dl.cloudsmith.io/public/pt9912/tdswire/maven/` und Koordinaten `io.github.pt9912:tdswire:0.1.1` stimmen mit Protokoll und Probe; `content { includeGroup("io.github.pt9912") }` und `mavenCentral()` daneben wie in der Probe.
- GitHub Packages mit Token wird **nicht** als gemessen behauptet: „ohne Token antwortet die Registry mit 401“ (gemessen) und „der Konsum mit einem Token `read:packages` ist nicht gemessen“. Gedeckt.
- Chronik und Planung: kein Treffer auf „bisher“, „noch nicht“, „soll“, „Zielzustand“, „seit Slice“ in `docs/user/` (Grep über alle Dateien); `docs/user/README.md` bekommt eine Zeile, die zum Schritt führt. Die anderen Dateien unter `docs/user/` nennen Registry nur negativ für den Testserver („ohne Registry“, `testserver-manuell.md`), das bleibt wahr. Die Weg-B-Beschreibung ist unverändert und trägt ihr Datum „gefahren am 2026-11-07“.
- Abweichung: Finding 3 (Snippet gegen gemessene Konfiguration), Finding 7 (LOW, Stil).

### (3) `releasing.md` und Kopfkommentare

- „gelaufen“ steht nur, wo gemessen: Tag-Lauf, `publish-cloudsmith` (fünf Versuche), Einzel-Aufgabe `publishMavenPublicationToCloudsmithRepository`, Ancestor-Prüfung positiv. Offen geführt: Konsum mit Token, Dateiliste bei GitHub, erster anonymer 200, Bruch des Jobs außerhalb von `main` (nie gelaufen, bleibt „gelesen“), Löschwirkung.
- Teilfehler-Weg: „erst prüfen, ob das rote Ziel die Version trägt, dann allein den roten Job wiederholen“ ist durch Versuche 2 bis 5 gedeckt (Finding 10 zur Schaltflächen-Zuordnung). Die Wartezeit „rund eineinhalb Minuten, gemessen 92 s bis zum Abschluss der Verarbeitung“ ist die Zeit der API, nicht des ersten 200; der Text sagt das („bis zum Abschluss der Verarbeitung“) und führt den ersten 200 unter „Ungemessen“. Gedeckt.
- Benutzername-Nachtrag: als Angabe des Nutzers gekennzeichnet, Form nicht genannt (Finding 8).
- Veraltete Kopfkommentare (nicht geändert): `.github/workflows/veroeffentlichung.yml` Zeilen 14 und 15 (Finding 2); `tools/publish.sh` und `tools/mk/publish.mk` tragen weder „nur GitHub“ noch „nie gelaufen“ (Grep, ohne Treffer), `tools/publish-probe.sh` Kopf nennt den Dummy-Server als Gegenstand der Arten `ziele` und `registry`, was wahr bleibt. Zusätzlich veraltet, außerhalb der vier genannten Dateien: `harness/README.md` (Finding 1).

### (4) SNAPSHOT-Rückstellung

`0.1.1-SNAPSHOT` statt der nächsten Version: konsistent. [ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md) Entscheidung 3a sagt „der Release-Commit setzt sie ohne `-SNAPSHOT` … danach wieder `-SNAPSHOT`“ und nennt keine Zählregel für den Folgestand; das Vorbild `c6523593` setzte nach `0.1.0` auf `0.1.0-SNAPSHOT` (gelesen im Diff), und `releasing.md` Schritt 5 sagt es ausdrücklich („der Zähler bleibt der der veröffentlichten Version, die nächste Version wählt der Nutzer“). Der Release-Commit in der Anleitung (`9c7439f5`) ist der Tag-Commit des Protokolls (`headSha`). Der Folge-Commit trägt IDs ([ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md), [ADR-0048](../plan/adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md), [TDS-RB-05](../../spec/lastenheft.md#tds-rb-05--lizenz), [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)). Kein Befund.

### Sensoren

`make docs-check` Exit 0 (1485 Dateien, 0 Befunde, auf Stand `f456e8a2`, vor diesem Report), `make flaeche` Exit 0, `make kopplung-zahlen` Exit 0. Mehr nicht gefahren (Änderungsart Doku/Spec; `make gates` ist Sache des Implementers und Verifiers). Keine künstliche Last, keine Veröffentlichung, kein Push; Abrufe nur anonym und lesend.

### Plan-Tabelle gegen Diff

Slice §3 nennt `build.gradle.kts`, Messprotokoll, `docs/user/` (alle Dateien), `README.md` (schon im Release-Commit), `releasing.md`, `tools/publish-probe.sh` unverändert. Der Diff berührt keine Datei darüber hinaus; `docs/user/README.md` fällt unter „übrige Dateien unter `docs/user/`“. Die zweite Fundstelle `harness/README.md` steht nicht in der Tabelle (Finding 1).

---

## Negativbefund (geprüft, ohne Befund)

- `build.gradle.kts`: geprüft, ohne Befund (nur `version`; keine Gate-, detekt- oder Quellmengen-Änderung; kein Host-Aufruf).
- `docs/user/` (alle Dateien, nicht nur die geänderten): geprüft, kein Chronik- oder Planungstext, Weg A mit Beleg bis auf Finding 3; GitHub-Konsum nicht als gemessen behauptet.
- `docs/maintainer/releasing.md`: geprüft, ohne Befund außer Finding 6; „gelaufen“ nur an gemessenen Stellen.
- `docs/messungen/`: geprüft, Zeiten, Größen, Zählungen und Zuordnungen bis auf Finding 4 und 5 am Beleg bestätigt.
- Hard Rules `AGENTS.md` §3.1 (kein Host-Aufruf von `gradle` oder `java` in den geänderten Anleitungen; Weg A ist ein Gradle-Eintrag der Anwendung des Nutzers, Weg B über Docker), §3.2, §3.5 (keine Accepted-ADR geändert), §3.6: ohne Befund.
- Secrets: kein Wert in Protokoll, Doku oder diesem Report; der Wert des Benutzernamens ist nirgends festgehalten.

Konfliktpfad: kein Finding ab HIGH, kein Rollen-Widerspruch bekannt; keine Sequenz über den Architect nötig.
