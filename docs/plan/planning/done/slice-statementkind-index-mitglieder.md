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
- Entfernen eines eindeutigen Index durch d-migrate (`DROP CONSTRAINT IF EXISTS <Index>`) — Register Punkt 14, Trigger: die Abnahme [slice-abnahme-d-migrate](../done/slice-abnahme-d-migrate.md).
- Mehrere Anweisungen in einem Text — Grenze (e) der Form D6 in [SPEC-036](../../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig), ein eigener Vorgang.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit.

Liefer-Punkte (drei):

- [x] **Bau** ([TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend)): `StatementKind.CREATE_INDEX` und `DROP_INDEX` in der öffentlichen Aufzählung (am Ende, die Ordinalwerte der vorhandenen Mitglieder bleiben); `Token.forKind` (`0x00c8`, `0x00c9`), `Token.countsRows` (beide ohne `COUNT`) und `SessionTransaction.apply` (kein Zustandswechsel) behandeln sie (der Compiler erzwingt die Ergänzung); der Planer meldet die Art für Form D4 (`CREATE [UNIQUE] INDEX`) und Form D6 (`DROP INDEX`); Tests am Draht (`DONE` ohne `COUNT`, Kennungen, auch im RPC-Fall der Abschlüsse) und der Einbettungstest `StatementKindEmbeddingTest` auf der öffentlichen Fläche; Mutationen je Zusage über enge Testklassen; die öffentliche Fläche wächst um genau diese zwei Mitglieder, sonst Entscheid des Nutzers.
- [x] **Fortschreibung** ([TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate)): `SPEC-059` (Liste und Änderungsprotokoll mit dem Vermerk **Inkompatibel** für Kotlin-`when` ohne `else` über `StatementKind`, wie bei den früheren Mitgliedern; ein Java-`switch` bleibt übersetzbar), `SPEC-002`, `SPEC-031`, `SPEC-035` und `SPEC-036` (Form D4 und D6, Grenze (a) entfällt), die Kompatibilitätsmatrix (Zeile der Befehlskennung nach `StatementKind`), die Nutzer-Doku (alle fünf Dateien unter `docs/user/` vollständig gegen diesen Slice gelesen, nicht nur die Fundstellen), das Register Punkt 13 (a). Der Entscheid „Mitglied nach Messung, keine Folge-ADR“ steht im Änderungsprotokoll.
- [x] **Beleg am Draht** ([TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate)): ein Lauf von d-migrate (`schema migrate --execute` mit einem neuen Index im Soll, dazu das zweite Soll mit geändertem Index) gegen den Testserver endet mit Exit 0 und `IDENTICAL`, weil der Draht für `CREATE INDEX` und `DROP INDEX` sich ändert (`0x00c8`/`0x00c9` ohne `COUNT`); Messprotokoll in `docs/messungen/` (Rechner, Last, Digest von d-migrate). Die Nachmessung am echten Server entfällt: `CREATE INDEX` ist in den vier Fällen je Instanz gemessen, die Ablage liegt im Repo; ENVCHANGE 8/9 für diese Formen bleibt ungemessen und ungebaut.

Nachweis-Pflichten für alle Punkte:

- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
      Ohne eigenen Reviewer (Entscheid des Orchestrators: kleiner Slice); der Verifikationsbericht liest den Diff dabei wie ein Reviewer (Kopf des Berichts), Bericht: [Verifikation](../../../reviews/2026-11-12-7d8bb877-verifikation-slice-statementkind-index-mitglieder.md).
