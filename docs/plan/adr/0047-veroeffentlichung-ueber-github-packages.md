# ADR-0047: Veröffentlichung der Bibliothek über GitHub Packages (Maven-Registry): Koordinaten, POM statt `lib/`, Versionsschema, Zugriff, Schreib-Token

**Status:** Accepted

**Datum:** 2026-11-13

**Autor:** pt9912

**Bezug:** [TDS-RB-01](../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-RB-05](../../../spec/lastenheft.md#tds-rb-05--lizenz), [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md), [ADR-0021](0021-oeffentlicher-integrations-einstieg.md), [ADR-0018](0018-laufzeit-artefakt.md), [ADR-0022](0022-e2e-laeufe-nicht-in-den-gates.md), [ADR-0024](0024-lokale-stempel-zwei-klassen.md), [ADR-0034](0034-testserver-als-gradle-modul.md)

**Schärft:** [SPEC-052](../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge) (Bibliotheks-Artefakt: eine zweite, veröffentlichte Form neben Jar und `lib/`), [SPEC-059](../../../spec/spezifikation.md#einstieg-des-integrators-vertrag-zu-spec-059) (Zeile „Abhängigkeiten“ und „Zusage“: Versionsschema ab der ersten Veröffentlichung). Nachzug in der Spezifikation erst nach `Accepted` (Folgepflicht).

**Supersedes (teilweise), jeweils nur wörtlich benannt, wirksam erst mit dem Status `Accepted`; die Dateien der Vorgänger bleiben unverändert und `Accepted` ([AGENTS.md](../../../AGENTS.md) §3.5):**

- [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md) Entscheidung 3, **nur** der Halbsatz „der `Class-Path` auf `lib/` bleibt als Komfort für Embedder nach [ADR-0021](0021-oeffentlicher-integrations-einstieg.md) (Setzung: kleinste Änderung, `lib/` enthält ohne Jackson nur noch, was die Bibliothek braucht)“ — **wirksam nur, wenn der Nutzer bei Entscheidung 2 die Option 2b oder 2c wählt.** Bei der Empfehlung (Option 2a) ersetzt diese ADR keinen Satz von [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md).
- Sonst nichts. Der Grund steht unter „Geprüft und nicht ersetzt“ im Kontext: Eine Aussage „kein Registry-Weg“ steht **nicht** in [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md) und nicht in [ADR-0018](0018-laufzeit-artefakt.md); ein Satz, den man wörtlich ersetzen könnte, fehlt dort.

**Regeln:** Baseline-Regelwerk `modul-04-adrs.md` §Ziel-Form: ADR (MADR).

---

## Kontext

**Nutzerwunsch (2026-11-13, Auftrag an den Architect):** Die Bibliothek (Jar `tdswire.jar`, ohne Jackson, ohne Testserver) soll über GitHub Packages als Maven-Paket veröffentlicht werden, Repository `pt9912/tdswire`, Lizenz MIT. Diese ADR legt je Entscheidung Optionen mit Empfehlung vor. **Alle Empfehlungen sind unbestätigt; entschieden ist erst mit der Annahme durch den Nutzer.**

**Was Beleg ist und was Setzung** ([AGENTS.md](../../../AGENTS.md) §3.8): *Gelesen* im Repo am 2026-11-13 (nichts ausgeführt): `build.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml`, Stage `build-jar` im `Dockerfile`, `tools/manifest-pruefung.sh`, `docs/maintainer/releasing.md`, `docs/user/einbettung-und-betrieb.md`. *Gelesen in der GitHub-Dokumentation am 2026-11-13* (Abruf der Seiten, keine Probe gegen die Registry): [Apache Maven registry](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry), [Gradle registry](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-gradle-registry), [About permissions for GitHub Packages](https://docs.github.com/en/packages/learn-github-packages/about-permissions-for-github-packages), und für die Gegenoption die [Central-Anforderungen](https://central.sonatype.org/publish/requirements/). **Nicht gemessen:** jede Aussage über das Verhalten der Registry, eines Konsumenten oder von `maven-publish` an diesem Projekt. Was als Setzung gilt, steht als solche da.

**Ist-Zustand im Repo (gelesen):**

- `build.gradle.kts`: `group = "io.github.pt9912"`, `version = "0.1.0-SNAPSHOT"`; `settings.gradle.kts`: `rootProject.name = "tdswire"`; das Projekt `:testserver` ist ein eigenes Modul ([ADR-0034](0034-testserver-als-gradle-modul.md)). Ein Plugin `maven-publish` ist nicht angewendet. Kein Tag im Repo ([`releasing.md`](../../maintainer/releasing.md) Schritt 8).
- `tasks.jar` setzt `archiveFileName` auf `tdswire.jar`, legt `LICENSE` nach `META-INF` und schreibt einen `Class-Path` aus `lib/<Name>` für jede Datei des `runtimeClasspath`; die Task `laufzeitBibliotheken` kopiert diese Dateien nach `build/libs/lib`. `tools/manifest-pruefung.sh` (Stage `build-jar`, in `make build`) verlangt einen nicht leeren `Class-Path` mit Einträgen `lib/<Name>`, keine `Main-Class`, keine Testserver-Klasse im Jar und kein Jackson in `lib/`.
- Abhängigkeiten der Bibliothek im Quelltext: `kotlinx-coroutines-core` als `implementation`; `lib/` führte am 2026-11-07 `annotations-23.0.0`, `kotlin-stdlib-2.4.10` und `kotlinx-coroutines-core-jvm-1.10.1` (so beschrieben in der [Nutzer-Anleitung](../../user/einbettung-und-betrieb.md), „gefahren“). Dass `kotlinx-coroutines` im öffentlichen Vertrag steckt (`suspend`, `Flow`), steht in [ADR-0021](0021-oeffentlicher-integrations-einstieg.md).
- Der Gradle-Wrapper-Ersatz ist das Image `gradle:8.14-jdk21` (Digest im `Dockerfile`); `maven-publish` ist ein Plugin dieser Gradle-Distribution (Setzung: kein neuer Eintrag in Lock oder Prüfsummen; im Bau zu belegen).

**Wo die „Kein Registry-Weg“-Aussage tatsächlich steht (gelesen):**

| Stelle | Wortlaut / Wirkung | Art |
|---|---|---|
| [`releasing.md`](../../maintainer/releasing.md) Kopf, „Nicht Teil dieses Ablaufs“, „Offene Entscheidungen“ | „Ein Produkt-Image und einen Registry-Weg gibt es nicht ([ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md))“; Veröffentlichung, Signierung, Versionsschema, Tag offen | Maintainer-Doku (Rang 6), **keine Entscheidung**; der Verweis auf [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md) belegt die Aussage nicht |
| [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md) Re-Evaluierungs-Trigger | „der Wunsch, die Bibliothek in einem Artefakt-Repository zu veröffentlichen ([ADR-0021](0021-oeffentlicher-integrations-einstieg.md), Option b2: dann gilt der Klassenpfad ohne Jackson als Voraussetzung und ist zu belegen)“ | Trigger, keine Entscheidung. **Er ist mit dem Wunsch vom 2026-11-13 eingetreten.** |
| [ADR-0021](0021-oeffentlicher-integrations-einstieg.md) Entscheidung 2 (b1): „Das Jar wird nicht in ein Repository veröffentlicht, solange das Lastenheft es nicht verlangt“ | war die einzige Entscheidung gegen die Veröffentlichung | von [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md) mit „Entscheidung 2 (b1) ganz“ bereits ersetzt; wirkt nicht mehr |
| [ADR-0018](0018-laufzeit-artefakt.md) Re-Evaluierungs-Trigger: „Entschluss, ein **Image** zu veröffentlichen (Registry, Signierung)“ | betrifft das Container-Image, das es nicht mehr gibt | Trigger; Entscheidungen 1 bis 4 von [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md) bereits ersetzt |
| Lastenheft | verlangt keine Veröffentlichung und schließt sie nicht aus; ein Abschnitt „Nicht Teil“ besteht dort nicht (gelesen: `TDS-RB-01`, `TDS-RB-05`, `TDS-QA-06`, Zweck) | Zielbild |

**Geprüft und nicht ersetzt (akzeptiertes Negativ, Grund hier, damit die nächste Runde es als entschieden liest):** Der Auftrag nannte [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md) („kein Registry-Weg“) und die betroffenen Teile von [ADR-0018](0018-laufzeit-artefakt.md) als Gegenstand von `Supersedes`. Gelesen ist: [ADR-0018](0018-laufzeit-artefakt.md) betrifft nur das Container-Image; dessen Entscheidungen 1 bis 4 sind seit [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md) ersetzt, ein weiterer Satz wird durch eine Maven-Veröffentlichung nicht berührt. [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md) enthält keinen ausschließenden Satz; der Ausschluss lebt in [`releasing.md`](../../maintainer/releasing.md), und diese Datei zieht der Folge-Slice nach (Konsequenzen). Verdikt in der Reihenfolge des Konflikt-Pfads: Die Entscheidungslage trägt den Wunsch, soweit die Voraussetzung des Triggers (Klassenpfad ohne Jackson) belegt wird (Entscheidung 2); die Aussage in `releasing.md` war breiter als ihr Beleg.

**Offener Punkt aus [ADR-0018](0018-laufzeit-artefakt.md), jetzt mit Lesart (keine Rechtsberatung):** Die Lizenzhinweise der Bibliotheken (Rechtsfrage, im Repo ungeprüft) betrafen ein Artefakt, das fremde Jars mitliefert. Bei der Maven-Form (Entscheidung 2a) enthält das veröffentlichte Paket keine fremden Jars, nur Abhängigkeitsangaben in der POM; die Frage bleibt für den Handweg mit `lib/` bestehen. Entschieden wird hier nichts.

**Annahme, die kippt:** dass Embedder ein Token für `read:packages` haben oder bereit sind, eines anzulegen (Entscheidung 5). Kippt sie, tragen Gegenoptionen oder der Trigger.

## Entscheidung

Vorgeschlagen (nicht entschieden): Die Bibliothek wird als **ein** Maven-Paket aus dem Wurzelprojekt über GitHub Packages veröffentlicht, mit den Empfehlungen der Tabellen unten. Der Testserver (`:testserver`) und das Test-Image werden **nicht** veröffentlicht (Setzung: Das Modul bekommt kein `maven-publish`; [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md), [ADR-0034](0034-testserver-als-gradle-modul.md)). Das Veröffentlichen ist ein **Werkzeug-Target außerhalb von `make gates`**, nie Teil der Gate-Kette (Entscheidung 6). Je Entscheidung eine Tabelle; **fett** ist die Empfehlung.

### 1. Koordinaten

GitHub-Dokumentation (gelesen): Ein Paket heißt `groupId:artifactId` und wird, wenn `distributionManagement` fehlt, in das Repository mit dem Namen des Pakets veröffentlicht; mit der URL des Repositories in `distributionManagement` ordnet GitHub das Paket diesem Repository zu (`https://maven.pkg.github.com/OWNER/REPOSITORY`, hier `https://maven.pkg.github.com/pt9912/tdswire`). Die `groupId` ist damit vom Repositorynamen unabhängig. `artifactId` darf nur Kleinbuchstaben, Ziffern und Bindestriche tragen, sonst antwortet die Registry mit 422 (gelesen). Ein Paket, das mit einem Repository verbunden ist, erbt dessen Berechtigungen (gelesen, Seite „About permissions“).

| Option | Pro | Contra |
|---|---|---|
| **1a — `io.github.pt9912:tdswire` (Stand im Quelltext)** | steht schon in `build.gradle.kts` und `rootProject.name`; keine Änderung; passt zum GitHub-Konto | die `groupId` ist an das GitHub-Konto gebunden; ein Umzug später ist ein Koordinatenwechsel für Konsumenten |
| 1b — `io.github.pt9912:tdswire-core` | hält Namen für spätere Module frei | es gibt kein zweites veröffentlichtes Modul; Umbenennung ohne Beleg für den Bedarf; ändert `rootProject.name` oder erzwingt eine Abweichung vom Projektnamen |
| 1c — eigene Domäne als `groupId` | unabhängig vom GitHub-Konto | im Repo ist keine Domäne belegt; ich erfinde keine |

Empfehlung **1a**; Setzung, nicht belegt: dass `io.github.pt9912` später für Maven Central taugt, ist nicht geprüft (Namensraum-Prüfung des Portals nicht gelesen).

### 2. Abbildung von Jar und `lib/` auf eine POM mit Abhängigkeiten

Der Gegenstand: Das veröffentlichte Paket führt `tdswire.jar` und eine POM; die Bibliotheken von `lib/` werden zu Abhängigkeitsangaben der POM (Gradle `maven-publish`, `from(components["java"])`, wie im gelesenen Beispiel der GitHub-Seite zur Gradle-Registry). Offen ist, was aus `Class-Path`/`lib/` wird.

| Option | Pro | Contra |
|---|---|---|
| **2a — dasselbe `tdswire.jar` (mit `Class-Path`) wird veröffentlicht; `lib/` bleibt der Handweg ohne Registry, die POM führt die Abhängigkeiten** | ein Jar, eine Quelle der Wahrheit; die Manifest-Prüfung bleibt wie sie ist; kein Satz von [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md) wird ersetzt | der `Class-Path` im Registry-Jar nennt Dateien, die dort nicht liegen. Nach Lesart der JVM-Jar-Spezifikation sind nicht auflösbare Einträge wirkungslos (**nicht gemessen**, im Folge-Slice einmal zu belegen) |
| 2b — zwei Jars: Registry-Jar ohne `Class-Path`, Handweg-Jar mit | sauberes Registry-Artefakt | zwei Jars aus einem Bau, zwei Wahrheiten, zweite Prüfung; die Manifest-Prüfung braucht zwei Modi; ersetzt den Halbsatz von [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md) |
| 2c — `Class-Path` und `lib/` entfallen ganz; der Handweg ist die Registry | ein Artefakt, keine Doppelung; `lib/` mit Lizenzfrage entfällt | die Nutzer-Anleitung verliert den Handweg (`docker cp`); Manifest-Prüfung wird umgekehrt (kein `Class-Path`); [SPEC-052](../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge) und [`einbettung-und-betrieb.md`](../../user/einbettung-und-betrieb.md) ändern sich; ersetzt den Halbsatz von [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md) |

Empfehlung **2a**, weil sie den geringsten Zusatzaufwand hat und trägt, ohne eine bestehende Entscheidung zu ersetzen; 2c ist der Endzustand, wenn die Registry der Hauptweg geworden ist (Trigger).

**Was zur Voraussetzung des Triggers von [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md) gehört („Klassenpfad ohne Jackson … zu belegen“), unabhängig von der Wahl:**

- **Aussage „lib/ ohne Jackson“ wird zur Aussage „Klassenpfad ohne Jackson, in beiden Formen“.** Belegt ist heute nur die Form `lib/` (Prüfung im Bau) und, einmalig gezählt am 2026-11-06, dass `gradle.lockfile` kein Jackson führt ([SPEC-059](../../../spec/spezifikation.md#einstieg-des-integrators-vertrag-zu-spec-059), „kein Gate hält sie“). Für die POM ist nichts belegt.
- **Empfehlung für den Beleg:** Der Bau erzeugt die POM (`generatePomFileForMavenPublication`, ohne Netz und ohne Token) und die Manifest-Prüfung liest sie: kein `jackson`, Lizenz MIT vorhanden, keine Abhängigkeit auf `testserver`. Dazu eine Mutation im Selbsttest der Prüfung. Das liegt in `make build` (Klasse `tests` nach [ADR-0024](0024-lokale-stempel-zwei-klassen.md)) und ist der Anteil dieser ADR am Gate; das Veröffentlichen selbst bleibt außerhalb.
- **Setzung zum Scope (aus [ADR-0021](0021-oeffentlicher-integrations-einstieg.md) abgeleitet, nicht gemessen):** `kotlinx-coroutines-core` steckt im öffentlichen Vertrag, steht aber als `implementation`; Gradle schreibt das als Laufzeit-Abhängigkeit in die POM, ein Konsument, der gegen `Flow` übersetzt, braucht sie zur Übersetzungszeit. Der Folge-Slice prüft, ob `api` nötig ist, und misst die Wirkung auf `gradle.lockfile`.

### 3. Versionsschema und Tag-Form

[SPEC-059](../../../spec/spezifikation.md#einstieg-des-integrators-vertrag-zu-spec-059) (Setzung): bis `1.0` ändert sich die öffentliche Fläche nur durch eine Änderung der Spezifikation, ab `1.0` gilt semantische Versionierung. Ein Schema für die Zählung vor `1.0` besteht nicht ([`releasing.md`](../../maintainer/releasing.md), offen). Gelesen in der GitHub-Dokumentation: SNAPSHOT-Versionen sind in dieser Registry möglich (der Konsument muss sie einschalten). Nicht geprüft: ob GitHub Packages das erneute Veröffentlichen einer Version ablehnt.

| Option | Pro | Contra |
|---|---|---|
| **3a — `0.y.z` ohne Zusatz; `y` steigt bei jeder Änderung der öffentlichen Fläche nach [SPEC-059](../../../spec/spezifikation.md#einstieg-des-integrators-vertrag-zu-spec-059), `z` sonst; `1.0.0` mit der ersten SemVer-Zusage. Die Version steht in `build.gradle.kts`: der Release-Commit setzt sie ohne `-SNAPSHOT`, der Tag heißt `v<Version>` (`v0.1.0`) und liegt auf diesem Commit; danach wieder `-SNAPSHOT`. Veröffentlicht wird nie ein `-SNAPSHOT`, und eine veröffentlichte Version wird nie neu veröffentlicht (Korrektur = neue Version; Setzung)** | folgt der Spec; eine Quelle der Version; Tag und Version prüfbar gleich | `y` ist eine Setzung dieser ADR, nicht der Spec; der Zählgrund „Fläche geändert“ ist Handarbeit (kein API-Werkzeug, Entscheidung 8) |
| 3b — die erste Veröffentlichung ist `1.0.0` | sofort SemVer | bindet an die Binärkompatibilität, ohne dass ein Werkzeug sie prüft ([ADR-0021](0021-oeffentlicher-integrations-einstieg.md) c2); die Spec nennt `1.0` als späteren Schritt |
| 3c — `-SNAPSHOT` laufend veröffentlichen | schnelle Konsumenten-Rückmeldung | veränderliche Artefakte widersprechen dem Reproduzierbarkeitsziel von [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit); kein Beleg für den Bedarf |

Das Veröffentlichen-Target (Entscheidung 6) bricht, wenn die Version auf `-SNAPSHOT` endet, der Tag `v<Version>` nicht auf `HEAD` liegt oder der Baum nicht sauber ist (Setzung).

### 4. Signierung

Die gelesene Maven-Seite von GitHub nennt keine Signaturpflicht (nicht Beleg, dass es keine gibt). Für Maven Central gilt laut den Central-Anforderungen (gelesen): jede Datei mit GPG/PGP signiert (`.asc`), dazu `sources`- und `javadoc`-Jar sowie POM-Metadaten.

| Option | Pro | Contra |
|---|---|---|
| **4a — keine Signierung, auch kein `sources`/`javadoc`-Jar** | nichts zu verwalten; kein zweites Geheimnis | Konsumenten können die Herkunft nicht per Signatur prüfen |
| 4b — GPG-Signierung jetzt | Schritt Richtung Central | Schlüsselverwaltung im Docker-only-Lauf (zweites Geheimnis, Schlüssel-Rotation); kein Bedarf bei GitHub Packages belegt |

Empfehlung **4a**; Trigger: Entscheidung für Maven Central (Entscheidung 5). Software-Stückliste: nicht Gegenstand, bleibt offen in [`releasing.md`](../../maintainer/releasing.md).

### 5. Zugriff der Konsumenten, Gegenoptionen

**Belegt (Abruf 2026-11-13, [Apache Maven registry](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry), [About permissions](https://docs.github.com/en/packages/learn-github-packages/about-permissions-for-github-packages)):** Ein Token ist nötig, um private, interne **und öffentliche** Pakete zu veröffentlichen, zu installieren und zu löschen; in den meisten Registries muss man zum Herunterladen auch öffentlicher Pakete mit einem Token authentifizieren, nur die Container-Registry erlaubt anonymen Zugriff auf öffentliche Pakete; GitHub Packages unterstützt dafür nur das *Personal Access Token (classic)* (Hinweis der Seite), Scope `read:packages` zum Installieren, `write:packages` zum Veröffentlichen. **Folge für Konsumenten:** Wer die Bibliothek bindet, braucht ein Konto und ein Token mit `read:packages` und trägt die Registry mit Zugangsdaten in sein Gradle- oder Maven-Setup ein. Das ist die Hauptkosten dieser Wahl und gehört in die Nutzer-Anleitung.

| Option | Pro | Contra |
|---|---|---|
| **5a — GitHub Packages, die Token-Pflicht der Konsumenten ist akzeptiert und dokumentiert** | Auftrag des Nutzers; Rechte erbt vom Repository; kein Zusatzkonto | jeder Konsument braucht ein Token; öffentliche Verwendung ohne Konto nicht möglich |
| 5b — zusätzlich ein GitHub Release mit Jar und `lib/` als Dateien (Spiegel des Handwegs) | Konsument braucht für den Handweg keine Registry-Zugangsdaten (**ob Release-Dateien eines öffentlichen Repositories anonym ladbar sind, ist in diesem Lauf nicht geprüft**) | kein Maven-Auflösen, zweiter Veröffentlichungsweg zu pflegen |
| 5c — Maven Central statt GitHub Packages | übliche Quelle der JVM-Welt, kein Token zum Lesen (Annahme aus allgemeinem Wissen, nicht in diesem Lauf geprüft) | Anforderungen laut gelesener Seite: GPG-Signatur, `sources`- und `javadoc`-Jar, vollständige POM-Metadaten; Namensraum-Prüfung nicht gelesen; mehr Pflege; widerspricht dem Auftrag |

Empfehlung **5a**; 5b ist ein später möglicher Zusatz, 5c der Trigger-Fall (unten).

### 6. Schreib-Token im Docker-only-Lauf

Randbedingungen: Host braucht nur Docker und `make` ([AGENTS.md](../../../AGENTS.md) §3.1); das Token darf in keinem Image-Layer, keiner Datei des Arbeitsbaums, keiner Commit-Datei und keinem Log landen. Das Token ist ein *Personal Access Token (classic)* mit **nur** `write:packages` (Setzung: kleinster Scope aus der gelesenen Tabelle; ob weitere Scopes für ein Repository dieser Sichtbarkeit nötig sind, ist nicht geprüft).

| Option | Pro | Contra |
|---|---|---|
| **6a — `docker run --rm -e GITHUB_ACTOR -e GITHUB_TOKEN` gegen ein Image aus einer neuen Stage `publish` (Jar, Gradle, Abhängigkeiten, kein Token); der Gradle-Aufruf liest `System.getenv`; Werte werden nur *namentlich* durchgereicht, nie als Argument** | Secret nur zur Laufzeit; kein Layer, kein Cache-Effekt auf eine Veröffentlichung; kleine Änderung (Stage, Make-Fragment, `publishing {}`-Block) | im laufenden Container per `docker inspect` und `/proc` sichtbar (derselbe Host-Nutzer; akzeptiert); der Gradle-Lauf darf kein `--info`/`--debug` tragen, ob ein Log das Token nennt, ist nicht gemessen |
| 6b — BuildKit-Secret (`RUN --mount=type=secret`), Veröffentlichen als Build-Schritt | Secret nicht im Layer | eine Veröffentlichung als gecachter Build-Schritt läuft ohne `--no-cache` unter Umständen nicht erneut; Nebenwirkung im Build; Ausgabe im Build-Log |
| 6c — Workflow bei Tag-Push mit `GITHUB_TOKEN` und `permissions: packages: write` | kein persönliches Token auf dem Rechner; GitHub nennt `GITHUB_TOKEN` zum Veröffentlichen von Paketen des Workflow-Repositories (gelesen) | neuer Workflow mit Schreibrecht (`gates.yml` hat heute nur `contents: read` und keine Secrets); SHA-gepinnte Actions, `make workflow-lint`; größer als 6a |

Empfehlung **6a**; 6c als Folge, wenn Veröffentlichungen regelmäßig werden (Trigger).

**Beleg im Folge-Slice (ohne echte Veröffentlichung):** Lauf mit Platzhalter-Token gegen ein Ziel, das nicht GitHub ist (oder gegen `publishToMavenLocal`); danach `docker history --no-trunc` des Images und das Log auf den Platzhalter durchsuchen; leeres Token bricht vor dem Netz.

**Ort und Klasse.** Zwei Targets (Namen legt der Slice fest; Vorschlag): `make publish-vorschau` (baut POM und Jar im Container, `publishToMavenLocal`, kein Token, kein Netz zu GitHub, gibt die POM aus) und `make publish` (Registry, braucht `GITHUB_TOKEN`). Beide **kein Gate**, nicht in `GATE_CHECKS`, nicht in `make e2e`; sie stehen in der Tabelle „Werkzeuge“ von [`harness/README.md`](../../../harness/README.md) §Sensors mit Bindung an diese ADR. Die Klassen von [ADR-0024](0024-lokale-stempel-zwei-klassen.md) (`werkzeug`, `tests`, `immer`) ordnen Gates ein und gelten hier nicht; das Eingangsskript (Prüfung Version, Tag, Baumzustand, Token gesetzt) bekommt einen Selbsttest in `make skript-selbsttest` (Klasse `werkzeug`). Dass das Target außerhalb der Gate-Kette liegt, folgt dem Muster von [ADR-0022](0022-e2e-laeufe-nicht-in-den-gates.md) (Läufe mit eigener Laufzeit und Außenwirkung stehen nicht in `GATE_CHECKS`), mit eigener Begründung: Veröffentlichen schreibt nach außen, ist nicht wiederholbar und gehört nicht in eine Kette, die bei jeder Änderung läuft (Setzung). Setzung (nicht entschieden): Vor `make publish` ist `make gates` auf dem Release-Commit grün ([`releasing.md`](../../maintainer/releasing.md) Schritt 1); das Target prüft das nicht selbst.

### 7. Re-Evaluierungs-Trigger

Siehe Abschnitt unten.

### 8. Prüfwerkzeug für Binärkompatibilität ([ADR-0021](0021-oeffentlicher-integrations-einstieg.md) c2)

[ADR-0021](0021-oeffentlicher-integrations-einstieg.md) hat c2 verworfen mit dem Grund „vor einer ersten Veröffentlichung gibt es keine Version, gegen die zu prüfen wäre“; dieser Grund entfällt mit der ersten Veröffentlichung. Das ist **kein** Satz, den diese ADR ersetzt, sondern die Neubewertung, die der Trigger von [ADR-0021](0021-oeffentlicher-integrations-einstieg.md) verlangt („dann b2 und c2 neu“; b2 ist durch [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md) erledigt).

| Option | Pro | Contra |
|---|---|---|
| **8a — kein Werkzeug bis `1.0`; vor `1.0` ändert die Spec die Fläche (Setzung aus [SPEC-059](../../../spec/spezifikation.md#einstieg-des-integrators-vertrag-zu-spec-059)); der Zähler `y` aus Entscheidung 3 ist Handarbeit und wird beim `1.0`-Schritt neu bewertet** | kein neues Plugin in Lock und Prüfsummen; bis `0.x` verspricht die Spec keine Kompatibilität | ein Bruch zwischen zwei `0.x` wird nur im Review gesehen (akzeptiertes Negativ, einmalig) |
| 8b — API-Dump-Werkzeug ab der ersten Veröffentlichung | mechanisch | neues Plugin (Lock, Prüfsummen, [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)), Pflege der Dump-Datei; vor `1.0` keine Zusage, die sie schützt |

Empfehlung **8a**; Trigger: Entwurf von `1.0`.

## Zielbild-Prüfung

Wo diese ADR Lastenheft oder Spezifikation berührt, stehen „Code angleichen“ und „Zielbild anpassen“ nebeneinander; der Nutzer entscheidet (Maßstab: Das Zielbild führt, Code folgt der Spezifikation; eine Abweichung ist Schuld, es sei denn, das Zielbild wird bewusst geändert).

| Stelle | Heutige Aussage | Berührung | Code angleichen | Zielbild anpassen | Empfehlung |
|---|---|---|---|---|---|
| [TDS-RB-01](../../../spec/lastenheft.md#tds-rb-01--implementierungssprache) | Bibliothek auf der JVM, Nachweis: reproduzierbarer JVM-Build und Einbettungstest | der Einbettungstest bindet das Klassenverzeichnis (Source-Set `integrator`), nicht das veröffentlichte Paket; der Konsum aus der Registry ist damit nicht belegt | einmalige Probe eines Konsumenten gegen `publishToMavenLocal` und gegen die Registry nach der ersten Veröffentlichung, Messprotokoll in `docs/messungen/` | Nachweis um „Konsum aus der Registry“ erweitern | Code angleichen (Probe); Lastenheft unverändert; die Lücke ist hier benannt |
| [TDS-RB-05](../../../spec/lastenheft.md#tds-rb-05--lizenz) | MIT, Nachweis `LICENSE` im Wurzelverzeichnis | das veröffentlichte Paket braucht die Lizenzangabe in der POM (MIT, URL) | POM führt die Lizenz; Prüfung im Bau (Entscheidung 2) | Nachweis um „POM nennt die Lizenz“ erweitern | Code angleichen; Lastenheft unverändert |
| [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) | Build, Tests und Testserver in Docker reproduzierbar | Veröffentlichen braucht Netz und Token und ist nicht reproduzierbar; Byte-Gleichheit des Jars bei zwei Bauten ist nicht belegt | Veröffentlichen als Werkzeug außerhalb der Gates; Bau bleibt in Docker | — | Code angleichen; **Byte-Gleichheit ist keine Zusage** (akzeptiertes Negativ, einmalig; Grund: QA-06 verlangt Reproduzierbarkeit der Umgebung, nicht des Bytes) |
| [SPEC-052](../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge) | Jar, `lib/` daneben, `Class-Path`, „kein Produkt-Image“ | bei 2a: Zusatz „veröffentlichte Form: Jar und POM, Abhängigkeiten statt `lib/`“; bei 2b/2c: Änderung des `Class-Path`-Satzes | Spec ergänzen (Folge-Slice nach `Accepted`) | — | Spec ergänzen |
| [SPEC-059](../../../spec/spezifikation.md#einstieg-des-integrators-vertrag-zu-spec-059) | Zusage bis `1.0` per Spec, ab `1.0` SemVer; „Abhängigkeiten: Jar und `lib/`“ | Zählweise vor `1.0` fehlt; Abhängigkeiten in der POM-Form | Satz zum Schema `0.y.z` und zur POM-Form | — | Spec ergänzen |
| „Nicht Teil dieses Ablaufs“ und „Offene Entscheidungen“ in [`releasing.md`](../../maintainer/releasing.md); Kopfsatz „einen Registry-Weg gibt es nicht“ | s. o. | die Aussage gilt nach `Accepted` nicht mehr | Nachzug im Folge-Slice (**nicht in dieser ADR**) | — | Nachzug |

Ein Abschnitt „Nicht Teil“ im Lastenheft besteht nicht (gelesen); es gibt dort nichts anzupassen, außer der Nutzer will die Veröffentlichung in den Zweck oder die Anforderungen schreiben (Lastenheft ist vertraglich; ändert nur er).

## Verglichene Alternativen

Die Wahl dieser ADR als Ganzes (Entscheidungen 1 bis 8 stehen in ihren Tabellen):

| Option | Pro | Contra |
|---|---|---|
| A — nichts tun: kein Registry-Weg, Handweg `docker cp` bleibt | keine Änderung; kein Token, kein Werkzeug | widerspricht dem Wunsch des Nutzers; Konsumenten bauen selbst und holen das Jar aus dem Build-Image |
| **B — GitHub Packages mit den Empfehlungen (1a, 2a, 3a, 4a, 5a, 6a, 8a)** | kleinste Änderung, die trägt: ein Plugin der Gradle-Distribution, ein Make-Fragment, eine Stage, ein Beleg im Bau; ersetzt keinen Satz einer angenommenen ADR | Token-Pflicht für Konsumenten; veröffentlichte Version ist dauerhaft sichtbar; `Class-Path`-Rest im Registry-Jar |
| C — Maven Central | übliche Quelle, kein Lese-Token (Annahme) | Signierung, `sources`/`javadoc`, Namensraum-Prüfung; großer Mehraufwand für die erste Veröffentlichung |
| D — GitHub Release mit Jar und `lib/` | kein Registry-Konto zum Lesen (nicht geprüft) | kein Auflösen von Abhängigkeiten; Handweg wird nur bequemer |

## Konsequenzen

- Positiv: Die Bibliothek ist mit Koordinaten, POM und Abhängigkeiten bindbar; die Voraussetzung des Triggers von [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md) wird für beide Formen belegt (Entscheidung 2); der Ausschluss in `releasing.md` verschwindet, ohne dass eine angenommene ADR überschrieben wird.
- Negativ: Konsumenten brauchen ein Token (Entscheidung 5); eine veröffentlichte Version bindet die öffentliche Fläche stärker als heute ([SPEC-059](../../../spec/spezifikation.md#einstieg-des-integrators-vertrag-zu-spec-059)); ein zweiter Weg zum Jar (`lib/` und Registry) ist zu pflegen; Schreib-Token auf dem Rechner des Maintainers.
- **Folgepflicht nach `Accepted` (ein Folge-Slice, Planner):** `maven-publish` und `publishing {}` im Wurzelprojekt (ohne `testserver`); POM mit Lizenz, URL, `scm`; Scope-Prüfung `api`/`implementation` für coroutines; POM-Prüfung im Bau samt Selbsttest-Mutation; Stage `publish` und die Targets samt Eingangsskript und dessen Selbsttest; Eintrag in der Tabelle „Werkzeuge“ von `harness/README.md`; Messung: `Class-Path` mit fehlenden Einträgen (2a), Probe-Konsument gegen `publishToMavenLocal`, Platzhalter-Token-Beleg; erste echte Veröffentlichung von Hand mit Messprotokoll; Nachzug von [`releasing.md`](../../maintainer/releasing.md), [SPEC-052](../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge), [SPEC-059](../../../spec/spezifikation.md#einstieg-des-integrators-vertrag-zu-spec-059) und der [Nutzer-Anleitung](../../user/einbettung-und-betrieb.md) (Setup mit `read:packages`).
- **Akzeptierte Negative (einmalig, mit Grund):** (a) Byte-Gleichheit des veröffentlichten Jars ist keine Zusage (siehe Zielbild-Prüfung). (b) Der Wächter auf Änderungen der GitHub-Token-Regeln (etwa fein granulare Token) ist kein Sensor: die Seite wird nur bei der Re-Evaluierung gelesen. (c) Ein Java-Konsument oder ein Maven-Konsument ist nicht gemessen; der Einbettungstest bleibt Kotlin und Klassenverzeichnis.

## Fitness Function (falls maschinell prüfbar)

| Tooling | Regel | Make-Target |
|---|---|---|
| `tools/manifest-pruefung.sh` (erweitert) und `generatePomFileForMavenPublication` in `build-jar` | die erzeugte POM nennt kein Jackson, keine Abhängigkeit auf `testserver`, die Lizenz MIT; das Jar bleibt ohne `Main-Class`; Selbsttest mit einer Mutation je Regel | `make build` (Klasse `tests`, [ADR-0024](0024-lokale-stempel-zwei-klassen.md)), Selbsttest `make skript-selbsttest` |
| Eingangsskript des Veröffentlichens (Version ohne `-SNAPSHOT`, Tag `v<Version>` auf `HEAD`, sauberer Baum, Token gesetzt) | bricht vor dem Netz | `make publish` (**kein Gate**), Selbsttest in `make skript-selbsttest` |
| Textprüfung im Folge-Slice | `make publish` steht nicht in `GATE_CHECKS` und nicht in `make e2e` (Absicht; `make gate-zuordnung` prüft Gates, nicht die Abwesenheit eines Targets) | nur Absicht, im Review |

## Re-Evaluierungs-Trigger

Ein Konsument, der kein Token anlegen kann oder will (dann 5b oder 5c); der Entschluss für Maven Central (dann 4b, `sources`/`javadoc`, Namensraum); eine Änderung der Token-Regeln von GitHub Packages (etwa Unterstützung anderer Token-Arten oder anonymes Lesen öffentlicher Maven-Pakete); der Entwurf von `1.0` (dann 8b und die Zählweise neu); ein zweites veröffentlichtes Modul (dann 1b); regelmäßige Veröffentlichungen, die ein persönliches Token unzumutbar machen (dann 6c); der Beleg, dass der `Class-Path` im Registry-Jar Konsumenten stört (dann 2b oder 2c, `Supersedes` des Halbsatzes aus [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md)); eine Version, die zurückgenommen werden muss; die Entscheidung über die Lizenzhinweise der Bibliotheken ([ADR-0018](0018-laufzeit-artefakt.md) „Offen“).

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-11-13 | Proposed – Auftrag des Nutzers: Veröffentlichung über GitHub Packages; Optionen mit Empfehlung, unbestätigt; Prämisse „[ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md)/[ADR-0018](0018-laufzeit-artefakt.md) schließen den Registry-Weg aus“ gelesen und nicht bestätigt (siehe Kontext) | — |
| 2026-11-14 | Accepted (Entscheidung des Nutzers: „ADR 0047 accepted“, gelesen als Annahme der jeweils empfohlenen Optionen 1a, 2a, 3a, 4a, 5a, 6a, 8a; bei 2a wird kein Satz der Vorgänger ersetzt) | — |


Nach `Accepted` wird diese Datei **nicht mehr inhaltlich überschrieben**.
Spätere Korrekturen oder Schärfungen entstehen als neue ADR mit
`Supersedes ADR-NNNN` (Baseline-Regelwerk `modul-04-adrs.md`
§Hard Rule für Accepted-ADRs).
