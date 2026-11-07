# Slice slice-katalog-indizes-defaults-fremdschluessel: Katalogsichten für Indizes, Standardwerte, Prüfbedingungen und Fremdschlüssel aus den mitgeteilten Tabellen, am Beispiel von d-migrate

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-testserver-manuell

**Bezug:** [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md)

**Berührte Spec-Stellen:** SPEC-035 (Tabellenbeschreibung, Katalog der Spalten: was `SimColumn` und `SimTable` führen) · SPEC-036 (Katalogabfragen für Eindeutigkeiten, Fremdschlüssel, Indizes, Prüfbedingungen; DDL-Formen mit Fremdschlüssel und Index) · SPEC-059 (falls die Fläche wächst) · SPEC-040

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-06.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Die Bibliothek beantwortet die Katalogabfragen von d-migrate für Eindeutigkeiten, Fremdschlüssel, Indizes, Standardwerte und Prüfbedingungen aus den ihr mitgeteilten Tabellen (heute antworten sie leer), sodass `schema reverse` und `schema compare` mit einem Soll, das diese Elemente führt, gegen den Testserver das Ergebnis des echten SQL Servers zeigen (Maßstab: „Spezifikation ist Zielbild, der echte SQL Server ist die Referenz“, Nutzer 2026-11-06).

**Herkunft.** Verifikation V-3 von [slice-katalog-und-ddl-zustand](../done/slice-katalog-und-ddl-zustand.md): dessen Liefer-Punkt 1 sagte „Indizes, Defaults, Prüfbedingungen“ aus den Tabellen beantwortet; geliefert sind sie leer (Setzung, `SimColumn` führt sie nicht), und kein offener Slice trug Fremdschlüssel, Indizes oder Standardwerte. Dieser Slice ist der Träger (Nutzerentscheid 2026-11-06, „deine Empfehlungen“). Das Messprotokoll [katalog-und-ddl-zustand-2026-11-06](../../../messungen/katalog-und-ddl-zustand-2026-11-06.md) nennt „Soll-Dateien mit Fremdschlüsseln, Indizes oder Standardwerten“ als nicht gemessen.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Die Messung der Antworten am echten SQL Server — [slice-qa-vergleichslauf-d-messungen-bulk-merge](../in-progress/slice-qa-vergleichslauf-d-messungen-bulk-merge.md) (Liefer-Punkt 2, Katalog); sie ist der Eingang dieses Slice, nicht sein Gegenstand (EULA, Nutzer-Lauf).
- Schreiben von Zeilen (Einfügen, Massenladen, MERGE) — [slice-schreibpfad-bulk-load](slice-schreibpfad-bulk-load.md) und [slice-schreibpfad-merge-output](slice-schreibpfad-merge-output.md).
- Sichten, Sequenzen, Routinen, Partitionen und Volltextindizes mit Inhalt — kein Lauf des Solls stellt sie mit Inhalt; sie bleiben leere Resultsets mit den Spalten ([slice-katalog-und-ddl-zustand](../done/slice-katalog-und-ddl-zustand.md) §1).
- Identität (`is_identity`) und berechnete Spalten — Grenze von [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) (Identität ohne Feld); eine Aufnahme ist eine eigene Entscheidung des Nutzers.
- Referentielle Integrität im Bestand (Fremdschlüssel erzwingen, Indizes nutzen) — der Katalog beschreibt, der Testserver prüft nichts.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **Bibliothek, Modell und Katalog** ([TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend)): `SimColumn`/`SimTable` (oder ein Mitglied, das die Entscheidung benennt) führen Eindeutigkeiten, Fremdschlüssel, Indizes, Standardwerte und Prüfbedingungen; die Katalogabfragen von d-migrate 1.12.0 für diese Elemente werden daraus beantwortet; ein Test je Abfragegruppe liest die Antwort mit Handvektoren aus dem Mitschnitt (`DMigrateWire.kt`) und, soweit vorhanden, mit den Zeilen der Messung am echten Server.
- [ ] **Bibliothek, Strukturänderung** ([TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend)): die DDL-Formen, mit denen `schema migrate --execute` diese Elemente anlegt (Fremdschlüssel in `CREATE TABLE`, `ALTER TABLE ... ADD CONSTRAINT`, `CREATE INDEX`), werden erkannt und gehen nach der Entscheidung an den Integrator; der Katalog zeigt danach die gemeldete Struktur. Welche Formen d-migrate sendet, ist am Soll zu messen, nicht zu raten.
- [ ] **Testserver und Beleg** ([TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate)): der Testserver hält die Elemente im Arbeitsspeicher und liest sie aus der Beschreibungsdatei; `schema reverse`, `schema compare` und `schema migrate --execute` von d-migrate gegen ein Soll mit Fremdschlüssel, Index, Standardwert und Prüfbedingung enden mit Exit 0 samt Nachprüfung; die Ausgabe steht mit Rechner, Last und Image-Digest in einem Messprotokoll.

