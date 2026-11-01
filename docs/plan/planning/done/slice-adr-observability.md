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

**Ziel:** Die Festlegungen, auf denen Logging und Telemetrie ruhen — Format und Ziel der Logzeilen, Form und Ort der Observability-Schnittstelle, Bibliothekswahl (keine, JDK oder Fremdbibliothek), Zugang zu Metriken und Spans, Herkunft der Korrelations-IDs und der Wert von `client_profile`, das Logfeld für die Anmeldekennung und der Schalter für Parameterwerte — liegen als Entscheidung des Nutzers in einer neuen ADR vor. Geliefert ist [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) (`Accepted`, 2026-11-01) mit **acht** Fragen statt der sechs des Plans (Frage 1 als Ereignismodell und Senke der Bibliothek mit dem Format als Adapter im Testserver, Frage 6 Telemetrie, Frage 7 Rückstau und Prozessende, Frage 8 Vertragsabschnitt); das Format ist durch [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) ein Adapter im Testserver, keine Bibliothekswahl mehr.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Code von Schnittstelle, Adapter, Ereignissen und Metriken — [slice-betrieb-log-schnittstelle](../open/slice-betrieb-log-schnittstelle.md), [slice-betrieb-log-adapter-testserver](../open/slice-betrieb-log-adapter-testserver.md), [slice-betrieb-zustandsereignisse](../open/slice-betrieb-zustandsereignisse.md) und [slice-betrieb-metriken-tracing](../open/slice-betrieb-metriken-tracing.md); dieser Slice entscheidet und baut nicht. Spike-Code liegt im Scratchpad und nie im Repo.
- Lastkosten der Telemetrie — ohne Produktivcode nicht messbar; die Messung trägt [slice-qa-performance](../open/slice-qa-performance.md), jede Aussage dazu bliebe hier eine Annahme.
- Die Loginspektion über das Gesamtsystem — [slice-betrieb-log-schnittstelle](../open/slice-betrieb-log-schnittstelle.md); die ADR legt nur fest, welche Felder es gibt, nicht den Gate-Test.
- Die Fortschreibung von `spec/spezifikation.md` Abschnitt 4 und 5 und von `TDS-FA-09.a` — [slice-spec-nachzug-telemetrie](../open/slice-spec-nachzug-telemetrie.md) hat sie übernommen ([ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) Folgepflicht 1); dieser Slice ändert die Spezifikation nicht (zwei Träger für dieselbe Arbeit wären eine Kollision).
- Änderungen am Lastenheft oder die Zeile des Lastenhefts, die Logging verlangt ([TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit)) — die ADR schärft die Spezifikation, nicht das Lastenheft.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [x] **Optionen mit Messung:** Zu jedem Entscheidungspunkt aus §6 stehen zwei bis drei Optionen; Aussagen über Aufwand tragen einen Spike im Scratchpad: (a) Logausgabe — eigener schmaler Schreiber auf der JDK-Schnittstelle gegenüber einer Fremdbibliothek, mit Folgen für Classpath, Sperre und Prüfsummen (`make deps-lock`) und für die Importregeln der Schichten (SPEC-041); (b) Telemetrie — eigene kleine Schnittstelle mit Sammler gegenüber der Schnittstelle einer Telemetrie-Bibliothek, mit Größe der Abhängigkeiten; (c) Zugang zu Metriken — Schnittstelle mit Sammler im Test, JMX oder ein Text-Endpunkt, jeweils mit zusätzlicher Angriffsfläche (neuer Port, Authentifizierung). Messwerte stehen als Tabelle im Slice und in der Entscheidung. Stand der Korrekturrunde: gemessen sind (i) das Blockieren der Standardausgabe bei stockendem Leser (synchron gegen begrenzte Warteschlange), (ii) das JMX-Modul im Laufzeit-Image und (iii) der Verlust beim sofortigen Prozessende, je als Einzelmessung mit Bedingungen in der Messtabelle der Entscheidung; nicht gemessen sind Größe und Lock-Kosten einer Fremdbibliothek, die Telemetrie-Bibliothek und der Jackson-Generator, diese Aussagen stehen dort als Einschätzung und als akzeptiertes Negativ. Die drei Spikes liegen im Scratchpad und sind im Repo nicht reproduzierbar (Review I-2); belegt ist, dass die Messtabelle mit Bedingungen in der ADR steht.
- [x] **Entscheidung des Nutzers** zu den Punkten aus §6, festgehalten als neue ADR (nächste freie Nummer laut ADR-Index, Status `Accepted` — in der Lieferung am 2026-11-01 vor dem Review gesetzt, siehe §7 —, `Bezug` auf [ADR-0003](../../adr/0003-transportframework.md) und [ADR-0009](../../adr/0009-authentifizierungsmodus.md), ohne `Supersedes`, Index und Geschichte nachgezogen); jede Aussage trägt eine Messzeile oder ist als Setzung des Nutzers gekennzeichnet.
- [x] **Anschlussstellen** nachgezogen: Start-Trigger und Risiken der fünf Folge-Slices (`slice-spec-nachzug-telemetrie`, `slice-betrieb-log-adapter-testserver`, `slice-betrieb-log-schnittstelle`, `slice-betrieb-zustandsereignisse`, `slice-betrieb-metriken-tracing`) und die Antwort auf die Frage nach einem Vertragsabschnitt (Frage 8 der ADR: A). **Nicht geliefert, übergeben:** die Fortschreibung von Spezifikation §4 und §5 (Pflicht-Logfelder, Feld für die Anmeldekennung, `client_profile`) liegt bei [slice-spec-nachzug-telemetrie](../open/slice-spec-nachzug-telemetrie.md); sie ist hier nicht verifiziert und nicht gelaufen (§7).

