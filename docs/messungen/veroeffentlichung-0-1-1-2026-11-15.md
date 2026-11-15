# Veröffentlichung der Version 0.1.1 auf GitHub Packages und Cloudsmith: Messprotokoll vom 2026-11-15

Messprotokoll zu [TDS-RB-05](../../spec/lastenheft.md#tds-rb-05--lizenz), [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) und dem Konsum aus der Registry ([TDS-RB-01](../../spec/lastenheft.md#tds-rb-01--implementierungssprache), Zielbild-Lücke bleibt benannt) (`AGENTS.md` §5 Regel 7), Gegenstand von [ADR-0049](../plan/adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) Entscheidungen 2, 4a und 5. Es hält fest, was der Tag-Lauf `v0.1.1`, die Registries danach und ein Probe-Konsument gezeigt haben, nicht, ob eine Anforderung belegt ist. **Der Lauf hat eine Version in zwei Registries geschrieben; sie ist nicht rücknehmbar.** Das [Messprotokoll des Tag-Laufs von `0.1.0`](veroeffentlichung-tag-lauf-2026-11-15.md) bleibt wahr (es beschreibt `0.1.0` und GitHub Packages). Alle Zeiten UTC, alle Angaben Einzelmessungen.

**Erzeuger.** Der Workflow `.github/workflows/veroeffentlichung.yml` und `tools/publish.sh` auf dem Release-Commit `9c7439f541ae90abe601945dab7a46b879874b6d` (Tag `v0.1.1`, Push durch den Nutzer `pt9912`). Quellen: die Felder von `gh api repos/pt9912/tdswire/actions/runs/43734101990/attempts/<n>` und `…/attempts/<n>/jobs` für `n` 1 bis 5, die Protokolle der fünf Versuche des Jobs `publish-cloudsmith` (`gh api …/actions/jobs/<id>/logs`) und das des Jobs `publish` im fünften Versuch, `gh secret list` (nur Namen und Zeit der letzten Änderung, nie ein Wert), `gh api /users/pt9912/packages/maven/io.github.pt9912.tdswire/versions`, anonyme `curl`-Abrufe und `tools/publish-probe.sh url`.

## Lauf und Versuche

Lauf [43734101990](https://github.com/pt9912/tdswire/actions/runs/43734101990), Ereignis `push`, `headBranch` `v0.1.1`, `headSha` wie oben. **Der Lauf hat sechs Versuche** (`run_attempt` 6, per `gh api` abgefragt): in den Versuchen 1 bis 4 war der Job `publish-cloudsmith` rot, in Versuch 5 grün, Versuch 6 führte keinen Job neu aus.

| Versuch | Start des Versuchs | Job `publish-cloudsmith` | Ausgang | Auslöser |
|---|---|---|---|---|
| 1 | 16:11:51 | 16:21:34 bis 16:23:26 | failure | Tag-Push |
| 2 | 16:45:33 | 16:45:37 bis 16:47:44 | failure | Wiederholung durch den Nutzer |
| 3 | 16:51:04 | 16:51:07 bis 16:52:50 | failure | Wiederholung durch den Nutzer |
| 4 | 17:01:33 | 17:01:37 bis 17:03:26 | failure | Wiederholung durch den Nutzer |
| 5 | 17:16:05 | 17:16:09 bis 17:17:58 | success | Wiederholung durch den Nutzer |
| 6 | 17:21:45 | keiner neu; der Job trägt die Zeiten von Versuch 5 (17:16:09 bis 17:17:58) | success (Versuch 17:21:45 bis 17:21:48) | `triggering_actor` `pt9912`; Wiederholungsanfrage des Orchestrators, siehe unten |

(„Auslöser“: `triggering_actor` jedes Versuchs ist `pt9912`; dass es „Re-run failed jobs“ war und nicht „Re-run all jobs“, folgt aus dem Verhalten der anderen Jobs, siehe unten.)

Weitere Jobs im ersten Versuch: `gates` success 16:11:53 bis 16:21:33 (rund 9 min 40 s), `publish` (GitHub Packages) success 16:21:35 bis 16:24:20 (rund 2 min 45 s), `vorschau` skipped. Gesamtausgang des Laufs im ersten Versuch `failure`, im fünften `success`. Beide Veröffentlichungs-Jobs starten unmittelbar nach `gates` (16:21:34 und 16:21:35) und laufen nebeneinander; keiner hängt am anderen.

### Der Fehler: HTTP 401 am ersten PUT

Wortlaut in allen vier roten Versuchen, Zeile des Protokolls, gleich bis auf die Zeit (Beispiel Versuch 1, 16:23:24):

`> Could not PUT 'https://maven.cloudsmith.io/pt9912/tdswire/io/github/pt9912/tdswire/0.1.1/tdswire-0.1.1.jar'. Received status code 401 from server: Unauthorized`

Davor `> Task :publishMavenPublicationToCloudsmithRepository FAILED`, danach `BUILD FAILED in 7s` (Versuch 2: 9 s, 3: 6 s, 4: 6 s), `make: *** [tools/mk/publish.mk:18: publish-cloudsmith] Error 1`. Der erste PUT ist der des Jars; die Anmeldung wurde vor dem ersten Byte abgewiesen. Die Prüfung des Eingangsskripts bestand in jedem Versuch (Zeile `publish: Version 0.1.1, Tag v0.1.1 auf HEAD, Baum sauber, Zugangsdaten gesetzt; Ziel cloudsmith, https://maven.cloudsmith.io/pt9912/tdswire/`), das Protokoll sagt also nur, dass beide Secrets nicht leer waren, nicht, dass sie gültig waren. Warum der Server abwies, nennt das Protokoll nicht.

Änderungen der Secrets, aus `gh secret list` (Zeit der letzten Änderung, nicht Wert, nicht Urheber): `CLOUDSMITH_API_KEY` 16:50:46, `CLOUDSMITH_USERNAME` 17:15:53. Zeitlich liegt damit: Versuch 1 und 2 vor beiden Änderungen (401), Versuch 3 und 4 nach der Änderung des Schlüssels und vor der des Benutzernamens (401), Versuch 5 nach beiden (success). Ob ein Versuch das Secret zum Zeitpunkt seines Starts oder des Jobs bindet, ist hier nicht gemessen; die Lesart oben nimmt den Start des Versuchs. **Ursache nach Angabe des Nutzers: der Benutzername** (Secret `CLOUDSMITH_USERNAME`); das Protokoll belegt sie nicht, es belegt, dass der Austausch dieses einen Secrets nach dem Schlüssel-Austausch aus 401 ein `success` machte. **Welche Form des Benutzernamens richtig ist, steht hier nicht**: der Wert wurde nicht gelesen und ist im Protokoll maskiert. Dass der Schlüssel in Versuch 1 und 2 auch ungültig war, folgt aus keiner Messung (er wurde vor Versuch 3 ersetzt, der Grund ist ungemessen).

### Der grüne Versuch

Versuch 5, Job `publish-cloudsmith`: `make publish-cloudsmith` ab 17:16:12; der Bau der Stage `publish` lief bis rund 17:17:37 (`naming to docker.io/library/tdswire-publish:lokal`), `generatePomFileForMavenPublication` 17:17:45, `publishMavenPublicationToCloudsmithRepository` im Protokoll um 17:17:55 ohne `FAILED`, `BUILD SUCCESSFUL in 15s`. Der Job endete 17:17:58.

### Wirkung von „Re-run failed jobs“: nur der rote Job lief neu (gemessen)

In den Versuchen 2 bis 6 tragen die Jobs `gates` und `publish` unverändert Start und Ende des ersten Versuchs (`gates` 16:11:53 bis 16:21:33, `publish` 16:21:35 bis 16:24:20), nur die Job-Kennung und das Feld `run_attempt` ändern sich; `vorschau` bleibt `skipped`. Das Protokoll des Jobs `publish` unter der Kennung des fünften Versuchs (132891254685) trägt Zeitstempel von 16:21 bis 16:24, nicht von 17:16. Die Pakete-API führt für `0.1.1` **eine** Anlage (`created_at` 16:23:51, `updated_at` 16:23:52). Gemessen ist damit: die Wiederholung betraf nur den roten Job, `publish` hat die veröffentlichte Version nicht ein zweites Mal geschrieben. Das entspricht der Lesart von [ADR-0049](../plan/adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) Entscheidung 2 (zuvor Dokumentationsstand aus dem Schwesterprojekt). Nicht gemessen: ob „Re-run all jobs“ dasselbe täte (es wurde nicht versucht).

**Versuch 6:** Er startete 17:21:45 und endete 17:21:48 (`success`); alle Jobs tragen die Zeiten der früheren Versuche, `vorschau` ist `skipped`, `gates`, `publish` und `publish-cloudsmith` liefen nicht neu (gemessen an den Zeiten der Abfrage `…/attempts/6/jobs`). Nach Angabe des Orchestrators war dies eine Wiederholungsanfrage, die `gh` mit „cannot be rerun“ ablehnte; dass die Anfrage den Versuch 6 erzeugte, ist aus diesem Zusammentreffen (Zeit der Anfrage, Fehlen eines neuen Jobs) geschlossen, nicht an einer Ausgabe der API belegt: die Abfrage nennt `run_attempt` und Zeiten, nicht die Ursache. Das Protokoll des Versuchs 6 ist nicht gelesen. Ein Versuch 7 besteht nicht (HTTP 404). Der Gesamtausgang des Laufs ist `success`, `updatedAt` 17:21:48.

Nach vier roten Versuchen trägt Cloudsmith genau ein Paket (`0.1.1`, siehe unten), kein Teilbestand aus den abgewiesenen PUTs: die API listet nur das Paket aus Versuch 5. Ob einzelne abgewiesene Dateien anderswo (etwa in einem Zwischenspeicher) verblieben wären, ist nicht gesehen; der 401 kam vor dem ersten Byte.

## Was die Registries zeigen

### GitHub Packages

Abfrage nach dem Lauf, nur lesend: Paket `io.github.pt9912.tdswire`, Versionen `0.1.1` (`created_at` 16:23:51, `updated_at` 16:23:52) und `0.1.0` (09:39:40, 09:39:41). Die Dateien der Version (Jar, POM, `.module`) zeigt die Pakete-API nicht. **Anonym** (ohne Token) antwortet `maven.pkg.github.com` auf `…/io/github/pt9912/tdswire/0.1.1/tdswire-0.1.1.pom` und auf `….jar` mit **401**: Maven-Abruf bei GitHub Packages verlangt eine Anmeldung, auch wenn das Paket `visibility` `public` führt (die offene Frage des Protokolls von `0.1.0`).

### Cloudsmith

Anonym gemessen (ohne Zugangsdaten), `https://dl.cloudsmith.io/public/pt9912/tdswire/maven/io/github/pt9912/tdswire/`:

| Pfad | HTTP | Größe in Bytes |
|---|---|---|
| `maven-metadata.xml` | 200 | 330 |
| `0.1.1/tdswire-0.1.1.pom` | 200 | 1643 |
| `0.1.1/tdswire-0.1.1.jar` | 200 | 1784788 |
| `0.1.1/tdswire-0.1.1.module` | 200 | 2858 |
| `.jar.sha1`, `.pom.sha1`, `.module.sha1`, `.jar.md5`, `.pom.md5`, `.module.md5`, `.jar.sha256`, `.jar.sha512`, `.module.sha256`, `.module.sha512`, `maven-metadata.xml.sha1` | 200 | 40, 40, 40, 32, 32, 32, 64, 128, 64, 128, 40 |
| `0.1.0/tdswire-0.1.0.pom`, `0.1.0/tdswire-0.1.0.jar` | 404 | 324 |

`maven-metadata.xml` nennt `latest` und `release` `0.1.1`, `versions` nur `0.1.1`, `lastUpdated` 17:19:19. Die Prüfsummen `.pom.sha256` und `.pom.sha512` sind nicht abgerufen. `0.1.0` liegt nicht auf Cloudsmith, wie in [ADR-0049](../plan/adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) Entscheidung 4a gesetzt. **Dass das Repository als „Open source“ anonym ausliefert, ist damit belegt** (200 ohne Zugangsdaten für POM, Jar und Metadaten); ein 404 vor der ersten Version war es nicht.

Dateiliste der Version, aus der API `https://api.cloudsmith.io/v1/packages/pt9912/tdswire/` (anonym, HTTP 200, ein Paket): `tdswire-0.1.1.jar` (primär, Format `maven`), `tdswire-0.1.1.pom` (1643 Bytes), `tdswire-0.1.1.module` (2858 Bytes); Lizenz „MIT License“, Besitzer `pt9912`, Status `Completed`. **Cloudsmith nimmt die `.module` an und liefert sie aus.** Das Feld `size` des Pakets, 1789289, ist die Summe der drei Dateien (1784788 + 1643 + 2858).

### Sammelfenster und Wartezeit

Aus der API: `uploaded_at` 17:17:46.68 (Jar), `sync_finished_at` 17:19:19.09 (`is_sync_completed` wahr), `lastUpdated` der Metadaten 17:19:19: rund **92 Sekunden** vom Upload bis zur abgeschlossenen Verarbeitung. Der Job selbst endete 17:17:58, also rund 81 Sekunden vor dem Abschluss. **Ungemessen** bleibt, wann der anonyme Abruf erstmals 200 lieferte: kein Abruf lief im Fenster zwischen 17:17:58 und 17:19:19; der erste anonyme Abruf dieser Messung liegt rund sechs Minuten nach `sync_finished_at` (Dateizeiten der Abrufe und der Probe 17:25 bis 17:26 UTC), und damit ist ein anonymer 200 erst ab etwa 17:25 gemessen. Die Wartezeit ist daher die der Verarbeitung nach der API, nicht die erste gesehene Auslieferung; das akzeptierte Negativ von [ADR-0049](../plan/adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) (Job-Erfolg ist noch keine Auslieferung) ist damit der Größe nach gesehen.

## Konsum

### Anonym über Cloudsmith (belegt)

`tools/publish-probe.sh url https://dl.cloudsmith.io/public/pt9912/tdswire/maven/ 0.1.1 anonym`, Host, Docker-Standardnetz, nur lesend, kein Zugang aus der Umgebung gelesen; Dauer des Laufs rund 54 s (17:25:10 bis 17:26:04, Image-Bau aus dem Cache). Ein Kotlin-Konsument mit Gradle löst `io.github.pt9912:tdswire:0.1.1` aus dieser URL auf (`content { includeGroup("io.github.pt9912") }`, daneben Maven Central für die Abhängigkeiten der Bibliothek), übersetzt und startet den Server über die öffentliche Fläche:

| Art | Ausgang |
|---|---|
| mit `gradleMetadata()` (die `.module` ist freigegeben) | Exit 0, `PROBE-OK` |
| nur POM (`ignoreGradleMetadataRedirection()`) | Exit 0, `PROBE-OK` |

Belegt: Gradle bindet die Version ohne Zugangsdaten über Cloudsmith, mit und ohne `.module`, und die Abhängigkeiten der Bibliothek lösen über Maven Central auf. **Ungemessen:** ob Gradle im Lauf „mit `.module`“ die `.module` tatsächlich abrief (die Ausgabe von `url` führt anders als bei `registry` keine Liste der Anfragen; am Dummy-Server war das Verhalten gemessen, nicht an Cloudsmith); Java und Maven; ein Konsument auf einem anderen Rechner als diesem Host.

### Mit Token über GitHub Packages (ungemessen)

**Nicht gemessen.** In der Umgebung dieser Messung stand kein Lese-Token bereit (kein `GITHUB_TOKEN`, kein `GITHUB_ACTOR`, keine Variable dieser Art; die Umgebung wurde nur auf Namen durchsucht). Das Token der Anmeldung von `gh` wurde nicht für den Konsum benutzt. Ungemessen bleiben damit: der Probe-Konsument gegen `https://maven.pkg.github.com/pt9912/tdswire` mit einem Token `read:packages`, die Dateien von `0.1.1` und die von `0.1.0` aus der Registry gesehen (deren Dateiliste war schon im Protokoll von `0.1.0` ungemessen). Gemessen ist nur die Abweisung ohne Token (401, oben). Aufruf für den Nutzer (das Token steht in der Umgebung des Aufrufs, nie in Datei oder Argument): `REG_USER=<Konto> REG_TOKEN=<Token read:packages> bash tools/publish-probe.sh url https://maven.pkg.github.com/pt9912/tdswire 0.1.1 zugang`.

## Maskierung im Protokoll

**Erzeuger nicht im Repo:** die Zählungen stammen aus einem Wegwerf-Skript (Python, reguläre Ausdrücke je Muster, Ausgabe nur Anzahlen) im Scratchpad der Messung, die `curl`-Tabelle aus einem Shell-Skript mit `curl -s -o /dev/null -w '%{http_code} %{size_download}'` je Pfad; beide liegen nicht im Repo. Die `curl`-Werte sind an der öffentlichen Registry wiederholbar (vom Reviewer fünf wiederholt, gleich), die Protokolle selbst liegen nur im Scratchpad und bei GitHub (Aufbewahrungsfrist); die Zählungen sind vom Reviewer mit eigenem Skript an den Einzelprotokollen wiederholt worden, gleich. Die Suche im Gesamtprotokoll des Laufs ist nicht nachgelesen worden.

Gesucht in den Protokollen der Versuche 1 bis 5 des Jobs `publish-cloudsmith`, im Protokoll des Jobs `publish` (fünfter Versuch) und im Gesamtprotokoll des Laufs (erster Versuch und Versuch 2), ohne je einen Wert auszugeben (nur Anzahlen):

- Im Schritt `make publish-cloudsmith` steht je Protokoll genau eine Zeile `CLOUDSMITH_USERNAME: ***` und eine `CLOUDSMITH_API_KEY: ***`; `GITHUB_TOKEN: ***` im Schritt `make publish`. Kein Protokoll führt `CLOUDSMITH_API_KEY:` oder `CLOUDSMITH_USERNAME:` mit etwas anderem als `***`.
- Keine Treffer für die Präfixe `ghs_`, `ghp_`, `github_pat`, für `Bearer`, `Basic <Zeichen>`, `X-Api-Key`, `api_key=` mit Wert und für `token`/`key`/`secret`/`password` gefolgt von 32 oder mehr Base64-Zeichen. Der eine Treffer des Wortes „authorization“ je Protokoll ist die Zeile des Schrittes `actions/checkout`, die das Token von GitHub setzt: `AUTHORIZATION: basic ***`, maskiert.
- Alle Folgen von 40 Hexzeichen in den Protokollen der Cloudsmith-Jobs sind drei verschiedene und bekannte Werte: Commit des Runner-Provisioners, Release-Commit, SHA der Checkout-Action. Der Schlüssel ist, soweit seine Länge bekannt ist, nicht in dieser Form im Log; welches Format der Schlüssel hat, ist hier nicht bekannt (er wurde nicht gelesen).

Belegt für diesen Lauf: der Schlüssel und der Benutzername sind in den durchsuchten Zeilen maskiert (`***` an der Stelle ihrer Verwendung). Die Suche kennt nur die genannten Muster; eine abgeleitete Form (zum Beispiel Base64 von `Benutzer:Schlüssel`) hätte sie nur über die Muster `Basic` und die lange Base64-Folge erfasst. Dass die Maskierung auch bei einem fehlgeschlagenen Aufruf hält (die Fehlerzeile nennt nur die URL), ist an den vier roten Versuchen gesehen.

## Was der Lauf belegt

- **Schreibrecht des Schlüssels:** Versuch 5 schrieb Jar, POM und `.module` nach `pt9912/tdswire`; der Schlüssel (Stand 16:50:46) mit dem Benutzernamen (Stand 17:15:53) hat Schreibrecht auf dieses Repository. Dass der Service **nur** dort schreiben darf (Planbedingung), ist nicht gemessen.
- **Benutzername:** ein falscher Wert endet vor dem ersten Byte in 401; der Austausch des Secrets `CLOUDSMITH_USERNAME` war nach Angabe des Nutzers die Ursache. Der Wert der richtigen Form ist hier nicht festgehalten.
- **Name der Aufgabe im Lauf:** `publishMavenPublicationToCloudsmithRepository` (Protokoll).
- **Teilfehler:** eingetreten. `gates` und `publish` blieben grün, der Cloudsmith-Job rot; die Wiederholung des roten Jobs allein stellte das Ziel her, ohne dass `publish` neu schrieb.
- **Zeitpunkt des Schreibens in GitHub Packages und Cloudsmith:** 16:23:51 und 17:17:46, rund 54 Minuten auseinander; `0.1.1` ist dort so wie in Cloudsmith dieselbe Version aus demselben Release-Commit.

## Was ungemessen bleibt

- Der Konsum mit Token über GitHub Packages und die Dateiliste der Versionen `0.1.0` und `0.1.1` dort (kein Token verfügbar, siehe oben).
- Der erste anonyme 200 nach dem Upload und damit das Sammelfenster von 60 Sekunden als eigene Größe; gemessen ist die Verarbeitung (92 s) nach der API.
- Ob Gradle die `.module` bei Cloudsmith im Lauf „mit `.module`“ abrief; Java und Maven als Konsumenten.
- Ob eine Löschung bei Cloudsmith die Version wieder freigäbe (nicht versucht); ob die abgewiesenen PUTs etwas hinterließen (die API zeigt nichts).
- Ob „Re-run all jobs“ `publish` ein zweites Mal ausgeführt hätte (nicht versucht).
- Ob die Secrets beim Start des Versuchs oder des Jobs gebunden werden (nur zeitlich eingegrenzt).
- Der Tag-Schutz `v*` und die Gegenprobe ohne Bypass (kein Gegenstand des Laufs); die Bedingungen und das Kontingent von Cloudsmith; ob das Repository dauerhaft ausliefert.
