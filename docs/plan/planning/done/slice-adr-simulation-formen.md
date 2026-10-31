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
- Code der Erkennung und des Backends — [slice-sim-erkennung](../done/slice-sim-erkennung.md), [slice-sim-auswahl-streaming](../open/slice-sim-auswahl-streaming.md) und [slice-sim-aenderung-integration](../open/slice-sim-aenderung-integration.md); dieser Slice entscheidet und baut nicht. Spike-Code liegt im Scratchpad und nie im Repo.
- Fehlernummer, Severity und Verbindungsfolge der Codes `TDS_UNSUPPORTED_SQL`, `TDS_SIM_UNKNOWN_OBJECT` und `TDS_SIM_INVALID_VALUE` — [slice-fehler-mapping](../done/slice-fehler-mapping.md) (Welle [welle-fehler-abbruch](../welle-fehler-abbruch.md)); hier fallen nur die Bedingungen, unter denen sie entstehen.
- Füllung der Form C (Texte, die der Referenzclient selbst stellt) durch Messung — die Messung von `SET`-Text bei `SchemaOnly` und `KeyInfo` trägt [slice-qa-kompatibilitaet](../open/slice-qa-kompatibilitaet.md); bis dahin bleibt Form C leer, oder der Nutzer legt eine Liste fest (Frage 3 in §6).
- Weitere Messung der SQL-Server-Semantik (dreiwertige Logik, Bezeichner-Vergleich) am echten Server — [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) misst eine Stichprobe; die Breite trägt der Referenzabgleich ([slice-referenzabgleich-live](../open/slice-referenzabgleich-live.md)).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [x] **Optionen mit Messung:** Zu jedem Entscheidungspunkt aus §6 stehen zwei bis drei Optionen; wo eine Aussage über Aufwand oder Verhalten nötig ist, trägt sie ein Spike im Scratchpad: (a) Beschreibungsform — Kotlin-Schnittstelle allein (Skizze in SPEC-035) gegenüber zusätzlich einer deklarativen Datei; für die Datei der Aufwand eines Parsers ohne Fremdbibliothek, weil `simulation` nur `domain`, `port` und die Laufzeitbibliothek für asynchrone Streams importieren darf; (b) Grenzfälle des Zeichenscanners (Literale mit `;`, `--`, `/* */`, `[…]`, `"…"`, `N'…'` mit verdoppeltem Hochkomma); (c) Überführung eines Literals oder Parameters in den Spaltentyp. Messwerte stehen als Tabelle in der Entscheidung, Rohtabellen in den Berichten.
  *Stand:* Die Messzeilen M1 bis MV stehen in [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) §Messzeilen, die Rohtabellen im Optionsbericht und im Nachtrag unter `docs/reviews/`. Der Wortlaut des Satzes über die Messwerte lautete „im Slice und in der Entscheidung“; der Planner hat ihn bei der Closure geändert, weil der Slice auf die Zahlen verweist statt eine dritte Fassung zu führen (Vorschlag V-8 im [Verifikationsbericht](../../../reviews/2026-10-30-69c32e6-verifikation-adr-0012-simulations-backend.md); dem Nutzer zur Bestätigung vorgelegt). Der Aufwand eines YAML-Parsers ohne Fremdbibliothek ist nicht gemessen; die ADR führt ihn als verworfene Alternative mit diesem Vermerk.
- [x] **Entscheidung des Nutzers** zu den Punkten aus §6, festgehalten als neue ADR ([ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md), `Accepted` seit 2026-10-31 nach Review, Verifikation, Nachreview und Verifikation 2; nächste freie Nummer laut ADR-Index, `Bezug` auf [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md), ohne `Supersedes`, Index und Geschichte nachgezogen); jede Aussage der Entscheidung trägt eine Messzeile oder ist als Setzung des Nutzers gekennzeichnet.
- [x] **Anschlussstellen** nachgezogen: SPEC-035 und SPEC-036 (das Wort „vorläufig“ entfällt nur dort, wo die Entscheidung es trägt), Start-Trigger und Risiken der drei Folge-Slices, und die Antwort auf die Frage, ob eine ein eigener Vertragsabschnitt in `spec/spezifikation.md` entsteht (Frage 2 in §6).
  *Stand:* SPEC-035 und SPEC-036 fortgeschrieben (SPEC-036 behält das Wort „vorläufig“ in der Überschrift: [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) und [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) (beide `Accepted`) verlinken den Anker, die Überschrift wird nicht umbenannt; der Sachgrund bleibt: Vergleich von Bezeichnern, Werten und Überführung sind nicht am Server gemessen); keine Vertragsdatei (Frage 2: Antwort A, Voreinstellung); Folge-Slices angeschlossen, zwei Risikozeilen in [slice-codec-datentypen-erweiterung](../open/slice-codec-datentypen-erweiterung.md) und [slice-referenzabgleich-live](../open/slice-referenzabgleich-live.md) tragen die offenen Punkte der ADR dorthin.

