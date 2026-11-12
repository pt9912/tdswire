# Slice slice-drop-index-if-exists: `DROP INDEX [IF EXISTS] <ix> ON <t>` als Form D6 erkennen, am echten SQL Server gemessen

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

**Berührte Spec-Stellen:** SPEC-036 (erkannte Anweisungsformen, Form D, Absatz Grenzen) · SPEC-035 (Katalog der Elemente) · `TDS-FA-11.a` (Ablaufpunkt 9, Strukturänderung)

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-11.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung**; die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks.

**Ziel:** Ein zweites `schema migrate --execute` von d-migrate, dessen Soll einen vorhandenen Index ändert, endet gegen den Testserver wie gegen den echten SQL Server. Heute sendet d-migrate in diesem Fall zuerst `DROP INDEX IF EXISTS [ix] ON [t];`. Der echte Server antwortet mit `DONE 0x00c9` (beide Instanzen, 2022 und 2025); der Testserver erkennt die Form nicht (`Unsupported SQL.`, Exit 5). Maßstab des Nutzers: die Spezifikation ist Zielbild, der echte SQL Server die Referenz.

**Herkunft.** Punkt 9 des Registers [katalog-elemente-grenzen-der-angleichung](../observations/BEO-TDS/katalog-elemente-grenzen-der-angleichung/state.md), Fund der Messung zu [slice-drop-constraint-form-d5](../done/slice-drop-constraint-form-d5.md). Entscheid des Nutzers vom 2026-11-11: der Fund bekommt einen eigenen Folge-Slice.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- `ALTER TABLE … DROP CONSTRAINT [IF EXISTS]` (Form D5) — liegt in [slice-drop-constraint-form-d5](../done/slice-drop-constraint-form-d5.md); dieser Slice setzt dessen Ergebnis voraus und ändert dieselben Dateien.
- Der Text mit `DECLARE @df … EXEC sp_executesql` für einen Standardwert an einer vorhandenen Spalte — größer und zurückgestellt: [slice-declare-df-standardwert-vorhandene-spalte](../done/slice-declare-df-standardwert-vorhandene-spalte.md).
- Die Abnahme mit d-migrate über alle Funktionen — [slice-abnahme-d-migrate](../open/slice-abnahme-d-migrate.md); dieser Slice liegt davor, weil der Validator bei einem geänderten Index sofort auf die Form trifft.
- Die alte Schreibweise `DROP INDEX <t>.<ix>` — d-migrate sendet sie nicht (Setzung des Planners, am Mitschnitt zu prüfen); sie bleibt unerkannt, bis ein Client sie verlangt.
- Referentielle Integrität im Bestand und Nutzung von Indizes — der Katalog beschreibt, der Testserver prüft nichts.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit.

Liefer-Punkte (drei):

