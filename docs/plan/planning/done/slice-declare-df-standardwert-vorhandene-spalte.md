# Slice slice-declare-df-standardwert-vorhandene-spalte: Text mit `DECLARE @df … EXEC sp_executesql` für einen Standardwert an einer vorhandenen Spalte

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

**Berührte Spec-Stellen:** SPEC-036 (erkannte Anweisungsformen, Form D, Absatz Grenzen) · SPEC-035 (Standardwert im Katalog) · SPEC-031 (ENVCHANGE bei `ALTER_TABLE`) · SPEC-045 und SPEC-059 (nur Zähler und Tags der Abdeckungsmatrix) · `TDS-FA-11.a` (Ablaufpunkt 9, Strukturänderung)

**Verantwortlich:** pt9912.

**Autor:** pt9912 (Entwurf in der Planner-Rolle, Closure von [slice-katalog-indizes-defaults-fremdschluessel](../done/slice-katalog-indizes-defaults-fremdschluessel.md)). **Datum:** 2026-11-11.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung**; die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks.

**Gestartet** am 2026-11-12 auf Freigabe des Nutzers. Die öffentliche Fläche wird nicht erweitert: Namen von Standardwert und Schlüssel bleiben intern.

**Ziel:** `schema migrate --execute` von d-migrate mit einem Standardwert an einer vorhandenen Spalte endet gegen den Testserver mit Exit 0 wie gegen den echten SQL Server. Heute sendet d-migrate dafür einen Text, der mit `DECLARE @df sysname, @sql nvarchar(max); SELECT @df = dc.name FROM sy…` beginnt und über `EXEC sp_executesql` ein `ALTER TABLE … DROP CONSTRAINT` mit dem gefundenen Namen des vorhandenen Standardwerts ausführt (das `ALTER TABLE … ADD CONSTRAINT [df_…] DEFAULT … FOR …` sendet d-migrate danach als eigenen Text, Form D3); der Text ist nicht erkannt, der Lauf endet mit Exit 5 (`Unsupported SQL.`, Messprotokoll [Zeile c1](../../../messungen/katalog-indizes-defaults-fremdschluessel-d-migrate-2026-11-09.md), im Verifikationsbericht zu [slice-katalog-indizes-defaults-fremdschluessel](../done/slice-katalog-indizes-defaults-fremdschluessel.md) reproduziert). Ein Standardwert in `CREATE TABLE` oder an einer neuen Spalte ist nicht betroffen.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- `ALTER TABLE … DROP CONSTRAINT [IF EXISTS]` (Punkt 2 des Registers) — kleiner, trifft den Validator zuerst: [slice-drop-constraint-form-d5](../done/slice-drop-constraint-form-d5.md).
- Allgemeines T-SQL (Variablen, Verzweigungen, Auswahl aus Katalogsichten) — der Slice erkennt den Text, den d-migrate 1.12.0 rendert, als feste Form; ein Interpreter für T-SQL ist nicht Gegenstand der Zusage in `TDS-FA-11`.
- Identität und berechnete Spalten — Grenze von [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), Entscheidung des Nutzers.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit.

Liefer-Punkte (drei):

- [x] **Messung** ([TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)): der vollständige Text, den d-migrate 1.12.0 für einen Standardwert an einer vorhandenen Spalte sendet (Mitschnitt), und die Antwort des echten SQL Servers darauf (Developer-Image, gepinnter Digest, EULA bestätigt, nur lokal): Tokenfolge, Zählung, Verhalten bei vorhandenem und fehlendem Standardwert; Messprotokoll in `docs/messungen/`.
- [x] **Bau** ([TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend)): der Text wird als feste Form erkannt (Parameter: Tabelle und Spalte), plant gegen den Katalog (der Standardwert der Spalte fällt; ohne Standardwert und an unbekannter Spalte oder Tabelle ein Erfolg ohne Änderung, wie am Server gemessen) und geht an den Integrator; Tests mit dem Handvektor des Mitschnitts und den Zeilen der Messung; Mutationen je Zusage.
- [x] **Beleg und Fortschreibung** ([TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate)): `schema migrate --execute` mit `default` an einer vorhandenen Spalte endet gegen den Testserver mit Exit 0 und `IDENTICAL`; `SPEC-036`, Nutzer-Doku und das Register [katalog-elemente-grenzen-der-angleichung](../observations/BEO-TDS/katalog-elemente-grenzen-der-angleichung/state.md) (Punkt 1) nachgezogen.

