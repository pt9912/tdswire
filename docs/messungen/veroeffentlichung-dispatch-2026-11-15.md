# Dispatch-Lauf des Workflows `veroeffentlichung.yml`: Messprotokoll vom 2026-11-15

Messprotokoll zu [TDS-RB-05](../../spec/lastenheft.md#tds-rb-05--lizenz) und [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) (`AGENTS.md` §5 Regel 7), Gegenstand von [ADR-0048](../plan/adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Entscheidung 4. Es hält fest, was **ein** Lauf von `workflow_dispatch` auf GitHub gezeigt hat, nicht, ob eine Anforderung belegt ist. **Es gab keinen Tag-Lauf. Nichts wurde in eine Registry geschrieben, kein Tag gesetzt, kein Token gelesen oder verwendet.** Der Lauf ist eine Einzelmessung.

**Erzeuger.** Der Workflow `.github/workflows/veroeffentlichung.yml` und das Target `make publish-vorschau` (`tools/mk/publish.mk`) liegen im Repo, Stand `5058c91b18d1fd3ce45a6bcc35b76a0ebf416a09`. Die Zahlen unten sind Zeitstempel des Laufprotokolls (`gh api repos/pt9912/tdswire/actions/runs/43619591947/logs`) und der Felder von `gh run view 43619591947 --json jobs,createdAt,updatedAt,headSha,event`; wiederholbar ist der Lauf durch erneutes Auslösen, nicht seine Dauer.

## Bedingungen

| Angabe | Wert |
|---|---|
| Lauf | [43619591947](https://github.com/pt9912/tdswire/actions/runs/43619591947), erster Versuch, ausgelöst und gestartet von `pt9912` |
| Ereignis, Ref | `workflow_dispatch` auf `main`, `headSha` `5058c91b18d1fd3ce45a6bcc35b76a0ebf416a09` |
| Zeitraum | 2026-11-14, `createdAt` 17:09:51 UTC, `updatedAt` 17:24:47 UTC (rund 14 min 56 s) |
| Runner | gehostet, Label `ubuntu-24.04`, Runner-Image Version `20261111.127.1`, Runner-Programm 2.338.2 |
| Docker-Version | nicht erhoben und nicht gefordert: das Laufprotokoll gibt sie nicht aus, die Liste der Software im Image ist nur verlinkt, nicht gelesen. Die Anforderung ist durch Entscheidung des Nutzers (2026-11-15) aus der DoD des Slice gestrichen; Runner-Label und Runner-Image-Version sind belegt, die Docker-Version ändert keine Aussage dieses Protokolls |
| Klon | `actions/checkout` auf Commit-SHA `d23441a48e516b6c34aea4fa41551a30e30af803`, `fetch-depth: 0`, `persist-credentials: false` |

## Ausgang und Dauer je Job

| Job | Ausgang | Start bis Ende (UTC) | Dauer | Anmerkung |
|---|---|---|---|---|
| `gates` | success | 17:09:54 bis 17:23:03 | rund 13 min 9 s | Schritt `make gates` von 17:09:58 bis rund 17:23:00; 1402 Testzeilen `PASSED` im Protokoll |
| `vorschau` | success | 17:23:05 bis 17:24:47 | rund 1 min 42 s | Schritt `make publish-vorschau` von 17:23:14 bis 17:24:45 (rund 91 s): `docker build` der Stage `publish` rund 84 s, `docker run` mit `publishToMavenLocal` rund 7 s |
| `publish` | skipped | 17:23:04 bis 17:23:04 | 0 s | keine Schritte, kein Protokoll |

Die Läufe haben nacheinander statt nebeneinander gerechnet: `vorschau` hat `needs: gates` und startete nach dessen Ende. Der Zeitposten ist `make gates` (rund 88 % der Laufzeit der beiden Jobs).

## Was das Protokoll zeigt

- **`publish` übersprungen.** Die API meldet `conclusion: skipped` ohne Schritte. Das Protokoll von `publish` liegt nicht vor; die Bedingung `github.event_name == 'push' && github.ref_type == 'tag'` ist für diesen Job also **nicht im Protokoll ausgewertet gezeigt**. Gezeigt ist die Auswertung für `vorschau` (`github.event_name == 'workflow_dispatch'` ergibt `true`). Dass die Bedingung von `publish` die Ursache ist, folgt daraus, dass `gates` erfolgreich war und `publish` nur von `gates` abhängt (Schluss, nicht Messung).
- **`make publish-vorschau` im Lauf.** Ausgegeben sind `maven-metadata-local.xml`, `tdswire-0.1.0-SNAPSHOT.jar`, `tdswire-0.1.0-SNAPSHOT.module`, `tdswire-0.1.0-SNAPSHOT.pom` und die POM mit `groupId` `io.github.pt9912`, `artifactId` `tdswire`, Version `0.1.0-SNAPSHOT`, Lizenz MIT, `scm` auf `https://github.com/pt9912/tdswire` und den zwei Abhängigkeiten `kotlinx-coroutines-core-jvm` 1.10.1 und `kotlin-stdlib` 2.4.10, beide Scope `compile`. Die lokale Messung der POM steht in [maven-paket-2026-11-14.md](maven-paket-2026-11-14.md); ein Abgleich der Zeilen beider Ausgaben ist hier nicht erfolgt.
- **Rechte des Tokens in den Jobs ohne Schreibrecht.** Das Protokoll von `gates` und von `vorschau` nennt unter „GITHUB_TOKEN Permissions“ je `Contents: read` und `Metadata: read`, wie die Datei es für diese Jobs vergibt.
- **Kein Token im Protokoll.** Eine Suche in beiden Protokollen nach den Präfixen `ghs_`, `ghp_`, `github_pat` und nach `Bearer` fand nichts; die Treffer auf `password` sind Namen von Testfällen. Der Wert `token` im Schritt `actions/checkout` erscheint als `***`, die Kopfzeile `AUTHORIZATION: basic ***` ebenso. Das belegt nur, dass das Lesetoken des Klons maskiert ist; ein Schreibtoken kam in diesem Lauf nicht vor.
- **Kein Zugriff auf GitHub Packages.** Keines der beiden Protokolle enthält `pkg.github.com`. Die Stage `publish` baute mit `--offline`; das Docker-Image und die Gradle-Abhängigkeiten kamen aus ihren Quellen (`docker.io`, Abhängigkeits-Stage), nicht aus GitHub Packages.

## Was dieser Lauf nicht belegt

Ungemessen, weil `publish` nicht lief und kein Tag-Ref vorlag (Punkt 9 ausgenommen, er entfällt als Anforderung); jeder übrige Punkt braucht eine echte Veröffentlichung oder einen Tag-Lauf ([ADR-0048](../plan/adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Entscheidung 4):

1. dass `GITHUB_TOKEN` mit `permissions: packages: write` auf dem Runner ein Paket schreiben darf, auch das erste eines noch nicht bestehenden Pakets;
2. die Zuordnung eines Pakets zum Repository;
3. die Maskierung eines echten Schreibtokens im Protokoll (hier kam keines vor);
4. der Klon eines Tag-Push: Tag auf `HEAD`, sauberer Baum für `tools/publish.sh`, losgelöster `HEAD`;
5. die Prüfung `git merge-base --is-ancestor HEAD origin/main` im Job `publish`, auch ihr Bruch bei einem Tag außerhalb von `main`;
6. ob ein Bypass-Berechtigter trotz der Regel „Restrict creations“ einen Tag `v*` pushen kann (Einstellung bei GitHub, Anleitung in [`releasing.md`](../maintainer/releasing.md#tag-schutz-v));
7. das Verhalten von `GITHUB_ACTOR` auf dem Runner (der Wert wird erst von `make publish` gelesen);
8. ob die Einstellung „Workflow permissions“ des Repositories das Recht `packages: write` einschränkt: das Protokoll zeigt nur Jobs mit `contents: read`;
9. *(entfallen)* die Docker-Version des Runners: keine Anforderung mehr, siehe Bedingungen; sie ist kein offener Punkt;
10. die Wirkung von `concurrency` bei zwei Läufen desselben Tags.

Der Lauf zeigt, dass der Workflow auf `main` syntaktisch angenommen wird, `gates` auf dem Runner grün durchläuft, `vorschau` die Stage `publish` ohne Token baut und die POM ausgibt, und dass `publish` bei `workflow_dispatch` nicht läuft.