- [x] **Messung am echten Server** ([TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)): `DROP INDEX [IF EXISTS] <ix> ON <t>` am echten SQL Server (Developer-Image, gepinnter Digest, EULA bestätigt, nur lokal; Werkzeug `make vergleichslauf-d`, Funktion `schemalauf`, Szenarien in `MessungBulkTests`): Abschlusskennung `0x00c9` und Tokenfolge; Fehlernummern ohne `IF EXISTS` und ohne Treffer; Name eines Index einer anderen Tabelle; Index, der eine Eindeutigkeit oder einen Primärschlüssel trägt (ist `DROP INDEX` darauf zulässig, mit welcher Nummer sonst?); Namen ohne Beachtung der Schreibweise. Das Protokoll steht in `docs/messungen/` mit Rechner, Last und Digest.
- [x] **Bau** ([TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend)): Form D6 in Parser, Planer und Katalog analog D5 (entfernt den benannten Index aus der Tabelle, meldet die gemessene Antwort, `IF EXISTS` ohne Treffer, unbekannte Tabelle); interne Fehlercodes nur für gemessene Fehlernummern; die öffentliche Fläche wächst nicht (sonst Entscheid des Nutzers nach [ADR-0046](../../adr/0046-oeffentliche-flaeche-der-katalog-elemente.md)); Tests mit Handvektoren und den Zeilen der Messung; ein zweites `schema migrate --execute` mit geändertem Index endet mit Exit 0; Mutationen je Zusage.
- [x] **Fortschreibung** ([TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate)): `SPEC-036` (Form D6, Grenze entfällt), `docs/user/` (Grenzen in `testserver-manuell.md` und `einbettung-und-betrieb.md`), das Register [katalog-elemente-grenzen-der-angleichung](../observations/BEO-TDS/katalog-elemente-grenzen-der-angleichung/state.md) (Punkt 9).

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
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/Statement.kt`, `StructureParts.kt`, `StructureChecks.kt`, `StructurePlanner.kt`, `StatementParser.kt`, `StatementPlanner.kt`, `SimulationChanges.kt`, `SimulationBackend.kt` | update | Form D6: Erkennung (`Statement.DropIndex`, Zweig `DROP` im Parser), Planung gegen den Katalog (Index der Tabelle entfernen, `IF EXISTS` ohne Treffer, Name einer anderen Tabelle, Eindeutigkeit, unbekannte Tabelle), die zwei neuen Namen in `SimCodes`, `PlanResult.Unchanged` für `IF EXISTS` an einer unbekannten Tabelle (Erfolg ohne Callback), KDoc von `SimulationBackend`; dieselben Dateien wie D5 |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/error/ErrorCode.kt`, `ErrorMapper.kt` | update | `TDS_SIM_NOT_AN_INDEX` (3701, Class 11, State 7) und `TDS_SIM_INDEX_OF_CONSTRAINT` (3723, State 5) als Codes und in der Positivliste der Backends; intern, keine Änderung der öffentlichen Fläche |
| `src/main/kotlin/.../simulation/SimModel.kt`, `SimElements.kt` | unverändert | der Katalog führt die Indizes je Tabelle (`SimTable.indexes`), der Name gilt je Tabelle; die öffentliche Fläche wächst nicht (die Art des Abschlusses `0x00c9` bleibt Grenze, Register Punkt 13) |
| `src/test/kotlin/.../simulation/SimulationBackendDropIndexTest.kt` (neu), `SimulationBackendDropConstraintTest.kt`, `DMigrateWire.kt`, `src/test/kotlin/.../protocol/error/ErrorMapperTest.kt`, `src/integrator/kotlin/.../DropIndexEmbeddingTest.kt` (neu) | neu / update | Handvektoren (Text von d-migrate am Draht, Zeilen der Messung), Fehlercodes, Einbettungstest auf den Rohbytes; der Test zu D5 führt `DROP INDEX IF EXISTS` nicht mehr unter den nicht erkannten Texten |
| `referenztest-dotnet/Vergleich/MessungBulkTests.cs` (Szenario `struktur-drop-index`), `referenztest-dotnet/Mitschnitt/MessungBulkBelegTests.cs`, `referenztest-dotnet/Mitschnitt/messung/bulk-merge-katalog/{2022,2025}/struktur-drop-index.messung`, `tools/mk/vergleichslauf-d.mk` | update / neu | Messung der Antworten des echten Servers und ihre Belege ohne Instanz (dazu die Zeile des Befehls `0x00c9` in der Ablage des Laufs `schema`); Zahl der Szenarien im Kommentar des Make-Fragments |
| `tools/vergleichslauf-d.sh` (Funktion `schemalauf`), `referenztest-dotnet/Mitschnitt/messung/jdbc-d-migrate/{2022,2025}.schema.aufnahme.txt` | unverändert | Schritt s14 des Laufs `schema` enthält den Text `DROP INDEX IF EXISTS` und seine Antwort bereits; d-migrate gegen den Testserver mit geändertem Index ist von Hand gefahren (Messprotokoll, Tabelle C) |
| `docs/messungen/drop-index-d-migrate-2026-11-11.md` (neu), `docs/messungen/referenzabgleich.md` | neu / update | Messprotokoll der Form am echten Server |
| `spec/spezifikation.md` (`SPEC-036` Form D6 und Grenzen, Fehlertabelle, Ablaufpunkt 9, Änderungsprotokoll), `docs/user/testserver-manuell.md`, `docs/user/einbettung-und-betrieb.md`, `docs/user/referenz-konfiguration.md` | update | Zusage und Grenzen; `docs/user/README.md` und `referenz-ereignisse.md` gelesen, unverändert |
| `docs/plan/planning/observations/BEO-TDS/katalog-elemente-grenzen-der-angleichung/` (`state.md`, `observation.md`) | update | Punkt 9: gebaut; neue Punkte 13 und 14; Punkt 11 ergänzt |
| `docs/plan/planning/in-progress/roadmap.md` | update | Ruhe-Marker und Zustandsfeld der Welle (der Slice liegt in `in-progress/`) |
| `docs/plan/planning/done/slice-drop-constraint-form-d5.md`, `docs/plan/planning/open/slice-abnahme-d-migrate.md`, `docs/plan/planning/welle-testserver-manuell.md` | update | Zeiger auf diesen Slice (drei im Slice zu D5, Verweise im Slice zur Abnahme), Tabellenzeile und Reihenfolge der Welle |
| `docs/plan/spec-abdeckung.md` | erzeugt | `make spec-abdeckung` einmal, Tests geändert |

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-drop-constraint-form-d5](../done/slice-drop-constraint-form-d5.md) liegt in `done/` (gleiche Dateien `StructureParts`, `StructurePlanner`, `StatementPlanner`); die EULA für das gepinnte Developer-Image ist vom Nutzer für diesen Lauf bestätigt; ein Verantwortlicher ist eingetragen. Der Slice steht vor [slice-abnahme-d-migrate](../open/slice-abnahme-d-migrate.md).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): die Messung zeigt, dass gewöhnlicher Index, Eindeutigkeits- und Schlüsselindex verschieden antworten; Schnittvorschlag: gewöhnlicher Index | Bedingungsindizes.
- `in-progress` → `open` (blockiert): der Nutzer bestätigt die EULA nicht; dann bleibt die Form Setzung, und der Nutzer entscheidet, ob ohne Messung gebaut wird.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln.

