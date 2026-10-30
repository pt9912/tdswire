# Slice slice-backend-sequenzierer: Sequenzierer QueryEvent → Token-Folge: DONE_MORE-Vorschau, Fehler im Strom, Reihenfolgeprüfung

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-backend-streaming

**Bezug:** [TDS-FA-05](../../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen), [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilit%C3%A4t), [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)

**Berührte Spec-Stellen:** SPEC-002 · SPEC-031 · SPEC-014 · SPEC-015 · ARC-007 · ARC-015

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

**Ziel:** Ein Sequenzierer übersetzt den `Flow<QueryEvent>` eines Backends in die Token-Folge des Codecs ([TDS-FA-05.a](../../../../spec/spezifikation.md#tds-fa-05a--ergebniskodierung) Schritt 2 bis 4, `SPEC-031`): COLMETADATA vor der ersten Zeile, ROW je Zeile, DONE-Familie mit `DONE_MORE` aus der Vorschau (ein `ResultEnd` oder `UpdateCount` wird ein Ereignis lang zurückgehalten), ERROR plus Abschluss mit Fehlerstatus bei einem Fehler im synchronen Strom, ein eigener Ausgang „Verbindung ohne Abschluss schließen“, und eine Prüfung der Ereignisreihenfolge; `close()` der Ausführung läuft in jedem Ausgang.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Backpressure bis zu einer Senke, Speichergrenze, Cancel mitten im Strom — `slice-backend-streaming-nachweis`; dieser Slice prüft Ereignisfolgen, nicht Durchsatz oder Heap.
- Port und Typabbildung — `slice-backend-port-vertrag`, `slice-backend-typabbildung`; der Sequenzierer ruft sie.
- Fehlernummer, Severity, State und Meldungstext aus `Failure.code` (Katalog nach [TDS-FA-06.a](../../../../spec/spezifikation.md#tds-fa-06a--fehlerabbildung)), ATTENTION-Bestätigung, Verbindungsende nach Severity — `welle-fehler-abbruch`; der Sequenzierer ruft eine Fehlerabbildung als Funktion und kennt keinen Katalog (siehe §6, Entscheidungsbedarf).
- Abbildung von `ReturnStatus` und `OutputParameter` auf `RETURNSTATUS`, `RETURNVALUE`, `DONEPROC`/`DONEINPROC` ([TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte)) — `welle-anfrageverarbeitung`; dieser Slice behandelt die fünf Ereignisse `ResultStart`, `Row`, `ResultEnd`, `UpdateCount`, `Failure` (siehe §6).
- Anfragezustand und Sperre je Sitzung (`SPEC-001`, `SPEC-029`, [ADR-0004](../../adr/0004-session-zustandsautomat.md)): der Sequenzierer ist ohne Sitzung testbar; die Anbindung ist Sache der Anfrageverarbeitung.
- Messung der Tokenreihenfolge und der DONE-Statusbits an einem echten SQL Server und am Referenzclient ([ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md): nicht gemessen) — `slice-qa-reproduzierbarkeit`, `slice-qa-kompatibilitaet`.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **Ereignis → Token-Abbildung** ([TDS-FA-05.a](../../../../spec/spezifikation.md#tds-fa-05a--ergebniskodierung) Schritt 2 bis 4, `SPEC-002`): `ResultStart` → COLMETADATA (nach der Validierung der Typabbildung), `Row` → ROW, `ResultEnd` und `UpdateCount` → DONE mit Zeilenzahl; `DONE_MORE` genau dann, wenn ein weiteres Ereignis folgt (Szenario aus [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md): Zeilen, Update-Count, Zeilen, leeres Ergebnis); leeres Ergebnis mit Metadaten ([TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung) Boundary); die Tokenfolge stimmt mit Handvektoren und dem Test-Decoder überein.
- [ ] **Fehler im Strom** (`SPEC-031`, [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung) Negative, Setzung des Nutzers in [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)): `Failure` vor `ResultStart`, `Failure` nach teilweise gesendeten Zeilen (Zeilen, dann ERROR und DONE mit Fehlerstatus), Ausnahme aus `execute()` oder aus dem `Flow` (Form nach Entscheidung des Port-Slice), Stellvertreter für das Mapping von `Failure.code` (Funktionsparameter, im Test fest; Stellung nach Entscheidung aus §6), ein ausdrücklicher Ausgang „Strom nicht mehr abschließbar → Verbindung ohne ERROR und DONE schließen“ als Ergebniswert, kein zweiter Resultset-Anfang nach einer Teilantwort, `close()` der Ausführung in jedem Ausgang.
- [ ] **Reihenfolgeprüfung und Robustheit** (`SPEC-031`, [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilit%C3%A4t)): `Row` vor `ResultStart`, doppeltes `ResultEnd`, `ResultStart` im offenen Ergebnis und jedes Ereignis nach `Failure` enden in einem definierten Ausgang (Ausgang nach Entscheidung aus §6); zufällige Ereignisfolgen mit festem Startwert enden stets in einem definierten Ausgang und nie in einer Ausnahme, die den Prozess oder eine andere Verbindung beeinflusst.

Nachweis-Pflichten für alle drei Punkte:

- [ ] Jeder Test trägt im KDoc über der Testfunktion den Block-Tag `@spec SPEC-<NNN>`; die Tests laufen in `make test` und sind grün.
- [ ] Jede Aussage im Quelltext (KDoc) und in Spec oder Vertrag steht an einem Test oder ist als Festlegung gekennzeichnet; die Closure-Notiz trennt *gemessen*, *Handvektor*, *Selbstkonsistenz* und *Festlegung* wie `slice-codec-tds-paket` (`BEO-TDS/aussagen-ueber-messung-hinaus`). Die Messung in [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) gilt für den Spike, nicht für den Quelltext dieses Slice.
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
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/` (Unterpackage nach Wahl des Implementers, z. B. `response`) | neu | Sequenzierer (`ARC-015`, soweit Ergebnissequenz; ohne Anfragezustand), ruft Port und Typabbildung, schreibt `Token`s des Codecs; keine Imports aus `transport`-Implementierung, `simulation`, `runtime` |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/` (gleiches Unterpackage) | neu | Ereignisfolgen mit dem Test-Backend (Happy, Boundary, Negative nach FA-05, FA-08), Handvektoren, Fuzz mit festem Startwert, Zähler für `close()`; `@spec`-Tag je Test |
| `spec/spezifikation.md`, `contracts/query-backend.md` | update | `SPEC-031` und §Offen des Vertrags: Prüfung der Reihenfolge, Verhalten der Vorschau bei `Failure`, Schicksal eines zurückgehaltenen Abschlusses; nur, was entschieden und gebaut ist, sonst „Offen“ lassen |

- Reihenfolge: Abbildung und vorschau, dann Fehlerausgänge, dann Reihenfolgeprüfung.
- Der Implementer liest `Token.kt`, `DoneStatus`, `TokenEncoder` und `TokenDecoder` (Test) und passt diese Tabelle an.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) steht auf `Accepted`, `slice-backend-port-vertrag` liegt in `done/` (erfüllt), `slice-backend-typabbildung` liegt in `done/`, `make gates` ist auf `main` grün, die Nutzerentscheidungen zu den offenen Punkten der Vorschau und des Fehlerausgangs (§6: `Failure` in der Vorschau, Schicksal des zurückgehaltenen Abschlusses, Definition „synchron“, Ausgang bei Reihenfolgeverstoß, Herkunft des Fehler-Mappings) liegen vor, und ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2 oder Entscheidungen aus §6, die den Umfang aufblähen; Schnittvorschlag: Slice A = Abbildung und Vorschau, Slice B = Fehlerausgänge und Reihenfolgeprüfung.
- `in-progress` → `open` (blockiert — Carveout?): [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) wird nicht `Accepted`; oder die Nutzerentscheidungen aus §6 bleiben aus, oder `welle-fehler-abbruch` legt das Fehler-Mapping so fest, dass die Funktionsparameter-Schnittstelle dieses Slice nicht trägt (Abstimmung mit dem Planer jener Welle).

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün, der Review-Report liegt vor, und die Closure-Notiz trägt die Zuordnung je Aussage des Sequenzierers (*gemessen* · *Handvektor* · *Selbstkonsistenz* · *Festlegung*) sowie den Steering-Loop-Lerneintrag; `SPEC-031` und `contracts/query-backend.md` §Offen sind auf den entschiedenen Stand gebracht.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **`Failure` in der `DONE_MORE`-Vorschau — Entscheidungsbedarf (Nutzer):** Zählt ein `Failure` als „nächstes Ereignis“ (ein zurückgehaltenes `ResultEnd` oder `UpdateCount` bekommt `DONE_MORE`) oder nicht? [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) weist das diesem Slice zu, „nach Messung am echten Server“; eine solche Messung gibt es im Repo nicht ([ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md): nicht gemessen). Eine Entscheidung ist eine Setzung und als *Festlegung* zu kennzeichnen. — **Ausgang:** offen bis zur Closure.
- **Statement- gegen batch-abbrechende Fehler — Entscheidungsbedarf (Nutzer):** R04 gegen R05 bis R07 ([ADR-0007](../../adr/0007-fehler-und-attention.md)) unterscheiden sich in dem, was nach dem Fehler noch läuft; `Failure` trägt keine Klasse. Der Sequenzierer kann nur eine Variante schreiben, solange der Port nichts anderes trägt. — **Ausgang:** offen bis zur Closure; Übergabe an `welle-fehler-abbruch` ist ein möglicher Ausgang, kein beschlossener.
- **Schicksal eines zurückgehaltenen Abschlusses bei Cancel oder Disconnect** ([ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) §Offen): wird er verworfen, mit oder ohne `DONE_MORE` geschrieben? Die Cancel-Seite (ATTENTION-Bestätigung) gehört zu `welle-fehler-abbruch`. — **Ausgang:** offen bis zur Closure.
- **Definition von „synchron“** (`SPEC-031`: ERROR und DONE nur, „wenn der Strom synchron bleibt“): die Spezifikation definiert den Begriff nicht (benannte Spec-Lücke). Ohne Definition ist der Ausgang „Verbindung ohne Abschluss schließen“ nicht prüfbar. — **Ausgang:** offen bis zur Closure; Vorschlag des Implementers als *Festlegung* mit Test.
- **Ausgang bei Reihenfolgeverstoß:** `SPEC-048` (`TDS_INTERNAL_ERROR`) legt ERROR, DONE und Verbindungsende für unerwartete Ausnahmen im Backend-Adapter fest; ob ein Reihenfolgeverstoß des Backends ein solcher Fall ist, ist nicht gesagt. — **Ausgang:** offen bis zur Closure.
- **Herkunft des Fehler-Mappings** (`Failure.code` → Nummer, Severity, State): [TDS-FA-06.a](../../../../spec/spezifikation.md#tds-fa-06a--fehlerabbildung) und die Nummern in `SPEC-011` bis `SPEC-016` sind ein „Vorschlag“; `welle-fehler-abbruch` ist die zuständige Welle. Planungs-Lesart (keine Entscheidung): Funktionsparameter am Sequenzierer, im Test mit festem Stellvertreter. — **Ausgang:** offen bis zur Closure.
- **`ReturnStatus`/`OutputParameter` vor `welle-anfrageverarbeitung`:** ablehnen ([TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte) Negative: „ohne unvollständigen Erfolgsstream“) oder durchreichen? — **Ausgang:** offen bis zur Closure; Entscheidung (Nutzer) und Abstimmung mit dem Planer von `welle-anfrageverarbeitung`.
- **`curCmd` und Zeilenzahl:** `ResultEnd(affectedRows: Long?)` und `UpdateCount` tragen keine Befehlskennung; der Spike sandte den Update-Count mit dem Befehlscode eines `SELECT`, was ihn verfälschte ([ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)). Woher `curCmd` kommt und wer bei `null` zählt, ist nicht festgelegt (benannte Spec-Lücke); der Codec schreibt die Zahl so, wie sie gegeben ist. — **Ausgang:** offen bis zur Closure.
- **Verzögerung durch den zurückgehaltenen Abschluss** ist nicht gemessen ([ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)). — **Ausgang:** weiter offen: → `slice-backend-streaming-nachweis` (Messung am Fake, ausdrücklich keine Aussage über echte Backends) oder `slice-qa-performance`.
- **Ohne Messung am echten Server:** Tokenreihenfolge und DONE-Bits sind Handvektor; `bn` (ohne Vorschau) zeigte, dass der Referenzclient das Bit in den Spike-Szenarien nicht brauchte, nicht dass es entbehrlich ist (`BEO-TDS/messung-am-echten-sql-server-fehlt`). — **Ausgang:** weiter offen: → `slice-qa-reproduzierbarkeit`.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); `port`, `protocol` und `domain` sind keine eigenen Pfad-Cluster mit eigener Strukturregel (die Schichten-Importregeln in `config/detekt/detekt.yml` sind Architektur-, keine Modus-Grenzen). Keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, acht Beobachtungen, alle Sub-Area `*`). Treffer: `BEO-TDS/aussagen-ueber-messung-hinaus` (7×, die Regel steht in `AGENTS.md` §3.8; sie bindet KDoc, Vertrag und Spec-Nachführung dieses Slice, Gegenmittel in §2) und `BEO-TDS/messung-am-echten-sql-server-fehlt` (3×; die Vorschau und die DONE-Statusbits sind nur als Handvektor belegt, §6). Ohne Treffer: die übrigen sechs.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
