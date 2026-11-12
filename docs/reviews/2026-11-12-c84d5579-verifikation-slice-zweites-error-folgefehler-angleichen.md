# Verifikationsbericht: slice-zweites-error-folgefehler-angleichen (HEAD c84d5579)

* Rolle: Verifier (Modul 11), frischer Kontext. Gegenstand: [slice-zweites-error-folgefehler-angleichen](../plan/planning/in-progress/slice-zweites-error-folgefehler-angleichen.md). Maßstab: DoD §2, Plan §3, [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), [ADR-0038](../plan/adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md) (Abschluss im Fehlerfall).
* Stand: Zweig `worktree-agent-a78a494dfdd9c96ed`, HEAD `c84d5579`, Basis `main` `e5e48313`; Arbeitsbaum sauber. Am Produktivcode nichts geändert. Haken in der DoD: keine gesetzt, und ich setze keine.
* Eingang: Erst-Review [2026-11-12-952af14c-review-slice-zweites-error-folgefehler-angleichen.md](2026-11-12-952af14c-review-slice-zweites-error-folgefehler-angleichen.md), Re-Review [2026-11-12-f37c39e6-review-slice-zweites-error-folgefehler-angleichen-nachbesserung.md](2026-11-12-f37c39e6-review-slice-zweites-error-folgefehler-angleichen-nachbesserung.md).

## Urteil

**Konform.** Beide Liefer-Punkte der DoD sind belegt, kein HIGH, kein MEDIUM. Die Handvektoren stimmen mit den Ablagen der Messung überein, die Setzungen sind als Setzung gekennzeichnet, die öffentliche Fläche ist unverändert. Offen sind nur Aufgaben des Planners: die zwei LOW des Re-Reviews (R-1 Plan-Tabelle ohne `SPEC-031`, F-3 `docs/plan/spec-abdeckung.md`), Haken, Closure-Notiz, Ausgänge der Risiken §6, Register (Punkte 10 und 15 (b)) und Paarungen.

## Eigene Läufe (Belege)

| Lauf | Ergebnis |
|---|---|
| Handvektoren gegen die Ablagen | siehe (a) |
| `make flaeche` | Exit 0 (Klasse `immer`, auch in `make gates`) |
| `make gates` | Exit 0, 20 s: Klassen `werkzeug` und `tests` ohne Beleg gelaufen, Docker-Cache (88 Schichten `CACHED`, Stage `test` aus dem Cache), `stempel-gruppe` über den Stempel übersprungen |
| `make clean-build` | Exit 0, 572 s (`--no-cache`); 1391 Testfälle `PASSED`, keiner `FAILED`; die neuen Klassen `ResponseFollowUpTest` (6 Fälle), `DropConstraintEmbeddingTest`, `DropDefaultEmbeddingTest` und `ErrorMapperTest` laufen frisch |
| `make e2e` | Exit 0, 8 Stufen grün, 392 s (Stufe 1 82 s, Stufe 2 108 s, Stufe 3 59 s, Stufe 4 54 s, Stufe 5 18 s, Stufe 6 19 s, Stufe 7 38 s, Stufe 8 14 s) |
| d-migrate gegen den Testserver | siehe (d) |
| Mutationen je Zusage | nicht gefahren (Pflicht des Implementers, nicht des Verifiers) |

Gesamtlaufzeit rund 35 Minuten; größte Posten: `make clean-build` 572 s, `make e2e` 392 s (davon Stufe 2 und Stufe 1 zusammen 190 s), d-migrate-Lauf rund 2 Minuten. Keine künstliche Last; die Läufe liefen nacheinander.

## (a) Handvektoren gegen die Ablagen

Die Ablagen `referenztest-dotnet/Mitschnitt/messung/bulk-merge-katalog/{2022,2025}/struktur-standardwert-ersetzen.messung` und `struktur-drop-constraint.messung` sind Hex-Mitschnitte; ich habe die ERROR-Tokens (`0xAA`: Nummer, State, Class, Text) mit einem eigenen Dekoder aus den Server-Nachrichten gelesen und nicht aus dem Protokolltext übernommen.

| Szenario, Zeile | 2022 und 2025 (gleich) | Code (`ErrorCode.kt`) |
|---|---|---|
| `struktur-standardwert-ersetzen`, 20 | 1781 State 1 Class 16 „Column already has a DEFAULT bound to it.“; danach 1750 State 0 Class 16 „Could not create constraint or index. See previous errors.“ | `TDS_SIM_COLUMN_HAS_DEFAULT`: 1781, 16, State 1, derselbe Text, `followUp` 1750, 16, State 0, derselbe Text: gleich |
| `struktur-drop-constraint`, 22, 32, 38, 44, 50 | 3728 State 1 Class 16; danach 3727 State 0 Class 16 „Could not drop constraint. See previous errors.“ | `TDS_SIM_NOT_A_CONSTRAINT`: 3728, State 1, `followUp` 3727, State 0, Class 16: gleich |
| `struktur-drop-constraint`, 28 | 3733 State 2 Class 16; danach 3727 State 0 Class 16 | `TDS_SIM_CONSTRAINT_OTHER_TABLE`: 3733, State 2, `followUp` 3727: gleich |

