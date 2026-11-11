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

- [ ] **Messung am echten Server** ([TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)): `ALTER TABLE … DROP CONSTRAINT [IF EXISTS]` am echten SQL Server (Developer-Image, gepinnter Digest, EULA bestätigt, nur lokal): Antwortart und Tokenfolge für Prüfbedingung, Eindeutigkeit, Fremdschlüssel und Standardwert; Fehlerfälle mit und ohne `IF EXISTS`, mit und ohne Treffer. Dazu die offene Frage von Punkt 3 des Registers: liest d-migrate ein `CREATE UNIQUE INDEX` auch am echten Server als Eindeutigkeit zurück (dann ist `POST_EXECUTE_DRIFT` mit `unique: true` im Index keine Abweichung vom echten Server)? Das Protokoll steht in `docs/messungen/` mit Rechner, Last und Digest.
- [ ] **Bau** ([TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend)): Form D5 in Parser, Planer und Katalog (entfernt die benannte Prüfbedingung, Eindeutigkeit, den Fremdschlüssel oder Standardwert aus der Tabelle, meldet die gemessene Antwort, geht nach der Entscheidung von [ADR-0046](../../adr/0046-oeffentliche-flaeche-der-katalog-elemente.md) an den Integrator); Tests mit Handvektoren und den Zeilen der Messung; ein zweites `schema migrate --execute` von d-migrate gegen den Testserver endet mit Exit 0 und `IDENTICAL`; Mutationen je Zusage.
- [ ] **Fortschreibung** ([TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate)): `SPEC-036` (Form D5, Grenze entfällt), `docs/user/` (Grenzen in `testserver-manuell.md` und `einbettung-und-betrieb.md`), das Register [katalog-elemente-grenzen-der-angleichung](../observations/BEO-TDS/katalog-elemente-grenzen-der-angleichung/state.md) (Punkt 2 und, je nach Messung, Punkt 3).

Nachweis-Pflichten für alle Punkte:

- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Haken der Liefer-Punkte setzt der Planner nach der Verifikation, nicht der Implementer (`BEO-TDS/haken-vor-verifikation-gesetzt`).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben oder „keine Beobachtung angefallen“ in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

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

- Die Form am echten Server weicht von der Lesart ab (zum Beispiel `IF EXISTS` ohne Treffer meldet eine Information statt Erfolg). — **Ausgang:** offen bis zur Closure.
- Punkt 1 und 3 des Registers sind durch Punkt 2 maskiert und erst nach diesem Slice an einem Server mit Prüfbedingung belegbar. — **Ausgang:** offen bis zur Closure.
- Die Frage zu `unique: true` im Index lässt sich nur am echten Server beantworten (EULA, Nutzer). — **Ausgang:** offen bis zur Closure.
- Der Name eines Standardwerts und der des Schlüssels fehlen im Modell; `DROP CONSTRAINT <df-/pk-Name>` trifft deshalb am Testserver nicht (3728), am echten Server fällt die Bedingung. Entscheid zur öffentlichen Fläche (Standardantwort des Orchestrators, **umkehrbar**, Entscheid des Nutzers steht aus): die Fläche wird nicht erweitert, die Grenze bleibt benannt (`SPEC-036` Form D5, Register Punkt 11); nur wenn [slice-declare-df-standardwert-vorhandene-spalte](../open/slice-declare-df-standardwert-vorhandene-spalte.md) die Namen braucht, entscheidet der Nutzer dort über eine Erweiterung (Folge-Entscheid zu [ADR-0046](../../adr/0046-oeffentliche-flaeche-der-katalog-elemente.md)). — **Ausgang:** weiter offen, Träger der zurückgestellte Slice.
- Zwei weitere Abweichungen vom Zielbild haben keinen Slice: das zweite ERROR 3727 nach 3728 und 3733 (Register Punkt 10) und die fehlende Prüfung des Verweises, 3725 (Punkt 12). — **Ausgang:** weiter offen, im Register mit Trigger geführt.

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register; `grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln.

- **Was hat funktioniert:** —
- **Was ging anders als geplant:** — (Vermerk des Implementers: Nachbesserung nach dem Review ohne zweiten Review: KDoc und Kommentare in `src/main` und `ErrorMapperTest` (F-1, F-3, F-5), Testcode `DropConstraintEmbeddingTest` mit Nummer und Befehl auf den Rohbytes (F-6, vier Mutationen rot gesehen))
- **Steering-Loop-Eintrag:** —
- **Beobachtungs-Register (`../observations/`):** —
- **Folge-Slices:** —
- **Risiken aus §6:** —

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung.

**Vorgelagert — Sub-Area-Wahl prüfen:** Eine Sub-Area (`*`, Kürzel `TDS`); keine Aufteilung nötig.

**Vorgelagert — offene Beobachtungen sichten:** `BEO-TDS/katalog-elemente-grenzen-der-angleichung` (Herkunft, Punkte 2 und 3), `BEO-TDS/messung-am-echten-sql-server-fehlt` (Eingang der Messung).

Alle berührten Sub-Areas GF.