Nachweis-Pflichten für alle drei Punkte:

- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

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

**Start** (`next` → `in-progress`): [slice-backend-port-vertrag](../done/slice-backend-port-vertrag.md) liegt in `done/` (Port und `spec/spezifikation.md` `SPEC-022` stehen auf `main`), der Nutzer hat den Start der Welle [welle-simulations-backend](../welle-simulations-backend.md) ausdrücklich verlangt, und ein Verantwortlicher ist im Kopf eingetragen.

**Abweichung vom Wellen-Start-Trigger, gekennzeichnet als Setzung des Nutzers.** Der Start-Trigger der Welle (Welle [welle-backend-streaming](../welle-backend-streaming.md) in `done/`) ist nicht eingetreten. Dieser Slice braucht keinen Code dieser Welle: Er entscheidet und baut nicht (§1), und was er am Port voraussetzt, liefert [slice-backend-port-vertrag](../done/slice-backend-port-vertrag.md). Die Trigger-Zeile der Welle bleibt für die Code-Slices ([slice-sim-erkennung](../done/slice-sim-erkennung.md), [slice-sim-auswahl-streaming](../open/slice-sim-auswahl-streaming.md), [slice-sim-aenderung-integration](../open/slice-sim-aenderung-integration.md)) unverändert. Die Umplanung steht im Drift-Log der Roadmap.

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