- [x] Haken der Liefer-Punkte setzt der Planner nach der Verifikation, nicht der Implementer (`BEO-TDS/haken-vor-verifikation-gesetzt`).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist (hier berührt: `SPEC-059`).
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben oder „keine Beobachtung angefallen“ in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — Pfad-Kandidaten für §8; der Implementer-Agent erweitert die Liste in seinem ersten Lauf. Die Tabelle nennt **alle** berührten Dateien, auch die Zeiger-Dateien (Reviewer-Skill, Kategorie Plan-Tabelle).

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/domain/StatementKind.kt` | update | zwei neue öffentliche Mitglieder am Ende, KDoc mit Kennung, „ohne Zeilenzahl“ und Messprotokoll (Aussage höchstens so weit wie die Messung: drei Formen von `CREATE INDEX`) |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/token/Token.kt`, `protocol/response/SessionTransaction.kt`, `protocol/response/ResponseSequencer.kt` | update | `forKind`, `countsRows`, `apply` (die `when` über `StatementKind` ohne `else`; der Compiler erzwingt es), KDoc von `ResponseSequencer` (Aufzählung der Kennungen) |
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/StructurePlanner.kt`, `StatementPlanner.kt`, `SimulationBackend.kt` | update | `createIndex` (Form D4) meldet `CREATE_INDEX`, `dropIndex` (Form D6) meldet `DROP_INDEX` statt `UNKNOWN`; die KDoc „Art `CREATE_TABLE`, `ALTER_TABLE` oder `UNKNOWN`“ nennt die neuen Arten |
| `src/test/kotlin/.../protocol/response/ResponseStatementKindTest.kt`, `ResponseTransactionTest.kt`, `protocol/dispatch/TransactionDispatchTest.kt` | update | Draht: `DONE` ohne `COUNT`, Kennungen `0x00c8` und `0x00c9`, kein Zustandswechsel (Handvektoren aus der Ablage `struktur-drop-index.messung`, Zeilen 03 bis 06 und 07) |
| `src/test/kotlin/.../simulation/SimulationBackendDropIndexTest.kt`, `SimulationBackendElementsStructureTest.kt`, `SimulationBackendStructureTest.kt`, `src/integrator/kotlin/.../DropIndexEmbeddingTest.kt`, `StatementKindEmbeddingTest.kt`, `IndexKindEmbeddingTest.kt` | update / neu | Planer meldet die Art (D4, D6); Einbettungstest auf der öffentlichen Fläche und auf den Rohbytes (`IndexKindEmbeddingTest` neu: `StatementKindEmbeddingTest` trägt schon die Höchstzahl der Funktionen je Klasse, die Konstanten der Aufzählung stehen dort); Mutationen je Zusage |
| `testserver/test/kotlin/.../TableStoreTest.kt`, `src/test/kotlin/.../protocol/dispatch/TransactionDispatchTest.kt` | prüfen | verweisen auf `StatementKind`; unverändert, der Compiler verlangt nichts |
| `spec/spezifikation.md` (`SPEC-002`, `SPEC-031`, `SPEC-035`, `SPEC-036` Form D4 und D6 Grenze (a), `SPEC-059` Liste und Änderungsprotokoll) | update | Zusage, Aufzählung, Entscheid im Änderungsprotokoll (Inkompatibilität für Kotlin-`when` ohne `else`) |
| `docs/plan/kompatibilitaetsmatrix.md` | update | Zeile „Befehlskennung nach `StatementKind`“ führt die zwei Mitglieder; Zitate unverändert oder nachgezogen (`tools/matrix-zitate.sh` am Verifier-Lauf) |
| `docs/user/README.md`, `referenz-ereignisse.md`, `referenz-konfiguration.md`, `testserver-manuell.md`, `einbettung-und-betrieb.md` | update / lesen | alle fünf Dateien vollständig gelesen; die Aussagen „Art `UNKNOWN`“ für `CREATE INDEX` und `DROP INDEX` entfallen, die Grenzen in den Aufgaben zu Index und Zeilen der Antwortformen folgen dem Bau |
| `docs/messungen/statementkind-index-d-migrate-2026-11-12.md` (neues Messprotokoll zum Lauf von d-migrate), `docs/messungen/drop-index-d-migrate-2026-11-11.md` | neu / update | Beleg am Draht (Liefer-Punkt 3); das ältere Protokoll bekommt nur den Zeiger auf den neuen Stand der Art |
| `docs/plan/planning/observations/BEO-TDS/katalog-elemente-grenzen-der-angleichung/` (`state.md`, `observation.md`) | update | Punkt 13 (a): gebaut; der Träger steht dann in `done/` |
| `docs/plan/planning/in-progress/roadmap.md`, `docs/plan/planning/welle-testserver-manuell.md` | update | Zustandsfeld und Zeile in der Slice-Tabelle der Welle, Reihenfolge |
| `docs/plan/planning/done/slice-drop-index-if-exists.md`, `docs/plan/planning/done/slice-abnahme-d-migrate.md` | update | Zeiger auf diesen Slice (Folge-Slice in §6/§7 des einen, Grenze „keine Voraussetzung“ im anderen) |
| `docs/plan/spec-abdeckung.md` | erzeugt | `make spec-abdeckung` einmal, Tests geändert |

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-drop-index-if-exists](../done/slice-drop-index-if-exists.md) liegt in `done/` (Form D6 und Messung der Ablage sind die Eingänge); ein Verantwortlicher ist eingetragen. Der Slice steht vor [slice-abnahme-d-migrate](../done/slice-abnahme-d-migrate.md) als Reihenfolge, nicht als Voraussetzung: keine Funktion des Solls hängt an den Mitgliedern (der Abnahme-Slice führt ihn unter den Slices, die den Server dem echten SQL Server angleichen: Grenze, keine Voraussetzung); liegt er vor der Abnahme, misst der Lauf den Draht mit.

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

- Fremdcode mit einem Kotlin-`when` über `StatementKind` ohne `else` übersetzt nicht mehr (zwei neue Konstanten); ein Java-`switch` bleibt übersetzbar. Die Inkompatibilität steht im Änderungsprotokoll von `SPEC-059`, wie bei den Mitgliedern vom 2026-11-08 (vor `1.0` ändert sich die Fläche nur durch eine Änderung der Spezifikation). — **Ausgang:** **eingetreten:** ein Kotlin-`when` ohne `else` über `StatementKind` übersetzt nicht mehr, ein Java-`switch` bleibt übersetzbar; die Inkompatibilität steht im Änderungsprotokoll von `SPEC-059` (Eintrag 2026-11-12). Der Verifier fand im Repo kein fremdes `when` ohne `else` (`Token.forKind` und `Token.countsRows` sind ergänzt, `SessionTransaction.apply` hat `else`), `testserver` übersetzt.
- Der Draht ändert sich für `CREATE INDEX` und `DROP INDEX` (`DONE` ohne `COUNT` und mit Kennung statt mit `COUNT` 0): ein Client, der den Zähler dieser Anweisungen liest, sieht ihn nicht mehr; der Server tut dasselbe. Beleg: Lauf von d-migrate gegen den Testserver, Rohbytes im Test. — **Ausgang:** **entfallen:** der Draht ändert sich wie geplant und ist belegt: Handvektoren `FD 00 00 C8 00` und `FD 00 00 C9 00` mit acht Nullbytes, byte-gleich mit der Ablage `struktur-drop-index.messung` (2022 und 2025, vom Verifier mit eigenem Dekoder gelesen), und fünf Läufe von d-migrate 1.12.0 gegen den Testserver, jeder mit Exit 0 und `IDENTICAL` ([Messprotokoll](../../../messungen/statementkind-index-d-migrate-2026-11-12.md)). Im RPC und beim Massenladen gilt die Kennung nicht (Setzung wie bei `CREATE_TABLE`, in Spezifikation und Nutzer-Doku genannt).
- Die öffentliche Fläche wächst um mehr als diese zwei Mitglieder (zum Beispiel ein Mitglied für `DROP CONSTRAINT` oder ein Feld). Genau zwei ist die Zusage dieses Plans; sonst entscheidet der Nutzer ([ADR-0046](../../adr/0046-oeffentliche-flaeche-der-katalog-elemente.md)); `make flaeche` prüft Typnamen, keine Mitglieder, der Einbettungstest und das Diff tragen die Zusage. — **Ausgang:** **entfallen:** die Fläche wuchs um genau zwei Mitglieder, beide am Ende der Aufzählung (Diff gelesen, Einbettungstest hält die zwölf Konstanten in Reihenfolge, Mutation M8 rot); `make flaeche` Exit 0.
- `ENVCHANGE` 8 und 9 bei `CREATE INDEX` und `DROP INDEX` unter `set implicit_transactions on` ist nicht gemessen; die Bibliothek beginnt keine Transaktion für diese Arten (Setzung wie bei `ALTER_TABLE`), und der echte Server tut es möglicherweise. — **Ausgang:** **weiter offen** als Setzung: `ENVCHANGE` Typ 8 und 9 bei `CREATE INDEX` und `DROP INDEX` nach `set implicit_transactions on` ist nicht gemessen und nicht gebaut; die Bibliothek beginnt keine Transaktion für diese Arten (Test, Mutation M3 rot), in Spezifikation, Messprotokoll und Nutzer-Doku genannt. Träger: Register `BEO-TDS/katalog-elemente-grenzen-der-angleichung` Punkt 13 (a), Trigger: eine Messung am echten Server oder ein Client, der es verlangt.
- Die Kennung `0x00c8` für `CREATE INDEX` ist an drei Formen gemessen (gewöhnlich, eindeutig, zweispaltig); `INCLUDE`, Filter und absteigende Spalte sind nicht gemessen, und die KDoc des Mitglieds darf nicht breiter sagen (`AGENTS.md` §3.8). — **Ausgang:** **weiter offen:** `INCLUDE`, Filter und absteigende Spalte sind nicht gemessen; `INCLUDE` meldet die Art als Setzung, Filter und absteigende Spalte weist der Planer ab (`TDS_UNSUPPORTED_SQL`). Die Aussagen der Spezifikation, der Matrix und der Nutzer-Doku sind in dieser Closure angeglichen (LOW-3); der KDoc-Beleg-Zeiger der Mitglieder und von `Token.forKind` und `Token.countsRows` nennt nur W-6 und M-20 (LOW-2) und ist im Register `BEO-TDS/aussagen-ueber-messung-hinaus` fortgeschrieben, weil die Änderung `src/` berührt.
- Eigene Backends melden die Art nicht und bleiben bei `UNKNOWN` ([ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) Entscheidung 4); ein Backend, das die neue Art falsch meldet, liefert die falsche Kennung. — **Ausgang:** **weiter offen** als Zusage des Backends: die Prüfung der Art eines fremden Backends fehlt weiter ([ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) Entscheidung 4).

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register; `grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln.