DoD vollständig, `make gates` grün, das zweite `schema migrate --execute` mit geändertem Index endet mit Exit 0, Review und Verifikation liegen vor, Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — jedes Risiko bekommt genau einen Ausgang.

- Ein Text trägt mehrere Namen (zwei `DROP INDEX IF EXISTS` in einer Nachricht): der Planer muss die Anweisungen einzeln erkennen und bei einem Fehler der zweiten die erste nicht still behalten. — **Ausgang:** weiter offen als Grenze: der echte Server antwortet mit zwei `DONE` (Zeilen 22 und 23 der Ablage, die zweite Anweisung läuft nach einem Fehler der ersten), die Bibliothek erkennt mehrere Anweisungen in einem Text nicht und antwortet `Unsupported SQL.` ([SPEC-036](../../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig), Form D6 Grenze e); Träger ist ein Client, der es verlangt, oder die Abnahme [slice-abnahme-d-migrate](../open/slice-abnahme-d-migrate.md); kein eigenes Register, die Grenze steht in der Spezifikation.
- Indexnamen gelten je Tabelle, nicht je Schema: gleicher Name an zwei Tabellen darf den Treffer nicht verwechseln; der Katalog führt ihn möglicherweise anders. — **Ausgang:** entfallen: der Katalog führt den Namen je Tabelle (`SimTable.indexes`); gemessen in den Zeilen 07, 09 und 20 der Ablage, Test „je Tabelle“ in `SimulationBackendDropIndexTest`, Mutation M8 rot.
- Ein zweiter `ERROR`-Abschluss wie bei D5 (die Antwort auf einen Fehler im Lauf erscheint doppelt, wenn die Anweisung zweimal gemeldet wird). — **Ausgang:** entfallen: die Ablage zeigt in den Zeilen 09 bis 23 kein zweites `ERROR`, der Verifier las alle 24 Anfragen je Instanz selbst.
- Die Form am echten Server weicht von der Lesart ab (zum Beispiel meldet `IF EXISTS` ohne Treffer eine Information statt Erfolg, oder `DROP INDEX` auf einen Schlüsselindex ist abgewiesen). — **Ausgang:** eingetreten und eingearbeitet: `IF EXISTS` an einer unbekannten Tabelle ist ein Erfolg (`PlanResult.Unchanged`, kein Callback), der Index einer Eindeutigkeit oder eines Schlüssels ist 3723 auch mit `IF EXISTS` (State 5 bzw. 4); Spezifikation Form D6 und Fehlertabelle, Tests und Mutationen M2, M3, M5, M10. Die zwei Abweichungen der Bibliothek (State 7 statt 6 und Befehl `0x00fd` statt `0x00c9` nach 3701) sind benannt und nicht gebaut: Register Punkt 13 (b).
- Die öffentliche Fläche wächst (neuer Typ für den Index-Abwurf). Keine neue Fläche ist die Zusage dieses Plans; sonst entscheidet der Nutzer. — **Ausgang:** entfallen: `make flaeche` Exit 0, `Statement.DropIndex`, `PlanResult.Unchanged`, die zwei Codes und `SimCodes` sind `internal`, `SimModel.kt`, `SimElements.kt` und [SPEC-059](../../../../spec/spezifikation.md#einstieg-des-integrators-vertrag-zu-spec-059) unverändert. Der Entscheid des Nutzers vom 2026-11-12 per Auswahl (`StatementKind` bekommt `CREATE_INDEX` `0x00c8` und `DROP_INDEX` `0x00c9`, beide ohne `COUNT`) ist nicht Teil dieses Slice, sondern der Folge-Slice [slice-statementkind-index-mitglieder](../done/slice-statementkind-index-mitglieder.md); hier bleibt der Abschluss `UNKNOWN`.

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register; `grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln.

- **Was hat funktioniert:** Die Messung zuerst (24 Anfragen je Instanz, 2022 und 2025 gleich) trug den Bau: zwei neue Fehlercodes, beide gemessen (3701, 3723), und jede Zusage der Spezifikation hat mindestens eine Mutation (M1 bis M10 des Verifiers, alle rot). Das zweite `schema migrate --execute` mit geändertem oder umbenanntem Index endet am Testserver mit Exit 0 und `IDENTICAL` (Tabelle C des Messprotokolls, vom Verifier mit d-migrate 1.12.0 wiederholt). Die Rohbytes las der Review und noch einmal der Verifier mit eigenem Dekoder; das fing Aussagen breiter als die Ablage (F-1, F-5, F-9) vor der Closure.
- **Was ging anders als geplant:** (1) `IF EXISTS` an einer unbekannten Tabelle ist am echten Server ein Erfolg, und 3723 gilt auch mit `IF EXISTS`; die Lesart des Plans war enger (Risiko 4, eingearbeitet). (2) Die Messung von `CREATE INDEX` (`0x00c8` ohne `COUNT`) fiel als Nebenbefund an; die Spezifikation sagte „nicht gemessen“ (F-2), das Register Punkt 13 hält es jetzt. (3) Die Plan-Tabelle nannte drei berührte Dateien nicht (F-3, Regel im Reviewer-Skill verkörpert). (4) Die Nachbesserung `93097c4d` (F-1, F-2, F-4, F-8, F-9) lief ohne zweiten Review; der Verifier las den neuen Zweig `PlanResult.Unchanged` in `SimulationBackend.kt` und mutierte ihn (M5 rot); Entscheid des Planners: der Verifikationsbericht genügt als Prüfung. (5) Die .NET-Belege (`MessungBulkBelegTests`) liefen im Gate aus dem Layer-Cache (Hinweis V-2 des Verifiers); die Messaussage trägt, weil der Verifier die Rohbytes selbst las, einen Frischbeleg der .NET-Tests gibt es für diesen Stand nicht.
- **Zuordnung Messung | Festlegung:** Gemessen (Ablage `struktur-drop-index.messung`, beide Instanzen): `DONE` `0x00c9` ohne `COUNT`; 3701 (Class 11, State 7; State 6 an unbekannter Tabelle); 3723 (Class 16, State 5 für die Eindeutigkeit, State 4 für den Schlüssel; `DONE` mit `0x00fd`); `IF EXISTS` ohne Treffer und an unbekannter Tabelle ist Erfolg; der Name gilt je Tabelle (Tabellen im Schema `dbo`); zwei Anweisungen in einem Text; `CREATE INDEX` `0x00c8` ohne `COUNT` für gewöhnlichen, eindeutigen und zweispaltigen Index. Festlegung (Setzung, so gekennzeichnet): Schreibweise des Namens am gewöhnlichen Index (gemessen am eindeutigen), unquotiertes `IF` als Name, Treffer der Zeilen 11 und 21 (ohne Lesen), der Text von 3701 als Auszug ohne Namen, der Name des Schlüssels im Modell (nicht geführt: 3701 statt 3723 State 4), die alte Schreibweise `DROP INDEX <t>.<ix>`, State 7 und `0x00fd` der Bibliothek nach 3701 (Punkt 13 (b)).
- **Steering-Loop-Eintrag:** benannte Spec-Lücke: die öffentliche Aufzählung `StatementKind` ([SPEC-059](../../../../spec/spezifikation.md#einstieg-des-integrators-vertrag-zu-spec-059)) führt keine Art für `CREATE INDEX` und `DROP INDEX`, obwohl der echte Server mit `0x00c8` und `0x00c9` ohne `COUNT` antwortet; benannt in [SPEC-036](../../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) Form D6 Grenze (a) (als Spec-Lücke ohne Zielort außerhalb der Spezifikation: sie ist in der versionierten Spezifikation verkörpert); ihr Träger ist der Folge-Slice [slice-statementkind-index-mitglieder](../done/slice-statementkind-index-mitglieder.md). Eine verkörperte Regel oder ein neuer Sensor entstand mit diesem Slice nicht; gezählt, nicht verkörpert: vier weitere Belege in bestehenden Beobachtungen (siehe Register).
- **Beobachtungs-Register (`../observations/`):** `evidence/slice-drop-index-if-exists.md` ergänzt in `BEO-TDS/aussagen-ueber-messung-hinaus/`, `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen/`, `BEO-TDS/plan-tabelle-nennt-neue-datei-nicht/` (die Regel ist verkörpert, der Review fand den Verstoß) und `BEO-TDS/nachbesserung-ohne-zweiten-review/`; die Zähler folgen aus den Dateien, den Lese-Schritt macht die Welle-Closure. `BEO-TDS/katalog-elemente-grenzen-der-angleichung/`: Punkt 9 gebaut als Form D6, Punkt 13 (a) mit Träger [slice-statementkind-index-mitglieder](../done/slice-statementkind-index-mitglieder.md), Punkt 13 (b) und Punkt 14 bleiben Trigger-Punkte (Client wertet Befehl oder State aus; die Abnahme [slice-abnahme-d-migrate](../open/slice-abnahme-d-migrate.md)), keine Blocker.
- **Folge-Slices:** [slice-statementkind-index-mitglieder](../done/slice-statementkind-index-mitglieder.md) (`CREATE_INDEX` `0x00c8` und `DROP_INDEX` `0x00c9` als öffentliche Mitglieder von `StatementKind`, beide ohne `COUNT`; Entscheid des Nutzers am 2026-11-12 per Auswahl).
- **Risiken aus §6:** mehrere Anweisungen in einem Text: weiter offen als Grenze; Indexname je Tabelle: entfallen; zweites `ERROR`: entfallen; Abweichung von der Lesart: eingetreten und eingearbeitet; Fläche wächst: entfallen (Folge-Slice für `StatementKind`).
- **Verifikation:** [Verifikationsbericht](../../../reviews/2026-11-12-93097c4d-verifikation-slice-drop-index-if-exists.md) auf `93097c4d`: konform; `make clean-build` Exit 0 (1369 `PASSED`), `make e2e` 8 Stufen grün, `tools/matrix-zitate.sh` 349 Zitate ohne Fehlbefund, `make gates FORCE=1` Exit 0, `make flaeche` und `make docs-check` Exit 0, d-migrate gegen den Testserver (Tabelle C). `main` (`d019cf2d`) ist im Zweig enthalten, der Merge war ohne Wirkung. Läufe des Planners auf dem End-Stand (nur Dokumente geändert): `make docs-check` (0 Befunde), `make flaeche` und `make kopplung-zahlen` Exit 0; `make gates` Exit 0 in 9 s, alle drei Stempel-Klassen übersprungen (Beleg greift, `main` brachte nichts).

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung.

**Vorgelagert — Sub-Area-Wahl prüfen:** Eine Sub-Area (`*`, Kürzel `TDS`); keine Aufteilung nötig.

**Vorgelagert — offene Beobachtungen sichten:** `BEO-TDS/katalog-elemente-grenzen-der-angleichung` (Herkunft, Punkt 9).

Alle berührten Sub-Areas GF.
