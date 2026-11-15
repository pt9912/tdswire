# Verifikationsbericht: slice-cloudsmith-veroeffentlichung-0-1-1, Stand 673bdb26 — 2026-11-15

**Rolle:** Verifier (Frage: bauen wir es richtig, gegen DoD, Plan und [ADR-0049](../plan/adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md), [ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md), [ADR-0048](../plan/adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md)). Kein Review, keine Reparatur.

**Gegenstand:** Zweig `worktree-agent-ae8ee070bc730ad4b`, HEAD `673bdb26`; Basis `main` `9c7439f5` (Release-Commit, Tag `v0.1.1` zeigt darauf: `git ls-remote` und lokale Auflösung gleich). Slice: `docs/plan/planning/in-progress/slice-cloudsmith-veroeffentlichung-0-1-1.md`. Eingang: Review `docs/reviews/2026-11-15-f456e8a2-slice-cloudsmith-veroeffentlichung-0-1-1-review.md` und Nachbesserung `673bdb26`.

**Änderungsart:** Doku, Messprotokoll, ein Kommentar im Workflow, `build.gradle.kts` (nur `version`). Kein Pfad unter `src/` oder `testserver/` (`git diff --stat 9c7439f5 673bdb26 -- src testserver` leer), daher kein `make clean-build`, kein `make e2e`.

Keine Secret-Werte gelesen oder ausgegeben (nur Namen und Zeiten aus `gh secret list`, Anzahlen aus den Protokollen). Nichts veröffentlicht, nichts gepusht, keine künstliche Last. Die Protokolle der Läufe liegen im Scratchpad der Verifikation.

## Ergebnis in Kürze

- DoD-Punkt 1 und 3 tragen. DoD-Punkt 2 trägt **nur zur Hälfte**: (a) anonym über Cloudsmith ist gemessen, (b) Konsum mit Token über GitHub Packages ist **nicht** gemessen. Punkt 2 darf nicht abgehakt werden; Closure-Trigger (1) nennt den Token-Konsum ausdrücklich und ist damit **nicht erfüllt**.
- Alle sieben Behebungen aus dem Review sind in `673bdb26` wirksam (Tabelle unten).
- Der Workflow-Diff berührt keine Logik (zwei Kommentarzeilen, gemessen per Diff); ein Re-Review ist dafür nicht nötig.
- **Neuer Befund V1 (MEDIUM):** Der Lauf 43734101990 hat inzwischen **sechs** Versuche, nicht fünf; das Messprotokoll und die Anfrage zählen fünf.

## 1. Eigene Messungen (Belege, nicht Behauptungen)

### Lauf 43734101990 (`gh api repos/pt9912/tdswire/actions/runs/43734101990[/attempts/<n>[/jobs]]`)

Ereignis `push`, `head_branch` `v0.1.1`, `head_sha` `9c7439f541ae90abe601945dab7a46b879874b6d`, `triggering_actor` jedes Versuchs `pt9912`. Zeiten UTC.

| Versuch | Start | `publish-cloudsmith` | Ausgang Versuch |
|---|---|---|---|
| 1 | 16:11:51 | 16:21:34 bis 16:23:26 failure | failure |
| 2 | 16:45:33 | 16:45:37 bis 16:47:44 failure | failure |
| 3 | 16:51:04 | 16:51:07 bis 16:52:50 failure | failure |
| 4 | 17:01:33 | 17:01:37 bis 17:03:26 failure | failure |
| 5 | 17:16:05 | 17:16:09 bis 17:17:58 success | success |
| **6** | **17:21:45** | Job unverändert (Zeiten und Ausgang des Versuchs 5), `vorschau` skipped 17:21:48 | success |

`gates` (16:11:53 bis 16:21:33, success) und `publish` (16:21:35 bis 16:24:20, success) tragen in allen sechs Versuchen dieselben Zeiten; `run_attempt` des Laufs ist 6, Versuch 7 gibt 404. Die Tabelle des Messprotokolls für die Versuche 1 bis 5 stimmt mit meiner Messung auf die Sekunde überein.

