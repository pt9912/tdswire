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
- Der Text mit `DECLARE @df … EXEC sp_executesql` für einen Standardwert an einer vorhandenen Spalte — größer und zurückgestellt: [slice-declare-df-standardwert-vorhandene-spalte](../open/slice-declare-df-standardwert-vorhandene-spalte.md).
- Die Abnahme mit d-migrate über alle Funktionen — [slice-abnahme-d-migrate](../open/slice-abnahme-d-migrate.md); dieser Slice liegt davor, weil der Validator bei einem geänderten Index sofort auf die Form trifft.
- Die alte Schreibweise `DROP INDEX <t>.<ix>` — d-migrate sendet sie nicht (Setzung des Planners, am Mitschnitt zu prüfen); sie bleibt unerkannt, bis ein Client sie verlangt.
- Referentielle Integrität im Bestand und Nutzung von Indizes — der Katalog beschreibt, der Testserver prüft nichts.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **Messung am echten Server** ([TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)): `DROP INDEX [IF EXISTS] <ix> ON <t>` am echten SQL Server (Developer-Image, gepinnter Digest, EULA bestätigt, nur lokal; Werkzeug `make vergleichslauf-d`, Funktion `schemalauf`, Szenarien in `MessungBulkTests`): Abschlusskennung `0x00c9` und Tokenfolge; Fehlernummern ohne `IF EXISTS` und ohne Treffer; Name eines Index einer anderen Tabelle; Index, der eine Eindeutigkeit oder einen Primärschlüssel trägt (ist `DROP INDEX` darauf zulässig, mit welcher Nummer sonst?); Namen ohne Beachtung der Schreibweise. Das Protokoll steht in `docs/messungen/` mit Rechner, Last und Digest.
- [ ] **Bau** ([TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend)): Form D6 in Parser, Planer und Katalog analog D5 (entfernt den benannten Index aus der Tabelle, meldet die gemessene Antwort, `IF EXISTS` ohne Treffer, unbekannte Tabelle); interne Fehlercodes nur für gemessene Fehlernummern; die öffentliche Fläche wächst nicht (sonst Entscheid des Nutzers nach [ADR-0046](../../adr/0046-oeffentliche-flaeche-der-katalog-elemente.md)); Tests mit Handvektoren und den Zeilen der Messung; ein zweites `schema migrate --execute` mit geändertem Index endet mit Exit 0; Mutationen je Zusage.
- [ ] **Fortschreibung** ([TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate)): `SPEC-036` (Form D6, Grenze entfällt), `docs/user/` (Grenzen in `testserver-manuell.md` und `einbettung-und-betrieb.md`), das Register [katalog-elemente-grenzen-der-angleichung](../observations/BEO-TDS/katalog-elemente-grenzen-der-angleichung/state.md) (Punkt 9).

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
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/Statement.kt`, `StructureParts.kt`, `StructurePlanner.kt`, `StatementPlanner.kt`, `SimulationChanges.kt` | update | Form D6: Erkennung (`Statement.DropIndex`), Planung gegen den Katalog (Index der Tabelle entfernen, `IF EXISTS` ohne Treffer, Name einer anderen Tabelle, unbekannte Tabelle); dieselben Dateien wie D5, daher nach dessen Closure |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/error/ErrorCode.kt`, `ErrorMapper.kt` | update | interne Codes nur für gemessene Fehlernummern; keine Änderung der öffentlichen Fläche |
| `src/main/kotlin/.../simulation/SimModel.kt`, `SimElements.kt` | voraussichtlich unverändert | der Katalog führt Indizes je Tabelle bereits; zu prüfen, ob der Name je Tabelle (nicht je Schema) geführt wird |
| `src/test/kotlin/.../simulation/SimulationBackendDropIndexTest.kt` (neu), `DMigrateWire.kt`, `ErrorMapperTest.kt`, `src/integrator/kotlin/.../DropIndexEmbeddingTest.kt` | neu / update | Handvektoren (Text von d-migrate am Draht, Zeilen der Messung), Fehlercodes, Einbettungstest auf den Rohbytes |
| `referenztest-dotnet/Vergleich/MessungBulkTests.cs` (Szenario `struktur-drop-index`), `referenztest-dotnet/Mitschnitt/MessungBulkBelegTests.cs`, `referenztest-dotnet/Mitschnitt/messung/bulk-merge-katalog/{2022,2025}/struktur-drop-index.messung` | update / neu | Messung der Antworten des echten Servers und ihre Belege ohne Instanz |
| `tools/vergleichslauf-d.sh` (Funktion `schemalauf`), `referenztest-dotnet/Mitschnitt/messung/jdbc-d-migrate/{2022,2025}.schema.aufnahme.txt` | update | d-migrate (`schema migrate`) mit geändertem Index gegen den echten Server, Aufnahme der Antworten |
| `docs/messungen/drop-index-d-migrate-<Datum>.md` (neu), `docs/messungen/referenzabgleich.md` | neu / update | Messprotokoll der Form am echten Server |
| `spec/spezifikation.md` (`SPEC-036` Form D6 und Grenzen, Fehlertabelle, Ablaufpunkt 9, Änderungsprotokoll), `docs/user/testserver-manuell.md`, `docs/user/einbettung-und-betrieb.md` | update | Zusage und Grenzen |
| `docs/plan/planning/observations/BEO-TDS/katalog-elemente-grenzen-der-angleichung/` (`state.md`, `observation.md`) | update | Punkt 9: Träger und Zustand |
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

- Ein Text trägt mehrere Namen (zwei `DROP INDEX IF EXISTS` in einer Nachricht): der Planer muss die Anweisungen einzeln erkennen und bei einem Fehler der zweiten die erste nicht still behalten. — **Ausgang:** offen bis zur Closure.
- Indexnamen gelten je Tabelle, nicht je Schema: gleicher Name an zwei Tabellen darf den Treffer nicht verwechseln; der Katalog führt ihn möglicherweise anders. — **Ausgang:** offen bis zur Closure.
- Ein zweiter `ERROR`-Abschluss wie bei D5 (die Antwort auf einen Fehler im Lauf erscheint doppelt, wenn die Anweisung zweimal gemeldet wird). — **Ausgang:** offen bis zur Closure.
- Die Form am echten Server weicht von der Lesart ab (zum Beispiel meldet `IF EXISTS` ohne Treffer eine Information statt Erfolg, oder `DROP INDEX` auf einen Schlüsselindex ist abgewiesen). — **Ausgang:** offen bis zur Closure.
- Die öffentliche Fläche wächst (neuer Typ für den Index-Abwurf). Keine neue Fläche ist die Zusage dieses Plans; sonst entscheidet der Nutzer. — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — offene Beobachtungen sichten:** `BEO-TDS/katalog-elemente-grenzen-der-angleichung` (Herkunft, Punkt 9).

Alle berührten Sub-Areas GF.