Nachweis-Pflichten für alle Punkte:

- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
      Beleg: [Review a70e06ef](../../../reviews/2026-11-12-a70e06ef-review-slice-declare-df-standardwert-vorhandene-spalte.md) (0 HIGH, 3 MEDIUM, 3 INFO) mit den Nachbesserungen `a23d0757` und `052ff677`.
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
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/DefaultDropForms.kt`, `Statement.kt`, `StatementParser.kt`, `StatementPlanner.kt`, `StructurePlanner.kt`, `SimulationChanges.kt` | neu / update | Form D7: Erkennung des Textes mit `DECLARE @df` (Textvergleich, kein Scanner), `Statement.DropDefault`, Planung gegen den Katalog; keine öffentliche Änderung |
| `src/test/kotlin/io/github/pt9912/tdswire/simulation/SimulationBackendDropDefaultTest.kt`, `DMigrateWire.kt`, `src/integrator/kotlin/io/github/pt9912/tdswire/integrator/DropDefaultEmbeddingTest.kt` | neu / update | Handvektor des Mitschnitts, Zeilen der Messung, Einbettungstest auf den Rohbytes, Mutationen je Zusage |
| `referenztest-dotnet/Vergleich/MessungBulkTests.cs`, `referenztest-dotnet/Mitschnitt/MessungBulkBelegTests.cs`, `referenztest-dotnet/Mitschnitt/messung/bulk-merge-katalog/<stufe>/struktur-standardwert-ersetzen.messung`, `tools/mk/vergleichslauf-d.mk` | update / neu | Szenario `struktur-standardwert-ersetzen` am echten Server (2022, 2025), Belegtests, Zahl der Szenarien |
| `docs/messungen/standardwert-ersetzen-d-migrate-2026-11-12.md`, `docs/messungen/referenzabgleich.md` | neu / update | Messprotokoll, Eingang des Baus |
| `spec/spezifikation.md` (`SPEC-036` Form D7, Änderungsprotokoll), `docs/user/testserver-manuell.md`, `docs/user/einbettung-und-betrieb.md`, `docs/user/referenz-konfiguration.md` | update | Zusage und Grenzen |
| `docs/plan/spec-abdeckung.md` | update (erzeugt) | `make spec-abdeckung`: Zähler SPEC-036, SPEC-045, SPEC-059 und Testklassen |
| `docs/plan/planning/observations/BEO-TDS/katalog-elemente-grenzen-der-angleichung/`, `docs/plan/planning/in-progress/roadmap.md` | update | Zustand und Beleg von Punkt 1, neuer Punkt 15 (Grenzen der Form), Marker der Roadmap |

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): Auslösung über das Register [katalog-elemente-grenzen-der-angleichung](../observations/BEO-TDS/katalog-elemente-grenzen-der-angleichung/state.md) (Punkt 1) oder über [slice-abnahme-d-migrate](../done/slice-abnahme-d-migrate.md), wenn der Validator einen Standardwert an einer vorhandenen Spalte verlangt; [slice-drop-constraint-form-d5](../done/slice-drop-constraint-form-d5.md) liegt in `done/` (Konflikte am Planer); die EULA für das gepinnte Developer-Image ist für diesen Lauf bestätigt; ein Verantwortlicher ist eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): der Scanner braucht Variablen und dynamisches SQL über die feste Form hinaus; Schnittvorschlag: Scanner-Erweiterung | Planung.
- `in-progress` → `open` (blockiert): der Nutzer bestätigt die EULA nicht, oder d-migrate rendert einen anderen Text als im Mitschnitt.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln.

DoD vollständig, `make gates` grün, das `schema migrate --execute` mit Standardwert an vorhandener Spalte endet mit Exit 0, Review und Verifikation liegen vor, Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — jedes Risiko bekommt genau einen Ausgang.

- Der Text hängt an der Version von d-migrate (1.12.0); eine andere Version rendert ihn anders. — **Ausgang:** **weiter offen:** der Wortlaut ist der von 1.12.0 (Digest des Werkzeugs gepinnt, Test mit dem Wortlaut des Mitschnitts, Belegtests am echten Server); rendert eine andere Version den Text anders, endet der Lauf mit `TDS_UNSUPPORTED_SQL` (Exit 5). Träger: Register `BEO-TDS/katalog-elemente-grenzen-der-angleichung` Punkt 15 (d) und (e), Trigger: eine neue Version von d-migrate in der Spezifikation oder die Abnahme [slice-abnahme-d-migrate](../done/slice-abnahme-d-migrate.md).
- Die feste Form trägt nur den Text von d-migrate; ein anderer Client mit eigenem `DECLARE` bleibt abgewiesen. — **Ausgang:** **weiter offen** als Zusage: ein fremder Text mit `DECLARE` ist nicht erkannt und endet mit `TDS_UNSUPPORTED_SQL` (in der Nutzer-Doku genannt, `SPEC-036` Form D7 (d)); ein Interpreter für T-SQL ist nicht Gegenstand von [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend). Träger: Punkt 15 (d) des Registers.

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register; `grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln.

