# Slice slice-bulk-fuer-eigene-backends: Massenladen für eigene Backends — die Fähigkeit am Backend öffentlich machen und den Import in Aufbau B messen

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** ohne Welle. Begründung (gelesen: [welle-qualitaet-grundlage](../welle-qualitaet-grundlage.md) §1 und §3, [welle-referenzabgleich-release](../welle-referenzabgleich-release.md)): `welle-qualitaet-grundlage` schließt, wenn jede der sieben Qualitätsanforderungen eine belegte Messung hat; dieser Slice ist in seinem Kern Funktionswachstum der öffentlichen Fläche ([TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung)), und die Welle verliert keine Closure-Bedingung, wenn er ausbleibt (der Import in Aufbau B ist aus der DoD von [slice-qa-performance-durchsatz-sitzungen](../done/slice-qa-performance-durchsatz-sitzungen.md) herausgenommen, nicht in die Closure der Welle verschoben). `welle-referenzabgleich-release` trägt Referenzabgleich und Veröffentlichung und passt nicht. Der Messteil (Punkt 3 der DoD) ergänzt das Messprotokoll der Welle, trägt aber keine Closure-Bedingung der Welle; er ist darum kein Grund, den Slice in die Welle aufzunehmen. Setzung des Planners; wer den Messteil als Teil der Welle führen will, entscheidet der Nutzer (§Fragen in §6).

