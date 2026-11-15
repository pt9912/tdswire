# ADR-0049: Cloudsmith als zweites, anonym lesbares Veröffentlichungsziel neben GitHub Packages (Konsumentenzugriff: ersetzt die alleinige Wahl 5a von ADR-0047)

**Status:** Accepted

**Datum:** 2026-11-15

**Autor:** Architect (Entwurf); Entscheidung: pt9912 (Nutzer), angenommen am 2026-11-15

**Anlass (Quelle):** Nutzer, 2026-11-15, im Auftrag an den Architect: Maven Central ist ihm zu aufwendig; die Token-Pflicht von GitHub Packages für Konsumenten hält er für keine gute Lösung; das Cloudsmith-Repository `tdswire` unter dem Owner `pt9912` hat er angelegt. **Alle Empfehlungen unten sind unbestätigt; entschieden ist erst mit der Annahme durch den Nutzer.**

**Bezug:** [TDS-RB-01](../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-RB-05](../../../spec/lastenheft.md#tds-rb-05--lizenz), [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [ADR-0047](0047-veroeffentlichung-ueber-github-packages.md), [ADR-0048](0048-veroeffentlichung-in-der-ci-bei-tag-push.md), [ADR-0022](0022-e2e-laeufe-nicht-in-den-gates.md), [ADR-0024](0024-lokale-stempel-zwei-klassen.md), [AGENTS.md](../../../AGENTS.md) §3.1, §3.5, §3.6, §3.8

**Schärft:** — (keine Spec-Stelle: Das Veröffentlichen ist ein Werkzeug außerhalb der Gates; gelesen am 2026-11-15 per `grep` über `spec/`: dort steht weder „GitHub Packages“ noch `read:packages`)

**Supersedes (teilweise), jeweils nur wörtlich benannt, wirksam erst mit dem Status `Accepted`; die Dateien der Vorgänger bleiben unverändert und `Accepted` ([AGENTS.md](../../../AGENTS.md) §3.5):**

- [ADR-0047](0047-veroeffentlichung-ueber-github-packages.md) Entscheidung 5, **ausschließlich** (a) die Wahl der Option 5a, „GitHub Packages, die Token-Pflicht der Konsumenten ist akzeptiert und dokumentiert“, **als alleiniger Weg der Konsumenten**, und (b) den Satz „Empfehlung **5a**; 5b ist ein später möglicher Zusatz, 5c der Trigger-Fall (unten).“ Für den Weg der Konsumenten gilt stattdessen Entscheidung 1 dieser ADR.
- **Nicht ersetzt** (gilt unverändert weiter): in Entscheidung 5 von [ADR-0047](0047-veroeffentlichung-ueber-github-packages.md) der Absatz „Belegt (Abruf 2026-11-13 …)“ samt der Folge für Konsumenten **des GitHub-Weges** (Token mit `read:packages`; das bleibt wahr, solange GitHub Packages ein Ziel ist), die Optionszeilen 5b und 5c als dokumentierte Alternativen, alle anderen Entscheidungen (1 bis 4, 7, 8) und von Entscheidung 6 alles, was [ADR-0048](0048-veroeffentlichung-in-der-ci-bei-tag-push.md) nicht ersetzt hat. Entscheidung 6 (Wahl 6a) ist bereits durch [ADR-0048](0048-veroeffentlichung-in-der-ci-bei-tag-push.md) teilweise ersetzt; diese ADR berührt diesen Satz nicht und überschneidet sich mit ihm nicht. Von [ADR-0048](0048-veroeffentlichung-in-der-ci-bei-tag-push.md) wird nichts ersetzt; sie wird in Entscheidung 3 und 4 nur **erweitert** (Konsequenzen), wo ihr Text einen einzigen Veröffentlichungsweg beschreibt.

**Regeln:** Baseline-Regelwerk `modul-04-adrs.md` §Ziel-Form: ADR (MADR).

---

## Kontext

**Auslöser ist ein Trigger der Vorgänger-ADR.** [ADR-0047](0047-veroeffentlichung-ueber-github-packages.md) nennt unter „Re-Evaluierungs-Trigger“: „Ein Konsument, der kein Token anlegen kann oder will (dann 5b oder 5c)“. Der Nutzer erklärt am 2026-11-15, die Token-Pflicht sei keine gute Lösung. Das ist kein fremder Konsument, sondern der Eigner des Projekts; diese ADR liest es als **eingetreten** (Setzung, offen benannt), statt den Trigger zu übergehen.

**Was gelesen, gemessen und nicht gelesen ist** ([AGENTS.md](../../../AGENTS.md) §3.8; Datum und Abrufweg je Aussage).

*Gemessen am 2026-11-15 mit `curl` ohne Token, im Lauf dieser ADR:*

| Abruf | HTTP |
|---|---|
| GitHub Packages, POM von `tdswire` 0.1.0 (`https://maven.pkg.github.com/pt9912/tdswire/io/github/pt9912/tdswire/0.1.0/tdswire-0.1.0.pom`) | 401 |
| Cloudsmith, `maven-metadata.xml` von `tdswire` (`https://dl.cloudsmith.io/public/pt9912/tdswire/maven/io/github/pt9912/tdswire/maven-metadata.xml`) | 404 (das Repository ist leer; ein 404 trennt „leer“ nicht von „nicht öffentlich“) |
| Cloudsmith, `maven-metadata.xml` des Schwesterprojekts (`…/public/pt9912/pg-change-feed/maven/io/github/pt9912/pgchangefeed-kotlin/maven-metadata.xml`) | 200 |

Der Nutzer meldet für denselben Tag zusätzlich: Jar und `maven-metadata.xml` in GitHub Packages 401, Paketseite 404 (vom Nutzer gemessen, hier nicht wiederholt; das POM-Ergebnis oben stimmt mit ihm überein).

*Gelesen am 2026-11-15 mit `curl` auf `https://docs.cloudsmith.com/…` (HTML geladen, in Text gewandelt; Seiten `resources/open-source-hosting-policy`, `formats/gradle-repository`, `formats/maven-repository`, `accounts-and-teams/service-accounts`, `repositories/create-a-repository`):*

| Aussage | Anker |
|---|---|
| Open-Source-Repositories entstehen ohne vorherige Genehmigung („We don't require advance permission …“, spätere Prüfung möglich); sie sind „Broadcasts“ mit Sichtbarkeit „Open source“, öffentlich verfügbar | `open-source-hosting-policy`, „Creating an open-source repository“ |
| Kontingent: „at least 50GB of artifact data + 200GB of package delivery for free, across all open-source repositories“, getrennt von anderen Repositories gezählt | dieselbe Seite, Kopfabsatz |
| Regeln: Projekt frei und quelloffen; Antragsteller ist Maintainer; kein Minor-Fork; Hauptartefakte gehören zum Projekt, nicht nur Abhängigkeiten; **Cloudsmith ist als Quelle des kostenlosen Hostings „adequately and fairly“ zu nennen** (Text mit Link auf `cloudsmith.com` oder `app.cloudsmith.io`, der das kostenlose Hosting nennt; Badge als Beispiel); für gewinnorientierte Unternehmen mit Umsatz oder Finanzierung ist ein bezahlter Plan „more appropriate“; bei Verstößen (genannt: fehlende Attribution, Fork) kann ein Repository ausgesetzt werden | dieselbe Seite, „Rules“, „Attribution“, Abschnitt zu kommerziellen Betreibern |
| Anlage nur in der Web-App: Name, Broadcast-Schalter, Sichtbarkeit „Open source“, SPDX-Lizenz, Projekt-URL, Zustimmung zu den Hosting-Bedingungen; **„It is not possible to create Open Source repositories via the Cloudsmith API.“** | `open-source-hosting-policy`; `repositories/create-a-repository` |
| Ein Broadcast ist eine öffentliche Seite, auf der Pakete „without requiring authentication“ durchsucht und geladen werden können | `repositories/create-a-repository`, „Broadcasts“ |
| Lesen ohne Zugangsdaten: `https://dl.cloudsmith.io/public/WORKSPACE/REPOSITORY/maven/` als Repository-Eintrag, Abhängigkeit `implementation 'GROUP_ID:ARTIFACT_ID:PACKAGE_VERSION'`; private Repositories verlangen Entitlement-Token oder Basic-Auth (die Seite nennt den Platzhalter jetzt `WORKSPACE`, die ältere Lesart des Schwesterprojekts `OWNER`; die URL-Form ist dieselbe) | `gradle-repository`, „Download / installing“ |
| Schreiben per Gradle: Plugin `maven-publish`, `url = "https://maven.cloudsmith.io/WORKSPACE/REPOSITORY/"`, `credentials { username = 'USERNAME'; password = 'API-KEY' }` | `gradle-repository`, „Upload via gradle publish“ |
| Ein „Service“ ist ein Konto mit eigenem API-Key, nicht an einen Nutzer gebunden, „ideal“ für CI/CD; Anlegen verlangt Owner- oder Manager-Rolle; der Key wird nur einmal angezeigt und lässt sich erneuern; der „Service username“ steht in der Spalte NAME | `service-accounts` |
| Native Maven-Uploads werden über ein 60-Sekunden-Sammelfenster zu einem Paket (Group, Artifact, Version) zusammengeführt, das mit jeder weiteren Datei neu beginnt (die Seite beschreibt es am Beispiel `mvn deploy`; dass Gradle denselben Weg nimmt, ist Schluss aus dem gemeinsamen Endpunkt, nicht gemessen) | `maven-repository`, „Native upload staging window“ |

*Gelesen im Schwesterprojekt `/Development/pg-change-feed` am 2026-11-15 (Dateien, nichts ausgeführt):* die Entscheidung zum zweiten Ziel und der Workflow `sdk-kotlin-release.yml` (zwei Jobs ohne `needs` untereinander, ohne `continue-on-error`, Cloudsmith-Werte nur im `env:` des Schritts, Job mit `contents: read`), der Block `publishing.repositories` des Kotlin-SDK (zweites Maven-Repository `Cloudsmith`, Zugangsdaten aus `CLOUDSMITH_USERNAME` und `CLOUDSMITH_API_KEY`) und die README des SDK (Cloudsmith als erster Weg ohne Konto, GitHub Packages als zweiter mit Token, Namensnennung). **Der Nutzer meldet dort erfolgreiche Läufe** (z. B. 37418480181) **und anonym 200 für POM und `maven-metadata.xml`; die Läufe habe ich nicht abgerufen, das `maven-metadata.xml`-200 oben ist im Lauf dieser ADR gemessen, die POM-Abfrage nicht wiederholt.** Das Schwesterprojekt ist Muster, nicht Beleg für dieses Repo.

*Gelesen im Repo am 2026-11-15:* `.github/workflows/veroeffentlichung.yml`, `tools/publish.sh`, `tools/mk/publish.mk`, der Block `publishing` in `build.gradle.kts` (Repository `registry`, Aufgabe `publishMavenPublicationToRegistryRepository`, im Tag-Lauf gemessen), das [Messprotokoll des Tag-Laufs](../../messungen/veroeffentlichung-tag-lauf-2026-11-15.md), [`releasing.md`](../../maintainer/releasing.md), [ADR-0047](0047-veroeffentlichung-ueber-github-packages.md) und [ADR-0048](0048-veroeffentlichung-in-der-ci-bei-tag-push.md).

**Ungemessen oder ungelesen (Grenze dieser ADR):** ob das Repository `tdswire` auf Cloudsmith als „Open source“ eingestellt ist (nicht belegt; Kriterium im Beleg unten); ob Cloudsmith die von Gradle mitveröffentlichte `.module`-Datei annimmt und ob Gradle gegen Cloudsmith mit `.module` auflöst (die Seiten nennen `.module` nicht; `grep` über die gelesenen Seiten ohne Treffer); ob der Benutzername beim Gradle-Upload der Service-Name sein muss; das Verhalten bei Teilfehler und bei erneutem Upload derselben Version (die Seite zur Wiederveröffentlichung, `artifact-management/package-upload`, ist hier nicht gelesen; das Schwesterprojekt nennt nur aus zweiter Hand „Republishing was not enabled“); ob eine Signatur nötig ist (in den gelesenen Seiten nicht gefordert; Beleg dafür, dass keine gilt, ist das nicht); OIDC als Weg ohne langlebiges Secret (Seite nicht gelesen, nur über das Schwesterprojekt bekannt); die Seite `cloudsmith.com/open-source` (laut dem Schwesterprojekt HTTP 429, hier nicht abgerufen).

**Ist-Zustand (gelesen):** `0.1.0` liegt nur in GitHub Packages (Tag-Lauf 43699125460, [Messprotokoll](../../messungen/veroeffentlichung-tag-lauf-2026-11-15.md)); der Tag `v0.1.0` zeigt auf `b0109953`, dessen Workflow-Datei kein Cloudsmith kennt. Der Tag-Push führt in der Datei von [ADR-0048](0048-veroeffentlichung-in-der-ci-bei-tag-push.md) die Jobs `gates` und `publish` aus; `publish` hat `packages: write` und liest nur `secrets.GITHUB_TOKEN`. `tools/publish.sh` verlangt `GITHUB_ACTOR` und `GITHUB_TOKEN`, kennt als echtes Ziel nur `https://maven.pkg.github.com/pt9912/tdswire` und nimmt ein anderes Ziel nur mit einem Platzhalter-Token an; es ruft `gradle publish`. Die Aufgabe `publish` führt alle Repositories des Projekts aus; sobald ein zweites hinzukommt, scheitert sie ohne dessen Zugangsdaten (aus der Funktion der Aufgabe geschlossen; das Schwesterprojekt nennt dasselbe).

**Annahme, die kippt:** dass Cloudsmith das Repository `tdswire` als Open-Source-Repository dauerhaft anonym ausliefert (Regeln und Prüfung nach der Anlage, siehe Tabelle). Kippt sie, bleibt GitHub Packages als Weg bestehen, und diese Entscheidung ist neu zu prüfen.

## Entscheidung

Vorgeschlagen (nicht entschieden, bis der Nutzer annimmt): **Option 5b'** in Entscheidung 1, mit den Empfehlungen der Tabellen unten. Je Entscheidung eine Tabelle; **fett** ist die Empfehlung, unbestätigt. Wo ein Zielbild berührt ist, stehen „Code angleichen“ und „Zielbild anpassen“ nebeneinander (Abschnitt Zielbild-Prüfung).

### 1. Weg der Konsumenten (ersetzt die alleinige Wahl 5a)

| Option | Pro | Contra |
|---|---|---|
| 5a — GitHub Packages allein, Token-Pflicht der Konsumenten akzeptiert (Stand von [ADR-0047](0047-veroeffentlichung-ueber-github-packages.md)) | nichts zu tun; kein zusätzliches Konto und Secret; Rechte erbt vom Repository | gemessen: anonymer Abruf 401; jeder Konsument braucht Konto und Token; der Nutzer hält das nicht für gut |
| **5b' — GitHub Packages bleibt, zusätzlich Cloudsmith als anonym lesbares Ziel desselben Pakets (`io.github.pt9912:tdswire`, dieselben Dateien)** | anonymer Bezug ohne Konto (Beleg: Kontext, Schwesterprojekt anonym 200); gleicher Mechanismus (`maven-publish`, ein zweites Repository im Block `publishing`); Kontingent weit über dem Bedarf (Größe des Pakets ungemessen, ein Jar mit POM; gegen 50 GB Speicher offensichtlich klein, nicht als Zahl belegt); GitHub Packages und `0.1.0` bleiben | ein weiterer Drittanbieter mit Nutzungsbedingungen (Prüfung nach Anlage, Aussetzung möglich); ein Konto und zwei Secrets, das **langlebige Schlüssel** sind (anders als `GITHUB_TOKEN`, Gegenstand von [ADR-0048](0048-veroeffentlichung-in-der-ci-bei-tag-push.md)); Namensnennung Pflicht; Konsumenten tragen einen Repository-Eintrag; ein Teilfehler zwischen zwei Zielen ist möglich (Entscheidung 2) |
| 5c — Maven Central statt oder zusätzlich | übliche Quelle der JVM-Welt, kein Repository-Eintrag beim Konsumenten (Annahme aus allgemeinem Wissen, nicht in diesem Lauf geprüft) | **vom Nutzer am 2026-11-15 als zu aufwendig abgelehnt;** laut den am 2026-11-13 gelesenen Central-Anforderungen ([ADR-0047](0047-veroeffentlichung-ueber-github-packages.md), Quelle dort) GPG-Signatur, `sources`- und `javadoc`-Jar, Namensraum-Prüfung; widerspricht der Setzung 4a von [ADR-0047](0047-veroeffentlichung-ueber-github-packages.md) |
| 5d — Release-Dateien (Jar, POM) an einem Release des Repositories als Spiegel (die Option 5b von [ADR-0047](0047-veroeffentlichung-ueber-github-packages.md)) | kein Registry-Konto zum Lesen (ob Release-Dateien anonym ladbar sind, ist hier nicht geprüft) | kein Maven-Auflösen der Abhängigkeiten; ein dritter Veröffentlichungsweg; löst „bindbar ohne Token“ für Gradle-Nutzer nicht |
| 5e — Cloudsmith **statt** GitHub Packages | ein Weg, ein Secret-Satz | `0.1.0` liegt in GitHub Packages und bleibt dort; der `GITHUB_TOKEN`-Weg ist gemessen, der Cloudsmith-Weg nicht; ein Ausfall oder eine Aussetzung bei Cloudsmith ließe keinen Weg |

Empfehlung **5b'**. Sie ist die Option mit dem geringsten Zusatzaufwand, die den Nutzerwunsch trägt (anonymer Bezug, kein Central). Zielbild-Seite: Es ist kein Lastenheft-Satz berührt, der die Token-Pflicht festschreibt (Abschnitt Zielbild-Prüfung).

**Verpflichtungen aus 5b', soweit gelesen:** Das README nennt Cloudsmith als Quelle des kostenlosen Hostings mit Link auf `cloudsmith.com` (Regel „Attribution“, Tabelle oben); das Projekt bleibt frei und quelloffen (Lizenz MIT, [TDS-RB-05](../../../spec/lastenheft.md#tds-rb-05--lizenz)); der Betreiber (Nutzer) legt Repository und Service in der Web-App an, **das ist schon teilweise geschehen** (Repository angelegt; Service und Secrets nicht).

### 2. Ablauf bei zwei Zielen und Folgen für den Workflow

Der Workflow `veroeffentlichung.yml` bekommt bei 5b' einen zweiten Veröffentlichungs-Job. Die Frage ist, wie die beiden Ziele zueinander stehen. Gemeinsam gilt für alle Optionen: beide Veröffentlichungs-Jobs hängen an `needs: gates` und an derselben Bedingung (nur Tag-Push, Tag-Commit in `origin/main`); `vorschau` und `workflow_dispatch` veröffentlichen nie.

| Option | Pro | Contra |
|---|---|---|
| **2a — zwei Jobs, beide `needs: gates`, ohne `needs` untereinander, ohne `continue-on-error`; ein roter Job wird allein wiederholt („Re-run failed jobs“)** | kein Ziel blockiert das andere; folgt dem Schwesterprojekt, in dem der Aufbau laut Nutzer erfolgreich läuft; je Job eigene `permissions` und eigenes Secret-Fenster | zwei Docker-Bauten der Stage `publish` (der eine gemessene Bau dauerte rund 78 s, [Messprotokoll](../../messungen/veroeffentlichung-tag-lauf-2026-11-15.md)); Teilfehler möglich (unten) |
| 2b — zwei Jobs in Reihe: Cloudsmith nach GitHub Packages (`needs: publish`) | weniger Zustände: scheitert GitHub Packages, ist die Version nirgends; nur „GitHub Packages ja, Cloudsmith nein“ bleibt als Teilzustand | ein Fehler bei GitHub Packages hält den anonymen Weg zurück, dem Ziel dieser ADR; Cloudsmith-Fehler zeigt sich erst nach der Registry-Schreibung |
| 2c — ein Job, zwei Schritte | ein Bau der Stage, kürzere Laufzeit | „Re-run“ des Jobs wiederholt den bereits erfolgreichen Schritt gegen eine veröffentlichte Version (Verhalten der Registry ungemessen, [ADR-0047](0047-veroeffentlichung-ueber-github-packages.md) Entscheidung 3); ein Schritt-Fehler maskiert sich leicht; beide Secrets im selben Job, `packages: write` und Cloudsmith-Key zusammen |

Empfehlung **2a**.

**Folge bei Teilfehler, benannt:** Scheitert genau ein Ziel, liegt die Version nur auf dem anderen; sie ist dort **verbraucht** (Setzung „eine veröffentlichte Version wird nie neu veröffentlicht“, [ADR-0047](0047-veroeffentlichung-ueber-github-packages.md) Entscheidung 3a, unverändert). Der Weg zurück ist **nicht** eine neue Version, sondern die Wiederholung des roten Jobs allein, **wenn** dessen Ziel die Version nicht trägt; das ist keine Neuveröffentlichung an einem Ziel, das sie schon hat. Das ist kein Satz, den [ADR-0048](0048-veroeffentlichung-in-der-ci-bei-tag-push.md) ersetzt wird: sein „Re-run“-Ausschluss („für eine veröffentlichte Version nicht vorgesehen“) gilt je Ziel und wird hier **enger gelesen**, nicht aufgehoben (Lesart, offen benannt; sie beruht darauf, dass „Re-run failed jobs“ den grünen Job nicht erneut startet, was im Schwesterprojekt als Dokumentationsstand gelesen, hier nicht gemessen ist). Bleibt nach einem Fehler ein Teilbestand (einzelne Dateien ohne vollständiges Paket) im Ziel, ist die Version dort ungewiss: Cloudsmith nennt das Löschen eines Pakets als Aktion der Web-App (Seite nicht gelesen), GitHub Packages ebenso (nicht gelesen); ob eine Löschung die Version wieder frei macht, ist **ungemessen** und bei Eintritt eine Entscheidung des Nutzers. Vor jeder Wiederholung prüft der Maintainer, ob das Ziel die Version trägt (GitHub Packages: Abfrage mit Token oder Paketseite; Cloudsmith: anonymer Abruf der POM, nach dem Sammelfenster und der asynchronen Verarbeitung ggf. mit Wartezeit).

**Akzeptiertes Negativ (einmalig, mit Grund):** Ein Cloudsmith-Upload, den der Job als Erfolg meldet, ist noch nicht ausgeliefert (Sammelfenster, Verarbeitung); der Job belegt den Upload, nicht die Verfügbarkeit. Grund: ein Warten im Workflow auf einen anonymen Abruf wäre ein neuer Schritt mit Zeitgrenze für einen einmaligen, harmlosen Blindfleck; der Beleg liegt im Messprotokoll der ersten echten Veröffentlichung (Entscheidung 6).

### 3. Secrets-Regime, Berechtigungen, Tag-Schutz

| Option | Pro | Contra |
|---|---|---|
| **3a — API-Key eines Cloudsmith-Services als Repository-Secret `CLOUDSMITH_API_KEY` mit `CLOUDSMITH_USERNAME`; beide stehen nur im `env:` des Schritts, der `make`-Aufruf reicht sie nur namentlich an den Container; Job `contents: read`, kein `packages: write`; Service mit Schreibrecht nur auf das Repository `tdswire`** | keine Action, kein neuer Pin ([AGENTS.md](../../../AGENTS.md) §3.8); derselbe Mechanismus wie das Schwesterprojekt; Docker-only ([AGENTS.md](../../../AGENTS.md) §3.1) | ein langlebiges Schreib-Secret in einem Repository; wer den Workflow auf einem Tag ändern und den Tag auf `main` pushen kann, kann es auslesen (Tag-Schutz ist die tragende Kontrolle, wie schon in [ADR-0048](0048-veroeffentlichung-in-der-ci-bei-tag-push.md)); „Schreibrecht nur auf dieses Repository“ ist Einstellung des Betreibers, vom Repo nicht prüfbar (die gelesene Service-Seite nennt Anlage, Schlüssel und Erneuerung, nicht den Zuschnitt auf ein Repository) |
| 3b — OIDC aus GitHub Actions, kurzlebiges Token | kein langlebiges Secret | braucht die Action `cloudsmith-io/cloudsmith-cli-action` und `id-token: write`; Verfügbarkeit im Open-Source-Angebot und die Seite hier nicht gelesen; das Token müsste in den Container; mehr Lieferkette |
| 3c — persönlicher API-Key des Nutzers | ein Objekt weniger | an eine Person gebunden, Rechte über das Repository hinaus; die Service-Seite empfiehlt Services für CI/CD |

Empfehlung **3a**. Folgen:

- **Tag-Schutz `v*`** ([`releasing.md`](../../maintainer/releasing.md) §Tag-Schutz): unverändert; seine Bedeutung wächst, weil ein Tag-Push nun auch ein Drittanbieter-Secret verwendet. Kein neuer Schutz nötig, kein Sensor (wie in [ADR-0048](0048-veroeffentlichung-in-der-ci-bei-tag-push.md): **akzeptiertes Negativ**, die Einstellung liegt bei GitHub).
- **Secrets nur im `env:` des Schritts** gilt je Job; der Job `publish` (GitHub Packages) bekommt die Cloudsmith-Werte nie, der Cloudsmith-Job nie `packages: write`. `make workflow-lint` prüft das nicht ([`harness/README.md`](../../../harness/README.md) §Sensors); der Review prüft es (wie bei [ADR-0048](0048-veroeffentlichung-in-der-ci-bei-tag-push.md)).
- **`tools/publish.sh` und Make-Ziele:** `tools/publish.sh` bleibt der einzige Eingang (Version, Tag auf `HEAD`, sauberer Baum, Präfixprüfung). Es braucht einen Zielschalter: für GitHub Packages `GITHUB_ACTOR`/`GITHUB_TOKEN` wie bisher, für Cloudsmith `CLOUDSMITH_USERNAME`/`CLOUDSMITH_API_KEY`, je Ziel nur dessen Werte nur namentlich an `docker run`. Echtes Schlüsselmaterial geht nur an das feste Standardziel des jeweiligen Anbieters (`https://maven.cloudsmith.io/pt9912/tdswire/`, Setzung nach der gelesenen Gradle-Seite), jedes andere Ziel nur mit Platzhalter wie bisher. Das Ziel von `make publish` bleibt GitHub Packages (bestehende Zusage, [ADR-0047](0047-veroeffentlichung-ueber-github-packages.md) Entscheidung 6 in der Fassung nach [ADR-0048](0048-veroeffentlichung-in-der-ci-bei-tag-push.md)); ein neues Make-Ziel (Name legt der Folge-Plan fest) führt Cloudsmith. Beide bleiben **kein Gate**, in keiner Liste `GATE_CHECKS`, `GATE_KLASSE_*` oder `GATE_STAGE`, nicht in `make e2e`; `make skript-selbsttest` (Klasse `werkzeug`, [ADR-0024](0024-lokale-stempel-zwei-klassen.md)) erweitert den Selbsttest des Skripts um den Zielschalter.
- **Gradle-Aufgaben je Ziel:** Weil `publish` alle Repositories ausführt, ruft der Lauf je Ziel die Einzel-Aufgabe (GitHub Packages: `publishMavenPublicationToRegistryRepository`, im Tag-Lauf gemessen; Cloudsmith: Name aus dem Repository-Namen abgeleitet, im Folge-Plan zu belegen). Das ändert den Aufruf in `tools/publish.sh` (heute `gradle publish`).
- **Ausschluss von `-SNAPSHOT`:** unverändert, im Eingangsskript für beide Ziele vor Docker (Setzung aus [ADR-0047](0047-veroeffentlichung-ueber-github-packages.md) Entscheidung 3). Die Verteilung der Snapshots über Cloudsmith ist **nicht** vorgesehen (die gelesene Gradle-Seite zeigt eine Auswahl der URL nach `SNAPSHOT`; sie wird hier nicht genutzt: ein Repository, eine URL).
- **Bau-Prüfung von POM und `.module`:** Die Prüfung in `tools/manifest-pruefung.sh` liest die POM (kein Jackson, kein Testserver, Lizenz MIT). Die `.module`-Datei hat keine Prüfung (offener Punkt aus dem Messprotokoll-Umfeld, im Plan der ersten Veröffentlichung geführt). Hier neu: Cloudsmith ist ein zweiter Empfänger der `.module`; ob er sie annimmt und Gradle sie dort auflöst, ist ungemessen (Entscheidung 5). Ein Abschalten der `.module` (`GenerateModuleMetadata`) wäre eine Änderung am veröffentlichten Artefakt und eine eigene Entscheidung, falls der Beleg es verlangt; sie wird hier nicht vorweggenommen.

### 4. Version der ersten Cloudsmith-Veröffentlichung

`0.1.0` liegt nur in GitHub Packages und wird nie neu veröffentlicht ([ADR-0047](0047-veroeffentlichung-ueber-github-packages.md) Entscheidung 3a, unverändert). Der Tag `v0.1.0` zeigt auf einen Commit, dessen Workflow-Datei kein Cloudsmith kennt; ein Re-Run des damaligen Laufs führte die alte Datei aus (aus den Tatsachen oben geschlossen, nicht versucht) und wäre überdies eine Wiederholung gegen eine veröffentlichte Version.

| Option | Pro | Contra |
|---|---|---|
| **4a — `0.1.1`, neu veröffentlicht auf **beiden** Zielen durch den Workflow bei Tag-Push `v0.1.1`** | der CI-Weg von [ADR-0048](0048-veroeffentlichung-in-der-ci-bei-tag-push.md) bleibt der einzige; kein Schlüssel auf einem Rechner; beide Ziele tragen dieselbe Version; folgt der Regel „Korrektur = neue Version“ von [ADR-0047](0047-veroeffentlichung-ueber-github-packages.md) 3a (`z` steigt, die öffentliche Fläche ändert sich nicht, der Zähler `y` bleibt) | `0.1.0` bleibt ohne anonymen Weg; eine Version, deren Inhalt sich nur im Veröffentlichungsweg unterscheidet |
| 4b — `0.1.0` von Hand mit Schreib-Key nach Cloudsmith | `0.1.0` wird anonym beziehbar | widerspricht dem CI-Weg von [ADR-0048](0048-veroeffentlichung-in-der-ci-bei-tag-push.md) und dessen Begründung (kein langlebiger Schlüssel auf einem Rechner); das Jar müsste dasselbe sein wie in GitHub Packages (Byte-Gleichheit bei zwei Bauten ist ausdrücklich keine Zusage, [ADR-0047](0047-veroeffentlichung-ueber-github-packages.md) Zielbild-Prüfung); zwei Wahrheiten über eine Version |
| 4c — keine neue Version, Cloudsmith erst bei der nächsten fachlichen Version (`0.2.0`) | keine Version ohne Inhalt | der anonyme Bezug, der Anlass dieser ADR, bleibt bis dahin aus |

Empfehlung **4a**. Der Release-Ablauf aus [`releasing.md`](../../maintainer/releasing.md) bleibt (Version ohne `-SNAPSHOT` im Release-Commit, Tag `v<Version>`, danach wieder `-SNAPSHOT`).

### 5. Beleg und Konsumenten-Anleitung

- **Beleg der ersten echten Veröffentlichung (kein Probelauf möglich, wie bei [ADR-0048](0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Entscheidung 4):** beide Jobs grün; **anonymer** Abruf (ohne Token) von POM und `maven-metadata.xml` am Download-Pfad von Cloudsmith ergibt 200; ein Probe-Konsument (Kotlin, Gradle) löst `io.github.pt9912:tdswire:0.1.1` **ohne Zugangsdaten** über den Cloudsmith-Eintrag auf, **mit** `.module` und ohne, übersetzt und läuft; die Messung hält fest, ob das Repository als „Open source“ öffentlich ausliefert (der anonyme 200 ist dafür der Beleg, ein 404 vor der ersten Version ist es nicht). Messprotokoll in `docs/messungen/`.
- **Konsumenten-Anleitung (Reihenfolge):** zuerst Cloudsmith ohne Konto (Repository-Eintrag `https://dl.cloudsmith.io/public/pt9912/tdswire/maven/`, Koordinaten), dann GitHub Packages mit Token (`read:packages`, wie bisher) als zweiter Weg; zusätzlich Maven Central für Abhängigkeiten der Bibliothek (die Seite des Cloudsmith-Repositories bietet sie nicht an; für den Eintrag des Schwesterprojekts gelesen, für dieses Repository ungemessen). Ort: die Nutzer-Doku (`docs/user/`), im Ist-Zustand erst nach dem Beleg.
- **README-Pflicht:** Das Projekt-README nennt Cloudsmith nach den Hosting-Regeln (Link auf `cloudsmith.com`, Hinweis auf kostenloses Hosting für Open Source); Text oder Badge, wie die gelesene Seite sie beschreibt.

## Zielbild-Prüfung

Wo diese ADR Lastenheft, Spezifikation oder Sensors-Tabelle berührt, stehen „Code angleichen“ und „Zielbild anpassen“ nebeneinander; der Nutzer entscheidet (Maßstab: Das Zielbild führt, Code folgt; eine Abweichung ist Schuld, es sei denn, das Zielbild wird bewusst geändert).

| Stelle | Heutige Aussage | Berührung | Code angleichen | Zielbild anpassen | Empfehlung |
|---|---|---|---|---|---|
| [TDS-RB-01](../../../spec/lastenheft.md#tds-rb-01--implementierungssprache) | Nachweis: reproduzierbarer JVM-Build und Einbettungstest (Klassenverzeichnis); der Konsum aus der Registry war als Lücke benannt ([ADR-0047](0047-veroeffentlichung-ueber-github-packages.md) Zielbild-Prüfung) | der Konsum ist mit Cloudsmith erstmals ohne Token messbar | Probe-Konsument gegen Cloudsmith (Entscheidung 5) | Nachweis um „Konsum aus der Registry“ erweitern (Lastenheft, vertraglich: ändert nur der Nutzer) | Code angleichen; Lastenheft unverändert |
| [TDS-RB-05](../../../spec/lastenheft.md#tds-rb-05--lizenz) | MIT, Nachweis `LICENSE` | Cloudsmith verlangt für Open-Source-Repositories eine SPDX-Lizenz im Formular; MIT ist SPDX-gelistet (SPDX-Listenstand nicht abgerufen) | Betreiber wählt `MIT` im Formular | — | Code angleichen; Lastenheft unverändert |
| [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) | Build, Tests und Testserver in Docker reproduzierbar | Veröffentlichen braucht Netz und Schlüssel, ist nicht wiederholbar; ein zweites Ziel ändert das nicht | Veröffentlichen bleibt Werkzeug außerhalb der Gates; Bau bleibt in Docker | — | Code angleichen |
| Zeile `.github/workflows/veroeffentlichung.yml` und Zeilen `make publish*` in [`harness/README.md`](../../../harness/README.md) §Sensors | nennen nur GitHub Packages | zweites Ziel, neues Make-Ziel, neue Secrets | Zeilen und Bindung an diese ADR nachziehen (Folge-Plan, nach `Accepted`) | — | Nachzug |
| Gates ([AGENTS.md](../../../AGENTS.md) §3.6) | keine Schwelle berührt | keine Lockerung: `make publish*` bleibt ohne Gate-Liste; `make skript-selbsttest` wird erweitert, nicht gesenkt | — | — | nichts zu tun |

Spec (`spec/`) nennt kein Veröffentlichungsziel (`grep` am 2026-11-15, kein Treffer); dort ist nichts nachzuziehen.

## Verglichene Alternativen

Die Wahl als Ganzes (Entscheidungen 1 bis 4) ist in den Tabellen oben geführt; „nichts tun“ ist die Option 5a. Eine zusätzliche Form:

| Option | Pro | Contra |
|---|---|---|
| JitPack aus dem Git-Tag | kein Konto, kein Secret | Bau beim Konsumenten aus dem Quelltext, keine geprüfte Lieferung; im Schwesterprojekt bereits verworfen (aus zweiter Hand, hier nicht geprüft); widerspricht der Docker-only-Linie ([AGENTS.md](../../../AGENTS.md) §3.1) für das Veröffentlichte |

## Konsequenzen

- Positiv: Gradle-Konsumenten binden die Bibliothek ohne Konto und Token (nach dem Beleg in Entscheidung 5); der CI-Weg von [ADR-0048](0048-veroeffentlichung-in-der-ci-bei-tag-push.md) bleibt der einzige Weg; GitHub Packages und `0.1.0` bleiben unberührt.
- Negativ: ein Drittanbieter mit Bedingungen (Attribution, Prüfung nach Anlage, mögliche Aussetzung); zwei langlebige Secrets; ein zweiter Docker-Bau je Tag-Push; Teilfehler zwischen den Zielen sind möglich und verbrauchen die Version am anderen Ziel (Entscheidung 2); `0.1.0` bleibt ohne anonymen Weg (Entscheidung 4).
- **Folgepflicht nach `Accepted` (Planner; die Pläne schreibt diese ADR nicht um):**
  1. *Ein Plan für das zweite Ziel:* zweites Repository in `build.gradle.kts`, Zielschalter und Selbsttest in `tools/publish.sh`, neues Make-Ziel in `tools/mk/publish.mk`, zweiter Job in `.github/workflows/veroeffentlichung.yml` (Entscheidung 2 und 3), Zeilen in [`harness/README.md`](../../../harness/README.md) §Sensors. **Start-Bedingung (beobachtbar):** der Nutzer hat Service und die Secrets `CLOUDSMITH_USERNAME` und `CLOUDSMITH_API_KEY` angelegt (`gh secret list` zeigt beide) und bestätigt, dass das Repository als „Open source“ eingestellt ist.
  2. *Release `0.1.1`* (Entscheidung 4a) mit Messprotokoll (Entscheidung 5): Tag-Push durch den Nutzer; Beleg anonym und mit Probe-Konsument.
  3. **Nachzug bestehender Aussagen (nur benannt, nicht in dieser ADR geändert):** [`releasing.md`](../../maintainer/releasing.md) (Kopf „als Maven-Paket für GitHub Packages“, Schritt 9 mit „Das Eingangsskript nimmt als Ziel nur GitHub Packages“, Tag-Schutz-Abschnitt um das Secret, „Offene Entscheidungen“); das [Messprotokoll des Tag-Laufs](../../messungen/veroeffentlichung-tag-lauf-2026-11-15.md) bleibt wahr (es beschreibt `0.1.0`), nur sein Hinweis „Ob das Paket anonym lesbar ist, ist … nicht gemessen“ bekommt in einem neuen Protokoll die Antwort für Cloudsmith, nicht durch Änderung der Datei; die Kopfkommentare von `veroeffentlichung.yml` und `tools/publish.sh`, `tools/mk/publish.mk` („Das Token kommt nur aus … `GITHUB_*`“); der laufende Plan der ersten Veröffentlichung unter `docs/plan/planning/in-progress/` (Titel „Erste Veröffentlichung der Bibliothek in GitHub Packages …“: sein Risiko „Konsumenten ohne Konto oder Token können die Bibliothek nicht binden“ und sein Nutzer-Doku-Punkt zur Anleitung mit `read:packages`) und die Roadmap, soweit sie dieses Risiko führt; das Nutzer-Setup in `docs/user/einbettung-und-betrieb.md` (Reihenfolge nach Entscheidung 5); das README (Namensnennung); der Index der ADRs (Vermerk am Eintrag von [ADR-0047](0047-veroeffentlichung-ueber-github-packages.md) nach der Annahme, nicht in dessen Datei, wie bei [ADR-0048](0048-veroeffentlichung-in-der-ci-bei-tag-push.md)).

## Fitness Function (falls maschinell prüfbar)

| Tooling | Regel | Make-Target |
|---|---|---|
| `tools/publish.sh` mit Selbsttest (erweitert) | je Ziel brechen leere Zugangsdaten, `-SNAPSHOT`, fehlender Tag, unsauberer Baum vor Docker; echtes Schlüsselmaterial nur am festen Standardziel des Anbieters | `make skript-selbsttest` (Klasse `werkzeug`, [ADR-0024](0024-lokale-stempel-zwei-klassen.md)) |
| `actionlint` im Container | `veroeffentlichung.yml` ist syntaktisch gültig | `make workflow-lint` (**kein Gate**) |
| Review (kein Sensor) | beide Veröffentlichungs-Jobs `needs: gates`; `packages: write` nur im GitHub-Packages-Job; Cloudsmith-Secrets nur im `env:` des Cloudsmith-Schritts; keine neue Action ohne SHA; `make publish*` in keiner Gate-Liste | nur Absicht |
| Messprotokoll der ersten Veröffentlichung | anonymer Abruf 200, Probe-Konsument ohne Zugangsdaten | kein Target, einmalige Messung |

## Re-Evaluierungs-Trigger

Der anonyme Abruf der veröffentlichten POM antwortet nicht mit 200 oder das Repository ist nicht „Open source“-öffentlich (dann 5a oder neue ADR); Cloudsmith setzt das Repository aus oder ändert Regeln, Kontingent oder Attributionspflicht; Gradle löst gegen Cloudsmith mit `.module` nicht auf (dann Entscheidung über die Moduldatei); ein Teilfehler zwischen den Zielen tritt auf (dann 2b oder die Löschregel neu); der Wunsch nach einem Weg ohne langlebiges Schreib-Secret (dann OIDC, 3b, nach Lektüre der Seite); der Entschluss für Maven Central (dann 5c mit Signierung und `sources`/`javadoc`, [ADR-0047](0047-veroeffentlichung-ueber-github-packages.md) Entscheidung 4); ein zweites veröffentlichtes Modul (dann je Modul ein Paket); eine Änderung der Token-Regeln von GitHub Packages, die anonymes Lesen erlaubt (dann entfällt der Anlass).

## Offene Fragen an den Nutzer

1. Entscheidung 1: 5b' (empfohlen), 5a, 5c (von Ihnen abgelehnt), 5d oder 5e?
2. Entscheidung 2: zwei unabhängige Jobs mit Wiederholung des roten Jobs (2a, empfohlen) oder in Reihe (2b, weniger Teilzustände, aber der anonyme Weg wartet auf GitHub Packages)?
3. Entscheidung 4: `0.1.1` auf beiden Zielen (4a, empfohlen), `0.1.0` von Hand (4b) oder erst `0.2.0` (4c)?
4. Ist das Cloudsmith-Repository `tdswire` als „Open source“ (Broadcast) eingestellt, und mit welcher Lizenzangabe und Projekt-URL? Der Service mit Schreibrecht nur auf dieses Repository und die beiden Secrets stehen aus.
5. Soll das Lastenheft ([TDS-RB-01](../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), Nachweis) um „Konsum aus der Registry“ ergänzt werden, oder bleibt es bei der benannten Lücke?

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-11-15 | Proposed – Auftrag des Nutzers an den Architect (Cloudsmith als zweites, anonym lesbares Ziel; Maven Central abgelehnt); Optionen mit Empfehlung, unbestätigt; ersetzt nach Annahme nur die alleinige Wahl 5a von Entscheidung 5 in [ADR-0047](0047-veroeffentlichung-ueber-github-packages.md) | — |
| 2026-11-15 | Accepted (Entscheidung des Nutzers: „Annehmen mit den Empfehlungen“ auf den Entwurf: Entscheidung 1 = 5b', 2 = 2a, 3 = 3a, 4 = 4a, `0.1.1` auf beiden Zielen über den CI-Weg; zu Frage 5 bleibt das Lastenheft ([TDS-RB-01](../../../spec/lastenheft.md#tds-rb-01--implementierungssprache) Nachweis) unverändert und die Lücke benannt); wirksam: die alleinige Wahl 5a von Entscheidung 5 in [ADR-0047](0047-veroeffentlichung-ueber-github-packages.md) ersetzt | — |

Nach `Accepted` wird diese Datei **nicht mehr inhaltlich überschrieben**.
Spätere Korrekturen oder Schärfungen entstehen als neue ADR mit
`Supersedes ADR-NNNN` (Baseline-Regelwerk `modul-04-adrs.md`
§Hard Rule für Accepted-ADRs).
