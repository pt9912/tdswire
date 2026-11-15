# Slice slice-cloudsmith-ziel-bauen: Zweites Veröffentlichungsziel Cloudsmith in Werkzeug und Workflow bauen, ohne Wirkung nach außen belegt

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** ohne Welle — keine Closure-Bedingung, die von der DoD dieses Slice verschieden ist.

**Bezug:** [TDS-RB-05](../../../../spec/lastenheft.md#tds-rb-05--lizenz), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) (`Accepted` 2026-11-15, Folgepflicht 1: Plan für das zweite Ziel; Entscheidungen 2 und 3), [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) (Entscheidungen 3 und 6, soweit [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) sie nicht ändert), [ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) (Ablauf bei Tag-Push, erweitert), [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md) (Selbsttest der Klasse `werkzeug`), [TDS-RB-01](../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache) (nur Zielbild-Prüfung, geliefert im Folge-Slice)

**Berührte Spec-Stellen:** — (`spec/` nennt kein Veröffentlichungsziel; [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) hat das am 2026-11-15 per `grep` gelesen).

**Verantwortlich:** pt9912 (Anspruch 2026-11-15).

**Autor:** Planner-Agent, Auftrag des Nutzers 2026-11-15 nach [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md). **Datum:** 2026-11-15.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Werkzeug und Workflow können `io.github.pt9912:tdswire` zusätzlich nach Cloudsmith (Standardziel `https://maven.cloudsmith.io/pt9912/tdswire/`) veröffentlichen, als zweiter, vom GitHub-Packages-Job unabhängiger Job ([ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) Entscheidungen 2a und 3a), und ein Probe-Konsument kann eine Registry über eine URL anonym oder mit Zugangsdaten ansprechen. Belegt ist das ohne Wirkung nach außen: gegen den Dummy-Server und mit Platzhalter-Zugangsdaten, wie [ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) es für den ersten Workflow getan hat.

**Warum ein eigener Slice, getrennt von der Veröffentlichung:** Der Bau ist wiederholbar und trocken belegbar, die Veröffentlichung ([slice-cloudsmith-veroeffentlichung-0-1-1](../done/slice-cloudsmith-veroeffentlichung-0-1-1.md)) schreibt zweimal nach außen und ist nicht rücknehmbar ([ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) Entscheidung 3a). Was beim einzigen echten Lauf rot werden kann, soll vorher rot geworden sein (Register: `einmal-rot-ohne-gesicherte-ausgabe`, 2×). Der Bau hat außerdem eine Prüfung, die `make workflow-lint` nicht leistet (Pins, Reihenfolge, Bedingung, Secret-Fenster je Job), und braucht dafür den Review ([ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) Fitness Function).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Release `0.1.1`, Release-Commit, Tag, Messprotokoll der Veröffentlichung, anonymer Konsum und Konsum mit Token gegen die echten Registries, Nutzer-Doku, Namensnennung im README — [slice-cloudsmith-veroeffentlichung-0-1-1](../done/slice-cloudsmith-veroeffentlichung-0-1-1.md). Die Kennung nimmt die Punkte an (DoD dort). Grund: Sie setzen das Veröffentlichen voraus, das dieser Slice nie ausführt; ein Agent hält kein Schreib-Token und pusht keinen Tag.
- Echtes Schlüsselmaterial (`CLOUDSMITH_API_KEY`) in jeder Form außer dem Repository-Secret im Workflow — der Slice benutzt nur Platzhalter. Grund: [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) Entscheidung 3a; ein Schlüssel auf einem Rechner ist der Weg, den [ADR-0048](../../adr/0048-veroeffentlichung-in-der-ci-bei-tag-push.md) ausgeschlossen hat.
- Ein Eingriff in die `.module`-Datei (`GenerateModuleMetadata` abschalten, Prüfung in `tools/manifest-pruefung.sh`): [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) Entscheidung 3 nennt es als eigene Entscheidung, falls ein Beleg es verlangt; der Beleg am echten Cloudsmith kommt erst in [slice-cloudsmith-veroeffentlichung-0-1-1](../done/slice-cloudsmith-veroeffentlichung-0-1-1.md). Hier wird die Datei weder verändert noch zugesagt.
- OIDC statt Secret (3b), Snapshots über Cloudsmith, Maven Central — Re-Evaluierungs-Trigger in [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md), keiner eingetreten.
- Eine Änderung des Lastenhefts ([TDS-RB-01](../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache) Nachweis): Entscheidung des Nutzers 2026-11-15, das Lastenheft bleibt unverändert, die Lücke bleibt benannt.
- Das Anlegen von Repository, Service und Secrets bei Cloudsmith und GitHub: Handlung des Betreibers in der Web-App ([ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) Kontext: nicht über die API möglich).
- Ein neues Gate: `make publish*` bleibt in keiner Gate-Liste ([ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) Entscheidung 3; [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md)); der Selbsttest wird erweitert, keine Schwelle gesenkt (`AGENTS.md` §3.6).

**Zielbild-Prüfung** (Maßstab: das Zielbild führt, Code folgt; „Zielbild anpassen“ nur mit Entscheidung des Nutzers). Die Zeilen sind aus [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) §Zielbild-Prüfung gelesen und auf diesen Slice zugeschnitten:

| Stelle | Code angleichen | Zielbild anpassen | Entscheid |
|---|---|---|---|
| Zeilen `make publish*`, `veroeffentlichung.yml`, `make skript-selbsttest` in [`harness/README.md`](../../../../harness/README.md) §Sensors und die Aussage in [`releasing.md`](../../../maintainer/releasing.md) Schritt 9 „Das Eingangsskript nimmt als Ziel nur GitHub Packages“ | Zeilen und Satz nachziehen, Bindung an [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) (in DoD, Doku-Update) | — | Nachzug, nichts zu entscheiden |
| [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) (Veröffentlichen braucht Netz und Schlüssel, ist nicht wiederholbar) | Veröffentlichen bleibt Werkzeug außerhalb der Gates, Bau in Docker | — | unverändert |
| Gates (`AGENTS.md` §3.6) | `make skript-selbsttest` wird erweitert, nicht gesenkt | — | nichts zu tun |
| [TDS-RB-01](../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache) Nachweis „Konsum aus der Registry“ | liegt bei [slice-cloudsmith-veroeffentlichung-0-1-1](../done/slice-cloudsmith-veroeffentlichung-0-1-1.md) (Probe-Konsument am echten Ziel) | Lastenheft ergänzen: **vom Nutzer abgelehnt 2026-11-15** | Code angleichen; Lastenheft unverändert |

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [x] **Zweites Ziel im Werkzeug, ohne Wirkung nach außen belegt:** `build.gradle.kts` führt neben `registry` ein zweites Maven-Repository für Cloudsmith (Standardziel wie oben, für Belegläufe überschreibbar wie `tdswireRegistryUrl`; Benutzer und Schlüssel nur aus `CLOUDSMITH_USERNAME` und `CLOUDSMITH_API_KEY` der Umgebung). `tools/publish.sh` hat einen Zielschalter, verlangt je Ziel nur dessen Zugangsdaten, gibt sie nur namentlich an `docker run` und ruft je Ziel die Einzel-Aufgabe statt `publish`; echtes Schlüsselmaterial geht nur an das feste Standardziel des Anbieters, jedes andere Ziel nur mit Platzhalter. Ein neues Make-Ziel führt Cloudsmith; `make publish` bleibt GitHub Packages, beide in keiner Gate-Liste. `make skript-selbsttest` deckt je Ziel die Brüche vor Docker (leere Zugangsdaten, `-SNAPSHOT`, Tag, Baum) und die Zielregel für Schlüsselmaterial. `tools/publish-probe.sh` zeigt je Ziel: die Einzel-Aufgabe läuft gegen den Dummy-Server, der Name der Cloudsmith-Aufgabe ist am Lauf belegt (nicht mehr abgeleitet), der Wert des Platzhalters steht in keiner Ausgabe, und die am Dummy angekommenen Dateien (Jar, POM, `.module`) sind aufgelistet.
- [x] **Zweiter Job im Workflow:** `.github/workflows/veroeffentlichung.yml` führt neben `publish` einen Job für Cloudsmith: `needs: gates`, keine `needs` zwischen beiden Jobs, kein `continue-on-error`, dieselbe Bedingung (nur Tag-Push, `github.ref_type == 'tag'`), derselbe Schritt „Tag-Commit liegt in origin/main“, `permissions: contents: read` ohne `packages: write`, die Cloudsmith-Werte nur im `env:` des Schritts `make <neues Ziel>`; der Job `publish` bekommt sie nie; `vorschau` und der Dispatch veröffentlichen weiter nie. Keine neue Action ohne Commit-SHA (nach Möglichkeit keine neue Action). `make workflow-lint` ist grün; der Review hat die Punkte geprüft, die der Linter nicht prüft (Pins, Reihenfolge `gates` vor beiden Jobs, Bedingung, Secret-Fenster, `packages: write` nur im GitHub-Job), und sein Report nennt sie einzeln.
- [x] **Probe-Konsument gegen eine Registry per URL, ohne Wirkung nach außen belegt:** `tools/publish-probe.sh` hat einen Modus, in dem der Probe-Konsument (Kotlin, Gradle) `io.github.pt9912:tdswire:<Version>` aus einer Registry-URL auflöst, anonym oder mit Zugangsdaten aus der Umgebung; belegt gegen einen Auslieferer im Docker-Netz ohne Zugang nach außen, der die im Bau-Lauf (erster Punkt) geschriebenen Dateien ausliefert: aufgelöst, übersetzt, gelaufen, mit `.module` und ohne. Der Modus ist der Erzeuger der Messzahlen von [slice-cloudsmith-veroeffentlichung-0-1-1](../done/slice-cloudsmith-veroeffentlichung-0-1-1.md); er ist vorher gelaufen, damit der einzige echte Lauf nicht an dem Werkzeug scheitert.

Nachweis-Pflichten für alle drei Punkte:

- [x] `make gates` grün (führt `make skript-selbsttest` der Klasse `werkzeug` und `make stempel-eingaenge-pruefung`); für die Doku-Teile `make docs-check`, `make flaeche`, `make kopplung-zahlen` ([`harness/README.md`](../../../../harness/README.md) §Minimal agent workflow, Zeile „Doku oder Spec“); zusätzlich `make workflow-lint` (von [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) Fitness Function genannt, kein Gate; die Tabelle führt keine Zeile für Werkzeug, Workflow und Build-Datei, siehe §6).
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8). Enthält die Nachbesserung Code oder Skripte, folgt ein zweiter Review in frischem Kontext (§3, Ablauf).
- [x] Doku-Update, soweit die Beschreibung des Werkzeugs sich ändert: die Zeilen `make publish`, das neue Make-Ziel, `veroeffentlichung.yml` und `make skript-selbsttest` in [`harness/README.md`](../../../../harness/README.md) §Sensors (Bindung an [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md)); in [`releasing.md`](../../../maintainer/releasing.md) der Satz zum Ziel des Eingangsskripts (Schritt 9), der Abschnitt Tag-Schutz um das zweite Secret und der Kopf; die Kopfkommentare von `veroeffentlichung.yml`, `tools/publish.sh`, `tools/mk/publish.mk` („Das Token kommt nur aus … `GITHUB_*`“). Die Aussagen sind im Zustand „gebaut, nicht gelaufen“ geschrieben (`AGENTS.md` §3.8); `docs/user/` bleibt unverändert, bis [slice-cloudsmith-veroeffentlichung-0-1-1](../done/slice-cloudsmith-veroeffentlichung-0-1-1.md) den Beleg hat ([ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) Entscheidung 5).
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben; **kein Zaehler wird gesetzt**. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