**Bezug:** [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung) (Scope: Anbindung eines eigenen Backends), [TDS-QA-04](../../../../spec/lastenheft.md#tds-qa-04--performance) (Messteil: Import in Aufbau B), [ADR-0052](../../adr/0052-oeffentliche-flaeche-fuer-massenladen-eigener-backends.md) (`Accepted`, Option N1; trägt die Entscheidung dieses Slice), [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) (Nr. 4 und 5, teilweise ersetzt; Nr. 5 und 6 gelten für den neuen Weg als Setzung weiter), [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) (Einstieg und Einbettungstest), [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md) (Messläufe außerhalb der Gates), [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) (Testserver bindet nur die öffentliche Fläche), [ADR-0034](../../adr/0034-testserver-als-gradle-modul.md) (Testserver als Modul), [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) (Backend-Vertrag).

**Berührte Spec-Stellen:** [SPEC-059](../../../../spec/spezifikation.md#einstieg-des-integrators-vertrag-zu-spec-059) (Liste der Typnamen, Zeile `BackendSource`, Zusage) · [SPEC-035](../../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) (Massenladen) · [SPEC-036](../../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) (Form B: wer erkennt `INSERT BULK` und die Probe) · [SPEC-040](../../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge) (Beleg, falls ein Test benannt wird). Die Spezifikation ändert dieser Slice nur im Sinn der angenommenen [ADR-0052](../../adr/0052-oeffentliche-flaeche-fuer-massenladen-eigener-backends.md); ein Widerspruch zwischen Messung und Spezifikation geht als Frage an den Nutzer, nicht als stille Anpassung.

**Verantwortlich:** pt9912

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-17.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar.

**Ziel:** Ein `QueryBackend` des Integrators, das zusätzlich `BulkLoadingBackend` trägt, nimmt das Massenladen (`INSERT BULK`, Nachricht 0x07) an, so wie es [ADR-0052](../../adr/0052-oeffentliche-flaeche-fuer-massenladen-eigener-backends.md) (Option N1) zusagt; die zehn Typnamen stehen in [SPEC-059](../../../../spec/spezifikation.md#einstieg-des-integrators-vertrag-zu-spec-059), sind belegt, und der Import in Aufbau B des Lasttests ist gemessen — oder die Trennschärfe von B gegen C ist ehrlich als nicht tragend benannt.

**Die Fläche wächst — das ist hier entschieden, nicht offen.** Ob die öffentliche Fläche der Bibliothek wächst, entscheidet der Nutzer fallweise (Fläche nach [SPEC-059](../../../../spec/spezifikation.md#einstieg-des-integrators-vertrag-zu-spec-059): „nur, was man dafür braucht“). Für dieses Wachstum hat er am 2026-11-17 Option N1 gewählt und [ADR-0052](../../adr/0052-oeffentliche-flaeche-fuer-massenladen-eigener-backends.md) angenommen; der Slice setzt das um und entscheidet darüber nicht neu. Jedes weitere Wachstum der Fläche über die zehn Namen hinaus ist eine neue Entscheidung des Nutzers.

**Herkunft:** [slice-qa-performance-durchsatz-sitzungen](../done/slice-qa-performance-durchsatz-sitzungen.md) §7 („Ausdrücklich offen“: Import in Aufbau B, Slice zur Fläche nicht angelegt, Träger Nutzer; Nutzerentscheidungen 2026-11-17 „Code angleichen“ und „DoD teilen, B später“), Messprotokoll [performance-durchsatz-2026-11-17](../../../messungen/performance-durchsatz-2026-11-17.md) (Abschnitt „Stand der DoD-Zeile Import in Aufbau B“), Nachzieh-Liste in [ADR-0052](../../adr/0052-oeffentliche-flaeche-fuer-massenladen-eigener-backends.md) §Konsequenzen. Auftrag des Nutzers 2026-11-17: „adr-0052 anlegen“ (Slice zu [ADR-0052](../../adr/0052-oeffentliche-flaeche-fuer-massenladen-eigener-backends.md)).

**Nachzieh-Liste der ADR gegen diesen Slice** (Zuordnung, gelesen am 2026-11-17): (1) SPEC-059 → Punkt 1; (2) SPEC-035 und SPEC-036 → Punkt 1; (3) Sichtbarkeit und KDoc → Punkt 1; (4) Belege und Einbettungstest → Punkt 2; (5) Aufbau B im Testserver → Punkt 3; (6) Referenz-Workload 2 und 3.6 → Punkt 3; (7) Vermerk „teilweise ersetzt durch [ADR-0052](../../adr/0052-oeffentliche-flaeche-fuer-massenladen-eigener-backends.md)“ in der Indexzeile von [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) — **steht bereits** (gelesen: `docs/plan/adr/README.md`, Zeile von [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md)), kein Liefer-Punkt.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- **Die Optionen N0, N2, N3, N4** — der Nutzer hat N1 gewählt und angenommen; ein Wechsel bräche die Zusage und braucht eine Folge-ADR mit `Supersedes [ADR-0052](../../adr/0052-oeffentliche-flaeche-fuer-massenladen-eigener-backends.md)` (`AGENTS.md` §3.5). Der Slice ändert [ADR-0052](../../adr/0052-oeffentliche-flaeche-fuer-massenladen-eigener-backends.md) und [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) nicht.
- **Weitere Anfragen von `SqlBulkCopy` an ein fremdes Backend** (zum Beispiel Katalog-Abfragen des Clients über die Probe hinaus, die nur ein Katalog beantwortet) — akzeptiertes Negativ der ADR: das Backend antwortet sie über `execute` selbst; die Bibliothek baut dafür nichts. Zeigt die Messung eine solche Anfrage am Aufbau B, steht sie im Messprotokoll als Befund (Risiko in §6); ein Bau dafür wäre ein eigener Slice nach Entscheidung des Nutzers.
- **Der Heap bei 16 MiB je Nachricht** (`-Xmx256m` des Workloads) — Setzung des Workloads und akzeptiertes Negativ der ADR (Heap mindestens die Größe der Nachricht); der Slice misst die Grenze nicht aus und ändert weder die Obergrenze (`SPEC-007`) noch die Heap-Angabe.
- **Gegendruck innerhalb einer Nachricht und Atomarität über mehrere Nachrichten** — Re-Evaluierungs-Trigger der ADR, keine Zusage; ein Bedarf braucht eine Folge-ADR.
- **Durchsatz-Grenzwerte im Lastenheft** — Gegenstand von [slice-qa-performance-grenzwerte](../open/slice-qa-performance-grenzwerte.md); die Messzeilen dieses Slice sind Einzelmessungen und kein Grenzwert.
- **Aufbau A in der Staffel der Sitzungen** und **`punkt-import` in Aufbau B** — nicht im Auftrag der ADR (sie nennt B, C und A-Import des Plans 3.6); `punkt-import` in B wäre mit dem fertigen Import technisch möglich (`tools/lasttest.sh` überspringt sie heute), erweitert aber die Messreihe; Entscheidung des Nutzers (siehe §6, Frage 2).
- **Der Bau eines Versionssprungs und eine Veröffentlichung** — [SPEC-059](../../../../spec/spezifikation.md#einstieg-des-integrators-vertrag-zu-spec-059) sagt: `y` der Version `0.y.z` steigt bei jeder Änderung der Fläche; daraus folgt, dass die nächste Veröffentlichung eine neue `y`-Version braucht (**Folgerung** aus dem Wortlaut, nicht gebaut, nicht belegt). Der Slice benennt sie in der Closure-Notiz und ändert weder Versionsnummer noch Release-Dateien ([ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md), [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md)).
- **Ein Gegenlauf mit d-migrate gegen ein eigenes Backend** — ungemessen und kein Verbraucher im Repo; [ADR-0052](../../adr/0052-oeffentliche-flaeche-fuer-massenladen-eigener-backends.md) hält das Simulations-Backend und d-migrate unberührt, belegt durch `make simulations-integration` (Punkt 2).
- **Mitglieder-Sensor für `make flaeche`** — der Sensor vergleicht Typnamen, keine Mitglieder (`BEO-TDS/oeffentliche-flaeche-mitglieder-ohne-sensor`, beim Nutzer); der Slice erweitert ihn nicht.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit.

- [ ] **Fläche** (Punkt 1; [ADR-0052](../../adr/0052-oeffentliche-flaeche-fuer-massenladen-eigener-backends.md) Entscheidung 1 bis 3, Nachzieh-Liste 1 bis 3): `BulkLoadingBackend` (`bulkTarget`, `bulkProbe`, `load`), `BulkTarget`, `BulkTargetResult` (`Ready`, `Rejected`), `BulkProbeColumn`, `BulkProbeResult` (`Ready`, `Rejected`) und `BulkRows` sind `public` mit KDoc unter `explicitApi` strict; die KDoc nennt die Zusagen der ADR (Erkennung von `INSERT BULK` und der Probe liegt beim Backend, die Form der Texte ist die von SPEC-036 Form B nur für den Referenzclient gemessen, Nachricht als Ganzes, IGNORE-Bit, Fehler als `TDS_BACKEND_ERROR`, keine Transaktion) und streicht „nicht öffentlich“; [SPEC-059](../../../../spec/spezifikation.md#einstieg-des-integrators-vertrag-zu-spec-059) führt die Liste um die zehn Namen, die Zeile `BackendSource` (ein `Custom`-Backend mit der Fähigkeit) und die Zusage samt Eintrag im Änderungsprotokoll der Mitglieder; [SPEC-035](../../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) und [SPEC-036](../../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) sagen, dass die Fähigkeit auch ein fremdes Backend tragen darf und wer Form B erkennt; der Versionssprung nach `0.y.z` steht als Folgerung, nicht gebaut; Nutzer-Doku `docs/user/` (alle Dateien gegen den Stand nach dem Slice gelesen, `AGENTS.md` §5 Regel 5) nennt den Weg für ein eigenes Backend im Ist-Zustand, ohne Planung und Chronik. Ob `SimulationBulk` bleibt oder entfällt (die Klasse `SimulationBackend` darf die Schnittstelle jetzt selbst tragen), entscheidet der Bau ([ADR-0052](../../adr/0052-oeffentliche-flaeche-fuer-massenladen-eigener-backends.md) Entscheidung 2: Folgerung, nicht festgelegt); die Auswahl `backend as? BulkLoadingBackend` im Kern bleibt.
- [ ] **Belege** (Punkt 2; [ADR-0052](../../adr/0052-oeffentliche-flaeche-fuer-massenladen-eigener-backends.md) Nachzieh-Liste 4, Abschnitt „Messung, die nötig ist“ 1 und 2): jeder der zehn Namen hat einen Beleg in `src/integrator` oder `testserver/kotlin` (`make flaeche` Exit 0 ohne Befund); ein Einbettungstest im Source-Set `integrator` ohne `internal`-Namen startet einen Server mit einem eigenen Backend, das `BulkLoadingBackend` trägt, und prüft über Rohbytes: die Probe der Zieltabelle, `INSERT BULK`, die Nachricht 0x07 mit Zahl und Werten der Zeilen, den Fehler des Backends (`TDS_BACKEND_ERROR`, Nummer 50004), das IGNORE-Bit (kein Aufruf von `load`), eine zweite Nachricht auf derselben Verbindung; dazu ein Regressionsfall mit einem Backend **ohne** die Fähigkeit (Paket 0x07 ergibt den Fehler, `INSERT BULK` ist ein Text für `execute`). Ein E2E-Beleg mit dem Referenzclient (`SqlBulkCopy`) gegen das eigene Backend läuft als Szenario im E2E-Durchgang (Stufe 8 oder Stufe 9; die Wahl trifft der Bau und nennt sie im Bericht). Die Nachricht über 16 MiB (die Verbindung schließt) ist ein Fall des Einbettungstests, soweit der Heap des Testprozesses sie trägt; sonst steht sie als „nicht lieferbar“ mit Begründung. `make simulations-integration` und `make e2e` sind unverändert grün (Nachweis, dass das Simulations-Backend und d-migrate unberührt sind, Entscheidung 4 der ADR).
- [ ] **Aufbau B und Messung** (Punkt 3; [ADR-0052](../../adr/0052-oeffentliche-flaeche-fuer-massenladen-eigener-backends.md) Nachzieh-Liste 5 und 6, Abschnitt „Messung, die nötig ist“ 3): `ZeilenBackend` in `LasttestBackends.kt` (oder ein Nachbar, die Plan-Tabelle §3 nennt die Datei) trägt `BulkLoadingBackend` mit einem zählenden `load` ohne Speicherung (S6 des Workloads); die Prüfung in `tools/lasttest.sh`, `tools/lasttest-selbsttest.sh` und `referenztest-dotnet/Lasttest/LasttestTests.cs`, dass der Import in B abgewiesen wird, ist durch die Zählprüfung des Imports ersetzt (wie die Prüfung der vollständigen Importe in Aufbau C), und `make lasttest` fährt den Import in Aufbau B; [referenz-workload.md](../../referenz-workload.md) Abschnitt 2 und 3.6 sind berichtigt (B misst den Import; der Befund „Import entfällt“ und „Eine öffentliche Fläche … entsteht nicht“ stehen nicht mehr); `tools/e2e.sh` Stufe 9 nennt den Fehler des Massenladens gegen ein eigenes Backend nicht mehr als Ergebnis. Die Nachmessung des Imports in B steht als Messzeilen im Anschluss an das Protokoll [performance-durchsatz-2026-11-17](../../../messungen/performance-durchsatz-2026-11-17.md) (neues Messprotokoll oder Fortschreibung, die Wahl trifft der Bau): Import in Aufbau B, C und A-Import, Stufen und Wiederholungen nach dem Plan 3.6 und S4 des Workloads, Streuung nach S11, Bedingungen (Host, Last, Fremdlast-Probe) wie im Protokoll; **C wird im selben Zustand des Hosts neu gemessen, die Zahlen vom 2026-11-17 sind kein Vergleichswert für B**. Das Protokoll sagt ehrlich, was B im Import von C trennt (Trennschärfe gegen C, akzeptiertes Negativ der ADR: fällt B nicht unterscheidbar aus, ist das ein Negativ der Messung und kein Rückbau); das Protokoll belegt „Nicht belegt“-Punkte als solche und nennt jede Zahl mit ihrem Erzeuger im Repo (Kette: Rohzeilen, `make lasttest-tabellen`, Probe der Umgebung; die Probe außerhalb des Repos ist benannt wie im Vorgängerprotokoll).
- [ ] `make docs-check`, `make flaeche`, `make kopplung-zahlen`, `make test`, `make detekt`, `make gates` Exit 0 (Art der Änderung: Doku/Spec und `src/` oder `testserver/`; engste Testklasse, Mutation je Zusage über enge Testklassen, `make spec-abdeckung` einmal bei neuen oder geänderten `@spec`-Tags, `make matrix-zitate` nur bei geänderten Zitaten; `make clean-build` und `make e2e` nur im Verifier, auf dem End-Stand; `harness/README.md` §Minimal agent workflow).
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor (`.harness/skills/reviewer.md`), kein Self-Review. Enthält die Nachbesserung Code oder löst ein Merge von `main` Konflikte im Code, folgt ein Re-Review in frischem Kontext; sonst Vermerk „Nachbesserung ohne zweiten Review“ in §7.
- [ ] Doku-Update: `docs/user/` (Punkt 1), `harness/README.md` (Zeile `make lasttest`, falls die Aufbauten dort genannt sind), `SPEC-059`, `SPEC-035`, `SPEC-036`, Referenz-Workload, Messprotokoll.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag; die Roadmap-Zeile ist fortgeschrieben (Zustand statt Chronik).
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben; keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — Pfad-Kandidaten für §8; geprüft ist das Vorhandensein der Dateien am 2026-11-17 im Zweig `planung-flaeche-adr0052` (Basis `81a67abd`), nicht ihr Inhalt im Einzelnen. Neue Dateien sind als „neu“ gekennzeichnet.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/port/BulkLoading.kt` | update | Sichtbarkeit `internal` → `public`, KDoc nach Zusage der ADR (Punkt 1); heute sagt die KDoc „nicht öffentlich“ |
| `src/main/kotlin/io/github/pt9912/tdswire/runtime/SimulationBulk.kt` | update oder entfällt | [ADR-0052](../../adr/0052-oeffentliche-flaeche-fuer-massenladen-eigener-backends.md) Entscheidung 2: der Wrapper ist für das Simulations-Backend nicht mehr nötig, sobald die Klasse die Schnittstelle selbst trägt (Folgerung); der Bau entscheidet |
| `src/main/kotlin/io/github/pt9912/tdswire/runtime/ConnectionWiring.kt` | update, falls `SimulationBulk` entfällt | Zeile 196 ruft `SimulationBulk.of(backend)` |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/BulkDispatch.kt` | prüfen, ändern nur wenn nötig | die Auswahl `backend as? BulkLoadingBackend` (Zeile 44) bleibt ([ADR-0052](../../adr/0052-oeffentliche-flaeche-fuer-massenladen-eigener-backends.md) Entscheidung 2); die KDoc sagt „Fähigkeit nicht öffentlich“ und wird geprüft |
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/SimulationBackend.kt`, `…/simulation/StatementPlanner.kt` | update, falls die Klasse die Schnittstelle selbst trägt | KDoc-Verweise auf `BulkLoadingBackend` (Zeilen 113 und 87); `BulkCopyForms.probe` bleibt `internal` und wird vom Simulations-Backend gerufen |
| `src/integrator/kotlin/io/github/pt9912/tdswire/integrator/MassenladenEmbeddingTest.kt` | update | Nachbar: Tabelle ohne `onInsertBatch`, Massenladen des Simulations-Backends; die Fälle des eigenen Backends kommen in diese oder eine neue Datei |
| `src/integrator/kotlin/io/github/pt9912/tdswire/integrator/FlaecheEmbeddingTest.kt` | prüfen | Beleg der Namen der Fläche; ob die zehn Namen hier oder in einem neuen Test belegt sind, entscheidet der Bau |
| `src/integrator/kotlin/io/github/pt9912/tdswire/integrator/` (Datei für den Fall des eigenen Backends) | neu | Einbettungstest über Rohbytes für Punkt 2; Name vom Bau; Plan-Tabelle gegen den Diff am Ende prüfen (`BEO-TDS/plan-tabelle-nennt-neue-datei-nicht`, 27 Dateien, verkörpert) |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/dispatch/BulkDispatchTest.kt`, `…/SpanOutcomesBulkTest.kt` | prüfen | bestehende Tests der Schnittstelle (Sichtbarkeit ändert sich, Verhalten nicht) |
| `testserver/kotlin/io/github/pt9912/tdswire/testserver/LasttestBackends.kt` | update | `ZeilenBackend` (Aufbau B) trägt `BulkLoadingBackend` mit zählendem `load` ohne Speicherung; die KDoc „Massenladen nimmt ein eigenes Backend nicht an“ wird berichtigt; Zählzeile wie in `referenzTabelle` |
| `testserver/kotlin/io/github/pt9912/tdswire/testserver/LasttestServer.kt` | prüfen | wählt `Aufbau.B -> BackendSource.Custom(ZeilenBackend(rows, seed))` (Zeile 37) |
| `tools/lasttest.sh` | update | Zeilen 236 und 258: der Import in B wird heute abgewiesen und darauf geprüft; `punkt-import` in B bleibt übersprungen, bis der Nutzer anders entscheidet |
| `tools/lasttest-selbsttest.sh` | update | Prüfung „Import nicht abgewiesen“ (Zeilen 133, 171, 277) und die Zahl der Fälle und Mutationen (Zeile 289) |
| `referenztest-dotnet/Lasttest/LasttestTests.cs` | update | Zeile 257: der Import in B erwartet heute den Fehler `InvalidOperationException`; er lädt künftig |
| `tools/e2e.sh` | update | Stufe 9 (Zeile 67): „das Massenladen gegen ein eigenes Backend endet mit einem Fehler des Clients (festgehalten …)“ |
| `tools/flaeche-pruefung.sh`, `tools/flaeche-selbsttest.sh` | nicht ändern | der Sensor vergleicht Typnamen und deckt die zehn Namen ohne Änderung; ändert sich seine Zählung (geschachtelte Fälle als `Eltern.Name`), steht das im Bericht |
| `spec/spezifikation.md` (`SPEC-059`, `SPEC-035`, `SPEC-036`, ggf. `SPEC-040`) | update | Punkt 1; keine Meilensteine und Slices in `spec/architecture.md` (`AGENTS.md` §3.4); ob `spec/architecture.md` die Fähigkeit nennt, wird gelesen und nur ohne ADR- und Slice-Bezug fortgeschrieben |
| `docs/user/einbettung-und-betrieb.md`, `docs/user/referenz-konfiguration.md`, `docs/user/testserver-manuell.md`, `docs/user/referenz-ereignisse.md`, übrige Dateien unter `docs/user/` | update nach Lesen | Punkt 1: alle Dateien gegen den Stand nach dem Slice lesen (Fundstellen zu „Massenladen“ gelesen in den vier genannten); der Weg für ein eigenes Backend im Ist-Zustand |
| `docs/plan/referenz-workload.md` | update | Abschnitt 2 (Zeile zum Schreiben) und 3.6 (Tabelle der Aufbauten, Befund „Import entfällt“) berichtigen; das Dokument ist eine Sammlung von Setzungen, Messzahlen stehen nur im Protokoll |
| `docs/messungen/performance-durchsatz-2026-11-17.md` oder neues Protokoll `docs/messungen/performance-import-b-<Datum>.md` | update oder neu | Messzeilen des Imports in B, C, A-Import mit Erzeuger, Bedingungen und Grenzen; Abschnitt „Stand der DoD-Zeile Import in Aufbau B“ im Vorgänger ist ein Zeitdokument: bei einem neuen Protokoll steht dort nur ein Zeiger |
| `docs/plan/planning/in-progress/roadmap.md` | update | Zeile der „Offenen Wellen“: Zustand des Slice (siehe Closure); keine Chronik |
| `harness/README.md` | prüfen, ändern falls berührt | Zeilen `make lasttest`, `make flaeche` nennen Aufbauten und Zahlen; `make kopplung-zahlen` hält Heap und Text von `gross` in sechs Orten gleich |
| `docs/plan/adr/0052-…md`, `docs/plan/adr/0033-…md` | **nicht ändern** | `Accepted` (`AGENTS.md` §3.5); eine Abweichung ist eine Folge-ADR |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf. **Ablauf:** (1) Fläche und Einbettungstest (Punkte 1 und 2) zuerst, `make flaeche` und die engste Testklasse; (2) Aufbau B und das Werkzeug; (3) Review und Verifikation; (4) Messlauf erst danach auf einem freien Host (nicht parallel zu einem anderen Messslice), damit ein Messlauf nicht auf einem Stand läuft, der noch geändert wird. Der Messlauf ist **nicht** Teil von `make gates` und `make e2e` ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)); `make lasttest` ist das Werkzeug, `make lasttest-tabellen` setzt die Tabellen.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`open` → `next` → `in-progress`):

1. **Nutzerzeichen**, den Slice zu starten (die Entscheidung für die Fläche liegt mit [ADR-0052](../../adr/0052-oeffentliche-flaeche-fuer-massenladen-eigener-backends.md) vor; das Zeichen betrifft den Zeitpunkt und die Reihenfolge zu [slice-qa-performance-rueckstau](../done/slice-qa-performance-rueckstau.md) und zu [slice-qa-performance-heap-abschluss](../open/slice-qa-performance-heap-abschluss.md); das WIP-Limit gilt).
2. **Ein Verantwortlicher** (`Verantwortlich:` ist bis dahin `—`).
3. **Der Host ist frei für den Messteil** (Punkt 3): Der Messlauf läuft **nicht parallel** zu `make lasttest`-Läufen und zu Messslices der Welle `welle-qualitaet-grundlage` (zum Beispiel `slice-qa-performance-rueckstau`), und nicht parallel zu einem Messslice am echten SQL Server (`slice-mehrfach-rpc-messluecken`): Last verfälscht beide Messungen (im Vorgängerprotokoll tragen 18 von 48 Tabellenzeilen den Vermerk „gestört“). Der Bau der Punkte 1 und 2 braucht keinen freien Host und darf vor dem Messteil abgeschlossen sein; der Start verlangt den freien Host für den Messteil, nicht für den Bau.

**Rückführungen — vorab benennen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2, oder die Messung (Punkt 3) braucht ein Werkzeug über `make lasttest` hinaus. Bevorzugter Schnitt in diesem Fall: Punkte 1 und 2 (Fläche und Belege) als Slice, Punkt 3 (Aufbau B und Messung) als eigener Folge-Slice; der Slice-Name `slice-import-in-aufbau-b-messen` ist ein Vorschlag.
- `in-progress` → `open` (blockiert): der Host ist für den Messteil nicht frei und der Nutzer will nicht ohne Messung schließen; oder ein Befund zeigt, dass `SqlBulkCopy` gegen ein eigenes Backend mehr Anfragen stellt, als die Fläche trägt (siehe §6): dann Entscheidung des Nutzers, bevor weitergebaut wird.

## 5. Closure-Trigger

Alle Punkte der DoD in §2 erledigt (Verifier bestätigt jeden Liefer-Punkt), `make gates` und `make docs-check` Exit 0, die Verifikation fuhr `make clean-build` und `make e2e` auf dem End-Stand, das Messprotokoll trägt die Messzeilen mit Datum, Host, Bedingung, Erzeuger und Grenzen (oder sagt ausdrücklich, dass der Messteil nicht lief, mit Träger), jede Abweichung zwischen Messung und Zusage steht als Frage an den Nutzer in §7, und die Closure-Notiz mit Steering-Loop-Lerneintrag ist geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **Trennschärfe von B gegen C im Import** (akzeptiertes Negativ der ADR): Aufbau B gibt die Zeilen als `BulkRows` an ein zählendes `load`, Aufbau C überführt sie in Maps für `onInsertBatch`; wie viel der Unterschied macht, ist ungemessen, und der Client ist in beiden Aufbauten nicht abgetrennt. B kann von C nicht unterscheidbar ausfallen. — **Ausgang:** offen bis zur Closure; fällt B nicht unterscheidbar aus, steht das als Aussage im Protokoll (kein Rückbau, ein Rückbau bräche die Zusage und braucht eine Folge-ADR).
- **`SqlBulkCopy` stellt gegen ein fremdes Backend weitere Anfragen** als die Probe (Katalog-Abfragen des Clients), die `ZeilenBackend` nicht beantwortet; dann scheitert der Import in B bereits vor der ersten Nachricht (die Messung vom 2026-11-15 zeigt nur den Fehler `Cannot access destination table`, nicht welche Anfrage fehlschlug). — **Ausgang:** offen bis zur Closure; das Backend beantwortet, was nötig und im Backend billig ist, und der Befund steht im Protokoll; verlangt die Antwort Arbeit in der Bibliothek, geht die Frage an den Nutzer (Rückführung nach §4).
- **Der Autor baut die Erkennung von `INSERT BULK` und der Probe selbst** (Entscheidung 3 der ADR; `BulkCopyForms.probe` bleibt `internal`): das `ZeilenBackend` des Testservers muss die Texte der Form B erkennen, ohne `internal`-Namen zu sehen, und die Form der Texte ist nur für den Referenzclient gemessen. Der Aufwand ist eine Mehrkost der Wahl N1 und trifft den Testserver als ersten Verbraucher. — **Ausgang:** offen bis zur Closure; die Texte stammen aus [SPEC-036](../../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) Form B und den Aufzeichnungen M-33 und M-34; ein Befund für die KDoc der Fläche (Wortlaut der Texte) wird dort nachgezogen.
- **`ErrorCode` ist `internal`** (gelesen: `protocol/error/ErrorCode.kt`), `BulkTargetResult.Rejected` und `BulkProbeResult.Rejected` tragen den Namen als `String` („ein Name aus `ErrorCode`“). Ein Integrator kennt die Namen nicht; die Zusage sagt nicht, welche gelten. — **Ausgang:** offen bis zur Closure; die KDoc nennt die zugesagten Namen (oder die Zusage entfällt, mit Eintrag im Bericht); eine neue Fläche für `ErrorCode` ist kein Teil dieses Slice und ginge als Frage an den Nutzer.
- **Das Zählen der Mitglieder hält kein Sensor** (`BEO-TDS/oeffentliche-flaeche-mitglieder-ohne-sensor`): die Methoden von `BulkLoadingBackend` und die Felder der Hilfstypen sind Fläche und stehen nur im Einbettungstest und im Änderungsprotokoll von `SPEC-059`. — **Ausgang:** weiter offen: → `BEO-TDS/oeffentliche-flaeche-mitglieder-ohne-sensor` im Register (Entscheidung beim Nutzer).
- **Die Messung läuft unter Fremdlast** wie das Vorgängerprotokoll (Host mit anderen Läufen; `-Xmx256m`, `--cpus 2`). — **Ausgang:** offen bis zur Closure; Fremdlast-Probe je Zelle, Kennzeichnung „gestört“ nach den Regeln des Vorgängers, Einzelmessung, kein Grenzwert.
- **Der Messteil hängt am freien Host** (Start-Bedingung 3) und an den Läufen anderer Messslices; die DoD hängt damit an einer Bedingung außerhalb des Slice (`BEO-TDS/dod-haengt-an-offenem-slice-ohne-rueckfuehrung`, 4×, offen beim Nutzer). — **Ausgang:** offen bis zur Closure; die Rückführung steht in §4 (Messteil als eigener Folge-Slice, wenn der Host nicht frei wird).
- **Ein Backend-Autor, der die Fläche braucht, ist nur durch den Lasttest belegt** (kein Einbettender im Repo). — **Ausgang:** weiter offen; benannt in [ADR-0052](../../adr/0052-oeffentliche-flaeche-fuer-massenladen-eigener-backends.md) Kontext („Nachweis eines Bedarfs“); kein Gegenstand dieses Slice.
- **Größe:** drei Liefer-Punkte, davon einer mit Messlauf auf freiem Host. — **Ausgang:** offen bis zur Closure; Rückführung nach §4.

**Fragen an den Nutzer (nicht Teil der DoD):**

1. Ob der Messteil Teil der Welle `welle-qualitaet-grundlage` geführt wird (Setzung hier: ohne Welle).
2. Ob `punkt-import` in Aufbau B (Punktabfrage unter Import-Last) mitgemessen wird; heute übersprungen.
3. Ob der Messteil (Punkt 3) im selben Slice bleibt oder nach Abschluss von Punkt 1 und 2 als eigener Slice geschnitten wird (Rückführung §4).
4. Ob die nächste Veröffentlichung die neue `y`-Version trägt (Folgerung aus `SPEC-059`; Entscheidung und Bau sind nicht Teil dieses Slice).

## 7. Closure-Notiz

Wird bei der Closure gefüllt.

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung.

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register (`../observations/BEO-TDS/`, 178 Verzeichnisse) gegen die Verzeichnisliste gelesen; Treffer: `oeffentliche-flaeche-mitglieder-ohne-sensor` (3×, verkörpert, die Mitglieder der neuen Typen sind betroffen), `plan-tabelle-nennt-neue-datei-nicht` (verkörpert; die Plan-Tabelle in §3 nennt neue Dateien mit „neu“), `dod-haengt-an-offenem-slice-ohne-rueckfuehrung` (4×, offen beim Nutzer; Rückführung des Messteils in §4 und §6), `aussagen-ueber-messung-hinaus` (verkörpert; die Aussagen zur Trennschärfe tragen nur so weit wie die Messzeile, `AGENTS.md` §3.8), `messung-am-echten-sql-server-fehlt` (nicht Gegenstand: keine Messung am echten SQL Server). `oeffentliche-flaeche-der-telemetrie-ohne-entscheidung` ist gestrichen (Ursache entfallen) und trifft hier nicht. Zähler-Stände stehen in den Dateien des Registers, nicht hier.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
