# Slice slice-betrieb-log-schnittstelle: Strukturiertes Log: Schnittstelle, Pflichtfelder, Adapter und Ablösung der minimalen Log-Anschlüsse

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-betrieb-diagnose

**Bezug:** [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit), [TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [ADR-0009](../../adr/0009-authentifizierungsmodus.md)

**Berührte Spec-Stellen:** SPEC-012 · SPEC-017 · SPEC-043 · SPEC-048 · ARC-009 · [TDS-FA-09.a](../../../../spec/spezifikation.md#tds-fa-09a--konfiguration-und-diagnose)

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

**Ziel:** Alle Betriebsereignisse laufen über eine Log-Schnittstelle, deren Ereignismodell die Pflichtfelder der Spezifikation (`connection_id`, `session_id`, `request_id`, `event`, `duration_ms`, `error_code`, `client_profile`) trägt und für Passwort, Prüfwert, Parameterwert und SQL-Text kein Feld hat; ein Adapter schreibt sie im Format der ADR, und die minimalen Log-Anschlüsse der Wellen rufen die Schnittstelle.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Metriken und Spans — [slice-betrieb-metriken-tracing](slice-betrieb-metriken-tracing.md); dieser Slice trägt nur das Log.
- Welche Zustandsereignisse der Server meldet (Start, Bindung, Beenden, Login) und die Startdiagnose als Prozesstest — [slice-betrieb-zustandsereignisse](slice-betrieb-zustandsereignisse.md); hier wird die Schnittstelle gebaut und werden die vorhandenen Aufrufstellen umgestellt, ohne neue Ereignisarten zu erfinden.
- Die Loginspektion über das Gesamtsystem als Gate (Passwort nie im Log, TLS-Modi, Konfigurationsnegativtests) — [slice-qa-sicherheit](slice-qa-sicherheit.md); dieser Slice liefert den Sammler-Adapter und die Redaktionstests je Ereignisart, nicht den Gate-Test des laufenden Gesamtsystems.
- Kopien des Passworts im Speicher von JVM und Adapter — `BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen`; das Log betrifft die Ausgabe, nicht den Speicher.
- Rotation, Aggregation und Versand der Logzeilen — Betreiber und Log-Backend (ARC-014 ist über die Schnittstelle austauschbar); der Server schreibt auf den Kanal der ADR.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **Schnittstelle und Ereignismodell:** Typen im Package nach der ADR aus [slice-adr-observability](slice-adr-observability.md); geschlossene Menge von Ereignisarten (`event`), `error_code` aus den `TDS_*`-Codes, Korrelations-IDs je Verbindung, Sitzung und Anfrage, die an die aufrufenden Schichten weitergereicht werden; das Modell hat kein Feld für Passwort, Prüfwert, Parameterwert und SQL-Text, und die Anmeldekennung steht nur im Feld, das die ADR dafür festlegt.
- [ ] **Adapter und Konfiguration:** Ausgabe nach Format und Ziel der ADR; die Log-Ebene (und, falls die ADR es vorsieht, das Format) ist über die Konfiguration einstellbar, ein ungültiger Wert bricht den Start ab (Schlüssel und Validierung im Rahmen von [slice-bootstrap-konfiguration](slice-bootstrap-konfiguration.md)); ein Sammler-Adapter für Tests existiert als zweiter Adapter.
- [ ] **Ablösung und Redaktion:** Die minimalen Log-Anschlüsse aus [slice-handshake-login](../done/slice-handshake-login.md), [slice-transport-listener-lebenszyklus](../in-progress/slice-transport-listener-lebenszyklus.md), [slice-bootstrap-konfiguration](slice-bootstrap-konfiguration.md), [slice-fehler-mapping](../next/slice-fehler-mapping.md) und [slice-abbruch-ressourcen-shutdown](slice-abbruch-ressourcen-shutdown.md) rufen die Schnittstelle (Aufrufstellen umgestellt, Verhalten unverändert); ein Redaktionstest je Ereignisart gibt Eingaben mit Passwort, Prüfwert, Parameterwert, SQL-Text, Pfad und Inhalt von Zertifikat und Schlüssel sowie Ausnahmetext (SPEC-043, SPEC-048) hinein und findet nichts davon in der Zeile — mit je verschiedenen Eingabewerten.

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
| `src/main/kotlin/io/github/pt9912/tdswire/observability/` <!-- d-check:ignore (geplant: neue Datei) --> | neu | Adapter; importiert nur `domain` und die Schnittstelle (Importregeln der Schichten) |
| Schnittstelle und Ereignismodell <!-- d-check:ignore (geplant: Ort nach ADR) --> | neu | Ort nach der ADR (Frage 2 in [slice-adr-observability](slice-adr-observability.md)) |
| Aufrufstellen der minimalen Log-Anschlüsse in `transport`, `protocol`, `config`, `runtime` | update | Umstellung auf die Schnittstelle |
| `src/test/kotlin/io/github/pt9912/tdswire/observability/` <!-- d-check:ignore (geplant: neue Datei) --> | neu | Redaktionstests Happy/Boundary/Negative; `@spec`-Tag SPEC-012, SPEC-043, SPEC-048 |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-adr-observability](slice-adr-observability.md), [slice-handshake-login](../done/slice-handshake-login.md), [slice-bootstrap-konfiguration](slice-bootstrap-konfiguration.md) und [slice-transport-listener-lebenszyklus](../in-progress/slice-transport-listener-lebenszyklus.md) liegen in `done/` (die Aufrufstellen existieren), und ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2 oder die Ablösung sprengt den Rahmen; Schnittvorschlag: Ablösung der Aufrufstellen als eigener Slice.
- `in-progress` → `open` (blockiert — Carveout?): ein Slice, dessen Aufrufstelle umzustellen ist, ist noch in Arbeit — die Umstellung wartet auf dessen `done/`.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün (einschließlich `make test`), der Review-Report liegt vor, und die Closure-Notiz trägt die Zuordnung *Test | Festlegung* je Aussage sowie den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Die Ablösung berührt Code, den andere Slices schreiben; sie läuft erst, wenn deren Slice in `done/` liegt, damit kein Slice unter einem anderen die Aufrufstelle wechselt. — **Ausgang:** offen bis zur Closure.
- Die Garantie „kein Feld für Geheimnisse“ gilt nur, solange das Modell kein Freitextfeld trägt, in das ein Aufrufer einen Wert schreibt; die ADR entscheidet, ob es eines gibt, und der Redaktionstest prüft den Ausnahmetext (SPEC-048) ausdrücklich. — **Ausgang:** offen bis zur Closure.
- Der Redaktionstest prüft die Ereignisarten, die es bei der Closure gibt; eine spätere Ereignisart ohne Test fällt nicht auf. Die Closure nennt die Grenze (`BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut`). — **Ausgang:** offen bis zur Closure.
- Schalter für Parameterwerte (Frage 4 in [slice-adr-observability](slice-adr-observability.md)): Gibt es ihn, braucht er einen Test, der den Standard ohne Werte belegt; die Einschaltung erscheint im Log wie die Abschwächung der Verschlüsselung. — **Ausgang:** offen bis zur Closure.
- Blockierendes Schreiben auf einen vollen Ausgabekanal könnte den Anfragepfad anhalten; ungemessen, die Entscheidung über asynchrone Ausgabe steht in der ADR, die Messung unter Last in [slice-qa-performance](slice-qa-performance.md). — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`). Die Observability-Schicht (ARC-009) ist ein Pfad-Cluster (Achse 3) mit eigener Importregel (Achse 1 plausibel); eine eigenständig abgleichbare Inventur-Linie (Achse 2) besteht erst mit dem Code. Der Modus Greenfield bliebe derselbe, eine eigene Zeile in `harness/conventions.md` löste keine andere Regel aus; `*` bleibt, eine Ausdifferenzierung ist Sache des Nutzers und hier nicht vorgenommen.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, 19 Beobachtungen, Stand des Worktrees auf `main`); alle tragen die Sub-Area `*`. Treffer mit Zähler-Stand (Zahl der Dateien unter `evidence/`):

- `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` — 3×, Stand *geplant* (Träger [slice-qa-sicherheit](slice-qa-sicherheit.md)). Betrifft die Redaktion von Passwort und Prüfwert im Log; die Gesamtinspektion bleibt bei [slice-qa-sicherheit](slice-qa-sicherheit.md) (§1).
- `BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen` — 1×, offen. Betrifft die Speicherkopien, nicht die Ausgabe; ausdrücklich nicht Gegenstand (§1).
- `BEO-TDS/strukturtest-ohne-variation-der-eingabe` — 1×, offen. Betrifft den Redaktionstest: die Eingabewerte variieren je Lauf (§2).
- `BEO-TDS/aussagen-ueber-messung-hinaus` — 16×, Stand *verkörpert* (`AGENTS.md` §3.8, Reviewer-Skill). Betrifft diesen Slice: Jede Aussage über „nie im Log“ nennt die geprüften Ereignisarten (§6).
- `BEO-TDS/test-stage-cache-verdeckt-wiederholung` — 2×, offen. Betrifft Tests in der Docker-Stage: gecachte grüne Läufe belegen Wiederholung nicht.
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — 1×, offen. Slice und Closure-Notiz nennen `ADR-*`- und `TDS-*`-Kennungen als Link.
- Ohne Treffer: `BEO-TDS/messung-am-echten-sql-server-fehlt`, `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`, `BEO-TDS/vergleichslauf-d-ungeklaert`, `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`, `BEO-TDS/offene-punkte-ohne-traeger`, `BEO-TDS/spec-einzelfaelle-ohne-lastenheft-deckung`, `BEO-TDS/schranken-test-mit-ungeprueftem-zaehler`, `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor`, `BEO-TDS/ablage-im-repo-bricht-docs-check`, `BEO-TDS/katalog-und-lock-ohne-gegenpruefung`, `BEO-TDS/java-quelle-umgeht-kotlin-regel`, `BEO-TDS/betreiberanforderung-benutzerverwaltung-fehlt`, `BEO-TDS/adapter-festlegungen-ohne-slice`.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
