# Verifikationsbericht: slice-release-lizenzen-lib-belegen, Stand 456df041 — 2026-11-16

**Rolle:** Verifier (Frage: bauen wir es richtig, gegen DoD, Plan und Entscheidungen). Kein Review, keine Reparatur; die DoD-Haken im Plan setzt der Planner bei der Closure und sind hier offen geblieben.

**Gegenstand:** Zweig `slice-release-lizenzen-lib-belegen`, HEAD `456df041`, Basis `main` `74eb8ffe`. Plan: [slice-release-lizenzen-lib-belegen](../plan/planning/in-progress/slice-release-lizenzen-lib-belegen.md) (§1 bis §8). Maßstäbe: [ADR-0050](../plan/adr/0050-github-release-im-tag-lauf.md) Entscheidung 3 und Mess-Pflicht 8, [TDS-RB-05](../../spec/lastenheft.md#tds-rb-05--lizenz), [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [ADR-0018](../plan/adr/0018-laufzeit-artefakt.md) (Abschnitt „Offen“), `AGENTS.md` 3.8. Eingang: Beleg `docs/messungen/lizenzen-lib-2026-11-16.md`, Reviews [Erst-Review](2026-11-16-7559de60-slice-release-lizenzen-lib-belegen-review.md) und [Re-Review](2026-11-16-be26c4af-slice-release-lizenzen-lib-belegen-re-review.md).

**Änderungsart:** Doku und Messprotokoll plus Datendateien (`lizenzen/lib/`, Text); `src/`, `testserver/`, Werkzeug unberührt (`git diff 74eb8ffe..HEAD --stat`: 14 Dateien, nur `docs/` und `lizenzen/`). Pflicht laut `harness/README.md` §Minimal agent workflow, Zeile „Doku oder Spec“: `make gates`, `make docs-check`, `make flaeche`, `make kopplung-zahlen`. `make clean-build` und `make e2e` sind für diese Änderungsart nicht vorgesehen; ich urteile nicht anders (kein Byte Quelltext, Dockerfile oder Skript im Diff) und habe sie nicht gefahren. `make spec-abdeckung` entfällt.

## Urteil in Kürze

Der Beleg trägt. Die Artefakt-Seite, die der Reviewer nicht fahren durfte, ist nachgemessen und stimmt in allen Zahlen. Kein HIGH, kein MEDIUM; zwei LOW, drei INFO. Die DoD ist erfüllt, soweit sie vor der Closure erfüllbar ist; offen sind nur die Closure-Pflichten (Notiz, Risiko-Ausgänge, Paarungen), wie vorgesehen.

## 1. DoD, Zeile für Zeile (§2 des Plans)

| # | DoD-Zeile | Urteil | Beleg |
|---|---|---|---|
| 1 | Beleg je Bibliothek in `lib/`, am gebauten Artefakt gemessen | **erfüllt** | Siehe Abschnitt 3: `docker build --target build-jar` (Cache), `build/libs` per `docker cp`; Manifest-`Class-Path` und die drei SHA-256 stimmen mit der Tabelle im Beleg überein, ebenso mit repo1.maven.org. Lockfile-Abgleich im Beleg vorhanden (drei Koordinaten, keine Abweichung). Je Eintrag Lizenz mit POM-Fundstelle, Quelle der Lizenzdatei mit Abrufdatum (2026-11-16), Weitergabe-Anforderungen wörtlich aus Abschnitt 4 der Lizenz, „Nicht gelesen“ je Bibliothek. Beleg beginnt mit „Lesung, keine Rechtsauskunft“, nennt die Rechtsfrage aus [ADR-0018](../plan/adr/0018-laufzeit-artefakt.md) als bestehend, führt „Offene Fragen an den Nutzer“ (1, 2a bis 2d, 3) und sagt je Bibliothek, ob das ZIP die Anforderungen erfüllen kann; eine nicht erfüllbare Anforderung wird nicht gefunden und als Lesung ausgewiesen (Befund 3). Ablage als Messprotokoll mit Datum, Host, Bedingung (`AGENTS.md` §5 Regel 7). |
| 2 | Lizenzdateien im Repo abgelegt, byte-gleich, SHA-256 im Beleg | **erfüllt** | Sieben Dateien unter `lizenzen/lib/` (ein Verzeichnis je Bibliothek, Name `<Gruppe>-<Artefakt>-<Version>`); `sha256sum` der Repo-Dateien gleich der Beleg-Tabelle und gleich dem frischen Abruf der sieben Quellen (Abschnitt 4). Ablageort steht im Beleg und in §7 des Plans; Fortschreibungssatz bei `lib/`-Wechsel steht im Beleg („neue Version → neues Verzeichnis …“). |
| 3 | `make gates` grün; Doku-Teile (`docs-check`, `flaeche`, `kopplung-zahlen`) | **erfüllt** | Abschnitt 5: alle vier Exit 0. |
| 4 | Review durchgeführt, Report liegt vor | **erfüllt** | Zwei Reports (Erst-Review, Re-Review) unter `docs/reviews/`; Erst-Review ein MEDIUM, im Re-Review als behoben bestätigt, Re-Review N-1 bis N-4 im HEAD `456df041` nachgezogen (Abschnitt 2). Die Nachbesserung ist reine Doku, ein zweiter Review in frischem Kontext nach Skill-Regel nicht nötig. |
| 5 | Doku-Update (Register-Stand; `releasing.md`, `harness/README.md` unverändert) | **erfüllt** | `state.md` und `evidence/slice-release-lizenzen-lib-belegen.md` im Register; `releasing.md` und `harness/README.md` nicht im Diff, wie der Plan verlangt. |
| 6 | Closure-Notiz mit Steering-Loop-Lerneintrag | **offen (vor Closure erwartbar)** | §7 trägt nur den Ablageort und „Der Rest wird bei der Closure gefüllt“. |
| 7 | Beobachtungs-Register fortgeschrieben, kein Zähler | **erfüllt** | Evidenz-Datei und Stand gesetzt; im Diff kein Zähler. (Die Notiz „keine Beobachtung / Zähler“ in §7 steht bei der Closure aus.) |
| 8 | Jedes Risiko aus §6 mit Ausgang | **offen (vor Closure erwartbar)** | Fünf Risiken, alle „offen bis zur Closure“. Material: Rechtsfrage bleibt offen (BEO), keine Lizenzdatei im Jar (eingetreten, im Beleg Befund 1), Lockfile-Abweichung entfallen, Altern des Belegs weiter offen (Sensor siehe F-2), Abruf nie gescheitert (entfallen). Ausgänge setzt der Planner. |
| 9 | Drei Paarungen (Anker, Folge-Slice, Register) | **offen (an die Closure gebunden)** | Register-Seite vorbereitet; Anker und Folge-Slice-Paarung bei der Closure. |

Liefer-Punkte (2 von 2): beide erfüllt.

## 2. Entscheidungs-Konformität

| Maßstab | Ergebnis |
|---|---|
| [ADR-0050](../plan/adr/0050-github-release-im-tag-lauf.md) Entscheidung 3 (je Bibliothek Lizenz, Quelle der Lizenzdatei samt Abrufdatum, Anforderungen an die Weitergabe) | Konform: drei Bibliotheken, je alle drei Angaben. Die Bedingung „ohne Beleg kein `lib/`“ ist mit diesem Beleg für die drei Bibliotheken erfüllt; der Anhang selbst liegt beim Bau-Slice. |
| [ADR-0050](../plan/adr/0050-github-release-im-tag-lauf.md) Mess-Pflicht 8 | Zum Teil Gegenstand dieses Slice: der Beleg liegt. Der zweite Teil („das ZIP führt die Lizenzdateien, der Lauf bricht bei fehlender Datei, eine Mutation“) ist ausdrücklich Sache von `slice-release-dateien-bauen` und hier nicht behauptet. |
| `AGENTS.md` 3.8 | Stichprobe der tragenden Aussagen gegen die Quelle: Jar-Hashes (Maven Central), sieben Lizenz- und Hinweisdateien (Abruf), POM-Lizenzangabe von kotlin-stdlib (`Apache-2.0`), Gleichheit der stdlib-LICENSE mit dem Text von apache.org (gleicher SHA-256 `cfc7749b…`), Tag-Commits (`git ls-remote`: kotlin v2.4.10 `5687445…`, java-annotations 23.0.0 Tag-Objekt `9a67a73e…`, Commit `5d06c0c4…`), keine Lizenz-/NOTICE-Datei im Namen der drei Jars (`unzip -l` am gebauten Artefakt). Alle gleich der Aussage. Grenzen sind benannt (Zuordnung der Kotlin-NOTICE „nicht Beleg“, Klasse-zu-Quelle „nicht gelesen“, Tags verschiebbar). Nicht von mir wiederholt: Quell-Jar-Suchläufe nach „Copyright“, `diff` gegen apache.org der Coroutines- und annotations-Datei, die GitHub-API-Wurzellisten (der Re-Review hat sie gelesen). |
| „keine Rechtsauskunft“ | Gehalten: Kopfsatz, Befund 3, „Nicht belegt“. Der Beleg schließt nirgends „genügt rechtlich“; Wertungen stehen in den Fragen. |
| Offene Fragen 1, 2a bis 2d, 3 | Nicht still entschieden. Die Ablage der Dritt-Lizenzen und beider NOTICE ist als Vorschlag gekennzeichnet („wie abgelegt vorgeschlagen“, Frage 2b), nicht als Entscheidung; Frage 2c (Form im Archiv) ungeklärt, der Bau-Slice wählt den Inhalt. Die Datei von annotations ist trotz der Jahres-Abweichung (Frage 3) unverändert abgelegt. |
| Hard Rules | 3.1 (Docker-only: Bau über `docker build`), 3.5 und 3.6 (keine ADR, kein Gate berührt), 3.4 (`spec/` nicht berührt): ohne Befund. Haken im Plan: `grep -c '\[x\]'` im Slice ergibt 0. |

## 3. Nachmessung am Artefakt (Punkt, den der Reviewer nicht fahren durfte)

Ein Lauf `docker build --target build-jar -t <temporär> .` im Worktree (Layer-Cache, 14 Schritte CACHED, 0,9 s), Container angelegt, `/src/build/libs` per `docker cp` in den Scratchpad gezogen; Image und Container danach entfernt (nur meine). Manifest von `tdswire.jar`:

`Class-Path: lib/kotlinx-coroutines-core-jvm-1.10.1.jar lib/kotlin-stdlib-2.4.10.jar lib/annotations-23.0.0.jar`

| Datei | SHA-256 am Artefakt | gleich Beleg-Tabelle | gleich repo1.maven.org (curl) |
|---|---|---|---|
| kotlin-stdlib-2.4.10.jar | `4ec0293b…a6df` | ja | ja |
| kotlinx-coroutines-core-jvm-1.10.1.jar | `069c5988…c00d` | ja | ja |
| annotations-23.0.0.jar | `7b0f1972…cd05` | ja | ja |
| `tdswire.jar` | `b6c8073c…d4ee` | ja (gleiche Bytes wie die Einzelmessung im Beleg) | n. a. |

Unter `build/libs/lib` liegen genau diese drei Dateien. `unzip -l` an allen drei: keine Datei mit „licen“, „notice“ oder „copying“ im Namen, Beleg-Satz „Lizenzdateien in den Jars: keine“ bestätigt. Der Beleg sagt „Gleiche Bytes im Artefakt wie bei Maven Central“ nur über Hashes dieses Laufs; das trägt.

## 4. Byte-Gleichheit und Quellen

`cd lizenzen/lib && find . -type f | sort | xargs sha256sum`: sieben Dateien, jeder Hash gleich der Tabelle „Ablage“ des Belegs. Alle **sieben** Quellen erneut abgerufen (raw.githubusercontent.com, Tags v2.4.10, 1.10.1, 23.0.0; Lesen): jeder Hash gleich dem abgelegten. `https://www.apache.org/licenses/LICENSE-2.0.txt` gleich der stdlib-LICENSE (`cfc7749b…`). POM von kotlin-stdlib 2.4.10: `Apache-2.0`, URL wie im Beleg zitiert.

## 5. Plan-vs-Code-Diff und Sensoren

`git diff 74eb8ffe..HEAD --stat`: 14 Dateien, +923 −3.

| Plan §3 | Diff |
|---|---|
| Messprotokoll `docs/messungen/` neu | `docs/messungen/lizenzen-lib-2026-11-16.md` (99 Zeilen) |
| Ablage der Lizenzdateien neu | `lizenzen/lib/…`: 7 Dateien |
| Register `BEO-TDS/lizenzhinweise-…`: `state.md`, eine Datei in `evidence/` | `state.md`, `evidence/slice-release-lizenzen-lib-belegen.md` |
| Slice §7 update | ja (Ablageort) und §3-Zeile selbst |
| nicht in §3 | `observation.md` des Registers (Annotations ergänzt, F-5 des Erst-Reviews, siehe F-1) und zwei Review-Reports unter `docs/reviews/` (Rollen-Artefakte). Keine Datei außerhalb von `docs/` und `lizenzen/`; weder `Dockerfile` noch `tools/` noch `LICENSE`, `README.md`, `docs/user/` berührt (Abgrenzung §1 gehalten). |

Eigene Läufe auf dem End-Stand (Arbeitsbaum sauber):

| Lauf | Ergebnis |
|---|---|
| `make docs-check` | Exit 0 (direkt gelesen), 1513 Dateien, 0 Befunde |
| `make gates` | Exit 0, 10 s; die drei Gruppenschritte (`werkzeug`, `tests`, `stempel`) per Stempel übersprungen („Beleg für <Hash>“), `immer`-Gates (`docs-check`, `flaeche`, `gate-zuordnung`, `stempel-eingaenge-pruefung`) gelaufen. Der Stempel gilt, weil der Diff nur nicht-eingehende Pfade berührt (`docs/`, `lizenzen/`); für HEAD selbst liegt keine Note (`git notes --ref=gates show HEAD`: keine), der Beleg hängt am Eingangs-Hash. |
| `make flaeche`, `make kopplung-zahlen` | je Exit 0 |
| `make clean-build`, `make e2e` | nicht gefahren, nicht erforderlich (s. o.) |

Gesamtlaufzeit der Verifikation (Wanduhr, mit Lesen): rund 65 s Sensor- und Mess-Zeit nach dem Lesen, gemessen vom ersten Befehl bis zum Ende des Abruf-/Gates-Teils; größte Posten: `make gates` 10 s, Abrufe von Maven Central und GitHub rund 20 s, Docker-Bau 1 s (Cache). Speicher (verfügbar): vor dem Bau 10 305 MB, nach dem Bau 10 512 MB, nach `make gates` 11 906 MB; nie unter 10 GB, keine künstliche Last.

## 6. Findings

| ID | Kategorie | Befund | Pfad |
|---|---|---|---|
| F-1 | LOW | Plan-vs-Code: §3 nennt für das Register nur `state.md` und eine Datei in `evidence/`; im Diff steht zusätzlich `observation.md` (Annotations als dritte Bibliothek). Inhaltlich richtig und im Re-Review (F-5) so verlangt, aber §3 des Plans ist nicht nachgezogen (der Planner setzt es bei der Closure, oder §3 wird um die Zeile ergänzt). | `docs/plan/planning/observations/BEO-TDS/lizenzhinweise-der-bibliotheken-rechtsfrage/observation.md` |
| F-2 | LOW | Sensor-Lücke „zu wenig abgelegte NOTICE“: im Repo gibt es **keinen** Sensor, der meldet, dass ein Verzeichnis unter `lizenzen/lib/` eine Datei weniger führt als nach dem Beleg (kein Treffer von `lizenzen/` in `Makefile`, `repo.mk`, `tools/`, `Dockerfile`, `harness/mk`, `tools/stempel`; `make gates` fasst `lizenzen/` nicht an). Der Beleg sagt das selbst (Befund 3), nennt als Träger `slice-release-dateien-bauen`. Dort ist die Prüfung „Lizenzdatei je Jar in `lib/`“ geplant (§3 Zeile Prüfskript, DoD „Prüfungen am entpackten Archiv“), die Wörter NOTICE und Hinweisdatei, die `lizenzen/lib/`-Eingangsliste mit sieben Dateien und ihre Hashes kommen im Slice-Plan nicht vor. Eine Prüfung „je Jar eine Lizenzdatei“ meldet eine fehlende NOTICE von kotlinx-coroutines nicht (die LICENSE wäre vorhanden). Empfehlung an den Planner: im Plan von `slice-release-dateien-bauen` die Sollmenge (alle sieben Dateien, nicht „eine je Jar“) und eine Mutation „NOTICE entfernt“ ausdrücklich aufnehmen. Das ist kein Mangel dieses Slice. | `docs/plan/planning/open/slice-release-dateien-bauen.md` (§2, §3) |
| F-3 | INFO | Der Beleg nennt im Abschnitt „Jars gleich dem Maven-Central-Jar“ die Artefakt-Seite als vom Verifier zu prüfen; erledigt, Ergebnis Abschnitt 3. Der Satz kann bei der Closure stehen bleiben. | `docs/messungen/lizenzen-lib-2026-11-16.md` |
| F-4 | INFO | `make gates` lief mit übersprungenen Gruppen (Stempel). Das ist bei reiner Doku-Änderung nach [ADR-0024](../plan/adr/0024-lokale-stempel-zwei-klassen.md) zulässig; die `immer`-Gates liefen real. Ein Frischlauf der Klassen `tests` und `werkzeug` brächte keine Aussage über diese Änderung. | `harness/README.md` §Sensors |
| F-5 | INFO | Nicht wiederholt (Grenze der Verifikation): Quell-Jar-Suchläufe nach Copyright-Zeilen (stdlib, Coroutines, annotations), `diff` der Coroutines- und annotations-Lizenz gegen apache.org, GitHub-API-Wurzellisten, Zuordnung der Dritt-Lizenzen im stdlib-Jar. Die Aussagen darauf stehen im Beleg als gemessen; der Re-Review hat die Listen gelesen, ich habe sie nicht unabhängig bestätigt. | Beleg Abschnitte stdlib und Coroutines |

## 7. Negativbefund-Pflicht

| Bereich | Ergebnis |
|---|---|
| Jar-Hashes und `Class-Path` am Artefakt gegen Beleg und Maven Central | geprüft, ohne Befund |
| Sieben Lizenz- und Hinweisdateien (Repo, Beleg, Quelle) | geprüft, ohne Befund |
| Tag-Stand (`git ls-remote`) gegen Beleg | geprüft, ohne Befund |
| Rechtsauskunft, stille Entscheidung der Fragen 1 bis 3 | geprüft, ohne Befund |
| Plan-vs-Code: Dateien außerhalb §3 | F-1 (LOW), sonst ohne Befund |
| `AGENTS.md` 3.8 im Beleg (Stichprobe) | geprüft, ohne Befund |
| Haken, Closure-Dateien, `releasing.md`, `harness/README.md` | unberührt, ohne Befund |
| Sensoren | `make docs-check`, `gates`, `flaeche`, `kopplung-zahlen` grün; Sensor-Lücke F-2 |

## 8. Verdikt

Die zwei Liefer-Punkte der DoD sind erfüllt, die Nachweise Gates und Review liegen, die drei Closure-Pflichten (Notiz, Risiko-Ausgänge, Paarungen) stehen vor der Closure aus. Kein HIGH, kein MEDIUM; der Planner kann F-1 und F-2 bei der Closure aufnehmen. Offene Fragen 1, 2a bis 2d und 3 gehen unverändert an den Nutzer.
