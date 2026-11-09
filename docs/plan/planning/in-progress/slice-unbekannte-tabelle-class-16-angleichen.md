# Slice slice-unbekannte-tabelle-class-16-angleichen: Antwort auf eine unbekannte Tabelle (208) mit Class 16 und offener Verbindung (M-15)

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-testserver-manuell

**Bezug:** [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0007](../../adr/0007-fehler-und-attention.md) (`Accepted`; eine Schärfung wäre eine neue ADR, siehe Liefer-Punkt 1)

**Berührte Spec-Stellen:** spezifikation.md §4 (Fehlertabelle, Zeile `TDS_SIM_UNKNOWN_OBJECT`; Grundregel, „Einzelzuordnung offen“) · SPEC-038 · SPEC-036 („Grenzen der Angleichung“ des `MERGE`) · SPEC-001 (Satz zur Zuordnung der Codes, falls er die Einzelzuordnung als offen führt).

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912 (Entwurf in der Planner-Rolle, aus der Verifikation V-2 von [slice-merge-antwort-an-messung-angleichen](../done/slice-merge-antwort-an-messung-angleichen.md)). **Datum:** 2026-11-09.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Der Fehler 208 (unbekannte Tabelle) des Testservers stimmt in Class und Verbindungsfolge mit dem echten SQL Server überein: Class 16 und offene Verbindung statt Class 20 und schließender Verbindung (M-15, `R05` im [Messprotokoll](../../../messungen/referenzabgleich.md#messreihe-zu-massenladen-merge-katalog-und-strukturänderung-messungen-vom-2026-11-07)); die Spezifikation ist Zielbild, der echte SQL Server die Referenz.

**Herkunft.** Verifikation V-2 von [slice-merge-antwort-an-messung-angleichen](../done/slice-merge-antwort-an-messung-angleichen.md): Nummer 208 und Tokenfolge stimmen, die Bibliothek sendet aber Class 20 und schließt die Verbindung, der Server Class 16 und lässt sie offen. Register [`BEO-TDS/unbekannte-tabelle-class-20-statt-16-am-server`](../observations/BEO-TDS/unbekannte-tabelle-class-20-statt-16-am-server/state.md) (dieser Slice ist sein Träger). Die Spezifikation führt die Zuordnung als „offen, nicht entschieden“ (§4, Grundregel). Standardantwort des Nutzers vom 2026-11-09 auf die Empfehlung: die Spezifikation folgt dem echten Server (umkehrbar).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Die Zuordnung der übrigen Codes der Grundregel (`TDS_UNSUPPORTED_TYPE`, Spaltenfall 207): kein gemessener Anlass; ein Punkt gehört dem Slice, der ihn misst.
- Das Verhalten des Referenzclients und von mssql-jdbc nach Class 16 über den Fall 208 hinaus (`AGENTS.md` §3.8: keine Aussage über andere Clients als gemessen).
- Reihenfolge der Antwort im Fehlerfall und der Fall „derselbe Schlüssel zweimal in der Quelle“ des `MERGE`: Register [`BEO-TDS/merge-antwort-grenzen-der-angleichung`](../observations/BEO-TDS/merge-antwort-grenzen-der-angleichung/state.md), eigener Anlass.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **(1) Entscheidung** ([TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)): ob `TDS_SIM_UNKNOWN_OBJECT` auf Class 16 und offene Verbindung gestellt wird. Empfehlung und Standardantwort des Nutzers: ja, die Spezifikation folgt dem echten Server. Ist dafür eine Schärfung von [ADR-0007](../../adr/0007-fehler-und-attention.md) nötig (Zuordnung der Codes zu Class und Verbindungsfolge), ist sie ein eigener Liefer-Punkt des Architects (neue ADR mit `Schärft:` bzw. `Supersedes`, `AGENTS.md` §3.5); Prüfung vorab: welche Tests und Spezifikationsstellen setzen Class 20 für diesen Code voraus (heute ungeprüft).
- [ ] **(2) Bau und Tests:** `TDS_SIM_UNKNOWN_OBJECT` sendet Class 16, die Verbindung bleibt offen; ein Test gegen den Verlauf ERROR, RETURNSTATUS, DONEPROC (Fehlerbit) mit Class im ERROR und Folgeanfrage auf derselben Verbindung, ein Einbettungstest `integrator` (`MergeFehlerEmbeddingTest` nimmt die Class 20 heraus); Mutation je Zusage über enge Testklassen.
- [ ] **(3) Spezifikation, Doku, Register:** §4 (Zeile `TDS_SIM_UNKNOWN_OBJECT`, Grundregel), `SPEC-038`, `SPEC-036` („Grenzen der Angleichung“ des `MERGE` ohne die Class-Abweichung), Messprotokoll (Zeile M-15), `docs/user/`; Register `unbekannte-tabelle-class-20-statt-16-am-server` fortgeschrieben; die Plan-Tabelle §3 und die Risiken §6 dieses Slice gegen den Diff ergänzt.

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
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `docs/plan/adr/` (neue ADR zu [ADR-0007](../../adr/0007-fehler-und-attention.md)) | entfällt | [ADR-0007](../../adr/0007-fehler-und-attention.md) setzt Class 20 nur für `TDS_PROTOCOL_ERROR` und `TDS_INTERNAL_ERROR`; die Zuordnung jedes weiteren Codes legt sie dem Fehler-Mapping zu (Kontext „Class der eigenen Fehlercodes“, Offene Punkte); keine Schärfung nötig |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/error/ErrorCode.kt` (`TDS_SIM_UNKNOWN_OBJECT`, KDoc des Enum) | update | Class 16, Verbindung bleibt offen; Herkunftsstufe „gemessen“ (Zeile M-15, Schritt `fehler-vor-zeilen`), Text bleibt Festlegung |
| `src/test/kotlin/.../protocol/error/ErrorMapperTest.kt` | update | Handvektor ERROR 208 Class 16, `OPEN`; Gruppen-Test führt den Code bei den offenen |
| `src/integrator/kotlin/.../MergeFehlerEmbeddingTest.kt` | update | Class 16 im ERROR, Folgeanfrage auf derselben Verbindung |
| `referenztest-dotnet/Kompatibilitaet/SzenarienTests.cs`, `SynchronitaetTests.cs` | update | 208 mit Class 16 und offener Verbindung; der Test „Fehler der Class 20“ nimmt den `xml`-Parameter (`TDS_UNSUPPORTED_TYPE`, 50003) als Auslöser |
| `referenztest-dotnet/Vergleich/live-ausnahmen.tsv`, `mitschnitt-ausnahmen.tsv` | update | Ausnahme A-3 nur noch im Text; die Zeilen `fehler-vor-zeilen/02/*` entfallen |
| `spec/spezifikation.md` (§4 Grundregel-Satz, Zeile `TDS_SIM_UNKNOWN_OBJECT`, `SPEC-038`, `SPEC-036` Form M, Änderungsverzeichnis) | update | Zielbild folgt dem Messwert |
| `docs/messungen/referenzabgleich.md` (A-3, Zeile M-13 bis M-15 der Bewertung), `docs/plan/kompatibilitaetsmatrix.md`, `docs/plan/e2e-abdeckung.md`, `docs/user/` (`referenz-konfiguration.md`, `einbettung-und-betrieb.md`), Register `BEO-TDS/unbekannte-tabelle-class-20-statt-16-am-server` | update | Ist-Zustand, Stand |

Die Liste ist gegen den Diff vervollständigt.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-merge-antwort-an-messung-angleichen](../done/slice-merge-antwort-an-messung-angleichen.md) in `done/`; Entscheid des Nutzers zu Liefer-Punkt 1 bestätigt (Standardantwort vom 2026-11-09 umkehrbar); kein anderer Bau-Slice in `in-progress/` (WIP 1).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß): die Prüfung der Tests, die Class 20 voraussetzen, zeigt breite Wirkung; Schnitt: Liefer-Punkt 1 als eigener Entscheidungs-Slice, Bau danach.
- `in-progress` → `open` (blockiert): der Nutzer entscheidet gegen Class 16; dann bleibt die Abweichung als Grenze in `SPEC-036` und im Register, der Slice entfällt mit `Gegenstand:` in §7.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle Liefer-Punkte der DoD erledigt, `make gates` grün, der Review-Report liegt vor, die Zeile M-15 des Messprotokolls trägt keine Class-Abweichung mehr, das Register `unbekannte-tabelle-class-20-statt-16-am-server` ist auf den Stand gezogen, die Closure-Notiz trägt den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **Tests oder Spezifikationsstellen setzen Class 20 für diesen Code voraus** (Class 20 schließt die Verbindung, Folgeanfragen setzen sie voraus). Befund des Implementers: `ErrorMapperTest` (Handvektor, Gruppen-Test), `MergeFehlerEmbeddingTest`, `SzenarienTests.cs` und `SynchronitaetTests.cs` (der Class-20-Test nutzte 208 als Auslöser, jetzt den `xml`-Parameter), die Ausnahmelisten des Live- und des Mitschnitt-Laufs, Kompatibilitätsmatrix, `e2e-abdeckung.md` und `docs/user/`; Spezifikation §4, `SPEC-036`, `SPEC-038`. Die .NET-Tests und die Ausnahmelisten sind vom Implementer nicht gefahren (`make e2e`, `make vergleichslauf-d` gehören dem Verifier) — **Ausgang:** offen bis zur Closure.
- **Verhalten der Clients nach Class 16:** der Referenzclient und mssql-jdbc sind für 208 nicht gemessen; die Messung R05 bezieht sich auf den Server, nicht auf die Clients — **Ausgang:** offen bis zur Closure.

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register · `grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln.

Wird bei der Closure gefüllt.

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung.

**Vorgelagert — Sub-Area-Wahl prüfen:** `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); eine eigene Sub-Area besteht nicht.

**Vorgelagert — offene Beobachtungen sichten:** [`BEO-TDS/unbekannte-tabelle-class-20-statt-16-am-server`](../observations/BEO-TDS/unbekannte-tabelle-class-20-statt-16-am-server/state.md), [`BEO-TDS/client-schliesst-bei-class-20-selbst`](../observations/BEO-TDS/client-schliesst-bei-class-20-selbst/state.md), [`BEO-TDS/spec-setzung-widerspricht-messung-am-echten-server`](../observations/BEO-TDS/spec-setzung-widerspricht-messung-am-echten-server/state.md).

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