**Prüfung der Liefer-Punkte gegen Bauart und Werkzeug** (`/plan-welle` Schritt 6b): Frage 1 — der Selbsttest sieht die Zielregeln (Stub für `docker`, Wegwerf-Repo, wie `tools/publish-selbsttest.sh` heute); die Einzel-Aufgabe und der Aufgabenname von Cloudsmith sind nur am Gradle-Lauf im Container sichtbar, deshalb steht deren Beleg im Lauf von `tools/publish-probe.sh`, nicht im Selbsttest. Ob der heutige Dummy-Server auch ausliefern (GET) kann, ist nicht gelesen; wenn nicht, ist der Auslieferer für den dritten Punkt Teil des Slice, und reicht das nicht, ist es die Rückführung nach `next` (§4). Der Workflow ist von `make workflow-lint` nicht im Ablauf zu sehen; der Selbsttest liest ihn als Text (nach dem Review nachgerüstet: Make-Target, Schritt, Job, Secrets des Cloudsmith-Schritts, `packages` und `contents` der zwei Jobs), nicht Pins, `needs`, Bedingung und Secrets außerhalb der zwei Jobs; dafür steht der Review in der DoD. Frage 2 — kein Punkt nennt eine Entscheidung des Nutzers als Lieferinhalt.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `build.gradle.kts` | update | zweites Repository `cloudsmith` im Block `publishing`; die Aufgabe heißt nach Gradle-Regel `publishMavenPublicationToCloudsmithRepository` (aus dem Namen abgeleitet, am Lauf zu belegen); Zugangsdaten nur aus der Umgebung, URL überschreibbar für Belegläufe ([ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) Entscheidung 3) |
| `tools/publish.sh` | update | Zielschalter; je Ziel nur dessen Pflicht-Umgebung (GitHub: `GITHUB_ACTOR`, `GITHUB_TOKEN`; Cloudsmith: `CLOUDSMITH_USERNAME`, `CLOUDSMITH_API_KEY`); Einzel-Aufgabe statt `gradle publish` (die Aufgabe `publish` führte beide Repositories aus und bräche ohne die Zugangsdaten des anderen); Standardziel je Anbieter, Platzhalter-Regel je Ziel; Form des Schalters legt der Implementer fest |
| `tools/mk/publish.mk` | update | neues Make-Ziel für Cloudsmith (Name legt der Implementer fest, passend zu `make help`), `make publish` bleibt GitHub Packages; keines in `GATE_CHECKS`, `GATE_KLASSE_*`, `GATE_STAGE`, nicht in `tools/mk/e2e.mk` |
| `tools/mk/skript-selbsttest.mk` | update | Hilfezeile des Targets nennt die Prüfungen je Ziel |
| `tools/publish-selbsttest.sh` | update | je Ziel Brüche vor Docker; Schlüsselmaterial nur am Standardziel; Abwesenheit des neuen Ziels in den Gate-Listen; Mutationen des Skripts bleiben rot ([ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md), Klasse `werkzeug`) |
| `tools/publish-probe.sh` | update | Belegläufe beider Ziele gegen den Dummy; Modus „Konsument gegen Registry-URL“ (dritter Punkt); Der Kopf nennt die Grenze: Dummy ersetzt weder das Sammelfenster noch die asynchrone Verarbeitung noch die Annahme der `.module` bei Cloudsmith |
| `.github/workflows/veroeffentlichung.yml` | update | zweiter Job (zweiter Punkt); Kopfkommentar um das zweite Ziel und die Grenze des Linters |
| `harness/README.md` (§Sensors), `docs/maintainer/releasing.md` | update | Beschreibungen nachziehen (Doku-Update der DoD) |
| `tools/stempel/*.eingaenge`, `Dockerfile` | nur falls nötig | eine neue `COPY`-Quelle oder ein neues Fragment gehört in die Listen; `make stempel-eingaenge-pruefung` (Klasse `immer`) bricht sonst |