Nachweis-Pflichten für alle drei Punkte:

- [x] `make gates` grün (Lauf bei der Closure, §7).
- [x] Review durchgeführt, Report [2026-11-01-390f951-slice-adr-observability.md](../../../reviews/2026-11-01-390f951-slice-adr-observability.md) liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8). Ein gesonderter Verifier-Lauf ist für diesen Slice nicht gelaufen; der Review ist die einzige unabhängige Prüfung (§7).
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
| `spec/spezifikation.md` | nicht hier | Abschnitte 4 und 5: [slice-spec-nachzug-telemetrie](../open/slice-spec-nachzug-telemetrie.md); dieser Slice lässt Rang 2 unberührt |
| Slices der Welle (fünf Folge-Slices) | update | Start-Trigger und Risiken an die Entscheidung anschließen |

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

- Logformat und Ziel (Frage 1): Zeilen mit festen Feldern als Text oder als JSON, auf Standardausgabe oder Datei; eine Fremdbibliothek für JSON im Produktivpfad ist eine Lieferketten-Entscheidung (AGENTS.md §3.1). — **Ausgang:** entfallen — entschieden in [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) Frage 1: die Bibliothek trägt Modell und Senke ohne Schreiber und ohne Fremdbibliothek, das Format (JSON-Zeile) ist ein Adapter im Testserver.
- Ort der Schnittstelle (Frage 2): ARC-009 lässt `protocol` und `transport` „Observability-Schnittstellen“ importieren, `observability` selbst nur `domain`; ob die Schnittstelle in `domain` oder in einem eigenen Package liegt, legt die Architektur nicht fest. — **Ausgang:** entfallen für den Ort (Frage 2 A, Unterpackage von `domain`); die Aussage „eine Regelzeile“ für die Importregel trug nicht (Review M-3) und geht als Aufgabe an [slice-betrieb-log-schnittstelle](../open/slice-betrieb-log-schnittstelle.md) (Risiko dort).
- Logfeld für die Anmeldekennung (Frage 3): die Spezifikation nennt dafür „bisher keines“. Ob die Kennung für die Rückverfolgung abgelehnter Anmeldungen im Log erscheint oder nie, ist eine Abwägung zwischen Betrieb und Enumeration; die ADR entscheidet sie mit Begründung, und die Spezifikation führt das Feld erst danach. — **Ausgang:** entfallen — Frage 3 B: `principal` nur im Ereignis „Anmeldung angenommen“, abgelehnte Anmeldungen ohne Kennung; die Spezifikation führt das Feld im Nachzug ([slice-spec-nachzug-telemetrie](../open/slice-spec-nachzug-telemetrie.md)).
- Parameterwerte im Log (Frage 4): die Spezifikation sagt „standardmäßig nicht“; ob es einen Schalter gibt, der sie einschaltet, ist offen — eine Einschaltung wäre eine Abschwächung nach AGENTS.md §3.6 und braucht dann eine sichtbare Konfiguration wie `ENCRYPTION_POLICY`. — **Ausgang:** entfallen — Frage 4 A: kein Schalter, Parameterwerte nie im Modell.
- Wert von `client_profile` und Herkunft der IDs (Frage 5): das Pflicht-Logfeld hat keine definierte Quelle (Profilname aus der Konfiguration oder aus den Verbindungsoptionen des Clients); Format von `connection_id`, `session_id` und `request_id` (Zähler oder Zufall) ist offen. — **Ausgang:** entfallen — Frage 5 C (`client_profile` aus dem ausgehandelten Protokoll; Zähler für `connection_id`, UUID für `session_id` und `request_id`, Setzung); die Spezifikation führt es im Nachzug.
- Vertragsdatei (Frage 6): Es gibt Vertragsabschnitte in `spec/spezifikation.md` §6 für Client-Profil, Backend, Authentifizierung und Sicherheit, aber keinen für die Observability-Schnittstelle; ob die ADR genügt oder ein Vertragsabschnitt entsteht, ist offen. — **Ausgang:** entfallen — Frage 8 A: kein eigener Vertragsabschnitt über die ADR hinaus; `SPEC-059` trägt die Namen nach dem Bau.
- Der minimale Log-Anschluss aus [slice-handshake-login](../done/welle-handshake-login/slice-handshake-login.md) soll diese Schnittstelle nicht vorwegnehmen; die ADR legt fest, wie er abgelöst wird. — **Ausgang:** weiter offen — die Ablösung des minimalen Anschlusses (`ServerLog`, `LogEntry`) trägt [slice-betrieb-log-schnittstelle](../open/slice-betrieb-log-schnittstelle.md); sie steht gegen zwei `Accepted`-ADRs, bis der Nutzer [ADR-0029](../../adr/0029-log-parameter-am-einstieg.md) (`Proposed`) annimmt (Review M-1; Startbedingung dort).
- Die Spikes laufen im Scratchpad ohne Produktivcode; was sie über Verhalten unter Last sagen, ist keine Messung am Produkt (`BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`). Drei Spikes sind gelaufen (Standardausgabe bei stockendem Leser, JMX-Modul im Laufzeit-Image, Verlust beim Prozessende); ihre Werte stehen in der Entscheidung. — **Ausgang:** weiter offen — die Spike-Werte bleiben Einzelmessungen im Scratchpad, nicht reproduzierbar im Repo (Review I-2); die Messung am Produkt trägt [slice-qa-performance](../open/slice-qa-performance.md) (`BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`, geplant).
- Offene Punkte der ADR brauchen je einen Träger als Datei (`BEO-TDS/offene-punkte-ohne-traeger`); eine neue Abhängigkeit im Katalog ohne `make deps-lock` bleibt grün (`BEO-TDS/katalog-und-lock-ohne-gegenpruefung`) — die Spikes ändern den Katalog nicht im Repo. — **Ausgang:** entfallen — jeder offene Punkt (a) bis (h) der ADR hat in §7 einen Träger; die Spikes ändern den Katalog nicht (kein Eintrag in `gradle.lockfile`, `BEO-TDS/katalog-und-lock-ohne-gegenpruefung` bekommt keine Evidenz).

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

