# Slice slice-statementkind-index-mitglieder: `StatementKind` bekommt `CREATE_INDEX` (`0x00c8`) und `DROP_INDEX` (`0x00c9`), beide ohne `COUNT`, wie der echte SQL Server

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-testserver-manuell

**Bezug:** [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md)

**Berührte Spec-Stellen:** SPEC-059 (Liste der öffentlichen Typen, Änderungsprotokoll) · SPEC-002 (Aufzählung `StatementKind`) · SPEC-031 (Befehlskennung des Abschlusses) · SPEC-036 (Form D4 und Form D6, Grenze (a)) · SPEC-035 (Callback der Strukturänderung, Art des Abschlusses)

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-12.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung**; die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks.

**Ziel:** `CREATE INDEX` und `DROP INDEX` schließen am Testserver wie am echten SQL Server (2022 und 2025): `DONE` ohne `COUNT` mit der Befehlskennung `0x00c8` bzw. `0x00c9`, statt `DONE` mit `COUNT` 0 und der Kennung 0 (Art `UNKNOWN`). Dafür bekommt die öffentliche Aufzählung `StatementKind` genau zwei Mitglieder, `CREATE_INDEX` und `DROP_INDEX`, nach dem Muster von `CREATE_TABLE` und `ALTER_TABLE`. Maßstab des Nutzers: die Spezifikation ist Zielbild, der echte SQL Server die Referenz.

**Herkunft.** Punkt 13 (a) des Registers [katalog-elemente-grenzen-der-angleichung](../observations/BEO-TDS/katalog-elemente-grenzen-der-angleichung/state.md), Fund der Messung und des Reviews zu [slice-drop-index-if-exists](../done/slice-drop-index-if-exists.md) (Form D6, Grenze (a)). **Entscheid des Nutzers am 2026-11-12 per Auswahl: „Ja, zwei Mitglieder aufnehmen“.** Eine Folge-ADR ist nicht nötig: [ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) Entscheidung 2 legt fest, dass ein Mitglied nach der Messung entsteht; der Entscheid steht in diesem Slice und im Änderungsprotokoll der Spezifikation. Gemessen ist `CREATE INDEX` mit `0x00c8` in vier Anweisungen je Instanz (gewöhnlich, eindeutig, zweispaltig), `DROP INDEX` mit `0x00c9` in der Ablage von `slice-drop-index-if-exists`; `INCLUDE`, Filter und absteigende Spalte sind nicht gemessen.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Weitere Mitglieder von `StatementKind` (auch für `DROP CONSTRAINT`: bleibt bei `ALTER_TABLE`, Kennung `0x00d8` gemessen) — der Entscheid des Nutzers nennt genau diese zwei; jede weitere Art ist ein neuer Entscheid des Nutzers nach [ADR-0046](../../adr/0046-oeffentliche-flaeche-der-katalog-elemente.md).
- `ENVCHANGE` Typ 8 und 9 bei `CREATE INDEX` und `DROP INDEX` nach `set implicit_transactions on` — nicht gemessen und nicht gebaut (Setzung wie bei `ALTER_TABLE`, [ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) Entscheidung 5): eine Zusage braucht einen Handvektor aus einer Messung.
- `CREATE INDEX` mit `INCLUDE`, Filter und absteigender Spalte — nicht gemessen; die Form bleibt, wie sie ist, und meldet die Art mit dem Wortlaut „Setzung, nicht gemessen“ (§6).
- Die zwei Abweichungen nach 3701 (State 7 statt 6 bei unbekannter Tabelle, Befehl `0x00fd` statt `0x00c9` im `DONE` nach dem Fehler) — Register Punkt 13 (b), intern, Trigger: ein Client wertet Befehl oder State aus.
- Entfernen eines eindeutigen Index durch d-migrate (`DROP CONSTRAINT IF EXISTS <Index>`) — Register Punkt 14, Trigger: die Abnahme [slice-abnahme-d-migrate](slice-abnahme-d-migrate.md).
- Mehrere Anweisungen in einem Text — Grenze (e) der Form D6 in [SPEC-036](../../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig), ein eigener Vorgang.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **Bau** ([TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend)): `StatementKind.CREATE_INDEX` und `DROP_INDEX` in der öffentlichen Aufzählung (am Ende, die Ordinalwerte der vorhandenen Mitglieder bleiben); `Token.forKind` (`0x00c8`, `0x00c9`), `Token.countsRows` (beide ohne `COUNT`) und `SessionTransaction.apply` (kein Zustandswechsel) behandeln sie (der Compiler erzwingt die Ergänzung); der Planer meldet die Art für Form D4 (`CREATE [UNIQUE] INDEX`) und Form D6 (`DROP INDEX`); Tests am Draht (`DONE` ohne `COUNT`, Kennungen, auch im RPC-Fall der Abschlüsse) und der Einbettungstest `StatementKindEmbeddingTest` auf der öffentlichen Fläche; Mutationen je Zusage über enge Testklassen; die öffentliche Fläche wächst um genau diese zwei Mitglieder, sonst Entscheid des Nutzers.
- [ ] **Fortschreibung** ([TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate)): `SPEC-059` (Liste und Änderungsprotokoll mit dem Vermerk **Inkompatibel** für Kotlin-`when` ohne `else` über `StatementKind`, wie bei den früheren Mitgliedern; ein Java-`switch` bleibt übersetzbar), `SPEC-002`, `SPEC-031`, `SPEC-035` und `SPEC-036` (Form D4 und D6, Grenze (a) entfällt), die Kompatibilitätsmatrix (Zeile der Befehlskennung nach `StatementKind`), die Nutzer-Doku (alle fünf Dateien unter `docs/user/` vollständig gegen diesen Slice gelesen, nicht nur die Fundstellen), das Register Punkt 13 (a). Der Entscheid „Mitglied nach Messung, keine Folge-ADR“ steht im Änderungsprotokoll.
- [ ] **Beleg am Draht** ([TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate)): ein Lauf von d-migrate (`schema migrate --execute` mit einem neuen Index im Soll, dazu das zweite Soll mit geändertem Index) gegen den Testserver endet mit Exit 0 und `IDENTICAL`, weil der Draht für `CREATE INDEX` und `DROP INDEX` sich ändert (`0x00c8`/`0x00c9` ohne `COUNT`); Messprotokoll in `docs/messungen/` (Rechner, Last, Digest von d-migrate). Die Nachmessung am echten Server entfällt: `CREATE INDEX` ist in den vier Fällen je Instanz gemessen, die Ablage liegt im Repo; ENVCHANGE 8/9 für diese Formen bleibt ungemessen und ungebaut.