- Beschreibungsform (Frage 1): Das Lastenheft nennt „Spalten, Typen, Schlüssel“, die Skizze in SPEC-035 kennt keine Schlüssel und keine Datei. Ohne Dateiform lässt sich ein Server mit Simulations-Backend nur mit Integrator-Code starten; der Referenzabgleich ([slice-referenzabgleich-live](../open/slice-referenzabgleich-live.md)) braucht aber ein Schema, das ohne Integrator-Code geladen wird (SPEC-045). — **Ausgang:** entfallen: Kotlin-Schnittstelle und Datei (neutrales Schema-Modell von d-migrate als YAML) sind entschieden ([ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md), Entscheidungen 1 bis 3); die Umsetzung tragen [slice-sim-erkennung](../done/slice-sim-erkennung.md) und [slice-sim-aenderung-integration](../open/slice-sim-aenderung-integration.md).
- Vertragsdatei (Frage 2): Der Backend-Vertrag hat `spec/spezifikation.md` `SPEC-022`; für das Simulations-Backend gibt es keine Vertragsdatei. Ob die ADR genügt oder eine Vertragsdatei für Integratoren entsteht (Beschreibungsform, Zeilenquelle, Callbacks), ist offen. — **Ausgang:** entfallen: keine Vertragsdatei jetzt (Setzung, Voreinstellung 2026-10-30, [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) Setzung 10); eine Vertragsdatei bleibt möglich, wenn ein Integrator sie braucht.
- Die Messung in [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) bestätigt die Annahmen zu dreiwertiger Logik, Groß-/Kleinschreibung und `"…"`-Bezeichnern nur für eine Stichprobe an einer Instanz; die ADR darf daraus keine allgemeine Aussage ableiten (`BEO-TDS/aussagen-ueber-messung-hinaus`). — **Ausgang:** weiter offen: die ADR leitet nichts über die Stichprobe hinaus ab (Verifikation 2); der Servergleich zu Überführung, Textvergleich, Kommentarschachtelung und Rundung liegt im Live-Lauf ([slice-referenzabgleich-live](../open/slice-referenzabgleich-live.md), Offen 6) → [BEO-TDS/messung-am-echten-sql-server-fehlt](../observations/BEO-TDS/messung-am-echten-sql-server-fehlt/observation.md).
- Form C (Frage 3): Das Clientprofil gibt die Texte des Referenzclients erst frei, wenn die Messgrenzen aus SPEC-030 geklärt sind (SPEC-004); eine Liste vor der Messung wäre eine Annahme. — **Ausgang:** weiter offen: Form C bleibt leer (Offen 4 der ADR), Träger [slice-qa-kompatibilitaet](../open/slice-qa-kompatibilitaet.md) → [BEO-TDS/aussagen-ueber-messung-hinaus](../observations/BEO-TDS/aussagen-ueber-messung-hinaus/observation.md).
- SPEC-036 führt „Nicht erkannt“ als Liste von Einzelfällen (`OR`, `NOT`, `<`, `>`, `LIKE`, `BETWEEN`, `TOP`, `DISTINCT`, Aliase); das Lastenheft nennt als Ausschluss Verbund, Aggregat, Ausdruck, Unterabfrage und Sortierung. Welcher Einzelfall durch welche Lastenheft-Zeile gedeckt ist, ist zu belegen, sonst erweitert die Spezifikation das Lastenheft (`BEO-TDS/spec-einzelfaelle-ohne-lastenheft-deckung`). — **Ausgang:** weiter offen: die Tabelle „Deckung“ in der ADR ordnet jeden Einzelfall einer Lastenheft-Zeile oder der Lesart zu; `IS [NOT] NULL` ist seit Lastenheft 0.5.0 wörtlich gedeckt, die Lesart „die positive Aufzählung ist abschließend“ ist vom Nutzer nicht bestätigt → [BEO-TDS/spec-einzelfaelle-ohne-lastenheft-deckung](../observations/BEO-TDS/spec-einzelfaelle-ohne-lastenheft-deckung/observation.md) und [BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt](../observations/BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt/observation.md).
- Entscheidungen des Nutzers über das Lastenheft (Offen 12 der ADR): Bestätigung der Lesart „die positive Aufzählung ist abschließend“ und ob die Zeile „Offene Entscheidungen“ zu [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) als erledigt geführt wird (Offen 12 (a) und (c)); (b), `IS [NOT] NULL`, ist durch das Lastenheft 0.5.0 gedeckt und erledigt. Der Slice ändert das Lastenheft nicht. — **Ausgang:** geteilt nach Punkt, je ein Ausgang. (b) entfallen: durch das Lastenheft 0.5.0 gedeckt und erledigt. (a) und (c) weiter offen, beim Nutzer: Träger als Datei sind für (a) `SPEC-036` (Satz „vom Nutzer nicht bestätigt“) und für (c) die Zeile zu [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) unter „Offene Entscheidungen“ in `spec/lastenheft.md`; die Datei benennt der Nutzer (Frage in der Closure-Notiz) → [BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt](../observations/BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt/observation.md).
- Offene Punkte, die die ADR nach ihrer Annahme führt, brauchen jeweils einen Träger als Datei (`BEO-TDS/offene-punkte-ohne-traeger`); der Slice legt sie vor `Accepted` an oder benennt den Ausgang. — **Ausgang:** entfallen: jeder Punkt der ADR hat einen Träger, gegen den Inhalt der Datei geprüft (Tabelle in §7); die Lücke bei Offen 6 (Verifikation 2, W-1) ist vor `Accepted` geschlossen; Offen 11 trägt die ADR als akzeptiert ohne Träger, Offen 12 (a) und (c) stehen oben.

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