**Gegenstand:** Entscheidungs-Slice, nichts gebaut. Geliefert ist [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) (`Accepted`, 2026-11-01, Index-Zeile steht). Geschlossen am 2026-11-01 vom Planner nach dem Review [2026-11-01-390f951-slice-adr-observability.md](../../../reviews/2026-11-01-390f951-slice-adr-observability.md) (0 HIGH, 4 MEDIUM, 2 LOW, 3 INFO; Dateiname mit der Kurz-SHA des Stands `390f951`; der Report ist unverändert aus dem Review übernommen, nur der Dateiname ist gesetzt).

**Verifikation — ehrlich.** Ein gesonderter Verifier-Lauf ist für diesen reinen ADR-Slice nicht gelaufen; der Review ist die einzige unabhängige Prüfung und ersetzt die Verifikation gegen die DoD nicht (der Report prüft ausdrücklich „nicht gegen die DoD“). Die DoD-Haken stehen nur dort, wo ein Beleg im Repo liegt: ADR `Accepted` und Index-Zeile, fünf Folge-Slices in `open/`, Review-Report. **DoD-Punkt 3 ist nur zur Hälfte geliefert:** Spezifikation §4 und §5 übergibt dieser Slice an [slice-spec-nachzug-telemetrie](../open/slice-spec-nachzug-telemetrie.md) (beidseitig vermerkt, dort §6; Setzung des Planners, Bestätigung durch den Nutzer steht aus). `spec/` ist von diesem Slice nicht verändert (Review: geprüft ohne Befund).

