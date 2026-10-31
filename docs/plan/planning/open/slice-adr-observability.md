# Slice slice-adr-observability: Entscheidung: Logformat, Observability-Schnittstelle und Zugang zu Metriken und Spans

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-betrieb-diagnose

**Bezug:** [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit), [TDS-QA-04](../../../../spec/lastenheft.md#tds-qa-04--performance), [ADR-0003](../../adr/0003-transportframework.md), [ADR-0009](../../adr/0009-authentifizierungsmodus.md)

**Berührte Spec-Stellen:** SPEC-012 · SPEC-017 · SPEC-018 · SPEC-019 · SPEC-041 · ARC-009 · ARC-014 · [TDS-FA-09.a](../../../../spec/spezifikation.md#tds-fa-09a--konfiguration-und-diagnose)

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

**Ziel:** Die Festlegungen, auf denen Logging und Telemetrie ruhen — Format und Ziel der Logzeilen, Form und Ort der Observability-Schnittstelle, Bibliothekswahl (keine, JDK oder Fremdbibliothek), Zugang zu Metriken und Spans, Herkunft der Korrelations-IDs und der Wert von `client_profile`, das Logfeld für die Anmeldekennung und der Schalter für Parameterwerte — liegen als Entscheidung des Nutzers in einer neuen ADR vor.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Code von Schnittstelle, Adapter, Ereignissen und Metriken — [slice-betrieb-log-schnittstelle](slice-betrieb-log-schnittstelle.md), [slice-betrieb-zustandsereignisse](slice-betrieb-zustandsereignisse.md) und [slice-betrieb-metriken-tracing](slice-betrieb-metriken-tracing.md); dieser Slice entscheidet und baut nicht. Spike-Code liegt im Scratchpad und nie im Repo.
- Lastkosten der Telemetrie — ohne Produktivcode nicht messbar; die Messung trägt [slice-qa-performance](slice-qa-performance.md), jede Aussage dazu bliebe hier eine Annahme.
- Die Loginspektion über das Gesamtsystem — [slice-betrieb-log-schnittstelle](slice-betrieb-log-schnittstelle.md); die ADR legt nur fest, welche Felder es gibt, nicht den Gate-Test.
- Änderungen am Lastenheft oder die Zeile des Lastenhefts, die Logging verlangt ([TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit)) — die ADR schärft die Spezifikation, nicht das Lastenheft.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **Optionen mit Messung:** Zu jedem Entscheidungspunkt aus §6 stehen zwei bis drei Optionen; Aussagen über Aufwand tragen einen Spike im Scratchpad: (a) Logausgabe — eigener schmaler Schreiber auf der JDK-Schnittstelle gegenüber einer Fremdbibliothek, mit Folgen für Classpath, Sperre und Prüfsummen (`make deps-lock`) und für die Importregeln der Schichten (SPEC-041); (b) Telemetrie — eigene kleine Schnittstelle mit Sammler gegenüber der Schnittstelle einer Telemetrie-Bibliothek, mit Größe der Abhängigkeiten; (c) Zugang zu Metriken — Schnittstelle mit Sammler im Test, JMX oder ein Text-Endpunkt, jeweils mit zusätzlicher Angriffsfläche (neuer Port, Authentifizierung). Messwerte stehen als Tabelle im Slice und in der Entscheidung.
- [ ] **Entscheidung des Nutzers** zu den Punkten aus §6, festgehalten als neue ADR (nächste freie Nummer laut ADR-Index, Status `Accepted` nach Review und Verifikation, `Bezug` auf [ADR-0003](../../adr/0003-transportframework.md) und [ADR-0009](../../adr/0009-authentifizierungsmodus.md), ohne `Supersedes`, Index und Geschichte nachgezogen); jede Aussage trägt eine Messzeile oder ist als Setzung des Nutzers gekennzeichnet.
- [ ] **Anschlussstellen** nachgezogen: Spezifikation §4 (Pflicht-Logfelder, das Feld für die Anmeldekennung, `client_profile`) und §5, Start-Trigger und Risiken der drei Folge-Slices, und die Antwort auf die Frage, ob eine Vertragsdatei für die Observability-Schnittstelle entsteht (Frage 6 in §6).

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
| `docs/plan/adr/` — neue ADR <!-- d-check:ignore (geplant: neue Datei) --> | neu | Entscheidung samt Optionen, Messzeilen und Offen-Liste mit Trägern |
| `docs/plan/adr/README.md` | update | ADR-Index (AGENTS.md §5, Regel 3) |
| `spec/spezifikation.md` | update | Abschnitte 4 und 5 nach der Entscheidung; Rang 2 |
| Slices der Welle | update | Start-Trigger und Risiken an die Entscheidung anschließen |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): Der Start-Trigger der Welle [welle-betrieb-diagnose](../welle-betrieb-diagnose.md) ist eingetreten und ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2; Schnittvorschlag: Log und Telemetrie als zwei Entscheidungen.
- `in-progress` → `open` (blockiert — Carveout?): die Entscheidung des Nutzers steht aus — der Slice bleibt in `open/`, bis sie vorliegt.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, die ADR steht im Index auf `Accepted`, `make gates` ist grün, der Review-Report liegt vor, und die Closure-Notiz ordnet jeden offenen Punkt der ADR einem Träger zu und trägt den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Logformat und Ziel (Frage 1): Zeilen mit festen Feldern als Text oder als JSON, auf Standardausgabe oder Datei; eine Fremdbibliothek für JSON im Produktivpfad ist eine Lieferketten-Entscheidung (AGENTS.md §3.1). — **Ausgang:** offen bis zur Closure.
- Ort der Schnittstelle (Frage 2): ARC-009 lässt `protocol` und `transport` „Observability-Schnittstellen“ importieren, `observability` selbst nur `domain`; ob die Schnittstelle in `domain` oder in einem eigenen Package liegt, legt die Architektur nicht fest. — **Ausgang:** offen bis zur Closure.
- Logfeld für die Anmeldekennung (Frage 3): die Spezifikation nennt dafür „bisher keines“. Ob die Kennung für die Rückverfolgung abgelehnter Anmeldungen im Log erscheint oder nie, ist eine Abwägung zwischen Betrieb und Enumeration; die ADR entscheidet sie mit Begründung, und die Spezifikation führt das Feld erst danach. — **Ausgang:** offen bis zur Closure.
- Parameterwerte im Log (Frage 4): die Spezifikation sagt „standardmäßig nicht“; ob es einen Schalter gibt, der sie einschaltet, ist offen — eine Einschaltung wäre eine Abschwächung nach AGENTS.md §3.6 und braucht dann eine sichtbare Konfiguration wie `ENCRYPTION_POLICY`. — **Ausgang:** offen bis zur Closure.
- Wert von `client_profile` und Herkunft der IDs (Frage 5): das Pflicht-Logfeld hat keine definierte Quelle (Profilname aus der Konfiguration oder aus den Verbindungsoptionen des Clients); Format von `connection_id`, `session_id` und `request_id` (Zähler oder Zufall) ist offen. — **Ausgang:** offen bis zur Closure.
- Vertragsdatei (Frage 6): Es gibt Vertragsabschnitte in `spec/spezifikation.md` §6 für Client-Profil, Backend, Authentifizierung und Sicherheit, aber keinen für die Observability-Schnittstelle; ob die ADR genügt oder ein Vertragsabschnitt entsteht, ist offen. — **Ausgang:** offen bis zur Closure.
- Der minimale Log-Anschluss aus [slice-handshake-login](../done/welle-handshake-login/slice-handshake-login.md) soll diese Schnittstelle nicht vorwegnehmen; die ADR legt fest, wie er abgelöst wird. — **Ausgang:** offen bis zur Closure.
- Die Spikes laufen im Scratchpad ohne Produktivcode; was sie über Verhalten unter Last sagen, ist keine Messung am Produkt (`BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`). — **Ausgang:** offen bis zur Closure.
- Offene Punkte der ADR brauchen je einen Träger als Datei (`BEO-TDS/offene-punkte-ohne-traeger`); eine neue Abhängigkeit im Katalog ohne `make deps-lock` bleibt grün (`BEO-TDS/katalog-und-lock-ohne-gegenpruefung`) — die Spikes ändern den Katalog nicht im Repo. — **Ausgang:** offen bis zur Closure.

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

- `BEO-TDS/aussagen-ueber-messung-hinaus` — 16×, Stand *verkörpert* (`AGENTS.md` §3.8, Reviewer-Skill). Betrifft diesen Slice: Jede Aussage der ADR trägt eine Messzeile oder ist als Setzung gekennzeichnet.
- `BEO-TDS/offene-punkte-ohne-traeger` — 6×, Stand *verkörpert* (`.claude/commands/implement-slice.md`). Betrifft die Offen-Liste der neuen ADR (§6).
- `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` — 3×, Stand *geplant* (Träger [slice-qa-sicherheit](../in-progress/slice-qa-sicherheit.md)). Betrifft das Logfeld für die Kennung und die Redaktion von Passwort und Prüfwert (§6); die Gesamtinspektion trägt [slice-betrieb-log-schnittstelle](slice-betrieb-log-schnittstelle.md).
- `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` — 1×, offen. Betrifft jede Aussage der Spikes über Verhalten unter Last (§6).
- `BEO-TDS/katalog-und-lock-ohne-gegenpruefung` — 1×, offen. Betrifft einen Spike, der eine Bibliothek in den Katalog nähme (§6).
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — 1×, offen. ADR, Slice und Closure-Notiz nennen `ADR-*`- und `TDS-*`-Kennungen als Link.
- Ohne Treffer: `BEO-TDS/messung-am-echten-sql-server-fehlt`, `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`, `BEO-TDS/vergleichslauf-d-ungeklaert`, `BEO-TDS/spec-einzelfaelle-ohne-lastenheft-deckung`, `BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen`, `BEO-TDS/strukturtest-ohne-variation-der-eingabe`, `BEO-TDS/schranken-test-mit-ungeprueftem-zaehler`, `BEO-TDS/test-stage-cache-verdeckt-wiederholung`, `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor`, `BEO-TDS/ablage-im-repo-bricht-docs-check`, `BEO-TDS/java-quelle-umgeht-kotlin-regel`, `BEO-TDS/betreiberanforderung-benutzerverwaltung-fehlt`, `BEO-TDS/adapter-festlegungen-ohne-slice`.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