- **Was hat funktioniert:** Der Schnitt trug: Die Entscheidung des Nutzers vom 2026-10-30 liegt als [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) vor, ohne dass die Rückführung nach §4 nötig wurde; SPEC-035 und SPEC-036 sind fortgeschrieben, die Folge-Slices angeschlossen. Die Folge von Review, Verifikation, Nachreview und Verifikation 2 hat jede Behauptung der ADR auf ihren Beleg zurückgeführt (F-1 bis F-13, V-1 bis V-11, N-1 bis N-8 behoben oder ehrlich offen geführt). Die Tabelle „Deckung“ in der ADR machte die Frage sichtbar, welcher Einzelfall von SPEC-036 durch welche Lastenheft-Zeile gedeckt ist, und führte zur Lastenheft-Ergänzung 0.5.0 (`IS [NOT] NULL`, Entscheidung des Nutzers).
- **Was ging anders als geplant:** (a) Die Träger der Offen-Liste standen nach zwei Fix-Runden noch nicht vollständig: Offen 6 war in [slice-referenzabgleich-live](../open/slice-referenzabgleich-live.md) nur halb getragen (Verifikation 2, W-1) und ist vor `Accepted` ergänzt. (b) Die Risikozeile „Lock-Werkzeug“ in [slice-sim-aenderung-integration](../open/slice-sim-aenderung-integration.md) beschrieb einen inzwischen reparierten Zustand (W-2) und nennt jetzt die Folgepflicht. (c) Der DoD-Satz über die Messwerte war erfüllbar nur durch eine dritte Fassung derselben Zahlen; der Wortlaut ist bei der Closure geändert (V-8). (d) Die Fitness Function der ADR behauptete einen Wächter, den es nicht gibt (F-1); sie steht jetzt als „kein Wächter heute“ mit Pflicht im Verdrahtungs-Slice.
- **Reports:** [Review F-1 bis F-13](../../../reviews/2026-10-30-69c32e6-adr-0012-simulations-backend.md) (kein HIGH; F-1 bis F-4 und F-8 MEDIUM), [Verifikation V-1 bis V-11](../../../reviews/2026-10-30-69c32e6-verifikation-adr-0012-simulations-backend.md), [Nachreview N-1 bis N-8](../../../reviews/2026-10-30-1565700-nachreview-adr-0012.md) (N-1, N-2 MEDIUM) und [Verifikation 2](../../../reviews/2026-10-30-cc81155-verifikation-2-adr-0012-simulations-backend.md) (W-1 bis W-6; „inhaltlich Accepted-fähig“, W-1 und W-2 als Slice-Pflege, kein Befund ändert die ADR). W-1 und W-2 sind in diesem Lauf behoben; W-3 (Halbsatz in Messzeile MK) und W-5 (Chronik, Legende) sind nicht nachgezogen, weil die ADR nach `Accepted` unverändert bleibt (`AGENTS.md` §3.5); W-4 (Datum im Lastenheft-Kopf) ist Sache des Nutzers.

**Zuordnung je Aussage der Entscheidung.** Fünf Stärken: *gemessen* heißt im Spike oder an d-migrate 1.12.0 beobachtet, **der Autor der Spikes hat auch die Erwartungen geschrieben** (Autor gleich Erwartender), und **nichts wurde an einem echten SQL Server gemessen**; *Handvektor* heißt ein Fall mit Quelle; *Selbstkonsistenz* heißt Code gegen Code oder Test gegen Fake; *Festlegung* heißt Setzung des Nutzers oder Voreinstellung ohne Messung; *nicht gemessen* nennt, was keine Aussage trägt.

| Stärke | Aussage | Beleg |
|---|---|---|
| gemessen (Spike, Autor gleich Erwartender) | Zeichenscanner erkennt 60 von 60 Eingaben wie erwartet, die Regex-Variante 45 von 54 mit einem still veränderten Wert; Scanner-Laufzeit; Überführung (28 gleiche, zehn unterscheidende Eingaben; JDK-Vergleiche); Lesewege, Fehlerfälle, Robustheit und Abhängigkeitsgröße der YAML-Bibliotheken; Verhalten von d-migrate 1.12.0 (Lesen von YAML und JSON, Tabellen mit Punkt im Namen, zehn von 21 Typen abbildbar); Katalogstand der Version | [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) §Messzeilen M1 bis MV; Rohtabellen in den beiden Optionsberichten |
| Handvektor | keiner: kein Fall mit Quelle außerhalb des Spikes | — |
| Selbstkonsistenz | keine: kein Code und kein Test im Repo; Spike-Code liegt im Scratchpad | — |
| Festlegung (Entscheidung des Nutzers, 2026-10-30) | Kotlin-Schnittstelle und Datei, neutrales Schema-Modell von d-migrate als YAML, Leseweg YAML direkt, Jackson 2.21.7, `IS [NOT] NULL`, strenge verlustfreie Überführung, `max_length` lesen und nicht durchsetzen, Typlücken beim Laden abweisen | ADR §Entscheidungen des Nutzers 1 bis 8 |
| Festlegung (Setzung, Voreinstellung 2026-10-30) | Ort der Bibliothek (Adapter in `runtime`), gelesene Teilmenge, kanonische Skalare, Tabellennamen, Nullbarkeit, Datei nur Schema, Schlüssel beschreibend, Textvergleich ohne Beachtung der Schreibweise, Form C leer, keine Vertragsdatei, Größengrenze 1 MiB, Startabbruch, Anweisungsformen von SPEC-036 | ADR §Setzungen 1 bis 14 |
| nicht gemessen | Verhalten eines SQL Servers zu Überführung, Textvergleich, Kommentarschachtelung, Rundung, Collation und Sitzungsoptionen; die erzeugte DDL gegen eine Referenzinstanz; Aufwand eines YAML-Parsers ohne Bibliothek; Wirkung von Jackson 2.21.8 und 2.22.4; Lesart „die positive Aufzählung ist abschließend“ (vom Nutzer nicht bestätigt) | ADR §Offen 6, 11, 12 (a); Träger unten |