Protokolle der Jobs (je Versuch, nur Zählungen): 401 am PUT in den Versuchen 1 bis 4 je genau eine Zeile, im fünften keine; `BUILD FAILED in 7s, 9s, 6s, 6s`, im fünften `BUILD SUCCESSFUL in 15s`. Die Protokolle des Jobs `publish` sind in allen Versuchen byte-gleich groß (37972 Bytes): `publish` lief nicht neu.

### GitHub Packages (Pakete-API, nur lesend)

Paket `io.github.pt9912.tdswire`, `visibility` `public`, zwei Versionen: `0.1.1` angelegt 16:23:51Z, aktualisiert 16:23:52Z; `0.1.0` 09:39:40Z und 09:39:41Z. **Eine** Anlage von `0.1.1`, auch nach Versuch 6. Anonymer Abruf der POM bei `maven.pkg.github.com`: **401**. Die Dateiliste der Versionen zeigt die API nicht (nicht erneut versucht, im Protokoll so gesagt).

### Cloudsmith anonym (`curl` ohne Zugangsdaten, `dl.cloudsmith.io/public/pt9912/tdswire/maven/io/github/pt9912/tdswire/…`)

| Pfad | HTTP | Bytes |
|---|---|---|
| `maven-metadata.xml` | 200 | 330 |
| `0.1.1/tdswire-0.1.1.pom` | 200 | 1643 |
| `0.1.1/tdswire-0.1.1.jar` | 200 | 1784788 |
| `0.1.1/tdswire-0.1.1.module` | 200 | 2858 |
| `0.1.0/tdswire-0.1.0.pom` und `.jar` | 404 | 324 |

`maven-metadata.xml`: `latest` und `release` `0.1.1`, `versions` nur `0.1.1`, `lastUpdated` 17:19:19. API `api.cloudsmith.io/v1/packages/pt9912/tdswire/` anonym: ein Paket, `size` 1789289 (= 1784788 + 1643 + 2858), `uploaded_at` 17:17:46.68, `sync_finished_at` 17:19:19.09 (92,4 s), Status `Completed`. Alles gleich dem Protokoll.

### Probe `tools/publish-probe.sh url https://dl.cloudsmith.io/public/pt9912/tdswire/maven/ 0.1.1 anonym` (selbst, einmal, anonym, Docker, Host)

Beide Läufe Exit 0: „mit Gradle-Metadaten (.module): Exit 0, PROBE-OK“ und „nur POM, ohne Umleitung auf die .module: Exit 0, PROBE-OK“. Gleich dem Protokoll. (Hinweis: das Skript hat kein Ausführungsrecht im Worktree; Aufruf über `bash`.)

### Maskierung (nur Anzahlen; zehn Protokolle: `publish-cloudsmith` und `publish`, Versuche 1 bis 5)

- `publish-cloudsmith`: je Protokoll genau 1 Zeile `CLOUDSMITH_USERNAME: ***`, 1 Zeile `CLOUDSMITH_API_KEY: ***`; Zeilen dieser beiden Namen mit anderem Wert als `***`: 0.
- `publish`: je Protokoll 1 Zeile `GITHUB_TOKEN: ***`.
- Treffer für `ghs_`, `ghp_`, `github_pat`, `Bearer`, `Basic <8+ Zeichen>`, `X-Api-Key`, `api_key=<Wert>`, `token|key|secret|password` mit 32+ Base64-Zeichen: je Protokoll 0.
- Wort „authorization“: je Protokoll 1 (Checkout-Schritt, maskiert).
- Verschiedene 40-Hex-Folgen je Protokoll: 3.

Bestätigt für diese Muster. Die Grenze der Suche (nur die genannten Muster; Schlüsselformat unbekannt; Gesamtprotokoll des Laufs nicht durchsucht) bleibt, wie das Messprotokoll sie nennt. Mein Erzeuger ist ein Wegwerf-Skript im Scratchpad, nicht im Repo (dieselbe Einschränkung wie im Protokoll).

### Sensoren auf `673bdb26`

