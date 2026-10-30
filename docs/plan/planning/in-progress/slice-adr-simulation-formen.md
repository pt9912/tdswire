# Slice slice-adr-simulation-formen: Entscheidung: erkannte Anweisungsformen, Typen und Beschreibungsform des Simulations-Backends

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-simulations-backend

**Bezug:** [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md)

**Berührte Spec-Stellen:** SPEC-035 · SPEC-036 · SPEC-037 · SPEC-038 · SPEC-039 · ARC-016 · [TDS-FA-11.a](../../../../spec/spezifikation.md#tds-fa-11a--simulations-backend)

**Verantwortlich:** pt9912.

**Autor:** pt9912. **Datum:** 2026-10-30.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Die Festlegung, die das Lastenheft unter „Offene Entscheidungen“ für das Simulations-Backend führt (erkannte Anweisungsformen, Bedingungen und Typen), liegt als Entscheidung des Nutzers in einer neuen ADR vor, und SPEC-035 sowie SPEC-036 sind auf sie fortgeschrieben; ohne die Festlegung gilt [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) nach dem Lastenheft als nicht abnehmbar.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Änderung des Lastenhefts — die Zeile unter „Offene Entscheidungen“ steht im Rang über der ADR; eine ADR schärft die Spezifikation, nie das Lastenheft. Ob die Zeile als erledigt geführt wird, entscheidet der Nutzer.
- Code der Erkennung und des Backends — [slice-sim-erkennung](../open/slice-sim-erkennung.md), [slice-sim-auswahl-streaming](../open/slice-sim-auswahl-streaming.md) und [slice-sim-aenderung-integration](../open/slice-sim-aenderung-integration.md); dieser Slice entscheidet und baut nicht. Spike-Code liegt im Scratchpad und nie im Repo.
- Fehlernummer, Severity und Verbindungsfolge der Codes `TDS_UNSUPPORTED_SQL`, `TDS_SIM_UNKNOWN_OBJECT` und `TDS_SIM_INVALID_VALUE` — [slice-fehler-mapping](../in-progress/slice-fehler-mapping.md) (Welle [welle-fehler-abbruch](../welle-fehler-abbruch.md)); hier fallen nur die Bedingungen, unter denen sie entstehen.
- Füllung der Form C (Texte, die der Referenzclient selbst stellt) durch Messung — die Messung von `SET`-Text bei `SchemaOnly` und `KeyInfo` trägt [slice-qa-kompatibilitaet](../open/slice-qa-kompatibilitaet.md); bis dahin bleibt Form C leer, oder der Nutzer legt eine Liste fest (Frage 3 in §6).
- Weitere Messung der SQL-Server-Semantik (dreiwertige Logik, Bezeichner-Vergleich) am echten Server — [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) misst eine Stichprobe; die Breite trägt der Referenzabgleich ([slice-referenzabgleich-live](../open/slice-referenzabgleich-live.md)).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **Optionen mit Messung:** Zu jedem Entscheidungspunkt aus §6 stehen zwei bis drei Optionen; wo eine Aussage über Aufwand oder Verhalten nötig ist, trägt sie ein Spike im Scratchpad: (a) Beschreibungsform — Kotlin-Schnittstelle allein (Skizze in SPEC-035) gegenüber zusätzlich einer deklarativen Datei; für die Datei der Aufwand eines Parsers ohne Fremdbibliothek, weil `simulation` nur `domain`, `port` und die Laufzeitbibliothek für asynchrone Streams importieren darf; (b) Grenzfälle des Zeichenscanners (Literale mit `;`, `--`, `/* */`, `[…]`, `"…"`, `N'…'` mit verdoppeltem Hochkomma); (c) Überführung eines Literals oder Parameters in den Spaltentyp. Messwerte stehen als Tabelle im Slice und in der Entscheidung.
  *Stand:* Der Haken steht offen, weil der Satz „Messwerte stehen als Tabelle im Slice“ nicht erfüllt ist: Optionsbericht und Nachtrag unter `docs/reviews/` tragen die Rohtabellen, [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) §Messzeilen die tragenden Zeilen, der Slice verweist (eine dritte Fassung derselben Zahlen würde driften). Der Vorschlag an Planner und Nutzer liegt im Bericht der Fix-Runde: den Satz auf „Messwerte stehen als Tabelle in der Entscheidung, die Rohtabellen in den Berichten“ ändern, danach gilt der Haken. Der Aufwand eines YAML-Parsers ohne Fremdbibliothek ist nicht gemessen; die ADR führt ihn als verworfene Alternative mit diesem Vermerk.
- [ ] **Entscheidung des Nutzers** zu den Punkten aus §6, festgehalten als neue ADR ([ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) liegt als `Proposed` vor; offen bleibt `Accepted` nach Review und Verifikation; nächste freie Nummer laut ADR-Index, `Bezug` auf [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md), ohne `Supersedes`, Index und Geschichte nachgezogen); jede Aussage der Entscheidung trägt eine Messzeile oder ist als Setzung des Nutzers gekennzeichnet.
- [x] **Anschlussstellen** nachgezogen: SPEC-035 und SPEC-036 (das Wort „vorläufig“ entfällt nur dort, wo die Entscheidung es trägt), Start-Trigger und Risiken der drei Folge-Slices, und die Antwort auf die Frage, ob eine Vertragsdatei `contracts/simulation-backend.md` <!-- d-check:ignore (geplant: neue Datei) --> entsteht (Frage 2 in §6).
  *Stand:* SPEC-035 und SPEC-036 fortgeschrieben (SPEC-036 behält das Wort „vorläufig“ in der Überschrift: der Anker wird von [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) (`Accepted`) verlinkt, [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) steht noch auf `Proposed` und trägt den Anker erst nach `Accepted`; bis dahin trägt der Sachgrund allein: Vergleich von Bezeichnern, Werten und Überführung sind nicht am Server gemessen); keine Vertragsdatei (Frage 2: Antwort A, Voreinstellung); Folge-Slices angeschlossen, zwei Risikozeilen in [slice-codec-datentypen-erweiterung](../open/slice-codec-datentypen-erweiterung.md) und [slice-referenzabgleich-live](../open/slice-referenzabgleich-live.md) tragen die offenen Punkte der ADR dorthin.

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
| `spec/spezifikation.md` | update | SPEC-035 und SPEC-036 nach der Entscheidung; Rang 2, schärft das Lastenheft nicht |
| Slices der Welle | update | Start-Trigger und Risiken an die Entscheidung anschließen |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-backend-port-vertrag](../done/slice-backend-port-vertrag.md) liegt in `done/` (Port und `contracts/query-backend.md` stehen auf `main`), der Nutzer hat den Start der Welle [welle-simulations-backend](../welle-simulations-backend.md) ausdrücklich verlangt, und ein Verantwortlicher ist im Kopf eingetragen.