**Ablauf:** (1) Implementer baut die drei Punkte; Befund am Dummy-Lauf ist Bericht, keine Schätzung. (2) Review in frischem Kontext mit den Workflow-Prüfpunkten (zweiter Punkt der DoD), Änderungsart-Sensoren aus der DoD. (3) Enthält die Behebung Code, Skripte oder Make-/Build-/Konfigurationsdateien, folgt ein Re-Review in frischem Kontext (`/plan-welle` 6f); sonst der Vermerk „Nachbesserung ohne zweiten Review“ in §7. (4) Verifier prüft gegen die DoD und fährt `make gates` auf dem End-Stand; `make clean-build` und `make e2e` sieht die Tabelle nur für `src/` und `testserver/` vor, beides ist hier nicht erforderlich. (5) Nichts wird veröffentlicht, kein Tag gesetzt, nichts gepusht.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): (1) [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) ist `Accepted` (erfüllt, 2026-11-15, in `main` gemergt). (2) Der Nutzer hat den Service und die Secrets `CLOUDSMITH_USERNAME` und `CLOUDSMITH_API_KEY` angelegt: `gh secret list` zeigt beide Namen (gesehen am 2026-11-15; Wert, Gültigkeit und Schreibrecht des Schlüssels sind nicht geprüft und dieser Slice braucht sie nicht). (3) Der Nutzer hat bestätigt, dass das Repository `pt9912/tdswire` bei Cloudsmith als „Open source“ eingestellt ist (Lizenz `MIT`, Projekt-URL) — so führt es [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) (Folgepflicht 1) als Start-Bedingung des Plans für das zweite Ziel; der Bau braucht sie nicht, der Plan folgt dem Wortlaut der ADR und hält die Bedingung hier, damit sie vor der Veröffentlichung nicht vergessen wird. **Stand: vom Nutzer am 2026-11-15 bestätigt, nicht beobachtet** (der Planner sieht die Einstellung bei Cloudsmith nicht; ein 404 des leeren Repositories trennt „leer“ nicht von „nicht öffentlich“); der Bau hat die Bedingung nicht gebraucht, die Veröffentlichung braucht sie. (4) Ein Verantwortlicher ist eingetragen. [slice-cloudsmith-veroeffentlichung-0-1-1](../done/slice-cloudsmith-veroeffentlichung-0-1-1.md) beginnt nicht vor diesem Slice in `done/`.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß): der dritte Punkt (Probe-Konsument gegen eine Registry) verlangt einen eigenen Auslieferer, der den Slice über das Maß dehnt, oder der Zielschalter in `tools/publish.sh` verlangt einen Umbau des Skripts über die Zusage hinaus; der Punkt wird ein eigener Slice, bevor [slice-cloudsmith-veroeffentlichung-0-1-1](../done/slice-cloudsmith-veroeffentlichung-0-1-1.md) beginnt.
- `in-progress` → `open` (blockiert): die Aufgabe `publish` oder `make publish-vorschau` bricht mit dem zweiten Repository und braucht eine Entscheidung zum Aufbau des `publishing`-Blocks; der Nutzer zieht Cloudsmith zurück ([ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) Re-Evaluierungs-Trigger); die Bestätigung nach (3) fällt negativ aus (Repository nicht „Open source“, dann 5a oder neue ADR).

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Beobachtbar: (1) `make skript-selbsttest` (in `make gates`) und `make workflow-lint` sind grün; der Lauf von `tools/publish-probe.sh` zeigt für beide Ziele die Einzel-Aufgabe gegen den Dummy mit den ankommenden Dateien, und der Konsument-Modus löst gegen den Auslieferer auf (die Ausgaben stehen im Bericht des Implementers und im Review-Report); der Review-Report mit den einzeln genannten Workflow-Prüfpunkten liegt vor. (2) Alle Punkte der DoD erledigt, `make gates` grün, Closure-Notiz mit Lerneintrag geschrieben. Die Closure-Notiz nennt außerdem den belegten Namen der Cloudsmith-Aufgabe und welche Sätze in [`harness/README.md`](../../../../harness/README.md) und [`releasing.md`](../../../maintainer/releasing.md) im Zustand „gebaut, nicht gelaufen“ stehen, damit [slice-cloudsmith-veroeffentlichung-0-1-1](../done/slice-cloudsmith-veroeffentlichung-0-1-1.md) sie auf „gelaufen“ hebt.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **Die Aufgabe `publish` führt beide Repositories aus:** ein von Hand oder aus alter Gewohnheit gerufenes `gradle publish` bricht ohne die Zugangsdaten des anderen Ziels (aus der Funktion der Aufgabe geschlossen, vom Schwesterprojekt bestätigt gelesen, hier nicht gemessen); ob Gradle fehlende Umgebungswerte eines nicht ausgeführten Repositories toleriert, ist Gradle-Verhalten, das der Lauf zeigen muss. — **Ausgang: weiter offen, Träger [slice-cloudsmith-veroeffentlichung-0-1-1](../done/slice-cloudsmith-veroeffentlichung-0-1-1.md)** (nur umgangen, nicht gemessen: das Werkzeug ruft je Ziel die Einzel-Aufgabe, `publishMavenPublicationToCloudsmithRepository` am Lauf belegt; ein `gradle publish` mit nur einem Satz Zugangsdaten ist nicht gefahren).
- **Der Dummy belegt den Ablauf, nicht Cloudsmith:** Sammelfenster von 60 Sekunden, asynchrone Verarbeitung, Annahme der `.module`, Form des Benutzernamens (Service-Name oder Konto) und Rechte des Schlüssels sind nur am echten Ziel zu messen; der Slice liefert die Gegenseite nicht (Register: `nachweis-von-der-falschen-seite-der-grenze`, 2×; die Gegenseite ist [slice-cloudsmith-veroeffentlichung-0-1-1](../done/slice-cloudsmith-veroeffentlichung-0-1-1.md)). — **Ausgang: weiter offen, Träger [slice-cloudsmith-veroeffentlichung-0-1-1](../done/slice-cloudsmith-veroeffentlichung-0-1-1.md)** (die Gegenseite; am Dummy gemessen, nicht an Cloudsmith).
- **Workflow-Aussagen ohne Sensor:** Pins, Reihenfolge, Bedingung, Secret-Fenster und `packages: write` je Job prüft nur der Review; ob der neue Job beim Dispatch übersprungen wird und beim Tag-Push läuft, zeigt erst der Tag-Lauf von [slice-cloudsmith-veroeffentlichung-0-1-1](../done/slice-cloudsmith-veroeffentlichung-0-1-1.md). — **Ausgang: weiter offen, Träger [slice-cloudsmith-veroeffentlichung-0-1-1](../done/slice-cloudsmith-veroeffentlichung-0-1-1.md)** (Tag-Lauf; Pins, Reihenfolge, Bedingung nur Review; N2-1: ein Cloudsmith-Secret außerhalb der beiden Jobs bindet der Selbsttest nicht, V-3 im Lauf des Verifiers grün).
- **Die Tabelle der Pflicht-Sensoren hat keine Zeile für Werkzeug, Workflow und Build-Datei:** der Plan nimmt `make gates` und für die Doku-Teile die Zeile „Doku oder Spec“, dazu `make workflow-lint` nach [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md); ob die Tabelle eine eigene Zeile bekommt, ist Sache des Nutzers. — **Ausgang: weiter offen, Entscheidung des Nutzers** (Zeile für Werkzeug, Workflow und Build-Datei in der Tabelle; nicht entschieden).
- **Neue `COPY`-Quelle oder neues Fragment:** `make stempel-eingaenge-pruefung` bricht, wenn eine Datei nicht in `tools/stempel/*.eingaenge` steht; Gate-Klasse und Stempel ändern sich mit `build.gradle.kts` und den Skripten. — **Ausgang: entfallen** (`make stempel-eingaenge-pruefung` grün, `tools/stempel/*.eingaenge` unverändert, im Dockerfile nur Kommentarzeilen).
- **Ein Platzhalter-Beleg ist kein Schutz gegen ein echtes Token von Hand:** die Präfixprüfung gilt nur für den Weg über `tools/publish.sh` (Grenze, die schon im Kopf des Skripts steht und für das zweite Ziel gleich bleibt). — **Ausgang: weiter offen, Grenze unverändert** (der Selbsttest belegt die Regel des Weges über `tools/publish.sh` an Mutation V-1; gegen ein echtes Token von Hand schützt sie nicht).

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