- **Was hat funktioniert:** Der Schnitt auf zwei Mitglieder mit dem Entscheid des Nutzers trug: Enum, `Token`, Planer, Spezifikation, Matrix und Nutzer-Doku zogen in einem Slice nach, und die sieben Mutationen des Verifiers (M1, M2, M3, M5, M7, M8, M9) sind alle rot, je Zusage eine enge Testklasse. Die Rohbytes las der Verifier selbst mit eigenem Dekoder (Anweisungen 3 bis 7 und 18 der Ablage, beide Instanzen) und fand sie gleich den Handvektoren der Tests. Die Position am Ende der Aufzählung ist durch den Listenvergleich des Einbettungstests gehalten (M8). Fünf Läufe von d-migrate 1.12.0 gegen den Testserver enden mit Exit 0 und `IDENTICAL`, im Log keine Zeile `request_failed`.
- **Was ging anders als geplant:** (1) Der Plan (§2, §3) sagte, der Compiler erzwinge die Ergänzung von `SessionTransaction.apply`; `apply` hat `else -> NONE`, der Compiler erzwingt nichts, und `SessionTransaction.kt` blieb ungeändert (INFO-1). Der Schutz „kein Zustandswechsel“ liegt allein im Test (`ResponseTransactionTest`, Mutation M3 rot) und genügt. (2) `TransactionDispatchTest` und `SimulationBackendStructureTest` blieben ungeändert: eine Index-Zusicherung mit `UNKNOWN` gab es dort nicht (INFO-2). (3) Die Läufe von d-migrate im Messprotokoll und im Verifikationsbericht nutzten verschiedene, nicht im Repo liegende Soll-Dateien; das Protokoll sagt es unter „Erzeuger der Zahlen“. (4) `make gates FORCE=1` des Verifiers kam wegen des Docker-Caches nach 35 s zurück; der frische Beleg der Tests ist das Log von `make clean-build` (INFO-4). (5) Ohne eigenen Reviewer (Entscheid des Orchestrators); der Verifier las den Diff wie ein Reviewer und fand LOW-1 bis LOW-4.
- **Zuordnung Messung | Festlegung:** Gemessen (Ablage `struktur-drop-index.messung`, beide Instanzen): `DONE` ohne `COUNT`, Status 0, Kennung `0x00c8` für `CREATE INDEX` (gewöhnlich, eindeutig, zweispaltig, Anweisungen 3 bis 6) und `0x00c9` für `DROP INDEX [IF EXISTS]` (Anweisung 7, auch an unbekannter Tabelle, Anweisung 18). Festlegung (Setzung, so gekennzeichnet): `INCLUDE` meldet `CREATE_INDEX`; `ENVCHANGE` Typ 8 und 9 bei den Arten wird nicht geschrieben; im RPC und beim Massenladen gilt die Kennung nicht (`DONE` mit Kennung 0 und `COUNT`); die Enum-Position am Ende (Setzung des Planners, nicht des Nutzers); `DROP CONSTRAINT` bleibt bei `ALTER_TABLE` `0x00d8` (gemessen, Entscheid des Nutzers).
- **Befunde der Verifikation und ihr Ausgang:** 0 HIGH, 0 MEDIUM, 4 LOW, 4 INFO. LOW-1 (der Satz „die Datei steht in `open/`“ im Done-Slice `slice-drop-index-if-exists` §7, ein falsches Zustandsfeld): in dieser Closure behoben, der Satz entfällt, der Link zeigt auf den Ruheort. LOW-3 (`INCLUDE` als Setzung nicht an allen Fundstellen): in dieser Closure in `SPEC-031`, der Kompatibilitätsmatrix und `docs/user/referenz-konfiguration.md` angeglichen. LOW-4 („Zeile“ ist die Nummer der Anweisung): im Messprotokoll gesagt. LOW-2 (Beleg-Zeiger von `Token.forKind`, `Token.countsRows` und dem Kopf-KDoc von `StatementKind` nennt nur W-6 und M-20; die Mitglieder zeigen auf `drop-index-d-migrate-2026-11-11`, die Ablage `struktur-drop-index` ist nicht M-20): **nicht behoben**, die Änderung liegt in `src/` und ist kein Auftrag der Closure; fortgeschrieben im Register `BEO-TDS/aussagen-ueber-messung-hinaus`, Wortlaut-Änderung im KDoc offen. INFO-1 bis INFO-4: siehe „Was ging anders“ und §6.
- **Steering-Loop-Eintrag:** Kein Eintrag verkörpert, daher kein `liegt in`; gezählt, nicht verkörpert, je Beleg 1× (unter der Schwelle): (1) *Eine Plan-Aussage „der Compiler erzwingt die Ergänzung“ wird vor dem Schreiben gegen die Bauart des `when` geprüft:* ein `when` mit `else` erzwingt nichts, und der Schutz liegt dann im Test; Prüfpunkt beim Schreiben des Plans: `grep -n "when (" ` über die Fundstellen des Typs und nachsehen, ob `else` steht. Neu geführt als Beobachtung `BEO-TDS/plan-nimmt-compilerzwang-an-when-mit-else-an` (Evidence dieses Slice). (2) *Der KDoc-Beleg-Zeiger folgt der Aussage mit:* wer einem Mitglied eine Messung zuordnet, zieht den Zeiger des Kopf-KDocs und der `when`-Funktionen nach; weiteres Auftreten in `BEO-TDS/aussagen-ueber-messung-hinaus` (Evidence dieses Slice). (3) *Eine Setzung steht an jeder Fundstelle der Aussage im selben Satz:* weiteres Auftreten in `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` (Evidence dieses Slice). Die Zähler folgen aus den Dateien, den Lese-Schritt macht die Welle-Closure. Keine benannte Spec-Lücke: die Lücke aus `slice-drop-index-if-exists` (Grenze (a) der Form D6) ist mit diesem Slice geschlossen.
- **Beobachtungs-Register (`../observations/`):** neu: [BEO-TDS/plan-nimmt-compilerzwang-an-when-mit-else-an](../observations/BEO-TDS/plan-nimmt-compilerzwang-an-when-mit-else-an/state.md). Fortgeschrieben mit `evidence/slice-statementkind-index-mitglieder.md`: [BEO-TDS/aussagen-ueber-messung-hinaus](../observations/BEO-TDS/aussagen-ueber-messung-hinaus/state.md), [BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen](../observations/BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen/state.md) und [BEO-TDS/katalog-elemente-grenzen-der-angleichung](../observations/BEO-TDS/katalog-elemente-grenzen-der-angleichung/state.md) (Punkt 13 (a): gebaut und verifiziert, Träger in `done/`; Punkt 13 (b) und Punkt 14 bleiben Trigger-Punkte).
- **Folge-Slices:** keiner angelegt. Das KDoc-Zeiger-Finding LOW-2 ist ein Eingriff in `src/` (Kommentar); Träger bis dahin das Register `BEO-TDS/aussagen-ueber-messung-hinaus`. Der nächste Slice der Welle ist [slice-abnahme-d-migrate](../done/slice-abnahme-d-migrate.md) (Reihenfolge, keine Voraussetzung).
- **Risiken aus §6:** alle 6 mit Ausgang eingetragen, in der Reihenfolge von §6 (eingetreten: 1; entfallen: 2, 3; weiter offen: 4 (`ENVCHANGE` 8/9, Setzung), 5 (`INCLUDE`, Filter, absteigende Spalte), 6 (fremde Backends)).
- **Verifikation:** [Verifikationsbericht](../../../reviews/2026-11-12-7d8bb877-verifikation-slice-statementkind-index-mitglieder.md) auf `7d8bb877`: konform; `make clean-build` Exit 0 (547 s, 1374 `PASSED`), `tools/matrix-zitate.sh` 352 Zitate ohne Fehlbefund, `make e2e` Exit 0 (8 Stufen, 324 s), `make gates FORCE=1` Exit 0 (35 s, Docker-Cache), `make flaeche` und `make docs-check` Exit 0, sieben von sieben Mutationen rot, d-migrate 1.12.0 gegen den Testserver (fünf Läufe, Exit 0, `IDENTICAL`). Läufe des Planners auf dem End-Stand (nur Dokumente und Register geändert): `make docs-check` (0 Befunde, 1374 Dateien), `make flaeche` und `make kopplung-zahlen` Exit 0; `make gates` Exit 0 in 13 s (Stempel und Docker-Cache, keine neue Test-Ausführung; der frische Beleg der Tests ist das Log von `make clean-build` des Verifiers).
- **Drei Paarungen:** Das Repo führt Wellen; die Paarungen prüft die Closure von [welle-testserver-manuell](../welle-testserver-manuell.md). Hier geprüft: *Anker*: kein Anker gesetzt (kein `liegt in`). *Folge-Slice*: keiner genannt, der als Datei bestehen müsste. *Register*: alle genannten Kennungen bestehen als Verzeichnis mit nicht leerem `evidence/` (die neue Beobachtung mit der Evidence dieses Slice).

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung.

**Vorgelagert — Sub-Area-Wahl prüfen:** Eine Sub-Area (`*`, Kürzel `TDS`); keine Aufteilung nötig.

**Vorgelagert — offene Beobachtungen sichten:** `BEO-TDS/katalog-elemente-grenzen-der-angleichung` (Herkunft, Punkt 13 (a)); `BEO-TDS/aussagen-ueber-messung-hinaus` (die KDoc der Mitglieder trägt nur die gemessenen Formen) und `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` (alle Fundstellen von „`UNKNOWN`“ für `CREATE INDEX` und `DROP INDEX` in Spezifikation und `docs/user/` nachziehen) sind mit Treffern gesichtet.

Alle berührten Sub-Areas GF.
