# Ein Release vorbereiten

Diese Anleitung ist für Maintainer. Sie nennt die Schritte eines Releases der Bibliothek, so weit sie entschieden und gebaut sind, die beiden geschuldeten Handlungen ohne Sensor (der Freshness-Audit der vendored Baseline und die Beobachtung von detekt) und die Entscheidungen, die der Nutzer noch nicht getroffen hat. Sie erfindet nichts: Wo etwas fehlt, steht es unter [Offene Entscheidungen](#offene-entscheidungen). Bezug: [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-RB-05](../../spec/lastenheft.md#tds-rb-05--lizenz), [ADR-0010](../plan/adr/0010-detekt-pre-release.md), [ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md), [ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md), [ADR-0049](../plan/adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md).

Das Produkt ist das Bibliotheks-Jar `tdswire.jar` mit seinem Verzeichnis `lib/`. Ein Produkt-Image gibt es nicht ([ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md)). Daneben ist die Bibliothek ein Maven-Paket (Koordinaten `io.github.pt9912:tdswire`, Jar und POM) auf zwei Zielen: GitHub Packages und Cloudsmith ([ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md), [ADR-0049](../plan/adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md)). Die Veröffentlichung läuft über die CI (Schritt 9). Welche Versionen auf welchem Ziel liegen und was dabei gemessen wurde, steht in den Messprotokollen unter [`docs/messungen/`](../messungen/veroeffentlichung-0-1-1-2026-11-15.md), nicht hier. Wer die Bibliothek einbettet, liest die [Nutzer-Anleitung](../user/einbettung-und-betrieb.md).

## Schritte

1. **Gates.** `make gates` im Wurzelverzeichnis muss grün sein. Es fährt unter anderem `make test`, `make detekt`, `make build`, `make docs-check` und `make baseline-verify` (Klassen und Stempel: [`harness/README.md`](../../harness/README.md) §Sensors). Das CI-Gegenstück ist `.github/workflows/gates.yml`; als Setzung dieser Anleitung (nicht entschieden) gilt der Lauf auf dem Release-Stand von `main` nur bei `success` (Abfrage `gh run list --workflow gates`). Das bleibt der Nachweis vor dem Tag. Der Job `gates` in `.github/workflows/veroeffentlichung.yml` führt `make gates` beim Tag-Push noch einmal und hält `publish` zurück, solange er rot ist; er ersetzt den Nachweis nicht, er sichert ihn ab ([ADR-0048](../plan/adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Entscheidung 2).
2. **E2E-Durchgang.** `make e2e` ist kein Gate ([ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md)). Der Nutzer hat am 2026-11-14 entschieden, dass er ein Schritt dieser Anleitung ist; er bleibt kein Gate. Ergebnis und Dauer stehen im Release-Vermerk (Schritt 8). Was der Durchgang umfasst und welche Dauer gemessen ist, steht in der Zeile `make e2e` von [`harness/README.md`](../../harness/README.md) §Sensors.
3. **Freshness-Audit der vendored Baseline.** Siehe [Freshness-Audit](#freshness-audit-der-vendored-baseline).
4. **Beobachtung von detekt.** Siehe [Wächter für ADR-0010](#wächter-für-adr-0010-stabil-release-von-detekt-2x).
5. **Version.** Im Quelltext steht `version = "<Zähler>-SNAPSHOT"` in `build.gradle.kts`. Der Release-Commit setzt die Version ohne `-SNAPSHOT`; ein Folge-Commit setzt `-SNAPSHOT` zurück, der Zähler bleibt der der veröffentlichten Version, die nächste Version wählt der Nutzer beim nächsten Release-Commit. Das Schema ist `0.y.z` ohne Zusatz (`y` steigt bei jeder Änderung der öffentlichen Fläche nach `SPEC-059`, `z` sonst; `1.0.0` mit der ersten Zusage der semantischen Versionierung; [ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md) Entscheidung 3). Ein `-SNAPSHOT` wird nicht veröffentlicht, und eine veröffentlichte Version wird nie neu veröffentlicht (Korrektur = neue Version). Ob eine Änderung den Zähler `y` hebt, ist Handarbeit: ein Werkzeug für Binärkompatibilität gibt es bis `1.0` nicht.
6. **Artefakt.** `make build` baut in Docker (Stage `build-jar`) das Jar `tdswire.jar` ohne `Main-Class` und prüft: das Jar führt `META-INF/LICENSE`, gleich der Datei `LICENSE`, das Manifest nennt keine `Main-Class`, der `Class-Path` führt nur vorhandene Einträge `lib/<Name>`, in `lib/` liegt kein Jackson und im Jar keine Klasse des Testservers (`tools/manifest-pruefung.sh`, `SPEC-052`). Dieselbe Prüfung liest die POM des Maven-Pakets (`build/publications/maven/pom-default.xml`, erzeugt ohne Netz und ohne Token): sie nennt weder Jackson noch den Testserver und führt die Lizenz MIT mit URL. `make publish-vorschau` baut Jar und POM im Container in ein lokales Maven-Verzeichnis des Containers und gibt die POM aus; es schreibt nichts nach außen. **`make build` schreibt das Jar nicht auf den Host.** Einen Weg, Jar und `lib/` ohne Registry herauszugeben, gibt es nicht, siehe [Offene Entscheidungen](#offene-entscheidungen).
7. **Lizenz.** Die Datei `LICENSE` im Wurzelverzeichnis ist die MIT-Lizenz (Rechteinhaber `pt9912`, Jahr 2026); der Bau (Schritt 6) und der Test `LizenzTest` halten sie gegen das Jar und gegen den Text. Hinweise zu den Lizenzen der Bibliotheken in `lib/` führt das Artefakt nicht; ob es sie braucht, ist eine offene Rechtsfrage ([ADR-0018](../plan/adr/0018-laufzeit-artefakt.md), Abschnitt „Offen“), und dieser Ablauf behauptet dazu nichts.
8. **Tag und Vermerk.** Der Tag heißt `v<Version>` (zum Beispiel `v0.1.1`) und liegt auf dem Release-Commit aus Schritt 5. **Der Push eines Tags `v*` löst die Veröffentlichung aus** (Schritt 9): der Tag steht nur auf einem Commit, der in `main` liegt, und der Schutz der Tags ([Tag-Schutz `v*`](#tag-schutz-v)) steht vorher. Ein Tag auf einem anderen Commit veröffentlicht nichts, die Jobs `publish` und `publish-cloudsmith` brechen in der Prüfung `git merge-base --is-ancestor HEAD origin/main`. Die Prüfung steht in der Datei, die der Tag-Push selbst mitbringt; die tragende Kontrolle ist der Tag-Schutz, nicht die Prüfung. Der Vermerk (Ergebnis von Schritt 1 bis 4 samt Datum, dazu die Dauer von `make e2e`) gehört in die Beschreibung des Release-Commits oder des Tags (Setzung dieser Anleitung, nicht entschieden).
9. **Veröffentlichen.** Den Tag pushen (Schritt 8); den Rest tut die CI. Der Workflow `.github/workflows/veroeffentlichung.yml` führt beim Tag-Push `v*` den Job `gates` (`make gates`) und danach, nur bei Erfolg, `make publish` (Job `publish`, GitHub Packages) und `make publish-cloudsmith` (Job `publish-cloudsmith`, Cloudsmith). Rechte, Secrets und Teilfehler: [Veröffentlichen in der CI](#veröffentlichen-in-der-ci). Eine veröffentlichte Version wird nicht wiederholt, Korrektur ist eine neue Version.

Nicht Teil dieses Ablaufs: Signierung und `sources`- oder `javadoc`-Jar (keine, [ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md) Entscheidung 4), Veröffentlichung von Testserver und Test-Image (keine), Software-Stückliste und ein Weg für Jar und `lib/` ohne Registry (nicht gebaut, nicht entschieden; die Neubewertung ist fällig, siehe [Offene Entscheidungen](#offene-entscheidungen)).

## Veröffentlichen in der CI

**Jobs und Rechte.** `publish` und `publish-cloudsmith` hängen je an `gates` (`needs: gates`) und nicht aneinander ([ADR-0049](../plan/adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) Entscheidungen 2 und 3). Das Recht `packages: write` hat nur `publish`; `GITHUB_TOKEN` stammt aus `secrets.GITHUB_TOKEN` und steht nur im Schritt `make publish`, ein persönliches Token ist nicht nötig ([ADR-0048](../plan/adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md)). Die Secrets `CLOUDSMITH_USERNAME` und `CLOUDSMITH_API_KEY` stehen nur im Schritt `make publish-cloudsmith`. Anlegen und Ändern der Secrets ist Handlung des Nutzers. Ein Lauf per `workflow_dispatch` führt `gates` und `make publish-vorschau` aus und veröffentlicht nie.

**Teilfehler** (genau ein Veröffentlichungs-Job rot, der andere grün):

1. Prüfen, ob das rote Ziel die Version trägt. GitHub Packages: Versionen der Paketseite oder der API. Cloudsmith: anonymer Abruf der POM, rund eineinhalb Minuten nach dem Upload (die Verarbeitung dauerte 92 s).
2. Trägt es sie nicht und ist die Ursache behoben, den roten Job allein wiederholen („Re-run failed jobs“). `gates` und `publish` laufen dabei nicht neu, die Version bleibt gleich.
3. Die Ausgabe jedes Versuchs vor dem nächsten sichern.
4. Bleibt ein Teilbestand, entscheidet der Nutzer. Ob eine Löschung bei Cloudsmith die Version frei macht, ist ungemessen.

Ein falscher Wert im Secret `CLOUDSMITH_USERNAME` zeigt sich im Protokoll nur als `Received status code 401 from server: Unauthorized`. Die richtige Form des Benutzernamens steht hier nicht, weil sie nicht gelesen wurde.

**Von Hand.** `make publish` und `make publish-cloudsmith` rufen `tools/publish.sh` mit `--ziel github` (Voreinstellung) und `--ziel cloudsmith`. Jedes Ziel hat eigene Zugangsdaten (`GITHUB_ACTOR`, `GITHUB_TOKEN` und `CLOUDSMITH_USERNAME`, `CLOUDSMITH_API_KEY`), eine eigene Gradle-Aufgabe und ein Standardziel (`https://maven.pkg.github.com/pt9912/tdswire`, `https://maven.cloudsmith.io/pt9912/tdswire/`). Das Skript bricht vor Docker, wenn die Version auf `-SNAPSHOT` endet oder nicht `<zahl>.<zahl>.<zahl>` ist, der Tag `v<Version>` nicht auf `HEAD` liegt, der Baum nicht sauber ist oder Zugangsdaten leer sind. Dass `make gates` auf dem Release-Commit grün ist (Schritt 1), prüft es nicht. Ein Token steht nie in einer Datei, einem Argument oder einem Log. Ein anderes Ziel als das Standardziel (`--url`, für Belegläufe) verlangt einen Wert mit Präfix `PLATZHALTER-`; ein direkter `gradle publish` im Container (`build.gradle.kts` erlaubt `http://`) und ein von Hand so genanntes echtes Token umgehen die Prüfung. Die Targets sind kein Gate und nicht Teil von `make e2e`.

**Ungemessen** und deshalb nicht zugesagt: der Konsum aus GitHub Packages mit Lese-Token, die Dateiliste der Versionen dort, der erste anonyme Abruf bei Cloudsmith im Sammelfenster nach dem Upload, der Bruch der Prüfung `git merge-base` bei einem Tag außerhalb von `main`, die Wirkung der Einstellung „Workflow permissions“ des Repositories, die Wirkung von `concurrency` bei zwei Läufen desselben Tags und das Verhalten eines Tag-Push ohne Bypass. Gemessenes steht in den Messprotokollen: [Dispatch-Lauf](../messungen/veroeffentlichung-dispatch-2026-11-15.md), [Tag-Lauf `0.1.0`](../messungen/veroeffentlichung-tag-lauf-2026-11-15.md), [Tag-Lauf `0.1.1`](../messungen/veroeffentlichung-0-1-1-2026-11-15.md).

## Tag-Schutz `v*`

Wer einen Tag `v*` auf `main` pushen darf, löst eine Veröffentlichung aus ([ADR-0048](../plan/adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Konsequenzen); der Job `publish-cloudsmith` liest dabei die Repository-Secrets `CLOUDSMITH_USERNAME` und `CLOUDSMITH_API_KEY`, langlebige Schlüssel ([ADR-0049](../plan/adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) Entscheidung 3a), und wer den Workflow auf einem Tag ändern und den Tag auf `main` pushen kann, kann sie auslesen; der Tag-Schutz ist die tragende Kontrolle. Den Schutz setzt der Maintainer **vor dem ersten Tag** in den Einstellungen des Repositories bei GitHub; kein Gate und kein Sensor prüft ihn, und der Workflow setzt ihn nicht.

**Quelle des Wortlauts:** GitHub-Dokumentation „Creating rulesets for a repository“ und „Available rules for rulesets“, am 2026-11-14 abgerufen. Die Oberfläche kann sich ändern; die Bezeichnungen unten sind der Stand dieses Abrufs, nicht gemessen am Repository dieses Projekts, und die Einstellung selbst ist hier nicht geprüft.

1. Im Repository **Settings** öffnen; links unter „Code, planning, and automation“ **Rulesets**, dann **New ruleset**.
2. **New tag ruleset** wählen und einen Namen vergeben (zum Beispiel `tags-v`).
3. Den Zustand „Enforcement status“ auf aktiv stellen; die Voreinstellung ist „Disabled“, ein deaktivierter Satz schützt nichts.
4. Unter „Bypass list“ nur die Rolle oder Person eintragen, die Tags `v*` setzen darf (zugelassen sind unter anderem Repository-Admins, die Rollen `maintain` und `write`, Teams und GitHub-Apps).
5. Unter „Target tags“ ein Ziel hinzufügen und das Muster `v*` angeben (fnmatch-Syntax).
6. Unter „Tag protections“ die Regeln **Restrict creations**, **Restrict updates** und **Restrict deletions** wählen: dann darf nur, wer in der Bypass-Liste steht, einen passenden Tag anlegen, verschieben oder löschen (laut Dokumentation ist „Restrict deletions“ vorgewählt).
7. Speichern mit **Create**.

Gemessen ist der Push eines Tags `v*` durch den Nutzer als Bypass-Berechtigten ([Messprotokoll](../messungen/veroeffentlichung-tag-lauf-2026-11-15.md)). Ungemessen sind das Verhalten ohne Bypass und die Wirkung der Workflow-Berechtigungen des Repositories (Settings, Actions, General, „Workflow permissions“) auf die Rechte `permissions` der Datei.

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

**Grenze.** Der Schritt hat keinen Auslöser und kein Gate: wer ihn vergisst, bemerkt den Trigger nur zufällig. Der Nutzer hat am 2026-11-14 entschieden, dass es beim Schritt in dieser Anleitung bleibt: ein Werkzeug-Target, das die Abfrage in Docker fährt und meldet, besteht nicht und ist nicht vorgesehen.

## Offene Entscheidungen

Diese Punkte hat der Nutzer nicht entschieden; die Anleitung setzt keinen davon.

- Lizenzhinweise zu den Bibliotheken in `lib/` (Rechtsfrage) und Dateirechte der Benutzerliste im Betrieb ([ADR-0018](../plan/adr/0018-laufzeit-artefakt.md), Abschnitt „Offen“).

Zurückgestellt: Software-Stückliste und ein Weg für Jar und `lib/` ohne Registry sind nicht gebaut und nicht entschieden. Die Neubewertung ist nach der ersten Veröffentlichung fällig (Nutzer, 2026-11-14) und Sache des Nutzers; sie hat keinen Sensor.
