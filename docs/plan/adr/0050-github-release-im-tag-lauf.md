# ADR-0050: GitHub Release im Tag-Lauf mit `tdswire.jar` und `lib/` als Anhang (Weg ohne Registry; Neubewertung der Alternativen 5b und D aus ADR-0047)

**Status:** Proposed

**Datum:** 2026-11-16

**Autor:** Architect (Entwurf); Richtung und drei Entscheidungen: pt9912 (Nutzer, 2026-11-16, siehe Kontext). **Alles unter „Vorschlag“ ist unbestätigt; entschieden ist es erst, wenn der Nutzer diese ADR annimmt.**

**Bezug:** [TDS-RB-05](../../../spec/lastenheft.md#tds-rb-05--lizenz), [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [ADR-0047](0047-veroeffentlichung-ueber-github-packages.md) (Alternative 5b und D), [ADR-0048](0048-veroeffentlichung-in-der-ci-bei-tag-push.md), [ADR-0049](0049-cloudsmith-als-zweites-veroeffentlichungsziel.md), [ADR-0022](0022-e2e-laeufe-nicht-in-den-gates.md), [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md), [ADR-0018](0018-laufzeit-artefakt.md) (Abschnitt „Offen“), [AGENTS.md](../../../AGENTS.md) §3.1, §3.5, §3.6, §3.8

**Schärft:** — (keine Spec-Stelle; Veröffentlichen ist ein Werkzeug außerhalb der Gates, wie in [ADR-0048](0048-veroeffentlichung-in-der-ci-bei-tag-push.md))

**Supersedes:** keines. Keine `Accepted`-ADR wird geändert ([AGENTS.md](../../../AGENTS.md) §3.5). [ADR-0047](0047-veroeffentlichung-ueber-github-packages.md) nennt „5b — zusätzlich ein GitHub Release mit Jar und `lib/`“ und „D“ als nicht gewählte, später mögliche Zusätze; diese ADR **führt 5b aus**, sie ersetzt keine Wahl. [ADR-0048](0048-veroeffentlichung-in-der-ci-bei-tag-push.md) sagt „der Workflow veröffentlicht Pakete“ und nennt kein Release; das ist keine Aussage „kein Release“, ein Widerspruch besteht nicht. Die Rechte-Minimierung aus [ADR-0048](0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Entscheidung 2 und [ADR-0049](0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) Entscheidung 3 (`packages: write` nur in `publish`, Cloudsmith-Secrets nur im Schritt) bleibt unberührt; der neue Job kommt hinzu (Vorschlag in Entscheidung 1).

**Regeln:** Baseline-Regelwerk `modul-04-adrs.md` §Ziel-Form: ADR (MADR).

---

## Kontext

**Ausgangslage.** Die Seite „Releases“ des Repositories `pt9912/tdswire` ist leer: der Workflow `.github/workflows/veroeffentlichung.yml` veröffentlicht nur Pakete (GitHub Packages, Cloudsmith) und legt nie ein GitHub Release an. Die Tags `v0.1.0` und `v0.1.1` bestehen ([`releasing.md`](../../maintainer/releasing.md) Schritt 8). Ein Weg für `tdswire.jar` und `lib/` ohne Registry besteht nicht; [`releasing.md`](../../maintainer/releasing.md) führt ihn unter „Offene Entscheidungen“ als zurückgestellt, nach der ersten Veröffentlichung neu zu bewerten und damit fällig (Nutzer, 2026-11-14), und Schritt 6 hält fest: `make build` schreibt das Jar bewusst nicht auf den Host.

**Entscheidungen des Nutzers (Quelle: Antworten vom 2026-11-16, im Auftrag an den Architect).** Diese drei sind entschieden, nicht Vorschlag:

- **N1 — Weg.** Der Workflow wird erweitert, sodass **jeder Tag-Lauf ein GitHub Release anlegt** (nicht nur nachträglich von Hand).
- **N2 — Dateien.** Das Release trägt **`tdswire.jar` und `lib/` als Anhänge**. Das ist zugleich der Weg „Jar und `lib/` ohne Registry“ aus [`releasing.md`](../../maintainer/releasing.md) §Offene Entscheidungen; die Neubewertung der Alternativen 5b und D ([ADR-0047](0047-veroeffentlichung-ueber-github-packages.md) Entscheidung 5 und Alternativen-Tabelle) fällt damit zugunsten von 5b aus.
- **N3 — Bestand.** Die vorhandenen Tags `v0.1.0` und `v0.1.1` bekommen nachträglich per `gh` **je ein Release ohne Dateien**; wann, regelt der Slice (nach der Lieferung, damit sie dem neuen Format entsprechen).

**Was Beleg ist und was nicht** ([AGENTS.md](../../../AGENTS.md) §3.8). *Gelesen am 2026-11-16, nichts ausgeführt, nichts gegen GitHub abgesetzt:* der Workflow (Jobs `gates`, `publish`, `publish-cloudsmith`, `vorschau`; Rechte je Job; Bedingung `github.event_name == 'push' && github.ref_type == 'tag'`), die Stage `build-jar` im `Dockerfile` (`gradle :assemble`, Prüfung `META-INF/LICENSE`, POM, `tools/manifest-pruefung.sh build/libs …`), `build.gradle.kts` (Laufzeit-Bibliotheken nach `build/libs/lib`, `Class-Path` mit `lib/<Name>`), [`releasing.md`](../../maintainer/releasing.md), die Messprotokolle `docs/messungen/veroeffentlichung-*.md`. **Aus den Protokollen belegt:** die Tag-Läufe 43699125460 (`v0.1.0`) und 43734101990 (`v0.1.1`); bei „Re-run failed jobs“ lief im Lauf von `0.1.1` nur der rote Job neu (aus Job-Zeiten und Protokoll geschlossen, nicht an der Oberfläche beobachtet). **Nicht gemessen, nur nach Dokumentationsstand oder Setzung:** alles, was unten über das Verhalten von GitHub Releases, `gh` und `GITHUB_TOKEN` mit `contents: write` steht; jede solche Stelle steht als Mess-Pflicht in Entscheidung 7.

**Annahme, die kippt:** dass `GITHUB_TOKEN` mit `contents: write` ein Release samt Anhängen für einen bestehenden Tag anlegen darf. Kippt sie, ist ein persönliches Token der Rückfall (wie 6a in [ADR-0048](0048-veroeffentlichung-in-der-ci-bei-tag-push.md)) und eine neue Entscheidung.

## Entscheidung

**Entschieden (Nutzer):** N1, N2, N3 aus dem Kontext. **Vorgeschlagen (Architect, unbestätigt):** die Entscheidungen 1 bis 7; je Punkt eine Tabelle, **fett** ist der Vorschlag. Der Nutzer entscheidet jeden Punkt.

### 1. Job-Zuschnitt (Vorschlag)

| Option | Pro | Contra |
|---|---|---|
| **1a — eigener Job `release`, `needs: [publish, publish-cloudsmith]`, gleiche Bedingung (Tag-Push), `permissions: contents: write` nur dort; Prüfung „Tag-Commit liegt in `origin/main`“ wie in den beiden anderen Jobs** | das Release erscheint erst, wenn beide Pakete oben sind (Notiz und Pakete stimmen überein); `contents: write` kommt nur in einen Job, der weder `packages: write` noch ein Cloudsmith-Secret trägt; die beiden Veröffentlichungs-Jobs bleiben unabhängig voneinander ([ADR-0049](0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) Entscheidung 2a); ein roter `release` verbraucht keine Registry-Version | ein Teilfehler eines Pakets hält das Release zurück (die Dateien wären ladbar, das Release fehlt); ein dritter Job nach den Registries |
| 1b — Job `release` mit `needs: gates` allein | Release und Dateien unabhängig vom Zustand der Registries; kürzere Laufzeit | die Notiz verlinkt Pakete, die noch nicht oben sein müssen; ein Release für eine halb veröffentlichte Version sieht fertiger aus, als sie ist |
| 1c — Schritt `gh release create` im Job `publish` | kein neuer Job | `publish` bekäme `contents: write` zusätzlich zu `packages: write`: größere Rechte in dem Job, der schon das Schreib-Token trägt; bricht die Rechte-Minimierung von [ADR-0048](0048-veroeffentlichung-in-der-ci-bei-tag-push.md) |
| 1d — nichts tun | kein Schreibrecht auf Inhalte | erfüllt N1 nicht |

**Rechte.** Neu im Repo ist das Recht `contents: write` (Releases anlegen). Es steht nur im Job `release`. Das Token steht nur im `env:` des einen Schritts `gh release create`; Docker-Bau, Prüfungen und Prüfsummen laufen in Schritten **davor ohne dieses Token im `env:`** (der Checkout bleibt `persist-credentials: false`). Dass `gh` auf dem Runner-Image vorhanden ist, ist nach Dokumentationsstand angenommen, nicht gemessen; eine zusätzliche Action ist damit nicht nötig. **Restrisiko (benannt, nicht aufgelöst):** wer den Workflow auf einem Tag ändern und den Tag nach `main` pushen kann, gewinnt mit `contents: write` zusätzlich die Möglichkeit, Releases anzulegen oder zu ändern. Das ist dieselbe Schutzlinie wie bei den Secrets: der Tag-Schutz `v*` ([`releasing.md`](../../maintainer/releasing.md) §Tag-Schutz), weiter Handlung des Nutzers ohne Sensor; das Release erweitert seine Bedeutung, nicht seine Form.

**Teilfehler und „Re-run failed jobs“ (Vorschlag).** Ein rotes Paket lässt `release` übersprungen. Wiederholt der Maintainer nur den roten Job (Weg nach [`releasing.md`](../../maintainer/releasing.md) Schritt 9), läuft nach dessen Erfolg `release` im selben Lauf nach (nach Dokumentationsstand werden abhängige, übersprungene Jobs mit neu ausgeführt; **ungemessen**). Ist `release` selbst rot (Pakete oben, Release fehlt), wiederholt der Maintainer allein `release`; der Job ist dafür **wiederholbar**: er prüft vor dem Anlegen, ob zum Tag schon ein Release besteht, und bricht dann mit klarer Meldung ab, ohne zu überschreiben (kein Löschen, kein `--clobber`). Ein halb angelegtes Release (Release da, Anhänge fehlen) soll dadurch vermieden werden, dass Release und Anhänge in **einem** Aufruf `gh release create <Tag> <Dateien…>` entstehen und nicht zuerst als Entwurf; ob das atomar ist, ist ungemessen (Mess-Pflicht 6). Bleibt dennoch ein Teilbestand, entscheidet der Nutzer, wie bei Paketen.

### 2. Herkunft der Dateien (Vorschlag)

Zwang: Docker-only ([AGENTS.md](../../../AGENTS.md) §3.1); das Jar auf dem Runner kommt nur aus dem Container.

| Option | Pro | Contra |
|---|---|---|
| **2a — neue Stage und neues Target `make release-dateien` (Dockerfile: `FROM build-jar`, Prüfung und Archivierung im Container, Export mit `--output type=local,dest=<Verzeichnis>` wie bei `lock-export`); das Verzeichnis liegt nur auf dem Runner, der Job gibt es an `gh release create`** | folgt dem vorhandenen Muster `lock-export` (nichts gemountet, kein `build/` auf dem Host); `make build` bleibt unverändert und schreibt weiter nichts auf den Host ([`releasing.md`](../../maintainer/releasing.md) Schritt 6); die Prüfungen der Stage `build-jar` stehen im Vorgänger und laufen mit | neue Stage, neues Target, Eingang der Klasse `werkzeug` (`tools/stempel/werkzeug.eingaenge`); Zeile in [`harness/README.md`](../../../harness/README.md) |
| 2b — Jar aus dem Maven-Artefakt der Registry laden | kein neuer Bau | bindet das Release an die Erreichbarkeit der Registry; `lib/` gibt es dort nicht (POM statt `lib/`, [ADR-0047](0047-veroeffentlichung-ueber-github-packages.md)) |
| 2c — `docker create` und `docker cp` auf dem Runner aus dem Image | kein `--output` | zweiter Weg neben `lock-export`; braucht Image-Namen und Aufräumen |

**Gleiches Jar wie im Paket?** Das Release baut das Jar ein weiteres Mal (Stage `build-jar`), die Pakete bauen es in der Stage `publish` (`gradle :jar`). Ob beide Bytes gleich sind, ist **nicht gemessen**. Folge für die Notiz (Entscheidung 4): sie nennt die Prüfsumme **des angehängten** Jars und behauptet keine Gleichheit mit dem Paket-Jar, bis gemessen ist (Mess-Pflicht 4).

**Archivform (Vorschlag):**

| Option | Pro | Contra |
|---|---|---|
| **A1 — ein ZIP `tdswire-<Version>.zip` mit `tdswire.jar`, `lib/`, `LICENSE`; dazu `tdswire.jar` einzeln und `SHA256SUMS`** | das Manifest führt `Class-Path` mit `lib/<Name>`: `tdswire.jar` und `lib/` müssen als Baum nebeneinander liegen, und das trägt nur ein Archiv; Anhänge einer Release-Seite sind flach, `lib/` kann dort kein Verzeichnis sein; die Einzeldatei gibt dem Konsumenten, der nur das Jar will, den direkten Anhang | Bytes des Jars doppelt (im ZIP und einzeln); eine Prüfsumme je Datei |
| A2 — nur einzelne Dateien (`tdswire.jar` und jedes Jar aus `lib/`) | keine Archivform zu erklären | der Konsument baut `lib/` selbst auf, sonst bricht der `Class-Path`; viele Anhänge |
| A3 — nur das ZIP | keine Dopplung | N2 nennt `tdswire.jar` und `lib/` als Anhänge; das ZIP trägt beides, aber das Jar ist nicht direkt ladbar |

**Prüfsummen.** `SHA256SUMS` (Format `sha256sum`) nennt jede Anhangsdatei außer sich selbst. Es entsteht im Container und wird im Job vor dem Anlegen gegen die exportierten Dateien geprüft (`sha256sum -c`; im Container oder auf dem Runner, ohne zusätzliche Hostabhängigkeit bevorzugt im Container; Wahl beim Slice, die Prüfung selbst ist Pflicht). Keine Signatur (wie in [ADR-0047](0047-veroeffentlichung-ueber-github-packages.md) Entscheidung 4 für die Pakete): die Prüfsumme belegt Unverändertheit gegenüber der Notiz auf derselben Seite, **nicht** Herkunft; die Notiz sagt das (Entscheidung 4).

**Dieselben Prüfungen auf dem angehängten Artefakt (Vorschlag).** Der Container entpackt das fertige ZIP in ein leeres Verzeichnis und führt darauf `tools/manifest-pruefung.sh` aus (keine `Main-Class`, `Class-Path` nur gegen vorhandene Einträge `lib/<Name>`, kein Jackson in `lib/`, kein Testserver im Jar) sowie die Prüfung `META-INF/LICENSE` im Jar gleich `LICENSE`. Die Prüfung läuft am **Archivinhalt**, nicht am Baum davor; sonst bliebe der Schritt „Archivieren“ ungeprüft. Das Skript verlangt heute eine POM als zweites Argument; ob der Slice es um einen Modus ohne POM erweitert oder die POM der Stage mitgibt, entscheidet der Slice (eine Änderung am Skript ist Eingang der Klasse `werkzeug` samt Selbsttest). Der Job bricht außerdem, wenn der Tag nicht `v<Version aus build.gradle.kts>` ist (Muster von `tools/publish.sh`); der Dateiname des ZIP trägt diese Version.

### 3. Lizenzfrage (offene Frage, keine Entscheidung)

Die Pakete tragen nur das Jar der Bibliothek und eine POM mit Abhängigkeiten ([ADR-0047](0047-veroeffentlichung-ueber-github-packages.md): POM statt `lib/`); der Konsument holt die fremden Bibliotheken bei ihrer eigenen Quelle. **Ein öffentlicher Anhang von `lib/` ist neu:** das Projekt gibt fremde Binärdateien selbst weiter. [ADR-0018](0018-laufzeit-artefakt.md) §Offen führt „Lizenzhinweise der gelieferten Bibliotheken“ als offene Rechtsfrage ohne Prüfung im Repo (Register `BEO-TDS/lizenzhinweise-der-bibliotheken-rechtsfrage`); [`releasing.md`](../../maintainer/releasing.md) Schritt 7 behauptet dazu nichts.

Gelesen (`gradle.lockfile`, nichts ausgeführt): zur Laufzeit gehören nach dem Lockfile unter anderem `kotlin-stdlib`, `kotlinx-coroutines-core-jvm` und `org.jetbrains:annotations` zur Konfiguration `runtimeClasspath`; die genaue Liste von `lib/` ist weder gebaut noch gelesen. **Die Lizenz dieser Bibliotheken ist hier nicht geprüft**, und welche Bedingungen sie an die Weitergabe knüpfen (Lizenztext, Hinweisdateien), ist nicht gelesen. Der Architect gibt dazu **keine** Rechtsauskunft und entscheidet nichts.

**Was der Nutzer klären muss, bevor der erste Anhang `lib/` öffentlich wird:** ob die Weitergabe so zulässig ist und welche Hinweise das ZIP führen muss. Optionen zur Entscheidung des Nutzers, ohne Aussage zur Rechtslage: (L1) `lib/` im ZIP samt den Lizenzdateien, soweit die Jars sie führen (die Prüfung der Jars darauf ist dann Teil des Slice); (L2) das Release trägt nur `tdswire.jar` und die Koordinaten der Abhängigkeiten mit Prüfsummen (löst N2 nicht ein; der Nutzer müsste N2 ändern); (L3) Anhang `lib/` erst nach der Klärung. Als Zwischenform ist ein Release ohne `lib/` möglich (Frage 3). Der Slice liefert den Anhang `lib/` nicht vor der Antwort des Nutzers.

### 4. Inhalt der Notiz (Vorschlag)

| Option | Pro | Contra |
|---|---|---|
| **4a — feste Notiz aus einer Vorlagedatei, gefüllt mit Version, Tag, Commit, Koordinaten, Links auf beide Pakete, Prüfsummen von ZIP und Jar, dem Hinweis „Prüfsumme belegt Unverändertheit, nicht Herkunft“, Lizenz MIT mit Verweis auf `LICENSE` und einem Zeiger auf die [Einbettungs-Anleitung](../../user/einbettung-und-betrieb.md); Linkziele der Pakete aus einer belegten Quelle, nicht aus dem Gedächtnis** | Inhalt bekannt und prüfbar; keine Behauptung über Gleichheit mit dem Paket-Jar; passt zu einem Repo, das auf `main` mit Commits statt Pull Requests arbeitet (Lesart des Verlaufs, nicht gezählt) | eine Vorlagedatei zu pflegen |
| 4b — `--generate-notes` | keine Vorlage | der Text ist eine Commit- oder PR-Liste ohne Aussage über Pakete und Prüfsummen; sein Inhalt steht nicht im Repo und ist ungeprüft |
| 4c — feste Notiz, darunter `--generate-notes` | vollständigste Seite | zwei Quellen, der erzeugte Teil ungeprüft |

Die Notiz trägt **keine** Aussage, die nicht gemessen ist ([AGENTS.md](../../../AGENTS.md) §3.8): kein „anonym ladbar“, solange Mess-Pflicht 1 offen ist, keine Gleichheit der Jars (Mess-Pflicht 4), keine Aussage zu den Lizenzen von `lib/` (Entscheidung 3). Wo das Linkziel eines Pakets nicht belegt ist, steht der Koordinatentext ohne Link.

### 5. Nachträgliche Releases für `v0.1.0` und `v0.1.1` (N3; Form und Zeitpunkt Vorschlag)

**Form (Vorschlag):** je ein Release über `gh release create v0.1.0` und `v0.1.1` auf den vorhandenen Tag (`--verify-tag`, damit kein Tag neu entsteht), Titel `0.1.0` bzw. `0.1.1`, **ohne Anhänge**, Notiz nach der Vorlage von Entscheidung 4 ohne Dateizeilen und mit einem Satz, dass für diese Version keine Dateien anliegen und die Version in GitHub Packages (für `0.1.1` auch in Cloudsmith) steht. **Grund für kein Anhängen:** die Jars der alten Tags müssten aus den Tags neu gebaut werden; ob sie den damals veröffentlichten Paket-Jars gleichen, ist nicht gemessen (Mess-Pflicht 4), und ein neu gebautes Jar an einer alten Version wäre eine Behauptung ohne Beleg. N3 bleibt damit, wie der Nutzer sie entschieden hat (Frage 6). **Zeitpunkt (Vorschlag):** nach der Lieferung des Workflows und nach dem ersten Tag-Lauf mit Release, damit Form und Notiz dem neuen Format entsprechen (N3). **Handlung des Nutzers oder des Maintainers, kein Workflow-Lauf;** es entsteht keine Version, nur die Release-Seite zu einer. Sie hat Außenwirkung und läuft unter ausdrücklicher Bestätigung des Nutzers.

### 6. Einordnung (Vorschlag, Folge aus ADR-0022 und ADR-0048)

Der Job ist **kein Gate**: `make release-dateien` steht in keiner Liste `GATE_CHECKS`, `GATE_KLASSE_*` oder `GATE_STAGE` und nicht in `make e2e`; der Workflow ist kein Eingang von `make gates` ([ADR-0022](0022-e2e-laeufe-nicht-in-den-gates.md), [ADR-0048](0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Entscheidung 3). Keine Schwelle sinkt, keine Regel wird gelockert; [AGENTS.md](../../../AGENTS.md) §3.6 ist nicht berührt. Der **Dispatch-Lauf** (`workflow_dispatch`) legt kein Release an; er kann `make release-dateien` als Vorschau fahren (Bau, Prüfungen, Prüfsummen, ohne Token und ohne `gh`), wie `vorschau` für die Pakete. Damit ist alles außer dem Anlegen selbst vor dem ersten Tag messbar, ohne eine Version zu verbrauchen. **Akzeptiertes Negativ, einmalig:** das Anlegen des Releases ist bis zum ersten echten Tag ungemessen; Grund: ein Probe-Release wäre Außenwirkung auf der öffentlichen Seite, und ein fehlgeschlagenes Anlegen verbraucht keine Registry-Version (das Release lässt sich von Hand nachholen).

### 7. Mess-Pflicht des Slice (nichts davon ist heute gemessen)

Der Slice misst und protokolliert in `docs/messungen/` (Datum, Lauf-Kennung, Ausgang; kein Token). Bis dahin steht keiner dieser Punkte als Zusage in einer Anleitung oder in der Notiz:

1. **Anonymer Abruf.** Ob die Anhänge eines Releases eines **öffentlichen** Repositories ohne Anmeldung ladbar sind (`curl` ohne Header gegen die Download-URL, Statuscode); [ADR-0047](0047-veroeffentlichung-ueber-github-packages.md) nennt genau das „nicht geprüft“.
2. **Rechte.** Ob `GITHUB_TOKEN` mit `contents: write` im Job `release` ein Release samt Anhängen für einen bestehenden Tag anlegt, und ob die Einstellung „Workflow permissions“ des Repositories das Recht der Datei überstimmt.
3. **Ablauf bei Teilfehler.** Ob nach „Re-run failed jobs“ eines roten Pakets `release` im selben Lauf nachläuft, und was ein Re-run des Jobs `release` bei bestehendem Release tut (Abbruch der Vorabprüfung).
4. **Gleichheit der Jars.** Ob `tdswire.jar` des Releases und das Jar im Paket bytegleich sind (Abgleich gegen den anonymen Abruf bei Cloudsmith); davon hängt ab, ob die Notiz einen Satz zur Gleichheit tragen darf.
5. **Prüfungen am Archiv.** Dass `tools/manifest-pruefung.sh` und die Lizenzprüfung auf dem **entpackten ZIP** laufen und rot werden, wenn `lib/` einen Jackson-Eintrag, das Manifest eine `Main-Class` oder das Jar eine andere `LICENSE` führt (je eine Mutation des Eingangs).
6. **Anlegen in einem Aufruf.** Ob ein Aufruf `gh release create <Tag> <Dateien…>` bei Abbruch ein Release ohne Anhänge hinterlässt, und ob das Repository unveränderliche Releases führt (Einstellung) und was sie für ein spät angehängtes Asset bedeutet.
7. **Laufzeit.** Dauer des Jobs `release` und Wirkung auf den Gesamtlauf (bisher drei Jobs nach `gates`).

## Verglichene Alternativen

Die Teilwahlen stehen in den Tabellen von Entscheidung 1, 2 und 4. Für das Ganze (N1 und N2 sind Vorgabe des Nutzers; die Tabelle zeigt die Wirkung):

| Option | Pro | Contra |
|---|---|---|
| A — nichts tun | kein Schreibrecht, keine Rechtsfrage zu `lib/` | Release-Seite leer; kein Weg ohne Registry; N1 und N2 nicht erfüllt |
| B — Releases nur nachträglich von Hand, mit Dateien | keine Workflow-Änderung, kein `contents: write` in der CI | Handarbeit je Version, Dateien nicht aus dem Lauf hinter den Gates; widerspricht N1 |
| **C — der Workflow legt je Tag-Lauf ein Release an (N1), mit Anhängen (N2)** | gleiche Quelle wie die Pakete, Gates vorher, ein Weg | neues Recht, neue Stage, Rechtsfrage zu `lib/` (Entscheidung 3) |
| D — Release ohne `lib/` (nur `tdswire.jar`, Koordinaten der Abhängigkeiten) | umgeht die Weitergabe fremder Binärdateien; kleiner | löst N2 nicht ein (Nutzer müsste ändern); der Handweg mit `Class-Path` braucht `lib/` doch |

## Konsequenzen

- Positiv: ein Weg für Jar und `lib/` ohne Registry (die fällige Neubewertung ist beantwortet); die Release-Seite zeigt jede veröffentlichte Version; Prüfsummen auf derselben Seite; Wiederverwendung des Musters `lock-export` und der Prüfungen der Stage `build-jar`.
- Negativ: neues Schreibrecht (`contents: write`) und damit größerer Schaden bei einem Angriff über den Tag; ein dritter Job und eine neue Stage; die Rechtsfrage zu `lib/` wird dringlich, weil das Projekt erstmals fremde Binärdateien selbst weitergibt; ein Teilbestand beim Anlegen ist ein weiterer möglicher Zustand.
- Folgepflicht nach `Accepted` (Planner, ein Slice; Pläne schreibt diese ADR nicht um): Workflow-Job `release`, Stage und Target `make release-dateien`, Vorlage der Notiz, Prüfungen am Archiv mit Mutation, Nachzug von [`releasing.md`](../../maintainer/releasing.md) (Schritt 6 „Ein Weg … besteht nicht“, Schritt 8 und 9, „Offene Entscheidungen“, Tag-Schutz mit dem Recht `contents: write`) und der Zeilen in [`harness/README.md`](../../../harness/README.md) §Sensors (Target, Workflow); Mess-Pflichten nach Entscheidung 7 mit Messprotokoll; die nachträglichen Releases nach Entscheidung 5. Die Freigabe von `lib/` (Entscheidung 3) hängt an der Antwort des Nutzers.
- Vom Nutzer außerhalb des Repos: der Tag-Schutz `v*` bleibt die tragende Kontrolle (Handlung des Nutzers, ohne Sensor); die Einstellung „Workflow permissions“ des Repositories ist ungemessen.
- **Akzeptiertes Negativ (mit Grund):** das Release trägt keine Signatur; Grund: wie bei den Paketen ([ADR-0047](0047-veroeffentlichung-ueber-github-packages.md) Entscheidung 4). Das einmalige Negativ „Anlegen vor dem ersten Tag ungemessen“ steht in Entscheidung 6.

## Fitness Function (falls maschinell prüfbar)

| Tooling | Regel | Make-Target |
|---|---|---|
| `actionlint` im Container | `veroeffentlichung.yml` ist syntaktisch gültig | `make workflow-lint` (**kein Gate**, [`harness/README.md`](../../../harness/README.md) §Sensors) |
| `tools/manifest-pruefung.sh` (Eingang der Klasse `werkzeug`, mit Selbsttest, wenn der Slice es erweitert) | `lib/` ohne Jackson, Manifest ohne `Main-Class` und `Class-Path` gegen vorhandene Einträge, am entpackten Archiv | in der Stage von `make release-dateien`; Selbsttest in `make skript-selbsttest` |
| Review | `contents: write` nur im Job `release`; `needs: [publish, publish-cloudsmith]`; Token nur im Schritt `gh release create`; Actions per SHA; `release-dateien` in keiner Gate-Liste | nur Absicht, im Review (kein Sensor: `make workflow-lint` prüft weder Rechte noch Reihenfolge) |

## Re-Evaluierungs-Trigger

Die Antwort des Nutzers auf die Rechtsfrage zu `lib/` (dann Entscheidung 3 und gegebenenfalls Archivinhalt); der Befund, dass Anhänge nicht anonym ladbar sind (Mess-Pflicht 1; dann entfällt der Zweck „ohne Registry“, neue Entscheidung); der Befund, dass `GITHUB_TOKEN` das Release nicht anlegen darf (dann Rückfall oder neue ADR); die Messung, dass Release-Jar und Paket-Jar verschieden sind (dann Notiz und gegebenenfalls ein gemeinsamer Bau); der Entschluss für Maven Central oder eine Signierung ([ADR-0047](0047-veroeffentlichung-ueber-github-packages.md) Entscheidung 4).

## Offene Fragen an den Nutzer

1. Job-Zuschnitt: `release` nach beiden Paketen (Vorschlag 1a) oder nur nach `gates` (1b)?
2. Archivform: ZIP mit Jar und `lib/` plus einzelnes Jar plus `SHA256SUMS` (A1), nur ZIP (A3) oder einzelne Dateien (A2)?
3. Lizenz von `lib/`: darf `lib/` öffentlich als Anhang liegen, und welche Hinweise gehören ins ZIP (L1 bis L3 in Entscheidung 3)? Darf der Slice, solange die Antwort fehlt, ein Release ohne `lib/` liefern?
4. Notiz: fest nach Vorlage (4a), `--generate-notes` (4b) oder beides (4c)?
5. Nachträgliche Releases: einverstanden mit „ohne Dateien, Notiz mit Hinweis, nach der ersten Lieferung des neuen Formats, unter ausdrücklicher Bestätigung“ (Entscheidung 5)?
6. Sollen `0.1.0` und `0.1.1` die Anhänge doch nachträglich tragen, falls Mess-Pflicht 4 gleiche Bytes belegt? Vorschlag: nein, N3 bleibt.

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-11-16 | Proposed: drei Entscheidungen des Nutzers (N1 bis N3), Gestaltungspunkte als Vorschlag, Rechtsfrage zu `lib/` offen | — |

Nach `Accepted` wird diese Datei **nicht mehr inhaltlich überschrieben**.
Spätere Korrekturen oder Schärfungen entstehen als neue ADR mit
`Supersedes ADR-NNNN` (Baseline-Regelwerk `modul-04-adrs.md`
§Hard Rule für Accepted-ADRs).
