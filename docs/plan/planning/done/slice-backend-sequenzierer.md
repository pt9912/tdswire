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

- [x] **Ereignis → Token-Abbildung** ([TDS-FA-05.a](../../../../spec/spezifikation.md#tds-fa-05a--ergebniskodierung) Schritt 2 bis 4, `SPEC-002`): `ResultStart` → COLMETADATA (nach der Validierung der Typabbildung), `Row` → ROW, `ResultEnd` und `UpdateCount` → DONE mit Zeilenzahl; `DONE_MORE` genau dann, wenn ein weiteres Ereignis folgt (Szenario aus [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md): Zeilen, Update-Count, Zeilen, leeres Ergebnis); leeres Ergebnis mit Metadaten ([TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung) Boundary); die Tokenfolge stimmt mit Handvektoren und dem Test-Decoder überein. *Bestätigt vom Verifier: alles bis auf den Decoder-Anteil; der Decoder-Test (`ResponseDecoderTest`) kam erst in der Restrunde (Commit `e9ce7af`, Befund V-2) und ist nicht nachverifiziert.*
- [x] **Fehler im Strom** (`SPEC-031`, [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung) Negative, Setzung des Nutzers in [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)): `Failure` vor `ResultStart`, `Failure` nach teilweise gesendeten Zeilen (Zeilen, dann ERROR und DONE mit Fehlerstatus), Ausnahme aus `execute()` oder aus dem `Flow` (Form nach Entscheidung des Port-Slice), Stellvertreter für das Mapping von `Failure.code` (Funktionsparameter, im Test fest; Stellung nach Entscheidung aus §6), ein ausdrücklicher Ausgang „Strom nicht mehr abschließbar → Verbindung ohne ERROR und DONE schließen“ als Ergebniswert, kein zweiter Resultset-Anfang nach einer Teilantwort, `close()` der Ausführung in jedem Ausgang.
- [x] **Reihenfolgeprüfung und Robustheit** (`SPEC-031`, [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilit%C3%A4t)): `Row` vor `ResultStart`, doppeltes `ResultEnd`, `ResultStart` im offenen Ergebnis und jedes Ereignis nach `Failure` enden in einem definierten Ausgang (Ausgang nach Entscheidung aus §6); zufällige Ereignisfolgen mit festem Startwert enden stets in einem definierten Ausgang und nie in einer Ausnahme, die den Prozess oder eine andere Verbindung beeinflusst.

Nachweis-Pflichten für alle drei Punkte:

- [x] Jeder Test trägt im KDoc über der Testfunktion den Block-Tag `@spec SPEC-<NNN>`; die Tests laufen in `make test` und sind grün. *(Verifier: alle 37 Tests in `protocol/response` tragen den Tag, 420 Tests PASSED auf `1b5f6f4`; die Tests der Restrunde `e9ce7af` liefen im Gate dieser Closure.)*
- [x] Jede Aussage im Quelltext (KDoc) und in Spec oder Vertrag steht an einem Test oder ist als Festlegung gekennzeichnet; die Closure-Notiz trennt *gemessen*, *Handvektor*, *Selbstkonsistenz* und *Festlegung* wie `slice-codec-tds-paket` (`BEO-TDS/aussagen-ueber-messung-hinaus`). Die Messung in [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) gilt für den Spike, nicht für den Quelltext dieses Slice.
- [x] `make gates` grün. *(Exit 0 auf dem Stand der Closure; der Verifier fuhr `make gates` nicht als Ganzes, V-7.)*
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
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/` (Unterpackage nach Wahl des Implementers, z. B. `response`) | neu | Sequenzierer (`ARC-015`, soweit Ergebnissequenz; ohne Anfragezustand), ruft Port und Typabbildung, schreibt `Token`s des Codecs; keine Imports aus `transport`-Implementierung, `simulation`, `runtime` |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/` (gleiches Unterpackage) | neu | Ereignisfolgen mit dem Test-Backend (Happy, Boundary, Negative nach FA-05, FA-08), Handvektoren, Fuzz mit festem Startwert, Zähler für `close()`; `@spec`-Tag je Test |
| `spec/spezifikation.md` (`SPEC-022`) | update | `SPEC-031` und §Offen des Vertrags: Prüfung der Reihenfolge, Verhalten der Vorschau bei `Failure`, Schicksal eines zurückgehaltenen Abschlusses; nur, was entschieden und gebaut ist, sonst „Offen“ lassen |

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

Die DoD ist vollständig, `make gates` ist grün, der Review-Report liegt vor, und die Closure-Notiz trägt die Zuordnung je Aussage des Sequenzierers (*gemessen* · *Handvektor* · *Selbstkonsistenz* · *Festlegung*) sowie den Steering-Loop-Lerneintrag; `SPEC-031` und `spec/spezifikation.md` `SPEC-022` §Offen sind auf den entschiedenen Stand gebracht.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

**Nutzerentscheidungen vom 2026-10-30** (Quelle: Anweisung des Nutzers an den Implementer der Fix-Runde; sie liegen nur hier und in den Texten von KDoc, `SPEC-031` und `spec/spezifikation.md` `SPEC-022` vor, nicht als eigenes Dokument). Sie betreffen die ersten Risiken dieser Liste; der Ausgang je Risiko bleibt der Closure vorbehalten:

1. `Failure` zählt als nächstes Ereignis der `DONE_MORE`-Vorschau. Beleg nur für das Bit vor dem ERROR: Referenzclient gegen einen echten SQL Server, [ADR-0007](../../adr/0007-fehler-und-attention.md) R05; am Produkt nicht gemessen. Für statement-abbrechende Fehler (R04: `DONE[MORE|ERR]`) schreibt der Sequenzierer die Variante ohne `MORE`.
2. Ein zurückgehaltener Abschluss wird bei Cancel oder Disconnect verworfen.
3. Ein Reihenfolgeverstoß des Backends ist ein Fall von `SPEC-048`.
4. `ReturnStatus` und `OutputParameter` werden abgelehnt (Fehler der Typabbildung), nicht abgebildet.

**Rest nach der Fix-Runde zum Review** ([Report](../../../reviews/2026-10-30-d7a7536-slice-backend-sequenzierer.md)), was nicht aufgelöst ist:

- Zwei Vokabulare für „Strom nicht synchron“ bleiben: `streamSynchronous = false` am `ErrorMapper` (Einstieg für Aufrufer ohne Sequenzierer) und `SequenceOutcome.CLOSE_WITHOUT_COMPLETION`. Die Folge ist eine: `ErrorMapper.fateOf(outcome)` bildet den Ausgang auf `CLOSE_WITHOUT_REPLY` ab, ein Test belegt die Entsprechung. Das DONE mit Fehlerstatus wird nur noch an einer Stelle gebildet. — **Ausgang:** weiter offen: → `slice-sql-batch-dispatch` §2, dessen Verdrahtung `ErrorMapper.sequencerMapping()` und `fateOf` nutzt; der Einstieg mit `streamSynchronous` hat bis dahin nur Tests als Aufrufer, und welches der beiden Vokabulare bleibt, ist dort zu entscheiden.
- `SequenceOutcome` trägt die Verbindungsfolge vollständig (`FAILED` offen, `FAILED_CLOSE_CONNECTION` schließt nach der Antwort), weil die Abbildung sie je Fall mitgibt (`MappedError.closesConnection`); ein Aufrufer muss die Meldung im Tokenstrom nicht suchen und nicht per Gleichheit einer Regel zuordnen. — **Ausgang:** entfallen: Feststellung ohne Risiko; die Entsprechung belegt `fateOf bildet jeden Ausgang des Sequenzierers ab` (total, Mutant V06 tot), der Leser fehlt (siehe vorigen Punkt).
- Das Fuzz-Orakel ist ein zweiter Leser derselben Festlegungen (getrenntes Modell), keine Messung. Die Zusage „kein `detail` eines `ProtocolError` erreicht den Client“ steht am Parameter von `ErrorMapping` (KDoc) und hängt am Mapper; ein Test, der einen Mapper mit Übernahme von `detail` rot macht, gibt es im Sequenzierer nicht. Die Verifikation sieht Mutant V17 (der Mapper hängt `detail` an den Meldungstext) nur deshalb tot, weil der Vergleichstest `protocol()` unverändert lässt; ein Mapper, der `detail` in beiden Pfaden übernimmt, wäre nicht abgedeckt. — **Ausgang:** weiter offen: → Register `BEO-TDS/aussagen-ueber-messung-hinaus` (Beleg dieses Slice: die Zusage steht enger als ihr Test); die Prüfung gehört an den Mapper, ein Träger-Slice mit eigenem Test ist nicht benannt.
- Unverändert offen: `OutOfMemoryError`, Zuordnung `curCmd`, Messung am echten Server und die Verzögerung durch den zurückgehaltenen Abschluss (siehe unten). — **Ausgang:** `OutOfMemoryError` weiter offen: → `slice-adr-backend-grenzen` (S2), mit Verweis in `slice-backend-streaming-nachweis` §6; die übrigen drei haben ihren Ausgang unten.

- **`Failure` in der `DONE_MORE`-Vorschau — Entscheidungsbedarf (Nutzer):** Zählt ein `Failure` als „nächstes Ereignis“ (ein zurückgehaltenes `ResultEnd` oder `UpdateCount` bekommt `DONE_MORE`) oder nicht? [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) weist das diesem Slice zu, „nach Messung am echten Server“; eine solche Messung gibt es im Repo nicht ([ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md): nicht gemessen). Eine Entscheidung ist eine Setzung und als *Festlegung* zu kennzeichnen. — **Ausgang:** entfallen: entschieden mit der Nutzerentscheidung 1 vom 2026-10-30 und umgesetzt als Festlegung (ein `Failure` gibt dem zurückgehaltenen Abschluss `DONE_MORE`, für statement-abbrechende Fehler die Variante ohne `DONE_MORE` am Abschluss mit Fehlerstatus); belegt durch `Failure nach ResultEnd gibt dem zurueckgehaltenen Abschluss DONE_MORE` und das Modell im Fuzz, Mutant V14 tot. Der Beleg am Produkt bleibt offen und geht in das Risiko „Ohne Messung am echten Server“ unten.
- **Statement- gegen batch-abbrechende Fehler — Entscheidungsbedarf (Nutzer):** R04 gegen R05 bis R07 ([ADR-0007](../../adr/0007-fehler-und-attention.md)) unterscheiden sich in dem, was nach dem Fehler noch läuft; `Failure` trägt keine Klasse. Der Sequenzierer kann nur eine Variante schreiben, solange der Port nichts anderes trägt. — **Ausgang:** weiter offen: → `slice-fehler-zustandsuebergang` §6 (seit dieser Closure eingetragen); die Lücke steht in der Spezifikation unter den offenen Punkten des Backend-Vertrags ([SPEC-022](../../../../spec/spezifikation.md#query-backend-vertrag-zu-spec-022)). Der Sequenzierer schreibt für jeden Fehler eine Variante und beendet den Lauf.
- **Schicksal eines zurückgehaltenen Abschlusses bei Cancel oder Disconnect** ([ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) §Offen): wird er verworfen, mit oder ohne `DONE_MORE` geschrieben? Die Cancel-Seite (ATTENTION-Bestätigung) gehört zu `welle-fehler-abbruch`. — **Ausgang:** entfallen: entschieden mit der Nutzerentscheidung 2 (verworfen) und umgesetzt; Test `Abbruch verwirft den zurueckgehaltenen Abschluss …`, Zweig `HANG` im Fuzz, Mutant V01 tot. Die Cancel-Seite (ATTENTION-Bestätigung) war nie Gegenstand dieses Slice, sie liegt bei `slice-attention-cancel-timeout`.
- **Definition von „synchron“** (`SPEC-031`: ERROR und DONE nur, „wenn der Strom synchron bleibt“): die Spezifikation definiert den Begriff nicht (benannte Spec-Lücke). Ohne Definition ist der Ausgang „Verbindung ohne Abschluss schließen“ nicht prüfbar. — **Ausgang:** entfallen: die Lücke ist als Festlegung geschlossen („synchron“ heißt, dass der Transport jedes bisher erzeugte Token angenommen hat, [SPEC-031](../../../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität)); Tests `abgelehntes …` und das Modell im Fuzz, Mutanten V07, V07b, V08 tot. Die Festlegung ist die des Implementers und vom Nutzer nicht bestätigt.
- **Ausgang bei Reihenfolgeverstoß:** `SPEC-048` (`TDS_INTERNAL_ERROR`) legt ERROR, DONE und Verbindungsende für unerwartete Ausnahmen im Backend-Adapter fest; ob ein Reihenfolgeverstoß des Backends ein solcher Fall ist, ist nicht gesagt. — **Ausgang:** entfallen: entschieden mit der Nutzerentscheidung 3 (Fall von `SPEC-048`) und umgesetzt; Test `Reihenfolgeverstoss endet in TDS_INTERNAL_ERROR` (sieben Verstöße), Mutant V10 tot.
- **Herkunft des Fehler-Mappings** (`Failure.code` → Nummer, Severity, State): [TDS-FA-06.a](../../../../spec/spezifikation.md#tds-fa-06a--fehlerabbildung) und die Nummern in `SPEC-011` bis `SPEC-016` sind ein „Vorschlag“; `welle-fehler-abbruch` ist die zuständige Welle. Planungs-Lesart (keine Entscheidung): Funktionsparameter am Sequenzierer, im Test mit festem Stellvertreter. — **Ausgang:** eingetreten: die Schnittstelle mit Stellvertreter trug nicht (Review F-1; die Rückführung in §4 war der benannte Fall) und wurde in der Fix-Runde umgebaut (`ErrorMapping` mit `MappedError`, `ErrorMapper.sequencerMapping()`, `fateOf`, Integrationstest ohne Fake). Der verbleibende Rest, ein Leser in `src/main`, geht an die Folge-Slices `slice-sql-batch-dispatch` §2 und `slice-fehler-zustandsuebergang` §6.
- **`ReturnStatus`/`OutputParameter` vor `welle-anfrageverarbeitung`:** ablehnen ([TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte) Negative: „ohne unvollständigen Erfolgsstream“) oder durchreichen? — **Ausgang:** entfallen: entschieden mit der Nutzerentscheidung 4 (abgelehnt) und umgesetzt, Test `ReturnStatus und OutputParameter werden abgelehnt`, Mutant R tot. Die Wirkung mit dem Mapping (Class 20, die Verbindung schließt, V-6) steht in KDoc und Spezifikation; die Abbildung statt der Ablehnung liefert `slice-antwort-mehrteilig-output`.
- **`curCmd` und Zeilenzahl:** `ResultEnd(affectedRows: Long?)` und `UpdateCount` tragen keine Befehlskennung; der Spike sandte den Update-Count mit dem Befehlscode eines `SELECT`, was ihn verfälschte ([ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)). Woher `curCmd` kommt und wer bei `null` zählt, ist nicht festgelegt (benannte Spec-Lücke); der Codec schreibt die Zahl so, wie sie gegeben ist. — **Ausgang:** weiter offen als benannte Spec-Lücke: [SPEC-031](../../../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität) und die offenen Punkte des Backend-Vertrags führen sie („der Sequencer schreibt 0; der Port trägt keine Kennung“), kein Slice trägt sie. Ob der Referenzclient die Kennung braucht, ist nicht gemessen (`BEO-TDS/messung-am-echten-sql-server-fehlt`).
- **Verzögerung durch den zurückgehaltenen Abschluss** ist nicht gemessen ([ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)). — **Ausgang:** weiter offen: → `slice-backend-streaming-nachweis` §6 (Ereigniszahl am Fake, keine Latenz), dort weiter an `slice-qa-performance`.
- **Ohne Messung am echten Server:** Tokenreihenfolge und DONE-Bits sind Handvektor; `bn` (ohne Vorschau) zeigte, dass der Referenzclient das Bit in den Spike-Szenarien nicht brauchte, nicht dass es entbehrlich ist (`BEO-TDS/messung-am-echten-sql-server-fehlt`). — **Ausgang:** weiter offen: → Register `BEO-TDS/messung-am-echten-sql-server-fehlt` (Beleg dieses Slice: `evidence/slice-backend-sequenzierer.md`), dessen Träger `slice-qa-vergleichslauf-d` ist (`slice-qa-reproduzierbarkeit` liegt in `done/` und trägt es nicht).

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

- **Was hat funktioniert:** Der Schnitt trug: Abbildung, Fehlerausgänge und Reihenfolgeprüfung blieben in einem Slice mit drei Liefer-Punkten, ohne Rückführung nach §4 (die Schnittstelle zum Mapping war der benannte Rückführungsfall und ließ sich in der Fix-Runde lösen). Der Sequenzierer ist ohne Sitzung und ohne Transport testbar (`protocol.response` importiert nur `domain`, `port`, `protocol.token`, `protocol.result`, `protocol.wire`; die Schichtregel hält `make detekt`). Die Prüfung durch Mutation fand, was Lesen und grüne Tests nicht fanden: der Review fuhr die Mutanten einzeln (einer überlebte), die Verifikation weitere 20 (alle tot). Die `DONE_MORE`-Vorschau ist über eine Steuerung mit `CompletableDeferred` ohne Wanduhr geprüft.
- **Was ging anders als geplant:** (a) **Die Schnittstelle mit Stellvertreter trug nicht** (Review F-1): der Mapper lieferte eine fertige Nachricht, der Sequenzierer brauchte den Inhalt des ERROR in der laufenden Nachricht, und die Verbindungsfolge hatte zwei Quellen. Umgebaut in der Fix-Runde: `ErrorMapping` mit `MappedError`, `ErrorMapper.sequencerMapping()` und `fateOf`, ein Integrationstest über beide Seiten ohne Fake. (b) **Die Ausnahme eines Backends erreichte nie den Log** (F-2): `internal` bekommt jetzt die Ausnahme (bei einem Reihenfolgeverstoß `null`). (c) **Mutant E überlebte** (F-3): `NonCancellable` um `close()` war nur mit einem nicht suspendierenden `close()` belegt; ein Test mit suspendierendem `close()` fängt ihn. (d) **Abschnittsnummern ohne Quelle** (F-4) sind aus KDoc und Tests entfernt; Stufe und Wortlaut der Festlegungen sind nachgeführt (F-7, F-9). (e) **Eine Abbruchausnahme aus dem Strom lief anders als die aus `execute()`** (F-6): beide laufen jetzt nach außen. (f) **Restrunde nach der Verifikation** (Commits `e9ce7af`, `55278d5`, `d827919`): Decoder-Test der Sequenzer-Ausgabe (V-2), zwei Ablehnungstests der Senke (V-5), `AuthExecutor` überschreibt das Passwort vor dem Abschluss des Jobs (V-10), KDoc und Spezifikation auf `FAILED_CLOSE_CONNECTION` und die Werte von 18456 gebunden (V-6, V-3), die Träger der Verdrahtung benannt (V-1) und die Lücke des Vollständigkeitstests als Risiko im Mapping-Slice geführt (V-4). **Diese Restrunde hat kein Verifier nachgeprüft;** dass die Mutanten dort rot sind (Zeilenzahl im DONE-Writer +1 färbt alle vier Decoder-Tests, die beiden Ablehnungsmutanten V07b und V08 die neuen Tests) und dass `AuthExecutorTest` nach dem Fix 0 von 12003 Läufen rot war (vorher 1 von 4000), sind Angaben des Implementers.
- **Reports:** [Review](../../../reviews/2026-10-30-d7a7536-slice-backend-sequenzierer.md) (kein HIGH; laut Tabelle F-1 bis F-4 MEDIUM, F-5, F-6 LOW, F-7 bis F-10 INFO; die Kopfzeile des Reports zählt drei MEDIUM) und [Verifikation](../../../reviews/2026-10-30-1b5f6f4-verifikation-slice-backend-sequenzierer-und-fehler-mapping.md) (gemeinsam mit `slice-fehler-mapping`, Stand `1b5f6f4`; kein HIGH; V-1, V-2 MEDIUM, V-3 bis V-6 und V-10 LOW, V-7 bis V-9 INFO). Stand der Befunde: F-1 bis F-7 und F-9 behoben, F-8 ehrlich offen (Geheimnisfreiheit des `detail` hängt am Mapper), F-10 mit Zuständigkeit `slice-backend-streaming-nachweis`; V-1 bis V-6 und V-10 laut Restrunde behoben (ungeprüft, siehe oben), V-7 durch das Gate dieser Closure (Exit 0), V-8 durch diese Closure, V-9 benannt (eine Ausnahme aus `close()` oder aus der Senke läuft nach außen und verdeckt den Ausgang; die KDoc sagt „dafür gibt es keine Zusage“, zuständig `slice-backend-streaming-nachweis`). Ein Validator-Lauf entfällt: der Slice liefert keinen End-Nutzer-Wert, außerhalb der Tests ruft ihn bisher niemand auf.

**Zuordnung Beleg | Aussage.** Fünf Stärken: *gemessen* heißt an einem echten Client oder Server beobachtet, hier nur die Messungen aus [ADR-0007](../../adr/0007-fehler-und-attention.md) (Referenzclient gegen einen echten SQL Server 2022, Messreihe R; Spike-Server, Messreihe T) und nie am Produkt; *Handvektor* heißt von Hand abgeleitet, ohne unabhängige Quelle im Repo; *Selbstkonsistenz* heißt Code gegen Code (Encoder, Test-Decoder, Modell im Test); *Festlegung* heißt Setzung des Nutzers oder des Implementers ohne Messung; *nicht gemessen* nennt, was keine Aussage trägt.

| Stärke | Aussage | Beleg im Repo |
|---|---|---|
| gemessen (nicht am Produkt) | Das Bit `DONE_MORE` vor dem ERROR eines batch-abbrechenden Fehlers (R05) und `DONE[MORE\|ERR]` eines statement-abbrechenden (R04); Class bis 19 offen, ab 20 schließt der Server (R10) als Hintergrund | [ADR-0007](../../adr/0007-fehler-und-attention.md), Messreihen R; der Sequenzierer hat sie nicht wiederholt, kein Referenzclient hat seinen Tokenstrom gesehen |
| Handvektor | Bytes einer Antwort mit einer Zeile; DONE mit COUNT und MORE | `ResponseMappingTest` (`@spec SPEC-031`, KDoc „von Hand abgeleitet“); ein Bericht über Nachrechnen mit anderem Werkzeug liegt nicht vor |
| Selbstkonsistenz | Tokenfolge eines Szenarios (Zeilen, Update-Count, Zeilen, leeres Ergebnis) gegen den Test-Decoder; Zufallsfolgen (4000 Läufe, Startwert 20261030) gegen ein getrenntes Modell der erlaubten Folgen, mit Ausnahme im Strom, Abbruch und Ablehnung der Senke; Sequenzierer gegen den echten Mapper ohne Fake | `ResponseDecoderTest` (Restrunde, ungeprüft), `ResponseOrderTest`, `ResponseErrorMapperTest`; Orakel und Implementierung teilen die Festlegungen, ein gemeinsamer Irrtum bliebe unentdeckt |
| Selbstkonsistenz (Verhalten) | `close()` genau einmal in jedem Ausgang, auch bei Abbruch und bei suspendierendem `close()`; ein Abbruch verwirft den zurückgehaltenen Abschluss; Ausnahmen und Reihenfolgeverstöße enden in `TDS_INTERNAL_ERROR` mit Log; die Abbruchausnahme läuft aus `execute()` und aus dem Strom nach außen | `ResponseFailureTest`, `ResponseOrderTest`; Mutanten: Review einzeln gefahren (einer überlebte, danach behoben), Verifikation 20 von 20 tot; die Mutationen der Restrunde sind Angabe des Implementers |
| Festlegung (Nutzerentscheidung 2026-10-30, Quelle nur als Text) | `Failure` zählt als nächstes Ereignis der Vorschau, für statement-abbrechende Fehler die Variante ohne `DONE_MORE`; ein zurückgehaltener Abschluss wird bei Cancel oder Disconnect verworfen; ein Reihenfolgeverstoß ist ein Fall von `SPEC-048`; `ReturnStatus` und `OutputParameter` werden abgelehnt | §6 dieses Slice, KDoc des `ResponseSequencer`, [SPEC-031](../../../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität); Tests je Punkt |
| Festlegung (Implementer, vom Nutzer nicht bestätigt) | „synchron“ heißt, dass der Transport jedes bisher erzeugte Token angenommen hat; ein Fehler beendet den Lauf, weitere Ereignisse werden nicht gelesen; die Folge der Ablehnung eines Typs ist mit dem Mapping Class 20 und Schließen | KDoc, [SPEC-031](../../../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität); Tests pinnen die Wahl; Register `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` |
| nicht gemessen | Annahme der Tokenfolge und der DONE-Bits durch den Referenzclient; Verzögerung durch den zurückgehaltenen Abschluss; Rückstau bis zur Senke (die Bauform trägt, ein Test fehlt bewusst); `OutOfMemoryError`; Herkunft von `curCmd` (der Sequenzierer schreibt 0); Ausnahmen aus `close()` und aus der Senke | Träger siehe Tabelle unten; Register `BEO-TDS/messung-am-echten-sql-server-fehlt` |

**Offene Punkte der Closure mit Träger** (gegen den Inhalt der Dateien geprüft; „Nutzerentscheidung“ ist kein Träger):

| Offener Punkt | Träger | Stand |
|---|---|---|
| `ErrorMapper` (`sequencerMapping`, `fateOf`) hat keinen Leser in `src/main` | `slice-sql-batch-dispatch` (`open/`) | DoD-Zeile „Verdrahtung des Fehler-Mappings“ nennt beide Funktionen und einen Test, dass ein schließender Ausgang nicht als offene Verbindung endet |
| Ausführung der Verbindungsfolge aus dem Ausgang | `slice-fehler-zustandsuebergang` (`open/`) | §6 nennt Leser und Ausführung |
| Statement- gegen batch-abbrechende Fehler (`Failure` trägt keine Klasse) | `slice-fehler-zustandsuebergang` (`open/`) | §6, mit dieser Closure eingetragen; die Lücke steht auch in den offenen Punkten des Backend-Vertrags der Spezifikation |
| `curCmd` der DONE-Tokens | benannte Spec-Lücke in der Spezifikation | [SPEC-031](../../../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität) und die offenen Punkte des Backend-Vertrags; kein Slice |
| Verzögerung durch den zurückgehaltenen Abschluss | `slice-backend-streaming-nachweis` (`open/`), weiter `slice-qa-performance` (`open/`) | §6 des Nachweis-Slice führt den Punkt und verweist auf die Performance |
| `OutOfMemoryError` | `slice-adr-backend-grenzen` (`open/`) | führt ihn als S2 |
| Messung am echten Server und am Referenzclient | Register `BEO-TDS/messung-am-echten-sql-server-fehlt` (Stand *geplant*, `slice-qa-vergleichslauf-d`) | Beleg dieses Slice liegt in `evidence/slice-backend-sequenzierer.md` |
| Cancel und Timeout als Fehler | `slice-attention-cancel-timeout` (`open/`) | §6 und DoD nennen die Entscheidung |
| Geheimnisfreiheit des `detail` hängt am Mapper, im Sequenzierer ohne eigenen Test | Register `BEO-TDS/aussagen-ueber-messung-hinaus` | kein Slice trägt einen Test; ehrlich offen |
| Zwei Vokabulare für „Strom nicht synchron“ | `slice-sql-batch-dispatch` (`open/`) | die Verdrahtung entscheidet, welcher Einstieg bleibt |

- **Gegenstand:** entfällt (der Slice lieferte).
- **Steering-Loop-Eintrag:** Kein `liegt in`: mit diesem Slice ist keine Regel, kein Sensor und keine Gate-Zeile verkörpert worden; gezählt wird, nicht verkörpert. Fünf Feststellungen. (1) **Gelernt: Eine Schnittstelle zu einem Slice, der parallel entsteht, wird erst an beiden Seiten zugleich sichtbar.** Der Plan sah „Funktionsparameter, im Test mit festem Stellvertreter“ vor; der Stellvertreter ließ jede Passung zu, und erst der Review fand, dass der echte Mapper nicht passte und die Verbindungsfolge zwei Quellen hatte. Der Integrationstest `ResponseErrorMapperTest` (Bytes und Folge je Backend-Code, ohne Fake) ist ein Test in `make test`, kein Gate und keine Regel; die Klasse ist `BEO-TDS/folge-einer-festlegung-im-nachbar-slice-unbenannt`, hier das zweite Auftreten (nach `slice-handshake-login`). (2) **Ein Abbruchschutz ist nur mit einem suspendierenden Gegenstück belegt:** `NonCancellable` um `close()` blieb unbemerkt, solange das `close()` des Test-Backends nicht suspendierte; zwei Ablehnungsmutanten starben nur am Fuzz. Das ist `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test` (zweites Auftreten, nach `slice-transport-listener-lebenszyklus`) und, für das Orakel, die neue Beobachtung `BEO-TDS/fuzz-orakel-spiegelt-die-implementierung`. (3) **Das Muster der Aussagen über den Beleg hinaus trat trotz der verkörperten Regel wieder auf** (Abschnittsnummern ohne Quelle, Stufe der Festlegung enger als der Beleg, KDoc mit `FAILED` gegen das Verhalten mit dem echten Mapper, Plan mit entschiedenen Fragen als offen): ein weiteres Auftreten von `BEO-TDS/aussagen-ueber-messung-hinaus`, keine Schärfung; `AGENTS.md` §3.8 war der Maßstab. (4) **Schwelle erreicht, nicht verkörpert:** `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` hat mit diesem Slice drei Belege (`slice-backend-typabbildung`, `slice-transport-listener-lebenszyklus`, `slice-backend-sequenzierer`). Eine Regel dazu ist nicht geschrieben; die Wellen laufen, also liegt der Lese-Schritt bei der nächsten Welle-Closure (`welle-backend-streaming` oder `welle-fehler-abbruch`, wer zuerst schließt; Schritt 25), und `state.md` bleibt `offen`. Kandidat für die Regel, keine Setzung: eine Lesart, die aus einer Nutzerentscheidung folgt, steht in der Closure-Notiz als eigene Zeile mit der Frage an den Nutzer. (5) **Benannte Spec-Lücke:** die Herkunft der Befehlskennung (`curCmd`) der DONE-Tokens; der Sequenzierer schreibt 0, die Spezifikation führt die Lücke in [SPEC-031](../../../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität) und unter den offenen Punkten des Backend-Vertrags. Dazu **ein Flake-Fix mit Reproduktion vor, aber nur Angabe des Implementers danach** (`AuthExecutorTest`, V-10): `BEO-TDS/flake-fix-ohne-reproduzierten-fehler`, hier das zweite Auftreten; die Zahlen sind Größenordnung (siehe die Evidence).
- **Beobachtungs-Register (`../observations/`):** `evidence/slice-backend-sequenzierer.md` ergänzt in `BEO-TDS/aussagen-ueber-messung-hinaus` (Review F-4, F-7, F-8; Verifikation V-6, V-8), `BEO-TDS/messung-am-echten-sql-server-fehlt`, `BEO-TDS/folge-einer-festlegung-im-nachbar-slice-unbenannt` (Review F-1, Verifikation V-1), `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test` (Review F-3, Verifikation V-5), `BEO-TDS/flake-fix-ohne-reproduzierten-fehler` (V-10) und `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` (V-6, Review F-9); eine neue Beobachtung mit einer Evidence-Datei: `BEO-TDS/fuzz-orakel-spiegelt-die-implementierung` (Review F-5, Verifikation V-5). Kein neues Auftreten: `BEO-TDS/test-belegt-nur-den-fake` (die Tests laufen gegen den Produktionscode; der Integrationstest nimmt den echten Mapper), `BEO-TDS/offene-punkte-ohne-traeger` (die Träger sind in der Tabelle oben geprüft; der Fund gehört zu `slice-fehler-mapping`), `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` und die übrigen. Die Zähler folgen aus den Dateien; `aussagen-ueber-messung-hinaus` und `messung-am-echten-sql-server-fehlt` stehen schon auf `verkörpert` bzw. `geplant`, ihr `state.md` bleibt unverändert.
- **Risiken aus §6:** alle vierzehn tragen genau einen Ausgang (siehe §6): sechs entfallen (Vorschau mit `Failure`, Schicksal des Abschlusses, Definition „synchron“, Ausgang bei Reihenfolgeverstoß, `ReturnStatus`, Feststellung zur Verbindungsfolge im Ausgang), eines eingetreten (Herkunft des Fehler-Mappings: umgebaut in der Fix-Runde, der Rest geht an die Folge-Slices `slice-sql-batch-dispatch` und `slice-fehler-zustandsuebergang`), sieben weiter offen mit Träger oder Register-Eintrag (statement- gegen batch-abbrechend, `curCmd`, Verzögerung, Messung, zwei Vokabulare, Geheimnisfreiheit des `detail`, `OutOfMemoryError`). Kein Risiko verlangt einen Carveout.
- **Drei Paarungen:** Anker: kein Eintrag trägt `liegt in`, die Paarung entfällt. Folge-Slice: `slice-sql-batch-dispatch`, `slice-fehler-zustandsuebergang`, `slice-backend-streaming-nachweis`, `slice-adr-backend-grenzen`, `slice-attention-cancel-timeout` und `slice-qa-vergleichslauf-d` existieren als Dateien und führen die Punkte; ein Start-Trigger von `slice-backend-streaming-nachweis` (`slice-backend-sequenzierer` liegt in `done/`) ist mit diesem `→ done` erfüllt. Register: sechs Evidence-Dateien in vorhandenen und eine in einer neuen Beobachtung, siehe oben.

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
