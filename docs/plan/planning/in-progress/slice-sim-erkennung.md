# Slice slice-sim-erkennung: Simulations-Backend, Teil 1: Tabellenbeschreibung und Anweisungserkennung

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-simulations-backend

**Bezug:** [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md), [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md)

**Berührte Spec-Stellen:** SPEC-035 · SPEC-036 · SPEC-037 · SPEC-038 · SPEC-040 · ARC-016 · [TDS-FA-11.a](../../../../spec/spezifikation.md#tds-fa-11a--simulations-backend)

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

**Ziel:** Ein Anweisungstext wird ohne Auswertung von SQL genau einer Form von SPEC-036 zugeordnet oder mit `TDS_UNSUPPORTED_SQL` bzw. `TDS_SIM_UNKNOWN_OBJECT` abgelehnt, bevor eine Zeilenquelle gelesen oder ein Callback gerufen wird; die Tabellenbeschreibung ist als Typ mit Validierung vorhanden.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Ausführung, Parameterbindung und schrittweises Lesen der Zeilenquelle — [slice-sim-auswahl-streaming](../open/slice-sim-auswahl-streaming.md); dieser Slice erzeugt ein Erkennungsergebnis, keine Ereignisse.
- Weitergabe von Einfügen, Ändern und Löschen an Callbacks — [slice-sim-aenderung-integration](../open/slice-sim-aenderung-integration.md); hier wird nur die Form erkannt.
- Auswertung von Ausdrücken, Verbund, Aggregat, Unterabfrage und Sortierung — nach [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) ausgeschlossen; sie enden als nicht erkannt.
- Form C (Texte des Referenzclients) — leer, bis die Entscheidung aus [slice-adr-simulation-formen](../done/slice-adr-simulation-formen.md) sie füllt; der Slice erzeugt dafür nur den Platz in der Zuordnung.
- Der minimale Fake im Test-Quelltext (`ScriptedBackend` aus [slice-backend-port-vertrag](../done/slice-backend-port-vertrag.md)) bleibt unberührt und lernt keine Erkennung: Der Fake spielt ein Skript von Ereignissen ab, das Simulations-Backend ordnet Text zu. Wächst der Fake um Anweisungserkennung, ist es [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) und gehört in dieses Package.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **Tabellenbeschreibung** (SPEC-035, Form nach [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) aus [slice-adr-simulation-formen](../done/slice-adr-simulation-formen.md)): Typen für Tabelle (mit Schlüssel), Spalte, Abfrage-Hinweis, Filter und Zeilenquelle im Package `simulation`; Validierung der Beschreibung — doppelte Tabellen- und Spaltennamen (ohne Beachtung der Groß-/Kleinschreibung), Spaltentyp nur aus dem Mindestkatalog von `SqlType`, leere Spaltenliste abgewiesen, Schlüssel nicht leer, vorhanden und nicht nullbar; dazu der reine Leser vom Baum (Abbildung, Liste, Skalar mit Zeile und Spalte) zur Beschreibung nach SPEC-035 §Beschreibung als Datei: gelesene Teilmenge samt Ignorierliste (auch die sieben typgebundenen Spaltenfelder `length`, `float_precision`, `values`, `ref_type`, `element_type`, `geometry_type`, `srid`) und `tables` als Pflicht (strenger als das Schema, Setzung), Typabbildung der zehn Typen, Ladefehler mit Pfad, Zeile und Spalte ohne Inhalt, Typen ohne Entsprechung abgewiesen. Der Leser kennt keine YAML-Bibliothek und wird mit handgebauten Bäumen getestet; der Adapter mit der Bibliothek, das Lesen der Datei und die Größengrenze liegen in [slice-sim-aenderung-integration](../open/slice-sim-aenderung-integration.md). Tests Happy/Boundary/Negative.
- [ ] **Anweisungserkennung** (SPEC-036, [TDS-FA-11.a](../../../../spec/spezifikation.md#tds-fa-11a--simulations-backend) Schritte 1 und 2): Zeichenscanner für Literale, Bezeichner (`[…]`, `"…"`, unquotiert) und Kommentare (`--`, `/* */`), Entfernen eines einzelnen abschließenden `;`; Zuordnung zu S1, W1, W2, W3 mit Ergebnis aus Form, Tabelle, Spalten, Termen und Werten oder Parameterverweisen; jede Zeile der Liste „Nicht erkannt“ ist ein Negativtest, die Grenzen bei Literalen, Bezeichnern und Kommentaren je mit eigener Eingabe.
- [ ] **Objektprüfung vor dem Lesen** ([TDS-FA-11.a](../../../../spec/spezifikation.md#tds-fa-11a--simulations-backend) Schritt 3): unbekannte Tabelle oder Spalte endet mit `TDS_SIM_UNKNOWN_OBJECT`, eine nicht erkannte Anweisung mit `TDS_UNSUPPORTED_SQL`; ohne Schema gilt `dbo`. Eine zählende Attrappe für Zeilenquelle und Callback belegt, dass beide in diesen Fällen nie gerufen werden, und ein positiver Kontrollfall belegt, dass der Zähler zählt.

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
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/` | neu | Beschreibung, Scanner, Erkennung; importiert nur `domain` und `port` (Importregel SPEC-041) |
| `src/test/kotlin/io/github/pt9912/tdswire/simulation/` | neu | Positiv- und Negativtests je Form, Eingabevariation je Grenzfall; `@spec`-Tag SPEC-035, SPEC-036 |

Erweiterung der Liste im ersten Lauf (Ausgangslage gemessen: `simulation` existiert weder in `src/main` noch in `src/test`; `ForbiddenImport/simulation` in `config/detekt/detekt.yml` liegt vor; `domain/SqlType` und `port/QueryBackend` tragen alles, was der Slice braucht; `ErrorCode` kennt `TDS_UNSUPPORTED_SQL`, `TDS_SIM_UNKNOWN_OBJECT` und `TDS_SIM_INVALID_VALUE`, liegt aber in `protocol` und ist für `simulation` gesperrt):

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `simulation/SimModel.kt` | neu | `SimTable`, `SimColumn`, `SimQuery`, `RowFilter`, `FilterTerm`, `RowSource`, `SimCatalog` nach SPEC-035; Validierung im Konstruktor; Namensvergleich ohne Beachtung der Schreibweise |
| `simulation/SimNames.kt` | neu | Vergleichsschlüssel für Namen ohne Beachtung der Schreibweise |
| `simulation/SimTree.kt` | neu | Baum aus Abbildung, Liste und Skalar mit Zeile und Spalte; dazu `SimLoadException` (Ladefehler mit Pfad, Zeile, Spalte) |
| `simulation/SimTreeAccess.kt`, `SimColumnTypes.kt`, `SimDescriptionReader.kt` | neu | Zugriff auf den Baum samt Pfadbereinigung, Typabbildung der Datei, reiner Leser ohne YAML-Bibliothek |
| `simulation/SqlLexemes.kt`, `SqlScanner.kt` | neu | Zeichenscanner: Teilschritte (Kommentare, Begrenzer, Wortzeichen je Codepunkt) und Token |
| `simulation/StatementParser.kt`, `Statement.kt` | neu | Zuordnung zu S1, W1, W2, W3; Ergebnis aus Form, Tabelle, Spalten, Termen, Werten oder Parameterverweisen |
| `simulation/StatementPlanner.kt` | neu | Objektprüfung vor dem Lesen; dazu `SimCodes` (Fehlercodes als Zeichenketten, `protocol` ist gesperrt) |
| `src/test/kotlin/io/github/pt9912/tdswire/runtime/SimulationCodesTest.kt` | neu | `runtime` darf beide Seiten kennen: die Codes in `simulation` sind Namen von `ErrorCode` |

- Die Überführung von Literal und Parameter in den Spaltentyp (SPEC-035) gehört nach der DoD von [slice-sim-auswahl-streaming](../open/slice-sim-auswahl-streaming.md); dieser Slice liefert Literale als `Number`/`Text`/`Null`/`Parameter` unüberführt.
- Belegstand von d-migrate (§6): Release-Liste am 2026-10-31 abgerufen, neuestes Release `v1.12.0` (2026-10-27) wie der Belegstand; kein Erneuern nötig.
- Reihenfolge: Beschreibung und Validierung, dann Scanner, dann Zuordnung, zuletzt die Objektprüfung.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-adr-simulation-formen](../done/slice-adr-simulation-formen.md) und [slice-backend-port-vertrag](../done/slice-backend-port-vertrag.md) liegen in `done/` ([ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) steht dann auf `Accepted`), und ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2, mehr als 1500 Zeilen Quelltext unter `simulation` in `src/main` oder mehr als 3500 Zeilen unter `simulation` in `src/test` (Setzung; Ist bei der Fix-Runde: rund 1050 und rund 2500, gezählt mit `wc -l`); Schnittvorschlag: Beschreibung und Erkennung als zwei Slices.
- `in-progress` → `open` (blockiert — Carveout?): [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) wird durch eine Folge-ADR abgelöst oder ist nicht `Accepted`.

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

- Der Scanner ist Eigencode über Text vom Client: Eine Fehlzuordnung (etwa ein `;` oder `--` im Literal) ergäbe ein falsches Ergebnis oder einen falschen Callback-Wert. Werte laufen nie als SQL-Text weiter ([TDS-FA-11.a](../../../../spec/spezifikation.md#tds-fa-11a--simulations-backend) Schritt 4); die Tests mischen Literale, Bezeichner und Kommentare in einer Eingabe. — **Ausgang:** offen bis zur Closure.
- Der Belegstand von d-migrate (Commit a339849, Release 1.12.0, 2026-10-30) altert still: Der Leser liest `schema_format` `1.0` nach dem Schema dort. Der Slice prüft vor dem Bau gegen die Release-Liste von d-migrate, ob ein neueres Release das Format ändert, und erneuert den Belegstand in SPEC-035 und im Leser-KDoc, statt den Text anzupassen ([ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) Folgepflicht). — **Ausgang:** offen bis zur Closure.
- Zwei Punkte der ADR sind nicht gemessen und fallen im Leser an: was bei fehlendem `precision` oder `scale` bei `decimal` gilt und welche Genauigkeit die Zeittypen haben (Offen 7 der ADR; das Schema von d-migrate nennt `precision` und `scale` bei `decimal` im Beschreibungstext „Pflicht“, ohne Constraint; der Slice führt das Ergebnis als Festlegung), und ob der Transport die Länge eines Anweisungstextes begrenzt (Offen 8; der Scanner hat nach den Messzeilen der ADR bei 1,29 Mio Zeichen rund 79 ms gebraucht, der Slice setzt keine Obergrenze). — **Ausgang:** offen bis zur Closure.
- Ein Test, der eine Auswahl- oder Gleichheitseigenschaft mit einheitlicher Eingabe behauptet, bleibt bei jeder Mutation grün (`BEO-TDS/strukturtest-ohne-variation-der-eingabe`); ein Zähler ohne Untergrenze prüft nichts (`BEO-TDS/schranken-test-mit-ungeprueftem-zaehler`). Die Tests variieren die Eingabe, die Attrappe hat einen positiven Kontrollfall. — **Ausgang:** offen bis zur Closure.
- Groß-/Kleinschreibung und `"…"` als Bezeichner sind in [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) an einer Instanz in einer Stichprobe bestätigt; die Tests führen das als Festlegung, nicht als Messung. — **Ausgang:** offen bis zur Closure.
- Die Lesart der Liste „Nicht erkannt“ gegenüber dem Lastenheft ist in [slice-adr-simulation-formen](../done/slice-adr-simulation-formen.md) zu belegen; weicht die ADR von der Liste ab, folgt dieser Slice der ADR. [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) belegt die Deckung je Einzelfall (Abschnitt „Deckung der Liste ‚Nicht erkannt‘“); die Lesart „die positive Aufzählung ist abschließend“ hat der Nutzer nicht ausdrücklich bestätigt; SPEC-036 führt sie als Setzung, und die Bestätigung ist Offen 12 der ADR (Träger: Risikozeile in [slice-adr-simulation-formen](../done/slice-adr-simulation-formen.md)). — **Ausgang:** offen bis zur Closure.
- Größe: Der erste Liefer-Punkt trägt jetzt Typen, Validierung, Schlüssel, Leser vom Baum, Typabbildung und Ladefehler; er ist der größte der drei. Wächst der Slice über das Maß aus §4 (Zeilen, Liefer-Punkte), ist der Schnitt „Tabellenbeschreibung samt Leser“ und „Anweisungserkennung“ als zwei Slices (siehe §4, Rückführung `in-progress` → `next`). — **Ausgang:** offen bis zur Closure.

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

- `BEO-TDS/aussagen-ueber-messung-hinaus` — 16×, Stand *verkörpert* (`AGENTS.md` §3.8, Reviewer-Skill). Betrifft diesen Slice: Jede Aussage über erkannte Formen trägt einen Test oder ist als Festlegung gekennzeichnet.
- `BEO-TDS/strukturtest-ohne-variation-der-eingabe` — 1×, offen. Betrifft die Tests der Zuordnung (Eingabevariation, §6).
- `BEO-TDS/schranken-test-mit-ungeprueftem-zaehler` — 1×, offen. Betrifft die zählende Attrappe von Zeilenquelle und Callback (Untergrenze, §2).
- `BEO-TDS/spec-einzelfaelle-ohne-lastenheft-deckung` — 2×, offen. Betrifft die Negativtests der Liste „Nicht erkannt“; die Deckung je Einzelfall klärt die ADR.
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — 1×, offen. Slice und Closure-Notiz nennen `ADR-*`- und `TDS-*`-Kennungen als Link.
- Ohne Treffer: `BEO-TDS/messung-am-echten-sql-server-fehlt`, `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`, `BEO-TDS/vergleichslauf-d-ungeklaert`, `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`, `BEO-TDS/offene-punkte-ohne-traeger`, `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut`, `BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen`, `BEO-TDS/test-stage-cache-verdeckt-wiederholung`, `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor`, `BEO-TDS/ablage-im-repo-bricht-docs-check`, `BEO-TDS/katalog-und-lock-ohne-gegenpruefung`, `BEO-TDS/java-quelle-umgeht-kotlin-regel`, `BEO-TDS/betreiberanforderung-benutzerverwaltung-fehlt`, `BEO-TDS/adapter-festlegungen-ohne-slice`.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