Nachweis-Pflichten für alle Punkte:

- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Haken der Liefer-Punkte setzt der Planner nach der Verifikation, nicht der Implementer (`BEO-TDS/haken-vor-verifikation-gesetzt`).
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
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/CatalogForms.kt`, `CatalogAnswers.kt`, `SimModel.kt`, `StructureParts.kt`, `StatementParser.kt`, `StatementPlanner.kt` | update | Antworten statt leerer Resultsets, Modell, DDL-Formen |
| `testserver/kotlin/.../TableStore.kt`, `SimDescriptionReader.kt`, `testserver/resources/manuell/sim.yaml` | update | Integrator-Anteil: Bestand und Beschreibung |
| `src/test/kotlin/.../simulation/` (Katalog-, Struktur-, Formentests, `DMigrateWire.kt`), `testserver/test/kotlin/`, `src/integrator/kotlin/.../FlaecheEmbeddingTest.kt` | update | Handvektoren, Antworten, Bestand, Namen von außen |
| `spec/spezifikation.md` (`SPEC-035`, `SPEC-036`, `SPEC-040`, `SPEC-059`), `docs/user/testserver-manuell.md`, Messprotokoll | update / neu | Zusage, Formen, Läufe |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf. **Wachsen öffentliche Namen** (ein Typ für Fremdschlüssel oder Index), listet [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Entscheidung 4 jeden neuen Namen; dann ist vor dem Bau eine Entscheidung nötig (Ergänzung oder Folge-ADR), und `make flaeche` führt die Prüfung.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-katalog-und-ddl-zustand](../done/slice-katalog-und-ddl-zustand.md) liegt in `done/`, die Messung der Katalogantworten ([slice-qa-vergleichslauf-d-messungen-bulk-merge](../in-progress/slice-qa-vergleichslauf-d-messungen-bulk-merge.md), Liefer-Punkt 2, Zeile (e)) liegt vor — oder der Nutzer entscheidet, ohne Messung zu bauen (dann bleibt die Antwort Setzung, nachträglich verglichen) — und ein Verantwortlicher ist eingetragen. Dieser Slice blockiert die Abnahme nicht, solange das Soll von [slice-abnahme-d-migrate](slice-abnahme-d-migrate.md) keine dieser Elemente führt; führt es sie, startet die Abnahme erst danach.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte; Schnittvorschlag: Fremdschlüssel und Eindeutigkeiten | Indizes, Standardwerte, Prüfbedingungen.
- `in-progress` → `open` (blockiert): die Fläche wächst und die Entscheidung über die neuen Namen steht aus; oder die Messung am echten Server widerspricht der Setzung des Planners über die Form.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln.

DoD vollständig, `make gates` grün, die Ausgabe der d-migrate-Läufe liegt vor, Review und Verifikation liegen vor, Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **Neue öffentliche Namen.** Fremdschlüssel und Index brauchen ein Modell; es kann ein neuer Typname werden, den [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) nicht listet. — **Ausgang:** offen bis zur Closure.
- **Form der Antworten ohne Messung.** Ohne die Messung am echten Server ist die Antwort Setzung (Spalten, Reihenfolge, Typen). — **Ausgang:** offen bis zur Closure.
- **DDL-Formen von d-migrate ungemessen.** Welche Anweisungen `schema migrate --execute` für Fremdschlüssel, Indizes und Standardwerte sendet, ist nicht gemessen. — **Ausgang:** offen bis zur Closure.

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
§Ziel-Form: Sub-Area-Modus-Begründung.

**Vorgelagert — Sub-Area-Wahl prüfen:** Eine Sub-Area (`*`, Kürzel `TDS`); keine Aufteilung nötig.

**Vorgelagert — offene Beobachtungen sichten:** `BEO-TDS/messung-am-echten-sql-server-fehlt` (Eingang), `BEO-TDS/offene-punkte-ohne-traeger` (Herkunft: Verifikation V-3), `BEO-TDS/dod-wortlaut-ueber-der-bauart` (der Wortlaut der DoD folgt der Lieferung).

Alle berührten Sub-Areas GF.
