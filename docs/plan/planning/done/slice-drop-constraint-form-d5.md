# Slice slice-drop-constraint-form-d5: `ALTER TABLE … DROP CONSTRAINT [IF EXISTS]` als Form D5 erkennen, am echten SQL Server gemessen

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-testserver-manuell

**Bezug:** [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), [ADR-0046](../../adr/0046-oeffentliche-flaeche-der-katalog-elemente.md)

**Berührte Spec-Stellen:** SPEC-036 (erkannte Anweisungsformen, Form D, Absatz Grenzen, Form D5) · SPEC-035 (Katalog der Elemente) · SPEC-031 (Abschlussbefehl `0x00d8` für `DROP CONSTRAINT`) · Abschnitt 4 (Fehlertabelle, Positivliste der Backends) · `TDS-FA-11.a` (Ablaufpunkt 9, Strukturänderung)

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912 (Entwurf in der Planner-Rolle, Closure von [slice-katalog-indizes-defaults-fremdschluessel](../done/slice-katalog-indizes-defaults-fremdschluessel.md)). **Datum:** 2026-11-11.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung**; die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks.

**Ziel:** Ein zweites `schema migrate --execute` von d-migrate mit demselben Soll, das eine Prüfbedingung führt, endet gegen den Testserver mit Exit 0 wie gegen den echten SQL Server. Heute sendet d-migrate für eine vorhandene Prüfbedingung bei jedem Lauf `ALTER TABLE … DROP CONSTRAINT IF EXISTS [ck_…]` und danach `WITH CHECK ADD CONSTRAINT`; die Form `DROP CONSTRAINT` ist nicht erkannt, der Lauf endet mit Exit 5 (`Unsupported SQL.`, Messprotokoll [Zeile e5](../../../messungen/katalog-indizes-defaults-fremdschluessel-d-migrate-2026-11-09.md), im Verifikationsbericht zu [slice-katalog-indizes-defaults-fremdschluessel](../done/slice-katalog-indizes-defaults-fremdschluessel.md) reproduziert). Maßstab des Nutzers: die Spezifikation ist Zielbild, der echte SQL Server die Referenz.

**Herkunft und Vorbehalt.** Punkt 2 und Punkt 3 des Registers [katalog-elemente-grenzen-der-angleichung](../observations/BEO-TDS/katalog-elemente-grenzen-der-angleichung/state.md). Anlage als Folge-Slice: Standardantwort auf die Empfehlung des Verifiers, **umkehrbar** (Entscheid des Nutzers steht aus; der Planner hat sie als Standard gesetzt).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Der Text mit `DECLARE @df … EXEC sp_executesql` für einen Standardwert an einer vorhandenen Spalte (Punkt 1 des Registers) — größer (Variablen und dynamisches SQL im Scanner) und nur nötig, wenn der Validator den Fall verlangt: [slice-declare-df-standardwert-vorhandene-spalte](../open/slice-declare-df-standardwert-vorhandene-spalte.md).
- Die Abnahme mit d-migrate über alle Funktionen — [slice-abnahme-d-migrate](../open/slice-abnahme-d-migrate.md); dieser Slice liegt davor, weil der Validator bei der Idempotenzprobe Punkt 2 sofort trifft.
- Referentielle Integrität im Bestand und Nutzung von Indizes — der Katalog beschreibt, der Testserver prüft nichts.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit.

Liefer-Punkte (drei):