`make docs-check` Exit 0 (1486 Dateien, 0 Befunde, vor dem Schreiben dieses Berichts), `make flaeche` Exit 0, `make kopplung-zahlen` Exit 0, `make gates` Exit 0 (9 s; Klassen mit Stempel übersprungen, weil `src/` und `testserver/` unverändert sind: der Beleg ist der Stempel, kein Frischlauf). `make docs-check` nach Anlage dieses Berichts: siehe Abschnitt Sensoren am Ende.

## 2. Plan gegen Diff

Der Diff `9c7439f5..673bdb26` berührt neun Dateien: `.github/workflows/veroeffentlichung.yml` (zwei Kommentarzeilen), `build.gradle.kts` (Version), `docs/maintainer/releasing.md`, `docs/messungen/veroeffentlichung-0-1-1-2026-11-15.md` (neu), `docs/user/README.md`, `docs/user/einbettung-und-betrieb.md`, `harness/README.md`, der Slice (eine Zeile, Plan-Tabelle ergänzt um `harness/README.md`), der Review-Report. Alle stehen in der Plan-Tabelle (§3) oder sind der Nachbesserung des Reviews geschuldet. `README.md` steht im Release-Commit (Zeile 26: Cloudsmith mit Link, „Kostenloses Hosting für Open Source“); `tools/publish-probe.sh` unverändert.

## 3. Behebungen des Reviews (673bdb26)

| # | Befund | Behoben? | Beleg |
|---|---|---|---|
| 1 MEDIUM | `harness/README.md` „nie gelaufen“ | ja | beide Zeilen der Sensors-Tabelle nennen Lauf 43734101990; „Aktueller Lauf-Status“ nennt Tag-Läufe 43699125460 und 43734101990; `grep "nie gelaufen"` über `harness/`, `docs/maintainer`, `docs/user`, `.github`, `tools`: kein Treffer |
| 2 MEDIUM | Workflow-Kommentar „erst die erste echte Veröffentlichung dorthin“ | ja | Zeilen 14/15: „das Schreiben nach Cloudsmith der Tag-Lauf 43734101990 (Version 0.1.1)“; wahr (Versuch 5 schrieb) |
| 3 MEDIUM | Snippet gegen gemessene Konfiguration | ja | Satz in `einbettung-und-betrieb.md` nennt jetzt ausdrücklich `metadataSources` und `isAllowInsecureProtocol` der Probe und dass das Snippet mit Gradle-Voreinstellungen „nicht eigens gefahren“ ist |
| 4 MEDIUM | Erzeuger nicht im Repo | ja, als Kennzeichnung | Protokoll führt „Erzeuger nicht im Repo“ mit Art des Skripts; die Zählung ist von mir unabhängig wiederholt und gleich (siehe oben). Die Aussage „Gesamtprotokoll des Laufs nicht nachgelesen“ steht darin |
| 5 MEDIUM | „Stunden später“ | ja | Protokoll: „rund sechs Minuten nach `sync_finished_at` (… 17:25 bis 17:26 UTC), anonymer 200 erst ab etwa 17:25 gemessen“ |
| 6 LOW | `releasing.md` Doppelstand | ja | Schritt 6 und „Nicht Teil dieses Ablaufs“ verweisen auf „Offene Entscheidungen“; Zeile 73 zitiert die Zurückstellung des Nutzers vom 2026-11-14 als Zitat und führt „Neubewertung fällig, nicht entschieden“. Kein Doppelstand mehr |
| 7 LOW | Stil der Nutzer-Doku | nicht verändert, vom Review als Grenze ohne Handlungsbedarf geführt | Ich folge dem Review |

Keine Behebung hat neue Aussagen breiter als ihren Beleg eingeführt, mit einer Ausnahme (V1 unten: die Versuchszahl).

## 4. Zusatzbefunde des Verifiers