- **Was hat funktioniert:** Die Messung am echten Server vor dem Bau trug: 27 Anfragen je Instanz (2022, 2025) mit gleicher Tokenfolge, aus denen die feste Form, die Antworten ohne Treffer und die Fehler 1781 und 1750 entstanden; der Verifier las die Rohbytes mit eigenem Dekoder und fand sie gleich den Tabellen des Messprotokolls und den Handvektoren der Tests. Die Fläche blieb unverändert (Option A, `Statement.DropDefault` und `DefaultDropForms` sind `internal`), die vier Mutationen des Verifiers (M1 bis M4) sind rot. d-migrate 1.12.0 gegen den Testserver: sechs von sechs Schritten Exit 0 und `IDENTICAL` (Standardwert neu, geändert, weggenommen, zweiter Lauf ohne Anweisung).
- **Was ging anders als geplant:** (1) Die Nachbesserung nach dem Review behob F-1 und F-2 nur an den genannten Stellen; die Verifikation fand vier weitere Fundstellen derselben Aufzählung (V-1, MEDIUM), behoben in `052ff677` (zwei davon KDoc in `src/main`, Kommentare, nach dem Lauf von `make clean-build`; der Tests-Lauf deckt sie nicht ab, `make gates` des Planners schon). (2) Das Ziel in §1 nannte zuerst das `ADD CONSTRAINT` statt des `DROP CONSTRAINT` des Textes (F-4). (3) Drei Abweichungen vom echten Server blieben bei der Lieferung ohne Entscheid (V-3); der Nutzer entschied am 2026-11-12 (siehe unten). (4) Die Läufe gegen den Testserver (Tabelle B des Messprotokolls) sind von Hand gefahren, die Skripte liegen nicht im Repo; der Verifier fuhr sie mit eigenen Soll-Dateien nach (INFO-1). (5) `make gates FORCE=1` und `make referenztest-dotnet` des Verifiers kamen aus dem Docker-Cache; der frische Beleg ist das Log von `make clean-build` (1383 `PASSED`) und der Frischlauf der .NET-Stage (160 von 160).
- **Zuordnung Messung | Festlegung:** Gemessen (Ablagen `struktur-standardwert-ersetzen.messung`, 2022 und 2025): der Text entfernt den Standardwert der Spalte, auch mit eigenem Namen; ohne Treffer, an unbekannter Spalte und Tabelle ein Erfolg ohne Änderung; die Antwortfolge (a); 1781 und 1750 beim zweiten Standardwert (b); `ENVCHANGE` Typ 8 unter `IMPLICIT_TRANSACTIONS` (c); Schreibweise ohne Beachtung der Groß- und Kleinschreibung (Voreinstellung der Kollation, nicht eigens erhoben). Festlegung (Setzung, so gekennzeichnet): der Leerraum im Text ist frei; die Einschränkung von Tabelle (ohne Schema, `[A-Za-z0-9_#$]`) und Spalte (Textliteral) auf die Form von d-migrate; der Abschluss `0x00d8` ohne `COUNT` für alle Fälle des Textes.
- **Entscheide des Nutzers 2026-11-12 zu den Grenzen (V-3 gegenstandslos):** (1) Antwortfolge: das Zielbild ist angepasst, die Folge ist eine ausdrückliche Grenze der Zusage in `SPEC-036` Form D7 (a), Trigger: ein Client, der die Zwischenabschlüsse auswertet; Vorbehalt, nicht untersucht, ob die vorhandenen Mittel die Folge ausdrücken könnten. (2) 1781 und 1750 (Bibliothek 2714) sowie das zweite ERROR 3727 (Punkt 10): der Code wird angeglichen, in einem eigenen Slice. (3) `ENVCHANGE` Typ 8 unter `IMPLICIT_TRANSACTIONS`: das Zielbild ist angepasst, eine gemeinsame Grenze der Transaktionszusage für alle Strukturanweisungen an einer Stelle (`SPEC-031`); die Fundstellen zeigen darauf. Das Änderungsprotokoll von `spec/spezifikation.md` nennt die Änderung des Zielbilds mit Datum; das Lastenheft ist nicht berührt (`TDS-FA-11`, `TDS-FA-12`, `TDS-QA-01` fordern weder Antwortfolge noch Fehlernummern noch `ENVCHANGE` bei Strukturanweisungen), keine ADR geändert.
- **Befunde von Review und Verifikation und ihr Ausgang:** Review: 0 HIGH, 3 MEDIUM (F-1 Aufzählungen, F-2 Grenzen an allen Fundstellen, F-3 Plan-Tabelle), 3 INFO (F-4 bis F-6); alle behoben in `a23d0757`. Verifikation: 0 HIGH, 1 MEDIUM, 2 LOW, 4 INFO. V-1 (MEDIUM, vier weitere Fundstellen der Aufzählung): behoben in `052ff677`. V-2 (LOW, Änderungsprotokoll ohne (c)): behoben in `052ff677`. V-3 (LOW, offener Entscheid nicht überall ausgewiesen): durch die Entscheide gegenstandslos, Spezifikation, Nutzer-Doku, Register und Messprotokoll nennen den neuen Stand gleich. V-4 (INFO, Grenze (e) in der Nutzer-Doku): behoben in `052ff677`. V-5 (INFO, Welle und Abnahme-Slice führten den Slice als „zurückgestellt“): in dieser Closure berichtigt. INFO-1 und INFO-2: siehe „Was ging anders“ und die Aussage zur Kollation.
- **Steering-Loop-Eintrag:** Kein Eintrag verkörpert, daher kein `liegt in`; gezählt, nicht verkörpert: (1) *Die Nachbesserung nach einem Fund zur Aufzählung folgt der Liste, nicht den genannten Stellen:* zum zweiten Mal in Folge (Review F-1/F-2, danach Verifikation V-1 mit vier weiteren Fundstellen) lief die Fortschreibung einer Aufzählung (hier: alle Strukturformen, gesucht mit `git grep -n "DROP CONSTRAINT" -- spec src/main docs/user`) über die im Fund genannten Stellen und ließ gleichartige stehen; die Regel „Fortschreibung lässt die zweite Fundstelle stehen“ ist im Reviewer-Skill verkörpert (Prüfung der Rolle Review), sie fing den Fund; was fehlt, ist der Wächter auf der Seite des Implementers, ein Suchschritt in der Nachbesserung. Neues Auftreten in `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` (Evidence dieses Slice). (2) *Eine Entscheidung steht an jeder Fundstelle der Aussage oder an einer, auf die alle zeigen:* die gemeinsame Grenze der Transaktionszusage (Entscheid 3) steht an einer Stelle (`SPEC-031`), SPEC-036 Form D7 (c), Register und Nutzer-Doku zeigen darauf; gleiche Beobachtung, im selben Register geführt. Die Zähler folgen aus den Dateien, den Lese-Schritt macht die Welle-Closure. Keine benannte Spec-Lücke: die Lücke „Entscheid steht aus“ (V-3) ist durch die Entscheide des Nutzers geschlossen, die Grenzen stehen in der Spezifikation.
- **Beobachtungs-Register (`../observations/`):** Fortgeschrieben mit `evidence/slice-declare-df-standardwert-vorhandene-spalte.md`: [BEO-TDS/katalog-elemente-grenzen-der-angleichung](../observations/BEO-TDS/katalog-elemente-grenzen-der-angleichung/state.md) (Punkt 1 gebaut und verifiziert, Punkt 15 neu und entschieden, Punkte 10 und 13 (a) fortgeschrieben; Punkt 11 bleibt offen bis zur Abnahme) und [BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen](../observations/BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen/state.md).
- **Folge-Slices:** [slice-zweites-error-folgefehler-angleichen](../open/slice-zweites-error-folgefehler-angleichen.md) (Fehler 1781 und 1750 und das zweite ERROR 3727 an den echten Server angleichen; Entscheid des Nutzers 2026-11-12) — eine Datei in `open/`, Mitglied von [welle-testserver-manuell](../welle-testserver-manuell.md) (die Welle gleicht den Server dem echten SQL Server an und schließt erst, wenn alle ihre Slices in `done/` liegen; der Trigger „spätestens die Wellen-Closure“ ist diese Bedingung), Eintrag im Drift-Log der Roadmap.
- **Risiken aus §6:** beide mit Ausgang eingetragen (weiter offen: 1 (Version von d-migrate), 2 (fremder Text mit `DECLARE`)).
- **Verifikation:** [Verifikationsbericht](../../../reviews/2026-11-12-a23d0757-verifikation-slice-declare-df-standardwert-vorhandene-spalte.md) auf `a23d0757`: konform; `make clean-build` Exit 0 (558 s, 1383 `PASSED`, `SimulationBackendDropDefaultTest` 8 von 8, `DropDefaultEmbeddingTest` 1 von 1), `make e2e` Exit 0 (8 Stufen, 383 s), `make gates FORCE=1` Exit 0 (36 s, Docker-Cache), `make referenztest-dotnet` Frischlauf 160 von 160, vier von vier Mutationen rot, d-migrate 1.12.0 gegen den Testserver sechs Schritte Exit 0 und `IDENTICAL`. Nach der Verifikation geändert: `052ff677` (Kommentare in `src/main` und Doku) und diese Closure (Dokumente, Register, Spezifikation, Nutzer-Doku, Messprotokoll); Läufe des Planners auf dem End-Stand stehen im Bericht an den Aufrufer.
- **Drei Paarungen:** Das Repo führt Wellen; die Paarungen prüft die Closure von [welle-testserver-manuell](../welle-testserver-manuell.md). Hier geprüft: *Anker*: kein Anker gesetzt (kein `liegt in`). *Folge-Slice*: [slice-zweites-error-folgefehler-angleichen](../open/slice-zweites-error-folgefehler-angleichen.md) besteht als Datei in `open/`. *Register*: beide genannten Kennungen bestehen als Verzeichnis mit nicht leerem `evidence/`.

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung.

**Vorgelagert — Sub-Area-Wahl prüfen:** Eine Sub-Area (`*`, Kürzel `TDS`); keine Aufteilung nötig.

**Vorgelagert — offene Beobachtungen sichten:** `BEO-TDS/katalog-elemente-grenzen-der-angleichung` (Herkunft, Punkt 1), `BEO-TDS/messung-am-echten-sql-server-fehlt` (Eingang der Messung).

Alle berührten Sub-Areas GF.