Geschlossen 2026-11-15 (Planner, Slice-Closure ohne Welle). Geprüfter Stand: `85820481` (Commits `2b62d0a9`, `9a9a681d`, `f25a9e9f`, `85820481`), dazu die Nachbesserung `04cb30ae` (unten). Prüfung: Verifikationsbericht [2026-11-15-85820481](../../../reviews/2026-11-15-85820481-slice-cloudsmith-ziel-bauen-verifikation.md); Reviews [2b62d0a9](../../../reviews/2026-11-15-2b62d0a9-slice-cloudsmith-ziel-bauen-review.md), [9a9a681d](../../../reviews/2026-11-15-9a9a681d-slice-cloudsmith-ziel-bauen-re-review.md), [f25a9e9f](../../../reviews/2026-11-15-f25a9e9f-slice-cloudsmith-ziel-bauen-re-review-2.md) (zweiter Re-Review; kein HIGH, kein MEDIUM).

**Geliefert.** (1) Zweites Ziel: Repository `cloudsmith` in `build.gradle.kts`, Zielschalter und Zielregel in `tools/publish.sh`, `make publish-cloudsmith`, Selbsttest je Ziel. **Belegter Name der Cloudsmith-Aufgabe: `publishMavenPublicationToCloudsmithRepository`** (am Lauf von `tools/publish-probe.sh ziele` gelesen, vom Verifier selbst gefahren; GitHub: `publishMavenPublicationToRegistryRepository`); je Ziel 20 Dateien mit 201, der Platzhalter in 18 Suchen 0 Treffer, Positivprobe 1 Treffer (Zahlen des Verifiers, [Bericht §3](../../../reviews/2026-11-15-85820481-slice-cloudsmith-ziel-bauen-verifikation.md); **V-A**: sie stehen nur dort, in keinem Messprotokoll, weil der Bericht des Implementers im Repo nicht vorliegt). (2) Zweiter Job `publish-cloudsmith` in `veroeffentlichung.yml`. (3) Modus `registry <ziel> <anonym|zugang>` des Probe-Konsumenten gegen einen Auslieferer im Docker-Netz: je drei Konsumenten-Läufe `PROBE-OK`, anonym 200, mit Zugang erst 401 ohne Auth, dann 200.