**Aussagen — gemessen / Handvektor / Selbstkonsistenz / Festlegung / nicht gemessen.**

| Klasse | Inhalt |
|---|---|
| gemessen | drei Spikes im Scratchpad als Einzelmessung (Standardausgabe bei stockendem Leser, JMX-Modul im Laufzeit-Image, Verlust beim Prozessende), Bedingungen in der Messtabelle der ADR; im Repo nicht reproduzierbar (Review I-2). Vom Reviewer nachgeprüft: nur die Basis-Images im Dockerfile und Aussagen über den Code (u. a. `ServerLog.STDOUT` mit `println`, Zähler ab 1, `UUID.randomUUID()`, `jackson-core` 2.21.7 im Lock) |
| Handvektor | keiner |
| Selbstkonsistenz | ADR gegen Index, `Bezug`- und `Schärft`-Anker und Spezifikation (Review: Anker lösen auf; Widersprüche M-1 bis M-3 unten) |
| Festlegung | alle Zahlen und Formen der ADR (Warteschlange, 256 Zeichen, 60 Sekunden, Arbeitsnamen der Senke) sind Setzung des Nutzers |
| nicht gemessen | Größe und Lock-Kosten einer Fremdbibliothek, Telemetrie-Bibliothek, Jackson-Generator, Overhead und Rückstau am Produkt (akzeptiertes Negativ der ADR; Träger `slice-qa-performance`) |

**Ausgang der Review-Befunde.**

| Befund | Ausgang |
|---|---|
| M-1 zwei `Accepted`-ADRs widersprechen [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) ohne `Supersedes` | Übergabeartefakt ist [ADR-0029](../../adr/0029-log-parameter-am-einstieg.md) (`Proposed`, teilweises `Supersedes` zu [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) und [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md)). **Offener Nutzerpunkt: „Annahme [ADR-0029](../../adr/0029-log-parameter-am-einstieg.md)“**; vermerkt als Startbedingung in [slice-betrieb-log-schnittstelle](../open/slice-betrieb-log-schnittstelle.md) §4. Dieser Slice schließt, ohne dass die ADR angenommen ist |
| M-2 `Aborted.reason` ohne Adresse gegen Regel zum Prozessende und `SPEC-043` | Aufgabe in [slice-spec-nachzug-telemetrie](../open/slice-spec-nachzug-telemetrie.md) §6 |
| M-3 Importregel `protocol`/`transport` nach `observability` fehlt, „eine Regelzeile“ nicht belegt | Aufgabe in [slice-betrieb-log-schnittstelle](../open/slice-betrieb-log-schnittstelle.md) (Liefer-Punkt 2, §6) |
| M-4 Plan veraltet, Spec-Arbeit mit zwei Trägern | in diesem Slice angeglichen (§1 bis §3, DoD 3) und in `welle-betrieb-diagnose` §4 und `slice-spec-nachzug-telemetrie` §6 vermerkt |
| L-1 Offen (a) durch Lastenheft 0.6.1 überholt | Aufgabe in [slice-spec-nachzug-telemetrie](../open/slice-spec-nachzug-telemetrie.md) §4 und §6 |
| L-2 Annahme vor dem Review | hier vermerkt: Der Plan sah `Accepted` nach Review und Verifikation vor; die Annahme geschah am 2026-11-01 vor dem Review, als Nutzerentscheidung in der Sitzung. Der Text der ADR wurde danach nicht mehr geändert (`AGENTS.md` §3.5); Korrekturen laufen über [ADR-0029](../../adr/0029-log-parameter-am-einstieg.md) und den Spec-Nachzug |
| I-1 Senkenvertrag bei `CancellationException` und `Error` | Aufgabe in [slice-betrieb-log-schnittstelle](../open/slice-betrieb-log-schnittstelle.md) (Belege von Liefer-Punkt 1) |
| I-2 Spikes nicht reproduzierbar | benannt (Tabelle oben, §6) |
| I-3 Benennungs-Drift, Geschichte nennt Optionen F und G | nicht behoben: die ADR ist `Accepted` und unveränderlich; die Geschichte ist die Chronik der Fassungen |