| # | Schwere | Befund | Träger |
|---|---|---|---|
| V1 | MEDIUM | Das Messprotokoll sagt viermal „fünf Versuche“ (Kopf des Abschnitts „Lauf und Versuche“, Erzeuger-Zeile `n` 1 bis 5, Maskierung) und führt keinen sechsten. Der Lauf hat `run_attempt` 6: Versuch 6 startete 17:21:45 (nach Versuch 5, vor Beginn der Messung 17:25), alle Jobs tragen unverändert die Zeiten aus den Versuchen 1 und 5, `publish` und `publish-cloudsmith` liefen nicht neu, GitHub Packages führt weiter eine Anlage, Cloudsmith ein Paket. Was Versuch 6 war (Wiederholung ohne roten Job), ist nicht gemessen. Es berührt auch die Zeile „Ob ‚Re-run all jobs‘ `publish` ein zweites Mal ausgeführt hätte (nicht versucht)“: Versuch 6 hat jedenfalls kein zweites Schreiben ausgelöst. | Doku-Nachzug im Slice, ohne Re-Review |
| V2 | LOW | `docs/messungen/…` Satz „vom Reviewer wiederholt“: der Verifier hat dieselben Werte nun auch unabhängig wiederholt (Abschnitt 1). Kein Mangel; Information für die Closure. | — |
| V3 | LOW | Der Zustand „danach steht `build.gradle.kts` wieder auf `-SNAPSHOT`“ gilt nur auf dem Zweig; `main` steht auf `9c7439f5` mit Version `0.1.1`. Der Haken von Punkt 1 setzt den Merge voraus oder muss ihn nennen. | Merge durch den Nutzer |

## 5. Bewertung je DoD-Zeile

| DoD-Zeile | Befund | Haken? |
|---|---|---|
| **Punkt 1: Veröffentlicht auf beiden Zielen** | `0.1.1` liegt auf GitHub Packages und Cloudsmith (gemessen); Release-Commit `9c7439f5` trägt Tag `v0.1.1` und liegt in `main`; beide Veröffentlichungs-Jobs `success` (Versuch 5 und 6; `publish` seit Versuch 1); `build.gradle.kts` auf `0.1.1-SNAPSHOT` im Folge-Commit `f456e8a2`. Das Messprotokoll hat Datum, Lauf, Ausgang und Dauer je Job, kein Token, nennt Schreibrecht, Maskierung, Namen der Aufgabe, Teilfehler, und führt Form des Benutzernamens ausdrücklich als nicht festgehalten, sowie das Ungemessene. Das ist die Pflicht „nennt, was belegt wird und was ungemessen bleibt“, auch wenn „Form des Benutzernamens“ nicht belegt, sondern benannt-offen ist. | **Ja**, mit zwei Vermerken: V1 im Protokoll nachziehen (Versuch 6), und V3 (Folge-Commit erst mit Merge in `main` wirksam). Form des Benutzernamens als „weiter offen“ in die Risiken. |
| **Punkt 2 (a): anonym über Cloudsmith** | POM und Metadaten 200 ohne Zugangsdaten (selbst gemessen), Probe mit und ohne `.module` Exit 0 (selbst gemessen), `0.1.0` 404, Repository liefert anonym aus (der 200 ist der Beleg). Wartezeit: 92 s bis zur abgeschlossenen Verarbeitung nach der API im Protokoll; der erste anonyme 200 ist ungemessen. | **Teil (a) ja**, mit dem Vermerk „erster anonymer 200 ungemessen“. |
| **Punkt 2 (b): Konsum mit Token über GitHub Packages** | **Nicht gemessen** (kein Token bereitgestellt); Dateien von `0.1.1` und `0.1.0` in GitHub Packages nicht gesehen. Gemessen ist nur 401 ohne Token. Die DoD hat den Konsum mit Token als Liefer-Inhalt und der Closure-Trigger (1) nennt ihn. Die DoD trägt dafür nicht. | **Nein.** Punkt 2 als Ganzes bleibt offen. |
| **Punkt 3: Doku, README, Release-Anleitung** | `einbettung-und-betrieb.md` führt Weg A (Cloudsmith) vor GitHub Packages; GitHub-Konsum nicht als gemessen behauptet; Snippet-Vorbehalt eingearbeitet (Befund 3); `README.md` Zeile 26 nennt Cloudsmith mit Link; `releasing.md` führt Tag und Vermerk als gelaufen, Schritt 9 ohne den Dummy-Vorbehalt (nennt Dummy, GitHub, Cloudsmith), Teilfehler-Weg und Neubewertung. Review hat alle Dateien unter `docs/user/` gelesen (AGENTS.md §5 Regel 5). | **Ja.** |
| `make gates`, `docs-check`, `flaeche`, `kopplung-zahlen` | alle Exit 0 (siehe oben) | **Ja** |
| Review durchgeführt, Report liegt vor | Report liegt vor, fünf MEDIUM und zwei LOW behoben | **Ja** |
| Doku-Update öffentlicher Vertrag | Punkt 3 | **Ja** |
| Closure-Notiz mit Lerneintrag, Register, Risiko-Ausgänge, Paarungen | §7 steht noch „Wird bei der Closure gefüllt“; Risiken stehen alle auf „offen bis zur Closure“ | **Nein**, Sache der Closure nach diesem Bericht |