Die Zeilen, die KDoc und `SPEC-036` nennen (05, 08, 10, 13, 16, 19), habe ich nicht gegen eine Zeilennummerierung abgeglichen; meine Zeilen oben sind Zeilen der Datei, und die Zählung des Szenarios (Antwort 1 bis n) kann abweichen. Die Anzahl der Fälle stimmt: sechs Antworten mit 3728 oder 3733 (22, 28, 32, 38, 44, 50) tragen alle das zweite ERROR. Beobachtung am Rand (INFO): Zeile 18 (Mitschnitt) zeigt auch hinter 3725 ein 3727; das Register nennt 3725 als nicht geplant (Punkt 12), `SPEC-036` behauptet dazu nichts.

## (b) Setzungen

* RPC-Folge: `SPEC-031` „Im RPC steht der zweite ERROR ebenso hinter dem ersten … (Setzung, nicht gemessen)“; `SPEC-035` (Strukturabweisung) „im RPC, im Massenladen und im `MERGE` Setzung“; `SPEC-036` Form D5 „im RPC gilt es als Setzung ebenso“, Form D7 (b) „im RPC gilt dieselbe Folge als Setzung“; KDoc des Tests `ResponseFollowUpTest` trägt „Setzung (nicht gemessen, die Messungen sind Batch)“. Änderungsprotokoll der Spezifikation: „für den RPC gilt dieselbe Folge als Setzung“.
* RETURNSTATUS = Nummer des letzten ERROR: `SPEC-031` mit „(Setzung, nicht gemessen)“; Code `trailer.record((mapped.followUp ?: mapped.message).number, …)` entspricht dem Wortlaut.
* Massenladen und `MERGE`: `SPEC-031` „Die Folge ist nicht gemessen (Setzung: beide ERROR vor dem INFO 3621 und dem Abschluss)“.
* Nutzer-Doku (`docs/user/`): die Aussagen „der echte SQL Server antwortet mit denselben Nummern und danach mit 3727, ebenso diese Bibliothek“ beziehen sich auf die gemessene Batch-Folge; ein Anspruch über den RPC steht dort nicht.

Ergebnis: keine Fundstelle trägt die Folge im RPC, im Massenladen oder im `MERGE` als gemessen. Offen bleibt R-2 des Re-Reviews (INFO): zwei Sätze in `SPEC-035`/`SPEC-036` nennen „gemessen, Form D7“ ohne „im SQL_BATCH“; die Lesart ist belegt, Form D7 ist ein Batch.

## (c) Öffentliche Fläche

`make flaeche` Exit 0. Der Diff gegen `main` berührt `src/integrator` nur in Testdateien (`DropConstraintEmbeddingTest.kt`, `DropDefaultEmbeddingTest.kt`, `WireClient.kt`); in `src/main` ist der neue Typ `ErrorRule.followUp`/`MappedError.followUp`/`TDS_SIM_COLUMN_HAS_DEFAULT` `internal` (`ErrorCode` ist `internal enum`); die Änderungsprotokollzeile der Spezifikation sagt „die öffentliche Fläche wächst nicht“, der Code bestätigt es.

## (d) d-migrate 1.12.0 gegen den Testserver aus dem Arbeitsbaum

Rig: Image `tdswire-testserver:ver-zef` aus dem Arbeitsbaum, Container `ver-zef-ts` (`TESTSERVER_NAME`, Port 1433, Benutzer `alice`), d-migrate per Digest `ghcr.io/pt9912/d-migrate@sha256:308f27753036c9cc423f716b9fc4c9e68016543a8003ecd2f8282e05a2ee6491`, `--network host`, `--user "$(id -u):$(id -g)"`, Soll-Dateien `a` bis `e` wie in Tabelle B des Messprotokolls [standardwert-ersetzen-d-migrate-2026-11-12.md](../messungen/standardwert-ersetzen-d-migrate-2026-11-12.md) (Tabellen `kunden`, `bestellungen`, `lieferanten`, `auftraege` mit Fremdschlüssel; die Dateien schrieb ich neu, die des Protokolls liegen nicht im Repo). Container und Image danach entfernt; andere Container (`u-boot-hetzner-viper-dc1`) nicht angefasst.

| Schritt | `migrate --execute`, Exit | `statementsAttempted` | Tabelle B | `compare`, Exit |
|---|---|---|---|---|
| a (ohne Standardwerte) | 0 | 2 | 2 | 0, `IDENTICAL` |
| b (`Menge` 1, `Status` neu) | 0 | 4 | 4 | 0, `IDENTICAL` |
| c (`Menge` 2) | 0 | 2 | 2 | 0, `IDENTICAL` |
| d (`Status` weg) | 0 | 1 | 1 | 0, `IDENTICAL` |
| e (`Menge` weg) | 0 | 1 | 1 | 0, `IDENTICAL` |