- [x] **Messung am echten Server** ([TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)): `ALTER TABLE … DROP CONSTRAINT [IF EXISTS]` am echten SQL Server (Developer-Image, gepinnter Digest, EULA bestätigt, nur lokal): Antwortart und Tokenfolge für Prüfbedingung, Eindeutigkeit, Fremdschlüssel und Standardwert; Fehlerfälle mit und ohne `IF EXISTS`, mit und ohne Treffer. Dazu die offene Frage von Punkt 3 des Registers: liest d-migrate ein `CREATE UNIQUE INDEX` auch am echten Server als Eindeutigkeit zurück (dann ist `POST_EXECUTE_DRIFT` mit `unique: true` im Index keine Abweichung vom echten Server)? Das Protokoll steht in `docs/messungen/` mit Rechner, Last und Digest.
- [x] **Bau** ([TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend)): Form D5 in Parser, Planer und Katalog (entfernt die benannte Prüfbedingung, Eindeutigkeit, den Fremdschlüssel oder Standardwert aus der Tabelle, meldet die gemessene Antwort, geht nach der Entscheidung von [ADR-0046](../../adr/0046-oeffentliche-flaeche-der-katalog-elemente.md) an den Integrator); Tests mit Handvektoren und den Zeilen der Messung; ein zweites `schema migrate --execute` von d-migrate gegen den Testserver endet mit Exit 0 und `IDENTICAL`; Mutationen je Zusage.
- [x] **Fortschreibung** ([TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate)): `SPEC-036` (Form D5, Grenze entfällt), `docs/user/` (Grenzen in `testserver-manuell.md` und `einbettung-und-betrieb.md`), das Register [katalog-elemente-grenzen-der-angleichung](../observations/BEO-TDS/katalog-elemente-grenzen-der-angleichung/state.md) (Punkt 2 und, je nach Messung, Punkt 3).

Nachweis-Pflichten für alle Punkte:

- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Haken der Liefer-Punkte setzt der Planner nach der Verifikation, nicht der Implementer (`BEO-TDS/haken-vor-verifikation-gesetzt`).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben oder „keine Beobachtung angefallen“ in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — Pfad-Kandidaten für §8; der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/Statement.kt`, `StructureParts.kt`, `StructureChecks.kt`, `StructurePlanner.kt`, `StatementPlanner.kt`, `SimulationChanges.kt` | update | Form D5: Erkennung (`Statement.DropConstraint`), Planung gegen den Katalog (Bedingung entfernen, `IF EXISTS` ohne Treffer, Name einer anderen Tabelle, unbekannte Tabelle), die zwei neuen Namen in `SimCodes` |
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/SimulationBackend.kt`, `src/main/kotlin/io/github/pt9912/tdswire/domain/StatementKind.kt` | update (nur KDoc) | die öffentliche KDoc nennt `DROP CONSTRAINT` als Form, die den Callback ruft, und als Art `ALTER_TABLE` (Befund F-1 des Reviews); keine Änderung des Verhaltens und der Fläche |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/error/ErrorCode.kt`, `ErrorMapper.kt` | update | `TDS_SIM_NOT_A_CONSTRAINT` (3728) und `TDS_SIM_CONSTRAINT_OTHER_TABLE` (3733) als Codes und in der Positivliste der Backends; intern, keine Änderung der öffentlichen Fläche |
| `src/main/kotlin/.../simulation/SimModel.kt`, `SimElements.kt`, `ConstraintParts.kt` | unverändert | das Modell führt keinen Namen für Standardwert und Schlüssel; die öffentliche Fläche wächst nicht (Grenze in `SPEC-036`, Register Punkt 2) |
| `src/test/kotlin/.../simulation/SimulationBackendDropConstraintTest.kt` (neu), `DMigrateWire.kt`, `src/test/kotlin/.../protocol/error/ErrorMapperTest.kt`, `src/integrator/kotlin/.../DropConstraintEmbeddingTest.kt` | neu / update | Handvektoren (Texte von d-migrate am Draht, Zeilen der Messung), Fehlercodes, Einbettungstest auf den Rohbytes |
| `referenztest-dotnet/Vergleich/MessungBulkTests.cs` (Szenario `struktur-drop-constraint`), `referenztest-dotnet/Mitschnitt/MessungBulkBelegTests.cs`, `referenztest-dotnet/Mitschnitt/messung/bulk-merge-katalog/{2022,2025}/struktur-drop-constraint.messung` | update / neu | Messung der Antworten des echten Servers auf `DROP CONSTRAINT` und ihre Belege ohne Instanz |
| `tools/vergleichslauf-d.sh` (Lauf `schema-2022`, `schema-2025`, Funktion `schemalauf`), `tools/mk/vergleichslauf-d.mk`, `harness/README.md`, `referenztest-dotnet/Mitschnitt/messung/jdbc-d-migrate/{2022,2025}.schema.aufnahme.txt` | update / neu | d-migrate (`schema migrate`, `compare`, `reverse`) gegen den echten Server mit Mitschnitt der Antworten; beantwortet die Frage zu Punkt 3 |
| `docs/messungen/drop-constraint-d-migrate-2026-11-11.md` (neu), `docs/messungen/referenzabgleich.md` | neu / update | Messprotokoll der Form am echten Server und Antwort auf die Frage zu Punkt 3 |
| `spec/spezifikation.md` (`SPEC-036` Form D5 und Grenzen, Fehlertabelle, Ablaufpunkt 9, Änderungsprotokoll), `docs/user/testserver-manuell.md`, `docs/user/einbettung-und-betrieb.md`, `docs/user/referenz-konfiguration.md` | update | Zusage und Grenzen; `docs/user/README.md` und `referenz-ereignisse.md` gelesen, unverändert |
| `docs/plan/planning/observations/BEO-TDS/katalog-elemente-grenzen-der-angleichung/` (`observation.md`, `state.md`) | update | Zustand der Punkte 2 und 3, neuer Punkt 9 (`DROP INDEX IF EXISTS`) |
| `docs/plan/planning/done/slice-katalog-indizes-defaults-fremdschluessel.md`, `open/slice-abnahme-d-migrate.md`, `open/slice-declare-df-standardwert-vorhandene-spalte.md`, `open/slice-drop-index-if-exists.md`, `welle-testserver-manuell.md`, `in-progress/roadmap.md` | update | Zeiger auf den Slice (`open/` → `in-progress/`) und Zustandsfeld der Roadmap; keine inhaltliche Änderung der Pläne |
| `docs/plan/spec-abdeckung.md` | update | `make spec-abdeckung` einmal nach den Korrekturen des Reviews (Matrix führte `DropConstraintEmbeddingTest` nicht) |
| `docs/reviews/2026-11-11-e5c345aa-review-slice-drop-constraint-form-d5.md` (neu), `docs/reviews/2026-11-11-8b231cef-verifikation-slice-drop-constraint-form-d5.md` (neu) | neu | Review (0 HIGH, 4 MEDIUM, 2 LOW, 3 INFO) und Verifikationsbericht des Slice |

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-katalog-indizes-defaults-fremdschluessel](../done/slice-katalog-indizes-defaults-fremdschluessel.md) liegt in `done/`; die EULA für das gepinnte Developer-Image ist vom Nutzer für diesen Lauf bestätigt; ein Verantwortlicher ist eingetragen. Der Slice steht vor [slice-abnahme-d-migrate](../open/slice-abnahme-d-migrate.md).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): die Messung zeigt, dass die vier Elementarten verschiedene Antworten geben; Schnittvorschlag: Prüfbedingung | übrige.
- `in-progress` → `open` (blockiert): der Nutzer bestätigt die EULA nicht; dann bleibt die Form Setzung, und der Nutzer entscheidet, ob ohne Messung gebaut wird.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln.

DoD vollständig, `make gates` grün, das zweite `schema migrate --execute` endet mit Exit 0, Review und Verifikation liegen vor, Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — jedes Risiko bekommt genau einen Ausgang.

- Die Form am echten Server weicht von der Lesart ab (zum Beispiel `IF EXISTS` ohne Treffer meldet eine Information statt Erfolg). — **Ausgang:** eingetreten für drei Grenzen, sonst entfallen: `IF EXISTS` ohne Treffer ist am echten Server ein Erfolg (Messung, 2022 und 2025); das zweite ERROR 3727 nach 3728 und 3733, der fehlende Name von Standardwert und Schlüssel und die Prüfung des Verweises (3725) weichen ab und stehen als Grenze in `SPEC-036` Form D5 und im Register (Punkte 10, 11, 12, siehe unten).
- Punkt 1 und 3 des Registers sind durch Punkt 2 maskiert und erst nach diesem Slice an einem Server mit Prüfbedingung belegbar. — **Ausgang:** entfallen: Die Maskierung ist aufgehoben, das zweite `schema migrate --execute` endet mit Exit 0 (Verifikation s03); Punkt 1 trifft jetzt am Testserver (Exit 5 mit `DECLARE @df`, s12, Träger [slice-declare-df-standardwert-vorhandene-spalte](../open/slice-declare-df-standardwert-vorhandene-spalte.md)), Punkt 3 ist beantwortet (nächster Punkt).
- Die Frage zu `unique: true` im Index lässt sich nur am echten Server beantworten (EULA, Nutzer). — **Ausgang:** entfallen: Die Frage ist am echten Server beantwortet (Messprotokoll [drop-constraint-d-migrate-2026-11-11](../../../messungen/drop-constraint-d-migrate-2026-11-11.md), Schritte s14 und s15 beider Instanzen): kein Unterschied des Testservers beim eindeutigen Index; stattdessen trifft `DROP INDEX IF EXISTS` (Punkt 9 des Registers, Träger [slice-drop-index-if-exists](../open/slice-drop-index-if-exists.md)).
- Der Name eines Standardwerts und der des Schlüssels fehlen im Modell; `DROP CONSTRAINT <df-/pk-Name>` trifft deshalb am Testserver nicht (3728), am echten Server fällt die Bedingung. Entscheid zur öffentlichen Fläche (Standardantwort des Orchestrators, **umkehrbar**, Entscheid des Nutzers steht aus): die Fläche wird nicht erweitert, die Grenze bleibt benannt (`SPEC-036` Form D5, Register Punkt 11); nur wenn [slice-declare-df-standardwert-vorhandene-spalte](../open/slice-declare-df-standardwert-vorhandene-spalte.md) die Namen braucht, entscheidet der Nutzer dort über eine Erweiterung (Folge-Entscheid zu [ADR-0046](../../adr/0046-oeffentliche-flaeche-der-katalog-elemente.md)). — **Ausgang:** weiter offen → `BEO-TDS/katalog-elemente-grenzen-der-angleichung` (Punkt 11), Träger der zurückgestellte Slice [slice-declare-df-standardwert-vorhandene-spalte](../open/slice-declare-df-standardwert-vorhandene-spalte.md); die Standardantwort „Fläche nicht erweitert“ ist nicht vom Nutzer bestätigt.
- Zwei weitere Abweichungen vom Zielbild haben keinen Slice: das zweite ERROR 3727 nach 3728 und 3733 (Register Punkt 10) und die fehlende Prüfung des Verweises, 3725 (Punkt 12). — **Ausgang:** weiter offen → `BEO-TDS/katalog-elemente-grenzen-der-angleichung` (Punkte 10 und 12, Trigger: die Abnahme [slice-abnahme-d-migrate](../open/slice-abnahme-d-migrate.md) oder ein Client trifft das Verhalten).

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register; `grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln.