**Sätze im Zustand „gebaut, nicht gelaufen“** (zu heben durch [slice-cloudsmith-veroeffentlichung-0-1-1](../done/slice-cloudsmith-veroeffentlichung-0-1-1.md)): in [`harness/README.md`](../../../../harness/README.md) §Sensors die Zeilen `make publish-cloudsmith` („am Dummy-Server belegt, nie gegen Cloudsmith gelaufen“) und `veroeffentlichung.yml` („nie gelaufen“); in [`releasing.md`](../../../maintainer/releasing.md) Schritt 9 („gebaut und nie gelaufen“) und der Abschnitt Tag-Schutz (zwei Secrets); `docs/user/` unverändert ([ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) Entscheidung 5).

**Nachbesserung ohne dritten Review, nur Kommentar/Beschriftung** (Commit `04cb30ae`, nach der Verifikation; N2-1-Kommentar und N2-2): im Kommentar von `tools/publish-selbsttest.sh` „Rechte und Secrets je Job“ auf die Jobs `publish` und `publish-cloudsmith` eingeengt (`AGENTS.md` §3.8); in `tools/publish-probe.sh` die zwei Beschriftungen „nur POM“ um die Bedingung ergänzt (Gradle fragt die `.module` an, wenn die Umleitung nicht abgeschaltet ist). Keine Logik geändert. Weil Skripte berührt sind, führt die Closure es ausdrücklich, statt es als reine Doku zu behandeln; danach liefen `make skript-selbsttest` (Exit 0) und `make gates`. **Dem Nutzer zur Entscheidung:** ob ein dritter Review der drei Textzeilen gewollt ist oder dieser Vermerk genügt (Regel `BEO-TDS/nachbesserung-ohne-zweiten-review`; sie nimmt reine Kommentar-Korrekturen selbst aus).