Der Pfad 1781/1750 wird von d-migrate selbst nicht ausgelöst (es sendet vorher den Text mit `DECLARE @df`); belegt ist damit die Zusage „bleibt bei Exit 0 und `IDENTICAL`“, nicht der Fehlerpfad. Der Fehlerpfad ist durch `ErrorMapperTest`, `ResponseFollowUpTest` und die Stufen 3 bis 7 von `make e2e` getragen.

## DoD-Punkte

| DoD-Punkt | Beleg | Urteil |
|---|---|---|
| Bau ([TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend)): 1781 und 1750; 3728 und 3733 mit 3727 danach; Nummern, Class, State aus den Ablagen; Tests je Zusage; RPC als Setzung | (a) Vektoren gleich; Tests `ErrorMapperTest`, `ResponseFollowUpTest`, `SimulationBackendDropDefaultTest`, `SimulationBackendElementsStructureTest`, Einbettungstests frisch grün in `make clean-build`; RPC als Setzung gekennzeichnet (b); Mutationen je Zusage Sache des Implementers, nicht von mir gefahren | erfüllt |
| Fortschreibung und Beleg ([TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität)): `SPEC-035`, `SPEC-036` (D3, D5, D7 (b)), Nutzer-Doku, Register (Punkte 10 und 15 (b)); d-migrate Exit 0 und `IDENTICAL` | Diff der Spezifikation und von `docs/user/` gelesen (Abgleich mit Code und Ablage); (d) Exit 0 und `IDENTICAL` an a bis e; Register `observation.md` und `state.md` im Diff (4 und 2 Zeilen), Inhalt der Punkte 10 und 15 (b) Sache der Closure | erfüllt; Register-Fortschreibung vom Planner bei der Closure gegenzulesen |
| `make gates` grün | Exit 0 | erfüllt |
| Review, Report liegt vor | Erst-Review und Re-Review unter `docs/reviews/` | erfüllt (Findings F-1 bis F-6 behoben oder INFO; R-1, F-3 LOW offen, Sache der Closure) |
| Haken, Doku-Update, Closure-Notiz, Register, Ausgänge §6, Paarungen | Planner beziehungsweise Closure | nicht Gegenstand der Verifikation |

Closure-Trigger §5 (`make clean-build` und `make e2e` einmal durch den Verifier auf dem End-Stand): erfüllt durch diesen Bericht.

## Entscheidungs-Konformität

* [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md): die Strukturabweisungen bleiben `Failure` des Backends beziehungsweise Abweisung der Simulation; die Zuordnung zu `ErrorCode` und die offene Verbindung sind unverändert; `TDS_SIM_COLUMN_HAS_DEFAULT` ist in der Liste der erlaubten `Failure`-Codes ergänzt (`SPEC-035`), die Fläche wächst nicht.
* [ADR-0038](../plan/adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md): der Abschluss nach dem Fehler bleibt nach der Form des Abschlusses; beide ERROR stehen vor dem zurückgehaltenen DONE; ein abgelehntes zweites ERROR schließt ohne DONE (zwei Tests).
* [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität): Nummern, State, Class und Texte der Folge-ERROR sind am echten Server (2022 und 2025) gemessen und gleich.
* `AGENTS.md` §3.2, §3.7, §3.8: kein `@Suppress`, Kommentare Zusage/Kopplung/Grenze, jede Folge gemessen oder Setzung.

## Plan-vs-Code-Diff

`git diff --stat e5e48313..c84d5579`: 23 Dateien. Die Plan-Tabelle §3 nennt alle Quell- und Testdateien; nicht genannt ist in Zeile 3 `SPEC-031` (R-1 des Re-Reviews, LOW, der Kopf „Berührte Spec-Stellen“ nennt sie). Nicht im Plan, aber im Diff: die zwei Review-Reports (Folge des Prozesses). Nicht im Diff, aber im Plan erwartet: `docs/plan/spec-abdeckung.md` (F-3, LOW, Erzeugung durch `make spec-abdeckung` steht aus). Keine Datei außerhalb der Plan-Sub-Area (`src/main`, `src/test`, `src/integrator`, `spec/`, `docs/`).

## Befunde

| ID | Stufe | Befund |
|---|---|---|
| V-1 | INFO | Die Zeilenangaben (05, 08 … 19) in KDoc und `SPEC-036` zählen anders als die Zeilen der Mitschnitt-Datei; ich habe die Zählung nicht abgeglichen, der Inhalt (sechs Fälle) stimmt. |
| V-2 | INFO | Der Mitschnitt zeigt 3727 auch hinter 3725 (Zeile 18); Register Punkt 12, nicht geplant. |
| V-3 | INFO | `make gates` lief in 20 s weitgehend aus dem Docker-Cache; der Frischbeleg der Tests ist `make clean-build`. |

## Offen für den Planner

R-1 (Plan-Tabelle ohne `SPEC-031`), F-3 (`make spec-abdeckung`, neue Testfälle mit `@spec`-Tags), Haken, Closure-Notiz, Ausgänge §6 (Risiko 1: RPC bleibt Setzung, Risiko 2: d-migrate bleibt bei Exit 0 und `IDENTICAL`), Register-Punkte 10 und 15 (b), Paarungen.
