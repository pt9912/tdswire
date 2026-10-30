# Slice slice-release-doku-prozess: Nutzer- und Maintainer-Doku, Release-Schritte und Wächter für den detekt-Stabil-Release und die vendored Baseline

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-referenzabgleich-release

**Bezug:** [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-RB-05](../../../../spec/lastenheft.md#tds-rb-05--lizenz), [ADR-0010](../../adr/0010-detekt-pre-release.md)

**Berührte Spec-Stellen:** SPEC-041 · SPEC-042

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

**Ziel:** Die Nutzer-Doku in `docs/user/` <!-- d-check:ignore (geplant: neues Verzeichnis) --> beschreibt den Ist-Zustand von Betrieb und Backend-Anbindung, `docs/maintainer/releasing.md` <!-- d-check:ignore (geplant: neue Datei) --> beschreibt die Release-Schritte; der Re-Evaluierungs-Trigger von [ADR-0010](../../adr/0010-detekt-pre-release.md) (Stabil-Release von detekt 2.x) und der Freshness-Audit der vendored Baseline haben je einen benannten Wächter.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Die Kompatibilitätsmatrix der Nachrichten und Datentypen — [slice-qa-wartbarkeit](../in-progress/slice-qa-wartbarkeit.md) führt sie unter `docs/plan/` (`AGENTS.md` §5, Regel 6); die Nutzer-Doku verlinkt sie und kopiert sie nicht, weil Kopien driften.
- Chronik, Forensik und Planung in der Nutzer-Doku — `AGENTS.md` §5, Regel 5: Was war, steht in `git`; was kommt, in `docs/plan/planning/`.
- Ein Sensor, der die Release-Liste des Kurs-Repos abruft — `harness/conventions.md` führt ihn als *kommt nicht mit*, weil er Netzzugriff über `bash`, `git` und `docker` hinaus bräuchte; der Audit bleibt eine Handlung im Release-Ablauf.
- Der Wechsel von detekt auf eine 2.x-Stabilversion — ein eigener Slice nach Eintritt des Triggers, mit neuer ADR (`Supersedes` [ADR-0010](../../adr/0010-detekt-pre-release.md)); [ADR-0010](../../adr/0010-detekt-pre-release.md) ist `Accepted` und bleibt unverändert (`AGENTS.md` §3.5). Eine Lockerung der Gates bräuchte eine ADR (`AGENTS.md` §3.6).
- Veröffentlichung in einer Registry, Signierung und Versionsschema des Releases als Entscheidung — Nutzerentscheidung (Frage in §6); der Slice dokumentiert, was entschieden ist, und erfindet nichts.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **Nutzer-Doku** (Ist-Zustand): Start des Servers im Container (aus [slice-release-artefakt-lizenz](slice-release-artefakt-lizenz.md)), Konfigurationsschlüssel (die Tabelle aus [slice-betrieb-zustandsereignisse](slice-betrieb-zustandsereignisse.md)), Logfelder und Startdiagnose, Metriken und Spans, Anbindung eines Backends über den Port (`contracts/query-backend.md`) und Nutzung des Simulations-Backends (Beschreibung der Tabellen, erkannte Formen als Verweis auf SPEC-036, nicht als Kopie), Grenzen mit Verweis auf die Kompatibilitätsmatrix; die Hinweise in `AGENTS.md` und `harness/README.md`, die `docs/user` und `docs/maintainer` als „falls vorhanden“ führen (mit `d-check:ignore`), werden auf Links umgestellt, soweit das Verzeichnis besteht. <!-- d-check:ignore (Verzeichnisse entstehen mit diesem Slice) -->
- [ ] **Maintainer-Doku** `docs/maintainer/releasing.md` <!-- d-check:ignore (geplant: neue Datei) -->: die Schritte eines Releases (Gates, Version, Artefakt, Lizenz, Tag) nach den Entscheidungen des Nutzers, der Freshness-Audit der vendored Baseline als Schritt (die Release-Liste des Kurs-Repos gegen den gepinnten Tag halten, Ergebnis im Ablauf vermerken) und der Pfad im Rang 6 der Quellen in `harness/README.md`.
- [ ] **Wächter für [ADR-0010](../../adr/0010-detekt-pre-release.md):** Der Trigger „Stabil-Release von detekt 2.x erscheint“ hat einen benannten Träger in der Form, die der Nutzer wählt (Frage in §6): ein Schritt in `releasing.md`, oder zusätzlich ein Werkzeug-Target außerhalb von `make gates`, das in Docker die Versionsliste abfragt und meldet; der Ausgang beim Eintreten ist ein Slice mit neuer ADR.

Nachweis-Pflichten für alle drei Punkte:

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
| `docs/user/` <!-- d-check:ignore (geplant: neues Verzeichnis) --> | neu | Ist-Zustand für Betreiber und Integratoren |
| `docs/maintainer/releasing.md` <!-- d-check:ignore (geplant: neue Datei) --> | neu | Release-Schritte, Freshness-Audit, Wächter |
| `harness/README.md`, `AGENTS.md` | update | Hinweise „falls vorhanden“ auf Links umstellen; Target des Wächters in §Sensors, falls eines entsteht |
| `tools/mk/` | update | nur falls der Nutzer ein Werkzeug-Target für den Wächter wählt (kein Gate) |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [welle-betrieb-diagnose](../welle-betrieb-diagnose.md), [welle-simulations-backend](../welle-simulations-backend.md) und [slice-release-artefakt-lizenz](slice-release-artefakt-lizenz.md) liegen in `done/` (Betrieb, Backend-Anbindung und Start im Container existieren, die beschrieben werden), und ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2; Schnittvorschlag: Nutzer-Doku und Maintainer-Doku als zwei Slices.
- `in-progress` → `open` (blockiert — Carveout?): die Entscheidungen des Nutzers zu Versionsschema und Veröffentlichung stehen aus.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün (einschließlich `make docs-check`), der Review-Report liegt vor, und die Closure-Notiz trägt die Form des Wächters, das Ergebnis eines ersten Freshness-Audits und den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Die Nutzer-Doku sagt nur, was gebaut und belegt ist (`AGENTS.md` §3.8); Setzungen stehen als solche, und die Doku entsteht nach den Wellen, die sie beschreiben, nicht davor. — **Ausgang:** offen bis zur Closure.
- Die dokumentierten Konfigurationsschlüssel können vom Code abweichen, ohne dass ein Sensor es meldet (verwandt mit `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor`); ob ein Test die dokumentierte Schlüsselliste gegen die Konfiguration hält, ist offen (Frage in §6). — **Ausgang:** offen bis zur Closure.
- Der Wächter für [ADR-0010](../../adr/0010-detekt-pre-release.md) braucht Netz, wenn er die Versionsliste abfragt; ein Gate bleibt netzlos, das Target ist ein Werkzeug ohne Gate-Wirkung. Ohne Werkzeug ist der Wächter ein Schritt, der vergessen werden kann — die Grenze der Deklaration steht in der Closure. — **Ausgang:** offen bis zur Closure.
- Der Freshness-Audit der vendored Baseline ist eine geschuldete Handlung ohne Sensor (`harness/conventions.md`); der Schritt in `releasing.md` ist sein Träger, nicht seine Durchsetzung. — **Ausgang:** offen bis zur Closure.
- Neue Verzeichnisse unter `docs/` laufen durch das Doku-Gate; `ADR-*`- und `TDS-*`-Kennungen müssen als Link stehen (`BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten`). — **Ausgang:** offen bis zur Closure.
- Lizenzhinweise zu Abhängigkeiten im Release sind eine offene Rechtsfrage ([slice-release-artefakt-lizenz](slice-release-artefakt-lizenz.md), §6); die Doku behauptet dazu nichts. — **Ausgang:** offen bis zur Closure.

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

- `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor` — 1×, offen. Verwandt: die Schlüsselliste der Doku kann hinter dem Code zurückbleiben, ohne dass ein Sensor es meldet (§6).
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — 1×, offen. Betrifft alle neuen Dokumente dieses Slice: Kennungen als Link, `make docs-check` ist das Gate.
- `BEO-TDS/katalog-und-lock-ohne-gegenpruefung` — 1×, offen. Betrifft den Wächter: der detekt-Pin steht im Katalog und im Lock, ohne Gegenprüfung durch ein Gate.
- `BEO-TDS/aussagen-ueber-messung-hinaus` — 16×, Stand *verkörpert* (`AGENTS.md` §3.8, Reviewer-Skill). Betrifft diesen Slice: Jede Aussage der Doku über das Verhalten des Servers trägt einen Beleg oder ist als Festlegung gekennzeichnet.
- `BEO-TDS/messung-am-echten-sql-server-fehlt` — 9×, Stand *geplant* (Träger [slice-qa-reproduzierbarkeit](../in-progress/slice-qa-reproduzierbarkeit.md)). Betrifft die Beschreibung von Fehlertexten und -nummern: gemessen ist nur, was der Abgleich zeigt.
- Ohne Treffer: `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`, `BEO-TDS/vergleichslauf-d-ungeklaert`, `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`, `BEO-TDS/offene-punkte-ohne-traeger`, `BEO-TDS/spec-einzelfaelle-ohne-lastenheft-deckung`, `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut`, `BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen`, `BEO-TDS/strukturtest-ohne-variation-der-eingabe`, `BEO-TDS/schranken-test-mit-ungeprueftem-zaehler`, `BEO-TDS/test-stage-cache-verdeckt-wiederholung`, `BEO-TDS/ablage-im-repo-bricht-docs-check`, `BEO-TDS/java-quelle-umgeht-kotlin-regel`, `BEO-TDS/betreiberanforderung-benutzerverwaltung-fehlt`, `BEO-TDS/adapter-festlegungen-ohne-slice`.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