**Offene Punkte der ADR mit Träger** (gegen den Inhalt der Dateien geprüft: jeder Träger existiert im Planning-Lifecycle und führt den Punkt in DoD oder Risikozeile mit Verweis auf die ADR):

| Offener Punkt | Träger | Stand |
|---|---|---|
| Offen 1, 2: Typkatalog und Länge von `UnicodeText` | [slice-codec-datentypen-erweiterung](../open/slice-codec-datentypen-erweiterung.md) | §6; Zuschnitt beim Nutzer |
| Offen 3: Quelle des Skripts der Referenzinstanz (N5), Nachführung von SPEC-045 | [slice-referenzabgleich-live](../open/slice-referenzabgleich-live.md) | §6 |
| Offen 4, 5: Form C, Schlüssel als Metadatum, Identitätsmerkmal | [slice-qa-kompatibilitaet](../open/slice-qa-kompatibilitaet.md) | §6 |
| Offen 6: Servergleiche (Überführung, Textvergleich, Kommentarschachtelung, Rundung, Collation, Sitzungsoptionen, erzeugtes Skript) | [slice-referenzabgleich-live](../open/slice-referenzabgleich-live.md) | §6, seit dieser Closure vollständig (W-1) |
| Offen 7, 8: `precision`/`scale` bei `decimal`, Genauigkeit der Zeittypen, Länge des Anweisungstextes; Belegstand von d-migrate | [slice-sim-erkennung](../done/slice-sim-erkennung.md) | §6 |
| Offen 9: Name der Umgebungsvariable; Abhängigkeit und Lock; Wächter `ForbiddenImport` mit Selbsttest | [slice-sim-aenderung-integration](../open/slice-sim-aenderung-integration.md) | DoD Verdrahtung und §6 |
| Offen 10: JVM-Typen der Werte je `SqlType` | [slice-adr-backend-grenzen](../done/slice-adr-backend-grenzen.md) | §6 (in Arbeit, Architect) |
| Lizenz der Abhängigkeiten im Artefakt | [slice-release-artefakt-lizenz](../in-progress/slice-release-artefakt-lizenz.md) | §6 |
| Offen 11 | kein Träger, von der ADR als einmalig und folgenlos akzeptiert; der Re-Evaluierungs-Trigger fängt jede Änderung | ADR §Offen 11 |
| Offen 12 (a), (c) | `SPEC-036` (Lesart) und die Zeile zu [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) unter „Offene Entscheidungen“; Nutzer | Frage unten |