Nachweis-Pflichten für alle Punkte:

- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Haken der Liefer-Punkte setzt der Planner nach der Verifikation, nicht der Implementer (`BEO-TDS/haken-vor-verifikation-gesetzt`).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist (hier berührt: `SPEC-059`).
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben oder „keine Beobachtung angefallen“ in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — Pfad-Kandidaten für §8; der Implementer-Agent erweitert die Liste in seinem ersten Lauf. Die Tabelle nennt **alle** berührten Dateien, auch die Zeiger-Dateien (Reviewer-Skill, Kategorie Plan-Tabelle).

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/domain/StatementKind.kt` | update | zwei neue öffentliche Mitglieder am Ende, KDoc mit Kennung, „ohne Zeilenzahl“ und Messprotokoll (Aussage höchstens so weit wie die Messung: drei Formen von `CREATE INDEX`) |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/token/Token.kt`, `protocol/response/SessionTransaction.kt`, `protocol/response/ResponseSequencer.kt` | update | `forKind`, `countsRows`, `apply` (die `when` über `StatementKind` ohne `else`; der Compiler erzwingt es), KDoc von `ResponseSequencer` (Aufzählung der Kennungen) |
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/StructurePlanner.kt`, `StatementPlanner.kt`, `SimulationBackend.kt` | update | `createIndex` (Form D4) meldet `CREATE_INDEX`, `dropIndex` (Form D6) meldet `DROP_INDEX` statt `UNKNOWN`; die KDoc „Art `CREATE_TABLE`, `ALTER_TABLE` oder `UNKNOWN`“ nennt die neuen Arten |
| `src/test/kotlin/.../protocol/response/ResponseStatementKindTest.kt`, `ResponseTransactionTest.kt`, `protocol/dispatch/TransactionDispatchTest.kt` | update | Draht: `DONE` ohne `COUNT`, Kennungen `0x00c8` und `0x00c9`, kein Zustandswechsel (Handvektoren aus der Ablage `struktur-drop-index.messung`, Zeilen 03 bis 06 und 07) |
| `src/test/kotlin/.../simulation/SimulationBackendDropIndexTest.kt`, `SimulationBackendElementsStructureTest.kt`, `SimulationBackendStructureTest.kt`, `src/integrator/kotlin/.../DropIndexEmbeddingTest.kt`, `StatementKindEmbeddingTest.kt` | update | Planer meldet die Art (D4, D6); Einbettungstest auf der öffentlichen Fläche und auf den Rohbytes; Mutationen je Zusage |
| `testserver/test/kotlin/.../TableStoreTest.kt` | prüfen | verweist auf `StatementKind`; nur ändern, wenn der Compiler es verlangt |
| `spec/spezifikation.md` (`SPEC-002`, `SPEC-031`, `SPEC-035`, `SPEC-036` Form D4 und D6 Grenze (a), `SPEC-059` Liste und Änderungsprotokoll) | update | Zusage, Aufzählung, Entscheid im Änderungsprotokoll (Inkompatibilität für Kotlin-`when` ohne `else`) |
| `docs/plan/kompatibilitaetsmatrix.md` | update | Zeile „Befehlskennung nach `StatementKind`“ führt die zwei Mitglieder; Zitate unverändert oder nachgezogen (`tools/matrix-zitate.sh` am Verifier-Lauf) |
| `docs/user/README.md`, `referenz-ereignisse.md`, `referenz-konfiguration.md`, `testserver-manuell.md`, `einbettung-und-betrieb.md` | update / lesen | alle fünf Dateien vollständig gelesen; die Aussagen „Art `UNKNOWN`“ für `CREATE INDEX` und `DROP INDEX` entfallen, die Grenzen in den Aufgaben zu Index und Zeilen der Antwortformen folgen dem Bau |
| `docs/messungen/` (neues Messprotokoll zum Lauf von d-migrate, Datum im Namen), `docs/messungen/drop-index-d-migrate-2026-11-11.md` | neu / update | Beleg am Draht (Liefer-Punkt 3); das ältere Protokoll bekommt nur den Zeiger auf den neuen Stand der Art |
| `docs/plan/planning/observations/BEO-TDS/katalog-elemente-grenzen-der-angleichung/` (`state.md`, `observation.md`) | update | Punkt 13 (a): gebaut; der Träger steht dann in `done/` |
| `docs/plan/planning/in-progress/roadmap.md`, `docs/plan/planning/welle-testserver-manuell.md` | update | Zustandsfeld und Zeile in der Slice-Tabelle der Welle, Reihenfolge |
| `docs/plan/planning/done/slice-drop-index-if-exists.md`, `docs/plan/planning/open/slice-abnahme-d-migrate.md` | update | Zeiger auf diesen Slice (Folge-Slice in §6/§7 des einen, Grenze „keine Voraussetzung“ im anderen) |
| `docs/plan/spec-abdeckung.md` | erzeugt | `make spec-abdeckung` einmal, Tests geändert |

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-drop-index-if-exists](../done/slice-drop-index-if-exists.md) liegt in `done/` (Form D6 und Messung der Ablage sind die Eingänge); ein Verantwortlicher ist eingetragen. Der Slice steht vor [slice-abnahme-d-migrate](slice-abnahme-d-migrate.md) als Reihenfolge, nicht als Voraussetzung: keine Funktion des Solls hängt an den Mitgliedern (der Abnahme-Slice führt ihn unter den Slices, die den Server dem echten SQL Server angleichen: Grenze, keine Voraussetzung); liegt er vor der Abnahme, misst der Lauf den Draht mit.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): die Mitglieder ziehen mehr als die Aufzählung und die Planer nach sich (zum Beispiel verlangt eine Form von `CREATE INDEX` eine eigene Kennung); Schnittvorschlag: `CREATE_INDEX` | `DROP_INDEX`.
- `in-progress` → `open` (blockiert): der Nutzer nimmt den Entscheid vom 2026-11-12 zurück; dann bleibt es bei `UNKNOWN` und der Grenze (a).

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln.

DoD vollständig, `make gates` grün, der Lauf von d-migrate gegen den Testserver endet mit Exit 0, Review und Verifikation liegen vor, Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — jedes Risiko bekommt genau einen Ausgang.

- Fremdcode mit einem Kotlin-`when` über `StatementKind` ohne `else` übersetzt nicht mehr (zwei neue Konstanten); ein Java-`switch` bleibt übersetzbar. Die Inkompatibilität steht im Änderungsprotokoll von `SPEC-059`, wie bei den Mitgliedern vom 2026-11-08 (vor `1.0` ändert sich die Fläche nur durch eine Änderung der Spezifikation). — **Ausgang:** offen bis zur Closure.
- Der Draht ändert sich für `CREATE INDEX` und `DROP INDEX` (`DONE` ohne `COUNT` und mit Kennung statt mit `COUNT` 0): ein Client, der den Zähler dieser Anweisungen liest, sieht ihn nicht mehr; der Server tut dasselbe. Beleg: Lauf von d-migrate gegen den Testserver, Rohbytes im Test. — **Ausgang:** offen bis zur Closure.
- Die öffentliche Fläche wächst um mehr als diese zwei Mitglieder (zum Beispiel ein Mitglied für `DROP CONSTRAINT` oder ein Feld). Genau zwei ist die Zusage dieses Plans; sonst entscheidet der Nutzer ([ADR-0046](../../adr/0046-oeffentliche-flaeche-der-katalog-elemente.md)); `make flaeche` prüft Typnamen, keine Mitglieder, der Einbettungstest und das Diff tragen die Zusage. — **Ausgang:** offen bis zur Closure.
- `ENVCHANGE` 8 und 9 bei `CREATE INDEX` und `DROP INDEX` unter `set implicit_transactions on` ist nicht gemessen; die Bibliothek beginnt keine Transaktion für diese Arten (Setzung wie bei `ALTER_TABLE`), und der echte Server tut es möglicherweise. — **Ausgang:** offen bis zur Closure.
- Die Kennung `0x00c8` für `CREATE INDEX` ist an drei Formen gemessen (gewöhnlich, eindeutig, zweispaltig); `INCLUDE`, Filter und absteigende Spalte sind nicht gemessen, und die KDoc des Mitglieds darf nicht breiter sagen (`AGENTS.md` §3.8). — **Ausgang:** offen bis zur Closure.
- Eigene Backends melden die Art nicht und bleiben bei `UNKNOWN` ([ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) Entscheidung 4); ein Backend, das die neue Art falsch meldet, liefert die falsche Kennung. — **Ausgang:** offen bis zur Closure.

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register; `grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln.

- **Was hat funktioniert:** —
- **Was ging anders als geplant:** —
- **Steering-Loop-Eintrag:** —
- **Beobachtungs-Register (`../observations/`):** —
- **Folge-Slices:** —
- **Risiken aus §6:** —

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung.

**Vorgelagert — Sub-Area-Wahl prüfen:** Eine Sub-Area (`*`, Kürzel `TDS`); keine Aufteilung nötig.

**Vorgelagert — offene Beobachtungen sichten:** `BEO-TDS/katalog-elemente-grenzen-der-angleichung` (Herkunft, Punkt 13 (a)); `BEO-TDS/aussagen-ueber-messung-hinaus` (die KDoc der Mitglieder trägt nur die gemessenen Formen) und `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` (alle Fundstellen von „`UNKNOWN`“ für `CREATE INDEX` und `DROP INDEX` in Spezifikation und `docs/user/` nachziehen) sind mit Treffern gesichtet.

Alle berührten Sub-Areas GF.