**Offene Punkte der ADR (a) bis (h) und ihre Träger.** (a) Lesart von [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose): durch Lastenheft 0.6.1 gestützt, Träger `slice-spec-nachzug-telemetrie`. (b) Folge-ADR: [ADR-0029](../../adr/0029-log-parameter-am-einstieg.md), Nutzer. (c) Zeitstempelformat: `slice-spec-nachzug-telemetrie`. (d) `metrics` als Perzentile oder Zähler: [slice-betrieb-metriken-tracing](../open/slice-betrieb-metriken-tracing.md). (e) Quelladresse: Entscheidung zur Anmeldedrosselung ([ADR-0016](../../adr/0016-anmeldedrosselung.md)). (f) Instanztrennung und (g) Namen: [slice-betrieb-log-schnittstelle](../open/slice-betrieb-log-schnittstelle.md). (h) Speicherkopien und Gesamtinspektion: `BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen`, `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` (Inspektion im [slice-betrieb-log-adapter-testserver](../open/slice-betrieb-log-adapter-testserver.md)).

**Beobachtungs-Register.** Evidenz geschrieben, kein Zähler gesetzt: `BEO-TDS/aussagen-ueber-messung-hinaus` (M-3: „eine Regelzeile“ breiter als der Beleg; bereits verkörpert, `AGENTS.md` §3.8), `BEO-TDS/bedingung-der-adr-fehlt-in-der-spezifikation` (M-2: Regel der ADR gegen die Spezifikation) und neu `BEO-TDS/slice-plan-veraltet-nach-gelieferter-adr` (M-4, L-2). Die Zähler folgen aus den Dateien; nichts davon wird hier verkörpert. Gesichtet, kein neues Auftreten: `BEO-TDS/offene-punkte-ohne-traeger` (alle Punkte haben einen Träger), `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` (Lesart (a) nun vom Lastenheft gestützt).

**Lerneintrag (benannte Spec-Lücke).** `StartOutcome.Aborted.reason` ist in der Spezifikation „ohne Konfigurationswert“ festgelegt, die Regel zum Prozessende in [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) setzt den Inhalt des Abbruch-Ereignisses (mit Adresse und Port) in den `reason`, und `SPEC-043` verlangt eine Diagnose mit Adresse und Port. Die Lücke ist benannt und hat einen Träger ([slice-spec-nachzug-telemetrie](../open/slice-spec-nachzug-telemetrie.md) §6); verkörpert ist in diesem Slice keine Regel, kein Sensor und kein Zielort (daher kein Feld `liegt in`). Befund zum Prozess: Eine ADR, die einen Satz der Spezifikation berührt, gehört vor `Accepted` gegen dessen Wortlaut gelesen (Beleg: M-2, M-3).


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
- `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` — 3×, Stand *geplant* (Träger [slice-qa-sicherheit](../done/slice-qa-sicherheit.md)). Betrifft das Logfeld für die Kennung und die Redaktion von Passwort und Prüfwert (§6); die Gesamtinspektion trägt [slice-betrieb-log-schnittstelle](../open/slice-betrieb-log-schnittstelle.md).
- `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` — 1×, offen. Betrifft jede Aussage der Spikes über Verhalten unter Last (§6).
- `BEO-TDS/katalog-und-lock-ohne-gegenpruefung` — 1×, offen. Betrifft einen Spike, der eine Bibliothek in den Katalog nähme (§6).
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — 1×, offen. ADR, Slice und Closure-Notiz nennen `ADR-*`- und `TDS-*`-Kennungen als Link.
- Ohne Treffer: `BEO-TDS/messung-am-echten-sql-server-fehlt`, `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`, `BEO-TDS/vergleichslauf-d-ungeklaert`, `BEO-TDS/spec-einzelfaelle-ohne-lastenheft-deckung`, `BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen`, `BEO-TDS/strukturtest-ohne-variation-der-eingabe`, `BEO-TDS/schranken-test-mit-ungeprueftem-zaehler`, `BEO-TDS/test-stage-cache-verdeckt-wiederholung`, `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor`, `BEO-TDS/ablage-im-repo-bricht-docs-check`, `BEO-TDS/java-quelle-umgeht-kotlin-regel`, `BEO-TDS/betreiberanforderung-benutzerverwaltung-fehlt`, `BEO-TDS/adapter-festlegungen-ohne-slice`.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