- **Gegenstand:** entfällt (der Slice lieferte).
- **Steering-Loop-Eintrag:** Eine benannte Spec-Lücke, kein `liegt in`, weil sie in einer versionierten Spec steht und keinen Zielort hat: [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) führt unter „Offene Entscheidungen“ weiter die Zeile „Erkannte Anweisungsformen, Bedingungen und Typen des Simulations-Backends“ (Trigger: vor Implementierung des Simulations-Backends), obwohl die Festlegung als `Accepted` vorliegt; und die Lesart „die positive Aufzählung ist abschließend“, auf der die Deckung der Liste „Nicht erkannt“ in `SPEC-036` beruht, ist vom Nutzer nicht bestätigt. Beides entscheidet der Nutzer (Rang 1); der Entscheider ist er, die Zeile in der Spec ist der Träger. Daneben zwei Feststellungen, gezählt und nicht verkörpert. (1) **Eine Liste von Einzelfällen in Spec oder ADR braucht ihre Deckungstabelle (Einzelfall, Lastenheft-Zeile oder Lesart), bevor sie in die Spec geht.** Ohne sie führte `SPEC-036` die Lesart des Architect als Tatsache (F-2) und `IS [NOT] NULL` ohne Anker (N-2); die Tabelle in der ADR machte beides sichtbar. `BEO-TDS/spec-einzelfaelle-ohne-lastenheft-deckung` erreicht damit 3×: der Lese-Schritt gehört in die Closure der Welle [welle-simulations-backend](../welle-simulations-backend.md), weil sie läuft; die Prüffrage steht im Reviewer-Skill (Kategorie „Die untere Spec-Schicht erweitert die obere“), die Deckungstabelle als Pflichtform ist ein Vorschlag, noch keine Regel. (2) **Eine Fitness-Zeile, die ein Gate nennt, muss sagen, ob das Gate die Aussage prüft** (F-1: `ForbiddenImport/simulation` sperrt nur Pakete des Repos). Der Sensor entsteht im Verdrahtungs-Slice; `BEO-TDS/zusage-ohne-sensor-in-der-ci-kette` steht bei 2×.
- **Beobachtungs-Register (`../observations/`):** `evidence/slice-adr-simulation-formen.md` ergänzt in [BEO-TDS/aussagen-ueber-messung-hinaus](../observations/BEO-TDS/aussagen-ueber-messung-hinaus/observation.md) (Review F-4, F-6, F-7, F-9; Nachreview N-4, N-6), [BEO-TDS/offene-punkte-ohne-traeger](../observations/BEO-TDS/offene-punkte-ohne-traeger/observation.md) (Review F-3, F-8; Nachreview N-1; Verifikation 2 W-1), [BEO-TDS/spec-einzelfaelle-ohne-lastenheft-deckung](../observations/BEO-TDS/spec-einzelfaelle-ohne-lastenheft-deckung/observation.md) (Review F-2; Nachreview N-2; jetzt 3×), [BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt](../observations/BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt/observation.md) (Offen 12 (a)), [BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten](../observations/BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten/observation.md) (Nachreview N-3, N-5), [BEO-TDS/messung-am-echten-sql-server-fehlt](../observations/BEO-TDS/messung-am-echten-sql-server-fehlt/observation.md) (Offen 6) und [BEO-TDS/zusage-ohne-sensor-in-der-ci-kette](../observations/BEO-TDS/zusage-ohne-sensor-in-der-ci-kette/observation.md) (Review F-1; Verifikation V-1). Zwei neue Beobachtungen mit je dieser Evidence-Datei: [BEO-TDS/risikozeile-nennt-behobenen-zustand](../observations/BEO-TDS/risikozeile-nennt-behobenen-zustand/observation.md) (Verifikation 2 W-2) und [BEO-TDS/dod-satz-verlangt-ablage-die-der-slice-ersetzt](../observations/BEO-TDS/dod-satz-verlangt-ablage-die-der-slice-ersetzt/observation.md) (Verifikation V-8). Schwelle: `BEO-TDS/aussagen-ueber-messung-hinaus` und `BEO-TDS/offene-punkte-ohne-traeger` stehen verkörpert (`AGENTS.md` §3.8, `implement-slice.md` Schritt 24); `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` steht über 3× ohne Ausgang, nur gezählt, die Bestätigung ist Sache des Nutzers; `BEO-TDS/spec-einzelfaelle-ohne-lastenheft-deckung` siehe oben.
- **Risiken aus §6:** alle sieben tragen genau einen Ausgang (siehe §6): drei entfallen (Beschreibungsform, Vertragsdatei, Träger der Offen-Liste), drei weiter offen mit Register und Träger (Stichprobe der Messung, Form C, Deckung der Einzelfälle), eines geteilt nach Punkten von Offen 12: (b) entfallen, (a) und (c) weiter offen beim Nutzer.
- **Drei Paarungen:** Anker: der Eintrag trägt kein `liegt in`, die Paarung entfällt. Folge-Slice: jeder genannte Träger existiert als Datei in `open/` oder `in-progress/` und führt den Punkt in seinem Inhalt (Tabelle oben). Register: jede genannte Kennung ist ein Verzeichnis, jedes mit nicht leerem `evidence/`.
- **Frage an den Nutzer:** (1) Offen 12 (a): Ist die Lesart „die Liste der positiven Formen ist abschließend“ bestätigt? Falls sie weitere Formen ausschließen soll, ist das eine Lastenheft-Änderung wie bei `IS [NOT] NULL`; sollen `OR` oder ähnliche Formen doch erkannt werden, braucht es eine Folge-ADR. (2) Offen 12 (c): Soll die Zeile zu [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) unter „Offene Entscheidungen“ als erledigt geführt werden? Empfehlung: vor dem Start von [slice-sim-erkennung](../done/slice-sim-erkennung.md). (3) Gilt der geänderte Wortlaut des DoD-Satzes zu den Messwerten? (4) Welche Datei soll (a) und (c) tragen, wenn der Nutzer sie nicht bis zur Abnahme von [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) entscheidet? Vorschlag: die beiden genannten Spec-Stellen.

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
