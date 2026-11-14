# Ein Release vorbereiten

Diese Anleitung ist für Maintainer. Sie nennt die Schritte eines Releases der Bibliothek, so weit sie entschieden und gebaut sind, die beiden geschuldeten Handlungen ohne Sensor (der Freshness-Audit der vendored Baseline und die Beobachtung von detekt) und die Entscheidungen, die der Nutzer noch nicht getroffen hat. Sie erfindet nichts: Wo etwas fehlt, steht es unter [Offene Entscheidungen](#offene-entscheidungen). Bezug: [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-RB-05](../../spec/lastenheft.md#tds-rb-05--lizenz), [ADR-0010](../plan/adr/0010-detekt-pre-release.md), [ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md), [ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md).

Das Produkt ist das Bibliotheks-Jar `tdswire.jar` mit seinem Verzeichnis `lib/`. Ein Produkt-Image gibt es nicht ([ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md)). Daneben ist die Bibliothek als Maven-Paket für GitHub Packages vorbereitet ([ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md)): Koordinaten `io.github.pt9912:tdswire`, Jar und POM; das Werkzeug besteht (Schritt 9), **eine Version ist nicht veröffentlicht**. Wer die Bibliothek einbettet, liest die [Nutzer-Anleitung](../user/einbettung-und-betrieb.md).

## Schritte

1. **Gates.** `make gates` im Wurzelverzeichnis muss grün sein. Es fährt unter anderem `make test`, `make detekt`, `make build`, `make docs-check` und `make baseline-verify` (Klassen und Stempel: [`harness/README.md`](../../harness/README.md) §Sensors). Das CI-Gegenstück ist `.github/workflows/gates.yml`; als Setzung dieser Anleitung (nicht entschieden) gilt der Lauf auf dem Release-Stand von `main` nur bei `success` (Abfrage `gh run list --workflow gates`). Das bleibt der Nachweis vor dem Tag. Der Job `gates` in `.github/workflows/veroeffentlichung.yml` führt `make gates` beim Tag-Push noch einmal und hält `publish` zurück, solange er rot ist; er ersetzt den Nachweis nicht, er sichert ihn ab ([ADR-0048](../plan/adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Entscheidung 2).
2. **E2E-Durchgang.** `make e2e` ist kein Gate ([ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md)). **Ob ein Release ihn verlangt, ist nicht entschieden**; wer ihn laufen lässt, vermerkt Ergebnis und Dauer im Release-Vermerk (Schritt 8).
3. **Freshness-Audit der vendored Baseline.** Siehe [Freshness-Audit](#freshness-audit-der-vendored-baseline).
4. **Beobachtung von detekt.** Siehe [Wächter für ADR-0010](#wächter-für-adr-0010-stabil-release-von-detekt-2x).
5. **Version.** Der Stand im Quelltext ist `version = "0.1.0-SNAPSHOT"` in `build.gradle.kts`. Das Schema ist `0.y.z` ohne Zusatz (`y` steigt bei jeder Änderung der öffentlichen Fläche nach `SPEC-059`, `z` sonst; `1.0.0` mit der ersten Zusage der semantischen Versionierung; [ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md) Entscheidung 3). Der Release-Commit setzt die Version ohne `-SNAPSHOT`; danach steht wieder ein `-SNAPSHOT` im Quelltext. Ein `-SNAPSHOT` wird nicht veröffentlicht, und eine veröffentlichte Version wird nie neu veröffentlicht (Korrektur = neue Version). Ob eine Änderung den Zähler `y` hebt, ist Handarbeit: ein Werkzeug für Binärkompatibilität gibt es bis `1.0` nicht.
6. **Artefakt.** `make build` baut in Docker (Stage `build-jar`) das Jar `tdswire.jar` ohne `Main-Class` und prüft: das Jar führt `META-INF/LICENSE`, gleich der Datei `LICENSE`, das Manifest nennt keine `Main-Class`, der `Class-Path` führt nur vorhandene Einträge `lib/<Name>`, in `lib/` liegt kein Jackson und im Jar keine Klasse des Testservers (`tools/manifest-pruefung.sh`, `SPEC-052`). Dieselbe Prüfung liest die POM des Maven-Pakets (`build/publications/maven/pom-default.xml`, erzeugt ohne Netz und ohne Token): sie nennt weder Jackson noch den Testserver und führt die Lizenz MIT mit URL. `make publish-vorschau` baut Jar und POM im Container in ein lokales Maven-Verzeichnis des Containers und gibt die POM aus; es schreibt nichts nach außen. **`make build` schreibt das Jar nicht auf den Host.** Ein Weg, Jar und `lib/` aus dem Bau herauszugeben (ohne Registry), besteht im Repo nicht; er ist offen.
7. **Lizenz.** Die Datei `LICENSE` im Wurzelverzeichnis ist die MIT-Lizenz (Rechteinhaber `pt9912`, Jahr 2026); der Bau (Schritt 6) und der Test `LizenzTest` halten sie gegen das Jar und gegen den Text. Hinweise zu den Lizenzen der Bibliotheken in `lib/` führt das Artefakt nicht; ob es sie braucht, ist eine offene Rechtsfrage ([ADR-0018](../plan/adr/0018-laufzeit-artefakt.md), Abschnitt „Offen“), und dieser Ablauf behauptet dazu nichts.
8. **Tag und Vermerk.** Der Tag heißt `v<Version>` (zum Beispiel `v0.1.0`) und liegt auf dem Release-Commit aus Schritt 5; im Repo besteht heute kein Tag. **Der Push eines Tags `v*` löst die Veröffentlichung aus** (Schritt 9): der Tag steht nur auf einem Commit, der in `main` liegt, und der Schutz der Tags ([Tag-Schutz `v*`](#tag-schutz-v)) steht vorher. Ein Tag auf einem anderen Commit veröffentlicht nach dem Text des Workflows nichts, der Job `publish` bricht in der Prüfung `git merge-base --is-ancestor HEAD origin/main` (gelesen, nie gelaufen). Die Prüfung steht in der Datei, die der Tag-Push selbst mitbringt; die tragende Kontrolle ist der Tag-Schutz, nicht die Prüfung. Der Vermerk (Ergebnis von Schritt 1 bis 4 samt Datum) gehört in die Beschreibung des Release-Commits oder des Tags (Setzung dieser Anleitung, nicht entschieden).
9. **Veröffentlichen.** Der Weg ist die CI: der Workflow `.github/workflows/veroeffentlichung.yml` läuft beim Tag-Push `v*`, führt `make gates` (Job `gates`) und danach, nur bei Erfolg und nur beim Push eines Tags, `make publish` (Job `publish`, `needs: gates`, Recht `packages: write` nur dort). `GITHUB_ACTOR` setzt der Runner, `GITHUB_TOKEN` stammt aus `secrets.GITHUB_TOKEN` und steht nur im Schritt `make publish`; ein persönliches Token ist dafür nicht nötig ([ADR-0048](../plan/adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md)). Ein Lauf per `workflow_dispatch` führt `gates` und `make publish-vorschau` aus und veröffentlicht nie. Wiederholt wird eine Veröffentlichung nicht: ein erneuter Lauf („Re-run“) für eine veröffentlichte Version ist nicht vorgesehen, Korrektur ist eine neue Version. `make publish` mit einem persönlichen *Personal Access Token (classic)* (Scope `write:packages`, in `GITHUB_ACTOR` und `GITHUB_TOKEN` der Umgebung des Aufrufs) bleibt möglich, ist aber nicht mehr der dokumentierte Weg; das Token steht nie in einer Datei, einem Argument oder einem Log. Das Eingangsskript `tools/publish.sh` nimmt als Ziel nur GitHub Packages; ein anderes Ziel (`--url`, für Belegläufe) verlangt ein Token, das mit `PLATZHALTER-` beginnt, das gilt nur für den Weg über `tools/publish.sh` und `make publish` und beruht auf der Präfixprüfung `PLATZHALTER-`: ein direkter `gradle publish` im Container (`build.gradle.kts` erlaubt `http://` über `isAllowInsecureProtocol`) und ein von Hand so genanntes echtes Token umgehen sie. Es bricht vor Docker, wenn die Version auf `-SNAPSHOT` endet oder nicht die Form `<zahl>.<zahl>.<zahl>` hat, der Tag `v<Version>` nicht auf `HEAD` liegt, der Baum nicht sauber ist oder eines der beiden Zugangsdaten leer ist. Danach läuft `gradle publish` in der Stage `publish`; die Version wird nie neu veröffentlicht. Dass `make gates` auf dem Release-Commit grün ist (Schritt 1), prüft das Target nicht. Das Target ist kein Gate und nicht Teil von `make e2e`. **Der Ablauf von `tools/publish.sh` ist belegt gegen einen Dummy-Server, nicht gegen GitHub**; eine erste Veröffentlichung und die Probe eines Konsumenten gegen die Registry stehen aus. Dass `GITHUB_TOKEN` mit `packages: write` ein Paket schreiben darf, dem Repository zugeordnet wird und im Log maskiert bleibt, dass der Klon eines Tag-Push die Prüfungen des Skripts besteht (Tag auf `HEAD`, sauberer Baum) und dass `origin/main` darin liegt, und dass der Job `publish` bei einem Tag auf einem Commit außerhalb von `main` bricht, ist ungemessen.

Nicht Teil dieses Ablaufs: Signierung und `sources`- oder `javadoc`-Jar (keine, [ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md) Entscheidung 4), Veröffentlichung von Testserver und Test-Image (keine), Software-Stückliste (nicht gebaut, nicht entschieden).

## Tag-Schutz `v*`

Wer einen Tag `v*` auf `main` pushen darf, löst eine Veröffentlichung aus ([ADR-0048](../plan/adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Konsequenzen). Den Schutz setzt der Maintainer **vor dem ersten Tag** in den Einstellungen des Repositories bei GitHub; kein Gate und kein Sensor prüft ihn, und der Workflow setzt ihn nicht.

**Quelle des Wortlauts:** GitHub-Dokumentation „Creating rulesets for a repository“ und „Available rules for rulesets“, am 2026-11-14 abgerufen. Die Oberfläche kann sich ändern; die Bezeichnungen unten sind der Stand dieses Abrufs, nicht gemessen am Repository dieses Projekts, und die Einstellung selbst ist hier nicht geprüft.

1. Im Repository **Settings** öffnen; links unter „Code, planning, and automation“ **Rulesets**, dann **New ruleset**.
2. **New tag ruleset** wählen und einen Namen vergeben (zum Beispiel `tags-v`).
3. Den Zustand „Enforcement status“ auf aktiv stellen; die Voreinstellung ist „Disabled“, ein deaktivierter Satz schützt nichts.
4. Unter „Bypass list“ nur die Rolle oder Person eintragen, die Tags `v*` setzen darf (zugelassen sind unter anderem Repository-Admins, die Rollen `maintain` und `write`, Teams und GitHub-Apps).
5. Unter „Target tags“ ein Ziel hinzufügen und das Muster `v*` angeben (fnmatch-Syntax).
6. Unter „Tag protections“ die Regeln **Restrict creations**, **Restrict updates** und **Restrict deletions** wählen: dann darf nur, wer in der Bypass-Liste steht, einen passenden Tag anlegen, verschieben oder löschen (laut Dokumentation ist „Restrict deletions“ vorgewählt).
7. Speichern mit **Create**.

Ob die Regeln auch das Anlegen eines Tags per Push eines Bypass-Berechtigten zulassen und ob die Workflow-Berechtigungen des Repositories (Settings, Actions, General, „Workflow permissions“) die Rechte `permissions` der Datei einschränken, ist hier nicht gemessen; der Maintainer sieht es beim ersten Tag und am Dispatch-Lauf.

## Freshness-Audit der vendored Baseline

`make baseline-verify` hält den Baum unter `.harness/baseline/` gegen `SHA256SUMS`; das ist die Integritätshälfte und sagt über das Alter nichts ([`harness/conventions.md`](../../harness/conventions.md) §Baseline). Das Alter prüft diese Handlung, kein Sensor: Die Gates laufen netzlos, und ein Sensor dafür *kommt nicht mit*.

1. Den gepinnten Tag lesen: das Verzeichnis unter `.harness/baseline/` (`make baseline-verify` nennt ihn in der Ausgabe).
2. Die **Release-Liste** des Kurs-Repos ansehen, nicht das Asset des Tags: `gh release list -R pt9912/ai-harness-course -L 5` oder die Release-Übersicht <https://github.com/pt9912/ai-harness-course/releases>. Ein Abgleich des Assets zeigt, ob sich dieser Tag änderte, nicht, ob ein neuerer existiert.
3. Steht ein neuerer Tag in der Liste, ist der Pin zu heben (eigener Slice: Baseline tauschen, die Tabelle der Regelblöcke in `harness/conventions.md` von Hand gegen den neuen Baum prüfen, jede aktive Adaption `MR-*` gegen die neue Fassung lesen). Steht keiner, ist der Pin aktuell.
4. Das Ergebnis mit Datum und dem Tag, der als jüngster erschien, im Release-Vermerk festhalten (Schritt 8).

**Grenze:** Der Schritt ist eine Handlung. Wer ihn auslässt, wird von keinem Gate erwischt.

## Wächter für ADR-0010: Stabil-Release von detekt 2.x

[ADR-0010](../plan/adr/0010-detekt-pre-release.md) pinnt detekt `2.0.0-alpha.6` (`gradle/libs.versions.toml`) und nennt als Re-Evaluierungs-Trigger: „Erscheint ein Stabil-Release von detekt 2.x, oder bricht ein Versionswechsel innerhalb der Alpha-Reihe `detekt.yml`.“ Der zweite Fall macht `make detekt` rot, soweit die Konfigurationsvalidierung ihn erfasst (so misst es die ADR). Für den ersten Fall gibt es keinen Sensor; der Träger ist dieser Schritt.

**Die Abfrage.** Ein Stabil-Release von 2.x liegt vor, wenn die Maven-Metadaten von `dev.detekt:detekt-cli` eine Version `2.<n>.<n>` ohne Zusatz führen:

```sh
curl -s https://repo1.maven.org/maven2/dev/detekt/detekt-cli/maven-metadata.xml \
  | grep -E '<version>2\.[0-9]+\.[0-9]+</version>'
```

Keine Ausgabe heißt: kein Stabil-Release. Eine Zeile heißt: Trigger eingetreten. Gegenprobe über GitHub: `gh release list -R detekt/detekt -L 10` führt Pre-Releases als `Pre-release`; ein `v2.*` ohne diese Marke ist ein Stabil-Release.

**Belegt ist** (einmal, 2026-11-06, Host, Netz): Die Metadaten führten die Versionen `2.0.0-alpha.0` bis `2.0.0-alpha.6` (`latest` und `release` zeigten `2.0.0-alpha.6`), die Abfrage lieferte keine Ausgabe; auf GitHub standen die Alphas als `Pre-release` und `v1.23.8` als `Latest`. Dieselbe Abfrage an einer Kopie der Metadaten, in der `2.0.0-alpha.6` durch `2.0.0` ersetzt war, lieferte eine Zeile, mit `2.0.0-rc.1` keine; dass die Abfrage greift, ist damit an diesen zwei Änderungen gesehen. Nicht belegt: dass ein Stabil-Release von 2.x unter derselben Koordinate erscheint; die Koordinate ist die des Pins.

**Ausgang beim Eintreten.** Ein Slice mit einer neuen ADR, die die bisherige ersetzt (`Supersedes`). [ADR-0010](../plan/adr/0010-detekt-pre-release.md) bleibt `Accepted` und unverändert; ein Wechsel der Version ist kein Handgriff am Pin. Die offenen Gegenmaßnahmen der ADR für den `ktlint`-Block gehören in diesen Slice. Eine Lockerung der Gates braucht eine ADR (`AGENTS.md` §3.6).

**Grenze.** Der Schritt hat keinen Auslöser und kein Gate: wer ihn vergisst, bemerkt den Trigger nur zufällig. Ein Werkzeug-Target, das die Abfrage in Docker fährt und meldet, ohne Gate-Wirkung, besteht nicht; ob es entsteht, entscheidet der Nutzer.

## Offene Entscheidungen

Diese Punkte hat der Nutzer nicht entschieden; die Anleitung setzt keinen davon.

- Ob eine Software-Stückliste entsteht.
- Wie Jar und `lib/` aus dem Bau herausgegeben werden (heute nur im Bau geprüft, nicht abgelegt).
- Ob `make e2e` Teil eines Releases ist.
- Ob der Wächter für detekt zusätzlich ein Werkzeug-Target bekommt.
- Lizenzhinweise zu den Bibliotheken in `lib/` (Rechtsfrage) und Dateirechte der Benutzerliste im Betrieb ([ADR-0018](../plan/adr/0018-laufzeit-artefakt.md), Abschnitt „Offen“).
