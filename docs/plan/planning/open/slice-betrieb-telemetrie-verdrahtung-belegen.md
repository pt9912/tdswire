# Slice slice-betrieb-telemetrie-verdrahtung-belegen: Telemetrie: Verdrahtung von ACCEPT_FAILED und CONNECTION_FAILED, Rot-Fälle der Ausgabe-Sperre je Package und die Lücke von Logger.log

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-betrieb-diagnose

**Bezug:** [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) (`Accepted`, Frage 2 und Frage 7)

**Berührte Spec-Stellen:** SPEC-059 · SPEC-041 · [TDS-FA-09.a](../../../../spec/spezifikation.md#tds-fa-09a--konfiguration-und-diagnose)

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912. **Datum:** 2026-11-01.

---

## 1. Ziel und Abgrenzung


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Drei Aussagen aus Review und Verifikation von [slice-betrieb-log-schnittstelle](../done/slice-betrieb-log-schnittstelle.md) bekommen einen Beleg, den heute nur das Lesen des Codes trägt: (1) `ACCEPT_FAILED` und `CONNECTION_FAILED` entstehen am Server und nicht nur im Modell-Test (Verifikation V-3: die Mutationen M9 und M10 überleben); (2) „im ganzen Hauptmodul ohne Ausnahme“ der Ausgabe-Sperre ist für jedes Package des Hauptmoduls durch einen Rot-Fall belegt oder als Grenze benannt (V-6: Rot-Fälle nur für `runtime`, `protocol`, `transport`); (3) die Lücke von `java.util.logging.Logger.log(Level, …)` ist geschlossen oder bleibt mit Begründung benannt (V-6, Mutation M6).

**Herkunft:** die Punkte stammen aus dem Bericht `docs/reviews/2026-11-01-2e6b7ba-verifikation-slice-betrieb-log-schnittstelle.md` (V-3, V-6) und aus der Closure-Notiz von [slice-betrieb-log-schnittstelle](../done/slice-betrieb-log-schnittstelle.md).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Umfang der öffentlichen Fläche (18 Ereignisarten, 4 Aufzählungen, rund 50 Namen; Optionen (a), (b), (c) in §6 von [slice-betrieb-log-schnittstelle](../done/slice-betrieb-log-schnittstelle.md)) — entschieden und umgesetzt in [slice-oeffentliche-flaeche-verkleinern-und-belegen](../done/slice-oeffentliche-flaeche-verkleinern-und-belegen.md) (`BEO-TDS/oeffentliche-flaeche-der-telemetrie-ohne-entscheidung`, gestrichen); die Arten stehen als `EventKind`, die Tests dieses Slice belegen sie in dieser Form.
- Neue Ereignisarten, `request_id` und `duration_ms` — [slice-betrieb-zustandsereignisse](slice-betrieb-zustandsereignisse.md) und [slice-betrieb-metriken-tracing](slice-betrieb-metriken-tracing.md); hier wird nur belegt, was gebaut ist.
- Der JSON-Adapter und die Inspektion des laufenden Prozesses — [slice-betrieb-log-adapter-testserver](../in-progress/slice-betrieb-log-adapter-testserver.md).
- `Error` aus der Senke bei `ACCEPT_FAILED` und `CONNECTION_FAILED`: bleibt „weder gemessen noch zugesagt“ (KDoc von `TelemetrySink`); der Slice darf es messen, muss es nicht. Nutzerentscheid 2026-11-06: Ein `Error` aus der Senke als Auslöser von `CONNECTION_FAILED` genügt als Grenze des Tests; ein anderer Auslöser wird nicht gesucht, und eine Zusage darüber hinaus gibt es nicht.
- Weitere Methoden von `java.util.logging.Logger` (`config`, `fine` und weitere): bleiben unter der benannten Grenze der Sperre, ohne eigene Sperre (Nutzerentscheid 2026-11-06).

## 2. Definition of Done


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **Verdrahtung von `ACCEPT_FAILED` und `CONNECTION_FAILED`** ([TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose)): ein Test löst jede der beiden Arten über den Server aus (ein Handler, der wirft; ein Listener, dessen Annehmen scheitert) und liest sie im Sammler; er prüft die Felder der Art und die Grenze „`CONNECTION_FAILED` trägt keine `session_id` und das Profil `unknown`“ aus der Spezifikation (Abschnitt 4). **Beleg:** die Mutationen „`ListenerEvent.Closed` liefert kein Ereignis“ und „`ListenerEvent.AcceptFailed` liefert kein Ereignis“ machen je mindestens einen Test rot. Der Redaktionstest (`TelemetryRedactionTest`) bekommt für beide Arten einen Fall über den Server, mit je verschiedenen Eingabewerten.
- [ ] **Rot-Fälle der Ausgabe-Sperre je Package** ([TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit)): unter `config/detekt/selbsttest-aufrufe/` liegt ein Rot-Fall für `domain`, `port`, `config`, `observability` und `simulation`, den `make detekt-selbsttest` fährt; **Beleg:** die Verengung des `includes` von `ForbiddenMethodCall/ausgabe` auf drei Packages macht `make detekt-selbsttest` rot (Mutation M11 der Verifikation). Der Erlaubt-Fall in `domain` und `port` (heute ohne `println`) belegt, dass die Kern-Regel dort nicht trifft; bleibt er, trägt er sein `println` wieder oder die Datei nennt, was sie nicht belegt.
- [ ] **Lücke `Logger.log`** ([TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit)): entweder ein Eintrag der Sperre für `java.util.logging.Logger.log` (alle Überladungen, die der Übersetzer auflöst) mit Rot-Fall, oder die Grenze steht mit Begründung in `config/detekt/detekt.yml` und in der Closure-Notiz; Ziel ist ein Beleg, kein Versprechen. Die Aufrufe `Thread.dumpStack` und `printStackTrace(PrintStream)` sind dabei gemessen und genannt (der Review fuhr sie nicht).

Nachweis-Pflichten für alle drei Punkte:

- [ ] Der Flake von `TelemetryRedactionTest` (einmal rot in der mutierten Kopie unter Last, Verifikation V-7 von [slice-katalog-und-ddl-zustand](../done/slice-katalog-und-ddl-zustand.md)) wird in diesem Slice stabilisiert, ohne eigenen Träger (Nutzerentscheid 2026-11-06); die Stabilisierung steht in §3 und trägt einen reproduzierten Fehler (`BEO-TDS/flake-fix-ohne-reproduzierten-fehler`).
- [ ] Jeder neue Test trägt im KDoc den Block-Tag `@spec SPEC-<NNN>`; die Tests laufen in `make test` und sind grün.
- [ ] Jede Aussage steht an einem Test oder ist als Festlegung gekennzeichnet (`BEO-TDS/aussagen-ueber-messung-hinaus`).
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update: der Satz zu `CONNECTION_FAILED` in Abschnitt 4 der Spezifikation trägt nach dem Test die Kennzeichnung „gebaut“ mit Verweis auf den Test; `SPEC-059` ändert sich durch diesen Slice nicht (die Arten stehen dort als `EventKind`).
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
| `src/test/kotlin/io/github/pt9912/tdswire/runtime/` (Wiring-Tests, `TelemetryRedactionTest`) | update / neu | Handler, der wirft; Listener, dessen Annehmen scheitert; Felder und Redaktion beider Arten (Happy, Negative) nach [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose) |
| `config/detekt/selbsttest-aufrufe/src/main/kotlin/io/github/pt9912/tdswire/` | neu | Rot-Fälle je Package (`domain`, `port`, `config`, `observability`, `simulation`) |
| `config/detekt/detekt.yml` | update | Eintrag oder benannte Grenze für `Logger.log`; nur Verschärfung (`AGENTS.md` §3.6) |
| `spec/spezifikation.md` Abschnitt 4 | update | Satz zu `CONNECTION_FAILED` mit Verweis auf den Test |
| `src/test/kotlin/io/github/pt9912/tdswire/runtime/TelemetryRedactionTest.kt` | update | Stabilisierung des Flakes (Wartebedingung auf das Ereignis), nur Test |


## 4. Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-betrieb-log-schnittstelle](../done/slice-betrieb-log-schnittstelle.md) liegt in `done/`; [slice-oeffentliche-flaeche-verkleinern-und-belegen](../done/slice-oeffentliche-flaeche-verkleinern-und-belegen.md) liegt in `done/` (erfüllt: die Nutzerentscheidung zum Umfang der öffentlichen Fläche ist gefallen und umgesetzt, die Arten stehen als `EventKind`); ein Verantwortlicher ist eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte; Schnittvorschlag: Verdrahtung | Sperren.
- `in-progress` → `open` (blockiert): die Entscheidung zur Fläche fällt erst während des Baus und ändert die Arten.

## 5. Closure-Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün (einschließlich `make test` und `make detekt-selbsttest`), der Review-Report liegt vor, und die Closure-Notiz trägt die Zuordnung *Test | Festlegung* und den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Ein Test über den Server für `ACCEPT_FAILED` verlangt einen Listener, dessen Annehmen scheitert; ob das ohne eine Naht im Produktcode geht (`BEO-TDS/test-naht-im-produktivcode`), ist ungeklärt. — **Ausgang:** offen bis zur Closure.
- Der Rot-Fall je Package belegt die Konfiguration des `includes`, nicht jeden künftigen Aufruf (Typauflösung, nur genannte Aufrufe). — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); der Slice berührt Tests und `config/detekt`, eine eigene Sub-Area besteht nicht.

**Vorgelagert — offene Beobachtungen sichten:** `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test` (verkörpert; Wiederkehr: die Fälle V-3 und V-6 stammen von dort), `BEO-TDS/aussagen-ueber-messung-hinaus` (verkörpert), `BEO-TDS/test-naht-im-produktivcode`, `BEO-TDS/strukturtest-ohne-variation-der-eingabe`.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