**Ungemessen, benannt.** (a) Der Tag-Lauf von `publish-cloudsmith`: nie gelaufen, der Dispatch-Lauf 43619591947 kannte den Job nicht. (b) Pins, Reihenfolge `gates` vor beiden Jobs, Bedingung: nur Review und Lesung. (c) Echtes Cloudsmith: ein 404 des leeren Repositories (Modus `url`) trennt „leer“ nicht von „nicht öffentlich“; Sammelfenster, asynchrone Verarbeitung, Annahme der `.module`, Form des Benutzernamens, Rechte des Schlüssels, erneuter Upload ungemessen. (d) Start-Bedingung (3) „Open source“: vom Nutzer am 2026-11-15 bestätigt, nicht beobachtet. (e) `gradle publish` mit einem Satz Zugangsdaten. (f) Frischlauf der Gate-Klassen beim Verifier per Hash-Beleg übersprungen (V-B); ersetzt durch die Selbsttest-Läufe V-1 bis V-3.

**Risiken (Ausgänge stehen in §6).** Weiter offen, Träger [slice-cloudsmith-veroeffentlichung-0-1-1](../done/slice-cloudsmith-veroeffentlichung-0-1-1.md): `publish` mit beiden Repositories (1), Dummy gegenüber Cloudsmith (2), Workflow ohne Sensor einschließlich der **N2-1-Sensorlücke: ein Cloudsmith-Secret außerhalb der beiden Jobs bindet der Selbsttest nicht** (3). Weiter offen beim Nutzer: Tabellenzeile für Werkzeug, Workflow und Build-Datei (4); Grenze des Platzhalters (6, unverändert). Entfallen: neue `COPY`-Quelle (5).

