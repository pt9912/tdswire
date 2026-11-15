# Tag-Lauf des Workflows `veroeffentlichung.yml`: Messprotokoll vom 2026-11-15

Messprotokoll zu [TDS-RB-05](../../spec/lastenheft.md#tds-rb-05--lizenz) und [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) (`AGENTS.md` §5 Regel 7), Gegenstand von [ADR-0048](../plan/adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Entscheidung 4 und der Folgepflicht von [ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md). Es hält fest, was **ein** Lauf bei Tag-Push und die Registry danach gezeigt haben, nicht, ob eine Anforderung belegt ist. **Der Lauf hat eine Version in eine Registry geschrieben; sie ist nicht rücknehmbar. Der Konsum aus der Registry ist nicht gemessen.** Der Lauf ist eine Einzelmessung; ein erneuter Lauf für dieselbe Version ist nicht vorgesehen.

**Erzeuger.** Der Workflow `.github/workflows/veroeffentlichung.yml` und das Eingangsskript `tools/publish.sh` auf dem Release-Commit `b0109953d4e906da8b05d3a09adc78c7c7f52510`. Die Angaben sind das Laufprotokoll (`gh api repos/pt9912/tdswire/actions/runs/43699125460/logs`, Zeitstempel je Zeile), die Felder von `gh run view 43699125460 --json jobs,createdAt,updatedAt,headSha,event,headBranch,conclusion` und zwei lesende Abfragen der Pakete-API (`gh api /users/pt9912/packages/maven/io.github.pt9912.tdswire` und `…/versions`). Die Dauer ist nicht wiederholbar.

## Bedingungen

| Angabe | Wert |
|---|---|
| Lauf | [43699125460](https://github.com/pt9912/tdswire/actions/runs/43699125460), ausgelöst durch den Tag-Push des Nutzers `pt9912` |
| Ereignis, Ref | `push`, `headBranch` `v0.1.0` (Ref-Typ `tag`); `headSha` `b0109953d4e906da8b05d3a09adc78c7c7f52510`, der Release-Commit „Release 0.1.0: Version ohne -SNAPSHOT“ |
| Zeitraum | 2026-11-15, `createdAt` 09:24:43 UTC, `updatedAt` 09:39:58 UTC (rund 15 min 15 s); Gesamtausgang `success` |
| Runner | gehostet, Label `ubuntu-24.04`, Runner-Programm 2.338.2 in beiden Jobs; Runner-Image: Job `gates` Version `20261111.370.1`, Job `publish` Version `20261111.127.1` (zwei verschiedene Image-Versionen im selben Lauf; der Dispatch-Lauf zeigte `20261111.127.1`) |
| Klon | `actions/checkout` auf Commit-SHA `d23441a48e516b6c34aea4fa41551a30e30af803`, `fetch-depth: 0`, `persist-credentials: false`, in beiden Jobs |
| Auswertung der Bedingungen | `publish.if` im Protokoll des Jobs: `Expanded: (true && (('push' == 'push') && ('tag' == 'tag')))`, Ergebnis `true`; `gates.if`: `success()`, Ergebnis `true` |

## Ausgang und Dauer je Job

| Job | Ausgang | Start bis Ende (UTC) | Dauer | Anmerkung |
|---|---|---|---|---|
| `gates` | success | 09:24:45 bis 09:37:58 | rund 13 min 13 s | Schritt `make gates` ab 09:24:49 bis letzte Zeile 09:37:52; 1402 Testzeilen `PASSED` im Protokoll |
| `publish` | success | 09:37:59 bis 09:39:58 | rund 1 min 59 s | Schritte unten |
| `vorschau` | skipped | 09:37:58 bis 09:37:58 | 0 s | keine Schritte |

Schritte von `publish` (Zeitstempel des Protokolls): Set up job ab 09:38:00; `actions/checkout` 09:38:02 bis 09:38:08 (rund 6 s); „Tag-Commit liegt in origin/main“ 09:38:08 (unter 1 s); `make publish` 09:38:08 bis 09:39:56 (rund 1 min 48 s); Complete job. Im Schritt `make publish`: `docker build` der Stage `publish` 09:38:13 bis 09:39:32 (rund 78 s; davon `abhaengigkeitenLaden` 31,0 s, `gradle classes` 33,0 s, `gradle --offline :jar` 6,0 s), danach `gradle publish` im Container 09:39:34 bis 09:39:56 (rund 22 s; Aufgabe `publishMavenPublicationToRegistryRepository` ab 09:39:39 bis 09:39:56). Zeitposten des Laufs ist `make gates` (rund 87 % der Laufzeit der beiden Jobs).

## Was das Protokoll zeigt

- **Rechte des Tokens.** Das Protokoll von `publish` nennt unter „GITHUB_TOKEN Permissions“: `Contents: read`, `Metadata: read`, `Packages: write`. Das Protokoll von `gates` nennt `Contents: read` und `Metadata: read`; `Packages` steht dort nicht.
- **Klon eines Tag-Push.** Der Abruf lief mit `fetch --no-tags --prune origin +refs/heads/*:refs/remotes/origin/* +refs/tags/*:refs/tags/*`; das Protokoll nennt `[new branch] main -> origin/main` und `[new tag] v0.1.0 -> v0.1.0`, `git rev-parse refs/tags/v0.1.0^{commit}` ergibt `b0109953d4e906da8b05d3a09adc78c7c7f52510`. `git checkout --force refs/tags/v0.1.0` meldet `detached HEAD`, `HEAD is now at b0109953 Release 0.1.0: …`. Dass der Tag leichtgewichtig ist (kein Tag-Objekt), folgt aus der Antwort `gh api repos/pt9912/tdswire/git/ref/tags/v0.1.0` (`object.type` `commit`), nicht aus dem Lauf.
- **Prüfung `origin/main`.** Der Schritt „Tag-Commit liegt in origin/main“ (`git merge-base --is-ancestor HEAD origin/main`) endete `success`; das Protokoll zeigt nur den Befehl, keine Ausgabe. Das ist der Fall „Commit liegt in `main`“.
- **Eingangsskript.** `make publish` gab als erste Zeile aus: `publish: Version 0.1.0, Tag v0.1.0 auf HEAD, Baum sauber, Zugangsdaten gesetzt; Ziel https://maven.pkg.github.com/pt9912/tdswire`. Damit haben die Prüfungen des Skripts (Version ohne `-SNAPSHOT`, Tag auf `HEAD`, sauberer Baum, beide Zugangsdaten nicht leer) auf dem Klon eines Tag-Push bestanden.
- **Gradle.** `generateMetadataFileForMavenPublication`, `generatePomFileForMavenPublication`, `publishMavenPublicationToRegistryRepository` und `publish` liefen; `BUILD SUCCESSFUL in 22s`. **Das Protokoll nennt keine Dateinamen der hochgeladenen Artefakte und keine Upload-URL außer dem Ziel oben**; die Dateiliste steht nicht im Protokoll.
- **Maskierung.** Im Protokoll von `make publish` steht `GITHUB_TOKEN: ***`, im Schritt `actions/checkout` `token: ***` und `AUTHORIZATION: basic ***`. Eine Suche in allen Protokolldateien beider Jobs nach den Präfixen `ghs_`, `ghp_`, `github_pat` und nach `Bearer` fand keinen Treffer. Das belegt für diesen Lauf, dass das Token in den Protokollzeilen, die die Suche erfasst, maskiert ist; die Suche kennt kein Muster außer den genannten.
- **`GITHUB_ACTOR`.** Der Wert steht nicht im Protokoll (der Name kommt nicht vor); dass er nicht leer war, folgt aus der Zeile des Skripts „Zugangsdaten gesetzt“ (Schluss aus der Prüfung des Skripts, nicht aus einer Ausgabe des Werts), und die Registry nahm die Anmeldung an.

## Was die Registry zeigt

Abfrage am 2026-11-15 nach dem Lauf, nur lesend.

| Angabe | Wert |
|---|---|
| Paket | `io.github.pt9912.tdswire`, Typ `maven`, Besitzer `pt9912` (Konto), `visibility` `public` |
| Zuordnung | Feld `repository`: `pt9912/tdswire` (`private: false`) |
| Versionen | `version_count` 1; Version `0.1.0`, Lizenz `MIT` |
| Zeit | Paket und Version `created_at` 2026-11-15 09:39:40 UTC, Version `updated_at` 09:39:41 UTC; das liegt in der Aufgabe `publishMavenPublicationToRegistryRepository` (09:39:39 bis 09:39:56) |
| Dateiliste | **ungemessen**: die Abfrage der Version liefert keine Dateien (nur Name, Lizenz, Zeiten, `package_type`); dass die Version Jar und POM (und die `.module`-Datei) trägt, ist hier nicht gesehen |

## Was die Liste des Ungemessenen aus dem Dispatch-Protokoll jetzt trägt

Nummern nach dem [Dispatch-Protokoll](veroeffentlichung-dispatch-2026-11-15.md).

1. **`GITHUB_TOKEN` mit `packages: write` schreibt ein Paket, auch das erste eines noch nicht bestehenden Pakets: belegt für diesen Lauf.** Das Protokoll nennt `Packages: write`, der Gradle-Schritt endete `success`, und das Paket trägt `created_at` im Lauf. Dass das Paket vorher nicht bestand, ist aus dem Zeitpunkt der Anlage und der Angabe, dies sei die erste Version, geschlossen; eine Abfrage vor dem Lauf gibt es nicht.
2. **Zuordnung des Pakets zum Repository: belegt** (Feld `repository` `pt9912/tdswire`, `visibility` `public`).
3. **Maskierung eines echten Schreibtokens im Protokoll: belegt** im Rahmen der Muster-Suche oben; `GITHUB_TOKEN: ***` steht im Schritt, der es verwendet.
4. **Klon eines Tag-Push für `tools/publish.sh` (Tag auf `HEAD`, sauberer Baum, losgelöster `HEAD`): belegt.**
5. **`git merge-base --is-ancestor HEAD origin/main` im Job `publish`: der positive Fall ist belegt.** Der Bruch bei einem Tag außerhalb von `main` bleibt **ungemessen**.
6. **Ein Bypass-Berechtigter kann einen Tag `v*` trotz „Restrict creations“ pushen: Quelle ist die Ausgabe des Pushs, vom Nutzer gesehen.** Sie nannte „Bypassed rule violations … Cannot create ref due to creations being restricted“; der Push gelang. Das ist am Push-Ausgang des Nutzers gemessen, nicht am Lauf, und dem Protokoll nicht zu entnehmen. **Ohne Bypass ist das Verhalten ungemessen** (ob der Push eines Nicht-Berechtigten abgewiesen wird, ist hier nicht gesehen).
7. **`GITHUB_ACTOR` auf dem Runner:** nicht leer (Schluss aus der Skript-Zeile), die Registry nahm die Anmeldung an; der Wert selbst steht nicht im Protokoll und ist nicht erhoben.
8. **Einstellung „Workflow permissions“ des Repositories:** in diesem Lauf trug `publish` `Packages: write` (Zeile des Protokolls); die Einstellung hat das Recht in diesem Lauf nicht eingeschränkt. Welchen Wert die Einstellung hat und ob ein anderer Wert das Recht einschränkt, ist **ungemessen**.
9. *(entfallen)* Docker-Version des Runners, wie im Dispatch-Protokoll; sie steht auch in diesem Protokoll nicht.
10. **Wirkung von `concurrency` bei zwei Läufen desselben Tags: ungemessen**; es gab einen Lauf.

## Was dieser Lauf nicht belegt

- **Konsum aus der Registry** (Punkt 2 der DoD des Slice): kein Konsument hat die Version aufgelöst oder gebunden; das braucht ein Lese-Token (`read:packages`) des Nutzers. Ob das Paket anonym lesbar ist, ist trotz `visibility` `public` nicht gemessen.
- Die Dateien der Version (Jar, POM, `.module`) und deren Inhalt in der Registry; die `.module`-Datei ist nicht gelesen.
- Der Bruch der Prüfung `origin/main`, die Abweisung ohne Bypass, die Einstellung „Workflow permissions“, `concurrency` (Punkte 5, 6, 8, 10 oben).
- Der Wortlaut der Oberfläche beim Anlegen des Tag-Schutzes: der Lauf berührt ihn nicht.
- Ein Re-Run, eine zweite Veröffentlichung derselben Version und das Löschen einer Version: nicht versucht.
