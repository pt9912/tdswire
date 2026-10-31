# Slice slice-sim-auswahl-streaming: Simulations-Backend, Teil 2: Auswahl mit Bedingung, Parameter und schrittweises Lesen der Zeilenquelle

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-simulations-backend

**Bezug:** [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md), [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md), [ADR-0013](../../adr/0013-backend-grenzen.md)

**Berührte Spec-Stellen:** SPEC-002 · SPEC-029 · SPEC-031 · SPEC-035 · SPEC-036 · SPEC-039 · ARC-006 · ARC-016 · [TDS-FA-11.a](../../../../spec/spezifikation.md#tds-fa-11a--simulations-backend)

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

**Ziel:** Das Simulations-Backend ist eine `QueryBackend`-Implementierung, die eine erkannte Auswahl (S1) beantwortet: Metadaten auch ohne Treffer, passende Zeilen schrittweise aus der Zeilenquelle, Abschluss mit Zeilenzahl; ein Ergebnis oberhalb der Heap-Grenze eines Testlaufs wird nicht vollständig gehalten.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Einfügen, Ändern und Löschen — [slice-sim-aenderung-integration](slice-sim-aenderung-integration.md).
- Sequenzierer, Tokens und Transport — der Slice endet am Strom `events` des Ports; die Kette bis zur Senke belegt [slice-backend-streaming-nachweis](../done/slice-backend-streaming-nachweis.md) am Fake, der Referenzclient-Lauf am Simulations-Backend [slice-sim-aenderung-integration](slice-sim-aenderung-integration.md).
- Zeilendarstellung je `SqlType`, Grenze für „kleine“ Ergebnisse und die Frist `deadlineEpochMillis` — entschieden in [ADR-0013](../../adr/0013-backend-grenzen.md): `List<Any?>` mit den Klassen je Spaltenart (`Int`, `Long`, `Boolean`, `String`, `BigDecimal`, `LocalDate`, `LocalTime`, `LocalDateTime`, `null`), keine Grenze für „kleine“ Ergebnisse im Vertrag (Setzung), die Frist setzt der Protokollkern durch und das Backend darf sie ignorieren (Träger: [slice-attention-cancel-timeout](slice-attention-cancel-timeout.md)); dieser Slice übernimmt die Entscheidung.
- Blockierende Zeilenquellen und nicht unterbrechbare Aufrufe — entschieden in [ADR-0013](../../adr/0013-backend-grenzen.md): Vertragspflicht „suspendierend“, Umhüllung beim Integrator, Hilfsadapter erst beim Trigger „echtes blockierendes Backend“; geprüft wird eine suspendierende Quelle.
- Ein Vorfilter in der Zeilenquelle als Optimierung — der Hinweis `SimQuery.filter` wird übergeben, die Bedingung wendet das Backend immer selbst an.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **Auswahl** ([TDS-FA-11.a](../../../../spec/spezifikation.md#tds-fa-11a--simulations-backend) Schritte 4 und 5): Parameter typisiert einsetzen (nie als Text) und in den Spaltentyp überführen — streng und verlustfrei nach SPEC-035 und [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) (gleiche Typfamilie, ISO-Formate, keine Rundung, keine Zahl↔Text-Überführung, Parameter nur verlustfrei erweitert, Überlauf immer ungültig), sonst `TDS_SIM_INVALID_VALUE` vor dem Lesen; Dezimalvergleich über `compareTo`, Textvergleich ohne Beachtung der Schreibweise; `ResultStart` mit den Spalten der Auswahlliste (bei `*` alle in der Reihenfolge der Beschreibung) auch ohne Treffer; die Bedingung wendet das Backend auf jede Zeile an (`In`, `IsNull`, UND, NULL trifft nie, ein Widerspruch trifft keine Zeile); `ResultEnd` mit der Zeilenzahl.
- [ ] **Schrittweises Lesen und Ende** (Schritte 5 und 7, SPEC-029, SPEC-031): `next()` wird nur auf Abnahme gerufen, `close()` genau einmal nach Ende, Fehler, Cancel und Disconnect; ein Fehler der Zeilenquelle vor `ResultStart` ist ein `Failure` mit `TDS_BACKEND_ERROR` ohne Metadaten, nach `ResultStart` endet der Strom nach SPEC-031 (die Folge für die Verbindung hält der Sequenzierer).
- [ ] **Port-Pflichten und Speichergrenze:** Die Pflichten, die die abstrakte Suite `QueryBackendContract` am Test-Backend prüft (`ScriptedBackendContractTest` ist ihre erste Implementierung), gelten auch für das Simulations-Backend (ein Backend durchläuft die Suite, indem es sie erbt; ein Gegenstück legt der Implementer fest); eine Zeilenquelle mit mehr Daten, als der gesetzte Heap eines Testlaufs hält, wird an einer langsamen Senke gelesen, der Heap bleibt unter der Grenze, und ein Zähler belegt, dass die Quelle bis zu einer bekannten Stelle gelesen wurde.

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
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/` <!-- d-check:ignore (geplant: neue Datei) --> | update | Backend-Klasse: Auswahl, Parameter, Bedingung, Lesen |
| `src/test/kotlin/io/github/pt9912/tdswire/simulation/` <!-- d-check:ignore (geplant: neue Datei) --> | neu | Auswahl Happy/Boundary/Negative, Fehler und Cancel der Quelle, Heap-Nachweis; `@spec`-Tag SPEC-002, SPEC-031, SPEC-035 |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-sim-erkennung](../done/slice-sim-erkennung.md) und [slice-backend-streaming-nachweis](../done/slice-backend-streaming-nachweis.md) liegen in `done/` (das Heap-Verfahren des Testlaufs wird übernommen, nicht neu erfunden), und die Zeilendarstellung ist entschieden ([ADR-0013](../../adr/0013-backend-grenzen.md): Klassen je Spaltenart; `Accepted`, erfüllt), und ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2 oder der Heap-Nachweis sprengt den Rahmen; Schnittvorschlag: Heap-Nachweis als eigener Slice.
- `in-progress` → `open` (blockiert — Carveout?): [ADR-0013](../../adr/0013-backend-grenzen.md) wird durch eine Folge-Entscheidung zur Zeilendarstellung abgelöst, oder der Port ändert sich unter dem Slice.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün (einschließlich `make test`), der Review-Report liegt vor, und die Closure-Notiz trägt die Zuordnung *Test | Festlegung* mit Messbedingungen des Heap-Laufs sowie den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Überführungsregel, Textvergleich und Dezimalvergleich sind Setzungen ([ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md): Messung nur im JDK-Spike, nichts am echten SQL Server); der Slice testet sie als Festlegung, nicht als Messung, und führt sie in der Closure so (`BEO-TDS/aussagen-ueber-messung-hinaus`, `BEO-TDS/messung-am-echten-sql-server-fehlt`). Sie gelten für die JVM-Typen der Werte, die [ADR-0013](../../adr/0013-backend-grenzen.md) festlegt (die Überführungsregel ist für diese Klassen nicht neu zu prüfen), und sind bei einer anderen Zeilendarstellung neu zu prüfen. — **Ausgang:** offen bis zur Closure.
- Die Heap-Aussage gilt für einen Testlauf mit gesetztem Heap und einer Test-Senke, nicht für das Produkt unter Last (Grenze der Aussage in [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md); `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`). — **Ausgang:** offen bis zur Closure.
- Eine Zeilenquelle, die den Hinweis `SimQuery.filter` ignoriert oder mehr liefert, muss trotzdem zum richtigen Ergebnis führen; ein Test mit einer solchen Quelle belegt die Pflicht, dass das Backend die Bedingung selbst anwendet. Eine Quelle, die zu wenig liefert, ist Sache des Integrators und wird nicht erkannt. — **Ausgang:** offen bis zur Closure.
- Die dreiwertige Logik (`ANSI_NULLS ON`) ist als vorläufig gekennzeichnet und nur in einer Stichprobe an einer Instanz bestätigt ([ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md)); die Tests führen sie als Festlegung. — **Ausgang:** offen bis zur Closure.
- Das Wiederverwenden der Suite `QueryBackendContract` kann eine Änderung an ihr verlangen, etwa wenn das Simulations-Backend das Skript nicht in der Form des Test-Backends entgegennimmt; sie wird in diesem Slice benannt und nicht still vorgenommen. — **Ausgang:** offen bis zur Closure.

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

- `BEO-TDS/aussagen-ueber-messung-hinaus` — 16×, Stand *verkörpert* (`AGENTS.md` §3.8, Reviewer-Skill). Betrifft diesen Slice: Jede Aussage über das Lesen und die Speichergrenze nennt ihre Messbedingung.
- `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` — 1×, offen. Betrifft die Heap-Aussage (Testlauf, nicht Produkt, §6).
- `BEO-TDS/schranken-test-mit-ungeprueftem-zaehler` — 1×, offen. Betrifft den Nachweis „höchstens so viel gelesen“: ein Zähler mit Untergrenze (§2).
- `BEO-TDS/strukturtest-ohne-variation-der-eingabe` — 1×, offen. Betrifft die Bedingungstests: Eingabe variieren (§6).
- `BEO-TDS/test-stage-cache-verdeckt-wiederholung` — 2×, offen. Betrifft den Heap-Lauf in einer Docker-Stage: ein gecachter grüner Lauf belegt die Stabilität nicht erneut; die Closure nennt `--no-cache-filter test` für die Wiederholung.
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — 1×, offen. Slice und Closure-Notiz nennen `ADR-*`- und `TDS-*`-Kennungen als Link.
- Ohne Treffer: `BEO-TDS/messung-am-echten-sql-server-fehlt`, `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`, `BEO-TDS/vergleichslauf-d-ungeklaert`, `BEO-TDS/offene-punkte-ohne-traeger`, `BEO-TDS/spec-einzelfaelle-ohne-lastenheft-deckung`, `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut`, `BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen`, `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor`, `BEO-TDS/ablage-im-repo-bricht-docs-check`, `BEO-TDS/katalog-und-lock-ohne-gegenpruefung`, `BEO-TDS/java-quelle-umgeht-kotlin-regel`, `BEO-TDS/betreiberanforderung-benutzerverwaltung-fehlt`, `BEO-TDS/adapter-festlegungen-ohne-slice`.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