- **Was hat funktioniert:** Die Form D5 erkennt `ALTER TABLE … DROP CONSTRAINT [IF EXISTS]` für Eindeutigkeit, Fremdschlüssel und Prüfbedingung; das zweite `schema migrate --execute` von d-migrate 1.12.0 gegen den Testserver endet mit Exit 0 und `IDENTICAL` (Verifikation s03, s04). Die Antworten des echten Servers sind an beiden Instanzen (2022, 2025) in 22 Fällen aufgenommen und vom Verifier selbst aus den Rohbytes dekodiert; sie stimmen mit der Bibliothek überein (Befehl `0x00d8` ohne `COUNT`, 3728 State 1, 3733 State 2, 4902 mit einem ERROR). Die Frage zu `unique: true` ist am echten Server beantwortet. Die öffentliche Fläche wuchs nicht (`make flaeche`, Diff).
- **Was ging anders als geplant:** Die Messung fand mit `DROP INDEX IF EXISTS` (Punkt 9) einen weiteren Text, den der Testserver nicht erkennt; der Nutzer entschied, dafür einen eigenen Folge-Slice anzulegen. Der Review (`e5c345aa`: 0 HIGH, 4 MEDIUM, 2 LOW, 3 INFO) fand Zusagen breiter als die Messung (F-3), nicht getragene Abweichungen (F-2) und einen schwachen Einbettungstest (F-6). Nachbesserung `8b231cef` ohne zweiten Review: KDoc und Kommentare in `src/main` und `ErrorMapperTest` (F-1, F-3, F-5), Testcode `DropConstraintEmbeddingTest` mit Nummer und Befehl auf den Rohbytes (F-6, vier Mutationen rot gesehen); der Verifier hat sie einzeln geprüft, ein Review der Nachbesserung gab es nicht (Grenze). Die Verifikation fand mit V-1 dieselbe Zusage in `docs/user/einbettung-und-betrieb.md` noch ohne die Bedingung der Messung; sie ist in der Closure angeglichen. Der Merge mit `main` (der BulkCopy-Slice) löste fünf Textkonflikte in Dokumenten und im Messtest; die Zahl der Messdateien je Instanz (20) und die Messnummern M-33 bis M-36 (gehören dem BulkCopy-Slice, D5 führt keine M-Nummer) blieben getrennt.
- **Zuordnung Messung | Festlegung | Review und Verifikation.** *Messung am echten Server* (2022 und 2025, Szenario `struktur-drop-constraint`, 22 Antworten): Antwort `0x00d8` ohne `COUNT` für Prüfbedingung (Treffer ohne `IF EXISTS`, Name in anderer Schreibweise), Standardwert und Schlüssel ohne Verweis (Treffer ohne `IF EXISTS`), Eindeutigkeit und Fremdschlüssel nur mit `IF EXISTS`; `IF EXISTS` ohne Treffer und mit dem Namen einer anderen Tabelle (Erfolg); 3728, 3733 (jeweils mit zweitem ERROR 3727), 4902 mit einem ERROR; 3725 für den Schlüssel mit Verweis. *Messung am Testserver* mit d-migrate 1.12.0 (nicht am echten Server): Exit-Codes und `IDENTICAL` der Schritte s01 bis s15 des Verifiers. *Festlegung (Setzung, in `SPEC-036` Form D5 gekennzeichnet):* Schreibweise für Eindeutigkeit und Fremdschlüssel und bei anderer Kollation, der Treffer ohne `IF EXISTS` für Eindeutigkeit und Fremdschlüssel, der Name einer anderen Tabelle in einem anderen Schema als `dbo` (gemessen nur `dbo`), die Zusammenlegung der Namen je Tabelle. *Review und Verifikation:* Review am Stand `e5c345aa`; die Nachbesserung `8b231cef` hat der Verifier einzeln gegen den Diff geprüft und die sieben Mutationen selbst gefahren (7 von 7 rot), ohne zweiten Review.
- **Verifikation.** Bericht [2026-11-11-8b231cef-verifikation](../../../reviews/2026-11-11-8b231cef-verifikation-slice-drop-constraint-form-d5.md), Gesamturteil „closure-fähig unter B1 bis B5“: `make clean-build` (Exit 0, 608 s, 1349 PASSED), `make e2e` (Exit 0, 8 Stufen), `make gates FORCE=1`, `make flaeche`, `make docs-check`, Zitate mit `tools/matrix-zitate.sh` (349 Zitate, 0 ohne PASSED), Rohbytes beider Instanzen dekodiert, d-migrate-Läufe s01 bis s15, 7 Mutationen. Haken gesetzt nach der Verifikation (`BEO-TDS/haken-vor-verifikation-gesetzt` eingehalten). Auf dem Merge-Stand mit `main` (`5c2e2804`) liefen in der Closure `make spec-abdeckung`, `make gates` und `make docs-check` (Ergebnis: Commit-Nachricht der Closure und Bericht des Planners); `make clean-build`, `make e2e` und `make matrix-zitate` fuhr die Closure nicht (Tabelle „Pflicht-Sensoren je Art der Änderung“: Verifier einmal auf dem End-Stand; der Merge ändert keine Zeile in `src/`, die Probe des Verifiers auf dem Merge-Stand zeigt 1357 PASSED).
- **Grenzen.** (1) Der Entscheid des Nutzers zur öffentlichen Fläche (Namen von Standardwert und Schlüssel, Folge-Entscheid zu [ADR-0046](../../adr/0046-oeffentliche-flaeche-der-katalog-elemente.md)) steht aus; geführt ist die Standardantwort des Orchestrators auf die Empfehlung des Reviewers, Option 3 „Fläche nicht erweitert“, nicht vom Nutzer bestätigt, umkehrbar; Punkt 11 des Registers trägt den Trigger. (2) Gegen den echten SQL Server fuhr nur die Messung des Implementers; die Läufe des Verifiers fuhren gegen den Testserver, die Ablagen las er (Rohbytes dekodiert, Exit-Codes). (3) Schritte s17 bis s19 der Messung wiederholte der Verifier nicht. (4) Bei den Mutationen M1 und M2 sah der Verifier die Rotfärbung nur im Einbettungstest; die Unit-Klassen zeigt der Review rot.
- **Steering-Loop-Eintrag:** keine Regel, kein Sensor und keine Skill-Zeile verkörpert; der Eintrag ist gezählt, nicht verkörpert. *Benannte Spec-Lücke* (getragen in der versionierten Spezifikation, `SPEC-036` Form D5 Absatz Grenzen): der Name von Standardwert und Schlüssel (Punkt 11), das zweite ERROR 3727 (Punkt 10), die Prüfung des Verweises 3725 (Punkt 12). Gelernt: (a) Ergänzt eine Behebung die Bedingung einer Messzusage in der Spezifikation oder im KDoc, sucht sie dieselbe Zusage in `docs/user/` mit; F-3 des Reviews behob Spezifikation und KDoc, V-1 der Verifikation fand die Zusage in der Nutzer-Doku unverändert (zweite Fundstelle; `AGENTS.md` §3.8 gilt für jede Fundstelle). (b) Ein Merge mit einem Slice, der dieselbe Messreihe erweitert, trifft Zählungen an mehreren Stellen (Zahl der Messdateien in Test, Make-Fragment und Messprotokoll) und die Messnummern; beides ist nach dem Zusammenführen zu prüfen, nicht aus einer Seite zu übernehmen.
- **Beobachtungs-Register (`../observations/`):** `evidence/slice-drop-constraint-form-d5.md` in `BEO-TDS/katalog-elemente-grenzen-der-angleichung/` ergänzt; Punkt 2 gebaut, Punkt 3 beantwortet, Punkte 9 bis 12 neu, Träger je Punkt in `state.md` (Punkt 9 [slice-drop-index-if-exists](../open/slice-drop-index-if-exists.md), Punkte 1 und 11 [slice-declare-df-standardwert-vorhandene-spalte](../open/slice-declare-df-standardwert-vorhandene-spalte.md), Punkte 10 und 12 Trigger an [slice-abnahme-d-migrate](../open/slice-abnahme-d-migrate.md)).
- **Folge-Slices:** [slice-drop-index-if-exists](../open/slice-drop-index-if-exists.md) (Punkt 9, Größe S, vor der Abnahme), [slice-declare-df-standardwert-vorhandene-spalte](../open/slice-declare-df-standardwert-vorhandene-spalte.md) (Punkte 1 und 11, zurückgestellt), [slice-abnahme-d-migrate](../open/slice-abnahme-d-migrate.md) (Punkte 10 und 12 als Trigger); je eine Datei in `open/`. Ihre Start-Trigger nennen „liegt in `done/`“: nach dem Move stimmt das.
- **Risiken aus §6:** alle fünf tragen einen Ausgang: Form weicht ab eingetreten für drei Grenzen, sonst entfallen; Maskierung entfallen; `unique: true`-Frage entfallen (beantwortet); Namen und Fläche weiter offen (Punkt 11); zwei Abweichungen ohne Slice weiter offen (Punkte 10, 12).
- **Drei Paarungen:** Anker — keine Regel verkörpert, nichts zu paaren. Folge-Slice — drei Dateien in `open/`. Register — `BEO-TDS/katalog-elemente-grenzen-der-angleichung` trägt Zustand und Beleg, die Folge-Slices und die Abnahme zeigen darauf. Getragen.

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung.

**Vorgelagert — Sub-Area-Wahl prüfen:** Eine Sub-Area (`*`, Kürzel `TDS`); keine Aufteilung nötig.

**Vorgelagert — offene Beobachtungen sichten:** `BEO-TDS/katalog-elemente-grenzen-der-angleichung` (Herkunft, Punkte 2 und 3), `BEO-TDS/messung-am-echten-sql-server-fehlt` (Eingang der Messung).

Alle berührten Sub-Areas GF.
