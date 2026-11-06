# Ein Release vorbereiten

Diese Anleitung ist für Maintainer. Sie nennt die Schritte eines Releases der Bibliothek, so weit sie entschieden und gebaut sind, die beiden geschuldeten Handlungen ohne Sensor (der Freshness-Audit der vendored Baseline und die Beobachtung von detekt) und die Entscheidungen, die der Nutzer noch nicht getroffen hat. Sie erfindet nichts: Wo etwas fehlt, steht es unter [Offene Entscheidungen](#offene-entscheidungen). Bezug: [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-RB-05](../../spec/lastenheft.md#tds-rb-05--lizenz), [ADR-0010](../plan/adr/0010-detekt-pre-release.md), [ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md).

Das Produkt ist das Bibliotheks-Jar `tdswire.jar` mit seinem Verzeichnis `lib/`. Ein Produkt-Image und einen Registry-Weg gibt es nicht ([ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md)). Wer die Bibliothek einbettet, liest die [Nutzer-Anleitung](../user/einbettung-und-betrieb.md).

## Schritte

1. **Gates.** `make gates` im Wurzelverzeichnis muss grün sein. Es fährt unter anderem `make test`, `make detekt`, `make build`, `make docs-check` und `make baseline-verify` (Klassen und Stempel: [`harness/README.md`](../../harness/README.md) §Sensors). Das CI-Gegenstück ist `.github/workflows/gates.yml`; der Lauf auf dem Release-Stand von `main` muss `success` sein (Abfrage `gh run list --workflow gates`).
2. **E2E-Durchgang.** `make e2e` ist kein Gate ([ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md)). **Ob ein Release ihn verlangt, ist nicht entschieden**; wer ihn laufen lässt, vermerkt Ergebnis und Dauer im Release-Vermerk (Schritt 8).
3. **Freshness-Audit der vendored Baseline.** Siehe [Freshness-Audit](#freshness-audit-der-vendored-baseline).
4. **Beobachtung von detekt.** Siehe [Wächter für ADR-0010](#wächter-für-adr-0010-stabil-release-von-detekt-2x).
5. **Version.** Der Stand im Quelltext ist `version = "0.1.0-SNAPSHOT"` in `build.gradle.kts`. **Das Versionsschema des Releases ist nicht entschieden** (siehe unten). Die Spezifikation sagt nur: bis zur Version `1.0` ändert sich die öffentliche Fläche durch eine Änderung der Spezifikation, ab `1.0` gilt semantische Versionierung (`SPEC-059`, Setzung).
6. **Artefakt.** `make build` baut in Docker (Stage `build-jar`) das Jar `tdswire.jar` ohne `Main-Class` und prüft: das Jar führt `META-INF/LICENSE`, gleich der Datei `LICENSE`, das Manifest nennt keine `Main-Class`, der `Class-Path` führt nur vorhandene Einträge `lib/<Name>`, in `lib/` liegt kein Jackson und im Jar keine Klasse des Testservers (`tools/manifest-pruefung.sh`, `SPEC-052`). **Das Target schreibt das Jar nicht auf den Host.** Ein Weg, Jar und `lib/` aus dem Bau herauszugeben, besteht im Repo nicht; er ist offen.
7. **Lizenz.** Die Datei `LICENSE` im Wurzelverzeichnis ist die MIT-Lizenz (Rechteinhaber `pt9912`, Jahr 2026); der Bau (Schritt 6) und der Test `LizenzTest` halten sie gegen das Jar und gegen den Text. Hinweise zu den Lizenzen der Bibliotheken in `lib/` führt das Artefakt nicht; ob es sie braucht, ist eine offene Rechtsfrage ([ADR-0018](../plan/adr/0018-laufzeit-artefakt.md), Abschnitt „Offen“), und dieser Ablauf behauptet dazu nichts.
8. **Tag und Vermerk.** Ob und in welcher Form ein Tag gesetzt wird, ist nicht entschieden; im Repo besteht heute kein Tag. Der Vermerk (Ergebnis von Schritt 1 bis 4 samt Datum) gehört in die Beschreibung des Release-Commits oder des Tags, sobald der Nutzer die Form wählt (Setzung dieser Anleitung, nicht entschieden).

Nicht Teil dieses Ablaufs, weil nicht gebaut und nicht entschieden: Veröffentlichung in einer Registry, Signierung, Software-Stückliste.

## Freshness-Audit der vendored Baseline

`make baseline-verify` hält den Baum unter `.harness/baseline/` gegen `SHA256SUMS`; das ist die Integritätshälfte und sagt über das Alter nichts ([`harness/conventions.md`](../../harness/conventions.md) §Baseline). Das Alter prüft diese Handlung, kein Sensor: Die Gates laufen netzlos, und ein Sensor dafür *kommt nicht mit*.

1. Den gepinnten Tag lesen: das Verzeichnis unter `.harness/baseline/` (Stand dieser Anleitung `v6.18.1`; `make baseline-verify` nennt ihn in der Ausgabe).
2. Die **Release-Liste** des Kurs-Repos ansehen, nicht das Asset des Tags: `gh release list -R pt9912/ai-harness-course -L 5` oder die Release-Übersicht <https://github.com/pt9912/ai-harness-course/releases>. Ein Abgleich des Assets zeigt, ob sich dieser Tag änderte, nicht, ob ein neuerer existiert.
3. Steht ein neuerer Tag in der Liste, ist der Pin zu heben (eigener Slice: Baseline tauschen, die Tabelle der Regelblöcke in `harness/conventions.md` von Hand gegen den neuen Baum prüfen, jede aktive Adaption `MR-*` gegen die neue Fassung lesen). Steht keiner, ist der Pin aktuell.
4. Das Ergebnis mit Datum und dem Tag, der als jüngster erschien, im Release-Vermerk festhalten (Schritt 8).

**Erster Lauf, 2026-11-06:** `gh release list -R pt9912/ai-harness-course -L 1 --json tagName,isLatest` lieferte `v6.18.1` als `Latest`. Der gepinnte Tag `v6.18.1` ist damit der jüngste der Liste; ein Lauf, eine Quelle, Abruf vom Host an diesem Tag. Die Grenze: Der Schritt ist eine Handlung. Wer ihn auslässt, wird von keinem Gate erwischt.

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

- Versionsschema des Releases (Zählweise, Beginn, Umgang mit `-SNAPSHOT`), und ob und wie ein Tag gesetzt wird.
- Ob und wo das Artefakt veröffentlicht wird (Registry), ob es signiert wird, ob eine Software-Stückliste entsteht.
- Wie Jar und `lib/` aus dem Bau herausgegeben werden (heute nur im Bau geprüft, nicht abgelegt).
- Ob `make e2e` Teil eines Releases ist.
- Ob der Wächter für detekt zusätzlich ein Werkzeug-Target bekommt.
- Lizenzhinweise zu den Bibliotheken in `lib/` (Rechtsfrage) und Dateirechte der Benutzerliste im Betrieb ([ADR-0018](../plan/adr/0018-laufzeit-artefakt.md), Abschnitt „Offen“).