**Register.** Fortgeschrieben, kein Zähler gesetzt: `BEO-TDS/nachbesserung-ohne-zweiten-review` um `evidence/slice-cloudsmith-ziel-bauen.md` ergänzt (Vermerk; Kommentar/Beschriftung am Skript); `BEO-TDS/aussagen-ueber-messung-hinaus` um `evidence/slice-cloudsmith-ziel-bauen.md` ergänzt (N2-1-Kommentar zu breit, N2-2 Beschriftung ohne Bedingung; am Review gefunden und behoben; schon verkörpert in `AGENTS.md` §3.8). Gesichtet, kein neues Auftreten: `nachweis-von-der-falschen-seite-der-grenze` (der Dummy belegt den Ablauf; die Gegenseite trägt der Folge-Slice), `einmal-rot-ohne-gesicherte-ausgabe`, `lizenzhinweise-der-bibliotheken-rechtsfrage`.

**Folge-Slice (nur gelesen, nicht beansprucht).** [slice-cloudsmith-veroeffentlichung-0-1-1](../done/slice-cloudsmith-veroeffentlichung-0-1-1.md), Start-Trigger: (1) erfüllt mit dieser Closure; (2) erfüllt (`done/`); (3) ausdrückliche Bestätigung von `0.1.1` nach Version und Zeitpunkt: offen; (4) „Open source“ bestätigt (2026-11-15), Schreibrecht des Service und Gültigkeit des Schlüssels nicht; (5) Tag-Schutz `v*`: Gegenprobe offen ([slice-maven-erste-veroeffentlichung](../done/slice-maven-erste-veroeffentlichung.md) §7); (6) Verantwortlicher: nicht eingetragen. Der Slice ist noch nicht startbar.

