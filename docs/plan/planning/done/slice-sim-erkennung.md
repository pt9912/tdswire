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

- Ausführung, Parameterbindung und schrittweises Lesen der Zeilenquelle — [slice-sim-auswahl-streaming](../in-progress/slice-sim-auswahl-streaming.md); dieser Slice erzeugt ein Erkennungsergebnis, keine Ereignisse.
- Weitergabe von Einfügen, Ändern und Löschen an Callbacks — [slice-sim-aenderung-integration](../open/slice-sim-aenderung-integration.md); hier wird nur die Form erkannt.
- Auswertung von Ausdrücken, Verbund, Aggregat, Unterabfrage und Sortierung — nach [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) ausgeschlossen; sie enden als nicht erkannt.
- Form C (Texte des Referenzclients) — leer, bis die Entscheidung aus [slice-adr-simulation-formen](../done/slice-adr-simulation-formen.md) sie füllt; der Slice erzeugt dafür nur den Platz in der Zuordnung.
- Der minimale Fake im Test-Quelltext (`ScriptedBackend` aus [slice-backend-port-vertrag](../done/welle-backend-streaming/slice-backend-port-vertrag.md)) bleibt unberührt und lernt keine Erkennung: Der Fake spielt ein Skript von Ereignissen ab, das Simulations-Backend ordnet Text zu. Wächst der Fake um Anweisungserkennung, ist es [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) und gehört in dieses Package.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [x] **Tabellenbeschreibung** (SPEC-035, Form nach [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) aus [slice-adr-simulation-formen](../done/slice-adr-simulation-formen.md)): Typen für Tabelle (mit Schlüssel), Spalte, Abfrage-Hinweis, Filter und Zeilenquelle im Package `simulation`; Validierung der Beschreibung — doppelte Tabellen- und Spaltennamen (ohne Beachtung der Groß-/Kleinschreibung), Spaltentyp nur aus dem Mindestkatalog von `SqlType`, leere Spaltenliste abgewiesen, Schlüssel nicht leer, vorhanden und nicht nullbar; dazu der reine Leser vom Baum (Abbildung, Liste, Skalar mit Zeile und Spalte) zur Beschreibung nach SPEC-035 §Beschreibung als Datei: gelesene Teilmenge samt Ignorierliste (auch die sieben typgebundenen Spaltenfelder `length`, `float_precision`, `values`, `ref_type`, `element_type`, `geometry_type`, `srid`) und `tables` als Pflicht (strenger als das Schema, Setzung), Typabbildung der zehn Typen, Ladefehler mit Pfad, Zeile und Spalte ohne Inhalt, Typen ohne Entsprechung abgewiesen. Der Leser kennt keine YAML-Bibliothek und wird mit handgebauten Bäumen getestet; der Adapter mit der Bibliothek, das Lesen der Datei und die Größengrenze liegen in [slice-sim-aenderung-integration](../open/slice-sim-aenderung-integration.md). Tests Happy/Boundary/Negative.
- [x] **Anweisungserkennung** (SPEC-036, [TDS-FA-11.a](../../../../spec/spezifikation.md#tds-fa-11a--simulations-backend) Schritte 1 und 2): Zeichenscanner für Literale, Bezeichner (`[…]`, `"…"`, unquotiert) und Kommentare (`--`, `/* */`), Entfernen eines einzelnen abschließenden `;`; Zuordnung zu S1, W1, W2, W3 mit Ergebnis aus Form, Tabelle, Spalten, Termen und Werten oder Parameterverweisen; jede Zeile der Liste „Nicht erkannt“ ist ein Negativtest, die Grenzen bei Literalen, Bezeichnern und Kommentaren je mit eigener Eingabe.
- [x] **Objektprüfung vor dem Lesen** ([TDS-FA-11.a](../../../../spec/spezifikation.md#tds-fa-11a--simulations-backend) Schritt 3): unbekannte Tabelle oder Spalte endet mit `TDS_SIM_UNKNOWN_OBJECT`, eine nicht erkannte Anweisung mit `TDS_UNSUPPORTED_SQL`; ohne Schema gilt `dbo`. Eine zählende Attrappe für Zeilenquelle und Callback belegt, dass beide in diesen Fällen nie gerufen werden, und ein positiver Kontrollfall belegt, dass der Zähler zählt.

Nachweis-Pflichten für alle drei Punkte:

- [x] Jeder Test trägt im KDoc über der Testfunktion den Block-Tag `@spec SPEC-<NNN>` (Konvention nach [slice-qa-spec-abdeckung](../done/slice-qa-spec-abdeckung.md)); die Tests laufen in `make test` und sind grün. Belegt: 89 von 89 `@Test` der Simulation tragen den Tag (Verifikation: 88 von 88, dazu der Test der Mini-Runde); frischer Lauf der Closure 648 Tests grün.
- [x] Jede Aussage steht an einem Test oder ist als Festlegung gekennzeichnet; die Zuordnung *Test | Festlegung* je Aussage steht in der Closure-Notiz (`BEO-TDS/aussagen-ueber-messung-hinaus`, siehe §8). Zuordnung in §7; die Festlegung des Namensvergleichs (`s` gleich `ſ`) hat seit ea338ef einen Test, die übrigen Festlegungen stehen ohne Messung in der Spalte „Festlegung“.
- [x] `make gates` grün. Auf dem Stand vor dem Move, siehe §7 „Frischer Lauf“.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor Der Report [2026-10-31-7aa0763](../../../reviews/2026-10-31-7aa0763-slice-sim-erkennung.md) prüft den Stand vor der Fix-Runde; die Fix-Runde 188c4f3 hat kein eigenes Review (nur die Verifikation hat sie durch Mutation gesehen), die Mini-Runde ea338ef nur der Implementer und die Planner-Durchsicht von `git show ea338ef` (siehe §7).
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

- Die Überführung von Literal und Parameter in den Spaltentyp (SPEC-035) gehört nach der DoD von [slice-sim-auswahl-streaming](../in-progress/slice-sim-auswahl-streaming.md); dieser Slice liefert Literale als `Number`/`Text`/`Null`/`Parameter` unüberführt.
- Belegstand von d-migrate (§6): Release-Liste am 2026-10-31 abgerufen, neuestes Release `v1.12.0` (2026-10-27) wie der Belegstand; kein Erneuern nötig.
- Reihenfolge: Beschreibung und Validierung, dann Scanner, dann Zuordnung, zuletzt die Objektprüfung.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-adr-simulation-formen](../done/slice-adr-simulation-formen.md) und [slice-backend-port-vertrag](../done/welle-backend-streaming/slice-backend-port-vertrag.md) liegen in `done/` ([ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) steht dann auf `Accepted`), und ein Verantwortlicher ist im Kopf eingetragen.

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

- Der Scanner ist Eigencode über Text vom Client: Eine Fehlzuordnung (etwa ein `;` oder `--` im Literal) ergäbe ein falsches Ergebnis oder einen falschen Callback-Wert. Werte laufen nie als SQL-Text weiter ([TDS-FA-11.a](../../../../spec/spezifikation.md#tds-fa-11a--simulations-backend) Schritt 4); die Tests mischen Literale, Bezeichner und Kommentare in einer Eingabe. — **Ausgang:** entfallen: Eine falsche Zuordnung oder ein falscher Wert trat nicht auf; der Review-Fund F-1 (Buchstabe außerhalb der Grundebene) war eine Ablehnung, kein falscher Callback-Wert, und ist behoben (Scanner je Codepunkt). Die Tests mischen Literale, Bezeichner und Kommentare (Orakel mit 600 Anweisungen, 64 Grenzfälle), die Verifikation röterte 24 von 25 Mutationen, die 25. ist behoben. Das Orakel ist Code gegen Code (siehe §7, Selbstkonsistenz).
- Der Belegstand von d-migrate (Commit a339849, Release 1.12.0, 2026-10-27) altert still: Der Leser liest `schema_format` `1.0` nach dem Schema dort. Der Slice prüft vor dem Bau gegen die Release-Liste von d-migrate, ob ein neueres Release das Format ändert, und erneuert den Belegstand in SPEC-035 und im Leser-KDoc, statt den Text anzupassen ([ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) Folgepflicht). — **Ausgang:** entfallen: Geprüft am 2026-10-31 gegen die Release-Liste (neuestes Release v1.12.0 vom 2026-10-27, `schema.json` von d-migrate zuletzt am 2026-10-25, vor dem Belegstand a339849); der Belegstand ist nicht gealtert, Erneuern war nicht nötig. Die Folgepflicht der [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) bleibt bei jedem Slice, der den Leser berührt.
- Zwei Punkte der ADR sind nicht gemessen und fallen im Leser an: was bei fehlendem `precision` oder `scale` bei `decimal` gilt und welche Genauigkeit die Zeittypen haben (Offen 7 der ADR; das Schema von d-migrate nennt `precision` und `scale` bei `decimal` im Beschreibungstext „Pflicht“, ohne Constraint; der Slice führt das Ergebnis als Festlegung), und ob der Transport die Länge eines Anweisungstextes begrenzt (Offen 8; der Scanner hat nach den Messzeilen der ADR bei 1,29 Mio Zeichen rund 79 ms gebraucht, der Slice setzt keine Obergrenze). — **Ausgang:** weiter offen: → `BEO-TDS/messung-am-echten-sql-server-fehlt`. Der Leser führt beide als Festlegung (`precision` und `scale` bei `decimal` Pflicht, `scale` höchstens `precision`, Grenze 38; Zeittypen ohne Genauigkeit; keine Obergrenze für die Länge des Anweisungstextes), am Server ist nichts gemessen. Der dort genannte Träger `slice-qa-vergleichslauf-d` führt diese beiden Punkte nicht in seiner DoD: der Träger ist für sie unvollständig, das Register führt sie.
- Ein Test, der eine Auswahl- oder Gleichheitseigenschaft mit einheitlicher Eingabe behauptet, bleibt bei jeder Mutation grün (`BEO-TDS/strukturtest-ohne-variation-der-eingabe`); ein Zähler ohne Untergrenze prüft nichts (`BEO-TDS/schranken-test-mit-ungeprueftem-zaehler`). Die Tests variieren die Eingabe, die Attrappe hat einen positiven Kontrollfall. — **Ausgang:** entfallen: Das Muster trat in zwei Formen auf (F-3 ein Negativtest an der Wertstelle fehlte, F-4 ein Zähler ohne Untergrenze) und ist im Slice behoben (`assertEquals(600, …)`, `assertEquals(7, …)`, sechs Schablonen mal sieben Quotierungen); die Verifikation röterte beide Mutationen. Die Wiederkehr steht als Evidence in `BEO-TDS/schranken-test-mit-ungeprueftem-zaehler` (3×).
- Groß-/Kleinschreibung und `"…"` als Bezeichner sind in [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) an einer Instanz in einer Stichprobe bestätigt; die Tests führen das als Festlegung, nicht als Messung. — **Ausgang:** weiter offen: → `BEO-TDS/messung-am-echten-sql-server-fehlt`. Die Regel (`toLowerCase(toUpperCase(cp))`, `s` gleich `ſ`, `i` gleich `İ`) steht in [SPEC-036](../../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) als Festlegung und hat einen Test (ea338ef); die Kollation eines echten Servers ist nicht gemessen.
- Die Lesart der Liste „Nicht erkannt“ gegenüber dem Lastenheft ist in [slice-adr-simulation-formen](../done/slice-adr-simulation-formen.md) zu belegen; weicht die ADR von der Liste ab, folgt dieser Slice der ADR. [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) belegt die Deckung je Einzelfall (Abschnitt „Deckung der Liste ‚Nicht erkannt‘“); die Lesart „die positive Aufzählung ist abschließend“ hat der Nutzer nicht ausdrücklich bestätigt; SPEC-036 führt sie als Setzung, und die Bestätigung ist Offen 12 der ADR (Träger: Risikozeile in [slice-adr-simulation-formen](../done/slice-adr-simulation-formen.md)). — **Ausgang:** weiter offen: → `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` und `BEO-TDS/spec-einzelfaelle-ohne-lastenheft-deckung`. Offen 12 (a) der ADR bleibt ohne Bestätigung des Nutzers; der Slice folgt der ADR, SPEC-036 führt die Lesart als Setzung.
- Größe: Der erste Liefer-Punkt trägt jetzt Typen, Validierung, Schlüssel, Leser vom Baum, Typabbildung und Ladefehler; er ist der größte der drei. Wächst der Slice über das Maß aus §4 (Zeilen, Liefer-Punkte), ist der Schnitt „Tabellenbeschreibung samt Leser“ und „Anweisungserkennung“ als zwei Slices (siehe §4, Rückführung `in-progress` → `next`). — **Ausgang:** entfallen: Das Maß aus §4 ist unterschritten (gemessen mit `wc -l` am Stand der Verifikation: 1044 Zeilen unter `simulation` in `src/main`, Grenze 1500; 2420 in `src/test`, Grenze 3500), es bleiben drei Liefer-Punkte.
- Hinzugekommen im Review (F-11, Verifikation V-2): `SimTable` behält `columns` und `primaryKey` unkopiert, weil acht Konstruktorparameter bei `LongParameterList` (Grenze 6) nur als `data class` das Gate passieren; Index und Validierung gelten nur zum Zeitpunkt der Übergabe, der Katalog kopiert. Die Zusage steht in KDoc und [SPEC-035](../../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) („der Aufrufer ändert sie nicht“), kein Test und kein Gate hält sie. — **Ausgang:** weiter offen: → `BEO-TDS/listen-ohne-kopie-wegen-parameterzahl-gate`; Träger für die Lösung ist die Risikozeile in [slice-sim-aenderung-integration](../open/slice-sim-aenderung-integration.md), der Zuschnitt von `SimTable` oder das Lockern von `LongParameterList` (ADR, `AGENTS.md` §3.6) ist Entscheidung des Nutzers.

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

- **Was hat funktioniert:** Die Reihenfolge Beschreibung, Scanner, Zuordnung, Objektprüfung trug; die drei Liefer-Punkte liegen in einem Package ohne Verstoß gegen die Importregel. Der Zeichenscanner ohne Regex mit Orakel, Grenzfällen und erkanntem Zwilling je Negativfall hielt: Die Verifikation röterte 24 von 25 Mutationen, die 25. ist durch den Test der Mini-Runde behoben. Die zählende Attrappe mit positivem Kontrollfall bewies, dass Zeilenquelle und Callback in den Ablehnungsfällen nie gerufen werden.
- **Was ging anders als geplant:** (a) Der Review fand drei MEDIUM (F-1 Buchstaben außerhalb der Grundebene, F-2 Stelle der Ladefehler nur teilweise belegt, F-3 Negativtest an der Wertstelle fehlte) und zwei LOW; die Fix-Runde (d0d932a Doku, 188c4f3 Code) behob alle zwölf Befunde, F-11 bewusst nur als Rest. (b) Die Verifikation fand kein HIGH und kein MEDIUM, aber V-1 (Festlegung `s` gleich `ſ` ohne Test), V-2 (Rest F-11), V-3 (Datum), V-5 (KDoc-Rest); die Mini-Runde ea338ef behob V-1, V-3 und V-5. (c) Die Plan-Tabelle in §3 und das Maß der Rückführung in §4 mussten nachgezogen werden (F-6, F-7). (d) Der Titel des Tests zu F-11 sprach von einem Schutz, den er für die Tabelle nicht belegt; der Planner hat ihn in 8eef26b präzisiert.
- **Reports:** [Review F-1 bis F-12](../../../reviews/2026-10-31-7aa0763-slice-sim-erkennung.md), [Verifikation V-1 bis V-5](../../../reviews/2026-10-31-b339b77-verifikation-slice-sim-erkennung.md). **Wer was gesehen hat:** (1) Den Stand 7aa0763 hat der Reviewer gesehen. (2) Die Fix-Runde 188c4f3 hat **kein eigenes Review**; sie hat nur die Verifikation gesehen, durch 25 Mutationen und Lesen des Codes (V-4). (3) Die Mini-Runde ea338ef (ein Test, eine Datumszeile im Plan, eine KDoc-Zeile) haben nur der Implementer und die Planner-Durchsicht von `git show ea338ef` gesehen: kein Produktionscode im Diff; dass der Test `hält s und langes s als gleichen Namen` die Mutation „nur `toLowerCase`“ rötet, folgt aus dem Lesen (`toLowerCase(ſ)` ist `ſ`, nicht `s`), ist aber von keinem Kontext gelaufen und gemessen. (4) Die Titeländerung 8eef26b hat nur der Planner gesehen (Name der Testfunktion, Rumpf unverändert). Ein Review durch einen weiteren Kontext liegt für (2) bis (4) nicht vor.
- **Frischer Lauf der Closure:** `docker build --no-cache-filter test --target test .` auf dem Stand 8eef26b (nach `git merge main`, einschließlich Mini-Runde), einmal: Exit 0, 648 Tests PASSED, 0 FAILED, BUILD SUCCESSFUL in 1 m 11 s, die Stage `test` nicht aus dem Cache. Darin der umbenannte Test `der Katalog kopiert seine Liste, die Tabelle behält den Spaltenindex der Übergabe` und der Test der Mini-Runde. `make spec-abdeckung`: Matrix neu erzeugt (SPEC-035: 33 Tests, SPEC-036: 50, SPEC-037: 6, SPEC-038: 5, alle ohne Waise; 14 Festlegungen der Matrix insgesamt ohne Nachweis, keine davon in SPEC-035 bis SPEC-038). `make gates` auf dem Stand vor dem Move: siehe den Bericht der Closure (Ergebnis nicht in diese Zeile eingetragen, damit sie keinen Lauf behauptet, der nach ihr stattfindet).

**Zuordnung je Aussage.** *gemessen* heißt ein Lauf, der besteht, am Code dieses Slice und an handgebauten Eingaben und Bäumen, **nicht** an einem echten SQL Server und nicht an einem Referenzclient; *Handvektor* ein Fall mit Rechnung von Hand oder Zählung gegen eine Quelle; *Selbstkonsistenz* Code gegen Code (Orakel und Parser aus demselben Slice); *Festlegung* eine Entscheidung ohne Messung.

| Stärke | Aussage | Beleg |
|---|---|---|
| gemessen (Eigencode, handgebaute Eingabe) | Jede Zeile der Liste „Nicht erkannt“ endet als nicht erkannt, und ihr erkannter Zwilling bleibt erkannt (`JOIN`, Unterabfrage, Aggregat, `ORDER BY`, Ausdrücke, `OR`, `NOT`, `LIKE`, `BETWEEN`, `OUTPUT`, mehrere Anweisungen und weitere) | `StatementFormsTest` (`assertOnlyExtensionFails`); Mutationen der Review- und der Verifikationsrunde rot |
| gemessen (Eigencode) | Literale, Bezeichner (`[…]`, `"…"`, unquotiert) und Kommentare werden an ihren Grenzen getrennt; `;` und `--` im Literal ändern die Zuordnung nicht; ein quotierter Bezeichner an der Wertstelle ist kein Wert (`[NULL]`, `"x"`) | `StatementScannerTest`, `StatementFormsTest` (sechs Schablonen mal sieben Quotierungen); Mutationen M3a, M3b rot |
| gemessen (Eigencode) | Wörter bestehen aus Unicode-Buchstaben je Codepunkt (U+20000, U+1D400; Nicht-Buchstabe U+1F600; einzelnes Ersatzzeichen) | `StatementScannerTest`, `StatementOracleTest`; Mutationen M1a bis M1c rot |
| gemessen (Eigencode, Attrappe) | Zuordnung und Objektprüfung rufen weder Zeilenquelle noch Callback; ein positiver Kontrollfall zählt fünf Aufrufe; unbekannte Tabelle oder Spalte ergibt `TDS_SIM_UNKNOWN_OBJECT`, nicht erkannte Anweisung `TDS_UNSUPPORTED_SQL`; ohne Schema gilt `dbo` | `StatementPlannerTest`; zwei `runBlocking`-Mutationen rot |
| gemessen (Eigencode) | Die Codes in `SimCodes` sind Namen von `ErrorCode`, für alle Konstanten | `SimulationCodesTest`; Mutationen M5, M5b rot |
| gemessen (Eigencode, handgebauter Baum) | Der Leser liest die Teilmenge samt Ignorierliste, verlangt `tables`, bildet zehn Typen ab, weist Typen ohne Entsprechung ab; jeder Ladefehler trägt Pfad, Zeile und Spalte der richtigen Stelle (Schlüssel- oder Wertstelle) und nie Inhalt; Pfadabschnitte werden bereinigt und gekürzt | `SimDescriptionReaderTest` (26 Tests); Mutationen der Stelle (sieben der Verifikation) und M6a bis M6d rot |
| gemessen (Eigencode) | Validierung: doppelte Tabellen- und Spaltennamen ohne Beachtung der Schreibweise, leere Spaltenliste, Schlüssel nicht leer, vorhanden und nicht nullbar, Dezimalstellen höchstens 38 in der Schnittstelle; der Katalog kopiert seine Liste | `SimModelTest` (8 Tests); Mutationen M7, M7b, `tables` ohne `toList()` rot |
| Handvektor | Ignorierliste 9 + 5 + 12 Felder und Gesamtzahlen 13, 7 und 19 Felder sowie 21 Typnamen (zehn abgebildet, elf abgewiesen) nachgezählt gegen das `schema.json` von d-migrate bei a339849; `schema_format` `1.0`; Zeilen unter `simulation` 1044 und 2420 mit `wc -l` | Review (Antwort 2), Verifikation (Plan-vs-Code-Diff); die Quelle wurde gelesen (`gh api`), nicht gebaut |
| Selbstkonsistenz | 600 erzeugte Anweisungen, 64 Grenzfälle und 7 Anhänge werden vom Parser wiedererkannt oder abgelehnt, wie der Erzeuger sie baut | `StatementOracleTest`; der Erzeuger und der Parser stammen aus demselben Slice, das belegt Übereinstimmung beider, keine Treue zu SQL Server |
| Festlegung | Namensvergleich ohne Beachtung der Schreibweise über `toLowerCase(toUpperCase(cp))`, also `s` gleich `ſ` und `i` gleich `İ`, `ss` verschieden von `ß` (Test seit ea338ef hält die Regel, nicht die Kollation eines Servers); Wörter als Unicode-Buchstaben; Groß-/Kleinschreibung und `"…"` als Bezeichner (Stichprobe an einer Instanz nach [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md)) | [SPEC-036](../../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig); `SimNames` |
| Festlegung | Schwellen der Rückführung 1500 Zeilen in `src/main` und 3500 in `src/test` unter `simulation` (Setzung des Plans, kein Messwert; die Ist-Werte 1044 und 2420 sind gemessen) | Plan §4 |
| Festlegung | Die Liste „Nicht erkannt“ ist abschließend (Lesart, vom Nutzer unbestätigt, Offen 12 (a) der ADR); `tables` ist Pflicht (strenger als das Schema); bei `decimal` sind `precision` und `scale` Pflicht, `scale` höchstens `precision`; Zeittypen ohne Genauigkeit; keine Obergrenze der Länge des Anweisungstextes; `max_length` wird gelesen, nicht durchgesetzt; Pfadabschnitt höchstens 64 Codepunkte | [SPEC-035](../../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends), SPEC-036, KDoc des Lesers und des Parsers |
| Festlegung | `SimTable` behält die übergebenen Listen unkopiert; die Zusage „der Aufrufer ändert sie nicht“ trägt nur KDoc und SPEC-035, kein Test hält sie (der Test belegt den Katalog und den Index zum Zeitpunkt der Übergabe) | `SimModel.kt`; Risiko F-11 in §6 |
| nicht gemessen | Alles am echten SQL Server (Kollation, Quotierung, Bezeichnerregeln, Länge des Anweisungstextes, Genauigkeit der Zeittypen, `decimal` ohne `precision`); das Verhalten des Referenzclients gegen die Erkennung; Scanner-Laufzeit und -Speicher in diesem Slice (die ADR nennt 79 ms bei 1,29 Mio Zeichen aus dem Spike, ein Test dazu liegt nicht vor); die Überführung von Literal und Parameter in den Spaltentyp ([slice-sim-auswahl-streaming](../in-progress/slice-sim-auswahl-streaming.md)); die Weitergabe an Callbacks ([slice-sim-aenderung-integration](../open/slice-sim-aenderung-integration.md)) | Plan §1 und §6; Verifikation „Eigene Läufe“ |

- **Gegenstand:** entfällt (der Slice lieferte).
- **Steering-Loop-Eintrag:** Eine **benannte Spec-Lücke**, ohne `liegt in`, weil sie in einer versionierten Spec steht und nicht an einem Zielort: [SPEC-036](../../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) führt die Lesart „die Liste ‚Nicht erkannt‘ ist abschließend“ als Setzung, und der Nutzer hat sie nicht bestätigt ([ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) Offen 12 (a)); der Slice hat sie in den Negativtests von `StatementFormsTest` gebaut, sie bleibt ohne Bestätigung. Dazu der Lerneintrag aus den Befunden: Eine Festlegung trägt erst dann, wenn ihre Abbildungsregel (`toUpperCase` vor `toLowerCase`) einen Test hat, der die Mutation ohne den Schritt rötet; die Kennzeichnung „nicht gemessen“ ersetzt den Test nicht (V-1). Das ist die Wiederkehr von `BEO-TDS/aussagen-ueber-messung-hinaus` (verkörpert: `AGENTS.md` §3.8, Reviewer-Skill) und kein neuer Sensor. Daneben gezählt und nicht verkörpert, alle mit diesem Slice über der Schwelle oder weiterhin darüber: (1) `BEO-TDS/schranken-test-mit-ungeprueftem-zaehler` erreicht mit diesem Slice 3× (F-4), über der Schwelle, nicht verkörpert; Entscheidung beim Nutzer (Regel, Sensor, Träger-Slice oder Streichung). (2) `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test` steht bei 8× (F-2, F-3, V-1), über der Schwelle, nicht verkörpert; Entscheidung beim Nutzer. (3) `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` steht bei 8×, über der Schwelle, nicht verkörpert; Entscheidung beim Nutzer. (4) `BEO-TDS/spec-einzelfaelle-ohne-lastenheft-deckung` steht bei 4×, über der Schwelle, nicht verkörpert; Entscheidung beim Nutzer (Lastenheft-Nachzug, Deckungsnachweis je Einzelfall oder Streichung). (5) `BEO-TDS/messung-am-echten-sql-server-fehlt` steht bei 18× und ist geplant (`slice-qa-vergleichslauf-d`), für Offen 7 und 8 der ADR unvollständig (§6). Kein Eintrag ohne Zählung: F-6 und F-7 (Plan-Tabelle gegen Diff, Rückführung ohne Maß) sind einmalig und behoben, F-8, F-9, F-10, F-12 behoben ohne Muster.
- **Beobachtungs-Register (`../observations/`):** `evidence/slice-sim-erkennung.md` ergänzt in [BEO-TDS/aussagen-ueber-messung-hinaus](../observations/BEO-TDS/aussagen-ueber-messung-hinaus/observation.md) (F-1, F-5, V-2, V-3; Zähler 32), [BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test](../observations/BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test/observation.md) (F-2, F-3, V-1; Zähler 8), [BEO-TDS/schranken-test-mit-ungeprueftem-zaehler](../observations/BEO-TDS/schranken-test-mit-ungeprueftem-zaehler/observation.md) (F-4; Zähler 3), [BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt](../observations/BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt/observation.md) (Offen 12 (a); Zähler 8), [BEO-TDS/spec-einzelfaelle-ohne-lastenheft-deckung](../observations/BEO-TDS/spec-einzelfaelle-ohne-lastenheft-deckung/observation.md) (Zähler 4) und [BEO-TDS/messung-am-echten-sql-server-fehlt](../observations/BEO-TDS/messung-am-echten-sql-server-fehlt/observation.md) (Offen 7 und 8, Namensvergleich; Zähler 18). Neue Beobachtung mit dieser Evidence-Datei: [BEO-TDS/listen-ohne-kopie-wegen-parameterzahl-gate](../observations/BEO-TDS/listen-ohne-kopie-wegen-parameterzahl-gate/observation.md) (F-11, V-2; Zähler 1, Ausgang offen mit Träger in [slice-sim-aenderung-integration](../open/slice-sim-aenderung-integration.md)). Nicht angefasst, trotz Nennung im Plan: `BEO-TDS/strukturtest-ohne-variation-der-eingabe` (6×); F-3 ist als Negativtest-Lücke unter `zeile-oder-zweig-ohne-unterscheidenden-test` geführt, weil dort die Eingabe fehlte, nicht gleichförmig war.
- **F-11 (weiter offen):** `SimTable` kopiert seine Listen nicht; das Gate `LongParameterList` (Grenze 6, `SimTable` hat acht Parameter) lässt nur die `data class` zu, die Zusage steht in KDoc und SPEC-035. Der Test-Titel, der mehr sagte als er belegt, ist präzisiert (8eef26b); sonst nicht geändert. Zeiger: die neue Risikozeile in [slice-sim-aenderung-integration](../open/slice-sim-aenderung-integration.md) (kopiert dort, wo der Slice `SimTable` aus Listen des Integrators erzeugt) und `BEO-TDS/listen-ohne-kopie-wegen-parameterzahl-gate`. **Nutzerentscheidung nötig**, wenn der Weg über den Integrationsslice nicht reicht: ein ADR zum Lockern von `LongParameterList` (`AGENTS.md` §3.6) oder ein Zuschnitt von `SimTable` auf höchstens sechs Parameter.
- **Folge-Slices:** keine neuen. Die weiter offenen Risiken haben Träger als Datei: [slice-sim-aenderung-integration](../open/slice-sim-aenderung-integration.md) (F-11), [slice-qa-vergleichslauf-d](../open/slice-qa-vergleichslauf-d.md) (Messung am echten Server; für Offen 7 und 8 unvollständig), das Register für die Lesart.
- **Risiken aus §6:** alle acht tragen genau einen Ausgang (siehe §6): vier entfallen (Scanner-Fehlzuordnung, Belegstand, Strukturtest und Zähler, Größe), vier weiter offen (Offen 7 und 8, Groß-/Kleinschreibung, Lesart, F-11).
- **Drei Paarungen:** Anker: der Eintrag trägt kein `liegt in`, die Paarung entfällt. Folge-Slice: [slice-sim-aenderung-integration](../open/slice-sim-aenderung-integration.md) und [slice-qa-vergleichslauf-d](../open/slice-qa-vergleichslauf-d.md) existieren als Datei in `open/`; der erste führt F-11 in seinem Risiko (ergänzt mit dieser Closure), der zweite führt Offen 7 und 8 nicht (benannte Ausnahme, im Register geführt). Register: jede genannte Kennung ist ein Verzeichnis mit nicht leerem `evidence/`.
- **Startbarkeit des Nachfolgers:** Der Trigger von [slice-sim-auswahl-streaming](../in-progress/slice-sim-auswahl-streaming.md) (`slice-sim-erkennung` und `slice-backend-streaming-nachweis` in `done/`, Zeilendarstellung entschieden in [ADR-0013](../../adr/0013-backend-grenzen.md) `Accepted`) ist mit diesem Move erfüllt; der Slice liegt in `open/`, sein Kopf trägt noch keinen Verantwortlichen („bis zur Priorisierung“). Er ist startbar, sobald ein Verantwortlicher eingetragen und der Slice nach `next/` und `in-progress/` bewegt ist.

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
