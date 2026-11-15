# Slice slice-cloudsmith-veroeffentlichung-0-1-1: Version 0.1.1 auf GitHub Packages und Cloudsmith durch den Workflow bei Tag-Push, Konsum ohne und mit Token gemessen

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** ohne Welle — keine Closure-Bedingung, die von der DoD dieses Slice verschieden ist.

**Bezug:** [TDS-RB-01](../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache) (Zielbild-Prüfung: Konsum aus der Registry), [TDS-RB-05](../../../../spec/lastenheft.md#tds-rb-05--lizenz), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) (Scope), [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) (`Accepted` 2026-11-15, Folgepflicht 2: Release `0.1.1` mit Messprotokoll; Entscheidungen 2, 4a und 5), [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) (Entscheidung 3a: eine veröffentlichte Version wird nie neu veröffentlicht), [ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) (Tag-Push als Handlung des Nutzers)

**Berührte Spec-Stellen:** — (`spec/` nennt kein Veröffentlichungsziel; [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) hat das am 2026-11-15 per `grep` gelesen). SPEC-052 · SPEC-059 nur, wenn die Messung eine Aussage widerlegt.

**Verantwortlich:** pt9912 (Anspruch 2026-11-15).

**Bestätigung des Nutzers:** am 2026-11-15 auf die Frage, ob der Slice beansprucht und der Release-Commit für Version `0.1.1` vorbereitet werden soll: „Ja, 0.1.1 jetzt“ (Version `0.1.1`, Zeitpunkt „so bald als möglich“). Das bestätigt Version und Zeitpunkt der Vorbereitung; die Handlung selbst ist der Tag-Push des Nutzers ([ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Entscheidung 1). Die Voraussetzungen aus §4, die kein Agent sieht (Schreibrecht und Gültigkeit des Schlüssels, Gegenprobe des Tag-Schutzes), sind damit nicht bestätigt und vor dem Tag-Push Sache des Nutzers.

**Autor:** Planner-Agent, Auftrag des Nutzers 2026-11-15 nach [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md). **Datum:** 2026-11-15.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Die Version `0.1.1` der Bibliothek liegt auf GitHub Packages und auf Cloudsmith, entstanden aus **einem** Tag-Push des Nutzers und dem Workflow aus [slice-cloudsmith-ziel-bauen](../done/slice-cloudsmith-ziel-bauen.md); der Konsum ist gemessen, anonym über Cloudsmith und mit einem Lese-Token über GitHub Packages, und die Nutzer-Doku führt beide Wege im Ist-Zustand.

**Warum ein eigener Slice:** Die Handlung schreibt zweimal nach außen und ist nicht rücknehmbar; die Bestätigung ist der Tag-Push des Nutzers ([ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Entscheidung 1, [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) Entscheidung 4a), Start-Bedingung (§4), kein Liefer-Punkt. Die Vorbereitung (Werkzeug, Workflow, Probe-Konsument, ohne Wirkung nach außen belegt) liefert [slice-cloudsmith-ziel-bauen](../done/slice-cloudsmith-ziel-bauen.md).

**Aus dem Plan von [slice-maven-erste-veroeffentlichung](../done/slice-maven-erste-veroeffentlichung.md) übernommen (Neuschnitt 2026-11-15, kein `Übernimmt:` im Sinn des Regelwerks: der alte Slice hat geliefert und schließt mit eigener Closure, die Zeile entfällt deshalb):** der Konsum aus der Registry (dort Liefer-Punkt 2), die Dateiliste der Version in der Registry (dort Teil von Liefer-Punkt 1: im [Messprotokoll des Tag-Laufs](../../../messungen/veroeffentlichung-tag-lauf-2026-11-15.md) ungemessen) und Nutzer-Doku samt Release-Anleitung (dort Liefer-Punkt 3), soweit [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) sie ändert. Der Konsum mit Token bleibt als zweiter Weg erhalten, der anonyme Weg über Cloudsmith kommt als erster hinzu.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Werkzeug, Workflow-Job, Zielschalter, Probe-Konsument — [slice-cloudsmith-ziel-bauen](../done/slice-cloudsmith-ziel-bauen.md). Dieser Slice benutzt sie und ändert sie nicht; ein Befund an ihnen ist ein Rückweg dorthin, kein Nebenbei.
- Ein Aufruf von `make publish` oder des Cloudsmith-Ziels von Hand mit einem Schlüssel, ein Schlüssel auf einem Rechner, `0.1.0` von Hand nach Cloudsmith (Option 4b): [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) Entscheidung 4a und [ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md); `0.1.0` bleibt nur in GitHub Packages und wird nie neu veröffentlicht ([ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) Entscheidung 3a).
- Ein zweites Veröffentlichen derselben Version an einem Ziel, das sie trägt, und ein Löschen: nicht vorgesehen; ob eine Löschung die Version wieder frei macht, ist ungemessen und bei Eintritt eine Entscheidung des Nutzers ([ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) Entscheidung 2).
- Eine Änderung der `.module`-Datei (`GenerateModuleMetadata` abschalten): eigene Entscheidung, falls die Messung (zweiter Punkt) sie verlangt; sie wird hier nicht vorweggenommen ([ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) Entscheidung 3).
- Ein Java- oder Maven-Konsument: von [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) als akzeptiertes Negativ geführt; der Probe-Konsument ist Kotlin und Gradle.
- Signierung, Maven Central (5c), OIDC (3b): Re-Evaluierungs-Trigger in [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md), keiner eingetreten.
- Eine Änderung des Lastenhefts: Entscheidung des Nutzers 2026-11-15, [TDS-RB-01](../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache) Nachweis bleibt unverändert, die Lücke bleibt benannt.
- Der Tag-Schutz `v*` und das Anlegen von Repository, Service und Secrets: Handlung des Betreibers; hier Start-Bedingung (§4).

**Zielbild-Prüfung** (Maßstab: das Zielbild führt, Code folgt; „Zielbild anpassen“ nur mit Entscheidung des Nutzers):

| Stelle | Code angleichen | Zielbild anpassen | Entscheid |
|---|---|---|---|
| [TDS-RB-01](../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache) Nachweis (reproduzierbarer JVM-Build, Einbettungstest; „Konsum aus der Registry“ war als Lücke benannt, [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) Zielbild-Prüfung) | Probe-Konsument gegen Cloudsmith und GitHub Packages (zweiter Punkt) | Nachweis um „Konsum aus der Registry“ ergänzen (Lastenheft, vertraglich: ändert nur der Nutzer) | **Code angleichen; Lastenheft unverändert, Lücke bleibt benannt** (Nutzer, 2026-11-15) |
| Ziel von [slice-maven-erste-veroeffentlichung](../done/slice-maven-erste-veroeffentlichung.md): „Konsum mit `read:packages`-Token“ als einziger Weg der Konsumenten; Risiko „Konsumenten ohne Konto oder Token können die Bibliothek nicht binden“ | der Konsum mit Token bleibt gemessen (zweiter Punkt, zweiter Weg) | das Ziel ändert sich: erster Weg ist der anonyme über Cloudsmith, GitHub Packages mit Token der zweite ([ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) Entscheidung 1 und 5, ersetzt die alleinige Wahl 5a von [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md)) | **Zielbild angepasst** — in diesem Neuschnitt am Plan von [slice-maven-erste-veroeffentlichung](../done/slice-maven-erste-veroeffentlichung.md) nachgezogen und hier geführt; das Lastenheft ist nicht berührt |
| Nutzer-Anleitung in `docs/user/einbettung-und-betrieb.md` (heute ohne Registry-Setup) | Setup im Ist-Zustand nach dem Beleg, Reihenfolge nach [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) Entscheidung 5 | — | Code angleichen (dritter Punkt) |
| Liefer-Punkt 2 (b) dieses Slice und Closure-Trigger (1): Konsum aus GitHub Packages mit Token gemessen | Probe-Konsument mit Token des Nutzers | Teil (b) streichen | **Zielbild/DoD angepasst — Entscheidung des Nutzers 2026-11-15** („Gar nicht messen“); GitHub Packages bleibt als zweiter Weg ohne Konsummessung, die Nutzer-Doku sagt es; Lastenheft unverändert, kein Folge-Slice |
| [`releasing.md`](../../../maintainer/releasing.md) (Kopf „als Maven-Paket für GitHub Packages“, Schritt 9, Tag-Schutz, „Offene Entscheidungen“) | von „gebaut, nicht gelaufen“ ([slice-cloudsmith-ziel-bauen](../done/slice-cloudsmith-ziel-bauen.md)) auf „gelaufen“ heben, Teilfehler-Weg aufnehmen | — | Nachzug (dritter Punkt) |

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [x] **Veröffentlicht auf beiden Zielen:** Die Version `0.1.1` liegt in GitHub Packages und auf Cloudsmith unter `io.github.pt9912:tdswire`; der Release-Commit (Version ohne `-SNAPSHOT`, in `main`) trägt den Tag `v0.1.1`, den der Nutzer pusht, beide Veröffentlichungs-Jobs von `veroeffentlichung.yml` sind `success`, danach steht `build.gradle.kts` wieder auf einem `-SNAPSHOT`-Stand. Das Messprotokoll in `docs/messungen/` stammt aus dem Tag-Lauf: Datum, Lauf-Kennung, Ausgang und Dauer je Job, ohne Token und Schlüssel; es nennt, was der Lauf **belegt** (Schreibrecht des Schlüssels, Form des Benutzernamens, Maskierung des Schlüssels im Log, Name der Cloudsmith-Aufgabe im Lauf, Verhalten bei einem Teilfehler, falls er eintrat) und was ungemessen bleibt.
- [x] **Konsum aus der Registry gemessen, auf beiden Wegen (Teil (a) gemessen, Teil (b) nach Entscheidung des Nutzers entfallen, siehe Zeile unter diesem Punkt):** (a) *anonym, Cloudsmith:* der Abruf von POM und `maven-metadata.xml` am Download-Pfad von Cloudsmith ergibt ohne Zugangsdaten 200 (nach Sammelfenster und Verarbeitung; die Wartezeit steht im Protokoll), und der Probe-Konsument aus [slice-cloudsmith-ziel-bauen](../done/slice-cloudsmith-ziel-bauen.md) löst `io.github.pt9912:tdswire:0.1.1` ohne Zugangsdaten über `https://dl.cloudsmith.io/public/pt9912/tdswire/maven/` auf, mit `.module` und ohne, übersetzt und läuft; das Protokoll sagt, ob das Repository als „Open source“ öffentlich ausliefert (der anonyme 200 ist der Beleg, ein 404 vor der ersten Version war es nicht). (b) *mit Token, GitHub Packages:* der Probe-Konsument bindet die Version mit einem Token des Nutzers (`read:packages`), übersetzt und läuft; die Dateien der Version (Jar, POM, `.module`) sind aus der Registry gesehen, und mit demselben Token auch die von `0.1.0` (deren Dateiliste war ungemessen). Das Protokoll nennt Setup und Token-Art (das Token steht nicht in Datei, Image oder Commit-Nachricht) und was ungemessen bleibt (Java und Maven).

  **Entscheidung des Nutzers, 2026-11-15** (Antwort auf die Frage nach dem Token-Konsum aus GitHub Packages, Wortlaut: „Gar nicht messen“): Teil (b) ist **vom Nutzer 2026-11-15 nicht gewollt, ungemessen**. Das ist eine Anpassung von Zielbild und DoD durch den Nutzer, kein Befund des Implementers und keine Erfüllung: der Haken steht für Teil (a) gemessen und Teil (b) entfallen. GitHub Packages bleibt als zweiter Weg ohne Konsummessung dokumentiert (Nutzer-Doku und `releasing.md` sagen es offen); gemessen sind nur das 401 ohne Token und die Anlage der Version durch den Workflow. Kein Folge-Slice.
- [x] **Nutzer-Doku, README und Release-Anleitung im Ist-Zustand:** `docs/user/einbettung-und-betrieb.md` führt das Setup eines Konsumenten in der Reihenfolge nach [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) Entscheidung 5 (Cloudsmith ohne Konto mit Repository-Eintrag und Koordinaten, dann GitHub Packages mit Token `read:packages`, zusätzlich Maven Central für die Abhängigkeiten der Bibliothek, soweit gemessen); alle Dateien unter `docs/user/` sind komplett gegen den Stand nach dem Slice gelesen (`AGENTS.md` §5 Regel 5). Das `README.md` nennt Cloudsmith nach den Hosting-Regeln (Link auf `cloudsmith.com`, Hinweis auf kostenloses Hosting für Open Source). [`releasing.md`](../../../maintainer/releasing.md) führt Tag und Vermerk als gelaufen, Schritt 9 ohne den Vorbehalt „gegen einen Dummy-Server“, den Weg bei Teilfehler (Wiederholung des roten Jobs allein, vorherige Prüfung, ob das Ziel die Version trägt) und die Neubewertung der offenen Punkte.

Nachweis-Pflichten für alle drei Punkte:

- [x] `make gates` grün; bei Doku `make docs-check`, `make flaeche`, `make kopplung-zahlen` ([`harness/README.md`](../../../../harness/README.md) §Minimal agent workflow, Zeile „Doku oder Spec“); `make spec-abdeckung` nicht, `make clean-build` und `make e2e` nicht (kein Pfad unter `src/` oder `testserver/`).
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist (hier der dritte Punkt).
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben; **kein Zaehler wird gesetzt**. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

**Prüfung der Liefer-Punkte gegen Bauart und Werkzeug** (`/plan-welle` Schritt 6b): Frage 1 — das Messprotokoll ist ein Dokument; was der Lauf zeigt, steht im Laufprotokoll (`gh api …/logs`) und in der Antwort der Pakete-API, was Cloudsmith ausliefert, im anonymen `curl`. Das Werkzeug sieht die Dateiliste einer GitHub-Version nicht über die Pakete-API (im [Messprotokoll des Tag-Laufs](../../../messungen/veroeffentlichung-tag-lauf-2026-11-15.md) gemessen: keine Dateien in der Antwort); der Konsum mit Token ist der Weg, sie zu sehen, deshalb steht er im zweiten Punkt. Der Probe-Konsument existiert vor diesem Slice ([slice-cloudsmith-ziel-bauen](../done/slice-cloudsmith-ziel-bauen.md)). Frage 2 — die Bestätigung und der Tag-Push sind Handlungen des Nutzers und stehen im Start-Trigger, nicht in der DoD.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `build.gradle.kts` | update (zwei Commits) | Release-Commit setzt die Version ohne `-SNAPSHOT`; der Folge-Commit setzt `-SNAPSHOT` zurück ([ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) Entscheidung 3a) |
| `docs/messungen/` (Messprotokoll) | neu | Tag-Lauf mit zwei Jobs, anonymer Abruf, Konsum auf beiden Wegen; das [Messprotokoll des Tag-Laufs](../../../messungen/veroeffentlichung-tag-lauf-2026-11-15.md) bleibt wahr (es beschreibt `0.1.0`) und wird nicht geändert |
| `docs/user/einbettung-und-betrieb.md` und übrige Dateien unter `docs/user/` | update | Setup im Ist-Zustand, erst nach dem Beleg ([ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) Entscheidung 5) |
| `harness/README.md` | update | Zeilen der Sensors-Tabelle zu `make publish-cloudsmith` und zum Workflow sowie „Aktueller Lauf-Status“ auf den gemessenen Stand (Befund des Reviews, zweite Fundstelle) |
| `README.md` | update | Namensnennung nach den Hosting-Regeln von Cloudsmith (Setzung unten im Ablauf, wann) |
| `docs/maintainer/releasing.md` | update | Schritte als gelaufen; Teilfehler-Weg; Wording „gebaut, nicht gelaufen“ aus [slice-cloudsmith-ziel-bauen](../done/slice-cloudsmith-ziel-bauen.md) aufheben |
| `tools/publish-probe.sh` | unverändert | Erzeuger der Messzahlen aus [slice-cloudsmith-ziel-bauen](../done/slice-cloudsmith-ziel-bauen.md); ob er danach bleibt, steht in der Closure-Notiz (Nutzerentscheidung 2026-11-14: bleibt bis zur Closure von [slice-maven-erste-veroeffentlichung](../done/slice-maven-erste-veroeffentlichung.md); der Konsument-Modus wird hier gebraucht) |

**Ablauf:** (1) Vor dem Release-Commit prüft der Nutzer die Voraussetzungen, die kein Agent sieht (§4). (2) Der Implementer setzt die Version im Release-Commit ohne `-SNAPSHOT`; der Commit gelangt nach `main` (der Workflow bricht, wenn der Tag-Commit nicht in `origin/main` liegt). Die Namensnennung im `README.md` steht schon im Release-Commit (Setzung des Planners: sie sagt, woher das kostenlose Hosting kommt, und behauptet keine Verfügbarkeit; die Nutzer-Doku bleibt bis nach dem Beleg unverändert). (3) **Der Nutzer pusht den Tag `v0.1.1`; der Workflow läuft** (`gates`, danach beide Veröffentlichungs-Jobs unabhängig voneinander). Kein Agent führt `make publish` oder das Cloudsmith-Ziel aus, hält ein Schreib-Token oder einen Schlüssel oder pusht den Tag. (4) Der Nutzer gibt die Lauf-Kennung weiter; die Ausgabe jedes Jobs wird vor jedem Neustart gesichert. Anonyme Abrufe (`curl` ohne Token) darf ein Agent ausführen; der Konsum mit Token (zweiter Punkt b) läuft mit einem Token des Nutzers, das in keine Datei kommt. (5) **Teilfehler** ([ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) Entscheidung 2): ist genau ein Job rot, prüft der Maintainer zuerst, ob das Ziel die Version trägt (GitHub Packages: Abfrage mit Token oder Paketseite; Cloudsmith: anonymer Abruf der POM, mit Wartezeit); trägt es sie nicht, wiederholt der Nutzer allein den roten Job („Re-run failed jobs“, im Schwesterprojekt als Dokumentationsstand gelesen, hier nicht gemessen); bleibt ein Teilbestand, entscheidet der Nutzer (Löschen: ungemessen). Ein Re-Run eines grünen Jobs für eine veröffentlichte Version ist nicht vorgesehen. (6) Messprotokoll, Doku-Nachzüge, dann der Folge-Commit mit `-SNAPSHOT`. (7) Nach dem Review mit Doku-Befunden folgt, wenn die Behebung nur Doku betrifft, kein zweiter Review (`/plan-welle` 6f); berührt sie ein Skript, folgt er.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): (1) [slice-cloudsmith-ziel-bauen](../done/slice-cloudsmith-ziel-bauen.md) liegt in `done/` (das Werkzeug und der Workflow sind ohne Wirkung nach außen belegt, der Review hat die Workflow-Prüfpunkte geprüft). (2) [slice-maven-erste-veroeffentlichung](../done/slice-maven-erste-veroeffentlichung.md) liegt in `done/`: der Neuschnitt hat ihm die Punkte genommen, die hier liegen, und [`releasing.md`](../../../maintainer/releasing.md) sowie `docs/user/` sind danach nur in diesem Slice offen; zwei Pläne in denselben Dateien laufen nicht nebeneinander. (3) **Der Nutzer bestätigt ausdrücklich** die Veröffentlichung von `0.1.1` nach Version und Zeitpunkt (Wortlaut im Plan-Kopf; die Handlung selbst ist sein Tag-Push, ein Token ist nicht bereitzustellen). (4) Der Nutzer hat bestätigt, dass das Repository `pt9912/tdswire` bei Cloudsmith als „Open source“ eingestellt ist (Lizenz `MIT`, Projekt-URL) und dass der Service Schreibrecht nur auf dieses Repository hat (von keinem Gate und vom Planner nicht prüfbar; [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) Entscheidung 3a), und dass die Secrets `CLOUDSMITH_USERNAME` und `CLOUDSMITH_API_KEY` den gültigen Schlüssel tragen (die Namen stehen in `gh secret list`, gesehen 2026-11-15; die Werte nicht). (5) Der Tag-Schutz `v*` besteht ([`releasing.md`](../../../maintainer/releasing.md) §Tag-Schutz; seine Bedeutung wächst, weil ein Tag-Push nun auch ein Drittanbieter-Secret verwendet; von keinem Gate geprüft). (6) Ein Verantwortlicher ist eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß): der Konsum verlangt eine zweite Bauart (Maven, Java) oder die Entscheidung über die `.module`-Datei verlangt eine Änderung am veröffentlichten Artefakt; beides wird ein eigener Slice, dieser liefert den Kotlin-Konsumenten.
- `in-progress` → `open` (blockiert): ein Job wird abgewiesen (Schlüssel ungültig oder ohne Schreibrecht, Benutzername-Form, Repository nicht „Open source“-öffentlich, Cloudsmith setzt aus) und die Version ist am anderen Ziel verbraucht: die Korrektur ist eine neue Version (`0.1.2`) mit eigener Bestätigung, oder die Wiederholung des roten Jobs nach Behebung durch den Nutzer; welcher Weg gilt, ist eine Entscheidung des Nutzers ([ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) Re-Evaluierungs-Trigger: dann 5a, 2b oder die Löschregel neu). Gradle löst gegen Cloudsmith mit `.module` nicht auf: Entscheidung über die Moduldatei (neue ADR).

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Beobachtbar: (1) Der Lauf von `veroeffentlichung.yml` bei Tag-Push `v0.1.1` hat beide Veröffentlichungs-Jobs `success` (das Messprotokoll nennt die Lauf-Kennung); der anonyme Abruf von POM und `maven-metadata.xml` am Cloudsmith-Pfad ergibt 200; der Probe-Konsument löst die Version ohne Zugangsdaten über Cloudsmith auf (Teil (a)); Teil (b), die Auflösung mit Token über GitHub Packages, ist **vom Nutzer 2026-11-15 nicht gewollt, ungemessen** (Entscheidung des Nutzers, Wortlaut „Gar nicht messen“; Zielbild/DoD-Anpassung, siehe DoD Punkt 2); der Tag `v0.1.1` steht auf dem Release-Commit. (2) Alle Punkte der DoD erledigt, `make gates` grün, Review-Report liegt vor, Closure-Notiz mit Lerneintrag geschrieben. Die Closure-Notiz nennt außerdem, ob `tools/publish-probe.sh` bleibt oder entfällt, hält die Neubewertung von Software-Stückliste und Weg für Jar und `lib/` ohne Registry fest, falls [slice-maven-erste-veroeffentlichung](../done/slice-maven-erste-veroeffentlichung.md) sie nicht schon trägt, und nennt, ob die Zielbild-Lücke zu [TDS-RB-01](../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache) geschlossen, bestehend oder dem Nutzer neu vorzulegen ist.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **Teilfehler zwischen den Zielen:** genau ein Job wird rot; die Version ist am anderen Ziel verbraucht ([ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) 3a); die Wiederholung des roten Jobs allein ist nach [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) Entscheidung 2 die Lesart, die auf „Re-run failed jobs“ beruht, im Schwesterprojekt gelesen, hier nicht gemessen; ein Teilbestand (einzelne Dateien ohne vollständiges Paket) macht die Version dort ungewiss, ob eine Löschung sie frei macht, ist ungemessen. — **Ausgang: eingetreten.** `publish-cloudsmith` war in den Versuchen 1 bis 4 rot (401 am PUT), `publish` grün; die Wiederholung des roten Jobs allein (Versuch 5) stellte Cloudsmith her, `publish` lief nicht neu, GitHub Packages führt eine Anlage von `0.1.1`. Ungemessen bleiben „Re-run all jobs“ und die Löschwirkung (weiter offen, Träger: Eintritt eines weiteren Teilfehlers, Entscheidung des Nutzers).
- **Schlüssel ungültig, ohne Schreibrecht oder Benutzername in falscher Form (Service-Name oder Konto):** der Cloudsmith-Job wird rot, `0.1.1` liegt nur in GitHub Packages; kein Probelauf möglich ([ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Entscheidung 4); ob der Schlüssel schreibt, ist ungemessen. — **Ausgang: eingetreten in der Form des Benutzernamens, dann behoben.** Der Schlüssel schreibt (Versuch 5 `success`, Paket in Cloudsmith); die Form des Benutzernamens, die den 401 der Versuche 1 bis 4 verursachte, ist nur als Angabe des Nutzers festgehalten und im Messprotokoll ausdrücklich nicht belegt: **weiter offen, Träger: der Nutzer** (Form ohne Wert in `releasing.md` notieren). Schlüsselrecht nur auf dieses Repository: weiter offen, Träger: der Nutzer (Cloudsmith-Einstellung, kein Sensor).
- **Repository nicht „Open source“-öffentlich oder später ausgesetzt:** Upload gelingt, der anonyme Abruf bleibt 401 oder 404 (die Annahme von [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md), die kippt; ein 404 vor der ersten Version trennt „leer“ nicht von „nicht öffentlich“); Aussetzung bei Verstoß gegen die Hosting-Regeln, genannt ist fehlende Namensnennung. — **Ausgang: entfallen.** Der anonyme Abruf von POM, Jar, `.module` und `maven-metadata.xml` ergibt 200 (Verifier und Reviewer unabhängig wiederholt); `0.1.0` liefert 404. Kontingent, Dauer und Bedingungen des Hostings: siehe unten.
- **`.module` bei Cloudsmith:** ob Cloudsmith die Datei annimmt und Gradle sie dort auflöst, ist ungemessen (die gelesenen Seiten nennen `.module` nicht); der Konsum mit und ohne `.module` im zweiten Punkt misst es. — **Ausgang: entfallen (belegt).** Cloudsmith nahm die `.module` an (2858 Bytes im Paket) und liefert sie anonym aus; der Probe-Konsument löst mit und ohne `.module` auf (je Exit 0, `PROBE-OK`). Ungemessen bleibt, ob Gradle die `.module` im Lauf tatsächlich abrief (weiter offen, kein Träger nötig: ohne Folgen für den Konsum), und Java/Maven (außerhalb des Slice, §1).
- **Auslieferung nach dem Upload:** der Job belegt den Upload, nicht die Verfügbarkeit (Sammelfenster von 60 Sekunden, asynchrone Verarbeitung; [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) akzeptiertes Negativ); die Messung wartet auf den anonymen 200 und hält die Wartezeit fest. — **Ausgang: eingetreten, der Größe nach gesehen.** Verarbeitung 92 s nach dem Upload (API), Job-Ende 81 s davor. **Weiter offen: der erste anonyme 200** (kein Abruf im Fenster; erster gemessener 200 rund sechs Minuten nach `sync_finished_at`); Träger: die nächste Veröffentlichung (anonymer Abruf in Intervallen nach Job-Ende, `releasing.md`).
- **Maskierung des Cloudsmith-Schlüssels im Log:** für Repository-Secrets Dokumentationsstand, für den Schlüssel dieses Repos nicht gemessen; die Suche im Laufprotokoll wie im [Messprotokoll des Tag-Laufs](../../../messungen/veroeffentlichung-tag-lauf-2026-11-15.md). — **Ausgang: belegt für die geprüften Muster.** Zehn Protokolle (Versuche 1 bis 5, beide Jobs): `CLOUDSMITH_USERNAME: ***`, `CLOUDSMITH_API_KEY: ***`, `GITHUB_TOKEN: ***`, 0 Treffer der Suchmuster; Reviewer und Verifier haben die Zählung unabhängig wiederholt. Grenze: nur die genannten Muster, Schlüsselformat unbekannt, Gesamtprotokoll des Laufs nicht nachgelesen, Erzeuger nicht im Repo.
- **Tag-Schutz `v*` nicht gesetzt oder nicht wirksam:** wer den Tag pusht, löst nun zwei Veröffentlichungen mit einem langlebigen Secret aus; die Einstellung ist von keinem Gate prüfbar (akzeptiertes Negativ von [ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) und [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md)). — **Ausgang: weiter offen, Träger: der Nutzer.** Der Lauf zeigt nur, dass der Nutzer den Tag pushen konnte; die Gegenprobe „ohne Bypass“ (Push durch einen Dritten wird abgewiesen) ist nicht gefahren; Beleg gehört in [`releasing.md`](../../../maintainer/releasing.md) §Tag-Schutz, sobald er sie gefahren hat.
- **Die einmalige Messung geht rot und die Ausgabe ist weg:** der Lauf ist nicht wiederholbar; Register `einmal-rot-ohne-gesicherte-ausgabe` (2×); die Ausgabe jedes Laufs wird vor jedem Neustart gesichert. — **Ausgang: entfallen.** Die Ausgabe jedes Versuchs wurde vor dem Neustart gesichert, die vier roten Versuche sind im Messprotokoll belegt; kein Auftreten der Klasse (Register gesichtet).
- **`0.1.0` bleibt ohne anonymen Weg, `0.1.1` unterscheidet sich nur im Veröffentlichungsweg:** akzeptiertes Negativ von [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) Entscheidung 4a. — **Ausgang: weiter offen, akzeptiert** (Entscheidung 4a der ADR unverändert): `0.1.0` liegt nur in GitHub Packages, anonym 404 bei Cloudsmith; Träger: [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md), kein Slice.
- **Kontingent und Bedingungen bei Cloudsmith** (Größe des Pakets ungemessen, gegen 50 GB Speicher offensichtlich klein; Attribution; Prüfung nach Anlage): Änderung oder Aussetzung trifft den anonymen Weg, GitHub Packages bleibt. — **Ausgang: weiter offen, Träger: der Nutzer** (Paket 1,79 MB gesehen; Kontingent, Dauer und Bedingungen des Open-Source-Hostings ohne Sensor; Änderung trifft den anonymen Weg, GitHub Packages bleibt).
- **Konsum aus GitHub Packages mit Token ungemessen** (Zusatz bei der Closure, Entscheidung des Nutzers 2026-11-15): ob ein Konsument `0.1.1` oder `0.1.0` mit einem `read:packages`-Token aus GitHub Packages bindet und welche Dateien die Versionen führen, ist nicht gemessen. — **Ausgang: weiter offen, vom Nutzer nicht gewollt** („Gar nicht messen“); ohne Träger und ohne Folge-Slice; die Nutzer-Doku nennt den Weg als nicht gemessen.

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register (vorhandene `BEO-<KUERZEL>/<slug>` **zitieren** statt neu
formulieren — sonst zählt das Register zwei Namen getrennt) ·
`grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln (das
Feld `liegt in` steht **nur**, wenn mit diesem Slice wirklich etwas verkörpert
wurde; Feld und Zielort auf **einer** Zeile, Sektionsangabe innerhalb der
Backticks). Ging der Gegenstand an einen anderen Slice oder entfiel er, trägt
diese Sektion die Zeile `Gegenstand:` mit Kennung oder Grund und jedes Risiko
aus §6 seinen Ausgang; die Liefer-Punkte der DoD bleiben leer
(`modul-05-planning-harness.md` §Ein Slice, dessen Gegenstand ein anderer
übernimmt).

Geschlossen 2026-11-15 (Planner, Slice-Closure ohne Welle). Geprüfter Stand: Zweig `worktree-agent-ae8ee070bc730ad4b`, HEAD `9cb30d48` (Basis `main` `9c7439f5`, Release-Commit mit Tag `v0.1.1`). Prüfung: Verifikationsbericht [2026-11-15-673bdb26](../../../reviews/2026-11-15-673bdb26-slice-cloudsmith-veroeffentlichung-0-1-1-verifikation.md), Review [2026-11-15-f456e8a2](../../../reviews/2026-11-15-f456e8a2-slice-cloudsmith-veroeffentlichung-0-1-1-review.md) (fünf MEDIUM, zwei LOW, behoben in `673bdb26`; Re-Review für die zwei Kommentarzeilen im Workflow nicht nötig, Verifikation §7), Messprotokoll [veroeffentlichung-0-1-1-2026-11-15](../../../messungen/veroeffentlichung-0-1-1-2026-11-15.md).

**Geliefert.** (1) `0.1.1` liegt auf GitHub Packages (Anlage 16:23:51Z) und auf Cloudsmith (Upload 17:17:46Z); Lauf `43734101990` bei Tag-Push `v0.1.1` durch den Nutzer, Release-Commit `9c7439f5`, danach `0.1.1-SNAPSHOT` im Folge-Commit `f456e8a2`. **Der `-SNAPSHOT`-Stand gilt nur auf diesem Zweig; `main` steht bis zum Merge auf `0.1.1`** (V3 des Verifiers). (2) Konsum anonym über Cloudsmith: POM, Jar, `.module`, `maven-metadata.xml` je 200, Probe-Konsument mit und ohne `.module` `PROBE-OK`, `0.1.0` 404; Reviewer und Verifier haben die Werte unabhängig wiederholt. (3) Nutzer-Doku mit Weg A (Cloudsmith) vor Weg B (GitHub Packages), `README.md` mit Cloudsmith-Namensnennung, `releasing.md` als gelaufen mit Teilfehler-Weg.

**Der Lauf hatte sechs Versuche** (`run_attempt` 6): Versuche 1 bis 4 `publish-cloudsmith` rot (401 am PUT), Versuch 5 grün, Versuch 6 ohne neuen Job (nach Angabe des Orchestrators eine abgelehnte Wiederholungsanfrage; die Ursache ist aus Zeit und Fehlen eines Jobs geschlossen, nicht an einer Ausgabe der API belegt). Die Zahl steht im Messprotokoll; das Review und der erste Stand des Protokolls zählten fünf (V1, nachgezogen in `9cb30d48`).

**Entscheidung des Nutzers, 2026-11-15, im Slice als solche benannt.** Auf die Frage nach dem Token-Konsum aus GitHub Packages: „Gar nicht messen“. Folge: DoD Punkt 2 Teil (b) und Closure-Trigger (1) Teil (b) tragen „vom Nutzer 2026-11-15 nicht gewollt, ungemessen“; der Haken von Punkt 2 steht für (a) gemessen und (b) entfallen, nicht für eine Messung von (b). Die Anpassung ist eine Zielbild/DoD-Änderung durch den Nutzer (Zielbild-Prüfung §1, neue Zeile), kein Befund des Implementers, kein Folge-Slice. **Nicht gesagt wird**, GitHub Packages sei konsumierbar belegt: gemessen sind nur 401 ohne Token und die Anlage der Version; die Dateien von `0.1.1` und `0.1.0` in GitHub Packages bleiben ungesehen.

**Ungemessen, benannt** (Träger je Risiko in §6). Konsum aus GitHub Packages mit Token (Entscheidung des Nutzers); Form des Benutzernamens (nur als Angabe des Nutzers); erster anonymer 200 im Sammelfenster; Gegenprobe des Tag-Schutzes ohne Bypass; Schlüsselrecht nur auf dieses Repository; Löschwirkung bei Cloudsmith; „Re-run all jobs“; Bindung der Secrets; Kontingent und Bedingungen des Hostings; ob Gradle die `.module` abrief; Java und Maven. Maskierung: für die genannten Muster belegt, Erzeuger der Zählung nicht im Repo.

**Zielbild-Lücke zu [TDS-RB-01](../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache):** besteht (Entscheidung des Nutzers 2026-11-15, Lastenheft unverändert); der anonyme Konsum aus der Registry ist gemessen, der Konsum mit Token nicht; dem Nutzer nicht neu vorzulegen, solange er bei „Gar nicht messen“ bleibt. **`tools/publish-probe.sh`:** bleibt (Konsument-Modus `url` wurde hier gebraucht; Nutzerentscheidung 2026-11-14 hing an der Closure von [slice-maven-erste-veroeffentlichung](../done/slice-maven-erste-veroeffentlichung.md), die erfolgt ist; ein Entfall wäre eine eigene Entscheidung, hier nicht gefällt, die nächste Veröffentlichung braucht den Modus). **Software-Stückliste und Weg für Jar und `lib/` ohne Registry:** von dieser Closure nicht neu bewertet; die Neubewertung ist in `releasing.md` §Offene Entscheidungen als „fällig, nicht entschieden“ geführt, Träger: der Nutzer.

**Risiken (Ausgänge in §6).** Eingetreten: Teilfehler zwischen den Zielen; Auslieferung nach dem Upload (92 s Verarbeitung gesehen). Belegt oder entfallen: Maskierung (geprüfte Muster), `.module` bei Cloudsmith, Repository anonym öffentlich, einmalige Messung ohne gesicherte Ausgabe. Weiter offen mit Träger: Form des Benutzernamens und Schlüsselrecht (Nutzer), erster anonymer 200 (nächste Veröffentlichung), Tag-Schutz ohne Bypass (Nutzer, `releasing.md` §Tag-Schutz), Löschwirkung und „Re-run all jobs“ (bei Eintritt eines Teilfehlers, Nutzer), Kontingent (Nutzer), `0.1.0` ohne anonymen Weg (akzeptiert), Token-Konsum (vom Nutzer nicht gewollt).

**Register.** Fortgeschrieben, kein Zähler gesetzt: `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` und `BEO-TDS/aussagen-ueber-messung-hinaus` um je `evidence/slice-cloudsmith-veroeffentlichung-0-1-1.md` ergänzt (Review 1: `harness/README.md` sagte „nie gelaufen“, nachdem `releasing.md` gehoben war; Review 2, 3 und 5, V1: Kommentar, Snippet-Reichweite, „Stunden später“ und Versuchszahl breiter als die Messung); neu `BEO-TDS/dod-verlangt-messung-mit-geheimnis-des-nutzers` (1×). Gesichtet, kein neues Auftreten: `nachweis-von-der-falschen-seite-der-grenze` (dieser Slice liefert die Gegenseite zum Dummy-Beleg, mit der Lücke des Token-Konsums), `einmal-rot-ohne-gesicherte-ausgabe` (Ausgaben gesichert), `dod-nennt-nutzerentscheidung-als-lieferinhalt` (Bestätigung und Tag-Push standen im Start-Trigger), `lizenzhinweise-der-bibliotheken-rechtsfrage`.

**Folge-Slice:** keiner (Entscheidung des Nutzers; die offenen Punkte tragen Nutzer oder die nächste Veröffentlichung). Die Welle [welle-referenzabgleich-release](../welle-referenzabgleich-release.md) wurde nur gelesen, nicht geschlossen.

**Lerneintrag (Steering Loop).**

- **Geschärft, keine neue Regel:** (i) Eine Fortschreibung sucht die Kernaussage im ganzen Repo, nicht nur die Fundstelle (`harness/README.md` „nie gelaufen“ blieb stehen, `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`, verkörpert in `.harness/skills/reviewer.md` und `.claude/commands/implement-slice.md` Schritt 20c; Retirement-Check: die Klasse ist wieder aufgetreten, die Regel bleibt). (ii) Eine Messaussage trägt höchstens so weit wie die Probe-Konfiguration (Snippet gegen `metadataSources`, Versuchszahl, „Stunden später“; `AGENTS.md` §3.8, verkörpert).
- **Neu benannt, Beobachtung 1×, nichts verkörpert:** Ein Liefer-Punkt, dessen Messung ein Geheimnis des Nutzers braucht (Token `read:packages`), hat keinen Agentenweg; der Plan nannte den Weg „mit einem Token des Nutzers“, aber keinen Träger und keine Rückfallfrage. Folge hier: Entscheidung des Nutzers, Teil gestrichen (`BEO-TDS/dod-verlangt-messung-mit-geheimnis-des-nutzers`). Für künftige Pläne: bei einem Liefer-Punkt mit Geheimnis des Nutzers die Frage „messen oder nicht“ im Plan vorab stellen.
- **Teilfehler über „Re-run failed jobs“ ohne neue Version:** gemessen, dass die Wiederholung des roten Jobs allein genügt, `publish` nicht neu läuft und die Version am anderen Ziel nicht ein zweites Mal geschrieben wird (vorher Dokumentationsstand aus dem Schwesterprojekt); in `releasing.md` und im Messprotokoll geführt, [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) Entscheidung 2 damit belegt, nicht geändert.
- **Benutzername-Form:** vier rote Versuche an einer Eigenschaft, die kein Gate und kein Probelauf sieht (Dummy belegt den Ablauf, nicht die Form, [ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) Entscheidung 4); die Form steht nur als Angabe des Nutzers. Benannt, kein Sensor möglich ohne Cloudsmith-Zugang; Träger: Nutzer.
- **„Ohne Bypass“-Messung des Tag-Schutzes:** der erfolgreiche Tag-Push des Nutzers belegt den Schutz nicht, nur die Abweisung eines anderen Pushers täte es; Gegenprobe ist Sache des Nutzers.
- **Benannte Spec-Lücke:** unverändert [TDS-RB-01](../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache) Nachweis (Konsum aus der Registry); kein `liegt in`, nichts verkörpert, daher kein Herkunfts-Anker.
- **Maskierung:** das Prüfverfahren (Zählung von Mustern, Erzeuger im Scratchpad) ist nicht im Repo; eine Wiederholung bei der nächsten Veröffentlichung hängt am Messprotokoll, nicht an einem Skript.

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung — dort die **zwei vorgelagerten
Schritte** (sie stehen in jedem Slice-Plan, unabhängig von Modus und
Slice-Typ) und die **vier Pflichtkriterien** (Konventionen-Dichte ·
Phase-Reife · Evidenz-/Diskrepanz-Risiko · Reconciliation-Aufwand), vier und
nicht mehr.

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration führt `*` als einzige Sub-Area (Greenfield).

**Vorgelagert — offene Beobachtungen sichten:** Register (`../observations/BEO-TDS/`, Stand des gemergten `main` vom 2026-11-15) gegen die Verzeichnisliste und die Stichworte Veröffentlichung, Registry, Release, Nachweis, DoD, Zielbild, einmal, Workflow, Secret, Token, Messung gelesen; Treffer: `nachweis-von-der-falschen-seite-der-grenze` (2×; dieser Slice liefert die Gegenseite zu den Belegen gegen den Dummy aus [slice-cloudsmith-ziel-bauen](../done/slice-cloudsmith-ziel-bauen.md) und gegen das lokale Repository aus [slice-maven-veroeffentlichung](../done/slice-maven-veroeffentlichung.md)), `einmal-rot-ohne-gesicherte-ausgabe` (2×; die Ausgabe eines roten Laufs wird vor dem Neustart gesichert), `dod-nennt-nutzerentscheidung-als-lieferinhalt` (1×; geprüft: Bestätigung und Tag-Push stehen im Start-Trigger, nicht in der DoD), `lizenzhinweise-der-bibliotheken-rechtsfrage` (offen, nicht Gegenstand). Kein Zähler erreicht mit diesem Slice 3×, soweit er am Plan abzusehen ist.

Alle berührten Sub-Areas GF.
