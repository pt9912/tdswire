# Slice slice-release-artefakt-lizenz: Lauffähiges Artefakt: Laufzeit-Image, Laufzeittest und Lizenznachweis

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-referenzabgleich-release

**Bezug:** [TDS-RB-01](../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-RB-05](../../../../spec/lastenheft.md#tds-rb-05--lizenz), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)

**Berührte Spec-Stellen:** SPEC-024 · SPEC-025 · SPEC-042 · ARC-008

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912. **Datum:** 2026-10-30.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Aus dem Build entsteht ein ausführbares Server-Artefakt (Jar mit Startklasse und Laufzeit-Image mit JRE), das in Docker startet, einen Login beantwortet und die Lizenz mitführt; der Laufzeittest und die Lizenzprüfung sind Teil der Gates.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- `main`, Konfiguration, Verdrahtung und der Rohbytes-Smoke — [slice-bootstrap-konfiguration](../in-progress/slice-bootstrap-konfiguration.md); dieser Slice verpackt und startet dasselbe, er schreibt sie nicht neu.
- Die Läufe des Referenzclients gegen das Image — [slice-qa-kompatibilitaet](slice-qa-kompatibilitaet.md) und [slice-referenzabgleich-live](slice-referenzabgleich-live.md) nutzen es, sobald es besteht; entstand dort schon ein Image, übernimmt dieser Slice es als das eine Artefakt (§6).
- Veröffentlichung (Registry, Signierung, Software-Stückliste) und Orchestrierung (Compose, Kubernetes) — das Lastenheft verlangt sie nicht ([TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) verlangt Reproduktion in Docker); ob ein Release sie braucht, entscheidet der Nutzer in [slice-release-doku-prozess](slice-release-doku-prozess.md).
- Bit-genau gleiche Jars aus zwei Builds — nicht gefordert; [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) verlangt eine reproduzierbare Umgebung, keine bitgleichen Artefakte.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **Laufzeit-Image:** Eine Stage im `Dockerfile` mit JRE-Basis-Image per Digest (SPEC-042), Nutzer ohne Root-Rechte, Jar mit Startklasse; die Konfiguration kommt zur Laufzeit herein (Umgebung oder Datei nach [slice-bootstrap-konfiguration](../in-progress/slice-bootstrap-konfiguration.md)), kein Geheimnis und keine Benutzerliste im Image (Test über den Inhalt); das bestehende Artefakt-Image `build` für das Jar bleibt, solange es jemand nutzt.
- [ ] **Laufzeittest** ([TDS-RB-01](../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache)): Der Container startet mit einer Testkonfiguration, ein Rohbytes-Client meldet sich an, der Stop beendet den Prozess; der Test ist ein `make`-Target in `make gates` oder, begründet mit der gemessenen Laufzeit, außerhalb; der Eintrag steht in `harness/README.md` §Sensors.
- [ ] **Lizenz** ([TDS-RB-05](../../../../spec/lastenheft.md#tds-rb-05--lizenz)): Die Datei `LICENSE` im Wurzelverzeichnis ist die MIT-Lizenz (Textprüfung), und das Artefakt führt sie mit (Jar oder Image); ein Test bricht, wenn sie fehlt.

Nachweis-Pflichten für alle drei Punkte:

- [ ] Jeder Test trägt im KDoc über der Testfunktion den Block-Tag `@spec SPEC-<NNN>` (Konvention nach [slice-qa-spec-abdeckung](../done/slice-qa-spec-abdeckung.md)); die Tests laufen in `make test` und sind grün.
- [ ] Jede Aussage steht an einem Test oder ist als Festlegung gekennzeichnet; die Zuordnung *Test | Festlegung* je Aussage steht in der Closure-Notiz (`BEO-TDS/aussagen-ueber-messung-hinaus`, siehe §8).
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `Dockerfile` | update | Laufzeit-Stage mit JRE-Basis per Digest; Digest-Wahl braucht Netz, der Gate-Lauf nicht |
| `build.gradle.kts` | update | Startklasse im Manifest; Lizenzdatei im Jar |
| `tools/mk/` | update | Make-Target für Image und Laufzeittest; `GATE_CHECKS +=` nach Entscheidung |
| Tests des Laufzeittests und der Lizenzprüfung <!-- d-check:ignore (geplant: neue Datei) --> | neu | Happy/Boundary/Negative: Start, Login, Stop; fehlende Lizenz; Secret im Image |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-bootstrap-konfiguration](../in-progress/slice-bootstrap-konfiguration.md) und [slice-qa-reproduzierbarkeit](../done/slice-qa-reproduzierbarkeit.md) liegen in `done/` (`main` und CI), und ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2 oder die Image-Stage sprengt den Rahmen.
- `in-progress` → `open` (blockiert — Carveout?): die Wahl des JRE-Basis-Images ist nicht entschieden oder der Digest ist ohne Netz nicht zu ermitteln.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün, der Review-Report liegt vor, und die Closure-Notiz trägt die Zuordnung *Test | Festlegung*, die gemessene Laufzeit des Laufzeittests und den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Zwei Definitionen desselben Artefakts: [slice-bootstrap-konfiguration](../in-progress/slice-bootstrap-konfiguration.md) und [slice-qa-kompatibilitaet](slice-qa-kompatibilitaet.md) müssen den Server in Docker starten, bevor dieser Slice startet. Prüft der Implementer beim Start, ob dort ein Image besteht, schrumpft der Slice auf Laufzeittest, Lizenz und die Vereinheitlichung; zwei Images für denselben Server sind zu vermeiden. — **Ausgang:** offen bis zur Closure.
- Basis-Image: das gepinnte Distroless-Image der Stage `build` enthält nach seiner Bezeichnung keine JRE; welches Laufzeit-Image gewählt wird (Distroless mit Java, JRE-Image), ist eine Entscheidung des Implementers mit Nutzerbestätigung, und der Digest ist pro Wahl zu ermitteln, nicht zu raten. — **Ausgang:** offen bis zur Closure.
- Lizenzen der Abhängigkeiten im Artefakt: [TDS-RB-05](../../../../spec/lastenheft.md#tds-rb-05--lizenz) verlangt nur die MIT-Lizenz des Projekts. Ob Hinweise zu Lizenzen der gelieferten Bibliotheken nötig sind, ist eine Rechtsfrage ohne Prüfung im Repo; die Closure führt sie als offen. Die Frage wird nicht mehr hypothetisch: [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) nimmt `jackson-dataformat-yaml` (Apache-2.0 nach den POMs; Lesart des Architect, keine Rechtsberatung) mit `databind`, `core`, `annotations` und `snakeyaml` als `implementation` auf, sobald [slice-sim-aenderung-integration](slice-sim-aenderung-integration.md) sie einbaut; ob das ausgelieferte Artefakt einen Lizenz- oder Hinweisnachweis braucht, entscheidet dieser Slice. — **Ausgang:** offen bis zur Closure.
- Der Laufzeittest belegt Start und Login mit dem Rohbytes-Client, nicht die Annahme durch `Microsoft.Data.SqlClient` und nicht das Verhalten unter Last. — **Ausgang:** offen bis zur Closure.
- Ein Test im Docker-Build wird nach einem grünen Lauf gecacht; die Wiederholung braucht `--no-cache-filter` (`BEO-TDS/test-stage-cache-verdeckt-wiederholung`). — **Ausgang:** offen bis zur Closure.

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

Wird bei der Closure gefüllt.

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung — dort die **zwei vorgelagerten
Schritte** (sie stehen in jedem Slice-Plan, unabhängig von Modus und
Slice-Typ) und die **vier Pflichtkriterien** (Konventionen-Dichte ·
Phase-Reife · Evidenz-/Diskrepanz-Risiko · Reconciliation-Aufwand), vier und
nicht mehr.

**Der Abschnitt selbst entfällt nie.** Die zwei vorgelagerten Prüfungen laufen
in **jedem** Slice-Plan — sie hängen weder am Modus noch am Slice-Typ. Bedingt
ist allein der Modus-Begründungsblock am Ende; deshalb nennt der Titel beide
Hälften.

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`). Der Slice berührt Testwerkzeug, Docker-Stages und Dokumentation; keine dieser Strukturen trägt eine eigene Strukturregel (Achse 1) oder eine eigene Inventur-Linie (Achse 2) neben dem Repo insgesamt: eine von drei Achsen, unter der Schwelle. Keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, 19 Beobachtungen, Stand des Worktrees auf `main`); alle tragen die Sub-Area `*`. Treffer mit Zähler-Stand (Zahl der Dateien unter `evidence/`):

- `BEO-TDS/aussagen-ueber-messung-hinaus` — 16×, Stand *verkörpert* (`AGENTS.md` §3.8, Reviewer-Skill). Betrifft diesen Slice: Jede Aussage über das Artefakt nennt, was der Laufzeittest belegt (§6).
- `BEO-TDS/katalog-und-lock-ohne-gegenpruefung` — 1×, offen. Betrifft den Pin des JRE-Images und der Abhängigkeiten: Katalog und Lock werden von keinem Gate gegeneinander gehalten.
- `BEO-TDS/test-stage-cache-verdeckt-wiederholung` — 2×, offen. Betrifft den Laufzeittest in der Docker-Stage (§6).
- `BEO-TDS/ablage-im-repo-bricht-docs-check` — 1×, offen. Betrifft Make-Rezepte, die Ausgaben aus Docker-Stages einsammeln.
- `BEO-TDS/java-quelle-umgeht-kotlin-regel` — 1×, offen. Betrifft das Manifest und Build-Skripte: keine Java-Quelle für die Startklasse.
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — 1×, offen. Slice und Closure-Notiz nennen `ADR-*`- und `TDS-*`-Kennungen als Link.
- Ohne Treffer: `BEO-TDS/messung-am-echten-sql-server-fehlt`, `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`, `BEO-TDS/vergleichslauf-d-ungeklaert`, `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`, `BEO-TDS/offene-punkte-ohne-traeger`, `BEO-TDS/spec-einzelfaelle-ohne-lastenheft-deckung`, `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut`, `BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen`, `BEO-TDS/strukturtest-ohne-variation-der-eingabe`, `BEO-TDS/schranken-test-mit-ungeprueftem-zaehler`, `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor`, `BEO-TDS/betreiberanforderung-benutzerverwaltung-fehlt`, `BEO-TDS/adapter-festlegungen-ohne-slice`.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