**Lerneintrag (Steering Loop).**

- **Geschärft, keine neue Regel:** Ein Kommentar über die Reichweite eines Sensors („je Job“) trägt höchstens so weit wie der Sensor (`AGENTS.md` §3.8, `BEO-TDS/aussagen-ueber-messung-hinaus`, verkörpert); gleiches gilt für Beschriftungen von Messläufen („nur POM“ ohne die Bedingung, unter der es gilt, N2-3). Eine Messaussage am Kopf braucht eine Protokollzeile.
- **Benannte Sensorlücke (N2-1), kein neuer Sensor:** Die Zusage „Cloudsmith-Secret nur im `env:` des Schritts `make publish-cloudsmith`“ ist über die beiden Jobs hinaus nur durch den Review getragen (V-3). Ein Sensor „Cloudsmith-Name in keinem Schritt außerhalb des Cloudsmith-Jobs“ wäre klein; er ist nicht gebaut, weil die DoD den Review zusagt. Träger: der Tag-Lauf im Folge-Slice. Kein `liegt in`, nichts verkörpert.
- **N2-4, bekannte Eigenschaft:** der Parser der Gradle-Metadaten lehnt in sicherer Richtung ab; die Meldung nennt die Ursache nicht.
- **Beleg im Repo (V-A):** Ein Slice, der Messzahlen „im Bericht des Implementers“ verspricht, braucht eine auflösbare Ablage; hier trägt sie der Verifikationsbericht. Für künftige Pläne: §5 auf ein Artefakt im Repo zeigen lassen.
- Verkörpert wurde mit diesem Slice keine Regel, daher kein Herkunfts-Anker; neue Spec-Lücke: keine ([TDS-RB-01](../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache) Nachweis bleibt benannt).

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung — dort die **zwei vorgelagerten
Schritte** (sie stehen in jedem Slice-Plan, unabhängig von Modus und
Slice-Typ) und die **vier Pflichtkriterien** (Konventionen-Dichte ·
Phase-Reife · Evidenz-/Diskrepanz-Risiko · Reconciliation-Aufwand), vier und
nicht mehr.

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration führt `*` als einzige Sub-Area (Greenfield).

**Vorgelagert — offene Beobachtungen sichten:** Register (`../observations/BEO-TDS/`, Stand des gemergten `main` vom 2026-11-15) gegen die Verzeichnisliste und die Stichworte Veröffentlichung, Registry, Release, Nachweis, DoD, Zielbild, einmal, Workflow, Secret, Token, Messung gelesen; Treffer: `nachweis-von-der-falschen-seite-der-grenze` (2×; dieser Slice liefert Belege gegen den Dummy, die Gegenseite liegt bei [slice-cloudsmith-veroeffentlichung-0-1-1](../done/slice-cloudsmith-veroeffentlichung-0-1-1.md)), `einmal-rot-ohne-gesicherte-ausgabe` (2×; Grund für den dritten Punkt und für das Sichern der Ausgabe jedes Laufs vor einem Neustart), `lizenzhinweise-der-bibliotheken-rechtsfrage` (offen, nicht Gegenstand). Kein Zähler erreicht mit diesem Slice 3×, soweit er am Plan abzusehen ist.

Alle berührten Sub-Areas GF.