## 6. Was ungemessen bleibt, und trägt die DoD dafür?

| Ungemessen | Trägt die DoD? | Vorschlag |
|---|---|---|
| **Konsum aus GitHub Packages mit Token** (Probe `url … zugang`, Dateiliste von `0.1.1` und `0.1.0`) | **Nein.** DoD Punkt 2 (b) und Closure-Trigger (1) verlangen ihn. | Träger: der Nutzer führt den Aufruf aus dem Messprotokoll aus (`REG_USER`, `REG_TOKEN` nur in der Umgebung), ein Agent trägt das Ergebnis ins Protokoll ein. Wird das nicht in diesem Slice getan, **entscheidet der Nutzer** über eine Änderung von DoD und Closure-Trigger und über einen **Folge-Slice** (Vorschlag: `slice-github-packages-konsum-messen`, Gegenstand: Probe `url … zugang` gegen `0.1.0` und `0.1.1`, Dateiliste, ein Beleg im Protokoll); dann ist das Risiko „weiter offen“ mit diesem Slice als Träger. Das ist eine Planänderung und nicht Sache des Verifiers. |
| **Form des Benutzernamens** (Service-Name oder Konto) | Teilweise: die DoD verlangt „nennt, was belegt … und was ungemessen bleibt“; das Protokoll nennt es ehrlich als offen. Die Ursache steht nur als Angabe des Nutzers. | Haken Punkt 1 setzbar. Als „weiter offen“ in die Risiken (Risiko „Schlüssel ungültig … oder Benutzername in falscher Form“), Träger: Nutzer notiert die Form (ohne Wert) in der Maintainer-Doku, oder ein Folge-Eintrag in `releasing.md`; kein Sensor kann sie prüfen. |
| **Erster anonymer 200 im Sammelfenster** | Nein, nicht gefordert in dieser Schärfe; DoD (a) verlangt die Wartezeit im Protokoll, geliefert ist die Verarbeitung (92 s) | Haken (a) setzbar. Risiko „Auslieferung nach dem Upload“ als „eingetreten bzw. der Größe nach gesehen, erster 200 weiter offen“; Träger: nächste Veröffentlichung (der Release-Ablauf in `releasing.md`: anonymer Abruf in Intervallen nach Job-Ende). |
| **Tag-Schutz `v*` ohne Bypass** (Gegenprobe des Nutzers) | Nein; der Slice führt ihn als Start-Bedingung (§4 Punkt 5) und Risiko, nicht als Liefer-Punkt; kein Gate prüft ihn. | Nicht abhaken. Risiko „weiter offen“; Träger: der Nutzer (Gegenprobe), Beleg in `releasing.md` §Tag-Schutz, sobald er sie gefahren hat. Der Lauf selbst zeigt nur, dass der Nutzer den Tag pushen konnte. |
| Schlüssel schreibt **nur** auf `pt9912/tdswire` | Nein (Start-Bedingung (4)) | weiter offen; Träger: Nutzer (Cloudsmith-Einstellung). |
| Ob Gradle die `.module` bei Cloudsmith im Lauf abrief; Java und Maven | Nein | Risiko „`.module` bei Cloudsmith“: Auflösung belegt (beide Läufe Exit 0); Abruf der `.module` weiter offen, Java/Maven außerhalb des Slice (§1). |
| Löschwirkung bei Cloudsmith, Re-run all jobs, Secret-Bindung | Nein | weiter offen; Träger: Eintritt eines Teilfehlers; Nutzerentscheidung. |
| Kontingent, Bedingungen und Dauer des Open-Source-Hostings | Nein | weiter offen; kein Sensor, Träger: Nutzer. |