**Abweichung vom Wellen-Start-Trigger, gekennzeichnet als Setzung des Nutzers.** Der Start-Trigger der Welle (Welle [welle-backend-streaming](../welle-backend-streaming.md) in `done/`) ist nicht eingetreten. Dieser Slice braucht keinen Code dieser Welle: Er entscheidet und baut nicht (§1), und was er am Port voraussetzt, liefert [slice-backend-port-vertrag](../done/slice-backend-port-vertrag.md). Die Trigger-Zeile der Welle bleibt für die Code-Slices ([slice-sim-erkennung](../open/slice-sim-erkennung.md), [slice-sim-auswahl-streaming](../open/slice-sim-auswahl-streaming.md), [slice-sim-aenderung-integration](../open/slice-sim-aenderung-integration.md)) unverändert. Die Umplanung steht im Drift-Log der Roadmap.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2; Schnittvorschlag: Beschreibungsform und Anweisungsformen als zwei Entscheidungen.
- `in-progress` → `open` (blockiert — Carveout?): die Entscheidung des Nutzers steht aus — der Slice bleibt in `open/`, bis sie vorliegt.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, die ADR steht im Index auf `Accepted`, `make gates` ist grün, der Review-Report liegt vor, und die Closure-Notiz ordnet jeden offenen Punkt der ADR einem Träger zu (entschieden mit Beleg, weiter offen mit Träger als Datei, oder an eine andere Welle übergeben) und trägt den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Beschreibungsform (Frage 1): Das Lastenheft nennt „Spalten, Typen, Schlüssel“, die Skizze in SPEC-035 kennt keine Schlüssel und keine Datei. Ohne Dateiform lässt sich ein Server mit Simulations-Backend nur mit Integrator-Code starten; der Referenzabgleich ([slice-referenzabgleich-live](../open/slice-referenzabgleich-live.md)) braucht aber ein Schema, das ohne Integrator-Code geladen wird (SPEC-045). — **Ausgang:** offen bis zur Closure.
- Vertragsdatei (Frage 2): Der Backend-Vertrag hat `contracts/query-backend.md`; für das Simulations-Backend gibt es keine Vertragsdatei. Ob die ADR genügt oder eine Vertragsdatei für Integratoren entsteht (Beschreibungsform, Zeilenquelle, Callbacks), ist offen. — **Ausgang:** offen bis zur Closure.
- Die Messung in [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) bestätigt die Annahmen zu dreiwertiger Logik, Groß-/Kleinschreibung und `"…"`-Bezeichnern nur für eine Stichprobe an einer Instanz; die ADR darf daraus keine allgemeine Aussage ableiten (`BEO-TDS/aussagen-ueber-messung-hinaus`). — **Ausgang:** offen bis zur Closure.
- Form C (Frage 3): Das Clientprofil gibt die Texte des Referenzclients erst frei, wenn die Messgrenzen aus SPEC-030 geklärt sind (SPEC-004); eine Liste vor der Messung wäre eine Annahme. — **Ausgang:** offen bis zur Closure.
- SPEC-036 führt „Nicht erkannt“ als Liste von Einzelfällen (`OR`, `NOT`, `<`, `>`, `LIKE`, `BETWEEN`, `TOP`, `DISTINCT`, Aliase); das Lastenheft nennt als Ausschluss Verbund, Aggregat, Ausdruck, Unterabfrage und Sortierung. Welcher Einzelfall durch welche Lastenheft-Zeile gedeckt ist, ist zu belegen, sonst erweitert die Spezifikation das Lastenheft (`BEO-TDS/spec-einzelfaelle-ohne-lastenheft-deckung`). — **Ausgang:** offen bis zur Closure.
- Entscheidungen des Nutzers über das Lastenheft (Offen 12 der ADR): Bestätigung der Lesart „die positive Aufzählung ist abschließend“, Ergänzung des Lastenhefts um `IS [NOT] NULL`, und ob die Zeile „Offene Entscheidungen“ zu [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) als erledigt geführt wird. Der Slice ändert das Lastenheft nicht. — **Ausgang:** offen bis zur Closure (entschieden mit Beleg, oder weiter offen mit einer vom Nutzer benannten Datei als Träger).
- Offene Punkte, die die ADR nach ihrer Annahme führt, brauchen jeweils einen Träger als Datei (`BEO-TDS/offene-punkte-ohne-traeger`); der Slice legt sie vor `Accepted` an oder benennt den Ausgang. — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`). Das Package `simulation` ist ein eigener Pfad-Cluster (Achse 3) mit Importgrenze im Gate (`ForbiddenImport/simulation` in `config/detekt/detekt.yml`; Achse 1 plausibel) und mit einer Doku-Aussage (SPEC-036), die gegen den Code abgleichbar ist (Achse 2 plausibel). Die Schwelle wäre damit erreicht; weil der Modus Greenfield derselbe bliebe und eine eigene Zeile in `harness/conventions.md` keine andere Regel auslöste, bleibt `*`. Eine Ausdifferenzierung ist Sache des Nutzers und hier nicht vorgenommen.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, 19 Beobachtungen, Stand des Worktrees auf `main`); alle tragen die Sub-Area `*`. Treffer mit Zähler-Stand (Zahl der Dateien unter `evidence/`):

- `BEO-TDS/aussagen-ueber-messung-hinaus` — 16×, Stand *verkörpert* (`AGENTS.md` §3.8, Reviewer-Skill). Betrifft diesen Slice: Jede Aussage der ADR trägt eine Messzeile oder ist als Setzung gekennzeichnet (§2, §6).
- `BEO-TDS/messung-am-echten-sql-server-fehlt` — 9×, Stand *geplant* (Träger [slice-qa-reproduzierbarkeit](../done/slice-qa-reproduzierbarkeit.md)). Betrifft die Semantik-Annahmen (SPEC-036): gemessen ist eine Stichprobe, die Breite folgt im Referenzabgleich; die ADR sagt nichts über das Verhalten eines echten Servers über die Stichprobe hinaus.
- `BEO-TDS/offene-punkte-ohne-traeger` — 6×, Stand *verkörpert* (`.claude/commands/implement-slice.md`). Betrifft die Offen-Liste der neuen ADR (§6).
- `BEO-TDS/spec-einzelfaelle-ohne-lastenheft-deckung` — 2×, offen. Betrifft die Liste „Nicht erkannt“ in SPEC-036 (§6).
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — 1×, offen. ADR, Slice und Closure-Notiz nennen `ADR-*`- und `TDS-*`-Kennungen als Link.
- Ohne Treffer: `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`, `BEO-TDS/vergleichslauf-d-ungeklaert`, `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`, `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut`, `BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen`, `BEO-TDS/strukturtest-ohne-variation-der-eingabe`, `BEO-TDS/schranken-test-mit-ungeprueftem-zaehler`, `BEO-TDS/test-stage-cache-verdeckt-wiederholung`, `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor`, `BEO-TDS/ablage-im-repo-bricht-docs-check`, `BEO-TDS/katalog-und-lock-ohne-gegenpruefung`, `BEO-TDS/java-quelle-umgeht-kotlin-regel`, `BEO-TDS/betreiberanforderung-benutzerverwaltung-fehlt`, `BEO-TDS/adapter-festlegungen-ohne-slice`.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