## 7. Re-Review für die Workflow-Kommentaränderung?

**Nein, nicht erforderlich.** Gemessen per Diff (`git diff 9c7439f5 673bdb26 -- .github`, ebenso `…HEAD`): eine Datei, zwei Kommentarzeilen (14 und 15), beide beginnen mit `#`; keine Zeile von `on:`, `jobs:`, `permissions:`, `env:`, `run:`, `uses:` oder `if:` berührt. Der Kommentar ist wahr (Lauf 43734101990 belegt das Schreiben nach Cloudsmith) und trägt Zusage und Rang-Zeiger, keinen Konjunktiv (AGENTS.md §3.7). Slice §3 Ablauf (7) bestimmt außerdem: betrifft die Behebung nur Doku, folgt kein zweiter Review; berührt sie ein Skript, folgt er. Ein Workflow-Kommentar ist kein Skript. Voraussetzung: `make workflow-lint` ist kein Pflicht-Sensor der Änderungsart und wurde nicht gefahren; ein Kommentar kann ihn nicht ändern. Anders wäre zu urteilen, wenn die Behebung von V1 oder der Folgeschritt `.github/`-Logik berührte.

## 8. Vorschlag für den Planner

1. **Haken setzen:** Punkt 1 (mit Vermerken V1, V3), Punkt 2 nur im Teil (a) (als Teilsatz, die Zeile bleibt offen), Punkt 3, Nachweis-Pflichten `gates`/`docs-check`/`flaeche`/`kopplung-zahlen`, Review, Doku-Update.
2. **Nicht abhaken:** Punkt 2 als Ganzes (Teil b), Closure-Notiz, Register, Risiko-Ausgänge und Paarungen (noch nicht geschrieben).
3. **Vor der Closure im Slice nachziehen (nur Doku):** V1 (sechster Versuch ins Messprotokoll, Zählung der Versuche im Text anpassen).
4. **In die Risiken als „weiter offen“ mit Träger:** Token-Konsum GitHub Packages (Träger: Nutzer-Aufruf oder Folge-Slice, Entscheidung des Nutzers über den Closure-Trigger), Form des Benutzernamens (Nutzer), Tag-Schutz-Gegenprobe (Nutzer, `releasing.md` §Tag-Schutz), erster anonymer 200 (nächste Veröffentlichung), Schlüsselrecht nur auf dieses Repository (Nutzer), Löschwirkung, Re-run all jobs, Kontingent und Bedingungen von Cloudsmith.
5. **In die Risiken als eingetreten:** Teilfehler zwischen den Zielen (eingetreten, vier rote Versuche, Wiederholung des roten Jobs allein stellte das Ziel her); Auslieferung nach dem Upload (Größe 92 s gesehen).
6. **Ausgang „entfallen/belegt“:** Maskierung (belegt für die genannten Muster, zehn Protokolle), `.module` bei Cloudsmith (angenommen und ausgeliefert, Auflösung mit und ohne), Repository öffentlich (anonymer 200).

## Sensoren

`make docs-check`, `make flaeche`, `make kopplung-zahlen`, `make gates` auf `673bdb26` (Exit 0 jeweils); `make docs-check` nach Anlage dieses Berichts: Exit 0 (Ergebnis in der Übergabe). Nicht gefahren: `make clean-build`, `make e2e` (kein `src/`, `testserver/`), `make spec-abdeckung`, `make matrix-zitate`, `make workflow-lint` (nicht Pflicht für die Änderungsart).
